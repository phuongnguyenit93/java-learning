<a id="back-to-top"></a>

# Nền tảng Spring WebSocket Transport

## Menu
- [1. Khi nào WebSocket phù hợp](#websocket-use-cases)
- [2. HTTP Upgrade handshake](#websocket-handshake)
- [3. WebSocketHandler và WebSocketSession](#websocket-handler-session)
- [4. WebSocketClient và WebSocketConnectionManager](#websocket-client)
- [5. Text, binary, ping và pong message](#websocket-message-types)
- [6. Tùy biến handshake và interceptor](#websocket-handshake-customization)
- [7. Origin policy](#websocket-origin-policy)
- [8. SockJS fallback](#sockjs-fallback)
- [9. Ranh giới Servlet WebSocket và reactive WebSocket](#servlet-reactive-websocket-boundary)

## <a id="websocket-use-cases">1. Khi nào WebSocket phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

WebSocket phù hợp khi client và server cần một kết nối full-duplex sống lâu và cả hai phía đều có thể chủ động gửi dữ liệu mà không phải tạo HTTP request mới cho từng exchange. Ví dụ điển hình là collaborative update, live dashboard, notification hoặc interactive messaging nơi polling liên tục gây thêm latency và overhead.

Đổi lại, lifecycle phức tạp hơn. WebSocket connection phải được thiết lập, duy trì, đóng và phục hồi khi mạng gặp lỗi. Mã ứng dụng cũng phải thống nhất ý nghĩa của text hoặc binary payload vì WebSocket tự nó không định nghĩa application message model.

Spring WebSocket API cung cấp abstraction ở mức framework cho handler/session trên Servlet-stack WebSocket runtime. Dùng raw WebSocket khi ứng dụng thực sự muốn tự sở hữu message format và routing. Khi cần destination, subscription, broker-style fan-out, acknowledgement hoặc higher-level messaging semantics, STOMP thường phù hợp hơn trên cùng transport.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-handshake">2. HTTP Upgrade handshake</a>

<details>
<summary>Xem chi tiết</summary>

Một WebSocket connection bắt đầu dưới dạng HTTP request. Client yêu cầu upgrade connection, server kiểm tra request, negotiate các chi tiết WebSocket như sub-protocol, rồi nếu thành công sẽ chuyển từ HTTP request-response thông thường sang WebSocket session persistent.

Spring expose ranh giới này qua handshake infrastructure. HandshakeInterceptor có thể inspect HTTP request/response trước và sau handshake, đồng thời copy các attribute đã chọn vào WebSocketSession sau này. HandshakeHandler thực hiện negotiation và xác định các chi tiết như user Principal khi phù hợp.

Handshake vẫn nằm ở ranh giới web nên cookie, HTTP header, origin information và Principal đã được thiết lập trước đó có thể xuất hiện tại đây. Module này dùng các dữ kiện đó để giải thích session identity và transport lifecycle. Authentication và authorization policy vẫn thuộc Spring Security.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-handler-session">3. WebSocketHandler và WebSocketSession</a>

<details>
<summary>Xem chi tiết</summary>

WebSocketHandler nhận các transport lifecycle callback: afterConnectionEstablished, handleMessage, handleTransportError và afterConnectionClosed. WebSocketSession biểu diễn một connection đã được thiết lập và expose thông tin đã negotiate, attribute được copy từ handshake, Principal nếu có, local/remote address, size limit, trạng thái open cùng operation send/close.

    public final class EchoHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(
                WebSocketSession session, TextMessage message) throws Exception {
            session.sendMessage(new TextMessage("echo: " + message.getPayload()));
        }
    }

Một rule thực tế rất quan trọng: underlying standard WebSocket session không cho phép arbitrary concurrent sends. Khi nhiều application thread có thể ghi vào cùng session, cần serialize việc send hoặc dùng ConcurrentWebSocketSessionDecorator; decorator này tuần tự hóa send và áp dụng buffer/send-time limit đã cấu hình.

Nếu WebSocketHandler để exception thoát ra ngoài, default decorator strategy của Spring sẽ log rồi đóng session với server-error status. Lỗi ứng dụng dự kiến trước nên được xử lý có chủ đích thay vì dùng việc đóng connection làm luồng điều khiển bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-client">4. WebSocketClient và WebSocketConnectionManager</a>

<details>
<summary>Xem chi tiết</summary>

WebSocketClient là contract của Spring để khởi tạo WebSocket handshake phía client. Trong Spring Framework 6.1, execute(...) trả CompletableFuture<WebSocketSession> và future hoàn tất khi session khả dụng. StandardWebSocketClient là implementation dựa trên Jakarta WebSocket thường dùng; SockJsClient cung cấp hành vi fallback theo SockJS.

    WebSocketClient client = new StandardWebSocketClient();
    CompletableFuture<WebSocketSession> future =
            client.execute(handler, "wss://example.test/updates");

WebSocketConnectionManager hữu ích khi connection cần tham gia Spring ApplicationContext lifecycle. Nó giữ WebSocketClient, WebSocketHandler và target URI, mở connection như một lifecycle component rồi đóng khi stop. Nó cũng có thể cấu hình handshake header, sub-protocol và origin.

Dùng direct client API khi application logic chủ động sở hữu connection lifecycle. Dùng connection manager khi long-lived connection là infrastructure nên start/stop cùng application context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-message-types">5. Text, binary, ping và pong message</a>

<details>
<summary>Xem chi tiết</summary>

Ở tầng Spring API, WebSocketHandler nhận các WebSocketMessage như TextMessage, BinaryMessage, PingMessage và PongMessage. Text/binary message mang dữ liệu ứng dụng. Ping/pong là WebSocket control message dùng cho protocol liveness/control chứ không phải application-domain command.

Ranh giới message cần được hiểu rõ. Handler có thể khai báo có hỗ trợ partial message hay không. Nếu partial-message support bật và underlying server hỗ trợ, một logical WebSocket message lớn có thể đến qua nhiều lần gọi handleMessage; WebSocketMessage.isLast() cho biết phần hiện tại có phải phần cuối hay không.

Không nên nhầm WebSocket message type với Spring Messaging Message<?> hoặc STOMP frame. Chúng nằm ở các tầng khác nhau. Chapter 4 giải thích cách STOMP frame chạy trên WebSocket được decode rồi biểu diễn thành Spring Message trong broker-backed programming model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-handshake-customization">6. Tùy biến handshake và interceptor</a>

<details>
<summary>Xem chi tiết</summary>

Handshake customization nên giải quyết vấn đề thuộc ranh giới transport. HandshakeInterceptor có thể inspect request, từ chối handshake bằng cách return false, và populate attribute để sau đó xuất hiện trong WebSocketSession. Custom HandshakeHandler có thể tùy biến hành vi negotiation như xác định Principal hoặc chọn server-specific upgrade behavior.

Với cấu hình thông thường, WebSocketConfigurer và WebSocketHandlerRegistry cung cấp surface gọn:

    registry.addHandler(chatHandler, "/chat")
            .addInterceptors(auditHandshakeInterceptor)
            .setAllowedOrigins("https://app.example.test");

Không đặt application message routing vào handshake code. Handshake chỉ chạy một lần khi connection được thiết lập, trong khi message bình thường có thể tiếp tục rất lâu sau đó. Business command nên nằm trong message handler hoặc, với STOMP, trong annotated message-handling infrastructure.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-origin-policy">7. Origin policy</a>

<details>
<summary>Xem chi tiết</summary>

Browser WebSocket handshake gửi Origin header và API đăng ký WebSocket của Spring cung cấp allowed-origin check. Với Spring Framework 6.1 server registration, default policy là same-origin nếu không cấu hình khác. setAllowedOrigins(...) nhận danh sách origin cụ thể; setAllowedOriginPatterns(...) hỗ trợ pattern linh hoạt hơn.

Origin check là ranh giới bảo vệ hướng tới browser, không phải cơ chế client authentication hoàn chỉnh. Non-browser client có thể tự tạo HTTP header, kể cả Origin. Vì vậy origin configuration nên được xem là một phần của cross-origin transport policy; identity và authorization decision thuộc security layer.

Khi SockJS được bật, origin restriction còn có thể disable các fallback transport không thể kiểm tra origin an toàn. Điều này làm giảm compatibility với một số browser transport cũ, nên thay đổi allowed origins có cả security consequence lẫn transport consequence.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="sockjs-fallback">8. SockJS fallback</a>

<details>
<summary>Xem chi tiết</summary>

SockJS mô phỏng WebSocket-like API bằng cách chọn WebSocket, HTTP streaming hoặc HTTP long-polling tùy capability của server/client. Trong Spring, gọi withSockJS() trên WebSocket hoặc STOMP endpoint sẽ bật SockJS service và các fallback transport.

SockJS có transport lifecycle và hành vi heartbeat riêng. Mặc định Spring SockJS service gửi heartbeat khi connection không có traffic để intermediary không xem connection như bị treo. Khi STOMP heartbeat được negotiate trên SockJS, SockJS heartbeat bị tắt và STOMP heartbeat trở thành cơ chế chịu trách nhiệm cho liveness traffic.

Chỉ nên dùng SockJS khi có compatibility requirement cụ thể. Deployment hiện đại có thể dựa vào native WebSocket thường đơn giản hơn. Fallback không miễn phí: thêm HTTP transport mode đồng nghĩa phải hiểu và test thêm hành vi proxy, timeout, origin và capacity.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="servlet-reactive-websocket-boundary">9. Ranh giới Servlet WebSocket và reactive WebSocket</a>

<details>
<summary>Xem chi tiết</summary>

Chapter này tập trung Servlet-stack Spring WebSocket API trong org.springframework.web.socket vì đây là transport foundation dùng bởi classic WebSocket/STOMP message-broker support của Spring. Reactive WebSocket API thuộc Spring WebFlux và có execution model khác.

Ranh giới về concept khá đơn giản: cả hai đều expose WebSocket connection/message, nhưng handler contract và threading/reactive execution model khác nhau. Không nên mang Reactor hoặc backpressure rule vào Servlet WebSocket handler model chỉ vì cả hai công nghệ đều có thể chở streaming data.

Khi application đã xây quanh WebFlux và end-to-end reactive processing, hãy theo Spring Reactive module cho WebSocket API tương ứng. Khi học broker-backed STOMP stack ở đây, giữ mental model Servlet WebSocket transport rồi chuyển lên STOMP và Spring Messaging channel.

</details>

- [Quay lại đầu trang](#back-to-top)
