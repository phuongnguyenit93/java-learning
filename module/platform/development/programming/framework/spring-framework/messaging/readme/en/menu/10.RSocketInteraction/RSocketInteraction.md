<a id="back-to-top"></a>

# RSocket Interaction Model and Reactive Boundary

## Menu
- [1. Why RSocket exists](#rsocket-purpose)
- [2. Request-response](#rsocket-request-response)
- [3. Fire-and-forget](#rsocket-fire-and-forget)
- [4. Request-stream](#rsocket-request-stream)
- [5. Request-channel](#rsocket-request-channel)
- [6. Requester and responder symmetry](#rsocket-symmetry)
- [7. Setup and connection model](#rsocket-setup-connection)
- [8. Routes and metadata](#rsocket-routes-metadata)
- [9. Reactive Streams and backpressure boundary](#rsocket-reactive-streams-boundary)
- [10. TCP, WebSocket, and protocol-feature boundary](#rsocket-transports-boundary)

## <a id="rsocket-purpose">1. Why RSocket exists</a>

<details>
<summary>Click for details</summary>

RSocket is an application protocol for asynchronous, multiplexed, duplex communication. Instead of treating every exchange as the same request/response shape, it defines four interaction models: request-response, fire-and-forget, request-stream, and request-channel. The protocol carries application data and metadata in binary frames and can run over byte-stream transports such as TCP or WebSocket.

The reason it belongs in Spring Messaging is the programming model around those interactions. Spring maps Java objects and reactive types to RSocket payloads, routes, metadata, and response streams. For streaming interactions, RSocket also carries Reactive Streams demand across the network so a consumer can slow the producer near the source instead of relying only on queues and transport congestion.

RSocket is most useful when an application needs many concurrent logical interactions on a long-lived connection, server-initiated requests, or streaming in one or both directions. It is not a durable message broker: protocol flow control does not provide persisted queues, replay after an outage, or broker-managed delivery guarantees.

The key design question is therefore not “Should every WebSocket become RSocket?” but “Does the application need RSocket's interaction and flow-control semantics?” If the requirement is only browser text/binary frames or STOMP destinations and subscriptions, the WebSocket/STOMP models in earlier chapters remain simpler.

### References

- [Spring Framework — RSocket](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [RSocket protocol](https://rsocket.io/about/protocol/)

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-request-response">2. Request-response</a>

<details>
<summary>Click for details</summary>

Request-response is the one-input, one-output interaction. The requester sends one request payload and expects at most one response payload, an error, or cancellation. It looks familiar to HTTP-style request/response, but the exchange is asynchronous and multiplexed with other RSocket streams on the same connection.

In Spring, a request such as:

```java
Mono<Status> status = requester
        .route("device.status")
        .data(new StatusRequest("d-42"))
        .retrieveMono(Status.class);
```

does not require one connection per call. Subscription initiates reactive work, and cancellation of the returned publisher can cancel the RSocket stream if the response is no longer needed.

Choose request-response when the semantic contract is genuinely one result: lookup, command-with-result, validation, or acknowledgement carrying domain information. Do not turn a naturally streaming result into one giant collection only to preserve a one-response API; request-stream is a better fit when results should be consumed incrementally.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-fire-and-forget">3. Fire-and-forget</a>

<details>
<summary>Click for details</summary>

Fire-and-forget is a one-way interaction. The requester sends a payload and does not receive an application response for that request stream. It is useful for signals where the sender does not need a business result, such as low-value telemetry, hints, or idempotent notifications whose loss is acceptable to the application.

With Spring's `RSocketRequester`, `send()` returns `Mono<Void>`. Completion means the send operation completed successfully from the requester's point of view; it is not an acknowledgement that the responder executed application logic.

```java
Mono<Void> sent = requester
        .route("telemetry.sample")
        .data(sample)
        .send();
```

That distinction matters operationally. The RSocket protocol describes fire-and-forget as best effort. If the sender needs explicit application confirmation, use a request-response contract that returns such a confirmation. Stronger requirements such as durable redelivery, at-least-once processing, or effective exactly-once business behavior require additional persistence, retry, deduplication/idempotency, or a durable messaging system; request-response by itself does not create those guarantees.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-request-stream">4. Request-stream</a>

<details>
<summary>Click for details</summary>

Request-stream sends one request and receives zero or more response payloads. It fits queries whose results arrive over time or are naturally incremental: search results, status updates, event feeds, or a large result set that should not be materialized into one response.

```java
Flux<Reading> readings = requester
        .route("sensor.readings")
        .data(new SensorQuery("s-9"))
        .retrieveFlux(Reading.class);
```

The important protocol property is demand. The requester grants response credit through RSocket flow-control frames such as `REQUEST_N`; the responder must not emit beyond the granted demand. Cancellation terminates the stream when the requester no longer wants data.

This model can represent both finite and long-lived streams. The application still owns lifecycle policy: timeouts, reconnect behavior, whether missed events are replayable, and how errors are surfaced. Backpressure controls the live stream; it does not imply persistent history.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-request-channel">5. Request-channel</a>

<details>
<summary>Click for details</summary>

Request-channel is the RSocket interaction for a **multi-value request input**. The requester opens the channel and can continue sending payloads while the responder may produce zero, one, or many response values. The common many-to-many case behaves like two coordinated streams rather than “a request with a long response.”

For annotated responders, Spring maps a multi-value input to request-channel for output cardinalities of zero, one, or many. A typical many-to-many requester sends a `Flux<Command>` and retrieves a `Flux<Result>`:

```java
Flux<Result> results = requester
        .route("session.commands")
        .data(commands, Command.class)
        .retrieveFlux(Result.class);
```

Request-channel is useful when information from the requester continues to change while responses are also flowing, for example live collaboration state, changing subscriptions, or bidirectional device control. Design cancellation and completion explicitly: one side completing its outbound stream does not mean the other side has already completed.

Avoid using a channel merely because the transport is duplex. If one request produces many responses and the requester sends no further data, request-stream communicates the contract more clearly.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-symmetry">6. Requester and responder symmetry</a>

<details>
<summary>Click for details</summary>

“Client” and “server” describe how the connection is established. After setup, an RSocket connection is symmetric: either peer can initiate a new request and therefore act as the requester for that stream, while the other peer acts as responder.

Spring exposes that symmetry directly. A server-side `@ConnectMapping` or `@MessageMapping` method may receive an `RSocketRequester` for the connected peer and use it to initiate requests back to the client. A client can also register an annotated responder through `RSocketMessageHandler.responder(...)` and the requester's underlying connector.

This changes how APIs should be named and reasoned about. “Requester” is a role for one interaction, not a synonym for browser/client, and “responder” is not permanently the server. A single peer can be requester on one stream and responder on another concurrently.

The practical consequence is that connection ownership and request ownership are separate concerns. Keep authorization, route contracts, and lifecycle rules valid for both directions if the application enables server-to-client requests.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-setup-connection">7. Setup and connection model</a>

<details>
<summary>Click for details</summary>

An RSocket connection begins with setup. The connecting side sends a `SETUP` frame that establishes connection-wide settings such as data and metadata MIME types, and the responder can accept or reject the connection. Spring's `RSocketRequester.Builder` prepares the underlying `RSocketConnector` and setup payload when connecting over TCP or WebSocket.

Setup is a connection lifecycle event, distinct from later request streams. In Spring's annotated model, `@ConnectMapping` handles the initial setup and later metadata-push notifications. For a new server-side connection, an asynchronous `@ConnectMapping` that returns an error rejects the connection.

```java
@ConnectMapping
Mono<Void> connected(RSocketRequester peer) {
    return registrationService.register(peer);
}
```

Do not block setup waiting for a request made back through the same connection. The setup must complete before normal requests can proceed. If the server needs to initiate work immediately, decouple that outbound request from setup handling, as the Spring reference recommends.

Connection settings such as keepalive, resumption, interceptors, and other protocol options belong to the underlying RSocket connector/server configuration. They should be chosen as connection policy, not hidden inside individual route handlers.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-routes-metadata">8. Routes and metadata</a>

<details>
<summary>Click for details</summary>

RSocket separates data from metadata. Spring commonly uses routing metadata to select an annotated handler and composite metadata when a request needs several independently typed metadata entries such as route, tracing information, or an authentication token.

The data MIME type and metadata MIME type are negotiated for the connection during setup. Per-request metadata is then carried with request frames. A route is application metadata; it is not an HTTP URL. Spring route matchers use `.` as the default separator, and RSocket routing does not perform HTTP-style URL decoding.

```java
Flux<Item> items = requester
        .route("catalog.find.{category}", "books")
        .metadata(token, authenticationMimeType)
        .data(query)
        .retrieveFlux(Item.class);
```

On the responder, routing metadata becomes the destination used for `@MessageMapping` or `@RSocketExchange`; other registered metadata values can be decoded and exposed as headers. Keep route names stable and semantic. Do not overload the data payload with protocol concerns that belong in metadata, and do not assume arbitrary metadata is usable until matching encoders/decoders and extractor registrations exist.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-reactive-streams-boundary">9. Reactive Streams and backpressure boundary</a>

<details>
<summary>Click for details</summary>

RSocket's reactive boundary is stronger than “the Java API returns `Flux`.” For request-stream and request-channel, demand is represented on the wire through RSocket flow control. A downstream subscriber that asks for fewer items can ultimately reduce how many payloads the remote peer is allowed to send.

Spring preserves that model by adapting supported reactive types through `ReactiveAdapterRegistry` and by using Reactor types naturally in requesters and annotated responders. This allows a stream to remain demand-aware across serialization and the network boundary.

Backpressure does not make arbitrary application code non-blocking. If a handler performs blocking database or filesystem work on an event-loop thread, it can still stall unrelated work. Likewise, inserting an unbounded queue between the network and the consumer defeats the memory benefit of demand control.

Treat the boundary explicitly:

- keep streaming pipelines demand-aware;
- isolate unavoidable blocking work on an appropriate scheduler/executor;
- bound application buffers and define overflow policy;
- propagate cancellation where expensive upstream work should stop.

Flow control protects a live stream from uncontrolled production. It is not a substitute for rate limits, durable queues, or admission control across independent requests.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-transports-boundary">10. TCP, WebSocket, and protocol-feature boundary</a>

<details>
<summary>Click for details</summary>

RSocket is the application protocol; TCP and WebSocket are transports that carry its frames. TCP is common for service-to-service connections. WebSocket can be useful when infrastructure, browsers, or network policy favor an HTTP Upgrade path. Choosing one transport does not change the four RSocket interaction models.

The protocol also defines connection and stream features beyond the basic interactions, including keepalive, fragmentation/reassembly, metadata push, leasing, and resumption. Some are core mechanics and some are optional capabilities. Spring exposes advanced connector configuration through `RSocketRequester.Builder.rsocketConnector(...)`, so application code can configure keepalive, resumption, interceptors, and related RSocket Java options when needed.

Keep the layers separate when diagnosing a problem:

```text
application contract
    -> Spring route / codec / responder
    -> RSocket frame and flow-control semantics
    -> TCP or WebSocket transport
    -> network
```

A WebSocket disconnect is a transport/connection failure; a route-not-found or application error belongs above it. Likewise, enabling WebSocket transport does not automatically provide STOMP semantics, and using RSocket over WebSocket does not turn RSocket into STOMP.

### References

- [Spring Framework — RSocket overview and requester](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [RSocket protocol — frame and flow-control semantics](https://rsocket.io/about/protocol/)

</details>

- [Back to top](#back-to-top)
