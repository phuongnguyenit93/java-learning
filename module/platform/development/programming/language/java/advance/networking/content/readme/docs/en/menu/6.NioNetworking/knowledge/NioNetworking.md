<a id="back-to-top"></a>

# NIO Networking, Multiplexing, and Asynchronous I/O

## Menu
- [SocketChannel, ServerSocketChannel, and DatagramChannel](#network-channels)
- [Buffers and Partial Network I/O](#network-buffer-interaction)
- [Selector and the Readiness Model](#selector-readiness-model)
- [Registration, Interest Sets, and SelectionKey](#selection-key-lifecycle)
- [AsynchronousSocketChannel and the Completion Model](#asynchronous-channel-model)
- [Unix-Domain Sockets with Network Channels](#unix-domain-channels)
- [Virtual Threads and Blocking Network I/O on Java 21](#virtual-threads-network-io)
- [Choosing Blocking, Selector, or Asynchronous I/O](#network-io-model-choice)

## <a id="network-channels">SocketChannel, ServerSocketChannel, and DatagramChannel</a>

<details>
<summary>Click for details</summary>

NIO networking keeps the TCP and UDP transport concepts from the previous chapters but replaces stream wrappers with **channels plus `ByteBuffer`**, and lets selectable channels operate in blocking or non-blocking mode.

- `SocketChannel` is a stream-oriented socket channel that reads/writes through `ByteBuffer`. A channel can be unconnected, connection-pending, or connected. The default `open()` family is used for Internet stream sockets; opening with `StandardProtocolFamily.UNIX` gives the same channel abstraction a Unix-domain endpoint instead.
- `ServerSocketChannel` binds/listens and `accept()`s new connections; each accepted connection is represented by its own `SocketChannel`.
- `DatagramChannel` sends and receives datagrams, preserving UDP message boundaries rather than exposing a byte stream.

```java
try (ServerSocketChannel server = ServerSocketChannel.open()) {
    server.bind(new InetSocketAddress(8080));

    try (SocketChannel connection = server.accept()) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int read = connection.read(buffer);
    }
}
```

New channels start in blocking mode. NIO does not mean "channels are always non-blocking"; the same abstractions can support a straightforward blocking flow or switch to non-blocking mode for selector registration.

For an Internet stream socket, a `SocketChannel` still exposes a **byte stream**. Moving from `Socket` to `SocketChannel` does not give TCP message boundaries. The framing rules from the TCP chapter still apply.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-buffer-interaction">Buffers and Partial Network I/O</a>

<details>
<summary>Click for details</summary>

`ByteBuffer` carries both bytes and state through `position`, `limit`, and `capacity`. Network I/O advances the buffer position, so protocol state and buffer state have to be managed together.

One `read()` or `write()` **does not mean that an entire frame was processed**:

- `read(buffer)` can consume fewer bytes than `buffer.remaining()`; in non-blocking mode it can return `0`; `-1` marks end-of-stream.
- `write(buffer)` can write fewer than the remaining bytes. If `buffer.hasRemaining()` is still true, the unsent data must be retained for a later write.

A simple blocking write can loop until the buffer is drained:

```java
ByteBuffer out = StandardCharsets.UTF_8.encode("hello\n");
while (out.hasRemaining()) {
    channel.write(out);
}
```

For a non-blocking channel, that loop must not become a busy-spin when `write()` returns `0`. An event loop normally stores the pending buffer in connection state and enables `OP_WRITE` only while unsent data exists.

The read side commonly follows this state transition:

```text
channel.read(buffer)
        ↓
buffer.flip()      // switch from filling the buffer to reading received data
        ↓
parse/consume bytes
        ↓
buffer.compact()   // retain an incomplete frame and make room for more input
```

`clear()` is appropriate when all old data was consumed; `compact()` is useful when part of a frame remains. This is why buffer state and protocol framing must be designed together in non-blocking network code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selector-readiness-model">Selector and the Readiness Model</a>

<details>
<summary>Click for details</summary>

A `Selector` is a **multiplexor of `SelectableChannel` objects**. One thread can wait for readiness across many channels instead of blocking separately on each channel. A selector does not execute business logic and is not a thread pool.

A channel must be in non-blocking mode before it can be registered with a selector:

```java
try (Selector selector = Selector.open();
     ServerSocketChannel server = ServerSocketChannel.open()) {

    server.bind(new InetSocketAddress(8080));
    server.configureBlocking(false);
    server.register(selector, SelectionKey.OP_ACCEPT);

    while (true) {
        selector.select();

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();

            if (key.isAcceptable()) {
                // accept and register the new channel when appropriate
            }
        }
    }
}
```

Readiness answers a question like "this operation **appears able to make progress now**"; it does not promise that the next I/O call will complete all work. The `SelectionKey` API explicitly describes the ready set as a hint whose accuracy can change after selection or after I/O occurs.

Common operations are `OP_ACCEPT`, `OP_CONNECT`, `OP_READ`, and `OP_WRITE`, depending on the channel type. `select()` can block for readiness, `selectNow()` checks without blocking, and `wakeup()` lets another thread make a blocked selector return so the event loop can observe registration or state changes.

When using `selectedKeys()`, application code removes or clears processed keys. Selection adds ready keys to that set; it does not remove each key merely because the application handled it.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="selection-key-lifecycle">Registration, Interest Sets, and SelectionKey</a>

<details>
<summary>Click for details</summary>

One channel/selector pair has one `SelectionKey`. The first registration creates that key; registering the same channel with the same selector again returns the existing key while updating the interest set, and a non-null attachment supplied to the three-argument overload replaces the current attachment. Registering the channel with a different selector has a distinct key. Each key therefore ties together one **channel**, one **selector**, and the selection state for that pair.

Keep its two operation sets distinct:

- the **interest set** says which operation categories the application wants tested on the next selection, read/changed through `interestOps(...)`;
- the **ready set** reports operation categories the selector detected as ready, read through `readyOps()` or helpers such as `isReadable()`.

Connection state is often attached to the key:

```java
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
key.attach(new ConnectionState());
```

When outbound data appears, the event loop can add `OP_WRITE`; after all pending bytes are sent, remove `OP_WRITE` from the interest set. Leaving write interest enabled continuously can make a usually-writable socket wake the selector repeatedly and create a busy loop.

Non-blocking connect also forms a state machine. `SocketChannel.connect(remote)` can return `false`; the channel can then be watched with `OP_CONNECT`, and `finishConnect()` is called when it becomes connectable to finish the connection or surface its failure.

`cancel()` invalidates a key, but actual deregistration is processed by the selector during a later selection operation. Closing the channel or selector also invalidates the key. Event loops therefore need validity checks when other threads may close or cancel resources.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="asynchronous-channel-model">AsynchronousSocketChannel and the Completion Model</a>

<details>
<summary>Click for details</summary>

`AsynchronousSocketChannel` and `AsynchronousServerSocketChannel` use a **completion model**: application code initiates an operation and consumes its result when the operation completes. This differs from Selector readiness, where the application is told that I/O may proceed and then performs the I/O itself.

The two main completion styles are `Future` and `CompletionHandler`:

```java
try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open()) {
    channel.connect(new InetSocketAddress("example.com", 8080)).get();

    ByteBuffer buffer = ByteBuffer.allocate(1024);
    Future<Integer> pendingRead = channel.read(buffer);
    int read = pendingRead.get();
}
```

The operation is still initiated through the asynchronous-channel model, but calling `Future.get()` immediately as in this example makes the caller wait at that point. A `Future` does not by itself turn the following code into a callback/composition pipeline.

Or a callback:

```java
channel.read(buffer, state, new CompletionHandler<Integer, State>() {
    @Override
    public void completed(Integer read, State state) {
        // operation completed; process the result and initiate the next step
    }

    @Override
    public void failed(Throwable error, State state) {
        // handle failure and resource lifecycle
    }
});
```

Asynchronous APIs do not remove partial I/O: a completion result is still the number of bytes actually read or written. A buffer also must not be concurrently modified while the asynchronous operation is using it.

These channels impose pending-operation rules. Starting another read while a previous read is outstanding can cause `ReadPendingException`; the corresponding write rule can cause `WritePendingException`, and an asynchronous server permits at most one outstanding accept (`AcceptPendingException`). The state machine still needs clear ownership.

Channels can belong to an `AsynchronousChannelGroup`, through which the implementation manages execution resources used for completion. That is the networking completion mechanism; deeper executor and concurrency design belongs to the Concurrency curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unix-domain-channels">Unix-Domain Sockets with Network Channels</a>

<details>
<summary>Click for details</summary>

Not every socket channel uses an IP address and port. The JDK supports **Unix-domain sockets** through `SocketChannel` and `ServerSocketChannel`, with a `UnixDomainSocketAddress` whose endpoint is a file-system path.

```java
Path socketPath = Path.of("/tmp/java-learning.sock");
Files.deleteIfExists(socketPath);

UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);

try (ServerSocketChannel server =
         ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
    server.bind(address);
    try (SocketChannel connection = server.accept()) {
        // use ByteBuffer read/write as with another stream-oriented SocketChannel
    }
} finally {
    Files.deleteIfExists(socketPath);
}
```

A client can open a channel in `StandardProtocolFamily.UNIX` and `connect(address)`. This transport is for same-host IPC; it is not another spelling of `InetSocketAddress`.

Support depends on the platform/provider, so code must account for the protocol family being unsupported. Also, when a Unix-domain server binds a named path, the Java API specifies that the socket file **persists after the channel closes**; the application is responsible for removing the path before rebinding when appropriate.

`UnixDomainSocketAddress` uses a `Path` from the system-default file system, and platforms impose implementation-specific limits on socket-path length. These constraints reinforce why Java's endpoint abstraction is broader than IP address plus port.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-threads-network-io">Virtual Threads and Blocking Network I/O on Java 21</a>

<details>
<summary>Click for details</summary>

Before virtual threads, a common reason to choose a Selector was to avoid dedicating an expensive platform thread to every connection that might spend most of its time blocked. Java 21 changes that trade-off.

JEP 444 specifies that when code in a virtual thread blocks on most network I/O in `java.net` and `java.nio.channels`, the runtime can suspend the virtual thread and release its carrier platform thread for other work. **High connection concurrency therefore no longer automatically implies a move to non-blocking Selector code.**

On Java 21, this benefit assumes the virtual thread can unmount from its carrier. If surrounding code pins the virtual thread, for example while blocking in certain `synchronized` regions or native calls, the carrier can remain occupied for the duration. Detailed pinning mechanics belong to the Virtual Threads/Concurrency curriculum; Networking only needs this limit when comparing I/O models.

A thread-per-connection flow can remain sequential and readable:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor();
     ServerSocket server = new ServerSocket(8080)) {

    while (!server.isClosed()) {
        Socket socket = server.accept();
        executor.submit(() -> {
            try (socket) {
                handle(socket);
            }
            return null;
        });
    }
}
```

JDK 21 also specifies blocking operations on the system-default `Socket`, `ServerSocket`, and `DatagramSocket` as interruptible when invoked in a virtual thread: interrupting the blocked virtual thread wakes it and closes the socket. Channels already have their own `InterruptibleChannel` cancellation/interrupt semantics.

Virtual threads do not make network I/O itself faster and do not remove limits such as bandwidth, remote latency, connection pools, or downstream capacity. Selector remains useful for an explicit event-loop/readiness state machine; asynchronous channels remain useful when completion-style APIs or composition fit the surrounding design.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-io-model-choice">Choosing Blocking, Selector, or Asynchronous I/O</a>

<details>
<summary>Click for details</summary>

All three models are valid. Start the decision from the required **semantics and control**, rather than from an assumption that "NIO is always faster."

| Model | Good fit | Design cost to accept |
|---|---|---|
| Blocking socket/channel + thread or virtual thread per task | Sequential request/connection flow and readable code; Java 21 virtual threads allow much higher blocking-style concurrency | Still requires resource limits, timeout/cancellation, and bounded downstream load |
| Non-blocking `SelectableChannel` + `Selector` | One/few event-loop threads must multiplex many channels with explicit readiness and pending-buffer/state control | Application manages state machines, partial I/O, interest ops, fairness, and lifecycle |
| `Asynchronous*Channel` | The system composes naturally around completion callbacks/Futures or specifically needs an operation-completion API | Completion state, pending-operation rules, and channel-group lifecycle add complexity |

A practical decision path is:

```text
blocking flow meets the requirements
        ↓
keep the simpler blocking model; consider virtual threads on Java 21

explicit readiness multiplexing is required
        ↓
Selector + non-blocking channels

completion-oriented API is required
        ↓
Asynchronous channels
```

None of these models automatically solves application framing, backpressure policy, admission control, or concurrency correctness. NIO supplies I/O mechanisms; application-level policies remain with their appropriate owners.

After establishing these lower-level I/O models, the next chapter moves up to the JDK HTTP Client to show how a higher-level HTTP abstraction hides most socket/channel mechanics while preserving important completion, timeout, and resource-lifetime concerns.

</details>

- [Quay lại đầu trang](#back-to-top)
