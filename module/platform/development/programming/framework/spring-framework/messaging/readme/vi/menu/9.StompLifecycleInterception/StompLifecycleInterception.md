<a id="back-to-top"></a>

# STOMP Lifecycle, Interception, Ordering và các ranh giới

## Menu
- [1. STOMP và WebSocket application event](#stomp-application-events)
- [2. ChannelInterceptor](#channel-interceptor)
- [3. ExecutorChannelInterceptor](#executor-channel-interceptor)
- [4. Message ordering khi thực thi đồng thời](#message-ordering)
- [5. Giữ receive order](#preserve-receive-order)
- [6. Giữ publish order](#preserve-publish-order)
- [7. Authentication identity từ WebSocket handshake](#websocket-authentication-identity)
- [8. Ranh giới message authorization](#message-authorization-boundary)
- [9. Ranh giới Spring Session](#spring-session-boundary)
- [10. WebSocket scope](#websocket-scope)

## <a id="stomp-application-events">1. STOMP và WebSocket application event</a>

<details>
<summary>Xem chi tiết</summary>

Spring phát application event quanh các bước quan trọng của WebSocket/STOMP lifecycle để infrastructure code quan sát connection mà không phụ thuộc controller method. Các event thường gặp gồm `SessionConnectEvent`, `SessionConnectedEvent`, `SessionSubscribeEvent`, `SessionUnsubscribeEvent`, `SessionDisconnectEvent` và broker availability event.

Event là tín hiệu quan sát, không phải distributed source of truth. `SessionDisconnectEvent` có thể xuất hiện nhiều lần cho cùng một session, vì vậy cleanup listener nên idempotent. Broker availability cũng có thể thay đổi theo thời gian; component gửi qua broker relay không nên coi một startup event thành bảo đảm broker sẽ luôn reachable. Với broker relay, `BrokerAvailabilityEvent` phản ánh việc shared **system connection** bị mất rồi được thiết lập lại; event này không có nghĩa các per-client broker connection đã được tự động phục hồi.

Hãy dùng event cho telemetry, session bookkeeping và operational reaction. Không nên đặt core business transaction vào lifecycle listener khi ordinary message handler tạo ownership rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="channel-interceptor">2. ChannelInterceptor</a>

<details>
<summary>Xem chi tiết</summary>

`ChannelInterceptor` quan sát và có thể tác động lên message khi nó đi qua `MessageChannel`. Các callback điển hình gồm `preSend`, `postSend` và `afterSendCompletion`. Trong STOMP stack, interceptor thường được đăng ký trên inbound hoặc outbound client channel qua `WebSocketMessageBrokerConfigurer`.

`preSend` có thể đọc message bằng `StompHeaderAccessor`, từ chối bằng cách trả `null`, hoặc trả một message đã chỉnh sửa. Vì vậy interceptor phù hợp cho protocol/infrastructure concern như correlation metadata, metrics, tracing hoặc trích authentication token trong STOMP `CONNECT` flow.

Boundary cần rõ: Framework cung cấp interception hook; authentication, authorization rule và security context management thuộc Spring Security. Interceptor cũng nên tránh blocking work dài vì nó nằm trực tiếp trên send path của channel và có thể làm giảm throughput.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-channel-interceptor">3. ExecutorChannelInterceptor</a>

<details>
<summary>Xem chi tiết</summary>

`ExecutorChannelInterceptor` bổ sung cho `ChannelInterceptor` trên các channel gọi handler thông qua executor. Callback của nó bao quanh **handler execution** thay vì chỉ bao quanh thao tác send của caller, nên callback chạy trên thread thực sự invoke `MessageHandler`.

Điểm này quan trọng với context gắn vào thread. Giá trị nhìn thấy trên sender thread không tự động xuất hiện trên executor worker thread. Executor-aware interceptor có thể thiết lập/dọn infrastructure context quanh handler invocation hoặc đo thời gian phản ánh execution thật.

Không nên dùng nó để sao chép tùy tiện mọi `ThreadLocal`. Khi dữ liệu là một phần message contract, ưu tiên explicit message header hoặc context-propagation mechanism được hỗ trợ. Executor interception dành cho concern thực sự thuộc execution boundary.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-ordering">4. Message ordering khi thực thi đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

Inbound và outbound client channel thường được backed bởi executor. Nhiều worker tăng throughput nhưng đồng thời làm scheduling order không còn đồng nghĩa message order. Hai frame từ cùng session có thể được nhận theo thứ tự nhưng hoàn tất downstream handling hoặc ghi ra network theo thứ tự khác nếu task chạy với thời gian khác nhau.

Đây là đặc tính concurrency, không phải lỗi STOMP parser. Trade-off xuất hiện ở cả hai hướng:

```text
client -> clientInboundChannel -> handler
broker/application -> clientOutboundChannel -> WebSocket client
```

Trước khi yêu cầu strict order, cần hỏi domain có thực sự phụ thuộc nó không. Sequence-sensitive command hoặc incremental state update có thể cần preserve order; notification độc lập thường hưởng lợi nhiều hơn từ parallel throughput.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-receive-order">5. Giữ receive order</a>

<details>
<summary>Xem chi tiết</summary>

Với message đi từ client vào, Spring có thể giữ handling order theo từng session. Cấu hình nằm trên `StompEndpointRegistry`:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws");
    registry.setPreserveReceiveOrder(true);
}
```

Khi bật, message của cùng một client session được xử lý trên `clientInboundChannel` lần lượt theo receive order dù channel dùng executor. Đây là per-session ordering guarantee, không phải global ordering giữa mọi client.

Ordering làm giảm parallelism khả dụng, nên chỉ bật khi application semantics cần. Nếu handler thực hiện blocking work chậm, preserve order còn có thể tạo head-of-line blocking trong chính session đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="preserve-publish-order">6. Giữ publish order</a>

<details>
<summary>Xem chi tiết</summary>

Với message đi ra client, Spring cũng có thể giữ publication order theo session:

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setPreservePublishOrder(true);
}
```

Khi bật, message cho cùng session được publish lên `clientOutboundChannel` từng message một để client quan sát cùng thứ tự Spring publish. Spring ghi rõ guarantee này có một chi phí hiệu năng nhỏ.

Cơ chế không tạo transactional ordering xuyên external broker hoặc giữa các session khác nhau. Nó chỉ kiểm soát Spring outbound path của một client session; broker-native ordering và consumer semantics là concern riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-authentication-identity">7. Authentication identity từ WebSocket handshake</a>

<details>
<summary>Xem chi tiết</summary>

Trong setup Servlet-stack thông thường, WebSocket handshake là một HTTP request. Nếu request đã có authenticated `Principal`, Spring mang cùng identity đó vào WebSocket session và cung cấp cho STOMP message handling sau này.

Vì vậy STOMP `login` và `passcode` header do browser gửi không phải authentication mechanism thông thường của Spring WebSocket server; Spring Reference nêu rằng các header này bị bỏ qua hoặc override trong setup này. Authentication thường xảy ra trước hoặc trong HTTP handshake.

Thiết kế token trong STOMP header vẫn có thể dùng inbound `ChannelInterceptor`, nhưng interceptor chỉ là Framework hook. Token validation, authentication object và authorization policy thuộc Spring Security.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-authorization-boundary">8. Ranh giới message authorization</a>

<details>
<summary>Xem chi tiết</summary>

Messaging infrastructure cung cấp destination, message type, header, user identity và interception point để security rule có thể sử dụng. Bản thân nó không quyết định user nào được send tới `/app/admin` hay subscribe `/topic/private`.

Spring Security sở hữu policy đó:

```text
Spring Framework Messaging
  route + decode + invoke + broker/user destination plumbing
Spring Security
  authenticate identity + authorize message operation
```

Module này chỉ cần giải thích identity đi vào flow ở đâu và security hook gắn ở đâu; không lặp lại `AuthorizationManager`, matcher configuration, CSRF policy hoặc chi tiết message-security rule. Khi debug cần phân biệt routing failure với authorization rejection vì chúng nằm ở layer khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-session-boundary">9. Ranh giới Spring Session</a>

<details>
<summary>Xem chi tiết</summary>

WebSocket connection có thể sống lâu hơn HTTP request đã tạo nó, trong khi ứng dụng vẫn có thể phụ thuộc HTTP session state. Spring Framework sở hữu WebSocket/messaging lifecycle; distributed persistence và lifecycle management của HTTP session thuộc Spring Session.

Spring Session integration có thể giữ HTTP session active khi WebSocket traffic diễn ra và có thể đóng WebSocket session khi backing session kết thúc. Điều này hữu ích trong clustered application nơi session không thể chỉ là container-local detail.

Mental model cần nhớ: WebSocket message là long-lived activity gắn với connection; nếu activity đó phải tham gia shared HTTP-session semantics, hãy handoff sang Spring Session thay vì tự triển khai session storage trong messaging code.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-scope">10. WebSocket scope</a>

<details>
<summary>Xem chi tiết</summary>

Spring WebSocket messaging configuration cung cấp bean scope `websocket` có lifetime gắn với một WebSocket session. Scope này phù hợp cho state thực sự connection-specific, ví dụ per-session accumulator hoặc conversation helper.

Vì scoped bean sống ngắn hơn nhiều component khác, scoped proxy thường được dùng khi inject vào bean sống lâu hơn. Session-specific state được backed bởi WebSocket session attributes và destruction callback chạy khi session kết thúc.

Không nên xem scope này là nơi lưu durable user state. Reconnect tạo session mới, một user có thể có nhiều session và process failure làm mất in-memory session state. Persistent business state phải nằm ở datastore thích hợp; WebSocket scope chỉ dành cho connection-lifetime behavior.

</details>

- [Quay lại đầu trang](#back-to-top)
