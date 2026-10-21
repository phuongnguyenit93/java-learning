<a id="back-to-top"></a>

# HTTP client đồng bộ

## Menu
- [Chọn RestClient, RestTemplate, WebClient hoặc HTTP Service Client](#synchronous-http-clients-001)
- [Tạo RestClient và cấu hình base URL, header, cookie mặc định](#synchronous-http-clients-002)
- [Gửi yêu cầu với URI template, query, form và multipart](#synchronous-http-clients-003)
- [retrieve, exchange và đọc status, header, body](#synchronous-http-clients-004)
- [HttpMessageConverter, kiểu generic và ParameterizedTypeReference](#synchronous-http-clients-005)
- [ClientHttpRequestFactory và lựa chọn thư viện HTTP nền](#synchronous-http-clients-006)
- [Timeout, connection pool, TLS và vòng đời tài nguyên](#synchronous-http-clients-007)
- [Interceptor, xử lý mã lỗi và thiết kế retry theo tính lặp lại của thao tác](#synchronous-http-clients-008)
- [RestTemplate trong hệ thống sẵn có và chuyển sang RestClient; deprecated từ Spring 7](#synchronous-http-clients-009)

## <a id="synchronous-http-clients-001">Chọn RestClient, RestTemplate, WebClient hoặc HTTP Service Client</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn RestClient, RestTemplate, WebClient hoặc HTTP Service Client trong Spring Framework

> **Ngữ cảnh:** HTTP client đồng bộ, nhưng cần hiểu cả phương án reactive và khai báo interface để chọn đúng kiến trúc. Bản hướng dẫn dùng **Spring Framework 6.2.x, Spring Boot 3.5.x và Java 17+** cho code chạy được. Spring Framework 7.x đã đánh dấu `RestTemplate` deprecated để hướng người dùng sang `RestClient`; điều này không có nghĩa `RestTemplate` ngừng chạy ngay, và **không** nên áp nguyên API Spring 7 lên project Spring 6.2. Mọi cấu hình HTTP timeout, pool, auth, error handling và tracing vẫn phải được kiểm tra theo phiên bản framework và thư viện transport thực tế.

## 1. Kết luận lựa chọn trong 30 giây

| Nhu cầu thực tế | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Mã Java/Spring MVC đồng bộ mới, gọi API JSON thông thường | **`RestClient`** | Fluent synchronous API, gọn, dễ đọc, dùng `HttpMessageConverter` |
| Codebase đã dùng nhiều `RestTemplate` ổn định | Giữ `RestTemplate` tạm thời, lên kế hoạch chuyển sang `RestClient` | Không phải thay toàn hệ thống chỉ vì có API mới |
| Gọi HTTP non-blocking, phối hợp nhiều remote calls, stream, WebFlux/Reactor | **`WebClient`** | Trả `Mono`/`Flux`, hỗ trợ reactive composition, backpressure/streaming |
| Nhiều endpoint của một remote service, muốn interface dễ đọc/dễ mock | **HTTP Service Client** trên `RestClient` hoặc `WebClient` | Proxy tạo từ interface `@HttpExchange`/`@GetExchange`, **không phải transport thứ tư** |
| Muốn interface khai báo nhưng vẫn giữ synchronous call site | HTTP Service Client + `RestClientAdapter` | Method trả DTO/`ResponseEntity<T>` đồng bộ |
| Muốn interface khai báo và method trả `Mono<T>`/`Flux<T>` | HTTP Service Client + `WebClientAdapter` | Reactive signature do transport adapter hỗ trợ |
| Một tác vụ định kỳ hoặc MVC controller xử lý I/O blocking theo request | `RestClient` hoặc interface proxy trên nó | Không cần thêm Reactor chỉ để GET một DTO |
| SSE, NDJSON hoặc response lớn/infinite stream | `WebClient` hoặc interface proxy trên nó | Streaming reactive, không gom hết vào bộ nhớ |

**Thứ tự ra quyết định:** (1) phương thức gọi **blocking hay non-blocking**, (2) API trực tiếp hay HTTP interface, (3) lựa chọn transport/pooling/timeouts/observability, (4) contract lỗi và retry. Đừng chọn client chỉ vì tên mới hơn hoặc vì “WebClient luôn nhanh hơn”.

## 2. Bốn khái niệm không cùng một cấp

`RestClient`, `RestTemplate` và `WebClient` trực tiếp xây/gửi HTTP requests; **HTTP Service Client** định nghĩa interface Java mà Spring tạo một proxy để **ủy quyền** các requests cho một trong những clients bên dưới.

```text
                    Spring HTTP client usage
                              |
                  +-----------+------------+
                  |                        |
             Fluent/direct             Interface-first
                  |                        |
      +-----------+-----------+       @HttpExchange
      |           |           |       @GetExchange
   RestClient  RestTemplate  WebClient        |
      |           |           |       HttpServiceProxyFactory
      +-----------+-----------+             |
                  |                  HTTP adapter lựa chọn
        Actual client request factory/connector
                  |
      TCP/TLS connection, HTTP exchange, codecs
                  |
             Remote HTTP API
```

- `RestClient`: synchronous fluent API, xuất hiện từ Spring Framework 6.1. Khi gọi terminal `retrieve().body(...)` hoặc `toEntity(...)`, caller đợi kết quả.
- `RestTemplate`: synchronous template-method API lâu đời, các phương thức như `getForObject`, `postForEntity`, `exchange`. Trên Spring 6.2 vẫn hoạt động; Spring 7 deprecates nó theo hướng khuyến nghị `RestClient`.
- `WebClient`: reactive HTTP API, sử dụng `Mono`/`Flux` của Reactor và HTTP connector non-blocking (thường Reactor Netty). Caller có thể compose async sources thay vì giữ thread chờ network.
- HTTP Service Client: một interface với `@HttpExchange`, `@GetExchange`, `@PostExchange`… do `HttpServiceProxyFactory` implement thông qua adapter. Với `RestClientAdapter`, method có thể trả DTO đồng bộ; với `WebClientAdapter`, có thể trả `Mono<T>`/`Flux<T>`.

**Điểm quan trọng:** interface proxy không thay business logic, không tự thêm circuit breaker, retry, transactions hay async execution. Những thuộc tính đó đến từ underlying client/config hoặc một layer ứng dụng được triển khai rõ.

## 3. So sánh chi tiết

| Thuộc tính | RestClient | RestTemplate | WebClient | HTTP Service Client |
| --- | --- | --- | --- | --- |
| Hình thức API | Fluent | Template operations | Fluent reactive | Annotated interface + proxy |
| Bản chất | Synchronous/blocking caller | Synchronous/blocking caller | Non-blocking HTTP I/O khi cấu hình thích hợp | **Phụ thuộc adapter** |
| Kiểu return phổ biến | DTO, `ResponseEntity<T>` | DTO, `ResponseEntity<T>` | `Mono<T>`, `Flux<T>` | DTO hoặc Mono/Flux tùy adapter/signature |
| JSON conversion | `HttpMessageConverter` | `HttpMessageConverter` | Reactive `HttpMessageReader/Writer` | Theo client nền |
| HTTP status errors mặc định | 4xx/5xx ném `RestClientException`/subtype với `retrieve()` | Default `ResponseErrorHandler` ném exception 4xx/5xx | `retrieve()` tạo error signal `WebClientResponseException` khi subscribe | Theo client nền và status policy |
| Custom lỗi | `onStatus`, builder status handler, `exchange` | `ResponseErrorHandler`, `exchange` | `onStatus`, `exchangeToMono/Flux` | Cấu hình underlying client |
| Streaming/backpressure | Không native reactive | Không native reactive | Native publisher/streaming | Native khi dùng reactive adapter |
| Dễ tích hợp Spring MVC blocking code | Rất phù hợp | Phù hợp khi có legacy | Có thể dùng, nhưng cần xác định sync/async contract | Tốt với sync adapter |
| Dễ tích hợp WebFlux event loop | **Không nên gọi trực tiếp** vì blocking | **Không nên gọi trực tiếp** | Phù hợp | Chọn WebClientAdapter |
| Connection pooling | Tùy `ClientHttpRequestFactory` | Tùy `ClientHttpRequestFactory` | Tùy `ClientHttpConnector`/Netty pool | Theo client nền |
| Phù hợp code mới Spring 6.2 | Ưu tiên synchronous | Chủ yếu bảo trì | Reactive/concurrent streaming | Interface-first khi giảm boilerplate có ích |

### 3.1. REST API và transport là hai lớp

Cùng một `RestClient` có thể chạy qua những HTTP libraries/request factories khác nhau. Cũng như vậy, `WebClient` có thể dùng Reactor Netty connector hoặc connector phù hợp khác. Lựa chọn “RestClient vs WebClient” nói lên **programming model** trước; throughput/latency còn phụ thuộc transport implementation, DNS, TLS, pool size, server khả dụng, payload và lưu lượng.

### 3.2. Không đồng nhất Reactive client với Reactive server

Ứng dụng Spring MVC/Servlet **có thể dùng `WebClient`** để gọi downstream, thậm chí return `Mono<T>` từ MVC controller qua Servlet async support. Điều này **không làm `DispatcherServlet` biến thành `DispatcherHandler`**. Ngược lại, WebFlux server vẫn có thể gọi một thư viện blocking, nhưng không nên gọi trực tiếp ở non-blocking event-loop.

### 3.3. Phiên bản cần nhớ

- `RestTemplate`: đã có từ Spring 3.0, dùng lâu năm.
- `WebClient`: có từ Spring 5.0 (WebFlux).
- `@HttpExchange`/HTTP Interface: từ Spring 6.0.
- `RestClient`: từ Spring 6.1.
- Với Spring 6.2, `HttpServiceProxyFactory.builderFor(adapter)` là API đúng; tránh các snippets rất cũ dùng `builder(HttpClientAdapter)` đã deprecated/loại bỏ.
- Từ Spring 7, `RestTemplate` bị đánh dấu deprecated để hướng đến `RestClient`; không suy diễn “mọi project Spring 6.2 ngay lập tức compile warning deprecated”.

## 4. RestClient: mặc định tốt cho HTTP đồng bộ mới

### 4.1. Một GET đơn giản

```java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test")
        .build();

Item item = client.get()
        .uri("/items/{id}", 7)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Item.class);
```

**Đây là snippet**: cần `Item` record/class, imports, endpoint thật. `.retrieve()` trả một `ResponseSpec`; **chưa phải terminal operation**. Lời gọi `.body(Item.class)` sẽ thực hiện exchange và decode result. Vì là synchronous API, thread gọi hàm phải chờ network/exchange/response decoding hoàn tất.

### 4.2. Muốn status và headers

```java
ResponseEntity<Item> response = client.get()
        .uri("/items/{id}", 7)
        .retrieve()
        .toEntity(Item.class);

HttpStatusCode status = response.getStatusCode();
HttpHeaders headers = response.getHeaders();
Item body = response.getBody();
```

Nếu server trả 204 thành công không body, `body` có thể null; kiểu DTO không có nghĩa runtime luôn trả value. Nếu status là 4xx/5xx, `retrieve()` thường ném exception trước khi bạn nhận `ResponseEntity`, trừ khi đã có handler tùy biến.

### 4.3. POST JSON

```java
record CreateItem(String name) {}

Item created = client.post()
        .uri("/items")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(new CreateItem("Java"))
        .retrieve()
        .body(Item.class);
```

Đoạn này minh họa request body converter; API thực có thể trả 201 + Location, nên `toEntity(Item.class)` phù hợp nếu cần đọc header Location hoặc ETag.

### 4.4. Danh sách generic

```java
List<Item> items = client.get()
        .uri("/items")
        .retrieve()
        .body(new ParameterizedTypeReference<List<Item>>() {});
```

Dùng `ParameterizedTypeReference` khi response là list/map generic; `List.class` không lưu element type tại runtime và dễ khiến JSON items trở thành `Map` thay vì `Item`.

### 4.5. Kiểm tra status rõ ràng

```java
Item item = client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError,
                (request, response) -> {
                    throw new IllegalStateException(
                            "Catalog returned " + response.getStatusCode());
                })
        .body(Item.class);
```

Snippet này minh họa syntax, **không khuyến khích biến mọi 4xx thành IllegalStateException** trong production. Hãy phân loại 404/resource missing, 401/403/auth, 429/rate-limit; log an toàn. `RestClient` mặc định đã throw cho 4xx/5xx; custom `onStatus` chỉ cần khi contract khác.

### 4.6. retrieve vs exchange

`RestClient.retrieve()` thực hiện high-level status handling/conversion. `RestClient.exchange((request,response) -> ...)` cho quyền xử lý lower-level với response trực tiếp.

**Bẫy:** trong RestClient, `exchange` bỏ qua status handlers gắn cho `retrieve`. Vì vậy nếu dùng `exchange` để lấy response raw, **phải** quyết định status, kiểm tra 4xx/5xx, body/resource closure đúng. Không chọn exchange chỉ vì trông “linh hoạt hơn” khi bài toán chỉ cần DTO.

## 5. RestTemplate: khi nào giữ và khi nào chuyển?

### 5.1. Dùng trong codebase hiện hữu

```java
RestTemplate template = new RestTemplate();

Item item = template.getForObject(
        "https://catalog.example.test/items/{id}",
        Item.class,
        7);
```

API này vẫn được hỗ trợ trong Spring 6.2 và tiện cho hệ thống cũ. `RestTemplate` sử dụng `HttpMessageConverter`, `ClientHttpRequestFactory` và request interceptors, tương tự hạ tầng dùng chung với `RestClient`. Một bean RestTemplate có thể được dùng từ nhiều threads sau khi cấu hình xong, nhưng **không** nên sửa config concurrent khi đang phục vụ requests.

### 5.2. exchange và generic result

```java
ResponseEntity<List<Item>> result = template.exchange(
        "https://catalog.example.test/items",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        new ParameterizedTypeReference<List<Item>>() {});
```

Snippet minh họa signature (không phải full program). Với cần header authorization, tạo `HttpEntity<?>` có HttpHeaders thích hợp, hoặc cấu hình interceptor cấp token tin cậy. Không gắn raw token vào query string hoặc stdout.

### 5.3. Phân biệt “legacy” với “cần rewrite ngay”

- Không có lý do phải dừng một dịch vụ đang chạy tốt chỉ vì `RestTemplate` có API cũ; thay client phải được test về exceptions, URI encoding, status, timeout, pooling và request interceptors.
- Với code đồng bộ **mới**, `RestClient` thường rõ ràng hơn và được Spring khuyến nghị.
- Spring Framework 7 deprecates `RestTemplate`; vì thế nên tránh tạo thêm khối lượng code RestTemplate mới nếu có thể.
- Đừng chuyển sang WebClient chỉ để “thoát RestTemplate” khi call sites đồng bộ và bạn dự định gọi `.block()` ở khắp nơi; lựa chọn tự nhiên là RestClient.

### 5.4. Chuyển RestTemplate sang RestClient

| RestTemplate | RestClient tương ứng |
| --- | --- |
| `getForObject(url,Item.class,id)` | `get().uri(url,id).retrieve().body(Item.class)` |
| `getForEntity(url,Item.class,id)` | `get().uri(url,id).retrieve().toEntity(Item.class)` |
| `postForObject(url,request,Item.class)` | `post().uri(url).body(request).retrieve().body(Item.class)` |
| `delete(url,id)` | `delete().uri(url,id).retrieve().toBodilessEntity()` |
| `exchange(...,ParameterizedTypeReference)` | `method(...).uri(...).body(...).retrieve().toEntity(new ParameterizedTypeReference<...>() {})` |
| `setErrorHandler(...)` | `onStatus(...)` hoặc builder defaultStatusHandler |
| `ClientHttpRequestFactory` | Chọn requestFactory phù hợp ở RestClient.Builder |

Bảng là **định hướng migration**, không phải phép thay thế máy móc từng overload. Kiểm tra status handling, encoding và đặc biệt các phương thức REST được gọi sau khi convert. Nếu application dùng interceptors, SSL client certificates, OAuth2, proxy hoặc compression thì phải di chuyển cấu hình tương ứng.

## 6. WebClient: khi cần reactive, async fan-out hoặc streaming

### 6.1. GET non-blocking

```java
WebClient client = WebClient.builder()
        .baseUrl("https://catalog.example.test")
        .build();

Mono<Item> pending = client.get()
        .uri("/items/{id}", 7)
        .retrieve()
        .bodyToMono(Item.class);
```

Khác với RestClient:
- `pending` mô tả một **reactive HTTP operation**, thường cold/lazy.
- Không có request thật chỉ vì tạo biến Mono; khi **subscribe** mới bắt đầu.
- Trong WebFlux controller, return Mono/Flux cho framework subscribe.
- Trong Spring MVC controller, có thể return Mono để MVC async adapter xử lý, nhưng server vẫn chạy MVC.
- Không gọi `pending.subscribe()` rồi return `null`, vì detached work không còn gắn response/error/cancellation.

### 6.2. Nhiều request độc lập

```java
Mono<Item> item = client.get()
        .uri("/items/{id}", 7)
        .retrieve()
        .bodyToMono(Item.class);

Mono<Stock> stock = client.get()
        .uri("/stock/{id}", 7)
        .retrieve()
        .bodyToMono(Stock.class);

Mono<ItemPage> page = Mono.zip(
        item, stock,
        (i, s) -> new ItemPage(i, s));
```

Snippets cần `Stock`/`ItemPage` DTOs. Với hai upstream calls độc lập, `Mono.zip` có thể overlap I/O và tổng latency thường gần max latency cộng overhead thay vì cộng hai latency tuần tự. Nhưng nếu một Mono rỗng, zip có thể hoàn tất rỗng. Với 404 cần mô hình rõ `switchIfEmpty` hoặc error mapping.

### 6.3. Fan-out danh sách có giới hạn

```java
Flux<Item> items = Flux.fromIterable(ids)
        .flatMap(id -> client.get()
                .uri("/items/{id}", id)
                .retrieve()
                .bodyToMono(Item.class),
                5);
```

`flatMap(...,5)` giới hạn inner publishers ở **một pipeline/subscription**, không phải tối đa năm connections của toàn ứng dụng. 100 inbound requests mỗi request concurrency=5 có thể tạo tới khoảng 500 operations đang active. Cần connection pool limits, rate-limit/bulkhead global, timeout và backpressure.

Nếu phải giữ output theo thứ tự IDs, cân nhắc `flatMapSequential`; nếu side effects phải thực thi tuần tự, `concatMap`. Tăng concurrency không luôn tăng throughput khi downstream đã bão hòa.

### 6.4. Lỗi HTTP reactive

```java
Mono<Item> item = client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .onStatus(status -> status.value() == 404,
                response -> Mono.error(
                        new ItemNotFoundException(id)))
        .bodyToMono(Item.class);
```

Snippet cần `ItemNotFoundException`. Với `retrieve()`, 4xx/5xx mặc định thành `WebClientResponseException` error signal. `exchangeToMono`/`exchangeToFlux` cho phép kiểm soát response status/body tùy outcome, nhưng phải consume/release body theo contract trước khi thoát callback. Không lưu cả error body lớn hoặc log PII/raw upstream error như một detail gửi client.

### 6.5. Có được dùng WebClient rồi block trong MVC?

Spring reference có hướng dẫn sử dụng `WebClient...bodyToMono(...).block()` trong môi trường đồng bộ, nhưng:
- Điều đó **vẫn chặn thread gọi**; nó không tự mang lại non-blocking end-to-end.
- Tránh làm vậy trên WebFlux event loop, nơi blocking terminal operation có thể bị cấm hoặc làm nghẽn.
- Nếu phần còn lại của ứng dụng synchronous, `RestClient` thường đơn giản hơn.
- Nếu cần chờ **nhiều** requests nhưng không thể đổi call site, có thể compose chúng trước bằng `Mono.zip` rồi block **một lần tại ranh giới thực sự được phép blocking**; không block từng request tuần tự.

### 6.6. Streaming response

```java
Flux<EventDto> events = client.get()
        .uri("/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .retrieve()
        .bodyToFlux(EventDto.class);
```

Đây là snippet, yêu cầu server và codec hỗ trợ event stream, class EventDto. Với SSE/NDJSON nên tránh `collectList()` khi stream vô hạn: nó không hoàn tất và có thể giữ dữ liệu không giới hạn. Cần cancellation, reconnect policy, backpressure, authentication expiry, memory limits và xử lý event ID/duplicate semantics theo protocol.

## 7. HTTP Service Client: interface + proxy, không phải thư viện HTTP thứ tư

### 7.1. Interface với @HttpExchange

```java
@HttpExchange("/items")
interface ItemApi {

    @GetExchange("/{id}")
    Item find(@PathVariable("id") long id);

    @GetExchange
    List<Item> list();
}
```

Đây là interface sync dùng với `RestClientAdapter` hoặc `RestTemplateAdapter`. `@HttpExchange` mô tả common base path, `@GetExchange` mô tả GET endpoint; tương tự có `@PostExchange`, `@PutExchange`, `@PatchExchange`, `@DeleteExchange`. Proxy tạo động implement interface, nên **không** cần tự viết implementation gửi HTTP.

**Lưu ý:** Spring HTTP Service Client interface là **client contract** được thực thi bởi proxy, khác hẳn cách server dispatch annotated controller. Interface không chứa transport, connection pool hay authentication policy; chúng được cấu hình trên client nền.

### 7.2. Proxy đồng bộ trên RestClient

```java
RestClient restClient = RestClient.builder()
        .baseUrl("https://catalog.example.test")
        .build();

RestClientAdapter adapter = RestClientAdapter.create(restClient);

HttpServiceProxyFactory proxyFactory =
        HttpServiceProxyFactory.builderFor(adapter).build();

ItemApi itemApi = proxyFactory.createClient(ItemApi.class);

Item item = itemApi.find(7);  // Call đồng bộ.
```

Đây là snippet; cần đúng imports:
- `org.springframework.web.client.RestClient`;
- `org.springframework.web.client.support.RestClientAdapter`;
- `org.springframework.web.service.invoker.HttpServiceProxyFactory`;
- `org.springframework.web.service.annotation.HttpExchange` và `GetExchange`;
- `org.springframework.web.bind.annotation.PathVariable`.

Method `find` trả Item vì adapter synchronous. Nếu remote bị chậm, thread gọi proxy vẫn chờ như khi gọi RestClient trực tiếp.

### 7.3. Proxy reactive trên WebClient

```java
@HttpExchange("/items")
interface ReactiveItemApi {
    @GetExchange("/{id}")
    Mono<Item> find(@PathVariable("id") long id);

    @GetExchange
    Flux<Item> stream();
}

WebClient webClient = WebClient.builder()
        .baseUrl("https://catalog.example.test")
        .build();

ReactiveItemApi reactiveApi = HttpServiceProxyFactory
        .builderFor(WebClientAdapter.create(webClient))
        .build()
        .createClient(ReactiveItemApi.class);

Mono<Item> future = reactiveApi.find(7);
```

Đoạn trên là snippet cần imports; `Flux<Item>` chỉ phù hợp khi remote response/codec hợp lý cho nhiều values, không phải tự động biến JSON object thành stream. Dùng `org.springframework.web.reactive.function.client.support.WebClientAdapter`. Với reactive signature, không dùng sync `RestClientAdapter` rồi mong proxy tự trả Mono/Flux non-blocking.

### 7.4. Proxy trên RestTemplate

Trong Spring 6.2 cũng có `RestTemplateAdapter`:

```java
RestTemplate template = new RestTemplate();

RestTemplateAdapter adapter =
        RestTemplateAdapter.create(template);

ItemApi api = HttpServiceProxyFactory
        .builderFor(adapter)
        .build()
        .createClient(ItemApi.class);
```

Đây là snippet; muốn dùng relative paths ở interface thì cấu hình `DefaultUriBuilderFactory(baseUrl)` cho RestTemplate trước. Nếu không có base URL, relative `/items/{id}` không đủ để mở connection. Đây là bridge tiện cho dự án cũ nhưng không phải lý do bắt đầu code mới trên RestTemplate.

### 7.5. Annotated method parameters

HTTP interface có thể nhận:
- `@PathVariable`: chèn URI template variables và encode đúng;
- `@RequestParam`: query/form params tùy method/content type;
- `@RequestHeader`: request headers;
- `@RequestBody`: request object;
- `URI` hoặc `UriBuilderFactory` cho dynamic target theo API được framework hỗ trợ.

Ví dụ:

```java
@HttpExchange("/orders")
interface OrderApi {

    @GetExchange("/{id}")
    OrderView find(@PathVariable("id") long id,
                   @RequestHeader("X-Correlation-Id") String traceId);

    @PostExchange(contentType = "application/json")
    OrderView create(@RequestBody CreateOrder command);
}
```

Đây là interface snippet; `OrderView` và `CreateOrder` cần khai báo. Header trace ID không thay Authorization. Sự kiện POST tạo resource có thể cần `ResponseEntity<OrderView>` để đọc Location/ETag nếu API contract yêu cầu.

### 7.6. Kiểu return theo adapter

| Return của HTTP interface | RestClientAdapter / RestTemplateAdapter | WebClientAdapter |
| --- | --- | --- |
| `T` | Có, synchronous | Có thể block qua adapter; cân nhắc tránh trong reactive code |
| `ResponseEntity<T>` | Có, synchronous | Có thể có synchronous form, nhưng vẫn block |
| `void` | Có | Có |
| `Mono<T>` | **Không dùng với sync adapter** | Có |
| `Flux<T>` | **Không dùng với sync adapter** | Có |
| `Mono<ResponseEntity<T>>` | Không | Có |
| `Flux<ServerSentEvent<T>>` | Không native reactive | Có nếu media type/codec hỗ trợ |

Khi API interface trả DTO đồng bộ nhưng dùng WebClientAdapter, proxy phải chờ để cung cấp một giá trị synchronous; điều đó không làm method trở thành non-blocking. Muốn reactive thật, khai báo reactive return type và compose publisher.

### 7.7. Ưu điểm và giới hạn của interface-first

**Ưu điểm:** code service gọn, một chỗ mô tả contract của nhiều endpoints, dễ thay implementation qua mock/stub interface, tái sử dụng API shape, giảm lặp boilerplate. **Giới hạn:** những requests rất động, streaming logic phức tạp, branching theo status, request signing/customization từng lần có thể dễ đọc hơn với direct RestClient/WebClient. Tính ổn định API interface đòi hỏi versioning và test khi upstream đổi.

**Không nhầm với OpenFeign:** Spring HTTP Service Client là tính năng framework riêng, không tự cung cấp các features của những hệ client declarative khác (service discovery, load balancing, circuit breaker, retry) nếu bạn chưa cấu hình.

## 8. Java 17 + Spring Boot 3.5: ví dụ chạy cả bốn lựa chọn

Mục tiêu một project có một **supplier HTTP endpoint giả lập** chạy ngay trong process, và bốn call paths:
- `/compare/rest-client/7` — gọi bằng RestClient đồng bộ;
- `/compare/rest-template/7` — gọi bằng RestTemplate đồng bộ;
- `/compare/http-interface/7` — gọi bằng HTTP Service Client trên RestClient;
- `/compare/web-client/7` — trả `Mono<Item>` từ WebClient.

Ứng dụng này dùng **Spring MVC server** để cho thấy WebClient vẫn có thể được dùng trong dự án MVC. Vì test demo supplier và caller cùng process dùng localhost:8080, không cần remote service thật; **đây không phải kiến trúc microservice production**. Cẩn thận: request sync gọi lại cùng server có thể góp phần cạn servlet thread pool dưới tải lớn; chỉ sử dụng pattern self-call này để học.

### 8.1. Dependency và cấu trúc

Tạo Maven Spring Boot **3.5.x**, Java 17+, các dependencies:
- **Spring Web**: MVC server, RestClient, RestTemplate;
- **Spring Reactive Web**: WebClient, Reactor.

Trong POM từ Spring Initializr có fragment:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

Đây **chỉ là dependency fragment**, không phải POM hoàn chỉnh; parent/BOM/plugins của Initializr quản lý các versions. Khi có **cả hai starters** trên Boot 3.5, mặc định web application type là **Servlet MVC**, không phải WebFlux server. `WebClient.Builder` vẫn có thể được auto-configure để gọi downstream reactive HTTP.

Cấu trúc:

```text
src/main/java/com/example/demo/DemoApplication.java
src/test/java/com/example/demo/RestClientExampleTest.java
```

### 8.2. Source đầy đủ (một file)

Lưu toàn bộ vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // Mọi client gọi local supplier chỉ để minh họa.
    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.baseUrl("http://localhost:8080").build();
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.rootUri("http://localhost:8080").build();
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8080").build();
    }

    @Bean
    SupplierApi supplierApi(RestClient restClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(SupplierApi.class);
    }
}

record Item(long id, String name, int stock) {}

record ErrorView(String code, int upstreamStatus) {}

// Client-side declarative HTTP service interface.
@HttpExchange("/supplier/items")
interface SupplierApi {
    @GetExchange("/{id}")
    Item find(@PathVariable("id") long id);
}

// Server-side stub (chỉ phục vụ demo; không phải upstream thật).
@RestController
@RequestMapping("/supplier/items")
class SupplierController {

    @GetMapping("/{id}")
    ResponseEntity<Item> find(@PathVariable("id") long id) {
        if (id != 7L) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                new Item(7L, "Spring Web", 12));
    }

    @GetMapping
    List<Item> list() {
        return List.of(new Item(7L, "Spring Web", 12));
    }
}

// Bốn client styles cùng gọi chung một supplier endpoint.
@RestController
@RequestMapping("/compare")
class ComparisonController {

    private final RestClient restClient;
    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final SupplierApi supplierApi;

    ComparisonController(
            RestClient restClient,
            RestTemplate restTemplate,
            WebClient webClient,
            SupplierApi supplierApi) {
        this.restClient = restClient;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
        this.supplierApi = supplierApi;
    }

    @GetMapping("/rest-client/{id}")
    Item viaRestClient(@PathVariable("id") long id) {
        return restClient.get()
                .uri("/supplier/items/{id}", id)
                .retrieve()
                .body(Item.class);
    }

    @GetMapping("/rest-template/{id}")
    Item viaRestTemplate(@PathVariable("id") long id) {
        return restTemplate.getForObject(
                "/supplier/items/{id}", Item.class, id);
    }

    @GetMapping("/http-interface/{id}")
    Item viaHttpInterface(@PathVariable("id") long id) {
        return supplierApi.find(id);
    }

    @GetMapping("/web-client/{id}")
    Mono<Item> viaWebClient(@PathVariable("id") long id) {
        return webClient.get()
                .uri("/supplier/items/{id}", id)
                .retrieve()
                .bodyToMono(Item.class);
    }
}

// Giữ thông điệp lỗi an toàn thay vì lộ raw upstream body.
@RestControllerAdvice
class DemoClientErrors {

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ErrorView> syncError(
            RestClientResponseException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErrorView(
                        "UPSTREAM_HTTP_ERROR",
                        exception.getStatusCode().value()));
    }

    @ExceptionHandler(WebClientResponseException.class)
    ResponseEntity<ErrorView> reactiveError(
            WebClientResponseException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErrorView(
                        "UPSTREAM_HTTP_ERROR",
                        exception.getStatusCode().value()));
    }
}
```

### 8.3. Vì sao code hợp lệ trên Boot 3.5?

- Java 17 cho phép `record`; một file có một public top-level class `DemoApplication`, các classes/interfaces/records còn lại package-private.
- `@SpringBootApplication` component-scan thấy `SupplierController`, `ComparisonController`, `DemoClientErrors` cùng package; các beans trong main class được phát hiện.
- `RestClient.Builder`, `RestTemplateBuilder`, `WebClient.Builder` được Boot cung cấp khi starters liên quan có mặt; beans built một lần và được dùng lại, không tạo HTTP client cho từng request.
- `SupplierApi` là HTTP Service Client proxy do `RestClientAdapter` xử lý; nó synchronous đúng chữ ký `Item find(long)`.
- `viaWebClient` return Mono, nên controller MVC dùng async support; **server vẫn Servlet MVC**. Không cần `.block()` để lấy Item trong controller.
- `DemoClientErrors` minh họa mapping errors từ hai client families sang status và sanitized error body. Trên API production, cần policy riêng cho rate limits, 502/503 upstream failures, permissions và return-timeout; không đơn giản forward mọi upstream status.
- Local supplier và caller cùng dùng port 8080; khi đổi `server.port`, cần đổi cả `baseUrl`/`rootUri` ở ba beans tương ứng. Đây là demo hardcoded local URLs, không phải config deployment.
- Không có network timeout, circuit breaker, retries, auth, observability hoặc connection limits do ví dụ nhắm vào **lựa chọn API**. Cần thêm trước production.

### 8.4. Chạy

Ở thư mục có Maven wrapper do Spring Initializr tạo:

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows PowerShell / CMD
mvnw.cmd spring-boot:run
```

Giữ port 8080 như code và không có dịch vụ khác dùng port đó.

### 8.5. So sánh kết quả HTTP

Gọi bốn phương án:

```bash
curl -i 'http://localhost:8080/compare/rest-client/7'
curl -i 'http://localhost:8080/compare/rest-template/7'
curl -i 'http://localhost:8080/compare/http-interface/7'
curl -i 'http://localhost:8080/compare/web-client/7'
```

Mỗi request thành công trả status 200 và một JSON object có giá trị:

```json
{"id":7,"name":"Spring Web","stock":12}
```

Sự khác biệt là **call mechanics**, không phải khác dữ liệu hay HTTP business contract:
- RestClient: blocking thread thực thi MVC handler trong lúc chờ local supplier.
- RestTemplate: cũng blocking, nhưng API template method.
- HTTP interface: method invocation trông như Java interface, bên dưới vẫn RestClient synchronous.
- WebClient: method trả Mono, không block trong controller code; Spring MVC dùng Servlet async support khi đợi Mono hoàn tất. HTTP I/O phía client là reactive.

### 8.6. Trường hợp không tìm thấy

```bash
curl -i 'http://localhost:8080/compare/rest-client/99'
curl -i 'http://localhost:8080/compare/rest-template/99'
curl -i 'http://localhost:8080/compare/http-interface/99'
curl -i 'http://localhost:8080/compare/web-client/99'
```

Supplier trả 404. Các clients mặc định biến 4xx thành exception/reactive error; advice minh họa mapping thành response 404 JSON:

```json
{"code":"UPSTREAM_HTTP_ERROR","upstreamStatus":404}
```

Đây là **deliberate demo policy**, không mặc nhiên sao chép cách “forward nguyên upstream 404/500 cho browser” trong production. Có lúc frontend resource không tìm thấy thì 404 là hợp lý; có lúc một third-party upstream 404 nghĩa là integration misconfiguration và nên được nội bộ xử lý thành 502. API gateway cần policy được thiết kế theo domain.

### 8.7. Powershell

```powershell
curl.exe -i "http://localhost:8080/compare/rest-client/7"
curl.exe -i "http://localhost:8080/compare/rest-template/7"
curl.exe -i "http://localhost:8080/compare/http-interface/7"
curl.exe -i "http://localhost:8080/compare/web-client/7"
```

Chạy `curl.exe` để tránh alias `curl` của một số phiên bản PowerShell. Nếu JSON pretty-printed, không so whitespace hay thứ tự object fields như dữ liệu nghiệp vụ.

## 9. Test RestClient và HTTP interface không cần remote server

Spring Test có `MockRestServiceServer` hỗ trợ bind vào `RestClient.Builder` và intercept mock HTTP requests. Thích hợp để verify URL, HTTP method, JSON conversion, default exception mapping mà không phụ thuộc service ngoài.

Thêm test dependency với Spring Boot 3.5:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 9.1. Test file đầy đủ

Tạo `src/test/java/com/example/demo/RestClientExampleTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientExampleTest {

    @Test
    void restClientDecodesJson() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.example.test");

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.build();

        server.expect(requestTo(
                        "https://catalog.example.test/supplier/items/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Spring Web\",\"stock\":12}",
                        MediaType.APPLICATION_JSON));

        Item item = client.get()
                .uri("/supplier/items/{id}", 7)
                .retrieve()
                .body(Item.class);

        assertNotNull(item);
        assertEquals(7L, item.id());
        assertEquals("Spring Web", item.name());
        assertEquals(12, item.stock());
        server.verify();
    }

    @Test
    void annotatedProxyUsesUnderlyingRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.example.test");

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.build();
        SupplierApi api = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client))
                .build()
                .createClient(SupplierApi.class);

        server.expect(requestTo(
                        "https://catalog.example.test/supplier/items/7"))
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Spring Web\",\"stock\":12}",
                        MediaType.APPLICATION_JSON));

        Item item = api.find(7L);
        assertEquals(7L, item.id());
        server.verify();
    }

    @Test
    void status404IsRaisedByRetrieve() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.example.test");

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.build();

        server.expect(requestTo(
                        "https://catalog.example.test/supplier/items/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> client.get()
                        .uri("/supplier/items/{id}", 99)
                        .retrieve()
                        .body(Item.class));

        assertEquals(404, exception.getStatusCode().value());
        server.verify();
    }
}
```

Các tests này dùng `Item` và `SupplierApi` package-private từ file main cùng package. Để chạy, Spring Initializr cần Maven/Java17 và `spring-boot-starter-test`. Test `RestClient.Builder` được bind tới mock server trước khi gọi `build()`; không cần mở socket thật.

### 9.2. Mocking RestTemplate

Với RestTemplate, Spring Test hỗ trợ:

```java
RestTemplate template = new RestTemplate();
MockRestServiceServer server = MockRestServiceServer.createServer(template);

server.expect(requestTo("https://catalog.example.test/items/7"))
        .andRespond(withSuccess(
                "{\"id\":7,\"name\":\"Spring Web\",\"stock\":12}",
                MediaType.APPLICATION_JSON));

Item item = template.getForObject(
        "https://catalog.example.test/items/{id}",
        Item.class,
        7);

server.verify();
```

Đây là fragment trong test method, cần imports. Không cần dùng WebClient chỉ để mock một RestTemplate call.

### 9.3. Testing WebClient

Có thể dùng local HTTP mock server (ví dụ OkHttp MockWebServer hoặc WireMock), hoặc `WebTestClient` để test WebFlux handler/server, hoặc cấp `ExchangeFunction` test double khi muốn test client only. **`MockRestServiceServer` không phải công cụ dùng nguyên xi cho WebClient**, vì WebClient dựa trên reactive connector/ExchangeFunction. Khi test WebClient, cần kiểm tra:
- request URI/method/headers và JSON body;
- 2xx decoding thành Mono/Flux;
- 404/429/500 thành error signal theo policy;
- timeout/retry/cancellation;
- streaming/backpressure và response body release khi dùng `exchangeToMono`;
- trace/auth/tenant header propagation (không log token).

### 9.4. HTTP service interface unit test

Với interface-first, tầng business gọi `SupplierApi` có thể mock interface trực tiếp (Mockito) mà không cần mở HTTP. Tuy nhiên riêng tests proxy/adapter vẫn cần mock HTTP để đảm bảo annotations `@HttpExchange`/`@GetExchange`/`@PathVariable` tạo đúng path, headers và body.

## 10. Timeout và connection pool: điều quan trọng hơn cú pháp

### 10.1. Một request phải có deadlines

Cần phân biệt:
- DNS lookup timeout;
- connect timeout (mở TCP connection);
- TLS handshake timeout;
- pending connection acquisition/pool wait timeout;
- response/read timeout;
- write timeout;
- toàn request deadline;
- thời gian retry/backoff nằm trong overall SLA.

Không có một option chung như `timeout=2s` bao tất cả các giới hạn ở mọi HTTP transport. `RestClient`/`RestTemplate` dùng `ClientHttpRequestFactory`; `WebClient` dùng `ClientHttpConnector`. Timeout/connection pool có thể nằm trên Apache HttpComponents, JDK HttpClient, Reactor Netty hoặc transport khác được chọn.

### 10.2. Timeout trên RestClient dùng request factory

Ví dụ nguyên tắc:

```java
// Snippet: cấu hình ở HTTP request factory bạn thực sự sử dụng.
ClientHttpRequestFactory factory = configuredFactory;

RestClient client = RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://catalog.example.test")
        .build();
```

`configuredFactory` phải được tạo/cấu hình connect/read/pool timeout theo concrete class. Đây không phải mã chạy độc lập; **không** mặc định chỉ xây RestClient.Builder là đã có production-safe timeouts. Trong Spring Boot, hãy xem request factory selection và RestClient customization/builder beans trước khi tự tạo một HttpClient mới bỏ qua cấu hình của Boot.

### 10.3. WebClient/Reactor Netty example

```java
HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
        .responseTimeout(Duration.ofSeconds(3));

WebClient client = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl("https://catalog.example.test")
        .build();
```

Đây là snippet cho connector Reactor Netty, cần imports:
- `reactor.netty.http.client.HttpClient`;
- `io.netty.channel.ChannelOption`;
- `org.springframework.http.client.reactive.ReactorClientHttpConnector`;
- `java.time.Duration`.

`responseTimeout` không tự là overall end-to-end deadline và chưa cấu hình pending acquire timeout/pool max size. Nếu tổ chức dùng proxy/TLS connection reuse, cần thêm TLS/HTTP2/proxy/pool settings và test slow responses.

### 10.4. Giới hạn kết nối và tổng concurrency

Giả sử 60 inbound requests, mỗi request fan-out 10 HTTP calls:
- Có thể tạo **600 in-flight operations** dù một `flatMap(mapper, 10)` trông nhỏ.
- Downstream pool max connections, max pending acquire và rate limit phải đủ hoặc có bulkhead.
- Với RestClient/RestTemplate, mỗi caller thường chiếm một thread chờ response; thread pool saturation có thể làm tail latency tăng.
- Với WebClient, bớt giữ thread chờ I/O nhưng **không xóa** bottleneck ở remote services hoặc sự cần thiết của backpressure/limits.

### 10.5. Singleton/shared client beans

Nên cấu hình clients một lần thành beans hoặc reuse builders đã cấu hình. Đừng tạo RestClient/WebClient mới cho mỗi HTTP request; chi phí pool/TLS/config và resource lifecycle sẽ bị lãng phí. Builders có tính mutable ở giai đoạn configuration; clients được built được chia sẻ trong ứng dụng theo thiết kế. Với RestTemplate, cấu hình trước khi share và không sửa đồng thời runtime.

## 11. Error handling: HTTP status, transport và parsing phải tách riêng

| Tình huống | RestClient/RestTemplate | WebClient |
| --- | --- | --- |
| 404/4xx từ server | Default status handling ném RestClientResponseException subtype | `retrieve()` chuyển thành WebClientResponseException error signal |
| 500/5xx từ server | Exception chứa status/body/header khi có | Error signal với status/header/body khi có |
| DNS/connect timeout | I/O exception bọc trong RestClientException family | `WebClientRequestException` hoặc cause/timeout signal |
| JSON sai schema/decoder | Message conversion/RestClientException family | Reactive codec/decoding error |
| Response 204 | DTO body có thể null | `Mono<T>` thường complete empty |
| Consumer cancel | Blocking caller có cơ chế interruption/transport-specific | Publisher cancellation có thể truyền tới connector |
| Retry | Phải thực thi explicit policy ở service/client resilience layer | `retryWhen` + filter/backoff với reactive pipeline |

Các kiểu exception thực tế có thể khác theo phương thức `retrieve/exchange`, status handler custom, codec, transport và Spring version. Đừng `catch(Exception.class) -> HTTP 200`.

### 11.1. Không map mọi upstream 404/500 theo một cách

Nếu client GET đúng resource mà upstream 404, API của bạn có thể trả 404. Nhưng nếu downstream là **identity provider** hoặc payment gateway, upstream 404 có thể nghĩa là route misconfiguration, không phải khách hàng không tồn tại. Bản đồ status phải có ý nghĩa nghiệp vụ.

### 11.2. Retry không tự động miễn phí

Chỉ retry lỗi tạm thời có thể phục hồi: connection reset/timeout trong điều kiện idempotent, 502/503/504/429 nếu API contract và Retry-After cho phép. **Không** retry bừa POST tạo thanh toán, 400 validation, 401/403 hay operations đã commit side effects mà không có idempotency key.

Với WebClient:

```java
Mono<Item> item = client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .bodyToMono(Item.class)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(150))
                .filter(ex -> isTransient(ex)));
```

Đây là snippet; `isTransient` phải được định nghĩa và cần import `reactor.util.retry.Retry`. Có 2 retry bổ sung sau lần đầu khi phù hợp filter. Đặt overall deadline và backoff/jitter để tránh retry storm.

Với synchronous callers, dùng resilience wrapper như Spring Retry/Resilience4j theo nhu cầu (phải bổ sung dependencies/config), không viết while(true) retry thiếu giới hạn.

### 11.3. Tránh log lộ secrets

Không log:
- `Authorization: Bearer ...`, cookies/session tokens;
- query strings chứa PII hoặc signed URLs;
- toàn raw HTTP response lỗi có dữ liệu khách hàng;
- credentials trong URI hoặc proxy settings.

Nên log upstream service logical name, safe status class, latency, requestId/traceId và mã lỗi nội bộ, với retention/sampling hợp lý.

## 12. Header, URI, auth và request signing

### 12.1. URI variables và encoding

Thay vì tự ghép URL với input của người dùng:

```java
String unsafe = baseUrl + "/items/" + userInput;
```

Dùng URI templates:

```java
client.get()
        .uri("/items/{id}", itemId)
        .retrieve()
        .body(Item.class);
```

URI template expansion giảm lỗi encoding và path injection; vẫn cần kiểm tra `itemId` thuộc business contract. Nếu client cho phép dynamic absolute URL từ user, có nguy cơ **SSRF** dù URI được encode: phải allowlist hosts/protocol/ports, deny private/internal targets theo policy và kiểm soát redirect/DNS rebinding.

### 12.2. Auth propagation

RestClient:
```java
client.get()
        .uri("/items/{id}", id)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .retrieve()
        .body(Item.class);
```

WebClient:
```java
client.get()
        .uri("/items/{id}", id)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .retrieve()
        .bodyToMono(Item.class);
```

Đây là snippets: `accessToken` phải được cấp hợp lệ theo OAuth2/service credentials flow, không copy token người dùng sang mọi downstream. Nếu dùng Spring Security OAuth2 client, cân nhắc dedicated interceptors/filters/AuthorizedClientManager. Tránh gửi token qua hosts không thuộc trust boundary; redirect phải được xử lý theo chính sách.

### 12.3. Content-Type và Accept

- `Content-Type` của request nói body đang gửi là JSON, form, binary…
- `Accept` nói client muốn response media type gì.
- RestClient/RestTemplate sử dụng `HttpMessageConverter`; WebClient dùng reactive codec writers/readers.
- Nếu server trả Content-Type không hỗ trợ, client có thể phát decoding error; không phải do HTTP status 500 trên server.

## 13. Observability, tracing, cancellation và testing

### 13.1. Metrics

Với Spring Boot Actuator/Micrometer nếu đã cấu hình, có thể instrument HTTP client calls để thấy:
- outgoing request count/status;
- latency average/p95/p99;
- timeout, connection pool wait, DNS/TLS errors;
- retry rate và downstream 429/503;
- active requests/connection counts;
- slow consumer/stream cancellation (WebClient).

Không đặt raw URL path có IDs riêng lẻ, requestId hay email address làm metrics tags high-cardinality; ưu tiên URI templates và logical service names.

### 13.2. Cân nhắc testing theo client

| Client | Kiểm thử có giá trị |
| --- | --- |
| RestClient | `MockRestServiceServer.bindTo(RestClient.Builder)`, kiểm tra URI/method/body/status |
| RestTemplate | `MockRestServiceServer.createServer(template)`, verify request/response |
| WebClient | HTTP mock server/ExchangeFunction stub, Reactor `StepVerifier` cho Mono/Flux |
| HTTP Service Client | Mockito mock interface cho business test + HTTP mock cho proxy/annotations |
| Tất cả | Integration test thật với TLS/proxy/pool/timeouts, 4xx/5xx, payload lớn |

Test không chỉ assert DTO 200: cần malformed JSON, bad status, 204 empty, timeouts, cancellation, header/auth security, retry/no retry và wrong Content-Type.

### 13.3. HTTP client không quyết định transaction

Một call POST sang remote service có thể commit thành công dù local client timeout trước khi nhận response. Retry không thể biết chắc status cuối nếu API thiếu idempotency key/reconciliation. `RestClient`, `WebClient`, hay interface proxy đều **không tự tạo distributed transaction**. Khi có nhiều downstream writes, phải cân nhắc saga/outbox/compensation và business-level idempotency.

### 13.4. Test thực và mock không giống nhau

`MockRestServiceServer` mock HTTP boundary nên nhanh, deterministic, không chứng minh DNS/TLS/client connection pool hoạt động thật. Với các lỗi proxy certificates, keepalive và slow downstream, cần test qua embedded/local upstream server hoặc staging. Việc một MockRestServiceServer test pass không tự chứng minh client production có proper timeouts.

## 14. Anti-pattern phổ biến

| Anti-pattern / triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Dùng WebClient + `block()` cho mọi call đồng bộ mới | Reactive overhead không mang lợi ích | RestClient |
| Dùng RestTemplate trong WebFlux handler event-loop | Blocking I/O | WebClient hoặc bridge boundedElastic có quota |
| Dùng RestClient rồi kỳ vọng Mono/Flux reactive | Client sync | WebClient |
| Dùng `Mono.just(restClient.get()...)` | Call đồng bộ đã chạy trước Mono | Chọn WebClient hoặc controlled bridge |
| HTTP interface trả Mono với RestClientAdapter | Adapter sync không hỗ trợ signature reactive | WebClientAdapter |
| Tưởng HTTP interface là protocol mới | Interface chỉ là proxy | Cấu hình underlying client |
| `retrieve()` mà không terminal operation ở RestClient | Chưa gửi request | Thêm `body/toEntity/toBodilessEntity` |
| `WebClient.retrieve()` mà không subscribe | Publisher lazy | Return Mono/Flux cho framework subscribe |
| Tạo WebClient mới mỗi request | Pool/resources/config thừa | Reuse configured bean |
| RestTemplate.config mutate trong concurrent requests | Không thread-safe khi sửa runtime | Cấu hình startup một lần |
| `exchange` rồi quên kiểm tra status | Bỏ default status mapping | Handle 4xx/5xx chủ động |
| `body(List.class)` khiến item thành Map | Generic type erasure | ParameterizedTypeReference |
| 204 body được dùng như non-null | Response rỗng | Optional/empty semantics rõ |
| Trả raw upstream exception body cho client | Lộ implementation/PII | Domain-safe error DTO |
| WebClient flatMap concurrency quá lớn | Downstream/pool saturation | Per request và global limits |
| Dùng `collectList()` trên stream vô hạn | Memory/never complete | SSE/NDJSON streaming, termination |
| Retry POST không idempotent | Duplicated side effects | Idempotency key/reconciliation |
| Chưa cấu hình timeout | Thread/connection treo lâu | Factory/connector timeouts + deadline |
| Kết quả 200 nhưng response invalid JSON | Codec/Content-Type mismatched | Test media types và conversion |
| Cho client nhập absolute URL bất kỳ | SSRF | URL allowlist/network egress controls |
| Gửi bearer token cho host redirect lạ | Credential leak | Redirect/auth policies |
| Chỉ mock test mà không load test | Không đo pool/DNS/TLS/resource pressure | Integration/load tests |
| Dùng Spring7 API trong Boot3.5 | Version/API mismatch | Khóa đúng framework version khi copy code |

## 15. Checklist chọn client và triển khai

1. [ ] Module của mình dùng synchronous thread-per-request, MVC hay WebFlux reactive event-loop?
2. [ ] Với sync mới: ưu tiên RestClient; legacy RestTemplate vẫn dùng được trong Spring 6.2 nhưng có migration plan.
3. [ ] Nếu chọn WebClient, caller có thể **compose Publisher** mà không chèn `block()` vào event-loop?
4. [ ] Nếu chọn HTTP interface, adapter backend và method return types có khớp (sync vs reactive)?
5. [ ] Base URL thuộc trusted configuration; dynamic targets được validate chống SSRF?
6. [ ] URI templates/encoding, Content-Type/Accept và DTO generic đúng?
7. [ ] Connection/read/response/pool wait timeouts và overall deadline có giới hạn?
8. [ ] Retry filter/backoff/jitter/idempotency/Retry-After đúng nghiệp vụ?
9. [ ] Pool size, max pending acquire, per-request concurrency và global rate limits được đo?
10. [ ] Status 404/401/403/429/5xx được map theo domain contract, không leak upstream body?
11. [ ] Có auth propagation/OAuth2 policy và redaction tokens/cookies?
12. [ ] Client beans được tái sử dụng; không mutate shared config lúc runtime?
13. [ ] HTTP Service Client interface không biến thành danh sách overload phức tạp hơn direct client?
14. [ ] 204/empty response và codecs xử lý được?
15. [ ] Với streaming, có cancellation, backpressure và resource release?
16. [ ] Tests cover URI, method, JSON, errors, timeout, auth, retry và proxy integration?
17. [ ] Giải pháp đã được benchmark theo workload thực, không suy từ tên thư viện?

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Trong code Spring MVC đồng bộ mới, nên chọn RestClient hay WebClient cho một GET JSON đơn giản?

**Đáp án:** Thường RestClient vì synchronous contract rõ và API fluent. WebClient có ý nghĩa khi cần non-blocking composition/streaming.

**Câu 2.** HTTP Service Client là HTTP transport thứ tư?

**Đáp án:** Không. Nó là Java interface + proxy trên RestClient, RestTemplate hoặc WebClient qua adapter.

**Câu 3.** `RestClient.get().retrieve()` đã gửi request chưa?

**Đáp án:** Theo Spring RestClient API, `retrieve()` chỉ trả ResponseSpec; terminal `body/toEntity/toBodilessEntity` mới thực thi exchange.

**Câu 4.** WebClient trả Mono có nghĩa request được gửi ngay khi tạo Mono?

**Đáp án:** Không, Publisher thường lazy; phải có subscription, framework có thể subscribe khi controller return Mono.

**Câu 5.** Khi HTTP interface method trả `Mono<Item>` nên chọn adapter nào?

**Đáp án:** `WebClientAdapter`; sync adapters không cung cấp reactive return contract.

**Câu 6.** `RestTemplate` ngừng hoạt động trên Spring Framework 6.2?

**Đáp án:** Không. Nó còn hoạt động; từ Spring 7 API được đánh dấu deprecated theo hướng chuyển sang RestClient.

**Câu 7.** `WebClient.bodyToMono(...).block()` trong WebFlux event-loop có an toàn?

**Đáp án:** Không; tránh blocking terminal methods trong WebFlux event-loop. Compose non-blocking Mono/Flux.

**Câu 8.** Vì sao cần timeout ngay cả khi dùng WebClient?

**Đáp án:** Non-blocking vẫn có connection/pool/server resources, có thể in-flight lâu vô hạn nếu không có deadlines; high concurrency không sửa được slow downstream.

**Câu 9.** Khi POST timeout nhưng upstream đã commit, retry mù có rủi ro gì?

**Đáp án:** Có thể tạo giao dịch/tài nguyên trùng; cần idempotency key và reconciliation.

**Câu 10.** MockRestServiceServer test pass có nghĩa TLS/proxy/pooling production đã được xác minh?

**Đáp án:** Không. Nó mock HTTP boundary; cần integration/load tests riêng.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — REST Clients](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html): lựa chọn RestClient, RestTemplate, WebClient và HTTP interfaces, method signatures, status/headers.
- [Spring Framework — REST Clients (current)](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html): cập nhật migration và tình trạng RestTemplate ở Spring 7.
- [Spring Framework 6.2 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestClient.html): retrieve, exchange, requestFactory, default status handlers.
- [Spring Framework 6.2 — RestTemplate Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestTemplate.html): synchronous template methods và request factory.
- [Spring Framework 6.2 — WebClient Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/reactive/function/client/WebClient.html): reactive HTTP API.
- [Spring Framework 6.2 — WebClient Synchronous Use](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-synchronous.html): hợp đồng block ở ranh giới sync và compose nhiều calls.
- [Spring Framework 6.2 — HttpServiceProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/service/invoker/HttpServiceProxyFactory.html): `builderFor(adapter)` và `createClient`.
- [Spring Framework 6.2 — HTTP Interface Client](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-http-interface-client.html): @HttpExchange/@GetExchange, adapters và return values.
- [Spring Boot 3.5 — Calling REST Services](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html): RestClient.Builder, RestTemplateBuilder, WebClient.Builder auto-configuration.
- [Spring Framework — WebClient retrieve()](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html): status/error handling.
- [Spring Framework — WebClient Exchange](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html): exchangeToMono/Flux và response release rules.
- [Spring Boot 3.5 — Testing Applications](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html): Spring Test/client testing practices.

**Kết luận:** Với module “HTTP client đồng bộ”, **RestClient** là lựa chọn mặc định hợp lý cho code Spring mới. **RestTemplate** tiếp tục hỗ trợ code hiện hữu và có thể chuyển dần, đặc biệt khi nâng Spring 7. **WebClient** phù hợp khi có non-blocking composition, fan-out hay stream và tránh blocking event loop. **HTTP Service Client** không phải một transport khác: nó đặt một interface annotation lên RestClient/WebClient (hoặc RestTemplate), nên lựa chọn **đồng bộ hay reactive vẫn nằm ở adapter và kiểu return**. Bất kể chọn API nào, chất lượng production phụ thuộc timeouts, pooling, errors, auth, retries, backpressure, observability và integration tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-002">Tạo RestClient và cấu hình base URL, header, cookie mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Spring Framework Web — Tạo RestClient và cấu hình base URL, header, cookie mặc định

## 1. Mục tiêu

`RestClient` là HTTP client **đồng bộ** dùng fluent API của Spring Framework. Được giới thiệu từ Spring Framework 6.1 và được mở rộng trong 6.2, RestClient giúp Spring MVC services, scheduled jobs hoặc batch applications gọi một REST API khác, decode JSON và quản lý HTTP status mà không phải tự mở socket.

Bài học này tập trung vào **một RestClient dùng lại được** với cấu hình chung:

- Tạo client bằng `RestClient.create()` hoặc `RestClient.builder()`.
- `baseUrl(String/URI)` để không lặp URL host/prefix ở từng request.
- `defaultUriVariables(...)` và quan hệ với `uriBuilderFactory(...)`.
- `defaultHeader(...)` và `defaultHeaders(...)` cho headers mặc định.
- `defaultCookie(...)` và `defaultCookies(...)` cho cookies mặc định **từ Spring Framework 6.2**.
- Ghi đè header/cookie ở mức **một request**.
- `defaultRequest(...)` khi cần thiết lập giá trị động mỗi request.
- Cách Spring Boot 3.5 auto-configure và inject `RestClient.Builder`, phân biệt builder với client đã build.
- Kiểm thử bằng `MockRestServiceServer.bindTo(builder)` và JUnit 5, không cần API ngoài Internet.
- Tránh dùng cookie/token nhạy cảm mặc định cho một singleton shared giữa nhiều người dùng.
- Cấu hình nâng cao về timeout, request factory, error handling, pooling, logging và authorization.

**Ngữ cảnh:** module cha là **HTTP client đồng bộ**. Không nhầm `RestClient` với `WebClient` (reactive) hoặc WebFlux server API. `retrieve().body(Class)` chặn thread đang thực thi tới khi nhận response/lỗi.

## 2. So sánh ba HTTP clients của Spring

| Client | Mô hình | Cách dùng chính |
| --- | --- | --- |
| `RestClient` | Synchronous, fluent | Ứng dụng blocking/MVC, outbound REST |
| `RestTemplate` | Synchronous, template methods | Legacy applications, migration |
| `WebClient` | Non-blocking, `Mono`/`Flux` | WebFlux/reactive pipelines |

Một RestClient có thể tạo:

~~~java
RestClient client = RestClient.create();
String body = client.get()
        .uri("https://example.org/api/health")
        .retrieve()
        .body(String.class);
~~~

Đây là fragment: endpoint chỉ minh họa. Sau khi được `build()`, RestClient có thể dùng lại an toàn giữa nhiều threads theo tài liệu Spring. **Builder mutable không nên được thay đổi đồng thời ở runtime**: cấu hình một lần lúc bootstrap và inject client đã build.

### 2.1. Vì sao không tạo RestClient mới cho từng request?

Mỗi lần tạo lại RestClient và underlying HTTP request factory/connection pool có thể gây overhead, quản lý connection và observability khó hơn. Trong application, khai báo một `@Bean RestClient` theo từng upstream. Các request specs được tạo riêng bằng `client.get()`/`post()` trên client shared, chứa values riêng cho mỗi HTTP call.

### 2.2. Khi nào phải dùng WebClient?

Nếu đang chạy code trên Reactor Netty event loop, một outbound RestClient call có thể làm block event loop. Trong WebFlux/reactive pipeline, ưu tiên `WebClient`; nếu bắt buộc gọi blocking legacy API, offload và giới hạn concurrency, không gọi `block()` trong event-loop.

## 3. Các cách khởi tạo

### 3.1. Không có defaults

~~~java
RestClient client = RestClient.create();
~~~

Request thường phải dùng URL tuyệt đối:

~~~java
String body = client.get()
        .uri("https://service.example.test/info")
        .retrieve()
        .body(String.class);
~~~

URL này không phải dịch vụ thật.

### 3.2. Dùng builder

~~~java
RestClient catalog = RestClient.builder()
        .baseUrl("https://catalog.example.test/api/v1")
        .defaultHeader("X-Client-Name", "inventory-service")
        .defaultCookie("locale", "vi")
        .build();
~~~

Gọi:

~~~java
Product product = catalog.get()
        .uri("/products/{id}", 42)
        .retrieve()
        .body(Product.class);
~~~

`Product` là DTO ứng dụng định nghĩa. Ví dụ `record Product(long id, String name) {}` có thể deserialize JSON body như `{"id":42,"name":"Tea"}` khi Jackson có sẵn.

### 3.3. Tạo từ RestTemplate

Spring có factory method `RestClient.create(RestTemplate)` để hỗ trợ migration:

~~~java
RestTemplate template = new RestTemplate();
RestClient client = RestClient.create(template);
~~~

Khi chuyển ứng dụng cũ, phải kiểm request factory, interceptors, status handlers, serializers và timeout thực tế. Không chỉ thấy code compile là đủ; các default policies có thể khác.

## 4. baseUrl và URI resolution

### 4.1. Một nguồn cấu hình cho host và prefix

~~~java
RestClient api = RestClient.builder()
        .baseUrl("https://catalog.example.test/v1")
        .build();

Product p = api.get()
        .uri("/products/{id}", 42)
        .retrieve()
        .body(Product.class);
~~~

Ý định URL cuối:

~~~text
https://catalog.example.test/v1/products/42
~~~

Cần kiểm bằng MockRestServiceServer để tránh sai path. Không đặt `/v1` cả trong baseUrl và `.uri("/v1/products/{id}")` vì sẽ có prefix lặp.

### 4.2. Absolute URI bypass baseUrl

Theo Spring Framework 6.2 Javadoc, khi request sử dụng absolute URL, cấu hình baseUrl bị bỏ qua:

~~~java
api.get()
        .uri("https://other.example.test/products/42")
        .retrieve()
        .body(String.class);
~~~

Điều này nguy hiểm nếu upstream được chọn từ input người dùng và RestClient có global Authorization/Cookie. Ngăn SSRF/credential leakage bằng allowlist trusted hosts/schemes/ports, redirect policy và client riêng cho mỗi trust boundary.

### 4.3. URI templates tốt hơn nối string

Ưu tiên:

~~~java
api.get()
        .uri("/products/{id}", userVisibleId)
        .retrieve()
        .body(String.class);
~~~

Không nên tự nối input:

~~~java
api.get().uri("/products/" + rawInput);
~~~

Một ID hoặc search term có dấu cách, `?`, `/` hay `#` có thể đổi ý nghĩa của URI. Dùng URI template và query builder để encoding được xử lý có chủ đích.

Ví dụ query:

~~~java
api.get()
        .uri(builder -> builder
                .path("/products")
                .queryParam("q", searchTerm)
                .queryParam("page", 1)
                .build())
        .retrieve()
        .body(String.class);
~~~

Fragment này cần `searchTerm`. Khi cung cấp trực tiếp `URI`, encoding behavior khác với String template; đọc Javadoc và không encode hai lần.

### 4.4. Default URI variables

Nếu nhiều API paths cần biến cố định, chẳng hạn region:

~~~java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test/v1")
        .defaultUriVariables(Map.of("region", "vn"))
        .build();

String body = client.get()
        .uri("/regions/{region}/products/{id}",
                Map.of("id", 42))
        .retrieve()
        .body(String.class);
~~~

Default variables là shortcut của `DefaultUriBuilderFactory`; không cất tenant ID của mỗi user trong globals trên một shared client.

### 4.5. uriBuilderFactory và độ ưu tiên

`uriBuilderFactory(UriBuilderFactory)` thay thế cơ chế tạo URI mặc định. Theo Javadoc Spring 6.2, việc cung cấp factory riêng **ghi đè tác động của shortcut baseUrl/defaultUriVariables**. Nếu đã có custom factory, baseUrl bạn đặt sau/trước có thể không hiệu lực như kỳ vọng. Chỉ dùng custom factory khi có yêu cầu encoding/URI templates thật sự đặc thù.

## 5. defaultHeader và defaultHeaders

### 5.1. Default header đơn

~~~java
RestClient client = RestClient.builder()
        .defaultHeader("X-Client-Name", "inventory-service")
        .build();
~~~

`RestClient.Builder.defaultHeader(String, String...)` chỉ thêm header mặc định **nếu request chưa có header cùng tên**. Vì thế per-call header có thể thay default:

~~~java
client.get()
        .uri("https://catalog.example.test/products/7")
        .header("X-Client-Name", "special-job")
        .retrieve()
        .body(String.class);
~~~

Khi request đã có X-Client-Name, defaultHeader không tự thêm inventory-service. Nếu một header trước đó đã được add nhiều lần, dùng `headers(h -> h.set(...))` để thay thay vì append.

### 5.2. defaultHeaders thao tác cả nhóm

~~~java
RestClient client = RestClient.builder()
        .defaultHeader("X-Client-Name", "inventory-service")
        .defaultHeaders(headers -> {
            headers.set("X-API-Version", "2026-10");
            headers.setAccept(
                    List.of(MediaType.APPLICATION_JSON));
            headers.remove("X-Legacy-Feature");
        })
        .build();
~~~

`defaultHeaders(Consumer<HttpHeaders>)` cho phép:

- `add(name,value)`: thêm value vào danh sách.
- `set(name,value)`: ghi đè danh sách thành một value.
- `remove(name)`: xóa default.
- `setAccept(List.of(...))`: cấu hình Accept đúng kiểu.

**Cẩn thận:** `Authorization` thường chỉ nên có một scheme/token được chọn đúng audience. Không biến `defaultHeader("Authorization",...)` thành chỗ chứa token của một người dùng trong RestClient singleton.

### 5.3. Accept và Content-Type

- `Accept` cho biết response representation bạn muốn nhận, ví dụ JSON.
- `Content-Type` mô tả media type của **request body**, nên không cần ép mặc định application/json cho mọi GET không có body.
- `User-Agent` và `X-Client-Name` hữu ích để upstream phân biệt service nếu được chính sách đồng ý.
- `X-API-Version` dùng khi upstream có hợp đồng versioning qua header.

Nếu API gọi POST JSON, có thể cấu hình per-call:

~~~java
client.post()
        .uri("/products")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateProductRequest("Tea"))
        .retrieve()
        .toBodilessEntity();
~~~

Đây là fragment, CreateProductRequest cần được khai báo. Khi client có base URL, path này sẽ được nối phù hợp. Có thể sử dụng media type convenience methods thay vì tự viết Content-Type string.

### 5.4. Một lỗi request ID phổ biến

Không tạo một request ID rồi gắn global:

~~~java
RestClient client = RestClient.builder()
        .defaultHeader("X-Request-ID",
                UUID.randomUUID().toString())
        .build();
~~~

Mọi requests của client có thể cùng một ID vì giá trị chỉ được sinh khi build. Nếu mỗi call cần ID mới, dùng `defaultRequest`/interceptor hoặc chuẩn tracing propagation.

## 6. defaultCookie và defaultCookies — Spring Framework 6.2

### 6.1. API mới có thật

~~~java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test/v1")
        .defaultCookie("locale", "vi")
        .build();
~~~

`RestClient.Builder.defaultCookie(String, String...)` được thêm **từ Spring Framework 6.2**. Nó cung cấp cookie mặc định cho request **nếu request chưa có cookie cùng tên**. Nếu IDE nói không có method, kiểm `spring-web` effective dependency version thay vì nghĩ RestClient không hỗ trợ cookies.

### 6.2. defaultCookies dùng MultiValueMap

~~~java
RestClient client = RestClient.builder()
        .defaultCookie("locale", "vi")
        .defaultCookies(cookies -> {
            cookies.set("theme", "light");
            cookies.remove("legacy");
        })
        .build();
~~~

`defaultCookies(Consumer<MultiValueMap<String,String>>)` mở map defaults cho phép add, replace hoặc remove cookies. Khi dùng `cookies.set`, danh sách values của cookie cùng key được thay mới.

Để đổi default locale đã khai báo:

~~~java
RestClient client = RestClient.builder()
        .defaultCookie("locale", "en")
        .defaultCookies(cookies ->
                cookies.set("locale", "vi"))
        .build();
~~~

Sau xây dựng, default locale là vi.

### 6.3. Cookie per-call có thể thay default

~~~java
client.get()
        .uri("/products/{id}", 42)
        .cookie("locale", "en")
        .retrieve()
        .body(String.class);
~~~

Spring 6.2 `RequestHeadersSpec.cookie(name,value)` và `cookies(Consumer<MultiValueMap<...>>)` thao tác request cookies. Vì defaultCookie được chèn khi tên chưa tồn tại, request cookie có thể thay default cùng tên.

Ví dụ rõ ý thay:

~~~java
client.get()
        .uri("/products/{id}", 42)
        .cookies(cookies -> cookies.set("locale", "en"))
        .retrieve()
        .body(String.class);
~~~

Khi có nhiều layers thêm Cookie header, kiểm request thực bằng mock để phát hiện duplicate values. Không tự thêm raw `Cookie` header nếu đã dùng cookie API, trừ khi có lý do tương thích đã test.

### 6.4. Request Cookie khác Set-Cookie

- Client outbound gửi **Cookie: locale=vi; theme=light**.
- Server trả **Set-Cookie** để yêu cầu user agent lưu cookie kèm Domain/Path/Expires/Secure/HttpOnly/SameSite.

RestClient.Builder.defaultCookie chỉ thiết lập **request cookie mặc định**; nó **không tự làm browser-like cookie jar** lưu/tự chọn mọi cookie server trả theo session/domain/expiry. Muốn stateful cookie store cần cấu hình HTTP client transport phù hợp và kiểm chính sách.

### 6.5. Bảo mật cookie

Không đặt per-user browser session, refresh token, CSRF token, Authorization cookie trên một RestClient singleton global. User A và B có thể vô tình dùng chung credentials. Không forward raw inbound Cookie header tới host khác. Nếu cần delegated access, thiết kế auth per-request, OAuth/token exchange, host allowlist, TLS và secret redaction.

DefaultCookie phù hợp hơn cho non-sensitive application-level locale/theme và vài service-specific metadata được phép chia sẻ.

## 7. defaultRequest và giá trị động

`defaultRequest(Consumer<RestClient.RequestHeadersSpec<?>>)` giúp tùy chỉnh mọi request được build, ví dụ sinh correlation ID khi request chưa có:

~~~java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test/v1")
        .defaultRequest(spec -> spec.headers(headers -> {
            if (!headers.containsKey("X-Correlation-ID")) {
                headers.set("X-Correlation-ID",
                        UUID.randomUUID().toString());
            }
        }))
        .build();
~~~

Đây là fragment, cần import UUID. Khác `defaultHeader` là có thể tạo UUID **mỗi request**, không một lần duy nhất lúc bootstrap. Nếu app dùng distributed tracing, nên propagate trace IDs đáng tin cậy từ observation context; tránh sinh ID độc lập ở mỗi hop khiến trace bị đứt.

| API | Điểm áp dụng |
| --- | --- |
| `defaultHeader` | Header ổn định, khi request chưa có |
| `defaultHeaders` | Chỉnh toàn bộ default header map lúc cấu hình builder |
| `defaultCookie` | Cookie ổn định, khi request chưa có cùng tên |
| `defaultCookies` | Chỉnh default cookie map |
| `defaultRequest` | Callback thực thi với mỗi request spec |
| `get().header/cookie` | Chỉ một request |
| `requestInterceptor` | Intercept quá trình outbound execution, logging/tracing/policy |

Với dynamic Authorization, cần bảo đảm identity/token được lấy theo mỗi request từ trusted context, chứ không giữ mutable `lastToken` ở singleton. Tách clients theo trust boundary và tránh gửi token nếu URL có thể bị caller thay bằng absolute URL khác host.

## 8. Spring Boot 3.5 và RestClient.Builder injection

Spring Boot auto-configuration tạo `RestClient.Builder` bean có **prototype scope**, nghĩa là mỗi injection point có builder được cấu hình riêng. Boot có thể áp HTTP converters, observation/tracing, HTTP client factories và `RestClientCustomizer`.

Trong ứng dụng Boot:

~~~java
@Configuration(proxyBeanMethods = false)
class CatalogConfig {
    @Bean
    RestClient catalogRestClient(
            RestClient.Builder builder,
            @Value("${catalog.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Client-Name", "inventory-service")
                .defaultCookie("locale", "vi")
                .build();
    }
}
~~~

Dùng Boot-injected builder thay vì tự gọi `RestClient.builder()` tại nhiều chỗ nếu bạn cần giữ những customizers/observation policies của ứng dụng.

### 8.1. RestClientCustomizer

Spring Boot 3.5 có `RestClientCustomizer` callback cấu hình các RestClient builders:

~~~java
@Bean
RestClientCustomizer platformCustomizer() {
    return builder -> builder.defaultHeader(
            "X-Platform", "learning-platform");
}
~~~

Đây là fragment với import `org.springframework.boot.web.client.RestClientCustomizer`. Global customizer chỉ nên thiết lập header chính sách thật sự **chung tất cả upstream**. Đừng set base URL hoặc cookie catalog trên mọi client vì billing/auth destinations có audience khác.

### 8.2. RestClientBuilderConfigurer

Spring Boot còn có `RestClientBuilderConfigurer` để áp Boot defaults lên một builder tự tạo. Nó hữu ích cho extension library hoặc custom builder factory. Ứng dụng thông thường chỉ cần inject builder từ Boot.

### 8.3. Một client cho một upstream

Nếu cần catalog và billing:

~~~java
@Bean
RestClient catalogClient(RestClient.Builder builder) {
    return builder.baseUrl("https://catalog.example.test").build();
}

@Bean
RestClient billingClient(RestClient.Builder builder) {
    return builder.baseUrl("https://billing.example.test").build();
}
~~~

Trên hai beans, dùng `@Qualifier` rõ ràng trong consumers nếu có nhiều RestClient beans. Prototype builder giúp tránh chia sẻ cùng mutable builder giữa hai upstreams.

### 8.4. Properties và secrets

~~~properties
catalog.base-url=https://catalog.example.test/v1
catalog.client-name=inventory-service
catalog.locale=vi
~~~

Config baseURL từ environment/config management. Token/password phải ở secret manager hoặc biến môi trường bảo vệ, không hardcode Git/README. Validate scheme=https và host allowlist trước khi app dùng dynamic endpoint settings.

## 9. Dự án Maven chạy độc lập (Java 17 + Spring Boot 3.5.0)

### 9.1. Cấu trúc

~~~text
restclient-defaults-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/RestClientDefaultsApplication.java
    │   └── resources/application.properties
    └── test/java/demo/CatalogGatewayTest.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>restclient-defaults-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties><java.version>17</java.version></properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dùng `spring-boot-starter-web` vì đây là HTTP client **đồng bộ** trong Boot MVC app; nó kéo vào `spring-web`, Jackson và các dependencies cơ bản. Demo không cần WebFlux starter.

### 9.3. application.properties

~~~properties
catalog.base-url=https://catalog.example.test/v1
catalog.client-name=inventory-service
catalog.locale=vi
~~~

URL `catalog.example.test` là example-only. App không gọi URL lúc startup; unit tests dùng mock HTTP response nên không có network.

### 9.4. RestClientDefaultsApplication.java

Lưu **toàn bộ** Java sau vào `src/main/java/demo/RestClientDefaultsApplication.java`. Chỉ một public top-level class, những class/record còn lại package-private, tương thích Java 17.

~~~java
package demo;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class RestClientDefaultsApplication {
    public static void main(String[] args) {
        SpringApplication.run(
                RestClientDefaultsApplication.class, args);
    }
}

record Product(long id, String name) {
}

@Configuration(proxyBeanMethods = false)
class CatalogConfig {

    @Bean
    RestClient catalogRestClient(
            RestClient.Builder builder,
            @Value("${catalog.base-url}") String baseUrl,
            @Value("${catalog.client-name}") String clientName,
            @Value("${catalog.locale}") String locale) {
        return create(builder, baseUrl, clientName, locale);
    }

    static RestClient create(RestClient.Builder builder,
                             String baseUrl,
                             String clientName,
                             String locale) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Client-Name", clientName)
                .defaultHeaders(headers -> {
                    headers.set("X-API-Version", "2026-10");
                    headers.setAccept(
                            List.of(MediaType.APPLICATION_JSON));
                })
                .defaultCookie("locale", locale)
                .defaultCookies(cookies ->
                        cookies.set("theme", "light"))
                .build();
    }
}

@Service
class CatalogGateway {
    private final RestClient client;

    CatalogGateway(RestClient catalogRestClient) {
        this.client = catalogRestClient;
    }

    Product getProduct(long id) {
        Product response = client.get()
                .uri("/products/{id}", id)
                .retrieve()
                .body(Product.class);
        return Objects.requireNonNull(
                response, "Empty product response");
    }

    Product getProductWithOverrides(long id) {
        Product response = client.get()
                .uri("/products/{id}", id)
                .header("X-Client-Name", "special-job")
                .cookie("locale", "en")
                .retrieve()
                .body(Product.class);
        return Objects.requireNonNull(
                response, "Empty product response");
    }
}
~~~

**Giải thích:**

- Spring Boot quản lý RestClient.Builder bean; code build một reusable RestClient.
- `CatalogConfig.create(...)` cho phép unit test truyền mock builder để không gọi API thật.
- `baseUrl` hợp nhất upstream host và version path.
- Default headers gồm X-Client-Name, X-API-Version và Accept JSON.
- Default cookies gồm `locale=vi` và `theme=light`.
- Method có overrides thay X-Client-Name và locale chỉ trên request đó.
- `Objects.requireNonNull` minh họa xử lý response body rỗng; production nên map thành typed domain exception.
- `Product` là record immutable phù hợp JSON decoding từ Spring/Jackson.
- Không có per-user token hoặc session cookie global.
- App không khai báo inbound controller; nó chạy để minh họa DI và HTTP client bean. Tests là cách chạy demo đầy đủ mà không cần catalog server.

### 9.5. CatalogGatewayTest.java

Lưu `src/test/java/demo/CatalogGatewayTest.java`:

~~~java
package demo;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogGatewayTest {
    private MockRestServiceServer server;
    private CatalogGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // Bind trước RestClient.build().
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = CatalogConfig.create(
                builder,
                "https://catalog.example.test/v1",
                "inventory-service",
                "vi");
        gateway = new CatalogGateway(rest);
    }

    @Test
    void baseUrlHeadersAndCookiesAreApplied() {
        server.expect(requestTo(URI.create(
                        "https://catalog.example.test/v1/products/7")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "X-Client-Name", "inventory-service"))
                .andExpect(header(
                        "X-API-Version", "2026-10"))
                .andExpect(header(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE))
                .andExpect(request -> {
                    String cookie = request.getHeaders().getFirst(
                            HttpHeaders.COOKIE);
                    assertTrue(cookie != null
                            && cookie.contains("locale=vi"));
                    assertTrue(cookie != null
                            && cookie.contains("theme=light"));
                })
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Coffee\"}",
                        MediaType.APPLICATION_JSON));

        Product p = gateway.getProduct(7);
        assertEquals(7L, p.id());
        assertEquals("Coffee", p.name());
        server.verify();
    }

    @Test
    void requestHeaderAndCookieOverrideDefaults() {
        server.expect(requestTo(
                        "https://catalog.example.test/v1/products/8"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Client-Name", "special-job"))
                .andExpect(request -> {
                    String cookie = request.getHeaders().getFirst(
                            HttpHeaders.COOKIE);
                    assertTrue(cookie != null
                            && cookie.contains("locale=en"));
                    assertFalse(cookie != null
                            && cookie.contains("locale=vi"));
                    assertTrue(cookie != null
                            && cookie.contains("theme=light"));
                })
                .andRespond(withSuccess(
                        "{\"id\":8,\"name\":\"Tea\"}",
                        MediaType.APPLICATION_JSON));

        Product p = gateway.getProductWithOverrides(8);
        assertEquals(8L, p.id());
        server.verify();
    }

    @Test
    void http404ThrowsByDefault() {
        server.expect(requestTo(
                        "https://catalog.example.test/v1/products/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(HttpClientErrorException.NotFound.class,
                () -> gateway.getProduct(999));
        server.verify();
    }
}
~~~

### 9.6. Chạy kiểm thử

~~~bash
mvn test
~~~

Tests dùng `MockRestServiceServer.bindTo(RestClient.Builder)` để thay outgoing HTTP request factory **trước khi build RestClient**, tránh DNS/network thật. Chúng xác minh:

1. URI gồm đúng host + `/v1/products/7`.
2. Default client header và API-version.
3. Accept JSON.
4. Cookies được gửi qua HTTP Cookie header.
5. Một request có thể override header/cookie cùng tên.
6. JSON decode thành Java record đúng.
7. `retrieve()` ném typed HttpClientErrorException khi response 404.

Cookie assertions dùng `contains` để không phụ thuộc thứ tự cookie pairs. Nếu sửa config hoặc nâng version làm test fail, đọc request thực và Javadoc version tương ứng trước khi nới test.

Chạy app riêng:

~~~bash
mvn spring-boot:run
~~~

Ứng dụng khởi động Spring Boot MVC runtime, tạo CatalogGateway bean, **không gửi outbound HTTP call** cho tới khi bạn gọi gateway qua service/controller khác. Để gọi upstream thật, cấu hình URL an toàn, TLS/timeout/auth và contract thật. Không đưa token nhạy cảm vào README hay Git.

## 10. Tùy chỉnh nâng cao cho production

### 10.1. Timeout

`baseUrl`, header và cookie defaults **không tự cấu hình timeout**. Underlying HTTP request factory quyết định connect/read/response timeout, có thể khác giữa JDK HttpClient, Apache HttpComponents và các clients khác.

Ví dụ fragment với SimpleClientHttpRequestFactory:

~~~java
SimpleClientHttpRequestFactory factory =
        new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofSeconds(2));
factory.setReadTimeout(Duration.ofSeconds(5));

RestClient client = RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://catalog.example.test/v1")
        .build();
~~~

Phải kiểm Javadoc exact Spring version và transport; Boot có request-factory configuration để giữ observability/SSL/pooling settings.

### 10.2. Error handling

`retrieve()` mặc định ném exceptions với HTTP 4xx/5xx, ví dụ `HttpClientErrorException.NotFound`. Có thể dùng `onStatus` per-request hoặc `defaultStatusHandler` ở builder.

~~~java
RestClient client = RestClient.builder()
        .defaultStatusHandler(
                status -> status.value() == 429,
                (request, response) -> {
                    throw new IllegalStateException(
                            "Upstream rate-limited");
                })
        .build();
~~~

Đây là fragment. Production nên dùng typed domain exception, không trả raw upstream body có thể chứa SQL/secrets/tokens. Khi sử dụng `exchange(...)` cấp thấp, việc xử lý status/body có contract khác với `retrieve()`; đọc Javadoc trước khi thay.

### 10.3. Interceptor

~~~java
ClientHttpRequestInterceptor interceptor =
        (request, body, execution) -> {
            request.getHeaders().set(
                    "X-Platform", "spring-course");
            return execution.execute(request, body);
        };

RestClient client = RestClient.builder()
        .requestInterceptor(interceptor)
        .build();
~~~

Đây là fragment, cần imports. Interceptor gần thời điểm I/O và có thể thay request/response. Không log Authorization, Cookie, Set-Cookie hoặc raw request body. Nếu Spring Boot đã cấu hình Micrometer tracing, tránh tự tạo duplicate spans.

### 10.4. Pooling và thread usage

RestClient bản thân là API abstraction; pooling/keepalive thuộc underlying HTTP client. Reuse client bean, cấu hình max connections/pending acquire/idle evict/response timeouts phù hợp và đo latency. RestClient synchronous giữ caller thread trong suốt HTTP call: gọi 100 remote endpoints song song bằng 100 threads vẫn có resource cost thực.

### 10.5. Retry và idempotency

Không retry mọi `POST /payments` nếu thiếu idempotency key/deduplication. Với GET idempotent và transient failures, có thể dùng resilience library/circuit breaker với bounded retries, backoff, jitter, overall deadline và metrics. Cookie/Authorization của một upstream không được gửi đến redirect destination ngoài allowlist.

## 11. An toàn của header/cookie mặc định

### 11.1. Sai lầm chia sẻ user session

Sai trong multi-user service:

~~~java
@Bean
RestClient client() {
    return RestClient.builder()
            .defaultCookie("session", "user-a-session")
            .build();
}
~~~

Mọi request qua singleton có thể mang credential của user A. Không chuyển nguyên inbound Cookie header sang downstream host khác. Tách service credentials và user-delegated tokens; dùng token exchange/per-request authorization thay vì mutable user session ở bean scope.

### 11.2. Nhầm request cookie với browser cookie store

`defaultCookie("locale","vi")` tạo request Cookie header cho các calls phù hợp. Nó không tự nhận Set-Cookie, lưu expires/domain/path/SameSite và chọn cookies cho hosts như browser; muốn session cookie management thực cần request factory/HTTP client cookie store cụ thể. Không giả định auto-login.

### 11.3. SSRF và absolute URI

Một malicious caller có thể cung cấp `http://169.254.169.254/...` hoặc private-network URL nếu bạn cho phép absolute destination tùy ý. Nếu RestClient có default Authorization/Cookie thì rủi ro tăng. Hạn chế outgoing hosts, vô hiệu/chặn redirects tùy policy, xác thực DNS/IP trước connect theo kiến trúc, không trộn trusted và untrusted destinations.

### 11.4. Redaction/observability

Logs nên có method, route template, upstream service name, HTTP status và latency. Không log raw Authorization/Cookie/Set-Cookie, không đưa raw query strings chứa token vào trace tags, và không dùng requestId/userId làm metric label high-cardinality. Tracing context là nguồn correlation đáng tin hơn tự gắn ID ở mọi lớp.

## 12. Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `defaultCookie` không compile | Spring Framework < 6.2 | Kiểm effective `spring-web` version |
| HTTP URL bỏ baseUrl | Request dùng absolute URL | Kiểm URI cuối bằng mock |
| Path `/v1/v1` | Prefix lặp | Chọn một owner path prefix |
| defaultUriVariables không hoạt động | Custom `uriBuilderFactory` override | Kiểm URI builder setup |
| Header mặc định bị bỏ qua | Request đã có header tên đó | Kiểm per-call specs |
| Header nhiều values ngoài ý muốn | Dùng add thay set | Dùng headers(h -> h.set(...)) |
| Cookie locale mặc định không xuất hiện | Request cookie locale khác | Điều kiện defaultCookie |
| Duplicate Cookie | Thêm raw Cookie header và cookie API | Dùng một nguồn |
| Cookie có nhưng upstream vẫn 401 | Session token invalid hoặc sai audience | Auth protocol riêng |
| User A session sang user B | Global cookie nhạy cảm | Chuyển sang per-request auth |
| Boot observability mất | Raw builder thay Boot-injected builder | Inject RestClient.Builder |
| 404 ném exception | Retrieve defaults | Handle typed error |
| Mock test vẫn gọi Internet | Bind mock vào builder khác/đã build | bindTo(builder) trước build |
| Cookie test sai vì thứ tự fields | Serializing/order khác | Assert contents semantics |
| Requests treo | Chưa đặt timeout underlying HTTP client | Connect/read timeouts |
| Throughput kém | Nhiều blocking calls/không pooling | Reuse client, optimize threads |
| Credential hiện trong logs | Logging raw headers | Redact sensitive headers |

## 13. Bài tập

**Bài 1.** Xây RestClient với baseUrl, default X-Client-Name và locale cookie. GET /products/{id}, decode JSON thành record.

**Bài 2.** Dùng defaultHeaders set Accept JSON, X-API-Version và remove X-Legacy. So sánh add/set/remove.

**Bài 3.** Dùng defaultCookies thiết lập theme=light, thay locale=en thành vi và xóa unused cookie.

**Bài 4.** Ghi đè X-Client-Name và locale chỉ trong một GET, chứng minh request sau vẫn có defaults.

**Bài 5.** Dùng URI template path variable và URI query builder; kiểm ký tự đặc biệt được encode đúng.

**Bài 6.** Kiểm absolute URI bỏ qua baseUrl. Phân tích cách chặn host không được phép.

**Bài 7.** Dùng defaultRequest tạo correlation ID theo request thay vì một UUID tạo lúc build.

**Bài 8.** Khai báo hai RestClient beans dùng Boot prototype builder injection, một cho catalog và một cho billing, không lẫn cookies.

**Bài 9.** Viết MockRestServiceServer tests kiểm URL, method, Accept, header, cookie, JSON decoding, 404 exception.

**Bài 10.** Thử thiếu defaultCookie khi dùng Spring 6.1; nâng Framework 6.2 và phân tích API mới.

**Bài 11.** Cấu hình HTTP timeout, kiểm response chậm và phân biệt network timeout với HTTP 504.

**Bài 12.** Thiết kế safe per-user delegated auth thay vì defaultCookie session trên singleton.

**Bài 13.** Viết interceptor chỉ log status/latency; bảo đảm Cookie/Authorization bị redact.

**Bài 14.** So sánh RestClient và WebClient khi dùng trong ứng dụng WebFlux event loop.

**Bài 15.** Thêm retry có giới hạn cho GET idempotent, giải thích vì sao POST payment khác.

## 14. Checklist

- [ ] Dùng Spring Framework 6.2+ khi gọi `RestClient.Builder.defaultCookie(s)`.
- [ ] Base URL đến từ trusted config và được validate.
- [ ] Header defaults chỉ là application-wide settings, không mang per-user credentials.
- [ ] Cookie defaults an toàn cho tất cả calls qua RestClient shared.
- [ ] URL không nhận arbitrary user-controlled absolute destination.
- [ ] Không chèn path/version prefix hai lần.
- [ ] URI path/query variables dùng builder/template đúng encoding.
- [ ] Per-call overrides được test thực tế.
- [ ] Dùng Boot-injected RestClient.Builder nếu cần Boot customizations.
- [ ] Không mutate builder/client internals concurrent khi app chạy.
- [ ] Có connect/read timeout, TLS, connection pool metrics.
- [ ] 4xx/5xx được map an toàn khi cần.
- [ ] Retries chỉ cho transient/idempotent operations.
- [ ] Không log Cookie/Set-Cookie/Authorization/raw PII.
- [ ] MockRestServiceServer được gắn trước RestClient.build().
- [ ] Không gọi synchronous RestClient trực tiếp trên WebFlux event loop.

## 15. Ghi nhớ

- **RestClient là synchronous**: request chặn caller thread trong lúc chờ HTTP I/O.
- `RestClient.Builder.baseUrl` thiết lập URL cho relative URI; absolute request URI có thể override base.
- `defaultHeader/defaultCookie` chỉ áp khi request chưa có field/cookie cùng tên.
- `defaultHeaders/defaultCookies` cho phép chỉnh map defaults.
- **Spring Framework 6.2 mới có RestClient.Builder.defaultCookie/defaultCookies**; kiểm BOM version.
- `defaultRequest` thích hợp cho custom metadata động theo request.
- Spring Boot 3.5 có auto-configured `RestClient.Builder` prototype; inject builder để giữ customizers/observations.
- Một RestClient shared không được chứa per-user auth/cookie secrets.
- Test outbound behavior bằng `MockRestServiceServer.bindTo(builder)` trước `build()`.
- Base URL và defaults không thay timeout, TLS, authentication hoặc retry policy.

## 16. Tài liệu chính thức

- [Spring Framework 6.2 — REST Clients](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html).
- [RestClient.Builder 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.Builder.html).
- [RestClient.RequestHeadersSpec 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.RequestHeadersSpec.html).
- [Spring Boot 3.5 — Calling REST Services](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html).
- [Spring Boot 3.5 RestClientAutoConfiguration](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/client/RestClientAutoConfiguration.html).
- [Spring Boot 3.5 RestClientCustomizer](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/client/RestClientCustomizer.html).
- [MockRestServiceServer 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html).
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110).
- [RFC 6265 — HTTP Cookies](https://www.rfc-editor.org/rfc/rfc6265).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-003">Gửi yêu cầu với URI template, query, form và multipart</a>

<details>
<summary>Xem chi tiết</summary>

# Gửi yêu cầu với URI template, query, form và multipart bằng HTTP client đồng bộ của Spring

## 1. Mục tiêu bài học

Một ứng dụng Spring thường cần gọi một HTTP API khác để lấy dữ liệu, tra cứu trạng thái, gửi biểu mẫu hoặc tải một tệp cùng metadata. Với HTTP client **đồng bộ**, thread gọi client thường chờ cho tới khi nhận và xử lý xong response hoặc xảy ra timeout/error. Nếu ghép URL và request body không đúng, những lỗi tưởng nhỏ như dấu `+`, dấu `&`, `Content-Type` và tên file có thể khiến server nhận dữ liệu khác ý định.

Bài học thuộc nhóm **HTTP client đồng bộ** trong Spring Framework Web. Các ví dụ chính sử dụng **Java 17+, Spring Framework 6.1+ và Spring Boot 3.2+**, vì `RestClient` được giới thiệu từ Spring Framework 6.1. Để học trong nhánh Spring 6.2, có thể dùng Spring Boot 3.4 hoặc 3.5 tương thích; chọn một phiên bản Boot 3.x cụ thể do dự án quản lý, không ghép thủ công các Spring jars khác version. Phần `RestTemplate` mô tả lựa chọn tương thích với code cũ.

Sau bài học, bạn cần làm được:

- Phân biệt **URI template**, **path variable**, **query parameter**, **form-urlencoded body** và **multipart/form-data body**.
- Dùng `RestClient.get()`/`post()`, `.uri(...)`, `.accept(...)`, `.contentType(...)`, `.body(...)`, `.retrieve()`.
- Truyền URI variables bằng map/arguments, tạo query với `UriBuilder`/`UriComponentsBuilder` thay vì nối chuỗi không kiểm soát.
- Hiểu vì sao `C++`, `a&b`, khoảng trắng, `/` và chuỗi đã percent-encoded cần được encode đúng ngữ cảnh và **không double-encode**.
- Gửi `application/x-www-form-urlencoded` bằng `MultiValueMap<String,String>` để giữ giá trị lặp.
- Gửi `multipart/form-data` bằng `MultiValueMap<String,Object>`, `FileSystemResource` và `HttpEntity<?>` cho từng part có header riêng.
- Hiểu cách message converters chọn representation, tự sinh multipart `boundary` và parse response JSON.
- Xử lý HTTP 4xx/5xx, lỗi mạng, timeout, retry, idempotency và kích thước payload.
- Tự chạy ứng dụng Spring Boot có HTTP endpoint kiểm chứng cả ba trường hợp và test bằng `RestClient`/JUnit.
- Biết khi nào nên tiếp tục dùng `RestTemplate` và khi nào cần client bất đồng bộ `WebClient`.

**Phạm vi:** đây là client gọi HTTP **đồng bộ**. `RestClient` API trông có fluent chaining giống `WebClient`, nhưng **không** trả `Mono`/`Flux` và không tự trở thành non-blocking. Nếu gọi từ Netty event loop trong WebFlux mà chờ I/O, bạn có thể chặn event loop; khi cần throughput I/O cao, chọn WebClient/reactive downstream hoặc tách blocking work sang bounded worker với giới hạn tài nguyên.

## 2. Bản đồ request: dữ liệu ở vị trí nào?

Xem request giả định:

```http
POST /orders/sku-42?region=vn&tag=urgent&tag=gift HTTP/1.1
Host: inventory.example.test
Accept: application/json
Content-Type: application/x-www-form-urlencoded

customer=Linh+An&note=C%2B%2B%20%26%20Java
```

Phân tích:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| URI path | `/orders/sku-42` | Xác định resource; `sku-42` có thể đến từ `{id}` |
| Query string | `region=vn&tag=urgent&tag=gift` | Tham số trong URL, có thể lặp cùng tên |
| `Accept` | `application/json` | Client muốn nhận representation nào |
| `Content-Type` | `application/x-www-form-urlencoded` | Loại **body request** đang gửi |
| Form field | `customer=Linh+An` | Form decoder hiểu `+` là khoảng trắng |
| Encoded form value | `note=C%2B%2B%20%26%20Java` | Literal `C++ & Java` sau decode |
| HTTP method | `POST` | Operation của resource; không thay thế việc xác định body |

Với **multipart** thì body được chia thành nhiều parts có `Content-Disposition`, tên field, optional filename và từng `Content-Type`. Multipart không giống một form-urlencoded string nối bằng `&`.

Các vị trí trên cần **các công cụ encode khác nhau**. Lỗi phổ biến là dùng `URLEncoder` dành cho HTML form để tạo cả URI path hoặc tự thêm `?`/`&` mà quên encode từng component.

## 3. Các client HTTP đồng bộ trong Spring

Theo tài liệu Spring Framework:

| API | Mô hình | Khi nào phù hợp |
| --- | --- | --- |
| `RestClient` | Synchronous, fluent API | Code mới với Spring Framework 6.1+ |
| `RestTemplate` | Synchronous, template methods | Ứng dụng cũ, cần bảo trì/tích hợp |
| HTTP Service Client | Java interface có HTTP exchange annotations; backend có thể dùng RestClient | Muốn HTTP contract declarative |
| `WebClient` | Non-blocking/reactive | I/O reactive, nhiều request đồng thời, streaming bất đồng bộ |
| Java 11+ `HttpClient.send` | Synchronous JDK API không phụ thuộc Spring | Java thuần hoặc cần hiểu HTTP primitives |

Bài tập dùng `RestClient` vì API hiện đại trong dòng Spring 6.x và hỗ trợ converters giống `RestTemplate`. Ở Spring Framework 7.x, `RestTemplate` đã được đánh dấu deprecated, nhưng **không suy tất cả ứng dụng Spring 6.x phải viết lại ngay**; chọn phương án theo version và vòng đời hệ thống thực tế.

### Synchronous khác reactive thế nào?

```text
Thread A: RestClient.get() ------ waiting I/O ------ response DTO
Thread B: WebClient.get() ------ returns Mono -------- subscription/callback

RestClient + Tomcat Servlet app: có thể hợp lý cho outbound HTTP ngắn.
RestClient trong Netty WebFlux event-loop: có thể gây blocking/starvation.
```

RestClient trả DTO khi operation hoàn tất (hoặc ném exception), không phải một Publisher có thể giữ mà chưa gọi. Nếu client timeout, remote server vẫn có thể đang xử lý request; timeout không chứng minh operation chưa commit.

## 4. Khởi tạo RestClient đúng version

Dependencies Maven của ứng dụng Spring Boot 3.2+:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

Đây là **đoạn dependencies** để thêm vào `pom.xml` do Spring Initializr tạo, **không** phải một POM độc lập. `spring-boot-starter-web` cung cấp Spring MVC để dựng local server demo cùng HTTP message converters/Jackson. Thư viện `spring-web` có `RestClient`; không cần thêm WebFlux chỉ để dùng RestClient.

Khởi tạo tối giản:

```java
RestClient client = RestClient.create();
```

Hoặc cấu hình:

```java
RestClient client = RestClient.builder()
    .baseUrl("https://api.example.test")
    .defaultHeader("X-App", "learning")
    .build();
```

Lớp `RestClient` là `org.springframework.web.client.RestClient`. Client sau `build()` có thể được chia sẻ giữa các threads theo tài liệu Spring; **không** vì thế mà biến mọi POJO/request body mutable thành thread-safe. Dự án production nên tạo Bean singleton với base URL/timeouts/error policy được cấu hình thay vì khởi tạo một RestClient mới cho mỗi request.

### Yêu cầu HTTP GET đơn giản

```java
ItemView item = client.get()
    .uri("/items/{id}", "SKU-42")
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .body(ItemView.class);
```

Đây là **snippet** yêu cầu client và record `ItemView`, không phải class độc lập. `.uri("/items/{id}", "SKU-42")` thay placeholder bằng URI variable theo chính sách encoding. `.retrieve().body(ItemView.class)` thực hiện HTTP exchange đồng bộ và dùng message converter để decode JSON; method có thể trả null nếu không có body phù hợp, không nên `item.id()` mà không kiểm null.

## 5. URI template: path variable và query không được trộn

Một URI template có placeholders:

```text
https://api.example.test/items/{id}
https://api.example.test/items/{id}?locale={locale}
https://api.example.test/search?q={keyword}&page={page}
```

Các placeholder **không phải literal path**. Chúng được thay bằng values để Spring có cơ hội encode theo URI component. Ví dụ:

```java
String id = "SKU-42";

ItemView item = client.get()
    .uri("/items/{id}", id)
    .retrieve()
    .body(ItemView.class);
```

Nếu value đến từ người dùng, đừng ghép:

```java
String unsafe = "/items/" + userSuppliedId;
```

Đây là anti-pattern: ID chứa `?`, `#`, `/`, `%` hoặc dấu cách có thể đổi ý nghĩa URI. Dùng template expansion và ID validation ở domain layer.

### Sử dụng nhiều biến với Map

```java
Map<String, Object> params = Map.of(
    "shop", "vn",
    "id", "SKU-42");

ItemView item = client.get()
    .uri("/shops/{shop}/items/{id}", params)
    .retrieve()
    .body(ItemView.class);
```

Đây là snippet với imports `java.util.Map`. Dùng map tránh phụ thuộc vào thứ tự positional arguments khi URI có nhiều placeholders.

**Không mặc định URI variable có slash sẽ được server chấp nhận.** Nếu item ID chứa `a/b`, template encoding có thể biểu diễn nó thành `a%2Fb`; nhiều server/gateway có chính sách chặn encoded slash hoặc decode trước routing. Thiết kế ID path-safe/opaque phù hợp và test qua ingress thật, không “vượt rào” bằng double encoding.

## 6. Query parameters với UriBuilder thay vì nối chuỗi

Trường hợp cần URI như:

```text
GET /search?q=C%2B%2B%20%26%20Java&page=2&tag=java&tag=spring%20web
```

Không nên tự viết:

```java
String url = "/search?q=" + keyword + "&tag=" + tag;
```

Nếu `keyword="C++ & Java"`, literal `+`/`&` có thể bị diễn giải khác bởi decoder/query parser. Dùng URI builder và **URI variables** khi cần strict encoding.

```java
SearchResult result = client.get()
    .uri(b -> b.path("/search")
        .queryParam("q", "{q}")
        .queryParam("page", 2)
        .queryParam("tag", "{tag1}", "{tag2}")
        .build(Map.of(
            "q", "C++ & Java",
            "tag1", "java",
            "tag2", "spring web")))
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .body(SearchResult.class);
```

Đây là snippet với `SearchResult` được ứng dụng định nghĩa. `queryParam("tag", ...)` có thể thêm repeated parameter values, còn `UriBuilder.build(Map)` expand URI variables.

**Mấu chốt encoding:** dùng `DefaultUriBuilderFactory` với mode phù hợp, thường `TEMPLATE_AND_VALUES` (giá trị template variable được encode nghiêm ngặt). Nếu dự án thay custom `UriBuilderFactory` hoặc truyền một `URI` đã build sẵn, encoding rules có thể khác. Khi query chứa `+`, percent escapes hoặc reserved characters, phải test **URI thực gửi lên wire** thay vì suy từ chuỗi Java.

## 7. Cấu hình URI builder encoding rõ ràng

`DefaultUriBuilderFactory` là factory Spring dùng để tạo URI builder. Mặc định từ những bản Spring 5.1 đã đổi theo hướng `TEMPLATE_AND_VALUES`, nhưng cấu hình rõ giúp code ít phụ thuộc các defaults bị sửa ở client.

```java
DefaultUriBuilderFactory factory =
    new DefaultUriBuilderFactory("https://api.example.test");

factory.setEncodingMode(
    DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);

RestClient client = RestClient.builder()
    .uriBuilderFactory(factory)
    .build();
```

Imports:

```java
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.client.RestClient;
```

Đây là snippet trong config/test có classpath Spring. Với `TEMPLATE_AND_VALUES`, template được xử lý và **giá trị biến** được encode như dữ liệu, giúp `C++ & Java` không phá ký tự phân cách. Trong ví dụ app chạy ở dưới, chúng ta dùng đúng cấu hình này.

### Query parameter trống, thiếu và nhiều values

```text
/search                  q không có
/search?q=               q có mặt, value rỗng
/search?q=one&q=two      q lặp hai lần
/search?q=a%2Bb          q literal "a+b" khi decode
```

API cần định nghĩa rõ **thiếu** khác **chuỗi rỗng** và repeated values. Đối với `page`, `-1` convert được thành số nhưng có thể sai domain range; validation là bước riêng của server.

### Optional query values

Nếu query optional, chỉ gọi `queryParamIfPresent(...)` khi có `Optional`, hoặc thêm parameter có điều kiện trong lambda builder. **Không** gửi string `"null"` chỉ vì variable Java có giá trị null; trong Spring, `queryParam("q", null)` còn có thể tạo parameter không có value tùy API. Mọi API cần quy định rõ absent vs empty.

## 8. encode URI: percent encoding và form encoding khác nhau

Có nhiều chuẩn biểu diễn:

| Giá trị đầu vào | Trong URI query variable (ví dụ strict encoding) | Trong form-urlencoded |
| --- | --- | --- |
| `A B` | `A%20B` | `A+B` |
| `C++` | `C%2B%2B` | `C%2B%2B` |
| `a&b` | `a%26b` | `a%26b` |
| `x=y` | `x%3Dy` | `x%3Dy` |
| `a/b` | `a%2Fb` khi là variable strict | `a%2Fb` |
| `100%` | `100%25` | `100%25` |

Bảng minh họa **giá trị** khi encoding nghiêm ngặt, không phải lời hứa mọi HTTP client/version đều chọn byte-for-byte cùng cách mã hóa nếu cấu hình khác. `%20` và `+` có thể cùng đại diện space khi giải mã form, nhưng **trong URI query nhiều parser không coi chúng hoàn toàn tương đương**. Literal dấu `+` rất dễ mất nếu client không encode đúng khi server xử lý query theo form convention.

### Vì sao không dùng URLEncoder cho URI path?

`java.net.URLEncoder` encode theo **application/x-www-form-urlencoded**, nghĩa là **space → +**. Trong path, `+` có thể là literal dấu cộng chứ không phải space. Vì vậy không lấy `URLEncoder.encode(id)` rồi nối vào path. Dùng `UriBuilder`, `UriComponentsBuilder`/URI template expansion đúng ngữ cảnh.

### Không double-encode

Nếu value gốc là `"a/b"`, encoder có thể gửi `a%2Fb`. Nếu bạn truyền vào client **chuỗi đã encode** `"a%2Fb"` như dữ liệu chưa encode, lần encode tiếp có thể tạo `a%252Fb`. Server nhận literal `a%2Fb` sau một lần decode, không phải slash. Quy tắc: **lưu value ở dạng chưa encode ở business layer; encode một lần tại HTTP boundary**. Nếu đã có một `URI` được build/encode đúng, truyền `uri(URI)` và đừng đưa nó lại vào template expander.

## 9. Tạo URI hoàn chỉnh bằng UriComponentsBuilder

Có trường hợp dùng cùng một URI cho RestClient và RestTemplate. `UriComponentsBuilder` giúp build `java.net.URI` hoàn chỉnh:

```java
URI uri = UriComponentsBuilder
    .fromUriString("https://api.example.test")
    .path("/items/{id}")
    .queryParam("q", "{q}")
    .queryParam("page", 2)
    .encode()
    .buildAndExpand(Map.of(
        "id", "SKU-42",
        "q", "C++ & Java"))
    .toUri();

ItemView item = client.get()
    .uri(uri)
    .retrieve()
    .body(ItemView.class);
```

Snippet cần imports `java.net.URI`, `java.util.Map`, `org.springframework.web.util.UriComponentsBuilder`. `encode()` **trước** `buildAndExpand` bảo đảm URI variables được encode nghiêm ngặt theo contract builder; literal template special characters có semantics khác variable values.

**Quan trọng:** `RestClient.uri(URI)` sử dụng URI đã tạo; không encode lại như `uri(String, variables)`. Nếu protocol/API yêu cầu query parameter order cụ thể để ký request, đừng tin unordered Map; xây canonical request theo chuẩn chữ ký của service, bao gồm thứ tự/escaping một cách tường minh.

## 10. GET parameters khác POST form body

Những cặp HTTP request sau **không giống nhau**:

```http
GET /search?name=Linh+An HTTP/1.1
Host: example.test

POST /form HTTP/1.1
Host: example.test
Content-Type: application/x-www-form-urlencoded

name=Linh+An
```

`GET` query nằm trong URI; `POST` form fields nằm trong **body**. Cùng server có thể gom query + form qua `@RequestParam` khi chạy **Spring MVC/Servlet**, nhưng không được vì vậy xây client giả định form-urlencoded là query string.

Form body thường được tạo bằng `MultiValueMap<String,String>`:

```java
MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
form.add("name", "Linh & An");
form.add("tag", "java");
form.add("tag", "C++");
```

`MultiValueMap` chứa nhiều values cho cùng key; map thường `Map<String,String>` không biểu đạt đủ `tag` xuất hiện hai lần. Đây cũng là cách an toàn để tránh nối body string thủ công.

## 11. POST application/x-www-form-urlencoded với RestClient

```java
MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
form.add("name", "Linh & An");
form.add("tag", "java");
form.add("tag", "C++");

FormReply reply = client.post()
    .uri("/form")
    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
    .accept(MediaType.APPLICATION_JSON)
    .body(form)
    .retrieve()
    .body(FormReply.class);
```

Đây là snippet cần client và record `FormReply`. Khi cấu hình message converters chuẩn Spring Web, `FormHttpMessageConverter` biến các fields thành **body form-urlencoded**, percent-encode values, thêm Content-Type phù hợp và gửi POST đồng bộ.

Ví dụ body wire **về mặt ý nghĩa**:

```text
name=Linh+%26+An&tag=java&tag=C%2B%2B
```

Thứ tự keys, charset/encoding và header phụ thuộc converter/config; dùng integration test để bảo đảm contract. Không xây string ghép tay trong business logic.

### Content-Type và Accept khác nhau

- `.contentType(APPLICATION_FORM_URLENCODED)`: client **đang gửi form**.
- `.accept(APPLICATION_JSON)`: client **muốn nhận JSON**.
- `.body(form)`: giá trị cần encode theo form writer.
- `.retrieve().body(FormReply.class)`: đọc JSON response thành record.

Nếu gửi form map mà đặt Content-Type `application/json`, server có thể không nhận được form như mong đợi và trả 400/415. Ngược lại một body JSON DTO không tự biến thành form chỉ nhờ gọi `.post()`.

## 12. Lựa chọn message converter cho form và multipart

Spring HTTP message conversion chọn writer dựa vào **Java body type** và **Content-Type**.

| Body Java | Content-Type chủ định | Converter thường phù hợp |
| --- | --- | --- |
| `MultiValueMap<String,String>` | `application/x-www-form-urlencoded` | `FormHttpMessageConverter` |
| `MultiValueMap<String,Object>` chứa Resource/HttpEntity | `multipart/form-data` | `FormHttpMessageConverter` / `AllEncompassingFormHttpMessageConverter` |
| DTO Java record | `application/json` | `MappingJackson2HttpMessageConverter` |
| `String` | `text/plain` | `StringHttpMessageConverter` |
| `byte[]` | `application/octet-stream` | `ByteArrayHttpMessageConverter` |
| `Resource` | stream binary content tùy converter | `ResourceHttpMessageConverter` |

Spring Framework reference cho phép multipart parts là `Object`, `Resource` hoặc `HttpEntity<?>` có headers riêng. Spring tự chọn part converter theo content và headers.

**Lưu ý:** Khi `MultiValueMap` chứa ít nhất một part **không phải String**, converter có thể suy ra multipart; nếu chỉ chứa String, default thường là form-urlencoded. Để bài dễ hiểu, **đặt rõ `Content-Type` mong muốn**, nhất là khi bạn muốn multipart chỉ có text fields. Đừng giả định `Content-Type` của response giống của request.

### Multipart chỉ chứa text có được không?

Có. Một client có thể gửi multipart với các parts đều là text. Vì đó vẫn là multipart representation, phải đặt `MediaType.MULTIPART_FORM_DATA` tường minh hoặc đảm bảo converter phân loại đúng. Nếu không có file và endpoint chỉ cần vài fields, form-urlencoded thường đơn giản và nhẹ hơn.

## 13. Multipart là gì?

HTTP multipart/form-data bao gồm các parts phân cách bằng **boundary** được quy định trong header.

```http
POST /upload HTTP/1.1
Host: example.test
Content-Type: multipart/form-data; boundary=someUniqueBoundary

--someUniqueBoundary
Content-Disposition: form-data; name="description"

Demo document
--someUniqueBoundary
Content-Disposition: form-data; name="file"; filename="sample.txt"
Content-Type: text/plain

hello
--someUniqueBoundary--
```

Đây là cấu trúc minh họa của HTTP payload, **không nên tự gõ ra** khi dùng Spring RestClient. Spring multipart converter sẽ tạo boundary phù hợp, viết từng phần và đóng dấu cuối.

Một multipart request có thể chứa:
- Text field `description`.
- File `file` từ `Resource`.
- JSON metadata `meta` được encode bằng Jackson.
- Part khác với media type và headers riêng.

Không đồng nhất multipart `Content-Type` tổng thể với `Content-Type` riêng của part. Một request `multipart/form-data` có thể chứa `application/json` ở part `meta`.

## 14. Xây multipart với MultiValueMap<String,Object>

```java
MultiValueMap<String, Object> parts =
    new LinkedMultiValueMap<>();

parts.add("description", "Demo document");
parts.add("file",
    new FileSystemResource(Path.of("sample.txt")));

UploadReply reply = client.post()
    .uri("/upload")
    .contentType(MediaType.MULTIPART_FORM_DATA)
    .accept(MediaType.APPLICATION_JSON)
    .body(parts)
    .retrieve()
    .body(UploadReply.class);
```

Đây là snippet trong method đã có client và `sample.txt` thực sự tồn tại. Imports `org.springframework.core.io.FileSystemResource`, `org.springframework.util.MultiValueMap`, `LinkedMultiValueMap`, `java.nio.file.Path`.

- `"description"` là plain form part.
- `"file"` dùng FileSystemResource; converter có thể lấy filename và đọc nội dung file.
- `contentType(MULTIPART_FORM_DATA)` khai báo toàn request là multipart.
- **Không** tự đặt boundary trong header; converter chịu trách nhiệm.
- Body `parts` được xử lý đồng bộ; implementation request factory có thể buffer hoặc stream tùy cấu hình. Đừng khẳng định mọi client mặc định stream file với memory O(1).

### Tại sao dùng FileSystemResource thay vì byte[]?

`FileSystemResource` cung cấp tên file, đường dẫn và nội dung stream theo Resource API, phù hợp một file đã có trên disk. Nếu bạn dùng `byte[]` thuần, converter không luôn coi đó là **file có filename** như mong muốn. Nếu API đòi filename, sử dụng Resource có `getFilename()` đúng hoặc bao part bằng `HttpEntity<Resource>` với Content-Disposition phù hợp.

## 15. Part JSON và HttpEntity có headers riêng

Một multipart request có thể có metadata JSON:

```java
record Meta(String source) {}

HttpHeaders jsonHeaders = new HttpHeaders();
jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

HttpEntity<Meta> jsonPart =
    new HttpEntity<>(new Meta("draft"), jsonHeaders);

MultiValueMap<String, Object> parts =
    new LinkedMultiValueMap<>();

parts.add("description", "Demo document");
parts.add("file",
    new FileSystemResource(Path.of("sample.txt")));
parts.add("meta", jsonPart);

UploadReply reply = client.post()
    .uri("/upload")
    .contentType(MediaType.MULTIPART_FORM_DATA)
    .body(parts)
    .retrieve()
    .body(UploadReply.class);
```

Đây là **snippet**: `Meta` record/`UploadReply` phải có đúng contract với server. `HttpEntity` là wrapper **part content + part headers**, không phải một HTTP request mới. JSON part có `Content-Type: application/json`, được Jackson converter encode khi dependency tương ứng có mặt. File part vẫn là FileSystemResource.

**Không dùng `HttpEntity` để thay cho top-level request headers.** Top-level RestClient `.contentType(...)`/`.accept(...)` thuộc HTTP request; part HttpEntity headers chỉ áp dụng cho part. Nếu service yêu cầu tên part là `metadata` thay vì `meta`, phải trùng chính xác.

## 16. ByteArrayResource và vấn đề filename

Có trường hợp file nằm trong bộ nhớ (ví dụ report nhỏ được tạo trước đó):

```java
byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

Resource resource = new ByteArrayResource(bytes) {
    @Override
    public String getFilename() {
        return "report.txt";
    }
};

MultiValueMap<String, Object> parts =
    new LinkedMultiValueMap<>();
parts.add("file", resource);
```

Đây là snippet với imports `java.nio.charset.StandardCharsets`, `org.springframework.core.io.ByteArrayResource`, `Resource`.

`ByteArrayResource` mặc định có thể **không có filename**; nhiều server yêu cầu `Content-Disposition: form-data; name="file"; filename="..."` để bind vào file part. Override `getFilename()` khi hợp đồng đòi tên file.

**Memory caution:** `ByteArrayResource` giữ toàn bộ bytes trong heap; không phù hợp upload hàng GB. `InputStreamResource` cũng không phải thay thế vô điều kiện: thường non-repeatable, tên file có thể null và một số converters/request factories cần biết content length hay replay body. Chọn `FileSystemResource` cho file có thật hoặc streaming adapter được kiểm thử khi tải file lớn.

## 17. Form và multipart khác JSON body

Một lỗi thường thấy:

```java
client.post()
    .uri("/upload")
    .contentType(MediaType.APPLICATION_JSON)
    .body(parts);
```

Đây là **anti-pattern** nếu `parts` là `MultiValueMap<String,Object>` intended multipart. JSON converter không tự tạo `Content-Disposition` và boundary cho file.

Phân biệt:

| Cần gửi | Content-Type | Request body Java |
| --- | --- | --- |
| JSON DTO | `application/json` | `new CreateOrder(...)` |
| Form các ô đơn giản | `application/x-www-form-urlencoded` | `MultiValueMap<String,String>` |
| File + text + JSON parts | `multipart/form-data` | `MultiValueMap<String,Object>` |
| Binary đơn lẻ không multipart | `application/octet-stream` | `byte[]`/`Resource` theo writer |
| Query filters | Nằm trong URL | `UriBuilder.queryParam(...)`, không cần body |

Server Spring MVC có thể dùng `@RequestParam` để đọc form fields hoặc `@RequestPart` cho multipart JSON/file parts. Spring WebFlux server lại có form/multipart body APIs riêng. Client RestClient chỉ chịu trách nhiệm phát HTTP payload đúng contract; server framework có thể là Java, Node, Python hoặc hệ thống khác.

## 18. Tổng hợp nhanh RestClient, RestTemplate và JDK HttpClient

### RestClient (Spring 6.1+)

```java
Book book = client.get()
    .uri("/books/{id}", id)
    .retrieve()
    .body(Book.class);
```

### RestTemplate (giữ tương thích)

```java
RestTemplate restTemplate = new RestTemplate();

Book book = restTemplate.getForObject(
    "https://api.example.test/books/{id}",
    Book.class, id);
```

### Java 17 HttpClient (không có Spring converters)

```java
HttpRequest request = HttpRequest.newBuilder(
        URI.create("https://api.example.test/books/42"))
    .GET().build();

HttpResponse<String> response =
    HttpClient.newHttpClient().send(
        request, HttpResponse.BodyHandlers.ofString());
```

Đây là **snippets** với `Book` record/`id` hoặc Java HTTP imports tương ứng. `HttpClient.send` là synchronous; muốn JSON DTO phải tự dùng JSON parser (ví dụ Jackson) và tự xây multipart/form encoding nếu không có Spring converters. Mẫu Java17 runnable không dùng mạng bên ngoài ở phần sau.

## 19. RestTemplate gửi form và multipart thế nào?

RestTemplate vẫn dùng `HttpMessageConverter` trong dòng Spring 6.x.

### Form-urlencoded

```java
MultiValueMap<String, String> form =
    new LinkedMultiValueMap<>();
form.add("name", "Linh");
form.add("tag", "java");
form.add("tag", "C++");

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

HttpEntity<MultiValueMap<String, String>> entity =
    new HttpEntity<>(form, headers);

FormReply reply = restTemplate.postForObject(
    "https://api.example.test/form",
    entity, FormReply.class);
```

### Multipart

```java
MultiValueMap<String, Object> parts =
    new LinkedMultiValueMap<>();
parts.add("description", "Demo document");
parts.add("file",
    new FileSystemResource(Path.of("sample.txt")));

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.MULTIPART_FORM_DATA);

HttpEntity<MultiValueMap<String, Object>> entity =
    new HttpEntity<>(parts, headers);

UploadReply reply = restTemplate.postForObject(
    "https://api.example.test/upload",
    entity, UploadReply.class);
```

Hai đoạn này là **snippets** cần `RestTemplate restTemplate`, response records và imports. RestTemplate dùng request `HttpEntity` ở top-level, còn RestClient tách `.headers(...)` và `.body(...)`. Không mang nguyên `RequestEntity`/`HttpEntity` từ RestTemplate nhét vào RestClient.body rồi mong RestClient hiểu top-level headers giống cũ.

### So sánh API

| Công việc | RestTemplate | RestClient |
| --- | --- | --- |
| GET URI template | `getForObject(url,Dto.class,vars)` | `get().uri(url,vars).retrieve().body(Dto.class)` |
| GET ResponseEntity | `getForEntity(...)` | `retrieve().toEntity(Dto.class)` |
| POST form/multipart | `postForObject(url,HttpEntity,Dto.class)` | `post().contentType(...).body(map).retrieve().body(Dto.class)` |
| Set request headers | `HttpEntity`/`RequestEntity` | `header/headers/accept/contentType` |
| Customize per response status | `ResponseErrorHandler` | `onStatus(...)` |
| Underlying HTTP transport | `ClientHttpRequestFactory` | `ClientHttpRequestFactory` |
| Async response publisher | Không | Không |

**Không trộn các object types** của MVC server (`MultipartFile` đại diện upload đã nhận) với client upload file (`Resource`). `MultipartFile` có thể xuất hiện trong HTTP service interface client API, nhưng với RestClient manual multipart body, dùng Resource/HttpEntity rõ ràng.

## 20. Xử lý HTTP response, lỗi và null body

RestClient `retrieve()` cung cấp response metadata/body:

```java
ResponseEntity<ItemView> result = client.get()
    .uri("/items/{id}", "SKU-42")
    .retrieve()
    .toEntity(ItemView.class);

int status = result.getStatusCode().value();
ItemView body = result.getBody();
```

Đây là snippet. `getStatusCode()` là `HttpStatusCode`, không giả định luôn là enum `HttpStatus`. `getBody()` có thể null cho response không body.

Theo mặc định `retrieve()` có thể ném `RestClientResponseException`/subclasses khi server trả 4xx hoặc 5xx; các lỗi khác do transport/converter cũng có thể là `RestClientException`. Đừng nghĩ mọi exception đều có HTTP status: **connect timeout, DNS failure, TLS handshake** không có HTTP response từ server để đọc.

### Custom status handling

```java
ItemView item = client.get()
    .uri("/items/{id}", "SKU-42")
    .retrieve()
    .onStatus(
        status -> status.value() == 404,
        (request, response) -> {
            throw new IllegalStateException(
                "Remote item not found");
        })
    .body(ItemView.class);
```

Đây là snippet cho một cách tùy chỉnh, không phải policy duy nhất. Đừng nuốt mọi 404 thành `null` rồi coi là thành công nếu API phải phân biệt not-found với upstream outage. Với 401/403, phải kiểm credentials và permission; với 429/503, có thể cần backoff; với lỗi format, inspect body có giới hạn và sanitize trước khi log.

### Timeout và status không giống nhau

HTTP 504 do gateway trả là một **response** (có status). Connect/read timeout ở client thường là **exception không có HTTP response**. Cùng một request POST có thể đã được remote xử lý dù client gặp timeout lúc chờ reply.

## 21. Timeout và ClientHttpRequestFactory

Đối với client đồng bộ, bắt buộc suy nghĩ về timeout và connection pool. Một ví dụ với Spring standard request factory:

```java
SimpleClientHttpRequestFactory requestFactory =
    new SimpleClientHttpRequestFactory();

requestFactory.setConnectTimeout(3000);
requestFactory.setReadTimeout(5000);

RestClient client = RestClient.builder()
    .requestFactory(requestFactory)
    .baseUrl("https://api.example.test")
    .build();
```

Snippet cần import `org.springframework.http.client.SimpleClientHttpRequestFactory`. Hai mốc ở đây là **milliseconds** với Spring 6.x API. Connect timeout giới hạn bước kết nối, read timeout là thời gian chờ hoạt động đọc theo transport; **không phải một deadline tổng cho mọi thao tác DNS/TLS/upload/retry/response**.

Production có thể dùng Apache HttpComponents/Jetty/JDK client request factory tùy dependencies, connection pooling và proxy/TLS needs. Khi đổi transport, phải đọc cách cấu hình pool/connection-request timeout riêng của thư viện đó. Không chỉ tăng timeout lên 10 phút để che một upstream chậm.

### Hạn mức tải đồng thời

Nếu 200 server request threads cùng gọi RestClient rồi đợi 5 giây, cả 200 có thể bị chiếm. Cần:
- Đo p95/p99 outbound latency.
- Pool connection có giới hạn; tránh tạo client/connection pool per request.
- Bulkhead/concurrency quota với downstream dễ quá tải.
- Timeout, retry với backoff/jitter và circuit breaker tùy nghiệp vụ.
- Hạn mức upload file, response size và streaming readers.
- Quan sát tổng deadline từ caller/gateway tới các downstream.

Đừng tạo retry storm bằng cách retry đồng loạt một endpoint 503 đang bão hòa.

## 22. Retry, idempotency và bảo vệ dữ liệu

URI/query GET thường an toàn để retry theo HTTP semantics nếu endpoint thực hiện GET đúng (không mutate server state). POST gửi form/multipart có thể tạo đơn hàng, upload hoặc thanh toán, vì vậy **retry không được bật mù quáng**.

Các tình huống:

| Tình huống | Có thể retry? | Ghi chú |
| --- | --- | --- |
| Connect failure trước khi gửi HTTP | Có thể, nếu đánh giá an toàn | Vẫn cần kiểm request thực sự chưa tới server |
| GET timeout | Thường được cân nhắc | Chỉ nếu GET không có side effect quan trọng |
| POST timeout sau upload bytes | **Nguy hiểm** | Server có thể đã lưu file/commit |
| POST 409 conflict | Không tự retry | Cần domain conflict resolution |
| 429/503 | Theo Retry-After/backoff nếu phù hợp | Rate limit và server capacity |
| Multipart từ non-repeatable InputStreamResource | Retry có thể không khả thi | Body có thể đã tiêu thụ stream |
| Multipart từ FileSystemResource | Có thể mở lại file, nhưng operation vẫn có side effect | Dùng idempotency key nếu cần |

**Idempotency key** phải được server hỗ trợ, giữ kết quả bền vững và gắn với actor/request semantics. Một header tùy ý không tự ngăn duplicate uploads nếu server bỏ qua. Với upload lớn, cân nhắc resumable upload protocol hoặc presigned URLs thay vì POST multipart đồng bộ kéo dài.

### Data security

- Không log nguyên Authorization, Cookie, API keys, form passwords hoặc binary multipart content.
- Không trả raw response error chứa PII/token lên client.
- Không để client chỉ định URL bất kỳ nếu backend sẽ gọi RestClient theo URL đó (SSRF).
- Chặn redirects bất ngờ tới internal network tùy request factory/policy.
- Pin base host, allowlist scheme/host/port và không trộn URI raw do user gửi với trusted base URL.
- Kiểm TLS certificates/hostname verification; không tắt SSL verification để “sửa” lỗi.
- Validate file filename/path và kích thước trước khi gửi; filename nhận lại từ remote vẫn là untrusted metadata.
- Không gửi credentials sang host khác qua redirect không kiểm soát.
- Tôn trọng rate limits và authorization.

## 23. Bài tập runnable Spring Boot 3.2+ (server + RestClient)

Để kiểm chứng end-to-end, tạo **một ứng dụng Spring MVC nhỏ đóng vai server nhận request** và một bài integration test dùng `RestClient` làm **client đồng bộ** gửi request tới localhost. Cách này không phụ thuộc API công cộng, không cần token, không cần mock cả luồng multipart.

Yêu cầu Maven/Gradle:

- Java 17+.
- Spring Boot **3.2+** trong dòng 3.x.
- `spring-boot-starter-web`.
- `spring-boot-starter-test` (test scope).

Từ Spring Initializr có Maven wrapper, tạo các tệp:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  DemoController.java
src/test/java/com/example/demo/
  RestClientIntegrationTest.java
```

Cả ba file cùng package `com.example.demo`. Demo không cần database hoặc Spring Security; chỉ phản hồi lại giá trị đã parse để quan sát URI/query/form/multipart.

### 23.1. DemoApplication.java

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 23.2. DemoController.java

```java
package com.example.demo;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/demo")
public class DemoController {

    @GetMapping(path = "/items/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ItemReply item(
            @PathVariable String id,
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "tag", required = false)
                    List<String> tags) {

        return new ItemReply(id, q, page,
                tags == null ? List.of() : List.copyOf(tags));
    }

    @PostMapping(path = "/form",
                 consumes =
                   MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public FormReply form(
            @RequestParam String name,
            @RequestParam("tag") List<String> tags) {

        return new FormReply(name, List.copyOf(tags));
    }

    @PostMapping(path = "/upload",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadReply upload(
            @RequestParam String description,
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") Meta meta) throws IOException {

        // Chỉ đọc metadata và độ dài, KHÔNG lưu file demo.
        return new UploadReply(
            description,
            file.getOriginalFilename(),
            file.getSize(),
            meta.source());
    }

    public record ItemReply(
        String id, String q, int page, List<String> tags) {}

    public record FormReply(
        String name, List<String> tags) {}

    public record Meta(String source) {}

    public record UploadReply(
        String description, String filename,
        long size, String source) {}
}
```

**Giải thích**: MVC controller nhận `@RequestParam` từ query (GET) hoặc body form-urlencoded (POST). `@RequestPart("file") MultipartFile` đại diện file nhận ở server; client gửi `FileSystemResource`. `@RequestPart("meta") Meta` decode JSON part bằng Jackson nhờ `Content-Type: application/json` riêng part.

Code nhận size từ MultipartFile, không lưu file. Demo sử dụng `throws IOException` dù method hiện không đọc file stream: có thể bỏ declaration nếu không cần; Java cho phép checked exception declaration dư. Trong production cần cấu hình file size limit, filename validation, virus scanning, storage permission và stream processing đúng.

## 24. RestClientIntegrationTest: URI template, query, form và multipart

Tệp `src/test/java/com/example/demo/RestClientIntegrationTest.java`:

```java
package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestClientIntegrationTest {

    @LocalServerPort
    int port;

    private RestClient client() {
        DefaultUriBuilderFactory factory =
            new DefaultUriBuilderFactory(
                "http://localhost:" + port);

        factory.setEncodingMode(
            DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);

        return RestClient.builder()
            .uriBuilderFactory(factory)
            .build();
    }

    @Test
    void uriTemplateAndRepeatedQueryValues() {
        DemoController.ItemReply result = client().get()
            .uri(builder -> builder
                .path("/demo/items/{id}")
                .queryParam("q", "{q}")
                .queryParam("page", 2)
                .queryParam("tag", "{tag1}", "{tag2}")
                .build(Map.of(
                    "id", "SKU-42",
                    "q", "C++ & Java",
                    "tag1", "java",
                    "tag2", "spring web")))
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(DemoController.ItemReply.class);

        assertNotNull(result);
        assertEquals("SKU-42", result.id());
        assertEquals("C++ & Java", result.q());
        assertEquals(2, result.page());
        assertEquals(
            java.util.List.of("java", "spring web"),
            result.tags());
    }

    @Test
    void postsFormUrlencoded() {
        MultiValueMap<String, String> form =
            new LinkedMultiValueMap<>();
        form.add("name", "Linh & An");
        form.add("tag", "java");
        form.add("tag", "C++");

        DemoController.FormReply reply = client().post()
            .uri("/demo/form")
            .contentType(
                MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(form)
            .retrieve()
            .body(DemoController.FormReply.class);

        assertNotNull(reply);
        assertEquals("Linh & An", reply.name());
        assertEquals(
            java.util.List.of("java", "C++"),
            reply.tags());
    }

    @Test
    void postsMultipartWithTextFileAndJsonPart(
            @TempDir Path tempDir) throws IOException {

        Path file = tempDir.resolve("sample.txt");
        Files.writeString(
            file, "hello", StandardCharsets.UTF_8);

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(
            MediaType.APPLICATION_JSON);

        HttpEntity<DemoController.Meta> metadata =
            new HttpEntity<>(
                new DemoController.Meta("draft"),
                jsonHeaders);

        MultiValueMap<String, Object> parts =
            new LinkedMultiValueMap<>();
        parts.add("description", "Demo document");
        parts.add("file", new FileSystemResource(file));
        parts.add("meta", metadata);

        DemoController.UploadReply reply = client().post()
            .uri("/demo/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .body(parts)
            .retrieve()
            .body(DemoController.UploadReply.class);

        assertNotNull(reply);
        assertEquals("Demo document",
                     reply.description());
        assertEquals("sample.txt", reply.filename());
        assertEquals(5L, reply.size());
        assertEquals("draft", reply.source());
    }
}
```

**Đây là Java test hoàn chỉnh dành cho project Boot 3.2+ đúng dependencies**, chưa phải kết quả test được chạy trong repository của người dùng. Test không dùng mock request builder mà chạy local HTTP server thực trên RANDOM_PORT, nhờ đó kiểm đồng thời URL encoding, form converter, multipart boundary/part headers và server parsing.

`DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES` là lựa chọn có chủ đích để `C++ & Java` và `spring web` được encode như **variable values**. Test kiểm dữ liệu được decode lại ở server, không dựa vào cách format URL theo thói quen.

### Chạy tests

macOS/Linux:

```bash
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

Nếu chạy app trước:

```bash
./mvnw spring-boot:run
```

Mặc định server khi chạy độc lập ở `http://localhost:8080` nếu không đổi port. Tests dùng **RANDOM_PORT**, vì vậy không nên hardcode :8080 trong test.

### Kết quả mong đợi

| Test | Status và nội dung |
| --- | --- |
| `uriTemplateAndRepeatedQueryValues` | 200, ID SKU-42, q literal `C++ & Java`, page 2, 2 tags |
| `postsFormUrlencoded` | 200, name `Linh & An`, tags `java`, `C++` |
| `postsMultipartWithTextFileAndJsonPart` | 200, filename sample.txt, file size 5, source draft |

Các code tests assert domain data, không assert thứ tự JSON properties, multipart boundary tự sinh hoặc header casing.

## 25. Curl đối chiếu cùng các requests

Sau khi chạy `DemoApplication` trên port 8080, có thể đối chiếu request HTTP bằng curl. Nếu base port bị đổi, thay giá trị URI.

### GET URI/query

```bash
curl -i --get 'http://localhost:8080/demo/items/SKU-42' \
  --data-urlencode 'q=C++ & Java' \
  --data-urlencode 'page=2' \
  --data-urlencode 'tag=java' \
  --data-urlencode 'tag=spring web' \
  -H 'Accept: application/json'
```

`curl --get --data-urlencode` xây query, tránh bạn tự encode dấu `+`/`&`. Response JSON minh họa:

```json
{
  "id": "SKU-42",
  "q": "C++ & Java",
  "page": 2,
  "tags": ["java", "spring web"]
}
```

### Form-urlencoded

```bash
curl -i -X POST 'http://localhost:8080/demo/form' \
  --data-urlencode 'name=Linh & An' \
  --data-urlencode 'tag=java' \
  --data-urlencode 'tag=C++' \
  -H 'Accept: application/json'
```

`curl --data-urlencode` gửi form body, kèm Content-Type form phù hợp. Response:

```json
{"name":"Linh & An","tags":["java","C++"]}
```

### Multipart (file + JSON part)

Tạo `sample.txt` chứa năm bytes `hello` không có newline, rồi chạy:

```bash
curl -i -X POST 'http://localhost:8080/demo/upload' \
  -F 'description=Demo document' \
  -F 'file=@sample.txt;type=text/plain' \
  -F 'meta={"source":"draft"};type=application/json' \
  -H 'Accept: application/json'
```

Dự kiến:

```json
{
  "description": "Demo document",
  "filename": "sample.txt",
  "size": 5,
  "source": "draft"
}
```

**Mấu chốt:** `curl -F` và Spring multipart converter đều tự quản lý boundary. Không tự thêm `Content-Type: multipart/form-data` không có boundary khi dùng curl hoặc Spring; điều đó dễ làm server không parse được parts.

## 26. Test request generation không cần network thật

Khi chỉ muốn xác nhận request method/URI/headers/body converter, Spring Test cung cấp `MockRestServiceServer` để bind với `RestClient.Builder`.

Ví dụ test GET đơn giản:

```java
RestClient.Builder builder = RestClient.builder()
    .baseUrl("https://api.example.test");

MockRestServiceServer server =
    MockRestServiceServer.bindTo(builder).build();

RestClient client = builder.build();

server.expect(requestTo(
        "https://api.example.test/items/SKU-42"))
    .andExpect(method(HttpMethod.GET))
    .andRespond(withSuccess(
        "{\"id\":\"SKU-42\"}",
        MediaType.APPLICATION_JSON));

ItemView response = client.get()
    .uri("/items/{id}", "SKU-42")
    .retrieve()
    .body(ItemView.class);

server.verify();
```

Đây là **snippet trong JUnit test method**, cần `ItemView`, `HttpMethod`, `MediaType` và static imports:

```java
import static org.springframework.test.web.client.match
    .MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match
    .MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response
    .MockRestResponseCreators.withSuccess;
```

**Chú ý:** đoạn imports ở trên được bẻ dòng bằng Java syntax không hợp lệ nếu copy nguyên bản; hãy viết trên một dòng đầy đủ như `import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;` trong file thực. Các dòng trình bày nhằm minh họa namespace, không phải file runnable.

`MockRestServiceServer` hữu ích kiểm client logic không phụ thuộc network, nhưng **không thay thế** HTTP integration test với server thật cho multipart parsing, filename, charset, proxy, TLS, redirects và large upload. Test cần biết form-urlencoded request body là chuỗi bytes, còn multipart body có dynamic boundary; không hardcode boundary literal để assert.

## 27. Java 17 độc lập: form encoding và URI khác nhau

Đây là **một file JDK 17 có thể biên dịch/chạy mà không có Spring dependencies**, giúp thấy rõ `URLEncoder` tạo form-urlencoded representation.

Tệp `FormEncodingDemo.java`:

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FormEncodingDemo {

    private static String encode(String value) {
        return URLEncoder.encode(
            value, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        String name = "Linh & An";
        String tag = "C++";

        String body = "name=" + encode(name)
            + "&tag=" + encode(tag);

        System.out.println(body);
        System.out.println(
            "Do not use form encoding for URI path");
    }
}
```

Chạy:

```bash
javac FormEncodingDemo.java
java FormEncodingDemo
```

Stdout **chính xác**:

```text
name=Linh+%26+An&tag=C%2B%2B
Do not use form encoding for URI path
```

Đây là mô hình giáo dục cho form encoding; khi dùng Spring RestClient hãy đưa `MultiValueMap` cho converter để không phải tự nối fields. Chương trình thuần JDK này không gửi request: ví dụ gọi HTTP sync thực đã có ở integration test của Spring Boot.

### Vì sao import URLEncoder nhưng không dùng cho path?

Trong form body, space được encode thành `+` theo form convention. URI path cần xử lý slash/path segment riêng; nếu dùng `URLEncoder` trực tiếp, giá trị có khoảng trắng có thể không được server hiểu như bạn muốn. Spring URI template builder có cơ chế đúng cho path/query variables.

## 28. Tình huống nâng cao: upload metadata và filename

Server đôi khi yêu cầu multipart structure:

```http
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=...

--...
Content-Disposition: form-data; name="meta"
Content-Type: application/json

{"source":"draft"}
--...
Content-Disposition: form-data; name="file"; filename="report.txt"
Content-Type: text/plain

hello
--...--
```

Có một số chi tiết cần xác minh ở API contract:

1. Tên part phải đúng: `file` khác `upload`.
2. Text field và JSON part khác nhau: `description` nhận String, `meta` nhận DTO.
3. `Content-Type` từng part cần đúng để server decoder chọn converter.
4. Filename do FileSystemResource/Resource#getFilename cung cấp; với in-memory resource có thể cần override.
5. Boundary của request không được hardcode cùng value ở nhiều client.
6. File bytes có thể chứa mọi ký tự nhị phân, không chuyển qua `String`/UTF-8 rồi tự ghép boundary.
7. Không dùng raw path user nhập làm file system path trên client nếu không qua allowlist/authorization.
8. Nếu file có size lớn, kiểm HTTP transport có thể streaming và policies về retry/body repeatability.
9. Một phần thành công rồi timeout không đồng nghĩa remote đã rollback file.
10. multipart/mixed và multipart/form-data không thay được cho nhau nếu server contract phân biệt.

### Chọn Resource

| Resource | Nguồn | Cảnh báo |
| --- | --- | --- |
| `FileSystemResource` | File trên disk | File có thể đổi giữa lúc kiểm và gửi; kiểm permission/size |
| `ByteArrayResource` | byte[] in memory | Không có filename mặc định, giữ toàn bộ bytes |
| `InputStreamResource` | InputStream đã mở | Non-repeatable; có thể không có filename/size và gây khó khi retry |
| `UrlResource` | Remote URL | Không dùng URL user nhập tùy tiện; SSRF và fetch ngoài ý muốn |
| `ClassPathResource` | Resource trong classpath | Phù hợp fixture nhỏ; filename/stream metadata tùy resource |

Trên production, bảo vệ thời điểm mở tệp, chủ sở hữu dữ liệu, giới hạn kích thước và tạo file tạm trong thư mục an toàn. Đừng giữ một `InputStream` mở qua nhiều requests hoặc threads mà không có lifecycle.

## 29. HTTP status khi request sai

Các lỗi thường gặp:

| Status/exception | Vì sao xuất hiện |
| --- | --- |
| 400 Bad Request | Thiếu form field, type conversion sai, malformed multipart |
| 401 Unauthorized | Thiếu/không hợp lệ authentication |
| 403 Forbidden | Có identity nhưng không đủ quyền |
| 404 Not Found | URL/path variable không map resource |
| 405 Method Not Allowed | Gửi POST tới route GET hoặc ngược lại |
| 406 Not Acceptable | `Accept` không tương thích representation |
| 409 Conflict | Operation đụng state/resource version |
| 413 Content Too Large | Payload/upload vượt limit |
| 415 Unsupported Media Type | Gửi body/part Content-Type không được hỗ trợ |
| 422 Unprocessable Content | Body syntactically đúng nhưng domain validation không chấp nhận, nếu API chọn status này |
| 429 Too Many Requests | Rate/quota limit |
| 500/502/503/504 | Server, gateway hoặc upstream lỗi/tạm quá tải |
| `ResourceAccessException` | I/O/connect/read timeout, DNS/TLS... ở RestClient |
| `RestClientResponseException` | HTTP response lỗi và RestClient chọn throw |

Không phải mọi hệ thống dùng đúng status giống nhau; verify dựa trên server API contract và tests. Mã lỗi 4xx là **response từ server**, còn `ResourceAccessException` có thể xảy ra khi chưa nhận HTTP response nào.

### Tại sao lỗi media type hay gặp?

- Gửi multipart nhưng quên boundary.
- Đặt Content-Type application/json cho form map.
- Gửi text/plain body vào server chỉ nhận JSON.
- Dùng JSON part nhưng không đặt part-level Content-Type.
- Truyền String body JSON có ký tự escape sai, trong khi đáng lẽ gửi DTO và để Jackson encode.
- Cho rằng `Accept` dùng để xác định loại request body.

## 30. Lỗi phổ biến khi viết client

1. **Dùng Spring Boot 3.0/3.1 nhưng gọi RestClient**: API ra mắt từ Spring Framework 6.1 (Boot 3.2+).
2. **Import `WebClient` nhưng mong đồng bộ**: WebClient là reactive, không thay RestClient một cách máy móc.
3. **Nối query bằng string interpolation**: `+`/`&`/`#` phá URI.
4. **Dùng URLEncoder cho path segment**: encoding form khác URI path.
5. **Double encode input đã có `%2F`**: server nhận sai value.
6. **Dùng default/custom URI factory mà không test encoded special chars**.
7. **Nhầm `.uri(URI)` với `.uri(String, vars)`**: cái trước là URI đã build.
8. **Đưa query vào body form hoặc form fields vào query mà không theo API contract**.
9. **Dùng Map<String,String> cho repeated query/form fields**: mất nhiều values.
10. **Không phân biệt absent param và empty string**: default/validation bị sai.
11. **Đặt Content-Type trong client theo format response**: nhầm `Accept`.
12. **Gửi MultiValueMap<String,Object> multipart nhưng set application/json**.
13. **Tự sinh multipart boundary mà không đồng bộ header/body**.
14. **Tự ghép multipart byte stream qua String**: hỏng binary file.
15. **Dùng ByteArrayResource không filename khi server bắt buộc filename**.
16. **Dùng InputStreamResource non-repeatable rồi retry POST**.
17. **Không set part JSON Content-Type khiến server parse sai DTO**.
18. **Tạo HTTP client/request factory cho mỗi server request**: connection pool overhead.
19. **Không có connect/read timeout**: threads chờ lâu vô hạn.
20. **Retry POST/multipart sau timeout mà không idempotency**: trùng upload/thanh toán.
21. **Gọi RestClient trực tiếp trên Netty event loop**: blocking.
22. **Tin `retrieve().body(...)` luôn trả non-null**: no body/204 có thể null.
23. **Catch mọi RestClientException rồi trả HTTP 200**: giấu outage.
24. **Log nguyên Authorization/form passwords/file bytes**: lộ secrets.
25. **Để user chọn absolute URL tùy ý**: SSRF.
26. **Dựa vào header Location/redirect từ remote không allowlist**: có thể leak credentials.
27. **Cho rằng POST form là JSON vì request object là POJO**: cần đúng converter.
28. **Không kiểm repeated field order** khi server business contract phụ thuộc.
29. **Dùng MultipartFile làm client upload Resource**: nhầm server và client APIs.
30. **Test chỉ với mock HTTP, bỏ qua server multipart parser**.
31. **Hardcode dynamic boundary trong assertions**: tests flaky.
32. **Không validate filenames/kích thước trước upload**.
33. **Phụ thuộc file tồn tại theo relative path của IDE**: tests khác môi trường fail.
34. **Tin RestTemplate đã biến mất trong Spring 6.2**: còn API, migrations phụ thuộc version.
35. **Tin RestClient gửi đi trên thread nền tự động**: client synchronous chặn caller.
36. **Gọi POST form-urlencoded nhưng thiếu Content-Type**: converter/server có thể hiểu sai.
37. **Dùng raw URI query chứa `+` không encode**: server có thể đổi thành space.
38. **Không kiểm charset/Unicode của form field**: tiếng Việt có thể lỗi encoding.
39. **Không tách 400 validation và 415 media mismatch**: client khó sửa request.
40. **Đọc file upload vào byte[] khổng lồ**: memory pressure.

## 31. Checklist triển khai production

### URI và query

- [ ] Dùng template variables/UriBuilder thay vì nối URL raw?
- [ ] URI variable chứa `&`, `+`, khoảng trắng, slash và Unicode được test?
- [ ] Không truyền dữ liệu đã encode vào formatter encode lần nữa?
- [ ] DefaultUriBuilderFactory/config encoding mode đã được ghi rõ nếu cần?
- [ ] Repeated query params và thiếu/rỗng được xử lý theo API contract?
- [ ] Base URL/scheme/host/port được allowlist; không SSRF từ user input?
- [ ] Không đưa secrets vào query URL/logs nếu có lựa chọn khác?

### Body và converters

- [ ] `Content-Type` biểu thị đúng request body?
- [ ] `Accept` biểu thị đúng representation response mong muốn?
- [ ] `MultiValueMap<String,String>` cho form-urlencoded có repeated values?
- [ ] `MultiValueMap<String,Object>` và Resource/HttpEntity cho multipart?
- [ ] File parts có đúng filename, size, media type và tên part?
- [ ] JSON parts có part-level `Content-Type`?
- [ ] Không tự quản lý multipart boundary?
- [ ] Multipart large files có size quota và streaming/repeatability policy?
- [ ] Charset/Unicode/form encoding được test?

### Độ tin cậy

- [ ] Connect/read timeout có giá trị rõ ràng và đo thực tế?
- [ ] Client/request factory/connection pools dùng lại có kiểm soát?
- [ ] Retry có phân biệt GET/POST và idempotency keys?
- [ ] Phân loại HTTP error vs transport error?
- [ ] Response DTO có nullable/no-body semantics rõ?
- [ ] Không block reactive event-loop nếu ứng dụng là WebFlux?
- [ ] 429/503 có backoff/jitter và circuit-breaker/bulkhead policy?
- [ ] Không để client tự follow redirects không an toàn?
- [ ] Không log secrets/file contents/PII?

### Tests và vận hành

- [ ] Integration test URI template + special characters + repeated query?
- [ ] Form-urlencoded test có dấu cộng/ampersand/Unicode?
- [ ] Multipart test gửi FileSystemResource + JSON HttpEntity part?
- [ ] Test 400/415/413 và error body đủ an toàn?
- [ ] Test timeout, connection reset, invalid TLS và rate-limit trong staging?
- [ ] Test qua gateway/proxy thật nếu encoding/redirect có khả năng bị thay đổi?
- [ ] File upload/response size limits và metrics theo downstream được bật?
- [ ] JUnit không phụ thuộc cổng cố định hoặc file nằm ngoài temp dir?

## 32. Câu hỏi tự kiểm tra

**1.** RestClient có phải asynchronous HTTP client không?

**Đáp án:** Không. RestClient đồng bộ, chặn thread gọi tới khi có response/error. WebClient mới là reactive/non-blocking.

**2.** Tại sao `"/items/" + id` kém an toàn hơn `.uri("/items/{id}", id)`?

**Đáp án:** URI template expansion giúp encode value theo ngữ cảnh, còn nối raw có thể biến characters thành path/query delimiters.

**3.** Khi query cần value `C++ & Java`, nên làm gì?

**Đáp án:** Dùng URI builder với template variables và encoding mode phù hợp (ví dụ TEMPLATE_AND_VALUES), kiểm value được decode đúng ở server.

**4.** URLEncoder có phù hợp cho URI path không?

**Đáp án:** Không. Nó encode application/x-www-form-urlencoded, trong đó space thành `+`, khác URI path semantics.

**5.** Query param và form field có cùng vị trí trong HTTP request không?

**Đáp án:** Không. Query nằm trên URL; form-urlencoded là request body.

**6.** Vì sao `MultiValueMap` hơn `Map<String,String>` cho form?

**Đáp án:** MultiValueMap giữ nhiều giá trị cùng tên như `tag=java&tag=webflux`.

**7.** Để gửi form body cần Content-Type nào?

**Đáp án:** `application/x-www-form-urlencoded`.

**8.** Để gửi file + metadata cùng request, nên dùng representation nào?

**Đáp án:** `multipart/form-data` với `MultiValueMap<String,Object>` chứa Resource, text field và HttpEntity cho part custom headers.

**9.** Multipart boundary nên do ai tạo?

**Đáp án:** HTTP message converter/RestClient; ứng dụng không tự hardcode header và body separators.

**10.** Vì sao dùng FileSystemResource cho file part?

**Đáp án:** Nó cung cấp stream và filename từ tệp trên disk để converter tạo Content-Disposition phù hợp.

**11.** ByteArrayResource có chắc gửi filename không?

**Đáp án:** Không. Có thể cần override getFilename hoặc bọc headers tương ứng.

**12.** HttpEntity chứa JSON part khác top-level HttpEntity ở RestTemplate thế nào?

**Đáp án:** Trong multipart map, HttpEntity đại diện body/headers **của part**. Top-level RestTemplate HttpEntity đại diện body/headers toàn request.

**13.** Khi server trả 204, `retrieve().body(Dto.class)` có chắc khác null không?

**Đáp án:** Không. HTTP response có thể không có body; phải xử lý null/ResponseEntity nếu cần status.

**14.** POST upload timeout thì có chắc server chưa lưu file không?

**Đáp án:** Không. Server có thể đã nhận/commit trước khi client mất response; retry cần idempotency.

**15.** Spring Boot 3.0 có RestClient không?

**Đáp án:** Chưa có trong Spring Framework 6.0 mặc định của Boot 3.0; dùng Boot 3.2+ hoặc phù hợp Framework 6.1+ để có RestClient.

**16.** Có cần tự chuyển multipart FileSystemResource thành byte[] trước không?

**Đáp án:** Không. Message converter có thể dùng Resource; giữ bytes toàn bộ trong heap thường không cần thiết.

**17.** Nếu Content-Type của một JSON part bị đặt text/plain thì chuyện gì có thể xảy ra?

**Đáp án:** Server không chọn đúng JSON decoder để bind DTO, gây lỗi request/part conversion; đặt HttpEntity với application/json.

**18.** Trong WebFlux server, có nên gọi RestClient đồng bộ trên Netty event-loop không?

**Đáp án:** Không. Nó có thể block loop; chọn WebClient reactive hoặc bridge blocking work sang bounded scheduler khi bắt buộc.

## 33. Nguồn tham khảo

- Spring Framework 6.2 — REST Clients, RestClient/RestTemplate/HTTP Interface và Multipart: https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html
- Spring Framework 6.2 — URI Links, UriBuilder/encoding: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-uri-building.html
- Spring Framework — `DefaultUriBuilderFactory` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/util/DefaultUriBuilderFactory.html
- Spring Framework — `UriComponentsBuilder` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html
- Spring Framework — `RestClient` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestClient.html
- Spring Framework — `RestTemplate` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestTemplate.html
- Spring Framework — `FormHttpMessageConverter` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/http/converter/FormHttpMessageConverter.html
- Spring Framework — `HttpEntity` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/http/HttpEntity.html
- Spring Framework — `FileSystemResource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/core/io/FileSystemResource.html
- Spring Framework — `MockRestServiceServer` tests: https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-client.html
- Spring Framework 6.2 — Spring MVC Multipart forms: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/multipart-forms.html
- Java 17 — `URLEncoder` Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLEncoder.html
- Java 17 — `HttpClient` Javadoc: https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html
- HTTP Semantics RFC 9110 — Content-Type, Accept, methods, status codes: https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-004">retrieve, exchange và đọc status, header, body</a>

<details>
<summary>Xem chi tiết</summary>

# RestClient: retrieve, exchange và đọc status, header, body trong HTTP client đồng bộ

## 1. Mục tiêu: không chỉ đọc JSON, mà còn hiểu toàn bộ HTTP response

Một HTTP client gọi API đối tác thường không chỉ cần dữ liệu JSON. Nó cần biết request thành công hay thất bại, status code thực, thông tin về caching, correlation ID, Location của tài nguyên mới được tạo, và phải quyết định có retry hay không.

Ví dụ server trả:

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/products/42
X-Request-ID: req-abc123

{"id":42,"name":"Sách Spring"}
```

Nếu chỉ deserialize body thành Java object, ta có thể bỏ mất tín hiệu **201 Created** và **Location**. Ngược lại, nếu chỉ xem status mà quên body, ta có thể đánh mất mã lỗi nghiệp vụ. Spring cung cấp API để lấy những phần này theo nhu cầu.

**Chủ đề trực tiếp:** `retrieve`, `exchange` và cách đọc **status, headers, body**, trong nhóm **HTTP client đồng bộ** của Spring Framework.

Với **Spring Framework 6.2.x / Spring Boot 3.5.x / Java 17+**, lựa chọn hiện đại cho client đồng bộ là **`org.springframework.web.client.RestClient`** (fluent API, xuất hiện từ Spring Framework 6.1). `RestTemplate` vẫn có thể gặp trong hệ thống cũ nhưng API và tên phương thức không hoàn toàn giống `RestClient`; `WebClient` thuộc nhánh non-blocking/reactive và có các kiểu trả về khác.

Bài này trả lời:

1. Khi nào gọi `retrieve().body(Type.class)`, `retrieve().toEntity(Type.class)` hoặc `retrieve().toBodilessEntity()`?
2. Làm sao đọc `ResponseEntity#getStatusCode()`, `getHeaders()`, `getBody()` đúng theo status?
3. Khi nào chọn `exchange((request, response) -> ...)` để tự phân tích response?
4. Vì sao `retrieve()` mặc định ném exception cho 4xx/5xx, còn `exchange()` không tự áp dụng status handlers?
5. `onStatus(...)` xử lý lỗi có khác `exchange(...)` không?
6. Làm sao đọc response body raw stream an toàn, không để stream escape callback hoặc đọc vô hạn bytes?
7. Cách cấu hình Spring Boot `RestClient.Builder`, timeout/request factory, test với `@RestClientTest` + `MockRestServiceServer`?
8. Vì sao các quyết định retry, caching, bảo mật dữ liệu và xử lý response rỗng là một phần của HTTP contract?

## 2. Bức tranh tổng thể HTTP request và response

```text
                         Spring application
                                  |
                         RestClient.get/post
                                  |
                    URI + request headers + body
                                  |
                                  v
                  ClientHttpRequestFactory
                      (JDK / Apache / ...)
                                  |
                           HTTP network
                                  |
                                  v
                 HTTP status + headers + bytes
                                  |
                  +---------------+---------------+
                  |                               |
             retrieve()                        exchange(...)
                  |                               |
       status handlers cho lỗi                 callback nhận
       + HttpMessageConverter                  request + response
                  |                               |
          .body(Type)                          tự kiểm tra
          .toEntity(Type)                      status/headers/body
          .toBodilessEntity()                  và trả giá trị
                  |                               |
                  +--------------+----------------+
                                 |
                      domain result / exception
```

**Một request synchronous có thể chặn thread gọi** cho tới khi hoàn tất mạng và phần body extraction. `RestClient` không biến phép gọi HTTP thành non-blocking. Trong Spring MVC Servlet, nó có thể được gọi trên request worker theo ngân sách concurrency và timeout phù hợp. Trong Spring WebFlux/Netty event loop, **không gọi `RestClient` trực tiếp**, nếu chưa cô lập sang scheduler dành cho blocking hoặc — tốt hơn — dùng `WebClient` reactive.

### 2.1. Cơ chế phía dưới

`RestClient` sử dụng:

- `ClientHttpRequestFactory`: thực thi request trên một HTTP transport cụ thể, ảnh hưởng kết nối, TLS, timeout và pooling.
- `HttpMessageConverter<?>`: encode Java object thành request bytes hoặc decode response bytes thành `String`, `byte[]`, JSON DTO hay các types khác.
- `HttpStatusCode`: biểu diễn HTTP status, gồm chuẩn và mã mở rộng.
- `HttpHeaders`: biểu diễn response headers (multi-value, case-insensitive semantics).
- `ResponseEntity<T>`: gói **status + headers + body**.
- `RestClientResponseException` và các subclasses: mặc định thể hiện HTTP error responses ở nhánh `retrieve`.

HTTP status không phải một JSON field. Một response có thể có `204 No Content`, `304 Not Modified` hoặc một số error response không mang body; code cần xử lý chính xác.

## 3. Tạo RestClient: sử dụng Boot-provided builder

Trong **Spring Boot 3.5**, dự án chỉ cần `spring-boot-starter-web` hoặc những dependencies tương thích với Spring Web để có `RestClient`, Jackson cho JSON và auto-configured `RestClient.Builder`.

Fragment của `build.gradle` trong project đã được tạo bởi Spring Initializr:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`RestClient` cũng có thể dùng bên ngoài HTTP server nếu bạn chỉ xây scheduled job/CLI app; khi đó dùng các module cần thiết cho HTTP client và converters, không bắt buộc mở embedded Tomcat. Bài này dùng Boot starter cho ví dụ và tests đơn giản.

### 3.1. Builder do Spring Boot cung cấp

```java
@Service
class CatalogGateway {
    private final RestClient http;

    CatalogGateway(RestClient.Builder builder) {
        this.http = builder
                .baseUrl("https://partner.example.test")
                .defaultHeader("X-Client-App", "catalog-sync")
                .build();
    }
}
```

Đây là **class fragment**; `RestClient.Builder` phải được import từ `org.springframework.web.client.RestClient`, và hostname ví dụ không phải API thật. Inject Boot-managed builder giúp nhận các customizations/observability phù hợp của Boot. Nếu gọi `RestClient.create()` độc lập, bạn **không nhất thiết** nhận mọi customization đã cấu hình cho builder Boot.

Một `RestClient` đã build có thể dùng lại qua nhiều threads; nên tái sử dụng thay vì tạo client và kết nối mới cho mỗi HTTP request. Đừng tự thêm `Authorization` token cố định vào default headers nếu nó có scope từng user; token từ request phải được truyền đúng theo security context và không bị cache rò rỉ giữa tenants.

### 3.2. Cấu hình base URL và URI variables

```java
Product product = http.get()
        .uri("/products/{id}", productId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Product.class);
```

Đây là **method fragment**; `Product` và `productId` do ứng dụng định nghĩa. Spring xử lý template variable phù hợp hơn việc nối chuỗi:

```java
// Không nên tự nối dữ liệu user vào URI:
String raw = "/products/" + userSuppliedPath;
```

Với query:

```java
http.get()
    .uri(uriBuilder -> uriBuilder
        .path("/products")
        .queryParam("page", 2)
        .queryParam("q", query)
        .build())
    .retrieve()
    .toEntity(String.class);
```

Đây là fragment với `query` đã được khai báo. Khi request URL liên quan dữ liệu không đáng tin, cần phân biệt path/query values và base URI; đừng để user tự nhập host/URL tuyệt đối nếu không có SSRF policy.

## 4. `retrieve()`: cách đọc response thông dụng

Chuỗi dùng phổ biến:

```java
Product found = http.get()
        .uri("/products/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(Product.class);
```

Ba bước:

1. `get().uri(...)` chuẩn bị HTTP request, chưa tự trả body.
2. `retrieve()` trả `RestClient.ResponseSpec` để cấu hình cách xử lý status/body; **bản thân `retrieve()` chưa thực thi request**.
3. **Terminal operation** `body(...)`, `toEntity(...)` hoặc `toBodilessEntity()` kích hoạt HTTP exchange và extraction.

Điểm quan trọng này giúp tránh anti-pattern:

```java
http.get().uri("/products/7").retrieve();
// Không gọi terminal method => không có HTTP request như mong đợi.
```

Nếu response body không quan trọng nhưng vẫn phải gửi HTTP request, hãy dùng `toBodilessEntity()` thay vì bỏ dở ở `retrieve()`.

### 4.1. `body(Class<T>)`

Khi chỉ cần nội dung và không phải ra quyết định dựa vào status/headers:

```java
String text = http.get()
        .uri("/health")
        .retrieve()
        .body(String.class);

Product product = http.get()
        .uri("/products/7")
        .retrieve()
        .body(Product.class);

byte[] bytes = http.get()
        .uri("/small-binary")
        .retrieve()
        .body(byte[].class);
```

Đây là fragments; types và endpoints minh họa. Converter được chọn dựa trên Java type, content type và dependencies. Với body rỗng, phương thức `body(Type)` **có thể trả null** theo contract, đặc biệt `204 No Content`. Đừng khẳng định chắc chắn nó luôn tạo object.

`body(byte[].class)` không phù hợp để tải file nhiều GB vì có thể giữ toàn response trong RAM. Với dữ liệu lớn, cần streaming extraction bằng API thích hợp và giới hạn tổng bytes.

### 4.2. `body(ParameterizedTypeReference<T>)` cho generics

```java
List<Product> products = http.get()
        .uri("/products")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(new ParameterizedTypeReference<
                List<Product>>() {});
```

Khác `List.class`, `ParameterizedTypeReference<List<Product>>` giữ **element type** cho JSON converter. Nếu dùng raw list, các phần tử có thể trở thành `LinkedHashMap`, dẫn đến `ClassCastException` khi cast thành `Product`.

Đừng dùng `body(new ParameterizedTypeReference<List<Product>>() {})` với một endpoint có thể trả **triệu phần tử** nếu toàn bộ dữ liệu sẽ được tích lũy; cần pagination, streaming reader hoặc giải pháp đồng bộ theo trang.

### 4.3. Không nhầm Content-Type với Accept

- `.accept(MediaType.APPLICATION_JSON)` đặt request header **Accept: application/json**, nói client mong định dạng response nào.
- `.contentType(MediaType.APPLICATION_JSON)` trên POST/PUT/PATCH cho biết **request body** đang gửi là JSON.
- Server có thể trả 406/415 nếu không có representation phù hợp; xử lý response status là việc client phải quan tâm.
- `Content-Type` response là do server trả về, không được tin hoàn toàn khi chuyển dữ liệu không đáng tin vào hệ thống.

## 5. `retrieve().toEntity(Class<T>)`: lấy đủ status, headers, body

Khi cần metadata của response:

```java
ResponseEntity<Product> response = http.get()
        .uri("/products/{id}", id)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .toEntity(Product.class);

HttpStatusCode status = response.getStatusCode();
HttpHeaders headers = response.getHeaders();
Product body = response.getBody();
```

Từ đó:

```java
int code = status.value();
MediaType contentType = headers.getContentType();
String requestId = headers.getFirst("X-Request-ID");
String etag = headers.getETag();
URI location = headers.getLocation();
long length = headers.getContentLength();
```

Đây là **method fragments** cho response có metadata thích hợp. Giá trị các headers có thể **null** hoặc vắng mặt; `getContentLength()` dùng giá trị sentinel nếu không biết. Từng API có semantics riêng (ví dụ ETag hợp lệ gồm dấu nháy kép trong trường hợp strong validator); đừng mặc định mọi header đều có trên HTTP response.

### 5.1. ResponseEntity không có nghĩa body luôn tồn tại

```java
ResponseEntity<Void> r = http.delete()
        .uri("/products/{id}", id)
        .retrieve()
        .toBodilessEntity();
```

Với response 204, `r.getBody()` là null; `r.getStatusCode().value()` là 204. Với response 202, không nên tự coi processing đã hoàn tất chỉ vì request thành công. Với 201, header `Location` giúp client biết URI tài nguyên mới.

### 5.2. HEAD và 304

Một HEAD request có headers/status nhưng **không có HTTP response body**:

```java
ResponseEntity<Void> head = http.head()
        .uri("/products/7")
        .retrieve()
        .toBodilessEntity();

String etag = head.getHeaders().getETag();
```

`304 Not Modified` cũng không mang representation body theo HTTP semantics. Nếu dùng conditional GET:

```java
ResponseEntity<Product> r = http.get()
        .uri("/products/7")
        .header(HttpHeaders.IF_NONE_MATCH, cachedEtag)
        .retrieve()
        .toEntity(Product.class);
```

`cachedEtag` phải là ETag đúng format. Khi server trả 304, response body thường null; ứng dụng phải dùng cache trước đó thay vì coi đây là “dữ liệu mất” hoặc retry vô hạn. Vấn đề cache còn liên quan Cache-Control, Vary, validators và tenant/auth scoping.

## 6. `toBodilessEntity()`: thực thi request nhưng không decode body

Nhiều APIs thành công mà không cần đọc body:

```java
ResponseEntity<Void> response = http.post()
        .uri("/products")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateProduct("Spring Basics"))
        .retrieve()
        .toBodilessEntity();
```

Khi server trả 201 Created:

```java
if (response.getStatusCode().value() == 201) {
    URI location = response.getHeaders().getLocation();
    // Validate expected host/path before following Location.
}
```

**Đây là fragment**, giả định record `CreateProduct` có trong ứng dụng. Dù method tên `toBodilessEntity`, HTTP response vẫn được xử lý/giải phóng theo client API; nếu server gửi body lỗi 4xx/5xx, nhánh `retrieve` mặc định **ném exception** trước khi cho caller một entity bình thường.

### 6.1. Vì sao không chỉ gọi retrieve?

```java
http.post()
        .uri("/jobs")
        .body(command)
        .retrieve();
```

Điều này chỉ tạo `ResponseSpec`, **chưa gửi request**. Dùng `toBodilessEntity()` nếu bạn quan tâm status + headers nhưng không cần body.

### 6.2. HTTP semantics của 201, 202, 204

| Status | Ý nghĩa | Header cần quan tâm |
| --- | --- | --- |
| 200 OK | Request thành công, thường có representation | Content-Type, ETag, Cache-Control |
| 201 Created | Đã tạo resource | Location, có thể ETag/body |
| 202 Accepted | Đã chấp nhận để xử lý, chưa chắc hoàn tất | Location/status endpoint, Retry-After theo contract |
| 204 No Content | Thành công, không có representation body | ETag hoặc metadata khác nếu có |
| 304 Not Modified | Có thể tái sử dụng cached representation | ETag/Cache-Control/Vary |
| 400 Bad Request | Input/protocol không hợp lệ | Error response body |
| 401 Unauthorized | Thiếu/chưa hợp lệ authentication | WWW-Authenticate |
| 403 Forbidden | Đã xác thực nhưng không đủ quyền | Error code an toàn |
| 404 Not Found | Không tìm thấy hoặc ẩn resource | Client quyết định not-found semantics |
| 409 Conflict | Xung đột trạng thái/uniqueness | Stable error code |
| 429 Too Many Requests | Vượt quota/rate limit | Retry-After nếu server cung cấp |
| 500 Internal Server Error | Lỗi server | Correlation ID |
| 503 Service Unavailable | Tạm hết capacity/downstream unavailable | Retry-After nếu có |

Không có một status-code mapping chung nào thích hợp mọi service; cần hợp đồng API của đối tác.

## 7. `retrieve()` xử lý 4xx/5xx mặc định ra sao?

Theo Spring Framework 6.2, `retrieve` mặc định ném một subclass của `RestClientException` khi nhận HTTP response **4xx hoặc 5xx**. Các lỗi status thường là:

- `HttpClientErrorException` cho 4xx, có thể là specialized subclasses như `HttpClientErrorException.NotFound`.
- `HttpServerErrorException` cho 5xx.
- `RestClientResponseException` là base phù hợp để đọc status/body/headers của **HTTP error response**.
- `ResourceAccessException` thường bọc lỗi I/O/network như connect timeout, read timeout, DNS/TLS — **không có HTTP status** nếu server chưa trả HTTP response.
- `UnknownContentTypeException` có thể xảy ra khi converter không xử lý representation mong đợi.
- Lỗi Jackson conversion/serialization có thể nằm trong hierarchy `RestClientException`/conversion exceptions, cần phân loại theo context.

### 7.1. Bắt exception HTTP đúng cách

```java
try {
    Product product = http.get()
            .uri("/products/{id}", id)
            .retrieve()
            .body(Product.class);
    // Xử lý product.
} catch (RestClientResponseException ex) {
    int status = ex.getStatusCode().value();
    HttpHeaders headers = ex.getResponseHeaders();
    String message = ex.getResponseBodyAsString(
            StandardCharsets.UTF_8);

    // Chỉ phân loại/sanitize thông tin để log/đưa ra client.
} catch (ResourceAccessException ex) {
    // I/O failure: có thể chưa nhận response/status nào.
}
```

Đây là **fragment** với imports `RestClientResponseException`, `ResourceAccessException`, `StandardCharsets`. `message` chứa raw error response body từ server không đáng tin; không hiển thị trực tiếp nó như thông báo public hoặc log toàn bộ dữ liệu nhạy cảm.

### 7.2. Một response 404 là exception, không phải ResponseEntity bình thường

```java
ResponseEntity<Product> r = http.get()
        .uri("/products/999")
        .retrieve()
        .toEntity(Product.class);
```

Nếu server trả 404, **mặc định không vào nhánh xử lý `r.getStatusCode()==404`** vì `toEntity` ném exception. Muốn 404 thành domain exception, hãy dùng `onStatus`. Muốn 404 thành một **giá trị bình thường** để xử lý đa dạng status, có thể dùng `exchange`.

### 7.3. Đừng catch-all rồi trả null

```java
// Anti-pattern:
try {
    return callPartner();
} catch (Exception ignored) {
    return null;
}
```

Cách này khiến 401/403/429/503/DNS timeout đều biến thành “không có sản phẩm”, dẫn đến dữ liệu sai, retry hỏng và che lỗi hệ thống. Dùng typed exceptions/codes, đo status và thông báo sự cố phù hợp.

## 8. `onStatus(...)`: custom status handling trong retrieve

Mỗi `retrieve()` có thể gắn xử lý theo status:

```java
Product value = http.get()
        .uri("/products/{id}", id)
        .retrieve()
        .onStatus(
            status -> status.value() == 404,
            (request, response) -> {
                throw new ProductMissingException(id);
            })
        .body(Product.class);
```

Đây là **fragment**; custom exception được khai báo trong demo. Handler nhận **request và response**, có thể đọc HTTP status, headers và body error nếu cần. Thay vì trả raw error JSON của đối tác lên người dùng, hãy map sang lỗi domain an toàn, ví dụ PRODUCT_NOT_FOUND.

### 8.1. Thứ tự xử lý

Khi có nhiều status handlers, ưu tiên phụ thuộc cấu hình và thứ tự chúng được đăng ký. Nên đặt handlers đặc hiệu trước handlers rộng, và kiểm tra bằng unit tests với 404/409/429/500.

Nếu có default status handlers cấu hình trên `RestClient.Builder`, các handlers của từng response có thể được dùng trước defaults tùy contract Spring 6.2. Đừng suy đoán “status handler này luôn chạy” nếu một handler khác đã match và throw trước.

### 8.2. onStatus không phải exchange

`retrieve().onStatus(...)` phù hợp khi chỉ cần đổi cách **biến lỗi thành exception** và vẫn muốn dùng `body(Class)`/`toEntity(Class)` cho success.

`exchange(...)` phù hợp khi cần **phân nhánh theo status**, đọc headers/raw bytes ngay tại callback hoặc trả một kiểu domain result khác nhau mà không để mặc định 4xx/5xx ném trước.

### 8.3. Đừng đè mọi 4xx thành “not found”

Không nên:

```java
.onStatus(HttpStatusCode::is4xxClientError,
    (req, res) -> {
        throw new ProductMissingException(id);
    })
```

Vì 401, 403, 409 và 429 có ý nghĩa hoàn toàn khác 404. Tránh làm mất nguyên nhân do authentication, conflict hoặc quota. Hãy nhận diện chính xác và giữ thông tin kỹ thuật an toàn trong logs/metrics.

## 9. `exchange(...)`: toàn quyền xem request và raw response

Spring 6.2 `RestClient.RequestHeadersSpec#exchange(...)` nhận callback:

```java
T result = http.get()
        .uri("/products/{id}", id)
        .exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            HttpHeaders headers = response.getHeaders();
            InputStream body = response.getBody();
            // Tự đọc/biến đổi/xử lý tại đây.
            return someResult;
        });
```

Đây là **fragment**; `someResult` cần được xác định cụ thể. `exchange` cho quyền điều khiển mạnh hơn và yêu cầu developer nắm rõ HTTP response contract.

**Điểm bắt buộc nhớ: `exchange(...)` không áp dụng default status handlers hay `retrieve().onStatus`.** Nếu server trả 404 hoặc 500, callback vẫn có thể nhận HTTP response bình thường để bạn xử lý; client không tự ném `HttpClientErrorException.NotFound` chỉ vì status 404 trong đường này.

### 9.1. Một callback có thể trả kiểu kết quả riêng

```java
record StatusProbe(
        int status,
        String requestId,
        String contentType,
        String body) {}
```

Ví dụ:

```java
StatusProbe probe = http.get()
        .uri("/probe/{id}", id)
        .exchange((request, response) -> {
            HttpHeaders headers = response.getHeaders();

            byte[] bytes = response.getBody()
                    .readNBytes(1025);

            if (bytes.length > 1024) {
                throw new OversizedProbeException();
            }

            MediaType contentType = headers.getContentType();

            return new StatusProbe(
                response.getStatusCode().value(),
                headers.getFirst("X-Request-ID"),
                contentType != null
                    ? contentType.toString() : null,
                new String(bytes, StandardCharsets.UTF_8));
        });
```

Đây là **method fragment** với `OversizedProbeException` được khai báo trong demo. `InputStream#readNBytes` (Java 17) có thể ném `IOException`; callback API cho phép throws IOException. Đọc tối đa **1.025 bytes**, từ chối nếu vượt 1.024; không dùng `readAllBytes()` vô hạn với response không đáng tin.

`new String(bytes, UTF_8)` chỉ phù hợp dữ liệu text mà bạn dự kiến; với file binary, decode thành String có thể làm hỏng dữ liệu. Với payload JSON phức tạp, prefer message converters trên đường `retrieve` hoặc converter API phù hợp thay vì tự parse String thủ công.

### 9.2. Ai đóng response và InputStream?

Spring 6.2 overload `exchange(callback)` mặc định đóng `ClientHttpResponse` sau khi callback kết thúc. Do đó:

- Đọc xong/copy xử lý **bên trong callback**.
- **Không trả `response.getBody()`** ra ngoài callback rồi đọc sau: stream có thể đã bị đóng.
- Khi dùng overload `exchange(callback, false)`, **người gọi chịu trách nhiệm** quản lý đóng response; chỉ dùng khi hiểu resource ownership.
- Một response không được tiêu thụ/đóng đúng có thể gây leak HTTP connections và làm connection pool cạn.
- Với file lớn, stream/copy dữ liệu đến sink có kiểm soát **bên trong callback** thay vì tích toàn bộ trong heap.

### 9.3. exchange không tự biến lỗi status thành exception

```java
record CheckResult(int status, boolean retryable) {}

CheckResult result = http.get()
        .uri("/health")
        .exchange((request, response) -> {
            int code = response.getStatusCode().value();

            return new CheckResult(
                code,
                code == 429 || code == 503);
        });
```

Với 503, callback vẫn có thể trả `CheckResult(503,true)` thay vì ném. Nhưng **không tự retry request** chỉ vì `retryable=true`. Phải xem method có an toàn/idempotent, Retry-After, retry budget, backoff và tình trạng downstream.

### 9.4. exchange không đồng nghĩa RestTemplate.exchange

Cần phân biệt:

```java
// RestClient:
restClient.get()
        .uri("/products/7")
        .exchange((request, response) ->
                response.getStatusCode().value());
```

và:

```java
// RestTemplate:
ResponseEntity<Product> r =
        restTemplate.exchange(
                "/products/7",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Product.class);
```

`RestTemplate.exchange(...)` là phương thức **template-style** có thể trả `ResponseEntity` trực tiếp và mặc định dùng `ResponseErrorHandler`. `RestClient.exchange(...)` nhận callback và **bỏ qua status handlers mặc định**. Đừng copy nhận định về exception/error handling của một API sang API kia.

## 10. Đọc status: HttpStatusCode và các trường hợp không đơn giản

`HttpStatusCode` thể hiện một status; nên dùng:

```java
HttpStatusCode status = response.getStatusCode();

boolean success = status.is2xxSuccessful();
boolean clientError = status.is4xxClientError();
boolean serverError = status.is5xxServerError();
int code = status.value();
```

Đây là fragments. Không cần ép mọi status sang `HttpStatus` enum bằng `HttpStatus.valueOf`, vì có HTTP status codes mở rộng không có trong enum. Với codes đối tác trả bất thường, hãy xử lý an toàn theo family/status.

### 10.1. `2xx` không phải cùng một ý nghĩa

- 200: thường có representation body.
- 201: nên xét Location/new resource.
- 202: request accepted để xử lý sau, có thể có job ID.
- 204: không có body; `body(Product.class)` có thể trả null.
- 206 Partial Content: dùng Range/Content-Range, không coi bytes nhận được là toàn resource.

Nếu service trả 204 nhưng code bắt buộc `body != null`, cần chuyển API contract sang `Optional<Product>` hoặc result type phân biệt trường hợp empty.

### 10.2. Lỗi 3xx và redirect

Redirect handling phụ thuộc `ClientHttpRequestFactory`/HTTP client và cấu hình redirect policy. Không mặc định một request sẽ luôn trả 302 cho code, hoặc luôn theo redirect. Khi redirect đi sang host khác, không chuyển nhầm Authorization headers/cookies; cấu hình client/transport theo security policy.

### 10.3. 401 và 403

- 401 thường cần xem `WWW-Authenticate` để hiểu authentication scheme/challenge; không log bearer credentials.
- 403 cho biết không có quyền trong context; auto retry cùng credentials thường không có ích.
- Đối tác đôi khi ẩn resource bằng 404 để tránh tiết lộ thông tin; client cần tuân contract, không đoán privilege từ response.

## 11. Đọc headers có kiểu dữ liệu rõ

`HttpHeaders` hỗ trợ nhiều getters và kiểu values:

```java
HttpHeaders headers = entity.getHeaders();

MediaType type = headers.getContentType();
URI location = headers.getLocation();
String etag = headers.getETag();
long contentLength = headers.getContentLength();
long modifiedEpochMillis = headers.getLastModified();
String requestId = headers.getFirst("X-Request-ID");
List<String> cacheControl = headers.get(HttpHeaders.CACHE_CONTROL);
```

Đây là fragments, một số values có thể null hoặc sentinel. Khi dùng cùng tên header nhiều lần, `headers.get(name)` có thể trả **danh sách**; `getFirst` bỏ các giá trị sau. Chọn theo semantics của header, không tùy tiện gộp Set-Cookie với dấu phẩy.

### 11.1. Location phải được hiểu đúng

Server có thể trả:

```http
Location: /api/products/42
```

Đây là URI **relative**, không phải URL tuyệt đối. Nếu cần theo liên kết, resolve theo request URI/public base URI đã kiểm tra. Với Location tuyệt đối từ bên ngoài, **không tự động follow sang mọi host**; SSRF hoặc token leakage có thể xảy ra nếu server/compromised upstream trả host nội bộ.

### 11.2. Retry-After có hai định dạng

Header `Retry-After` có thể biểu diễn:
- **delta-seconds**, ví dụ `Retry-After: 5`.
- **HTTP-date**, ví dụ một ngày GMT theo chuẩn HTTP.

Đừng luôn `Integer.parseInt(headers.getFirst("Retry-After"))` vì giá trị date sẽ gây lỗi. Thiết kế parser có upper bound, clock skew handling, jitter và retry budget. Không retry request POST tạo resource nếu không có idempotency strategy.

### 11.3. ETag, Last-Modified và caching

`ETag` là validator của representation. Conditional GET có thể gửi `If-None-Match`, nhận 304 và dùng cached bytes tương ứng. Cache phải được khóa theo URI, headers ảnh hưởng representation (`Vary`), authentication/tenant và policy `Cache-Control`; không chia sẻ dữ liệu riêng tư giữa người dùng chỉ vì cùng path.

### 11.4. Correlation ID

`X-Request-ID`/`traceparent` có thể giúp đối chiếu calls, nhưng nếu upstream tự gửi requestId bất kỳ, đừng tin nó như authenticated identity. Khi log, sanitize length/characters và không đưa raw URI/query/token vào logs.

## 12. Đọc body có kiểu: String, DTO, List, bytes và empty

| Body target | Dùng khi | Nguy cơ |
| --- | --- | --- |
| `String.class` | Plain text hoặc debug response nhỏ | Dữ liệu text có thể chứa secrets; memory nếu quá lớn |
| `Product.class` | JSON object | Jackson schema mismatch / fields không có |
| `byte[].class` | Binary nhỏ | Tích toàn bộ bytes vào heap |
| `ParameterizedTypeReference<List<Product>>` | JSON list nhỏ/vừa | Heap tăng với số phần tử |
| `Void.class` / `toBodilessEntity()` | Không cần body | Đừng chờ DTO trên 204 |
| `exchange(callback)` với InputStream | Custom parsing/stream copy/bounded inspection | Tự quản status, resource và size |
| `Resource.class` | Response dạng tài nguyên theo converter | Phải xem transport, streaming và close semantics cụ thể |

Không coi Java type thành bằng chứng dữ liệu an toàn. DTO có thể được deserialize nhưng vẫn sai constraints nghiệp vụ. Nếu bắt buộc validate, dùng Jakarta Validation/service validation sau khi đọc body trước khi side effects.

### 12.1. Body bị tiêu thụ và response đã đóng

`RestClient` synchronous extraction có thể đóng response sau terminal operation. Đừng nghĩ có thể gọi:

```java
RestClient.ResponseSpec spec =
        http.get().uri("/products/7").retrieve();

Product p = spec.body(Product.class);
String raw = spec.body(String.class); // Không nên: tái dùng spec.
```

Mỗi terminal invocation có thể kích hoạt exchange riêng hoặc rơi vào behavior không mong đợi; không dùng pattern này để “đọc body hai lần” với giả định cùng HTTP response. Nếu cần cả body + headers, dùng **một** `toEntity`. Nếu cần raw bytes + status, dùng **một** `exchange` callback.

### 12.2. Không tự động tin Content-Length

Content-Length có thể thiếu, response chunked/HTTP2, compressed content size khác decoded size, hoặc server báo sai. **Cần giới hạn thực tế trong lúc đọc bytes**, không chỉ kiểm tra `getContentLength()` trước khi `readAllBytes()`.

Ví dụ `readNBytes(MAX+1)` từ phần `exchange` giới hạn việc giữ dữ liệu; với stream copy lớn, đếm bytes trên từng chunk và abort khi vượt quota.

### 12.3. JSON không đúng schema

HTTP 200 không bảo đảm JSON body hợp lệ theo DTO. Jackson có thể gặp `HttpMessageNotReadableException` hoặc conversion-related exception tùy call path/framework version. Cần classify **decoder/schema error** riêng với network timeout hay 404, không retry vô hạn vì một field type không khớp.

## 13. Khi nào nên dùng retrieve và khi nào dùng exchange?

| Nhu cầu | Cách khuyến nghị | Lý do |
| --- | --- | --- |
| Chỉ cần JSON object từ success response | `retrieve().body(Product.class)` | Ngắn gọn, converter tự xử lý |
| Cần status/headers và body DTO | `retrieve().toEntity(Product.class)` | `ResponseEntity` bao gồm cả ba |
| POST nhận 201 + Location, không cần body | `retrieve().toBodilessEntity()` | Có metadata, không decode body |
| Đọc generic JSON list | `retrieve().body(ParameterizedTypeReference)` | Giữ generic type |
| 404 thành domain exception | `retrieve().onStatus(...).body(...)` | Đổi cách xử lý lỗi nhưng vẫn dùng converter |
| Muốn 404/503 là values, không ném mặc định | `exchange(...)` | Tự inspect status |
| Xử lý raw stream và byte cap | `exchange(...)` | Full callback, giữ resource scope |
| Chọn parser dựa vào status/header | `exchange(...)` hoặc thiết kế reader phù hợp | Có response metadata trước khi decode |
| Đơn giản tìm body text và vẫn theo dõi metadata | `retrieve().toEntity(String.class)` | Không cần exchange |
| Cần response streaming non-blocking | `WebClient`/reactive body | RestClient sync có thể giữ thread |

**Không chọn exchange chỉ vì “nó mạnh hơn”.** Nó khiến bạn phải chịu trách nhiệm áp HTTP status policy, parse body, đóng resource và kiểm soát input size — những việc `retrieve` đã làm tiện lợi trong use case thông thường.

## 14. Ví dụ ứng dụng hoàn chỉnh: Java 17 + Spring Boot 3.5

Mục tiêu demo: gọi một **API đối tác giả lập trong unit tests**, không kết nối network thật. Tạo `PartnerCatalogClient` sử dụng Spring Boot-managed `RestClient.Builder`:

- `find`: `retrieve().body`, trả DTO trên 200.
- `withMetadata`: `retrieve().toEntity`, lấy status + headers + DTO.
- `list`: `ParameterizedTypeReference<List<Product>>`.
- `create`: POST `toBodilessEntity`, đọc 201 + Location.
- `findOrThrow`: `onStatus(404)` đổi sang custom exception.
- `probe`: `exchange` đọc status/headers/body tối đa 1.024 bytes, kể cả khi response 404 hoặc 503.
- `inspectWithPolicy`: `exchange` chủ động ném lỗi khi 5xx, chứng minh developer phải quyết định.

**Giới hạn:** demo đặt host `https://partner.example.test` để làm mock, không phải endpoint hoạt động trên Internet. Không có authentication, retry, network transport tuning hoặc persistent DB. Các tests sẽ mock HTTP responses qua Spring test infrastructure mà không phụ thuộc Internet.

### 14.1. Cấu trúc file

```text
restclient-response-demo/
├── build.gradle
└── src/
    ├── main/java/demo/restread/
    │   └── RestReadApplication.java
    └── test/java/demo/restread/
        └── PartnerCatalogClientTests.java
```

### 14.2. Toàn bộ source Java

Tạo `src/main/java/demo/restread/RestReadApplication.java`:

```java
package demo.restread;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class RestReadApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestReadApplication.class, args);
    }
}

record Product(long id, String name) {}

record CreateProduct(String name) {}

record Probe(
        int status,
        String requestId,
        String contentType,
        String body) {}

final class ProductMissingException extends RuntimeException {
    ProductMissingException(long id) {
        super("Product " + id + " not found");
    }
}

final class PartnerUnavailableException extends RuntimeException {
    PartnerUnavailableException(int status) {
        super("Partner unavailable, HTTP " + status);
    }
}

final class OversizedProbeException extends RuntimeException {
    OversizedProbeException() {
        super("Probe response exceeds 1024 bytes");
    }
}

@Service
class PartnerCatalogClient {
    private static final int MAX_PROBE_BYTES = 1024;

    private final RestClient http;

    PartnerCatalogClient(RestClient.Builder builder) {
        // Host chỉ dành cho mock HTTP tests.
        this.http = builder
                .baseUrl("https://partner.example.test")
                .build();
    }

    Product find(long id) {
        return http.get()
                .uri("/products/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Product.class);
    }

    ResponseEntity<Product> withMetadata(long id) {
        return http.get()
                .uri("/products/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(Product.class);
    }

    List<Product> list() {
        return http.get()
                .uri("/products")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<
                        List<Product>>() {});
    }

    ResponseEntity<Void> create(CreateProduct input) {
        return http.post()
                .uri("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(input)
                .retrieve()
                .toBodilessEntity();
    }

    Product findOrThrow(long id) {
        return http.get()
                .uri("/products/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                    status -> status.value() == 404,
                    (request, response) -> {
                        throw new ProductMissingException(id);
                    })
                .body(Product.class);
    }

    Probe probe(long id) {
        return http.get()
                .uri("/probe/{id}", id)
                .exchange((request, response) -> {
                    HttpHeaders headers = response.getHeaders();

                    byte[] bytes = response.getBody()
                            .readNBytes(MAX_PROBE_BYTES + 1);
                    if (bytes.length > MAX_PROBE_BYTES) {
                        throw new OversizedProbeException();
                    }

                    MediaType type = headers.getContentType();

                    return new Probe(
                        response.getStatusCode().value(),
                        headers.getFirst("X-Request-ID"),
                        type != null ? type.toString() : null,
                        new String(bytes,
                                StandardCharsets.UTF_8));
                });
    }

    int inspectWithPolicy(long id) {
        return http.get()
                .uri("/probe/{id}", id)
                .exchange((request, response) -> {
                    HttpStatusCode status =
                            response.getStatusCode();

                    if (status.is5xxServerError()) {
                        throw new PartnerUnavailableException(
                                status.value());
                    }

                    return status.value();
                });
    }
}
```

**Vì sao file này có thể compile với Java 17?**

- Có một top-level `public class RestReadApplication` khớp tên file.
- Các `record`/exceptions/`PartnerCatalogClient` là package-private dưới `demo.restread`.
- `@SpringBootApplication` component scan phát hiện `@Service PartnerCatalogClient`.
- Spring Boot 3.5 cung cấp bean `RestClient.Builder` thông qua auto-configuration.
- `@RestClientTest(PartnerCatalogClient.class)` trong phần test sẽ tạo đúng service và builder có mock customization.
- `exchange` callback cho phép xử lý `IOException` từ `InputStream.readNBytes` theo method signature của Spring 6.2.
- `Probe` được tạo **trong callback**, không trả `InputStream` đã sắp đóng ra ngoài.
- `onStatus` chỉ xử lý chính xác 404, không làm mất 401, 409 hoặc 429.
- `ResponseEntity<Void>` cho POST làm rõ rằng body không được dùng để đọc Location.

### 14.3. Mẫu sử dụng kết quả

```java
ResponseEntity<Product> response =
        partner.withMetadata(7);

if (response.getStatusCode().is2xxSuccessful()) {
    String id = response.getHeaders()
            .getFirst("X-Request-ID");
    Product product = response.getBody();
    // Persist/cache/return product theo policy.
}
```

`partner` ở đây là `PartnerCatalogClient` đã được inject vào service tương ứng; fragment không phải `main` độc lập. Khi status 404, `withMetadata` mặc định ném exception nên nhánh if không xử lý 404; chọn `probe` hoặc `findOrThrow` nếu cần hành vi khác.

## 15. Kiểm thử đầy đủ với @RestClientTest và MockRestServiceServer

`@RestClientTest` trong Boot 3.5 auto-configures `RestClient.Builder`, JSON converters và `MockRestServiceServer` để kiểm tra một HTTP client **đồng bộ** không cần mở server socket thật. Nó là slice test: thông thường chỉ đăng ký component được chỉ định thay vì toàn bộ application beans.

Tạo `src/test/java/demo/restread/PartnerCatalogClientTests.java`:

```java
package demo.restread;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@RestClientTest(PartnerCatalogClient.class)
class PartnerCatalogClientTests {
    private static final String BASE =
            "https://partner.example.test";

    @Autowired
    PartnerCatalogClient partner;

    @Autowired
    MockRestServiceServer server;

    @Test
    void retrieveBodyMapsJsonToDto() {
        server.expect(requestTo(BASE + "/products/7"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                        {"id":7,"name":"Guide"}
                        """));

        Product result = partner.find(7);

        assertEquals(new Product(7, "Guide"), result);
        server.verify();
    }

    @Test
    void toEntityProvidesStatusHeadersAndBody() {
        server.expect(requestTo(BASE + "/products/8"))
                .andRespond(withStatus(HttpStatus.OK)
                    .header("X-Request-ID", "req-8")
                    .header(HttpHeaders.ETAG, "\\"v8\\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                        {"id":8,"name":"Metadata"}
                        """));

        ResponseEntity<Product> response =
                partner.withMetadata(8);

        assertEquals(200,
                response.getStatusCode().value());
        assertEquals("req-8",
                response.getHeaders()
                        .getFirst("X-Request-ID"));
        assertEquals("\\"v8\\"",
                response.getHeaders().getETag());
        assertEquals(new Product(8, "Metadata"),
                response.getBody());
        server.verify();
    }

    @Test
    void listPreservesGenericProductType() {
        server.expect(requestTo(BASE + "/products"))
                .andRespond(withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                        [{"id":1,"name":"A"},
                         {"id":2,"name":"B"}]
                        """));

        List<Product> values = partner.list();

        assertEquals(
                List.of(new Product(1, "A"),
                        new Product(2, "B")),
                values);
        server.verify();
    }

    @Test
    void postReadsCreatedStatusAndLocationWithoutBody() {
        server.expect(requestTo(BASE + "/products"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                    .header(HttpHeaders.LOCATION,
                            "/products/41"));

        ResponseEntity<Void> response =
                partner.create(
                        new CreateProduct("Created"));

        assertEquals(201,
                response.getStatusCode().value());
        assertEquals(URI.create("/products/41"),
                response.getHeaders().getLocation());
        assertEquals(null, response.getBody());
        server.verify();
    }

    @Test
    void retrieveDefaultsToExceptionFor404() {
        server.expect(requestTo(BASE + "/products/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                        {"code":"NOT_FOUND"}
                        """));

        RestClientResponseException error =
                assertThrows(
                    RestClientResponseException.class,
                    () -> partner.find(404));

        assertEquals(404,
                error.getStatusCode().value());
        server.verify();
    }

    @Test
    void onStatusMaps404ToTypedDomainError() {
        server.expect(requestTo(BASE + "/products/99"))
                .andRespond(
                    withStatus(HttpStatus.NOT_FOUND));

        ProductMissingException error =
                assertThrows(
                    ProductMissingException.class,
                    () -> partner.findOrThrow(99));

        assertTrue(error.getMessage()
                .contains("99"));
        server.verify();
    }

    @Test
    void exchangeSees404WithoutDefaultException() {
        server.expect(requestTo(BASE + "/probe/4"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                    .header("X-Request-ID", "req-4")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("missing"));

        Probe response = partner.probe(4);

        assertEquals(404, response.status());
        assertEquals("req-4",
                response.requestId());
        assertEquals("text/plain",
                response.contentType());
        assertEquals("missing", response.body());
        server.verify();
    }

    @Test
    void exchangeLetsApplicationReject503() {
        server.expect(requestTo(BASE + "/probe/5"))
                .andRespond(
                    withStatus(
                        HttpStatus.SERVICE_UNAVAILABLE));

        PartnerUnavailableException error =
                assertThrows(
                    PartnerUnavailableException.class,
                    () -> partner.inspectWithPolicy(5));

        assertTrue(error.getMessage()
                .contains("503"));
        server.verify();
    }

    @Test
    void probeRefusesPayloadOver1024Bytes() {
        server.expect(requestTo(BASE + "/probe/6"))
                .andRespond(withStatus(HttpStatus.OK)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("A".repeat(1025)));

        assertThrows(
                OversizedProbeException.class,
                () -> partner.probe(6));
        server.verify();
    }
}
```

Chạy:

```bash
./gradlew test
```

**Chín tests được thiết kế để pass** với Boot 3.5 / Spring Framework 6.2 / Java 17. Đây là **kỳ vọng qua rà soát API/source, chưa phải log của một lần thực thi Gradle**.

Các tests chứng minh:

- Converter đọc JSON DTO qua `retrieve().body`.
- `toEntity` giữ status, headers và body; ETag vẫn gồm dấu quote theo chuẩn.
- Generic list được deserialize thành `List<Product>`.
- POST đọc 201 + Location qua `toBodilessEntity`.
- 404 trên `retrieve` **mặc định ném exception**.
- `onStatus` đổi 404 thành `ProductMissingException`.
- 404 trên `exchange` **không bị status handler mặc định biến thành exception**.
- `exchange` có thể chủ động ném 503 theo policy.
- `exchange` đọc body có giới hạn, tránh buffer tùy ý.

### 15.1. Những gì slice test không chứng minh

`MockRestServiceServer` giả lập HTTP transport, không mở kết nối TLS/real socket. Chúng **không xác minh** DNS, proxy, SSL handshake, connection pooling, connect/read timeouts, redirect policy hay behavior thực của server remote. Để test phần đó cần integration tests với embedded server hoặc mock HTTP server và client transport thật (kèm network fault injection nếu cần).

`@RestClientTest` cũng không tự load mọi `@Configuration` trong application. Nếu production client phụ thuộc config bean khác, sử dụng `@Import` hoặc cấu hình đúng phạm vi slice test.

### 15.2. Tránh test dựa vào Internet thật

Nếu tests gửi request tới URL API public, kết quả sẽ dễ flaky vì network, rate limits, data thay đổi và credentials. Sử dụng `MockRestServiceServer` cho deterministic contract tests, thêm vài integration tests tới sandbox/staging của đối tác cho compatibility. Không ghi secrets vào repo hoặc mock expectations.

### 15.3. Test trên status and headers, không chỉ body

Một integration test tốt cần kiểm tra cả:
- HTTP method và đường dẫn.
- `Accept`, `Content-Type` và auth/correlation headers phù hợp.
- Status code chính xác.
- Location/ETag/Retry-After nếu hợp đồng yêu cầu.
- JSON DTO/field types.
- 4xx/5xx mapping và timeout/cancellation/retries theo policy.
- Body rỗng và unknown media type.
- Resource cleanup của raw exchange stream.

Không phải mọi endpoint đều cần assert mọi header; chỉ assert những gì là contract của chức năng.

## 16. Đọc response body lỗi an toàn

Một đối tác có thể trả:

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://partner.example.test/problems/duplicate",
  "title": "Conflict",
  "status": 409,
  "detail": "Product name already exists"
}
```

Với `retrieve()`, bạn có thể:

1. Dùng `onStatus` cho **409** để map sang domain `DuplicateProductException`.
2. Hoặc bắt `RestClientResponseException` và đọc status/headers/bytes, rồi parse một error DTO sau khi kiểm tra max size, Content-Type và schema.

Không nên:

```java
catch (RestClientResponseException ex) {
    throw new RuntimeException(
            ex.getResponseBodyAsString());
}
```

Error body là dữ liệu từ remote server; nó có thể chứa HTML, stack trace, IDs, request data, các ký tự không mong đợi. Đừng đưa nguyên văn đến browser, logs hoặc metrics tags. Ánh xạ sang mã lỗi ổn định như `PARTNER_CONFLICT` và ghi `requestId` an toàn để tra cứu.

### 16.1. Đọc body sau HTTP exception khác callback raw response

`RestClientResponseException` lưu status/headers/body được framework trích xuất cho error case; developer có thể đọc qua exception. `exchange` cho trực tiếp stream response trong callback. **Không giữ `ClientHttpResponse` hoặc `InputStream` của exchange ra ngoài** khi overload mặc định đóng tài nguyên.

### 16.2. Lỗi do network không có HTTP status

`ResourceAccessException` khi connect timeout/DNS/TLS/read failure không thể luôn gắn status 503 do remote server: có thể **chưa nhận bất kỳ HTTP response nào**. Ghi loại lỗi nội bộ và áp policy retry có giới hạn, thay vì giả lập status code mà server chưa trả.

## 17. Cấu hình timeout và HTTP transport

`RestClient` có abstraction `ClientHttpRequestFactory`. Cách cấu hình timeout phụ thuộc HTTP backend đã chọn: JDK `HttpClient`, Apache HttpComponents, Simple/JDK request factory hoặc library khác.

**Không gán một property chung tưởng tượng rồi tin mọi RestClient đã có timeout hợp lý.** Cần xác nhận theo Spring Boot 3.5 auto-configuration và chosen HTTP client library.

Ví dụ Java fragment khi dùng JDK request factory:

```java
JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory();

requestFactory.setReadTimeout(
        Duration.ofSeconds(3));

RestClient client = RestClient.builder()
        .requestFactory(requestFactory)
        .baseUrl("https://partner.example.test")
        .build();
```

Đây là **fragment**, cần `java.time.Duration` và `org.springframework.http.client.JdkClientHttpRequestFactory`. Ngoài read timeout, **connect timeout** được cấu hình trên `java.net.http.HttpClient` tương ứng khi cần. Với Apache HttpComponents request factory, constructors/settings khác và cần dependency tương ứng; đừng copy code JDK sang Apache.

Cần phân biệt:
- DNS lookup/connection establishment.
- TLS handshake.
- Connection pool acquisition.
- Socket/read inactivity timeout.
- Overall request deadline.
- Retries/backoff và circuit breaker.
- HTTP response body streaming time.

Một timeout 3 giây không tự bảo đảm mọi phase/case chỉ mất tối đa ba giây nếu backend định nghĩa nó là read timeout; test failure modes thực tế.

### 17.1. RestClient là blocking, đừng lạm dụng trên WebFlux event loop

Nếu một controller WebFlux cần gọi API đồng bộ tạm thời:

```java
Mono<Product> result = Mono
        .fromCallable(() -> partner.find(id))
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là **fragment**, không phải cách dùng được khuyến nghị lâu dài nếu có WebClient. `boundedElastic` cô lập blocking call nhưng vẫn có thread/queue capacity và cần giới hạn concurrency/timeout. Nếu project đã thuần reactive, `WebClient` không chặn thread sẽ phù hợp hơn.

## 18. Retry, idempotency và status trong tích hợp hệ thống

Một API có thể trả 429/503/504, nhưng việc retry cần xem method và side effects:

| Method/case | Retry nên làm thế nào |
| --- | --- |
| GET idempotent, transient 503 | Có thể retry với budget/backoff/jitter |
| HEAD, transient network failure | Có thể retry nếu an toàn và server policy cho phép |
| POST tạo tài nguyên | Chỉ retry khi có idempotency key/deduplication hoặc contract rõ |
| PUT idempotent theo định nghĩa endpoint | Có thể retry với điều kiện identity/version/ETag |
| DELETE | Xem semantics/not-found state và idempotency thực tế |
| 400 malformed input | Không retry cùng payload |
| 401 token expired | Refresh token có kiểm soát, không vòng lặp |
| 403 forbidden | Không retry vô hạn |
| 404 missing | Thường domain outcome, trừ eventual consistency policy đặc biệt |
| 409 conflict | Cần reconcile và optimistic locking, không blind retry |
| 429 quota | Tôn trọng Retry-After/rate budgets |
| 503 overload | Backoff; tránh retry storm |

`retrieve`/`exchange` **không tự cung cấp chính sách retry an toàn cho business**. Muốn tự động hóa, xem retry library/circuit breaker như Resilience4j, nhưng phải thiết kế idempotency, tracing, timeouts, hedging và max attempts.

### 18.1. Khi có HTTP 202

Response 202 có thể chứa một job ID hoặc Location của status endpoint. Client cần polling với bounded cadence hoặc callback/webhook, không coi 202 là kết quả final. Đọc `Retry-After` như hint nếu được hỗ trợ. Nếu polling dùng `RestClient` blocking, đặt scheduler và giới hạn số jobs phù hợp.

## 19. Quan sát/metric/logging khi đọc status/header/body

Nên thu thập:
- Logical downstream service name (cardinality thấp).
- Route template hoặc operation name, method.
- Status family 2xx/4xx/5xx và error code chuẩn hóa.
- Latency p50/p95/p99 và timeout/retry count.
- HTTP client connection pool pending/active nếu có.
- Downstream request ID đã kiểm tra/sanitize.
- Body size/chunk counts khi có quan sát phù hợp.

Không log:
- Authorization, Proxy-Authorization, Cookie, Set-Cookie.
- Raw query strings nếu chứa tokens/PII.
- Body JSON có password, email, số điện thoại, token.
- Raw error HTML/stack traces do partner trả.
- URI với credentials nhúng.
- Metrics tags lấy trực tiếp từ product ID/user ID tạo cardinality không giới hạn.

Trong Spring Boot, inject builder giúp giữ nhiều customizations/observability. Khi tạo request factory riêng, phải test xem interceptors, TLS/proxy settings và tracing còn được áp không.

## 20. Bảo mật: SSRF, redirects, response limits, schema trust

### 20.1. SSRF với URI từ input

Nếu code xây URI từ một tham số user:

```java
String url = request.getParameter("url");

http.get()
    .uri(url)
    .retrieve()
    .body(String.class);
```

Đây là **anti-pattern**; user có thể cố buộc server gọi metadata service, loopback hoặc hosts nội bộ. Ngay cả có baseUrl, một absolute URL truyền vào `uri(...)` có thể thay host. Sử dụng allowlist base hosts, URI template path/query variables, DNS/IP/redirect policy, không cho client chọn scheme/host tùy ý.

### 20.2. HTTP redirects có thể làm leak credentials

Kiểm soát redirect following theo request factory; không cho bearer token, cookies hoặc client certificates được chuyển sang host không đáng tin. Nếu partner trả Location bất thường, xác minh scheme/hostname/port/path trước khi follow; không mặc định GET Location an toàn.

### 20.3. Response size và decompression

Server có thể trả payload rất lớn hoặc nén mạnh. `body(String.class)`/`byte[].class`/`List<Product>` có thể aggregate dữ liệu gây OOM. Giới hạn bytes sau decompression, record counts, JSON depth và concurrency; một check Content-Length trước khi đọc **không đủ**.

### 20.4. JSON object hợp lệ vẫn có thể sai nghiệp vụ

Jackson decode thành Product không kiểm chứng authorization, allowed identifiers hoặc business constraints. Validate fields/schema/server provenance theo contract. Không tự động persist mọi trường remote trả về vào entity có quyền/role/tenant ID.

## 21. RestClient vs RestTemplate vs WebClient

| Đặc điểm | RestClient | RestTemplate | WebClient |
| --- | --- | --- | --- |
| Mô hình | Đồng bộ, fluent | Đồng bộ, template methods | Reactive, non-blocking |
| Từ Spring Framework | 6.1+ | Đã có lâu | 5+ |
| Đọc body | `retrieve().body(Type)` | `getForObject(...)`, `exchange(...)` | `retrieve().bodyToMono(Type)` |
| Đọc status + headers | `retrieve().toEntity(Type)` | `getForEntity` / `exchange` | `retrieve().toEntity(Type)` trả `Mono<ResponseEntity<T>>` |
| Truy cập raw response | `exchange(callback)` | `execute(callbacks)` | `exchangeToMono/Flux` |
| Default 4xx/5xx | Throw ở terminal retrieve | `ResponseErrorHandler` | Error signal của Mono/Flux |
| Phạm vi status handler | `onStatus` cho retrieve | `setErrorHandler` | `onStatus` cho retrieve |
| Trả dữ liệu | T / ResponseEntity<T> | T / ResponseEntity<T> | Mono<T> / Flux<T> |
| Thread blocking | Có | Có | Không chặn I/O theo thiết kế |
| Môi trường điển hình | MVC/scheduled sync clients | Legacy MVC code | WebFlux/reactive integrations |

**Đặc biệt:** `RestTemplate.exchange(...)` và `RestClient.exchange(...)` trùng tên nhưng khác signatures và default error handling. `WebClient.exchangeToMono(...)` cũng không giống `RestClient.exchange(...)` về callback typing và body/resource lifecycle.

### 21.1. Migration RestTemplate sang RestClient

| RestTemplate cũ | RestClient tương đương |
| --- | --- |
| `getForObject(url,Product.class)` | `get().uri(url).retrieve().body(Product.class)` |
| `getForEntity(url,Product.class)` | `get().uri(url).retrieve().toEntity(Product.class)` |
| `postForEntity(url,input,Product.class)` | `post().uri(url).body(input).retrieve().toEntity(Product.class)` |
| `delete(url)` | `delete().uri(url).retrieve().toBodilessEntity()` |
| `exchange(url,GET,entity,Product.class)` | `method(GET).uri(url).headers(...).retrieve().toEntity(Product.class)` |
| `execute(url,method,callback,extractor)` | `method(method).uri(url).exchange(exchangeFunction)` |

Migration không phải chỉ đổi API names. Phải giữ auth headers, error mapping, pooling/timeouts, URI encoding, interceptors và testing contract.

## 22. Sơ đồ quyết định thực hành

```text
Cần gọi API đồng bộ?
  |
  +-- Không -> dùng WebClient / reactive client phù hợp
  |
  +-- Có -> dùng RestClient
              |
              +-- Chỉ cần body thành công?
              |      |
              |      +-- yes -> retrieve().body(Type)
              |
              +-- Cần cả status + headers + body DTO?
              |      |
              |      +-- yes -> retrieve().toEntity(Type)
              |
              +-- Không dùng body nhưng cần status/Location?
              |      |
              |      +-- yes -> retrieve().toBodilessEntity()
              |
              +-- Cần custom 404/409 -> domain exception?
              |      |
              |      +-- yes -> retrieve().onStatus(...)
              |
              +-- Cần mọi status làm value,
              |   hoặc raw body stream/conditional extraction?
                     |
                     +-- yes -> exchange(callback)
                                  |
                                  +-- tự kiểm status
                                  +-- giới hạn body
                                  +-- hoàn tất read trong callback
                                  +-- không leak resource
```

## 23. Các lỗi phổ biến và cách khắc phục

| Biểu hiện | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Gọi retrieve nhưng server không nhận request | Chưa gọi terminal operation | `body/toEntity/toBodilessEntity` |
| 404 mà code không vào `if(status==404)` | `retrieve` ném default exception | `onStatus` hoặc `exchange` |
| `exchange` trả 503 mà app coi thành success | `exchange` không áp default status handlers | Kiểm tra status tường minh |
| Không đọc được Location 201 | Chỉ gọi `body(Product.class)` | `toEntity` hoặc `toBodilessEntity` |
| Generic JSON list bị map thành LinkedHashMap | Dùng raw `List.class` | `ParameterizedTypeReference<List<Product>>` |
| `body(...)` trả null trên 204 | Không có response body | Dùng `ResponseEntity<Void>`, xử lý empty |
| Mất correlation ID | Chỉ lấy body | Đọc `getHeaders().getFirst(...)` |
| HTTP 429 bị coi là product missing | Status mapping quá rộng | Typed 429/rate limit policy |
| I/O exception bị gán 503 của server | Chưa chắc có HTTP response | Phân biệt `ResourceAccessException` |
| Read body sau `exchange` callback lỗi | Response tự đóng | Đọc/copy trong callback |
| Download file lớn gây OOM | `body(byte[].class)`/readAllBytes | Stream bounded với exchange hoặc HTTP client phù hợp |
| Retry POST tạo trùng resource | Timeout nhưng server đã commit | Idempotency key / dedupe |
| Header Retry-After không parse được | Giả định luôn là số | Hỗ trợ seconds hoặc HTTP-date |
| ETag cache trả sai giữa tenants | Cache key thiếu auth/Vary | Thiết kế cache key/policy |
| Test slice không inject service | `@RestClientTest` chỉ scan scope hẹp | Chỉ định component/`@Import` |
| HTTPS thật lỗi dù MockRestServiceServer pass | Mock không test TLS/transport | Real transport integration tests |
| WebFlux lag nặng khi gọi RestClient | Blocking trên event loop | `WebClient` hoặc boundedElastic tạm thời |
| Authorization header xuất hiện trong logs | Logging không redacted | Sanitize request/response logs |
| URI partner dùng user input host | SSRF | Allowlist scheme/host/IP/redirect policy |
| Connection pool cạn | Timeout lớn/response chưa đóng | Correct resource ownership, pool metrics |
| `onStatus` có nhưng exchange không dùng | Status handlers của retrieve không áp trong exchange | Kiểm tra status ngay callback |
| Nâng Spring major, method overload khác | API versions thay đổi | Đọc Javadoc/BOM phù hợp và chạy tests |

### 23.1. Debug theo thứ tự

1. Xác nhận runtime thực là Spring Framework 6.2/Spring Boot 3.5 và loại client `RestClient`.
2. Kiểm tra `baseUrl` + template variables + method/path/query/headers.
3. Bảo đảm terminal operation đã được gọi.
4. Xác định đang dùng `retrieve` hay `exchange` để hiểu error policy.
5. Kiểm tra HTTP status/headers trước khi kết luận body semantics.
6. Với 4xx/5xx, xem typed exception hoặc callback status policy.
7. Với network fail, xem DNS/TLS/connect/read timeout và request factory.
8. Với body null/JSON lỗi, xem status 204/304, Content-Type/schema.
9. Với file lớn, kiểm tra aggregate bytes, direct memory, resource closure.
10. Với tests, dùng MockRestServiceServer cho logic và real HTTP transport integration cho network.

## 24. Checklist trước khi tích hợp production

- [ ] Chọn đúng `RestClient` cho yêu cầu đồng bộ và `WebClient` khi cần reactive non-blocking.
- [ ] Inject Boot `RestClient.Builder` nếu cần auto-configured customizations.
- [ ] Tái sử dụng RestClient instance; không tạo client/connection vô hạn theo request.
- [ ] Gọi terminal operation sau `retrieve()`.
- [ ] Dùng `toEntity` nếu cần cả status, header, body.
- [ ] Dùng `toBodilessEntity` cho 201/202/204 không cần body.
- [ ] Hiểu `retrieve` mặc định throw 4xx/5xx; áp `onStatus` theo business.
- [ ] Nếu dùng `exchange`, tự phân loại status và đọc body trong callback.
- [ ] Không trả raw InputStream/ClientHttpResponse khỏi callback mặc định.
- [ ] Không đọc file lớn vô hạn vào `byte[]`/String/List.
- [ ] Dùng `ParameterizedTypeReference` cho generic JSON bodies.
- [ ] URI variables được encode và base URL/redirect theo allowlist chống SSRF.
- [ ] Timeout kết nối/đọc/deadline/pool có cấu hình và tests riêng.
- [ ] HTTP retry có max attempts/backoff/jitter/idempotency.
- [ ] Phân biệt 401/403/404/409/429/500/503 và I/O failures.
- [ ] Parse Retry-After đúng cả delta-seconds và HTTP-date nếu sử dụng.
- [ ] Kiểm tra ETag/304/cache Vary/auth scope đúng.
- [ ] Không log Authorization/cookies/PII/raw error body.
- [ ] Test with @RestClientTest + MockRestServiceServer cho success/errors/headers/body.
- [ ] Có integration tests với TLS/proxy/connection pools/real transport khi cần.
- [ ] Spring version/migration API và các header semantics được review sau nâng cấp.

## 25. Bài tập tự đánh giá

1. `RestClient.retrieve()` có phát HTTP request ngay không? Phương thức nào là terminal?
2. Vì sao chỉ dùng `body(Product.class)` chưa đủ khi API tạo trả 201 + Location?
3. Viết đoạn `retrieve().toEntity(Product.class)` lấy ETag, Content-Type và X-Request-ID.
4. Phân biệt `toBodilessEntity()` với `body(Void.class)` trong tình huống cần status/Location.
5. Vì sao `ResponseEntity<Product>.getBody()` có thể null dù status là 204?
6. API trả JSON array, dùng `ParameterizedTypeReference<List<Product>>` thay `List.class` để giải quyết lỗi nào?
7. Khi retrieve gặp 404, code phía sau `toEntity` có chạy bình thường không?
8. Thêm `onStatus` cho 404 thành ProductMissingException, giữ 401/429 theo default.
9. Gọi `exchange` với response 503. Vì sao không có default HttpServerErrorException?
10. Xây `Probe` chứa status, requestId và body text, có cap 1024 bytes trong callback.
11. Nếu trả `InputStream` từ `exchange` callback, chuyện gì có thể xảy ra khi caller đọc sau đó?
12. Khi cần xử lý file nhiều GB, tại sao `body(byte[].class)` không an toàn?
13. So sánh ý nghĩa `HttpHeaders#getFirst` với `get` khi header có nhiều giá trị.
14. Server trả `Retry-After: Wed, 21 Oct 2026 08:00:00 GMT`, parse int có được không?
15. Khi dùng ETag và 304, vì sao phải quan tâm Vary và tenant/auth cache key?
16. Nếu `RestClient` báo `ResourceAccessException` trước HTTP response, lấy status code từ đâu?
17. Một POST nhận timeout nhưng server có thể đã tạo resource. Nên retry thế nào?
18. Phân biệt 429 theo tenant quota với 503 downstream overload khi thiết kế retry.
19. Vì sao test `@RestClientTest` không bảo đảm DNS/TLS/redirect đúng ở production?
20. Dùng MockRestServiceServer test default 404, custom onStatus 404 và exchange 404 như ba trường hợp khác.
21. Một REST API trả body là HTML mặc dù Accept JSON. Client cần xem những thông tin nào trước khi deserialize?
22. Xây `RestClient` bằng Boot-provided Builder có lợi gì so với `RestClient.create()` trong ứng dụng lớn?
23. Nếu ứng dụng WebFlux dùng RestClient trên Netty event loop, triệu chứng và giải pháp nào?
24. Phân biệt `RestTemplate.exchange(...)` và `RestClient.exchange(...)` theo signatures và error policy.
25. Thêm timeout chính xác cho HTTP backend bạn dùng, và test connect timeout/read timeout bằng real mock server.
26. Thiết kế circuit breaker/retry policy cho partner API có 200, 404, 429, 503 và network timeout.
27. Nếu Location response trỏ sang `http://127.0.0.1/...`, vì sao không tự động follow?
28. Dùng status/headers/body trong một object ứng dụng mà không cho raw headers chứa secrets bị trả lại frontend.
29. Hãy viết test chứng minh response body 1025 bytes bị `OversizedProbeException` từ bounded read.
30. Khi nâng từ Spring Boot 3.5 lên Boot 4, cần xác minh những APIs, starters, test imports và request factories nào?

## 26. Tài liệu chính thức

- [Spring Framework 6.2 — REST Clients reference](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html): RestClient synchronous API, retrieve/body/toEntity, status/error handling, exchange và so sánh RestTemplate.
- [Spring Framework 6.2 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.html): builder, fluent API.
- [Spring Framework 6.2 — RestClient.ResponseSpec Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.ResponseSpec.html): `body`, `toEntity`, `toBodilessEntity`, `onStatus`.
- [Spring Framework 6.2 — RestClient.RequestHeadersSpec Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.RequestHeadersSpec.html): `retrieve`, `exchange`, response lifecycle.
- [Spring Framework 6.2 — HttpHeaders Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/HttpHeaders.html): ETag, Location, Content-Type, contentLength, retry semantics.
- [Spring Framework 6.2 — ResponseEntity Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ResponseEntity.html): status, headers, body.
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html#rest-message-conversion): converter selection và JSON mapping.
- [Spring Boot 3.5 — RestClientTest](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html): `@RestClientTest` và MockRestServiceServer.
- [Spring Framework — MockRestServiceServer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html): request expectations, fake responses và verify.
- [Spring Framework — Client Request Factories](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html#rest-client-request-factories): request transport/timeouts.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): response status, Location, Retry-After, ETag và caching semantics.
- [Spring Framework — Reactive WebClient](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient.html): khi cần asynchronous/non-blocking I/O.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-005">HttpMessageConverter, kiểu generic và ParameterizedTypeReference</a>

<details>
<summary>Xem chi tiết</summary>

# HttpMessageConverter, kiểu generic và ParameterizedTypeReference trong HTTP client đồng bộ

## 1. Học gì qua bài này?

Trong ứng dụng Spring, một lệnh HTTP không chỉ gửi/nhận bytes. Spring cần biết **kiểu dữ liệu Java đích** và **media type của HTTP body** để chọn bộ chuyển đổi từ JSON, text, byte array, XML hoặc form thành Java object. Khi API trả `List<Book>` hay `ApiEnvelope<List<Book>>`, kiểu `Class<List>` không đủ mô tả phần tử bên trong do **Java type erasure**. Vì thế, Spring cung cấp **`ParameterizedTypeReference<T>`** để giữ metadata kiểu generic tại runtime cho các APIs HTTP tương thích.

Chủ đề này nằm trong nhóm **HTTP client đồng bộ**. Trọng tâm là:

- **`RestClient`** (Spring Framework 6.1+, API fluent synchronous; lựa chọn hiện đại trong Spring 6.2/Boot 3.5).
- **`RestTemplate`** (API synchronous lâu đời, vẫn dùng nhiều trong dự án).
- **`HttpMessageConverter`**, **`GenericHttpMessageConverter`** và **`MappingJackson2HttpMessageConverter`** cho Java ↔ HTTP body.
- Khác biệt giữa `Class<T>`, `Type`, `ParameterizedTypeReference<T>`, `ResolvableType` và Jackson `TypeReference` (không phải cùng class).
- Request `Content-Type` so với `Accept`, response `Content-Type`, lỗi status, lỗi converter, DTO mapping và nested generics.
- Ví dụ **Java 17+, Spring Framework 6.2, Spring Boot 3.5** có thể kiểm thử độc lập bằng **`MockRestServiceServer`**, không gọi Internet thật.

**Kết quả kỳ vọng:** đọc JSON `[{"id":1,"title":"Spring"}]` thành **`List<Book>`** đúng kiểu; đọc `ApiEnvelope<List<Book>>` vẫn giữ kiểu `Book`; biết chẩn đoán lỗi `LinkedHashMap cannot be cast to Book`, sai media type và lỗi body serialization.

### 1.1. Phạm vi chính xác

`RestClient` và `RestTemplate` là **blocking/synchronous HTTP clients**. Converter ở đây thường đọc/ghi qua `InputStream`/`OutputStream`. **`WebClient`** là reactive client dùng `HttpMessageReader`/`HttpMessageWriter` và codecs; nó cũng có overload `ParameterizedTypeReference` nhưng **không sử dụng chính `HttpMessageConverter` synchronous theo cùng pipeline**. Đừng trộn hai cơ chế khi cấu hình.

Bài dùng ví dụ JSON vì phổ biến; `HttpMessageConverter` không đồng nghĩa “Jackson converter duy nhất”. Một request có thể đi qua String, byte array, form, XML hoặc converter tùy biến.

## 2. Cơ chế HTTP Message Conversion end-to-end

~~~text
Java request object / HttpEntity
          |
          | RestClient.body(obj) hoặc RestTemplate.exchange(...)
          v
Request headers: Content-Type / Accept
          |
          v
HttpMessageConverter.canWrite(targetType, mediaType)
          |
          v
HttpMessageConverter.write(...) -> HTTP bytes
          |
          v
Remote HTTP server
          |
          v
Status + headers + HTTP response body
          |
          v
Requested response Java type (Class<T> / Type via ParameterizedTypeReference)
          |
          v
Converter.canRead(targetType, response Content-Type)
          |
          v
Converter.read(...) -> Java value
~~~

Trong request/response đồng bộ, thread gọi phương thức `retrieve().body(...)` hoặc `exchange(...)` thường **chờ hoàn thành I/O/đọc body**. Có thể đặt thread pool, timeouts, connection pooling và concurrency budgets; không nên gọi blocking REST client trên WebFlux event-loop thread.

**Đường gửi và đường nhận độc lập:**
- **Request write**: object Java → bytes. Spring chọn converter theo **kiểu request body**, `Content-Type`, danh sách converters.
- **Response read**: bytes → object Java. Spring cần **kiểu đích** và **response `Content-Type`** để chọn converter.
- HTTP status (2xx/4xx/5xx) được giải quyết bởi client/error handler trước hoặc bên cạnh body conversion tùy API/nhánh gọi.

### 2.1. RestClient không gửi request chỉ vì gọi retrieve()

Trong Spring 6.2:

~~~java
RestClient.ResponseSpec spec = client.get()
        .uri("/books")
        .retrieve();

// Terminal operation mới thực sự đọc phản hồi:
List<Book> books = spec.body(BOOK_LIST);
~~~

Đây là snippet minh họa: `client`, `Book`, `BOOK_LIST` phải được khai báo. Spring docs nêu `retrieve()` tự nó chưa tạo side effect; phương thức terminal `body(...)`, `toEntity(...)`, `toBodilessEntity()` mới thực thi request. Không giả định response được gọi ngay sau `retrieve()`.

Với RestTemplate, lời gọi như `getForObject` hoặc `exchange` thực thi trong chính phương thức đó.

## 3. HttpMessageConverter là gì?

`org.springframework.http.converter.HttpMessageConverter<T>` là interface Spring để đọc/ghi HTTP body:

~~~java
boolean canRead(Class<?> clazz, MediaType mediaType);
boolean canWrite(Class<?> clazz, MediaType mediaType);
List<MediaType> getSupportedMediaTypes();
T read(Class<? extends T> clazz, HttpInputMessage inputMessage);
void write(T t, MediaType contentType, HttpOutputMessage outputMessage);
~~~

Đây là **chữ ký đã giản lược** nhằm minh họa, không phải source nguyên vẹn của interface trong mọi version (nullable annotations, overloads và declared exceptions được lược bỏ).

Các converter thường gặp:

| Converter | Đầu vào/đầu ra | Media type tiêu biểu | Điểm cần nhớ |
| --- | --- | --- | --- |
| `StringHttpMessageConverter` | `String` ↔ text | `text/plain`, `text/*` | Không parse JSON thành DTO dù chuỗi chứa JSON |
| `ByteArrayHttpMessageConverter` | `byte[]` ↔ bytes | `*/*`, thường ghi octet-stream | Dùng cho binary nhỏ đã có giới hạn |
| `ResourceHttpMessageConverter` | `Resource` ↔ stream/file response | Tùy resource/media type | Không coi đây là parser JSON |
| `FormHttpMessageConverter` | Form URL-encoded, ghi multipart | `application/x-www-form-urlencoded`, multipart | Multipart có nhiều parts, cần phân biệt map và file |
| `MappingJackson2HttpMessageConverter` | DTO/collection ↔ JSON | `application/json`, `application/*+json` | Dựa Jackson ObjectMapper và Type metadata |
| `MarshallingHttpMessageConverter` | XML thông qua marshaller | `application/xml`/`text/xml` | Tùy dependencies/config |
| Custom converter | Binary/protobuf/vendor media | Ví dụ `application/x-my-format` | Phải chỉ rõ canRead/canWrite và ordering |

**Không có một danh sách cố định bất biến** cho mọi ứng dụng: classpath, Spring Boot auto-configuration, client builder và custom registration quyết định converter thực tế. Cấu hình Spring MVC server-side và RestClient/RestTemplate client-side có thể khác nhau.

### 3.1. Vì sao GenericHttpMessageConverter quan trọng?

Interface base `HttpMessageConverter` nhận **`Class`**, ví dụ `Book.class` hoặc `List.class`. `Class<List>` không thể biểu diễn kiểu đầy đủ `List<Book>` vì kiểu generic bị erase ở runtime.

`GenericHttpMessageConverter<T>` mở rộng interface này để làm việc với Java reflection **`Type`**:

~~~java
boolean canRead(Type type, Class<?> contextClass, MediaType mediaType);
T read(Type type, Class<?> contextClass, HttpInputMessage inputMessage);
boolean canWrite(Type type, Class<?> clazz, MediaType mediaType);
~~~

Đây cũng là chữ ký giản lược. Nếu converter hỗ trợ generic, nó có thể nhìn thấy `List<Book>`, `Map<String, List<Book>>` hay `ApiEnvelope<List<Book>>` thay vì chỉ thấy raw `List`.

**Nhưng:** `ParameterizedTypeReference` chỉ **mang thông tin type**; nó không tự parse JSON. Cần một converter thực sự hiểu media type và generic shape, thường là Jackson JSON converter trên classpath. Nếu response là `text/html` hoặc service trả HTML error page thì một type reference đúng vẫn không thể biến HTML thành `Book`.

## 4. Content-Type, Accept và response type: ba khái niệm khác nhau

| Header/API | Nằm ở đâu | Ý nghĩa |
| --- | --- | --- |
| Request `Content-Type: application/json` | Request từ client | Bytes gửi lên đang có định dạng JSON |
| Request `Accept: application/json` | Request từ client | Client muốn nhận response kiểu JSON |
| Response `Content-Type: application/json` | Response từ server | Bytes phản hồi thực tế là JSON |
| `body(Book.class)` | Java client code | Mong parse body thành Book |
| `body(new ParameterizedTypeReference<List<Book>>() {})` | Java client code | Mong parse body thành List có phần tử Book |

**Ví dụ POST:**

~~~java
Book result = client.post()
        .uri("/books")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(new Book(3L, "Reactive"))
        .retrieve()
        .body(Book.class);
~~~

Đây là snippet có `client` đã cấu hình. Converter **ghi JSON** từ Book cho request, server xử lý, converter **đọc JSON** từ response trở lại Book. `Accept` không tự đặt request body `Content-Type`, và `Content-Type` của request không thể ép remote server phải trả JSON.

Khi server trả JSON nhưng khai `Content-Type: text/plain`, việc chọn converter có thể khác; đừng sửa vấn đề bằng cách ép `List.class` hay bỏ validation. Hãy sửa HTTP contract ở upstream hoặc cấu hình converter custom có chủ đích, với test đầy đủ.

## 5. Vấn đề Java type erasure và List.class

Giả sử upstream trả:

~~~json
[
  {"id": 1, "title": "Spring"},
  {"id": 2, "title": "Jackson"}
]
~~~

Bạn muốn `List<Book>` nhưng viết:

~~~java
List<?> books = client.get()
        .uri("/books")
        .retrieve()
        .body(List.class);
~~~

Jackson chỉ nhận raw `List`; các phần tử JSON object thông thường thành `LinkedHashMap`, không phải `Book`. Nếu ép:

~~~java
@SuppressWarnings("unchecked")
List<Book> wrong = (List<Book>) (List<?>) books;
Book first = wrong.get(0);  // Có thể ClassCastException
~~~

Bộ biên dịch không bảo đảm phần tử thật là Book khi generic type bị erase, nên lỗi thường chỉ xuất hiện lúc lấy/ép phần tử.

### 5.1. Vì sao không có `List<Book>.class`?

Trong Java, class literal chỉ viết được `Book.class`, `List.class`, không thể `List<Book>.class`:

~~~java
Class<Book> one = Book.class;
Class<List> many = List.class;
// Class<List<Book>> typed = List<Book>.class; // Java syntax invalid
~~~

Không phải Spring bỏ quên overload: Java không có class literal cho một **parameterized type**. Muốn giữ parameter `Book` phải truyền Java `Type` hoặc một object có thể giữ Type.

### 5.2. Trường hợp object không generic

Nếu phản hồi duy nhất là:

~~~json
{"id":1,"title":"Spring"}
~~~

thì `Book.class` là đủ:

~~~java
Book book = client.get()
        .uri("/books/1")
        .retrieve()
        .body(Book.class);
~~~

Không bắt buộc dùng `ParameterizedTypeReference<Book>` cho DTO không generic. Dùng type reference chỉ khi cần biểu diễn `List<Book>`, `Page<Book>`, `Map<String, Book>`, nested envelopes hoặc type variable đã được resolve.

## 6. ParameterizedTypeReference<T>: ghi lại Type đầy đủ

`org.springframework.core.ParameterizedTypeReference<T>` dùng dạng **anonymous subclass** để nhận thông tin generic từ superclass signature:

~~~java
ParameterizedTypeReference<List<Book>> type =
        new ParameterizedTypeReference<List<Book>>() {};
~~~

**Hai dấu ngoặc nhọn `{}` ở cuối là quan trọng**: bạn đang tạo một anonymous subclass mà superclass mang type parameter `List<Book>`; Spring dùng reflection để nắm bắt kiểu đích.

Sau đó RestClient:

~~~java
List<Book> books = client.get()
        .uri("/books")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(type);
~~~

Hoặc lấy status, headers và body:

~~~java
ResponseEntity<List<Book>> response = client.get()
        .uri("/books")
        .retrieve()
        .toEntity(type);

HttpStatusCode status = response.getStatusCode();
List<Book> list = response.getBody();
~~~

`ResponseEntity.getBody()` có thể trả **null** khi body không tồn tại hoặc contract cho phép, nên đừng dereference `response.getBody().size()` nếu không có null policy. `retrieve().body(type)` cũng có thể trả null trong một số trường hợp 204/empty body.

### 6.1. Nested generic

Một API dùng envelope:

~~~json
{
  "data": [
    {"id":1,"title":"Spring"},
    {"id":2,"title":"Jackson"}
  ],
  "traceId": "rid-42"
}
~~~

Với DTO:

~~~java
public record ApiEnvelope<T>(T data, String traceId) {}
~~~

Type phải giữ được **cả hai lớp generic**:

~~~java
ParameterizedTypeReference<ApiEnvelope<List<Book>>> type =
        new ParameterizedTypeReference<ApiEnvelope<List<Book>>>() {};

ApiEnvelope<List<Book>> envelope = client.get()
        .uri("/books/envelope")
        .retrieve()
        .body(type);
~~~

Nếu chỉ truyền `ApiEnvelope.class`, Jackson có thể giải mã `data` thành raw `List<LinkedHashMap>`, làm mất tầng `Book`.

### 6.2. Generic không chỉ là List

Ví dụ:

~~~java
ParameterizedTypeReference<Map<String, List<Book>>> mapType =
        new ParameterizedTypeReference<Map<String, List<Book>>>() {};

ParameterizedTypeReference<List<ApiEnvelope<Book>>> listEnvelopeType =
        new ParameterizedTypeReference<List<ApiEnvelope<Book>>>() {};
~~~

Lưu ý `Map<String, List<Book>>` cần JSON object có property names ứng với String keys, còn `List<ApiEnvelope<Book>>` cần JSON array mà mỗi phần tử có cấu trúc ApiEnvelope. **Metadata type đúng không bù cho JSON shape sai**.

### 6.3. Không capture type variable chưa resolve như một kiểu cụ thể

Anti-pattern trong utility generic:

~~~java
public <T> T fetch(String path) {
    ParameterizedTypeReference<T> type =
            new ParameterizedTypeReference<T>() {};
    return client.get().uri(path).retrieve().body(type);
}
~~~

Đây là method minh họa nằm trong một class có `RestClient client`. `T` là **type variable** bị erase, nên anonymous subclass ở đây không thể biết caller muốn `List<Book>` hay `ApiEnvelope<Book>` thực sự. Không nên tin rằng `T` sẽ tự được suy ra đầy đủ tại runtime.

Cách đúng là caller cung cấp **type token đã resolve**:

~~~java
public <T> T fetch(String path, ParameterizedTypeReference<T> type) {
    return client.get().uri(path).retrieve().body(type);
}
~~~

Khi gọi:

~~~java
List<Book> books = fetch(
        "/books", new ParameterizedTypeReference<List<Book>>() {});
~~~

Hoặc nếu cần runtime composition, sử dụng `ParameterizedTypeReference.forType(Type)` với Java reflection Type được xây chính xác. Không tự tạo `List<T>` trên type variable chưa được resolve.

### 6.4. `Type` và `ParameterizedTypeReference.forType`

Khi một thư viện đã tạo `java.lang.reflect.Type` từ method generic thật, bạn có thể bridge:

~~~java
Type type = SomeApi.class
        .getDeclaredMethod("listBooks")
        .getGenericReturnType();

ParameterizedTypeReference<?> ref =
        ParameterizedTypeReference.forType(type);
~~~

Đây là **snippet minh họa**; `SomeApi` phải có method phù hợp và phải xử lý `NoSuchMethodException`. Nếu `listBooks` trả `List<Book>` thật, reflection Type chứa type đó. Nếu method trả type variable `T` mà chưa được resolve trong context, `forType` không thể tự đoán Book.

Spring còn có `ResolvableType` để phân tích generic type hierarchies và kết hợp Type metadata. Nó không phải JSON decoder.

## 7. RestClient: lời gọi typed response

`RestClient` được thêm từ Spring Framework 6.1, vì vậy ví dụ áp dụng trực tiếp với **Spring Framework 6.2 / Spring Boot 3.5**; không giả định tương thích Spring Boot 3.0 (Spring Framework 6.0).

Tạo client:

~~~java
RestClient client = RestClient.builder()
        .baseUrl("https://api.example.com")
        .defaultHeader("X-App", "demo")
        .build();
~~~

`api.example.com` là hostname ví dụ, không có service thật. `RestClient` sau khi build có thể tái sử dụng giữa threads; không tạo một client mới cho mỗi request nếu cần connection pooling/consistent configuration.

**Gọi GET với generic:**

~~~java
ParameterizedTypeReference<List<Book>> type =
        new ParameterizedTypeReference<>() {};

List<Book> books = client.get()
        .uri("/books")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(type);
~~~

**Lấy response entity:**

~~~java
ResponseEntity<List<Book>> entity = client.get()
        .uri("/books")
        .retrieve()
        .toEntity(type);
~~~

**Gọi POST body DTO:**

~~~java
Book created = client.post()
        .uri("/books")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Book(3L, "REST"))
        .retrieve()
        .body(Book.class);
~~~

**Nếu endpoint không trả body:**

~~~java
ResponseEntity<Void> response = client.delete()
        .uri("/books/{id}", 3)
        .retrieve()
        .toBodilessEntity();
~~~

Đừng gọi `body(Void.class)` rồi giả định sẽ nhận một `Void` instance. `toBodilessEntity()` rõ nghĩa hơn nếu chỉ cần status/headers.

### 7.1. RestClient retrieve() và exchange() không giống nhau

- `retrieve()` dùng các thao tác terminal để giải mã theo converter, kèm status handlers phù hợp. Theo mặc định, các status 4xx/5xx có thể dẫn tới `RestClientResponseException` trước khi business xử lý body thành DTO thành công.
- `exchange((request, response) -> ...)` cung cấp mức thao tác thấp hơn với HTTP response và streams, phù hợp xử lý response custom/streaming/advanced. Khi dùng low-level exchange callback, **default status handlers của retrieve không tự áp dụng theo cùng cách**; caller phải chủ động kiểm tra status/error/cleanup.
- Chỉ `retrieve()` chưa gửi request; `body(...)`, `toEntity(...)`, `toBodilessEntity()` là terminal.

Ví dụ chặn lỗi domain theo status khi dùng retrieve:

~~~java
Book book = client.get().uri("/books/{id}", 99)
        .retrieve()
        .onStatus(status -> status.value() == 404,
                (request, response) -> {
                    throw new IllegalArgumentException("Book not found");
                })
        .body(Book.class);
~~~

Đoạn minh họa dùng client sẵn có. Trong production nên dùng exception/domain policy có HTTP status, request ID và retry rules rõ ràng; không nuốt status 500 rồi chuyển sang empty Book.

## 8. RestTemplate: sử dụng exchange với ParameterizedTypeReference

Dự án cũ thường viết:

~~~java
RestTemplate template = new RestTemplate();
Book book = template.getForObject(
        "https://api.example.com/books/1", Book.class);
~~~

Với generic, `getForObject(url, List.class)` **không mang `Book`**, nên dùng `exchange`:

~~~java
ParameterizedTypeReference<List<Book>> type =
        new ParameterizedTypeReference<>() {};

ResponseEntity<List<Book>> response = template.exchange(
        "https://api.example.com/books",
        HttpMethod.GET,
        null,
        type
);

List<Book> books = response.getBody();
~~~

`RestTemplate.exchange(..., HttpMethod, HttpEntity<?>, ParameterizedTypeReference<T>)` giữ `List<Book>` khi converter hỗ trợ generic. Không có overload `getForObject(url, ParameterizedTypeReference)` giống hệt; chọn đúng API.

Nếu cần request header:

~~~java
HttpHeaders headers = new HttpHeaders();
headers.setAccept(List.of(MediaType.APPLICATION_JSON));
HttpEntity<Void> request = new HttpEntity<>(headers);

ResponseEntity<List<Book>> response = template.exchange(
        "https://api.example.com/books",
        HttpMethod.GET,
        request,
        type
);
~~~

Nếu có request body generic, có thể dùng `RequestEntity` hoặc `HttpEntity<T>`, các overload phù hợp; xem **cả hướng serialize** (request) và deserialize (response).

### 8.1. RestTemplate không “sai”, RestClient không tự “nhanh hơn”

Cả hai là synchronous HTTP clients; RestClient là API fluent mới hơn, RestTemplate có ecosystem rộng và code chạy ổn định. Việc đổi syntax từ RestTemplate sang RestClient **không tự loại bỏ blocking I/O**. Hãy đo latency, request factory, connection pool, retry policy và network trước khi tuyên bố cải thiện hiệu năng.

Dùng `WebClient` khi thật sự cần reactive composition, nhưng không block result trên Reactor event loop. `ParameterizedTypeReference` cũng xuất hiện trong `WebClient.bodyToMono(type)`/`bodyToFlux(type)` nhưng implementation conversion khác.

## 9. RestClient và RestTemplate chọn converter bằng cách nào?

Với response JSON `application/json` và đích `ApiEnvelope<List<Book>>`, client duyệt cấu hình converters để chọn converter có thể đọc kiểu/media type cần thiết. Converter Jackson generic có thể dùng Type đầy đủ để tạo `ApiEnvelope`, `List` và `Book`. Nếu không có Jackson dependency hoặc converter bị xóa khỏi list, parse có thể thất bại dù TypeReference đúng.

Ngược lại, nếu response có `text/plain` và bạn gọi `body(String.class)`, String converter có thể xử lý, không cần Jackson.

### 9.1. Cách kiểm tra converter thực có sẵn

~~~java
RestTemplate template = new RestTemplate();
template.getMessageConverters()
        .forEach(c ->
            System.out.println(c.getClass().getSimpleName()));
~~~

Đây là snippet chạy được trong class Java với imports phù hợp và Spring Web dependencies. Danh sách in ra tùy classpath Spring version; **không gán một stdout cố định**. Khi RestClient được cấu hình thông qua builder, có thể cấu hình list converters bằng callback:

~~~java
RestClient configured = RestClient.builder()
        .messageConverters(converters -> {
            // Quan sát/thay đổi list có chủ đích.
            converters.forEach(c ->
                    System.out.println(c.getClass().getName()));
        })
        .build();
~~~

**Không xóa các converter mặc định mù quáng.** Nếu bạn gọi `converters.clear()` rồi chỉ đăng ký `StringHttpMessageConverter`, kết quả JSON DTO sẽ không được đọc/ghi như trước.

### 9.2. Thứ tự converter và media type

Một custom converter quá rộng (ví dụ `canRead(..., */*)` cho Object) được thêm trước Jackson có thể chặn Jackson và khiến body bị parse sai. Đăng ký media type chính xác, chỉ các classes converter biết xử lý, kiểm thử serialize + deserialize, status + headers. Tránh override `supportedMediaTypes` của Jackson thành `*/*` để “sửa lỗi” upstream gửi sai Content-Type, vì dễ vô tình đọc HTML/error page như JSON và làm giảm khả năng chẩn đoán.

### 9.3. Boot auto-configuration

Spring Boot 3.x có thể cung cấp cấu hình builder, ObjectMapper và customizers theo starters. Nếu dùng **`RestClient.builder()` trực tiếp**, bạn có thể **bỏ qua một số customization** của Boot mà bean `RestClient.Builder` được Boot cấu hình sẵn.

Trong Boot 3.5, pattern:

~~~java
@Configuration
class ClientConfiguration {

    @Bean
    CatalogClient catalogClient(RestClient.Builder builder) {
        RestClient rest = builder
                .baseUrl("https://api.example.com")
                .build();
        return new CatalogClient(rest);
    }
}
~~~

Đây là class minh họa, `CatalogClient` sẽ được định nghĩa ở phần thực hành; URL ví dụ cần thay bằng endpoint tin cậy/cấu hình ở production. Nếu có nhiều RestClient, dùng builder clone/configuration một cách có kiểm soát, không mutate shared builder sau build trong luồng xử lý đồng thời.

## 10. Jackson ObjectMapper và ParameterizedTypeReference: khác nhau thế nào?

**Hai giải pháp khác API layer:**

| Khái niệm | Đơn vị | Mục đích |
| --- | --- | --- |
| `Class<Book>` | Java reflection | Một concrete class |
| `Type`/`ParameterizedType` | Java reflection | Mô tả `List<Book>`, nested generics |
| `ParameterizedTypeReference<T>` | Spring Core | Giữ `Type` qua APIs Spring generic |
| `ResolvableType` | Spring Core | Phân giải/phân tích generics |
| `TypeReference<T>` | Jackson | Giữ generic metadata cho `ObjectMapper.readValue` |
| `JavaType` | Jackson | Mô hình kiểu của Jackson cho deserialization |
| `HttpMessageConverter` | Spring Web | Biến HTTP bytes ↔ Java object |
| `ObjectMapper` | Jackson | JSON ↔ Java object |

Nếu bạn chỉ có JSON string/byte array và tự dùng Jackson:

~~~java
ObjectMapper mapper = new ObjectMapper();

List<Book> books = mapper.readValue(
        json,
        new com.fasterxml.jackson.core.type.TypeReference<List<Book>>() {});
~~~

Đây là snippet với `String json` đã có; khác namespace và use case với Spring `ParameterizedTypeReference`. Khi dùng RestClient/RestTemplate, thông thường **không cần tự `ObjectMapper.readValue`**: để converter xử lý content-type, headers và TypeToken.

Nếu phải chuyển qua JavaType:

~~~java
JavaType type = mapper.getTypeFactory()
        .constructCollectionType(List.class, Book.class);

List<Book> books = mapper.readValue(json, type);
~~~

Đây là ví dụ Jackson thuần ngoài request client abstraction. Trong hệ thống lớn, giữ một `ObjectMapper` đã cấu hình nhất quán cho Java time, naming, modules, date format và polymorphic type restrictions; không tạo Mapper mới cho từng request nếu không cần.

## 11. Thực hành chạy được: Java 17 + Spring Boot 3.5

Bài tập tập trung **synchronous HTTP client**, không cần khởi động REST server thật. `MockRestServiceServer` sẽ đáp ứng những requests được `RestClient` và `RestTemplate` gửi qua mock `ClientHttpRequestFactory`. Nhờ đó có thể test converter/type reference chính xác, không phụ thuộc mạng, token, hostname hay external API.

Dùng **Spring Boot 3.5.x / Spring Framework 6.2.x** và Java 17+, package `com.example.demo`.

### 11.1. Dependencies

Tạo Maven project qua Spring Initializr (Spring Boot 3.5.x, Java 17), chọn:
- Spring Web (để có Spring Web, RestClient/RestTemplate, Jackson JSON).
- Spring Boot Starter Test (JUnit Jupiter, Spring Test/MockRestServiceServer).

Trong file `pom.xml` do Spring Initializr sinh sẵn, phần dependencies có dạng:

~~~xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
~~~

Đây là **một phần** của `pom.xml`, không phải file Maven độc lập. Spring Initializr chịu trách nhiệm parent, Java version, Maven plugins và version management. `spring-boot-starter-web` trong demo bao gồm khả năng chạy Servlet HTTP server nếu cần, nhưng test ở đây dùng **mock outgoing HTTP client**, không cần server lắng nghe socket.

Cấu trúc:

~~~text
src/main/java/com/example/demo/
    DemoApplication.java
    Book.java
    ApiEnvelope.java
    CatalogClient.java
src/test/java/com/example/demo/
    CatalogClientTest.java
    RestTemplateGenericTest.java
~~~

### 11.2. DemoApplication.java

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

Ứng dụng Boot này có thể khởi động bằng `./mvnw spring-boot:run`. Tuy nhiên, **các unit/integration-style client tests không cần chạy DemoApplication**; tạo RestClient và MockRestServiceServer trực tiếp để chỉ kiểm thử bài toán converter.

### 11.3. Book.java

~~~java
package com.example.demo;

public record Book(long id, String title) {}
~~~

Jackson 2.x tích hợp trong Spring Boot 3.5 xử lý Java records chuẩn theo constructors/components, khi JSON keys khớp `id` và `title`.

### 11.4. ApiEnvelope.java

~~~java
package com.example.demo;

public record ApiEnvelope<T>(T data, String traceId) {}
~~~

`T` có thể là `Book`, `List<Book>` hoặc kiểu khác. JSON field `traceId` minh họa metadata response, **không** phải phần của Java generic mechanism.

### 11.5. CatalogClient.java — RestClient dùng type token đúng

~~~java
package com.example.demo;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

public final class CatalogClient {

    private static final ParameterizedTypeReference<List<Book>>
            BOOK_LIST = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<
            ApiEnvelope<List<Book>>> ENVELOPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public CatalogClient(RestClient client) {
        this.client = client;
    }

    public Book findOne(long id) {
        return client.get()
                .uri("/books/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Book.class);
    }

    public List<Book> findAll() {
        return client.get()
                .uri("/books")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BOOK_LIST);
    }

    public ResponseEntity<List<Book>> findAllWithMetadata() {
        return client.get()
                .uri("/books")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(BOOK_LIST);
    }

    public ApiEnvelope<List<Book>> findEnvelope() {
        return client.get()
                .uri("/books/envelope")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ENVELOPE);
    }

    public Book create(Book book) {
        return client.post()
                .uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(book)
                .retrieve()
                .body(Book.class);
    }
}
~~~

Điểm chính:

1. `BOOK_LIST` và `ENVELOPE` là type references **tĩnh, immutable, có type đã resolve**. Tái sử dụng mà không phải tạo anonymous subclass ở mỗi HTTP call.
2. `findOne` dùng `Book.class` vì không có generic.
3. `findAll` dùng `List<Book>` và `findEnvelope` dùng nested `ApiEnvelope<List<Book>>`, tránh `LinkedHashMap`.
4. `findAllWithMetadata` giữ HTTP status/headers trong ResponseEntity; vẫn có thể trả body null theo contract.
5. `create` phải set request `Content-Type` để converter serialize Book sang JSON; `Accept` chỉ mô tả response mong đợi.
6. Caller phải xác định policy khi null body, 404/500, timeout, retries và malformed JSON. Demo không tự retry vì GET/POST có idempotency khác nhau.
7. **RestClient là blocking**, không gọi từ event loop WebFlux nếu không offload.
8. Không có external URL trong class; baseUrl được gắn trong test, nên test deterministic.

### 11.6. CatalogClientTest.java — MockRestServiceServer kiểm thử đúng kiểu

~~~java
package com.example.demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogClientTest {

    MockRestServiceServer server;
    CatalogClient catalog;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.test");

        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new CatalogClient(builder.build());
    }

    @Test
    void singleDtoUsesClass() {
        server.expect(requestTo("https://catalog.test/books/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"id":1,"title":"Spring"}
                        """,
                        MediaType.APPLICATION_JSON));

        Book book = catalog.findOne(1);
        assertNotNull(book);
        assertEquals(1, book.id());
        assertEquals("Spring", book.title());
        server.verify();
    }

    @Test
    void genericListKeepsBookElementType() {
        server.expect(requestTo("https://catalog.test/books"))
                .andRespond(withSuccess(
                        """
                        [{"id":1,"title":"Spring"},
                         {"id":2,"title":"Jackson"}]
                        """,
                        MediaType.APPLICATION_JSON));

        List<Book> books = catalog.findAll();

        assertNotNull(books);
        assertEquals(2, books.size());
        assertEquals(Book.class, books.get(0).getClass());
        assertEquals("Jackson", books.get(1).title());
        server.verify();
    }

    @Test
    void nestedGenericEnvelopeKeepsBookType() {
        server.expect(
                requestTo("https://catalog.test/books/envelope"))
                .andRespond(withSuccess(
                        """
                        {"data":[{"id":7,"title":"Generic"}],
                         "traceId":"rid-42"}
                        """,
                        MediaType.APPLICATION_JSON));

        ApiEnvelope<List<Book>> envelope = catalog.findEnvelope();

        assertNotNull(envelope);
        assertEquals("rid-42", envelope.traceId());
        assertEquals(Book.class, envelope.data().get(0).getClass());
        assertEquals("Generic", envelope.data().get(0).title());
        server.verify();
    }

    @Test
    void responseEntityHasStatusAndTypedBody() {
        server.expect(requestTo("https://catalog.test/books"))
                .andRespond(withSuccess(
                        """
                        [{"id":1,"title":"Spring"}]
                        """,
                        MediaType.APPLICATION_JSON));

        ResponseEntity<List<Book>> response =
                catalog.findAllWithMetadata();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Book.class,
                response.getBody().get(0).getClass());
        server.verify();
    }

    @Test
    void postSerializesRequestAndDeserializesResponse() {
        server.expect(requestTo("https://catalog.test/books"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(
                        MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        """
                        {"id":3,"title":"REST"}
                        """))
                .andRespond(withSuccess(
                        """
                        {"id":3,"title":"REST"}
                        """,
                        MediaType.APPLICATION_JSON));

        Book created = catalog.create(new Book(3, "REST"));

        assertEquals("REST", created.title());
        assertEquals(3, created.id());
        server.verify();
    }

    @Test
    void http404IsNotAnEmptyBook() {
        server.expect(requestTo("https://catalog.test/books/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            """
                            {"message":"not found"}
                            """));

        assertThrows(RestClientResponseException.class,
                () -> catalog.findOne(99));
        server.verify();
    }
}
~~~

**Vì sao test này chứng minh đúng bài?**

- Không gọi `https://catalog.test` qua DNS: `MockRestServiceServer.bindTo(builder)` **gắn mock request factory trước khi `builder.build()`**. Nếu build RestClient trước rồi mới bind, test có thể không sử dụng factory vừa cấu hình.
- Test list kiểm tra **runtime class của phần tử** bằng `Book.class`, không chỉ size hoặc string.
- Test nested generic kiểm tra Book nằm **bên trong** `ApiEnvelope<List<Book>>`.
- Test POST kiểm tra cả **outgoing request `Content-Type` + JSON** và incoming JSON DTO.
- Test HTTP 404 xác minh lỗi status không bị nuốt thành “Book rỗng”.
- `server.verify()` yêu cầu các expected mock HTTP requests được thực hiện.
- JUnit test không phải benchmark performance hay end-to-end API integration thật; nó kiểm tra client conversion code/URI/status bằng mock.
- Các import thừa (nếu IDE báo warning) có thể dọn bằng formatter; với Java compiler bình thường chúng không ngăn biên dịch.

### 11.7. RestTemplateGenericTest.java — tương đương API cũ

~~~java
package com.example.demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestTemplateGenericTest {

    @Test
    void exchangeWithParameterizedTypeReference() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(template).build();

        server.expect(requestTo("https://catalog.test/books"))
                .andRespond(withSuccess(
                        """
                        [{"id":1,"title":"Spring"}]
                        """,
                        MediaType.APPLICATION_JSON));

        ParameterizedTypeReference<List<Book>> type =
                new ParameterizedTypeReference<>() {};

        ResponseEntity<List<Book>> result = template.exchange(
                "https://catalog.test/books",
                HttpMethod.GET,
                null,
                type);

        assertNotNull(result.getBody());
        assertEquals(Book.class,
                result.getBody().get(0).getClass());
        server.verify();
    }
}
~~~

Test cho thấy cả hai synchronous HTTP clients **đều** hỗ trợ đọc generic types khi dùng đúng API và converter.

### 11.8. Chạy kiểm thử

Trong project Maven:

~~~bash
./mvnw test
~~~

Trên Windows PowerShell:

~~~powershell
.\mvnw.cmd test
~~~

Nếu thiếu Maven Wrapper nhưng Maven đã cài: `mvn test`.

Kết quả **mong đợi nếu dependencies/config đã khớp và tests chạy thành công**: 7 tests pass (6 `CatalogClientTest`, 1 `RestTemplateGenericTest`), không có network call bên ngoài. Nếu bạn thêm test class khác hoặc thay version/dependencies thì tổng test count có thể khác. Đừng bịa rằng những tests này đã chạy nếu chưa thực sự chạy trên một project.

Có thể khởi động Boot app:

~~~bash
./mvnw spring-boot:run
~~~

App chỉ là skeleton Spring Boot, **không expose endpoint `/books`** và không thực hiện HTTP requests tự động. Các tests dùng mock server mới cung cấp responses để bảo đảm bài tập độc lập.

## 12. Chẩn đoán lỗi generic sai bằng một test nhỏ

Để thấy `List.class` làm mất kiểu:

~~~java
Object raw = new ObjectMapper().readValue(
        """
        [{"id":1,"title":"Spring"}]
        """,
        List.class);

List<?> list = (List<?>) raw;

System.out.println(list.get(0).getClass().getName());
~~~

Nếu Jackson 2.x mặc định và JSON là array các object như trên, stdout sẽ là:

~~~text
java.util.LinkedHashMap
~~~

Snippet Java cần import `ObjectMapper` và `List`; có thể chạy bằng một method trong project chứa Jackson. Đây là **một thử nghiệm local Jackson**, không cần HTTP, vì lỗi bắt nguồn từ raw generic type chứ không phải từ network.

Dùng đúng type token:

~~~java
List<Book> typed = new ObjectMapper().readValue(
        """
        [{"id":1,"title":"Spring"}]
        """,
        new com.fasterxml.jackson.core.type.TypeReference<List<Book>>() {});
System.out.println(typed.get(0).getClass().getName());
~~~

Kết quả dự kiến:

~~~text
com.example.demo.Book
~~~

Đối với RestClient/RestTemplate, dùng **Spring ParameterizedTypeReference** thay vì Jackson TypeReference, để client chuyển đầy đủ `java.lang.reflect.Type` tới `HttpMessageConverter`.

## 13. Request body có generic: lúc nào cần type token?

Khi **response** là `List<Book>`, type token là điểm mấu chốt để deserializer tạo đúng object elements. Với **request**, generic payload cũng có thể quan trọng khi converter quyết định serialization dựa trên generic type hoặc các type annotations.

Ví dụ request body list:

~~~java
List<Book> books = List.of(
        new Book(1, "Spring"),
        new Book(2, "Jackson"));

ResponseEntity<Void> response = client.post()
        .uri("/books/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(books)
        .retrieve()
        .toBodilessEntity();
~~~

Jackson thường có thể serialize List<Book> đúng dựa vào các **runtime values** của phần tử. Nhưng với container generic phức tạp, polymorphism, empty collection, type-dependent custom serializers hoặc generic envelope, có thể cần chỉ rõ generic type thay vì dựa vào raw runtime class.

Spring RestClient còn có overload `body(Object, ParameterizedTypeReference)` trên request body ở Spring 6.2. Cấu trúc minh họa:

~~~java
ParameterizedTypeReference<List<Book>> type =
        new ParameterizedTypeReference<>() {};

client.post()
        .uri("/books/import")
        .contentType(MediaType.APPLICATION_JSON)
        .body(books, type)
        .retrieve()
        .toBodilessEntity();
~~~

Đoạn cần `RestClient client` đã cấu hình. Type token không đảm bảo remote API chấp nhận JSON đó; vẫn cần schema/validation/status tests.

## 14. MediaType và lỗi converter hay gặp

### 14.1. Server trả Content-Type sai

Server trả:

~~~http
HTTP/1.1 200 OK
Content-Type: text/html

<html>upstream error</html>
~~~

Caller lại:

~~~java
Book book = client.get().uri("/books/1")
        .retrieve().body(Book.class);
~~~

Sẽ không tự có một Book hợp lệ. Spring có thể báo không tìm được converter phù hợp hoặc lỗi decoding khác theo pipeline/version. Hãy log **status, content type, request ID, endpoint** một cách an toàn và sửa upstream contract.

### 14.2. Raw List → LinkedHashMap

Dấu hiệu:

~~~text
java.lang.ClassCastException:
java.util.LinkedHashMap cannot be cast to com.example.demo.Book
~~~

Giải pháp: dùng `ParameterizedTypeReference<List<Book>>` trong `RestTemplate.exchange` hoặc `RestClient.retrieve().body(type)`. Đừng ép unchecked cast hoặc dùng vòng lặp cast trực tiếp Map sang Book.

### 14.3. JSON không khớp DTO

~~~json
{"identifier":1,"name":"Spring"}
~~~

Nhưng DTO cần:

~~~java
public record Book(long id, String title) {}
~~~

Cần map đúng schema, dùng `@JsonProperty` khi cần hoặc cập nhật DTO. Type reference không tự rename fields. Nếu kiểu số vượt range, JSON sai, field bắt buộc thiếu hoặc enum lạ, ObjectMapper/validation vẫn có thể thất bại hay đặt defaults tùy cấu hình.

### 14.4. Không có Jackson trên classpath

Nếu project chỉ có spring-web nhưng không có JSON converter/Jackson, `body(Book.class)` cho JSON có thể không được hỗ trợ theo cách bạn kỳ vọng. Với Spring Boot MVC starter, Jackson thường đi kèm; nếu dùng dependencies thủ công, cần bổ sung và đăng ký converter.

### 14.5. Generic TypeReference sai tầng

Một response `ApiEnvelope<List<Book>>` không thể đọc đúng bằng:

~~~java
new ParameterizedTypeReference<List<Book>>() {}
~~~

vì root JSON là object có `data`, không phải array. Dùng TypeReference **khớp root schema**. Tương tự, `ApiEnvelope<Book>` khác `ApiEnvelope<List<Book>>` ở field data.

### 14.6. 204 No Content

Một response status **204** không có body. `body(Book.class)` không thể “tự tạo Book rỗng”; phải xử lý null/ResponseEntity hoặc dùng `toBodilessEntity()` cho endpoint có contract trả body rỗng.

### 14.7. Error responses không nhất thiết đi qua success DTO converter

Nếu upstream trả status 400 kèm problem JSON, mặc định `retrieve()` có thể ném `RestClientResponseException`. Đừng nghĩ `body(Book.class)` parse được Book từ error body. Cần `onStatus`, `RestClientResponseException`, hoặc API error deserialization phù hợp.

### 14.8. 415 và 406 không phải lúc nào cũng do client-side parser

- **415 Unsupported Media Type** thường xảy ra server không chấp nhận `Content-Type` request/body.
- **406 Not Acceptable** thường xảy ra server không thể sản xuất representation phù hợp với request `Accept`.
- **Client-side converter/decoding error** xảy ra khi client không thể đọc body theo media type/target type, ngay cả status 200.

Phân biệt lỗi ở **bên nào**. Không chữa 415 bằng tăng timeout hoặc chữa 406 bằng đổi `List<Book>` thành `List.class`.

## 15. Cấu hình custom HttpMessageConverter: khi nào nên và không nên?

Nếu API dùng binary/proprietary media type, bạn có thể triển khai converter mới. Nhưng đa số JSON APIs nên dùng Jackson converter sẵn có và tùy chỉnh `ObjectMapper` hợp lý.

Pseudo-pattern minh họa:

~~~java
RestClient custom = RestClient.builder()
        .messageConverters(list -> {
            list.add(0, myConverter);
        })
        .build();
~~~

`myConverter` là instance thật đã implement `HttpMessageConverter<T>`, và phải có media-type class matching chính xác. Không nên đưa custom converter `canRead(Object.class, */*)` trước Jackson.

Nếu muốn custom generic converter, cân nhắc `GenericHttpMessageConverter<T>` hoặc base class phù hợp; xử lý `Type` cùng Jackson/Gson library thay vì **không parse generic element type**.

### 15.1. Cấu hình JSON theo ObjectMapper

Ví dụ minh họa với Jackson:

~~~java
ObjectMapper mapper = new ObjectMapper()
        .findAndRegisterModules();

MappingJackson2HttpMessageConverter converter =
        new MappingJackson2HttpMessageConverter(mapper);
~~~

Đây là snippet trong code có Jackson dependencies/imports. Tạo `ObjectMapper` mới tùy tiện có thể **mất config của Boot**: Java time settings, naming strategies, property inclusion, unknown fields policies, custom serializers, security restrictions. Khi ở Spring Boot, ưu tiên dùng `ObjectMapper` bean đã được Boot customize hoặc `Jackson2ObjectMapperBuilder`/customizer thích hợp.

Nếu dùng `@JsonTypeInfo`/polymorphic deserialization, cần kiểm soát trusted subtypes và tránh cấu hình Jackson default typing rộng cho dữ liệu không tin cậy. `ParameterizedTypeReference` không là cơ chế bảo mật polymorphism.

### 15.2. Không tùy tiện sửa converters của tất cả clients

Mỗi RestClient/RestTemplate có cấu hình message converters riêng; thay converter list ở một nơi không mặc định thay toàn bộ clients khác. Spring MVC server dùng converters qua request mapping adapter. Spring WebFlux dùng codec infrastructure khác. Cần thống nhất client/server content negotiation policy, nhưng đừng suy ngược mọi instance dùng cùng list.

## 16. HTTP client đồng bộ và vận hành production

Dù converter/type reference đúng, client vẫn có thể thất bại vì:
- Timeouts mạng/remote service;
- Pool hết kết nối, DNS/TLS/connect delays;
- Payload quá lớn cần parse thành collection khổng lồ;
- 429/503/retry storm;
- Error body chứa PII/credentials nếu log bừa;
- SSRF nếu nhận URL upstream từ user không validate;
- Rate limiting/circuit breaker không được đặt;
- Unbounded threads gọi blocking I/O;
- Mismatch Jackson/schema version giữa client và server.

### 16.1. Đặt connect/read timeout phù hợp

Ví dụ với request factory của Spring:

~~~java
SimpleClientHttpRequestFactory factory =
        new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(3_000); // milliseconds
factory.setReadTimeout(5_000);

RestClient client = RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://api.example.com")
        .build();
~~~

Đây là cấu hình minh họa chạy được với Spring Web, không chứng minh `SimpleClientHttpRequestFactory` là lựa chọn hiệu năng tốt nhất cho mọi workload. Với production high concurrency, lựa chọn Apache HttpComponents/JDK client với connection pooling theo hạ tầng phù hợp, đo pending acquires, max connections, connection TTL và TLS costs.

**Tách biệt:**
- Connect timeout giới hạn thời gian tạo connection;
- Read timeout giới hạn chờ đọc dữ liệu theo semantics của HTTP client factory;
- Business deadline giới hạn toàn bộ operation, có thể cần ở tầng gọi ngoài;
- Retry nên bounded, có backoff và chỉ khi an toàn/idempotent.

### 16.2. Không parse một response vô hạn thành List

`body(new ParameterizedTypeReference<List<Book>>() {})` là lựa chọn tốt khi endpoint trả **một JSON array hữu hạn, có size limit hợp lý**. Nhưng nếu API trả millions of items, đây vẫn là một collection materialization trong memory. `ParameterizedTypeReference` **không** phải streaming parser.

Với các dữ liệu lớn:
- Pagination ở upstream: endpoint trả Page/limit/next cursor hữu hạn.
- NDJSON/chunked streaming: cân nhắc `RestClient.exchange` + bounded streaming parser hoặc reactive `WebClient.bodyToFlux` khi kiến trúc phù hợp.
- Files: dùng `Resource`/streaming output, không `body(byte[].class)` cho file nhiều GiB.
- Đặt max response bytes/timeouts và bảo vệ trước các JSON objects cực lớn.

### 16.3. Observability

Thu thập metrics theo endpoint:
- Status 2xx/4xx/5xx, retry count và timeout reasons.
- p50/p95/p99 HTTP latency, queue wait, upstream network time.
- Payload size, JSON decoding time/allocations.
- Connection pool active/idle/pending.
- Thread count và blocking worker saturation.
- Serialization/deserialization failures theo media type/schema version.
- Trace/correlation ID, nhưng không log body, tokens hoặc user data nhạy cảm.

Đừng coi lỗi converter chỉ là “body null”; để metric/error policy phản ánh nguyên nhân cụ thể.

## 17. Bảng quyết định nhanh

| Đầu vào HTTP | Đích Java | Cách nên dùng |
| --- | --- | --- |
| `{"id":1,"title":"Spring"}` | `Book` | `body(Book.class)` |
| `[{"id":1,...}]` | `List<Book>` | `body(new ParameterizedTypeReference<List<Book>>() {})` |
| `{"data":[...],"traceId":"..."}` | `ApiEnvelope<List<Book>>` | `body(new ParameterizedTypeReference<ApiEnvelope<List<Book>>>() {})` |
| `{"items":{"a":[...]}}` | `Map<String, List<Book>>` | `ParameterizedTypeReference<Map<String,List<Book>>>` nếu JSON shape thực khớp |
| `hello` với `text/plain` | `String` | `body(String.class)` |
| Bytes nhị phân nhỏ | `byte[]` | `body(byte[].class)` + hard size limit |
| Không có body / HTTP 204 | Không cần DTO | `toBodilessEntity()` |
| HTTP status + headers + typed body | `ResponseEntity<List<Book>>` | `toEntity(ParameterizedTypeReference<List<Book>>)` |
| RestTemplate GET generic | `ResponseEntity<List<Book>>` | `exchange(..., type)` |
| Response streaming/huge payload | Stream/chunks/pagination | Không `collectList` toàn bộ; chọn API có bounded memory |

## 18. Những nhầm lẫn cần tránh

1. **Nhầm `HttpMessageConverter` là Jackson ObjectMapper.** Converter quản lý HTTP media type, đọc/ghi body; Jackson chỉ là một implementation cho JSON.
2. **Nhầm `Accept` với `Content-Type`.** Accept mô tả mong muốn response, Content-Type mô tả bytes thực của request/response.
3. **Dùng `List.class` rồi cast sang `List<Book>`.** Không giải quyết Java erasure; sẽ có `LinkedHashMap` cho object JSON.
4. **Dùng `new ParameterizedTypeReference<T>() {}` trong generic method rồi kỳ vọng biết caller type.** T chưa được resolve, phải truyền type token từ caller.
5. **Dùng `ApiEnvelope.class` cho `ApiEnvelope<List<Book>>`.** Mất generic type của trường data.
6. **Đọc response JSON bằng converter khi response Content-Type là `text/html`.** Phải sửa contract hoặc handling.
7. **Tự `ObjectMapper.readValue` sau khi RestClient đã decode.** Tránh parse hai lần không cần thiết.
8. **Đặt custom converter hỗ trợ mọi media type trước converter mặc định.** Có thể cướp request/response của converter đúng.
9. **Nhầm `RestClient` là reactive.** Nó synchronous; `WebClient` mới là non-blocking reactive.
10. **Tăng connection threads vô hạn để xử lý chậm.** Cần timeouts, bounded pools và upstream capacities.
11. **Tưởng ParameterizedTypeReference giới hạn JSON array size.** Nó chỉ giữ kiểu, không giới hạn số phần tử hoặc memory.
12. **Không test 204, 404, malformed JSON, wrong media type.** Happy-path generic test là cần nhưng chưa đủ production.
13. **Tự đoán Boot 3.0 có RestClient.** RestClient xuất hiện Spring Framework 6.1; dùng Boot 3.2+ hoặc xác minh dependency version.
14. **Copy converter config WebFlux qua RestClient.** Reactive codec và synchronous converter là hai abstraction riêng.
15. **Gửi URL tùy ý cho RestClient từ user.** Có thể dẫn tới SSRF; kiểm soát domain/scheme, redirects và trusted endpoints.
16. **Ghi raw error body vào log.** Upstream có thể trả PII/secrets; log metadata an toàn thay vì toàn payload.

## 19. Câu hỏi ôn tập

**1. Tại sao `getForObject(..., List.class)` thường trả `List<LinkedHashMap>`?**

Vì `List.class` không có type parameter `Book`. Jackson giải JSON object thành Map khi không có element target class rõ ràng.

**2. `ParameterizedTypeReference` có tự parse JSON không?**

Không. Nó lưu Java `Type`; một `HttpMessageConverter` hỗ trợ target type/media type (thường Jackson) mới parse bytes.

**3. `GenericHttpMessageConverter` thêm điều gì?**

Nó có overload làm việc với `Type` ngoài `Class`, cho phép xét generic element/field types.

**4. Khi nào `Book.class` đã đủ?**

Khi response là một concrete `Book` object không generic, và JSON schema tương ứng.

**5. REST client mới Spring 6.2 lấy `List<Book>` thế nào?**

`restClient.get().uri(...).retrieve().body(new ParameterizedTypeReference<List<Book>>() {})`.

**6. RestTemplate dùng cách tương ứng nào?**

`restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<List<Book>>() {})`.

**7. Sự khác nhau giữa Jackson TypeReference và Spring ParameterizedTypeReference?**

Cả hai lưu type metadata cho generic nhưng thuộc API layer/library khác. Jackson TypeReference dùng với ObjectMapper, Spring ParameterizedTypeReference dùng với Spring client APIs.

**8. Có thể dùng một Method `<T> T fetch()` chứa `new ParameterizedTypeReference<T>() {}` để suy ra T từ nơi gọi?**

Không đáng tin. T là unresolved type variable trong method; caller cần truyền Class/ParameterizedTypeReference hoặc Type đã resolve.

**9. Vì sao `body(List.class)` trả kết quả nhưng `Book b = list.get(0)` có thể ném lỗi?**

Kiểu generic lúc compile bị erase, trong List thực là `LinkedHashMap`, không phải Book.

**10. `Accept: application/json` có bắt buộc server trả JSON không?**

Nó chỉ là negotiation preference. Server có thể trả lỗi/response khác; client phải kiểm tra HTTP status và actual `Content-Type`.

**11. `RestClient.retrieve()` có tự gọi network ngay không?**

Theo Spring 6.2 reference, `retrieve()` một mình chưa thực thi request; phải gọi terminal `body(...)`, `toEntity(...)`, hoặc `toBodilessEntity()`.

**12. Vì sao type token không thay paging/size limits?**

Nó mô tả kiểu đích, còn số lượng bytes/items vẫn có thể cực lớn. Cần pagination/streaming/caps độc lập.

## 20. Checklist trước khi đưa vào production

1. Xác định rõ HTTP client đang là **RestClient**, **RestTemplate** hay WebClient; cấu hình đúng converter/codec stack.
2. Kiểm tra Spring version: RestClient từ 6.1, trong ví dụ là Framework 6.2/Boot 3.5.
3. Thiết kế DTO/records khớp JSON schema, field names, nullability, date/time và unknown-fields policy.
4. Với response generic/nested, luôn chuyển type token đã resolve `ParameterizedTypeReference<T>` ở đúng tầng API.
5. Đảm bảo Jackson hoặc converter tương ứng có trong classpath và danh sách client converters.
6. Đặt `Accept` và `Content-Type` theo đúng request/response contract, không ép media type `*/*` để che lỗi.
7. Kiểm tra status, headers, null/204 và 4xx/5xx; định nghĩa exception mapping/retry/idempotency rõ.
8. Cấu hình connect/read/overall timeouts và connection pool/concurrency budgets.
9. Không nhận URL tùy ý hoặc redirects nguy hiểm gây SSRF, dùng allowlist outbound hosts.
10. Đặt max response bytes/number of elements, pagination hoặc bounded streaming cho payload lớn.
11. Không serialize secrets hoặc log request/response bodies nhạy cảm; có tracing/observability và redact.
12. Dùng `MockRestServiceServer` kiểm thử converters, generic element runtime class, nested DTO, POST JSON, status errors và malformed media type.
13. Kiểm thử integration với upstream thực khi cần verify proxy, TLS, network timeout và content negotiation.
14. Đừng coi chuyển RestTemplate → RestClient là một thay đổi từ blocking sang non-blocking; đo yêu cầu cụ thể.

## 21. Nguồn tham khảo

- [Spring Framework 6.2 — REST Clients (RestClient, RestTemplate, ParameterizedTypeReference)](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html)
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html)
- [Spring Framework 6.2 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestClient.html)
- [Spring Framework 6.2 — RestTemplate Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/client/RestTemplate.html)
- [Spring Framework 6.2 — GenericHttpMessageConverter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/converter/GenericHttpMessageConverter.html)
- [Spring Framework 6.2 — HttpMessageConverter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/converter/HttpMessageConverter.html)
- [Spring Framework 6.2 — ParameterizedTypeReference Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/ParameterizedTypeReference.html)
- [Spring Framework 6.2 — MockRestServiceServer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html)
- [Spring Boot 3.5 — REST clients auto configuration](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html)
- [Oracle Java — Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-006">ClientHttpRequestFactory và lựa chọn thư viện HTTP nền</a>

<details>
<summary>Xem chi tiết</summary>

# ClientHttpRequestFactory và lựa chọn thư viện HTTP nền trong Spring

## 1. Bối cảnh: cùng một RestClient, nhiều HTTP engine khác nhau

Trong Java/Spring, lời gọi đến dịch vụ HTTP bên ngoài có thể viết ở cấp cao:

~~~java
Order order = restClient.get()
        .uri("/orders/{id}", 42)
        .retrieve()
        .body(Order.class);
~~~

Đây là fragment có `RestClient restClient` và class/record `Order` được cấu hình ở nơi khác. Đoạn code mô tả **cái gì cần gọi**: GET, URI template, đổi JSON sang DTO. Nhưng để thật sự gửi request qua TCP/TLS, Spring cần một **HTTP transport implementation**.

Trục kỹ thuật:

~~~text
Code nghiệp vụ
     |
     v
RestClient / RestTemplate              (API HTTP đồng bộ)
     |
     v
HttpMessageConverter + Interceptors   (JSON, headers, logging)
     |
     v
ClientHttpRequestFactory              (SPI chọn transport)
     |
     +-- JdkClientHttpRequestFactory  --> java.net.http.HttpClient
     +-- HttpComponentsClientHttpRequestFactory --> Apache HttpClient 5
     +-- JettyClientHttpRequestFactory --> Jetty HttpClient
     +-- ReactorNettyClientRequestFactory --> Reactor Netty HttpClient
     +-- SimpleClientHttpRequestFactory --> HttpURLConnection
     |
     v
DNS -> connection acquire -> TCP -> TLS -> HTTP write/read -> response
~~~

**`ClientHttpRequestFactory` là abstraction của Spring cho việc tạo một HTTP request có thể thực thi, không phải factory tạo RestClient.** Đổi factory có thể đổi connection pooling, HTTP versions, redirect/proxy, TLS, các loại timeout, hiệu năng và error behavior nhưng không nhất thiết cần sửa toàn bộ code nghiệp vụ.

### Mục tiêu học tập

Sau bài này, bạn có thể:
1. Giải thích interface `ClientHttpRequestFactory` và vai trò của `ClientHttpRequest`/`ClientHttpResponse`.
2. Biết cách `RestClient` và `RestTemplate` chọn request factory.
3. So sánh `SimpleClientHttpRequestFactory`, `JdkClientHttpRequestFactory`, Apache HttpClient 5, Jetty và Reactor Netty.
4. Chọn HTTP transport phù hợp workload: tần suất request, nhiều host, pooling, HTTP/2, proxy, mTLS, TLS và retry policy.
5. Cấu hình timeout **đúng tầng**: connection acquisition, TCP connect, TLS handshake, response/read, overall deadline.
6. Hiểu giới hạn Spring Framework **6.1** so với **6.2+**, không copy API chưa tồn tại.
7. Cấu hình bean `RestClient` với explicit factory và Java 17.
8. Xây demo chạy được bằng Spring Boot 3.3.5, server địa phương và kiểm thử JUnit HTTP thật.
9. Tránh resource leaks, thread starvation, SSRF, credential leakage, redirect surprises và giả định mặc định transport bất biến.

Bối cảnh cha: **HTTP client đồng bộ**. `RestClient` có từ Spring Framework 6.1. Ví dụ dưới dùng **Java 17 + Spring Boot 3.3.5**, không tự áp dụng các Spring 7/Boot 4 API mới cho code demo.

## 2. Interface `ClientHttpRequestFactory` thực sự định nghĩa gì?

Ở Spring Framework 6.1, interface thuộc package:

~~~java
org.springframework.http.client.ClientHttpRequestFactory
~~~

Phương thức cốt lõi:

~~~java
ClientHttpRequest createRequest(
        URI uri, HttpMethod httpMethod) throws IOException;
~~~

- `URI` xác định destination và path cần gọi.
- `HttpMethod` xác định GET/POST/PUT/PATCH/DELETE...
- Kết quả là một `ClientHttpRequest` với headers/body có thể chuẩn bị rồi thực thi.
- `IOException` có thể phát sinh khi không tạo request được; lỗi khi thật sự gửi/đọc thường xuất hiện muộn hơn.
- `ClientHttpRequest.execute()` trả `ClientHttpResponse` khi request được gửi và response sẵn có.

### 2.1. Luồng đồng bộ dạng thấp

~~~text
factory.createRequest(uri, GET)
  -> request.getHeaders().setAccept(...)
  -> request.execute()                 [network có thể block]
  -> response.getStatusCode()
  -> response.getBody()                [I/O có thể block]
  -> close response/body resources
~~~

Đây là mô tả khái niệm, không đề nghị viết HTTP business code bằng SPI thấp này. `RestClient`/`RestTemplate` thường quản lý phần lớn headers, converters, error handling và đóng response đúng lifecycle. Khi tự dùng `ClientHttpRequest` trực tiếp, ứng dụng phải có chính sách đóng `ClientHttpResponse`.

### 2.2. Request factory có làm HTTP client non-blocking không?

**Không.** Một factory nền có engine nội bộ hỗ trợ asynchronous I/O (ví dụ Reactor Netty hoặc JDK HttpClient) vẫn phục vụ **synchronous request/response contract** khi dùng qua `RestClient`/`RestTemplate`. Thread gọi `restClient.get()...retrieve()...body(...)` có thể chờ kết quả.

Nếu đang ở Spring WebFlux event-loop thread, **đừng** dùng `RestClient` trực tiếp trong handler rồi cho rằng có Reactor Netty factory là non-blocking. Để non-blocking end-to-end, dùng reactive `WebClient` và trả `Mono/Flux`, tránh `block()`. Nếu phải gọi legacy synchronous API trong WebFlux, cô lập qua bounded worker, nhưng phải đo queue/timeout.

### 2.3. Factory khác message converter

`HttpMessageConverter` chuyển đổi Java object ↔ HTTP payload (Jackson JSON, String, form...). Nó không quyết định connection pool hoặc TLS protocol. `ClientHttpRequestFactory` quyết định cách gửi/nhận bytes qua HTTP. Thay factory không tự động thay Jackson serializer, DTO version, retry policy hay API error schema.

## 3. ClientHttpRequestFactory là SPI, RestClient là API sử dụng

### 3.1. `RestClient`

`RestClient` là HTTP client **đồng bộ, fluent**, giới thiệu trong Spring 6.1:

~~~java
RestClient client = RestClient.builder()
        .baseUrl("https://service.example.test")
        .requestFactory(factory)
        .build();
~~~

`factory` là bean `ClientHttpRequestFactory` được inject hoặc dựng riêng. Các thao tác:
- `get()`/`post()` chọn method.
- `uri(...)` hỗ trợ template variables.
- `header(...)`, `accept(...)`, `contentType(...)` thêm HTTP metadata.
- `body(...)` gửi body.
- `retrieve()` đọc response theo mô hình thông dụng, có default status handling.
- `exchange(...)` cho xử lý raw response/status custom; cần quản lý error/status theo cách bạn chọn.

### 3.2. `RestTemplate`

`RestTemplate` vẫn tồn tại và được dùng nhiều trong code cũ:

~~~java
RestTemplate template = new RestTemplate(factory);

Order order = template.getForObject(
        "https://service.example.test/orders/{id}",
        Order.class, 42);
~~~

Hai API có thể dùng **cùng một factory**. Việc chuyển `RestTemplate` → `RestClient` không bắt buộc đổi HTTP engine ngay lập tức; tách hai quyết định để migration giảm rủi ro.

### 3.3. Không nhầm WebClient và ClientHttpConnector

`WebClient` (reactive) dùng abstraction `ClientHttpConnector`, ví dụ `ReactorClientHttpConnector`, chứ **không** sử dụng `ClientHttpRequestFactory` làm extension point chủ yếu. `ReactorNettyClientRequestFactory` là adapter để dùng Reactor Netty trong synchronous client; nó không làm API của RestClient trở thành reactive.

## 4. Các HTTP backend có sẵn trong Spring 6.1

| Factory | Library HTTP nền | Khi phù hợp | Điểm cần xem xét |
| --- | --- | --- | --- |
| `SimpleClientHttpRequestFactory` | `java.net.HttpURLConnection` | Demo, tích hợp đơn giản, dependency tối thiểu | Giới hạn/quirks legacy JDK API, status/error path, tính năng |
| `JdkClientHttpRequestFactory` | `java.net.http.HttpClient` (Java 11+) | Java 17, không thêm thư viện thứ ba, HTTP/2 nếu peer hỗ trợ | Pool knobs hạn chế hơn Apache; JDK TLS/proxy/redirect semantics |
| `HttpComponentsClientHttpRequestFactory` | Apache HttpClient **5.x** | Pool tuning, enterprise proxy, nhiều outbound calls, connection management | Phải thêm Apache HttpClient 5, quản lý pooling/lifecycle/timeouts |
| `JettyClientHttpRequestFactory` | Jetty HttpClient | Đội ngũ đã chuẩn hóa Jetty, HTTP features của Jetty | Thêm dependencies, lifecycle, timeout/proxy riêng |
| `ReactorNettyClientRequestFactory` | Reactor Netty HttpClient | Hệ thống đã dùng Reactor Netty và muốn share/tối ưu resources có chủ đích | Client API gọi vẫn đồng bộ; event loops/pool ownership cần rõ |
| `BufferingClientHttpRequestFactory` | Wrapper của factory khác | Có nhu cầu đọc request/response body nhiều lần | Buffer toàn bộ body trong RAM; nguy hiểm với payload lớn |
| `InterceptingClientHttpRequestFactory` | Wrapper hỗ trợ interceptors | Headers, tracing, request hooks | Không thay transport; log raw body có rủi ro |

Không nên coi các factory là những tính năng hoàn toàn tương đương: TLS/proxy/redirect/HTTP protocol, exception wrapping và connection reuse có thể khác. Khi chuyển transport, **cần regression tests** với downstream và gateway thật.

### 4.1. `SimpleClientHttpRequestFactory`

Adapter cũ nhất, dùng `HttpURLConnection`:

~~~java
SimpleClientHttpRequestFactory f =
        new SimpleClientHttpRequestFactory();
f.setConnectTimeout(Duration.ofSeconds(2));
f.setReadTimeout(Duration.ofSeconds(5));
~~~

Đây là Java fragment (imports ở tài liệu chính thức). Đơn giản nhưng không nên chọn chỉ vì nghĩ tất cả backend khác đều phức tạp không cần thiết. Trong một số môi trường, `HttpURLConnection` có hành vi không thuận lợi khi truy cập status của error response; Spring reference khuyên thử factory khác nếu gặp lỗi.

**Spring 6.1:** `setBufferRequestBody(boolean)` và `setOutputStreaming(boolean)` trên Simple factory đã deprecated/for removal vì request body **không còn buffered theo lựa chọn đó**. Đừng sao chép bài viết cũ hướng dẫn `setBufferRequestBody(false)` như tối ưu mới bắt buộc; cần đọc javadoc đúng version.

### 4.2. `JdkClientHttpRequestFactory`

Dựa trên `java.net.http.HttpClient` của JDK:

~~~java
HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();

JdkClientHttpRequestFactory f =
        new JdkClientHttpRequestFactory(httpClient);
f.setReadTimeout(Duration.ofSeconds(3));
~~~

Ưu điểm:
- Có sẵn trong Java 17, không thêm Apache/Jetty dependency.
- HTTP/1.1 hoặc thử HTTP/2 tùy `HttpClient.Version` và server hỗ trợ.
- Reuse client cho nhiều requests, JDK quản lý connections và executors theo implementation.
- Có APIs cấu hình proxy, authenticator, SSLContext/SSLParameters đúng JDK.

**Không có nghĩa JDK HttpClient có cùng mức tùy chỉnh** cho pool per-route/max total/lease timeout như Apache HttpClient 5. Nếu cần giới hạn outbound concurrency, vẫn có thể dùng semaphore/bulkhead/application limits, nhưng chúng không tương đương chỉnh internal pool knobs.

### 4.3. `HttpComponentsClientHttpRequestFactory`

Dựa trên **Apache HttpClient 5.1+** với thư viện `org.apache.httpcomponents.client5:httpclient5`, không phải Apache HttpClient 4.x/`org.apache.http.client.HttpClient`.

~~~java
HttpComponentsClientHttpRequestFactory f =
        new HttpComponentsClientHttpRequestFactory();
f.setConnectTimeout(Duration.ofSeconds(2));
f.setConnectionRequestTimeout(Duration.ofMillis(500));
~~~

Đây là fragment. `setConnectionRequestTimeout` quan trọng khi dùng connection manager: giới hạn thời gian **chờ mượn connection** từ pool. `setConnectTimeout` giới hạn giai đoạn connect ở lớp cấu hình của request, nhưng javadoc Spring 6.1 nói rõ nó **không bao trùm toàn bộ TLS handshake/CONNECT proxy**; cần socket/TLS config riêng nếu yêu cầu timeout chặt.

**Điểm khác biệt phiên bản dễ gây lỗi biên dịch: Spring Framework 6.1 không có `HttpComponentsClientHttpRequestFactory.setReadTimeout(...)`.** API này có ở các dòng mới hơn như Spring 6.2. Với Boot 3.3.5/Framework 6.1, dùng Apache `RequestConfig.setResponseTimeout(...)` qua cấu hình underlying HttpClient.

### 4.4. `JettyClientHttpRequestFactory`

Dùng Jetty HTTP client:

~~~java
JettyClientHttpRequestFactory f =
        new JettyClientHttpRequestFactory();
~~~

Fragment này chưa xử lý lifecycle, dependencies/transport configuration. Phù hợp nếu hệ thống đã dùng Jetty client features, không phải lựa chọn mặc định cho mọi app mới. Khởi tạo nhiều Jetty clients độc lập có thể tạo nhiều worker/executor resources. Xem API 6.1, chọn factory/client bean dùng chung, đóng đúng shutdown.

### 4.5. `ReactorNettyClientRequestFactory`

Dùng Reactor Netty trong sync framework interface. Cần dependency/runtime Reactor Netty tương ứng. Có thể dùng khi ecosystem sẵn có Reactor Netty nhưng phải biết:
- Các networking resources có event loops và connection pools.
- RestClient call **vẫn blocking đối với caller**.
- Reuse resources và HTTP client lifecycle cần chiến lược nếu app tạo nhiều factories.
- Không nên chỉ thêm WebFlux starter vào Spring MVC app để chọn transport; dependency graph có thể ảnh hưởng Boot web application type hoặc auto-config. Chọn reactor-netty dependency/boot platform một cách chủ đích.

## 5. Spring RestClient chọn factory mặc định thế nào?

### 5.1. Đừng dựa vào giả định HttpURLConnection luôn là default

Tài liệu Spring Framework 6.1 cho `RestClient.Builder.requestFactory(...)` giải thích rằng khi không chỉ định factory, framework xét sự hiện diện của Apache/Jetty clients trên classpath; nếu JDK HTTP client sẵn có, sử dụng JDK client; cuối cùng mới tới simple fallback.

**Tùy ứng dụng và cách khởi tạo**, Spring Boot có thể can thiệp thêm vào auto-detection qua auto-configured builder, và classpath quyết định lựa chọn. Thêm một thư viện HTTP qua một dependency transitively có thể làm backend mặc định thay đổi mà bạn không sửa một dòng HTTP call nào.

Vì vậy, với service yêu cầu timeout/pooling/TLS behavior cụ thể:
- Dùng explicit `requestFactory(factory)` khi muốn đảm bảo loại transport.
- Không suy từ request vẫn 200 rằng connection policy không thay.
- Kiểm dependency tree trước/sau khi thêm starter/library.
- Viết integration tests timeout/redirect/proxy và quan sát connection pools ở staging.

### 5.2. Boot 3.3 khác các Boot phiên bản mới hơn

Spring Boot 3.3 có `RestClient.Builder` và `RestTemplateBuilder` auto-configurations. Các Spring Boot docs “current” hoặc Boot 3.4+/4.x có thể đổi package/import của builder, property tên mới hoặc cách chọn HTTP client. **Đừng chép `spring.http.clients.imperative.factory` hay Spring Boot 4 builder imports vào ứng dụng Boot 3.3.5 mà không kiểm version.**

Trong demo, ta dùng `RestClient.Builder` do Boot cung cấp nhưng **gắn explicit JDK factory**, tránh phụ thuộc auto-detection. Nếu muốn dùng `RestTemplateBuilder` của Boot 3.3, package đúng là `org.springframework.boot.web.client.RestTemplateBuilder`, không phải package mới của Boot 4.

## 6. Điều gì khiến một HTTP backend phù hợp?

Không có một HTTP client library luôn tối ưu cho mọi ứng dụng. Hãy quyết định bằng **yêu cầu thực tế**:

| Yêu cầu | Câu hỏi chọn transport |
| --- | --- |
| Dễ đóng gói | Java 17/JDK client đã đủ tính năng chưa? |
| Outbound concurrency lớn | Cần max total, per-route pools, lease timeout, queue depth không? |
| HTTP/2 | Client/server/proxy có thương lượng HTTP/2 và fallback đúng không? |
| Enterprise proxies | Có HTTP CONNECT proxy, custom authentication, allowlists không? |
| TLS/mTLS | Có cần client certificate, custom trust bundle, hostname verification? |
| Latency limits | Có bao phủ connect, TLS, pool wait, first-byte/read và overall deadline? |
| Redirect | Có được phép follow redirects? Có nguy cơ rời trusted origin không? |
| Payload lớn | Có streaming upload/download thực sự, không buffer vô hạn? |
| Observability | Có metrics active/idle/pending connections, TLS errors, status/latency không? |
| Operation | Pool đóng đúng lifecycle, hỗ trợ keep-alive/idle eviction, DNS rotation không? |
| Dependencies | Security patches, CVEs, compatibility và BOM được quản lý không? |
| Team | Debug/tooling hiện có phù hợp Apache, JDK, Jetty hay Reactor Netty? |

### 6.1. Trường hợp nên bắt đầu bằng JDK HttpClient

- Service có outbound requests ở mức vừa phải.
- Java 17 đã là baseline.
- Không cần tuning phức tạp của connection manager.
- Muốn giảm third-party dependency.
- Muốn rõ explicit redirect policy và JDK SSL configuration.
- Có integration tests chứng minh TLS/proxy/timeouts đủ dùng.

JDK client không đồng nghĩa “không có pooling” — nó có cơ chế connection reuse nội bộ, nhưng pool controls khác Apache. Không tạo một `HttpClient` mới cho mỗi request.

### 6.2. Trường hợp Apache HttpClient 5 đáng cân nhắc

- Đa dạng downstream hosts và concurrency cao.
- Cần max connections/per-route và connection leasing timeout.
- Cần custom proxy route planner, authentication hoặc TLS/socket controls.
- Muốn kiểm tra pool saturation/eviction bằng Apache manager APIs.
- Hệ thống đã chuẩn hóa HttpClient 5.

Đổi sang Apache không tự nâng throughput nếu bottleneck là downstream 429, database hoặc giới hạn server. Tăng pool total không được phép vượt rate limit của partner API.

### 6.3. Có khi nên dùng Spring MVC thay vì WebFlux

Nếu ứng dụng chủ yếu là JDBC/JPA + RestClient sync, Spring MVC blocking request model có thể phù hợp. Dùng WebFlux nhưng bọc gần như toàn bộ operations vào boundedElastic sẽ thêm complexity mà không chắc có lợi. Chọn stack dựa trên dependencies và concurrency thực, không theo tên framework.

## 7. Những giai đoạn timeout khác nhau

Một “timeout 3 giây” nghe đơn giản nhưng có thể ứng với các giai đoạn:

~~~text
time -->
[DNS lookup] [pool wait] [TCP connect] [TLS handshake]
                                  [send body] [wait response header]
                                             [read response body]
|------------------------- overall request deadline -------------|
~~~

| Timeout/budget | Giai đoạn | Ví dụ cách cấu hình |
| --- | --- | --- |
| Pool acquire / connection request | Chờ connection từ pool | Apache `setConnectionRequestTimeout` |
| Connect | Kết nối TCP | JDK `HttpClient.Builder.connectTimeout` hoặc Simple `setConnectTimeout` |
| TLS handshake | Thương lượng TLS | Client/socket-specific SSL settings |
| Response/read | Chờ response/đọc dữ liệu | JDK factory `setReadTimeout`; Apache 5 `RequestConfig.responseTimeout` |
| Whole-call deadline | End-to-end operation | Policy bên ngoài HTTP factory, deadline/cancellation tùy API |
| Caller's deadline | Thời gian caller sẵn sàng chờ | Request SLA/gateway timeout, thread budget |

**Không cộng/trừ các giá trị tùy tiện rồi gọi chúng là toàn bộ end-to-end SLA.** DNS lookup và TLS handshake có thể có semantics riêng; JDK/Apache/Jetty không hoàn toàn giống nhau.

### 7.1. Connect timeout

Khi downstream host không phản hồi TCP handshake, connect timeout giảm khả năng giữ thread mãi. Nhưng:
- DNS lookup có thể dùng cơ chế resolver riêng.
- Connection reuse có thể bỏ qua TCP connect mới.
- TLS handshake là giai đoạn khác.
- Proxy CONNECT có thể thêm một tầng kết nối.

### 7.2. Connection request timeout ở Apache

Nếu pool giới hạn max 20 connections per route, request thứ 21 có thể phải chờ. `setConnectionRequestTimeout(Duration.ofMillis(500))` kiểm thời gian chờ **lấy connection từ pool**, không phải network read timeout và không làm server phản hồi nhanh hơn.

Khi ứng dụng có 100 server threads và Apache pool 10 connections:
- 10 requests có thể đang dùng connections.
- 90 threads còn lại có thể đợi pool acquire và chiếm servlet worker.
- Nếu không có acquire timeout và queue budget, một đợt downstream chậm kéo theo server thread exhaustion.

Pool cần được dimension theo concurrency, latency, downstream quotas và backpressure/admission control; không cứ nâng lên 1.000 connections là tốt.

### 7.3. Response timeout khác deadline tổng

Apache `RequestConfig` có response timeout. JDK factory `setReadTimeout` có semantics gắn với underlying `HttpRequest.Builder.timeout` theo Javadoc Spring. Các cơ chế này không nhất thiết đồng nghĩa socket SO_TIMEOUT cho từng read byte, và cần kiểm thực nghiệm trên streaming/slow-body endpoints.

Nếu business deadline bắt buộc 2 s, nên dùng client support end-to-end timeout/deadline phù hợp, operation-level cancellation hoặc pool/driver/proxy policy; kiểm DNS, connect, headers và body chứ không chỉ gọi `setReadTimeout(2s)` rồi coi là tuyệt đối.

### 7.4. Giá trị 0 không phải luôn "tắt timeout an toàn"

Một số APIs dùng 0 = infinite timeout; timeout defaults có thể từ JVM/system/client. Một deadline vô hạn gây thread starvation khi downstream treo. Trong production, luôn có time budget được phê duyệt, deadline propagation và circuit breaker/rate limit theo đường gọi.

## 8. Vòng đời connections và pooling

### 8.1. Reuse một client/factory dài hạn

**Không tạo RestClient+HttpClient trong mỗi request handler**:

~~~java
@GetMapping("/orders/{id}")
Order bad(@PathVariable long id) {
    HttpClient newHttpClient = HttpClient.newHttpClient();
    RestClient newRestClient = RestClient.builder()
            .requestFactory(
                    new JdkClientHttpRequestFactory(newHttpClient))
            .build();

    return newRestClient.get()
            .uri("https://trusted.example.test/orders/{id}", id)
            .retrieve()
            .body(Order.class);
}
~~~

Đây là **anti-pattern**: tạo object/client resources liên tục, giảm reuse connections/executors, làm metrics và shutdown khó hơn. Sample còn thiếu method/class context và không phải runnable hoàn chỉnh.

Đúng hơn là inject singleton `RestClient` (hoặc typed Gateway) với configured request factory. Không có nghĩa một RestClient dùng cho mọi downstream cần cùng auth/timeouts; có thể tạo **một RestClient bean per upstream service** khi requirements khác nhau.

### 8.2. Apache pool lifecycle

Apache `CloseableHttpClient` và `PoolingHttpClientConnectionManager` cần được đóng/evicted theo owner. Spring `HttpComponentsClientHttpRequestFactory` implement `DisposableBean` ở 6.1 và có thể đóng underlying client khi factory được destroy; **đừng vô ý để nhiều factories đồng sở hữu cùng một Apache client rồi mỗi factory cùng close nó ở thời điểm khó dự đoán**.

Nếu bạn tạo manager/client riêng để inject nơi khác, xác định ai có quyền đóng; tránh double-shutdown của shared pools. Không để test case trước đóng một client singleton khiến test sau không còn connection.

### 8.3. Keep-alive và idle connections

Connection reuse giảm TCP/TLS setup overhead, nhưng có trade-offs:
- Server/load balancer có thể đóng idle connections.
- DNS IP thay đổi có thể tương tác với keep-alive.
- TLS certificates/rotation và proxy config thay đổi cần test.
- Pool stale connection retry có thể xảy ra; không tự retry operations không idempotent.
- Connection max life/idle eviction cần phù hợp infrastructure.

### 8.4. Chỉ có 1 factory không có nghĩa chỉ 1 connection

Một singleton factory có thể quản lý nhiều pooled connections. Tài nguyên cần đo là **connections active/idle, pending acquires, request latency**, không đếm factory objects và suy ra “chỉ có 1 TCP socket”.

## 9. HTTP status, lỗi mạng và RestClient

### 9.1. Network failure khác HTTP 4xx/5xx

- **HTTP 404**: server đã trả HTTP response; đây là application/protocol response, không mặc định là TCP failure.
- **HTTP 503/429**: downstream báo quá tải/chính sách, có thể cần backoff theo Retry-After.
- **Connection refused/reset, DNS failure, TLS handshake failure**: không nhận response HTTP hợp lệ.
- **Read timeout**: client chờ quá giới hạn được cấu hình; không phải bằng chứng server chưa thực hiện tác dụng của POST.

Trong `RestClient.retrieve()`, Spring mặc định ánh xạ status lỗi HTTP (4xx/5xx) thành nhóm exception tương ứng. Nếu muốn một gateway/domain mapping khác, dùng `onStatus(...)` hoặc `exchange(...)` và trả response hợp đồng của chính ứng dụng, không catch Throwable rồi coi mọi thứ là 404.

### 9.2. Ví dụ xử lý 404 ở gateway

~~~java
public Optional<Order> maybeGet(URI uri) {
    try {
        Order order = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Order.class);
        return Optional.ofNullable(order);
    } catch (HttpClientErrorException.NotFound notFound) {
        return Optional.empty();
    }
}
~~~

Đây là method fragment trong class có `RestClient restClient`; `HttpClientErrorException.NotFound` thuộc `org.springframework.web.client`. Chỉ bắt 404; không nuốt 401/403/429/500, connect timeout, JSON decode error hay SSL error thành Optional.empty.

### 9.3. Đóng response khi dùng `exchange` raw

Khi dùng low-level `ClientHttpRequest.execute()`, đóng response đúng cách để trả pooled connection và tránh leak. Với `RestClient.exchange(...)`, framework quản lý execution callback/closing theo contract, nhưng **đừng giữ raw InputStream/response body sau khi callback đã kết thúc** rồi kỳ vọng đọc tiếp.

~~~java
int status = restClient.get()
        .uri(uri)
        .exchange((request, response) ->
                response.getStatusCode().value());
~~~

Fragment này chỉ đọc status và không giữ reference của response object ngoài callback; khác `retrieve()`, `exchange()` không tự áp status handlers theo cùng cách, nên bạn phải tự xác định status interpretation.

## 10. Bảo mật khi chọn HTTP transport

### 10.1. SSRF (Server-Side Request Forgery)

Sai:

~~~java
@GetMapping("/proxy")
String unsafe(@RequestParam String url) {
    return restClient.get()
            .uri(url)
            .retrieve()
            .body(String.class);
}
~~~

**Không bao giờ** để người dùng chỉ định URL tùy ý rồi server gọi trực tiếp nếu không có SSRF protections. User có thể nhắm tới loopback, cloud metadata IP, internal control plane, DNS rebinding hoặc redirect sang private destination.

Đúng hơn:
- Base URL cố định được lấy từ trusted server config.
- Path/ID được encode qua URI template.
- Allowlist schemes/host/port và DNS/redirect policy nếu phải cho dynamic destinations.
- Network egress/firewall chặn private/internal endpoints không được phép.
- Không follow redirects không kiểm soát tới origins khác.
- Test resolution và effective destination, kể cả IPv6, punycode và redirects.

Việc `RestClient` tự encode URI không tự là SSRF protection; encoding chỉ xử lý syntax.

### 10.2. TLS verification không được tắt

Khi chọn JDK/Apache/Jetty, giữ CA trust validation và hostname verification. Các snippets “trust-all SSLContext” hoặc `NoopHostnameVerifier` có thể khiến credentials rò rỉ qua MITM; không triển khai chúng ở production, kể cả “tạm thời để fix handshake”. Với mTLS, dùng client certificate/keystore hoặc Spring Boot SSL bundles (theo version) và test rotation/expiry.

### 10.3. Không log Authorization hoặc raw body

Interceptors/logging có thể quan sát headers và request/response bodies. Tránh:
- Log `Authorization`, `Cookie`, `Set-Cookie`, API keys.
- Dump raw request body chứa passwords, cards, tokens hoặc PII.
- Đặt unbounded user IDs/URLs thành metric labels.
- Giữ raw response bodies trong memory để debug quá lâu.

Ưu tiên logging method, trusted host, path template, status, elapsed, bytes/size và trace IDs đã được kiểm soát.

### 10.4. Redirect policy và credentials

JDK `HttpClient.Redirect.NEVER` là một default có chủ đích trong demo. Redirect có thể đi tới host khác hoặc đổi method theo HTTP status/client implementation. Nếu bật followRedirects:
- Kiểm trusted destinations và không rò Authorization khi đổi origin.
- Cẩn thận 301/302/303 vs 307/308 cho POST.
- Đặt giới hạn redirect hop và tối đa response size.
- Không retry side-effect POST mù quáng.

### 10.5. Proxy authentication và trust boundary

Enterprise proxy có thể yêu cầu authentication, TLS interception hoặc mTLS. Dùng APIs của JDK/Apache để cấu hình proxy có kiểm soát; không nhúng password vào HTTP URI hoặc source code. Xác định cả proxy connect timeout, CONNECT/TLS handshake và certificate trust chain.

## 11. Ví dụ chạy được: RestClient + explicit JDK factory

Ứng dụng Spring Boot nhỏ có:
- Một **HTTP endpoint địa phương** `GET /downstream/orders/{id}` trả JSON đơn hàng hoặc 404.
- Một bean `ClientHttpRequestFactory` lựa chọn **JdkClientHttpRequestFactory** và JDK `HttpClient`.
- Một bean `RestClient` sử dụng chính factory đó, không phụ thuộc classpath auto-detection.
- Một `OrderGateway` để API nghiệp vụ không phụ thuộc transport implementation.
- `@SpringBootTest(webEnvironment=RANDOM_PORT)` tạo HTTP server thật và tests dùng `@LocalServerPort` để client gọi vào local test server.
- JUnit tests xác minh 200/404, HTTP factory bean và URI/template đúng; không cần Internet.

**Vì tests dùng random port**, chúng không dựa trên cổng 8080 cứng. Demo chạy được nếu có Java 17, Gradle 8.x tương thích và dependencies tải được từ Maven Central. Các file dưới đây không bao gồm scripts Gradle wrapper; tạo wrapper nếu repository chưa có.

~~~text
http-factory-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/httpfactory/
    │   ├── HttpFactoryApplication.java
    │   ├── config/HttpClientConfig.java
    │   ├── domain/Order.java
    │   ├── gateway/OrderGateway.java
    │   └── web/DownstreamDemoController.java
    └── test/java/com/example/httpfactory/
        └── HttpFactoryIntegrationTest.java
~~~

### 11.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'http-factory-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` ở đây chỉ dựng một **HTTP test server bằng Spring MVC/Tomcat**. Điều đó hoàn toàn phù hợp với client **đồng bộ** `RestClient`; không cần WebFlux dependency.

### 11.2. Main application

`src/main/java/com/example/httpfactory/HttpFactoryApplication.java`:

~~~java
package com.example.httpfactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpFactoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(HttpFactoryApplication.class, args);
    }
}
~~~

### 11.3. Order DTO

`src/main/java/com/example/httpfactory/domain/Order.java`:

~~~java
package com.example.httpfactory.domain;

public record Order(long id, String state) {}
~~~

Record Java 17 được Jackson Spring Boot mặc định encode/decode thành JSON. `state` là chuỗi để ví dụ dễ hiểu; hệ sản xuất có thể chọn enum/validation theo hợp đồng riêng.

### 11.4. HTTP factory được xác định tường minh

`src/main/java/com/example/httpfactory/config/HttpClientConfig.java`:

~~~java
package com.example.httpfactory.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public ClientHttpRequestFactory orderRequestFactory() {
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    @Bean
    public RestClient orderRestClient(
            RestClient.Builder builder,
            ClientHttpRequestFactory orderRequestFactory) {

        return builder
                .requestFactory(orderRequestFactory)
                .build();
    }
}
~~~

Giải thích:
1. `@Bean ClientHttpRequestFactory` là một singleton trong Spring context.
2. HTTP engine underlying được tạo từ `java.net.http.HttpClient` của JDK 17.
3. Connect timeout 2 s và read/request timeout 3 s là **hai knobs khác nhau**, không phải global 5-second SLA.
4. `Redirect.NEVER` tránh tự follow redirects trong demo; ứng dụng sẽ có thể nhận 3xx status hoặc xử lý theo policy của `RestClient`.
5. HTTP/1.1 được chọn để test đơn giản với embedded Tomcat. Nếu thử HTTP/2, cần cả server/proxy/TLS support và regression tests.
6. `RestClient.Builder` được Boot cung cấp; gọi `requestFactory` explicit để giữ transport nhất quán dù classpath có thêm Apache/Jetty.
7. Dùng bean RestClient để tái sử dụng configuration/HTTP resources, không tạo trong mỗi handler method.

**Lưu ý:** Nếu dự án có nhiều `ClientHttpRequestFactory` beans, hãy dùng `@Qualifier("orderRequestFactory")` ở injection point để tránh ambiguity. Demo chỉ có một factory, nên Spring inject trực tiếp được.

### 11.5. Gateway bọc HTTP API

`src/main/java/com/example/httpfactory/gateway/OrderGateway.java`:

~~~java
package com.example.httpfactory.gateway;

import com.example.httpfactory.domain.Order;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderGateway {

    private final RestClient client;

    public OrderGateway(RestClient orderRestClient) {
        this.client = orderRestClient;
    }

    public Order fetch(URI trustedResourceUri) {
        return client.get()
                .uri(trustedResourceUri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Order.class);
    }
}
~~~

Tên `trustedResourceUri` nhấn mạnh **giá trị phải đến từ application configuration hoặc code đáng tin**, không truyền raw `?url=...` do người dùng nhập. Trong demo, test tạo URI từ `localhost/random port` do embedded server cấp, nên không có chức năng SSRF public.

Trong production:
- Inject cấu hình trusted `baseUrl` vào Gateway và chỉ nhận business ID.
- Dùng `uri("/orders/{id}", id)` để encode path variable.
- Quy định 404/401/429/5xx mapping, timeouts và retries ở gateway/domain layer.
- Không coi `OrderGateway.fetch` là một method non-blocking: thread gọi sẽ chờ response.

### 11.6. HTTP server endpoint để test transport

`src/main/java/com/example/httpfactory/web/DownstreamDemoController.java`:

~~~java
package com.example.httpfactory.web;

import com.example.httpfactory.domain.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/downstream/orders")
public class DownstreamDemoController {

    @GetMapping("/{id}")
    public ResponseEntity<Order> find(@PathVariable long id) {
        if (id == 1L) {
            return ResponseEntity.ok(new Order(1L, "CONFIRMED"));
        }
        return ResponseEntity.notFound().build();
    }
}
~~~

Local downstream server phản hồi:
- `GET /downstream/orders/1` → 200 + JSON `{"id":1,"state":"CONFIRMED"}`.
- `GET /downstream/orders/999` → 404, không có JSON Order.
- Không có database, authentication hay external service.

Không phải production design để một app gọi HTTP vào chính nó; **đây là test fixture tiện chạy thật qua network stack** và kiểm request factory trên loopback. Ứng dụng thật gọi downstream service khác theo domain/trust boundary.

### 11.7. Integration tests trên local port

`src/test/java/com/example/httpfactory/HttpFactoryIntegrationTest.java`:

~~~java
package com.example.httpfactory;

import com.example.httpfactory.domain.Order;
import com.example.httpfactory.gateway.OrderGateway;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpFactoryIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    OrderGateway gateway;

    @Autowired
    ClientHttpRequestFactory factory;

    URI url(long id) {
        return URI.create(
                "http://127.0.0.1:" + port + "/downstream/orders/" + id);
    }

    @Test
    void usesExplicitJdkRequestFactory() {
        assertInstanceOf(JdkClientHttpRequestFactory.class, factory);
    }

    @Test
    void convertsResponseToOrderRecord() {
        Order result = gateway.fetch(url(1L));
        assertEquals(1L, result.id());
        assertEquals("CONFIRMED", result.state());
    }

    @Test
    void mapsHttp404ToRestClientException() {
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> gateway.fetch(url(999L)));
    }
}
~~~

**Tests thực sự kiểm gì?**
- Test 1 xác nhận bean request factory type do cấu hình explicit cung cấp.
- Test 2 chứng minh JDK factory gửi HTTP thật, nhận 200 và JSON converter tạo `Order`.
- Test 3 chứng minh `RestClient.retrieve()` xử lý 404 theo mặc định thành `HttpClientErrorException.NotFound` thay vì trả Order null.
- Server dùng random port để tránh conflict với service địa phương.
- Không cần Internet hay credentials; host là 127.0.0.1 từ test, không bị lấy từ user input.
- Chưa có test timeout, TLS/mTLS, proxy, connection pool saturation hoặc HTTP/2: phải bổ sung riêng theo deployment thật.
- Test không đo QPS, latency p99 hoặc độ hiệu quả pooling.

### 11.8. Chạy project

Ở thư mục có Gradle wrapper phù hợp:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Với server chạy default port 8080, thử:

~~~bash
curl -i 'http://localhost:8080/downstream/orders/1'
curl -i 'http://localhost:8080/downstream/orders/999'
~~~

Dự kiến (response 200):

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"state":"CONFIRMED"}
~~~

Response 404 dự kiến:

~~~http
HTTP/1.1 404 Not Found
~~~

Headers như Date, Content-Length, transfer-encoding, charset có thể khác theo server; test xác nhận status/JSON, không assert byte-for-byte toàn bộ raw HTTP headers. Lệnh curl gọi server fixture, còn JUnit kiểm **RestClient gọi chính fixture này qua JDK request factory**.

## 12. Tùy chọn Apache HttpClient 5 khi cần connection pool

Đây là **một lựa chọn thay thế**, không đặt cùng vào project demo JDK ở mục 11 trừ khi bạn chủ động chuyển cấu hình.

### 12.1. Thêm dependency đúng thế hệ

Trong Gradle (Spring Boot 3.3 quản lý version qua BOM):

~~~groovy
dependencies {
    implementation 'org.apache.httpcomponents.client5:httpclient5'
}
~~~

Nếu dependency tree đã có Apache HttpClient 4, **đừng** cố inject `org.apache.http.client.HttpClient` vào Spring 6 `HttpComponentsClientHttpRequestFactory`; class này yêu cầu Apache HttpClient **5.1+**.

### 12.2. Cấu hình pool + response timeout đúng Spring 6.1

~~~java
import java.time.Duration;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

public final class ApacheFactoryExample {

    public static ClientHttpRequestFactory create() {
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(60)
                .setMaxConnPerRoute(20)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(500))
                .setResponseTimeout(Timeout.ofSeconds(3))
                .build();

        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(client);
        factory.setConnectTimeout(Duration.ofSeconds(2));
        return factory;
    }
}
~~~

Đây là class Java 17 dùng Apache HttpClient 5 APIs tương ứng Spring 6.1. Để Spring quản lý lifecycle, tạo factory trong một `@Bean` và để context destroy; đừng tạo class/factory mới mỗi HTTP request. Nếu được thiết kế ownership khác, bảo đảm `CloseableHttpClient.close()` và connection manager giải phóng đúng một lần.

**Vì sao có ba timeout/limit khác nhau?**
1. `MaxConnTotal(60)`: trần connections của pool (demo).
2. `MaxConnPerRoute(20)`: trần theo route.
3. `ConnectionRequestTimeout(500ms)`: chờ mượn connection.
4. `ResponseTimeout(3s)`: chờ response theo Apache config.
5. `ConnectTimeout(2s)`: giới hạn TCP connect theo Spring factory.

Số trên là **demo**, không phải defaults hay thông số production khuyến nghị. TCP connect/TLS handshake/proxy còn phải kiểm theo client implementation; Spring 6.1 `setConnectTimeout` có giới hạn phạm vi.

### 12.3. Tại sao không dùng `factory.setReadTimeout`?

Ở **Spring Framework 6.1**, `HttpComponentsClientHttpRequestFactory` không có method đó. Đoạn:

~~~java
factory.setReadTimeout(Duration.ofSeconds(3));
~~~

là **ví dụ sai** nếu `factory` có kiểu `HttpComponentsClientHttpRequestFactory` trên Framework 6.1. Phải cấu hình response timeout trên Apache `RequestConfig` như đoạn code trước. JDK `JdkClientHttpRequestFactory` và Simple `SimpleClientHttpRequestFactory` lại có `setReadTimeout` trong 6.1. Không suy từ tên gần giống rằng mọi request factories có bộ setters chung.

### 12.4. Pool sizing theo Little's Law

Giả sử một downstream trung bình mất 200 ms, cần 100 calls/s sustained. Số in-flight requests trung bình theo Little's Law vào khoảng **100 × 0,2 = 20** (với hệ ổn định, chưa xét queue và tails). Nếu per-route max 10, requests có thể xếp hàng ngay cả khi server có nhiều worker threads.

Tuy nhiên dùng pool 20 là **không đủ kết luận thiết kế**: p95/p99 response times, retries, burst, tenant quotas, downstream max connections và TLS overhead đều cần headroom/limits. Benchmark trên workload thật và quan sát pool pending/acquire wait thay vì chỉ đếm peak QPS.

## 13. RestTemplate cũng dùng factory, nhưng API khác RestClient

Trong Spring Boot 3.3, có thể cấu hình RestTemplate:

~~~java
@Bean
RestTemplate customRestTemplate(
        ClientHttpRequestFactory factory) {

    return new RestTemplate(factory);
}
~~~

Hoặc dùng Boot 3.3 `RestTemplateBuilder` để tận dụng customizers/converters:

~~~java
@Bean
RestTemplate customRestTemplate(RestTemplateBuilder builder) {
    return builder
            .requestFactory(JdkClientHttpRequestFactory.class)
            .build();
}
~~~

Đây là fragments trong `@Configuration` với các imports. Chú ý `RestTemplateBuilder.requestFactory(Class<? extends ClientHttpRequestFactory>)` yêu cầu factory có constructor phù hợp; nếu cần JDK `HttpClient` cấu hình tùy biến và timeout cụ thể, dùng supplier/function hoặc explicit factory bean phù hợp.

**Không tự thay toàn bộ RestTemplateBuilder bean** nếu muốn giữ Boot auto-configuration/customizers đang áp dụng. Khi cung cấp builder custom, cần `RestTemplateBuilderConfigurer` đúng Boot 3.3 để giữ thiết lập của Boot, hoặc đơn giản inject builder do Boot cung cấp.

### 13.1. Migration RestTemplate → RestClient

Một chiến lược:
1. Ghi lại timeout, proxy, TLS, redirects, default headers, interceptors, auth và error mapping hiện hữu.
2. Giữ cùng factory/client backend khi chuyển syntax để cô lập rủi ro.
3. So contract với tests 200/4xx/5xx, JSON, content type, malformed body.
4. Xác minh `RestClient.retrieve()` default error mapping khác các wrappers/custom ResponseErrorHandler cũ như thế nào.
5. Sau đó mới thay transport nếu có lý do: performance, pooling, TLS, HTTP/2, proxy.
6. Đo latency/pool/retries trên staging, dùng feature flag/rollout nếu call path trọng yếu.

### 13.2. Tránh cấu hình bị lặp giữa các clients

Có thể dùng một @Configuration quản lý shared HTTP factory settings cho nhiều RestClients, nhưng đừng ép mọi downstream dùng cùng trust store, connection pool, proxy hoặc retry budget. Client cho payment API và client cho public metadata API có thể cần security/timeouts khác nhau.

## 14. Interceptors, request initializers và request factory wrappers

### 14.1. Gắn header/correlation đúng chỗ

~~~java
RestClient client = RestClient.builder()
        .requestFactory(factory)
        .defaultHeader("X-App-Client", "catalog")
        .requestInterceptor((request, body, execution) -> {
            long started = System.nanoTime();
            try {
                return execution.execute(request, body);
            } finally {
                long nanos = System.nanoTime() - started;
                // Export metric với low-cardinality labels.
            }
        })
        .build();
~~~

Đây là fragment có `factory`. Interceptor chạy quanh HTTP execution, có thể quan sát call duration. Không ghi request body bằng mặc định; body trong interceptor có thể là byte[] toàn payload, rủi ro memory/privacy.

### 14.2. BufferingClientHttpRequestFactory

Spring có wrapper:

~~~java
ClientHttpRequestFactory buffering =
        new BufferingClientHttpRequestFactory(factory);
~~~

Nó cho phép lưu request/response streams trong bộ nhớ để có thể đọc lại theo use case. **Tuy nhiên buffering mọi payload** rất nguy hiểm với downloads/uploads lớn và PII. Chỉ bật cho workloads bounded, hoặc tránh entirely bằng metadata-only tracing. Không nghĩ rằng Buffering factory tự giải quyết body streaming/backpressure.

### 14.3. Ghi trace ID nhưng không tin header raw

Nếu lấy correlation ID từ inbound request, validate format/length và sanitize trước khi forward. Không forward Authorization token của user sang một arbitrary URL hoặc redirect target; dùng service-to-service credentials/scopes đúng domain. Với Micrometer/OpenTelemetry, tận dụng integrations và propagation policy thay vì copy mọi headers indiscriminately.

## 15. Request body lớn, streaming và memory pressure

### 15.1. Synchronous không đồng nghĩa luôn buffer

Spring 6.1 đã thay đổi cách buffering request bodies trong nhiều factories. Một `RestClient` call có thể stream bytes tới transport, nhưng:
- `HttpMessageConverter` có thể tự tạo byte[]/String trước khi gửi.
- Interceptors có thể nhận entire serialized byte[].
- Buffering factory có thể lưu request/response body.
- HTTP client backend có buffering/flow control của riêng nó.
- `body(InputStream)` không tự là API streaming chuẩn cho mọi Spring converter.

Nếu cần stream một tệp rất lớn:
1. Chọn factory backend hỗ trợ streaming thực tế.
2. Dùng body writer/input stream/resource API tương thích.
3. Không log/buffer toàn body.
4. Giới hạn request/response bytes, timeouts và disk/memory quotas.
5. Đóng resource kể cả error/cancel.
6. Test với file kích thước thực và server phản hồi chậm.

### 15.2. Synchronous response stream chiếm thread

Đọc một download nhiều GB bằng `RestClient` có thể giữ thread caller trong toàn bộ thời gian đọc. Không nhầm “streaming network bytes” với “non-blocking API”; connection pooling và worker threads vẫn cần dimension. Nếu nhu cầu là fan-out/thousands concurrent downloads, cân nhắc reactive WebClient hoặc async JDK HttpClient với architecture phù hợp.

## 16. Diagnostics: xác định transport và lỗi thực tế

### 16.1. Đừng suy từ dependency tree rằng factory đúng đã được sử dụng

Classpath là một dấu hiệu, không phải bằng chứng; client bean có thể explicitly set khác default. Hãy:
- Kiểm cấu hình bean `requestFactory` của từng RestClient.
- Log loại factory **không chứa secrets** khi khởi động ở môi trường debug.
- Với Apache, đo pool stats (leased, available, pending).
- Với JDK, dùng metrics/tracing ở cấp application và JDK HTTP client logs khi cần (tránh bật body).
- Test redirect/timeout/proxy và TLS thực thay vì chỉ ping HTTP 200.

### 16.2. Spring exception mapping

Một số exception layers:
- `RestClientException`: Spring HTTP client abstraction.
- `RestClientResponseException`: có HTTP error response/status/body.
- `ResourceAccessException`: thường lỗi I/O/transport.
- `HttpClientErrorException`, `HttpServerErrorException`: nhóm lỗi 4xx/5xx từ `retrieve()`.
- Underlying `IOException`/`SocketTimeoutException`/TLS exceptions có thể nằm trong cause chain.

Không rely vào exact exception message theo JDK version. Error handling cần phân loại **retryable vs non-retryable** dựa trên method, status, idempotency và nguyên nhân mạng.

### 16.3. Timeout và retry thường phối hợp sai

Một retry policy có tối đa 3 attempts, mỗi attempt timeout 2 s cộng backoff 1 s có thể vượt HTTP request SLA 3 s. Cần:
- Overall deadline và per-attempt limits.
- Retry only transient failures và HTTP methods/safety phù hợp.
- Exponential backoff + jitter; theo `Retry-After` khi có.
- Circuit breaker, bulkhead, rate limiter.
- Budget cho caller thread và downstream request quotas.
- Correlation IDs/idempotency keys cho POST commands.

Client timeout không chứng minh downstream chưa xử lý request trước khi connection đứt. Với POST có side effects, retry cần idempotency contract hoặc status reconciliation.

### 16.4. Kiểm status 401/403 khi dùng Simple factory

Nếu khi chuyển từ JDK/Apache sang Simple factory nhận exception bất thường ở `HttpURLConnection` error status, kiểm Spring reference note về hành vi Simple implementation. Không chữa bằng cách vô hiệu hóa HTTP error checking hoặc trả default 200; chọn factory phù hợp và có regression test error statuses.

## 17. Các lỗi phổ biến và hướng sửa

| Lỗi / nhầm lẫn | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “RestClient luôn dùng HttpURLConnection” | Backend chọn theo classpath/builder | Gắn explicit `requestFactory` |
| “Reactor Netty factory giúp RestClient non-blocking” | RestClient là synchronous API | Dùng `WebClient` nếu cần reactive |
| Copy Spring 6.2 Apache `setReadTimeout` sang 6.1 | API chưa tồn tại | Apache 5 `RequestConfig.responseTimeout` |
| Dùng Apache HttpClient 4 với Spring 6 factory | Spring 6 yêu cầu 5.1+ | `httpclient5` và đúng imports |
| Chỉ đặt connect timeout | Không chặn read/pool wait vô hạn | Phân tầng timeouts |
| Timeout 0 ở mọi client | Có thể là infinite | Đặt budget/deadline rõ |
| Tạo HttpClient mỗi HTTP request | Mất connection reuse, resources nhiều | Reuse singleton beans |
| Pool max 500 cho downstream giới hạn 10 | Tăng overload phía đối tác | Align quotas, admission, pool sizing |
| Client 404 bị coi là socket failure | Status là HTTP response, không network error | Mapping 4xx/5xx riêng |
| Catch RuntimeException rồi trả empty Order | Giấu auth/timeout/5xx/bug | Typed status/error policy |
| Bật follow redirect mọi URL | Rủi ro SSRF/credential leak | Trusted destination checks |
| Tắt SSL hostname verification | Nguy cơ MITM | Trust chain đúng, mTLS/certs |
| Dùng user-supplied URL trực tiếp | SSRF | Fixed base URL, allowlist + egress controls |
| Interceptor log raw Authorization/body | Leak secrets | Redaction/metadata-only tracing |
| Buffering all responses | Heap pressure | Limit payload, avoid wrapper |
| Quên close Apache manager/client | Socket/resource leaks | Bean lifecycle/close ownership |
| Một bean factory bị nhiều owner close | Double shutdown shared resources | Single owner rõ ràng |
| Dùng RestClient trên WebFlux Netty loop | Blocking calls stall event loop | WebClient hoặc bounded worker |
| Chỉ test HTTP 200 localhost | Chưa test real TLS/proxy/timeout | Integration/load tests theo contract |
| Dùng Boot 4 property trên Boot 3.3 | Version mismatch | API/docs đúng platform |
| Suy từ error message để phân loại timeout | Message không ổn định | Exception types/root causes |
| Auto retries POST sau socket reset | Có thể duplicate side effects | Idempotency/reconciliation |

### Debug có trình tự

1. Xác nhận Java, Spring Boot, Spring Framework versions và BOM.
2. Xem đúng factory bean và underlying HTTP library dependency.
3. Xác minh RestClient/RestTemplate đã gắn factory đó, không bị builder khác override.
4. Kiểm host/port, trusted URL, DNS resolution và proxy route.
5. Kiểm connect, pool acquire, TLS, response timeouts riêng.
6. Kiểm 2xx, 4xx, 5xx và malformed JSON bằng tests.
7. Nếu có concurrency, đo threads, active/pending/idle connections, queue.
8. Kiểm interceptors không buffer/log credentials.
9. Kiểm response/stream/client lifecycle, resource cleanup.
10. Test rollout staging với egress/TLS/proxy và failure injection.

## 18. Checklist trước production

- [ ] Chọn factory dựa trên workload/feature/security chứ không chỉ default.
- [ ] Java/Spring/HTTP client dependency versions tương thích.
- [ ] Apache HttpClient đúng 5.x cho Spring 6.x.
- [ ] Kiểm Spring 6.1/6.2 API khác nhau, nhất là Apache setReadTimeout.
- [ ] RestClient/RestTemplate beans dùng reusable factory và connection resources.
- [ ] Factory lifecycle/CloseableHttpClient ownership rõ.
- [ ] Connect, pool acquire, TLS, response/read và overall deadlines có budget.
- [ ] Không dùng 0/infinite timeout không chủ đích.
- [ ] Pool limits phù hợp downstream concurrency/rate limits.
- [ ] TLS trust/hostname verification không bị tắt.
- [ ] Proxy/mTLS/redirect policies được review.
- [ ] Không gọi URL do user chỉ định mà không có SSRF defenses.
- [ ] Credentials được scope theo destination, không forward tùy ý.
- [ ] Header/body logging redacted, tránh buffering large streams.
- [ ] Typed error handling 401/403/404/429/5xx/transport failures.
- [ ] POST retries có idempotency hoặc reconciliation.
- [ ] HTTP response resources được close đúng khi dùng API thấp.
- [ ] Không gọi synchronous RestClient trên reactive event loop.
- [ ] JUnit integration test HTTP thật trên random port.
- [ ] Có tests TLS/proxy/timeouts/redirect/pooling trên infrastructure tương ứng.
- [ ] Đo p95/p99, pool pending/acquire, exceptions và outbound retries.
- [ ] Duy trì transport config & dependency security patch policy.

### Câu hỏi tự kiểm tra

**1.** `ClientHttpRequestFactory` thuộc layer nào?

**Trả lời:** Abstraction tạo executable client HTTP request của Spring, nằm dưới `RestClient`/`RestTemplate` và message conversion, trên backend transport.

**2.** Nếu chuyển từ RestTemplate sang RestClient, bắt buộc đổi HTTP library không?

**Trả lời:** Không. Có thể giữ cùng ClientHttpRequestFactory để tách rủi ro migration API khỏi migration transport.

**3.** Vì sao JDK factory phù hợp Java 17 service đơn giản?

**Trả lời:** Có sẵn `java.net.http.HttpClient`, reuse connections, SSL/proxy/redirect configuration, không cần Apache dependency nếu workload không cần pool tuning đặc thù.

**4.** Khi nào Apache HttpClient 5 có lợi?

**Trả lời:** Khi cần pooling max total/per route, lease timeout, proxy/TLS và connection management sâu với tải outbound cao.

**5.** Spring 6.1 `HttpComponentsClientHttpRequestFactory` có `setReadTimeout` không?

**Trả lời:** Không. Cấu hình `RequestConfig.setResponseTimeout` trên underlying Apache HttpClient 5; `setReadTimeout` của HttpComponents factory thuộc phiên bản mới hơn.

**6.** ConnectionRequestTimeout khác ConnectTimeout ra sao?

**Trả lời:** ConnectionRequestTimeout giới hạn thời gian chờ mượn connection từ Apache pool; ConnectTimeout giới hạn giai đoạn tạo TCP connection theo client config.

**7.** Tại sao RestClient với ReactorNettyClientRequestFactory vẫn blocking?

**Trả lời:** RestClient có synchronous caller contract; backend có thể dùng event loops nội bộ nhưng caller vẫn phải chờ kết quả.

**8.** `RestClient.retrieve()` nhận HTTP 404 thì có bắt buộc nhận DTO null không?

**Trả lời:** Không. Mặc định 4xx/5xx thường phát HTTP status exception; phải mapping rõ hoặc dùng status handler/exchange.

**9.** Có nên dùng BufferingClientHttpRequestFactory cho mọi outbound request?

**Trả lời:** Không. Nó có thể buffer toàn payload trong memory, gây OOM và lộ dữ liệu nhạy cảm nếu log.

**10.** Vì sao redirect và URL từ user nguy hiểm khi dùng HTTP client server-side?

**Trả lời:** Có thể dẫn đến SSRF vào nội bộ/cloud metadata và rò credentials sang origin ngoài; cần fixed/allowlisted destinations, egress controls và redirect policy.

## 19. Tài liệu chính thức

- [Spring Framework 6.1 — ClientHttpRequestFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/ClientHttpRequestFactory.html): interface `createRequest(URI,HttpMethod)` và implementations.
- [Spring Framework 6.1 — JdkClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html): constructors, `setReadTimeout`.
- [Spring Framework 6.1 — SimpleClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/SimpleClientHttpRequestFactory.html): HttpURLConnection, connect/read timeout và buffering deprecation.
- [Spring Framework 6.1 — HttpComponentsClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html): Apache HttpClient 5, connect/lease timeouts và pooling.
- [Spring Framework 6.1 — JettyClientHttpRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/JettyClientHttpRequestFactory.html): Jetty HttpClient adapter.
- [Spring Framework 6.1 — ReactorNettyClientRequestFactory](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/ReactorNettyClientRequestFactory.html): Reactor Netty adapter cho sync clients.
- [Spring Framework 6.1 — RestClient.Builder](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/RestClient.Builder.html): `requestFactory` và builder configuration.
- [Spring Framework 6.1 — REST Clients Reference](https://docs.spring.io/spring-framework/reference/6.1/integration/rest-clients.html): RestClient, RestTemplate, request factories và selection.
- [Spring Boot 3.3 — Calling REST Services](https://docs.spring.io/spring-boot/3.3/reference/io/rest-client.html): RestClient.Builder, RestTemplateBuilder và auto configuration.
- [Apache HttpClient 5 — Classic Client](https://hc.apache.org/httpcomponents-client-5.4.x/current/httpclient5/apidocs/): connection manager, RequestConfig, response timeout.
- [JDK 17 — java.net.http.HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): HTTP/1.1/2, SSL, redirects, proxy, connection handling.
- [Spring Framework — HTTP Client Test Utilities](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html): MockRestServiceServer khi cần mock behavior; integration với server thật bổ sung transport tests.
- [OWASP — SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html): allowlists, DNS, redirects và network restrictions.

**Ghi nhớ:** `RestClient`/`RestTemplate` là API đồng bộ; `ClientHttpRequestFactory` chọn HTTP backend. JDK HttpClient là lựa chọn khởi đầu hợp lý trên Java 17 nếu nhu cầu đơn giản; Apache HttpClient 5 phù hợp khi cần pool/timeout/proxy/TLS tuning sâu. Đừng để transport thay đổi ngoài ý muốn theo classpath, đừng nhầm sync với non-blocking, và luôn cấu hình deadlines, lifecycle, credentials, SSRF policy, logging cùng integration tests trước khi triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-007">Timeout, connection pool, TLS và vòng đời tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Timeout, connection pool, TLS và vòng đời tài nguyên của HTTP client đồng bộ

## 1. Vấn đề thực tế và mục tiêu

Trong một ứng dụng Spring MVC, batch job hay Java service, khi gọi REST API bằng `RestClient` hoặc `RestTemplate`, caller thường **chờ đồng bộ** cho đến khi có HTTP response hoặc lỗi. Nếu upstream chậm, việc chờ này có thể giữ Java worker thread, chiếm một connection trong outbound pool, tạo pending queue và kéo theo timeout dây chuyền. Ngay cả API có business logic đơn giản vẫn có thể cạn worker threads, file descriptors hay sockets nếu các giới hạn không được cấu hình.

Bốn vấn đề phải được xử lý đồng thời:

1. **Timeout:** mất bao lâu để lấy connection từ pool, DNS/TCP connect, TLS handshake, gửi request và chờ response? Giới hạn thời gian nào bảo vệ toàn bộ HTTP call?
2. **Connection pool:** connection được tạo, cho thuê, trả lại, tái sử dụng, kiểm tra stale và đóng theo policy nào? Chia giới hạn tổng và theo route thế nào?
3. **TLS:** client tin certificate nào, xác minh hostname ra sao, khi nào dùng truststore, keystore, mTLS? Làm gì khi gặp `SSLHandshakeException`?
4. **Resource lifecycle:** RestClient/factory/pool được tạo một lần hay từng request, ai là chủ sở hữu để đóng, khi nào dọn idle connections và response streams?

**Phạm vi:** Java 17, Spring Framework 6.1/6.2, Spring Boot 3.3.x, Spring `RestClient` và `RestTemplate`, JDK `java.net.http.HttpClient`, Apache HttpClient 5.x. Đây là bài **HTTP client đồng bộ**, không phải WebClient reactive. Bản ví dụ hoàn chỉnh sử dụng Spring Boot **3.3.13** / Java **17** và Apache HttpClient 5, có mock HTTP upstream chạy trên localhost, không cần gọi dịch vụ Internet.

### Học xong bạn cần trả lời được

- Timeout kết nối khác timeout đợi response và chờ thuê connection như thế nào?
- Vì sao 700 ms response timeout **không** bảo đảm toàn bộ call kết thúc trước 700 ms?
- Khi pool max 5 connections tới một upstream, request thứ sáu đi đâu?
- Vì sao keep-alive giảm TCP/TLS handshake, nhưng idle connection cũng có thể stale?
- RestClient có tự động đảm bảo cùng pool/timeouts trên mọi môi trường không?
- Spring 6.1 và Spring 6.2 khác nhau thế nào ở HttpComponents request factory?
- PKIX/hostname mismatch phải xử lý thế nào **mà không tắt TLS verification**?
- Khi POST timeout, retry có thể tạo duplicate record không?
- Vì sao Java 17 JDK HttpClient chưa có `close()` public như Java 21?
- Làm sao viết JUnit tests kiểm chứng HTTP status vs transport timeout và đóng pool đúng lifecycle?

## 2. Một synchronous HTTP call gồm các giai đoạn nào?

```text
Inbound MVC request / background job
                 |
                 v
       RestClient.get(...).retrieve().body(...)
                 |
          Caller thread chờ
                 |
       ClientHttpRequestFactory
                 |
                 +--- [A] Pool lease / acquire connection
                 |         Có thể phải chờ nếu pool đầy
                 |
                 +--- [B] DNS resolution (nếu chưa cache)
                 |
                 +--- [C] TCP connect (nếu không reuse)
                 |
                 +--- [D] TLS handshake + certificate/hostname check
                 |         Chỉ HTTPS, thường không cần lặp khi reuse
                 |
                 +--- [E] Gửi HTTP headers/body
                 |
                 +--- [F] Chờ status/headers/body
                 |
                 +--- [G] Decode JSON, xử lý HTTP error status
                 |
                 +--- [H] Consume/close response, release pool resource
                 |
                 v
        Trả DTO/ResponseEntity hoặc exception
```

Một request có thể bỏ qua [B]–[D] khi connection đang sống được reuse. Với HTTP/2, nhiều logical streams có thể chia sẻ một connection khi client/transport và server hỗ trợ; không giả định HTTP/2 trên tất cả synchronous HTTP clients.

**Connection không phải worker thread.** Apache connection pool quản lý outbound sockets; Tomcat worker pool quản lý inbound request execution. Nếu 100 Tomcat workers cùng muốn gọi một upstream mà Apache per-route limit chỉ 10, phần lớn có thể đang chờ acquire. Tăng Tomcat maxThreads không khắc phục được outbound pool saturation.

## 3. Phân loại timeout: đúng tầng, đúng ý nghĩa

| Tên | Giai đoạn | Ý nghĩa |
| --- | --- | --- |
| Pool acquire / connection-request timeout | [A] | Thời gian tối đa chờ một connection từ pool |
| DNS lookup timeout | [B] | Phụ thuộc resolver/OS/JVM/client; không luôn có setting độc lập |
| TCP connect timeout | [C] | Giới hạn kết nối mới tới host:port |
| TLS handshake timeout | [D] | Giới hạn phiên TLS/ALPN và certificate exchange; có thể cấu hình riêng |
| Write/socket timeout | [E] | Chờ ghi bytes; semantics tùy engine |
| Response timeout | [F] | Đợi response theo HTTP client, không mặc nhiên toàn call |
| Socket read inactivity timeout | [F] | Đợi dữ liệu tiếp theo; stream có thể kéo dài qua nhiều khoảng |
| Whole-call deadline | [A]–[H] | Ngân sách thời gian nghiệp vụ tổng thể; thường cần policy riêng |
| Idle timeout / connection TTL | Sau call | Khi nào pool đóng socket không dùng hoặc quá tuổi |
| Gateway/proxy timeout | Ngoài JVM | Có thể trả 502/504 dù client/remote có policy khác |

### 3.1. Connect timeout chỉ giới hạn một giai đoạn

Giả sử TCP kết nối sau 5 ms, server giữ request 15 giây rồi trả body. Nếu client chỉ đặt connect timeout 1 giây, caller vẫn có thể chờ lâu vì bước connect đã hoàn tất. Cần timeout cho response/read và **overall deadline** khi business có SLA.

### 3.2. Lease timeout khác connect timeout

Nếu Apache pool max 5 connections và đã có 5 calls đang bận, call thứ sáu có thể chờ **trong pool trước khi mở bất kỳ TCP socket mới nào**. Nếu lease timeout 200 ms và không có connection được trả về kịp, call có thể fail mà upstream không nhìn thấy request thứ sáu. Việc tăng TCP connect timeout không giúp.

### 3.3. TLS handshake không đơn giản thuộc connect timeout

Trong Spring Framework 6.1, `HttpComponentsClientHttpRequestFactory#setConnectTimeout` cấu hình Apache RequestConfig connection timeout nhưng Javadoc cảnh báo **không bao trùm SSL handshakes hoặc HTTP CONNECT**; có thể cần low-level socket/TLS config. Với JDK HttpClient, thời gian TLS/request thuộc các APIs và implementation semantics khác. Không dùng cụm “connectTimeout bao luôn TLS” khi chưa xác minh engine/version.

### 3.4. Response timeout không phải total wall-clock cap

Một server có thể gửi headers rồi stream body thành nhiều chunks, mỗi chunk đến trước ngưỡng inactivity timeout nhưng tổng download mất hàng phút. Các response/socket timeouts có semantics theo engine. Nếu business cần SLA 2 giây, phải đo deadline end-to-end và phối hợp timeout/cancellation/retry policy; riêng `setResponseTimeout(2s)` chưa đủ để cam kết mọi byte đã đọc xong trước 2 giây.

### 3.5. Timeout có thể không dừng side effect từ remote

Client gửi POST tạo đơn hàng. Server đã lưu vào database, nhưng response bị mất giữa đường và client read timeout. Client **không thể suy ra operation đã rollback**. Retry POST không kiểm soát sẽ tạo đơn trùng. Cần idempotency key, unique constraints, bounded retries và quy tắc business rõ.

### 3.6. Ví dụ tính latency budget

API inbound có SLA 1.000 ms; một cách phân bổ minh họa:

| Bước | Budget ví dụ |
| --- | ---: |
| Authentication/validation | 100 ms |
| Lease outbound connection | 100 ms |
| TCP/TLS nếu cần mới | 200 ms |
| Wait upstream response | 450 ms |
| Encode/send inbound response, contingency | 150 ms |

Đây không phải benchmark: mỗi stage có thể overlap hoặc chậm bất thường; số liệu phải lấy từ metrics. Nếu downstream timeout lớn hơn SLA inbound, request dễ bị gateway trả 504 trước khi caller biết lỗi.

## 4. RestClient, RestTemplate và factory thật sự làm I/O

### 4.1. RestClient (Spring Framework 6.1+)

`RestClient` là API fluent **đồng bộ**:

```java
RestClient client = RestClient.builder()
    .baseUrl("https://api.example.test")
    .build();

Product product = client.get()
    .uri("/products/{id}", 42L)
    .retrieve()
    .body(Product.class);
```

Đây là snippet minh họa, cần DTO `Product` và server thật. Khi `retrieve()` nhận 4xx/5xx, Spring thường ném `RestClientResponseException` subtype tương ứng. Khi I/O lỗi, thường có `ResourceAccessException` với root cause khác. Khi 204 No Content hoặc body trống, `body(Product.class)` có thể trả null; tránh unbox/deserialize như thể luôn có object.

### 4.2. RestTemplate cũng đồng bộ

`RestTemplate` được Spring tiếp tục hỗ trợ, nhưng RestClient thuận tiện hơn với fluent style. Cả hai sử dụng `ClientHttpRequestFactory` để thực hiện I/O:

```java
RestTemplate template = new RestTemplate(requestFactory);
Product product = template.getForObject(
    "https://api.example.test/products/42", Product.class);
```

Đây là snippet có `requestFactory`/Product cần được cấu hình. Migrate RestTemplate sang RestClient **không tự** tạo timeouts/pool nếu request factory thực vẫn không được kiểm soát.

### 4.3. Các transport factories

| Spring factory | HTTP engine | Ý nghĩa |
| --- | --- | --- |
| `JdkClientHttpRequestFactory` | Java `java.net.http.HttpClient` | Có connect/read timeout APIs, JDK-managed connections |
| `HttpComponentsClientHttpRequestFactory` | Apache HttpClient 5 | Pool metrics/limits và lifecycle explicit |
| `SimpleClientHttpRequestFactory` | Java `HttpURLConnection` | Đơn giản, không có API pool tường minh như Apache |
| `JettyClientHttpRequestFactory` | Jetty HTTP client | Tuning riêng |
| `ReactorNettyClientRequestFactory` | Reactor Netty | Một request factory có thể dùng dưới synchronous RestClient facade |

Nếu không set factory, selection phụ thuộc Spring Framework version, HTTP libraries trên classpath và Spring Boot auto-configuration. **Không** giả định tất cả environments sử dụng chung các pool/timeout defaults. Với production, cấu hình request factory tường minh để dễ review/test.

### 4.4. RestClient.Builder do Boot quản lý

Spring Boot tạo `RestClient.Builder` đã có message converters, customizers và observation hooks theo application environment. Nên inject builder, tạo một RestClient bean/service-scoped client với base URL và factory nhất quán, thay vì `RestClient.create()` cho mỗi request.

```java
@Service
class ExternalGateway {
    private final RestClient client;

    ExternalGateway(RestClient.Builder builder) {
        client = builder.baseUrl("https://api.example.test")
            .build();
    }
}
```

Builder là mutable; khi dùng chung để build nhiều target clients, cân nhắc `clone()` hoặc các builder injections riêng. Không mutate builder trong request concurrent.

## 5. JDK HttpClient trên Java 17

Java 17 cung cấp `java.net.http.HttpClient`. Nó có pool/reuse phía implementation, redirects, SSLContext và HTTP/2 negotiation tùy server. Nhưng JDK 17 **không** có public API chuẩn tương đương Apache `setMaxConnPerRoute`/`getTotalStats` để giới hạn và quan sát pool chi tiết.

### 5.1. Tích hợp RestClient với JDK request factory

```java
HttpClient jdk = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

JdkClientHttpRequestFactory factory =
    new JdkClientHttpRequestFactory(jdk);
factory.setReadTimeout(Duration.ofSeconds(3));

RestClient client = RestClient.builder()
    .requestFactory(factory)
    .baseUrl("https://api.example.test")
    .build();
```

Đây là snippet Java17/Spring6.1+, cần imports `java.net.http.HttpClient`, `Duration` và Spring types. JDK request factory read timeout ánh xạ tới `HttpRequest.Builder.timeout` theo Javadoc Spring. Nó không giống hoàn toàn Apache socket read timeout trong mọi trường hợp.

### 5.2. Java HttpClient trực tiếp

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.test/data"))
    .timeout(Duration.ofSeconds(3))
    .GET()
    .build();

HttpResponse<String> response =
    jdk.send(request, HttpResponse.BodyHandlers.ofString());
```

`send` chặn thread gọi, còn `sendAsync` trả CompletableFuture và có lifecycle khác. Nếu dùng `BodyHandlers.ofInputStream()` hoặc `ofLines()`, cần consume/close stream đúng cách; nếu bỏ ngang, implementation có thể không reuse kết nối và giữ resources.

### 5.3. Lifecycle JDK 17 khác JDK 21

Trong **Java 17**, `java.net.http.HttpClient` chưa có public `close()`/`shutdown()` và không implement AutoCloseable như ở **Java 21**. Không viết mã Java17 `try (HttpClient c = ...)` hoặc `c.close()`. Reuse một instance ứng dụng thay vì tạo nhiều client tùy tiện; nếu yêu cầu pool lifecycle tường minh và metrics ở Java17, Apache HttpClient 5 thường thuận tiện hơn.

## 6. Apache HttpClient 5: pool, timeout và lifecycle rõ ràng

Apache 5 classic synchronous client thường được dùng bên dưới `HttpComponentsClientHttpRequestFactory`. Những tham số cơ bản:

| API/config | Công dụng |
| --- | --- |
| `PoolingHttpClientConnectionManagerBuilder.setMaxConnTotal(n)` | Số connections tối đa toàn pool |
| `setMaxConnPerRoute(n)` | Số connections tối đa tới một route |
| `ConnectionConfig.setConnectTimeout(...)` | TCP connect timeout |
| `ConnectionConfig.setSocketTimeout(...)` | Socket I/O timeout |
| `ConnectionConfig.setTimeToLive(...)` | Tuổi tối đa connection |
| `ConnectionConfig.setValidateAfterInactivity(...)` | Check pooled connection stale sau idle |
| `RequestConfig.setConnectionRequestTimeout(...)` | Chờ lấy connection từ manager |
| `RequestConfig.setResponseTimeout(...)` | Chờ response theo Apache semantics |
| `HttpClientBuilder.evictIdleConnections(...)` | Dọn idle connections |
| `evictExpiredConnections()` | Dọn connections quá tuổi |
| `HttpClientBuilder.disableAutomaticRetries()` | Tắt Apache-level retries để tự quản policy |

### 6.1. Chú ý về Spring 6.1 và 6.2

`HttpComponentsClientHttpRequestFactory` Spring **6.1** cung cấp `setConnectTimeout(Duration)` và `setConnectionRequestTimeout(Duration)` nhưng **chưa có `setReadTimeout(Duration)`**. Spring **6.2** bổ sung readTimeout setter cho Apache factory. Do đó trên Spring Boot 3.3.x (Spring Framework 6.1.x), cấu hình response timeout thông qua `RequestConfig.setResponseTimeout(...)` của Apache 5 là cách phù hợp. Trong khi đó, `JdkClientHttpRequestFactory.setReadTimeout(Duration)` đã có từ Spring 6.1.

Copy một blog dùng Spring 6.2 vào Boot 3.3 rồi gọi `factory.setReadTimeout(Duration...)` trên Apache factory có thể fail lúc compile. Phân biệt theo **actual class và Spring version**, không chỉ tên phương thức giống nhau.

### 6.2. Pool total và per-route

Nếu total=20, per-route=5, một upstream có tối đa 5 established/leased connections theo route; HTTP requests tiếp theo có thể xếp hàng để lease tới khi timeout hoặc connection được trả. Một "route" gắn với scheme/host/port/proxy path, không phải URL path như `/orders` vs `/customers`. Do đó nhiều API endpoints trên cùng host có thể tranh cùng giới hạn.

### 6.3. Pool size không phải caller concurrency

Application Tomcat có thể có 200 workers; Apache outbound pool chỉ có 10. 190 workers vẫn có thể đứng chờ lease. Tăng total connections có thể làm remote quá tải. Cần phối hợp caller thread pool, semaphores/bulkheads, rate limits và backend capacity.

### 6.4. Connection reused vs stale

Khi keep-alive còn hiệu lực, connection đã hoàn tất request A có thể xử lý request B mà không cần TCP/TLS handshake mới. Nếu load balancer đóng socket vì idle nhưng client không biết, reuse có thể fail. `validateAfterInactivity`, idle eviction, TTL và retry có kiểm soát giúp giảm stale reuse, nhưng không bảo đảm mọi network race biến mất.

### 6.5. Vì sao phải close body?

Khi HTTP response body đã được consume hoặc response được đóng đúng, engine có cơ hội trả/loại bỏ connection đúng trạng thái. Nếu nhận raw InputStream rồi không close, pool có thể giữ connection leased cho đến khi timeout/GC/transport abort. `RestClient.retrieve().body(Dto.class)` thường quản lý vòng đời decode thông thường; nhưng `exchange` callback/custom stream hoặc Apache low-level response phải tuân thủ contract close/consume.

## 7. TLS: trust, hostname và mTLS

### 7.1. HTTPS cần xác thực đối tác, không chỉ mã hóa

Khi kết nối `https://orders.example.test`, TLS client cần xác minh:

1. Certificate chain từ server có dẫn tới trust anchor được tin cậy không?
2. Certificate còn hiệu lực, đúng key usage/algorithm policies không?
3. SAN có khớp hostname trong URL **orders.example.test** không?
4. TLS protocol/cipher/ALPN có tương thích không?
5. Nếu server yêu cầu client certificate (mTLS), client có key/cert hợp lệ không?

Nếu server gửi certificate đúng CA nhưng SAN chỉ có `payments.example.test`, client phải reject cho `orders.example.test`. **Trust chain và hostname verification là hai việc bổ sung**, không thay nhau.

### 7.2. Truststore vs keystore

| Artifact | Nội dung | Dùng khi |
| --- | --- | --- |
| Truststore | CA certificates/trust anchors | Client xác minh server |
| Client keystore | Private key + client certificate chain | Server yêu cầu mTLS |
| SSLContext | Key/trust managers + TLS settings | Client cấu hình TLS |
| Hostname verification | So URL host và certificate SAN | Ngăn MITM endpoint giả |
| System trust | Root CA bundle/policy của JDK | HTTPS thông thường |

Không dùng header `X-Client-Cert` do untrusted client tự cung cấp làm bằng chứng mTLS. Nếu qua gateway, trust boundary/cert forwarding phải được thiết kế chặt.

### 7.3. Truststore riêng trong Java

Ví dụ minh họa Java17 (không phải file độc lập):

```java
KeyStore trust = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(
        Path.of("/secure/api-truststore.p12"))) {
    trust.load(in, password);
}

TrustManagerFactory tmf =
    TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trust);

SSLContext tls = SSLContext.getInstance("TLS");
tls.init(null, tmf.getTrustManagers(),
    new SecureRandom());

HttpClient httpsClient = HttpClient.newBuilder()
    .sslContext(tls)
    .connectTimeout(Duration.ofSeconds(2))
    .build();
```

Cần `KeyStore`, `InputStream`, `Files/Path`, `TrustManagerFactory`, `SSLContext`, `SecureRandom`, `HttpClient` và `Duration` imports. `password` là `char[]` lấy từ secrets, không hardcode. Custom truststore có thể **thay** system default roots; nếu muốn đồng thời tin public/private CA cần trust policy riêng. Không gộp trust managers bằng cách chấp nhận mọi certificate.

### 7.4. mTLS thêm client private key

Sau khi load client keystore, khởi tạo `KeyManagerFactory` và:

```java
SSLContext tls = SSLContext.getInstance("TLS");
tls.init(keyManagerFactory.getKeyManagers(),
    trustManagerFactory.getTrustManagers(),
    new SecureRandom());
```

Đây là snippet chỉ minh họa, cần load cả stores. mTLS xác nhận client certificate nhưng authorization người dùng/resource vẫn riêng biệt. Certificate/key rotation và grace period phải được lên kế hoạch.

### 7.5. Không sửa SSL lỗi bằng trust-all

**Không sử dụng**:

- `TrustManager.checkServerTrusted` method trống;
- `NoopHostnameVerifier.INSTANCE`;
- property tắt hostname verification;
- trust toàn bộ untrusted self-signed certificate;
- HTTP plaintext để tránh certificate issue trên traffic chứa credentials.

Khi gặp `PKIX path building failed` hoặc `SSLHandshakeException`, điều tra CA/intermediate chain, SAN, expiry, clock skew, protocol/cipher, proxy TLS interception và mTLS requirement. Nếu upstream dùng private CA, cấu hình truststore hợp lệ; **không bỏ kiểm tra certificate**.

### 7.6. JVM truststore system properties và Apache client

Một số engines có thể dùng hệ thống JSSE trust properties khi được cấu hình `useSystemProperties()`. Nhưng không giả định **mọi** Apache custom SSLContext đều tự nhận `-Djavax.net.ssl.trustStore=...`. Với đa client/tenant, nên cấu hình per-client truststore, tránh thay JVM global trust state ngoài ý muốn.

### 7.7. TLS handshake và pool reuse

Khi connection reused, không cần làm lại TLS handshake cho mỗi request. Khi bị idle eviction/TTL/remote close, kết nối mới cần handshake; p99 có thể khác giữa cold start và warm pool. HTTP/2 multiplexing, TLS session resumption, OCSP và ALPN còn làm khác chi phí connection. Benchmark cần xét cả hai trường hợp.

## 8. Timeout + retry + error taxonomy

### 8.1. Phân loại lỗi

| Trường hợp | Biểu hiện điển hình | Phản ứng |
| --- | --- | --- |
| 404 | `HttpClientErrorException.NotFound` với `retrieve()` | Nghiệp vụ không tìm thấy (nếu contract nói vậy) |
| 429 | TooManyRequests | Rate limit/Retry-After/backoff |
| 503 | ServiceUnavailable | Service outage, circuit breaker |
| TCP connect refused | `ResourceAccessException` bọc ConnectException | Host/port/listener/network |
| Connect timeout | ResourceAccessException/root timeout | Network path/DNS/firewall |
| Pool lease timeout | Apache request connection manager timeout | pool stats, active/pending |
| Response/socket timeout | I/O exception | upstream latency, response policy |
| Bad certificate | `SSLHandshakeException`/PKIX | chain, truststore, hostname |
| JSON decode error | message conversion exception | DTO/schema/media type |
| 204 response | body có thể null | API contract no-content |
| Redirect ngoài host | 3xx/redirect policy | chống SSRF/credential leak |

Các exception chi tiết thay đổi theo Spring/Apache/JDK version; log root cause an toàn và phân biệt HTTP status (remote thực sự trả) với I/O failure (có thể chưa có HTTP response).

### 8.2. Retry không phải công tắc bật là xong

- HTTP 400/401/403: thường không retry.
- 404: chỉ retry khi eventual consistency đã có thỏa thuận.
- 429: giảm tốc, tôn trọng `Retry-After` nếu có.
- 500/502/503/504: có thể retry giới hạn nếu idempotent và còn deadline.
- TCP reset/timeout: không biết remote operation có chạy xong chưa.
- POST create: dùng idempotency key và server-side deduplication khi retry có thể xảy ra.

Đồng thời kiểm tra tầng Apache auto retries, Spring retry, gateway retries và orchestration retries để không tạo retry multiplication. Trong lab Apache automatic retries bị tắt, bảo đảm tests dễ hiểu.

### 8.3. Circuit breaker khác timeout

Timeout chặn một lần chờ quá lâu; circuit breaker tạm giảm calls tới upstream lỗi; bulkhead giới hạn concurrency; rate limiter điều chỉnh tốc độ; retry có thể tăng traffic. Chúng bổ sung nhau, không thay thế. Nếu circuit breaker dùng metrics 503, không coi mọi exception (ví dụ 404) là backend unhealthy.

## 9. Resource lifecycle: ai đóng client và khi nào?

### 9.1. Không new HTTP client cho từng request

Anti-pattern:

```java
@GetMapping("/proxy")
Product wrong() {
    RestClient client = RestClient.builder()
        .baseUrl("https://api.example.test")
        .build();
    return client.get()
        .uri("/product").retrieve().body(Product.class);
}
```

Code minh họa, không độc lập; vấn đề là tạo client/factory liên tục có thể tạo nhiều transports/pools và không có owner đóng, mất reuse, tăng socket churn. Tốt hơn: singleton `RestClient` được inject theo upstream với một shared factory/pool đã quản lý.

### 9.2. Ownership rõ ràng

Với Apache:

```text
Spring ApplicationContext
  └── HttpComponentsClientHttpRequestFactory bean
        └── owns CloseableHttpClient
              └── owns PoolingHttpClientConnectionManager
                    └── socket connections + optional evictor thread

ApplicationContext shutdown
  └── factory.destroy()
        └── closes Apache client / manager / workers
```

Tránh khai báo `CloseableHttpClient` vừa là bean có destroy callback, vừa được factory đóng lần nữa mà không xác định ownership. Trong ví dụ runnable, **factory là owner duy nhất**, chứa Apache client trong method và có `@Bean(destroyMethod="destroy")`.

### 9.3. Response streaming và raw entities

Nếu dùng low-level Apache response:

```java
try (CloseableHttpResponse response =
        apache.execute(new HttpGet(url))) {
    // Đọc entity/body trong scope.
}
```

Đây là snippet, cần imports và `apache` đã tạo. Không trả trực tiếp raw InputStream ra ngoài `try` rồi đọc sau đó, không bỏ response body unconsumed; nếu response không thể tái sử dụng, engine cần được phép hủy connection thay vì giữ leased mãi.

### 9.4. Graceful shutdown và active calls

`server.shutdown=graceful` là cấu hình **inbound** web server; nó không tự bảo đảm mọi outbound pool đã drain. Spring lifecycle phải ngừng nhận việc mới, cho active calls deadline phù hợp rồi đóng pools. Chú ý điều gì xảy ra nếu HTTP clients đang thực hiện request khi bean destroy; integration test shutdown/cancellation phù hợp.

### 9.5. Pool eviction và metrics

Idle eviction/TTL giúp giảm stale sockets và tài nguyên rảnh, nhưng idle policy quá ngắn tăng CPU/TLS handshakes. Giám sát `leased`/`available`/`pending`/`max`, thời gian chờ lease, active caller threads và upstream p95/p99. Một pooled connection còn leased có thể là call dài hợp lệ, không nhất thiết leak.

## 10. Demo chạy được: Spring Boot 3.3.13, Java 17, Apache 5

Demo có **hai mode** trong cùng Spring Boot fat JAR:

- Mode `--mock-upstream`: chạy JDK `com.sun.net.httpserver.HttpServer` trên `127.0.0.1:9090`; endpoint `/fast`, `/slow`, `/missing`, `/unavailable`, `/echo`. Không dùng TLS vì chỉ là localhost lab.
- Mode mặc định: Spring MVC web app trên port 8080, tạo **một** Apache HTTP client pool tường minh, Spring RestClient gọi mock server đồng bộ.
- JUnit: dùng mock server trên ephemeral port thay vì 9090 để tests không phụ thuộc port đang rảnh.

### 10.1. Cây thư mục

```text
http-client-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/
    │   │   ├── HttpClientDemoApplication.java
    │   │   ├── Message.java
    │   │   ├── ClientConfiguration.java
    │   │   ├── UpstreamGateway.java
    │   │   ├── GatewayController.java
    │   │   └── MockUpstreamServer.java
    │   └── resources/application.properties
    └── test/java/example/
        └── UpstreamGatewayTest.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>http-client-demo</artifactId>
  <version>1.0.0</version>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.13</version>
    <relativePath/>
  </parent>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.apache.httpcomponents.client5</groupId>
      <artifactId>httpclient5</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

Dependency versions do Spring Boot quản lý; không lấy một HttpClient 5 alpha/release khác tự ý để tránh mismatch.

### 10.3. Message.java

```java
package example;
public record Message(String value) {}
```

Record là JSON DTO cho mock response.

### 10.4. ClientConfiguration.java — pool & timeouts

```java
package example;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfiguration {

    @Bean(destroyMethod = "destroy")
    public HttpComponentsClientHttpRequestFactory pooledRequestFactory() {
        ConnectionConfig connection = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(1))
            .setSocketTimeout(Timeout.ofSeconds(2))
            .setTimeToLive(TimeValue.ofMinutes(3))
            .setValidateAfterInactivity(TimeValue.ofSeconds(5))
            .build();

        PoolingHttpClientConnectionManager manager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(5)
                .setDefaultConnectionConfig(connection)
                .build();

        RequestConfig perRequest = RequestConfig.custom()
            .setConnectionRequestTimeout(
                Timeout.ofMilliseconds(300))
            .setResponseTimeout(Timeout.ofMilliseconds(700))
            .build();

        CloseableHttpClient apache = HttpClients.custom()
            .setConnectionManager(manager)
            .setDefaultRequestConfig(perRequest)
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .disableAutomaticRetries()
            .build();

        return new HttpComponentsClientHttpRequestFactory(apache);
    }

    @Bean
    public RestClient upstreamRestClient(
            RestClient.Builder builder,
            HttpComponentsClientHttpRequestFactory pooledRequestFactory,
            @Value("${demo.upstream-base-url}") String baseUrl) {
        return builder
            .requestFactory(pooledRequestFactory)
            .baseUrl(baseUrl)
            .build();
    }
}
```

Các điểm cốt lõi:

- `setMaxConnTotal(20)` và `setMaxConnPerRoute(5)` giới hạn connections; đây là **lab numbers**, không phải production defaults.
- `ConnectionConfig.setConnectTimeout` khác `RequestConfig.setConnectionRequestTimeout`.
- `RequestConfig.setResponseTimeout` có trong Apache 5 và dùng được với Spring Framework 6.1.
- `setSocketTimeout` áp low-level socket I/O; không coi response timeout là total deadline.
- `evictIdleConnections`/`evictExpiredConnections` cần client lifecycle đúng.
- Factory tạo và **sở hữu** `CloseableHttpClient`; `destroy()` đóng underlying client. Không đăng ký thêm một Apache client Spring Bean rồi close trùng.
- `disableAutomaticRetries()` giúp có **một** retry policy rõ ràng ở app layer nếu sau này cần.
- Không copy các ngưỡng 300/700ms sang production nếu chưa đo traffic/demand và upstream p99.

### 10.5. UpstreamGateway.java

```java
package example;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class UpstreamGateway {
    private final RestClient client;

    public UpstreamGateway(RestClient upstreamRestClient) {
        this.client = upstreamRestClient;
    }

    public Message fast() {
        return client.get()
            .uri("/fast")
            .retrieve()
            .body(Message.class);
    }

    public Message slow(long ms) {
        return client.get()
            .uri(builder -> builder.path("/slow")
                .queryParam("ms", ms).build())
            .retrieve()
            .body(Message.class);
    }

    public Message echo(String requestId) {
        return client.get()
            .uri("/echo")
            .header("X-Request-Id", requestId)
            .retrieve()
            .body(Message.class);
    }

    public void missing() {
        client.get().uri("/missing")
            .retrieve().toBodilessEntity();
    }

    public void unavailable() {
        client.get().uri("/unavailable")
            .retrieve().toBodilessEntity();
    }
}
```

Các method trên đều **synchronous**. `retrieve().toBodilessEntity()` vẫn kiểm tra HTTP status; 404/503 ném exceptions mặc định. Không bắt mọi lỗi thành null vì điều đó sẽ trộn transport error với empty business result.

### 10.6. GatewayController.java

```java
package example;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@RestController
@RequestMapping("/demo")
public class GatewayController {
    private final UpstreamGateway gateway;

    public GatewayController(UpstreamGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/fast")
    public Message fast() {
        return gateway.fast();
    }

    @GetMapping("/slow")
    public Message slow(@RequestParam(defaultValue = "50") long ms) {
        if (ms < 0 || ms > 5000) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "ms out of range");
        }
        return gateway.slow(ms);
    }

    @GetMapping("/safe-status")
    public ResponseEntity<String> safeStatus() {
        try {
            gateway.unavailable();
            return ResponseEntity.ok("upstream-ok");
        } catch (HttpServerErrorException.ServiceUnavailable ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("upstream-unavailable");
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body("upstream-IO-failed");
        }
    }
}
```

Demo mapping upstream 503 thành inbound 502 là một lựa chọn API policy, không phải tất cả 503 bắt buộc thành 502. Mapping ResourceAccessException thành 504 cũng chỉ là đơn giản hóa; connect refused có thể là 502/503. Trong production nên phân biệt root causes và schema lỗi thống nhất, không trả exception details có host/secret.

### 10.7. MockUpstreamServer.java

```java
package example;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class MockUpstreamServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;

    private MockUpstreamServer(
            HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static MockUpstreamServer start(int port)
            throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", port), 0);
        ExecutorService workers = Executors.newCachedThreadPool();
        server.setExecutor(workers);

        server.createContext("/fast", exchange ->
            respond(exchange, 200, "{\"value\":\"fast\"}"));

        server.createContext("/slow", exchange -> {
            long ms = parseSleep(exchange.getRequestURI()
                .getRawQuery());
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
                return;
            }
            respond(exchange, 200, "{\"value\":\"slow\"}");
        });

        server.createContext("/missing", exchange ->
            respond(exchange, 404, "{\"error\":\"missing\"}"));

        server.createContext("/unavailable", exchange ->
            respond(exchange, 503,
                "{\"error\":\"unavailable\"}"));

        server.createContext("/echo", exchange -> {
            String candidate = exchange.getRequestHeaders()
                .getFirst("X-Request-Id");
            String safe = candidate != null &&
                candidate.matches("[A-Za-z0-9._-]{1,64}")
                    ? candidate : "invalid";
            respond(exchange, 200,
                "{\"value\":\"" + safe + "\"}");
        });

        server.start();
        return new MockUpstreamServer(server, workers);
    }

    private static long parseSleep(String query) {
        if (query == null) return 0L;
        for (String part : query.split("&")) {
            if (part.startsWith("ms=")) {
                try {
                    long ms = Long.parseLong(part.substring(3));
                    return Math.max(0, Math.min(ms, 5000));
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private static void respond(
            HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().add(
                "Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        try (MockUpstreamServer mock = start(9090)) {
            System.out.println(
                "Mock upstream: http://127.0.0.1:9090");
            new CountDownLatch(1).await();
        }
    }
}
```

Mock sử dụng class Java17 `com.sun.net.httpserver.HttpServer` từ JDK module `jdk.httpserver`. `Thread.sleep` ở mock cố tình tạo response chậm, **không phải** pattern tốt cho app sản xuất. Nhờ `start(0)`, tests lấy ephemeral port nên tránh conflict giữa CI workers.

### 10.8. HttpClientDemoApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpClientDemoApplication {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 &&
                "--mock-upstream".equals(args[0])) {
            MockUpstreamServer.main(new String[0]);
            return;
        }
        SpringApplication.run(HttpClientDemoApplication.class, args);
    }
}
```

Mode mock **không khởi động Spring application**, chỉ phục vụ upstream API trên 9090. Mode thường khởi động MVC app trên 8080. Đây là hai processes độc lập, tránh HTTP self-call làm khó quan sát thread contention.

### 10.9. application.properties

```properties
server.port=8080
demo.upstream-base-url=http://127.0.0.1:9090
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

Production phải thay loopback HTTP bằng HTTPS host đã được allowlist, trust config và credential policy. Đừng cung cấp endpoint proxy nhận một arbitrary URL từ client rồi cho RestClient truy cập được internal metadata endpoints (SSRF).

### 10.10. Build và chạy

Với JDK 17+, Maven, từ thư mục có `pom.xml`:

```bash
mvn clean package

# Terminal 1 - mock server
java -jar target/http-client-demo-1.0.0.jar --mock-upstream

# Terminal 2 - Spring MVC web server
java -jar target/http-client-demo-1.0.0.jar
```

Sau đó chạy:

```bash
curl -i http://localhost:8080/demo/fast
curl -i "http://localhost:8080/demo/slow?ms=50"
curl -i http://localhost:8080/demo/safe-status
curl -i "http://localhost:8080/demo/slow?ms=-1"
```

Kỳ vọng:

| Request | HTTP kết quả |
| --- | --- |
| `/demo/fast` | 200, JSON `{"value":"fast"}` |
| `/demo/slow?ms=50` | 200, JSON `{"value":"slow"}` |
| `/demo/safe-status` | 502, text `upstream-unavailable` |
| `/demo/slow?ms=-1` | 400, lỗi query invalid |
| `/demo/slow?ms=1800` | Có thể ném ResourceAccessException do response timeout; inbound default error handling tùy Boot |

Không assert raw stacktrace/error JSON chưa được cấu hình. Các timeouts nêu trên là lab values và chạy trên OS/CI tải cao có thể thay đổi thời gian thực tế.

## 11. Tests JUnit 5: HTTP status, timeout và reuse client

File `src/test/java/example/UpstreamGatewayTest.java`:

```java
package example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class UpstreamGatewayTest {
    private MockUpstreamServer mock;
    private HttpComponentsClientHttpRequestFactory factory;
    private UpstreamGateway gateway;

    @BeforeEach
    void startMock() throws Exception {
        mock = MockUpstreamServer.start(0);
        factory = new ClientConfiguration().pooledRequestFactory();
        RestClient client = RestClient.builder()
            .baseUrl("http://127.0.0.1:" + mock.port())
            .requestFactory(factory)
            .build();
        gateway = new UpstreamGateway(client);
    }

    @AfterEach
    void closeAll() throws Exception {
        if (factory != null) factory.destroy();
        if (mock != null) mock.close();
    }

    @Test
    void fastJsonIsDecoded() {
        Message result = gateway.fast();
        assertNotNull(result);
        assertEquals("fast", result.value());
    }

    @Test
    void sameClientCanBeUsedRepeatedly() {
        for (int i = 0; i < 5; i++) {
            assertEquals("fast", gateway.fast().value());
        }
    }

    @Test
    void shortDelayStillSucceeds() {
        assertEquals("slow", gateway.slow(10).value());
    }

    @Test
    void slowResponseTriggersTransportTimeout() {
        assertThrows(ResourceAccessException.class,
            () -> gateway.slow(1800));
    }

    @Test
    void http404IsNotAnIoTimeout() {
        assertThrows(HttpClientErrorException.NotFound.class,
            gateway::missing);
    }

    @Test
    void http503IsAStatusError() {
        assertThrows(
            HttpServerErrorException.ServiceUnavailable.class,
            gateway::unavailable);
    }

    @Test
    void safeHeaderReachesUpstream() {
        assertEquals("req-001",
            gateway.echo("req-001").value());
    }

    @Test
    void mockRejectsUnexpectedHeaderValue() {
        assertEquals("invalid",
            gateway.echo("unsafe whitespace").value());
    }
}
```

**8 JUnit methods** kiểm tra JSON, reuse cùng client object, slow response timeout, status 404/503 và header forwarding. Tests dùng ephemeral port, không cần Internet. Chúng **không chứng minh** một TCP connection vật lý đã được reuse mỗi lần; chứng minh client/factory object được reuse và request processing hoạt động qua nhiều calls. Muốn test socket reuse thật phải quan sát remote socket/session/connection stats.

### 11.1. Kiểm thử pool acquisition cần test khác

Với pool 20/5 ở demo, hai HTTP calls không làm pool lease bão hòa. Test đúng cần pool max=1, giữ một slow response đang lease socket, để call thứ hai chờ lâu hơn lease timeout và assert pending/acquire error. Nếu chỉ dùng `gateway.slow(1800)`, bạn đang kiểm tra **response timeout**, không phải acquisition timeout.

### 11.2. Chưa được test TLS

Mock server là `http://127.0.0.1`, không dùng HTTPS. Vì vậy 8 tests **không** validate PKIX, hostname, mTLS hoặc ALPN. TLS cần môi trường HTTPS test với local CA/cert hợp lệ, hostname SAN khớp, client truststore/keystore được quản lý; cũng cần negative tests expired/untrusted/wrong-host cert. Không tự tuyên bố “TLS PASS” chỉ vì HTTP tests chạy.

### 11.3. Mã đã được soạn nhưng không khẳng định đã chạy

Các lệnh `mvn test` và `mvn clean package` ở đây dành cho người học. Chỉ công bố Maven tests PASS khi có output Maven thực tế. Nếu test timeout flaky dưới CI cực tải, điều chỉnh mock timing trong test setup/test-specific request policy thay vì bỏ timeout validation.

## 12. Connection pool sizing và tải hệ thống

Một quan hệ định lượng thường dùng là Little's Law:

`in_flight ≈ requests_per_second × average_latency_seconds`

Ví dụ upstream nhận 100 calls/s, trung bình 0,2s → khoảng **20 in-flight calls** trong steady state. Nhưng p99 spikes, retries và bursts có thể làm concurrency peak cao hơn. Không lấy 20 làm maxConn mặc định nếu backend chỉ cho phép 8 concurrent requests; sizing phải tôn trọng quotas.

### 12.1. Quá ít connections

Pool lease pending tăng, caller thread chờ, dù upstream khỏe. Cần kiểm tra active/leased/pending metrics, backend response latency và queue. Có thể cải thiện bằng giảm traffic, cache, giới hạn concurrency hoặc tăng pool **sau khi** downstream chấp nhận được.

### 12.2. Quá nhiều connections

Số instance × maxConnPerRoute tạo cluster-wide possible connections. Ví dụ 20 JVMs × 100 per route = 2000 potential sockets tới cùng host. Cần quan tâm FD limits, SNAT ephemeral ports, TLS sessions, proxy/LB connection quotas, remote CPU và rate limits.

### 12.3. Idle, TTL và stale

- **Idle:** thời gian connection không được sử dụng.
- **TTL:** tuổi tối đa connection.
- **Server keep-alive timeout:** server có thể đóng pooled connection trong lúc idle.
- **Validate after inactivity:** giảm dùng lại socket đã stale.
- **Evict idle/expired:** dọn sockets nhưng có thể tăng connect/TLS khi cần lại.

Chọn idle eviction phù hợp proxy/server behavior, quan sát stale retry rates. Không đặt tất cả thành 0 hoặc cực thấp chỉ để “an toàn”.

### 12.4. Pool metric và thread dump

Nếu worker threads đều blocked ở `leaseConnection`, đó khác vấn đề block ở socket read. Thread dump kết hợp Apache connection manager pool stats, Spring HTTP client metrics và downstream server p99 giúp phân biệt. Một leased connection không tự là leak: nếu server stream dài hoặc client tải dữ liệu lớn thì trạng thái leased có thể đúng.

## 13. Error handling và bảo mật trong hệ thống production

### 13.1. Network failure vs HTTP response

- HTTP 404/429/503: **đã có HTTP status** từ server/gateway.
- `ResourceAccessException`: I/O layer có thể chưa nhận HTTP response; root cause có thể là timeout, refused, DNS, SSL hoặc pool acquisition.
- JSON parse/converter error: upstream body không phù hợp DTO, dù status 200.
- Client cancellation/interruption: không đảm bảo remote operation bị rollback.

Tầng gateway/service nên phân loại errors thành business not-found, validation, transient, policy violation và unexpected; không chuyển mọi exception thành 404 hoặc 504.

### 13.2. Retry budget

Đặt max attempts (ví dụ 2), jitter, exponential backoff, deadline và circuit breaker. Nếu server trả `Retry-After` cần xử lý theo contract. Tránh retry multiplication giữa Apache HttpClient, framework resilience library, reverse proxy và message broker consumer. Với POST/PUT/PATCH, xác định idempotency theo nghiệp vụ/HTTP semantics; không retry mù quáng.

### 13.3. SSRF và redirect

Không tạo request từ URL tùy ý do client gửi; kẻ tấn công có thể yêu cầu gọi localhost, metadata endpoints hoặc private networks. Dùng cố định base URL, validate scheme/host/port, kiểm soát DNS resolution, redirect hosts và outbound egress policies. Tắt follow redirects khi cần hạn chế destinations; nếu theo redirect cần loại bỏ sensitive headers khi đổi origin.

### 13.4. Không lộ secrets

Không log `Authorization`, client keystore password, private keys, toàn response body PII, hay SSL debug traces chứa dữ liệu nhạy cảm. Với custom truststore/keystore, lưu passwords bằng secret management, phân quyền filesystem và rotation. Không commit `.p12` chứa private key hoặc đặt password thật vào sample.

### 13.5. HTTP client đồng bộ trong reactive handler

Đừng gọi RestClient blocking trực tiếp trong Netty event-loop của WebFlux. Dùng WebClient + reactive driver nếu cần reactive end-to-end hoặc cô lập blocking call qua bounded worker pool có queue/timeout limits rõ; `Mono.just(restClient.get()...)` vẫn chạy blocking **trước** Mono assembly.

## 14. Troubleshooting theo triệu chứng

| Triệu chứng | Chẩn đoán ưu tiên |
| --- | --- |
| 700ms timeout nhưng call kéo dài hơn 1s | Lease/DNS/connect/TLS/response/body là các giai đoạn khác nhau |
| 100 inbound requests, chỉ 5 outbound chạy | Max per-route = 5, pending acquire |
| Timeout dù upstream không nhận request | Lease/connection acquisition timeout |
| `Connection refused` | Listener/firewall/host:port, không phải HTTP 404 |
| `UnknownHostException` | DNS/resolver/network |
| `PKIX path building failed` | CA/cert chain/truststore |
| Hostname mismatch | URL host không khớp certificate SAN |
| Local HTTPS tốt, production fail | Proxy, TLS version, truststore, cert rotation |
| POST timeout nhưng có hai records | Automatic retry/duplicate write |
| Pool leased count không giảm | Slow response, unconsumed body hoặc resource leak |
| JVM không exit khi app shutdown | Pool/client/evictor/executor chưa đóng |
| JDK 17 compile error với HttpClient.close | Method này không có trên Java 17 |
| Spring 6.1 compile error Apache setReadTimeout | Method này chỉ được thêm ở 6.2 |
| Browser nhận 504 nhưng remote có 200 | Gateway timeout xảy ra trước response đến |
| CPU thấp nhưng p99 cao | Blocking waits/pool queue/downstream latency |
| Too many open files | Sockets/pools/FDS chưa giới hạn |
| TLS trust-all khiến kết nối “hoạt động” | Security verification đã bị vô hiệu hóa |

## 15. Checklist production

1. [ ] Biết HTTP engine/factory thực, không dựa vào defaults ngẫu nhiên.
2. [ ] Chỉ rõ maxConnTotal, maxConnPerRoute theo downstream capacity.
3. [ ] Có pool acquire timeout, connect timeout, response timeout và whole-call deadline.
4. [ ] DNS, proxy CONNECT, TLS handshake được tính riêng trong SLA.
5. [ ] HTTP client/factory/pool là shared beans được khởi tạo một lần.
6. [ ] Connection có TTL/idle eviction/stale validation hợp lý.
7. [ ] Response body/low-level InputStream luôn consume/close đúng.
8. [ ] Close/dispose pool/evictor đúng lúc app shutdown.
9. [ ] Có bulkhead/rate limit cho số synchronous calls đồng thời.
10. [ ] Có chính sách retry rõ, deadline và idempotency key cho writes.
11. [ ] HTTP status errors không bị nhầm với network transport exceptions.
12. [ ] TLS CA trust, SAN hostname verification và mTLS (nếu yêu cầu) được kiểm thử.
13. [ ] Không dùng trust-all, NoopHostnameVerifier, disableHostnameVerification.
14. [ ] Secrets/truststores/keystores được lưu và rotate an toàn.
15. [ ] Base URL/redirect/egress hạn chế SSRF.
16. [ ] Pool leased/pending/available và p95/p99 latency có metric.
17. [ ] Cluster-wide connection budget được tính theo số instances.
18. [ ] Test 404/429/503, timeout, pool exhaustion, untrusted/wrong-host cert.
19. [ ] Test graceful shutdown và active outbound calls.
20. [ ] Khi upgrade Java/Spring/Apache, kiểm tra API và default semantics.
21. [ ] Trong WebFlux event-loop, không chạy RestClient blocking trực tiếp.
22. [ ] Benchmark gồm cold/warm connections và TLS/proxy thực tế.

## 16. Câu hỏi ôn tập và đáp án

**1.** TCP connect timeout có đảm bảo response nhanh không?  
**Đáp án:** Không; response/read timeout là giai đoạn khác.

**2.** Lease timeout là gì?  
**Đáp án:** Chờ connection từ pool, có thể fail trước khi HTTP request ra mạng.

**3.** Pool total khác per-route?  
**Đáp án:** Total giới hạn mọi connection; per-route giới hạn mỗi target route.

**4.** Vì sao RestClient thường nên được reuse?  
**Đáp án:** Reuse request factory/pool, quản lý TLS/timeouts/lifecycle nhất quán.

**5.** RestTemplate có dùng pool Apache được không?  
**Đáp án:** Có qua HttpComponentsClientHttpRequestFactory.

**6.** Spring 6.1 HttpComponents factory có setReadTimeout(Duration) chưa?  
**Đáp án:** Chưa; API được thêm từ Spring 6.2.

**7.** Spring 6.1 JdkClientHttpRequestFactory có setReadTimeout(Duration) không?  
**Đáp án:** Có.

**8.** Java 17 HttpClient có close() như Java 21 không?  
**Đáp án:** Không, Java 21 bổ sung public lifecycle APIs.

**9.** Connection idle và TTL khác gì?  
**Đáp án:** Idle đo lúc không dùng; TTL đo tuổi tối đa từ khi tạo.

**10.** HTTP keep-alive giảm chi phí gì?  
**Đáp án:** Giảm tạo TCP/TLS connection mới qua reuse.

**11.** Một response stream chưa đóng ảnh hưởng pool ra sao?  
**Đáp án:** Connection có thể còn leased/không reusable.

**12.** 204 có đảm bảo body DTO không null không?  
**Đáp án:** Không; 204 không có body.

**13.** HTTP 404 khác TCP connection refused?  
**Đáp án:** 404 là HTTP response, refused là TCP failure.

**14.** Timeout sau POST có chứng minh server không lưu record?  
**Đáp án:** Không, server có thể đã commit.

**15.** Vì sao cần idempotency key cho retry POST?  
**Đáp án:** Tránh tạo resource trùng khi response của lần trước bị mất.

**16.** PKIX path building failed nên sửa thế nào?  
**Đáp án:** Kiểm tra CA/intermediates/truststore, không trust-all.

**17.** Hostname verification làm gì?  
**Đáp án:** Kiểm tra SAN certificate có khớp URL host.

**18.** Truststore và client keystore có giống nhau không?  
**Đáp án:** Không; truststore để xác thực server, keystore private key để client auth/mTLS.

**19.** TLS mTLS tự thay authorization của business không?  
**Đáp án:** Không; vẫn cần mapping identity/permissions.

**20.** Tăng pool vô hạn có tốt không?  
**Đáp án:** Không; có thể làm upstream quá tải, cạn sockets/FDS.

**21.** Response timeout 700ms là total-call deadline 700ms chứ?  
**Đáp án:** Không, còn các stages khác và body semantics.

**22.** Timeout khi chờ pool có thể xảy ra dù upstream khỏe?  
**Đáp án:** Có, khi pool tất cả connections đang leased.

**23.** Vì sao Java 17 JDK HttpClient khó kiểm soát pool hơn Apache 5?  
**Đáp án:** Không có cùng public per-route pool limit/metrics/close APIs.

**24.** Khi Spring Boot shutdown, chỉ cần server.shutdown=graceful là Apache client đã đóng đúng?  
**Đáp án:** Không; cần bean lifecycle/destroy của outbound pool.

**25.** Nếu REST client gọi trong WebFlux event-loop sẽ sao?  
**Đáp án:** Có thể chặn event-loop, làm giảm concurrency; dùng WebClient hoặc bounded bridge.

**26.** Test gateway.slow(1800) với 700ms response timeout chứng minh pool lease limit không?  
**Đáp án:** Không; test đó chỉ chứng minh response timeout.

**27.** TLS handshake có thể ảnh hưởng p99 dù request body rất nhỏ không?  
**Đáp án:** Có, đặc biệt cold connections/certificate negotiation.

**28.** Client theo redirect sang host khác cần chú ý gì?  
**Đáp án:** SSRF/Authorization header leak và trust boundary.

**29.** Apache HttpClient automatic retries có nên bật mọi khi không?  
**Đáp án:** Không; phải xét idempotency, deadline và các retry layers khác.

**30.** Đo gì đầu tiên khi leased=max và pending tăng?  
**Đáp án:** Pool stats, upstream latency, body closes và caller concurrency.

## 17. Nguồn tham khảo

- [Spring Framework 6.1 — Rest Clients](https://docs.spring.io/spring-framework/reference/6.1/integration/rest-clients.html): RestClient/RestTemplate và transport adapters.
- [Spring 6.1 JdkClientHttpRequestFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/http/client/JdkClientHttpRequestFactory.html): read timeout và JDK bridge.
- [Spring 6.1 HttpComponentsClientHttpRequestFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html): connect/lease timeout và lifecycle.
- [Spring 6.2 HttpComponentsClientHttpRequestFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html): read timeout API được thêm trong 6.2.
- [Apache HttpClient 5.2 ConnectionConfig API](https://javadoc.io/static/org.apache.httpcomponents.client5/httpclient5/5.2.1/org/apache/hc/client5/http/config/ConnectionConfig.Builder.html): connect/socket timeout, TTL, idle validation.
- [Apache HttpClient 5 Pooling Manager API](https://hc.apache.org/components/httpcomponents-client-5.4.x/5.4.4/httpclient5/apidocs/org/apache/hc/client5/http/impl/io/PoolingHttpClientConnectionManagerBuilder.html): total/per-route pool limits.
- [Java 17 HttpClient API](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html): send/sendAsync, connectTimeout, SSLContext.
- [Java 21 HttpClient API](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html): new public lifecycle methods.
- [Java 17 HttpExchange Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpExchange.html): local test server lifecycle.
- [Spring Boot 3.3 — Calling REST Services](https://docs.spring.io/spring-boot/3.3/reference/io/rest-client.html): injected RestClient.Builder.
- [Java Secure Socket Extension](https://docs.oracle.com/en/java/javase/17/security/java-secure-socket-extension-jsse-reference-guide.html): trust managers, key managers, TLS.
- [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): status, methods, idempotency.
- [RFC 8446 TLS 1.3](https://www.rfc-editor.org/rfc/rfc8446): TLS handshake.

**Kết luận:** HTTP client đồng bộ chỉ đáng tin khi **connect, lease, response và whole-call deadlines được phân biệt**, connection pool có limits/metrics và lifecycle rõ, TLS chain cùng hostname luôn được xác minh, response streams được đóng và retries có idempotency. Chọn request factory theo Spring/JDK/Apache **phiên bản thực**, không copy một phương thức chưa tồn tại hoặc mặc định rằng mọi HTTP client có pool giống nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-008">Interceptor, xử lý mã lỗi và thiết kế retry theo tính lặp lại của thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Interceptor, xử lý mã lỗi và thiết kế retry theo tính lặp lại của thao tác

## 1. Vì sao HTTP client đồng bộ cần ba cơ chế riêng?

Một ứng dụng Spring gọi HTTP tới dịch vụ khác thường cần thêm thông tin chung cho request, diễn giải phản hồi lỗi và thử lại khi có sự cố tạm thời. Ba vấn đề liên quan nhau nhưng **không phải một cơ chế duy nhất**:

1. **Interceptor**: can thiệp trước/sau một lời gọi HTTP ở client để thêm correlation ID, header phù hợp, quan sát thời gian phản hồi, trace hoặc policy dùng chung.
2. **Xử lý mã lỗi**: phân loại HTTP 4xx/5xx, lỗi transport, timeout, body không hợp lệ và chuyển chúng thành kết quả/exception có ý nghĩa nghiệp vụ.
3. **Retry theo tính lặp lại (idempotency)**: chỉ thực hiện lại yêu cầu khi việc lặp là an toàn theo **HTTP method, operation semantics, trạng thái request body, server contract, số lần thử và ngân sách thời gian**.

Điểm quan trọng nhất: **một kết nối timeout không chứng minh server chưa xử lý yêu cầu**. Client có thể đã gửi POST, server đã ghi DB, nhưng response bị mất. Nếu client tự POST lại, có thể tạo hai đơn hàng hoặc thanh toán hai lần.

Bài thuộc nhóm **HTTP client đồng bộ**, lấy **Spring Framework 6.1 / Spring Boot 3.3.5 / Java 17** làm nền tảng. API chính là `RestClient` (được giới thiệu trong Spring Framework 6.1), kèm đối chiếu với `RestTemplate` trong hệ thống hiện hữu. `RestClient` là synchronous/blocking client, **không phải WebClient reactive**.

### Bạn sẽ làm được gì?

- Phân biệt vị trí `ClientHttpRequestInterceptor` với status/error handler và retry wrapper.
- Viết interceptor chỉ thực thi request đúng một lần mỗi attempt, ghi logs an toàn và tránh giữ response chưa đóng.
- Biết default error behavior của `RestClient.retrieve()`, `onStatus(...)`, `defaultStatusHandler(...)`, `exchange(...)` và `RestTemplate.ResponseErrorHandler`.
- Phân loại đúng 400, 401, 403, 404, 409, 412, 422, 429, 500, 502, 503, 504 và lỗi network.
- Hiểu HTTP *safe*, *idempotent*, *non-idempotent* và sự khác nhau giữa "HTTP method idempotent" với "business operation có thể lặp lại".
- Lựa chọn những operation được phép retry, xử lý `Retry-After`, exponential backoff, jitter, max attempts, timeout và budget.
- Thiết kế `Idempotency-Key` phía client **và phía server**, không nhầm một header tùy ý với bảo đảm chống duplicate.
- Viết dự án Maven chạy với `RestClient`/`MockRestServiceServer`, kiểm tra chính xác số lần gọi qua 4xx/5xx/timeout.
- Biết các vấn đề production như circuit breaker, retry storms, distributed tracing, logging PII, pool starvation và concurrency.

## 2. Đường đi của một lời gọi RestClient

Một request HTTP đồng bộ có thể đi qua các lớp:

```text
Business service
   |
   v
Retry boundary (chỉ với operation đủ điều kiện)
   |
   +--> attempt 1
   |       |
   |       v
   |     RestClient request specification
   |       |
   |       v
   |     ClientHttpRequestInterceptor chain
   |       |
   |       v
   |     ClientHttpRequestFactory -> HTTP transport
   |       |
   |       v
   |     HTTP response
   |       |
   |       v
   |     retrieve() status handler -> body conversion
   |       |
   |       +--> success value
   |       +--> typed HTTP exception
   |       +--> transport exception
   |
   +--> (nếu retryable và còn budget)
   |       attempt 2 -> toàn bộ RestClient execution mới
   |
   v
Final success / terminal failure
```

**Retry nên bao quanh toàn bộ một attempt** thay vì gọi `execution.execute(...)` tùy tiện nhiều lần bên trong interceptor. Cấu trúc này cho phép status handler của `retrieve()` nhìn từng response, và caller quyết định có được lặp thao tác hay không.

Một interceptor có thể quan sát cả HTTP 404 và HTTP 503 mà **không tự biến chúng thành exception**. Status handler hoạt động sau giai đoạn transport/interception của `RestClient.retrieve()`. Các lỗi network trước khi nhận response cần được phân biệt với lỗi HTTP đã có status.

### Các abstraction liên quan

| Loại | Package/đối tượng | Trách nhiệm |
| --- | --- | --- |
| Fluent client | `org.springframework.web.client.RestClient` | Tạo request, gửi đồng bộ, nhận response |
| Legacy synchronous client | `org.springframework.web.client.RestTemplate` | API template, vẫn gặp trong nhiều ứng dụng |
| Interceptor | `org.springframework.http.client.ClientHttpRequestInterceptor` | Can thiệp request/response trước/sau HTTP execution |
| Request factory | `ClientHttpRequestFactory` | Kết nối, timeout, transport implementation |
| RestClient status handler | `retrieve().onStatus(...)` | Ánh xạ lỗi theo từng request |
| Global status handler | `RestClient.Builder.defaultStatusHandler(...)` | Rule mặc định cho nhiều requests |
| RestTemplate handler | `ResponseErrorHandler` | Xác định error và xử lý response lỗi |
| HTTP response exception | `RestClientResponseException` | Status, response headers và error body |
| I/O exception | `ResourceAccessException` | Transport timeout, connection, TLS và các I/O issues |
| Client mock | `MockRestServiceServer` | Test requests, headers, responses, số lần retry |

## 3. RestClient và RestTemplate khác nhau ở đâu?

### RestClient: fluent API của Spring Framework 6.1

```java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test")
        .build();

CatalogItem item = client.get()
        .uri("/items/{id}", 42)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(CatalogItem.class);
```

Đây là snippet; `CatalogItem` là DTO ở ví dụ hoàn chỉnh. Lệnh gọi `body(...)` thực hiện request đồng bộ, đọc và convert response. Trong tình huống bình thường, nếu upstream trả HTTP 4xx/5xx thì `retrieve()` **mặc định tạo RestClientException**, thường là các subclasses của `RestClientResponseException`.

### RestTemplate trong ứng dụng cũ

```java
RestTemplate template = new RestTemplate();

ResponseEntity<CatalogItem> response = template.exchange(
        "https://catalog.example.test/items/{id}",
        HttpMethod.GET,
        HttpEntity.EMPTY,
        CatalogItem.class,
        42);
```

`RestTemplate` vẫn hữu ích với code đã có. Nó sử dụng `ResponseErrorHandler` (mặc định có `DefaultResponseErrorHandler`) và hỗ trợ cùng `ClientHttpRequestInterceptor`. Spring có `RestClient.builder(restTemplate)` để lấy cấu hình nền khi muốn migrate từng phần.

**Không cần rewrite toàn bộ RestTemplate chỉ để thêm interceptor/retry.** Quan trọng hơn là hợp đồng API, timeout, status mappings, test và dữ liệu giao dịch. Cũng không thay RestClient thành WebClient chỉ vì muốn có retry; WebClient sử dụng pipeline reactive khác và không tự sửa được non-idempotent POST.

## 4. ClientHttpRequestInterceptor là gì?

Chữ ký cốt lõi:

```java
@FunctionalInterface
public interface ClientHttpRequestInterceptor {
    ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution)
            throws IOException;
}
```

Đây là bản trình bày chữ ký của API Spring, không phải interface cần tự định nghĩa.

- `request` chứa HTTP method, URI và mutable headers.
- `body` là bytes của request body đã được chuẩn bị cho execution; cần thận trọng với memory và secrets.
- `execution.execute(request, body)` gọi interceptor tiếp theo hoặc request factory/transport.
- `ClientHttpResponse` cho phép xem status/headers và phản hồi từ server.
- Interceptor có thể short-circuit nhưng lúc đó phải trả response hợp lệ hoặc throw một exception có semantics rõ.

Một interceptor thông thường nên gọi **`execution.execute(...)` chính xác một lần trên đường thành công**. Tự chạy nhiều lần vì status 503 trong interceptor có thể vô tình phát lại POST, mất body replay guarantees và khiến error handling khó kiểm soát.

### Thứ tự nhiều interceptors

`RestClient.Builder.requestInterceptor(...)` thêm interceptor vào chain; `requestInterceptors(...)` cho quyền quản lý danh sách. Thứ tự quyết định:
- Header auth được gắn trước signing hay sau?
- Trace span bao gồm giai đoạn nào?
- Logs có nhìn thấy request headers sau khi đã chỉnh sửa không?
- Retry (nếu có thành phần ngoại vi) ở ngoài hay trong metrics/observability?

Đừng dựa vào class name hay thứ tự quét bean không được bảo đảm. Xác định thứ tự tường minh trong config và test headers ở HTTP mock.

### Không đọc/ghi logs toàn bộ request body

`byte[] body` có thể chứa PII, password, access token, payment data hoặc file upload. Logging nó là một rủi ro bảo mật. Nên log:
- Logical service name.
- HTTP method và endpoint template (không raw query parameters chứa secrets).
- HTTP status/code family.
- Duration, attempt count, server trace/request ID được phép.
- Error category (timeout/4xx/5xx), không dump authorization headers.

Không log `Authorization`, `Cookie`, `Set-Cookie` hay raw `ResponseEntity` có body nhạy cảm.

## 5. Interceptor không phải điểm nên retry mặc định

Phản ví dụ:

```java
// PHẢN VÍ DỤ — interceptor tự gọi upstream nhiều lần.
return execution.execute(request, body);
// ...nếu 503 thì execution.execute(request, body) một lần nữa...
```

Ngoài việc chỉ ra flow sai, ý tưởng trên còn có vấn đề:
1. Interceptor **không biết chắc** operation business có thể lặp.
2. HTTP body có thể là stream/file upload không replayable theo kiểu request factory hiện tại.
3. Mỗi interceptor có thể được tái sử dụng bởi GET, POST, PUT, DELETE và những requests đặc biệt.
4. Nếu response đã được tạo nhưng interceptor quyết định retry, nó phải **đóng response cũ** chính xác; không làm sẽ leak connection/pool resources.
5. Status/error handler của `RestClient.retrieve()` thường chưa chạy trong interceptor.
6. Auth refresh, idempotency key, tracing và metrics có thể bị trộn vào nhau.
7. Client-cancel/interrupted thread có thể bị bỏ qua khi ngủ backoff trong interceptor.

**Thiết kế ưu tiên:** để interceptor làm transport decoration/observability; để **retry boundary** ở service method hoặc resilience policy có metadata rõ cho từng operation; để `RestClient.retrieve()`/`onStatus` quyết định error mapping.

## 6. Error status: RestClient.retrieve() và RestClient.exchange()

### Default retrieve()

```java
CatalogItem item = client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .body(CatalogItem.class);
```

Mặc định, phản hồi thuộc **4xx và 5xx** được xử lý như error; code thường nhận subclasses của `RestClientResponseException`, có thông tin:
- `getStatusCode()` (Spring `HttpStatusCode`).
- `getResponseHeaders()`.
- `getResponseBodyAsString()` hoặc bytes.
- HTTP response metadata khác theo implementation.

Không đem nguyên error body từ dịch vụ nội bộ trả cho public client nếu nó có hostname, stack trace hay secrets.

### onStatus per request

```java
CatalogItem item = client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .onStatus(
                code -> code.value() == 404,
                (request, response) -> {
                    throw new CatalogNotFoundException(
                            "Catalog item unavailable");
                })
        .body(CatalogItem.class);
```

Đây là snippet, `CatalogNotFoundException` là custom exception do ứng dụng định nghĩa. Handler `onStatus` ở request này nhận quyền xử lý 404; ví dụ chủ động throw domain exception. Tránh swallow error mà tiếp tục decode 404 body như DTO thành công ngoài ý định.

Một số API có thể muốn 404 → `Optional.empty()` thay exception. Thiết kế contract rõ; có thể dùng `exchange(...)` để trực tiếp đọc status và trả Optional, nhưng **exchange không tự áp dụng default retrieve status handlers**.

### defaultStatusHandler toàn client

```java
RestClient restClient = RestClient.builder()
        .defaultStatusHandler(
                HttpStatusCode::is5xxServerError,
                (request, response) -> {
                    throw new IllegalStateException(
                            "Upstream server returned 5xx");
                })
        .build();
```

Đây là snippet minh họa, không phải lựa chọn production lý tưởng vì đã **làm mất status và Retry-After** của exception mặc định. Khi cần retry theo 502/503/504, giữ nguyên `RestClientResponseException` hoặc wrap thành custom exception chứa code/headers/retry hints.

Các `defaultStatusHandler` chạy theo thứ tự đăng ký, sau request-specific handlers. Đừng cài một catch-all status handler rồi vô tình biến 404/409 thành 500 hoặc biến 429 thành domain invalid input.

### exchange(...) bypass status handlers của retrieve()

```java
Boolean exists = client.head()
        .uri("/items/{id}", 42)
        .exchange((request, response) ->
                response.getStatusCode().is2xxSuccessful());
```

Đây là snippet; 404 được trả dưới dạng `false` chứ không tự ném exception theo default `retrieve()`, vì callback của `exchange` tự chịu trách nhiệm status handling. Chỉ dùng khi bạn chủ động kiểm tra các status/headers và bảo đảm phản hồi được xử lý/đóng phù hợp. Với service có retry policy dựa trên `RestClientResponseException`, thay retrieve bằng exchange sẽ **làm thay đổi điều kiện retry**.

### Status handler vs transport errors

Nếu DNS không phân giải, TLS handshake thất bại hoặc socket timeout **trước khi có HTTP response**, không có HTTP status 503 để `onStatus` xử lý. Những lỗi này thường đi qua `ResourceAccessException` hoặc lỗi client I/O khác. Cần phân loại riêng và giữ nguyên cause/trace nội bộ.

## 7. Bảng phân loại mã lỗi để thiết kế retry

| Mã | Ý nghĩa thường gặp | Retry tự động? |
| --- | --- | --- |
| 200/201/204 | Thành công | Không |
| 301/302/307/308 | Redirect | Không phải lỗi để retry đơn giản; kiểm tra redirect/credentials/method |
| 400 | Request sai cấu trúc/vi phạm validation | Không; sửa input |
| 401 | Chưa authenticated/token hết hạn | Không retry vô hạn; có thể refresh credentials tối đa một lần theo policy |
| 403 | Không có quyền | Không; sửa authz/ACL |
| 404 | Không thấy resource | Thường không; eventual consistency chỉ khi contract cho phép và bounded |
| 408 | Request Timeout | Có thể retry **nếu operation an toàn** và context phù hợp |
| 409 | Conflict | Không mặc định; kiểm tra version/concurrency |
| 412 | Precondition Failed | Không; làm mới ETag/state |
| 422 | Unprocessable Content | Không; sửa dữ liệu |
| 425 | Too Early | Có trường hợp client retry khi không dùng early data; cần TLS/protocol policy |
| 429 | Too Many Requests | Có thể theo Retry-After/quota, chỉ nếu operation replay-safe |
| 500 | Internal Server Error | Không mặc định; phụ thuộc API/incident semantics |
| 502 | Bad Gateway | Có thể là transient; retry có kiểm soát |
| 503 | Service Unavailable | Thường transient; cân nhắc Retry-After |
| 504 | Gateway Timeout | Có thể transient, **server có thể đã thực hiện side effect** |
| Transport timeout/reset | Không có response tin cậy | Chỉ retry nếu business operation replay-safe |
| TLS certificate error | Cấu hình bảo mật sai | Không retry trong vòng lặp; cần sửa cấu hình |

Bảng là **điểm khởi đầu**, không phải luật bất biến. Một `GET /export/start` làm phát sinh job/tính phí có thể vi phạm yêu cầu safe dù dùng GET; một `POST /payments` có idempotency key hợp lệ có thể được phép retry khi phía server đã có dedup atomic.

**Đừng viết `if (status >= 500) retry` cho mọi method.** Mã 500 có thể là bug nghiệp vụ/cấu hình không cải thiện sau nhiều attempts; hơn nữa 504 không chứng minh operation chưa chạy.

## 8. Safe, idempotent và replayable khác nhau

Theo RFC 9110:

- **Safe methods**: về semantics, client không yêu cầu làm thay đổi trạng thái server. GET, HEAD, OPTIONS, TRACE được định nghĩa là safe, dù server vẫn có thể log/đếm thống kê.
- **Idempotent methods**: nhiều requests giống nhau có intended effect trên server tương đương một request. Các safe methods cùng với **PUT** và **DELETE** là idempotent theo định nghĩa HTTP semantics.
- **Không mặc định idempotent**: POST và PATCH (trừ khi server thiết kế semantics/idempotency riêng).

### Ví dụ GET

`GET /catalog/items/42` chỉ đọc một item. Khi socket reset sau khi gửi request, gọi lại thường không tạo dữ liệu mới. Tuy vậy:
- API server phải tuân thủ semantics.
- Request có thể chứa one-time token hoặc counter không đáng lặp.
- Retry vẫn tiêu tốn quota và có thể đẩy server đang yếu vào overload.

### Ví dụ PUT

`PUT /users/42` với resource representation và cùng ETag/precondition có thể được coi idempotent theo intended effect. Nhưng:
- Nếu yêu cầu dùng `If-Match`, retry sau khi lần đầu thành công có thể trả **412** vì version đã thay đổi. Điều đó không có nghĩa trạng thái cuối sai; cần logic nghiệp vụ/ETag để diễn giải.
- Upload body phải replayable.
- Một server implement "PUT tăng số lần truy cập có tính phí" không tuân thủ semantics mong muốn; không tin method name hơn hợp đồng thật.

### Ví dụ DELETE

`DELETE /resources/42` hai lần thường có desired effect "resource không còn tồn tại". Lần 2 có thể trả 404 trong khi lần 1 thành công: **idempotency không có nghĩa responses giống hệt nhau**. Nếu backend phát sinh side effect phi idempotent khi DELETE, phải sửa contract hoặc tắt retry.

### Ví dụ POST thanh toán

`POST /payments` tạo charge. Nếu client timeout ngay sau gửi, request **có thể đã được ghi**; retry có thể charge hai lần. Không retry tự động chỉ vì mã 503/timeout. Có thể dùng server-supported Idempotency-Key và lưu kết quả dedup, nhưng yêu cầu bảo đảm hệ thống chứ không chỉ đặt header.

### Ba điều kiện nên có trước retry

1. **Semantically safe/idempotent hoặc được server bảo vệ bằng dedup**.
2. **Request replayable** (body không phải one-shot stream và credentials/headers còn hợp lệ).
3. **Retry budget** với max attempts, deadline, backoff và phân loại lỗi thật sự transient.

Nếu thiếu một điều kiện, **không tự retry**.

## 9. Tại sao "đã gửi nhưng chưa nhận response" nguy hiểm?

Hình dung:

```text
Client                Network                 Payment service
  | POST /payments       |                           |
  |--------------------->|-------------------------->|
  |                      |                        Ghi charge
  |                      |<--------------------------|
  |    timeout / reset   X                           |
  |                      |                           |
  | Client không biết operation đã commit hay chưa    |
  |                      |                           |
  | POST /payments lại   |                           |
  |--------------------->|-------------------------->|
  |                      |                       Charge lần 2?
```

**At-least-once attempts** không đồng nghĩa **exactly-once business effects**. Muốn chống duplicate:
- Client tạo một idempotency key ổn định cho **một logical business operation**.
- Server lưu key, request fingerprint, trạng thái đang xử lý/kết quả và trả lại kết quả đã có theo policy.
- Key phải atomic với transaction tạo side effect (hoặc có chiến lược consistency đủ mạnh).
- Cần TTL, concurrency conflict rules và tenant/user scoping.
- Client gửi lại **cùng key** khi retry **cùng operation**, không tạo key mới mỗi attempt.
- Nếu cùng key nhưng payload khác, server nên báo conflict theo contract.
- Không dựa chỉ vào reverse proxy hay Redis cache đơn giản nếu side effect commit mà dedup record thất bại.

### Key không tự mang phép màu

`Idempotency-Key: abc123` chỉ là header. Nếu server không triển khai lưu và kiểm tra, gửi lại POST vẫn có thể tạo charge thứ hai. Nếu server chỉ ghi key sau khi DB transaction commit nhưng mất điện trước khi ghi key, còn có race. Idempotency cần thiết kế xuyên storage/transaction/broker.

Không reuse một key cho các operation khác nhau. Không gửi key nhạy cảm trong URL. Giới hạn TTL, entropy và scope; tránh cho client đoán key của user khác rồi lấy cached response.

## 10. Retry-After: server yêu cầu đợi bao lâu?

`Retry-After` có hai dạng HTTP phổ biến:

1. **delta-seconds**: `Retry-After: 5` — đợi ít nhất 5 giây.
2. **HTTP-date**: `Retry-After: Wed, 21 Oct 2026 15:00:00 GMT` — thời điểm có thể thử lại.

Dạng HTTP-date chịu ảnh hưởng clock skew giữa client/server; parser cần dùng RFC 1123/HTTP-date hợp lệ và đưa giá trị âm về 0 khi thời điểm đã qua.

Một server có thể gửi 429 hoặc 503 kèm Retry-After. Nếu giá trị yêu cầu **lâu hơn budget mà client có**, lựa chọn hợp lý là **không retry trong call đó**, chuyển failure cho caller/circuit breaker hoặc lập lịch công việc bên ngoài; **không** bí mật rút ngắn Retry-After rồi spam server.

Retry-After không phải lệnh bắt buộc rằng mọi request sẽ thành công sau khi chờ. Các policies về auth, idempotency, deadline và server quota vẫn quyết định cuối cùng.

### Khi Retry-After thiếu

Có thể dùng **exponential backoff + jitter**. Với base 100 ms và max 2 s, các attempts có thể được đợi theo những khoảng tăng dần nhưng ngẫu nhiên (full jitter) để tránh hàng nghìn clients đồng loạt đánh lại đúng một nhịp.

Đừng dùng `Thread.sleep(100)` trong vòng lặp vô hạn. Một request đồng bộ giữ một thread trong thời gian sleep; ở server chịu tải, chiến lược này phải có giới hạn thấp hoặc chuyển sang queue/async processing.

## 11. Retry budget và nguy cơ retry storm

Một service call có thể có nhiều tầng retries:

```text
Browser retry 2 lần
   |
   v
API gateway retry 3 lần
   |
   v
Application retry 3 lần
   |
   v
Load balancer retry 2 lần
   |
   v
Downstream dependency
```

Trong worst case, một logical action có thể tạo **2 × 3 × 3 × 2 = 36 attempts** ở downstream. Nếu downstream đang overload, retries có thể làm sự cố nặng hơn. Đó là lý do:
- Đặt **retry ở một tầng sở hữu rõ** nếu có thể.
- Giới hạn **max attempts**, bao gồm attempt đầu tiên.
- Giới hạn **per-call deadline** (connect + read + sleeps + attempts).
- Giới hạn **retry concurrency/budget** cho toàn process, không chỉ từng request.
- Tôn trọng 429/503 Retry-After và health signals.
- Dùng circuit breaker, bulkhead, rate limit khi thích hợp.
- Có telemetry phân biệt logical requests và raw attempts.

**Không hứa một total timeout = readTimeout**: readTimeout thường áp cho một giai đoạn network I/O, không nhất thiết bao gồm DNS, queue wait, nhiều attempts và sleeps. Deadline end-to-end phải đo/tính riêng.

## 12. Thiết kế ba tầng trong Spring

Kiến trúc gọn, dễ test:

```text
OrderFacade / business operation
   |
   +--> CatalogGateway.get(id) -- gọi RetryExecutor cho GET
   |                                 |
   |                                 +--> RestClient.get().retrieve()
   |
   +--> PaymentGateway.create(...) -- không auto retry POST
                                     |
                                     +--> RestClient.post().retrieve()

RestClient configuration
   +--> baseUrl
   +--> connect/read timeouts
   +--> ClientHttpRequestInterceptor (trace/log an toàn)
   +--> request factory
   +--> codecs / Jackson
   +--> status handling theo từng operation
```

**Phân chia trách nhiệm:**
- Interceptor: trace attempt, header từ cấu hình tin cậy, metrics/diagnostics.
- Gateway method: biết URL, DTO, domain status, operation semantics.
- RetryExecutor: biết lỗi retryable, method whitelist, budget, backoff, Retry-After.
- Business service: quyết định nếu thiếu resource, conflict, authorization hay cần idempotency key.
- Server downstream: thực sự bảo đảm dedup/transactions khi cho retry unsafe method.

Không đưa tên operation/payment vào retry utility bằng `if (path.startsWith("/payments"))`; truyền policy tường minh tốt hơn string matching path.

## 13. Dự án Maven hoàn chỉnh — Java 17, Spring Boot 3.3.5

Demo dùng `RestClient` gọi một upstream catalog giả định. Runtime có:
- `SimpleClientHttpRequestFactory` với connect/read timeout cụ thể.
- `ClientHttpRequestInterceptor` gắn `X-Client-Attempt-Id` per attempt và log method/status/duration.
- `RetryExecutor` chỉ cho `GET`/`HEAD`; lỗi transport/408/429/502/503/504 được cân nhắc.
- Backoff ngẫu nhiên bounded và hỗ trợ `Retry-After` dạng seconds/HTTP-date; nếu server yêu cầu đợi quá budget, dừng retry.
- `CatalogGateway` có `get` an toàn theo policy và `create` POST **không retry**.
- Unit/integration-style client tests với `MockRestServiceServer` và không cần gọi Internet.

**Giới hạn chủ động của demo:** Không có transaction/idempotency-key server hay distributed retry budget. `ResourceAccessException` được coi có thể transient trong demo **chỉ cho GET/HEAD**; sản phẩm thật cần phân loại TLS certificate/DNS configuration failures không đáng thử lại. Deadline tổng, bulkhead/circuit breaker và metrics phải bổ sung tùy workload.

### 13.1. Cấu trúc project

```text
sync-http-retry-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/syncclient/
    │   │   ├── SyncClientApplication.java
    │   │   ├── HttpClientConfig.java
    │   │   ├── CatalogItem.java
    │   │   ├── CatalogDraft.java
    │   │   ├── RetryExecutor.java
    │   │   └── CatalogGateway.java
    │   └── resources/application.properties
    └── test/java/example/syncclient/
        └── CatalogGatewayTest.java
```

### 13.2. Maven pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>example</groupId>
    <artifactId>sync-http-retry-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Spring Boot Web starter cung cấp Spring Web, Jackson và logging. `spring-boot-starter-test` cung cấp JUnit 5, `MockRestServiceServer` và Spring MVC test utilities. **Không cần WebFlux starter** để có RestClient.

### 13.3. Application class

**`src/main/java/example/syncclient/SyncClientApplication.java`**

```java
package example.syncclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SyncClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncClientApplication.class, args);
    }
}
```

Đây là Spring Boot app hoàn chỉnh. Không định nghĩa `CommandLineRunner` tự động gọi catalog giả định: chạy server không có nghĩa bắt buộc phải kết nối Internet. Code client được gọi từ service hoặc tests khi cần.

### 13.4. Cấu hình URL

**`src/main/resources/application.properties`**

```properties
catalog.base-url=https://catalog.example.test
```

Domain `example.test` là placeholder hợp lệ cho demo, **không phải dịch vụ catalog đang hoạt động**. Các bài test dùng MockRestServiceServer nên vẫn chạy offline. Trong production, dùng environment-specific trusted base URL qua application config/secrets management; không lấy host từ request của người dùng để tránh SSRF.

### 13.5. Cấu hình RestClient và interceptor

**`src/main/java/example/syncclient/HttpClientConfig.java`**

```java
package example.syncclient;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    private static final Logger log =
            LoggerFactory.getLogger(HttpClientConfig.class);

    @Bean
    RestClient catalogRestClient(
            RestClient.Builder builder,
            Environment environment) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);

        ClientHttpRequestInterceptor interceptor =
                (request, body, execution) -> {
                    // Mỗi attempt có một ID riêng.
                    request.getHeaders().set(
                            "X-Client-Attempt-Id",
                            UUID.randomUUID().toString());

                    long start = System.nanoTime();
                    try {
                        ClientHttpResponse response =
                                execution.execute(request, body);

                        long elapsedMs =
                                (System.nanoTime() - start)
                                        / 1_000_000;

                        log.info(
                                "catalog method={} status={} durationMs={}",
                                request.getMethod(),
                                response.getStatusCode().value(),
                                elapsedMs);

                        return response;
                    } catch (IOException error) {
                        log.warn(
                                "catalog method={} transportError={}",
                                request.getMethod(),
                                error.getClass().getSimpleName());
                        throw error;
                    }
                };

        String baseUrl = environment.getProperty(
                "catalog.base-url",
                "https://catalog.example.test");

        return builder.baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(interceptor)
                .build();
    }
}
```

**Vì sao interceptor này an toàn hơn "logging interceptor" thường thấy?**

- Một attempt gọi `execution.execute(request, body)` **một lần**.
- Header `X-Client-Attempt-Id` thay đổi theo attempt để điều tra network logs; **nó không phải Idempotency-Key**.
- Không log body/URL query/authentication headers.
- Log method/status/duration từ metadata ít nhạy cảm.
- Interceptor không tự retry. Khi retry boundary chạy một request mới, interceptor được gọi lại, tạo attempt ID mới.
- `ClientHttpResponse` được return cho RestClient xử lý/đóng theo flow bình thường. Nếu interceptor **đã nhận response rồi tự throw**, nó phải đóng response trước khi throw theo contract API; đoạn này không throw sau khi đã có response.
- Cấu hình timeout ở request factory giúp request đồng bộ không chờ vô hạn ở một số giai đoạn cơ bản.

**Hạn chế:** `SimpleClientHttpRequestFactory` dựa trên JDK URLConnection; ứng dụng production có thể chọn Apache HttpClient/JDK HttpClient request factory và connection pooling/TLS/proxy settings phù hợp. Connect/read timeout **không tự là tổng deadline**. Nếu có RestClient interceptors khác do Boot customizers cài sẵn, kiểm tra thứ tự/headers và không vô hiệu hóa instrumentation ngoài ý muốn.

### 13.6. DTO records

**`src/main/java/example/syncclient/CatalogItem.java`**

```java
package example.syncclient;

public record CatalogItem(long id, String name) {}
```

**`src/main/java/example/syncclient/CatalogDraft.java`**

```java
package example.syncclient;

public record CatalogDraft(String name) {}
```

Jackson trong Spring Boot xử lý Java records theo DTO. `CatalogDraft` không có validation framework ở demo vì trọng tâm là HTTP client; trong sản phẩm thật cần validate input trước khi gọi upstream và xử lý response schema/versions.

### 13.7. RetryExecutor: whitelist GET/HEAD, max 3 attempts

**`src/main/java/example/syncclient/RetryExecutor.java`**

```java
package example.syncclient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class RetryExecutor {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    private static final Duration MAX_DELAY =
            Duration.ofSeconds(2);

    private final int maxAttempts;
    private final Sleeper sleeper;

    public RetryExecutor(int maxAttempts, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.sleeper = sleeper;
    }

    public <T> T execute(
            HttpMethod method,
            Supplier<T> action) {

        // Demo chỉ tự retry hai phương thức đọc an toàn.
        // PUT/DELETE có thể idempotent theo HTTP nhưng cần
        // xác minh replayable body và upstream contract riêng.
        if (method != HttpMethod.GET &&
                method != HttpMethod.HEAD) {
            return action.get();
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RestClientException error) {
                if (attempt >= maxAttempts
                        || !isRetryable(error)) {
                    throw error;
                }

                Duration wait = delayFor(attempt, error);

                // Retry-After quá dài -> bỏ cuộc,
                // KHÔNG thử lại sớm hơn server yêu cầu.
                if (wait.compareTo(MAX_DELAY) > 0) {
                    throw error;
                }

                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Retry interrupted", interrupted);
                }
            }
        }
    }

    private boolean isRetryable(RestClientException error) {
        if (error instanceof ResourceAccessException) {
            // Chỉ cho read-only GET/HEAD; production cần
            // lọc DNS/certificate/configuration failures.
            return true;
        }

        if (error instanceof RestClientResponseException http) {
            int code = http.getStatusCode().value();
            return code == 408 || code == 429
                    || code == 502 || code == 503
                    || code == 504;
        }

        return false;
    }

    private Duration delayFor(
            int attempt,
            RestClientException error) {

        Duration requested = retryAfter(error);
        if (requested != null) {
            return requested;
        }

        // Full jitter, exponential growth, giới hạn nhỏ.
        int shift = Math.min(attempt - 1, 3);
        long upperMs = Math.min(400L, 50L << shift);
        long chosenMs = ThreadLocalRandom.current()
                .nextLong(upperMs + 1);
        return Duration.ofMillis(chosenMs);
    }

    private Duration retryAfter(RestClientException error) {
        if (!(error instanceof RestClientResponseException http)) {
            return null;
        }

        HttpHeaders headers = http.getResponseHeaders();
        if (headers == null) {
            return null;
        }

        String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        try {
            long seconds = Long.parseLong(value);
            if (seconds < 0) {
                return null;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // HTTP-date thay cho delta-seconds.
        }

        try {
            Instant when = ZonedDateTime.parse(
                    value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            Duration remaining = Duration.between(
                    Instant.now(), when);
            return remaining.isNegative()
                    ? Duration.ZERO
                    : remaining;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
```

### Giải thích chính sách của RetryExecutor

1. Chỉ `GET`/`HEAD` được xét retry tự động. **PUT/DELETE** dù idempotent theo HTTP vẫn bị tắt trong demo vì phải xác minh replayable request body và per-operation contract.
2. `maxAttempts = 3` nghĩa **tối đa 3 HTTP attempts gồm attempt đầu tiên**, không phải 1 + 3 retries.
3. `ResourceAccessException` được xem là candidate transient chỉ khi method safe; điều này vẫn quá rộng cho production nếu có TLS cert hoặc DNS misconfiguration.
4. Với HTTP status, chỉ chọn 408, 429, 502, 503, 504; 400/401/403/404/409/412/422/500 không được retry mặc định.
5. `Retry-After` dạng seconds hoặc RFC 1123 date được ưu tiên. Nếu yêu cầu đợi quá **2 giây per-backoff budget**, code **dừng** thay vì retry trước hạn.
6. Khi không có Retry-After, dùng exponential backoff nhỏ có full jitter. Số thực có thể thay đổi mỗi lần; tests không assert một delay tuyệt đối.
7. Interrupted thread được restore flag; không swallow interruption rồi tiếp tục HTTP call.
8. Retry wrapper ở ngoài RestClient: mỗi attempt đi qua interceptor, transport, status handlers như một request mới.
9. **Không có tổng deadline tuyệt đối** trong code demo; trong production nên thêm total budget và tính cả attempt time. Với 3 seconds read timeout trên mỗi attempt, tổng thời gian có thể vượt nhiều giây.
10. `Thread.sleep` ở Sleeper implementation là blocking **có chủ đích** vì bài nói về synchronous client; không dùng utility này trên Reactor Netty event loop hoặc task executor không đủ threads.

### Một điểm về HTTP-date và clock skew

`Instant.now()` lấy clock máy client. Nếu đồng hồ lệch, HTTP-date có thể dẫn tới duration không chính xác. Production có thể dùng injected `Clock` và cân nhắc HTTP `Date` header/time skew, nhưng không bao giờ nên coi một server-supplied date rất xa là lý do giữ thread ngủ hàng giờ. Với budget nhỏ, code chọn fail-fast.

### Khi nào mở rộng policy?

Khi upstream xác nhận PUT/DELETE replay-safe và có body có thể tái tạo, hãy truyền một **operation-level policy** thay vì chỉ sửa whitelist toàn app. Với POST được bảo vệ bằng Idempotency-Key, policy phải được bật cụ thể ở method đó và server có dedup atomic; không bật retry toàn bộ POST vì một endpoint hỗ trợ key.

### 13.8. Đăng ký RetryExecutor với Spring

Thêm Bean vào chính class cấu hình **`HttpClientConfig.java`** ở mục 13.5, trước dấu ngoặc đóng class:

```java
@Bean
RetryExecutor catalogRetryExecutor() {
    return new RetryExecutor(
            3,
            delay -> Thread.sleep(delay.toMillis()));
}
```

Đây là **đoạn bổ sung đặt vào class `HttpClientConfig`**, không phải một source file độc lập. Class config đã import `Bean`. Vì `RetryExecutor` và config cùng package, không cần import type đó. Trong production, config max attempts/backoff từ validated properties chứ không hardcode.

### 13.9. CatalogGateway: retry GET, không retry POST

**`src/main/java/example/syncclient/CatalogGateway.java`**

```java
package example.syncclient;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogGateway {

    private final RestClient client;
    private final RetryExecutor retry;

    public CatalogGateway(
            RestClient catalogRestClient,
            RetryExecutor catalogRetryExecutor) {
        this.client = catalogRestClient;
        this.retry = catalogRetryExecutor;
    }

    public CatalogItem get(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "id must be positive");
        }

        return retry.execute(HttpMethod.GET, () -> {
            CatalogItem item = client.get()
                    .uri("/items/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(CatalogItem.class);

            if (item == null) {
                throw new IllegalStateException(
                        "Empty catalog response");
            }
            return item;
        });
    }

    public void create(CatalogDraft draft) {
        if (draft == null || draft.name() == null
                || draft.name().isBlank()) {
            throw new IllegalArgumentException(
                    "name required");
        }

        // POST cố ý chỉ thực hiện MỘT attempt.
        // Retry chỉ khi server có Idempotency-Key
        // contract và client giữ ổn định key.
        client.post()
                .uri("/items")
                .contentType(MediaType.APPLICATION_JSON)
                .body(draft)
                .retrieve()
                .toBodilessEntity();
    }
}
```

**Tại sao không catch 404 và gọi lại?** HTTP 404 thường là kết quả cuối cho resource không tồn tại. Caller có thể chuyển thành domain "catalog item not found", không cần retry. Nếu `get` nhận 503 và policy cho phép, RetryExecutor tự chạy lại tối đa 3 attempts.

**Tại sao POST không retry dù trả 503?** Upstream có thể đã tạo item nhưng reverse proxy trả 503 hoặc response bị mất. Chỉ khi catalog API có idempotency/dedup contract thật mới xem xét bật retry có key ổn định.

### Bước dùng code demo

- `mvn test`: chạy tests với mock upstream, không cần kết nối mạng đến domain giả.
- `mvn spring-boot:run`: khởi động app bình thường; Bean RestClient trỏ tới demo catalog URL chỉ **khi có code gọi CatalogGateway**. Domain `example.test` không phục vụ API thật.
- Khi tích hợp dịch vụ catalog thật, cấu hình `catalog.base-url` tới trusted endpoint và gọi `CatalogGateway` từ business service; không gửi host tùy ý từ client.

## 14. MockRestServiceServer: kiểm tra số lần thử thật

`MockRestServiceServer` mô phỏng downstream HTTP responses mà **không mở socket thật**. Nó cho phép assert:
- Method, URI, headers mà RestClient gửi.
- Mã status trả về trên từng attempt.
- Có đúng 1 hay 3 request.
- DTO response được decode.
- Retry-After dài có làm dừng retry không.
- POST và HTTP lỗi không phù hợp có tránh retry không.

### 14.1. Test file đầy đủ

**`src/test/java/example/syncclient/CatalogGatewayTest.java`**

```java
package example.syncclient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogGatewayTest {

    private MockRestServiceServer server;
    private CatalogGateway gateway;
    private RetryExecutor retries;

    @BeforeEach
    void prepare() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.example.test")
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(
                            "X-Client-Attempt-Id", "from-test");
                    return execution.execute(request, body);
                });

        server = MockRestServiceServer.bindTo(builder).build();

        // Không ngủ thật trong unit tests.
        retries = new RetryExecutor(
                3,
                duration -> { });

        gateway = new CatalogGateway(builder.build(), retries);
    }

    @Test
    void get200ReturnsDecodedDtoAndInterceptorHeader() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "X-Client-Attempt-Id", "from-test"))
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Pen\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(new CatalogItem(7, "Pen"),
                gateway.get(7));
        server.verify();
    }

    @Test
    void get503Then200RetriesExactlyOnce() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/8"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(
                        HttpStatus.SERVICE_UNAVAILABLE));

        server.expect(requestTo(
                    "https://catalog.example.test/items/8"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":8,\"name\":\"Notebook\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("Notebook", gateway.get(8).name());
        server.verify();
    }

    @Test
    void get429Then200CanRetry() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/9"))
                .andRespond(withStatus(
                        HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "0"));

        server.expect(requestTo(
                    "https://catalog.example.test/items/9"))
                .andRespond(withSuccess(
                        "{\"id\":9,\"name\":\"Ruler\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("Ruler", gateway.get(9).name());
        server.verify();
    }

    @Test
    void get404DoesNotRetry() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/11"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(HttpClientErrorException.class,
                () -> gateway.get(11));
        server.verify();
    }

    @Test
    void get400DoesNotRetry() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/12"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(HttpClientErrorException.class,
                () -> gateway.get(12));
        server.verify();
    }

    @Test
    void get500DoesNotRetryByThisPolicy() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/13"))
                .andRespond(withStatus(
                        HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.class,
                () -> gateway.get(13));
        server.verify();
    }

    @Test
    void get502ExhaustsThreeAttempts() {
        for (int n = 0; n < 3; n++) {
            server.expect(requestTo(
                        "https://catalog.example.test/items/14"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(
                            HttpStatus.BAD_GATEWAY));
        }

        assertThrows(HttpServerErrorException.class,
                () -> gateway.get(14));
        server.verify();
    }

    @Test
    void tooLongRetryAfterSkipsFurtherAttempts() {
        server.expect(requestTo(
                    "https://catalog.example.test/items/15"))
                .andRespond(withStatus(
                        HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "30"));

        assertThrows(HttpClientErrorException.class,
                () -> gateway.get(15));
        server.verify();
    }

    @Test
    void post503MustNotBeRetried() {
        server.expect(requestTo(
                    "https://catalog.example.test/items"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(
                        HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class,
                () -> gateway.create(
                        new CatalogDraft("Keyboard")));
        server.verify();
    }

    @Test
    void retryExecutorWillNotRetryPostEvenForIoError() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(ResourceAccessException.class,
                () -> retries.execute(HttpMethod.POST, () -> {
                    calls.incrementAndGet();
                    throw new ResourceAccessException(
                            "simulated socket reset");
                }));

        assertEquals(1, calls.get());
    }

    @Test
    void retryExecutorCanRetryIoErrorForGet() {
        AtomicInteger calls = new AtomicInteger();

        String value = retries.execute(HttpMethod.GET, () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw new ResourceAccessException(
                        "simulated transient I/O error");
            }
            return "ok";
        });

        assertEquals("ok", value);
        assertEquals(2, calls.get());
    }
}
```

**11 test methods**, không ngủ thật nhờ injected `Sleeper` no-op. Mỗi `server.expect(...)` tạo kỳ vọng về **một** request; `server.verify()` xác nhận mọi expectations đã được thực hiện. Nếu RetryExecutor lặp một request 404, 400, 500 hoặc POST không đúng policy, test sẽ thất bại vì có request ngoài expectations.

### Cần đọc kỹ assertions

- Test 503→200 và 429→200: xác nhận transient response và successful retry.
- Test 502 exhausted: có ba HTTP attempts đúng bằng max attempts.
- Test 404/400/500: fail ngay, không sinh request thứ hai.
- Test Retry-After 30 giây: vì per-backoff budget chỉ 2 giây, code **không retry**; không chờ 2 giây rồi gửi request vi phạm chỉ dẫn 30 giây.
- Test POST 503: client **chỉ gửi một POST** vì không có idempotency server contract.
- Test transport exception GET: policy minh họa cho retry read-only.
- Test transport exception POST: ngay cả khi chưa có HTTP status, thao tác unsafe vẫn không được lặp.
- Test header: chứng minh interceptor chạy và thêm request metadata, không cần ngủ hoặc kết nối Internet.

### Ghi chú runtime QA

Mã được biên soạn để phù hợp Java 17, Spring Boot 3.3.5, Spring Framework 6.1 và các APIs `MockRestServiceServer.bindTo(RestClient.Builder)`. Trong phạm vi tạo Knowledge trên Google Sheets, **không có bước chạy Maven trên workspace**; đây là ví dụ cần người triển khai chạy `mvn test` với toolchain đã cài. Không coi phần mô tả expected assertions là kết quả test đã thực thi.

### Kiểm thử bổ sung cần cho production

- HTTP-date Retry-After với injected Clock và time skew.
- Read timeout xảy ra sau khi server đã nhận request.
- TLS certificate errors và DNS configuration failures được đánh dấu nonretryable.
- Redirect policy, bảo toàn Authorization và tránh redirect cross-host.
- Body replayability khi retry PUT/POST; streaming upload.
- Connect/read timeout thực với local test server, không chỉ MockRestServiceServer.
- Tổng retry budget khi một attempt treo 3 giây và nhiều sleeps.
- Pool exhaustion và bulkhead behavior dưới concurrent load.
- Idempotency-Key end-to-end với server atomic dedup và parallel duplicate submits.
- Partial success/business 409/412 behavior.
- Observability: logical request vs attempt spans và masking PII.
- Proxy, mTLS, rate limit, circuit breaker, client cancellation/interruption.

## 15. Một ví dụ xử lý 404 thành Optional thay vì exception

Nếu application contract coi missing catalog item là expected outcome, có thể dùng `exchange`:

```java
Optional<Boolean> result = client.head()
        .uri("/items/{id}", id)
        .exchange((request, response) -> {
            int status = response.getStatusCode().value();
            if (status == 404) {
                return Optional.empty();
            }
            if (status >= 200 && status < 300) {
                return Optional.of(Boolean.TRUE);
            }
            throw new IllegalStateException(
                    "Unexpected upstream status " + status);
        });
```

Đây là snippet với `client`/`id` đã có và import `Optional`. Nó thể hiện cách **không dùng default retrieve() exceptions** khi muốn kiểm soát status trực tiếp. Không làm `if (status >= 400) return Optional.empty()` vì sẽ che 429/503 thành "resource không có".

**Quan trọng:** snippet này không có retry. Nếu muốn retry sau `exchange`, phải chuyển status 429/502/503/504 thành typed error hoặc custom result có retry metadata mà RetryExecutor hiểu. Mọi error mapping thay đổi tác động đến retry policy.

Với `RestClient.retrieve()`, nếu 404 là expected, có thể dùng `onStatus` để throw domain exception riêng rồi xử lý ở caller. Đó là lựa chọn API semantics, không có một cách áp dụng đúng cho mọi client.

## 16. RestTemplate: interceptor và ResponseErrorHandler

Trong codebase dùng `RestTemplate`:

```java
RestTemplate restTemplate = new RestTemplate();

restTemplate.setInterceptors(
        List.of((request, body, execution) -> {
            request.getHeaders().set(
                    "X-Caller-Service", "inventory");
            return execution.execute(request, body);
        }));

restTemplate.setErrorHandler(
        new DefaultResponseErrorHandler());
```

Snippet cần imports `List`, `RestTemplate`, `DefaultResponseErrorHandler`. Handler mặc định đã thường có sẵn; dòng `setErrorHandler` ở đây để minh họa điểm cấu hình. Nó xử lý 4xx/5xx theo template semantics; custom implementation có thể đổi cách phân loại.

Nếu muốn chuyển sang RestClient mà giữ cấu hình nền từ template:

```java
RestClient client = RestClient.builder(restTemplate)
        .build();
```

Đây là API supported, giúp lấy một số cấu hình RestTemplate vào builder (request factory, converters, interceptors, URI settings, error handler). Sau migrate phải test thứ tự interceptors, status exceptions, client metrics và retry counts; không giả định behavior tuyệt đối giống nhau với mọi custom RestTemplate extension.

### ResponseErrorHandler custom

`ResponseErrorHandler` có `hasError(ClientHttpResponse)` và `handleError(...)`. Hãy phân biệt:
- Custom handler chỉ thay status-to-error mapping.
- Không tự biết operation nào idempotent.
- Không tự cung cấp retry/backoff.
- Nếu handler chỉ swallow 503, caller có thể cố parse 503 body thành DTO success.

Vì vậy, vẫn để retry policy ở tầng operation/service, không nhúng retries vào `handleError`.

## 17. Timeout: connect, response/read và toàn call

Một synchronous HTTP call tiêu tốn thread khi:
- Chờ connection/DNS/TLS.
- Chờ server response.
- Đọc body bytes.
- Ngủ backoff rồi gửi attempt khác.

`SimpleClientHttpRequestFactory.setConnectTimeout(2_000)` và `setReadTimeout(3_000)` giúp đặt một số giới hạn thấp của transport. Tuy nhiên:
- DNS resolution có thể có behavior khác.
- Một response stream chuyển dữ liệu ít một có thể giữ tổng request lâu hơn read timeout mỗi lần chờ.
- TLS handshake/proxy timeout có thể cần config client implementation riêng.
- Connection pool wait không phải lúc nào cũng tính vào connect timeout.
- Ba attempts có thể mất xấp xỉ ba lần timeout cộng backoffs.

**Per-request timeout khác end-to-end deadline.** Nếu HTTP handler phía trên có deadline 2 giây nhưng retry wrapper có thể giữ thread tới 10 giây, request trên server sẽ vượt budget. Production cần tổng deadline từ caller, cancel/timeout policy đúng client implementation và resource pool.

### Không ngủ rất lâu trong thread của synchronous API

Nếu server gửi `Retry-After: 120`, có hai phương án:
1. Chuyển failure/metadata cho higher layer và không retry inline.
2. Lên lịch lại công việc qua durable queue/job scheduler nếu nhiệm vụ cho phép.

Không giữ một Tomcat request thread ngủ 120 giây chỉ để thử lại GET. Nếu caller thực sự là batch worker với ít concurrency, policy có thể khác; nhưng vẫn cần giới hạn.

## 18. Exponential backoff, jitter và các biến số

Công thức backoff thông dụng:

\[
B_n = \min(B_{\max}, B_0 \cdot 2^{n-1})
\]

Với full jitter:

\[
W_n \sim U(0,B_n)
\]

Trong đó:
- `n` là số attempt vừa thất bại (attempt đầu n=1).
- `B0` là base delay.
- `Bmax` là cap.
- `Wn` được chọn ngẫu nhiên trong khoảng từ 0 đến Bn.

Ví dụ base 100ms/cap 1.000ms:

| Lỗi sau attempt | Upper bound không jitter | Một delay hợp lệ |
| --- | --- | --- |
| 1 | 100 ms | 74 ms |
| 2 | 200 ms | 11 ms |
| 3 | 400 ms | 205 ms |
| 4 | 800 ms | 690 ms |
| 5 | 1000 ms | 447 ms |

Các delays bên phải **chỉ là ví dụ**, không phải output cố định khi chạy code. Jitter phân tán các requests của nhiều clients, tránh đồng bộ "thundering herd". **Không** dùng random jitter để phớt lờ `Retry-After` lớn hơn.

### Retry budget theo số lượng và thời gian

`maxAttempts=3` là giới hạn lần gọi, nhưng chưa đủ. Nếu mỗi attempt 10 giây và sleep 2 giây, một logical request có thể giữ thread >30 giây. Cần:
- Per-attempt connect/read timeout.
- Absolute deadline/remaining budget truyền từ caller.
- Limit tổng queue/concurrency.
- Budget thống nhất với gateway/client khác.
- Metrics logical latency và number-of-attempts.

### Circuit breaker khác retry

Retry phù hợp với lỗi tạm thời ngắn. Circuit breaker dừng gọi upstream khi nó đang fail/overloaded thường xuyên; bulkhead giới hạn số concurrent requests; rate limiter ngăn tạo quá nhiều load. Đây là các cơ chế **bổ sung**, không nên cấu hình để tất cả cùng retry ở nhiều tầng.

## 19. Idempotency-Key: contract phía server phải xử lý ra sao?

Một pattern phổ biến với POST create:

```http
POST /payments HTTP/1.1
Host: payments.example.test
Content-Type: application/json
Idempotency-Key: 2fb18684-1dbd-484e-b188-87dc29d4dace

{"orderId":"order-1024","amount":1500}
```

Key là minh họa. Hệ thống thật cần:
1. Validate principal/tenant và quy tắc tạo payment.
2. Khóa/deduplicate cùng key **theo tenant+operation scope**.
3. So sánh payload fingerprint; cùng key khác payload phải conflict.
4. Với first request, ghi side effect và result theo transaction/consistency policy.
5. Với retry cùng key, trả lại outcome có contract rõ (same status/body hoặc status retrieval).
6. Với concurrent identical submissions, chỉ một request thực hiện side effect; các request khác nhận cached result/in-progress status.
7. Có TTL phù hợp với client retry window; TTL hết rồi key có thể lại được xem như mới.
8. Có metrics/audit, không lộ kết quả operation của user khác.

Một client có thể gắn key qua:

```java
UUID logicalOperationId = UUID.randomUUID();

client.post()
        .uri("/payments")
        .header(
                "Idempotency-Key",
                logicalOperationId.toString())
        .body(paymentRequest)
        .retrieve()
        .toBodilessEntity();
```

Đây là **snippet không triển khai retries** và sử dụng biến `client`/`paymentRequest` giả định. Khi nghiệp vụ quyết định retry cùng logical operation, phải **giữ nguyên logicalOperationId** qua mọi attempts — đừng sinh UUID mới bên trong interceptor cho mỗi attempt.

**Không tự bật retry POST chỉ nhờ snippet này.** Nếu payment server không có idempotency-key handling, header không giúp chống duplicate. Và nếu body streaming không replayable, client còn phải tái tạo bytes cho mỗi attempt.

## 20. Observability: logical request vs từng attempt

Một logical operation có thể tạo ba physical HTTP attempts. Nên có hai cấp telemetry:

```text
Logical operation: catalog.get(id=42)
   |
   +-- attempt=1, status=503, duration=350ms
   +-- wait=100ms
   +-- attempt=2, status=502, duration=120ms
   +-- wait=200ms
   +-- attempt=3, status=200, duration=80ms
   |
   +-- finalOutcome=SUCCESS
   +-- logicalLatency=850ms
```

Con số latency là ví dụ. Nếu chỉ đếm attempts, request count sẽ tăng 3x và error-rate metric có thể cao dù logical caller cuối cùng thành công. Nếu chỉ đo logical operation, bạn có thể bỏ qua 503 storms upstream.

Nên đo:
- Logical outcome và attempts-per-operation.
- Status distribution theo upstream service và endpoint **template**, không raw ID/query.
- Retries attempted/exhausted/suppressed (unsafe, nonretryable, budget expired, Retry-After too long).
- Per-attempt duration vs overall latency.
- Connect/read timeout, pool wait, response deserialization errors.
- Concurrency, circuit breaker state và load.
- Trace IDs qua frameworks như Micrometer Observation/tracing; không tự ghi raw Authorization header.

### Correlation IDs

Một request attempt có thể có ID riêng cho debugging. Logical operation nên có trace/span hoặc business correlation ID ổn định ở tầng cao hơn. **Idempotency-Key là khóa chống lặp nghiệp vụ, không đồng nghĩa trace ID**, và không nên dùng UUID per attempt làm idempotency key.

## 21. Bảo mật và privacy trong interceptor

### Không log secrets

Đặc biệt thận trọng với:
- `Authorization`/Bearer tokens.
- `Cookie` và `Set-Cookie`.
- Basic auth credentials.
- JWT claims hoặc query tokens.
- Payment/card data và nội dung body.
- Request URLs chứa customer identifiers.

Có thể log HTTP method, upstream service name, status family, duration và correlation ID đã sanitize.

### Tránh SSRF

Đừng cho caller truyền host tuyệt đối hoặc URL từ user input vào `RestClient` được phép truy cập network nội bộ. Sử dụng trusted `baseUrl`, allowlist hosts/schemes và kiểm soát redirects. Một interceptor tự động gắn Authorization vào mọi redirects/cross-host calls có thể lộ credentials nếu không có redirect/host policy phù hợp.

### TLS và certificate

Không retry lỗi certificate validation như thể upstream chỉ tạm quá tải. Không tắt hostname verification để "fix" `ResourceAccessException`. Chọn trust store, mTLS/client cert, proxy/HTTPS configuration đúng.

### Headers và duplicate authorization

Một interceptor nên tránh ghi đè các headers do secure auth mechanism thiết lập, trừ khi có chủ đích. Với Spring Security OAuth2 client, sử dụng integrations tương ứng để refresh/attach token; không tự retry 401 mãi hoặc log tokens.

## 22. Những tình huống retry thực tế

### 22.1. GET catalog 503 rồi 200

Có thể retry bounded, tốt nhất tôn trọng Retry-After. Kết quả cuối là item 200 và hai attempts. Không cần data dedup vì GET là read-only theo contract.

### 22.2. GET 404 ngay sau tạo resource

Nếu upstream eventual-consistent và contract **cho phép đọc chậm**, có thể dùng polling/backoff theo "resource readiness" trong một **workflow riêng**, không tự bật retry 404 cho mọi GET. Timeout/polling budget và visibility semantics phải rõ.

### 22.3. PUT với If-Match

PUT idempotent về intended effect, nhưng retry có thể gặp 412 do lần đầu đã commit và ETag đổi. Client cần đọc lại resource/version để xác định trạng thái cuối. Không cố retry vô hạn bằng một ETag cũ.

### 22.4. DELETE đã thành công nhưng retry trả 404

Desired state "không còn resource" có thể đã đạt. Một API contract có thể coi 404 sau retry là acceptable, hoặc có semantics riêng. Cần phân biệt business operation thành công với HTTP status trên attempt cuối.

### 22.5. POST payment timeout

Không retry nếu thiếu server idempotency contract. Nếu có key/dedup, trả lại cùng logical operation và reconcile với payment status endpoint. Không tạo key mới từng attempt.

### 22.6. 429 với Retry-After: 60

Nếu caller chỉ còn budget 3 giây, fail fast hoặc schedule lại công việc sau 60 giây. Không ngủ 3 giây rồi gửi lại, vi phạm server hint.

### 22.7. 500 do deserialization/schema mismatch

Không retry: cùng response body hỏng sẽ tiếp tục hỏng. Tách mapping errors (Jackson/DTO versioning) khỏi status 500 từ upstream, alert schema compatibility.

### 22.8. 502/504 ở API gateway

Có thể xảy ra sau khi upstream đã nhận side-effecting request. Chỉ retry nếu idempotent/replay-safe. Gateway 504 không phải bằng chứng request chưa chạy.

## 23. Kiểm thử idempotency end-to-end với fake payment server

Một unit test client POST no-retry là cần thiết nhưng **không đủ** để chứng minh exactly-once charge. Với server hỗ trợ Idempotency-Key, integration test nên:

1. Gửi cùng `Idempotency-Key` và cùng payload hai lần.
2. Xác nhận **chỉ có một** payment record/charge trong DB/ledger.
3. Xác nhận response thứ hai theo contract (same ID/result).
4. Gửi cùng key với payload khác, server trả conflict.
5. Gửi hai requests đồng thời cùng key để tìm race.
6. Mô phỏng crash giữa charge commit và dedup persistence.
7. Kiểm tra TTL/key scope theo principal/tenant.
8. Mô phỏng response loss sau commit và retry cùng key.

Nếu server chỉ trả 200 cho hai attempts mà DB có hai charge, idempotency **đã thất bại** dù HTTP response trông hợp lệ. Exactly-once business effects đòi hỏi server-side atomicity/coordination và kiểm thử dưới concurrency/failures.

## 24. Các lỗi phổ biến

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| GET 503 không retry | Caller không dùng RetryExecutor hoặc custom status handler nuốt exception | Giữ typed error, gọi retry boundary đúng operation |
| POST bị tạo hai records | Retry unsafe operation thiếu idempotency contract | Không auto retry POST; server key + atomic dedup |
| Retry vô hạn | Không maxAttempts/deadline | Bounded attempts và total budget |
| Retry quá sớm so với Retry-After | Chỉ dùng fixed sleep/backoff | Parse Retry-After và skip khi vượt budget |
| Retry 400/403 | Coi mọi 4xx transient | Fail fast và sửa input/ACL |
| Retry 500 vô ích | Bug code hoặc schema mismatch | Phân loại lỗi, đừng blanket-retry 5xx |
| 429 response không còn header | Custom exception wrapper làm mất response headers | Preserve status, Retry-After, safe diagnostics |
| 404 bị biến thành empty DTO | Swallow status trong onStatus/handler | Map missing rõ thành Optional/domain exception |
| Dùng `exchange()` nhưng đợi `onStatus` chạy | `exchange` bypass status handlers | Xử lý status trực tiếp trong callback |
| Interceptor chạy hai lần một attempt | Nhiều registrations hoặc tự execute lặp | Kiểm tra registration/chain, one execute |
| Connection pool cạn | Response interceptor ném sau khi nhận nhưng không close | Close response trước throw, monitor pool |
| Logs lộ password | Dump body/Authorization/query | Sanitize metadata, không log raw bodies |
| Một idempotency key mới mỗi retry | Tạo UUID trong interceptor | Tạo key một lần tại logical business operation |
| Cùng key nhưng hai payload khác nhau | Không kiểm tra fingerprint/server scope | Server 409/conflict rule |
| TLS cert lỗi mà client cứ retry | ResourceAccessException bị coi mọi lỗi transient | Phân loại certificate/DNS config failure |
| Total latency rất dài | Chỉ giới hạn per-attempt timeout | End-to-end deadline và budget |
| Requests đồng loạt sau sự cố | Fixed sleep, không jitter | Full jitter/backoff/rate limits |
| Retry storm qua gateway/app | Mỗi tầng retry độc lập | Chọn owner một tầng, global budget |
| Error-rate metric tăng x3 | Đếm raw attempts như logical operations | Metrics tách logical và attempts |
| Client chuyển credentials sai host | Redirect/SSRF policy thiếu | Trusted base URL, redirect allowlist |
| Blocking threads bị nghẽn | Sleep lâu trong synchronous web request | Fail fast/deferred job hoặc async architecture |
| DELETE lần 2 trả 404 gây "thất bại" | Nhầm idempotency với response equality | Kiểm tra desired state và contract |
| PUT lần 2 trả 412 | If-Match stale sau lần đầu commit | Reconcile resource/ETag |
| Retry body stream lỗi | One-shot body không replayable | Buffer giới hạn/tái tạo request body |
| Test không xác nhận số lần gọi | Chỉ assert final 200 | MockRestServiceServer.verify và expectations cụ thể |

## 25. Checklist thiết kế HTTP client đồng bộ

- [ ] Chọn đúng API `RestClient`/`RestTemplate` theo project và hỗ trợ Spring version.
- [ ] Interceptor chỉ can thiệp request/response, không tự retry mọi requests.
- [ ] Ghi metadata an toàn; không log body/secrets.
- [ ] Không để response bị leak khi interceptor ném sau HTTP response.
- [ ] Lỗi HTTP và lỗi I/O được phân biệt.
- [ ] `retrieve()`/`exchange()` semantics được hiểu và test.
- [ ] 400/401/403/404/409/412/422/429/5xx có policy cụ thể.
- [ ] Retry chỉ bật khi operation semantically safe/idempotent/replayable.
- [ ] POST/PATCH không retry mặc định; Idempotency-Key chỉ có ý nghĩa khi server hỗ trợ.
- [ ] Max attempts tính cả attempt đầu, có per-attempt timeout và deadline tổng.
- [ ] Tôn trọng Retry-After seconds/HTTP-date.
- [ ] Backoff có jitter, không vượt caller budget.
- [ ] Không có retry storm qua gateway/app/load balancer.
- [ ] Không retry TLS certificate/configuration errors như lỗi transient.
- [ ] Một logical operation có trace ID; mỗi attempt có diagnostics riêng.
- [ ] Auth token refresh bounded, không bật infinite retry 401.
- [ ] Test chính xác requests/headers/status/attempt counts bằng MockRestServiceServer.
- [ ] Test socket timeout và real HTTP transport riêng.
- [ ] Test idempotency server-side dưới concurrent duplicate requests/failure injection.
- [ ] Quan sát circuit breaker, bulkhead và metrics trước rollout production.

## 26. Bài tập thực hành

### Bài 1 — RestClient status mapping

Cho mock upstream trả 404, 429, 500 và 503. Viết ba phiên bản:
1. `retrieve()` mặc định.
2. `retrieve().onStatus(...)` ánh xạ riêng 404.
3. `exchange(...)` trả một domain result tùy status.

Chứng minh rằng `exchange(...)` **không tự kích hoạt** các status handlers của retrieve và phải giữ đúng retry metadata khi cần.

### Bài 2 — Retry 503 với Retry-After

Mock GET trả 503 + `Retry-After: 0`, sau đó 200. Bảo đảm có **hai** attempts. Đổi Retry-After thành 30 seconds, budget 2 seconds: chỉ **một** attempt. Không sleep thật trong test; inject Sleeper và capture requested delays.

### Bài 3 — Lựa chọn method

Cho GET, HEAD, PUT, DELETE, POST và PATCH cùng gặp socket reset. Với RetryExecutor demo, chỉ GET/HEAD tự retry. Giải thích vì sao PUT/DELETE idempotent theo RFC nhưng cần chính sách riêng về body replayability/preconditions/server semantics trước khi bật.

### Bài 4 — Payment Idempotency-Key

Tạo một server giả có DB và unique constraint theo tenant + key. Gửi cùng key/payload hai lần, trả cùng payment ID. Gửi key đó với payload khác, trả conflict. Chạy hai requests đồng thời để tìm race; xử lý crash/TTL và không leak kết quả giữa các tenants.

### Bài 5 — Chặn retry storm

Mô phỏng gateway retry 2 lần và application retry 3 lần. Đo raw downstream attempts so với logical calls. Chọn một retry owner, thêm circuit breaker và budget; kiểm tra attempts giảm.

### Bài 6 — Phân loại I/O error

Giả lập connect timeout, connection reset, certificate validation failure và DNS configuration error. Cho một GET safe, quyết định lỗi nào đáng retry và lỗi nào fail fast. Đừng chỉ dùng `instanceof ResourceAccessException` cho mọi tình huống production.

### Bài 7 — Logging an toàn

Thêm interceptor logging method/status/latency/correlation ID. Assert logs **không** chứa bearer token, password trong body, customer ID trên query hoặc full exception response body. Nếu interceptor throw sau response, verify đã close.

## 27. Câu hỏi tự kiểm tra

1. `ClientHttpRequestInterceptor` nhận tham số nào và trả về gì?
2. Vì sao interceptor nên gọi `execution.execute` một lần mỗi attempt?
3. `RestClient.retrieve()` thường xử lý 4xx/5xx như thế nào?
4. `RestClient.exchange()` có chạy default status handlers không?
5. `onStatus` khác `defaultStatusHandler` về scope và order?
6. `RestTemplate.ResponseErrorHandler` có phải retry policy không?
7. `RestClientResponseException` khác `ResourceAccessException` ở đâu?
8. Có thể có server side effect dù client nhận timeout không?
9. Safe method và idempotent method giống/khác nhau ra sao?
10. Vì sao GET, HEAD thường dễ retry hơn POST?
11. PUT/DELETE idempotent có nghĩa response retry luôn bằng response đầu không?
12. Vì sao HTTP 504 không chứng minh operation chưa chạy?
13. Status 400/403/404/409/412 có nên retry tự động không?
14. 429 và 503 có thể kèm header nào gợi ý thời gian chờ?
15. Retry-After có những hai định dạng nào?
16. Nếu Retry-After 60s nhưng deadline còn 2s, xử lý thế nào?
17. MaxAttempts=3 nghĩa tối đa bao nhiêu HTTP requests?
18. Vì sao exponential backoff nên có jitter?
19. Vì sao nhiều tầng retries tạo retry storm?
20. Idempotency-Key giúp gì nếu server chưa triển khai dedup?
21. Vì sao key phải giữ nguyên cho cùng logical operation qua attempts?
22. Cùng key nhưng payload khác phải xử lý ra sao?
23. Có thể dựa vào process-local cache để bảo đảm exactly-once charge trong cluster không?
24. Vì sao không retry TLS certificate errors mãi?
25. Vì sao read timeout không phải total deadline?
26. Khi dùng `ResponseErrorHandler` tự viết, nó có biết business operation nào safe không?
27. Trong test, làm sao assert một GET 503 được gọi hai lần thay vì chỉ assert final 200?
28. Vì sao interceptor không nên log response body raw?
29. Khi dùng RestClient.Builder(RestTemplate), cần kiểm tra lại những gì?
30. Nếu request body là input stream one-shot, retry có nguy cơ gì?
31. Vì sao POST `Idempotency-Key` cần backend transaction/unique constraint?
32. `HttpStatusCode` có khác integer status không?
33. `onStatus` chỉ chạy khi đã có HTTP response hay cả khi DNS timeout?
34. Khi nào circuit breaker có ích hơn tiếp tục retry?
35. Mục tiêu chính của MockRestServiceServer trong bài là gì?

**Gợi ý:** (1) HttpRequest, byte[] body, execution; ClientHttpResponse. (2) Tránh duplicate side effect và leaking response. (3) Ném RestClientException cho error statuses. (4) Không. (5) Per request vs client-wide defaults. (6) Không. (7) Có HTTP response vs network access failure. (8) Có. (9) Safe không yêu cầu side effect, idempotent lặp có intended effect tương đương. (10) Read-only semantics. (11) Không. (12) Upstream có thể đã commit. (13) Không mặc định. (14) Retry-After. (15) delta-seconds, HTTP-date. (16) Fail/defer, không retry sớm. (17) Ba, gồm attempt đầu. (18) Giảm đồng loạt. (19) Multiplicative attempts. (20) Không có bảo đảm nếu server bỏ qua key. (21) Dedup cùng operation. (22) Conflict. (23) Không. (24) Configuration không tự hết lỗi. (25) Có nhiều phases/attempts/sleeps. (26) Không. (27) Expect hai requests và verify. (28) Secrets/privacy. (29) Factory, interceptors, codecs, error/status behavior. (30) Không replayable. (31) Chống double side effect race. (32) Spring abstraction cho status. (33) Có response. (34) Khi upstream unhealthy kéo dài. (35) Xác minh requests/status/attempts mà không cần Internet.

## 28. Tài liệu tham khảo chính thức

- [Spring Framework — REST Clients: RestClient và RestTemplate](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring Framework 6.1 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/RestClient.html)
- [Spring Framework 6.1 — RestClient.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/RestClient.Builder.html)
- [Spring Framework 6.1 — ClientHttpRequestInterceptor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/ClientHttpRequestInterceptor.html)
- [Spring Framework 6.1 — ClientHttpRequestExecution Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/client/ClientHttpRequestExecution.html)
- [Spring Framework 6.1 — RestClientResponseException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/RestClientResponseException.html)
- [Spring Framework 6.1 — DefaultResponseErrorHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/DefaultResponseErrorHandler.html)
- [Spring Framework 6.1 — ResourceAccessException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/client/ResourceAccessException.html)
- [Spring Framework — MockRestServiceServer](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html)
- [Spring Boot 3.3 — Calling REST Services](https://docs.spring.io/spring-boot/3.3/reference/io/rest-client.html)
- [RFC 9110 — HTTP Semantics, Safe and Idempotent Methods](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 9110 — Retry-After header](https://www.rfc-editor.org/rfc/rfc9110.html#field.retry-after)
- [RFC 6585 — 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)

**Tổng kết:** `ClientHttpRequestInterceptor` là cơ chế bổ sung headers/observability quanh **mỗi HTTP attempt**, không nên là nơi tự động phát lại mọi request. `RestClient.retrieve()`, `onStatus`/`defaultStatusHandler` và `RestTemplate.ResponseErrorHandler` giúp chuyển HTTP statuses thành thông tin lỗi có cấu trúc, nhưng **không tự quyết định thao tác có được lặp hay không**. Retry phải được điều khiển theo semantics (safe/idempotent/replayable), error classification, `Retry-After`, backoff/jitter, total budget và Idempotency-Key phía server khi thao tác có side effects. Trong dịch vụ đồng bộ, retries giữ thread và có thể nhân tải — vì vậy **fail fast với lỗi không transient và POST unsafe**, kiểm thử số attempts chính xác, đo logical request lẫn attempts và chỉ thêm resilience features theo nhu cầu thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-http-clients-009">RestTemplate trong hệ thống sẵn có và chuyển sang RestClient; deprecated từ Spring 7</a>

<details>
<summary>Xem chi tiết</summary>

# RestTemplate trong hệ thống sẵn có và chuyển dần sang RestClient — deprecation từ Spring Framework 7

> **Phạm vi:** Spring Framework 6.2.x, Spring Boot 3.5.x, Java 17+ cho ví dụ có thể chạy; có đối chiếu thông báo deprecation ở Spring Framework **7.0** và kế hoạch annotation Java `@Deprecated` ở **7.1**. Bài học tập trung vào **hệ thống đang sử dụng RestTemplate**: thời điểm nên giữ, những khả năng tái sử dụng, hai bước chuyển sang RestClient, tương đương phương thức gọi, khác biệt trong lỗi/status, kiểm thử hồi quy, timeouts, connection pool, auth và kế hoạch rollout/rollback. Không đề xuất chuyển sang WebClient chỉ vì RestTemplate cũ: cả RestTemplate và RestClient đều là HTTP client **đồng bộ**.

## 1. Nắm đúng thông báo “deprecated từ Spring 7”

### 1.1. Mốc phiên bản cần phân biệt

| Phiên bản | Trạng thái RestTemplate | Việc nên làm |
| --- | --- | --- |
| Spring Framework 6.0 | RestTemplate tiếp tục được hỗ trợ; chưa có RestClient | Duy trì, bảo đảm request factory/timeouts |
| Spring Framework 6.1 | RestClient xuất hiện, API fluent đồng bộ | Có thể bắt đầu migration song song |
| Spring Framework 6.2 / Spring Boot 3.5 | RestTemplate còn hoạt động; RestClient là API ưu tiên cho code sync mới | Chuyển dần module có kiểm thử |
| Spring Framework 7.0 | **Deprecated theo tài liệu/reference và release notes**; khuyến nghị RestClient | Tránh viết RestTemplate mới; lập roadmap migration |
| Spring Framework 7.1 (dự kiến phát hành sau tháng 10/2026) | **Kế hoạch** gắn Java `@Deprecated` cho RestTemplate và các loại liên quan | Xác minh Javadoc của phiên bản 7.1 thực sự dùng trước khi dựa vào compiler warning |
| Spring Framework 8.0 (chưa định ngày) | Spring công bố **kế hoạch loại bỏ** RestTemplate | Không chờ đến lúc nâng phiên bản mới bắt đầu chuyển |

Spring Team công bố kế hoạch vào ngày 30/09/2025. Release notes Spring 7.0 làm rõ: deprecation đã ở tài liệu 7.0, còn Java annotation `@Deprecated` được dự kiến ở 7.1; removal dự kiến cho 8.0. **Đừng ghi “RestTemplate bị xóa từ Spring 7” hoặc “Spring 7.0 bắt buộc compiler warning”**. Đây là hai khẳng định sai.

Theo kế hoạch công khai của Spring, thời điểm chính xác cho 7.1/8.0 có thể thay đổi. Đừng suy từ tài liệu SNAPSHOT rằng một phiên bản chưa phát hành đã là release ổn định.

Nguồn chính thức:
- [Spring Framework 7.0 Release Notes — Deprecations](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes).
- [The state of HTTP clients in Spring — Spring Blog, 30/09/2025](https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring/).
- [Spring Framework 6.2 — REST Clients](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html).

### 1.2. Deprecated không có nghĩa phải rewrite ngay

Với hệ thống đang chạy, câu hỏi đúng không phải “xóa RestTemplate hôm nay?”, mà là:
- Có bao nhiêu call sites và khả năng test?
- Đang dùng `ClientHttpRequestFactory`/Apache HttpClient/JDK client nào?
- Có custom `ResponseErrorHandler`, `ClientHttpRequestInterceptor`, `HttpMessageConverter`, `UriTemplateHandler` hoặc certificate/pool config?
- Có lịch nâng Spring Framework 7/8 hay thay đổi JDK?
- Business endpoints có retry, 404 semantics, timeout và auth riêng?
- Có thể chuyển từng service/client bean với compatibility tests mà không thay hành vi bên ngoài không?

Một RestTemplate đã được cấu hình và giám sát tốt **có thể tiếp tục được sử dụng khi migration từng bước**. Nhưng tạo thêm mã RestTemplate mới trong hệ Spring 6.2+ làm tăng số call sites phải chuyển trong tương lai. Với HTTP đồng bộ **mới**, nên chọn RestClient.

## 2. Vì sao RestClient thay RestTemplate?

RestTemplate là API kiểu template-method từ Spring 3.0:

```java
Item item = restTemplate.getForObject(
        "/items/{id}", Item.class, 7L);

ResponseEntity<Item> response = restTemplate.getForEntity(
        "/items/{id}", Item.class, 7L);
```

RestClient có API fluent từ Spring Framework 6.1:

```java
Item item = restClient.get()
        .uri("/items/{id}", 7L)
        .retrieve()
        .body(Item.class);

ResponseEntity<Item> response = restClient.get()
        .uri("/items/{id}", 7L)
        .retrieve()
        .toEntity(Item.class);
```

Các đoạn trên là **snippets**, cần khai báo clients, `Item` và base URL. **Cả hai vẫn synchronous/blocking ở caller** khi thực hiện exchange. Lợi ích chính của RestClient là builder fluent, cấu hình status handler theo request và dễ mở rộng API ở tầng cao; **không tự chuyển request thành reactive I/O**.

### 2.1. Cơ sở hạ tầng chung giúp migration nhỏ

RestTemplate và RestClient cùng sử dụng:
- `ClientHttpRequestFactory` (chọn HTTP transport, timeout/connection behavior);
- `HttpMessageConverter<?>` (JSON, String, byte[], form, Resource);
- `ClientHttpRequestInterceptor`;
- `ClientHttpRequestInitializer`;
- `UriBuilderFactory`/URI template handling;
- cơ chế error mapping, với adapter hỗ trợ tái sử dụng `ResponseErrorHandler`.

Vì vậy Spring cung cấp `RestClient.create(existingRestTemplate)` và `RestClient.builder(existingRestTemplate)` để khởi tạo client **dựa trên cấu hình đã có**.

```java
RestClient newClient = RestClient.create(restTemplate);
// Hoặc nếu muốn tùy chỉnh thêm trước khi build:
RestClient customized = RestClient.builder(restTemplate)
        .defaultHeader("Accept", "application/json")
        .build();
```

Từ Spring Framework 6.1, overload này lấy cấu hình cũ như request factory, converters, interceptors, initializers, URI builder factory và error handler. Đây là **lợi thế migration lớn nhất**: bạn có thể chuyển **cách gọi API** trước, rồi cải tổ **cách dựng client** sau. Không nhất thiết thay pool/certificates/cookie/auth cùng lúc.

### 2.2. Cái gì không được hứa tự tương đương?

Tái sử dụng cấu hình không miễn phí cho mọi behavior:
- Status/error handling với `RestClient.retrieve()` khác `RestClient.exchange(...)`; cần xem bên dưới.
- Một request converter hoặc custom interceptor có thể phụ thuộc header order/body buffering.
- URI template vs URI builder, percent encoding, slash, query params có thể đổi nếu bạn thay base URL/builder trong khi migrate.
- Method GET/HEAD/POST và body extraction phải được thay bằng terminal operation chính xác.
- Multipart/form payload, download dạng streaming, ETag, content-length và redirects cần tests riêng.
- Cấu hình shared mutable ở `RestTemplate` không nên được sửa khi đang phục vụ requests; RestClient.create(template) không biến việc mutate này thành thread-safe.
- TLS/proxy pool/client library phải được kiểm tra ở environment thật, không chỉ mock.
- Không mặc định mọi custom `ResponseErrorHandler` đều tương đương với request-specific `onStatus` khi bạn thay cấu hình.

**Mục tiêu:** giữ wire contract với downstream ổn định, không “đổi API rồi tiện tay rewrite luôn retries/errors/auth”.

## 3. Kế hoạch migration hai giai đoạn

### Giai đoạn A — giữ hạ tầng, chuyển call sites

1. Đánh dấu các nhóm RestTemplate theo downstream: catalog, pricing, identity, payments; ghi nhận base URL, interceptors, timeout, error handler.
2. Chọn một nhóm nhỏ có unit/integration tests tốt.
3. Tạo RestClient từ **bean RestTemplate cũ** bằng `RestClient.create(template)` hoặc `builder(template)`.
4. Thay `getForObject/getForEntity/exchange/postForEntity/...` bằng chain RestClient tương đương, **không** thay request factory/pool/auth cùng lúc.
5. So sánh URI, method, headers, request body, JSON mapping, status/exceptions và latency với tests.
6. Đưa lên staging, canary, đo 4xx/5xx, pool wait, timeout và correlation IDs.
7. Rollback bằng route hoặc cấu hình chọn implementation nếu có regression.

```text
Legacy endpoints                  New endpoints
       |                                 |
 RestTemplate ----------------> RestClient.create(template)
       |                         (config lấy từ template)
       +------------+--------------------+
                    |
       same request factory / interceptors /
       URI builder / message converters / error handler
                    |
                HTTP transport
                    |
               Downstream API
```

Trên giai đoạn này, có thể giữ song song RestTemplate và RestClient đã dựng. Đừng xóa legacy bean trước khi chắc chắn không còn consumer. Về tài nguyên, cần kiểm tra chúng có chia sẻ request factory/pool như mong muốn, không tạo nhiều HTTP clients độc lập gây tăng connections.

### Giai đoạn B — thay cách cấu hình khi call sites đã ổn

Khi toàn bộ service methods đã dùng RestClient:
1. Dựng `RestClient.Builder` riêng với `baseUrl`, `requestFactory`, `messageConverters`, `requestInterceptors` và `defaultStatusHandler` theo nhu cầu.
2. Dùng config của Spring Boot `RestClient.Builder` nếu phù hợp để không mất customization/observability.
3. Di chuyển custom error handler sang status-handler fluent API đã test.
4. Xóa RestTemplate bean khi không còn consumers; gỡ adapter chỉ phục vụ migration.
5. Rà soát dependency và Java/Spring version trước khi nâng Spring Framework 7.x/8.x.
6. Giữ các tests đã tạo làm **regression contract**, không chỉ code compilation.

```java
RestClient client = RestClient.builder()
        .baseUrl("https://catalog.example.test")
        .requestFactory(configuredRequestFactory)
        .requestInterceptor(configuredTracingInterceptor)
        .defaultStatusHandler(
                HttpStatusCode::is5xxServerError,
                (request, response) -> {
                    throw new IllegalStateException("Remote service failure");
                })
        .build();
```

**Snippet** minh họa API builder, cần khai báo request factory và interceptor. Production nên dùng typed exception và không nuốt upstream status/body cần monitoring. Nếu Spring Boot cung cấp builder được customizer cấu hình, tránh tạo `RestClient.builder()` tùy tiện và vô tình bỏ qua SSL/observability từ Boot.

## 4. Phương thức tương đương: RestTemplate → RestClient

| RestTemplate cũ | RestClient mới (trong Spring 6.2) | Lưu ý |
| --- | --- | --- |
| `getForObject(url, Dto.class, id)` | `get().uri(url,id).retrieve().body(Dto.class)` | DTO có thể null nếu không có body |
| `getForEntity(url, Dto.class, id)` | `get().uri(url,id).retrieve().toEntity(Dto.class)` | Lấy status/headers |
| `postForObject(url, request, Dto.class)` | `post().uri(url).body(request).retrieve().body(Dto.class)` | Kiểm tra Content-Type/JSON |
| `postForEntity(url, request, Dto.class)` | `post().uri(url).body(request).retrieve().toEntity(Dto.class)` | Đọc Location/status 201 |
| `put(url, request, id)` | `put().uri(url,id).body(request).retrieve().toBodilessEntity()` | Trả ResponseEntity<Void>, method caller có thể bỏ result |
| `delete(url, id)` | `delete().uri(url,id).retrieve().toBodilessEntity()` | Không đòi body trên 204 |
| `exchange(url,GET,...,ParameterizedTypeReference)` | `get().uri(url).retrieve().toEntity(ParameterizedTypeReference)` | Giữ generic type |
| `exchange(url,PUT/POST,HttpEntity,...)` | `method(HttpMethod.X).uri(...).headers(...).body(...).retrieve()...` | Headers/body phải tách đúng |
| `headForHeaders(url,id)` | `head().uri(url,id).retrieve().toBodilessEntity().getHeaders()` | HEAD có headers, không body |
| `execute(url, method, callback, extractor)` | `exchange((request,response)->...)` | **Tự** xử lý status/body/cleanup đúng |
| `setErrorHandler(handler)` | `RestClient.builder(template)` giữ handler; về sau `defaultStatusHandler(...)` | Status override/order cần test |

Các biểu thức trong bảng là **mapping patterns**, không phải một thay thế text-to-text có thể áp dụng vô điều kiện cho mọi overload. Một số methods trả kết quả khác kiểu (ví dụ delete/put cũ là `void`, chain RestClient trả ResponseEntity<Void>); thay call sites sao cho không ảnh hưởng service API contract.

### 4.1. GET với URI template

**Trước:**

```java
ItemView item = restTemplate.getForObject(
        "/items/{id}", ItemView.class, itemId);
```

**Sau:**

```java
ItemView item = restClient.get()
        .uri("/items/{id}", itemId)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(ItemView.class);
```

`.uri("/items/{id}", ...)` dùng URI template, không tự ghép string bằng input có thể chứa slash/space. Nếu path variable có constraint numeric, validate từ domain layer. URI encoding giữa hai API/cấu hình URI factory cần regression test; không cho client cung cấp absolute target URI tùy ý vì SSRF.

### 4.2. Lấy status và headers

**Trước:**

```java
ResponseEntity<ItemView> response =
        restTemplate.getForEntity("/items/{id}", ItemView.class, id);
```

**Sau:**

```java
ResponseEntity<ItemView> response = restClient.get()
        .uri("/items/{id}", id)
        .retrieve()
        .toEntity(ItemView.class);
```

Dùng `ResponseEntity` khi cần `ETag`, `Last-Modified`, `Location`, status 204/206 hoặc headers nghiệp vụ. Với `retrieve()`, 4xx/5xx mặc định ném `RestClientResponseException` trừ status handlers đã custom.

### 4.3. POST và Location

```java
record CreateItem(String name) {}

ResponseEntity<ItemView> created = restClient.post()
        .uri("/items")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(new CreateItem("Spring"))
        .retrieve()
        .toEntity(ItemView.class);

URI location = created.getHeaders().getLocation();
```

Mã trên là snippet, cần RestClient bean, DTO và imports. Nếu server tạo 201 nhưng không trả body, `created.getBody()` có thể null; đừng ném NPE. Khi Location tương đối, không tin raw remote header để redirect browser ngoài trust boundary.

### 4.4. PUT, PATCH và DELETE

```java
ResponseEntity<Void> updated = restClient.put()
        .uri("/items/{id}", id)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateItem("Renamed"))
        .retrieve()
        .toBodilessEntity();

ResponseEntity<Void> deleted = restClient.delete()
        .uri("/items/{id}", id)
        .retrieve()
        .toBodilessEntity();
```

Snippet cần DTO. Nếu server trả 204, response không body, đó là expected; đừng cố `body(ItemView.class)` cho 204. PATCH cần request factory/underlying HTTP transport thực sự hỗ trợ method PATCH, không chỉ code compile; test lại nếu legacy RestTemplate đã dùng custom Apache HttpClient factory.

### 4.5. Danh sách generic

```java
List<ItemView> items = restClient.get()
        .uri("/items")
        .retrieve()
        .body(new ParameterizedTypeReference<List<ItemView>>() {});
```

Không dùng `List.class` rồi cast list elements thành ItemView — JSON decoder có thể trả Map vì mất generic type. Chính xác tương đương với `RestTemplate.exchange(..., new ParameterizedTypeReference<List<ItemView>>() {})`.

### 4.6. Query params, headers và authentication

```java
ItemView item = restClient.get()
        .uri(builder -> builder
                .path("/items/{id}")
                .queryParam("lang", "vi")
                .build(id))
        .headers(headers -> {
            headers.setBearerAuth(validAccessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        })
        .retrieve()
        .body(ItemView.class);
```

Snippet cần `validAccessToken`. Client credentials/service identity phải lấy từ OAuth2/trusted provider; không tự chuyển toàn bộ user bearer token sang bất kỳ upstream nào. Tránh đưa token vào URL/query hoặc exception logs. Khi migrate interceptors, kiểm tra có vô tình đặt hai Authorization headers hay lặp tracing headers.

## 5. Điểm dễ sai: retrieve() khác exchange()

### 5.1. RestClient retrieve()

```java
ItemView item = restClient.get()
        .uri("/items/{id}", 7)
        .retrieve()
        .body(ItemView.class);
```

`retrieve()` chọn high-level API; phép thực hiện exchange thường xảy ra ở terminal `body(...)`, `toEntity(...)`, `toBodilessEntity()`. Với status 4xx/5xx, mặc định RestClient ném exception. Có thể custom trên request:

```java
ItemView item = restClient.get()
        .uri("/items/{id}", 7)
        .retrieve()
        .onStatus(status -> status.value() == 404,
                (request, response) -> {
                    throw new ItemMissingException("not found");
                })
        .body(ItemView.class);
```

Đây là snippet; `ItemMissingException` phải được định nghĩa. Status predicate chỉ match 404; mặc định 500 vẫn thành server exception nếu không thay các default handlers. Không ném `RuntimeException` tùy tiện làm mất ngữ nghĩa status nếu API cần phân biệt auth, 404 và transient errors.

### 5.2. RestClient exchange() không dùng retrieve status handlers

```java
ResponseEntity<String> raw = restClient.get()
        .uri("/items/{id}", 7)
        .exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            if (status.isError()) {
                throw new IllegalStateException("Upstream HTTP error");
            }
            String body = new String(
                    response.getBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            return ResponseEntity.status(status).body(body);
        });
```

Snippet có imports `StandardCharsets`, `HttpStatusCode` và `ResponseEntity`; ví dụ chỉ đọc body nhỏ, không dùng `readAllBytes()` với response lớn/stream vô hạn. **Quan trọng:** `exchange(...)` **không áp dụng** `onStatus` và `defaultStatusHandler` dành cho `retrieve()`, vì người gọi phải kiểm soát response. Nếu code RestTemplate cũ dùng `execute(...,ResponseExtractor)` để stream file, migration bằng `RestClient.exchange(...)` cần kiểm thử quản lý input stream và tài nguyên trước khi trả khỏi callback.

### 5.3. RestTemplate error handler được carry over

```java
RestTemplate template = new RestTemplate();
// Đã cấu hình template.setErrorHandler(customHandler) nếu cần.
RestClient restClient = RestClient.create(template);
```

Theo Javadoc Spring, overload này **dựa trên** template cũ gồm error handler. Khi tạo RestClient mới từ template đã có `ResponseErrorHandler`, hãy kiểm thử với 400, 404, 429, 500:
- Error handler có throw đúng exception subtype không?
- Có đọc response body làm mất dữ liệu để decoder dùng tiếp không?
- Behavior có khác giữa `retrieve` và `exchange`?
- Handler có lộ PII hay token không?

Đừng đồng thời đăng ký default status handlers mới và cũ mà không kiểm tra order: policy có thể bị override hoặc xử lý hai lần không mong muốn.

### 5.4. Mất dữ liệu khi body rỗng

`RestTemplate.getForObject(...)` và `RestClient.retrieve().body(...)` có thể trả null nếu 2xx không body. `toEntity(Dto.class)` giúp lấy status/headers kể cả body null. Nếu service contract yêu cầu item phải có, validate null rõ trước khi dereference và quyết định exception nghiệp vụ.

## 6. Chiến lược xử lý HTTP status khi chuyển

| HTTP / tình huống | Hành vi nên kiểm tra |
| --- | --- |
| 200 JSON object | Body decode đúng DTO |
| 201 Created | Status, Location, body tùy downstream |
| 202 Accepted | Có thể là tác vụ async phía server; không coi 202 là completed |
| 204 No Content | Không dereference null body |
| 206 Partial Content | Range/content headers và body streaming semantics |
| 301/302/307/308 | Redirect được HTTP transport xử lý thế nào, có giữ method/token không? |
| 304 Not Modified | ETag/cache logic, không expect body |
| 400 Validation | Trả lỗi domain phù hợp, không nuốt lỗi |
| 401/403 | Auth/permission; không retry mù |
| 404 | Resource thiếu hay upstream misconfiguration tùy business |
| 409/412 | Conflict/precondition failure, optimistic lock |
| 429 | Rate-limit/Retry-After, có giới hạn retry |
| 500/502/503/504 | Server/downstream failure; logging, timeout, circuit breaker |
| Timeout/DNS/TLS failure | I/O exception, không nhất thiết có HTTP status |
| JSON malformed | Message conversion exception, khác HTTP status |
| Response body quá lớn | Giới hạn read/buffering, test memory/OOM |

**Không “catch mọi RestClientException rồi trả 404”.** Exception transport khác status error, và upstream 404 không mặc định bằng resource 404 của API facade.

### 6.1. RestClientException là umbrella type

Một số nhóm thường gặp:
- `RestClientResponseException` và subclasses: server có HTTP response lỗi, exception giữ status/headers/body nếu được capture.
- `ResourceAccessException`: I/O errors, connect/read timeout, DNS/socket issues.
- `UnknownContentTypeException`/message conversion failures: không giải mã được representation.
- Custom exception từ `onStatus`/domain layer.

Từng phiên bản/library có subtype cụ thể khác. Đừng chỉ assert exact class cho mọi lỗi network trong integration tests; assert status, cause category và behavior cần thiết. Khi log, không in raw upstream body chứa personal data.

### 6.2. Retry có điều kiện

Với synchronous HTTP client, retry thông qua policy tầng service hoặc resilience layer (Spring Retry, Resilience4j…) khi đã thêm dependencies/config và xác định request idempotency. Không viết vòng `while(true)` hoặc retry mọi IOException bất kể POST đã có side effects.

Ví dụ phác thảo logic business (không phải chương trình hoàn chỉnh):

```text
attempt request
  -> success: return
  -> 400/401/403: no retry
  -> 404: theo domain contract, thường no retry
  -> 429: xét Retry-After + budget + jitter
  -> 503/timeouts: chỉ retry khi operation idempotent và còn deadline
  -> exhausted: sanitized error + metrics + tracing
```

Khi client POST payment/create order timeout, server có thể đã commit; retry vô điều kiện sẽ tạo kết quả trùng. Dùng idempotency key, lookup reconciliation hoặc saga/outbox theo nghiệp vụ.

## 7. Cấu hình request factory, timeout và pool

### 7.1. Cấu hình RestTemplate hiện hữu qua Boot

Spring Boot 3.5 có RestTemplateBuilder:

```java
@Bean
RestTemplate catalogTemplate(RestTemplateBuilder builder) {
    return builder
            .rootUri("https://catalog.example.test")
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .build();
}
```

Snippet cần import `Duration`, `RestTemplateBuilder`. API `connectTimeout/readTimeout` của Boot 3.5 sử dụng Duration. Với Spring Boot cũ, tên method có thể là `setConnectTimeout/setReadTimeout`. Trong Boot 4, kiểm tra package và autoconfiguration mới; không trộn imports Boot 3.5 và 4.

Tạo RestClient giai đoạn A:

```java
@Bean
RestClient catalogRestClient(RestTemplate catalogTemplate) {
    return RestClient.create(catalogTemplate);
}
```

Đây là cách giữ cấu hình template. Tuy nhiên **timeout cần đo thực trên HTTP transport**: connect timeout, socket/read, connection pool lease và overall deadline khác nhau. Read timeout không tự bao DNS/TLS/queue/retry. Mọi trường hợp timeouts cần test với mock HTTP server đáp ứng chậm.

### 7.2. Cấu hình RestClient trực tiếp trong giai đoạn B

```java
@Bean
RestClient catalogClient(RestClient.Builder builder,
                         ClientHttpRequestFactory factory) {
    return builder
            .baseUrl("https://catalog.example.test")
            .requestFactory(factory)
            .build();
}
```

Snippet cần provider bean `factory` do app chọn/config theo transport. Nếu cần Apache HttpClient, hãy kiểm tra dependencies/classpath và `HttpComponentsClientHttpRequestFactory` thực sự được dùng; không mặc định mọi factory có connection pooling mạnh. Timeout/certificates/proxy/compression cấu hình ở **factory/underlying HTTP client** phù hợp.

### 7.3. Những timeout thường bị nhầm

- DNS: resolve hostname; có thể có policy riêng của OS/network/library.
- Connect: mở TCP connection.
- TLS handshake: xác thực certificate và hoàn thành TLS.
- Connection pool acquire: chờ lấy connection rảnh.
- Response/read: đợi server data và đọc bytes.
- Overall deadline: tổng thời gian, gồm redirects/retries/backoff.
- Idle timeout: connection keepalive/pool eviction, không cùng request deadline.

**Không đặt mỗi “3 giây” ở một option rồi kết luận mọi request chắc chắn kết thúc dưới 3 giây.** Phải đo với slow downstream, connection exhaustion, DNS/TLS failure, large responses, retries.

### 7.4. Pool và thread concurrency

RestTemplate/RestClient vẫn chặn caller trong một synchronous exchange. Nếu MVC application có 200 worker threads cùng gọi downstream chậm, có thể giữ 200 threads dù HTTP pool nhỏ hơn. Một pool 20 connections và 200 callers có thể làm 180 callers chờ acquire hoặc lỗi timeout. Cần capacity planning theo:
- inbound RPS/concurrency;
- downstream latency p95/p99;
- max connections, per-route limits và pending queue;
- max servlet threads và scheduled workers;
- rate limiter/bulkhead;
- retries có thể nhân tải.

REST API fluent không tự cấp rate limiting hay non-blocking resource control.

## 8. Message converters, JSON và generic types

RestTemplate và RestClient dùng `HttpMessageConverter`. Nhưng migration có thể làm lộ differences nếu bạn bỏ cấu hình converter cũ.

### 8.1. Converter tùy biến

```java
RestClient client = RestClient.builder()
        .messageConverters(converters -> {
            // Đăng ký custom converter nếu business cần.
            // Tránh xóa converter mặc định vô ý.
        })
        .build();
```

Đây là snippet, không phải cấu hình JSON production. Trong Spring 7, một số APIs converter configuration thay đổi/deprecate; code khóa Spring 6.2 phải đối chiếu Javadoc tương ứng.

### 8.2. Kiểu generic

**Trước:**

```java
ResponseEntity<List<ItemView>> response =
        restTemplate.exchange(
                "/items", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ItemView>>() {});
```

**Sau:**

```java
List<ItemView> items = restClient.get()
        .uri("/items")
        .retrieve()
        .body(new ParameterizedTypeReference<List<ItemView>>() {});
```

Đây là snippets, cần ItemView DTO và imports. `List.class` chỉ giữ kiểu thô; JSON objects có thể trở thành maps, rồi gây ClassCastException khi dùng như DTO. Với generic wrappers `PageEnvelope<ItemView>`, dùng cùng ParameterizedTypeReference.

### 8.3. Multipart, form và download

Không đơn giản thay `RestTemplate.execute(..., ResponseExtractor)` đọc file lớn bằng `RestClient.retrieve().body(byte[].class)`: cách mới có thể gom toàn response vào heap. Với download, đánh giá `RestClient.exchange(...)` xử lý stream trong callback và đóng/cleanup đúng; tests large payload và client disconnect cần riêng.

Nếu legacy client gửi multipart form (files, strings, repeated keys), so request wire format:
- multipart boundary, Content-Disposition, filename escaping;
- Content-Type của từng part;
- Content-Length vs chunked transfer;
- encoded form parameters;
- request buffering/streaming và memory;
- HTTP method PATCH/PUT support của request factory.

Một test JSON 200 không thay thế tests multipart.

## 9. Security, headers và URI encoding

### 9.1. Interceptor có thể tái sử dụng, nhưng quyền truy cập vẫn phải kiểm tra

```java
RestTemplate template = new RestTemplate();
template.getInterceptors().add((request, body, execution) -> {
    request.getHeaders().set("X-Service-Name", "catalog-reader");
    return execution.execute(request, body);
});

RestClient client = RestClient.create(template);
```

Snippet minh họa interceptor chung; header `X-Service-Name` không phải authentication credential và client có thể tự spoof header nếu inbound proxy không kiểm soát. Không dùng nó như bằng chứng quyền. Với auth thật, dùng OAuth2/token provider/signed requests phù hợp và bảo đảm không gửi bearer token sang host lạ.

### 9.2. Không tự ghép URL từ input

```java
// Nguy cơ sai encoding và SSRF nếu userInput là URL tùy ý:
String unsafe = baseUrl + "/items/" + userInput;
```

URI template expansion:

```java
client.get()
        .uri("/items/{id}", id)
        .retrieve()
        .body(ItemView.class);
```

Nhưng URI encoding **không tự chống SSRF** khi vẫn cho người dùng nhập absolute `http://internal.host/...`. Với dynamic URLs, dùng host/scheme/port allowlist, network egress restrictions, redirect restrictions và DNS rebinding defenses theo threat model.

### 9.3. Không làm mất security/observability khi chuyển sang builder mới

Nếu `RestTemplate` cũ có `ClientHttpRequestInterceptor` để thêm:
- Authorization từ service credential provider;
- traceparent/request ID;
- tenant context/locale;
- metrics/timing;
- mTLS client certificate qua request factory;

thì cách dựng RestClient mới **phải** giữ tương đương. Với Spring Security/OAuth2, token context propagation có thể phụ thuộc thread/scheduler; đừng lấy JWT từ ThreadLocal không thuộc request. Test rằng headers đã được đặt một lần, không bị nhân đôi hoặc lộ sang redirect khác origin.

### 9.4. ResponseEntity và cache headers

Một số endpoints trả 304 với ETag/If-None-Match. Khi chuyển client, phải giữ:
- request conditional headers;
- status 304 và không có body;
- ETag, Last-Modified, Cache-Control response headers;
- xử lý cache ở business layer.

Đừng mã hóa logic như “body null = 404” nếu 204/304 là kết quả hợp lệ theo HTTP.

## 10. Ví dụ chạy được: hai clients, một cấu hình

Demo Spring Boot 3.5/Java 17 chạy **Spring MVC server** và có mock supplier trong chính application:
- `GET /remote/items/7` là upstream demo;
- `GET /bridge/old/7` gọi nó bằng RestTemplate;
- `GET /bridge/new/7` gọi bằng RestClient được tạo từ RestTemplate đã cấu hình;
- `GET /bridge/new-meta/7` cho thấy status/headers với ResponseEntity;
- `GET /bridge/new-list` dùng generic type;
- `POST /bridge/old` và `POST /bridge/new` tạo item qua hai clients.

**Lưu ý demo:** sync HTTP call từ MVC handler quay lại cùng server ở localhost:8080 chỉ để dễ quan sát, không phải kiến trúc production; trong load lớn kiểu self-call này có thể làm cạn servlet worker threads. Trong thực tế client nên gọi upstream service độc lập qua trusted URL và phải có pool/deadline cụ thể.

### 10.1. Project Maven và dependency

Từ [Spring Initializr](https://start.spring.io/) tạo Maven project:
- Spring Boot **3.5.x**;
- Java **17**;
- dependency **Spring Web**.

POM fragment (parent, plugins và dependency management do Initializr tạo):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Không cần spring-boot-starter-webflux cho migration **đồng bộ** này.

### 10.2. Java source đầy đủ

Lưu toàn bộ nội dung vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.net.URI;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    RestTemplate legacyTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri("http://localhost:8080")
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Bean
    RestClient migratedClient(RestTemplate legacyTemplate) {
        // Giai đoạn A: kế thừa cấu hình HTTP hiện hữu.
        return RestClient.create(legacyTemplate);
    }
}

record Item(long id, String name) {
}

record CreateItem(String name) {
}

record ApiError(String code, int upstreamStatus) {
}

// Server giả lập một upstream độc lập (chỉ dùng trong demo).
@RestController
@RequestMapping("/remote/items")
class RemoteItemController {

    private final AtomicLong nextId = new AtomicLong(7);
    private final ConcurrentMap<Long, Item> items =
            new ConcurrentHashMap<>();

    RemoteItemController() {
        items.put(7L, new Item(7L, "Spring Framework"));
    }

    @GetMapping("/{id}")
    ResponseEntity<Item> get(@PathVariable("id") long id) {
        Item item = items.get(id);
        return item == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(item);
    }

    @GetMapping
    List<Item> list() {
        return items.values().stream()
                .sorted(Comparator.comparingLong(Item::id))
                .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Item> create(@RequestBody CreateItem command) {
        if (command.name() == null || command.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        long id = nextId.incrementAndGet();
        Item item = new Item(id, command.name().trim());
        items.put(id, item);

        return ResponseEntity
                .created(URI.create("/remote/items/" + id))
                .body(item);
    }
}

// Bước chuyển từ call sites cũ sang fluent RestClient.
@RestController
@RequestMapping("/bridge")
class BridgeController {

    private final RestTemplate oldClient;
    private final RestClient newClient;

    BridgeController(RestTemplate legacyTemplate,
                     RestClient migratedClient) {
        this.oldClient = legacyTemplate;
        this.newClient = migratedClient;
    }

    @GetMapping("/old/{id}")
    Item oldGet(@PathVariable("id") long id) {
        return oldClient.getForObject(
                "/remote/items/{id}", Item.class, id);
    }

    @GetMapping("/new/{id}")
    Item newGet(@PathVariable("id") long id) {
        return newClient.get()
                .uri("/remote/items/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Item.class);
    }

    @GetMapping("/new-meta/{id}")
    ResponseEntity<Item> newGetWithHeaders(
            @PathVariable("id") long id) {
        return newClient.get()
                .uri("/remote/items/{id}", id)
                .retrieve()
                .toEntity(Item.class);
    }

    @GetMapping("/new-list")
    List<Item> newList() {
        return newClient.get()
                .uri("/remote/items")
                .retrieve()
                .body(new ParameterizedTypeReference<List<Item>>() {});
    }

    @PostMapping(path = "/old",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Item> oldCreate(
            @RequestBody CreateItem input) {
        return oldClient.postForEntity(
                "/remote/items", input, Item.class);
    }

    @PostMapping(path = "/new",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Item> newCreate(
            @RequestBody CreateItem input) {
        return newClient.post()
                .uri("/remote/items")
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .retrieve()
                .toEntity(Item.class);
    }
}

@RestControllerAdvice
class ClientErrorAdvice {

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ApiError> handle(
            RestClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(
                        "UPSTREAM_HTTP_ERROR",
                        ex.getStatusCode().value()));
    }
}
```

### 10.3. Giải thích code

- Java 17 cho phép `record`; một file chỉ có một public top-level class `DemoApplication`.
- `@SpringBootApplication` quét các @RestController cùng package và tạo embedded Tomcat mặc định của Spring MVC starter.
- `legacyTemplate(RestTemplateBuilder)` là bean cấu hình **một lần** với root URL và Duration timeouts. Không thay cấu hình khi nhiều threads đang dùng nó.
- `migratedClient(RestTemplate)` dùng `RestClient.create(template)` kế thừa request factory, converters, interceptors, initializers, URI builder và error handler của template.
- `RemoteItemController` là upstream tự dựng trong cùng process. Endpoint GET/POST dùng in-memory map và AtomicLong, không có database/auth.
- `BridgeController` cho thấy hai HTTP client cùng trả domain type `Item`, chỉ khác cách gọi.
- `newGetWithHeaders` trả ResponseEntity để giữ status/headers; `newList` dùng ParameterizedTypeReference tránh raw List.
- `ClientErrorAdvice` là **demo policy**: forward upstream 404 hoặc 500 status để tiện so sánh. Trong production, upstream 500 thường nên map thành 502/503 của facade hoặc lỗi nghiệp vụ phù hợp; không chuyển mọi upstream status mù quáng.
- `RestClient` và `RestTemplate` đều synchronous; MVC worker thread chờ remote call hoàn tất. Không hứa sẽ nhanh hơn do fluent syntax.
- Các bean tạo ở startup, không phát sinh HTTP client mới cho mỗi request.
- Nếu đổi `server.port` khỏi 8080, phải đổi `rootUri("http://localhost:8080")`; đây là cấu hình demo cố định.
- Không có auth/CSRF/CORS/rate limiting/mTLS/retry/observability/persistence; không public deploy.

### 10.4. Chạy

Trong thư mục Maven project do Initializr sinh:

```bash
# Linux/macOS
./mvnw spring-boot:run

# Windows PowerShell hoặc CMD
mvnw.cmd spring-boot:run
```

### 10.5. GET qua hai clients

```bash
curl -i 'http://localhost:8080/bridge/old/7'
curl -i 'http://localhost:8080/bridge/new/7'
curl -i 'http://localhost:8080/bridge/new-meta/7'
```

Cả ba response khi hoạt động bình thường:
- Status 200;
- Content-Type JSON phù hợp;
- body tương đương:

```json
{"id":7,"name":"Spring Framework"}
```

JSON whitespace và thứ tự headers không phải business contract. `/bridge/new-meta/7` chứng minh dùng `ResponseEntity` để giữ status và response headers. Legacy RestTemplate vs RestClient chỉ khác lập trình API ở client side.

### 10.6. Generic list

```bash
curl -i 'http://localhost:8080/bridge/new-list'
```

Khi mới khởi động, JSON body có một item:

```json
[{"id":7,"name":"Spring Framework"}]
```

Sau các POST, list có thêm items theo ID tăng dần vì controller sắp xếp theo Item.id.

### 10.7. POST qua hai clients

```bash
curl -i -X POST 'http://localhost:8080/bridge/old' \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":"Legacy-created"}'

curl -i -X POST 'http://localhost:8080/bridge/new' \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":"RestClient-created"}'
```

Khi mới khởi động, POST đầu tiên tạo ID 8, POST thứ hai ID 9 nếu không có request đồng thời; mỗi response là HTTP 201 Created, header `Location: /remote/items/{id}` và JSON body `{"id":8,"name":"Legacy-created"}` hoặc ID phù hợp. Chú ý: Location trỏ **resource của remote endpoint demo**, không nhất thiết là endpoint bridge.

### 10.8. 404 qua legacy và modern

```bash
curl -i 'http://localhost:8080/bridge/old/999'
curl -i 'http://localhost:8080/bridge/new/999'
```

Upstream demo trả HTTP 404. Legacy RestTemplate và migrated RestClient sẽ nhận HTTP status error theo cơ chế mặc định; `ClientErrorAdvice` biến thành cùng response policy demo:

```json
{"code":"UPSTREAM_HTTP_ERROR","upstreamStatus":404}
```

Không dựa vào điều này để khẳng định mọi custom error handler trong app thật luôn được copy và chạy giống hệt. Cần regression test cho handler thực, đặc biệt với `RestClient.exchange` và request-specific `onStatus`.

### 10.9. Windows PowerShell

```powershell
curl.exe -i "http://localhost:8080/bridge/old/7"
curl.exe -i "http://localhost:8080/bridge/new/7"
curl.exe -i "http://localhost:8080/bridge/new-list"
curl.exe -i "http://localhost:8080/bridge/new/999"
```

Nếu shell Windows khó escape JSON quotes, tạo `request.json` UTF-8:

```json
{"name":"RestClient-created"}
```

Gửi qua:

```powershell
curl.exe -i -X POST "http://localhost:8080/bridge/new" -H "Content-Type: application/json" --data-binary "@request.json"
```

## 11. Kiểm thử migration: không chỉ assert HTTP 200

Migration thành công nghĩa là **hành vi hai clients tương đương ở ranh giới HTTP**, không chỉ cùng DTO. Cần test method, path/query encoding, headers, body bytes/JSON, 200/201/204, 404/429/5xx, TLS/proxy, connection reuse và deadlines.

### 11.1. Unit tests với MockRestServiceServer

Spring Test hỗ trợ `MockRestServiceServer.createServer(RestTemplate)` và `MockRestServiceServer.bindTo(RestClient.Builder)`. Điều này giúp mock HTTP boundary và so URI/method/JSON một cách deterministic.

Với Boot 3.5, thêm POM fragment:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Lưu `src/test/java/com/example/demo/MigrationClientTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MigrationClientTest {

    private static RestTemplate templateWithBaseUri() {
        RestTemplate template = new RestTemplate();
        template.setUriTemplateHandler(
                new DefaultUriBuilderFactory("https://catalog.example.test"));
        return template;
    }

    @Test
    void oldTemplateSendsExpectedGet() {
        RestTemplate template = templateWithBaseUri();

        MockRestServiceServer server =
                MockRestServiceServer.createServer(template);

        server.expect(requestTo(
                        "https://catalog.example.test/items/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Spring Framework\"}",
                        MediaType.APPLICATION_JSON));

        Item item = template.getForObject(
                "/items/{id}", Item.class, 7);

        assertNotNull(item);
        assertEquals(7, item.id());
        assertEquals("Spring Framework", item.name());
        server.verify();
    }

    @Test
    void newClientCopiesLegacyUriConfiguration() {
        RestTemplate template = templateWithBaseUri();

        RestClient.Builder builder = RestClient.builder(template);

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.build();

        server.expect(requestTo(
                        "https://catalog.example.test/items/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":7,\"name\":\"Spring Framework\"}",
                        MediaType.APPLICATION_JSON));

        Item item = client.get()
                .uri("/items/{id}", 7)
                .retrieve()
                .body(Item.class);

        assertNotNull(item);
        assertEquals(7, item.id());
        assertEquals("Spring Framework", item.name());
        server.verify();
    }

    @Test
    void newClientRetrieveStillThrowsOn404() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://catalog.example.test");

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient client = builder.build();

        server.expect(requestTo(
                        "https://catalog.example.test/items/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> client.get()
                        .uri("/items/{id}", 99)
                        .retrieve()
                        .body(Item.class));

        assertEquals(404, exception.getStatusCode().value());
        server.verify();
    }
}
```

Giải thích tests:
- Test 1 giữ RestTemplate API cũ và URI base.
- Test 2 tạo `RestClient.Builder` **từ template đã có cấu hình**, sau đó `bindTo(builder)` đưa mock request factory vào builder trước `build()`. Test chỉ tập trung URI/method/JSON, không thực hiện DNS/TLS.
- Test 3 bảo vệ contract 404 từ `retrieve()`; mặc định `RestClientResponseException` là base type phù hợp.
- Việc mock builder thay request factory nghĩa là **không chứng minh** connection pool, SSL, timeout của factory cũ; cần integration test riêng cho những behavior đó.

### 11.2. Additional test matrix

| Hành vi | Kiểm tra cụ thể |
| --- | --- |
| JSON GET 200 | DTO fields, UTF-8, Content-Type |
| List generic | ParameterizedTypeReference giữ type |
| POST 201 | Location, request Content-Type, response status/body |
| Empty 204 | Không NPE, code xử lý body null |
| 304/ETag | Headers, conditional GET nếu sử dụng |
| 400/401/403 | Không retry và không leak raw upstream body |
| 404 | Policy: resource missing vs dependency misconfiguration |
| 409/412 | Conflict/ETag behavior |
| 429/Retry-After | Rate limit/backoff, không retry storm |
| 500/503/504 | Typed service errors, metrics, deadlines |
| Connect/read timeout | Thử với slow HTTP server, kiểm tra không giữ worker vô hạn |
| TLS/proxy | Certificate/host validation, ALPN, proxies theo prod |
| Multipart/PATCH | Request factory hỗ trợ method, binary body đúng |
| Authorization/tracing | Không mất/nhân đôi interceptor headers |
| Query/path encoding | Có space, Unicode, slash, dấu %, repeated query |
| Concurrency | Thread pool, connection reuse, saturation |
| Redirect | Có forward Authorization ngoài trust boundary không? |

### 11.3. Chạy tests

```bash
# Linux / macOS
./mvnw test

# Windows
mvnw.cmd test
```

**Không coi việc README có source/test code là kết quả thực thi.** Để xác nhận runnable trong dự án thật, hãy chạy `./mvnw test` và các curl steps trong môi trường Spring Boot 3.5/JDK17 phù hợp. Nếu có custom beans/security/autoconfiguration, cần điều chỉnh tests theo chính sách ứng dụng.

## 12. Rollout, monitoring và rollback từng bước

### 12.1. Inventory toàn bộ RestTemplate consumers

Ghi bảng theo service/call site (không cần tạo spreadsheet mới nếu đang ở một dự án):

| Mục tiêu kiểm kê | Điều cần ghi |
| --- | --- |
| Tên bean và consumer | Service dùng RestTemplate nào? |
| Downstream logical name | Catalog, payments, identity, shipping |
| Method/path | GET, POST, PATCH, multipart/stream |
| Timeout/request factory | Connect/read/pool acquisition limits |
| Interceptors | Auth, tracing, tenancy, metrics, signatures |
| Converters | JSON/XML/custom, multipart |
| Error handler | Class, status mapping, body behavior |
| Resilience | Retry, circuit breaker, fallback, idempotency |
| Bảo mật | Credentials, redirect/SSRF restrictions |
| Test coverage | 200/4xx/5xx, timeouts, encoding |
| Migration status | Legacy, dual-client, converted, validated |

### 12.2. Canary theo client bean hoặc service

Có thể giữ hai implementations cùng interface:

```java
interface CatalogGateway {
    ItemView load(long id);
}

class LegacyCatalogGateway implements CatalogGateway {
    // RestTemplate getForObject...
}

class MigratedCatalogGateway implements CatalogGateway {
    // RestClient get().retrieve().body()...
}
```

Đây là design snippet, không phải full source. Chọn implementation bằng Spring Profiles/feature flag server-side và đo canary. Cần chú ý **không gửi song song hai POST thực tế** chỉ để shadow-compare, vì side effects có thể bị nhân đôi. Nếu shadow GET read-only, vẫn phải kiểm tra quota, auth/PII và cost.

### 12.3. Metrics trước và sau

So sánh trước/sau migration:
- error rate 4xx/5xx và I/O timeouts;
- latency p50/p95/p99 ở từng downstream;
- connection pool active/idle/pending;
- JVM thread states, servlet pool usage, heap/gc;
- request volume và retry attempts;
- URI templates/HTTP method distributions;
- response payload bytes/codec errors;
- authorization/correlation header propagation.

Không dùng raw user IDs, order IDs, email, request IDs làm high-cardinality Micrometer tags. Dùng templates và logical service names.

### 12.4. Tiêu chí rollback

Rollback nếu:
- 404/500 mapping thay đổi không theo contract;
- 429/throttling hoặc connection pool timeout tăng mạnh;
- mất/mismatch auth signature hoặc redirect sang host khác;
- POST trùng do retry;
- multipart/streaming payload sai;
- tỷ lệ JSON decode lỗi tăng;
- request timeouts không còn chặn được slow downstream;
- có data leak, SSRF hoặc security regression.

Vì giai đoạn A giữ RestTemplate cũ cùng request factory/config, rollback có thể đơn giản là chuyển call site/service implementation về RestTemplate, miễn không sửa đổi dữ liệu nghiệp vụ ngoài ý muốn.

## 13. Khi nào phải xem lại kiến trúc thay vì chỉ chuyển API?

### 13.1. Nếu dùng WebFlux event loop

Không thay RestTemplate bằng RestClient rồi gọi trực tiếp trong WebFlux handler: **RestClient vẫn blocking**. Trong ứng dụng WebFlux native, ưu tiên WebClient để compose network I/O, hoặc tạm bridge một số blocking calls sang `Schedulers.boundedElastic()` có giới hạn:

```java
Mono<ItemView> bridged = Mono.fromCallable(
                () -> restClient.get()
                        .uri("/items/{id}", id)
                        .retrieve()
                        .body(ItemView.class))
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippet reactive bridge, không biến RestClient thành non-blocking HTTP transport. Cần kiểm soát boundedElastic queue/concurrency và downstream pool. Nếu ứng dụng chủ yếu dùng JDBC/JPA và sync HTTP, Spring MVC có thể hợp lý hơn WebFlux.

### 13.2. HTTP Service Client interface-first

Spring 6+ cho phép khai báo HTTP interface:

```java
@HttpExchange("/items")
interface CatalogApi {
    @GetExchange("/{id}")
    ItemView get(@PathVariable("id") long id);
}
```

Và tạo proxy trên RestClient:

```java
CatalogApi catalog = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(CatalogApi.class);
```

Đây là snippets cần imports và RestClient bean. HTTP Service Client là **interface/proxy trên RestClient**, không phải transport mới, và method vẫn đồng bộ nếu dùng RestClientAdapter. Có ích khi nhiều endpoints cùng một remote contract, giảm boilerplate và dễ mock interface. Không bắt buộc đổi RestTemplate sang HTTP interface nếu bài toán chỉ cần một GET.

### 13.3. Với tác vụ streaming rất lớn

RestClient hỗ trợ low-level exchange để xử lý InputStream trong callback, nhưng vẫn có caller thread chờ. WebClient thích hợp hơn với event streams/NDJSON/high concurrency và reactive processing. Không ép tất cả tasks streaming sang `body(byte[].class)` vì có thể giữ toàn bộ payload trong RAM.

## 14. Các lỗi migration thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| RestClient không có trong Spring 6.0 | API bắt đầu từ 6.1 | Nâng theo compatibility plan |
| Code nói RestTemplate đã bị xóa Spring 7 | Hiểu sai deprecation | 7.0 docs deprecation; 7.1 planned @Deprecated; 8.0 planned removal |
| Cứ dùng RestTemplate mãi vì không thấy warning | Spring 7.0 chưa nhất thiết có annotation | Làm migration plan dựa trên roadmap |
| Đổi RestClient mà mất auth headers | Bỏ interceptor/customizer cũ | `RestClient.create(template)` + integration test |
| URI route khác trước | Đổi URI builder/baseUrl/encoding | So HTTP requests byte-level |
| 404/500 xử lý khác | Không giữ error handler/status policy | Test retrieve/onStatus/defaultStatusHandler |
| Dùng `exchange` và không throw cho 4xx | exchange bỏ retrieve status handlers | Tự check status và body |
| Request không được gửi | Gọi `retrieve()` nhưng không terminal method | `body/toEntity/toBodilessEntity` |
| 204 làm NullPointerException | Body rỗng là hợp lệ | Check null/ResponseEntity |
| JSON List thành List<Map> | Type erasure | ParameterizedTypeReference |
| POST bị nhân đôi khi retry | Không có idempotency | Key + reconciliation |
| Download gây OOM | Thay stream extractor bằng byte[] | Streaming exchange và giới hạn |
| PATCH hoạt động trên client này nhưng lỗi client khác | Request factory khác | Test transport-specific PATCH |
| HTTP pool bão hòa | Connection limits không theo load | Pool metrics/bulkhead/timeouts |
| Vẫn nghẽn Netty WebFlux | RestClient vẫn blocking | WebClient/native reactive |
| SSL/certificates lỗi sau migration | Chọn request factory mới bỏ cấu hình mTLS | Reuse/test TLS config |
| Redirect làm lộ token | Auto-follow cross-origin không hạn chế | Policy redirect/auth/allowlist |
| Chỉ test happy-path | Thiếu 4xx/5xx/timeout/encoding tests | Contract test matrix |
| Tạo RestClient ở mỗi request | Pool/client lifecycle lãng phí | Reuse bean |
| Cập nhật RestTemplate config khi có concurrent calls | Unsafe mutation | Startup-only configuration |
| Gọi deprecated annotation không đúng phiên bản | 7.0 docs khác 7.1 API | Đọc Javadoc/release notes đúng version |

## 15. Checklist triển khai

- [ ] Khóa Spring/JDK version; hiểu sự khác nhau giữa deprecation **docs 7.0** và **Java annotation dự kiến 7.1**.
- [ ] Phân loại tất cả RestTemplate beans và consumers, ghi rõ contracts và owners.
- [ ] Giai đoạn A dùng `RestClient.create(existingRestTemplate)`, không thay cùng lúc factory/pool/auth.
- [ ] Mapping các method cũ sang fluent API kèm terminal operation đúng.
- [ ] Có test status 201/204/404/429/5xx, ResponseEntity headers, body rỗng.
- [ ] Có test Generic `List<T>`, URI template, Unicode/encoding, query params.
- [ ] Có test custom ResponseErrorHandler và interceptors migrated.
- [ ] RestClient.retrieve vs exchange được phân biệt, không bỏ status policy.
- [ ] Connect/read/pool/TLS/deadline được cấu hình và đo với real HTTP server.
- [ ] Retry/idempotency/circuit breaker/rate limit giữ đúng business semantics.
- [ ] Security credentials, mTLS, tracing/tenant propagation không mất hoặc leak.
- [ ] Với multipart/download/PATCH, có transport-specific contract tests.
- [ ] Client beans được tái sử dụng; shared template không bị mutate khi xử lý concurrent requests.
- [ ] Có canary/rollback và metrics so sánh trước/sau với URI template tags.
- [ ] Không giả định RestClient thành async/reactive; chọn WebClient khi cần non-blocking.
- [ ] Bước B chỉ bỏ RestTemplate bean sau khi mọi consumers đã chuyển và vượt qua test.
- [ ] Nếu nâng Spring 7/Boot 4, xem lại import/versions và chính sách deprecation của bản đang dùng.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Spring Framework 7.0 có xóa RestTemplate không?

**Đáp án:** Không. Spring 7.0 deprecated RestTemplate trong tài liệu; annotation Java `@Deprecated` dự kiến ở 7.1 và removal thuộc kế hoạch 8.0 chưa định ngày.

**Câu 2.** Có thể chuyển API mà không sửa connection pool ngay không?

**Đáp án:** Có. Dùng `RestClient.create(restTemplate)` hoặc `builder(restTemplate)` để tái sử dụng request factory và các cấu hình liên quan.

**Câu 3.** RestClient có giúp MVC thread không bị block khi network chậm không?

**Đáp án:** Không. Nó là synchronous client giống RestTemplate. WebClient mới có non-blocking HTTP model.

**Câu 4.** Tại sao không nên thay `RestTemplate.execute(...,ResponseExtractor)` thành `body(byte[].class)` với file lớn?

**Đáp án:** Cách mới có thể gom toàn body vào RAM, gây memory pressure hoặc OOM; phải thiết kế streaming/cleanup đúng.

**Câu 5.** `RestClient.get().retrieve()` đã trả Item chưa?

**Đáp án:** Chưa. Cần terminal `body(Item.class)`, `toEntity` hoặc operation tương ứng.

**Câu 6.** `RestClient.exchange` có áp dụng default status handlers của retrieve không?

**Đáp án:** Không. Callback tự chịu trách nhiệm xử lý status/body.

**Câu 7.** Khi một 204 trả body null, có phải upstream lỗi không?

**Đáp án:** Không. 204 No Content không có body là hợp lệ.

**Câu 8.** Khi copy RestTemplate error handler, có được bỏ 404 regression test không?

**Đáp án:** Không. Thứ tự status handler, request-specific override và retrieve/exchange có thể khác, nên phải test.

**Câu 9.** Việc shadow-compare hai POST payment cùng lúc có an toàn không?

**Đáp án:** Không. Có thể tạo side effects hai lần; dùng contract tests/mocks, idempotency và controlled canary.

**Câu 10.** Nếu muốn HTTP client reactive trong WebFlux, chọn RestClient hay WebClient?

**Đáp án:** WebClient, vì RestClient vẫn blocking ngay cả khi API syntax hiện đại hơn.

## 17. Nguồn tham khảo chính thức

- [Spring Framework 6.2 — REST Clients](https://docs.spring.io/spring-framework/reference/6.2/integration/rest-clients.html): RestTemplate/RestClient/WebClient, converters, error handling.
- [Spring Framework — Migrating to RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#migrating-to-restclient): hướng dẫn hai giai đoạn và equivalence table.
- [Spring Framework 7.0 — Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes): deprecation ở tài liệu 7.0 và dự định @Deprecated ở 7.1.
- [Spring Blog — The state of HTTP clients in Spring (30/09/2025)](https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring/): roadmap migration và removal.
- [Spring Framework 6.2 — RestClient Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestClient.html): `create(RestTemplate)`, `builder(RestTemplate)`, retrieve/exchange.
- [Spring Framework 6.2 — RestTemplate Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestTemplate.html): template methods, thread configuration.
- [Spring Framework 6.2 — RestClient.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/client/RestClient.Builder.html): requestFactory/converters/interceptors/defaultStatusHandler.
- [Spring Boot 3.5 — Calling REST Services](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html): RestTemplateBuilder/RestClient.Builder.
- [Spring Framework — MockRestServiceServer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/test/web/client/MockRestServiceServer.html): Test RestTemplate và RestClient.Builder.
- [Spring Security — OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html): credentials, authorized HTTP calls.
- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html): non-blocking option when architecture changes.

**Kết luận:** `RestTemplate` là legacy synchronous client vẫn hoạt động trong Spring 6.2 và không bị xóa trong Spring 7.0. Spring 7.0 tuyên bố deprecation ở tài liệu, 7.1 dự kiến đánh dấu `@Deprecated`, 8.0 dự kiến loại bỏ. Chọn **RestClient** cho mã HTTP đồng bộ mới và **migrate từng service** bằng `RestClient.create(restTemplate)` để giữ hạ tầng trước, rồi mới cải tổ builder và xóa bean cũ. Migration tốt phải bảo toàn HTTP status, headers/body, error policy, timeout/pool, auth và side effects — chứ không chỉ thay syntax.


</details>

- [Quay lại đầu trang](#back-to-top)
