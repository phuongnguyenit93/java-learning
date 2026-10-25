<a id="back-to-top"></a>

# Hạ tầng WebFlux

## Menu
- [HttpHandler, WebHandler và ServerWebExchange](#webflux-infrastructure-001)
- [DispatcherHandler, HandlerMapping, HandlerAdapter và HandlerResultHandler](#webflux-infrastructure-002)
- [WebFilter, WebFilterChain và WebExceptionHandler](#webflux-infrastructure-003)
- [ServerHttpRequest, ServerHttpResponse và dữ liệu request/response](#webflux-infrastructure-004)
- [Codec, HttpMessageReader, HttpMessageWriter và cấu hình giới hạn bộ nhớ](#webflux-infrastructure-005)
- [@EnableWebFlux, WebFluxConfigurer và cấu hình hạ tầng](#webflux-infrastructure-006)
- [WebSession, LocaleContext và Reactor Context](#webflux-infrastructure-007)
- [Máy chủ reactive, kết nối và khác biệt với mô hình Servlet](#webflux-infrastructure-008)

## <a id="webflux-infrastructure-001">HttpHandler, WebHandler và ServerWebExchange</a>

<details>
<summary>Xem chi tiết</summary>

# HttpHandler, WebHandler và ServerWebExchange — nền tảng xử lý HTTP trong Spring WebFlux

## 1. Vấn đề: giữa socket HTTP và @RestController có những lớp nào?

Khi một client gửi `GET /api/exchange/hello/Lan` tới ứng dụng Spring WebFlux, người viết controller thường chỉ thấy `@GetMapping` và `Mono<T>`. Tuy nhiên framework cần giải quyết nhiều việc trước và sau khi gọi controller:

- Nhận request/response từ Reactor Netty, Tomcat, Jetty hoặc một runtime HTTP tương thích.
- Biểu diễn HTTP request/response qua abstraction chung không phụ thuộc implementation server.
- Cung cấp một đối tượng đại diện **toàn bộ trao đổi HTTP**: attributes, locale, web session, principal, request path, multipart/form data, response lifecycle.
- Cho `WebFilter` áp dụng logic trước/sau handler.
- Định tuyến tới annotated controller, functional endpoint hoặc handler tùy biến.
- Áp dụng codec, xử lý exception, viết body bất đồng bộ và kết thúc response.
- Giữ đúng quy tắc Reactive Streams và phối hợp khi client chậm, hủy yêu cầu hoặc socket gặp lỗi.

Ba abstraction quan trọng nhất ở tầng hạ tầng là:

1. `HttpHandler` — hợp đồng HTTP **thấp nhất** của Spring WebFlux, nhận `ServerHttpRequest` và `ServerHttpResponse`, trả `Mono<Void>`.
2. `WebHandler` — hợp đồng xử lý HTTP ở tầng web cao hơn, nhận `ServerWebExchange`, trả `Mono<Void>`.
3. `ServerWebExchange` — ngữ cảnh đại diện cho **một cuộc trao đổi request–response** với các services HTTP/web liên quan.

Ba khái niệm này **không tương đương** với `HandlerFunction` (functional routing) hay `DispatcherHandler` (front controller của WebFlux). Chúng nằm ở những tầng khác nhau và kết nối qua `HttpWebHandlerAdapter` / `WebHttpHandlerBuilder`.

**Phạm vi bài:** Spring Framework **6.1.x**, Spring Boot **3.3.5**, Java **17**, stack **Spring WebFlux**. Bài sử dụng Reactor `Mono`/`Flux`, `WebFilter`, `WebExceptionHandler`, `WebTestClient` và mock HTTP classes. Nó không giả định chỉ Reactor Netty mới chạy WebFlux; WebFlux có adapters cho các runtime được Spring hỗ trợ.

### Sau bài này bạn sẽ biết

- Vẽ đúng đường đi từ adapter HTTP đến `DispatcherHandler` và bộ xử lý result.
- Đọc chữ ký Java của `HttpHandler`, `WebHandler` và `WebFilter`.
- Hiểu vì sao `ServerWebExchange` khác request/response objects và khác `ServerRequest` của functional endpoint.
- Sử dụng request attributes, headers, query, session, principal, locale và response đúng vòng đời.
- Hiểu thứ tự `WebFilter`, `WebExceptionHandler`, định tuyến và serialization.
- Lắp một `HttpHandler` từ `WebHandler` thủ công và biết khi nào không nên làm vậy.
- Tạo ứng dụng WebFlux với WebFilter/controller, chạy curl và test end-to-end bằng `WebTestClient`.
- Tránh các lỗi blocking event-loop, body bị subscribe hai lần, response đã commit, bỏ quên cancellation và rò rỉ DataBuffer.

## 2. Bức tranh lớn của WebFlux

Một ứng dụng Spring Boot WebFlux dùng runtime Reactor Netty thường có đường đi như sau:

```text
HTTP client
   |
   v
Runtime HTTP server: Reactor Netty / supported adapter
   |
   v
ServerHttpRequest + ServerHttpResponse
   |
   v
HttpHandler (low-level reactive HTTP contract)
   |
   v
HttpWebHandlerAdapter
   |  tạo ServerWebExchange, thiết lập services
   v
WebExceptionHandler chain
   |
   v
WebFilter chain
   |
   v
WebHandler
   |
   +--- DispatcherHandler (WebFlux front controller, thường dùng)
   |      |
   |      +--> HandlerMapping tìm endpoint
   |      +--> HandlerAdapter gọi handler
   |      +--> HandlerResultHandler ghi kết quả
   |
   +--- hoặc một WebHandler tùy chỉnh nếu application xây low-level
   |
   v
Response commit + write + completion signals
   |
   v
HTTP client
```

Sơ đồ mô tả trách nhiệm **logic**, không thể hiện một thread riêng cho từng hình hộp. `WebHttpHandlerBuilder` lắp chuỗi các thành phần; việc decorator/filter/exception handler bao quanh có ảnh hưởng tới signal propagation khi thành công hoặc gặp lỗi.

**Điểm phân biệt:** `DispatcherHandler` **là một implementation của WebHandler**. Nó không thay thế abstraction `HttpHandler`; adapter ở phía ngoài vẫn nhận request/response từ HTTP server. Các annotated controllers và WebFlux.fn routes được đặt dưới `DispatcherHandler` qua HandlerMapping/Adapter/ResultHandler.

### Bốn tầng không nên nhầm

| Tầng | Hợp đồng | Biết gì? | Vai trò |
| --- | --- | --- | --- |
| Server adapter | API Reactor Netty, Servlet async/non-blocking bridge, v.v. | Socket/server-specific events | Chuyển server I/O sang Spring HTTP |
| `HttpHandler` | `handle(ServerHttpRequest, ServerHttpResponse)` | HTTP request/response dạng reactive | Entry point độc lập server |
| `WebHandler` | `handle(ServerWebExchange)` | Ngữ cảnh web đã có request, response và services | Thực thi web handling chain |
| WebFlux programming model | Annotated controllers hoặc `RouterFunction`/`HandlerFunction` | Route, DTO, return values | Logic endpoint ứng dụng |

Dù tên "handler" xuất hiện ở nhiều nơi, API và điểm móc hoàn toàn khác nhau.

## 3. HttpHandler: hợp đồng ở tầng HTTP thấp nhất

### 3.1. Chữ ký cốt lõi

`HttpHandler` thuộc package `org.springframework.http.server.reactive`:

```java
@FunctionalInterface
public interface HttpHandler {
    Mono<Void> handle(
            ServerHttpRequest request,
            ServerHttpResponse response);
}
```

Đây là chữ ký ý nghĩa của interface, không phải source file cần tự định nghĩa.

- `ServerHttpRequest` biểu diễn method, URI, headers, path, body as `Flux<DataBuffer>`, cookies, remote address và các metadata HTTP.
- `ServerHttpResponse` cho phép đặt status/headers/cookies và ghi response body bằng `Publisher<? extends DataBuffer>`.
- `Mono<Void>` biểu diễn **completion signal** của việc xử lý request. Nó có thể complete thành công, complete với error hoặc bị cancel; không chứa DTO Java trực tiếp.

**Không đồng nghĩa void method sync:** `Mono<Void>` không có value nhưng vẫn phải được **subscribe bởi framework/runtime**. Nếu tự gọi handler trong test hoặc custom adapter, phải subscribe/verify completion; nếu không, một chuỗi phản ứng có thể chưa chạy và response chưa được ghi.

### 3.2. Ví dụ HttpHandler đơn giản

```java
HttpHandler handler = (request, response) -> {
    response.setStatusCode(HttpStatus.OK);
    response.getHeaders().setContentType(
            MediaType.TEXT_PLAIN);

    byte[] bytes = "hello-low-level"
            .getBytes(StandardCharsets.UTF_8);
    DataBuffer body = response.bufferFactory().wrap(bytes);

    return response.writeWith(Mono.just(body));
};
```

Đây là snippet với imports `HttpHandler`, `HttpStatus`, `MediaType`, `DataBuffer`, `Mono` và `StandardCharsets`. Trong một app thật, HTTP runtime cần được nối với HttpHandler; đoạn này **không tự tạo server đang lắng nghe port**.

Chú ý:

- Không tự `response.getOutputStream().write(...)` kiểu Servlet sync.
- `writeWith` nhận một `Publisher<DataBuffer>`; framework/container sẽ phối hợp write/complete theo reactive contract.
- Không giữ `DataBuffer` dùng lại giữa requests hoặc đưa cùng buffer cho nhiều consumers.
- Với buffer allocation đặc biệt (pooled buffer), quyền sở hữu và release rất quan trọng; xem phần 16.
- Logic low-level không có sẵn controller mapping, validation DTO, handler adapters hay WebSession services nếu bạn không tự lắp.
- Trong Spring Boot application thông thường, bạn **không cần đăng ký HttpHandler bằng tay** chỉ để làm một REST API.

### 3.3. Tại sao Spring cần một hợp đồng chung?

Các HTTP runtimes có API socket/stream khác nhau. Spring định nghĩa `HttpHandler` để framework xử lý reactive requests mà không buộc annotated controllers phụ thuộc trực tiếp vào Netty channel hoặc Servlet response object. Một adapter phía server chuyển request bytes thành `ServerHttpRequest`/`ServerHttpResponse` rồi gọi `HttpHandler`; Spring framework xử lý phần tiếp theo.

Nhờ lớp này, concept WebFlux ở tầng web không phải đổi khi runtime đổi từ Netty sang một server hỗ trợ khác. Tuy vậy, hành vi cụ thể về HTTP/2, TLS, compression, connection timeouts và native buffers vẫn tùy server/proxy configuration; abstraction không làm mọi runtime có cùng performance profile.

## 4. WebHandler: ngữ cảnh web cao hơn HttpHandler

`WebHandler` nằm ở `org.springframework.web.server`:

```java
@FunctionalInterface
public interface WebHandler {
    Mono<Void> handle(ServerWebExchange exchange);
}
```

Đây là signature đại diện API, không phải một custom interface ứng dụng cần viết.

Khác với `HttpHandler`, WebHandler **không** nhận hai arguments request và response trực tiếp. Nó nhận `ServerWebExchange` chứa cả request/response và web infrastructure.

Ví dụ:

```java
WebHandler handler = exchange -> {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.OK);
    response.getHeaders().setContentType(
            MediaType.TEXT_PLAIN);

    String path = exchange.getRequest()
            .getPath().value();

    DataBuffer bytes = response.bufferFactory()
            .wrap(path.getBytes(StandardCharsets.UTF_8));

    return response.writeWith(Mono.just(bytes));
};
```

Đây là một **WebHandler snippet**; có thể bọc nó bằng `WebHttpHandlerBuilder.webHandler(handler).build()` để tạo HttpHandler thật, như test hoàn chỉnh ở mục 11.

### WebHandler không phải HandlerFunction

Có hai tên dễ gây nhầm:

| Type | Package | Method | Vị trí |
| --- | --- | --- | --- |
| `WebHandler` | `org.springframework.web.server` | `handle(ServerWebExchange) -> Mono<Void>` | Hạ tầng WebFlux |
| `HandlerFunction<T>` | `org.springframework.web.reactive.function.server` | `handle(ServerRequest) -> Mono<ServerResponse>` | WebFlux.fn programming model |
| `HandlerMethod` | Spring web method metadata | Method Java của annotated controller | MVC/WebFlux annotated mapping |
| `HttpHandler` | `org.springframework.http.server.reactive` | `handle(ServerHttpRequest,ServerHttpResponse) -> Mono<Void>` | Tầng HTTP thấp |

`HandlerFunction` tạo `ServerResponse` như một kết quả function-level; `WebHandler` chịu trách nhiệm hoàn thành HTTP exchange (hoặc delegate cho `DispatcherHandler` xử lý results). `ServerRequest` của WebFlux.fn cũng khác với `ServerHttpRequest` low-level và `ServerWebExchange`.

## 5. ServerWebExchange: trung tâm của một HTTP exchange

`ServerWebExchange` thuộc `org.springframework.web.server`, tượng trưng cho một request/response exchange đang diễn ra.

Các API hay dùng:

| Thành phần | Ví dụ | Tác dụng |
| --- | --- | --- |
| Request | `exchange.getRequest()` | Method, URL, headers, cookies, body publisher |
| Response | `exchange.getResponse()` | Status, headers, cookies, write/commit |
| Attributes | `exchange.getAttributes()` | Mutable map dữ liệu gắn với exchange hiện tại |
| Attribute | `exchange.getAttribute("key")` | Lấy attribute có type do caller dự kiến |
| Default attribute | `exchange.getAttributeOrDefault("key", fallback)` | Đặt giá trị fallback khi key không tồn tại |
| Web session | `exchange.getSession()` | `Mono<WebSession>`, reactive session access |
| Principal | `exchange.getPrincipal()` | `Mono<Principal>` hoặc subtype, tùy authentication |
| Locale | `exchange.getLocaleContext()` | LocaleContext theo resolver |
| Form data | `exchange.getFormData()` | `Mono<MultiValueMap<String,String>>` |
| Multipart data | `exchange.getMultipartData()` | `Mono<MultiValueMap<String,Part>>` |
| URL transform | `exchange.transformUrl(String)` | Áp dụng URL transformer nếu cấu hình |
| Conditional request | `exchange.checkNotModified(...)` | Hỗ trợ đánh giá conditional GET theo API |
| Request mutation | `exchange.mutate().request(...).build()` | Tạo exchange wrapper phục vụ filters |

### 5.1. Attributes sống trong phạm vi exchange

```java
exchange.getAttributes().put(
        "requestMarker", "from-filter");

String marker = exchange.getAttributeOrDefault(
        "requestMarker", "absent");
```

**Đây không phải HTTP header, không phải Session và không phải Reactor Context.** Attributes là map nội bộ, tiện để filters trao thông tin với handler trong một request. Đừng đặt secrets vào logs chỉ vì chúng nằm trong attributes. Với logic đi qua nhiều async publishers, exchange có thể được các stages cùng thấy; việc ghi nhiều threads vào mutable map cần xem xét synchronization/happens-before.

Một key khác nhau ở nhiều thư viện có thể collision; dùng constants/namespaced keys như `com.example.trace.correlationId` thay vì `id` hoặc `user` chung chung.

### 5.2. getSession() trả publisher

```java
Mono<WebSession> sessionMono =
        exchange.getSession();
```

Không làm:

```java
// PHẢN VÍ DỤ: có thể block event loop.
WebSession session = exchange.getSession().block();
```

Đúng hơn:

```java
return exchange.getSession()
        .flatMap(session -> {
            String value = session.getAttribute("theme");
            return buildResponse(value);
        });
```

Đây là **snippet** vì `buildResponse` do application triển khai. Session là asynchronous abstraction có thể tương tác session store. Việc gọi `getSession()` **không nhất thiết đồng nghĩa tạo một session cookie mới**; việc start/commit session phụ thuộc cách sử dụng và session manager.

### 5.3. Principal cũng là reactive

```java
return exchange.getPrincipal()
        .map(Principal::getName)
        .defaultIfEmpty("anonymous");
```

`getPrincipal()` trả `Mono<Principal>`; có thể empty khi chưa được xác thực. Đừng dùng `getAttribute("user")` tùy tiện để thay authentication; khi thêm Spring Security, hãy dùng security context/principal từ framework và kiểm tra quyền theo resource/tenant.

**Quan trọng:** Nếu ứng dụng có Spring Security WebFlux, authentication/authorization nằm trong reactive `WebFilter` chain do Spring Security quản lý. Không tin giá trị header `X-User` của client làm identity chỉ vì WebFilter có thể gắn attribute.

### 5.4. Request path và URI không giống servletRequest

Trong WebFlux, `ServerHttpRequest` cung cấp `getPath()` và `getURI()`. Một request path có thể có context path và pathWithinApplication; HTTP URI raw/decoded segments cần xử lý đúng khi routing/security. Không giả định có `HttpServletRequest` sẵn trong app chạy Netty. Các APIs `jakarta.servlet.http.HttpServletRequest` thuộc Servlet stack và không phải portable WebFlux code.

### 5.5. Một exchange, một response lifecycle

`ServerWebExchange` gắn với một HTTP exchange. Nó không được lưu trong singleton field hoặc lưu vô thời hạn trong queue để tái sử dụng cho request khác. Response có giai đoạn **committed**: khi status/headers/body đã được commit, việc đổi status/headers hoặc gửi một error JSON mới có thể không còn khả thi.

Kiểm tra:

```java
if (exchange.getResponse().isCommitted()) {
    return Mono.error(error);
}
```

Đây là snippet với `error` đã có. Việc kiểm tra `isCommitted()` không giải quyết toàn bộ race; đôi khi commit xảy ra ngay sau check. Thiết kế error policies nên xử lý lỗi **trước** khi bắt đầu stream body hoặc chấp nhận chỉ có thể đóng kết nối khi streaming response đã gửi một phần.

## 6. HttpWebHandlerAdapter: cầu nối hai hợp đồng

`HttpWebHandlerAdapter` thuộc `org.springframework.web.server.adapter`. Về vai trò, nó **nhận** low-level request/response từ `HttpHandler` và **thích nghi** thành `ServerWebExchange` để gọi một `WebHandler`.

```text
HttpHandler.handle(ServerHttpRequest, ServerHttpResponse)
                 |
                 v
         HttpWebHandlerAdapter
                 |
                 +-- tạo ServerWebExchange
                 +-- session manager
                 +-- codec config cho form/multipart
                 +-- locale resolver
                 +-- forwarded-header handling nếu cấu hình
                 |
                 v
WebHandler.handle(ServerWebExchange)
                 |
                 v
          Mono<Void> completion
```

Nó còn tham gia các xử lý HTTP lifecycle như response logging, request details, session save, cleanup và lỗi theo integration cụ thể. Ở mức API ứng dụng, **nên để `WebHttpHandlerBuilder` hoặc Spring Boot dựng adapter**, không new thủ công adapter khi chưa hiểu dependencies/chain.

### Vì sao builder giúp giảm lỗi?

Một WebHandler đơn lẻ chưa nói các filters/exceptions/session/codec đã có chưa. `WebHttpHandlerBuilder` gom các cấu hình liên quan để tạo thành HttpHandler hoàn chỉnh, tránh bỏ quên WebFilter hoặc WebExceptionHandler khi tự xây một custom reactive HTTP server adapter.

## 7. WebHttpHandlerBuilder lắp chuỗi xử lý

`org.springframework.web.server.adapter.WebHttpHandlerBuilder` có hai cách khởi tạo đáng nhớ:

```java
HttpHandler fromContext =
        WebHttpHandlerBuilder
                .applicationContext(applicationContext)
                .build();

HttpHandler manual =
        WebHttpHandlerBuilder
                .webHandler(webHandler)
                .filter(myWebFilter)
                .exceptionHandler(myErrorHandler)
                .build();
```

Đây là **snippet**: `applicationContext`, `webHandler`, `myWebFilter` và `myErrorHandler` là các instances đã được cung cấp. Dùng static `applicationContext(...)` khi muốn builder tự dò Spring-managed beans; dùng `webHandler(...)` khi chủ động cung cấp target.

Theo Spring 6.1, builder có thể nhận biết các loại bean/điểm cấu hình như sau:

| Bean hoặc dependency | Số lượng/điều kiện khái niệm | Nhiệm vụ |
| --- | --- | --- |
| `WebHandler` tên `webHandler` | Một target | Trong Boot WebFlux thường là DispatcherHandler |
| `WebFilter` | 0..N, ordered | Chặn/bao quanh request chain |
| `WebExceptionHandler` | 0..N, ordered | Xử lý lỗi từ filter/handler chain |
| `WebSessionManager` tên `webSessionManager` | Optional | Quản lý WebSession |
| `ServerCodecConfigurer` tên `serverCodecConfigurer` | Optional | Readers cho form/multipart và codec settings |
| `LocaleContextResolver` tên `localeContextResolver` | Optional | Locale context |
| `ForwardedHeaderTransformer` tên chuẩn của framework | Optional | Xử lý Forwarded/X-Forwarded-* từ proxy theo trust policy |
| HTTP handler decorators / observation support | Tùy version/config | Decorate tổng thể HttpHandler, observability |

`WebHttpHandlerBuilder.applicationContext(context)` tìm `WebHandler` theo tên hạ tầng chuẩn `webHandler`, thường do WebFlux config khai báo cho `DispatcherHandler`; filters/exception handlers tìm theo type và sắp thứ tự. Trong app Boot chuẩn, **không cần tự dựng bean HttpHandler cùng tên** vì Boot/WebFlux configuration đã lắp pipeline.

### Thứ tự WebFilter và WebExceptionHandler

Về bản chất builder dựng một **target WebHandler**, bọc bằng các `WebFilter` rồi bọc bằng các `WebExceptionHandler`. Điều đó cho phép exception handlers thấy lỗi phát sinh cả khi filter hoặc handler ở bên trong báo error.

```text
WebExceptionHandler A
   |
   +--> WebExceptionHandler B
          |
          +--> WebFilter A
                 |
                 +--> WebFilter B
                        |
                        +--> WebHandler
```

Sơ đồ chỉ minh họa **nesting**. Thứ tự thực tế phụ thuộc `Ordered`/`@Order` và component registration; không suy luận sequence chính xác từ tên class A/B. Xác minh thứ tự/filter chain với tests khi business/security correctness phụ thuộc nó.

### Không đặt custom WebHandler bean tên webHandler vô ý

Trong Boot WebFlux, `DispatcherHandler` thường chính là WebHandler trung tâm với bean name `webHandler`. Nếu bạn tự tạo một bean `WebHandler` tên `webHandler` mà không có chủ đích thay toàn bộ dispatch pipeline, có thể làm mất route discovery cho annotated controllers/functional endpoints hoặc gây bean collision. Với custom API thông thường, dùng `@RestController`, `RouterFunction`, `WebFilter` hoặc WebFlux config thay vì override front controller.

## 8. DispatcherHandler: WebHandler thực hiện web routing

`org.springframework.web.reactive.DispatcherHandler` là front controller của WebFlux. Nó implement `WebHandler` và có thể được phát hiện dưới tên bean `webHandler`.

Ba nhóm extension chính:

1. **HandlerMapping**: tìm handler đầu tiên match request (annotated controller, functional route hoặc handler khác).
2. **HandlerAdapter**: thực thi handler được chọn và trả `HandlerResult`.
3. **HandlerResultHandler**: xử lý kết quả để tạo HTTP response (body, `ResponseEntity`, functional `ServerResponse` hoặc view).

```text
DispatcherHandler.handle(exchange)
            |
            v
HandlerMapping(s): matching handler?
            |
            v
HandlerAdapter: invoke handler -> HandlerResult
            |
            v
HandlerResultHandler: render response
            |
            v
          Mono<Void>
```

### Các loại handler mapping

- `RequestMappingHandlerMapping`: annotated `@RequestMapping`, `@GetMapping`, `@PostMapping`.
- `RouterFunctionMapping`: WebFlux.fn `RouterFunction<ServerResponse>`.
- `SimpleUrlHandlerMapping`: mappings tường minh URL → WebHandler/handler khác.

### Các loại result handlers

- `ResponseEntityResultHandler` cho `ResponseEntity`.
- `ServerResponseResultHandler` cho WebFlux functional `ServerResponse`.
- `ResponseBodyResultHandler` cho `@ResponseBody`/`@RestController` objects.
- `ViewResolutionResultHandler` cho HTML views/model theo configuration.

Mục tiêu là hỗ trợ nhiều programming models phía trên một WebHandler/HttpHandler pipeline thống nhất.

**Không nhầm:** WebFlux `DispatcherHandler` **khác** Spring MVC `DispatcherServlet`. WebFlux có thể chạy trên Servlet containers bằng bridge non-blocking I/O, nhưng nó vẫn dùng **reactive WebFlux processing contracts**, không tự trở thành MVC DispatcherServlet.

## 9. WebFilter: xử lý trước/sau ở tầng exchange

`WebFilter` có chữ ký:

```java
public interface WebFilter {
    Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain);
}
```

Bạn thường trả `chain.filter(exchange)` để tiếp tục. Có thể:
- Đọc request metadata và gắn attributes cho downstream.
- Set headers trước khi response commit.
- Logging/tracing/metrics/correlation hoặc policy có phạm vi rõ.
- Dừng chain sớm bằng một `Mono<Void>` trả response cụ thể.
- Dùng reactive operators `doOnError`, `doFinally`, `contextWrite` theo semantics Reactor.

Ví dụ:

```java
@Component
@Order(-10)
public class MarkerWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {
        exchange.getAttributes().put(
                "demo.marker", "from-web-filter");

        exchange.getResponse().getHeaders()
                .set("X-WebFilter", "active");

        return chain.filter(exchange);
    }
}
```

Đây là **snippet class đầy đủ về logic**; cần imports `Component`, `Order`, `WebFilter`, `ServerWebExchange`, `WebFilterChain`, `Mono`. Phiên bản runnable tại mục 11 có scope `/api/exchange/**` rõ ràng.

### Gọi chain đúng một lần

```java
return chain.filter(exchange);
```

Không làm:

```java
// PHẢN VÍ DỤ: multiple subscriptions có thể thực hiện xử lý lần hai.
chain.filter(exchange).subscribe();
return chain.filter(exchange);
```

Spring/HTTP runtime subscribe outer Mono. Tự `subscribe()` có thể kích hoạt second request processing, mất context/cancellation, race response và data corruption. Cũng không được `block()` trong WebFilter event loop.

### Chặn request từ filter

Một filter có thể trả 403/429 mà không gọi downstream handler:

```java
if (blockedByServerSidePolicy(exchange)) {
    exchange.getResponse().setStatusCode(
            HttpStatus.TOO_MANY_REQUESTS);
    return exchange.getResponse().setComplete();
}
return chain.filter(exchange);
```

Đây là snippet với `blockedByServerSidePolicy` cần implement ở business/security policy. **Không dùng header do client tự khai để xác nhận user là admin**. Authn/authz production nên đặt trong Spring Security reactive security chain, kết hợp service-level permission checks.

### WebFilter so với GatewayFilter, HandlerFilterFunction và Servlet Filter

| API | Thuộc stack/phạm vi | Điểm khác |
| --- | --- | --- |
| `WebFilter` | WebFlux WebHandler chain | Trước/bao quanh DispatcherHandler hoặc target WebHandler |
| `HandlerFilterFunction` | WebFlux.fn router/handler | Chỉ bọc functional router/handler tương ứng |
| `GlobalFilter`/`GatewayFilter` | Spring Cloud Gateway | Là abstraction riêng của Gateway, không đồng nghĩa mọi WebFlux app |
| `jakarta.servlet.Filter` | Servlet stack | Filter chain ở Servlet container |
| `HandlerInterceptor` | Spring MVC | Dựa trên MVC HandlerMapping lifecycle |

Một WebFlux app có thể chạy trên Servlet container, nhưng WebFilter vẫn là reactive API ở WebHandler chain; nó **không tự giống** Servlet Filter dùng `doFilter`.

## 10. WebExceptionHandler: chặn lỗi trong reactive chain

`WebExceptionHandler` thuộc `org.springframework.web.server`:

```java
public interface WebExceptionHandler {
    Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable ex);
}
```

Nó xử lý error signal ở tầng WebHandler chain. Nếu **không xử lý**, trả `Mono.error(ex)` để error handlers tiếp theo có cơ hội xử lý hoặc runtime kết thúc với lỗi. Nếu xử lý thành công, đặt status/headers/body thích hợp và trả một Mono complete.

Ví dụ cho custom exception nội bộ:

```java
WebExceptionHandler handler = (exchange, ex) -> {
    if (!(ex instanceof IllegalArgumentException)) {
        return Mono.error(ex);
    }
    if (exchange.getResponse().isCommitted()) {
        return Mono.error(ex);
    }

    exchange.getResponse().setStatusCode(
            HttpStatus.BAD_REQUEST);
    exchange.getResponse().getHeaders().setContentType(
            MediaType.TEXT_PLAIN);

    byte[] bytes = "bad request"
            .getBytes(StandardCharsets.UTF_8);
    DataBuffer body = exchange.getResponse()
            .bufferFactory().wrap(bytes);
    return exchange.getResponse()
            .writeWith(Mono.just(body));
};
```

Đây là **snippet handler**; không thêm nó bừa vào production. `IllegalArgumentException` có thể đại diện bug programmer, không chỉ bad user input; nên dùng domain exception cụ thể, status contract/`ProblemDetail` ổn định và security-safe logging. Khi response đã commit, không được cố đổi 200 thành 400 và viết JSON mới.

### WebExceptionHandler khác @ExceptionHandler

`@ExceptionHandler`/`@RestControllerAdvice` thường thuộc cơ chế xử lý exception trong annotated controller adapter. `WebExceptionHandler` nằm **ngoài** ở WebHandler chain và có thể bắt lỗi từ filter hoặc handler không được xử lý ở tầng bên trong. Nó không có nghĩa mọi exception bắt buộc phải đi qua global handler: exceptions có thể đã được xử lý bởi annotated advice, result adapter hoặc WebFlux infrastructure trước đó.

Spring Boot còn có error web exception handlers được auto-configured để tạo responses cho các lỗi chưa được ứng dụng xử lý. Nếu thêm custom WebExceptionHandler, cần xem `@Order`/status/error body để không đè/che lỗi framework hoặc leak thông tin.

## 11. Dự án Spring Boot 3.3.5 runnable với ServerWebExchange

Project demo minh họa:

1. WebFlux chạy trên **Reactor Netty** qua `spring-boot-starter-webflux`.
2. `@RestController` dùng `ServerWebExchange` để đọc request path/method và attributes.
3. Một `WebFilter` đánh dấu exchange và thêm response header.
4. Một endpoint trả `Mono.delay` để cho thấy reactive async completion (không dùng `Thread.sleep`).
5. Một endpoint tạo `ResponseStatusException` để thử error status.
6. `WebTestClient` test trên **embedded HTTP server thật** với port ngẫu nhiên.
7. Một test low-level tạo `HttpHandler` từ `WebHandler` bằng `WebHttpHandlerBuilder`, không mở socket thật.

Không có database, OAuth, Spring Security hay logging PII; chương trình chỉ để học abstraction/lifecycle.

### 11.1. Cấu trúc project

```text
webflux-infra-demo/
├── pom.xml
└── src/
    ├── main/java/demo/webflux/
    │   ├── WebFluxInfraApplication.java
    │   ├── ExchangeController.java
    │   └── MarkerWebFilter.java
    └── test/java/demo/webflux/
        ├── ExchangeControllerTest.java
        └── LowLevelHttpHandlerTest.java
```

### 11.2. pom.xml

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
    <groupId>demo</groupId>
    <artifactId>webflux-infra-demo</artifactId>
    <version>1.0.0</version>
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

Không thêm `spring-boot-starter-web`, để Boot chọn reactive web application type từ WebFlux starter. Nếu thêm cả Servlet MVC và WebFlux starters cùng lúc, Boot thường ưu tiên Servlet MVC application type trừ khi cấu hình khác; **không dùng classpath hỗn hợp làm bằng chứng WebFlux đang active**.

### 11.3. Main application

**`src/main/java/demo/webflux/WebFluxInfraApplication.java`**

```java
package demo.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebFluxInfraApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxInfraApplication.class, args);
    }
}
```

### 11.4. WebFilter đặt marker vào ServerWebExchange

**`src/main/java/demo/webflux/MarkerWebFilter.java`**

```java
package demo.webflux;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-10)
public class MarkerWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String path = exchange.getRequest()
                .getPath().value();

        if (!path.startsWith("/api/exchange/")) {
            return chain.filter(exchange);
        }

        exchange.getAttributes().put(
                "demo.marker", "from-web-filter");
        exchange.getResponse().getHeaders()
                .set("X-WebFilter", "active");

        return chain.filter(exchange);
    }
}
```

**Giải thích:**

- `@Component` đăng ký WebFilter ở Spring context; framework có thể đưa nó vào WebHandler chain.
- `@Order(-10)` cho thứ tự tương đối so với các filters khác, không có nghĩa nó an toàn khi chặn/mở security một mình.
- `exchange.getAttributes()` chia sẻ dữ liệu trong một exchange, không gửi marker như request header lên server khác.
- `X-WebFilter` được ghi trước khi response commit.
- Filter chỉ tác động tới đường bắt đầu `/api/exchange/` và luôn gọi downstream chain **một lần**.
- Không có `block()` hoặc `subscribe()` thủ công.
- Nếu body streaming đã commit, thay đổi headers sau đó không bảo đảm có hiệu lực; ví dụ gắn trước để dễ đoán.

### 11.5. REST controller nhận ServerWebExchange

**`src/main/java/demo/webflux/ExchangeController.java`**

```java
package demo.webflux;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/exchange")
public class ExchangeController {

    @GetMapping("/hello/{name}")
    public Mono<ResponseEntity<Map<String, String>>> hello(
            @PathVariable String name,
            ServerWebExchange exchange) {

        String marker = exchange.getAttributeOrDefault(
                "demo.marker", "missing");
        String method = exchange.getRequest()
                .getMethod().name();

        Map<String, String> body = Map.of(
                "name", name,
                "marker", marker,
                "method", method);

        return Mono.just(ResponseEntity.ok(body));
    }

    @GetMapping("/async")
    public Mono<Map<String, String>> async() {
        return Mono.delay(Duration.ofMillis(20))
                .map(tick -> Map.of("state", "ready"));
    }

    @GetMapping("/fail")
    public Mono<String> fail() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "invalid request"));
    }
}
```

**Những gì code chứng minh:**

- `@RestController` chỉ là programming model ở trên WebFlux `DispatcherHandler`; ở hạ tầng vẫn có HttpHandler, WebHandler và exchange.
- `ServerWebExchange` được framework inject vào controller argument mà không cần tự tạo.
- Filter đặt `demo.marker` vào map attributes, controller đọc lại marker đó.
- `getRequest().getMethod()` lấy `HttpMethod` của request; endpoint là GET nên method mong đợi là "GET".
- `Mono<ResponseEntity<Map<...>>>` là reactive single-value response; Jackson serialize Map thành JSON.
- `Mono.delay(...)` phát signal sau một khoảng thời gian mà không giữ một thread ngủ chờ riêng cho mỗi subscription theo kiểu `Thread.sleep`. Tuy nhiên bản demo không phải benchmark và runtime vẫn cần scheduler/event-loop threads.
- `Mono.error(new ResponseStatusException(...))` dẫn tới HTTP 400 qua WebFlux error machinery. Response body/error details có thể thay đổi theo Boot configuration; test chỉ assert status để tránh phụ thuộc định dạng mặc định.
- Ứng dụng không truy cập blocking JDBC, không tự subscribe, không giữ `ServerWebExchange` trong singleton field.

### 11.6. Chạy bằng Maven và curl

Từ thư mục `webflux-infra-demo` với Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Đây là hai lệnh **chạy tuần tự**. Khi Boot khởi động trên port mặc định 8080:

```bash
curl -i 'http://localhost:8080/api/exchange/hello/Lan'
```

Các phần chính của response:

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-WebFilter: active

{"name":"Lan","marker":"from-web-filter","method":"GET"}
```

Thứ tự các keys JSON do serializer quyết định, không dùng string equality trên toàn JSON nếu không cần.

```bash
curl -i 'http://localhost:8080/api/exchange/async'
```

Dự kiến status 200, header `X-WebFilter: active` và JSON:

```json
{"state":"ready"}
```

```bash
curl -i 'http://localhost:8080/api/exchange/fail'
```

Dự kiến HTTP status **400 Bad Request**. Header từ filter có thể vẫn được gửi nếu response chưa commit; error body tùy cấu hình `ErrorWebExceptionHandler` và content negotiation.

Với `curl -i` không có caching/retry mặc định tương đương browser. Nếu chạy sau proxy, quan sát thêm forwarded headers, idle timeout, `Content-Length`/chunked transfer và Content-Encoding; các giá trị ấy phụ thuộc runtime.

## 12. Test WebTestClient: đường thực qua HttpHandler tới Controller

**`src/test/java/demo/webflux/ExchangeControllerTest.java`**

```java
package demo.webflux;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ExchangeControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void exchangeAttributesFlowFromWebFilterToHandler() {
        client.get()
              .uri("/api/exchange/hello/Lan")
              .exchange()
              .expectStatus().isOk()
              .expectHeader().valueEquals(
                      "X-WebFilter", "active")
              .expectBody()
              .jsonPath("$.name").isEqualTo("Lan")
              .jsonPath("$.marker")
                  .isEqualTo("from-web-filter")
              .jsonPath("$.method").isEqualTo("GET");
    }

    @Test
    void asynchronousPublisherCompletesWithBody() {
        client.get()
              .uri("/api/exchange/async")
              .exchange()
              .expectStatus().isOk()
              .expectHeader().valueEquals(
                      "X-WebFilter", "active")
              .expectBody()
              .jsonPath("$.state").isEqualTo("ready");
    }

    @Test
    void errorSignalBecomesBadRequestStatus() {
        client.get()
              .uri("/api/exchange/fail")
              .exchange()
              .expectStatus().isBadRequest();
    }
}
```

Ba tests này khởi động embedded HTTP server trên một port ngẫu nhiên. `WebTestClient` gửi request đến server thật, nên đi qua reactive server adapter và toàn bộ WebFlux infrastructure. Điều đó **khác** với gọi trực tiếp controller method mà không có dispatcher, filter hoặc serializer.

Không khẳng định body errors như `{"message":"invalid request"}` sẽ luôn xuất hiện: trong Boot default và tùy `server.error.include-message`, error response details có thể thay đổi. Tập trung kiểm tra status và behavior đã khai báo.

## 13. Test HttpHandler thấp hơn DispatcherHandler, không cần mở port

Để phân biệt `HttpHandler` và `WebHandler`, ta xây một pipeline low-level trong test độc lập: không có `@RestController`, không có `DispatcherHandler`, nhưng vẫn có `ServerWebExchange`/`WebFilter` vì `WebHttpHandlerBuilder` tạo adapter.

**`src/test/java/demo/webflux/LowLevelHttpHandlerTest.java`**

```java
package demo.webflux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowLevelHttpHandlerTest {

    @Test
    void adapterBuildsExchangeAndRunsWebFilter() {
        WebHandler target = exchange -> {
            String marker = exchange.getAttributeOrDefault(
                    "demo.marker", "missing");

            exchange.getResponse().setStatusCode(HttpStatus.OK);
            exchange.getResponse().getHeaders().setContentType(
                    MediaType.TEXT_PLAIN);

            DataBuffer buffer = exchange.getResponse()
                    .bufferFactory()
                    .wrap(marker.getBytes(StandardCharsets.UTF_8));

            return exchange.getResponse()
                    .writeWith(Mono.just(buffer));
        };

        WebFilter markerFilter = (exchange, chain) -> {
            exchange.getAttributes().put(
                    "demo.marker", "seen-by-web-handler");
            return chain.filter(exchange);
        };

        HttpHandler handler = WebHttpHandlerBuilder
                .webHandler(target)
                .filter(markerFilter)
                .build();

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/raw/demo").build();
        MockServerHttpResponse response =
                new MockServerHttpResponse();

        handler.handle(request, response)
                .block(Duration.ofSeconds(2));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("seen-by-web-handler",
                response.getBodyAsString()
                        .block(Duration.ofSeconds(2)));
    }
}
```

**Điều bài test chứng minh:**

1. Test tự định nghĩa **WebHandler** chứ không định nghĩa annotated controller.
2. Một **WebFilter** thêm attribute `demo.marker` vào exchange.
3. `WebHttpHandlerBuilder.webHandler(target).filter(markerFilter).build()` tạo **HttpHandler** từ các lớp cấp web.
4. Test gọi `HttpHandler.handle(MockServerHttpRequest, MockServerHttpResponse)` — tầng thấp nhất sau HTTP server adapter.
5. `HttpWebHandlerAdapter` bên trong tạo exchange, WebFilter chạy trước target.
6. `getBodyAsString()` kiểm tra bytes được viết thành HTTP response.
7. `block()` **chỉ nằm trong test** để chờ completion; không dùng `block()` trong server handler/WebFilter chạy trên event loop.

Nếu chạy Maven với cả `ExchangeControllerTest` và `LowLevelHttpHandlerTest`, project có **4 test methods**. Khi tất cả pass, Maven exit code `0`; summary thông thường sẽ có tổng `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`. Bài này cung cấp mã được rà soát tĩnh theo các APIs Spring Boot 3.3.5; không khẳng định đã chạy Maven trong quá trình biên soạn trên Google Sheets.

### Khi nào tự build HttpHandler là hợp lý?

- Khi tích hợp Spring WebFlux với một custom HTTP runtime/adapter.
- Khi unit test một WebHandler/filter chain thấp hơn DispatcherHandler.
- Khi xây reusable reactive HTTP infrastructure module cần kiểm soát session/codec/exception chains.
- Khi quan sát/đo đạc toàn bộ HTTP exchange ở lớp low-level.

**Không** phải lựa chọn mặc định để thêm một endpoint REST: `@RestController` hoặc WebFlux.fn `RouterFunction` dễ maintain, có JSON codecs, validation/route tooling và error handling đầy đủ hơn.

## 14. ServerWebExchange trong filters: mutate request đúng cách

Các exchange wrappers hỗ trợ thay đổi request metadata theo functional style:

```java
ServerWebExchange updated = exchange.mutate()
        .request(builder -> builder.header(
                "X-Internal-Trace", "trace-generated-server-side"))
        .build();

return chain.filter(updated);
```

Đây là snippet đặt trong `WebFilter.filter(...)`. Chú ý:

- Header `X-Internal-Trace` ở đây chỉ là **ví dụ**; auth/identity không được lấy từ header client tùy ý.
- Khi tạo request wrapper, phải truyền **updated exchange** cho chain, không bỏ qua biến updated.
- Mutation không làm request bytes được giải mã/re-encode tự động. Với body transformations, cần `ServerHttpRequestDecorator`, `DataBuffer`, codecs và cancellation/backpressure handling đúng.
- Không tự sửa URI/Host/Forwarded headers từ input user để đổi route sang tài nguyên admin.
- Khi framework đã cung cấp Spring Security/tracing gateway integration, ưu tiên công cụ chính thức hơn một filter tự viết phức tạp.

### Attributes hay Reactor Context?

`exchange.getAttributes()` là dữ liệu gắn với **một exchange object**. `reactor.util.context.Context` là context đi cùng reactive subscription, dùng cho cross-cutting data như tracing/security/reactive transaction.

```java
return chain.filter(exchange)
        .contextWrite(context ->
                context.put("auditTag", "from-webfilter"));
```

Snippet trên thêm `auditTag` vào Reactor Context cho các publishers **upstream của contextWrite theo quy tắc Reactor Context propagation**. Nó không tự thay đổi `exchange.getAttributes()`, và `ThreadLocal` cũng không tự đồng bộ với Reactor Context. Cách truy cập Context bên trong reactive chain thường dùng `Mono.deferContextual(...)`; không nên dùng `Thread.currentThread()` làm request identity.

**Đừng nhầm ba nơi:**
- Request headers: dữ liệu HTTP client/proxy gửi (có thể chưa đáng tin).
- Exchange attributes: mutable map của web exchange.
- Reactor Context: subscription-scoped context lan qua reactive operators, dùng bởi nhiều infrastructure integrations.

## 15. Form/multipart data, body publisher và single subscription

`ServerWebExchange` có `getFormData()` và `getMultipartData()`, trả `Mono<MultiValueMap<...>>`. Nó cung cấp parsed/cached view theo request content type, dựa vào cấu hình readers. Nhưng raw `ServerHttpRequest.getBody()` là `Flux<DataBuffer>`, thường là **one-shot stream**.

### 15.1. Không đọc body hai lần tùy tiện

Phản ví dụ:

```java
// PHẢN VÍ DỤ: subscribe raw body để log rồi downstream lại đọc.
exchange.getRequest().getBody()
        .subscribe(buffer -> logBuffer(buffer));
return chain.filter(exchange);
```

Đoạn này có thể tiêu thụ stream trước body decoder của controller, gây lỗi unreadable/empty body, mất cancellation context và rò rỉ DataBuffer nếu không release. Không log raw body chứa passwords/tokens/PII.

Muốn observability cho request body, nên dùng framework tooling được thiết kế cho streaming, hoặc viết `ServerHttpRequestDecorator` có giới hạn tối đa bytes, buffering/ref-count và backpressure, test content-length/transfer-encoding, slow clients, errors và cancel. Đừng mặc định "WebFilter luôn có thể đọc body mà không ảnh hưởng controller".

### 15.2. Đừng block khi đọc multipart/form data

```java
return exchange.getMultipartData()
        .flatMap(parts -> {
            // validate metadata/Part, enforce limits
            return processParts(parts);
        });
```

Đây là snippet vì `processParts` do application tự định nghĩa. Multipart body có thể lớn; phải giới hạn upload size, số parts, file names/path traversal, content type, temp storage và virus/malware scanning theo threat model. Reactive multipart APIs không tự làm file an toàn.

### 15.3. Streaming response và cancellation

Nếu client ngắt kết nối khi stream lớn, reactive pipeline có thể nhận **cancel signal**. Nguồn dữ liệu phải giải phóng subscriptions, file handles, Netty buffers và remote connections đúng. Với `Flux<DataBuffer>`, backpressure thể hiện demand nhưng chỉ hiệu quả khi source thực sự tôn trọng demand; một `Flux.create` đẩy vô hạn lên bộ nhớ có thể OOM dù giao diện là reactive.

## 16. Response commit, headers và beforeCommit

`ServerHttpResponse` có trạng thái commit. Với response thông thường, status/headers cần được đặt **trước** khi body bắt đầu ghi. Để gắn logic cuối trước commit, response hỗ trợ `beforeCommit(Supplier<? extends Mono<Void>>)`:

```java
exchange.getResponse().beforeCommit(() -> {
    exchange.getResponse().getHeaders()
            .add("X-Before-Commit", "yes");
    return Mono.empty();
});

return chain.filter(exchange);
```

Đây là snippet trong WebFilter. Callback phải trả một **deferred Mono** nếu có async side effect; callback chỉ có tác dụng trước commit, không cho phép chỉnh status/header sau khi response đã được gửi. Tránh blocking work trong callback; response commit phase không phải nơi gọi DB dài hoặc remote API sync.

### Lỗi sau khi đã ghi một phần body

Giả sử stream đã gửi một nửa CSV rồi publisher báo error. `WebExceptionHandler` có thể thấy error signal, nhưng response có thể đã commit 200 và gửi bytes. Nó không thể bảo đảm chuyển thành 500 JSON đúng contract; thường chỉ có thể kết thúc/đóng connection hoặc xử lý theo protocol streaming (SSE event riêng nếu còn write được).

Với endpoint tải file, SSE hoặc chunked response:
- Đặt headers/status/authorization trước body.
- Điều khiển error/cancellation, body buffers và timeout.
- Nếu có content checksum, trả metadata/streaming protocol được thiết kế rõ.
- Không hứa với client rằng mọi lỗi đều được map thành JSON 500 sau khi streaming bắt đầu.

## 17. WebFlux backpressure và non-blocking: những gì thật sự được bảo đảm

`HttpHandler.handle(...)` trả `Mono<Void>` và body request/response dùng Reactive Streams. Điều này **cho phép** runtime/pipeline thực hiện I/O không chặn và truyền backpressure signals. Nhưng không thể kết luận mọi code Spring WebFlux đều non-blocking chỉ vì signatures dùng Mono/Flux.

Phản ví dụ:

```java
@GetMapping("/broken")
public Mono<String> broken() {
    String result = blockingJdbcRepository.load();
    return Mono.just(result);
}
```

Đây là snippet với repository chưa định nghĩa, cố ý minh họa lỗi. Lời gọi JDBC đồng bộ chạy **ngay khi controller method được gọi** và có thể block event loop.

Một cách offload có giới hạn nếu tạm thời phải dùng thư viện blocking:

```java
return Mono.fromCallable(
            () -> blockingJdbcRepository.load())
        .subscribeOn(Schedulers.boundedElastic());
```

Đây cũng là snippet, dùng `reactor.core.scheduler.Schedulers`. Nó **chuyển nơi blocking work diễn ra** sang boundedElastic worker, **không biến JDBC thành non-blocking**. Cần cấu hình connection pool, statement timeouts, bounds, transaction context và đo throughput; tránh làm quá nhiều blocking jobs/hàng đợi gây latency.

### Những resource vẫn tồn tại

Khi một request đang chờ reactive signal:
- Socket/connection và HTTP exchange state vẫn tồn tại.
- Buffers, codec objects, subscriptions và timeouts vẫn có thể chiếm memory.
- Scheduler/event-loop threads vẫn chạy callbacks/serialization.
- DB/network pool bottlenecks không tự biến mất.

Reactive giúp xử lý nhiều pending operations với ít blocked threads **khi toàn pipeline non-blocking và backpressure-aware**. Không tự động tăng throughput cho CPU-bound work hoặc downstream chậm.

## 18. Spring Security, CORS, forwarded headers và privacy

### 18.1. Security ở đâu?

Trong Spring Security WebFlux, security chain nằm trong **WebFilter chain**. Một SecurityWebFilterChain có các rules về authentication, authorization, CSRF, CORS và exception handling. Nó bảo vệ request **trước annotated controller/WebFlux.fn**, không phụ thuộc route style.

Không dùng:

```java
// PHẢN VÍ DỤ: client tự đặt header.
String user = exchange.getRequest()
        .getHeaders().getFirst("X-User");
exchange.getAttributes().put("authenticatedUser", user);
```

để thay Spring Security. Header này do client kiểm soát và không chứng minh danh tính. Khi cần principal, truy cập `exchange.getPrincipal()` hoặc reactive SecurityContext theo framework và áp quyền resource/tenant ở service.

### 18.2. CORS ở WebFlux layer

CORS ảnh hưởng browser. Có thể xử lý qua WebFlux CORS configuration hoặc `CorsWebFilter`, và khi thêm Security cần thứ tự integration đúng. Không tự viết WebFilter luôn trả `Access-Control-Allow-Origin: *` cho endpoint có credentials. CORS **không phải authentication**; command-line HTTP clients có thể gửi request dù browser cross-origin policy không cho đọc response.

### 18.3. Forwarded/X-Forwarded-* headers

`ServerWebExchange` sử dụng request metadata đã qua server/proxy adapter. Nếu hệ thống đứng sau reverse proxy, public scheme/host/prefix có thể đến từ `Forwarded` hoặc `X-Forwarded-*`; `ForwardedHeaderTransformer` cần trust model rõ. Proxy tin cậy phải **loại bỏ headers giả mạo từ Internet rồi đặt giá trị chuẩn**. Nếu tin client-supplied `X-Forwarded-Host`, app có thể sinh URL/redirect sai, dẫn tới phishing, open redirects hoặc cache poisoning.

### 18.4. Session và cache

`WebSession` có thể mang state user nhưng không biến các HTTP responses thành private automatically. Endpoint cá nhân hóa cần `Cache-Control: private` hoặc `no-store` theo mức nhạy cảm và policy CDN. Không lấy `exchange.getSession()` rồi mặc định shared proxies sẽ phân biệt users đúng.

## 19. Logging, observability và debugging

Một pipeline reactive có thể chuyển execution thread giữa nhiều scheduler; vì vậy gắn correlation ID bằng plain `ThreadLocal` không đảm bảo giữ được xuyên async operators.

Nên dùng:
- Spring/Micrometer observation integration và tracing context propagation được framework hỗ trợ.
- Reactor Context cho dữ liệu subscription-scoped.
- Exchange attributes cho thông tin ngắn hạn request-scoped.
- Sanitized logs: method, route template, status, latency, correlation ID; **không log tokens, raw cookies hoặc request body nhạy cảm**.
- Ordered WebFilters và error handlers với trách nhiệm riêng, không để logging filter nuốt mọi exception.

### Các bước debug

1. **Xác nhận stack thật**: ứng dụng Spring Boot có WebFlux active, không phải MVC `DispatcherServlet` vô tình được chọn do classpath.
2. Xác nhận `WebHandler` bean tên `webHandler` và `DispatcherHandler` được WebFlux configuration phát hiện; không override nhầm.
3. Quan sát từ `HttpHandler` đến `WebHttpHandlerBuilder`/`HttpWebHandlerAdapter` rồi WebFilters; test low-level nếu cần.
4. Kiểm tra `HandlerMapping` nào match route và `HandlerResultHandler` nào ghi response.
5. Với header thiếu, kiểm tra có đặt trước commit không, filter có gọi chain không.
6. Với 404, xem path mapping/controller/functional predicates trước khi nghi Netty.
7. Với body rỗng, xem ai subscribe/consume request body trước converter.
8. Với timeout, xem downstream client/database, event-loop blocking, queue và network idle timeout.
9. Với 500, xác định exception đã xảy ra trước hay sau response commit.
10. Với memory leak, kiểm tra `DataBuffer` pooling, body caching và cancellation cleanup.
11. Với mất principal/MDC, xem SecurityContext/Reactor Context và scheduler boundaries.
12. Với CORS errors, test OPTIONS/preflight Origin thực qua security filter chain.

## 20. Các lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Import `web.servlet.function.ServerRequest` trong WebFlux | Nhầm MVC.fn với WebFlux.fn | Dùng `web.reactive.function.server` |
| Tạo WebHandler rồi nghĩ có routing/JSON tự động | WebHandler chỉ nhận exchange | Dùng DispatcherHandler/config hoặc tự xử lý toàn pipeline |
| Override bean `webHandler` làm annotated controllers mất | Đè front controller WebFlux | Không override trừ khi đang xây low-level infrastructure |
| Thêm starter-web và starter-webflux, controller chạy MVC | Boot ưu tiên MVC app type trong case thông thường | Định rõ stack, dependencies, integration tests |
| `Mono<Void>` không làm gì trong custom test | Chưa subscribe/chờ completion | WebTestClient hoặc `block` trong test, không ở event loop |
| Filter chạy hai lần | Gọi `subscribe()` rồi lại return chain | Trả chain một lần, không subscribe thủ công |
| Lỗi `block()` trên event-loop | Gọi blocking API trong reactive callback | Non-blocking client hoặc bounded offload |
| Body request trống | WebFilter đọc raw body trước decoder | Không consume raw body hai lần, decorate/cache đúng |
| JSON malformed bị trả 500 sai schema | Error handler/codecs/advice khác mong đợi | Test status/content-type, consistent errors |
| Không set được status trong WebExceptionHandler | Response đã committed | Xử lý errors sớm, streaming-specific policy |
| Response header mất | Gắn sau khi body đã commit | Đặt trước hoặc `beforeCommit` |
| Filter order gây security bypass | Trust boundary/order không rõ | Spring Security reactive chain + test |
| ThreadLocal mất traceId | Reactive scheduler changes | Reactor Context/context propagation |
| Multipart OOM | Buffer hết body vào memory không giới hạn | Max in-memory size, streaming, upload limits |
| DataBuffer memory leak | Pooled buffers không được release khi filter thao tác | Dùng framework operators/`DataBufferUtils.release` đúng ownership |
| `getSession().block()` gây stall | Blocking event loop | flatMap/reactive composition |
| `ServerWebExchange` bị lưu trong singleton | Mix nhiều requests, giữ resources lâu | Chỉ giữ dữ liệu cần thiết và lifecycle ngắn |
| Wrong Host ở URLs | Untrusted forwarded headers/proxy config | Sanitize tại ingress, canonical base |
| SSE chậm, memory tăng | Producer không tôn trọng demand/cancel | Backpressure, buffer bounds, proper disposal |
| HTTP response khác giữa Netty/Servlet bridge | Server-specific behavior / proxy | Integration tests theo runtime thực |

## 21. Lựa chọn mức API nào khi xây ứng dụng?

| Nhu cầu | Điểm tích hợp nên chọn |
| --- | --- |
| CRUD REST JSON bình thường | `@RestController` hoặc WebFlux.fn `RouterFunction` |
| Áp dụng policy chung trước routes | `WebFilter` hoặc Spring Security WebFilter chain |
| Map lỗi HTTP chưa được xử lý | `WebExceptionHandler` hoặc Boot/WebFlux error mechanisms phù hợp |
| Dùng chung state request qua filters/controllers | `ServerWebExchange.getAttributes()` với keys rõ |
| Đọc body/multipart ở business logic | Annotated binding/WebFlux.fn readers/codecs; tránh raw buffer khi không cần |
| Quan sát toàn bộ handler pipeline | Observability / WebFilter / HttpHandler decorators khi cần |
| Thay đổi implementation HTTP runtime | Adapter integration dựa trên `HttpHandler` |
| Xây custom routing engine | `WebHandler`/`HandlerMapping` khi thực sự cần |
| Test full HTTP chain | `WebTestClient` bind to server/context |
| Test WebHandler/HttpHandler lower layer | `MockServerHttpRequest` + `MockServerHttpResponse` + WebHttpHandlerBuilder |

**Nguyên tắc:** chọn abstraction ở mức cao nhất đủ đáp ứng yêu cầu. Controller/router APIs ít gây lỗi low-level body handling hơn. Chỉ tự xây HttpHandler khi có nhu cầu hạ tầng thật.

## 22. Câu hỏi tự kiểm tra

1. `HttpHandler.handle` nhận những parameters nào và trả kiểu gì?
2. `WebHandler.handle` khác `HttpHandler.handle` ở đầu vào thế nào?
3. `ServerWebExchange` cung cấp gì ngoài `ServerHttpRequest` và `ServerHttpResponse`?
4. `WebHttpHandlerBuilder` làm hai việc chính nào?
5. `HttpWebHandlerAdapter` tạo abstraction nào để WebHandler xử lý?
6. `DispatcherHandler` có phải một implementation của WebHandler không?
7. Phân biệt `HandlerMapping`, `HandlerAdapter` và `HandlerResultHandler`.
8. Một `WebFilter` có được tự gọi `subscribe()` để chạy chain không?
9. Vì sao `WebExceptionHandler` có thể không đổi được HTTP status khi lỗi xảy ra giữa response stream?
10. `exchange.getAttributes()` có tương đương Reactor Context hoặc WebSession không?
11. Vì sao `exchange.getSession()` và `exchange.getPrincipal()` trả Mono?
12. `WebMvc.fn HandlerFunction` có giống `WebHandler` của WebFlux không?
13. Nếu ứng dụng có Spring Boot WebFlux với annotated controllers, có nên tự tạo `WebHandler` bean tên `webHandler` không?
14. `Mono<Void>` không phát value nhưng đóng vai trò gì ở response completion?
15. Nếu WebFilter đọc raw `getBody()` rồi controller cũng đọc JSON, rủi ro gì?
16. `DataBuffer` được pool thì vì sao reference counting/release quan trọng?
17. Khi nào dùng `beforeCommit`?
18. Thêm `subscribeOn(Schedulers.boundedElastic())` quanh JDBC có khiến JDBC non-blocking không?
19. Tại sao WebFilter nên không thay Spring Security cho authentication/authorization?
20. Làm sao test low-level HttpHandler mà không cần khởi động Netty server?
21. Tại sao `ForwardedHeaderTransformer` cần trust boundary?
22. Khi có query/form/multipart data, tại sao phải kiểm soát max size và cancellation?
23. Nếu filter ghi error response, có phải luôn tiếp tục `chain.filter(exchange)` không?
24. Nếu endpoint trả `Mono.error`, response error body chắc chắn giống giữa mọi Boot configurations không?

**Gợi ý:** (1) ServerHttpRequest, ServerHttpResponse → Mono<Void>. (2) Exchange bao request, response và web services. (3) Attributes, session, principal, form/multipart, locale, conditionals. (4) Lắp filter/error chain và adapt thành HttpHandler. (5) ServerWebExchange. (6) Có. (7) Chọn handler, thực thi, render result. (8) Không. (9) Response đã commit. (10) Không. (11) Reactive asynchronous services. (12) Không, package và abstraction khác. (13) Tránh override front controller. (14) Completion/error/cancel signals. (15) Body one-shot bị tiêu thụ hoặc leak. (16) Pooled memory phải trả lại. (17) Logic header ngay trước commit. (18) Không, chỉ chuyển nơi block. (19) Không đủ trust/security policy. (20) WebHttpHandlerBuilder + mock HTTP request/response. (21) Client có thể giả headers. (22) Chống OOM/slow clients. (23) Nếu đã short-circuit thì không gọi tiếp. (24) Không, tùy error handlers và Boot config.

## 23. Tài liệu tham khảo chính thức

- [Spring Framework 6.1 — WebFlux: Reactive Core / HttpHandler & WebHandler](https://docs.spring.io/spring-framework/reference/6.1/web/webflux/reactive-spring.html)
- [Spring Framework 6.1 — DispatcherHandler](https://docs.spring.io/spring-framework/reference/6.1/web/webflux/dispatcher-handler.html)
- [Spring Framework 6.1 — WebHttpHandlerBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/adapter/WebHttpHandlerBuilder.html)
- [Spring Framework 6.1 — HttpWebHandlerAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/adapter/HttpWebHandlerAdapter.html)
- [Spring Framework 6.1 — ServerWebExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/ServerWebExchange.html)
- [Spring Framework 6.1 — WebHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/WebHandler.html)
- [Spring Framework 6.1 — WebFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/WebFilter.html)
- [Spring Framework 6.1 — WebExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/web/server/WebExceptionHandler.html)
- [Spring Framework 6.1 — HttpHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.19/javadoc-api/org/springframework/http/server/reactive/HttpHandler.html)
- [Spring Framework — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
- [Spring Framework — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/web/webflux-functional.html)
- [Spring Framework — Reactive Web Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html)
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [Project Reactor — Reference Guide](https://projectreactor.io/docs/core/release/reference/)

**Tổng kết:** `HttpHandler` là entry point reactive HTTP thấp nhất, xử lý `ServerHttpRequest` và `ServerHttpResponse`. `HttpWebHandlerAdapter` thích nghi chúng thành `ServerWebExchange` và gọi `WebHandler`; `WebHttpHandlerBuilder` lắp WebFilters, WebExceptionHandlers cùng các services cần thiết. Trong ứng dụng WebFlux thông thường, `DispatcherHandler` chính là WebHandler trung tâm để thực hiện annotated controllers/WebFlux.fn routes. Nắm đúng các tầng này giúp bạn viết filters, xử lý errors, tích hợp Security, quan sát HTTP requests và debug reactive I/O mà không nhầm WebFlux với Servlet MVC hoặc coi mọi Mono/Flux tự động là non-blocking.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-002">DispatcherHandler, HandlerMapping, HandlerAdapter và HandlerResultHandler</a>

<details>
<summary>Xem chi tiết</summary>

# DispatcherHandler, HandlerMapping, HandlerAdapter và HandlerResultHandler trong Spring WebFlux

## 1. Mục tiêu bài học

Spring WebFlux dùng kiến trúc **front controller**: HTTP request đi qua hạ tầng reactive rồi được chuyển tới một bộ điều phối trung tâm là `DispatcherHandler`. Dispatcher không cần tự hiểu mọi kiểu controller/route hoặc cách viết JSON/HTML. Nó phối hợp ba nhóm SPI: `HandlerMapping` **tìm handler**, `HandlerAdapter` **gọi handler**, `HandlerResultHandler` **xử lý kết quả để tạo response**.

Đây là bài nền tảng trong mục **Hạ tầng WebFlux**, dành cho **Java 17+, Spring Framework 6.x và Spring Boot 3.x**. Mục tiêu cụ thể:

- Vẽ chính xác pipeline từ HTTP server/`HttpHandler` qua `WebFilter`, `DispatcherHandler` tới response.
- Phân biệt `WebHandler`, `HandlerMapping`, `HandlerAdapter`, `HandlerResult` và `HandlerResultHandler`; hiểu chữ “Handler” trong mỗi tên có nghĩa gì.
- Giải thích thuật toán *mapping đầu tiên khớp → adapter hỗ trợ → result handler hỗ trợ* và thứ tự ưu tiên.
- Nhận biết annotated controllers (`@RestController`, `@GetMapping`, `ResponseEntity`) so với functional routes (`RouterFunction`, `HandlerFunction`, `ServerResponse`).
- Biết tại sao body JSON, status, header và view rendering đi qua những result handlers khác nhau.
- Hiểu `Mono<Void>` hoàn thành HTTP response, nguồn `Mono/Flux` do ai subscribe, xử lý lỗi trước/sau khi response commit.
- Tạo ứng dụng Boot WebFlux nhỏ chạy cả hai mô hình route, quan sát `WebFilter`, in danh sách SPI beans, thử `curl` và `WebTestClient`.
- Tránh tùy tiện ghi đè SPI mặc định, gắn `@Order` sai, block event loop, hay hiểu nhầm `DispatcherHandler` với `DispatcherServlet` của Spring MVC.

**Phạm vi:** đây là **WebFlux reactive stack**, không phải Servlet MVC. Các lớp có tên tương tự ở Spring MVC nhưng nằm ở package khác và có kiểu trả về khác; không copy một interceptor/adapter của MVC sang WebFlux.

## 2. Sơ đồ tổng thể một HTTP request

```text
Client / HTTP request
         |
         v
Reactor Netty (hoặc reactive server adapter phù hợp)
         |
         v
HttpHandler / HttpWebHandlerAdapter
         |
         v
WebExceptionHandler chain / WebFilter chain
         |
         v
DispatcherHandler             (WebHandler trung tâm, bean "webHandler")
         |
         +--> HandlerMapping #1 --no match--+
         +--> HandlerMapping #2 --match----> handler object
         |                                    |
         |                                    v
         +--------------------------> HandlerAdapter hỗ trợ handler
                                              |
                                              v
                                         Mono<HandlerResult>
                                              |
                                              v
                                     HandlerResultHandler hỗ trợ
                                              |
                               +--------------+---------------+
                               |                              |
                         JSON / HTTP body                HTML view / khác
                               |                              |
                               +--------------+---------------+
                                              |
                                              v
                                       Mono<Void> hoàn tất
                                              |
                                              v
                                         HTTP response
```

Sơ đồ là mô hình logic, **không phải một thread/transaction riêng tại mỗi mũi tên**. Tất cả các đoạn có thể trả về một `Mono`; thực thi thật bắt đầu khi hạ tầng HTTP đăng ký vào chuỗi reactive. Bộ ghi response có thể phát nhiều `DataBuffer`/events, gặp backpressure hoặc cancellation khi client ngắt kết nối.

### Vai trò theo một câu

| Thành phần | Một câu giải thích |
| --- | --- |
| `DispatcherHandler` | Phối hợp pipeline chọn handler, gọi handler, chọn cách ghi result |
| `HandlerMapping` | “Request này dành cho handler object nào?” |
| `HandlerAdapter` | “Làm sao gọi object handler thuộc loại này?” |
| `HandlerResult` | Gói giá trị trả về cùng thông tin cần thiết để xử lý result |
| `HandlerResultHandler` | “Kết quả này phải được viết thành HTTP response như thế nào?” |

**Hai lần “handler” khác nhau:** *HandlerAdapter* gọi **request handler** đã tìm được; *HandlerResultHandler* xử lý **giá trị trả về** của việc gọi đó. Không thể thay nhóm này bằng nhóm kia.

## 3. HTTP server, HttpHandler và WebHandler nằm ở đâu?

WebFlux tách abstraction của server transport khỏi web application:

- `org.springframework.http.server.reactive.HttpHandler` là hợp đồng cấp HTTP server, xử lý `ServerHttpRequest` và `ServerHttpResponse`, trả về `Mono<Void>`.
- `org.springframework.web.server.WebHandler` là hợp đồng cấp web application, xử lý `ServerWebExchange`, trả về `Mono<Void>`.
- `ServerWebExchange` gói request/response cộng attributes, session, principal, locale, form/multipart access và trạng thái xử lý.
- `HttpWebHandlerAdapter` là một trong các lớp liên kết HTTP server adapter và `WebHandler` pipeline.
- `WebHttpHandlerBuilder.applicationContext(...)` có thể khám phá `WebFilter`, `WebExceptionHandler` và `WebHandler` tên `webHandler` để dựng chain.
- `DispatcherHandler` là một implementation trung tâm của `WebHandler`; Spring WebFlux configuration/Boot đăng ký nó thông thường.

Ví dụ chữ ký ở mức khái niệm:

```java
// Các API có sẵn trong Spring, KHÔNG tự tạo lại.
Mono<Void> HttpHandler.handle(
    ServerHttpRequest request,
    ServerHttpResponse response);

Mono<Void> WebHandler.handle(ServerWebExchange exchange);
```

Đây là **chữ ký minh họa**, không phải source Java hợp lệ đặt nguyên vào project. Các interfaces thực tế tồn tại trong Spring libraries.

**Điểm quan trọng:** `WebFilter` thường chạy **bao quanh** dispatcher và có thể can thiệp cả route annotation lẫn functional. Một functional `HandlerFilterFunction` lại bao quanh những `HandlerFunction` của router cụ thể; phạm vi khác nhau.

## 4. DispatcherHandler: front controller reactive

`org.springframework.web.reactive.DispatcherHandler` giữ thuật toán chung cho request routing và response processing. Nó khám phá SPI beans từ `ApplicationContext`:

1. Danh sách `HandlerMapping`.
2. Danh sách `HandlerAdapter`.
3. Danh sách `HandlerResultHandler`.

Các bean có thứ tự (`Ordered` hoặc `@Order`) có thể được ưu tiên theo cấu hình. `DispatcherHandler` là một Spring bean; trong cấu hình tiêu chuẩn nó có tên `webHandler` để `WebHttpHandlerBuilder` nhận diện.

### Thuật toán giản lược

```text
handle(exchange):
  handler = first HandlerMapping that produces a handler
  if no handler:
      signal response status 404 / error pathway as configured
  else:
      adapter = first HandlerAdapter where supports(handler) is true
      result = adapter.handle(exchange, handler)
      if result emitted:
          resultHandler = first handler where supports(result) is true
          resultHandler.handleResult(exchange, result) -> Mono<Void>
      if adapter completes empty:
          response may already be fully handled
  finish on completion/error/cancel
```

Đây là **pseudocode**, không phải Java implementation nội bộ từng dòng. Trong thực tế còn có CORS preflight, metadata của exchange, dispatch exception handling, special no-handler behavior, WebExceptionHandlers và reactive operator composition.

### Tại sao chỉ chọn một HandlerMapping?

Mappings được duyệt theo thứ tự ưu tiên; ứng viên đầu tiên phát ra handler quyết định đường xử lý. Một mapping đứng trước và *phát lỗi* vì điều kiện HTTP method/media type không khớp có thể chấm dứt lookup; không được suy “nếu mapping trước có vấn đề thì luôn thử mapping sau”. Điều này khác với ý tưởng thử tất cả route tới khi trả được 200.

**Không dùng @Order để giải quyết hai endpoints trùng nhau một cách mù quáng.** Chọn path/predicate không chồng lấn, test các trường hợp GET/POST/Accept/Content-Type và audit thứ tự thực tế trong application context.

## 5. HandlerMapping: từ request tới handler object

Hợp đồng reactive mapping có thể nhìn như:

```java
Mono<Object> getHandler(ServerWebExchange exchange);
```

Đây là chữ ký của interface Spring `org.springframework.web.reactive.HandlerMapping` (chỉ trích để học). `Mono.empty()` thường nghĩa là mapping này không tìm thấy handler phù hợp; không nhất thiết phải tạo HTTP response. Error signal có thể được phát khi request không đáp ứng điều kiện, tùy implementation.

Những implementations quan trọng:

| Mapping | Đặc trưng | Handler object được chọn |
| --- | --- | --- |
| `RequestMappingHandlerMapping` | Dò `@RequestMapping`, `@GetMapping`, `@PostMapping` của annotated controllers | `HandlerMethod` biểu diễn method Java đã được ánh xạ |
| `RouterFunctionMapping` | Dò beans `RouterFunction<?>`, kết hợp các route functional | `HandlerFunction<?>` |
| `SimpleUrlHandlerMapping` | Ánh xạ URL pattern trực tiếp tới đối tượng handler đã đăng ký | Chẳng hạn một `WebHandler` |
| Resource mappings | Phục vụ tài nguyên tĩnh theo WebFlux config | Handler phù hợp resource infrastructure |

**Không đánh đồng HandlerMapping với URL string comparison đơn thuần.** Annotated mapping còn xét HTTP method, path variables, query params, headers, `consumes`, `produces`, content negotiation và các điều kiện cụ thể khác.

### 5.1. Annotated request mapping

```java
@RestController
@RequestMapping("/api")
class ExampleController {

    @GetMapping(path = "/products/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ProductDto> one(@PathVariable String id) {
        return service.find(id);
    }
}
```

Đây là **snippet** yêu cầu `service`/`ProductDto` và imports. `RequestMappingHandlerMapping` phát hiện annotation, tạo metadata mapping cho method. Khi `GET /api/products/42` tới, handler object là một `HandlerMethod`, không phải trực tiếp `Mono<ProductDto>`.

### 5.2. Functional route mapping

```java
@Bean
RouterFunction<ServerResponse> routes() {
    return RouterFunctions.route()
        .GET("/fn/ping",
             request -> ServerResponse.ok().bodyValue("pong"))
        .build();
}
```

Đây là **snippet** trong class `@Configuration` có imports. `RouterFunctionMapping` tìm `RouterFunction` và chọn một `HandlerFunction`; functional route không cần `@RestController`. Để invocation tạo HTTP response, pipeline còn dùng `HandlerFunctionAdapter` và `ServerResponseResultHandler`.

### 5.3. HandlerMapping ordering

Một hệ thống có cả annotation và functional routes hoạt động được vì mỗi kiểu có mapping tương ứng. Nếu hai mappings khác nhau nhận **cùng một URL/method**, kết quả có thể phụ thuộc thứ tự mappings. Không khẳng định “functional luôn ưu tiên annotation” hay ngược lại trên mọi Spring version/configuration. Phương pháp an toàn: tạo paths không trùng, dùng test xác minh và chỉ can thiệp order khi thực sự có nhu cầu hạ tầng.

## 6. HandlerAdapter: gọi handler bất kể kiểu nào

Interface `org.springframework.web.reactive.HandlerAdapter` có hai operations cốt lõi:

```java
boolean supports(Object handler);

Mono<HandlerResult> handle(
        ServerWebExchange exchange, Object handler);
```

Đây là **chữ ký minh họa**, không phải class compile độc lập. `supports` quyết định adapter có hiểu handler object đó không. `handle` gọi handler và cung cấp `Mono<HandlerResult>` cho dispatcher.

Những adapters điển hình:

- `RequestMappingHandlerAdapter`: gọi `HandlerMethod` trong annotated controller; giải quyết `@PathVariable`, `@RequestParam`, `@RequestBody`, validation, return value và exception-handling metadata.
- `HandlerFunctionAdapter`: gọi functional `HandlerFunction<ServerResponse>` với `ServerRequest` thích hợp.
- `SimpleHandlerAdapter`: hỗ trợ những handler kiểu `WebHandler` đơn giản.

### Controller invocation không phải response serialization

Ví dụ annotated method:

```java
@GetMapping("/data")
Mono<MyDto> data() {
    return service.load();
}
```

`RequestMappingHandlerAdapter` gọi method và nhận giá trị trả về (ở đây là `Mono<MyDto>`) dưới dạng metadata trong `HandlerResult`; adapter **không phải luôn là thành phần cuối cùng tự gửi JSON**. `ResponseBodyResultHandler`, HTTP message writers/codecs và response layer mới chịu trách nhiệm serialization/writing.

Một adapter cũng có thể tự xử lý toàn bộ request và hoàn thành với `Mono.empty()` trong những tình huống hỗ trợ; đừng xây middleware giả định luôn có một `HandlerResult`.

## 7. HandlerResult: kết quả chưa phải bytes HTTP

`org.springframework.web.reactive.HandlerResult` gói:

- **Handler** đã tạo ra result.
- **Return value** (có thể là regular object hoặc reactive wrapper).
- **Return type metadata** như `MethodParameter`/`ResolvableType` để quyết định xử lý.
- **Model** nếu phù hợp cho view rendering.
- **Exception handling** reference tùy adapter, ví dụ `DispatchExceptionHandler` cho lỗi phát sinh muộn.

Với controller:

```java
@GetMapping("/users/{id}")
Mono<ResponseEntity<UserDto>> user(@PathVariable long id) {
    return userService.find(id)
        .map(ResponseEntity::ok);
}
```

Đây là snippet với `userService`/`UserDto`. Adapter có thể trả `HandlerResult` chứa một giá trị reactive được khai báo là `Mono<ResponseEntity<UserDto>>` và return type metadata. **Đừng nghĩ mỗi `onNext` từ Mongo/DB tạo một HandlerResult mới**: HandlerResult là kết quả của việc gọi handler, còn Publisher bên trong là nguồn values được result handler consume để viết body.

`HandlerResult` không phải `ServerResponse` của WebFlux.fn. `ServerResponse` là một loại **return value** có thể nằm trong `HandlerResult` để được `ServerResponseResultHandler` xử lý.

## 8. HandlerResultHandler: biến result thành HTTP response

Hợp đồng Spring `org.springframework.web.reactive.HandlerResultHandler`:

```java
boolean supports(HandlerResult result);

Mono<Void> handleResult(
        ServerWebExchange exchange, HandlerResult result);
```

`supports` xác nhận có thể xử lý return value + metadata, `handleResult` cập nhật status/headers, chọn body writer hay view và hoàn tất reactive operation. `Mono<Void>` đại diện cho thời điểm response-writing kết thúc, **không** phải body rỗng bắt buộc.

Spring WebFlux 6.x config cung cấp những nhóm chính:

| Result handler | Giá trị được hỗ trợ | Default order được tài liệu Spring nêu |
| --- | --- | --- |
| `ResponseEntityResultHandler` | `ResponseEntity` từ annotated controller | **0** |
| `ServerResponseResultHandler` | `ServerResponse` của functional endpoints | **0** |
| `ResponseBodyResultHandler` | `@ResponseBody` / `@RestController` ordinary body | **100** |
| `ViewResolutionResultHandler` | View name, `View`, model, `Rendering`, ... | **Integer.MAX_VALUE** |

Hai result handlers có cùng order 0 **không** đồng nghĩa tranh nhau cùng value. Mỗi handler có `supports(...)` để nhận đúng contract. Không ghi đè order nhằm ép mọi `Mono<Dto>` đi qua `ResponseEntityResultHandler`.

### 8.1. ResponseEntityResultHandler

Annotated method trả:

```java
@GetMapping("/accepted")
Mono<ResponseEntity<Message>> accepted() {
    return Mono.just(ResponseEntity.accepted()
        .header("X-Example", "yes")
        .body(new Message("queued")));
}
```

Snippet với `Message` record/imports đã có. `ResponseEntityResultHandler` xử lý metadata status **202**, header và body `Message` qua message writer. Nó không chuyển 202 thành “job đã được thực thi”; đó là semantic của ứng dụng.

### 8.2. ResponseBodyResultHandler

`@RestController` trả `Mono<Message>` hoặc `Flux<Message>` được xử lý theo `@ResponseBody` semantics. Với `application/json`, phù hợp message writer/codecs (thường Jackson) encode DTO. Với `text/event-stream`, codec streaming được dùng; không suy một Reactor element bằng một TCP packet.

### 8.3. ServerResponseResultHandler

Functional `HandlerFunction` trả `Mono<ServerResponse>`. Result handler nhận `ServerResponse` và gọi logic `writeTo(...)` của response, sử dụng message writers/handler strategies thích hợp. Không bắt annotated controller trả `ServerResponse` chỉ vì cả hai đều gọi là “response”; đó là contract riêng của WebFlux.fn.

### 8.4. ViewResolutionResultHandler

Với annotated `@Controller` trả logical view name và có view resolver engine, handler có thể tạo HTML (Thymeleaf/Freemarker tùy dependencies/config). Nếu không có view technology, không tự giả định template luôn render được. Nếu method có `@ResponseBody`, một `String` thường là **HTTP response body**, không phải tên view.

## 9. Bốn con đường trên cùng một DispatcherHandler

```text
GET /api/hello/Ana
  RequestMappingHandlerMapping -> HandlerMethod
  RequestMappingHandlerAdapter -> HandlerResult(Mono<Greeting>)
  ResponseBodyResultHandler -> 200 JSON

GET /api/status
  RequestMappingHandlerMapping -> HandlerMethod
  RequestMappingHandlerAdapter -> HandlerResult(Mono<ResponseEntity<Greeting>>)
  ResponseEntityResultHandler -> 202 JSON

GET /fn/hello/Ana
  RouterFunctionMapping -> HandlerFunction
  HandlerFunctionAdapter -> HandlerResult(Mono<ServerResponse>)
  ServerResponseResultHandler -> 200 JSON

GET /pages/home   (khi có view template config)
  RequestMappingHandlerMapping -> HandlerMethod
  RequestMappingHandlerAdapter -> HandlerResult("home")
  ViewResolutionResultHandler -> rendered HTML
```

Sơ đồ diễn tả các luồng **theo contract chuẩn**, không phải trace được thu từ app thật. Những phần như annotation metadata, content negotiation và response writing có thêm bước nội bộ. Mục đích là thấy Dispatcher **không đổi thuật toán** dù kết quả do các result handlers khác nhau xử lý.

## 10. Message readers, writers và content negotiation

`HandlerAdapter` cần đọc/bind parameters/body; `HandlerResultHandler` thường phải chọn cách ghi response. Với WebFlux, codec infrastructure gồm `HttpMessageReader`, `HttpMessageWriter` và `ServerCodecConfigurer`.

- `Content-Type` của request cho biết loại body client gửi. Một endpoint `consumes="application/json"` có thể bị từ chối bằng **415 Unsupported Media Type** nếu client gửi loại không hỗ trợ.
- `Accept` cho biết loại representation client chấp nhận. Một endpoint chỉ trả JSON có thể bị từ chối bằng **406 Not Acceptable** khi không có representation phù hợp.
- `produces` trong annotation/request predicate là điều kiện route; message writer cũng phải hỗ trợ encode kiểu dữ liệu/định dạng đó.
- Đọc request body `Mono<Dto>` cần decoder phù hợp; malformed JSON có thể tạo **400 Bad Request**.
- `@ResponseBody` + String không tự thành view; `@Controller` + String không mặc định là raw body.
- Server-Sent Events và NDJSON có streaming semantics khác JSON array thông thường, cần cấu hình media type đúng.

**Không viết một HandlerResultHandler mới chỉ để thêm field vào JSON DTO.** Nếu nhu cầu là validation/serialization, xem Jackson, controller advice, `CodecCustomizer`, WebFlux config hoặc message writers trước khi tự tạo SPI.

## 11. Reactive response completion và backpressure

Mặc dù tên class giống Spring MVC, WebFlux dùng reactive types:

```text
Adapter.handle(...)          -> Mono<HandlerResult>
ResultHandler.handleResult() -> Mono<Void>
Response body Publisher      -> Mono<T> / Flux<T>
```

Điểm dễ nhầm:

- `Mono<HandlerResult>` không đảm bảo xử lý synchronous: handler có thể đọc body hoặc xử lý async để tạo result.
- `Mono<Void>` hoàn thành khi công việc ghi response tương ứng hoàn tất/được xử lý; không có nghĩa response body luôn rỗng.
- HTTP infrastructure subscribe vào Publisher được tạo từ request chain; **controller không tự gọi `subscribe()`** để làm result handler chạy.
- Một stream có thể bị **cancel** khi HTTP client ngắt; cleanup tài nguyên phải xử lý cả complete/error/cancel.
- Một result handler có thể gọi codecs và phối hợp flow control; `onNext` không nhất thiết tương ứng một lần TCP write.
- Khi response đã commit header/body, không đảm bảo chuyển một lỗi stream muộn thành JSON lỗi mới cùng status khác.

**Chỉ return Flux/Mono là chưa đủ để mọi thứ non-blocking.** Nếu controller gọi JDBC blocking trước khi return Mono hoặc dùng `block()` trên Netty event loop, có thể gây thread starvation/error. Dùng reactive drivers hoặc chuyển legacy blocking work sang bounded scheduler với quản lý capacity.

## 12. Xử lý lỗi ở các lớp khác nhau

Không phải mọi lỗi đều xảy ra ở một chỗ. Phân biệt:

| Giai đoạn | Ví dụ | Cơ chế xử lý thường gặp |
| --- | --- | --- |
| `WebFilter` trước dispatcher | Authentication/rate/error | Security filter, `WebExceptionHandler` |
| `HandlerMapping` | Không có route, method sai, mismatch predicates | Not-found/status exception hoặc mapping error theo cấu hình |
| `HandlerAdapter` khi bind | Query sai kiểu, JSON body hỏng, validation | Exception resolver của annotated controller hoặc WebExceptionHandler |
| Business logic reactive | `Mono.error(ex)` từ service | `@ExceptionHandler`/`@ControllerAdvice` khi phù hợp |
| Result handling | Không có codec, serialize lỗi, view thiếu | Exception handling trước commit nếu khả thi |
| Sau response commit | Streaming error/client disconnect | Cleanup/metrics, thường không thay status đã gửi |

Spring Framework 6.x có `DispatchExceptionHandler` để hỗ trợ chuyển một `Throwable` thành `HandlerResult` cho một số tình huống. `RequestMappingHandlerAdapter` hỗ trợ exception handling của annotated controllers; `HandlerResult` có thể mang exception handler dùng cho **deferred errors** hoặc lỗi trong result rendering.

Ở cấp web chain, `WebExceptionHandler` xử lý errors từ filters/target handler; Spring Boot còn có auto-configured error handling. **`@RestControllerAdvice` không phải bắt mọi exception trước/ngoài dispatcher** và không bảo đảm can thiệp sau khi bytes đã gửi tới client.

### Không có mapping và HTTP status

Một route không tồn tại thường nhận 404. Nhưng:

- Đường dẫn đúng mà HTTP method không hỗ trợ có thể thành **405 Method Not Allowed**.
- `consumes` mismatch có thể thành 415.
- `produces`/Accept mismatch có thể thành 406.
- Security filter có thể trả 401/403 trước mapping.
- Custom error handlers/proxy có thể thay body hoặc status.

Vì vậy không khẳng định mọi request không chạy vào controller đều là 404 hoặc mọi 404 đều do `HandlerMapping` cụ thể.

## 13. WebFilter khác HandlerMapping và ControllerAdvice thế nào?

Một `WebFilter` có contract:

```java
Mono<Void> filter(
    ServerWebExchange exchange, WebFilterChain chain);
```

Đây là chữ ký của interface Spring, không phải mã class hoàn chỉnh. Nó có thể xử lý trước/sau `chain.filter(exchange)`, thêm response header, lấy correlation ID, thiết lập context hoặc từ chối request sớm.

| Công cụ | Phạm vi | Nên dùng cho |
| --- | --- | --- |
| `WebFilter` | Bao ngoài request xử lý qua WebHandler pipeline | Authn/authz trong Spring Security, tracing, request metrics |
| `HandlerMapping` | Tìm endpoint/handler theo request | Annotation routes, functional routes, custom routing |
| `HandlerAdapter` | Gọi một kiểu handler object | Extension của programming model |
| `HandlerResultHandler` | Xử lý return values | HTTP body/result/view strategy |
| `@RestControllerAdvice` | Exception/binding response thuộc annotated controller model | Domain/validation error mapping |
| `WebExceptionHandler` | Errors xuyên WebFilter/WebHandler chain | Lỗi ngoài annotated method hoặc infrastructure |
| Functional `HandlerFilterFunction` | Bao các handler functions thuộc router | Logic chung cho một nhóm functional routes |

Một `WebFilter` có thể set header trước khi route được chọn; nó **không phải** là một HandlerMapping. Nếu WebFilter từ chối request và tự complete response, DispatcherHandler có thể không được gọi.

## 14. Config WebFlux và khả năng mở rộng SPI

Trong project bình thường, **để Spring Boot và WebFlux Config tạo SPI mặc định** là lựa chọn tốt. Các entrypoint tùy chỉnh thường là:

- `WebFluxConfigurer` (cấu hình formatters, codecs, CORS, view resolvers, resource handlers...).
- Custom `WebFilter` cho logic cắt ngang ở request chain.
- Bean `RouterFunction<ServerResponse>` hoặc annotated `@RestController` cho routes nghiệp vụ.
- Global exception handling, codecs, validation và functional filters.
- Custom implementations của `HandlerMapping`, `HandlerAdapter` hoặc `HandlerResultHandler` **chỉ khi thêm một programming model thực sự mới**.

**Đừng mặc định thêm `@EnableWebFlux` trong Spring Boot rồi kỳ vọng Boot autoconfiguration vẫn hoạt động nguyên như trước.** Khi tự chiếm quyền WebFlux configuration, một số Boot conveniences có thể thay đổi/không được áp dụng theo cách cũ; hãy đọc đúng tài liệu Boot version hiện tại. Trong ví dụ runnable ở dưới **không dùng `@EnableWebFlux`**.

Một adapter nhận mọi `Object` hoặc result handler luôn `supports(...) == true` với order rất cao có thể chiếm request/result của các thành phần chuẩn. Nếu cần custom SPI, hãy viết `supports` **chặt chẽ**, dùng order có chủ đích, kiểm tra coexistence với annotated và functional routes và test cả no-match.

## 15. Project Spring Boot 3 Java 17+ runnable

Tạo Maven project từ Spring Initializr với **Java 17+**, Spring Boot **3.x**, dependencies:

- `spring-boot-starter-webflux`.
- `spring-boot-starter-test` (scope test).
- `io.projectreactor:reactor-test` (scope test nếu starter test của bản được dùng chưa cung cấp).

**Không thêm `spring-boot-starter-web`** nếu muốn Boot tự chọn reactive WebFlux server application type khi hai starters cùng xuất hiện. Nếu có cả MVC và WebFlux, Boot thường ưu tiên MVC trừ khi application type được chọn riêng. Project này dùng WebFlux thuần.

Cấu trúc source:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  ApiController.java
  FunctionalRoutes.java
  TraceWebFilter.java
  PipelineInspector.java
src/test/java/com/example/demo/
  PipelineHttpTest.java
```

Đặt tất cả file trong package `com.example.demo`. Không cần database, security hoặc template engine. Sau khi chạy, WebFlux sẽ có cả annotated routes `/api/*` lẫn functional route `/fn/*`, tránh trùng URL để test deterministic.

### 15.1. DemoApplication.java

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

### 15.2. ApiController.java

```java
package com.example.demo;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping(path = "/hello/{name}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Greeting> hello(@PathVariable String name) {
        return Mono.just(new Greeting("annotated", name));
    }

    @GetMapping(path = "/status",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Greeting>> status() {
        return Mono.just(ResponseEntity.status(
                HttpStatus.ACCEPTED)
            .body(new Greeting("responseEntity", "ok")));
    }

    @GetMapping(path = "/events",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Greeting> events() {
        return Flux.range(1, 3)
            .map(i -> new Greeting("event", Integer.toString(i)))
            .delayElements(Duration.ofMillis(60));
    }

    @GetMapping("/missing")
    public Mono<Greeting> missing() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "not found"));
    }

    public record Greeting(String style, String name) {}
}
```

Các endpoint:

- `/api/hello/{name}`: annotated route trả `Mono<Greeting>`; `ResponseBodyResultHandler` và JSON writer xử lý result.
- `/api/status`: annotated route trả `Mono<ResponseEntity<Greeting>>`; `ResponseEntityResultHandler` xử lý 202, body JSON.
- `/api/events`: annotated route trả `Flux<Greeting>` với `text/event-stream`; body writer phát SSE.
- `/api/missing`: phát error trước response commit; `ResponseStatusException` dẫn tới 404 theo error handling tiêu chuẩn.

Controller không gọi `subscribe()` hoặc `block()`. Các Mono/Flux synchronous/lazy/scheduled theo từng operator; framework giữ subscription và response writing lifecycle. `delayElements` không cam kết thời gian chính xác 60ms trong môi trường tải cao.

### 15.3. FunctionalRoutes.java

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class FunctionalRoutes {

    @Bean
    public RouterFunction<ServerResponse> greetingRoutes() {
        return RouterFunctions.route()
            .GET("/fn/hello/{name}", request ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ApiController.Greeting(
                        "functional", request.pathVariable("name"))))
            .build();
    }
}
```

Không dùng `@RestController` cho functional route. `RouterFunctionMapping` chọn `HandlerFunction`, `HandlerFunctionAdapter` gọi lambda, `ServerResponseResultHandler` ghi `ServerResponse`. Functional `ServerResponse.ok().bodyValue(...)` tạo `Mono<ServerResponse>` theo contract, controller function không tự ghi bytes bằng tay.

### 15.4. TraceWebFilter.java

```java
package com.example.demo;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class TraceWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange, WebFilterChain chain) {

        // Demo: thêm header trước khi WebFlux ghi response.
        exchange.getResponse().getHeaders()
                .add("X-Pipeline", "webflux");

        return chain.filter(exchange);
    }
}
```

Filter đơn giản áp dụng cả hai route families. Không dùng header này làm tín hiệu xác thực/bảo mật; production tracing cần correlation ID đáng tin và sanitize. Nếu security filter hoặc gateway kết thúc request trước filter này do thứ tự chain khác, header có thể không xuất hiện — đừng dùng nó như bằng chứng duy nhất route đã chạy.

### 15.5. PipelineInspector.java

Để **quan sát SPI beans được Boot/WebFlux config tạo**, dùng một `ApplicationRunner` in class names khi app khởi động:

```java
package com.example.demo;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResultHandler;

@Component
public class PipelineInspector implements ApplicationRunner {

    private final List<HandlerMapping> mappings;
    private final List<HandlerAdapter> adapters;
    private final List<HandlerResultHandler> resultHandlers;

    public PipelineInspector(
            List<HandlerMapping> mappings,
            List<HandlerAdapter> adapters,
            List<HandlerResultHandler> resultHandlers) {
        this.mappings = mappings;
        this.adapters = adapters;
        this.resultHandlers = resultHandlers;
    }

    @Override
    public void run(ApplicationArguments args) {
        mappings.forEach(x ->
            System.out.println("mapping=" +
                x.getClass().getSimpleName()));

        adapters.forEach(x ->
            System.out.println("adapter=" +
                x.getClass().getSimpleName()));

        resultHandlers.forEach(x ->
            System.out.println("result=" +
                x.getClass().getSimpleName()));
    }
}
```

Output **có thể** có các dòng:

```text
mapping=RequestMappingHandlerMapping
mapping=RouterFunctionMapping
adapter=RequestMappingHandlerAdapter
adapter=HandlerFunctionAdapter
result=ResponseEntityResultHandler
result=ServerResponseResultHandler
result=ResponseBodyResultHandler
```

Đây là **danh sách ví dụ**, không phải stdout đầy đủ, cố định về thứ tự/số bean. Version Spring, auto-config, static resources và các beans khác có thể tạo thêm dòng; danh sách phụ thuộc context. Tool này introspect SPI mặc định, **không sửa `@Order` hoặc override behavior**.

### 15.6. Chạy chương trình

Bên trong thư mục project Maven Boot có wrapper:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Server mặc định thường ở port 8080 nếu chưa đổi `server.port`. Không biên dịch từng controller bằng `javac ApiController.java` khi thiếu Spring/Reactor/Jackson classpath; ví dụ là **ứng dụng Maven**, không phải một file JDK thuần.

## 16. curl: từng đường đi qua result handler khác nhau

### Annotated route → response body

```bash
curl -i 'http://localhost:8080/api/hello/Ana'
```

Dự kiến:

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Pipeline: webflux

{"style":"annotated","name":"Ana"}
```

Các headers tự động khác, casing và thứ tự có thể tùy web server. `X-Pipeline` được filter demo thêm trước dispatcher; JSON object được Jackson serialize.

### ResponseEntity → status/headers/body

```bash
curl -i 'http://localhost:8080/api/status'
```

Dự kiến HTTP **202 Accepted**, body:

```json
{"style":"responseEntity","name":"ok"}
```

Không phải response 202 tự chứng minh một background job đã được enqueue; endpoint chỉ minh họa `ResponseEntityResultHandler` đọc status từ `ResponseEntity`.

### Functional route → ServerResponse

```bash
curl -i 'http://localhost:8080/fn/hello/Ana'
```

Dự kiến HTTP **200**:

```json
{"style":"functional","name":"Ana"}
```

Đây là đường `RouterFunctionMapping → HandlerFunctionAdapter → ServerResponseResultHandler`. Cùng dispatcher, khác bộ delegates so với annotated route.

### SSE → streaming body writer

```bash
curl -N -i 'http://localhost:8080/api/events'
```

Ví dụ phần body:

```text
data:{"style":"event","name":"1"}

data:{"style":"event","name":"2"}

data:{"style":"event","name":"3"}

```

`curl -N` tắt buffering ở curl để dễ quan sát events. Định dạng body theo SSE writer, nhưng thời gian flush/packet boundaries khác nhau. Đừng khẳng định event thứ ba đến chính xác sau 180ms.

### Error status và no-match

```bash
curl -i 'http://localhost:8080/api/missing'
curl -i 'http://localhost:8080/not-found'
```

Cả hai thường trả **404**, nhưng cơ chế phát hiện khác nhau: endpoint `/api/missing` được route tới method rồi phát error; `/not-found` không có handler phù hợp. Error body có thể khác tùy Spring Boot/problem-details config; không assert một JSON chi tiết chưa cấu hình.

## 17. WebTestClient: kiểm tra luồng HTTP thực

Tệp `src/test/java/com/example/demo/PipelineHttpTest.java`:

```java
package com.example.demo;

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
class PipelineHttpTest {

    @Autowired
    WebTestClient client;

    @Test
    void annotatedControllerProducesJson() {
        client.get().uri("/api/hello/Ana")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Pipeline", "webflux")
            .expectBody()
            .jsonPath("$.style").isEqualTo("annotated")
            .jsonPath("$.name").isEqualTo("Ana");
    }

    @Test
    void responseEntityControlsStatus() {
        client.get().uri("/api/status")
            .exchange()
            .expectStatus().isAccepted()
            .expectBody()
            .jsonPath("$.style").isEqualTo("responseEntity");
    }

    @Test
    void functionalRouteUsesServerResponse() {
        client.get().uri("/fn/hello/Ana")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Pipeline", "webflux")
            .expectBody()
            .jsonPath("$.style").isEqualTo("functional");
    }

    @Test
    void controllerErrorsAndUnknownRoutesYield404() {
        client.get().uri("/api/missing")
            .exchange().expectStatus().isNotFound();

        client.get().uri("/does-not-exist")
            .exchange().expectStatus().isNotFound();
    }

    @Test
    void streamEmitsThreeEvents() {
        Flux<ApiController.Greeting> events =
            client.get().uri("/api/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ApiController.Greeting.class)
                .getResponseBody();

        StepVerifier.create(events)
            .expectNext(new ApiController.Greeting("event", "1"))
            .expectNext(new ApiController.Greeting("event", "2"))
            .expectNext(new ApiController.Greeting("event", "3"))
            .verifyComplete();
    }
}
```

**Test caveat:** WebTestClient có các codecs do Spring Test/WebFlux cấu hình. Trong một số phiên bản/client settings, SSE JSON cần decode qua `ServerSentEvent<Greeting>` hoặc một `ParameterizedTypeReference` rõ kiểu thay vì DTO trực tiếp; điều chỉnh test nếu codec không map thẳng. Việc dùng `returnResult` và `StepVerifier` giúp xác minh **stream signals**, không nhầm với TCP chunks. Nếu bật Spring Security, cần mock identity/CSRF hoặc client credentials theo test profile; security có thể chặn trước handler.

Chạy:

```bash
./mvnw test
```

Trên PowerShell:

```powershell
.\mvnw.cmd test
```

Đây là code mẫu cho project Spring Boot 3 có dependencies tương ứng, **không phải tuyên bố tests đã chạy trong repository của người dùng**. Khi muốn xác minh trên Netty/proxy thật, dùng `RANDOM_PORT`/HTTP và test qua đúng ingress production, không chỉ dùng mock exchange.

## 18. Java 17 thuần: mô phỏng ba trách nhiệm với stdout cố định

Phần này **không giả lập Reactor hay implement Spring SPI**; nó chỉ làm lộ rõ ba trách nhiệm bằng Java 17 độc lập, để đối chiếu khi đọc các interfaces thật.

Tệp `PipelineRolesDemo.java`:

```java
public class PipelineRolesDemo {

    interface Mapping {
        String map(String path);
    }

    interface Adapter {
        String invoke(String handler);
    }

    interface ResultWriter {
        void write(String result);
    }

    public static void main(String[] args) {
        Mapping mapping = path ->
            "/hello".equals(path) ? "helloHandler" : null;

        Adapter adapter = handler ->
            "helloHandler".equals(handler) ? "Xin chào" : "unknown";

        ResultWriter writer = result ->
            System.out.println("HTTP body: " + result);

        String handler = mapping.map("/hello");
        System.out.println("handler=" + handler);
        String result = adapter.invoke(handler);
        System.out.println("result=" + result);
        writer.write(result);
    }
}
```

Chạy không cần Spring:

```bash
javac PipelineRolesDemo.java
java PipelineRolesDemo
```

Stdout **chính xác**:

```text
handler=helloHandler
result=Xin chào
HTTP body: Xin chào
```

Điều mô phỏng này bỏ qua `ServerWebExchange`, reactive Publishers, HTTP status, codecs, errors và real SPI contracts. Khi đọc code WebFlux, đổi từ interface demo `Mapping/Adapter/ResultWriter` sang `HandlerMapping/HandlerAdapter/HandlerResultHandler` với signatures đúng và kết quả `Mono`.

## 19. Thử nghiệm routing nâng cao và thứ tự ưu tiên

Nếu cần chẩn đoán một route không chạy:

1. Xem `HandlerMapping` beans hiện diện trong context: có `RequestMappingHandlerMapping` và `RouterFunctionMapping` hay không.
2. Kiểm tra method, path, headers, consumes/produces và path variables, không chỉ URL.
3. Xem thứ tự mappings (`Ordered`), nhưng đừng dựa vào số order được truyền miệng từ Spring version khác.
4. Nếu mapping đúng mà controller không được gọi, kiểm `WebFilter`/Spring Security, preflight CORS, exception trước adapter.
5. Nếu controller được gọi mà status/body không đúng, xét **return value type** và result handler tương ứng.
6. Nếu body không serialize, kiểm media type, codec registration và DTO/Jackson configuration.
7. Nếu response là HTML view thay vì string body, kiểm `@Controller` vs `@RestController`/`@ResponseBody`.
8. Nếu exception không vào `@ExceptionHandler`, xác định lỗi xảy ra ở layer filter, mapping, adapter hay **sau response commit**.
9. Kiểm tra startup logs/mapping introspection, test HTTP thực rồi mới cân nhắc custom SPI.

### Custom HandlerMapping chỉ khi cần programming model khác

Một custom mapping triển khai `HandlerMapping.getHandler(ServerWebExchange)` và trả handler object, nhưng **phải có HandlerAdapter biết gọi kiểu object đó**. Nếu bạn trả custom object không adapter nào `supports`, dispatcher không thể tự đoán cách invoke.

### Custom HandlerAdapter phải trả kết quả hợp contract

Một adapter custom phải:

- `supports(Object)` chặt chẽ, tránh nuốt HandlerMethod/HandlerFunction của Spring.
- `handle(exchange, handler)` trả `Mono<HandlerResult>` hoặc complete empty nếu đã tự handle response.
- Không block event loop khi gọi I/O.
- Tôn trọng cancellation/error và lưu exception handling strategy khi phù hợp.
- Có `HandlerResultHandler` hỗ trợ kiểu return value mới nếu default handlers không biết cách ghi.
- Có tests cho cả custom route và **mọi route tiêu chuẩn** cùng tồn tại.

### Custom HandlerResultHandler phải cẩn thận với order

Một result handler `supports` mọi thứ và `@Order(-1000)` có thể nhận cả `ResponseEntity`/`ServerResponse`, phá semantics HTTP. Nếu muốn hỗ trợ một specialized return type, giới hạn `supports` theo metadata/type và dùng `Mono<Void>` trả về từ response writer đúng reactive contract; kiểm error/commit/backpressure.

**Không viết custom SPI chỉ để đáp ứng một endpoint JSON thông thường.** Annotated controller hoặc RouterFunction đã hỗ trợ gần như toàn bộ use cases HTTP nghiệp vụ phổ biến.

## 20. Khi nào chọn annotated hay functional endpoints?

| Tiêu chí | Annotated controllers | Functional endpoints |
| --- | --- | --- |
| Entry | `@RestController`/`@RequestMapping` | `RouterFunction`/`HandlerFunction` |
| Mapping | `RequestMappingHandlerMapping` | `RouterFunctionMapping` |
| Adapter | `RequestMappingHandlerAdapter` | `HandlerFunctionAdapter` |
| Result | `ResponseBodyResultHandler`/`ResponseEntityResultHandler`/view... | `ServerResponseResultHandler` |
| Truy cập request | Method args và binding/validation annotations | `ServerRequest` |
| Response | Return DTO/Mono/Flux/ResponseEntity | `Mono<ServerResponse>` |
| Shared concerns | `@ControllerAdvice`, filters, codecs | Router-specific filters, `WebFilter`, codecs |
| Sở thích | API domain + validation annotations | Routing tập trung và functional composition |

Có thể phối hợp hai phong cách trong **một WebFlux app**. Chọn dựa trên team/style/khả năng kiểm thử, không vì một kiểu “non-blocking hơn” kiểu kia: cả hai đều dùng cùng Reactor/HTTP infrastructure. **Spring MVC functional `RouterFunction` thuộc package Servlet khác với WebFlux.fn**, cần imports chính xác.

## 21. Bảo mật, CORS và thứ tự xử lý

`DispatcherHandler` không phải lớp chịu trách nhiệm duy nhất về bảo mật:

- Spring Security WebFlux sử dụng `SecurityWebFilterChain`/WebFilters để authenticate/authorize request theo policy.
- `WebFilter` có thể reject trước routing, nhưng route metadata và matcher policy phải nhất quán để tránh bypass.
- CORS preflight có đường xử lý đặc biệt; Spring MVC `CorsFilter` Servlet không được dùng làm thay thế cho WebFlux CORS config.
- Không cho phép custom routing dựa raw client headers/path không canonicalized mà bỏ kiểm tra quyền trên resource.
- `@RestControllerAdvice` xử lý domain errors, **không** thay authorization policy ở filter chain.
- Error response không tiết lộ Java stack trace, internal handler bean names, session tokens hoặc SQL details.
- Không biến default result handler thành “send raw HTML” cho untrusted data: XSS cần output encoding/content policy.
- WebFlux reactive Context và security context không nên được thay bằng một ThreadLocal tự tạo trên Netty event loop.
- Với streaming, cần giới hạn số events, kích thước buffers và cleanup subscriptions khi disconnect.

Một `WebFilter` tự ghi status/body sớm có thể làm dispatcher không chạy; điều này phải có chủ ý và test, không coi là lỗi HandlerMapping nếu controller không được gọi.

## 22. Hiệu năng và observability theo từng lớp

Pipeline WebFlux rất nhanh khi đi qua các SPI mặc định, nhưng bottleneck thường ở service/database/network hoặc response body writer hơn là việc dò mapping.

Những tín hiệu nên đo:

| Metric / trace | Diễn giải |
| --- | --- |
| Request route match / no match | Route conditions có đúng không? |
| 404/405/406/415 | URI, method, Accept/Content-Type hoặc codec mismatch |
| Controller/service latency | Thời gian thực hiện business logic |
| Async body emit / write latency | Backpressure, client chậm, serialization |
| Reactor Netty event-loop blocking | Có gọi JDBC/`block()`/file IO trên event loop? |
| JSON encode/error rates | DTO, codec, payload shape |
| WebFilter/security failures | Lỗi trước dispatcher |
| Client cancellations | Connection đóng, cleanup resources |
| Memory buffers/queues | Streaming có accumulation không giới hạn? |
| Exception stage | Mapping, adapter, service, result writing, filter |

Nếu có tracing/metrics, nên gắn cùng trace ID xuyên request (dùng Reactor Context/observability đúng), không giả định một HTTP request luôn nằm trên một Java thread. Đừng log raw Authorization headers, credentials hoặc private response body để debug.

Tự in toàn bộ HandlerResult/handler object vào logs có thể lộ dữ liệu và gây chi phí; startup inspector chỉ nên in class names, và production có thể bỏ/giới hạn logs.

## 23. Bảng phân biệt với Spring MVC

| Đặc điểm | Spring WebFlux | Spring MVC |
| --- | --- | --- |
| Front controller | `DispatcherHandler` | `DispatcherServlet` |
| Core API | `WebHandler`, `ServerWebExchange`, `Mono<Void>` | `HttpServletRequest`/`HttpServletResponse` và Servlet dispatch |
| Mapping SPI | `org.springframework.web.reactive.HandlerMapping` | `org.springframework.web.servlet.HandlerMapping` |
| Adapter SPI | `org.springframework.web.reactive.HandlerAdapter` | `org.springframework.web.servlet.HandlerAdapter` |
| Result handling | `HandlerResultHandler` với `Mono<Void>` | MVC return-value handling/message converters/view resolvers |
| HTTP server | Reactor Netty hoặc reactive adapters | Servlet container |
| Programming models | Annotated + WebFlux.fn | Annotated + MVC.fn |
| I/O model | Reactive/non-blocking end-to-end nếu dependencies hỗ trợ | Servlet request model (có async support) |

Spring MVC cũng có `@Controller`, `@RestController`, `ResponseEntity` và hỗ trợ reactive return values trong những tình huống nhất định. Vì vậy tên annotation giống nhau **không chứng minh app chạy WebFlux**. Xem dependencies, Boot `WebApplicationType`, actual server và packages.

## 24. Các anti-pattern hay gặp

1. **Nhầm DispatcherHandler với DispatcherServlet**: khác stack và packages.
2. **Nghĩ DispatcherHandler tự gọi mọi method controller bằng reflection mà không cần adapter**.
3. **Nghĩ HandlerMapping vừa tìm vừa ghi JSON**: mapping chỉ tìm handler.
4. **Nghĩ HandlerAdapter đồng nghĩa HandlerResultHandler**.
5. **Nghĩ HandlerResult là body được ghi xong**: thực tế còn return type/metadata và writer.
6. **Nghĩ cùng default order=0 khiến ResponseEntity và ServerResponse tranh nhau**: `supports` phân biệt.
7. **Tạo custom HandlerResultHandler catch-all và order quá cao**: phá default routing/response semantics.
8. **Gắn @EnableWebFlux tùy tiện trên Boot app**: có thể thay đổi Boot customization.
9. **Thêm spring-boot-starter-web rồi tưởng app chắc chắn chạy reactive stack**.
10. **Trùng URL annotation và functional không kiểm order**: khó dự đoán route nào thắng.
11. **Chỉ kiểm URL path, quên method/consumes/produces/headers**: dẫn tới 405/415/406.
12. **Tự subscribe trong controller**: mất error/cancel/HTTP lifecycle.
13. **Gọi block() trên event loop**: chặn non-blocking processing.
14. **Dùng String return của @Controller và tưởng là raw body**: có thể là view name.
15. **Dùng @RestControllerAdvice để bắt mọi WebFilter exception**: không bao phủ mọi tầng.
16. **Cố ghi JSON lỗi sau khi SSE đã commit bytes**: status không thể luôn đổi.
17. **Coi mỗi Flux element là TCP packet**: codecs/buffering khác nhau.
18. **Không cleanup khi client cancel stream**: memory/connection leak.
19. **Dùng custom mapping không có matching adapter**: Dispatcher không thể invoke.
20. **Cho custom adapter supports mọi Object**: nuốt handler chuẩn.
21. **Không test body writer phù hợp DTO/Accept**: serialization error.
22. **Bỏ Spring Security/WebFilter khi xét flow routing**: request có thể bị chặn trước dispatcher.
23. **Dựa theo log tên SPI rồi kết luận thứ tự runtime cố định**: order phụ thuộc config.
24. **Không phân biệt reactive service thực với blocking I/O bọc trong Mono**.
25. **Tự sửa hạ tầng vì một route không được match** thay vì xem predicate/error stage.
26. **Không kiểm thử annotated và functional khi cùng app**: regression sau custom config.
27. **Không phân biệt Mono.empty do handler đã xử lý với no-handler**: adapter result và mapping là hai giai đoạn khác.
28. **Dùng status HTTP như bằng chứng DB mutation thành công** khi timeout/cancel còn race.
29. **Đặt handler logging lộ user context/headers**: rủi ro bảo mật.
30. **Bắt mọi Throwable và trả 200 để tránh lỗi log**: giấu error và khiến client nhận dữ liệu sai.

## 25. Checklist code review/production

### Routing

- [ ] Dự án thật sự dùng WebFlux starter/application type, không nhầm MVC?
- [ ] `DispatcherHandler` do WebFlux config/Boot quản lý, không tự tạo bản thứ hai?
- [ ] `HandlerMapping` đúng cho annotated và functional endpoints?
- [ ] Routes không trùng ngoài ý muốn, predicates method/headers/consumes/produces rõ?
- [ ] Custom mapping có order và no-match semantics được test?
- [ ] Security/CORS kiểm trước hoặc cùng route theo chính sách?

### Invocation và return values

- [ ] `HandlerAdapter` phù hợp handler type được mapping phát ra?
- [ ] Handler method không block event loop với JDBC/`block()`/sleep?
- [ ] `HandlerResultHandler` đúng với ResponseEntity, body DTO, ServerResponse và view?
- [ ] JSON codecs/HTTP message writers hỗ trợ DTO/media type?
- [ ] Không tự `subscribe()` trong controller?
- [ ] Streaming xử lý backpressure/cancel/cleanup đúng?
- [ ] Errors trước/sau response commit được phân biệt?

### Validation/operation

- [ ] `curl`/`WebTestClient` kiểm cả annotation lẫn functional paths?
- [ ] Test 202 ResponseEntity/status và 200 functional JSON?
- [ ] Test 404 no-route vs 404 từ handler, 405/406/415 khi áp dụng?
- [ ] Test WebFilter trước dispatcher và lỗi ngoài controller khi cần?
- [ ] Test SSE/stream completion và client disconnect với HTTP thực?
- [ ] Có metrics routing/error/codec/serialization/event-loop blocking?
- [ ] Không tùy tiện bật @EnableWebFlux/custom SPI trong Boot mà thiếu integration tests?

## 26. Câu hỏi tự kiểm tra

**1.** DispatcherHandler làm nhiệm vụ chính gì?

**Đáp án:** Là WebFlux front controller phối hợp HandlerMapping chọn request handler, HandlerAdapter gọi handler, HandlerResultHandler ghi result thành HTTP response.

**2.** HandlerMapping trả về object gì cho annotated controller?

**Đáp án:** Thường là `HandlerMethod` đại diện Java controller method đã được match, không phải JSON hay DTO.

**3.** Tại sao DispatcherHandler cần HandlerAdapter?

**Đáp án:** Handler object có nhiều kiểu như HandlerMethod, HandlerFunction, WebHandler; adapter che chi tiết cách gọi từng kiểu.

**4.** `HandlerAdapter.handle(...)` trả kiểu gì?

**Đáp án:** `Mono<HandlerResult>`, hoặc có thể hoàn thành empty khi request đã xử lý theo contract.

**5.** `HandlerResultHandler.handleResult(...)` trả kiểu gì?

**Đáp án:** `Mono<Void>` đại diện reactive operation hoàn tất việc xử lý response, không đồng nghĩa body rỗng.

**6.** Hai endpoints trả `Mono<Greeting>` và `Mono<ResponseEntity<Greeting>>` thường đi qua result handlers nào?

**Đáp án:** `ResponseBodyResultHandler` cho ordinary `@ResponseBody`, `ResponseEntityResultHandler` cho ResponseEntity.

**7.** Functional RouterFunction dùng adapter/result handler gì?

**Đáp án:** `HandlerFunctionAdapter` và `ServerResponseResultHandler` sau `RouterFunctionMapping`.

**8.** Nếu result handler cho ServerResponse và ResponseEntity đều order 0 thì xử lý thế nào?

**Đáp án:** `supports(result)` phân biệt type/metadata; không tranh mọi result chỉ vì cùng order.

**9.** Có cần tự subscribe Mono trong WebFlux controller không?

**Đáp án:** Không. Trả Mono/Flux cho HTTP infrastructure quản lý demand, serialization, error và cancellation.

**10.** Lỗi trong WebFilter có chắc vào @RestControllerAdvice không?

**Đáp án:** Không. Tùy vị trí/chain, lỗi ngoài annotated controller có thể do WebExceptionHandler xử lý.

**11.** Tại sao HTTP 404 có thể xuất hiện sau khi route đã match?

**Đáp án:** Controller/service có thể cố ý phát `ResponseStatusException(HttpStatus.NOT_FOUND)`; khác với route không tồn tại.

**12.** Có thể ghi JSON error mới sau khi streaming response đã commit không?

**Đáp án:** Không đảm bảo; phải cleanup, log/tracing, và có cơ chế báo lỗi qua stream protocol nếu cần.

**13.** Khi một custom HandlerMapping trả custom handler nhưng không adapter nào supports, chuyện gì xảy ra?

**Đáp án:** Dispatcher không có cách gọi kiểu handler đó; cần một HandlerAdapter tương ứng hoặc dùng programming model chuẩn.

**14.** Khi nào cần custom HandlerResultHandler?

**Đáp án:** Khi thực sự bổ sung return type/response strategy mới mà handlers mặc định không hỗ trợ, và đã cân nhắc codecs/ResponseEntity/ServerResponse trước.

**15.** Có thể vừa dùng annotated controller vừa dùng RouterFunction trong một WebFlux app không?

**Đáp án:** Có. Chúng cùng DispatcherHandler nhưng có mapping/adapter/result contract khác nhau.

## 27. Tài liệu tham khảo

- Spring Framework 6.2 — DispatcherHandler, SPI and result-handling order: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html
- Spring Framework 6.2 — Reactive Core / WebHandler API, filters and errors: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html
- Spring Framework 6.2 — Functional Endpoints and infrastructure beans: https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html
- Spring Framework 6.2 — WebFlux Overview: https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html
- Spring Framework — DispatcherHandler Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/DispatcherHandler.html
- Spring Framework — HandlerMapping Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/HandlerMapping.html
- Spring Framework — HandlerAdapter Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/HandlerAdapter.html
- Spring Framework — HandlerResultHandler Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/HandlerResultHandler.html
- Spring Framework — HandlerResult Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/HandlerResult.html
- Spring Framework — DispatchExceptionHandler Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/DispatchExceptionHandler.html
- Spring Framework — WebFlux configuration: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html
- Spring Framework — WebFlux annotated return values: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html
- Spring Framework — WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Project Reactor documentation: https://projectreactor.io/docs/core/release/reference/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-003">WebFilter, WebFilterChain và WebExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

# WebFilter, WebFilterChain và WebExceptionHandler trong Spring WebFlux

## 1. Mục tiêu bài học

Một ứng dụng WebFlux có thể phục vụ HTTP thông qua annotated `@RestController` hoặc functional `RouterFunction`. Trước khi đến endpoint, request thường cần authentication/authorization, correlation ID, logging an toàn, CORS, tracing, rate limiting; sau đó có thể cần chuẩn hóa một số lỗi và giải phóng tài nguyên. Spring cung cấp các thành phần hạ tầng:

- **`WebFilter`**: can thiệp **trước, xung quanh và sau** phần xử lý request ở tầng WebFlux. Nó nhận `ServerWebExchange` và một `WebFilterChain`, trả `Mono<Void>`.
- **`WebFilterChain`**: đại diện cho **phần còn lại** của pipeline, gồm các WebFilter tiếp theo và `WebHandler` đích. Gọi `chain.filter(exchange)` để chuyển tiếp.
- **`WebExceptionHandler`**: xử lý error signal từ WebFilter chain hoặc target WebHandler khi lỗi chưa được xử lý bên trong, thường bằng cách ghi HTTP status/body nếu response chưa committed.

Tất cả thuộc **Spring WebFlux**, không phải Servlet `Filter` của Spring MVC. Chúng hoạt động ở tầng `org.springframework.web.server`, bên dưới annotated/functional routing. Bài dùng **Java 17, Spring Boot 3.3.5, Spring Framework 6.1.x** và Reactor `Mono`/`Flux`.

Sau bài này, bạn sẽ:
1. Vẽ được thứ tự và trách nhiệm của `HttpHandler`, `WebHandler`, filters, exception handlers và `DispatcherHandler`.
2. Viết filter gọi chain **đúng một lần**, thực hiện logic "before", "after", "finally" và short-circuit.
3. Phân biệt lỗi được handle bởi `@ExceptionHandler` và lỗi rơi tới `WebExceptionHandler`.
4. Hiểu `@Order`, reactive signals (complete/error/cancel), response commit và giới hạn sửa status/body.
5. Dùng `ServerWebExchange` để truyền request-scoped attributes, mutate request/response trước khi commit.
6. Tránh `block()`/`subscribe()` thủ công, tiêu thụ request body hai lần và làm lộ dữ liệu nhạy cảm.
7. Viết ứng dụng chạy thật với Spring Boot WebFlux, hai filters, một custom exception handler, controller và WebTestClient tests.
8. Kiểm tra một chain low-level bằng `WebHttpHandlerBuilder` và mock HTTP objects.
9. Lựa chọn vị trí phù hợp cho Spring Security, CORS, observability, backpressure và exception policies.

## 2. Bản đồ hạ tầng WebFlux

WebFlux định nghĩa hai cấp abstraction xử lý HTTP:

- `HttpHandler.handle(ServerHttpRequest, ServerHttpResponse) -> Mono<Void>`: giao diện tối thiểu giữa Spring và HTTP server adapter.
- `WebHandler.handle(ServerWebExchange) -> Mono<Void>`: tầng web nhận một **exchange** chứa request/response và những services liên quan.

`WebHttpHandlerBuilder` lắp một `WebHandler` mục tiêu với các WebFilter, WebExceptionHandler, rồi dùng `HttpWebHandlerAdapter` để tạo một `HttpHandler` có thể nối với server.

```text
Client HTTP
   |
   v
Reactor Netty / supported reactive server adapter
   |
   v
HttpHandler
   |
   v
HttpWebHandlerAdapter
   |  tạo ServerWebExchange
   v
WebExceptionHandler chain (outer layer)
   |
   +--> WebFilter A
         |
         +--> WebFilter B
                |
                +--> target WebHandler
                       |
                       +--> DispatcherHandler (thường dùng)
                              |
                              +--> HandlerMapping
                              +--> HandlerAdapter
                              +--> HandlerResultHandler
   |
   v
Mono<Void> completion/error/cancellation
   |
   v
ServerHttpResponse commit/write
```

Các hình hộp **không** tương ứng với những thread riêng. Pipeline reactive được thực hiện khi HTTP runtime subscribe; thứ tự callback có thể chịu ảnh hưởng `flatMap`, `publishOn`, `subscribeOn` và I/O async.

Trong Spring Boot WebFlux thông thường, target WebHandler là `DispatcherHandler`. Nó dùng HandlerMapping/HandlerAdapter/HandlerResultHandler cho cả annotated controllers và WebFlux.fn endpoints. **Không cần tự tạo HttpHandler/WebHttpHandlerBuilder trong app REST thông thường**; Boot/WebFlux auto-configuration làm việc đó.

## 3. WebFilter là gì?

Package và interface:

```java
package org.springframework.web.server;

public interface WebFilter {
    Mono<Void> filter(ServerWebExchange exchange,
                      WebFilterChain chain);
}
```

Đây là **chữ ký minh họa** của API, không phải file bạn tự định nghĩa.

- `exchange` đại diện **một HTTP request/response exchange**, có `getRequest()`, `getResponse()`, `getAttributes()`, `getSession()`, `getPrincipal()`...
- `chain` là phần còn lại của pipeline. Nếu filter không gọi chain, target handler sẽ **không tự được chạy**.
- `Mono<Void>` báo completion/error/cancel cho toàn exchange. Đây không phải một DTO response; HTTP body thường do downstream viết qua `ServerHttpResponse`.
- WebFilter có thể chạy cho cả annotated `@RestController` và WebFlux.fn routes nằm dưới WebHandler của application.

### Filter tối giản

```java
@Component
public class RequestMarkerFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             WebFilterChain chain) {
        exchange.getAttributes().put("example.marker", "created");
        return chain.filter(exchange);
    }
}
```

Code là snippet đặt trong project Spring WebFlux, cần imports `Component`, `WebFilter`, `ServerWebExchange`, `WebFilterChain` và `Mono`. Khi Boot scan `@Component`, nó đăng ký filter bean và builder WebFlux có thể đưa filter vào chain.

### Một WebFilter không phải Servlet Filter

| So sánh | WebFlux `WebFilter` | Servlet `Filter` |
| --- | --- | --- |
| Package | `org.springframework.web.server` | `jakarta.servlet` |
| API | `Mono<Void> filter(ServerWebExchange, WebFilterChain)` | `void doFilter(ServletRequest, ServletResponse, FilterChain)` |
| Request context | `ServerWebExchange` | `ServletRequest/HttpServletRequest` |
| Chain delegation | `return chain.filter(exchange)` | `chain.doFilter(request,response)` |
| Completion | Reactive signals | Synchronous/async Servlet lifecycle |
| Framework | Spring WebFlux | Servlet/MVC |
| I/O | Reactive streams, có thể dùng non-blocking server adapters | Servlet I/O; có Servlet async/non-blocking APIs riêng |

**Không chỉ dựa vào tên "Filter".** WebFlux chạy trên Netty không có `HttpServletRequest` bắt buộc, nên code Servlet filter không portable sang WebFlux.

## 4. WebFilterChain điều phối bước tiếp theo

`WebFilterChain` chỉ có trách nhiệm cơ bản:

```java
public interface WebFilterChain {
    Mono<Void> filter(ServerWebExchange exchange);
}
```

**Gọi một lần, return kết quả**:

```java
return chain.filter(exchange);
```

Mỗi filter có thể chọn:
- **Tiếp tục**: `return chain.filter(exchange)`.
- **Chặn**: đặt status và `return exchange.getResponse().setComplete()`.
- **Biến đổi**: tạo exchange wrapper bằng `exchange.mutate()`, rồi truyền wrapper cho chain.
- **Bao quanh**: gắn `doOnError`, `doOnSuccess`, `doFinally`, `onErrorResume` hoặc `contextWrite` lên Mono từ chain.

### Điều gì xảy ra khi không gọi chain?

```java
if (tooManyRequests(exchange)) {
    exchange.getResponse()
            .setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    return exchange.getResponse().setComplete();
}
return chain.filter(exchange);
```

Đây là snippet có `tooManyRequests` là policy server-side. Nhánh đầu tiên **short-circuit** pipeline và không gọi controller. Cần bảo đảm filter không chỉ return `Mono.empty()` mà bỏ quên status/body, nếu không response có thể là 200 rỗng ngoài ý muốn.

### Không gọi chain hai lần

```java
// SAI: tự subscribe làm pipeline chạy ngoài lifecycle do server quản lý.
chain.filter(exchange).subscribe();
return chain.filter(exchange);
```

Hậu quả có thể là:
- Target handler xử lý hai lần, gây duplicate side effects.
- Hai subscriptions tranh nhau đọc body HTTP (thường one-shot).
- Hai writers cạnh tranh ghi status/body.
- Request cancellation, security context, tracing và error handling không còn gắn với một subscription thống nhất.

**Không `subscribe()` hoặc `block()` bên trong WebFilter chỉ để "ép" chain chạy.** Server runtime subscribe outer Publisher.

## 5. Before, after, errors và cancellation trong Reactor

Một WebFilter có thể đặt logic **trước** `chain.filter` và gắn operators cho kết quả của downstream.

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange,
                         WebFilterChain chain) {
    long startedAt = System.nanoTime();
    String route = exchange.getRequest().getPath().value();

    return chain.filter(exchange)
            .doOnError(error ->
                    log.error("Request failed: {}", route, error))
            .doFinally(signalType -> {
                long elapsedNanos =
                        System.nanoTime() - startedAt;
                metrics.record(route, elapsedNanos, signalType);
            });
}
```

Đây là **snippet** cần `log`/`metrics` do ứng dụng định nghĩa. Không dùng request raw path chứa PII/secrets làm metric tag trong production; ưu tiên route templates/low cardinality, và không log raw query/token.

### Hiểu đúng các operators

| Operator | Khi kích hoạt | Ghi nhớ |
| --- | --- | --- |
| `doOnSubscribe` | Khi subscriber bắt đầu | Không nhất thiết chạy cùng lúc tạo Mono |
| `doOnSuccess` | Mono complete thành công, với Void thường value là null | Không đồng nghĩa status HTTP là 2xx |
| `doOnError` | Mono phát error | Quan sát, **không nuốt error** |
| `doFinally` | Sau terminate/cancel | Thấy `ON_COMPLETE`, `ON_ERROR` hoặc `CANCEL` |
| `onErrorResume` | Khi phát error | Có thể thay error thành Publisher khác |
| `contextWrite` | Bổ sung Reactor Context | Subscription context, không phải exchange attributes |
| `then(...)` | Chờ upstream complete rồi chạy downstream | Cần hiểu thời điểm commit/response write |

**HTTP status là metadata, không phải Reactor error signal.** Một handler có thể trả HTTP 404 bằng `ServerResponse.notFound()` và Publisher vẫn complete thành công; `doOnError` sẽ **không** chạy. Ngược lại, có error sau khi body đã commit 200; HTTP status có thể không đổi được. Vì vậy đếm HTTP 4xx/5xx cần nhìn response status, còn đếm reactive failures cần nhìn signals — hai khái niệm khác nhau.

### Logic trước khi subscribe: defer khi cần

Nếu một phép đo hay thao tác có side effect cần chạy đúng thời điểm subscription thay vì lúc tạo Mono, dùng:

```java
return Mono.defer(() -> {
    long start = System.nanoTime();
    return chain.filter(exchange)
            .doFinally(signal ->
                    recordDuration(System.nanoTime() - start, signal));
});
```

Snippet có `recordDuration` giả định. Cách này rõ ràng về tính lazy của reactive pipeline, hữu ích trong tests/retries. Tránh ghi dữ liệu request-specific vào singleton fields.

### Đặt status/header trước khi commit

```java
exchange.getResponse().getHeaders()
        .set("X-Request-Scope", "webflux");

return chain.filter(exchange);
```

Đặt header **trước** delegation cho downstream. Nếu cần làm việc ngay trước commit, có `response.beforeCommit(...)`, nhưng vẫn không được block hoặc thay header sau khi committed.

## 6. Thứ tự nhiều WebFilter

Spring phát hiện các bean `WebFilter`, sắp xếp theo `Ordered`, `@Order` hoặc framework conventions. Trong một pipeline với:

- `@Order(-20)` — filter A.
- `@Order(-10)` — filter B.

Thông thường A là **outer** và B là **inner**; A chạy trước khi gọi B, B trước handler. Sau khi downstream complete, callback "after" có thể diễn ra theo **chiều ngược** vì các publishers lồng nhau:

```text
A before
  B before
    Handler
  B after
A after
```

Thứ tự chính xác của nhiều operators `doOnSuccess`/`doFinally` và asynchronous signal processing cần được test khi quan trọng: `doFinally` chạy khi terminate/cancel và có thể thấy thứ tự callback khác intuitive "stack unwind". Sơ đồ chỉ là model cơ bản.

### Không suy ra thứ tự chỉ từ tên class

`WebHttpHandlerBuilder.applicationContext(...)` dùng ordered discovery. Nếu app có Spring Security WebFilters, tracing filters và custom filters, order của chúng không chỉ là các con số do bạn viết. Đừng tự đặt `@Order(Ordered.HIGHEST_PRECEDENCE)` cho custom filter rồi cho rằng nó luôn nên chạy trước authentication; có thể vô tình bypass hoặc làm sai CORS/CSRF/context propagation.

**Chính sách bảo mật** nên ở Spring Security reactive chain, không ở custom filter dựa trên header do client kiểm soát. Khi thật sự cần order cụ thể, kiểm tra security chain configuration và integration tests.

### Đăng ký filter một lần

`@Component` hoặc `@Bean` thường đủ để builder phát hiện filter. Đừng đồng thời đăng ký cùng object qua cả bean auto-discovery và builder manual nếu không có chủ đích — dễ bị chạy lặp.

## 7. ServerWebExchange: dữ liệu dùng giữa filters và endpoint

`ServerWebExchange` đại diện **một cuộc trao đổi request–response**.

```java
String path = exchange.getRequest().getPath().value();
HttpMethod method = exchange.getRequest().getMethod();

exchange.getResponse().getHeaders()
        .set("X-Policy", "observed");

exchange.getAttributes().put("audit.tag", "ok");
String tag = exchange.getAttributeOrDefault(
        "audit.tag", "missing");
```

Đây là snippet cần imports `HttpMethod`, `ServerWebExchange`. Một số dữ liệu khác:
- `exchange.getPrincipal()` → `Mono<Principal>`, có thể empty.
- `exchange.getSession()` → `Mono<WebSession>`.
- `exchange.getFormData()`/`getMultipartData()` → Mono chứa parsed data.
- `exchange.getLocaleContext()` theo locale resolver.
- `exchange.getResponse().isCommitted()` để nhận biết write lifecycle.
- `exchange.mutate()` để bọc request/response.

### Exchange attributes không phải request headers

`exchange.getAttributes()` là map nội bộ **chỉ tồn tại trong exchange**, không tự được gửi sang client. Đừng dùng một key chung như `user` khiến các thư viện clash; chọn key có namespace như `com.example.trace.id`.

### Exchange attributes khác Reactor Context

| Nơi chứa | Đặc điểm | Dùng phù hợp |
| --- | --- | --- |
| HTTP request headers | Client/proxy cung cấp; có thể giả mạo | Accept, content type, correlation ID sau khi validate |
| Exchange attributes | Mutable map request-scoped | Trao dữ liệu giữa WebFilter và Handler |
| Reactor `Context` | Subscription-scoped immutable context | Reactive tracing/security propagation |
| `WebSession` | State giữa nhiều exchanges theo session policy | Session attributes khi thật sự cần |
| `ThreadLocal` | Thread-bound state | Không tự bảo toàn khi chuyển reactive scheduler |

Dùng `Mono.deferContextual(contextView -> ...)` nếu phải đọc Reactor Context từ reactive pipeline. Không lấy `Thread.currentThread().getName()` làm identity của user và không giả định MDC custom luôn truyền đúng qua event loops.

### Đọc Principal mà không block

```java
return exchange.getPrincipal()
        .map(Principal::getName)
        .defaultIfEmpty("anonymous");
```

Snippet này trả `Mono<String>`. Không gọi `exchange.getPrincipal().block()` trên event loop. Với Spring Security, reactive authentication và authorization nên dùng SecurityContext/Principal tích hợp framework; **không dùng `X-User` header do client tự gửi làm user đã đăng nhập**.

## 8. Mutate request/exchange để chuẩn hóa metadata

`exchange.mutate()` cho phép tạo wrapper view của exchange:

```java
ServerWebExchange nextExchange = exchange.mutate()
        .request(builder ->
                builder.header("X-Internal-Marker", "server-side"))
        .build();

return chain.filter(nextExchange);
```

Đây là snippet trong `WebFilter.filter(...)`. Dùng khi cần thêm header nội bộ do server kiểm soát cho downstream handler. Chỉ `return chain.filter(nextExchange)` mới truyền wrapper cho filter tiếp theo; nếu truyền exchange cũ, thay đổi có thể không đến downstream.

Cẩn thận:
- Không tạo authentication identity từ header chưa xác thực.
- Nếu thay path, routing, host hoặc scheme, ảnh hưởng lớn tới authorization và cache key; cần trust boundary/allowlist.
- Mutate metadata không tự giúp đọc, cache hoặc chuyển đổi body bytes.
- Không lưu `ServerWebExchange` vào singleton state hoặc background queue lâu dài.

## 9. WebExceptionHandler: vai trò và chữ ký

`WebExceptionHandler` nằm ở cùng tầng `org.springframework.web.server` với WebFilter:

```java
@FunctionalInterface
public interface WebExceptionHandler {
    Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
}
```

Đây là chữ ký khái niệm từ Javadoc. Một exception handler được gọi khi phía trong pipeline phát **error signal**. Nó quyết định:

1. Có xử lý loại error này không?
2. Response đã committed chưa?
3. Nếu có thể xử lý: status/headers/body nào sẽ trả client?
4. Nếu không xử lý được: trả `Mono.error(ex)` để handler kế tiếp hoặc runtime xử lý.

**Không trả `Mono.empty()` cho mọi loại lỗi** chỉ để logs sạch; điều đó có thể khiến client nhận status/body không chính xác.

### Xử lý một custom error

```java
WebExceptionHandler handler = (exchange, ex) -> {
    if (!(ex instanceof DemoFailure)) {
        return Mono.error(ex);
    }
    if (exchange.getResponse().isCommitted()) {
        return Mono.error(ex);
    }

    exchange.getResponse()
            .setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    return exchange.getResponse().setComplete();
};
```

Đây là **snippet**, `DemoFailure` là một custom exception do app định nghĩa. Nó trả 503 không body; bản runnable ở mục 13 trả một JSON error có content type rõ.

### Một WebExceptionHandler bắt lỗi từ đâu?

Nó có thể xử lý lỗi **chưa được xử lý** từ:
- `WebFilter` phía trong.
- Target `WebHandler`, thường là `DispatcherHandler`.
- Handler mapping, controller/handler invocation, response rendering nếu lỗi đi thành Publisher error và response chưa quá muộn.
- Một số lỗi lifecycle bên trong WebHandler chain.

Nó **không** đảm bảo bắt được:
- Lỗi socket/TLS ở HTTP server trước khi tạo exchange.
- Một exception đã được `@ExceptionHandler` hoặc framework handler khác xử lý.
- Một lỗi tự sinh trong detached `subscribe()` mà không thuộc outer pipeline.
- Error đã xảy ra khi HTTP response đã gửi một phần body và cannot be rewritten as normal JSON response.

### Error propagation là phần của reactive contract

```java
return chain.filter(exchange)
        .onErrorResume(MyKnownException.class, error ->
                writeKnownError(exchange, error));
```

Đây là snippet có `writeKnownError` từ app. Nó đặt error handling **trong WebFilter**, có thể ngăn error đi ra `WebExceptionHandler`. Dùng hợp lý với lỗi mà filter sở hữu; với API-wide error policy, có thể chọn một `WebExceptionHandler` được đăng ký/order đúng. Tránh hai tầng cùng bắt một exception và ghi hai lần.

## 10. WebHttpHandlerBuilder lắp filter/error chain

`WebHttpHandlerBuilder` có nhiệm vụ:
1. Lắp một target `WebHandler`, bọc bởi các `WebFilter` rồi bọc thêm các `WebExceptionHandler`.
2. Adapt chain thành `HttpHandler` bằng `HttpWebHandlerAdapter`.

Có thể tạo từ ApplicationContext:

```java
HttpHandler handler = WebHttpHandlerBuilder
        .applicationContext(applicationContext)
        .build();
```

Hoặc lắp manual:

```java
HttpHandler handler = WebHttpHandlerBuilder
        .webHandler(targetWebHandler)
        .filter(firstWebFilter, secondWebFilter)
        .exceptionHandler(myWebExceptionHandler)
        .build();
```

Đây là snippets với objects đã có; cần imports của `HttpHandler` và `WebHttpHandlerBuilder`. Bản manual không tự khởi động HTTP server. `WebTestClient.bindToHttpHandler(handler)` hoặc mock `MockServerHttpRequest/Response` có thể kiểm tra pipeline không cần mở TCP port.

Theo Spring 6.1, builder tìm các beans thường dùng:

| Loại | Cách tìm/đăng ký | Vai trò |
| --- | --- | --- |
| `WebHandler` | Thường bean tên `webHandler` | Target xử lý request; thường DispatcherHandler |
| `WebFilter` | Beans theo type/ordering | Cross-cutting interception |
| `WebExceptionHandler` | Beans theo type/ordering | Errors chưa được xử lý |
| `WebSessionManager` | Optional bean tên chuẩn | WebSession |
| `ServerCodecConfigurer` | Optional bean tên chuẩn | Form/multipart, codec integration |
| `LocaleContextResolver` | Optional bean tên chuẩn | Locale |
| `ForwardedHeaderTransformer` | Optional bean tên chuẩn | Trust-aware proxy forwarded headers |
| HTTP decorators/observation | Theo phiên bản/config | Observability hoặc wrapper |

**Không tự tạo thêm bean `webHandler` trong ứng dụng Boot REST chỉ vì muốn gắn filter.** Nó có thể thay DispatcherHandler hoặc tạo bean conflict. Dùng `WebFilter`/`WebExceptionHandler` beans là cách đúng để mở rộng phần lớn use cases.

### Ordering của exception handlers

Khi có nhiều `WebExceptionHandler`, order xác định ai được cơ hội xử lý trước. Một handler có thể xử lý custom exception rồi complete; handler kế tiếp không cần nhận nữa. Nếu không nhận diện lỗi, nó phải `Mono.error(ex)` để downstream error chain khác có thể xử lý.

Spring WebFlux cung cấp `ResponseStatusExceptionHandler` và `WebFluxResponseStatusExceptionHandler`. Spring Boot có error handler riêng; thứ tự thực tế của default/custom handlers phải được kiểm chứng theo app. **Không assume tất cả `ResponseStatusException` đều đi qua custom WebExceptionHandler** nếu annotated advice đã xử lý sớm hơn.

## 11. Response commit và giới hạn error handling

Một response thường có các giai đoạn khái niệm:
1. Đặt HTTP status/headers.
2. Chuẩn bị response body publisher.
3. Commit headers và bắt đầu ghi body.
4. Ghi dữ liệu từng phần (có thể streaming).
5. Complete hoặc fail/cancel.

`exchange.getResponse().isCommitted()` cho biết response đã committed ở thời điểm kiểm tra. Sau commit, không thể đảm bảo đổi status/header hoặc bắt đầu JSON error response mới.

### beforeCommit hook

```java
exchange.getResponse().beforeCommit(() -> {
    exchange.getResponse().getHeaders()
            .set("X-Before-Commit", "yes");
    return Mono.empty();
});

return chain.filter(exchange);
```

Đây là snippet trong WebFilter. `beforeCommit` callback được gọi trước commit khi response lifecycle đi đến đó. Đừng gọi blocking database/HTTP sync bên trong callback, và đừng đăng ký callback quá muộn. Khi side effect phụ thuộc subscription, hãy bảo đảm code trả về Publisher được thực thi đúng theo API, không thao tác ngoài chain.

### Lỗi sau partial write

Ví dụ streaming CSV:

```text
HTTP/1.1 200 OK
Content-Type: text/csv

id,name
1,Lan
2,Minh
[Flux<DataBuffer> error]
```

Client đã nhận headers và một phần body. WebExceptionHandler thường không thể biến response thành `500 application/problem+json` nguyên vẹn nữa. Thiết kế streaming protocol phải quy định broken streams, retries, checksums hoặc error events (SSE) khi phù hợp. Đừng nuốt error rồi coi CSV đã thành công.

### ResponseStatusException và @ExceptionHandler

Ở annotated controllers, `@ExceptionHandler`/`@RestControllerAdvice` có thể map lỗi nghiệp vụ thành HTTP response **trước khi** lỗi ra tầng WebExceptionHandler. Với functional routes, handler/filter có thể trả `ServerResponse` lỗi tường minh. Do đó hai error mechanisms bổ sung nhau chứ không có luật "WebExceptionHandler luôn luôn chạy với mọi exception".

## 12. Security, CORS và filter order

Spring Security WebFlux dùng các reactive filters/`SecurityWebFilterChain` để authentication/authorization. Vì vậy các request bảo vệ bởi security chain phải được xác thực trước khi business xử lý. Custom WebFilter thường **không nên** tự giải mã bearer token, suy diễn user qua `X-User`, hoặc implement quyền admin bằng path string.

### Không dùng header client làm quyền

```java
// PHẢN VÍ DỤ: client có thể tự gửi X-Role: ADMIN.
if ("ADMIN".equals(exchange.getRequest()
        .getHeaders().getFirst("X-Role"))) {
    grantAccess();
}
```

Đây là snippet không an toàn, `grantAccess` giả định. Encode/validate header không thay chữ ký, token verification hoặc role/tenant checks. Dùng Spring Security và service-level ACL/authorization checks thay thế.

### CORS không phải authentication

Browser `OPTIONS` preflight có thể đến trước request chính và thường không có credentials. Khi Security chặn preflight trước CORS processing, app có thể xuất hiện lỗi CORS dù endpoint thật hoạt động. Sử dụng Spring WebFlux CORS config/`CorsWebFilter` và tích hợp Spring Security theo tài liệu, đảm bảo origin/method/header/credentials phù hợp. Không thêm `Access-Control-Allow-Origin: *` vào mọi response có cookies.

### Forwarded headers/Host

`Forwarded`/`X-Forwarded-Host`/`X-Forwarded-Proto` có thể bị spoof nếu proxy không sanitize. Khi filter dựng redirect hoặc absolute URL từ request host chưa tin, nguy cơ host header poisoning/phishing/cache poisoning. Proxy tin cậy cần xóa incoming forwarded headers và set canonical values; ứng dụng cần trust model hoặc cấu hình canonical public origin.

### CSRF và session

Với session-cookie auth, POST/PATCH/DELETE WebFlux vẫn có CSRF risk. Không tắt CSRF chỉ vì "API reactive" hoặc muốn curl test nhanh. `WebSession` không tự biến request thành authenticated; session handling và auth policy phải rõ ràng.

## 13. Dự án Spring Boot WebFlux chạy được

Ta xây một app demo:

- `GET /api/demo/hello/{name}`: controller đọc exchange attributes từ filter.
- `GET /api/demo/async`: trả Mono phát kết quả sau một khoảng nhỏ.
- `GET /api/demo/from-controller-error`: tạo lỗi `DemoFailure` từ handler, được custom WebExceptionHandler map thành 503 JSON.
- `GET /api/demo/from-filter-error`: filter bên trong tạo cùng loại lỗi, custom WebExceptionHandler cũng xử lý.
- `GET /api/demo/blocked`: inner filter chủ động short-circuit 429, **không có controller method**, chứng minh filter có thể tự hoàn tất request.
- Hai WebFilters với thứ tự `@Order(-20)` và `@Order(-10)`.
- Một WebExceptionHandler với order `@Order(-2)` để nhận `DemoFailure` trước một số default Boot handlers.
- WebTestClient tests qua embedded server và một low-level mock chain test.

**Giới hạn demo:** Không có authentication, database hay high-load benchmark. JSON error code là chuỗi cố định từ server để tránh injection. Các chính sách rate limit chỉ minh họa bằng một path cố định, **không phải thuật toán rate limit production**.

### 13.1. Cấu trúc project

```text
webflux-filter-demo/
├── pom.xml
└── src/
    ├── main/java/demo/filters/
    │   ├── FilterDemoApplication.java
    │   ├── DemoFailure.java
    │   ├── OuterMarkerFilter.java
    │   ├── InnerPolicyFilter.java
    │   ├── DemoErrorHandler.java
    │   └── DemoController.java
    └── test/java/demo/filters/
        ├── DemoWebFluxTest.java
        └── LowLevelChainTest.java
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
    <groupId>demo</groupId>
    <artifactId>webflux-filter-demo</artifactId>
    <version>1.0.0</version>
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

Chỉ thêm `starter-webflux`, **không thêm `starter-web`**, để Boot khởi động reactive WebFlux app thông thường. Khi cả starter-web và starter-webflux cùng trên classpath, Boot thường chọn Servlet MVC trừ khi được cấu hình rõ; đừng kiểm tra một app MVC rồi gọi đó là WebFlux.

### 13.3. Boot application

**`src/main/java/demo/filters/FilterDemoApplication.java`**

```java
package demo.filters;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FilterDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilterDemoApplication.class, args);
    }
}
```

### 13.4. Exception class do ứng dụng sở hữu

**`src/main/java/demo/filters/DemoFailure.java`**

```java
package demo.filters;

public final class DemoFailure extends RuntimeException {
    public DemoFailure(String message) {
        super(message);
    }
}
```

Không dùng `IllegalArgumentException` cho tất cả lỗi vì một IllegalArgumentException có thể đại diện bug lập trình chứ không phải business/HTTP 4xx/5xx cụ thể. Ở đây `DemoFailure` được intentionally mapped thành 503 Service Unavailable.

### 13.5. Outer WebFilter: attributes và header

**`src/main/java/demo/filters/OuterMarkerFilter.java`**

```java
package demo.filters;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-20)
public class OuterMarkerFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/demo/")) {
            return chain.filter(exchange);
        }

        exchange.getAttributes().put(
                "demo.marker", "outer-filter");
        exchange.getResponse().getHeaders()
                .set("X-Demo-Outer", "visited");

        return chain.filter(exchange);
    }
}
```

Filter này:
- Chỉ thao tác với request paths dưới `/api/demo/`.
- Thêm attribute nội bộ `demo.marker`.
- Thêm response header `X-Demo-Outer` **trước khi** body commit.
- Gọi `chain.filter(exchange)` đúng một lần.
- Không đọc body, không truy cập network/DB và không tạo thread mới.
- Không phụ thuộc vào HTTP handler style: annotated/functional endpoints nếu nằm dưới exchange chain đều có thể được filter.

### 13.6. Inner WebFilter: short-circuit hoặc error signal

**`src/main/java/demo/filters/InnerPolicyFilter.java`**

```java
package demo.filters;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(-10)
public class InnerPolicyFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/demo/")) {
            return chain.filter(exchange);
        }

        exchange.getResponse().getHeaders()
                .set("X-Demo-Inner", "visited");

        if (path.equals("/api/demo/blocked")) {
            exchange.getResponse()
                    .setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        if (path.equals("/api/demo/from-filter-error")) {
            return Mono.error(new DemoFailure("filter-failed"));
        }

        return chain.filter(exchange);
    }
}
```

Nhánh `blocked` cố tình không có controller method. Client vẫn nhận `429 Too Many Requests` vì filter **tự** đặt status và complete; không cần target handler. Nhánh `from-filter-error` trả error publisher. Tầng WebExceptionHandler ở bên ngoài có thể map nó thành HTTP 503.

Quan trọng:
- Đây **không phải** rate limiting bằng key/bucket thật; path cố định chỉ chứng minh short-circuit.
- Không bắt/tự rewrite response đã committed.
- Không tự subscribe.
- Nếu app sau này dùng Spring Security, policy phải được xem lại theo reactive security chain ordering; demo không có Spring Security dependency.

### 13.7. Custom WebExceptionHandler: chỉ xử lý DemoFailure

**`src/main/java/demo/filters/DemoErrorHandler.java`**

```java
package demo.filters;

import java.nio.charset.StandardCharsets;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class DemoErrorHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            Throwable error) {

        if (!(error instanceof DemoFailure)) {
            return Mono.error(error);
        }

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        exchange.getResponse()
                .setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        // JSON cố định, không nhúng nội dung exception.
        byte[] data = "{\"code\":\"demo_unavailable\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory().wrap(data);

        return exchange.getResponse()
                .writeWith(Mono.just(buffer));
    }
}
```

**Tại sao map 503 và body cố định?**

- `DemoFailure` là một error type đã biết, policy cụ thể.
- Không để raw exception message đi thẳng vào JSON vì có thể chứa internal details.
- `WebExceptionHandler` là low-level WebFlux component: viết trực tiếp qua `ServerHttpResponse`, không giả định Jackson sẽ serialize `ApiError` cho mình.
- `isCommitted` được kiểm tra trước status/body. Kiểm tra vẫn không đảm bảo không có race nếu response được commit đồng thời; với async streaming phải có policy riêng.
- `@Order(-2)` minh họa việc tham gia error handler ordering. Trong app Boot thực, audit toàn chain/default handlers và viết integration tests; **đừng dựa chỉ vào số order như bảo chứng trong mọi Spring phiên bản**.
- Với lỗi khác, trả `Mono.error(error)` để default Spring Boot/WebFlux error mechanisms có cơ hội xử lý.

### 13.8. Controller dùng attribute và reactive publisher

**`src/main/java/demo/filters/DemoController.java`**

```java
package demo.filters;

import java.time.Duration;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/hello/{name}")
    public Mono<Map<String, String>> hello(
            @PathVariable String name,
            ServerWebExchange exchange) {

        String marker = exchange.getAttributeOrDefault(
                "demo.marker", "missing");

        return Mono.just(Map.of(
                "name", name,
                "marker", marker));
    }

    @GetMapping("/async")
    public Mono<Map<String, String>> async() {
        return Mono.delay(Duration.ofMillis(20))
                .map(ignored -> Map.of("status", "ready"));
    }

    @GetMapping("/from-controller-error")
    public Mono<String> controllerError() {
        return Mono.error(new DemoFailure("controller-failed"));
    }
}
```

**Kết quả học tập từ demo:**
- Cùng một `ServerWebExchange` đi qua WebFilter và controller; attribute `demo.marker` đọc lại được.
- `Mono.delay` dùng scheduler phát signal trong tương lai thay vì `Thread.sleep` trên event loop.
- Controller `Mono.error(DemoFailure)` cho thấy lỗi từ handler đi qua reactive error chain.
- Không có endpoint controller cho `/blocked` và `/from-filter-error`; chúng được xử lý hoàn toàn ở filter/error-handler tiers.
- Không giữ exchange vào bean field, không tự tạo request-specific mutable state trong singleton.

## 14. Chạy bằng Maven và curl

Tại thư mục `webflux-filter-demo`, cài JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Đây là **hai lệnh tuần tự**. Spring Boot mặc định lắng nghe `localhost:8080` trừ khi port/profile khác đã được cấu hình.

### 14.1. Request thành công

```bash
curl -i 'http://localhost:8080/api/demo/hello/Lan'
```

Các phần đáng chú ý:

```http
HTTP/1.1 200 OK
Content-Type: application/json
X-Demo-Outer: visited
X-Demo-Inner: visited

{"name":"Lan","marker":"outer-filter"}
```

JSON key order không phải contract; nó phụ thuộc Jackson Map serialization, không assert nguyên chuỗi body theo thứ tự.

### 14.2. Async endpoint

```bash
curl -i 'http://localhost:8080/api/demo/async'
```

Dự kiến HTTP 200, có cả hai headers từ filters, JSON:

```json
{"status":"ready"}
```

Việc dùng Mono.delay không chứng minh event-loop code của toàn app luôn non-blocking; project chỉ có publisher minh họa.

### 14.3. Filter tự short-circuit

```bash
curl -i 'http://localhost:8080/api/demo/blocked'
```

Dự kiến:

```http
HTTP/1.1 429 Too Many Requests
X-Demo-Outer: visited
X-Demo-Inner: visited
```

Body rỗng vì `setComplete()` không cung cấp JSON. Không có controller mapping cho URL này mà vẫn trả 429 — chính `InnerPolicyFilter` đã hoàn thành exchange.

### 14.4. Lỗi từ filter và controller cùng đi tới exception handler

```bash
curl -i 'http://localhost:8080/api/demo/from-filter-error'
curl -i 'http://localhost:8080/api/demo/from-controller-error'
```

Cả hai dự kiến:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
X-Demo-Outer: visited
X-Demo-Inner: visited

{"code":"demo_unavailable"}
```

Đây là contract do **custom `WebExceptionHandler`** cung cấp cho `DemoFailure`. Nếu một exception đã được annotated `@ExceptionHandler` xử lý thành response trước đó, WebExceptionHandler không bắt buộc nhận nó. Cũng không giả định default Boot error response có cùng schema.

## 15. WebTestClient integration tests với HTTP server thật

**`src/test/java/demo/filters/DemoWebFluxTest.java`**

```java
package demo.filters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DemoWebFluxTest {

    @Autowired
    WebTestClient client;

    @Test
    void bothFiltersRunBeforeAnnotatedController() {
        client.get().uri("/api/demo/hello/Lan")
              .exchange()
              .expectStatus().isOk()
              .expectHeader().valueEquals(
                      "X-Demo-Outer", "visited")
              .expectHeader().valueEquals(
                      "X-Demo-Inner", "visited")
              .expectBody()
              .jsonPath("$.marker").isEqualTo("outer-filter")
              .jsonPath("$.name").isEqualTo("Lan");
    }

    @Test
    void asyncBodyFinishes() {
        client.get().uri("/api/demo/async")
              .exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$.status").isEqualTo("ready");
    }

    @Test
    void filterCanCompleteRequestWithoutController() {
        client.get().uri("/api/demo/blocked")
              .exchange()
              .expectStatus().isEqualTo(429)
              .expectHeader().valueEquals(
                      "X-Demo-Inner", "visited");
    }

    @Test
    void errorFromFilterGoesToWebExceptionHandler() {
        client.get().uri("/api/demo/from-filter-error")
              .exchange()
              .expectStatus().isEqualTo(503)
              .expectBody()
              .jsonPath("$.code")
                  .isEqualTo("demo_unavailable");
    }

    @Test
    void errorFromControllerAlsoReachesHandler() {
        client.get().uri("/api/demo/from-controller-error")
              .exchange()
              .expectStatus().isEqualTo(503)
              .expectBody()
              .jsonPath("$.code")
                  .isEqualTo("demo_unavailable");
    }
}
```

Các tests chạy với embedded server `RANDOM_PORT`, gửi HTTP thông qua `WebTestClient`. Có **5 test methods**; chúng kiểm tra cả success/short-circuit/error flow, không chỉ gọi trực tiếp Java controller.

Điều tests **chưa** chứng minh:
- Spring Security authentication/CSRF rules (demo chưa thêm Security).
- Proxy/ingress forwarded headers đúng production.
- Backpressure và performance khi hàng nghìn slow clients.
- Lỗi socket sau khi response đã stream một phần.
- HTTP status/body đồng nhất giữa mọi Spring Boot versions.

## 16. Low-level unit test cho WebFilterChain và WebExceptionHandler

Để quan sát cách builder lắp tầng web khi **không có controller**, dùng một `WebHandler` custom với filter/error handler manual. Không cần khởi động Netty server.

**`src/test/java/demo/filters/LowLevelChainTest.java`**

```java
package demo.filters;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LowLevelChainTest {

    @Test
    void manualChainRunsFilterThenWebHandler() {
        WebHandler target = exchange -> {
            String marker = exchange.getAttributeOrDefault(
                    "marker", "missing");

            exchange.getResponse()
                    .setStatusCode(HttpStatus.OK);
            exchange.getResponse().getHeaders()
                    .setContentType(MediaType.TEXT_PLAIN);

            DataBuffer buffer = exchange.getResponse()
                    .bufferFactory()
                    .wrap(marker.getBytes(StandardCharsets.UTF_8));

            return exchange.getResponse()
                    .writeWith(Mono.just(buffer));
        };

        WebFilter filter = (exchange, chain) -> {
            exchange.getAttributes()
                    .put("marker", "filter-was-here");
            return chain.filter(exchange);
        };

        HttpHandler handler = WebHttpHandlerBuilder
                .webHandler(target)
                .filter(filter)
                .build();

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/raw").build();
        MockServerHttpResponse response =
                new MockServerHttpResponse();

        handler.handle(request, response)
                .block(Duration.ofSeconds(2));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("filter-was-here",
                response.getBodyAsString()
                        .block(Duration.ofSeconds(2)));
    }

    @Test
    void manualExceptionHandlerCanCatchFilterError() {
        WebHandler target = exchange ->
                exchange.getResponse().setComplete();

        WebFilter broken = (exchange, chain) ->
                Mono.error(new DemoFailure("expected"));

        HttpHandler handler = WebHttpHandlerBuilder
                .webHandler(target)
                .filter(broken)
                .exceptionHandler(new DemoErrorHandler())
                .build();

        MockServerHttpRequest request =
                MockServerHttpRequest.get("/raw-error").build();
        MockServerHttpResponse response =
                new MockServerHttpResponse();

        handler.handle(request, response)
                .block(Duration.ofSeconds(2));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode());
        assertEquals("{\"code\":\"demo_unavailable\"}",
                response.getBodyAsString()
                        .block(Duration.ofSeconds(2)));
    }
}
```

Test class có **2 test methods**. Cộng với 5 tests ở mục 15 là **7 test methods**. Nếu project được tạo đúng và `mvn test` thực thi tất cả thành công, Maven exit code là 0 và thường thấy tổng tương tự `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` (cách báo cáo theo plugin/test runners có thể khác).

**Vì sao `block()` có mặt trong test?** Đây là unit test chạy ngoài event loop, cần đợi completion của một Mono trên mock exchange để assert body; việc blocking **chỉ phục vụ test**. Trong WebFilter/controller production không có `block()` hoặc `subscribe()` thủ công.

**Giới hạn test:** Lớp mock HTTP không tái hiện mọi đặc điểm socket/I/O, timeouts và WebFlux controller metadata. Test manual chain bổ sung cho integration tests, không thay thế chúng.

## 17. Viết custom WebFilter hiệu quả và an toàn

### 17.1. Không đọc request body hai lần

`ServerHttpRequest.getBody()` là `Flux<DataBuffer>`, thường đại diện one-shot network stream. Cách sai:

```java
// PHẢN VÍ DỤ: tự subscribe raw body và để handler đọc lần nữa.
exchange.getRequest().getBody()
        .subscribe(buffer -> logRaw(buffer));
return chain.filter(exchange);
```

Đây là pseudocode vì `logRaw` chưa có. Nó có thể:
- Tiêu thụ body trước controller decoder, khiến `@RequestBody` nhận dữ liệu rỗng/lỗi.
- Tạo hai subscriptions cạnh tranh.
- Làm lộ password/token/PII trong logs.
- Rò rỉ pooled DataBuffer nếu không release.
- Mất backpressure/cancellation của outer request pipeline.

Nếu thực sự cần audit body, ưu tiên instrumentation được framework hỗ trợ, hoặc xây decorator/buffer policy có **giới hạn bytes**, bảo toàn backpressure và release ownership. Đừng mặc định một WebFilter được phép log mọi HTTP body.

### 17.2. Avoid blocking I/O trong filter

```java
// PHẢN VÍ DỤ: blocking JDBC trên WebFlux event loop.
User user = jdbcRepository.findById(id);
return chain.filter(exchange);
```

Đây là snippet minh họa, `jdbcRepository`/`id` do ứng dụng giả định. Chữ ký `Mono<Void>` không khiến đoạn JDBC đồng bộ thành non-blocking.

Nếu cần thư viện blocking tạm thời:

```java
return Mono.fromCallable(() -> blockingPolicyService.evaluate(id))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(allowed -> {
            if (!allowed) {
                exchange.getResponse()
                        .setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        });
```

Đây là snippet có service/id imports chưa định nghĩa. `boundedElastic` **offload việc block sang bounded thread pool**, không biến driver thành non-blocking. Bảo vệ pool size, queue, downstream timeout và transaction/context. Với hệ thống reactive thực sự, ưu tiên auth/policy lookup dùng driver/client non-blocking.

### 17.3. Không giữ request/exchange vào singleton fields

`@Component` WebFilter thường singleton và phục vụ nhiều requests đồng thời. Không làm:

```java
private ServerWebExchange currentExchange;
```

rồi gán trong filter để dùng về sau. Request A và B có thể ghi đè state, gây rò rỉ dữ liệu và giữ request resources sau completion. Dùng biến local, exchange attributes, Reactor Context và request-specific structures cần cleanup.

### 17.4. Reactive Context và MDC

Dữ liệu trace/user/security không nên dựa chỉ vào custom `ThreadLocal` vì reactive work có thể chuyển scheduler. Sử dụng Reactor Context/context propagation của Spring/Micrometer/Spring Security; nếu map với MDC, luôn restore/clear phù hợp. `exchange.getAttributes()` có thể dùng để pass một marker nội bộ nhưng không thay full distributed tracing context.

### 17.5. Metrics cardinality

Một filter muốn ghi request count theo path không nên dùng raw `/users/1000001`, `/users/1000002` làm mỗi tag mới. Nên dùng **route template** `/users/{id}` khi sẵn có và thận trọng với thời điểm route mapping được resolve. Không log query có access tokens hoặc values mang PII.

### 17.6. Cancellation

`doFinally(signalType -> ...)` nhận `CANCEL` khi subscriber cancel. Client disconnect thường dẫn tới cancellation ở một số cấp reactive pipeline, nhưng **không mọi** kiểu network failure đều quan sát y hệt `CANCEL`. Hãy cleanup resources trong finally/dispose/usingWhen đúng contract, không chỉ `doOnSuccess`.

## 18. CORS, tracing và auth: nên gắn ở đâu?

| Nhu cầu | Vị trí phù hợp | Không nên làm |
| --- | --- | --- |
| Authentication/authorization | Spring Security reactive `SecurityWebFilterChain` + service ACL | Tự tin `X-Role` client |
| CORS | Spring WebFlux CORS config/`CorsWebFilter` đúng thứ tự với security | Wildcard allow-origin + credentials |
| Correlation/trace | Observation/Micrometer + Reactor Context | Copy raw `ThreadLocal` tùy tiện |
| Request header policy | WebFilter có phạm vi rõ | Set sau commit |
| Rate limiting | Gateway/server-side shared policy với key tin cậy | Short-circuit dựa trên user-supplied "admin" header |
| Domain errors | Controller advice/functional handler mapping tùy scope | WebExceptionHandler bắt mọi Exception rồi trả 400 |
| Infrastructure errors | WebExceptionHandler có thứ tự và fallback đúng | Bỏ qua error khi response đã committed |
| Request body logging | Approved redacted bounded instrumentation | subscribe body trước converter |
| Logging status/latency | Observation hoặc WebFilter có cleanup | High-cardinality tags, log secrets |
| HTTP response transform | Codec/result handler hoặc decorater phù hợp | Edit headers sau commit |

### Vì sao không dùng một filter cho tất cả?

Một filter ôm auth, CORS, multipart parsing, cache, tracing, error serialization và metrics sẽ:
- Khó kiểm soát order và trust boundaries.
- Dễ tạo hai subscriptions/đọc body lặp.
- Cản trở unit/integration tests.
- Dễ nuốt exceptions hoặc làm sai status.
- Phức tạp khi migrate Spring Security/Boot phiên bản.

Dùng filter nhỏ, trách nhiệm rõ, composition qua Spring container và test các hợp đồng.

## 19. Phân tích sự khác nhau giữa 401/403/404/429 và lỗi publisher

**HTTP 401 Unauthorized**: client chưa được xác thực đúng, thường do authentication entry point ở Spring Security xử lý, có thể cần `WWW-Authenticate` theo scheme.

**HTTP 403 Forbidden**: client có identity nhưng không đủ quyền (hoặc CSRF rules), thường do authorization policy.

**HTTP 404 Not Found**: không có route/resource phù hợp; có thể là một response hoàn toàn bình thường, Mono complete thành công.

**HTTP 429 Too Many Requests**: policy rate limit chủ động short-circuit; có thể thêm `Retry-After` nếu có semantics rõ.

**HTTP 503 Service Unavailable**: backend/dependency tạm không sẵn sàng; trong demo do `DemoFailure` custom exception handler trả về.

**Reactive error signal**: publisher thất bại. Signal này **không tự xác định status**; handler/error mapping quyết định status trước khi response commit.

Một ứng dụng nên phân biệt:
- HTTP response status metrics.
- Exceptions/error signals.
- Client cancellation.
- Downstream 5xx errors từ WebClient (có thể là ordinary ResponseEntity nếu dùng exchangeToMono và xử lý status chủ động).
- Body streaming failures sau commit.

Không coi mọi 4xx là Java exception và không coi mọi Mono.error tự động thành 500 JSON cùng schema.

## 20. WebExceptionHandler, @ControllerAdvice và functional router error handling

Cùng một app WebFlux có thể kết hợp:
- Annotated controller `@ExceptionHandler`/`@RestControllerAdvice`.
- Functional router `HandlerFilterFunction` hoặc explicit `ServerResponse` cho lỗi domain.
- Outer `WebExceptionHandler` cho lỗi còn thoát ra hạ tầng WebHandler chain.
- Spring Boot `ErrorWebExceptionHandler` mặc định cho các exceptions khác.

**Thứ tự logic:** nếu exception đã được một tầng bên trong chuyển thành HTTP response, tầng bên ngoài thường chỉ nhìn completion bình thường; nó không có lý do nhận cùng exception lần nữa. Nếu tầng bên trong để error propagate, WebExceptionHandler có thể nhận. Nếu đã commit một phần response, việc biến đổi body/status có giới hạn.

### Không nuốt lỗi không nhận diện

```java
if (!(error instanceof MyExpectedError)) {
    return Mono.error(error);
}
```

Dòng này có vai trò quan trọng trong custom WebExceptionHandler: **để những handlers/fallbacks khác xử lý**. Nếu luôn trả `Mono.empty()` cho mọi exception, request có thể trông như thành công hoặc chứa response body dang dở mà không có status thích hợp.

### Không lộ nội dung Throwable

`error.getMessage()` có thể chứa secrets, SQL fragments, internal hostnames hoặc stack details. Error response ra public cần code/messages đã sanitize. Logs nội bộ dùng trace ID, security-safe context và structured logging, tránh dump auth headers/raw request body.

## 21. Debugging checklist

Khi filters hoặc custom exception handling hoạt động không như kỳ vọng:

1. **Xác định đúng stack**: Boot có đang chạy WebFlux reactive hay vô tình MVC do starter-web?
2. Xác nhận imports `org.springframework.web.server.WebFilter`/`WebFilterChain`/`WebExceptionHandler`, không phải Servlet Filter.
3. Kiểm tra @Component/@Bean được component scan, không tạo hai registrations.
4. Xem order qua `@Order`/`Ordered`, đặc biệt khi có Spring Security/CORS.
5. Đặt marker/header trong hai filters để chứng minh request đi qua thứ tự mong muốn.
6. Kiểm tra filter có **return** `chain.filter(exchange)` đúng một lần không.
7. Với request 429/403, xác định filter đã short-circuit và target controller không gọi.
8. Với error, kiểm tra `Mono.error` có propagate hay đã được `onErrorResume` xử lý.
9. Kiểm tra `@ControllerAdvice` có xử lý trước WebExceptionHandler hay không.
10. Xem `exchange.getResponse().isCommitted()` khi custom handler không thay status.
11. Nếu body trống, tìm code subscribe/read `getBody()` từ một WebFilter.
12. Nếu app chậm, tìm `block()`/JDBC sync trên event loops và queue overload.
13. Nếu response headers không có, kiểm tra thời điểm commit hoặc filter scope.
14. Nếu 404 dù route tồn tại, xem predicate/method/path, handler mapping và prefix.
15. Nếu CORS preflight bị 401/403, xem WebFlux CORS/Security order và OPTIONS policy.
16. Nếu memory tăng, xem buffers, sessions, long-lived publishers và cancellation cleanup.
17. Với forwarded headers, kiểm tra proxy sanitize/trust policy tránh Host injection.
18. Test app với WebTestClient và sau đó qua reverse proxy/runtime giống production.

## 22. Những lỗi phổ biến

| Triệu chứng | Nguyên nhân | Sửa thế nào |
| --- | --- | --- |
| Không thấy filter chạy | Import nhầm Servlet filter, bean không scan, sai path scope | Dùng đúng WebFlux WebFilter và xác minh bean |
| Filter bị gọi lặp | Tự subscribe chain hoặc đăng ký hai lần | Return chain duy nhất, đăng ký một bean |
| Controller không chạy | Filter quên `chain.filter(exchange)` hoặc short-circuit | Xem nhánh return, test 429 intentional |
| WebExceptionHandler không bắt lỗi | Error đã được advice xử lý hoặc detached subscription | Kiểm tra propagation, bỏ manual subscribe |
| WebExceptionHandler chạy nhưng không đổi status | Response đã committed | Error handling sớm; streaming error policy |
| HTTP 404 nhưng `doOnError` không chạy | 404 là response bình thường | Đọc response status, không chỉ error signal |
| `Mono<Void>` đã return mà request đứng | Publisher không complete/never callback | Kiểm tra subscription, timeouts và producer |
| CPU cao | Blocking database/call trong event loop | Non-blocking client hoặc bounded offload |
| Request body bị mất | WebFilter đọc one-shot body trước decoder | Không double-read; decorator đúng |
| Response bị ghi hai lần | Filter và handler cùng write/subscribe | Một chain một writer theo policy |
| Error body lộ SQL exception | Trả raw Throwable message | Sanitized API errors |
| `@Order` làm mất auth | Đặt custom filter trước/ngoài policy không kiểm chứng | Spring Security integration + tests |
| Filter không nhìn thấy principal | Chưa authenticated hoặc Mono empty | Reactive SecurityContext/Principal |
| Mất trace ID | Dùng ThreadLocal ngoài Reactor Context | Supported context propagation |
| Session creation nhiều | Filter gọi/ghi session không cần thiết | Chỉ dùng session khi thực sự cần |
| Header mất | Đặt sau commit | Set trước hoặc beforeCommit |
| CORS lỗi | Security chặn OPTIONS/preflight | Config CORS đúng thứ tự |
| Mutable field lộ data user | Singleton filter giữ state request | Dùng local/exchange attributes |
| Netty buffer leak | Không release retained DataBuffer | DataBufferUtils.release đúng ownership |
| Retry gây duplicate POST | Retry publisher từ filter khi endpoint có side effect | Dùng idempotency contract, tránh retry bừa |
| Unexpected 200 empty body | Nuốt error bằng `Mono.empty()` hoặc setComplete thiếu status | Map error/status rõ |
| Slow client làm memory tăng | Buffer không giới hạn, thiếu backpressure | Bounded buffering, cancellation, observe |
| WebFilter khác GatewayFilter | Nhầm Spring Cloud Gateway-specific API | Phân biệt WebFlux vs Gateway |
| Test pass nhưng production error status khác | Default Boot handler/version khác, proxy/commit | Integration test on actual stack |

## 23. Checklist triển khai production

- [ ] Đúng `spring-boot-starter-webflux` và reactive application context.
- [ ] WebFilter beans được scan, order rõ, không duplicate.
- [ ] Mỗi nhánh hoặc **return chain once** hoặc **short-circuit + response completion**.
- [ ] Không gọi `block()`/`subscribe()` thủ công trên event-loop request pipeline.
- [ ] Không log body/Authorization/Cookie hoặc request ID nhạy cảm.
- [ ] Attributes namespaced; không lưu request-specific data vào singleton fields.
- [ ] Header/status được set trước response commit.
- [ ] WebExceptionHandler chỉ bắt error loại biết, giữ fallback cho error khác.
- [ ] Error JSON không leak raw Throwable và status phù hợp.
- [ ] Streaming errors và cancellation có cleanup riêng.
- [ ] SecurityWebFilterChain xử lý authentication/authorization đúng.
- [ ] CORS/preflight/CSRF/session policy đúng khi có credentials.
- [ ] Hạn chế forwarded headers và Host trust boundary.
- [ ] Giới hạn body/multipart, pooled DataBuffers và backpressure.
- [ ] Metrics theo route template, không tạo high-cardinality tags.
- [ ] WebTestClient kiểm chứng success, block, filter error và handler error.
- [ ] Low-level tests dành cho custom infrastructure, không thay integration tests.
- [ ] Downstream timeouts, rate limiting, retries, circuit breaker và observability được thiết kế riêng.

## 24. Bài tập tự kiểm tra

1. `WebFilter.filter` nhận đối số nào? Trả kiểu gì?
2. `WebFilterChain.filter(exchange)` đại diện cho phần xử lý nào?
3. Khi nào một filter không nên gọi `chain.filter`?
4. Khác biệt giữa HTTP 404 response và `Mono.error` là gì?
5. Vì sao WebExceptionHandler không thể luôn trả 500 JSON khi lỗi xuất hiện giữa stream?
6. `@Order(-20)` và `@Order(-10)` ảnh hưởng gì tới nesting chain?
7. Vì sao không tự `subscribe()` trên Mono của chain?
8. `doFinally` khác `doOnError` thế nào?
9. `ServerWebExchange.getAttributes()` khác Reactor Context?
10. `getPrincipal()` là async vì lý do gì?
11. Nếu filter muốn gắn header trước commit, cần làm gì?
12. `WebExceptionHandler` nhận error từ cả WebFilter và WebHandler được không?
13. Khi nào nó không bắt error do controller phát ra?
14. Vì sao custom exception handler nên trả `Mono.error(ex)` cho errors không được nhận diện?
15. Servlet `Filter` và WebFlux `WebFilter` khác API nào?
16. Có nên triển khai authentication từ `X-User` client header trong custom filter?
17. CORS có thay thế quyền truy cập được không?
18. Vì sao raw body publisher thường không được đọc hai lần?
19. `DataBuffer` pooled có thể gây leak ở đâu?
20. Bằng cách nào test WebFilter và custom WebExceptionHandler mà không mở port?
21. Tại sao phải phân biệt `setComplete()` với viết response JSON?
22. `Mono.delay` có chứng minh tất cả nghiệp vụ non-blocking không?
23. Vì sao phải test cancel khi server stream dữ liệu lâu?
24. Khi thêm WebFilter, tại sao không nên tự định nghĩa lại bean `webHandler`?
25. Số lượng HTTP 5xx có luôn bằng số Mono.error signals không?

**Gợi ý:** (1) Exchange + chain, Mono<Void>. (2) Các filters tiếp theo và WebHandler cuối. (3) Khi đã short-circuit hợp lệ. (4) Một cái response status; cái kia error signal. (5) Đã commit/ghi body. (6) Outer/inner theo thứ tự. (7) Runtime subscribe và cần một pipeline. (8) doFinally còn thấy cancellation. (9) Request map vs subscription context. (10) Reactive auth/context services. (11) Đặt sớm hoặc beforeCommit. (12) Có nếu lỗi propagate. (13) Đã được advice/inner handler xử lý. (14) Cho fallback khác xử lý. (15) Reactive Mono và ServerWebExchange vs Servlet doFilter. (16) Không. (17) Không. (18) One-shot network stream. (19) Decorators/caching/subscribe lặp không release. (20) WebHttpHandlerBuilder + mock requests/responses. (21) setComplete không có body. (22) Không. (23) Giải phóng subscriptions/buffers/connections. (24) Dễ thay DispatcherHandler. (25) Không.

## 25. Tài liệu tham khảo

- [Spring Framework — WebFlux Reactive Core: WebHandler API, Filters and Exceptions](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html)
- [Spring Framework 6.1 — WebFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/WebFilter.html)
- [Spring Framework 6.1 — WebFilterChain Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/WebFilterChain.html)
- [Spring Framework 6.1 — WebExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/WebExceptionHandler.html)
- [Spring Framework 6.1 — WebHttpHandlerBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/adapter/WebHttpHandlerBuilder.html)
- [Spring Framework 6.1 — DefaultWebFilterChain Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/handler/DefaultWebFilterChain.html)
- [Spring Framework 6.1 — ServerWebExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/ServerWebExchange.html)
- [Spring Framework — WebFlux DispatcherHandler](https://docs.spring.io/spring-framework/reference/web/webflux/dispatcher-handler.html)
- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html)
- [Spring Security — Reactive Web Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html)
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html)
- [Project Reactor — Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)

**Kết luận:** `WebFilter` can thiệp quanh reactive HTTP exchange, `WebFilterChain` tiếp tục các filters và WebHandler đích, còn `WebExceptionHandler` xử lý **error signals chưa được xử lý** ở tầng web khi response vẫn có thể thay đổi. Ba abstraction này giúp tổ chức logging, policy, error handling và observability mà không phụ thuộc việc endpoint dùng annotated controller hay WebFlux.fn. Muốn đúng và an toàn, phải **return một publisher duy nhất**, không tự subscribe/block, kiểm soát order/security, hiểu response commit và body ownership, và test riêng success, short-circuit, errors, cancellation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-004">ServerHttpRequest, ServerHttpResponse và dữ liệu request/response</a>

<details>
<summary>Xem chi tiết</summary>

# ServerHttpRequest, ServerHttpResponse và dữ liệu request/response trong Spring WebFlux

## 1. Vì sao cần hiểu hai giao diện HTTP cấp thấp?

Một controller Spring WebFlux thường nhận `@RequestBody`, `@RequestHeader`, `@RequestParam` rồi trả `Mono<DTO>` hoặc `ResponseEntity<DTO>`. Cách viết đó rất phù hợp cho API nghiệp vụ. Nhưng khi xây **WebFilter**, hạ tầng logging/tracing, gateway, custom error handling, response headers, streaming hoặc middleware cần can thiệp vào giao thức HTTP, bạn sẽ gặp hai abstraction:

- **`ServerHttpRequest`**: request đầu vào của server, từ method, URI, path, headers và cookies đến **body dạng `Flux<DataBuffer>`**.
- **`ServerHttpResponse`**: response server đang xây, gồm status, headers, cookies và các phương thức **ghi body bất đồng bộ** như `writeWith`, `writeAndFlushWith` và `setComplete`.

Trong WebFlux, hai đối tượng này được kết nối qua **`ServerWebExchange`** — ngữ cảnh của **một tương tác HTTP request/response**. Hiểu chúng giúp bạn tránh các lỗi khó thấy: đọc body hai lần, ghi status sau khi response đã commit, rò rỉ pooled buffers, trả request body qua thread blocking hoặc cache nhầm dữ liệu riêng tư.

Ví dụ đọc metadata:

~~~java
@GetMapping("/inspect")
public Mono<Map<String, String>> inspect(
        ServerHttpRequest request,
        ServerHttpResponse response) {

    response.getHeaders().set("Cache-Control", "no-store");
    String method = request.getMethod().name();
    String path = request.getPath().pathWithinApplication().value();

    return Mono.just(Map.of("method", method, "path", path));
}
~~~

Đây là **fragment** Spring WebFlux: cần đặt trong một `@RestController` cùng các imports. Không có câu lệnh blocking; Spring WebFlux có thể chọn converter để ghi JSON từ giá trị Mono. Response headers phải được đặt **trước khi response được commit**, không phải sau khi body gửi xong.

### Kết quả học tập

Sau bài bạn sẽ:
1. Phân biệt `ServerHttpRequest`, `ServerHttpResponse`, `ServerWebExchange`, `ServerRequest` và các đối tượng Servlet MVC.
2. Đọc method/URI/path/query/headers/cookies/client address mà không đánh đồng dữ liệu client với danh tính tin cậy.
3. Hiểu luồng body `Flux<DataBuffer>`: chỉ tiêu thụ có chủ đích, phối hợp codecs, giới hạn bộ nhớ, reference-counting và cancellation.
4. Ghi status, headers, `ResponseCookie` và body bằng các methods phản ứng của `ServerHttpResponse`.
5. Hiểu `beforeCommit`, `isCommitted` và khác biệt giữa `writeWith`, `writeAndFlushWith`, `setComplete`.
6. Dùng `WebFilter` để đặt exchange attributes, biến đổi request có kiểm soát, gắn response headers.
7. Viết ứng dụng Java 17 / Spring Boot 3.3.5 với endpoint metadata, JSON body, raw body bị giới hạn kích thước, cookies và response completion.
8. Kiểm bằng `WebTestClient` response cuối, bao gồm mã 201, 204, 413, echo bytes và headers.
9. Chẩn đoán các lỗi về security, content type, proxy headers, logging, body consumption và throughput.

Bài dùng **Spring WebFlux trên Reactor Netty**, không phải Servlet MVC. Các ví dụ runnable dùng Spring Framework 6.1.x do Spring Boot 3.3.5 quản lý; với Spring Framework 7, kiểm tài liệu API tương ứng.

## 2. Bức tranh kiến trúc HTTP trong WebFlux

~~~text
HTTP client
   |
   v
Reactor Netty / HTTP server adapter
   |
   v
ServerHttpRequest ---------+
                           |  ServerWebExchange
ServerHttpResponse --------+
   |
   v
WebFilter chain
   |
   v
WebHandler / DispatcherHandler
   |
   +-- HandlerMapping
   +-- HandlerAdapter (annotated / functional endpoints)
   +-- Codec: HttpMessageReader / HttpMessageWriter
   |
   v
ServerHttpResponse.writeWith / setComplete
   |
   v
HTTP client
~~~

Spring WebFlux triển khai một **`HttpHandler`** hướng framework-neutral để làm việc với Reactive HTTP server adapters; thường có `WebHttpHandlerBuilder` kết hợp WebFilter, WebExceptionHandler, session và các thành phần cần thiết. Lớp chọn handler có thể là `DispatcherHandler` cho annotated controllers hay functional routes.

### 2.1. Các package phải phân biệt

| Kiểu | Package quan trọng | Vai trò |
| --- | --- | --- |
| `ServerHttpRequest` | `org.springframework.http.server.reactive` | HTTP request server cấp thấp |
| `ServerHttpResponse` | `org.springframework.http.server.reactive` | HTTP response server cấp thấp |
| `ServerWebExchange` | `org.springframework.web.server` | Ngữ cảnh request + response + attributes + session |
| `WebFilter` | `org.springframework.web.server` | Middleware reactive trước/sau handler |
| `ServerRequest` | `org.springframework.web.reactive.function.server` | Request API tiện dùng cho functional WebFlux endpoint |
| `ServerResponse` | `org.springframework.web.reactive.function.server` | Response builder của functional WebFlux |
| `HttpServletRequest` | `jakarta.servlet.http` | Servlet stack, không phải WebFlux core |
| `org.springframework.web.servlet.function.ServerRequest` | Servlet WebMvc.fn | Functional Servlet MVC, **khác** reactive ServerRequest |

Trong cùng chương trình có thể có `WebClient`, nhưng `ClientRequest`/`ClientResponse` là abstraction **outbound client**, không phải hai đối tượng server đang nghiên cứu.

### 2.2. Vì sao ServerWebExchange quan trọng?

Một exchange cho phép truy cập:

~~~java
ServerHttpRequest request = exchange.getRequest();
ServerHttpResponse response = exchange.getResponse();
Map<String, Object> attributes = exchange.getAttributes();
~~~

Ngoài ra có `exchange.getSession()` trả `Mono<WebSession>`, thông tin principal `getPrincipal()`, locale, conditional request support như `checkNotModified(...)` và utilities khác. **Exchange attributes** là dữ liệu gắn với một request/response interaction; chúng không tự là global store hoặc user session.

Không lưu toàn bộ `ServerWebExchange` vào static collection hoặc queue dài hạn: exchange có thể giữ request body, response handles, headers, context và các tham chiếu ngoài vòng đời HTTP.

## 3. `ServerHttpRequest`: đọc các phần của request

### 3.1. HTTP method

~~~java
HttpMethod method = request.getMethod();
~~~

Đối tượng `HttpMethod` biểu diễn GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS... Đừng coi method là proof rằng request an toàn: một GET có tác dụng thay đổi dữ liệu do implementation sai vẫn có thể gây side effect; ngược lại POST có thể cần idempotency key và kiểm quyền.

### 3.2. URI và đường dẫn

~~~java
URI uri = request.getURI();
RequestPath path = request.getPath();
String relative = path.pathWithinApplication().value();
String fullPath = path.value();
~~~

Mã fragment cần `java.net.URI` và `org.springframework.http.server.RequestPath`. `getURI()` trả URI request theo server abstraction; `getPath()` là path API có thể xét context path. `pathWithinApplication()` thường dùng khi route application cần đường dẫn bên trong ứng dụng.

**Đừng tự parse path bằng `uri.toString().split("/")`**: encoded characters, context path, dấu `?`, slash normalization và gateway rewrites làm cách đó sai. Với annotated controller, `@PathVariable` thường thích hợp hơn trong business handler.

### 3.3. Query parameters

~~~java
MultiValueMap<String, String> query = request.getQueryParams();
String q = query.getFirst("q");
List<String> tags = query.get("tag");
~~~

`getQueryParams()` đã đưa tham số URI query vào một cấu trúc hỗ trợ nhiều giá trị cùng tên. Ví dụ:

~~~http
GET /api/search?q=java&tag=reactor&tag=spring HTTP/1.1
~~~

thì `q` là `"java"`, `tags` có hai phần tử. `getFirst` lấy giá trị đầu tiên, không có nghĩa những giá trị khác đã được validate.

**Quan trọng:** query string là dữ liệu không đáng tin. Giới hạn length/count, chuẩn hóa sort/filter values, validate và không đưa raw query vào SQL, logs có bí mật hoặc metric labels. Nếu client gửi `?role=admin`, điều đó không tự có giá trị phân quyền.

### 3.4. Request headers

~~~java
HttpHeaders headers = request.getHeaders();
MediaType contentType = headers.getContentType();
String language = headers.getFirst("Accept-Language");
String requestId = headers.getFirst("X-Request-Id");
~~~

`HttpHeaders` có thể chứa multi-value headers; `getFirst` chỉ đọc một giá trị. Header names theo HTTP không phân biệt hoa thường, nhưng header **values có semantics riêng**, ví dụ value của Authorization là credential bí mật.

Những headers quan trọng:

| Header | Ý nghĩa thông dụng | Thực hành an toàn |
| --- | --- | --- |
| `Content-Type` | Media type của request body | Kiểm phù hợp với codec/endpoint |
| `Accept` | Representation mà client có thể nhận | Dùng produces/negotiation khi cần |
| `Authorization` | Credential hoặc bearer token | Không log, xác thực qua Spring Security |
| `Cookie` | State client gửi | Không suy ra user/tenant chỉ bằng tên cookie |
| `Origin` | Browser origin cho CORS | CORS không thay authorization |
| `If-None-Match` | HTTP conditional request | Check ETag đúng tài nguyên/quyền |
| `Forwarded`, `X-Forwarded-*` | Metadata do proxy có thể thêm | Chỉ tin proxy chain được cấu hình |
| `Content-Length` | Declared body length nếu có | Không phải bằng chứng body thực tế sẽ đúng |
| `Traceparent` / correlation ID | Distributed tracing | Không dùng raw arbitrary values làm metric label |

### 3.5. Cookies

~~~java
MultiValueMap<String, HttpCookie> cookies = request.getCookies();
HttpCookie session = cookies.getFirst("SESSION");
~~~

`HttpCookie` chỉ mô tả cookie từ request. Session cookie có thể được kiểm bằng Spring Security/session management, nhưng controller không nên tự đọc `SESSION` rồi coi value là authenticated user.

Ví dụ demo sẽ chỉ trả **boolean có/không có cookie tên `demo`**, không đưa raw value vào JSON hoặc logs. Với dữ liệu thật, cookies mang authentication phải có HTTPS, `Secure`, `HttpOnly` và `SameSite` phù hợp threat model.

### 3.6. Client address và forwarded headers

~~~java
InetSocketAddress remote = request.getRemoteAddress();
InetSocketAddress local = request.getLocalAddress();
~~~

Đây là socket-layer information hoặc giá trị đã được adapter/transformer xử lý tùy hạ tầng. Sau CDN/reverse proxy, `getRemoteAddress()` có thể là địa chỉ proxy, **không nhất thiết client thật**. Đừng tin mọi `X-Forwarded-For` do client tự gửi, vì attacker có thể spoof IP.

Khi cần xây absolute URLs hoặc áp dụng IP allowlist/rate-limit, phải cấu hình trusted proxy/forwarded-header handling chính xác, giới hạn proxy chain được tin và kiểm ở ingress. Đừng tự lấy phần tử đầu tiên của `X-Forwarded-For` rồi coi đó là định danh chắc chắn.

### 3.7. Request ID

`request.getId()` cung cấp một ID để hỗ trợ đối chiếu logs cho request theo implementation. Không coi nó là durable transaction ID, session ID hoặc token bảo mật. Hệ tracing thường có trace/span IDs độc lập. Nếu dùng `request.getLogPrefix()` hoặc exchange log prefix, chúng phục vụ correlation, không biến một header do client gửi thành nguồn tin cậy.

## 4. Dữ liệu body không nằm trong `getHeaders()`

Trên WebFlux, `ServerHttpRequest.getBody()` trả:

~~~java
Flux<DataBuffer> body = request.getBody();
~~~

`DataBuffer` biểu diễn phần bytes do server adapter phát qua reactive stream. **Một DataBuffer không nhất thiết là một JSON object, HTTP chunk hay một message hoàn chỉnh.** TCP fragmentation, Netty read events và codec buffering có thể khiến body được cắt thành nhiều buffers tùy thời điểm.

Vì thế:
- Không giả định `getBody().next()` là **toàn bộ body**.
- Không đọc `getBody()` ở WebFilter rồi để controller `@RequestBody` đọc lại như thể `Flux` có khả năng replay mặc định.
- Không gọi `getBody().blockFirst()` trên event loop.
- Không dùng `buffer.collectList()` vô hạn cho user-upload không giới hạn.
- Không biến `DataBuffer` thành `String` bằng `buffer.toString()` mà không chỉ định cách decode, charset và release.
- Khi trực tiếp thao tác pooled `DataBuffer`, cần hiểu **quyền sở hữu/release** để tránh rò rỉ native memory.

### 4.1. Body thường được đọc đúng một lần

Trên socket HTTP thật, request body là **nguồn streaming**. Trong nhiều pipeline không có bảo đảm khả năng đọc lại sau lần consume đầu tiên. Ví dụ WebFilter logging:

~~~java
return DataBufferUtils.join(exchange.getRequest().getBody())
        .doOnNext(buffer -> logBody(buffer))
        .then(chain.filter(exchange));
~~~

Đây là **anti-pattern**, fragment có hàm `logBody` giả định. Nó có thể tiêu thụ body trước handler, làm mất dữ liệu controller cần; `join` không giới hạn có thể làm tăng memory; logging có thể lộ passwords/tokens; chưa kể reference-counted buffers cần release.

Nếu thực sự phải log/inspect body, cần request decorator phát **một stream có khả năng replay** được quản lý bộ nhớ và cleanup nghiêm ngặt, giới hạn bytes, redaction, cancellation và không bao giờ cache/log nội dung nhạy cảm tùy tiện. Thông thường chỉ log method/path/status/latency hoặc schema-level metadata là an toàn và đủ dùng hơn.

### 4.2. Vì sao codec cấp cao tốt hơn cho JSON?

Với annotated WebFlux controller:

~~~java
@PostMapping(
        path = "/api/notes",
        consumes = MediaType.APPLICATION_JSON_VALUE)
public Mono<NoteOut> create(@RequestBody Mono<NoteIn> input) {
    return input.map(this::convert);
}
~~~

Đây là fragment có `NoteIn`, `NoteOut`, `convert` chưa định nghĩa. Spring WebFlux dùng `HttpMessageReader`/JSON decoder để parse `Flux<DataBuffer>` thành đối tượng. Framework thường quản lý release/aggregation theo implementation, áp dụng memory limit codec, parse media type và truyền lỗi decoding đến error layer. Bạn vẫn cần Bean Validation hoặc validation domain rõ ràng.

**Không đọc `request.getBody()` trong một filter và `@RequestBody` lại trong controller** nếu chưa có lớp cache/decorator được thiết kế đúng. Cũng không tự subscribe request body ngoài pipeline bằng `subscribe()`.

## 5. `DataBuffer`: quản lý bytes và bộ nhớ

### 5.1. DataBuffer không chỉ là byte array

Spring cung cấp `DataBuffer` abstraction có `readableByteCount()`, `read(byte[])`, `write` và các chỉ số đọc/ghi. Tùy runtime, implementation có thể dựa trên Netty pooled `ByteBuf` với reference counting hoặc bộ nhớ heap. **Đừng assume GC Java sẽ tự giải phóng mọi direct/native buffers khi bạn đánh rơi reference.**

### 5.2. Cách nối body với giới hạn rõ

Khi thật sự cần một body nhỏ (ví dụ demo raw echo), có thể:

~~~java
Mono<byte[]> bytes = DataBufferUtils
        .join(request.getBody(), 1024)
        .map(buffer -> {
            try {
                byte[] copy = new byte[buffer.readableByteCount()];
                buffer.read(copy);
                return copy;
            } finally {
                DataBufferUtils.release(buffer);
            }
        })
        .defaultIfEmpty(new byte[0]);
~~~

Đây là fragment dùng `ServerHttpRequest request` và imports `DataBufferUtils`/`Mono`. `join(...,1024)` giới hạn số bytes được tổng hợp; quá giới hạn phát `DataBufferLimitException`. Nếu stream lỗi/hủy trước khi joined buffer phát ra, `join` chịu trách nhiệm release phần đã tích lũy theo contract; khi nhận thành công một joined buffer để tự đọc, đoạn `finally` **release kết quả**.

Nếu nội dung có thể lớn, **không** gom tất cả vào `byte[]`. Với file upload, sử dụng streaming resource/`FilePart.transferTo`, storage API phù hợp, server/gateway limits và validation trên luồng.

### 5.3. Chuyển buffer sang String phải biết charset

~~~java
String text = new String(bytes, StandardCharsets.UTF_8);
~~~

Fragment này giả định `bytes` là `byte[]` đã được copy đúng. Không decode từng DataBuffer bằng UTF-8 rồi nối String nếu một multi-byte character bị cắt giữa buffers: decoder phải xử lý boundary đúng hoặc aggregate trong giới hạn. Với JSON/text, thường để codec của Spring làm việc này.

### 5.4. Release / retain / ownership

- Khi bạn **nhận và tiêu thụ** một pooled `DataBuffer` ở application level (không chuyển cho framework writer), bạn có thể cần `DataBufferUtils.release(buffer)`.
- Nếu bạn đưa publishers/buffers trực tiếp vào `response.writeWith(...)`, response writer quản lý luồng ghi và quyền sở hữu theo contract; không release trước khi body được viết xong.
- Nếu bạn muốn replay/copy/slice shared pooled buffers, việc `retain`/`release` cần cân bằng theo lifecycle; đừng giữ reference khi nguồn đã release.
- Trên error/cancel, phải kiểm resource cleanup ở các operators; `doOnDiscard(DataBuffer.class, DataBufferUtils::release)` có thể có ích ở pipeline cụ thể, nhưng không thêm cơ học khi một thành phần khác đang sở hữu buffer.
- Không viết mã vừa release buffer trong `doOnNext` **vừa** chuyển cùng object xuống `writeWith`: có thể gây use-after-release.

**Ranh giới rõ:** body bytes được copy thành `byte[]` trong demo; sau khi release joined buffer, ứng dụng sử dụng **bản sao** để tạo response buffer mới.

### 5.5. Giới hạn body: nhiều tầng khác nhau

| Giới hạn | Tầng áp dụng | Lưu ý |
| --- | --- | --- |
| Reverse proxy/CDN maximum request body | Infrastructure | Có thể chặn request trước khi vào Spring |
| Reactor Netty server config | HTTP server | Các giới hạn liên quan decoder/headers/form behavior |
| `spring.codec.max-in-memory-size` | Codec aggregation | Áp dụng cho một số codec/buffer decode, không phải mọi custom raw pipeline |
| `DataBufferUtils.join(body,maxByteCount)` | Code tự aggregate | Bảo vệ lần gom buffer cụ thể |
| Validation DTO/payload | Domain/service | Giới hạn field count, string length, types |
| Multipart/file storage limits | Upload pipeline | Chú ý disk quota, temporary files, streaming |

**Không tin mỗi header `Content-Length`** để bảo vệ memory: client có thể dùng chunked transfer hoặc gửi sai/trùng thông tin, tùy HTTP server xử lý. Giới hạn phải được áp dụng trong quá trình đọc và ở infrastructure.

## 6. `ServerHttpResponse`: một response có vòng đời commit

`ServerHttpResponse` cho phép:
- `setStatusCode(HttpStatusCode)`: chọn HTTP status nếu response chưa commit; trả boolean biểu thị khả năng set trong API.
- `getStatusCode()`: trạng thái hiện tại có thể null nếu chưa set tường minh, tùy abstraction.
- `getHeaders()`: thêm/sửa response headers trước commit.
- `getCookies()` và `addCookie(ResponseCookie)`: outbound cookies.
- `beforeCommit(Supplier<Mono<Void>>)`: đăng ký hành động trước commit.
- `isCommitted()`: response đã ở trạng thái committed theo abstraction?
- `bufferFactory()`: tạo DataBuffer cho output.
- `writeWith(Publisher<? extends DataBuffer>)`: ghi body reactive.
- `writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>>)`: ghi các nhóm buffers và flush theo mức publisher bên trong.
- `setComplete()`: đánh dấu output hoàn tất, không có body tự tạo.

### 6.1. Status và headers nên được quyết định sớm

~~~java
response.setStatusCode(HttpStatus.ACCEPTED);
response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
response.getHeaders().setCacheControl("no-store");
~~~

Đây là fragment với response trong controller/filter. Gọi `setStatusCode` không tự viết ngay bytes xuống socket; việc commit xảy ra khi publisher response được xử lý. **Một khi response đã commit, không thể tin rằng thay status hay headers sẽ có hiệu lực.** Trong ứng dụng đa filter, luôn kiểm response cuối bằng integration test.

### 6.2. `ResponseCookie` không giống request `HttpCookie`

Để gửi cookie:

~~~java
ResponseCookie cookie = ResponseCookie.from("demo", "ok")
        .path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .build();

response.addCookie(cookie);
~~~

Trên HTTPS production, nếu cookie cần đi qua TLS, cân nhắc `secure(true)` và cookie scope/domain/path. `HttpOnly` làm JavaScript không đọc cookie qua document.cookie; `SameSite` ảnh hưởng browser cross-site sending; **không cái nào thay authentication/authorization và CSRF protection**.

Đừng viết cookie bearer token hoặc session ID vào URL để debug. Headers `Set-Cookie` có thể lộ bí mật nếu log raw; lọc/redact chúng ở proxies và application logging.

### 6.3. `beforeCommit` chạy vào lúc nào?

~~~java
response.beforeCommit(() ->
        Mono.fromRunnable(() ->
                response.getHeaders().set("X-Commit", "ready")));
~~~

`beforeCommit` **đăng ký hành động**, không có nghĩa thực thi ngay. Callback nên trả một `Mono<Void>` **được defer** (ví dụ `Mono.fromRunnable` hoặc `Mono.defer`), vì response commit machinery sẽ subscribe đúng thời điểm và đúng thứ tự với callbacks khác. Nếu làm side-effect ngay khi tạo supplier hoặc trả một Mono đã eager complete, bạn có thể ghi header sai thời điểm.

**Đừng gọi `block()`, DB/JDBC synchronous, HTTP client đồng bộ hoặc sleep trong beforeCommit callback**: nó có thể đang chạy trong I/O/event-loop thread. Chỉ thực hiện việc nhẹ, deterministic và không cần network.

### 6.4. `isCommitted` không phải công cụ rollback

~~~java
if (!response.isCommitted()) {
    response.setStatusCode(HttpStatus.BAD_REQUEST);
}
~~~

Kiểm tra này có thể hữu ích để quyết định xử lý lỗi, nhưng race vẫn có thể xảy ra khi luồng response đang commit. Không dùng nó để hứa rằng status đổi chắc chắn thành công; `setStatusCode` có kết quả boolean và response writes/errors cần được điều phối theo pipeline.

Một khi HTTP headers/body đã gửi đi, bạn thường **không thể** thay status 200 thành 500 rồi ghi JSON ProblemDetail tùy ý; đặc biệt với SSE và streaming response.

### 6.5. `setComplete()`

~~~java
response.setStatusCode(HttpStatus.NO_CONTENT);
return response.setComplete();
~~~

Phù hợp response **204 No Content** không có body. `setComplete()` trả `Mono<Void>`, phải **return** cho WebFlux hoặc compose vào pipeline để framework thực thi. Chỉ gọi method rồi bỏ qua Mono chưa bảo đảm work hoàn tất.

## 7. Ghi response body: `writeWith` và `writeAndFlushWith`

### 7.1. `writeWith`

~~~java
byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
response.getHeaders().setContentType(
        new MediaType("text", "plain", StandardCharsets.UTF_8));

return response.writeWith(Mono.fromSupplier(
        () -> response.bufferFactory().wrap(bytes)));
~~~

Fragment này thuộc handler trả `Mono<Void>`. `Mono.fromSupplier` trì hoãn việc tạo output buffer đến subscription; `writeWith` nhận publisher của DataBuffer và ghi ra HTTP transport.

Không cần `subscribe()` hay `block()`; controller trả Mono để WebFlux quản lý lifecycle. Nếu truyền `Flux<DataBuffer>` từ request input sang response output, cần biết ai sở hữu/releases từng buffer và có giới hạn body phù hợp.

### 7.2. `writeAndFlushWith`

Khi output có nhiều nhóm buffers và bạn muốn điều khiển các flush boundaries theo nested publishers:

~~~java
Flux<Publisher<DataBuffer>> groups = ...;
return response.writeAndFlushWith(groups);
~~~

Đây là **pseudocode** với `groups` chưa được định nghĩa; không phải yêu cầu sử dụng khi trả JSON thông thường. Nó thích hợp hơn cho một số trường hợp streaming; nhưng flushing phụ thuộc server/transport và không cam kết mỗi item đến client thành một TCP packet riêng.

Đừng dùng writeAndFlushWith cho một response JSON nhỏ chỉ vì tên có chữ “flush”. HTTP codecs hoặc WebFlux `ResponseEntity<Flux<...>>`/SSE có thể là API phù hợp hơn cho streaming ở controller.

### 7.3. `Mono<Void>`, `Mono<T>` và `ResponseEntity`

| Handler return type | Ai quyết định body/status? |
| --- | --- |
| `Mono<DTO>` | WebFlux chọn writer/codec, thường status 200 nếu thành công |
| `Mono<ResponseEntity<DTO>>` | Khi Mono emit, status, headers, body từ ResponseEntity được áp dụng |
| `ResponseEntity<Mono<DTO>>` | Status/headers có sẵn sớm hơn, body bất đồng bộ |
| `Mono<Void>` sau `ServerHttpResponse.writeWith(...)` | Application tự ghi body vào response |
| `Mono<Void>` sau `ServerHttpResponse.setComplete()` | Application hoàn tất response không có body |

Đừng trộn hai chủ sở hữu response: nếu controller đã gọi `response.writeWith`, đừng đồng thời trả `Mono<ResponseEntity<?>>` từ cùng method để có hai luồng cố ghi body/status. Hãy chọn **một** cách.

## 8. `ServerWebExchange` và request attributes

Một filter có thể đặt metadata **chỉ cho request này**:

~~~java
exchange.getAttributes().put("demo.observed", Boolean.TRUE);
return chain.filter(exchange);
~~~

Handler sau đó đọc:

~~~java
Boolean observed =
        exchange.getAttribute("demo.observed");
~~~

Attributes là Map trong exchange:
- Phù hợp đánh dấu đã kiểm tra request, correlation metadata hoặc routing state nội bộ.
- Không tự được gửi sang client; muốn response header phải set riêng.
- Không giống session hoặc cookie; không sống qua nhiều request độc lập.
- Không giống `Reactor Context`: việc một reactive subscription đổi thread không tự biến attribute thành ThreadLocal.
- Không phải nơi an toàn để cache nhiều bytes hoặc secrets; đừng giữ body copies không giới hạn.

### 8.1. WebSession khác exchange attributes

`exchange.getSession()` trả `Mono<WebSession>`. WebSession có lifecycle/persistence riêng tùy configured store. Chỉ **truy cập** session không nhất thiết tạo session mới với cookie ngay; việc ghi attributes/khởi tạo session mới có thể làm phát sinh persistence/cookie. Đừng dùng request-scoped attributes để thay session, và đừng dùng session làm cache toàn bộ HTTP body.

### 8.2. Principal/security context

`exchange.getPrincipal()` trả reactive principal của request theo security integration. Với Spring Security Reactive, `ReactiveSecurityContextHolder` và Reactor Context mới là vị trí đáng tin để truy cập authentication đúng pipeline. Không tự gán principal bằng một `X-User` header trừ khi header chỉ đến từ trusted authentication proxy với chain được bảo vệ và verified.

## 9. `WebFilter`: cắt ngang HTTP request/response một cách an toàn

`WebFilter` có signature:

~~~java
Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain);
~~~

Nó hoạt động quanh handler và có thể:
- Kiểm metadata method/path/header trước handler.
- Đặt exchange attributes.
- Chỉnh response headers **trước commit**.
- Tạo response ngắn và dừng chain khi có một quyết định được phép tại layer này.
- Đo duration với `doFinally` mà không cần block.

### 9.1. Cách gắn response header đúng commit phase

~~~java
@Bean
WebFilter traceFilter() {
    return (exchange, chain) -> {
        exchange.getAttributes().put("demo.observed", true);

        exchange.getResponse().beforeCommit(() ->
                Mono.fromRunnable(() ->
                        exchange.getResponse().getHeaders().set(
                                "X-Demo-Filter", "observed")));

        return chain.filter(exchange);
    };
}
~~~

Code này gần hoàn chỉnh, cần imports `@Bean`, `WebFilter` và `Mono`/class `@Configuration` ở demo. **Không đọc body ở filter**; response header được set khi commit nên áp dụng cả response bình thường và nhiều error responses nếu commit còn có thể làm được.

### 9.2. Vì sao `then(Mono.fromRunnable(...))` có thể quá muộn?

Một pattern dễ sai:

~~~java
return chain.filter(exchange)
        .then(Mono.fromRunnable(() ->
                exchange.getResponse().getHeaders()
                        .set("X-Late-Header", "value")));
~~~

Với nhiều handlers, chain hoàn thành **sau khi response đã được ghi/commit**, vì vậy viết header ở `then` có thể không còn tác dụng hoặc gặp trạng thái read-only. Muốn header ngay trước commit, dùng `beforeCommit`.

### 9.3. Không xử lý body tùy tiện ở filter

Muốn chỉnh request headers/URI bằng một request wrapper, có thể dùng builder:

~~~java
ServerHttpRequest request = exchange.getRequest().mutate()
        .header("X-Internal-Demo", "checked")
        .build();

ServerWebExchange updated =
        exchange.mutate().request(request).build();

return chain.filter(updated);
~~~

Đây là fragment thuộc WebFilter; chỉ gắn header nội bộ khi bạn có trust boundary rõ. Đối với header đã tồn tại và không đáng tin, hãy **remove/replace** theo API phù hợp thay vì thêm một giá trị thứ hai rồi tin `getFirst`: duplicate headers có thể gây request smuggling hoặc nhầm identity nếu mỗi tầng chọn một value khác nhau.

Để **đổi body** thực sự, cần `ServerHttpRequestDecorator` override `getBody()` cùng buffer retain/release, backpressure, content length và cleanup đúng. Không thay body mà giữ `Content-Length` cũ hoặc content encoding không tương thích.

### 9.4. Filter order và Spring Security

Ứng dụng có Spring Security reactive sẽ có `SecurityWebFilterChain` và các WebFilters cần thứ tự rõ. Filter tự tạo không được coi là thay thế authorization/CSRF/CORS. Việc decorate request/response không tự thay quyền của user.

Nếu filter tự trả 401/403, phải bảo đảm response format, auth challenge, correlation header và không leak private details; tốt hơn dùng Spring Security exception handlers/entry points đúng trách nhiệm.

## 10. Servlet và WebFlux: những điểm tương tự dễ nhầm

| Chủ đề | Servlet MVC | WebFlux |
| --- | --- | --- |
| Request low-level | `HttpServletRequest` | `ServerHttpRequest` |
| Response low-level | `HttpServletResponse` | `ServerHttpResponse` |
| Chỗ nối request/response | Servlet request + response arguments | `ServerWebExchange` |
| Body | `ServletInputStream` hoặc argument resolver | `Flux<DataBuffer>` hoặc reactive codecs |
| Filter | `jakarta.servlet.Filter` | `WebFilter` |
| Controller input body | `@RequestBody DTO` | `@RequestBody Mono<DTO>` hoặc DTO tùy contract |
| Body response low-level | `ServletOutputStream` | `writeWith(Publisher<DataBuffer>)` |
| Completion | Servlet return/async lifecycle | `Mono<Void>` hoàn tất |
| Buffer ownership | Servlet streams/server implementation | DataBuffer lifecycle cần chú ý release |
| Async dispatch | Servlet `DispatcherType.ASYNC` có thể áp dụng | Reactor/Reactive server pipeline, không đồng nhất với MVC async dispatch |

Không import `org.springframework.web.servlet.function.ServerRequest` vào WebFlux hoặc cố tạo `HttpServletRequest` ở một ứng dụng Reactor Netty không có Servlet container.

**Một điểm đáng nhớ:** "Reactive HTTP server" không có nghĩa bạn được phép xử lý body blocking bằng `.block()`. `ServerHttpRequest.getBody().collectList().block()` trên event loop vừa chặn thread vừa gom memory không giới hạn.

## 11. Ví dụ hoàn chỉnh: Spring Boot 3.3.5 / Java 17 / WebFlux

Ứng dụng demo có năm routes:
1. `GET /api/request`: đọc method, path, query `q`, kiểm có custom header/cookie, đọc exchange attribute do WebFilter đặt; response có `Cache-Control: no-store`.
2. `POST /api/notes`: body JSON thành record qua `@RequestBody Mono<NoteInput>`, trả HTTP 201 với DTO, không đọc raw DataBuffer.
3. `POST /api/raw-echo`: đọc `ServerHttpRequest.getBody()` **có giới hạn 1024 bytes**, copy rồi release joined DataBuffer; tạo response body UTF-8 bằng `ServerHttpResponse.writeWith`, trả 413 nếu vượt.
4. `GET /api/commit`: đặt status 204, `beforeCommit` thêm header, `setComplete` trả Mono.
5. `GET /api/cookie`: dùng `ResponseCookie` để phát Set-Cookie với HttpOnly, SameSite và Path.

Mỗi route được WebFilter gắn `X-Demo-Filter: observed` vào thời điểm commit. Dữ liệu demo không cần database, tài khoản hoặc network dependency. Không thêm Spring Security starter trong project ví dụ; **production cần security phù hợp**.

~~~text
webflux-http-foundation/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/httpfoundation/
    │   ├── HttpFoundationApplication.java
    │   ├── web/ExchangeObservationFilter.java
    │   └── web/HttpFoundationController.java
    └── test/java/com/example/httpfoundation/
        └── HttpFoundationIntegrationTest.java
~~~

### 11.1. Gradle build

`settings.gradle`:

~~~groovy
rootProject.name = 'webflux-http-foundation'
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

Chỉ dùng `spring-boot-starter-webflux`, không thêm `spring-boot-starter-web`. Nếu cả hai starters có mặt, Boot có thể chọn MVC application type theo mặc định; điều đó làm sai mục tiêu demo Reactor Netty WebFlux.

### 11.2. Main application

`src/main/java/com/example/httpfoundation/HttpFoundationApplication.java`:

~~~java
package com.example.httpfoundation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpFoundationApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpFoundationApplication.class, args);
    }
}
~~~

### 11.3. WebFilter ghi exchange attribute và header trước commit

`src/main/java/com/example/httpfoundation/web/ExchangeObservationFilter.java`:

~~~java
package com.example.httpfoundation.web;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ExchangeObservationFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange, WebFilterChain chain) {

        exchange.getAttributes().put("demo.observed", Boolean.TRUE);

        exchange.getResponse().beforeCommit(() ->
                Mono.fromRunnable(() ->
                        exchange.getResponse().getHeaders().set(
                                "X-Demo-Filter", "observed")));

        return chain.filter(exchange);
    }
}
~~~

Filter này **không** đụng body, không block, không tự subscribe. Nó đặt marker vào exchange và đăng ký callback ghi header trước commit. Trong ứng dụng thật, nếu filter cần quan sát status/timeouts, cân nhắc `doFinally`, tracing libraries và bảo đảm correlation data không chứa secrets.

### 11.4. Controller: request metadata, JSON body, bounded raw body, commit, cookie

`src/main/java/com/example/httpfoundation/web/HttpFoundationController.java`:

~~~java
package com.example.httpfoundation.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class HttpFoundationController {

    public record RequestSnapshot(
            String method, String path, String query,
            boolean hasDemoHeader, boolean hasDemoCookie,
            boolean observed) {}

    public record NoteInput(String text) {}

    public record NoteOutput(String text, int length) {}

    @GetMapping(path = "/request",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RequestSnapshot> inspect(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String q = request.getQueryParams().getFirst("q");
        String safeQuery = q == null ? ""
                : q.substring(0, Math.min(q.length(), 64));

        response.getHeaders().setCacheControl("no-store");

        return Mono.just(new RequestSnapshot(
                request.getMethod().name(),
                request.getPath().pathWithinApplication().value(),
                safeQuery,
                request.getHeaders().containsKey("X-Demo-Client"),
                request.getCookies().containsKey("demo"),
                Boolean.TRUE.equals(
                        exchange.getAttribute("demo.observed"))));
    }

    @PostMapping(path = "/notes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<NoteOutput>> notes(
            @RequestBody Mono<NoteInput> input) {

        return input.map(note -> {
            String text = note.text();
            if (text == null || text.isBlank()
                    || text.length() > 120) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid note text");
            }

            String normalized = text.trim();
            NoteOutput output =
                    new NoteOutput(normalized, normalized.length());

            return ResponseEntity.created(URI.create("/api/notes/1"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(output);
        });
    }

    @PostMapping(path = "/raw-echo",
            consumes = MediaType.TEXT_PLAIN_VALUE)
    public Mono<Void> rawEcho(
            ServerHttpRequest request, ServerHttpResponse response) {

        response.getHeaders().setContentType(
                new MediaType(
                        "text", "plain", StandardCharsets.UTF_8));
        response.getHeaders().setCacheControl("no-store");

        Mono<byte[]> content =
                DataBufferUtils.join(request.getBody(), 1024)
                        .map(buffer -> {
                            try {
                                byte[] bytes =
                                        new byte[buffer.readableByteCount()];
                                buffer.read(bytes);
                                return bytes;
                            } finally {
                                DataBufferUtils.release(buffer);
                            }
                        })
                        .defaultIfEmpty(new byte[0]);

        return content
                .flatMap(bytes -> response.writeWith(
                        Mono.fromSupplier(() ->
                                response.bufferFactory().wrap(bytes))))
                .onErrorResume(DataBufferLimitException.class, error -> {
                    response.setStatusCode(
                            HttpStatus.valueOf(413));
                    return response.setComplete();
                });
    }

    @GetMapping("/commit")
    public Mono<Void> commit(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.NO_CONTENT);
        response.beforeCommit(() ->
                Mono.fromRunnable(() ->
                        response.getHeaders().set(
                                "X-Commit-Phase", "ready")));
        return response.setComplete();
    }

    @GetMapping("/cookie")
    public Mono<Void> cookie(ServerHttpResponse response) {
        response.addCookie(ResponseCookie.from("demo", "ok")
                .path("/")
                .httpOnly(true)
                .sameSite("Lax")
                .build());
        response.setStatusCode(HttpStatus.NO_CONTENT);
        return response.setComplete();
    }
}
~~~

### 11.5. Giải thích đúng ownership trong raw echo

`rawEcho` là phần quan trọng nhất:
1. Chỉ endpoint này gọi `request.getBody()` trực tiếp; không có `@RequestBody` cạnh tranh cùng body.
2. `DataBufferUtils.join(...,1024)` giới hạn aggregate bytes. Quá 1024 có thể gây `DataBufferLimitException`.
3. `map(buffer -> {... finally release(buffer)})` **copy** bytes ra mảng Java rồi giải phóng joined DataBuffer.
4. `response.writeWith(Mono.fromSupplier(...wrap(bytes)))` tạo một **output buffer mới** và để response writer tiêu thụ.
5. Khi body trống, `defaultIfEmpty(new byte[0])` bảo đảm có đường kết thúc phù hợp, không cố viết JSON.
6. Nếu aggregation vượt giới hạn trước response commit, `onErrorResume` chọn 413 và complete response.
7. Không tự gọi `subscribe`, `block` hoặc `Thread.sleep`; Spring quản lý reactive lifecycle.
8. Đây là ví dụ cho **body nhỏ**. Với file upload/streaming thật, không aggregate lên heap, không echo thông tin nhạy cảm, dùng storage/stream APIs có request-size limits ở ingress và application.

**Lưu ý về Java memory:** copy sang `byte[]` nghĩa là dữ liệu vẫn tạm tồn tại trong heap, dù pooled buffer gốc được release. Không giữ hoặc log byte array nếu chứa credentials/PII. `no-store` ở response chỉ yêu cầu HTTP caches tuân thủ không lưu, không tự xóa mọi bản copy ở heap/application stores.

### 11.6. Vì sao `@RequestBody Mono<NoteInput>` không cần DataBufferUtils.join?

Ở `/api/notes`, WebFlux JSON reader parse body giúp bạn. Bạn chỉ thao tác DTO:
- `@RequestBody Mono<NoteInput>` nhận JSON asynchronous.
- Input không hợp lệ sẽ phát lỗi từ decoder hoặc từ `ResponseStatusException` trong `map`.
- `ResponseEntity.created(...)` đặt 201, Location và Cache-Control rõ; không gọi `ServerHttpResponse.writeWith` thủ công.
- Vì demo không có DB, Location `/api/notes/1` chỉ là minh họa contract, **không tồn tại một endpoint GET notes/1**. Trong production không trả 201/Location giả mà không lưu tài nguyên; cần repository và ID thực, hoặc chọn response khác.

Nếu muốn validation tự động, có thể dùng Bean Validation với Spring validation starter và `@Valid` cho DTO, nhưng cần test input constraints, media type và error schema. Ở demo logic kiểm text ngắn gọn và không cần thêm dependency.

### 11.7. Vì sao `/api/commit` dùng 204 và không ghi body?

HTTP 204 No Content không nên chứa response payload. `setComplete` trả Mono để Spring thực thi commit, gọi `beforeCommit` và hoàn tất. Gọi `writeWith` song song với `setComplete` trên cùng response sẽ tạo hai paths tranh nhau commit; tránh điều đó.

### 11.8. Cookie an toàn còn phụ thuộc HTTPS và auth

Cookie demo tên `demo` không chứa secret; `httpOnly(true)` và `sameSite("Lax")` chỉ minh họa response cookie builder. Với session cookie trên HTTPS production, thường cần `secure(true)`, policy SameSite đúng cross-site flows, rotation, expiration và CSRF protection phù hợp. Đừng tin client có cookie thì tự có quyền đọc tài nguyên.

## 12. Kiểm thử WebFlux với WebTestClient

`WebTestClient` là test HTTP client phù hợp Spring WebFlux, có thể bind vào application context hoặc kết nối embedded server. Ở đây dùng `RANDOM_PORT` để yêu cầu đi qua Reactor Netty thực, giúp kiểm response body/headers sau commit.

`src/test/java/com/example/httpfoundation/HttpFoundationIntegrationTest.java`:

~~~java
package com.example.httpfoundation;

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
class HttpFoundationIntegrationTest {

    @Autowired
    WebTestClient client;

    @Test
    void inspectUsesRequestMetadataAndExchangeAttribute() {
        client.get().uri("/api/request?q=hello")
                .header("X-Demo-Client", "yes")
                .cookie("demo", "ok")
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                    .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectHeader()
                    .valueEquals("X-Demo-Filter", "observed")
                .expectBody()
                .jsonPath("$.method").isEqualTo("GET")
                .jsonPath("$.path").isEqualTo("/api/request")
                .jsonPath("$.query").isEqualTo("hello")
                .jsonPath("$.hasDemoHeader").isEqualTo(true)
                .jsonPath("$.hasDemoCookie").isEqualTo(true)
                .jsonPath("$.observed").isEqualTo(true);
    }

    @Test
    void jsonBodyIsDecodedAndResponseStatusIs201() {
        client.post().uri("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", "Spring WebFlux"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader()
                    .valueEquals(HttpHeaders.LOCATION, "/api/notes/1")
                .expectHeader()
                    .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.text").isEqualTo("Spring WebFlux")
                .jsonPath("$.length").isEqualTo(14);
    }

    @Test
    void invalidJsonDomainValueIsRejected() {
        client.post().uri("/api/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("text", "  "))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void smallRawBodyIsCopiedAndReturned() {
        client.post().uri("/api/raw-echo")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hello")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.TEXT_PLAIN)
                .expectBody(String.class).isEqualTo("hello");
    }

    @Test
    void rawBodyLimitIsEnforced() {
        client.post().uri("/api/raw-echo")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("a".repeat(1200))
                .exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void beforeCommitRunsAndNoContentHasNoBody() {
        client.get().uri("/api/commit")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader()
                    .valueEquals("X-Commit-Phase", "ready")
                .expectHeader()
                    .valueEquals("X-Demo-Filter", "observed")
                .expectBody().isEmpty();
    }

    @Test
    void responseCookieContainsSecurityAttributes() {
        client.get().uri("/api/cookie")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE, ".*demo=ok.*")
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE, ".*HttpOnly.*")
                .expectHeader().valueMatches(
                        HttpHeaders.SET_COOKIE, ".*SameSite=Lax.*");
    }
}
~~~

**Kiểm về độ chính xác test:** `"Spring WebFlux"` có 14 ký tự UTF-16 (`6 + 1 + 7`) nên `length` dự kiến 14. Nếu đổi nội dung input, phải cập nhật expected length theo Java `String.length()`; đó không phải byte length UTF-8 khi có dấu tiếng Việt. Không assert header order, timestamp hoặc server-generated ID.

Chạy ở project có Gradle wrapper tương thích Java 17:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Trên Windows:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Test không chứng minh under-load performance hoặc proxy configuration. Nó xác minh contracts của application trên embedded server. Để kiểm uploads thực, security, retries/cancellation hoặc streaming clients, cần integration/load tests riêng.

## 13. Quan sát bằng curl

Khởi động project bằng `./gradlew bootRun` và gọi:

### 13.1. Metadata endpoint

~~~bash
curl -i 'http://localhost:8080/api/request?q=java' \
  -H 'X-Demo-Client: yes' \
  -H 'Cookie: demo=ok'
~~~

Dự kiến HTTP 200, JSON dạng:

~~~json
{
  "method": "GET",
  "path": "/api/request",
  "query": "java",
  "hasDemoHeader": true,
  "hasDemoCookie": true,
  "observed": true
}
~~~

Headers quan trọng:

~~~http
Cache-Control: no-store
X-Demo-Filter: observed
Content-Type: application/json
~~~

Response có thể thêm headers khác, nén hoặc truyền chunked tùy server. Không dựa vào thứ tự JSON fields hay headers.

### 13.2. Tạo JSON note

~~~bash
curl -i -X POST 'http://localhost:8080/api/notes' \
  -H 'Content-Type: application/json' \
  -d '{"text":"Spring WebFlux"}'
~~~

Dự kiến 201 Created, `Location: /api/notes/1`, `Cache-Control: no-store` và body:

~~~json
{"text":"Spring WebFlux","length":14}
~~~

Đây là **demo không persistence**, nên đừng sao chép y nguyên Location cố định này cho production: dùng repository tạo resource thật hoặc đổi semantics/status.

### 13.3. Raw body echo và 413

~~~bash
curl -i -X POST 'http://localhost:8080/api/raw-echo' \
  -H 'Content-Type: text/plain' \
  --data-binary 'hello'
~~~

Kết quả 200 body text:

~~~text
hello
~~~

Nếu body vượt 1024 bytes, Spring code chủ động đặt HTTP 413. Có thể tạo file lớn bằng công cụ tùy hệ điều hành để kiểm (không cần gửi dữ liệu riêng tư). Giới hạn ở demo **không** đại diện cho toàn bộ request-size policies của product.

### 13.4. Commit và cookie

~~~bash
curl -i 'http://localhost:8080/api/commit'
curl -i 'http://localhost:8080/api/cookie'
~~~

`/api/commit` trả 204, header `X-Commit-Phase: ready`, `X-Demo-Filter: observed` và không có body.

`/api/cookie` trả 204 cùng `Set-Cookie` có `demo=ok`, `Path=/`, `HttpOnly` và `SameSite=Lax`. Cookie demo không có `Secure` vì ví dụ chạy plain HTTP localhost. Trong sản phẩm HTTPS/session thực, bảo đảm chính sách bảo mật bổ sung.

## 14. Những lỗi nghiêm trọng nhất khi thao tác dữ liệu request/response

### 14.1. Đọc request body hai lần

**Sai:**

~~~java
return DataBufferUtils.join(request.getBody())
        .doOnNext(this::logRaw)
        .then(chain.filter(exchange));
~~~

Sau khi filter consume, handler có thể nhận body trống, decoder lỗi hoặc lifecycle phức tạp. Fragment còn thiếu release và giới hạn bytes. **Sửa:** không log raw body; nếu bắt buộc, dùng custom decorator có replay/buffer ownership được test kỹ, redaction và limits.

### 14.2. `subscribe()` bên trong WebFilter

~~~java
exchange.getRequest().getBody().subscribe(this::auditBuffer);
return chain.filter(exchange);
~~~

**Sai** vì tách một subscription ngoài HTTP request lifecycle; nguồn request có thể bị consume/race, error/cancel khó quản lý và audit không bảo đảm bền vững. Đừng dùng `subscribe` như một cách "trộm xem" HTTP body. Hãy compose đúng trong Mono/Flux của chain.

### 14.3. Buffer không release khi tự tiêu thụ

~~~java
request.getBody().map(buffer -> {
    byte[] bytes = new byte[buffer.readableByteCount()];
    buffer.read(bytes);
    return bytes;
});
~~~

Code fragment trên không release pooled `DataBuffer`; trong Netty có nguy cơ memory leak. Khi đã lấy quyền xử lý buffer, phải dùng `DataBufferUtils.release` theo đúng ownership, thường trong `finally` và xử lý cancellation/discard phù hợp.

### 14.4. Release buffer trước khi writer dùng

~~~java
Flux<DataBuffer> broken = request.getBody()
        .doOnNext(DataBufferUtils::release);
return response.writeWith(broken);
~~~

**Sai**: writer có thể đọc một buffer đã release. Khi forward raw body trực tiếp, publisher ownership và lifecycle cần nhất quán; không gọi release ở nhánh upstream rồi lại chuyển cùng buffer cho downstream writer.

### 14.5. Ghi header sau `chain.filter(...)` đã hoàn tất

~~~java
return chain.filter(exchange).then(
        Mono.fromRunnable(() ->
                exchange.getResponse().getHeaders()
                        .set("X-Late", "value")));
~~~

Có thể quá trễ do response đã commit. Dùng `response.beforeCommit(() -> Mono.fromRunnable(...))` trước khi gọi chain.

### 14.6. Đặt status nhưng không return Mono ghi/complete

~~~java
response.setStatusCode(HttpStatus.NO_CONTENT);
response.setComplete();
return Mono.empty();
~~~

Mã này bỏ qua `Mono<Void>` được trả từ `setComplete`; có thể hoạt động phụ thuộc framework bên ngoài nhưng **không diễn đạt đúng completion composition**. Chọn:

~~~java
response.setStatusCode(HttpStatus.NO_CONTENT);
return response.setComplete();
~~~

### 14.7. Kết hợp manual write với typed return body

Không vừa `response.writeWith(...)` rồi trả `Mono<SomeDto>` từ cùng handler. Hai cơ chế có thể tranh commit/body; framework khó xử lý error/status nhất quán. Nếu muốn WebFlux tự encode JSON, trả Mono DTO hoặc ResponseEntity. Nếu muốn raw bytes, return Mono<Void> từ response writer.

### 14.8. Nghĩ rằng ResponseCookie tự tạo quyền truy cập

Một cookie value do client gửi có thể bị sửa hoặc giả mạo nếu không có signing/session infrastructure đúng. `HttpCookie`/`ResponseCookie` chỉ là HTTP cookie representation; application authorization phải dùng Spring Security và kiểm principal/resource ownership.

### 14.9. Tin hoàn toàn `X-Forwarded-For` hoặc `Forwarded`

Các headers này có thể do client tự đưa nếu proxy chưa loại bỏ. Cấu hình trusted ingress và forwarded-header transformer/strategy đúng; không tự xác định rate limit/authorization trên một IP dễ spoof từ header thô.

### 14.10. Log raw request body chứa credentials

JSON body có thể chứa password, OTP, address, card data hoặc tokens. Một WebFilter log body chung cho mọi route dễ rò rỉ thông tin qua log pipeline. Chỉ log metadata cần thiết, allowlist fields không nhạy cảm, giới hạn bytes và bảo vệ retention/access.

## 15. Buffering, streaming và backpressure

### 15.1. Reactive body không phải “một chunk = một tin nhắn”

Một `Flux<DataBuffer>` có thể phát nhiều buffers tùy Netty transport, TCP frames và producer tốc độ nào. Một dòng JSON có thể nằm trong nhiều buffers; một buffer có thể chứa nhiều dòng. Khi cần parse JSON streaming, dùng codec phù hợp, delimiter-aware parser hoặc protocol framing, không tự split theo sự kiện onNext.

### 15.2. Khi nào dùng aggregate?

Dùng aggregate khi:
- Payload nhỏ, có **maxByteCount**.
- Chỉ cần một DTO hoặc checksum trên toàn payload.
- Không thể xử lý streaming và memory budget chấp nhận được.
- Có error policy 413 rõ ràng.

Không aggregate khi:
- Upload nhiều MB/GB hoặc unknown size.
- Số requests đồng thời cao khiến memory N × payload lớn.
- Data cần stream tới file/object store hoặc downstream.
- Body chứa dữ liệu nhạy cảm không cần giữ toàn bộ trong heap.

### 15.3. Backpressure

`Flux<DataBuffer>` là Reactive Streams publisher, nên downstream có thể điều tiết demand; nhưng không nên suy ra mọi nguồn bên ngoài đều vô hạn sức chứa hoặc luôn có thể bị chậm lại mà không cần buffer. Khi output response chậm, Netty/codec và subscriber cancellation ảnh hưởng upstream. Mọi custom `flatMap`/`buffer`/`onBackpressureBuffer` cần giới hạn concurrency/bộ đệm rõ.

### 15.4. `writeAndFlushWith` và SSE

Với Server-Sent Events, điều quan trọng là event framing, flush cadence, heartbeat, error handling và client reconnect. Một custom `writeAndFlushWith` không tự xây SSE protocol. Spring WebFlux hỗ trợ `ServerSentEvent<T>` và content type `text/event-stream` ở cấp controller cao hơn; dùng nó khi phù hợp thay vì byte-level framing thủ công.

### 15.5. Response đã commit thì error body bị giới hạn

Nếu đã gửi status 200/headers và vài bytes, một exception sau đó thường không thể biến response thành JSON 500 chuẩn. Với streaming, có thể cần in-band error event, đóng connection hoặc retry protocol. Luôn suy nghĩ về **error trước commit** và **error sau commit** như hai trường hợp vận hành khác nhau.

## 16. Request/response trong lỗi và cancellation

### 16.1. Error trước khi request body giải mã xong

JSON malformed, header Content-Type sai hoặc body vượt codec limit có thể khiến decoder/handler phát error trước khi ứng dụng tạo response. Hệ thống WebFlux có `WebExceptionHandler` và `@ExceptionHandler`, nhưng không phải mọi lỗi HTTP đều bắt được bởi một advice duy nhất (đặc biệt security/proxy layer).

Khi trả lỗi:
- Giữ status có ý nghĩa (400/401/403/404/413/415/422/429/500...).
- Không phản chiếu raw body/SQL/internal stack traces.
- Không trả 200 với payload `{"error":"bad"}` nếu contract là HTTP error.
- Đảm bảo Cache-Control phù hợp khi body/status liên quan đến user.
- Kiểm integration tests qua filter chain và gateway thật.

### 16.2. Client disconnect

Nếu client đóng kết nối giữa chừng:
- Subscription có thể được cancel; behavior chính xác tùy server/adapter/timing.
- `doFinally` có thể thấy `SignalType.CANCEL` trong pipeline liên quan.
- Native/pooled buffers phải được giải phóng qua đúng operators.
- Business side effect đã commit trong DB không tự rollback.
- Không giả định server luôn gửi được body 500 khi client đã mất kết nối.

### 16.3. Timeout không giải quyết một blocking call

Nếu có `.timeout(Duration.ofSeconds(2))` trên request body hoặc downstream publisher, timeout phát onError/cancel. Nhưng blocking JDBC/client call không tôn trọng interruption có thể vẫn chạy. Tránh `block()` ở WebFilter, response preCommit callback và event-loop code. Với blocking legacy, dùng bounded worker và dependency-side timeouts.

## 17. Security và privacy khi làm việc với raw HTTP

### 17.1. Thông tin không đáng tin đến từ client

Các trường sau **không tự đủ để xác thực/ủy quyền**: `X-User`, `X-Tenant`, `X-Role`, `X-Forwarded-For`, `Cookie`, query parameter `?user`, URL segment `{tenant}`, hostname hoặc custom Origin.

Nếu hạ tầng có trusted identity proxy, cần bảo đảm proxy loại bỏ/rewrite spoofed identity headers, network path origin bị khóa và application xác thực signed/trusted identity theo contract. Không dùng `request.getHeaders().getFirst("X-Role")` để cấp ADMIN trực tiếp.

### 17.2. Bảo vệ cookie và token

- Cookie session: `Secure` trên HTTPS, `HttpOnly`, `SameSite` phù hợp.
- Bearer token: `Authorization` phải được redact ở logs/traces.
- Error response: không phản chiếu token trong `ProblemDetail.detail`.
- `Cache-Control: no-store` cho nội dung rất nhạy cảm, hoặc `private` nếu policy cho phép browser cache nhưng cấm shared cache.
- Không dùng `Vary: Cookie` để thay no-store/private cho body user-specific.
- Không gửi raw user details trong custom trace header.

### 17.3. CORS

Spring WebFlux có CORS infrastructure; `Origin` là request metadata và `Access-Control-Allow-Origin` là response header của browser policy. **CORS không phải cơ chế phân quyền user**. Không viết WebFilter tự echo bất kỳ `Origin` nào rồi bật credentials; dùng allowlist đúng, xử lý preflight và đảm bảo Spring Security reactive tích hợp phù hợp.

### 17.4. Header injection và response splitting

Không nối trực tiếp untrusted header/query values vào response headers, `Location` hoặc `Set-Cookie` mà không validate/encode. Runtime HTTP server thường kiểm những giá trị header không hợp lệ, nhưng không nên dựa toàn bộ an toàn vào parser. Nếu tạo redirect URL, dùng URI builder và allowlist destinations; tránh open redirects và CRLF injection.

## 18. Troubleshooting: lỗi và hướng kiểm

| Triệu chứng | Nguyên nhân phổ biến | Hướng khắc phục |
| --- | --- | --- |
| `@RequestBody` nhận body trống | WebFilter đã đọc `getBody()` | Không consume hai lần; decorator replay đúng |
| API upload gây tăng direct memory | DataBuffer pooled không release | Rà ownership/release/discard/cancel |
| `DataBufferLimitException` | Codec/join vượt max bytes | Giới hạn hợp lý, stream payload lớn, trả 413 theo policy |
| Controller không trả body dù set headers | Trả Mono chưa được subscribe/composed | Return `writeWith`/`setComplete` hoặc typed response |
| Header trong filter không xuất hiện | Đặt sau response commit | Dùng `beforeCommit` |
| `IllegalStateException` khi sửa response headers | Headers đã read-only/committed | Đặt sớm, kiểm beforeCommit |
| Response 204 vẫn có body | Ghi payload dù HTTP 204 | Chỉ `setComplete`, không body |
| POST JSON trả 415 | Content-Type không hợp route/decoder | Kiểm consumes và Content-Type |
| POST text/plain nhưng body parse sai | Nhầm charset hoặc manual decode từng chunk | Decode UTF-8 đúng qua codec/bound |
| GET query param trùng bị bỏ qua | Dùng `getFirst` | Xác định duplicate policy |
| Cookies bị log ra file | Log request headers/body không redact | Allowlist logging và secrets policy |
| Response cookie không được browser gửi lại | Domain/path/SameSite/Secure/TLS không phù hợp | Kiểm cookie attributes và browser behavior |
| Client IP toàn proxy | Đọc `getRemoteAddress` sau CDN | Trusted forwarded-header configuration |
| CPU thấp nhưng latency cao | Blocking I/O/event-loop starvation | Thread dump, không block, bounded pool |
| WebFilter đếm request nhiều lần? | Custom instrumentation không idempotent | Thiết kế per-request attribute và lifecycle rõ |
| Header CORS có nhưng browser vẫn chặn | Preflight/method/credentials mismatch | Kiểm OPTIONS và CORS policy |
| Advice không bắt 401 từ Security | Exception phát sinh trước handler | SecurityWebFilterChain handlers |
| Response đã stream mà handler muốn đổi 500 | Headers/body đã commit | Streaming-aware error protocol |
| Test WebTestClient đúng nhưng CDN sai | Cache/proxy có custom rules | End-to-end test qua CDN, kiểm Vary/Cache-Control |
| Một buffer có nửa ký tự UTF-8 | Decode từng chunk độc lập | Codec streaming/charset-aware decoder |
| Mono `block()` làm treo Netty loop | Blocking API trong reactive chain | Return/compose publishers; offload legacy calls |

### Quy trình gỡ lỗi thực dụng

1. Xác định app thực sự chạy **Spring WebFlux/Netty** hay MVC/Servlet: xem starters và runtime.
2. Xác định layer lỗi: gateway, WebFilter, handler mapping, decoder, controller, response writer, Security hay client.
3. Ghi method, path, Content-Type, Accept, status, request ID/trace ID; **không log raw credentials**.
4. Kiểm có code nào subscribe request body ngoài pipeline hay không.
5. Kiểm DataBufferUtils.join/collectList có max size và release trên success/error/cancel không.
6. Nếu response header mất, xác minh set trước commit hay trong beforeCommit.
7. Kiểm response đã ghi qua typed return hay manual `writeWith`/`setComplete`, không trộn.
8. Với status bất ngờ, kiểm Spring Security/WebExceptionHandler/filter order.
9. Với client IP/redirect URL sai, kiểm trusted forwarded headers và proxy rewrite.
10. Dùng WebTestClient integration test trên embedded server, sau đó test gateway/CDN/browser thật nếu sản phẩm có các tầng đó.

## 19. Checklist khi viết WebFlux HTTP infrastructure

- [ ] Import đúng `org.springframework.http.server.reactive.ServerHttpRequest/Response`.
- [ ] Dùng `ServerWebExchange` cho request + response + attributes của một interaction.
- [ ] Không coi query/headers/cookies là identity đã xác thực.
- [ ] Không tự parse URI bằng string splitting hoặc tin mọi forwarded headers.
- [ ] Không đọc raw body trong nhiều filter/controller cùng lúc.
- [ ] Dùng `@RequestBody Mono<T>`/codecs cho JSON khi không cần xử lý raw bytes.
- [ ] Khi aggregate DataBuffer, đặt max bytes; release joined buffers sau khi copy/consume.
- [ ] Không release buffers đã chuyển quyền sở hữu cho response writer.
- [ ] Không ghi raw body/tokens/PII vào logs.
- [ ] Set status/headers/cookies trước response commit; `beforeCommit` callback lazy/nhẹ.
- [ ] Return Mono<Void> từ `writeWith`/`setComplete`; không subscribe/block thủ công.
- [ ] Không vừa manual write vừa trả thêm body từ annotated controller.
- [ ] Phân biệt response lỗi trước/sau khi headers commit.
- [ ] Chặn uploads quá lớn tại gateway/server/application.
- [ ] Cấu hình CORS, Spring Security reactive, cookie và cache privacy riêng.
- [ ] Giới hạn memory/concurrency, đo cancellation/timeout và reference leaks.
- [ ] Test metadata/query/headers/cookies, JSON, 204, 201, 413 và error behavior.
- [ ] Với streaming/file upload, dùng protocol/codec chuyên dụng thay aggregate mù.
- [ ] Xác nhận headers cuối sau WebFilter/security/proxy, không chỉ controller code.

### Câu hỏi tự kiểm tra

**1.** `ServerHttpRequest` và `ServerHttpResponse` thuộc package nào?

**Trả lời:** `org.springframework.http.server.reactive`, không phải Servlet `HttpServletRequest`/`HttpServletResponse`.

**2.** Vì sao `request.getBody().next()` không phải cách đọc JSON hoàn chỉnh?

**Trả lời:** Nó lấy phần tử DataBuffer đầu tiên, body có thể gồm nhiều buffers không trùng JSON boundaries.

**3.** Có thể đọc request body ở WebFilter rồi để controller đọc lại như thường không?

**Trả lời:** Không bảo đảm. Raw body thường single-consumer streaming; cần decorator/replay được thiết kế nếu có lý do đặc biệt, hoặc không consume trong filter.

**4.** Khi tự đọc joined pooled DataBuffer rồi chuyển sang byte array, phải làm gì?

**Trả lời:** Copy bytes, bảo đảm `DataBufferUtils.release` trong `finally`; xử lý cả giới hạn size và error/cancel.

**5.** `response.beforeCommit(...)` khác `chain.filter(exchange).then(...)` ở điểm nào?

**Trả lời:** beforeCommit callback chạy ngay trước commit; `then` sau chain có thể quá trễ để thay response headers.

**6.** Vì sao `writeWith(...)` trả Mono<Void>?

**Trả lời:** Ghi body là hoạt động reactive có completion/error; phải return/compose Mono để framework subscribe đúng vòng đời, không tự block.

**7.** Khi trả `Mono<DTO>` có nhất thiết gọi `ServerHttpResponse.writeWith` thủ công không?

**Trả lời:** Không. Spring WebFlux codecs có thể encode DTO; chỉ dùng raw write khi cần kiểm soát bytes/streaming thấp hơn.

**8.** `HttpCookie` và `ResponseCookie` khác gì?

**Trả lời:** HttpCookie là cookie đọc từ request; ResponseCookie tạo cookie server gửi qua Set-Cookie với thuộc tính như Path, HttpOnly, SameSite, Secure.

**9.** `exchange.getAttributes()` có phải session không?

**Trả lời:** Không. Attributes thuộc request/response interaction; WebSession có lifecycle riêng và được lấy qua `getSession()`.

**10.** `X-Forwarded-For` có thể dùng trực tiếp làm client IP đáng tin không?

**Trả lời:** Không nếu không có trusted proxy configuration; attacker có thể spoof header.

**11.** Vì sao không đổi HTTP status từ 200 sang 500 sau khi đã stream bytes?

**Trả lời:** Response đã commit headers/status và có thể đã gửi body; cần error handling phù hợp streaming và không hứa sửa status sau commit.

## 20. Tài liệu chính thức

- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): mô hình WebFlux, HTTP server adapters, handler infrastructure.
- [Spring Framework — WebFlux DispatcherHandler](https://docs.spring.io/spring-framework/reference/web/webflux/dispatcher-handler.html): HandlerMapping, HandlerAdapter, HandlerResultHandler.
- [Spring Framework — WebFlux Method Arguments](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html): `ServerHttpRequest`, `ServerHttpResponse` và `ServerWebExchange` trong annotated controllers.
- [Spring Framework — ServerHttpRequest 6.1 Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/server/reactive/ServerHttpRequest.html): request method/URI/headers/cookies/body APIs.
- [Spring Framework — ServerHttpResponse 6.1 Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/server/reactive/ServerHttpResponse.html): status, cookies, headers, response writing, commit.
- [Spring Framework — ServerWebExchange 6.1 Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/ServerWebExchange.html): request/response interaction, session, attributes, mutator.
- [Spring Framework — WebFilter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/server/WebFilter.html): reactive middleware contract.
- [Spring Framework — Data Buffers and Codecs](https://docs.spring.io/spring-framework/reference/core/databuffer-codec.html): DataBufferFactory, pooled buffers, release, join và codecs.
- [Spring Framework — DataBufferUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html): bounded join, retain/release và stream utilities.
- [Spring Framework — ReactiveHttpOutputMessage Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/ReactiveHttpOutputMessage.html): `beforeCommit`, `isCommitted`, `writeWith`, `writeAndFlushWith`, `setComplete`.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): reactive HTTP integration testing.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): dependency/runtime server và configuration.
- [Spring Framework — CORS for WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): browser cross-origin và reactive security integration.
- [Spring Security — WebFlux](https://docs.spring.io/spring-security/reference/reactive/index.html): reactive SecurityWebFilterChain và context.

**Ghi nhớ:** `ServerHttpRequest` cung cấp metadata và body đầu vào dạng `Flux<DataBuffer>`; `ServerHttpResponse` là nơi xây status/headers/cookies và ghi output reactive. `ServerWebExchange` nối chúng với lifecycle của một HTTP request. Hãy ưu tiên Spring codecs/DTOs khi có thể, tránh consume body nhiều lần, kiểm buffer ownership, đặt giới hạn kích thước và quyết định headers trước commit. Khi cần raw HTTP infrastructure, hãy test trên WebFlux/Netty thực thay vì chỉ tin một phương thức trả về `Mono`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-005">Codec, HttpMessageReader, HttpMessageWriter và cấu hình giới hạn bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Codec, HttpMessageReader, HttpMessageWriter và cấu hình giới hạn bộ nhớ trong Spring WebFlux

## 1. Vì sao một ứng dụng reactive vẫn có thể hết bộ nhớ?

Spring WebFlux dùng API reactive và I/O bất đồng bộ để xử lý HTTP, nhưng **không đồng nghĩa mọi request/response luôn được streaming không giới hạn bộ nhớ**. Khi nhận JSON, form, multipart, SSE hoặc text, Spring vẫn cần biến các **bytes của HTTP body** thành đối tượng Java. Khi trả response, nó lại phải chuyển đối tượng Java thành bytes. Công việc này do hệ thống **HTTP codecs** đảm nhiệm.

Một API:

```java
@PostMapping("/documents")
Mono<Result> upload(@RequestBody Mono<Document> input) {
    return input.map(this::process);
}
```

có thể phải **tích lũy nhiều bytes của một JSON object** trước khi decoder tạo được `Document`. Nếu client gửi object có giá trị chuỗi rất dài hoặc nhiều mảng lồng nhau, quá trình này có thể gây áp lực heap/direct memory dù controller không hề gọi `collectList()`. Đây là lý do hệ thống HTTP codecs có giới hạn **`maxInMemorySize`** và có thể phát sinh `DataBufferLimitException`.

Các câu hỏi của bài:

1. Sự khác biệt giữa **`Encoder`/`Decoder`** và **`HttpMessageWriter`/`HttpMessageReader`** là gì?
2. Một `@RequestBody Mono<T>`, `@RequestBody Flux<T>` và `@ResponseBody Flux<T>` được chuyển qua codec như thế nào?
3. Khi nào limit áp vào **cả body**, khi nào áp vào **một record/delimiter/event**?
4. Cấu hình đúng `spring.http.codecs.max-in-memory-size` cho **Spring Boot 3.5** và `ServerCodecConfigurer.defaultCodecs().maxInMemorySize(...)` cho WebFlux ra sao?
5. Làm thế nào thiết lập riêng cho **WebClient** và phân biệt inbound server với outbound client?
6. Vì sao codec limit không phải **max HTTP upload**, rate limit, tổng heap limit hay backpressure policy?
7. Tại sao multipart có giới hạn riêng cho RAM, disk và part count? Khi nào dùng `FilePart`, `PartEvent`?
8. Làm sao kiểm thử decode, NDJSON streaming, `DataBufferLimitException` và byte-buffer lifecycle một cách có căn cứ?

**Phạm vi:** Spring Framework **6.2.x**, Spring Boot **3.5.x**, Java **17+**, **WebFlux reactive stack** và mặc định Reactor Netty. Tên property ở Spring Boot 3.5 được lấy theo chính metadata của release, không dùng máy móc các ví dụ Boot cũ. Bài thuộc nhóm **Hạ tầng WebFlux**.

## 2. Tổng quan đường đi request và response

```text
                  INBOUND HTTP REQUEST
HTTP/TCP bytes
    |
Reactor Netty / ServerHttpRequest
    |
Flux<DataBuffer>
    |
HttpMessageReader<T>
   + Decoder<T> nếu dùng codec chuyển bytes -> object
    |
@RequestBody Mono<T> / Flux<T> hoặc ServerRequest.bodyToMono(...)
    |
Handler / Service (validation + business)
    |
Flux<R> / Mono<R> / ResponseEntity<...>
    |
HttpMessageWriter<R>
   + Encoder<R> nếu dùng codec chuyển object -> bytes
    |
Flux<DataBuffer>
    |
ServerHttpResponse -> Reactor Netty -> HTTP client
                  OUTBOUND HTTP RESPONSE
```

Điều quan trọng: **`DataBuffer`** là abstraction cho vùng bytes, không nhất thiết là `byte[]` trên heap. Với Reactor Netty, dữ liệu có thể dựa trên Netty `ByteBuf` với pooled/direct memory. Hệ thống codecs và writers/readers quản lý nhiều tác vụ decode/encode, media type, character set, hints và buffer lifecycle.

- **Reader** quan tâm HTTP request/response body **đầu vào** của một thành phần.
- **Writer** quan tâm HTTP request/response body **đầu ra** của một thành phần.
- Trên **server**, đọc request bằng reader, ghi response bằng writer.
- Trên **WebClient**, ghi outbound request bằng writer, đọc inbound response bằng reader.
- Các vai trò đổi theo chiều đọc/ghi; **không có một bộ codec “chỉ dành cho server” về mặt nguyên lý**.
- HTTP codec không thay thế business validation, authorization, chống malware, giới hạn tổng payload hay quản lý các kết nối chậm.

## 3. So sánh các tầng API

| Lớp/API | Package Spring tiêu biểu | Vai trò | Phụ thuộc HTTP? |
| --- | --- | --- | --- |
| `DataBuffer` | `org.springframework.core.io.buffer` | Đại diện bytes/chunks | Không |
| `DataBufferFactory` | `org.springframework.core.io.buffer` | Tạo/cấp phát DataBuffer | Không |
| `DataBufferUtils` | `org.springframework.core.io.buffer` | Join, read, write, retain/release, stream hỗ trợ | Không |
| `Decoder<T>` | `org.springframework.core.codec` | `Flux<DataBuffer>` → objects | Không nhất thiết |
| `Encoder<T>` | `org.springframework.core.codec` | Objects → `DataBuffer` | Không nhất thiết |
| `HttpMessageReader<T>` | `org.springframework.http.codec` | Đọc HTTP body, có headers, media type, hints | **Có** |
| `HttpMessageWriter<T>` | `org.springframework.http.codec` | Ghi HTTP body, xử lý HTTP output/message metadata | **Có** |
| `DecoderHttpMessageReader<T>` | `org.springframework.http.codec` | Adapter bọc một Decoder để đọc HTTP | Có |
| `EncoderHttpMessageWriter<T>` | `org.springframework.http.codec` | Adapter bọc một Encoder để ghi HTTP | Có |
| `ServerCodecConfigurer` | `org.springframework.http.codec` | Cấu hình readers/writers cho WebFlux server | Có |
| `ClientCodecConfigurer` | `org.springframework.http.codec` | Cấu hình codecs cho WebClient | Có |
| `DataBufferLimitException` | `org.springframework.core.io.buffer` | Cảnh báo decoder/join vượt giới hạn tích lũy | Không |

Spring Framework mô tả `Encoder` và `Decoder` là hợp đồng mức thấp, độc lập với HTTP; `HttpMessageReader` và `HttpMessageWriter` đưa hợp đồng này vào ngữ cảnh HTTP. Những codec đặc thù như **multipart, form data, SSE** có thể là reader/writer chuyên dụng, không nhất thiết chỉ là Jackson encoder bọc ngoài.

### 3.1. Hai chuyển đổi cơ bản

**Đọc JSON:**

```text
Content-Type: application/json
Body: {"id":7,"name":"An"}
                |
     Jackson2JsonDecoder
                |
             Item
```

**Ghi JSON:**

```text
Item(id=7,name="An")
                |
     Jackson2JsonEncoder
                |
application/json response bytes
{"id":7,"name":"An"}
```

`Jackson2JsonDecoder` và `Jackson2JsonEncoder` có các quy tắc xử lý stream khác nhau theo media type và Reactor publisher type. Đừng mô tả WebFlux là “convert text sang object” mà bỏ qua MediaType, DataBuffer lifecycle, validation và limit.

## 4. HttpMessageReader: inbound body được đọc như thế nào?

Trong Spring WebFlux annotated controllers:

```java
@PostMapping("/items")
Mono<Item> create(@RequestBody Mono<Item> input) {
    return input;
}
```

`@RequestBody` được infrastructure dùng một **`HttpMessageReader` phù hợp** với body type và `Content-Type` để decode request. Với JSON, reader thường bọc Jackson decoder khi Jackson có trong classpath.

### 4.1. Chọn reader

Việc chọn phụ thuộc vào:

- Target type: `Item`, `String`, `byte[]`, `DataBuffer`, `MultiValueMap`, `Part`, v.v.
- `Content-Type` do client khai báo và media type mà reader hỗ trợ.
- Reactive wrapper/cardinality: `Mono<T>` hoặc `Flux<T>`.
- Generic type information như `List<Item>`, `Map<String,Object>`, `Flux<Item>`.
- Codecs đã đăng ký và thứ tự ưu tiên.
- ObjectMapper/Kotlin/Jackson XML/Protobuf dependencies nếu cần.
- Decoding hints/charset/validation sau khi deserialize.

**Không tin Content-Type là xác minh nội dung an toàn.** Header do client kiểm soát; một JSON parser có thể từ chối cú pháp sai, nhưng điều đó không đồng nghĩa dữ liệu đã đúng quy tắc nghiệp vụ.

### 4.2. `Mono<T>` có thể phải gom nội dung

```java
@PostMapping(path = "/echo", consumes = "application/json")
Mono<Echo> echo(@RequestBody Mono<Echo> request) {
    return request;
}
```

Đây là **fragment** giả định có record `Echo`. Khi một JSON object nằm trên nhiều network chunks, decoder cần ghép đủ tokens để tạo object. **Limit áp vào bytes tích lũy để decode một đơn vị**, không chỉ một TCP chunk.

Nếu JSON object lớn hơn limit (ví dụ một field chứa vài MB text), có thể ném `DataBufferLimitException`. Tùy lớp xử lý lỗi/exception resolver, status mặc định **không nên được đoán là luôn 413**. Hãy cấu hình HTTP error contract có kiểm thử thực tế.

### 4.3. `Flux<T>` hỗ trợ stream nhưng không miễn limit

```java
@PostMapping(
    path = "/items/stream",
    consumes = "application/x-ndjson")
Mono<Summary> summary(@RequestBody Flux<Item> items) {
    // Xử lý theo từng record...
    return items.reduce(...);
}
```

Fragment minh họa. Với NDJSON, mỗi dòng là một JSON value. Reader có thể decode từng record mà **không tích lũy cả HTTP body**. Tuy nhiên **một dòng/record quá dài** vẫn có thể vượt `maxInMemorySize` khi codec phải giữ đến delimiter hoặc object hoàn chỉnh.

Một stream có **1 triệu dòng × mỗi dòng 1 KiB** về nguyên tắc có thể được đọc dần dù tổng request lớn hơn 256 KiB. Nhưng nếu bạn `collectList()` toàn stream hoặc `reduce` tạo structure tăng dần không giới hạn thì heap vẫn có thể OOM; limit codec không giới hạn tổng kết quả mà application tự giữ.

### 4.4. Form body và multipart

Với `application/x-www-form-urlencoded`, framework thường phải phân tích toàn bộ form fields thành cấu trúc, nên có buffering và các giới hạn tương ứng. Với multipart, parser có cơ chế chuyển file parts sang disk và các giới hạn riêng. Không xem multipart là “chỉ một JSON request lớn”.

## 5. HttpMessageWriter: outgoing response bytes

`@ResponseBody` / `@RestController` được WebFlux xử lý bằng `HttpMessageWriter`:

```java
@GetMapping(path = "/item", produces = "application/json")
Mono<Item> one() {
    return Mono.just(new Item(1, "demo"));
}
```

Jackson encoder biến `Item` thành JSON bytes, HttpMessageWriter ghi vào `ServerHttpResponse` qua reactive buffers.

### 5.1. Flux và media type quyết định behavior

```java
@GetMapping(path = "/items", produces = "application/json")
Flux<Item> items() {
    return Flux.range(1, 3)
            .map(i -> new Item(i, "item-" + i));
}
```

Với `application/json`, nhiều giá trị thường được biểu diễn thành **JSON array**:

```json
[
  {"id":1,"name":"item-1"},
  {"id":2,"name":"item-2"},
  {"id":3,"name":"item-3"}
]
```

Trong khi `application/x-ndjson` biểu diễn từng JSON object theo dòng:

```text
{"id":1,"name":"item-1"}
{"id":2,"name":"item-2"}
{"id":3,"name":"item-3"}
```

Và `text/event-stream` dùng SSE framing:

```text
event: update
data: {"id":1,"name":"item-1"}

event: update
data: {"id":2,"name":"item-2"}

```

**Không mặc định** mỗi `onNext` sẽ lập tức tạo một HTTP packet. Encoder/writer, buffering/flush policy, proxy, HTTP/2 DATA frames và client đều ảnh hưởng thời điểm nhận dữ liệu. `Flux<T>` chỉ là một nguồn reactive; semantics của HTTP output còn phụ thuộc `Content-Type`.

### 5.2. Writer và custom serialization

Khi muốn đổi tên JSON fields hoặc format timestamps, ưu tiên cấu hình **Jackson ObjectMapper** / DTO annotations theo chuẩn Boot. Không tự viết một `HttpMessageWriter` chỉ để đổi tên field hoặc cắt chuỗi.

Khi có **media type riêng** như `application/vnd.mycompany.binary`, cân nhắc viết `Encoder`/`Decoder` hoặc reader/writer chuyên dụng nếu thật sự có wire protocol mới; cần tuân thủ backpressure, byte lifecycle và validation. Xem phần 17.

## 6. DataBuffer: byte storage và lifecycle

`DataBuffer` trừu tượng hóa byte regions. Trên Reactor Netty, một `DataBuffer` có thể bọc pooled Netty `ByteBuf` (direct memory). Bộ nhớ trực tiếp **không hiển thị đầy đủ trong heap usage**; ứng dụng có thể chịu OOM/direct memory pressure dù heap metrics còn ổn.

Các thao tác thường gặp:

```java
DataBufferFactory factory = new DefaultDataBufferFactory();
DataBuffer buffer = factory.wrap(
        "hello".getBytes(StandardCharsets.UTF_8));
```

Đây là fragment với imports Spring buffer + `StandardCharsets`, không phải file độc lập. `DefaultDataBufferFactory` trong test thường tạo buffer unpooled; trong Netty có thể dùng pooled buffer factory.

### 6.1. Không được giữ DataBuffer tùy tiện

Sai kiểu:

```java
List<DataBuffer> all = new ArrayList<>();
return request.getBody()
        .doOnNext(all::add)
        .then();
```

Đây là **anti-pattern**: giữ mọi buffer có thể gây tăng RAM/direct memory, và pooled buffers có vòng đời release. Phải consume, relay hoặc release theo đúng contract.

Nếu code **tự sở hữu** một buffer và quyết định bỏ nó, sử dụng `DataBufferUtils.release(buffer)`. Nếu sử dụng buffer sau asynchronous handoff, có thể phải `retain` theo cách phù hợp; không double-release hoặc dùng sau release.

### 6.2. `DataBufferUtils.join` là aggregation — có limit khi cần

```java
Mono<DataBuffer> combined =
        DataBufferUtils.join(sourceBuffers, 8 * 1024);
```

Khi tổng bytes vượt 8192, Spring có thể phát `DataBufferLimitException`. Nếu thành công, một combined buffer được trả về; bên **nhận/tiêu thụ** phải release đúng nếu tự quản lý nó. Khi `join` gặp error/cancel, API đã có contract giải phóng các buffers tích lũy.

Không dùng overload `DataBufferUtils.join(sourceBuffers)` không có bound cho data untrusted hoặc stream vô hạn nếu không có control/limit ở nơi khác.

### 6.3. File streaming

Đối với file lớn, hãy **stream** từ disk/object store thay vì `DataBufferUtils.join` toàn bộ file. Ví dụ dùng `DataBufferUtils.read(Path, DataBufferFactory, bufferSize)` và `ServerHttpResponse.writeWith(...)` trong code phù hợp, hoặc `Resource`/`FilePart.transferTo(...)` theo use case.

Lưu ý: không biến việc dùng `Flux<DataBuffer>` thành bảo đảm an toàn tuyệt đối. Cần giới hạn **tổng bytes, thời gian, số concurrent streams, disk quota, permissions và file validation**.

## 7. Vì sao có DataBufferLimitException?

Class:

```java
import org.springframework.core.io.buffer.DataBufferLimitException;
```

Exception biểu thị số bytes tích lũy vượt giới hạn đặt trước trong một decoder/reader/join operation. Điều này có thể xảy ra khi:

- Decode một JSON request object quá lớn.
- `bodyToMono(String.class)`/`bodyToMono(byte[].class)` cho body lớn.
- Decode form-data thành toàn bộ map.
- JSON streaming: **một** record/tokens quá lớn.
- SSE: một event payload/line vượt khả năng tích lũy.
- `DataBufferUtils.join(source, maxBytes)`.
- WebClient nhận response lớn và muốn aggregate thành một object.
- Decoder XML/protobuf/custom codec cần buffer một đơn vị lớn, tùy implementation.

Lỗi điển hình:

```text
org.springframework.core.io.buffer.DataBufferLimitException:
Exceeded limit on max bytes to buffer
```

Thông báo chính xác có thể khác theo Spring version/decoder. Không tự kết luận đây là “heap đã đầy”; thường là **guardrail cố ý từ chối một unit quá lớn trước khi dùng thêm bộ nhớ**.

### 7.1. Không phải mọi trường hợp đều giống nhau

| Đường xử lý | Giới hạn thường áp vào | Có giới hạn tổng HTTP body? |
| --- | --- | --- |
| JSON object → `Mono<Pojo>` | Bytes cần để decode một object | Không nhất thiết tách biệt |
| Text → `Mono<String>` | Chuỗi body được aggregate | Thực tế là body mà decoder đọc |
| NDJSON → `Flux<Pojo>` | Một record/JSON value | **Không** giới hạn toàn stream |
| SSE → `Flux<ServerSentEvent<T>>` | Nội dung một event/đơn vị parser | **Không** giới hạn toàn kết nối |
| Multipart form field | Bytes của từng non-file part | **Không** thay max-parts |
| Multipart file part | Ngưỡng giữ in-memory trước khi spool disk | **Không** thay disk usage quota |
| `Flux<DataBuffer>` | Chunks thô | Không tự aggregate; app phải kiểm soát |
| `collectList()` trong service | Heap dùng cho tất cả items trong list | Codec limit không kiểm soát |
| `WebClient.bodyToMono(...)` | Client decode/aggregation | Không phải server inbound limit |

**Cần phân biệt unit-of-aggregation và tổng bytes** khi quyết định nâng từ 256 KiB lên 2 MiB. Nếu mỗi user gửi 2 MiB và có 200 requests đồng thời, chỉ riêng buffered payloads có thể tương đương hàng trăm MB, chưa kể parser structures và network buffers.

## 8. Mặc định và cấu hình Spring Boot 3.5

### 8.1. Property ĐÚNG cho Boot 3.5

Trong **Spring Boot 3.5**, cấu hình codec liên quan chính thức là:

```properties
# src/main/resources/application.properties
spring.http.codecs.max-in-memory-size=8KB
```

Tên namespace này là **`spring.http.codecs.*`** trong Boot **3.5**. Spring Boot 3.5 metadata xác nhận `HttpCodecsProperties` với prefix `spring.http.codecs` (có từ 3.5.0). Một số bài hướng dẫn **Boot cũ** dùng `spring.codec.max-in-memory-size`; không copy property cũ mà không xem phiên bản và configuration metadata.

**Mặc định Boot 3.5:** property `spring.http.codecs.max-in-memory-size` **không đặt** nếu người dùng không cấu hình. Khi đó các codec dùng default riêng; đa số codec tích lũy có giới hạn khoảng **256 KiB**. Không khẳng định mọi codec/multipart mode có cùng default/behavior.

Property này áp dụng cho **auto-configured WebFlux server** và các **WebClient instances được Boot auto-configure**; nó không tự thay đổi mọi `WebClient.create()` tự tạo ngoài Boot builder, không tự áp dụng cho app Spring Framework tự quản lý hoàn toàn và không phải “giới hạn memory của cả JVM”.

### 8.2. YAML tương đương

```yaml
spring:
  http:
    codecs:
      max-in-memory-size: 8KB
```

Chỉ chọn một nơi khai báo thống nhất. Với config theo environment, cần xác định precedence và kiểm tra config hiệu dụng trong staging/production thay vì chỉ đọc file source.

### 8.3. Vì sao chọn 8 KiB cho demo?

Để dễ thử guardrail trên các JSON nhỏ:

- Body JSON echo vài chục bytes → qua.
- Một JSON field lớn hàng chục KiB → có thể gây decoder limit.
- NDJSON nhiều dòng nhỏ → có thể stream qua dù **tổng** body lớn hơn 8 KiB.
- Một NDJSON **dòng duy nhất** trên 8 KiB → có thể vượt limit từng record.

**8 KiB chỉ là ngưỡng học tập**, không phải recommendation production. Với API upload hồ sơ hoặc thông tin người dùng thực tế, một record hợp lệ có thể cần lớn hơn; chọn dựa trên payload distributions, business validation, concurrent load và overhead.

### 8.4. Chỉ có property không đủ để giới hạn upload

Nếu cần giới hạn **tổng request HTTP** ở 1 MiB, một codec max-in-memory-size 1 MiB chưa chắc bảo đảm điều đó. Một `Flux<DataBuffer>` hoặc multipart file đã spool disk có thể vượt tổng dung lượng, mà codec vẫn xử lý streaming bình thường. Hãy kết hợp proxy/gateway max request size, server/container controls và byte-counting ở application layer.

## 9. Cấu hình trực tiếp ServerCodecConfigurer

Không dùng Boot autoconfiguration hoặc cần custom codec behavior? Spring WebFlux cung cấp `WebFluxConfigurer`:

```java
package demo.codec;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
class CodecConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs()
                .maxInMemorySize(512 * 1024);
    }
}
```

**Code class này độc lập hợp lệ khi đặt trong app WebFlux cùng package.** `ServerCodecConfigurer` cung cấp bộ default readers/writers và cách thêm, thay hoặc tùy chỉnh codecs. `WebFluxConfigurer#configureHttpMessageCodecs` được WebFlux gọi cho cả annotated controllers lẫn functional endpoints.

**Đừng tự thêm `@EnableWebFlux` vào Spring Boot app chỉ để đổi limit.** Trong Boot, `@EnableWebFlux` có thể làm Boot WebFlux auto-configuration bị thay thế/disable theo cách không mong muốn; implement `WebFluxConfigurer` mà không thêm annotation này khi muốn giữ Boot defaults.

### 9.1. Property vs Java config

- **Property** phù hợp khi muốn đặt chung global default trong Boot.
- **`WebFluxConfigurer`** phù hợp khi cần kiểm soát codec setup theo application architecture.
- Nếu cùng lúc đặt property và Java override khác nhau, thứ tự áp dụng có thể phụ thuộc auto-config/customization; **không suy đoán giá trị hiệu lực**. Chọn một nguồn cấu hình, test payload tại ranh giới.
- Có thể có codec custom với limit riêng, không tự nghe mọi default settings.
- Với **WebClient**, cần cấu hình codec ở client builder riêng nếu không dùng Boot-configured `WebClient.Builder` hoặc muốn giới hạn khác.

### 9.2. Server side không dùng Servlet `HttpMessageConverter`

Spring MVC Servlet dùng `HttpMessageConverter`. **Spring WebFlux dùng `HttpMessageReader`/`HttpMessageWriter` với reactive codecs.** Tên lớp tương tự nhưng khác contract và stack. Đừng copy MVC `configureMessageConverters`/`extendMessageConverters` để điều khiển WebFlux reactive codec.

## 10. WebClient: HTTP client cũng có codec memory limit

`WebClient` viết request và đọc response qua `ClientCodecConfigurer`. Khi cần đọc response JSON lớn:

```java
WebClient client = WebClient.builder()
        .baseUrl("https://api.example.org")
        .codecs(configurer -> configurer.defaultCodecs()
                .maxInMemorySize(2 * 1024 * 1024))
        .build();
```

Đây là **fragment**, cần import `org.springframework.web.reactive.function.client.WebClient`. Limit trên áp cho codec của **client instance này**, không thay `ServerCodecConfigurer` inbound server.

Nếu cần giữ tùy chỉnh Boot:

```java
@Service
class PartnerClient {
    private final WebClient http;

    PartnerClient(WebClient.Builder builder) {
        this.http = builder
                .baseUrl("https://partner.example")
                .codecs(c -> c.defaultCodecs()
                        .maxInMemorySize(512 * 1024))
                .build();
    }
}
```

Đây là fragment cần `@Service` và import WebClient; partner hostname minh họa. Trong Boot, injection `WebClient.Builder` giúp hưởng các customizations được auto-configured; `WebClient.create()` **không nhất thiết** có chúng. Khi mutate builder, kiểm tra limit nào được dùng cuối cùng.

### 10.1. bodyToMono và bodyToFlux

```java
Mono<List<Item>> all = client.get()
        .uri("/items")
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<Item>>() {});
```

Dạng này thường cần decode **một JSON array** vào `List<Item>`; nếu body/array rất lớn, có thể bị codec limit hoặc heap cao.

```java
Flux<Item> stream = client.get()
        .uri("/items.ndjson")
        .accept(MediaType.APPLICATION_NDJSON)
        .retrieve()
        .bodyToFlux(Item.class);
```

Với NDJSON, mỗi record có thể decode riêng, làm giảm nhu cầu aggregate toàn response. Nhưng `stream.collectList()` sẽ lại tập hợp toàn bộ items trong application heap. **Streaming codec không sửa được một consumer gom tất cả dữ liệu.**

### 10.2. Client không mặc nhiên đồng bộ limit với server

Ví dụ origin server cho JSON object tối đa 1 MiB nhưng WebClient consumer còn default khoảng 256 KiB → client có thể ném `DataBufferLimitException` khi server đã trả HTTP 200. Đọc stack trace/metrics để xác định **lỗi xuất hiện trong client decoder hay server decoder**.

Không nâng client limit lên 100 MiB chỉ để “sửa lỗi” mà không hỏi vì sao response cần lớn vậy. Cân nhắc pagination/NDJSON/streaming endpoints, giới hạn array size và kiểm soát aggregate trong service.

## 11. Multipart: giới hạn RAM khác giới hạn disk và số parts

Multipart thường xuất hiện trong API upload ảnh, file CSV, video hoặc form có nhiều fields. Spring WebFlux có parser và `FilePart`/`Part`/`PartEvent`; các giới hạn chuyên biệt khác với global HTTP codec memory setting.

**Spring Boot 3.5** có các properties được document:

```properties
# Chỉ là ví dụ policy, phải điều chỉnh theo sản phẩm:
spring.webflux.multipart.max-in-memory-size=64KB
spring.webflux.multipart.max-disk-usage-per-part=10MB
spring.webflux.multipart.max-parts=8
spring.webflux.multipart.max-headers-size=10KB
```

Ý nghĩa:

| Property | Phạm vi |
| --- | --- |
| `spring.webflux.multipart.max-in-memory-size` | Ngưỡng giữ non-file part trong RAM; với file part có thể xác định khi nào spool ra disk |
| `spring.webflux.multipart.max-disk-usage-per-part` | Mức disk sử dụng tối đa cho mỗi file part được ghi ra disk |
| `spring.webflux.multipart.max-parts` | Tổng số parts cho request |
| `spring.webflux.multipart.max-headers-size` | Bộ nhớ cho phần headers của một part |
| `spring.webflux.multipart.file-storage-directory` | Nơi lưu file parts khi parser spool ra disk |
| `spring.webflux.multipart.headers-charset` | Charset để decode part headers |

Các defaults Boot 3.5 đáng chú ý: `max-in-memory-size` thường **256KB**; `max-disk-usage-per-part` và `max-parts` mặc định **-1** (không áp limit tương ứng), nên phải quyết định chính sách production phù hợp. Một số trường hợp stream multipart qua `PartEvent` có behavior khác parser dùng aggregated `Part`; đừng áp tất cả properties mà mặc định chúng giới hạn như nhau.

**Đây không phải chống malware hay phân quyền.** Multipart parser chỉ đọc cấu trúc tệp; ứng dụng vẫn cần allowlist định dạng, kiểm tra nội dung, quota/tần suất, storage quyền riêng, scanning/CDR nếu cần, auth và cleanup.

### 11.1. `FilePart.transferTo(...)`

Một controller reactive có thể nhận file part và chuyển nó sang path do server tạo:

```java
@PostMapping(path = "/files",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
Mono<Void> save(@RequestPart("file") FilePart file) {
    Path safeDestination = ...; // Server-generated & private.
    return file.transferTo(safeDestination);
}
```

**Đây là fragment**, không phải runnable method độc lập: `safeDestination` phải được tạo trong service với quyền, quota, collision/cleanup policy. Không dùng `file.filename()` làm path; đây là tên do client điều khiển. `FilePart.transferTo` trả `Mono<Void>`; để WebFlux subscribe, **trả Mono** thay vì gọi `subscribe()` bên trong controller.

### 11.2. `PartEvent` để stream multipart theo part

Spring WebFlux 6.2 hỗ trợ `@RequestBody Flux<PartEvent>`. Mỗi part có thể tạo nhiều events; `PartEvent.isLast()` đánh dấu event cuối part. Một mẫu tổ chức:

```java
Flux<PartEvent> events = ...;

Flux<Flux<PartEvent>> byPart =
        events.windowUntil(PartEvent::isLast);
```

Đây là fragment; mỗi inner flux cần được **consume, relay hoặc release** hoàn toàn, đặc biệt nếu các `DataBuffer` chứa pooled memory. Nếu xử lý file content tự viết với `PartEvent.content()`, phải có ownership contract: khi bỏ content, release buffer; khi chuyển downstream, tránh double-release.

**Không đồng nhất `PartEvent` với `Flux<FilePart>`**. PartEvent giúp streaming data theo events, còn `FilePart` thường là abstraction cho part đã được parser quản lý, có thể dùng disk spool.

## 12. Cấu hình không thay thế giới hạn tổng HTTP request

Giả sử API chấp nhận:

```http
POST /api/items.ndjson HTTP/1.1
Content-Type: application/x-ndjson

{"id":1,"name":"A"}
{"id":2,"name":"B"}
...
```

Nếu mỗi line nhỏ và decoder phát object từng dòng, **tổng request 2 GB** vẫn có thể đi qua codec limit 8KB theo từng item, miễn các layers khác cho phép. Điều này có thể không an toàn với tài nguyên hệ thống.

Để đặt tổng tối đa 1 MiB cần:

1. Gateway/load balancer policy về request body size và connection limits.
2. HTTP server settings tương thích Reactor Netty / underlying server.
3. **Per-request byte counting** ở stream khi cần, với cancel/cleanup khi vượt ngưỡng.
4. Application quota theo user/tenant, multipart total-size/file count limits.
5. Timeout/slow upload defenses, concurrency limits, disk quotas.
6. Không giữ infinite streams hoặc vô hạn buffered items.

### 12.1. Tự giới hạn số bytes trong Flux<DataBuffer>

Phác thảo fragment giới hạn tổng inbound bytes:

```java
AtomicLong total = new AtomicLong();

Flux<DataBuffer> limited = request.getBody()
        .handle((buffer, sink) -> {
            long length = buffer.readableByteCount();
            long now = total.addAndGet(length);
            if (now > MAX_TOTAL_BYTES) {
                DataBufferUtils.release(buffer);
                sink.error(new PayloadTooLargeException());
            } else {
                sink.next(buffer);
            }
        });
```

Đây **chỉ là fragment minh họa**: `request`, `MAX_TOTAL_BYTES`, `PayloadTooLargeException`, imports, ownership của buffers và xử lý cancel/error phải được xác định trong app thật. Dùng `AtomicLong` để xử lý số cộng và overflow theo policy (giới hạn max thực tế, tránh long overflow), không reuse state across requests. Một khi `sink.next(buffer)`, downstream nhận ownership theo contract; không tự release buffer sau khi chuyển. Trong functional WebFlux, có thể decorate `ServerHttpRequest`/WebFilter để đếm bytes, nhưng phải giữ backpressure, giải phóng buffer đúng và tránh hai consumers đọc body.

Đối với phần lớn ứng dụng, **proxy/gateway request body cap** đơn giản và đáng tin hơn tự viết một global filter đọc lại body. Nếu cần limit sau khi parse, kiểm tra cả metadata và source stream.

## 13. Chạy demo Spring Boot 3.5 WebFlux / Java 17

Ví dụ tối giản nhưng tách rõ từng codec behavior:

- `POST /api/echo` đọc một JSON object và trả một JSON object.
- `GET /api/items.ndjson` xuất ba JSON records theo dạng NDJSON.
- `POST /api/summary` nhận NDJSON `Flux<Item>` và chỉ giữ **count + sum IDs**, không collect toàn bộ records.
- Codec memory limit cho demo đặt **8KB** để kiểm thử ranh giới.
- Unit test trực tiếp `DataBufferUtils.join(maxByteCount)` và HTTP test qua WebTestClient.

Không có DB, upload, auth hay production rate limiting; code **để học codec behavior**, không dùng trực tiếp cho public service.

### 13.1. Build dependencies

Dự án được tạo từ Spring Initializr với **Spring Boot 3.5.x**, Java **17**, dependency **Spring Reactive Web**. Với Gradle project đã có Boot plugin, repositories, BOM và toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment `build.gradle`**, không phải file Gradle build độc lập. Không thêm `spring-boot-starter-web` vào demo này: khi MVC và WebFlux cùng hiện diện, Boot 3.x thường chọn MVC web application type mặc định, làm kết quả lệch mục tiêu.

### 13.2. Properties

Tạo `src/main/resources/application.properties`:

```properties
# Spring Boot 3.5 namespace; 8KB để dễ quan sát decoder limit.
spring.http.codecs.max-in-memory-size=8KB

# Không chủ động log raw HTTP request details:
spring.http.codecs.log-request-details=false
```

Property log request details được Boot 3.5 metadata công bố; `false` là default thông dụng. Không có nghĩa gateway, APM hoặc code khác sẽ tự masking nếu chúng log payload.

### 13.3. Java application

Tạo **`src/main/java/demo/codec/CodecDemoApplication.java`**:

```java
package demo.codec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class CodecDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodecDemoApplication.class, args);
    }
}

record EchoInput(String message) {}

record EchoOutput(String message, int length) {}

record Item(int id, String name) {}

record Summary(long count, long sumIds) {}

@RestController
@RequestMapping("/api")
class CodecDemoController {

    @PostMapping(
            path = "/echo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<EchoOutput> echo(@RequestBody Mono<EchoInput> incoming) {
        return incoming.map(body -> {
            if (body.message() == null
                    || body.message().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "message must not be blank");
            }

            String text = body.message().strip();
            return new EchoOutput(text, text.length());
        });
    }

    @GetMapping(
            path = "/items.ndjson",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    Flux<Item> itemsNdjson() {
        return Flux.range(1, 3)
                .map(id -> new Item(id, "item-" + id));
    }

    @PostMapping(
            path = "/summary",
            consumes = MediaType.APPLICATION_NDJSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Summary> summary(@RequestBody Flux<Item> items) {
        return items.reduce(
                new Summary(0L, 0L),
                (acc, item) -> new Summary(
                        acc.count() + 1,
                        acc.sumIds() + item.id()));
    }
}
```

Mã dùng Java 17 **records**, `Flux`/`Mono` và WebFlux annotation controllers. Chỉ class `CodecDemoApplication` là public; các records và controller cùng package được component scan. Jackson từ `starter-webflux` có thể encode/decode Java records.

#### Điều cần đọc kỹ trong code

- `echo(...)` dùng **`Mono<EchoInput>`**: reader tạo **một object**. Limit 8KB có thể chặn request JSON quá lớn trong decode trước khi vào `map`.
- `itemsNdjson()` trả **`Flux<Item>`** cùng `produces=application/x-ndjson`: writer có thể encode ba object theo line framing.
- `summary(...)` dùng `@RequestBody Flux<Item>`: reader decode mỗi NDJSON record; `reduce` giữ đúng **hai số**, không tạo list tăng dần. Nhưng nếu client gửi vô hạn record, CPU/network vẫn hoạt động vô hạn; **codec limit không thay request size/time/count quota**.
- `Summary.count` và `sumIds` có thể overflow `long` nếu source cực kỳ lớn hoặc ID không tin cậy. Production cần range check/limit count/checked arithmetic nếu business đòi chính xác.
- Body JSON không hợp lệ thường dẫn đến error từ reader; body hợp lệ nhưng business invalid trả `400` như trong code. Đừng kết luận mọi `DataBufferLimitException` sẽ tự biến thành 413 nếu chưa có error policy.
- Không gọi `subscribe()` hoặc `block()` trong controller; WebFlux HTTP adapter tự subscribe và quản lý lifecycle.

### 13.4. Chạy HTTP

```bash
./gradlew bootRun
```

Request nhỏ:

```bash
curl -i -X POST 'http://localhost:8080/api/echo' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"message":"Hello WebFlux"}'
```

Response dự kiến:

```json
{"message":"Hello WebFlux","length":13}
```

`Hello` = 5, một space = 1, `WebFlux` = 7 nên length = **13** theo Java String length với toàn ký tự ASCII của ví dụ. Nếu thêm Unicode supplementary characters, `String#length()` đếm **UTF-16 code units**, không phải số grapheme/user-perceived characters; ví dụ cố ý ASCII để output có thể xác minh.

GET NDJSON:

```bash
curl -N -i \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/items.ndjson'
```

Body dự kiến:

```text
{"id":1,"name":"item-1"}
{"id":2,"name":"item-2"}
{"id":3,"name":"item-3"}
```

POST NDJSON:

```bash
printf '%s\n' \
  '{"id":1,"name":"A"}' \
  '{"id":2,"name":"B"}' \
  '{"id":3,"name":"C"}' \
  | curl -i -X POST 'http://localhost:8080/api/summary' \
      -H 'Content-Type: application/x-ndjson' \
      -H 'Accept: application/json' \
      --data-binary @-
```

Body dự kiến:

```json
{"count":3,"sumIds":6}
```

Các response trên là **kết quả dự kiến nếu đúng project/baseline và input**; chưa phải output của một lần chạy thực tế.

### 13.5. Kiểm tra payload vượt limit

Với `spring.http.codecs.max-in-memory-size=8KB`, thử gửi một `message` dài hơn 10.000 ký tự ASCII trong **một JSON object**:

```bash
# Ví dụ tạo payload bằng Python tại terminal ngoài ứng dụng:
python -c "import json; print(json.dumps({'message':'A'*10000}))" \
  | curl -i -X POST 'http://localhost:8080/api/echo' \
      -H 'Content-Type: application/json' \
      --data-binary @-
```

Kỳ vọng **decode vượt limit**; HTTP status và body lỗi chính xác còn phụ thuộc exception translation của phiên bản/config. **Không cố gắng khẳng định mặc định luôn 413**, và không log payload 10.000 bytes trong môi trường có dữ liệu nhạy cảm.

Với NDJSON, có thể stream hàng trăm dòng 100 bytes; tổng request lớn hơn 8KB nhưng từng record nằm trong giới hạn, tùy những giới hạn khác. Nếu một dòng chứa 20KB text thì decoder có thể báo `DataBufferLimitException` cho **dòng đó**.

## 14. Tests có kiểm chứng: HTTP codecs và giới hạn bộ đệm

Tạo **`src/test/java/demo/codec/CodecDemoApplicationTests.java`**:

```java
package demo.codec;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodecDemoApplicationTests {

    @Autowired
    WebTestClient client;

    @Test
    void echoesSmallJsonWithJacksonCodec() {
        client.post()
                .uri("/api/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"message":"Hello WebFlux"}
                    """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Hello WebFlux")
                .jsonPath("$.length").isEqualTo(13);
    }

    @Test
    void emitsThreeNdjsonRecords() {
        Flux<Item> items = client.get()
                .uri("/api/items.ndjson")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_NDJSON)
                .returnResult(Item.class)
                .getResponseBody();

        StepVerifier.create(items)
                .expectNext(new Item(1, "item-1"))
                .expectNext(new Item(2, "item-2"))
                .expectNext(new Item(3, "item-3"))
                .verifyComplete();
    }

    @Test
    void limitedJoinRejectsExcessBytes() {
        DefaultDataBufferFactory factory =
                new DefaultDataBufferFactory();

        Flux<DataBuffer> tooLarge = Flux.just(
                factory.wrap(new byte[8193]));

        StepVerifier.create(
                DataBufferUtils.join(tooLarge, 8192))
                .expectError(DataBufferLimitException.class)
                .verify();
    }

    @Test
    void limitedJoinCanCombineAllowedBytes() {
        DefaultDataBufferFactory factory =
                new DefaultDataBufferFactory();

        Flux<DataBuffer> parts = Flux.just(
                factory.wrap("ab".getBytes(
                        StandardCharsets.UTF_8)),
                factory.wrap("cd".getBytes(
                        StandardCharsets.UTF_8)));

        StepVerifier.create(DataBufferUtils.join(parts, 8))
                .assertNext(buffer -> {
                    try {
                        byte[] bytes = new byte[
                                buffer.readableByteCount()];
                        buffer.read(bytes);
                        assertEquals(
                                "abcd",
                                new String(bytes,
                                        StandardCharsets.UTF_8));
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .verifyComplete();
    }
}
```

Chạy:

```bash
./gradlew test
```

Bốn tests được **thiết kế để pass** trên baseline Java17/Boot3.5/WebFlux; **chưa chạy Gradle trong tài liệu này**. Test HTTP dùng `RANDOM_PORT` để chạy qua WebFlux HTTP stack thay vì chỉ unit-call controller. Test `limitedJoinRejectsExcessBytes` kiểm tra **hợp đồng chính xác** của `DataBufferUtils.join(source,maxBytes)`: buffer 8193 > limit 8192, phát `DataBufferLimitException`; test thành công đọc đủ 4 bytes và release buffer sở hữu.

**Giới hạn của tests:** hai test `join` minh họa **DataBufferUtils guard**, không khẳng định toàn bộ Boot decoder limit được cấu hình đúng. Để kiểm chứng hiệu lực property 8KB qua HTTP, bổ sung một HTTP test gửi một JSON object >8KB và xác nhận lỗi decode; khi muốn assert **413**, phải xây error-handling contract của application và test nó, không phụ thuộc mặc định không được kiểm chứng.

## 15. Thiết kế HTTP status khi vượt bộ đệm

Một API public nên xác định semantics cho payload quá lớn:

| Tình huống | Status thường phù hợp | Cần lưu ý |
| --- | --- | --- |
| JSON body vượt kích thước cho phép theo API | `413 Content Too Large` | Có thể chặn tại proxy trước khi tới codec |
| Form/file part vượt quota | `413 Content Too Large` | Trường hợp disk/part count có exception riêng |
| JSON syntax sai | `400 Bad Request` | Không phải vấn đề quota |
| Media type không hỗ trợ | `415 Unsupported Media Type` | Reader selection khác với byte limit |
| Response `Accept` không đáp ứng | `406 Not Acceptable` | Writer content negotiation |
| Business validation fail | `400`/`422` theo contract | Không phải DataBufferLimitException |
| Authentication thiếu | `401 Unauthorized` | Không phải codec |
| Không đủ quyền upload/download | `403 Forbidden` | Không phải codec |
| Server heap thực sự cạn | Sự cố hệ thống, không đơn thuần 413 | Không dựa vào exception mapper để cứu OOM |

Có thể ánh xạ `DataBufferLimitException` thành 413 trong một `@RestControllerAdvice` / `WebExceptionHandler` có scope phù hợp. Nhưng lỗi có thể bị **wrap** bởi reader/decoder hoặc xảy ra **sau khi response committed**, và có thể đến từ **WebClient outbound** hoặc một internal aggregate, không luôn là request quá lớn của client. Khi xử lý, cần phân loại nguyên nhân trước, tránh biến mọi internal buffering error thành HTTP 413 “client sai”.

**Không in raw exception/toString body ra client**, nhất là nếu thông báo chứa request content, parser details hoặc paths. Có error code ổn định như `PAYLOAD_TOO_LARGE` và message public ngắn gọn, log kỹ thuật theo privacy policy.

## 16. Reader, writer và content negotiation

HTTP codecs được chọn theo cả **kiểu Java** và **MediaType**:

- Inbound: `Content-Type` khai loại body để reader decode.
- Outbound: `Accept` khai format client muốn nhận, còn `produces` và available writers quyết định kiểu đáp ứng.
- `Content-Encoding` như gzip/br là một tầng khác; không nhầm nó với media type `application/json`.
- Charset quan trọng với text codecs, nhưng JSON phổ biến dùng UTF-8 theo các quy tắc HTTP/JSON tương ứng.
- Generic type parameters quan trọng: `List<Item>` và raw `List.class` có thông tin khác, ảnh hưởng decode element types.
- Classpath quyết định có Jackson JSON, JAXB/XML, Protobuf hoặc codecs optional; thiếu dependency có thể gây unsupported media type/codec selection failures.
- HTTP codecs không tự bảo vệ khỏi unsafe deserialization business types nếu ObjectMapper được cấu hình mở polymorphic typing tùy tiện. Chỉ bật loại typing đã đánh giá kỹ và cập nhật dependencies.

### 16.1. `Mono<List<T>>` khác `Flux<T>`

```java
Mono<List<Item>> a = ...;
Flux<Item> b = ...;
```

Nếu source thực sự là list lớn, `Mono<List<Item>>` đã aggregate vào heap trước khi writer nhìn thấy. Trong khi `Flux<Item>` có thể giữ stream, nhưng nếu media type là `application/json` writer có thể có behavior khác NDJSON/SSE. Để kiểm soát memory, cần xét **producer, operator và codec**, không chỉ kiểu return.

### 16.2. String, byte[] và Resource

`Mono<String>` hoặc `Mono<byte[]>` thường đại diện một body hoàn chỉnh; khi decode những dạng này từ network response, codec có thể gom bytes trước. Với `Flux<DataBuffer>` raw response streaming, memory use có thể thấp hơn nhưng ownership/release rủi ro cao hơn.

Khi file có trên disk, **`Resource`** và HTTP range/conditional support có thể phù hợp hơn tự viết `Mono<byte[]>` cho file lớn. Không copy toàn bộ file vào heap vì muốn trả download 200.

## 17. Khi thực sự cần custom codec

Chỉ viết codec riêng khi có **wire representation không được built-in codecs xử lý hợp lý**, ví dụ binary event format nội bộ có framing cụ thể. Nếu chỉ muốn đổi tên JSON field, annotation/Jackson module là đủ.

Một custom `Decoder<CustomPacket>` phải quan tâm:

1. **`canDecode(ResolvableType, MimeType)`** thật sự match type/media type.
2. Decode `Flux<DataBuffer>` một cách **backpressure-aware**.
3. Có framing rõ: length-prefix, delimiter hoặc self-delimiting format.
4. Enforce max frame/object bytes trước khi cấp phát dữ liệu lớn.
5. Cẩn thận partial frame ở ranh giới giữa hai DataBuffers.
6. Xử lý UTF-8 split chars nếu frame là text; không decode từng chunk như chuỗi độc lập rồi ghép tùy tiện.
7. Release buffers đã consume, discard và lỗi/cancel; không double release.
8. Reject invalid/truncated/oversized messages một cách có kiểm soát.
9. Không tạo blocking read hoặc `block()` trên Netty event loop.
10. Có tests phân mảnh bytes ở mọi vị trí và fuzz malformed frames.
11. Đăng ký reader/writer theo thứ tự sao cho không che built-in codec cho các media types chung.
12. Cấu hình limit/metrics theo chính codec đó, không giả định defaultCodecs.maxInMemorySize tự điều khiển một custom codec không hỗ trợ limit.

### 17.1. Adapter lớp thấp và lớp HTTP

Framework có:

```java
DecoderHttpMessageReader<MyType> reader =
        new DecoderHttpMessageReader<>(myDecoder);

EncoderHttpMessageWriter<MyType> writer =
        new EncoderHttpMessageWriter<>(myEncoder);
```

Đây là **fragment** giả định `myDecoder`/`myEncoder` là implementations có interface/generics hợp lệ. Reader/writer sẽ hiểu HTTP headers/media type ngoài semantics bytes của encoder/decoder. Đăng ký với `ServerCodecConfigurer.customCodecs()` bằng API tương ứng; xem Javadoc version cụ thể vì custom codec registration có overload khác nhau giữa release series.

Không ghi source custom codec 20 dòng và khẳng định production-safe. Binary framing, buffer release, streams vô hạn, byte limits và malformed data cần test sâu hơn code mẫu.

## 18. Byte memory vs heap vs direct memory vs disk

| Tầng tài nguyên | Ví dụ | Ai phải giới hạn |
| --- | --- | --- |
| Java heap | Decoded DTOs, Strings, Lists, cached objects | JVM sizing, business quotas, bounded operators |
| Direct/native memory | Netty ByteBuf/pooled DataBuffer | Netty allocator, buffer lifecycle, runtime metrics |
| Codec aggregate memory | JSON object/string/form parsing | `maxInMemorySize` của codec tương ứng |
| Operator queues | `flatMap` prefetch, `buffer`, `collectList` | Reactor policies, concurrency/backpressure |
| Multipart temporary disk | FilePart spool directory | WebFlux multipart limits, disk quotas, cleanup |
| Persistent storage | Object store, DB/file uploads | Business quotas/retention/permissions |
| Gateway buffers | NGINX/CDN buffering | Proxy request/response size and buffering policy |
| Client heap | `WebClient.bodyToMono`, browser JSON parse | Client codecs, application and browser limits |

Một application có codec limit 256KB vẫn có thể dùng 10GB heap qua `collectList()` hoặc nhiều concurrent requests. Ngược lại, nâng codec limit lên 10MB không tự làm heap tăng 10MB cho mọi request; mức sử dụng còn phụ thuộc concurrency, payload size, decoder và connection behavior.

**Chiến lược chọn ngưỡng:**

- Đo P50/P95/P99 size cho từng endpoint, cộng biên hợp lý.
- Dùng DTO/schema validation và size quotas theo nghiệp vụ.
- Tách endpoint stream/bulk import khỏi endpoint JSON nhỏ.
- Định mức concurrent requests × peak buffered bytes cùng các buffers/decoded object overhead.
- Kiểm tra upload cả RAM và disk, không xem multipart limit như một số.
- Test slow clients và nhiều requests đồng thời trước khi tăng global default.
- Thử chaos/overflow/large nested JSON để xác định failure modes.
- Theo dõi memory và status errors sau rollout; đừng thay property trên production mà chưa biết side effects.

## 19. Multipart, SSE, NDJSON và backpressure: mối liên hệ

Backpressure trong Reactive Streams đo **nhu cầu theo phần tử**, còn codec limit đo **bytes tích lũy khi decode một đơn vị**. Hai cơ chế bổ sung nhưng **không thay thế nhau**.

Ví dụ:

```text
Client gửi NDJSON 100MB
  gồm 100.000 dòng, mỗi dòng 1KB

Codec maxInMemorySize=8KB
  => mỗi record có thể được decode riêng

Reactive downstream request(n)
  => hướng dẫn tốc độ xử lý item

Application collectList()
  => vẫn có thể tích toàn bộ records 100MB+ vào heap

Gateway maxBodySize=10MB
  => có thể chặn request trước khi đủ 100MB

Kết luận:
  Bốn giới hạn/cơ chế khác nhau
```

Với SSE, một event có nhiều dòng `data:` và event delimiter; decoder có thể tích lũy cho đến khi event hoàn chỉnh. Nếu event quá lớn, codec limit có thể gây error, dù mỗi network chunk nhỏ.

Với multipart, `FilePart` có thể spool data sang disk ngay khi vượt ngưỡng, nên tổng file lớn hơn `max-in-memory-size` mà không nhất thiết là `DataBufferLimitException`. Tuy nhiên nếu không giới hạn disk usage per part, part count và concurrency thì kẻ tấn công vẫn có thể làm đầy ổ đĩa.

## 20. Lỗi điển hình và cách xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Cách kiểm tra |
| --- | --- | --- |
| `DataBufferLimitException: Exceeded limit...` khi POST JSON | Một object/body aggregate vượt limit | Kiểm tra kích thước bytes thật, Boot property và decoder |
| Request JSON nhỏ pass, JSON có field rất dài fail | Per-object buffer limit | Validation max field length; cân nhắc 413 có contract |
| WebClient fail khi origin trả 200 | Client decoder limit nhỏ hơn response | Kiểm tra `WebClient.Builder.codecs` |
| Property không có hiệu lực | Dùng tên của Boot version cũ | Với Boot 3.5 dùng `spring.http.codecs.max-in-memory-size` |
| WebFlux custom codec vẫn nhận payload quá lớn | Custom codec không obey default limit | Thêm explicit max frame/object size |
| App memory tăng dù limit 8KB | `collectList`, operator buffer/queue, nhiều concurrent requests | Heap profiler + Reactor/Netty metrics |
| Direct memory OOM | Pooled DataBuffer không được release hoặc Netty buffer pressure | Kiểm tra retain/release và allocator metrics |
| Multipart upload vượt 256KB vẫn thành công | 256KB là ngưỡng RAM→disk, không phải upload max | `spring.webflux.multipart.max-disk-usage-per-part` và total body policy |
| Disk đầy vì multipart | Max-disk-usage default unbounded hoặc cleanup sai | Cấu hình quota, spool directory, retention |
| NDJSON stream 100MB không bị 8KB chặn | Mỗi record nhỏ hơn limit | Bổ sung total byte limit/timeout/count limit |
| Một NDJSON record lớn bị lỗi giữa stream | Buffering giữa delimiters quá lớn | Giới hạn record, client split data đúng |
| SSE event lớn khiến client decoder fail | Tích lũy event vượt limit | Event size limit, chunk domain data hợp lý |
| `HTTP 415` dù JSON hợp lệ | Content-Type không được reader hỗ trợ | Kiểm tra media type và dependencies |
| `HTTP 406` khi client yêu cầu YAML | Không có writer cho Accept type | Cấu hình produces hoặc bổ sung codec thích hợp |
| Unicode text bị lỗi | Charset/decoder hoặc split bytes sai | Sử dụng codec xử lý UTF-8 chuẩn |
| Request body rỗng làm Mono.empty | Client không gửi body, handler chưa có default | Quy định contract required body/validation |
| Test chỉ assert 500 khi oversized | Exception mapping không ổn định/không rõ | Test DataBufferLimitException trực tiếp và HTTP policy riêng |
| Netty event loop block | Custom codec/handler gọi `block()` hoặc blocking I/O | Dùng non-blocking APIs hoặc isolate boundedElastic |
| Form details bị lộ trong DEBUG | Bật raw request detail logging | Giữ `spring.http.codecs.log-request-details=false`, redaction toàn tầng |

### Quy trình debug ngắn

1. Xác nhận runtime là WebFlux (không bị `starter-web` đưa thành MVC).
2. Xem request `Content-Type`, expected Java type và serialization format.
3. Xem exception **server hay WebClient**, trước hay sau controller.
4. Kiểm tra property **Boot 3.5 namespace** và WebFluxConfigurer/ClientCodecConfigurer customization.
5. Kiểm tra aggregation xảy ra ở codec hay ở `collectList/join/reduce` trong service.
6. Kiểm tra media type streaming như NDJSON/SSE và kích thước một record.
7. Nếu multipart, kiểm tra settings **riêng** và disk metrics.
8. Với custom DataBuffer access, audit release/retain.
9. Tái hiện bằng payload gần threshold và load test concurrent requests.
10. Đưa response lỗi có status, error code và retry policy phù hợp.

## 21. Test boundary và những điều cần đo thật

Một bộ test có giá trị nên gồm ba tầng:

### A. Unit-level codec/buffer

- `DataBufferUtils.join` với bytes ngay dưới/đúng/bên trên limit.
- `StepVerifier` kiểm tra `DataBufferLimitException`.
- Test release buffers khi normal/error/cancel.
- Nếu custom Decoder, test dữ liệu được split giữa mọi byte positions, malformed UTF-8/JSON, trailing bytes và memory leaks.

### B. WebFlux HTTP integration

- `@SpringBootTest(RANDOM_PORT)` + `WebTestClient` để đi qua Reactor Netty/codec thật.
- Gửi JSON nhỏ đúng type và kiểm tra deserialize/serialize.
- Gửi một JSON object vượt limit, quan sát HTTP error path thực.
- Gửi NDJSON nhiều records nhỏ tổng lớn hơn limit, xác minh từng record đến.
- Gửi một NDJSON record vượt limit, xác minh error/cancel.
- Gửi Accept/Content-Type mismatch để xem 406/415 contract.
- Kiểm tra client codec limit khi downstream WebClient đọc response lớn.

### C. Staging qua gateway/production topology

- Payload vượt tổng ingress body limit; xác nhận bị chặn trước app.
- Multipart nhiều parts, lớn hơn disk usage quota, ngắt giữa upload.
- Pooled buffers/Netty direct memory/GC/concurrency dưới load.
- Test slow client/sender, cancellation và streaming flush latency.
- Reverse proxy buffering, compression và HTTP/2→HTTP/1.1 translation.
- Kiểm tra không lưu raw body, passwords hoặc file bytes trong logs/APM.
- Quan sát HTTP 413/4xx/5xx và error recovery.

**Đừng lấy một test StepVerifier thuần làm bằng chứng** rằng Boot property đã được bind, hay một HTTP test success làm bằng chứng rằng pooled buffers không leak. Mỗi tầng kiểm tra một giả thuyết khác nhau.

## 22. Lưu ý bảo mật cho dữ liệu đi qua codec

- Parser JSON/XML có thể đối diện input bất thường, deeply nested data hoặc resource-exhaustion payloads; giữ dependencies cập nhật và validation schema/depth/size.
- Không bật polymorphic deserialization/default typing không kiểm soát cho dữ liệu client.
- Không log raw HTTP body, Authorization, cookies, access tokens, multipart parts hoặc signed URLs.
- Cấu hình `spring.http.codecs.log-request-details=false` chỉ liên quan những log qua codec config; gateway/Netty access logs/APM cần redaction riêng.
- Khi custom codec trả lỗi, không đưa stack traces/parser internals vào response.
- Upload format allowlist, anti-malware/quarantine/storage isolation là việc của nghiệp vụ, không của codec.
- Với XML, lưu ý XXE/entity expansion policy của XML parser nếu bật XML codec; đừng tự thêm parser không an toàn.
- Multipart filename, Content-Type của part, JSON field data đều do client đưa; không dùng làm filesystem path hoặc trusted host.
- DataBuffer không tự mã hóa, ký chữ ký hoặc kiểm tra integrity; cần HTTPS, auth và application-level integrity khi hợp đồng yêu cầu.
- `DataBufferLimitException` giúp chống một kiểu tăng memory nhưng không bảo vệ khỏi flood nhiều requests nhỏ; dùng ingress rate/concurrency limits.

## 23. So sánh giữa Spring Framework/Boot versions

| Thế hệ | Điểm khác biệt đáng chú ý |
| --- | --- |
| Spring Framework 5.x / Boot 2.x | WebFlux codecs đã có; namespace Boot codec settings có thể khác bản mới; phải xem đúng reference |
| Spring Framework 6.2 / Boot 3.5 | Java 17+, Jakarta APIs; WebFlux `ServerCodecConfigurer`; Boot property **`spring.http.codecs.max-in-memory-size`** |
| Spring Framework 7.x / Boot 4.x | Core concepts reader/writer/encoder/decoder còn, nhưng codec classes (đặc biệt JSON), Boot starters/modules và config properties có thể đổi; kiểm tra migration docs của exact version |

**Không chép nguyên** `spring.codec.max-in-memory-size` từ tutorial Boot 2/đầu Boot 3 rồi coi chắc chắn hiệu lực trên Boot 3.5. Cũng không nhầm `spring.servlet.multipart.*` của Servlet MVC với **`spring.webflux.multipart.*`** trong reactive stack.

Khi chuyển WebFlux sang MVC hoặc ngược lại, ngoài annotations/controller code còn phải đổi **HTTP serialization infrastructure, validation/error handling, multipart parsing, response streaming và test setup**. Reactor `Flux` có thể xuất hiện trong Spring MVC trong vài return types, nhưng MVC servlet response writing không cùng contract với WebFlux reactive readers/writers.

## 24. Checklist vận hành production

- [ ] Xác nhận app thật chạy WebFlux, không vô tình chạy MVC vì dependency trùng.
- [ ] Có media types/DTO contracts cho inbound và outbound.
- [ ] Dùng property đúng Spring Boot 3.5: `spring.http.codecs.max-in-memory-size`.
- [ ] Đã xác định default (~256KiB với nhiều codecs) và config hiệu dụng bằng test.
- [ ] Không tăng global limit chỉ để chữa một endpoint thiết kế body quá lớn.
- [ ] Endpoint cần stream dùng NDJSON/SSE/Flux phù hợp, không aggregate toàn bộ.
- [ ] Có record/event size cap, total HTTP payload cap, timeout và quota.
- [ ] WebClient được config riêng nếu cần limit khác server; tránh `WebClient.create()` bypass customizations.
- [ ] Multipart có RAM/disk/part/header limits và storage cleanup.
- [ ] Không dùng originalFilename làm đường dẫn; kiểm soát validation và scan.
- [ ] Không giữ buffers thô vô hạn, pooled buffers được release/relay đúng.
- [ ] Không có `collectList/join` unbounded trên dữ liệu không tin cậy.
- [ ] Custom codec tuân thủ frame limit, media type và backpressure.
- [ ] Xử lý lỗi decoder/limit thành HTTP contract ổn định, không lộ stack traces.
- [ ] Có test với payload sát/ngay trên limit và nhiều request đồng thời.
- [ ] Có WebTestClient HTTP test và buffer unit tests riêng.
- [ ] Theo dõi heap, direct memory, Netty pending buffers, disk usage và 413/5xx rates.
- [ ] Gateway/proxy size limits, rate limits và timeouts đồng bộ với policy.
- [ ] Không log raw HTTP headers/form data/PII nếu không được phép.
- [ ] Migration Spring/Boot được rà soát theo dependency BOM và property metadata.

## 25. Bài tập tự đánh giá

1. Phân biệt `Decoder<T>` và `HttpMessageReader<T>`. Vì sao reader cần biết HTTP metadata?
2. `HttpMessageWriter` trên server làm gì và khi WebClient gửi request thì class tương tự đóng vai trò nào?
3. Tại sao `@RequestBody Mono<byte[]>` có thể vượt limit khi một HTTP request body lớn, dù các network chunks nhỏ?
4. Với NDJSON gồm 10.000 records × 100 bytes, codec limit 8KiB có giới hạn tổng 1MB không?
5. Một NDJSON record dài 16KiB nhưng mỗi TCP segment chỉ 1KiB. Vì sao vẫn có thể `DataBufferLimitException`?
6. Phân biệt `spring.http.codecs.max-in-memory-size` (Boot 3.5) với property trong tutorial Boot cũ.
7. Khi có một `WebFluxConfigurer` custom và Boot property khác nhau, làm sao xác minh limit hiệu dụng?
8. Vì sao `WebClient.create()` có thể không nhận auto-configured Boot customizations?
9. Tại sao server trả 200 với body 1MiB nhưng WebClient vẫn lỗi ở client-side decoder 256KiB?
10. Vì sao `WebClient.bodyToFlux(Item.class)` thường giúp stream, nhưng `collectList()` lại có thể gây OOM?
11. Với multipart, vì sao file có thể lớn hơn `max-in-memory-size` mà parser vẫn lưu được?
12. Tại sao maxDiskUsagePerPart, maxParts và gateway max request size là ba giới hạn khác nhau?
13. Khi tự đọc `Flux<DataBuffer>`, khi nào cần `DataBufferUtils.release`? Tại sao double-release nguy hiểm?
14. `DataBufferUtils.join(source, 8192)` phải làm gì nếu source có 8193 bytes? Test bằng StepVerifier.
15. Kiểm thử `DataBufferUtils.join` thành công: khi nhận combined buffer, ai chịu trách nhiệm release nếu tự tiêu thụ nó?
16. Một `Flux<Item>` response với `application/json` và `application/x-ndjson` khác nhau ra sao về framing?
17. Khi dữ liệu đã trả 200 + một vài NDJSON lines rồi decoder/producer gặp error, có thay HTTP status thành 413 không?
18. Nếu viết codec custom cho length-prefixed binary, làm sao xử lý một frame bị split qua nhiều DataBuffers?
19. Vì sao `spring.http.codecs.log-request-details=false` không bảo đảm proxy logs không chứa token?
20. Hãy thiết kế policy cho API nhập CSV 1GB nhưng mỗi record nhỏ: total-size quota, disk storage, streaming decoder, auth, retry, cancellation và validation.
21. Bạn sẽ đo metric nào để phân biệt codec aggregation pressure với memory leak do pooled Netty ByteBuf?
22. Khi server code gọi `collectList()` sau khi decoder đã tạo từng object, codec max-in-memory-size có bảo vệ bộ nhớ list đó không?
23. Với JSON nested array lớn, việc tăng limit toàn hệ thống có tác động ra sao tới peak memory khi 200 users upload đồng thời?
24. Khi chuyển Boot 3.5 sang Boot 4, tại sao cần kiểm tra chính xác codec classes, property metadata và tests thay vì chỉ copy snippets?

## 26. Tài liệu chính thức

- [Spring Framework 6.2 — Reactive Core, codecs và Limits](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html): DataBuffer, HTTP codecs, memory limits, multipart và sensitive logging.
- [Spring Framework 6.2 — WebFlux Config / HTTP Message Codecs](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html): `WebFluxConfigurer#configureHttpMessageCodecs`, `ServerCodecConfigurer`.
- [Spring Framework 6.2 — @RequestBody](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestbody.html): `HttpMessageReader`, Mono/Flux request bodies.
- [Spring Framework 6.2 — @ResponseBody](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/responsebody.html): `HttpMessageWriter` và reactive response.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html): `spring.http.codecs.*` và `spring.webflux.multipart.*`.
- [Spring Boot 3.5 — HttpCodecsProperties Javadoc](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/http/codec/HttpCodecsProperties.html): `@ConfigurationProperties("spring.http.codecs")`.
- [Spring Framework 6.2 — DataBufferUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html): `join`, `read`, `write` và buffer lifecycle.
- [Spring Framework 6.2 — DataBufferLimitException Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferLimitException.html): lỗi vượt aggregation limit.
- [Spring Framework 6.2 — Multipart Content](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/multipart-forms.html): `FilePart`, `PartEvent` và resource ownership.
- [Spring Framework — WebClient Configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-builder.html): `WebClient.Builder.codecs`.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): HTTP reactive integration testing.
- [Spring Framework — HTTP Codecs API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/ServerCodecConfigurer.html): default codecs/custom codecs.
- [Project Reactor — StepVerifier](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html): signal/exception tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-006">@EnableWebFlux, WebFluxConfigurer và cấu hình hạ tầng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring WebFlux — @EnableWebFlux, WebFluxConfigurer và cấu hình hạ tầng

## 1. Mục tiêu bài học

Khi xây API phản ứng bằng Spring WebFlux, các controller `@RestController` và functional routes chỉ là phần dễ nhìn thấy. Phía dưới còn một hạ tầng gồm handler mappings, handler adapters, result handlers, HTTP message readers/writers, converters, CORS, static resource handling, exception handling và những cầu nối tới web server.

Spring Framework cung cấp `@EnableWebFlux` và interface `WebFluxConfigurer` để bật/tùy chỉnh hạ tầng ấy. **Spring Boot lại có auto-configuration riêng** nên cách dùng hai thành phần này trong ứng dụng Boot khác với cách viết một Spring Framework application thuần.

**Nguyên tắc cần nhớ ngay từ đầu:**

- Với **Spring Boot WebFlux**: mặc định chỉ dùng `spring-boot-starter-webflux` và properties. Muốn thêm cấu hình thì viết `@Configuration implements WebFluxConfigurer` nhưng **không thêm `@EnableWebFlux`**, để giữ tiện ích Boot.
- Với **Spring Framework cấu hình thủ công** hoặc khi muốn chịu trách nhiệm quản lý hạ tầng WebFlux: dùng `@EnableWebFlux`. Trong Boot, điều này chuyển hướng quyền kiểm soát và làm nhiều phần auto-configuration WebFlux của Boot không còn được áp dụng như trước; phải tự bảo đảm các hành vi cần thiết.
- Khi cần thay thế những infrastructure beans sâu hơn API `WebFluxConfigurer`: tìm hiểu `DelegatingWebFluxConfiguration`/`WebFluxConfigurationSupport` và Boot-specific customization hooks, chỉ dùng khi có lý do rõ.

Bài này giúp bạn:

1. Vẽ đúng request processing flow WebFlux, phân biệt nó với DispatcherServlet/Spring MVC.
2. Hiểu vai trò và mức độ can thiệp của `@EnableWebFlux`, `WebFluxConfigurer` và Spring Boot auto-configuration.
3. Cấu hình formatters/converters, CORS, codecs, static resources, path matching, content negotiation, view resolvers và blocking execution.
4. Hiểu cấu hình nào thuộc WebFlux infrastructure, cấu hình nào thuộc Reactor Netty, WebClient, Spring Security hoặc Spring Boot.
5. Tránh các lỗi điển hình như tự bật `@EnableWebFlux` rồi mất mapping static, custom codec vô tình xóa JSON defaults, mở CORS wildcard với credentials hay block Netty event-loop.
6. Có một ví dụ **Java 17 / Spring Boot 3.5.0 / Maven** có thể dựng lại độc lập, cùng `WebTestClient` kiểm tra API, CORS và static resources.
7. Biết các tiêu chí quyết định khi nào chỉ dùng properties, khi nào implement `WebFluxConfigurer`, khi nào cần customizer/advanced config.

**Phạm vi:** Spring Framework 6.2, Spring Boot 3.5, Reactor trên WebFlux reactive application. Không dùng tên lớp `WebMvcConfigurer`, `@EnableWebMvc`, `DispatcherServlet` hoặc `HandlerInterceptor` của Servlet MVC như vật thay thế cho hạ tầng WebFlux.

## 2. Spring WebFlux hoạt động ở tầng nào?

### 2.1. Request flow

Mô hình được đơn giản hóa:

~~~text
Client HTTP
   |
   v
Server: Reactor Netty / supported runtime
   |
   v
HttpHandler
   |
   v
WebHttpHandlerBuilder-built processing chain
   |-- WebFilter(s)
   |-- WebExceptionHandler(s)
   |-- WebSession / locale-related handling
   |
   v
DispatcherHandler
   |
   +-- HandlerMapping: tìm handler
   |
   +-- HandlerAdapter: gọi handler
   |
   +-- HandlerResultHandler: ghi kết quả
   |       |
   |       +-- HttpMessageWriter -> JSON/text/streaming
   |       +-- View rendering
   |
   v
Reactive HTTP response (Publisher/Mono<Void>)
~~~

Tên lớp chính:

| Thành phần | Vai trò |
| --- | --- |
| `HttpHandler` | Hợp đồng mức thấp giữa web server runtime và framework |
| `HttpWebHandlerAdapter` | Adapter HTTP request/response vào WebHandler chain |
| `WebFilter` | Filter reactive trước/sau web handling (không phải Servlet Filter) |
| `WebExceptionHandler` | Xử lý lỗi trong reactive WebHandler chain |
| `DispatcherHandler` | Dispatcher chính của Spring WebFlux |
| `HandlerMapping` | Chọn controller, functional route hoặc resource handler |
| `HandlerAdapter` | Thực thi handler thích hợp |
| `HandlerResultHandler` | Render kết quả handler; có body writers và view handlers |
| `ServerCodecConfigurer` | Cấu hình các HTTP readers/writers |
| `ConversionService` | Chuyển đổi string/number/date/value cho binding |
| `RequestedContentTypeResolver` | Chọn media type phản hồi dựa trên request/config |
| `ResourceWebHandler` | Phục vụ static resources |

**Sự khác biệt với Spring MVC:** WebFlux dùng `DispatcherHandler`, không phải `DispatcherServlet`. `WebFilter` xử lý reactive request, không dựa vào Servlet FilterChain. Trong WebFlux, blocking operations trên event-loop làm suy giảm throughput, dù controller có trả về `Mono`.

### 2.2. Annotated controller và functional endpoint đều thuộc WebFlux

WebFlux hỗ trợ hai phong cách:

- Annotated: `@RestController`, `@GetMapping`, `@RequestBody Mono<T>`, `Flux<T>`.
- Functional: `RouterFunction<ServerResponse>`, `HandlerFunction<ServerResponse>`.

Các cấu hình ở WebFlux hạ tầng có thể ảnh hưởng annotated handlers, functional endpoints, static resources hoặc một nhóm nhỏ tùy hook. Ví dụ path prefix bằng `PathMatchConfigurer.addPathPrefix` chủ yếu dành cho annotated controller mappings; không được tự động suy ra nó đổi mọi `RouterFunction` path.

### 2.3. Reactive không đồng nghĩa phép màu không blocking

Một controller trả `Mono.fromCallable(() -> jdbcRepository.findAll())` vẫn gọi JDBC đồng bộ bên trong. Nếu không offload đúng chỗ, nó có thể block event-loop. WebFlux cho phép các adapters non-blocking (R2DBC, reactive WebClient), nhưng chúng không tự biến mọi SDK/JPA/JDBC blocking thành reactive.

## 3. @EnableWebFlux thực sự làm gì?

Khi dùng Spring Framework Java config thuần:

~~~java
@Configuration
@EnableWebFlux
public class ReactiveWebConfiguration {
}
~~~

Annotation `@EnableWebFlux` import `DelegatingWebFluxConfiguration`. Nó cung cấp những WebFlux infrastructure beans, phát hiện các bean triển khai `WebFluxConfigurer` và áp dụng tùy chỉnh.

Điểm chính:

- Kích hoạt cấu hình cần thiết cho annotated controllers và phần liên quan của WebFlux.
- Đăng ký handler mappings/adapters/result handlers và các mặc định thích hợp tùy dependencies trên classpath.
- Cho phép các `WebFluxConfigurer` tham gia tùy chỉnh.
- Không đồng nghĩa tự tạo Reactor Netty server hoặc mọi chức năng Boot. Việc khởi động web server, management, config binding và vận hành là trách nhiệm tầng runtime/Boot nếu sử dụng.

### 3.1. Tại sao Boot thường không cần @EnableWebFlux?

Spring Boot phát hiện reactive web application khi dùng `spring-boot-starter-webflux` (và không bị MVC starter chọn ưu tiên), rồi tự cấu hình WebFlux phù hợp:

- HTTP codecs, Jackson và các Boot `CodecCustomizer`.
- Static resource locations, mapping và welcome page.
- Error handling mặc định.
- Các properties liên quan formatters, static assets, reactive paths và một số infrastructure integration.
- Server/runtime management do Boot kết hợp.

Nếu muốn thêm CORS, converters, path matching hoặc codec cap, bạn không cần giành quyền cấu hình toàn bộ hệ thống.

**Cách được Spring Boot tài liệu khuyến nghị:**

~~~java
@Configuration
class MyWebFluxConfiguration implements WebFluxConfigurer {
    // override những methods cần thiết
    // KHÔNG gắn @EnableWebFlux
}
~~~

Đây là một phần class minh họa; ví dụ chạy đầy đủ ở mục 12.

### 3.2. Điều gì xảy ra khi thêm @EnableWebFlux trong Boot?

Trong ứng dụng Boot, `@EnableWebFlux` biểu thị mong muốn **take complete control** đối với WebFlux configuration. Boot WebFlux auto-configuration có cơ chế lùi lại khi hạ tầng đã do người dùng khai báo (không còn tự áp đặt các customizations mặc định theo cùng đường đi).

Hệ quả có thể thấy:

- Các tùy biến codec/mapping/static resources của Boot không còn được tự động áp dụng giống trước.
- Properties và auto-configuration dành cho Boot WebFlux có thể không còn điều khiển các hạ tầng tương ứng như trước; phải tự xác minh.
- Một static `index.html` vốn hoạt động trước có thể mất mapping/welcome behavior nếu chưa đăng ký lại resource handlers.
- Việc tích hợp Jackson, view resolution, exception handling hoặc argument resolvers có thể khác; cần test lại.
- Spring Security, Reactor Netty và Actuator thuộc những cấu hình liên quan khác: không nên khẳng định cứ bật `@EnableWebFlux` là mọi thứ của Boot đều biến mất. Ảnh hưởng chính là **WebFlux framework auto-configuration** và các customizations của nó.

Không phải `@EnableWebFlux` là annotation “sai”. Nó phù hợp khi kiến trúc cần full control và đội phát triển chấp nhận bảo trì/tái cấu hình hạ tầng tương ứng.

### 3.3. Bảng lựa chọn ba cấp độ

| Mục tiêu | Cách ưu tiên | Mức độ kiểm soát |
| --- | --- | --- |
| API WebFlux thông thường | Starter + Boot properties | Giữ auto-config gần như trọn vẹn |
| Thêm CORS/Converter/resource/codec limit/path rules | `@Configuration implements WebFluxConfigurer`, **không @EnableWebFlux** | Tùy chỉnh API chính thống |
| Cấu hình codec tương thích Boot trên nhiều layers | `CodecCustomizer` Bean hoặc Boot properties | Bổ sung vào Boot hạ tầng |
| Tùy chỉnh HttpHandler xây dựng bởi Boot | `WebHttpHandlerBuilderCustomizer` | Boot-specific hook chuyên biệt |
| Thay framework WebFlux infrastructure mạnh | `@EnableWebFlux`, custom config | Tự chịu trách nhiệm defaults |
| Overriding bean declarations ở mức framework | Extend `DelegatingWebFluxConfiguration` hoặc nghiên cứu `WebFluxConfigurationSupport` | Advanced, cần kiểm tra tương thích |

### 3.4. Spring Boot chọn MVC hay WebFlux thế nào?

Nếu ứng dụng có đồng thời:

~~~xml
<artifactId>spring-boot-starter-web</artifactId>
<artifactId>spring-boot-starter-webflux</artifactId>
~~~

Spring Boot thường chọn **Spring MVC** cho web application type. Điều này hữu ích khi ứng dụng MVC chỉ muốn dùng reactive `WebClient`. Nhưng nếu bài tập kỳ vọng `DispatcherHandler`, `WebFilter` và reactive server, hai starters có thể khiến kết quả sai.

Với một demo WebFlux thuần, **chỉ đưa `spring-boot-starter-webflux`**, không thêm `spring-boot-starter-web`. Nếu có lý do bắt buộc giữ cả hai, có thể ép reactive application type qua Boot API/config, nhưng phải kiểm tra toàn bộ auto-configuration, libraries và tests. Tránh ép chỉ để “cho lỗi biến mất”.

## 4. WebFluxConfigurer: interface tùy chỉnh hạ tầng

Import:

~~~java
import org.springframework.web.reactive.config.WebFluxConfigurer;
~~~

Một class có thể implement:

~~~java
@Configuration
class WebConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Configure route-specific CORS...
    }
}
~~~

Methods của interface có default implementations; chỉ override những phần bạn muốn. Có thể tạo **nhiều WebFluxConfigurer beans** và framework phối hợp áp dụng. Tuy nhiên những registrations của nhiều modules có thể tương tác, trùng path hoặc conflict; trong dự án lớn nên quản trị ownership và kiểm thử thứ tự/kết quả thực tế.

### 4.1. Các extension points quan trọng

| Method | Mục đích | Thời điểm dùng |
| --- | --- | --- |
| `addFormatters(FormatterRegistry)` | Đăng ký converters/formatters cho argument/data binding | Custom ID, date-time, locale |
| `addCorsMappings(CorsRegistry)` | CORS theo URL patterns | Browser frontend khác origin |
| `configurePathMatching(PathMatchConfigurer)` | Đường dẫn, prefix cho annotated controllers | Thêm API prefix có chọn lọc |
| `configureHttpMessageCodecs(ServerCodecConfigurer)` | Readers/writers, memory cap, codecs | JSON/body/streaming |
| `configureContentTypeResolver(RequestedContentTypeResolverBuilder)` | Media type negotiation | Content negotiation policy |
| `addResourceHandlers(ResourceHandlerRegistry)` | Định tuyến static assets và cache policy | CSS/JS/images/docs |
| `configureViewResolvers(ViewResolverRegistry)` | Render HTML/templates | SSR với FreeMarker/Thymeleaf |
| `configureArgumentResolvers(ArgumentResolverConfigurer)` | Thêm resolver cho custom controller args | TenantContext/auth context |
| `getValidator()` | Cung cấp Validator khi cần | Validation integration |
| `getMessageCodesResolver()` | Thay resolution của validation error codes | Form/validation messages |
| `configureBlockingExecution(BlockingExecutionConfigurer)` | Chọn executor và predicate cho blocking controller methods | Legacy blocking handlers |
| `getWebSocketService()` | Điều chỉnh service xử lý WebSocket handshake | WebSocket infra chuyên biệt |

**Lưu ý version:** Bảng nhắm Spring Framework 6.2. Những APIs thêm ở phiên bản mới hơn không mặc nhiên tồn tại ở Boot 3.5/Framework 6.2. Khi copy từ trang “current” của Spring Framework 7, cần kiểm API phiên bản.

### 4.2. addFormatters: custom conversion

Giả sử HTTP route dùng:

~~~text
GET /api/books/42
~~~

Bạn muốn `@PathVariable BookId` được parse thành một Java record:

~~~java
record BookId(long value) {}
~~~

Cấu hình:

~~~java
@Override
public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, BookId.class,
            text -> new BookId(Long.parseLong(text)));
}
~~~

Cần `org.springframework.format.FormatterRegistry`. Khi controller khai báo:

~~~java
@GetMapping("/books/{id}")
Mono<BookDto> get(@PathVariable BookId id) {
    return Mono.just(new BookDto(id.value(), "Book"));
}
~~~

Đây là fragment trong cấu hình/controller. Khi `id` là chuỗi không parse được, binding có thể phát lỗi 400 theo framework exception handling; đừng map parse error thành 500 hoặc chấp nhận overflow mù quáng. Trong thực tế có thể dùng converter riêng, validate range ID và test invalid values.

Không tự viết parser custom cho `LocalDate`/`Instant` nếu Java time formatters hoặc `@DateTimeFormat(iso=...)` đã phù hợp. Với form fields, format theo Locale có thể khác ISO JSON serialization — hai tầng khác nhau.

### 4.3. addCorsMappings: quyền cross-origin của browser

Ví dụ:

~~~java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins("https://app.example.com",
                    "http://localhost:5173")
            .allowedMethods("GET", "POST")
            .allowedHeaders("Content-Type", "Authorization")
            .exposedHeaders("X-Request-ID")
            .allowCredentials(true)
            .maxAge(1800);
}
~~~

Một credentialed CORS response cần `Access-Control-Allow-Origin` tương ứng **một origin cụ thể**, không thể dùng `allowedOrigins("*")` cùng `allowCredentials(true)`. Spring Framework có `allowedOriginPatterns` nhưng một pattern quá rộng như `"*"` có thể mở credentialed CORS cho origins không đáng tin. Cho phép origin ≠ cho phép user: API vẫn cần authentication, authorization, CSRF/cookie security.

**Quan trọng:** CORS không giải quyết server-to-server authorization. `curl` không thực thi browser same-origin policy. Với Spring Security reactive, cần thiết kế thứ tự WebFilterChain và Security CORS integration để preflight OPTIONS không bị yêu cầu session/cookie; không copy cấu hình Servlet `HttpSecurity` vào WebFlux (phải dùng `ServerHttpSecurity`).

### 4.4. configurePathMatching: thêm prefix một cách có chọn lọc

Ví dụ:

~~~java
@Override
public void configurePathMatching(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(
            "/api",
            HandlerTypePredicate.forAnnotation(RestController.class));
}
~~~

Import `org.springframework.web.method.HandlerTypePredicate` và `org.springframework.web.bind.annotation.RestController`.

Controller khai báo:

~~~java
@RestController
class BookController {
    @GetMapping("/books/{id}")
    Mono<BookDto> find(@PathVariable long id) {
        return Mono.just(new BookDto(id, "Book"));
    }
}
~~~

Tuyến truy cập thực tế trở thành:

~~~text
GET /api/books/42
~~~

**Đừng thêm `/api` hai lần**: nếu controller đã khai báo `@RequestMapping("/api")` và configurer lại gắn prefix `/api`, route có thể thành `/api/api/...`. Hãy chọn một owner cho prefix.

Phương thức `addPathPrefix` áp dụng theo predicate của **controller type**, không tự biến đổi mọi router function/static resource. Spring WebFlux dùng parsed `RequestPath`/`PathPatternParser` semantics; đừng mang thói quen suffix `.json` matching, `AntPathMatcher` và legacy URL decode flags của MVC vào WebFlux.

### 4.5. configureHttpMessageCodecs: giới hạn bộ nhớ và readers/writers

Codecs chuyển request body thành object và object thành body. Trong WebFlux, đọc/ghi qua:

~~~text
HTTP request Flux<DataBuffer>
   -> HttpMessageReader / Decoder
   -> DTO/Mono<T>

DTO/Flux<T>
   -> Encoder / HttpMessageWriter
   -> HTTP response body
~~~

Để đặt giới hạn buffering cho phần lớn default codecs:

~~~java
@Override
public void configureHttpMessageCodecs(
        ServerCodecConfigurer configurer) {
    configurer.defaultCodecs().maxInMemorySize(256 * 1024);
}
~~~

Import `org.springframework.http.codec.ServerCodecConfigurer`.

Mốc này giới hạn lượng bytes được aggregate trong memory theo codec operations nhất định; **không phải** overall server RAM limit, request concurrency limit, Netty receive buffer size hoặc một cách thay thế upload quota. Với payload vượt mức, bạn có thể gặp `DataBufferLimitException` khi có operation buffer/aggregate. Cần xác minh exception/status/error contract và tests, không tự giả định mọi request 300KB chắc chắn HTTP 413 trong mọi codec path.

**Không dùng**:

~~~java
configurer.registerDefaults(false);
~~~

chỉ để “tăng giới hạn” hoặc “tối ưu hiệu năng”: call này vô hiệu default readers/writers, dễ làm JSON request/response fail vì không có codec phù hợp. Chỉ thay thế toàn bộ defaults khi bạn hiểu và tự đăng ký bộ codecs cần thiết.

### 4.6. Boot CodecCustomizer và properties

Spring Boot 3.5 cũng hỗ trợ `CodecCustomizer` và properties dưới `spring.http.codecs.*`, cùng tích hợp Jackson. Nếu nhu cầu đã có property Boot tương đương, nên ưu tiên property để giảm code và tránh nhiều layers cùng set giá trị.

Đây là fragment minh họa một customizer:

~~~java
@Bean
CodecCustomizer codecLimits() {
    return configurer ->
            configurer.defaultCodecs().maxInMemorySize(256 * 1024);
}
~~~

Trong Spring Boot 3.5, kiểu `CodecCustomizer` được cung cấp trong package phù hợp phiên bản Boot; khi dùng cần kiểm import Javadoc của chính version dependency, không copy nhầm từ Boot 4.0/4.1 vì packages đã thay đổi ở một số module. Nếu project đã có `WebFluxConfigurer.configureHttpMessageCodecs` cho cùng settings, chỉ chọn **một owner** để dễ kiểm soát precedence.

### 4.7. configureContentTypeResolver: content negotiation

Trong annotated controllers, framework dùng requested content type resolver từ HTTP request. Thông thường nên dựa trên `Accept` header và `produces/consumes`:

~~~http
GET /api/books/42
Accept: application/json
~~~

Controller:

~~~java
@GetMapping(path = "/books/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE)
Mono<BookDto> get(@PathVariable long id) { ... }
~~~

Trong config:

~~~java
@Override
public void configureContentTypeResolver(
        RequestedContentTypeResolverBuilder builder) {
    // Chỉ thêm policy thật sự được thiết kế.
}
~~~

Đây là fragment (cần import `org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder`), không phải ví dụ runnable độc lập. Không vội bật path-extension negotiation (`.json`/`.xml`) nếu có thể dùng Accept vì có rủi ro liên quan URL format/ambiguity và cache behavior. Kiểm 406 Not Acceptable/415 Unsupported Media Type trong API tests khi customize.

### 4.8. addResourceHandlers: static assets có cache

Ví dụ:

~~~java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/guide/**")
            .addResourceLocations("classpath:/guide-assets/")
            .setCacheControl(CacheControl
                    .maxAge(Duration.ofMinutes(5))
                    .cachePublic());
}
~~~

Imports cần có: `java.time.Duration`, `org.springframework.http.CacheControl`, `org.springframework.web.reactive.config.ResourceHandlerRegistry`.

Giả sử file:

~~~text
src/main/resources/guide-assets/intro.txt
~~~

HTTP route:

~~~text
GET /guide/intro.txt
~~~

được `ResourceWebHandler` xử lý, không cần controller.

Chỉ phục vụ các classpath/static locations đã chọn; không map tùy tiện file system root (`file:/`) hoặc upload directory chứa PII. Với static JS/CSS versioned URLs, có thể dùng ResourceResolver/VersionResourceResolver và cache dài hơn; nếu file name không versioned mà vẫn cache một năm, người dùng có thể thấy phiên bản cũ sau deploy.

### 4.9. configureViewResolvers và template rendering

WebFlux không chỉ phục vụ JSON. Với Thymeleaf/FreeMarker/Mustache hoặc HTML responses, view resolvers có thể được cấu hình:

~~~java
@Override
public void configureViewResolvers(ViewResolverRegistry registry) {
    // Thêm view resolver khi thực sự cần, hoặc dùng Boot auto-config.
}
~~~

Đây là fragment không có resolver đăng ký cụ thể. Với Spring Boot, thêm template starter phù hợp và đặt HTML templates trong `src/main/resources/templates` thường đủ. Không viết resolver thủ công nếu chỉ muốn mặc định.

### 4.10. configureArgumentResolvers: custom method arguments

Một ứng dụng multi-tenant có thể muốn:

~~~java
@GetMapping("/me")
Mono<ProfileDto> me(CurrentTenant tenant) { ... }
~~~

Muốn WebFlux tự resolve `CurrentTenant`, có thể đăng ký custom `HandlerMethodArgumentResolver` qua:

~~~java
@Override
public void configureArgumentResolvers(
        ArgumentResolverConfigurer configurer) {
    configurer.addCustomResolver(new TenantArgumentResolver());
}
~~~

Fragment này cần implement `TenantArgumentResolver` của WebFlux, không dùng servlet `HandlerMethodArgumentResolver` từ MVC package. Resolver phải non-blocking, không tin request header tenant tùy ý mà thiếu authentication/authorization. Dùng `ReactiveSecurityContextHolder` hoặc service/lookup được tin cậy.

### 4.11. Validator và validation

WebFlux có thể validate input models qua Jakarta Bean Validation nếu provider phù hợp nằm trên classpath. `WebFluxConfigurer#getValidator()` có thể dùng khi cần Validator riêng, nhưng trong Boot thường chỉ cần dependency starter validation và annotations.

Ví dụ:

~~~java
record CreateBookRequest(
        @NotBlank String title
) {}
~~~

Controller:

~~~java
@PostMapping("/books")
Mono<BookDto> create(@Valid @RequestBody Mono<CreateBookRequest> input) {
    return input.map(this::save);
}
~~~

Đây là fragment minh họa: phương thức `save`, imports/validation provider cần tồn tại. Với reactive arguments, cơ chế validation và exception types phụ thuộc cách khai báo signature; kiểm `WebExchangeBindException`/validation exception semantics trước khi map status/error payload.

**Đừng thay Validator** nếu chỉ muốn dùng `@NotBlank` thông thường. Kiểm validation error messages không trả rejected secret values cho client.

### 4.12. configureBlockingExecution: bridge cho blocking controller methods

Spring Framework 6.2 có:

~~~java
@Override
public void configureBlockingExecution(
        BlockingExecutionConfigurer configurer) {
    configurer.setExecutor(myAsyncTaskExecutor);
}
~~~

Fragment: `myAsyncTaskExecutor` là một `AsyncTaskExecutor` được quản lý. Framework cho phép cấu hình executor và predicate xác định những controller methods được xem là blocking (mặc định liên quan reactive adapter registry và return types).

Điểm quan trọng:

- **Không đồng nghĩa mọi blocking call trong `Mono.map` tự được offload.**
- Nếu bạn dùng JDBC/SDK blocking trong reactor chain, cần cân nhắc `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` hoặc chuyển sang non-blocking library.
- Blocking execution executor phải có limits, thread-name, metrics, shutdown policy và timeout; không tạo thread-per-request vô hạn.
- `WebFluxConfigurer.configureBlockingExecution` cấu hình handler invocation cho các methods được nhận diện, **không phải thay thế cho Reactor scheduler strategy**.

### 4.13. WebSocket service

`WebFluxConfigurer#getWebSocketService()` hỗ trợ customization của WebSocket handshake service trong WebFlux. Đừng cố cấu hình WebSocket qua CORS mappings như một biện pháp an ninh duy nhất. WebSocket có Origin validation, authentication và message-level auth riêng; Spring Security reactive và gateway/proxy rules cũng quan trọng.

## 5. WebFluxConfigurer không quản lý mọi thứ

Một lỗi kiến trúc là dồn bất cứ thiết lập WebFlux/Netty nào vào WebFluxConfigurer, dù chúng thuộc các tầng khác.

| Nhu cầu | Tầng phù hợp |
| --- | --- |
| Controller routing / converters / CORS | `WebFluxConfigurer` |
| HTTP body codecs | `WebFluxConfigurer.configureHttpMessageCodecs`, `CodecCustomizer`, Boot properties |
| Client-side WebClient timeouts | `WebClient.Builder`, Reactor Netty HttpClient, connector |
| Server port/TLS/compression | Boot server properties, `NettyReactiveWebServerFactory`/customizer |
| Authentication/authorization | Spring Security `SecurityWebFilterChain` |
| Cross-cutting request filter | `WebFilter` |
| Correlation/tracing/logging | Observability/Micrometer, Reactor Context/WebFilter |
| Business caching | Service/repository cache, HTTP headers/cache rules |
| Functional routes | `RouterFunction` bean, RouterFunctions DSL |
| Global errors | `@RestControllerAdvice`, `ErrorWebExceptionHandler` |
| Backpressure and scheduling | Reactor operators/Schedulers, source design |
| Reactive DB transaction | R2DBC/reactive transaction manager |
| Proxy forwarding | Forwarded header transformer/trusted proxy + Boot config |

**Chọn cấu hình ở đúng tầng.** Ví dụ WebFluxConfigurer CORS không cấp auth, một Netty timeout không tương đương Mono.timeout cho toàn bộ pipeline, và Reactor Context không giống Servlet ThreadLocal.

## 6. Cấu trúc cấu hình hạ tầng và thứ tự xử lý

### 6.1. Multiple WebFluxConfigurer beans

Framework có thể nhận nhiều `WebFluxConfigurer`. Ví dụ:

~~~java
@Configuration
class CorsWebConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.com");
    }
}

@Configuration
class CodecWebConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(512 * 1024);
    }
}
~~~

Hai đoạn config này là fragments có imports tương ứng. Tuy framework tổng hợp customizers, bạn vẫn phải kiểm các xung đột:

- Cùng một path có nhiều CORS definitions khó hiểu.
- Một bean đặt limit 256KB, bean khác 1MB; final value có thể phụ thuộc thứ tự applied registrations.
- Hai pathPrefix rules dẫn tới route bất ngờ hoặc conflict.
- Custom codec thay default readers/writers mà một module khác dựa vào.
- Nhiều handler mappings/predicates đè nhau khiến route khó tìm.

Với cấu hình rủi ro bảo mật, tốt nhất có **một owner được kiểm soát**, hợp nhất policy rõ và regression tests.

### 6.2. @Order không phải lời giải cho mọi xung đột

Spring hỗ trợ ordering cho một số config beans và handler/filter chains, nhưng chỉ thêm `@Order(0)` không tự giải thích semantics khi nhiều customizers cùng sửa mutable config. Điều quan trọng là kiểm final HandlerMapping/codec/CORS policy và hành vi HTTP thực, không đoán "config cuối cùng thắng".

### 6.3. Request mapping và ResourceHandlerMapping

Các `HandlerMapping` được đăng ký bởi WebFlux infrastructure có ordering nhằm chọn annotated controllers, functional routes, resource handling, view/welcome page. Resource handlers thường là fallback để không chiếm route API. Nhưng một broad mapping như `/**` hoặc custom RouterFunction có thể tạo bất ngờ nếu ordering không được kiểm.

Không đăng ký một `RouterFunction` bắt mọi path chỉ để thêm metrics, khiến static/error handlers mất cơ hội chạy. Cross-cutting concern nên dùng `WebFilter`/observation layer phù hợp.

## 7. Spring Boot properties thường gặp

Một số cấu hình Boot 3.5:

~~~properties
# Server:
server.port=8080

# Static assets:
spring.webflux.static-path-pattern=/assets/**
spring.web.resources.static-locations=classpath:/static/

# Formatters (tuỳ nhu cầu):
spring.webflux.format.date=iso
spring.webflux.format.time=iso
spring.webflux.format.date-time=iso
~~~

Đây là **ví dụ minh họa properties**, không phải file properties áp dụng trong demo ở mục 12. Nếu dùng `spring.webflux.static-path-pattern=/assets/**` thì mặc định static resources sẽ nằm dưới `/assets/**` thay vì `/**`. Nếu dùng `spring.web.resources.static-locations`, danh sách custom sẽ thay default locations; phải kiểm welcome page và file locations.

**Lưu ý:** Giá trị `iso` cho Spring Boot format properties cần được kiểm theo Boot 3.5 configuration binding/format settings của version bạn dùng; với APIs quan trọng, sử dụng DateTimeFormatterRegistrar rõ ràng hoặc test parse input. Không dùng `spring.mvc.*` để cấu hình reactive WebFlux routes.

### 7.1. Codec configuration properties

Spring Boot 3.5 documentation mô tả nhóm `spring.http.codecs.*`, các `CodecCustomizer` và Jackson config liên quan. Trong các dự án nâng cấp từ Boot 2.x/3.x, có thể gặp property keys cũ; đối chiếu **configuration metadata của đúng phiên bản Boot** thay vì đoán giá trị nào đang có hiệu lực.

Nếu cần limit aggregation trong demo, Java API ở mục 12 đảm bảo cấu hình được áp dụng qua `WebFluxConfigurer` mà không phụ thuộc property key/version alias.

### 7.2. global WebFlux base path vs controller prefix

Boot có thể cung cấp cấu hình application base path riêng với annotated controller prefix. Khi chọn base path ở tầng server/framework, nó có thể ảnh hưởng các loại mappings khác như static assets/functional routes. Còn:

~~~java
configurer.addPathPrefix(
    "/api",
    HandlerTypePredicate.forAnnotation(RestController.class));
~~~

chỉ nhắm controller types khớp predicate. Đừng dùng **cả hai** cho cùng một mục tiêu và dẫn đến `/api/api/...`. Ghi rõ ai quản lý public mount prefix: reverse proxy, Boot base path hay controller mapping.

## 8. CORS + Security trong WebFlux

CORS được xử lý theo browser protocol: Origin, preflight OPTIONS và response headers. `WebFluxConfigurer#addCorsMappings` thường cung cấp chính sách cho WebFlux HandlerMapping; khi thêm Spring Security reactive cần kiểm preflight không bị ngăn quá sớm.

### 8.1. Origin cụ thể và credentials

Ví dụ safe-ish cho SPA được tin:

~~~java
registry.addMapping("/api/**")
        .allowedOrigins("https://app.example.com")
        .allowedMethods("GET", "POST")
        .allowedHeaders("Content-Type", "Authorization")
        .allowCredentials(true);
~~~

**Phải hiểu rằng:** với cookie session credentials, CORS cho phép một origin đọc response khi user có credentials hợp lệ. Nó **không tự bảo vệ CSRF**. Cần cookie SameSite/Secure/HttpOnly, server auth/authz và CSRF policy. Đừng dùng `allowedOriginPatterns("*")` để “né” lỗi wildcard+credentials.

### 8.2. Reactive security khác Servlet security

Với WebFlux, code security cơ bản dùng `ServerHttpSecurity` và `SecurityWebFilterChain`, không phải `HttpSecurity` và `SecurityFilterChain` của Servlet.

Fragment:

~~~java
@Bean
SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http
            .cors(Customizer.withDefaults())
            .authorizeExchange(exchanges -> exchanges
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().permitAll())
            .build();
}
~~~

Fragment **không runnable độc lập**, cần `spring-boot-starter-security`, auth provider, CSRF policy và imports `ServerHttpSecurity`/`SecurityWebFilterChain`/`Customizer`. Không thêm fragment này vào demo ở mục 12 nếu chưa thiết kế cơ chế login, vì khi có starter-security, endpoints có thể bị chặn khác kỳ vọng.

### 8.3. Preflight không có cookie auth

Browser preflight OPTIONS không mang credentials của actual request theo cách thông thường, nên yêu cầu session cho mọi OPTIONS trước CORS handling có thể trả 401/403. Hãy để reactive CORS handling chạy đúng thứ tự với security, không “sửa” bằng cách tắt authorization toàn bộ API.

### 8.4. Vary, exposed headers và HTTP caching

Nếu server trả ACAO khác nhau theo Origin, cần `Vary: Origin` để shared caches không tái dùng response với origin khác sai. Khi frontend cần đọc `X-Request-ID` ngoài response header safelist, dùng `exposedHeaders("X-Request-ID")`. Đừng coi exposure của header là authentication permission.

## 9. Codecs, streaming và memory safety

### 9.1. Giới hạn maxInMemorySize có mục đích gì?

WebFlux có thể đọc JSON/XML/aggregated text bodies bằng codecs. Nếu không giới hạn, nhiều concurrent requests lớn sẽ tiêu tốn direct/heap memory. Cấu hình:

~~~java
configurer.defaultCodecs().maxInMemorySize(256 * 1024);
~~~

giúp một số operations không aggregate payload vượt ngưỡng đã định. Không có nghĩa server **không thể stream file 1GB**: nếu streaming giữ dữ liệu trong chunks và không buffer toàn bộ, policy khác. Nếu upload/file/JSON sâu cần những giới hạn riêng:

- Reverse proxy body size.
- Netty/body decoder limits.
- Memory aggregation cap.
- Business validation/JSON nesting depth.
- Concurrency and rate limits.
- Per-user quotas và multipart configuration.

### 9.2. Pooled DataBuffer ownership

Nếu bạn custom `HttpMessageReader`/`HttpMessageWriter` hoặc trực tiếp xử lý `Flux<DataBuffer>`, một số DataBuffers dựa trên pooled reference-counted Netty buffers. Chỉ release khi ứng dụng sở hữu chúng, dùng `DataBufferUtils.release(...)` và discard policies thích hợp. **Không double-release** buffers mà framework còn cần/đã tự release.

Đừng custom toàn bộ codecs chỉ vì muốn đổi một JSON property name. Sử dụng Jackson annotations, Boot `spring.jackson.*` hoặc Jackson configuration ở nơi phù hợp.

### 9.3. SSE và Flux<DataBuffer>

WebFlux hỗ trợ streaming response `Flux<ServerSentEvent<T>>` và raw binary streams. `ServerCodecConfigurer` ảnh hưởng writers/encoders của một số media types. Khi custom codecs hoặc message readers, cần test:

- `application/json` 200/400/415/406.
- `text/event-stream` streaming không bị buffer vô hạn.
- Error handling trước và sau response commit.
- Cancellation và pooled DataBuffer release.
- Slow client/backpressure và proxy buffering.

Một codec override có thể hoạt động với GET JSON nhỏ nhưng làm SSE hoặc multipart hỏng; test đa dạng payload.

## 10. Spring WebFlux static assets

### 10.1. Mặc định trong Boot

Spring Boot WebFlux thông thường hỗ trợ static files trong classpath locations:

~~~text
classpath:/static/
classpath:/public/
classpath:/resources/
classpath:/META-INF/resources/
~~~

Ví dụ đặt:

~~~text
src/main/resources/static/index.html
src/main/resources/static/app.js
~~~

là cách đơn giản để phục vụ welcome page/static assets nếu auto-config còn nguyên và routing không trùng.

### 10.2. Custom resource handler

Một custom handler bổ sung:

~~~java
registry.addResourceHandler("/guide/**")
        .addResourceLocations("classpath:/guide-assets/")
        .setCacheControl(
                CacheControl.maxAge(Duration.ofMinutes(5))
                        .cachePublic());
~~~

Chỉ tạo file ở `src/main/resources/guide-assets/intro.txt`, không cần controller. Với cache, nên tách:

- Versioned immutable JS/CSS: public long max-age.
- Unversioned docs có thể đổi: short max-age/no-cache + validators.
- Private files: **không đưa vào public static handler**; phục vụ qua authenticated controller/service có policy.

### 10.3. Security và path traversal

Framework resource handling hỗ trợ path normalization và location protections, nhưng đừng mở `file:/` tới cả filesystem hay một thư mục upload có tài liệu người dùng riêng tư. Không dựa vào file extension hoặc CORS để bảo vệ file nhạy cảm.

### 10.4. Static resources sau @EnableWebFlux

Nếu thêm `@EnableWebFlux` để “bật WebFlux” trong Boot và static `/index.html` bắt đầu 404, nguyên nhân có thể là Boot auto-config static mapping/welcome page đã không còn áp dụng. Cần quay về Boot defaults hoặc khai báo resource handlers theo mong muốn. **Đừng sửa bằng cách thêm controller GET /** nếu chỉ muốn phục hồi static auto configuration.

## 11. Advanced configuration và WebHttpHandlerBuilderCustomizer

### 11.1. Khi WebFluxConfigurer chưa đủ

`WebFluxConfigurer` giúp thêm registry-based configuration mà không phải biết từng infrastructure bean. Nhưng khi cần override sâu hơn:

- Cách tạo/tùy chỉnh HandlerMappings hoặc HandlerAdapters.
- Thứ tự WebExceptionHandler hoặc WebFilter chain.
- Tùy chỉnh HttpHandler building logic.
- Custom framework object registration và behavior ngoài interface extension points.

Spring Framework cho phép lấy quyền sâu hơn bằng `DelegatingWebFluxConfiguration` hoặc `WebFluxConfigurationSupport`. Tuy nhiên **đây không phải cách mặc định** trong Boot, có thể làm thay đổi baseline auto-config và tăng maintenance cost.

### 11.2. WebHttpHandlerBuilderCustomizer trong Boot

Tài liệu Spring Boot WebFlux cung cấp `WebHttpHandlerBuilderCustomizer` khi muốn tùy chỉnh `HttpHandler` đã được auto-configured. Đây là Hook Boot-specific, khác WebFluxConfigurer. Nên xem exact API và thứ tự filter/error handler trước khi can thiệp.

Không tự dựng một `HttpHandler` khác hoặc đổi adapter Netty ở production chỉ để log một header; dùng WebFilter/observability trước.

### 11.3. WebFilter, WebExceptionHandler

- `WebFilter` là reactive cross-cutting filter, thích hợp cho header/correlation/context và chính sách yêu cầu.
- `WebExceptionHandler` là low-level error handler.
- `@RestControllerAdvice` xử lý lỗi controller rõ ràng, gần API contract hơn.

Không xử lý exceptions bằng cách subscribe fire-and-forget trong filter; return Publisher để framework quản lý cancellation và backpressure. Khi HTTP response committed, handler không thể đổi status/headers đã gửi.

### 11.4. Không override framework internals tùy tiện

WebFlux infrastructure tự thay đổi theo Spring versions. Nếu override beans từ `DelegatingWebFluxConfiguration` bằng custom subclasses, cần kiểm:

- Bean names/types và conditional auto-config.
- Ordering handler mappings/result handlers.
- Codec availability (JSON, SSE, multipart).
- Reactive Security integration.
- Static resources, CORS, views.
- Error handling, observability.
- Upgrade path từ Framework 6.2 sang 7.x.

Chỉ thực hiện khi có tests hồi quy cho mọi lớp liên quan và team chấp nhận maintenance.

## 12. Dự án Java 17 / Spring Boot 3.5.0 chạy được

Ứng dụng minh họa nhiều extension points trong **một cấu hình WebFluxConfigurer không có @EnableWebFlux**:

- `configurePathMatching` thêm `/api` cho annotated REST controllers.
- `addFormatters` chuyển path string sang `BookId`.
- `addCorsMappings` giới hạn trusted frontend origins.
- `configureHttpMessageCodecs` đặt maxInMemorySize 256KiB cho default codecs.
- `addResourceHandlers` phục vụ `/guide/intro.txt` từ `classpath:/guide-assets/`.
- Giữ Boot static default: `/boot-health.txt` từ `classpath:/static/`.
- Controller trả JSON, nhận POST JSON và một header `X-Request-ID`.
- Tests `WebTestClient` kiểm route prefix/converter, CORS preflight, resource handler, preserved Boot static behavior.

Không thêm `spring-boot-starter-web`, không thêm `@EnableWebFlux`. Đây là điểm quan trọng của ví dụ.

### 12.1. Cấu trúc

~~~text
webflux-infrastructure-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/WebFluxInfrastructureApplication.java
    │   └── resources/
    │       ├── static/boot-health.txt
    │       └── guide-assets/intro.txt
    └── test/java/demo/WebFluxInfrastructureTest.java
~~~

### 12.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>webflux-infrastructure-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
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
~~~

### 12.3. WebFluxInfrastructureApplication.java

Lưu **toàn bộ** code dưới đây vào `src/main/java/demo/WebFluxInfrastructureApplication.java`. Chỉ class khởi động là public top-level; các record/class còn lại package-private nên đặt chung file hợp lệ trong Java 17.

~~~java
package demo;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class WebFluxInfrastructureApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxInfrastructureApplication.class, args);
    }
}

record BookId(long value) {
}

record BookResponse(long id, String title) {
}

record CreateBookRequest(String title) {
}

@Configuration(proxyBeanMethods = false)
class MyWebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configurePathMatching(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api",
                HandlerTypePredicate.forAnnotation(RestController.class));
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, BookId.class,
                source -> new BookId(Long.parseLong(source)));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.com",
                        "http://localhost:5173")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Authorization")
                .exposedHeaders("X-Request-ID")
                .allowCredentials(true)
                .maxAge(1800);
    }

    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(256 * 1024);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/guide/**")
                .addResourceLocations("classpath:/guide-assets/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofMinutes(5))
                                .cachePublic());
    }
}

@RestController
class BookController {

    @GetMapping(path = "/books/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<BookResponse> book(@PathVariable BookId id) {
        if (id.value() <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "ID must be positive"));
        }
        return Mono.just(
                new BookResponse(id.value(), "Book #" + id.value()));
    }

    @PostMapping(path = "/books",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<BookResponse>> create(
            @RequestBody Mono<CreateBookRequest> request) {
        return request.map(input -> {
            if (input.title() == null || input.title().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Title required");
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("X-Request-ID",
                            UUID.randomUUID().toString())
                    .body(new BookResponse(1001L, input.title()));
        });
    }
}
~~~

**Giải thích runnable code:**

1. `@SpringBootApplication` chọn Boot auto-configuration và khởi động Reactor Netty web server từ starter-webflux.
2. `MyWebFluxConfig implements WebFluxConfigurer` nhưng **không @EnableWebFlux** giữ Boot baseline.
3. `@GetMapping("/books/{id}")` được prefix thành `/api/books/{id}` do `HandlerTypePredicate.forAnnotation(RestController.class)`.
4. `BookId` là value object, converter parse string path variable bằng `Long.parseLong`.
5. CORS chỉ áp dụng `/api/**`, GET/POST và trusted origins; credentials yêu cầu origin cụ thể.
6. `ServerCodecConfigurer` giới hạn một số aggregation operations; default JSON codecs vẫn được giữ.
7. `ResourceHandlerRegistry` bổ sung `/guide/**`, không tự xóa static mapping mặc định của Boot.
8. POST sinh `X-Request-ID` và expose header cho JavaScript cross-origin được phép, nhưng UUID demo không thay distributed tracing thực.
9. 1001L là ID giả cố định để minh họa, **không phải persistence**; muốn API thực cần repository, transactions và uniqueness.
10. Code không thêm Spring Security để tests có thể tập trung vào WebFlux infrastructure; production bắt buộc thiết kế auth/authz/CSRF phù hợp.

### 12.4. Tạo static files

`src/main/resources/guide-assets/intro.txt`:

~~~text
WebFlux infrastructure guide
~~~

`src/main/resources/static/boot-health.txt`:

~~~text
Boot static resource remains available
~~~

Hai files không được đặt trong `src/main/webapp`. Spring Boot reactive app dùng classpath static resources thay vì cấu trúc WAR Servlet truyền thống.

### 12.5. Khởi chạy

Trong thư mục có pom.xml:

~~~bash
mvn spring-boot:run
~~~

Gọi:

~~~bash
curl -i http://localhost:8080/api/books/42
~~~

Response body tương đương:

~~~json
{"id":42,"title":"Book #42"}
~~~

Gọi URL **không có prefix**:

~~~bash
curl -i http://localhost:8080/books/42
~~~

Dự kiến không khớp controller mapping; 404 được trả về theo default routing (có thể có static handler fallback).

Thử POST:

~~~bash
curl -i -X POST http://localhost:8080/api/books \
  -H 'Content-Type: application/json' \
  --data '{"title":"Reactive Spring"}'
~~~

Response HTTP 201, một header X-Request-ID được server tạo, body:

~~~json
{"id":1001,"title":"Reactive Spring"}
~~~

Đọc static guide:

~~~bash
curl -i http://localhost:8080/guide/intro.txt
~~~

Body:

~~~text
WebFlux infrastructure guide
~~~

Đọc Boot static mặc định:

~~~bash
curl -i http://localhost:8080/boot-health.txt
~~~

Body:

~~~text
Boot static resource remains available
~~~

Đây là tiêu chí test hữu ích để chứng minh Boot auto-configuration vẫn còn khi không dùng `@EnableWebFlux`.

### 12.6. Curl kiểm CORS preflight

Một browser SPA trên `http://localhost:5173` gửi POST JSON có thể dùng OPTIONS preflight:

~~~bash
curl -i -X OPTIONS http://localhost:8080/api/books \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Response thường có:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: content-type
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 1800
~~~

Headers có thể khác thứ tự/casing; đừng so raw text cứng. Khi Origin không được phép:

~~~bash
curl -i -X OPTIONS http://localhost:8080/api/books \
  -H 'Origin: https://attacker.example' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
~~~

Spring WebFlux CORS processing thường từ chối preflight (ví dụ 403). Browser không gửi actual POST khi preflight không thành công, nhưng `curl` vẫn nhận HTTP response vì curl không thực thi SOP.

## 13. Kiểm thử WebTestClient

Tạo file `src/test/java/demo/WebFluxInfrastructureTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WebFluxInfrastructureTest {

    @Autowired
    WebTestClient http;

    @Test
    void prefixedControllerAndConverterWork() {
        http.get().uri("/api/books/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(42)
                .jsonPath("$.title").isEqualTo("Book #42");
    }

    @Test
    void invalidIdGetsBadRequest() {
        http.get().uri("/api/books/0").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void postJsonKeepsDefaultCodec() {
        http.post().uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateBookRequest("Reactive"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("X-Request-ID")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reactive");
    }

    @Test
    void trustedOriginPreflightIsAllowed() {
        http.options().uri("/api/books")
                .header(HttpHeaders.ORIGIN,
                        "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173")
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true");
    }

    @Test
    void untrustedOriginPreflightIsRejected() {
        http.options().uri("/api/books")
                .header(HttpHeaders.ORIGIN,
                        "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void customStaticResourceWorks() {
        http.get().uri("/guide/intro.txt")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueContains(
                        HttpHeaders.CACHE_CONTROL, "max-age=300")
                .expectBody(String.class)
                .value(body -> org.junit.jupiter.api.Assertions.assertTrue(
                        body.contains("WebFlux infrastructure guide")));
    }

    @Test
    void bootDefaultStaticResourceIsStillAvailable() {
        http.get().uri("/boot-health.txt")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.junit.jupiter.api.Assertions.assertTrue(
                        body.contains("Boot static resource remains available")));
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

**Vì sao chọn WebTestClient với RANDOM_PORT?** Bộ kiểm thử kết nối tới HTTP server được Boot khởi động, qua reactive network runtime, nên phù hợp để kiểm hạ tầng, static files và CORS thực tế hơn việc chỉ tự gọi controller method. `WebTestClient` còn có mode bindToApplicationContext cho slice/unit tests khi không cần socket.

**Giới hạn:** Sample dùng `spring-boot-starter-test`, không tích hợp gateway hoặc Spring Security; tests không thay thế security integration, reverse proxy CORS/headers và production traffic validation. Các imports dư trong ví dụ test nếu IDE báo warning có thể được gỡ mà không ảnh hưởng test behavior.

### 13.1. Một lỗi dễ bỏ qua ở test CORS

Test OPTIONS preflight cần cả:

- `Origin`.
- `Access-Control-Request-Method`.
- `Access-Control-Request-Headers` nếu actual request gửi custom/non-safelisted headers.

Chỉ gửi `Origin` cùng method OPTIONS có thể bị hiểu thành một actual cross-origin OPTIONS request, không phải preflight. Test phải phản ánh browser behavior.

### 13.2. Kiểm giới hạn codec

Muốn test `maxInMemorySize`, tạo payload thực sự cần được aggregate/decoded vượt giới hạn, rồi kiểm exception/status theo setup. Đừng hardcode assert 413 nếu framework default error mapping của phiên bản đang dùng không cam kết status ấy. Với production, quyết định một error contract rõ (ví dụ HTTP 413 khi body vượt giới hạn policy) và test integration.

### 13.3. Test negative cases

Bên cạnh happy-path, nên bổ sung:

- `/books/42` không tự được map khi đã prefix `/api`.
- Origin `https://attacker.example` không được cấp ACAO.
- POST JSON thiếu title trả 400 chứ không 500.
- Converter ID `abc` trả 400 (tùy binding semantics đã cấu hình).
- Unsupported content type POST trả 415; Accept không phù hợp có thể trả 406.
- File ngoài `guide-assets` không được route công khai.
- Body vượt memory cap theo codec aggregate path.
- Nếu thêm Spring Security, preflight/actual auth, CSRF và 401/403 đều đúng.
- Nếu thêm `@EnableWebFlux`, static resource regression test có thể phơi ra Boot auto-config loss.

## 14. So sánh với @EnableWebFlux: ví dụ cấu hình thay thế

Một Spring Framework app không dựa vào Boot WebFlux auto-config có thể định nghĩa:

~~~java
@Configuration
@EnableWebFlux
class StandaloneWebFluxConfig implements WebFluxConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://app.example.com");
    }

    @Override
    public void configureHttpMessageCodecs(
            ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(256 * 1024);
    }
}
~~~

Đây là **phương án thay thế**, **không thêm vào demo mục 12**. Nếu bạn copy cấu hình này vào Boot app, có thể thay đổi baseline static/resource/codec configurations do Boot cung cấp. Để giữ Boot defaults, bỏ annotation `@EnableWebFlux` và chỉ giữ WebFluxConfigurer.

### 14.1. Khi thật sự cần full control

Dùng khi:

- Framework integration rất đặc thù không đáp ứng bởi Boot properties/WebFluxConfigurer.
- Ứng dụng xây môi trường runtime riêng không dùng Boot autoconfig.
- Cần thay thế hạ tầng beans và chấp nhận bảo trì regression tests.
- Security/compliance demands dẫn tới một custom exact infrastructure configuration đã được kiểm toán.

Không dùng khi chỉ muốn thêm một converter hoặc CORS mapping.

### 14.2. DelegatingWebFluxConfiguration và WebFluxConfigurationSupport

`@EnableWebFlux` nhập `DelegatingWebFluxConfiguration`, cấu hình này kế thừa `WebFluxConfigurationSupport` và áp dụng các `WebFluxConfigurer` implementations.

Trong advanced mode, có thể trực tiếp extend `DelegatingWebFluxConfiguration` và override infrastructure bean declarations khi cần. Nhưng:

- Đây là thay đổi sâu vào framework lifecycle.
- Dễ mất Boot defaults nếu trộn sai.
- Cần đọc Javadoc cho exact version.
- Override chỉ được xác nhận an toàn sau khi kiểm path mapping, codecs, errors, security, static resources, blocking handlers và HTTP integration.

## 15. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân hay gặp | Cách xử lý |
| --- | --- | --- |
| Tự thêm @EnableWebFlux, static index.html biến mất | Boot WebFlux auto-config không còn cung cấp defaults giống trước | Bỏ @EnableWebFlux nếu không cần full control |
| @EnableWebFlux rồi JSON codec behavior khác | Boot CodecCustomizer/properties không còn áp dụng như mong đợi | Cấu hình lại hoặc trả về Boot managed config |
| API GET có /api/api/... | Prefix tại configurer và controller cùng khai báo | Chọn một owner cho prefix |
| CORS preflight 403 | Origin/method/header không có trong policy | Kiểm đúng preflight headers |
| CORS preflight 401 khi có Security | Reactive security filter chạy trước CORS handling phù hợp | Integrate CORS đúng với ServerHttpSecurity |
| Wildcard Origin + credentials error | Cấu hình CORS không hợp lệ | Exact allowlist; tránh pattern quá rộng |
| Dùng WebMvcConfigurer nhưng không có tác dụng | App chạy WebFlux, không phải MVC | Dùng WebFluxConfigurer |
| Có cả starter-web và starter-webflux nhưng handler không reactive | Boot chọn MVC theo default selection | Chỉ dùng starter-webflux hoặc chọn app type có chủ đích |
| JSON body lớn ném DataBufferLimitException | Codec aggregation vượt giới hạn | Review cap/payload/streaming/413 policy |
| Gọi registerDefaults(false) và mất JSON | Vô hiệu default codecs | Chỉ bổ sung codec cần, giữ defaults |
| Static asset 404 | File không nằm classpath/route mismatch | Kiểm resource location/path pattern |
| CORS đúng nhưng cookie không gửi | SameSite/Secure/cross-site restrictions | Debug browser cookies, not CORS alone |
| Blocking JDBC làm request chậm hàng loạt | Chặn event-loop | Native reactive DB hoặc boundedElastic |
| @Order không giải quyết codec conflict | Hai configs thay cùng mutable setting | Một owner, test final behavior |
| WebFilter đọc ServletRequest không được | Dùng sai stack abstraction | WebFlux ServerWebExchange/WebFilter |
| 500 sau khi streaming response bắt đầu | Response committed, không thay status được | In-band error or cancel/log |
| App gọi Codecs từ Boot 4 docs trong Boot 3.5 | Khác packages/property API | Đọc Javadocs đúng version |
| Async cancel không dọn resource | Resource không scope theo subscription | using/usingWhen/doFinally và tests |

## 16. Checklist code review / vận hành

- [ ] Ứng dụng thật sự được Boot chọn **REACTIVE** chứ không vô tình MVC.
- [ ] Không thêm `@EnableWebFlux` trong Boot chỉ để bật WebFlux.
- [ ] Configurer bổ sung những chức năng cụ thể; không override mọi thứ.
- [ ] Các Boot properties được dùng đúng phiên bản và tests chứng minh chúng có hiệu lực.
- [ ] Codec memory cap, body size/codec limits, concurrency quotas và backpressure được xem xét.
- [ ] CORS origin/method/header allowlist đủ hẹp; Security/CSRF/cookies tách trách nhiệm.
- [ ] API prefix chỉ có một owner và không làm mất static/functional routes.
- [ ] Converter/formatters xử lý invalid input thành lỗi phù hợp.
- [ ] Static assets nằm ở đúng classpath, có cache policy, không lộ private uploads.
- [ ] View resolvers/Jackson serializers giữ behavior sau customization.
- [ ] Không đặt blocking I/O trên event-loop; có executor/scheduler policy.
- [ ] Error handler không gửi stacktrace/secrets cho client, xử lý committed response đúng.
- [ ] Multi-configurer conflicts được quản trị bằng ownership và integration tests.
- [ ] WebTestClient kiểm positive/negative routes, CORS, static resources, codecs.
- [ ] Nên có observability: HTTP latency/status, route, scheduler blocking, buffer memory và server resource usage.
- [ ] Nếu chọn full-control @EnableWebFlux, có regression suite rộng và tài liệu lý do.

## 17. Bài tập tự luyện

**Bài 1 — Ba lựa chọn:** Khi nào chỉ dùng Boot properties? Khi nào implement WebFluxConfigurer? Khi nào dùng @EnableWebFlux? Nêu trade-off của mỗi mức.

**Bài 2 — Startup behavior:** Một project có cả starter-web và starter-webflux. Vì sao WebFluxConfigurer không tạo ra reactive dispatcher? Cách sửa dependency?

**Bài 3 — CORS:** Viết addCorsMappings chỉ cho https://app.example.com gọi GET/POST /api/** với credentials. Tại sao allowedOrigins("*") không phù hợp?

**Bài 4 — Prefix:** Controller có @GetMapping("/books/{id}"), configurer addPathPrefix("/api", RestController). URL public là gì? Nếu controller đã /api/books thì sao?

**Bài 5 — Converter:** Tạo record BookId(long value) và converter String->BookId. Test GET /api/books/42 và invalid negative ID.

**Bài 6 — Codecs:** Dùng configureHttpMessageCodecs đặt 256KiB. Test một request body phải aggregate >256KiB và phân tích DataBufferLimitException, không mặc định status luôn 413.

**Bài 7 — Static resources:** Thêm /guide/** từ classpath:/guide-assets/ và cache 5 phút, đồng thời giữ /boot-health.txt mặc định từ Boot.

**Bài 8 — Security:** Dùng ServerHttpSecurity để bảo vệ /api/**, kết hợp CORS preflight, cookie CSRF và 401/403 handling.

**Bài 9 — @EnableWebFlux regression:** Thêm @EnableWebFlux vào demo, chạy tests, ghi lại những behavior Boot từng quản lý bị ảnh hưởng. Thiết kế lựa chọn bỏ annotation hoặc tái tạo mapping hợp lệ.

**Bài 10 — Functional routing:** Tạo một RouterFunction GET /fn/ping và kiểm configurePathMatching addPathPrefix không tự động áp cùng prefix cho functional route.

**Bài 11 — Blocking:** Có một legacy JDBC call trong controller Mono chain. So sánh configureBlockingExecution và Mono.fromCallable(...).subscribeOn(boundedElastic()).

**Bài 12 — Observability:** Khi API trả 500 do codecs config sai hoặc path rewrite sai, những metrics/logs nào giúp phân biệt config errors và business errors?

**Bài 13 — Custom codecs:** Tại sao registerDefaults(false) nguy hiểm? Khi muốn custom Jackson date format, nên bắt đầu từ đâu thay vì xóa toàn bộ readers/writers?

**Bài 14 — Multi-configurer:** Hai beans cùng set maxInMemorySize khác nhau. Thiết kế cơ chế ownership/centralized property/test để tránh behavior phụ thuộc ordering.

## 18. Ghi nhớ

- **WebFlux infrastructure** quản lý handler mappings/adapters/result handlers, codecs, converters, CORS, resource handling và exception processing.
- **@EnableWebFlux** import `DelegatingWebFluxConfiguration` để cung cấp cấu hình Framework; trong Boot chỉ dùng khi muốn kiểm soát toàn bộ WebFlux setup.
- **WebFluxConfigurer** là extension point để thêm từng cấu hình; với Boot thường implement dưới `@Configuration` **không @EnableWebFlux**.
- Boot starter và auto-config mang theo static resources, codecs, Jackson, error handling và tiện ích production; không bỏ chúng vì một annotation không cần thiết.
- `configureHttpMessageCodecs` chỉnh HTTP readers/writers; `defaultCodecs().maxInMemorySize` không phải toàn bộ request/server memory quota.
- `addCorsMappings` chỉ là policy browser cross-origin, **không thay authentication, authorization hoặc CSRF**.
- `configurePathMatching` cho annotated controllers, không tự gắn prefix mọi functional route/static resource.
- `addResourceHandlers` giúp custom static assets; security/caching strategy cần rõ.
- Reactor Netty, WebClient, Security, cancellation/backpressure và blocking execution có tầng cấu hình riêng.
- Chỉ nâng lên `@EnableWebFlux`/advanced config khi có lý do được kiểm chứng và regression tests.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — WebFlux Configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html): @EnableWebFlux, WebFluxConfigurer, formatters, codecs, resources, path matching, blocking execution và advanced mode.
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): Boot WebFlux auto-config, Boot-specific properties, customizers, static resources, Security/error handling.
- [Spring Framework 6.2 — WebFlux Overview](https://docs.spring.io/spring-framework/reference/6.2/web/webflux.html).
- [Spring Framework 6.2 — DispatcherHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html): HandlerMapping/HandlerAdapter/HandlerResultHandler.
- [Spring Framework 6.2 — WebFlux CORS](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-cors.html).
- [WebFluxConfigurer 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/config/WebFluxConfigurer.html).
- [ServerCodecConfigurer 6.2 Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/ServerCodecConfigurer.html).
- [Spring Boot 3.5 — Auto-configuration](https://docs.spring.io/spring-boot/3.5/reference/using/auto-configuration.html).
- [Spring Framework 6.2 — Path Matching](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html#webflux-config-path-matching).
- [Spring Security — Reactive CORS](https://docs.spring.io/spring-security/reference/6.5/reactive/integrations/cors.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-007">WebSession, LocaleContext và Reactor Context</a>

<details>
<summary>Xem chi tiết</summary>

# WebSession, LocaleContext và Reactor Context trong Spring WebFlux

## 1. Mục tiêu và ngữ cảnh

Khi chuyển từ Spring MVC Servlet sang **Spring WebFlux**, ta vẫn phải xử lý trạng thái người dùng, ngôn ngữ giao diện, tracing, correlation ID, security và các thông tin cần đi qua pipeline bất đồng bộ. Nhưng không thể mang nguyên các giả định `HttpSession`, `LocaleContextHolder` hay `ThreadLocal` của mô hình request-thread cũ sang Reactor mà không kiểm tra vòng đời dữ liệu.

Spring WebFlux cung cấp ba khái niệm liên quan nhưng **khác bản chất**:

- **`WebSession`**: phiên server-side, mang session attributes qua **nhiều HTTP requests** của cùng client khi có session ID hợp lệ; có vòng đời và kho lưu trữ.
- **`LocaleContext`**: thông tin ngôn ngữ/vùng miền (và tùy resolver có thể có múi giờ) được xác định cho **HTTP exchange hiện tại**; thông thường dựa trên `Accept-Language`.
- **Reactor `Context`/`ContextView`**: tập metadata immutable-key/value đi theo **một reactive subscription**, truyền qua async operators mà không đòi hỏi một thread cố định. Nó không phải database session hay HTTP session.

Sau bài này, bạn sẽ có thể:

1. Phân biệt **`WebSession`**, `ServerWebExchange`, `LocaleContext`, Reactor `Context` và `ThreadLocal` theo scope/lifetime.
2. Dùng `ServerWebExchange.getSession()` hoặc argument `WebSession` để đọc, ghi, invalidate, rotate session ID và đặt idle timeout đúng cách.
3. Biết **đọc session không đồng nghĩa tạo cookie phiên mới**; session chỉ khởi động khi ghi attributes hoặc `start()` rõ ràng.
4. Dùng `ServerWebExchange.getLocaleContext()` và `LocaleContextResolver`; hiểu `AcceptHeaderLocaleContextResolver` mặc định và lý do `setLocaleContext` có thể không được hỗ trợ.
5. Sử dụng `Mono.deferContextual`, `contextWrite` và `WebFilter` truyền request correlation ID qua `publishOn`/`flatMap` đúng Reactor semantics.
6. Tích hợp Spring Security reactive qua `ReactiveSecurityContextHolder` mà không nhầm với `SecurityContextHolder`/Servlet session.
7. Xây ứng dụng **Java 17+, Spring Boot 3.x + Spring WebFlux**, có stateful endpoint, Locale, Reactor Context và kiểm thử HTTP thật bằng `WebTestClient`.
8. Nhận diện rủi ro lưu dữ liệu mutable trong session, client không gửi cookie, mất context do subscribe thủ công, leak session ID, cache/CORS/SameSite và tình huống scale nhiều instance.

**Phiên bản thực hành:** Spring Framework **6.x**, Spring Boot **3.x**, Reactor 3.x, Java 17+. Ví dụ dùng WebFlux trên reactive HTTP server; không thêm `spring-boot-starter-web` để tránh Boot chọn MVC khi đồng thời có hai web starters. Cú pháp/conventions có thể đổi khi lên Spring Framework 7/Boot 4; tra tài liệu theo version đang sử dụng.

## 2. Bản đồ vòng đời: ba context không thể thay thế nhau

| Đối tượng | Phạm vi | Tồn tại qua HTTP requests? | Giữ dữ liệu ở đâu? | Truy cập thông thường |
| --- | --- | --- | --- | --- |
| `WebSession` | Session của client/server | **Có**, nếu session còn hạn và ID được trả lại | `WebSessionStore`, mặc định in-memory hoặc Spring Session backend | `exchange.getSession()`/`WebSession` |
| `ServerWebExchange` attributes | **Một** HTTP exchange | Không tự động | Map request/exchange-scoped | `exchange.getAttributes()` |
| `LocaleContext` | Locale của HTTP exchange | Không tự động | Resolver đọc request/strategy | `exchange.getLocaleContext()` |
| Reactor `Context` | **Một reactive subscriber/subscription** | Không tự động | Context gắn vào subscriber chain | `Mono.deferContextual`, `contextWrite` |
| Java `ThreadLocal` | Một JVM thread | Không có bảo đảm phù hợp với request/reactive signals | Thread hiện tại | `ThreadLocal.get()` |
| Reactive security context | Reactive subscription/request security chain | Chỉ khi security có cơ chế lưu/auth thích hợp | Reactor Context, security repository khi cấu hình | `ReactiveSecurityContextHolder` |

Một request thực tế có thể cùng lúc có cả bốn thứ:

~~~text
Browser ---- Cookie: SESSION=... ----> ServerWebExchange
                                        |
                                        +-- WebSession
                                        |     savedTheme=dark
                                        |
                                        +-- LocaleContext
                                        |     locale=vi-VN (Accept-Language)
                                        |
                                        +-- exchange.attributes
                                        |     requestId=req-001
                                        |
                                        +-- WebFilter
                                              |
                                              v
                                        Reactor subscription Context
                                              requestId=req-001
                                              security context ...
                                              |
                                  publishOn / flatMap / WebClient...
                                              |
                                              v
                                        HTTP response
~~~

**Quan trọng:** Reactor Context không biến dữ liệu thành trạng thái lâu dài của user. Khi request kết thúc, request-scoped Context không tự được lưu. Nếu cần giữ một tùy chọn cho request sau, chọn phương án phù hợp như WebSession, database/user profile hoặc cookie tùy use case và threat model.

## 3. WebSession là gì?

`org.springframework.web.server.WebSession` là hợp đồng session server-side của WebFlux. Nó **không phải** `jakarta.servlet.http.HttpSession`, và việc dùng WebFlux trên một Servlet container cũng không có nghĩa controller reactive nên phụ thuộc trực tiếp HttpSession.

Các API đáng nhớ:

| API | Ý nghĩa | Chú ý |
| --- | --- | --- |
| `getId()` | ID của session | Cần coi là dữ liệu nhạy cảm, không trả ra JSON/log |
| `getAttributes()` | Map chứa attributes | Có thể chứa mutable objects, cần đồng bộ/thiết kế state |
| `getAttribute(name)` | Đọc một attribute | Không bảo đảm đã tồn tại hoặc đúng kiểu |
| `getAttributeOrDefault(...)` | Đọc kèm fallback | Hữu ích cho tùy chọn nhỏ |
| `getCreationTime()` | Mốc tạo session | Không phải thời gian đăng nhập |
| `getLastAccessTime()` | Mốc truy cập gần nhất | Tùy store và hoạt động phiên |
| `getMaxIdleTime()` | Khoảng idle tối đa | Không phải cookie max-age hoặc absolute expiry |
| `setMaxIdleTime(Duration)` | Cập nhật thời gian idle | Cân nhắc policy chung |
| `isStarted()` | Session đã được start chưa | Chỉ `getSession()` không tự start |
| `start()` | Buộc session bắt đầu | Dẫn tới cookie/session ID khi lưu |
| `invalidate()` | Invalidate và xóa session storage | **Trả `Mono<Void>`**, phải compose/subscription |
| `changeSessionId()` | Rotate session ID | **Trả `Mono<Void>`**, cần hoàn thành thật |
| `save()` | Lưu session ở store | Thường được framework tự gọi trước khi response commit |

### 3.1. Lấy session mà không khởi tạo phiên lưu trữ

`ServerWebExchange.getSession()` trả:

~~~java
Mono<WebSession> sessionMono = exchange.getSession();
~~~

Dạng method minh họa với `ServerWebExchange exchange`. WebSessionManager bảo đảm có một `WebSession` mới hoặc session khớp ID client (nếu tồn tại và chưa hết hạn), nhưng **đọc/nhận một đối tượng WebSession không tự tạo session cookie**.

Ví dụ đọc:

~~~java
public Mono<String> theme(ServerWebExchange exchange) {
    return exchange.getSession()
            .map(session ->
                    session.getAttributeOrDefault("theme", "light"));
}
~~~

Nếu request chưa có session started, đoạn này thường không bắt browser lưu session mới. Khi request có cookie phiên cũ hợp lệ, nó có thể đọc `theme` được lưu trước đó.

### 3.2. Thêm attribute → session được start ngầm

~~~java
public Mono<Void> chooseTheme(ServerWebExchange exchange) {
    return exchange.getSession()
            .doOnNext(session ->
                    session.getAttributes().put("theme", "dark"))
            .then();
}
~~~

Khi attribute được thêm vào session mới, `WebSession` thường được start ngầm. Trong WebFlux mặc định, framework gọi lưu session đúng lifecycle trước khi response commit. **Không tự `session.save().block()` trên event loop**; `save()` vốn là reactive và framework chủ động quản lý.

Hãy nhớ: thêm attribute vào Map là một thao tác trong memory của session object, không biến Map thành transaction DB. Với distributed session store, persistence/serialization/synchronization là vấn đề riêng.

### 3.3. start() khi không có attribute

~~~java
public Mono<String> start(ServerWebExchange exchange) {
    return exchange.getSession().map(session -> {
        session.start();
        return "started";
    });
}
~~~

Đây là method minh họa. `start()` ép phiên bắt đầu kể cả Map attributes đang trống. Chỉ dùng khi nghiệp vụ cần thực sự tạo session; **đừng start mọi request theo thói quen**, làm tăng số session/cookie và chi phí storage không cần thiết.

### 3.4. Invalidate và rotate session ID đúng kiểu reactive

~~~java
@PostMapping("/logout")
public Mono<Void> logout(WebSession session) {
    return session.invalidate();
}
~~~

~~~java
@PostMapping("/rotate")
public Mono<Void> rotate(WebSession session) {
    return session.changeSessionId();
}
~~~

Hai snippet là handler minh họa, cần class controller và imports. Vì `invalidate()`/`changeSessionId()` trả `Mono<Void>`, **phải return hoặc compose Publisher**. Chỉ gọi method rồi bỏ giá trị:

~~~java
session.invalidate(); // sai: không subscribe/return
return Mono.empty();
~~~

có thể khiến công việc invalidate chưa được thực thi. Tương tự, không tự gọi `.subscribe()` để fire-and-forget vì có thể phá error propagation, cancellation và lifecycle response.

Session ID rotation là một lớp phòng vệ session fixation khi authentication state thay đổi; **không tự ý coi chỉ gọi rotate là toàn bộ thiết kế bảo mật**. Khi ứng dụng dùng Spring Security, để security framework quản lý session/authentication policies theo đúng chain.

## 4. WebSessionManager, WebSessionStore và cookie/session ID

Luồng quản trị session:

~~~text
Browser
  | Cookie: SESSION=<opaque-id>
  v
WebSessionIdResolver
  | resolveSessionIds(exchange)
  v
WebSessionManager (DefaultWebSessionManager)
  | lookup hoặc tạo session object
  v
WebSessionStore
  | retrieve/store/expire
  v
WebSession.getAttributes()

Before HTTP response commit:
  WebSession.save() -> store + WebSessionIdResolver response cookie
~~~

Các thành phần:

- **`WebSessionManager`**: lấy session theo HTTP exchange. Mặc định `DefaultWebSessionManager`.
- **`WebSessionIdResolver`**: lấy/gửi ID giữa client và server. Mặc định thường dùng `CookieWebSessionIdResolver`, cookie tên **`SESSION`** trong cấu hình WebFlux thông thường.
- **`WebSessionStore`**: lưu và truy xuất session. Mặc định WebFlux có thể dùng **in-memory store**, không dùng chung giữa nhiều instances.
- **Spring Session**: có thể cung cấp persistent/distributed reactive session store (ví dụ Redis) khi cấu hình dependency và repository tương thích.

**Đừng nhầm cookie chứa session ID với dữ liệu session:** ở phương án mặc định, giá trị `theme` hay `cart` được giữ server-side; cookie chủ yếu chứa một ID ngẫu nhiên/opaque dùng để lookup. Ai lấy được một session ID hợp lệ có thể thử chiếm phiên, nên áp dụng TLS, Secure/HttpOnly/SameSite, session rotation và bảo vệ XSS/CSRF phù hợp.

### 4.1. Đặt thời gian sống

Hai khái niệm khác nhau:

- **Idle timeout trên WebSession**: nếu không có user activity sau `getLastAccessTime()` trong khoảng `maxIdleTime`, session bị coi là expired.
- **Cookie Max-Age/Expires**: browser còn giữ/gửi cookie bao lâu. Cookie vẫn tồn tại không bảo đảm server-side session chưa expired, và cookie bị xóa không nhất thiết ngay lập tức xóa storage server khi không có logout request.

Spring Boot 3.5 có nhóm cấu hình reactive session, ví dụ:

~~~properties
server.reactive.session.timeout=30m
server.reactive.session.cookie.same-site=lax
server.reactive.session.cookie.secure=true
~~~

Đây là cấu hình **minh họa deployment HTTPS production**, không phải cấu hình bắt buộc cho bài chạy trên `http://localhost`: cookie `Secure` có thể không được client gửi qua HTTP thường. Tên properties phải kiểm tra theo **Spring Boot version đang chạy** (Boot 3.x có khác biệt giữa các minor versions và auto-configuration). Những thuộc tính được dẫn theo Boot 3.5 docs; nếu dùng Boot 3.0/3.1 hãy kiểm tra reference tương ứng.

Không đặt `setMaxIdleTime(Duration.ofMinutes(...))` trên mọi request nếu đã có policy tập trung mà không có lý do; mất kiểm soát session TTL giữa các endpoints. Một số stores có expiration semantics riêng và cleanup chậm, cần giám sát.

### 4.2. Cookie security và CORS

Khi frontend chạy ở origin khác gọi backend có session cookie:
- `fetch` có thể cần `credentials: "include"` và server CORS cho phép origin cụ thể + `Access-Control-Allow-Credentials: true`.
- Cookie vẫn phải thỏa `SameSite`, `Secure`, `Domain`, `Path`, expiry và third-party cookie restrictions.
- **CORS không thay CSRF protection**. Với cookie auth, unsafe methods POST/PUT/DELETE vẫn cần cơ chế chống CSRF thích hợp.
- `HttpOnly` hạn chế JavaScript đọc cookie; không ngăn browser gửi cookie trong requests đủ điều kiện.
- Không sử dụng `allowedOrigins("*")` cùng credentialed CORS; Spring/browser không chấp nhận tổ hợp ấy.

Nếu test bằng `curl` không lưu cookie, mỗi request sẽ có một WebSession mới. Nếu test bằng browser có cookie nhưng session store là memory ở node A còn load balancer chuyển qua node B, phiên có thể mất; cần sticky sessions đúng chính sách hoặc shared session repository.

## 5. Session attributes: kiểu dữ liệu, concurrency và memory

Tránh lưu cả object graph nặng hoặc object khó serialize trong session. Hợp lý hơn:

~~~java
session.getAttributes().put("preferredLanguage", "vi");
session.getAttributes().put("favoriteColor", "blue");
~~~

Không nên đặt:

~~~java
session.getAttributes().put("password", rawPassword);
session.getAttributes().put("request", exchange);
session.getAttributes().put("openStream", inputStream);
~~~

Ba dòng trên là **anti-pattern**, với biến chưa định nghĩa. Session có thể sống qua nhiều requests; giữ request/stream có thể làm resource leak, credentials trong session có thể bị đánh cắp khi storage/log leak, và distributed serialization sẽ gặp lỗi.

### 5.1. Mutable session state có thể bị race

Giả sử hai AJAX requests cùng session chạy đồng thời và cập nhật một `ArrayList` hoặc map đếm giỏ hàng. `session.getAttributes()` **không có nghĩa** các object con của session tự động thread-safe, không có database transaction. Hai request có thể mất cập nhật hoặc ghi lại phiên bản session cũ vào shared store.

Một lựa chọn an toàn hơn cho dữ liệu quan trọng là **DB với transaction/optimistic locking**, lưu session tối thiểu các identifiers:

~~~java
session.getAttributes().put("cartId", cartId);
~~~

Đoạn minh họa dùng `cartId` đã tạo từ server và validate. Không dùng mutable session attributes làm nguồn sự thật cho tồn kho, số dư, quyền truy cập hoặc các thao tác đa bước cần consistency mạnh.

### 5.2. @SessionAttribute và @SessionAttributes

- `@SessionAttribute` trên argument đọc **attribute đã tồn tại** trong session và có thể dùng khi attribute được quản lý bên ngoài controller, ví dụ filter.
- `@SessionAttributes` trên **class @Controller** dùng cho một workflow nhiều request, lưu một số model attributes giữa các request, thường kết hợp `SessionStatus.setComplete()` để đánh dấu kết thúc.
- Để chủ động thêm/xóa attributes, dùng `WebSession` trực tiếp thường minh bạch hơn.

Đừng nhầm `@SessionAttributes` (số nhiều) với `@SessionAttribute` (số ít) hoặc một annotation có thể cấu hình tất cả security/session TTL.

## 6. LocaleContext của WebFlux được lấy từ đâu?

`LocaleContext` nằm ở `org.springframework.context.i18n.LocaleContext`. Với WebFlux, `ServerWebExchange.getLocaleContext()` trả thông tin từ bean **`LocaleContextResolver`** tương ứng.

Theo Spring Framework 6.2, strategy mặc định là **`AcceptHeaderLocaleContextResolver`**, đọc `Accept-Language` từ request. Ví dụ:

~~~http
GET /api/context/state HTTP/1.1
Accept-Language: vi-VN,vi;q=0.9,en-US;q=0.5
~~~

Nếu không cấu hình danh sách supported locales hạn chế, locale được resolver chọn có thể là `vi-VN`. Một request khác từ browser `en-US` sẽ nhận locale khác **mà không có bất kỳ WebSession attribute nào thay đổi**.

Đọc trong handler:

~~~java
@GetMapping("/language")
public Mono<String> language(ServerWebExchange exchange) {
    LocaleContext context = exchange.getLocaleContext();
    Locale locale = context.getLocale();
    String tag = locale == null ? "und" : locale.toLanguageTag();
    return Mono.just(tag);
}
~~~

Đây là method minh họa trong controller, cần import `LocaleContext`, `Locale`, `Mono` và annotation. `"und"` là language tag biểu thị **undetermined** khi không có locale, không bắt buộc response của mọi resolver.

### 6.1. Locale khác timezone

`Locale` thường mô tả ngôn ngữ/quốc gia dùng hiển thị (ví dụ `vi-VN`, `en-US`), **không phải** timezone. `LocaleContext` có thể được mở rộng bởi `TimeZoneAwareLocaleContext`, nhưng **`AcceptHeaderLocaleContextResolver` mặc định không cung cấp timezone** theo cơ chế đó.

Đoạn minh họa nhận timezone có điều kiện:

~~~java
LocaleContext lc = exchange.getLocaleContext();
TimeZone zone = lc instanceof TimeZoneAwareLocaleContext aware
        ? aware.getTimeZone()
        : null;
~~~

Đoạn trên cần `java.util.TimeZone`, `TimeZoneAwareLocaleContext`, `LocaleContext`, biến exchange. Không tự suy `Asia/Ho_Chi_Minh` chỉ vì `Locale.forLanguageTag("vi-VN")`; locale và timezone là hai dữ liệu độc lập.

### 6.2. AcceptHeaderLocaleContextResolver không phải nơi lưu locale tùy ý

`LocaleContextResolver` định nghĩa:
- `resolveLocaleContext(ServerWebExchange)`
- `setLocaleContext(ServerWebExchange, LocaleContext)`

Nhưng `AcceptHeaderLocaleContextResolver` **không hỗ trợ lưu/đổi locale qua `setLocaleContext`**: request header `Accept-Language` do client gửi. Gọi `setLocaleContext` có thể ném `UnsupportedOperationException` theo API. Nếu user chọn ngôn ngữ bằng UI và muốn ghi nhớ, bạn phải:
- Lưu lựa chọn ở profile database, WebSession hoặc cookie theo business design.
- Xây một resolver/filter/controller có chính sách lựa chọn hợp lệ (ví dụ ưu tiên profile/session hơn Accept-Language).
- Xác định precedence một cách rõ ràng và dùng locale thực tế khi format.
- Không ghi `LocaleContextHolder.setLocale(...) ` rồi cho rằng reactive pipeline luôn nhận đúng qua các async thread shifts.

### 6.3. Cấu hình supported locales

Ví dụ Spring Framework 6.2:

~~~java
@Configuration
public class LocaleWebConfig {
    @Bean(name = "localeContextResolver")
    public LocaleContextResolver localeContextResolver() {
        AcceptHeaderLocaleContextResolver resolver =
                new AcceptHeaderLocaleContextResolver();
        resolver.setSupportedLocales(
                List.of(Locale.forLanguageTag("vi-VN"),
                        Locale.forLanguageTag("en-US")));
        resolver.setDefaultLocale(Locale.forLanguageTag("vi-VN"));
        return resolver;
    }
}
~~~

**Đây là class minh họa bổ sung, không phải phần buộc phải thêm vào demo ở mục 10.** Imports cần: `Configuration`, `Bean`, `LocaleContextResolver` từ `org.springframework.web.server.i18n`, `AcceptHeaderLocaleContextResolver`, `List`, `Locale`. Trong application có Spring Boot auto-configuration, phải kiểm tra chính sách bean overriding/conditional config nếu đã có resolver từ Boot hoặc thư viện; không tạo hai beans cùng tên.

Javadoc của `setSupportedLocales` nhấn mạnh matching có yêu cầu tương thích cụ thể **language và country**. Ví dụ nếu chỉ hỗ trợ `en-US`, request `en-GB` hoặc `en` có thể không match và sẽ quay về default/first requested locale tùy cấu hình. Muốn hỗ trợ language-only, liệt kê `Locale.ENGLISH` trong supported set hoặc thiết kế normalization thích hợp.

**Không mặc định** controller sẽ lưu `Accept-Language` vào session. Đó là hai cơ chế độc lập.

## 7. Reactor Context: metadata per subscription, không phải WebSession

Reactor `Context` là cấu trúc **immutable** kiểu key/value liên kết với subscriber. `ContextView` cung cấp read-only view, còn `Context` hỗ trợ `put`, `delete` bằng cách tạo context mới.

Một ví dụ Reactor thuần:

~~~java
Mono<String> message =
        Mono.deferContextual(view ->
                Mono.just(view.getOrDefault("requestId", "missing")))
            .contextWrite(ctx ->
                ctx.put("requestId", "req-42"));
~~~

Khi có subscriber, `message` emit **`req-42`**. Điểm then chốt:

- `Mono.deferContextual` đọc Context **ở thời điểm subscription**, không nhất thiết ở lúc controller method được gọi.
- `contextWrite` không mutate một biến global; nó tạo một Context mới cho các upstream operators theo Reactor subscriber semantics.
- Context **không tự gửi cookie**, không tự persist qua request tiếp theo.
- Một Reactor Context key là object; có thể dùng `String` nhưng trong library lớn nên tránh collision, ví dụ dùng constant duy nhất hoặc class key.

### 7.1. Hướng truyền của contextWrite

Một nguyên tắc dễ nhầm: **Context truyền từ phía subscriber lên phía publisher** trong subscription, trong khi signals/data thường đi từ publisher xuống subscriber.

~~~text
Source publisher --- map --- flatMap --- contextWrite --- Subscriber
                 <------------------- Context -------
                 -------------------- onNext ------->
~~~

`contextWrite` **ở phía downstream** trong biểu thức chain thường làm context khả dụng cho `deferContextual`/operators **upstream**. Không phải mọi operator phía dưới nó đều nhìn thấy context được viết bởi nó.

Ví dụ đúng:

~~~java
Mono<String> result = Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("correlationId", "missing")))
    .contextWrite(c -> c.put("correlationId", "abc123"));
~~~

Khi subscribe, `result` emit **`abc123`**.

### 7.2. Không coi ThreadLocal như Reactor Context

Ví dụ dễ lỗi:

~~~java
static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

Mono<String> result = Mono.just("start")
        .publishOn(Schedulers.parallel())
        .map(v -> REQUEST_ID.get());
~~~

Đây là **anti-pattern khái niệm**, không phải file độc lập. Nếu request ID được set trên thread nhận HTTP, `publishOn` có thể chuyển xử lý sang thread khác và `REQUEST_ID.get()` thành `null` (hoặc mang giá trị từ task khác nếu quản lý sai). Không dựa vào `InheritableThreadLocal` cho Reactor event loops/worker pools.

Thay bằng:

~~~java
Mono<String> result = Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("correlationId", "missing")))
    .publishOn(Schedulers.parallel())
    .flatMap(id -> Mono.deferContextual(ctx ->
            Mono.just(id + "/" +
                    ctx.getOrDefault("correlationId", "missing"))))
    .contextWrite(c -> c.put("correlationId", "abc123"));
~~~

Kết quả subscriber nhận **`abc123/abc123`** vì Context đi theo reactive subscriber dù scheduling chuyển thread. Không tự suy luận mọi custom library sẽ truyền context nếu nó thoát khỏi Reactor chain vào callback/executor độc lập.

### 7.3. Không tạo side subscription vô chủ

Tránh:

~~~java
@GetMapping("/wrong")
public Mono<String> wrong() {
    service.call().subscribe(); // fire-and-forget
    return Mono.just("accepted");
}
~~~

Mẫu phản diện này có thể đánh mất cancellation, errors, trace context và không chứng minh tác vụ đã hoàn thành trước response. Tốt hơn **return composed Publisher**:

~~~java
@GetMapping("/right")
public Mono<String> right() {
    return service.call().thenReturn("done");
}
~~~

Đây là snippet có `service` bên ngoài, chưa chạy độc lập. Với background jobs thật, dùng queue/scheduler có ownership, retry, audit và idempotency rõ, không fire-and-forget tùy tiện từ HTTP handler.

## 8. Đưa Reactor Context vào pipeline từ WebFilter

Spring WebFlux `WebFilter` nhận `ServerWebExchange` và `WebFilterChain`:

~~~java
public Mono<Void> filter(
        ServerWebExchange exchange,
        WebFilterChain chain) {

    String id = "req-42";
    return chain.filter(exchange)
            .contextWrite(ctx -> ctx.put("requestId", id));
}
~~~

Đây là method minh họa. Cách này đặt `requestId` vào Context **cho phần chain upstream khi được subscribe**, thường gồm downstream handler/service và nested reactive operators.

Tách biệt hai nơi chứa thông tin:

1. `exchange.getAttributes().put("requestId", id)` đặt metadata **ở HTTP exchange hiện tại**; tiện cho các filters/handler cùng nhìn thấy exchange, nhưng không tự đi theo mọi nested reactive requests.
2. `.contextWrite(ctx -> ctx.put("requestId", id))` đặt metadata **ở reactive subscriber context**; thuận tiện cho `Mono.deferContextual` và các operators/filters tham gia chuỗi.

Nếu dùng `WebClient`, một `ExchangeFilterFunction` cũng có thể đọc Reactor Context khi WebClient publisher được subscribe trong cùng pipeline:

~~~java
WebClient client = WebClient.builder()
        .filter((request, next) ->
                Mono.deferContextual(ctx -> {
                    String id = ctx.getOrDefault(
                            "requestId", "missing");

                    ClientRequest updated = ClientRequest
                            .from(request)
                            .header("X-Request-Id", id)
                            .build();

                    return next.exchange(updated);
                }))
        .build();
~~~

Đây là snippet minh họa cần imports `WebClient`, `ClientRequest`, `Mono` và một HTTP URI cho lời gọi thực. Chỉ truyền correlation ID đã validate tới hệ thống **tin cậy**; không tự đính kèm token/session ID vào mọi outbound request hoặc gọi URL không kiểm soát.

## 9. Reactive Security Context và WebSession

Spring Security có reactive APIs:
- `ReactiveSecurityContextHolder.getContext()`: đọc security context từ Reactor subscriber Context.
- `ReactiveSecurityContextHolder.withAuthentication(...)` hoặc `withSecurityContext(...)`: tạo context cần thiết theo hợp đồng security.
- `SecurityWebFilterChain`: filter chain cho WebFlux (khác `SecurityFilterChain` của servlet).
- `ServerSecurityContextRepository`: contract lưu và tải context khi ứng dụng có stateful authentication.
- `WebSessionServerSecurityContextRepository`: một implementation có thể lưu `SecurityContext` trong WebSession khi cấu hình thích hợp.
- `ReactorContextWebFilter`: dùng security context repository để đưa security context vào Reactor Context.

**Không đồng nhất ba điều:**
- `WebSession` tồn tại;
- người dùng đã authentication;
- Reactor `SecurityContext` hiện diện ở subscriber hiện tại.

Một WebSession có thể chứa chỉ tùy chọn giao diện mà không có authentication. Một API dùng bearer token/stateless resource server có thể **không cần server-side session** nhưng vẫn có Reactive SecurityContext cho mỗi request.

Đoạn code minh họa đọc username qua reactive security:

~~~java
public Mono<String> currentUser() {
    return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .defaultIfEmpty("anonymous");
}
~~~

Đây là **method minh họa**, cần Spring Security reactive dependency. Khi `Authentication` null hoặc unauthenticated, ứng dụng cần policy rõ ràng; không mặc nhiên dùng `getName()` như kiểm tra authorization. Hãy để Security chain/authorization rules bảo vệ endpoint, không chỉ dựa vào trường tên.

### Bảo mật session và chống fixation

- Khi đăng nhập bằng session, framework security phải thực hiện chính sách session fixation protection hợp lý.
- Dùng `WebSession.changeSessionId()` đúng theo lifecycle; không tự gửi ID qua query parameter.
- Đặt cookie `Secure`, `HttpOnly`, `SameSite` theo use case và deployment; HTTPS là cơ sở.
- Logout cần invalidate hoặc clear server authentication state theo chính sách. Không chỉ xóa localStorage trên frontend.
- Cần CSRF protection khi dùng browser cookie-based authentication và các thao tác thay đổi trạng thái.
- Không truyền `Authentication`, cookie hoặc token qua custom Reactor Context đến các service không đáng tin; Context không tự mã hóa dữ liệu.

## 10. Ứng dụng Spring Boot 3.x chạy được

Bài thực hành sử dụng:
- **WebSession** để ghi/đọc lựa chọn màu `favoriteColor` qua hai HTTP requests.
- **LocaleContext** từ `Accept-Language` để trả language tag `vi-VN`/ `en-US`.
- **Reactor Context** để truyền `X-Request-Id` qua `WebFilter`, `publishOn` và nested `flatMap`.
- `WebTestClient` để xác minh session cookie, các header, JSON và context propagation bằng **HTTP server thật trên random port**.

Dự án chỉ cần Java 17+, Spring Boot 3.x với `spring-boot-starter-webflux` và `spring-boot-starter-test`. Chưa thêm Spring Security/Micrometer vì mục tiêu của runnable demo là kiểm chứng ba khái niệm của đề bài **không phụ thuộc một hệ thống authentication hoặc tracing bên ngoài**.

### 10.1. Maven và cấu trúc dự án

Dùng Spring Initializr tạo project Java 17+, Maven, package `com.example.demo`. Trong `pom.xml` có dependencies:

~~~xml
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
</dependencies>
~~~

Đây là **fragment** nằm trong project Maven đã được Spring Initializr tạo (có parent/BOM, Java version và build plugins), không phải toàn bộ `pom.xml` độc lập. Tránh thêm `spring-boot-starter-web` để giữ web application type là reactive.

Tạo:

~~~text
src/main/java/com/example/demo/
  DemoApplication.java
  RequestContextFilter.java
  ContextController.java
src/test/java/com/example/demo/
  ContextControllerTest.java
~~~

### 10.2. DemoApplication.java

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

### 10.3. RequestContextFilter.java

~~~java
package com.example.demo;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class RequestContextFilter implements WebFilter {

    public static final String REQUEST_ID_CONTEXT_KEY =
            RequestContextFilter.class.getName() + ".requestId";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        String supplied = exchange.getRequest()
                .getHeaders().getFirst("X-Request-Id");

        String id = supplied != null &&
                supplied.matches("[A-Za-z0-9_-]{1,48}")
                ? supplied : UUID.randomUUID().toString();

        exchange.getAttributes().put("requestId", id);
        exchange.getResponse().getHeaders().set("X-Request-Id", id);

        return chain.filter(exchange)
                .contextWrite(context ->
                        context.put(REQUEST_ID_CONTEXT_KEY, id));
    }
}
~~~

**Giải thích:**

1. Filter sinh request ID hoặc nhận header từ client chỉ khi nó khớp allowlist 1–48 ký tự, tránh lưu CR/LF và dữ liệu tùy ý vào log/header.
2. Cùng `id` được đưa vào **exchange attributes**, **response header**, và **Reactor Context**. Mỗi nơi có vòng đời riêng.
3. Filter không tạo `WebSession`, không gọi database và không tự giữ request ID vào global static mutable field.
4. `contextWrite` nằm **sau** `chain.filter(exchange)` theo chuỗi, vì context hướng từ subscriber tới upstream.
5. Trong production, không dùng client-supplied ID làm một bằng chứng danh tính; correlation ID từ gateway tin cậy vẫn phải validate và không dùng như authorization token.
6. Import `HttpHeaders` trong file này là không bắt buộc (không sử dụng trực tiếp) nhưng không làm lỗi biên dịch; có thể loại bỏ nếu bật style checks cho unused imports.

### 10.4. ContextController.java

~~~java
package com.example.demo;

import java.util.Locale;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/context")
public class ContextController {

    @GetMapping("/state")
    public Mono<StateView> state(
            WebSession session,
            ServerWebExchange exchange) {

        String language = localeTag(exchange);
        String color = session.getAttribute("favoriteColor");

        return Mono.deferContextual(context ->
                Mono.just(new StateView(
                        language,
                        color,
                        context.getOrDefault(
                                RequestContextFilter.REQUEST_ID_CONTEXT_KEY,
                                "missing"),
                        session.isStarted())));
    }

    @PostMapping("/favorite/{color}")
    public Mono<StateView> favorite(
            @PathVariable("color") String color,
            ServerWebExchange exchange) {

        if (!color.matches("[a-z]{1,16}")) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "invalid color"));
        }

        return exchange.getSession()
                .flatMap(session -> {
                    session.getAttributes().put("favoriteColor", color);

                    return Mono.deferContextual(context ->
                            Mono.just(new StateView(
                                    localeTag(exchange),
                                    color,
                                    context.getOrDefault(
                                      RequestContextFilter.REQUEST_ID_CONTEXT_KEY,
                                      "missing"),
                                    session.isStarted())));
                });
    }

    @PostMapping("/invalidate")
    public Mono<Void> invalidate(WebSession session) {
        return session.invalidate();
    }

    @PostMapping("/rotate")
    public Mono<Void> rotate(WebSession session) {
        return session.changeSessionId();
    }

    @GetMapping("/hop")
    public Mono<HopView> hop() {
        return Mono.deferContextual(context ->
                        Mono.just(context.getOrDefault(
                                RequestContextFilter.REQUEST_ID_CONTEXT_KEY,
                                "missing")))
                .publishOn(Schedulers.parallel())
                .flatMap(before -> Mono.deferContextual(context ->
                        Mono.just(new HopView(
                                before,
                                context.getOrDefault(
                                  RequestContextFilter.REQUEST_ID_CONTEXT_KEY,
                                  "missing")))));
    }

    private static String localeTag(ServerWebExchange exchange) {
        LocaleContext context = exchange.getLocaleContext();
        Locale locale = context.getLocale();
        return locale == null ? "und" : locale.toLanguageTag();
    }

    public record StateView(
            String locale,
            String favoriteColor,
            String requestId,
            boolean sessionStarted) {}

    public record HopView(
            String beforeHop,
            String afterHop) {}
}
~~~

**Quan sát ba cơ chế hoạt động:**

- `state`: framework có thể inject `WebSession` vào handler, lấy `favoriteColor` nếu có. Gọi handler này **không tự start session** khi chỉ đọc attribute.
- `favorite`: `exchange.getSession()` trả `Mono<WebSession>`; thêm attribute `favoriteColor` khiến session bắt đầu, framework ghi session ID vào cookie khi lưu.
- `localeTag`: đọc locale theo `LocaleContextResolver`, trong default config chịu tác động `Accept-Language`; không đổi dữ liệu session.
- `Mono.deferContextual`: đọc request ID từ reactive subscriber Context; không dùng `ThreadLocal`.
- `hop`: có `publishOn(Schedulers.parallel())` để chuyển execution context qua scheduler; `beforeHop` và `afterHop` vẫn được lấy từ cùng Reactor Context. Không assert tên thread vì scheduler implementation/timing không phải business contract.
- `invalidate` và `rotate`: trả publisher gốc cho framework subscribe. Không gọi `.block()` trong event-loop thread.

**Cảnh báo thiết kế:** demo dùng `favoriteColor` chỉ là dữ liệu nhỏ, không nhạy cảm. Không nên cho client tự gửi một serialized object tùy ý vào session hoặc lưu tokens/passwords/raw request ở đây.

### 10.5. ContextControllerTest.java

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive
        .AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ContextControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void readOnlyRequestDoesNotStartNewSession() {
        EntityExchangeResult<ContextController.StateView> result =
                client.get()
                        .uri("/api/context/state")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN")
                        .header("X-Request-Id", "req-read")
                        .exchange()
                        .expectStatus().isOk()
                        .expectHeader().valueEquals(
                                "X-Request-Id", "req-read")
                        .expectBody(ContextController.StateView.class)
                        .returnResult();

        ContextController.StateView body = result.getResponseBody();
        assertNotNull(body);
        assertEquals("vi-VN", body.locale());
        assertEquals("req-read", body.requestId());
        assertFalse(body.sessionStarted());
    }

    @Test
    void webSessionPersistsAcrossRequestsWithCookie() {
        EntityExchangeResult<ContextController.StateView> created =
                client.post()
                        .uri("/api/context/favorite/blue")
                        .header("X-Request-Id", "req-save")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(ContextController.StateView.class)
                        .returnResult();

        ContextController.StateView saved = created.getResponseBody();
        assertNotNull(saved);
        assertTrue(saved.sessionStarted());
        assertEquals("blue", saved.favoriteColor());

        ResponseCookie cookie =
                created.getResponseCookies().getFirst("SESSION");
        assertNotNull(cookie);

        client.get()
                .uri("/api/context/state")
                .cookie("SESSION", cookie.getValue())
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .header("X-Request-Id", "req-read-again")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.locale").isEqualTo("en-US")
                .jsonPath("$.favoriteColor").isEqualTo("blue")
                .jsonPath("$.requestId").isEqualTo("req-read-again")
                .jsonPath("$.sessionStarted").isEqualTo(true);
    }

    @Test
    void reactorContextSurvivesSchedulerHop() {
        client.get()
                .uri("/api/context/hop")
                .header("X-Request-Id", "req-hop")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.beforeHop").isEqualTo("req-hop")
                .jsonPath("$.afterHop").isEqualTo("req-hop");
    }

    @Test
    void invalidColorIsRejected() {
        client.post()
                .uri("/api/context/favorite/RED123")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
~~~

**Vì sao test này hữu ích?**

- `@SpringBootTest(RANDOM_PORT)` và `@AutoConfigureWebTestClient` tạo HTTP integration test với embedded reactive server, không phải chỉ gọi Java method trực tiếp.
- Test đầu khẳng định **đọc session chưa started** và Locale từ request; nếu một security filter/library khác cố ý start session, điều kiện phải được thiết kế lại vì lúc đó chính sách đã thay đổi.
- Test thứ hai lấy cookie `SESSION` từ response **đầu**, chủ động gửi lại trong request **sau** và xác minh `favoriteColor` được duy trì.
- Test `hop` khẳng định Reactor Context vẫn sẵn có sau `publishOn` và `flatMap`; không dựa vào ThreadLocal hay tên thread.
- Test cuối minh họa validation ở controller, không liên quan đến session ID.
- Test không mặc định rằng một client HTTP tự động duy trì cookie jar; nó **lấy và gửi cookie tường minh** để kiểm tra request isolation.

Khi chạy trên production hoặc thêm Spring Security, bạn phải điều chỉnh tests phù hợp authentication/CSRF rules. Ví dụ không thêm Security dependency nên `POST` đơn giản hoạt động.

### 10.6. Chạy và kiểm tra bằng curl

Chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có wrapper và Maven đã cài, dùng `mvn spring-boot:run`.

Đọc trạng thái request không có cookie:

~~~bash
curl -i   -H "Accept-Language: vi-VN"   -H "X-Request-Id: req-001"   http://localhost:8080/api/context/state
~~~

Body kỳ vọng có các fields tương ứng:

~~~json
{
  "locale": "vi-VN",
  "favoriteColor": null,
  "requestId": "req-001",
  "sessionStarted": false
}
~~~

Giá trị `favoriteColor: null` có thể bị Jackson omission nếu dự án đã cấu hình bỏ null properties. Quan trọng là trạng thái không có color, và WebSession chưa được start. Không dựa vào thứ tự JSON keys.

Lưu cookie khi chọn màu:

~~~bash
curl -i -c cookies.txt   -X POST   -H "X-Request-Id: req-save"   http://localhost:8080/api/context/favorite/blue
~~~

Browser/client thường nhận `Set-Cookie: SESSION=...` khi session được start/lưu. Lệnh `-c cookies.txt` cho curl ghi cookie vào jar file.

Đọc lại session:

~~~bash
curl -i -b cookies.txt   -H "Accept-Language: en-US"   -H "X-Request-Id: req-next"   http://localhost:8080/api/context/state
~~~

Kỳ vọng favoriteColor vẫn **blue**, locale **en-US**, requestId **req-next**. Đó là minh họa rõ ràng cho ba scopes độc lập: session giữ state qua HTTP requests, locale đổi theo header từng request, Reactor Context được tạo lại cho mỗi subscription.

Thử scheduler hop:

~~~bash
curl -i   -H "X-Request-Id: rid-hop"   http://localhost:8080/api/context/hop
~~~

JSON kỳ vọng:

~~~json
{"beforeHop":"rid-hop","afterHop":"rid-hop"}
~~~

**Không gửi session ID đến log công khai hoặc sao chép cookie file chứa ID lên hệ thống khác.** Curl cookie jar nên chỉ dùng cho thử nghiệm local và có thể chứa thông tin nhạy cảm.

## 11. Cách dùng Context qua nested WebClient calls

Reactor Context có ích khi một `WebFilter` nhận correlation metadata, sau đó `WebClient` gửi request đến microservice khác. Spring WebFlux hỗ trợ đọc Context trong `ExchangeFilterFunction` thông qua `Mono.deferContextual`.

Một service minh họa:

~~~java
@Service
public class PartnerClient {
    private final WebClient client;

    public PartnerClient(WebClient.Builder builder) {
        this.client = builder
                .baseUrl("https://partner.example.com")
                .filter((request, next) ->
                        Mono.deferContextual(context -> {
                            String rid = context.getOrDefault(
                                RequestContextFilter.REQUEST_ID_CONTEXT_KEY,
                                "missing");
                            ClientRequest withId = ClientRequest
                                    .from(request)
                                    .header("X-Request-Id", rid)
                                    .build();
                            return next.exchange(withId);
                        }))
                .build();
    }

    public Mono<String> getInfo(long id) {
        return client.get()
                .uri("/data/{id}", id)
                .retrieve()
                .bodyToMono(String.class);
    }
}
~~~

**Đây là class minh họa bổ sung**, không chạy độc lập cùng ví dụ mục 10 vì `partner.example.com` chỉ là sample domain; cần service thật và dependencies phù hợp. Imports gồm `@Service`, `WebClient`, `ClientRequest`, `Mono`. Trong code thật, xác minh upstream trust boundary, timeout, retry, TLS và không truyền dữ liệu nhạy cảm vào header ngoài ý định.

Một lưu ý quan trọng: context propagation qua Reactor chain hoạt động khi **WebClient Publisher được subscribe như một phần chain có Context phù hợp**. Nếu service tách ra tạo `new Thread(...)`, `CompletableFuture.supplyAsync(...)`, hoặc `subscribe()` riêng, việc truyền context không được bảo đảm tự động. Khi buộc đổi concurrency model, chụp metadata bất biến hoặc dùng Micrometer Context Propagation đúng cách.

## 12. Reactor Context, MDC và Micrometer: observability

Nhiều hệ thống dùng SLF4J MDC:

~~~java
MDC.put("requestId", "rid-1");
~~~

Nhưng MDC phần lớn dựa trên **ThreadLocal**. Trong WebFlux, signals của một request có thể đi qua các threads khác nhau. Đặt MDC một lần trong filter rồi cho rằng mọi callback đọc được nó là **không an toàn**.

Cách tiếp cận:
1. Giữ correlation metadata trong **Reactor Context** hoặc tracing/observation context mà instrumentation hỗ trợ.
2. Dùng **Micrometer Context Propagation** và các registered `ThreadLocalAccessor` khi cần bridge tới MDC/ThreadLocal-based APIs.
3. Chọn rõ cơ chế automatic vs explicit context propagation phù hợp Reactor/Spring version; không vô tình bật nhiều global hooks không kiểm soát.
4. Khi dùng custom `TaskDecorator`, cần capture/restore trong `finally` và không giả định điều đó tự propagate mọi reactor subscriber Context.
5. Không đặt bearer token, raw session ID, password hoặc PII vào tracing baggage/logs.
6. Kiểm tra trace ID xuyên `publishOn`, `flatMap`, WebClient, retry và error/cancellation.

Ví dụ an toàn hơn để truy cập ID trong business pipeline là `Mono.deferContextual(ctx -> ...)` thay vì `MDC.get(...)`. Nếu đã có Spring Boot Actuator, Micrometer tracing và security integration, ưu tiên cấu hình theo tài liệu cùng phiên bản trước khi viết thủ công một cơ chế context global mới.

## 13. Quan hệ với Spring Session reactive và scaling

In-memory session store phù hợp demo hoặc ứng dụng đơn instance. Trong deployment nhiều replicas:

~~~text
Browser
   |
Load balancer
   +---- Pod A: local WebSessionStore
   +---- Pod B: local WebSessionStore
   +---- Pod C: local WebSessionStore
~~~

Nếu browser gửi một ID phiên được lưu ở Pod A nhưng lần sau tới Pod B, Pod B có thể không tìm thấy session tương ứng. Giải pháp có thể là:
- Sticky session theo thiết kế hạ tầng (có giới hạn, failover khó hơn).
- **Spring Session** với Redis/compatible reactive session repository để chia sẻ storage theo ứng dụng.
- Stateless auth hoặc lưu preference vào database profile và tránh HTTP session khi không cần.

Với Spring Session reactive:
- Bảo đảm dependency/repository hỗ trợ WebFlux, reactive connection factory, serialization và expiration.
- Không lưu object graph không serializable hoặc dữ liệu mutable mà nhiều requests sửa đồng thời mà không có conflict control.
- Đo latency/availability của Redis/session store — session lookup trở thành I/O trên mỗi request cần đọc.
- Áp dụng TTL và security cho Redis credentials, network, backups và key namespace.
- Nếu Spring Security lưu SecurityContext trong WebSession, kiểm tra hành vi của security context repository, session fixation và logout.

**Không suy ra WebSession mặc định là một Redis database session.** Nó chỉ là interface; cơ chế lưu phụ thuộc cấu hình.

## 14. Locale được lựa chọn theo user profile hay WebSession?

Giả sử ứng dụng đa ngôn ngữ cho phép user chọn tiếng Việt ngay trong web UI. Có nhiều nguồn locale:

1. `Accept-Language` của browser — gợi ý do client tự gửi cho mỗi request.
2. Language preference trong **user profile** (bền vững qua thiết bị và đăng nhập).
3. Language preference trong **WebSession** (chỉ liên quan session hiện hành).
4. URL locale segment như `/vi/.../` hoặc query param `lang=vi`.
5. Default locale của product.

Bạn cần thiết kế thứ tự ưu tiên, ví dụ:

~~~text
Authenticated user profile
   > explicit validated language selection in current session
   > Accept-Language compatible supported locale
   > product default
~~~

Đây chỉ là **policy minh họa**: ứng dụng có thể dùng URL locale ưu tiên hơn session để phục vụ SEO/bookmark, tùy thiết kế.

Khi xử lý user lựa chọn language:
- Chỉ nhận locale trong allowlist `vi-VN`, `en-US`, v.v.
- Nếu ghi vào session, hãy áp dụng rõ trong locale resolution pipeline cho request sau. Chỉ `put("language", "vi")` **không khiến** `AcceptHeaderLocaleContextResolver` tự đọc session đó.
- Đừng suy đoán timezone từ locale; lưu timezone user riêng, ví dụ IANA ZoneId.
- Đối với response được cache, `Content-Language`, `Vary: Accept-Language` và user-specific caching phải nhất quán.
- Khi localization ảnh hưởng API error messages, hãy tránh để cùng một shared cache key dùng cho hai biến thể ngôn ngữ.

## 15. Khi nào dùng session, exchange attributes hay Reactor Context?

| Tình huống | Giải pháp ưu tiên | Lý do |
| --- | --- | --- |
| User chọn giao diện dark mode dùng qua nhiều requests | WebSession hoặc profile DB | Cần trạng thái có vòng đời dài hơn request |
| Correlation ID của HTTP request hiện tại | Reactor Context + HTTP response header | Propagate qua reactive pipeline, request-scoped |
| Một filter cần gắn flag cho handler trong cùng exchange | `exchange.getAttributes()` | Dùng chung object exchange hiện tại |
| Định dạng ngày/giờ theo locale hiện hành | `LocaleContext` + timezone policy | Locale cho request, timezone xử lý riêng |
| Lưu cart/tồn kho cần concurrency transaction | DB, không chỉ session attribute | Consistency/locking |
| Authentication principal trong reactive code | `ReactiveSecurityContextHolder` | Security model nhất quán |
| Client OAuth bearer token | Security filter/authentication, thường stateless | Không buộc phải khởi tạo WebSession |
| Truyền request ID qua WebClient nested call | Reactor Context + WebClient filter | Subscriber metadata đi theo chain |
| Java thread khác không tham gia Reactor chain | Explicit immutable snapshot/propagation bridge | Reactor Context không tự teleport qua thread tùy ý |
| Locale cố định toàn hệ thống | Fixed/accept-header locale resolver config | Không cần lưu locale vào session |
| Ghi log trong thư viện chỉ biết MDC | Micrometer context propagation/bridge | MDC thread-local không tự được đảm bảo |
| Gửi session ID tới upstream service | **Thông thường không nên** | Rò credential/trust boundary |

Đừng tạo stateful session chỉ để truyền một request ID, cũng đừng đặt giỏ hàng vào Reactor Context và nghĩ request sau vẫn có giá trị đó.

## 16. Các lỗi thường gặp và chẩn đoán

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `getSession()` được gọi nhưng không có cookie SESSION | Session chỉ được đọc, chưa start | Kiểm tra `isStarted()`, write attribute/start |
| Chọn màu rồi GET tiếp không nhớ | Client không gửi cookie, session expired hoặc node khác | Network Cookie/Set-Cookie, store, load balancer |
| Session ID có nhưng state sai | Concurrent writes, reset store, mutable attributes | Kiểm tra store và race/lifecycle |
| Logout chỉ gọi `invalidate()` mà vẫn giữ session | Bỏ qua publisher, không return/subscribe | Return `session.invalidate()` trong chain |
| `getLocaleContext()` luôn giống request header | Default AcceptHeader resolver | Muốn đổi preference cần resolver riêng |
| `setLocaleContext` ném UnsupportedOperationException | AcceptHeader resolver không cho thay header client | Custom policy hoặc profile/session storage |
| Locale vi-VN nhưng timezone sai | Locale không tự xác định timezone | Dùng ZoneId riêng, TimeZoneAware context nếu có |
| `MDC.get("rid")` mất sau publishOn | Thread-local không theo scheduler hop | Reactor Context hoặc propagation bridge |
| `contextWrite` không đến vị trí mong đợi | Đặt sai phía subscribe hoặc tạo chain khác | Quan sát `deferContextual` và operator order |
| Request được trả response nhưng background task lỗi im lặng | `subscribe()` riêng trong handler | Return/composition Publisher, central job queue nếu cần |
| Spring Security `SecurityContextHolder` null | Nhầm servlet thread-local với reactive security model | ReactiveSecurityContextHolder / SecurityWebFilterChain |
| WebSession cookie hoạt động ở local nhưng mất khi scale | In-memory store không chia sẻ giữa pods | Spring Session reactive Redis hoặc sticky sessions |
| HTTP response đã commit, session changes không persist | Ghi session quá muộn sau response | Sắp xếp async lifecycle và session save đúng |
| SameSite/CORS khiến cookie không gửi | Cross-site policy/credentials mismatch | DevTools, cookie flags, allow-origin/credentials |
| Một session chứa nhiều bí mật | Lưu credentials/PII không cần thiết | Giảm dữ liệu session, mã hóa storage, access policy |
| Bật Secure cookie trên HTTP test làm cookie không gửi | Browser chặn Secure qua HTTP thường | Test HTTPS hoặc dùng cookie policy theo môi trường |
| Request ID từ header có CRLF | Không validate input | Regex allowlist, generate ID server-side |
| Endpoint có Mono nhưng JDBC blocking | Blocking library không biến mất | Dùng reactive driver hoặc bounded scheduler phù hợp |

### Debug có hệ thống

1. Bắt đúng request và response headers bằng DevTools/curl: `Cookie`, `Set-Cookie`, `Accept-Language`, `X-Request-Id`.
2. Xác định session đã **started** chưa, ID có được browser gửi lại không, session store đang chạy ở node nào và TTL.
3. Kiểm tra `WebSession` attributes thực tế, loại dữ liệu lưu, concurrent requests và phiên bản saved.
4. Đọc loại `LocaleContextResolver` đang đăng ký, cách match supported locales, timezone strategy và default locale.
5. Chụp Reactor pipeline trước và sau `publishOn`/`flatMap`, đọc qua `Mono.deferContextual`; đừng suy luận từ MDC.
6. Nếu thêm Spring Security, kiểm tra `SecurityWebFilterChain` và repository mà reactive auth đang dùng, không tự gán state.
7. Test quyền/CSRF/CORS trên browser, session cookie rotation/logout và session timeout ngoài test demo.
8. Khi scale, giám sát Redis/store, eviction, serialized session size, cookie count, p95 latency, failure modes.

## 17. Bảo mật và hiệu năng: review checklist

### Bảo mật

- Session cookie có Secure trên HTTPS, HttpOnly/SameSite theo ứng dụng và auth flow.
- Session ID là credential nhạy cảm: không log, không đưa trong URL, không đưa qua Reactor Context sang bên thứ ba.
- Session fixation protection và logout/invalidate do security policy điều phối.
- CSRF protection với cookie-based session auth và unsafe methods.
- WebSession storage có TTL, quota, network access controls; tránh ghi credentials/passwords hoặc object graphs nặng.
- CORS chỉ cho origin được phép, đặc biệt khi `credentials: include`.
- Correlation ID input được validate, không dùng như user identity hoặc ACL.
- Không tiết lộ security context trong JSON/log; lựa chọn principal phải được authorize riêng.
- Dữ liệu user preferences được validate (ngôn ngữ, timezone, theme, v.v.).
- Với đa tenant, session và caches luôn gắn đúng tenant/user, không trộn session state vào static singleton mutable fields.

### Hiệu năng

- Không khởi tạo session trên mọi anonymous GET nếu không có nhu cầu.
- Không lưu list/cart lớn vào WebSession gây tăng memory/Redis traffic.
- Với Redis-backed Spring Session, đo lookup/save latency và serialization cost; session store cũng có thể là bottleneck.
- Không dùng ThreadLocal để truyền request state xuyên Reactor scheduler shifts.
- Giới hạn `WebClient` connections, Reactor scheduler/buffers và API concurrency; context propagation không thay quota.
- Nếu cần cross-request preference thường xuyên, cân nhắc profile DB/cache có thiết kế invalidation rõ.
- Thiết kế session idle timeout và cookie max age nhất quán.
- Tách correlation ID request-scoped khỏi profile state, tránh lưu trace baggage quá nhiều.
- Test thật trong môi trường nhiều nodes/proxy, không chỉ một embedded server local.

## 18. Câu hỏi ôn tập

**1. `ServerWebExchange.getSession()` có luôn tạo cookie không?**

Không. Nó trả WebSession object hiện hữu hoặc mới; session mới chỉ start khi thêm attributes hoặc gọi `start()`, rồi được lưu theo lifecycle.

**2. Tại sao `session.invalidate(); return Mono.empty();` sai?**

Vì `invalidate()` trả reactive Publisher; nếu không compose/return thì không bảo đảm nó được subscribe/thực thi. Hãy return Publisher từ handler.

**3. `WebSession` có phải `HttpSession` của Servlet không?**

Không. WebSession là reactive session contract của Spring WebFlux, có resolver/store riêng. Đừng tự chuyển cast sang Servlet HttpSession.

**4. Locale vi-VN có tự suy ra múi giờ Asia/Ho_Chi_Minh không?**

Không. Locale và timezone độc lập; resolver mặc định đọc Accept-Language không tự cung cấp timezone.

**5. Nếu user chọn `en-US` lưu trong WebSession, resolver mặc định có tự dùng nó không?**

Không. `AcceptHeaderLocaleContextResolver` dựa vào header và cấu hình supported/default locales. Cần logic/custom resolver để áp dụng session preference.

**6. Reactor Context có sống qua HTTP requests không?**

Không tự động. Nó gắn với reactive subscription. Muốn giữ preference qua request mới, dùng session/profile DB/cookie.

**7. Vì sao `ThreadLocal`/MDC bị mất sau `publishOn`?**

Signals có thể chạy trên thread khác; ThreadLocal gắn với JVM thread chứ không theo subscriber. Dùng Reactor Context và propagation bridge khi cần.

**8. `contextWrite` truyền context theo hướng nào?**

Trong subscription, Context truyền từ phía subscriber (downstream) tới các upstream operators; dữ liệu `onNext` thường truyền chiều ngược lại.

**9. API bearer-token stateless có nhất thiết phải tạo WebSession để có security context không?**

Không. Spring Security reactive có thể đặt SecurityContext theo mỗi request/subscription mà không tạo session server-side.

**10. Dùng in-memory session store trên ba pods có rủi ro gì?**

Một cookie ID của Pod A có thể không tìm được state ở Pod B. Cần shared reactive session store hoặc infrastructure routing phù hợp.

**11. Trong demo test, yêu cầu đầu locale `vi-VN`, yêu cầu sau locale `en-US` nhưng vẫn giữ blue có ý nghĩa gì?**

`WebSession` giữ favoriteColor qua requests nhờ cookie, Locale thay theo từng request, và Reactor Context request ID cũng được tạo riêng cho mỗi subscription.

**12. Có thể dùng Reactor Context làm một nơi lưu giỏ hàng session không?**

Không phù hợp. Nó không persist xuyên requests; lưu giỏ hàng vào DB hoặc state store/session theo yêu cầu consistency.

## 19. Checklist áp dụng thực tế

1. Xác định **scope** trước: multi-request user state → WebSession/profile; single exchange metadata → exchange attributes; reactive subscription metadata → Reactor Context; locale request → LocaleContextResolver.
2. Khi chỉ đọc session, không `start()` nếu không có nhu cầu; khi ghi hoặc logout, compose các reactive operations đúng lifecycle.
3. Với session ID, dùng TLS, cookie flags, CSRF, rotation, invalidation và auth policies.
4. Giới hạn session attribute kích thước; tránh mutable state, secrets và object graph khó serialize.
5. Thiết kế shared session repository khi scale nhiều instances; test cookie, TTL, failover và concurrency.
6. Dùng `exchange.getLocaleContext()` trong reactive handlers; không suy timezone từ locale.
7. Xác định precedence cho ngôn ngữ từ URL/profile/session/Accept-Language và custom resolver khi cần.
8. Dùng `Mono.deferContextual`/`contextWrite` để truyền correlation/tracing metadata qua Reactor chain; không dựa vào ThreadLocal của request.
9. Không tự `subscribe()` trong controller cho nghiệp vụ cần report kết quả; return composed Publisher.
10. Tích hợp `ReactiveSecurityContextHolder` trong WebFlux security, phân biệt với Session và Servlet SecurityContextHolder.
11. Test `WebTestClient` trên HTTP socket thật: session cookie trước/sau, locale headers, context scheduler hop, logout và lỗi dữ liệu.
12. Đánh giá CORS, CSRF, session store/Redis, latency, memory, context propagation và tracing dưới tải production phù hợp.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — WebFlux reactive core, WebSessionManager và LocaleContextResolver](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html)
- [Spring Framework 6.2 — WebSession Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/server/WebSession.html)
- [Spring Framework 6.2 — ServerWebExchange Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/server/ServerWebExchange.html)
- [Spring Framework 6.2 — LocaleContextResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/server/i18n/LocaleContextResolver.html)
- [Spring Framework 6.2 — AcceptHeaderLocaleContextResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/server/i18n/AcceptHeaderLocaleContextResolver.html)
- [Spring Framework 6.2 — WebClient Reactor Context](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-context.html)
- [Spring Framework 6.2 — WebFlux @SessionAttributes](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/sessionattributes.html)
- [Spring Boot 3.5 — Common Application Properties (reactive session)](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Spring Security 6.x — Reactive SecurityContextHolder](https://docs.spring.io/spring-security/reference/6.5/reactive/authorization/index.html)
- [Project Reactor — Adding a Context to a Reactive Sequence](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)
- [Spring Session — WebFlux custom cookies](https://docs.spring.io/spring-session/reference/guides/boot-webflux-custom-cookie.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-infrastructure-008">Máy chủ reactive, kết nối và khác biệt với mô hình Servlet</a>

<details>
<summary>Xem chi tiết</summary>

# Máy chủ reactive, kết nối và khác biệt với mô hình Servlet trong Spring WebFlux

## 1. Vấn đề: nhiều kết nối HTTP có cần từng ấy thread?

Một API có 20.000 client đang giữ HTTP connections, nhưng mỗi request chỉ làm một số việc ngắn rồi đợi remote service hoặc streaming event. Nếu mỗi lượt xử lý bị gắn với một Java worker thread **đang chờ I/O blocking**, khả năng mở rộng sẽ chịu áp lực bởi số thread, stack memory và context switching. Reactive server chọn cách khác: theo dõi socket readiness, cho một tập nhỏ các **event-loop threads** phục vụ nhiều channels, và nối tác vụ bất đồng bộ qua `Publisher`, `Mono` và `Flux` thay vì giữ worker chỉ để đợi I/O.

Nhưng câu “WebFlux một thread phục vụ hàng nghìn kết nối” cũng dễ gây hiểu lầm:

- **Connection không đồng nghĩa request**, đặc biệt với HTTP keep-alive và HTTP/2 multiplexing.
- **Request không đồng nghĩa thread**. Một request có thể bắt đầu trên event loop, đi qua các publishers/schedulers, rồi kết thúc trên một execution context khác.
- **Reactive không đồng nghĩa tự động nhanh hơn** trong mọi workload. Các tác vụ CPU-bound vẫn tốn CPU; JDBC blocking, `Thread.sleep` hoặc `Future.get` đặt sai vị trí vẫn có thể nghẽn event loop.
- **WebFlux không bắt buộc dùng Netty.** Spring có adapters cho Reactor Netty, Tomcat/Jetty và các Servlet containers với non-blocking I/O. Spring Boot WebFlux mặc định dùng Reactor Netty khi dependency starter mặc định được giữ.
- **Servlet không đồng nghĩa một thread sống cùng một TCP connection.** Tomcat có non-blocking connector; Spring MVC có Servlet asynchronous processing. Sự khác biệt chủ yếu ở **hợp đồng ứng dụng và request processing model**, không thể quy thành server nào “có NIO” và server nào “không NIO”.

Bài học giải thích hạ tầng WebFlux từ TCP socket đến response, event loop, backpressure và connection reuse; so sánh Servlet MVC synchronous/MVC async/WebFlux; rồi cung cấp demo Spring Boot 3 + Java 17 cùng WebTestClient để kiểm chứng HTTP behavior mà không bịa các con số thread/throughput.

### Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt **TCP connection**, **HTTP request**, **response**, **Reactor Netty Channel**, **event-loop thread**, **Servlet worker thread** và **WebClient outbound connection pool**.
2. Vẽ request pipeline từ Reactor Netty tới `HttpHandler`, `WebHandler`, `DispatcherHandler`, Controller/functional handler, codecs và response.
3. Giải thích tại sao non-blocking I/O giảm thời gian chiếm thread khi chờ, nhưng không xóa công việc CPU, database hoặc network.
4. Phân biệt synchronous Servlet MVC, Servlet MVC async và WebFlux chạy trên Netty hoặc Servlet adapter mà không đưa ra so sánh sai.
5. Hiểu `Mono<Void>` khi ghi response, `Flux<DataBuffer>`, Reactive Streams demand, cancellation, streaming media types và HTTP backpressure.
6. Quản lý timeout, keep-alive, client connection pool, TLS, proxy, graceful shutdown, metrics và tránh blocking trên event loop.
7. Chạy ứng dụng demo reactive trên Reactor Netty và dùng WebTestClient xác nhận 200/400/204, reactive delay, SSE, filter và error policy.

**Phạm vi:** Spring Framework 6.x, Spring Boot 3.3.x, Reactor 3.x, Reactor Netty trong Servlet-vs-reactive context. Mọi giá trị performance/thread count được trình bày như nguyên tắc hoặc ví dụ minh họa, **không** gán thành benchmark thực tế của một hệ thống chưa đo.

## 2. Định nghĩa đúng: connection, request, thread, channel

### 2.1. TCP connection

TCP connection là kênh truyền byte giữa hai endpoints (IP/port và trạng thái TCP tương ứng), thường sống lâu hơn một request HTTP/1.1 khi dùng **keep-alive**. Một connection có thể chở nhiều HTTP requests tuần tự qua keep-alive; phía HTTP/2, nhiều logical streams có thể **đồng thời** chia sẻ một TCP/TLS connection.

Một connection còn cần file descriptor/socket buffers, flow-control state, TLS state khi có HTTPS và các cấu trúc trong HTTP server. Vì vậy không nên tin câu “event loop nhẹ nên connection không tốn gì”.

### 2.2. HTTP request và response

Một request là một trao đổi HTTP logic, ví dụ `GET /api/ping`. Phản hồi `200` có body, `204` không có body, hoặc event stream kéo dài. Một kết nối có thể chở nhiều request, và một request có thể tiếp tục qua nhiều bước async.

```text
HTTP/1.1 keep-alive trên một TCP connection:
  TCP connection ─ GET /a → 200 ─ GET /b → 200 ─ GET /c → 404 ─ close

HTTP/2 trên một TCP/TLS connection:
  Connection ─ stream 1: GET /events ───── nhiều DATA frames ───
             ├ stream 3: GET /api/a ─ 200
             └ stream 5: GET /api/b ────── 200
```

**Không suy ra:** 1000 concurrent requests nhất thiết là 1000 TCP connections. HTTP version, keep-alive, proxy, connection limits và client behavior quyết định mối quan hệ ấy.

### 2.3. Netty Channel

Trong Netty, `Channel` là abstraction của một I/O connection hoặc endpoint. `ChannelPipeline` chứa handlers chịu trách nhiệm xử lý inbound/outbound events và protocol transformations. Một channel thường gắn với một `EventLoop` chịu trách nhiệm I/O events của channel ấy; event loop có thể phục vụ **nhiều** channels.

### 2.4. Event-loop thread

Event loop chạy các callbacks cho I/O-ready events và scheduled tasks. Thay vì một thread ngủ chờ một socket, event loop xử lý ngắn gọn những event đã sẵn sàng. Nếu một callback gọi `Thread.sleep(2000)` hay blocking JDBC, **các channels khác cùng loop** có thể bị chậm theo.

Số lượng event-loop threads **phụ thuộc phiên bản, CPU, cấu hình, native transport và môi trường chạy**; không hardcode “luôn 1 loop/1 core” hoặc “chỉ có 2 threads”.

### 2.5. Servlet request worker thread

Trong Spring MVC synchronous, một worker thread thường chạy toàn bộ filter/controller/render sequence của **một request** và có thể bị giữ trong lúc dùng blocking APIs. Nhưng một TCP connection keep-alive **không bị ghim cùng một thread mọi thời điểm**. Connector Tomcat vẫn có cơ chế NIO theo dõi sockets; thread pool thường liên quan tới việc thực thi request processing chứ không đồng nhất connection count.

## 3. So sánh ba mô hình cùng một công việc

Giả sử API gọi một remote HTTP service cần 300 ms để phản hồi; business code hầu như không dùng CPU.

### 3.1. Spring MVC synchronous

```text
TCP socket ─ Servlet container/connector
                    │ request sẵn sàng
                    ▼
              Worker thread T1
                 ├─ Servlet Filters
                 ├─ DispatcherServlet
                 ├─ @Controller
                 ├─ blocking HTTP client call: chờ ~300ms
                 ├─ JSON serialize
                 └─ HTTP response
                    T1 trở về pool
```

Ở kiểu ứng dụng này, một worker có thể bị chiếm 300 ms dù ít tính toán. Nếu tất cả worker bận chờ, request mới có thể xếp hàng/reject theo server policy.

### 3.2. Spring MVC với Callable/DeferredResult

```text
Servlet worker T1:
  DispatcherServlet → Controller trả Callable / DeferredResult
  → Servlet async start → T1 được trả về pool

Callable: MVC AsyncTaskExecutor worker T2
  → blocking remote call (~300ms) → result

hoặc DeferredResult:
  → kết quả được producer callback/event điền vào

Servlet worker T3:
  ASYNC dispatch → serialize/write response → kết thúc
```

Servlet async giúp **giải phóng container thread ban đầu**, nhưng `Callable` đang chạy blocking operation vẫn giữ một executor worker; `DeferredResult` có thể giảm thread chờ nếu producer thật sự event-driven. MVC async không tự tạo non-blocking database/client API.

### 3.3. Spring WebFlux với non-blocking HTTP client

```text
Reactor Netty event-loop:
  HTTP request received
    → WebFlux handlers
    → WebClient reactive request được khởi tạo
    → Publisher chưa có kết quả; callbacks được đăng ký
    → event-loop không ngồi sleep chờ remote response

Khi remote HTTP data đến:
  → network readiness event
  → Reactor operator callbacks
  → HTTP codecs encode response
  → non-blocking write khi channel sẵn sàng
```

Với WebClient/driver thực sự non-blocking, không cần giữ một Java worker chỉ để chờ network. Vẫn có CPU để parse/serialize, TLS, xử lý business, và vẫn có thread pools riêng cho một số công việc.

### 3.4. Bảng khác biệt tổng hợp

| Câu hỏi | MVC sync (Servlet) | MVC async (Servlet) | WebFlux reactive |
| --- | --- | --- | --- |
| Điểm vào HTTP | Servlet/DispatcherServlet | Servlet/DispatcherServlet + async | HttpHandler/WebHandler/DispatcherHandler |
| Return type phổ biến | DTO, String, ResponseEntity | Callable, DeferredResult, CompletableFuture | Mono, Flux, Publisher, Coroutine |
| I/O downstream | Thường blocking nhưng có thể dùng async client | Blocking worker hoặc callback async | Thiết kế hướng non-blocking; vẫn có thể gọi blocking sai cách |
| Thread khi chờ | Worker request có thể chờ | Servlet thread rảnh; worker riêng có thể chờ | Không cần worker chờ nếu dependency reactive |
| Xử lý backpressure | Không phải hợp đồng chính của controller sync | Tùy async/streaming APIs | Reactive Streams demand xuyên publishers và server adapters khi hỗ trợ |
| Server runtime | Tomcat/Jetty/Undertow, Servlet | Tomcat/Jetty/Undertow, Servlet async | Reactor Netty mặc định Boot; cũng có Tomcat/Jetty/adapters |
| Phù hợp | Logic imperative/blocking, ecosystem Servlet | Async callback hoặc giải phóng Servlet thread | Nhiều concurrent I/O, streaming, reactive dependencies |
| Điều phải đo | pool saturation/queue | executor/timeout/cancellation | event-loop lag, buffers, demand, cancellation, connector pool |

**Không có người thắng mặc định.** MVC thường đơn giản hơn nếu toàn bộ stack JDBC và library đều blocking; WebFlux phát huy khi HTTP client, DB driver, broker adapters và serialization pipeline được thiết kế đúng non-blocking. Với workload CPU-bound, reactive không tự giúp tính nhanh hơn.

## 4. Spring WebFlux có bắt buộc Reactor Netty không?

**Không.** Spring WebFlux nằm ở web framework layer, dưới đó có adapter cho HTTP runtimes.

Spring Framework phân tầng:

```text
@REST Controller / RouterFunction
            │
            ▼
       DispatcherHandler
            │
            ▼
         WebHandler
            │
            ▼
        HttpHandler
            │
            ├── ReactorHttpHandlerAdapter → Reactor Netty
            ├── ServletHttpHandlerAdapter → Servlet container
            ├── Tomcat/Jetty adapters theo stack/version
            └── HTTP server adapter khác được framework hỗ trợ
```

Tên adapter và cách triển khai chính xác có thể khác theo Spring Framework version và embedded server. Điểm ổn định là **`HttpHandler` contract** có thể thích nghi với nhiều runtimes.

Trong Spring Boot **3.3.x**:

- `spring-boot-starter-webflux` mặc định đưa vào `spring-boot-starter-reactor-netty`, nên embedded server mặc định là **Reactor Netty**.
- Có thể thay server starter Netty bằng Tomcat/Jetty/Undertow **trong cấu hình Boot 3.3 phù hợp**. Khi đó ứng dụng vẫn là **WebFlux programming model**, không phải tự chuyển sang Spring MVC.
- Nếu đồng thời thêm `spring-boot-starter-web` và `spring-boot-starter-webflux`, Boot thường **ưu tiên MVC/Servlet web application type**. Đừng dùng việc có `Mono`/`Flux` trong classpath để suy ra server đang chạy WebFlux.
- Khi dùng WebFlux trên Tomcat/Jetty, HTTP non-blocking I/O được bridge từ Servlet support; đừng nói Tomcat “không thể chạy reactive”.
- Netty tự nó **không phải** Servlet container. Reactor Netty sử dụng Netty API/event loop, không thông qua DispatcherServlet.

### 4.1. Netty và Servlet container khác nhau ở API tầng dưới

| Thành phần | Reactor Netty runtime | Servlet container dùng cho MVC |
| --- | --- | --- |
| HTTP entrypoint | Netty Channel/Pipeline, Reactor HTTP adapters | Servlet request/response và Servlet filters |
| Java web framework | WebFlux WebHandler/DispatcherHandler | Spring MVC DispatcherServlet |
| I/O primitives | Netty asynchronous channel I/O | Servlet container connectors (có NIO), Servlet request/response APIs |
| Async semantics | Reactive Publisher, event-driven flow | Servlet async `AsyncContext` hoặc synchronous handler |
| Web filters | WebFlux `WebFilter`, `WebExceptionHandler` | Servlet `Filter`, Spring Security Servlet chain |
| Request objects | `ServerHttpRequest`, `ServerWebExchange` | `HttpServletRequest`/`HttpServletResponse` |
| Main risk | Blocking event loop, unbounded buffers | Worker pool saturation, blocking wait, queue pressure |

**Từ “Servlet container” không đồng nghĩa “blocking TCP socket API”.** Các containers hiện đại có non-blocking transport và asynchronous Servlet I/O, nhưng servlet-style controller code vẫn có thể block workers.

### 4.2. Boot chọn web application type

Ví dụ `pom.xml` chỉ gồm WebFlux starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Mặc định Boot tạo **reactive** application context và Reactor Netty embedded server.

Ví dụ chuyển embedded server sang Tomcat trên Boot 3.3 (mẫu thay thế dependencies; không đặt đồng thời hai server starters một cách vô ý):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-reactor-netty</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
</dependency>
```

Đây là một **biến thể thay thế** cho `pom.xml` demo ở mục 11, không kết hợp trực tiếp với dependency WebFlux starter vẫn giữ Reactor Netty. Khi thay server, test TLS/HTTP2/Servlet filters và resource management riêng.

## 5. HTTP server pipeline trong WebFlux

### 5.1. HttpHandler: contract tối thiểu

Trong Spring reactive HTTP layer, `HttpHandler` có thể hiểu dưới dạng:

```java
public interface HttpHandler {
    Mono<Void> handle(
        ServerHttpRequest request,
        ServerHttpResponse response);
}
```

Đây là chữ ký cốt lõi để giải thích; các interfaces thuộc package `org.springframework.http.server.reactive`. Kết quả `Mono<Void>` biểu diễn **quá trình hoàn tất việc xử lý HTTP response**, không phải một DTO/body duy nhất.

Spring triển khai/adapt contract này vào server API cụ thể:

- Reactor Netty: Netty inbound/outbound APIs.
- Servlet non-blocking bridge: HTTP request/response có thể được chuyển qua adapters sang Publisher-based body flow.
- Các server adapters khác theo version.

Bạn thường **không phải tự viết HttpHandler** trong Boot app; Boot và WebFlux auto-configuration lắp framework pipeline.

### 5.2. WebHandler và ServerWebExchange

`WebHandler` contract:

```java
public interface WebHandler {
    Mono<Void> handle(ServerWebExchange exchange);
}
```

`ServerWebExchange` gom request, response, session, attributes, locale/principal-related APIs và các context cần để xử lý một HTTP exchange. Nó **không phải** `HttpServletRequest`. Nếu chạy Reactor Netty, không có Servlet request native để ép cast.

Các lớp đại diện:

| Class/interface | Trách nhiệm |
| --- | --- |
| `HttpHandler` | Contract server-independent cho HTTP exchange |
| `ServerHttpRequest` | Reactive request abstraction, headers/method/body |
| `ServerHttpResponse` | Reactive response abstraction, status/headers/write |
| `WebHandler` | Contract xử lý một `ServerWebExchange` |
| `WebFilter` | Functional WebFlux filter chain ở tầng WebHandler |
| `WebExceptionHandler` | Xử lý lỗi trong reactive web chain |
| `DispatcherHandler` | WebFlux front controller tìm handler/router |
| `HandlerMapping` | Chọn controller/function dựa trên request |
| `HandlerAdapter` | Gọi handler tương ứng |
| `HandlerResultHandler` | Chuyển return value sang HTTP response |
| `HttpMessageReader/Writer` | Codec đọc/ghi body theo media type |
| `DataBuffer` | Buffer abstraction dùng cho body stream |

### 5.3. Full request flow

```text
Client TCP/TLS connection
    │ (HTTP/1.1 hoặc HTTP/2; Netty Channel)
    ▼
Reactor Netty event-loop / HTTP codecs
    ▼
ReactorHttpHandlerAdapter
    ▼
HttpHandler (Spring WebFlux)
    ▼
HttpWebHandlerAdapter / WebFilter chain
    ▼
DispatcherHandler
    ├── HandlerMappings → tìm @Controller hoặc RouterFunction
    ├── HandlerAdapter → invoke handler
    └── HandlerResultHandler → encode result
            │
            ├── Mono<DTO>       → JSON object hoặc response logic
            ├── Flux<DTO>       → finite JSON array / streaming khi phù hợp
            ├── Mono<Void>      → completion không cần body value
            └── Flux<DataBuffer> → body bytes theo demand
    ▼
ServerHttpResponse non-blocking write
    ▼
Netty outbound Channel → socket → client
```

Trên error path, WebFlux exception handling/filter chain có thể xử lý lỗi trước khi response committed. Nếu server đã flush một số SSE/stream bytes, không luôn chuyển được HTTP status từ 200 sang 500; phải dùng protocol-level error event hoặc kết thúc stream theo hợp đồng.

### 5.4. Các tầng codec và giới hạn buffer

Trước khi handler nhận object `@RequestBody Mono<MyDto>`, HTTP body bytes qua readers/decoders như Jackson. Khi handler trả Flux, encoding behavior phụ thuộc media type và codec.

Với `application/json`, một Flux hữu hạn thường được encode thành JSON array; **không cam kết flush từng item ngay khi onNext xuất hiện**. Với `text/event-stream` hoặc newline-delimited JSON, streaming semantics rõ ràng hơn.

Spring Boot có property:

```properties
spring.codec.max-in-memory-size=256KB
```

Đây là giới hạn in-memory buffering khi codecs cần aggregate, không phải giới hạn tổng số bytes mỗi TCP connection hay bộ nhớ toàn bộ JVM. Trường hợp body lớn phải cân nhắc streaming readers, uploads, max request size tại proxy/server và chính sách lưu trữ. Lỗi `DataBufferLimitException` thường liên quan tới giới hạn codec aggregation.

## 6. Event loop thực thi request như thế nào?

### 6.1. Sơ đồ một vòng xử lý

```text
Nhiều channels cùng được gán cho các event loops:

Channel A: data ready  ───┐
Channel B: idle         ───┤      EventLoop #1
Channel C: write ready ───┘       ├─ đọc bytes A
                                 ├─ decode HTTP request A
                                 ├─ gọi các non-blocking Reactor callbacks
                                 └─ flush bytes C khi writable

Channel D: data ready  ───┐
Channel E: idle         ───┤      EventLoop #2
Channel F: stream       ───┘       ├─ xử lý D
                                 └─ tiếp tục F khi có demand/ready
```

Đây là sơ đồ **minh họa**, không phải khẳng định mỗi instance dùng đúng hai loops. Event loop có thể thực thi scheduled tasks, channel callbacks, TLS encode/decode và logic framework. Nó phải tránh công việc blocking kéo dài để các channels khác không bị starve.

### 6.2. Đâu là công việc hợp lý trên event loop?

- Parse header/kết nối, route handler nhẹ.
- Compose reactive Publishers.
- Mapping/validation/serialization có chi phí vừa phải.
- Scheduling network writes và reactive callbacks ngắn.

### 6.3. Công việc không nên giữ event-loop

- `Thread.sleep(1000)` hoặc `CompletableFuture.get()` chặn.
- JDBC/JPA query blocking.
- Gọi HTTP bằng client đồng bộ hoặc `RestTemplate` trong path event-loop.
- Đọc/ghi file lớn theo Java blocking I/O trực tiếp.
- Tính CPU nặng hàng trăm ms, mã hóa/giải nén lớn trên loop.
- Thao tác database transaction dài trong code imperative.

**Một request blocking có thể làm chậm nhiều requests khác** nếu chúng chia sẻ event loop. Khi debugging hãy xem thread dump, event-loop latency, BlockHound (nếu cấu hình cho test), request queue và upstream timeouts; không chỉ số HTTP 500.

### 6.4. Mono/Flux không tự biến blocking thành non-blocking

Sai:

```java
@GetMapping("/bad")
Mono<String> bad() {
    return Mono.just(legacyBlockingClient.call());
}
```

`legacyBlockingClient.call()` chạy **ngay lúc controller được gọi**, trước khi Mono được tạo. API trả Mono không thay đổi tính blocking của lời gọi này.

Cũng chưa đủ:

```java
@GetMapping("/still-bad")
Mono<String> badToo() {
    return Mono.fromCallable(() -> legacyBlockingClient.call());
}
```

Đoạn này **lazy** nhưng khi subscribe vẫn có thể chạy trên event loop nếu không chọn scheduler thích hợp. Khi buộc phải bridge một blocking API, dùng giải pháp tạm:

```java
@GetMapping("/bridge")
Mono<String> bridge() {
    return Mono.fromCallable(() -> legacyBlockingClient.call())
        .subscribeOn(Schedulers.boundedElastic());
}
```

Snippet cần `legacyBlockingClient` dependency và Reactor imports. `boundedElastic` là scheduler được giới hạn phù hợp hơn với blocking tasks, nhưng không biến JDBC thành non-blocking; nếu pool/queue bão hòa vẫn có delay/reject. Ưu tiên R2DBC, WebClient hoặc reactive clients nếu cần non-blocking end-to-end.

### 6.5. CPU-bound không được che dưới boundedElastic

Nếu task là transform cryptography/compression/ML rất nặng, việc dùng boundedElastic (thiết kế chủ yếu cho blocking waits) không đảm bảo latency. Dùng bounded compute executor/scheduler theo tài nguyên CPU với workload/queue policy đúng, hoặc tách dịch vụ/worker job khi cần. Reactor `Schedulers.parallel()` hỗ trợ các tác vụ CPU ngắn/phù hợp, nhưng cần tránh monopolize worker thread bằng loops vô hạn.

## 7. Backpressure từ Publisher đến network

Reactive Streams phân biệt:

- **Publisher** có khả năng phát dữ liệu.
- **Subscriber** nhận dữ liệu.
- **Subscription** quản lý request(n)/cancel.
- **Demand**: downstream muốn thêm bao nhiêu items.

Ví dụ:

```text
Flux.just(1,2,3,4,5)
       │
       ▼
Subscriber request(2)
       │
       ├── onNext(1)
       └── onNext(2)
       │
Subscriber request(3)
       ├── onNext(3)
       ├── onNext(4)
       └── onNext(5)
       │
       └── onComplete()
```

Với WebFlux, HTTP readers/writers và Reactor Netty liên kết downstream readiness/buffering với reactive subscriptions trong những hoàn cảnh hỗ trợ. Nhưng phải phân biệt ba lớp flow control:

1. **Reactive Streams demand** giữa Publisher/Subscriber trong JVM.
2. **TCP flow control** dựa vào receive window/network stacks.
3. **HTTP/2 stream/connection flow control** tại protocol layer.

Chúng có tương tác nhưng **không phải một đối một** và không đảm bảo mọi external producer tuân thủ demand. Một upstream broker callback push liên tục có thể tạo `Flux` vượt tốc độ client; bạn phải đặt policy buffer/drop/error và quan sát memory.

### 7.1. Streaming và JSON array

```java
@GetMapping("/api/numbers")
Flux<Integer> numbers() {
    return Flux.range(1, 5);
}
```

Với content type JSON thường là body:

```json
[1,2,3,4,5]
```

Response codec có thể buffer và không gửi từng item riêng biệt tới browser theo thời điểm `onNext`. Muốn client thấy event ngay, dùng SSE:

```java
@GetMapping(
        value = "/api/ticks",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<ServerSentEvent<String>> ticks() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(n -> ServerSentEvent.<String>builder()
            .event("tick")
            .data("tick-" + n)
            .build());
}
```

Đây là snippet minh họa trong controller, stream vô hạn **cần xử lý cancellation, timeout và rate-limit** trong production. Dự án runnable mục 11 giới hạn ba events để test dễ.

### 7.2. Streaming cũng cần giới hạn

- `collectList()` phải giữ tất cả items tới khi Flux hoàn tất. Nếu Flux vô hạn, không có result và heap có thể tăng.
- `onBackpressureBuffer()` không giới hạn có thể dẫn tới OOM.
- `Flux.interval(...)` khi consumer chậm có thể phát lỗi overflow tùy strategy; không coi timer là data queue tin cậy.
- `doFinally` hữu ích để thu dọn resource khi stream **complete, error hoặc cancel**.
- WebSocket/SSE nhiều clients cần connection budgets, heartbeat và proxy idle timeouts.

## 8. WebClient outbound connections: khác với inbound server

Một trong những nhầm lẫn phổ biến là coi số HTTP connections do Reactor Netty **server** chấp nhận giống với số connections mà `WebClient` tạo tới remote services.

### 8.1. Inbound server socket

`GET /api/proxy` từ browser vào app: app đang **accept inbound** connection thông qua Reactor Netty HttpServer. Server quản lý channels, request read/write, TLS, keep-alive và event loops.

### 8.2. Outbound WebClient pool

Bên trong handler, WebClient gọi `https://catalog.example.test`: app đóng vai trò **HTTP client**, cần tạo hoặc reuse **outbound connections** tới target host. Reactor Netty HttpClient thường dùng connection pooling phù hợp; có thể quản lý bằng `ConnectionProvider`.

```text
Browser
   ─ TCP/TLS inbound #1 ─► Spring WebFlux / Reactor Netty server
                            │
                            │ WebClient.get(...)
                            ▼
                       HttpClient pool (outbound)
                            │   ├ TCP/TLS outbound A
                            │   ├ TCP/TLS outbound B
                            │   └ pending acquire queue
                            ▼
                      Remote catalog service
```

**Không gán** “1 incoming request = 1 outbound connection”. Outbound connections có thể được reuse; một incoming request có thể fan-out sang nhiều services; pending acquire queue có thể đầy dù inbound server còn nhận socket.

### 8.3. Custom outbound pool ví dụ

Một cấu hình tường minh (đặt trong `@Configuration`; đây là snippet minh họa không phải phần bắt buộc của runnable demo):

```java
@Bean(destroyMethod = "dispose")
ConnectionProvider catalogPool() {
    return ConnectionProvider.builder("catalog-pool")
        .maxConnections(30)
        .pendingAcquireMaxCount(60)
        .pendingAcquireTimeout(Duration.ofSeconds(2))
        .maxIdleTime(Duration.ofSeconds(20))
        .build();
}

@Bean
WebClient catalogWebClient(WebClient.Builder builder,
                           ConnectionProvider catalogPool) {
    HttpClient client = HttpClient.create(catalogPool)
        .responseTimeout(Duration.ofSeconds(3))
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);

    return builder
        .baseUrl("https://catalog.example.test")
        .clientConnector(new ReactorClientHttpConnector(client))
        .build();
}
```

Cần imports `reactor.netty.resources.ConnectionProvider`, `reactor.netty.http.client.HttpClient`, `io.netty.channel.ChannelOption`, `org.springframework.http.client.reactive.ReactorClientHttpConnector`, `org.springframework.web.reactive.function.client.WebClient`, `Duration` và Spring `@Bean`. Host là domain ví dụ, không phải endpoint thật để chạy integration tests. Khi dùng Boot, thường nên **reuse `WebClient.Builder` được Boot cung cấp** thay vì tự new WebClient cho mỗi request.

Ý nghĩa:

- `maxConnections(30)` hạn mức pool trong cấu hình ví dụ, không phải default chung cho Reactor Netty phiên bản bất kỳ.
- `pendingAcquireMaxCount(60)` giới hạn số requests chờ connection khi pool đang bận.
- `pendingAcquireTimeout(2s)` giới hạn thời gian chờ lấy một connection từ pool.
- `CONNECT_TIMEOUT_MILLIS` xử lý timeout của giai đoạn mở TCP connection.
- `responseTimeout` là chính sách thời gian đợi response network I/O của HttpClient; khác timeout nghiệp vụ end-to-end.
- `maxIdleTime` nhằm hạn chế giữ connection idle quá lâu; nên cân nhắc timeout phía remote/proxy.
- `destroyMethod="dispose"` cho custom ConnectionProvider một lifecycle cleanup khi Spring context đóng.

Không sao chép 30/60/2s cho production nếu chưa đo traffic, TLS handshakes, upstream rate limits, p95 response time và pool saturation. Một pool cực lớn có thể khiến upstream/cổng NAT hoặc OS cạn resources. Một pool quá nhỏ lại tạo pending acquire latency và timeout.

### 8.4. Các loại timeout cần phân biệt

| Loại | Liên quan |
| --- | --- |
| TCP connect timeout | Thời gian kết nối tới remote host |
| TLS handshake timeout | Thời gian bắt tay TLS |
| Pool pending acquire timeout | Chờ lấy outbound connection rảnh |
| HTTP response timeout | Chờ response/network progress theo HttpClient semantics |
| Reactive `timeout(Duration)` | Time budget của publisher theo operator |
| Inbound HTTP server idle timeout | Kết nối client tới server không hoạt động |
| Reverse proxy read timeout | Gateway/LB có thể đóng request trước server |
| Business deadline | Toàn bộ tác vụ phải xong trước SLA |

Đặt `timeout(Duration)` vào Mono không nhất thiết **hủy ngay** mọi side effect đã được remote server nhận. Cancellation sẽ propagate qua reactive client khi hỗ trợ, nhưng remote nghiệp vụ có thể vẫn đang chạy; cân nhắc idempotency cho POST, retries và duplicate suppression.

## 9. Thay đổi runtime: Reactor Netty, Tomcat, Jetty

### 9.1. Netty mặc định của Boot

Khi dùng `spring-boot-starter-webflux` mà không loại Netty, Boot thường chọn Netty reactive web server factory. Điều này có lợi khi WebClient cũng dùng Reactor Netty vì có thể chia sẻ một số event-loop resources theo configuration.

### 9.2. WebFlux trên Servlet adapter

Có thể sử dụng Servlet container hỗ trợ async/non-blocking I/O để adapt WebFlux contract. Khi đó app code vẫn return Mono/Flux, còn server adapter ánh xạ reactive read/write lên Servlet non-blocking APIs. **Không** dùng `HttpServletRequest` và `DispatcherServlet` như abstraction trung tâm trong controller WebFlux, chỉ vì runtime là Tomcat.

### 9.3. Spring MVC trên Netty?

Spring MVC thiết kế trên Servlet API; Reactor Netty không phải Servlet container. Vì vậy không suy diễn rằng thay `spring-boot-starter-tomcat` bằng `reactor-netty` sẽ khiến Spring MVC controllers chạy đúng trên Netty như WebFlux. Nếu ứng dụng còn cần Servlet filters, JSP, synchronous servlet integrations, hãy chọn MVC/Servlet runtime phù hợp.

### 9.4. HTTP/2 và TLS

HTTP/2 multiplexing làm thay đổi mapping connection→request. Một connection có thể mang nhiều streams. Bật HTTP/2 còn phụ thuộc TLS/h2 ALPN hoặc h2c, phiên bản Netty/Tomcat/Jetty, reverse proxy và client.

Trong Boot, cấu hình:

```properties
server.http2.enabled=true
```

Chỉ là một phần: kiểm tra protocol negotiation và TLS/certificate/runtime support theo server. Không gắn HTTP/2 đồng nghĩa reactive; MVC/Tomcat cũng có thể phục vụ HTTP/2.

## 10. So sánh performance và giới hạn thực tế

### 10.1. Ít threads không tự động nhanh hơn

Bảng *giả lập để học*, không phải benchmark Spring thực tế:

| Workload | Servlet sync | Reactive non-blocking |
| --- | --- | --- |
| 1 request gọi remote API và chờ 500ms | Một worker có thể bị giữ lúc chờ | Event loop không phải chờ nếu WebClient non-blocking |
| 1000 requests chờ remote API 500ms | Worker/queue có thể chịu áp lực | Có thể giữ nhiều in-flight subscriptions với ít event-loop threads |
| 1 request mã hóa 2GB CPU-intensive | Vẫn phải dùng CPU | Vẫn phải dùng CPU; event-loop còn dễ bị monopolize nếu chạy trực tiếp |
| 1 request JDBC blocking 500ms | Đặc tính blocking rõ ràng | Nếu gọi trực tiếp trong event-loop sẽ gây nghẽn, dù method return Mono |
| Nhiều SSE connections | Servlet async có mô hình phù hợp nếu cấu hình đúng | Reactive streaming/backpressure tương thích tự nhiên |
| 100% cache-hit CDN | Origin có thể không nhận request | Origin cũng có thể không nhận request |

Điều cần đo là **p95/p99 latency, throughput, CPU, active threads, queue lengths, heap, GC, connection metrics, response bytes, event-loop lag** và downstream pool saturation. Không so hai server với thread pool/database config khác nhau rồi kết luận framework quyết định tất cả.

### 10.2. Little's Law và thời gian chờ

Một mô hình quan hệ phổ biến: số requests in-flight trung bình xấp xỉ arrival rate × latency trung bình trong hệ thống ổn định. Nếu nhiều request có latency chủ yếu do I/O wait, concurrency có thể cao dù CPU thấp. Reactive làm mô hình **giữ thread** linh hoạt hơn, nhưng **không biến số in-flight requests hoặc buffer thành zero**.

Ví dụ 200 requests/s × 0,5 giây latency ≈ 100 requests in-flight trung bình. Đây chỉ là ví dụ số học, không hứa server có thể phục vụ vô hạn requests nếu có đủ event-loop threads.

### 10.3. Tài nguyên dễ hết dù event loops ít

- File descriptors và ephemeral ports.
- Heap memory cho request metadata, pending subscriptions, buffers, JSON objects.
- Direct/off-heap buffers trong Netty.
- HTTP client connection pool và DNS/TLS overhead.
- DB connection pool / remote upstream quota.
- Pending writes khi client download chậm.
- Retry storms, timeouts và queue overload.
- CPU spike do codecs, compression và TLS.
- Long-lived SSE/WebSocket state, heartbeat và proxy idling.

Cần thiết kế connection budgets, bulkheads, admission control/rate limit và graceful degradation. Không tạo unbounded queue chỉ vì endpoint được gọi “async”.

## 11. Demo runnable: Spring Boot 3 + Java 17 trên Reactor Netty

Ứng dụng sau minh họa **đúng những khác biệt có thể quan sát trực tiếp** mà không giả vờ đếm event-loop thread bằng tests không ổn định.

Endpoint:

| URL | Return type | Mục đích |
| --- | --- | --- |
| `GET /api/ping` | `Mono<Reply>` | Một kết quả JSON |
| `GET /api/delay?ms=25` | `Mono<Reply>` | Delay không chặn bằng `Mono.delay` |
| `GET /api/blocking-bridge` | `Mono<Reply>` | Offload `Thread.sleep` demo sang `boundedElastic` |
| `GET /api/headers` | `Mono<Map>` | Đọc HTTP headers trong WebFlux, không dùng Servlet request |
| `GET /api/none` | `Mono<ResponseEntity<Void>>` | Explicit HTTP 204 |
| `GET /api/stream` | `Flux<ServerSentEvent<String>>` | Finite SSE stream với 3 events |
| `GET /api/fail` | `Mono<Reply>` | Reactive error đi qua exception handling |
| Tất cả `/api/**` | `WebFilter` | Response header cho biết WebFlux filter đã chạy |

Không dùng dịch vụ remote hoặc database thật, nên dự án có thể chạy sau khi Maven tải dependencies. Đoạn `Thread.sleep` bên trong boundedElastic chỉ làm ví dụ phân biệt event-loop với worker, **không phải phương pháp ưu tiên** cho sản phẩm thật.

### 11.1. Cây thư mục

```text
reactive-server-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/
    │   │   ├── ReactiveServerApplication.java
    │   │   ├── ApiController.java
    │   │   └── ReactiveFilter.java
    │   └── resources/application.properties
    └── test/
        └── java/example/ApiControllerTest.java
```

### 11.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>reactive-server-demo</artifactId>
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

### 11.3. ReactiveServerApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveServerApplication.class, args);
    }
}
```

### 11.4. application.properties

```properties
server.port=8080
spring.codec.max-in-memory-size=256KB
logging.level.reactor.netty.http.server=INFO
```

Property codec là một guardrail khi decoder cần buffering; không có nghĩa “mọi body trên 256KB đều bị từ chối” trong mọi streaming path. Tùy source, filter và decoder, có thể có giới hạn khác từ server/proxy.

### 11.5. ApiController.java

```java
package example;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class ApiController {

    public record Reply(String kind, long value) {}

    @GetMapping("/api/ping")
    public Mono<Reply> ping() {
        return Mono.just(new Reply("pong", 1));
    }

    @GetMapping("/api/delay")
    public Mono<Reply> delay(
            @RequestParam(defaultValue = "25") long ms) {

        if (ms < 0 || ms > 2000) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "ms must be in 0..2000"));
        }

        return Mono.delay(Duration.ofMillis(ms))
                .map(tick -> new Reply("delayed", ms));
    }

    @GetMapping("/api/blocking-bridge")
    public Mono<Reply> bridge() {
        return Mono.fromCallable(() -> {
                // Blocking legacy work được cô lập khỏi event loop.
                TimeUnit.MILLISECONDS.sleep(20);
                return new Reply("bridged", 20);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/api/headers")
    public Mono<Map<String, String>> headers(
            @RequestHeader(name = "X-Demo", defaultValue = "absent")
                    String demo) {

        return Mono.just(Map.of("demo", demo));
    }

    @GetMapping("/api/none")
    public Mono<ResponseEntity<Void>> none() {
        return Mono.<Void>empty()
                .thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping(
            value = "/api/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream() {
        return Flux.interval(Duration.ofMillis(20))
            .take(3)
            .map(index -> ServerSentEvent.<String>builder()
                .id(Long.toString(index))
                .event("tick")
                .data("tick-" + index)
                .build());
    }

    @GetMapping("/api/fail")
    public Mono<Reply> fail() {
        return Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "dependency temporarily unavailable"));
    }
}
```

**Code này minh họa gì?**

- `Mono.just` trả một `Reply` ngay mà không cần thêm thread.
- `Mono.delay` phát tín hiệu khi timer scheduler tới hạn, **không dùng `Thread.sleep` trên event loop**.
- `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` cho phép tiếp tục dùng một blocking legacy function ngắn nhưng tránh chặn event-loop. Cần đặt timeout/bulkhead thực ở production.
- `@RequestHeader` vẫn có trong annotated WebFlux, nhưng request infrastructure reactive không yêu cầu `HttpServletRequest`.
- `Mono<Void>.thenReturn(ResponseEntity.noContent())` tạo HTTP 204 một cách tường minh.
- `Flux.interval(...).take(3)` dừng sau 3 events để response hữu hạn, dễ test; stream thật có thể kéo dài và cần cleanup/cancellation.
- Error từ `Mono.error(ResponseStatusException)` được WebFlux error handling chuyển thành status, nếu response chưa committed.

**Không khẳng định một request qua Mono luôn chạy trên một thread cố định.** Các Reactor schedulers và Netty callbacks có thể thay đổi execution context; test body/status là cách xác minh hành vi ổn định, thread name chỉ có giá trị diagnostics.

### 11.6. ReactiveFilter.java

```java
package example;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(0)
public class ReactiveFilter implements WebFilter {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {

        if (exchange.getRequest().getPath()
                .value().startsWith("/api/")) {
            exchange.getResponse().getHeaders()
                    .add("X-Processing-Stack", "webflux");
        }
        return chain.filter(exchange);
    }
}
```

Đây là **WebFlux WebFilter**, không phải `jakarta.servlet.Filter`. Trong ứng dụng này, response header `X-Processing-Stack: webflux` giúp test xác nhận filter đi qua chain. Nó không phải security control, và tên header này chỉ để học.

WebFlux Security nếu thêm `spring-boot-starter-security` sử dụng `SecurityWebFilterChain` / reactive authentication/authorization; không tự áp Servlet `SecurityFilterChain` như trong Spring MVC. Với deploy hybrid, phải nhận diện web application type đúng.

### 11.7. Chạy và quan sát bằng cURL

Ở thư mục `pom.xml` với Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Một vài requests:

```bash
curl -i http://localhost:8080/api/ping
curl -i "http://localhost:8080/api/delay?ms=25"
curl -i http://localhost:8080/api/blocking-bridge
curl -i -H "X-Demo: hello" http://localhost:8080/api/headers
curl -i http://localhost:8080/api/none
curl -i "http://localhost:8080/api/delay?ms=-1"
curl -i http://localhost:8080/api/fail
```

Kỳ vọng:

| URL | Status | Body chính |
| --- | ---: | --- |
| `/api/ping` | 200 | `{"kind":"pong","value":1}` |
| `/api/delay?ms=25` | 200 | `{"kind":"delayed","value":25}` |
| `/api/blocking-bridge` | 200 | `{"kind":"bridged","value":20}` |
| `/api/headers` với X-Demo: hello | 200 | `{"demo":"hello"}` |
| `/api/none` | 204 | Không có body |
| `/api/delay?ms=-1` | 400 | Error response do Spring error handler định dạng |
| `/api/fail` | 503 | Error response do Spring error handler định dạng |

Kiểm thử SSE:

```bash
curl -N -i -H "Accept: text/event-stream" \
  http://localhost:8080/api/stream
```

Body dự kiến ở media type SSE (bỏ các response headers thay đổi):

```text
id:0
event:tick
data:tick-0

id:1
event:tick
data:tick-1

id:2
event:tick
data:tick-2

```

Không hardcode thứ tự HTTP headers hay Reactor Netty thread name; cURL `-N` tắt output buffering của riêng curl, không đổi semantics server. Dù dùng Flux streaming, actual flush timing còn phụ thuộc server, client và proxy.

### 11.8. Quan sát điều gì ở logs?

Reactor Netty có thể log lúc HTTP server khởi động, port đang bind và lỗi transport. Không đảm bảo tên thread cụ thể theo version hoặc môi trường. Nếu cần đo event-loop state, dùng metrics/observability phù hợp, thread dumps và load test; không suy từ việc JVM có ít threads ở idle ra khả năng chịu tải peak.

## 12. WebTestClient: kiểm thử API reactive end-to-end

Spring WebFlux thường dùng `WebTestClient`. Với `@SpringBootTest(webEnvironment = RANDOM_PORT)` và `@AutoConfigureWebTestClient`, tests gửi HTTP tới embedded server, kiểm tra Spring routing/filter/codecs/Netty configuration hiệu quả hơn unit-test controller thuần.

File `src/test/java/example/ApiControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void pingIsJsonAndPassesReactiveWebFilter() {
        client.get().uri("/api/ping")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(
                    "X-Processing-Stack", "webflux")
            .expectBody()
                .jsonPath("$.kind").isEqualTo("pong")
                .jsonPath("$.value").isEqualTo(1);
    }

    @Test
    void reactiveDelayReturnsExpectedValue() {
        client.get().uri("/api/delay?ms=1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.kind").isEqualTo("delayed")
                .jsonPath("$.value").isEqualTo(1);
    }

    @Test
    void negativeDelayIsRejected() {
        client.get().uri("/api/delay?ms=-1")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void tooLargeDelayIsRejected() {
        client.get().uri("/api/delay?ms=5000")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void blockingBridgeReturnsFromOffloadedPublisher() {
        client.get().uri("/api/blocking-bridge")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.kind").isEqualTo("bridged")
                .jsonPath("$.value").isEqualTo(20);
    }

    @Test
    void requestHeaderIsAvailableThroughWebFlux() {
        client.get().uri("/api/headers")
            .header("X-Demo", "checked")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
                .jsonPath("$.demo").isEqualTo("checked");
    }

    @Test
    void noBodyEndpointReturns204() {
        client.get().uri("/api/none")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();
    }

    @Test
    void reactiveErrorMapsTo503() {
        client.get().uri("/api/fail")
            .exchange()
            .expectStatus().isEqualTo(503);
    }

    @Test
    void sseEndpointHasCorrectContentType() {
        client.get().uri("/api/stream")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void missingRouteIsNotFound() {
        client.get().uri("/api/does-not-exist")
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

Bài kiểm thử không assert “server có đúng N event-loop threads” vì đó là implementation/configuration detail, không phải API contract. Test `reactiveDelayReturnsExpectedValue` xác nhận reactive timer completion; nó **không** chứng minh CPU/latency tốt hơn MVC.

### 12.1. Kiểm thử SSE body thay vì chỉ Content-Type

Một test stream sâu hơn có thể dùng:

```java
FluxExchangeResult<ServerSentEvent<String>> result =
    client.get().uri("/api/stream")
        .accept(MediaType.TEXT_EVENT_STREAM)
        .exchange()
        .expectStatus().isOk()
        .returnResult(new ParameterizedTypeReference<
                ServerSentEvent<String>>() {});

StepVerifier.create(result.getResponseBody())
    .expectNextMatches(e -> "tick-0".equals(e.data()))
    .expectNextMatches(e -> "tick-1".equals(e.data()))
    .expectNextMatches(e -> "tick-2".equals(e.data()))
    .verifyComplete();
```

Đây là snippet bổ sung đặt trong test method, cần imports `FluxExchangeResult`, `ServerSentEvent`, `ParameterizedTypeReference`, `StepVerifier`. Không dùng câu lệnh này ở ngoài một context đã có `client`. Ưu điểm: kiểm tra **ba signals SSE thực**, không chỉ header.

### 12.2. Testing không thay thế load testing

Để đo reactive concurrency, cần test representative traffic: 100–1000 concurrent clients, wait latency thực, connection reuse và response sizes. Dùng JFR, Micrometer/Actuator, Reactor Netty metrics, OS socket stats, thread dumps và network tooling. Đừng kết luận WebFlux nhanh hơn MVC chỉ dựa vào 10 HTTP assertions đúng.

## 13. Quản lý response lifecycle và client disconnect

### 13.1. Khi response chưa bắt đầu

Nếu handler trả `Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST))` trước commit, WebFlux có thể set status và error response tương ứng. Security filters/CORS/codecs vẫn có thể tham gia theo configuration.

### 13.2. Khi SSE đã gửi bytes

```text
GET /api/stream
HTTP/1.1 200 OK
Content-Type: text/event-stream

data:tick-0

data:tick-1

... sau đó upstream lỗi ...
```

Khi response đã committed và gửi event cho client, server không thể quay lại đổi 200 thành HTTP 500 theo cách của một normal JSON response. Có thể encode sự kiện error theo protocol SSE hoặc đóng stream và để client retry theo policy.

### 13.3. Khi client hủy

Reactive subscription có thể nhận cancellation khi browser đóng kết nối hoặc user hủy request. Các cleanup hooks:

```java
Flux<String> values =
    service.events()
        .doFinally(signal -> {
            // signal có thể COMPLETE, ERROR hoặc CANCEL.
            // Thu thập metric/dọn tài nguyên nhẹ;
            // không chạy blocking operation trong event loop.
        });
```

Đây là snippet minh họa với `service.events()` giả định. Client disconnect có thể không được phát hiện ngay khi qua proxy/LB; cần heartbeat/idle timeout khi ứng dụng cần phát hiện kết nối chết sớm.

### 13.4. Retry và idempotency

Không retry POST không idempotent theo cách mù quáng khi có lỗi network. Remote có thể đã nhận request và thực hiện side effect trước khi kết nối mất. Dùng idempotency keys, transaction/broker semantics, retry/backoff có giới hạn và duplicate suppression.

## 14. ServerResource và lifecycle

Trong Reactor Netty, event loops, pooled buffers và connections đều là tài nguyên có lifecycle.

Với Boot:

- Sử dụng embedded Netty server qua auto-configuration thay vì tự tạo `HttpServer.bindNow()` ở nhiều nơi.
- Reuse `WebClient.Builder` do Boot cung cấp; đừng tạo client/pool mới cho **mỗi HTTP request**.
- Khi tự tạo `ConnectionProvider`, dispose đúng lúc ứng dụng đóng.
- Đối với Netty LoopResources custom, cần quản lý disposal và tránh tạo event loops trùng nhau.
- Khi thay client connector hoặc server factory, xác định cái nào sở hữu event-loop resources.
- Graceful shutdown có thể dùng Boot:

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

Thời gian shutdown thực tế với long-lived SSE/WebSocket/request phụ thuộc server/runtime và ứng dụng. Không giả định mọi connection luôn được drain trước timeout chỉ nhờ bật graceful shutdown.

### 14.1. Kết nối keep-alive và idle timeout

Một connection đã xử lý xong HTTP request có thể được giữ lại để tái sử dụng. Nếu idle quá lâu, server/client/proxy có thể đóng. Với SSE, connection còn hoạt động logic nhưng có thể không có bytes trong thời gian dài; proxy có thể áp read timeout riêng. Cần heartbeat phù hợp và tránh giữ bất kỳ connection vô hạn nếu client không thực sự tồn tại.

### 14.2. HTTP server metrics nên có

- Active/open connections, new connections và close/error counts.
- Requests in-flight, completed statuses, latency percentiles.
- Event-loop pending tasks / blocking detection / latency.
- Outbound connection pool active/idle/pending/acquire failures.
- TLS handshake/connect timeouts.
- Response stream cancellation/error counts.
- Heap/direct memory, GC pause và DataBuffer utilization.
- Remote dependency latency và circuit-breaker state.
- TCP retransmits, proxy 502/503/504 và OS file-descriptor usage.

Các tên metric cụ thể tùy Reactor Netty/Micrometer/Actuator versions; không tự bịa tên series mà không kiểm tra runtime thực tế.

## 15. Security, CORS và client identity

Reactive server không tự thay thế lớp bảo mật.

- Spring Security reactive sử dụng `SecurityWebFilterChain`, không dùng Servlet `SecurityFilterChain` như MVC.
- CORS quyết định origin nào được browser chia sẻ response; không phải authentication/authorization.
- Nếu login session/cookie tự đính kèm, cần CSRF protection cho state-changing methods phù hợp; “WebFlux” không làm CSRF biến mất.
- Nếu gateway reverse proxy gửi `X-Forwarded-For` hoặc `Forwarded`, chỉ trust những forwarded headers từ proxy đáng tin; request client có thể giả header nếu ingress chưa strip/validate.
- Rate limit theo trusted principal/tenant và resource usage, không chỉ source IP.
- Request body limits, WebSocket/SSE limits, idle timeout và HTTP/2 stream limits có thể là lớp bảo vệ against resource exhaustion.
- Không log raw bearer tokens, passwords, session cookies, personal data trong headers hoặc payload.
- TLS termination ở proxy thay đổi nơi có encryption/cert/ALPN; đo transport theo layer chính xác.

### 15.1. Reactor Context và ThreadLocal

Một request reactive có thể chuyển thread qua operators. Không dựa vào ThreadLocal/MDC truyền xuyên chain theo cách imperative nếu không có tích hợp context propagation phù hợp. Reactor `Context` gắn với subscriber; Spring Security reactive có cơ chế context tương ứng.

Ví dụ:

```java
Mono<String> trace =
    Mono.deferContextual(ctx ->
        Mono.just(ctx.getOrDefault("traceId", "absent")))
        .contextWrite(Context.of("traceId", "req-001"));
```

Đây là snippet minh họa, cần `reactor.util.context.Context`. Đừng đưa principal giả từ request header vào Context rồi coi đó là đã xác thực.

## 16. Gỡ lỗi theo triệu chứng

| Triệu chứng | Khả năng nguyên nhân | Bước kiểm tra |
| --- | --- | --- |
| WebFlux thấy rất nhiều requests pending | Downstream chậm, pool đầy, retry storms | HTTP client pool, downstream p95, pending queues |
| CPU thấp nhưng p99 latency cao | Blocking waits hoặc network timeouts | Thread dump, event-loop lag, client connect/response timeout |
| Một blocking handler làm nhiều APIs chậm | Chặn event-loop | Tìm `block()`, `Thread.sleep`, JDBC, sync HTTP |
| `DataBufferLimitException` | Codec aggregate vượt max-in-memory | Body format, codec limit, streaming strategy |
| Netty direct memory tăng | Buffers chưa giải phóng/traffic lớn | Memory metrics, buffer ownership, leak detection |
| Connection refused | Server không bind đúng host/port hoặc backend chết | Startup logs, listener, LB target |
| Nhiều `PrematureCloseException` | Client/remote/proxy đóng sớm, overload/timeouts | Connect, maxIdleTime, LB idle, retries |
| WebClient pending acquire timeout | Outbound pool bão hòa | Active/idle/pending counts, remote response time |
| SSE tự ngắt sau một phút | Proxy idle/read timeout hoặc không heartbeat | Reverse proxy/LB, heartbeat, cancellation |
| HTTP/2 chỉ dùng HTTP/1.1 | ALPN/TLS/proxy/runtime chưa negotiate | curl `--http2`, server config, TLS chain |
| Security filter Servlet không chạy trên Netty | Đang dùng WebFlux reactive stack | Sử dụng SecurityWebFilterChain |
| WebFlux controller chạy nhưng có DispatcherServlet | Boot đã chọn Servlet/MVC vì dependency mix | Kiểm tra starters/web-application-type |
| Trả Flux nhưng client không thấy item ngay | JSON array buffering/content negotiation | SSE/NDJSON media type và codec |
| Test kỳ vọng một thread cố định | Scheduler/Netty implementation thay đổi | Assert API contract, đo threads riêng |
| Requests bị 504 ở gateway nhưng app vẫn làm việc | Gateway timeout trước business completion | End-to-end deadlines, cancellation, idempotency |
| OOM dù chỉ vài event-loop threads | Nhiều concurrent buffers/pending queues | Heap/direct memory, body size, backpressure |
| Mono.just(...) gây delay ngay trong controller | Argument của just được tính eager | Mono.defer/fromCallable + scheduler phù hợp |
| Reactor .block() ném IllegalStateException | Chạy trên non-blocking thread | Dùng composition/flatMap/WebClient |

### 16.1. Quy trình điều tra 5 bước

1. Xác nhận app đang **reactive hay Servlet** (dependencies, logs, WebApplicationType, handler/filter types).
2. Xác nhận request có qua **proxy/CDN** trước Netty/Tomcat không; kiểm tra protocol, TLS, idle timeout.
3. Tách inbound server connections khỏi outbound WebClient connection pool.
4. Tìm blocking calls và kiểm tra event-loop lag, queues, downstream latency.
5. Đo response size, codec buffering, cancellations và per-route p95/p99 trước khi thay cấu hình hoặc framework.

## 17. Lỗi phổ biến: phân biệt các lớp để tránh tối ưu nhầm

| Hiểu lầm | Sai ở đâu | Cách hiểu đúng |
| --- | --- | --- |
| “Một TCP connection = một thread Servlet” | Keep-alive và connectors không ghim thread mãi | Worker thường gắn với request processing |
| “WebFlux chỉ có một thread cho tất cả client” | Có nhiều event loops/schedulers | Một event loop phục vụ nhiều channels, count do runtime |
| “Netty là Spring WebFlux” | Netty chỉ là một transport/server runtime | WebFlux có framework pipeline và server adapters |
| “WebFlux bắt buộc Netty” | Có Tomcat/Jetty/Servlet adapters | Boot Netty chỉ là default |
| “Tomcat là blocking TCP nên không thể WebFlux” | Tomcat có async/non-blocking I/O | WebFlux có bridge non-blocking |
| “MVC async chính là WebFlux” | HTTP contract/execution khác | Servlet async vẫn qua DispatcherServlet |
| “Mono tự có thread riêng” | Mono mô tả publisher | Scheduler/source quyết định execution |
| “Mono.fromCallable đã giải quyết blocking” | Lazy chứ không tự offload | BoundedElastic hoặc non-blocking driver |
| “Thread.sleep trong Netty vô hại” | Starves other channels on same loop | Không block loop |
| “Cứ trả Flux là response stream ngay” | JSON codec có thể aggregate | Chọn SSE/NDJSON đúng media type |
| “Flux buffer vô hạn vẫn có backpressure” | Buffer có thể ăn hết heap | Bounded queues / rate limits |
| “HTTP/2 nghĩa chỉ có một request trên connection” | HTTP/2 multiplexes streams | Connection và request tách biệt |
| “Inbound Netty pool là WebClient pool” | Hai vai trò HTTP server/client khác nhau | Quản lý riêng |
| “ConnectionProvider default luôn bằng 500” | Defaults phụ thuộc Reactor Netty version | Kiểm tra effective config/metrics |
| “HTTP timeout tự hủy mọi remote side effect” | Remote server có thể đã thực hiện | Idempotency và deadlines |
| “Cancellation luôn có onComplete” | Cancel khác completion | doFinally/usingWhen |
| “Bật HTTP/2 chỉ cần một property” | Còn TLS/ALPN/server/proxy/client | Verify negotiated protocol |
| “Có ít threads thì ít memory” | Buffers/channels vẫn dùng memory | Heap, direct memory, fd budgets |
| “WebFlux bảo mật tự động” | Vẫn cần auth/authz/CSRF/CORS | SecurityWebFilterChain |
| “CORS headers là thông tin xác thực” | CORS là browser policy | Authentication từ credentials |
| “SSE đã gửi event vẫn có thể đổi status 500” | Response đã committed | Protocol error event/close stream |
| “Phải tạo WebClient mới mỗi request” | Mất connection reuse, tăng resource | Reuse managed client |
| “Thêm MVC starter để dùng annotation” | Boot có thể chọn Servlet stack | Chỉ WebFlux starter cho reactive app |
| “Một benchmark nhỏ chứng minh reactive tốt hơn mọi tình huống” | Không xét workload/config | Load test và đo bottlenecks |
| “Mọi library trong WebFlux đều non-blocking” | JDBC/JPA/file APIs có thể block | Xem từng downstream dependency |

## 18. Checklist áp dụng vào hệ thống thực

Trước khi đưa WebFlux/Netty vào production, xác nhận:

1. **Application type**: đúng Spring Boot reactive web app, không vô ý trộn MVC.
2. **Runtime**: Reactor Netty/Tomcat/Jetty được chọn minh bạch theo dependencies và requirements.
3. **Inbound limits**: max connections/idle timeouts/request body limits, HTTP2 streams và proxy budgets.
4. **Outbound pools**: max active/idle/pending, acquire/connect/response timeout, ownership và disposal.
5. **Downstream APIs**: WebClient, R2DBC, reactive messaging hoặc bridge blocking có giới hạn.
6. **Event loops**: tránh `block()`, `Thread.sleep`, sync DB/HTTP trực tiếp trong handler.
7. **Bounded resources**: queues, buffers, SSE clients, infinite Flux, concurrency/bulkheads.
8. **Backpressure**: kiểm tra publisher producer thật sự tôn trọng demand/cancellation.
9. **HTTP semantics**: 204, 404, error trước và sau response commit, JSON vs SSE.
10. **Auth/security**: SecurityWebFilterChain, permissions/ownership, CORS, CSRF, TLS.
11. **Reactive Context**: trace/principal propagation đúng qua thread changes.
12. **Observability**: inbound requests/connections, outbound pools, event-loop lag, direct memory, GC.
13. **Graceful shutdown**: app/sidecar/LB drain + long-lived streams và Kubernetes termination grace.
14. **Testing**: WebTestClient unit/integration, real HTTP/2/proxy, load tests và client disconnect.
15. **Decision rationale**: có lý do dựa vào concurrent I/O/streaming hay chỉ đổi style cho đẹp?

## 19. Câu hỏi tự kiểm tra

**1.** “Một connection luôn dùng một worker thread” có chính xác không?  
**Đáp án:** Không; transport/socket readiness và request workers không tương ứng 1:1. Servlet worker thường gắn với xử lý request, không cố định suốt TCP keep-alive.

**2.** HTTP/2 có thể mang bao nhiêu requests trên một connection?  
**Đáp án:** Nhiều logical streams theo protocol/settings; không chỉ một request.

**3.** Netty Channel là gì?  
**Đáp án:** Abstraction cho một I/O channel/endpoint với pipeline handlers, được quản lý bởi EventLoop.

**4.** Một EventLoop có thể phục vụ nhiều channels không?  
**Đáp án:** Có, đó là mô hình chủ đạo.

**5.** Spring Boot WebFlux mặc định dùng server gì?  
**Đáp án:** Reactor Netty nếu giữ default dependencies của spring-boot-starter-webflux.

**6.** WebFlux có thể chạy trên Tomcat không?  
**Đáp án:** Có, qua reactive/non-blocking HTTP adapter phù hợp trên Servlet container.

**7.** Vì sao MVC synchronous có thể dùng nhiều request workers?  
**Đáp án:** Blocking service calls có thể giữ worker trong lúc chờ.

**8.** MVC Callable có giúp Servlet thread rảnh không?  
**Đáp án:** Có, nhưng blocking logic vẫn giữ AsyncTaskExecutor worker.

**9.** Mono<String> được return có làm JDBC call non-blocking không?  
**Đáp án:** Không. Downstream API vẫn có thể block.

**10.** Vì sao Mono.just(blockingCall()) nguy hiểm?  
**Đáp án:** blockingCall được gọi ngay khi assembly, có thể chạy trên event loop.

**11.** BoundedElastic có biến JDBC thành non-blocking không?  
**Đáp án:** Không; chỉ offload blocking wait sang worker phù hợp hơn.

**12.** HttpHandler trong Spring WebFlux trả type nào?  
**Đáp án:** Mono<Void> biểu diễn completion của HTTP exchange.

**13.** ServerWebExchange thuộc mô hình nào?  
**Đáp án:** Spring WebFlux WebHandler abstraction.

**14.** DispatcherHandler và DispatcherServlet giống nhau hoàn toàn không?  
**Đáp án:** Không; WebFlux dùng DispatcherHandler/reactive contracts, MVC dùng DispatcherServlet/Servlet API.

**15.** Flux<T> luôn flush từng item qua HTTP không?  
**Đáp án:** Không; phụ thuộc HTTP codec/media type. SSE/NDJSON có streaming semantics phù hợp.

**16.** Backpressure Reactive Streams giống TCP flow control một-một không?  
**Đáp án:** Không; tương tác nhưng hoạt động ở các layers khác nhau.

**17.** Inbound server connection pool và WebClient pool là một không?  
**Đáp án:** Không; server accept inbound, HttpClient quản lý outbound connections.

**18.** pendingAcquireTimeout trong WebClient nói về gì?  
**Đáp án:** Thời gian chờ lấy connection từ outbound pool.

**19.** HTTP client timeout có chắc remote transaction rollback không?  
**Đáp án:** Không; remote có thể đã thực hiện side effect.

**20.** Vì sao WebFlux với Tomcat vẫn reactive?  
**Đáp án:** WebFlux programming contract được adapt lên Servlet non-blocking I/O; runtime không tự đổi app thành MVC.

**21.** Bật WebFlux và MVC starters cùng lúc luôn chọn reactive không?  
**Đáp án:** Không; Spring Boot thường ưu tiên MVC nếu cả hai có mặt.

**22.** Khi nhiều SSE clients, chỉ cần vài threads là mọi tài nguyên miễn phí không?  
**Đáp án:** Không; connections, buffers, keep-alive state, fd và subscriber memory vẫn có giá.

**23.** Response đã phát SSE event rồi lỗi, có thể chuyển HTTP 200 thành 500 không?  
**Đáp án:** Thường không sau commit; phải có error event/close protocol.

**24.** Vì sao phải reuse WebClient?  
**Đáp án:** Tận dụng pools/resources, giảm kết nối/TLS setup và resource pressure.

**25.** Khi nào dùng WebFlux hợp lý?  
**Đáp án:** Khi stack có nhu cầu concurrent I/O/streaming và downstream dependencies reactive, được đo bằng workload thực chứ không chỉ trông vào tên framework.

## 20. Tài liệu chính thức

- [Spring Framework 6.2 — Reactive Core](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html): HttpHandler, WebHandler, server adapters, non-blocking I/O.
- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/new-framework.html): khái niệm reactive, contrast với MVC, servers.
- [Spring Framework — WebFlux DispatcherHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html): mappings/adapters/result handlers.
- [Spring Boot 3.3 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html): Reactor Netty default và lựa chọn Tomcat/Jetty/Undertow, HTTP/2.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): Boot WebFlux auto-configuration.
- [Reactor Netty Reference — HTTP Server](https://projectreactor.io/docs/netty/1.1.26/reference/http-server.html): server, protocol configuration, observability.
- [Reactor Netty Reference — HTTP Client](https://projectreactor.io/docs/netty/1.1.26/reference/http-client.html): connection pooling, timeout, reuse.
- [Project Reactor — Reference Guide](https://projectreactor.io/docs/core/release/reference/): Mono/Flux, threading, backpressure và schedulers.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html): reactive HTTP tests.
- [Reactive Streams Specification](https://www.reactive-streams.org/): Publisher/Subscriber/Subscription và demand.
- [RFC 9113 — HTTP/2](https://www.rfc-editor.org/rfc/rfc9113): multiplexing streams trên một connection.

**Quy tắc cuối cùng:** **Reactive server không đồng nghĩa “một connection một thread”, cũng không đồng nghĩa “không còn threads”.** Reactor Netty cho event loops phục vụ nhiều connections; WebFlux dùng contract Publisher/non-blocking I/O để không giữ worker chỉ vì đang chờ. Servlet containers cũng có NIO và Servlet async, nhưng Spring MVC có programming model khác. Để WebFlux thực sự hiệu quả, bạn phải làm rõ **inbound/outbound connections, blocking boundaries, event-loop scheduling, backpressure, timeout và resource lifecycle**, rồi đo workload thật.


</details>

- [Quay lại đầu trang](#back-to-top)
