<a id="back-to-top"></a>

# Destination, chuyển đổi dữ liệu và metadata của message

## Menu
- [Queue và Topic tại ranh giới Spring](#jms-destination-model)
- [DestinationResolver và tên logic](#jms-destination-resolver)
- [Chiến lược MessageConverter](#jms-message-converter)
- [Chuyển đổi Payload và dữ liệu hướng JSON](#jms-payload-conversion)
- [JMS Header, Property và Spring Messaging Header](#jms-headers-properties)
- [Correlation ID và Reply Metadata](#jms-correlation-reply-metadata)

## <a id="jms-destination-model">Queue và Topic tại ranh giới Spring</a>

<details>
<summary>Xem chi tiết</summary>

Ở ranh giới Spring JMS, destination trả lời một câu hỏi đơn giản: **operation JMS này sẽ gửi tới đâu hoặc nhận từ đâu?** Application code có thể làm việc trực tiếp với JMS `Destination`, nhưng Spring cũng cho phép dùng logical name rồi trì hoãn việc chuyển name đó thành queue/topic thật cho hạ tầng.

Sự tách này giúp application service không phụ thuộc trực tiếp vào destination object đặc thù provider. Ví dụ:

```java
jmsTemplate.convertAndSend("orders", command);
```

không có nghĩa Spring xem `"orders"` như một network address chuẩn dùng ở mọi broker. `DestinationResolver` đã cấu hình sẽ diễn giải name đó trong JMS environment hiện tại.

Khi cần dynamic destination qua JMS `Session`, Spring vẫn phải biết domain mong muốn. Với `JmsTemplate`, `pubSubDomain=false` biểu diễn phía point-to-point/queue và là mặc định; `pubSubDomain=true` chọn phía publish-subscribe/topic cho các operation cần phân biệt hai domain này.

Chương này cố ý dừng ở ranh giới tích hợp của Spring. Ngữ nghĩa giao nhận của queue/topic, quy tắc subscription, bảo đảm lưu bền và việc quản trị broker thuộc Jakarta Messaging hoặc provider. Phần Spring cần hiểu là logical destination của ứng dụng được biến thành `Destination` mà hạ tầng template/listener sử dụng như thế nào.

Trong thực tế, nên coi logical destination name là configuration của integration thay vì hard-code provider topology vào business code. Cách này giúp đổi môi trường dễ hơn và giữ naming policy của broker ở ngoài domain logic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-destination-resolver">DestinationResolver và tên logic</a>

<details>
<summary>Xem chi tiết</summary>

`DestinationResolver` là giao diện chiến lược chuyển destination name thành JMS `Destination`. Hợp đồng của nó nhận `Session` hiện tại, logical name và cờ `pubSubDomain`, sau đó trả về queue/topic object mà Spring sẽ dùng.

`JmsTemplate` dùng `DynamicDestinationResolver` mặc định. Chiến lược này gọi `createQueue(...)` hoặc `createTopic(...)` trên JMS `Session` đang hoạt động với name được truyền vào. Dù tên method của JMS có chữ `create`, Spring ở đây đang yêu cầu provider trả về một destination object; lời gọi đó chỉ resolve physical destination đã tồn tại hay còn làm provider tự tạo dynamic destination là hành vi đặc thù provider, không phải bảo đảm dùng chung của Spring. Chiến lược này cố ý đơn giản và phù hợp khi cách resolve động đó khớp với provider đang dùng.

Một số môi trường cần lookup model khác. `JndiDestinationResolver` phù hợp khi destination được quản trị sẵn và publish qua JNDI; custom resolver có thể chứa naming/lookup policy riêng của hệ thống. Điểm thiết kế quan trọng là producer/consumer call site không phải đổi:

```text
"orders"
   ↓
DestinationResolver
   ↓
Destination theo provider/JNDI
```

Lỗi resolution là lỗi hạ tầng và nên fail rõ. Nếu lỗi gõ sai trong logical name bị âm thầm chuyển sang destination dự phòng khác, nó có thể biến thành lỗi định tuyến ở production khó phát hiện hơn nhiều so với một send/startup thất bại ngay.

Nên giữ resolution policy tập trung. Nếu nhiều call site tự dịch cùng một logical name theo các cách khác nhau, rất khó xác định message thực sự đi đâu. Ưu tiên một resolver strategy rõ ràng cho mỗi integration policy, chỉ thay đổi khi runtime environment thật sự cần lookup mechanism khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-message-converter">Chiến lược MessageConverter</a>

<details>
<summary>Xem chi tiết</summary>

Mã ứng dụng thường muốn gửi dữ liệu domain, còn JMS vận chuyển `jakarta.jms.Message`. `MessageConverter` là chiến lược Spring sở hữu ranh giới biểu diễn này.

Hợp đồng có hai chiều:

```text
Java object --toMessage--> JMS Message
Java object <--fromMessage-- JMS Message
```

`JmsTemplate` dùng `SimpleMessageConverter` mặc định. Trong Spring 6.1, converter này hỗ trợ các mapping cơ bản: `String` ↔ `TextMessage`, `byte[]` ↔ `BytesMessage`, `Map` ↔ `MapMessage`, và `Serializable` ↔ `ObjectMessage`. Với inbound message type không nhận diện, converter trả lại raw JMS `Message` thay vì đoán thành một domain type tùy ý.

Mapping `Serializable`/`ObjectMessage` tiện nhưng tạo liên kết chặt giữa định dạng trên đường truyền với Java serialization và tính tương thích class ở cả hai phía. Nên xem đây là lựa chọn nhạy với khả năng tương thích, không phải mặc định ưu tiên cho các service độc lập. Với hợp đồng giữa nhiều service, text/bytes cùng một cách biểu diễn tường minh như JSON thường rõ và ổn định hơn.

Converter được dùng bởi các thao tác như `convertAndSend` và `receiveAndConvert`; listener adapter cũng có thể dùng converter trước khi gọi mã ứng dụng. Vì vậy conversion là hợp đồng dùng chung giữa producer và consumer. Thay converter là thay hợp đồng tích hợp, không chỉ là một thay đổi serialization cục bộ.

Trách nhiệm của converter nên hẹp: biểu diễn application payload dưới dạng JMS message và dựng lại payload mong đợi. Business validation, database lookup, authorization hay orchestration không thuộc conversion logic.

Khi conversion thất bại, Spring ném `MessageConversionException`. Trường hợp này thường báo hợp đồng payload/cấu hình không tương thích. Thử lại cùng một biểu diễn sai với cùng converter thường không giải quyết được vấn đề.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-payload-conversion">Chuyển đổi Payload và dữ liệu hướng JSON</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng thực tế thường ưu tiên cách biểu diễn trung lập hơn Java serialization, ví dụ JSON. Spring cung cấp `MappingJackson2MessageConverter` để chuyển object sang/từ JSON bằng Jackson.

Trong Spring Framework 6.1, converter này mặc định ghi JSON vào `BytesMessage`; có thể cấu hình `MessageType.TEXT` để dùng `TextMessage`. Khi chuyển đổi JMS message đi vào, converter cần đủ thông tin để biết phải dựng Java type nào. Cơ chế mặc định là một **message property chứa type-id** đã được cấu hình, và phải gọi `setTypeIdPropertyName(...)` để converter có thể chuyển JMS message đi vào thành đối tượng Java. Có thể cấu hình type-id mapping để dùng logical id ổn định thay vì đưa trực tiếp tên class Java lên message. Nếu cần chiến lược xác định type khác, có thể tùy biến converter và override `getJavaTypeForMessage(...)`; việc một API receive ở tầng cao hơn yêu cầu target class ở bước sau không tự loại bỏ yêu cầu type-id tại bước chuyển đổi JMS đầu vào.

Ví dụ cấu hình có chủ đích:

```java
MappingJackson2MessageConverter converter =
        new MappingJackson2MessageConverter();
converter.setTargetType(MessageType.TEXT);
converter.setTypeIdPropertyName("message_type");
converter.setTypeIdMappings(Map.of("order", OrderCommand.class));
```

Điều quan trọng không phải “dùng JSON là tự động xong”. Producer và consumer phải thống nhất cách biểu diễn payload, encoding và cách xác định type. Nếu một phía gửi JSON nhưng thiếu metadata mà converter phía nhận cần, deserialization không thể suy ra domain type một cách đáng tin cậy.

Nếu tích hợp đi qua nhiều service, nên tránh ràng buộc hợp đồng trên đường truyền vào tên package/class Java khi có thể dùng logical type id ổn định. Logical id giúp thay đổi cách triển khai mà ít làm vỡ tích hợp hơn và giữ message metadata tập trung vào hợp đồng thay vì cấu trúc code nội bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-headers-properties">JMS Header, Property và Spring Messaging Header</a>

<details>
<summary>Xem chi tiết</summary>

JMS message không chỉ có payload. Nó còn có JMS header chuẩn và application property dùng cho routing, correlation, expiration, reply handling và các concern ở mức transport. Spring Messaging lại có `MessageHeaders` riêng, nên khi `org.springframework.messaging.Message<?>` đi vào JMS cần một lớp mapping.

`MessagingMessageConverter` thực hiện bridge đó. Nó delegate payload conversion xuống một Spring JMS `MessageConverter`, đồng thời delegate header mapping cho `JmsHeaderMapper`. Cách triển khai header mapper mặc định là `SimpleJmsHeaderMapper`.

Mô hình tư duy:

```text
Spring Message
  payload ──> payload MessageConverter ──> JMS message body
  headers ──> JmsHeaderMapper ──────────> JMS headers/properties
```

Vì vậy header map truyền qua `JmsMessagingTemplate` không phải đơn giản được copy nguyên xi vào JMS message. Header name/value phải hợp lệ trong JMS representation, và một số JMS-defined field có semantics riêng thay vì chỉ là property thông thường.

Application metadata nên được thiết kế có chủ đích. Nếu downstream service phụ thuộc vào một header thì header đó đã là một phần integration contract và nên có name/type ổn định. Không nên đẩy tùy tiện framework internals hay trạng thái đối tượng vào JMS property chỉ vì header mapper cho phép map.

Generic Spring Messaging header model thuộc module `messaging`. Ranh giới trách nhiệm của JMS module là cách những header đó được biểu diễn khi một Spring message đi vào hoặc ra khỏi JMS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-correlation-reply-metadata">Correlation ID và Reply Metadata</a>

<details>
<summary>Xem chi tiết</summary>

Request-reply flow cần metadata để nối outbound request với response path. Hai phần quan trọng ở Spring JMS boundary là **reply destination** và **correlation identity**.

JMS cung cấp `JMSReplyTo` để responder biết gửi reply về đâu và `JMSCorrelationID` để liên kết các message có quan hệ. Spring template/listener infrastructure sử dụng các concept này thay vì tạo một request-reply protocol hoàn toàn riêng.

Với `JmsTemplate.sendAndReceive`, Spring tạo temporary reply queue cho operation, gửi request rồi chờ reply tương ứng. Trong listener-style request-reply, `MessageListenerAdapter` hoặc annotation endpoint infrastructure có thể gửi return value của method tới reply destination của incoming message hoặc một response destination đã cấu hình.

Không nên trộn correlation metadata với business identity. Correlation id trả lời “message/exchange này liên quan đến exchange nào?”, còn order id, payment id hay idempotency key trả lời câu hỏi domain. Có hệ thống cố ý dùng cùng một value, nhưng không nên xem hai khái niệm đó mặc định là một.

Tương tự, reply destination chỉ là routing metadata chứ không đảm bảo availability. Requester vẫn cần timeout và chính sách xử lý lỗi, còn responder phải dùng conversion/header convention tương thích. Metadata đúng giúp exchange có địa chỉ và liên kết; nó không làm distributed workflow trở nên atomic hay không thể lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)
