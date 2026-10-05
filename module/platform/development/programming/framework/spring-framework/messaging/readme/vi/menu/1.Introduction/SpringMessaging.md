<a id="back-to-top"></a>

# Spring Messaging: Mục đích và ranh giới

## Menu
- [1. Vì sao messaging tồn tại](#messaging-purpose)
- [2. Giới hạn của direct coupling và request-response](#direct-coupling-limits)
- [3. Spring Messaging như một transport-neutral foundation](#spring-messaging-foundation)
- [4. Các messaging programming model trong module](#messaging-programming-models)
- [5. Ranh giới với các Spring module lân cận](#neighboring-module-boundaries)
- [6. Learning path khuyến nghị](#messaging-learning-path)

## <a id="messaging-purpose">1. Vì sao messaging tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thường cần truyền thông tin giữa các thành phần nhưng không muốn bên gửi biết chi tiết triển khai của bên nhận. Lời gọi method trực tiếp rất phù hợp khi hai phía nằm trong cùng luồng gọi và bên gọi cần kết quả ngay. Messaging trở nên hữu ích khi chính việc giao tiếp cần một contract rõ ràng: payload cùng metadata có thể được giao cho hạ tầng để route, chuyển đổi, queue hoặc chuyển tới một hay nhiều consumer mà producer không phải gọi trực tiếp từng consumer.

Spring Framework biểu diễn contract đó bằng Message<?> và các abstraction liên quan. Mental model quan trọng là **tách coupling tại ranh giới giao tiếp**. Producer tạo hoặc gửi message; hạ tầng quyết định message tới handler nào. Điều này không tự động biến hệ thống thành durable, distributed, asynchronous hay broker-backed. Các đặc tính đó phụ thuộc channel cụ thể, transport, broker và cách thiết kế ứng dụng.

Trong module này, messaging là nền chung cho programming model WebSocket/STOMP và RSocket của Spring. Các chapter sau đi từ Message model tổng quát tới WebSocket session sống lâu, STOMP destination/broker, rồi tới requester/responder. Thứ tự này giúp learner luôn giữ được câu hỏi cốt lõi: thông tin nào đang di chuyển, và component nào chịu trách nhiệm xử lý nó?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-coupling-limits">2. Giới hạn của direct coupling và request-response</a>

<details>
<summary>Xem chi tiết</summary>

Direct call và HTTP request-response cố ý coupling theo thời gian: bên gọi thực hiện một operation rồi thường chờ operation hoàn tất hoặc thất bại. Mô hình này dễ suy luận và trong nhiều tình huống vẫn là lựa chọn tốt nhất. Vấn đề xuất hiện khi bên gọi không nên sở hữu lifecycle của bên được gọi, khi nhiều consumer cần cùng một event, khi công việc cần tiếp tục sau khi request khởi tạo đã kết thúc, hoặc khi một kết nối hai chiều sống lâu phù hợp hơn.

Messaging đưa vào một contract trung gian. Sender có thể gửi tới logical destination hoặc channel và để hạ tầng chọn handler tiếp theo. Điều này có thể giảm structural coupling, nhưng đổi lại ứng dụng phải quan tâm thêm tới ordering, conversion, lỗi delivery, trạng thái connection, backpressure hoặc hành vi broker.

Quy tắc thực tế là chọn messaging vì **communication model** phù hợp, không phải vì mặc định rằng asynchronous luôn nhanh hơn. Một Spring MessageChannel hoàn toàn có thể deliver đồng bộ ngay trên thread của sender, và một WebSocket interaction vẫn có thể cần application response gần như tức thời. Messaging thay đổi cách các thành phần cộng tác; concurrency và distribution là quyết định riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-messaging-foundation">3. Spring Messaging như một transport-neutral foundation</a>

<details>
<summary>Xem chi tiết</summary>

Module spring-messaging cung cấp các building block trung lập với transport để nhiều công nghệ Spring cùng tái sử dụng. Ở trung tâm là Message<T>, gồm payload và MessageHeaders. MessageChannel biểu diễn ranh giới gửi message, còn MessageHandler biểu diễn code xử lý message. SubscribableChannel bổ sung registry các handler được gọi khi message được deliver.

Tầng này cố ý không định nghĩa WebSocket frame, STOMP command, broker queue hay RSocket interaction type. Những khái niệm đó nằm ở tầng cao hơn hoặc song song. Lợi ích là message handling, conversion, header access, interception và annotation infrastructure có thể dùng chung một vocabulary dù transport thay đổi.

Ranh giới này cũng giữ scope của module rõ ràng. Spring Integration sử dụng các Spring Messaging primitive, nhưng các Enterprise Integration Pattern như router, filter, splitter, aggregator hay integration gateway thuộc Spring Integration. Module này sở hữu Framework primitive và programming model WebSocket/STOMP/RSocket trực tiếp xây trên chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-programming-models">4. Các messaging programming model trong module</a>

<details>
<summary>Xem chi tiết</summary>

Module này đi qua ba programming model có liên hệ:

1. **Spring Messaging foundation** — Message, header, channel, handler, conversion, interception và handler-method infrastructure dùng chung.
2. **WebSocket + STOMP application messaging** — WebSocket transport sống lâu, STOMP làm application-level messaging protocol, cùng broker routing và annotated-controller model của Spring.
3. **Spring RSocket requester/responder model** — request-response, fire-and-forget, streaming, routing, metadata và requester/responder API của Spring.

Raw WebSocket và STOMP không cùng một tầng. WebSocket cung cấp full-duplex transport nhưng text/binary payload tự nó không có application semantics. STOMP bổ sung command, destination, subscription, header, acknowledgement, receipt và broker-oriented semantics. RSocket lại là một protocol/programming model riêng với tính đối xứng requester/responder và streaming interaction dựa trên Reactive Streams.

Các Spring messaging abstraction dùng chung giúp ba phần này liên hệ được với nhau, nhưng không nên gộp chúng thành một protocol. Khi debug, luôn hỏi tầng nào đang sở hữu hành vi hiện tại: generic message infrastructure, WebSocket transport, STOMP routing/broker hay RSocket interaction semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="neighboring-module-boundaries">5. Ranh giới với các Spring module lân cận</a>

<details>
<summary>Xem chi tiết</summary>

Spring Messaging nằm cạnh nhiều Spring project khác nên ownership cần được giữ rõ:

- **Spring Integration** sở hữu Enterprise Integration Pattern và cách ghép integration flow. Module này dạy nền Message/channel mà các flow đó có thể tái sử dụng.
- **Spring Security** sở hữu authentication và authorization policy. Messaging chỉ cung cấp identity/interception point để security tích hợp.
- **Spring Session** sở hữu HTTP-session lifecycle và distributed session management. WebSocket activity có thể tham gia lifecycle đó qua integration nhưng chính sách lưu session nằm ngoài module.
- **Spring Web MVC / WebFlux** sở hữu request-response stack của chúng. Module này tập trung Servlet-stack Spring WebSocket API dùng trong Framework messaging và chỉ xác định ranh giới với reactive WebSocket.
- Broker product sở hữu durability, acknowledgement, queue lifecycle, clustering và hành vi riêng của vendor vượt ra ngoài ranh giới broker relay của Spring.

Các handoff này giữ mental model ổn định: hiểu cơ chế Spring Framework messaging ở đây, rồi chuyển sang module lân cận khi bài toán trở thành security policy, EIP topology, reactive fundamentals hoặc broker administration.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="messaging-learning-path">6. Learning path khuyến nghị</a>

<details>
<summary>Xem chi tiết</summary>

Bắt đầu với transport-neutral model trước khi đi vào STOMP configuration. Trước hết cần hiểu Message là gì, channel và handler nối producer với consumer ra sao, conversion xảy ra ở đâu và ranh giới thread có thể xuất hiện chỗ nào. Vocabulary này được tái sử dụng xuyên suốt module.

Tiếp theo học raw Spring WebSocket: HTTP upgrade, WebSocketHandler, WebSocketSession, client API, origin rule và SockJS fallback. Phần này xác định chính xác transport cung cấp gì và không cung cấp gì.

Sau đó mới thêm STOMP. Học frame và destination trước khi theo dõi clientInboundChannel, brokerChannel và clientOutboundChannel của Spring. Khi đã có luồng end-to-end này, @MessageMapping, broker routing, user destination, client lifecycle, interception, ordering và các vấn đề vận hành sẽ dễ suy luận hơn nhiều.

RSocket được học sau vì đây là application-messaging model khác. Module chỉ dạy interaction/reactive concept cần thiết để hiểu requester/responder API của Spring. Kết thúc module, learner phải có thể trace message end to end và xác định đúng tầng sở hữu từng quyết định routing, lifecycle, conversion hay delivery.

</details>

- [Quay lại đầu trang](#back-to-top)
