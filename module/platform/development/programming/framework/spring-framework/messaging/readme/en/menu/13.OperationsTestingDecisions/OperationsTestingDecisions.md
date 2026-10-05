<a id="back-to-top"></a>

# Operations, Testing, and Design Decisions

## Menu
- [1. Channel executor capacity](#messaging-executor-capacity)
- [2. Send-time and buffer limits](#messaging-send-limits)
- [3. Message-size limits](#messaging-message-size-limits)
- [4. Broker availability and recovery](#broker-availability-recovery)
- [5. Slow clients and backpressure boundary](#slow-client-boundary)
- [6. WebSocketMessageBrokerStats and monitoring](#websocket-broker-stats)
- [7. Testing WebSocket and STOMP messaging](#stomp-testing)
- [8. Testing RSocket messaging](#rsocket-testing)
- [9. Choosing raw WebSocket, STOMP, or RSocket](#choose-messaging-model)
- [10. Choosing the simple broker or broker relay](#choose-stomp-broker)
- [11. Handoff to Spring Integration](#spring-integration-handoff)
- [12. Production readiness checklist](#messaging-production-checklist)

## <a id="messaging-executor-capacity">1. Channel executor capacity</a>

<details>
<summary>Click for details</summary>

STOMP-over-WebSocket messaging uses asynchronous Spring channels backed by executors, especially `clientInboundChannel` and `clientOutboundChannel`. Capacity planning therefore starts with the work each channel performs, not with an arbitrary thread count.

Inbound work often invokes application handlers. CPU-bound handlers generally benefit from a pool near CPU capacity; blocking database or remote calls hold threads longer and can require more concurrency or, preferably, redesign that isolates blocking work. Outbound work is dominated by writing to connected clients, so slow networks can hold sending capacity much longer than fast local clients.

Configure the executor as a system of **core size, maximum size, and queue capacity**. A common `ThreadPoolExecutor` trap is setting a larger maximum pool while leaving an effectively unbounded queue: queued tasks absorb all excess work, so the pool never grows beyond its core size.

```java
@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.taskExecutor()
            .corePoolSize(8)
            .maxPoolSize(16)
            .queueCapacity(2000);
}
```

Measure queue growth, active threads, task latency, and rejection behavior under realistic traffic. Increasing threads without bounding downstream work can only move the bottleneck and may increase memory pressure or contention.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-send-limits">2. Send-time and buffer limits</a>

<details>
<summary>Click for details</summary>

For STOMP over WebSocket, the difficult outbound case is a client that accepts data slowly while the application keeps producing messages. Spring allows only one thread at a time to send to a given WebSocket session; additional messages for that session are buffered.

`WebSocketTransportRegistration` provides two complementary limits:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
            .setSendTimeLimit(15_000)
            .setSendBufferSizeLimit(512 * 1024);
}
```

`sendTimeLimit` bounds how long Spring allows a slow send to remain in progress while later messages are being queued, while `sendBufferSizeLimit` bounds the amount of queued outbound data for the session. Exceeding these transport safety limits can cause the session to be closed rather than allowing one slow client to consume unbounded server resources.

One important 6.1 detail is that the send-time limit is checked when another message attempts to send. If a single send hangs and no later send occurs, this setting alone does not interrupt that physical socket write. It is therefore a buffering/resource guardrail, not a replacement for the WebSocket server or network connection timeout.

These are safety controls, not throughput targets. Set them from observed message sizes, acceptable client lag, network conditions, and memory budget. A huge buffer can hide a slow-client problem until latency and heap usage become severe; a tiny buffer may disconnect healthy clients during short bursts.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-message-size-limits">3. Message-size limits</a>

<details>
<summary>Click for details</summary>

`messageSizeLimit` controls the maximum incoming STOMP message size Spring will buffer and assemble. It solves a different problem from the outbound send buffer.

WebSocket containers have their own frame/message limits, and STOMP clients may split a larger STOMP message across multiple WebSocket messages. Spring's STOMP support can reassemble those pieces, so the application needs an explicit upper bound on the logical STOMP message:

```java
@Override
public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(128 * 1024);
}
```

Choose the limit from the largest legitimate application command or event plus serialization overhead. Large payloads increase allocation, copying, parsing time, and denial-of-service exposure. If messages routinely approach multi-megabyte sizes, consider whether the content belongs in object/blob storage with a reference sent through messaging instead.

Keep three limits conceptually separate: WebSocket container frame/message limits, Spring's assembled STOMP message-size limit, and the outbound per-session send buffer limit. Tuning one does not automatically tune the others.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-availability-recovery">4. Broker availability and recovery</a>

<details>
<summary>Click for details</summary>

With the simple broker, broker processing is in the application process. With a STOMP broker relay, Spring maintains TCP connections to an external broker, including a shared “system” connection used for application-originated messages. That external dependency can become unavailable independently of WebSocket client sessions.

Spring publishes `BrokerAvailabilityEvent` when broker relay availability changes. Components that proactively send through the broker can listen for this event and pause, reject, or degrade work while the relay's system connection is unavailable.

```java
@EventListener
void brokerAvailability(BrokerAvailabilityEvent event) {
    brokerAvailable.set(event.isBrokerAvailable());
}
```

The broker relay automatically attempts to re-establish its shared **system connection** when broker connectivity is lost. Client broker connections associated with WebSocket/STOMP sessions are not automatically reconnected; clients need their own reconnect logic. Neither kind of reconnection makes application sends during an outage durable by itself. Decide what the application should do with messages created while the relay is unavailable: fail fast, buffer in a bounded durable component, retry idempotently, or drop according to business semantics.

Also monitor the external broker independently. A healthy WebSocket endpoint only proves the application can accept client connections; it does not prove the broker relay can route subscriptions and broadcasts.

</details>

- [Back to top](#back-to-top)

---

## <a id="slow-client-boundary">5. Slow clients and backpressure boundary</a>

<details>
<summary>Click for details</summary>

STOMP over WebSocket and RSocket have different backpressure models. A STOMP/WebSocket client can be slow, but the protocol stack does not provide Reactive Streams demand that propagates from that client back through every application producer.

Spring therefore protects STOMP sessions with executor capacity plus send-time and buffer limits. Once a client falls behind, the application must decide whether to coalesce updates, sample data, cap queues, drop expendable messages, or disconnect the session. A larger queue only postpones the decision.

RSocket request-stream and request-channel carry demand with `REQUEST_N`, allowing a remote requester to limit payload production for that stream. Even there, backpressure is scoped to the reactive stream; it does not limit the number of new independent requests unless additional mechanisms such as leasing/admission control are used.

For either protocol, watch for fan-out multiplication. One application event sent to thousands of clients can create thousands of independent slow-consumer paths. Design limits per session and at the aggregate service level, and make loss/replay semantics explicit rather than relying on memory buffers.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-broker-stats">6. WebSocketMessageBrokerStats and monitoring</a>

<details>
<summary>Click for details</summary>

`WebSocketMessageBrokerStats` exposes a compact operational view of Spring's WebSocket/STOMP infrastructure. It reports information about:

- WebSocket sessions;
- STOMP sub-protocol processing;
- STOMP broker relay state when present;
- `clientInboundChannel` executor;
- `clientOutboundChannel` executor;
- the SockJS task scheduler.

Spring logs these statistics periodically; the default logging period is 30 minutes and can be changed through `setLoggingPeriod`. The class also exposes individual `get...StatsInfo()` methods for programmatic inspection.

Use these stats to answer questions such as “Are sessions accumulating?”, “Is the outbound executor saturated?”, or “Is the broker relay connected?” They are diagnostic summaries, not a complete observability system. Production monitoring should still export structured metrics for latency, failures, queue depth, disconnect reasons, message rates, broker health, and business-level success.

Correlate infrastructure metrics with application symptoms. A high outbound queue may be caused by slow clients, oversized messages, or a bursty publisher; thread counts alone cannot identify which one.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-testing">7. Testing WebSocket and STOMP messaging</a>

<details>
<summary>Click for details</summary>

WebSocket/STOMP tests should cover the framework mapping layer as well as business logic. Calling a controller method directly verifies Java code but does not verify destination mapping, message conversion, headers, interceptors, or channel routing.

Spring documents two server-side approaches: load the real application configuration and send messages through `clientInboundChannel`, or create the minimum messaging infrastructure such as `SimpAnnotationMethodMessageHandler` around selected controllers. These tests are focused and faster than opening a network connection.

End-to-end tests add an embedded WebSocket server and a STOMP client such as `WebSocketStompClient`. They can verify handshake, CONNECT/SUBSCRIBE/SEND flows, broker routing, serialization, user destinations, and disconnect behavior across the actual transport.

Use bounded synchronization primitives instead of arbitrary sleeps. For example, complete a future/latch when the expected STOMP frame arrives and fail with a clear timeout. Clean up subscriptions and sessions in test teardown so one test cannot leak state into another.

Keep a small number of end-to-end tests for wiring and protocol behavior, then test business services below the messaging layer with ordinary unit/integration tests.

</details>

- [Back to top](#back-to-top)

---

## <a id="rsocket-testing">8. Testing RSocket messaging</a>

<details>
<summary>Click for details</summary>

RSocket tests should verify the interaction contract, not only call responder methods directly. The highest-value cases are route resolution, codec compatibility, metadata extraction, input/output cardinality, cancellation, and connection lifecycle.

For focused reactive behavior, test the service/handler publisher with Reactor `StepVerifier`. For framework integration, wire `RSocketMessageHandler` as a responder and use `RSocketRequester` against a test RSocket server, preferably on an ephemeral port. Exercise the same API a real peer uses:

```java
StepVerifier.create(
        requester.route("inventory.watch")
                .data(request)
                .retrieveFlux(ItemEvent.class)
                .take(2))
    .expectNextCount(2)
    .verifyComplete();
```

Include at least one test for each interaction model the application exposes. For request-stream/channel, assert cancellation or bounded demand where it matters. For setup logic, test accepted and rejected `@ConnectMapping` paths. For metadata, verify custom MIME types are encoded, extracted, and mapped to the expected headers.

Connection failures should be deterministic in tests: dispose requesters/servers explicitly, use timeouts, and avoid depending on external brokers or ports unless the test is intentionally system-level.

</details>

- [Back to top](#back-to-top)

---

## <a id="choose-messaging-model">9. Choosing raw WebSocket, STOMP, or RSocket</a>

<details>
<summary>Click for details</summary>

Choose the messaging model from the contract the application needs:

| Need | Raw WebSocket | STOMP over WebSocket | RSocket |
| --- | --- | --- | --- |
| Custom text/binary duplex protocol | Strong fit | Extra protocol layer | Possible but more structure |
| Destinations, subscriptions, pub/sub conventions | Build yourself | Strong fit | Route model, not STOMP broker semantics |
| External STOMP broker relay | No | Strong fit | No direct equivalent |
| Request-response / request-stream / bidirectional stream semantics | Build yourself | Application convention | First-class |
| Reactive Streams demand across network | No | No | Request-stream/channel |
| Browser ecosystem simplicity | Native WebSocket API | Mature STOMP JS clients | Depends on RSocket client support |

Raw WebSocket gives maximum freedom and minimum protocol help; the application must define framing, routing, errors, subscriptions, and compatibility. STOMP adds a standard messaging vocabulary and fits broker-oriented pub/sub. RSocket fits multiplexed requester/responder interactions and reactive streaming.

Do not choose solely from latency benchmarks. Team familiarity, browser/client libraries, broker requirements, delivery semantics, observability, network infrastructure, and failure recovery often dominate the long-term cost.

</details>

- [Back to top](#back-to-top)

---

## <a id="choose-stomp-broker">10. Choosing the simple broker or broker relay</a>

<details>
<summary>Click for details</summary>

Spring's built-in simple broker is useful when an application needs basic in-process subscription tracking and broadcasting. It supports a subset of STOMP behavior and keeps deployment simple because there is no separate broker process.

Its trade-off is architectural: the simple broker is not designed for clustering and does not provide full broker features such as STOMP acknowledgements and receipts. With multiple application instances, it cannot by itself distribute a broadcast from one instance to clients connected to another.

A STOMP broker relay forwards messages to an external STOMP-capable broker such as RabbitMQ or ActiveMQ. This adds operational dependencies and broker connection management, but enables robust broker features and cross-instance broadcasting through shared broker infrastructure.

Choose the simple broker when:

- one application instance or non-clustered delivery is acceptable;
- basic destination subscriptions are enough;
- operational simplicity is more valuable than broker features.

Choose a broker relay when:

- the application must scale horizontally with shared broadcasts;
- broker acknowledgements/receipts or other full STOMP features matter;
- broker durability/routing/operations are already part of the architecture.

The relay is a bridge, not a magic reliability switch. End-to-end delivery guarantees still depend on STOMP commands, broker configuration, client acknowledgements, retries, and application idempotency.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-integration-handoff">11. Handoff to Spring Integration</a>

<details>
<summary>Click for details</summary>

Spring Integration should enter the design when the main problem becomes message-flow composition rather than the endpoint programming model itself. It builds on Spring Messaging abstractions and provides gateways, adapters, routers, transformers, filters, channels, and integration-flow lifecycle.

For example, a controller with a handful of `@MessageMapping` methods that delegates to services belongs comfortably in Spring Messaging. A system that must bridge RSocket, JMS, Kafka, files, and HTTP with reusable routing/transformation stages is an integration problem and is better represented by Spring Integration.

Keep the handoff clean:

```text
Spring Messaging
    -> message model, channels, WebSocket/STOMP/RSocket endpoint semantics

Spring Integration
    -> enterprise integration patterns and composed adapter/gateway flows
```

Do not reproduce a large integration flow manually inside WebSocket or RSocket handlers. Likewise, do not introduce Spring Integration for a simple endpoint merely to avoid a few lines of delegation.

</details>

- [Back to top](#back-to-top)

---

## <a id="messaging-production-checklist">12. Production readiness checklist</a>

<details>
<summary>Click for details</summary>

A production review should make the messaging contract and its resource limits explicit. At minimum, verify:

- **Protocol choice:** why raw WebSocket, STOMP, or RSocket fits the interaction model.
- **Connection lifecycle:** authentication, authorization, setup/handshake failure, heartbeats/keepalive, disconnect cleanup, and reconnect behavior.
- **Capacity:** inbound/outbound executor sizing, bounded queues, rejection behavior, and blocking work isolation.
- **Slow consumers:** send-time/buffer limits for STOMP/WebSocket; demand/cancellation behavior for RSocket streams.
- **Payloads:** message-size limits, codec/MIME compatibility, validation, and large-object strategy.
- **Broker behavior:** simple broker limitations or external broker relay availability, reconnect policy, and outage semantics.
- **Delivery semantics:** what may be lost, retried, replayed, duplicated, or acknowledged; idempotency where retries are possible.
- **Observability:** connected sessions, message rates, queue depth, handler latency, errors, disconnect reasons, broker health, and RSocket connection failures.
- **Testing:** route/destination mapping, metadata/headers, serialization, every used interaction model, cancellation, outage paths, and a small end-to-end suite.
- **Shutdown:** stop new work, close sessions/requesters, and release executors/servers without leaking connections.

The checklist should produce concrete limits and failure behavior, not only “monitor this in production.” Load-test with representative message sizes, fan-out, connection counts, and slow consumers because messaging systems often fail first at resource boundaries rather than happy-path handler code.

### References

- [Spring Framework — STOMP performance](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html)
- [Spring Framework — STOMP testing](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/testing.html)
- [Spring Framework — RSocket](https://docs.spring.io/spring-framework/reference/rsocket.html)

</details>

- [Back to top](#back-to-top)
