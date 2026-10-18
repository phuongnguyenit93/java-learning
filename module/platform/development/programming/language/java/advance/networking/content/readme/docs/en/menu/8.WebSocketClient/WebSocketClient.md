<a id="back-to-top"></a>

# JDK WebSocket Client

## Menu
- [The java.net.http WebSocket Client Model](#websocket-client-model)
- [Opening the Connection and Handshake](#websocket-opening-handshake)
- [WebSocket.Listener and Callback Lifecycle](#websocket-listener-lifecycle)
- [request(n) and Demand Control](#websocket-demand-control)
- [Text/Binary Message Fragmentation and the last Flag](#websocket-message-fragmentation)
- [Sending Messages and CompletionStage](#websocket-send-completion)
- [Close, Abort, and Error Handling](#websocket-close-error)
- [Boundary with WebSocket Protocol and Realtime Architecture](#websocket-integration-boundary)

## <a id="websocket-client-model">The java.net.http WebSocket Client Model</a>

<details>
<summary>Click for details</summary>

The JDK WebSocket client lives in java.net.http but uses a different data model from HTTP request/response. After the opening handshake, the client keeps a **longer-lived bidirectional connection** for asynchronous message exchange.

Mental model:

~~~text
HttpClient
   ↓ newWebSocketBuilder()
opening handshake
   ↓
WebSocket connection
   ↙              ↘
send messages   Listener callbacks
~~~

The JDK API is a **WebSocket client API**. It is not a WebSocket server framework and it does not provide an application messaging protocol such as STOMP.

Builder and WebSocket operations use a non-blocking API style: buildAsync and send methods return CompletableFuture/CompletionStage values rather than waiting for the entire network action before returning.

The HttpClient still provides infrastructure for the opening handshake, such as proxy, executor, and client-level configuration. After the handshake, WebSocket and Listener become the main lifecycle abstractions.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-opening-handshake">Opening the Connection and Handshake</a>

<details>
<summary>Click for details</summary>

A connection is opened through a WebSocket.Builder obtained from HttpClient:

~~~java
HttpClient client = HttpClient.newHttpClient();

CompletableFuture<WebSocket> future =
        client.newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .buildAsync(
                      URI.create("wss://example.com/events"),
                      listener
              );
~~~

buildAsync completes normally with a WebSocket when the opening handshake succeeds. It may complete exceptionally for I/O failure, timeout, or a rejected/invalid handshake.

The builder can also configure headers and requested subprotocols. A subprotocol is negotiated as part of the opening handshake; it is not merely an arbitrary label attached after the connection exists.

URIs normally use ws or wss. With wss, TLS runs underneath the WebSocket connection. Certificate, trust, and key semantics belong to Security & Cryptography; Networking only needs to recognize that transport/security setup can make the handshake fail.

Do not block indefinitely on future.join() merely because the API returns a future. Time bounds, cancellation, and ownership still need an explicit application policy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-listener-lifecycle">WebSocket.Listener and Callback Lifecycle</a>

<details>
<summary>Click for details</summary>

WebSocket.Listener is the receive-side contract of the JDK client. Its main callbacks reflect connection lifetime:

~~~text
onOpen
  ↓
onText / onBinary / onPing / onPong
  ↓
onClose

failure path
→ onError
~~~

A minimal listener can look like:

~~~java
WebSocket.Listener listener = new WebSocket.Listener() {
    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(
            WebSocket webSocket,
            CharSequence data,
            boolean last) {

        System.out.println(data);
        webSocket.request(1);
        return null;
    }
};
~~~

Receive callbacks for one WebSocket are ordered by the client API, so application code should not invent unnecessary races by assuming arbitrary concurrent callbacks for the same connection.

That ordering has a precise boundary: the next listener invocation starts only after the previous listener **method invocation returns**. For `onText`, `onBinary`, `onPing`, and `onPong`, the returned `CompletionStage` primarily tells the WebSocket when the supplied data object may be reclaimed; completing that stage is **not** what advances the receive-demand counter and is not required before another requested callback can begin.

Callbacks are network-event boundaries, not good places for long blocking business work. If expensive processing is handed off to another executor and the callback returns immediately, later callbacks can begin once demand allows them, so application state may now be processed concurrently. Keep both demand and processing ownership explicit instead of assuming the returned stage serializes application work.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-demand-control">request(n) and Demand Control</a>

<details>
<summary>Click for details</summary>

The JDK WebSocket client does not push an unlimited number of receive callbacks to a listener. The application expresses **demand** through WebSocket.request(n).

~~~java
@Override
public void onOpen(WebSocket webSocket) {
    webSocket.request(1);
}

@Override
public CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {

    handle(data);
    webSocket.request(1);
    return null;
}
~~~

request(1) permits another receive invocation; it does not mean “read exactly one complete application message”. The counter applies to `onText`, `onBinary`, `onPing`, `onPong`, **and `onClose`**. `onOpen` and `onError` are not demand-controlled receive methods; in particular, `onError` may be invoked regardless of the counter.

A text or binary message can be delivered in several fragments, so demand must be reasoned about together with fragmentation. The `CompletionStage` returned from a receive callback is separate from this counter: requesting another invocation is what increases demand.

Demand control keeps callbacks from being delivered beyond what the application has requested. It does not turn WebSocket into a complete Reactive Streams abstraction; deeper Flow/reactive semantics belong elsewhere.

A common pitfall is forgetting to request more data after onOpen or a callback, leaving the connection alive but no longer receiving events as expected.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-message-fragmentation">Text/Binary Message Fragmentation and the last Flag</a>

<details>
<summary>Click for details</summary>

One WebSocket text or binary message may arrive through **multiple callback fragments**. The last flag tells the listener whether the current fragment ends the message.

~~~java
StringBuilder current = new StringBuilder();

@Override
public CompletionStage<?> onText(
        WebSocket webSocket,
        CharSequence data,
        boolean last) {

    current.append(data);

    if (last) {
        processMessage(current.toString());
        current.setLength(0);
    }

    webSocket.request(1);
    return null;
}
~~~

Do not assume:

~~~text
one onText callback = one application message
~~~

Binary callbacks use the same idea with ByteBuffer. If fragments are accumulated, application code should enforce sensible size limits so a peer cannot make memory grow without bound.

Fragmentation here is receive-API behavior needed for correct client code. Deep protocol framing belongs to the WebSocket protocol/realtime area.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-send-completion">Sending Messages and CompletionStage</a>

<details>
<summary>Click for details</summary>

Send-side methods return CompletableFuture<WebSocket>, representing completion of the corresponding send operation under the WebSocket API contract.

~~~java
webSocket.sendText("hello", true)
         .thenRun(() -> System.out.println("sent"));
~~~

The main send groups are:

- sendText(...);
- sendBinary(...);
- sendPing(...);
- sendPong(...);
- sendClose(...).

The last flag on text/binary sends lets the application deliberately fragment a message when needed.

Completion of the send future does **not** mean the remote business application has processed the message. It only describes the local send operation. Business acknowledgement, when required, must be designed as part of the application messaging protocol.

Keep this distinction explicit:

~~~text
send operation completed
≠ remote business action completed
~~~

Send operations also have pending-operation rules. Starting a new text/binary send while another text/binary send is pending, or switching text/binary type before a fragmented message is completed, can complete exceptionally with `IllegalStateException`. Ping/Pong sends have a similar pending-operation restriction for control messages.

Compose send stages or use a clear queue/serialization strategy so each logical send respects those state constraints:

~~~java
webSocket.sendText("part-1", false)
         .thenCompose(ws -> ws.sendText("part-2", true));
~~~

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-close-error">Close, Abort, and Error Handling</a>

<details>
<summary>Click for details</summary>

WebSocket has two termination paths worth distinguishing: a **protocol close handshake** and an **abort**.

sendClose(code, reason) begins protocol-level close:

~~~java
webSocket.sendClose(
        WebSocket.NORMAL_CLOSURE,
        "done"
);
~~~

Listener.onClose(...) observes a Close message from the peer and returns a CompletionStage representing callback processing.

`sendClose(...)` closes the WebSocket's **output** side by sending a Close message; it does not immediately close the input side. Input remains open until a peer Close is received, `abort()` is invoked, or an error closes the WebSocket.

`onClose(...)` is the last listener invocation for that WebSocket. If output is still open when a peer Close arrives, the stage returned from `onClose` tells the implementation when it may reciprocate by closing output; returning `null` means that may happen immediately.

abort() is a stronger path: it closes the connection immediately rather than completing the graceful close handshake. It is appropriate when the application cannot or should not continue normal protocol lifecycle.

onError(...) reports receive/lifecycle failure. After such a failure, do not merely log and assume the same WebSocket continues normally. Move connection state to a defined terminal/error state and let any reconnect policy live in the layer that owns resilience/realtime behavior.

`onError(...)` is terminal for that WebSocket: it is the last listener invocation, and both input and output are already closed when it begins. Unlike receive methods, it is not gated by `request(n)`.

Close status and reason are protocol information. Business failures should use an application message/schema rather than overload close codes.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-integration-boundary">Boundary with WebSocket Protocol and Realtime Architecture</a>

<details>
<summary>Click for details</summary>

This Networking chapter owns **JDK client mechanics**:

- building/opening the connection;
- Listener callback lifetime;
- demand;
- fragmentation;
- send, close, and error behavior.

It does not own:

- WebSocket server design;
- reconnect/backoff policy;
- application heartbeat strategy;
- STOMP or broker semantics;
- topic/channel/subscription models;
- application-protocol authentication flows;
- distributed realtime architecture.

Mental handoff:

~~~text
JDK WebSocket API
→ how the Java client sends and receives

Realtime / Integration
→ what messages mean
→ how reconnect/subscription works
→ topology and delivery semantics
~~~

This boundary remains useful even when a framework such as Spring WebSocket hides low-level client mechanics. Framework abstraction does not move application-protocol responsibilities into core Java Networking.

The final chapter combines TCP, UDP, NIO, HttpClient, and WebSocket into one decision model so the learner can choose an appropriate abstraction and recognize when a concern belongs to Security, Concurrency, or Integration instead.

</details>

- [Quay lại đầu trang](#back-to-top)
