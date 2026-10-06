<a id="back-to-top"></a>

# Luồng STOMP Message trong Spring

## Menu
- [Bật WebSocket message broker với @EnableWebSocketMessageBroker](#enable-websocket-message-broker)
- [WebSocketMessageBrokerConfigurer như configuration extension point](#websocket-message-broker-configurer)
- [Đăng ký STOMP endpoint](#register-stomp-endpoints)
- [Application và broker destination prefix](#destination-prefixes)
- [clientInboundChannel](#client-inbound-channel)
- [clientOutboundChannel](#client-outbound-channel)
- [brokerChannel](#broker-channel)
- [Luồng message qua application handler](#application-message-flow)
- [Luồng message hướng tới broker](#broker-message-flow)

## <a id="enable-websocket-message-broker">Bật WebSocket message broker với @EnableWebSocketMessageBroker</a>

<details>
<summary>Xem chi tiết</summary>

@EnableWebSocketMessageBroker bật broker-backed messaging infrastructure của Spring cho higher-level protocol như STOMP trên WebSocket. Annotation này import configuration tạo message channel, annotation-based message handler, broker-side component, WebSocket sub-protocol handling và hạ tầng hỗ trợ cần cho luồng phía server.

Vì vậy annotation này nhiều hơn một endpoint switch. Nó thiết lập internal message graph mà các section sau gọi tên rõ: clientInboundChannel mang message đã decode từ client, brokerChannel mang message phía server hướng tới broker, còn clientOutboundChannel mang output từ broker/application về connected client.

Đặt annotation trên @Configuration class và implement WebSocketMessageBrokerConfigurer khi cần customize default. Bản thân annotation không quyết định application destination hay broker topology; các quyết định đó nằm trong configurer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="websocket-message-broker-configurer">WebSocketMessageBrokerConfigurer như configuration extension point</a>

<details>
<summary>Xem chi tiết</summary>

WebSocketMessageBrokerConfigurer là Java configuration extension point chính quanh @EnableWebSocketMessageBroker. Các callback của nó cho phép application đăng ký STOMP endpoint, cấu hình application/broker routing model, tune inbound/outbound channel, tune WebSocket transport limit, thêm custom argument/return-value handler và điều chỉnh message conversion.

    @Configuration
    @EnableWebSocketMessageBroker
    class MessagingConfig implements WebSocketMessageBrokerConfigurer {
        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws");
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.setApplicationDestinationPrefixes("/app");
            registry.enableSimpleBroker("/topic");
        }
    }

Nên giữ configuration đúng tầng mà nó điều khiển. Endpoint registration cấu hình WebSocket/STOMP entry point; message-broker configuration điều khiển destination routing; channel callback điều khiển đặc tính thực thi. Trộn các trách nhiệm này khiến lỗi message flow khó debug hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="register-stomp-endpoints">Đăng ký STOMP endpoint</a>

<details>
<summary>Xem chi tiết</summary>

STOMP endpoint là HTTP/WebSocket handshake URL mà client kết nối tới trước khi bất kỳ STOMP frame nào có thể chạy. Ví dụ registry.addEndpoint("/ws") expose /ws làm connection entry point. Nó không phải application destination như /app/orders và cũng không phải broker destination như /topic/orders.

Endpoint registration có thể thêm HandshakeInterceptor, cấu hình allowed origins và bật SockJS fallback:

    registry.addEndpoint("/ws")
            .setAllowedOrigins("https://app.example.test")
            .withSockJS();

Sau khi WebSocket connection được thiết lập, STOMP frame mang destination header riêng. Việc phân biệt endpoint URL với STOMP destination rất quan trọng khi debug: không connect được là handshake/transport issue; SEND hoặc SUBSCRIBE đã connect nhưng route sai là STOMP/Spring messaging issue.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="destination-prefixes">Application và broker destination prefix</a>

<details>
<summary>Xem chi tiết</summary>

Destination prefix quyết định component phía server nào sẽ nhận message. Application prefix như /app đánh dấu message dành cho annotated application handler. Trước khi match @MessageMapping, Spring loại prefix này nên SEND tới /app/orders có thể match @MessageMapping("/orders").

Broker prefix như /topic và /queue đánh dấu destination do configured broker xử lý. Với built-in simple broker, các tên prefix này chỉ là routing convention; /topic và /queue không tự tạo vendor-grade topic/queue semantics. Với external broker relay, destination model của broker mới trở thành yếu tố quyết định.

    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");

Nên chọn prefix tách biệt và dễ đọc. Convention overlap hoặc mơ hồ khiến khó xác định frame nên tới mã ứng dụng hay bypass application để đi thẳng broker.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="client-inbound-channel">clientInboundChannel</a>

<details>
<summary>Xem chi tiết</summary>

clientInboundChannel là điểm vào phía ứng dụng cho message nhận từ connected STOMP client sau khi WebSocket transport đã decode. CONNECT, SUBSCRIBE, SEND, DISCONNECT và event liên quan trở thành Spring Message rồi đi vào channel này để được xử lý tiếp.

Nhiều infrastructure handler có thể subscribe channel. Tùy message type và destination, inbound message có thể được xử lý bởi annotated-method infrastructure, message broker, user-destination handling hoặc framework component khác.

Trong broker configuration, channel này được executor-back nên inbound handling có thể vượt qua ranh giới thread. Mã ứng dụng không nên giả định WebSocket I/O thread cũng là thread gọi @MessageMapping. Executor sizing, ordering và interception của channel là các vấn đề vận hành được học ở chapter sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="client-outbound-channel">clientOutboundChannel</a>

<details>
<summary>Xem chi tiết</summary>

clientOutboundChannel mang các message đã sẵn sàng deliver tới WebSocket client. Broker output, resolved user-destination message và application reply cuối cùng đi qua tuyến outbound này trước khi Spring encode chúng thành STOMP frame và ghi vào đúng WebSocket session.

Channel tách việc tạo message khỏi socket I/O. Broker component có thể publish Spring Message mà không trực tiếp thao tác WebSocketSession; tầng WebSocket sub-protocol dùng session metadata trong message header để tìm đúng client connection.

Giống inbound channel, clientOutboundChannel mặc định cũng executor-backed trong broker setup. Concurrent execution giúp throughput nhưng nghĩa là publication order cần được xử lý rõ nếu application phụ thuộc strict ordering. Spring có preserve-order option được trình bày trong chapter lifecycle/ordering.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-channel">brokerChannel</a>

<details>
<summary>Xem chi tiết</summary>

brokerChannel là tuyến cho message sinh ra ở phía server và cần gửi tới configured message broker. Return value của @MessageMapping method, kết quả @SendTo/@SendToUser sau khi resolve, hoặc application component dùng SimpMessagingTemplate đều có thể cuối cùng tạo message trên tuyến này.

Channel này khác clientInboundChannel về mental model. Inbound channel biểu diễn traffic đi vào từ remote client. brokerChannel biểu diễn application-originated hoặc framework-resolved message đi về broker để subscription matching và distribution.

Ranh giới này hữu ích khi trace lỗi: nếu controller đã chạy và tạo reply hợp lệ nhưng subscriber không nhận được, kiểm tra broker-bound destination và tuyến outbound. Nếu controller chưa từng chạy, bắt đầu sớm hơn ở clientInboundChannel và application destination mapping.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-message-flow">Luồng message qua application handler</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử client gửi STOMP SEND frame tới /app/greeting. Tầng WebSocket decode frame và Spring tạo Message có destination /app/greeting. Message này đi vào clientInboundChannel.

Vì /app được cấu hình làm application destination prefix, Spring route message tới annotated handler infrastructure thay vì đưa trực tiếp cho broker. Prefix được bỏ trước khi matching nên @MessageMapping("/greeting") có thể xử lý. Payload conversion và argument resolution xảy ra trước khi method được invoke.

Nếu method return value, Spring convert giá trị đó thành outbound Message rồi chọn reply destination. Mặc định broker destination được derive từ inbound destination; @SendTo và @SendToUser có thể override. Message kết quả được gửi qua brokerChannel, broker tìm subscription phù hợp rồi delivery tiếp tục qua clientOutboundChannel tới WebSocket session tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="broker-message-flow">Luồng message hướng tới broker</a>

<details>
<summary>Xem chi tiết</summary>

Một số client message hướng broker ngay từ đầu. SUBSCRIBE tới /topic/greeting đi vào clientInboundChannel rồi được broker xử lý và lưu subscription. SEND có destination thuộc configured broker prefix cũng có thể bypass annotated application handler và đi thẳng tới broker processing.

Khi broker sau đó nhận message cho /topic/greeting, nó match các subscription hiện tại và tạo outbound message cho client session liên quan. Những message đó đi qua clientOutboundChannel, được encode thành STOMP MESSAGE frame và ghi qua WebSocket.

Luồng này cho thấy destination prefix là một phần architecture chứ không phải decoration. /app diễn đạt rằng mã ứng dụng phải xử lý trước; /topic hoặc /queue thường diễn đạt broker sở hữu destination. Nếu ứng dụng vô tình gửi business command thẳng vào broker prefix, không @MessageMapping method nào chạy để validate hay thực thi command đó.

</details>

- [Quay lại đầu trang](#back-to-top)
