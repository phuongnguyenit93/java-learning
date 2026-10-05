<a id="back-to-top"></a>

# Mô hình Message, Channel và Handler

## Menu
- [1. Message gồm payload và headers](#message-payload-headers)
- [2. Tạo message và truy cập headers](#message-building-access)
- [3. MessageChannel](#message-channel)
- [4. SubscribableChannel và subscriber](#subscribable-channel)
- [5. MessageHandler](#message-handler)
- [6. Delivery đồng bộ và delivery qua executor](#channel-delivery-semantics)
- [7. Message conversion](#message-conversion)
- [8. Messaging error và delivery failure](#messaging-errors)

## <a id="message-payload-headers">1. Message gồm payload và headers</a>

<details>
<summary>Xem chi tiết</summary>

Spring biểu diễn một application message bằng hai phần: payload có kiểu và metadata. Message<T> cung cấp getPayload() cùng getHeaders(). Payload là dữ liệu nghiệp vụ được truyền đi; header mang thông tin cần cho routing, conversion, correlation hoặc mô tả payload mà không phải nhét các concern đó vào chính kiểu payload.

MessageHeaders implements Map<String, Object>, nhưng trong Spring Framework 6.1 nó là immutable. Các thao tác như put, putAll, remove hoặc clear sẽ ném UnsupportedOperationException. Hãy xem Message như một value được tạo xong rồi truyền qua ranh giới giữa các component. Nếu bước sau cần header khác, tạo hoặc rebuild message thay vì mutate header map của message đã publish.

MessageHeaders có thể chứa các framework key như id, timestamp, contentType, replyChannel và errorChannel. Việc một header tồn tại không tự động kích hoạt hành vi routing; component tiêu thụ message mới quyết định header đó có ý nghĩa gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-building-access">2. Tạo message và truy cập headers</a>

<details>
<summary>Xem chi tiết</summary>

MessageBuilder là API thường dùng khi mã ứng dụng cần tạo hoặc derive message mà vẫn giữ immutable-header contract:

    Message<OrderCreated> message =
            MessageBuilder.withPayload(event)
                    .setHeader("tenantId", tenantId)
                    .build();

GenericMessage tiện khi payload và header map đã có sẵn. Với metadata theo protocol, Spring cung cấp các MessageHeaderAccessor chuyên biệt như SimpMessageHeaderAccessor và StompHeaderAccessor. Accessor cho phép thao tác typed trong lúc message đang được chuẩn bị; MessageHeaders mà message sau khi build expose cho bên gọi vẫn immutable.

Nên dùng tên header có ý nghĩa rõ và tránh nhét trạng thái ứng dụng mutable vào header chỉ vì giá trị đó vừa kiểu Object. Map của MessageHeaders không thể mutate cấu trúc, nhưng object mutable nằm bên trong header vẫn có thể bị thay đổi và gây race condition.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-channel">3. MessageChannel</a>

<details>
<summary>Xem chi tiết</summary>

MessageChannel là ranh giới gửi trong core model. Producer phụ thuộc channel contract thay vì gọi trực tiếp một consumer cụ thể. Các method send trả về việc message đã được gửi thành công theo delivery contract của channel hay chưa; overload có timeout cho phép implementation áp dụng bounded send khi khái niệm timeout có ý nghĩa.

Abstraction này cố ý không nói nhiều về storage, durability, threading hay fan-out. Những đặc tính đó đến từ channel implementation và hạ tầng xung quanh. Trong STOMP stack, clientInboundChannel, brokerChannel và clientOutboundChannel đều là channel nhưng mỗi channel có vai trò riêng trong luồng end-to-end.

Sự tách biệt này giúp thiết kế ứng dụng rõ hơn: sender tập trung tạo message hợp lệ và chọn ranh giới giao tiếp đúng. Không nên suy ra broker guarantee hay asynchronous execution chỉ vì dữ liệu được gửi qua MessageChannel.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="subscribable-channel">4. SubscribableChannel và subscriber</a>

<details>
<summary>Xem chi tiết</summary>

SubscribableChannel mở rộng MessageChannel bằng subscribe và unsubscribe dành cho MessageHandler. Khi message được deliver qua channel, subscriber được gọi để xử lý; consumer không poll channel để lấy dữ liệu.

Implementation Spring Framework được dùng nhiều trong WebSocket/STOMP infrastructure là ExecutorSubscribableChannel. Nó giữ danh sách subscriber và delegate handler invocation trực tiếp hoặc qua Executor tùy cách khởi tạo. Vì vậy SubscribableChannel là cầu nối quan trọng giữa logical message graph và thread thực sự thực thi handler.

Subscription ở đây là quan hệ hạ tầng trong JVM, không phải broker subscription như STOMP SUBSCRIBE frame. Hai tầng dùng cùng từ nhưng semantics khác: SubscribableChannel quản lý Java MessageHandler subscriber bên trong ứng dụng, còn STOMP subscription là protocol-level registration của client được broker infrastructure xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-handler">5. MessageHandler</a>

<details>
<summary>Xem chi tiết</summary>

MessageHandler là phía nhận của core contract. Method handleMessage(Message<?>) biểu diễn một đơn vị message-processing work. Nhờ đó framework infrastructure có thể route Message mà không cần biết concrete class nào thực hiện công việc.

Tùy vai trò, handler có thể đọc header, convert hoặc delegate payload, gọi mã ứng dụng hoặc forward message khác. Generic interface không ép một hành vi cụ thể. Nó cũng cho phép MessagingException biểu diễn lỗi gắn với message handling.

Nên giữ handler tập trung vào một trách nhiệm xử lý. Khi thiết kế phát triển thành router, filter, splitter, aggregator hoặc enterprise integration flow rõ ràng, ownership chuyển sang Spring Integration. Foundational MessageHandler contract vẫn được dùng, nhưng module này không biến nó thành catalog EIP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="channel-delivery-semantics">6. Delivery đồng bộ và delivery qua executor</a>

<details>
<summary>Xem chi tiết</summary>

Threading phụ thuộc concrete channel và executor. Khi không có Executor, ExecutorSubscribableChannel gọi handler ngay trên thread đang gọi send. Khi có Executor, việc invoke handler được delegate qua executor đó. Việc này có thật sự tạo thread handoff hay asynchronous completion hay không còn phụ thuộc implementation của Executor; ví dụ SyncTaskExecutor vẫn chạy task đồng bộ trên calling thread.

Khác biệt này thay đổi hành vi runtime. Với caller-thread delivery, exception từ handler có thể quay về luồng gọi của sender và thread-local context tự nhiên vẫn nằm trên cùng thread. Khi executor thực sự schedule work bất đồng bộ, send có thể return trước khi handler xử lý xong, exception xảy ra trong executor task, ordering phụ thuộc executor, và ThreadLocal thông thường không tự động trở thành application context trên thread khác.

    ExecutorSubscribableChannel sync = new ExecutorSubscribableChannel();
    ExecutorSubscribableChannel executorBacked =
            new ExecutorSubscribableChannel(taskExecutor);

Không nên nhìn tên channel, hoặc chỉ thấy có Executor, rồi mặc định processing là asynchronous. Hãy kiểm tra semantics của concrete executor. Việc thay caller-thread execution bằng executor thật sự dispatch work sang thread khác là thay đổi semantics chứ không chỉ là tuning performance; mã phụ thuộc vào calling thread có thể hành xử khác sau handoff đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-conversion">7. Message conversion</a>

<details>
<summary>Xem chi tiết</summary>

Message conversion nối Java application type với representation được mang trong message. Contract MessageConverter có thể convert object thành representation phù hợp cho message và convert incoming Message thành target type mà handler cần. CompositeMessageConverter có thể thử nhiều converter theo thứ tự.

Conversion thường được gọi bởi higher-level infrastructure như messaging template hoặc argument/return-value processing của annotated handler. MessageChannel tự nó không inspect một payload bất kỳ rồi tự động convert chỉ vì message được send.

Content type thường tham gia converter selection. Trong STOMP handling, client có thể gửi JSON bytes cùng content-type header và Spring dùng converter đã cấu hình để cung cấp domain object cho @MessageMapping method. Nếu không converter nào hỗ trợ source/target combination, lỗi nằm ở ranh giới conversion, không phải routing.

Nên cấu hình converter rõ ràng và dễ dự đoán. Một custom converter quá rộng có thể làm debugging khó hơn vì nó chấp nhận cả những payload đáng lẽ nên fail sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-errors">8. Messaging error và delivery failure</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi messaging phải được đọc cùng delivery model. Với caller-thread channel delivery, MessagingException từ handler có thể propagate qua send call. Khi executor được cấu hình thật sự schedule handler work bất đồng bộ, sender ban đầu có thể đã return thành công trước khi processing thất bại.

MessageHeaders định nghĩa replyChannel và errorChannel key, nhưng core MessageChannel abstraction không hứa có universal error bus hoặc automatic retry. Một số framework/component cụ thể có thể diễn giải các header đó; Spring Integration có richer error-channel semantics. Trong module này, luôn xác định component nào thực sự sở hữu error handling thay vì giả định chỉ cần có header là đủ.

Ở tầng cao hơn, WebSocketHandler có transport-error callback, annotated messaging có @MessageExceptionHandler, STOMP client có thể nhận ERROR frame, còn broker connection có tín hiệu lỗi riêng. Đây là các nhóm lỗi khác nhau. Chuỗi debug hữu ích là: send thất bại, conversion thất bại, handler code thất bại, routing không match, hay transport/broker gặp lỗi?

</details>

- [Quay lại đầu trang](#back-to-top)
