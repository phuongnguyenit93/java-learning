<a id="back-to-top"></a>

# STOMP Lifecycle, Interception, Ordering, and Boundaries

## Menu
- [STOMP and WebSocket application events](#stomp-application-events)
- [ChannelInterceptor](#channel-interceptor)
- [ExecutorChannelInterceptor](#executor-channel-interceptor)
- [Message ordering under concurrent execution](#message-ordering)
- [Preserving receive order](#preserve-receive-order)
- [Preserving publish order](#preserve-publish-order)
- [Authentication identity from the WebSocket handshake](#websocket-authentication-identity)
- [Message authorization boundary](#message-authorization-boundary)
- [Spring Session boundary](#spring-session-boundary)
- [WebSocket scope](#websocket-scope)

## <a id="stomp-application-events">STOMP and WebSocket application events</a>

<details>
<summary>Click for details</summary>

Spring publishes application events around important WebSocket/STOMP lifecycle transitions so infrastructure code can observe connections without being coupled to controller methods. Common events include `SessionConnectEvent`, `SessionConnectedEvent`, `SessionSubscribeEvent`, `SessionUnsubscribeEvent`, `SessionDisconnectEvent`, and broker availability events.

The events are observations, not a distributed source of truth. A disconnect event can be seen more than once for the same session, so cleanup listeners should be idempotent. Likewise, broker availability can change over time; a component that sends through a broker relay should react to current availability instead of assuming that one successful startup event means the broker will remain reachable forever. For a broker relay, `BrokerAvailabilityEvent` reflects loss and re-establishment of the shared **system connection**; it does not mean that per-client broker connections were automatically recovered.

Use events for telemetry, session bookkeeping, and operational reactions. Avoid embedding core business transactions in lifecycle listeners when ordinary message handlers provide a clearer ownership boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="channel-interceptor">ChannelInterceptor</a>

<details>
<summary>Click for details</summary>

`ChannelInterceptor` observes and can influence messages as they pass through a `MessageChannel`. Typical callbacks include `preSend`, `postSend`, and `afterSendCompletion`. In the STOMP stack, interceptors are commonly registered on the inbound or outbound client channels through `WebSocketMessageBrokerConfigurer`.

A `preSend` interceptor can inspect a message with `StompHeaderAccessor`, reject it by returning `null`, or return a modified message. That makes the interceptor useful for protocol-level concerns such as correlation metadata, metrics, tracing, or extracting an authentication token during a STOMP `CONNECT` flow.

Keep the boundary clear: the Framework supplies the interception hook. Authorization rules, authentication mechanisms, and security context management belong to Spring Security. Interceptors should also avoid long blocking work because they run on the channel's send path and can reduce throughput.

</details>

- [Back to top](#back-to-top)

---

## <a id="executor-channel-interceptor">ExecutorChannelInterceptor</a>

<details>
<summary>Click for details</summary>

`ExecutorChannelInterceptor` complements `ChannelInterceptor` for channels that invoke handlers through an executor. Its callbacks surround **handler execution** rather than only the caller's send operation, so they run in the thread that actually invokes the `MessageHandler`.

This distinction matters when context is thread-bound. A value visible to the sender thread is not automatically visible to an executor worker thread. An executor-aware interceptor can establish and clean up context around handler invocation, or collect timing that reflects real handler execution.

Do not use it as an excuse to copy arbitrary `ThreadLocal` state. Prefer explicit message headers or supported context-propagation mechanisms when data is part of the message contract. Use executor interception for infrastructure concerns that genuinely belong to the execution boundary.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-ordering">Message ordering under concurrent execution</a>

<details>
<summary>Click for details</summary>

The inbound and outbound client channels are typically backed by executors. Parallel workers improve throughput, but they also mean that scheduling order is not automatically message order. Two frames from the same session can be accepted in sequence yet reach downstream handling or the network in a different sequence if tasks complete differently.

This is not a STOMP parser bug; it is a concurrency property. The same trade-off appears in both directions:

```text
client -> clientInboundChannel -> handlers
broker/application -> clientOutboundChannel -> WebSocket client
```

Before requiring strict order, ask whether the domain truly depends on it. Sequence-sensitive commands, incremental state updates, or protocols with causal ordering may need preservation. Independent notifications often benefit more from parallel throughput.

</details>

- [Back to top](#back-to-top)

---

## <a id="preserve-receive-order">Preserving receive order</a>

<details>
<summary>Click for details</summary>

For messages arriving from a client, Spring can preserve handling order per session. Configure it on `StompEndpointRegistry`:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setPreserveReceiveOrder(true);
}
```

When enabled, messages from the same client session are processed on `clientInboundChannel` one at a time in receive order, despite the executor-backed channel. This is a per-session ordering guarantee, not a global ordering guarantee across all clients.

Ordering reduces available parallelism, so enable it because the application semantics require it, not as a default "safety" switch. If handlers perform slow blocking work, preserved order can also amplify head-of-line blocking within that session.

</details>

- [Back to top](#back-to-top)

---

## <a id="preserve-publish-order">Preserving publish order</a>

<details>
<summary>Click for details</summary>

For messages going to a client, Spring can similarly preserve publication order per session:

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setPreservePublishOrder(true);
}
```

With the flag enabled, messages for the same session are published to `clientOutboundChannel` one at a time so the client observes the same order in which Spring publishes them. Spring documents a small performance cost for this guarantee.

This does not create transactional ordering across an external broker or across different sessions. It only controls the Spring outbound path for a given client session. Broker-native ordering guarantees and consumer semantics remain separate concerns.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-authentication-identity">Authentication identity from the WebSocket handshake</a>

<details>
<summary>Click for details</summary>

For the usual Servlet-stack setup, the WebSocket handshake is an HTTP request. If that request has an authenticated `Principal`, Spring carries the same identity into the WebSocket session and exposes it to subsequent STOMP message handling.

That is why STOMP `login` and `passcode` headers sent by a browser are not the normal authentication mechanism for the Spring WebSocket server; Spring's reference documentation notes that such headers are ignored or overridden in this setup. Authentication normally happens before or during the HTTP handshake.

Token-in-STOMP-header designs are possible with an inbound `ChannelInterceptor`, but the interceptor is only the Framework hook. Token validation, authentication objects, and authorization policy belong to Spring Security and should be taught there in depth.

</details>

- [Back to top](#back-to-top)

---

## <a id="message-authorization-boundary">Message authorization boundary</a>

<details>
<summary>Click for details</summary>

The messaging infrastructure exposes destinations, message types, headers, user identity, and interception points that security rules can consume. It does not itself decide which user may send to `/app/admin` or subscribe to `/topic/private`.

Spring Security owns that policy. A useful division is:

```text
Spring Framework Messaging
  route + decode + invoke + broker/user destination plumbing
Spring Security
  authenticate identity + authorize message operations
```

Knowledge in this module should therefore explain where identity enters the flow and where security hooks attach, but it should not duplicate `AuthorizationManager`, matcher configuration, CSRF policy, or detailed message-security rules. When debugging, distinguish a routing failure from an authorization rejection; they occur at different layers.

</details>

- [Back to top](#back-to-top)

---

## <a id="spring-session-boundary">Spring Session boundary</a>

<details>
<summary>Click for details</summary>

A WebSocket connection can outlive the HTTP request that created it, while applications may still rely on HTTP session state. Spring Framework owns the WebSocket and messaging lifecycle, but distributed persistence and lifecycle management of HTTP sessions belong to Spring Session.

Spring Session integration can keep an HTTP session active while WebSocket traffic is occurring and can close WebSocket sessions when the backing session is ended. Those behaviors are useful in clustered applications where the session cannot remain a container-local implementation detail.

This module needs only the handoff mental model: WebSocket messages are long-lived activity associated with a connection; if that activity must participate in shared HTTP-session semantics, move to the Spring Session curriculum rather than reimplementing session storage inside messaging code.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-scope">WebSocket scope</a>

<details>
<summary>Click for details</summary>

Spring's WebSocket messaging configuration provides a `websocket` bean scope whose lifetime is tied to a WebSocket session. It is useful for state that is genuinely connection-specific, such as a per-session accumulator or conversation helper.

Because a shorter-lived scoped bean may be injected into longer-lived components, scoped proxies are commonly used. Session-scoped state is backed by WebSocket session attributes, and destruction callbacks run when the session ends.

Use this scope carefully. It is not a substitute for durable user state: reconnecting creates a new WebSocket session, multiple sessions may exist for one user, and process failure destroys in-memory session state. Persistent business state belongs in an appropriate datastore; WebSocket scope is for connection-lifetime behavior.

</details>

- [Back to top](#back-to-top)
