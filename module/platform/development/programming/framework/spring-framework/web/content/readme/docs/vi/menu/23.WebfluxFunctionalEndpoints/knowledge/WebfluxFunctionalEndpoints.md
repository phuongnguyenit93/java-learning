<a id="back-to-top"></a>

# Functional WebFlux

## Menu
- [WebFlux.fn, RouterFunction và HandlerFunction](#webflux-functional-endpoints-001)
- [ServerRequest, ServerResponse và hợp đồng bất đồng bộ](#webflux-functional-endpoints-002)
- [Định tuyến theo đường dẫn, method, header và kiểu nội dung](#webflux-functional-endpoints-003)
- [Nesting, hợp thành router và tổ chức tuyến theo tính năng](#webflux-functional-endpoints-004)
- [Đọc body bằng bodyToMono/bodyToFlux và kiểm tra dữ liệu](#webflux-functional-endpoints-005)
- [Filter cho router, xử lý lỗi và tạo phản hồi thống nhất](#webflux-functional-endpoints-006)
- [Tích hợp router bean với DispatcherHandler](#webflux-functional-endpoints-007)

## <a id="webflux-functional-endpoints-001">WebFlux.fn, RouterFunction và HandlerFunction</a>

<details>
<summary>Xem chi tiết</summary>

# WebFlux.fn, RouterFunction và HandlerFunction trong Spring WebFlux

> **Phạm vi:** Functional WebFlux (WebFlux.fn), Spring Framework 6.2.x và Spring Boot 3.5.x, Java 17+. Bài giải thích `RouterFunction<ServerResponse>`, `HandlerFunction<ServerResponse>`, `ServerRequest`, `ServerResponse`, request predicates, route composition/nesting/filtering, cách đọc và ghi body reactive, validation và HTTP errors, kiểm thử `WebTestClient`, bảo mật và hiệu năng. Có một ứng dụng Spring Boot WebFlux có thể chạy độc lập. **Đừng nhầm với WebMvc.fn:** package của bài này là `org.springframework.web.reactive.function.server` và handler trả **`Mono<ServerResponse>`**, không phải `ServerResponse` đồng bộ.

## 1. WebFlux.fn là gì?

Spring WebFlux cung cấp hai mô hình khai báo HTTP endpoints:

1. **Annotated controllers:** dùng `@RestController`, `@GetMapping`, `@PostMapping`, `@RequestBody`.
2. **Functional endpoints (WebFlux.fn):** dùng các hàm nhận `ServerRequest` và trả `Mono<ServerResponse>`, ghép thành `RouterFunction<ServerResponse>` bằng Java DSL.

Cả hai cùng sử dụng **reactive WebFlux foundation**. Chúng không phải hai HTTP servers riêng biệt, và có thể cùng tồn tại trong một ứng dụng WebFlux. Khác biệt chủ yếu nằm ở cách **khai báo mapping và handler contract**, không nằm ở chuyện một bên có thể non-blocking còn bên kia thì không.

Ví dụ đối chiếu:

```java
// Annotated WebFlux: framework đọc @GetMapping để đăng ký route.
@RestController
class AnnotatedHello {
    @GetMapping("/hello")
    Mono<String> hello() {
        return Mono.just("Hello");
    }
}
```

```java
// Functional WebFlux: route và function được khai báo trực tiếp.
// Snippet trong class @Configuration với @Bean và imports phù hợp.
@Bean
RouterFunction<ServerResponse> helloRoutes() {
    return RouterFunctions.route()
            .GET("/hello", request ->
                    ServerResponse.ok().bodyValue("Hello"))
            .build();
}
```

Nếu đặt **cả hai routes cùng `GET /hello`** vào một ứng dụng, việc chọn handler có thể phụ thuộc mapping order/registration và gây xung đột về contract. Đây là hai **phương án thay thế**, không nên copy đồng thời cho cùng URL/method mà thiếu kế hoạch.

**Ghi nhớ hai kiểu hàm quan trọng:**

```java
// HandlerFunction<ServerResponse>:
ServerRequest -> Mono<ServerResponse>

// RouterFunction<ServerResponse>:
ServerRequest -> Mono<HandlerFunction<ServerResponse>>
```

Hình thức thứ hai cho thấy một router **không xử lý body**; nó chọn handler khi request phù hợp, hoặc trả Mono rỗng nếu không match. Trong phần lớn ứng dụng, ta không tự implement interfaces mà dùng `RouterFunctions.route()`/builder và method references.

## 2. Sơ đồ request đi qua WebFlux.fn

```text
Browser / mobile / service client
             |
      HTTP request bytes
             |
       Reactor Netty
             |
     HttpHandler + WebFilter chain
             |
      DispatcherHandler
             |
   RouterFunctionMapping
             |
  RouterFunction<ServerResponse>
    |       |       |
 predicate? |       +-> route khác
    |       |
    | match v
    +--> HandlerFunctionAdapter
                  |
        HandlerFunction.handle(ServerRequest)
                  |
            Mono<ServerResponse>
                  |
        ServerResponseResultHandler
                  |
        HttpMessageWriter / codecs
                  |
         HTTP response bytes
```

Trong ứng dụng Spring Boot dùng WebFlux starter, hạ tầng WebFlux đăng ký:
- **`RouterFunctionMapping`:** tìm các bean `RouterFunction<?>` và tổng hợp routes có thứ tự.
- **`HandlerFunctionAdapter`:** gọi `HandlerFunction` đã được chọn.
- **`ServerResponseResultHandler`:** ghi `ServerResponse` vào HTTP response.

Thông thường không cần tự gọi `RouterFunctions.toHttpHandler(...)` hoặc tự khởi tạo `DispatcherHandler`; đó là khả năng dùng WebFlux.fn với adapter/server tự cấu hình hoặc trường hợp nâng cao.

### 2.1. Vì sao tính bất biến hữu ích?

`ServerRequest` và `ServerResponse` có hợp đồng giao diện theo hướng bất biến. Handler có thể trả một mô tả phản hồi mà không trực tiếp ghi socket bằng lệnh imperative:

```java
HandlerFunction<ServerResponse> handler =
        request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("Hello WebFlux.fn");
```

Framework điều phối body writers và subscription. Việc **xây dựng pipeline** không đồng nghĩa toàn bộ business work đã được thực hiện; publishers thường lazy và chỉ hoạt động khi được subscribe.

Bất biến của request/response abstraction **không** có nghĩa code bên trong handler không có side effects; database writes, email, transaction và shared state vẫn cần được kiểm soát.

## 3. API cốt lõi: bốn thành phần

| Thành phần | Vai trò | Kiểu hoặc API quan trọng |
| --- | --- | --- |
| `RouterFunction<ServerResponse>` | Ghép request vào handler | `RouterFunctions.route()`, `andOther(...)` |
| `HandlerFunction<ServerResponse>` | Xử lý request và tạo response bất đồng bộ | `Mono<ServerResponse> handle(ServerRequest)` |
| `ServerRequest` | Đọc method/URI/path variables/query/headers/cookies/body | `pathVariable`, `queryParam`, `bodyToMono`, `bodyToFlux` |
| `ServerResponse` | Xây status, headers và body reactive | `ok`, `created`, `notFound`, `bodyValue`, `body`, `build` |
| `RequestPredicate` | Điều kiện một route có được chọn | `path`, `method`, `accept`, `contentType` |
| `HandlerFilterFunction` | Bao quanh handler được match | `filter((request,next) -> ...)` |
| `WebFilter` | Lớp HTTP filter ở WebFlux infrastructure | Authentication/tracing/CORS phù hợp |

Một route không nhất thiết cần class handler. Với endpoint rất đơn giản, lambda có thể là đủ:

```java
RouterFunction<ServerResponse> ping = RouterFunctions.route()
        .GET("/ping",
                request -> ServerResponse.ok().bodyValue("pong"))
        .build();
```

Khi có nhiều chức năng, tách business logic vào một `@Component` handler class giúp kiểm thử và duy trì tốt hơn.

## 4. RouterFunction: chọn đường dẫn và method

### 4.1. Khai báo nhiều routes

```java
@Bean
RouterFunction<ServerResponse> itemRoutes(ItemHandler handler) {
    return RouterFunctions.route()
            .GET("/api/items", handler::list)
            .GET("/api/items/{id}", handler::get)
            .POST("/api/items", handler::create)
            .PUT("/api/items/{id}", handler::replace)
            .DELETE("/api/items/{id}", handler::delete)
            .build();
}
```

Đây là **snippet bean** cần `ItemHandler` và imports. Lưu ý handler methods đều có chữ ký ý tưởng:

```java
public Mono<ServerResponse> get(ServerRequest request) {
    // xử lý body/status không blocking
}
```

Các shortcut `GET`/`POST` của builder đã bao gồm HTTP method predicate, và path tương ứng.

### 4.2. Routes được xét theo thứ tự

Một khác biệt rất quan trọng: **WebFlux.fn đánh giá routes theo thứ tự được khai báo**. Nếu route trước match, nó chọn handler trước khi xét route sau. Vì vậy, route cụ thể nên đứng trước route tổng quát:

```java
return RouterFunctions.route()
        .GET("/api/items/popular", handler::popular)
        .GET("/api/items/{id}", handler::getById)
        .build();
```

Nếu đảo thứ tự và `/{id}` đứng trước, literal `popular` có thể bị coi là ID. Với annotated controllers, framework dùng mapping comparator để chọn mapping cụ thể nhất theo điều kiện; đừng mặc định WebFlux.fn tự đổi thứ tự các route của bạn cho giống annotations.

### 4.3. RequestPredicate

`RequestPredicates` có builders cho method, path, accept, content-type và các điều kiện khác:

```java
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.http.MediaType.APPLICATION_JSON;

return RouterFunctions.route()
        .GET("/api/items", accept(APPLICATION_JSON), handler::list)
        .build();
```

Hai predicates có thể được kết hợp bằng `and`, `or` hoặc `negate` khi thực sự cần logic phức tạp. Ví dụ:

```java
RequestPredicate jsonPost = RequestPredicates
        .method(HttpMethod.POST)
        .and(RequestPredicates.path("/api/items"))
        .and(RequestPredicates.contentType(MediaType.APPLICATION_JSON));
```

Đây là fragment cần `HttpMethod`/`MediaType` imports. Nếu predicate `contentType(APPLICATION_JSON)` không match, route có thể bị bỏ qua và trả 404 hay do mapping khác xử lý; **không tự mặc định** rằng predicate mismatch luôn tạo **415** giống tình huống decoder không hỗ trợ content type. Nếu API cần 415/406 chính xác, hãy kiểm thử và thiết kế error handling.

### 4.4. Nested routes và prefix

Thay vì lặp `/api/items`:

```java
return RouterFunctions.route()
        .path("/api/items", b -> b
                .GET("", handler::list)
                .GET("/{id}", handler::get)
                .POST("", handler::create))
        .build();
```

Nested route có thể chia sẻ path prefix hoặc predicate khác. Trong trường hợp nested `accept(JSON)`, ta có thể áp điều kiện chỉ cho nhóm GET mà không ảnh hưởng POST:

```java
return RouterFunctions.route()
        .path("/api/items", b -> b
                .nest(RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        get -> get
                                .GET("", handler::list)
                                .GET("/{id}", handler::get))
                .POST("", handler::create))
        .build();
```

Ví dụ trên là snippet; routes GET yêu cầu Accept phù hợp JSON, trong khi POST không nhận cùng predicate này. Filter nằm bên trong nested builder chỉ có scope đến routes trong nested group; filter trên outer builder có thể bao toàn bộ các routes được xây dựng ở outer level.

### 4.5. Ghép nhiều RouterFunction

Ngoài builder `.add(otherRoute)`, RouterFunction còn hỗ trợ kết hợp bằng `and(...)`/`andOther(...)` tùy generic type. Khi khai báo nhiều RouterFunction beans, `RouterFunctionMapping` thu thập và sắp thứ tự. Trong ứng dụng lớn cần tổ chức thứ tự có chủ đích (ví dụ `@Order`) để route fallback/catch-all không nuốt routes của module khác. Không tự xem thứ tự bean scan hay thứ tự tên class là contract routing ổn định.

### 4.6. Static resources

`RouterFunctions.resources(...)` có thể ghép route phục vụ static resources. Đừng dùng một catch-all route rất rộng trước API routes; nó có thể làm lỗi API bị trả nhầm resource/static response. Với file upload/download, cần path canonicalization, permissions, MIME và header security; phục vụ resource không đồng nghĩa cho phép client chọn đường dẫn filesystem tùy ý.

## 5. HandlerFunction: nhiệm vụ đọc và trả response

Một handler function không được annotation `@GetMapping` gắn trực tiếp lên method; RouterFunction đã làm nhiệm vụ routing:

```java
@Component
class GreetingHandler {
    Mono<ServerResponse> hello(ServerRequest request) {
        String name = request.queryParam("name").orElse("guest");
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("Hello " + name);
    }
}
```

Đây là snippet; cần imports. Khi route gọi `handler::hello`, giá trị nhận là `ServerRequest`, không phải một loạt tham số tự resolve bằng `@PathVariable`, `@RequestBody` như annotated controller method. **Handler phải lấy các dữ liệu đó từ `ServerRequest` một cách tường minh**.

### 5.1. Không return ServerResponse trực tiếp

Một trong các lỗi khi chuyển từ WebMvc.fn:

```java
// Sai với HandlerFunction của WebFlux.fn:
ServerResponse get(ServerRequest request) { ... }

// Đúng trong WebFlux.fn:
Mono<ServerResponse> get(ServerRequest request) { ... }
```

Thay thế chỉ bằng `Mono.just(serverResponse)` có thể chưa đủ trong handler có nguồn reactive: việc chọn status/body thường phụ thuộc kết quả `Mono<T>`. Cần composition thích hợp:

```java
return service.find(id)
        .flatMap(found -> ServerResponse.ok().bodyValue(found))
        .switchIfEmpty(ServerResponse.notFound().build());
```

Khi `find(id)` là Mono rỗng, method trả 404; không cần `block()` và không tự subscribe.

### 5.2. Một handler lambda hay class riêng?

| Cách | Phù hợp | Điểm cần lưu ý |
| --- | --- | --- |
| Lambda ngay trong route | Ping, hello, endpoint rất ngắn | Đừng biến RouterFunction bean thành một controller dài khó test |
| Method reference trong component | REST domain, nhiều endpoints | Có thể inject service, tách unit test |
| HandlerFunction implements riêng | Custom behavior hoặc composition | Đừng viết nhiều boilerplate nếu method reference đủ |

Dù dùng class, business logic nên ở service/repository thay vì nhồi DB transactions vào router declaration.

## 6. ServerRequest: URI, query, path và headers

`ServerRequest` cung cấp các API gần HTTP semantics:

```java
String id = request.pathVariable("id");
String query = request.queryParam("q").orElse("");
String method = request.method().name();
URI uri = request.uri();
List<MediaType> accept = request.headers().accept();
Optional<MediaType> contentType = request.headers().contentType();
String trace = request.headers().firstHeader("X-Trace-Id");
```

Đây là snippet, cần imports `java.net.URI`, `java.util.*` và `MediaType`. Các API query/cookie/headers có typed variants; hãy dùng chúng thay cho parser tự viết nếu khớp yêu cầu.

### 6.1. Parse path variable và lỗi

```java
long id;
try {
    id = Long.parseLong(request.pathVariable("id"));
} catch (NumberFormatException ex) {
    return ServerResponse.badRequest()
            .bodyValue(new ApiError("invalid_id"));
}
```

Đây là ví dụ trong một handler có return `Mono<ServerResponse>`; cần DTO `ApiError`. Tránh để `NumberFormatException` thành 500 và đừng phơi nội dung exception thô ra client. Trong code runnable bên dưới, route-level `onError(IllegalArgumentException.class,...)` giúp trả 400 khi ID không hợp lệ.

### 6.2. Query param nhiều giá trị

`request.queryParam("tag")` lấy một giá trị tùy API contract, còn request có thể chứa `?tag=a&tag=b`. Nếu API cho phép nhiều tags, xem `request.queryParams()` (`MultiValueMap<String,String>`) và định nghĩa rõ semantics OR/AND/deduplication/maximum count. Không ngầm chuyển các tham số lọc/search thành SQL string không có binding.

### 6.3. Header không phải security authority

`request.headers().firstHeader("X-User-Role")` chỉ đọc header do client có thể gửi. Nó **không** có tác dụng chứng minh người gọi là admin. Dùng reactive Spring Security `SecurityWebFilterChain` và quyền truy cập theo resource/tenant. Nếu cần principal:

```java
Mono<Principal> principal = request.principal();
```

`request.principal()` là async Mono; nếu API cần phân biệt anonymous user, phải xử lý empty theo contract và reactive SecurityContext đúng cách. Không tin raw `X-Forwarded-*` để xác định identity hay build redirects trừ khi trusted proxy đã scrub/normalize.

## 7. Đọc request body reactive

### 7.1. Một JSON object: bodyToMono

Với POST JSON:

```http
POST /api/books HTTP/1.1
Content-Type: application/json

{"title":"Spring WebFlux"}
```

Handler có thể dùng:

```java
Mono<CreateBook> input = request.bodyToMono(CreateBook.class);
```

Reader/codec (thường Jackson JSON) dùng media type và Java target type để decode bytes thành DTO. **Publisher là lazy**, nên việc gọi `bodyToMono(...)` không yêu cầu đọc toàn body ngay tại thời điểm tạo biến; WebFlux quản lý subscription và backpressure.

Không gọi:

```java
// ANTI-PATTERN: tránh block event-loop.
CreateBook input = request.bodyToMono(CreateBook.class).block();
```

Thay bằng compose:

```java
return request.bodyToMono(CreateBook.class)
        .flatMap(service::create)
        .flatMap(saved -> ServerResponse
                .created(URI.create("/api/books/" + saved.id()))
                .bodyValue(saved));
```

Đoạn là snippet; `service.create` trả `Mono<Book>` và cần URI/import. Cần áp validation trước khi ghi.

### 7.2. Nhiều values: bodyToFlux

```java
Flux<CreateBook> commands = request.bodyToFlux(CreateBook.class);
```

Nếu request là NDJSON hoặc streaming media type tương thích, body có thể được decode theo từng phần tử. Với JSON array, codec có thể decode nhiều values theo quy tắc media type/codec version. **Không** giả định mọi Content-Type đều hỗ trợ streaming từng record với cùng semantics.

Một ví dụ xử lý từng item:

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_NDJSON)
        .body(
                request.bodyToFlux(CreateBook.class)
                        .concatMap(service::create),
                Book.class);
```

Đây là snippet: cần codec hỗ trợ `application/x-ndjson`, `service.create` trả Mono<Book>, và contract cho phép trả records theo dòng. `concatMap` giữ thứ tự inner writes; `flatMap` có thể tăng concurrency nhưng đổi thứ tự. Cần hard limits trên số phần tử và kích thước request, chứ không dùng stream vô hạn không kiểm soát.

### 7.3. Generic type

Khi cần body là `List<CreateBook>` trong một JSON array:

```java
Mono<List<CreateBook>> batch = request.bodyToMono(
        new ParameterizedTypeReference<List<CreateBook>>() {});
```

Java type erasure khiến `List.class` không mang generic element type; typed reference cho Jackson biết các phần tử phải là `CreateBook` thay vì raw map. Mặc dù generic metadata đúng, bạn vẫn phải giới hạn số phần tử và kích thước payload.

### 7.4. Không đọc cùng body hai lần

Một reactive request body thường là stream được consume theo lifecycle HTTP. Anti-pattern:

```java
request.bodyToMono(String.class).subscribe(System.out::println);
return request.bodyToMono(CreateBook.class)
        .flatMap(service::create)
        .flatMap(ServerResponse.ok()::bodyValue);
```

Subscription tự tạo đã tách khỏi response lifecycle, đồng thời lần đọc thứ hai không được đảm bảo có cùng dữ liệu. Đối với logging/auditing, dùng bounded request-body decorator/observability được thiết kế đúng; không chép nguyên JWT/PII/raw body vào logs.

### 7.5. Validation tách khỏi deserialization

JSON codec chỉ biến bytes thành DTO; không có nghĩa `title` hợp lệ hoặc caller được phép ghi. Một manual rule:

```java
private static void validate(CreateBook input) {
    if (input == null || input.title() == null
            || input.title().isBlank()
            || input.title().length() > 120) {
        throw new IllegalArgumentException("Invalid title");
    }
}
```

Nếu dùng Jakarta Bean Validation/`@Validated`, trong functional handler phải áp Validator có chủ đích; không dựa vào tự động `@Valid @RequestBody` của annotated controller method. Spring reference minh họa `Validator` + `BeanPropertyBindingResult` và `ServerWebInputException` cho 400. Cần DTO allowlist, không decode JSON trực tiếp vào JPA entity có fields như `role`, `ownerId`, `tenantId`.

## 8. ServerResponse: status, headers, body

### 8.1. 200 OK JSON body

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new Book(1L, "Spring"));
```

`bodyValue(Object)` dành cho value đã có; trả `Mono<ServerResponse>`, framework encode body qua `HttpMessageWriter`. Với DTO `Book` từ demo, JSON thường là:

```json
{"id":1,"title":"Spring"}
```

### 8.2. 201 Created + Location

```java
return ServerResponse
        .created(URI.create("/api/books/42"))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new Book(42L, "Spring"));
```

Location URI có thể tương đối; nếu cần absolute canonical links, không ghép raw `Host`/`X-Forwarded-Host` của client thành external URL không tin cậy.

### 8.3. 204 No Content

```java
return ServerResponse.noContent().build();
```

HTTP 204 không có response body. Dùng cho delete/update thành công khi không cần trả resource mới. Nếu body cần hiển thị phiên bản mới, chọn 200 và JSON representation.

### 8.4. 400, 404, 409 với error DTO

```java
return ServerResponse.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new ApiError("invalid_input"));
```

```java
return ServerResponse.notFound().build();
```

```java
return ServerResponse.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new ApiError("conflict"));
```

Tình huống 409 có thể biểu thị business conflict, còn 412 phù hợp failed HTTP conditional request như `If-Match` stale version. Không trả HTTP 200 với `{"error":"not_found"}` nếu client cần dựa vào status code.

### 8.5. Header và cache

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header("Cache-Control", "no-store")
        .header("X-Example", "webflux")
        .bodyValue(book);
```

Cache-Control phải phù hợp data sensitivity/authorization. Một header tùy biến không thay authentication. Tránh tự set `Content-Length` bằng Java `String.length()` vì ký tự Unicode và encoded bytes khác nhau.

### 8.6. Response body là Publisher

```java
Flux<Book> records = service.findAll();

return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(records, Book.class);
```

Khác `bodyValue(book)`, `body(records, Book.class)` dùng Publisher và element type. **Đừng nhầm `bodyValue(flux)` với body Publisher.** Hãy dùng builder `body(Publisher, Class)` hoặc phù hợp `BodyInserter` cho source reactive.

### 8.7. SSE và NDJSON

```java
Flux<ServerSentEvent<String>> events = Flux.interval(
                Duration.ofSeconds(1))
        .take(3)
        .map(i -> ServerSentEvent.<String>builder()
                .id(i.toString())
                .event("tick")
                .data("event-" + i)
                .build());

return ServerResponse.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(events,
                new ParameterizedTypeReference<ServerSentEvent<String>>() {});
```

Snippet cần `Duration`, `ServerSentEvent`, `ParameterizedTypeReference` imports. SSE events có framing `event:`/`id:`/`data:` và kết thúc bằng dòng trống. Trong production, cần xử lý heartbeats, retries/resume IDs, proxy buffering, auth/tenant scope và client disconnect. Với `application/x-ndjson`, từng JSON value theo dòng; không tự kết luận đây là JSON array bình thường.

## 9. Reactive lifecycle, backpressure và non-blocking I/O

Trong WebFlux.fn, `ServerRequest.bodyToFlux(...)` và `ServerResponse.body(flux,...)` hỗ trợ **Reactive Streams backpressure**. Framework quản lý subscription khi đọc/ghi HTTP body. Nhưng reactive code vẫn có thể gây nghẽn khi:
- gọi JDBC/JPA blocking trên event-loop;
- gọi `Thread.sleep(...)` trong mapper;
- gọi `.block()` trong handler;
- tạo `Flux` vô hạn rồi `collectList()`;
- dùng `onBackpressureBuffer()` không giới hạn;
- thực thi nhiều downstream calls mà không đặt concurrency/connection limits.

### 9.1. Mono/Flux không tự làm code bất đồng bộ

```java
// Anti-pattern: work chạy ngay trước khi tạo Publisher.
Mono<Book> bad = Mono.just(legacyJdbc.findById(id));
```

`Mono.just(...)` nhận **giá trị đã tính xong**. Nếu code ở argument blocking, thread hiện tại vẫn bị block. Khi phải tích hợp blocking API tạm thời:

```java
Mono<Book> bridged = Mono.fromCallable(
                () -> legacyJdbc.findById(id))
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là snippet bọc blocking call có giới hạn; không biến JDBC thành reactive driver. Cân nhắc WebFlux + R2DBC, async WebClient hoặc Spring MVC khi app chủ yếu dùng blocking integrations.

### 9.2. Tránh tự subscribe trong handler

Handler nên **return publisher** để framework điều khiển cancellation/errors. Nếu tự `subscribe()` rồi trả response sớm, downstream error không còn gắn với HTTP request và thao tác có thể tiếp tục sau client disconnect. Đừng lấy `Mono<Void>` không có value làm lý do gọi `subscribe()` thủ công; có thể dùng `ServerResponse.ok().build(publisher)` để gắn completion với response khi phù hợp.

### 9.3. Streaming response đã committed

Khi SSE/NDJSON stream đã gửi header/body, một lỗi xảy ra muộn không đảm bảo đổi status 200 thành 500 hoặc thay response thành JSON error. Thiết kế protocol-level error event, close/cancel, reconnect/resume và logs/metrics riêng cho stream. Kiểm tra bằng HTTP client thực vì proxy/ingress có thể buffer các chunks.

## 10. Route filtering, before/after và onError

Functional router DSL có các chức năng:
- `before(Function<ServerRequest,ServerRequest>)`: thay đổi request abstraction trước handler, áp dụng trong scope builder.
- `after(BiFunction<ServerRequest,ServerResponse,ServerResponse>)`: hậu xử lý response trong scope.
- `filter(HandlerFilterFunction)`: bao quanh handler, quyết định có gọi `next.handle(request)` hay trả response sớm.
- `onError(...)`: chuyển exception phù hợp thành response.

### 10.1. Handler filter minh họa

```java
return RouterFunctions.route()
        .GET("/api/ping",
                request -> ServerResponse.ok().bodyValue("pong"))
        .filter((request, next) -> {
            long started = System.nanoTime();
            return next.handle(request)
                    .doOnSuccess(response -> {
                        long micros =
                                (System.nanoTime() - started) / 1000L;
                        System.out.println("responseBuiltMicros=" + micros);
                    });
        })
        .build();
```

Đây là snippet nhằm minh họa filter có thể bao quanh handler và đo thời điểm xây được `ServerResponse`. **Nó không đo đầy đủ thời gian ghi body/network**, vì response body có thể được stream sau đó. Production dùng Micrometer/Observation thay vì in stdout, không log raw headers/secrets.

### 10.2. Filter từ chối request

```java
.filter((request, next) -> {
    if (maintenanceModeEnabled()) {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .build();
    }
    return next.handle(request);
})
```

Đây là fragment; `maintenanceModeEnabled()` cần được định nghĩa. Không dùng một header do client tự đặt để quyết định quyền truy cập. Với authentication/authorization thật, dùng `SecurityWebFilterChain` và policy ở resource/service layer.

### 10.3. Nested filter scope

Nếu filter được thêm bên trong nested `path("/api/private", ...)`, scope không mặc định lan sang routes ở bên ngoài; một outer filter có thể bao các routes của outer builder. Do đó đặt auth controls hoàn toàn bằng một nested DSL filter dễ bỏ sót route mới. Security nên có global reactive filter chain và object-level authorization.

### 10.4. onError cho IllegalArgumentException

```java
return RouterFunctions.route()
        .GET("/api/books/{id}", handler::get)
        .onError(IllegalArgumentException.class,
                (ex, request) -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ApiError("invalid_input")))
        .build();
```

Đây là snippet route, không tự động bắt mọi lỗi từ WebFilter hoặc decoder trước routing. Catch type hẹp; không biến database failures thành 400. Nếu lỗi đã phát sau streaming response committed, onError cũng không thể đảm bảo thay status/header đã gửi.

### 10.5. before/after không thay security pipeline

Các DSL filters được gắn vào RouterFunction đã được match; WebFilter và Spring Security reactive filters nằm ở tầng HTTP handling rộng hơn. Nếu một router không match (hoặc request đến static resource/annotated controller), DSL filter gắn riêng router ấy không phải một security boundary tổng quát.

## 11. Cấu hình ServerRequest/ServerResponse codecs

WebFlux dùng `HttpMessageReader`/`HttpMessageWriter` reactive codecs. Các nội dung JSON trong demo phụ thuộc Jackson codec từ Boot WebFlux starter.

So sánh:

| Tác vụ | WebFlux.fn | WebMvc.fn |
| --- | --- | --- |
| Package | `web.reactive.function.server` | `web.servlet.function` |
| Handler return | `Mono<ServerResponse>` | `ServerResponse` |
| Read one JSON DTO | `request.bodyToMono(Dto.class)` | `request.body(Dto.class)` |
| Read stream | `request.bodyToFlux(Dto.class)` | Không có trực tiếp bodyToFlux |
| Write one value | `ServerResponse.ok().bodyValue(dto)` | `ServerResponse.ok().body(dto)` |
| Write Publisher | `body(publisher,Element.class)` | MVC có adapters nhưng write dựa trên Servlet |
| Codec | Reactive `HttpMessageReader/Writer` | `HttpMessageConverter` |
| I/O | Non-blocking reactive stack | Servlet blocking response writes |
| Async handling | Native Mono/Flux | Servlet async support nếu dùng reactive return |

**Không** chuyển import `org.springframework.web.servlet.function.ServerResponse` sang dự án WebFlux rồi thắc mắc vì sao `bodyValue`/`Mono<ServerResponse>` không khớp. Đây là **hai types khác nhau dù trùng tên**.

### 11.1. Codec buffer và kích thước payload

Spring Boot 3.5 có thể cấu hình:

```properties
spring.codec.max-in-memory-size=256KB
```

Đây là giới hạn bộ đệm codec cho các tình huống decode/aggregate, **không phải** hard cap cho tất cả HTTP bytes, file uploads hoặc mọi request streaming. Với JSON cực sâu, multipart lớn hoặc user gửi batch vô hạn, cần gateway/server/parser/stream quotas bổ sung. Không gom một Flux vô hạn bằng `collectList()`.

### 11.2. Content-Type, Accept và predicates

- Request `Content-Type` cho biết bytes body được gửi là JSON, text hay loại khác.
- Client `Accept` cho biết representation có thể nhận.
- `RequestPredicates.contentType(JSON)` hạn chế route theo Content-Type.
- `RequestPredicates.accept(JSON)` hạn chế route theo Accept.
- `ServerResponse.contentType(JSON)` khai báo media type phản hồi.

Nếu route predicate không match, HTTP status phụ thuộc phần còn lại của routing (có thể 404); nếu route đã match nhưng decoder không hỗ trợ input media type, thường có 415; nếu writer không tạo được output phù hợp, thường có 406/5xx tùy lỗi. Viết integration test cho contract 400/404/406/415 thay vì suy diễn từ một annotation dùng ở mô hình khác.

## 12. Demo runnable: Spring Boot 3.5, Java 17, WebFlux.fn

Demo tạo API sách bằng RouterFunction + HandlerFunction **không dùng `@RestController`**:

- `GET /api/books` — Flux các sách thành JSON array.
- `GET /api/books/{id}` — một sách JSON hoặc 404.
- `POST /api/books` — `bodyToMono(CreateBook.class)`, validate, tạo 201 + Location + JSON.
- `PUT /api/books/{id}` — cập nhật title hoặc 404.
- `DELETE /api/books/{id}` — 204 hoặc 404.
- `GET /api/books/ping` — route tĩnh phải khai báo trước `/{id}` để thể hiện order.

Store là in-memory `ConcurrentHashMap`, phục vụ demo routing/response; **không có database/authentication, CSRF, CORS, persistence, audit hay concurrency versioning**. Không đưa code này trực tiếp ra internet.

### 12.1. Khởi tạo Maven project

Tại [Spring Initializr](https://start.spring.io/), chọn Spring Boot **3.5.x**, Java **17+**, Maven, dependency **Spring Reactive Web**.

Fragment `pom.xml` do Initializr chuẩn bị:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

Dùng Spring Boot parent/BOM từ Initializr để quản lý versions của Reactor, Jackson, WebFlux và Netty. **Không thêm `spring-boot-starter-web` vào demo này**, vì Boot 3.x khi có cả MVC + WebFlux starters thường auto-configure Servlet MVC server. WebClient có thể được dùng trong MVC project, nhưng ở đây ta cần server thật sự là WebFlux.

Cấu trúc:

```text
src/main/
  java/com/example/demo/
    DemoApplication.java
src/test/
  java/com/example/demo/
    FunctionalBookTest.java
```

### 12.2. Toàn bộ Java source độc lập

Đặt **chính xác** nội dung sau vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.net.URI;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> bookRoutes(BookHandler handler) {
        return RouterFunctions.route()
                // Route tĩnh trước route động.
                .GET("/api/books/ping", handler::ping)
                .GET("/api/books", handler::list)
                .GET("/api/books/{id}", handler::get)
                .POST("/api/books", handler::create)
                .PUT("/api/books/{id}", handler::replace)
                .DELETE("/api/books/{id}", handler::delete)
                .onError(IllegalArgumentException.class,
                        (ex, request) -> ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new ApiError("invalid_input")))
                .build();
    }
}

record Book(long id, String title) {}

record CreateBook(String title) {}

record ApiError(String error) {}

@Component
class BookHandler {

    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentMap<Long, Book> books =
            new ConcurrentHashMap<>();

    Mono<ServerResponse> ping(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("pong");
    }

    Mono<ServerResponse> list(ServerRequest request) {
        int limit = Integer.parseInt(
                request.queryParam("limit").orElse("20"));

        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid limit");
        }

        Flux<Book> result = Flux.defer(() ->
                Flux.fromIterable(
                        books.values().stream()
                                .sorted(Comparator.comparingLong(Book::id))
                                .limit(limit)
                                .toList()));

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result, Book.class);
    }

    Mono<ServerResponse> get(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));

        return Mono.defer(() -> Mono.justOrEmpty(books.get(id)))
                .flatMap(book -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateBook.class)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("body required")))
                .flatMap(command -> {
                    validate(command);
                    return Mono.fromSupplier(() -> {
                        long id = nextId.incrementAndGet();
                        Book created = new Book(
                                id, command.title().trim());
                        books.put(id, created);
                        return created;
                    });
                })
                .flatMap(saved -> ServerResponse
                        .created(URI.create("/api/books/" + saved.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    Mono<ServerResponse> replace(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(CreateBook.class)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("body required")))
                .flatMap(command -> {
                    validate(command);
                    return Mono.defer(() -> Mono.justOrEmpty(
                            books.computeIfPresent(
                                    id, (key, oldBook) ->
                                            new Book(
                                                    id,
                                                    command.title().trim()))));
                })
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    Mono<ServerResponse> delete(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));

        return Mono.fromSupplier(() -> books.remove(id) != null)
                .flatMap(deleted -> deleted
                        ? ServerResponse.noContent().build()
                        : ServerResponse.notFound().build());
    }

    private static void validate(CreateBook command) {
        if (command == null
                || command.title() == null
                || command.title().isBlank()
                || command.title().length() > 120) {
            throw new IllegalArgumentException("invalid title");
        }
    }
}
```

### 12.3. Giải thích tính runnable

- Java 17 hỗ trợ `record`; một file có đúng một public class `DemoApplication`, các top-level records/classes khác package-private hợp lệ.
- `@SpringBootApplication` và `@Component` phát hiện handler; method `@Bean RouterFunction<ServerResponse>` đăng ký functional routes.
- `RouterFunctions.route()` là WebFlux.fn, không phải WebMvc.fn vì imports bắt đầu bằng `org.springframework.web.reactive.function.server`.
- Methods `ping/list/get/create/replace/delete` đều trả `Mono<ServerResponse>`. Không dùng `.block()` hay detached `.subscribe()`.
- `request.bodyToMono(CreateBook.class)` decode JSON theo reactive HTTP codecs.
- `ServerResponse.ok().body(flux, Book.class)` cung cấp Publisher với element type để writer serialize thành response.
- `Mono.defer` và `Mono.fromSupplier` trì hoãn đọc/ghi in-memory cho đến subscription. **Chúng không làm mọi imperative code trở thành non-blocking**; demo chỉ dùng thao tác map rất nhanh, không có JDBC/disk/network blocking.
- `onError(IllegalArgumentException.class, ...)` trả 400 JSON cho lỗi parse path/query hoặc manual title validation trong phạm vi router này.
- JSON malformed/converter errors có exception type khác; không tự nhận rằng router `onError(IllegalArgumentException)` bắt mọi JSON parse error.
- `ConcurrentHashMap` + `AtomicLong` bảo đảm ID khác nhau trong một JVM, không cho độ bền dữ liệu hoặc multi-instance consistency.
- `PUT` không có `If-Match`/ETag; một stale client vẫn có thể overwrite. Production nên dùng optimistic locking.
- `GET /api/books/ping` đứng trước `GET /api/books/{id}` theo **thứ tự routing WebFlux.fn**.

### 12.4. Khởi chạy

Trong project root đã có Maven wrapper:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell/CMD
mvnw.cmd spring-boot:run
```

Nếu chưa cấu hình port, server thường nghe `http://localhost:8080`. Đây là Spring Boot WebFlux reactive application; không cần `@EnableWebFlux` để tạo handler mappings mặc định, và bật annotation này không cần thiết có thể ảnh hưởng auto-configuration.

### 12.5. Kiểm tra routes bằng curl

**Ping tĩnh:**

```bash
curl -i 'http://localhost:8080/api/books/ping'
```

Dự kiến status 200, `Content-Type` tương thích `text/plain`, body:

```text
pong
```

Nếu `/{id}` đặt trước trong router, `ping` có thể bị parse như ID, gây 400. Đây là minh họa rõ vì sao WebFlux.fn routing order quan trọng.

**List ban đầu:**

```bash
curl -i 'http://localhost:8080/api/books?limit=10'
```

Status 200, JSON array rỗng:

```json
[]
```

**POST tạo sách:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"WebFlux Functional"}'
```

Nếu app vừa khởi động chưa tạo tài nguyên khác, status 201, Location và JSON tương ứng:

```http
HTTP/1.1 201 Created
Location: /api/books/1
Content-Type: application/json

{"id":1,"title":"WebFlux Functional"}
```

Status, Location và các JSON fields là điều cần kiểm tra; thứ tự headers, header Date, charset hay whitespace JSON do runtime/converter quyết định.

**GET theo ID:**

```bash
curl -i 'http://localhost:8080/api/books/1'
curl -i 'http://localhost:8080/api/books/9999'
```

Với ID 1 tồn tại: status 200 JSON. Với ID không tồn tại: 404.

**PUT title:**

```bash
curl -i -X PUT 'http://localhost:8080/api/books/1' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"Updated title"}'
```

Khi ID tồn tại: status 200 JSON với title mới. Với ID không tồn tại: 404. Với title trống: 400 do validation.

**DELETE:**

```bash
curl -i -X DELETE 'http://localhost:8080/api/books/1'
```

Lần đầu nếu ID tồn tại: status 204 No Content. Lần tiếp theo cùng ID: 404 theo contract demo.

**Query limit lỗi:**

```bash
curl -i 'http://localhost:8080/api/books?limit=0'
curl -i 'http://localhost:8080/api/books?limit=abc'
```

Dự kiến 400 với JSON error DTO:

```json
{"error":"invalid_input"}
```

**Title lỗi:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"  "}'
```

Dự kiến 400, `{"error":"invalid_input"}`. Với malformed JSON (ví dụ `{"title":`), việc decode thất bại sẽ được error infrastructure xử lý; **đừng giả định error body giống hệt manual validation** nếu chưa cấu hình chung.

### 12.6. Windows PowerShell

Dùng `curl.exe` thay vì `curl` alias để giữ cú pháp phổ biến:

```powershell
curl.exe -i "http://localhost:8080/api/books/ping"
curl.exe -i "http://localhost:8080/api/books?limit=10"
```

Với POST/PUT JSON, tạo file `request.json` UTF-8:

```json
{"title":"WebFlux Functional"}
```

Gửi:

```powershell
curl.exe -i -X POST "http://localhost:8080/api/books" -H "Content-Type: application/json" --data-binary "@request.json"
```

Cách này tránh escape-quote khác nhau giữa Bash, PowerShell 5 và PowerShell 7.

## 13. Kiểm thử WebFlux.fn bằng WebTestClient

Spring WebFlux có `WebTestClient` hỗ trợ bind-to-router-function hoặc gửi request qua embedded server thật. Để test end-to-end Spring Boot auto-configuration/codec/routes, dùng integration test với RANDOM_PORT.

### 13.1. Test dependency

Với Spring Boot 3.5, thêm:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Trong Boot 4 starter/test auto-configuration packages đã được modular hóa, vì vậy cần kiểm tra lại dependency/import nếu nâng cấp; đoạn test dưới đây dành cho Boot 3.5.

### 13.2. File FunctionalBookTest.java

Lưu `src/test/java/com/example/demo/FunctionalBookTest.java`:

```java
package com.example.demo;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FunctionalBookTest {

    @Autowired
    WebTestClient client;

    @Test
    void staticRouteShouldNotBeCapturedAsNumericId() {
        client.get().uri("/api/books/ping")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("pong");
    }

    @Test
    void createThenReadUsesRealLocation() {
        EntityExchangeResult<byte[]> created = client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Test Book\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Test Book")
                .returnResult();

        URI location = created.getResponseHeaders().getLocation();
        assertNotNull(location);

        client.get().uri(location.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Test Book");
    }

    @Test
    void unknownResourceReturns404() {
        client.get().uri("/api/books/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void invalidTitleReturns400() {
        client.post().uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"   \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_input");
    }

    @Test
    void invalidLimitReturns400() {
        client.get().uri("/api/books?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
```

Test không hardcode ID=1 vì store có thể đã được tạo dữ liệu trong những tests khác cùng Spring context. `returnResult().getResponseHeaders().getLocation()` lấy URI thật từ 201 response, rồi GET URI đó. Test `/ping` bảo vệ thứ tự route tĩnh/động.

### 13.3. Test router đơn vị không khởi động server

Có thể dùng:

```java
WebTestClient client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .build();
```

Đây là snippet; cần một `RouterFunction<ServerResponse> routerFunction` được tạo hoặc inject. Cách này test routing/handler mà không cần mở socket thật. Tuy nhiên không chứng minh proxy, Netty configuration, TLS, WebFilter/security chain production được áp đúng; kết hợp router-level tests với integration tests khi cần.

### 13.4. Chạy tests

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Thêm test cho PUT/DELETE, wrong content type, malformed JSON, batch quá lớn, CORS/security, client disconnect và concurrency nếu chuyển store sang database. Với shared in-memory state, cần fixture isolation nếu tests chạy song song.

## 14. So sánh với WebMvc.fn và annotated WebFlux

| Tiêu chí | WebFlux.fn | WebMvc.fn | Annotated WebFlux |
| --- | --- | --- | --- |
| Router declaration | `RouterFunction` | `RouterFunction` | `@RequestMapping` |
| Handler | `Mono<ServerResponse> handle(ServerRequest)` | `ServerResponse handle(ServerRequest)` | Java method với annotated args/return |
| Request body | `bodyToMono/bodyToFlux` | `body(Class)/body(ParameterizedTypeReference)` | `@RequestBody Mono<Dto>` |
| Response body | `bodyValue/body(Publisher,Class)` | `body(Object)` | `Mono<Dto>`/`Flux<Dto>`/`ResponseEntity` |
| HTTP stack | Reactive WebFlux | Servlet MVC | Reactive WebFlux |
| Codec | `HttpMessageReader/Writer` | `HttpMessageConverter` | `HttpMessageReader/Writer` |
| Route ordering | **Khai báo trước được kiểm tra trước** | Functional routing theo order | Mapping comparator |
| Filter DSL | `before/after/filter/onError` | Có functional filters | @ControllerAdvice, WebFilter |
| HTTP I/O | Non-blocking khi runtime hỗ trợ | Servlet blocking writes | Non-blocking khi runtime hỗ trợ |
| Rõ ràng về route contract | DSL tập trung | DSL tập trung | Annotation nằm cạnh method |
| Controller argument binding | Tường minh qua ServerRequest | Tường minh qua ServerRequest | Framework resolve annotated params |

WebFlux.fn và annotated WebFlux có thể **cùng tồn tại** trong `DispatcherHandler`; nên tránh trùng path/method mà không có ownership routing rõ ràng. Nhiều teams thích annotated model cho CRUD đơn giản/Bean Validation mạnh, và WebFlux.fn cho gateways, route composition, filters hoặc handlers cần xây động. Không có mô hình nào “luôn nhanh hơn” chỉ vì cách khai báo; benchmark tập trung vào I/O, codecs, data access, thread/connection pools và backpressure.

### 14.1. Kotlin router DSL

Ngoài Java DSL, Spring hỗ trợ Kotlin `router { }` / `coRouter { }` (coroutines). Coroutine handlers có thể là `suspend fun` trả response với DSL riêng. **Không** chép trực tiếp các lời gọi `bodyValueAndAwait` từ Kotlin coroutine examples vào Java WebFlux.fn và kỳ vọng compile; Java dùng `Mono<ServerResponse>`.

### 14.2. Spring Framework 7 API versioning

Spring Framework 7 bổ sung khả năng version predicates/mappings có cấu hình API version strategy. **Không** dùng `RequestPredicates.version("1.0")` nếu codebase Spring Framework 6.2/Boot 3.5 chưa có API đó. Trên Spring 6.x, thực hiện version qua path/query/header/media type hoặc custom route policy được test rõ; khi nâng Spring 7 cần đọc tài liệu mới để biết supported versions/baseline/deprecation semantics.

## 15. CORS, Security và giao dịch

Functional routing là **cách chọn handler**, không phải security boundary.

### 15.1. Reactive Spring Security

Với WebFlux server, Spring Security sử dụng `SecurityWebFilterChain` và các reactive security context APIs. Khi viết business handler:
- xác thực người dùng theo token/cookie policy;
- authorize theo method/path và object ID/tenant;
- fail closed khi permission service không khả dụng;
- tránh log Authorization token;
- không coi `X-User-Role` hoặc `API-Version` là bằng chứng quyền.

Nếu app nhận cookies/session và có các thao tác state-changing, cần CSRF policy phù hợp. CORS origin allowlist cần tách biệt với authentication và không phản chiếu tùy ý mọi Origin kèm credentials.

### 15.2. CORS với WebFlux.fn

Spring reference mô tả CORS của functional endpoints qua `CorsWebFilter`. Một số cơ chế MVC annotations như `@CrossOrigin` gắn vào controller method **không phải** cấu hình CORS cho RouterFunction đã khai báo bằng DSL. Khi dùng Spring Security, CORS configuration phải phối hợp với reactive security filter chain và OPTIONS preflight; kiểm thử cross-origin từ client thật.

### 15.3. Giao dịch và consistency

WebFlux.fn không tự cấp database transaction. Dùng reactive transactional service/`TransactionalOperator` phù hợp với R2DBC nếu cần các thay đổi atomically; với external side effects cần outbox/saga/idempotency khi nghiệp vụ yêu cầu. Demo dùng in-memory map, không có transaction isolation giữa nhiều pods. Cũng không có ETag/If-Match để tránh lost update.

### 15.4. Upload/multipart

Đối với multipart, ServerRequest có thể truy cập parts qua reactive APIs; dùng `Part`/`FilePart` hoặc `PartEvent` streaming theo nhu cầu. Với `PartEvent`, cần consume/relay/release đầy đủ contents để tránh memory leaks. Không đọc file upload lớn vào một `byte[]` không giới hạn; phải có file size quota, file type validation, safe filename server-generated, scanning nếu policy yêu cầu và private storage.

## 16. Những lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| IDE không tìm `bodyToMono` | Import WebMvc.fn ServerRequest | Dùng `web.reactive.function.server.ServerRequest` |
| Handler method báo sai return type | Dùng `ServerResponse` thay `Mono<ServerResponse>` | Compose và trả Mono |
| `bodyValue(flux)` không phù hợp | Truyền Publisher như plain value | Dùng `body(flux, Element.class)` |
| Route static `/ping` bị parse như ID | Route động `/{id}` đứng trước | Đặt route tĩnh trước |
| Hai routes trùng nhau | DSL order/registration không kiểm soát | Định nghĩa path/predicate rõ, tests |
| Route POST không match | `contentType` predicate/HTTP method/path sai | Test route với Content-Type |
| GET Accept chỉ XML không match JSON route | `accept(JSON)` false | Thống nhất media type, kiểm thử status |
| POST JSON lỗi trở thành 5xx | Không xử lý decoder/validation errors đúng | Error policy riêng cho parse/body |
| `Mono.empty()` trả response không đúng | Không mapping absence -> 404 | `switchIfEmpty(notFound().build())` |
| Handler tự `subscribe()` rồi response rỗng | Subscription tách HTTP lifecycle | Return Publisher |
| Handler dùng `block()` gây nghẽn | Blocking event loop | Compose Mono/Flux |
| Flux vô hạn không hoàn tất | Response JSON kiểu collect/buffer không có termination | Streaming media, timeout/quota |
| Client chậm gây memory pressure | Queue/buffer thiếu giới hạn | Backpressure, bounded buffers/concurrency |
| JSON generic ra List<Map> | Mất element type | `ParameterizedTypeReference` |
| Router filter không áp endpoint khác | Nested/router-local filter scope | Global WebFilter/security |
| CORS preflight bị từ chối | Chưa cấu hình CorsWebFilter/security | CORS chain đúng cho functional routes |
| Kết quả null từ Mono mapper | Reactor không cho null onNext | Dùng Mono.empty/justOrEmpty |
| JSON decode đúng nhưng dữ liệu vượt quyền | DTO/ACL thiếu | Validate và authorize |
| Header role spoofing | Tin X-User-Role từ client | Spring Security và tenant scope |
| 201 thiếu Location | Chỉ trả status 201 | `ServerResponse.created(uri)` |
| 204 có body | Dùng body trên noContent | `ServerResponse.noContent().build()` |
| Đã stream rồi muốn đổi status | Response committed | Stream error protocol/close |
| Có MVC và WebFlux starters, đang chạy MVC | Boot chọn Servlet MVC | Chọn web application stack rõ ràng |
| @EnableWebFlux làm mất Boot conventions | Can thiệp config không cần thiết | Giữ WebFlux Boot auto-config nếu đủ |
| Tests phụ thuộc ID=1 | Shared in-memory store | Dùng Location, fixture isolation |
| Malformed path id thành 500 | Parse không được map 400 | Narrow onError/manual parse |
| Data race khi nhiều writers | Map/DB update không có version | Optimistic locking/CAS |
| Proxy buffer SSE | Reverse proxy/HTTP client buffering | Test streaming end-to-end |

## 17. Checklist code review và vận hành

- [ ] Imports thuộc đúng `org.springframework.web.reactive.function.server`, không trộn WebMvc.fn.
- [ ] Có bean `RouterFunction<ServerResponse>` được Spring Boot phát hiện.
- [ ] Mỗi route có method/path/predicate rõ và theo **thứ tự tĩnh trước động**.
- [ ] Không có catch-all/fallback route vô tình che API khác.
- [ ] Handler return `Mono<ServerResponse>` và không gọi `block()` hoặc detached `subscribe()`.
- [ ] Request metadata đọc từ ServerRequest, body từ `bodyToMono/bodyToFlux` đúng codec.
- [ ] DTO và generic type đúng, không bind entity có trường server-managed.
- [ ] Validation/ACL và request body size/quota được áp dụng trước khi side effects.
- [ ] Response status, Content-Type, Location, Cache-Control, empty body semantics hợp lệ.
- [ ] Streaming SSE/NDJSON có timeout/heartbeat/cancel/reconnect và backpressure.
- [ ] onError/filter có scope rõ, không thay SecurityWebFilterChain.
- [ ] Global CORS preflight OPTIONS tương thích security, không phản chiếu Origin tùy ý.
- [ ] Không có JDBC/JPA blocking trực tiếp trên Netty event loop.
- [ ] Concurrency fan-out/gateway traffic có giới hạn; backpressure queues không vô hạn.
- [ ] Tests WebTestClient bao 200/201/204/400/404, invalid payload và thứ tự router.
- [ ] Với production writes có DB transactions, optimistic locking và idempotency.
- [ ] Monitoring có latency, 4xx/5xx, active requests, upstream errors, client disconnect mà không log secrets.
- [ ] Khi upgrade Spring Framework 7, rà soát request versioning và starter/test packages.

## 18. Câu hỏi ôn tập

**Câu 1.** WebFlux.fn khác WebMvc.fn về import và handler signature ra sao?

**Đáp án:** WebFlux.fn dùng `org.springframework.web.reactive.function.server`, handler nhận `ServerRequest` và trả `Mono<ServerResponse>`; WebMvc.fn dùng `org.springframework.web.servlet.function` và handler trả `ServerResponse`.

**Câu 2.** Tại sao đặt `GET /api/books/ping` trước `GET /api/books/{id}`?

**Đáp án:** WebFlux.fn kiểm tra routes **theo thứ tự**; route động có thể match literal `ping` nếu nó được đặt trước.

**Câu 3.** Đọc một JSON object thành CreateBook trong WebFlux.fn?

**Đáp án:** `request.bodyToMono(CreateBook.class)`, sau đó validate và compose kết quả bằng `flatMap`.

**Câu 4.** Trả Flux<Book> qua ServerResponse?

**Đáp án:** `ServerResponse.ok().contentType(APPLICATION_JSON).body(books,Book.class)`; không coi Flux là plain DTO cho `bodyValue`.

**Câu 5.** `Mono.empty()` có tự động mang nghĩa HTTP 404 không?

**Đáp án:** Không. Handler phải trả `ServerResponse.notFound().build()` hoặc xử lý absence rõ ràng.

**Câu 6.** Khi route filter gọi `next.handle(request)`, điều gì xảy ra?

**Đáp án:** Chuỗi handler/filter kế tiếp được chạy; nếu không gọi, filter có thể trả response sớm để chặn request.

**Câu 7.** `RequestPredicates.contentType(JSON)` false có bảo đảm 415 không?

**Đáp án:** Không. Router có thể đơn giản không match và đường xử lý lỗi phụ thuộc các routes khác; test nếu API cần 415.

**Câu 8.** Functional handler có tự chạy @Valid trên DTO mà bodyToMono decode không?

**Đáp án:** Không theo cùng cách annotated argument validation. Cần áp Validator/manual validation rõ.

**Câu 9.** Có thể dùng `@CrossOrigin` của annotated controller thay cho CorsWebFilter trên WebFlux.fn không?

**Đáp án:** Không mặc định; functional endpoints cần reactive CORS configuration tương ứng, thường thông qua `CorsWebFilter`.

**Câu 10.** WebFlux.fn đã non-blocking thì gọi JDBC trong `Mono.just(...)` có an toàn không?

**Đáp án:** Không. JDBC call chạy trước khi Mono.just được tạo và có thể block event loop; dùng reactive I/O hoặc controlled boundedElastic bridge/kiến trúc MVC phù hợp.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — Functional Endpoints (WebFlux.fn)](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): RouterFunction/HandlerFunction/ServerRequest/ServerResponse, predicates, nested routes, filters, validation.
- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): reactive HTTP foundation, non-blocking I/O, backpressure.
- [Spring Framework 6.2 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/reactive/function/server/ServerRequest.html): bodyToMono/bodyToFlux, query/path/header, multipart.
- [Spring Framework 6.2 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html): response builder/status/body/headers.
- [Spring Framework 6.2 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.Builder.html): GET/POST/nest/filter/onError methods.
- [Spring Framework 6.2 — RequestPredicates Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/reactive/function/server/RequestPredicates.html): method/path/accept/contentType composition.
- [Spring Framework — WebFlux HTTP Message Codecs](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html): Reactive HttpMessageReader/Writer foundation.
- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): CorsWebFilter và bảo mật cross-origin.
- [Spring Boot 3.5 — Reactive Web](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): starter, RouterFunction beans và WebFlux server.
- [Spring Boot 3.5 — Testing Spring Boot Applications](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html): WebTestClient integration testing.
- [Spring Security — WebFlux Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html): reactive SecurityWebFilterChain và access controls.
- [Project Reactor — Core Reference](https://projectreactor.io/docs/core/release/reference/): Mono/Flux, cancellation, scheduling, backpressure và tests.

**Kết luận:** WebFlux.fn biểu diễn routing bằng `RouterFunction<ServerResponse>` và behavior bằng `HandlerFunction<ServerResponse>`; mỗi handler nhận `ServerRequest` và trả **`Mono<ServerResponse>`**. Route matching được đánh giá theo thứ tự DSL; request bodies được đọc qua `bodyToMono/bodyToFlux`, response dùng `bodyValue` hoặc `body(Publisher,Class)`. Để vận hành an toàn, phải có DTO/validation/auth, giới hạn resource/concurrency, proper error handling và tests — không chỉ ghép lambda rồi cho rằng đã có một REST API production-ready.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-002">ServerRequest, ServerResponse và hợp đồng bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# ServerRequest, ServerResponse và hợp đồng bất đồng bộ trong Functional WebFlux

## 1. Functional WebFlux là gì và bài học giải quyết vấn đề nào?

Spring WebFlux có hai cách phổ biến để tạo HTTP endpoints: annotated controllers (`@RestController`, `@GetMapping`, `@RequestBody`) và **functional endpoints** hay **WebFlux.fn**. Ở WebFlux.fn, ta biểu diễn cách chọn route và cách xử lý request bằng các hàm Java, không cần gắn annotation lên từng HTTP handler.

Ba kiểu trung tâm thuộc package **`org.springframework.web.reactive.function.server`**:

- `ServerRequest` mô tả một HTTP request theo contract tiện dụng và immutable: method, path, URI, query, headers, path variables, cookies, body publishers.
- `HandlerFunction<T extends ServerResponse>` nhận một `ServerRequest` và trả **`Mono<T>`**, tức một publisher biểu diễn response được tạo **bất đồng bộ**.
- `ServerResponse` mô tả một HTTP response gồm status, headers, cookies và body. Builder cũng trả **`Mono<ServerResponse>`**.

Thành phần kết nối là `RouterFunction<T extends ServerResponse>`: nó lấy `ServerRequest` và trả một **`Mono<HandlerFunction<T>>`** nếu match; không match thì publisher complete empty. Trong ứng dụng bình thường ta không tự gọi router trên request từng lần mà đăng ký một `RouterFunction<ServerResponse>` Spring bean hoặc triển khai `HttpHandler`.

**Mục tiêu quan trọng nhất:** hiểu *hai hợp đồng bất đồng bộ khác nhau*:

1. **Routing phase:** `Mono<HandlerFunction<ServerResponse>>` — tìm handler khớp request; có thể empty khi không match.
2. **Handling phase:** `Mono<ServerResponse>` — sau khi match, handler có thể chờ dữ liệu reactive rồi tạo response với status/headers/body phù hợp.

Từ đó mới hiểu vì sao `Mono.empty()` của việc **không match route** khác với `Mono.empty()` của **không tìm thấy entity**, và vì sao response có thể chứa tiếp một `Flux<T>` body phát nhiều phần tử.

### 1.1. Kết quả học tập

Sau bài, bạn có thể:

1. Giải thích bằng chữ ký Java chuẩn: `ServerRequest → Mono<HandlerFunction> → Mono<ServerResponse>`.
2. Phân biệt WebFlux.fn với **WebMvc.fn** trên Servlet, không import lẫn package.
3. Đọc HTTP request bằng `pathVariable`, `queryParam`, `headers`, `bodyToMono` và `bodyToFlux`.
4. Dựng HTTP response 200/201/204/400/404/409/429; so `bodyValue`, `body(publisher, elementClass)` và `build()`.
5. Hiểu assembly, subscription, no-blocking, error/completion/cancellation, codec, backpressure và streaming media types.
6. Đăng ký `RouterFunctions.route()`, biết thứ tự first-match, `RequestPredicates`, nested/composed routes và `HandlerFilterFunction`.
7. Tách handler/service, bảo vệ validation, authentication, authorization, CORS, CSRF và tài nguyên dùng chung.
8. Có một dự án Spring Boot 3/Java 17 với CRUD reactive không có database, WebTestClient HTTP tests và Reactor StepVerifier tests.

**Phạm vi:** Spring Framework 6.x, Spring Boot 3.3.x, Project Reactor 3.x. Ví dụ chính chạy bằng **spring-boot-starter-webflux** trên Reactor Netty mặc định, không mang sang Spring MVC Servlet. Dự án dùng in-memory store để cô lập đúng nguyên lý WebFlux.fn; production dùng reactive database/HTTP clients với timeout và security thật.

## 2. So sánh hợp đồng với WebMvc.fn: cùng tên, khác bản chất

Một lỗi dễ mắc là dùng `org.springframework.web.servlet.function.ServerRequest` thay vì `org.springframework.web.reactive.function.server.ServerRequest`. Hai API có tên tương tự nhưng không thay thế được cho nhau.

| Điểm | WebMvc.fn (Servlet) | WebFlux.fn (Reactive) |
| --- | --- | --- |
| Package | `org.springframework.web.servlet.function` | `org.springframework.web.reactive.function.server` |
| HTTP infrastructure | `DispatcherServlet` | `DispatcherHandler` |
| Route result | `Optional<HandlerFunction<ServerResponse>>` | `Mono<HandlerFunction<ServerResponse>>` |
| Handler result | `ServerResponse` | `Mono<ServerResponse>` |
| Đọc DTO body | `request.body(MyDto.class)` | `request.bodyToMono(MyDto.class)` |
| Đọc sequence body | `request.body(new ParameterizedTypeReference<...>())` | `request.bodyToFlux(MyDto.class)` |
| Response có dữ liệu | `ServerResponse.ok().body(dto)` | `ServerResponse.ok().bodyValue(dto)` |
| Response với Publisher | Có integrations riêng nhưng không phải contract cốt lõi | `.body(flux, Item.class)` |
| Filter của functional routing | Servlet `HandlerFilterFunction` đồng bộ | WebFlux `HandlerFilterFunction` trả `Mono<ServerResponse>` |
| Web filtering toàn ứng dụng | Servlet `Filter` hoặc interceptor | Reactive `WebFilter` |
| Mục tiêu | Routing functional trên Servlet | Routing functional trên reactive HTTP runtime |

**Import chuẩn:**

```java
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
```

**Import sai trong bài này:**

```java
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
```

Cùng một kiểu tên `ServerResponse` nhưng bên WebFlux builder hoàn tất bằng một **Mono**. Vì thế:

```java
// WEBFLUX.FN — đúng chữ ký
HandlerFunction<ServerResponse> hello =
    request -> ServerResponse.ok().bodyValue("Hello");

// WEBMVC.FN — chữ ký của stack KHÁC
// HandlerFunction<ServerResponse> hello =
//    request -> ServerResponse.ok().body("Hello");
```

Snippet trên là phần minh họa trong một class có đúng imports reactive và là đoạn có thể sử dụng bên trong method với type thích hợp. Chỉ một dependency WebFlux là chưa đủ nếu Spring Boot cũng kéo thêm `spring-boot-starter-web`: khi hai starters hiện diện, Boot thông thường ưu tiên Servlet MVC. Kiểm tra web application type qua configuration và startup logs, không suy từ tên controller.

## 3. Vẽ đúng hai giai đoạn routing và handling

Flow căn bản:

```text
HTTP GET /api/notes/7
      │
      ▼
Reactor Netty inbound Channel
      ▼
WebFlux HttpHandler / WebHandler / WebFilter
      ▼
DispatcherHandler
      ▼
RouterFunctionMapping
      │
      └── RouterFunction.route(ServerRequest)
             │
             ├── No match: Mono.empty()
             │       → thử route/mapping tiếp, cuối cùng có thể 404
             │
             └── Match: Mono.just(HandlerFunction)
                          │
                          ▼
                  HandlerFunction.handle(ServerRequest)
                          │
                          └── Mono<ServerResponse>
                               │
                               ├── DB/WebClient publisher complete
                               ├── chọn HTTP status + headers
                               ├── ServerResponseResultHandler
                               └── ServerResponse.writeTo(response,...)
                                      │
                                      ▼
                                HTTP codecs + response body Publisher
                                      │
                                      ▼
                                  Client
```

Từ góc nhìn API chính thức, hợp đồng rút gọn:

```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}

// Rút gọn conceptual signature của RouterFunction:
Mono<HandlerFunction<T>> route(ServerRequest request);
```

Đây là **signature-level pseudocode** để học: interface thực của Spring có generic types, annotations và các methods khác tùy phiên bản. Không copy đoạn khai báo này thành hai interface mới trùng tên với Spring.

### 3.1. “Bất đồng bộ” nghĩa gì?

`Mono<ServerResponse>` là **lời hứa theo Reactive Streams** cho việc tạo ra một response trong tương lai, không phải `Future.get()` đang chặn. Handler có thể tạo publisher ngay, còn logic đọc dữ liệu đến khi downstream subscribe mới bắt đầu:

```java
Mono<ServerResponse> response = service.find(id)
    .flatMap(note ->
        ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(note))
    .switchIfEmpty(ServerResponse.notFound().build());
```

Nếu `service.find(id)` dùng một reactive data driver, network/database wait không yêu cầu giữ thread ngồi chờ. Nhưng đoạn code **không biến JDBC blocking thành non-blocking**; phải kiểm tra dependencies và bất kỳ mapping/validation function nào chạy đồng bộ.

### 3.2. “Một response” không nghĩa “một item body”

`Mono<ServerResponse>` chỉ có tối đa **một ServerResponse descriptor**; bên trong descriptor có thể chứa:

- `String` hay `Note`: body một object/string.
- `Mono<Note>`: nguồn một entity async.
- `Flux<Note>`: nhiều entities hoặc streaming data.
- `Flux<DataBuffer>`: các chunks bytes.
- Không body: `ServerResponse.noContent().build()`.

Sơ đồ:

```text
HandlerFunction:
  Mono<ServerResponse>                  (0..1 descriptor)

ServerResponse.body:
  Publisher<T>                          (0..N onNext, theo loại publisher)

HTTP exchange:
  status/headers được gửi một lần; body có thể stream đến khi complete
```

**Không lẫn “không match router” với “response body không có item”.** Nếu route match và handler chủ động trả `ServerResponse.notFound().build()`, đó là một HTTP 404 response hợp lệ. Nếu chính handler return `Mono.empty()` thay vì tạo descriptor, HTTP result behavior không có status được quyết định rõ; tránh dựa vào đó để biểu diễn entity not found.

### 3.3. Publisher có ba tín hiệu cốt lõi

```text
Subscriber nhận onSubscribe
  → onNext(value) zero or more times
  → onComplete() HOẶC onError(error)
```

Mono tối đa một `onNext`, Flux 0..N. Null không được phát làm `onNext`. Cancellation là một tín hiệu quản lý subscription, không đồng nghĩa `onComplete`. Với HTTP client disconnect, transport có thể cancel body publisher; cleanup cần tính cả error/cancel.

## 4. ServerRequest: đọc method, URL, headers, params và body reactive

`ServerRequest` là interface bất biến ở góc nhìn handler, cung cấp các API dễ gọi hơn so với truy cập trực tiếp `ServerWebExchange`. Nó không có nghĩa request body đã được parse hoặc cache trong một Java object.

Các API thông dụng:

| Dữ liệu cần đọc | API | Kiểu/ý nghĩa |
| --- | --- | --- |
| HTTP method | `request.method()` | `HttpMethod` |
| URI | `request.uri()` | `URI` |
| Path | `request.path()` | path string |
| Path variable | `request.pathVariable("id")` | `String`, parse khi cần |
| Query param | `request.queryParam("q")` | `Optional<String>` |
| Tất cả query | `request.queryParams()` | `MultiValueMap<String,String>` |
| Headers | `request.headers()` | `ServerRequest.Headers` |
| Header đầu tiên | `request.headers().firstHeader("X-Trace-Id")` | nullable String |
| Content-Type | `request.headers().contentType()` | `Optional<MediaType>` |
| Accept | `request.headers().accept()` | `List<MediaType>` |
| Cookies | `request.cookies()` | `MultiValueMap<String,HttpCookie>` |
| Body một object | `request.bodyToMono(Command.class)` | `Mono<Command>` |
| Body nhiều items | `request.bodyToFlux(Event.class)` | `Flux<Event>` |
| Low-level HTTP | `request.exchange()` | `ServerWebExchange` |

API có thể return empty Optional khi query param không có, hoặc throw khi path variable bắt buộc không hiện diện trong mapping; không dùng chúng như đã validate input.

### 4.1. Đọc path variable và validate kiểu

Route:

```java
.GET("/api/notes/{id}", handler::get)
```

Trong handler:

```java
String raw = request.pathVariable("id");
long id;
try {
    id = Long.parseLong(raw);
} catch (NumberFormatException e) {
    return ServerResponse.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("error", "invalid_id"));
}
```

Đây là snippet nằm trong method `Mono<ServerResponse> get(ServerRequest request)`, không chạy độc lập. Khác với annotated `@PathVariable long id`, một functional handler nhận String rồi tự convert/validate. Đặt business invariant (ID phải dương, tenant hợp lệ) ở service/handler thích hợp.

### 4.2. Query parameters

```java
String query = request.queryParam("q").orElse("").trim();
```

`?q=webflux` là query param; `/api/notes/{id}` là path variable. Với nhiều query values:

```java
List<String> tags = request.queryParams().getOrDefault(
        "tag", List.of());
```

Đây là snippet cần imports `List` và một `ServerRequest request`. Không nối raw query string vào SQL. Nếu dùng R2DBC, hãy dùng parameter binding của repository/database API, không tự ghép SQL từ request.

### 4.3. Headers

```java
String traceId = request.headers().firstHeader("X-Request-Id");

List<MediaType> accepted = request.headers().accept();

Optional<MediaType> contentType =
    request.headers().contentType();
```

Không tin một header do client đặt tên `X-Admin`/`X-Role` để phân quyền. Authentication phải dựa trên trusted credentials/context với Spring Security; business authorization còn phải kiểm tra resource ownership.

### 4.4. JSON body thành Mono<DTO>

```java
Mono<CreateNote> command =
    request.bodyToMono(CreateNote.class);
```

Điểm quan trọng:

- Đây là **Publisher**, việc decode body xảy ra theo reactive processing.
- Body rỗng có thể thành `Mono.empty()`. Với POST yêu cầu body, cần error contract 400 rõ ràng.
- JSON sai cú pháp thường dẫn tới codec decode error, không phải empty thành công.
- Media type không hỗ trợ có thể gây lỗi tương ứng; cần test status trong ứng dụng thực.
- Body stream bình thường không được đọc hai lần tùy ý; request body là tài nguyên stream theo subscription.
- Không dùng `request.bodyToMono(CreateNote.class).block()` trên WebFlux event loop.

### 4.5. Nhiều objects bằng bodyToFlux

Nếu client gửi streaming JSON/NDJSON phù hợp:

```java
Flux<Event> events = request.bodyToFlux(Event.class);
```

Từng item được emit theo codec/media type phù hợp. **Một HTTP JSON array** và **NDJSON stream** có buffering/codec/flush semantics khác nhau, dù cùng có thể decode thành Flux. Không xây API nhập một triệu items mà dùng `collectList()` vô giới hạn trước khi validate; cân nhắc limits, backpressure, batching, timeout và xử lý lỗi giữa stream.

### 4.6. ParameterizedTypeReference

Với generic type:

```java
Mono<List<CreateNote>> items =
    request.bodyToMono(new ParameterizedTypeReference<
        List<CreateNote>>() {});
```

Cần import `org.springframework.core.ParameterizedTypeReference`. Nếu dùng `List<CreateNote>` làm input, việc decode một JSON array thành toàn List có thể giữ dữ liệu lớn trong memory; đặt request body size limits và kiểm tra codec buffer limits. Nếu muốn stream dài, thiết kế input media type phù hợp và dùng Flux.

## 5. ServerResponse: status, header, cookie, body và completion

`ServerResponse` là interface theo immutable contract. Thông thường lấy builder từ các static factories rồi gọi phương thức kết thúc **trả Mono<ServerResponse>**.

| Mục tiêu | WebFlux.fn |
| --- | --- |
| 200 với object có sẵn | `ServerResponse.ok().bodyValue(note)` |
| 200 JSON từ Flux | `ServerResponse.ok().contentType(APPLICATION_JSON).body(flux, Note.class)` |
| 201 + Location | `ServerResponse.created(uri).bodyValue(note)` |
| 202 Accepted | `ServerResponse.accepted().build()` |
| 204 No Content | `ServerResponse.noContent().build()` |
| 400 Bad Request | `ServerResponse.badRequest().bodyValue(error)` |
| 404 Not Found | `ServerResponse.notFound().build()` |
| 409 Conflict | `ServerResponse.status(HttpStatus.CONFLICT).bodyValue(error)` |
| 429 Too Many Requests | `ServerResponse.status(TOO_MANY_REQUESTS).bodyValue(error)` |
| 500 Internal Error | `ServerResponse.status(INTERNAL_SERVER_ERROR).build()` |

### 5.1. bodyValue: có một giá trị sẵn

```java
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(Map.of("status", "ready"));
```

Phương thức `bodyValue` dùng với object có sẵn, như DTO/Map/String. **Không đưa một `Mono<Note>` vào bodyValue và mong Reactor tự serialize dữ liệu khi Mono complete**. Với publisher, dùng method `body(publisher, type)`.

### 5.2. body: một Publisher có thể phát nhiều values

```java
Flux<Note> notes = repository.findAll();

return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(notes, Note.class);
```

Đây là snippet khi đã có reactive repository phù hợp. Spring dùng message writers/codecs để serialize items. Với media type `application/json` và Flux hữu hạn, output thường là **JSON array**. **Không khẳng định** từng item flush ngay lập tức như SSE khi đang ở `application/json`.

Một Mono:

```java
Mono<Note> note = repository.findById(id);
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(note, Note.class);
```

Nếu `note` empty, HTTP status ở ví dụ vẫn đã chọn 200; muốn 404 theo nghiệp vụ, hãy đợi source bằng `flatMap` trước khi tạo ServerResponse, như phần tiếp theo.

### 5.3. Trả 404 theo giá trị reactive

```java
return repository.findById(id)
    .flatMap(note ->
        ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(note))
    .switchIfEmpty(ServerResponse.notFound().build());
```

Đây là idiom WebFlux.fn rất quan trọng: **lựa chọn response phụ thuộc vào Mono có item hay empty**. Với source error, lỗi propagate, không tự biến thành 404.

### 5.4. 201 + Location khi tạo resource

```java
return service.create(command)
    .flatMap(saved ->
        ServerResponse.created(
            URI.create("/api/notes/" + saved.id()))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(saved));
```

`ServerResponse.created(uri)` set status 201 và header Location. URI tương đối dùng được trong demo; trên production cần xét host/context path/reverse proxy nếu client cần absolute Location. Không lấy Host header không tin cậy rồi lắp thẳng thành Location/open redirect.

### 5.5. 204 không có body

```java
return ServerResponse.noContent().build();
```

Khi DELETE thành công, 204 là HTTP status phù hợp nếu contract không trả body. `Mono<ServerResponse>` ở đây **phát một descriptor** có status 204, còn response body không có phần tử.

### 5.6. Header và cookie

```java
return ServerResponse.ok()
    .header("X-API-Version", "1")
    .cacheControl(CacheControl.noCache())
    .bodyValue(Map.of("ok", true));
```

Đây là snippet có `CacheControl`/`Map` imports. Set-Cookie có API `cookie(ResponseCookie)` trên builder, nhưng session/auth cookies phải có Secure, HttpOnly, SameSite, path/domain policy phù hợp. `Cache-Control: no-cache` cho phép cache lưu với revalidation, khác `no-store`.

## 6. Khi nào ServerResponse thực sự được gửi?

Một số thời điểm khác nhau:

1. Controller/Handler **assembly**: ghép Reactor pipeline, có thể chưa gọi service hay đọc body.
2. Subscription: framework bắt đầu xử lý Publisher.
3. `Mono<ServerResponse>` emit descriptor: framework biết HTTP status/headers/body publisher.
4. `ServerResponseResultHandler` ghi response qua codecs khi body Publisher emit.
5. Body complete/error/cancel; HTTP exchange kết thúc hoặc bị ngắt.

```text
Handler assembly             Mono<ServerResponse>
                                    │
Subscription                        ▼
Service I/O async             emits ServerResponse
                                    │
Response headers/status             ▼
HTTP writer subscribes        Flux<Note> body
                                    │
Output                             onNext() onNext() onComplete()
```

**Không nhầm “đã tạo Mono” với “đã thực sự gửi HTTP response”.** Nếu side effect tạo/ghi DB nằm trong một reactive Publisher, nó thường chỉ diễn ra khi subscription tương ứng được kích hoạt. Ngược lại, side effect thực hiện ngay bên ngoài factory (như `Mono.just(blockingCall())`) vẫn chạy **trước** subscription.

### 6.1. Eager vs lazy

Sai về lazy I/O:

```java
Mono<Note> note = Mono.just(
    legacyBlockingRepository.load(id)); // load chạy ngay
```

Cách trì hoãn call:

```java
Mono<Note> note =
    Mono.fromCallable(() -> legacyBlockingRepository.load(id));
```

Nhưng `fromCallable` chỉ làm việc lazy, **không làm JDBC blocking thành non-blocking**. Nếu buộc phải bridge synchronous API:

```java
Mono<Note> note =
    Mono.fromCallable(() -> legacyBlockingRepository.load(id))
        .subscribeOn(Schedulers.boundedElastic());
```

Đây là các **snippets giải thích** cần variables/classes thật, không phải chương trình standalone. `boundedElastic` có giới hạn threads/queue và vẫn phải cấu hình DB pool, timeouts; dùng R2DBC/reactive driver mới có non-blocking I/O đúng nghĩa.

### 6.2. Không gọi subscribe trong HandlerFunction để “chạy nhanh hơn”

Anti-pattern:

```java
Mono<ServerResponse> create(ServerRequest request) {
    service.save(data).subscribe(); // fire-and-forget mất HTTP lifecycle
    return ServerResponse.accepted().build();
}
```

Dễ gây duplicate execution, không biết success/failure, không propagate cancellation và làm response “accepted” trước khi xác nhận sự kiện nghiệp vụ. Nếu mục tiêu thật là async job, cần durable queue/job store và HTTP 202 contract; nếu muốn hoàn tất save rồi trả 201, hãy compose bằng `flatMap` hoặc `then`, không subscribe thủ công.

## 7. HTTP status, empty và error: ba lớp dễ bị nhầm

### 7.1. Router không match

`RouterFunction.route(request)` có thể complete empty vì predicate path/method/media không match. Framework sẽ thử những router/handler mappings khác. Nếu không có handler phù hợp, HTTP có thể kết thúc 404 hoặc một status mapping-specific.

### 7.2. Handler match nhưng business source empty

`repository.findById(id)` trả `Mono.empty()` biểu thị không tìm thấy entity. Handler có trách nhiệm mapping sang HTTP 404 hoặc kết quả thay thế phù hợp. **Không** return `Mono.empty()` như một response descriptor rồi dựa vào Framework suy ra 404.

### 7.3. Handler source error

`repository.findById` trả `Mono.error(ex)` nghĩa là **thất bại**, không phải “dữ liệu không tồn tại”. Lỗi có thể đi qua WebFlux exception handling và thành 4xx/5xx theo policy. Tránh:

```java
return repository.findById(id)
    .onErrorResume(error -> Mono.empty());
```

vì có thể biến lỗi database thành HTTP 404 và che giấu outage. Với lỗi nghiệp vụ đã được phân loại (ví dụ `DuplicateKeyException`), có thể map thành 409; với lỗi không biết, giữ 5xx và log nội bộ an toàn.

### 7.4. Mô hình quyết định

| Tình huống | Reactive stream | HTTP contract đề nghị |
| --- | --- | --- |
| Không có route GET | Router Mono.empty | 404 hoặc status theo mapping infrastructure |
| GET ID tồn tại | Mono<Note> phát note | 200 + JSON |
| GET ID không tồn tại | Mono<Note> empty | Handler trả 404 |
| POST không có body | request.bodyToMono empty | Handler trả 400 |
| POST title trống | DTO hợp lệ JSON nhưng invalid business | Handler trả 400 |
| POST thành công | Service Mono<Note> phát saved note | 201 + Location |
| DELETE thành công | Service Mono<Boolean> true | 204 |
| DELETE ID không tồn tại | Service Mono<Boolean> false | 404/204 theo hợp đồng đã định |
| Service không kết nối DB | Mono.error | 5xx + monitoring; không giả 404 |
| Stream đã bắt đầu rồi lỗi | Flux phát một số items rồi error | Status có thể đã commit; protocol error/close |

## 8. RouterFunction: chọn handler một cách có thứ tự

Spring có builder `RouterFunctions.route()`:

```java
@Bean
RouterFunction<ServerResponse> routes(NoteHandler h) {
    return RouterFunctions.route()
        .GET("/api/notes/search", h::search)
        .GET("/api/notes", h::list)
        .GET("/api/notes/{id}", h::get)
        .POST("/api/notes", h::create)
        .DELETE("/api/notes/{id}", h::delete)
        .build();
}
```

WebFlux.fn **xét các routes theo thứ tự khai báo**. Đường `/api/notes/search` phải đứng trước `/api/notes/{id}`, vì path variable có thể nhận chuỗi `"search"`. Khác annotated controller mapping thường chọn most specific method.

### 8.1. RequestPredicate

Route có thể match HTTP method, path, Accept và Content-Type:

```java
return RouterFunctions.route()
    .GET("/api/notes", RequestPredicates.accept(
         MediaType.APPLICATION_JSON), handler::list)
    .POST("/api/notes", RequestPredicates.contentType(
         MediaType.APPLICATION_JSON), handler::create)
    .build();
```

Đây là snippet thay thế route definition ở trên, không phải file độc lập. `Accept` yêu cầu representation client muốn nhận; `Content-Type` diễn đạt format body client gửi. Đặt predicate quá chặt khiến request bị **không match** thay vì decode fail; status thực có thể phụ thuộc các mappings khác và WebFlux exception infrastructure. Test behavior end-to-end.

### 8.2. Nested routes

```java
return RouterFunctions.route()
    .path("/api/notes", b -> b
        .GET("/search", handler::search)
        .GET(handler::list)
        .GET("/{id}", handler::get)
        .POST(handler::create)
        .DELETE("/{id}", handler::delete))
    .build();
```

Đây là cấu hình **thay thế**, không đăng ký song song với non-nested bean cùng URL. Nested route giảm lặp prefix. Với route root path hoặc slash, cần test actual matcher; không giả định mọi dấu gạch chéo được chuẩn hóa giống nhau.

### 8.3. Composition

Có thể ghép:

```java
RouterFunction<ServerResponse> apiRoutes = ...;
RouterFunction<ServerResponse> adminRoutes = ...;

RouterFunction<ServerResponse> all =
    apiRoutes.and(adminRoutes);
```

Các snippets trên chỉ mô tả composition khi hai router variables có sẵn. Thứ tự vẫn quan trọng, đặc biệt với `/**` hoặc wildcard quá rộng. Spring Boot có thể detect nhiều RouterFunction beans, order/combine qua `RouterFunctionMapping`; nếu có cùng URL, định nghĩa ownership/order rõ và viết regression tests.

### 8.4. Một router không phải business service

Lựa chọn tốt là RouterFunction chỉ điều phối method/path/predicate; handler đọc request, validate, gọi service; service làm business/transaction; repository làm data access. Không nhét một màn hình CRUD lớn vào một lambda route vài trăm dòng rồi khó test.

## 9. HandlerFilterFunction, trước/sau xử lý và WebFilter

Functional middleware:

```java
.filter((request, next) -> {
    String blocked = request.headers()
        .firstHeader("X-Demo-Flow");
    if ("reject".equals(blocked)) {
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
            .bodyValue(Map.of("error", "demo_filtered"));
    }
    return next.handle(request);
})
```

Trong WebFlux.fn, `next.handle(request)` trả `Mono<ServerResponse>`; filter có thể short-circuit **bằng cách không gọi next**. Đây là ví dụ quan sát middleware, **không phải rate limiting hoặc security thật**. Header `X-Demo-Flow` do client tự đặt, tuyệt đối không dùng để xác thực hay phân quyền.

### 9.1. Filter là bất đồng bộ

Một handler filter có thể compose:

```java
.filter((request, next) ->
    next.handle(request)
        .doOnNext(response -> {
            // lightweight diagnostics; response descriptor có sẵn
        }))
```

Snippet cần context builder, nên không standalone. Không gọi `.block()` trong filter, không đặt network I/O sync trong callback. Khi cần xử lý exception, có thể dùng Reactor `onErrorResume` với filter policy rõ hoặc framework `WebExceptionHandler`, nhưng không nuốt toàn bộ lỗi tùy tiện.

### 9.2. before và after

Router builder có các hooks:

```java
.before(request -> ServerRequest.from(request)
    .header("X-Internal-Marker", "demo")
    .build())
.after((request, response) -> {
    // metrics/logging nhẹ, không ghi dữ liệu nhạy cảm
})
```

Các đoạn trên là code minh họa trong RouterFunction builder; scope tùy root/nested builder. Với API WebFlux version cụ thể, kiểm tra lifecycle và không mutate request object bất biến trực tiếp.

### 9.3. So sánh layers

| Công cụ | Layer | Khi nào dùng |
| --- | --- | --- |
| `HandlerFilterFunction` | Một RouterFunction hoặc route group | Pre/post hoặc short-circuit logic gắn nhóm routes |
| `WebFilter` | WebFlux WebHandler chain | Request ID, logging, security integration, CORS |
| `WebExceptionHandler` | Reactive exception handling | Error response trước commit |
| `SecurityWebFilterChain` | Reactive Spring Security | Authentication/authorization/CSRF |
| Servlet `Filter` | Servlet container/MVC | **Không phải** cơ chế trung tâm khi chạy WebFlux trên Reactor Netty |
| MVC `HandlerInterceptor` | Servlet MVC handler chain | Không dùng cho WebFlux.fn trên Netty |

Spring WebFlux functional endpoints dùng `CorsWebFilter` khi cần CORS; không dựa vào `@CrossOrigin` trên một Java HandlerFunction như với annotated controllers. CORS là browser policy, không thay authorization.

## 10. Request và response body có backpressure ra sao?

Một reactive request body thường được đọc thành `Flux<DataBuffer>` rồi decode thành Mono/Flux typed objects. Response body `Flux<Note>` được encoder đưa ra socket theo khả năng ghi/HTTP codec. **Reactive Streams có demand** giữa publisher và subscriber; transport HTTP/TCP cũng có buffers/flow control riêng.

```text
Client upload body
   ↓ network packets / Reactor Netty
   ↓ Flux<DataBuffer> request body
   ↓ HttpMessageReader decode
   ↓ request.bodyToFlux(Event.class)
   ↓ Handler/service operators
   ↓ ServerResponse.body(publisher,Event.class)
   ↓ HttpMessageWriter / encoder
   ↓ socket write readiness / client
```

### 10.1. “Publisher” không có nghĩa buffer vô hạn được phép

- `bodyToMono(List.class)` có thể cần aggregate request body lớn.
- `Flux.collectList()` phải đợi complete và giữ toàn bộ items.
- `onBackpressureBuffer()` có thể làm heap tăng không giới hạn nếu không cấu hình.
- Server/proxy/client đều có request body size/time limits cần phối hợp.
- External push producer có thể không tuân thủ demand trọn vẹn; thiết kế overflow policy và metrics.

Spring Boot có property `spring.codec.max-in-memory-size` để điều chỉnh giới hạn codec aggregation trong một số operations. Đây **không phải** một quota RAM tổng của JVM, và không phải mọi stream dài sẽ luôn được buffer toàn bộ.

### 10.2. JSON array và SSE không giống nhau

Với `Content-Type: application/json`, Flux hữu hạn thường encode thành một JSON array. Client có thể chỉ xử lý sau khi array hoàn chỉnh. Nếu cần event streaming, chọn `MediaType.TEXT_EVENT_STREAM` hoặc `application/x-ndjson` khi client/server hỗ trợ:

```java
Flux<String> events = Flux.interval(Duration.ofSeconds(1))
    .map(index -> "tick-" + index);

return ServerResponse.ok()
    .contentType(MediaType.TEXT_EVENT_STREAM)
    .body(events, String.class);
```

Đây là snippet sử dụng trong handler, cần imports `Flux`, `Duration`, `MediaType`. Demo ở mục 14 cố ý giới hạn events để không giữ kết nối mãi. Với SSE, production cần heartbeat, authorization, disconnect/cancel, replay policy, slow-consumer handling và reverse-proxy idle timeout.

### 10.3. Response đã commit và lỗi giữa stream

Khi một HTTP 200 SSE stream đã phát headers và hai events, error thứ ba không thể bảo đảm sửa status thành HTTP 500. Cần xác định protocol-level error event hoặc đóng stream/retry semantics; đừng trả JSON error object chen vào SSE stream mà không có protocol contract.

## 11. Asynchrony, schedulers, ThreadLocal và error handling

### 11.1. Mono/Flux không tự tạo thread mới

Đoạn `Mono.just("ok").map(...)` có thể chạy trên thread đang subscribe. `Flux.range` thường phát synchronously theo demand. Chính source async (WebClient/R2DBC/interval) hoặc scheduler operators thay đổi execution.

- `subscribeOn(scheduler)` ảnh hưởng quá trình subscribe/upstream.
- `publishOn(scheduler)` chuyển execution context cho downstream operators sau nó.
- `Schedulers.boundedElastic()` phù hợp hơn khi cần bridge **blocking** APIs có giới hạn.
- `Schedulers.parallel()` dành cho loại compute work phù hợp, không phải nơi để giữ blocking JDBC thread.

### 11.2. Tránh block()

Anti-pattern trong handler:

```java
Mono<ServerResponse> bad(ServerRequest request) {
    Note note = service.find(1L).block();
    return ServerResponse.ok().bodyValue(note);
}
```

Trên Reactor Netty event loop, `block()` có thể throw `IllegalStateException` hoặc gây event-loop starvation. Sửa:

```java
Mono<ServerResponse> good(ServerRequest request) {
    return service.find(1L)
        .flatMap(note -> ServerResponse.ok().bodyValue(note))
        .switchIfEmpty(ServerResponse.notFound().build());
}
```

Cả hai snippets giả định class có service phù hợp; chỉ method `good` phản ánh idiom nên dùng. Trong main/tests ở boundary imperative có thể dùng `block()` khi chủ động chờ, nhưng không trong reactive request handling.

### 11.3. Context không phải thread-local bất biến

Request có thể đổi thread. Đừng kỳ vọng `ThreadLocal`, MDC, Spring Security context dạng Servlet hay JPA transaction holder luôn có cùng giá trị. Reactor dùng `Context` per-subscriber; Spring Security reactive context propagation có infrastructure riêng. Nếu cần audit user/resource, lấy principal đáng tin cậy và truyền thông tin theo API reactive; không copy tùy tiện mutable SecurityContext qua worker threads.

### 11.4. Error như Publisher signal

Nếu body JSON sai, `bodyToMono(Command.class)` có thể phát `onError` do decode. Nếu service lỗi, `Mono.error(...)` propagate. Có thể dùng `onErrorResume(SpecificBusinessException.class, ex -> ServerResponse.status(...).bodyValue(...))` khi exception được phân loại. Lỗi chưa phân loại cần error handler chung và log có correlation, không leak stack trace cho client.

### 11.5. Timeout và cancellation

`Mono.timeout(Duration.ofSeconds(2))` là timeout của một publisher, khác TCP connect/HTTP client response timeout, pool acquisition timeout và business deadline. Khi timeout/cancel propagate, remote service có thể đã nhận POST, side effects có thể vẫn tiếp tục; dùng idempotency/transaction/retry policy. `doFinally` chạy với complete/error/cancel thích hợp để cleanup, nhưng tránh blocking trong callback.

## 12. Ứng dụng thực tế nào phù hợp WebFlux.fn?

| Bài toán | Có phù hợp không? | Lý do |
| --- | --- | --- |
| REST CRUD với reactive repository | Phù hợp | Compose Mono/Flux theo contract HTTP |
| API proxy bằng WebClient non-blocking | Phù hợp | Không cần giữ worker chờ remote network |
| Event streaming SSE | Phù hợp | Body Publisher nhiều items, cancel/backpressure |
| API toàn bộ JPA/JDBC blocking | Cân nhắc | Reactive wrapper không tự non-blocking |
| HTML SSR với templates | Có thể nhưng cần integration | WebFlux view resolver/Rendering hoặc annotated controllers |
| Legacy Servlet Filter/JSP stack lớn | Có thể MVC thuận lợi hơn | Servlet integration không map 1:1 sang Netty |
| Cần routing khai báo tập trung/composable | Phù hợp | RouterFunction builder dễ nhóm |
| Một vài annotated controllers đơn giản | Không bắt buộc chuyển | WebFlux annotated API cũng được hỗ trợ |
| Long-running background job nhiều phút | Dùng durable job store hơn giữ HTTP | 202+job ID, poll/status/events |

### 12.1. Khi nào nên dùng WebFlux annotated controllers?

Nếu đội đã quen DTO validation qua `@Valid`, `@RequestBody`, handler advice và OpenAPI generators, annotated WebFlux có thể tự nhiên hơn. WebFlux.fn không phải cách duy nhất để hưởng non-blocking I/O. Chọn style theo tính nhất quán và tooling; lựa chọn **WebFlux reactive stack** là quyết định khác với lựa chọn **functional hay annotation routing**.

### 12.2. Validation thủ công trong HandlerFunction

Với functional route, bạn thường nhận DTO qua `bodyToMono` và cần validate cụ thể. Với Jakarta Bean Validation, có thể inject `jakarta.validation.Validator`, gọi `validator.validate(command)` rồi mapping violations. Không mặc nhiên giả định đặt `@Valid` trong record rồi `bodyToMono` tự động trigger method argument validation y hệt annotated `@RequestBody @Valid`.

Với upload/form, APIs của `ServerRequest.formData()`/`multipartData()` có reactive semantics; cần cẩn thận file buffers, kích thước và lifecycle của `Part`.

## 13. Hệ thống gỡ lỗi theo tầng

| Hiện tượng | Nguyên nhân hay gặp | Hướng kiểm tra |
| --- | --- | --- |
| Method trả `ServerResponse` nhưng compiler đòi `Mono<ServerResponse>` | Import WebFlux.fn nhưng dùng signature WebMvc.fn | Return builder Publisher |
| Import `org.springframework.web.servlet.function` sai | Nhầm stack | Chuyển sang `web.reactive.function.server` |
| GET /api/notes/search báo invalid_id | Route `/{id}` đứng trước `/search` | Đặt route static trước variable |
| GET entity không tồn tại lại 200 | Chọn response trước khi `Mono.empty` resolve | Dùng flatMap + switchIfEmpty |
| JSON request empty dẫn tới no response | Không handle Mono.empty | Explicit badRequest |
| `bodyValue(flux)` không hoạt động như kỳ vọng | Nhầm object value với publisher | Dùng `body(flux, Note.class)` |
| Spring Boot chạy DispatcherServlet | Vô tình thêm starter-web | Chỉ starter-webflux cho reactive app |
| Handler không được tìm thấy | RouterFunction không phải Spring bean | Đăng ký @Bean/router config |
| Không serialize được record | Jackson/codec dependency hoặc incompatible DTO | Kiểm tra starter-webflux, model, Content-Type |
| Client gửi `text/plain` tới JSON body route | Media type không phù hợp | Content-Type và codec handlers |
| `onErrorResume` che lỗi DB thành 404 | Nuốt errors | Chỉ convert chính xác business error |
| Infinite Flux với collectList không trả | Chờ completion không đến | Streaming/lấy hữu hạn |
| Callback gọi RestTemplate/JDBC đồng bộ | Event loop block | Dùng WebClient/R2DBC hoặc offload hợp lý |
| Browser CORS error nhưng curl OK | CORS là browser policy | CorsWebFilter, OPTIONS, Security |
| filter không áp route như kỳ vọng | Nested filter scope khác root | Kiểm tra builder composition |
| WebClient timeout dù inbound còn rảnh | Outbound connection pool/pending acquire đầy | Pool metrics, remote latency |
| Test dùng MockMvc cho Netty WebFlux | Nhầm testing stack | Dùng WebTestClient |
| 404 từ router nhưng annotated handler vẫn trả 200 | Mappings có thứ tự khác | Kiểm tra RouterFunctionMapping/DispatcherHandler |
| Header đã được gửi rồi handler gặp lỗi | Response đã committed | Streaming protocol/error contract |
| Số item response không như tưởng | Flux JSON vs SSE writer | Media type, codec, backpressure |

## 14. Demo chạy được: Spring Boot 3 + Java 17 + WebFlux.fn

Mục đích của dự án: **chứng minh ngay ở code và HTTP response rằng hợp đồng WebFlux.fn là bất đồng bộ** nhưng service/middleware vẫn kiểm soát HTTP semantics đúng.

Endpoints:

- `GET /api/notes` → `Flux<Note>`, JSON array.
- `GET /api/notes/search?q=Spring` → Flux lọc từ in-memory service.
- `GET /api/notes/{id}` → Mono<Note> hoặc `ServerResponse.notFound()`.
- `POST /api/notes` → JSON `CreateNote`, 400 khi invalid, 201+Location khi hợp lệ.
- `DELETE /api/notes/{id}` → 204 khi xóa, 404 khi không tồn tại.
- `GET /api/notes/events` → ba SSE events từ Flux timer, chứng minh body Publisher nhiều values.
- `GET /api/ping` → dùng `Mono.defer` minh họa response builder Publisher.
- `GET /annotated/ping` → một annotated WebFlux controller cạnh functional router.
- Functional filter demo `X-Demo-Flow: reject` → 429, không phải security thật.

In-memory data reset khi restart; dùng `ConcurrentHashMap`, `AtomicLong` vì nhiều requests có thể xử lý song song. Đây là sample tự học, **không cung cấp authentication/rate limit production**.

### 14.1. Cấu trúc file

```text
webflux-fn-contract/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── WebFluxFnApplication.java
    │   ├── Note.java
    │   ├── CreateNote.java
    │   ├── NoteService.java
    │   ├── NoteHandler.java
    │   ├── NoteRoutes.java
    │   └── AnnotatedPing.java
    └── test/java/example/
        ├── NoteApiTest.java
        └── NoteServiceTest.java
```

### 14.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>webflux-fn-contract</artifactId>
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
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
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

Không thêm `spring-boot-starter-web` để tránh Boot chuyển sang Servlet MVC mặc định. `reactor-test` mang `StepVerifier`; `starter-test` mang JUnit 5 và WebTestClient testing hỗ trợ.

### 14.3. WebFluxFnApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebFluxFnApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxFnApplication.class, args);
    }
}
```

### 14.4. Note.java và CreateNote.java

File `Note.java`:

```java
package example;

public record Note(long id, String title) {}
```

File `CreateNote.java`:

```java
package example;

public record CreateNote(String title) {}
```

Hai record là DTO contract riêng. Không lấy owner/role từ client-supplied body rồi tin đó là authorization context. Thực tế thêm validation schema, whitespace constraints và giới hạn title.

### 14.5. NoteService.java

```java
package example;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class NoteService {

    private final Map<Long, Note> notes = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(2);

    public NoteService() {
        notes.put(1L, new Note(1L, "Spring WebFlux"));
    }

    public Flux<Note> list(String query) {
        String wanted = query.toLowerCase(Locale.ROOT);
        return Flux.defer(() ->
            Flux.fromIterable(notes.values())
                .filter(note -> note.title()
                    .toLowerCase(Locale.ROOT).contains(wanted))
                .sort(Comparator.comparingLong(Note::id))
        );
    }

    public Mono<Note> find(long id) {
        return Mono.defer(() -> Mono.justOrEmpty(notes.get(id)));
    }

    public Mono<Note> create(CreateNote command) {
        return Mono.fromSupplier(() -> {
            long id = nextId.getAndIncrement();
            Note note = new Note(id, command.title().trim());
            notes.put(id, note);
            return note;
        });
    }

    public Mono<Boolean> delete(long id) {
        return Mono.fromSupplier(() -> notes.remove(id) != null);
    }
}
```

- `Flux.defer`/`Mono.defer` khiến mỗi subscription quan sát snapshot/giá trị tại thời điểm subscribe; code không tự query trước khi subscribe.
- `ConcurrentHashMap` bảo vệ operations map cơ bản và `AtomicLong` cấp ID không trùng trong **một JVM instance**, không phải distributed persistence/transaction.
- `sort(...)` cần buffer toàn sequence. In-memory 1–vài notes không sao; với dữ liệu lớn phải sort/paginate **ở database query**, không đem triệu records về rồi sort trong Flux.
- `create` không được subscribe hai lần nếu muốn tránh tạo hai notes. HTTP framework sẽ quản lý subscription bình thường; service API production cần idempotency key cho POST retried.
- Đây là **cold reactive wrapper trên in-memory operations nhỏ**, không phải bằng chứng có non-blocking DB I/O. Thay bằng R2DBC/reactive repository khi làm hệ thống thật.

### 14.6. NoteHandler.java

```java
package example;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class NoteHandler {

    private final NoteService service;

    public NoteHandler(NoteService service) {
        this.service = service;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        String q = request.queryParam("q").orElse("").trim();

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(service.list(q), Note.class);
    }

    public Mono<ServerResponse> search(ServerRequest request) {
        return list(request);
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        Long id = parseId(request);
        if (id == null) {
            return badId();
        }

        return service.find(id)
            .flatMap(note ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(note))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateNote.class)
            .flatMap(command -> {
                if (command.title() == null ||
                        command.title().isBlank()) {
                    return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "error", "title_required"));
                }

                if (command.title().trim().length() > 120) {
                    return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "error", "title_too_long"));
                }

                return service.create(command)
                    .flatMap(saved -> ServerResponse.created(
                                URI.create("/api/notes/" + saved.id()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
            })
            .switchIfEmpty(ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("error", "body_required")));
    }

    public Mono<ServerResponse> delete(ServerRequest request) {
        Long id = parseId(request);
        if (id == null) {
            return badId();
        }

        return service.delete(id)
            .flatMap(existed -> existed
                ? ServerResponse.noContent().build()
                : ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> events(ServerRequest request) {
        Flux<String> source = Flux.interval(Duration.ofMillis(20))
            .take(3)
            .map(index -> "tick-" + index);

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(source, String.class);
    }

    public Mono<ServerResponse> ping(ServerRequest request) {
        return Mono.defer(() ->
            ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "ready")));
    }

    private Long parseId(ServerRequest request) {
        try {
            long id = Long.parseLong(request.pathVariable("id"));
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Mono<ServerResponse> badId() {
        return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("error", "invalid_id"));
    }
}
```

#### Vì sao handler này thực sự reactive?

Chú ý return type của **mọi** method là `Mono<ServerResponse>`. Handler không trả trực tiếp `ServerResponse`, không gọi `block()` hay `subscribe()`.

- `list()` tạo response descriptor có body `Flux<Note>`; framework subscribe body khi ghi.
- `get()` đợi `service.find(id)`; nếu entity empty mới tạo response 404. Không có phản hồi 200 sai khi dữ liệu không tồn tại.
- `create()` dùng `bodyToMono(CreateNote.class)`, validate rồi `flatMap` sang service create. Khi body empty, `switchIfEmpty` trả 400.
- `delete()` biến Mono<Boolean> thành 204/404 theo quy tắc business.
- `events()` trả response descriptor một lần, body stream thêm ba values từ Flux.
- `ping()` dùng `Mono.defer` để minh họa việc tạo descriptor lúc subscription.

**Chú ý HTTP error:** body JSON sai cú pháp do codec decode có thể gây HTTP 400 qua WebFlux exception handling, nhưng ví dụ chỉ custom `error: body_required/title_required/title_too_long` cho những trường hợp do handler kiểm soát. Đừng hardcode rằng mọi malformed JSON đều có chính xác cùng JSON error schema.

### 14.7. NoteRoutes.java

```java
package example;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class NoteRoutes {

    @Bean
    RouterFunction<ServerResponse> noteRouter(NoteHandler handler) {
        return RouterFunctions.route()
            .GET("/api/notes/search", handler::search)
            .GET("/api/notes/events", handler::events)
            .GET("/api/notes", handler::list)
            .GET("/api/notes/{id}", handler::get)
            .POST("/api/notes", handler::create)
            .DELETE("/api/notes/{id}", handler::delete)
            .GET("/api/ping", handler::ping)
            .filter((request, next) -> {
                if ("reject".equals(request.headers()
                        .firstHeader("X-Demo-Flow"))) {
                    return ServerResponse.status(
                            HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                            "error", "demo_filtered"));
                }
                return next.handle(request);
            })
            .build();
    }
}
```

Đây là router một bean, tránh concurrency/order không rõ giữa nhiều beans. Đặt `/api/notes/search` và `/api/notes/events` trước `/api/notes/{id}`. Functional filter root builder áp dụng mọi routes vừa build (gồm `/api/ping`), nhưng không áp cho annotated controller riêng ở phần sau.

`ServerResponse.status(429)` có thể được trả ngay từ filter; **không có authorization/security thật**. Nếu app công khai, đừng dùng header demo này làm quyết định rate limit, vì client có thể tự gửi hoặc bỏ header.

### 14.8. AnnotatedPing.java

```java
package example;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
public class AnnotatedPing {

    @GetMapping("/annotated/ping")
    public Mono<Map<String, String>> ping() {
        return Mono.just(Map.of("style", "annotated-webflux"));
    }
}
```

Spring Boot WebFlux có thể vận hành `RouterFunction` và `@RestController` song song trên cùng `DispatcherHandler`. Không khai báo overlap giữa hai styles trừ khi có chính sách handler mapping order rõ. Functional route nhận `Mono<ServerResponse>`; annotated REST route nhận `Mono<DTO>` và để HTTP writer xử lý.

### 14.9. Chạy chương trình

Trong thư mục chứa `pom.xml`, với JDK 17+, Maven và khả năng tải dependencies:

```bash
mvn test
mvn spring-boot:run
```

Kỳ vọng Netty server chạy trên port 8080 khi chưa đổi cấu hình. Đó là **kỳ vọng cấu hình mặc định**, không phải lời khẳng định bài kiểm thử đã chạy trong môi trường hiện tại.

Đọc note seed:

```bash
curl -i http://localhost:8080/api/notes/1
```

HTTP 200 và body JSON cấu trúc:

```json
{"id":1,"title":"Spring WebFlux"}
```

Đọc không tồn tại:

```bash
curl -i http://localhost:8080/api/notes/999
```

HTTP 404; body có thể do framework tạo hoặc empty, không assert raw error format.

Danh sách:

```bash
curl -i http://localhost:8080/api/notes
```

HTTP 200:

```json
[{"id":1,"title":"Spring WebFlux"}]
```

Route static search:

```bash
curl -i "http://localhost:8080/api/notes/search?q=Spring"
```

Kỳ vọng HTTP 200 với một note. Nếu đổi thứ tự router cho `/{id}` lên trước `/search`, chuỗi `search` có thể bị parse sai dẫn tới 400, vì vậy thứ tự là một phần của behavior.

Create:

```bash
curl -i -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title":"Reactor API"}'
```

HTTP 201, `Location: /api/notes/2` trong app mới khởi động, body:

```json
{"id":2,"title":"Reactor API"}
```

Nếu concurrent requests đã tạo notes, ID không còn nhất thiết là 2. Client thực cần đọc Location header.

Request body rỗng hoặc title blank:

```bash
curl -i -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{}'

curl -i -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

Cả hai đều có thể đến handler với `CreateNote.title() == null` hoặc blank, trả HTTP 400 với `error: title_required`. Nếu gửi **hoàn toàn không có request body**, `bodyToMono` empty và handler trả `body_required`.

Xóa ID mới:

```bash
curl -i -X DELETE http://localhost:8080/api/notes/2
```

Nếu ID 2 tồn tại, kết quả là 204 không body. Gọi DELETE lại thì 404 theo policy demo. Trong một API khác, DELETE idempotent có thể trả 204 cho cả hai; **idempotent semantics không yêu cầu status phải giống nhau** khi lặp request, mà yêu cầu intended side effect ổn định.

Test filter:

```bash
curl -i -H "X-Demo-Flow: reject" \
  http://localhost:8080/api/notes
```

HTTP 429:

```json
{"error":"demo_filtered"}
```

SSE:

```bash
curl -N -i -H "Accept: text/event-stream" \
  http://localhost:8080/api/notes/events
```

Response Content-Type `text/event-stream`, body có dữ liệu tương ứng các ticks 0,1,2. Event formatting/chunk boundaries do codecs và transport quyết định; không assert chunk bytes như một contract API nếu không thiết kế event schema rõ.

Annotated endpoint:

```bash
curl -i http://localhost:8080/annotated/ping
```

Body JSON:

```json
{"style":"annotated-webflux"}
```

Các lệnh curl dùng syntax shell POSIX. Trên PowerShell dùng `curl.exe` và điều chỉnh nối dòng.

## 15. Test WebTestClient: kiểm chứng HTTP contract cuối cùng

Dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` và `@AutoConfigureWebTestClient` để kiểm thử embedded WebFlux server, codecs, RouterFunction bean và handler thực. Không dùng MockMvc để suy kết luận về Reactor Netty trong demo này.

File `src/test/java/example/NoteApiTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class NoteApiTest {

    @Autowired
    WebTestClient client;

    @Test
    void seededItemIsReadable() {
        client.get().uri("/api/notes/1")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON)
            .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.title").isEqualTo("Spring WebFlux");
    }

    @Test
    void missingEntityIsExplicit404() {
        client.get().uri("/api/notes/999999")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void invalidPathVariableIs400() {
        client.get().uri("/api/notes/not-number")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
                .jsonPath("$.error").isEqualTo("invalid_id");
    }

    @Test
    void listEmitsJsonArray() {
        client.get().uri("/api/notes")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$[0].id").isEqualTo(1);
    }

    @Test
    void staticSearchRoutePrecedesIdRoute() {
        client.get().uri("/api/notes/search?q=Spring")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$[0].title").isEqualTo("Spring WebFlux");
    }

    @Test
    void noSearchResultsIsAnEmptyArray() {
        client.get().uri("/api/notes/search?q=NoMatchAtAll")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    @Test
    void blankTitleIs400() {
        client.post().uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"title\":\"   \"}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
                .jsonPath("$.error").isEqualTo("title_required");
    }

    @Test
    void createReturns201LocationAndGetWorks() {
        String location = client.post().uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"title\":\"New note\"}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
                .jsonPath("$.title").isEqualTo("New note")
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        assertNotNull(location);
        assertTrue(location.startsWith("/api/notes/"));

        client.get().uri(location)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.title").isEqualTo("New note");
    }

    @Test
    void deletingCreatedItemReturns204Then404() {
        String location = client.post().uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"title\":\"Delete me\"}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        assertNotNull(location);

        client.delete().uri(location)
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        client.get().uri(location)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void functionalFilterCanShortCircuit() {
        client.get().uri("/api/notes")
            .header("X-Demo-Flow", "reject")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
                .jsonPath("$.error").isEqualTo("demo_filtered");
    }

    @Test
    void pingReturnsReactiveResponseBuilder() {
        client.get().uri("/api/ping")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.status").isEqualTo("ready");
    }

    @Test
    void annotatedHandlerCanCoexistWithFunctionalRouter() {
        client.get().uri("/annotated/ping")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.style").isEqualTo("annotated-webflux");
    }

    @Test
    void eventStreamUsesSseMediaType() {
        client.get().uri("/api/notes/events")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(
                MediaType.TEXT_EVENT_STREAM);
    }
}
```

**13 HTTP tests** trong class trên kiểm tra đúng route, status và JSON fields. Test create lấy Location từ response, không hardcode ID 2 để tránh phụ thuộc thứ tự tests. Nhưng controller state ở @SpringBootTest context dùng chung nên một production suite lớn nên có cleanup/isolation thêm, hoặc tạo app context riêng. Không assert số thread/event-loop vì đó là detail runtime.

### 15.1. Test RouterFunction riêng mà không mở server

Spring WebFlux cho phép `WebTestClient.bindToRouterFunction(router).build()` để test router/handler mà không start real network listener.

Snippet:

```java
WebTestClient client =
    WebTestClient.bindToRouterFunction(noteRouter).build();

client.get().uri("/api/ping")
    .exchange()
    .expectStatus().isOk();
```

Cần RouterFunction bean `noteRouter` đã được tạo/truyền vào method test và imports phù hợp. Cách này hữu ích cho unit/integration mức routing; test embedded Netty ở trên bổ sung validation của server integration/auto-configuration.

### 15.2. Test SSE sâu hơn

Để verify **ba events thực**, không chỉ media type, có thể dùng:

```java
FluxExchangeResult<String> result =
    client.get().uri("/api/notes/events")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(String.class);

StepVerifier.create(result.getResponseBody())
    .expectNext("tick-0", "tick-1", "tick-2")
    .verifyComplete();
```

Đây là snippet cần imports `FluxExchangeResult`, `reactor.test.StepVerifier`, `MediaType`, và biến `client` có sẵn. Behavior decode SSE dưới dạng String có thể phụ thuộc codec/accept setup; nếu có sự khác biệt, ưu tiên return body dưới kiểu `ServerSentEvent<String>` và assert `event.data()` theo negotiated SSE representation. Dù là test stream, tránh chạy vô hạn và đợi không có timeout.

## 16. StepVerifier: test đúng reactive completion, empty và side effect

HTTP tests đảm bảo ứng dụng trả status/body; Reactor tests kiểm tra Publisher signals, cũng hữu ích để chứng minh không có hidden blocking `get()` trong code.

File `src/test/java/example/NoteServiceTest.java`:

```java
package example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteServiceTest {

    private NoteService service;

    @BeforeEach
    void setup() {
        service = new NoteService();
    }

    @Test
    void findExistsEmitsOneValueThenCompletes() {
        StepVerifier.create(service.find(1L))
            .expectNext(new Note(1L, "Spring WebFlux"))
            .verifyComplete();
    }

    @Test
    void findMissingCompletesEmptyNotError() {
        StepVerifier.create(service.find(777L))
            .verifyComplete();
    }

    @Test
    void listEmitsOneSeedItem() {
        StepVerifier.create(service.list("spring"))
            .expectNext(new Note(1L, "Spring WebFlux"))
            .verifyComplete();
    }

    @Test
    void createIsLazyAndDoesNotRunBeforeSubscribe() {
        var publisher = service.create(new CreateNote("Lazy"));
        StepVerifier.create(service.list("lazy"))
            .verifyComplete();

        StepVerifier.create(publisher)
            .assertNext(note -> {
                assertEquals(2L, note.id());
                assertEquals("Lazy", note.title());
            })
            .verifyComplete();
    }

    @Test
    void deleteIsFirstTrueThenFalse() {
        StepVerifier.create(service.delete(1L))
            .expectNext(true)
            .verifyComplete();

        StepVerifier.create(service.delete(1L))
            .expectNext(false)
            .verifyComplete();
    }
}
```

**5 Reactor StepVerifier tests** xác nhận empty không phải error, việc tạo note lazy và kết quả delete tường minh. Điều đó không chứng minh throughput cao/low latency trên Netty; cần load test, profiling và fake/real reactive I/O drivers để đo năng lực thực.

## 17. Hướng phát triển lên service production

Một API thật cần thêm một số lớp, không copy demo in-memory nguyên xi:

### 17.1. Persistence

Dùng `ReactiveCrudRepository<NoteEntity,Long>`/R2DBC khi muốn reactive relational database. Với tài nguyên phân trang, viết query có LIMIT/cursor thay vì `findAll().collectList()` trên toàn bảng. Với transaction, dùng reactive transaction integration và kiểm tra context propagation.

### 17.2. Security và ownership

Spring Security WebFlux cấu hình `SecurityWebFilterChain`. Route-level `HandlerFilterFunction` có thể bổ sung checks domain-specific nhưng **không** thay authentication, CSRF, authorization theo principal và resource/tenant. Trên API POST/DELETE, client không nên được cấp quyền qua custom X-Role header.

### 17.3. HTTP preconditions và caching

Nếu cần ETag/Last-Modified để revalidate GET, có thể trả appropriate headers/conditional response dựa trên stable resource version. Đừng hash toàn body đắt đỏ rồi cho rằng DB computation đã được tránh; early validator từ metadata mới cho phép short circuit. Với user-specific responses, xem `private/no-store` và Vary/Accept-Encoding.

### 17.4. Observability

Ghi các metrics: route match/status/error count, inbound active requests, event loop lag, outbound WebClient pool active/pending, downstream latency, Reactor cancellation, buffer pressure, retry/backoff, p95/p99. Với tracing, dùng Reactor Context và Spring observability integration thay vì tự tin mọi ThreadLocal MDC được truyền tự động.

### 17.5. Tests ngoài WebTestClient

Thêm tests cho request JSON malformed, Content-Type mismatch, missing body, unknown/delete race, SSE complete/error/cancel, CORS preflight, CSRF, auth/role/tenant and large-body limits. Load test với proxy/TLS/HTTP2 nếu deploy có những layers ấy. Không chỉ assert 200/201 mà còn thử error path, timeout và idempotency key.

## 18. Lỗi phổ biến: từ signature đến production

| Hiểu nhầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “WebFlux.fn HandlerFunction trả ServerResponse” | Nó trả Mono<ServerResponse> | Đúng reactive signature |
| “WebFlux RouterFunction trả Optional” | Mono<HandlerFunction> | Phân biệt WebMvc.fn |
| “Mono<ServerResponse> tức response body một item” | Descriptor có body Publisher 0..N | Tách response và body lifecycle |
| “Router Mono.empty tương đương entity not found” | Hai giai đoạn khác nhau | Route match và service lookup tách |
| “bodyToMono trả ngay DTO” | Nó trả Publisher | flatMap/map |
| “bodyToFlux luôn có nhiều items” | Flux có thể empty hoặc chỉ một item | 0..N |
| “ServerResponse.ok().bodyValue(flux) tự subscribe Flux” | bodyValue dành cho value object | Dùng body(flux, Class) |
| “Mono.empty của service tự cho 404” | Status chưa được quyết định | switchIfEmpty(notFound) |
| “Mono.error cũng là không có dữ liệu” | Error khác empty | Propagate/map error đúng |
| “Thêm @RestController cho HandlerFunction” | RouterFunction bean mới là mapping | Đăng ký router và handler |
| “Chỉ cần starter-webflux thì chắc chắn reactive” | starter-web có thể khiến Boot chọn MVC | Kiểm tra dependencies/context |
| “Route search có thể đặt sau {id}” | First match wins | Static route trước |
| “Request body đọc đi đọc lại được” | Stream không auto-replay | Decode một lần rồi reuse DTO |
| “Mono.just(blocking()) là lazy” | Argument eager | defer/fromCallable và scheduler |
| “Mono.fromCallable làm JDBC non-blocking” | Chỉ trì hoãn execution | Offload hoặc dùng driver reactive |
| “Gọi block() trong Netty là bình thường” | Block event loop/throw exception | Compose reactive operations |
| “Gọi subscribe() trong handler để save nhanh” | Lifecycle/sai error/retry handling | Return/combine Publisher |
| “Stream có backpressure là không bao giờ OOM” | Hot sources/buffers vẫn có giới hạn | Bounded resource policy |
| “Flux JSON array flush theo mỗi onNext” | Codec/media type buffering khác | SSE/NDJSON khi cần |
| “HTTP 200 SSE sau commit đổi thành 500 tùy ý” | Headers đã commit | Event-level error/close |
| “Filter đọc X-Role là security” | Header client giả được | SecurityWebFilterChain/authz |
| “Có ít event loop threads thì response miễn phí” | CPU/memory/buffer/conn vẫn tốn | Profile và cap concurrency |
| “CORS tương đương authorization” | CORS là browser policy | AuthN/AuthZ riêng |
| “Reactor Context là HttpSession” | Khác scope/lifecycle | Context/principal đúng |
| “Retry POST tự động an toàn” | Có thể duplicate side effects | Idempotency key và dedupe |
| “WebTestClient pass chứng minh Netty nhanh” | Chỉ xác minh correctness | Load/performance tests |
| “R2DBC mọi query đều phát từng row không buffer” | Driver/query/operator có thể aggregate | Đo data flow, pagination |

## 19. Checklist thiết kế WebFlux.fn

Trước khi hoàn thiện một API:

1. [ ] Import đúng `org.springframework.web.reactive.function.server`.
2. [ ] Mọi HandlerFunction trả `Mono<ServerResponse>`; không block và không subscribe thủ công.
3. [ ] RouterFunction bean được Boot/WebFlux detect; không trùng route annotated.
4. [ ] Các routes cụ thể trước `/{id}` và wildcard.
5. [ ] RequestPredicate phân biệt method/path/Accept/Content-Type rõ.
6. [ ] Path/query/header parsing và input validation có error contract.
7. [ ] Body decoded bằng `bodyToMono`/`bodyToFlux` một lần phù hợp.
8. [ ] Chọn `bodyValue` cho một value, `body(publisher,type)` cho reactive body.
9. [ ] Status 404/400/201+Location/204 được tạo **tường minh**.
10. [ ] Không nuốt lỗi DB/network thành `Mono.empty()`.
11. [ ] Service reactive end-to-end hoặc blocking bridge được offload/giới hạn.
12. [ ] Streaming media type, client cancellation và backpressure được thiết kế.
13. [ ] Filters functional/WebFilter/SecurityWebFilterChain đặt đúng layer.
14. [ ] CSRF/CORS/authentication/authorization theo trust boundary.
15. [ ] Connections, buffers, concurrency, timeouts, retries và event-loop latency có metric.
16. [ ] Tests kiểm tra success, invalid, missing, empty, error và stream.
17. [ ] POST retries và DELETE semantics/idempotency có chính sách.
18. [ ] Domain service tách khỏi RouterFunctions và DTO tách khỏi persistence entities.
19. [ ] Actual Spring Boot application type là reactive chứ không vô tình MVC.
20. [ ] Kết quả performance được đo chứ không suy từ việc class return Mono.

## 20. Câu hỏi ôn tập có đáp án

**1.** `HandlerFunction<ServerResponse>` trong WebFlux.fn trả kiểu gì?  
**Đáp án:** `Mono<ServerResponse>`.

**2.** `RouterFunction<ServerResponse>.route` có kiểu kết quả gì khi match?  
**Đáp án:** `Mono<HandlerFunction<ServerResponse>>` phát một handler.

**3.** Không match một route được biểu diễn ra sao?  
**Đáp án:** Routing publisher complete empty để framework thử mapping khác.

**4.** Package ServerRequest WebFlux.fn?  
**Đáp án:** `org.springframework.web.reactive.function.server.ServerRequest`.

**5.** Package ServerRequest WebMvc.fn?  
**Đáp án:** `org.springframework.web.servlet.function.ServerRequest`.

**6.** Đọc JSON body thành DTO trong WebFlux.fn dùng gì?  
**Đáp án:** `request.bodyToMono(Dto.class)`.

**7.** Khi body chứa nhiều records, dùng gì?  
**Đáp án:** `request.bodyToFlux(Record.class)` nếu codec/media type phù hợp.

**8.** Tạo response có một DTO sẵn dùng gì?  
**Đáp án:** `ServerResponse.ok().bodyValue(dto)`.

**9.** Tạo response body Flux dùng gì?  
**Đáp án:** `ServerResponse.ok().body(flux, Item.class)`.

**10.** `Mono<ServerResponse>` nghĩa response chỉ có một body record không?  
**Đáp án:** Không; ServerResponse descriptor có thể bọc Flux 0..N.

**11.** Service `Mono.empty()` sau khi route match có tự là HTTP 404 không?  
**Đáp án:** Không, handler phải chọn status bằng switchIfEmpty.

**12.** Response cho create thường có status và header nào?  
**Đáp án:** 201 Created và Location.

**13.** DELETE thành công không trả body nên chọn gì?  
**Đáp án:** `ServerResponse.noContent().build()` cho 204 theo API policy.

**14.** Tại sao `/search` phải trước `/{id}`?  
**Đáp án:** Routes WebFlux.fn được thử theo thứ tự, first-match wins.

**15.** `HandlerFilterFunction` có thể không gọi next không?  
**Đáp án:** Có, nó có thể short-circuit một request.

**16.** `HandlerFilterFunction` có thay Spring Security không?  
**Đáp án:** Không, authorization và auth phải dùng trust context.

**17.** `Mono.just(legacyJDBC.query())` là non-blocking không?  
**Đáp án:** Không, JDBC query đã chạy trước khi Mono được tạo.

**18.** `Mono.fromCallable` tự làm blocking call thành non-blocking không?  
**Đáp án:** Không, cần scheduler phù hợp hoặc reactive I/O driver.

**19.** Gọi `block()` trên Reactor Netty event loop có vấn đề gì?  
**Đáp án:** Có thể block loop hoặc bị Reactor từ chối.

**20.** Một response 200 SSE đã gửi data có luôn sửa status thành 500 sau lỗi không?  
**Đáp án:** Không khi headers đã committed.

**21.** CORS có thay thế kiểm tra owner/tenant không?  
**Đáp án:** Không; CORS là browser access policy.

**22.** WebTestClient dùng cho gì?  
**Đáp án:** Kiểm thử routing, HTTP status, headers, body và integration của reactive stack.

**23.** `StepVerifier` xác minh điều gì khác WebTestClient?  
**Đáp án:** onNext/onComplete/onError/empty trên Publisher.

**24.** Nếu cần sự kiện liên tục từ Flux, dùng media type nào?  
**Đáp án:** Thường dùng text/event-stream hoặc NDJSON tùy client/protocol.

**25.** Reactive Routing có thay đổi bản chất CPU-heavy task không?  
**Đáp án:** Không, CPU-bound vẫn cần CPU và capacity plan.

## 21. Tài liệu chính thức

- [Spring Framework 6.2 — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): bất đồng bộ của `RouterFunction` và `HandlerFunction`, ServerRequest/ServerResponse, handlers, predicates, routing, filters, resources.
- [Spring Framework 6.2 — WebMvc Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-functional.html): so sánh chữ ký Servlet `Optional`/`ServerResponse` và reactive `Mono`.
- [Spring Framework 6.2 — WebFlux DispatcherHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html): RouterFunctionMapping, HandlerFunctionAdapter và ServerResponseResultHandler.
- [Spring Framework 6.2 — WebFlux WebHandler API](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/web-handler-api.html): HTTP server chain và WebFilter.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html): API HTTP tests cho WebFlux.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): WebFlux auto-configuration và runtime.
- [Project Reactor Reference Guide](https://projectreactor.io/docs/core/release/reference/): Mono/Flux, lazy subscription, schedulers, backpressure, error/cancel.
- [Reactive Streams specification](https://www.reactive-streams.org/): Publisher/Subscriber/Subscription và demand.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): status codes, HTTP request/response, conditional and idempotency semantics.

**Tóm lại:** Functional WebFlux không chỉ là cú pháp lambdas thay `@RestController`. Cốt lõi của nó là **hợp đồng bất đồng bộ hai tầng**: router tạo ra `Mono<HandlerFunction>`, handler tạo ra `Mono<ServerResponse>`, và body của response có thể là một `Publisher` riêng phát 0..N items. Đặt status/headers dựa trên completion/empty/error đúng thời điểm; dùng reactive I/O thực sự, không block event loop, và xác định routes/filters/security theo đúng layer.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-003">Định tuyến theo đường dẫn, method, header và kiểu nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# Định tuyến theo đường dẫn, method, header và kiểu nội dung trong Functional WebFlux

## 1. Mục tiêu bài học

**Spring WebFlux.fn** (functional endpoints) là mô hình khai báo HTTP routing và xử lý request bằng các hàm Java, song song với `@RestController`/`@RequestMapping`. Trong WebFlux.fn, một `RouterFunction<ServerResponse>` tìm `HandlerFunction<ServerResponse>` thích hợp dựa trên **RequestPredicate**: đường dẫn (path), HTTP method, header, `Content-Type`, `Accept`, query param và những điều kiện tùy chỉnh. Route phù hợp đầu tiên thường quyết định handler được chọn.

Bài học này thuộc **Functional WebFlux** của Spring Framework, dùng **Java 17+, Spring Boot 3.x và Spring Framework 6.x**, tập trung vào bốn trục routing: **path, method, header, media type**. Bài có mã ứng dụng WebFlux chạy được, `curl` và `WebTestClient`, cùng các tình huống dễ nhầm khi client gửi sai thông tin.

Sau bài học, bạn cần làm được:

- Hiểu `RouterFunction`, `HandlerFunction`, `ServerRequest`, `ServerResponse`, `RequestPredicate`.
- Viết route theo `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` hoặc `method(HttpMethod)` khi cần.
- Match path tĩnh, path variable, pattern, nested path bằng `.path(...)`, và đọc `request.pathVariable(...)`.
- Dùng `RequestPredicates.headers(...)` để kiểm một hoặc nhiều HTTP headers; phân biệt dữ liệu header với authentication.
- Phân biệt `contentType(MediaType.APPLICATION_JSON)` (loại **request body**) với `accept(MediaType.APPLICATION_JSON)` (loại **response client muốn nhận**).
- Kết hợp predicates bằng `and`, `or`, `negate`; tổ chức thứ tự route để tránh shadowing.
- Biết khi nào mismatch trở thành 404, 405, 406 hoặc 415, tại sao phải kiểm bằng HTTP integration tests thay vì luôn giả định một status.
- Sử dụng nested builder, grouping, test predicate và kiểm route sau thêm/bớt một endpoint.
- Tránh blocking I/O, double-subscription, content negotiation sai, header spoofing và xử lý body trước khi quyết định route.

**Lưu ý phạm vi:** các imports phải là package `org.springframework.web.reactive.function.server`, **không phải** `org.springframework.web.servlet.function.server` của MVC.fn. Hai nhóm API tên gần giống nhau nhưng không cùng stack. Không thêm `spring-boot-starter-web` nếu muốn project demo dùng WebFlux reactive application type mặc định.

## 2. Sơ đồ WebFlux.fn từ HTTP tới hàm xử lý

```text
HTTP client
   |
   | GET /api/books/special
   | Accept: application/json
   v
Reactor Netty / reactive server adapter
   |
   v
WebFilter chain -> DispatcherHandler
   |
   v
RouterFunctionMapping
   |
   v
RouterFunction.route(ServerRequest)
   |
   +-- Route 1: method/path/headers/media predicates? ── no
   +-- Route 2: method/path/headers/media predicates? ── yes
   |                                                   |
   |                                                   v
   |                                           HandlerFunction
   |                                           handle(ServerRequest)
   |                                                   |
   |                                                   v
   |                                           Mono<ServerResponse>
   |                                                   |
   v                                                   v
HandlerFunctionAdapter                        ServerResponseResultHandler
                                                       |
                                                       v
                                              HTTP status, headers, body
                                                       |
                                                       v
                                                     Client
```

Sơ đồ mô tả sự phối hợp logic: `RouterFunctionMapping` chọn một handler; `HandlerFunctionAdapter` gọi handler; `ServerResponseResultHandler` ghi kết quả. Đây là **một pipeline reactive**, không phải một thread hay transaction riêng cho từng hộp.

Đặc tả đơn giản hóa:

```java
// Chữ ký ý tưởng của các contracts có sẵn trong Spring:
interface RouterFunction<T extends ServerResponse> {
    Mono<HandlerFunction<T>> route(ServerRequest request);
}

interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
```

Đây là **mô tả API rút gọn**, không phải hai interfaces để tạo file mới. Spring đã định nghĩa chúng và có thêm phương thức hỗ trợ như visitor/nesting/filter.

Quan trọng: `RouterFunction` là **router**, không phải request handler. `HandlerFunction` mới thực thi business processing và trả `Mono<ServerResponse>`. Một request không khớp predicate không tự động được thử lại sau khi handler đã bắt đầu rồi gặp lỗi; routing lựa chọn handler trước khi handler đọc/tiêu thụ body.

## 3. Những khái niệm nền tảng

| API | Vai trò | Ví dụ |
| --- | --- | --- |
| `RouterFunctions.route()` | Tạo fluent builder nhiều routes | `.GET(...).POST(...).build()` |
| `RouterFunction<ServerResponse>` | Duyệt predicates, trả handler phù hợp | Bean do Spring Boot nhận diện |
| `RequestPredicate` | Kiểm một điều kiện trên `ServerRequest` | `accept(JSON)`, `contentType(JSON)` |
| `RequestPredicates` | Factory predicates chuẩn | `GET("/x")`, `headers(...)` |
| `HandlerFunction<ServerResponse>` | Gọi logic của endpoint | `handler::getBook` |
| `ServerRequest` | Immutable request API | `pathVariable`, `headers`, `bodyToMono` |
| `ServerResponse` | Builder HTTP response | `ok()`, `status(201)`, `contentType` |
| `RouterFunctionMapping` | WebFlux handler mapping cho routers | Khám phá các RouterFunction beans |
| `HandlerFunctionAdapter` | Gọi functional handler đã chọn | Đưa result vào pipeline |
| `ServerResponseResultHandler` | Viết ServerResponse thành HTTP | Chọn message writers/codecs |

Khác annotated route: `@GetMapping("/books/{id}")` được phân tích theo metadata của `RequestMappingHandlerMapping`. Còn functional routes **được kiểm theo thứ tự đã compose**; route đứng trước mà match sẽ thắng, ngay cả khi bạn đặt route tổng quát trước route cụ thể hơn.

### Khi nào dùng WebFlux.fn?

Functional routing hợp với nhóm muốn tách **routing table** khỏi **request handlers**, sử dụng composition/Java lambdas và gom shared predicates. Annotated controllers vẫn hợp lý khi dự án có nhiều method argument resolvers, Bean Validation annotations và các convention `@ControllerAdvice`. Cả hai chạy trên Spring WebFlux reactive core; functional không “non-blocking hơn” chỉ bởi không dùng annotations.

## 4. Route theo HTTP method

Cách ngắn gọn với builder:

```java
RouterFunction<ServerResponse> routes = RouterFunctions.route()
    .GET("/api/books", handler::list)
    .POST("/api/books", handler::create)
    .PUT("/api/books/{id}", handler::replace)
    .PATCH("/api/books/{id}", handler::patch)
    .DELETE("/api/books/{id}", handler::delete)
    .build();
```

Đây là **snippet** trong method cấu hình có `handler` và các functions tương ứng; không phải source runnable độc lập. Khi gọi `.GET(pattern, handler)`, builder phối hợp predicate **HTTP method GET** với **path pattern**. Tương tự cho POST, PUT, PATCH và DELETE.

Nếu dùng builder thủ công theo predicate:

```java
RouterFunction<ServerResponse> getRoute =
    RouterFunctions.route(
        RequestPredicates.method(HttpMethod.GET)
            .and(RequestPredicates.path("/api/books")),
        handler::list);
```

Snippet cần imports `RouterFunctions`, `RequestPredicates`, `HttpMethod`. Tương tự có `RequestPredicates.GET(String)`, `POST(String)`, `PUT(String)`, `DELETE(String)`, `HEAD(String)` và `OPTIONS(String)`.

### Method không phải “chi tiết không quan trọng”

- GET: đọc resource, không nên tạo side effects quan trọng.
- POST: tạo resource hoặc thực hiện operation theo contract, cần validate body.
- PUT: thay thế/tạo representation theo semantics, idempotency.
- PATCH: sửa một phần resource, cần định nghĩa patch document media type.
- DELETE: xóa resource hoặc đánh dấu xóa, vẫn cần authorization.
- HEAD: tương tự metadata GET nhưng không trả representation body; route/tooling có support riêng.
- OPTIONS: khám phá methods/options và CORS preflight; không nhầm với ứng dụng nghiệp vụ.
- Khác phương thức HTTP có thể khiến route không match dù path giống hệt.

**Không giả định HTTP method mismatch trong WebFlux.fn luôn trả chính xác 405.** Nếu không có handler nào được chọn, tùy RouterFunctionMapping, error handler và cấu hình, response có thể như một no-match 404. Một số hạ tầng Spring/HTTP server có xử lý bổ sung cho OPTIONS/HEAD và CORS. Nếu yêu cầu hợp đồng 405 + `Allow`, thiết kế fallback/method policy có chủ đích và integration-test trên đúng phiên bản/framework.

## 5. Route theo đường dẫn cố định

Ví dụ:

```java
return RouterFunctions.route()
    .GET("/api/health", req ->
        ServerResponse.ok().bodyValue("ok"))
    .GET("/api/version", req ->
        ServerResponse.ok().bodyValue("v1"))
    .build();
```

Đây là snippet có thể đặt trong `@Bean RouterFunction<ServerResponse> routes()`. Mỗi URL được so với pattern đã khai báo. `/api/health` và `/api/health/` **không nhất thiết** là cùng một đường dẫn; Spring Framework 6.x đã thay đổi một số defaults về optional trailing separators, nên phải test nếu ứng dụng cần redirect/canonical URL.

Path matching dùng `PathPattern` (không chỉ `String.equals`) và xử lý URI path đúng ngữ cảnh. URL percent-encoded slash, semicolon, dot segments và Unicode có thể chịu policy của server/gateway/firewall. Không dùng raw path làm filesystem path/authorization key mà không có canonicalization policy.

## 6. Path variable và route cụ thể trước route tổng quát

```java
return RouterFunctions.route()
    .GET("/api/books/special", handler::special)
    .GET("/api/books/{id}", handler::getById)
    .build();
```

Đặt `special` **trước** `/{id}`. Nếu đặt ngược và cả hai predicates match `/api/books/special`, route tổng quát có thể ăn request và coi `id="special"`; functional routing không tự chọn “method cụ thể nhất” như annotated mappings thường làm.

Trong handler:

```java
public Mono<ServerResponse> getById(ServerRequest req) {
    String id = req.pathVariable("id");
    return ServerResponse.ok().bodyValue(
        new BookView(id, "Sách " + id));
}
```

Đây là snippet, `BookView` cần được định nghĩa. `pathVariable("id")` đọc giá trị binding; không nên decode lại percent escapes hoặc dùng làm đường dẫn tệp. Nếu id phải là UUID, validate bằng converter và trả 400 theo contract; nếu user không có quyền xem ID đó, route match không đồng nghĩa authorization thành công.

### Path patterns phổ biến

```text
/api/books              exact path
/api/books/{id}         một segment biến id
/api/books/*            wildcard một path segment theo PathPattern rules
/api/files/**           nhiều path segments ở cuối pattern
/api/{category}/{id}    nhiều variables
```

Khi cần capture nhiều path segments ở cuối, Spring `PathPattern` còn có cú pháp đặc biệt như `{*path}`. Không sử dụng tùy tiện làm “file path từ URL”: hãy validate, canonicalize và map tới resource thuộc server, xét security/quyền.

**Không mặc định** `/api/files/a%2Fb` luôn đi qua được encoded slash; reverse proxy, Netty/Servlet adapters, firewall/decoder policy có thể can thiệp. Tốt hơn dùng ID path-safe hoặc query/JSON body cho dữ liệu chứa slash tự do.

## 7. Nested paths: gom nhóm thay vì lặp tiền tố

Functional builder có `.path(prefix, builderConsumer)`:

```java
@Bean
RouterFunction<ServerResponse> bookRoutes(BookHandler handler) {
    return RouterFunctions.route()
        .path("/api/books", b -> b
            .GET("/special", handler::special)
            .GET("/{id}", handler::getById)
            .POST(handler::create))
        .build();
}
```

Đây là snippet cần `@Configuration` và imports, handler methods tương ứng. Các inner patterns được match tương đối với prefix `/api/books`.

So sánh:

```text
path("/api/books", b -> b.GET("/special", ...))
           tương đương route GET /api/books/special

path("/api/books", b -> b.GET("/{id}", ...))
           tương đương route GET /api/books/{id}

path("/api/books", b -> b.POST(...))
           tương đương route POST /api/books
```

`path(...)` hữu ích cho API versioning như `/api/v1`, nhưng version segment là cấu trúc route, **không phải giá trị arbitrary ID** để nhét slash vào.

### Nest theo predicate không phải path

Ngoài `path`, builder có `nest(RequestPredicate, Consumer<Builder>)`, ví dụ:

```java
RouterFunctions.route()
    .path("/api/books", b -> b
        .nest(RequestPredicates.accept(MediaType.APPLICATION_JSON),
              json -> json
                  .GET("/{id}", handler::getById)
                  .GET("/special", handler::special)))
    .build();
```

Đây là snippet. **Thứ tự vẫn quan trọng trong nhánh nested!** Với snippet này `/{id}` đứng trước `/special` nên có thể shadow route cụ thể; phải đảo thứ tự trước khi dùng thật.

Thay vì lặp `accept(JSON)` nhiều lần, nesting biểu đạt một điều kiện chung. Tuy vậy nếu mọi route POST có content-type khác nhau, đừng nest `contentType(JSON)` ngoài cả nhóm khiến form/text branch không bao giờ match.

## 8. Header predicates: lọc theo metadata request

Spring WebFlux.fn có `RequestPredicates.headers(Predicate<ServerRequest.Headers>)`. Ví dụ kiểm header `X-View` có giá trị `trace`:

```java
RequestPredicate debugOnly =
    RequestPredicates.headers(headers ->
        "trace".equals(headers.firstHeader("X-View")));
```

Nếu request gửi:

```http
GET /api/status HTTP/1.1
X-View: trace
```

thì predicate match. Nếu không có header, `firstHeader(...)` trả null, biểu thức `"trace".equals(null)` là false.

**Headers case-insensitive theo HTTP specification** về tên; giá trị cần so sánh theo semantics từng header. Với custom `X-View` do ứng dụng tự định nghĩa, so exact value có thể phù hợp; không tùy tiện normalize Authorization hay signature values.

Có thể kết hợp:

```java
RequestPredicate jsonAndTrace =
    RequestPredicates.headers(h ->
        "trace".equals(h.firstHeader("X-View")))
    .and(RequestPredicates.accept(MediaType.APPLICATION_JSON));
```

Trong builder:

```java
.GET("/api/status", jsonAndTrace, handler::status)
```

Snippet yêu cầu biến `handler`. `RequestPredicates.headers(...)` kiểm **metadata**, không đọc JSON body và không xác thực ai gửi header đó.

### Header là input, không phải authentication

`X-User: admin`/`X-Role: ADMIN` do client gửi không phải chứng cứ danh tính. Nếu gateway đáng tin chèn header identity, phải cấu hình trust boundary, strip headers client tự khai, bảo vệ liên kết nội bộ và kiểm authorization ở backend. Dùng Spring Security reactive `SecurityWebFilterChain` cho authentication/authorization thay vì tự viết một `headers(...)` predicate giả làm access control.

### Header nhiều giá trị

`ServerRequest.Headers` có API cho giá trị đầu hoặc list tùy header. Nếu client gửi hai header cùng tên, proxy có thể gộp/giữ nhiều values; với security-sensitive matching cần định nghĩa policy chống request smuggling/header ambiguity. `firstHeader` phù hợp demo metadata đơn giản, không phải thuật toán canonicalization tổng quát.

## 9. Content-Type: mô tả request body

`Content-Type` trong request mô tả **loại representation mà client gửi**. Với POST:

```http
POST /api/books HTTP/1.1
Content-Type: application/json

{"title":"WebFlux"}
```

Để route chỉ nhận JSON:

```java
.POST("/api/books",
      RequestPredicates.contentType(MediaType.APPLICATION_JSON),
      handler::createJson)
```

Nếu client gửi `Content-Type: text/plain`, predicate này không match. Đừng cố `bodyToMono(BookInput.class)` cho plain text bằng cách “đổi route” khi codec không hỗ trợ.

### Charset và content-type compatibility

Media types có type/subtype và có thể parameters như charset/boundary. `contentType(APPLICATION_JSON)` là predicate theo compatibility semantics, không phải đơn giản `headerString.equals("application/json")`. Với JSON có charset parameter như `application/json;charset=UTF-8`, predicate vẫn có thể phù hợp.

**Media type matching không tự bảo đảm body hợp lệ**: JSON syntax hỏng nhưng Content-Type là `application/json` vẫn match route; sau đó Jackson decoder có thể phát error, thường HTTP 400. Ngược lại một body JSON hợp lệ gửi dưới `text/plain` không nên coi là JSON chỉ vì bytes trông giống JSON.

### Form và multipart

`application/x-www-form-urlencoded` và `multipart/form-data` là hai media types khác. WebFlux.fn `ServerRequest.bodyToMono(...)`/`formData()`/`multipartData()` có APIs tùy content type; không đọc multipart file content như String vô hạn trong event loop. Multipart cần limits về size/count và cleanup `DataBuffer`/`Part` đúng.

## 10. Accept: client muốn nhận gì?

`Accept` là **khả năng nhận response representation**, không phải mô tả request body.

```http
GET /api/books/42 HTTP/1.1
Accept: application/json
```

Predicate:

```java
.GET("/api/books/{id}",
     RequestPredicates.accept(MediaType.APPLICATION_JSON),
     handler::getById)
```

Khi match, handler có thể trả JSON với:

```java
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new BookView("42", "WebFlux"));
```

**Phân biệt hai giai đoạn:** `accept(JSON)` quyết định **route có phù hợp** theo request headers; `ServerResponse.contentType(JSON)` chọn **Content-Type của response** và codecs thực sự ghi body. Chỉ viết `accept(JSON)` không tự buộc handler phải trả JSON nếu handler cố ý viết text; ngược lại set response Content-Type không đảm bảo route đã match theo Accept.

### Accept wildcard, specificity và q-value

Ví dụ:

```http
Accept: */*
Accept: application/json
Accept: application/json, text/plain;q=0.5
Accept: text/*;q=0.8, application/json;q=0.4
```

Media type matching theo compatibility; wildcard có thể chấp nhận nhiều representations. Không viết code `request.headers().firstHeader("Accept").equals("application/json")` để triển khai content negotiation. Spring `RequestPredicates.accept(...)` giúp dùng semantics của HTTP media types; nếu cần chọn giữa nhiều representations dựa trên preference, hãy định nghĩa routes có thứ tự và test thực tế, hoặc dùng content negotiation strategy phù hợp.

**Lưu ý quan trọng:** Hai route khác nhau cùng path/method nhưng `accept(JSON)` và `accept(TEXT_PLAIN)` có thể **cùng match** một client gửi `Accept: */*`. Khi đó thứ tự functional routes quyết định handler nào được chọn. Vì vậy đừng coi `Accept` là độc quyền trừ khi client contract và route order được kiểm soát.

## 11. Tạo route theo cả Content-Type và Accept

Một endpoint nhận JSON và trả JSON:

```java
RequestPredicate jsonRequestAndResponse =
    RequestPredicates.contentType(MediaType.APPLICATION_JSON)
        .and(RequestPredicates.accept(MediaType.APPLICATION_JSON));

RouterFunction<ServerResponse> router = RouterFunctions.route()
    .POST("/api/books", jsonRequestAndResponse, handler::createJson)
    .build();
```

Đây là snippet trong class có `handler`. Request cần gửi cả đúng `Content-Type` lẫn `Accept` tương thích:

```http
POST /api/books HTTP/1.1
Content-Type: application/json
Accept: application/json

{"title":"Routing"}
```

Nếu client gửi `Content-Type: text/plain` và `Accept: application/json`, JSON route không match vì request body type sai. Nếu client gửi `Content-Type: application/json` và `Accept: text/plain`, JSON route không match vì representation output không được chấp nhận.

**Hợp đồng route là logic AND**, nhưng HTTP status mặc định khi không match không thể suy chỉ từ boolean. Còn tùy các routes khác, fallback, handlers và WebFlux version, một mismatch có thể thể hiện như no-handler 404 hoặc lỗi 406/415 do infrastructure đánh giá media mismatch. Nếu API contract yêu cầu cụ thể 406/415, integration tests phải chứng minh và có error policy tường minh khi cần.

## 12. Kết hợp predicates bằng and, or và negate

`RequestPredicate` cung cấp composition:

```java
RequestPredicate wantsJson =
    RequestPredicates.accept(MediaType.APPLICATION_JSON);

RequestPredicate internalView =
    RequestPredicates.headers(h ->
        "internal".equals(h.firstHeader("X-View")));

RequestPredicate strict =
    wantsJson.and(internalView);

RequestPredicate either =
    wantsJson.or(RequestPredicates.accept(MediaType.TEXT_PLAIN));

RequestPredicate notInternal = internalView.negate();
```

Các biến trên là code snippet trong Java method có static imports hoặc imports.

- `A.and(B)`: request phải thỏa **cả hai**.
- `A.or(B)`: ít nhất một phù hợp.
- `A.negate()`: đảo điều kiện.
- Composition có thể short-circuit; đừng đặt side effects quan trọng trong predicate rồi giả định nó luôn chạy.
- Predicates nên là các phép kiểm nhanh, không chặn I/O và không thay đổi resource state.
- Những điều kiện tổ hợp khó đọc nên được đặt tên hàm/biến như `jsonOnly`, `traceView`, `businessVersion` và có test riêng.

### Khi cần custom RequestPredicate

```java
RequestPredicate validApiKeyFormat = request ->
    request.headers().firstHeader("X-Api-Key") != null;
```

Đây chỉ là **kiểm sự hiện diện** của header, **không kiểm secret thực**, không đủ bảo vệ API. Hạn chế custom predicates tới các request-routing criteria ổn định; xác thực dùng Spring Security, validation business sau khi route match. Một predicate không nên gọi DB blocking hoặc tự subscribe reactive pipeline chỉ để quyết định route.

## 13. Functional routing có ưu tiên theo thứ tự khai báo

Theo Spring Framework reference, các routes được đánh giá **theo thứ tự**. Khi route đầu tiên match, route sau không được ưu tiên chỉ vì pattern cụ thể hơn.

Sai:

```java
RouterFunctions.route()
    .GET("/api/books/{id}", handler::getById)
    .GET("/api/books/special", handler::special)
    .build();
```

Request `GET /api/books/special` có thể vào `getById` với `id="special"`.

Đúng:

```java
RouterFunctions.route()
    .GET("/api/books/special", handler::special)
    .GET("/api/books/{id}", handler::getById)
    .build();
```

**Nested cũng không tự sửa route ordering.** Trong cùng một `.path(prefix, b -> ...)`, vẫn khai báo special before variable. Nếu tách nhiều `RouterFunction` beans, `RouterFunctionMapping` có thể sắp xếp/ghép beans theo `Ordered`; đừng kỳ vọng lexical bean name hoặc source file order luôn thắng. Nếu route order có ý nghĩa nghiệp vụ, gộp vào một router builder rõ ràng hoặc đánh `@Order` một cách có kiểm thử.

### Điều kiện header/media có thể shadow nhau

Nếu route rộng `GET("/api/books", handler::all)` đứng trước route `GET("/api/books", headers(...), handler::special)`, mọi request match route rộng sẽ đi vào `all`, kể cả có header đặc biệt. Cần đặt route cụ thể có header predicate **trước**, hoặc thiết kế khác path để tránh ambiguity.

Nếu bạn đặt GET JSON trước GET text trên cùng path và client dùng Accept wildcard, JSON có thể thắng. Điều này không phải bug codec; đó là route order logic.

## 14. Route theo query parameter (phụ trợ)

Mặc dù trọng tâm là path/method/header/media, query params thường cần trong route conditions:

```java
RouterFunctions.route()
    .GET("/api/books",
        RequestPredicates.queryParam("view", "compact"),
        handler::compact)
    .GET("/api/books", handler::list)
    .build();
```

Request `?view=compact` chọn route đầu tiên; thiếu hoặc khác thì route thứ hai. Đặt route conditional trước route fallback. `queryParam` kiểm query **đã parse**, không phải kiểm raw URI bằng substring. Nếu dữ liệu chứa `&`/`=` thì phải encode đúng component; không gọi `URLDecoder` hai lần.

Với nhiều values cùng tên, đừng coi một queryParam predicate là full validation của repeated parameter policy. Handler có thể dùng `request.queryParams()`/`queryParam(...)` và định nghĩa có nhận duplicates hay không.

**Không đưa identity/authorization vào query parameter một cách thiếu bảo mật**: `?role=admin` không phải permission và query thường xuất hiện trong logs/history.

## 15. Ghi response với ServerResponse

Functional handler trả `Mono<ServerResponse>`, thường tạo bằng builder:

```java
public Mono<ServerResponse> okBook(ServerRequest request) {
    BookView body = new BookView(
        request.pathVariable("id"), "Đọc sách");

    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body);
}
```

Đây là snippet, cần record `BookView` tương ứng.

Các patterns:

| Builder | Ý nghĩa |
| --- | --- |
| `ServerResponse.ok()` | HTTP 200 |
| `ServerResponse.status(HttpStatus.CREATED)` | HTTP 201 |
| `ServerResponse.accepted()` | HTTP 202 |
| `ServerResponse.noContent()` | HTTP 204 không body |
| `ServerResponse.badRequest()` | HTTP 400 |
| `ServerResponse.notFound()` | HTTP 404 |
| `ServerResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)` | HTTP 415 nếu app chủ động trả |
| `.header("X-Request-Id", id)` | Response header |
| `.contentType(MediaType.APPLICATION_JSON)` | Representation của response |
| `.bodyValue(dto)` | Encode regular body value qua codecs |
| `.body(flux, Dto.class)` | Viết reactive body stream |

Tạo `Mono<ServerResponse>` không có nghĩa bytes đã gửi ngay; HTTP infrastructure sẽ subscribe, invoke `ServerResponseResultHandler` và codecs, rồi ghi dữ liệu đúng response lifecycle.

### Đọc request body sau khi route phù hợp

```java
public Mono<ServerResponse> create(ServerRequest request) {
    return request.bodyToMono(CreateBook.class)
        .flatMap(input ->
            ServerResponse.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BookView("book_1", input.title())));
}
```

Snippet chưa xử lý empty/invalid body. Trong app thực thêm `switchIfEmpty(ServerResponse.badRequest().build())` và validation. Router predicate `contentType` **không thay thế** JSON decoding hoặc Bean Validation.

Không gọi `request.bodyToMono(...).block()` trong handler; Netty event loop không dành cho blocking work.

## 16. Ứng dụng hoàn chỉnh Spring Boot 3 Java 17+

Project Maven tạo bằng Spring Initializr, dependencies:

- `spring-boot-starter-webflux` (runtime).
- `spring-boot-starter-test` (test scope).
- `io.projectreactor:reactor-test` nếu không được kéo vào qua test starter phù hợp.

Không cần database, Spring Security, Servlet MVC hay template engine. Các files:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  BookHandler.java
  BookRoutes.java
src/test/java/com/example/demo/
  BookRoutesTest.java
```

**Các endpoints demo:**

| Method | URL | Predicate thêm | Result |
| --- | --- | --- | --- |
| GET | `/api/books/special` | `Accept` JSON | JSON book đặc biệt |
| GET | `/api/books/{id}` | `Accept` JSON | JSON book với ID |
| POST | `/api/books` | `Content-Type` JSON **and** `Accept` JSON | 201 JSON |
| POST | `/api/books` | `Content-Type` text/plain **and** `Accept` text/plain | 200 text |
| GET | `/api/status` | `X-View: trace` | JSON trạng thái |
| GET | `/api/stream` | `Accept` SSE | Ba SSE events |
| GET | `/api/docs/hello` | Nested path | JSON lời chào |

Routes có cùng path/method cho POST nhưng **Content-Type phân biệt rõ**; không đoán representation chỉ theo body. Test sẽ gửi `Accept` tường minh để tránh wildcard ambiguity.

### 16.1. DemoApplication.java

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

### 16.2. BookHandler.java

```java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class BookHandler {

    public Mono<ServerResponse> special(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new BookView("special", "Sách đặc biệt"));
    }

    public Mono<ServerResponse> getById(ServerRequest request) {
        String id = request.pathVariable("id");

        if (!id.matches("[A-Za-z0-9_-]{1,64}")) {
            return ServerResponse.badRequest().build();
        }

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new BookView(id, "Book " + id));
    }

    public Mono<ServerResponse> createJson(ServerRequest request) {
        return request.bodyToMono(CreateBook.class)
            .flatMap(input -> {
                if (input.title() == null ||
                    input.title().isBlank() ||
                    input.title().length() > 120) {
                    return ServerResponse.badRequest().build();
                }

                return ServerResponse.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new BookView("book_1",
                                             input.title()));
            })
            .switchIfEmpty(ServerResponse.badRequest().build());
    }

    public Mono<ServerResponse> createText(ServerRequest request) {
        return request.bodyToMono(String.class)
            .flatMap(raw -> {
                if (raw.isBlank() || raw.length() > 120) {
                    return ServerResponse.badRequest().build();
                }
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .bodyValue("text:" + raw);
            })
            .switchIfEmpty(ServerResponse.badRequest().build());
    }

    public Mono<ServerResponse> status(ServerRequest request) {
        // X-View là request metadata, không là authentication.
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new StatusView("trace"));
    }

    public Mono<ServerResponse> events(ServerRequest request) {
        Flux<NumberEvent> data = Flux.range(1, 3)
            .delayElements(Duration.ofMillis(60))
            .map(NumberEvent::new);

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(data, NumberEvent.class);
    }

    public Mono<ServerResponse> docsHello(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(new StatusView("docs-hello"));
    }

    public record BookView(String id, String title) {}
    public record CreateBook(String title) {}
    public record StatusView(String mode) {}
    public record NumberEvent(int value) {}
}
```

Giải thích:

- `BookHandler` là Spring bean có methods nhận `ServerRequest` và trả `Mono<ServerResponse>`.
- `getById` validate ID path-safe nhưng **không** thay authorize domain resource ở production.
- `createJson` nhận JSON body và trả 201; mỗi request demo tạo cùng ID `book_1` để đơn giản hóa, **không** có durable persistence. Production cần DB uniqueness, idempotency và permission.
- `createText` nhận `text/plain`, không deserialize thành JSON.
- `status` không đọc/suy role từ `X-View`; header chỉ định dạng route demo.
- `events` trả finite `Flux` với Content-Type SSE; delay chỉ để quan sát, không phải cam kết latency thực.
- `switchIfEmpty(ServerResponse.badRequest().build())` xử lý body rỗng; không tự tạo second subscription lên body stream.
- Với malformed JSON, decoder có thể phát error trước khi `flatMap` chạy; HTTP status/body lỗi phụ thuộc WebFlux error handling. Muốn thống nhất error contract, bổ sung `WebExceptionHandler`/functional routing error handler theo chính sách.

### 16.3. BookRoutes.java

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class BookRoutes {

    @Bean
    public RouterFunction<ServerResponse> functionalBookRoutes(
            BookHandler handler) {

        RequestPredicate jsonInputAndOutput =
            RequestPredicates.contentType(MediaType.APPLICATION_JSON)
                .and(RequestPredicates.accept(
                    MediaType.APPLICATION_JSON));

        RequestPredicate textInputAndOutput =
            RequestPredicates.contentType(MediaType.TEXT_PLAIN)
                .and(RequestPredicates.accept(MediaType.TEXT_PLAIN));

        RequestPredicate traceHeader =
            RequestPredicates.headers(h ->
                "trace".equals(h.firstHeader("X-View")));

        return RouterFunctions.route()

            // Đặt /special trước /{id} để route cụ thể thắng.
            .path("/api/books", b -> b
                .GET("/special",
                     RequestPredicates.accept(
                         MediaType.APPLICATION_JSON),
                     handler::special)
                .GET("/{id}",
                     RequestPredicates.accept(
                         MediaType.APPLICATION_JSON),
                     handler::getById)
                .POST(jsonInputAndOutput, handler::createJson)
                .POST(textInputAndOutput, handler::createText))

            .GET("/api/status", traceHeader, handler::status)

            .GET("/api/stream",
                 RequestPredicates.accept(
                     MediaType.TEXT_EVENT_STREAM),
                 handler::events)

            .path("/api/docs", b -> b
                .GET("/hello", handler::docsHello))

            .build();
    }
}
```

Vì sao đây là ví dụ tốt cho đề bài:

1. **Path**: nested `/api/books`, `/api/docs`; route `/special` trước `/{id}`.
2. **Method**: GET và POST không bị nhập làm một.
3. **Header**: `/api/status` chỉ match khi `X-View` bằng `trace`.
4. **Content-Type**: chọn POST JSON hoặc POST text.
5. **Accept**: yêu cầu client chấp nhận representation tương ứng, thêm SSE riêng.
6. **Composition**: `and` buộc cả input và output media-type conditions.
7. **Không shadow**: các routes chung path/method phân biệt bởi explicit predicates.
8. **Không tùy tiện dùng @EnableWebFlux**: để Spring Boot WebFlux auto-configuration tạo hạ tầng đúng.

### 16.4. Chạy demo

Trong thư mục project Maven có wrapper:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Cổng mặc định thường `8080` nếu không chỉnh `server.port`. Các source Spring cần dependency classpath; không chạy `javac BookRoutes.java` độc lập khi thiếu Spring/Reactor libraries.

## 17. curl: GET path và Accept

### Route đặc biệt

```bash
curl -i 'http://localhost:8080/api/books/special' \
  -H 'Accept: application/json'
```

Kỳ vọng HTTP 200 và JSON:

```json
{"id":"special","title":"Sách đặc biệt"}
```

Mục đích test: `/special` đã được match **trước** `/{id}`. Nếu đảo route order, cùng request có thể vào generic handler và trả `{"id":"special","title":"Book special"}`. Đây là dấu hiệu rõ của shadowing.

### Route variable

```bash
curl -i 'http://localhost:8080/api/books/book_42' \
  -H 'Accept: application/json'
```

Kỳ vọng:

```json
{"id":"book_42","title":"Book book_42"}
```

Dữ liệu path variable do application validate theo format `[A-Za-z0-9_-]{1,64}`. `/api/books/../../secret` không phải cách hợp lệ đọc resource và có thể bị gateway normalizer/canonicalizer xử lý sớm; vẫn cần authorization/ID mapping phía server.

### Đường dẫn nested

```bash
curl -i 'http://localhost:8080/api/docs/hello'
```

Kỳ vọng HTTP 200:

```json
{"mode":"docs-hello"}
```

Endpoint không yêu cầu `Accept` predicate nhưng handler vẫn đặt `Content-Type: application/json`. Nếu client không chấp nhận JSON và gửi Accept khác, hạ tầng codec/content negotiation hoặc error handler có thể phản ứng khác; route match không thay việc viết representation phù hợp.

## 18. curl: kiểm header predicate

```bash
curl -i 'http://localhost:8080/api/status' \
  -H 'X-View: trace'
```

Kỳ vọng HTTP 200:

```json
{"mode":"trace"}
```

Nếu bỏ `X-View` hoặc gửi `X-View: debug`, predicate không match. Trong project demo không có route fallback cho `/api/status` nên thường trả HTTP 404 (cũng tùy error layer); kiểm trực tiếp bằng integration tests thay vì coi header không tồn tại tự tạo 403.

**Tại sao không 403?** Header predicate chỉ định tuyến, không là authorization mechanism. Để bảo vệ endpoint hãy khai báo Spring Security policies và kiểm user/role/tenant. Trả 404 cho route không match và 403 cho user không đủ quyền là hai vấn đề khác nhau.

## 19. curl: POST Content-Type và Accept

### 19.1. JSON vào, JSON ra

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"title":"Học Spring WebFlux"}'
```

Kỳ vọng status **201 Created** và JSON:

```json
{"id":"book_1","title":"Học Spring WebFlux"}
```

Tại sao phải có cả hai headers? `Content-Type` mô tả JSON body, `Accept` báo client có thể nhận JSON. Client gửi `Accept: */*` vẫn có thể match JSON route theo compatibility; đây không phải công tắc yêu cầu header phải xuất hiện literal `application/json`.

### 19.2. Plain text vào, plain text ra

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: text/plain' \
  -H 'Accept: text/plain' \
  --data-binary 'hello'
```

Kỳ vọng status 200, `Content-Type: text/plain`, body:

```text
text:hello
```

Cùng URL + POST nhưng Content-Type khác, vì vậy handler khác.

### 19.3. Trộn sai Content-Type/Accept

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/plain' \
  -d '{"title":"not-matched"}'
```

Không route nào trong demo thỏa **đồng thời** cả hai điều kiện. **Không hardcode một mã trạng thái duy nhất cho mọi phiên bản WebFlux**, bởi xử lý no-match/media mismatch có thể thông qua RouterFunctionMapping/WebExceptionHandler; hãy xem HTTP thật và quyết định có cần error contract riêng hay không.

Đặc biệt, status 415 nên biểu thị request media type không được hỗ trợ, 406 biểu thị representation không được chấp nhận; 404 biểu thị không có resource/route phù hợp. Các nghĩa HTTP khác nhau, nhưng functional predicate mismatch **không phải** annotation `consumes/produces` tự sinh mọi status như nhau.

### 19.4. JSON malformed

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"title":'
```

Predicate Content-Type và Accept vẫn match; **body parser** khi đọc `CreateBook` thất bại, thường trả 400 qua WebFlux error infrastructure. Không viết predicate dựa vào body JSON content khi body đã được consume một lần.

### 19.5. Request thiếu body

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data-binary ''
```

Route vẫn match theo Content-Type; `bodyToMono` complete empty và handler `switchIfEmpty` trả 400. Đừng nhầm “route matched” với “body passes validation”.

## 20. curl: Server-Sent Events và media negotiation

```bash
curl -N -i 'http://localhost:8080/api/stream' \
  -H 'Accept: text/event-stream'
```

Kỳ vọng status 200, `Content-Type: text/event-stream` và các sự kiện:

```text
data:{"value":1}

data:{"value":2}

data:{"value":3}

```

Reactor `Flux.range(1,3).delayElements(...)` phát ba phần tử theo thứ tự. `curl -N` tắt buffering ở curl, nhưng **mỗi SSE event không đồng nghĩa một TCP packet**, timing không chắc chắn chính xác mỗi 60ms. Nếu client disconnect sớm, downstream có thể cancel subscription.

SSE đòi giới hạn số connections, kích thước buffer, idle timeouts, xác thực và chính sách reconnect/replay. Content negotiation giúp chọn codec, không tự giới hạn memory.

## 21. WebTestClient: kiểm route đúng và sự khác biệt predicates

Tệp `src/test/java/com/example/demo/BookRoutesTest.java`:

```java
package com.example.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class BookRoutesTest {

    @Autowired
    WebTestClient client;

    @Test
    void specificPathWinsBeforeIdPath() {
        client.get().uri("/api/books/special")
            .accept(MediaType.APPLICATION_JSON)
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("special")
            .jsonPath("$.title").isEqualTo("Sách đặc biệt");
    }

    @Test
    void pathVariableIsBoundIntoHandler() {
        client.get().uri("/api/books/book_42")
            .accept(MediaType.APPLICATION_JSON)
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("book_42")
            .jsonPath("$.title").isEqualTo("Book book_42");
    }

    @Test
    void headerPredicateSelectsRoute() {
        client.get().uri("/api/status")
            .header("X-View", "trace")
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.mode").isEqualTo("trace");
    }

    @Test
    void missingHeaderDoesNotMatchThisRoute() {
        client.get().uri("/api/status")
            .exchange().expectStatus().isNotFound();
    }

    @Test
    void jsonInputAndJsonAcceptUseJsonHandler() {
        client.post().uri("/api/books")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("title", "WebFlux"))
            .exchange().expectStatus().isCreated()
            .expectHeader().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo("book_1")
            .jsonPath("$.title").isEqualTo("WebFlux");
    }

    @Test
    void textInputAndTextAcceptUseTextHandler() {
        client.post().uri("/api/books")
            .contentType(MediaType.TEXT_PLAIN)
            .accept(MediaType.TEXT_PLAIN)
            .bodyValue("hello")
            .exchange().expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(
                MediaType.TEXT_PLAIN)
            .expectBody(String.class).isEqualTo("text:hello");
    }

    @Test
    void emptyJsonBodyFailsValidation() {
        client.post().uri("/api/books")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue("")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void nestedPathIsMatched() {
        client.get().uri("/api/docs/hello")
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.mode").isEqualTo("docs-hello");
    }

    @Test
    void sseStreamEmitsThreeItems() {
        Flux<BookHandler.NumberEvent> events = client.get()
            .uri("/api/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange().expectStatus().isOk()
            .returnResult(BookHandler.NumberEvent.class)
            .getResponseBody();

        StepVerifier.create(events)
            .expectNext(new BookHandler.NumberEvent(1))
            .expectNext(new BookHandler.NumberEvent(2))
            .expectNext(new BookHandler.NumberEvent(3))
            .verifyComplete();
    }
}
```

**Đây là bộ test mẫu chưa được thực thi trong repository của người dùng.** Spring Boot 3/WebTestClient/Netty codecs cần đúng dependencies đã nêu.

**Caveat đối với test empty JSON:** `bodyValue("")` với `contentType(APPLICATION_JSON)` có thể được JSON encoder diễn giải thành một JSON string literal `""` thay vì request body 0 byte; khi đó decoder cho `CreateBook` vẫn tạo lỗi 400, nhưng không chứng minh nhánh `switchIfEmpty`. Để kiểm chính xác zero-length request body, dùng `BodyInserters.empty()`/request body rỗng tương ứng và assert. Tùy codec/version, nên tách test malformed JSON và empty body riêng.

**Caveat đối với SSE codec:** một số cấu hình WebTestClient có thể decode SSE thành `ServerSentEvent<NumberEvent>` hoặc cần `ParameterizedTypeReference` thay vì DTO class trực tiếp. Nếu gặp codec mismatch, sử dụng kiểu `ServerSentEvent<BookHandler.NumberEvent>` đúng và assert `event.data()`; không coi một network chunk là một event. Test `returnResult`/`StepVerifier` về mặt khái niệm đúng.

**Caveat đối với no-match:** Test thiếu header kỳ vọng 404 ở cấu hình demo chuẩn; nếu application thêm exception handler/fallback router, hợp đồng phải được cập nhật. Đừng cố bắt 404 cho một ứng dụng đã tùy biến để trả status khác.

Chạy tests:

```bash
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

Nếu bật Spring Security reactive, các endpoint POST có thể đòi CSRF token và các request không xác thực có thể nhận 401/403 trước router; test cần cấu hình authentication/CSRF đúng policy. Demo không có Spring Security.

## 22. Kiểm thử thuần RouterFunction không cần HTTP server

Khi muốn test route selection nhanh, Spring có thể tạo `ServerRequest` giả qua `MockServerRequest` (API trong spring-test) hoặc dùng `WebTestClient.bindToRouterFunction(routes)` để không cần mở cổng.

Ý tưởng:

```java
WebTestClient client = WebTestClient
    .bindToRouterFunction(bookRoutes)
    .build();
```

Snippet dùng biến `bookRoutes` và imports `org.springframework.test.web.reactive.server.WebTestClient`. Với `bindToRouterFunction`, có thể kiểm các predicates/handlers mà không chạy Netty; nhưng **không nhất thiết** gồm toàn bộ security/filter/exception infrastructure của Boot app. Vì vậy dùng cả unit test router và HTTP integration test khi cần xác minh status, media negotiation, CORS hoặc proxy behavior.

### Test contract bằng ma trận thay vì một happy path

| Method | Path | X-View | Content-Type | Accept | Route mong muốn |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/books/special` | — | — | JSON | `special` |
| GET | `/api/books/book_42` | — | — | JSON | `getById` |
| POST | `/api/books` | — | JSON | JSON | `createJson` |
| POST | `/api/books` | — | text/plain | text/plain | `createText` |
| GET | `/api/status` | trace | — | */* | `status` |
| GET | `/api/status` | absent | — | */* | Không match route status |
| GET | `/api/stream` | — | — | text/event-stream | `events` |
| GET | `/api/docs/hello` | — | — | */* | `docsHello` |

Các dấu `—` nghĩa header không cần cho predicate đang xét. Riêng HTTP request GET không có body, `Content-Type` thường không quan trọng; đừng bắt GET client gửi Content-Type chỉ để match một response type. `Accept` thường có wildcard default nếu client không đặt.

Đặc biệt thử thêm các trường hợp `Accept: */*`, header lowercase/uppercase, query duplicates, encoded path, trailing slash, OPTIONS/CORS preflight và các method sai để phát hiện ambiguity.

## 23. Java 17 thuần: minh họa route order deterministic

Phần này không dùng Spring. Nó chỉ dùng Java 17 để chứng minh một router functional đơn giản đi qua danh sách điều kiện **theo thứ tự** và chọn điều kiện đầu tiên đúng.

Tệp `RouteOrderDemo.java`:

```java
import java.util.List;
import java.util.function.Predicate;

public class RouteOrderDemo {

    record Request(String method, String path, String accept) {}
    record Route(String name, Predicate<Request> matches) {}

    public static void main(String[] args) {
        List<Route> wrongOrder = List.of(
            new Route("byId",
                r -> r.method().equals("GET") &&
                     r.path().startsWith("/books/")),
            new Route("special",
                r -> r.method().equals("GET") &&
                     r.path().equals("/books/special"))
        );

        List<Route> rightOrder = List.of(
            wrongOrder.get(1),
            wrongOrder.get(0)
        );

        Request req = new Request(
            "GET", "/books/special", "application/json");

        System.out.println("wrong=" + choose(wrongOrder, req));
        System.out.println("right=" + choose(rightOrder, req));
    }

    private static String choose(
            List<Route> routes, Request request) {
        return routes.stream()
            .filter(route -> route.matches().test(request))
            .map(Route::name)
            .findFirst()
            .orElse("not-found");
    }
}
```

Biên dịch/chạy:

```bash
javac RouteOrderDemo.java
java RouteOrderDemo
```

Stdout chính xác:

```text
wrong=byId
right=special
```

Chương trình này minh họa ý tưởng **first matching predicate** chứ không tự thực thi Spring `PathPattern`, MIME compatibility, headers parsing hay Reactor. Để xác minh WebFlux.fn thực sự, dùng các tests của project Boot ở trên.

## 24. Bảo mật khi route theo header và đường dẫn

Routing theo header/path/content-type **không thay authentication, authorization hoặc input validation**:

- `X-User`, `X-Tenant`, `X-Role` do client tự khai không phải identity đáng tin.
- `RequestPredicates.headers(h -> "admin".equals(...))` là branch selection, không phải security guard.
- Dùng Spring Security `SecurityWebFilterChain`, bảo vệ routes và kiểm resource-level permissions sau khi bind ID.
- Cần xét các route functional và annotated cùng WebFlux application; một rule chỉ match annotation metadata có thể bỏ sót functional route nếu config không đúng.
- CORS preflight OPTIONS có thể được xử lý bởi `CorsWebFilter`/security chain trước route; đừng tạo route OPTIONS trả “cho phép hết” để bỏ qua policy.
- Content-Type không chứng minh body an toàn: validate kích thước, JSON syntax, schema và business rules.
- Accept không ngăn HTML/XSS nếu bạn ghi dữ liệu không được escape vào `text/html`; dùng output encoding theo context.
- Raw path/encoded slash và trailing slash cần canonicalization nhất quán qua proxy/server/security.
- Kiểm soát method override từ gateway/proxy nếu có; tránh hạ tầng hiểu POST thành PUT/PATCH khác nhau.
- Không log toàn bộ `Authorization`/`Cookie`/query string để debug route mismatch.
- Với multipart uploads và form parsing, có giới hạn size/count và cleanup `DataBuffer`.
- Path variable không được trực tiếp nối vào filesystem path để mở file.

**Chính sách nguồn tin cậy** cần đặt ở chỗ authentication/security; predicates chỉ nên kiểm metadata request phục vụ dispatch.

## 25. Khi nào status 404, 405, 406, 415?

HTTP semantics:

| Status | Ý nghĩa | Tình huống |
| --- | --- | --- |
| `404 Not Found` | Không có representation/resource phù hợp được tiết lộ | Không route match hoặc server chủ ý ẩn resource |
| `405 Method Not Allowed` | URL/resource có tồn tại nhưng method không được hỗ trợ | Cần `Allow` header thích hợp |
| `406 Not Acceptable` | Không có response representation client chấp nhận | `Accept` không tương thích |
| `415 Unsupported Media Type` | Body Content-Type không được hỗ trợ | Client gửi content type sai |
| `400 Bad Request` | Input sai format/schema/validation | JSON malformed, path ID invalid, required field missing |
| `401 Unauthorized` | Chưa xác thực theo policy | Security entrypoint |
| `403 Forbidden` | Đã xác thực nhưng không đủ quyền | Authorization failure |

**Đừng suy** rằng `RequestPredicates.accept(...)` false **luôn** sinh 406, hoặc `contentType(...)` false **luôn** sinh 415. Trong WebFlux.fn, predicate false thường chỉ làm router tìm route khác; cách error được dựng khi hết routes phụ thuộc mapping/error infrastructure và các route khác. Với annotated controllers, Spring có nhiều loại mismatch diagnostics khác, nên không chuyển nguyên hành vi qua functional.

Nếu API yêu cầu rất chặt status 405/406/415:
1. Định nghĩa expected contract rõ ràng.
2. Test ma trận method/path/Content-Type/Accept qua HTTP server thật.
3. Bổ sung fallback/error handler/hạ tầng phù hợp nếu default khác mong muốn.
4. Không trả 200 cho malformed JSON hoặc route không chấp nhận media type chỉ để “dễ gọi API”.
5. Đừng mở catch-all route trước route cụ thể vì nó sẽ shadow các status chuyên biệt.

## 26. Debug routing khi kết quả bất ngờ

Quy trình chẩn đoán từ dễ đến khó:

1. Xác nhận application thực sự chạy **WebFlux** (Boot starter, application type, Reactor Netty) chứ không phải MVC.
2. Xác nhận `RouterFunction<ServerResponse>` được tạo thành Spring bean trong application context, không chỉ là biến local chưa đăng ký.
3. Kiểm HTTP method từ client (`GET` khác `POST`; HEAD/OPTIONS có đường xử lý riêng).
4. Kiểm path và thứ tự: static before variables, nested base path, trailing slash, context path, encoded separators.
5. Kiểm raw `Content-Type` và `Accept` thực sự gửi; client libraries thường tự thêm Accept hoặc content type.
6. Kiểm `headers(...)` custom predicate có so đúng name/value, missing header null.
7. Xác nhận `and`/`or`/`negate` biểu đạt đúng logic; `Accept: */*` có thể match nhiều routes.
8. Kiểm RouterFunctionMapping ordering khi app có nhiều router beans/annotated controllers.
9. Nếu route đã match mà body lỗi, kiểm handler `bodyToMono`, codecs, validation, `ServerResponse.contentType`.
10. Kiểm WebFilter/Spring Security/CORS có từ chối request trước router không.
11. Nếu response đã commit và stream lỗi, không thể luôn đổi status/body; log/cancel cleanup.
12. Dùng `WebTestClient` với request headers tường minh, không chỉ test qua browser đã cache/proxy.
13. Bật route mapping diagnostics ở test/staging có kiểm soát, không expose secrets.
14. Với redirects/proxy, kiểm trusted forwarded headers để tránh host/scheme/path sai.
15. So sánh cùng HTTP request qua direct Netty và ingress production khi nghi proxy rewrites.

### Anti-pattern: kiểm header trong handler thay vì routing

```java
.GET("/api/status", request -> {
    if ("trace".equals(
            request.headers().firstHeader("X-View"))) {
        return handler.status(request);
    }
    return ServerResponse.notFound().build();
})
```

Đây là snippet có thể làm, nhưng nếu ý định là *điều kiện chọn route*, dùng `headers(...)` sẽ thể hiện contract rõ hơn và cho phép fallback routes. **Không** dùng header predicate cho auth; cả hai cách đều cần security policy riêng.

## 27. Lỗi phổ biến

1. **Import MVC.fn package trong dự án WebFlux:** classes cùng tên nhưng khác reactive/servlet APIs.
2. **Định nghĩa RouterFunction nhưng không @Bean:** WebFlux không biết router.
3. **Đặt `/{id}` trước `/special`:** generic route nuốt request.
4. **Cho rằng functional routing tự chọn pattern cụ thể nhất:** thứ tự thắng.
5. **Đặt route GET không predicate trước GET có header predicate:** route có header không bao giờ tới.
6. **Nhầm Content-Type với Accept:** một cái mô tả body request, cái kia mô tả response chấp nhận.
7. **So literal header Accept bằng equals:** wildcard/quality/media compatibility không được xử lý.
8. **Tin Content-Type JSON bảo đảm JSON hợp lệ:** cần decoder và validation.
9. **Chỉ route theo Content-Type nhưng trả response body loại khác Accept:** client có thể không xử lý.
10. **Không set response contentType khi cần JSON/SSE:** codec/media mismatch.
11. **Dùng header X-Role cho auth:** header client tự khai dễ giả mạo.
12. **Tin HTTP 415/406 luôn xuất hiện khi predicate false:** router có thể chỉ xem là no-match.
13. **Không test POST method mismatch:** 405/404/OPTIONS khác tùy stack.
14. **Không test HEAD/OPTIONS/CORS:** browser có thể gửi preflight trước request thật.
15. **Không validate path variable:** ID có thể sai format và tạo resource traversal.
16. **Tự decode URI path một lần nữa:** có thể đổi dữ liệu thành separator.
17. **Dùng route pattern chứa wildcard rộng và đặt trước protected route:** shadow security/matching.
18. **Đặt nested accept(JSON) cho cả text POST:** text branch bị loại trước khi tới.
19. **Viết custom RequestPredicate gọi JDBC/block:** chặn reactive event loop.
20. **Tự subscribe trong HandlerFunction:** tách completion/error/cancel khỏi HTTP.
21. **Gọi block() trong Netty event loop:** throughput giảm hoặc Reactor ném lỗi.
22. **Dùng contentType(JSON) cho GET body rỗng:** có thể loại bỏ request hợp lệ.
23. **Không giới hạn request body trước decode:** memory/CPU pressure.
24. **Chấp nhận malformed JSON và trả 200 empty:** che lỗi validation.
25. **Không phân biệt query parameter và header predicate:** metadata khác nhau.
26. **Tạo nhiều RouterFunction beans trùng route không kiểm @Order:** route bị chọn tùy order.
27. **Tin client luôn gửi Accept cụ thể:** nhiều clients gửi `*/*`.
28. **Hardcode JSON property order/stdout timestamp trong tests:** flaky/unnecessary.
29. **Cho rằng mỗi SSE element là một packet:** network framing khác message boundaries.
30. **Không cleanup trên client disconnect:** giữ event subscriptions/timers.
31. **Coi response 201 là đã persist thật:** demo không có database.
32. **Không kiểm authenticated principal/resource permission sau route match:** route match không cấp quyền.
33. **Chuyển 404 thành catch-all 200 cho API lạ:** giấu lỗi client.
34. **Tin routing theo path xử lý toàn bộ nội dung encode:** cần canonicalization/URL-safe IDs.
35. **Lẫn RouterFunctionMapping với RequestMappingHandlerMapping:** functional và annotated không có cùng route precedence.
36. **Sửa SPI WebFlux chỉ để thêm một GET/POST route:** builder đã đủ.
37. **Dùng header metadata để đoán tenant đáng tin:** tenant phải đến từ trusted identity/authorization.
38. **Không test content negotiation đa representation:** Accept wildcard tạo ambiguity.

## 28. Checklist production và code review

### Routing

- [ ] Dùng `org.springframework.web.reactive.function.server` đúng stack WebFlux?
- [ ] Có `@Bean RouterFunction<ServerResponse>` được Boot nhận diện?
- [ ] Path tĩnh đặt trước path variables/generic wildcard?
- [ ] Nested path prefixes không bị lặp/trùng slash ngoài ý muốn?
- [ ] Method route khớp API contract (GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS)?
- [ ] Header predicates ngắn, pure và không dùng làm authentication?
- [ ] `contentType` dùng cho loại request body, `accept` cho response?
- [ ] Các predicates `and/or/negate` có test riêng?
- [ ] Không có routes shadow nhau khi client gửi `Accept: */*`?
- [ ] Multiple RouterFunction beans có order rõ nếu overlapping?

### HTTP result và bảo mật

- [ ] `ServerResponse` đặt status và response Content-Type đúng?
- [ ] Body readers/codecs parse JSON/form/multipart theo đúng media type?
- [ ] Body size/validation/authorization được kiểm sau route match?
- [ ] Không gọi blocking IO/`block()`/tự `subscribe()` trong handler?
- [ ] Không tin custom header hay path ID làm authenticated identity?
- [ ] Spring Security/CORS/CSRF policy tương ứng với functional routes?
- [ ] Response committed/stream cancellation/error có cleanup?
- [ ] Không log token, cookies hoặc raw query có secrets?

### Kiểm thử

- [ ] Test static `/special` thắng generic `/{id}`?
- [ ] Test POST JSON và POST text với Content-Type/Accept tường minh?
- [ ] Test header present/absent/wrong value?
- [ ] Test wildcard Accept, text/json swapped và wrong HTTP method?
- [ ] Test 400 malformed/empty body và domain-invalid fields?
- [ ] Test unknown path/no match, nested route và trailing slash?
- [ ] Test trên WebTestClient + HTTP server thật để thấy error pipeline?
- [ ] Test qua proxy/gateway nếu có path rewrites hoặc forwarded headers?
- [ ] Test SSE events và cancellation khi streaming được dùng?

## 29. Câu hỏi tự kiểm tra

**1.** `RouterFunction` khác `HandlerFunction` thế nào?

**Đáp án:** RouterFunction chọn handler bằng predicates, HandlerFunction xử lý request và trả Mono<ServerResponse>.

**2.** Vì sao `GET("/books/{id}")` nên đứng sau `GET("/books/special")`?

**Đáp án:** Functional routes được xét theo thứ tự; generic route có thể match “special” như một id, không tự chọn pattern cụ thể nhất.

**3.** `contentType(APPLICATION_JSON)` kiểm gì?

**Đáp án:** Loại **request body** client gửi, không phải loại body server sẽ trả.

**4.** `accept(APPLICATION_JSON)` kiểm gì?

**Đáp án:** `Accept` cho representation client có thể nhận.

**5.** `ServerResponse.contentType(APPLICATION_JSON)` có thay thế predicate `accept(JSON)` không?

**Đáp án:** Không. Nó xác định representation/codec của response, còn predicate dùng trong chọn route.

**6.** Header `X-Role: ADMIN` có thể dùng làm điều kiện quyền không?

**Đáp án:** Không; client tự khai được. Spring Security và business authorization mới quyết định quyền.

**7.** `and` và `or` trong RequestPredicate khác gì?

**Đáp án:** `and` yêu cầu cùng đúng, `or` chỉ cần một đúng; đặt thứ tự và test composition.

**8.** Request `Accept: */*` match những route nào?

**Đáp án:** Có thể match nhiều representations tương thích; functional route order quyết định khi nhiều match.

**9.** Khi gửi Content-Type text/plain tới route chỉ nhận JSON, status chắc chắn là 415?

**Đáp án:** Không thể kết luận chỉ từ predicate false. Nó có thể thành no-match tùy hạ tầng; HTTP contract cần được test/cấu hình.

**10.** Có thể vừa dùng annotated controllers vừa functional routes không?

**Đáp án:** Có, cùng WebFlux DispatcherHandler nhưng mapping/adapter/result handlers khác. Tránh overlap ngoài ý muốn.

**11.** Dùng `@RequestBody` trong HandlerFunction có đúng không?

**Đáp án:** Functional model dùng `ServerRequest.bodyToMono(...)` hoặc `bodyToFlux(...)`, không dùng method parameter annotations kiểu controller.

**12.** Một HandlerFunction cần tự gọi `Mono.subscribe()` để ghi body?

**Đáp án:** Không. Trả Mono<ServerResponse> cho WebFlux HTTP infrastructure.

**13.** Khi nào nên dùng `.path(prefix, builder)`?

**Đáp án:** Khi nhiều routes dùng chung path prefix, ví dụ `/api/books`; vẫn cần thứ tự specific-before-general bên trong.

**14.** Vì sao route match chưa đủ để trả 200?

**Đáp án:** Body có thể hỏng, validation fail, thiếu authorization, codec không hỗ trợ hoặc downstream error.

**15.** Khi nào dùng WebTestClient.bindToRouterFunction?

**Đáp án:** Khi muốn test router/handler logic nhanh không mở server, bổ sung HTTP integration tests để bao phủ security/filter/transport.

## 30. Tài liệu tham khảo

- Spring Framework 6.2 — Functional Endpoints (WebFlux.fn) và thứ tự routes/nested/predicates: https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html
- Spring Framework 6.2 — `RequestPredicates` (reactive API Javadoc): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/reactive/function/server/RequestPredicates.html
- Spring Framework 6.2 — `RouterFunctions.Builder` (reactive API Javadoc): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.Builder.html
- Spring Framework 6.2 — `ServerRequest` (reactive API Javadoc): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/reactive/function/server/ServerRequest.html
- Spring Framework 6.2 — `ServerResponse` (reactive API Javadoc): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html
- Spring Framework 6.2 — DispatcherHandler and WebFlux routing SPI: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html
- Spring Framework — Path Patterns: https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-requestmapping.html
- Spring Framework — WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Spring Security — Reactive/WebFlux Security: https://docs.spring.io/spring-security/reference/reactive/index.html
- Reactor Core Reference — reactive lifecycle, backpressure and schedulers: https://projectreactor.io/docs/core/release/reference/
- RFC 9110 — HTTP Semantics, Accept, Content-Type, status codes and methods: https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-004">Nesting, hợp thành router và tổ chức tuyến theo tính năng</a>

<details>
<summary>Xem chi tiết</summary>

# Nesting, hợp thành router và tổ chức tuyến theo tính năng trong Spring WebFlux

## 1. Tổng quan: vì sao không đặt tất cả route vào một class?

Ứng dụng Spring WebFlux dùng functional endpoints thường bắt đầu với vài route:

~~~java
RouterFunction<ServerResponse> routes = RouterFunctions.route()
        .GET("/api/books", bookHandler::list)
        .GET("/api/books/{id}", bookHandler::byId)
        .POST("/api/books", bookHandler::create)
        .GET("/api/authors", authorHandler::list)
        .GET("/api/authors/{id}", authorHandler::byId)
        .build();
~~~

Đây là đoạn minh họa cần các handler beans đã được khai báo. Khi API phát triển, một file cấu hình chứa hàng trăm `GET/POST/PUT/DELETE` rất khó đọc: lặp prefix `/api`, trùng điều kiện `Accept: application/json`, khó xác định route nào match trước, và nhóm sách có thể ảnh hưởng nhóm tác giả.

**Nesting** (lồng router/predicate) và **composition** (hợp thành nhiều RouterFunction) giúp thiết kế:

~~~text
/api
├── /health
├── /books                 Book feature
│   ├── GET               danh sách
│   ├── GET /featured     tuyến cụ thể
│   ├── GET /{id}         tuyến có path variable
│   └── POST              tạo sách
└── /authors               Author feature
    ├── GET               danh sách
    └── GET /{id}         chi tiết
~~~

Mỗi feature có thể tự sở hữu handler + router + DTO/service, còn một lớp composition gắn base path cho cả hệ thống. Cách này đặc biệt hữu ích trong codebase có nhiều bounded contexts, multiple teams hoặc API versions.

**Điểm cần nhớ:** các tuyến được chọn **theo thứ tự hợp thành**. Nesting chỉ tổ chức matching; nó không tự cung cấp authentication, validation, thread isolation hay database transactions.

### Kết quả học tập

Sau bài, bạn có thể:
- Phân biệt `RouterFunction<ServerResponse>`, `RequestPredicate`, `HandlerFunction<ServerResponse>` và reactive `ServerResponse`.
- Dùng `RouterFunctions.route()`, `RouterFunctions.nest(...)`, builder `path(...)` và `nest(...)`.
- Ghép router bằng `RouterFunction.and(...)`, `andRoute(...)` và builder `add(...)`.
- Thiết kế module router có **child paths tương đối**, compose tại một điểm duy nhất.
- Hiểu độ ưu tiên của static route, path variable, catch-all, Accept/Content-Type và fallback.
- Biết phạm vi filter `before/after/filter/onError` khi đặt trong/ngoài nested group.
- Tránh import nhầm Servlet WebMvc.fn vào reactive WebFlux.fn.
- Chạy ví dụ Java 17 / Spring Boot 3.3.5, Book và Author routers độc lập, WebTestClient kiểm thử.
- Đánh giá CORS, Spring Security, lỗi, tracing và OpenAPI ở hệ thống nhiều router.

## 2. Phải chọn đúng WebFlux.fn, không phải WebMvc.fn

Hai framework có classes gần như cùng tên:

| Spring WebFlux.fn reactive | Spring WebMvc.fn Servlet |
| --- | --- |
| `org.springframework.web.reactive.function.server.RouterFunction` | `org.springframework.web.servlet.function.RouterFunction` |
| `org.springframework.web.reactive.function.server.RequestPredicate` | `org.springframework.web.servlet.function.RequestPredicate` |
| `org.springframework.web.reactive.function.server.ServerRequest` | `org.springframework.web.servlet.function.ServerRequest` |
| `org.springframework.web.reactive.function.server.ServerResponse` | `org.springframework.web.servlet.function.ServerResponse` |
| Handler trả `Mono<ServerResponse>` | Handler trả `ServerResponse` |
| `ServerResponse.ok().bodyValue(value)` | `ServerResponse.ok().body(value)` |
| `RequestPredicates.queryParam(...)` | `RequestPredicates.param(...)` |
| Reactor/WebFlux runtime | Servlet MVC runtime |

Trong **Spring WebFlux.fn** signature là:

~~~java
@FunctionalInterface
interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
~~~

Đây là dạng giản lược của interface để học, không phải file Java cần tự chép vào project. Framework quản lý subscription và viết HTTP response khi `Mono<ServerResponse>` hoàn thành.

Không thêm `spring-boot-starter-web` vào ví dụ chỉ vì Spring có chữ “web”: khi cả MVC và WebFlux starters cùng có mặt, Spring Boot thường chọn MVC web application type theo default. Ví dụ chạy WebFlux/Netty chỉ dùng `spring-boot-starter-webflux`.

## 3. Những thành phần xuất hiện trong một router

### 3.1. `ServerRequest`

Đọc path variable, query parameter, headers và body:

~~~java
String id = request.pathVariable("id");
String q = request.queryParam("q").orElse("");
MediaType accept = request.headers().accept().stream()
        .findFirst().orElse(MediaType.ALL);
Mono<CreateBook> input = request.bodyToMono(CreateBook.class);
~~~

Mã fragment trên cần `ServerRequest request`, `MediaType` và `CreateBook`. `bodyToMono` sử dụng reactive codecs và không nên gọi `.block()` trong handler. Path variable ban đầu là String; hãy parse/validate trước khi tìm entity.

### 3.2. `ServerResponse`

Dạng fluent reactive:

~~~java
Mono<ServerResponse> ok =
        ServerResponse.ok().bodyValue(book);

Mono<ServerResponse> created =
        ServerResponse.created(URI.create("/api/books/3"))
                .bodyValue(book);

Mono<ServerResponse> missing =
        ServerResponse.notFound().build();
~~~

Ba snippets giả định `book` và imports đã có. `ServerResponse.ok()` tạo response builder; `bodyValue(...)`/`build()` trả `Mono<ServerResponse>`. Không gọi `subscribe()` để tự “gửi response” hoặc `block()` để lấy ServerResponse trong reactive handler.

### 3.3. `RequestPredicate`

Predicate quyết định một request có phù hợp tuyến không:

~~~java
RequestPredicate predicate =
        RequestPredicates.GET("/api/books/{id}")
                .and(RequestPredicates.accept(
                        MediaType.APPLICATION_JSON));
~~~

Các factory phổ biến: `GET`, `POST`, `PUT`, `DELETE`, `path`, `accept`, `contentType`, `headers`, `queryParam` và `method`. Dùng `and`, `or`, `negate` để tạo điều kiện phức tạp; viết tests vì một predicate sai có thể khiến handler **không bao giờ được chọn**, không nhất thiết tạo đúng HTTP error status bạn mong muốn.

### 3.4. `RouterFunction`

Về mặt trừu tượng, router nhận `ServerRequest` và tìm handler phù hợp, kết quả là một publisher của handler hoặc empty khi không match. Ví dụ:

~~~java
RouterFunction<ServerResponse> health =
        RouterFunctions.route(
                RequestPredicates.GET("/api/health"),
                request -> ServerResponse.ok().bodyValue("OK"));
~~~

Đây là một router độc lập có thể hợp thành. Để Spring Boot phát hiện và đăng ký khi có auto-config WebFlux, bạn thường cung cấp một bean kiểu `RouterFunction<ServerResponse>`.

## 4. Router builder DSL: định nghĩa nhiều đường dẫn

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .GET("/api/books", handler::list)
                .GET("/api/books/{id}", handler::byId)
                .POST("/api/books", handler::create)
                .build();
~~~

`handler` là bean có các methods nhận `ServerRequest` và trả `Mono<ServerResponse>`. Builder method nhận HTTP method/path, optional extra predicate và handler; cuối cùng `build()` tạo router.

Ưu điểm:
- Route declaration tập trung, dễ review.
- Handler method có thể tách sang bean riêng; không phải annotate từng HTTP method.
- Có thể dùng DSL để nhóm common path/header/content-type conditions.
- Dễ composition với router đã được định nghĩa ở module khác.

### 4.1. Route order là một phần hợp đồng

**Sai** nếu `/{id}` đặt trước một static route:

~~~java
RouterFunctions.route()
        .GET("/api/books/{id}", handler::byId)
        .GET("/api/books/featured", handler::featured)
        .build();
~~~

`featured` có thể match `{id}` ở route đầu, khiến handler parse ID báo lỗi hoặc trả resource khác. **Đúng:**

~~~java
RouterFunctions.route()
        .GET("/api/books/featured", handler::featured)
        .GET("/api/books/{id}", handler::byId)
        .build();
~~~

Khác với annotated controller `@RequestMapping` có thuật toán so độ đặc hiệu giữa các mapping, functional router composition xét **trong thứ tự định nghĩa/hợp thành**. Nên đặt static/cụ thể trước variable/wildcards, và fallback/catch-all ở cuối.

## 5. `path(prefix, ...)` — nesting theo đường dẫn

Khi mọi books endpoints chung `/api/books`:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api/books", books -> books
                        .GET("/featured", handler::featured)
                        .GET("/{id}", handler::byId)
                        .GET("", handler::list)
                        .POST("", handler::create))
                .build();
~~~

`path("/api/books", builderConsumer)` là một cách **nesting path prefix**. Bên trong, `/{id}` chỉ là child path; HTTP client vẫn gọi `GET /api/books/3`, không gọi `/{id}`.

Trong code thực, một số examples của Spring dùng `GET(handler::list)` không khai báo child path cho danh sách. Nếu muốn route chỉ match collection root chứ không làm fallback quá rộng, có thể khai báo child path `""` và **test** `/api/books`, `/api/books/`, `/api/books/extra/path` theo Spring path-pattern rules bạn đang dùng. Không đoán mọi biến thể trailing slash đều tương đương.

### 5.1. Nest nhiều cấp prefix

~~~java
RouterFunctions.route()
        .path("/api", api -> api
                .path("/v1", v1 -> v1
                        .path("/books", books -> books
                                .GET("", handler::list)
                                .GET("/{id}", handler::byId))))
        .build();
~~~

Ví dụ trên tạo `GET /api/v1/books` và `GET /api/v1/books/{id}`. Mục đích là minh họa prefix nesting, không phải đề xuất cứ ba tầng path mới đúng. Quá nhiều cấp nesting có thể khiến full URL khó nhìn, khó debug và tăng rủi ro đặt filter sai phạm vi.

### 5.2. Builder `path` nhận cả Supplier router module

Spring WebFlux hỗ trợ một overload rất hữu ích:

~~~java
RouterFunctions.route()
        .path("/api/books", bookModule::routes)
        .path("/api/authors", authorModule::routes)
        .build();
~~~

`bookModule.routes()` và `authorModule.routes()` trả `RouterFunction<ServerResponse>` với các child routes tương đối. Đây là pattern **module composition**; application root config sở hữu public URL prefixes, còn feature modules sở hữu logic routing và handler.

## 6. `nest(predicate, ...)` — nesting theo điều kiện chung

Không phải mọi điều kiện chung đều là URL. Bạn có thể group theo `Accept`, `Content-Type`, request header hoặc custom predicate.

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api/books", b -> b
                        .nest(
                                RequestPredicates.accept(
                                        MediaType.APPLICATION_JSON),
                                json -> json
                                        .GET("/featured", handler::featured)
                                        .GET("/{id}", handler::byId)
                                        .GET("", handler::list))
                        .POST("",
                                RequestPredicates.contentType(
                                        MediaType.APPLICATION_JSON),
                                handler::create))
                .build();
~~~

Ý nghĩa:
- GET bên trong `nest(accept(JSON), ...)` chỉ match nếu client chấp nhận response JSON.
- POST ở **cấp builder books**, ngoài nhánh nested Accept nên không phải tuân theo Accept constraint của nhánh đó.
- POST có `contentType(JSON)`, kiểm định dạng **request body**, không phải Accept response.
- Route static `/featured` đứng trước variable `/{id}`.
- `GET("")` chọn collection root, `GET("/{id}")` chọn chi tiết theo matching path.
- Khi một nhánh không match, router tiếp tục thử route tiếp theo; không phải lập tức trả error JSON do bạn tự định nghĩa.

### 6.1. `path` và `nest(path(...))`

`path("/api/books", ...)` là DSL chuyên cho path prefix. `nest(RequestPredicates.path("/api/books"), ...)` cũng dùng predicate path để lồng router; `nest` còn nhận các điều kiện khác. Hãy dùng `path` cho tree theo resource và `nest` cho shared cross-cutting matching condition khi code dễ đọc hơn.

### 6.2. Kết hợp predicate

~~~java
RequestPredicate jsonVersionTwo =
        RequestPredicates.accept(MediaType.APPLICATION_JSON)
                .and(RequestPredicates.headers(
                        h -> "2".equals(
                                h.firstHeader("API-Version"))));
~~~

Nó chỉ chọn tuyến HTTP; không biến request header do client tự gửi thành quyền bảo mật hay quyền truy cập dữ liệu. Nếu có versioned API, định nghĩa rõ version parser, missing/unsupported values, default version và cache key. Không dùng cùng một route match quá rộng rồi âm thầm thay DTO không tương thích.

### 6.3. `queryParam` trong reactive package

~~~java
RequestPredicate summary =
        RequestPredicates.queryParam("mode", "summary");
~~~

Đây là **WebFlux.fn** `queryParam`, khác tên `param` thường thấy ở WebMvc.fn. Điều kiện này match `?mode=summary`; nếu thiếu/giá trị khác, route không match và có thể được xử lý bởi fallback nếu bạn khai báo.

## 7. Thứ tự route và fallback bên trong nhóm lồng nhau

Ví dụ rõ nhất là hai routes trùng path nhưng khác query:

~~~java
RouterFunctions.route()
        .GET("/api/books/featured",
                RequestPredicates.queryParam("mode", "summary"),
                handler::featuredSummary)
        .GET("/api/books/featured", handler::featuredHelp)
        .GET("/api/books/{id}", handler::byId)
        .build();
~~~

Khi có `?mode=summary`, chọn route đầu. Khi không có, route đầu không match nhưng route thứ hai có thể match; handler `featuredHelp` trả hướng dẫn lỗi 400. Vì `/{id}` đứng sau cả hai, `featured` không bị hiểu nhầm là một ID.

**Anti-pattern:** đặt `GET("/{id}")` ở đầu, hoặc đặt `GET("/**")` catch-all trước các routes cụ thể. Cái sai có thể âm thầm trả status 200 với body **không đúng handler**; chỉ kiểm status chưa đủ.

### 7.1. Routing fallback không thay validation

Nếu `GET /api/books/abc` match `/{id}`, predicate matching thành công. Khi handler không parse được `abc` thành số, đó là **validation error trong handler**, có thể trả 400. Không mặc định `{id}` chỉ nhận numeric.

### 7.2. Không được tùy tiện coi non-match là 400

Nếu `Accept: text/html` không match nested JSON route, kết quả có thể 404/406 tùy handler mapping, media type negotiation và error layer. Khi API contract yêu cầu 406 cụ thể, cần test và cấu hình lỗi rõ thay vì đoán Spring luôn tự map mọi predicate mismatch thành cùng status.

### 7.3. Context path và trailing slashes

`GET /api/books`, `GET /api/books/` và URL encoded segment khác nhau có thể không có cùng behavior theo Spring Framework path matching version/config. Test những URL được contract cho phép; đừng tự rewrite path bằng phép nối chuỗi đơn giản trong proxy rồi hy vọng tất cả nested paths vẫn đúng.

## 8. Hợp thành router bằng `and(...)` và `andRoute(...)`

Có thể xây router từng phần:

~~~java
RouterFunction<ServerResponse> first =
        RouterFunctions.route(
                RequestPredicates.GET("/api/health"),
                handler::health);

RouterFunction<ServerResponse> second =
        RouterFunctions.route(
                RequestPredicates.GET("/api/status"),
                handler::status);

RouterFunction<ServerResponse> combined = first.and(second);
~~~

Khi request match router đầu, router đó cung cấp handler; nếu không, chuyển sang router sau. `and(...)` giữ tính modular mà không bắt buộc dùng builder. `andRoute(...)` tiện khi nối một predicate/handler:

~~~java
RouterFunction<ServerResponse> combined =
        first.andRoute(
                RequestPredicates.GET("/api/status"),
                handler::status);
~~~

Đây là fragments cần `handler` định nghĩa riêng. Không tự suy rằng `and` chạy **cả hai handlers** hoặc thực hiện cả hai side effects. Nó tạo một logic **chọn một handler phù hợp**.

### 8.1. Hợp thành qua Builder `add(...)`

~~~java
RouterFunction<ServerResponse> finalRouter =
        RouterFunctions.route()
                .GET("/api/health", handler::health)
                .add(bookRouter)
                .add(authorRouter)
                .build();
~~~

`bookRouter` và `authorRouter` đã có từ module khác. Nếu các module tự sở hữu absolute paths, `add` hữu ích. Nếu muốn **root config sở hữu prefixes**, dùng `path("/api/books", bookModule::routes)` như ví dụ runnable. Đừng cùng lúc prefix ở module và bên ngoài làm URL thành `/api/books/api/books`.

### 8.2. Hợp thành với `RouterFunctions.nest(...)` tĩnh

~~~java
RouterFunction<ServerResponse> child =
        RouterFunctions.route()
                .GET("/{id}", handler::byId)
                .build();

RouterFunction<ServerResponse> nested =
        RouterFunctions.nest(
                RequestPredicates.path("/api/books"), child);
~~~

Đây là một phiên bản khác của nesting, hữu ích khi child router đã được dựng sẵn. Nó vẫn tuân theo quy tắc path matching và order của router con; không thêm round trip, HTTP redirect hay Spring bean mới cho mỗi request.

## 9. Tổ chức tuyến theo tính năng (feature-oriented routing)

### 9.1. Thiết kế thư mục

~~~text
com.example.catalog
├── CatalogApplication.java
├── config/
│   └── ApiRoutesConfiguration.java    # composition root
├── books/
│   ├── Book.java
│   ├── CreateBook.java
│   ├── BookStore.java
│   ├── BookHandler.java
│   └── BookRoutes.java                # child router
├── authors/
│   ├── AuthorHandler.java
│   └── AuthorRoutes.java
└── health/
    └── HealthHandler.java
~~~

**Một feature sở hữu** DTO/validation/handlers/routing, application root sở hữu URL prefix và thứ tự giữa các modules. Service/domain không phụ thuộc trực tiếp vào HTTP routing nếu hệ thống lớn; ví dụ demo gom những thứ đơn giản cho dễ chạy.

### 9.2. So với một router toàn cục

| Router toàn cục rất lớn | Feature-oriented routers |
| --- | --- |
| Dễ tìm mọi route ở một file ban đầu | Routes và handler gần cùng feature |
| Prefix và Accept có thể lặp | Prefix/conditions có thể group tại một nơi |
| Conflict khó review khi API lớn | Ownership mỗi nhóm rõ ràng |
| Một thay đổi có thể gây merge conflicts | Team làm việc song song theo module |
| Có nguy cơ một catch-all che mọi nhóm | Composition root kiểm soát thứ tự |
| Cấu hình filters có thể lan sang toàn API | Có thể áp filter đúng phạm vi |
| Dễ khi dự án rất nhỏ | Tốt khi nhiều bounded contexts |

Không tách 20 classes cho một API chỉ có một route. Dùng feature modules khi chúng **giảm coupling, lặp và xung đột** chứ không phải để chạy theo một chuẩn folder layout máy móc.

### 9.3. Ownership phải nhất quán

Chọn một trong hai cách:
1. **Module sở hữu absolute path** (`/api/books/...`) và root ghép bằng `and/add`.
2. **Root sở hữu prefix** (`/api/books`), module chỉ định child paths (`""`, `/{id}`, `/featured`) rồi root dùng `path(prefix, supplier)`.

Ví dụ bên dưới dùng **cách 2**. Không kết hợp vô ý cả hai, không copy prefix vào module và composition root cùng lúc.

### 9.4. Bean lifecycle và registration

Trong Boot WebFlux, một `RouterFunction<ServerResponse>` **bean** được tìm bởi routing infrastructure. Nếu chỉ tạo một RouterFunction local bên trong method nhưng không đăng ký hoặc gắn vào một bean hợp thành, route có thể không tồn tại ở runtime.

Bài demo dùng **một bean duy nhất `apiRoutes`** ở composition root. `BookRoutes` và `AuthorRoutes` là Spring components có method `routes()` trả router con, **không** đăng ký mỗi router con như một RouterFunction bean độc lập; nhờ vậy không có bản route child path `/{id}` lọt ra ngoài global prefix.

## 10. Bộ lọc `before`, `after` và `filter` theo phạm vi

Functional WebFlux cho phép filter trên router function/builder:
- `before(Function<ServerRequest,ServerRequest>)`: biến đổi request trước handler.
- `after(BiFunction<ServerRequest,ServerResponse,ServerResponse>)`: xử lý response object sau handler.
- `filter(HandlerFilterFunction<ServerResponse,ServerResponse>)`: bao quanh invocation và gọi `next.handle(request)`.
- `onError(...)`: biến exception thành `Mono<ServerResponse>` phù hợp theo API reactive.

Ví dụ conceptual fragment:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api/books", books -> books
                        .GET("/{id}", handler::byId)
                        .before(req -> ServerRequest.from(req)
                                .header("X-Route-Scope", "books")
                                .build()))
                .after((req, response) -> response)
                .build();
~~~

`before` nằm ở nhánh books sẽ áp cho routes trong nhánh đó; `after` nằm ở builder ngoài áp cho những routes được builder xây, bao gồm routes con. Đừng dùng headers nhân tạo như `X-Route-Scope` làm permission/identity; nó chỉ minh họa cách builder scope hoạt động.

### 10.1. `filter` và error propagation

~~~java
.filter((request, next) ->
        next.handle(request)
                .doOnError(error -> {
                    // Chỉ log metadata phù hợp chính sách, không log token/body.
                }))
~~~

Filter reactive có thể quan sát hoặc biến đổi publisher, nhưng **không nên gọi `block()`**. Một filter không gọi `next.handle(request)` có thể chủ động trả alternate response, phù hợp rate limits/feature gates khi thiết kế kỹ, nhưng **không thay** Spring Security.

### 10.2. Security và request matching

Routing predicates quyết định **handler**, không quyết định **user có quyền**. Spring Security WebFlux dùng `SecurityWebFilterChain` và `ServerHttpSecurity`, thường xử lý security trước functional handlers. Với sách thuộc tenant, kiểm ownership/tenant ở service hoặc auth layer dù route match đúng `/api/books/{id}`.

Router filter custom không tự có CSRF/session/cookie security. Nếu frontend cross-origin, cần CorsWebFilter và preflight phối hợp đúng security; hãy cấu hình theo chuẩn WebFlux, không copy `HttpSecurity` của Servlet MVC.

### 10.3. `onError` không bắt mọi lỗi toàn stack

Một `onError` trong router có thể xử lý exceptions của handler/filter thuộc phạm vi router. Lỗi ở gateway, proxy, security filters trước router hoặc response đã commit có thể cần error mechanisms riêng. Với JSON error contract ổn định, nên có common error model/handlers và integration tests; đừng catch `Throwable` bừa bãi rồi trả HTTP 200.

### 10.4. Phạm vi filter theo nested group

Nếu chỉ muốn biến đổi các GET JSON của Books, gắn filter vào builder **bên trong `nest(accept(JSON), ...)`**. Nếu muốn mọi Books route kể cả POST, gắn lên **BookRoutes builder**. Nếu muốn cả Books và Authors, gắn ở **apiRoutes composition builder**.

Sơ đồ:

~~~text
API builder-level filter
|
+-- /health
+-- /books    <- Books builder-level filter
|    |
|    +-- nest(accept(JSON)) <- GET JSON-only filter
|    |     +-- /featured
|    |     +-- /{id}
|    |     +-- collection list
|    +-- POST
|
+-- /authors  <- Authors builder-level filter
~~~

Luôn kiểm thứ tự và phạm vi filter bằng tests (header/trace logging) khi thêm nhiều modules. Đừng dùng wildcard filter scope rồi vô tình áp audit/payload logging vào endpoint login hoặc dữ liệu nhạy cảm.

## 11. Ví dụ chạy được: Java 17 / Spring Boot 3.3.5 / WebFlux.fn

Ta tạo một ứng dụng với:
- `GET /api/health` trả plain text `OK`.
- `GET /api/books` trả danh sách JSON.
- `GET /api/books/featured?mode=summary` trả sách nổi bật.
- `GET /api/books/featured` thiếu tham số trả 400 với error code xác định, **không lọt vào `/{id}`**.
- `GET /api/books/{id}` trả sách hoặc 404; ID sai format trả 400.
- `POST /api/books` nhận JSON và tạo sách, trả 201 + Location; title trống trả 400.
- `GET /api/authors` và `GET /api/authors/{id}` được khai báo ở feature router độc lập.
- Một `@Bean RouterFunction<ServerResponse> apiRoutes` duy nhất hợp thành ba modules.

Ví dụ sử dụng in-memory store, không có authentication/database. Dù in-memory data được tạo an toàn trên một JVM, nó **không** chia sẻ giữa nhiều instances/pods và không thay repository production. HTTP tests xác minh behavior mà không dựa vào thứ tự test.

~~~text
webflux-feature-router/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/catalog/
    │   ├── CatalogApplication.java
    │   ├── config/ApiRoutesConfiguration.java
    │   ├── books/
    │   │   ├── Book.java
    │   │   ├── CreateBook.java
    │   │   ├── BookStore.java
    │   │   ├── BookHandler.java
    │   │   └── BookRoutes.java
    │   ├── authors/
    │   │   ├── AuthorHandler.java
    │   │   └── AuthorRoutes.java
    │   └── health/HealthHandler.java
    └── test/java/com/example/catalog/
        └── FeatureRouterIntegrationTest.java
~~~

### 11.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'webflux-feature-router'
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
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Không thêm Spring MVC starter. Spring Boot quản lý phiên bản Spring WebFlux và Reactor tương thích. Cần Gradle wrapper tương thích Java 17/Boot 3.3.5 khi chạy các lệnh `./gradlew`.

### 11.2. Main application

`src/main/java/com/example/catalog/CatalogApplication.java`:

~~~java
package com.example.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
~~~

### 11.3. Book domain types

`src/main/java/com/example/catalog/books/Book.java`:

~~~java
package com.example.catalog.books;

public record Book(long id, String title) {}
~~~

`src/main/java/com/example/catalog/books/CreateBook.java`:

~~~java
package com.example.catalog.books;

public record CreateBook(String title) {}
~~~

Record DTOs dùng Java 17. Khi deserialize JSON `{"title":"Reactive Routing"}`, Jackson hỗ trợ canonical record constructors theo starter WebFlux. Ví dụ validate title trong handler; production nên áp schema/Bean Validation, độ dài/tập ký tự, authorization và request-size limit.

### 11.4. BookStore: repository giả lập thread-safe

`src/main/java/com/example/catalog/books/BookStore.java`:

~~~java
package com.example.catalog.books;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class BookStore {

    private final ConcurrentMap<Long, Book> books =
            new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong(2);

    public BookStore() {
        books.put(1L, new Book(1L, "Spring WebFlux"));
        books.put(2L, new Book(2L, "Reactor Patterns"));
    }

    public Flux<Book> all() {
        return Flux.defer(() -> Flux.fromIterable(
                books.values().stream()
                        .sorted(Comparator.comparingLong(Book::id))
                        .toList()));
    }

    public Mono<Book> find(long id) {
        return Mono.defer(() -> Mono.justOrEmpty(books.get(id)));
    }

    public Mono<Book> create(String title) {
        return Mono.fromSupplier(() -> {
            long id = sequence.incrementAndGet();
            Book book = new Book(id, title);
            books.put(id, book);
            return book;
        });
    }
}
~~~

**Lưu ý về tính reactive:** `ConcurrentHashMap` chỉ dùng bộ nhớ local và thao tác rất nhanh; `Mono.defer/fromSupplier` khiến việc đọc/ghi diễn ra khi subscription, nhưng **không tự làm blocking database calls non-blocking**. Không thay `books.get(id)` bằng JDBC `jdbcTemplate.queryForObject` trong lambda rồi để nó chạy trên event loop. Nếu dùng JDBC legacy, isolate qua `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`; nếu cần reactive DB end-to-end, cân nhắc R2DBC đúng driver/transaction stack.

### 11.5. BookHandler

`src/main/java/com/example/catalog/books/BookHandler.java`:

~~~java
package com.example.catalog.books;

import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class BookHandler {

    public record ApiError(String code, String message) {}

    private final BookStore store;

    public BookHandler(BookStore store) {
        this.store = store;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.all(), Book.class);
    }

    public Mono<ServerResponse> featured(ServerRequest request) {
        return store.find(1L)
                .flatMap(book -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> featuredHelp(ServerRequest request) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError(
                        "FEATURED_MODE_REQUIRED",
                        "Use mode=summary"));
    }

    public Mono<ServerResponse> byId(ServerRequest request) {
        String raw = request.pathVariable("id");
        long id;

        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return invalidId();
        }

        if (id <= 0) {
            return invalidId();
        }

        return store.find(id)
                .flatMap(book -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> invalidId() {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError(
                        "INVALID_BOOK_ID", "id must be a positive integer"));
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateBook.class)
                .flatMap(input -> {
                    if (input.title() == null
                            || input.title().isBlank()
                            || input.title().length() > 100) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new ApiError(
                                        "INVALID_BOOK_TITLE",
                                        "title is required and max 100 chars"));
                    }

                    return store.create(input.title().trim())
                            .flatMap(book ->
                                    ServerResponse.created(
                                            URI.create("/api/books/" + book.id()))
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(book));
                })
                .switchIfEmpty(ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new ApiError(
                                "MISSING_BODY", "JSON body is required")));
    }
}
~~~

Điểm quan trọng:
- Tất cả handler methods trả `Mono<ServerResponse>` (hoặc `Mono` từ body publisher), không dùng Servlet `ServerResponse` sync.
- `ServerResponse.ok().body(store.all(), Book.class)` cho phép framework stream/encode `Flux<Book>` theo JSON codec; đây là array response cho use case thông thường với Jackson/WebFlux.
- Trong `create`, **chỉ subscribe khi HTTP framework xử lý request**; không gọi `request.bodyToMono(...).block()`.
- Bad ID được map 400, missing book được map 404, thiếu `mode=summary` được map 400 có mã ổn định.
- Demo không làm authentication/tenant authorization. Trước khi trả Book thật, service production phải kiểm principal và quyền đối với resource.

### 11.6. BookRoutes: child paths không có `/api/books`

`src/main/java/com/example/catalog/books/BookRoutes.java`:

~~~java
package com.example.catalog.books;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RequestPredicates.queryParam;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
public class BookRoutes {

    private final BookHandler handler;

    public BookRoutes(BookHandler handler) {
        this.handler = handler;
    }

    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
                .nest(accept(MediaType.APPLICATION_JSON), json -> json
                        .GET("/featured",
                                queryParam("mode", "summary"),
                                handler::featured)
                        .GET("/featured", handler::featuredHelp)
                        .GET("/{id}", handler::byId)
                        .GET("", handler::list))
                .POST("", contentType(MediaType.APPLICATION_JSON),
                        handler::create)
                .build();
    }
}
~~~

Đây là một **router con**, nên không có `/api/books` trong các paths. Nó được bọc ở composition root bằng `path("/books", bookRoutes::routes)` trong nhánh `path("/api", ...)`.

**Order có chủ đích:**
1. Featured có query param `mode=summary` là tuyến cụ thể nhất.
2. Featured fallback không có mode đứng sau.
3. Variable route `/{id}` đứng sau static `/featured`.
4. Collection route `GET("")` đứng sau các GET khác.
5. POST có `contentType(JSON)` nhưng **không nằm trong nest Accept(JSON)** vì Accept và Content-Type có vai trò khác.

Nếu bạn đổi thứ tự để `/{id}` đứng đầu, `GET /api/books/featured` có thể vào `byId`, khiến API error code khác. WebTestClient bên dưới kiểm chính xác error message để phát hiện regression này.

### 11.7. AuthorHandler: feature độc lập

`src/main/java/com/example/catalog/authors/AuthorHandler.java`:

~~~java
package com.example.catalog.authors;

import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuthorHandler {

    public record Author(long id, String name) {}

    private static final List<Author> AUTHORS =
            List.of(new Author(1L, "An"),
                    new Author(2L, "Binh"));

    public Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AUTHORS);
    }

    public Mono<ServerResponse> byId(ServerRequest request) {
        String id = request.pathVariable("id");

        return AUTHORS.stream()
                .filter(author ->
                        Long.toString(author.id()).equals(id))
                .findFirst()
                .map(author -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(author))
                .orElseGet(() -> ServerResponse.notFound().build());
    }
}
~~~

Ở đây `byId` so sánh path variable với ID dạng string; input không đúng định dạng trả 404, khác với Book handler có policy 400. Đây là minh họa **để bạn nhận ra policies theo feature cần thống nhất khi phát hành API chung**. Nếu muốn error contracts đồng nhất, dùng common validation/error model và tests. Danh sách authors là in-memory fixture, không DB.

### 11.8. AuthorRoutes: router con đơn giản

`src/main/java/com/example/catalog/authors/AuthorRoutes.java`:

~~~java
package com.example.catalog.authors;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
public class AuthorRoutes {

    private final AuthorHandler handler;

    public AuthorRoutes(AuthorHandler handler) {
        this.handler = handler;
    }

    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route()
                .GET("/{id}", handler::byId)
                .GET("", handler::list)
                .build();
    }
}
~~~

Không dùng bean `RouterFunction` ở module này: chỉ có `@Component AuthorRoutes` và method trả router khi root composer gọi.

### 11.9. HealthHandler: tách cơ chế vận hành

`src/main/java/com/example/catalog/health/HealthHandler.java`:

~~~java
package com.example.catalog.health;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class HealthHandler {

    public Mono<ServerResponse> health(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("OK");
    }
}
~~~

Demo `/api/health` không gọi network/JDBC. Với production liveness/readiness, cân nhắc Spring Boot Actuator `/actuator/health`, Kubernetes probes và security policy phù hợp; đừng nhầm một response “OK” với hệ thống đủ sẵn sàng cho tất cả dependency calls.

### 11.10. Composition root — lớp quyết định URL public

`src/main/java/com/example/catalog/config/ApiRoutesConfiguration.java`:

~~~java
package com.example.catalog.config;

import com.example.catalog.authors.AuthorRoutes;
import com.example.catalog.books.BookRoutes;
import com.example.catalog.health.HealthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ApiRoutesConfiguration {

    @Bean
    RouterFunction<ServerResponse> apiRoutes(
            BookRoutes books, AuthorRoutes authors,
            HealthHandler health) {

        return RouterFunctions.route()
                .path("/api", api -> api
                        .GET("/health", health::health)
                        .path("/books", books::routes)
                        .path("/authors", authors::routes))
                .build();
    }
}
~~~

**Vì sao đây là composition root:**
- Chỉ một `@Bean RouterFunction<ServerResponse>` được công bố cho WebFlux routing infrastructure.
- Prefix `/api` bao trùm API; `/books` và `/authors` được định nghĩa ở đây, không trong child module.
- `BookRoutes` và `AuthorRoutes` vẫn là Spring-managed components để nhận dependency injection, nhưng methods `routes()` không bị tự đăng ký như top-level router beans.
- `/api/health` là route riêng, không bị `accept(JSON)` của Books ảnh hưởng.
- Cây router dễ đọc khi review; thứ tự compose ở đây có ý nghĩa nếu các child routers có overlapping/catch-all paths.

Nếu muốn thêm `/api/v2/books`, có thể compose một BookRoutesV2 dưới một prefix mới và giữ v1. Không tự tái sử dụng một module không tương thích DTO rồi nghĩ chỉ thêm `/v2` là đủ để version API.

## 12. WebTestClient kiểm chứng nesting, order và module boundaries

`WebTestClient` phù hợp cho reactive Spring WebFlux. Chúng ta dùng embedded server trên port ngẫu nhiên để kiểm thực sự HTTP routing/body/status. Không dùng Servlet `MockMvc` như thể chúng là cùng programming model.

`src/test/java/com/example/catalog/FeatureRouterIntegrationTest.java`:

~~~java
package com.example.catalog;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FeatureRouterIntegrationTest {

    @Autowired
    WebTestClient client;

    @Test
    void healthIsIndependentFromBooksJsonCondition() {
        client.get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");
    }

    @Test
    void bookCollectionIsUnderNestedPrefix() {
        client.get().uri("/api/books")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[1].id").isEqualTo(2);
    }

    @Test
    void bookDetailMatchesVariableRoute() {
        client.get().uri("/api/books/2")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reactor Patterns");
    }

    @Test
    void featuredQueryPredicateBeatsFallback() {
        client.get().uri("/api/books/featured?mode=summary")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void featuredWithoutModeDoesNotHitIdHandler() {
        client.get().uri("/api/books/featured")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("FEATURED_MODE_REQUIRED")
                .jsonPath("$.message")
                    .isEqualTo("Use mode=summary");
    }

    @Test
    void invalidIdHasExplicitBusinessError() {
        client.get().uri("/api/books/abc")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BOOK_ID");
    }

    @Test
    void missingBookReturnsNotFound() {
        client.get().uri("/api/books/9999")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void postBookCreatesResourceWithLocation() {
        client.post().uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Reactive Routing"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches(
                        HttpHeaders.LOCATION, "/api/books/[0-9]+")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reactive Routing");
    }

    @Test
    void blankBookTitleReturns400() {
        client.post().uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "   "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_BOOK_TITLE");
    }

    @Test
    void authorListIsIndependentModule() {
        client.get().uri("/api/authors")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("An")
                .jsonPath("$[1].name").isEqualTo("Binh");
    }

    @Test
    void authorDetailIsNestedUnderAuthors() {
        client.get().uri("/api/authors/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("An");
    }
}
~~~

### 12.1. Vì sao phải assert JSON chứ không chỉ status?

Nếu `GET("/{id}")` được chuyển lên trước `GET("/featured")`, URL `featured` có thể vào handler ID và trả **400**. Nếu test chỉ assert `status().isBadRequest()`, nó vẫn pass dù route sai. Vì vậy test kiểm cả `code=FEATURED_MODE_REQUIRED` và message; đây là **contract test cho thứ tự route**.

### 12.2. Vì sao tests không phụ thuộc thứ tự execution?

BookStore mặc định có ID 1 và 2, cả hai không bị delete. POST test tạo thêm sách với AtomicLong nhưng không assert fixed ID 3. Tests cho initial IDs kiểm hai phần tử đầu được sort tăng dần, nên vẫn ổn ngay cả khi POST chạy trước. Nếu thêm delete/mutate fixtures hoặc nhiều test instances, nên reset repository theo test hoặc khởi tạo context độc lập.

### 12.3. Thử negative Accept trong integration tests

Một bài tập mở rộng là gửi `Accept: text/html` đến `GET /api/books/1` để xác nhận **nhánh nested JSON không match**. HTTP status cuối có thể khác tùy handler mappings/global error handling, vì thế không hard-code một status chưa xác minh. Test có thể bắt đầu bằng assertion rằng response **không** phải 200 với JSON Book, rồi khi có policy rõ, chốt exact status/error contract.

### 12.4. Chạy tests

Trong project có Gradle wrapper đã tạo tương thích Java 17:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Windows PowerShell:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Tài liệu nguồn mô tả mọi class/code files, nhưng không kèm scripts của Gradle wrapper. Nếu chưa có wrapper, tạo từ Gradle installation phù hợp; `build.gradle` không tự sinh `gradlew`. Không có database migration hay external APIs, nên output HTTP deterministic ngoại trừ ID tạo mới theo trạng thái process.

## 13. Chạy với curl

### 13.1. Health

~~~bash
curl -i 'http://localhost:8080/api/health'
~~~

Dự kiến HTTP 200, Content-Type text/plain, body:

~~~text
OK
~~~

### 13.2. Books collection và details

~~~bash
curl -i 'http://localhost:8080/api/books' \
  -H 'Accept: application/json'

curl -i 'http://localhost:8080/api/books/2' \
  -H 'Accept: application/json'
~~~

Danh sách ban đầu:

~~~json
[
  {"id":1,"title":"Spring WebFlux"},
  {"id":2,"title":"Reactor Patterns"}
]
~~~

Chi tiết ID 2:

~~~json
{"id":2,"title":"Reactor Patterns"}
~~~

JSON response thực tế có thể một dòng; đây là phiên bản dễ đọc. Controller/handler có thể trả 404 nếu ID không tồn tại, không tự ép mọi query thành 200.

### 13.3. Featured đúng query và fallback

~~~bash
curl -i 'http://localhost:8080/api/books/featured?mode=summary' \
  -H 'Accept: application/json'

curl -i 'http://localhost:8080/api/books/featured' \
  -H 'Accept: application/json'
~~~

Với `mode=summary`, response JSON sách ID 1. Không có mode, response 400:

~~~json
{"code":"FEATURED_MODE_REQUIRED","message":"Use mode=summary"}
~~~

Đây là bằng chứng nhóm static routes đã được chọn trước variable `/{id}`.

### 13.4. Tạo sách

~~~bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Functional WebFlux"}'
~~~

Dự kiến HTTP 201, `Location: /api/books/3` trên JVM mới chưa có POST nào, cùng JSON:

~~~json
{"id":3,"title":"Functional WebFlux"}
~~~

ID sẽ thay đổi nếu có POST trước đó; không đặt fixed ID vào client business logic. Endpoint demo dùng in-memory store và không có auth; production cần DB/validation/authorization/idempotency phù hợp.

### 13.5. Author routes

~~~bash
curl -i 'http://localhost:8080/api/authors'
curl -i 'http://localhost:8080/api/authors/1'
~~~

Ban đầu authors list:

~~~json
[
  {"id":1,"name":"An"},
  {"id":2,"name":"Binh"}
]
~~~

Chi tiết:

~~~json
{"id":1,"name":"An"}
~~~

Phần này chứng minh **AuthorRoutes** và **BookRoutes** có thể được compose dưới cùng `/api` mà không phải chung một handler class.

## 14. Hợp thành qua nhiều bean RouterFunction: nên và không nên

Trong Spring Boot WebFlux có thể khai báo nhiều beans `RouterFunction<ServerResponse>`, framework sẽ thu thập/ghép route theo infrastructure. Tuy vậy, nếu các routers có overlapping paths, việc dựa vào registration order không tường minh có thể làm API biến động khi refactor.

**Khuyến nghị thực dụng:** với hệ thống có nhiều module và path prefix chung, dùng một bean composition root như ví dụ; thứ tự được viết rõ trong code và có integration tests. Nếu cố ý dùng nhiều top-level RouterFunction beans, tài liệu hóa `@Order`/ordering semantics đúng version framework, thêm conflict detection/tests và giữ quyền sở hữu paths rõ ràng.

Một lỗi dễ gặp: child module đã tạo bean route `GET("/{id}")` nhưng root lại compose nó dưới `/api/books`; route relative `/{id}` có thể được framework đăng ký ngoài prefix khi bean tồn tại độc lập. Nếu module chỉ cung cấp child routes để root compose, **đừng đánh dấu child RouterFunction là `@Bean` riêng**.

### 14.1. Versioning qua root composer

Hai phiên bản API có thể dùng modules riêng:

~~~java
RouterFunctions.route()
        .path("/api/v1/books", v1BookRoutes::routes)
        .path("/api/v2/books", v2BookRoutes::routes)
        .build();
~~~

Fragment này cần hai components. Một số dự án có thể dùng header/query/media type versioning; với Spring Framework 7 có version-specific routing infrastructure mới hơn. **Đừng dùng `RequestPredicates.version(...)` theo tài liệu Spring 7 trong Spring Boot 3.3.x**, trừ khi bạn nâng cả platform tương thích.

### 14.2. Giữ business logic dùng chung khi hợp lý

Router v1/v2 có thể dùng shared domain service nhưng DTO adapters khác nhau. Không expose persistence entities trực tiếp ra mọi API versions; một refactor entity có thể vô tình phá cả v1. Nếu business semantics khác thật, cần policy rõ thay vì chỉ đổi name của router.

## 15. CORS, authentication và errors với nhiều feature routers

### 15.1. CORS cho WebFlux functional endpoints

Spring WebFlux.fn sử dụng CORS infrastructure thường qua `CorsWebFilter` khi cần global/functional routing. CORS policy không tự được tạo bởi `path("/api", ...)` hay `nest(accept(JSON), ...)`. Browser POST JSON cross-origin thường preflight; cần allow đúng Origin/Methods/Headers và phối hợp với Spring Security reactive.

**Không dùng `Access-Control-Allow-Origin: *` cùng credentialed browser requests.** Với cookie authentication phải xét `SameSite`, `Secure`, CSRF protection và thực tế third-party cookies. CORS không chứng minh user được cấp quyền.

### 15.2. Spring Security filter chain

Các tuyến functional WebFlux vẫn đi qua reactive `SecurityWebFilterChain` nếu dependency/config được bật. Có thể phân policy:
- `/api/health` public theo threat model.
- `/api/books/**` authenticated, POST cần write permission.
- `/api/authors/**` read policy độc lập.
- Mọi route ngoài allowlist deny by default.

Đây chỉ là design guideline; demo không kèm security config nên các endpoints có thể gọi công khai. Đừng vận hành production với data private theo mô hình demo mà thiếu auth.

### 15.3. Lỗi và `ProblemDetail`

Router không match là một loại lỗi routing; handler ném exception là loại khác; security layer 401/403 có thể xảy ra trước handler. Nếu sản phẩm muốn một JSON schema như RFC 9457 `ProblemDetail` với `code` ổn định, thiết kế common error handling cho đúng từng layer và test 400/401/403/404/405/406/415/500. Tránh bắt `Throwable` rồi trả 200 vì đã “chuẩn hóa” body.

### 15.4. Filter and auditing

Router-level filter có thể gắn X-Trace-Id, đo latency, audit module. Tuy nhiên:
- Không log body/Authorization/Cookie/tokens.
- Không dùng raw unbounded headers để đặt metric label.
- Không dùng request ThreadLocal để giữ security context trong Reactor pipelines có thể đổi scheduler.
- Nếu cần tracing, dùng Reactor Context và integrations tương thích (Micrometer/Spring Security).
- Lỗi response đã commit có thể không đổi status/headers được như trước commit.

## 16. Routing functional và annotated controllers có thể cùng tồn tại

Spring WebFlux hỗ trợ cả annotated controllers và functional routes. Nên tránh:
- Cùng `GET /api/books/{id}` được đăng ký ở hai mô hình.
- Một global catch-all RouterFunction vô tình che static resources và annotated endpoints.
- Dùng error formatter ở annotated controller nhưng quên functional handler.
- Dùng `@Valid @RequestBody` expectations khi functional handler chỉ gọi `bodyToMono` không có validator.
- Dựa vào thứ tự mapping của Spring WebFlux infrastructure mà không có test.

Trong codebase lớn, thống nhất **ownership cho mỗi URL/method** và quy tắc chuyển API sang mô hình mới. Không refactor hàng loạt sang RouterFunction chỉ vì syntax trông ngắn hơn; nếu annotations đang hoạt động tốt và có OpenAPI tooling phù hợp thì có thể giữ chúng.

## 17. Hiệu năng và reactive stream không bị thay đổi vì nesting

Nesting/hợp thành routers chủ yếu là chuyện **matching/declaration**, không phải kỹ thuật tăng tốc network I/O. Một số hiểu nhầm:

| Hiểu nhầm | Thực tế |
| --- | --- |
| "RouterFunction tự làm JDBC non-blocking" | Không; JDBC có thể chặn event loop, cần offload hoặc reactive driver |
| "Nesting tạo thread pool riêng" | Không; chỉ tổ chức match predicates |
| "Mỗi feature có RouterFunction thì độc lập resource quota" | Không; vẫn có thể chia sẻ Netty loops/schedulers/DB pool |
| "and() chạy song song các handlers" | Không; nó chọn handler đầu tiên match |
| "Flux đã có backpressure nên database không thể quá tải" | Cần concurrency/connection-pool budgets, bounded buffers và admission |
| "before/after filters sẽ tự bảo mật mọi route" | Không; SecurityWebFilterChain/domain auth độc lập |
| "Một request chạy cả module A và module B" | Thường chỉ một route handler được chọn nếu match |
| "Router order không quan trọng vì Spring chọn pattern cụ thể" | Thứ tự trong functional composition rất quan trọng |

Nếu feature gọi service blocking từ handler, dùng Reactor `Mono.fromCallable(...).subscribeOn(boundedElastic)` hoặc dedicated worker pool theo dependency load. Với WebClient/R2DBC non-blocking, return publishers và tránh `block()`. Đo p95/p99, Netty event-loop starvation, connection pool, memory pressure và error rates thay vì giả định tổ chức router sẽ tự cải thiện throughput.

## 18. Troubleshooting: sai route và sai module ownership

| Triệu chứng | Nguyên nhân | Hướng khắc phục |
| --- | --- | --- |
| `RouterFunction` không được nhận | Nhầm package WebMvc.fn hoặc không đăng ký bean | Dùng reactive package và composition bean |
| `GET /api/books` 404 | Nested prefix/child path sai | Vẽ full URL từng path cấp |
| `/api/books/featured` vào ID handler | `/{id}` đứng trước static route | Đặt static trước và assert error code |
| `GET /api/books` trả dữ liệu Authors | Compose router rộng/catch-all sai | Ownership/prefixes tường minh |
| `path("/api/books", ...)` tạo double prefix | Module cũng khai báo `/api/books` | Module child paths tương đối |
| `GET /api/books/1` trả không match khi Accept text/html | `nest(accept(JSON))` áp lên GET | Kiểm client Accept, policy 406 |
| POST body JSON bị 415 | Content-Type sai hoặc decoder không hỗ trợ | Kiểm `contentType(JSON)` và codecs |
| Query `mode=summary` không được nhận | Import nhầm `param` hoặc thiếu query | Dùng reactive `queryParam` |
| `@Bean` child routes chạy ngoài prefix | Đăng ký router con riêng ngoài composition | Chỉ root bean khi dùng relative routers |
| Filter GET áp nhầm POST | Filter đặt ở builder cha thay vì nested GET group | Chuyển vào đúng `nest` scope |
| Security policy chỉ bảo vệ annotations | Request matchers chưa phủ functional URL | Kiểm SecurityWebFilterChain với WebTestClient |
| CORS preflight bị 401 | Auth xử lý OPTIONS trước CORS | Config CorsWebFilter/Security đúng |
| Errors khác giữa modules | Mỗi handler tự thiết kế status/body | Common error contract và tests |
| GET byId với "abc" trả status khó đoán | Không validate path variable | Parse/validate và typed 400 |
| Dữ liệu test tăng ID tùy thứ tự | Shared mutable in-memory store | Test isolation, không assert absolute next ID |
| 2 RouterFunction beans có conflict | Không rõ registration order | Single composition root hoặc @Order có test |
| Spring Boot chạy MVC | Lẫn `starter-web` với WebFlux starter | Chọn platform đúng |
| WebFlux handler gọi `block()` | Nhầm reactive type với non-blocking | Return publishers, offload blocking legacy |
| HTTP 200 nhưng payload sai | Route đầu match nhầm handler | Assert body/schema/error code/headers |
| OpenAPI không thấy functional route | Tool chỉ đọc annotation mapping | Explicit OpenAPI registration/annotations phù hợp |

### Quy trình debug

1. Xác minh Gradle dependencies và imports thuộc **WebFlux.fn**.
2. Kiểm chỉ những RouterFunction beans mong đợi được register.
3. Vẽ route tree của composition root cùng child paths tương đối.
4. Rà route order trong từng module: static → constrained → variable → fallback.
5. Ghi method, full path, query, Accept, Content-Type, headers — không log secrets.
6. Kiểm nest predicates và filter scope theo tầng.
7. Phân biệt route non-match, handler validation error và security filter error.
8. Kiểm bodyToMono/codec và response type `Mono<ServerResponse>`, không block.
9. Chạy WebTestClient success/negative tests có assertions đúng handler/schema.
10. Nếu có gateway/CDN/rewrite, kiểm HTTP thực end-to-end sau embedded integration tests.

## 19. Checklist thiết kế module router

- [ ] Root owns public prefixes hoặc modules own full paths — chọn một nhất quán.
- [ ] Module handlers/services/repositories không phụ thuộc vào route của feature khác.
- [ ] Tạo một `RouterFunction<ServerResponse>` bean composition rõ ràng khi child paths tương đối.
- [ ] Mọi static/constrained routes đứng trước variable/wildcard/catch-all.
- [ ] Nest path prefix đúng, không nhân đôi `/api/books`.
- [ ] Nested Accept/Content-Type predicates chỉ áp cho nhóm mong muốn.
- [ ] Reactive WebFlux `queryParam` đúng package; không import Servlet `param`.
- [ ] Handler return `Mono<ServerResponse>`, không tự subscribe/block.
- [ ] DTO/input/path/headers được validate, JSON codec xử lý body một lần.
- [ ] HTTP status 400/404/201 và Location/error codes có contract rõ.
- [ ] Router filters có scope cụ thể và không thay auth/CSRF/CORS.
- [ ] Error mapping không leak exception internals hay thông tin user.
- [ ] Không dùng unbounded queues/schedulers cho blocking service.
- [ ] WebTestClient kiểm route correctness, **body**, chứ không chỉ status.
- [ ] Có test cho featured vs ID và collection vs detail/fallback.
- [ ] Auth/tenant ownership được thực thi cả trên nested routes.
- [ ] API versioning không phá hợp đồng DTO/business semantics cũ.
- [ ] Docs/OpenAPI bao phủ functional routes và client examples.
- [ ] CI chạy tests khi thêm/bớt modules để phát hiện route conflicts.
- [ ] Production observability dùng trace IDs và bounded metric labels.

### Câu hỏi tự kiểm tra

**1.** `path("/api/books", builder)` làm gì?

**Trả lời:** Tạo nhóm router với common path prefix; các child paths bên trong được match tương ứng dưới `/api/books`, không yêu cầu mỗi child lặp prefix.

**2.** `nest(accept(APPLICATION_JSON), ...)` có bắt buộc POST cùng builder trả JSON không?

**Trả lời:** Chỉ những routes bên trong nest chịu điều kiện Accept đó. POST bên ngoài nhánh nest không bị áp predicate chỉ vì cùng parent path.

**3.** Khi nào nên dùng `RouterFunction.and(...)` so với Builder `add(...)`?

**Trả lời:** Cả hai hợp thành routers theo thứ tự. `and` tiện nối hai RouterFunctions; `add` tiện thêm router đã tạo vào DSL builder. `path(..., supplier)` tốt khi composition root sở hữu URL prefix cho child router.

**4.** Điều gì xảy ra nếu `GET("/{id}")` đứng trước `GET("/featured")`?

**Trả lời:** Request `/featured` có thể match variable `{id}` trước, khiến handler sai được chọn. Test body/error code cần bắt regression đó.

**5.** Vì sao child router không nên tự là @Bean nếu root cũng dùng path prefix?

**Trả lời:** Một RouterFunction bean child có thể được đăng ký độc lập với relative URLs. Đặt một root RouterFunction bean và child modules là components thường giúp không leak routes ngoài prefix.

**6.** WebFlux.fn handler trả kiểu gì?

**Trả lời:** Thường `Mono<ServerResponse>`; Servlet WebMvc.fn trả `ServerResponse` sync. Chọn đúng imports và response builders.

**7.** Router predicate header có phải authorization không?

**Trả lời:** Không. Nó chỉ quyết định route match; auth/tenant access kiểm ở SecurityWebFilterChain/domain layer.

**8.** `queryParam("mode","summary")` khác `ServerRequest.queryParam("mode")` thế nào?

**Trả lời:** Factory `RequestPredicates.queryParam` dùng để **chọn route**; method trên `ServerRequest` dùng **đọc** query param sau khi chọn handler.

**9.** Hợp thành routers có tự tạo concurrency không?

**Trả lời:** Không. Nó tổ chức match/dispatch, không tự tạo worker threads hay làm blocking I/O trở thành non-blocking.

**10.** Vì sao test chỉ assert HTTP 400 cho `/featured` chưa đủ?

**Trả lời:** Handler ID sai cũng có thể trả 400. Cần assert error code/message để biết router đã chọn đúng fallback.

## 20. Tài liệu chính thức

- [Spring Framework — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): RouterFunction, nested path/Accept, composition, filters, handlers.
- [Spring Framework 6.1 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.1/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.Builder.html): `path`, `nest`, `add`, `GET`/`POST`, filter/onError.
- [Spring Framework 6.1 — RouterFunctions Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.html): `route`, `nest`, `toHttpHandler`.
- [Spring Framework 6.1 — RequestPredicates Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/reactive/function/server/RequestPredicates.html): `GET`, `POST`, `accept`, `contentType`, `queryParam`.
- [Spring Framework 6.1 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/reactive/function/server/ServerRequest.html): request path/query/headers/body decoding.
- [Spring Framework 6.1 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html): reactive body/status/headers.
- [Spring Framework — WebFlux DispatcherHandler](https://docs.spring.io/spring-framework/reference/web/webflux/dispatcher-handler.html): handler mappings/adapters/results.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): reactive HTTP-level integration testing.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): Boot WebFlux configuration và server.
- [Spring Security — Reactive Security](https://docs.spring.io/spring-security/reference/reactive/index.html): reactive SecurityWebFilterChain.
- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): CORS policies cho reactive endpoints.

**Ghi nhớ:** trong WebFlux.fn, `path` nhóm routes theo prefix, `nest` nhóm routes theo RequestPredicate, còn `and`/`andRoute`/`add` giúp hợp thành router modules. **Thứ tự matching và ownership của prefixes là trọng tâm thiết kế.** Một composition root rõ ràng, child routes tương đối, filter scope có chủ đích và WebTestClient assertions đúng handler giúp API nhiều tính năng vừa mở rộng được vừa dễ bảo trì.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-005">Đọc body bằng bodyToMono/bodyToFlux và kiểm tra dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc body bằng bodyToMono/bodyToFlux và kiểm tra dữ liệu trong Functional WebFlux

## 1. Vấn đề: HTTP body là luồng bytes, không phải đối tượng Java đáng tin

Khi client gửi request đến API:

```http
POST /api/books HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "title": "WebFlux thực hành",
  "contact": {"email": "reader@example.org"}
}
```

Server trước hết chỉ nhận **bytes**. Để xử lý nghiệp vụ, ứng dụng cần giải mã JSON, tạo DTO, kiểm tra dữ liệu, rồi mới gọi service hoặc lưu database. Spring WebFlux functional endpoints (thường gọi **WebFlux.fn**) cung cấp hai phương thức quan trọng trong `ServerRequest`:

- **`bodyToMono(Class<T>)`:** đọc/giải mã request body để tạo `Mono<T>`, phù hợp khi hợp đồng mong **một đối tượng** hoặc một giá trị.
- **`bodyToFlux(Class<T>)`:** tạo `Flux<T>` gồm **nhiều giá trị** được giải mã từ body, phù hợp các representation có framing cho nhiều phần tử, chẳng hạn NDJSON.

Cả hai dùng hệ thống `HttpMessageReader`/`Decoder` của WebFlux; chúng **không** tự xác nhận rằng dữ liệu đạt Bean Validation hoặc domain rules.

Đặc biệt, trong **functional handler** bạn thường phải gọi validator **tường minh**, khác cơ chế `@Valid @RequestBody` được hạ tầng Spring áp vào tham số của annotated controller. Nếu bỏ qua bước này, JSON có trường trống vẫn có thể được deserialize thành DTO và đi vào kho dữ liệu.

Bài thuộc nhóm **Functional WebFlux**, áp dụng cho **Java 17+, Spring Framework 6.2.x, Spring Boot 3.5.x, Reactor 3.7.x**, chạy WebFlux trên Reactor Netty. Các ví dụ không dùng Servlet MVC và không trộn packages `org.springframework.web.servlet.function` với `org.springframework.web.reactive.function.server`.

### Sau bài này, bạn sẽ làm được

1. Chọn `bodyToMono` hay `bodyToFlux` theo đúng HTTP content representation.
2. Biết `ServerRequest.body(BodyExtractors.toMono/toFlux)` tương đương phương thức tiện ích.
3. Dùng `jakarta.validation.Validator` hoặc Spring `Validator` để validate trước side effect.
4. Phân biệt **body rỗng**, **JSON hỏng**, **đúng JSON nhưng vi phạm constraint**, **sai Content-Type**, **request quá lớn**, **sai business rule**.
5. Xử lý lỗi reactive với `onErrorResume` theo loại, không bắt mọi lỗi rồi đổi thành 400.
6. Biết streaming theo từng record NDJSON, backpressure và xử lý lỗi giữa stream.
7. Thiết lập testing cho request hợp lệ và không hợp lệ với `WebTestClient`.
8. Kiểm soát memory/cancellation, tránh `collectList()` hay `block()` trên luồng dài.

## 2. Kiến trúc request body trong WebFlux.fn

```text
HTTP request
  |
  v
Reactor Netty -> ServerHttpRequest
  |
  v
Flux<DataBuffer>       (bytes / buffers)
  |
  v
ServerRequest
  |
  | bodyToMono(CreateBook.class)
  | bodyToFlux(ImportLine.class)
  | body(BodyExtractors.toMono(...))
  |
  v
HttpMessageReader + Decoder (chọn theo type và Content-Type)
  |
  +--> onError(DecodingException / WebFlux input errors)
  |
  v
Mono<DTO> hoặc Flux<DTO>
  |
  v
Validator / business rules (phải được gọi đúng chỗ)
  |
  +--> onError(input validation)
  |
  v
Reactive service / repository
  |
  v
Mono<ServerResponse>
  |
  v
HttpMessageWriter -> HTTP status + headers + body
```

Các thành phần:

| Thành phần | Nhiệm vụ |
| --- | --- |
| `RouterFunction<ServerResponse>` | Chọn route theo method, path và predicates |
| `HandlerFunction<ServerResponse>` | Nhận `ServerRequest` và trả `Mono<ServerResponse>` |
| `ServerRequest` | Truy cập headers, URI, query, path variables, body |
| `HttpMessageReader` | Đọc HTTP body thành một hay nhiều giá trị Java |
| `Decoder<T>` | Giải mã từ `DataBuffer`/bytes thành kiểu `T` |
| `Mono<T>` | Publisher có 0 hoặc 1 giá trị |
| `Flux<T>` | Publisher có 0 đến nhiều giá trị |
| `jakarta.validation.Validator` | Đánh giá ràng buộc Bean Validation trên object |
| `ServerResponse` | Khai báo status, headers, body, được ghi khi Mono response được subscribe |

Khác Spring MVC.fn, handler WebFlux.fn **trả `Mono<ServerResponse>`**:

```java
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

Mono<ServerResponse> handle(ServerRequest request) {
    return ServerResponse.ok().bodyValue("Hello");
}
```

Đoạn trên là **method fragment** cho một handler; `ServerResponse.ok().bodyValue(...)` tạo **Mono của ServerResponse**, không phải synchronous `ServerResponse` như MVC Servlet functional handlers.

## 3. `bodyToMono(Class<T>)`: một đối tượng trong request

Cú pháp cơ bản:

```java
Mono<CreateBook> body =
        request.bodyToMono(CreateBook.class);
```

Phù hợp với:

- JSON object cho create/update command.
- Một JSON scalar hoặc một string tùy media type/decoder.
- Metadata object cần kiểm tra trước khi ghi dữ liệu.
- Một body được client gửi đúng một lần, không phải vô hạn records.

Ví dụ:

```java
Mono<ServerResponse> create(ServerRequest request) {
    return request.bodyToMono(CreateBook.class)
            .flatMap(command ->
                    ServerResponse.ok().bodyValue(command));
}
```

**Đây chỉ là fragment minh họa deserialize**, chưa có validation, authorization hoặc persistence.

### 3.1. `Mono` là một publisher, không phải DTO đã có sẵn

Ngay sau `request.bodyToMono(CreateBook.class)`, bạn có một mô tả computation bất đồng bộ. Decoder/body data được tiêu thụ khi pipeline được subscribe bởi hạ tầng WebFlux. **Không** được:

```java
// Sai trong WebFlux request handling:
CreateBook command =
        request.bodyToMono(CreateBook.class).block();
```

`block()` trên Reactor Netty event loop có thể chặn processing, dẫn đến throughput thấp hoặc lỗi blocking operation not supported.

Cũng không tự subscribe rồi trả response thành công trước khi work kết thúc:

```java
// Sai: side effect rời khỏi HTTP response lifecycle:
request.bodyToMono(CreateBook.class)
        .subscribe(this::save);

return ServerResponse.accepted().build();
```

Nếu `save` thất bại, HTTP response không còn phản ánh kết quả; lỗi/cancel có thể bị xử lý sai. **Compose publisher và trả nguyên Mono tới WebFlux**.

### 3.2. Body rỗng khác JSON chứa null

Với `bodyToMono(T.class)`, **không có giá trị** có thể dẫn đến `Mono.empty()`. Một body thực sự rỗng và JSON literal `null` có thể dẫn đến behavior phụ thuộc decoder; không giả định mọi trường hợp có object non-null.

Một API yêu cầu body nên quy định riêng:

```java
Mono<CreateBook> required = request
        .bodyToMono(CreateBook.class)
        .switchIfEmpty(Mono.error(
                new IllegalArgumentException(
                        "Request body required")));
```

Fragment này minh họa `switchIfEmpty`; code production cần map exception thành **400 với error code ổn định** thay vì trả raw message. Dùng `defaultIfEmpty(...)` khi domain thật sự có giá trị mặc định hợp lệ; **không** tạo DTO giả để bỏ qua input bắt buộc.

### 3.3. Nếu body là JSON array?

```http
Content-Type: application/json

[
  {"sku":"A-1","quantity":2},
  {"sku":"B-2","quantity":3}
]
```

Có ít nhất hai hướng:

- Muốn **một aggregate object**: `bodyToMono(new ParameterizedTypeReference<List<ImportLine>>() {})`. Cần tích lũy list; giới hạn bộ nhớ theo tổng array.
- Muốn processing nhiều items: dùng `bodyToFlux(ImportLine.class)` khi reader hỗ trợ streaming/decode representation phù hợp; với một JSON array, mức độ decode incremental và buffering tùy codec.

**Ưu tiên NDJSON** nếu hợp đồng truyền vô số records riêng, mỗi dòng là một JSON object. Không giả định `Flux<T>` tự biến mọi body thành streaming tối ưu.

## 4. `bodyToFlux(Class<T>)`: nhiều phần tử nối tiếp

```java
Flux<ImportLine> items =
        request.bodyToFlux(ImportLine.class);
```

Phù hợp với **stream các record** trong content representation có framing rõ ràng, ví dụ:

```http
POST /api/books/import HTTP/1.1
Content-Type: application/x-ndjson

{"sku":"B-101","quantity":3}
{"sku":"B-102","quantity":5}
{"sku":"B-103","quantity":2}
```

`application/x-ndjson` (Newline Delimited JSON) diễn đạt: **mỗi dòng riêng là một JSON value**, khác một JSON array chung.

Một handler có thể dùng:

```java
Mono<ServerResponse> importLines(ServerRequest request) {
    return request.bodyToFlux(ImportLine.class)
            .map(this::validateAndNormalize)
            .reduce(
                new ImportSummary(0, 0),
                (summary, line) ->
                    new ImportSummary(
                        summary.rows() + 1,
                        summary.quantity() + line.quantity()))
            .flatMap(total ->
                ServerResponse.ok().bodyValue(total));
}
```

Đây là **fragment**, cần khai báo records, validator và method `validateAndNormalize`. Dùng `reduce` giữ hai số liệu, không `collectList()` mọi record trong RAM.

### 4.1. Flux có backpressure nhưng không có nghĩa tổng memory tự bị giới hạn

- `Flux<T>` dùng `request(n)` để phối hợp **nhu cầu phần tử** giữa publisher/subscriber.
- Decoder có thể phải buffer đến khi một JSON record hoàn chỉnh.
- `flatMap` có concurrency/prefetch và queues riêng.
- `collectList()` tích lũy **mọi item** trước khi xuất response.
- Một item rất lớn vẫn có thể vượt `maxInMemorySize`.
- Một NDJSON stream hàng GB có thể chứa records nhỏ hơn codec limit mà vẫn vượt **total upload quota**.

Do đó với public import API phải có **giới hạn tổng bytes, số records, thời lượng, rate limit, concurrency và quota**, không chỉ quan sát memory per-record.

### 4.2. Thứ tự khi validate và ghi dữ liệu

Nếu các dòng cần xử lý theo thứ tự:

```java
return request.bodyToFlux(ImportLine.class)
        .concatMap(line ->
                validate(line)
                    .then(repository.save(line)))
        .then(ServerResponse.noContent().build());
```

Đây là **fragment**; `validate` được giả định trả `Mono<Void>` và repository.save là reactive. `concatMap` giữ thứ tự các source elements. **Không hứa transactional all-or-nothing**: nếu dòng 100 lỗi sau khi 99 dòng đã lưu, side effects 1–99 có thể đã commit. Nếu muốn toàn bộ batch atomic, cần staging, transaction logic phù hợp, idempotency hoặc job import độc lập.

`flatMap` có thể xử lý concurrent và thứ tự hoàn tất thay đổi; chọn dựa trên semantics nghiệp vụ, không chỉ tốc độ.

## 5. `body(...)` và `BodyExtractors`: phiên bản tổng quát

`bodyToMono` và `bodyToFlux` là shortcuts:

```java
Mono<CreateBook> a = request.bodyToMono(CreateBook.class);

Mono<CreateBook> b = request.body(
        BodyExtractors.toMono(CreateBook.class));

Flux<ImportLine> c = request.bodyToFlux(ImportLine.class);

Flux<ImportLine> d = request.body(
        BodyExtractors.toFlux(ImportLine.class));
```

Đây là fragment, cần:

```java
import org.springframework.web.reactive.function.BodyExtractors;
```

`BodyExtractors` có hỗ trợ các cách đọc **raw DataBuffer, Resource, parts, form/multipart** tùy media type. Dùng API chuyên dụng khi representation phức tạp, không lạm dụng `bodyToFlux(MyPojo.class)` cho file lớn.

### 5.1. Kiểu generic

`bodyToMono(Class<T>)` tiện khi `T` không chứa generic type. Với `List<Book>` hoặc `Map<String,List<Book>>`:

```java
Mono<List<ImportLine>> lines =
        request.bodyToMono(
            new ParameterizedTypeReference<
                List<ImportLine>>() {});
```

Đây là fragment; cần import `org.springframework.core.ParameterizedTypeReference` và `java.util.List`. Overload `ParameterizedTypeReference<T>` giữ được generic element type cho Jackson/codec; `List.class` làm mất generic type trong nhiều trường hợp.

**Không dùng `bodyToMono(List.class)` rồi cast toàn bộ items** sang DTO; dữ liệu thường được decode thành maps không phải record cụ thể nếu thiếu thông tin generic.

## 6. Bean Validation trong functional endpoint không tự chạy

Giả sử DTO:

```java
record Contact(
        @NotBlank @Email String email) {}

record CreateBook(
        @NotBlank @Size(min = 3, max = 80) String title,
        @NotNull @Valid Contact contact) {}
```

Nếu handler chỉ:

```java
return request.bodyToMono(CreateBook.class)
        .flatMap(book -> repository.save(book));
```

**Không tự động validate** chỉ vì có `@NotBlank` ở record. WebFlux.fn không có tham số method được đánh dấu `@Valid @RequestBody` như annotated controller. Cần gọi Spring Validator hoặc Jakarta Bean Validation **sau khi decode, trước save**.

### 6.1. Cách A: Spring `Validator` + `BeanPropertyBindingResult`

Đây là cách tài liệu chính thức Spring WebFlux.fn sử dụng:

```java
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebInputException;

private final Validator springValidator;

private void validate(CreateBook book) {
    Errors errors =
            new BeanPropertyBindingResult(book, "createBook");
    springValidator.validate(book, errors);

    if (errors.hasErrors()) {
        // Thực tế hãy dùng error code và message an toàn.
        throw new ServerWebInputException(
                "Book input validation failed");
    }
}
```

**Đây là class fragment**, cần có field `springValidator` được khởi tạo/injected. Một global `LocalValidatorFactoryBean` có thể tích hợp Jakarta constraints; cũng có thể dùng Spring custom `Validator`.

- `BeanPropertyBindingResult` mang cả field errors và object errors.
- `@Valid` nested cascade được hỗ trợ khi provider và Spring adapter dùng đúng.
- Dùng `errors.getFieldErrors()` để lấy tên field, nhưng **không trả raw rejected value** ra client/log.
- `ServerWebInputException` thường có HTTP 400 semantics trên WebFlux, nhưng body JSON cụ thể phụ thuộc error handler của ứng dụng.

### 6.2. Cách B: Jakarta `Validator` trực tiếp

Trong project có `spring-boot-starter-validation`, inject `jakarta.validation.Validator`:

```java
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

private final Validator beanValidator;

private <T> T requireValid(T input) {
    Set<ConstraintViolation<T>> violations =
            beanValidator.validate(input);

    if (!violations.isEmpty()) {
        throw new InputProblem("INPUT_VALIDATION");
    }

    return input;
}
```

Đây là fragment, cần import `java.util.Set` và custom exception `InputProblem`. Cách này dùng Bean Validation API chuẩn, thích hợp DTO đã đặt `jakarta.validation.constraints.*`. Trong runnable demo sẽ dùng **Jakarta Validator** và tạo error field paths một cách an toàn.

**Điều cốt lõi:** cả hai cách đều phải được **gọi tường minh**:

```java
Mono<CreateBook> validated =
        request.bodyToMono(CreateBook.class)
                .map(this::requireValid);
```

`map` không chạy với `Mono.empty()` hoặc khi decoder đã báo lỗi; vì vậy cần `switchIfEmpty` cho body bắt buộc và đường xử lý decoder exception riêng.

### 6.3. Cascaded validation và null

| Annotation | Ý nghĩa | Ghi chú |
| --- | --- | --- |
| `@NotNull` | Không được null | Không cấm chuỗi whitespace |
| `@NotBlank` | Text có non-whitespace | Phù hợp title/email |
| `@Size(min,max)` | Giới hạn kích thước string/collection | Không tự cấm null |
| `@Email` | Hình thức email | Không chứng minh email thuộc người dùng |
| `@Positive` | Số phải lớn hơn 0 | Không tự cấm null |
| `@Valid` | Cascade vào nested object | Không tự báo lỗi nếu nested object null |
| `@Validated(...)` | Chọn validation group với Spring | Dùng có chủ đích; không tự áp chỉ vì functional body |

Ví dụ `@NotNull @Valid Contact contact` có **hai nhiệm vụ**: bắt buộc có contact và kiểm tra constraints bên trong. Nếu chỉ `@Valid Contact contact`, `contact=null` thường không gây violation.

### 6.4. Dùng validation cho cả `Flux`

```java
Flux<ImportLine> validLines =
        request.bodyToFlux(ImportLine.class)
                .index()
                .map(tuple -> {
                    long line = tuple.getT1() + 1;
                    return checkLine(
                            tuple.getT2(),
                            "lines[" + line + "].");
                });
```

`Flux#index()` trả index **bắt đầu ở 0**, nên cộng 1 nếu muốn báo lỗi theo số dòng người dùng. `checkLine` trong ví dụ đầy đủ trả object sau khi validate; nếu lỗi thì throw exception, gây `onError`, dừng xử lý tiếp. Với dữ liệu NDJSON, dòng trống/delimiter và parser behavior phải được test, không giả định index luôn bằng số dòng vật lý bao gồm dòng trống.

## 7. Phân biệt năm kiểu lỗi đầu vào

Một REST API nghiêm túc không được biến mọi exception thành “invalid body”:

| Tình huống | Nguyên nhân | Phản hồi thường hợp lý |
| --- | --- | --- |
| Body bắt buộc nhưng trống | `Mono.empty()` / `Flux.empty()` | 400, `BODY_REQUIRED` |
| Body JSON sai cú pháp/kiểu | Decoder/Jackson error | 400, `MALFORMED_BODY` |
| JSON hợp lệ nhưng vi phạm `@NotBlank`, `@Positive` | Bean Validation | 400, `INPUT_VALIDATION` |
| `Content-Type` không đúng route/reader | Route predicate hoặc unsupported media type | 415 hay 404/khác theo routing/config; không đoán |
| Business conflict sau validate | Domain service / database unique constraint | 409 hay 422 theo contract |

Một số lỗi khác phải để hệ thống riêng xử lý:

- **Body/record quá lớn:** `DataBufferLimitException` có thể từ codec; API nên có policy 413 nhưng không mặc định hứa mọi phiên bản trả 413.
- **Unacceptable response media type:** 406 khi không có writer phù hợp.
- **Too many requests:** 429 là quota/rate limiting, không phải Bean Validation.
- **Auth missing/forbidden:** 401/403 ở Spring Security layer.
- **DB/network outage:** 5xx hoặc retry policy, **không** trả 400.
- **Client disconnect:** cancellation/transport handling; không giả định luôn gửi được JSON lỗi.

### 7.1. Lỗi decoder và validation xảy ra ở giai đoạn khác nhau

Ví dụ JSON sai:

```json
{"title":
```

Không có `CreateBook` hợp lệ để validate. Error có thể là `DecodingException` hoặc được WebFlux bọc thành `ServerWebInputException`; cần kiểm tra chain của Spring Framework/codec version bạn dùng.

Ví dụ JSON đúng nhưng title rỗng:

```json
{
  "title": " ",
  "contact": {"email":"reader@example.org"}
}
```

Decoder tạo DTO, sau đó **validator do bạn gọi** phát violation. Bắt cả hai trong một `onErrorResume(Throwable.class, ...)` rồi trả 400 sẽ che mất error từ DB, programming bug hoặc OOM.

### 7.2. Phạm vi `onErrorResume`

```java
return request.bodyToMono(CreateBook.class)
        .map(this::requireValid)
        .flatMap(this::save)
        .onErrorResume(InputProblem.class, this::badInput);
```

Dạng này chỉ xử lý exception **InputProblem**. Nếu DB lỗi, lỗi được propagates tới WebFlux global error infrastructure, thay vì bị biến thành invalid request. Đây là nguyên tắc phân loại lỗi theo **exception type và layer**.

Nếu bạn dùng `onErrorResume(DecodingException.class,...)`, chỉ map lỗi decode sang MALFORMED_BODY. Nếu có `ServerWebInputException` từ reader, map input problem theo HTTP status/policy; giữ exception text riêng tư.

## 8. Ranh giới của Content-Type, Accept và route predicates

Với router:

```java
RouterFunctions.route()
        .POST(
            "/api/books",
            RequestPredicates.contentType(
                    MediaType.APPLICATION_JSON),
            handler::create)
        .build();
```

Request predicate chỉ match nếu `Content-Type` phù hợp. Client gửi XML có thể **không match route**, dẫn đến 404/415 tùy các handler mappings và error policies; không tự suy ra Bean Validation sẽ bắt.

- `Content-Type` mô tả **request representation**, ví dụ JSON, NDJSON, form.
- `Accept` mô tả **các representations client nhận được** ở response.
- `contentType(APPLICATION_JSON)` **không** chứng minh body thật sự là JSON hợp lệ.
- `accept(APPLICATION_JSON)` không thay nội dung được ghi thành JSON nếu writer/DTO không phù hợp.
- HTTP headers do client cung cấp, cần kiểm tra cả payload và business rules.
- Nếu cần trả chuẩn 415 thay vì “không có route”, thiết kế fallback/error handling ở application/gateway và viết tests.

**Với WebFlux.fn, route predicates được xét theo thứ tự khai báo.** Một route rộng khai trước có thể shadow route cụ thể hơn. Đặt `/api/books/import` trước route `/api/books/{id}` nếu cả hai dùng cùng HTTP method và có nguy cơ overlap.

## 9. Không đọc body hai lần nếu không chủ đích cache

HTTP request body là **một stream**, không phải Java object cache tùy ý.

Anti-pattern:

```java
// Không làm thế này:
request.bodyToMono(String.class)
        .doOnNext(System.out::println)
        .subscribe();

return request.bodyToMono(CreateBook.class)
        .flatMap(this::save);
```

Bạn vừa tự subscribe (rời lifecycle) rồi thử consume body lần hai. Body có thể đã tiêu thụ; pooled buffers có thể bị release, dẫn tới lỗi.

Để inspect/validate, dùng **một pipeline**:

```java
return request.bodyToMono(CreateBook.class)
        .doOnNext(dto ->
                metrics.markReceived())
        .map(this::requireValid)
        .flatMap(this::save);
```

Không log raw DTO nếu có password, token, email/PII. Với request logging body thật sự, cần buffer decorator được thiết kế đúng, cap bytes, lifecycle/retain/release, redaction và security review; đừng tái đọc request để log.

### 9.1. BodyExtractors và buffer ownership

- Standard `bodyToMono/bodyToFlux` để HTTP codec xử lý `DataBuffer` lifecycle.
- Khi tự đọc `Flux<DataBuffer>`, cần hiểu pooled buffer retention, `DataBufferUtils.release`/`retain`, cancellation và discard.
- Không `collectList()` toàn bộ raw DataBuffers rồi giữ vô hạn.
- `DataBufferUtils.join(...)` aggregate bytes và có overload maxByteCount, nhưng không phù hợp infinite/unbounded body.
- Không đưa raw `DataBuffer` vào downstream cache sau khi framework đã release; nếu phải handoff, có ownership contract rõ.
- Spring WebFlux codecs tôn trọng Reactive Streams demand trong khả năng của implementation, nhưng network/proxy vẫn có queues và buffers riêng.

## 10. Validation phải xảy ra trước side effects

Luồng đúng:

```text
decode body
  |
  v
validate DTO / field paths
  |
  +--- invalid ---> 400 JSON error, KHÔNG lưu
  |
  v
validate business rules / authorization
  |
  v
repository.save / publish event
  |
  v
201 / 200 response
```

Nếu gọi database trước rồi mới `validate`, một request có title rỗng có thể đã tạo record sai. Với stream nhiều records, phải phân biệt:

- **Per-record validation:** mỗi record được kiểm tra trước khi xử lý riêng.
- **Whole-batch validation:** kiểm tra cross-record duplicates, limits và invariant toàn batch. Có thể cần staging/transaction/snapshot.
- **Atomicity:** với stream lớn, không thể hứa rollback mọi side effects chỉ bằng `onErrorResume`. Phải thiết kế DB transaction hoặc job-based processing.
- **Ordering:** `concatMap` giữ thứ tự nguồn khi side effects cần tuần tự; `flatMap` xử lý concurrency có thể reorder.
- **Database uniqueness:** Bean Validation không thay DB unique constraint khi nhiều concurrent requests.
- **Authorization:** các ràng buộc field không thay quyền truy cập theo user/tenant.

### 10.1. Lấy thông tin user đáng tin

Không để input DTO chứa `ownerId`/`role` rồi mặc định tin. Lấy authenticated identity từ security context trong WebFlux và kiểm tra quyền dùng reactive services. Validation kiểm tra định dạng; authorization kiểm tra **ai được làm gì**.

## 11. Lỗi trả về: code và danh sách fields an toàn

Gợi ý error JSON:

```json
{
  "code": "INPUT_VALIDATION",
  "fields": ["title", "contact.email"]
}
```

Với NDJSON dòng thứ hai:

```json
{
  "code": "INPUT_VALIDATION",
  "fields": ["lines[2].quantity"]
}
```

Tên trường và mã lỗi thường đủ để frontend hiển thị hoặc i18n. Không trả:

```json
{
  "field": "password",
  "rejectedValue": "MySecret",
  "stackTrace": "java.lang.IllegalArgumentException..."
}
```

Exception messages/custom constraints có thể vô tình chứa user input. Hạn chế echo; nếu cần human-readable messages, chọn từ bảng thông báo an toàn, không trực tiếp trả `violation.getInvalidValue()`.

### 11.1. ProblemDetail là một lựa chọn

Spring 6.2 hỗ trợ `ProblemDetail`/RFC 9457 cho HTTP errors. Với functional endpoints, bạn có thể xây `ServerResponse` cho `application/problem+json`, hoặc sử dụng global exception infrastructure với error response. Nhưng **đừng trộn nhiều schema lỗi vô tổ chức**; chọn consistent error format theo API version.

## 12. Ví dụ chạy được: Spring Boot 3.5 + Java 17 WebFlux.fn

Demo minh họa đầy đủ cả hai kiểu body:

1. **`POST /api/books`**: nhận một JSON object bằng `bodyToMono(CreateBook.class)`, validate nested contact, lưu in-memory, trả **201 Created** + Location.
2. **`POST /api/books/import`**: nhận nhiều NDJSON records bằng `bodyToFlux(ImportLine.class)`; validate **từng item** cùng vị trí index, sau đó giảm thành thống kê count/quantity, trả 200.
3. **`GET /api/books/{id}`**: đọc resource đã lưu để chứng minh Location.
4. Định dạng lỗi an toàn cho BODY_REQUIRED, INPUT_VALIDATION, MALFORMED_BODY và INVALID_ID.
5. Không dùng `subscribe()`/`block()` trong handler. Không chạy blocking JDBC trên Netty event loop.

Đây là **demo in-memory, không phải ứng dụng production**: khi restart mất dữ liệu, scale nhiều nodes không chia sẻ map. Batch import chỉ tính tổng, không lưu rows; không có database, auth, global total-byte cap hay cross-record reconciliation.

### 12.1. Dependencies Gradle

Dùng Spring Initializr tạo project **Spring Boot 3.5.x, Java 17, Spring Reactive Web, Validation**. Trong `build.gradle` đã có Boot plugin, repositories và Java toolchain, dependencies:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment build.gradle**, không phải file Gradle hoàn chỉnh. Không thêm `spring-boot-starter-web` chỉ vì muốn dùng RouterFunction; Boot 3.x thường chọn Servlet MVC nếu cả MVC và WebFlux starters cùng có mặt.

### 12.2. Toàn bộ Java source

Tạo file **`src/main/java/demo/fnbody/FnBodyApplication.java`**:

```java
package demo.fnbody;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;

import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@SpringBootApplication
public class FnBodyApplication {
    public static void main(String[] args) {
        SpringApplication.run(FnBodyApplication.class, args);
    }
}

record Contact(@NotBlank @Email String email) {}

record CreateBook(
        @NotBlank @Size(min = 3, max = 80) String title,
        @NotNull @Valid Contact contact) {}

record Book(long id, String title, String contactEmail) {}

record ImportLine(
        @NotBlank String sku,
        @Positive @Max(1000) int quantity) {}

record ImportSummary(long rows, long units) {}

record ApiError(String code, List<String> fields) {}

final class InputProblem extends RuntimeException {
    private final String code;
    private final List<String> fields;

    InputProblem(String code, List<String> fields) {
        super(code);
        this.code = code;
        this.fields = List.copyOf(fields);
    }

    String code() { return code; }
    List<String> fields() { return fields; }
}

@Component
class BookHandler {
    private final Validator validator;
    private final AtomicLong ids = new AtomicLong(2000);
    private final ConcurrentMap<Long, Book> books =
            new ConcurrentHashMap<>();

    BookHandler(Validator validator) {
        this.validator = validator;
    }

    private <T> T checked(T value, String prefix) {
        Set<ConstraintViolation<T>> violations =
                validator.validate(value);

        if (!violations.isEmpty()) {
            List<String> fields = violations.stream()
                    .map(v -> prefix + v.getPropertyPath())
                    .distinct()
                    .sorted()
                    .toList();

            throw new InputProblem(
                    "INPUT_VALIDATION", fields);
        }

        return value;
    }

    private Mono<ServerResponse> error(
            String code, List<String> fields) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError(code, fields));
    }

    // Không catch Throwable hay database errors thành 400.
    private Mono<ServerResponse> handleInputErrors(
            Mono<ServerResponse> publisher) {
        return publisher
                .onErrorResume(InputProblem.class,
                    problem -> error(
                        problem.code(), problem.fields()))
                .onErrorResume(DecodingException.class,
                    problem -> error(
                        "MALFORMED_BODY", List.of("body")))
                .onErrorResume(ServerWebInputException.class,
                    problem -> error(
                        "MALFORMED_BODY", List.of("body")));
    }

    Mono<ServerResponse> create(ServerRequest request) {
        Mono<ServerResponse> result =
                request.bodyToMono(CreateBook.class)
                    .switchIfEmpty(Mono.error(
                        new InputProblem(
                            "BODY_REQUIRED", List.of("body"))))
                    .map(value -> checked(value, ""))
                    .flatMap(value -> {
                        long id = ids.incrementAndGet();
                        Book created = new Book(
                            id, value.title().trim(),
                            value.contact().email());
                        books.put(id, created);

                        // Relative Location: demo chạy tại context root.
                        URI uri = URI.create("/api/books/" + id);

                        return ServerResponse.created(uri)
                                .contentType(
                                    MediaType.APPLICATION_JSON)
                                .bodyValue(created);
                    });

        return handleInputErrors(result);
    }

    Mono<ServerResponse> importLines(ServerRequest request) {
        Mono<ImportSummary> count =
                request.bodyToFlux(ImportLine.class)
                    .index()
                    .map(tuple -> {
                        long lineNumber = tuple.getT1() + 1;
                        return checked(
                                tuple.getT2(),
                                "lines[" + lineNumber + "].");
                    })
                    .reduce(
                        new ImportSummary(0L, 0L),
                        (acc, line) ->
                            new ImportSummary(
                                acc.rows() + 1,
                                acc.units() + line.quantity()));

        Mono<ServerResponse> result =
                count.flatMap(summary -> {
                    if (summary.rows() == 0) {
                        return error(
                            "BODY_REQUIRED", List.of("body"));
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(summary);
                });

        return handleInputErrors(result);
    }

    Mono<ServerResponse> get(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(
                    request.pathVariable("id"));
        } catch (NumberFormatException badId) {
            return error(
                "INVALID_ID", List.of("id"));
        }

        return Mono.justOrEmpty(books.get(id))
                .flatMap(book ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book))
                .switchIfEmpty(
                    ServerResponse.notFound().build());
    }
}

@Configuration
class BookRoutes {
    @Bean
    RouterFunction<ServerResponse> bookRouter(
            BookHandler handler) {
        return RouterFunctions.route()
                .POST(
                    "/api/books/import",
                    contentType(MediaType.APPLICATION_NDJSON),
                    handler::importLines)
                .POST(
                    "/api/books",
                    contentType(MediaType.APPLICATION_JSON),
                    handler::create)
                .GET("/api/books/{id}", handler::get)
                .build();
    }
}
```

**Kiểm tra cấu trúc:**

- Một `public class FnBodyApplication`; các records, `BookHandler` và `BookRoutes` package-private cùng package.
- `@Component BookHandler` được Spring Boot component scan, inject `jakarta.validation.Validator` do validation starter cấu hình.
- `@Configuration BookRoutes` cung cấp một `RouterFunction<ServerResponse>` bean WebFlux.fn.
- `BookHandler` nhận `ServerRequest` và trả `Mono<ServerResponse>` đúng WebFlux.fn.
- `checked` dùng typed `ConstraintViolation` nhưng chỉ đưa ra **field paths**, không echo input hay constraint message có thể chứa dữ liệu nhạy cảm.
- `create` chỉ sinh ID, lưu map **sau** khi decode và Bean Validation hoàn tất.
- `importLines` validate từng dòng trước khi cộng aggregate; không materialize cả batch.
- `handleInputErrors` chỉ bắt input/domain validation exception cụ thể và decoder-related exceptions; không bắt mọi bug thành 400.
- `GET /api/books/{id}` trả 404 nếu không tồn tại, INVALID_ID nếu ID không phải số.
- `@Positive @Max(1000)` giới hạn quantity mỗi dòng, nhưng **không** giới hạn tổng số dòng hoặc tổng quantity của toàn stream; production cần quota/overflow handling.

### 12.3. Chạy và kiểm tra bằng curl

```bash
./gradlew bootRun
```

**Tạo book hợp lệ:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Reactive Guide","contact":{"email":"reader@example.org"}}'
```

Nếu đây là request tạo đầu tiên, kết quả dự kiến:

```http
HTTP/1.1 201 Created
Location: /api/books/2001
Content-Type: application/json

{"id":2001,"title":"Reactive Guide","contactEmail":"reader@example.org"}
```

`Location` là relative path, phù hợp demo context root. Với production sau gateway/context prefix, cần generate links phù hợp public base/path và Host/Forwarded trust policy.

**Thiếu body:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary ''
```

Kỳ vọng 400:

```json
{"code":"BODY_REQUIRED","fields":["body"]}
```

**JSON hợp lệ nhưng sai constraints:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":" ","contact":{"email":"not-an-email"}}'
```

Kỳ vọng 400; fields thường là `title` và `contact.email` (do `@Valid` cascade):

```json
{"code":"INPUT_VALIDATION","fields":["contact.email","title"]}
```

Các field names được code sort alphabetic, nên thứ tự ổn định trong demo (nhưng thứ tự violations do provider trả trước sort không bảo đảm).

**Malformed JSON:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":'
```

Kỳ vọng lỗi decoder được mã hóa thành `MALFORMED_BODY` khi đi vào `DecodingException` hoặc `ServerWebInputException`:

```json
{"code":"MALFORMED_BODY","fields":["body"]}
```

Nếu hạ tầng báo lỗi theo class/adapter khác, phải kiểm chứng bằng integration tests và bổ sung handler có chọn lọc; không map `Throwable` sang 400.

**Import NDJSON hợp lệ:**

```bash
printf '%s\n' \
  '{"sku":"B-101","quantity":3}' \
  '{"sku":"B-102","quantity":5}' \
  '{"sku":"B-103","quantity":2}' \
  | curl -i -X POST 'http://localhost:8080/api/books/import' \
      -H 'Content-Type: application/x-ndjson' \
      --data-binary @-
```

Body dự kiến:

```json
{"rows":3,"units":10}
```

**Dòng thứ hai sai:**

```bash
printf '%s\n' \
  '{"sku":"B-101","quantity":3}' \
  '{"sku":"B-102","quantity":0}' \
  | curl -i -X POST 'http://localhost:8080/api/books/import' \
      -H 'Content-Type: application/x-ndjson' \
      --data-binary @-
```

Kỳ vọng 400:

```json
{"code":"INPUT_VALIDATION","fields":["lines[2].quantity"]}
```

Vì import chỉ tính aggregate và không lưu dữ liệu, invalid stream không tạo dữ liệu partial. **Đừng coi ví dụ này là transactional bulk import** khi chuyển sang DB.

## 13. Test WebTestClient qua HTTP trên RANDOM_PORT

Kiểm thử dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)` để chạy WebFlux với HTTP codecs/Netty, thay vì chỉ gọi handler trực tiếp.

Tạo **`src/test/java/demo/fnbody/FnBodyApplicationTests.java`**:

```java
package demo.fnbody;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FnBodyApplicationTests {
    @Autowired
    WebTestClient client;

    @Test
    void createsBookFromOneJsonObject() {
        client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "title":"Reactive Guide",
                      "contact":{"email":"reader@example.org"}
                    }
                    """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.title")
                    .isEqualTo("Reactive Guide")
                .jsonPath("$.contactEmail")
                    .isEqualTo("reader@example.org");
    }

    @Test
    void emptyBodyIsRejectedBeforePersistence() {
        client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("BODY_REQUIRED");
    }

    @Test
    void invalidFieldsProduceValidationError() {
        client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "title":" ",
                      "contact":{"email":"invalid-email"}
                    }
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INPUT_VALIDATION")
                .jsonPath("$.fields[0]")
                    .isEqualTo("contact.email")
                .jsonPath("$.fields[1]")
                    .isEqualTo("title");
    }

    @Test
    void missingNestedContactIsInvalid() {
        client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"title":"Valid title"}
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INPUT_VALIDATION")
                .jsonPath("$.fields[0]")
                    .isEqualTo("contact");
    }

    @Test
    void malformedJsonIsNotBeanValidationError() {
        client.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("MALFORMED_BODY");
    }

    @Test
    void multiLineNdjsonIsReducedWithoutCollectList() {
        client.post()
                .uri("/api/books/import")
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(Flux.just(
                    new ImportLine("B-101", 3),
                    new ImportLine("B-102", 5),
                    new ImportLine("B-103", 2)),
                    ImportLine.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rows").isEqualTo(3)
                .jsonPath("$.units").isEqualTo(10);
    }

    @Test
    void invalidNdjsonItemIdentifiesIndex() {
        client.post()
                .uri("/api/books/import")
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(Flux.just(
                    new ImportLine("B-101", 3),
                    new ImportLine("B-102", 0)),
                    ImportLine.class)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INPUT_VALIDATION")
                .jsonPath("$.fields[0]")
                    .isEqualTo("lines[2].quantity");
    }

    @Test
    void emptyNdjsonIsRejected() {
        client.post()
                .uri("/api/books/import")
                .contentType(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("BODY_REQUIRED");
    }

    @Test
    void malformedIdIsBadInput() {
        client.get()
                .uri("/api/books/not-a-number")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                    .isEqualTo("INVALID_ID");
    }

    @Test
    void missingBookReturns404() {
        client.get()
                .uri("/api/books/999999999")
                .exchange()
                .expectStatus().isNotFound();
    }
}
```

Chạy:

```bash
./gradlew test
```

**Mười tests được thiết kế để pass** với Boot 3.5, Reactor Netty và Java 17 code ở trên. Đây là **kỳ vọng qua review source và API**, **không phải bằng chứng Gradle đã chạy**. Trong hệ thống thực, hãy thực thi tests và điều chỉnh chỗ behavior thực của codec/config phiên bản không đúng giả định.

Điểm đáng chú ý:

- Test `bodyToMono` qua JSON hợp lệ, trống, invalid/nested constraints và malformed syntax.
- Test `bodyToFlux` bằng **NDJSON publisher** từ WebTestClient và xác minh thống kê/invalid index.
- Test body rỗng không bị tự chấp nhận là batch 0 thành công.
- Test path variable do functional handler tự parse, phân biệt bad ID và missing resource.
- Các tests tạo book mới dùng id sequence nhưng không assert ID cố định để tránh phụ thuộc execution order.
- `@SpringBootTest(RANDOM_PORT)` bao gồm actual HTTP encoding/decoding, còn unit test Java Validator nên có riêng nếu ràng buộc domain phức tạp.

### 13.1. Test “không lưu input sai” nghiêm túc

Demo không trả số lượng stored books. Để xác minh invalid payload không ghi DB ở production, dùng reactive repository spy/mocks hoặc database integration tests:

1. Gửi invalid DTO.
2. Assert status 400.
3. Assert repository `save` **không được gọi**.
4. Nếu có transaction, assert rollback/DB rows unchanged.
5. Test concurrency/race, auth và idempotency.

Không chỉ assert code 400 rồi suy ra không side effect; test đúng layer repository.

## 14. Body rỗng, malformed, validation và lỗi domain: thiết kế exception flow

Thứ tự khuyến nghị:

```text
bodyToMono / bodyToFlux
  |
  +--- Flux/Mono empty --> explicit BODY_REQUIRED
  |
  +--- decode error --> MALFORMED_BODY
  |
  v
validate each DTO
  |
  +--- violations --> INPUT_VALIDATION (400)
  |
  v
business & security checks
  |
  +--- permission --> 403
  +--- conflict --> 409
  |
  v
repository
  |
  +--- infrastructure failure --> 5xx
  |
  v
ServerResponse success
```

**Không chuyển `DataBufferLimitException` thành MALFORMED_BODY mặc định** nếu error bị wrap trong ServerWebInputException; một số input decoding limit failures cần 413 theo product contract. Demo chọn hạn chế error handler để dễ học; production nên phân loại cause chain, có giới hạn payload tại ingress/codec, và test 413.

### 14.1. Vì sao tránh response body chứa raw exception?

`DecodingException#getMessage()` có thể chứa class names, parsing positions hoặc fragments từ payload. `ConstraintViolation#getInvalidValue()` có thể là secret. Thay bằng safe **field names/error codes**, và log internal error information có kiểm soát, redaction ở app/gateway/APM.

### 14.2. Khi response đã committed

Trong `bodyToFlux` request input, bạn có thể stream phản hồi song song hoặc theo từng item. Nếu đã gửi 200 + vài bytes rồi mới gặp dòng lỗi, HTTP status **không thể chắc chắn đổi sang 400**. Chọn giao thức explicit stream-error frames hoặc đợi hoàn tất validation trước khi trả success nếu batch nhỏ và business đòi atomic reply.

Trong demo `reduce` chờ hết input trước khi sinh response, nên lỗi invalid line thường có thể trả 400 trước khi response commit. Đây là **đánh đổi latency/aggregation**: memory nhỏ với reduce, nhưng response chỉ xuất sau khi stream kết thúc.

## 15. NDJSON, JSON array và những lựa chọn khác

| Input representation | Cách đọc thường phù hợp | Trade-off |
| --- | --- | --- |
| `application/json`, một object | `bodyToMono(MyDto.class)` | Dễ validate một command; decoder có thể aggregate object |
| `application/json`, một array | `bodyToMono(ParameterizedTypeReference<List<Dto>>)` | Giữ list trong RAM, dễ cross-item validation |
| `application/x-ndjson`, nhiều dòng | `bodyToFlux(Dto.class)` | Per-record processing, framing rõ |
| `text/plain`, một chuỗi | `bodyToMono(String.class)` | Kiểm tra charset và max bytes |
| `application/octet-stream`, file lớn | `body(BodyExtractors.toDataBuffers())` / `FilePart` | Raw bytes ownership/quota/storage phức tạp |
| `application/x-www-form-urlencoded` | `ServerRequest.formData()` | Bind form map, validation tự gọi |
| `multipart/form-data` | `ServerRequest.multipartData()` | Files/parts + disk/RAM/part limits riêng |

`bodyToFlux<T>` không thích hợp cho mọi file upload; việc decode bytes thành DTO cần representation thích hợp. `multipartData()` trả dữ liệu parts đã được đọc theo contract parser, có thể spool file ra disk; nếu file cực lớn hoặc cần streaming parts, xem `PartEvent` APIs của WebFlux 6.2 và quản lý buffers.

### 15.1. Form data

```java
Mono<MultiValueMap<String, String>> form =
        request.formData();
```

Đây là fragment; mỗi value do client cung cấp phải được parse/validate, ví dụ `Integer.parseInt` có thể ném `NumberFormatException`. Không nghĩ form map đã tự chạy `@NotBlank` trong functional handler.

### 15.2. Multipart

```java
Mono<MultiValueMap<String, Part>> parts =
        request.multipartData();
```

Đây là fragment; cần import `org.springframework.http.codec.multipart.Part`. Với file, `FilePart.filename()` không đáng tin làm filesystem path. Khi transfer file, dùng path **do server sinh**, kiểm soát authorization, size, storage, MIME validation và lifecycle; Bean Validation trên metadata không kiểm tra virus hay file content.

## 16. Giới hạn bộ nhớ và byte-size quan trọng hơn number of DTOs

Các giới hạn độc lập:

| Giới hạn | Lớp tác động | Nó không bảo đảm |
| --- | --- | --- |
| Codec `maxInMemorySize` | Bytes của một đơn vị decode/aggregate | Không phải tổng request quota |
| Body max bytes | Gateway/server/app | Không phải business field validation |
| NDJSON max record length | Decoder/endpoint contract | Không giới hạn tổng batch |
| `@Size(max=80)` | DTO field length (Java semantic) | Không giới hạn toàn bộ JSON bytes |
| `take(maxRecords)` | Số phần tử reactive được downstream nhận | Có thể cancel sớm; không luôn chặn transport bytes đã gửi |
| Database unique constraint | Trùng khóa persisted | Không thay client validation |
| Rate/concurrency limit | Request volume và resource usage | Không thay codec limit |
| Timeout | Thời gian request/stream | Không giới hạn số bytes |

Với **Spring Boot 3.5**, property cấu hình HTTP codecs:

```properties
spring.http.codecs.max-in-memory-size=256KB
```

Giá trị ở trên là một **giá trị cấu hình minh họa**: namespace `spring.http.codecs` thuộc Boot 3.5; kiểm tra bản dùng nếu bạn chuyển Boot major. Nhiều codecs có default khoảng 256 KiB cho một đơn vị tích lũy; property này không phải giới hạn tổng uploads. Khi dùng WebClient tự khởi tạo, client codec limits có thể khác server.

Đừng copy `spring.codec.max-in-memory-size` từ tutorial Boot cũ rồi tin nó vẫn có hiệu lực ở Boot 3.5.

### 16.1. Body đến chậm và cancellation

Client có thể gửi dữ liệu rất chậm (slow upload), hoặc disconnect giữa NDJSON stream. Reactor có `timeout`/`doFinally`/`doOnCancel` nhưng network request cancellation propagation không luôn tức thời và một upstream source có thể vẫn hoạt động. Trong production:

- Giới hạn upload duration/idle timeout ở gateway/netty.
- Có per-tenant request concurrency và rate quota.
- Nếu tạo external subscriptions/storage streams, cleanup ở `doFinally`/`usingWhen`.
- Không tạo unbounded replay/logging queue cho raw input.
- Hạn chế recursive/very deeply nested JSON, numeric overflow, CPU-heavy regex.
- Giới hạn trực tiếp **số dòng** và **số bytes** với contract rõ, không chỉ count đã decode.

## 17. Khi nào nên dùng annotated controller thay WebFlux.fn?

| Tình huống | Functional WebFlux | Annotated WebFlux |
| --- | --- | --- |
| Tổ chức route theo hàm/predicates | `RouterFunctions.route()` | `@RequestMapping` |
| Đọc một body | `request.bodyToMono(Dto.class)` | `@RequestBody Mono<Dto>` |
| Đọc stream body | `request.bodyToFlux(Dto.class)` | `@RequestBody Flux<Dto>` |
| Validate DTO từ JSON | Gọi Validator trong handler | `@Valid/@Validated` được framework hỗ trợ |
| Method constraints direct `@Min` | Parse/validate tường minh hoặc domain/service | Built-in handler method validation |
| Body field errors | Custom `InputProblem` + error response | `WebExchangeBindException` thường gặp |
| Middleware cục bộ | `HandlerFilterFunction` | `WebFilter`, advice, interceptors tùy stack |
| Error schema | `ServerResponse` hoặc WebExceptionHandler | `@RestControllerAdvice`/ErrorResponse |
| JSON codecs | Chung `HttpMessageReader/Writer` của WebFlux | Chung codecs |
| Backpressure | `Mono/Flux` | `Mono/Flux` |

**Quan trọng:** khi dùng `bodyToMono` trong functional handler, không tự kỳ vọng framework phát `WebExchangeBindException` chỉ vì DTO có `@NotBlank`. Nó cần validator được gọi tường minh. Ngược lại, trong annotated controller với `@Valid`, WebFlux hạ tầng hỗ trợ validation và exception path theo controller signatures.

Spring MVC.fn là một sản phẩm khác (`org.springframework.web.servlet.function.ServerRequest`), nơi `body(...)` thường trả synchronous object; không copy cách đó vào reactive handler.

## 18. Custom validation và nghiệp vụ bất đồng bộ

### 18.1. Cross-field constraints

Ví dụ DTO có `startDate`, `endDate`; ràng buộc start không sau end cần class-level Bean Validation constraint hoặc code domain:

```java
Mono<EventCommand> checkRange(EventCommand input) {
    if (input.start().isAfter(input.end())) {
        return Mono.error(
                new InputProblem(
                    "INVALID_TIME_RANGE",
                    List.of("start", "end")));
    }
    return Mono.just(input);
}
```

Đây là fragment; DTO và exception phải tồn tại. Không bọc tất cả lỗi thành `IllegalArgumentException` rồi generic 400 nếu có thể dùng typed business exceptions.

### 18.2. Kiểm tra uniqueness qua reactive repository

```java
Mono<Book> checkThenSave(CreateBook input) {
    return repository.existsByTitle(input.title())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(
                        new DuplicateBookException());
                }
                return repository.save(toEntity(input));
            });
}
```

Đây là fragment. **Có race** giữa `exists` và `save`, nên nếu business không cho trùng, phải dùng DB unique index/constraint và map conflict 409. Không đặt blocking database calls trong `ConstraintValidator` chạy trên Netty event loop.

### 18.3. Bean Validation groups

Có thể dùng `validator.validate(dto, CreateGroup.class)` để chọn group khi tự gọi Jakarta Validator. Nếu muốn create/update rules khác nhau:

```java
Set<ConstraintViolation<CreateBook>> errors =
        validator.validate(input, OnCreate.class);
```

Fragment giả định `OnCreate` marker interface tồn tại và constraints đã gắn group phù hợp. Khi chọn group riêng, các constraints thuộc `Default` không tự được kích hoạt nếu không chọn đúng; test `null`, empty, whitespace và nested cascade.

## 19. Test thêm: không có body, ID, serialization và memory

Một bộ test đủ tốt ngoài mười bài phía trên nên có:

1. Body JSON đúng, `Content-Type` sai: xác minh status thực của router/predicate policy.
2. Missing `Content-Type`: xác minh route behavior; không suy ra lỗi validation.
3. Generic JSON array: kiểm tra `ParameterizedTypeReference<List<...>>` decode đúng element type.
4. Một NDJSON record vượt `maxInMemorySize`: xác nhận codec limit và error policy riêng.
5. Nhiều records nhỏ tổng request lớn: xác nhận upload quota tầng proxy hoặc application.
6. Stream có lỗi tại dòng thứ N: xác nhận các side effects trước đó/transaction semantics.
7. Stream client cancel: xác minh publisher/external resource cleanup.
8. Nested Bean Validation errors với thứ tự field sort ổn định.
9. Producer không tự subscribe/block (có thể static analysis/review).
10. Test trên staging proxy với request chunked/HTTP2, slow upload và gateway timeouts.

### 19.1. Why WebTestClient?

`WebTestClient` hỗ trợ WebFlux HTTP tests. Có thể bind trực tiếp vào router functions hoặc chạy server thật. Demo dùng **RANDOM_PORT** để kiểm tra codec/network request/response, không chỉ logic handler.

Khi test external network, thời gian, connection pool và backpressure có nhiều yếu tố khiến assertions quá sát wall clock dễ flaky. Để test raw reactive demand, dùng `StepVerifier` trong unit tests; để kiểm tra HTTP body framing, dùng WebTestClient hoặc client streaming phù hợp.

## 20. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| `bodyToMono` rồi `block()` | Block event loop | Compose `flatMap/map` và return Mono |
| Tự `subscribe()` trong handler | Mất HTTP error/cancellation lifecycle | Để WebFlux subscribe |
| DTO có `@NotBlank` nên “tự valid” trong fn | Input sai lọt service | Inject Validator và gọi tường minh |
| Chỉ validate record đầu | Các record sau không an toàn | Validate từng phần tử Flux |
| `collectList()` với infinite NDJSON | Tăng heap/vô hạn | Stream/reduce bounded |
| Không xử lý `Mono.empty` | Request thiếu body bị chấp nhận/response treo tùy code | `switchIfEmpty` hoặc policy rõ |
| Catch mọi Exception thành 400 | DB failure/programming bug bị che | Typed errors theo layer |
| Log `violation.getInvalidValue()` | Lộ credentials/PII | Field paths + safe error code |
| JSON syntax lỗi mà trả INPUT_VALIDATION | Sai phân loại | Decoder errors riêng |
| Content-Type sai coi như @Valid fail | Thực ra route/predicate không match | Test HTTP routing/status |
| `bodyToFlux` mà đọc file binary bằng DTO | Codec không phù hợp | FilePart/DataBuffer extractor |
| Đọc body hai lần để log | Stream một lần, buffers bị tiêu thụ | Một pipeline; decorator bounded nếu cần |
| `flatMap(save)` cho nghiệp vụ tuần tự | Order thay đổi, concurrency/race | `concatMap` hoặc transaction |
| Stream lỗi dòng 100 nhưng 99 dòng đã commit | Partial batch | Staging/transaction/job design |
| Chỉ giới hạn record size | Tổng upload vẫn có thể quá lớn | Gateway/request body quota |
| `take(1000)` coi là đã “reject” upload quá hạn | Nó có thể cancel sau 1000 mà không báo vượt | Explicit reject/count policy |
| @Valid nested mà không có @NotNull | Null nested object có thể qua | `@NotNull @Valid` |
| `@Email` coi là xác minh sở hữu địa chỉ | Identity spoofing | Verify email/authorization riêng |
| HTTP 200 đã ghi rồi mới gặp lỗi | Không đổi status sang 400 được | Streaming error protocol/retry |
| Thử case input trực tiếp mà không HTTP test | Bỏ sót codec/media type | WebTestClient RANDOM_PORT |

## 21. Checklist trước khi dùng trong sản phẩm

- [ ] Chọn `bodyToMono` cho single-value representation và `bodyToFlux` cho multi-record streaming phù hợp.
- [ ] `Content-Type`, Accept và body framing được document/test.
- [ ] Không nhầm WebFlux.fn với Spring MVC.fn.
- [ ] Có validation provider + chủ động gọi Bean Validator trong handler.
- [ ] Nested DTO fields bắt buộc có `@NotNull` bên cạnh `@Valid` khi cần.
- [ ] Xử lý Mono.empty/Flux.empty rõ ràng.
- [ ] Bắt decoder error và Bean Validation error riêng, không catch-all 400.
- [ ] Side effects chỉ chạy sau khi validate item/command tương ứng.
- [ ] Không tự subscribe/block trong handler.
- [ ] Không đọc request body hai lần nếu chưa có caching decorator an toàn.
- [ ] Import stream có per-record và batch-level limits/quota, transaction semantics.
- [ ] `flatMap` concurrency, `concatMap` order được chọn có chủ đích.
- [ ] Error response không lộ rejected values/secret/parser stack trace.
- [ ] Authn/authz, tenant boundary và mass assignment được xử lý độc lập.
- [ ] Codec max memory, total bytes, maximum records, timeout và per-user rate limits rõ ràng.
- [ ] Multipart file xử lý qua Part/FilePart/PartEvent và private storage policy.
- [ ] Trước khi lưu, số lượng/giá trị được kiểm tra overflow nếu accumulator có thể rất lớn.
- [ ] WebTestClient tests bao phủ success, invalid, empty, malformed, nested, stream.
- [ ] Có monitoring cancel/error/latency/queue bytes, không log raw DTO có PII.
- [ ] Migration Spring Boot/Framework version được đối chiếu Javadoc và properties tương ứng.

## 22. Bài tập tự thực hành

1. So sánh `bodyToMono(CreateBook.class)` và `bodyToFlux(ImportLine.class)`: publishers khác nhau thế nào?
2. Nếu body JSON là `null` hoặc request không có body, `Mono.empty` ảnh hưởng `map(this::validate)` ra sao?
3. Vì sao Bean Validation annotations trên DTO không đủ cho WebFlux.fn khi thiếu lời gọi Validator?
4. Hãy thay `jakarta.validation.Validator` bằng Spring `Validator` + `BeanPropertyBindingResult`.
5. Tạo nested DTO có `@NotNull @Valid`; xóa `@NotNull` rồi test payload `contact=null`.
6. Thêm `@Size(max=20)` vào SKU và kiểm tra dòng NDJSON nào sai.
7. Đổi `.index().map(...)` thành `.map(...)`; nếu không giữ index thì lỗi UI sẽ định vị dòng như thế nào?
8. Chuyển import thành `concatMap(repository::save)` và mô phỏng fail tại record thứ ba. Bao nhiêu dòng có thể đã commit?
9. Nếu import phải atomic, thiết kế staging/transaction/job thay cho save từng dòng.
10. Vì sao `onErrorResume(Throwable.class,...)` là lỗi khi DB outage?
11. So sánh body malformed JSON với valid JSON có title blank; trả codes nào và lỗi được phát ở đâu?
12. Khi request có Content-Type `text/plain` nhưng route yêu cầu JSON, status nào có thể xuất hiện và cần test gì?
13. Body là `List<CreateBook>`: viết overload dùng `ParameterizedTypeReference` để giữ generic type.
14. Vì sao `request.bodyToFlux(...)` rồi `collectList()` phá lợi ích streaming?
15. Thử một NDJSON line 1 MiB; giới hạn codec nào liên quan? Nếu 10.000 lines nhỏ, tổng body quota ở đâu?
16. Vì sao `@Size(max=80)` không tự chặn 100 MB JSON?
17. Một client ngắt sau khi gửi hai dòng. Bạn cleanup repository stream/subscription bằng API Reactor nào?
18. Dùng `doOnCancel` và `doFinally`, so sánh complete/error/cancel khi client đóng.
19. Thêm query parameter page ở functional request với `request.queryParam("page")`; parse/validate thế nào?
20. Nếu một sản phẩm có upload binary/multipart, tại sao không dùng `bodyToFlux(MyDto.class)` thay `FilePart`?
21. Thiết kế response ProblemDetail cho `INPUT_VALIDATION` không lộ password/token.
22. Nếu một WebFlux app scale 3 nodes với in-memory map, GET Location có thể lỗi vì sao?
23. Khi `Mono<ServerResponse>` đã tạo nhưng chưa subscribe, repository side effect có bắt buộc đã chạy không? Phân biệt những side effects nằm trước và trong operators.
24. Viết WebTestClient test empty body, malformed JSON, nested validation và invalid NDJSON line.
25. So sánh `@Valid @RequestBody Mono<DTO>` của annotated WebFlux controller với manual validation trong WebFlux.fn.
26. Trình bày kế hoạch chuyển demo thành production: auth/tenant, database uniqueness/atomic import, total quotas, codec limits, error codes và observability.

## 23. Tài liệu chính thức

- [Spring Framework 6.2 — Functional Endpoints WebFlux.fn](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): ServerRequest, bodyToMono/bodyToFlux, BodyExtractors, functional Bean Validation, routing và filters.
- [Spring Framework 6.2 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/server/ServerRequest.html): overloads `bodyToMono(Class)`, `bodyToMono(ParameterizedTypeReference)`, `bodyToFlux`, formData, multipartData.
- [Spring Framework 6.2 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html): Mono response, created, ok, bodyValue và error responses.
- [Spring Framework 6.2 — BodyExtractors Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/BodyExtractors.html): `toMono`, `toFlux`, raw DataBuffer, form/multipart.
- [Spring Framework 6.2 — WebFlux HTTP Codecs](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html): Reader/Decoder, byte buffers, aggregation limit, backpressure.
- [Spring Framework 6.2 — Annotated Controller Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-validation.html): khác biệt automatic validation khi dùng `@Valid` trên annotated method arguments.
- [Spring Framework 6.2 — WebFlux Multipart](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/multipart-forms.html): FilePart, PartEvent và upload safety.
- [Spring Boot 3.5 — Servlet/Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): reactive web stack và WebFlux setup.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html): `spring.http.codecs.max-in-memory-size`.
- [Spring Boot 3.5 — Validation](https://docs.spring.io/spring-boot/3.5/reference/io/validation.html): Jakarta Validation provider/autoconfiguration.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): WebFlux HTTP integration testing.
- [Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/): constraints, nested cascade and groups.
- [Reactive Streams Specification](https://www.reactive-streams.org/): demand, cancellation và publisher/subscriber contracts.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-006">Filter cho router, xử lý lỗi và tạo phản hồi thống nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Filter cho router, xử lý lỗi và tạo phản hồi thống nhất trong Functional WebFlux

> **Phạm vi:** Spring Framework 6.2.x, Spring Boot 3.5.x, Java 17+ và **WebFlux.fn** (package `org.springframework.web.reactive.function.server`). Bài học tập trung vào `RouterFunctions.Builder.before`, `after`, `filter`, `onError`, chuẩn hóa status/headers/JSON error bằng response factory và ranh giới giữa filter của một router với `WebFilter`/`ErrorWebExceptionHandler` toàn hệ thống. Demo hoàn chỉnh dùng `Mono<ServerResponse>`, Spring Reactive Web, cURL và WebTestClient. Không nhầm với **WebMvc.fn**: handler MVC trả `ServerResponse` đồng bộ và được chạy trên Servlet stack.

## 1. Vì sao router cần filter và error policy?

Khi API có nhiều handlers, những nhu cầu như request ID, log status, chặn request theo policy, phân loại input lỗi và trả error body thường bị viết lại ở từng method. Dùng filter và response factory giúp tránh lặp logic và tạo một HTTP contract dễ dùng cho mobile/web clients.

```text
HTTP request
     |
WebFilter / Spring Security            [toàn ứng dụng]
     |
DispatcherHandler
     |
RouterFunctionMapping
     |
RouterFunction + RequestPredicate     [chỉ khi route match]
     |
  before / filter
     |
  next.handle(ServerRequest)
     |
  Mono<ServerResponse>
     |
  onError -> Mono<ServerResponse>      [nếu lỗi trong scope]
     |
  after (quan sát response object)
     |
ServerResponseResultHandler + codecs
     |
HTTP response bytes
```

Sơ đồ là **mô hình trách nhiệm**, không phải cam kết mọi filter được gọi theo đúng thứ tự vẽ. Spring ghép các filter theo thứ tự đăng ký/builder scope; khi một thứ tự là requirement nghiệp vụ, hãy thể hiện nó rõ và kiểm thử. Hơn nữa, một `ServerResponse` đã dựng không đồng nghĩa body đã được chuyển xong tới client.

### 1.1. Kết quả mong muốn

- Thành công: giữ status đúng (200, 201, 204), response DTO/JSON phù hợp và `X-Request-Id`.
- Thất bại nghiệp vụ: HTTP 400/404/409 hoặc 5xx thực tế, body `application/problem+json` ổn định.
- Có mã lỗi máy đọc được (`code`) để frontend phân nhánh mà không parse tiếng Anh của `detail`.
- Không lộ raw exception, stack trace, mật khẩu, SQL connection string và tenant data trong response.
- Những lỗi từ đường dẫn không match, decoder, security hoặc quá trình stream phải có chiến lược **global** nếu muốn thống nhất trên toàn hệ thống.

**Chuẩn hóa response không có nghĩa luôn trả HTTP 200 với `{"success":false}`.** HTTP status là phần của hợp đồng và monitoring.

## 2. Nền tảng WebFlux.fn

Functional WebFlux mô tả routing và behavior bằng interfaces:

| Thành phần | Ý nghĩa | API tiêu biểu |
| --- | --- | --- |
| `RouterFunction<ServerResponse>` | Chọn handler khi request match | `RouterFunctions.route()` |
| `HandlerFunction<ServerResponse>` | Xử lý request, trả response reactive | `Mono<ServerResponse> handle(ServerRequest)` |
| `ServerRequest` | URI/method/headers/query/body/attributes | `pathVariable`, `queryParam`, `bodyToMono` |
| `ServerResponse` | Status, headers và response body | `status`, `ok`, `created`, `bodyValue`, `body` |
| `RequestPredicate` | Điều kiện route | `accept`, `contentType`, `method`, `path` |
| `HandlerFilterFunction` | Bao quanh handler chain | `filter((request, next) -> ...)` |

Ví dụ một router bean:

```java
@Bean
RouterFunction<ServerResponse> routes(BookHandler handler) {
    return RouterFunctions.route()
            .GET("/api/books", handler::list)
            .GET("/api/books/{id}", handler::get)
            .POST("/api/books", handler::create)
            .build();
}
```

Đây là snippet trong configuration class, cần `BookHandler` và các imports. Method trong handler có chữ ký:

```java
Mono<ServerResponse> get(ServerRequest request) {
    // Kết hợp dữ liệu và tạo ServerResponse bất đồng bộ.
}
```

Trong WebFlux.fn, **RouterFunctions được đánh giá theo thứ tự**, vì vậy route tĩnh như `/api/books/stats` nên đặt trước `/api/books/{id}`. Đừng dựa vào quy tắc comparator của annotated `@GetMapping` để giả định Spring tự đảo route.

## 3. Scope: Router filter và WebFilter khác nhau

| Công cụ | Hoạt động ở đâu? | Nên dùng |
| --- | --- | --- |
| `RouterFunctions.Builder.before` | Handler đã match thuộc builder | Bổ sung request metadata, request attributes |
| `RouterFunctions.Builder.after` | Response object từ routes trong builder | Logging/tracing response metadata |
| `RouterFunctions.Builder.filter` | Functional handler chain thuộc builder | Kiểm tra precondition, short-circuit, wrapper |
| `RouterFunctions.Builder.onError` | Lỗi phù hợp phát sinh trong handler/filter chain | Map domain exception sang HTTP error |
| `RouterFunction.filter(...)` | Một router function đã được tạo | Áp dụng reusable handler filter |
| `WebFilter` | Reactive HTTP handler chain toàn ứng dụng | Auth, request ID toàn cục, CORS, observation |
| `ErrorWebExceptionHandler` | Lỗi từ WebHandler chain chưa được giải quyết | Fallback, codec/routing errors |
| `@RestControllerAdvice` | Chủ yếu annotation-based controller methods | Annotation exception handling |

**Ranh giới cần nhớ:** filter của router thường chỉ chạy nếu router **đã tìm thấy handler**. Request `GET /path-not-exist` có thể bypass cả `before`, `filter` và `onError` của router đó. Do đó không nên ghi README hoặc API spec rằng “mọi lỗi 404 đều có Problem Details giống router-local 404” nếu chưa có global error handling.

### 3.1. Nested filters chỉ áp dụng đúng nhóm

```java
RouterFunction<ServerResponse> router = RouterFunctions.route()
        .path("/api", api -> api
                .GET("/public", handler::publicData)
                .path("/private", secured -> secured
                        .GET("/account", handler::account)
                        .filter(privateFilter)))
        .filter(commonFilter)
        .build();
```

Đây là snippet, cần handlers và filters được định nghĩa. Filter bên trong nested private builder chỉ áp dụng private routes, còn filter bên ngoài có thể bao toàn bộ các routes của outer builder. Khi thêm route mới, phải review xem nó có nằm trong scope filter dự định hay không. Authentication/authorization production nên được đảm bảo bởi Spring Security reactive filter chain và object-level checks, không chỉ bởi một router-local lambda dễ bị bỏ sót.

## 4. before: biến đổi ServerRequest

`before` nhận function `ServerRequest -> ServerRequest`:

```java
.before(request -> ServerRequest.from(request)
        .header("X-Internal-Mode", "demo")
        .build())
```

Đây là snippet biến đổi request abstraction cho các handlers được match. Đừng tự sửa `Authorization`, `Host` hay `X-Forwarded-*` một cách tùy tiện rồi coi thông tin trở thành đáng tin. Request header vẫn có thể do client gửi.

### 4.1. Tạo request ID

`ServerRequest.attributes()` trả một mutable map của request attributes. Có thể dùng:

```java
.before(request -> {
    request.attributes().computeIfAbsent(
            "demoRequestId",
            ignored -> UUID.randomUUID().toString());
    return request;
})
```

Snippet này cần import `java.util.UUID`. Request ID được đặt vào map, nhưng **chưa tự động xuất hiện ở response header**. Response factory cần đọc và viết `X-Request-Id`. Khi có nhiều filters với thứ tự phức tạp, helper `computeIfAbsent` giúp cùng request nhận một ID nhất quán nếu trước đó chưa có.

### 4.2. Không block trong before

Đây là một anti-pattern:

```java
// Sai: chặn reactive event loop, consume body ngoài vòng đời chính.
.before(req -> {
    CreateBook body = req.bodyToMono(CreateBook.class).block();
    return req;
})
```

`before` nên làm thao tác metadata rẻ. Nếu cần decode JSON và validate, thực hiện trong handler reactive pipeline hoặc một filter được thiết kế để consume/decorate body **đúng một lần**. Không gọi `bodyToMono()` trong filter rồi lại đọc body lần hai tại handler mà không có cơ chế replay được thiết kế.

## 5. filter: bao quanh và có thể short-circuit

`filter` nhận một `HandlerFilterFunction`:

```java
.filter((request, next) -> {
    if (shouldReject(request)) {
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .build();
    }
    return next.handle(request);
})
```

Đây là snippet: `shouldReject` phải được định nghĩa. Nếu không gọi `next.handle(request)`, business handler không chạy. `next.handle(...)` trả `Mono<ServerResponse>`; filter có thể compose bằng `map`/`flatMap`/`doFinally` mà không `block()`.

### 5.1. Một ví dụ short-circuit rõ ràng

```java
.filter((request, next) -> {
    if ("yes".equalsIgnoreCase(
            request.headers().firstHeader("X-Demo-Block"))) {
        return ApiHttp.problem(request,
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEMO_BLOCKED",
                "Demo request was blocked");
    }
    return next.handle(request);
})
```

`X-Demo-Block` là header **được client tự kiểm soát** và chỉ để minh họa. Đừng dùng header này làm bằng chứng người dùng có quyền hoặc làm công tắc bảo trì production. Với maintenance thực, lấy trạng thái từ cấu hình tin cậy server-side.

### 5.2. Observation không đồng nghĩa đã gửi xong body

```java
.filter((request, next) -> {
    long begin = System.nanoTime();
    return next.handle(request)
            .doOnNext(response -> recordStatus(
                    response.statusCode().value()))
            .doFinally(signal -> recordTime(
                    System.nanoTime() - begin, signal));
})
```

Đây là snippet, cần hàm `recordStatus` và `recordTime`. Nó theo dõi lifecycle của **Mono dựng ServerResponse**, không chắc đo toàn bộ HTTP body transmission. Một SSE stream có thể tiếp tục phát hàng phút sau khi đã tạo response object. Metrics end-to-end nên tích hợp đúng tầng HTTP/server Observation.

### 5.3. Khi nào không dùng router filter?

Không thay Spring Security `SecurityWebFilterChain` bằng logic kiểm tra `X-Role` do client gửi. Các nhiệm vụ auth, session, CSRF và CORS nên ở global reactive web/security layer. Router filter phù hợp precondition route-specific, policy rõ phạm vi, logic logging và response preparation.

## 6. after: quan sát ServerResponse

`after` nhận `(ServerRequest, ServerResponse) -> ServerResponse`.

```java
.after((request, response) -> {
    System.out.printf("path=%s status=%d%n",
            request.path(),
            response.statusCode().value());
    return response;
})
```

Trong production dùng SLF4J/Micrometer thay stdout; log có redaction, không ghi token/cookie/body nhạy cảm. Không hiểu thời điểm `after` chạy như “client đã nhận tất cả bytes”.

**Bẫy mất body:** nếu muốn thêm header, đừng vội làm `ServerResponse.from(response).header(...).build()`. Việc tạo response mới từ status/headers và gọi build mà không copy body publisher có thể làm mất payload. Trong demo sử dụng **response factories** đặt status/headers ngay khi xây từng response, thay vì sửa đối tượng response đã có.

### 6.1. Exception trong after

Nếu `after` ném exception, kết quả phụ thuộc filter composition, lỗi đã commit hay chưa. Logging/observation helper phải không phá response; tránh blocking I/O hoặc thao tác dễ ném error trong after. Test callback/filter order nếu môi trường thêm nhiều nested builders.

## 7. onError: phân loại ngoại lệ trong router scope

Một router-level error mapper:

```java
.onError(BadInputException.class,
        (ex, request) -> ApiHttp.problem(
                request, HttpStatus.BAD_REQUEST,
                "INVALID_INPUT", ex.getMessage()))
```

Chữ ký response provider trong **WebFlux.fn** là một hàm trả `Mono<ServerResponse>`. Với **WebMvc.fn** là `ServerResponse` đồng bộ. Đây là lý do rất phổ biến khiến code copy giữa hai module không compile.

### 7.1. Ba nhóm lỗi cần tách

```text
BadInputException          -> HTTP 400 + INVALID_INPUT
ItemMissingException       -> HTTP 404 + ITEM_NOT_FOUND
IllegalStateException      -> HTTP 500 + INTERNAL_ERROR (demo)
```

Đây là mapping do ứng dụng thiết kế, không phải Spring tự gán status cho custom exceptions. Với domain conflict có thể thêm `ConflictException -> 409`, nhưng cần một nguyên nhân cụ thể như resource name trùng, optimistic locking failure. Không mapping **mọi `IllegalArgumentException`** thành 400 ở production vì lỗi lập trình cũng có thể là IllegalArgumentException.

### 7.2. onError vs onErrorResume

- `onErrorResume` bên trong service/publisher xử lý một lỗi có ý nghĩa nghiệp vụ cụ thể: fallback remote source, chuyển missing value thành typed exception, cleanup có kiểm soát.
- `RouterFunctions.Builder.onError` biến lỗi trong functional handler chain thành **HTTP response nhất quán** cho nhóm routes.
- `ErrorWebExceptionHandler` xử lý **global** exceptions còn chưa xử lý từ web handler chain.

Ví dụ service fallback:

```java
return service.find(id)
        .onErrorResume(RemoteTimeoutException.class,
                ex -> cachedService.find(id))
        .flatMap(book -> ServerResponse.ok().bodyValue(book));
```

Snippet chỉ minh họa khi cached result thực sự được phép dùng. Không fallback dữ liệu quyền truy cập bằng giá trị mở rộng permissions và không tự `onErrorResume(ex -> Mono.empty())` cho mọi lỗi vì dễ gây partial result vô hình.

### 7.3. Lỗi đồng bộ và reactive

Một handler có thể ném `BadInputException` ngay khi parse query:

```java
long id = Long.parseLong(request.pathVariable("id"));
return service.find(id).flatMap(item -> ServerResponse.ok().bodyValue(item));
```

Hoặc nhận `Mono.error(...)` sau async operation:

```java
return service.find(id)
        .switchIfEmpty(Mono.error(new ItemMissingException()))
        .flatMap(item -> ServerResponse.ok().bodyValue(item));
```

Đó là hai thời điểm khác nhau. Route `onError` được thiết kế để xử lý lỗi từ chain của handler, nhưng luôn test cả synchronous exception và reactive error signal trên Spring version được sử dụng.

## 8. Một error contract thống nhất theo RFC 9457

**RFC 9457 Problem Details** quy định các thành phần tiêu chuẩn của error representation. Demo chọn record tự định nghĩa với các fields:

| Field | Vai trò |
| --- | --- |
| `type` | URI reference định danh loại lỗi; ví dụ `about:blank` |
| `title` | Tên lỗi ổn định, tương ứng HTTP status |
| `status` | Con số status phải trùng HTTP status |
| `detail` | Chi tiết an toàn cho client, không lộ secrets |
| `instance` | Đường dẫn resource/request gây lỗi |
| `code` | Mã lỗi tùy biến ổn định để client tự xử lý |
| `requestId` | ID tương quan cho log/support |

Response mẫu:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
X-Request-Id: <server-generated-id>

{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Item not found",
  "instance": "/api/items/999",
  "code": "ITEM_NOT_FOUND",
  "requestId": "<same-server-generated-id>"
}
```

Đây là shape minh họa, không phải một requestId cố định. Hai extension members `code`/`requestId` hợp lệ theo định hướng RFC 9457 nhưng cần được công bố trong API contract. Nếu không có problem type URI riêng, `about:blank` phù hợp.

### 8.1. Có nên dùng Spring ProblemDetail không?

Spring Framework cung cấp `org.springframework.http.ProblemDetail`, ví dụ:

```java
ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, "Item not found");
pd.setTitle("Not Found");
pd.setType(URI.create("about:blank"));
pd.setInstance(URI.create("/api/items/999"));
pd.setProperty("code", "ITEM_NOT_FOUND");
```

Đây là snippet, cần imports. Có thể ghi qua `ServerResponse.status(NOT_FOUND).contentType(APPLICATION_PROBLEM_JSON).bodyValue(pd)` với codec phù hợp. Demo sử dụng record `ApiProblem` để minh bạch JSON fields và không phụ thuộc Bean Validation/exception abstractions thêm.

### 8.2. Lỗi nội bộ phải được che ở HTTP edge

Không trả:

```json
{"error":"database at 10.0.0.5 refused connection; secret=..."}
```

Nên trả:

```json
{
  "type":"about:blank",
  "title":"Internal Server Error",
  "status":500,
  "detail":"Unexpected server error",
  "instance":"/api/items/boom",
  "code":"INTERNAL_ERROR",
  "requestId":"..."
}
```

Log exception đầy đủ (sau redaction) ở server tương quan với requestId. Không gửi stack trace, raw SQL, JWT hoặc connection details sang client.

## 9. Ranh giới: lỗi nào onError không bao hết?

Một request có thể fail **trước** hoặc **sau** functional handler:

| Nguồn lỗi | Thường qua router-local onError? | Cần chú ý |
| --- | --- | --- |
| Bad input từ matched handler | Có | Dùng typed exception 400 |
| Không tìm thấy record trong handler | Có | Typed exception 404 |
| Service failure trong returned Mono | Có thể trong router filter chain | 500 generic, không leak |
| Router không match URL | **Không** | Global 404 error handling |
| CORS/CSRF/auth fail trước router | **Không** | Reactive Security/CORS filters |
| Content-Type/Accept predicate mismatch | Có thể không có matched handler | Test 404/406/415 theo policy |
| JSON decoder malformed trong handler | Tùy nơi phát lỗi | Global codec handling hoặc typed mapping |
| Exception khi body writer đã flush data | Không thể chắc đổi status | Streaming error strategy |
| Client ngắt kết nối | Không luôn có body lỗi để gửi | Cancel/log/cleanup |

### 9.1. ErrorWebExceptionHandler

Spring WebFlux định nghĩa `ErrorWebExceptionHandler` làm global web exception boundary. Spring Boot có error handling tự cấu hình cho WebFlux, và có thể tùy biến `ErrorAttributes` hoặc dùng `AbstractErrorWebExceptionHandler` khi cần kiểm soát error response đồng nhất toàn ứng dụng. Ví dụ signature interface:

```java
// Signature tham khảo, không phải implementation đủ để compile.
Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
```

Triển khai thực sự phải set HTTP status, headers và ghi JSON bytes bằng reactive APIs/codecs **trước khi committed**, tôn trọng DataBuffer lifecycle, handler ordering và Boot default handler. Không tạo một global error handler nửa vời rồi tự `writeWith` buffer không release, gây leak/double write.

### 9.2. Error khác security policy

401 Unauthorized (chưa xác thực) và 403 Forbidden (không đủ quyền) thường do `SecurityWebFilterChain`/reactive exception handlers tạo, không phải business router. Muốn cùng schema `ApiProblem` cho security errors thì cấu hình `ServerAuthenticationEntryPoint` và `ServerAccessDeniedHandler` của reactive Spring Security, đảm bảo không vô ý trả 200/400 thay 401/403.

### 9.3. Annotation advice không là universal router filter

`@RestControllerAdvice`/`@ExceptionHandler` đặc biệt hữu ích cho annotated WebFlux controllers; đừng giả định nó tự bao mọi functional router filter, CORS, static resources, security và những request không có handler. Phân biệt ranh giới lỗi trong kiến trúc và kiểm thử với WebTestClient.

### 9.4. Lỗi sau HTTP commit

Khi response SSE/NDJSON đã gửi headers và chunks đầu, HTTP status không thể đổi từ 200 sang 500 một cách đáng tin và không thể chèn một Problem JSON nguyên vẹn vào giữa stream. Với stream hãy có event protocol cho `event:app-error`, connection close, retry/backoff, resume ID và cancellation. Bộ lọc trên `Mono<ServerResponse>` không đồng nghĩa filter toàn bộ body publisher.

## 10. Request ID và không làm mất body response

Một helper dễ dùng:

```java
static String requestId(ServerRequest request) {
    return (String) request.attributes().computeIfAbsent(
            "demoRequestId",
            ignored -> UUID.randomUUID().toString());
}
```

Trong response factory, dùng một giá trị đã đọc:

```java
String id = requestId(request);
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Request-Id", id)
        .bodyValue(value);
```

Do đó success và error đều ghi request ID ở thời điểm dựng response; không cần cố copy body vào response mới trong `after`.

**Lưu ý:** Nếu URL không match router, local requestId helper không được gọi, vì vậy global 404 từ Boot có thể thiếu header. Muốn trace ID mọi request kể cả authentication/rejected/no-route, tạo ID tại global `WebFilter` hoặc observability middleware thống nhất. Request ID không thay bearer token hoặc authorization.

## 11. Ứng dụng hoàn chỉnh: Spring Boot 3.5 và Java 17

API demo có:
- `GET /api/items` trả 200 list JSON;
- `GET /api/items/{id}` trả 200 hoặc 404;
- `POST /api/items` đọc JSON body và trả 201 + Location;
- `DELETE /api/items/{id}` trả 204 hoặc 404;
- `GET /api/items/boom` cố ý tạo lỗi 500, chỉ dùng để test;
- `before` cấp request ID;
- `filter` từ chối yêu cầu có `X-Demo-Block: yes` để minh họa short-circuit;
- `onError` map domain errors thành thống nhất `ApiProblem`;
- `after` log status object.

### 11.1. Spring Initializr/Maven

Tạo Maven project Java 17+, Spring Boot **3.5.x**, dependency **Spring Reactive Web** qua [Spring Initializr](https://start.spring.io/). `pom.xml` của project có fragment:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

Spring Boot parent/BOM do Initializr tạo quản lý Reactor, Netty, Jackson và Spring Framework versions. Không thêm `spring-boot-starter-web` vào demo này, vì Boot 3.x thường ưu tiên Servlet MVC nếu có cả MVC và WebFlux starters.

Cấu trúc:

```text
src/main/java/com/example/demo/DemoApplication.java
src/test/java/com/example/demo/RouterErrorTest.java
```

### 11.2. Java source độc lập

Đặt tất cả nội dung sau vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routes(ItemHandler handler) {
        return RouterFunctions.route()
                // Route cụ thể đặt trước /{id}.
                .GET("/api/items/boom",
                        req -> Mono.error(
                                new IllegalStateException("internal demo")))
                .GET("/api/items", handler::list)
                .GET("/api/items/{id}", handler::get)
                .POST("/api/items", handler::create)
                .DELETE("/api/items/{id}", handler::delete)

                .before(req -> {
                    ApiHttp.requestId(req);
                    return req;
                })

                // Kiểm tra quyền thực phải dùng Spring Security.
                .filter((req, next) -> {
                    if ("yes".equalsIgnoreCase(
                            req.headers().firstHeader("X-Demo-Block"))) {
                        return ApiHttp.problem(
                                req, HttpStatus.SERVICE_UNAVAILABLE,
                                "DEMO_BLOCKED", "Demo request was blocked");
                    }
                    return next.handle(req);
                })

                .onError(BadInputException.class,
                        (ex, req) -> ApiHttp.problem(
                                req, HttpStatus.BAD_REQUEST,
                                "INVALID_INPUT", ex.getMessage()))
                .onError(ItemMissingException.class,
                        (ex, req) -> ApiHttp.problem(
                                req, HttpStatus.NOT_FOUND,
                                "ITEM_NOT_FOUND", "Item not found"))
                .onError(IllegalStateException.class,
                        (ex, req) -> ApiHttp.problem(
                                req, HttpStatus.INTERNAL_SERVER_ERROR,
                                "INTERNAL_ERROR", "Unexpected server error"))

                .after((req, response) -> {
                    System.out.printf("route=%s status=%d%n",
                            req.path(), response.statusCode().value());
                    return response;
                })
                .build();
    }
}

record Item(long id, String name) {
}

record CreateItem(String name) {
}

record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String requestId) {
}

class BadInputException extends RuntimeException {
    BadInputException(String message) {
        super(message);
    }
}

class ItemMissingException extends RuntimeException {
}

final class ApiHttp {

    private ApiHttp() {
    }

    static String requestId(ServerRequest req) {
        return (String) req.attributes().computeIfAbsent(
                "demoRequestId",
                key -> UUID.randomUUID().toString());
    }

    static Mono<ServerResponse> ok(ServerRequest req, Object value) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId(req))
                .bodyValue(value);
    }

    static Mono<ServerResponse> created(ServerRequest req, Item value) {
        return ServerResponse.created(
                        URI.create("/api/items/" + value.id()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId(req))
                .bodyValue(value);
    }

    static Mono<ServerResponse> noContent(ServerRequest req) {
        return ServerResponse.noContent()
                .header("X-Request-Id", requestId(req))
                .build();
    }

    static Mono<ServerResponse> problem(
            ServerRequest req, HttpStatus status,
            String code, String safeDetail) {

        String id = requestId(req);
        ApiProblem body = new ApiProblem(
                "about:blank",
                status.getReasonPhrase(),
                status.value(),
                safeDetail,
                req.path(),
                code,
                id);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Request-Id", id)
                .bodyValue(body);
    }
}

@Component
class ItemHandler {

    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentMap<Long, Item> items =
            new ConcurrentHashMap<>();

    Mono<ServerResponse> list(ServerRequest req) {
        int limit = parseLimit(req.queryParam("limit").orElse("20"));

        List<Item> result = items.values().stream()
                .sorted(Comparator.comparingLong(Item::id))
                .limit(limit)
                .toList();

        return ApiHttp.ok(req, result);
    }

    Mono<ServerResponse> get(ServerRequest req) {
        long id = parseId(req.pathVariable("id"));

        return Mono.defer(() -> Mono.justOrEmpty(items.get(id)))
                .switchIfEmpty(Mono.error(new ItemMissingException()))
                .flatMap(item -> ApiHttp.ok(req, item));
    }

    Mono<ServerResponse> create(ServerRequest req) {
        return req.bodyToMono(CreateItem.class)
                .switchIfEmpty(Mono.error(
                        new BadInputException("JSON body required")))
                .flatMap(input -> {
                    if (input.name() == null
                            || input.name().isBlank()
                            || input.name().length() > 80) {
                        return Mono.error(new BadInputException(
                                "Name must contain 1 to 80 characters"));
                    }

                    return Mono.fromSupplier(() -> {
                        long id = nextId.incrementAndGet();
                        Item created =
                                new Item(id, input.name().trim());
                        items.put(id, created);
                        return created;
                    });
                })
                .flatMap(item -> ApiHttp.created(req, item));
    }

    Mono<ServerResponse> delete(ServerRequest req) {
        long id = parseId(req.pathVariable("id"));

        return Mono.fromSupplier(() -> items.remove(id) != null)
                .flatMap(deleted -> deleted
                        ? ApiHttp.noContent(req)
                        : Mono.error(new ItemMissingException()));
    }

    private static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id < 1) {
                throw new BadInputException("ID must be positive");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new BadInputException("ID must be numeric");
        }
    }

    private static int parseLimit(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > 100) {
                throw new BadInputException("Limit must be 1 to 100");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BadInputException("Limit must be numeric");
        }
    }
}
```

### 11.3. Giải thích tính runnable và giới hạn

- Java 17 hỗ trợ record. File có một public class `DemoApplication`; các class/record khác cùng package là package-private.
- `@SpringBootApplication` scan `@Component ItemHandler` và bean `RouterFunction<ServerResponse>`.
- Tất cả imports của functional endpoints nằm trong `web.reactive.function.server`; WebMvc.fn sẽ không có cùng reactive method return signatures.
- `ApiHttp` tạo header và error body thống nhất; 204 dùng `noContent().build()` không có body.
- `before` cấp ID; `filter` có thể short-circuit; `onError` map typed errors; `after` chỉ log response object.
- `Mono.defer`/`Mono.fromSupplier` giữ operations lazy; nhưng không biến một blocking JDBC call thành non-blocking I/O. Store demo chỉ là các thao tác in-memory rất ngắn.
- Request body dùng Jackson codec từ WebFlux starter; manual validation kiểm tra field `name`.
- Demo không có authentication, authorization, CORS, CSRF, database durability, transactions, rate limiting; **không triển khai công khai**.
- `GET /api/items/boom` là endpoint mô phỏng lỗi server cần loại bỏ khi chuyển production.
- `X-Demo-Block` là test switch của client, không phải cơ chế security/maintenance production.

### 11.4. Chạy project

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell / CMD
mvnw.cmd spring-boot:run
```

Nếu không đặt `server.port`, server thường chạy trên `http://localhost:8080`.

### 11.5. GET danh sách thành công

```bash
curl -i 'http://localhost:8080/api/items?limit=10'
```

Khi store rỗng:
- HTTP 200;
- `Content-Type: application/json`;
- `X-Request-Id` có giá trị UUID của request;
- body `[]`.

### 11.6. POST tạo item

```bash
curl -i -X POST 'http://localhost:8080/api/items' \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":"Spring WebFlux"}'
```

Nếu project mới khởi động, POST đầu tiên thường tạo ID 1:

```http
HTTP/1.1 201 Created
Location: /api/items/1
Content-Type: application/json
X-Request-Id: ...

{"id":1,"name":"Spring WebFlux"}
```

Header request ID và thứ tự header/JSON whitespace không cố định. Khi chạy nhiều lần, ID tăng; không hardcode ID trong tests.

### 11.7. GET item không tồn tại

```bash
curl -i 'http://localhost:8080/api/items/999999'
```

Dự kiến 404, `Content-Type: application/problem+json` và JSON gồm:

```json
{
  "type":"about:blank",
  "title":"Not Found",
  "status":404,
  "detail":"Item not found",
  "instance":"/api/items/999999",
  "code":"ITEM_NOT_FOUND",
  "requestId":"..."
}
```

`X-Request-Id` ở header và trường `requestId` trong cùng response phải bằng nhau.

### 11.8. Sai ID/limit

```bash
curl -i 'http://localhost:8080/api/items/abc'
curl -i 'http://localhost:8080/api/items?limit=0'
curl -i 'http://localhost:8080/api/items?limit=abc'
```

Dự kiến HTTP 400 Problem Details với `code=INVALID_INPUT`; detail được tạo an toàn bởi domain exceptions: “ID must be numeric”, “Limit must be 1 to 100”, “Limit must be numeric”.

### 11.9. Chặn bởi filter

```bash
curl -i 'http://localhost:8080/api/items' \
  -H 'X-Demo-Block: yes'
```

Dự kiến HTTP 503, Problem body với `code=DEMO_BLOCKED` và `X-Request-Id`. Filter trả response sớm, **không gọi `next.handle(req)`**, nên handler `list` không được thực thi.

### 11.10. Lỗi 500 an toàn

```bash
curl -i 'http://localhost:8080/api/items/boom'
```

Dự kiến HTTP 500 với `code=INTERNAL_ERROR`, `detail=Unexpected server error`. Error body không được xuất hiện raw message `internal demo`. Trong hệ thực, log nội bộ đã redacted cần giữ stacktrace/diagnostic metadata và requestId.

### 11.11. Body sai và decoder errors

```bash
curl -i -X POST 'http://localhost:8080/api/items' \
  -H 'Content-Type: application/json' \
  --data-binary '{"name":" "}'
```

Dự kiến 400 với `code=INVALID_INPUT`. Nhưng với JSON malformed như `{"name":` hoặc `Content-Type: text/plain`, lỗi có thể đến từ HTTP decoder/route matching; **không** khẳng định local onError luôn tạo cùng schema nếu chưa có global error handling.

### 11.12. DELETE và 204

```bash
curl -i -X DELETE 'http://localhost:8080/api/items/1'
```

Nếu item ID=1 tồn tại: status 204, không có body, header `X-Request-Id`. Lần tiếp theo: HTTP 404 Problem Details. Với test tự động hãy lấy ID từ Location của POST, không dựa vào store vừa khởi động.

### 11.13. PowerShell

```powershell
curl.exe -i "http://localhost:8080/api/items"
curl.exe -i "http://localhost:8080/api/items?limit=0"
curl.exe -i "http://localhost:8080/api/items/boom"
curl.exe -i -H "X-Demo-Block: yes" "http://localhost:8080/api/items"
```

Với POST JSON trên PowerShell, tạo file `request.json` UTF-8 chứa `{"name":"Spring WebFlux"}` rồi dùng `curl.exe -i -X POST ... -H "Content-Type: application/json" --data-binary "@request.json"`; cách này tránh khác biệt escape quotes giữa các shell.

## 12. Kiểm thử WebTestClient

Với Boot 3.5, thêm test dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 12.1. File test hoàn chỉnh

Tạo `src/test/java/com/example/demo/RouterErrorTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class RouterErrorTest {

    @Autowired
    WebTestClient client;

    @Test
    void listHasRequestIdAndJsonBody() {
        client.get().uri("/api/items")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .json("[]");
    }

    @Test
    void invalidLimitHasStandardProblemBody() {
        client.get().uri("/api/items?limit=0")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    void missingItemReturnsProblem404() {
        client.get().uri("/api/items/999999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("ITEM_NOT_FOUND");
    }

    @Test
    void filterCanShortCircuitWith503() {
        client.get().uri("/api/items")
                .header("X-Demo-Block", "yes")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DEMO_BLOCKED");
    }

    @Test
    void internalErrorUsesSafeMessage() {
        client.get().uri("/api/items/boom")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail")
                .isEqualTo("Unexpected server error");
    }

    @Test
    void postCreatesResourceWithLocation() {
        client.post().uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"demo item\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.name").isEqualTo("demo item");
    }

    @Test
    void blankNameIsRejected() {
        client.post().uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"  \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_INPUT");
    }
}
```

Test này chỉ xác nhận các đường đi **match router** và logic được khai báo. Chưa bảo đảm cùng JSON format cho `/not-a-route`, preflight CORS, Spring Security 401/403, unsupported media type hay malformed JSON nếu các lỗi đến từ tầng khác. Cần integration tests bổ sung khi áp dụng global error policy.

### 12.2. So header và JSON requestId

Có thể lấy `EntityExchangeResult<byte[]> result = ...expectBody().returnResult()` rồi dùng Jackson `ObjectMapper` parse `requestId`; assert giá trị bằng `result.getResponseHeaders().getFirst("X-Request-Id")`. Đây là một **test extension** cần viết thêm, không phải method đã có trong file ở trên. Không hardcode UUID hoặc lấy ID request trước.

### 12.3. Router-only vs HTTP integration test

`WebTestClient.bindToRouterFunction(router).build()` cho phép test routing/filter nhanh mà không cần mở socket. Tuy vậy không chứng minh Netty/proxy/security chain/global error behavior. Với API public cần cả HTTP integration tests của Boot và end-to-end tests qua gateway/ingress.

### 12.4. Chạy

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Các ví dụ ở đây được biên soạn theo Spring Boot 3.5.x/Spring Framework 6.2.x; thao tác xây README không tương đương việc thực sự khởi động server và chạy test trong một Maven project.

## 13. Production: thiết kế error boundaries theo lớp

Một kiến trúc thực tế có thể tổ chức:

```text
Incoming HTTP request
       |
Global WebFilter: request ID + observation + CORS
       |
SecurityWebFilterChain: 401/403
       |
Routing
  |          \
  |           \ no route -> global error 404
  v
Router-local filters + typed domain onError
       |
Reactive handlers and services
       |
Codec/body write
       |
Client/network
       |
Late write error -> logs/cancel/stream event protocol
```

### 13.1. Một contract, nhiều producers

Có thể dùng chung một error model/problem factory ở tầng các producers:
- Router onError cho domain failures;
- Global ErrorWebExceptionHandler cho fallback/unmatched routes/codec;
- Security exception entry point và denied handler cho 401/403;
- Gateway/proxy contract nếu infrastructure tự tạo lỗi 413/429/502/503.

**Đừng tạo circular dependency:** global handler có thể không có `ServerRequest` từ functional API vì không có match; khi đó factory nên nhận một context-neutral request path/request ID/status thay vì ép mọi lỗi đi qua RouterFunction.

### 13.2. Thứ tự và double write

Nếu tự đăng ký global ErrorWebExceptionHandler, phải xác định priority so với Boot mặc định, kiểm tra response `isCommitted()` và chỉ viết một lần. Khi filter đồng thời ghi error rồi trả Mono.error, một handler khác cố ghi tiếp có thể làm response hỏng. Một khi đã tạo error response thành công, terminal path nên kết thúc có kiểm soát.

### 13.3. Problem title/detail i18n

Mã lỗi máy đọc nên ổn định; localized client message nên được phân tách hoặc được quốc tế hóa bằng policy. Không dùng raw detail làm identifier. Cần giới hạn độ dài/path/parameter echoes trong error body để tránh log injection, reflected content và gửi PII.

### 13.4. Bảo mật và backpressure

- Input DTO phải validate allowed fields, string lengths, batch size, request body limits.
- WebFlux codec buffering có thể cấu hình qua `spring.codec.max-in-memory-size` trong Boot 3.5 cho một số tình huống; nó **không phải** giới hạn tất cả request streaming.
- Không `block()` hoặc gọi JDBC blocking trực tiếp trên event loop.
- Các nguồn `Flux` lớn cần limits/backpressure, tránh `collectList()` vô hạn.
- Body đã stream một phần thì phải báo lỗi bằng application-level semantics hoặc đóng kết nối.
- Không dùng custom error filter để thay authorization checks.

## 14. Bảng lỗi thường gặp

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Router filter không chạy với URL lạ | Không có route match | Global WebFilter/ErrorWebExceptionHandler |
| 401/403 không có cùng Problem schema | Security chặn trước router | Custom reactive entry point/denied handler |
| `onError` không compile sau khi copy MVC code | WebMvc.fn trả response đồng bộ | WebFlux.fn trả Mono<ServerResponse> |
| Filter chặn request nhưng handler vẫn chạy | Vẫn gọi `next.handle(req)` | Return early từ filter |
| Filter không bảo vệ router module khác | Scope chỉ một RouterFunction | Ghép đúng router hoặc WebFilter toàn cục |
| `after` log trước khi SSE kết thúc | Chỉ thấy response object | HTTP network instrumentation |
| Sửa headers làm mất body | Tạo response mới bằng from(...).build() | Header trong response factory |
| 400 thay cho lỗi DB 500 | Catch quá rộng hoặc status mapping sai | Typed exceptions, generic 500 fallback |
| Trả stacktrace trong detail | Copy raw error message | Client-safe details và internal logs |
| `Mono.empty()` không ra 404 | Không map absence | switchIfEmpty typed missing exception |
| JSON malformed không theo schema | Codec error khác domain error | Global codec/error policy |
| Content-Type sai ra 404 | Predicate không match route | Test 404/415/406 contract |
| Không có X-Request-Id trên global 404 | Request ID chỉ sinh local before | Global trace WebFilter |
| Body 204 không rỗng | Trả thêm response DTO | noContent().build() |
| X-Demo-Block bị lạm dụng | Test header do client tự điều khiển | Gỡ production |
| HTTP error sau SSE event đầu không thành JSON | Response committed | Stream error/cancel/reconnect |
| Router `subscribe()` thủ công | Work tách HTTP lifecycle | Return Mono/Flux |
| MDC/tenant trace rơi giữa threads | Reactive context khác ThreadLocal | Context propagation |
| Tests phụ thuộc UUID/ID cứng | Dữ liệu sinh mỗi request | Assert pattern/Location thay số cứng |

## 15. Checklist hoàn thiện

1. [ ] Đúng stack WebFlux, imports thuộc `web.reactive.function.server`.
2. [ ] Router functions theo thứ tự, static path trước variable path.
3. [ ] Scope nested/builder filter được kiểm tra khi thêm endpoints.
4. [ ] `before` xử lý metadata nhanh, không consume body/bật blocking I/O.
5. [ ] `filter` return Mono và short-circuit không gọi next khi cần.
6. [ ] `after` chỉ log response metadata, không tưởng là network complete.
7. [ ] `onError` xử lý typed domain errors; unknown 500 không leak.
8. [ ] Mỗi status/body/header đúng semantics 200/201/204/400/404/500/503.
9. [ ] Error body `application/problem+json` hợp contract RFC9457; `code` ổn định.
10. [ ] Request ID trong body/header trùng nhau và không chứa thông tin nhạy cảm.
11. [ ] Global error policy bao unknown route, codec và security failures.
12. [ ] SecurityWebFilterChain, CORS/CSRF, tenant ACL/rate-limit rõ.
13. [ ] Không có Mono.block/Thread.sleep/JDBC blocking event loop.
14. [ ] Streaming errors sau commit có protocol, cleanup/cancel và retry policy.
15. [ ] WebTestClient test success, bad input, missing item, 500, filter 503, header correlation.
16. [ ] Tests riêng cho global 404/401/403/415, malformed JSON và proxy CORS.
17. [ ] Gỡ test-only `/api/items/boom` và `X-Demo-Block` khi production.
18. [ ] Khi nâng Spring Framework 7/Boot 4, xác minh starter/test imports mới trước khi reuse code.

## 16. Câu hỏi ôn tập

**1. WebFlux.fn filter khác WebFilter ở đâu?** Router filter chỉ áp vào functional routes đã matched trong scope; WebFilter nằm ở HTTP handler chain toàn ứng dụng.

**2. before trả kiểu gì?** `ServerRequest`; không trả `Mono<ServerRequest>` trong Java builder API 6.2.

**3. filter trả kiểu gì?** `Mono<ServerResponse>`, có thể gọi `next.handle(req)` hoặc trả response sớm.

**4. after có nghĩa response body đã gửi hết không?** Không; nó xử lý `ServerResponse` object, còn body publisher có thể stream sau.

**5. onError trong WebFlux.fn trả kiểu gì?** Response provider trả `Mono<ServerResponse>`, khác WebMvc.fn đồng bộ.

**6. Có nên map mọi Exception thành 400 không?** Không; cần phân loại lỗi client/domain/server/security và giữ status tương ứng.

**7. Vì sao một URL không match không nhận local request ID?** Vì local before/filter chưa chạy; cấp request ID ở global WebFilter khi cần bao mọi route.

**8. HTTP 204 có body JSON lỗi thành công không?** Không; response 204 không có body.

**9. Lỗi sau khi một SSE event đã gửi có đổi HTTP status thành 500 không?** Không bảo đảm vì headers đã committed; dùng stream protocol/connection close.

**10. `X-Request-Id` có phải authentication không?** Không. Nó phục vụ correlation và hỗ trợ gỡ lỗi.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — Functional WebFlux Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): before/after/filter, nested scopes, RouterFunction/HandlerFunction.
- [Spring Framework 6.2 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.Builder.html): methods `filter`/`onError`/`before`/`after`.
- [Spring Framework 6.2 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/web/reactive/function/server/ServerRequest.html): mutable attributes, headers, bodyToMono.
- [Spring Framework 6.2 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html): status, headers, bodyValue, body.
- [Spring Framework 6.2 — Reactive Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/ann-rest-exceptions.html): ProblemDetail/RFC9457, ErrorResponse, annotation-handling boundary.
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): starter, error configuration, routing.
- [Spring Security — WebFlux Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html): reactive SecurityWebFilterChain, 401/403.
- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): CorsWebFilter.
- [Project Reactor — Reference Guide](https://projectreactor.io/docs/core/release/reference/): Mono error signaling, doFinally, cancellation.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457): standardized error JSON body and extension fields.

**Kết luận:** `before` bổ sung request metadata, `filter` bao/short-circuit handler, `after` quan sát response object, và `onError` chuyển domain exceptions thành `Mono<ServerResponse>`. Hãy dùng một response factory để có HTTP status, headers và Problem JSON nhất quán; đồng thời phải hiểu local router filters **không thay** global error/security handling. Error sau khi body đã được gửi một phần còn cần chiến lược streaming riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-functional-endpoints-007">Tích hợp router bean với DispatcherHandler</a>

<details>
<summary>Xem chi tiết</summary>

# Spring WebFlux — Tích hợp RouterFunction bean với DispatcherHandler

## 1. Vấn đề và mục tiêu

Spring WebFlux có hai phong cách khai báo HTTP endpoints:

- **Annotated controller:** `@RestController`, `@GetMapping`, `@RequestBody`.
- **Functional endpoint (WebFlux.fn):** `RouterFunction<ServerResponse>` chọn route, `HandlerFunction<ServerResponse>` xử lý `ServerRequest` và tạo `Mono<ServerResponse>`.

Hai phong cách **có thể chạy cùng một `DispatcherHandler` và một HTTP server**. Trong ứng dụng Spring Boot WebFlux, khai báo `RouterFunction<?>` là Spring bean thường đã đủ; các WebFlux infrastructure beans tự phát hiện và kết hợp routers.

Bài này giải thích:

1. `DispatcherHandler` và ba loại delegates `HandlerMapping`, `HandlerAdapter`, `HandlerResultHandler`.
2. Ba thành phần của functional endpoints: `RouterFunctionMapping`, `HandlerFunctionAdapter`, `ServerResponseResultHandler`.
3. Cách Boot nhận các `RouterFunction` beans; vì sao không cần tự tạo `DispatcherHandler` hoặc gọi `RouterFunctions.toHttpHandler`.
4. Nhiều router beans, composition và **first-match routing order**.
5. Việc functional và annotated endpoints cùng tồn tại, khác biệt giữa router-order và handler-mapping-order.
6. Cách tổ chức handlers/services, dependency injection, filters, security/CORS, error handling.
7. Các lỗi phổ biến khi kết hợp `@EnableWebFlux`, Servlet WebMvc.fn, route prefixes, wildcard routes.
8. Ví dụ Java 17 / Spring Boot 3.5.0 / Spring Framework 6.2, Maven, WebTestClient tests và các câu hỏi luyện tập.

**Ngữ cảnh:** Functional WebFlux. Không nhầm package reactive `org.springframework.web.reactive.function.server` với Servlet `org.springframework.web.servlet.function`.

## 2. RouterFunction và HandlerFunction

### 2.1. HandlerFunction

Functional handler nhận một `ServerRequest`, trả về một `Mono<ServerResponse>`:

~~~java
Mono<ServerResponse> hello(ServerRequest request) {
    return ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("Hello WebFlux");
}
~~~

Đây là code fragment, không phải một file Java chạy độc lập. `ServerRequest` có các API như `pathVariable("id")`, `queryParam("q")`, `headers()`, `bodyToMono(BookInput.class)`, `bodyToFlux(...)`, `principal()` và metadata khác.

`ServerResponse` là một mô tả immutable của status, headers, cookies và cách ghi body; framework thực thi việc encode/write, không phải HandlerFunction tự quản lý socket.

### 2.2. RouterFunction

Về bản chất, `RouterFunction<T>` kiểm tra một `ServerRequest` rồi trả `Mono<HandlerFunction<T>>` nếu match hoặc `Mono.empty()` nếu không match.

~~~java
RouterFunction<ServerResponse> route =
        RouterFunctions.route()
                .GET("/hello", request ->
                        ServerResponse.ok().bodyValue("Hello"))
                .build();
~~~

Muốn router này được Spring Boot phát hiện, trả nó từ `@Bean`:

~~~java
@Configuration
class RouterConfiguration {
    @Bean
    RouterFunction<ServerResponse> helloRouter() {
        return RouterFunctions.route()
                .GET("/hello", req ->
                        ServerResponse.ok().bodyValue("Hello"))
                .build();
    }
}
~~~

Khi `RouterConfiguration` nằm trong component scan, Boot đưa bean vào ApplicationContext; không cần viết thêm adapter/mapping.

### 2.3. Functional so với annotated

Annotated:

~~~java
@RestController
class HealthController {
    @GetMapping("/status")
    Mono<Map<String, String>> status() {
        return Mono.just(Map.of("status", "ok"));
    }
}
~~~

Functional:

~~~java
@Bean
RouterFunction<ServerResponse> statusRouter() {
    return RouterFunctions.route()
            .GET("/status", req ->
                    ServerResponse.ok()
                            .bodyValue(Map.of("status", "ok")))
            .build();
}
~~~

Đây là hai phương án **thay thế**; không nên khai báo cả hai cho cùng GET /status trừ khi có kế hoạch ưu tiên được test. Functional handler chủ động đọc ServerRequest và build ServerResponse; annotated controller dùng argument resolvers/return value handlers của framework.

## 3. DispatcherHandler xử lý request thế nào?

### 3.1. Luồng chung

~~~text
Browser or HTTP client
        |
        v
Reactive HTTP server (thường Reactor Netty)
        |
        v
WebHttpHandlerBuilder-created HttpHandler
        |
        v
WebFilter/WebExceptionHandler chain
        |
        v
DispatcherHandler
        |
        +--> HandlerMapping: tìm handler
        |
        +--> HandlerAdapter: gọi handler
        |
        +--> HandlerResultHandler: ghi response
        |
        v
HTTP status + headers + body
~~~

`DispatcherHandler` là front controller của WebFlux, **không phải DispatcherServlet**. Nó lấy các delegate beans từ ApplicationContext và không tự biết chi tiết về từng RouterFunction hay annotation.

| Component | Mục đích |
| --- | --- |
| `HandlerMapping` | Chọn handler phù hợp theo request |
| `HandlerAdapter` | Gọi handler đã chọn |
| `HandlerResult` | Bao kết quả và context của handler |
| `HandlerResultHandler` | Chọn cách render response và hoàn tất ghi HTTP |

Theo tài liệu Spring Framework 6.2, `DispatcherHandler` hỏi `HandlerMapping` beans theo thứ tự và **sử dụng kết quả match đầu tiên**.

### 3.2. Bộ ba hạ tầng WebFlux.fn

| Class | Vai trò |
| --- | --- |
| `RouterFunctionMapping` | Khám phá, sắp xếp, kết hợp RouterFunction beans và route request |
| `HandlerFunctionAdapter` | Cho phép DispatcherHandler gọi một HandlerFunction |
| `ServerResponseResultHandler` | Ghi Mono<ServerResponse> bằng ServerResponse.writeTo(...) |

Flow:

~~~text
GET /api/fn/books/42
    |
    v
DispatcherHandler
    |
    v
RouterFunctionMapping
    |
    v
RouterFunction.route(request)
    |
    v
HandlerFunction for GET /api/fn/books/{id}
    |
    v
HandlerFunctionAdapter
    |
    v
Mono<ServerResponse>
    |
    v
ServerResponseResultHandler -> codecs -> HTTP response
~~~

Nếu composite router không match, `RouterFunctionMapping` có thể trả không có handler để DispatcherHandler thử mapping khác.

### 3.3. Annotated components khác

Annotated controllers có `RequestMappingHandlerMapping` và `RequestMappingHandlerAdapter`; `@ResponseBody`/`@RestController` được `ResponseBodyResultHandler` xử lý. `ServerResponseResultHandler` xử lý ServerResponse functional. Tất cả làm việc trong cùng một DispatcherHandler nhờ contract delegate chung.

**Không** tự gọi `ServerResponse.writeTo(...)` trong HandlerFunction thông thường; để framework quản lý codecs, write lifecycle và error handling.

## 4. Spring Boot tự cấu hình những gì?

Dùng `spring-boot-starter-webflux` để Spring Boot 3.5 tạo reactive HTTP runtime, WebFlux DispatcherHandler, handler mappings/adapters/result handlers, codecs và các tiện ích Boot.

**Không cần**:

- Tự đăng ký RouterFunctionMapping.
- Tự đăng ký HandlerFunctionAdapter/ServerResponseResultHandler.
- Tự tạo thêm DispatcherHandler.
- Tự biến router bean thành HttpHandler.
- Gắn `@EnableWebFlux` chỉ để chạy functional routing.

Với Boot, nếu muốn thêm CORS/formatters/codecs, dùng `@Configuration implements WebFluxConfigurer` **không gắn @EnableWebFlux**, để giữ các Boot-specific WebFlux auto-config conveniences.

### 4.1. @EnableWebFlux dùng trong trường hợp nào?

Trong Spring Framework non-Boot Java configuration, `@EnableWebFlux` nhập các infrastructure configurations. Nếu dùng nó trong Boot, bạn đang chọn kiểm soát WebFlux config sâu hơn; một số Boot auto-configuration có thể lùi lại và static/codec/error defaults cần được tự kiểm. Không dùng cho bài này.

### 4.2. Cẩn thận starter-web

Nếu classpath có đồng thời `spring-boot-starter-web` và `spring-boot-starter-webflux`, Boot thường chọn MVC theo mặc định. Khi đó `RouterFunction<ServerResponse>` của WebFlux sẽ không chạy trong một WebFlux DispatcherHandler như mong đợi. Để demo reactive, chỉ dùng WebFlux starter.

### 4.3. Sai import là lỗi thường gặp

WebFlux.fn:

~~~java
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
~~~

WebMvc.fn:

~~~java
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
~~~

Chữ ký và lifecycle khác; hãy kiểm IDE auto-import.

## 5. Nhiều RouterFunction beans

### 5.1. RouterFunctionMapping khám phá và compose

Tài liệu Spring nói rằng RouterFunctionMapping nhận một hoặc nhiều bean `RouterFunction<?>`, **sắp xếp** chúng và kết hợp bằng `RouterFunction.andOther`.

Ví dụ:

~~~text
catalogRoutes  -> GET /api/catalog
orderRoutes    -> GET /api/orders
systemRoutes   -> GET /internal/ping
~~~

Một WebFlux DispatcherHandler đủ để phục vụ tất cả. Khi các paths không trùng, bean ordering ít ảnh hưởng; khi overlaps, thứ tự quyết định outcome.

### 5.2. Specific trước variable

Sai:

~~~java
return RouterFunctions.route()
        .GET("/api/books/{id}", handler::byId)
        .GET("/api/books/latest", handler::latest)
        .build();
~~~

GET /api/books/latest có thể vào byId với id="latest". Đúng:

~~~java
return RouterFunctions.route()
        .GET("/api/books/latest", handler::latest)
        .GET("/api/books/{id}", handler::byId)
        .build();
~~~

**Functional routes được xét theo thứ tự**, không sử dụng thuật toán “most specific wins” của annotated controller mappings trong cùng router builder.

### 5.3. Thứ tự nhiều beans và @Order

Khi mỗi bean có chồng path hoặc predicate, đừng dựa vào tên file, registration order hoặc giả định @Order áp dụng theo cùng cách trong mọi version. Nếu thực sự cần precedence:

- Đặt các overlapping routes trong **cùng một builder**.
- Hoặc compose rõ bằng `andOther`/`add`.
- Hoặc chọn sorting contract được Framework version hỗ trợ và viết HTTP integration test.
- Tốt nhất thiết kế **một owner cho mỗi method/path**.

### 5.4. Kết hợp tường minh

~~~java
RouterFunction<ServerResponse> first =
        RouterFunctions.route()
                .GET("/api/a", handler::a)
                .build();

RouterFunction<ServerResponse> second =
        RouterFunctions.route()
                .GET("/api/b", handler::b)
                .build();

RouterFunction<ServerResponse> combined =
        first.andOther(second);
~~~

Code fragment cần handler methods. Composite xét router thứ nhất rồi fallback sang router thứ hai nếu không match. Builder cũng hỗ trợ `add(second)` khi cần kết hợp.

### 5.5. Nested router

~~~java
return RouterFunctions.route()
        .path("/api/books", b -> b
                .GET("/latest", handler::latest)
                .GET("/{id}", handler::byId)
                .POST(handler::create))
        .build();
~~~

Nesting giảm lặp path prefix; route order vẫn được giữ.

## 6. Functional và annotated controller có thể chạy cùng nhau

Giả sử:

~~~java
@RestController
class AnnotatedStatus {
    @GetMapping("/api/annotated/status")
    Mono<Map<String, String>> status() {
        return Mono.just(Map.of("mode", "annotated"));
    }
}
~~~

và:

~~~java
@Bean
RouterFunction<ServerResponse> functionalStatus() {
    return RouterFunctions.route()
            .GET("/api/fn/status", request ->
                    ServerResponse.ok()
                            .bodyValue(Map.of("mode", "functional")))
            .build();
}
~~~

Hai routes không xung đột, có cùng WebFlux DispatcherHandler.

**Nếu cùng GET /api/status xuất hiện ở cả hai**, DispatcherHandler chọn HandlerMapping match đầu tiên dựa vào mapping order. Không nên hứa một loại handler luôn thắng. Cần kiểm HandlerMapping order thực tế và có regression test, hoặc loại bỏ overlap.

Ba tầng chọn handler cần phân biệt:

1. **Trong functional router:** first matching request predicate wins.
2. **Giữa các HandlerMapping:** first matching handler mapping wins.
3. **Trong annotated RequestMappingHandlerMapping:** mapping specificity/conditions chọn method theo annotation matching algorithm.

`WebFluxConfigurer#configurePathMatching(...).addPathPrefix` thường dùng với **annotated controller type predicate**; không mặc định thêm prefix cho tất cả functional RouterFunction paths.

## 7. HandlerFilterFunction và WebFilter

Functional route builder có `filter`, `before`, `after`:

~~~java
return RouterFunctions.route()
        .GET("/api/books", handler::list)
        .filter((request, next) ->
                next.handle(request)
                        .doOnError(error -> metrics.increment()))
        .build();
~~~

Đây là fragment, metrics chưa định nghĩa. Filter này áp dụng cho router scope, không tự động áp các @RestController hoặc RouterFunction beans khác.

Một filter có thể short-circuit:

~~~java
.filter((request, next) -> {
    if (maintenance.isActive()) {
        return ServerResponse.status(503)
                .bodyValue(Map.of("code", "MAINTENANCE"));
    }
    return next.handle(request);
})
~~~

Phân biệt:

| Cơ chế | Phạm vi |
| --- | --- |
| `HandlerFilterFunction` | Các HandlerFunction trong router/nested route được filter |
| `WebFilter` | WebHandler chain, có thể bao annotated + functional |
| `SecurityWebFilterChain` | Reactive authentication/authorization |
| `CorsWebFilter` | CORS cho WebFlux functional endpoints theo tài liệu Spring |

Router filter không phải Spring Security policy. Trong ứng dụng có frontend khác origin, CORS preflight cần allowlist origins/methods/headers và thứ tự phù hợp với Security; CORS không thay auth/CSRF.

## 8. Lỗi và HTTP status trong functional handlers

### 8.1. Không dựa hoàn toàn vào @RestControllerAdvice

`@RestControllerAdvice` thuộc annotated controller exception resolution; không mặc định nó xử lý mọi lỗi từ HandlerFunction. Một route filter có thể xử lý domain input error:

~~~java
.filter((request, next) ->
        next.handle(request)
                .onErrorResume(BadInputException.class,
                        ex -> ServerResponse.badRequest()
                                .bodyValue(Map.of(
                                        "code", "INVALID_INPUT"))))
~~~

Với HTTP API public, nên dùng `ProblemDetail` RFC 9457, đặt HTTP status và body status tương thích, không echo exception message hoặc secrets.

### 8.2. No-match vs domain not-found

- `GET /api/fn/books/999` match router nhưng store không có record → domain 404 với `BOOK_NOT_FOUND`.
- `GET /api/fn/unknown` không match bất kỳ handler nào → framework 404, có thể có body khác.

Một HTTP 404 không đủ biết nguồn lỗi; app cần domain code nếu client có logic khác nhau.

### 8.3. Accept và Content-Type predicates

Nếu functional route chứa `RequestPredicates.accept(APPLICATION_JSON)` nhưng client gửi media type khác, router có thể **không match**, khiến response cuối là 404 hoặc error khác tùy mappings. Đừng giả định mọi mismatch luôn 406. Tương tự Content-Type predicate không tự bảo đảm 415 trong mọi topology. Test chính xác method/path/predicates/codecs.

### 8.4. Lỗi khi response đã committed

Khi SSE/stream đã gửi status 200 và body bytes, không thể đổi thành HTTP 500 JSON mới trên cùng response. Nếu channel còn usable, có thể gửi SSE error event theo application protocol; nếu client ngắt transport, cancel/cleanup/log. `DispatcherHandler`/`ServerResponseResultHandler` không thể đảo ngược HTTP headers đã gửi.

## 9. Dự án Maven chạy độc lập

Dự án dùng **Java 17 + Spring Boot 3.5.0 + Spring WebFlux** với:

- Hai bean RouterFunction: `bookRoutes` và `systemRoutes`.
- Một annotated controller `AnnotatedPingController`.
- BookHandler được Spring inject BookStore.
- Route specific `/latest` nằm trước variable `/{id}`.
- POST JSON tạo Book trong in-memory Map, trả 201 + relative Location.
- HandlerFilterFunction map invalid input thành ProblemDetail HTTP 400.
- WebTestClient integration tests xác minh DispatcherHandler/RouterFunctionMapping và endpoints.

### 9.1. Cấu trúc

~~~text
functional-router-demo/
├── pom.xml
└── src/
    ├── main/java/demo/FunctionalRouterApplication.java
    └── test/java/demo/FunctionalRouterTest.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>functional-router-demo</artifactId>
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
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
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

### 9.3. FunctionalRouterApplication.java

Đặt toàn bộ Java sau trong `src/main/java/demo/FunctionalRouterApplication.java`. Chỉ một top-level public class; những records/classes khác là package-private.

~~~java
package demo;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class FunctionalRouterApplication {
    public static void main(String[] args) {
        SpringApplication.run(FunctionalRouterApplication.class, args);
    }
}

record Book(long id, String title) {
}
record NewBook(String title) {
}
class BadInputException extends RuntimeException {
}

@Service
class BookStore {
    private final ConcurrentMap<Long, Book> books =
            new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(10);

    BookStore() {
        books.put(1L, new Book(1L, "Spring WebFlux"));
    }

    Mono<Book> find(long id) {
        return Mono.defer(() -> Mono.justOrEmpty(books.get(id)));
    }

    Mono<Book> create(String title) {
        return Mono.fromSupplier(() -> {
            long id = ids.incrementAndGet();
            Book book = new Book(id, title);
            books.put(id, book);
            return book;
        });
    }
}

@Component
class BookHandler {
    private final BookStore store;

    BookHandler(BookStore store) {
        this.store = store;
    }

    Mono<ServerResponse> latest(ServerRequest request) {
        return store.find(1L)
                .flatMap(book -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book));
    }

    Mono<ServerResponse> byId(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException exception) {
            return Mono.error(new BadInputException());
        }

        if (id <= 0) {
            return Mono.error(new BadInputException());
        }

        return store.find(id)
                .flatMap(book -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(book))
                .switchIfEmpty(problem(HttpStatus.NOT_FOUND,
                        "BOOK_NOT_FOUND", "Không tìm thấy sách"));
    }

    Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(NewBook.class)
                .switchIfEmpty(Mono.error(new BadInputException()))
                .flatMap(input -> {
                    if (input.title() == null
                            || input.title().isBlank()) {
                        return Mono.error(new BadInputException());
                    }
                    return store.create(input.title())
                            .flatMap(book -> ServerResponse
                                    .created(URI.create(
                                            "/api/fn/books/" + book.id()))
                                    .contentType(
                                            MediaType.APPLICATION_JSON)
                                    .bodyValue(book));
                });
    }

    Mono<ServerResponse> badInput() {
        return problem(HttpStatus.BAD_REQUEST,
                "INVALID_INPUT", "Dữ liệu đầu vào không hợp lệ");
    }

    private Mono<ServerResponse> problem(
            HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                status, detail);
        p.setProperty("code", code);
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(p);
    }
}

@Configuration(proxyBeanMethods = false)
class FunctionalRoutes {
    @Bean
    RouterFunction<ServerResponse> bookRoutes(BookHandler books) {
        return RouterFunctions.route()
                .GET("/api/fn/books/latest", books::latest)
                .GET("/api/fn/books/{id}", books::byId)
                .POST("/api/fn/books", books::create)
                .filter((request, next) ->
                        next.handle(request)
                                .onErrorResume(
                                        BadInputException.class,
                                        error -> books.badInput()))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> systemRoutes() {
        return RouterFunctions.route()
                .GET("/api/fn/ping", request ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of(
                                        "style", "functional",
                                        "ok", true)))
                .build();
    }
}

@RestController
class AnnotatedPingController {
    @GetMapping(path = "/api/annotated/ping",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Map<String, Object>> ping() {
        return Mono.just(Map.of(
                "style", "annotated", "ok", true));
    }
}
~~~

### 9.4. Giải thích code

- Hai RouterFunction beans có disjoint paths, không phụ thuộc bean-order.
- RouterFunctionMapping khám phá hai beans; HandlerFunctionAdapter gọi functional handlers; ServerResponseResultHandler encode/write.
- `AnnotatedPingController` chạy trong cùng DispatcherHandler qua RequestMappingHandlerMapping/Adapter.
- `/latest` trước `/{id}` để không parse chữ "latest" thành long.
- `BookHandler` là @Component singleton nhưng không giữ mutable ServerRequest trong field.
- `BookStore` dùng AtomicLong + ConcurrentHashMap minh họa, không có database/transactions.
- `Mono.defer` và `Mono.fromSupplier` giúp tác vụ được thực hiện khi có subscription.
- Router-specific filter chỉ bắt `BadInputException` của book routes; không tự bắt lỗi annotated/security.
- `ProblemDetail` và HTTP status được xây an toàn, không trả exception message.
- `ServerResponse.created(URI.create(...))` trả 201 Location relative URL, tránh hardcode internal backend origin.
- Không thêm @EnableWebFlux hoặc tự tạo infrastructure beans.
- Demo không có authentication, CORS hay persistence; production phải thiết kế riêng.

### 9.5. Chạy

~~~bash
mvn spring-boot:run
~~~

Functional:

~~~bash
curl -i http://localhost:8080/api/fn/ping
curl -i http://localhost:8080/api/fn/books/latest
curl -i http://localhost:8080/api/fn/books/1
~~~

Ví dụ body:

~~~json
{"id":1,"title":"Spring WebFlux"}
~~~

Annotated:

~~~bash
curl -i http://localhost:8080/api/annotated/ping
~~~

Body:

~~~json
{"style":"annotated","ok":true}
~~~

Lỗi:

~~~bash
curl -i http://localhost:8080/api/fn/books/999
curl -i http://localhost:8080/api/fn/books/not-a-number
~~~

Kỳ vọng 404/BOOK_NOT_FOUND và 400/INVALID_INPUT tương ứng.

POST:

~~~bash
curl -i -X POST http://localhost:8080/api/fn/books \
  -H 'Content-Type: application/json' \
  --data '{"title":"Reactive Book"}'
~~~

Lần tạo đầu có thể trả:

~~~http
HTTP/1.1 201 Created
Location: /api/fn/books/11
Content-Type: application/json

{"id":11,"title":"Reactive Book"}
~~~

ID tăng qua requests; đừng hardcode 11 trong tests.

## 10. WebTestClient integration tests

Tạo `src/test/java/demo/FunctionalRouterTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter;
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping;
import org.springframework.web.reactive.function.server.support.ServerResponseResultHandler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FunctionalRouterTest {
    @Autowired WebTestClient http;
    @Autowired ApplicationContext context;

    @Test
    void webfluxInfrastructureIsManagedBySpring() {
        assertNotNull(context.getBean(DispatcherHandler.class));
        assertNotNull(context.getBean(RouterFunctionMapping.class));
        assertNotNull(context.getBean(HandlerFunctionAdapter.class));
        assertNotNull(context.getBean(ServerResponseResultHandler.class));
        assertTrue(context.getBeansOfType(RouterFunction.class)
                .size() >= 2);
    }

    @Test
    void functionalAndAnnotatedBothRespond() {
        http.get().uri("/api/fn/ping").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.style").isEqualTo("functional");

        http.get().uri("/api/annotated/ping").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.style").isEqualTo("annotated");
    }

    @Test
    void exactRouteWinsOverVariableRoute() {
        http.get().uri("/api/fn/books/latest").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.title").isEqualTo("Spring WebFlux");
    }

    @Test
    void variableRouteWorks() {
        http.get().uri("/api/fn/books/1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void missingBookReturnsDomain404() {
        http.get().uri("/api/fn/books/999999").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("BOOK_NOT_FOUND");
    }

    @Test
    void invalidBookIdReturns400() {
        http.get().uri("/api/fn/books/abc").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    void createReturns201WithLocation() {
        http.post().uri("/api/fn/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NewBook("Test"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches(
                        HttpHeaders.LOCATION,
                        "/api/fn/books/[0-9]+")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Test");
    }

    @Test
    void invalidPostReturns400() {
        http.post().uri("/api/fn/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NewBook(" "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    void unmatchedPathReturns404() {
        http.get().uri("/api/fn/no-such-route").exchange()
                .expectStatus().isNotFound();
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Đây là Spring Boot HTTP integration test: kiểm exact beans trong ApplicationContext và routing thật qua server, không chỉ gọi phương thức controller trực tiếp.

### 10.1. Vì sao không chỉ bindToRouterFunction?

WebTestClient có:

~~~java
WebTestClient client =
        WebTestClient.bindToRouterFunction(router).build();
~~~

Cách này thích hợp **isolated route tests** và chạy nhanh, nhưng không tự chứng minh RouterFunction bean được Spring Boot phát hiện, nhiều beans được RouterFunctionMapping compose đúng hay annotated controller cùng DispatcherHandler. Vì bài học nhắm bean-integration, `@SpringBootTest(RANDOM_PORT)` là một kiểm thử quan trọng.

## 11. Tình huống gỡ lỗi

| Triệu chứng | Kiểm tra |
| --- | --- |
| Router bean có nhưng 404 | Component scan, package, exact route/predicates |
| Không thấy RouterFunctionMapping | App type MVC, sai WebFlux dependency |
| Import RouterFunction sai | `web.servlet.function` vs `web.reactive.function.server` |
| `/latest` bị parse long | Variable route trước static route |
| Router A chiếm Router B | Predicate overlap và bean composition ordering |
| Annotated thắng functional trên same path | HandlerMapping order; nên tránh overlap |
| JSON body fail | Jackson/codec configuration/media type |
| Handler filter không chạy ở annotated route | Phạm vi HandlerFilterFunction chỉ theo router |
| @RestControllerAdvice không bắt functional error | Dùng route filter/WebExceptionHandler đúng tầng |
| CORS preflight sai | CorsWebFilter/Security OPTIONS policy |
| Static resources biến mất sau @EnableWebFlux | Boot auto-configuration đã thay đổi |
| Blocking DB kéo chậm toàn app | Netty event-loop bị block |
| bindToRouterFunction pass nhưng Boot app 404 | Test isolated không xác minh bean discovery |
| SSE lỗi sau 200 nhưng không thể gửi 500 | Response đã committed |

## 12. Bài tập

**Bài 1.** Mô tả ba bước RouterFunctionMapping → HandlerFunctionAdapter → ServerResponseResultHandler và trách nhiệm DispatcherHandler.

**Bài 2.** Đăng ký hai bean RouterFunction ở hai module khác nhau với paths không trùng; kiểm cả hai bằng WebTestClient.

**Bài 3.** Thêm @RestController trong cùng ứng dụng, chứng minh cả hai phong cách dùng một server/DispatcherHandler.

**Bài 4.** Đặt variable route trước static route, quan sát GET /books/latest, sau đó sửa và viết test.

**Bài 5.** Tạo hai RouterFunction beans có method/path overlap và đo precedence thực tế; refactor thành một owner.

**Bài 6.** Compose router qua andOther/builder.add thay vì tạo duplicate broad routes.

**Bài 7.** Dùng HandlerFilterFunction để map BadInputException thành 400 ProblemDetail; kiểm filter không áp vào annotated controller.

**Bài 8.** So sánh WebFilter và HandlerFilterFunction về phạm vi, cross-cutting logging và Security.

**Bài 9.** Tạo một functional POST JSON handler, kiểm request Content-Type, body decode và lỗi.

**Bài 10.** Thêm CORS preflight qua CorsWebFilter, allowlist SPA origin; giải thích vì sao không thay authorization.

**Bài 11.** So sánh bindToRouterFunction với SpringBootTest RANDOM_PORT. Điều gì chỉ integration test mới xác minh được?

**Bài 12.** Thử gọi RouterFunctions.toHttpHandler trực tiếp trong custom adapter; nêu các config/filters/codecs cần quản lý.

**Bài 13.** Tạo một wildcard route rồi test nó không chiếm annotated/static endpoints.

**Bài 14.** Chuyển blocking legacy call sang Mono.fromCallable + boundedElastic, kiểm event-loop không bị block.

## 13. Checklist production

- [ ] Spring Boot dùng reactive WebFlux đúng package và dependencies.
- [ ] RouterFunction beans thực sự xuất hiện trong ApplicationContext.
- [ ] DispatcherHandler, RouterFunctionMapping, HandlerFunctionAdapter, ServerResponseResultHandler do Framework quản lý.
- [ ] Functional và annotated routes có path ownership rõ, không overlap tùy tiện.
- [ ] Trong functional builder, static/specific routes được xét trước variable/wildcard.
- [ ] Mọi route methods/predicates/Accept/Content-Type có integration tests.
- [ ] Handler có DI, không lưu request-specific mutable state trong singleton.
- [ ] Handler trả Publisher, không gọi subscribe() fire-and-forget cho request work.
- [ ] HTTP status/ErrorResponse/ProblemDetail an toàn, không leak stacktraces/secrets.
- [ ] SecurityWebFilterChain và CorsWebFilter có scope/order phù hợp.
- [ ] Reactive I/O không bị blocking, có timeout/backpressure/cancellation policy.
- [ ] Stream sau commit không cố thay HTTP status/headers.
- [ ] Static resources và actuator/health mappings không bị wildcard chiếm.
- [ ] WebTestClient qua HTTP server/Boot context xác minh bean discovery và annotated coexistence.
- [ ] Metrics dùng low-cardinality route templates, logs có safe correlation IDs.

## 14. Ghi nhớ

- `RouterFunctionMapping` nhận Spring-managed RouterFunction beans, order/compose chúng và tìm handler.
- `HandlerFunctionAdapter` gọi functional handler; `ServerResponseResultHandler` ghi ServerResponse.
- Functional và annotated controllers dùng **cùng DispatcherHandler**, nhưng HandlerMappings/Adapters khác nhau.
- Functional builder dùng **first match**, DispatcherHandler dùng **first matching HandlerMapping**; annotated selection dùng specificity.
- Không cần `@EnableWebFlux` hay custom HttpHandler để chạy RouterFunction bean với Boot.
- Không dựa vào incidental bean order khi có overlapping routes; tránh overlap hoặc compose có test.
- HandlerFilterFunction khác WebFilter/SecurityWebFilterChain về scope.
- Isolated router test không chứng minh Spring Boot bean discovery; dùng HTTP WebTestClient integration test.
- Thiết kế reactive publisher/error/cancellation và bảo vệ thông tin nhạy cảm ngay từ đầu.

## 15. Tài liệu tham khảo

- [Spring Framework 6.2 — Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html).
- [Spring Framework 6.2 — DispatcherHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html).
- [Spring Framework 6.2 — WebFlux Configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html).
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html).
- [RouterFunctionMapping Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/server/support/RouterFunctionMapping.html).
- [HandlerFunctionAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/server/support/HandlerFunctionAdapter.html).
- [ServerResponseResultHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/function/server/support/ServerResponseResultHandler.html).
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/).


</details>

- [Quay lại đầu trang](#back-to-top)
