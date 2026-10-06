<a id="back-to-top"></a>

# Spring Messaging: Purpose and Boundaries

## Menu
- [What Is Messaging and Why Does It Exist?](#messaging-purpose)
- [Direct coupling and request-response limits](#direct-coupling-limits)
- [Spring Messaging as a transport-neutral foundation](#spring-messaging-foundation)
- [Messaging programming models in this module](#messaging-programming-models)
- [Boundaries with neighboring Spring modules](#neighboring-module-boundaries)
- [Recommended learning path](#messaging-learning-path)

## <a id="messaging-purpose">What Is Messaging and Why Does It Exist?</a>

<details>
<summary>Click for details</summary>

Applications often need to move information between components that should not know each other's implementation details. A direct method call is excellent when both sides live in the same call path and the caller needs an immediate result. Messaging becomes useful when the communication itself deserves an explicit contract: a payload plus metadata can be handed to infrastructure, routed, transformed, queued, or delivered to one or more consumers without the producer invoking those consumers directly.

Spring Framework models that contract with Message<?> and related abstractions. The key idea is decoupling at the communication boundary. The producer creates or sends a message; infrastructure decides how that message reaches a handler. That does not automatically make the system durable, distributed, asynchronous, or broker-backed. Those properties depend on the concrete channel, transport, broker, and application design.

In this module, messaging is the common foundation underneath Spring's WebSocket/STOMP and RSocket programming models. Later chapters move from the generic Message model to long-lived WebSocket sessions, STOMP destinations and brokers, and finally requester/responder interaction. Keeping that order prevents protocol details from hiding the simpler question: what information is moving, and which component is responsible for handling it?

</details>

- [Back to top](#back-to-top)

---

## <a id="direct-coupling-limits">Direct coupling and request-response limits</a>

<details>
<summary>Click for details</summary>

Direct calls and HTTP request-response are intentionally coupled in time: the caller invokes an operation and normally waits for that operation to complete or fail. That shape is easy to reason about and is often the best choice. Problems appear when a caller should not own the callee's lifecycle, when several consumers need the same event, when work may continue after the initiating request ends, or when a long-lived bidirectional connection is a better interaction model.

Messaging introduces an intermediate contract. The sender can address a logical destination or channel and let infrastructure select the next handler. This can reduce structural coupling, but it also introduces new concerns: ordering, conversion, delivery failures, connection state, backpressure, or broker behavior may now matter.

A useful rule is to choose messaging because the communication model fits, not because asynchronous execution is assumed to be faster. A Spring MessageChannel can deliver synchronously on the sender's thread, and a WebSocket exchange can still require an immediate application response. Messaging changes the shape of collaboration; concurrency and distribution are separate decisions.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-messaging-foundation">Spring Messaging as a transport-neutral foundation</a>

<details>
<summary>Click for details</summary>

The spring-messaging module provides transport-neutral building blocks that other Spring technologies can reuse. At the center is Message<T>, which exposes a payload and MessageHeaders. A MessageChannel represents a sending boundary, while MessageHandler represents code that consumes a message. SubscribableChannel adds a registry of handlers that can be invoked when a message is sent.

This layer deliberately does not define WebSocket frames, STOMP commands, broker queues, or RSocket interaction types. Those concepts sit above or beside the generic model. The benefit is that message handling, conversion, header access, interception, and annotation support can share a common vocabulary even when the transport changes.

That distinction is important for scope. Spring Integration also uses Spring Messaging primitives, but Enterprise Integration Patterns such as routers, filters, splitters, aggregators, and integration gateways belong to Spring Integration. This module owns the Framework primitives and the WebSocket/STOMP/RSocket programming models that build directly on them.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-programming-models">Messaging programming models in this module</a>

<details>
<summary>Click for details</summary>

The module uses three related programming models:

1. **Spring Messaging foundation** — Message, headers, channels, handlers, conversion, interception, and common handler-method infrastructure.
2. **WebSocket + STOMP application messaging** — a long-lived WebSocket transport with STOMP as the application-level messaging protocol, plus Spring's broker-routing and annotated-controller model.
3. **Spring RSocket requester/responder model** — request-response, fire-and-forget, streaming, routing, metadata, and Spring's requester/responder APIs.

Raw WebSocket and STOMP are not the same layer. WebSocket supplies a full-duplex transport whose text or binary payload has no application semantics by itself. STOMP adds commands, destinations, subscriptions, headers, acknowledgements, receipts, and broker-oriented semantics. RSocket is a separate protocol and programming model with requester/responder symmetry and Reactive Streams-based streaming interactions.

The shared Spring messaging abstractions make these models easier to relate, but they should not be collapsed into one protocol. When debugging, always ask which layer currently owns the behavior: generic message infrastructure, WebSocket transport, STOMP routing/broker behavior, or RSocket interaction semantics.

</details>

- [Back to top](#back-to-top)

---

## <a id="neighboring-module-boundaries">Boundaries with neighboring Spring modules</a>

<details>
<summary>Click for details</summary>

Spring Messaging sits next to several Spring projects whose responsibilities must stay distinct.

- **Spring Integration** owns Enterprise Integration Patterns and flow composition. This module teaches the Message/channel foundation those flows may reuse.
- **Spring Security** owns authentication and authorization policy. Messaging exposes identity and interception points that security can integrate with, but policy design belongs there.
- **Spring Session** owns HTTP-session lifecycle and distributed session management. WebSocket activity can participate in that lifecycle through integration, but session storage policy is outside this module.
- **Spring Web MVC / WebFlux** own their request-response stacks. This module covers the Servlet-stack Spring WebSocket API used by Framework messaging and only identifies the reactive WebSocket boundary.
- Broker products own broker-native durability, acknowledgement, queue lifecycle, clustering, and vendor-specific behavior beyond Spring's broker relay boundary.

These handoffs are intentional. They keep the learner's mental model stable: understand the Spring Framework messaging mechanism here, then follow a neighboring module when the problem becomes security policy, EIP topology, reactive fundamentals, or broker administration.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-learning-path">Recommended learning path</a>

<details>
<summary>Click for details</summary>

Start with the transport-neutral model before touching STOMP configuration. First learn what a Message is, how channels and handlers connect producers to consumers, how conversion happens, and where thread boundaries can appear. That vocabulary is reused by the rest of the module.

Then move to raw Spring WebSocket. Learn the HTTP upgrade, WebSocketHandler, WebSocketSession, client APIs, origin rules, and SockJS fallback. This establishes what the transport can and cannot provide by itself.

After that, add STOMP. Learn frames and destinations before studying Spring's clientInboundChannel, brokerChannel, and clientOutboundChannel. With that flow in place, annotated handlers such as @MessageMapping, broker routing, user destinations, client lifecycle, interception, ordering, and operational concerns become much easier to reason about.

RSocket comes later because it is a different application-messaging model. The module teaches only the interaction and reactive concepts necessary to understand Spring's requester/responder APIs. By the end, the learner should be able to trace a message end to end and identify the layer that owns each routing, lifecycle, conversion, or delivery decision.

</details>

- [Back to top](#back-to-top)
