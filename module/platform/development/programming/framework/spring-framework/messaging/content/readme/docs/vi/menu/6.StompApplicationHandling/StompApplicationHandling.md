<a id="back-to-top"></a>

# Xử lý STOMP Application Message

## Menu
- [@MessageMapping handler method](#message-mapping)
- [Destination variable](#destination-variable)
- [Payload conversion](#payload-conversion)
- [Payload validation](#payload-validation)
- [@SubscribeMapping](#subscribe-mapping)
- [@SendTo và reply destination](#send-to)
- [@SendToUser và user reply](#send-to-user)
- [SimpMessagingTemplate](#simp-messaging-template)
- [Xử lý message exception](#message-exception-handling)

## <a id="message-mapping">@MessageMapping handler method</a>

<details>
<summary>Xem chi tiết</summary>

@MessageMapping là cơ chế destination-based dispatch cho STOMP application programming trong Spring. Spring đọc destination của Message đi vào clientInboundChannel, bỏ configured application prefix rồi chọn controller method có mapping pattern phù hợp với phần destination còn lại.

Mapping có thể đặt ở class level và method level. Handler parameter có thể nhận toàn bộ Message, MessageHeaders, typed header accessor, @Header cụ thể, @DestinationVariable, payload hoặc connection Principal. Payload parameter được hiểu ngầm khi không argument resolver nào khác match.

    @Controller
    class OrderMessageController {
        @MessageMapping("/orders")
        @SendTo("/topic/orders")
        OrderView create(OrderCommand command) {
            return service.create(command);
        }
    }

Nên giữ destination handler tập trung vào application work. Broker subscription matching, WebSocket session I/O và protocol framing vẫn là trách nhiệm của infrastructure bên ngoài controller method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="destination-variable">Destination variable</a>

<details>
<summary>Xem chi tiết</summary>

Message destination có thể chứa template variable để một handler đại diện cho cả nhóm logical route. @DestinationVariable bind giá trị được extract từ matched destination vào method argument.

    @MessageMapping("/rooms/{roomId}/messages")
    void post(@DestinationVariable String roomId, ChatMessage message) {
        chatService.post(roomId, message);
    }

Variable đến từ message destination chứ không phải payload. Cách này phù hợp với routing context vốn là một phần của address. Nên giữ destination convention ổn định và tránh duplicate cùng identifier trong cả destination lẫn payload nếu application không có lý do validation rõ ràng để so sánh chúng.

Destination pattern là routing syntax, không phải authorization rule. Việc bên gọi có thể ghi tên room hoặc user vào destination không chứng minh bên gọi có quyền thao tác trên đó; security policy thuộc Spring Security.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="payload-conversion">Payload conversion</a>

<details>
<summary>Xem chi tiết</summary>

Annotated handler thường làm việc với application type thay vì raw byte array. Trước khi invoke method, Spring dùng configured MessageConverter chain để chuyển incoming payload thành handler parameter type. Ở chiều return, converter serialize return value thành representation cho outbound message.

Ví dụ, STOMP SEND mang JSON cùng application/json content type có thể được convert thành OrderCommand khi JSON converter phù hợp đã được cấu hình. Nếu content type, payload representation và target type không được converter nào hỗ trợ, handler invocation không thể tiếp tục.

Conversion nằm giữa routing và application logic: trước hết Spring tìm handler từ destination, sau đó resolve/convert argument rồi mới invoke method. Ranh giới này giúp debug đúng chỗ. Destination mismatch không phải JSON problem, và conversion exception không phải bằng chứng broker thất bại.

WebSocketMessageBrokerConfigurer.configureMessageConverters(...) là extension point khi default converter set cần được bổ sung hoặc thay thế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="payload-validation">Payload validation</a>

<details>
<summary>Xem chi tiết</summary>

Payload validation có thể chạy ngay trong annotated method argument resolution. Đánh dấu payload parameter bằng Jakarta @Valid hoặc Spring @Validated và bảo đảm Validator phù hợp đã được cấu hình; Spring validate converted object trước khi gọi handler.

    @MessageMapping("/orders")
    void create(@Valid OrderCommand command) {
        orderService.create(command);
    }

Validation nằm sau conversion vì constraint áp dụng lên Java object, không áp dụng lên STOMP frame chưa parse. Lỗi validation ngăn handler thực thi theo luồng bình thường và có thể đi vào messaging exception-handling path.

Nên giữ ownership của validation rõ ràng. Chapter này giải thích cách messaging invoke validation. Constraint design, custom Validator mechanics và Bean Validation depth thuộc curriculum validation/data-binding. Security check cũng không nên được ngụy trang thành validation annotation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="subscribe-mapping">@SubscribeMapping</a>

<details>
<summary>Xem chi tiết</summary>

@SubscribeMapping xử lý STOMP SUBSCRIBE message được route tới mã ứng dụng. Nó hữu ích khi việc subscribe cần trả ngay dữ liệu do ứng dụng tạo, chẳng hạn initial snapshot, mà không cần thêm một SEND request riêng.

Return-value rule ở đây khác ordinary @MessageMapping: nếu @SubscribeMapping method không có @SendTo hoặc @SendToUser, Spring gửi return value trực tiếp về connected client và không route reply đó qua broker. Điều này tạo một request-reply pattern gắn với subscription event.

    @SubscribeMapping("/prices/initial")
    PriceBook initialPrices() {
        return priceService.snapshot();
    }

Dùng @SubscribeMapping cho hành vi ứng dụng tại thời điểm subscribe, không dùng nó thay thế broker subscription thông thường. Nếu các update tiếp theo phải broadcast qua broker destination, hãy model destination/subscription đó một cách rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="send-to">@SendTo và reply destination</a>

<details>
<summary>Xem chi tiết</summary>

@SendTo tùy biến nơi return value của message-handling method được gửi tới. Spring convert return value thành Message rồi gửi nó hướng về broker destination hoặc nhiều destination khai báo trong annotation.

    @MessageMapping("/orders")
    @SendTo("/topic/orders")
    OrderView create(OrderCommand command) {
        return service.create(command);
    }

Nếu @MessageMapping thông thường không có explicit @SendTo, Spring có thể derive default broker destination từ inbound destination theo message-handling convention đã cấu hình. Khai báo @SendTo hữu ích khi reply destination là một phần rõ ràng của application contract hoặc khác default.

@SendTo điều khiển reply routing; nó không phải công cụ publish message tùy ý tại bất kỳ thời điểm nào. Với notification sinh ra ngoài handler return path, dùng SimpMessagingTemplate.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="send-to-user">@SendToUser và user reply</a>

<details>
<summary>Xem chi tiết</summary>

@SendToUser route handler return value qua user-destination mechanism của Spring. Thay vì broadcast cùng broker destination tới mọi subscriber, Spring gắn message với user của inbound message rồi resolve thành destination cho active session hoặc các session của user đó.

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    ErrorView handle(OrderRejectedException ex) {
        return new ErrorView(ex.getMessage());
    }

Default user-destination prefix là /user/. Client subscribe theo user-destination convention còn Spring translate sang session-specific broker destination. Chapter 7 sẽ đi sâu vào quá trình resolution này.

Dùng @SendToUser cho user-targeted reply, không dùng nó làm authorization mechanism. Principal và destination resolution xác định gửi tới đâu; Spring Security mới chịu trách nhiệm quyết định user được phép làm gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="simp-messaging-template">SimpMessagingTemplate</a>

<details>
<summary>Xem chi tiết</summary>

SimpMessagingTemplate cho mã ứng dụng gửi message bằng API thay vì buộc mọi publication phải đi qua controller return value. Nó thường được inject vào service/controller và được backing bởi broker-facing MessageChannel mà WebSocket message-broker configuration tạo ra.

    @Service
    class PricePublisher {
        private final SimpMessagingTemplate messaging;

        PricePublisher(SimpMessagingTemplate messaging) {
            this.messaging = messaging;
        }

        void publish(PriceUpdate update) {
            messaging.convertAndSend("/topic/prices", update);
        }
    }

convertAndSend(...) convert application object trước khi gửi. convertAndSendToUser(...) nhắm tới user-destination mechanism của Spring. Template cũng expose send timeout và header customization.

Nên dùng template khi publication bắt nguồn từ timer, HTTP handler, domain service hoặc application event khác. Với request-reply đơn giản từ @MessageMapping, return value thường rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-exception-handling">Xử lý message exception</a>

<details>
<summary>Xem chi tiết</summary>

Exception từ annotated message handler có thể được xử lý bằng @MessageExceptionHandler. Handler có thể khai báo exception type ngay trong annotation hoặc nhận exception làm method argument. Exception method hỗ trợ cùng general messaging argument/return-value model như @MessageMapping.

    @MessageExceptionHandler(OrderRejectedException.class)
    @SendToUser("/queue/errors")
    ErrorView rejected(OrderRejectedException ex) {
        return new ErrorView(ex.getMessage());
    }

Mặc định, @MessageExceptionHandler method áp dụng trong controller class hierarchy nơi nó được khai báo. Khi cần common messaging exception handling xuyên nhiều controller, đặt các method đó trong @ControllerAdvice.

Khi thiết kế error response, cần tách các tầng lỗi. Application exception có thể trở thành structured application message. Lỗi conversion hoặc validation cũng có thể đi vào handler-exception processing khi phát sinh trong pipeline đó. Lỗi WebSocket transport và lỗi broker connection là concern khác, không nên bị che bằng application-level error payload.

</details>

- [Quay lại đầu trang](#back-to-top)
