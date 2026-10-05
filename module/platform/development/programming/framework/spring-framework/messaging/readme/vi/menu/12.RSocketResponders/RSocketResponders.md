<a id="back-to-top"></a>

# RSocket Responder và Service Interface

## Menu
- [1. RSocketMessageHandler](#rsocket-message-handler)
- [2. @MessageMapping responder](#rsocket-message-mapping)
- [3. @ConnectMapping cho SETUP và metadata push](#rsocket-connect-mapping)
- [4. Interaction model từ input và output cardinality](#rsocket-interaction-cardinality)
- [5. RSocket service interface](#rsocket-service-interface)
- [6. @RSocketExchange](#rsocket-exchange)
- [7. RSocketServiceProxyFactory](#rsocket-service-proxy)
- [8. Service method parameter và return value](#rsocket-service-method-contract)
- [9. Annotated responder ở cả client và server](#rsocket-responder-symmetry)
- [10. Ranh giới Boot, Security và Spring Integration](#rsocket-neighbor-boundaries)

## <a id="rsocket-message-handler">1. RSocketMessageHandler</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketMessageHandler` là cầu nối của Spring Messaging từ RSocket frame sang annotated Java handler method. Nó dùng reactive message-handling infrastructure để lấy route và metadata, decode request data, chọn handler, gọi method rồi encode giá trị trả về thành RSocket response stream.

Ở phía server, nó thường được khai báo thành Spring bean để phát hiện controller method, sau đó `responder()` được đưa vào RSocket Java dưới dạng `SocketAcceptor`:

```java
@Bean
RSocketMessageHandler rsocketMessageHandler(RSocketStrategies strategies) {
    RSocketMessageHandler handler = new RSocketMessageHandler();
    handler.setRSocketStrategies(strategies);
    return handler;
}
```

Class này cũng hỗ trợ responder phía client. Static factory `RSocketMessageHandler.responder(strategies, handlers...)` tạo responder từ các handler object cụ thể mà không bắt buộc `@Controller`, hữu ích khi cùng một application vừa có server controller vừa có client responder.

`RSocketMessageHandler` là framework infrastructure. Codec, route matcher, metadata extraction và reactive adaptation nằm trong strategy; business logic nên nằm ở các mapped method được framework gọi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-message-mapping">2. @MessageMapping responder</a>

<details>
<summary>Xem chi tiết</summary>

`@MessageMapping` ánh xạ một RSocket request riêng lẻ vào responder method. Route có thể là pattern và có thể được khai báo ở type level lẫn method level. Handler có thể nhận request payload, destination variable, metadata đã extract thành header và `RSocketRequester` để gọi lại peer.

```java
@Controller
class RadarController {

    @MessageMapping("radar.find.{id}")
    Mono<Radar> find(
            @DestinationVariable String id,
            @Header("tenant") String tenant) {
        return radarService.find(tenant, id);
    }
}
```

`@Payload` có thể bỏ qua nếu Spring xác định được một non-simple argument là payload. Reactive payload như `Mono<T>` hoặc `Flux<T>` cũng được hỗ trợ.

Interaction type được quyết định từ cardinality của input và output. Vì vậy một route không tự thân là “request-response” hay “stream”; Java signature là một phần của protocol contract. Nếu đổi `Mono<T>` thành `Flux<T>`, chẳng hạn, wire interaction cũng thay đổi. Cần test cả route resolution và cardinality.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-connect-mapping">3. @ConnectMapping cho SETUP và metadata push</a>

<details>
<summary>Xem chi tiết</summary>

`@ConnectMapping` xử lý frame ở mức connection thay vì ordinary request stream. Nó được gọi cho RSocket `SETUP` ban đầu và các frame `METADATA_PUSH` về sau. Có thể khai báo mapping pattern để chỉ xử lý setup/push metadata có route phù hợp; nếu không khai báo pattern thì method có thể match mọi connection event loại này.

Method dùng được các nhóm argument tương tự `@MessageMapping`, nhưng value đến từ setup hoặc metadata-push content. Method không được trả application data; dùng `void` hoặc `Mono<Void>`.

```java
@ConnectMapping("client.{tenant}")
Mono<Void> connect(
        @DestinationVariable String tenant,
        RSocketRequester peer) {
    return registry.register(tenant, peer);
}
```

Ở server, nếu asynchronous handling của initial setup kết thúc bằng error thì connection mới bị từ chối. Không nên chờ trong setup handler cho một request gọi ngược cùng peer requester, vì normal request cần setup hoàn tất trước. Outbound call như vậy phải được tách khỏi completion path của setup.

Ở client responder, `@ConnectMapping` là callback và không quyết định việc server có chấp nhận underlying connection hay không. Cần nhớ khác biệt lifecycle này khi chia sẻ responder code giữa hai endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-interaction-cardinality">4. Interaction model từ input và output cardinality</a>

<details>
<summary>Xem chi tiết</summary>

Spring suy ra interaction model từ số lượng request value và response value mà handler signature biểu diễn:

| Input cardinality | Output cardinality | Interaction |
| --- | --- | --- |
| 0 hoặc 1 | 0 | Fire-and-forget hoặc request-response không có response value |
| 0 hoặc 1 | 1 | Request-response |
| 0 hoặc 1 | many | Request-stream |
| many | 0, 1 hoặc many | Request-channel |

`1` là concrete value hoặc single-value reactive type như `Mono<T>`. `many` là multi-value type như `Flux<T>`. `0` nghĩa là không có payload ở input hoặc không có value ở output (`void` hay `Mono<Void>`).

Trường hợp output bằng 0 với input 0/1 cần chú ý: cả fire-and-forget và request-response không có response value đều có thể tới cùng handler; operation phía requester quyết định có response stream hay không.

Mô hình cardinality giúp tránh việc method name che giấu protocol behavior. Mỗi khi API đổi từ một value sang stream hoặc ngược lại, cần review signature và đồng bộ requester/responder. Cardinality mismatch là lỗi protocol contract dù cả hai codebase Java vẫn compile riêng rẽ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-service-interface">5. RSocket service interface</a>

<details>
<summary>Xem chi tiết</summary>

RSocket service interface cho phép requester và responder chia sẻ một Java contract. Method được annotate bằng `@RSocketExchange`; interface có thể đưa vào `RSocketServiceProxyFactory` để tạo requester proxy, còn responder có thể implement chính interface đó.

```java
@RSocketExchange("inventory")
interface InventoryRSocketService {

    @RSocketExchange("find.{sku}")
    Mono<ItemView> find(@DestinationVariable String sku);

    @RSocketExchange("watch")
    Flux<ItemEvent> watch(@Payload WatchRequest request);
}
```

Cách này giảm duplication của route string cùng Java payload/response type. Tuy nhiên network boundary vẫn tồn tại: hai peer vẫn phải thống nhất serialization, metadata convention, interaction cardinality và error semantics.

Nên dùng service interface khi hai phía chủ động chia sẻ một RSocket contract ổn định. Nếu responder cần route pattern linh hoạt, parameter đặc thù phía server hoặc coupling lỏng hơn, `@MessageMapping` độc lập thường phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-exchange">6. @RSocketExchange</a>

<details>
<summary>Xem chi tiết</summary>

`@RSocketExchange` khai báo endpoint route cho một RSocket service interface. Annotation có thể đặt ở type level để tạo common route prefix và ở method level cho route của operation cụ thể.

Nó cố ý bị ràng buộc chặt hơn `@MessageMapping` vì cùng một declaration phải dùng được cho requester proxy và responder. Exchange method có một concrete route declaration; `@MessageMapping` có thể map nhiều route và dùng responder-oriented route pattern.

Các argument được hỗ trợ gồm:

- `@DestinationVariable` để mở rộng placeholder trong route;
- `@Payload` cho request data;
- một metadata value ngay trước argument `MimeType` tương ứng.

```java
@RSocketExchange("orders")
interface OrderService {

    @RSocketExchange("submit.{region}")
    Mono<OrderResult> submit(
            @DestinationVariable String region,
            @Payload OrderCommand command,
            String bearerToken,
            MimeType bearerMimeType);
}
```

Vì metadata argument dùng cặp value/MIME type theo vị trí, interface nên giữ signature dễ đọc và document custom metadata. Nếu contract cần arbitrary responder header hoặc nhiều route pattern thay thế nhau, `@MessageMapping` có thể diễn đạt server-side model tốt hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-service-proxy">7. RSocketServiceProxyFactory</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketServiceProxyFactory` biến annotated service interface thành client-side proxy dùng một `RSocketRequester` bên dưới.

```java
RSocketRequester requester = ...;

RSocketServiceProxyFactory factory =
        RSocketServiceProxyFactory.builder(requester).build();

InventoryRSocketService inventory =
        factory.createClient(InventoryRSocketService.class);
```

Khi gọi proxy method, factory lấy route và argument từ `@RSocketExchange`, tạo request value, thực hiện RSocket interaction rồi thích nghi response về Java return type đã khai báo. Proxy không tự host responder; một implementation của interface được đăng ký với `RSocketMessageHandler` mới xử lý phía nhận.

Với synchronous service method, proxy phải block để chờ response. `RSocketServiceProxyFactory.Builder.blockTimeout(...)` có thể đặt giới hạn cho thời gian block; tuy vậy Spring khuyến nghị cấu hình timeout ở mức RSocket transport/protocol khi cần kiểm soát connection behavior chính xác hơn. Reactive signature tránh đưa blocking boundary vào service contract.

Factory chỉ là adapter từ Java contract sang requester đã tồn tại. Connection lifecycle, retry/resumption, codec và metadata strategy vẫn thuộc requester/RSocket configuration bên dưới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-service-method-contract">8. Service method parameter và return value</a>

<details>
<summary>Xem chi tiết</summary>

Signature của service interface mã hóa cùng các cardinality rule như fluent requester API. Request payload có thể là concrete value hoặc producer thích nghi được sang Reactive Streams `Publisher`; return value cũng có thể là concrete value hoặc reactive producer.

Ở input, `@Payload` đánh dấu data argument. Payload mặc định là bắt buộc, trừ khi annotation hoặc Java type cho phép optional. `@DestinationVariable` cung cấp route template value. Metadata bổ sung được biểu diễn bằng một object ngay trước `MimeType` tương ứng.

Ở output:

- `Mono<T>` hoặc single-value reactive type biểu diễn 0/1 response;
- `Flux<T>` hoặc multi-value type biểu diễn response stream;
- `Mono<Void>` biểu diễn completion không có response value;
- synchronous value khiến phía proxy block cho đến khi có kết quả.

Nên ưu tiên reactive signature cho streaming và code cần giữ non-blocking composition. Nếu cố ý chọn synchronous method, phải định nghĩa timeout và không gọi nó trên non-blocking event-loop thread.

Đổi parameter từ một value sang multi-value publisher hoặc đổi return type từ one sang many làm thay đổi RSocket interaction contract. Đây là wire-level API change, không chỉ là local refactoring.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-responder-symmetry">9. Annotated responder ở cả client và server</a>

<details>
<summary>Xem chi tiết</summary>

Annotated responder của RSocket mang tính đối xứng. Cùng một `RSocketMessageHandler` infrastructure có thể dispatch request mà server nhận hoặc request mà client nhận. Điểm khác nhau nằm ở cách handler được phát hiện và gắn vào RSocket connection.

Ở server, Spring-managed `RSocketMessageHandler` thường phát hiện `@Controller` bean và `responder()` của nó được đăng ký với `RSocketServer`. Ở client, handler có thể được truyền trực tiếp:

```java
SocketAcceptor clientResponder =
        RSocketMessageHandler.responder(strategies, new ClientCallbacks());

RSocketRequester requester = RSocketRequester.builder()
        .rsocketConnector(connector -> connector.acceptor(clientResponder))
        .tcp(host, port);
```

Nếu cùng application host cả hai loại responder qua Spring configuration, custom handler predicate có thể phân biệt component thuộc vai trò nào và tránh đăng ký nhầm hoặc trùng.

Tính đối xứng rất mạnh nhưng phải được thiết kế chủ động. Server-initiated route cũng là một phần của client-facing API và cần authorization, timeout, lifecycle cùng compatibility rule giống như route do client khởi tạo.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-neighbor-boundaries">10. Ranh giới Boot, Security và Spring Integration</a>

<details>
<summary>Xem chi tiết</summary>

Phạm vi của chapter này là mô hình lập trình RSocket trong Spring Framework Messaging: `RSocketRequester`, `RSocketStrategies`, `RSocketMessageHandler` và annotation/service interface liên quan. Các Spring project lân cận xây trên mô hình này nhưng giải quyết concern khác.

Spring Boot có thể dựng và cấu hình RSocket infrastructure từ application configuration, đồng thời cung cấp auto-configuration. Property convention và lifecycle của Boot thuộc curriculum Spring Boot; Framework contract ở đây vẫn dùng được khi không có Boot.

Spring Security bổ sung authentication và authorization cho RSocket. Metadata extraction có thể đưa credential/claim vào Spring message model, nhưng decode metadata thành công không đồng nghĩa peer đã được authenticate hay route đã được authorize.

Spring Integration cung cấp RSocket inbound/outbound gateway và nối RSocket traffic vào integration flow. Nó phù hợp khi bài toán chính là enterprise integration, adapter, routing hoặc chuỗi message flow thay vì controller/service-style responder.

Nên giữ ranh giới rõ:

```text
Spring Messaging RSocket  -> protocol mapping và handler/requester model
Spring Boot               -> application setup và auto-configuration
Spring Security           -> authentication và authorization
Spring Integration        -> integration flow và gateway
```

### Tài liệu tham khảo

- [Spring Framework — Annotated RSocket responder và service interface](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [Spring Framework API — RSocketMessageHandler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/messaging/rsocket/annotation/support/RSocketMessageHandler.html)

</details>

- [Quay lại đầu trang](#back-to-top)
