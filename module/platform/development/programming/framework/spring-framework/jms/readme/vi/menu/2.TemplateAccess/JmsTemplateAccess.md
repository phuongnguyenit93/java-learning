<a id="back-to-top"></a>

# Truy cập JMS bằng Template

## Menu
- [ConnectionFactory, JmsOperations và JmsTemplate](#jms-template-core-roles)
- [Gửi Message bằng JmsTemplate](#jms-template-send)
- [Synchronous Receive và Timeout](#jms-template-sync-receive)
- [Request-Reply với sendAndReceive](#jms-template-request-reply)
- [Callback với Session và Producer](#jms-template-callbacks)
- [Thiết lập Quality-of-Service](#jms-template-qos)
- [Chuyển đổi JMS Exception sang Unchecked Exception](#jms-template-exception-translation)
- [JmsMessagingTemplate và cầu nối sang Spring Messaging](#jms-messaging-template-bridge)

## <a id="jms-template-core-roles">ConnectionFactory, JmsOperations và JmsTemplate</a>

<details>
<summary>Xem chi tiết</summary>

`ConnectionFactory`, `JmsOperations` và `JmsTemplate` nằm ở ba mức khác nhau của access model.

`ConnectionFactory` là factory phía provider để tạo JMS connection. Spring cần nó, nhưng application service thường không nên tự mở/đóng JMS resource ở mọi operation. `JmsOperations` là contract của Spring cho các thao tác JMS phổ biến, còn `JmsTemplate` là cách triển khai chuẩn của contract đó.

`JmsTemplate` áp dụng template pattern: application code cung cấp phần thay đổi như destination, payload, selector hoặc callback; template sở hữu chuỗi công việc hạ tầng lặp lại quanh nó:

```text
lấy / dùng JMS resource
        ↓
thực hiện operation của bên gọi
        ↓
đổi lỗi truy cập JMS sang Spring exception
        ↓
giải phóng resource theo lifecycle của operation
```

Template nên được cấu hình một lần rồi tái sử dụng như Spring bean. `JmsTemplate` **thread-safe sau khi đã cấu hình xong**, vì vậy một instance đã hoàn tất cấu hình có thể được inject vào nhiều đối tượng cộng tác. Cấu hình thường có `ConnectionFactory`, default destination hoặc destination name, `MessageConverter`, receive timeout và một số QoS option. Không cần tạo `JmsTemplate` mới cho mỗi message chỉ để “tách biệt” operation; template chủ yếu giữ cấu hình, còn JMS resource được Spring lấy theo quy tắc resource management và transaction hiện hành. Mặt còn lại của contract này cũng quan trọng: hãy hoàn tất cấu hình trước khi dùng concurrent, không mutate setting của shared template quanh từng call.

Chất lượng của `ConnectionFactory` vẫn ảnh hưởng trực tiếp đến hiệu năng. Javadoc 6.1 cảnh báo các thao tác ad-hoc sẽ tốn kém nếu connection, session và producer không được dùng pool hoặc dùng chung phù hợp. Các chương sau sẽ tách riêng provider pooling, Spring wrapper của `ConnectionFactory` và caching bên trong listener container vì chúng giải quyết những lifecycle khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-send">Gửi Message bằng JmsTemplate</a>

<details>
<summary>Xem chi tiết</summary>

Operation gửi có thể bắt đầu từ một JMS `Message` được tạo có chủ đích hoặc từ object ứng dụng để Spring chuyển đổi.

Nhóm API `send` ở mức thấp hơn nhận `MessageCreator`. Spring truyền `Session` đang dùng vào callback và callback tạo message:

```java
jmsTemplate.send("orders", session ->
        session.createTextMessage("order-42"));
```

Cách này hữu ích khi cần điều khiển chi tiết JMS-specific lúc tạo message. Với payload ứng dụng thông thường, `convertAndSend` thường rõ hơn:

```java
jmsTemplate.convertAndSend("orders", orderCommand);
```

Ở đây `MessageConverter` đã cấu hình sẽ tạo JMS message. Một số overload cho phép áp dụng `MessagePostProcessor` sau khi convert; đây là chỗ phù hợp để thêm property đặc thù message mà không buộc domain code tự dựng JMS message.

Destination cũng theo cùng nguyên tắc. Lời gọi có thể truyền `Destination`, logical destination name hoặc dùng default destination đã cấu hình. String destination name được `DestinationResolver` của template resolve; nó không tự động là một broker address mà Spring hiểu theo cách cố định.

`send` trả về thành công chỉ cho biết operation gửi này đã hoàn tất theo contract JMS/provider, không chứng minh downstream consumer đã xử lý business event. Đồng thời không nên nhét business orchestration không liên quan vào `MessageCreator` hay producer callback; giữ callback tập trung vào message/producer giúp transaction và resource lifecycle dễ hiểu hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-sync-receive">Synchronous Receive và Timeout</a>

<details>
<summary>Xem chi tiết</summary>

Các operation `JmsTemplate.receive...` thực hiện **synchronous consumption**. Bên gọi chờ trong lúc Spring tạo/lấy consumer và yêu cầu provider trả về message.

Thiết lập quan trọng nhất là `receiveTimeout`, tính bằng mili giây. Mặc định `JmsTemplate` chờ vô hạn. `RECEIVE_TIMEOUT_INDEFINITE_WAIT` biểu diễn hành vi đó, còn `RECEIVE_TIMEOUT_NO_WAIT` (và các giá trị timeout âm) chọn chế độ nhận không chờ; giá trị dương giới hạn thời gian chờ. Nếu không có message trong khoảng timeout, các phương thức receive trả về `null` thay vì tự biến trường hợp “chưa có message” thành lỗi ứng dụng.

Vì vậy timeout phải được xử lý như một phần của control flow:

```java
Message message = jmsTemplate.receive("reply.queue");
if (message == null) {
    // không có message trong khoảng receive đã cấu hình
}
```

`receiveAndConvert` thêm bước chuyển đổi sau khi nhận; các selected variant cho phép dùng JMS selector. Những tiện ích đó không thay đổi bản chất blocking của receive call.

Indefinite wait có thể hợp lý với dedicated worker chỉ làm nhiệm vụ chờ, nhưng rất nguy hiểm trên request thread hoặc thread pool có giới hạn vì bên gọi có thể bị giữ vô thời hạn. Ngược lại, timeout quá ngắn dễ biến latency bình thường thành chuỗi poll rỗng. Hãy chọn timeout dựa trên latency budget của workflow đang gọi.

Với background consumer chạy liên tục, listener container thường là abstraction phù hợp hơn: container sở hữu receive lifecycle và recovery thay vì bắt application code tự dựng polling loop quanh `JmsTemplate`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-request-reply">Request-Reply với sendAndReceive</a>

<details>
<summary>Xem chi tiết</summary>

`sendAndReceive` triển khai request-reply đồng bộ trên JMS. Trong Spring Framework 6.1, `JmsTemplate` gửi request rồi chờ reply trên một temporary queue được tạo cho exchange đó.

Mô hình tư duy:

```text
bên gọi
  │ gửi request
  ▼
request destination
  │
  │ responder gửi reply
  ▼
temporary reply destination
  │
  └──> bên gọi đang chờ
```

API này tiện khi workflow thực sự cần câu trả lời trước khi đi tiếp, nhưng nó thay đổi latency model: thread của bên gọi phải chờ một component khác nhận, xử lý và gửi đáp án. Nếu responder chậm hoặc unavailable, request-reply path cũng chậm hoặc unavailable theo.

Có một ranh giới transaction trong Spring 6.1 rất dễ bỏ sót: `sendAndReceive` dùng local execution path của `JmsTemplate` cho exchange này, với một session cục bộ không transacted, thay vì đơn giản tái sử dụng transactional session đang gắn với thread của bên gọi. Vì vậy không nên giả định chỉ cần bọc bên gọi trong Spring JMS transaction thì temporary-queue request/reply exchange này tự động nằm trong cùng local JMS transaction đó.

Với raw JMS message, dùng `sendAndReceive`. `JmsMessagingTemplate` bổ sung các dạng convert-send-and-receive làm việc với Spring Messaging payload và target class. Ở cả hai hướng, conversion và correlation metadata của hai phía phải tương thích.

Không nên biến mọi asynchronous flow thành request-reply chỉ vì API có sẵn. Command/event một chiều thường cô lập lỗi tốt hơn. Request-reply hợp lý khi requester thực sự phụ thuộc vào kết quả và có thể định nghĩa timeout cùng chính sách xử lý lỗi rõ ràng.

Ngoài ra cần tách reply correlation khỏi idempotency của nghiệp vụ. Ghép đúng reply với request không tự làm repeated request trở nên an toàn; duplicate-processing vẫn là vấn đề riêng của delivery semantics và application design.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-callbacks">Callback với Session và Producer</a>

<details>
<summary>Xem chi tiết</summary>

Template bao phủ phần lớn operation thường dùng, nhưng Spring vẫn cho phép truy cập JMS API ở mức thấp hơn khi thật sự cần.

`SessionCallback<T>` chạy với một JMS `Session` do Spring quản lý:

```java
String result = jmsTemplate.execute(session -> {
    // dùng Session hiện tại cho một operation có phạm vi rõ
    return "done";
});
```

`ProducerCallback<T>` tương tự nhưng cung cấp `Session` cùng `MessageProducer` gắn với destination. Các callback này hữu ích khi operation khó diễn đạt qua API send/receive chuẩn nhưng vẫn muốn template sở hữu việc lấy và dọn resource.

Mô hình tư duy là **mượn resource, làm việc JMS có phạm vi rõ, rồi trả quyền sở hữu lại cho template**. Callback không chuyển quyền sở hữu resource cho application. Không giữ `Session`, producer, consumer hoặc object phụ thuộc vào chúng để dùng sau khi callback đã kết thúc; lifecycle của chúng thuộc template/transaction context.

Callback cũng nên chỉ chứa logic hạ tầng cần thiết. Nếu callback bắt đầu điều phối nhiều business operation không liên quan, transaction/resource scope sẽ khó đọc và khó kiểm tra.

Ưu tiên API cao nhất có thể diễn đạt đúng nhu cầu: `convertAndSend` cho payload thông thường, `send` khi cần tự tạo JMS message, và callback khi surface chuẩn thật sự không đủ. Cách này làm low-level escape hatch nổi bật trong code review thay vì biến nó thành đường đi mặc định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-qos">Thiết lập Quality-of-Service</a>

<details>
<summary>Xem chi tiết</summary>

JMS producer có thể áp dụng quality-of-service như delivery mode, priority, time-to-live và delivery delay. `JmsTemplate` đưa các setting này vào cấu hình chung để application không phải chỉnh từng producer thủ công.

Một quy tắc dễ bỏ sót trong Spring 6.1 là `deliveryMode`, `priority` và `timeToLive` chỉ được template chủ động dùng khi **explicit QoS đã được bật**. Việc gọi setter cho các giá trị đó chưa có nghĩa template chắc chắn ghi đè giá trị mặc định của provider ở mỗi lần gửi. Gọi `setQosSettings(...)` vừa gom các giá trị QoS chính vào `QosSettings`, vừa tự bật explicit QoS.

`deliveryDelay` là setting riêng cho delayed delivery khi JMS/provider hỗ trợ. Template cũng cho phép bật/tắt message ID và timestamp.

Những field này là transport metadata, không phải business workflow policy. Priority không đảm bảo một quy tắc thứ tự nghiệp vụ mang tính toàn cục, và time-to-live không thay thế application deadline hay compensation strategy. Semantics cụ thể vẫn do Jakarta Messaging và provider quyết định.

Nên cấu hình một số ít producer policy rõ ràng thay vì thay QoS liên tục trước từng call. `JmsTemplate` là bean giữ cấu hình dùng chung; việc mutate cấu hình quanh các call concurrent rất khó reasoning. Nếu hai loại traffic cần policy khác hẳn nhau, hai template bean cấu hình riêng thường rõ hơn việc thay đổi một template dùng chung ở runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-template-exception-translation">Chuyển đổi JMS Exception sang Unchecked Exception</a>

<details>
<summary>Xem chi tiết</summary>

Jakarta Messaging dùng checked `JMSException` cho lỗi truy cập API. Nếu mọi Spring service đều phải catch rồi wrap những exception đó, chi tiết hạ tầng sẽ lan ra toàn tầng ứng dụng.

Spring JMS vì vậy chuyển lỗi truy cập JMS sang hierarchy unchecked `org.springframework.jms.JmsException`. `JmsTemplate` và hạ tầng truy cập thực hiện translation này quanh template operation.

Lợi ích chính là về kiến trúc: application code có thể để lỗi hạ tầng nổi lên tới ranh giới sở hữu retry, rollback, error mapping hoặc logging policy. Bên gọi không cần những khối `catch (JMSException)` chỉ wrap rồi throw lại cùng một lỗi.

Unchecked không có nghĩa là có thể bỏ qua. Send vẫn có thể thất bại vì không lấy được connection, không resolve được destination, conversion lỗi hoặc provider từ chối operation. Exception sau translation vẫn biểu diễn một lỗi thật của hạ tầng messaging, chỉ là nó phù hợp với runtime exception model của Spring.

Chỉ catch Spring JMS exception ở nơi code có thể đưa ra quyết định thực sự, ví dụ đổi sang lỗi ở ranh giới ứng dụng, kích hoạt phương án dự phòng cụ thể hoặc thêm ngữ cảnh rồi ném lại lỗi. Không nên catch rộng mọi `RuntimeException` quanh messaging rồi lặng lẽ tiếp tục vì rất dễ che một send/receive thất bại và phá transaction/error-handling semantics.

Lỗi conversion cũng cần được phân biệt: `MessageConversionException` thường báo object ứng dụng hoặc message representation không chuyển đổi được; đây thường là lỗi contract/configuration hơn là broker outage tạm thời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-messaging-template-bridge">JmsMessagingTemplate và cầu nối sang Spring Messaging</a>

<details>
<summary>Xem chi tiết</summary>

`JmsMessagingTemplate` là cầu nối giữa generic Spring Messaging model và Spring JMS. Nó triển khai `JmsMessageOperations` cùng các contract gửi, nhận và request-reply chung cho JMS destination.

Thiết kế cốt lõi là composition: `JmsMessagingTemplate` dùng một `JmsTemplate` bên dưới. Constructor nhận `ConnectionFactory` sẽ tạo ngầm một `JmsTemplate`; constructor nhận `JmsTemplate` cho phép tái sử dụng toàn bộ cấu hình JMS đã có.

Nhờ đó application code có thể làm việc theo kiểu Spring Messaging với `Message<?>`, payload và header map:

```java
jmsMessagingTemplate.convertAndSend(
        "orders",
        orderCommand,
        Map.of("tenant", "acme"));
```

Bridge sẽ map model này sang JMS qua messaging converter và delegate công việc JMS xuống `JmsTemplate`. Cách này hữu ích khi codebase đã dùng convention của Spring Messaging và muốn giữ cùng kiểu payload/header API giữa nhiều transport.

Tuy nhiên generic messaging abstraction không thuộc ranh giới trách nhiệm của JMS module. `Message`, `MessageHeaders`, channel và Spring Messaging model rộng hơn thuộc module `messaging`; ở đây chỉ học cách JMS tham gia vào model đó.

`JmsMessagingTemplate` cũng không phải API thay thế thế hệ mới cho `JmsTemplate`. Trong Spring 6.1, hai loại cùng tồn tại: chọn `JmsTemplate` khi code cần direct JMS-centric access; chọn `JmsMessagingTemplate` khi Spring Messaging-style payload, header hoặc typed request-reply làm interface của application rõ hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
