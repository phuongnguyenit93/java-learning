# Spring Messaging

Spring Messaging is the Spring Framework foundation for message-oriented application communication. This module focuses on three related programming models that share Spring messaging infrastructure:

```text
Spring Messaging core abstractions
→ WebSocket + STOMP application messaging
→ Spring RSocket requester/responder programming model
```

The goal is not to memorize annotations or protocol commands. The goal is to understand how messages move through Spring, how application handlers are selected, where broker or transport infrastructure participates, and where responsibility transfers to another Spring project.

## Prerequisites

Before this module, learners should be comfortable with:

- Spring container and dependency-injection fundamentals;
- basic Java concurrency concepts such as executors and asynchronous execution;
- HTTP request/response and the idea of an HTTP Upgrade;
- reactive-programming and backpressure fundamentals before the RSocket chapters.

Deep Reactor mechanics, Spring Security policy, Spring Session internals, broker-native administration, and Enterprise Integration Patterns are intentionally not prerequisites taught again here.

## Learning flow

The menu is ordered as one continuous learning journey:

1. establish why messaging exists and the module boundary;
2. learn the transport-neutral `Message`, channel, handler, conversion, and error model;
3. connect that model to Spring's Servlet-stack WebSocket support;
4. learn STOMP semantics and how STOMP frames become Spring messages;
5. trace the end-to-end Spring STOMP message flow;
6. learn annotated application handling and programmatic sending;
7. understand broker routing and user destinations;
8. learn the Spring STOMP client and connection lifecycle;
9. study STOMP/WebSocket lifecycle events, interception, ordering, and security/session boundaries;
10. build the minimum RSocket interaction model required by Spring;
11. learn `RSocketRequester` and `RSocketStrategies`;
12. learn annotated RSocket responders and declarative RSocket service interfaces;
13. finish with operations, testing, scaling, and architecture decisions.

The chapter order is pedagogical. WebSocket/STOMP does not technically depend on RSocket, and RSocket does not depend on STOMP. They are separate application-messaging models that reuse parts of the same Spring messaging foundation.

## Module boundaries

This module owns Spring Framework messaging mechanics:

- `Message`, headers, channels, handlers, conversion, and interception;
- Spring WebSocket abstractions used by the Servlet stack;
- STOMP-over-WebSocket application messaging;
- simple broker and STOMP broker-relay integration at the Spring Framework boundary;
- user destinations, STOMP client support, ordering, and messaging lifecycle;
- Spring RSocket requester/responder APIs, routing/metadata, annotated handling, and RSocket service interfaces.

It does not own:

- Enterprise Integration Patterns, gateways, routers, transformers, splitters, aggregators, or integration adapters — those belong to Spring Integration;
- Kafka or AMQP-specific producer/consumer semantics — those belong to Spring Kafka and Spring AMQP;
- authentication and authorization policy — that belongs to Spring Security;
- distributed HTTP-session management — that belongs to Spring Session;
- Reactor and Reactive Streams fundamentals — those belong to the reactive-learning owner;
- raw WebSocket or deep RSocket protocol internals beyond what is needed to understand Spring behavior.

By the end of the module, a learner should be able to trace a message end to end, choose deliberately between raw WebSocket, STOMP, and RSocket, understand when a simple broker or broker relay is appropriate, and recognize when the problem should be handed off to Spring Integration or another neighboring module.
