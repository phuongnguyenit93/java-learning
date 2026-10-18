<a id="back-to-top"></a>

# Sẵn sàng vận hành và ranh giới tích hợp

## Menu
- [Spring JMS Observability trong Framework 6.1: Publish, Process và ranh giới Receive](#jms-observability-61)
- [Kiểm thử Conversion, Listener và Transaction](#jms-testing-strategy)
- [Kiểm thử tích hợp với Provider thật](#jms-real-provider-testing)
- [Dùng Spring JMS trực tiếp hay Spring Integration](#jms-direct-vs-integration)
- [Ranh giới với Spring Messaging, AMQP và Kafka](#jms-neighboring-messaging-boundaries)
- [Mô hình tư duy Spring JMS End-to-End](#jms-end-to-end-synthesis)

## <a id="jms-observability-61">Spring JMS Observability trong Framework 6.1: Publish, Process và ranh giới Receive</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 tích hợp JMS với Micrometer Jakarta JMS instrumentation khi có `io.micrometer:micrometer-jakarta9` trên classpath. Chỉ có Micrometer dependency chưa đủ để ghi dữ liệu hữu ích: phải cấu hình `ObservationRegistry` trên chính JMS component sở hữu operation cần quan sát.

Baseline này có hai observation name chính:

```text
jms.message.publish
    → thời gian gửi JMS message

jms.message.process
    → thời gian ứng dụng xử lý message
```

Cấu hình publication observation trên `JmsTemplate`:

```java
@Bean
JmsTemplate jmsTemplate(
        ConnectionFactory connectionFactory,
        ObservationRegistry observationRegistry) {
    var template = new JmsTemplate(connectionFactory);
    template.setObservationRegistry(observationRegistry);
    return template;
}
```

Cấu hình processing observation trên listener-container factory:

```java
factory.setObservationRegistry(observationRegistry);
```

Khi phương thức `@JmsListener` trả về value và Spring gửi reply, lần send reply đó cũng có thể tạo publish observation. Micrometer JMS instrumentation truyền tracing context qua JMS message headers.

Spring 6.1 cố ý **không** tạo `jms.message.receive` observation cho thời gian block trong `MessageConsumer.receive`. Đo thời gian chờ message không giống đo thời gian ứng dụng xử lý và không tạo trace scope xử lý mong muốn. Đây là một ranh giới quan trọng khi đọc dashboard: không có receive timer là hành vi dự kiến, không mặc nhiên là thiếu instrumentation.

Các observation key mặc định có thông tin về messaging operation, destination, message/correlation identifier khi có và error. Với convention tự định nghĩa, tránh đưa identifier có cardinality cao vào metric label; việc điều tra từng message phù hợp hơn với trace/log correlation.

### Tài liệu tham khảo

- Spring Framework Reference — Observability Support, JMS messaging instrumentation
- Micrometer Jakarta JMS instrumentation được Spring Framework 6.1 sử dụng

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-testing-strategy">Kiểm thử Conversion, Listener và Transaction</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm thử Spring JMS hiệu quả nhất khi mỗi test chứng minh hành vi ở lớp nhỏ nhất thực sự sở hữu hành vi đó. Không có một kiểu test duy nhất có thể đồng thời chứng minh conversion, method binding, transaction rollback, redelivery và provider interoperability.

Một test pyramid hữu ích:

```text
unit test thuần
    → business listener logic và quyết định idempotency

converter test
    → Java object ↔ JMS Message representation

Spring configuration test
    → @JmsListener registration, factory selection, method binding, validation

provider integration test
    → send/receive thật, transaction, redelivery, hành vi selector/subscription
```

Khi có thể, giữ domain handler có thể gọi trực tiếp mà không cần broker:

```java
class OrderListener {
    private final OrderService service;

    @JmsListener(destination = "orders.in")
    void handle(OrderPlaced event) {
        service.apply(event);
    }
}
```

Hành vi nghiệp vụ của `service.apply` được test trực tiếp. Các Spring/JMS integration test riêng sau đó chứng minh annotated endpoint convert và route JMS message thật vào đúng method.

Với transaction test, nên assert observable contract thay vì lời gọi ở mức triển khai. Ví dụ: listener thất bại khiến message đủ điều kiện redelivery dưới transacted session; listener thành công commit; idempotency record ngăn tác động nghiệp vụ trùng lặp. Chỉ verify `Session.rollback()` được gọi trên mock không chứng minh provider redelivery semantics.

Với reply endpoint, test cả destination precedence và conversion: incoming `JMSReplyTo`, static `@SendTo` và dynamic `JmsResponse` là ba contract khác nhau. Validation test cũng nên phân biệt lỗi conversion với lỗi constraint.

Không nên dùng `sleep` làm cơ chế đồng bộ chính cho asynchronous test. Ưu tiên bounded receive, latch, điều kiện chờ có timeout hoặc test-harness mechanism có thể fail xác định khi message mong đợi không tới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-real-provider-testing">Kiểm thử tích hợp với Provider thật</a>

<details>
<summary>Xem chi tiết</summary>

Kiểm thử tích hợp với provider thật trả lời những câu hỏi mock không thể trả lời: destination có resolve đúng không, selector có được chấp nhận không, transaction có rollback thật không, redelivery metadata có xuất hiện không, durable/shared subscription có hoạt động như dự kiến không và connection recovery của provider có khớp giả định production không.

Dùng provider instance hoặc test namespace cô lập với quy trình thiết lập/dọn dẹp xác định. Cơ chế cụ thể có thể là containerized broker, embedded/test broker do provider hỗ trợ hoặc môi trường CI cấp sẵn. Điều quan trọng là test đi qua Jakarta Messaging contract bằng chính provider client library thực tế.

Các kịch bản kiểm thử có giá trị cao cho module này gồm:

- `JmsTemplate` gửi converted payload và consumer nhận đúng representation;
- transacted listener ném exception và provider redeliver theo policy đã cấu hình;
- lần retry thành công không tạo tác động nghiệp vụ trùng lặp;
- selector nhận/loại message đúng dự kiến;
- durable/shared subscription giữ đúng semantics qua lifecycle liên quan của ứng dụng;
- DMLC recovery sau khi provider connection bị gián đoạn;
- request/reply dùng đúng reply destination và correlation metadata.

Mọi asynchronous assertion đều nên có giới hạn thời gian. Một test có thể chờ vô hạn khi broker/configuration lỗi còn tệ hơn một test thất bại rõ ràng vì nguyên nhân gốc bị che bởi CI timeout.

Các redelivery count header, dead-letter address, administration API và connection-pool tuning đặc thù provider nên nằm trong provider integration test hoặc provider documentation. Knowledge của Spring JMS nên tập trung vào phần framework cấu hình và phần nó delegate xuống provider.

Không gọi test dùng mocked `ConnectionFactory` là integration test. Mock hữu ích để kiểm tra hành vi hẹp của đối tượng cộng tác; chỉ provider thật mới chứng minh được provider protocol/resource semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-direct-vs-integration">Dùng Spring JMS trực tiếp hay Spring Integration</a>

<details>
<summary>Xem chi tiết</summary>

Dùng Spring JMS trực tiếp phù hợp khi bài toán tích hợp của ứng dụng thực sự có hình dạng JMS: gửi tới destination, synchronous receive, chạy message-driven listener, convert payload, quản lý JMS resource và tham gia transaction.

Spring Integration nằm ở một lớp abstraction cao hơn. Nó triển khai Enterprise Integration Patterns như channel, router, filter, transformer, splitter, aggregator, service activator cùng protocol adapter/gateway. JMS inbound/outbound adapter và gateway của Spring Integration sử dụng hạ tầng Spring JMS ở bên dưới.

```text
ứng dụng cần direct JMS access
    → Spring Framework JMS

ứng dụng cần integration flow ghép từ các EIP building block
    → Spring Integration
        ↓ có thể dùng
      Spring JMS adapter/gateway
        ↓ dùng
      JmsTemplate / listener container
```

Dùng Spring JMS trực tiếp khi một số ít JMS endpoint map rõ vào application service. Spring Integration có giá trị khi integration topology tự nó trở thành thiết kế quan trọng cần routing, transformation, aggregation, channel composition hoặc nhiều transport.

Lựa chọn Spring Integration không có nghĩa JMS semantics biến mất. Spring Integration JMS adapter vẫn dựa vào destination, transaction, acknowledgement, redelivery và hành vi của provider trong JMS stack bên dưới.

Ngược lại, không nên tự xây một EIP framework bằng hàng loạt phương thức `@JmsListener`. Nếu listener code chủ yếu trở thành routing table, fan-out, filtering, trạng thái aggregation và transport bridge, đó là dấu hiệu nên đánh giá một abstraction chuyên biệt cho integration.

### Tài liệu tham khảo

- Spring Framework Reference — JMS
- Spring Integration Reference — JMS Support

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-neighboring-messaging-boundaries">Ranh giới với Spring Messaging, AMQP và Kafka</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều công nghệ Spring lân cận cùng dùng từ “message”, nhưng mỗi công nghệ sở hữu một lớp abstraction khác nhau.

**Spring Messaging** (`org.springframework.messaging`) cung cấp các primitive ở cấp framework như `Message`, headers, handler-method argument resolution và các abstraction liên quan. Spring JMS dùng các primitive này cho `JmsMessagingTemplate`, `@Header` và việc gọi annotated method. Spring Messaging không tự định nghĩa JMS queue, session, acknowledgement hay provider recovery.

**Spring Framework JMS** sở hữu lớp tích hợp trực tiếp với Jakarta Messaging: `JmsTemplate`, listener container, `@JmsListener`, conversion/header mapping, JMS resource wrapper và transaction integration.

**Spring Integration** ghép integration flow và Enterprise Integration Patterns. JMS channel adapter/gateway của nó sử dụng Spring JMS support ở bên dưới, không phải một JMS client layer thay thế.

**Spring AMQP** và **Spring for Apache Kafka** là các Spring project riêng dành cho hệ messaging tương ứng. Listener container, template, acknowledgement/offset model, retry mechanism và broker semantics của chúng không thể dùng thay thế JMS API chỉ vì cùng có khái niệm producer/consumer.

```text
Spring Messaging
    → generic framework message model

Spring Framework JMS
    → Jakarta Messaging integration

Spring Integration
    → EIP / integration-flow composition, gồm cả JMS adapter

Spring AMQP / Spring Kafka
    → Spring abstraction riêng cho protocol/broker tương ứng
```

Khi migrate giữa các messaging technology, có thể giữ ý định nghiệp vụ nhưng phải đánh giá lại delivery, ordering, transaction, retry, consumer-group/subscription và observability semantics. Sao chép tên một JMS setting sang Kafka hoặc AMQP không phải chiến lược portability.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-end-to-end-synthesis">Mô hình tư duy Spring JMS End-to-End</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình Spring JMS đầy đủ có thể thu gọn thành ranh giới trách nhiệm và một luồng lúc chạy.

```text
application payload
    ↓
JmsTemplate / @JmsListener
    ↓ conversion + headers
Spring JMS infrastructure
    ↓
ConnectionFactory / Session / producer hoặc consumer
    ↓
Jakarta Messaging provider
    ↓
broker destination
```

Ở phía producer, `JmsTemplate` sở hữu phần resource access lặp lại và conversion, còn provider sở hữu delivery thật. Ở phía consumer, `MessageListenerContainer` sở hữu consumer thread, JMS resource, lifecycle, recovery, tương tác acknowledgement/transaction và việc gọi listener endpoint.

Annotation-driven listener thêm một lớp declarative:

```text
@EnableJms
    ↓ phát hiện
@JmsListener endpoint
    ↓ được tạo bởi
JmsListenerContainerFactory
    ↓ được quản lý bởi
JmsListenerEndpointRegistry
    ↓ khi chạy
MessageListenerContainer
```

Độ tin cậy đến từ việc kết hợp đúng nhiều cơ chế chứ không phải một annotation: transacted/managed consumption, chính sách redelivery của provider, tác động nghiệp vụ idempotent, retry/dead-letter có giới hạn và lỗi có thể quan sát. Concurrency là cơ chế điều khiển throughput với đánh đổi về thứ tự và tài nguyên. Caching là tối ưu hóa nhưng phải tôn trọng transaction model.

Observability production trong Spring Framework 6.1 ghi JMS publish/process operation khi cấu hình đúng `ObservationRegistry` và Micrometer Jakarta JMS instrumentation; thời gian chờ của blocking receive cố ý nằm ngoài observation model đó.

Cuối cùng phải giữ rõ ranh giới trách nhiệm. Tài liệu Jakarta Messaging/provider định nghĩa protocol và broker semantics. Spring Framework JMS định nghĩa lớp tích hợp trực tiếp của Spring. Spring Messaging cung cấp message abstraction dùng chung. Spring Integration, AMQP và Kafka sở hữu các mô hình EIP/integration hoặc protocol-specific riêng.

Nếu người học có thể lần theo một message xuyên qua các lớp này và giải thích conversion, lifecycle của listener, kết quả transaction, redelivery, concurrency và observation được quyết định ở đâu, mô hình tư duy end-to-end của module đã hoàn chỉnh.

</details>

- [Quay lại đầu trang](#back-to-top)
