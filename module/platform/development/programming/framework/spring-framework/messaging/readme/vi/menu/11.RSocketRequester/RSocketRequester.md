<a id="back-to-top"></a>

# RSocketRequester và RSocketStrategies

## Menu
- [1. Vai trò của RSocketRequester](#rsocket-requester-role)
- [2. Client requester connection setup](#rsocket-client-requester)
- [3. Server-side requester](#rsocket-server-requester)
- [4. Route, data và response retrieval](#rsocket-route-data-retrieve)
- [5. Request metadata](#rsocket-request-metadata)
- [6. Requester lifecycle và disposal](#rsocket-requester-lifecycle)
- [7. RSocketStrategies](#rsocket-strategies)
- [8. Codec và route matching](#rsocket-codecs-route-matching)
- [9. Metadata extraction](#rsocket-metadata-extraction)

## <a id="rsocket-requester-role">1. Vai trò của RSocketRequester</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketRequester` là API requester cấp cao của Spring Messaging. Nó bao bọc sending side của RSocket để application code làm việc với Java object, route, metadata, `Mono` và `Flux` thay vì phải tự tạo `Payload` buffer và gọi trực tiếp low-level frame API.

Vai trò của nó có thể hình dung như sau:

```text
application object
    -> RSocketRequester
    -> Encoder / chuẩn bị metadata
    -> RSocket interaction
    -> Decoder
    -> application object
```

Requester có thể được dùng ở cả hai endpoint. Client tạo requester khi kết nối tới server; server có thể nhận requester đại diện cho peer đã kết nối trong annotated responder. Vì vậy API này mô hình hóa vai trò requester của một interaction, không phải vai trò “client” cố định.

Interaction type phần lớn được suy ra từ cardinality của input và output. Ví dụ một input + `retrieveMono` là request-response, một input + `retrieveFlux` là request-stream, multi-value input dẫn tới request-channel, còn `send()` là fire-and-forget.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-client-requester">2. Client requester connection setup</a>

<details>
<summary>Xem chi tiết</summary>

Builder phía client chuẩn bị `RSocketConnector` và frame `SETUP` của connection. Trong Spring Framework 6.1, hai cách cơ bản qua TCP và WebSocket là:

```java
RSocketRequester tcp = RSocketRequester.builder()
        .tcp("localhost", 7000);

RSocketRequester websocket = RSocketRequester.builder()
        .webSocket(URI.create("wss://example.org/rsocket"));
```

Các lời gọi builder trên không nhất thiết mở connection ngay. Khi application gửi request, requester thiết lập và dùng chung connection một cách trong suốt.

Có thể cấu hình data áp dụng cho setup trước khi tạo requester:

```java
RSocketRequester requester = RSocketRequester.builder()
        .dataMimeType(MediaType.APPLICATION_JSON)
        .setupRoute("client.{id}", clientId)
        .setupData(clientInfo)
        .setupMetadata(token, authenticationMimeType)
        .tcp(host, port);
```

`setupData`, `setupRoute` và `setupMetadata` thuộc initial connection setup, không được gửi lặp lại với mọi request. Các tùy chọn RSocket Java nâng cao như keepalive, resumption hay interceptor được cấu hình qua callback `rsocketConnector(...)`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-server-requester">3. Server-side requester</a>

<details>
<summary>Xem chi tiết</summary>

Vì RSocket connection đã thiết lập mang tính đối xứng, server có thể chủ động gửi request tới client đang kết nối. Spring cung cấp `RSocketRequester` đại diện cho peer dưới dạng method argument trong `@ConnectMapping` và `@MessageMapping`.

```java
@MessageMapping("device.register")
Mono<Void> register(Device device, RSocketRequester peer) {
    requestersByDevice.put(device.id(), peer);
    return Mono.empty();
}
```

Requester này gắn với connection của đúng peer đó. Nếu registry giữ requester để server gọi lại về sau, nó cũng phải xóa requester khi connection kết thúc và xử lý race với disconnect.

`@ConnectMapping` có thêm một ràng buộc lifecycle: setup phải hoàn tất trước khi normal request có thể chạy. Nếu server muốn gọi ngược client ngay lúc setup, request đó phải được tách khỏi completion path của setup thay vì đồng bộ chờ response.

Mô hình này hữu ích cho server-to-client query/command, nhưng client cũng phải cấu hình responder cho các route mà server được phép gọi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-route-data-retrieve">4. Route, data và response retrieval</a>

<details>
<summary>Xem chi tiết</summary>

Một request thường được tạo theo ba bước: chọn route hoặc metadata, cung cấp data nếu có, rồi khai báo cách nhận response.

```java
Flux<Airport> airports = requester
        .route("airports.near.{code}", "SGN")
        .data(searchRequest)
        .retrieveFlux(Airport.class);
```

`route(String, Object...)` mở rộng route variable rồi đặt routing information vào metadata. `data(...)` nhận concrete value hoặc producer có thể được thích nghi qua `ReactiveAdapterRegistry`. Với multi-value publisher, overload có thêm element `Class` hoặc `ParameterizedTypeReference` giúp tránh phải suy luận element type và tìm encoder cho từng item.

Lựa chọn nhận response quyết định output cardinality:

- `retrieveMono(T.class)` — 0 hoặc 1 giá trị đã decode;
- `retrieveFlux(T.class)` — 0 đến nhiều giá trị;
- `send()` — fire-and-forget, trả `Mono<Void>`;
- `sendMetadata()` — metadata push ở mức connection.

Cần khớp những lựa chọn này với responder contract. Trong fluent API của `RSocketRequester`, Spring ghi rõ many-input/one-output là tổ hợp suy luận không hợp lệ. Khi request data là một stream, nên mô hình hóa exchange dưới dạng request-channel và nhận response theo stream semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-request-metadata">5. Request metadata</a>

<details>
<summary>Xem chi tiết</summary>

Request metadata mang các thông tin tách khỏi application data payload như route, tracing context, authentication material, feature flag hoặc protocol concern có kiểu riêng. Với composite metadata mặc định, nhiều metadata entry có thể cùng tồn tại và mỗi entry giữ MIME type riêng.

```java
Mono<OrderView> result = requester
        .route("orders.find.{id}", orderId)
        .metadata(bearerToken, bearerMimeType)
        .metadata(traceContext, tracingMimeType)
        .retrieveMono(OrderView.class);
```

Mỗi metadata value cần encoder hỗ trợ cả Java type và MIME type tương ứng. Phía responder cũng phải có decoder/extractor registration phù hợp trước khi value đó có thể xuất hiện dưới dạng Spring message header.

Không nên xem metadata như một `Map` không kiểu mà hai phía tự nhiên cùng hiểu. Metadata MIME type của connection, quy ước composite metadata, encoder, decoder và `MetadataExtractor` cùng tạo thành contract. Với custom MIME type, cần document schema và bên nào sở hữu contract đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-requester-lifecycle">6. Requester lifecycle và disposal</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketRequester` triển khai Reactor `Disposable`. Gọi `dispose()` sẽ ủy quyền cho `RSocketClient` bên dưới để đóng transport connection và báo terminal signal cho các subscriber phụ thuộc vào connection đó. `isDisposed()` cho biết requester đã ở terminal state hay chưa.

Nên xem lifecycle của requester như lifecycle của connection:

- giữ requester dài hạn khi nhiều interaction logic cần dùng chung một connection;
- đóng requester khi component/application shutdown;
- không giữ server-side requester sau khi peer đã disconnect;
- chuẩn bị cho việc in-flight request fail khi connection kết thúc.

Trong Spring Framework 6.1, requester được xây trên `RSocketClient` có thể không có một “live” `io.rsocket.RSocket` để trả về qua `rsocket()`; method đó có thể trả `null`. Nên ưu tiên high-level requester API, hoặc dùng `rsocketClient()` nếu thật sự cần low-level client access.

Reconnect và resumption là policy riêng. Dispose là hành động đóng chủ động, không phải yêu cầu resume cùng session. Cấu hình retry/resumption ở RSocket client/connector theo failure model của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-strategies">7. RSocketStrategies</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketStrategies` gom các strategy chuyển đổi và routing mà requester/responder infrastructure của Spring cùng sử dụng. Nó bao gồm:

- encoder để biến application value thành data hoặc metadata bytes;
- decoder để biến payload bytes trở lại application value;
- một `RouteMatcher`;
- một `ReactiveAdapterRegistry`;
- một `DataBufferFactory`;
- một `MetadataExtractor`.

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .encoders(list -> list.add(new Jackson2JsonEncoder()))
        .decoders(list -> list.add(new Jackson2JsonDecoder()))
        .routeMatcher(new PathPatternRouteMatcher())
        .build();
```

Đối tượng này được thiết kế để tái sử dụng. Khi cùng một process có cả requester và responder, dùng chung strategy tương thích giúp tránh lỗi tinh vi như một phía encode được domain object nhưng responder đồng vị trí lại không decode được, hoặc hai phía dùng route separator/pattern rule khác nhau.

`RSocketStrategies` không phải nơi cấu hình protocol-level connector option. Keepalive, lease, resumption, transport và interceptor thuộc RSocket Java; object conversion, route matching và metadata extraction thuộc `RSocketStrategies`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-codecs-route-matching">8. Codec và route matching</a>

<details>
<summary>Xem chi tiết</summary>

`RSocketStrategies` mặc định hỗ trợ các kiểu nền tảng như `String` và các buffer dạng byte. Domain object cần encoder/decoder tương thích, thường lấy từ `spring-web` như Jackson JSON/CBOR hoặc Protobuf support.

Codec selection xét cả Java element type lẫn MIME type. Route có thể match hoàn toàn chính xác nhưng handler vẫn không được gọi nếu không có decoder đọc request payload; chiều trả về cũng có thể fail khi không tìm được encoder cho kiểu dữ liệu responder tạo ra.

Route matching là concern khác với payload codec. Mặc định Spring dùng `SimpleRouteMatcher` dựa trên `AntPathMatcher` với `.` làm separator. `PathPatternRouteMatcher` từ `spring-web` được khuyến nghị khi cần matching hiệu quả hơn:

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .routeMatcher(new PathPatternRouteMatcher())
        .build();
```

RSocket route là logical destination, không phải URL. Không có HTTP URL decoding. Các component cùng chia sẻ route contract nên dùng matcher strategy tương thích, nhất là khi service interface và annotated responder cùng đại diện cho một API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rsocket-metadata-extraction">9. Metadata extraction</a>

<details>
<summary>Xem chi tiết</summary>

`MetadataExtractor` biến serialized RSocket metadata thành các value có tên trong Spring messaging model. Sau đó annotated responder có thể đọc các value này qua `@Header` hoặc `@Headers`.

`DefaultMetadataExtractor` hiểu routing metadata của RSocket ngay từ đầu và đưa route vào key tương ứng. Các metadata MIME type khác phải được đăng ký cùng target Java type, header name và decoder đọc được kiểu đó.

```java
RSocketStrategies strategies = RSocketStrategies.builder()
        .metadataExtractorRegistry(registry ->
                registry.metadataToExtract(
                        tenantMimeType,
                        TenantContext.class,
                        "tenant"))
        .build();
```

Composite metadata phù hợp vì routing, security, tracing và application context có thể giữ kiểu riêng, độc lập với nhau. Nếu peer không dùng composite metadata, extraction có thể cần custom logic để decode một metadata document rồi điền nhiều output entry.

Extraction chỉ là một phần của responder boundary. Metadata decode thành công vẫn là external input và cần validation. Việc một header đã xuất hiện không đồng nghĩa value đó đáng tin cậy hoặc đã được authorize.

### Tài liệu tham khảo

- [Spring Framework — RSocketRequester và MetadataExtractor](https://docs.spring.io/spring-framework/reference/rsocket.html)
- [Spring Framework API — RSocketStrategies](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/messaging/rsocket/RSocketStrategies.html)

</details>

- [Quay lại đầu trang](#back-to-top)
