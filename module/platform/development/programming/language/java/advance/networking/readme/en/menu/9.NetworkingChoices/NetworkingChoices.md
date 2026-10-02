<a id="back-to-top"></a>

# Choosing Networking Abstractions and Preserving Boundaries

## Menu
- [A Decision Model for Choosing Networking APIs](#networking-choice-model)
- [Choosing TCP, UDP, or Higher-Level APIs](#transport-choice)
- [Blocking I/O, Virtual Threads, and Event-Driven I/O](#blocking-concurrency-choice)
- [TLS/JSSE Boundary with Security & Cryptography](#networking-security-boundary)
- [Retry, Resilience, and Application-Policy Boundary](#networking-resilience-boundary)
- [Handoff to HTTP/WebSocket Integration](#networking-integration-handoff)
- [End-to-End Networking Mental Model](#networking-end-to-end-model)

## <a id="networking-choice-model">A Decision Model for Choosing Networking APIs</a>

<details>
<summary>Click for details</summary>

At the end of the module, the useful question is no longer “which classes exist?” but **which abstraction matches the current communication problem?**

A practical decision model has five dimensions:

1. **Data semantics** — byte stream, datagrams, request/response, or a message-oriented connection?
2. **Peer relationship** — one long-lived connection or independent interactions?
3. **Concurrency model** — blocking thread-per-task, selector readiness, or asynchronous completion?
4. **Control level** — must the application own framing/socket options, or should a higher-level API own the protocol?
5. **Lifecycle/failure** — who owns timeout, cancellation, close, and retry policy?

~~~text
problem
 ↓
communication semantics
 ↓
required control
 ↓
concurrency/lifecycle constraints
 ↓
smallest suitable JDK abstraction
~~~

A lower-level API is not automatically faster, and a higher-level API is not automatically simpler in every case. Choose **the lowest abstraction level that is necessary, but no lower**.

If the task is simply to call an HTTP endpoint, manually opening a Socket and implementing HTTP framing adds unnecessary complexity. If the task is a custom binary TCP protocol, HttpClient is the wrong abstraction.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transport-choice">Choosing TCP, UDP, or Higher-Level APIs</a>

<details>
<summary>Click for details</summary>

Start with data semantics rather than API familiarity.

**TCP / Socket**

- ordered byte stream;
- connection-oriented communication;
- application-owned framing/protocol;
- direct connection lifecycle control.

**UDP / Datagram**

- independent datagram boundaries;
- transport can lose, reorder, or duplicate traffic;
- useful where connection-oriented stream semantics are not a fit;
- reliability above UDP, if required, belongs to the application protocol.

**HttpClient**

- the problem is naturally HTTP request/response;
- reusable client configuration, redirects, proxies, authentication, body handling, HTTP/1.1 or HTTP/2 are useful;
- the application should not implement HTTP framing itself.

**WebSocket client**

- a long-lived bidirectional message-oriented connection is required;
- the server speaks WebSocket;
- the application accepts an event/listener lifecycle.

Quick map:

~~~text
custom stream protocol      → TCP
independent datagrams       → UDP
HTTP resource interaction   → HttpClient
bidirectional WS messaging  → WebSocket client
~~~

Requirements such as “real-time” or “fast” are not sufficient by themselves. Delivery semantics, lifetime, and application protocol still have to be identified.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocking-concurrency-choice">Blocking I/O, Virtual Threads, and Event-Driven I/O</a>

<details>
<summary>Click for details</summary>

Concurrency model and network API influence each other, but they are not the same decision.

Classic blocking code often has a direct control flow:

~~~text
accept/connect
→ read
→ process
→ write
~~~

Before virtual threads, one blocking platform thread per connection could become expensive at very high concurrency. Selector-based non-blocking I/O multiplexes many channels over fewer threads, but in exchange the application owns readiness handling and per-connection state more explicitly.

Java 21 changes that trade-off: a virtual thread blocked on supported network I/O can suspend without holding its carrier thread. Therefore:

~~~text
many concurrent connections
≠ automatically require Selector
~~~

Choose **blocking + virtual threads** when:

- the workflow is naturally sequential;
- thread-per-task keeps code clearer;
- readiness-level control is not required.

Choose **Selector/event-driven I/O** when:

- the architecture is already event-loop based;
- explicit readiness multiplexing/control is valuable;
- the application intentionally owns connection state machines.

Choose **asynchronous channels** when completion-style operations fit the surrounding architecture.

Do not decide this from a synthetic microbenchmark alone. Throughput, latency, memory, scheduling behavior, and code complexity all matter in the real workload.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-security-boundary">TLS/JSSE Boundary with Security & Cryptography</a>

<details>
<summary>Click for details</summary>

Networking needs to know **where TLS sits in the stack**, but it does not own the complete security curriculum.

Mental model:

~~~text
application protocol
        ↓
TLS secure transport
        ↓
TCP/network transport
~~~

JSSE provides JDK abstractions such as SSLContext, SSLSocket, SSLServerSocket, and SSLEngine. HttpClient/WebSocket over HTTPS/WSS can use TLS underneath without application code manipulating TLS records directly.

Networking needs the operational consequences:

- handshake can fail before application data is exchanged;
- endpoint identity/certificate validation can prevent a connection from succeeding;
- TLS has its own configuration and lifetime.

The following belong to **Security & Cryptography**:

- trust stores and key stores;
- certificate chains and X.509;
- cipher suites and protocol security;
- key material;
- custom TrustManager/KeyManager behavior;
- cryptographic correctness.

When SSLHandshakeException occurs, Networking helps locate the failure at the security setup stage after/beside transport establishment; the certificate/trust cause belongs to the Security module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-resilience-boundary">Retry, Resilience, and Application-Policy Boundary</a>

<details>
<summary>Click for details</summary>

Networking detects failures; **resilience policy decides what should happen after them**.

Avoid the simplistic rule:

~~~text
catch IOException
→ retry forever
~~~

Safe retry depends on information above transport:

- is the operation idempotent?
- did the request reach the server before the response was lost?
- was the timeout during connect or read?
- should backoff/jitter be applied?
- is there a retry budget or circuit breaker?
- is the error transient or a permanent configuration failure?

An HTTP POST may already have changed server state before the client loses the connection. Retrying merely because an IOException occurred can duplicate side effects.

Networking should:

- expose/classify failures well enough for callers;
- keep timeout, cancellation, and resource state explicit;
- preserve the original cause.

The resilience/integration layer owns retry, circuit breakers, fallback, deadline propagation, and idempotency strategy.

This boundary prevents a transport component from guessing whether a business operation is safe to repeat.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-integration-handoff">Handoff to HTTP/WebSocket Integration</a>

<details>
<summary>Click for details</summary>

Java Networking ends at **client API mechanics and transport behavior**. Once the application cares about the meaning of a request or message, responsibility has moved into an integration layer.

For HTTP, Networking owns:

- HttpClient configuration and reuse;
- HttpRequest/HttpResponse mechanics;
- body publisher/subscriber behavior;
- timeout/proxy/authenticator mechanics.

Integration owns:

- REST/resource design;
- API contracts and status-code policy;
- serialization/domain schema;
- idempotency/retry semantics;
- service-to-service communication patterns.

For WebSocket, Networking owns listener, demand, fragmentation, send, close, and error lifecycle. Realtime Integration owns reconnect strategy, subscription/topic models, STOMP/broker semantics, and the application message protocol.

Mental handoff:

~~~text
Networking
→ "How does Java send and receive?"

Integration
→ "What contract are the applications using?"
~~~

This keeps Java Advanced deep in runtime/API mechanics without duplicating the Microservice/Integration curriculum.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="networking-end-to-end-model">End-to-End Networking Mental Model</a>

<details>
<summary>Click for details</summary>

A complete network interaction can be reasoned about as one chain:

~~~text
1. application has a communication intent
        ↓
2. choose protocol/API abstraction
        ↓
3. host name / resource identifier
        ↓
4. name resolution
        ↓
5. concrete local + remote endpoint
        ↓
6. bind / connect / opening handshake
        ↓
7. data transfer or async completion
        ↓
8. timeout / failure / cancellation handling
        ↓
9. close / reuse / release resource
~~~

When diagnosing a production issue, walking backward through this flow is more useful than changing options at random:

~~~text
Did the name resolve correctly?
→ Is the endpoint correct?
→ Did connect/handshake succeed?
→ Which stage is waiting?
→ Is the data model/framing correct?
→ Did the peer close or reset?
→ Which layer owns timeout/cancellation?
→ Was the resource closed or reused correctly?
~~~

After this module, a learner does not need to memorize every java.net method. The durable skill is placing APIs in the right mental model and answering:

- which layer does this abstraction solve?
- where can this operation wait?
- which lifecycle stage owns this failure?
- who owns the resource lifetime?
- when should the concern be handed to Concurrency, Security, or Integration?

That model remains useful even as individual JDK APIs evolve in later Java releases.

</details>

- [Quay lại đầu trang](#back-to-top)
