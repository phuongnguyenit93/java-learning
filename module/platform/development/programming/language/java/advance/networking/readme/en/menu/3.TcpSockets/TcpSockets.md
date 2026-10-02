<a id="back-to-top"></a>

# TCP and Stream Sockets

## Menu
- [The Java TCP Connection Model](#tcp-connection-model)
- [Bind, Connect, and Accept](#tcp-bind-connect-accept)
- [Socket and ServerSocket Lifecycle](#tcp-client-server-lifecycle)
- [InputStream and OutputStream Network I/O](#tcp-stream-io)
- [Byte Streams and Message Framing](#tcp-message-framing)
- [Half-Close, shutdownInput, and shutdownOutput](#tcp-half-close)
- [Connection Close and Resource Ownership](#tcp-resource-lifecycle)

## <a id="tcp-connection-model">The Java TCP Connection Model</a>

<details>
<summary>Click for details</summary>

TCP provides a **bidirectional byte-stream connection** between two endpoints. In Java, a `Socket` represents one end of that connection, while a server uses a `ServerSocket` to wait for new connections. When a client connects successfully, the server does not read or write through the `ServerSocket`; `accept()` returns a new `Socket` dedicated to that client connection.

The relationship can be pictured as:

```text
client Socket  <========== TCP connection ==========>  accepted Socket
                                                      ^
                                                      |
                                                ServerSocket
                                             bind + wait in accept()
```

The key mental model is that TCP carries an ordered sequence of bytes. `Socket` exposes that sequence through `InputStream` and `OutputStream`, but TCP does not know where an application "request", "record", or "message" begins or ends. The application protocol must define those boundaries itself.

An established connection has two complete endpoints: a local address/port and a remote address/port. A server can therefore use one listening port for many clients while every accepted `Socket` still represents an independent connection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-bind-connect-accept">Bind, Connect, and Accept</a>

<details>
<summary>Click for details</summary>

`bind`, `connect`, and `accept` describe different roles in establishing a TCP connection.

- `bind` associates a socket with a local endpoint. A `ServerSocket` normally binds before receiving connections. A client can also bind its `Socket` to a specific local address/port when required; otherwise the platform normally chooses an appropriate local address and ephemeral port as part of connecting.
- `connect` is the active-side operation: the client `Socket` asks to establish a connection to the server endpoint.
- `accept` is the passive-side operation: the `ServerSocket` waits for an incoming connection and returns a new `Socket` when one is accepted.

For example, a server can be created unbound and then bound explicitly:

```java
try (ServerSocket server = new ServerSocket()) {
    server.bind(new InetSocketAddress("0.0.0.0", 8080));

    try (Socket client = server.accept()) {
        // client is the newly accepted connection.
    }
}
```

`0.0.0.0` is the IPv4 wildcard address in this example, allowing the server to receive connections sent to suitable local IPv4 addresses. Binding to port `0` asks the system to choose an ephemeral port; `getLocalPort()` reveals the selected port after binding.

`ServerSocket.bind(endpoint, backlog)` also accepts a `backlog`, the **requested** maximum length of the pending incoming-connection queue. The effective queue behavior is implementation and operating-system dependent, so the value should not be treated as an exact application-level capacity limit.

For a normal blocking `ServerSocket`, `accept()` waits until a connection is available. Timeout, cancellation, and failure control belong to the dedicated failure chapter; the important lifecycle fact here is that `accept()` creates a connected socket and does not turn the `ServerSocket` itself into the client connection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-client-server-lifecycle">Socket and ServerSocket Lifecycle</a>

<details>
<summary>Click for details</summary>

The basic client and server lifecycles can be read side by side:

```text
Server                                  Client
------                                  ------
create ServerSocket                     create Socket
bind local endpoint                     connect remote endpoint
accept() waits for an incoming          TCP connection is established
connection                              with the server endpoint
   |
accepted Socket
   |                                    |
read/write bytes <--------------------> read/write bytes
   |                                    |
close accepted Socket                  close Socket

The ServerSocket can continue accepting other connections until it is closed.
```

A minimal sequential server shows the ownership of each accepted socket:

```java
try (ServerSocket server = new ServerSocket(8080)) {
    while (!server.isClosed()) {
        try (Socket connection = server.accept()) {
            handle(connection);
        }
    }
}
```

A real server often handles multiple connections concurrently. This Networking chapter owns the socket boundary: every connection has its own `Socket` and resource lifecycle. Thread, executor, and virtual-thread design belong to the corresponding concurrency curriculum.

A client commonly scopes one communication session with `try-with-resources`:

```java
try (Socket socket = new Socket()) {
    socket.connect(new InetSocketAddress("example.com", 8080));

    // use socket.getInputStream() and socket.getOutputStream()
}
```

After `close()`, the socket cannot be reused for a new connection; create another socket for another connection. Likewise, closing an accepted `Socket` does not close the `ServerSocket`. The listener and each established connection are distinct resources.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-stream-io">InputStream and OutputStream Network I/O</a>

<details>
<summary>Click for details</summary>

Once a `Socket` is connected, `getInputStream()` exposes bytes arriving at the local endpoint and `getOutputStream()` exposes bytes sent toward the remote endpoint.

```java
InputStream in = socket.getInputStream();
OutputStream out = socket.getOutputStream();

out.write("PING\n".getBytes(StandardCharsets.UTF_8));
out.flush();

byte[] buffer = new byte[1024];
int read = in.read(buffer);
if (read == -1) {
    // The peer ended its sending direction and all received bytes were consumed.
}
```

`read(byte[])` does not promise to fill the buffer. It can return fewer bytes than the buffer size even when the sender performed one large `write()`. Conversely, one read can contain bytes originating from several earlier writes. That behavior follows directly from the **byte-stream** model.

`write()` contributes bytes to the stream; it does not turn the call into one message on the wire. If the socket stream is wrapped in `BufferedOutputStream`, `BufferedWriter`, `DataOutputStream`, or another buffered layer, account for the wrapper's buffering rules. `flush()` matters when a wrapper is holding data that the peer needs before the protocol can progress.

When the peer normally finishes its sending direction and every received byte has been consumed, `InputStream.read()` returns `-1`. That value is stream EOF, not a data byte and not a signal that "no data is available right now."

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-message-framing">Byte Streams and Message Framing</a>

<details>
<summary>Click for details</summary>

TCP preserves byte order, but it **does not preserve the boundaries between `write()` calls**. Code that assumes "one sender write equals one receiver read" therefore does not define a valid framing protocol.

Suppose a sender performs:

```text
write("HELLO")
write("JAVA")
```

The receiver might observe reads such as `"HEL"`, `"LOJA"`, `"VA"`, or another split. The final byte sequence is still `HELLOJAVA`; only the sizes of individual reads differ.

An application needs a **framing** rule. Common approaches include:

- fixed length: every record has a known size;
- delimiter: for example, a text protocol terminates each line with `\n`, together with suitable encoding/escaping rules if payloads can contain the delimiter;
- length prefix: send a length first, then read exactly that many payload bytes;
- self-describing format: a parser identifies the end from the format, while still handling input that arrives in pieces.

A simple length-prefix example:

```java
// sender
byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
out.writeInt(payload.length);
out.write(payload);
out.flush();

// receiver
DataInputStream in = new DataInputStream(socket.getInputStream());
int length = in.readInt();
if (length < 0 || length > 1_048_576) {
    throw new IOException("Invalid frame length: " + length);
}
byte[] message = in.readNBytes(length);
if (message.length != length) {
    throw new EOFException("Connection ended in the middle of a frame");
}
```

Validating the advertised length before allocating memory is part of safe framing. `readNBytes(length)` expresses the intent to collect the requested number of bytes, but EOF can still occur in the middle of a frame and must be treated according to the application protocol.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-half-close">Half-Close, shutdownInput, and shutdownOutput</a>

<details>
<summary>Click for details</summary>

TCP is full-duplex, so the two transfer directions can finish independently. Java exposes that capability through `shutdownOutput()` and `shutdownInput()` on `Socket`.

`shutdownOutput()` says that the local endpoint will **send no more bytes**. For a TCP socket, data written earlier is sent and followed by TCP's normal termination sequence for the output direction. The peer can still send data in the opposite direction, and the local socket can continue reading it.

One useful pattern is a request whose end is signaled by EOF in one direction:

```java
try (Socket socket = new Socket("example.com", 8080)) {
    OutputStream out = socket.getOutputStream();
    out.write(requestBytes);
    out.flush();

    socket.shutdownOutput(); // request is complete

    byte[] response = socket.getInputStream().readAllBytes();
    // The peer may send a response and then finish its sending direction.
}
```

Unlike `close()`, a half-close leaves the other direction usable. For the same reason, **do not close the `OutputStream` merely to request a half-close**: the `Socket` contract states that closing the stream returned by either `getInputStream()` or `getOutputStream()` closes the associated socket.

`shutdownInput()` places the local read side at end-of-stream; subsequent reads behave as EOF according to the `Socket` contract. It is a local input-side operation, not an application message telling the peer "I no longer want to receive." Application protocols therefore use `shutdownOutput()` more often as a meaningful EOF signal to the other endpoint, while `shutdownInput()` is less common in ordinary application code.

`isInputShutdown()` and `isOutputShutdown()` expose the local half-close state, but those flags do not replace handling EOF and `IOException` in the actual I/O flow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tcp-resource-lifecycle">Connection Close and Resource Ownership</a>

<details>
<summary>Click for details</summary>

A network connection holds operating-system resources, so code needs a clear answer to **who owns each socket and who closes it**. A simple ownership rule is that the component taking ownership of a connection closes it with `try-with-resources` when the session ends.

```java
void handleClient(ServerSocket server) throws IOException {
    try (Socket socket = server.accept()) {
        process(socket);
    } // this client connection is closed here
}
```

The `ServerSocket` and accepted `Socket` instances are independent resources:

- closing one accepted `Socket` ends that connection while the server can keep accepting others;
- closing the `ServerSocket` stops the listener and causes a thread blocked in `accept()` to wake with a socket-close failure;
- closing the `ServerSocket` is not automatic lifecycle management for all accepted connections that have already been handed to other code.

The two streams obtained from a `Socket` are tied to the same connection. The Java API specifies that closing either returned `InputStream` or returned `OutputStream` closes the associated socket. Avoid ownership designs where unrelated layers independently believe they own one stream without realizing that closing it affects the entire connection.

`try-with-resources` makes cleanup deterministic on both success and exception paths:

```java
try (Socket socket = new Socket("example.com", 8080)) {
    InputStream in = socket.getInputStream();
    OutputStream out = socket.getOutputStream();
    exchange(in, out);
}
```

Here the `Socket` is the resource with explicit ownership; closing it also ends the streams associated with that connection. This avoids suggesting that the three objects represent three independent network lifecycles.

When half-close is required, manage the lifecycle at the `Socket` level and use `shutdownOutput()`/`shutdownInput()` for their intended roles instead of closing a stream early. Timeouts, resets, cancellation, and other failure modes are developed in the Failure Control chapter; the rule carried forward here is that every exit path must leave resource ownership and closure deterministic.

Before moving into those cross-cutting failure controls, the next chapter contrasts TCP's connection-oriented byte stream with UDP's independent datagram model so the learner can separate transport/data semantics from lifecycle policy.

</details>

- [Quay lại đầu trang](#back-to-top)
