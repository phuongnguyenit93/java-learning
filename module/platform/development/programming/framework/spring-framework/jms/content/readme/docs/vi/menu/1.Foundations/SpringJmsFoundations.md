<a id="back-to-top"></a>

# Nền tảng Spring JMS

## Menu
- [Vì sao Spring JMS tồn tại?](#jms-purpose)
- [Đường đi Producer và Consumer trong Spring JMS](#jms-producer-consumer-model)
- [Truy cập đồng bộ và xử lý Message-Driven](#jms-sync-async-consumption)
- [Ranh giới giữa Spring JMS, Jakarta Messaging và Provider](#jms-framework-spec-provider-boundary)
- [Mốc API của Spring Framework 6.1](#jms-61-api-baseline)

## <a id="jms-purpose">Vì sao Spring JMS tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Ứng dụng hiếm khi muốn mỗi chỗ gửi hoặc nhận message đều tự quản lý việc mở/đóng `Connection`, `Session`, tìm destination, chuyển đổi dữ liệu, đổi exception, vận hành listener, recovery và tham gia transaction. Những việc đó cần thiết, nhưng chúng là trách nhiệm hạ tầng. Spring JMS tồn tại để gom chúng về một lớp framework chung, để mã ứng dụng tập trung vào ý định nghiệp vụ như “gửi command này”, “chờ reply này” hoặc “xử lý event này”.

Mô hình tư duy hữu ích nhất là nhìn theo các lớp:

```text
application code
      ↓
Spring JMS abstractions
      ↓
Jakarta Messaging API
      ↓
JMS provider / broker
```

Spring không thay thế Jakarta Messaging. Framework điều phối và đơn giản hóa cách truy cập API đó. `JmsTemplate` quản lý các luồng truy cập đồng bộ; listener container sở hữu vòng đời consumer bất đồng bộ; converter và destination resolver tách code nghiệp vụ khỏi chi tiết tạo message và tìm destination; transaction support kết nối JMS resource với hạ tầng transaction của Spring.

Sự tách lớp này giúp thay đổi policy ở một nơi. Team có thể đổi converter, thay cách resolve destination, chỉnh listener concurrency hoặc bọc `ConnectionFactory` mà không phải sửa mọi producer và consumer.

Đổi lại, abstraction tiện lợi có thể che khuất hành vi runtime nếu xem Spring JMS như “phép thuật”. Người học vẫn phải biết chỗ nào có thể block, lớp nào sở hữu acknowledgement/redelivery semantics, và hành vi nào thực sự đến từ provider. Vì vậy module này luôn giữ lớp Spring hiển thị rõ thay vì chỉ dạy annotation và helper method.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-producer-consumer-model">Đường đi Producer và Consumer trong Spring JMS</a>

<details>
<summary>Xem chi tiết</summary>

Spring JMS có hai đường đi chính cho message và cả hai cùng dựa trên hạ tầng JMS bên dưới.

Đường producer thường ngắn từ góc nhìn mã ứng dụng:

```text
business method
    ↓
JmsTemplate / JmsMessagingTemplate
    ↓
resolve destination + convert message
    ↓
JMS Session / MessageProducer
    ↓
provider
```

Đường consumer có thể đồng bộ hoặc message-driven. Với synchronous receive, code gọi `JmsTemplate.receive...` và thread hiện tại chờ theo `receiveTimeout`. Với message-driven consumption, listener của ứng dụng nằm sau một Spring `MessageListenerContainer`; container sở hữu tài nguyên consumer và gọi mã ứng dụng khi có message.

Phân biệt này hữu ích hơn việc chỉ nghĩ theo “class gửi” và “class nhận”. Cùng một `ConnectionFactory`, converter, chiến lược destination và hạ tầng transaction có thể phục vụ cả hai đường, nhưng lifecycle khác nhau. Mã ứng dụng nhìn một thao tác template như một thao tác ngắn; listener container là thành phần chạy lâu dài và được Spring container quản lý.

Ví dụ, một HTTP handler có thể dùng `JmsTemplate` để publish `OrderPlaced` rồi kết thúc request, trong khi một listener container chạy suốt vòng đời ứng dụng để nhận công việc fulfillment. Provider vẫn cung cấp transport ở cả hai trường hợp; Spring quyết định application đi vào và rời transport đó như thế nào.

Khi phân tích lỗi, hãy xác định đường nào đang lỗi và ai đang sở hữu tài nguyên tại thời điểm đó. Câu hỏi này trở nên rất quan trọng ở các chương về transaction, recovery và caching.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-sync-async-consumption">Truy cập đồng bộ và xử lý Message-Driven</a>

<details>
<summary>Xem chi tiết</summary>

Truy cập đồng bộ và xử lý message-driven giải quyết hai kiểu phối hợp khác nhau.

Với synchronous receive, application chủ động yêu cầu lấy message và thread gọi có thể block trong lúc chờ. Cách này hợp lý khi code thực sự cần semantics kiểu “lấy cho tôi một message ngay bây giờ”, một tác vụ quản trị, hoặc request-reply có thời gian chờ giới hạn. Vì latency nằm trực tiếp trên control flow của bên gọi, `receiveTimeout` phải được chọn có chủ đích.

Message-driven consumption đảo chiều quyền điều khiển. Application không tự viết polling loop. Spring listener container sở hữu việc lấy message rồi gọi application code khi có công việc:

```text
provider → listener container → application listener
```

Mô hình này phù hợp hơn cho consumer chạy liên tục vì container có thể quản lý startup, shutdown, concurrency, recovery, acknowledgement/transaction configuration và việc tái sử dụng resource như một lifecycle thống nhất.

Không nên hiểu “message-driven” đơn giản là “method chạy trên thread khác nên mọi thứ đều asynchronous”. Điểm chính là container kiểm soát việc nhận và dispatch message. Provider, container hay executor cấp thread cụ thể như thế nào là chi tiết runtime sẽ được học sau.

Tương tự, việc `JmsTemplate` là synchronous access template không có nghĩa khi `send` trả về thì downstream business work đã hoàn tất. `send` kết thúc chỉ phản ánh operation gửi cục bộ đã hoàn tất theo contract JMS/provider. Semantics xử lý end-to-end vẫn thuộc messaging system, không phải guarantee do Spring JMS tự tạo ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-framework-spec-provider-boundary">Ranh giới giữa Spring JMS, Jakarta Messaging và Provider</a>

<details>
<summary>Xem chi tiết</summary>

Mọi tương tác Spring JMS đều có ba lớp tham gia. Giữ rõ trách nhiệm từng lớp giúp chẩn đoán lỗi chính xác hơn.

**Spring JMS** sở hữu phần tích hợp framework: template execution, lifecycle của listener container, strategy resolve destination, hook chuyển đổi message, đổi checked exception thành runtime exception, Spring-managed transaction, annotation endpoint infrastructure và các tích hợp lifecycle/observability liên quan.

**Jakarta Messaging** định nghĩa API contract mà Spring gọi như `ConnectionFactory`, `Connection`, `Session`, `Destination`, `Message`, producer, consumer, acknowledgement mode, selector và các khái niệm messaging cơ bản. Module này dùng chúng như prerequisite và chỉ giải thích đủ để hiểu Spring, không dạy lại toàn bộ specification.

**JMS provider** triển khai API và cung cấp hành vi broker/runtime. Kết nối broker, lưu trữ, clustering, protocol, administration và tuning đặc thù provider thuộc lớp này.

Một quy tắc thực tế là hãy xác định hành vi thuộc lớp nào trước khi đổi config. Nếu logical destination name resolve sai, kiểm tra `DestinationResolver` của Spring cùng naming của provider. Nếu listener không phục hồi sau connection failure, kiểm tra recovery của container và connectivity của provider. Nếu delivery guarantee bị hiểu sai, cần xem Jakarta Messaging và provider thay vì giả định một Spring annotation có thể thay đổi guarantee đó.

Spring abstraction cố ý giữ nguyên boundary này. Nó loại bỏ boilerplate quanh API chứ không xóa semantics của messaging system bên dưới. Code quên boundary thường vô tình phụ thuộc vào hành vi đặc thù broker rồi tưởng đó là guarantee của Spring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-61-api-baseline">Mốc API của Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Kho mã này dùng **Spring Framework 6.1.14**, vì vậy mô hình API trong module phải theo đúng dòng phiên bản đó.

Với truy cập JMS đồng bộ, hợp đồng trung tâm là `JmsOperations`, còn `JmsTemplate` là cách triển khai chuẩn. `JmsTemplate` cung cấp send, convert-and-send, synchronous receive, request-reply, browse và các thao tác callback, đồng thời quản lý tài nguyên JMS quanh mỗi thao tác. Mặc định nó dùng `DynamicDestinationResolver` để resolve destination name và `SimpleMessageConverter` để chuyển đổi payload; cả hai chiến lược đều có thể thay thế.

`JmsMessagingTemplate` cũng tồn tại trong 6.1. Nó cung cấp các thao tác theo Spring Messaging cho JMS destination và ủy quyền công việc JMS xuống một `JmsTemplate` bên dưới. Vì vậy đây là cầu nối sang mô hình lập trình chung của `org.springframework.messaging`, không phải thế hệ thay thế mới hơn của `JmsTemplate`.

Với message-driven consumption, Spring 6.1 có họ listener container theo `MessageListenerContainer`. `DefaultMessageListenerContainer` là container standalone linh hoạt chủ đạo, còn `SimpleMessageListenerContainer` là lựa chọn đơn giản hơn với số consumer cố định. Các endpoint bằng `@JmsListener` được xây trên hạ tầng container này, không đi vòng qua nó.

Có một ranh giới phiên bản rất dễ nhầm: **`JmsClient` chỉ thuộc Spring Framework 7.0 trở đi**. Ví dụ hoặc tài liệu dùng API này không được kéo ngược vào mốc 6.1. Khi đọc tài liệu Spring hiện tại, luôn kiểm tra phiên bản của tài liệu trước khi kết luận API có mặt trong kho mã này.

</details>

- [Quay lại đầu trang](#back-to-top)
