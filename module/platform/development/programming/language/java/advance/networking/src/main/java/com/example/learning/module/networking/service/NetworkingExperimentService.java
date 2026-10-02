package com.example.learning.module.networking.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class NetworkingExperimentService {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    public Map<String, Object> tcpFramingDemo() throws Exception {
        byte[] payload = "HELLO-WORLD".getBytes(StandardCharsets.UTF_8);
        List<Integer> readSizes = new ArrayList<>();

        try (ServerSocket server = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<String> received = new CompletableFuture<>();

            Thread receiver = Thread.ofPlatform()
                    .daemon(true)
                    .name("networking-tcp-framing-receiver")
                    .start(() -> {
                        try (Socket accepted = server.accept()) {
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            byte[] chunk = new byte[4];
                            int read;

                            while ((read = accepted.getInputStream().read(chunk)) != -1) {
                                readSizes.add(read);
                                buffer.write(chunk, 0, read);
                            }

                            received.complete(buffer.toString(StandardCharsets.UTF_8));
                        } catch (Exception exception) {
                            received.completeExceptionally(exception);
                        }
                    });

            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(LOOPBACK, server.getLocalPort()), 1_000);
                client.getOutputStream().write(payload);
                client.getOutputStream().flush();
                client.shutdownOutput();

                String reconstructed = received.get(2, TimeUnit.SECONDS);

                return map(
                        "singleApplicationWriteBytes", payload.length,
                        "receiverBufferBytes", 4,
                        "readSizes", List.copyOf(readSizes),
                        "readCalls", readSizes.size(),
                        "reconstructedPayload", reconstructed,
                        "payloadPreserved", "HELLO-WORLD".equals(reconstructed),
                        "oneWriteObservedAcrossMultipleReads", readSizes.size() > 1
                );
            } finally {
                server.close();
                receiver.join(2_000);
                if (receiver.isAlive()) {
                    receiver.interrupt();
                    receiver.join(1_000);
                }
                if (receiver.isAlive()) {
                    throw new IllegalStateException("TCP framing receiver did not terminate");
                }
            }
        }
    }

    public Map<String, Object> udpTruncationDemo() throws IOException {
        byte[] sent = "ABCDEFGH".getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket receiver = new DatagramSocket(0, LOOPBACK);
             DatagramSocket sender = new DatagramSocket()) {

            receiver.setSoTimeout(1_000);

            DatagramPacket outgoing = new DatagramPacket(
                    sent,
                    sent.length,
                    LOOPBACK,
                    receiver.getLocalPort()
            );
            sender.send(outgoing);

            byte[] receiveBuffer = new byte[4];
            DatagramPacket incoming = new DatagramPacket(
                    receiveBuffer,
                    receiveBuffer.length
            );
            receiver.receive(incoming);

            String observed = new String(
                    incoming.getData(),
                    incoming.getOffset(),
                    incoming.getLength(),
                    StandardCharsets.UTF_8
            );

            receiver.setSoTimeout(150);
            boolean secondReceiveTimedOut = false;
            try {
                byte[] secondBuffer = new byte[4];
                receiver.receive(new DatagramPacket(
                        secondBuffer,
                        secondBuffer.length
                ));
            } catch (SocketTimeoutException exception) {
                secondReceiveTimedOut = true;
            }

            return map(
                    "sentBytes", sent.length,
                    "receiveBufferBytes", receiveBuffer.length,
                    "receivedBytes", incoming.getLength(),
                    "observedPayload", observed,
                    "senderAddress", incoming.getSocketAddress().toString(),
                    "datagramTruncated", incoming.getLength() < sent.length,
                    "secondReceiveTimedOut", secondReceiveTimedOut,
                    "secondDatagramObserved", !secondReceiveTimedOut
            );
        }
    }

    public Map<String, Object> readTimeoutDemo() throws IOException {
        int timeoutMillis = 150;

        try (ServerSocket server = new ServerSocket(0, 1, LOOPBACK);
             Socket client = new Socket()) {

            client.connect(new InetSocketAddress(LOOPBACK, server.getLocalPort()), 1_000);
            try (Socket peer = server.accept()) {
                client.setSoTimeout(timeoutMillis);

                String exceptionType = null;
                long started = System.nanoTime();

                try {
                    client.getInputStream().read();
                } catch (SocketTimeoutException exception) {
                    exceptionType = exception.getClass().getSimpleName();
                }

                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - started
                );
                boolean openAfterTimeout = !client.isClosed();

                peer.getOutputStream().write('R');
                peer.getOutputStream().flush();

                client.setSoTimeout(1_000);
                int recoveryRead = client.getInputStream().read();

                return map(
                        "configuredReadTimeoutMillis", timeoutMillis,
                        "observedException", exceptionType,
                        "elapsedMillis", elapsedMillis,
                        "readTimedOut", "SocketTimeoutException".equals(exceptionType),
                        "socketStillOpenAfterTimeout", openAfterTimeout,
                        "recoveryRead", recoveryRead == -1
                                ? null
                                : Character.toString((char) recoveryRead),
                        "sameSocketReadSucceededAfterTimeout", recoveryRead == 'R'
                );
            }
        }
    }

    public Map<String, Object> selectorReadinessDemo() throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open();
             SocketChannel client = SocketChannel.open()) {

            server.configureBlocking(false);
            server.bind(new InetSocketAddress(LOOPBACK, 0));
            SelectionKey serverKey = server.register(
                    selector,
                    SelectionKey.OP_ACCEPT
            );

            InetSocketAddress serverAddress =
                    (InetSocketAddress) server.getLocalAddress();
            client.connect(serverAddress);

            int acceptSelectionCount = 0;
            boolean acceptReady = false;
            SocketChannel accepted = null;
            long acceptDeadline =
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

            while (accepted == null && System.nanoTime() < acceptDeadline) {
                long remainingNanos =
                        acceptDeadline - System.nanoTime();
                long waitMillis = Math.max(
                        1,
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos)
                );

                acceptSelectionCount += selector.select(waitMillis);
                acceptReady = serverKey.isValid()
                        && serverKey.isAcceptable();
                selector.selectedKeys().clear();

                if (acceptReady) {
                    accepted = server.accept();
                }
            }

            if (accepted == null) {
                throw new IllegalStateException(
                        "No connection became acceptable before the experiment deadline"
                );
            }

            SocketChannel acceptedChannel = accepted;
            try (acceptedChannel) {
                acceptedChannel.configureBlocking(false);
                SelectionKey readKey = acceptedChannel.register(
                        selector,
                        SelectionKey.OP_READ
                );

                ByteBuffer outbound =
                        ByteBuffer.wrap(
                                "PING".getBytes(StandardCharsets.UTF_8)
                        );

                while (outbound.hasRemaining()) {
                    client.write(outbound);
                }

                int readSelectionCount = 0;
                boolean readReady = false;
                long readDeadline =
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(1);

                while (!readReady && System.nanoTime() < readDeadline) {
                    long remainingNanos =
                            readDeadline - System.nanoTime();
                    long waitMillis = Math.max(
                            1,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos)
                    );

                    readSelectionCount += selector.select(waitMillis);
                    readReady = readKey.isValid()
                            && readKey.isReadable();
                    selector.selectedKeys().clear();
                }

                if (!readReady) {
                    throw new IllegalStateException(
                            "Accepted channel did not become readable before the experiment deadline"
                    );
                }

                ByteBuffer buffer = ByteBuffer.allocate(16);
                int bytesRead = acceptedChannel.read(buffer);
                buffer.flip();
                String payload = StandardCharsets.UTF_8.decode(buffer).toString();

                return map(
                        "acceptSelectionCount", acceptSelectionCount,
                        "acceptReady", acceptReady,
                        "readSelectionCount", readSelectionCount,
                        "readReady", readReady,
                        "bytesRead", bytesRead,
                        "payload", payload,
                        "readMadeProgress", bytesRead > 0,
                        "selectorObservedBothStages", acceptReady && readReady
                );
            }
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }
}
