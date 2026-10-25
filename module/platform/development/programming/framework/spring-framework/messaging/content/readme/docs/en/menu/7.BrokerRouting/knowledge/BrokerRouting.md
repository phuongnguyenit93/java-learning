<a id="back-to-top"></a>

# Broker Routing and User Destinations

## Menu
- [Built-in simple broker](#simple-broker)
- [Simple broker capabilities and limits](#simple-broker-limits)
- [External STOMP broker relay](#broker-relay)
- [System and client broker connections](#broker-relay-connections)
- [Destination naming conventions](#destination-conventions)
- [User destinations](#user-destinations)
- [Multiple sessions per user](#multi-session-users)
- [Unresolved user destinations in clustered applications](#unresolved-user-destinations)
- [Broker-native behavior boundary](#broker-native-boundary)

## <a id="simple-broker">Built-in simple broker</a>

<details>
<summary>Click for details</summary>

The built-in simple broker is the smallest broker option in Spring's STOMP stack. It runs inside the application process, tracks subscriptions in memory, and forwards messages whose destinations match the configured broker prefixes. That makes it useful for learning the message flow, local development, and modest single-node applications where an external broker would add unnecessary infrastructure.

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");
}
```

Messages under `/app` are routed toward application handlers such as `@MessageMapping`. Messages under `/topic` or `/queue` are handled by `SimpleBrokerMessageHandler`, which maintains a `SubscriptionRegistry` and publishes matching messages to subscribed sessions through `clientOutboundChannel`.

The key mental model is **in-process subscription routing**, not a durable messaging server. State belongs to this application instance and disappears with it.

</details>

- [Back to top](#back-to-top)

---

## <a id="simple-broker-limits">Simple broker capabilities and limits</a>

<details>
<summary>Click for details</summary>

The simple broker deliberately implements only a subset of STOMP behavior. It supports subscription-based routing and can support heartbeats when a task scheduler is configured, but it is not a full STOMP broker. Spring's reference documentation explicitly notes that it does not support STOMP acknowledgements or receipts, and it is not suitable for clustering.

That boundary matters. Do not present the simple broker as if it provides durable queues, delivery guarantees, transactions, dead-lettering, or cross-node subscription state. Prefixes such as `/topic` and `/queue` are only routing conventions for the simple broker; they do not create durable broker entities.

Use it when the requirement is essentially "remember active subscriptions in this process and fan messages out." Move to a full broker when correctness or scale depends on broker-owned state or shared routing across application instances.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-relay">External STOMP broker relay</a>

<details>
<summary>Click for details</summary>

A STOMP broker relay keeps Spring's application-facing programming model while delegating broker duties to a real STOMP-capable broker. Configure it with `enableStompBrokerRelay(...)` instead of `enableSimpleBroker(...)`. Spring then forwards broker-directed STOMP messages to that external process and routes broker responses back to WebSocket clients.

`StompBrokerRelayMessageHandler` is therefore a bridge, not an embedded broker. This is the usual choice when the application needs features or scale that the simple broker does not provide.

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableStompBrokerRelay("/topic", "/queue")
            .setRelayHost("broker.internal")
            .setRelayPort(61613);
}
```

Destination durability, permissions, queue lifecycle, and vendor-specific routing are still defined by the selected broker.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-relay-connections">System and client broker connections</a>

<details>
<summary>Click for details</summary>

The relay has two connection roles that are easy to confuse. For each STOMP client session, Spring opens an independent TCP connection to the external broker and uses that connection for frames associated with that client. Separately, the relay maintains a **system connection** for messages originating from server-side application components.

This explains several runtime observations: server-originated broadcasts use the system connection; client traffic uses per-client broker connections; broker connection counts can grow with active STOMP sessions; and broker/network capacity is part of application capacity planning.

When the broker sends a message back on a per-client connection, the relay associates it with the corresponding Spring session before sending it to `clientOutboundChannel`. The broker is therefore outside the application process, but Spring still preserves the application session context needed to route frames back to the right client.

Recovery is asymmetric. If broker connectivity is lost, the relay automatically keeps trying to re-establish its shared **system connection**. The per-client broker connections are not automatically reconnected, so affected WebSocket/STOMP clients must reconnect and establish new sessions themselves. Broker availability for server-originated messaging should therefore not be mistaken for recovery of every client relay connection.

</details>

- [Back to top](#back-to-top)

---

## <a id="destination-conventions">Destination naming conventions</a>

<details>
<summary>Click for details</summary>

A destination is primarily a routing key. Spring uses prefixes to decide which component should handle a message, but the destination text does not carry universal queue or topic semantics by itself.

```text
/app/**   -> application handlers
/topic/** -> broker-routed broadcast convention
/queue/** -> broker-routed point-to-point convention
/user/**  -> logical per-user destinations
```

With the built-in simple broker, `/topic` and `/queue` are only conventions. With an external broker relay, destination syntax and semantics are constrained by that broker's STOMP implementation.

Choose prefixes that make routing intent obvious and keep application destinations separate from broker destinations. Do not encode authorization policy into naming conventions; routing names and security policy solve different problems.

</details>

- [Back to top](#back-to-top)

---

## <a id="user-destinations">User destinations</a>

<details>
<summary>Click for details</summary>

A user destination lets application code address a logical user without knowing the concrete session-specific destination created for a connection. A client can subscribe to a logical destination such as `/user/queue/updates`, and Spring resolves it to a destination unique enough to avoid collisions with other users or sessions.

On the sending side, `SimpMessagingTemplate.convertAndSendToUser(...)` and `@SendToUser` use the same resolution model. The application expresses "send to user X" while Spring maps that request to the active session destination or destinations for X.

This is a routing feature, not an authentication system. The user identity usually comes from the WebSocket session `Principal`, commonly inherited from the authenticated HTTP handshake. How that identity is established and what it may access belongs to Spring Security.

Authentication is not strictly required for the mechanism itself. An unauthenticated WebSocket session can subscribe to a user destination; in that case `@SendToUser` behaves like `broadcast = false` and targets only the session that sent the message.

</details>

- [Back to top](#back-to-top)

---

## <a id="multi-session-users">Multiple sessions per user</a>

<details>
<summary>Click for details</summary>

One authenticated user can have several simultaneous WebSocket sessions: multiple browser tabs, devices, or reconnects can all be active. Spring therefore treats "user" and "session" as related but distinct identities.

By default, a user-targeted message can be delivered to all active sessions for that user. When a handler response should return only to the session that triggered it, `@SendToUser(broadcast = false)` narrows delivery to that session.

This distinction is a design decision: "the user should see this notification everywhere" is different from "only this interaction needs the reply." Application state should not assume one WebSocket session per account, and disconnect handling should tolerate normal session churn.

</details>

- [Back to top](#back-to-top)

---

## <a id="unresolved-user-destinations">Unresolved user destinations in clustered applications</a>

<details>
<summary>Click for details</summary>

In a multi-instance deployment, the node performing a send to user X may not host any of X's sessions. Locally, the user destination is unresolved even though another node may have the target session.

Spring can broadcast unresolved user-destination messages so other application servers get a chance to resolve them. Message broker configuration exposes `userDestinationBroadcast` for this purpose, while user-registry broadcasts help nodes exchange information about connected users.

This solves a routing-discovery problem, not distributed user-state management in general. The broadcast destinations must be shared through the external broker and configured consistently. Cleanup of per-user destinations is broker-specific and should follow the broker's own guidance.

</details>

- [Back to top](#back-to-top)

---

## <a id="broker-native-boundary">Broker-native behavior boundary</a>

<details>
<summary>Click for details</summary>

Spring's STOMP support intentionally stops at the broker integration boundary. Once correctness depends on durable queues, acknowledgements, transactions, dead-letter policies, TTL, broker-side selectors, persistence, federation, or vendor-specific routing, the broker's own documentation becomes authoritative.

```text
Spring Framework
  handlers + channels + STOMP adaptation + relay
        |
        v
External broker
  durable routing + broker entities + broker protocol semantics + operations
```

Do not turn a broker-native guarantee into a Spring Framework guarantee merely because the application uses `enableStompBrokerRelay`. Spring carries frames to and from the broker; the broker defines their operational meaning.

</details>

- [Back to top](#back-to-top)
