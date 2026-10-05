<a id="back-to-top"></a>

# STOMP Client và Connection Lifecycle

## Menu
- [1. WebSocketStompClient](#websocket-stomp-client)
- [2. ReactorNettyTcpStompClient và STOMP trên TCP](#reactor-netty-tcp-stomp-client)
- [3. STOMP session lifecycle](#stomp-session-lifecycle)
- [4. Client subscription và send](#stomp-client-subscriptions)
- [5. Client heartbeat](#stomp-client-heartbeats)
- [6. Receipt và xác nhận server đã xử lý frame](#stomp-client-receipts)
- [7. Ranh giới failure và recovery của Spring STOMP client](#stomp-connection-recovery)

## <a id="websocket-stomp-client">1. WebSocketStompClient</a>

<details>
<summary>Xem chi tiết</summary>

`WebSocketStompClient` là STOMP client của Spring chạy trên WebSocket transport. Nó xây trên một Spring `WebSocketClient`, bổ sung STOMP encode/decode và session management, đồng thời cung cấp abstraction `StompSession` thay vì buộc application code thao tác trực tiếp với raw WebSocket frame.

Client thường cung cấp `StompSessionHandler` và kết nối bất đồng bộ. Trong Spring Framework 6.1, các API connect bất đồng bộ trả về `CompletableFuture<StompSession>`. Handler nhận lifecycle callback và có thể tạo subscription sau khi STOMP session được thiết lập.

```java
WebSocketStompClient client =
        new WebSocketStompClient(new StandardWebSocketClient());
client.setMessageConverter(new MappingJackson2MessageConverter());

CompletableFuture<StompSession> future =
        client.connectAsync("ws://localhost:8080/ws", sessionHandler);
```

Đây là protocol infrastructure, không phải resilience framework. Spring xử lý WebSocket/STOMP mechanics; retry policy, backoff, recovery và business idempotency vẫn là trách nhiệm của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reactor-netty-tcp-stomp-client">2. ReactorNettyTcpStompClient và STOMP trên TCP</a>

<details>
<summary>Xem chi tiết</summary>

STOMP không chỉ chạy trên WebSocket. `ReactorNettyTcpStompClient` cho phép Spring application giao tiếp STOMP trực tiếp qua TCP bằng Reactor Netty. Nó dùng chung mô hình `StompClientSupport` với `WebSocketStompClient`, nên session handler, converter, heartbeat, receipt, subscription và send vẫn theo cùng Spring STOMP abstraction.

Hãy chọn client này khi peer là STOMP broker/server được truy cập trực tiếp qua TCP và HTTP/WebSocket upgrade không mang thêm giá trị. Ngược lại, browser-facing application thường dùng WebSocket vì browser không mở arbitrary TCP socket.

Việc Reactor Netty xuất hiện ở đây không biến chapter thành Reactor curriculum. Module sở hữu Spring STOMP client abstraction; event-loop và reactive networking details chỉ là implementation/background concern khi cần cho configuration hoặc debugging.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-session-lifecycle">3. STOMP session lifecycle</a>

<details>
<summary>Xem chi tiết</summary>

`StompSession` biểu diễn một STOMP conversation đã được thiết lập. Session chỉ dùng được sau khi transport connection thành công và STOMP negotiation hoàn tất. `StompSessionHandler` nhận connected callback, transport error và STOMP error frame, nên đây là nơi tự nhiên để đồng bộ client-side application state.

Lifecycle có thể hình dung như sau:

```text
transport connect
→ STOMP CONNECT
→ CONNECTED
→ active StompSession
→ subscription / send / ACK khi được hỗ trợ
→ disconnect hoặc transport failure
```

Không nên coi việc có client object là session đã connected; connection establishment là bất đồng bộ. Khi transport đóng, STOMP session tương ứng cũng kết thúc. Các subscription của session cũ không còn active và phải được tạo lại sau khi application thiết lập connection mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-client-subscriptions">4. Client subscription và send</a>

<details>
<summary>Xem chi tiết</summary>

`StompSession` cung cấp các operation phía application để subscribe và send. `subscribe(destination, handler)` tạo subscription và trả về handle có thể unsubscribe sau đó. `send(destination, payload)` chuyển payload qua `MessageConverter` đã cấu hình rồi gửi STOMP `SEND` frame.

Subscription handler nên được thiết kế theo payload type mà converter có thể tạo. Nếu kỳ vọng JSON, hãy cấu hình JSON converter và khai báo Java type phù hợp trong frame handler.

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

Subscription id và broker acknowledgement mode là STOMP concepts. Hành vi thực tế còn phụ thuộc server/broker nên client không được giả định capability mà phía bên kia không cung cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-client-heartbeats">5. Client heartbeat</a>

<details>
<summary>Xem chi tiết</summary>

Heartbeat dùng để phát hiện một connection im lặng nhưng không còn tiến triển. Hai STOMP peer công bố interval gửi và nhận heartbeat trong connection setup. Effective interval được thương lượng từ cả hai phía, không phải do client đơn phương quyết định.

Spring STOMP client có thể dùng `TaskScheduler` để gửi và theo dõi heartbeat. Heartbeat không phải business health check: nhận heartbeat chỉ cho biết STOMP connection còn đủ sống để trao đổi heartbeat traffic, không chứng minh downstream consumer đã xử lý business message.

Cấu hình interval cần cân bằng network và operational requirement. Interval quá ngắn gây overhead và nhạy với pause tạm thời; quá dài làm chậm failure detection. Proxy, load balancer và broker cũng có thể có idle timeout riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-client-receipts">6. Receipt và xác nhận server đã xử lý frame</a>

<details>
<summary>Xem chi tiết</summary>

STOMP client có thể yêu cầu receipt bằng cách thêm `receipt` header. Server sau đó trả `RECEIPT` frame có `receipt-id` tương ứng. Spring biểu diễn cơ chế này qua `StompSession.Receiptable`, và client có thể bật auto-receipt cho operation.

Receipt xác nhận STOMP server **đã xử lý frame tương ứng theo protocol**. Nó không phải end-to-end guarantee rằng subscriber cuối cùng đã consume message, database transaction đã commit hay business result đã được tạo.

Sự phân biệt này rất quan trọng khi thiết kế reliability. Receipt chứng minh protocol progress; business delivery thường cần acknowledgement hoặc domain-level confirmation khác. Built-in simple broker của Spring không hỗ trợ receipt, vì vậy logic phụ thuộc receipt cần full broker/server có feature này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stomp-connection-recovery">7. Ranh giới failure và recovery của Spring STOMP client</a>

<details>
<summary>Xem chi tiết</summary>

Spring STOMP client cung cấp connection/session callback nhưng không định nghĩa một reconnection strategy hoàn chỉnh cho ứng dụng. Sau transport failure, `StompSession` cũ không còn là session khỏe mạnh. Ứng dụng resilient thường tạo connection mới, chờ session được thiết lập rồi recreate các subscription cần thiết.

Recovery design nên trả lời rõ:

- retry bao lâu và bao nhiêu lần;
- có cần jitter/backoff hay không;
- subscription nào phải khôi phục;
- send nào có thể retry an toàn;
- duplicate business event được nhận diện thế nào;
- khi nào cần báo outage thay vì retry vô hạn.

Spring cung cấp transport/protocol hook. Retry orchestration và business idempotency thuộc ứng dụng, còn broker-side redelivery semantics thuộc broker.

</details>

- [Quay lại đầu trang](#back-to-top)
