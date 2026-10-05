<a id="back-to-top"></a>

# Spring WebSocket Transport Foundation

## Menu
- [1. When WebSocket is a fit](#websocket-use-cases)
- [2. HTTP Upgrade handshake](#websocket-handshake)
- [3. WebSocketHandler and WebSocketSession](#websocket-handler-session)
- [4. WebSocketClient and WebSocketConnectionManager](#websocket-client)
- [5. Text, binary, ping, and pong messages](#websocket-message-types)
- [6. Handshake customization and interceptors](#websocket-handshake-customization)
- [7. Origin policy](#websocket-origin-policy)
- [8. SockJS fallback](#sockjs-fallback)
- [9. Servlet WebSocket and reactive WebSocket boundary](#servlet-reactive-websocket-boundary)

## <a id="websocket-use-cases">1. When WebSocket is a fit</a>

<details>
<summary>Click for details</summary>

WebSocket is useful when client and server need a long-lived, full-duplex connection and either side may send data without creating a new HTTP request for every exchange. Typical examples include collaborative updates, live dashboards, notifications, and interactive messaging where repeated polling would add latency or overhead.

The trade-off is lifecycle complexity. A WebSocket connection has to be established, kept healthy, closed, and recovered when the network fails. Application code also needs an agreement about the meaning of text or binary payloads because WebSocket itself does not define an application message model.

Spring's WebSocket API gives applications a framework-level handler/session abstraction over the Servlet-stack WebSocket runtime. Use raw WebSocket when the application really wants to own its message format and routing. When destinations, subscriptions, broker-style fan-out, acknowledgements, or higher-level message semantics are needed, STOMP can be a better fit on top of the same transport.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-handshake">2. HTTP Upgrade handshake</a>

<details>
<summary>Click for details</summary>

A WebSocket connection begins as an HTTP request. The client asks to upgrade the connection, the server validates the request, negotiates WebSocket details such as sub-protocols, and, if successful, switches from ordinary HTTP request-response handling to a persistent WebSocket session.

Spring exposes this boundary through handshake infrastructure. HandshakeInterceptor can inspect the HTTP request and response before and after the handshake and can copy selected attributes into the eventual WebSocketSession. HandshakeHandler performs the actual negotiation and determines details such as the user Principal when appropriate.

The handshake is still part of the web boundary, so cookies, HTTP headers, origin information, and an already-established Principal can be visible there. This module uses those facts to explain the session identity and transport lifecycle. Authentication and authorization policy remain Spring Security responsibilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-handler-session">3. WebSocketHandler and WebSocketSession</a>

<details>
<summary>Click for details</summary>

WebSocketHandler receives transport lifecycle callbacks: afterConnectionEstablished, handleMessage, handleTransportError, and afterConnectionClosed. WebSocketSession represents one established connection and exposes negotiated information, attributes copied from the handshake, the Principal when available, local/remote addresses, size limits, open state, and send/close operations.

    public final class EchoHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(
                WebSocketSession session, TextMessage message) throws Exception {
            session.sendMessage(new TextMessage("echo: " + message.getPayload()));
        }
    }

One practical rule is especially important: the underlying standard WebSocket session does not permit arbitrary concurrent sends. When multiple application threads can write to the same session, synchronize sending or use ConcurrentWebSocketSessionDecorator, which serializes sends and applies configured buffer/send-time limits.

If a WebSocketHandler lets an exception escape, Spring's default decorator strategy logs it and closes the session with a server-error status. Handle expected application failures deliberately rather than relying on connection closure as normal control flow.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-client">4. WebSocketClient and WebSocketConnectionManager</a>

<details>
<summary>Click for details</summary>

WebSocketClient is Spring's contract for initiating a client-side WebSocket handshake. In Spring Framework 6.1, execute(...) returns a CompletableFuture<WebSocketSession> that completes when the session is available. StandardWebSocketClient is the usual Jakarta WebSocket-based implementation, while SockJsClient can provide SockJS-based fallback behavior.

    WebSocketClient client = new StandardWebSocketClient();
    CompletableFuture<WebSocketSession> future =
            client.execute(handler, "wss://example.test/updates");

WebSocketConnectionManager is useful when the connection should participate in the Spring ApplicationContext lifecycle. It holds a WebSocketClient, WebSocketHandler, and target URI, opens the connection as a lifecycle component, and closes it when stopped. It can also configure handshake headers, sub-protocols, and origin.

Choose the direct client API when connection ownership belongs to explicit application logic. Choose the connection manager when a long-lived connection is infrastructure whose start/stop lifecycle should follow the application context.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-message-types">5. Text, binary, ping, and pong messages</a>

<details>
<summary>Click for details</summary>

At the Spring API level, a WebSocketHandler receives WebSocketMessage values such as TextMessage, BinaryMessage, PingMessage, and PongMessage. Text and binary messages carry application data. Ping and pong are WebSocket control messages used by the protocol for liveness and control rather than application-domain commands.

Message boundaries matter. A handler can declare whether it supports partial messages. If partial-message support is enabled and the underlying server supports it, one logical WebSocket message may arrive through multiple handleMessage calls, with WebSocketMessage.isLast() indicating the final part.

Do not confuse WebSocket message types with Spring Messaging Message<?> or STOMP frames. They are different layers. Chapter 4 shows how STOMP frames carried over WebSocket are decoded and represented as Spring Messages for the broker-backed programming model.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-handshake-customization">6. Handshake customization and interceptors</a>

<details>
<summary>Click for details</summary>

Handshake customization should solve transport-boundary concerns. HandshakeInterceptor can inspect the request, reject the handshake by returning false, and populate attributes that later appear in WebSocketSession. A custom HandshakeHandler can customize negotiation behavior such as determining the Principal or selecting server-specific upgrade behavior.

For ordinary registration, WebSocketConfigurer and WebSocketHandlerRegistry provide a compact configuration surface:

    registry.addHandler(chatHandler, "/chat")
            .addInterceptors(auditHandshakeInterceptor)
            .setAllowedOrigins("https://app.example.test");

Keep application message routing out of handshake code. The handshake runs once when the connection is established; normal messages may continue for a long time afterward. Business commands belong in message handlers or, for STOMP, annotated message-handling infrastructure.

</details>

- [Back to top](#back-to-top)

---

## <a id="websocket-origin-policy">7. Origin policy</a>

<details>
<summary>Click for details</summary>

Browser WebSocket handshakes include an Origin header, and Spring's WebSocket registration APIs provide allowed-origin checks. For Spring Framework 6.1 server registration, the default policy is same-origin unless configured otherwise. setAllowedOrigins(...) accepts explicit origins, while setAllowedOriginPatterns(...) supports pattern-based matching.

Origin checks are a browser-oriented protection boundary, not a complete client-authentication mechanism. Non-browser clients can construct their own HTTP headers, including Origin. Treat origin configuration as part of cross-origin transport policy and use the security layer for identity and authorization decisions.

When SockJS is enabled, origin restrictions can also disable fallback transports that cannot expose origin information safely. That can reduce compatibility with older browser transport modes, so changing allowed origins has both security and transport consequences.

</details>

- [Back to top](#back-to-top)

---

## <a id="sockjs-fallback">8. SockJS fallback</a>

<details>
<summary>Click for details</summary>

SockJS emulates a WebSocket-like API by selecting from WebSocket, HTTP streaming, or HTTP long-polling transports according to server and client capabilities. In Spring, calling withSockJS() on a WebSocket or STOMP endpoint enables the SockJS service and its fallback transports.

SockJS has its own transport lifecycle and heartbeat behavior. By default, the Spring SockJS service sends heartbeats during otherwise idle connections so intermediaries do not treat the connection as abandoned. When STOMP heartbeats are negotiated over SockJS, SockJS heartbeats are disabled and the STOMP heartbeat mechanism becomes responsible for liveness traffic.

Use SockJS for a concrete compatibility requirement. Modern deployments that can rely on native WebSocket often prefer the simpler transport stack. Fallback is not free: more HTTP transport modes mean more proxy, timeout, origin, and capacity behavior to understand and test.

</details>

- [Back to top](#back-to-top)

---

## <a id="servlet-reactive-websocket-boundary">9. Servlet WebSocket and reactive WebSocket boundary</a>

<details>
<summary>Click for details</summary>

This chapter covers the Servlet-stack Spring WebSocket API in org.springframework.web.socket, because that is the transport foundation used by Spring's classic WebSocket/STOMP message-broker support. The reactive WebSocket API belongs to Spring WebFlux and has a different execution model.

The conceptual boundary is straightforward: both expose WebSocket connections and messages, but their handler contracts and threading/reactive execution models differ. Do not import Reactor or backpressure rules into the Servlet WebSocket handler model simply because both technologies can carry streaming data.

When the application is already built around WebFlux and reactive end-to-end processing, follow the Spring Reactive module for its WebSocket API. When learning Spring's broker-backed STOMP stack here, keep the Servlet WebSocket transport mental model and then move upward into STOMP and Spring Messaging channels.

</details>

- [Back to top](#back-to-top)
