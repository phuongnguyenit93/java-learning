<a id="back-to-top"></a>

# WebClient và outbound reactive HTTP

## Menu
- [Vì sao WebClient tồn tại](#webclient-purpose)
- [Builder model và immutable client instance](#webclient-builder-immutability)
- [HTTP client connector](#webclient-client-connectors)
- [Tạo request và chèn body](#webclient-request-and-body)
- [Lấy response](#webclient-response-retrieval)
- [Exchange control ở mức thấp hơn](#webclient-exchange-control)
- [Xử lý lỗi phía client](#webclient-error-handling)
- [Streaming response body](#webclient-streaming-responses)
- [Filter, attribute và Reactor Context](#webclient-filters-attributes-context)
- [Codec infrastructure dùng chung với WebFlux](#webclient-codec-infrastructure)
- [Connection và event-loop resource do connector sở hữu](#webclient-connector-resources)
- [Shared-resource model của Reactor Netty](#webclient-reactor-netty-shared-resources)
- [Dùng WebClient trong ứng dụng Spring MVC](#webclient-in-mvc)

## <a id="webclient-purpose">Vì sao WebClient tồn tại</a>

<details>
<summary>Xem chi tiết</summary>

WebClient là HTTP client reactive cấp cao của Spring Framework. Nó tồn tại để outbound HTTP có thể tham gia vào cùng kiểu composition non-blocking với WebFlux server flow, thay vì chèn một lời gọi blocking vào giữa pipeline.

WebClient cung cấp fluent API để mô tả request, giao network I/O cho ClientHttpConnector, rồi dùng codec infrastructure để chuyển byte thành object và ngược lại. Việc build fluent chain chưa thực hiện xong HTTP exchange; kết quả vẫn là publisher được thực thi theo reactive subscription.

~~~java
Mono<Account> account = webClient.get()
        .uri("/accounts/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(Account.class);
~~~

WebClient hỗ trợ cả request/response thông thường lẫn streaming, nhưng không làm biến mất các vấn đề HTTP như timeout, connection pool, TLS, DNS, status handling, body limit, cancellation hay connector lifecycle. Reactive API thay đổi cách các vấn đề này được compose, không xóa chúng.

Module này sở hữu các cơ chế chuyên sâu của WebClient ở cấp Spring Framework. Câu hỏi kiến trúc rộng hơn về chọn HTTP client nào cho application-to-application integration thuộc area HTTP integration của repository.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-builder-immutability">Builder model và immutable client instance</a>

<details>
<summary>Xem chi tiết</summary>

WebClient.Builder là giai đoạn cấu hình mutable; WebClient sinh ra từ build() được thiết kế để tái sử dụng. Các cấu hình dùng chung ổn định như base URL, default header, filter, connector, codec và URI handling nên được thiết lập một lần rồi dùng client đó cho nhiều request độc lập.

~~~java
WebClient client = WebClient.builder()
        .baseUrl("https://inventory.internal")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .filter(correlationFilter())
        .build();
~~~

WebClient có mutate(), trả về builder đã copy cấu hình từ client hiện tại:

~~~java
WebClient adminClient = client.mutate()
        .defaultHeader("X-Client-Role", "admin")
        .build();
~~~

Cách này phù hợp khi cần một biến thể client có policy ổn định khác. State riêng của một HTTP request nên đặt trên request specification; policy dùng chung nên đặt ở client.

Không nên dùng một shared builder như global mutable state rồi sửa tùy ý trong runtime. Client instance với filter/header/codec/connector ổn định dễ hiểu và dễ vận hành hơn nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-client-connectors">HTTP client connector</a>

<details>
<summary>Xem chi tiết</summary>

WebClient không tự mở socket. ClientHttpConnector là boundary giữa API WebClient và HTTP client implementation bên dưới.

Spring Framework hỗ trợ connector cho các implementation như Reactor Netty, JDK HttpClient, Jetty reactive client và Apache HttpComponents. WebClient.create() dùng Reactor Netty theo setup mặc định khi dependency phù hợp có mặt; WebClient.builder().clientConnector(...) cho phép ứng dụng chọn và cấu hình transport tường minh.

~~~java
HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(3));

WebClient client = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
~~~

Connector choice ảnh hưởng connection pooling, event-loop/worker resources, TLS, proxy, timeout và lifecycle. Các detail transport nên nằm ở connector boundary thay vì rải native-client configuration khắp application code.

Không nên suy ra “WebClient luôn hoạt động như thế này” từ hành vi của một connector cụ thể. WebClient contract thuộc Framework; hành vi transport cụ thể thuộc HTTP client được chọn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-request-and-body">Tạo request và chèn body</a>

<details>
<summary>Xem chi tiết</summary>

Quá trình tạo request đi từ method và URI sang header, cookie, attribute và body nếu có. Dùng bodyValue cho một giá trị đã có; dùng body(Publisher, Class) hoặc BodyInserter khi body được sản xuất theo reactive flow.

~~~java
Mono<OrderResult> result = client.post()
        .uri("/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new CreateOrder(customerId, items))
        .retrieve()
        .bodyToMono(OrderResult.class);
~~~

Với stream:

~~~java
Flux<Ack> acknowledgements = client.post()
        .uri("/events")
        .contentType(MediaType.APPLICATION_NDJSON)
        .body(events, Event.class)
        .retrieve()
        .bodyToFlux(Ack.class);
~~~

Content-Type khai báo cách HttpMessageWriter biểu diễn body. Việc truyền Publisher không tự bảo đảm wire-level streaming; media type, codec, connector và remote protocol đều phải hỗ trợ ghi dần.

Tránh làm blocking work ngay khi chuẩn bị request. Nếu code đọc file lớn đồng bộ hoặc gọi SDK blocking trước bodyValue(), pipeline đã block trước khi WebClient có cơ hội làm gì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-response-retrieval">Lấy response</a>

<details>
<summary>Xem chi tiết</summary>

retrieve() là đường tiện dụng để xử lý response. Nó trả WebClient.ResponseSpec, từ đó ứng dụng có thể decode body hoặc lấy ResponseEntity.

~~~java
Mono<ResponseEntity<Account>> response = client.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .toEntity(Account.class);
~~~

Mặc định response 4xx và 5xx được chuyển thành WebClientResponseException. onStatus(...) cho phép map một nhóm status thành exception hoặc policy khác trước khi body được decode.

~~~java
Mono<Account> account = client.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .onStatus(
                status -> status.value() == 404,
                response -> Mono.error(new AccountMissing(id)))
        .bodyToMono(Account.class);
~~~

Dùng retrieve() khi chính sách status khá trực tiếp và mục tiêu chính là đọc body. Với các phương thức decode body thông thường và biến thể ResponseEntity hữu hạn, WebClient giữ luồng quản lý body khá gọn. Tuy nhiên nếu dùng toEntityFlux(...), ResponseEntity<Flux<T>> trả về chuyển trách nhiệm subscribe và consume body Flux sang bên gọi; nếu body đó không được subscribe thì tài nguyên gắn với response có thể không được release.

HTTP error status vẫn là một HTTP response hợp lệ. Nó khác transport failure như connection refused, TLS failure hay timeout, nơi không có response HTTP hoàn chỉnh để xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-exchange-control">Exchange control ở mức thấp hơn</a>

<details>
<summary>Xem chi tiết</summary>

exchangeToMono(...) và exchangeToFlux(...) là API mức thấp hơn khi logic decode phụ thuộc trực tiếp vào status, header hoặc ClientResponse.

~~~java
Mono<Account> account = client.get()
        .uri("/accounts/{id}", id)
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(Account.class);
            }
            if (response.statusCode().value() == 404) {
                return Mono.empty();
            }
            return response.createError();
        });
~~~

Spring 6.1.14 có lifecycle rule quan trọng: sau khi publisher do response handler trả về hoàn thành, phần response body chưa được consume sẽ được release. Nếu code cần body, response handler phải khai báo cách đọc nó trước khi handler completion kết thúc scope đó.

Phương thức exchange() cũ đã deprecated vì việc trao ClientResponse thô cho downstream code khiến người dùng dễ quên consume/release body, dẫn tới memory hoặc connection leak. Hãy ưu tiên retrieve() cho common case và exchangeToMono/exchangeToFlux khi thật sự cần branch dựa trên full response.

“Mức thấp hơn” ở đây nghĩa là application chịu nhiều trách nhiệm hơn cho quyết định status/body, không có nghĩa là bypass codec hay connector.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — WebClient.RequestHeadersSpec
- Spring Framework Reference — WebClient Exchange

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-error-handling">Xử lý lỗi phía client</a>

<details>
<summary>Xem chi tiết</summary>

Lỗi phía WebClient nên được tách ít nhất thành ba nhóm:

1. **Chính sách HTTP status** — server đã trả response như 404 hoặc 503.
2. **Lỗi xử lý body** — codec không decode được, vượt memory limit hoặc body không hợp lệ.
3. **Lỗi transport** — DNS, connection, TLS, timeout hay connector I/O thất bại trước khi có response dùng được.

retrieve().onStatus(...) xử lý nhóm thứ nhất. Reactive error operator có thể map/recover error signal sau đó, nhưng một onErrorResume quá rộng dễ gom những lỗi hoàn toàn khác nhau thành cùng fallback.

~~~java
return client.get()
        .uri("/catalog/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is5xxServerError,
                response -> response.createException())
        .bodyToMono(CatalogItem.class)
        .timeout(Duration.ofSeconds(2));
~~~

Retry phải xét HTTP semantics và idempotency. Retry mọi lỗi có thể lặp thao tác ghi, khuếch đại outage hoặc giữ connection lâu hơn khi hệ thống đang chịu áp lực. Lý thuyết retry/backoff tổng quát thuộc reactive/resilience layer; WebClient cần giữ tín hiệu lỗi đủ chính xác để chính sách bên trên ra quyết định đúng.

Nếu filter hoặc exchange callback tự consume ClientResponse, nó cũng phải giữ đúng body lifecycle. Response không được consume/release có thể ngăn connection reuse và tạo leak.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-streaming-responses">Streaming response body</a>

<details>
<summary>Xem chi tiết</summary>

WebClient có thể trả response body dưới dạng Flux<T> để codec decode từng phần khi wire format hỗ trợ streaming. Hai ví dụ phổ biến là Server-Sent Events và newline-delimited JSON.

~~~java
Flux<PriceTick> ticks = client.get()
        .uri("/prices/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(PriceTick.class);
~~~

Lợi ích chính là application không cần giữ toàn bộ response trong memory rồi mới bắt đầu xử lý. Demand và cancellation có thể đi qua reactive client boundary, còn connector quản lý network read thực tế.

Streaming đồng thời kéo dài resource lifetime. Connection của một stream có thể sống nhiều phút hoặc nhiều giờ. Cancellation, remote disconnect, idle timeout và slow consumer vì vậy là lifecycle event bình thường chứ không phải case hiếm.

Không nên biến streaming endpoint thành unbounded aggregation bằng collectList() nếu dữ liệu không có bound rõ ràng. Làm vậy sẽ đánh mất lợi ích memory và latency vốn là lý do chọn streaming.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-filters-attributes-context">Filter, attribute và Reactor Context</a>

<details>
<summary>Xem chi tiết</summary>

ExchangeFilterFunction là interception mechanism của WebClient. Filter nhận ClientRequest cùng ExchangeFunction kế tiếp, nhờ đó có thể thêm header, quan sát response, ghi observation hoặc áp dụng client-wide policy.

~~~java
ExchangeFilterFunction addRequestId = (request, next) ->
        Mono.deferContextual(contextView -> {
            String requestId = contextView.getOrDefault("requestId", "unknown");
            ClientRequest filtered = ClientRequest.from(request)
                    .header("X-Request-Id", requestId)
                    .build();
            return next.exchange(filtered);
        });
~~~

Request attribute là metadata cục bộ cho request WebClient hiện tại và hữu ích để filter trao đổi thông tin. Nó không tự propagate sang nested request hoặc request được tạo sau đó.

Reactor Context phù hợp hơn cho contextual data cần đi xuyên reactive composition và xuất hiện ở WebClient call lồng bên trong. Hãy gắn context bằng Reactor contextWrite trên composed chain. RequestHeadersSpec.context(...) đã deprecated trong Spring 6.1.14 vì nó không thể cung cấp context cho downstream nested/subsequent request.

Filter trực tiếp đọc hoặc thay ClientResponse phải tôn trọng body ownership: hoặc propagate response cho stage sau consume, hoặc tự consume/release trước khi thay bằng request/response khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-codec-infrastructure">Codec infrastructure dùng chung với WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

WebClient và WebFlux server cùng dựa trên HTTP codec model gồm HttpMessageReader và HttpMessageWriter để chuyển giữa byte buffer và application value. Phía client, strategy được cấu hình qua ExchangeStrategies và WebClient.Builder.codecs(...).

~~~java
WebClient client = WebClient.builder()
        .codecs(configurer ->
                configurer.defaultCodecs().maxInMemorySize(512 * 1024))
        .build();
~~~

Model dùng chung giúp JSON, form, multipart và các kiểu message conversion có mental model nhất quán giữa inbound và outbound HTTP. Điều đó không có nghĩa server và client dùng cùng một configuration object; mỗi phía có strategy set của riêng mình.

Memory limit đặc biệt quan trọng với codec phải aggregate trước khi decode. Pipeline non-blocking vẫn có thể hết memory nếu đồng thời buffer nhiều body lớn. Với format thực sự streaming, nên ưu tiên decode/xử lý dần thay vì cứ tăng maxInMemorySize.

Custom codec cũng phải giữ DataBuffer ownership rule giống infrastructure có sẵn. Giữ pooled buffer quá lâu hoặc bỏ quên buffer bị discard có thể tạo leak dù object ở application level trông hoàn toàn bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-connector-resources">Connection và event-loop resource do connector sở hữu</a>

<details>
<summary>Xem chi tiết</summary>

Connection pool và event-loop thread thuộc ClientHttpConnector cùng HTTP client implementation bên dưới, không thuộc WebClient facade. Việc tạo nhiều WebClient không tự động tạo mô hình tài nguyên tốt nếu mỗi instance lại dùng một bộ tài nguyên transport được quản lý riêng thiếu kiểm soát.

Với Reactor Netty, Spring Framework 6.1 có org.springframework.http.client.ReactorResourceFactory để quản lý lifecycle của LoopResources và ConnectionProvider trong ApplicationContext. Ứng dụng có thể dùng tài nguyên global của Reactor Netty hoặc tự quản lý một bộ tài nguyên độc lập.

~~~java
@Bean
ReactorResourceFactory reactorResources() {
    ReactorResourceFactory factory = new ReactorResourceFactory();
    factory.setUseGlobalResources(false);
    return factory;
}

@Bean
WebClient webClient(ReactorResourceFactory resources) {
    ReactorClientHttpConnector connector =
            new ReactorClientHttpConnector(resources, client -> client);

    return WebClient.builder()
            .clientConnector(connector)
            .build();
}
~~~

Chỉ khai báo factory bean không làm một WebClient bất kỳ tự discover và sử dụng nó. Với plain Framework, cần wire factory vào ReactorClientHttpConnector (hoặc cấu hình trực tiếp Reactor Netty client/server tương ứng) để tài nguyên thực sự được dùng. Nếu tài nguyên được quản lý bên ngoài, quyền sở hữu phải rõ: component tạo pool/event loop cũng phải có shutdown lifecycle tương ứng. Tái sử dụng WebClient nhưng vô tình tạo lại connector hoặc pool liên tục sẽ làm mất connection reuse và có thể gây tăng thread/tài nguyên.

Connector khác có mô hình tài nguyên khác. Hãy tách chính sách ở cấp WebClient khỏi connector-specific tuning để khi đổi transport, phần thay đổi được khoanh đúng ranh giới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-reactor-netty-shared-resources">Shared-resource model của Reactor Netty</a>

<details>
<summary>Xem chi tiết</summary>

Mặc định Reactor Netty dùng shared global HttpResources cho event-loop thread và connection pool. Fixed shared resource phù hợp với mô hình non-blocking vì mục tiêu là multiplex nhiều connection trên một nhóm thread ổn định thay vì tạo thread theo request.

Khi cả WebFlux server và WebClient trong cùng process đều dùng Reactor Netty, chúng mặc định chia sẻ event-loop resources. Cách này giảm thread count nhưng đồng thời làm blocking call nguy hiểm hơn: một event-loop thread bị giữ có thể đang phục vụ nhiều connection không liên quan.

Với application sống suốt JVM process, global resource có thể sống đến khi process kết thúc. Với ApplicationContext được start/stop bên trong một JVM dài hạn, Spring-managed ReactorResourceFactory có thể gắn initialization/shutdown của resource vào context lifecycle.

Nếu setUseGlobalResources(false), ứng dụng nhận trách nhiệm wire custom LoopResources và ConnectionProvider nhất quán vào các Reactor Netty client/server cần chia sẻ chúng. Custom một nửa có thể vô tình tạo nhiều pool hoặc event-loop group với lifecycle khó đoán.

Resource sharing là vấn đề ở tầng transport. Nó phải được tính vào capacity planning và diagnostics, nhưng application code vẫn nên phụ thuộc WebClient thay vì tự điều phối Netty event loop.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — org.springframework.http.client.ReactorResourceFactory
- Spring Framework Reference — WebFlux concurrency model và WebClient Reactor Netty resources

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webclient-in-mvc">Dùng WebClient trong ứng dụng Spring MVC</a>

<details>
<summary>Xem chi tiết</summary>

WebClient không bị giới hạn ở ứng dụng WebFlux server. Ứng dụng Spring MVC có thể dùng nó khi cần outbound client hỗ trợ reactive composition, streaming hoặc non-blocking I/O dù inbound request chạy trên Servlet stack.

Câu hỏi kiến trúc nằm ở cách MVC code xử lý publisher. Nếu gọi block() ngay, phía gọi đã chọn chờ đồng bộ tại boundary đó và phải định cỡ số lượng Servlet thread cùng timeout phù hợp. Nếu MVC tích hợp reactive return type theo asynchronous flow, inbound request vẫn thuộc MVC execution model còn WebClient thực hiện outbound I/O qua connector riêng.

~~~java
Account account = webClient.get()
        .uri("/accounts/{id}", id)
        .retrieve()
        .bodyToMono(Account.class)
        .block(timeout);
~~~

Ví dụ trên có thể là lựa chọn có chủ đích trong một blocking MVC service; không nên sao chép mẫu này vào WebFlux request event loop. Việc một ứng dụng “dùng WebClient” không đủ để kết luận toàn bộ application là reactive.

Ngay cả khi caller blocking để đợi kết quả, connector resource của WebClient vẫn cần lifecycle management đúng vì transport phía dưới tiếp tục dùng connection/execution resources riêng.

</details>

- [Quay lại đầu trang](#back-to-top)
