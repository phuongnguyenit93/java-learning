<a id="back-to-top"></a>

# STOMP and the Simple Messaging Model

## Menu
- [Why STOMP over WebSocket](#stomp-purpose)
- [STOMP frames and commands](#stomp-frames-commands)
- [Destinations and subscriptions](#stomp-destinations-subscriptions)
- [Headers and message content](#stomp-headers-content)
- [Heartbeats and receipts](#stomp-heartbeats-receipts)
- [Acknowledgements, transactions, and broker-support boundary](#stomp-ack-transactions)
- [STOMP over the WebSocket transport](#stomp-over-websocket)
- [SIMP message types and destination, session, and user headers](#simp-message-model)
- [Mapping STOMP frames to Spring Message](#stomp-spring-message)

## <a id="stomp-purpose">Why STOMP over WebSocket</a>

<details>
<summary>Click for details</summary>

WebSocket gives two peers a full-duplex pipe, but it does not define what an application message means. If every application invents its own envelope, destination syntax, subscription model, and error conventions, client and server become tightly coupled to a private protocol.

STOMP adds a small text-oriented messaging protocol on top of a transport such as WebSocket or TCP. It introduces commands such as CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, ACK, NACK, and DISCONNECT; headers; destinations; subscriptions; heartbeats; and receipts. Spring uses those semantics to build a broker-backed application programming model on top of its generic Message infrastructure.

The reason to choose STOMP over raw WebSocket is therefore not that WebSocket is incapable of carrying the bytes. STOMP is useful when the application benefits from a shared messaging vocabulary: route to a destination, register subscriptions, broadcast through a broker, and map application handlers to logical destinations.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-frames-commands">STOMP frames and commands</a>

<details>
<summary>Click for details</summary>

A STOMP frame has a command line, zero or more headers, a blank line, an optional body, and a terminating null byte. The command identifies the protocol action while headers carry routing and protocol metadata.

Client-to-server commands include CONNECT/STOMP, SEND, SUBSCRIBE, UNSUBSCRIBE, ACK, NACK, BEGIN, COMMIT, ABORT, and DISCONNECT. Server-to-client commands include CONNECTED, MESSAGE, RECEIPT, and ERROR. Spring's StompDecoder and StompEncoder translate between wire-level frames and Spring Messages so application code does not normally parse frame text itself.

Commands belong to the STOMP protocol layer, not directly to Java controller methods. A SEND may become an application message handled by @MessageMapping, or it may be routed to the broker, depending on destination prefixes. A SUBSCRIBE is normally broker-oriented but can also be mapped to @SubscribeMapping for an application-produced initial reply.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-destinations-subscriptions">Destinations and subscriptions</a>

<details>
<summary>Click for details</summary>

STOMP uses destination strings as logical addresses. The protocol deliberately leaves the exact semantics of a destination to the server or broker. Names such as /topic/prices and /queue/orders are conventions until the configured broker gives them behavior.

A client sends a SUBSCRIBE frame with a destination and a subscription id. Later MESSAGE frames from the server identify the subscription that matched. A client SEND frame carries a destination but is not itself a subscription operation.

In Spring's WebSocket/STOMP stack, destination prefixes split responsibility. An application prefix such as /app routes eligible messages toward annotated application handlers, while broker prefixes such as /topic or /queue route messages to the configured message broker. Do not infer queue durability, competing-consumer semantics, or topic persistence from the string alone; those are broker capabilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-headers-content">Headers and message content</a>

<details>
<summary>Click for details</summary>

STOMP headers describe protocol concerns such as destination, subscription, message id, acknowledgement mode, content type, content length, receipt request, and heartbeat negotiation. The frame body is opaque to STOMP beyond framing rules and metadata such as content type and length.

Spring preserves the distinction between protocol-native headers and framework processing headers. StompHeaderAccessor stores actual STOMP headers in a native-header map while its SimpMessageHeaderAccessor parent exposes common processing metadata such as destination, session id, subscription id, user, and message type.

This distinction matters when constructing messages programmatically. A Spring header used only inside the application is not automatically a native STOMP header sent to the remote peer. Conversely, native headers should be handled through the appropriate accessor or messaging-template APIs rather than mixed casually into the top-level header map.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-heartbeats-receipts">Heartbeats and receipts</a>

<details>
<summary>Click for details</summary>

Heartbeats detect an otherwise idle or dead connection. During STOMP connection negotiation, each side advertises how frequently it can send and how frequently it expects to receive heartbeats. The negotiated values define liveness expectations; normal traffic also counts as activity, so a heartbeat is needed only when no other frame data is flowing.

Receipts solve a different problem. For a server that supports STOMP receipts, a client frame carrying a receipt header requests a RECEIPT; after the server successfully processes that frame, it sends a RECEIPT whose receipt-id matches the requested value. If processing fails, the server can report an ERROR instead. A receipt is therefore an acknowledgement of protocol-level processing, not proof that a business transaction committed, a downstream consumer finished, or durable storage succeeded.

Broker capability matters. Spring's built-in simple broker supports heartbeats when configured with a TaskScheduler, but it intentionally supports only a subset of STOMP and does not provide full receipt semantics. Applications that depend on richer protocol guarantees should use an appropriate external STOMP broker through the broker relay.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-ack-transactions">Acknowledgements, transactions, and broker-support boundary</a>

<details>
<summary>Click for details</summary>

STOMP acknowledgement modes let a broker decide when delivered messages may be considered consumed. ACK and NACK are meaningful only when the server or broker supports the corresponding subscription acknowledgement semantics. STOMP transactions group protocol operations between BEGIN and COMMIT or ABORT frames.

These features are a clear broker-support boundary in Spring. The built-in simple broker is designed for in-memory subscription tracking and broadcasting and supports only a subset of STOMP commands. Spring's reference documentation explicitly calls out that it does not support acknowledgements or receipts; it should not be treated as a full transactional message broker.

When an application requires broker-native acknowledgement modes, durable queues, transactions, redelivery policy, or clustered broker behavior, use an external broker that supports the needed STOMP features and consult that broker's documentation. Spring's broker relay forwards STOMP traffic; it does not redefine those vendor capabilities.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-over-websocket">STOMP over the WebSocket transport</a>

<details>
<summary>Click for details</summary>

When STOMP runs over WebSocket, each STOMP frame is carried inside WebSocket messages. WebSocket owns connection establishment, framing, and transport closure. STOMP owns commands, destinations, subscriptions, heartbeat negotiation, receipts, and broker-oriented message semantics.

Spring connects the layers with SubProtocolWebSocketHandler and STOMP codec infrastructure. Incoming WebSocket content is decoded into STOMP frames and then represented as Spring Messages. Outbound Spring Messages are encoded back into STOMP frames and written through the WebSocket session.

This layered model helps diagnose failures. An HTTP upgrade failure occurs before STOMP exists. A WebSocket transport error can break an established STOMP session. A malformed STOMP frame can fail after WebSocket transport is already healthy. A wrong application destination can be a Spring routing problem even when both WebSocket and STOMP framing are valid.

</details>

- [Back to top](#back-to-top)

---

## <a id="simp-message-model">SIMP message types and destination, session, and user headers</a>

<details>
<summary>Click for details</summary>

Spring's Simple Messaging Protocol model normalizes common protocol events into SimpMessageType values. In Spring Framework 6.1 these include CONNECT, CONNECT_ACK, MESSAGE, SUBSCRIBE, UNSUBSCRIBE, HEARTBEAT, DISCONNECT, DISCONNECT_ACK, and OTHER.

SimpMessageHeaderAccessor exposes the common metadata needed by Spring's routing infrastructure: destination, session id, subscription id, user, session attributes, heartbeat information, and message type. The abstraction lets Spring reason about a simple-messaging event without forcing every component to depend on raw STOMP header names.

SIMP is an internal/common Spring messaging model, not a wire protocol that clients speak. STOMP is one protocol adapted into it. This is why application code can inspect destination or user information through Spring APIs while StompHeaderAccessor still retains the native STOMP headers required for encoding back to the client or broker.

</details>

- [Back to top](#back-to-top)

---

## <a id="stomp-spring-message">Mapping STOMP frames to Spring Message</a>

<details>
<summary>Click for details</summary>

The important bridge in Spring's STOMP support is the conversion from a wire frame into Message<byte[]> plus structured headers. StompDecoder parses the command and native headers, creates a StompHeaderAccessor, and produces Spring Message instances. Common STOMP information is projected into SIMP headers so downstream components can route on destination, session, subscription, user, and message type.

The reverse happens for outbound traffic: Spring infrastructure builds or receives a Message, uses the STOMP header accessor to determine the appropriate protocol command and native headers, and StompEncoder writes the frame bytes.

Payload conversion into domain objects is a separate step handled later by message converters and annotated-method infrastructure. Keeping those two conversions distinct avoids a common confusion: STOMP decoding turns protocol bytes into a Spring Message; application conversion turns the message payload into the Java type expected by the handler.

</details>

- [Back to top](#back-to-top)
