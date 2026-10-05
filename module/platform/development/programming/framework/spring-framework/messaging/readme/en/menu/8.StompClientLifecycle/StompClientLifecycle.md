<a id="back-to-top"></a>

# STOMP Client and Connection Lifecycle

## Menu
- [1. WebSocketStompClient](#websocket-stomp-client)
- [2. ReactorNettyTcpStompClient and STOMP over TCP](#reactor-netty-tcp-stomp-client)
- [3. STOMP session lifecycle](#stomp-session-lifecycle)
- [4. Client subscriptions and sends](#stomp-client-subscriptions)
- [5. Client heartbeats](#stomp-client-heartbeats)
- [6. Receipts and server processing confirmation](#stomp-client-receipts)
- [7. Spring STOMP client failure and recovery boundary](#stomp-connection-recovery)

## <a id="websocket-stomp-client">1. WebSocketStompClient</a>

<details>
<summary>Click for details</summary>

`WebSocketStompClient` is Spring's STOMP client for a WebSocket transport. It builds on a Spring `WebSocketClient`, adds STOMP encoding/decoding and session management, and exposes a higher-level `StompSession` instead of forcing application code to work with raw WebSocket frames.

A client normally provides a `StompSessionHandler` and connects asynchronously. In Spring Framework 6.1, the asynchronous connect APIs return a `CompletableFuture<StompSession>`. The handler receives lifecycle callbacks and can subscribe once the STOMP session is established.

```java
WebSocketStompClient client =
        new WebSocketStompClient(new StandardWebSocketClient());
client.setMessageConverter(new MappingJackson2MessageConverter());

CompletableFuture<StompSession> future =
        client.connectAsync("ws://localhost:8080/ws", sessionHandler);
```

Treat this class as protocol infrastructure, not a resilience framework. It handles the WebSocket/STOMP mechanics; retry policy, backoff, application recovery, and business idempotency remain application concerns.

</details>

- [Back to top](#back-to-top)

---

## <a id="reactor-netty-tcp-stomp-client">2. ReactorNettyTcpStompClient and STOMP over TCP</a>

<details>
<summary>Click for details</summary>

STOMP is not limited to WebSocket. `ReactorNettyTcpStompClient` lets a Spring application speak STOMP directly over TCP through Reactor Netty. It shares the `StompClientSupport` model with `WebSocketStompClient`, so session handlers, converters, heartbeats, receipts, subscriptions, and send operations follow the same Spring STOMP abstractions.

Choose this client when the peer is a STOMP broker or server reached directly over TCP and an HTTP/WebSocket upgrade would add no value. Conversely, browser-facing applications normally use WebSocket because browsers cannot open arbitrary TCP sockets.

The presence of Reactor Netty here does **not** make this chapter a Reactor curriculum. Spring owns the STOMP client abstraction; connection event loops and reactive networking details remain implementation/background knowledge unless they materially affect configuration or debugging.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-session-lifecycle">3. STOMP session lifecycle</a>

<details>
<summary>Click for details</summary>

A `StompSession` represents one established STOMP conversation. The session is usable only after the transport connection succeeds and STOMP negotiation completes. A `StompSessionHandler` receives the connected callback, transport errors, and STOMP error frames, which makes it the natural place to coordinate application-level client state.

The lifecycle is roughly:

```text
transport connect
→ STOMP CONNECT
→ CONNECTED
→ active StompSession
→ subscriptions / sends / ACKs as supported
→ disconnect or transport failure
```

Do not assume that obtaining a client object means the session is already connected. Connection establishment is asynchronous. Likewise, a transport close ends the associated STOMP session; any subscriptions held by that session are no longer active and must be recreated after a new connection if the application reconnects.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-client-subscriptions">4. Client subscriptions and sends</a>

<details>
<summary>Click for details</summary>

`StompSession` provides the application-facing operations for subscribing and sending. `subscribe(destination, handler)` creates a subscription and returns a handle that can later be unsubscribed. `send(destination, payload)` converts the payload through the configured `MessageConverter` and sends a STOMP `SEND` frame.

A subscription handler should be designed around the payload type that the converter can produce. If JSON is expected, for example, configure a JSON converter and return the expected Java type from the frame handler.

```java
session.subscribe("/topic/prices", new StompFrameHandler() {
    public Type getPayloadType(StompHeaders headers) {
        return PriceUpdate.class;
    }

    public void handleFrame(StompHeaders headers, Object payload) {
        // consume PriceUpdate
    }
});
```

Subscription identifiers and broker acknowledgement modes are STOMP concepts. Their effective behavior also depends on the server or broker, so client code should not assume broker capabilities that are absent on the other side.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-client-heartbeats">5. Client heartbeats</a>

<details>
<summary>Click for details</summary>

Heartbeats detect an otherwise silent connection that has stopped making progress. STOMP peers advertise outgoing and incoming heartbeat intervals during connection setup. The effective interval is negotiated from both sides rather than being chosen unilaterally by the client.

Spring's STOMP clients can use a `TaskScheduler` to send and monitor heartbeats. Heartbeats are not business-level health checks: receiving them only proves that the STOMP connection is alive enough to exchange heartbeat traffic. They do not prove that a downstream consumer processed a business message.

Configure heartbeat intervals according to network characteristics and operational needs. Intervals that are too aggressive waste resources and amplify transient pauses; intervals that are too relaxed delay failure detection. Also remember that WebSocket proxies, load balancers, and brokers may have their own idle timeouts.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-client-receipts">6. Receipts and server processing confirmation</a>

<details>
<summary>Click for details</summary>

A STOMP client can request a receipt by adding a `receipt` header. The server later returns a `RECEIPT` frame whose `receipt-id` correlates with that request. Spring exposes this through `StompSession.Receiptable`, and auto-receipt support can be enabled for client operations.

A receipt confirms that the STOMP server **processed the corresponding client frame according to the protocol**. It is not an end-to-end guarantee that an eventual subscriber consumed the message, committed a database transaction, or produced a business result.

This distinction matters when building reliability logic. A receipt can prove protocol progress, while business delivery usually requires a separate acknowledgement or domain-level confirmation. With Spring's built-in simple broker, receipts are not supported, so code relying on them needs a full broker/server that implements the feature.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-connection-recovery">7. Spring STOMP client failure and recovery boundary</a>

<details>
<summary>Click for details</summary>

Spring's STOMP client gives you connection/session callbacks, but it does not define a complete application reconnection strategy. After a transport failure, the old `StompSession` is no longer a healthy session. A resilient application normally creates a new connection, waits for it to become established, and then recreates the subscriptions required by its use case.

Recovery design should answer several questions explicitly:

- how long and how often to retry;
- whether reconnect attempts need jitter/backoff;
- which subscriptions must be restored;
- whether sends may be retried safely;
- how duplicate business events are detected;
- when the application should surface an outage instead of retrying forever.

Spring provides the transport/protocol hooks. Retry orchestration and business idempotency remain application concerns, and broker-side redelivery semantics remain broker concerns.

</details>

- [Back to top](#back-to-top)
