package com.example.learning.module.messaging.service;

import com.example.learning.module.messaging.stomp.StompExperimentProbe;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.TcpServerTransport;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MessagingExperimentService {

    private static final Duration RSOCKET_TIMEOUT = Duration.ofSeconds(3);

    private final ServletWebServerApplicationContext applicationContext;
    private final StompExperimentProbe stompExperimentProbe;

    public MessagingExperimentService(
            ServletWebServerApplicationContext applicationContext,
            StompExperimentProbe stompExperimentProbe
    ) {
        this.applicationContext = applicationContext;
        this.stompExperimentProbe = stompExperimentProbe;
    }

    public Map<String, Object> channelDeliverySemantics() throws InterruptedException {
        String senderThread = Thread.currentThread().getName();

        AtomicReference<String> syncHandlerThread = new AtomicReference<>();
        AtomicBoolean syncHandlerCompleted = new AtomicBoolean(false);
        ExecutorSubscribableChannel synchronousChannel = new ExecutorSubscribableChannel();
        synchronousChannel.subscribe(message -> {
            syncHandlerThread.set(Thread.currentThread().getName());
            syncHandlerCompleted.set(true);
        });

        boolean syncAccepted = synchronousChannel.send(
                MessageBuilder.withPayload("sync").build()
        );
        boolean syncCompletedBeforeSendReturned = syncHandlerCompleted.get();

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "messaging-experiment-worker");
            thread.setDaemon(true);
            return thread;
        });

        CountDownLatch asyncStarted = new CountDownLatch(1);
        CountDownLatch releaseAsyncHandler = new CountDownLatch(1);
        CountDownLatch asyncCompleted = new CountDownLatch(1);
        AtomicReference<String> asyncHandlerThread = new AtomicReference<>();
        AtomicReference<Throwable> asyncFailure = new AtomicReference<>();

        try {
            ExecutorSubscribableChannel asynchronousChannel =
                    new ExecutorSubscribableChannel(executor);
            asynchronousChannel.subscribe(message -> {
                asyncHandlerThread.set(Thread.currentThread().getName());
                asyncStarted.countDown();
                try {
                    if (!releaseAsyncHandler.await(2, TimeUnit.SECONDS)) {
                        asyncFailure.compareAndSet(
                                null,
                                new IllegalStateException("Timed out waiting for experiment release")
                        );
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    asyncFailure.compareAndSet(null, exception);
                } catch (Throwable failure) {
                    asyncFailure.compareAndSet(null, failure);
                } finally {
                    asyncCompleted.countDown();
                }
            });

            boolean asyncAccepted = asynchronousChannel.send(
                    MessageBuilder.withPayload("async").build()
            );
            boolean asyncCompletedWhenSendReturned = asyncCompleted.getCount() == 0;
            boolean asyncHandlerStarted = asyncStarted.await(2, TimeUnit.SECONDS);

            releaseAsyncHandler.countDown();
            boolean asyncHandlerCompleted = asyncCompleted.await(2, TimeUnit.SECONDS);

            if (!asyncHandlerStarted || !asyncHandlerCompleted) {
                throw new IllegalStateException("Asynchronous channel experiment did not finish in time");
            }
            if (asyncFailure.get() != null) {
                throw new IllegalStateException("Asynchronous handler failed", asyncFailure.get());
            }

            return map(
                    "senderThread", senderThread,
                    "syncSendAccepted", syncAccepted,
                    "syncHandlerThread", syncHandlerThread.get(),
                    "syncSameThread", senderThread.equals(syncHandlerThread.get()),
                    "syncCompletedBeforeSendReturned", syncCompletedBeforeSendReturned,
                    "asyncSendAccepted", asyncAccepted,
                    "asyncHandlerThread", asyncHandlerThread.get(),
                    "asyncDifferentThread", !senderThread.equals(asyncHandlerThread.get()),
                    "asyncHandlerStarted", asyncHandlerStarted,
                    "asyncSendReturnedBeforeHandlerCompleted", !asyncCompletedWhenSendReturned,
                    "asyncHandlerCompletedAfterRelease", asyncHandlerCompleted
            );
        } finally {
            releaseAsyncHandler.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Channel experiment executor did not terminate");
            }
        }
    }

    public synchronized Map<String, Object> stompRoundTrip() throws Exception {
        int port = applicationContext.getWebServer().getPort();
        String contextPath = applicationContext.getServletContext().getContextPath();
        String endpoint = "ws://127.0.0.1:" + port
                + (StringUtils.hasText(contextPath) ? contextPath : "")
                + "/messaging-stomp";

        String correlation = UUID.randomUUID().toString();
        String outboundPayload = "round-trip:" + correlation;
        String expectedPayload = "echo:" + outboundPayload;
        CompletableFuture<String> inboundPayload = new CompletableFuture<>();
        CompletableFuture<Throwable> transportFailure = new CompletableFuture<>();
        stompExperimentProbe.reset();

        ExecutorService connectExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "stomp-experiment-connect");
            thread.setDaemon(true);
            return thread;
        });
        StandardWebSocketClient standardWebSocketClient = new StandardWebSocketClient();
        standardWebSocketClient.setTaskExecutor(new TaskExecutorAdapter(connectExecutor));
        AtomicReference<CompletableFuture<WebSocketSession>> handshakeFuture = new AtomicReference<>();
        AtomicReference<WebSocketSession> establishedWebSocketSession = new AtomicReference<>();
        AtomicReference<Throwable> abandonedWebSocketCleanupFailure = new AtomicReference<>();
        AtomicBoolean connectionAbandoned = new AtomicBoolean(false);
        WebSocketClient trackingWebSocketClient = new WebSocketClient() {
            @Override
            public CompletableFuture<WebSocketSession> execute(
                    WebSocketHandler webSocketHandler,
                    String uriTemplate,
                    Object... uriVariables
            ) {
                WebSocketHandler trackedHandler = trackEstablishedWebSocketSession(
                        webSocketHandler,
                        establishedWebSocketSession,
                        connectionAbandoned,
                        abandonedWebSocketCleanupFailure
                );
                CompletableFuture<WebSocketSession> future = standardWebSocketClient.execute(
                        trackedHandler,
                        uriTemplate,
                        uriVariables
                );
                handshakeFuture.set(future);
                return future;
            }

            @Override
            public CompletableFuture<WebSocketSession> execute(
                    WebSocketHandler webSocketHandler,
                    WebSocketHttpHeaders headers,
                    URI uri
            ) {
                WebSocketHandler trackedHandler = trackEstablishedWebSocketSession(
                        webSocketHandler,
                        establishedWebSocketSession,
                        connectionAbandoned,
                        abandonedWebSocketCleanupFailure
                );
                CompletableFuture<WebSocketSession> future = standardWebSocketClient.execute(
                        trackedHandler,
                        headers,
                        uri
                );
                handshakeFuture.set(future);
                return future;
            }
        };
        WebSocketStompClient stompClient = new WebSocketStompClient(trackingWebSocketClient);
        stompClient.setMessageConverter(new StringMessageConverter());
        StompSession session = null;
        StompSession.Subscription subscription = null;
        Throwable primaryFailure = null;
        long started = System.nanoTime();

        try {
            CompletableFuture<StompSession> connectFuture = stompClient.connectAsync(
                    endpoint,
                    new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    transportFailure.complete(exception);
                    inboundPayload.completeExceptionally(exception);
                }
                    }
            );
            try {
                session = connectFuture.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException timeout) {
                connectionAbandoned.set(true);
                connectFuture.cancel(true);
                CompletableFuture<WebSocketSession> activeHandshake = handshakeFuture.get();
                if (activeHandshake != null) {
                    activeHandshake.cancel(true);
                }
                WebSocketSession activeWebSocketSession = establishedWebSocketSession.get();
                if (activeWebSocketSession != null) {
                    closeAbandonedWebSocket(
                            activeWebSocketSession,
                            abandonedWebSocketCleanupFailure
                    );
                }
                throw timeout;
            }

            StompSession connectedSession = session;
            subscription = runBoundedStompAction("subscribe", () -> connectedSession.subscribe(
                    "/topic/experiment.echo",
                    new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    inboundPayload.complete((String) payload);
                }
                    }
            ));

            runBoundedStompAction("send", () -> {
                connectedSession.send("/app/experiment.echo", outboundPayload);
                return null;
            });
            String receivedPayload = inboundPayload.get(3, TimeUnit.SECONDS);
            StompExperimentProbe.Observation handlerObservation = stompExperimentProbe.snapshot();

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started
            );

            return map(
                    "webSocketEndpoint", endpoint,
                    "stompSessionId", session.getSessionId(),
                    "sentDestination", "/app/experiment.echo",
                    "subscriptionDestination", "/topic/experiment.echo",
                    "sentPayload", outboundPayload,
                    "handlerObserved", handlerObservation != null,
                    "handlerPayload", handlerObservation == null ? null : handlerObservation.payload(),
                    "handlerThread", handlerObservation == null ? null : handlerObservation.handlerThread(),
                    "receivedPayload", receivedPayload,
                    "roundTripMatched", expectedPayload.equals(receivedPayload),
                    "transportFailureObserved", transportFailure.isDone(),
                    "elapsedMillis", elapsedMillis
            );
        } catch (Exception failure) {
            primaryFailure = failure;
            throw failure;
        } catch (Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = null;
            if (session == null) {
                connectionAbandoned.set(true);
                CompletableFuture<WebSocketSession> activeHandshake = handshakeFuture.get();
                if (activeHandshake != null && !activeHandshake.isDone()) {
                    activeHandshake.cancel(true);
                }
                WebSocketSession activeWebSocketSession = establishedWebSocketSession.get();
                if (activeWebSocketSession != null) {
                    closeAbandonedWebSocket(
                            activeWebSocketSession,
                            abandonedWebSocketCleanupFailure
                    );
                }
            }
            if (subscription != null) {
                StompSession.Subscription activeSubscription = subscription;
                try {
                    runBoundedStompAction("unsubscribe", () -> {
                        activeSubscription.unsubscribe();
                        return null;
                    });
                } catch (Throwable failure) {
                    cleanupFailure = mergeFailures(cleanupFailure, failure);
                }
            }
            if (session != null && session.isConnected()) {
                StompSession connectedSession = session;
                try {
                    runBoundedStompAction("disconnect", () -> {
                        connectedSession.disconnect();
                        return null;
                    });
                } catch (Throwable failure) {
                    cleanupFailure = mergeFailures(cleanupFailure, failure);
                }
            }
            try {
                shutdownExecutor(connectExecutor, "STOMP connect");
            } catch (Throwable failure) {
                cleanupFailure = mergeFailures(cleanupFailure, failure);
            }
            Throwable abandonedCloseFailure = abandonedWebSocketCleanupFailure.get();
            if (abandonedCloseFailure != null) {
                cleanupFailure = mergeFailures(cleanupFailure, abandonedCloseFailure);
            }

            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else if (cleanupFailure instanceof Exception exception) {
                    throw exception;
                } else if (cleanupFailure instanceof Error error) {
                    throw error;
                } else {
                    throw new IllegalStateException("STOMP cleanup failed", cleanupFailure);
                }
            }
        }
    }

    public Map<String, Object> rsocketInteractionCardinality() {
        RSocketStrategies strategies = RSocketStrategies.builder().build();
        var server = RSocketServer.create(
                RSocketMessageHandler.responder(strategies, new CardinalityResponder())
        ).bind(TcpServerTransport.create("127.0.0.1", 0))
                .block(RSOCKET_TIMEOUT);
        if (server == null) {
            throw new IllegalStateException("RSocket experiment server did not bind");
        }
        try {
            int port = ((InetSocketAddress) server.address()).getPort();
            RSocketRequester requester = RSocketRequester.builder()
                    .rsocketStrategies(strategies)
                    .tcp("127.0.0.1", port);
            try {
                String oneToOne = requester.route("cardinality.one-one")
                        .data("A")
                        .retrieveMono(String.class)
                        .block(RSOCKET_TIMEOUT);

                List<String> oneToMany = requester.route("cardinality.one-many")
                        .data("B")
                        .retrieveFlux(String.class)
                        .collectList()
                        .block(RSOCKET_TIMEOUT);

                List<String> manyToMany = requester.route("cardinality.many-many")
                        .data(Flux.just("C1", "C2", "C3"), String.class)
                        .retrieveFlux(String.class)
                        .collectList()
                        .block(RSOCKET_TIMEOUT);

                return map(
                        "serverPort", port,
                        "oneToOne", cardinality(
                                "request-response", 1, 1, List.of(oneToOne)
                        ),
                        "oneToMany", cardinality(
                                "request-stream", 1, oneToMany.size(), oneToMany
                        ),
                        "manyToMany", cardinality(
                                "request-channel", 3, manyToMany.size(), manyToMany
                        )
                );
            } finally {
                requester.dispose();
            }
        } finally {
            server.dispose();
            server.onClose().block(RSOCKET_TIMEOUT);
        }
    }

    private static Map<String, Object> cardinality(
            String interaction,
            int inputCount,
            int outputCount,
            List<String> values
    ) {
        return map(
                "interaction", interaction,
                "inputCount", inputCount,
                "outputCount", outputCount,
                "values", values
        );
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    private static <T> T runBoundedStompAction(
            String action,
            Callable<T> callable
    ) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "stomp-experiment-" + action.replace(' ', '-'));
            thread.setDaemon(true);
            return thread;
        });
        Future<T> future = executor.submit(callable);
        Throwable primaryFailure = null;

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            IllegalStateException failure = new IllegalStateException(
                    "STOMP " + action + " did not complete within 3 seconds",
                    timeout
            );
            primaryFailure = failure;
            throw failure;
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            primaryFailure = interrupted;
            throw interrupted;
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause();
            primaryFailure = cause;
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("STOMP " + action + " failed", cause);
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    IllegalStateException cleanup = new IllegalStateException(
                            "STOMP " + action + " executor did not terminate"
                    );
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(cleanup);
                    } else {
                        throw cleanup;
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(interrupted);
                } else {
                    throw interrupted;
                }
            }
        }
    }

    private static Throwable mergeFailures(Throwable current, Throwable next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private static void closeAbandonedWebSocket(
            WebSocketSession session,
            AtomicReference<Throwable> cleanupFailure
    ) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close();
        } catch (IOException failure) {
            recordCleanupFailure(cleanupFailure, failure);
        }
    }

    private static void recordCleanupFailure(
            AtomicReference<Throwable> cleanupFailure,
            Throwable failure
    ) {
        synchronized (cleanupFailure) {
            Throwable current = cleanupFailure.get();
            if (current == null) {
                cleanupFailure.set(failure);
            } else if (current != failure) {
                current.addSuppressed(failure);
            }
        }
    }

    private static WebSocketHandler trackEstablishedWebSocketSession(
            WebSocketHandler delegate,
            AtomicReference<WebSocketSession> establishedWebSocketSession,
            AtomicBoolean connectionAbandoned,
            AtomicReference<Throwable> abandonedWebSocketCleanupFailure
    ) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
                establishedWebSocketSession.set(webSocketSession);
                if (connectionAbandoned.get()) {
                    closeAbandonedWebSocket(
                            webSocketSession,
                            abandonedWebSocketCleanupFailure
                    );
                    return;
                }
                super.afterConnectionEstablished(webSocketSession);
            }
        };
    }

    private static void shutdownExecutor(
            ExecutorService executor,
            String label
    ) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException(label + " executor did not terminate");
        }
    }

    private static final class CardinalityResponder {

        @MessageMapping("cardinality.one-one")
        String oneToOne(String value) {
            return "one:" + value;
        }

        @MessageMapping("cardinality.one-many")
        Flux<String> oneToMany(String value) {
            return Flux.just(value + "-1", value + "-2", value + "-3");
        }

        @MessageMapping("cardinality.many-many")
        Flux<String> manyToMany(Flux<String> values) {
            return values.map(value -> "mapped:" + value);
        }
    }
}
