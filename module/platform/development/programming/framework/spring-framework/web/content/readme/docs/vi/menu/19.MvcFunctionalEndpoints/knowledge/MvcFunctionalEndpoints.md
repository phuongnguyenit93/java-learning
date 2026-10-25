<a id="back-to-top"></a>

# Functional MVC

## Menu
- [WebMvc.fn và khác biệt với controller dùng annotation](#mvc-functional-endpoints-001)
- [ServerRequest, ServerResponse và HandlerFunction](#mvc-functional-endpoints-002)
- [RouterFunction, RequestPredicate và khai báo tuyến lồng nhau](#mvc-functional-endpoints-003)
- [Đọc đầu vào, tạo phản hồi và sử dụng message converter](#mvc-functional-endpoints-004)
- [HandlerFilterFunction, xử lý lỗi và tổ chức các tuyến chức năng](#mvc-functional-endpoints-005)
- [Kết hợp functional endpoint với hạ tầng Spring MVC hiện có](#mvc-functional-endpoints-006)

## <a id="mvc-functional-endpoints-001">WebMvc.fn và khác biệt với controller dùng annotation</a>

<details>
<summary>Xem chi tiết</summary>

# WebMvc.fn và khác biệt với controller dùng annotation trong Spring MVC

## 1. WebMvc.fn giải quyết bài toán gì?

Trong Spring Web MVC, cách viết endpoint phổ biến là đặt `@RestController`, `@GetMapping`, `@PostMapping` lên class/method. Spring sẽ đọc metadata annotation và quyết định handler thích hợp. **WebMvc.fn** là một **mô hình lập trình functional** thay thế: ta mô tả các quy tắc route bằng `RouterFunction`, rồi đưa request vào `HandlerFunction` để nhận `ServerResponse`.

Hai mô hình **chạy trên cùng hệ sinh thái Servlet/MVC**, không phải lựa chọn giữa thread blocking và reactive server:

- **Annotated MVC:** ánh xạ request với controller method bằng annotation và các argument/return-value resolvers.
- **WebMvc.fn:** ánh xạ request bằng các hàm predicate/router và xử lý qua interfaces `ServerRequest`/`ServerResponse`.
- **Cùng hạ tầng:** `DispatcherServlet`, Servlet container, HTTP message converters, filters, Spring IoC và phần lớn cấu hình MVC.
- **Không đồng nhất với WebFlux.fn:** các class WebFlux nằm trong `org.springframework.web.reactive.function.server` và dùng `Mono`/`Flux`; MVC dùng `org.springframework.web.servlet.function` và các body APIs theo Servlet stack.

Bài này trình bày **Spring Framework 6.1.x**, **Spring Boot 3.3.5**, **Java 17**. Mục đích là hiểu kiến trúc và chọn phong cách phù hợp, không tuyên bố functional MVC nhanh hơn controller annotation hay tự động tạo non-blocking I/O.

### Kết quả học tập

Sau bài học, bạn có thể:

1. Phân biệt `RouterFunction`, `RequestPredicate`, `HandlerFunction`, `ServerRequest` và `ServerResponse`.
2. Xây routes GET/POST có path variables, query parameters và JSON body.
3. Dùng `route()`, `path()`, `nest()`, `and(...)` và route order chính xác.
4. Chọn status, content type, body và `Location` bằng `ServerResponse`.
5. Thực hiện validation/exception handling khi không có `@Valid @RequestBody` trên tham số controller.
6. Viết functional filters bằng `before`, `after` và `filter`, biết phạm vi áp dụng.
7. So sánh cùng một API được triển khai bằng WebMvc.fn và annotated controllers.
8. Chạy ví dụ Maven hoàn chỉnh, gọi curl và test bằng MockMvc.
9. Nhận diện security, CORS, request binding, OpenAPI, khả năng bảo trì và các lỗi migration.
10. Hiểu vì sao WebMvc.fn **vẫn có thể blocking** và khác WebFlux.fn.

## 2. Nền tảng: HTTP request đi qua Spring MVC như thế nào?

Với request:

```http
GET /fn/books/42 HTTP/1.1
Host: localhost:8080
Accept: application/json
```

Annotated controller có thể được khai báo:

```java
@RestController
@RequestMapping("/ann/books")
public class AnnotatedBooks {
    @GetMapping("/{id}")
    public Book get(@PathVariable long id) {
        return service.find(id);
    }
}
```

Đây là **snippet mô tả**; class `Book` và `service` chưa định nghĩa. Spring xem annotation mapping, chuyển `"42"` thành `long`, gọi method và serialize body thành JSON.

Functional MVC tương đương về mục tiêu:

```java
RouterFunction<ServerResponse> routes = route()
        .GET("/fn/books/{id}", request -> {
            long id = Long.parseLong(request.pathVariable("id"));
            Book book = service.find(id);
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(book);
        })
        .build();
```

Đây cũng là **snippet**: `service` chưa định nghĩa và thiếu imports. Điểm quan trọng là route + handler nằm trong các giá trị/function Java, không phải annotation metadata.

### Đường đi của một request WebMvc.fn

```text
Client HTTP request
         |
         v
Servlet Filter chain / Spring Security
         |
         v
DispatcherServlet
         |
         v
RouterFunctionMapping
  - thử các RouterFunction
  - đánh giá RequestPredicate
  - chọn HandlerFunction đầu tiên phù hợp
         |
         v
HandlerFunctionAdapter
  - gọi handler.handle(ServerRequest)
         |
         v
ServerResponse.writeTo(...)
  - HTTP status / headers / body conversion
         |
         v
Servlet response
```

Đây là sơ đồ giản lược. WebMvc.fn dùng các MVC components tương ứng để tích hợp với `DispatcherServlet`. Với annotation, `RequestMappingHandlerMapping` và `RequestMappingHandlerAdapter` đảm nhiệm bước mapping/invocation. **Cùng DispatcherServlet không có nghĩa cùng mọi argument resolver/interceptor behavior.**

Khi đăng ký `RouterFunction<ServerResponse>` dưới dạng bean trong Spring context, MVC tự phát hiện router và lắp vào hạ tầng mapping. Với Spring Boot web starter, ứng dụng MVC cơ bản **không cần thêm WebFlux dependency** và thường không cần `@EnableWebMvc`. Việc tự đặt `@EnableWebMvc` trong Boot có thể thay đổi các auto-configurations nên không thêm tùy tiện.

## 3. Năm thành phần cốt lõi của WebMvc.fn

### 3.1. RequestPredicate — một quy tắc match

`org.springframework.web.servlet.function.RequestPredicate` quyết định một request có phù hợp hay không. `RequestPredicates` cung cấp predicates theo HTTP method, path, headers, Accept, Content-Type.

Ví dụ:

```java
RequestPredicate rule = GET("/fn/books/{id}")
        .and(accept(MediaType.APPLICATION_JSON));
```

Đây là snippet với static imports `GET` và `accept` từ `RequestPredicates`. Nếu request thỏa cả GET/path và Accept, rule có thể chọn handler. `and`, `or` và `negate` cho phép kết hợp predicates.

**Chú ý:** Predicate `accept(APPLICATION_JSON)` kiểm tra header `Accept`, tức kiểu client muốn **nhận**. Predicate `contentType(APPLICATION_JSON)` kiểm tra `Content-Type`, tức kiểu body client **gửi**. Không tráo hai headers.

### 3.2. HandlerFunction — công việc xử lý request

`HandlerFunction<T extends ServerResponse>` là functional interface. Về ý nghĩa:

```java
ServerResponse handle(ServerRequest request) throws Exception;
```

Chữ ký trên chỉ để nhớ khái niệm; exception declaration cụ thể phụ thuộc API, hãy xem Javadoc 6.1. Handler có thể là lambda hoặc method reference:

```java
HandlerFunction<ServerResponse> hello =
        request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Hello MVC");
```

Một controller annotation method có nhiều tham số Spring tự resolve; functional handler thường nhận **một `ServerRequest`** rồi chủ động đọc path/query/header/body.

### 3.3. ServerRequest — đọc dữ liệu đầu vào

`org.springframework.web.servlet.function.ServerRequest` cung cấp:

| API | Tác dụng |
| --- | --- |
| `request.pathVariable("id")` | Giá trị path placeholder |
| `request.pathVariables()` | Map các path variables |
| `request.param("q")` | `Optional<String>` của query/form param |
| `request.params()` | `MultiValueMap<String,String>` các parameters |
| `request.headers()` | `ServerRequest.Headers`, gồm Accept/Content-Type/custom headers |
| `request.body(MyDto.class)` | Giải mã body bằng `HttpMessageConverter`, có thể throw |
| `request.body(new ParameterizedTypeReference<List<MyDto>>() {})` | Đọc generic collection |
| `request.servletRequest()` | Truy cập Servlet request khi thực sự cần |
| `request.principal()` | Access authenticated principal nếu có |

Ví dụ:

```java
long id = Long.parseLong(request.pathVariable("id"));
String q = request.param("q").orElse("");
CreateBookRequest dto = request.body(CreateBookRequest.class);
```

Đây là snippet cần `request` thuộc một functional handler. Trên annotated controller, các phần này có thể là `@PathVariable long id`, `@RequestParam(defaultValue="") String q` và `@RequestBody CreateBookRequest dto`.

Functional không tự có **magic** với việc parse `long`: `pathVariable()` trả String; bạn cần parse và xử lý `NumberFormatException` hay chọn policy lỗi rõ ràng. Với body JSON, `request.body(...)` dùng converter đã đăng ký và có thể báo lỗi khi JSON sai, thiếu body hoặc media type không được hỗ trợ.

### 3.4. ServerResponse — khai báo HTTP response

`org.springframework.web.servlet.function.ServerResponse` cung cấp builder-style APIs:

```java
ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(book);

ServerResponse.created(URI.create("/fn/books/42"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(book);

ServerResponse.noContent().build();

ServerResponse.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ApiError("invalid_request"));
```

Đây là **snippets** có biến `book`/class `ApiError` từ ứng dụng giả định. Không dùng `ServerResponse.bodyValue(...)` của WebFlux.fn trong MVC: API Servlet `ServerResponse.BodyBuilder` dùng `body(...)` cho object thông thường.

Body converter/serialization của Spring được dùng khi ghi response. Dùng `ServerResponse.created(URI)` tạo HTTP 201 và header `Location` tương ứng, chứ không cần tự viết `status=201` trong JSON body.

### 3.5. RouterFunction — từ request tới handler

`RouterFunction<T>` khi match request sẽ trả optional handler function. Thực tế thường dùng builder:

```java
RouterFunction<ServerResponse> api = RouterFunctions.route()
        .GET("/fn/hello", request ->
                ServerResponse.ok().body("Hello"))
        .GET("/fn/status", request ->
                ServerResponse.ok().body("ready"))
        .build();
```

Builder có thể tạo một router từ nhiều rules, thêm nested routes và filters. **Order của functional routes là một phần của behavior** — route đầu tiên match có thể thắng.

## 4. Khác biệt cốt lõi: route order

Trong annotated MVC, `RequestMappingHandlerMapping` có logic so sánh độ cụ thể của mapping. Ví dụ `/books/latest` thường được chọn thay `/books/{id}` nhờ độ cụ thể, dù hai methods được viết trong file theo thứ tự nào.

Trong WebMvc.fn, **routes được thử theo thứ tự**. Vì vậy:

```java
// CẦN CẨN THẬN: /books/{id} đặt trước /books/latest
RouterFunction<ServerResponse> wrong = route()
        .GET("/fn/books/{id}", handler::getBook)
        .GET("/fn/books/latest", handler::latest)
        .build();
```

Request `GET /fn/books/latest` có thể match placeholder `{id}`, đưa string `"latest"` tới handler lấy ID. Kết quả có thể là lỗi parsing thay vì endpoint latest.

Đặt specific route lên trước:

```java
RouterFunction<ServerResponse> correct = route()
        .GET("/fn/books/latest", handler::latest)
        .GET("/fn/books/{id}", handler::getBook)
        .build();
```

Hai ví dụ là snippets với `handler` giả định. **Đừng chỉ sắp xếp theo alphabet**. Cần có test để bảo vệ thứ tự những route có thể trùng match.

Khi có **nhiều RouterFunction beans**, Spring MVC phát hiện/sắp xếp/chaining theo infrastructure. Thứ tự có thể chịu ảnh hưởng bean ordering/configuration. Vì vậy với các routes có xung đột, đừng mặc định thứ tự file hay tên bean; ưu tiên group rõ ràng, dùng `@Order`/ordering policy phù hợp và kiểm thử route collisions.

Khi dùng cả annotations và functional endpoints ở chung một DispatcherServlet, mỗi phong cách có mapping component riêng; không suy luận độ ưu tiên cross-style chỉ từ thứ tự khai báo Java. Kiểm tra behavior của phiên bản Spring/framework config nếu hai phong cách cùng định nghĩa chính xác một URL + method.

## 5. Nested routes và composition

### 5.1. Nhóm bằng path()

Nếu tất cả endpoint dùng chung prefix `/fn/books`:

```java
RouterFunction<ServerResponse> routes = route()
        .path("/fn/books", books -> books
                .GET("/latest", handler::latest)
                .GET("/{id}", handler::getBook)
                .GET(handler::listBooks)
                .POST(handler::createBook))
        .build();
```

Đây là snippet với handler class giả định. `.path("/fn/books", builder -> ...)` không chỉ là nối chuỗi: nó tạo nested route group và áp prefix/predicate của nhóm. Method `GET(handler::listBooks)` trong nested builder match root route của group. Ưu tiên đường cụ thể trước đường biến.

### 5.2. Nest theo predicate

Ví dụ một nhóm GET routes cùng muốn JSON:

```java
RouterFunction<ServerResponse> routes = route()
        .path("/fn/books", books -> books
                .nest(accept(MediaType.APPLICATION_JSON), json -> json
                        .GET("/latest", handler::latest)
                        .GET("/{id}", handler::getBook)
                        .GET(handler::listBooks))
                .POST(handler::createBook))
        .build();
```

Đây là snippet. `accept(APPLICATION_JSON)` giới hạn **nhóm GET**, không giới hạn POST ở bên ngoài `nest`. Nếu client gửi `Accept: text/html`, route GET có thể không match, tạo behavior khác hẳn việc handler chạy rồi serializer chọn format; vì vậy response **404/406** cho unmatched predicates không nên được giả định giống controller annotation `produces`. Test cụ thể error contract.

### 5.3. Composition bằng and()/andRoute()

Khi có hai route groups độc lập:

```java
RouterFunction<ServerResponse> all =
        catalogRoutes.and(searchRoutes);
```

Hoặc builder `route().add(catalogRoutes).add(searchRoutes).build()`. Đó là việc **nối hành vi routing**, không phải copy annotations. Thứ tự thường quan trọng nếu rules có thể cùng match. Nên chia theo domain/module để có cấu trúc kiểm thử được.

### 5.4. RouterFunction là Spring bean, không phải controller annotation

Một bean:

```java
@Configuration
class Routes {
    @Bean
    RouterFunction<ServerResponse> router(BookHandler handler) {
        return route()
                .GET("/fn/books", handler::listBooks)
                .build();
    }
}
```

Đây là snippet với `BookHandler`, `route()` static import. Spring MVC tự nhận diện bean router. Không cần gắn `@RestController` lên handler function; handler là một Spring bean/service thuần hoặc lambda, còn Spring IoC vẫn injection như bình thường.

## 6. Filters của WebMvc.fn: before, after và filter

Functional routes có khả năng gắn filters ở tầng router. Cần phân biệt chúng với **Servlet Filter** và **Spring Security filter chain**.

### 6.1. before: biến đổi request được truyền cho handler

```java
RouterFunction<ServerResponse> router = route()
        .GET("/fn/hello", handler::hello)
        .before(request -> ServerRequest.from(request)
                .header("X-Internal-Marker", "functional")
                .build())
        .build();
```

Đây là snippet với `handler` đã tồn tại. `before` có thể tạo một `ServerRequest` wrapper mới để route handlers nhìn thấy request metadata đã được bổ sung. Không lấy nó làm giải pháp xác thực: client vẫn có thể tự gửi headers; auth phải dựa trên Spring Security/credentials và quy tắc trust.

### 6.2. after: quan sát hoặc thay đổi response

`after` nhận `(request, response)` rồi trả một `ServerResponse`. Ví dụ:

```java
RouterFunction<ServerResponse> router = route()
        .GET("/fn/hello", handler::hello)
        .after((request, response) -> {
            auditService.record(
                    request.path(), response.statusCode());
            return response;
        })
        .build();
```

Đây là snippet với `auditService` giả định. Nếu cần gắn header vào response, có thể dùng response builder/copy API theo Javadoc và kiểm thử sự tương thích với body/commit status. Do response contract bất biến, đừng thử sửa properties trực tiếp theo cách mutable annotated `HttpServletResponse` nếu code không quản lý lifecycle.

### 6.3. filter: bao quanh handler và có thể short-circuit

`HandlerFilterFunction` nhận request và `next` handler. Một filter có thể gọi `next.handle(request)` hoặc chặn:

```java
RouterFunction<ServerResponse> router = route()
        .GET("/fn/hello", handler::hello)
        .filter((request, next) -> {
            if ("true".equals(
                    request.headers().firstHeader("X-Demo-Block"))) {
                return ServerResponse.status(HttpStatus.FORBIDDEN)
                        .body("blocked");
            }
            return next.handle(request);
        })
        .build();
```

Đây là snippet chỉ dùng **header giả lập để học short-circuit**, tuyệt đối **không sử dụng như kiểm tra authorization thực tế**. Một client có thể gửi hoặc không gửi header đó theo ý họ. Trong demo đầy đủ, filter này sẽ giúp test chứng minh functional filter chỉ áp dụng cho functional route group.

### 6.4. Nested filter scope

Filters đặt ở root builder ảnh hưởng các routes do builder ấy tạo; filters ở nested builder chỉ áp dụng cho nested routes, không tự lan ra các route sibling/parent bên ngoài phạm vi. Điều này khác Servlet Filter theo URL mappings.

**Không nhầm ba tầng:**

| Tầng | Điểm can thiệp | Dùng khi nào |
| --- | --- | --- |
| Servlet `Filter` | Trước DispatcherServlet; có thể áp dụng mọi endpoint, static routes và annotations theo mapping | Security filter chain, correlation ID, servlet-level concerns |
| `HandlerInterceptor` / `WebMvcConfigurer` | MVC handler lifecycle, theo mapping và execution flow | MVC cross-cutting behavior; kiểm tra tương thích functional dispatch |
| WebMvc.fn `HandlerFilterFunction` | Bao quanh functional `HandlerFunction` đã được route match | Per-router logging, response adaptations, routing policies |

Đặc biệt **Spring Security** nên được cấu hình tại security filter chain/path policies; đừng thay bằng WebMvc.fn header-check filter hoặc tin filter thứ tự tùy ý. Các exceptions từ functional handlers cũng cần được xử lý phù hợp trong MVC/global/error path — không suy luận rằng mọi `@ControllerAdvice`/`@ExceptionHandler` trong annotated controllers sẽ xử lý chính xác giống nhau cho functional endpoint; có thể dùng router filters hoặc error handling được kiểm thử riêng.

## 7. JSON body, validation và exception handling

### 7.1. Với annotated controller

```java
@PostMapping("/ann/books")
public ResponseEntity<Book> create(
        @Valid @RequestBody NewBook input) {
    Book book = service.create(input);
    return ResponseEntity
            .created(URI.create("/ann/books/" + book.id()))
            .body(book);
}
```

Đây là snippet với `NewBook`/`service` chưa định nghĩa. Spring MVC tự resolve `@RequestBody` bằng `HttpMessageConverter`, `@Valid` kích hoạt Bean Validation khi có validator, và nếu không có `BindingResult` để nhận lỗi thì có thể phát sinh `MethodArgumentNotValidException`. Pipeline annotated tự tích hợp khá nhiều annotation metadata.

### 7.2. Với WebMvc.fn

```java
public ServerResponse createBook(ServerRequest request)
        throws Exception {
    NewBook input = request.body(NewBook.class);

    var errors = validator.validate(input);
    if (!errors.isEmpty()) {
        return ServerResponse.badRequest()
                .body(new ApiError("invalid_title"));
    }
    Book book = service.create(input);
    return ServerResponse.created(
            URI.create("/fn/books/" + book.id()))
            .body(book);
}
```

Đây là snippet với `validator`/`service` được inject và các record classes ở mục 10. `ServerRequest.body(NewBook.class)` dùng converter để đọc JSON; annotation `@NotBlank` trên DTO vẫn có thể được validator kiểm tra, nhưng **phải chủ động gọi `validator.validate(input)`** trong functional handler hoặc thiết kế validation binder/filter phù hợp. Đừng kỳ vọng khai báo `@Valid` trên một tham số method `HandlerFunction.handle(ServerRequest)` tự nhiên hoạt động như method argument resolution của annotated controller.

### 7.3. Lỗi JSON malformed không giống lỗi validation

- **Malformed JSON:** converter không deserialize được, có thể ném exception trước `validator.validate`.
- **Invalid DTO field:** parse JSON xong rồi Bean Validation phát hiện constraint.
- **Missing path ID/non-numeric ID:** functional handler parse `request.pathVariable`; cần quy định 400 hoặc 404.
- **Unsupported media type:** `contentType` predicate có thể làm route không match, vì vậy **không mặc định** response sẽ giống annotated `@PostMapping(consumes=...)`.
- **Unauthorized:** auth/filter chain cần kiểm tra quyền, không chỉ validation/serialization.

Nếu API bắt buộc mọi lỗi trả RFC 9457 `ProblemDetail` hoặc JSON error schema nhất quán, hãy xây các handlers/advice/filters đủ coverage và test từng path; không hứa rằng chuyển đổi từ annotated sang functional giữ nguyên tất cả exception mappings.

## 8. WebMvc.fn có đồng nghĩa non-blocking hoặc reactive không?

**Không.** Lớp Servlet `ServerRequest` có method `body(Class)` đọc body thông qua MVC converters; handler có thể gọi JDBC/JPA hoặc REST client đồng bộ. Chỉ vì cú pháp route là lambda không làm I/O trở thành asynchronous:

```java
HandlerFunction<ServerResponse> slow = request -> {
    Book book = blockingRepository.findById(42);
    return ServerResponse.ok().body(book);
};
```

Đây là snippet minh họa; `blockingRepository` chưa định nghĩa. Trong lúc `findById` chờ, Servlet thread có thể bị giữ như controller đồng bộ thông thường. Functional **WebMvc.fn** vẫn chạy trên Servlet MVC.

So với **WebFlux.fn**:

| Đặc điểm | WebMvc.fn | WebFlux.fn |
| --- | --- | --- |
| Package | `org.springframework.web.servlet.function` | `org.springframework.web.reactive.function.server` |
| Request | Servlet `ServerRequest` | Reactive `ServerRequest` |
| Response | Servlet `ServerResponse` (object) | Reactive `Mono<ServerResponse>` |
| Body object | `ServerResponse.ok().body(book)` | `ServerResponse.ok().bodyValue(book)` / publishers |
| Routing | Servlet `RouterFunction` | Reactive `RouterFunction` |
| Runtime | DispatcherServlet / Servlet stack | DispatcherHandler / reactive stack |
| I/O behavior | Synchronous/blocking code có thể chạy trực tiếp | Thiết kế reactive, nhưng dependency blocking vẫn có thể chặn event loop |
| Testing | MockMvc | WebTestClient và reactive testing tools |
| Nhu cầu | Functional style với Spring MVC hiện có | End-to-end reactive pipelines nếu I/O hỗ trợ |

**Đừng import nhầm classes theo tên ngắn giống nhau.** Cùng có `ServerRequest`, `ServerResponse` và `RouterFunction` nhưng package/subtype/phương thức khác. Với Spring Boot app đã có `spring-boot-starter-web`, dùng WebMvc.fn mà không thêm `spring-boot-starter-webflux` chỉ để lấy router APIs.

## 9. Khi nào chọn functional route, khi nào giữ annotated controller?

| Tiêu chí | Annotated controllers | WebMvc.fn |
| --- | --- | --- |
| Cách routing | `@GetMapping`, `@PostMapping` trên methods | `route().GET(...).POST(...)` DSL |
| Binding của path/query | `@PathVariable` / `@RequestParam` auto-resolve | `request.pathVariable` / `request.param` chủ động |
| JSON request | `@RequestBody Dto` | `request.body(Dto.class)` |
| Bean Validation | `@Valid` và MVC argument resolution | Validator/binding explicit theo thiết kế |
| HTTP response | `ResponseEntity`, DTO, view name | `ServerResponse` builder |
| Path grouping | `@RequestMapping` ở type-level | `path()`/`nest()` |
| Route precedence | Mapping specificity comparison | **Thứ tự khai báo routes** |
| Cross-cutting | Servlet filters, interceptors, advice | Servlet filters + router before/after/filter; error policy explicit |
| API docs generation | Nhiều OpenAPI tools hiểu annotation tốt hơn | Có thể cần tooling/integration riêng, tùy version |
| Kotlin DSL / Java lambdas | Có thể dùng annotations trên Kotlin/Java classes | Functional composition thuận tiện, Kotlin router DSL |
| Cùng project | Có | Có, test overlapping paths |
| Non-blocking | Không tự động | **Không tự động** |

### Những lúc WebMvc.fn hữu ích

- Xây một module API nhỏ, ít handler, muốn route tree rõ ràng và functional composition.
- Viết API gateways/facades với nhiều predicates tùy biến cần kiểm thử đường routing chi tiết.
- Muốn giữ business handlers thành Spring beans thuần và gom routing trong một configuration class.
- Hệ thống dùng DSL/functions mạnh, thích tách route table khỏi logic.
- Tái sử dụng functional filters theo nhóm endpoints.

### Những lúc annotation thường phù hợp hơn

- Dự án controller-based lớn đã có conventions, validations, `@ControllerAdvice`, annotations và OpenAPI tooling.
- Nhiều DTOs, form validation, binding/cookies/multipart/model attributes và cần tự động mapping method arguments.
- Đội nhóm cần code dễ đọc với `@GetMapping`, `@PreAuthorize`, `@Valid` và các IDE tooling quen thuộc.
- Migration sang functional sẽ làm tăng mã parse/validation/error handling thủ công nhưng không mang lợi ích rõ rệt.

**Không có yêu cầu phải chuyển toàn bộ ứng dụng.** Hai mô hình có thể tồn tại song song, chỉ cần giữ mapping paths không đè nhau và kiểm thử security/exception/CORS behavior.

## 10. Project runnable: cùng một API bằng WebMvc.fn và @RestController

Ta tạo một ứng dụng Spring Boot có:
- Functional endpoints dưới `/fn/books` và `/fn/search`.
- Annotated endpoints tương đương dưới `/ann/books`.
- Một service lưu books trong RAM, có `AtomicLong` sinh ID, `ConcurrentHashMap` lưu entries.
- JSON request body `{"title":"Spring MVC"}` với Jakarta `@NotBlank`.
- Functional validation do handler chủ động gọi `jakarta.validation.Validator`.
- Annotated validation thông qua `@Valid @RequestBody`.
- Functional filter giả lập chặn request khi `X-Demo-Block: true`, chỉ để hiểu short-circuit.
- Một functional route `/fn/books/latest` đặt **trước** `/fn/books/{id}` để thể hiện route order.

Kết quả là ứng dụng có thể build/test với Java 17+ và Maven. Không có database, session hoặc authentication; các API chỉ phục vụ học tập.

### 10.1. Cây thư mục

```text
webmvc-fn-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/example/webfn/
    │       ├── WebFnApplication.java
    │       ├── Book.java
    │       ├── NewBook.java
    │       ├── ApiError.java
    │       ├── BookStore.java
    │       ├── BookHandler.java
    │       ├── FunctionalRoutes.java
    │       └── AnnotatedBookController.java
    └── test/
        └── java/example/webfn/
            └── WebFnTest.java
```

**`pom.xml`**

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
    <artifactId>webmvc-fn-demo</artifactId>
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
            <artifactId>spring-boot-starter-validation</artifactId>
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

**`src/main/java/example/webfn/WebFnApplication.java`**

```java
package example.webfn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebFnApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFnApplication.class, args);
    }
}
```

### 10.2. DTOs dùng chung

**`src/main/java/example/webfn/Book.java`**

```java
package example.webfn;

public record Book(long id, String title) {}
```

**`src/main/java/example/webfn/NewBook.java`**

```java
package example.webfn;

import jakarta.validation.constraints.NotBlank;

public record NewBook(@NotBlank String title) {}
```

**`src/main/java/example/webfn/ApiError.java`**

```java
package example.webfn;

public record ApiError(String code) {}
```

Java 17 `record` có constructors/accessors, Jackson hỗ trợ deserialize/serialize trong Boot 3.3. `@NotBlank` từ Jakarta Validation đảm bảo title không null/empty/whitespace khi **validator được gọi**. DTO record không tự validate chỉ vì có annotation; validation phải được kích hoạt ở pipeline tương ứng.

### 10.3. Service lưu dữ liệu, không phụ thuộc routing style

**`src/main/java/example/webfn/BookStore.java`**

```java
package example.webfn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class BookStore {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Book> books =
            new ConcurrentHashMap<>();

    public Book create(String title) {
        long id = sequence.incrementAndGet();
        Book book = new Book(id, title.strip());
        books.put(id, book);
        return book;
    }

    public Optional<Book> find(long id) {
        return Optional.ofNullable(books.get(id));
    }

    public List<Book> all() {
        List<Book> result = new ArrayList<>(books.values());
        result.sort(Comparator.comparingLong(Book::id));
        return result;
    }

    public Optional<Book> latest() {
        return all().stream()
                .max(Comparator.comparingLong(Book::id));
    }

    public List<Book> search(String query) {
        String term = query.strip().toLowerCase(
                java.util.Locale.ROOT);
        return all().stream()
                .filter(book -> book.title().toLowerCase(
                        java.util.Locale.ROOT).contains(term))
                .toList();
    }
}
```

`ConcurrentHashMap` và `AtomicLong` chỉ minh họa xử lý nhiều request trong RAM. `latest()` và `search()` quét dữ liệu để đơn giản, không phải thiết kế tối ưu cho hàng triệu records. App restart thì mất state; không có transaction/database.

### 10.4. Functional handler: ServerRequest → ServerResponse

**`src/main/java/example/webfn/BookHandler.java`**

```java
package example.webfn;

import java.net.URI;

import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class BookHandler {

    private final BookStore store;
    private final Validator validator;

    public BookHandler(BookStore store, Validator validator) {
        this.store = store;
        this.validator = validator;
    }

    public ServerResponse listBooks(ServerRequest request)
            throws Exception {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.all());
    }

    public ServerResponse getBook(ServerRequest request)
            throws Exception {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException invalid) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("bad_id"));
        }

        Book book = store.find(id).orElse(null);
        if (book == null) {
            return ServerResponse.notFound().build();
        }

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }

    public ServerResponse latest(ServerRequest request)
            throws Exception {
        Book book = store.latest().orElse(null);
        if (book == null) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }

    public ServerResponse search(ServerRequest request)
            throws Exception {
        String q = request.param("q").orElse("");

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.search(q));
    }

    public ServerResponse createBook(ServerRequest request)
            throws Exception {
        NewBook input = request.body(NewBook.class);

        if (input == null ||
                !validator.validate(input).isEmpty()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("invalid_title"));
        }

        Book book = store.create(input.title());
        URI location = URI.create("/fn/books/" + book.id());

        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }
}
```

**Điểm quan trọng:**

1. `@Component` tạo bean handler, không phải `@RestController`.
2. Không có `@GetMapping` trên class; URI routing nằm ở `FunctionalRoutes`.
3. `request.body(NewBook.class)` kích hoạt converter JSON cho part **toàn body HTTP** (không phải multipart), rồi `validator.validate(input)` chạy Bean Validation chủ động.
4. `request.pathVariable("id")` trả String. Handler tự parse và trả `400` nếu không phải số; khác annotated `@PathVariable long id` được MVC convert trước method invocation.
5. `ServerResponse.notFound().build()` tạo 404 không body; `ServerResponse.created(location)` tạo 201 và header Location.
6. `search` dùng `Optional` từ `request.param("q")` để đặt default rỗng; bản demo trả tất cả nếu query rỗng.
7. Các methods trả ServerResponse và có `throws Exception` vì `request.body(...)`/response building có các checked exceptions theo Servlet API.
8. Validation không thay authentication, authorization hoặc giới hạn input length/complexity. Với app thật, giới hạn title, request body size, số kết quả và kiểm tra quyền.
9. Store/handler dependency injection giống annotated controller; functional không bắt bạn viết toàn bộ logic trong lambda route.

### 10.5. RouterFunction bean, path precedence và filter

**`src/main/java/example/webfn/FunctionalRoutes.java`**

```java
package example.webfn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.contentType;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
public class FunctionalRoutes {

    @Bean
    RouterFunction<ServerResponse> bookRoutes(BookHandler handler) {
        return route()
                // Thứ tự quan trọng: cụ thể trước path variable.
                .GET("/fn/books/latest", handler::latest)
                .GET("/fn/books/{id}", handler::getBook)
                .GET("/fn/books", handler::listBooks)
                .GET("/fn/search", handler::search)
                .POST("/fn/books",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::createBook)
                .filter((request, next) -> {
                    // Demo filter, KHÔNG phải security control.
                    String blocked = request.headers()
                            .firstHeader("X-Demo-Block");
                    if ("true".equalsIgnoreCase(blocked)) {
                        return ServerResponse
                                .status(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ApiError("demo_blocked"));
                    }
                    return next.handle(request);
                })
                .build();
    }
}
```

Chỉ một `RouterFunction<ServerResponse>` bean trong ví dụ. Router sẽ được Spring MVC tự phát hiện; không cần viết `@RestController` cho functional routes.

**Route order được kiểm chứng:** `/fn/books/latest` đặt trước `/fn/books/{id}`. Nếu đảo thứ tự, literal `latest` có thể bị truyền vào `getBook` và nhận `bad_id` thay vì chọn latest.

**POST constraint:** Route `POST /fn/books` dùng predicate `contentType(APPLICATION_JSON)`. Body JSON được `ServerRequest.body(NewBook.class)` decode. Nếu Content-Type không khớp, route có thể không match trước khi handler chạy, và status thực tế có thể khác annotated `consumes`. Không tự hứa status 415 cho mọi trường hợp; API contract cần test riêng.

**Functional filter:** Nếu request gửi `X-Demo-Block: true`, router trả `403` trước khi handler chạy. Đây chỉ là marker để kiểm thử short-circuit và phạm vi filter, **không có giá trị bảo mật**. Đối với API thật, dùng Spring Security với authentication/authorization rules; không lấy header tùy ý do client kiểm soát làm quyền.

### 10.6. Annotated controller dùng chung BookStore

**`src/main/java/example/webfn/AnnotatedBookController.java`**

```java
package example.webfn;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ann/books")
public class AnnotatedBookController {

    private final BookStore store;

    public AnnotatedBookController(BookStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Book> list() {
        return store.all();
    }

    @GetMapping("/latest")
    public ResponseEntity<Book> latest() {
        return store.latest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> get(@PathVariable long id) {
        return store.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Book> create(
            @Valid @RequestBody NewBook input) {
        Book book = store.create(input.title());
        return ResponseEntity.created(
                URI.create("/ann/books/" + book.id()))
                .body(book);
    }
}
```

Điểm đối chiếu:

- `@RestController` + `@RequestMapping` gom các routes theo class.
- `@GetMapping("/{id}")` + `@PathVariable long id` tự convert String thành long; malformed ID được Spring argument resolution xử lý.
- `@Valid @RequestBody NewBook` tích hợp validation theo annotated method, không phải gọi validator trong body method.
- `ResponseEntity<Book>` có status/headers/body, còn functional dùng `ServerResponse`.
- Hai nhóm paths `/ann/books` và `/fn/books` **không trùng nhau**, nên coexist trong một application rõ ràng.
- Cùng `BookStore` chứng minh business logic không cần gắn với routing style. Nếu tạo qua `/ann/books`, có thể đọc qua `/fn/books/{id}` vì dùng cùng store.

### 10.7. Chạy project và xem HTTP response

Tại thư mục `webmvc-fn-demo` với Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Đây là hai lệnh tuần tự, không phải một lệnh chạy đồng thời. Sau khi Boot khởi động trên cổng mặc định `8080`:

**Tạo book qua functional route:**

```bash
curl -i -X POST \
  'http://localhost:8080/fn/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring MVC"}'
```

HTTP response lần tạo đầu trên server vừa khởi động:

```http
HTTP/1.1 201
Location: /fn/books/1
Content-Type: application/json

{"id":1,"title":"Spring MVC"}
```

`Location` là relative URI reference hợp lệ cho resource vừa tạo, dùng server-owned ID. Trong production, nếu client contract yêu cầu absolute URL, dùng origin/context path đáng tin (ví dụ `ServletUriComponentsBuilder` với forwarded-header trust policy), không tin raw Host từ Internet.

**Lấy resource:**

```bash
curl -i 'http://localhost:8080/fn/books/1'
```

Khi book ID 1 đã tồn tại, body:

```json
{"id":1,"title":"Spring MVC"}
```

**Danh sách:**

```bash
curl -i 'http://localhost:8080/fn/books'
```

Ví dụ:

```json
[{"id":1,"title":"Spring MVC"}]
```

**Tìm kiếm qua query param:**

```bash
curl -G -i 'http://localhost:8080/fn/search' \
  --data-urlencode 'q=Spring'
```

Dự kiến JSON array chứa book có chữ `Spring`, không phân biệt hoa/thường theo cách `BookStore.search` được viết.

**Annotated API tạo thêm book:**

```bash
curl -i -X POST \
  'http://localhost:8080/ann/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Annotated MVC"}'
```

Response lần tạo thứ hai dự kiến:

```http
HTTP/1.1 201
Location: /ann/books/2

{"id":2,"title":"Annotated MVC"}
```

Nếu trong cùng JVM đã chạy tests hoặc tạo thêm books trước đó, ID có thể khác. Điều cần kiểm chứng là status 201, `Location` trỏ đúng resource, body ID khớp.

**Latest functional route:**

```bash
curl -i 'http://localhost:8080/fn/books/latest'
```

Body chứa book có ID lớn nhất tại thời điểm xử lý. Nếu route `/{id}` được khai báo trước `/latest`, có nguy cơ handler nhận `"latest"` làm `id` và trả 400; test phải bắt điều đó.

**Validation lỗi:**

```bash
curl -i -X POST \
  'http://localhost:8080/fn/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'
```

Functional handler trả:

```http
HTTP/1.1 400
Content-Type: application/json

{"code":"invalid_title"}
```

Annotated endpoint với cùng body cũng có thể trả `400` từ Bean Validation pipeline, nhưng **error body/schema của Boot mặc định không nhất thiết giống** `{"code":"invalid_title"}`. Đừng viết client phụ thuộc vào exact body nếu chưa định nghĩa error contract chung.

**Short-circuit functional filter:**

```bash
curl -i 'http://localhost:8080/fn/books' \
  -H 'X-Demo-Block: true'
```

Dự kiến:

```http
HTTP/1.1 403
Content-Type: application/json

{"code":"demo_blocked"}
```

Header `X-Demo-Block` chỉ dành cho demonstration filter. Nếu gửi header đó tới `/ann/books`, functional filter này **không áp dụng** cho annotated route; security filter chain bên ngoài có quy tắc độc lập nếu được thêm về sau.

## 11. MockMvc tests: functional và annotated coexist

**`src/test/java/example/webfn/WebFnTest.java`**

```java
package example.webfn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebFnTest {

    @Autowired MockMvc mvc;

    @Test
    void functionalCreateAndRead() throws Exception {
        MvcResult created = mvc.perform(post("/fn/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Function Book\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Function Book"))
                .andReturn();

        String uri = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(uri);
        assertTrue(uri.matches("/fn/books/[1-9][0-9]*"));

        mvc.perform(get(uri))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Function Book"));
    }

    @Test
    void annotatedCreateAndRead() throws Exception {
        MvcResult created = mvc.perform(post("/ann/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Annotation Book\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String uri = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(uri);
        assertTrue(uri.matches("/ann/books/[1-9][0-9]*"));

        mvc.perform(get(uri))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Annotation Book"));
    }

    @Test
    void functionalHandlerValidatesDtoExplicitly()
            throws Exception {
        mvc.perform(post("/fn/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_title"));
    }

    @Test
    void annotatedControllerUsesBeanValidation()
            throws Exception {
        mvc.perform(post("/ann/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void specificFunctionalRouteIsNotShadowed()
            throws Exception {
        mvc.perform(post("/fn/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Latest Candidate\"}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/fn/books/latest"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void functionalFilterCanShortCircuit()
            throws Exception {
        mvc.perform(get("/fn/books")
                        .header("X-Demo-Block", "true"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("demo_blocked"));

        mvc.perform(get("/ann/books")
                        .header("X-Demo-Block", "true"))
           .andExpect(status().isOk());
    }

    @Test
    void functionalSearchReadsQueryParameter()
            throws Exception {
        mvc.perform(post("/fn/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spring Searchable\"}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/fn/search").param("q", "Spring Searchable"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].title")
                    .value("Spring Searchable"));
    }

    @Test
    void missingBookReturns404ForBothStyles()
            throws Exception {
        mvc.perform(get("/fn/books/99999999"))
           .andExpect(status().isNotFound());
        mvc.perform(get("/ann/books/99999999"))
           .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
mvn test
```

Class khai báo **8 test methods**. Nếu mọi tests pass, Maven exit code `0` và có summary tương tự `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`. Nội dung này được thiết kế/kiểm tra tĩnh cho Java 17 + Spring Boot 3.3.5, **không tuyên bố đã chạy Maven trong quá trình soạn bài**.

### Đặc điểm của tests

- Dùng `@SpringBootTest` + `@AutoConfigureMockMvc` để đi qua MVC infrastructure, không chỉ gọi Java handler trực tiếp.
- Tests tạo book đọc lại `Location` thực, không hardcode ID = 1 hoặc dựa vào thứ tự tests.
- Functional validation assert body `invalid_title` vì handler chủ động tạo response.
- Annotated validation chỉ assert status `400` vì hình dạng default error body còn phụ thuộc Boot/error handling.
- Filter test cho thấy scope chỉ ở functional routes được builder tạo, không tự áp vào annotated controller.
- Route order test bảo đảm URL literal `/latest` không bị `/{id}` bắt trước.
- Shared `BookStore` có state; test search dùng phrase cụ thể để không phụ thuộc list size nếu có books khác.
- MockMvc không giả lập mọi behavior của ingress/proxy, HTTP timeouts hoặc full security filter chain production. Cần bổ sung integration tests khi thêm authentication, CORS, forwarded headers, file upload/streaming.

## 12. Validation và exception policy cho hệ thống thật

Trong annotated style, framework tự điều khiển nhiều bước: type conversion, method argument validation, `@Valid`, `BindingResult`, method return values và `@ControllerAdvice`. Functional style giữ hạ tầng MVC nhưng **handler chịu trách nhiệm rõ hơn** đối với input parsing và ứng xử lỗi của mình.

### 12.1. Dùng Jakarta Validator

Đối với record:

```java
public record NewBook(
        @NotBlank
        @Size(max = 120)
        String title) {}
```

Nếu muốn một body error thống nhất trong functional handler:

```java
Set<ConstraintViolation<NewBook>> violations =
        validator.validate(input);

if (!violations.isEmpty()) {
    return ServerResponse.badRequest()
            .body(new ApiError("invalid_book"));
}
```

Đây là snippet cần imports `jakarta.validation.Validator`, `ConstraintViolation`, `java.util.Set` và record đã có. Nên dùng **stable machine-readable codes** cho client; tránh trả raw rejected values hoặc exception messages chứa PII. Với annotated controller, có thể dùng `@RestControllerAdvice` và handlers của Bean Validation exceptions để đưa lỗi về cùng schema, nhưng phải kiểm thử thật khi cả hai route models coexist.

### 12.2. Type conversion vs business validation

Annotated `@PathVariable long id` sẽ bị MVC conversion framework xử lý nếu ID không phải số. Functional `request.pathVariable("id")` nhận String và việc `Long.parseLong` nằm trong handler. Hai trường hợp đều có thể kết thúc HTTP 400, nhưng **exception class, điểm bắt lỗi và error body khác nhau**.

Đừng bắt `Exception` rộng trong functional filter rồi luôn trả 400 — điều này có thể che bug server/DB và khiến 5xx bị biến thành 4xx. Hãy phân biệt bad input, not found, authorization, conflict, downstream failure, timeout.

### 12.3. Transaction, DI và tests không biến mất

Functional handler class vẫn có constructor injection, có thể gọi Spring `@Service`, `@Transactional` trên service và Spring Security auth context như bất kỳ controller nào. Không nên đặt transaction trên mọi functional lambda hoặc tự gọi persistence trực tiếp từ route config. Business logic nên nằm ở service để dễ chia sẻ, tái sử dụng và test.

## 13. Security: Servlet Filter và functional filter không giống nhau

Khi ứng dụng có Spring Security:

```text
HTTP Request
  |
  v
Spring Security FilterChain
  - authentication
  - authorization
  - CSRF (khi phù hợp)
  - session/cookie protection
  |
  v
DispatcherServlet
  |
  +--> Annotated controllers
  |
  +--> WebMvc.fn RouterFunction
          |
          +--> HandlerFilterFunction (module policies)
          |
          +--> HandlerFunction
```

**Không đặt toàn bộ security ở functional filter**: nếu một annotated endpoint nằm ngoài router, filter đó sẽ không chạy; nếu có static resources/admin endpoints, cũng có thể không chịu filter. Dùng Spring Security path authorization và method/service-level checks cho business permissions.

### CORS

WebMvc.fn có cơ chế CORS qua Servlet `CorsFilter`; đừng mặc định `@CrossOrigin` gắn trên một annotated controller sẽ tự áp vào functional routes khác. Có thể cấu hình CORS ở Servlet filter/Spring Security theo topology. CORS **không phải authentication**, chỉ giới hạn browser cross-origin reads theo HTTP policy; non-browser clients vẫn có thể gửi request.

### CSRF

Với cookie/session authentication, POST functional route vẫn có CSRF risk giống annotated `@PostMapping`. Spring Security CSRF policy nằm ở security filter chain, không phụ thuộc cách route có annotation hay lambda. Không tắt CSRF chỉ để `curl` demo chạy khi thêm login thật.

### Host và Location header

`ServerResponse.created(URI)` tạo Location nhưng URI vẫn phải đến từ nguồn đáng tin. Nếu dùng request host/forwarded headers để dựng absolute URL, reverse proxy phải strip/sanitize untrusted headers để tránh host-header poisoning/phishing. Demo dùng relative `/fn/books/{id}` cố ý tránh tin host tùy ý.

## 14. CORS, content negotiation, route predicates và errors

Khi annotated controller khai báo:

```java
@GetMapping(path = "/ann/books",
            produces = MediaType.APPLICATION_JSON_VALUE)
```

Spring sẽ dùng mapping conditions và content negotiation của annotated infrastructure. Functional equivalent có thể là:

```java
.GET("/fn/books",
        accept(MediaType.APPLICATION_JSON),
        handler::listBooks)
```

Nhưng các trường hợp request không phù hợp predicate **không nhất thiết trả cùng status** với annotated mapping, dù cả hai dùng DispatcherServlet. Khi xây API cần hợp đồng 404/405/406/415 rõ ràng, hãy test mỗi tổ hợp:

- Path tồn tại, HTTP method khác (405 hay not matched?).
- `Accept` không chấp nhận JSON.
- `Content-Type` sai hoặc thiếu với POST.
- Body JSON malformed.
- JSON hợp lệ nhưng DTO constraints fail.
- Authorization/CSRF rejection xảy ra ở filter trước router.
- Conditional requests `If-None-Match`, ETag, caching/Range.
- OPTIONS/preflight CORS, HEAD và path trailing slash.

Không đem test passes của annotated controller áp thẳng làm "bằng chứng" functional router cũng xử lý 100% giống vậy. Ứng dụng có thể đặt error contract bằng exceptions/filters/advice/custom mappings nếu cần nhất quán.

## 15. Ứng dụng thực tế: tổ chức package và refactor

Một cấu trúc maintainable cho functional MVC:

```text
example.book/
├── api/
│   ├── BookRoutes.java       # RouterFunction beans, predicates, filter composition
│   ├── BookHandler.java      # Request -> Response, validation/HTTP errors
│   ├── NewBook.java          # DTO inbound
│   └── BookView.java         # DTO outbound nếu cần
├── application/
│   └── BookService.java      # Business rules
├── domain/
│   └── Book.java
└── infrastructure/
    └── BookRepository.java
```

Route config nên **ngắn**: path grouping, method predicates, filter configuration. Không nên nhồi hàng trăm dòng JDBC, file I/O, auth policy vào một lambda dài trong `@Bean RouterFunction`. Handler class nên nhận dependencies qua constructor injection, delegating xuống service; validation và response contract có thể được gom thành helper có test.

Với annotated controller, package tương tự nhưng `BookController.java` thường vừa khai báo paths vừa làm HTTP adaptation qua annotations. Functional giúp tách route table rõ nhưng có thể tạo nhiều Java files cho API nhỏ; đó là trade-off, không phải ưu/nhược điểm tuyệt đối.

### Không share mutable RouterFunction builder xuyên request

`RouterFunctions.route()` cho builder dùng khi khởi tạo bean; `build()` ra RouterFunction. Đừng lưu builder mutable trong singleton rồi thay route graph khi có request. Dynamic routing ở runtime cần kiến trúc riêng và đồng bộ hóa. Cũng đừng tạo RouterFunction mới mỗi HTTP request; khai báo bean tại startup.

### OpenAPI và documentation

Nhiều OpenAPI generators/springdoc integrations phát hiện annotated endpoints và `@Operation` metadata dễ hơn. Functional routes cũng có thể được hỗ trợ tùy tool/version hoặc yêu cầu explicit route documentation; cần kiểm chứng công cụ cụ thể, không suy diễn. Nếu migration lớn, kiểm thử OpenAPI output lẫn client SDK contract và security schemes trước khi chuyển.

## 16. Một số use case đáng giá và giới hạn

### 16.1. Gateway/facade với nhiều predicates

Một facade có thể có routes dựa vào path, Accept, Content-Type và custom headers. Functional route composition làm predicates/traversal order rõ. Tuy nhiên nếu nó thực hiện HTTP calls blocking, router DSL vẫn chạy synchronous — hãy thiết kế downstream timeouts, connection pooling, cancellation và circuit breakers.

### 16.2. Static resources

WebMvc.fn có hỗ trợ phục vụ `Resource` qua router utilities, phù hợp một số trường hợp SPA fallback/custom routes. Không nên tự viết một catch-all functional route quá sớm làm che `/api/**`, 404 hoặc security routes. Với static assets thông thường, Spring MVC static resource handlers đã giải quyết nhiều việc như caching, ETag và byte ranges; chọn cơ chế đơn giản phù hợp.

### 16.3. SSE/streaming

Spring MVC có async response APIs, WebMvc.fn cũng có một số response facilities theo phiên bản. Nhưng một functional route lambda **không tự chứng minh** streaming end-to-end non-blocking. Muốn phục vụ SSE hàng nghìn clients hoặc backpressure phức tạp, phải kiểm tra MVC vs WebFlux, network write behavior, executor resource usage, reconnect và timeout — đây là lựa chọn kiến trúc khác với router style.

### 16.4. API versioning

Với `/api/v1` và `/api/v2`, functional route grouping bằng `path("/api/v1",...)` thuận tiện. Với annotation dùng type-level `@RequestMapping("/api/v1")`. Cả hai vẫn cần contract tests cho version routing và backward compatibility; không phải cứ đổi path prefix là toàn bộ model/service/schema đã tương thích.

## 17. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| `RouterFunction` bean không có effect | Khai báo sai type/package, bean không scan, không dùng Spring MVC infrastructure | Xác nhận `org.springframework.web.servlet.function` và Spring-managed bean |
| Dùng WebFlux `ServerResponse.bodyValue(...)` | Import nhầm `org.springframework.web.reactive.function.server` | Trong WebMvc.fn dùng `ServerResponse.ok().body(...)` |
| `GET /books/latest` thành `bad_id` | Route `/{id}` đứng trước `/latest` | Đặt literal route trước, test ordering |
| `@Valid` không tự kiểm tra body trong functional handler | Chỉ gọi `request.body(...)` mà quên validate | Chủ động dùng Validator/appropriate binding |
| POST JSON returns 404 thay vì 415 | `contentType` predicate không match | Xem request predicate semantics và error contract |
| `@ControllerAdvice` không bao phủ hết lỗi functional | Error handling path khác annotated argument flow | Test malformed JSON, conversion, uncaught exceptions |
| Header route filter chặn annotated controllers? Không | Functional filter chỉ áp vào router tương ứng | Dùng security Servlet filter chain nếu cần toàn app |
| CORS preflight bị chặn | Chỉ dựa `@CrossOrigin` annotated | Thiết kế `CorsFilter`/Security CORS cho functional endpoints |
| CSRF POST lỗi sau khi thêm Security | CSRF vẫn áp dụng với session endpoints | Gửi token hợp lệ, không tắt policy tùy tiện |
| Route ngắn nhưng business logic rối | Mọi logic bị nhồi vào lambda | Tách Handler/Service/Repository |
| Functional không nhanh hơn annotated | Cùng Servlet MVC, blocking I/O vẫn blocking | Benchmark/đo service/downstream bottlenecks |
| API OpenAPI thiếu endpoints | Tool không phát hiện functional routes mặc định | Verify docs generator/version/integration |
| Không nhận header Accept mong muốn | Nhầm `accept` với `contentType` | Inspect HTTP request và predicates |
| Hai nhóm annotation/functional cùng URL | HandlerMapping precedence không rõ | Tránh route overlap; test framework-specific order |
| Error response không nhất quán | Validation/errors xử lý ở hai pipeline khác nhau | Định nghĩa shared error DTO và tests |
| Route filter ghi response nhưng không chạy handler | `next.handle` không được gọi | Kiểm tra short-circuit intentional hay bug |
| Response Location trỏ host lạ | Tin Host/Forwarded từ client | Trusted proxy, canonical base URL hoặc relative URI |
| Trả 201 trong JSON nhưng HTTP status 200 | Quên dùng `ServerResponse.created` | Assert actual HTTP status/Location trong MockMvc |
| DTO từ input chứa field quyền hạn | Mass assignment/over-posting | DTO allowlist, server-side user/tenant auth |
| Test chỉ gọi Handler trực tiếp, mapping vẫn lỗi | Không test DispatcherServlet route predicates | MockMvc + integration tests |

Cách debug hệ thống:

1. Xác nhận đúng namespace `web.servlet.function` và router là bean được Spring scan.
2. Dò request HTTP method/path/headers và thứ tự routes thực tế.
3. Nếu `latest` fail, kiểm tra wildcard/variable route đứng trước hay sau.
4. Kiểm tra `Content-Type` cho POST JSON và `Accept` cho GET.
5. Xác nhận `request.body(Dto.class)` có converter và data type hợp lệ.
6. Phân biệt parse exception, validation errors và handler business errors.
7. Kiểm tra filter scope, việc gọi `next.handle(request)` và security chain.
8. Test cả functional và annotated paths, nhất là nếu ứng dụng kết hợp cả hai.
9. Khi có reverse proxy, kiểm tra forwarded headers, context path, CORS và Location.
10. Đo real latency/throughput trước khi kết luận đổi routing style giúp hiệu năng.

## 18. Checklist đưa WebMvc.fn vào sản phẩm

1. Chọn WebMvc.fn vì nhu cầu route composition/maintainability, không vì tưởng non-blocking.
2. Tất cả Spring MVC functional classes dùng `org.springframework.web.servlet.function`.
3. Đặt routes cụ thể trước routes có placeholders/wildcards.
4. Viết tests cho route collisions và thứ tự khi có nhiều router beans.
5. Dùng constructor injection cho handler, tách service/repository.
6. Đọc body qua converters; khai báo Content-Type/Accept expectations đúng.
7. Kích hoạt Bean Validation tường minh trong functional handler.
8. Chuẩn hóa JSON errors/status/Location và kiểm thử bằng MockMvc.
9. Không dùng functional filter thay Spring Security filter chain.
10. CORS/CSRF/auth/tenant rules được kiểm tra từ HTTP bên ngoài.
11. Không tin request Host/Forwarded headers khi dựng redirects/absolute URLs.
12. Không đặt business logic blocking dài trên Servlet thread chỉ vì route là lambda.
13. Kiểm tra compatibility của OpenAPI/Actuator/tracing tools.
14. Khi migration, không chuyển toàn bộ chỉ để thay annotations — đo chi phí và benefits.
15. Có integration tests cho proxy/Servlet runtime nếu production deployment phức tạp.
16. Không tạo builders/routers động mỗi request khi không có yêu cầu rõ.

## 19. Bài tập tự kiểm tra

1. `RouterFunction` khác `HandlerFunction` như thế nào?
2. `ServerRequest.body(MyDto.class)` và `@RequestBody MyDto` dùng chung cơ chế converter nào, khác nhau ở đâu?
3. Vì sao `/books/latest` phải đứng trước `/books/{id}` trong functional route builder?
4. `ServerResponse.created(location)` đặt HTTP status/header gì?
5. `@NotBlank` trong DTO tự chạy khi functional handler gọi `request.body(...)` không?
6. Vì sao WebMvc.fn chạy trên DispatcherServlet nhưng không phải WebFlux.fn?
7. `accept(APPLICATION_JSON)` khác `contentType(APPLICATION_JSON)` về request header nào?
8. Functional `filter` và Servlet `Filter` khác phạm vi áp dụng thế nào?
9. Nếu một filter không gọi `next.handle(request)` thì chuyện gì xảy ra?
10. Tại sao `HandlerFunction` có thể block Servlet thread dù được viết bằng lambda?
11. Khi migrate annotated sang functional, test những gì với 400/404/405/406/415?
12. Vì sao nên tránh trùng URL giữa annotated controller và functional routes?
13. Có thể dùng một Spring `@Service` cho cả hai loại handler không?
14. WebMvc.fn có tự tạo OpenAPI docs đầy đủ như annotated controllers trong mọi generator không?
15. Nếu thêm Spring Security, functional POST có chịu CSRF policy của app không?

**Gợi ý đáp án:** (1) Router chọn handler, handler xử lý request thành response. (2) Cùng MVC converters nhưng functional đọc tường minh còn annotation method argument resolution. (3) First-match order. (4) 201 + Location. (5) Không tự động, cần validator/binding chính xác. (6) Servlet package/sync pipeline khác reactive. (7) Accept header vs Content-Type body header. (8) Router group vs Servlet/filter chain scope. (9) Short-circuit. (10) Blocking API vẫn block. (11) Route predicates, media types, validation, exceptions và security. (12) Không dự đoán HandlerMapping cross-style precedence. (13) Có, như BookStore demo. (14) Phụ thuộc tooling. (15) Có, theo security config.

## 20. Tài liệu chính thức

- [Spring Framework — Functional Endpoints, Servlet MVC](https://docs.spring.io/spring-framework/reference/web/webmvc-functional.html)
- [Spring Framework 6.1 — RouterFunction Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/RouterFunction.html)
- [Spring Framework 6.1 — RouterFunctions Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.html)
- [Spring Framework 6.1 — ServerRequest Javadoc (Servlet)](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/ServerRequest.html)
- [Spring Framework 6.1 — ServerResponse Javadoc (Servlet)](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/ServerResponse.html)
- [Spring Framework 6.1 — HandlerFunction Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/HandlerFunction.html)
- [Spring Framework — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Framework — Spring MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)
- [Spring Framework — Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [Spring MVC Testing with MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)

**Tổng kết:** WebMvc.fn là mô hình functional routing và handling trong **Spring MVC Servlet**, nơi `RouterFunction`/`RequestPredicate` chọn `HandlerFunction` nhận `ServerRequest` rồi trả `ServerResponse`. Nó thay cách khai báo routing/HTTP adapter, **không thay DispatcherServlet hoặc biến blocking I/O thành reactive**. Khác biệt cần nhớ nhất so với annotated controllers là **thứ tự route matching**, **cách lấy/validate tham số tường minh**, **functional filter scope** và **cách quản lý error contract**. Có thể chạy cả hai phong cách cạnh nhau, nhưng nên bảo vệ behavior bằng MockMvc tests và giữ security/validation/business logic tách bạch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-functional-endpoints-002">ServerRequest, ServerResponse và HandlerFunction</a>

<details>
<summary>Xem chi tiết</summary>

# ServerRequest, ServerResponse và HandlerFunction trong Spring Web MVC Functional Endpoints

## 1. Functional MVC giải quyết bài toán gì?

Trong Spring MVC quen thuộc, ta thường khai báo `@RestController`, `@GetMapping`, `@PostMapping`. Spring quét các annotation để chọn handler cho từng HTTP request. Ngoài mô hình đó, **Spring Web MVC còn có WebMvc.fn**, một API theo kiểu **functional endpoints**: ta mô tả route bằng function và xử lý request bằng Java lambda hoặc method reference.

Hợp đồng cốt lõi là:

- `ServerRequest`: abstraction **đầu vào**, cung cấp HTTP method, URI, query/path parameters, headers, cookies và việc đọc/bind body.
- `HandlerFunction<ServerResponse>`: **hàm xử lý**, nhận một `ServerRequest` và trả một `ServerResponse`.
- `ServerResponse`: abstraction **đầu ra**, biểu diễn status, headers, cookies và body; được dựng bằng builder (immutable contract).
- `RouterFunction<ServerResponse>`: **quy tắc chọn hàm**; nhận request rồi trả về `Optional<HandlerFunction<ServerResponse>>` khi match.

Một phép ghép đơn giản:

```java
HandlerFunction<ServerResponse> sayHello =
        request -> ServerResponse.ok().body("Xin chào");

RouterFunction<ServerResponse> router =
        RouterFunctions.route(
                RequestPredicates.GET("/hello"), sayHello);
```

Đây là đoạn code minh họa để hiểu chữ ký. Ví dụ Spring Boot hoàn chỉnh có thể chạy ở mục 10.

**Điểm rất quan trọng:** WebMvc.fn chạy trên **Servlet stack** với `DispatcherServlet`. Nó **không phải** WebFlux.fn; không cần Reactor `Mono`/`Flux` để sử dụng ba thành phần này. API Servlet có package:

```java
org.springframework.web.servlet.function.ServerRequest
org.springframework.web.servlet.function.ServerResponse
org.springframework.web.servlet.function.HandlerFunction
org.springframework.web.servlet.function.RouterFunction
org.springframework.web.servlet.function.RouterFunctions
org.springframework.web.servlet.function.RequestPredicates
```

WebFlux.fn dùng `org.springframework.web.reactive.function.server.*` và các chữ ký bất đồng bộ như `Mono<ServerResponse>`. Import nhầm hai hệ sẽ dẫn tới lỗi compile hoặc kỳ vọng sai về cách đọc body.

### 1.1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích chính xác quan hệ `RouterFunction → HandlerFunction → ServerResponse`.
2. Đọc route parameters, query parameters, headers và JSON body từ `ServerRequest`.
3. Trả 200, 201 với Location, 204, 400, 404 và JSON bằng `ServerResponse` builder.
4. Soạn `RouterFunctions.route()`, hiểu thứ tự đánh giá routes, `RequestPredicates`, lồng path và composition.
5. Sử dụng `HandlerFilterFunction`/`before`/`after` mà không nhầm với Servlet Filter hoặc Spring Security.
6. Tách routing với business logic, validation, transaction, lỗi và security; biết giới hạn của functional style.
7. Xây một ứng dụng Spring Boot 3 + Java 17 có route list/search/get/create/delete và 9 bài kiểm thử MockMvc.

Bài học áp dụng cho Spring Framework 6.x / Spring Boot 3.x với Java 17+. Java records được dùng để viết DTO ngắn gọn.

## 2. Luồng hoạt động dưới DispatcherServlet

WebMvc.fn không tạo một web server thứ hai. Nó dùng cùng cơ sở hạ tầng Servlet MVC với annotated controllers:

```text
Browser/API client
     │ HTTP GET /api/notes/1
     ▼
Servlet filters (security, logging, ...)
     │
     ▼
DispatcherServlet
     │
     ▼
HandlerMapping chain
     ├── RouterFunctionMapping
     │      └── RouterFunction<ServerResponse>
     │             ├── xét RequestPredicate của route
     │             └── trả HandlerFunction nếu match
     │
     ├── RequestMappingHandlerMapping
     │      └── annotated @GetMapping/@PostMapping...
     │
     └── các HandlerMapping khác
     │
     ▼
HandlerFunctionAdapter
     │
     ▼
HandlerFunction.handle(ServerRequest)
     ├── đọc request
     ├── gọi service/repository
     └── tạo ServerResponse
     │
     ▼
ServerResponse.writeTo(...) / HTTP converters
     ▼
HTTP status + headers + body
```

Sơ đồ là lược đồ lý luận, **không khẳng định RouterFunctionMapping luôn có order cao hơn RequestMappingHandlerMapping** trong mọi Spring/Boot configuration. Các HandlerMapping có `order` và setup riêng; khi một URL có hai loại handler cạnh tranh, cần xem actual HandlerMapping order, tránh cùng khai báo một đường dẫn trong functional và annotated controllers.

Trong setup Spring Boot MVC thông thường, bạn chỉ cần:

```java
@Bean
RouterFunction<ServerResponse> routes(NoteHandlers handlers) {
    return RouterFunctions.route()
            .GET("/api/notes", handlers::list)
            .GET("/api/notes/{id}", handlers::get)
            .build();
}
```

Spring tự tìm `RouterFunction<?>` beans, ghép/đăng ký thông qua `RouterFunctionMapping` và sử dụng `HandlerFunctionAdapter` để gọi handler. Không bắt buộc tự cấu hình một Servlet khác. `spring-boot-starter-web` thường đủ để bật hạ tầng WebMvc.fn.

### 2.1. So với @RestController

Annotated style:

```java
@RestController
class NoteController {
    @GetMapping("/api/notes/{id}")
    Note get(@PathVariable long id) {
        return service.find(id);
    }
}
```

Functional style:

```java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .GET("/api/notes/{id}", handlers::get)
                .build();
```

Chức năng cuối có thể giống nhau, nhưng responsibilities khác:

| Annotated MVC | WebMvc.fn |
| --- | --- |
| Annotation trên class/method quyết định mapping | `RouterFunction` và `RequestPredicate` quyết định mapping |
| Parameters được bind thành method arguments | `ServerRequest` cung cấp phương thức truy cập |
| Return DTO/ResponseEntity | Trả `ServerResponse` |
| Logic thường đặt trong controller method | Logic trong lambda/handler class |
| Advice/validation/integration có convention của annotated MVC | Cần hiểu cụ thể WebMvc.fn binding/exception/filter pipeline |
| Có thể cùng tồn tại trên DispatcherServlet | Có thể cùng tồn tại với annotated controllers |

WebMvc.fn không tự tăng tốc mọi endpoint hoặc làm blocking JDBC thành non-blocking. Giá trị chính là **route definitions composable**, behavior biểu diễn bằng functions và separation rõ ràng giữa route mapping và handler logic.

## 3. HandlerFunction: một hàm xử lý một request

Về bản chất, chữ ký API có thể hiểu như:

```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    T handle(ServerRequest request) throws Exception;
}
```

Đây là **trích yếu chữ ký** để học, không phải toàn bộ source code Spring. Lambda:

```java
HandlerFunction<ServerResponse> hello = request ->
        ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Hello from WebMvc.fn");
```

Một class handler thường dễ bảo trì hơn nhiều lambdas lớn:

```java
class NoteHandlers {
    ServerResponse list(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of("Spring", "MVC"));
    }

    ServerResponse health(ServerRequest request) {
        return ServerResponse.ok().body("OK");
    }
}
```

Code trên minh họa methods được tham chiếu bởi `handlers::list`, `handlers::health`. Trong dự án đầy đủ, handler được tạo thành Spring component, inject service và đưa vào RouterFunction bean.

### 3.1. HandlerFunction không nhất thiết là Spring bean

Lambda có thể tạo trong bean route; handler class có thể là Spring `@Component`, một object được tự tạo hoặc phụ thuộc được cung cấp qua DI. Điều quan trọng là `RouterFunction` chứa một reference tới handler hợp lệ.

Tuy nhiên, **không tự `new Service()` khắp nơi** nếu service cần transaction/security/configuration do Spring quản lý. Tách dependencies và inject qua constructor giúp kiểm thử tốt hơn.

### 3.2. Checked exceptions

`HandlerFunction#handle` cho phép ném `Exception`. Phương thức handler đọc JSON body có thể khai báo `throws Exception`:

```java
ServerResponse create(ServerRequest request) throws Exception {
    CreateNote command = request.body(CreateNote.class);
    return ServerResponse.ok().body(command);
}
```

Đây chỉ là ví dụ cú pháp; trong production, phải validate command và xử lý lỗi decode/validation theo error contract. Không trả stack trace hoặc raw persistence exception cho client.

## 4. ServerRequest: đọc HTTP request qua một interface

`ServerRequest` là façade phía handler cho Servlet HTTP request. Một số nhóm API thường dùng:

| Nhu cầu | API điển hình | Đầu ra |
| --- | --- | --- |
| Method | `request.method()` | `HttpMethod` |
| URI đầy đủ | `request.uri()` | `URI` |
| Path | `request.path()` | Request path string |
| Query một giá trị | `request.queryParam("q")` | `Optional<String>` |
| Toàn query params | `request.params()` | `MultiValueMap<String,String>` |
| Path variable | `request.pathVariable("id")` | `String` |
| Headers | `request.headers()` | `ServerRequest.Headers` |
| Header đơn | `request.headers().firstHeader("X-Request-Id")` | String hoặc null |
| Body kiểu class | `request.body(CreateNote.class)` | DTO từ `HttpMessageConverter` |
| Body generic | `request.body(new ParameterizedTypeReference<List<Note>>() {})` | `List<Note>` |
| Servlet request gốc | `request.servletRequest()` | `HttpServletRequest` |
| Bind params | `request.bind(Foo.class, binder -> ...)` | Object qua binder |

Một số methods có thể throw exception khi không có path variable hoặc input không parse được. `ServerRequest` giúp code functional thuận tiện hơn, **không bỏ qua** HTTP semantics, converters hoặc validation.

### 4.1. Path parameters

Route:

```java
RouterFunctions.route()
    .GET("/api/notes/{id}", handlers::get)
    .build();
```

Handler:

```java
ServerResponse get(ServerRequest request) {
    String raw = request.pathVariable("id");
    long id;
    try {
        id = Long.parseLong(raw);
    } catch (NumberFormatException ex) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "invalid_id"));
    }

    return service.find(id)
            .map(note -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(note))
            .orElseGet(() -> ServerResponse.notFound().build());
}
```

Đây là snippet minh họa, không phải method standalone vì `service` chưa được khai báo. Bài hoàn chỉnh sẽ dùng ConcurrentHashMap để không phụ thuộc DB.

**Lưu ý:** `ServerRequest.pathVariable("id")` trả string, không tự động thực hiện type conversion y hệt `@PathVariable long id` trên annotated controller. Handler phải parse và quyết định response lỗi theo contract.

### 4.2. Query params

```java
String q = request.queryParam("q").orElse("");
String trimmed = q.trim();
```

Nếu query có nhiều giá trị, `request.params()` cho phép đọc danh sách. Phân biệt:

- `pathVariable("id")` từ `/{id}` trong route.
- `queryParam("q")` từ `?q=spring`.
- `headers().firstHeader("...")` từ HTTP headers.
- `body(...)` từ request entity.

Không đọc `request.body(...)` hai lần một cách ngẫu nhiên: Servlet request body stream thông thường không được tự động rewind. Sử dụng DTO một lần rồi truyền DTO vào service.

### 4.3. Headers và content negotiation

```java
MediaType requested =
        request.headers().accept().stream()
                .findFirst()
                .orElse(MediaType.ALL);

String correlation =
        request.headers().firstHeader("X-Request-Id");
```

Snippet minh họa cách truy cập; server không nhất thiết phải lấy media type đầu tiên để quyết định response trong mọi tình huống. Dùng `RequestPredicates.accept(MediaType.APPLICATION_JSON)` hoặc Spring HTTP converters khi muốn negotiation rõ hơn.

Đừng coi một header như `X-User-Role` do client tự gửi là bằng chứng phân quyền. Headers có thể bị giả bởi non-browser clients; authentication/authorization phải dựa trên trust boundary và Spring Security.

### 4.4. Đọc JSON body

Với `spring-boot-starter-web`, Jackson và MVC converters thường hỗ trợ JSON:

```java
record CreateNote(String title) {}

ServerResponse create(ServerRequest request) throws Exception {
    CreateNote data = request.body(CreateNote.class);

    if (data == null || data.title() == null ||
            data.title().isBlank()) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "title_required"));
    }

    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(data);
}
```

Snippet này có thể đặt trong một handler class cùng record; Java 17 hỗ trợ record. Khi JSON sai cú pháp hoặc content type không được converter hỗ trợ, Spring có thể ném lỗi parse/media type để tầng exception handling xử lý.

**Không mặc định cho rằng** `@Valid` đặt trên record type hoặc argument của một `HandlerFunction` sẽ tự tạo `BindingResult` như annotated controller. Functional endpoint cần kiểm tra validation một cách **tường minh**, hoặc dùng binder/validator tích hợp phù hợp.

### 4.5. Bind form/query params

Spring MVC Functional API có:

```java
FormInput input = request.bind(
        FormInput.class,
        binder -> binder.setAllowedFields("title", "category"));
```

Đoạn trên minh họa binder, cần `FormInput` là một class bindable có properties tương ứng. `setAllowedFields(...)` hữu ích để giảm **mass assignment** khi bind từ request parameters; cần hiểu cách binding/validation errors được báo về handler hoặc MVC exception resolution.

Với JSON body, hãy dùng DTO request riêng, không deserialize thẳng entity persistence chứa các fields như role, ownerId, paymentStatus rồi lưu không kiểm soát.

## 5. ServerResponse: dựng status, headers và body

`ServerResponse` có immutable-style contract. Ta không mutate một response đã “build” theo ý muốn; thường gọi static builder để tạo response:

```java
ServerResponse response = ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Demo", "webmvc-fn")
        .body(Map.of("status", "ready"));
```

Các static entrypoints thường gặp:

| Mục tiêu | Ví dụ |
| --- | --- |
| 200 OK có body | `ServerResponse.ok().body(data)` |
| 200 JSON | `ServerResponse.ok().contentType(APPLICATION_JSON).body(data)` |
| 201 Created | `ServerResponse.created(location).body(resource)` |
| 204 No Content | `ServerResponse.noContent().build()` |
| 400 Bad Request | `ServerResponse.badRequest().body(error)` |
| 404 Not Found | `ServerResponse.notFound().build()` |
| 401/403/409/422... | `ServerResponse.status(HttpStatus.CONFLICT).body(error)` |
| Redirect | `ServerResponse.temporaryRedirect(uri).build()` |
| Header tùy ý | Builder `.header("X-Key", "value")` |
| Content type | Builder `.contentType(MediaType.APPLICATION_JSON)` |

### 5.1. 200 với body

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("id", 12, "name", "Example"));
```

Kết quả JSON dự kiến:

```json
{"id":12,"name":"Example"}
```

Thứ tự keys của JSON object không phải contract mặc định; kiểm thử từng field thay vì so chuỗi byte-for-byte.

### 5.2. 201 + Location

```java
URI location = URI.create("/api/notes/12");
return ServerResponse.created(location)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Note(12, "Created"));
```

Một response “create” tốt thường có status `201 Created` và `Location` trỏ tới URL của tài nguyên mới. Trong sản phẩm thật, tùy yêu cầu URI có thể là absolute (host/context path đúng qua reverse proxy) hoặc relative như demo; không ghép host từ header không tin cậy.

### 5.3. 204 và body rỗng

```java
return ServerResponse.noContent().build();
```

HTTP 204 không có representation body. Không gắn nội dung JSON vào response rồi kỳ vọng browser nhận body ở 204.

### 5.4. Response body không tự là WebFlux Publisher

Ở WebMvc.fn:

```java
ServerResponse.ok().body(note);
```

Đây là Servlet MVC API. WebFlux.fn thường viết một `Mono<ServerResponse>` hoặc response builder với `bodyValue(...)` tùy API. Một số async adapters có hỗ trợ cho body async trong WebMvc.fn, **nhưng không suy ra API Servlet giống hệt reactive stack**. Nếu ứng dụng cần non-blocking end-to-end, cần xét stack, các dependencies và contract riêng.

## 6. RouterFunction: request được chuyển cho HandlerFunction nào?

Một router là function chọn handler. Với builder:

```java
@Bean
RouterFunction<ServerResponse> routes(NoteHandlers handler) {
    return RouterFunctions.route()
            .GET("/api/notes/search", handler::search)
            .GET("/api/notes/{id}", handler::get)
            .GET("/api/notes", handler::list)
            .POST("/api/notes", handler::create)
            .DELETE("/api/notes/{id}", handler::delete)
            .build();
}
```

**Thứ tự trong RouterFunction có ý nghĩa.** Spring mô tả routes được đánh giá theo thứ tự; route đầu tiên match được chọn. Vì vậy đặt `GET /api/notes/search` **trước** `GET /api/notes/{id}`, nếu không string `search` có thể bị xem là `id`, dẫn tới 400/404 trong handler sai.

### 6.1. Các RequestPredicate tiêu biểu

Có thể match không chỉ URL mà còn method, Accept, Content-Type:

```java
import static org.springframework.web.servlet.function.RequestPredicates.*;

RouterFunction<ServerResponse> router = RouterFunctions.route()
    .GET("/api/notes", accept(MediaType.APPLICATION_JSON),
         handler::list)
    .POST("/api/notes", contentType(MediaType.APPLICATION_JSON),
          handler::create)
    .build();
```

Đây là ví dụ cấu hình **khác** với routes của project chạy ở mục 10. Predicate có thể kết hợp:

```java
RequestPredicate predicate =
        RequestPredicates.GET("/api/notes")
                .and(RequestPredicates.accept(MediaType.APPLICATION_JSON));
```

Khi predicate không match, RouterFunction có thể thử route tiếp; nếu không route nào match, Servlet MVC tìm handler khác và có thể trả 404/405/406/415 tùy mapping/converters và hạ tầng. **Đừng khẳng định mọi HTTP content-type lỗi luôn được WebMvc.fn tự trả đúng 415**, nhất là khi POST route có `contentType` predicate mà không match.

### 6.2. Nested routes

Khi nhiều endpoints chung prefix, có thể nhóm:

```java
return RouterFunctions.route()
    .path("/api/notes", builder -> builder
        .GET("/search", handler::search)
        .GET("/{id}", handler::get)
        .GET(handler::list)
        .POST(handler::create))
    .build();
```

Đoạn này là cấu hình thay thế cho routes dùng absolute path; không tạo đồng thời cả hai để tránh mapping trùng. Khi nesting, cần hiểu path rỗng và slash trong builder; nếu muốn tránh nhầm giữa `/api/notes` và `/api/notes/`, cấu hình và test đúng URL thực tế.

### 6.3. Compose RouterFunctions

Có thể ghép nhiều nhóm routes:

```java
RouterFunction<ServerResponse> apiRoutes = ...;
RouterFunction<ServerResponse> adminRoutes = ...;

RouterFunction<ServerResponse> all =
        apiRoutes.and(adminRoutes);
```

Hoặc builder `.add(otherRouter)`, `.andRoute(predicate, handler)`. Thứ tự ghép có nghĩa vì các router functions được đánh giá tuần tự; tránh catch-all pattern quá sớm.

### 6.4. Cùng tồn tại nhiều RouterFunction bean

`RouterFunctionMapping` có thể tự tìm các RouterFunction beans, sắp xếp và kết hợp. Với nhiều beans có overlapping paths, cần order rõ và test kết quả; đừng giả định tên bean hay thứ tự file Java quyết định precedence. Trong một nhóm API vừa phải, một router bean cho từng domain/module thường đủ minh bạch.

### 6.5. RouterFunctionMapping order khác thứ tự route trong builder

Có hai tầng:

1. **Giữa HandlerMappings:** các mapping MVC/annotations/static resources có order riêng.
2. **Bên trong RouterFunction:** các routes trong một function được thử theo thứ tự khai báo/compose.

Một URL có functional route cụ thể nhưng HandlerMapping khác được chọn trước có thể không bao giờ tới router đó. Không nhầm “đặt route cụ thể lên đầu” với việc đổi `HandlerMapping#order` toàn MVC.

## 7. HandlerFilterFunction: behavior trước/sau HandlerFunction

WebMvc.fn có middleware kiểu functional qua `HandlerFilterFunction`. Filter nhận:

- `ServerRequest request`.
- `HandlerFunction<ServerResponse> next`.

Nó có thể xử lý request trước, gọi `next.handle(request)`, quan sát response, hoặc **không gọi next** để trả response sớm.

Ví dụ filter minh họa:

```java
.filter((request, next) -> {
    if ("reject".equals(
            request.headers().firstHeader("X-Demo-Flow"))) {
        return ServerResponse.status(
                HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "demo_filtered"));
    }
    return next.handle(request);
})
```

Trong project hoàn chỉnh, đây là **tình huống demo để kiểm tra middleware có thể short-circuit**, **không phải authorization/filter bảo mật production**. Client có thể tự chọn header X-Demo-Flow, vì vậy không dùng nó làm quyết định user identity hoặc rate limiting thật.

### 7.1. before và after

Builder còn có `before` và `after`:

```java
.before(request ->
        ServerRequest.from(request)
                .header("X-Internal-Hint", "demo")
                .build())
.after((request, response) -> {
    // logging/observability (tránh log dữ liệu nhạy cảm)
})
```

Ví dụ này mô tả API, chưa hoàn chỉnh một RouterFunction độc lập. `before` có thể cung cấp một ServerRequest wrapper tùy biến cho downstream; `after` phù hợp quan sát nhẹ sau handler. Không dùng hook để tốn CPU lớn hoặc viết vào HttpServletResponse khi response đã commit.

**Trong nested routes**, filter đặt bên trong nested builder chỉ áp dụng nhóm con tương ứng, khác với filter đặt ở root builder áp dụng tất cả routes trong root. Hãy test scope filter bằng URL ngoài/ trong nhóm.

### 7.2. So với Servlet Filter và HandlerInterceptor

| Công cụ | Layer | Phạm vi |
| --- | --- | --- |
| Servlet `Filter` | Container, trước DispatcherServlet | Theo URL/dispatcher mapping, có thể áp dụng rộng |
| MVC `HandlerInterceptor` | Spring MVC handler chain | Trước/sau handler MVC phù hợp |
| WebMvc.fn `HandlerFilterFunction` | Bên trong RouterFunction | Chỉ các routes được wrap |
| Spring Security `SecurityFilterChain` | Security Servlet filters | Authentication, authorization, CSRF, headers... |
| `@ControllerAdvice` | MVC error/binding/response handling | Khác phạm vi và hỗ trợ tùy handler adapter |

Trong production, authentication/authorization nên do Spring Security đảm nhiệm, không tự dùng một request header và HandlerFilterFunction để thay thế. Middleware functional có ích để áp dụng logic domain-specific, enrichment nhẹ hoặc custom error mapping cho route group.

## 8. Validation, exceptions và persistence

### 8.1. Validation với DTO

Functional handler đọc body:

```java
CreateNote input = request.body(CreateNote.class);
```

Việc decode thành Java record **không bảo đảm tất cả Bean Validation constraints tự chạy như một annotated method có `@Valid @RequestBody`**. Lựa chọn:

- Với rule đơn giản, validate minh bạch trong handler/service (như demo `title` không blank, max length).
- Với rules phức tạp, inject `jakarta.validation.Validator` hoặc Spring `org.springframework.validation.Validator` và chuyển violations thành error response ổn định.
- Với form/query binding, xem `ServerRequest.bind(...)` và `DataBinder` để giới hạn fields.

Một đoạn dùng Jakarta Bean Validation (cần starter-validation và validator bean):

```java
Set<ConstraintViolation<CreateNote>> violations =
        validator.validate(input);

if (!violations.isEmpty()) {
    return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("error", "validation_failed"));
}
```

Đây là snippet minh họa, không có đủ class/import để chạy độc lập. Với ứng dụng thật, nên gom violations thành trường/constraint messages có thể localize, không trả raw sensitive values.

### 8.2. Lỗi parse body và lỗi nghiệp vụ khác nhau

| Tình huống | Cách xử lý hợp lý |
| --- | --- |
| JSON sai syntax | 400 nếu MVC converter/handler mapping xử lý theo error policy |
| JSON đúng syntax nhưng field title blank | 400 với error code `title_required` |
| ID path không parse long | 400 với error code `invalid_id` |
| ID hợp lệ nhưng không tìm thấy | 404 |
| Conflict vì resource đã tồn tại | 409 nếu contract quy định |
| Content-Type không được hỗ trợ | 415 nếu Framework/converter contract áp dụng |
| User đã login nhưng thiếu quyền | 403 qua Spring Security |
| Lỗi DB/remote service | 5xx có structured error, không leak stack trace |

Trong annotated MVC, `@ExceptionHandler`/`@ControllerAdvice` có tích hợp quen thuộc với controller methods. Với functional endpoints, cần **test thực tế** cách exceptions đi qua `HandlerFunctionAdapter` và MVC exception resolvers; nếu muốn xử lý tất cả domain errors trong một route group, `HandlerFilterFunction` có thể catch domain exceptions rồi dựng `ServerResponse` tương ứng. Đừng giả định annotation-based binding errors và mọi handler advice áp dụng tự động 100% giống nhau.

### 8.3. Transaction boundaries

Router/handler không thay thế service layer. Ví dụ lưu một note vào DB:

```java
@Service
class NoteService {
    @Transactional
    public Note create(CreateNote command) {
        // validate ownership, convert DTO, persist through repository
        // and return saved object
    }
}
```

Đoạn minh họa thiếu model/repository và không standalone. Trong project thật, service method transactional cần được gọi qua Spring proxy phù hợp, và validations/authorization trên resource vẫn phải thực thi khi ghi.

### 8.4. OpenAPI, observability và filters

Một số tools tự nhận diện annotated controllers sâu hơn functional endpoints. Nếu dùng OpenAPI generator/documentation plugins, cần kiểm tra phiên bản có support `RouterFunction`/functional routes hay không, hoặc khai báo docs bổ sung. Instrumentation/logging thường vẫn có Servlet Filter/Spring MVC chain, nhưng custom metrics cần xác định matched route/handler đúng.

## 9. Security, caching và HTTP semantics

WebMvc.fn **không tự cung cấp authentication, authorization, CSRF hoặc session management**. Nó chia sẻ Servlet security/filter chain với Spring MVC.

- **Authentication:** ai là user? Dựa trên Spring Security/OIDC/Session/Bearer hợp lệ.
- **Authorization:** user có quyền đọc/sửa note với ID cụ thể và tenant phù hợp không?
- **CSRF:** state-changing methods khi browser dùng cookie/ambient credentials cần policy đúng.
- **CORS:** JavaScript từ origin nào có thể đọc response? WebMvc.fn reference khuyến nghị dedicated `CorsFilter` cho functional endpoints khi cần.
- **Validation:** không đưa DTO tùy ý vào entity; chống mass assignment.
- **Caching:** GET có thể dùng ETag/Cache-Control khi representation thích hợp; 304 không tự bỏ query DB nếu validator tính muộn.
- **Idempotency:** GET là safe; DELETE/PUT có idempotent semantics phù hợp; POST create có thể sinh duplicate nếu retry, cần idempotency key ở nghiệp vụ nếu cần.
- **Content negotiation:** `Accept` và `Content-Type` là những header khác nhau; test preflight/CORS và converter nếu API được gọi từ browser.

### 9.1. Không mở endpoint mutation vô điều kiện trong production

Demo cố tình cho phép POST/DELETE để tập trung vào ServerRequest/ServerResponse; trong ứng dụng thật phải thêm Spring Security, kiểm tra quyền theo resource/tenant và CSRF khi phù hợp. Đừng copy bộ demo lên internet như API production mà không có các lớp kiểm soát này.

### 9.2. Lỗi CORS không phải lỗi routing

Nếu browser báo CORS, không vội sửa `RouterFunction.GET(...)`. Kiểm tra `OPTIONS` preflight, allowed origins/methods/headers và Spring Security filter order. HTTP client ngoài browser có thể gọi route dù browser JavaScript không được đọc response; CORS không thay authentication.

### 9.3. Đừng cho client quyết định ID/owner một cách mù quáng

Khi POST body:

```json
{"title":"My note","ownerId":"other-user","role":"ADMIN"}
```

Nếu handler chỉ dùng `CreateNote(String title)`, những fields không thuộc contract không nên được sử dụng để đặt ownership/quyền. Trong sản phẩm thật, chính sách xử lý unknown JSON properties cần được định nghĩa, và identity của owner phải lấy từ principal đáng tin cậy, không từ body.

## 10. Demo hoàn chỉnh: Spring Boot 3, Java 17, functional CRUD

Ứng dụng nhỏ có:

- `GET /api/notes`: danh sách Notes theo ID.
- `GET /api/notes/search?q=Spring`: tìm theo title; đặt route `search` trước `{id}`.
- `GET /api/notes/{id}`: trả một note hoặc 400/404.
- `POST /api/notes`: nhận JSON `{"title":"..."}`, validate, trả 201 + Location.
- `DELETE /api/notes/{id}`: xóa, trả 204 hoặc 404.
- `HandlerFilterFunction` kiểm tra một **demo header** và có thể trả 429 (không phải security control).
- `GET /annotated/ping` dùng `@RestController` để chứng minh hai style cùng chạy trong một ứng dụng.

Dùng Spring Boot **3.3.13**, Java **17+**, Maven. Data store dùng `ConcurrentHashMap` và `AtomicLong` để không cần cài DB; dữ liệu sẽ mất khi ứng dụng restart.

### 10.1. Cấu trúc project

```text
webmvc-fn-demo/
├── pom.xml
└── src/
    ├── main/java/example/FnMvcApplication.java
    └── test/java/example/FnMvcApplicationTest.java
```

### 10.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>webmvc-fn-demo</artifactId>
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

### 10.3. FnMvcApplication.java

Toàn bộ code dưới đây nằm trong một file. Chỉ main class là public top-level class; DTO records là public nested types, giúp Jackson đọc/ghi dễ dàng. `NoteHandlers` và `RouteConfig` là package-private classes được Spring component scan tìm thấy.

```java
package example;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootApplication
public class FnMvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(FnMvcApplication.class, args);
    }

    public record Note(long id, String title) {}
    public record CreateNote(String title) {}
}

@Configuration
class RouteConfig {

    @Bean
    RouterFunction<ServerResponse> noteRoutes(NoteHandlers handler) {
        return RouterFunctions.route()
            // Cụ thể trước; /search không bị route /{id} bắt.
            .GET("/api/notes/search", handler::search)
            .GET("/api/notes/{id}", handler::get)
            .GET("/api/notes", handler::list)
            .POST("/api/notes", handler::create)
            .DELETE("/api/notes/{id}", handler::delete)
            .filter((request, next) -> {
                // Chỉ để học cơ chế short-circuit,
                // KHÔNG dùng header này cho security.
                String mode = request.headers()
                        .firstHeader("X-Demo-Flow");
                if ("reject".equals(mode)) {
                    return ServerResponse.status(
                            HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", "demo_filtered"));
                }
                return next.handle(request);
            })
            .build();
    }
}

@Component
class NoteHandlers {

    private final Map<Long, FnMvcApplication.Note> notes =
            new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(2);

    NoteHandlers() {
        notes.put(1L, new FnMvcApplication.Note(1L, "Spring MVC"));
    }

    ServerResponse list(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(sortedNotes(""));
    }

    ServerResponse search(ServerRequest request) {
        String q = request.queryParam("q")
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(sortedNotes(q));
    }

    private List<FnMvcApplication.Note> sortedNotes(String query) {
        return notes.values().stream()
                .filter(note -> note.title()
                        .toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingLong(
                        FnMvcApplication.Note::id))
                .toList();
    }

    ServerResponse get(ServerRequest request) {
        Long id = parseId(request);
        if (id == null) {
            return badId();
        }

        FnMvcApplication.Note note = notes.get(id);
        if (note == null) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }

    ServerResponse create(ServerRequest request) throws Exception {
        FnMvcApplication.CreateNote command =
                request.body(FnMvcApplication.CreateNote.class);

        if (command == null || command.title() == null ||
                command.title().isBlank()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "title_required"));
        }

        String title = command.title().trim();
        if (title.length() > 120) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "title_too_long"));
        }

        long id = nextId.getAndIncrement();
        FnMvcApplication.Note note =
                new FnMvcApplication.Note(id, title);
        notes.put(id, note);

        return ServerResponse.created(
                    URI.create("/api/notes/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }

    ServerResponse delete(ServerRequest request) {
        Long id = parseId(request);
        if (id == null) {
            return badId();
        }

        FnMvcApplication.Note removed = notes.remove(id);
        return removed != null
                ? ServerResponse.noContent().build()
                : ServerResponse.notFound().build();
    }

    private Long parseId(ServerRequest request) {
        try {
            return Long.valueOf(request.pathVariable("id"));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ServerResponse badId() {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "invalid_id"));
    }
}

@RestController
class PingController {
    @GetMapping("/annotated/ping")
    Map<String, String> ping() {
        return Map.of("style", "annotated");
    }
}
```

#### Giải thích từng vai trò

**FnMvcApplication:** bật Spring Boot và định nghĩa hai Java records. `Note` là response DTO; `CreateNote` là request DTO chỉ chấp nhận title ở tầng handler.

**RouteConfig:** bean `noteRoutes` là entrypoint đăng ký functional endpoints. Spring tự detect bean, không cần tự instantiate `RouterFunctionMapping`. Static route `/search` đứng trước variable route `/{id}`. `HandlerFilterFunction` ở cuối builder áp dụng cho routes thuộc builder này; header demo chỉ dùng để chứng minh filter có thể không gọi next.

**NoteHandlers:** chứa business logic đơn giản của từng route. Nó đọc `ServerRequest`, parse query/path/body rồi trả `ServerResponse` với status và content type rõ. `ConcurrentHashMap` bảo đảm thao tác map cơ bản an toàn khi nhiều request, nhưng **không** thay thế database transaction, policy cho nhiều tenants hoặc idempotency của POST.

**PingController:** chứng minh annotated style và functional style dùng chung DispatcherServlet. Không đăng ký hai handlers cùng URL để tránh bài toán precedence ngoài trọng tâm.

### 10.4. Chạy ứng dụng

Tại thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Yêu cầu Maven/JDK 17+ và kết nối để tải dependencies nếu chúng chưa có ở local repository.

Gọi GET:

```bash
curl -i http://localhost:8080/api/notes
curl -i http://localhost:8080/api/notes/1
curl -i "http://localhost:8080/api/notes/search?q=Spring"
curl -i http://localhost:8080/annotated/ping
```

Các body JSON kỳ vọng trong trạng thái ứng dụng mới khởi động:

```json
[{"id":1,"title":"Spring MVC"}]
```

GET ID 1:

```json
{"id":1,"title":"Spring MVC"}
```

Search:

```json
[{"id":1,"title":"Spring MVC"}]
```

Annotated ping:

```json
{"style":"annotated"}
```

Create:

```bash
curl -i -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title":"WebMvc.fn"}'
```

Kết quả dự kiến ở app mới chạy và chưa có POST khác: status 201, header `Location: /api/notes/2` và body:

```json
{"id":2,"title":"WebMvc.fn"}
```

Nếu có nhiều POST, số ID sẽ tăng; **không** hardcode ID 2 vào client production. Dùng Location thực được server trả.

Bad request:

```bash
curl -i -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{"title":"   "}'
```

Kỳ vọng HTTP 400 với JSON:

```json
{"error":"title_required"}
```

Xóa:

```bash
curl -i -X DELETE http://localhost:8080/api/notes/2
```

Kỳ vọng 204 nếu ID 2 tồn tại ở server đang chạy. Sau đó `GET /api/notes/2` trả 404. Demo không có database nên restart app sẽ đưa seed ID 1 trở lại.

Filter demo:

```bash
curl -i http://localhost:8080/api/notes \
  -H "X-Demo-Flow: reject"
```

Kỳ vọng status 429 và:

```json
{"error":"demo_filtered"}
```

Đây là **middleware demonstration**; header do client gửi không có ý nghĩa bảo mật. Các lệnh cURL dùng nối dòng của POSIX shell; trên Windows PowerShell dùng `curl.exe` và cách xuống dòng tương ứng.

## 11. MockMvc: kiểm thử functional routes thật sự

Sử dụng Spring Boot test context vì `@SpringBootTest` nạp `RouterFunctionMapping`, `HandlerFunctionAdapter`, handlers và converters. `@AutoConfigureMockMvc` cho phép gọi MVC mà không cần mở cổng network. Mỗi test chỉ assert contract cần thiết, không phụ thuộc thứ tự JSON keys.

File `src/test/java/example/FnMvcApplicationTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FnMvcApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void seededNoteCanBeRead() throws Exception {
        mvc.perform(get("/api/notes/1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("Spring MVC"));
    }

    @Test
    void listContainsSeededNote() throws Exception {
        mvc.perform(get("/api/notes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void specificSearchRoutePrecedesIdVariable() throws Exception {
        mvc.perform(get("/api/notes/search")
                    .param("q", "spring"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Spring MVC"));
    }

    @Test
    void invalidPathIdGives400() throws Exception {
        mvc.perform(get("/api/notes/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_id"));
    }

    @Test
    void unknownIdGives404() throws Exception {
        mvc.perform(get("/api/notes/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201LocationAndReadableResource()
            throws Exception {
        MvcResult created = mvc.perform(post("/api/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"A new note\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                    HttpHeaders.LOCATION,
                    startsWith("/api/notes/")))
            .andExpect(jsonPath("$.title").value("A new note"))
            .andReturn();

        String location = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("A new note"));
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        mvc.perform(post("/api/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error")
                    .value("title_required"));
    }

    @Test
    void functionalFilterCanShortCircuit() throws Exception {
        mvc.perform(get("/api/notes")
                    .header("X-Demo-Flow", "reject"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("demo_filtered"));
    }

    @Test
    void annotatedAndFunctionalHandlersCoexist() throws Exception {
        mvc.perform(get("/annotated/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.style").value("annotated"));
    }
}
```

Các tests không giả định ID mới luôn bằng 2; case create lấy `Location` từ response rồi GET resource mới. Test list chỉ assert note seeded ở vị trí đầu vì danh sách được sort theo ID. Nếu tích hợp test parallelization hoặc stateful persistence thật, cần data isolation/cleanup rõ.

**Test thứ tự route rất quan trọng:** `/api/notes/search` phải match route tường minh trước `/api/notes/{id}`; nếu đảo thứ tự, handler get có thể parse chữ `search` thành số và trả 400, làm test search thất bại.

### 11.1. Bổ sung bài test DELETE

Để xác nhận 204 và 404 sau xóa mà không dựa vào seeded resource:

```java
@Test
void deleteCreatedNote() throws Exception {
    MvcResult created = mvc.perform(post("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Delete me\"}"))
        .andExpect(status().isCreated())
        .andReturn();

    String url = created.getResponse()
            .getHeader(HttpHeaders.LOCATION);

    mvc.perform(delete(url))
        .andExpect(status().isNoContent());

    mvc.perform(get(url))
        .andExpect(status().isNotFound());
}
```

Đặt method này vào class test phía trên để có đủ imports đã khai báo. Khi đó suite có **10 tests**. Nội dung main application đã hỗ trợ route DELETE.

### 11.2. MockMvc kiểm thử được gì, không kiểm thử được gì?

MockMvc xác nhận route registration/selection, HTTP converters, response status/body và composition theo Spring MVC. Nó không mô phỏng đầy đủ TLS, reverse proxy, Servlet container actual network buffering, connection timeouts hay browser CORS enforcement.

Với ứng dụng production, thêm các bài integration tests chạy embedded server trên random port để kiểm tra forwarded headers, network status, caching, cookie, filter mapping và Spring Security thật.

## 12. Gỡ lỗi WebMvc.fn theo từng tầng

### 12.1. Route không match

Tình huống:

```text
GET /api/notes/search → 400 invalid_id
```

Nguyên nhân hay gặp: `GET /api/notes/{id}` đứng trước route `search`. Sửa bằng cách đặt route cụ thể trước. Đừng đoán rằng Spring sẽ tự chọn route cụ thể nhất như ở annotated RequestMapping.

### 12.2. Chọn nhầm WebFlux import

Nếu IDE auto-import:

```java
org.springframework.web.reactive.function.server.ServerRequest
```

thì code của bạn đang dùng type reactive, không phù hợp với `org.springframework.web.servlet.function.RouterFunction`. Sửa package nhất quán theo WebMvc.fn; không chèn `Mono<ServerResponse>` vào method handler đồng bộ rồi kỳ vọng compile.

### 12.3. JSON body không đọc được

Checklist:

1. `spring-boot-starter-web` có Jackson JSON converter phù hợp không?
2. Client có gửi `Content-Type: application/json` không?
3. Body JSON có đúng syntax và schema DTO không?
4. Record/class có constructors/accessors để converter tạo object không?
5. Handler có đọc body một lần hay đã tiêu thụ stream trước?
6. Khi binder lỗi, server có chuyển exception thành error response với code rõ ràng không?

### 12.4. Functional filter gây 429 không mong muốn

Nếu filter ở root builder, mọi routes xây từ builder có thể chịu filter đó. Kiểm tra predicate/header và scope. Nếu muốn áp cho một nhóm con, dùng nested builder/filter đúng vị trí thay vì lọc root quá rộng.

### 12.5. CORS hoặc 401/403 xuất hiện

Chúng có thể đến từ Servlet/Spring Security layers trước khi handler chạy. Không sửa route để “cho qua” security. Kiểm tra CORS preflight, authentication, roles/ownership và CSRF cùng configured filter chain.

### 12.6. Đường URL trả 404 từ static ResourceHandler

Trong Spring Boot, static resource handler có thể match wildcard `/**` sau khi không có controller/functional route. Vì vậy 404 có thể là “resource not found” thay vì “không có handler nào”, tùy configuration. Gỡ lỗi theo HandlerMapping được chọn, không chỉ HTTP status.

### 12.7. Response không có Content-Type JSON

Chỉ trả Map/object mà không đặt content type có thể để message converter chọn mặc định theo Accept negotiation; để API demo ổn định, chúng ta dùng `.contentType(MediaType.APPLICATION_JSON)` trên các JSON responses. Với application thực, thiết kế Accept/produces behavior rõ ràng.

### 12.8. Performance không tăng sau khi chuyển từ @RestController

WebMvc.fn là cách **biểu đạt routing/handling**, không bảo đảm query nhanh hơn, DB non-blocking hoặc lượng object allocations ít hơn đáng kể. Profiling DB latency, serialization, request concurrency, CPU và garbage collection trước khi quyết định refactor.

## 13. Lỗi phổ biến và cách tránh

| Nhầm lẫn | Hậu quả | Hướng đúng |
| --- | --- | --- |
| “WebMvc.fn chính là WebFlux.fn” | Import sai, kỳ vọng Mono/Flux | Dùng Servlet package cho WebMvc.fn |
| “HandlerFunction tự chọn URL” | Thiếu routing bean, endpoint 404 | Đăng ký RouterFunction bean |
| “RouterFunction là controller method” | Không phân biệt routing với handling | Router chọn handler, handler dựng response |
| “ServerRequest là HttpServletRequest y hệt” | Bỏ qua wrapper/convenience API | Dùng ServerRequest, chỉ xuống servletRequest khi cần |
| “ServerResponse giống HttpServletResponse mutable” | Mutate sai contract | Dùng immutable-style builder |
| “GET /{id} sau /search cũng tự ưu tiên search” | Search bị parse làm ID | Đặt route cụ thể trước trong RouterFunction |
| “Dùng body DTO là tự @Valid” | Invalid data đi vào service | Validation explicit |
| “Client cho ownerId thì cứ tin” | Mass assignment/IDOR | Ownership từ authenticated context |
| “Middleware filter là Spring Security” | Bỏ sót auth/CSRF | Dùng SecurityFilterChain |
| “Thêm route filter root chỉ ảnh hưởng route gần nhất” | Không mong muốn filter áp cả nhóm | Hiểu scope builder/nesting |
| “404 có nghĩa chắc chắn RouterFunctionMapping không match” | Gỡ lỗi sai layer | Xem HandlerMapping/static fallback |
| “201 không cần Location” | Client khó định vị resource mới | Trả Location khi phù hợp |
| “Trả 204 vẫn có JSON body” | HTTP semantics sai | No content thật sự |
| “Cho POST retry vô tư” | Có thể tạo duplicate | Idempotency key/logic nếu cần |
| “Functional style tự giải quyết error handling” | Leak stack traces/unstructured errors | Định nghĩa error contract và test |
| “Functional API non-blocking end-to-end” | Capacity plan sai | Servlet stack có blocking I/O |
| “@ControllerAdvice áp 100% như @RequestBody” | Validation/advice không như dự kiến | Test adapter/converter/error flow thực tế |
| “Route filter return 429 theo header demo là rate limiting thật” | Security policy sai | Dùng rate limiter thực với identity/trust boundary |
| “Có ConcurrentHashMap là có transaction” | Business race/không persistent | DB transaction, optimistic locking |
| “Test hardcode ID mới = 2” | Flaky khi test order hoặc concurrency | Lấy Location/ID từ response |
| “Kiểm thử body JSON bằng chuỗi y hệt” | Thất bại vô ích do key order | Dùng jsonPath/JSON structural assertions |
| “CORS OK tức user được phép POST” | Nhầm browser policy với authorization | Tách CORS, auth, role, CSRF |

## 14. Checklist áp dụng thực tế

Khi bắt đầu một module WebMvc.fn:

1. **Chọn stack:** chắc chắn là Servlet MVC hay reactive WebFlux, imports nhất quán.
2. **Router structure:** gom routes theo domain, path/method/media predicates minh bạch.
3. **Precedence:** đặt routes cụ thể trước wildcard/path-variable rộng.
4. **Handler functions:** giữ nhỏ, chỉ đọc request, validate, gọi service, dựng response.
5. **DTO schema:** tách request/response DTO khỏi JPA entities; tránh mass assignment.
6. **HTTP status:** GET 200/404, POST 201+Location, DELETE 204, validation 400 và lỗi policy rõ.
7. **Business service:** transactions, optimistic concurrency, ownership checks, multi-tenant isolation.
8. **Security:** CORS/Csrf/Spring Security filters, method/object authorization và private cache policy.
9. **Error mapping:** chuẩn hóa JSON error code, tránh leak stack trace; test body parse/converter errors.
10. **Cross-cutting:** phân biệt HandlerFilterFunction với Servlet Filter/MVC interceptor.
11. **Composition:** tránh nhiều RouterFunction beans overlapping paths mà không biết order.
12. **Concurrency:** kiểm tra thread-safety và resource exhaustion nếu handler gọi blocking services.
13. **Testing:** test static-vs-variable route, 201/Location, 400, 404, 204, functional middleware.
14. **Observability:** ghi route matched, request ID, status, p95 latency, security denials không lộ secrets.
15. **API docs:** xem generator/OpenAPI tooling có hỗ trợ functional routes và khai báo bổ sung nếu cần.

### 14.1. Khi nào nên chọn WebMvc.fn?

Phù hợp khi bạn muốn routes khai báo tập trung, composable predicates, handler classes đơn giản, middleware áp một group, hoặc một codebase ưu tiên functional style.

Không nhất thiết phù hợp nếu cả nhóm đã dùng annotated controllers, validation/exception advice/tools quen thuộc và chuyển style chỉ tăng rủi ro refactor mà không cải thiện rõ readability. **Spring hỗ trợ cả hai**, nên một hệ thống có thể dùng từng style ở module thích hợp.

## 15. Tự kiểm tra

**1.** `HandlerFunction<ServerResponse>` nhận và trả gì?  
**Đáp án:** nhận `ServerRequest` và trả `ServerResponse`.

**2.** `RouterFunction` thực hiện nhiệm vụ gì?  
**Đáp án:** chọn HandlerFunction phù hợp theo predicate/method/path, trả Optional khi match.

**3.** WebMvc.fn có dùng Reactor Mono theo mặc định không?  
**Đáp án:** không; Servlet `HandlerFunction` trả ServerResponse trực tiếp.

**4.** Package ServerRequest của WebMvc.fn là gì?  
**Đáp án:** `org.springframework.web.servlet.function.ServerRequest`.

**5.** Lấy ID từ path variable thế nào?  
**Đáp án:** `request.pathVariable("id")`, rồi parse/validate khi cần.

**6.** Lấy query `?q=java` thế nào?  
**Đáp án:** `request.queryParam("q").orElse("")`.

**7.** Đọc JSON thành DTO thế nào?  
**Đáp án:** `request.body(CreateNote.class)` khi converter hỗ trợ.

**8.** Response 201 có Location làm sao?  
**Đáp án:** `ServerResponse.created(uri).body(object)`.

**9.** Trả 204 không body thế nào?  
**Đáp án:** `ServerResponse.noContent().build()`.

**10.** Tại sao route `/search` phải đứng trước `/{id}`?  
**Đáp án:** RouterFunction xét routes theo thứ tự; first match wins.

**11.** RequestPredicates có thể kiểm tra gì ngoài URL?  
**Đáp án:** HTTP method, Accept, Content-Type và các điều kiện custom.

**12.** `HandlerFilterFunction` khác Servlet Filter thế nào?  
**Đáp án:** middleware của RouterFunction, không có phạm vi toàn Servlet container.

**13.** Dùng `HandlerFilterFunction` để tin `X-User-Role` từ client có an toàn không?  
**Đáp án:** không; authentication/authorization cần trust boundary và Spring Security.

**14.** Có phải Java record với @NotBlank tự được validate khi dùng `request.body(...)`?  
**Đáp án:** không mặc nhiên; cần trigger Validator/binding phù hợp.

**15.** Một bean RouterFunction có cần @EnableWebMvc trên Spring Boot không?  
**Đáp án:** thông thường không; MVC auto-configuration hỗ trợ.

**16.** Functional route và @RestController có thể cùng tồn tại không?  
**Đáp án:** có trên DispatcherServlet, nhưng tránh overlapping URLs không kiểm soát.

**17.** Vì sao `ConcurrentHashMap` không thay thế DB transaction?  
**Đáp án:** map chỉ cung cấp thao tác concurrency ở memory; không có durability/transaction nghiệp vụ.

**18.** Test nào xác nhận functional route không bị `/{id}` bắt nhầm?  
**Đáp án:** GET `/api/notes/search?q=Spring` phải trả danh sách thành công.

**19.** WebMvc.fn có tự làm JDBC non-blocking không?  
**Đáp án:** không; đây là functional routing/handling trên Servlet stack.

**20.** Response 404 có luôn nghĩa không có HandlerFunction matched không?  
**Đáp án:** không; handler có thể tự trả 404, hoặc static fallback báo missing resource.

## 16. Nguồn tham khảo chính thức

- [Spring Framework 6.2 — Web MVC Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-functional.html): mô hình WebMvc.fn, RouterFunction, HandlerFunction, ServerRequest/Response, predicates, nesting/filtering.
- [Spring Framework 6.2 — Servlet Functional API package](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/web/servlet/function/package-summary.html): interfaces/builder classes.
- [Spring Framework 6.2 — RouterFunctionMapping Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/web/servlet/function/support/RouterFunctionMapping.html): detect/combine RouterFunction beans.
- [Spring Framework 6.2 — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): so sánh Servlet MVC với reactive API, tránh import nhầm.
- [Spring Framework 6.2 — Spring MVC Test Framework](https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html): MockMvc testing.
- [Spring Boot 3.3 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/servlet.html): MVC auto-configuration, Servlet stack.
- [HTTP Semantics — RFC 9110](https://www.rfc-editor.org/rfc/rfc9110): status codes và request/response semantics.

**Ghi nhớ:** `RouterFunction` quyết định **route nào phù hợp**; `HandlerFunction` thực hiện **nghiệp vụ/điều phối** trên `ServerRequest`; `ServerResponse` biểu diễn **kết quả HTTP**. Bộ ba này là **functional style trên Spring MVC Servlet**, có thể đứng cạnh annotated controllers và dùng chung Servlet/security infrastructure, nhưng vẫn cần validation, phân quyền, error handling, tests và thiết kế dữ liệu đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-functional-endpoints-003">RouterFunction, RequestPredicate và khai báo tuyến lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# RouterFunction, RequestPredicate và khai báo tuyến lồng nhau trong Spring MVC

## 1. Functional MVC là gì?

Trong **Spring MVC**, bạn thường xây HTTP API bằng `@RestController` và `@GetMapping`:

~~~java
@RestController
class BookController {
    @GetMapping("/api/books")
    List<Book> books() {
        return bookService.findAll();
    }
}
~~~

Mã trên là fragment, vì `Book` và `bookService` chưa được định nghĩa. Spring MVC còn hỗ trợ mô hình **functional endpoints**, thường gọi **WebMvc.fn**. Thay vì gắn annotation vào từng handler method, bạn khai báo việc chọn handler như **hàm ánh xạ từ HTTP request tới handler**.

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route(
                RequestPredicates.GET("/api/health"),
                request -> ServerResponse.ok().body("OK"));
~~~

Đây là một route độc lập dùng API `org.springframework.web.servlet.function`. Nó kết nối:
- **`RequestPredicate`**: điều kiện nhận diện request, ví dụ method GET và path `/api/health`.
- **`HandlerFunction<ServerResponse>`**: xử lý request đã match, tạo `ServerResponse`.
- **`RouterFunction<ServerResponse>`**: tìm handler phù hợp với request.
- **`ServerRequest`**: đối tượng request kiểu functional, cung cấp path variables, query parameters, headers, body.
- **`ServerResponse`**: builder cho status, headers, body hoặc rendering response.

Các route được **xét theo thứ tự khai báo**: nếu route đầu match thì nó được chọn trước. Khác với annotation MVC nơi framework dùng độ đặc hiệu của mapping để chọn handler, trong functional routing thứ tự composition là một phần của thiết kế. Vì thế `/api/books/featured` phải được đặt trước route tổng quát `/api/books/{id}` nếu cả hai có thể match.

### Mục tiêu học tập

Sau bài này bạn có thể:
1. Phân biệt WebMvc.fn Servlet với WebFlux.fn reactive.
2. Hiểu `RouterFunction`, `HandlerFunction`, `RequestPredicate`, `ServerRequest` và `ServerResponse`.
3. Đọc và viết predicates theo HTTP method, path, `Accept`, `Content-Type`, header, query/form parameter.
4. Dùng `and`, `or`, `negate`, `andRoute`, `RouterFunctions.route()` builder và các phương thức `GET`/`POST`.
5. Khai báo route **lồng nhau** bằng `path(prefix, builder)` và `nest(predicate, builder)`, không lặp path và ràng buộc header.
6. Hiểu cách phân biệt handler không match, handler lỗi, request body không hợp lệ, thiếu quyền và exception mapping.
7. Xây ứng dụng Java 17+ Spring Boot 3.3.5 có BookStore, BookHandler, nested RouterFunction và MockMvc integration tests.
8. Biết khi nào nên dùng functional MVC, khi nào annotation controller dễ bảo trì hơn.

**Phiên bản:** WebMvc.fn có từ Spring Framework 5.2; ví dụ chạy trên Spring Boot 3.3.5 / Spring Framework 6.1.x. Một số tài liệu Spring Framework “current” đã chuyển sang các API mới hơn; ví dụ dưới chỉ dùng API hiện hữu ở dòng 6.1.

## 2. WebMvc.fn thuộc Servlet stack, không phải WebFlux.fn

Hai hệ có tên class rất giống, nhưng **package khác**:

| Servlet WebMvc.fn | Reactive WebFlux.fn |
| --- | --- |
| `org.springframework.web.servlet.function.RouterFunction` | `org.springframework.web.reactive.function.server.RouterFunction` |
| `org.springframework.web.servlet.function.ServerRequest` | `org.springframework.web.reactive.function.server.ServerRequest` |
| `org.springframework.web.servlet.function.ServerResponse` | `org.springframework.web.reactive.function.server.ServerResponse` |
| Handler thường trả `ServerResponse` đồng bộ | Handler thường trả `Mono<ServerResponse>` |
| Servlet container + `DispatcherServlet` | Reactive HTTP runtime và reactive adapters |
| `spring-boot-starter-web` | `spring-boot-starter-webflux` |

**Đừng để IDE tự import nhầm `org.springframework.web.reactive.function.server.*`** vào ứng dụng Spring MVC. Java generic signatures, body builders và test setup của WebFlux khác. Dùng WebMvc.fn không có nghĩa tất cả I/O trở nên non-blocking; `ServerRequest.body(Class)` và JDBC/HTTP client đồng bộ vẫn có thể blocking trên servlet thread.

Ứng dụng Spring Boot có thể dùng WebMvc.fn cùng `@RestController` nếu muốn, nhưng phải quản lý URL conflicts, mapping order, SecurityFilterChain, error handling và tài liệu API. Không biến việc chọn hai kiểu API thành một công cụ “tự chữa” lỗi route.

## 3. Luồng xử lý của một functional MVC endpoint

~~~text
HTTP client
   |
   v
Servlet container / filters / Spring Security (nếu có)
   |
   v
DispatcherServlet
   |
   v
RouterFunctionMapping
   |
   +-- RouterFunction #1: match RequestPredicate?
   |       | yes -> HandlerFunction
   |       + no
   |
   +-- RouterFunction #2: match?
   |       | yes -> HandlerFunction
   |       + no -> Các HandlerMapping khác / 404 theo cấu hình
   |
   v
HandlerFunctionAdapter / ServerRequest
   |
   v
HandlerFunction.handle(request)
   |
   v
ServerResponse (status + headers + body)
   |
   v
HTTP message converters / response writing
   |
   v
HTTP client
~~~

Sơ đồ giản lược: Spring MVC có nhiều `HandlerMapping` (annotated methods, functional routes, static resources...) và thứ tự `order` riêng. `RouterFunctionMapping` tìm handler từ router functions; `HandlerFunctionAdapter` gọi functional handler; response sau đó được viết qua MVC infrastructure. Không tuyên bố functional endpoints luôn thắng hoặc luôn thua mọi annotation controller ở mọi cấu hình — đặc biệt khi tự thay đổi order các mapping beans.

### Ba giai đoạn quan trọng

1. **Routing:** `RequestPredicate` quyết định request nào đi vào handler. Đây chưa phải authentication hay validation của body.
2. **Handling:** `HandlerFunction` đọc dữ liệu qua `ServerRequest`, gọi service và tạo một `ServerResponse`.
3. **Writing:** Spring Web MVC viết status, headers, body bằng các converter/view facilities phù hợp.

Nếu route **không match**, handler không chạy; một response 404/405/406/415 có thể đến từ cơ chế mapping hoặc error handling. Nếu route **đã match** nhưng handler ném exception, đó là nhánh khác và cần error mapping riêng. Đừng coi mọi 404 là cùng một lỗi.

## 4. `RequestPredicate` là gì?

`RequestPredicate` là điều kiện trên request; về ý tưởng nó giống `Predicate<ServerRequest>` nhưng Spring thêm khả năng hỗ trợ composition, path nesting và introspection. Các static factories từ `RequestPredicates`:

| Factory (Servlet MVC) | Dùng để |
| --- | --- |
| `GET("/path")` | Match GET và path |
| `POST("/path")` | Match POST và path |
| `PUT("/path")`, `PATCH("/path")`, `DELETE("/path")` | Match method + path |
| `method(HttpMethod.GET)` | Match method không ràng buộc path |
| `methods(GET,HEAD)` | Match một nhóm methods |
| `path("/api/**")` | Match URL path |
| `accept(MediaType.APPLICATION_JSON)` | Match Accept negotiation |
| `contentType(MediaType.APPLICATION_JSON)` | Match request Content-Type |
| `headers(h -> ...)` | Predicate tùy biến theo request headers |
| `param("view","summary")` | Match request parameter của WebMvc.fn |
| `all()` | Luôn match |
| `predicate.and(other)` | Cả hai phải đúng |
| `predicate.or(other)` | Ít nhất một đúng |
| `predicate.negate()` | Phủ định điều kiện |

**Lưu ý về tên API:** trong `org.springframework.web.servlet.function.RequestPredicates` có `param(...)`. Tài liệu `WebFlux.fn` có `queryParam(...)` nhưng **đừng copy `RequestPredicates.queryParam(...)` của reactive package vào Servlet WebMvc.fn 6.1**. `ServerRequest.param("name")` đọc request parameter; nó có thể bao gồm query/form parameters theo Servlet semantics.

### 4.1. Match method + path

~~~java
RequestPredicate findBook =
        RequestPredicates.GET("/api/books/{id}");
~~~

`GET("/api/books/{id}")` là tổ hợp kiểm method GET và path pattern. Khi handler được chọn, `request.pathVariable("id")` lấy giá trị đã match.

Không tự suy ra `{id}` là số: path variable bắt đầu là chuỗi; handler cần parse và validate kiểu/giới hạn.

### 4.2. Kết hợp predicate bằng `and`

~~~java
RequestPredicate onlyJson =
        RequestPredicates.GET("/api/books")
                .and(RequestPredicates.accept(MediaType.APPLICATION_JSON));
~~~

Route chỉ match nếu cả GET path và client Accept phù hợp JSON. `Accept` là yêu cầu định dạng **response**, không phải mô tả request body.

### 4.3. Kết hợp bằng `or` và `negate`

~~~java
RequestPredicate simpleReads =
        RequestPredicates.method(HttpMethod.GET)
                .or(RequestPredicates.method(HttpMethod.HEAD));

RequestPredicate notAdminPath =
        RequestPredicates.path("/admin/**").negate();
~~~

Đây là fragments cần imports tương ứng. `or`/`negate` thay đổi logic match, **không tạo security boundary**. Một `notAdminPath` route không thay Spring Security authorization cho `/admin/**`.

### 4.4. Header predicate

~~~java
RequestPredicate versionTwo = RequestPredicates.headers(
        headers -> "2".equals(headers.firstHeader("API-Version")));
~~~

Nếu browser từ origin khác gửi custom header `API-Version`, nó thường yêu cầu CORS preflight. Router match version header không tự cấu hình CORS hoặc cấp quyền user.

### 4.5. Request parameter predicate

~~~java
RequestPredicate summary = RequestPredicates.param("view", "summary");
~~~

Có thể kết hợp với path GET:

~~~java
RouterFunction<ServerResponse> route =
        RouterFunctions.route(
                RequestPredicates.GET("/api/books/featured").and(summary),
                handler::featured);
~~~

Nếu `view` thiếu, route này không match; một route tổng quát sau nó **có thể** match cùng path. Đó là lý do cần thứ tự hợp lý và fallback rõ, không giả định predicate không match tự sinh một lỗi 400 theo mong muốn.

### 4.6. Content-Type và Accept khác nhau

| HTTP header | Predicate | Ý nghĩa |
| --- | --- | --- |
| `Accept: application/json` | `accept(APPLICATION_JSON)` | Client muốn **nhận** JSON |
| `Content-Type: application/json` | `contentType(APPLICATION_JSON)` | Client đang **gửi body** JSON |

Với POST/PUT có JSON body, `contentType` thường hữu ích. Với GET trả JSON, `accept` thường phù hợp. Cả hai **chỉ là điều kiện routing**; nếu content là JSON sai syntax, handler/body converter vẫn có thể báo lỗi sau khi đã match.

## 5. Khai báo route bằng `RouterFunctions.route(predicate, handler)`

Cách tối giản:

~~~java
RouterFunction<ServerResponse> hello =
        RouterFunctions.route(
                RequestPredicates.GET("/hello"),
                request -> ServerResponse.ok().body("Hello MVC.fn"));
~~~

Trường hợp chỉ một route, cách này dễ đọc. Khi có nhiều handlers, dùng `RouterFunction.andRoute` để nối:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route(
                RequestPredicates.GET("/hello"),
                request -> ServerResponse.ok().body("Hello"))
            .andRoute(
                RequestPredicates.GET("/ping"),
                request -> ServerResponse.ok().body("pong"));
~~~

Các snippets ở đây chưa khai báo class/bean; để Spring Boot phát hiện routes cần đăng ký `RouterFunction<ServerResponse>` thành bean hoặc tích hợp theo cơ chế tương đương. Ví dụ đầy đủ ở mục 10 có `@Bean` rõ ràng.

### 5.1. Hàm router chọn handler, không tự thực thi logic nghiệp vụ

`RouterFunction.route(request)` trả về một `Optional<HandlerFunction<...>>` theo contract; handler mới thực hiện công việc khi MVC gọi nó. Tránh database/network call side-effect bên trong `RequestPredicate` hoặc lúc đăng ký route, vì predicate có thể được kiểm nhiều lần và không đại diện cho một business transaction.

### 5.2. Composition với `and`

~~~java
RouterFunction<ServerResponse> composed =
        firstRoutes.and(secondRoutes);
~~~

Đây là fragment giả định `firstRoutes`/`secondRoutes` đã được định nghĩa. `and` sẽ thử router đầu rồi tới router sau nếu cần, giữ thứ tự route. Có thể dùng `andRoute(predicate, handler)` để tiện thêm route đơn lẻ.

## 6. Builder DSL: `RouterFunctions.route()`

Khi nhiều path/methods, builder DSL sáng sủa hơn:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .GET("/api/health", handler::health)
                .GET("/api/books/{id}", handler::byId)
                .GET("/api/books", handler::all)
                .POST("/api/books",
                        RequestPredicates.contentType(
                                MediaType.APPLICATION_JSON),
                        handler::create)
                .build();
~~~

`handler` là bean ứng dụng có các methods `ServerResponse health(ServerRequest)`, `byId(...)`, `all(...)`, `create(...)`; đây là fragment rút gọn, code đầy đủ nằm trong mục 10.

Mỗi builder method thể hiện method, path, predicate bổ sung (nếu có), handler. Cuối cùng `build()` trả một `RouterFunction<ServerResponse>` hoàn chỉnh.

### 6.1. Thứ tự route là quy tắc bắt buộc

Ví dụ **sai**:

~~~java
RouterFunctions.route()
        .GET("/api/books/{id}", handler::byId)
        .GET("/api/books/featured", handler::featured)
        .build();
~~~

`/api/books/featured` có thể bị match bởi `{id}` ở route đầu. Nếu `byId` parse số, bạn có thể nhận lỗi NumberFormatException thay vì response featured.

Sửa lại:

~~~java
RouterFunctions.route()
        .GET("/api/books/featured", handler::featured)
        .GET("/api/books/{id}", handler::byId)
        .build();
~~~

**Quy tắc review:** route tĩnh/cụ thể trước, route variable/wildcard rộng sau; fallback hoặc catch-all phải đứng cuối. Điều này khác annotation-based mappings của `@RequestMappingHandlerMapping` vốn lựa chọn độ đặc hiệu theo các rule nội bộ.

## 7. Nested routes bằng `path(prefix, builder)`

Nếu API có nhiều routes dưới cùng prefix:

~~~text
GET  /api/health
GET  /api/books
GET  /api/books/{id}
POST /api/books
~~~

Ta có thể tránh lặp `/api` và `/books`:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api", api -> api
                        .GET("/health", handler::health)
                        .path("/books", books -> books
                                .GET("/{id}", handler::byId)
                                .GET("", handler::all)
                                .POST("", handler::create)))
                .build();
~~~

`path("/api", ...)` là một **path prefix grouping**. Trong nhánh này, đường dẫn con được diễn giải tương đối với prefix. `/api/books/{id}` vẫn là URL hoàn chỉnh client gọi; bạn **không gửi `/books/{id}`** chỉ vì trong builder lồng nhau viết vậy.

Điểm cần chú ý: khi có route động và route cụ thể ở cùng prefix, vẫn giữ độ cụ thể theo thứ tự. Để match đúng `GET /api/books`, builder con thường dùng `GET("", handler::all)` như ví dụ chính thức của Spring khi nest path.

### 7.1. Vì sao nested routes hữu ích?

- Tổ chức tuyến theo domain/module/resource.
- Giảm duplication prefix khi đổi `/api/v1` sang `/api/v2`.
- Áp dụng điều kiện chung (ví dụ `Accept`) cho một nhóm routes.
- Gắn filter logging/headers cho một nhánh thay vì toàn bộ API.
- Phân chia route theo module và ghép lại bằng `add` hoặc `path(prefix, supplier)`.

Nhược điểm: nesting quá sâu làm routing khó lần theo, đặc biệt khi có nhiều `nest` và filter scopes. Nên giữ cây route ngắn, đặt tên rõ và có tests chính xác cho từng URL.

### 7.2. Dùng `path` khác `nest(path(...))` như thế nào?

`path("/api", builder)` là tiện ích chuyên cho path prefix. `nest(RequestPredicates.path("/api"), builder)` cho phép nested routing bằng một predicate path. Với nhiều use cases chúng diễn đạt cùng mục tiêu, nhưng `nest` linh hoạt hơn vì có thể dùng **bất kỳ predicate** (headers, Accept...) không chỉ URL prefix.

Không nhầm `nest` với một lần Servlet FORWARD/HTTP redirect. Nó chỉ tổ chức việc route match và handler selection.

## 8. Nested routes bằng `nest(predicate, builder)`

Ngoài path, ta có thể đặt điều kiện dùng chung cho các GET trả JSON:

~~~java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api/books", books -> books
                        .nest(
                                RequestPredicates.accept(
                                        MediaType.APPLICATION_JSON),
                                json -> json
                                        .GET("/{id}", handler::byId)
                                        .GET("", handler::all))
                        .POST("",
                                RequestPredicates.contentType(
                                        MediaType.APPLICATION_JSON),
                                handler::create))
                .build();
~~~

Logic:
- Cả GET `/{id}` và GET `""` phải có Accept phù hợp JSON để match nhánh `nest(accept(...))`.
- POST nằm **ngoài nhánh Accept** nhưng vẫn trong `path("/api/books")`; chỉ yêu cầu Content-Type JSON.
- Nếu client gửi `Accept: text/html` mà không có route khác, GET có thể không match; HTTP status cuối phụ thuộc mapping/error infrastructure, không tự coi như handler trả 406 chắc chắn.
- Bật nested Accept **không** tự làm JSON object serialization; handler còn phải trả `ServerResponse` có body và converter phù hợp.
- Filter gắn vào nested builder có phạm vi khác filter gắn trên parent builder.

### 8.1. Kết hợp nested path và header

Ví dụ nếu một API thay đổi theo header:

~~~java
RouterFunctions.route()
        .path("/api", api -> api
                .nest(
                        RequestPredicates.headers(
                                h -> "2".equals(
                                        h.firstHeader("API-Version"))),
                        v2 -> v2
                                .GET("/books", handler::booksV2)))
        .build();
~~~

Fragment này cần method `booksV2` trong handler. Nếu còn một route `GET("/books")` v1 rộng đứng trước nhánh v2, route v1 có thể match trước; **điều kiện version cụ thể phải đứng trước fallback**. Với API lớn, dùng một chiến lược versioning tường minh và tests thay vì nhồi các predicates tùy biến vào mọi route.

## 9. HandlerFunction, ServerRequest và ServerResponse

### 9.1. `HandlerFunction<ServerResponse>`

Functional interface về bản chất nhận một `ServerRequest` và tạo `ServerResponse`:

~~~java
ServerResponse health(ServerRequest request) {
    return ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("OK");
}
~~~

Không cần `@GetMapping` trên handler method. Router bean liên kết URL với method bằng `handler::health`. Handler vẫn có thể nằm trong Spring-managed `@Component`/`@Service`, dùng constructor injection và gọi application services.

### 9.2. Đọc path và query parameter

~~~java
String rawId = request.pathVariable("id");
long id = Long.parseLong(rawId);

String page = request.param("page").orElse("1");
~~~

Đây là fragments minh họa trong method nhận `ServerRequest request`. `pathVariable` là giá trị path variable của route đã match, không tự là `long`. `param` trả Optional; nếu missing mà gọi `get()` ngay sẽ dẫn đến exception. Parse số và validate range trước khi gọi database.

### 9.3. Đọc headers

~~~java
String requestId = request.headers().firstHeader("X-Request-Id");
MediaType contentType = request.headers().contentType()
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
~~~

`headers().contentType()` trả Optional cho request content type. Header `X-Request-Id` do client gửi không phải bằng chứng danh tính; nếu cần trace ID, nên validate/trust boundary thay vì đưa raw value làm metric label. Đừng log Authorization/Cookie để debug routing.

### 9.4. Đọc body

~~~java
CreateBook dto = request.body(CreateBook.class);
~~~

Đây là fragment cần record/class `CreateBook`, có thể ném lỗi Servlet/IO/conversion. Trong WebMvc.fn, JSON conversion sử dụng `HttpMessageConverter` của Spring MVC. **Chỉ có `contentType(APPLICATION_JSON)` trong route chưa kiểm mọi business rules**, và functional handler không mặc nhiên giống `@Valid @RequestBody` của annotated controller. Hãy validate DTO với code hoặc Jakarta Validator ở service/handler nếu cần.

### 9.5. Tạo response

~~~java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(book);
~~~

~~~java
return ServerResponse.created(URI.create("/api/books/3"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(createdBook);
~~~

~~~java
return ServerResponse.noContent().build();
~~~

Các fragments giả định biến `book` và `createdBook` đã định nghĩa. `ServerResponse` cung cấp fluent builders cho status/headers/body; không trả `ResponseEntity` trực tiếp như kiểu return value chuẩn của WebMvc.fn handler. `ServerResponse.ok().body(...)` ở Servlet API là sync builder, khác `bodyValue(...)` ở WebFlux.fn reactive examples.

### 9.6. So sánh signature annotation và functional

| Annotation MVC | Functional MVC |
| --- | --- |
| `@GetMapping("/{id}")` | `GET("/{id}", handler::byId)` |
| `@PathVariable long id` | `request.pathVariable("id")` rồi parse |
| `@RequestParam String q` | `request.param("q")` |
| `@RequestBody DTO input` | `request.body(DTO.class)` |
| `ResponseEntity.ok(dto)` | `ServerResponse.ok().body(dto)` |
| `@ControllerAdvice`, `@ExceptionHandler` | `onError(...)` trên router builder và MVC exception infrastructure tùy cấu hình |
| `HandlerInterceptor` | `before/after/filter` trên router builder, ngoài các Servlet filters |
| `@RequestMapping` grouping | `path(...)` và `nest(...)` |

Không có nghĩa functional model thiếu tích hợp với Spring Security, MVC message converters hoặc DI. Điểm khác là **cách khai báo routing và handler signature**.

## 10. Ví dụ Java 17 chạy được: API sách dùng nested RouterFunction

Ứng dụng nhỏ minh họa đầy đủ:
- `GET /api/health` → text `OK`.
- `GET /api/books` → JSON danh sách sách.
- `GET /api/books/{id}` → JSON một sách hoặc 404.
- `GET /api/books/featured?view=summary` → JSON sách nổi bật khi tham số khớp.
- `GET /api/books/featured` thiếu `view=summary` → 400 có mã lỗi minh họa.
- `POST /api/books` với JSON body tạo một sách ở in-memory store, HTTP 201 và Location.
- Route `GET` lồng điều kiện `Accept: application/json`, route POST lồng prefix nhưng ràng buộc `Content-Type: application/json`.
- Error mapping `onError(BookMissingException.class, ...)` và `onError(InvalidBookInputException.class, ...)`.

Demo chạy trên **Spring Boot 3.3.5 / Java 17**, sử dụng `spring-boot-starter-web` và không có Spring WebFlux dependency. Data trong bộ nhớ để thấy luồng HTTP; restart sẽ reset. Không coi đây là production database, audit hay authentication.

Cấu trúc:

~~~text
mvc-fn-books/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/mvcfn/
    │   ├── MvcFnBooksApplication.java
    │   ├── data/BookStore.java
    │   └── web/
    │       ├── BookHandler.java
    │       └── BookRoutes.java
    └── test/java/com/example/mvcfn/
        └── BookRoutesTest.java
~~~

### 10.1. Gradle

`settings.gradle`:

~~~groovy
rootProject.name = 'mvc-fn-books'
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

Không thêm `spring-boot-starter-webflux` chỉ vì thấy tên RouterFunction. Trên Boot 3, các import từ `org.springframework.web.servlet.function` là quan trọng nhất.

### 10.2. Main application

`src/main/java/com/example/mvcfn/MvcFnBooksApplication.java`:

~~~java
package com.example.mvcfn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MvcFnBooksApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcFnBooksApplication.class, args);
    }
}
~~~

### 10.3. BookStore: nguồn dữ liệu in-memory thread-safe

`src/main/java/com/example/mvcfn/data/BookStore.java`:

~~~java
package com.example.mvcfn.data;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BookStore {

    public record Book(long id, String title) {}

    private final ConcurrentMap<Long, Book> books =
            new ConcurrentHashMap<>();

    private final AtomicLong ids = new AtomicLong(2);

    public BookStore() {
        books.put(1L, new Book(1L, "Spring Web"));
        books.put(2L, new Book(2L, "Functional MVC"));
    }

    public List<Book> all() {
        return books.values().stream()
                .sorted(Comparator.comparingLong(Book::id))
                .toList();
    }

    public Optional<Book> find(long id) {
        return Optional.ofNullable(books.get(id));
    }

    public Book create(String title) {
        long id = ids.incrementAndGet();
        Book book = new Book(id, title);
        books.put(id, book);
        return book;
    }
}
~~~

Đây là state in-memory dùng để chứng minh route/handler. `ConcurrentHashMap` và `AtomicLong` tránh lỗi phổ biến khi nhiều request tạo ID đồng thời **trong một JVM**. Chúng không chia sẻ dữ liệu giữa nhiều pods/nodes và không cung cấp persistence/transaction. Nếu triển khai sản phẩm thật, thay bằng repository/database, kiểm quyền truy cập, validation, unique constraints và transactions.

### 10.4. BookHandler: nơi xử lý HTTP request

`src/main/java/com/example/mvcfn/web/BookHandler.java`:

~~~java
package com.example.mvcfn.web;

import com.example.mvcfn.data.BookStore;
import com.example.mvcfn.data.BookStore.Book;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class BookHandler {

    public record NewBook(String title) {}
    public record ApiError(String code, String message) {}

    public static final class BookMissingException
            extends RuntimeException {
        public BookMissingException(long id) {
            super("Book not found: " + id);
        }
    }

    public static final class InvalidBookInputException
            extends RuntimeException {
        public InvalidBookInputException(String message) {
            super(message);
        }
    }

    private final BookStore store;

    public BookHandler(BookStore store) {
        this.store = store;
    }

    public ServerResponse health(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("OK");
    }

    public ServerResponse all(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.all());
    }

    public ServerResponse byId(ServerRequest request) {
        String raw = request.pathVariable("id");
        long id;
        try {
            id = Long.parseLong(raw);
        } catch (NumberFormatException badId) {
            throw new InvalidBookInputException(
                    "id must be an integer");
        }

        if (id <= 0) {
            throw new InvalidBookInputException(
                    "id must be positive");
        }

        Book book = store.find(id)
                .orElseThrow(() -> new BookMissingException(id));

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }

    public ServerResponse featured(ServerRequest request) {
        Book book = store.find(1L)
                .orElseThrow(() -> new BookMissingException(1L));
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }

    public ServerResponse invalidFeatured(ServerRequest request) {
        throw new InvalidBookInputException(
                "featured requires view=summary");
    }

    public ServerResponse create(ServerRequest request)
            throws Exception {

        NewBook input = request.body(NewBook.class);
        if (input == null || input.title() == null ||
                input.title().isBlank()) {
            throw new InvalidBookInputException(
                    "title is required");
        }

        Book book = store.create(input.title().trim());
        return ServerResponse.created(
                        URI.create("/api/books/" + book.id()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(book);
    }
}
~~~

Không phải mọi runtime sẽ gọi handler khi JSON body malformed; request conversion có thể ném exception trước khi có `NewBook`. Error mapping trong router ở dưới xử lý hai loại lỗi nghiệp vụ minh họa, không cố “catch tất cả” và che 500.

Các `ApiError` trong demo chỉ chứa thông tin an toàn. Không trả raw stack trace, SQL errors, credentials hoặc path filesystem từ exception vào JSON. Validation hoàn chỉnh có thể dùng Jakarta Bean Validation riêng với `Validator` hoặc validation trong service; **functional routing không tự thay một `@Valid @RequestBody` của annotated controller**.

### 10.5. BookRoutes: prefix path, nested Accept, parameter predicate và onError

`src/main/java/com/example/mvcfn/web/BookRoutes.java`:

~~~java
package com.example.mvcfn.web;

import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;
import static org.springframework.web.servlet.function.RequestPredicates.param;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class BookRoutes {

    @Bean
    RouterFunction<ServerResponse> bookRouter(BookHandler handler) {
        return RouterFunctions.route()
                .path("/api", api -> api
                        .GET("/health", handler::health)
                        .path("/books", books -> books
                                .nest(accept(MediaType.APPLICATION_JSON),
                                        json -> json
                                                .GET("/featured",
                                                        param("view", "summary"),
                                                        handler::featured)
                                                .GET("/featured",
                                                        handler::invalidFeatured)
                                                .GET("/{id}", handler::byId)
                                                .GET("", handler::all))
                                .POST("",
                                        contentType(MediaType.APPLICATION_JSON),
                                        handler::create)))
                .onError(BookHandler.BookMissingException.class,
                        (ex, request) ->
                                ServerResponse.status(404)
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .body(new BookHandler.ApiError(
                                                "BOOK_NOT_FOUND",
                                                "Book does not exist")))
                .onError(BookHandler.InvalidBookInputException.class,
                        (ex, request) ->
                                ServerResponse.badRequest()
                                        .contentType(
                                                MediaType.APPLICATION_JSON)
                                        .body(new BookHandler.ApiError(
                                                "INVALID_BOOK_INPUT",
                                                ex.getMessage())))
                .build();
    }
}
~~~

**Kiểm thứ tự quan trọng:**
1. `path("/api", ...)` gom root API.
2. `GET("/health")` không có điều kiện Accept JSON vì trả text.
3. `path("/books", ...)` gom resource.
4. `nest(accept(JSON), ...)` áp một điều kiện Accept chung cho **toàn nhóm GET**; không áp điều kiện này lên POST nằm ngoài.
5. `GET("/featured", param("view","summary"),...)` phải đứng trước `GET("/featured",...)` fallback.
6. Cả hai featured routes phải đứng trước `GET("/{id}", ...)`; nếu không “featured” bị hiểu là ID.
7. `GET("", ...)` chỉ match danh sách tại prefix `/api/books`.
8. `POST("", contentType(JSON),...)` chỉ match request POST tại prefix với Content-Type JSON phù hợp.
9. `onError` ở router builder map hai exception types được handler ném thành JSON với mã máy đọc.
10. `build()` tạo một `RouterFunction<ServerResponse>` duy nhất và Spring Boot phát hiện bean.

**Đừng giả định `onError` là thay thế tất cả global error handlers.** Nó xử lý các exception đi qua router filter chain của những routes này; exception do security filter, Servlet container hoặc những components khác tạo có thể đi theo error path khác. Phải test đầy đủ cả HTTP stack khi có Spring Security.

### 10.6. Request → route → handler → response

| Request | Route match | Handler | Expected HTTP |
| --- | --- | --- | --- |
| GET `/api/health` | `/api` + `/health` | health | 200 text `OK` |
| GET `/api/books`, Accept JSON | `/api` + `/books` + nested Accept + GET empty | all | 200 JSON list |
| GET `/api/books/1`, Accept JSON | GET `/{id}` | byId | 200 book 1 |
| GET `/api/books/999`, Accept JSON | GET `/{id}` | byId + onError | 404 JSON code `BOOK_NOT_FOUND` |
| GET `/api/books/not-a-number`, Accept JSON | GET `/{id}` | byId + onError | 400 JSON code `INVALID_BOOK_INPUT` |
| GET `/api/books/featured?view=summary`, Accept JSON | parameter-aware featured | featured | 200 JSON book 1 |
| GET `/api/books/featured`, Accept JSON | featured fallback | invalidFeatured | 400 JSON |
| POST `/api/books`, Content-Type JSON | POST empty + Content-Type | create | 201 JSON + Location |
| GET `/api/books`, Accept text/html | nested Accept không match | không phải all | Response phụ thuộc mapping/error path, không giả định JSON 200 |
| POST `/api/books`, Content-Type text/plain | POST Content-Type không match | không phải create | Routing/error status phụ thuộc Spring MVC config |

Bảng minh họa cách đi qua nested routing, không bao hàm authentication, CORS hoặc production validation. Demo không thêm Spring Security starter nên mọi GET/POST được gọi mà không đăng nhập; **điều đó không an toàn cho dữ liệu người dùng thật**.

## 11. Chạy thử với curl

Ở project có Gradle wrapper tương thích Java 17:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat bootRun
~~~

### 11.1. Health

~~~bash
curl -i http://localhost:8080/api/health
~~~

Dự kiến:

~~~http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

OK
~~~

`charset`, transfer headers và thứ tự HTTP headers có thể khác theo server; phần cần kiểm là HTTP 200, response text `OK`.

### 11.2. Danh sách sách

~~~bash
curl -i 'http://localhost:8080/api/books' \
  -H 'Accept: application/json'
~~~

Dự kiến 200:

~~~json
[
  {"id":1,"title":"Spring Web"},
  {"id":2,"title":"Functional MVC"}
]
~~~

JSON formatting thật thường là một dòng. Nếu trước đó POST thêm sách, danh sách sẽ có thêm sách mới; ở request sau khi restart sạch, hai sách mặc định trên luôn có mặt.

### 11.3. Chi tiết và 404

~~~bash
curl -i 'http://localhost:8080/api/books/1' \
  -H 'Accept: application/json'

curl -i 'http://localhost:8080/api/books/999' \
  -H 'Accept: application/json'
~~~

Trường hợp ID 1 trả 200:

~~~json
{"id":1,"title":"Spring Web"}
~~~

ID 999 trả 404:

~~~json
{"code":"BOOK_NOT_FOUND","message":"Book does not exist"}
~~~

ID không phải số:

~~~bash
curl -i 'http://localhost:8080/api/books/abc' \
  -H 'Accept: application/json'
~~~

Dự kiến 400:

~~~json
{"code":"INVALID_BOOK_INPUT","message":"id must be an integer"}
~~~

### 11.4. Route nổi bật có param và fallback

~~~bash
curl -i 'http://localhost:8080/api/books/featured?view=summary' \
  -H 'Accept: application/json'
~~~

Dự kiến 200, sách ID 1. Nếu thiếu param:

~~~bash
curl -i 'http://localhost:8080/api/books/featured' \
  -H 'Accept: application/json'
~~~

Dự kiến 400 với `INVALID_BOOK_INPUT`. Nhờ fallback đứng **trước** `/{id}`, chuỗi `featured` không bị parse như một ID.

### 11.5. POST JSON tạo sách

~~~bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"MVC.fn Handbook"}'
~~~

Trên một instance mới chưa POST trước, status 201, header `Location: /api/books/3` và body:

~~~json
{"id":3,"title":"MVC.fn Handbook"}
~~~

Kiểm lại:

~~~bash
curl -i 'http://localhost:8080/api/books/3' \
  -H 'Accept: application/json'
~~~

Dự kiến 200 với sách vừa tạo. ID thật tăng theo số lượt POST, không cố định 3 nếu application đã chạy lâu. Đây là dữ liệu memory-local; restart mất sách mới.

Nếu POST body có title rỗng:

~~~bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'
~~~

Dự kiến 400 và `INVALID_BOOK_INPUT`. Mã route chỉ bắt loại exception này; JSON syntax hỏng có thể dẫn đến một error response khác tùy Spring MVC converter/container config.

## 12. MockMvc: kiểm route lồng nhau và thứ tự match

MockMvc chạy qua Spring MVC handler mappings mà không cần mở port HTTP. Vì ta đăng ký một `RouterFunction<ServerResponse>` bean, `RouterFunctionMapping` nhận route thật và test có thể gọi giống HTTP client.

`src/test/java/com/example/mvcfn/BookRoutesTest.java`:

~~~java
package com.example.mvcfn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookRoutesTest {

    @Autowired
    MockMvc mvc;

    @Test
    void healthIsOutsideJsonAcceptNest() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void nestedBookListMatchesJsonGet() throws Exception {
        mvc.perform(get("/api/books")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Spring Web"));
    }

    @Test
    void dynamicIdMatchesAfterSpecificRoutes() throws Exception {
        mvc.perform(get("/api/books/2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.title").value("Functional MVC"));
    }

    @Test
    void featuredWithParamBeatsVariableRoute() throws Exception {
        mvc.perform(get("/api/books/featured")
                        .param("view", "summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void featuredWithoutParamGetsExplicitBadRequest() throws Exception {
        mvc.perform(get("/api/books/featured")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_INPUT"));
    }

    @Test
    void invalidNumericIdGetsBadRequest() throws Exception {
        mvc.perform(get("/api/books/not-a-number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_INPUT"));
    }

    @Test
    void unknownBookGets404FromRouterOnError() throws Exception {
        mvc.perform(get("/api/books/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void postJsonCreatesResourceAndLocation() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New MVC.fn Book\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        startsWith("/api/books/")))
                .andExpect(jsonPath("$.title").value("New MVC.fn Book"));
    }

    @Test
    void postBlankTitleGets400() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_INPUT"));
    }
}
~~~

Chạy:

~~~bash
./gradlew test
~~~

**Điều test chứng minh:** route prefix nesting, JSON Accept predicate, query/form param predicate, route order, handler, custom error response và POST Content-Type. Đừng suy luận MockMvc chứng minh reverse proxy URL rewrite, CORS preflight, cookie policy, TLS hay database transactions.

Bộ test tạo book memory-local trong một method; các test đọc ID 1 và 2 là ổn định vì store không xóa dữ liệu cũ. Nếu cần assert số lượng chính xác ở mọi test, hãy reset store hoặc tạo application context/store riêng theo test; tránh phụ thuộc thứ tự chạy của JUnit.

### 12.1. Test thứ tự bằng negative case

Một bài kiểm thử regression có giá trị là `featuredWithoutParamGetsExplicitBadRequest`: nếu ai đổi thứ tự để `/{id}` đứng trước `/featured` fallback, request có thể bị xử lý nhầm như ID chuỗi. Error code/status ở ví dụ vẫn có thể là 400 vì cả hai nhánh đều map 400; để bắt chính xác **handler** bị chọn, hãy assert thêm message `"featured requires view=summary"` thay vì chỉ status/code.

Trong sản phẩm thật, các tests cần xác nhận **nội dung phản hồi và handler semantics**, không chỉ HTTP status.

## 13. Filters trong RouterFunction Builder

Functional MVC cho phép các filters quanh handler:
- `before(Function<ServerRequest,ServerRequest>)`: biến đổi request trước handler.
- `after(BiFunction<ServerRequest,ServerResponse,ServerResponse>)`: biến đổi/quan sát response sau handler.
- `filter(HandlerFilterFunction<ServerResponse,ServerResponse>)`: bao quanh handler và gọi `next.handle(request)`.
- `onError(Predicate<Throwable>, BiFunction<Throwable,ServerRequest,ServerResponse>)` hoặc `onError(Class<? extends Throwable>,...)`: tạo alternate response cho các exception match.

Ví dụ fragment logging:

~~~java
RouterFunction<ServerResponse> route =
        RouterFunctions.route()
                .GET("/hello", request ->
                        ServerResponse.ok().body("Hello"))
                .filter((request, next) -> {
                    long start = System.nanoTime();
                    try {
                        return next.handle(request);
                    } finally {
                        long elapsed = System.nanoTime() - start;
                        // Ghi metric elapsed với nhãn được allowlist.
                    }
                })
                .build();
~~~

Một `HandlerFilterFunction` chỉ bao quanh **những routes trong builder** mà nó được gắn. Khi gắn trên nested builder, phạm vi có thể giới hạn trong nhánh; khi gắn trên parent, có thể bao trùm cả các routes con. Đây là khác biệt quan trọng khi viết audit/logging, correlation IDs hoặc filters trả response phụ.

### 13.1. Filter ở WebMvc.fn không thay Servlet Filter/Spring Security

`filter((request,next)->...)` không thay authentication/authorization của Spring Security. Nó chỉ áp dụng cho các functional routes được build tại vị trí đó, không phải tất cả MVC controllers/Servlet endpoints. Security protection thường thuộc `SecurityFilterChain` và được áp dụng trước `DispatcherServlet`. Với kiểm soát quyền theo object/tenant, vẫn cần service/domain rules.

Không đưa credentials raw vào tracing logs. Không đo riêng handler duration rồi gọi đó là “HTTP response latency” nếu response writing/network time không nằm trong khoảng đo.

### 13.2. `before` không biến body đã đọc thành unread

Nếu một request filter tự đọc toàn bộ `ServerRequest.body(...)`, handler sau đó có thể không đọc lại được input stream tùy implementation. Tốt hơn là validate metadata/header ở route filter, hoặc dùng một wrapper/caching request body được thiết kế đúng nếu ứng dụng bắt buộc cần kiểm body hai lần. Cũng cần tránh mutate header `Authorization` hoặc user identity trong before filter một cách không an toàn.

### 13.3. `onError` và exception scopes

`RouterFunctions.Builder.onError(...)` giúp tạo response cụ thể khi handler/filter ném exception thuộc phạm vi phù hợp. Nhưng:
- Error phát sinh **trước** khi đến router (auth filter, CORS preflight) thường nằm ngoài handler filter.
- Error khi response body đang stream/đã commit có thể không sửa status được.
- Catch-all `onError(Throwable.class,...)` có thể giấu lỗi programming quan trọng; dùng typed exceptions và default 500 + logging an toàn.
- Một handler có thể tự tạo `ServerResponse.status(400)` thay vì ném exception, lúc đó `onError` không cần chạy.
- Các nhánh error từ message conversion và global HandlerExceptionResolver có hành vi theo implementation/version; hãy test dạng lỗi cụ thể.

## 14. `@ControllerAdvice` và functional endpoints

Trong annotated MVC, `@RestControllerAdvice`/`@ExceptionHandler` thường được sử dụng cho exception mapping. Với functional MVC, `onError` trên router builder là nơi rất rõ để bao một nhóm functional routes; các MVC exception resolvers khác vẫn tồn tại và tham gia theo cách Spring xử lý request.

Không giả định:
- Mọi `@ControllerAdvice` chỉ chọn annotated handler và bỏ qua functional route.
- Hoặc một advice bất kỳ sẽ luôn bắt mọi lỗi ném từ RouterFunction, kể cả lỗi ở Security filter.
- Hay một catch-all `onError` có thể trả lỗi JSON ngay cả khi network body đã gửi một phần.

Hãy viết integration tests cho HTTP response cuối của từng lỗi cụ thể. Khi muốn một ProblemDetail schema thống nhất giữa annotated MVC, functional MVC và Spring Security, thiết kế shared error model nhưng cấu hình đúng **từng** error entry point/handler layer.

## 15. RouterFunctions và Spring Security/CORS

### 15.1. SecurityFilterChain vẫn cần thiết

Dù route được khai báo bằng `RouterFunction` thay vì `@RestController`, request vẫn đi qua Servlet filters và Spring Security nếu có dependency/config. Ví dụ security policy ở conceptual layer:

~~~java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/health").permitAll()
    .requestMatchers("/api/books/**").authenticated()
    .anyRequest().denyAll())
~~~

Fragment này cần SecurityConfig và `HttpSecurity`. Nó **không** tự có trong demo không-security của mục 10. Nếu muốn POST/DELETE cần CSRF token đối với cookie-authenticated browser clients, không được bỏ qua chỉ vì handler là functional.

### 15.2. CORS khi có frontend khác origin

Browser khác origin gửi `POST /api/books` với `Content-Type: application/json` thường phải thực hiện preflight OPTIONS. Predicate `contentType(APPLICATION_JSON)` của POST **không** xử lý CORS thay browser/framework. CORS cần cấu hình ở filter/MVC/Security layer phù hợp, cho đúng origin/method/header, và actual request vẫn kiểm authentication/authorization/CSRF.

Nếu một OPTIONS chỉ khớp route GET/POST không rõ ràng, đừng tạo một fallback `all()` tự echo mọi origin tùy ý. Dùng Spring MVC CORS infrastructure hoặc cấu hình Security `cors()` đúng, và test với browser/MockMvc.

### 15.3. Functional handlers và user identity

Một `ServerRequest` có truy cập Servlet request/principal APIs tương ứng; identity nên được lấy từ authentication context tin cậy, không từ `?user=...` do client tự gửi. Nếu route xử lý `/api/books/{id}` theo owner/tenant, phải kiểm quyền với dữ liệu thực trước khi trả body, kể cả ở GET.

## 16. Functional MVC và annotation MVC: khi nào chọn?

| Khía cạnh | Functional MVC | Annotation MVC |
| --- | --- | --- |
| Khai báo route | Bean `RouterFunction`, predicate DSL | `@RequestMapping`/`@GetMapping` trên method |
| Gom prefix | `path/nest` | `@RequestMapping` trên class |
| Điều kiện media/header | `accept/contentType/headers` | `produces/consumes/headers` |
| Thứ tự match | Theo thứ tự compose | Chọn mapping đặc hiệu theo framework |
| Handler signature | `ServerRequest -> ServerResponse` | Tham số có thể `@PathVariable`, `@RequestBody`, `Model`... |
| Binding/validation | Chủ động dùng `ServerRequest.body/bind` và validator | Argument resolvers, `@Valid`... |
| Filter theo nhóm route | `before/after/filter/onError` | Servlet filters/interceptors/advice |
| OpenAPI/codegen ecosystem | Có thể cần explicit docs/metadata | Nhiều tools suy ra tốt từ annotations |
| Phù hợp | Routing DSL, composition, chia module | CRUD controllers thông dụng, DI/validation quy ước |

Đừng thay tất cả controllers sang WebMvc.fn chỉ vì functional syntax ngắn hơn. Nếu phần lớn code dùng `@Valid`, `@RequestBody`, Springdoc metadata, annotated method security, annotation controllers có thể dễ hiểu hơn. Nếu cần compose nhiều routes từ modules, filter theo nhóm, tách routing declarations khỏi handlers, functional MVC là lựa chọn đáng cân nhắc.

Một codebase có thể phối hợp hai mô hình, nhưng route ownership phải rõ và test overlaps để không tạo behavior bất ngờ.

## 17. Tách router thành nhiều module

Một ứng dụng lớn có thể định nghĩa:
- `BookRoutes` cho `/books`.
- `AuthorRoutes` cho `/authors`.
- `HealthRoutes` cho `/health`.

Sau đó ghép lại bằng `RouterFunctions.Builder.add(...)` hoặc `RouterFunction.and(...)`, và đặt prefix bằng `path(...)`. Ví dụ conceptual fragment:

~~~java
RouterFunction<ServerResponse> combined =
        RouterFunctions.route()
                .path("/api/books", bookRoutes::routes)
                .path("/api/authors", authorRoutes::routes)
                .build();
~~~

Ở đây `bookRoutes.routes()` và `authorRoutes.routes()` phải trả đúng `RouterFunction<ServerResponse>` với child paths tương đối, và handler beans được inject. Fragment không chạy riêng vì chưa có hai classes. Khi có nhiều `RouterFunction` beans, hãy kiểm thứ tự bean registration/order và tránh overlapping catch-all routes; không giả định thứ tự file/source hoặc tên bean là business contract.

### 17.1. Không lồng logic nghiệp vụ vào Router bean

`@Bean RouterFunction` được tạo khi ứng dụng startup, không phải khi từng HTTP request chạy. Vì thế:
- Đừng query database lấy sách khi đang xây DSL và giữ cố định kết quả cho mọi request.
- Đừng dùng `System.currentTimeMillis()` trong routing configuration để quyết định policy theo request.
- Đừng tạo new service/thread pool trong handler lambda mỗi request.
- Nên inject handler/service với constructor, dễ test và bảo trì.
- Route predicates nên thuần, rẻ, không có side effects; business logic ở handler/service.

## 18. Path pattern và nguy cơ route rộng

Spring Framework 6.x dùng path matching infrastructure có `PathPattern`. Path variable `/{id}` có thể match nhiều chuỗi, không nhất thiết chỉ numeric. Path wildcard `/**` có thể match phạm vi lớn; nếu đứng trước routes cụ thể trong functional chain, nhiều request đi nhầm handler.

Ví dụ anti-pattern:

~~~java
RouterFunctions.route()
        .GET("/api/{anything}", handler::catchAll)
        .GET("/api/health", handler::health)
        .build();
~~~

Với thứ tự này, `/api/health` có thể bị route đầu match. Sửa bằng cách đưa route cụ thể lên trước. Nếu chức năng catch-all thật sự cần thiết, đặt nó cuối, kiểm URL normalization, encoded segments, trailing slash và method matching theo framework/server version.

Không tự giả định functional routes và annotation mappings có cùng ưu tiên khi cả hai đăng ký cùng path; phải test với full Spring application context.

## 19. Lỗi thường gặp và cách xử lý

| Triệu chứng | Nguyên nhân hay gặp | Hướng kiểm |
| --- | --- | --- |
| `RouterFunction` không chạy trong MVC | Import nhầm `web.reactive.function.server` | Dùng `web.servlet.function`, starter Web |
| GET `/featured` bị parse làm `id` | Route `/{id}` đứng trước route cụ thể | Đảo thứ tự |
| Nested route luôn 404 | Prefix bị lặp/thiếu hoặc child path sai | Vẽ full path từ các `path` |
| GET JSON không match | `Accept` không tương thích nested predicate | Kiểm Accept và nhóm `nest` |
| POST JSON không match | Thiếu/nhầm Content-Type | Kiểm contentType predicate |
| Handler không đọc được body lần hai | Filter/interceptor đã consume input stream | Tránh đọc hai lần, dùng caching wrapper đúng |
| `param("view","summary")` không match | Thiếu param hoặc giá trị khác | Kiểm request parameter thực, fallback |
| `queryParam(...)` không biên dịch | Dùng API WebFlux.fn ở Servlet package | Dùng `param(...)` |
| `ServerResponse.bodyValue(...)` không có | Copy builder của WebFlux.fn | Dùng `ServerResponse.ok().body(...)` |
| Không serialize JSON record | Thiếu Jackson converter hoặc sai Content-Type | Kiểm starter, converter và response |
| `@Valid` không tự chạy | Functional body decode không tương đương annotated resolver | Gọi validator/validate tại service |
| 404/406/415 khác dự đoán | Route không match; các mapping/resolvers xử lý khác | Test actual response/mapping context |
| Catch-all `onError` che 500 | Nuốt mọi exception | Typed exceptions và safe default logging |
| Route chạy được nhưng không bảo mật | Nhầm predicate với authorization | Spring Security + domain auth |
| Browser báo CORS ở POST | Preflight OPTIONS chưa cấu hình | CORS/Security filter integration |
| Test GET pass nhưng POST tạo dữ liệu trùng | In-memory IDs, state dùng chung giữa tests | Test isolation và persistence đúng |
| API Docs không tìm functional endpoint | Tool chỉ introspect annotations | Explicit OpenAPI mappings/config |
| Một request chạy hai route | Hiểu sai composition | Router chọn route đầu match, rà side effects |
| Lỗi khi phối hợp annotation routes | HandlerMapping order/overlap | Kiểm mappings, integration tests |
| Dùng blocking JDBC trong WebMvc.fn tưởng reactive | Nhầm programming model | MVC Servlet vẫn blocking |
| Nhiều routers beans có thứ tự không rõ | Registration/order không tường minh | Compose rõ và test |

### Quy trình debug có thứ tự

1. Xác nhận dependency Spring Web/Boot và **imports Servlet WebMvc.fn**.
2. Xác nhận bean `RouterFunction<ServerResponse>` được tạo trong Spring context.
3. Vẽ full path từ tất cả `path(prefix)`/`nest(path)`.
4. Kiểm thứ tự: static/cụ thể trước variable/wildcard.
5. Ghi method, path, Accept, Content-Type, query/form params và custom headers thực sự gửi.
6. Kiểm nested Accept/Content-Type/header predicates có làm route không match không.
7. Phân biệt không match route và exception trong handler; kiểm đúng status/error code.
8. Nếu có Security, xác minh filter chain/CORS/CSRF trước MVC.
9. Kiểm message converters và body validation.
10. Thêm MockMvc tests cho positive, negative và route ordering, sau đó E2E với proxy/gateway nếu có.

## 20. Checklist review trước production

- [ ] Sử dụng đúng package `org.springframework.web.servlet.function`.
- [ ] Bean RouterFunction được Spring quản lý, handlers inject qua constructor.
- [ ] Routes cụ thể đặt trước wildcard/variable; fallback cuối cùng.
- [ ] Nested path prefixes chính xác, child paths không trùng prefix.
- [ ] `accept`/`contentType` dùng đúng ý nghĩa HTTP, không thay validation.
- [ ] `RequestPredicates.param` đúng Servlet API, không lẫn `queryParam` của WebFlux.
- [ ] Input path/query/body được parse, validate và giới hạn trước domain operations.
- [ ] Error mapping 400/404/500 an toàn, không leak exception details.
- [ ] SecurityFilterChain, CSRF/CORS, tenant/object authorization được áp dụng đúng.
- [ ] Các filters ở router có scope rõ; không tự thay Spring Security.
- [ ] API docs/OpenAPI có thể mô tả routes chức năng; có contract tests.
- [ ] Đo performance của handler và converter, không tự cho rằng functional MVC non-blocking.
- [ ] MockMvc kiểm nesting, param/header/media predicates, conflicts, status và response body.
- [ ] Integration tests qua proxy/gateway nếu route cần rewrite.
- [ ] Module lớn được compose có thứ tự rõ, không có catch-all che route quan trọng.

### Câu hỏi tự kiểm tra

**1.** `RouterFunction` khác `HandlerFunction` thế nào?

**Trả lời:** RouterFunction tìm một HandlerFunction phù hợp request; HandlerFunction xử lý ServerRequest và trả ServerResponse.

**2.** Tại sao route `GET("/books/{id}")` không nên đứng trước `GET("/books/featured")`?

**Trả lời:** Functional routes được xét theo thứ tự, `{id}` có thể nhận `featured`, khiến handler sai được chọn.

**3.** `path("/api", ...)` và `nest(accept(JSON), ...)` khác nhau gì?

**Trả lời:** path nhóm URL prefix; nest áp RequestPredicate bất kỳ (ở đây Accept) lên routes con.

**4.** `Content-Type` và `Accept` ảnh hưởng predicates nào?

**Trả lời:** Content-Type của body request dùng `contentType`; Accept mô tả response client mong muốn dùng `accept`.

**5.** Trong WebMvc.fn Servlet, dùng `param` hay `queryParam`?

**Trả lời:** Dùng `RequestPredicates.param` của `org.springframework.web.servlet.function`. Không nhầm API `queryParam` của WebFlux.fn.

**6.** Nested Accept JSON có tự serialize DTO sang JSON không?

**Trả lời:** Không. Nó chỉ kiểm điều kiện match; `ServerResponse` và MVC HttpMessageConverter mới tạo response body.

**7.** Router filter có thay `SecurityFilterChain` không?

**Trả lời:** Không. Router filter thuộc các routes chức năng, còn Spring Security là bảo vệ HTTP requests qua Servlet filter chain và service/domain authorization.

**8.** Vì sao không chỉ assert HTTP 200 khi test functional routing?

**Trả lời:** Có thể match sai handler nhưng status vẫn 200. Cần assert body/headers và trường hợp route tương phản.

**9.** WebMvc.fn có tự dùng non-blocking I/O như WebFlux.fn không?

**Trả lời:** Không. Nó là Servlet MVC programming model, vẫn có thể block trên JDBC hoặc đồng bộ network I/O.

## 21. Tài liệu chính thức

- [Spring Framework 6.2 — Functional Endpoints (Spring MVC)](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-functional.html): handler functions, request predicates, router functions, nested routing, filtering.
- [Spring Framework 6.1 — RouterFunctions Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.html): route/nest API.
- [Spring Framework 6.1 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.Builder.html): `GET/POST/path/nest/onError/filter`.
- [Spring Framework 6.1 — RequestPredicates Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/function/RequestPredicates.html): `GET/POST/path/param/headers/accept/contentType`.
- [Spring Framework 6.1 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/web/servlet/function/ServerRequest.html): path variables, parameters, headers, body.
- [Spring Framework 6.1 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/web/servlet/function/ServerResponse.html): response status, headers, body builders.
- [Spring Framework 6.2 — MVC DispatcherServlet](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet.html): overall MVC handler mapping and adapter pipeline.
- [Spring Framework — MockMvc](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html): integration-style MVC test without opening a server port.
- [Spring Security — Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html): request authorization independent of router syntax.
- [Spring Framework — CORS trong Servlet MVC](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): cross-origin request policies.

**Ghi nhớ:** WebMvc.fn dùng `RouterFunction` để chọn `HandlerFunction` qua `RequestPredicate` rồi trả `ServerResponse`. `path` gom prefix, `nest` gom điều kiện chung, còn **thứ tự route phải được thiết kế chủ động**. Chỉ dùng predicate để chọn tuyến — bảo mật, validation, error semantics và dữ liệu nghiệp vụ vẫn cần lớp riêng và kiểm thử HTTP đầy đủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-functional-endpoints-004">Đọc đầu vào, tạo phản hồi và sử dụng message converter</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc đầu vào, tạo phản hồi và sử dụng message converter trong Functional Spring MVC

> **Bối cảnh:** Spring Framework 6.2.x, Spring Boot 3.5.x, Java 17+, trên **Servlet stack**. Chủ đề này thuộc **Functional MVC (WebMvc.fn)**: xử lý request bằng `HandlerFunction<ServerResponse>` thay vì khai báo controller method với `@RequestMapping`, đọc dữ liệu từ `ServerRequest`, trả `ServerResponse` và dùng các **`HttpMessageConverter`** của Spring MVC để biến HTTP bytes thành Java objects hoặc ngược lại. Cần phân biệt với **WebFlux.fn**: WebMvc.fn sử dụng package `org.springframework.web.servlet.function`, các thao tác đọc body chính là `request.body(Class<T>)` / `request.body(ParameterizedTypeReference<T>)`, không phải `bodyToMono()` hay `HttpMessageReader` của WebFlux.

## 1. Functional MVC giải quyết điều gì?

Spring MVC có hai cách quen thuộc để khai báo HTTP endpoints:

**Annotation-based MVC:**

```java
@RestController
@RequestMapping("/api/books")
class BookController {

    @PostMapping
    public Book create(@RequestBody CreateBook input) {
        return service.create(input);
    }
}
```

**Functional MVC (WebMvc.fn):**

```java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .POST("/api/books", handler::create)
                .build();

// Trong class handler:
ServerResponse create(ServerRequest request) throws Exception {
    CreateBook input = request.body(CreateBook.class);
    Book saved = service.create(input);
    return ServerResponse.ok().body(saved);
}
```

Hai đoạn trên **là snippets minh họa**, yêu cầu `Book`, `CreateBook`, `service`, `handler` được định nghĩa. Functional MVC vẫn chạy bên trong **`DispatcherServlet`**, vẫn dùng Servlet request/response và các message converters; nó **không** tự biến web application thành reactive WebFlux stack.

Đối với yêu cầu “đọc đầu vào – tạo phản hồi – dùng converter”, mô hình WebMvc.fn là:

```text
HTTP request bytes
      |
Servlet container / DispatcherServlet
      |
RouterFunction<ServerResponse> chọn route
      |
HandlerFunction<ServerResponse>.handle(ServerRequest)
      |
ServerRequest.body(Class/ParameterizedTypeReference)
      |
HttpMessageConverter.read(...)  <-- JSON/text/form/binary
      |
Java DTO + validation/authorization/business logic
      |
ServerResponse.status(...).contentType(...).body(Java object)
      |
HttpMessageConverter.write(...) <-- serialize body theo type/media
      |
HTTP status + headers + response bytes
```

**Ghi nhớ:** Router quyết định **handler nào** chạy; Handler quyết định **đọc gì và trả gì**; converter quyết định **cách diễn dịch và serialize HTTP body**. Validation, authentication/authorization và giới hạn kích thước payload là trách nhiệm riêng; có Jackson không có nghĩa input tự an toàn.

## 2. Bốn thành phần của Functional MVC

| Thành phần | Package | Vai trò |
| --- | --- | --- |
| `RouterFunction<ServerResponse>` | `org.springframework.web.servlet.function` | Từ `ServerRequest` xác định handler tương ứng |
| `HandlerFunction<ServerResponse>` | `org.springframework.web.servlet.function` | Nhận `ServerRequest`, trả `ServerResponse`; có thể `throws Exception` |
| `ServerRequest` | `org.springframework.web.servlet.function` | Truy cập method/URI/header/query/path variables/body/cookies/Servlet request |
| `ServerResponse` | `org.springframework.web.servlet.function` | Tạo status/headers/cookies/body hoặc response trống, sau đó ghi qua converters |
| `HttpMessageConverter<T>` | `org.springframework.http.converter` | Chuyển đổi giữa Java value và HTTP request/response bytes |
| `RouterFunctionMapping` | `org.springframework.web.servlet.function.support` | Phát hiện RouterFunction beans và tìm route phù hợp |
| `HandlerFunctionAdapter` | `org.springframework.web.servlet.function.support` | Thực thi HandlerFunction bên trong MVC lifecycle |

Trong ứng dụng Spring Boot Web, Spring MVC infrastructure thường tự phát hiện `RouterFunction<?>` beans. **Không cần `@EnableWebMvc`** chỉ để dùng functional routes; bật nó tùy tiện có thể làm mất một số Boot MVC auto-configuration defaults.

### 2.1. Functional không đồng nghĩa với hoàn toàn thuần hàm

Các API request/response mô tả **hợp đồng mang tính immutable** cho việc xây dựng routes/response; nhưng handler vẫn có thể tương tác database, transaction, Servlet session và các Spring beans có trạng thái. `ServerRequest` có thể cho phép truy cập request attributes, body và underlying servlet request; do vậy vẫn phải có thiết kế thread-safety, validation và authorization.

### 2.2. Không nhầm với WebFlux.fn

| Vấn đề | WebMvc.fn | WebFlux.fn |
| --- | --- | --- |
| Import server request | `org.springframework.web.servlet.function.ServerRequest` | `org.springframework.web.reactive.function.server.ServerRequest` |
| Handler trả | `ServerResponse` | `Mono<ServerResponse>` hoặc Kotlin coroutine adapters |
| Đọc JSON DTO | `request.body(Dto.class)` | `request.bodyToMono(Dto.class)` |
| Đọc `List<T>` | `request.body(new ParameterizedTypeReference<List<T>>() {})` | `bodyToFlux(T.class)` hoặc `bodyToMono(...)` |
| Định dạng HTTP | MVC `HttpMessageConverter` | WebFlux reactive `HttpMessageReader/Writer` codecs |
| HTTP I/O | Servlet blocking foundation | Reactive stack, non-blocking I/O khi runtime hỗ trợ |
| Server entrypoint | `DispatcherServlet` | `DispatcherHandler` |

Nếu IDE báo không tìm thấy `bodyToMono()` trên `ServerRequest` mà dự án của bạn đang dùng `web.servlet.function`, **đó không phải thiếu import**: bạn đã đọc ví dụ của WebFlux.fn. Đổi đúng API thay vì thêm starter reactive chỉ để compiler nhận method không có trên kiểu MVC.

## 3. RouterFunction và HandlerFunction: đưa request tới đúng chỗ

Ví dụ:

```java
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Bean
RouterFunction<ServerResponse> booksRouter(BookHandler handler) {
    return route()
            .GET("/api/books", handler::list)
            .GET("/api/books/{id}", handler::get)
            .POST("/api/books", handler::create)
            .build();
}
```

Đây là một bean method minh họa trong `@Configuration` class, cần `BookHandler`, `@Bean` và `RouterFunction`/`ServerResponse` imports.

- `RouterFunctions.route()` tạo DSL builder.
- `.GET(path, handler)` và `.POST(path, handler)` gắn method/path với callback.
- `handler::get` là method reference; target method nhận `ServerRequest` và trả `ServerResponse`.
- `.build()` trả một `RouterFunction<ServerResponse>` cho Spring MVC đăng ký.
- Trong annotation MVC, metadata routing đến từ `@GetMapping`/`@PostMapping`; trong WebMvc.fn, metadata đến từ **function/builder**.

### 3.1. Predicate lọc Content-Type và Accept

Nếu chỉ muốn route POST xử lý JSON, có thể dùng:

```java
import static org.springframework.web.servlet.function.RequestPredicates.contentType;

RouterFunction<ServerResponse> router = route()
        .POST("/api/books", contentType(MediaType.APPLICATION_JSON),
                handler::create)
        .build();
```

Nếu muốn áp điều kiện `Accept: application/json`:

```java
import static org.springframework.web.servlet.function.RequestPredicates.accept;

RouterFunction<ServerResponse> router = route()
        .GET("/api/books", accept(MediaType.APPLICATION_JSON),
                handler::list)
        .build();
```

**Lưu ý quan trọng:** khi predicate không match, router có thể đơn giản không chọn handler và dẫn tới 404/hoặc xử lý của mappings khác, **không bắt buộc** giống status 415/406 của annotated controller. Nếu API cần contract lỗi chính xác cho sai Content-Type/Accept, thực hiện integration tests và một error policy rõ ràng. Ví dụ runnable ở mục 9 chủ động không dùng predicate `contentType(...)` để dễ quan sát lỗi body/converter.

### 3.2. Nested routes và filter function

```java
RouterFunction<ServerResponse> router = route()
        .path("/api", b -> b
                .GET("/health", handler::health)
                .POST("/books", handler::create))
        .build();
```

Đây là snippet với `handler` khác được định nghĩa. Nested routing giúp nhóm prefix/conditions. WebMvc.fn còn có `before`, `after`, `filter` và `onError` trong router builder. Khi đăng ký filter **bên trong nested route**, scope của filter không tự áp cho mọi route bên ngoài. Dùng chúng cho trách nhiệm cross-cutting phù hợp nhưng không dùng để thay Spring Security FilterChain.

## 4. Đọc metadata request trước khi đọc body

`ServerRequest` cung cấp API dễ dùng:

```java
String id = request.pathVariable("id");
Optional<String> q = request.param("q");
HttpMethod method = request.method();
URI uri = request.uri();
String accept = request.headers().firstHeader("Accept");
Map<String, Object> attrs = request.attributes();
```

Đây là snippet cần imports `java.net.URI`, `java.util.*` và `org.springframework.http.HttpMethod`. API có thể khác khi dùng Spring major version khác; trong Spring MVC 6.2 có `pathVariable`, `param`, `headers` và `attributes`.

- `request.pathVariable("id")` lấy path segment được RouterFunction match; có thể ném lỗi nếu route không có biến tương ứng.
- `request.param("q")` là `Optional<String>`; xử lý trường hợp không có thay vì gọi `.get()`.
- `request.params()` lấy danh sách query parameters trong `MultiValueMap<String,String>` (có thể gồm nhiều giá trị).
- `request.headers()` có các phương thức đọc typed header tiện lợi như `accept()` và `contentType()` tùy API.
- `request.servletRequest()` cho phép truy cập `HttpServletRequest` khi cần khả năng Servlet-specific; không dùng nó làm cách chính đọc toàn body rồi đọc thêm lần nữa.

### 4.1. PathVariable cần parse và validate

```java
long id;
try {
    id = Long.parseLong(request.pathVariable("id"));
} catch (NumberFormatException ex) {
    return ServerResponse.badRequest()
            .body("Invalid id");
}
```

Đây là snippet minh họa, nhưng JSON API nên dùng error DTO và `contentType(APPLICATION_JSON)` thay vì body text mặc định. Không chuyển lỗi `NumberFormatException` thành HTTP 500, cũng không dùng exception message thô làm body.

### 4.2. Query có default và range

```java
String rawLimit = request.param("limit").orElse("20");
int limit = Integer.parseInt(rawLimit);
if (limit < 1 || limit > 100) {
    throw new IllegalArgumentException("limit outside range");
}
```

Validate cả parse lỗi, giới hạn kích thước, semantic constraints và bất kỳ quyền lọc/tìm kiếm nào. Đối với pagination, dùng LIMIT ở repository/DB thay vì đọc toàn triệu records rồi `stream().limit(20)` trong app.

## 5. Đọc HTTP body: ServerRequest.body(Class<T>)

Cách ngắn nhất cho JSON object:

```java
CreateBook command = request.body(CreateBook.class);
```

Khi request có:

```http
POST /api/books HTTP/1.1
Content-Type: application/json

{"title":"Spring MVC"}
```

Spring MVC tìm `HttpMessageConverter` có thể đọc:
- Target Java type `CreateBook.class`;
- Request `Content-Type` là `application/json`;
- Dependency/converter đăng ký trong MVC.

Một JSON converter phù hợp (ví dụ Jackson, từ Spring Web starter) sẽ đọc bytes từ Servlet request input stream, deserialize thành `CreateBook`. `request.body(...)` có thể ném lỗi nếu JSON sai hoặc converter không phù hợp; handler method có thể khai báo `throws Exception`.

### 5.1. Body có thể bị tiêu thụ

Servlet request input stream bình thường **không phải stream có thể tua về đầu tùy ý**. Đừng làm:

```java
String raw = request.body(String.class);
CreateBook second = request.body(CreateBook.class);
```

Đây là anti-pattern: lần đọc thứ hai không nên được xem là một lần parse lại từ đầu. Tương tự, nếu custom Servlet Filter đã gọi `getInputStream().readAllBytes()` để log trước khi MVC đọc body, request có thể rỗng ở handler. Muốn quan sát body hãy dùng bounded caching wrapper và để downstream đọc theo lifecycle phù hợp; đừng coi cache-on-read là cơ chế replay mặc định.

### 5.2. Body conversion KHÔNG tự động là validation

Giả sử DTO:

```java
record CreateBook(String title) {
}
```

Jackson có thể tạo instance khi field `title` vắng mặt/null, nên application vẫn phải kiểm tra field bắt buộc, độ dài, giá trị hợp lệ và business constraints. Trong functional handler, bare `request.body(CreateBook.class)` không đồng nghĩa tự chạy Bean Validation của `@Valid @RequestBody` trong annotated method.

Cách tối thiểu:

```java
CreateBook input = request.body(CreateBook.class);
if (input == null || input.title() == null
        || input.title().isBlank()
        || input.title().length() > 120) {
    return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ApiError("invalid_title"));
}
```

Trong app lớn, dùng Spring `Validator`/Jakarta Bean Validation qua một lớp validation service rõ ràng, tái sử dụng DTO rules. Không đọc input rồi mapping thẳng vào JPA entity có các trường nhạy cảm như `isAdmin`/`ownerId`/`tenantId`.

### 5.3. Exception liên quan body đọc

Các lớp lỗi thường gặp:
- JSON cú pháp sai hoặc dữ liệu không deserialize được có thể gây `HttpMessageNotReadableException`.
- Không có converter phù hợp cho content type hoặc data format có thể gây `HttpMediaTypeNotSupportedException` / các exception tương ứng trong infrastructure.
- Định dạng sai/ràng buộc nghiệp vụ bị từ chối bởi code handler có thể thành `IllegalArgumentException` hoặc error DTO.
- `IOException`/`ServletException` có thể phát sinh từ tầng đọc HTTP body.
- Body quá lớn cần chính sách ingress/container/filter **độc lập**, không tự có hard cap chỉ vì converter được đăng ký.

Không gộp tất cả thành HTTP 400 nếu lỗi hệ thống. Một lỗi I/O từ client disconnect hoặc database outage không cùng ngữ nghĩa với JSON malformed.

## 6. Đọc kiểu generic: ParameterizedTypeReference<T>

Java generics bị type erasure. Để deserialize `List<CreateBook>` (mảng JSON các object) đúng generic element type, dùng:

```java
import org.springframework.core.ParameterizedTypeReference;

List<CreateBook> commands =
        request.body(new ParameterizedTypeReference<List<CreateBook>>() {});
```

Request tương ứng:

```json
[
  {"title":"Book A"},
  {"title":"Book B"}
]
```

Nếu dùng `request.body(List.class)`, converter thiếu generic element type và có thể tạo `List<LinkedHashMap>` thay vì `List<CreateBook>`. Việc cast trực tiếp phần tử thành `CreateBook` có thể ném `ClassCastException`.

### 6.1. Khi nào dùng Class, khi nào dùng ParameterizedTypeReference?

| Đầu vào | API nên dùng |
| --- | --- |
| JSON object của một DTO | `request.body(CreateBook.class)` |
| JSON string/plain text | `request.body(String.class)` với Content-Type phù hợp |
| JSON array có generic element | `request.body(new ParameterizedTypeReference<List<CreateBook>>() {})` |
| JSON nested map/list | `request.body(new ParameterizedTypeReference<Map<String, List<ValueDto>>>() {})` |
| Raw bytes | `request.body(byte[].class)` với converter phù hợp; cảnh báo memory |
| Form URL-encoded | `request.params()` / form handling hoặc `MultiValueMap` theo converter và request handling |
| Multipart upload | Dùng Servlet multipart parsing và binding được hỗ trợ; không giả định JSON converter đọc file binary |

### 6.2. ParameterizedTypeReference không đảm bảo mọi format

Nó cung cấp **type metadata**, không cài đặt converter. Với custom media type hoặc một object không được Jackson/MessageConverter hỗ trợ, bạn vẫn cần converter tương ứng và security/resource limits. Không tự tin rằng “dùng ParameterizedTypeReference thì JSON/XML/CSV đều parse được”.

### 6.3. Bảo vệ bộ nhớ khi nhận List lớn

Request chứa hàng triệu phần tử JSON có thể được deserialize thành một `List<CreateBook>` khổng lồ. Vì converter MVC thường đọc/deserialize body thành object graph, hãy:
- đặt giới hạn body tại reverse proxy/container hoặc counting input stream;
- giới hạn số phần tử và độ sâu JSON bằng policy/parser configuration khi phù hợp;
- cân nhắc gửi batch nhỏ/pagination hoặc streaming ingestion chuyên dụng cho big data;
- không dùng `readAllBytes()` để nhân đôi bộ nhớ.

Ví dụ demo chỉ cho phép tối đa 5 item trong batch, nhưng **validation sau deserialize chưa phải hard memory limit**. Phải chặn payload quá lớn từ tầng trước nếu threat model yêu cầu.

## 7. Tạo ServerResponse: status, headers và body

WebMvc.fn cung cấp static factories:

```java
ServerResponse.ok()
ServerResponse.created(URI.create("/api/books/42"))
ServerResponse.accepted()
ServerResponse.noContent()
ServerResponse.badRequest()
ServerResponse.notFound()
ServerResponse.status(HttpStatus.CONFLICT)
```

Những biểu thức trên trả builder hoặc response-building APIs; để tạo response hoàn chỉnh cần gọi `.body(value)` hoặc `.build()` tùy trường hợp.

### 7.1. HTTP 200 với JSON body

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Book(42L, "Spring MVC"));
```

Java object không được đưa nguyên vẹn qua network. Khi Spring ghi `ServerResponse`, một converter phù hợp serialize `Book` thành JSON bytes. Với Jackson mặc định, body tương tự:

```json
{"id":42,"title":"Spring MVC"}
```

Thứ tự fields/whitespace có thể khác theo Jackson/version, nên test JSON theo path thay vì so toàn chuỗi.

### 7.2. HTTP 201 Created và Location

```java
return ServerResponse.created(URI.create("/api/books/42"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Book(42L, "Spring MVC"));
```

Kết quả có status **201 Created**, header `Location: /api/books/42` (relative URI là một URI hợp lệ cho Location), và JSON body. Đừng tự ghép absolute host từ raw `X-Forwarded-Host` để tạo Location nhạy cảm; proxy trust boundary và canonical public base URL phải được kiểm soát.

### 7.3. Không body: build()

```java
return ServerResponse.noContent().build();
```

Trả **204 No Content**; không dùng `.body(...)` trên 204 rồi mong browser nhận JSON. Với lệnh DELETE thành công hoặc update không cần representation, 204 là một lựa chọn thường gặp.

### 7.4. 400/404/409 và error DTO

```java
return ServerResponse.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ApiError("conflict"));
```

Với lỗi validation:

```java
return ServerResponse.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ApiError("invalid_request"));
```

Với tài nguyên không tìm thấy:

```java
return ServerResponse.notFound().build();
```

Dùng HTTP status phản ánh semantics chứ không trả 200 với `{"error":...}`. Đừng đưa raw exception stack trace, token hoặc internal filesystem paths vào response.

### 7.5. Header, cache, content negotiation

```java
return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Request-Tag", "demo")
        .header("Cache-Control", "no-store")
        .body(book);
```

Các API của response builder cũng có hỗ trợ cookies, ETag/Last-Modified và headers khi cần. Cẩn thận:
- `Content-Type` mô tả kiểu bytes trả ra, không phải Java class.
- `Accept` của client cho biết kiểu representation client chấp nhận.
- Nếu bạn ép `contentType(APPLICATION_JSON)` dù client chỉ chấp nhận XML, không tự mặc định handler sẽ thương lượng giống annotation `produces`; cần router predicates, infrastructure, và tests 406/serialization đúng.
- `Content-Length` phải tính theo **bytes đã encode**, không theo `String.length()`; thông thường để container/converter xử lý.
- Khi ghi response xong/committed, không thể thay status/header một cách chắc chắn.

## 8. HttpMessageConverter thực sự làm gì?

Spring MVC `HttpMessageConverter<T>` là cầu nối:

```text
Read:
application/json bytes -> Jackson JSON converter -> Java DTO

Write:
Java DTO -> Jackson JSON converter -> application/json bytes

Read:
text/plain bytes -> StringHttpMessageConverter -> String

Write:
byte[] -> ByteArrayHttpMessageConverter -> application/octet-stream bytes

Read form:
application/x-www-form-urlencoded -> FormHttpMessageConverter
                                         -> MultiValueMap<String, String>
```

Converter quyết định khả năng đọc/ghi dựa trên Java type, `Content-Type` / `MediaType`, dependency có trên classpath, và thứ tự converters đã đăng ký.

### 8.1. Các converter thường gặp

| Converter | Kiểu Java thường gặp | Media type |
| --- | --- | --- |
| `StringHttpMessageConverter` | `String` | `text/plain` / text media |
| `MappingJackson2HttpMessageConverter` | POJO/record/List/Map | `application/json` và các dạng JSON tương thích |
| `ByteArrayHttpMessageConverter` | `byte[]` | Thường `application/octet-stream` |
| `ResourceHttpMessageConverter` | `Resource` | Binary/stream resource |
| `FormHttpMessageConverter` | `MultiValueMap<String,String>` | `application/x-www-form-urlencoded` (đọc/ghi), multipart (ghi trong phạm vi được hỗ trợ) |
| XML converters | JAXB/Jackson XML types | `application/xml` hoặc `text/xml` nếu dependency/cấu hình có |

**Đừng nhầm `HttpMessageConverter` với `Converter<S,T>` trong `ConversionService`.** ConversionService thường phục vụ chuyển String path/query/form field thành primitive/enum/type; HttpMessageConverter đọc/ghi **HTTP body**.

### 8.2. Converter không thay validation

Quá trình deserialize thành `CreateBook` không xác nhận user có quyền tạo sách, không giới hạn title theo nghiệp vụ, không sanitize HTML script và không chặn owner spoofing. Cần DTO riêng, bean validation/manual validation và domain service authorization.

### 8.3. Converter không bảo đảm an toàn với untrusted payload

Một JSON parser có thể bị gây áp lực CPU/memory bởi payload quá lớn/sâu. Multipart/XML/file upload có thêm rủi ro riêng. Cấu hình:
- giới hạn request body và header sizes tại proxy/container;
- parser constraints/phần tử/depth và multipart limits;
- validation DTO/schema, allowlist fields;
- authentication/authorization, rate limits;
- logging có redaction, không dump raw body theo mặc định.

### 8.4. Request và response converters trong WebMvc.fn

`ServerRequest.messageConverters()` cho biết converters phục vụ đọc request body. Khi ghi `ServerResponse`, infrastructure dùng danh sách converters trong response context để chọn cách serialize body. Spring Boot MVC auto-configuration cung cấp các converters mặc định dựa theo classpath.

Nếu bạn thêm một custom converter chỉ vào một phần infrastructure nhưng endpoint không thấy, cần kiểm tra cấu hình MVC và thứ tự converters; đừng mặc định mọi custom Jackson `ObjectMapper`/codec đều áp dụng cho tất cả routes.

## 9. Ví dụ chạy được: Java 17 + Spring Boot 3.5, WebMvc.fn

Ứng dụng minh họa **không dùng `@RestController`**; chỉ có:
- Một `RouterFunction<ServerResponse>` bean;
- Một handler class (Spring component) làm việc với `ServerRequest`/`ServerResponse`;
- Một in-memory store tách khỏi HTTP routing.

Các endpoints:
- `GET /api/books?limit=10` — đọc query, trả JSON array;
- `GET /api/books/{id}` — đọc path variable, trả JSON hoặc 404;
- `POST /api/books` — dùng `body(CreateBook.class)`, validate, trả 201 + Location + JSON;
- `POST /api/books/batch` — dùng `body(ParameterizedTypeReference<List<CreateBook>>)`, validate và trả JSON array.
- Lỗi `IllegalArgumentException` do input tự kiểm tra được router `onError` map thành 400 JSON.

**Đây là ví dụ học tập:** dùng in-memory concurrent map; không có database, user auth, CSRF, CORS hoặc persistent storage. Những bảo vệ ấy phải bổ sung trong production. `CREATE` trong demo có thể được gọi không xác thực; tuyệt đối không triển khai công khai như service production.

### 9.1. POM/Dependencies

Tạo Maven project tại [Spring Initializr](https://start.spring.io/) với:
- Spring Boot 3.5.x, Java 17+;
- Dependency **Spring Web** (`spring-boot-starter-web`).

Fragment `pom.xml` do Initializr quản lý version:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

Spring Web starter mang Spring MVC, Jackson JSON converter và embedded Tomcat mặc định. Không cần `spring-boot-starter-webflux`; functional MVC dùng `web.servlet.function`.

### 9.2. Một file Java đầy đủ

Tạo file:

```text
src/main/java/com/example/demo/DemoApplication.java
```

Toàn bộ nội dung:

```java
package com.example.demo;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> bookRoutes(BookHandler handler) {
        return RouterFunctions.route()
                .GET("/api/books", handler::list)
                .GET("/api/books/{id}", handler::get)
                .POST("/api/books", handler::create)
                .POST("/api/books/batch", handler::createBatch)
                .onError(IllegalArgumentException.class,
                        (ex, request) -> ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ApiError("invalid_request")))
                .build();
    }
}

record Book(long id, String title) {
}

record CreateBook(String title) {
}

record ApiError(String error) {
}

@Component
class BookHandler {

    private final AtomicLong nextId = new AtomicLong();
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    public ServerResponse list(ServerRequest request) {
        int limit = Integer.parseInt(
                request.param("limit").orElse("10"));

        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit out of range");
        }

        List<Book> result = books.values().stream()
                .sorted(Comparator.comparingLong(Book::id))
                .limit(limit)
                .toList();

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .body(result);
    }

    public ServerResponse get(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));
        Book found = books.get(id);

        if (found == null) {
            return ServerResponse.notFound().build();
        }

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(found);
    }

    public ServerResponse create(ServerRequest request)
            throws Exception {

        // HttpMessageConverter đọc JSON -> CreateBook.
        CreateBook command = request.body(CreateBook.class);
        validate(command);

        long id = nextId.incrementAndGet();
        Book created = new Book(id, command.title().trim());
        books.put(id, created);

        // Location dùng URI tương đối, không tin raw Host headers.
        return ServerResponse.created(
                        URI.create("/api/books/" + id))
                .contentType(MediaType.APPLICATION_JSON)
                .body(created);
    }

    public ServerResponse createBatch(ServerRequest request)
            throws Exception {

        // Giữ generic type List<CreateBook> cho JSON converter.
        List<CreateBook> commands = request.body(
                new ParameterizedTypeReference<List<CreateBook>>() {});

        if (commands == null
                || commands.isEmpty()
                || commands.size() > 5) {
            throw new IllegalArgumentException("invalid batch size");
        }

        // Validate tất cả trước khi ghi vào map.
        commands.forEach(this::validate);

        List<Book> created = commands.stream()
                .map(command -> {
                    long id = nextId.incrementAndGet();
                    Book book = new Book(id, command.title().trim());
                    books.put(id, book);
                    return book;
                })
                .toList();

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(created);
    }

    private void validate(CreateBook command) {
        if (command == null
                || command.title() == null
                || command.title().isBlank()
                || command.title().length() > 120) {
            throw new IllegalArgumentException("invalid title");
        }
    }
}
```

### 9.3. Kiểm tra tính độc lập của ví dụ

Đoạn code nằm **trong một Java file** với một public class `DemoApplication` và các class/record khác package-private. JDK 17 hỗ trợ `record`, method references và `Stream.toList()`. `RouterFunctions.route()` gọi đúng package của Servlet MVC. Body input parse bằng `ServerRequest.body(...)`, output viết bằng `ServerResponse.body(...)`. Jackson converter từ Spring Web starter có thể serialize các record.

Logic store dùng `AtomicLong` và `ConcurrentHashMap` để tránh trùng ID khi nhiều requests đồng thời; **không** có atomic transaction cho toàn batch và không có persistence khi restart. Trong production, dùng transactional service/repository và kiểm tra quyền/constraints trước khi ghi.

Handler không catch JSON parsing exceptions để làm chúng biến thành success. Router `onError(IllegalArgumentException.class,...)` chỉ xử lý loại exception tương ứng. Lỗi JSON malformed/converter không đọc được có thể được Spring exception resolvers xử lý với status tương ứng tùy MVC config, nhưng nếu cần một JSON lỗi thống nhất phải thêm error handling có chủ đích.

**Vấn đề routing:** `POST /api/books/batch` và `GET /api/books/{id}` không xung đột vì HTTP method khác. Nếu sau này bổ sung `GET /api/books/batch`, phải đánh giá static path vs variable path và thứ tự/độ cụ thể, tránh mapping ambiguity.

### 9.4. Chạy ứng dụng

Ở root project:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell / CMD
mvnw.cmd spring-boot:run
```

Server mặc định `http://localhost:8080` nếu chưa thay `server.port` hoặc Servlet context path.

### 9.5. Test 1: GET danh sách rỗng

```bash
curl -i 'http://localhost:8080/api/books'
```

Dự kiến:
- Status `200 OK`.
- `Content-Type: application/json`.
- Body:

```json
[]
```

Store ban đầu không có sách; không có lý do trả 404 cho một danh sách hợp lệ nhưng rỗng. `Cache-Control: no-store` được handler đặt ở list response.

### 9.6. Test 2: POST JSON object thành Book

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"Spring MVC.fn"}'
```

Dự kiến:

```http
HTTP/1.1 201
Location: /api/books/1
Content-Type: application/json

{"id":1,"title":"Spring MVC.fn"}
```

Lưu ý `1` là ID đầu tiên khi app vừa khởi động và chưa có POST nào khác; chạy test khác trước sẽ tạo ID khác. Spring/Jackson có thể thêm headers hoặc serialize JSON whitespace khác, nên xem status, Location và JSON fields thay vì so nguyên string.

### 9.7. Test 3: GET theo ID

```bash
curl -i 'http://localhost:8080/api/books/1'
```

Khi record ID 1 tồn tại, status 200, JSON:

```json
{"id":1,"title":"Spring MVC.fn"}
```

Gọi ID không tồn tại:

```bash
curl -i 'http://localhost:8080/api/books/99999'
```

Dự kiến status **404 Not Found**. Response body với `ServerResponse.notFound().build()` có thể rỗng, tùy error dispatch/container; không khẳng định một JSON lỗi nhất định.

### 9.8. Test 4: POST generic JSON array

```bash
curl -i -X POST 'http://localhost:8080/api/books/batch' \
  -H 'Content-Type: application/json' \
  --data-binary '[{"title":"Java"},{"title":"Spring"}]'
```

Dự kiến status **200 OK**, JSON array có hai object với ID tiếp theo:

```json
[
  {"id":2,"title":"Java"},
  {"id":3,"title":"Spring"}
]
```

Con số ID như trên giả định chỉ có POST từ bước 2 trước đó. Đây là ví dụ điển hình chứng minh converter cần biết generic element type để tạo `List<CreateBook>`.

### 9.9. Test 5: sai query limit

```bash
curl -i 'http://localhost:8080/api/books?limit=0'
```

`list()` ném `IllegalArgumentException` và router `onError(...)` trả **400 Bad Request**, body:

```json
{"error":"invalid_request"}
```

Tương tự `limit=abc` cũng gây `NumberFormatException`, là subclass của `IllegalArgumentException`, nên được router error handler xử lý. Không đưa nội dung exception gốc lên client.

### 9.10. Test 6: DTO validation

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"  "}'
```

Dự kiến **400** với error DTO, vì `title.isBlank()` là true. Trong ví dụ, logic validation là manual Java checks; ứng dụng thật nên dùng shared Bean Validation/Validator khi cần rules phức tạp.

### 9.11. Test 7: lỗi JSON malformed

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":'
```

Jackson converter không thể parse; request phải bị từ chối, không có book mới. Status thực tế thường thuộc nhóm **4xx** do MVC exception handling, nhưng định dạng error body cụ thể cần kiểm thử với Boot/Spring version và global exception configuration. Nếu API contract yêu cầu JSON lỗi cố định, đăng ký error handler cho `HttpMessageNotReadableException` và trả 400. Đừng test mong `{"error":"invalid_request"}` của router `onError(IllegalArgumentException)`, vì parse error không nhất thiết thuộc kiểu đó.

### 9.12. Windows PowerShell

Dùng `curl.exe` (không dùng alias `curl` khi muốn cú pháp curl truyền thống):

```powershell
curl.exe -i "http://localhost:8080/api/books"
curl.exe -i "http://localhost:8080/api/books?limit=5"
```

Khi gửi JSON phức tạp, cách đáng tin là tạo file `request.json` UTF-8:

```json
{"title":"Spring MVC.fn"}
```

Sau đó:

```powershell
curl.exe -i -X POST "http://localhost:8080/api/books" -H "Content-Type: application/json" --data-binary "@request.json"
```

Cách này tránh các biến thể escape dấu quote của native commands giữa các phiên bản PowerShell.

## 10. Test chức năng bằng MockMvc

Với Spring Boot 3.5.x, thêm test starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Test ở `src/test/java/com/example/demo/FunctionalBookTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FunctionalBookTest {

    @Autowired
    MockMvc mvc;

    @Test
    void canCreateAndReadThroughFunctionalRoutes() throws Exception {
        MvcResult created = mvc.perform(
                        post("/api/books")
                                .contentType("application/json")
                                .content("{\"title\":\"Demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Demo"))
                .andReturn();

        String location =
                created.getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo"));
    }

    @Test
    void invalidInputIsHandledAs400() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType("application/json")
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("invalid_request"));
    }

    @Test
    void genericBatchReadsTypedList() throws Exception {
        mvc.perform(post("/api/books/batch")
                        .contentType("application/json")
                        .content(
                                "[{\"title\":\"A\"},{\"title\":\"B\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("A"))
                .andExpect(jsonPath("$[1].title").value("B"));
    }
}
```

Các tests không giả định ID tuyệt đối vì store dùng `AtomicLong` và application context có thể tái sử dụng giữa tests. Test chỉ kiểm tra route, converter, status/body và Location; cần thêm tests cho:
- Sai/mất Content-Type;
- Accept không tương thích và 406/route predicate;
- Malformed JSON;
- Path ID không phải số;
- Batch rỗng, batch quá 5 items;
- Authorization/CSRF/rate limits trong production;
- Concurrency và persistence nếu dùng database.

Chạy:

```bash
# Linux / macOS
./mvnw test

# Windows
mvnw.cmd test
```

## 11. Cấu hình message converters khi mặc định chưa đủ

Spring Boot auto-register converters từ classpath. Với API JSON thông thường, ưu tiên giữ auto-config và cấu hình Jackson qua các phương tiện được Spring Boot hỗ trợ. Chỉ tùy biến converters khi có nhu cầu rõ như media type độc quyền, codec dữ liệu nhị phân hoặc serialization đặc biệt.

### 11.1. extendMessageConverters thay vì thay toàn danh sách

```java
@Configuration
class MvcConversionConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        // Kiểm tra thứ tự/type, thêm hoặc điều chỉnh converter
        // mà vẫn giữ các defaults cần thiết.
    }
}
```

Đây là **snippet cấu hình**, cần imports `@Configuration`, `WebMvcConfigurer`, `HttpMessageConverter`, `List`. Khi dùng `configureMessageConverters(...)` (kiểu cấu hình đời Spring 6.x), bạn có thể **thay thế cấu hình defaults** nếu không thêm lại converters. Tùy phiên bản Spring 7 có API config dạng builder mới, nên không copy chữ ký giữa các major versions.

**Đề xuất:** nếu chỉ muốn đổi format Jackson date hoặc field naming, nên cấu hình Jackson thay vì tự thay converter list. Nếu cần CSV, có thể viết custom `HttpMessageConverter` có canRead/canWrite/MediaType rõ ràng, nhưng phải tính đến security, streaming và format parsing.

### 11.2. Converter selection và content negotiation

Khi đọc request body:

```text
Target: CreateBook.class
Content-Type: application/json
Converter candidate: Jackson JSON
-> canRead(CreateBook.class, application/json)
-> deserialize
```

Khi viết response:

```text
Body: Book record
Response contentType: application/json
Converter candidate: Jackson JSON
-> canWrite(Book.class, application/json)
-> serialize
```

Khi không có converter phù hợp, MVC không thể thành công chỉ bằng cách gọi `.body(value)`. Error có thể là 415 cho request media type không được hỗ trợ, 406 cho response negotiation, hoặc server-side converter failure/500 tùy vị trí lỗi và error handler. **Test status và error contract thực tế**, không mặc định mọi route predicate failure đều tạo 415/406.

### 11.3. Form data không giống JSON body

Đối với `application/x-www-form-urlencoded`:

```http
POST /api/forms
Content-Type: application/x-www-form-urlencoded

name=Lan&city=Hanoi
```

Form fields thường được expose qua request parameters, khác với JSON object body. Với `ServerRequest.bind(...)` (Spring 6.1+), có thể bind request parameters, URI variables hoặc headers theo DataBinder. Ví dụ:

```java
CreateProfile form = request.bind(
        CreateProfile.class,
        binder -> binder.setAllowedFields("displayName", "city"));
```

Đây là snippet: cần `CreateProfile` có kiểu bindable và customizer phù hợp. `setAllowedFields(...)` chỉ ràng buộc trường cho DataBinder, không tự làm JSON allowlist khi handler dùng `request.body(CreateProfile.class)` với Jackson. **Không** bind trực tiếp JPA entity chứa quyền admin/billingId.

### 11.4. Multipart file upload

File upload không nên được đối xử như một JSON DTO chứa byte array khổng lồ. Đối với Servlet MVC multipart, kiểm tra Spring multipart resolver, `MultipartFile`/`Part`, request size limits, metadata validation và private storage. Với Functional MVC, có thể cần truy cập Servlet multipart API hoặc request binding được framework hỗ trợ; không mặc định `request.body(MyDto.class)` tự xử lý file/stream như JSON.

Ví dụ cấu hình Spring Boot 3.x:

```properties
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

Những thuộc tính trên dành cho multipart, **không phải universal hard cap** cho JSON/mọi request payload. Bảo vệ JSON lớn cần thêm cấu hình edge/container hoặc bounded read policy.

## 12. Xử lý lỗi trong Functional MVC

### 12.1. Router-level onError

```java
RouterFunction<ServerResponse> routes = RouterFunctions.route()
        .GET("/api/items/{id}", handler::get)
        .onError(IllegalArgumentException.class,
                (ex, request) -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiError("invalid_request")))
        .build();
```

Đây là snippet dùng `handler` đã định nghĩa. `onError(Class<? extends Throwable>, ...)` áp dụng cho exception phù hợp xuất hiện trong scope router builder. Đây không phải thay thế cho security error handling hay toàn bộ Servlet container exception handling. Một exception nảy sinh **trước** khi route được tìm thấy hoặc từ Servlet Filter ngoài MVC không mặc định đi qua router onError.

### 12.2. Bọc conversion exceptions có chọn lọc

Nếu contract yêu cầu JSON 400 cho malformed JSON, có thể bổ sung error handler cho `HttpMessageNotReadableException`:

```java
.onError(HttpMessageNotReadableException.class,
        (ex, request) -> ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("malformed_json")))
```

Đây là **fragment** thêm vào router builder và cần import `org.springframework.http.converter.HttpMessageNotReadableException`. Không cho generic error handler bắt mọi exception và trả 400, vì lỗi của service/DB phải được phân loại khác.

### 12.3. Handler trả lỗi thay vì throw

Nếu kiểm tra input đơn giản, trả ServerResponse rõ ràng có thể dễ hơn:

```java
if (input == null || input.title() == null) {
    return ServerResponse.badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ApiError("title_required"));
}
```

Nếu có nhiều endpoints, dùng shared validation/service để tránh lặp các quy tắc. Error responses nên nhất quán với API contract, ví dụ RFC 9457 Problem Details khi phù hợp; WebMvc.fn không tự bắt mọi domain exception và biến thành đúng DTO bạn muốn.

### 12.4. @ControllerAdvice và WebMvc.fn có giống nhau không?

`@ControllerAdvice` được dùng mạnh nhất với annotated controllers (global `@ExceptionHandler`, `@InitBinder`, `@ModelAttribute`). Functional routing có cơ chế **`before/after/filter/onError` riêng**. Không được giả định mọi `@ControllerAdvice @ExceptionHandler` lập tức bao trọn functional handler theo cùng thứ tự/semantics với `@RequestMapping` methods. Nên kiểm thử framework exception resolvers, functional `onError` và security filter riêng theo đúng Spring version đang chạy.

### 12.5. Response committed

Khi `ServerResponse` đã bắt đầu ghi body và response committed, không thể chắc chắn đổi status hay viết một JSON error object mới. Với streaming/file output, cần xác định trước validation, headers, authorization, timeout, và cách báo lỗi khi stream dang dở. Đối với DTO JSON nhỏ, lỗi serialize có thể xảy ra lúc converter ghi; đừng giả định toàn quá trình luôn xử lý được bằng status 400.

## 13. Các lỗi triển khai thường gặp

| Triệu chứng | Nguyên nhân điển hình | Hướng sửa |
| --- | --- | --- |
| Import sai `web.reactive.function.server` | Copy ví dụ WebFlux.fn | Dùng `web.servlet.function` |
| Không tìm thấy `request.bodyToMono()` | WebMvc.fn có `body(Class)`, không có bodyToMono | Dùng đúng API, không thêm WebFlux vô cớ |
| Handler không được gọi | `RouterFunction` không đăng ký bean, route/predicate sai | Tạo `@Bean RouterFunction<ServerResponse>` và test |
| POST JSON trả 404 | Path/method/predicate không match | Kiểm tra routing và contentType/accept predicates |
| `request.body(List.class)` trả `List<Map>` | Generic type erasure | Dùng `ParameterizedTypeReference<List<Dto>>` |
| JSON lỗi thành 500/response không như mong muốn | Converter exception không được map đúng | Route/onError/global error config phù hợp |
| `@Valid` đặt đâu đó không chạy | Bare `request.body(...)` không tự chạy validation như annotation MVC | Gọi Validator/manual validation |
| `String` bị serialize không đúng kiểu | Converter/media type không phù hợp | Kiểm tra Content-Type và converter ordering |
| 415 Unsupported Media Type | Request Content-Type không được converter hỗ trợ | Gửi đúng media type/thêm converter đúng |
| 406 Not Acceptable | Client Accept không khớp representation | Cấu hình produces/negotiation rõ |
| PUT/POST đọc body rỗng | Servlet filter đã tiêu thụ stream | Không đọc thô trước, dùng bounded cache-on-read |
| 201 thiếu Location | Response chỉ trả status 201 | Dùng `ServerResponse.created(uri)` |
| 204 mà client mong JSON | `noContent().build()` cố tình không có body | Chọn 200/201 nếu cần representation |
| Request JSON quá lớn gây heap pressure | Converter tạo object graph lớn | Payload size limit/streaming strategy |
| Batch ghi một phần trước khi validate xong | Validate và mutate xen kẽ | Validate cả batch trước, dùng transaction |
| Multipart upload path traversal | Tin original filename của client | Server-generated key/private storage |
| Security bypass | Có router route nhưng không có auth | Spring Security FilterChain + object ACL |
| @ControllerAdvice không bắt như tưởng tượng | Functional router có error processing riêng | Dùng router `onError` hoặc test resolver |
| Code dùng `@EnableWebMvc` làm mất Boot defaults | Tự quản lý MVC config không cần thiết | Giữ Boot auto-config và WebMvcConfigurer |
| Response lớn bị cache/log toàn byte[] | Dùng `readAllBytes`/response wrapper không giới hạn | Streaming + quotas + bounded observation |

## 14. Bảo mật và giới hạn tài nguyên

WebMvc.fn mang cùng bề mặt tấn công với annotated MVC endpoints:

1. **Authorization:** cả `GET /api/books/{id}` lẫn `POST /api/books` phải kiểm tra quyền theo resource/action; RouterFunction không tự cung cấp authentication.
2. **Mass assignment:** JSON body chỉ bind vào **DTO tối thiểu**, không bind nguyên domain entity nhạy cảm.
3. **DoS/payload:** giới hạn body size, số phần tử batch, chiều sâu JSON, thời gian đọc và concurrent requests ở proxy/container/parser.
4. **CSRF/CORS:** khi dùng session cookies hoặc cross-origin frontend, áp Spring Security CSRF và CORS allowlist đúng; HTTP router không tự giải quyết.
5. **Error exposure:** không trả raw stack trace, SQL fragments, filesystem paths hoặc tokens trong lỗi.
6. **Content type:** chặn binary/HTML/XML không nằm trong hợp đồng, kiểm tra sniffing và file upload riêng.
7. **Logging:** chỉ log metadata đã lọc/redact, tránh raw Authorization/Cookie/body chứa PII.
8. **Transactions:** một handler có thể gọi service `@Transactional`; bản thân `RouterFunction` không tự tạo giao dịch nguyên tử.
9. **Timeout/streaming:** với body lớn hoặc asynchronous publishers, xác định chính sách timeout/error/response commit; WebMvc.fn trên Servlet stack không tự biến socket writes thành non-blocking WebFlux I/O.
10. **Cache:** nếu response theo user, chọn `Cache-Control: private/no-store` và không để shared caches trộn dữ liệu qua cookies/headers.

**Không coi tên Functional là không có side effects:** handler có thể thay DB, ghi tệp, gửi email. Những side effects vẫn cần idempotency, validation, permissions và error recovery như mọi HTTP endpoint khác.

## 15. Checklist QA và code review

- [ ] Đúng Spring MVC stack; tất cả imports chính thuộc `web.servlet.function`.
- [ ] RouterFunction đã là Spring bean và route path/method/predicates đã test.
- [ ] Handler dùng `ServerRequest.body(Class)` hoặc `body(ParameterizedTypeReference)` để parse DTO.
- [ ] JSON `Content-Type` hợp lệ, JSON converter có mặt và các custom converters không phá defaults.
- [ ] Query/path params được parse và giới hạn giá trị.
- [ ] Validation tách khỏi converter; DTO không chứa trường bị client kiểm soát ngoài hợp đồng.
- [ ] Response status, `Content-Type`, `Location`, `Cache-Control` và body hợp lệ.
- [ ] Có 400/404/409/415/406/error policy tùy use case, không làm mọi lỗi thành 200.
- [ ] Batch input có size/quota và transaction nếu có nhiều writes.
- [ ] CORS/CSRF/auth/ACL nằm trong security architecture, không dựa vào route name.
- [ ] Kiểm thử MockMvc cả success, malformed body, generic list, negative cases.
- [ ] Không đọc cùng Servlet body hai lần hoặc log toàn payload không giới hạn.
- [ ] Không nhầm `ServerResponse.body(Object)` với `WebFlux.fn bodyValue/publisher-based API`.
- [ ] Với file/stream, kiểm soát response committed, buffering và timeout.
- [ ] Khi nâng Spring Boot 4/Spring Framework 7, kiểm tra lại các method signatures, starter/test packages và converter configuration APIs.

## 16. Tự kiểm tra kiến thức

**Câu 1.** Trong WebMvc.fn, đọc JSON body thành `CreateBook` bằng API nào?

**Đáp án:** `request.body(CreateBook.class)`. Converter phù hợp (thường Jackson) deserialize bytes; cần validation riêng.

**Câu 2.** Muốn đọc JSON array thành `List<CreateBook>` nhưng tránh bị `List<Map>`, làm gì?

**Đáp án:** `request.body(new ParameterizedTypeReference<List<CreateBook>>() {})` để giữ generic element type.

**Câu 3.** Trả `201 Created` kèm Location và JSON body?

**Đáp án:** `ServerResponse.created(URI.create("/api/books/42")).contentType(APPLICATION_JSON).body(book)`.

**Câu 4.** Trả `204 No Content`?

**Đáp án:** `ServerResponse.noContent().build()`. Không có JSON body theo 204 semantics.

**Câu 5.** `ServerResponse.ok().body(myPojo)` tự biến Java object thành JSON nếu không có converter không?

**Đáp án:** Không. Cần converter ghi tương thích với body type và media type.

**Câu 6.** Vì sao `request.body(List.class)` không lý tưởng cho `List<Book>`?

**Đáp án:** Type erasure làm mất generic element metadata; converter có thể tạo maps thay Book instances.

**Câu 7.** WebMvc.fn có dùng `bodyToMono()` không?

**Đáp án:** Không phải API chính của Servlet WebMvc.fn; đó là API WebFlux.fn. MVC dùng `body(Class)` và `body(ParameterizedTypeReference)`.

**Câu 8.** Có `request.body(Dto.class)` rồi thì Bean Validation/ACL tự chạy không?

**Đáp án:** Không. Converter parse data, còn validation và authorization phải được áp dụng rõ ràng.

**Câu 9.** Nếu predicate `contentType(APPLICATION_JSON)` không match thì chắc chắn server trả 415?

**Đáp án:** Không. Predicate có thể khiến router không tìm được handler, do đó HTTP status tùy mappings/error handling. Cần integration test nếu muốn 415/406 chính xác.

**Câu 10.** Vì sao nên test create endpoint rồi GET theo `Location` thay vì hardcode ID = 1?

**Đáp án:** In-memory store/DB sequence có thể đã tăng do tests khác. Header Location phản ánh ID thực của resource vừa tạo.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — Functional Endpoints (WebMvc.fn)](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-functional.html): tổng quan RouterFunction/HandlerFunction/ServerRequest/ServerResponse, filters, nested routes và chạy trong DispatcherServlet.
- [Spring Framework 6.2 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/web/servlet/function/ServerRequest.html): `body(Class)`, `body(ParameterizedTypeReference)`, params, headers, bind.
- [Spring Framework 6.2 — ServerResponse.BodyBuilder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/servlet/function/ServerResponse.BodyBuilder.html): `body(Object)`, generic body, response-building.
- [Spring Framework 6.2 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.Builder.html): GET/POST, before/after/filter/onError.
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): converters, JSON/text/form/binary, configuration.
- [Spring Framework 6.2 — MVC Config Message Converters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/message-converters.html): `WebMvcConfigurer` và converter customization.
- [Spring Framework 6.2 — Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html): HTTP errors và ProblemDetail.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): Spring MVC auto-configuration, HTTP converters và routing support.
- [Spring Framework — WebFlux Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-functional.html): để đối chiếu `bodyToMono` và HTTP message codecs, **không** copy WebFlux-specific imports vào Servlet MVC.
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): filter chain, authentication, authorization và request processing.

**Kết luận:** trong Spring WebMvc.fn, `RouterFunction` tìm route, `HandlerFunction` xử lý `ServerRequest` và trả `ServerResponse`, còn `HttpMessageConverter` chuyển request/response body giữa bytes và Java values. Đọc DTO bằng `body(Class)`, đọc generic list bằng `body(ParameterizedTypeReference)`, tạo status/headers/body bằng `ServerResponse` và test thực tế bằng MockMvc. Phải tách bạch conversion với validation/security, đặt payload limits và không nhầm Servlet Functional MVC với Reactive WebFlux.fn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-functional-endpoints-005">HandlerFilterFunction, xử lý lỗi và tổ chức các tuyến chức năng</a>

<details>
<summary>Xem chi tiết</summary>

# HandlerFilterFunction, xử lý lỗi và tổ chức các tuyến chức năng trong Spring MVC

## 1. Bài toán: tránh lặp logic khi API có nhiều functional routes

Spring MVC không bắt buộc mỗi HTTP endpoint phải là một method gắn `@GetMapping` hay `@PostMapping`. Bên cạnh annotated controllers, Spring hỗ trợ **WebMvc.fn**, nơi route và handler là các hàm có hợp đồng rõ ràng:

- `RouterFunction<ServerResponse>` chọn handler phù hợp với request.
- `HandlerFunction<ServerResponse>` nhận `ServerRequest` và trả `ServerResponse`.
- `HandlerFilterFunction<ServerResponse, ServerResponse>` bao quanh lời gọi handler để xử lý những mối quan tâm chung như logging, đo thời gian, kiểm soát đầu vào hay một kiểu short-circuit theo nghiệp vụ.
- `RouterFunctions.Builder` cho phép nhóm routes theo prefix/path/predicate, thêm `before`/`after`/`filter` và khai báo `onError` theo phạm vi.

Khi API phát triển, ba vấn đề thường xuất hiện:

1. Mười route cùng cần log hoặc kiểm tra header, khiến code trong từng handler bị lặp.
2. Một exception nghiệp vụ phải được ánh xạ thành HTTP status/body giống nhau, nhưng handler nào cũng có `try/catch` riêng.
3. Routes cho các nhóm `/api/v1/notes`, `/api/v1/orders`, `/admin/**` được khai báo không theo thứ tự, gây route shadowing hoặc filter lan sang endpoint không liên quan.

Giải pháp không phải đặt mọi thứ trong một filter khổng lồ. Cần hiểu **phạm vi của router functions**, vị trí của từng filter và nơi xử lý exception.

**Baseline:** Java 17+, Spring Framework **6.2.x** / Spring Boot **3.5.x**, Spring MVC trên Servlet stack. Toàn bộ imports cho functional API thuộc **`org.springframework.web.servlet.function`**; **không** dùng `org.springframework.web.reactive.function.server` của WebFlux. Code mẫu dùng in-memory repository để tập trung vào tổ chức routes; không bao gồm database hay authentication production.

## 2. Luồng xử lý WebMvc.fn trong DispatcherServlet

Functional MVC không tạo một HTTP server độc lập với Spring MVC. Nó chạy trong hạ tầng Servlet/`DispatcherServlet`:

```text
HTTP Request
     |
     v
Servlet FilterChain
     |
     v
DispatcherServlet
     |
     v
HandlerMappings
  RouterFunctionMapping -> tìm RouterFunction matching
     |
     v
HandlerFunctionAdapter
     |
     v
HandlerFilterFunction(s)
  pre logic -> next.handle(request) -> post logic
     |
     v
HandlerFunction(ServerRequest) -> ServerResponse
     |
     v
HttpMessageConverter / Servlet response writer
     |
     v
HTTP status + headers + body
```

Nếu một `RouterFunction` không match, cơ chế routing trả **`Optional.empty()`** và DispatcherServlet có thể xét những mappings khác. Nếu có handler được chọn, các filters gắn với router đó mới có cơ hội thực thi.

Trong WebMvc.fn:

```java
@FunctionalInterface
interface HandlerFunction<T extends ServerResponse> {
    T handle(ServerRequest request) throws Exception;
}
```

Đây là **chữ ký khái niệm** của API Spring, không cần tự khai báo interface trong project. `ServerRequest` và `ServerResponse` dùng cách tiếp cận bất biến/immutable contract; bạn trả một response object do builder tạo, không trực tiếp viết toàn bộ Servlet output như code thấp tầng.

Ví dụ ngắn:

```java
RouterFunction<ServerResponse> router =
        RouterFunctions.route()
                .GET("/hello", request ->
                        ServerResponse.ok()
                                .contentType(MediaType.TEXT_PLAIN)
                                .body("Hello WebMvc.fn"))
                .build();
```

Đây là **fragment**, cần imports WebMvc.fn, `MediaType` và đăng ký thành bean `@Bean` nếu muốn DispatcherServlet tìm được. Nó chưa có filters/error handling, nhưng là điểm xuất phát.

## 3. `HandlerFilterFunction` thực sự nhận tham số gì?

Trong Spring MVC:

```java
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
```

Hợp đồng phương thức chính:

```java
R filter(ServerRequest request, HandlerFunction<T> next)
        throws Exception;
```

Trong đó:

- `request` là functional request của route match.
- `next` đại diện **handler hoặc filter kế tiếp** trong chuỗi.
- `next.handle(request)` cho phép thực thi phần tiếp theo.
- `ServerResponse` trả về là response cuối cho xử lý route sau khi filters bao quanh.
- Filter có thể **không** gọi next và tự trả response, ví dụ chặn request thiếu header bắt buộc.
- Filter có thể dùng `try/finally` để cleanup context hoặc đo duration khi downstream ném exception.
- Filter có thể bắt **những exception đã xác định**, trả response khác hoặc chuyển exception cho `onError`/MVC exception resolvers xử lý.

### 3.1. Dạng cơ bản: trước → next → sau

```java
HandlerFilterFunction<ServerResponse, ServerResponse> timer =
        (request, next) -> {
            long started = System.nanoTime();
            try {
                return next.handle(request);
            } finally {
                long elapsedNanos = System.nanoTime() - started;
                // Record metric đã được chuẩn hóa.
            }
        };
```

Đây là **fragment**, không phải runnable class; chưa có metrics sink. `finally` quan trọng vì cleanup/recording vẫn chạy khi downstream ném exception.

Ví dụ minh họa một filter có thể trả lỗi sớm:

```java
HandlerFilterFunction<ServerResponse, ServerResponse> requireDemoHeader =
        (request, next) -> {
            String value = request.headers()
                    .firstHeader("X-Demo-Client");
            if (!"learning".equals(value)) {
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiError(
                                "DEMO_HEADER_REQUIRED",
                                "Missing demo client header"));
            }
            return next.handle(request);
        };
```

Fragment giả định có record `ApiError`. Header `X-Demo-Client` **chỉ là quy tắc mô phỏng cho bài học**, không được dùng như authentication. Client hoàn toàn có thể tự tạo header. Với API thật, authentication/authorization do **Spring Security `SecurityFilterChain`** xử lý, cộng với quyền trên tài nguyên.

### 3.2. Filter không phải Servlet Filter hay MVC HandlerInterceptor

| Cấp | API | Thời điểm/vùng tác dụng |
| --- | --- | --- |
| **Servlet container** | `jakarta.servlet.Filter` | Trước/bao quanh Servlet, có URL mapping + dispatcher types |
| **Spring Security** | `FilterChainProxy` / `SecurityFilterChain` | Cơ chế bảo vệ HTTP request, authentication, authorization, CSRF |
| **Spring MVC functional route** | `HandlerFilterFunction` | Sau khi một router function match; chỉ nằm trong phạm vi router được gắn |
| **Spring MVC annotated controller** | `HandlerInterceptor` | Trên handler được MVC chọn qua interceptor registrations |
| **Exception infrastructure** | `HandlerExceptionResolver`, `@ControllerAdvice` | Có các contract xử lý lỗi khác và thứ tự ưu tiên riêng |

Vì handler filter nằm **bên trong functional routing**, nó không tự chạy trên:

- Endpoint annotated `@RestController` không thuộc router function đó.
- Static resources do resource handler khác phục vụ.
- Servlet khác, filter chain trước `DispatcherServlet`.
- Request **không match** bất cứ route nào trong router đã gắn filter.
- Các request bị Spring Security từ chối **trước khi** vào handler.

**Không dùng `HandlerFilterFunction` thay cho Spring Security** trên những ranh giới bảo mật bắt buộc của toàn ứng dụng.

## 4. `RouterFunctions.route()`: builder và thứ tự routes

Tạo router với các routes HTTP:

```java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .GET("/api/v1/notes", handler::list)
                .GET("/api/v1/notes/{id}", handler::get)
                .POST("/api/v1/notes", handler::create)
                .build();
```

Đây là fragment, giả định `handler` có các methods đúng kiểu `ServerRequest -> ServerResponse`.

### 4.1. Route đầu tiên khớp là route được chọn

**Quan trọng:** WebMvc.fn đánh giá `RouterFunction` **theo thứ tự khai báo**. Nếu một route khái quát match trước, route đặc hiệu phía sau có thể không bao giờ được chọn:

```java
// Dễ sai: route biến động có thể match "stats" trước.
RouterFunctions.route()
        .GET("/api/v1/notes/{id}", handler::get)
        .GET("/api/v1/notes/stats", handler::stats)
        .build();
```

Trong ví dụ trên, GET `/api/v1/notes/stats` có thể đi vào `get` với `id="stats"` rồi thất bại khi parse ID. Khai báo static route trước:

```java
RouterFunctions.route()
        .GET("/api/v1/notes/stats", handler::stats)
        .GET("/api/v1/notes/{id}", handler::get)
        .build();
```

Đây là khác biệt với annotated controller model, nơi framework có thuật toán chọn mapping đặc hiệu. **Đừng dựa vào heuristic “Spring sẽ tự chọn static path trước” trong WebMvc.fn**. Với overlapping predicates như `accept`, `contentType` hay nested wildcard, nguyên tắc first matching route vẫn cần được kiểm chứng bằng tests.

### 4.2. Route predicates và HTTP media types

`RequestPredicates` hỗ trợ điều kiện kết hợp:

```java
import static org.springframework.web.servlet.function.RequestPredicates.accept;
import static org.springframework.web.servlet.function.RequestPredicates.contentType;
```

Ví dụ:

```java
RouterFunctions.route()
        .GET("/notes", accept(MediaType.APPLICATION_JSON),
                handler::list)
        .POST("/notes", contentType(MediaType.APPLICATION_JSON),
                handler::create)
        .build();
```

Đây là fragment. `accept` xét thứ client có thể nhận trong response; `contentType` xét định dạng request body. Một request không match predicate có thể rơi qua routes khác hoặc nhận 404/405/406/415 tùy mappings/resolvers, **không** mặc định cứ sai Content-Type là mọi cấu hình trả 415.

Nếu yêu cầu API rõ ràng rằng POST chỉ nhận JSON, có thể dùng predicate và thêm error contract cho unsupported media types qua exception handling hoặc infrastructure. Handler vẫn phải validate nội dung đã deserialize.

## 5. Tổ chức routes bằng `path` và `nest`

Thay vì lặp `/api/v1` ở từng route, nhóm một prefix:

```java
RouterFunction<ServerResponse> routes =
        RouterFunctions.route()
                .path("/api/v1", api -> api
                        .GET("/notes/stats", handler::stats)
                        .GET("/notes/{id}", handler::get)
                        .GET("/notes", handler::list)
                        .POST("/notes", handler::create))
                .build();
```

`path("/api/v1", consumer)` tạo nested builder. Suffix route bên trong sẽ được ghép theo semantics của `RequestPredicate`. Dễ review hơn khi toàn bộ version API nằm trong cùng một group.

### 5.1. Nest theo predicate khác path

Ví dụ nhiều GET routes chỉ dành cho client nhận JSON:

```java
RouterFunctions.route()
        .path("/api/v1/notes", notes -> notes
                .nest(
                    RequestPredicates.accept(
                            MediaType.APPLICATION_JSON),
                    json -> json
                            .GET("/stats", handler::stats)
                            .GET("/{id}", handler::get)))
        .build();
```

Trong bài này, `nest` có ý nghĩa gom những routes dùng chung một predicate. Nó **không** tự thực thi validation nội dung JSON hay thêm Security. `accept` là điều kiện match trên Accept header; muốn giới hạn body JSON của POST, dùng `contentType` tương ứng.

### 5.2. Phạm vi filter theo nhóm

Xét hai routes:

```java
RouterFunctions.route()
        .GET("/health", healthHandler)
        .path("/api/v1", api -> api
                .GET("/notes", notesHandler)
                .filter(notesFilter))
        .build();
```

`notesFilter` thuộc nested builder `/api/v1`, **không** áp lên `GET /health`. Nếu thay vào đó gắn `.filter(notesFilter)` ở outer builder sau `path(...)`, filter có thể áp vào **cả /health và nhóm API**.

Tài liệu Spring nhấn mạnh filter thêm vào nested routes không chạy trên top-level routes. Điều này cho phép chia trách nhiệm:

- Shared API filter cho `/api/v1/**`.
- Narrow filter cho `/api/v1/admin/**`.
- Không yêu cầu auth/business header cho `/health`.
- Filter ở outer builder nếu thật sự phải áp tới toàn bộ routes đã xây.

Đừng giả định bất kỳ filter nào “tự áp toàn ứng dụng”. Kiểm tra chính xác builder/RouterFunction mà bạn gắn filter.

## 6. Ba cách thêm hành vi chung: `before`, `after`, `filter`

### 6.1. `before(Function<ServerRequest,ServerRequest>)`

Được dùng để xử lý/biến đổi functional request trước handler:

```java
.before(request -> {
    audit.info("route={} method={}",
            request.path(), request.method());
    return request;
})
```

Fragment giả định có `audit` logger. Nên log path đã kiểm soát/route template khi có thể, tránh log raw query/token.

Có thể dùng `ServerRequest.from(request)` để xây request wrapper bất biến:

```java
.before(request -> ServerRequest.from(request)
        .header("X-Lesson-Scope", "notes")
        .build())
```

Đây là minh họa request transformation. **Đừng biến một header tự thêm vào functional request thành bằng chứng authentication.** Với dữ liệu đáng tin, dùng service/validated context và chính sách phân quyền phù hợp.

### 6.2. `after(BiFunction<ServerRequest,ServerResponse,ServerResponse>)`

Sau khi handler trả `ServerResponse`, xử lý response object:

```java
.after((request, response) -> {
    audit.info("mvc_fn_status={}",
            response.statusCode().value());
    return response;
})
```

Dạng này giúp ghi metric/status của response thành công hoặc filter trả về sớm. **Không nên coi `after` là một `finally` cho mọi exceptions:** nếu downstream ném trước khi có `ServerResponse`, xử lý lỗi hoặc `filter try/finally` mới là chỗ thích hợp. Nếu trả response stream, phần xử lý **body thực tế** có thể diễn ra sau khi `after` trả về; metric ở đây chưa nhất thiết là thời gian gửi toàn bộ bytes tới client.

### 6.3. `filter(HandlerFilterFunction<...>)`

Dùng khi cần chủ động gọi hoặc bỏ qua `next.handle`:

```java
.filter((request, next) -> {
    String gate = request.headers()
            .firstHeader("X-Demo-Client");

    if (!"learning".equals(gate)) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "code", "DEMO_HEADER_REQUIRED"));
    }

    return next.handle(request);
})
```

`before` và `after` phù hợp với biến đổi request/response đơn giản; `filter` linh hoạt hơn khi cần **short-circuit**, phối hợp trước/sau trong một `try/finally` hoặc bắt một kiểu exception xác định.

### 6.4. Dùng static adapters cho filter có sẵn

`HandlerFilterFunction` có:

- `ofRequestProcessor(Function<ServerRequest,ServerRequest>)`.
- `ofResponseProcessor(BiFunction<ServerRequest,T,R>)`.
- `ofErrorHandler(Predicate<Throwable>, BiFunction<Throwable,ServerRequest,T>)`.
- `andThen(...)` để compose filters có kiểu tương thích.
- `apply(handler)` để áp filter vào handler.

Ví dụ fragment:

```java
HandlerFilterFunction<ServerResponse, ServerResponse> passThrough =
        HandlerFilterFunction.ofRequestProcessor(
                request -> request);

HandlerFilterFunction<ServerResponse, ServerResponse> trace =
        passThrough.andThen((request, next) -> {
            long started = System.nanoTime();
            try {
                return next.handle(request);
            } finally {
                long nanos = System.nanoTime() - started;
                // Record bounded metric only.
            }
        });
```

`andThen` compose theo thứ tự đã khai báo trong contract `HandlerFilterFunction`; khi có nhiều nested/outer builders, **đừng suy ra thứ tự chỉ từ cảm giác về decorator**. Hãy viết integration test ghi event order nếu phụ thuộc business vào thứ tự.

## 7. Xử lý lỗi bằng `RouterFunctions.Builder.onError`

Handler có thể ném exception vì resource không tồn tại, dữ liệu không hợp lệ hoặc server gặp lỗi. Với WebMvc.fn, builder hỗ trợ error mapping ngay trong router:

```java
.onError(NoteNotFoundException.class, (error, request) ->
        ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(
                        "NOTE_NOT_FOUND", "Note does not exist")))
```

Đây là fragment trong builder, giả định `NoteNotFoundException`/`ApiError` đã được định nghĩa. `onError` nhận:

1. Một `Class<? extends Throwable>`, hoặc
2. Một `Predicate<Throwable>` để quyết định lỗi nào được xử lý,

và một hàm sinh `ServerResponse` từ `Throwable` cùng `ServerRequest`.

### 7.1. Class-based mapping

```java
.onError(InvalidNoteException.class,
        (error, request) ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError(
                            "NOTE_INVALID", "Invalid note input")))
.onError(NoteNotFoundException.class,
        (error, request) ->
                ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError(
                            "NOTE_NOT_FOUND", "Note not found")))
```

Ưu điểm: typed business exceptions, ít phải xem message; dễ test error contract. Nên dùng **stable application error code** và message an toàn cho client, không trả raw SQL, stack traces hay token.

### 7.2. Predicate-based mapping

```java
.onError(error ->
        error instanceof IllegalArgumentException,
        (error, request) ->
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError(
                            "INVALID_ARGUMENT",
                            "Invalid request argument")))
```

Dùng predicate khi muốn match theo một nhóm lỗi hoặc điều kiện cụ thể, nhưng tránh `error -> true` cho tất cả rồi biến **bug nội bộ thành HTTP 400**. Một catch-all có thể che lấp lỗi lập trình, khiến monitoring bỏ sót 500.

### 7.3. `ofErrorHandler(...)` khi không dùng builder.onError

Từ filter API:

```java
HandlerFilterFunction<ServerResponse, ServerResponse> notFound =
        HandlerFilterFunction.ofErrorHandler(
            error -> error instanceof NoteNotFoundException,
            (error, request) ->
                ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError(
                        "NOTE_NOT_FOUND", "Note not found")));
```

Filter này có thể được thêm qua `RouterFunction.filter(...)` hoặc builder `.filter(notFound)`. Chọn **một chiến lược error mapping có cấu trúc**, tránh xử lý cùng lỗi ở nhiều lớp không rõ precedence.

### 7.4. Exceptions nào thuộc trách nhiệm `onError`?

`onError` là filter bao quanh **handler function** trong phạm vi router. Nó phù hợp cho lỗi ném **khi handler/filter xử lý request sau khi router match**.

Nó không phải global exception handler cho mọi thứ:

- Lỗi xảy ra **trước route match** (Servlet filter, Security, request parsing/dispatch trước functional handler) có thể không nằm trong phạm vi.
- Lỗi của controller gắn annotation ở router khác không nhất thiết đi qua `onError` của functional builder.
- Lỗi khi **serialize/write HTTP body sau khi response đã commit** có thể không thể chuyển thành JSON error mới; tùy message converter/container.
- Router **không match** không có exception để `onError` bắt; nó thường trở thành 404 hoặc mapping/exception khác do MVC xử lý.
- Invalid JSON body có thể phát sinh exception conversion; nếu không match custom types/predicate, nó sẽ đi qua MVC exception infrastructure.
- Khi đã gửi một phần response streaming, không hứa có thể thay 200 thành 500.

`@RestControllerAdvice` / `@ExceptionHandler` / `HandlerExceptionResolver` là những cơ chế khác. Với functional endpoints, **cách tường minh nhất cho lỗi nghiệp vụ cục bộ thường là `onError`**; global MVC exception resolvers có thể tham gia tùy đường xử lý exception/adapter và Spring configuration. Không giả định advice của annotated controllers tự thay thế mọi error mapping chức năng.

### 7.5. Không lộ thông tin nhạy cảm trong lỗi

Không nên:

```java
.onError(Throwable.class, (error, request) ->
        ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("debug", error.toString())))
```

Đây là **anti-pattern**: exception messages có thể chứa DB paths, SQL, request body, credentials hoặc private implementation details. Thay bằng lỗi chuẩn:

```json
{"code":"INTERNAL_ERROR","message":"Unexpected server error"}
```

Log nội bộ vẫn cần phân loại/giới hạn để điều tra. Không log toàn bộ `Authorization` header, Cookie, raw JSON body hoặc giá trị path không đáng tin.

## 8. Cấu trúc code khi ứng dụng có nhiều nhóm functional routes

Một cấu trúc có thể mở rộng:

```text
src/main/java/demo/webfn/
├─ WebFnApplication.java
├─ routes/
│  ├─ PublicRoutes.java
│  ├─ NotesRoutes.java
│  └─ OrdersRoutes.java
├─ handlers/
│  ├─ NotesHandler.java
│  └─ OrdersHandler.java
├─ service/
│  ├─ NoteService.java
│  └─ OrderService.java
├─ model/
│  └─ ApiError.java
└─ filters/
   ├─ RequestMetricsFilter.java
   └─ CorrelationIdFilter.java
```

Đây là **cấu trúc minh họa**, không phải tất cả các file phải tồn tại trong demo một file ở phần 10. Phân trách nhiệm:

- **Routes** chỉ khai báo HTTP path/method/predicate, gắn filters và onError.
- **Handlers** giải mã `ServerRequest`, gọi services, xây `ServerResponse`.
- **Services** chứa nghiệp vụ, persistence, transactions, validations.
- **Filters** chỉ xử lý mối quan tâm chung trong phạm vi rõ ràng.
- **Error mapping** đưa exception types thành HTTP contracts; tránh tạo response theo raw exception messages.
- **Spring Security** xử lý authorization bắt buộc và CSRF/CORS theo kiến trúc, không đẩy trách nhiệm này vào route builder.

### 8.1. Ghép các router functions

Một số cách hợp lệ:

```java
RouterFunction<ServerResponse> combined =
        notesRouter.and(ordersRouter);
```

Hoặc:

```java
RouterFunction<ServerResponse> combined =
        RouterFunctions.route()
                .add(notesRouter)
                .add(ordersRouter)
                .build();
```

Đây là **fragments**, giả định hai routers tồn tại. Việc ghép theo thứ tự có nghĩa router nào match đầu tiên sẽ được chọn. Nếu URL patterns có overlap, thứ tự ghép là một phần của contract cần test.

### 8.2. Nhiều bean `RouterFunction` và @Order

Trong Boot MVC, một `RouterFunction<ServerResponse>` được khai báo `@Bean` được `RouterFunctionMapping` phát hiện. Khi có nhiều router function beans, framework sắp xếp/kết hợp chúng. Nếu routing priority quan trọng, hãy cấu hình thứ tự tường minh phù hợp cho bean/router, xem Bean ordering/@Order của version đang dùng, và tránh hai routers đăng ký cùng route ngoài ý muốn.

Ví dụ **fragment**:

```java
@Bean
@Order(10)
RouterFunction<ServerResponse> publicRouter() {
    return RouterFunctions.route()
            .GET("/public/status",
                request -> ServerResponse.ok().body("ready"))
            .build();
}
```

Khi cùng request match nhiều router beans, test trực tiếp precedence thay vì giả định Spring chọn “specific” hơn như annotated controllers. Trong một nhóm cùng domain, gộp thành **một router bean** giúp giảm rủi ro ambiguity và kiểm soát thứ tự dễ hơn.

### 8.3. Prefix/version tổ chức hợp đồng API

Nhóm `/api/v1`, `/api/v2` riêng để:

- Tránh rơi nhầm handler version khác.
- Có error envelope/route filters độc lập khi cần.
- Cho phép migrate từng version với test độc lập.
- Không áp dụng filter dành cho `/api/v2` lên `/health` hoặc `/api/v1` một cách vô tình.

Nếu version được đàm phán qua header/media type thay vì path, dùng predicates hợp đồng tương ứng; không mặc định cứ có prefix `/v2` là đã xử lý hết thay đổi schema và version compatibility.

## 9. Một số điều về HandlerFunction và ServerResponse

### 9.1. Đọc path variables, query, body

```java
String id = request.pathVariable("id");

String search = request.param("q").orElse("");

NewNote input = request.body(NewNote.class);
```

Đây là fragment trong handler với `ServerRequest request`. `pathVariable` chỉ hợp lệ sau route có `{id}` đã match; `param` trả `Optional<String>`. `body(Class)` dùng message converters để đọc request body theo media type/content negotiation.

Không tin dữ liệu chỉ vì nó đã thành Java object: vẫn cần domain validation, giới hạn độ dài, phân quyền. `NumberFormatException` khi parse `id` cần map thành 400 phù hợp; resource không tồn tại sau parse nên map 404.

### 9.2. Các kiểu ServerResponse

```java
ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Note(42, "Hello"));

ServerResponse.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ApiError("NOTE_NOT_FOUND", "Note not found"));

ServerResponse.created(URI.create("/api/v1/notes/42"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new Note(42, "Hello"));

ServerResponse.noContent().build();
```

Các fragments minh họa API response builder. `ServerResponse.created(uri)` tạo 201 + Location; URI root-relative giả định app ở context root. Với app sau proxy/context path, dùng URI generation phù hợp, ví dụ `ServletUriComponentsBuilder` hoặc canonical base.

### 9.3. Không nhầm functional ServerResponse với reactive Mono

Trong Servlet WebMvc.fn:

```java
HandlerFunction<ServerResponse> hello =
        request -> ServerResponse.ok().body("Hello");
```

Còn WebFlux.fn thường dùng `Mono<ServerResponse>` từ Reactor và imports `org.springframework.web.reactive.function.server`. **Không copy reactive `Mono` signatures** vào MVC sample nếu không chủ ý tích hợp reactive adapters.

## 10. Ứng dụng chạy được: Spring Boot 3.5 + Java 17

Demo dùng một `RouterFunction` bean để tránh phụ thuộc ordering giữa nhiều beans. Các endpoints:

| Endpoint | Hành vi |
| --- | --- |
| `GET /health` | Trả `OK`; **không bị notes filter chặn** |
| `GET /api/v1/notes` | Danh sách notes trong memory |
| `GET /api/v1/notes/stats` | Trả số lượng notes; được đặt **trước** `/{id}` |
| `GET /api/v1/notes/{id}` | 200 nếu tìm thấy; 404 nếu chưa có; 400 nếu ID không hợp lệ |
| `POST /api/v1/notes` | Nhận JSON, validate title, tạo record, trả 201 + Location |
| Các notes routes | Yêu cầu `X-Demo-Client: learning` từ filter minh họa; nếu thiếu trả 400 |

**Giới hạn quan trọng:** header demo không có giá trị bảo mật; client tự gửi được. Không có Spring Security/DB/transactions/tracing thật. Đây là bản học thuật về functional routes/filter/error mapping, không phải hệ thống sản xuất.

### 10.1. Dependencies

Dùng Spring Initializr chọn Java 17+, Spring Boot **3.5.x**, Gradle và **Spring Web**. Trong project đã có Boot plugin, repository/dependency management, thêm:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là fragment `build.gradle`; cần build file Gradle hoàn chỉnh do Spring Initializr hoặc project cung cấp. Starter web có Spring MVC Servlet stack và Jackson JSON converter.

### 10.2. Java source đầy đủ

Tạo file **`src/main/java/demo/webfn/WebFnApplication.java`**:

```java
package demo.webfn;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootApplication
public class WebFnApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFnApplication.class, args);
    }
}

record NewNote(String title) {}

record Note(long id, String title) {}

record ApiError(String code, String message) {}

class InvalidNoteException extends RuntimeException {
    InvalidNoteException() {
        super("Invalid note");
    }
}

class NoteNotFoundException extends RuntimeException {
    NoteNotFoundException() {
        super("Note not found");
    }
}

class NotesHandler {
    private final AtomicLong sequence = new AtomicLong(1000);
    private final ConcurrentMap<Long, Note> notes =
            new ConcurrentHashMap<>();

    ServerResponse list(ServerRequest request) {
        List<Note> result = notes.values().stream()
                .sorted(Comparator.comparingLong(Note::id))
                .toList();

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    ServerResponse stats(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("count", notes.size()));
    }

    ServerResponse get(ServerRequest request) {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException ex) {
            throw new InvalidNoteException();
        }

        Note note = notes.get(id);
        if (note == null) {
            throw new NoteNotFoundException();
        }

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }

    ServerResponse create(ServerRequest request) throws Exception {
        NewNote input = request.body(NewNote.class);
        if (input == null || input.title() == null
                || input.title().isBlank()
                || input.title().length() > 100) {
            throw new InvalidNoteException();
        }

        long id = sequence.incrementAndGet();
        Note note = new Note(id, input.title().trim());
        notes.put(id, note);

        // Request URI chứa /api/v1/notes, bỏ query cũ.
        URI location = UriComponentsBuilder
                .fromUri(request.uri())
                .replaceQuery(null)
                .pathSegment("{id}")
                .encode()
                .buildAndExpand(id)
                .toUri();

        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }
}

@Configuration
class FunctionalRoutesConfig {
    private static final Logger log =
            LoggerFactory.getLogger(FunctionalRoutesConfig.class);

    @Bean
    NotesHandler notesHandler() {
        return new NotesHandler();
    }

    private HandlerFilterFunction<ServerResponse, ServerResponse>
    notesDemoFilter() {
        return (request, next) -> {
            String client = request.headers()
                    .firstHeader("X-Demo-Client");

            if (!"learning".equals(client)) {
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiError(
                                "DEMO_HEADER_REQUIRED",
                                "X-Demo-Client must be learning"));
            }

            long started = System.nanoTime();
            try {
                return next.handle(request);
            } finally {
                long elapsed = System.nanoTime() - started;
                log.info("notes_route durationMicros={}",
                        elapsed / 1_000L);
            }
        };
    }

    @Bean
    RouterFunction<ServerResponse> applicationRoutes(
            NotesHandler handler) {

        return RouterFunctions.route()
                // Không chịu notes filter.
                .GET("/health", request -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("OK"))

                // Tất cả routes bên trong dùng shared policies.
                .path("/api/v1", api -> api

                    // Static path trước path variable.
                    .GET("/notes/stats", handler::stats)
                    .GET("/notes/{id}", handler::get)
                    .GET("/notes", handler::list)
                    .POST("/notes", handler::create)

                    .before(request -> {
                        log.info("notes_request method={}",
                                request.method());
                        return request;
                    })

                    .filter(notesDemoFilter())

                    .after((request, response) -> {
                        log.info("notes_response status={}",
                                response.statusCode().value());
                        return response;
                    })

                    .onError(InvalidNoteException.class,
                        (error, request) ->
                            ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ApiError(
                                        "NOTE_INVALID",
                                        "Invalid note input")))

                    .onError(NoteNotFoundException.class,
                        (error, request) ->
                            ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ApiError(
                                        "NOTE_NOT_FOUND",
                                        "Note does not exist"))))
                .build();
    }
}
```

**Cấu trúc Java:** file có một top-level class public (`WebFnApplication`), còn các records/classes còn lại package-private. Java 17 hỗ trợ `record` và `Stream#toList()`. Spring Boot component scan từ `demo.webfn` tìm `FunctionalRoutesConfig`. Bean `applicationRoutes` được Spring MVC `RouterFunctionMapping` phát hiện.

**Lưu ý compiler:** các method references `handler::list`, `handler::stats`, `handler::get`, `handler::create` đều nhận `ServerRequest` và trả `ServerResponse`. `create` khai báo `throws Exception`, phù hợp `HandlerFunction#handle`. `UriComponentsBuilder.fromUri(request.uri())` nhận `URI` và không cần current request holder. `replaceQuery(null)` loại query request cũ; `pathSegment("{id}")` + `encode()` + `buildAndExpand(id)` giữ ID thành URI segment.

**Lưu ý dữ liệu demo:** in-memory map chỉ có trong một JVM; restart mất dữ liệu và nhiều nodes không chia sẻ state. Để đưa vào production cần service/database, transactions, auth/authorization, input validation và concurrency policies theo sản phẩm.

### 10.3. Cách chạy

```bash
./gradlew bootRun
```

**Public health, không cần demo header:**

```bash
curl -i 'http://localhost:8080/health'
```

Kỳ vọng `200 OK` và body:

```text
OK
```

**Notes endpoint thiếu header:**

```bash
curl -i 'http://localhost:8080/api/v1/notes'
```

Kỳ vọng `400 Bad Request` với JSON:

```json
{
  "code": "DEMO_HEADER_REQUIRED",
  "message": "X-Demo-Client must be learning"
}
```

**Danh sách notes khi có header:**

```bash
curl -i 'http://localhost:8080/api/v1/notes' \
  -H 'X-Demo-Client: learning'
```

Ban đầu (trước khi POST) body dự kiến:

```json
[]
```

**Tạo note:**

```bash
curl -i -X POST \
  'http://localhost:8080/api/v1/notes?source=docs' \
  -H 'X-Demo-Client: learning' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Functional MVC"}'
```

Với request tạo đầu tiên, kết quả dự kiến:

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/notes/1001
Content-Type: application/json

{"id":1001,"title":"Functional MVC"}
```

`source=docs` **không** xuất hiện ở Location nhờ `replaceQuery(null)`. ID 1001 chỉ là kết quả của counter ban đầu khi chưa có request tạo khác.

**Đọc note:**

```bash
curl -i \
  'http://localhost:8080/api/v1/notes/1001' \
  -H 'X-Demo-Client: learning'
```

Kỳ vọng 200 và body note tương ứng.

**Truy cập route static `stats`:**

```bash
curl -i \
  'http://localhost:8080/api/v1/notes/stats' \
  -H 'X-Demo-Client: learning'
```

Sau đúng một lần POST, body:

```json
{"count":1}
```

Nếu đặt `/{id}` trước `/stats`, string `"stats"` có thể bị parse như số ID và trả lỗi. Ví dụ thực tế đặt route static trước.

**Nhập ID không phải số:**

```bash
curl -i \
  'http://localhost:8080/api/v1/notes/abc' \
  -H 'X-Demo-Client: learning'
```

Handler ném `InvalidNoteException` và `onError` trả 400:

```json
{"code":"NOTE_INVALID","message":"Invalid note input"}
```

**ID hợp lệ nhưng không tồn tại:**

```bash
curl -i \
  'http://localhost:8080/api/v1/notes/999999' \
  -H 'X-Demo-Client: learning'
```

Dự kiến 404 và:

```json
{"code":"NOTE_NOT_FOUND","message":"Note does not exist"}
```

Trên môi trường khác (đặc biệt sau proxy/context path), Location host/prefix hoặc headers phụ thuộc request thực và deployment. Các ví dụ HTTP minh họa output contract, **không phải log đã chạy Gradle**.

## 11. Kiểm thử MVC functional routes bằng MockMvc

`MockMvc` khởi động MVC test/request infrastructure mà không cần mở cổng mạng. Test này dùng `@SpringBootTest` để bảo đảm Spring Boot tìm được `RouterFunction` bean và `@AutoConfigureMockMvc` để thực hiện request.

Tạo **`src/test/java/demo/webfn/WebFnApplicationTests.java`**:

```java
package demo.webfn;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebFnApplicationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void healthIsNotAffectedByNestedFilter() throws Exception {
        mvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }

    @Test
    void notesFilterShortCircuitsWithoutClientHeader()
            throws Exception {
        mvc.perform(get("/api/v1/notes"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("DEMO_HEADER_REQUIRED"));
    }

    @Test
    void staticStatsRouteWinsBeforeVariableRoute()
            throws Exception {
        mvc.perform(get("/api/v1/notes/stats")
                .header("X-Demo-Client", "learning"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void invalidIdUsesFunctionalOnError400()
            throws Exception {
        mvc.perform(get("/api/v1/notes/not-a-number")
                .header("X-Demo-Client", "learning"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("NOTE_INVALID"));
    }

    @Test
    void missingNoteUsesFunctionalOnError404()
            throws Exception {
        mvc.perform(get("/api/v1/notes/999999999")
                .header("X-Demo-Client", "learning"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                    .value("NOTE_NOT_FOUND"));
    }

    @Test
    void postCreatesResourceAndLocationHasNoOldQuery()
            throws Exception {

        MvcResult response = mvc.perform(
                post("/api/v1/notes?source=docs")
                    .header("X-Demo-Client", "learning")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"Created with WebMvc.fn"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title")
                    .value("Created with WebMvc.fn"))
            .andReturn();

        String location = response.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);

        URI uri = URI.create(location);
        assertTrue(uri.getPath().matches(
                "/api/v1/notes/[0-9]+"));
        assertTrue(uri.getRawQuery() == null);

        mvc.perform(get(uri.getRawPath())
                .header("X-Demo-Client", "learning"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title")
                    .value("Created with WebMvc.fn"));
    }

    @Test
    void blankTitleUsesFunctionalOnError400()
            throws Exception {
        mvc.perform(post("/api/v1/notes")
                .header("X-Demo-Client", "learning")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"   "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("NOTE_INVALID"));
    }
}
```

Chạy:

```bash
./gradlew test
```

**Bảy tests được thiết kế để pass với code và Spring Boot 3.5 baseline nêu trên**. Đây là các kết quả dự kiến từ phân tích mã và Spring API, **chưa phải chứng cứ Gradle đã chạy**. Các tests không assert ID cố định hoặc count chính xác (ngoại trừ kiểu number) để hạn chế phụ thuộc test order/shared Spring context.

Các phép kiểm tra có ý nghĩa:

- `/health` không cần header → nested filter không lan lên top-level route.
- `/notes` không header → `filter` short-circuit trước handler.
- `/notes/stats` trả JSON stats, không đi nhầm `/{id}`.
- ID không phải số → 400 từ `onError(InvalidNoteException.class,...)`.
- ID số không tồn tại → 404 từ `onError(NoteNotFoundException.class,...)`.
- POST tạo note → 201 và Location không chứa query cũ.
- POST title blank → 400 theo business validation mapping.

### 11.1. Test phạm vi filter và error mapping bằng negative cases

Những test bổ sung có giá trị:

- Gọi `/health` kèm header bất kỳ, assert nó không thay đổi behavior.
- Gọi `/api/v1/notes/foo` không demo header: filter short-circuit trả lỗi header, **chưa vào parser ID**, theo hợp đồng ví dụ.
- Gọi endpoint không match như `/does-not-exist`: onError của notes router **không** biến 404 đó thành lỗi NOTE_NOT_FOUND.
- Gửi JSON sai syntax với `Content-Type: application/json`: xác minh error response thực do message converter/MVC infrastructure sinh, không khẳng định tự map NOTE_INVALID.
- Đưa cùng path vào một router khác hoặc add catch-all trước: test route shadowing.
- Nếu có Security starter, test unauthorized access ở Servlet security filter layer, không chỉ header demo.
- Gửi request với context path và proxy headers trong integration test nếu Location phải là external URL.

### 11.2. Khi nào cần test server thật?

`MockMvc` xác minh route, handler, filter và onError của MVC mà không chạy đầy đủ embedded socket/gateway. Khi cần kiểm tra:

- Servlet filter registrations, CORS preflight và security chain.
- Proxy/Host/Forwarded URL generation.
- Real content negotiation/compression/HTTP headers.
- Multipart lớn, streaming, disconnects/timeouts.
- Multiple nodes/load balancing hoặc transaction consistency.

Hãy dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)` + HTTP client thật, và test staging qua gateway khi chính sách phụ thuộc proxy/CDN.

## 12. `HandlerFilterFunction` và thứ tự thực thi filters

Hình dung một filter lồng nhau:

```java
HandlerFilterFunction<ServerResponse, ServerResponse> a =
        (request, next) -> {
            events.add("A-in");
            try {
                return next.handle(request);
            } finally {
                events.add("A-out");
            }
        };

HandlerFilterFunction<ServerResponse, ServerResponse> b =
        (request, next) -> {
            events.add("B-in");
            try {
                return next.handle(request);
            } finally {
                events.add("B-out");
            }
        };
```

Đây là fragment, `events` là danh sách được cấp trong test; nếu được dùng đồng thời, cần thread-safe instrumentation hoặc request-scoped event list.

Một chain cụ thể `a.andThen(b).apply(handler)` có luồng khái niệm:

```text
A-in
  B-in
    handler
  B-out
A-out
```

Trong thực tế, builder/nested router còn có các lớp filter khác, `before`/`after`/`onError` và thứ tự wrap. Khi thứ tự ảnh hưởng quyền truy cập hoặc logging, hãy assert event order trong test. **Không dựa vào nhãn tên filter hoặc thứ tự @Bean registration không được chỉ định**.

### 12.1. Filter chain không bảo đảm truy cập body an toàn nhiều lần

Nếu một handler filter đọc `request.body(NewNote.class)` để validate và handler lại đọc body lần hai, Servlet request body có thể đã bị tiêu thụ. Không viết một filter JSON logging đọc toàn body rồi tin downstream vẫn deserialize bình thường.

Giải pháp:

- Validation domain ở handler/service sau khi parse một lần.
- Nếu filter buộc phải kiểm tra raw payload, dùng buffering wrapper đúng cấu hình/giới hạn hoặc thay kiến trúc.
- Không log raw credentials/PII/Authorization.
- Cẩn thận với multipart large body, stream và memory limits.
- Nếu cần audit event, ghi metadata đã xác minh thay vì toàn request content.

### 12.2. Dùng try/finally cho MDC

Nếu dùng `MDC` cho correlation ID trong filter, luôn cleanup:

```java
return (request, next) -> {
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    try {
        return next.handle(request);
    } finally {
        MDC.remove("requestId");
    }
};
```

Đây là fragment với imports `java.util.UUID`, `org.slf4j.MDC`. Filter chỉ bao quanh **handler invocation**; nếu response body streaming viết sau đó trên thread khác, MDC có thể không còn, và cần context propagation riêng. Nếu muốn correlation ID từ sớm cho Servlet filter/error response, cân nhắc filter tầng Servlet hoặc framework tracing/observability.

## 13. Xử lý lỗi: phân loại theo “không match”, “client error” và “server error”

### 13.1. Không match route ≠ exception trong handler

Ví dụ GET `/api/v1/unknown` có thể không match functional router nào. `onError(NoteNotFoundException.class,...)` **không** được gọi chỉ vì request trở thành 404.

Với một route đã match nhưng note ID không có trong repository, handler ném `NoteNotFoundException`: đây mới là lỗi nghiệp vụ được `onError` ánh xạ thành 404. Hai trường hợp cùng status nhưng nguồn và error body có thể khác.

### 13.2. Bad request vs not found

| Trường hợp | Status nên dùng | Nguồn xử lý |
| --- | --- | --- |
| Thiếu demo header theo contract bài học | 400 | Short-circuit trong `HandlerFilterFunction` |
| ID không parse được theo `/{id}` | 400 | `InvalidNoteException` → `onError` |
| ID hợp lệ nhưng record không tồn tại | 404 | `NoteNotFoundException` → `onError` |
| Route không tồn tại | 404 (thường gặp) | MVC handler mapping / default error handling |
| Body JSON cú pháp sai | 400 (thường gặp) | Message converter + MVC exception infrastructure |
| Unsupported request media type | Có thể 415 tùy routes/config | Predicate/HTTP handling và explicit error contract |
| Method không phù hợp | Có thể 405 hoặc fallback handling tùy mappings | Request mapping/resolvers |
| Chưa xác thực | 401 | Spring Security authentication |
| Đã xác thực nhưng không đủ quyền | 403 | Spring Security authorization |
| Server code/persistence lỗi không lường trước | 500 | Error infrastructure/monitoring |

Không biến mọi exception thành 200 với `{"ok":false}`. HTTP status là một phần quan trọng của public API.

### 13.3. Response đã commit có giới hạn

Nếu một handler/filter tạo streaming response và lỗi phát sinh **sau khi bytes đầu tiên đã được gửi**, `onError` có thể không thể thay headers hoặc status. Đây là vấn đề vòng đời HTTP/Servlet, không riêng functional APIs.

Muốn truyền lỗi trong stream đã mở, phải có framing/error protocol bên trong stream (ví dụ event type "error" trong SSE), hoặc để client xử lý disconnect/retry. Đừng trả stack trace theo body đang mở.

## 14. Functional routes có thể cùng tồn tại với annotated controllers không?

Có. Trong Spring MVC, `RouterFunctionMapping` và annotated `RequestMappingHandlerMapping` đều là các handler mapping dưới `DispatcherServlet`. Một app có thể vừa có:

```java
@RestController
class LegacyController {
    @GetMapping("/legacy/ping")
    String ping() {
        return "pong";
    }
}
```

vừa có:

```java
@Bean
RouterFunction<ServerResponse> functionalRouter() {
    return RouterFunctions.route()
            .GET("/new/ping",
                request -> ServerResponse.ok().body("pong"))
            .build();
}
```

Hai fragment minh họa chạy song song trong một Boot MVC application. Tuy nhiên:

- `HandlerFilterFunction` trên `/new/ping` không tự chạy cho `/legacy/ping`.
- `@ControllerAdvice`, interceptors, HandlerAdapters và message converters có phạm vi/semantics riêng.
- Nếu annotated controller và functional router đăng ký **cùng method/path**, handler mapping order quyết định ai nhận request; dễ gây bất ngờ.
- Tổ chức routes tránh overlap giữa functional và annotated styles, đặc biệt khi migrate từng module.
- Security policy theo paths/methods phải bao phủ cả hai, không chỉ một kiểu controller.

Một chiến lược migration phù hợp là chuyển từng bounded module (ví dụ `/api/v2/catalog/**`), test API contract, giữ annotation controllers còn lại, rồi đánh giá độ phức tạp trước khi chuyển tiếp.

## 15. CORS, Security và Observability

### 15.1. Spring Security và CORS

Tài liệu WebMvc.fn ghi rõ CORS support cho functional endpoints được cung cấp qua **`CorsFilter` chuyên dụng**. Không mặc định rằng functional builder có mọi hỗ trợ CORS annotation giống controller annotation.

Với API có cross-origin clients:

- Cấu hình `CorsFilter` hoặc Spring Security CORS integration phù hợp Servlet filter chain.
- Xác minh preflight `OPTIONS` được xử lý đúng **trước lớp authentication thích hợp**.
- Không dùng `Access-Control-Allow-Origin: *` khi trả credentials/cookies cross-origin.
- CORS không thay authentication/authorization/CSRF; client bên ngoài browser không bị CORS hạn chế.
- Nếu sử dụng cookies với state-changing requests, thiết kế CSRF và SameSite theo threat model.

Header mô phỏng `X-Demo-Client` trong code mẫu **không phải security control**. Client có thể gửi bất kỳ giá trị. Muốn bảo vệ thật, xây `SecurityFilterChain`, xác thực bearer token/session và phân quyền từng tài nguyên.

### 15.2. Metric/logging fields an toàn

Nên thu thập:

- Route template/operation name, HTTP method và status class.
- Latency bucket, count/error rate.
- Correlation ID do server hoặc hạ tầng tin cậy sinh.
- Exception type/error code đã phân loại, không phải raw exception messages.
- Tenant/user pseudonymous ID theo privacy policy nếu cần.

Không ghi raw request bodies, Authorization headers, cookies, password, OTP, access tokens hoặc query parameters nhạy cảm. Nếu log request path có resource IDs/PII, thay bằng template hoặc hash/pseudonymization được thiết kế đúng.

`HandlerFilterFunction` có thể đo thời gian đồng bộ quanh `next.handle`, **không nhất thiết đo hết thời gian truyền body streaming**, timeout hoặc network latency. Khi cần metrics toàn bộ HTTP lifecycle, dùng Servlet filters/observability framework ở đúng tầng.

## 16. Dùng `HandlerFilterFunction` khi nào? Dùng cơ chế khác khi nào?

| Mục tiêu | Cơ chế hợp lý | Vì sao |
| --- | --- | --- |
| Thực hiện kiểm tra trước một nhóm functional routes | `RouterFunctions.Builder.filter` | Chỉ áp route group match, có quyền short-circuit |
| Biến đổi functional request đơn giản | `before` | Code rõ, không cần tự gọi next |
| Ghi status sau handler function | `after` | Nhận `ServerResponse` đã tạo |
| Chuyển domain exception thành HTTP body cho nhóm routes | `onError` | Error mapping cục bộ, explicit |
| Reuse filter trên một router function đã build | `RouterFunction.filter(...)` | Composition sau khi có router |
| CORS preflight và servlet-level concerns | `CorsFilter`/Servlet Filter | Chạy trước functional routing |
| Xác thực/ủy quyền mọi entry points | `SecurityFilterChain` | Bảo vệ thống nhất HTTP layer |
| Intercept annotated controller handler | `HandlerInterceptor` | Biết MVC handler execution context |
| Global exception/ProblemDetail policies | MVC exception infrastructure/`@ControllerAdvice` với test tương thích | Không giới hạn một router riêng |
| Streaming HTTP response | `ServerResponse` streaming API hoặc MVC streaming APIs | Chú ý async/response commit |
| Event/log tracing toàn request kể cả not matched | Servlet filter/observation pipeline | Handler filter chỉ chạy sau route match |

Khi ứng dụng còn rất nhỏ, ba routes và một filter có thể đặt trong một class. Khi domain có nhiều routes, tách router, handler, service và filter theo module để tránh “God Config” khó đọc.

## 17. Những lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh |
| --- | --- | --- |
| Filter không chạy trên controller annotation | Filter chỉ gắn một functional router | Phân biệt `HandlerFilterFunction` và Servlet Filter |
| Filter notes vô tình chạy ở /health | Đặt `.filter(...)` trên outer builder | Chuyển filter vào nested builder, thêm test |
| GET /notes/stats đi vào get ID | Route `/{id}` trước static route | Đổi thứ tự, test output stats |
| Route không match nhưng onError không chạy | Chưa có selected handler/exception | Phân biệt 404 mapping vs exception |
| JSON sai format trả error body khác mong đợi | Message converter/MVC resolver xử lý | Test invalid JSON, config global error |
| Return 200 nhưng body vẫn báo lỗi | Exception bị bắt và trả body success vô ý | Sử dụng HTTP status đúng |
| Exception bị leak raw message trong JSON | Error handler trả `error.toString()` | Stable safe error code/message |
| Header gate xem như authentication | Header do client tự gửi | Dùng Spring Security |
| Code đã đặt filter nhưng hầu như không log | Không có route match hoặc filter scope sai | Log selected route, kiểm tra mapping |
| Hành vi khác khi chia nhiều @Bean routers | Bean order/route overlaps | Giảm overlap, explicit order và tests |
| Route group version v1/v2 chọn nhầm | Path/predicate overlap và first match | Gộp theo group, test mỗi version |
| `ServerRequest.body(...)` đọc xong không đọc lại được | Body stream đã bị tiêu thụ | Deserialize một lần, không double-read |
| before gắn header nhưng downstream thấy giá trị lạ | Header addition/duplicate từ client | Không dùng request header làm trusted identity |
| after không chạy với exception | Không có response thành công trước throw | try/finally hoặc onError đúng |
| Filter try/finally metric ngắn hơn full download | `ServerResponse` body có thể write sau | Đo Servlet lifecycle end-to-end |
| Boot không phát hiện route | Thiếu `@Bean`/component scan hoặc sai WebFlux imports | Kiểm tra router bean, WebMvc.fn package |
| Controller annotation có mapping trùng router | HandlerMapping ordering | Tránh overlapping, test |
| CORS preflight bị 401/403 | Chưa có proper CorsFilter/Security integration | Kiểm tra Servlet filter ordering |
| Test MockMvc không như server thật | Filter/proxy/container behaviors chưa được mô phỏng | RANDOM_PORT + gateway tests |

### Quy trình debug

1. Xác nhận app dùng Spring MVC (`spring-webmvc`), không nhầm WebFlux.
2. Xác nhận `RouterFunction<ServerResponse>` tồn tại là Spring bean.
3. Kiểm tra HTTP method, path, query, Accept, Content-Type.
4. Kiểm tra thứ tự tuyến khai báo và route shadowing.
5. Kiểm tra nested/outer scopes của before/after/filter/onError.
6. Nếu route match, xem filter có gọi `next.handle(request)` không.
7. Nếu handler ném exception, xác minh onError predicate/class match.
8. Nếu vẫn lỗi, xem HandlerExceptionResolver/Boot error handling.
9. Kiểm tra response status, Location, JSON body theo API contract.
10. Nếu lỗi chỉ xảy ra ở production, kiểm tra Servlet Filter/Security/proxy/tracing trước MVC.

## 18. So sánh version Spring

| Môi trường | Điểm cần chú ý |
| --- | --- |
| Spring Framework 5.2+ / Boot 2.x | WebMvc.fn có từ Spring 5.2; nhiều Servlet APIs dùng `javax.servlet.*` |
| Spring Framework 6.2 / Boot 3.5 | Java 17+, Jakarta Servlet `jakarta.servlet.*`, WebMvc.fn builder và error handler theo ví dụ |
| Spring Framework 7.x / Boot 4.x | Concepts functional routes/filters tiếp tục phát triển; starter và test infrastructure có thể đổi; đọc Javadoc đúng version |

Không copy signatures `Mono<ServerResponse>` và import reactive package từ WebFlux.fn vào code WebMvc.fn. Một vài phương thức mới như API-version-aware route predicates có thể chỉ xuất hiện ở những phiên bản Framework mới hơn; không lén dùng method của Spring 7 khi code ví dụ nhắm Spring 6.2.

Các nguồn chi tiết của WebMvc.fn có thể cùng xuất hiện trong Spring Framework docs và Javadoc của `org.springframework.web.servlet.function`. Khi nâng Boot major, kiểm tra:

- RouterFunctions.Builder method overloads.
- ServerRequest/ServerResponse serialization, message converters.
- Dependency `spring-boot-starter-web`/MVC starter của version.
- Spring Security CORS integration.
- MockMvc test imports/autoconfig.
- RouterFunctionMapping bean ordering và path matching.
- Error formatting/ProblemDetail policy.

## 19. Checklist triển khai thực tế

- [ ] RouterFunction bean chỉ chứa các routes đúng module/version.
- [ ] Static paths trước variable/wildcard paths khi overlap.
- [ ] Method/media type predicates hợp đồng rõ ràng.
- [ ] Nested path filters không vô tình áp ra top-level routes.
- [ ] Shared filter luôn gọi next khi request hợp lệ.
- [ ] Short-circuit status/body đúng và không dùng user-controlled demo header làm auth.
- [ ] before/after phù hợp lifecycle; try/finally cho cleanup.
- [ ] onError match domain exceptions có chủ đích, không bắt mọi throwable thành 400.
- [ ] Thông điệp lỗi public không lộ exception internals/secrets.
- [ ] Missing route và missing resource có error contracts/test phân biệt.
- [ ] ServerRequest body chỉ parse cần thiết; giới hạn input và validate.
- [ ] Security/CORS/CSRF áp ở đúng Servlet/Security layer.
- [ ] Multiple router beans được compose/order có chủ đích, không overlap.
- [ ] Tests bao gồm route order, nested filter, onError 400/404, POST Location.
- [ ] Có HTTP integration tests cho môi trường proxy/security nếu quan trọng.
- [ ] Functional routes và annotated controllers không đăng ký trùng mapping.
- [ ] Metrics/logging dùng route template, không log secrets.
- [ ] Tài liệu API mô tả chính xác success/error schema và status.

## 20. Bài tập thực hành

1. Trong demo, đưa `GET("/notes/{id}")` lên trước `GET("/notes/stats")`. Điều gì xảy ra với `GET /api/v1/notes/stats` và vì sao?
2. Di chuyển `.filter(notesDemoFilter())` từ nested builder lên outer builder. `GET /health` còn trả 200 khi thiếu header hay không?
3. Viết `HandlerFilterFunction` đo latency bằng `try/finally`. Vì sao số này có thể chưa bằng HTTP response end-to-end với stream body?
4. Chuyển quy tắc header demo từ `filter` sang `before`. Vì sao `before` không phù hợp khi muốn short-circuit theo cách tường minh như trên?
5. Dùng `ServerRequest.from(request)` để tạo request wrapper có thêm demo header. Tại sao không dùng giá trị này như authenticated principal?
6. Tạo record `ApiError` theo contract gồm `code` và `message`; thêm onError cho một exception nghiệp vụ mới.
7. Một request không match route nào nhận 404. Vì sao `onError(NoteNotFoundException.class,...)` không chắc được gọi?
8. Một POST có malformed JSON khiến framework báo 400. Hãy xác minh exception resolver nào xử lý; có nên dùng catch-all onError không?
9. Thêm GET `/api/v2/notes` với schema khác, tách filter và error contract theo version rồi viết tests.
10. Đặt cùng URL vào một annotated controller và một RouterFunction bean. Quan sát handler mapping order; vì sao không nên làm trong sản phẩm?
11. Tách demo một file thành `NotesRoutes`, `NotesHandler`, `NotesService` và `ApiError` mà vẫn giữ tests pass.
12. Viết một filter trả 429 khi rate limit đạt ngưỡng **với counters server-side**, không dựa vào header do client tự gửi. Vì sao nên dùng gateway/Security rate limiter cho nhiều entry points?
13. Với Spring Security bật, giải thích vì sao `HandlerFilterFunction` không thay thế `SecurityFilterChain`.
14. Tại sao một `@ControllerAdvice` của annotated controllers không nên được giả định bao phủ mọi lỗi WebMvc.fn mà chưa test?
15. Gắn `.after(...)` log status và cho handler ném exception. Có bao giờ after không chạy? Khi nào nên đặt metric ở Servlet observation layer?
16. Dùng `RouterFunctions.route().add(ordersRouter).add(notesRouter)`, phân tích ảnh hưởng nếu hai routers overlap.
17. Thêm một request predicate `accept(MediaType.APPLICATION_JSON)`. Phân biệt với `contentType(APPLICATION_JSON)`.
18. Thiết kế test MockMvc xác minh filter short-circuit trước khi handler tạo record trong database.
19. Hãy xây error response nhất quán cho unknown route và unknown resource với `ProblemDetail`/MVC error infrastructure, nhưng không bắt nhầm 401/403.
20. Nếu app phải chạy trên Spring Boot 4, các API starter/test và framework version nào cần kiểm tra trước khi copy ví dụ?

## 21. Tài liệu chính thức

- [Spring Framework 6.2 — WebMvc.fn Functional Endpoints](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc-functional.html): RouterFunction, HandlerFunction, nested routes, filters, route matching order, server execution.
- [Spring Framework 6.2 — RouterFunctions.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.Builder.html): GET/POST/path/nest/add/filter/before/after/onError overloads.
- [Spring Framework 6.2 — HandlerFilterFunction Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/function/HandlerFilterFunction.html): filter(request,next), composition, ofErrorHandler, request/response adapters.
- [Spring Framework 6.2 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/function/ServerRequest.html): body, pathVariable, params, URI and headers.
- [Spring Framework 6.2 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/function/ServerResponse.html): ok/created/status/build/body.
- [Spring Framework 6.2 — RouterFunction Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/function/RouterFunction.html): route, and, andRoute, filter.
- [Spring Framework 6.2 — MVC Exception Handling](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/exceptionhandlers.html): HandlerExceptionResolver pipeline.
- [Spring Framework 6.2 — Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html): RFC 9457 ProblemDetail and MVC error architecture.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): MVC auto-configuration, RouterFunction integration.
- [Spring Framework — MockMvc](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html): Servlet MVC handler testing.
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html): SecurityFilterChain; not replaced by WebMvc.fn filters.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-functional-endpoints-006">Kết hợp functional endpoint với hạ tầng Spring MVC hiện có</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp functional endpoint với hạ tầng Spring MVC hiện có

## 1. Bối cảnh và mục tiêu

Một ứng dụng Spring MVC đang vận hành thường đã có nhiều thành phần: `@RestController`, `@ControllerAdvice`, `HttpMessageConverter`, `WebMvcConfigurer`, Servlet filters, HandlerInterceptors, Spring Security, Jackson, Bean Validation, CORS, static resources, templates, tracing và các bài kiểm thử MockMvc. Khi muốn thêm một module viết theo phong cách functional, câu hỏi không chỉ là "làm sao khai báo RouterFunction?" mà còn là:

- Functional endpoint có chạy chung `DispatcherServlet` với controller annotation không?
- Service, repository, transaction và dependency injection đang có có thể tái sử dụng không?
- JSON converter, locale, validation, CORS, interceptor, security, logging sẽ vận hành ở tầng nào?
- `@ControllerAdvice`, `@Valid`, `@InitBinder`, `@ResponseBody` có tự được áp dụng giống nhau cho handler functional không?
- Có thể khai báo hai nhóm endpoint cạnh nhau mà không xung đột route/HTTP status không?
- Làm sao migrate theo từng bước và kiểm thử không phá hợp đồng API cũ?

**WebMvc.fn** là mô hình functional HTTP endpoints của **Spring MVC Servlet stack**, package `org.springframework.web.servlet.function`. Nó có `RouterFunction`, `HandlerFunction`, `ServerRequest` và `ServerResponse`, nhưng vẫn dùng cùng hạ tầng `DispatcherServlet`. Nó **không phải WebFlux.fn** (package `org.springframework.web.reactive.function.server`) và không tự khiến HTTP/database I/O trở thành non-blocking.

Bài này dùng **Java 17, Spring Boot 3.3.5, Spring Framework 6.1.x**, có ví dụ Maven chạy được với functional + annotated endpoints cùng ứng dụng, dùng chung service và cấu hình MVC. Sau đó phân tích kỹ các ranh giới của converters, validation, errors, filters, CORS, Spring Security, versioning và migration.

### Bạn sẽ làm được gì?

1. Đưa `RouterFunction<ServerResponse>` vào ứng dụng MVC hiện hữu, không tạo `DispatcherServlet` thứ hai.
2. Chạy song song functional routes với `@RestController` trên các URL khác nhau.
3. Dùng chung Spring-managed `@Service`, DTO, validator, Jackson converters và transaction boundaries.
4. Áp dụng MVC-level `HandlerInterceptor`/CORS chung; phân biệt chúng với functional `filter`.
5. Hiểu cơ chế chọn handler, thứ tự routes và rủi ro khi mapping chồng lấn.
6. Thiết kế error response đồng nhất khi argument resolution và validation của hai phong cách khác nhau.
7. Bảo vệ Spring Security, CSRF, authorization, logging và metric không phụ thuộc route style.
8. Viết MockMvc tests cho cả hai loại endpoint và kiểm chứng thay đổi thật.
9. Lập kế hoạch migrate tăng dần, không rewrite toàn bộ chỉ để chuyển cú pháp.

## 2. Một DispatcherServlet, nhiều kiểu handler

Spring MVC được tổ chức quanh `DispatcherServlet`. Nó nhận HTTP request từ Servlet container và hỏi lần lượt các `HandlerMapping` được đăng ký để chọn handler phù hợp; sau đó một `HandlerAdapter` có khả năng sẽ gọi handler đó.

Sơ đồ rút gọn:

```text
HTTP client
     |
     v
Servlet container (Tomcat/Jetty/Undertow)
     |
     v
Servlet Filter chain (security, tracing, request policy)
     |
     v
DispatcherServlet
     |
     +---- RequestMappingHandlerMapping
     |        |
     |        v
     |    @RestController / @GetMapping / @PostMapping
     |        |
     |        v
     |    RequestMappingHandlerAdapter
     |    @RequestBody, @Valid, ResponseEntity...
     |
     +---- RouterFunctionMapping
     |        |
     |        v
     |    RouterFunction + RequestPredicate
     |        |
     |        v
     |    HandlerFunctionAdapter
     |    ServerRequest -> ServerResponse
     |
     +---- Static resource / other HandlerMappings
              |
              v
     HTTP status, headers, body -> Servlet response
```

Với WebMvc.fn, Spring MVC sử dụng các thành phần như `RouterFunctionMapping` và `HandlerFunctionAdapter`. RouterFunctionMapping tìm các `RouterFunction<?>` beans trong application context, sắp xếp và kết hợp để định tuyến. WebMvc.fn và annotated controllers có thể cùng tồn tại trong một ứng dụng Boot Web.

**Điều giống nhau không có nghĩa mọi cơ chế method argument handling đều giống nhau.** Cả hai dùng chung nền tảng MVC và message converters, nhưng functional handler nhận một `ServerRequest` và tự đọc body/params; annotated controller được `RequestMappingHandlerAdapter` resolve các tham số Java dựa vào `@RequestParam`, `@PathVariable`, `@RequestBody`, `@Valid`, v.v.

### Không cần webflux-starter

Ứng dụng chỉ có:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

đã có Spring Web MVC và functional Servlet API cần thiết. **Không import nhầm** các class cùng tên dưới `web.reactive.function.server`. Thêm `spring-boot-starter-webflux` chỉ để viết WebMvc.fn vừa không cần thiết vừa có thể tạo confusion về môi trường/test và reactive clients.

### Không bật @EnableWebMvc tùy tiện trong Spring Boot

Boot auto-configures MVC cùng Jackson, static resources và converter defaults. Việc thêm `@EnableWebMvc`/mở rộng `WebMvcConfigurationSupport` thủ công có thể vô hiệu hóa hoặc thay thế một phần auto-configuration. Để bổ sung interceptor, CORS, formatters hoặc resource handlers, thường bắt đầu bằng `@Configuration implements WebMvcConfigurer` thay vì dựng một MVC stack thứ hai.

## 3. Các thành phần tái sử dụng được và ranh giới áp dụng

| Thành phần hiện có | Functional endpoint dùng lại được? | Điều cần kiểm chứng |
| --- | --- | --- |
| `DispatcherServlet` và Servlet runtime | **Có** | Chỉ một MVC application/context như bình thường |
| Spring `@Service` / `@Repository` / DI | **Có** | Handler là Spring bean, inject qua constructor |
| `@Transactional` ở service | **Có** | Gọi qua Spring proxy; transaction nằm ở service boundary |
| Jackson / `HttpMessageConverter` | **Có** | `request.body(Dto.class)` và `ServerResponse.body(dto)` dùng MVC converters |
| Servlet `Filter` / Spring Security chain | **Có** | Đúng URL/dispatcher types và thứ tự filter chains |
| MVC `HandlerInterceptor` | Có thể áp dụng qua MVC HandlerMapping config | Test filter/interceptor mappings và request lifecycle |
| `WebMvcConfigurer.addCorsMappings` | Có cấu hình CORS theo HandlerMapping | Test preflight và Origin với functional URL |
| `@Valid @RequestBody` method argument magic | **Không tự áp dụng vào HandlerFunction như controller method** | Functional cần validator/binding explicit |
| `@InitBinder` / `@ModelAttribute` method arguments | **Không tự áp dụng cùng cách** | Dùng `ServerRequest` API hoặc custom binding |
| `@ControllerAdvice` / `@ExceptionHandler` | Không bảo đảm xử lý giống mọi lỗi functional | Test từng lỗi, dùng error boundary thích hợp |
| `ResponseBodyAdvice` cho controller method | Không giả định nó biến đổi mọi ServerResponse functional | Test thực tế converter/adapter path |
| Router-level `filter` | Chỉ cho router/nhóm functional tương ứng | Không tự bọc các annotated controllers |
| `@CrossOrigin` trên annotated method | Chỉ metadata của annotated handler | Dùng CORS global/filter phù hợp cho functional routes |
| `@PreAuthorize` trên service method | Có khi Spring method security được bật và gọi qua proxy | Không dựa vào annotation controller method thay full filter-chain auth |
| OpenAPI generator/Actuator/tracing | Phụ thuộc từng integration/version | Verify tài liệu, operation IDs, metrics tags, links |

**Quy tắc kiến trúc:** tái sử dụng các thành phần **nằm dưới routing** (service, repository, transaction) và các thành phần **nằm trước DispatcherServlet** (Servlet Filter/security). Với tính năng dựa trực tiếp vào `HandlerMethod` hay annotation metadata, phải kiểm thử hoặc chuyển sang cơ chế explicit cho functional handlers.

## 4. Dùng chung service và DTO, chỉ thay HTTP adapter

Một thiết kế ổn định:

```text
                      Shared Spring Context
  +------------------------------------------------+
  | DTOs + Bean Validation                         |
  | NoteService (@Service, @Transactional nếu cần) |
  | NoteRepository                                 |
  +------------------------------------------------+
             ^                            ^
             |                            |
  +---------------------+    +------------------------+
  | Annotated adapter   |    | Functional adapter     |
  | @RestController     |    | RouterFunction @Bean   |
  | @RequestBody @Valid |    | HandlerFunction        |
  | ResponseEntity      |    | ServerRequest/Response |
  +---------------------+    +------------------------+
             |                            |
             +---------- HTTP ------------+
```

Ví dụ cả hai REST styles cần "tạo note":

- Annotated controller có `@PostMapping` và tham số `@Valid @RequestBody NewNote input`.
- Functional handler gọi `NewNote input = request.body(NewNote.class)`, rồi tự gọi `validator.validate(input)`.
- Cả hai gọi `NoteStore.create(input.title())` (hoặc một `NoteService` ở hệ thống thật).
- Cả hai trả HTTP `201 Created`, header `Location` và JSON DTO `Note`.

**Đừng copy business rules vào hai handler**. Nếu đã có `@Service` chứa authorization, transaction, idempotency và invariant, để nó là nguồn logic duy nhất. Functional/annotated chỉ nên xử lý những khác biệt ở biên HTTP.

## 5. Route discovery, thứ tự và tránh xung đột

### 5.1. WebMvc.fn dùng thứ tự match

Ví dụ:

```java
RouterFunction<ServerResponse> router = route()
        .GET("/api/fn/notes/latest", handler::latest)
        .GET("/api/fn/notes/search", handler::search)
        .GET("/api/fn/notes/{id}", handler::find)
        .build();
```

Đây là **snippet** (cần bean handler/imports). Routes literal `latest` và `search` được đặt **trước** `/{id}`. Nếu `/{id}` match đầu, nó có thể nhận `id="latest"` và handler parse sai số. Với annotated controllers, Spring dùng ranking theo độ cụ thể của `@RequestMapping`; functional route traversal thường phụ thuộc **order**.

### 5.2. Hai phong cách nên có namespace rõ ràng

Trong lúc thử nghiệm, có thể đặt:

- `/api/annotated/notes/**` cho API annotations hiện hành.
- `/api/fn/notes/**` cho phiên bản functional được phát triển song song.

Trong migration thật, không nhất thiết giữ cả hai public prefixes; có thể chuyển **từng route** tại boundary được kiểm soát. Nhưng tránh khai báo **chính xác cùng HTTP method + path** đồng thời trên RouterFunction và @RestController rồi mong framework "chọn đúng" theo thứ tự Java source. HandlerMapping precedence phụ thuộc cấu hình/framework, không phải một hợp đồng để dựa vào nhằm rollout.

Nếu cần so sánh hai implementations trước khi thay endpoint, có thể:

1. Dùng URL riêng/staging để chạy contract tests.
2. Đặt traffic splitting ở gateway theo quy tắc được kiểm soát và quan sát.
3. Chuyển từng route khi functional handler đạt contract và security checks.
4. Gỡ mapping cũ sau khi migration hoàn tất.

### 5.3. Nhiều RouterFunction beans

`RouterFunctionMapping` có thể phát hiện nhiều RouterFunction beans và kết hợp chúng theo thứ tự. Với modules:

```java
@Bean
RouterFunction<ServerResponse> catalogRoutes(CatalogHandler handler) {
    return route()
            .GET("/api/catalog", handler::list)
            .build();
}

@Bean
RouterFunction<ServerResponse> billingRoutes(BillingHandler handler) {
    return route()
            .GET("/api/billing", handler::summary)
            .build();
}
```

Đây là **snippets** giả định các handlers tương ứng. Nếu các prefixes hoàn toàn khác nhau, thứ tự ít nguy hiểm hơn. Nếu route patterns overlap (catch-all, path variables), phải dùng thứ tự bean/order có chủ đích và tests; đừng dựa vào alphabet/tên class.

## 6. @Bean RouterFunction nằm trong @Configuration

Kiểu tối giản:

```java
@Configuration
class FunctionalNotesConfiguration {

    @Bean
    RouterFunction<ServerResponse> notesRoutes(NoteHandler handler) {
        return route()
                .GET("/api/fn/notes", handler::list)
                .build();
    }
}
```

Đây là snippet; `NoteHandler`/static `route()` được định nghĩa ở ví dụ đầy đủ mục 11. Spring Boot scan `@Configuration`, tạo `RouterFunction` bean khi khởi động; MVC phát hiện và phục vụ HTTP request. Không cần `@RestController` trên `NoteHandler` và không cần tạo DispatcherServlet/Servlet registration khác.

**Quan trọng:** RouterFunctions builder thường được tạo lúc startup; không tạo builder mutable và gắn thêm routes trên mỗi request. Handler đã inject từ Spring bean thường là singleton và được dùng lại, vì vậy đừng giữ `ServerRequest` hoặc dữ liệu user vào mutable instance fields của handler.

## 7. Chung MVC HttpMessageConverters, khác cách gọi

Một cấu hình Spring Boot bình thường có Jackson converter đọc/ghi JSON.

Annotated:

```java
@PostMapping(
        path = "/api/annotated/notes",
        consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Note> create(
        @Valid @RequestBody NewNote body) {
    // ...
}
```

Functional:

```java
public ServerResponse create(ServerRequest request) throws Exception {
    NewNote body = request.body(NewNote.class);
    // validate body rồi gọi service
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(result);
}
```

Cả hai có thể dùng cùng Jackson configuration, nhưng:

- Annotated method `@RequestBody` được một MVC argument resolver gọi trước controller.
- Functional handler **chủ động** gọi `request.body(NewNote.class)`. Với malformed JSON, lỗi có thể phát sinh **bên trong handler** và đi qua error handling khác.
- Annotated `@ResponseBody`/`ResponseEntity` được handler adapter xử lý; functional `ServerResponse` ghi HTTP body theo cơ chế `ServerResponse.writeTo` và converters.
- Cùng converter không đồng nghĩa mọi `RequestBodyAdvice`/`ResponseBodyAdvice` vốn gắn vào annotated method pipeline đều tự chạy cho functional ServerResponse.
- Cả hai cần content-type/Accept negotiation chính xác; functional route có thể dùng `contentType(MediaType.APPLICATION_JSON)` predicate.

**Cẩn thận với custom converters:** Nếu team dùng `WebMvcConfigurer.configureMessageConverters(...)` sai cách, có thể thay toàn bộ default converters thay vì chỉ mở rộng, khiến JSON route bị lỗi. Thường dùng `extendMessageConverters` hoặc Boot Jackson customizers khi chỉ cần điều chỉnh nhỏ. Sau khi thay converter, test **cả hai** request/response styles.

### Generic types

Với functional handlers, nếu cần deserialize JSON array của DTOs:

```java
List<NewNote> notes = request.body(
    new ParameterizedTypeReference<List<NewNote>>() {});
```

Đây là snippet với import `org.springframework.core.ParameterizedTypeReference` và `java.util.List`. Generic type token cho converter biết element type, thay vì `List.class` (có thể sinh Maps thay DTO). Bảo đảm converter hỗ trợ media type và body content.

## 8. Validation: tái sử dụng Validator, không kỳ vọng @Valid tự chạy

DTO record:

```java
public record NewNote(
        @NotBlank
        @Size(max = 80)
        String title) {}
```

Annotated method:

```java
public ResponseEntity<Note> create(
        @Valid @RequestBody NewNote input) {
    // Spring MVC kích hoạt Bean Validation theo annotated pipeline
}
```

Functional handler:

```java
NewNote input = request.body(NewNote.class);
Set<ConstraintViolation<NewNote>> violations =
        validator.validate(input);

if (!violations.isEmpty()) {
    return ServerResponse.badRequest()
            .body(new ApiError("invalid_note"));
}
```

**Sự khác biệt nằm ở bước kích hoạt validation**. Cùng Jakarta Validator Bean có thể được inject qua constructor, nhưng `HandlerFunction.handle(ServerRequest)` không có `@Valid @RequestBody` method argument để Spring tự xử lý giống annotated handler. Nếu dùng functional, hãy viết một helper nhất quán để:

1. Đọc JSON body và nhận biết malformed body.
2. Kiểm tra Bean Validation constraints.
3. Chuyển các violations thành error code/schema ổn định.
4. Không lộ exception stack trace hoặc giá trị nhạy cảm.
5. Phân biệt 400 (bad request) với 403 (not authorized), 404 (not found), 409 (conflict), 500 (server fault).

Với form binding, multipart, request params hoặc constructor binding phức tạp, annotated controller thường có auto binding features phong phú hơn; functional handler phải được thiết kế tường minh khi cần những API đó.

## 9. Error handling: nhất quán contract nhưng không đồng nhất pipeline

Một annotated endpoint có thể ném `MethodArgumentNotValidException` khi `@Valid` fail. Functional endpoint có thể tự trả `ServerResponse.badRequest().body(new ApiError(...))` hoặc bắt conversion exceptions đúng loại. Cả hai có thể trả HTTP 400 nhưng **body format khác nhau**.

Các điểm cần kiểm tra:

| Lỗi | Annotated MVC thông thường | Functional MVC cần chủ động kiểm thử |
| --- | --- | --- |
| JSON không hợp lệ | Resolver/converter có thể ném `HttpMessageNotReadableException` | `request.body(...)` có thể ném trong HandlerFunction |
| DTO vi phạm @NotBlank | `@Valid` method argument pipeline | Explicit Validator hoặc custom binding |
| `id=abc` khi cần long | MVC type conversion exception | `Long.parseLong`/conversion do handler gọi |
| Thiếu query param bắt buộc | `@RequestParam(required=true)` | `request.param(...)` Optional rồi validate |
| Unsupported media type | `consumes` mapping/converter | `contentType` predicate và converter; status có thể khác |
| Business conflict | Service exception/response | Cùng service; adapter mapping cần tương thích |
| Security rejection | Spring Security filter chain | Vẫn Spring Security filter chain, không bypass |
| Lỗi serialization | MVC return-value handling | `ServerResponse` body writing; test behavior |

### @ControllerAdvice có tự xử lý mọi functional error không?

**Không giả định.** Một số exception resolvers có thể xử lý exceptions trong cả hai flows, nhưng `@ExceptionHandler` thường gắn với khái niệm `HandlerMethod` của annotated controllers. Functional handlers có thể không đi đúng đường annotation-based error resolution. Hãy:

- Dùng **router-level `onError`/filter** theo API phù hợp phiên bản khi muốn error policy cho functional routes.
- Dùng explicit error-to-`ServerResponse` helpers cho conversion/validation do handler quản lý.
- Xác nhận global `HandlerExceptionResolver` và `ProblemDetail` response qua MockMvc.
- Không bắt `Exception` rồi luôn trả 400, vì sẽ che 500 thật.
- Nếu backend đã có chuẩn RFC 9457 Problem Details, test trường `type`, `title`, `status`, `detail` cho cả hai styles, thay vì chỉ kiểm tra HTTP status.

### Functional error boundary là một lớp khác controller advice

Một middleware/router filter có thể chọn short-circuit hoặc chặn exceptions:

```java
.filter((request, next) -> {
    try {
        return next.handle(request);
    } catch (IllegalArgumentException ex) {
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("bad_parameter"));
    }
})
```

Snippet này chỉ minh họa; chỉ bắt `IllegalArgumentException` **nếu** domain policy coi loại exception đó là bad input. Không nên dùng kiểu exception quá rộng cho nhiều lỗi khác bản chất. Lưu ý lỗi body serialization có thể xảy ra **sau** khi handler tạo `ServerResponse` nên filter bọc `next.handle()` không nhất thiết bắt được lỗi xảy ra khi body được ghi.

## 10. MVC Interceptor, Servlet Filter, functional filter, Security

Có ít nhất ba loại interception mà tên gọi dễ gây nhầm.

| Tầng | Ví dụ API | Phạm vi |
| --- | --- | --- |
| Servlet filter | `jakarta.servlet.Filter`, Spring `OncePerRequestFilter` | Trước DispatcherServlet, phụ thuộc URL/dispatcher mappings |
| MVC interceptor | `HandlerInterceptor` + `WebMvcConfigurer.addInterceptors` | MVC HandlerMappings được cấu hình để dùng interceptor |
| Functional filter | `RouterFunctions.Builder.filter` | Chỉ routes được RouterFunction builder/nhóm tương ứng tạo |
| Spring Security | `SecurityFilterChain` ở Servlet filters | Bảo vệ các HTTP requests theo security matcher rules |

### 10.1. Dùng chung MVC interceptor

Một ứng dụng đã có MVC correlation header, locale hoặc metrics interceptor có thể tiếp tục đăng ký:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new HandlerInterceptor() {
        @Override
        public boolean preHandle(
                HttpServletRequest request,
                HttpServletResponse response,
                Object handler) {
            response.setHeader("X-MVC-Pipeline", "active");
            return true;
        }
    }).addPathPatterns("/api/**");
}
```

Đây là snippet để minh họa; class runnable ở mục 11. Tác dụng thực tế cần kiểm tra với các HandlerMapping được dùng trong phiên bản Spring/Boot đang chạy, đặc biệt nếu application tự đăng ký custom HandlerMappings. Spring MVC Java configuration thường cung cấp interceptors cho các mappings do nó quản lý.

**Lưu ý quan trọng:** Interceptors không phải nơi tối ưu để triển khai authorization vì có thể có khác biệt/mismatch giữa mappings; Spring docs khuyên dùng Spring Security hoặc Servlet filter chain cho bảo mật. Correlation/observability thích hợp hơn nhưng vẫn cần xử lý ASYNC redispatch khi có async endpoints.

### 10.2. Functional filter chỉ bọc functional routes

Ví dụ:

```java
RouterFunction<ServerResponse> router = route()
        .GET("/api/fn/notes", handler::list)
        .filter((request, next) -> {
            if ("true".equals(
                    request.headers().firstHeader("X-Demo-Block"))) {
                return ServerResponse.status(HttpStatus.FORBIDDEN)
                        .body(new ApiError("demo_blocked"));
            }
            return next.handle(request);
        })
        .build();
```

Snippet mang tính giáo dục, **không dùng header `X-Demo-Block` làm hệ thống authorization**. Khi request đi tới annotated URL, functional filter này không tự động chạy vì request không được RouterFunction đó handle. Ngược lại, Servlet Security filter chain áp dụng theo SecurityFilterChain matcher của toàn app.

### 10.3. Security FilterChain cho cả hai phong cách

Nếu ứng dụng có Spring Security:

- Request `/api/fn/**` và `/api/annotated/**` cần cùng hoặc tương đương security matcher policies.
- Filter chain xử lý authentication, authorization, CSRF, CORS integration và exception entry points **trước DispatcherServlet**.
- `@PreAuthorize` trên shared service methods có thể bảo vệ business layer nếu đã bật method security và invocation đi qua proxy; không chỉ dựa vào route matchers.
- Nếu dùng session/cookie auth, POST functional vẫn chịu CSRF protection khi policy bật. Không tắt CSRF vì functional route không dùng `@PostMapping`.
- Nếu phân quyền theo tenant/resource, phải kiểm tra quyền tại service sau khi biết resource ID, không chỉ kiểm tra URL prefix.
- Các functional filters để audit/transform không thay filter chain security chuẩn.

**Mô hình bảo mật:** Handler thích hợp có thể thay đổi theo routing style, nhưng danh tính user và quyền truy cập phải được kiểm tra nhất quán ở request và service boundaries.

## 11. Dự án Maven runnable: functional + annotated + shared MVC infrastructure

Demo sau chạy **một Spring Boot MVC application** với:

1. `POST /api/fn/notes`, `GET /api/fn/notes`, `GET /api/fn/notes/{id}`, `GET /api/fn/notes/latest` và `GET /api/fn/notes/search?q=...`.
2. Annotated equivalents tại `/api/annotated/notes`.
3. Một `NoteStore` singleton lưu dữ liệu trong RAM dùng chung, không tạo hai databases.
4. Cùng DTO và Jakarta Bean Validation.
5. Một MVC `HandlerInterceptor` ghi header `X-MVC-Pipeline: active` cho API routes.
6. Một `WebMvcConfigurer.addCorsMappings` minh họa global CORS.
7. Một functional filter giả lập `X-Demo-Block: true` để quan sát phạm vi route-specific filter.
8. MockMvc tests xác nhận HTTP status, Location, JSON, validations, shared store, routing và MVC interceptor.

**Giới hạn demo:** Không có Spring Security dependency, DB hay production authorization; `X-Demo-Block` chỉ là minh họa. CORS origin `https://portal.example.test` chỉ là tên minh họa; không phải một dịch vụ đang hoạt động.

### 11.1. Cấu trúc tệp

```text
mvc-mixed-endpoints/
├── pom.xml
└── src/
    ├── main/java/demo/mixed/
    │   ├── MixedApplication.java
    │   ├── Note.java
    │   ├── NewNote.java
    │   ├── ApiError.java
    │   ├── NoteStore.java
    │   ├── NoteHandler.java
    │   ├── FunctionalRoutes.java
    │   ├── AnnotatedNotesController.java
    │   └── MvcInfrastructureConfig.java
    └── test/java/demo/mixed/
        └── MixedMvcTest.java
```

**`pom.xml`**

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
    <artifactId>mvc-mixed-endpoints</artifactId>
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
            <artifactId>spring-boot-starter-validation</artifactId>
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

### 11.2. Boot application

**`src/main/java/demo/mixed/MixedApplication.java`**

```java
package demo.mixed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MixedApplication {
    public static void main(String[] args) {
        SpringApplication.run(MixedApplication.class, args);
    }
}
```

### 11.3. DTOs và entity đơn giản dùng chung

**`src/main/java/demo/mixed/Note.java`**

```java
package demo.mixed;

public record Note(long id, String title) {}
```

**`src/main/java/demo/mixed/NewNote.java`**

```java
package demo.mixed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewNote(
        @NotBlank @Size(max = 80) String title) {}
```

**`src/main/java/demo/mixed/ApiError.java`**

```java
package demo.mixed;

public record ApiError(String code) {}
```

Tất cả dùng Java 17 record. Jackson có thể serialize/deserialize các record này trong Spring Boot 3.3.5. `@NotBlank` và `@Size` chỉ đặt metadata constraints; chúng cần được validate qua `@Valid` (annotated method) hoặc `Validator.validate` (functional handler).

### 11.4. Shared business component

**`src/main/java/demo/mixed/NoteStore.java`**

```java
package demo.mixed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class NoteStore {

    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentHashMap<Long, Note> notes =
            new ConcurrentHashMap<>();

    public Note create(String title) {
        Note note = new Note(ids.incrementAndGet(), title.strip());
        notes.put(note.id(), note);
        return note;
    }

    public Optional<Note> find(long id) {
        return Optional.ofNullable(notes.get(id));
    }

    public List<Note> list() {
        List<Note> data = new ArrayList<>(notes.values());
        data.sort(Comparator.comparingLong(Note::id));
        return data;
    }

    public Optional<Note> latest() {
        return list().stream()
                .max(Comparator.comparingLong(Note::id));
    }

    public List<Note> search(String q) {
        String term = q.toLowerCase(Locale.ROOT).strip();
        return list().stream()
                .filter(n -> n.title()
                        .toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }
}
```

Store chỉ là ví dụ: dữ liệu biến mất sau restart; không có persistence/authorization. Sự hiện diện của `ConcurrentHashMap` không giải quyết được transactions, tenant isolation hay unique constraints. Với DB thật, thay bằng service/repository/JPA và đặt `@Transactional` trên service method cần transaction. Functional lẫn annotated đều gọi cùng Spring proxy nếu injection đúng, nên không phải hai business implementations riêng.

### 11.5. Functional handler: ServerRequest/ServerResponse

**`src/main/java/demo/mixed/NoteHandler.java`**

```java
package demo.mixed;

import java.net.URI;

import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class NoteHandler {

    private final NoteStore store;
    private final Validator validator;

    public NoteHandler(NoteStore store, Validator validator) {
        this.store = store;
        this.validator = validator;
    }

    public ServerResponse list(ServerRequest request) throws Exception {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.list());
    }

    public ServerResponse latest(ServerRequest request) throws Exception {
        Note note = store.latest().orElse(null);
        if (note == null) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }

    public ServerResponse search(ServerRequest request) throws Exception {
        String q = request.param("q").orElse("");
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.search(q));
    }

    public ServerResponse find(ServerRequest request) throws Exception {
        long id;
        try {
            id = Long.parseLong(request.pathVariable("id"));
        } catch (NumberFormatException ex) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("bad_id"));
        }

        Note note = store.find(id).orElse(null);
        if (note == null) {
            return ServerResponse.notFound().build();
        }

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }

    public ServerResponse create(ServerRequest request) throws Exception {
        NewNote input = request.body(NewNote.class);

        if (input == null || !validator.validate(input).isEmpty()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiError("invalid_note"));
        }

        Note note = store.create(input.title());
        URI location = URI.create("/api/fn/notes/" + note.id());

        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .body(note);
    }
}
```

**Điểm cần quan sát:**

- `@Component` để inject handler và reuse `NoteStore`/`Validator`.
- Không có `@RequestMapping`/`@PostMapping` trên class: functional `RouterFunction` chịu trách nhiệm mapping.
- `request.body(NewNote.class)` đọc JSON bằng MVC converter, sau đó handler chủ động gọi validator.
- `request.pathVariable("id")` được chuyển String sang long thủ công; nếu không hợp lệ trả JSON error `bad_id`.
- `ServerResponse.created(location)` trả **HTTP 201 Created** và `Location`; URI tương đối được xây từ prefix do server kiểm soát. Không lấy Host/Forwarded user-supplied rồi ghép redirect.
- Handler không giữ request-specific state trong object fields; `NoteStore` đồng thời được hai handler styles sử dụng.
- Trong production, `request.body(...)` có thể ném khi JSON malformed; demo không định nghĩa custom malformed JSON error schema. Test thực trước khi hứa một định dạng lỗi cụ thể.

### 11.6. RouterFunction bean

**`src/main/java/demo/mixed/FunctionalRoutes.java`**

```java
package demo.mixed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.contentType;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
public class FunctionalRoutes {

    @Bean
    RouterFunction<ServerResponse> noteRoutes(NoteHandler handler) {
        return route()
                // Literal routes trước variable route.
                .GET("/api/fn/notes/latest", handler::latest)
                .GET("/api/fn/notes/search", handler::search)
                .GET("/api/fn/notes/{id}", handler::find)
                .GET("/api/fn/notes", handler::list)
                .POST("/api/fn/notes",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::create)
                .filter((request, next) -> {
                    // Chỉ minh họa scope, KHÔNG phải authorization.
                    String blocked = request.headers()
                            .firstHeader("X-Demo-Block");
                    if ("true".equalsIgnoreCase(blocked)) {
                        return ServerResponse
                                .status(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ApiError("demo_blocked"));
                    }
                    return next.handle(request);
                })
                .build();
    }
}
```

Cả `latest` và `search` nằm trước `/{id}`, tránh bị parse thành ID. `contentType(JSON)` áp cho POST body; nó không phải `accept(JSON)` (kiểm tra kiểu response client muốn nhận). Functional filter chỉ có hiệu lực trong router bean này, không áp lên annotated endpoint dưới `/api/annotated/**`.

Khi API có hàng chục routes, dùng `path(...)`/`nest(...)` hoặc multiple router beans để nhóm domain. Không dùng một wildcard functional route `/**` bắt toàn bộ và vô tình che static files, controllers hoặc error handlers.

### 11.7. Annotated controller dùng chung store

**`src/main/java/demo/mixed/AnnotatedNotesController.java`**

```java
package demo.mixed;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/annotated/notes")
public class AnnotatedNotesController {

    private final NoteStore store;

    public AnnotatedNotesController(NoteStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Note> list() {
        return store.list();
    }

    @GetMapping("/latest")
    public ResponseEntity<Note> latest() {
        return store.latest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public List<Note> search(@RequestParam(defaultValue = "") String q) {
        return store.search(q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Note> find(@PathVariable long id) {
        return store.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Note> create(
            @Valid @RequestBody NewNote input) {
        Note note = store.create(input.title());
        URI location = URI.create(
                "/api/annotated/notes/" + note.id());
        return ResponseEntity.created(location).body(note);
    }
}
```

Annotated code cần ít HTTP plumbing hơn khi binding/validation đơn giản:
- `@PathVariable long id`: Spring MVC tự convert.
- `@RequestParam(defaultValue = "")`: Spring MVC tự đọc query/default.
- `@Valid @RequestBody`: dùng body converter + automatic Bean Validation.
- `ResponseEntity.created(location)`: trả 201 + Location.

Cùng `NoteStore` nghĩa note tạo qua `/api/fn/notes` có thể đọc qua `/api/annotated/notes/{id}` và ngược lại. Điều này chứng minh **coexistence trong chung business layer** chứ không chỉ hai response tĩnh.

### 11.8. WebMvcConfigurer: interceptor và CORS chung

**`src/main/java/demo/mixed/MvcInfrastructureConfig.java`**

```java
package demo.mixed;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcInfrastructureConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler) {
                response.setHeader("X-MVC-Pipeline", "active");
                return true;
            }
        }).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://portal.example.test")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
```

Đây là cấu hình **bổ sung**, không tạo DispatcherServlet thứ hai. Interceptor gửi `X-MVC-Pipeline: active` cho các MVC handlers mà MVC configuration đã gắn interceptor theo path; test dưới đây kiểm tra cả functional và annotated endpoints.

CORS cho phép browser scripts tại `https://portal.example.test` gọi các API đường `/api/**` theo methods/headers đã cho, nếu handler mapping/CORS integration hỗ trợ đầy đủ. Đây không cho phép website không đáng tin đọc dữ liệu user; khi thêm cookie auth, cần policy allowed credentials, CSRF và Spring Security CORS integration cẩn thận.

**Không bật `allowedOrigins("*")` kết hợp credentials tùy tiện**. Và không dùng CORS thay authentication: curl hoặc non-browser clients vẫn gọi API được khi không có security controls.

## 12. Chạy ứng dụng, quan sát cùng DispatcherServlet

Tại thư mục `mvc-mixed-endpoints` chứa `pom.xml`, cài Java 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh chạy theo thứ tự. Nếu chỉ muốn chạy server mà chưa thử tests, có thể chạy `mvn spring-boot:run` riêng. Với Boot default, server nghe tại `http://localhost:8080`.

### 12.1. Tạo note bằng functional route

```bash
curl -i -X POST 'http://localhost:8080/api/fn/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring MVC Functional"}'
```

Trên JVM mới, chưa có dữ liệu nào:

```http
HTTP/1.1 201
Location: /api/fn/notes/1
Content-Type: application/json
X-MVC-Pipeline: active

{"id":1,"title":"Spring MVC Functional"}
```

Status 201 và Location là giao thức HTTP thật; không tạo URL bằng JSON field rồi trả HTTP 200. URL tương đối tránh phụ thuộc Host header chưa được kiểm chứng. Header `X-MVC-Pipeline` là từ MVC interceptor, không phải functional router filter.

### 12.2. Đọc lại từ annotated controller

```bash
curl -i 'http://localhost:8080/api/annotated/notes/1'
```

Dự kiến 200 và JSON:

```json
{"id":1,"title":"Spring MVC Functional"}
```

Nếu thấy đúng note đó, chứng minh hai endpoint styles dùng chung `NoteStore`.

### 12.3. Tạo note qua annotation, đọc qua functional

```bash
curl -i -X POST 'http://localhost:8080/api/annotated/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring MVC Annotation"}'

curl -i 'http://localhost:8080/api/fn/notes/2'
```

Dự kiến note ID 2 xuất hiện trong response functional nếu hai requests chạy tuần tự sau lần tạo đầu và không có request nào khác chen vào. ID có thể khác trong test/in-memory state khác; đừng hardcode ID trong automated tests.

### 12.4. Search, latest và precedence

```bash
curl -G -i 'http://localhost:8080/api/fn/notes/search' \
  --data-urlencode 'q=Functional'

curl -i 'http://localhost:8080/api/fn/notes/latest'
```

Search trả JSON array notes có title chứa "Functional". Latest trả note có ID lớn nhất. Nếu route `/{id}` bị đặt trước `/search` hoặc `/latest`, client có thể nhận 400 bad_id — đây là lỗi route precedence của functional builder.

### 12.5. Functional-only filter

```bash
curl -i 'http://localhost:8080/api/fn/notes' \
  -H 'X-Demo-Block: true'

curl -i 'http://localhost:8080/api/annotated/notes' \
  -H 'X-Demo-Block: true'
```

Request đầu dự kiến `403` với body:

```json
{"code":"demo_blocked"}
```

Request thứ hai vẫn được annotated handler trả 200; nó không nằm trong functional router filter. **Đây không phải một policy cho quyền truy cập**: client có thể tự sửa header, và mọi security thực tế phải được kiểm soát ở Spring Security/service.

### 12.6. Validation 400 ở cả hai nhưng body có thể khác

```bash
curl -i -X POST 'http://localhost:8080/api/fn/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'

curl -i -X POST 'http://localhost:8080/api/annotated/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'
```

Functional handler chủ động trả:

```http
HTTP/1.1 400
Content-Type: application/json

{"code":"invalid_note"}
```

Annotated handler có thể trả 400 nhờ `@Valid`, nhưng **body mặc định do Boot/MVC error handling quyết định**, không phải tự nhiên trùng với `ApiError("invalid_note")`. Nếu API client cần error body chung, thiết kế adapter/error contract rõ ràng và test cả hai.

## 13. MockMvc: contract tests cho hai handler styles và hạ tầng dùng chung

**`src/test/java/demo/mixed/MixedMvcTest.java`**

```java
package demo.mixed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MixedMvcTest {

    @Autowired
    MockMvc mvc;

    @Test
    void functionalCreateCanBeReadByAnnotatedController()
            throws Exception {
        MvcResult created = mvc.perform(post("/api/fn/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Cross Style One\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String location = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        assertTrue(location.matches("/api/fn/notes/[1-9][0-9]*"));

        String id = location.substring(location.lastIndexOf('/') + 1);
        mvc.perform(get("/api/annotated/notes/" + id))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Cross Style One"));
    }

    @Test
    void annotatedCreateCanBeReadByFunctionalHandler()
            throws Exception {
        MvcResult created = mvc.perform(
                    post("/api/annotated/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Cross Style Two\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String location = created.getResponse()
                .getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        String id = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(get("/api/fn/notes/" + id))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.title").value("Cross Style Two"));
    }

    @Test
    void mvcInterceptorAppliesToBothHandlerStyles()
            throws Exception {
        mvc.perform(get("/api/fn/notes"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    "X-MVC-Pipeline", "active"));

        mvc.perform(get("/api/annotated/notes"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    "X-MVC-Pipeline", "active"));
    }

    @Test
    void functionalFilterDoesNotInterceptAnnotations()
            throws Exception {
        mvc.perform(get("/api/fn/notes")
                    .header("X-Demo-Block", "true"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("demo_blocked"));

        mvc.perform(get("/api/annotated/notes")
                    .header("X-Demo-Block", "true"))
           .andExpect(status().isOk());
    }

    @Test
    void functionalValidationUsesInjectedValidator()
            throws Exception {
        mvc.perform(post("/api/fn/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("invalid_note"));
    }

    @Test
    void annotationValidationUsesMethodArgumentPipeline()
            throws Exception {
        mvc.perform(post("/api/annotated/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"   \"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void literalRoutesOutrankVariableRouteByDeclaredOrder()
            throws Exception {
        mvc.perform(post("/api/fn/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Latest Marker\"}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/fn/notes/latest"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void searchReadsQueryParameter() throws Exception {
        mvc.perform(post("/api/fn/notes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Unique Search Phrase\"}"))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/fn/notes/search")
                    .param("q", "Unique Search Phrase"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].title")
                    .value("Unique Search Phrase"));
    }

    @Test
    void missingResourceGets404InBothStyles()
            throws Exception {
        mvc.perform(get("/api/fn/notes/999999999"))
           .andExpect(status().isNotFound());

        mvc.perform(get("/api/annotated/notes/999999999"))
           .andExpect(status().isNotFound());
    }

    @Test
    void badFunctionalIdHasExplicitErrorContract()
            throws Exception {
        mvc.perform(get("/api/fn/notes/not-a-number"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("bad_id"));
    }
}
```

Bộ test trên có **10 test methods**. Chạy:

```bash
mvn test
```

Khi source files được tạo đúng và tất cả tests pass, Maven exit code 0 với summary tương tự `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`. Đây là ví dụ được viết và rà soát tĩnh theo Java 17/Spring MVC 6.1; **chưa chạy Maven thực tế trong môi trường biên soạn bài trên Google Sheets**.

### Vì sao test không hardcode ID 1/2?

Tests dùng `NoteStore` chung trong cùng application context; thứ tự chạy JUnit tests không phải contract. Sau khi POST, đọc `Location` thực và lấy ID từ URI rồi thực hiện GET. Như vậy tests ít phụ thuộc thứ tự/parallel test state hơn. Với production DB, còn phải tính isolation/test transaction/cleanup theo từng test.

### MockMvc kiểm chứng được gì và còn thiếu gì?

MockMvc kiểm chứng:
- DispatcherServlet routing tới đúng functional hoặc annotated handler.
- Header từ MVC interceptor.
- Functional router filter không áp annotated endpoint.
- JSON converter, validation/status và `Location`.
- Shared store/read-your-write behavior.
- Literal route không bị `/{id}` bắt nhầm.

MockMvc **không đủ** để chứng minh:
- Reverse proxy rewrite Host/Forwarded/CORS đúng trên deployment thật.
- Servlet security chain hoạt động theo cấu hình production nếu test không thêm Spring Security.
- HTTP client abort/async timeout/socket backpressure.
- DB transaction rollback, deadlocks, connection pooling dưới concurrency.
- OpenAPI generator phát hiện functional routes.
- Cache/CDN policy không leak dữ liệu user.

Bổ sung integration tests qua embedded server/gateway và Spring Security test support khi ứng dụng thật có những chức năng đó.

## 14. CORS và preflight: cấu hình phải đi qua đúng mapping

Một browser gửi request từ `https://portal.example.test` tới `http://localhost:8080/api/fn/notes` sẽ bị same-origin policy kiểm soát. CORS là cơ chế cho **browser** quyết định đọc cross-origin response; nó không chứng minh user đã authenticated hay authorized.

Với `WebMvcConfigurer.addCorsMappings("/api/**")`, MVC HandlerMappings có thể dùng policy đã đăng ký. Tuy nhiên preflight `OPTIONS` được xử lý ở mapping/CORS processing layer; `RouterFunction` với các predicates GET/POST có thể tạo nhánh error khác nếu cấu hình/routing/custom filters khác nhau. Khi thêm functional endpoints vào ứng dụng cũ, **phải test preflight** thực:

```bash
curl -i -X OPTIONS \
  'http://localhost:8080/api/fn/notes' \
  -H 'Origin: https://portal.example.test' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

Quan sát `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers` và status. Không giả định header `@CrossOrigin` trên một annotated controller sẽ tự áp dụng cho một functional endpoint khác.

Khi có Spring Security, nên tích hợp CORS ở security filter chain phù hợp, vì preflight thường không chứa session cookie; nếu security chặn trước CORS processor thì browser request có thể thất bại. Chỉ bật `allowCredentials(true)` với origins đáng tin và không phối hợp wildcard origins không hợp lệ.

## 15. Multipart, file upload, static resources và templates

Khi dần chuyển sang functional endpoints, không phải mọi use case đều có một bản chuyển đổi 1:1 đơn giản.

### Multipart / forms

Annotated MVC cho phép:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> upload(
        @RequestPart("metadata") UploadMetadata metadata,
        @RequestPart("file") MultipartFile file) {
    // ...
}
```

Snippet trên có types chưa định nghĩa. Đây là use case Spring binding tự hỗ trợ đa dạng. Với WebMvc.fn có thể truy cập `ServerRequest.servletRequest()` / multipart servlet facilities và dùng converters/helpers phù hợp, nhưng **không giả định** có cùng `@RequestPart` method argument auto-resolution trong một HandlerFunction. Nếu API có nhiều uploads/validation constraints/binding, giữ annotated controller có thể đơn giản và dễ bảo trì hơn.

### Static resources

Spring MVC thường phục vụ JS/CSS/images từ `classpath:/static` qua resource handler và cache policy. WebMvc.fn có `RouterFunctions.resources(...)`/`resource(...)` cho cases đặc biệt; không cần thay static resource handler chỉ để thống nhất phong cách functional.

Nếu viết SPA fallback route như `/**`, đảm bảo không che:
- `/api/**` handlers và 404.
- Actuator/health endpoints.
- Static resources/assets caching và range requests.
- Security-sensitive paths.

Với một URL không khớp functional route, RouterFunction có thể trả empty, để DispatcherServlet kiểm tra HandlerMappings khác. Nhưng một catch-all functional route có thể match quá sớm và làm mất fallback theo ý định.

### Rendering templates

Annotated `@Controller` có `Model`, `@ModelAttribute` và view name. WebMvc.fn cũng có cơ chế render views qua `ServerResponse`/view resolvers ở MVC, nhưng không có automatic method argument binding của controller annotations. Nếu dự án lớn dùng Thymeleaf forms, custom binders và validation message codes, phải đánh giá chi phí chuyển đổi; giữ annotated MVC cho web pages và WebMvc.fn cho API modules có thể là cách kết hợp hợp lý.

## 16. Observable application: logs, metrics, tracing và interceptors

Trước khi thêm một routing model, team nên biết tracing/metrics đang dựa trên lớp nào.

1. **Servlet Filter / Spring Security filter**: có thể quan sát cả functional và annotated requests theo URL pattern.
2. **MVC interceptor**: thường bao phủ các HandlerMappings MVC do cấu hình quản lý, nhưng có thể phụ thuộc dispatch type, exception/async lifecycle.
3. **HandlerMethod-specific instrumentations**: có thể cần thêm support cho `HandlerFunction`; không giả định tên controller/method là metrics tags cho functional endpoints.
4. **Micrometer/Observations**: frameworks có integration HTTP server chung, nhưng naming/tagging của route patterns cần kiểm chứng ở version được deploy.
5. **Structured logging/MDC**: đường route nào cũng cần trace ID, tránh log secret body/Authorization; async workers cần propagation nếu có.

Một cách kiểm tra đơn giản là gửi cùng loại GET tới `/api/fn/notes` và `/api/annotated/notes`, rồi so sánh:
- Trace/span ID có xuất hiện đầy đủ?
- HTTP method/status/route pattern tags có nhất quán?
- Request latency/error counts có được tính cho cả hai?
- Security principal/tenant correlation có đúng?
- Exception được ghi nhận là 4xx hay 5xx?
- Không tạo hai spans trùng khi Servlet async redispatch?

Đây là phần quan trọng khi migration: API có thể trả HTTP đúng nhưng **mất giám sát** nếu instrumentation chỉ bắt `HandlerMethod`.

## 17. Versioning và contract tests khi chuyển dần

Một dự án cũ có `/api/v1/orders` được annotated controller phục vụ. Nếu chuyển từng route sang functional, có thể tách theo HTTP operation hoặc resource subdomain:

```text
Trước migration:
  GET  /api/v1/orders        @RestController
  POST /api/v1/orders        @RestController
  GET  /api/v1/orders/{id}   @RestController

Giai đoạn chuyển:
  GET  /api/v1/orders        @RestController
  POST /api/v1/orders        WebMvc.fn
  GET  /api/v1/orders/{id}   @RestController

Sau migration nếu cần:
  GET  /api/v1/orders        WebMvc.fn
  POST /api/v1/orders        WebMvc.fn
  GET  /api/v1/orders/{id}   WebMvc.fn
```

**Điều kiện:** một method + route chỉ có **một** owner trong từng release; không khai báo duplicate và hy vọng HandlerMapping order tự xử lý migration. Cập nhật controller/router theo commit/release có tests; không thay URL contract với clients một cách vô tình.

### Contract checklist

Với mỗi endpoint, so sánh trước/sau:
- Method/path/trailing slash và status 200/201/204/400/404/405/406/415.
- Header `Content-Type`, `Location`, `Cache-Control`, `ETag`, `Vary`, CORS và security headers.
- JSON field names, null handling, ordering (nếu contract yêu cầu), locale/timezone.
- Validation constraints/error body/schema.
- Cookie/session/CSRF/authorization behavior.
- Pageable/sorting/query param semantics, duplicate query values.
- Multipart and file download: content type, bytes, range requests.
- Compression/proxy/forwarded host and context paths.
- OpenAPI docs + generated clients, operation IDs và required fields.
- Trace/metrics, load/timeout và rate limiting.

**Không đo “thành công” chỉ bằng việc controller không còn @GetMapping.** Migration thành công khi API consumers không bị phá hợp đồng và hạ tầng quan sát/bảo mật không suy giảm.

## 18. Các chiến lược xử lý lỗi cho một ứng dụng hỗn hợp

Mục tiêu là một response schema thống nhất, ví dụ:

```json
{
  "code": "invalid_note",
  "message": "Title must not be blank"
}
```

Nhưng không cần ép cả hai handler styles đi qua **cùng exception class**. Một cách tổ chức:

1. Định nghĩa `ApiError` hoặc `ProblemDetail` contract ở module HTTP-shared.
2. Có adapter cho annotated exceptions (`MethodArgumentNotValidException`, type mismatch, message-not-readable) và adapter cho functional exceptions/body validation.
3. Mọi branch trả status phù hợp; đừng vô tình biến business 409 thành 500 hoặc 400.
4. Ghi log với trace ID, không leak raw rejected PII/secrets.
5. Tests gửi cùng invalid requests tới cả endpoints và so sánh status + stable error fields.
6. Kiểm thử cả errors ở converter/write phase vì không phải mọi exception xảy ra trong handler lambda.
7. Khi đã trả một phần streaming response, status/body không còn sửa tùy ý.
8. Với handler exception resolutions, phân biệt hạ tầng global với `@ControllerAdvice` hướng method annotations.

### Vì sao không bắt Exception rộng trong mọi functional filter?

```java
// PHẢN VÍ DỤ: che mọi bug thành bad request.
try {
    return next.handle(request);
} catch (Exception e) {
    return ServerResponse.badRequest().body("invalid");
}
```

Lỗi database, NullPointerException, permission checks hoặc network failures có thể bị phân loại sai, che sự cố và làm monitoring/circuit breaker không hiệu quả. Chỉ bắt exception mà application có thể giải thích đúng ngữ nghĩa, còn những lỗi ngoài dự kiến để error boundary chung ghi nhận 5xx.

## 19. Hiệu năng: functional style không phải non-blocking I/O

Một functional handler:

```java
return ServerResponse.ok().body(
        blockingRepository.findAll());
```

vẫn có thể block Servlet request thread khi `findAll()` chạy JDBC đồng bộ. Trong cùng MVC Servlet stack, thay `@GetMapping` bằng lambda/RouterFunction không biến database/HTTP client thành reactive/non-blocking.

Nếu ứng dụng gặp bottleneck do I/O:
- Đo DB pool, remote latency và thread saturation trước.
- Cân nhắc pooling, caching, queries/index và timeouts.
- Với MVC, `Callable`/`DeferredResult` có thể giải phóng initial Servlet thread, nhưng blocking work có thể chuyển sang worker executor.
- Với streaming/live events, đánh giá backpressure và connection limits.
- Chỉ cân nhắc WebFlux khi data path phù hợp reactive/non-blocking và team có lý do về concurrency/resource usage; không dùng WebFlux chỉ để có functional router syntax.

Một migration WebMvc.fn hữu ích về **cấu trúc code** cũng có thể giữ nguyên throughput. Vì vậy benchmark dựa trên workload, không dựa vào từ "functional".

## 20. Những lỗi thường gặp khi tích hợp

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Functional endpoint trả 404 | RouterFunction bean không scan, import WebFlux package, predicate không match | Kiểm tra application context, package và request method/path |
| Annotated API bị mất sau khi thêm router | Catch-all functional route hoặc custom MVC config | Tránh `/**`/overlap, không bật `@EnableWebMvc` tùy tiện |
| Route /latest bị parse thành ID | `/{id}` đặt trước literal route | Đặt literal routes đầu, test precedence |
| Có hai implementations cùng URL | Hai HandlerMappings cùng có match | Phân một owner cho mỗi method/path, chuyển theo release |
| JSON đọc được bên annotated nhưng functional fail | `request.body(...)`/contentType predicate khác annotation pipeline | Dùng đúng converters/media type, test malformed JSON |
| `@NotBlank` không báo 400 bên functional | Chưa gọi Validator | Inject `Validator` và validate explicit |
| Error body khác dù cùng 400 | Annotated advice khác functional explicit responses | Shared API error contract + tests |
| `@ControllerAdvice` không bắt functional exception | Handler không phải HandlerMethod | Functional error boundary hoặc global resolver phù hợp |
| Functional filter không chạy trên annotation | Router-level scope | Sử dụng Servlet filter / Spring Security cho concerns toàn app |
| MVC interceptor không chạy ở một custom mapping | HandlerMapping custom không được cấu hình tương ứng | Kiểm tra registry/MappedInterceptor và mapping order |
| CORS preflight bị 403 | Security/CORS order hoặc policy thiếu | Spring Security CORS integration và OPTIONS tests |
| POST functional bị CSRF 403 | Cookie/session auth vẫn có CSRF policy | Gửi CSRF token hợp lệ, không tắt tùy tiện |
| OpenAPI thiếu route | Tool không tự discover RouterFunction đầy đủ | Chọn compatible integration hoặc explicit docs |
| Trace/meter thiếu handler tag | Metrics integration chỉ nhìn annotation metadata | Verify instrumentation cho functional handler |
| ResponseBodyAdvice không can thiệp | Functional response writing path khác annotated | Viết shared converter/HTTP adapter phù hợp, test |
| URL Location sai sau reverse proxy | Forwarded headers trust/path prefix sai | Trusted ingress, canonical base URL/relative URI |
| DTO overposting/IDOR | Validation chỉ kiểm tra cú pháp, không xác thực quyền | Server-owned identity và per-resource authorization |
| Static JS/css bị 404 | Catch-all route chặn resource handler | Đặt static routing/SPA fallback đúng scope |
| GET trả body khác nhau giữa hai styles | Jackson/view configuration hoặc adapter behavior khác | Contract tests cả fields, headers, bytes và nulls |
| Async request mất MDC/security context | Cross-thread context không tự propagate | Delegating executor/context propagation đúng |
| File upload không bind MultipartFile | Functional không có @RequestPart method args như annotated | Giữ annotated cho multipart hoặc viết binding explicit |

## 21. Checklist triển khai production

1. Xác định functional endpoints có thật sự đem lại lợi ích về route composition, testability, maintainability.
2. Không thêm một MVC Servlet application thứ hai; dùng RouterFunction bean trong context hiện hữu.
3. Sử dụng đúng namespace `org.springframework.web.servlet.function`.
4. Giữ annotated controllers cần thiết; không rewrite toàn app vô lý.
5. Tách REST adapter khỏi business layer, dùng lại Spring `@Service` và transaction boundaries.
6. Kiểm tra RouteFunctionMapping discovery/order, nhất là nếu nhiều router beans.
7. Tránh method/path ownership trùng nhau; đặt literal route trước path variable.
8. Tiếp tục dùng converter/JSON configuration chung; không vô tình override defaults.
9. Thực thi Bean Validation chủ động ở functional handlers.
10. Thiết kế error schema/chính sách status thống nhất cả hai pipelines.
11. Đặt Spring Security/CSRF/auth ở filter chain/service chứ không chỉ functional filter.
12. Test MVC interceptors và Servlet filters với functional + annotated routes.
13. Test CORS preflight, OPTIONS/HEAD và headers.
14. Kiểm tra static resource handling/SPA fallback để không che routes khác.
15. Bảo vệ Host/Forwarded headers và location redirects khi chạy sau proxy.
16. Verify OpenAPI docs và generated client contract sau migration.
17. Verify logging/tracing/metrics có đủ routes/status.
18. Không suy luận functional là non-blocking; kiểm tra database/network performance thật.
19. MockMvc contract tests trên mọi path; bổ sung integration tests cho proxy/security/database.
20. Roll out từng route có rollback strategy, theo dõi 4xx/5xx và latency.

## 22. Bài tập tự kiểm tra

1. Vì sao WebMvc.fn và @RestController có thể sống chung trong một Spring Boot Web application?
2. `RouterFunctionMapping` và `RequestMappingHandlerMapping` khác nhau ở vai trò gì?
3. Với `GET /api/fn/notes/latest`, vì sao `/{id}` có thể gây lỗi nếu đứng trước?
4. Một `NoteStore` singleton có thể được dùng bởi cả annotated và functional handlers không? Tại sao?
5. `ServerRequest.body(NewNote.class)` có tự động kích hoạt `@NotBlank` không?
6. Nếu một app có `@RestControllerAdvice` cho MethodArgumentNotValidException, có chắc mọi functional validation error đi qua advice đó không?
7. `WebMvcConfigurer.addInterceptors` khác `RouterFunctions.Builder.filter` về phạm vi ở đâu?
8. Functional filter gắn vào `/api/fn/**` có bảo vệ `/api/annotated/**` không?
9. Vì sao Spring Security filter chain vẫn phải áp dụng khi chuyển sang functional endpoints?
10. Một preflight request OPTIONS từ browser có thể bị chặn ở đâu trước khi tới functional handler?
11. `@Transactional` trong shared service có ý nghĩa thế nào đối với hai handler styles?
12. Có thể tránh duplicate route bằng cách viết một HandlerMapping với order nhỏ hơn không, hay nên có một owner rõ ràng?
13. Vì sao static fallback `/**` có thể gây ra các 404/200 sai cho API?
14. Khi mixed app trả 400 từ cả hai styles, client có chắc nhận cùng JSON error schema không?
15. Vì sao đổi syntax router không tự làm JDBC non-blocking?
16. Khi migrate một endpoint, những HTTP headers nào ngoài JSON body cần kiểm tra?
17. CORS có thay authentication/authorization được không?
18. Làm sao chứng minh interceptor dùng chung hoạt động: chỉ đọc config hay test HTTP thực?
19. Vì sao OpenAPI instrumentation có thể nhìn annotated endpoints dễ hơn functional routes?
20. Khi nào giữ annotated controller thay vì chuyển sang WebMvc.fn?

**Gợi ý:** (1) Cùng DispatcherServlet, khác HandlerMapping/Adapter. (2) Functional vs annotation routing. (3) First-match order. (4) Có, cùng Spring IoC context/business service. (5) Không; cần Validator explicit. (6) Không nên giả định. (7) MVC mapping config vs router-local handler composition. (8) Không. (9) Bảo vệ toàn HTTP request, không chỉ lambdas. (10) Servlet/Security filters hoặc CORS/HandlerMapping policy. (11) Transaction áp dụng tại service proxy, không phụ thuộc HTTP adapter. (12) Một route owner rõ ràng an toàn hơn. (13) Match quá rộng có thể che static/other mappings. (14) Không. (15) Framework vẫn chạy Servlet stack/blocking dependencies. (16) Status, Content-Type, Location, CORS, cache, security, ETag... (17) Không. (18) MockMvc/integration test cả hai paths. (19) Tooling đọc HandlerMethod/annotations. (20) Khi binding/form/validation/error tooling hiện hành phù hợp hơn, không có lợi ích migration rõ.

## 23. Tài liệu chính thức và đọc thêm

- [Spring Framework 6.1 — Functional Endpoints (Spring MVC)](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc-functional.html)
- [Spring Framework — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Framework 6.1 — RouterFunctions Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/function/RouterFunctions.html)
- [Spring Framework 6.1 — ServerRequest Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/function/ServerRequest.html)
- [Spring Framework 6.1 — ServerResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/function/ServerResponse.html)
- [Spring Framework 6.1 — RouterFunctionMapping Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/function/support/RouterFunctionMapping.html)
- [Spring Framework — MVC Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config.html)
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)
- [Spring Framework — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html)
- [Spring Framework — MVC Interceptors](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html)
- [Spring Framework — Exception Handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- [Spring MVC Testing — MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
- [Spring Security — Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)

**Tổng kết:** Functional endpoints là một cách khai báo HTTP routes trong Spring MVC hiện hữu, **không phải một web server/framework khác**. `RouterFunction` + `HandlerFunction` có thể cùng chạy với annotated controllers trên `DispatcherServlet`; chúng dùng chung Spring IoC, service/repository, HTTP converters và nhiều cấu hình MVC. Nhưng annotation-specific argument binding/validation/error handling **không tự được sao chép sang functional handlers**. Để tích hợp an toàn, hãy giữ business logic dùng chung, phân rõ route ownership, thiết kế validation và error contracts tường minh, bảo vệ HTTP bằng Servlet/Spring Security, kiểm thử interceptors/CORS/OpenAPI và phát hành từng phần có regression tests.


</details>

- [Quay lại đầu trang](#back-to-top)
