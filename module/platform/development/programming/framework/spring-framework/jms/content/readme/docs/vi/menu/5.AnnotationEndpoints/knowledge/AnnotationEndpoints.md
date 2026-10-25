<a id="back-to-top"></a>

# Listener Endpoint điều khiển bằng Annotation

## Menu
- [Bật Annotation-driven JMS bằng @EnableJms](#jms-enable-jms)
- [JmsListenerContainerFactory và quá trình tạo Container](#jms-listener-container-factory)
- [JmsListenerEndpointRegistry và vòng đời Endpoint](#jms-listener-endpoint-registry)
- [Chữ ký phương thức @JmsListener](#jms-listener-method-signature)
- [Tích hợp Payload Conversion, Header và Validation](#jms-listener-conversion-validation)
- [Xử lý phản hồi với JMSReplyTo, @SendTo và JmsResponse](#jms-listener-replies)
- [Đăng ký Endpoint bằng Code](#jms-programmatic-endpoints)

## <a id="jms-enable-jms">Bật Annotation-driven JMS bằng @EnableJms</a>

<details>
<summary>Xem chi tiết</summary>

`@EnableJms` biến các phương thức listener được annotate thành JMS endpoint hoạt động khi ứng dụng chạy. Annotation này import cấu hình bootstrap của Spring JMS, từ đó đăng ký hạ tầng quét các Spring-managed bean để tìm `@JmsListener`. Nó không tự tạo broker, `ConnectionFactory` hay destination; các thành phần đó vẫn thuộc cấu hình ứng dụng/provider.

Có thể hình dung luồng như sau:

```text
phương thức trên Spring-managed bean có @JmsListener
        ↓ được JmsListenerAnnotationBeanPostProcessor phát hiện
metadata JmsListenerEndpoint
        ↓ chuyển cho JmsListenerContainerFactory
MessageListenerContainer
        ↓ nhận JMS message
gọi phương thức listener
```

Ở cấu hình thông thường, ứng dụng khai báo một `JmsListenerContainerFactory` bean có tên `jmsListenerContainerFactory`. Từng endpoint có thể chọn factory khác bằng thuộc tính `containerFactory` của annotation; `JmsListenerConfigurer` cũng có thể thiết lập default factory rõ ràng cho registrar.

```java
@Configuration
@EnableJms
class JmsConfiguration {

    @Bean
    DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
        return factory;
    }
}

@Component
class OrderListener {

    @JmsListener(destination = "orders.in")
    void handle(OrderPlaced order) {
        // xử lý nghiệp vụ
    }
}
```

Chỉ bean do Spring quản lý mới tham gia cơ chế annotation này. Nếu tự tạo `OrderListener` bằng `new`, đối tượng đó nằm ngoài container nên `@JmsListener` trên nó không được hạ tầng Spring phát hiện.

**Quy tắc thực tế:** xem `@EnableJms` như công tắc bật việc phát hiện endpoint. Reliability, concurrency, transaction, destination resolution, conversion, recovery và observability là chính sách của listener container/factory được tạo cho các endpoint đó.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `EnableJms`
- Spring Framework Reference — JMS annotation-driven listener endpoints

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-container-factory">JmsListenerContainerFactory và quá trình tạo Container</a>

<details>
<summary>Xem chi tiết</summary>

`JmsListenerContainerFactory` là factory dùng để tạo runtime container phục vụ một endpoint. Endpoint mô tả *lắng nghe cái gì*; factory cung cấp chính sách dùng lại cho lúc chạy như `ConnectionFactory`, destination resolver, transaction mode, acknowledgement mode, converter, concurrency, error handling, recovery và observation registry.

`DefaultJmsListenerContainerFactory` tạo `DefaultMessageListenerContainer` (DMLC). Đây là lựa chọn phổ biến cho Spring JMS chạy độc lập vì DMLC sở hữu polling consumer, recovery, dynamic concurrency, caching và khả năng phối hợp external transaction manager.

```java
@Bean
DefaultJmsListenerContainerFactory reliableJmsFactory(
        ConnectionFactory connectionFactory,
        MessageConverter messageConverter) {

    var factory = new DefaultJmsListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setSessionTransacted(true);
    factory.setConcurrency("3-10");
    factory.setRecoveryInterval(5_000L);
    return factory;
}

@JmsListener(
        destination = "orders.in",
        containerFactory = "reliableJmsFactory",
        concurrency = "5-12")
void handle(OrderPlaced order) {
}
```

Giá trị `concurrency` tại endpoint sẽ override concurrency từ factory cho listener đó. Các thuộc tính khác như `selector` và `subscription` cũng trở thành cấu hình riêng của endpoint/container tương ứng.

Tách factory khỏi listener bean giúp nhiều listener dùng chung một chính sách vận hành nhưng vẫn cho phép endpoint đặc biệt chọn factory khác. Ví dụ listener billing chậm có thể dùng concurrency nhỏ hơn và transaction/recovery khác mà không ảnh hưởng listener khác.

Không nên coi factory là consumer đang chạy. Factory tạo container thật cho từng endpoint; lifecycle của các container đã tạo do endpoint registry quản lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-endpoint-registry">JmsListenerEndpointRegistry và vòng đời Endpoint</a>

<details>
<summary>Xem chi tiết</summary>

`JmsListenerEndpointRegistry` sở hữu các `MessageListenerContainer` được tạo cho annotation-driven endpoint và phối hợp lifecycle của chúng với application context. Nhờ đó một phương thức `@JmsListener` có thể bắt đầu consume mà application code không phải tự gọi `start()`.

Các listener container được registry quản lý thay vì đăng ký như bean thông thường trong application context. Vì vậy không nên dựa vào việc autowire từng generated container theo type. Với endpoint cần quản trị, hãy đặt `id` ổn định và truy cập container qua registry.

```java
@JmsListener(id = "order-consumer", destination = "orders.in")
void handle(OrderPlaced order) {
}

@Component
class JmsListenerControl {
    private final JmsListenerEndpointRegistry registry;

    JmsListenerControl(JmsListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    void pauseOrders() {
        MessageListenerContainer container =
                registry.getListenerContainer("order-consumer");
        if (container != null) {
            container.stop();
        }
    }
}
```

Registry implement các lifecycle contract của Spring và truyền thao tác start/stop xuống những container mà nó quản lý. Cơ chế này hữu ích cho thao tác vận hành có kiểm soát như tạm dừng consumer khi bảo trì, nhưng không nên liên tục bật/tắt consumer để thay thế back-pressure, retry hay flow control ở broker.

Endpoint đăng ký bằng code cũng dùng cùng registry. Vì thế annotation-driven và programmatic endpoint hội tụ về cùng mô hình lifecycle khi chạy, thay vì tạo hai hệ consumer độc lập.

**Điểm dễ nhầm:** registry quản lý lifecycle của container, không tự làm cho đối tượng nghiệp vụ trở nên thread-safe. Nếu listener tự tạo thread không được quản lý hoặc giữ trạng thái dùng chung có thể thay đổi, ứng dụng vẫn phải giải quyết các vấn đề đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-method-signature">Chữ ký phương thức @JmsListener</a>

<details>
<summary>Xem chi tiết</summary>

Một phương thức `@JmsListener` được Spring gọi thông qua hạ tầng handler-method của Spring Messaging. Nhờ vậy chữ ký phương thức có thể tập trung vào input nghiệp vụ thay vì tự bóc tách mọi `jakarta.jms.Message`, nhưng vẫn có thể truy cập raw JMS object khi thật sự cần.

Các dạng tham số thường dùng gồm:

- payload đã convert như `OrderPlaced` hoặc `String`;
- `@Payload` để chỉ rõ payload argument, kể cả khi cần validation;
- `@Header` / `@Headers` để lấy Spring Messaging header, trong đó có các JMS header được map vào;
- `org.springframework.messaging.Message<T>` khi cần cả payload lẫn headers;
- `jakarta.jms.Message` khi cần metadata hoặc hành vi riêng của JMS/provider;
- `jakarta.jms.Session` khi listener cần session hiện tại, ví dụ cho response phụ thuộc session.

```java
@JmsListener(destination = "orders.in")
void handle(
        OrderPlaced order,
        @Header(JmsHeaders.CORRELATION_ID) String correlationId,
        jakarta.jms.Message rawMessage) throws JMSException {

    log.info("order={}, correlation={}, redelivered={}",
            order.id(), correlationId, rawMessage.getJMSRedelivered());
}
```

Nên chọn chữ ký hẹp nhất đủ biểu đạt nhu cầu của ứng dụng. Chỉ nhận domain payload giúp business code ít phụ thuộc JMS. Chỉ thêm raw JMS message hoặc `Session` khi metadata hoặc hành vi ở cấp session thực sự là một phần của use case.

`@JmsListener` là repeatable annotation nên một phương thức có thể khai báo nhiều listener endpoint. Mỗi annotation tạo endpoint/container tương ứng trỏ về cùng phương thức; nó không gộp nhiều destination thành một consumer.

Giá trị trả về cũng có ý nghĩa: non-void return có thể trở thành reply message. Cách chọn reply destination cần tách riêng vì `JMSReplyTo`, `@SendTo` và `JmsResponse` có vai trò khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-conversion-validation">Tích hợp Payload Conversion, Header và Validation</a>

<details>
<summary>Xem chi tiết</summary>

Quanh một listener có annotation có hai lớp chuyển đổi liên quan. Trước hết Spring JMS dùng JMS `MessageConverter` được cấu hình trên listener-container factory để chuyển `jakarta.jms.Message` từ provider thành đối tượng của ứng dụng. Sau đó hạ tầng handler-method của Spring ánh xạ message đã chuyển đổi vào các tham số phương thức và có thể áp dụng validation cho payload khi lớp handler-method đó đã được cấu hình một `Validator`.

Với các JMS message type cơ bản, default converter của Spring xử lý được nhiều trường hợp text, byte, map và serializable object. Với contract hướng JSON, nên cấu hình `MappingJackson2MessageConverter` rõ ràng để wire representation và chính sách xác định target type là chủ đích của hệ thống.

```java
@Bean
MappingJackson2MessageConverter orderConverter() {
    var converter = new MappingJackson2MessageConverter();
    converter.setTargetType(MessageType.TEXT);
    converter.setTypeIdPropertyName("_type");
    return converter;
}

@JmsListener(destination = "orders.in")
void handle(@Valid @Payload OrderCommand command) {
}
```

`@Valid` đánh dấu payload cần được kiểm tra, nhưng với Spring Framework thuần thì chỉ có annotation này chưa làm validation tự hoạt động. `DefaultMessageHandlerMethodFactory` mặc định đưa việc validation tới cơ chế không làm gì nếu chưa được cung cấp `Validator`. Khi listener cần validation thật sự, hãy cấu hình `DefaultMessageHandlerMethodFactory` với adapter validation Spring/Jakarta phù hợp rồi đăng ký factory đó qua `JmsListenerConfigurer` / `JmsListenerEndpointRegistrar#setMessageHandlerMethodFactory(...)`.

Validation xảy ra sau khi conversion thành công. JSON sai cấu trúc và domain object vi phạm constraint là hai loại lỗi khác nhau. Lỗi conversion nghĩa là Spring không tạo được payload theo kiểu yêu cầu; lỗi validation nghĩa là object đã được tạo nhưng không thỏa constraint đã cấu hình. Log và test nên giữ hai trường hợp này tách biệt.

Header không phải nơi thay thế payload schema. Header phù hợp với metadata như correlation, content type, routing hint và tracing context. Trạng thái nghiệp vụ nên ở payload trừ khi messaging contract bên ngoài quy định khác.

**Điểm cần tránh:** nếu class name Java được truyền như type-id header thì nó đã trở thành một phần của message contract. Hãy cấu hình type mapping và nguồn dữ liệu tin cậy có chủ đích, thay vì cho phép producer tùy ý chọn application class.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `JmsListener` và payload có validation
- Spring Framework 6.1 API — `DefaultMessageHandlerMethodFactory#setValidator`
- Spring Framework 6.1 API — tùy biến handler-method factory qua `EnableJms`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-listener-replies">Xử lý phản hồi với JMSReplyTo, @SendTo và JmsResponse</a>

<details>
<summary>Xem chi tiết</summary>

Giá trị trả về của listener có thể được convert rồi gửi thành JMS reply. Việc chọn destination của reply tuân theo request/reply contract, không chỉ là một lệnh send tùy ý sau khi listener kết thúc.

Thứ tự ưu tiên của reply destination là rõ ràng: destination nằm trong `JmsResponse` được ưu tiên trước; nếu không có thì Spring dùng `JMSReplyTo` của request đi vào; chỉ khi cả hai đều không có mới dùng response destination dự phòng đã cấu hình/mặc định như `@SendTo`. Tóm lại: `JmsResponse destination > JMSReplyTo > @SendTo/default`. `@SendTo` phù hợp khi reply destination dự phòng đã biết trước ở mức khai báo, còn `JmsResponse<T>` phù hợp khi logic nghiệp vụ phải quyết định destination lúc chạy.

```java
@JmsListener(destination = "quote.requests")
@SendTo("quote.replies")
Quote reply(QuoteRequest request) {
    return pricing.quote(request);
}
```

Khi bên gọi cung cấp `JMSReplyTo`, Spring có thể gửi giá trị trả về tới destination đó thay vì `@SendTo` tĩnh. Nhờ vậy cùng một listener có thể hỗ trợ temporary reply destination do request chỉ định và vẫn có destination dự phòng đã cấu hình.

Với destination động, trả về `JmsResponse`:

```java
@JmsListener(destination = "routing.requests")
JmsResponse<Result> route(RoutingRequest request) {
    Result result = service.process(request);
    return request.priority()
            ? JmsResponse.forQueue(result, "priority.results")
            : JmsResponse.forQueue(result, "standard.results");
}
```

`JmsResponse` mang cả response value lẫn destination thực tế. Nó có thể trỏ tới queue name, topic name hoặc `Destination` cụ thể. Nếu destination là cố định, ưu tiên `@SendTo` vì contract dễ đọc và dễ test hơn.

Việc gửi reply vẫn chịu các quy tắc JMS thông thường: conversion có thể fail, send có thể tham gia local hoặc external transaction, và publish observation phụ thuộc vào hạ tầng observation được cấu hình. Listener return thành công không tự chứng minh broker đã commit reply bền vững; transaction đang hoạt động và delivery contract của provider mới quyết định điều đó.

### Tài liệu tham khảo

- Spring Framework 6.1 API — `JmsResponse`
- Spring Framework 6.1 API — `@JmsListener`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jms-programmatic-endpoints">Đăng ký Endpoint bằng Code</a>

<details>
<summary>Xem chi tiết</summary>

Annotation thuận tiện khi endpoint metadata là cố định và nằm gần handler method. Programmatic registration hữu ích khi endpoint phải được tạo từ cấu hình, tenant, deployment topology hoặc một runtime model không thể biểu diễn gọn bằng annotation cố định.

Implement `JmsListenerConfigurer` để truy cập `JmsListenerEndpointRegistrar`, rồi đăng ký một `JmsListenerEndpoint` cùng factory cần dùng.

```java
@Configuration
@EnableJms
class DynamicJmsConfiguration implements JmsListenerConfigurer {

    private final JmsListenerContainerFactory<?> factory;

    DynamicJmsConfiguration(JmsListenerContainerFactory<?> factory) {
        this.factory = factory;
    }

    @Override
    public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
        var endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setId("audit-listener");
        endpoint.setDestination("audit.events");
        endpoint.setMessageListener(message -> handleAudit(message));
        registrar.registerEndpoint(endpoint, factory);
    }

    private void handleAudit(jakarta.jms.Message message) {
        // xử lý message
    }
}
```

`MethodJmsListenerEndpoint` là model dùng cho endpoint theo phương thức; `SimpleJmsListenerEndpoint` nhận trực tiếp một JMS `MessageListener`. Trong cả hai trường hợp, endpoint vẫn đi qua `JmsListenerContainerFactory` và `JmsListenerEndpointRegistry`, nên transaction, recovery, lifecycle và observation vẫn là quy tắc của container.

Với listener ứng dụng thông thường, nên ưu tiên annotation vì destination nằm ngay cạnh handler và có sẵn cơ chế resolve method argument của Spring. Chỉ chọn đăng ký bằng code khi endpoint động là một yêu cầu thực sự, không phải chỉ để tránh dùng annotation.

**Quy tắc thiết kế:** dynamic endpoint registration không phải broker administration. Tạo queue/topic, retention policy, permission, partition hoặc provider topology vẫn là hạ tầng riêng của provider, nằm ngoài trách nhiệm của Spring Framework JMS endpoint registration.

</details>

- [Quay lại đầu trang](#back-to-top)
