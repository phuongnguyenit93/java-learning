<a id="back-to-top"></a>

# Java Networking Mental Model

## Menu
- [What Java Networking Is and Why It Exists](#networking-purpose)
- [Hosts, Addresses, Ports, and Network Endpoints](#network-endpoint-model)
- [Transport and Data Models](#transport-and-data-model)
- [Blocking, Non-Blocking, and Asynchronous Completion](#io-completion-models)
- [Java Networking Module Boundary](#networking-module-boundary)

## <a id="networking-purpose">What Java Networking Is and Why It Exists</a>

<details>
<summary>Click for details</summary>

Networking lets a Java program exchange data with **another process through a network endpoint**. The peer may run on the same machine, on a nearby host, or in a remote data center. Unlike local memory or file access, network I/O crosses a boundary outside the JVM: it has latency, depends on operating-system resources, can fail midway, and must be closed deliberately.

Without networking, an application is limited to local computation or a different IPC mechanism. Networking solves a broader chain of problems:

~~~text
independent peers
→ find one another
→ establish a communication model
→ transfer data
→ handle failure
→ release resources
~~~

The JDK offers several abstraction levels. Low-level APIs include Socket, ServerSocket, DatagramSocket, and network channels. Higher-level APIs include HttpClient and the WebSocket client. The goal of this module is not to memorize classes; it is to understand **which part of the communication problem each abstraction owns**.

A useful mental model is:

~~~text
application intent
    ↓
protocol / API abstraction
    ↓
endpoint + transport
    ↓
network I/O
    ↓
remote process
~~~

When debugging network code, start with four questions: which endpoint is involved, which transport is in use, which operation can be waiting, and which resource must eventually be closed?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="network-endpoint-model">Hosts, Addresses, Ports, and Network Endpoints</a>

<details>
<summary>Click for details</summary>

A network endpoint contains enough information to locate **one side of a communication**. For Internet sockets, the common mental model is:

~~~text
host/IP + port
~~~

A host name such as <code>example.com</code> is a logical name that normally has to be resolved to one or more IP addresses. A port identifies a transport endpoint on the target host; many services can share one IP address by listening on different ports.

Java keeps these concerns separate:

- InetAddress represents an IP address and may also know a host name;
- InetSocketAddress represents an Internet socket address, typically host/IP + port;
- SocketAddress is the base abstraction for socket addresses;
- UnixDomainSocketAddress represents a local Unix-domain endpoint by path.

Do not equate a URL with a socket endpoint. A URL also carries a scheme, path, query, and resource semantics. For example, <code>https://example.com/orders?id=42</code> contains much more information than the transport endpoint used by the eventual connection.

This distinction becomes the foundation for later chapters: name resolution produces addresses, TCP and UDP use endpoints, and HTTP/WebSocket add higher-level semantics over connections.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transport-and-data-model">Transport and Data Models</a>

<details>
<summary>Click for details</summary>

A transport determines **the basic communication model visible to the application**.

TCP exposes an **ordered byte stream**. It does not preserve application message boundaries. Two writes by the sender do not imply two matching reads by the receiver, so framing must come from the application protocol: a delimiter, a length prefix, or another structured format.

UDP exposes **datagrams**. Each datagram preserves its own boundary, but UDP does not provide the same delivery and ordering guarantees as TCP. Applications that need more reliability must define it above UDP.

Higher-level APIs add richer semantics:

~~~text
TCP byte stream
    ↑
HTTP request / response

TCP connection
    ↑
WebSocket messages
~~~

Therefore “sending data over the network” is not one universal operation. Before choosing an API, decide whether the application needs a byte stream, independent datagrams, request/response exchanges, or a message-oriented conversation. Choosing the wrong data model often creates unnecessary framing, retry, and lifecycle complexity.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="io-completion-models">Blocking, Non-Blocking, and Asynchronous Completion</a>

<details>
<summary>Click for details</summary>

Three terms are frequently mixed together: **blocking**, **non-blocking**, and **asynchronous completion**.

With **blocking I/O**, a call may keep the current thread waiting until the operation can make progress or completes. Reading from a socket input stream can wait for data.

With **non-blocking I/O**, the operation returns when it cannot make progress immediately. A non-blocking SocketChannel is commonly paired with a Selector so the program can discover which channels are ready.

With **asynchronous I/O**, code starts an operation and receives its result later through a Future or CompletionHandler, as with AsynchronousSocketChannel.

~~~text
blocking       → wait in the call
non-blocking   → call returns; observe readiness
asynchronous   → operation completes later
~~~

These are I/O models, not complete application concurrency architectures. Java 21 adds an important option: blocking network I/O in a virtual thread can suspend that virtual thread rather than monopolize its carrier. Blocking therefore does not automatically mean “cannot scale”.

Networking uses concurrency concepts only to explain I/O behavior. Scheduling, synchronization, executors, and the Java Memory Model remain owned by the Concurrency area.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-module-boundary">Java Networking Module Boundary</a>

<details>
<summary>Click for details</summary>

This module owns **JDK networking APIs and their runtime mechanics from application code**:

- addressing and name resolution;
- TCP and UDP sockets;
- socket options, timeouts, failures, and resource lifetime;
- NIO network channels, selectors, and asynchronous channels;
- the JDK HTTP Client and WebSocket client;
- choosing among networking abstractions.

Several neighboring topics appear only to make the boundary explicit:

- TLS/JSSE, certificates, and key material → Security & Cryptography;
- scheduling, executors, and the virtual-thread model → Concurrency;
- HTTP API design, REST conventions, retries, and resilience policy → Integration / Resilience;
- WebSocket protocol architecture, reconnect policy, STOMP, and messaging semantics → Realtime Integration;
- gateways, proxy architecture, service meshes, and routing → Infrastructure / Network.

This prevents a common learning mistake: seeing one HTTPS request with JSON, retries, and TLS and labeling all of it “Java Networking”. Networking explains the connection/API mechanics; the other concerns have their own owners.

After this entry chapter, the learning flow should feel clear: **endpoint → transport → failure/lifecycle → NIO model → HTTP/WebSocket → decision trade-offs**.

</details>

- [Quay lại đầu trang](#back-to-top)
