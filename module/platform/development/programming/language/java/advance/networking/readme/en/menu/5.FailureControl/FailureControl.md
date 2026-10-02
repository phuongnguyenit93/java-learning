<a id="back-to-top"></a>

# Timeouts, Failures, and Network Resource Lifetime

## Menu
- [Where Network I/O Can Wait](#network-wait-points)
- [Connect, Accept, and Read Timeouts](#socket-timeouts)
- [Socket Options and the Transport/OS Boundary](#socket-options)
- [Failures During Resolve, Bind, Connect, and Transfer](#network-failure-model)
- [Ownership, Close, and Failure Cleanup](#network-resource-cleanup)
- [Interrupt, Cancellation, and Blocking Socket I/O](#network-cancellation)
- [Common Networking Pitfalls](#networking-pitfalls)

## <a id="network-wait-points">Where Network I/O Can Wait</a>

<details>
<summary>Click for details</summary>

Network code often appears “stuck” because it is **waiting for an external condition**, not because the JVM has stopped. Important waiting points include:

- host-name resolution;
- connecting to a remote endpoint;
- ServerSocket.accept() waiting for a new connection;
- InputStream.read() or DatagramSocket.receive() waiting for data;
- selector or asynchronous operations waiting through different readiness/completion models.

There is no single “network timeout” that covers the entire chain:

~~~text
resolve
  ↓
connect
  ↓
request / write
  ↓
read / receive
  ↓
close
~~~

Each stage has its own API and failure modes. Socket.connect(address, timeout) bounds connect, while Socket.setSoTimeout(...) bounds blocking reads; ServerSocket.setSoTimeout(...) bounds accept.

Before configuring a timeout, identify **which stage is supposed to be bounded**. Applying one number at the wrong boundary neither fixes the real hang nor produces useful diagnostics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-timeouts">Connect, Accept, and Read Timeouts</a>

<details>
<summary>Click for details</summary>

The JDK scopes timeouts to operations instead of exposing one global setting.

Connect timeout:

~~~java
Socket socket = new Socket();
socket.connect(
        new InetSocketAddress("example.com", 443),
        3_000
);
~~~

Read timeout on a classic Socket:

~~~java
socket.setSoTimeout(5_000);
int value = socket.getInputStream().read();
~~~

If the read would block beyond that interval, the JDK throws SocketTimeoutException. SO_TIMEOUT itself does not close the socket merely because it expires.

ServerSocket and DatagramSocket expose the same option for accept() and receive():

~~~java
serverSocket.setSoTimeout(5_000);
datagramSocket.setSoTimeout(5_000);
~~~

A common mistake is to treat SO_TIMEOUT as a universal write or DNS timeout. It is neither. Higher-level APIs such as HttpClient also define time bounds at their own client/request layers, so always interpret a timeout together with the API operation it controls.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="socket-options">Socket Options and the Transport/OS Boundary</a>

<details>
<summary>Click for details</summary>

Socket options are controls or hints that Java passes to the operating system's networking stack. They can affect socket behavior, but they should not be changed merely because a production template contains them.

Common examples include:

- TCP_NODELAY: affects delaying/coalescing small TCP writes;
- SO_KEEPALIVE: requests transport-level TCP keepalive;
- SO_SNDBUF / SO_RCVBUF: buffer-size hints;
- SO_REUSEADDR: affects local-address reuse in platform-dependent situations;
- SO_LINGER: changes close behavior when TCP output remains;
- SO_BROADCAST: enables UDP broadcast.

Modern NetworkChannel APIs also expose type-safe options:

~~~java
channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
~~~

Some options are only hints, and exact behavior can vary by operating system. Measure and understand the problem before tuning. Socket options do not replace application timeouts, retry policy, or application-level health checks.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-failure-model">Failures During Resolve, Bind, Connect, and Transfer</a>

<details>
<summary>Click for details</summary>

Network failures are easier to reason about when classified by lifecycle stage instead of being reduced to one generic IOException.

~~~text
resolve
→ UnknownHostException

bind
→ BindException / address in use / permission failure

connect
→ ConnectException / timeout / route failure

read-write
→ SocketTimeoutException / EOF / reset / IOException

closed resource
→ SocketException / ClosedChannelException
~~~

**EOF is not a timeout.** On a TCP stream, read returning -1 normally means the peer's output has reached end-of-stream from the local side's perspective. A timeout means data did not arrive within the configured wait. A reset is a different termination mode again.

Context matters. A ConnectException to a known host and port tells a different story from an UnknownHostException before an IP address exists.

Useful logging retains the stage, operation, and local/remote endpoints without dumping secret payloads. Good classification also helps decide what may be retried, what is a configuration error, and what belongs to a higher-level resilience policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-resource-cleanup">Ownership, Close, and Failure Cleanup</a>

<details>
<summary>Click for details</summary>

Sockets and channels own operating-system resources. Garbage collection is not an appropriate connection-lifecycle strategy. Code should have a **clear owner** responsible for close.

For scope-bound resources, prefer try-with-resources:

~~~java
try (Socket socket = new Socket("localhost", 8080);
     InputStream in = socket.getInputStream()) {

    // communicate
}
~~~

Closing a Socket closes the streams associated with that socket. Closing a channel makes subsequent I/O fail because the channel is no longer open.

Cleanup must also happen when:

- connection setup succeeds but protocol setup fails;
- read or write throws;
- a task is cancelled;
- the application abandons an operation.

Avoid contracts where several layers all believe they own the same socket lifecycle. If a library receives a Socket from its caller, the contract should state whether the library closes it.

Clear ownership becomes essential for cancellation, orderly shutdown, and connection reuse in later chapters.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-cancellation">Interrupt, Cancellation, and Blocking Socket I/O</a>

<details>
<summary>Click for details</summary>

Cancelling network I/O should answer two questions: **which operation must stop, and is the resource still usable afterward?**

In Java 21, the system-default implementations of Socket, ServerSocket, and DatagramSocket have an important behavior when blocking I/O runs in a **virtual thread**: interrupting a virtual thread blocked on socket I/O wakes the thread and closes the socket.

~~~java
Thread worker = Thread.ofVirtual().start(() -> {
    try (Socket socket = new Socket("localhost", 8080)) {
        socket.getInputStream().read(); // may block
    } catch (IOException e) {
        // cancellation / close is observed here
    }
});

worker.interrupt();
~~~

That behavior is why cancellation must be part of resource-lifetime reasoning. Do not catch the resulting failure and continue under the assumption that the same socket remains usable.

InterruptibleChannel APIs have their own interruption/asynchronous-close semantics, which the NIO chapter builds on.

The module-level rule is simple: cancellation is not merely “stop the thread”; it must leave the system in a **defined resource state**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-pitfalls">Common Networking Pitfalls</a>

<details>
<summary>Click for details</summary>

Recurring Java networking failures usually come from a wrong mental model rather than difficult syntax:

1. **No time bound on an operation that can wait**  
   A request path can retain resources indefinitely when a peer stops responding.

2. **Treating one TCP read as one message**  
   TCP is a byte stream; message framing belongs to the application protocol.

3. **Treating UDP connect as a reliable connection**  
   DatagramSocket.connect associates/filters a peer; it does not add delivery guarantees.

4. **Tuning socket options by recipe**  
   Buffers, linger, keepalive, and TCP_NODELAY only make sense when the bottleneck and OS behavior are understood.

5. **Catching IOException and retrying everything**  
   Unknown host, refused connection, timeout, and invalid application data are different failures.

6. **Leaking sockets or leaving ownership ambiguous**  
   File-descriptor exhaustion eventually becomes a production outage.

7. **Assuming non-blocking is always superior**  
   Selector-based designs carry state-management cost; Java 21 virtual threads make blocking style practical for many I/O-bound workloads.

The next chapter introduces NIO so that its additional complexity can be chosen for a reason rather than by default.

</details>

- [Quay lại đầu trang](#back-to-top)
