<a id="back-to-top"></a>

# Controller WebFlux

## Menu
- [Ánh xạ controller bằng annotation và các điểm chung với MVC](#webflux-controllers-001)
- [Đọc tham số, header, cookie, Principal và dữ liệu trong ServerWebExchange](#webflux-controllers-002)
- [@RequestBody với Mono/Flux và phản hồi reactive qua ResponseEntity](#webflux-controllers-003)
- [Binding, Bean Validation và nhận diện lỗi đầu vào trong WebFlux](#webflux-controllers-004)
- [Multipart, FilePart, PartEvent và xử lý tệp theo luồng](#webflux-controllers-005)
- [@ExceptionHandler, @RestControllerAdvice và ProblemDetail trong WebFlux](#webflux-controllers-006)
- [Trả view, Model và tích hợp template engine reactive](#webflux-controllers-007)
- [Cấu hình CORS, tài nguyên tĩnh và HTTP caching trong WebFlux](#webflux-controllers-008)

## <a id="webflux-controllers-001">Ánh xạ controller bằng annotation và các điểm chung với MVC</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ controller bằng annotation trong Spring WebFlux và các điểm chung với Spring MVC

> **Phạm vi:** Spring Framework 6.2.x, Spring Boot 3.5.x, Java 17+, annotation-based WebFlux trên reactive HTTP stack. Bài giải thích `@Controller`, `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`; match URL/HTTP method/params/headers/consumes/produces; đọc URI/query/header/body; trả `Mono`, `Flux`, `ResponseEntity`; validation, errors, content negotiation và kiểm thử bằng `WebTestClient`. Đồng thời đối chiếu **những annotation dùng chung** với MVC và **những khác biệt thực sự** về dispatching, request-body decoding, I/O, method signatures và threading. Spring Framework 7/Spring Boot 4 có thể thêm APIs (ví dụ version mapping) và đổi tên starter/test package; ví dụ hoàn chỉnh dưới đây cố định tại **Boot 3.5 + WebFlux**.

## 1. Cùng syntax annotation, khác hạ tầng xử lý

Nếu đã học Spring MVC, bạn sẽ nhận ra hầu hết annotation:

```java
@RestController
@RequestMapping("/api/books")
class BookController {

    @GetMapping("/{id}")
    Mono<BookView> find(@PathVariable long id) {
        return service.find(id);
    }

    @PostMapping
    Mono<BookView> create(@RequestBody Mono<CreateBook> input) {
        return input.flatMap(service::create);
    }
}
```

**Đây là snippet**, cần khai báo `service`, DTO và imports; phần demo runnable nằm ở mục 11. Những annotation trên từ `org.springframework.web.bind.annotation` cũng được dùng ở Spring MVC. Tuy nhiên WebFlux **không** dùng `DispatcherServlet` để chạy handler; nó dùng các thành phần reactive như `DispatcherHandler` và `HandlerMapping` trên `ServerWebExchange`.

```text
HTTP request
     |
Reactor Netty / Servlet nonblocking adapter / server runtime
     |
HttpHandler, WebFilter chain
     |
DispatcherHandler                  <-- WebFlux
     |
RequestMappingHandlerMapping      <-- chọn @RequestMapping method
     |
RequestMappingHandlerAdapter      <-- resolve method arguments
     |
@Controller/@RestController method
     |
Mono<T> / Flux<T> / ResponseEntity<T>
     |
HandlerResultHandler + HttpMessageWriter
     |
HTTP response
```

**Trong MVC** đường đi tương ứng là `DispatcherServlet` → `HandlerMapping` → `HandlerAdapter` → `HttpMessageConverter` → Servlet response. **Trong WebFlux**, đọc/ghi body bằng `HttpMessageReader`/`HttpMessageWriter` từ reactive codecs và request được xử lý qua reactive HTTP abstractions.

### 1.1. Những điểm chung đáng nhớ

Cả WebFlux và MVC đều hỗ trợ:
- `@Controller` và `@RestController` (`@Controller` + `@ResponseBody`);
- khai báo route bằng `@RequestMapping` và shortcut HTTP-method annotations;
- URI templates `/{id}`, regex path variables, `@PathVariable`;
- `@RequestParam`, `@RequestHeader`, `@CookieValue`;
- `@RequestBody` và `@ResponseBody`;
- `ResponseEntity`, `@ResponseStatus`;
- validation Bean Validation, `@ControllerAdvice`/`@ExceptionHandler`;
- `@CrossOrigin`/CORS, content negotiation và conversion;
- `@ModelAttribute` cho form/model binding (với khác biệt runtime).

Chúng chia sẻ nhiều annotations của **module spring-web**, khiến code điều khiển request nhìn rất giống nhau. **Đừng suy luận runtime chỉ từ tên annotation:** controller trả `Mono<T>` vẫn có thể đang chạy trên Servlet MVC nếu Boot chọn WebMvc stack.

### 1.2. Khác biệt cốt lõi

| Chủ đề | Spring MVC | Spring WebFlux |
| --- | --- | --- |
| Dispatcher | `DispatcherServlet` | `DispatcherHandler` |
| Mô hình web | Servlet API; có blocking I/O | Reactive Streams, non-blocking HTTP I/O khi runtime hỗ trợ |
| Server phổ biến | Tomcat/Jetty Servlet | Reactor Netty; cũng hỗ trợ adapter khác |
| Request abstraction | `HttpServletRequest` | `ServerWebExchange`/`ServerHttpRequest` |
| HTTP body đọc | `HttpMessageConverter` | `HttpMessageReader` reactive codecs |
| HTTP body ghi | `HttpMessageConverter`; Servlet output writes có thể blocking | `HttpMessageWriter` reactive |
| `@RequestBody Mono<T>` | Không phải cách đọc body reactive tương đương | **Được hỗ trợ** |
| Return `Mono/Flux` | Có hỗ trợ adapter và Servlet async | Native reactive pipeline |
| Blocking JDBC/JPA | Tương đối phù hợp với MVC thread model | Không nên block event-loop; cân nhắc R2DBC/boundedElastic |
| Functional API | `web.servlet.function` | `web.reactive.function.server` |

WebFlux không có nghĩa một phép toán Java bình thường tự trở thành async. `Flux.just(...)` và `Mono.just(...)` có thể phát ngay trên thread subscription; WebClient/reactive drivers mang async I/O thật. Hạ tầng sẽ không sửa một đoạn code `Thread.sleep(5000)` đặt trong controller.

## 2. @Controller và @RestController

### 2.1. @RestController dành cho API JSON/HTTP body

```java
@RestController
class HelloController {
    @GetMapping("/hello")
    Mono<String> hello() {
        return Mono.just("Hello WebFlux");
    }
}
```

Với `@RestController`, giá trị trả về được ghi vào response body; một `String` là nội dung, **không phải view name**. Kiểu `Mono<String>` được WebFlux subscribe rồi encode khi value phát.

### 2.2. @Controller dùng cho view rendering hoặc @ResponseBody

```java
@Controller
class PageController {
    @GetMapping("/welcome")
    String page(Model model) {
        model.addAttribute("title", "Welcome");
        return "welcome";
    }
}
```

Đây là snippet, cần view resolver và template `welcome`. Một `String` trả từ `@Controller` thông thường được hiểu là **tên view**, trừ khi method có `@ResponseBody`. Để trả JSON trên class `@Controller`:

```java
@ResponseBody
@GetMapping("/api/ping")
Mono<Map<String,String>> ping() {
    return Mono.just(Map.of("status", "ok"));
}
```

Không nhầm `@ResponseBody` với `@RequestBody`: cái đầu áp vào **response**, cái sau đọc **request**.

## 3. @RequestMapping trên class và method

`@RequestMapping` có thể khai báo:
- `path`/`value`;
- `method` (HTTP method);
- `params` (điều kiện query parameters);
- `headers`;
- `consumes` (request Content-Type);
- `produces` (response media type).

```java
@RestController
@RequestMapping("/api/books")
class BooksController {

    @GetMapping("/{id}")
    Mono<BookView> get(@PathVariable long id) {
        // ...
    }

    @PostMapping(consumes="application/json",
                 produces="application/json")
    Mono<BookView> create(@RequestBody Mono<CreateBook> body) {
        // ...
    }
}
```

Các mapping ở method kế thừa prefix `/api/books` từ type level:
- GET `/api/books/10`;
- POST `/api/books` với Content-Type JSON, có thể trả JSON khi client Accept phù hợp.

**Không dùng `@RequestMapping` trống `method` khi chỉ muốn GET**, vì annotation đó mặc định không giới hạn HTTP method. Với REST, ưu tiên `@GetMapping`/`@PostMapping`/... để giảm bề mặt không mong muốn.

### 3.1. Shortcut annotations

| Annotation | HTTP method | Ý nghĩa ứng dụng |
| --- | --- | --- |
| `@GetMapping` | GET | Đọc dữ liệu |
| `@PostMapping` | POST | Tạo/chạy hành động |
| `@PutMapping` | PUT | Thay thế representation |
| `@PatchMapping` | PATCH | Thay đổi một phần |
| `@DeleteMapping` | DELETE | Xóa |
| `@RequestMapping(method=RequestMethod.HEAD)` | HEAD | Nếu cần custom HEAD route |

Chúng là **composed annotations** dựa trên `@RequestMapping`. Tránh đặt **hai mapping annotations** trên cùng một element (ví dụ `@GetMapping` và `@PostMapping` cùng method) vì Spring chỉ lấy một mapping và có thể cảnh báo. Nếu muốn một method phục vụ nhiều verbs, khai báo một `@RequestMapping(method={RequestMethod.GET, RequestMethod.POST})` với lý do rõ ràng hoặc tách methods.

### 3.2. HTTP HEAD và OPTIONS

Theo Spring WebFlux reference:
- `@GetMapping` hỗ trợ HEAD tương ứng mà thường không cần định nghĩa method riêng; response xử lý `Content-Length` và **không gửi body**.
- OPTIONS mặc định có thể trả `Allow` thể hiện methods được ánh xạ trên URL, trừ khi ứng dụng có handling riêng.
- Với `@RequestMapping` không khai báo method, Allow có thể bao gồm nhiều HTTP verbs ngoài ý định; vì vậy nên định danh verb thật.

Đừng dùng response GET thông thường rồi khẳng định HEAD sẽ có cùng body; HEAD chỉ mang metadata. CORS preflight OPTIONS còn liên quan CORS configuration/security filter khác.

## 4. URI templates và path patterns

### 4.1. Đọc path variables

```java
@GetMapping("/{bookId}")
public Mono<BookView> get(
        @PathVariable("bookId") long bookId) {
    return repository.findById(bookId)
            .map(BookView::from);
}
```

Snippet yêu cầu reactive repository/DTO. Nếu URL `/api/books/42`, `bookId=42`. String được chuyển sang `long` thông qua type conversion; path `/api/books/abc` gây lỗi conversion, không tự trở thành `0`.

Khai báo tên cụ thể `@PathVariable("bookId")` hữu ích nếu project không compile với `-parameters` hoặc Java variable có tên khác trong URI template.

### 4.2. PathPatternParser của WebFlux

Spring WebFlux dùng `PathPattern`/`PathPatternParser`. Một số mẫu:

| Pattern | Match ví dụ | Không match / lưu ý |
| --- | --- | --- |
| `/books/{id}` | `/books/42` | `/books/42/reviews` |
| `/files/*.png` | `/files/a.png` | `/files/a/b.png` |
| `/books/{name:[a-z-]+}` | `/books/spring-web` | `/books/Spring123` |
| `/assets/**` | `/assets/a/b.png` | Catch-all nên ít cụ thể hơn |
| `/assets/{*path}` | `/assets/a/b.png`, capture phần đuôi | Catch-all đặt ở cuối pattern |

Không dùng `/**/info` hoặc catch-all ở **giữa** như một pattern hợp lệ theo PathPattern rules. Nếu nhiều mappings có thể match, Spring so độ cụ thể; static paths thường được ưu tiên hơn variable/catch-all tương ứng. Tránh những mappings mơ hồ có cùng độ ưu tiên khiến ứng dụng không thể chọn handler.

### 4.3. Không mặc định suffix matching

WebFlux không áp kiểu suffix path matching cổ như `/person` tự match `/person.json`. Dùng `Accept`, `produces` hoặc query param được công bố nếu cần representation khác. Không coi phần `.json` trong URL là content negotiation an toàn một cách mặc định.

### 4.4. Prefix ở reverse proxy

Nếu proxy publish `/portal/api/books` nhưng backend chỉ match `/api/books`, đó là vấn đề proxy route rewrite/path prefix, không phải `@GetMapping` tự biết external prefix. Cấu hình gateway và forwarded headers theo trust boundary; không tự lấy raw `X-Forwarded-Prefix` do client gửi rồi ghép URI. Test URL public thực tế và link/redirect khi có prefix.

## 5. Điều kiện params, headers, consumes và produces

### 5.1. params: điều kiện để chọn method, không phải argument value

```java
@GetMapping(path = "/search", params = "mode=featured")
Mono<List<BookView>> featured() {
    return service.featured().collectList();
}
```

Mapping trên chỉ được xét khi query có `mode=featured`. Tương tự:
- `params="q"`: phải có query param q;
- `params="!q"`: không có q;
- `params="mode=featured"`: giá trị đúng cụ thể.

Trong khi đó:

```java
@GetMapping("/search")
Mono<List<BookView>> search(
        @RequestParam(name = "q", required = false) String q) {
    return service.search(q).collectList();
}
```

`@RequestParam` ở tham số **đọc giá trị**; nếu `required=true`, thiếu tham số gây lỗi argument resolution. Hai khái niệm “mapping điều kiện params” và “binding @RequestParam” liên quan nhưng không đồng nhất. Nếu có hai mappings chồng lấn, Spring sẽ chọn mapping cụ thể theo bộ so sánh điều kiện, chứ không đơn giản “method khai báo trước thắng”.

### 5.2. headers

```java
@GetMapping(path="/internal-report",
            headers="X-Client-Type=partner")
Mono<ReportView> report() {
    return service.report();
}
```

Đây là snippet; yêu cầu header `X-Client-Type: partner` để route match. **Không phải bảo mật:** client có thể tự đặt header; không dùng nó thay authentication, role hoặc tenant authorization. Dùng Spring Security và object-level ACL.

### 5.3. consumes: request Content-Type

```java
@PostMapping(path="/api/books",
             consumes=MediaType.APPLICATION_JSON_VALUE)
Mono<Void> ingest(@RequestBody Mono<CreateBook> command) {
    return command.flatMap(service::save).then();
}
```

`consumes` hẹp theo Content-Type (ví dụ application/json). Gửi `text/plain` tới route chỉ chấp nhận JSON thường dẫn đến **415 Unsupported Media Type**, tùy các mappings/error handling khác. Đừng dùng `consumes` để kiểm tra response type.

### 5.4. produces: response Accept

```java
@GetMapping(path="/api/books",
            produces=MediaType.APPLICATION_JSON_VALUE)
Flux<BookView> books() {
    return service.findAll();
}
```

`produces` nói controller có thể tạo JSON; client có `Accept: application/json` hoặc `*/*` thường match. Nếu client chỉ chấp nhận `application/xml` trong khi API chỉ hỗ trợ JSON, HTTP có thể là **406 Not Acceptable**. `produces` ảnh hưởng mapping/negotiation, nhưng không tự cài thêm XML codec hay đảm bảo Java type serialize thành JSON.

### 5.5. Mối quan hệ consumes và produces

| Client header | Controller mapping | Mục đích |
| --- | --- | --- |
| `Content-Type: application/json` | `consumes="application/json"` | Đọc request JSON |
| `Accept: application/json` | `produces="application/json"` | Trả response JSON |
| `Content-Type: text/plain` | JSON-only consumes | Thường 415 |
| `Accept: application/xml` | JSON-only produces | Thường 406 |
| `X-Mode: admin` | `headers="X-Mode=admin"` | Chỉ là điều kiện routing, không phải quyền |

Trong `@RequestMapping`, `consumes`/`produces` ở method-level có thể **override**, không nhất thiết gộp như các mapping conditions khác từ class-level. Với `headers` và `params`, hiểu các kết hợp điều kiện theo quy tắc framework và test bằng HTTP requests thực để tránh nhầm precedence.

## 6. Đọc request input qua WebFlux annotated arguments

Các annotation đọc URI, query, header và cookie có **cú pháp quen thuộc với MVC**. Trong WebFlux, đối số `ServerWebExchange` và reactive body arguments thể hiện sự khác biệt rõ nhất:

| Nhu cầu | Phương thức WebFlux hợp lý | Phân biệt với MVC |
| --- | --- | --- |
| ID từ URI | `@PathVariable("id") long id` | Đọc route metadata, không cần bọc `Mono<Long>` |
| Query, header, cookie | `@RequestParam`, `@RequestHeader`, `@CookieValue` | WebFlux `@RequestParam` chỉ đọc **query**, không ngầm hợp nhất form/multipart như Servlet request parameters |
| Nhìn toàn HTTP exchange | `ServerWebExchange` / `ServerHttpRequest` | Không tiêm `HttpServletRequest` của MVC vào WebFlux annotated handler |
| JSON request body | `@RequestBody Mono<Command>` | Sử dụng `HttpMessageReader`/decoder theo publisher khi WebFlux thực sự active |
| Form/multipart | `exchange.getFormData()`, `@RequestPart`, `FilePart` | Đường parser riêng; không coi form/multipart là một JSON request body |
| Principal | `Mono<Principal>`, `exchange.getPrincipal()` | Identity phải do security chain xác thực, không tin `X-User` client gửi |

Ví dụ giữ cơ chế routing và body ở một method:

```java
@PostMapping(path = "/api/books", consumes = "application/json")
Mono<BookView> create(
        @RequestHeader(name = "X-Trace", required = false) String trace,
        @RequestBody Mono<CreateBook> body) {
    // Trace là metadata chưa đáng tin, không được dùng làm credential.
    return body.flatMap(service::create);
}
```

Đây là **snippet**, cần `service`, DTO và imports. Trong WebFlux, `@RequestBody Mono<CreateBook>` được framework decode từ publisher của HTTP body; nó không phải cách binding dùng chung với Servlet MVC. Ngược lại, `@PathVariable`/`@RequestParam` được resolve từ URI/query metadata, nên không bọc tùy tiện vào reactive publisher.

Bài chuyên về **`ServerWebExchange` và tham số** trong chương giải thích đầy đủ repeated query values, attributes, principal, session, form/multipart APIs và những lỗi binding. Bài này tập trung vào **mapping chọn đúng handler trước** khi từng argument được resolve.

## 7. Chọn reactive return type và HTTP response

Kiểu trả về cũng là một phần hợp đồng API. Trong annotated WebFlux, publisher biểu diễn **sự sẵn sàng của data**, nhưng status/headers có thể được xác định ở thời điểm khác:

| Kiểu trả về | Điều được quyết định khi nào? | Nên dùng |
| --- | --- | --- |
| `BookView` / `Mono<BookView>` | Thông thường body được encode khi có kết quả | 200 với một DTO |
| `Flux<BookView>` | 0..N giá trị, codec phụ thuộc media type | Collection hoặc reactive stream |
| `ResponseEntity<BookView>` | Status/headers/body đã biết ngay | 201, 204, headers rõ ràng |
| `Mono<ResponseEntity<BookView>>` | Status/headers được chọn **sau** async lookup | 200/404 theo dữ liệu |
| `ResponseEntity<Flux<BookView>>` | Status/headers sớm, stream body tiếp theo | SSE, NDJSON, chuỗi data |
| `Mono<Void>` | Hoàn tất/error mà không phát value | Operation không trả representation |

Hai kiểu dễ nhầm nhất:

```java
// 200 hoặc 404 chỉ được quyết định khi lookup phát kết quả.
Mono<ResponseEntity<BookView>> deferred = service.find(id)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());

// 202 được quyết định ngay; Mono chỉ đại diện body sẽ phát sau.
ResponseEntity<Mono<BookView>> immediate =
        ResponseEntity.accepted().body(service.find(id));
```

Đây là snippets dùng `service`/`id` đã có. `Mono.empty()` **không có nghĩa tự động trả 404** trong mọi contract; API muốn 404 thì map thành `ResponseEntity.notFound()` hoặc phát một `ResponseStatusException` thích hợp. `Flux` cũng **không bảo đảm** mỗi phần tử được browser nhận ngay: media type (JSON array/SSE/NDJSON), codec, server và proxy buffering quyết định tính streaming thực tế.

Ví dụ `201 + Location`:

```java
return service.create(command)
        .map(saved -> ResponseEntity
                .created(URI.create("/api/books/" + saved.id()))
                .body(saved));
```

Chỉ xây public URL tuyệt đối từ origin/proxy đã được tin cậy, không từ raw `Host` hay `X-Forwarded-Host`. Bài `@RequestBody với Mono/Flux và ResponseEntity` chuyên sâu hơn về JSON/NDJSON wire format, backpressure, cancellation, error operators và tính chất của từng tổ hợp publisher.

## 8. Validation và lỗi HTTP trong annotated WebFlux

Annotation route **không xác minh nội dung đầu vào**. `@Valid @RequestBody Mono<Dto>` có thể kích hoạt Bean Validation sau khi decoder tạo DTO nếu project có validation provider và constraints, ví dụ `@NotBlank`. Spring WebFlux 6.1+ phân biệt lỗi validation của một argument (thường `WebExchangeBindException`) với method validation (thường `HandlerMethodValidationException`). Khi không có validator/constraints, chỉ thêm `@Valid` không tạo policy.

```java
@PostMapping("/api/books")
Mono<ResponseEntity<BookView>> create(
        @Valid @RequestBody Mono<CreateBook> body) {
    return body.flatMap(service::create)
            .map(saved -> ResponseEntity
                    .created(URI.create("/api/books/" + saved.id()))
                    .body(saved));
}
```

Đây là method minh họa trong controller có `service`, DTO và imports. **Bản chạy được ở mục 11** chủ động dùng manual validation, nên không cần thêm validation starter; muốn áp đoạn `@Valid` trên phải cung cấp annotations/validation provider tương ứng.

Xử lý lỗi HTTP nên có **tầng sở hữu rõ ràng**:

- Request JSON malformed, sai Content-Type, conversion thất bại hoặc thiếu field: phân biệt lỗi decoder/binding trước business logic, thường là 400/415 phù hợp.
- Không tìm thấy entity: map `Mono.empty()` thành 404 theo API contract, không dùng 200 rỗng nếu caller cần nhận diện absence.
- Nghiệp vụ ném exception có ý nghĩa: dùng `Mono.error(...)` hoặc `ResponseStatusException`, và `@ExceptionHandler`/`@RestControllerAdvice` nếu cần error representation thống nhất.
- Filter, authentication, gateway và response stream đã commit có **error boundaries khác**; advice ở controller không tự đảm bảo bắt tất cả.
- Không đưa exception message chứa secret, raw user input, stacktrace hoặc internal path vào JSON lỗi.

Bài về **binding/Bean Validation** phân tích các loại lỗi đầu vào và groups/custom validators; bài về **ProblemDetail/@RestControllerAdvice** phân tích RFC 9457, phạm vi error handling và status sau khi response commit. Ở bài routing này, chỉ cần nhớ mapping, decoding, validation và business failure là **những pha khác nhau**.

## 9. Quy tắc chọn handler khi có nhiều mappings

Các mapping conditions cùng tác động:
- URL path matching;
- HTTP verb;
- query params;
- headers;
- consumes/request Content-Type;
- produces/Accept.

Cùng một path có thể có GET và POST riêng. Hai GET cùng `/books` có thể phân biệt bằng `params="view=full"` hoặc `produces`; tuy nhiên ambiguous overlap vẫn có thể xảy ra nếu conditions không đủ tách biệt. Spring dùng một comparator có quy tắc specificity, **không dựa vào thứ tự methods trong source code**.

### 9.1. Static path vs variable path

```java
@GetMapping("/api/books/featured")
Mono<List<BookView>> featured() {
    return service.featured().collectList();
}

@GetMapping("/api/books/{id}")
Mono<BookView> byId(@PathVariable long id) {
    return service.find(id);
}
```

Nếu GET `/api/books/featured`, static path thường cụ thể hơn variable path. Dù thế, không nên để endpoint variable mong `id` là số nhưng không có regex khi dùng nhiều aliases; có thể ràng buộc:

```java
@GetMapping("/api/books/{id:\\d+}")
Mono<BookView> byNumericId(@PathVariable long id) {
    return service.find(id);
}
```

Trong Java string phải escape backslash thành `\\d+` để pattern nhận `\d+`. Ví dụ này là snippet; nếu copy trong IDE, đảm bảo literal đúng dấu escape.

### 9.2. Ambiguous mapping

Nếu hai methods cùng `@GetMapping("/api/books")` và không có conditions phân biệt, có thể gây startup failure hoặc lỗi chọn handler tùy thời điểm/framework config. Cách sửa không phải dựa vào tên class/file, mà xác định route contract cụ thể: tách path, method, params/headers/media type hoặc xóa duplicate.

### 9.3. @RequestMapping trên interface

Spring hỗ trợ annotation trên controller interface trong nhiều trường hợp, nhưng proxying (JDK vs class-based), mix annotation ở implementation/interface và cách detect handler có lưu ý. Nếu định nghĩa API contract ở Java interface, đặt annotation mapping/parameter annotations nhất quán và kiểm thử handler detection, không tùy tiện copy cùng annotation chồng lên implementation.

### 9.4. API versioning

Spring Framework 7 cung cấp attribute `version` trên request mappings và khả năng cấu hình API version resolver cho WebFlux. **Không dùng API mới này trong Spring Framework 6.2 / Boot 3.5**, vì mã không compile. Với stack 6.2, version routes thường là `/api/v1/books`, header conditions, media types hoặc custom strategy. Nếu nâng Spring 7, đọc lại API version strategy/version deprecation docs tương ứng thay vì chỉ thêm `version="1.0"` vào project cũ.

## 10. Những ranh giới quan trọng giữa WebFlux và MVC

### 10.1. Thêm WebFlux starter không chắc đã kích hoạt WebFlux server

Spring Boot 3.5 docs nêu: nếu project có cả `spring-boot-starter-web` và `spring-boot-starter-webflux`, Boot **ưu tiên auto-configure Spring MVC** vì nhiều người dùng WebClient trong app MVC. Để demo WebFlux đúng nghĩa, chỉ dùng:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Nếu dự án cố tình cần reactive web type dù có cả starters, có thể cấu hình `SpringApplication.setWebApplicationType(WebApplicationType.REACTIVE)`, nhưng không coi đó là giải pháp thay việc phân tích dependencies/config. Cần xác nhận DispatcherHandler/WebFlux codecs thực sự được sử dụng.

### 10.2. Không chạy blocking operations trên event-loop

```java
// Anti-pattern trong WebFlux controller.
@GetMapping("/blocking")
Mono<BookView> bad() {
    return Mono.just(jdbcRepository.findBook(42));
}
```

Lời gọi `jdbcRepository.findBook(42)` chạy **trước** khi Mono được tạo, có thể block request event-loop. Tương tự, `Mono.fromCallable(...)` không tự chuyển threads. Bridge có giới hạn:

```java
Mono<BookView> safer = Mono
        .fromCallable(() -> jdbcRepository.findBook(42))
        .subscribeOn(Schedulers.boundedElastic());
```

Đây chỉ là adapter cho blocking code, không làm JDBC non-blocking. Với ứng dụng chủ yếu JPA/JDBC, Spring MVC có thể đơn giản và phù hợp hơn. Với WebFlux high concurrency, ưu tiên reactive HTTP client/driver, cân nhắc boundedElastic quotas và overload protection.

### 10.3. Servlet filters và WebFilter

Trong MVC, authentication/tracing có thể dùng `jakarta.servlet.Filter` và `OncePerRequestFilter`. Trong WebFlux, dùng `org.springframework.web.server.WebFilter` và Spring Security **reactive SecurityWebFilterChain** theo stack. Không sao chép một Servlet Filter rồi kỳ vọng tự chạy trong Netty WebFlux app.

### 10.4. ThreadLocal không tự truyền qua reactive boundaries

`SecurityContextHolder`, MDC, LocaleContextHolder và request-scoped ThreadLocal của MVC không tự bảo đảm hiện diện trên các Reactor schedulers. WebFlux thường dùng Reactor Context/context propagation và Spring Security reactive context; dữ liệu principal/tenant phải được truy cập theo API phù hợp. Không cho phép client tự chọn tenant ID từ header khi thiếu authorization.

### 10.5. Data binding khác body JSON decoding

`@ModelAttribute` bind request parameters/URI variables trong model; `@RequestBody` deserialize HTTP body bằng codec. Dùng DTO rõ ràng và chỉ chấp nhận fields thuộc contract; Spring Boot/WebFlux có khả năng constructor binding và validator, nhưng **không nên bind JPA entity trực tiếp** chứa `role`, `ownerId`, `price` do server quản lý.

## 11. Demo đầy đủ: Spring Boot 3.5 / Java 17 / WebFlux

Tạo REST API sách bằng annotation WebFlux, với:
- GET `/api/books`: trả Flux JSON;
- GET `/api/books/{id}`: trả Mono<ResponseEntity<BookView>>, chọn 200/404;
- GET `/api/books/search?mode=featured`: mapping có `params`;
- POST `/api/books`: nhận `@RequestBody Mono<CreateBook>`, validate và trả 201 + Location;
- PUT `/api/books/{id}`: thay title, trả 200/404;
- DELETE `/api/books/{id}`: trả 204/404.

Store là ConcurrentHashMap in-memory, đủ minh họa route/binding/response; không phải giải pháp persistence/auth production.

### 11.1. Project dependency

Từ [Spring Initializr](https://start.spring.io/), tạo Maven project **Spring Boot 3.5.x**, **Java 17+**, dependency **Spring Reactive Web**. Trong `pom.xml` do Initializr sinh có dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

Đây là **POM fragment**, Initializr cung cấp parent/BOM/plugins đầy đủ. Reactor Core/Jackson/WebFlux/Reactor Netty được kéo vào theo starter. Không cần thêm `spring-boot-starter-web`.

### 11.2. File DemoApplication.java độc lập

Lưu **toàn bộ** đoạn sau vào `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record BookView(long id, String title) {}
record CreateBook(String title) {}

@Service
class BookStore {

    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentMap<Long, BookView> books =
            new ConcurrentHashMap<>();

    Flux<BookView> list(int limit) {
        return Flux.defer(() -> Flux.fromIterable(
                books.values().stream()
                        .sorted(Comparator.comparingLong(BookView::id))
                        .limit(limit)
                        .toList()));
    }

    Mono<BookView> find(long id) {
        return Mono.defer(() -> Mono.justOrEmpty(books.get(id)));
    }

    Mono<BookView> create(CreateBook command) {
        return Mono.fromSupplier(() -> {
            long id = nextId.incrementAndGet();
            BookView saved = new BookView(
                    id, command.title().trim());
            books.put(id, saved);
            return saved;
        });
    }

    Mono<BookView> replace(long id, CreateBook command) {
        return Mono.defer(() -> Mono.justOrEmpty(
                books.computeIfPresent(
                        id,
                        (key, previous) ->
                                new BookView(id, command.title().trim()))));
    }

    Mono<Boolean> delete(long id) {
        return Mono.fromSupplier(() -> books.remove(id) != null);
    }
}

@RestController
@RequestMapping(path = "/api/books")
class BookController {

    private final BookStore store;

    BookController(BookStore store) {
        this.store = store;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<BookView> list(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        if (limit < 1 || limit > 100) {
            return Flux.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be 1..100"));
        }

        return store.list(limit);
    }

    @GetMapping(path = "/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BookView>> get(
            @PathVariable("id") long id) {

        return store.find(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping(path = "/search", params = "mode=featured",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<BookView> featured() {
        // Query param mode=featured là điều kiện chọn mapping.
        return store.list(5);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BookView>> create(
            @RequestBody Mono<CreateBook> body) {

        return body
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "JSON body required")))
                .flatMap(command -> {
                    validate(command);
                    return store.create(command);
                })
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/books/" + saved.id()))
                        .body(saved));
    }

    @PutMapping(path = "/{id}",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<BookView>> replace(
            @PathVariable("id") long id,
            @RequestBody Mono<CreateBook> body) {

        return body
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "JSON body required")))
                .flatMap(command -> {
                    validate(command);
                    return store.replace(id, command);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable("id") long id) {
        return store.delete(id)
                .map(deleted -> deleted
                        ? ResponseEntity.<Void>noContent().build()
                        : ResponseEntity.<Void>notFound().build());
    }

    private static void validate(CreateBook command) {
        if (command == null
                || command.title() == null
                || command.title().isBlank()
                || command.title().length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "title is required (1..120 characters)");
        }
    }
}
```

### 11.3. Giải thích tính chạy được và giới hạn

- Một Java file có đúng một public class `DemoApplication`; các `record` và component classes còn lại là package-private, hợp lệ với Java 17.
- `@SpringBootApplication` component-scan thấy `BookStore`/`BookController` vì cùng package.
- Project dùng `spring-boot-starter-webflux` và không có MVC starter nên mặc định chạy WebFlux server (thường Reactor Netty).
- `BookStore` dùng `ConcurrentHashMap` và `AtomicLong`; đó là in-memory synchronization phục vụ demo, **không phải reactive database**. Mọi operation ở đây rất nhỏ, không block I/O; `Mono.defer/Mono.fromSupplier` chỉ trì hoãn công việc đến subscription, **không tự làm IO non-blocking**.
- `GET /api/books` sử dụng `Flux.defer` tạo snapshot list tại subscription, sắp xếp theo ID; database production nên dùng reactive repository/R2DBC với pagination.
- `@RequestBody Mono<CreateBook>` minh họa API đặc thù của WebFlux. Chỉ có JSON DTO tối thiểu; không bind trực tiếp entity có fields nhạy cảm.
- `Mono<ResponseEntity<BookView>>` cho phép quyết định 200 hoặc 404 dựa trên async result.
- `POST` dùng `ResponseEntity.created(...)` tạo status 201 + Location tương đối.
- `PUT` thay một title trên một record in-memory; đó là ví dụ **replace một representation đơn giản**, không có ETag/If-Match. Nếu nhiều clients cùng sửa, cần optimistic versioning ở DB để tránh lost update.
- Không có authentication, CSRF, CORS, database, transaction hoặc metrics; **không deploy demo như production API công khai**.
- Tất cả methods đều dùng explicit HTTP annotations; `@RequestMapping` class-level chỉ làm prefix.

### 11.4. Chạy server

Trong root của project do Initializr tạo:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows (PowerShell/CMD)
mvnw.cmd spring-boot:run
```

Nếu không đổi port, server nghe ở `http://localhost:8080`.

### 11.5. GET danh sách rỗng

```bash
curl -i -H 'Accept: application/json' \
  'http://localhost:8080/api/books?limit=10'
```

Dự kiến status 200, JSON body:

```json
[]
```

List rỗng là một response hợp lệ; không cần 404. Header Content-Type JSON được xác định qua `produces` và codec.

### 11.6. POST tạo sách

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data-binary '{"title":"Spring WebFlux"}'
```

Dự kiến sau khởi động mới, chưa có create khác:

```http
HTTP/1.1 201 Created
Location: /api/books/1
Content-Type: application/json

{"id":1,"title":"Spring WebFlux"}
```

Các header như Date/Transfer-Encoding và JSON whitespace có thể khác theo runtime. Nếu đã tạo sách khác, ID không nhất thiết 1. HTTP Location nên đọc từ header thật.

### 11.7. GET theo ID, 404

```bash
curl -i 'http://localhost:8080/api/books/1'
curl -i 'http://localhost:8080/api/books/9999'
```

Với ID 1 tồn tại, response 200 JSON `{"id":1,"title":"Spring WebFlux"}`. Với ID 9999 chưa có, status **404** vì `defaultIfEmpty(ResponseEntity.notFound().build())`.

Đây là điểm khác với việc return `Mono.empty()` đơn giản: API đã chỉ rõ status 404.

### 11.8. GET search theo params condition

```bash
curl -i 'http://localhost:8080/api/books/search?mode=featured'
```

Route `/search` có điều kiện query `mode=featured` và static path cụ thể. Handler trả tối đa 5 bản ghi. Nếu không có `mode=featured`, mapping đó không thỏa; status thực tế tùy các mappings còn lại và error handling, không giả định response từ `featured()` sẽ được gọi.

### 11.9. PUT thay nội dung

```bash
curl -i -X PUT 'http://localhost:8080/api/books/1' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data-binary '{"title":"WebFlux Updated"}'
```

Nếu ID tồn tại: status **200**, body JSON title mới. Nếu ID không tồn tại: status **404**. Nếu title blank hoặc hơn 120 ký tự: **400** do manual validation.

**Về nghiệp vụ:** ví dụ không có If-Match/ETag nên có thể lost update trong concurrent edits. Với project thật, bổ sung version/CAS/optimistic lock thay vì dựa vào reactive return type để ngăn race.

### 11.10. DELETE

```bash
curl -i -X DELETE 'http://localhost:8080/api/books/1'
```

Lần đầu nếu ID 1 tồn tại: **204 No Content**. Gửi lại cùng ID sau khi đã xóa: **404 Not Found** theo contract demo. Đừng kỳ vọng body JSON trên 204.

### 11.11. Các trường hợp lỗi

**Sai limit:**

```bash
curl -i 'http://localhost:8080/api/books?limit=0'
```

Dự kiến status 400 (range check). Với `limit=xyz`, argument conversion có thể trả 400 trước khi vào method.

**JSON thiếu title:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data-binary '{}'
```

Dự kiến 400. Validation manual trong `validate` không phụ thuộc bean-validation starter.

**Content-Type sai:**

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: text/plain' \
  --data-binary 'hello'
```

Vì `@PostMapping(consumes="application/json")`, response thường là **415** nếu không có mapping khác.

**Accept không hỗ trợ:**

```bash
curl -i -H 'Accept: application/xml' \
  'http://localhost:8080/api/books'
```

Mapping `produces="application/json"` không chấp nhận chỉ XML, nên thường **406 Not Acceptable**, nếu không có route XML khác.

### 11.12. Windows PowerShell và curl

PowerShell có thể alias `curl` thành cmdlet riêng. Dùng `curl.exe`:

```powershell
curl.exe -i "http://localhost:8080/api/books?limit=10"
curl.exe -i "http://localhost:8080/api/books/9999"
```

Với request JSON, tạo file `request.json` UTF-8:

```json
{"title":"Spring WebFlux"}
```

Gửi:

```powershell
curl.exe -i -X POST "http://localhost:8080/api/books" -H "Content-Type: application/json" -H "Accept: application/json" --data-binary "@request.json"
```

Tránh lỗi escape dấu quote native process args khác nhau giữa các phiên bản PowerShell.

## 12. Kiểm thử WebFlux bằng WebTestClient

### 12.1. Dependencies

Thêm test starter cho Spring Boot **3.5**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Đây là fragment POM. Với code Boot 4 phải xem modular test starter và package imports mới; không trộn tùy tiện imports Boot 3 vào Boot 4.

### 12.2. WebTestClient integration test

Lưu `src/test/java/com/example/demo/BookControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class BookControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void canCreateAndRead() {
        String location = client.post()
                .uri("/api/books")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"WebFlux Test\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.title").isEqualTo("WebFlux Test")
                .returnResult()
                .getResponseHeaders()
                .getLocation()
                .toString();

        client.get().uri(location)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("WebFlux Test");
    }

    @Test
    void missingBookReturns404() {
        client.get().uri("/api/books/999999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void badTitleReturns400() {
        client.post().uri("/api/books")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"   \"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void wrongContentTypeIsRejected() {
        client.post().uri("/api/books")
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .bodyValue("hello")
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    void wrongAcceptIsRejected() {
        client.get().uri("/api/books")
                .accept(org.springframework.http.MediaType.APPLICATION_XML)
                .exchange()
                .expectStatus().isEqualTo(406);
    }

    @Test
    void badLimitReturns400() {
        client.get().uri("/api/books?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
```

**Lưu ý quan trọng về test này:** API `EntityExchangeResult<byte[]> result = ...expectBody().returnResult()` có method `getResponseHeaders()` để đọc Location. `getLocation()` trả URI khi header tồn tại; nếu test không có Location, assertion trước sẽ báo. Các tests không giả định ID=1 vì store in-memory có thể đã có các test POST khác cùng context.

`WebTestClient` có thể hoạt động với server thật như test RANDOM_PORT hoặc bind-to-controller/router; trong ví dụ dùng RANDOM_PORT để kiểm tra network/server/reactive codecs đầy đủ.

### 12.3. Điều cần kiểm tra thêm

- `GET /api/books/search?mode=featured` match đúng và không bị variable path nuốt.
- Duplicate/conflicting mappings không xảy ra.
- HEAD/OPTIONS behaviors theo WebFlux contract.
- JSON malformed và request quá lớn không được trả 200.
- Params/header conditions: có/không có/wrong value.
- Query/path conversion errors và error body.
- Validation Bean Validation (nếu thêm `@Valid`/constraints).
- Authentication/authorization/CORS/CSRF khi bảo vệ endpoints.
- SSE/NDJSON theo `produces` và response streaming/cancellation.
- Concurrency writes và optimistic versioning trong DB, không chỉ kiểm tra in-memory map.

### 12.4. Chạy tests

```bash
# Linux/macOS
./mvnw test

# Windows
mvnw.cmd test
```

Nếu CI có tests chạy song song và dùng chung in-memory state, đừng assert ID cứng hoặc xóa tài nguyên thuộc test khác. Dùng test fixture/isolated context hoặc test database rollback/cleanup phù hợp.

## 13. Lỗi thường gặp khi chuyển controller MVC sang WebFlux

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Controller trông giống WebFlux nhưng đang chạy MVC | Project có cả web và webflux starters | Kiểm tra web application type, chọn WebFlux server rõ |
| `@RequestBody Mono<T>` không hoạt động | Servlet MVC đang handle request | Dùng WebFlux đúng stack hoặc DTO đồng bộ cho MVC |
| `HttpServletRequest` không resolve | WebFlux không dùng Servlet API làm abstraction chung | Dùng `ServerWebExchange`/`ServerHttpRequest` |
| `@GetMapping` không match | Class path + method path sai, version, path pattern | Test URI thực, method và conditions |
| GET/POST bị match sai | Dùng bare `@RequestMapping` quá rộng | Shortcut mapping theo verb |
| Path regex trong Java sai escape | `\d+` chưa escape trong string literal | Dùng `\\d+` trong Java |
| `GET /books/featured` vào `/{id}` | Overlapping paths/conversion | Static path/regex route, test specificity |
| Sai Content-Type với POST | `consumes` không thỏa | Gửi JSON hoặc khai báo đúng media |
| `Accept: xml` trả 406 | `produces` chỉ JSON | Thống nhất client Accept/API representation |
| Mono.empty trả 200 rỗng | Không map absence thành HTTP 404 | ResponseEntity.notFound/switchIfEmpty |
| Có `Flux<T>` nhưng output không như kỳ vọng | Codecs/media type/stream flush | Test JSON/SSE/NDJSON đúng media |
| `@Valid` không phát huy | Thiếu validation provider/constraints | Khai báo constraints/validation starter và test |
| @ControllerAdvice không bắt đúng lỗi | Copy loại exception của MVC | WebExchangeBindException/HandlerMethodValidationException |
| WebFlux response bị nghẽn | Dùng JDBC/JPA/Thread.sleep/block() trên event loop | Reactive I/O hoặc boundedElastic có giới hạn |
| CORS preflight bị chặn | Không có CORS/security cấu hình đúng | @CrossOrigin/Global CORS/SecurityWebFilterChain |
| Controller trả sai mã 201/404/204 | Chỉ return body value | Dùng ResponseEntity/ResponseStatusException |
| Mapping duplicate startup fail | Methods cùng path/verb/conditions | Tách routes/conditions, test |
| Controller tạo Location domain giả | Tin Host/X-Forwarded-Host thô | Canonical public base URL + trusted proxy |
| Header điều kiện bị xem như authorization | Client tự gửi được custom headers | Spring Security + ACL |
| Memory quá lớn với Flux | collectList() trên nguồn lớn/infinite | Pagination/bounded streaming |
| HTTP error sau SSE committed | Không thể đổi response status đã gửi | Event protocol/close/reconnect |

## 14. CORS, Security và trách nhiệm API

Annotation mapping **chỉ quyết định route**, không xác thực người gọi. Cần:
1. Spring Security reactive `SecurityWebFilterChain` để kiểm soát authentication và endpoint authorization.
2. Kiểm tra object ownership/tenant/ACL trong service; `@PathVariable id` không tự đảm bảo được phép truy cập.
3. CSRF phù hợp khi browser dùng cookie/session credentials cho state-changing POST/PUT/DELETE.
4. CORS allowlist origin/headers/methods; `@CrossOrigin` không thay authentication.
5. DTO input allowlist, validation và chống mass assignment; không cho client tự đặt `role`, `ownerId` hoặc `price` server-managed.
6. Payload size/codec max in-memory, file upload limits, quota, rate limiting và request timeout.
7. Fail closed khi permission service lỗi; đừng fallback quyền mở bằng `onErrorReturn(true)`.
8. Chống open redirect/Host-header poisoning khi tạo absolute link trong Location.
9. Error responses không tiết lộ stacktrace/secrets/SQL/tenant data.
10. Logging/tracing reactive context propagation; không để token/PII trong URL/log.

### 14.1. Giới hạn codec buffer

WebFlux có max-in-memory-size cho codecs và Spring Boot hỗ trợ cấu hình, ví dụ trong Boot 3.5:

```properties
spring.codec.max-in-memory-size=256KB
```

Đây là một **codec buffering limit**, không phải hard cap cho mọi HTTP bytes, request streaming, file upload hoặc toàn hệ thống. Với large request bodies phải có chính sách bổ sung tại gateway/server/decoder và không tự gọi `collectList()` trên stream không giới hạn.

### 14.2. @CrossOrigin

```java
@CrossOrigin(origins="https://frontend.example.test")
@GetMapping("/api/public")
Mono<String> publicEndpoint() {
    return Mono.just("ok");
}
```

Đây là snippet minh họa allowlist origin. Origin này là host ví dụ, không phải domain thật. Nếu có Spring Security, CORS integration/phương thức OPTIONS phải được cấu hình nhất quán. Không phản chiếu tùy tiện mọi Origin kèm credentials.

## 15. Checklist QA trước khi phát hành

- [ ] Project thực sự chạy WebFlux reactive server, không vô ý auto-config MVC do hai starters.
- [ ] Có class-level prefix và method-level verbs đúng; không dùng `@RequestMapping` không giới hạn method ngoài ý định.
- [ ] Các path variables/regex và catch-all patterns dùng PathPatternParser hợp lệ.
- [ ] Params, headers, consumes, produces được xác định và test.
- [ ] `Content-Type` nói về request; `Accept` nói về response.
- [ ] `@RequestBody Mono<Dto>` được decode bằng WebFlux codecs; DTO không chứa field nhạy cảm.
- [ ] `@Valid`/manual validation và HTTP lỗi 400/404/406/415 theo contract.
- [ ] Return `Mono<ResponseEntity<T>>` khi status/headers phụ thuộc kết quả async.
- [ ] Có thể stream Flux/SSE/NDJSON đúng media type; cancellation/errors sau commit được xử lý.
- [ ] Không đặt blocking JDBC/Thread.sleep/Mono.block trong event loop.
- [ ] SecurityWebFilterChain/CORS/CSRF/ACL/rate limits phù hợp cả GET/POST/PUT/DELETE.
- [ ] Không ghép Host/Forwarded headers thô thành Location/callback URLs.
- [ ] Test bằng WebTestClient với JSON, route specificity, error cases và wrong media.
- [ ] Nếu cần API version mapping, phân biệt Spring Framework 7 với 6.2.
- [ ] Metrics/logs theo dõi latency, codec buffer memory, 4xx/5xx, active requests, client disconnect.
- [ ] Data race/update đồng thời giải quyết trong repository/transaction, không dựa vào Reactor return types.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `@RestController` trong MVC và WebFlux có cùng ý nghĩa cơ bản không?

**Đáp án:** Có. Nó tương đương `@Controller` + `@ResponseBody`; nhưng bên dưới dùng hạ tầng request/response khác.

**Câu 2.** `@RequestMapping("/api/books")` không chỉ định HTTP method trên method handler có thể nguy hiểm chỗ nào?

**Đáp án:** Nó có thể match nhiều HTTP methods thay vì chỉ GET; nên dùng `@GetMapping`/`@PostMapping`/... khi muốn route hẹp.

**Câu 3.** Nếu client gửi JSON tới POST, annotation nào kiểm tra kiểu input, annotation nào thể hiện kiểu response?

**Đáp án:** `consumes="application/json"` kiểm tra request Content-Type, `produces="application/json"` cho response Accept/representation.

**Câu 4.** Khi nào dùng `@RequestBody Mono<CreateBook>`?

**Đáp án:** Khi chạy WebFlux reactive stack và muốn decode request body bất đồng bộ; không coi đó là signature tương đương được MVC hỗ trợ.

**Câu 5.** `Mono.empty()` có phải luôn trả 404 không?

**Đáp án:** Không. Dùng `switchIfEmpty(Mono.error(new ResponseStatusException(404)))` hoặc `Mono<ResponseEntity<T>>` với notFound.

**Câu 6.** WebFlux và MVC cùng hỗ trợ `@GetMapping`, vậy WebFlux có dùng `DispatcherServlet` không?

**Đáp án:** Không. WebFlux dùng `DispatcherHandler` trên reactive stack.

**Câu 7.** Có cả `spring-boot-starter-web` và `spring-boot-starter-webflux` trong Boot 3.5 thì mặc định nghiêng về framework nào?

**Đáp án:** Spring MVC, trừ khi chủ động ép reactive web application type.

**Câu 8.** `@RequestHeader("X-Role") String role` có chứng minh user là admin không?

**Đáp án:** Không. Header do client tự gửi; phải xác thực/ủy quyền bằng security framework và quyền nguồn dữ liệu.

**Câu 9.** `@Valid` với WebFlux có thể dẫn đến loại exception nào?

**Đáp án:** `WebExchangeBindException` cho argument validation, `HandlerMethodValidationException` khi method validation áp dụng; kiểm tra theo signature và Spring version.

**Câu 10.** Làm sao xác minh annotation route hoạt động đúng?

**Đáp án:** Dùng WebTestClient integration tests cho path, method, params, headers, Content-Type, Accept, đúng status/body và negative cases.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller.html): mô hình @Controller/@RestController, method signatures.
- [Spring Framework — Mapping Requests (WebFlux)](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-requestmapping.html): class/method mappings, URI patterns, params/headers/consumes/produces, HEAD/OPTIONS.
- [Spring Framework 6.2 — Method Arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/arguments.html): @PathVariable, @RequestParam, @RequestHeader, ServerWebExchange và reactive arguments.
- [Spring Framework 6.2 — Request Body](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestbody.html): @RequestBody Mono/Flux, HttpMessageReader, validation.
- [Spring Framework 6.2 — Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html): Publisher, ResponseEntity, HTTP response writing.
- [Spring Framework 6.2 — Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-validation.html): @Valid, WebExchangeBindException, HandlerMethodValidationException.
- [Spring Framework — WebFlux Overview](https://docs.spring.io/spring-framework/reference/web/webflux.html): nonblocking reactive architecture và MVC comparison.
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html): dependency, starter choice, WebFlux config/codecs.
- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): @CrossOrigin, global CORS.
- [Spring Security — WebFlux Security](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html): SecurityWebFilterChain và reactive authentication/authorization.
- [Spring Framework — Spring MVC Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html): nguồn để đối chiếu annotations dùng chung và Servlet differences.

**Kết luận:** học controller WebFlux từ nền tảng MVC là hợp lý vì cú pháp `@RestController`/`@RequestMapping`/`@GetMapping`/`@RequestBody` rất tương đồng. Nhưng để xây ứng dụng reactive đúng, cần nhận biết `DispatcherHandler`, `ServerWebExchange`, `HttpMessageReader/Writer`, `@RequestBody Mono<T>` và native `Mono/Flux` return semantics; tuyệt đối không block event-loop, không bỏ security/validation, và phải kiểm thử route matching lẫn content negotiation bằng HTTP thực. **Giống annotation không có nghĩa giống runtime I/O**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-002">Đọc tham số, header, cookie, Principal và dữ liệu trong ServerWebExchange</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc tham số, header, cookie, Principal và dữ liệu trong ServerWebExchange

## 1. Mục tiêu và phạm vi

Một HTTP request gửi tới Spring WebFlux có nhiều nguồn dữ liệu: **query parameters** trong URI, **path variables**, **headers**, **cookies**, **request body** (JSON, form hoặc multipart), **principal** đã xác thực và **exchange attributes** do server tạo. Chúng không đồng nghĩa với nhau, có vòng đời và mức độ tin cậy khác nhau. Controller nên dùng argument resolvers của WebFlux khi chỉ cần một giá trị; khi cần cái nhìn tổng thể thì dùng `ServerWebExchange`, đồng thời giữ nguyên mô hình bất đồng bộ.

Bài học thuộc **Controller WebFlux** của Spring Framework, dùng **Java 17+, Spring Framework 6.x, Spring Boot 3.x** (reactive stack, thông thường Reactor Netty). Kết thúc bài, bạn có thể:

- Dùng `@RequestParam` với `required`/`defaultValue`/`Optional`, chuyển đổi kiểu và multi-value query.
- Phân biệt *thiếu parameter*, *parameter rỗng*, *giá trị không chuyển được kiểu*, *parameter xuất hiện nhiều lần*.
- Đọc `@RequestHeader`, `HttpHeaders`, `@CookieValue`; tránh tin `X-User` hay token được gửi từ client như identity.
- Lấy `java.security.Principal` hoặc `Mono<Principal>`, xử lý anonymous một cách rõ ràng và hiểu mối liên hệ với Spring Security.
- Sử dụng `ServerWebExchange.getRequest()`, `getResponse()`, `getAttributes()`, `getPrincipal()`, `getSession()`, `getFormData()`, `getMultipartData()`, `getLocaleContext()` và những giới hạn liên quan.
- Phân biệt form-urlencoded, multipart, JSON và query string trong WebFlux, không cố coi tất cả thành `@RequestParam`.
- Thực hành một ứng dụng Spring Boot WebFlux có `WebFilter` tạo correlation ID, controller, HTTP examples và `WebTestClient`.
- Tránh đọc body hai lần, chặn Netty event loop bằng `block()`, ghi log bí mật hoặc bỏ qua validation/authorization.

**Điểm phải nhớ:** Trên **Spring WebFlux**, `@RequestParam` **chỉ** bind **query parameters**. Ngược lại, khái niệm “request parameter” của Servlet/Spring MVC còn có thể gộp form-data và một số multipart values. Với WebFlux, form và multipart có API riêng qua `ServerWebExchange` hoặc data binding phù hợp. Đây là khác biệt được Spring Framework ghi rõ trong tài liệu chính thức.

## 2. Bản đồ dữ liệu trong một request

Ví dụ:

```http
POST /api/input/notes/42?tag=java&tag=webflux&page=2 HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
X-Client: mobile-app
Cookie: theme=dark; session=opaque

{"message":"Xin chào"}
```

Bóc tách:

| Vị trí | Ví dụ | Cách đọc WebFlux |
| --- | --- | --- |
| Path variable | `42` | `@PathVariable("id")` |
| Query param | `tag=java&tag=webflux` | `@RequestParam` hoặc `exchange.getRequest().getQueryParams()` |
| Header | `X-Client: mobile-app` | `@RequestHeader` hoặc `getRequest().getHeaders()` |
| Cookie | `theme=dark` | `@CookieValue` hoặc `getRequest().getCookies()` |
| JSON body | `{"message":"Xin chào"}` | `@RequestBody Mono<NoteInput>` |
| Principal | Không phải thông tin client tự khai | `Principal`, `Mono<Principal>` hoặc `exchange.getPrincipal()` |
| Exchange attribute | `app.traceId` do filter ghi | `@RequestAttribute` hoặc `exchange.getAttribute(...)` |
| Web session | Server-managed session state | `exchange.getSession()` hoặc `WebSession` |
| URL-encoded form body | `name=An&topic=java` | `exchange.getFormData()` |
| Multipart | Field/file parts | `exchange.getMultipartData()`, `@RequestPart` |

*Request headers/cookies/query/body nhìn chung là dữ liệu từ client và cần kiểm tra.* Ngay cả khi đã URL decode hoặc tự động convert thành kiểu Java, chúng không tự được “làm sạch” hoặc chứng thực. Principal cũng chỉ đáng tin khi authentication/security chain thực sự đã được cấu hình đúng.

## 3. Nên dùng annotation hay ServerWebExchange?

Thông thường chọn phương án nhỏ nhất:

```java
@GetMapping("/books")
public Mono<Book> one(
        @RequestParam("id") long id,
        @RequestHeader("Accept-Language") String language) {
    return repository.find(id, language);
}
```

Đây là **snippet** cần bean `repository` và record `Book`; không phải file Java hoàn chỉnh. Controller tự nhận đúng hai giá trị, không phải thủ công đọc request raw.

Sử dụng `ServerWebExchange` khi cần nhiều thành phần cùng lúc hoặc chức năng hạ tầng, ví dụ xem repeated query values, lấy request attributes từ `WebFilter`, form/multipart map, session, headers hay principal.

```java
@GetMapping("/inspect")
public Mono<String> inspect(ServerWebExchange exchange) {
    String method = exchange.getRequest().getMethod().name();
    String client = exchange.getRequest()
            .getHeaders().getFirst("X-Client");
    return Mono.just(method + ":" + client);
}
```

Đây cũng là snippet, cần imports. **Đừng dùng ServerWebExchange chỉ vì ngại học annotation binding.** Khi lấy toàn bộ raw request, bạn phải tự quyết định trường nào được phép đọc/echo/log, cách convert, validation và thiếu giá trị. Với argument binding, Spring xử lý nhiều bước này rõ ràng và nhất quán hơn.

## 4. @RequestParam: query parameters trong WebFlux

WebFlux bind tham số của query string:

```text
GET /api/search?q=spring&page=3
GET /api/search?q=a%26b
GET /api/search?tag=java&tag=webflux
```

Ví dụ:

```java
@GetMapping("/search")
public Mono<SearchView> search(
        @RequestParam("q") String q,
        @RequestParam(name = "page", defaultValue = "1") int page) {
    return Mono.just(new SearchView(q, page));
}
```

Với định nghĩa `record SearchView(String q,int page) {}`, URL `?q=spring&page=3` tạo giá trị tương ứng. Query là dữ liệu URI, không liên quan việc request là GET hay POST: một POST cũng có thể chứa query params.

### Required và defaultValue

- `@RequestParam("q") String q`: mặc định **required=true**; thiếu `q` gây lỗi binding request (thường status 400).
- `@RequestParam(name="q",required=false) String q`: nếu thiếu, giá trị thường là null; logic phải kiểm.
- `@RequestParam(name="q",defaultValue="all") String q`: dùng default khi thiếu hoặc khi giá trị trống theo annotation contract; `defaultValue` ngầm khiến `required=false`.
- `@RequestParam Optional<String> q`: Spring hỗ trợ kiểu `Optional`, phân biệt có/không có theo resolver; không dùng `Optional<Optional<T>>`.
- Với `int page` không có default và missing, dùng required behavior rõ ràng hoặc `Integer`/`Optional<Integer>` để thể hiện optional.

### Thiếu so với rỗng

```text
/search               -> q không xuất hiện
/search?q=            -> q có xuất hiện, giá trị chuỗi rỗng
/search?q=hello       -> q="hello"
/search?q=%20         -> q=" " sau percent-decoding
```

Nếu nghiệp vụ không cho rỗng, **cần validation riêng** như kiểm `isBlank()`/Bean Validation, không nghĩ `required=true` luôn cấm `q=`. Đối với `defaultValue`, giá trị rỗng có thể được thay bằng default nên không nên sử dụng nó để chứng minh client có gửi giá trị thật.

### Chuyển đổi kiểu

`@RequestParam int page` chuyển String → int theo conversion service; `?page=abc` thường dẫn tới HTTP 400 (type mismatch), không thành 500 nếu xử lý lỗi mặc định đúng. `?page=-1` vẫn là int hợp lệ về mặt conversion nhưng có thể **không hợp lệ nghiệp vụ**. Phải đặt quy tắc `page>=1`/`page<=max` riêng.

Với enum, UUID, LocalDate và số thập phân, ConversionService có thể áp dụng converter phù hợp; đảm bảo format và thông báo lỗi có tính nhất quán thay vì nhận chuỗi rồi tự parse vội vàng ở controller.

## 5. Query nhiều giá trị và MultiValueMap

Query string có thể lặp lại cùng tên:

```http
GET /api/search?tag=java&tag=webflux&tag=reactor
```

`exchange.getRequest().getQueryParams()` trả `MultiValueMap<String,String>`, giữ **nhiều giá trị trên cùng key**:

```java
MultiValueMap<String, String> query =
        exchange.getRequest().getQueryParams();

List<String> tags = query.getOrDefault("tag", List.of());
String first = query.getFirst("tag");
```

Đây là snippet trong method có `ServerWebExchange exchange` và imports `java.util.List`, `org.springframework.util.MultiValueMap`. Theo request ví dụ, `tags` chứa `["java","webflux","reactor"]` và `first` là `"java"`.

Với `@RequestParam MultiValueMap<String,String> params`, Spring cũng có thể bind tất cả query params. Nếu chỉ dùng `Map<String,String>`, bạn có thể mất các giá trị lặp; nếu API quy định một giá trị duy nhất, **phải định nghĩa policy đối với duplicates**, đặc biệt khi gateway và backend có thể diễn giải query khác nhau.

### Không tự cắt raw query theo dấu &/=

Giá trị `q="a&b=c"` trên URI cần encode cho đúng component:

```text
?q=a%26b%3Dc  -> một q: "a&b=c"
?q=a&b=c      -> hai parameters q="a", b="c"
```

Dùng URI builder ở client và Spring argument binding ở server. Không giải mã percent sequences lần hai trong controller (ví dụ `%252F` phải còn literal `%2F` sau một lần decode). Query parsing và auth policy phải hiểu các ký tự phân cách theo cùng một cách qua reverse proxy/gateway.

## 6. @RequestHeader: đọc metadata HTTP

Header là tên không phân biệt hoa/thường theo HTTP semantics. Các dạng:

```java
@GetMapping("/headers")
public Mono<String> headers(
        @RequestHeader("X-Client") String client,
        @RequestHeader(name = "Accept-Language",
                       required = false) String language) {
    return Mono.just(client + ":" + language);
}
```

Snippet này sẽ yêu cầu `X-Client` nếu chưa đặt `required=false` hoặc `defaultValue`. Nếu header không có và required=true, Spring thường trả 400. `Accept-Language` có thể có nhiều giá trị/ranges, không nên tự dựa vào một chuỗi tự cắt để chọn locale khi framework đã có `LocaleContextResolver`.

### Một header, nhiều giá trị và typed header

```java
@RequestHeader(name = "X-Retry", defaultValue = "0") int retry;
@RequestHeader(name = "X-Flag", required = false)
        Optional<String> flag;
```

Các khai báo trên là **snippet tham số method**, không phải câu lệnh Java độc lập. Spring có thể convert String → type đích; `X-Retry: not-number` tạo lỗi conversion. Một số headers cho phép nhiều giá trị, dùng `List<String>`/`String[]` hoặc `@RequestHeader MultiValueMap<String,String>`/`HttpHeaders` để đọc đầy đủ.

```java
@GetMapping("/selected-header")
public Mono<String> chosen(@RequestHeader HttpHeaders headers) {
    return Mono.just(headers.getFirst("X-Client") == null
            ? "unknown" : headers.getFirst("X-Client"));
}
```

Đây là snippet trong controller, cần `org.springframework.http.HttpHeaders`. **Đừng đưa toàn bộ HttpHeaders vào response/log**: Authorization, Cookie và các token có thể lộ.

### Header không phải chứng cứ danh tính

`X-User: admin` hoặc `X-Role: root` do client gửi **không tự chứng thực** người dùng. Nếu upstream proxy xác thực rồi truyền identity bằng header, cần trust boundary: gateway kiểm xác thực, loại bỏ header do client tự khai, kết nối nội bộ được bảo vệ và downstream validate metadata. Thông thường **Spring Security reactive** nên tạo Authentication/Principal đáng tin trong security chain.

`Host`, `Forwarded`, `X-Forwarded-*` cũng có thể bị giả mạo nếu proxy trust không cấu hình đúng; không tự sinh link tuyệt đối hoặc quyết định tenant/redirect từ headers chưa xác thực.

## 7. @CookieValue: lấy cookie riêng lẻ

Cookie đi trong request header `Cookie` nhưng có resolver riêng:

```java
@GetMapping("/theme")
public Mono<String> theme(
        @CookieValue(name = "theme",
                     defaultValue = "light") String theme) {
    return Mono.just(theme);
}
```

- `@CookieValue("theme")` mặc định required; không gửi có thể gây HTTP 400.
- `required=false`, `Optional<String>` hoặc `defaultValue` thích hợp khi cookie tùy chọn.
- `@CookieValue` chuyển giá trị theo converter nếu kiểu đích không phải String.
- `exchange.getRequest().getCookies()` trả `MultiValueMap<String,HttpCookie>` khi cần xem nhiều cookies/metadata.
- Cookie tên trùng nhau có thể đến từ các path/domain khác nhau; không tự chọn một cách thiếu chủ đích nếu đó là quyết định bảo mật.

Ví dụ:

```java
MultiValueMap<String, HttpCookie> cookies =
        exchange.getRequest().getCookies();
HttpCookie first = cookies.getFirst("theme");
String value = first == null ? "light" : first.getValue();
```

Đây là snippet trong method đã có `ServerWebExchange` và imports `org.springframework.http.HttpCookie`.

### HttpOnly, Secure, SameSite không tạo authentication tự động

- `HttpOnly` khiến JavaScript phía browser thông thường không đọc cookie, **server vẫn nhận cookie** trong HTTP requests phù hợp.
- `Secure` giới hạn việc browser gửi cookie trên kết nối an toàn, không xác minh tính toàn vẹn business content.
- `SameSite` giúp giảm một số tình huống gửi cookie cross-site, **không thay** CSRF protection khi ứng dụng cần.
- Cookie value có thể bị sửa trừ khi dùng cơ chế ký/mã hóa và server-side validation; tên cookie như `role=admin` không phải role thật.
- Session cookie cần cấu hình Secure/HttpOnly/SameSite và session security policy theo môi trường; không echo session token trong JSON hoặc log.

Cookie là input cần kiểm tra, không phải kho dữ liệu server bảo đảm đáng tin chỉ vì browser quản lý nó.

## 8. Principal: danh tính đã được framework xác thực

WebFlux hỗ trợ method argument kiểu `java.security.Principal` và reactive wrapper khi thích hợp, cùng `ServerWebExchange.getPrincipal()` trả `Mono<Principal>`.

```java
@GetMapping("/whoami")
public Mono<String> whoami(ServerWebExchange exchange) {
    return exchange.getPrincipal()
            .map(Principal::getName)
            .defaultIfEmpty("anonymous");
}
```

Đây là snippet trong controller với import `java.security.Principal`. Nếu không có principal, `Mono.empty()` được chuyển thành chuỗi `"anonymous"`. **Không kết luận rằng unauthenticated user được phép truy cập mọi resource**: endpoint public có thể trả anonymous; endpoint bảo vệ phải được policy chặn (401/403) trước khi controller xử lý.

### @AuthenticationPrincipal thuộc Spring Security

Nếu tích hợp `spring-boot-starter-security`/Spring Security WebFlux:

```java
@GetMapping("/secure/profile")
public Mono<ProfileView> profile(
        @AuthenticationPrincipal CustomUser user) {
    return service.profileFor(user.id());
}
```

Đây là **snippet ý tưởng** cần Spring Security dependency, type `CustomUser`/`ProfileView` và bean `service`. `@AuthenticationPrincipal` là annotation Spring Security (`org.springframework.security.core.annotation.AuthenticationPrincipal`), không phải từ `spring-boot-starter-webflux` thuần. Không thêm nó vào file demo không có security.

`Authentication` trong Spring Security thường implement `Principal`; `Principal.getName()` là một tên định danh, không tự bảo đảm mọi application-specific role, tenant hoặc permission. Để kiểm tra quyền, dùng reactive security configuration, method security khi áp dụng và domain-level authorization.

### Principal có thể là empty

`exchange.getPrincipal()` là một Mono, không được gọi `.block()` trong Netty event loop để lấy giá trị cho controller. Kết hợp:

```java
return exchange.getPrincipal()
    .map(Principal::getName)
    .defaultIfEmpty("anonymous")
    .map(name -> new UserView(name));
```

Đây là composition non-blocking; WebFlux subscribe và ghi HTTP response. Nếu code lấy principal bằng `@RequestHeader("X-User")` mà không có trusted authentication middleware, client có thể tự đổi sang bất kỳ tên nào.

## 9. ServerWebExchange là gì?

`org.springframework.web.server.ServerWebExchange` bao toàn bộ interaction ở lớp WebFlux:

| API | Kiểu hoặc ý nghĩa |
| --- | --- |
| `getRequest()` | `ServerHttpRequest` (method, URI, headers, cookies, query params, path, ID...) |
| `getResponse()` | `ServerHttpResponse` (status, headers, cookies, body writing) |
| `getAttributes()` | `Map<String,Object>` mutable cho **một exchange** |
| `getAttribute(key)` | Đọc attribute đã tồn tại, có thể null |
| `getRequiredAttribute(key)` | Lấy bắt buộc; lỗi nếu thiếu |
| `getAttributeOrDefault(key, fallback)` | Fallback khi key không có |
| `getPrincipal()` | `Mono<Principal>` hoặc Mono empty |
| `getSession()` | `Mono<WebSession>` |
| `getFormData()` | `Mono<MultiValueMap<String,String>>` |
| `getMultipartData()` | `Mono<MultiValueMap<String,Part>>` |
| `getLocaleContext()` | LocaleContext theo resolver |
| `getLogPrefix()` | Prefix tương quan log của exchange |
| `checkNotModified(...)` | Hỗ trợ conditional requests ETag/Last-Modified |

**Exchange không phải một singleton:** mỗi HTTP interaction có attributes và response state riêng. Không cất `ServerWebExchange` trong static map/background thread sống lâu hơn request. Khi result body là `Flux` chưa hoàn tất, exchange/connection vẫn còn vòng đời xử lý, cần cleanup trên cancel và error.

### getRequest() phổ biến

```java
ServerHttpRequest request = exchange.getRequest();
String method = request.getMethod().name();
String path = request.getPath().value();
String rawQuery = request.getURI().getRawQuery();
String client = request.getHeaders().getFirst("X-Client");
String firstTag = request.getQueryParams().getFirst("tag");
String requestId = request.getId();
```

Snippet trên yêu cầu imports. `getRawQuery()` có thể null nếu URI không có query. `getPath().value()` không phải filesystem path; đừng nối nó vào file API. `getId()`/`getLogPrefix()` hỗ trợ logs, không phải security credentials.

### Response headers/status

```java
exchange.getResponse().getHeaders()
        .add("X-App-Info", "webflux");
```

Có thể set header trước khi response commit; sau commit không bảo đảm sửa status/headers còn tác dụng. Thông thường controller trả `ResponseEntity<T>` để kiểm soát status/headers, còn `WebFilter` thêm cross-cutting correlation headers.

Đừng gọi `getResponse().setComplete()` rồi tiếp tục return Publisher ghi JSON cùng response. Hoặc để framework quản lý body, hoặc chủ động handle response với `Mono<Void>` đúng contract.

## 10. Exchange attributes: dữ liệu nội bộ của một request

Filter có thể tạo metadata khi request đến:

```java
exchange.getAttributes().put("app.traceId", traceId);
return chain.filter(exchange);
```

Controller đọc bằng:

```java
String trace = exchange.getRequiredAttribute("app.traceId");
```

Hoặc khai báo:

```java
@GetMapping("/trace")
public Mono<String> trace(
        @RequestAttribute("app.traceId") String traceId) {
    return Mono.just(traceId);
}
```

`@RequestAttribute` lấy **attribute đã được server/filter tạo**, không đọc query/header cùng tên. Không nhầm `@RequestAttribute` với `@RequestParam` hoặc `@SessionAttribute`.

Các quy tắc:

- Dùng key namespace rõ, ví dụ `app.traceId`, tránh đụng framework keys.
- Attributes tồn tại theo **một exchange**, không tự lưu qua request khác.
- Attribute có thể chứa object Java, không phải chỉ String; tránh cast sai kiểu.
- Không đưa credential/secret vào attributes nếu không kiểm soát người có thể log/serialize chúng.
- Nếu filter không chạy (route/filter configuration khác), required attribute thiếu có thể tạo error; dùng `required=false` hoặc kiểm tra fallback khi thích hợp.
- Dùng correlation ID do server tạo hoặc do trusted ingress kiểm chứng. Header `X-Request-Id` tự khai từ client nên được validate/normalize trước khi dùng làm trace ID tin cậy.

**Attribute không phải Session:** khi reload trang, browser gửi request mới; exchange attributes cũ không có nữa. **Attribute không phải Reactor Context:** context đi theo subscriber chain, không phải mutable map thay thế cho mọi propagation qua scheduler.

## 11. Đọc form-urlencoded: getFormData()

Request:

```http
POST /api/input/form?name=query HTTP/1.1
Content-Type: application/x-www-form-urlencoded

name=Linh&topic=java&topic=webflux
```

Trong WebFlux:

```java
@PostMapping(path = "/form",
             consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
public Mono<FormView> form(ServerWebExchange exchange) {
    return exchange.getFormData()
        .map(form -> new FormView(
            form.getFirst("name"),
            form.getOrDefault("topic", List.of())));
}
```

Đây là snippet có `record FormView(String name,List<String> topic) {}` trong controller.

`getFormData()` trả `Mono<MultiValueMap<String,String>>` được giải mã từ **body form-urlencoded**. Spring sử dụng configured `HttpMessageReader`. Nếu Content-Type không phù hợp, contract trả empty map thay vì “đọc mọi body dưới dạng form”.

**Khác biệt trọng yếu:** WebFlux `@RequestParam("name")` đọc `?name=query` ở URI, **không tự lấy** `name=Linh` trong form body. Trong Spring MVC Servlet, khái niệm request parameters thường gộp query/form; không áp suy luận đó vào reactive stack.

### getFormData có được gọi hai lần không?

`DefaultServerWebExchange` đọc và parse form body thành map rồi **cache kết quả** để `getFormData()` có thể được gọi nhiều lần mà không đọc lại stream từ network. Điều đó không có nghĩa raw body `Flux<DataBuffer>` có thể tùy tiện đọc lặp lại.

Vì parser có thể phải tiêu thụ/cất form data, cần đặt payload size/memory budgets và không chấp nhận form body khổng lồ. Với JSON, dùng `@RequestBody Mono<Dto>` để framework decode, không gọi `getFormData()`.

## 12. Đọc multipart: getMultipartData()

Với `multipart/form-data`:

```bash
curl -i -F 'description=demo' \
  -F 'file=@sample.txt' \
  http://localhost:8080/api/input/multipart
```

`exchange.getMultipartData()` trả `Mono<MultiValueMap<String,Part>>`. Trong đó `Part` biểu diễn form/file part, có thể là `FormFieldPart` hoặc `FilePart`.

Một endpoint chỉ xem **metadata** về các tên part có thể viết:

```java
@PostMapping(path = "/multipart",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Mono<UploadSummary> multipart(ServerWebExchange exchange) {
    return exchange.getMultipartData()
        .map(parts -> new UploadSummary(
            parts.keySet().stream().sorted().toList(),
            parts.values().stream().mapToInt(List::size).sum()));
}
```

Snippet giả định `record UploadSummary(List<String> names,int count) {}`. Đây không phải xử lý upload thực: chưa lưu hay quét file, chỉ đếm các parts. **Không log nguyên file content hoặc raw filename từ client**.

### Những giới hạn rất quan trọng

- `getMultipartData()` đọc/parse request thành `MultiValueMap` và cache **map parts** khi phù hợp; **nội dung mỗi Part không được cache để đọc lặp**.
- Với file lớn, full multipart map parsing có overhead và giới hạn memory/disk tùy codec; xem streaming `Flux<PartEvent>` và configuration limits nếu cần.
- Nếu lấy `Part.content()` là `Flux<DataBuffer>`, data buffers phải được tiêu thụ, chuyển tiếp hoặc release đúng cơ chế; không giữ các DataBuffer trong list vô hạn.
- `FilePart.filename()` là **tên không tin cậy do client cung cấp**; không dùng trực tiếp làm path lưu file.
- Validate file size, count, MIME/content signature, filename canonicalization, storage permissions và antivirus/quarantine tùy rủi ro.
- Đặt hạn mức `maxInMemorySize`/multipart codec configuration phù hợp. Vì một request có thể gửi nhiều parts, cần tổng request budget, không chỉ per-field.
- `@RequestPart("file") FilePart file` hoặc `@RequestPart("meta") Mono<Dto>` là lựa chọn tốt khi chỉ cần vài parts.

**Không trộn hai cách consume body** (`getMultipartData()` và raw `@RequestBody Flux<PartEvent>`) trên cùng request rồi mong đọc cùng bytes hai lần.

## 13. Đọc JSON body: @RequestBody Mono<T>

Nếu request body JSON:

```http
POST /api/input/json HTTP/1.1
Content-Type: application/json

{"message":"Xin chào"}
```

Khai báo:

```java
@PostMapping(path = "/json",
             consumes = MediaType.APPLICATION_JSON_VALUE)
public Mono<NoteView> json(
        @RequestBody Mono<NoteInput> input) {
    return input.map(v -> new NoteView(v.message()));
}
```

Đây là snippet cần DTO records/imports. `@RequestBody` dùng `HttpMessageReader`/Jackson decoder phù hợp để parse JSON. Mono của body cho phép nối các toán tử mà không block event loop.

**Không gọi `exchange.getRequest().getBody().collectList().block()` trong controller.** Nó vừa block vừa có thể gom bytes vào memory và lấy mất stream khỏi decoder, dẫn tới lỗi khi handler khác đọc body. Nếu muốn log body trong WebFilter, cần kiến trúc decorator/buffer bounded, redaction, reference counting và security review; không đơn giản “đọc một lần rồi chuyển tiếp y nguyên”.

Form-urlencoded, multipart và JSON là **body formats khác nhau**, không thể chỉ đổi annotation `@RequestParam` rồi mong WebFlux đọc hết cả ba.

## 14. getSession(): WebSession và giới hạn lưu trạng thái

`exchange.getSession()` trả `Mono<WebSession>`. Khi cần state server quản lý cho người dùng, dùng WebSession thay vì Exchange attributes:

```java
@GetMapping("/session-info")
public Mono<SessionView> sessionInfo(
        ServerWebExchange exchange) {
    return exchange.getSession()
        .map(s -> new SessionView(
            s.isStarted(), s.getAttributes().size()));
}
```

Snippet này không trả session ID/token ra client. `WebSession` có `getId()`, `getAttributes()`, `getMaxIdleTime()`, `invalidate()`, `save()`; không phải API cho phép lưu vô hạn data giữa các requests.

Spring WebFlux method arguments cũng có thể nhận `WebSession` (hoặc reactive wrapper phù hợp). Gọi `getSession()` để đọc không đồng nghĩa phải bắt đầu session mới ở phía client; theo Spring docs, session thường không bị start cho tới khi attributes được thêm hoặc API start được gọi.

### Attribute, session và Principal không thay nhau

| Khái niệm | Thời gian sống | Dùng làm gì |
| --- | --- | --- |
| Exchange attribute | Một HTTP exchange | Trace, metadata từ filter, request-local state |
| Reactor Context | Một subscription chain | Trace/security/reactive context propagation |
| WebSession | Tồn tại qua requests theo session policy | Server-side user session state |
| Cookie | Client gửi lại theo browser policies | Session identifier hoặc client preference |
| Principal | Identity từ authentication chain | Xác định user hiện tại khi auth được cấu hình |
| Database record | Durable theo domain | Thông tin nghiệp vụ và authorization data |

Không lưu `ServerWebExchange` hay object có reference đến request/response trong `WebSession`. Khi dùng Redis WebSession, chỉ lưu dữ liệu nhỏ, được serializable theo config và không nhạy cảm ngoài ý muốn. Session không thay authorization check mỗi lần truy cập protected resource.

## 15. Locale, method, URI và request context

`ServerWebExchange.getLocaleContext()` cho locale/time zone theo resolver ứng dụng. Method argument `Locale` được Spring WebFlux hỗ trợ trực tiếp. Đừng tự lấy `Accept-Language` rồi chọn chuỗi đầu tiên khi resolver đã cung cấp logic.

```java
@GetMapping("/locale")
public Mono<String> locale(Locale locale) {
    return Mono.just(locale.toLanguageTag());
}
```

Snippet này trả locale tag ứng dụng quyết định. Khi client gửi `Accept-Language: vi-VN,vi;q=0.9`, kết quả tùy locale resolver có thể là `vi-VN` nếu cấu hình phù hợp; không coi thông tin locale là identity/country của người dùng.

Lấy đường dẫn/canonical request data:

```java
String uri = exchange.getRequest().getURI().toString();
String rawPath = exchange.getRequest().getURI().getRawPath();
String path = exchange.getRequest().getPath().value();
String method = exchange.getRequest().getMethod().name();
String logPrefix = exchange.getLogPrefix();
```

Đây là snippets. `rawPath` chứa percent-encoding theo URI; `getPath().value()` biểu diễn path theo API WebFlux (đừng tự decode hai lần). Không dùng URI/query raw làm filesystem path, key authorization hoặc log toàn bộ khi URL có bí mật. Forwarded headers có thể bị spoof nếu chưa cấu hình trusted proxy.

## 16. Vòng đời data trong reactive pipeline

Sơ đồ:

```text
HTTP headers + query + cookies  ──────> Handler argument binding
       |
       +-- WebFilter writes exchange attribute
       |
HTTP body (JSON/form/multipart) ───────> Mono/Flux decoder/parser
       |
Authentication middleware  ───────────> exchange.getPrincipal()
       |
WebSession manager ────────────────────> exchange.getSession()
       |
Controller returns Mono<DTO> / Flux<T>
       |
HTTP infrastructure subscribes / writes response
       |
complete / error / cancel -> cleanup
```

Header/query/cookie information thường đã sẵn trong request metadata; body reading là **reactive**, có thể chỉ thực thi khi downstream subscribe. Không gọi `subscribe()` bằng tay để “đọc body sớm” rồi return response độc lập.

`Mono<Principal>` hay `Mono<MultiValueMap<...>>` không bảo đảm asynchronous thread mới; Reactor chỉ định tín hiệu/demand. Không gọi `block()` bên trong controller/WebFilter trên Netty event loop. Dữ liệu request thuộc về exchange; sau completion/cancel không giữ một lambda background tham chiếu exchange rồi thao tác response.

## 17. Mã runnable: Spring Boot 3 WebFlux + Java 17

Tạo Maven project Spring Initializr:

- Java 17+, Spring Boot **3.x**.
- Dependency `spring-boot-starter-webflux`.
- Dependency `spring-boot-starter-test` (test scope), `io.projectreactor:reactor-test` nếu project chưa có.
- **Không thêm `spring-boot-starter-web`** nếu muốn chắc Boot chọn WebFlux reactive application type trong cấu hình mặc định.

Chương trình demo không cần database hoặc Spring Security, vì vậy `exchange.getPrincipal()` mặc định không có user và trả `"anonymous"` theo fallback. Demo chỉ **echo** những giá trị đã chọn; không mở endpoint hiển thị toàn bộ headers/cookies.

Cấu trúc:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  CorrelationFilter.java
  InputController.java
src/test/java/com/example/demo/
  InputControllerTest.java
```

### 17.1. DemoApplication.java

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

### 17.2. CorrelationFilter.java

```java
package com.example.demo;

import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class CorrelationFilter implements WebFilter {

    public static final String TRACE_ID = "app.traceId";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange, WebFilterChain chain) {

        // Server tự tạo trace ID, không tin X-Trace-Id client tự khai.
        String trace = UUID.randomUUID().toString();

        exchange.getAttributes().put(TRACE_ID, trace);
        exchange.getResponse().getHeaders()
                .set("X-Trace-Id", trace);

        return chain.filter(exchange);
    }
}
```

Filter tạo một attribute trong scope của exchange và header response cùng giá trị. UUID minh họa **correlation ID**, không phải access token, secret hay thứ dùng để quyết định authorization. Trong production, dùng observation/tracing infrastructure để liên kết spans/requests, điều chỉnh order trước/sau security theo policy.

### 17.3. InputController.java

```java
package com.example.demo;

import java.security.Principal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/input")
public class InputController {

    @GetMapping(path = "/query",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<QueryView> query(
            @RequestParam("q") String q,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestHeader(name = "X-Client",
                           defaultValue = "unknown") String client,
            @CookieValue(name = "theme",
                         defaultValue = "none") String theme,
            @RequestAttribute(CorrelationFilter.TRACE_ID)
                    String traceId,
            ServerWebExchange exchange) {

        // Repeated query params không bị mất trong MultiValueMap.
        List<String> tags = exchange.getRequest().getQueryParams()
                .getOrDefault("tag", List.of());

        // Khi không có Authentication provider, principal có thể empty.
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .map(name -> new QueryView(
                    q, page, List.copyOf(tags), client,
                    theme, name, traceId));
    }

    @GetMapping(path = "/raw",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RawView> raw(ServerWebExchange exchange) {
        var request = exchange.getRequest();

        String method = request.getMethod().name();
        String path = request.getPath().value();
        String firstTag = request.getQueryParams().getFirst("tag");
        String firstClient = request.getHeaders().getFirst("X-Client");

        HttpCookie cookie = request.getCookies().getFirst("theme");
        String theme = cookie == null ? "none" : cookie.getValue();

        return Mono.just(new RawView(
            method, path,
            firstTag == null ? "none" : firstTag,
            firstClient == null ? "unknown" : firstClient,
            theme,
            exchange.getRequiredAttribute(
                    CorrelationFilter.TRACE_ID)));
    }

    @GetMapping(path = "/whoami",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserView> whoami(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .map(UserView::new);
    }

    @GetMapping(path = "/session-info",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SessionView> session(ServerWebExchange exchange) {
        // Không trả session ID/token về client.
        return exchange.getSession()
                .map(s -> new SessionView(
                    s.isStarted(), s.getAttributes().size()));
    }

    @PostMapping(path = "/form",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<FormView> form(
            @RequestParam(name = "name", required = false)
                    String queryName,
            ServerWebExchange exchange) {

        // @RequestParam trong WebFlux chỉ lấy query "name".
        // getFormData đọc name và topic từ BODY form-urlencoded.
        return exchange.getFormData()
                .map(values -> new FormView(
                    queryName == null ? "(không có)" : queryName,
                    values.getFirst("name"),
                    List.copyOf(values.getOrDefault(
                        "topic", List.of()))));
    }

    @PostMapping(path = "/json",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<NoteView> json(
            @RequestBody Mono<NoteInput> input) {
        return input.map(value -> new NoteView(value.message()));
    }

    @PostMapping(path = "/multipart",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UploadView> multipart(
            ServerWebExchange exchange) {
        // Chỉ thống kê tên part, KHÔNG đọc/lưu file bytes.
        return exchange.getMultipartData()
                .map(parts -> new UploadView(
                    parts.keySet().stream().sorted().toList(),
                    parts.values().stream()
                        .mapToInt(List::size).sum()));
    }

    public record QueryView(
            String q, int page, List<String> tags,
            String client, String theme,
            String principal, String traceId) {}

    public record RawView(
            String method, String path,
            String firstTag, String client,
            String theme, String traceId) {}

    public record UserView(String name) {}

    public record SessionView(
            boolean started, int attributeCount) {}

    public record FormView(
            String queryName, String formName, List<String> topics) {}

    public record NoteInput(String message) {}
    public record NoteView(String message) {}

    public record UploadView(
            List<String> partNames, int partCount) {}
}
```

**Vì sao code này dùng hai đường để đọc metadata?** Endpoint `/query` minh họa annotation binding cùng Principal; `/raw` minh họa `ServerWebExchange` direct APIs. Cả hai vẫn chạy cùng Spring WebFlux handler infrastructure.

**Vì sao Principal fallback?** Project demo chưa cài Spring Security; `getPrincipal()` thường empty. Trong app có security, principal có thể hiện diện theo policy. `"anonymous"` chỉ là một nhãn output hiển thị, **không phải** identity đã xác thực.

**Vì sao form queryName khác formName?** Để kiểm chứng WebFlux `@RequestParam` chỉ đọc URI query còn `getFormData()` đọc form body. Không tự trộn chúng theo hành vi Spring MVC.

**Vì sao Multipart chỉ đếm parts?** Chủ đề là đọc dữ liệu trong exchange; triển khai upload thật cần giới hạn kích thước, kiểm chữ ký file, storage an toàn và lựa chọn streaming riêng. File content có thể tiêu tốn tài nguyên ngay cả khi endpoint chỉ đếm tên parts; không bật endpoint metadata demo ngoài môi trường test mà không có access control.

**Về imports:** `HttpHeaders` và `MultiValueMap` được nêu trong các ví dụ phía trước nhưng chưa dùng trực tiếp trong method hoàn chỉnh; Java cho phép unused imports (compiler Java thông thường không coi đó là lỗi). Có thể bỏ khi dùng IDE để code gọn.

### 17.4. Chạy ứng dụng

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Nếu không tùy chỉnh, endpoint ở `http://localhost:8080`. Mã Spring Boot phải có Maven/Gradle classpath; `javac InputController.java` độc lập không thể biên dịch vì thiếu Spring/Reactor dependencies.

## 18. Curl: query, header, cookie, principal và trace

### 18.1. Query có tag lặp và header/cookie

```bash
curl -i 'http://localhost:8080/api/input/query?q=a%2Bb%26c&page=2&tag=java&tag=webflux' \
  -H 'X-Client: mobile-app' \
  -H 'Cookie: theme=dark'
```

Dự kiến HTTP 200 với body mang các trường:

```json
{
  "q": "a+b&c",
  "page": 2,
  "tags": ["java", "webflux"],
  "client": "mobile-app",
  "theme": "dark",
  "principal": "anonymous",
  "traceId": "UUID-khác-nhau-mỗi-request"
}
```

Chuỗi `"UUID-khác-nhau-mỗi-request"` ở JSON trên chỉ là **ký hiệu minh họa**, không phải stdout literal. Thực tế `traceId` là UUID do filter server tạo, ví dụ `"550e8400-e29b-41d4-a716-446655440000"`, đồng thời response header `X-Trace-Id` mang đúng cùng giá trị. Không hardcode UUID hoặc thứ tự JSON properties trong test.

### 18.2. Default values

```bash
curl -i 'http://localhost:8080/api/input/query?q=hello'
```

Dự kiến `page=1`, `tags=[]`, `client="unknown"`, `theme="none"`, `principal="anonymous"`, còn `traceId` thay đổi mỗi request.

### 18.3. Missing query bắt buộc

```bash
curl -i 'http://localhost:8080/api/input/query'
```

Dự kiến **HTTP 400 Bad Request** vì thiếu `q`. Body lỗi phụ thuộc Spring Boot/WebFlux error handling; không cam kết một JSON định dạng duy nhất nếu chưa tự cấu hình `ErrorWebExceptionHandler`.

### 18.4. Sai kiểu page

```bash
curl -i 'http://localhost:8080/api/input/query?q=ok&page=abc'
```

Dự kiến **400** do conversion String→int thất bại. Ngược lại, `page=-1` vẫn convert được, cần domain validation nếu không chấp nhận.

### 18.5. Đọc ServerWebExchange metadata trực tiếp

```bash
curl -i 'http://localhost:8080/api/input/raw?tag=java&tag=webflux' \
  -H 'X-Client: cli' \
  -b 'theme=light'
```

Body JSON ví dụ:

```json
{
  "method": "GET",
  "path": "/api/input/raw",
  "firstTag": "java",
  "client": "cli",
  "theme": "light",
  "traceId": "UUID-khác-nhau-mỗi-request"
}
```

Response `X-Trace-Id` và JSON traceId phải bằng nhau; ý tưởng này test filter tạo exchange attribute rồi controller đọc, **không phải lấy trace trực tiếp từ header client gửi**.

### 18.6. Principal khi chưa cài security

```bash
curl -i 'http://localhost:8080/api/input/whoami'
```

Dự kiến 200:

```json
{"name":"anonymous"}
```

Trong app có Spring Security yêu cầu authentication, request chưa có thông tin hợp lệ có thể nhận 401/redirect thay vì chạy controller; đây là kết quả mong đợi của policy, không phải lỗi `exchange.getPrincipal()`.

### 18.7. Session chỉ được xem metadata

```bash
curl -i 'http://localhost:8080/api/input/session-info'
```

Dự kiến response JSON với `started` (boolean) và `attributeCount` (số nguyên). Với project demo chưa thêm session attributes, thường `started=false`, `attributeCount=0`. Không echo session ID hoặc Set-Cookie chứa session token như “bằng chứng”.

## 19. Curl: form-urlencoded khác query params

Gửi body form:

```bash
curl -i -X POST \
  'http://localhost:8080/api/input/form?name=query' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'name=Linh&topic=java&topic=webflux'
```

Expected JSON:

```json
{
  "queryName": "query",
  "formName": "Linh",
  "topics": ["java", "webflux"]
}
```

Vì `@RequestParam(name="name")` trong WebFlux chỉ nhìn query string nên `queryName="query"`, còn `getFormData().getFirst("name")` là `"Linh"`. Nếu bỏ `?name=query`, `queryName` sẽ thành `"(không có)"` theo code demo, còn body vẫn giữ Linh.

Để khỏi tự encode form fields:

```bash
curl -i -X POST \
  'http://localhost:8080/api/input/form' \
  --data-urlencode 'name=Linh' \
  --data-urlencode 'topic=a+b' \
  --data-urlencode 'topic=Spring & WebFlux'
```

`--data-urlencode` sẽ encode dữ liệu theo form-style, literal `+` và `&` không biến thành delimiters ngoài ý muốn. Nếu gửi raw `topic=a+b` trong form body, dấu `+` có thể được parser coi là space; muốn dấu cộng literal phải dùng `%2B`.

### Vì sao không dùng URLDecoder trong controller?

`getFormData()` đã được form parser decode. Nếu value là literal `"%2F"` được gửi trên wire thành `%252F`, ứng dụng nhận đúng `"%2F"`. Chạy `URLDecoder.decode(...)` thêm lần nữa sẽ đổi thành slash `"/"` và có thể tạo bug bảo mật/đổi business value.

## 20. Curl: JSON và multipart

### 20.1. JSON body

```bash
curl -i -X POST \
  'http://localhost:8080/api/input/json' \
  -H 'Content-Type: application/json' \
  -d '{"message":"Xin chào WebFlux"}'
```

Dự kiến HTTP 200:

```json
{"message":"Xin chào WebFlux"}
```

Đây là `@RequestBody Mono<NoteInput>`, không phải `@RequestParam` và không phải `getFormData`. Nếu gửi JSON syntax hỏng, reactive body reader thường tạo 400. Nếu thiếu `message`, record có thể nhận null theo Jackson nếu không có validation; ứng dụng thật cần `@Valid`/constraints và domain checks.

### 20.2. Multipart form

Tạo tệp nhỏ `sample.txt` chứa dữ liệu test rồi chạy:

```bash
curl -i -X POST \
  'http://localhost:8080/api/input/multipart' \
  -F 'description=demo' \
  -F 'file=@sample.txt'
```

Body ví dụ:

```json
{"partNames":["description","file"],"partCount":2}
```

Spring multipart codec quyết định parsing và giới hạn. Không dùng ví dụ “đếm parts” để kết luận file upload đã được lưu, quét virus hoặc kiểm MIME. Nếu tên file độc hại và size quá lớn, production phải reject/sanitize đúng và cleanup resources, không chỉ echo metadata.

## 21. WebTestClient: test controller và filter trên HTTP thật

Tệp `src/test/java/com/example/demo/InputControllerTest.java`:

```java
package com.example.demo;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class InputControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void queryHeaderCookieAndFilterAttribute() {
        client.get()
            .uri("/api/input/query?q=a%2Bb%26c&page=2"
                + "&tag=java&tag=webflux")
            .header("X-Client", "mobile-app")
            .cookie("theme", "dark")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("X-Trace-Id")
            .expectBody()
            .jsonPath("$.q").isEqualTo("a+b&c")
            .jsonPath("$.page").isEqualTo(2)
            .jsonPath("$.tags[0]").isEqualTo("java")
            .jsonPath("$.tags[1]").isEqualTo("webflux")
            .jsonPath("$.client").isEqualTo("mobile-app")
            .jsonPath("$.theme").isEqualTo("dark")
            .jsonPath("$.principal").isEqualTo("anonymous")
            .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    void missingRequiredQueryParameterReturns400() {
        client.get().uri("/api/input/query")
            .exchange().expectStatus().isBadRequest();
    }

    @Test
    void wrongPageTypeReturns400() {
        client.get().uri("/api/input/query?q=ok&page=nope")
            .exchange().expectStatus().isBadRequest();
    }

    @Test
    void rawExchangeSeesRequestMetadata() {
        client.get().uri("/api/input/raw?tag=java&tag=reactor")
            .header("X-Client", "cli")
            .cookie("theme", "light")
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.method").isEqualTo("GET")
            .jsonPath("$.path").isEqualTo("/api/input/raw")
            .jsonPath("$.firstTag").isEqualTo("java")
            .jsonPath("$.client").isEqualTo("cli")
            .jsonPath("$.theme").isEqualTo("light");
    }

    @Test
    void withoutSecurityPrincipalIsAnonymous() {
        client.get().uri("/api/input/whoami")
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("anonymous");
    }

    @Test
    void formBodyIsDifferentFromQueryString() {
        client.post()
            .uri("/api/input/form?name=query")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("name", "Linh")
                .with("topic", "java")
                .with("topic", "webflux"))
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.queryName").isEqualTo("query")
            .jsonPath("$.formName").isEqualTo("Linh")
            .jsonPath("$.topics[0]").isEqualTo("java")
            .jsonPath("$.topics[1]").isEqualTo("webflux");
    }

    @Test
    void jsonBodyIsDecodedByHttpMessageReader() {
        client.post().uri("/api/input/json")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("message", "hello"))
            .exchange().expectStatus().isOk()
            .expectBody()
            .jsonPath("$.message").isEqualTo("hello");
    }

    @Test
    void contentTypeIsPartOfMappingContract() {
        client.post().uri("/api/input/json")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("not-json")
            .exchange()
            .expectStatus().isUnsupportedMediaType();
    }
}
```

Chạy trong Maven Boot 3 project:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

Các tests kiểm chính xác những giá trị cốt lõi, **không hardcode UUID** của filter. Nếu muốn khẳng định response header X-Trace-Id **bằng** trường `traceId` trong body, dùng `EntityExchangeResult<byte[]>` và JSON parser/Jackson hoặc consumer của `WebTestClient` để so hai giá trị động trong cùng một response. Việc chỉ kiểm tồn tại/không rỗng (như test mẫu) chưa chứng minh bằng nhau; đây là test bổ sung có ích.

**Giới hạn:** đây là code kiểm thử mẫu; không phải lời khẳng định đã chạy trong repository người dùng. Với Spring Security, request có thể bị chặn bởi authentication/CSRF và test phải tạo security context hoặc credentials phù hợp. `RANDOM_PORT` giúp đi qua HTTP server thực, nhưng chưa bao phủ reverse proxy/CDN và cấu hình production.

## 22. Test Principal thực với Spring Security

Để test authenticated Principal, cần thêm Spring Security WebFlux:

- `spring-boot-starter-security` cho runtime, tạo `SecurityWebFilterChain` đúng policy.
- `spring-security-test` cho test với mock authentication.
- Dùng `@AuthenticationPrincipal` hoặc `ReactiveSecurityContextHolder` khi contract cần thông tin sâu hơn tên `Principal`.
- Khi dùng WebTestClient, cấu hình `mutateWith(mockUser("alice"))` với mock-bound WebTestClient hoặc các setup phù hợp theo Spring Security testing docs; không assume nó hoạt động với mọi kiểu `RANDOM_PORT` live server không được bind test security context.
- Với HTTP thật, dùng Basic/Bearer/cookie/session credentials qua security filter; đừng giả danh `X-User: alice` và coi đó là Principal.
- Kiểm phân biệt **401 Unauthorized** khi thiếu credentials và **403 Forbidden** khi đã xác thực nhưng thiếu permission; status cụ thể phụ thuộc cấu hình Spring Security, nên test theo policy thực tế.
- Kiểm authentication trước khi trả nội dung hay metadata bí mật. Không dùng một `Optional<Principal>` có mặt làm authorization thay cho kiểm roles/tenant/resource owner.

Một ví dụ cực ngắn (không phải file compile độc lập):

```java
@GetMapping("/secure/me")
public Mono<String> me(ServerWebExchange exchange) {
    return exchange.getPrincipal()
        .map(Principal::getName)
        .switchIfEmpty(Mono.error(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
}
```

Endpoint này mới **phản ánh tên principal**, chưa phải security policy đầy đủ; với WebFlux Security nên bảo vệ route bằng `SecurityWebFilterChain`. Nếu authentication là một anonymous Authentication object, cần phân biệt nó với user thật theo Spring Security policy, không chỉ kiểm `Mono<Principal>` có giá trị.

## 23. Bảo mật: dữ liệu nào đáng tin?

### Xác thực khác dữ liệu do client khai

| Input | Có tự đáng tin? | Yêu cầu |
| --- | --- | --- |
| Query `userId=42` | Không | Authz kiểm actor được đọc resource 42? |
| Header `X-Role=ADMIN` | Không | Chỉ đọc nếu trusted gateway kiểm và loại client spoof |
| Cookie `role=admin` | Không | Signed/encrypted/server-validated value nếu cần |
| `Principal` từ security chain | Là identity do security xác định | Vẫn phải check permission/tenant |
| Exchange attribute do trusted WebFilter tạo | Tin theo contract nội bộ | Không cho untrusted caller ghi đè |
| Session attribute | Server-side state theo session policy | CSRF/session fixation và authz vẫn cần |
| `Forwarded`/`X-Forwarded-For` | Có thể bị giả mạo | Config trust proxy, không tin trực tiếp |
| JSON/form/multipart body | Không | Validation, size limits, authorization |

Không tạo URL redirect/external HTTP fetch từ `Host`/`Forwarded` raw mà không kiểm soát: có nguy cơ host-header poisoning, open redirect hoặc SSRF.

### Privacy trong logs và response

Không log/echo toàn bộ `exchange.getRequest().getHeaders()`, `getCookies()` hoặc raw query, vì có thể chứa tokens, email, số điện thoại, session IDs và PII. `Principal.getName()` cũng có thể là định danh cá nhân; chỉ trả trong endpoint có authorization và mục đích rõ. Demo `/query` echo principal để dạy API, không dùng nguyên trạng cho production public.

Header như `X-Request-Id` do client đưa vào cần giới hạn length/characters để chống log injection hoặc trace spoofing. Server-generated correlation ID tránh một số rủi ro nhưng vẫn không phải một authentication token.

## 24. Những lỗi thường gặp và cách xử lý

1. **Dùng @RequestParam để đọc form-urlencoded body trong WebFlux.** Sai phạm vi; dùng `getFormData`/`@ModelAttribute` phù hợp.
2. **Nhầm WebFlux với Spring MVC Servlet request parameters.** Hai stack không gộp query/form giống nhau.
3. **Quên required=true mặc định.** Thiếu query/header/cookie cần thiết thường gây 400.
4. **Nhầm q thiếu với q rỗng.** Dùng validation nếu blank không được phép.
5. **Cho rằng String→int conversion cũng kiểm giá trị trong range.** `-999` là số nhưng có thể sai domain.
6. **Dùng Map<String,String> cho repeated tags.** Nên dùng MultiValueMap/List và quy định duplicates.
7. **Tự split query sau decode theo '&'/'='.** Data encoded có thể biến thành delimiter.
8. **URLDecoder.decode thêm lần nữa sau binding.** Double-decode và thay đổi ký tự gốc.
9. **Lấy header X-User làm identity đã xác thực.** Header do client tự khai.
10. **Ghi toàn bộ Authorization/Cookie headers ra log.** Lộ credentials.
11. **Lưu session ID vào response DTO.** Rò token/có thể bị chiếm phiên.
12. **Coi HttpOnly cookie là server không đọc được.** HttpOnly chặn JS, không chặn HTTP server.
13. **Lấy Principal.getName làm kết luận user có quyền admin.** Authorization riêng.
14. **Tưởng exchange attribute tồn tại qua nhiều requests.** Nó là request-scoped.
15. **Dùng @RequestAttribute nhưng quên WebFilter tạo giá trị.** Thiếu required attribute gây lỗi.
16. **Dùng getFormData và getRequest().getBody như hai nguồn độc lập.** Body consumption cạnh tranh.
17. **Nghĩ getMultipartData cache cả file content.** Map cache, content của part không được đọc hai lần.
18. **Giữ DataBuffer từ multipart mà không release/consume.** Có thể leak native memory.
19. **Tin FilePart.filename an toàn làm filesystem path.** Rủi ro path traversal/name collision.
20. **Không giới hạn số lượng/kích thước multipart.** Memory/disk exhaustion.
21. **Đọc getPrincipal().block() trong Netty event-loop.** Chặn reactive processing.
22. **Tự subscribe Mono<Principal>/getFormData để mutate shared variable.** Race/lifecycle lỗi.
23. **Dùng WebSession như global cache vô hạn.** Tăng memory và data retention.
24. **Tin Forwarded/X-Forwarded headers từ bất kỳ client.** Host/proxy spoofing.
25. **Gửi principal/session/tracing internal values vào public DTO không kiểm tra mục đích.** Rò identity/metadata.
26. **Không test 400/415 trong controller argument handling.** Happy path chưa đủ.
27. **Không phân biệt JSON @RequestBody và multipart @RequestPart.** Codec/validation khác.
28. **Ghi response body thủ công rồi cũng return ResponseEntity/Mono DTO.** Gây double-writing.
29. **Không xử lý client disconnect/cancel với upload dài.** Leak file handles/buffers.
30. **Cho rằng ServerWebExchange an toàn để giữ sau HTTP completion.** Request/response lifecycle hữu hạn.
31. **Dùng RequestParam Optional<T> nhưng quên kiểm empty.** Null/empty contract cần rõ.
32. **Hardcode tên header case-sensitive trong logic tự viết.** HTTP header names case-insensitive.
33. **Không kiểm security khi echo cookies/query của người khác.** Inadvertent data exposure.
34. **Dựa vào raw URI làm tenant/authorization identity.** Cần canonical path và domain policy.
35. **Kỳ vọng @RequestParam không thể xuất hiện trong POST.** Query param vẫn tồn tại ở mọi HTTP method.

## 25. Checklist triển khai production

### Binding và validation

- [ ] Biết rõ parameter nào thuộc query, path, header, cookie, JSON/form/multipart body?
- [ ] `@RequestParam` trong WebFlux chỉ dùng cho query, không nhầm Servlet behavior?
- [ ] `required=false`/`defaultValue`/`Optional` có hợp đồng thiếu/rỗng rõ?
- [ ] Có kiểm format/range/size các giá trị và xử lý conversion errors thành 400?
- [ ] Repeated query params được giữ hoặc từ chối theo policy?
- [ ] Không decode URL thêm lần nữa sau Spring binding?
- [ ] Dùng `HttpHeaders`/`MultiValueMap` có kiểm redaction khi log?
- [ ] Cookie flags, data integrity và CSRF/authorization được cấu hình?

### Exchange, Principal và session

- [ ] Principal đến từ authentication chain đáng tin, không từ header tự khai?
- [ ] Authorization kiểm đúng actor, tenant, resource và action?
- [ ] Xử lý `Mono.empty()`/anonymous một cách đúng với public/protected route?
- [ ] Request attributes có key namespace và producer rõ ràng?
- [ ] Không lưu exchange/session ID/HTTP objects ngoài lifetime phù hợp?
- [ ] Reactor Context/security context không bị mất do tự subscribe?
- [ ] WebSession không giữ PII/credential ngoài chính sách retention?
- [ ] Không tin raw forwarded headers từ proxy chưa cấu hình?

### Bodies và tài nguyên

- [ ] JSON dùng `@RequestBody Mono<T>` và decoder phù hợp?
- [ ] Form-urlencoded dùng `getFormData`/binding, không chồng chéo raw body?
- [ ] Multipart có giới hạn file size/count, storage, scanning và cleanup?
- [ ] Part content/DataBuffer được consume/release chính xác, không đọc lặp?
- [ ] HTTP response chưa commit trước khi ghi header/status?
- [ ] Client disconnect/cancel có cleanup?
- [ ] Không block event-loop với `block()`/JDBC/I/O?

### Test

- [ ] `WebTestClient` kiểm query/header/cookie/trace và data conversion?
- [ ] Test form queryName khác formName, repeated values?
- [ ] Test principal unauthenticated/authenticated với security config thật?
- [ ] Test JSON input, 400, 415 và malformed body?
- [ ] Test upload metadata/size limit và rejected large payload ở môi trường thực?
- [ ] Test qua proxy/ingress để xác minh trusted forwarded headers?
- [ ] Logs không chứa Authorization/Cookie/session/query secrets?

## 26. Tự kiểm tra kiến thức

**1.** Trong WebFlux, `@RequestParam("name")` có tự đọc field `name` trong form body không?

**Đáp án:** Không. WebFlux `@RequestParam` chỉ bind query. Dùng `getFormData()`/`@ModelAttribute` phù hợp với form body.

**2.** `@CookieValue` và `@RequestHeader("Cookie")` có giống nhau không?

**Đáp án:** Cả hai truy cập dữ liệu liên quan cookies, nhưng `@CookieValue` bind một cookie theo tên; `@RequestHeader` lấy raw header string. Với nhiều cookie và decoding, ưu tiên API cookie-aware.

**3.** `@RequestParam("page") int page` nhận `page=abc` ra sao?

**Đáp án:** Conversion thất bại; thường HTTP 400 theo error handling chuẩn.

**4.** `getRequest().getQueryParams()` nên có kiểu map gì?

**Đáp án:** `MultiValueMap<String,String>` để giữ repeated query parameters.

**5.** Principal có thể lấy trực tiếp từ `X-User` client gửi không?

**Đáp án:** Không. Chỉ nhận identity từ trusted auth middleware/Spring Security chain và vẫn phải kiểm quyền.

**6.** Vì sao `exchange.getPrincipal()` trả `Mono<Principal>`?

**Đáp án:** WebFlux dùng reactive abstraction; principal có thể có hoặc không, được nối vào pipeline mà không cần block.

**7.** `@RequestAttribute` lấy ở đâu?

**Đáp án:** Exchange/request attribute đã được set trước, ví dụ bởi `WebFilter`; không phải query/header cùng tên.

**8.** `getFormData()` có thể gọi nhiều lần không?

**Đáp án:** Trong `DefaultServerWebExchange`, form body được parse/cached thành map; gọi lại API này lấy map cache, không chứng minh raw body có thể consume hai lần.

**9.** `getMultipartData()` có cache contents mỗi file part không?

**Đáp án:** Không. Map/parts được cache theo contract, còn nội dung từng Part không được đảm bảo đọc lại.

**10.** Có thể dùng `WebSession.getId()` trả về public JSON như minh họa không?

**Đáp án:** Không nên. Session ID/token là dữ liệu nhạy cảm có thể dẫn tới session hijacking.

**11.** Nếu không có principal và route public, nên làm gì?

**Đáp án:** Xử lý Mono empty/fallback rõ ràng; với route protected thì security filter phải đòi authentication, không tự coi anonymous là user đã có quyền.

**12.** ServerWebExchange attributes tồn tại bao lâu?

**Đáp án:** Trong lifetime của một HTTP exchange, không tự kéo dài sang request khác.

**13.** Vì sao không đọc `getRequest().getBody()` rồi `block()`?

**Đáp án:** Chặn event loop, có thể consume một lần body stream và phá decoder/memory budget; dùng reactive reader/binding APIs.

**14.** Header `Forwarded` có thể tin tuyệt đối để xác định host/protocol ban đầu không?

**Đáp án:** Không. Cần trusted proxy configuration và validation tránh spoofing.

**15.** Khi nào dùng `ServerWebExchange` thay vì chỉ annotations?

**Đáp án:** Khi cần nhiều thành phần request/response, attributes, session, principal, form/multipart map hay chức năng hạ tầng mà annotation binding không diễn đạt tốt bằng một vài tham số rõ ràng.

## 27. Tài liệu tham khảo

- Spring Framework 6.2 — WebFlux Controller Method Arguments: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/arguments.html
- Spring Framework 6.2 — `@RequestParam` query-only ở WebFlux: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestparam.html
- Spring Framework 6.2 — `@RequestHeader`: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestheader.html
- Spring Framework 6.2 — `@CookieValue`: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/cookievalue.html
- Spring Framework 6.2 — `@RequestAttribute`: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestattrib.html
- Spring Framework 6.2 — `@ModelAttribute` / Data Binding: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/modelattrib-method-args.html
- Spring Framework 6.2 — Multipart Content: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/multipart-forms.html
- Spring Framework 6.2 — `@RequestBody`: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestbody.html
- Spring Framework 6.2 — WebHandler API and form/multipart parsing: https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html
- Spring Framework 6.2 — ServerWebExchange API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/server/ServerWebExchange.html
- Spring Framework 6.2 — ServerHttpRequest API: https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/http/server/reactive/ServerHttpRequest.html
- Spring Framework — WebTestClient: https://docs.spring.io/spring-framework/reference/testing/webtestclient.html
- Spring Security — Reactive WebFlux Security: https://docs.spring.io/spring-security/reference/reactive/index.html
- Spring Security — WebFlux testing: https://docs.spring.io/spring-security/reference/reactive/test/web/index.html
- Reactor Core reference: https://projectreactor.io/docs/core/release/reference/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-003">@RequestBody với Mono/Flux và phản hồi reactive qua ResponseEntity</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestBody với Mono/Flux và phản hồi reactive qua ResponseEntity trong Spring WebFlux

## 1. Bài toán thực tế và mục tiêu

Một HTTP API Spring WebFlux thường cần nhận JSON từ client, xử lý bất đồng bộ, rồi quyết định HTTP status, headers và body. Ba câu hỏi dễ bị lẫn là:

1. `@RequestBody Mono<NewOrder>` khác `@RequestBody NewOrder` ở cách đọc và xử lý body như thế nào?
2. `@RequestBody Flux<Event>` có nghĩa "đọc cả một JSON array" hay "xử lý từng bản ghi trong stream"?
3. `ResponseEntity<Mono<Order>>`, `Mono<ResponseEntity<Order>>` và `ResponseEntity<Flux<Order>>` khác nhau thế nào về **thời điểm biết HTTP status/headers**, việc ghi body, streaming và lỗi?

Nếu chỉ nhìn thấy chữ `Mono` hoặc `Flux` rồi kết luận mọi thao tác là non-blocking, API rất dễ gặp lỗi: block event loop, trả HTTP 200 trước khi biết resource có tồn tại, decode sai Content-Type, ghi response khi headers đã commit, hoặc giữ quá nhiều dữ liệu trong RAM.

**Phạm vi:** Controller annotation của **Spring WebFlux 6.1.x**, **Java 17** và **Spring Boot 3.3.5**. Đây là bài thuộc nhóm **Controller WebFlux**, không phải Spring MVC Servlet. Ta dùng package `org.springframework.web.bind.annotation` cho `@RequestBody`, Reactor `reactor.core.publisher.Mono`/`Flux`, `ResponseEntity` từ Spring HTTP, HTTP codecs của WebFlux và `WebTestClient`.

### Kết quả học tập

Sau bài, bạn có thể:

- Đọc body reactive bằng `@RequestBody Mono<T>`/`Flux<T>` và biết khi nào dùng DTO trực tiếp.
- Hiểu role `HttpMessageReader`, Jackson decoder, `DataBuffer`, backpressure và request body one-shot.
- Phân biệt `application/json`, `application/x-ndjson` và các chuẩn streaming JSON.
- Chọn đúng một trong các kiểu response `ResponseEntity<T>`, `Mono<T>`, `ResponseEntity<Mono<T>>`, `ResponseEntity<Flux<T>>`, `Mono<ResponseEntity<T>>` và `Mono<ResponseEntity<Flux<T>>>`.
- Tạo HTTP 201 + `Location` sau khi lưu thành công, hoặc trả 404 đúng khi không tìm thấy.
- Dùng `@Valid` với body reactive và nhận biết `WebExchangeBindException`.
- Không vô tình nuốt lỗi bằng `onErrorResume`, không làm sai HTTP 4xx/5xx và không rò rỉ dữ liệu.
- Xây ứng dụng Boot WebFlux với JSON POST, NDJSON batch input, dynamic status và output reactive, kèm WebTestClient tests.
- Xác định ranh giới về cancellation, buffering, timeout, streaming response commit, Spring Security và persistence.

## 2. Hai hướng dữ liệu của một HTTP exchange

Một endpoint có hai pipelines độc lập nhưng phối hợp:

```text
HTTP request bytes
       |
       v
Reactive HTTP server adapter (Reactor Netty...)
       |
       v
ServerHttpRequest.getBody() : Flux<DataBuffer>
       |
       v
HttpMessageReader + Decoder (ví dụ Jackson JSON)
       |
       +--> @RequestBody Mono<DTO> (0..1 DTO)
       |
       +--> @RequestBody Flux<DTO> (0..N DTOs)
       |
       v
Controller -> service -> Mono/Flux result
       |
       v
HandlerResultHandler / HttpMessageWriter + Encoder
       |
       v
ServerHttpResponse headers/status + body publisher
       |
       v
HTTP client
```

`@RequestBody` không tự đọc bytes bằng `InputStream`/`HttpServletRequest` như trong Servlet MVC. WebFlux nhận body bằng `Publisher<DataBuffer>` và decode thông qua `HttpMessageReader` phù hợp với Content-Type.

Điểm quan trọng:

- **Request body** thuộc HTTP exchange, thông thường được consume **một lần**. Không tự subscribe `exchange.getRequest().getBody()` ở filter rồi để controller đọc tiếp.
- **Mono<T>** có tối đa một phần tử, có thể complete empty hoặc error.
- **Flux<T>** có từ 0 đến nhiều phần tử, có thể complete hoặc chạy lâu tùy source.
- **Reactive I/O** nghĩa kết hợp publisher/demand/cancellation đúng; không phải chỉ thay tên kiểu Java.
- Khi trả response, status/headers và publisher body có những thời điểm quyết định khác nhau. Đây là lý do có nhiều dạng `ResponseEntity` chứa publisher.

## 3. @RequestBody DTO trực tiếp, Mono<T>, Flux<T>

### 3.1. DTO trực tiếp

```java
@PostMapping("/items")
public Mono<Item> create(@RequestBody NewItem input) {
    return store.create(input);
}
```

Đoạn này là snippet, `Item`/`NewItem`/`store` sẽ được định nghĩa đầy đủ trong project bên dưới. Với tham số `NewItem` trực tiếp, WebFlux phải decode body trước khi gọi method controller. Kiểu trả về reactive vẫn có thể áp dụng cho phần xử lý sau đó.

Cách viết này tiện cho request nhỏ có một object và cần binding/validation theo annotated controller. Nó **không** tự biến toàn bộ hoạt động đọc body sang "blocking". WebFlux codec vẫn đọc request theo cơ chế reactive; method argument resolution cung cấp DTO sau khi decode.

### 3.2. @RequestBody Mono<T>

```java
@PostMapping("/items")
public Mono<Item> create(
        @RequestBody Mono<NewItem> incoming) {
    return incoming.flatMap(store::create);
}
```

Lúc controller method nhận `incoming`, đó là một `Mono<NewItem>` mô tả việc decode body. Method chủ động ghép các bước bằng `map`/`flatMap` và **trả publisher cho framework subscribe**.

- `flatMap` là phù hợp khi `store.create` trả `Mono<Item>`.
- Nếu `store.create` là method đồng bộ trả `Item`, `map(store::createSync)` có thể dùng, nhưng blocking call trong `map` vẫn có thể block event loop.
- Không gọi `incoming.block()` để lấy `NewItem` rồi mới xử lý.
- Không gọi `incoming.subscribe()` rồi trả một Mono khác, vì có thể làm body được consume ngoài lifecycle của HTTP request.

`Mono<NewItem>` không có nghĩa có một extra HTTP request hay một thread riêng đang chờ. Nó chỉ là cách biểu diễn result của quá trình decode.

### 3.3. @RequestBody Flux<T>

```java
@PostMapping(path = "/events",
        consumes = MediaType.APPLICATION_NDJSON_VALUE)
public Mono<Long> accept(@RequestBody Flux<Event> events) {
    return events
            .flatMap(eventStore::save)
            .count();
}
```

Đây là snippet có `Event`, `eventStore` giả định. NDJSON (`application/x-ndjson`) đưa **một JSON value trên một dòng**, cho phép decoder tạo từng phần tử trong Flux từ request body stream khi hỗ trợ media type.

`Flux<Event>` không phải là yêu cầu framework lưu toàn bộ dữ liệu vào một `List<Event>` rồi mới gọi handler. Với Content-Type streaming phù hợp, decoder có thể phát nhiều `Event` qua thời gian theo dữ liệu đến. Tuy vậy, các buffers, codecs, `flatMap` concurrency, client/proxy và serialization có thể ảnh hưởng mức streaming/backpressure thực tế.

**Không dùng `Flux<T>` như bảo đảm rằng mọi dạng JSON đều phát từng object ngay lập tức.** Một JSON array `[{"x":1},{"x":2}]` với `application/json` có semantics khác NDJSON lines; decoder/content negotiation quyết định cách phát elements và flush. Nếu contract là NDJSON, khai báo Content-Type NDJSON và test bytes/wire format thật.

## 4. application/json vs application/x-ndjson

### JSON object

```http
POST /api/items HTTP/1.1
Content-Type: application/json

{"name":"Sách Spring"}
```

Đây là một document JSON duy nhất. `@RequestBody Mono<NewItem>` thường là lựa chọn tự nhiên.

### JSON array

```http
POST /api/items/batch HTTP/1.1
Content-Type: application/json

[{"name":"A"},{"name":"B"}]
```

Nếu business operation cần toàn bộ list/transactional validation, có thể dùng `@RequestBody Mono<List<NewItem>>` hoặc một DTO wrapper, với giới hạn max size. Dùng Flux với JSON array tùy codec semantics nhưng **đừng đồng nhất** với line-delimited streaming.

### NDJSON

```http
POST /api/items/batch HTTP/1.1
Content-Type: application/x-ndjson

{"name":"A"}
{"name":"B"}
```

NDJSON có mỗi dòng một JSON object, thường thuận lợi cho streaming và backpressure. Đường response `application/x-ndjson` cũng cho phép server ghi các JSON objects thành từng dòng; khác với `application/json` thường là một JSON array/document hợp lệ.

Trong Spring, `MediaType.APPLICATION_NDJSON` và `MediaType.APPLICATION_NDJSON_VALUE` đại diện media type NDJSON. `application/stream+json` là media type từng được dùng trong hệ sinh thái Spring, nhưng với API mới nên ưu tiên NDJSON khi client/server đồng ý.

### Khi nào không nên stream?

- Cần transaction "tất cả thành công hoặc không gì cả".
- Cần validate toàn batch trước khi ghi dữ liệu.
- Cần biết tổng record count và trả status cuối.
- Client/proxy phải có response JSON document duy nhất.
- Có giới hạn bộ nhớ và độ phức tạp thích hợp cho dữ liệu nhỏ.

Khi batch có vài chục items, decode thành list giới hạn có thể đơn giản hơn streaming. Streaming có giá trị khi tránh giữ toàn bộ payload, xử lý incremental/cancellation và client cần nhận phần tử sớm.

## 5. HttpMessageReader, Decoder và lỗi body

WebFlux `@RequestBody` dùng `HttpMessageReader` và `Decoder` từ cấu hình codecs; mặc định Boot WebFlux với Jackson đọc JSON thành record/DTO. Dựa vào `Content-Type`, framework chọn reader hỗ trợ type và media type.

Các lỗi cần phân biệt:

| Tình huống | Ý nghĩa |
| --- | --- |
| JSON malformed | Không decode được body; thông thường 400 |
| `Content-Type: text/plain` nhưng handler đợi JSON | Có thể bị 415 Unsupported Media Type |
| Body required nhưng empty | Có thể 400 khi `@RequestBody(required=true)` áp dụng |
| DTO thiếu field bắt buộc theo Bean Validation | 400 nếu có `@Valid`/validation phù hợp |
| DTO đọc được nhưng business resource không tồn tại | 404 theo business/response mapping |
| Body quá lớn | Có thể gặp codec buffer limit hoặc 413 policy |
| Client disconnect | Cancellation/network errors; không nhất thiết tạo JSON response |
| Decoder gặp lỗi giữa NDJSON stream | Publisher phát error, có thể ngừng đọc các phần còn lại |

Không mặc định exact exception class cho mọi lỗi decode; `ServerWebInputException` và các subclasses/converter exceptions có thể xuất hiện tùy Spring configuration. Bean Validation cho annotated `@RequestBody` thường báo `WebExchangeBindException`. Nếu có method-level validation do constraints trực tiếp trên parameters, có thể gặp `HandlerMethodValidationException`.

## 6. Bean Validation với Mono<T>/Flux<T>

Giả sử:

```java
public record NewItem(
        @NotBlank
        @Size(max = 80)
        String name) {}
```

Trên controller WebFlux, có thể dùng:

```java
@PostMapping
public Mono<ResponseEntity<Item>> create(
        @Valid @RequestBody Mono<NewItem> request) {
    return request
            .flatMap(store::create)
            .map(item -> ResponseEntity
                    .created(URI.create("/api/items/" + item.id()))
                    .body(item));
}
```

Đây là snippet cần Java record, store và imports, được viết đầy đủ ở mục 13.

- `@Valid` chỉ yêu cầu kiểm tra constraints nested object; nó **không** là một constraint như `@NotBlank`.
- Với `Mono<NewItem>`, validation có thể phát error **trong Mono** khi giá trị được decode. Spring MVC/WebFlux argument-resolution khác nhau nên đừng dùng tên exception của Servlet MVC thay vì WebFlux.
- Lỗi validation thường được chuyển thành **HTTP 400 Bad Request**, trừ khi bạn thay mapping.
- Nếu bạn thêm constraints trực tiếp trên method parameters (ví dụ `@Min` cho numeric query argument) trong Spring 6.1, method validation có thể phát `HandlerMethodValidationException`.
- Với `Flux<NewItem>`, chú ý từng phần tử của streaming body và khả năng lỗi xảy ra **sau khi response bắt đầu**. Với streaming output, không bảo đảm có thể sửa status thành 400 khi đã commit một phần.

**Không xử lý validation bằng cách gọi `block()`.** Nếu muốn phản hồi error schema tùy chỉnh, sử dụng `@RestControllerAdvice`/`@ExceptionHandler` hoặc error operators **bên trong reactive chain** với scope và exception class chính xác.

### @Valid không thay thế business validation

Tên không rỗng chưa chứng minh người dùng được quyền tạo resource, không bị spam và không chứa domain value ngoài policy. Xác thực quyền, idempotency, rate limit, uniqueness, input length và SQL injection defense cần triển khai riêng tại service/persistence/security boundaries.

## 7. ResponseEntity<T> nghĩa là gì trong WebFlux?

`ResponseEntity<T>` chứa:
- HTTP status code.
- Headers như `Location`, `ETag`, `Cache-Control`, `Content-Type`, `X-Request-ID`.
- Body `T` (có thể là publisher).

`@RestController` trả DTO trực tiếp thường ngụ ý status 200. Khi cần 201, 202, 204, 404 hoặc header cụ thể, dùng `ResponseEntity`.

```java
return ResponseEntity
        .status(HttpStatus.CREATED)
        .location(URI.create("/api/items/42"))
        .body(new Item(42, "Java"));
```

Đây là snippet có record `Item` đã định nghĩa. Nó đặt status/headers trước khi body được serialize.

Trong WebFlux, `ResponseEntity` có thể bao publishers; **vị trí của Mono/Flux bên trong hay bên ngoài ResponseEntity quyết định thời điểm status/headers được biết**.

## 8. Bốn mẫu ResponseEntity reactive quan trọng nhất

### 8.1. ResponseEntity<Mono<T>>

```java
public ResponseEntity<Mono<Item>> accepted() {
    Mono<Item> body = service.computeLater();
    return ResponseEntity.accepted()
            .header("X-Mode", "body-later")
            .body(body);
}
```

Snippet này chưa runnable vì `service` giả định. Tại lúc method return, **status 202 và X-Mode đã được xác định**. `body` vẫn là publisher có thể phát item hoặc error sau.

Phù hợp khi:
- HTTP status/header chắc chắn ngay khi tạo response.
- Kết quả body có thể sẵn sàng sau.
- Body có tối đa một phần tử.

**Bẫy quan trọng:** Nếu `body` complete empty, status ban đầu vẫn là 202; không tự biến thành 404. Nếu bạn cần status 404 khi resource không có, nên chờ lookup rồi trả `Mono<ResponseEntity<Item>>`.

### 8.2. ResponseEntity<Flux<T>>

```java
public ResponseEntity<Flux<Item>> stream() {
    Flux<Item> body = service.streamItems();
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_NDJSON)
            .body(body);
}
```

Status 200 và Content-Type được chọn **trước**, body phát nhiều items sau. Với NDJSON, client có thể nhận từng JSON line theo nhịp body publisher, tùy buffering/transport.

Khi cần JSON array thông thường, `ResponseEntity<Flux<Item>>` + `application/json` vẫn có thể là lựa chọn. Codec có thể serialize Flux thành một array và flush khác NDJSON. Đừng giả định cứ là `Flux` sẽ có mỗi item là một HTTP chunk; chunk boundaries không phải record boundaries.

### 8.3. Mono<ResponseEntity<T>>

```java
public Mono<ResponseEntity<Item>> find(long id) {
    return repository.find(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

Đây là snippet, `repository.find(id)` trả `Mono<Item>`. **ResponseEntity được tạo sau kết quả lookup**:

- Có item → HTTP 200 và JSON body.
- Empty → HTTP 404 không body.

Ở đây HTTP status **phụ thuộc** vào kết quả asynchronous. Đây thường là lựa chọn đúng cho GET resource đơn lẻ, lookup/permission decisions, tạo resource có thể thất bại, conditional response và business conflict.

### 8.4. Mono<ResponseEntity<Flux<T>>>

```java
public Mono<ResponseEntity<Flux<Item>>> export() {
    return permissionService.check()
            .map(allowed -> {
                if (!allowed) {
                    return ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .<Flux<Item>>build();
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_NDJSON)
                        .body(service.streamItems());
            });
}
```

Snippet với `permissionService`/`service` giả định. **Hai giai đoạn bất đồng bộ:**
1. Mono ngoài quyết định status/headers sau một check async.
2. Flux bên trong cung cấp body sau khi response được chọn.

Phù hợp khi **quyền/status/header phụ thuộc I/O async**, nhưng payload có nhiều phần tử streaming. Không tự thay thế Spring Security/service authorization thật chỉ bằng một check minh họa.

### Bảng tóm tắt

| Kiểu return | Biết status/headers khi nào? | Body | Dùng khi |
| --- | --- | --- | --- |
| `ResponseEntity<T>` | Ngay khi return | Một object/value | Kết quả/status đã sẵn |
| `Mono<T>` | Thường response 200 mặc định, có handling khác | 0..1 value | Không cần status/header tùy điều kiện |
| `Flux<T>` | Thường response 200 mặc định, có handling khác | 0..N values | Danh sách/stream |
| `ResponseEntity<Mono<T>>` | **Trước khi Mono body phát** | 0..1 reactive | Status/header xác định sớm |
| `ResponseEntity<Flux<T>>` | **Trước Flux body phát** | 0..N reactive | Status/header xác định sớm, stream/list |
| `Mono<ResponseEntity<T>>` | **Sau khi Mono ngoài phát** | Một DTO hoặc empty | Status phụ thuộc lookup/async result |
| `Mono<ResponseEntity<Flux<T>>>` | **Sau Mono ngoài, trước Flux body** | Stream sau khi xác thực/chọn status | Quyết định envelope async, body streaming |

**Lưu ý:** "biết status sớm" nói về thời điểm phần mềm đã **chọn status/header**, không khẳng định bytes header đã được gửi qua socket ngay lúc Java method return. HTTP response thường chỉ commit khi body write/complete; thời điểm thực phụ thuộc server, codec và flow.

## 9. Mono.empty(), Flux.empty() và HTTP status

Một lỗi contract thường gặp:

```java
@GetMapping("/{id}")
public ResponseEntity<Mono<Item>> wrong(@PathVariable long id) {
    return ResponseEntity.ok().body(repository.find(id));
}
```

Nếu repository không tìm được item, `Mono.empty()` không tự sinh 404. Response được chọn 200 ngay.

Đúng hơn:

```java
@GetMapping("/{id}")
public Mono<ResponseEntity<Item>> correct(@PathVariable long id) {
    return repository.find(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

Tương tự:
- `Flux.empty()` + 200 là response danh sách rỗng, thường hợp lệ.
- `Mono.empty()` + 204 có thể phù hợp hành động không có body, nhưng phải **chủ động** chọn 204 nếu contract cần.
- `Mono.error(ex)` đi vào reactive error handling; HTTP status phụ thuộc exception resolvers và response commit.
- `onErrorReturn(defaultValue)` có thể che business error, chuyển 5xx thành 200 vô ý nếu không thiết kế.

Không lẫn "Publisher không có phần tử" với "resource không tồn tại" hoặc "user không có quyền".

## 10. HTTP status, Location, Cache-Control và ETag

### 201 Created

Khi POST tạo item thành công, REST contract thường trả:
- `201 Created`.
- `Location: /api/items/42` trỏ tới item mới.
- Body DTO được server tạo, chứa server-owned ID.

Với body reactive:

```java
return body.flatMap(store::create)
        .map(created -> ResponseEntity
                .created(URI.create("/api/items/" + created.id()))
                .body(created));
```

Ở đây Status và Location chỉ được chọn **sau khi create Mono phát thành công**. Nếu create thất bại, không gửi 201 sai.

### 202 Accepted

`202 Accepted` cho biết request được chấp nhận để xử lý nhưng chưa hoàn tất; nếu thực sự có background job thì nên trả job ID/status URL phù hợp. **Đừng** chỉ đặt 202 vì body là Mono và mất vài milliseconds. Một `Mono` hoàn thành trong cùng HTTP request vẫn có thể trả 200/201 bình thường.

### 204 No Content

`ResponseEntity.noContent().build()` tạo 204. Theo HTTP semantics, không gửi body. Đừng gắn `Flux<Item>` body vào 204 rồi kỳ vọng client đọc được stream.

### Cache headers

`ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(...)` hữu ích khi body nhạy cảm; cache policy áp cho response độc lập với Mono/Flux. Nếu danh sách là user-specific, tránh public shared cache làm leak dữ liệu. ETag/conditional GET cần value ổn định và status 304 đúng trước khi body commit; không tạo ETag từ một stream chưa hoàn thành mà không tính chi phí/bộ đệm.

### Response already committed

Với NDJSON:
- Status/header được commit khi bắt đầu response.
- Item thứ ba mới lỗi, trong khi hai dòng đã tới client.
- Server **không bảo đảm** có thể đổi 200 thành 500 ProblemDetail hoặc 400 validation body.
- Client phải biết chính sách incomplete stream/retry/idempotency và giới hạn tác dụng phụ.

Vì vậy nếu cần atomic batch validation và error status cuối, thường phải **hoàn tất kiểm tra trước khi commit** thay vì trả một Flux output ngay.

## 11. Reactive streams, backpressure và serialization

WebFlux dùng Reactive Streams để điều khiển demand từ consumer, không phải bắt mỗi request thread ngủ đợi network.

- `Mono.just(dto)` phát một item khi subscribed.
- `Flux.fromIterable(items)` phát nhiều items.
- `flatMap` có thể cho concurrency và thay đổi ordering; `concatMap` giữ thứ tự tuần tự.
- `collectList()` gom toàn bộ Flux vào bộ nhớ; không phù hợp với input vô hạn hoặc body không giới hạn.
- `buffer(n)` tạo nhóm, cũng tăng memory usage.
- `take(n)` lấy tối đa n items và hủy upstream khi đủ, nhưng nếu dùng sai có thể lặng lẽ bỏ phần còn lại.
- `doOnCancel`/`doFinally` hỗ trợ cleanup khi client disconnect/response cancel.

**Backpressure chỉ hữu ích end-to-end** khi source, decoder, persistence client và HTTP writer cùng tôn trọng demand. Nếu trong controller gọi JDBC đồng bộ hoặc tạo `Flux.create` đẩy vô hạn không bound, việc dùng Flux không cứu hệ thống khỏi nghẽn thread/bộ nhớ.

### Blocking code trong map/flatMap

```java
// PHẢN VÍ DỤ: JDBC blocking trên event loop.
return incoming.map(dto ->
        jdbcRepository.save(dto));
```

Trong WebFlux Netty, operator có thể chạy trên event loop. JDBC đồng bộ có thể block event-loop, kéo chậm nhiều connections khác.

Phương án tạm khi phải dùng blocking dependency:

```java
return incoming.flatMap(dto ->
        Mono.fromCallable(() -> jdbcRepository.save(dto))
                .subscribeOn(Schedulers.boundedElastic()));
```

Đây là snippet với `jdbcRepository` và `Schedulers` imports giả định. `boundedElastic` **chuyển blocking sang bounded workers**, không biến JDBC thành non-blocking driver. Với database thật, cân nhắc R2DBC/non-blocking client phù hợp, quản lý transaction, pool, timeout và backpressure.

## 12. Thiết kế batch NDJSON an toàn

Một `@RequestBody Flux<NewItem>` có thể nhận hàng nghìn JSON lines. Nếu dùng:

```java
return requests.collectList()
        .flatMap(store::saveBatch);
```

không giới hạn kích thước, client có thể đẩy vô hạn làm server tích tụ RAM.

Với một API hỗ trợ tối đa 100 items/batch:
- Chỉ nhận tối đa **101** entries để phát hiện batch quá lớn, sau đó báo lỗi.
- Kiểm tra constraints từng entry trước khi ghi.
- Tạo danh sách kết quả khi tất cả hợp lệ.
- Trả `Mono<ResponseEntity<List<Item>>>` **sau khi** hoàn tất batch.
- Triển khai DB transaction nếu contract yêu cầu all-or-nothing.
- Giới hạn request bytes và connection timeouts ở proxy/runtime vì giới hạn record count không bảo vệ khỏi **một record rất lớn**.

Ví dụ:

```java
return items.take(101)
        .collectList()
        .flatMap(list -> {
            if (list.size() > 100) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "At most 100 items"));
            }
            return store.saveBatch(list);
        });
```

Snippet này trả kiểu do `store.saveBatch` xác định; project đầy đủ ở mục 13 sẽ trả `Mono<ResponseEntity<List<Item>>>`. Khi nguồn có 101 items, `take(101)` đã đủ để phát hiện quá giới hạn, không **âm thầm chấp nhận** 100 đầu. Khi nguồn có nhiều hơn 101, subscription cancel phần sau theo reactive contract. Với HTTP keep-alive/upload streaming, kiểm tra policy drain/cancel connection của server/client khi request bị từ chối sớm.

**Atomicity:** `collectList()` không tạo DB transaction. Nếu code lưu từng item rồi lỗi ở item cuối, các items trước có thể đã được ghi. Demo sẽ có validation trước khi gọi service, và production phải chọn transactional database service nếu cần atomic batch.

## 13. Dự án Maven chạy được: JSON Mono, Flux NDJSON, ResponseEntity

Ứng dụng minh họa:

- `POST /api/items` nhận `@Valid @RequestBody Mono<NewItem>` và trả `Mono<ResponseEntity<Item>>` với 201 + Location sau khi lưu.
- `GET /api/items/{id}` trả `Mono<ResponseEntity<Item>>` chọn 200/404 theo lookup.
- `GET /api/items/accepted` trả `ResponseEntity<Mono<StatusMessage>>` với status/header biết sớm, body phát sau.
- `GET /api/items` trả `ResponseEntity<Flux<Item>>` với `application/json` để tạo một JSON array.
- `GET /api/items/stream` trả `ResponseEntity<Flux<Item>>` với `application/x-ndjson` để stream từng item.
- `POST /api/items/batch` nhận `@Valid @RequestBody Flux<NewItem>` dạng NDJSON, giới hạn 100 items, trả `Mono<ResponseEntity<List<Item>>>`.
- `@RestControllerAdvice` dùng một error body cố định cho `WebExchangeBindException`.
- Tests `WebTestClient` xác minh headers/status/JSON/NDJSON/validation và batch limit.

Dữ liệu nằm trong RAM. Không có database, Spring Security hoặc transaction thực; dùng store để tập trung vào HTTP reactive semantics.

### 13.1. Cây tệp

```text
webflux-body-response-demo/
├── pom.xml
└── src/
    ├── main/java/example/bodyresponse/
    │   ├── BodyResponseApplication.java
    │   ├── NewItem.java
    │   ├── Item.java
    │   ├── StatusMessage.java
    │   ├── ApiError.java
    │   ├── ItemStore.java
    │   ├── ItemController.java
    │   └── ApiErrors.java
    └── test/java/example/bodyresponse/
        └── ItemControllerTest.java
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
    <artifactId>webflux-body-response-demo</artifactId>
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

Chỉ thêm `spring-boot-starter-webflux` (không `spring-boot-starter-web`) để ứng dụng dùng WebFlux reactor stack như bài học. Có Jackson qua WebFlux starter và Jakarta Bean Validation qua validation starter. WebTestClient có trong starter-test.

### 13.3. Spring Boot application

**`src/main/java/example/bodyresponse/BodyResponseApplication.java`**

```java
package example.bodyresponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BodyResponseApplication {
    public static void main(String[] args) {
        SpringApplication.run(BodyResponseApplication.class, args);
    }
}
```

### 13.4. DTOs và response record

**`src/main/java/example/bodyresponse/NewItem.java`**

```java
package example.bodyresponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NewItem(
        @NotBlank
        @Size(max = 80)
        String name) {}
```

**`src/main/java/example/bodyresponse/Item.java`**

```java
package example.bodyresponse;

public record Item(long id, String name) {}
```

**`src/main/java/example/bodyresponse/StatusMessage.java`**

```java
package example.bodyresponse;

public record StatusMessage(String state) {}
```

**`src/main/java/example/bodyresponse/ApiError.java`**

```java
package example.bodyresponse;

public record ApiError(String code) {}
```

Các record dùng Java 17, được Jackson serialize/deserialize. `@NotBlank` kiểm tra null/empty/whitespace; `@Size(max=80)` chỉ kiểm tra độ dài string. Đó là **DTO validation**, chưa phải authorization hoặc SQL/input safety.

### 13.5. Store dùng RAM, API trả Mono/Flux

**`src/main/java/example/bodyresponse/ItemStore.java`**

```java
package example.bodyresponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ItemStore {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, Item> data =
            new ConcurrentHashMap<>();

    private Item save(NewItem input) {
        long id = sequence.incrementAndGet();
        Item item = new Item(id, input.name().strip());
        data.put(id, item);
        return item;
    }

    public Mono<Item> create(NewItem input) {
        return Mono.fromSupplier(() -> save(input));
    }

    public Mono<Item> find(long id) {
        return Mono.defer(() ->
                Mono.justOrEmpty(data.get(id)));
    }

    public Flux<Item> all() {
        return Flux.defer(() -> {
            List<Item> snapshot =
                    new ArrayList<>(data.values());
            snapshot.sort(Comparator.comparingLong(Item::id));
            return Flux.fromIterable(snapshot);
        });
    }

    public Mono<List<Item>> saveBatch(List<NewItem> input) {
        return Mono.fromSupplier(() -> {
            List<Item> saved = new ArrayList<>();
            for (NewItem draft : input) {
                saved.add(save(draft));
            }
            return List.copyOf(saved);
        });
    }
}
```

**Điều cần hiểu:**

1. `Mono.fromSupplier` thực hiện `save` khi Publisher được subscribe, không phải lúc controller tạo Mono.
2. `Mono.defer` chỉ tra Map khi subscribe, hữu ích để quan sát trạng thái mới trong tests.
3. `Flux.defer` tạo snapshot list lúc subscribe và phát items theo thứ tự ID.
4. Không có JDBC/file/network blocking nên các thao tác ngắn trong memory chạy trên event loop không gây wait I/O; nhưng trong app thật dùng blocking DB driver phải offload hoặc chọn reactive data client.
5. `ConcurrentHashMap` và `AtomicLong` giúp demo không hỏng do đồng thời cơ bản, **không thay DB transaction**.
6. `saveBatch` có thể để lại partial writes nếu giữa chừng gặp unexpected runtime failure; production cần transaction/idempotency nếu contract đòi atomicity.
7. Store là Spring `@Service`, tách khỏi HTTP controller để dễ test/business reuse.
8. Không dùng `Thread.sleep` để giả lập non-blocking latency.

### 13.6. REST controller: sáu endpoints minh họa

**`src/main/java/example/bodyresponse/ItemController.java`**

```java
package example.bodyresponse;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemStore store;

    public ItemController(ItemStore store) {
        this.store = store;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Item>> create(
            @Valid @RequestBody Mono<NewItem> request) {

        return request.flatMap(store::create)
                .map(item -> ResponseEntity
                        .created(URI.create(
                                "/api/items/" + item.id()))
                        .body(item));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Item>> find(
            @PathVariable long id) {

        return store.find(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(
                        ResponseEntity.notFound().build());
    }

    @GetMapping("/accepted")
    public ResponseEntity<Mono<StatusMessage>> accepted() {
        Mono<StatusMessage> body =
                Mono.just(new StatusMessage("queued"))
                        .delayElement(Duration.ofMillis(10));

        return ResponseEntity.accepted()
                .header("X-Body-Mode", "mono")
                .body(body);
    }

    @GetMapping
    public ResponseEntity<Flux<Item>> list() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(store.all());
    }

    @GetMapping(
            path = "/stream",
            produces = MediaType.APPLICATION_NDJSON_VALUE)
    public ResponseEntity<Flux<Item>> stream() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(store.all());
    }

    @PostMapping(
            path = "/batch",
            consumes = MediaType.APPLICATION_NDJSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<Item>>> batch(
            @Valid @RequestBody Flux<NewItem> incoming) {

        return incoming.take(101)
                .collectList()
                .flatMap(drafts -> {
                    if (drafts.size() > 100) {
                        return Mono.error(
                                new ResponseStatusException(
                                    HttpStatus.PAYLOAD_TOO_LARGE,
                                    "At most 100 items"));
                    }
                    return store.saveBatch(drafts);
                })
                .map(saved -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(saved));
    }
}
```

### Tại sao mỗi method return kiểu khác nhau?

- **`create`**: chưa biết được `201 Location` cho tới khi item đã được tạo, nên return `Mono<ResponseEntity<Item>>`.
- **`find`**: phải quyết định giữa 200/404 theo `Mono<Item>`, nên cũng return `Mono<ResponseEntity<Item>>`.
- **`accepted`**: status 202 và `X-Body-Mode` đã cố định; body là `Mono<StatusMessage>` có thể phát trễ.
- **`list`**: status/header biết ngay, `Flux<Item>` được serialize thành JSON response thông thường.
- **`stream`**: status/header biết ngay, nhưng Flux được encode theo NDJSON media type, cho phép nhiều JSON lines.
- **`batch`**: NDJSON là input Flux, nhưng ta muốn **biết batch hợp lệ và lưu xong** mới chọn 201, nên return `Mono<ResponseEntity<List<Item>>>`.

**Sáu endpoints này đủ để thấy** vị trí Mono/Flux trong type signature phản ánh business response semantics chứ không chỉ phong cách lập trình.

### Vì sao batch giới hạn 101?

Nếu dùng `incoming.take(100)` rồi lưu, một request có 101 lines có thể bị lặng lẽ bỏ phần tử 101 nhưng vẫn báo thành công. `take(101)` cho phép nhìn thấy phần tử thứ 101 và **từ chối** toàn batch (trước khi gọi `saveBatch`) khi quá giới hạn 100.

Bản demo giới hạn số records; production còn phải đặt max body bytes, max JSON nesting, per-record size, connection timeouts, rate limits và cancellation cleanup. Không giả định `collectList()` an toàn cho input vô hạn.

### Validation trong batch

`@Valid @RequestBody Flux<NewItem>` cho phép validation của từng DTO phát hiện constraint violations qua reactive body handling. Với batch đang gom trước khi ghi, lỗi validation sẽ khiến Publisher fail và `store.saveBatch` không được gọi. Nhưng với một hệ thống streaming handler thực sự lưu từng item ngay khi đọc, các items trước lỗi có thể đã tồn tại; khi cần atomicity phải định nghĩa transaction/compensation policy.

## 14. Error handler riêng cho validation

**`src/main/java/example/bodyresponse/ApiErrors.java`**

```java
package example.bodyresponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> invalidBody(
            WebExchangeBindException error) {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_item"));
    }
}
```

Điểm cần phân biệt:

- `WebExchangeBindException` là lỗi binding/validation trong **WebFlux**, khác `MethodArgumentNotValidException` ở Servlet MVC.
- Body trả client chỉ chứa code ổn định, không leak rejected values, exception stack hoặc internal validation messages.
- Đây chỉ xử lý `WebExchangeBindException`; JSON malformed, `HandlerMethodValidationException`, request size, authorization hay downstream exception còn cần error policy riêng.
- `@RestControllerAdvice` xử lý lỗi trong annotated controller pipeline; `WebExceptionHandler` là abstraction ở outer WebHandler chain, có scope/ordering khác. Không cần tự viết low-level WebExceptionHandler chỉ để map một lỗi validation thông thường.
- Không bắt `Throwable` rồi trả 400 cho mọi thứ; có thể che 500, bỏ qua cancellation hoặc rò rỉ thông tin.

## 15. Chạy Maven và gửi request thật

Cài **JDK 17+** và Maven. Từ thư mục `webflux-body-response-demo` chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Đây là hai lệnh **chạy tuần tự**. Với Boot default, app nghe tại `http://localhost:8080`; server khác/proxy khác có thể thay URL/port.

### 15.1. POST một JSON object, nhận 201 + Location

```bash
curl -i -X POST \
  'http://localhost:8080/api/items' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spring WebFlux"}'
```

Dự kiến (JVM vừa khởi động, chưa có dữ liệu):

```http
HTTP/1.1 201 Created
Location: /api/items/1
Content-Type: application/json

{"id":1,"name":"Spring WebFlux"}
```

`Location` là URI reference tương đối trỏ tới GET endpoint trên cùng origin. Nó không phụ thuộc request Host header chưa được kiểm chứng. Nếu public client cần absolute URL, cấu hình trusted external base/forwarded-header policy đúng; đừng nối raw Host thành redirect.

### 15.2. GET có hoặc không tồn tại

```bash
curl -i 'http://localhost:8080/api/items/1'
curl -i 'http://localhost:8080/api/items/999999'
```

Lần đầu sau POST trả 200 và JSON Item; lần thứ hai trả **404 Not Found** không có item. Status 404 đến từ `Mono<ResponseEntity<Item>>` sau khi `store.find(id)` complete empty, không phải vì body Mono tự sinh 404.

### 15.3. ResponseEntity chứa Mono với status biết sớm

```bash
curl -i 'http://localhost:8080/api/items/accepted'
```

Dự kiến:

```http
HTTP/1.1 202 Accepted
X-Body-Mode: mono
Content-Type: application/json

{"state":"queued"}
```

Status/header đã được controller chọn trước khi delayed Mono phát object. **Không đồng nghĩa một job nền thật đang chạy**; endpoint demo dùng 202 để minh họa HTTP envelope. Nếu triển khai production 202, cần job tracking/processing semantics rõ.

### 15.4. JSON array với ResponseEntity<Flux<Item>>

```bash
curl -i 'http://localhost:8080/api/items'
```

Sau khi tạo item đầu, body JSON dạng:

```json
[{"id":1,"name":"Spring WebFlux"}]
```

Một Flux có nhiều DTO nhưng Content-Type `application/json` ở đây là danh sách theo một JSON document/array. Đừng dựa vào network chunk boundaries để phân tách items.

### 15.5. NDJSON output streaming

```bash
curl -i -N \
  -H 'Accept: application/x-ndjson' \
  'http://localhost:8080/api/items/stream'
```

Nếu có hai items ID 1,2, body gồm những dòng JSON riêng:

```text
{"id":1,"name":"Spring WebFlux"}
{"id":2,"name":"Reactor"}
```

`-N` của curl tắt một số output buffering của curl; server/proxy có thể vẫn buffer tùy configuration. Nếu không có item, response 200 NDJSON có thể có body rỗng. Với transport streaming, flushing và khả năng quan sát từng item phụ thuộc nguồn phát, decoder/encoder và intermediary proxies.

### 15.6. NDJSON input batch

```bash
curl -i -X POST \
  'http://localhost:8080/api/items/batch' \
  -H 'Content-Type: application/x-ndjson' \
  -H 'Accept: application/json' \
  --data-binary $'{"name":"Alpha"}\n{"name":"Beta"}\n'
```

Trong Bash, ký hiệu `$'...'` tạo các newline thật. Windows PowerShell có cú pháp quote/newline khác; tạo một file NDJSON rồi dùng `--data-binary @batch.ndjson` khi cần portability.

Dự kiến HTTP 201 và JSON array chứa hai Item mới có server-owned IDs:

```json
[{"id":3,"name":"Alpha"},{"id":4,"name":"Beta"}]
```

IDs ví dụ giả sử trước đó chỉ tạo hai items. Nếu đã gọi thêm requests/tests hoặc restart server, IDs khác. Không viết client code phụ thuộc vào ID tuyệt đối trong body.

### 15.7. Validation lỗi

```bash
curl -i -X POST \
  'http://localhost:8080/api/items' \
  -H 'Content-Type: application/json' \
  -d '{"name":"   "}'
```

Dự kiến `400 Bad Request` và error body:

```json
{"code":"invalid_item"}
```

Điều này xảy ra vì record `NewItem` có `@NotBlank` và controller `@Valid @RequestBody Mono<NewItem>`. Nếu chuyển sang plain `@RequestBody Mono<NewItem>` mà không có validation nơi khác, body có thể qua được dù name không hợp lệ, dẫn tới business data sai.

## 16. WebTestClient integration tests

Để bảo đảm không chỉ có code snippets mà còn xác minh HTTP contract, dùng `@SpringBootTest(webEnvironment=RANDOM_PORT)` với `WebTestClient`. Các tests không phụ thuộc ID cố định hoặc thứ tự thực thi.

**`src/test/java/example/bodyresponse/ItemControllerTest.java`**

```java
package example.bodyresponse;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ItemControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void monoRequestBodyCreates201AndLocation() {
        String location = client.post()
                .uri("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NewItem("Spring"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists(HttpHeaders.LOCATION)
                .expectBody(Item.class)
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        assertNotNull(location);
        assertTrue(location.matches("/api/items/[1-9][0-9]*"));

        client.get().uri(location).exchange()
              .expectStatus().isOk()
              .expectBody()
              .jsonPath("$.name").isEqualTo("Spring");
    }

    @Test
    void validationForMonoBodyProduces400() {
        client.post()
              .uri("/api/items")
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(new NewItem("   "))
              .exchange()
              .expectStatus().isBadRequest()
              .expectBody()
              .jsonPath("$.code").isEqualTo("invalid_item");
    }

    @Test
    void monoResponseEntityCanChoose404() {
        client.get()
              .uri("/api/items/999999999")
              .exchange()
              .expectStatus().isNotFound();
    }

    @Test
    void entityWithMonoHasEarlyStatusAndHeaders() {
        client.get().uri("/api/items/accepted")
              .exchange()
              .expectStatus().isAccepted()
              .expectHeader().valueEquals(
                      "X-Body-Mode", "mono")
              .expectBody()
              .jsonPath("$.state").isEqualTo("queued");
    }

    @Test
    void entityWithFluxCanProduceJsonArray() {
        client.get().uri("/api/items")
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(
                      MediaType.APPLICATION_JSON)
              .expectBody()
              .jsonPath("$").isArray();
    }

    @Test
    void fluxInputSupportsNdjsonBatch() {
        String ndjson =
                "{\"name\":\"Kotlin\"}\n"
                + "{\"name\":\"Reactor\"}\n";

        client.post()
              .uri("/api/items/batch")
              .contentType(MediaType.APPLICATION_NDJSON)
              .bodyValue(ndjson)
              .exchange()
              .expectStatus().isCreated()
              .expectBody()
              .jsonPath("$").isArray()
              .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void ndjsonOutputHasDeclaredStreamingMediaType() {
        client.get().uri("/api/items/stream")
              .accept(MediaType.APPLICATION_NDJSON)
              .exchange()
              .expectStatus().isOk()
              .expectHeader().contentTypeCompatibleWith(
                      MediaType.APPLICATION_NDJSON);
    }
}
```

Bộ test có **7 test methods**.

**Đặc biệt:** Test đầu gọi POST, lấy header Location thực rồi GET lại, không hardcode ID 1. Những tests khác chỉ kiểm tra cấu trúc JSON array, nên không phụ thuộc số lượng items do các tests khác tạo. Test NDJSON input dùng raw body text chứa hai JSON lines; không nhầm nó với JSON string literal encoded thành `"..."`.

**Lưu ý static QA:** Bộ tests được soạn để dùng APIs Spring WebFlux/Spring Boot 3.3.5. `@AutoConfigureWebTestClient` có thể dùng với `RANDOM_PORT` để test reactive server. Mã này chưa được chạy Maven trong phạm vi Google Sheets; trước khi dùng production, chạy đầy đủ `mvn test`/integration tests với đúng toolchain.

### Mở rộng tests hữu ích

Bổ sung:
- Body rỗng với `@RequestBody(required=true)`.
- Bad Content-Type / unsupported media.
- JSON malformed và field type mismatch.
- 100 vs 101 NDJSON records.
- Validation lỗi ở element thứ hai khi batch đã đọc phần tử đầu.
- Client cancel giữa NDJSON stream.
- Slow consumer/response buffer/backpressure.
- Reactor Context/trace ID propagation.
- Spring Security auth/authorization/CSRF/CORS.
- Large body, max in-memory size, response timeout.
- R2DBC or other truly non-blocking database client behavior.

## 17. Một vấn đề nhỏ về WebTestClient và BodyExtractors

`WebTestClient` có nhiều cách assert response body:

- `expectBody(Item.class)` cho một object.
- `expectBodyList(Item.class)` cho list/array JSON.
- `expectBody().jsonPath(...)` cho JSON tree.
- `returnResult(Item.class).getResponseBody()` để consume streamed Flux items với Reactor operators/StepVerifier.
- `exchange()` bắt đầu HTTP request/response exchange.

Với NDJSON streaming dài, test `expectBodyList` có thể **chờ stream complete**; nếu SSE/Flux vô hạn, test không nên đợi toàn bộ publisher. Dùng `returnResult(Item.class)` và `StepVerifier`/`take(n)`/timeouts, bảo đảm cancellation và dọn subscription sau test.

### Không dùng HTTP status để đo từ số lượng body items

`Flux.empty()` có thể là kết quả hợp lệ với 200. Với resource không tồn tại, status nên được quyết định dựa vào lookup/metadata trước khi trả response (thường `Mono<ResponseEntity<...>>`). Với stream đang chạy, status không thể thay đổi cho mỗi element sau khi đã commit.

## 18. Chọn Mono hay Flux ở request body

| Đầu vào | Chọn loại nào? | Lý do |
| --- | --- | --- |
| Một JSON object nhỏ | `Mono<DTO>` hoặc DTO trực tiếp | Tối đa một object |
| Một JSON array nhỏ, atomic batch | `Mono<List<DTO>>`/wrapper DTO | Validate toàn bộ trước khi ghi |
| NDJSON nhiều records | `Flux<DTO>` | Phát từng object, hỗ trợ backpressure |
| SSE input | Không phổ biến cho POST; chọn protocol rõ | SSE thường là server→client |
| Binary upload lớn | `Flux<DataBuffer>`/file upload APIs | Quản lý pooled buffers, size, cancel |
| Multipart | WebFlux `@RequestPart`/`FilePart`/`Part` | Có API riêng, không coi là JSON DTO |
| Form URL encoded | WebFlux form data APIs | Media type/decoder khác JSON |
| Client gửi 0 bytes | Policy `required`/optional rõ | Mono empty khác validation lỗi |
| Client gửi malformed JSON | Error handling, 400 | Không biến thành default DTO âm thầm |

Với media type `application/json` truyền một object, không cần dùng `Flux<T>` chỉ vì ứng dụng đã reactive. Dùng đúng **cardinality contract** thường quan trọng hơn việc "càng nhiều Flux càng tốt".

## 19. Chọn ResponseEntity reactive dựa trên quyết định status

Một cách hỏi ngắn:

**Status/headers phụ thuộc vào kết quả bất đồng bộ không?**

- **Không** → `ResponseEntity<Mono<T>>` hoặc `ResponseEntity<Flux<T>>`. Nếu body sẵn thì `ResponseEntity<T>`.
- **Có** → `Mono<ResponseEntity<T>>`.
- **Có, và body tiếp tục streaming sau khi envelope đã chọn** → `Mono<ResponseEntity<Flux<T>>>`.

Ví dụ:
- `GET /items/{id}` phải 404 nếu không có: `Mono<ResponseEntity<Item>>`.
- `GET /stream` luôn 200 NDJSON: `ResponseEntity<Flux<Item>>`.
- `POST /items` cần ID/Location sau khi tạo: `Mono<ResponseEntity<Item>>`.
- `GET /long-operation` luôn 202, body trạng thái phát muộn: `ResponseEntity<Mono<StatusMessage>>` (nhưng cần job semantics nếu thực sự async background).
- `GET /export` cần check permissions/quota trước rồi mới stream: `Mono<ResponseEntity<Flux<byte[]>>>` hoặc phù hợp hơn stream DataBuffer/Resource theo codec, kèm cancellation/backpressure tests.

### Vì sao ResponseEntity<Mono<T>> không tự thay 200 bằng 404?

Lúc `ResponseEntity.ok().body(sourceMono)` được tạo, HTTP 200 đã được quyết định. Khi `sourceMono` complete empty, framework không thể "tự hiểu" đây là missing resource. Nếu source phát `ResponseStatusException` trước commit, framework có thể đổi status tùy resolver, nhưng dựa vào error như phép thay thế cho conditional success/absence là cách thiết kế API khó đoán. Với missing bình thường, chọn `Mono<ResponseEntity<T>>`.

## 20. Error operators: đâu là chỗ xử lý lỗi đúng?

Trong một pipeline:

```java
return incoming.flatMap(store::create)
        .onErrorResume(DuplicateItemException.class, e ->
                Mono.error(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Duplicate item")));
```

Đây là snippet với `DuplicateItemException` và `store` giả định. Nó chuyển **một lỗi domain đã biết** thành HTTP 409, không biến mọi Exception thành 409.

Sai:

```java
return incoming
        .onErrorReturn(new NewItem("default"))
        .flatMap(store::create);
```

Nếu JSON malformed, validation fail hoặc body thiếu, đoạn trên có thể âm thầm tạo item mặc định và trả 201 — thay đổi ý nghĩa HTTP request, nguy hiểm cho dữ liệu. Không dùng `onErrorReturn` để che invalid input.

### Khi HTTP response đã commit

`ResponseEntity<Flux<T>>` cho stream dài có thể đã gửi response 200 và vài items. Nếu item tiếp theo báo error:
- App không thể bảo đảm trả JSON 500 sạch.
- HTTP connection có thể kết thúc sớm hoặc client thấy stream bị gián đoạn.
- Cần retry/resume semantics ở protocol level nếu cần.
- Logging và tracing cần phân biệt partial response từ full success.
- Không retry tự động POST non-idempotent ở reactive operator nếu có side effects.

### Exceptions trong WebFlux

`@RestControllerAdvice`/`@ExceptionHandler` xử lý exception trong annotated endpoint flow; `WebExceptionHandler` ở outer WebHandler chain xử lý errors còn propagate. `ResponseStatusException` có thể được framework map thành status. Các thành phần có thể được cấu hình theo order; không tự khai một WebExceptionHandler "catch-all" rồi ẩn mọi lỗi thành 400.

## 21. Content negotiation, 406/415 và streaming codecs

- `Content-Type` của request chỉ **format dữ liệu gửi tới server**.
- `Accept` của request cho biết **format client muốn nhận**.
- `consumes` trong `@PostMapping` giới hạn request media type.
- `produces` trong `@GetMapping` giới hạn response media type.
- `ResponseEntity.contentType(...)` đặt response Content-Type.

Ví dụ:

```java
@GetMapping(path="/stream",
            produces=MediaType.APPLICATION_NDJSON_VALUE)
public ResponseEntity<Flux<Item>> stream() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_NDJSON)
            .body(store.all());
}
```

Nếu client gửi `Accept: text/plain` và endpoint chỉ `produces=application/x-ndjson`, có thể gặp 406 (Not Acceptable) theo content negotiation. Nếu client POST `Content-Type: text/plain` tới handler chỉ consumes JSON, có thể gặp 415. Các status chính xác còn phụ thuộc mapping, codecs, exception handling và framework configuration; kiểm thử thực tế thay vì chỉ suy đoán.

## 22. Request body one-shot, filter logging và DataBuffer

`ServerHttpRequest.getBody()` trả `Flux<DataBuffer>`. Một WebFilter muốn log raw request body bằng cách gọi `subscribe()` có thể tiêu thụ body trước khi `@RequestBody Mono<T>` đọc, gây lỗi.

Phản ví dụ:

```java
exchange.getRequest().getBody().subscribe(buffer ->
        unsafeLog(buffer));
return chain.filter(exchange);
```

Đây là pseudocode; `unsafeLog` không định nghĩa vì bản thân pattern là sai. Hậu quả:
- Hai subscribers tranh cùng network stream.
- Body reader sau có thể nhận empty/malformed data.
- Passwords, tokens, PII bị ghi logs.
- Pooled DataBuffers có thể leak nếu không release đúng.
- Reactive cancellation/backpressure không đồng bộ.

Nếu cần request body instrumentation, dùng API/tooling chuyên dụng với redaction, byte limits, `ServerHttpRequestDecorator` và ownership của `DataBufferUtils` rõ ràng; ưu tiên không log body nhạy cảm.

**Không lưu toàn bộ request body vào memory để debug production** mà thiếu size cap và privacy policy.

## 23. Bảo mật, concurrency và vận hành

### 23.1. Input size và codec limits

- Giới hạn max in-memory codec buffer (`spring.codec.max-in-memory-size` tùy Spring Boot config).
- Giới hạn HTTP body size/JSON depth ở proxy/server khi cần.
- Giới hạn items per batch và max name length.
- Đặt request/idle timeouts, connection and concurrency limits.
- Giới hạn tốc độ và body chunk sizes với slow clients.
- Không để client gửi Flux vô hạn rồi `collectList()` vô hạn.

### 23.2. Authentication và authorization

`@Valid` kiểm tra cú pháp DTO, không có nghĩa principal được tạo/đọc Item. Với WebFlux, dùng Spring Security `SecurityWebFilterChain` cùng reactive SecurityContext, sau đó kiểm tra ownership/tenant trong service. Không lấy client-provided `userId`/`X-Role` làm danh tính đã xác thực. Với browser cookie/session auth, POST còn chịu CSRF policy.

### 23.3. Thao tác idempotent và retry

POST create thường không idempotent. Nếu client timeout sau khi server đã lưu item, retry có thể tạo duplicate. Với hệ thống thật, cân nhắc `Idempotency-Key` do server validate, transaction/unique constraints, trace IDs và retry policy rõ. Reactor `retry()` không tự phân biệt side-effecting operations với read-only operations.

### 23.4. Backpressure và slow clients

Với Flux NDJSON, nếu client đọc chậm, upstream demand/cancellation có thể ảnh hưởng nguồn phát. Nhưng nếu code tự cache quá nhiều item, buffer vô hạn hoặc remote producer phớt lờ demand, RAM vẫn tăng. Phải đo active connections, pending buffers, response write latency, cancellations, DB connections và scheduler saturation.

### 23.5. Logging, context propagation và metrics

Reactive execution có thể chuyển scheduler; đừng dựa vào custom ThreadLocal cho request ID hoặc SecurityContext. Dùng Reactor Context/tracing integration và metrics theo route template để tránh cardinality cao. Không log raw query/body chứa secrets.

### 23.6. Reverse proxy và HTTP framing

Server có thể stream NDJSON qua chunked transfer hoặc HTTP/2 frames; **TCP/HTTP chunk boundaries không trùng JSON line boundaries**. Client parser phải dựa trên newline/codec framing, không giả sử một network read bằng một record. Reverse proxy có thể buffer/compress response, ảnh hưởng latency streaming. Test qua deployment topology thực khi việc nhận từng item sớm là yêu cầu nghiệp vụ.

## 24. Các lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân | Khắc phục |
| --- | --- | --- |
| GET không có Item nhưng trả 200 body rỗng | `ResponseEntity.ok().body(Mono.empty())` | `Mono<ResponseEntity<Item>>` để chọn 404 |
| POST trả 200 thay vì 201 | Return DTO/Mono không có status override | `Mono<ResponseEntity<Item>>` + `created(Location)` |
| `Mono<NewItem>` không được xử lý | Tạo Mono nhưng không return/subscribe đúng lifecycle | Return reactive composition, không subscribe thủ công |
| Event loop bị block | Gọi JDBC/file/network sync trong `map/flatMap` | Non-blocking driver hoặc bounded scheduler offload |
| Invalid DTO được lưu | Quên `@Valid`/business validation | `@Valid @RequestBody Mono<DTO>` và service checks |
| JSON malformed ra status/body không đúng | Error policy không cover WebFlux decoder errors | Test malformed JSON và exception mappings |
| Flux input không stream | Dùng JSON document/array không phải NDJSON hoặc decoder buffering | Dùng đúng Content-Type và wire format |
| Mỗi output item không nhận được ngay | `application/json` encode thành array, proxy buffering | NDJSON/SSE và streaming tests |
| NDJSON response client parse lỗi | Parser dựa vào HTTP chunks thay vì newline | Dùng NDJSON framing |
| Batching gây OOM | `Flux.collectList()` không bound | Giới hạn items/bytes, streaming processing |
| Batch 101 items vẫn 201 cho 100 đầu | `take(100)` truncates silently | `take(101)` rồi reject nếu size>100 |
| Stream lỗi giữa chừng nhưng status vẫn 200 | Response đã commit | Protocol resume/retry, không hứa JSON 500 |
| Spring MVC classes xuất hiện trong WebFlux | Import hoặc starter-web nhầm | Dùng WebFlux stack đúng dependencies |
| `Mono.empty()` gây API "not found" không rõ | Thiếu distinction absent vs success empty | Chọn status/DTO contract |
| Too many publisher subscriptions | Manual `subscribe()` hoặc share one-shot body | Để framework subscribe, không dùng duplicate |
| Header/Location sai sau proxy | Host/Forwarded trust sai | Canonical base/trusted proxy policy |
| CORS 403 khi browser POST | Preflight/security ordering | WebFlux CORS/Spring Security integration |
| 401/403 xử lý bởi controller thay vì Security | Không có auth policy ở HTTP filter chain | SecurityWebFilterChain + service ACL |
| `@Valid` exception class khác MVC | WebFlux validation flow khác Servlet | Handle `WebExchangeBindException` và method validation appropriately |
| Mất tracing principal khi scheduler đổi | Dùng ThreadLocal custom | Reactor Context/official context propagation |
| Reactive code retry tạo duplicate | Retry POST không có idempotency | Idempotency key và transaction |
| ResponseEntity bên ngoài Flux đổi status sai | Chọn 200 trước khi biết kết quả | Mono<ResponseEntity<...>> |
| Body JSON array bị coi là NDJSON | Nhầm media type | Content-Type/Accept matching and tests |
| Body quá lớn dù chỉ một record | Giới hạn số records nhưng không giới hạn bytes | Codec byte/size limits |
| DataBuffer leak trong WebFilter | Cache/retain mà không release | Dùng DataBufferUtils và test cancellation |

## 25. Checklist để code review

- [ ] Controller là WebFlux, không lẫn Servlet MVC.
- [ ] `@RequestBody Mono<T>` dùng cho một object, `Flux<T>` cho nhiều elements/streaming contract đúng media type.
- [ ] `@Valid` được đặt ở input reactive khi cần Bean Validation.
- [ ] Không `block()`/`subscribe()` trên request thread/event loop.
- [ ] Không dùng body publisher hai lần hoặc log raw secrets.
- [ ] Status/headers phải được quyết định trước commit và dựa vào đúng async result.
- [ ] `Mono<ResponseEntity<T>>` cho 200/404, 201/409 hay điều kiện business động.
- [ ] `ResponseEntity<Flux<T>>` cho status biết sớm cùng stream nhiều items.
- [ ] JSON array khác NDJSON line-delimited; client parser không dựa network chunks.
- [ ] Giới hạn số items, bytes, nesting, timeout, concurrency và queue.
- [ ] Với streaming lỗi sau commit, có policy retry/resume/cancellation riêng.
- [ ] Validation, auth, ownership, CSRF, idempotency và transactions không lẫn nhau.
- [ ] Đặt Location đúng server resource, không tin Host/Forwarded client tùy ý.
- [ ] Tests cover body empty/invalid/malformed/unsupported content type.
- [ ] WebTestClient covers 200, 201, 202, 204 nếu có, 400, 404, 413 nếu có, Content-Type, headers, array, NDJSON.
- [ ] Mọi external blocking dependency được xử lý đúng; không chỉ "bọc Mono" rồi coi non-blocking.
- [ ] Không dùng `Flux.collectList()` với input không giới hạn.
- [ ] Đo metrics latency, downstream errors, slow clients và cancelled streams.
- [ ] Dùng route template metrics để tránh high-cardinality tags.
- [ ] Mỗi API có error schema và content negotiation contract rõ.

## 26. Bài tập tự kiểm tra

1. `@RequestBody NewItem` khác `@RequestBody Mono<NewItem>` thế nào?
2. `@RequestBody Flux<NewItem>` có bắt buộc nhận mỗi object ngay khi đến nếu Content-Type là `application/json` array không?
3. Vì sao NDJSON thuận tiện cho stream nhiều JSON objects?
4. `Mono<ResponseEntity<Item>>` khác `ResponseEntity<Mono<Item>>` ở thời điểm quyết định 404 ra sao?
5. Vì sao `Flux.empty()` thường có thể trả 200 danh sách rỗng, còn missing resource cần 404?
6. Khi nào chọn `Mono<ResponseEntity<Flux<T>>>` thay `ResponseEntity<Flux<T>>`?
7. `@Valid` trên reactive body có thể gây exception WebFlux nào?
8. Có nên bắt `WebExchangeBindException` rồi trả raw `error.getMessage()` cho public client không?
9. `Location` cho POST resource nên tạo trước hay sau khi server xác định ID?
10. `ResponseEntity<Flux<Item>>` với `application/json` và `application/x-ndjson` khác nhau gì về format?
11. Vì sao `collectList()` trên NDJSON input vô hạn có thể dẫn đến OOM?
12. Batching tối đa 100 cần đọc tới phần tử thứ bao nhiêu để biết "too many"?
13. Khi response NDJSON đã gửi ba dòng và dòng thứ tư lỗi, status có thể đổi từ 200 sang 500 sạch không?
14. Vì sao không gọi `incoming.block()` trong controller WebFlux trên event loop?
15. `Mono.fromCallable(() -> jdbc.save(...)).subscribeOn(boundedElastic())` có làm JDBC non-blocking không?
16. `@RequestBody Flux<T>` và `Flux<DataBuffer>` khác nhau ở mức trừu tượng nào?
17. Khi nào server dùng `413 Payload Too Large`?
18. Vì sao 202 Accepted không đồng nghĩa mọi `Mono` delayed?
19. Tại sao không nên dùng `onErrorReturn(new DefaultItem(...))` cho JSON malformed/validation errors?
20. Client muốn parsing NDJSON theo đâu: HTTP chunks hay newline framing?
21. CORS có thay authorization không?
22. Retry POST gây dữ liệu duplicate ra sao?
23. Vì sao response headers nên xác định trước body write?
24. Khi no resource mà body Publisher empty, làm sao trả 404 đúng?
25. Những gì `WebTestClient` xác minh được và không thể thay benchmark/production proxy test?

**Gợi ý:** (1) Direct DTO decode trước handler, Mono là reactive value của decode. (2) Không, tùy codecs/media type. (3) Mỗi JSON line là record độc lập. (4) Mono<ResponseEntity> chờ lookup rồi chọn 200/404; ResponseEntity<Mono> status đã chọn. (5) Khác collection rỗng với resource absent. (6) Khi envelope/status phụ thuộc async decision nhưng body là Flux. (7) WebExchangeBindException; method constraints còn có HandlerMethodValidationException. (8) Không. (9) Sau khi server xác định ID. (10) JSON array vs newline-delimited objects. (11) Không bound memory. (12) 101. (13) Không bảo đảm. (14) Block event loop. (15) Không, chỉ offload blocking. (16) Decoded DTOs vs raw pooled buffers. (17) Request vượt giới hạn theo HTTP policy. (18) 202 là business semantics. (19) Che invalid input, có thể ghi dữ liệu sai. (20) NDJSON delimiters. (21) Không. (22) Side effect lặp. (23) Response commit. (24) Mono<ResponseEntity>. (25) Contract/runtime behavior cơ bản, không tự chứng minh throughput/backpressure/reverse proxy.

## 27. Tài liệu tham khảo chính thức

- [Spring Framework — WebFlux @RequestBody](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/requestbody.html)
- [Spring Framework — WebFlux ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responseentity.html)
- [Spring Framework — WebFlux Controller Method Arguments](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html)
- [Spring Framework — WebFlux Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html)
- [Spring Framework — WebFlux Validation](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-validation.html)
- [Spring Framework — WebFlux HTTP Codecs](https://docs.spring.io/spring-framework/reference/web/webflux/config.html)
- [Spring Framework — WebFlux Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
- [Spring Framework — WebFlux Reactive Core](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html)
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html)
- [Spring Framework 6.1 — ResponseEntity Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/ResponseEntity.html)
- [Project Reactor — Mono/Flux Operators](https://projectreactor.io/docs/core/release/reference/coreFeatures/simple-ways-to-create-a-flux-or-mono-and-subscribe-to-it.html)
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)

**Tổng kết:** `@RequestBody Mono<T>` biểu diễn request body một object dưới dạng reactive publisher; `@RequestBody Flux<T>` thích hợp khi nhận nhiều objects, đặc biệt NDJSON/streaming. Với response, hãy chọn kiểu theo **thời điểm cần quyết định status/headers**: `ResponseEntity<Mono<T>>` và `ResponseEntity<Flux<T>>` biết envelope sớm; `Mono<ResponseEntity<T>>` chờ kết quả async để chọn status như 201/404; `Mono<ResponseEntity<Flux<T>>>` cho phép quyết định envelope async rồi mới phát stream. Tất cả phải đi kèm media type đúng, validation, bounds, error policy, cancellation và không blocking event loop.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-004">Binding, Bean Validation và nhận diện lỗi đầu vào trong WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

# Binding, Bean Validation và nhận diện lỗi đầu vào trong Spring WebFlux

## 1. Mục tiêu: từ HTTP input không đáng tin đến lỗi có thể xử lý

Trong một API Spring WebFlux, client có thể gửi dữ liệu qua **JSON body, query string, path variables, headers, form fields hoặc multipart parts**. Trước khi tạo tài nguyên, hệ thống cần phân biệt rõ các bước:

1. **Route matching:** request có tới đúng handler theo path/method/content type hay không.
2. **Binding và conversion:** tham số HTTP dạng chuỗi/bytes được chuyển thành Java object, number, enum, record hay form object.
3. **Bean Validation:** giá trị Java hợp lệ về cấu trúc có thỏa `@NotBlank`, `@Size`, `@Email`, `@Min`, custom constraints và các groups hay không.
4. **Business validation:** tài nguyên có trùng hay không, người dùng có quyền tạo không, trạng thái domain có cho phép thao tác không.
5. **Error representation:** trả đúng HTTP status, error code/field, không rò rỉ rejected input, token hoặc stack trace.
6. **Reactive lifecycle:** validation trên `Mono<T>` có thể thất bại **khi subscription diễn ra**; phải hiểu lỗi lan truyền qua pipeline, không vô tình nuốt lỗi bằng `onErrorResume` sai phạm vi.

Bài này giải thích **Spring Framework 6.2 / Spring Boot 3.5, Java 17+**, module **Controller WebFlux**. API, exceptions và cách bind có thể khác với Servlet MVC. Các ví dụ dùng `org.springframework.web.bind.annotation` cho controller và **`org.springframework.web.reactive`** cho infrastructure, không dùng nhầm `HttpMessageConverter` hoặc exception `MethodArgumentNotValidException` từ MVC.

### Mục tiêu thực hành

- Ràng buộc data qua `@RequestBody`, `@RequestParam`, `@PathVariable`, `@ModelAttribute`, `@RequestPart`.
- Khai báo Jakarta Bean Validation và biết khi nào validation **thật sự chạy**.
- Phân biệt **`WebExchangeBindException`** với **`HandlerMethodValidationException`**, cùng các lỗi decode/conversion không phải Bean Validation.
- Biết lúc nào `BindingResult`/`Errors` có thể được đặt ngay sau model attribute hoặc non-reactive request body, và khi nào nên dùng `Mono<T>.onError*`.
- Định dạng error JSON/ProblemDetail nhất quán, bản địa hóa message, giữ HTTP status đúng.
- Bảo vệ mass assignment, nested validation, validation groups, input size và phản hồi lỗi không tiết lộ bí mật.
- Viết ứng dụng chạy được cùng tests WebTestClient, bao phủ cả valid/invalid/path/form/body.

## 2. Ba lớp thường bị gọi chung là “validation”

### 2.1. HTTP/body decoding: có đọc được dữ liệu hay không?

Request:

```http
POST /api/notes HTTP/1.1
Content-Type: application/json

{"title":
```

Đây là **JSON chưa hoàn chỉnh**. Jackson/HTTP codec không thể deserialize thành DTO; vấn đề xảy ra **trước** việc kiểm tra `@NotBlank` trên trường title.

Những lỗi tương tự:

- JSON syntax hỏng, trường type sai (ví dụ object thay vì string).
- `Content-Type` không phù hợp media type mà endpoint chấp nhận.
- Thiếu body bắt buộc, thiếu request parameter hoặc header.
- Query/path value không convert được sang `int`/`UUID`/`enum`.
- Body hoặc một JSON record vượt giới hạn codec `maxInMemorySize`.
- Form field sai định dạng/số, multipart syntax sai.

Không nên dồn mọi lỗi nói trên thành “Bean Validation failed”. Chúng có nguồn khác nhau và có thể được WebFlux exception infrastructure ánh xạ thành 400, 413, 415... theo tình huống/cấu hình.

### 2.2. Data binding: ghép tên field với property/constructor

Ví dụ form gửi:

```text
term=reactor&page=2
```

Spring dùng `WebDataBinder` và conversion service để tạo/điền một model attribute có các properties `term` và `page`. Nếu page là `"abc"` nhưng target type là `int`, xảy ra **binding/type mismatch** dù chưa xét `@Min(1)`.

`BindingResult` có thể chứa cả **binding errors** và **validation errors**. Những lỗi này khác domain exceptions (ví dụ note title trùng trong database).

### 2.3. Bean Validation: object đúng kiểu nhưng không thỏa constraint

Ví dụ:

```json
{
  "title": " ",
  "contact": {
    "email": "not-an-email"
  }
}
```

JSON hợp cú pháp và Jackson có thể tạo object; nhưng `@NotBlank` và `@Email` có thể tạo violation. Bean Validation dùng các annotations từ **`jakarta.validation`**, implementation phổ biến là **Hibernate Validator** qua `spring-boot-starter-validation`.

> Cần phân biệt **validation contract** và **security authorization**. Một DTO có title đúng độ dài không có nghĩa người dùng được phép tạo resource hay truy cập record của tenant khác.

## 3. Bộ dependencies và namespace cho Spring Boot 3.5

Với project Gradle đã khai báo Spring Boot 3.5 plugin, repositories và Java 17 toolchain:

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

Đây là **fragment `build.gradle`**, không phải file build độc lập. `spring-boot-starter-validation` bổ sung Bean Validation provider mà project cần; chỉ có annotations trong code chưa chắc có runtime validator nếu dependency thiếu.

Các imports cốt lõi:

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
```

Dùng **`jakarta.validation.*`** trên Boot 3/Framework 6, không copy `javax.validation.*` từ Boot 2. `jakarta.validation.Valid` là cascade marker; nó không phải một constraint trực tiếp như `@NotNull`.

### 3.1. Cách Spring tìm Validator

Khi có Bean Validation provider trên classpath, WebFlux có thể đăng ký global `LocalValidatorFactoryBean`; `WebFluxConfigurer#getValidator()` cho phép cung cấp validator khác. `@InitBinder` cho phép thiết lập validators hay binding options cho controller phạm vi hẹp.

**Không thêm `@EnableWebFlux` chỉ để bật validation trong một ứng dụng Boot.** Nó có thể làm Boot WebFlux auto-configuration không còn hoạt động như mặc định; dùng starter và cấu hình chuẩn của Boot.

## 4. Bảng phân loại dữ liệu đầu vào theo annotation

| Nguồn input | Annotation/method | Cách convert | Lỗi tiêu biểu |
| --- | --- | --- | --- |
| JSON request body | `@RequestBody DTO` hoặc `Mono<DTO>` | `HttpMessageReader` + Jackson | Decode error, `WebExchangeBindException` |
| Query parameter | `@RequestParam` | `ConversionService` | Missing value, type mismatch, method constraint |
| Path segment | `@PathVariable` | `ConversionService` | Missing/unparseable, method constraint |
| HTTP header | `@RequestHeader` | Type conversion | Missing, format error, method constraint |
| Form/query/URI/header thành object | `@ModelAttribute` | `WebDataBinder`, constructor/property binding | BindingResult hoặc `WebExchangeBindException` |
| JSON metadata của multipart | `@RequestPart` | Part reader/codec | Decode failure, `WebExchangeBindException` |
| Functional WebFlux endpoint | `ServerRequest.bodyToMono(...)` | Codecs | Decode failure; Bean Validation không tự luôn áp như controller annotations |
| Response/return value method constraint | Constraint trực tiếp trên return | Method validation | `HandlerMethodValidationException`, thường server error với invalid return |

Với **`@ModelAttribute`**, Spring WebFlux có thể bind từ **form data, query parameters, URI variables và request headers**. Form/query thường được ưu tiên khi trùng tên. Không nên dùng entity persistence có setter cho trường nhạy cảm làm public model attribute vì có nguy cơ **mass assignment**. Thiết kế DTO riêng và hạn chế `allowedFields` nếu dùng property binding.

## 5. Bean Validation annotations thường gặp

| Constraint | Ý nghĩa chính | Lưu ý quan trọng |
| --- | --- | --- |
| `@NotNull` | Giá trị khác null | Empty string vẫn có thể qua |
| `@NotEmpty` | Collection/string/map không null và không rỗng | Chuỗi chỉ whitespace vẫn có thể qua |
| `@NotBlank` | `CharSequence` không null, phải có non-whitespace | Phù hợp title, tên |
| `@Size(min,max)` | Độ dài string/collection/map/array | Không chặn null nếu không kết hợp `@NotNull` |
| `@Min` / `@Max` | Giá trị số theo giới hạn | Thích hợp page, quantity |
| `@Positive` / `@PositiveOrZero` | Số dương / không âm | Không tự ngăn null |
| `@Email` | Hình thức địa chỉ email theo validator | Không chứng minh địa chỉ tồn tại/sở hữu |
| `@Pattern` | Chuỗi phù hợp regex | Không tự chặn null; regex quá phức tạp có thể gây CPU pressure |
| `@Past` / `@Future` | Thời điểm theo constraint | Chú ý clock/time zone |
| `@Valid` | Cascade validation vào object/nested elements | Không tự cấm null |
| `@Validated(Group.class)` | Chọn validation groups | Spring-specific annotation, group semantics phải xác định |

Ví dụ DTO Java 17 dùng **record components**:

```java
record Contact(
        @NotBlank @Email String email) {}

record CreateNote(
        @NotBlank @Size(min = 3, max = 80) String title,
        @NotNull @Valid Contact contact) {}
```

Nếu client gửi:

```json
{"title":"  ","contact":{"email":"bad-mail"}}
```

Jackson tạo được `CreateNote`, nhưng validation có thể báo lỗi title và contact.email. Với `contact=null`, `@NotNull` báo lỗi; `@Valid` **không có nghĩa null tự vi phạm**.

### 5.1. Thứ tự field violations không phải API contract

Bean Validation provider có thể trả nhiều `ConstraintViolation`/`FieldError` theo thứ tự khác nhau giữa phiên bản. Khi test, kiểm tra **set tên field và error code**, không bắt buộc thứ tự lỗi nếu API chưa cam kết sort.

### 5.2. DTO validation và business validation

`@Email` xác minh format tương đối. Nó không tra DNS, gửi xác minh email hay kiểm tra user đang sở hữu địa chỉ. Tương tự `@Size(max=80)` không kiểm tra title có bị trùng, profanity, ký tự cấm riêng domain hoặc quyền tạo tài nguyên.

Đừng thêm thao tác network/DB vào một custom Bean Validation constraint nếu gây blocking trên Reactor Netty event loop. Với ràng buộc cần I/O, thực hiện trong **reactive service layer**, trả `Mono.error(domainException)` khi vi phạm.

## 6. `@Valid @RequestBody Mono<T>` và lỗi reactive

Spring WebFlux cho phép:

```java
@PostMapping("/api/notes")
Mono<Note> create(
        @Valid @RequestBody Mono<CreateNote> body) {
    return body.map(this::save);
}
```

Đây là fragment; `save` và DTO phải có trong controller/service. Framework đọc body qua HTTP codecs, validate decoded object khi publisher được tiêu thụ, rồi mới phát item hợp lệ tới `map`. Nếu validation thất bại, pipeline **phát `onError(WebExchangeBindException)`** và `map(this::save)` không được chạy cho item đó.

**Đặc điểm WebFlux:** controller có thể được gọi và trả publisher trước khi decode/validation hoàn tất. Do đó, đừng nghĩ mọi Bean Validation lỗi xảy ra đồng bộ tại thời điểm vào Java method.

### 6.1. Bắt lỗi cục bộ trong reactive chain

```java
return body
        .map(this::save)
        .onErrorResume(WebExchangeBindException.class, ex ->
            Mono.error(ex));
```

Fragment này chỉ minh họa type-specific handling; ở đây rethrow nên không cần trong ứng dụng thật. Nếu muốn tạo response JSON ngay trong reactive chain, hãy đổi kiểu trả phù hợp (`Mono<ResponseEntity<?>>` hoặc custom envelope), xử lý chính xác `WebExchangeBindException` và không catch mọi `Throwable` rồi biến lỗi database thành 400.

Một ví dụ có ý nghĩa hơn nếu muốn thêm audit metric:

```java
return body
        .map(this::save)
        .doOnError(WebExchangeBindException.class, ex ->
            metrics.incrementInvalidInput());
```

Đây là fragment với `metrics` đã được khai báo. Không log raw rejected body hoặc credentials. Nếu đã có `@RestControllerAdvice` trả JSON chuẩn, để exception đi qua hạ tầng xử lý lỗi thay vì lặp `onErrorResume` ở mọi endpoint.

### 6.2. `BindingResult` và reactive wrapper

Spring docs cho phép:

```java
@PostMapping("/items")
ResponseEntity<?> submit(
        @Valid @RequestBody CreateNote body,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return ResponseEntity.badRequest().body("Invalid input");
    }
    return ResponseEntity.ok("ok");
}
```

Đây là **fragment**: khi request body là **object không bọc Mono** và `BindingResult` nằm **ngay sau** argument validated, controller có thể tự xem lỗi. Ngược lại:

```java
// Không dùng BindingResult ngay sau Mono<DTO> như thể DTO
// đã được decode đồng bộ:
void submit(
        @Valid @RequestBody Mono<CreateNote> body,
        BindingResult errors) { /* không phải pattern chuẩn */ }
```

Với `Mono<DTO>`, dùng reactive error operators hoặc global advice. **`BindingResult` không được đặt tùy ý ở cuối danh sách tham số**; phải gắn trực tiếp với đúng argument tương ứng theo support của framework.

## 7. Khi nào WebFlux ném `WebExchangeBindException`?

Đây là lỗi chủ yếu khi **validate/bind một command object đơn lẻ** (ví dụ `@RequestBody`, `@ModelAttribute`, `@RequestPart`) với `@Valid` hoặc `@Validated`, nếu:

- Đối tượng đó không phải container `Map`/`Collection` như các trường hợp docs phân biệt.
- Không khai báo `Errors`/`BindingResult` hợp lệ ngay sau tham số để xử lý cục bộ.
- Phương thức không thuộc tình huống cần **method validation** (xem phần kế).

`WebExchangeBindException` có `BindingResult`:

```java
@ExceptionHandler(WebExchangeBindException.class)
ResponseEntity<?> invalidBody(WebExchangeBindException ex) {
    List<String> fields = ex.getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .toList();

    return ResponseEntity.badRequest()
            .body(Map.of(
                    "code", "INPUT_VALIDATION",
                    "fields", fields));
}
```

Fragment này cần các imports cho exception, ResponseEntity, List, FieldError. Nó chỉ trả tên field, **không phản chiếu rejected values**. Kiểu và danh sách lỗi thực có thể bao gồm object-level errors (`getGlobalErrors`) chứ không chỉ field-level errors; production nên xây error representation phù hợp.

**Không nhầm `WebExchangeBindException` với MVC `MethodArgumentNotValidException`.** Hai stack có tên exception khác nhau.

## 8. Method validation và `HandlerMethodValidationException`

Spring Framework 6.1+ có **built-in method validation** cho annotated controller methods; Spring 6.2 giữ hai cấp validation.

Ví dụ:

```java
@GetMapping("/page")
Mono<Page> page(
        @RequestParam("number") @Min(1) @Max(20) int number) {
    return Mono.just(new Page(number));
}
```

`@Min` và `@Max` áp **trực tiếp lên method parameter**, nên framework kiểm tra ở mức method. Khi thất bại, exception điển hình là **`HandlerMethodValidationException`**, không phải `WebExchangeBindException`.

### 8.1. Quy tắc phân loại

| Method signature | Cơ chế thường dùng | Lỗi |
| --- | --- | --- |
| `create(@Valid @RequestBody Mono<CreateNote> body)` | Validate object body riêng | `WebExchangeBindException` |
| `form(@Valid @ModelAttribute Form form)` | Binding/validate object riêng | `WebExchangeBindException` |
| `upload(@Valid @RequestPart("meta") Metadata meta)` | Validate part DTO riêng | `WebExchangeBindException` |
| `page(@RequestParam @Min(1) int page)` | **Method validation** | `HandlerMethodValidationException` |
| `create(@NotNull @Valid @RequestBody CreateNote body)` | Có constraint trực tiếp `@NotNull` → method validation | Có thể là `HandlerMethodValidationException` |
| Method có `@NotNull`/`@Positive` trên return value | Method return validation | `HandlerMethodValidationException`; server-side invariant |

**`@Valid` tự nó không phải constraint annotation để kích hoạt method validation**. Thêm `@NotNull` trực tiếp trên tham số có thể chuyển cả method sang nhánh method validation; khi đó framework áp cả direct constraints và nested `@Valid`.

Không viết global advice chỉ bắt một loại lỗi. Chữ ký controller thay đổi có thể khiến nhánh exception đổi dù business DTO như cũ.

### 8.2. Class-level `@Validated` trên controller: trường hợp dễ nhầm

Spring docs khuyến nghị **bỏ `@Validated` ở cấp controller class** nếu muốn sử dụng built-in controller method validation (từ Spring Framework 6.1). Lý do: class-level `@Validated` có thể bật method validation qua **AOP proxy**, với semantics/exception path khác.

Phân biệt:

- **Controller:** validation method trực tiếp có thể được WebFlux áp dụng mà **không** cần `@Validated` trên class.
- **Service bean:** muốn kích hoạt method constraint thông qua Bean Validation AOP, ví dụ `@Service @Validated`, cần hiểu proxy limitations và dependency provider.
- **Parameter DTO:** `@Validated(SomeGroup.class)` trên argument phù hợp để chọn group cho object validation.

Tránh thêm `@Validated` lên mọi controller “cho chắc” vì có thể khiến tests/error mapping thay đổi.

### 8.3. Đọc lỗi method theo parameter

`HandlerMethodValidationException` có:

```java
List<ParameterValidationResult> results =
        ex.getParameterValidationResults();
```

Mỗi `ParameterValidationResult` cho biết method parameter, argument (cẩn thận vì có thể nhạy cảm) và `getResolvableErrors()`. Khi nested object có lỗi, result có thể là `ParameterErrors`, cung cấp field-level information.

Spring còn có **visitor** `HandlerMethodValidationException.Visitor` để xử lý theo annotation types (`requestParam`, `requestHeader`, `modelAttribute`...). Đây là lựa chọn tốt hơn tự đoán index parameter khi API có nhiều tham số.

**Không trả `getArgument()` hoặc raw invalid values vào JSON/log**. Nếu method validation áp lên **return value**, đó thường là bug/invariant của server — xử lý theo server error, không đổ lỗi client.

## 9. Lỗi type conversion, JSON decode và thiếu input

Bean Validation chỉ chạy sau khi binder/decoder tạo giá trị có kiểu phù hợp. So sánh:

```http
GET /api/page?number=abc
```

`number` không convert được thành `int`; đây không phải vi phạm `@Min(1)`. Nó có thể phát lỗi input/binding trước method validator.

```http
GET /api/page?number=0
```

`number=0` convert thành int được, sau đó `@Min(1)` thất bại → **method validation**.

```http
POST /api/notes
Content-Type: application/json

{"title":
```

Đây là JSON parse error, không phải `WebExchangeBindException` do `@NotBlank`.

### 9.1. Những ngoại lệ cần nhận diện

| Nguồn lỗi | Lớp/thực tế WebFlux | HTTP status thường phù hợp |
| --- | --- | --- |
| Validate DTO | `WebExchangeBindException` | 400 |
| Method constraint | `HandlerMethodValidationException` | 400 cho parameters, 500 cho invalid return |
| JSON parse/type conversion trong body | `ServerWebInputException` hoặc codec exceptions được wrap | 400 theo contract |
| Thiếu request param/header/path variable | WebFlux input value exceptions | 400 |
| Content-Type không được hỗ trợ | `UnsupportedMediaTypeStatusException` | 415 |
| Accept không hỗ trợ | `NotAcceptableStatusException` | 406 |
| Codec body/record quá lớn | `DataBufferLimitException` có thể được wrap | Theo policy, thường 413 cho payload quá lớn |
| Resource ID đúng dạng nhưng không tồn tại | Business/domain exception | 404 |
| Domain conflict (trùng định danh) | Domain exception | 409 |
| Chưa xác thực/không đủ quyền | Spring Security | 401/403 |
| Database unavailable/internal bug | Server exception | 5xx |

**Không dựa vào mỗi class name để quyết định status tuyệt đối**. Spring có thể wrap lỗi, error handler mặc định và reverse proxy có thể thay đổi response. Thiết kế tests theo API contract.

## 10. `@ModelAttribute` trong WebFlux: data binding và BindingResult

Ví dụ một form/search request:

```http
GET /api/notes/search?term=spring&page=2
```

Một form DTO:

```java
class SearchForm {
    @NotBlank
    private String term;

    @Min(1)
    private int page = 1;

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
}
```

Trong annotated WebFlux controller:

```java
@GetMapping("/search")
ResponseEntity<?> search(
        @Valid @ModelAttribute SearchForm form,
        BindingResult binding) {

    if (binding.hasErrors()) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "FORM_INVALID"));
    }

    return ResponseEntity.ok(
            Map.of("term", form.getTerm(), "page", form.getPage()));
}
```

Ở đây `BindingResult` nằm ngay sau **non-reactive `@ModelAttribute`**. Controller có thể chọn render lại HTML form với field errors hoặc trả 400 JSON API.

### 10.1. Binding error khác constraint violation

Ví dụ:

- `page=abc` → không convert được sang int, `BindingResult` ghi type mismatch.
- `page=0` → convert thành int thành công nhưng không đạt `@Min(1)`.
- `term=` → chuỗi rỗng, không đạt `@NotBlank`.

Cả ba cùng có thể được hiển thị dưới dạng field errors cho người dùng, nhưng **nguồn gốc và cách sửa khác nhau**. Không lặp raw exception message hoặc raw rejected value trong response.

### 10.2. Bảo vệ mass assignment

`@ModelAttribute` có thể kết hợp constructor binding và property binding. Nếu bạn dùng entity có setter:

```java
class AccountEntity {
    private boolean admin;
    private String displayName;
    // getters/setters...
}
```

Client có thể gửi `admin=true` nếu binder cho phép property binding. **Bean Validation không phải mass-assignment protection**. Giải pháp:

- Dùng **request DTO** chỉ chứa các field được phép.
- Ưu tiên constructor binding immutable DTO khi phù hợp.
- Nếu phải dùng property binding, đặt `WebDataBinder#setAllowedFields(...)` và không nhận entity trực tiếp.
- Không cho client ghi `id`, `tenantId`, `role`, `ownerId`, `isAdmin` khi business không cho phép.
- Kiểm tra quyền theo server identity, không dựa vào trường client tự khai.

### 10.3. `@InitBinder` giới hạn những field được bind

Fragment:

```java
@InitBinder("searchForm")
void initSearchBinder(WebDataBinder binder) {
    binder.setAllowedFields("term", "page");
}
```

Cần import `org.springframework.web.bind.WebDataBinder` và `@InitBinder`. Tên attribute phải khớp (ví dụ dùng `@ModelAttribute("searchForm")`); nếu không chắc convention, khai báo tên tường minh trong controller. Đặt allowed fields là defense-in-depth, vẫn cần DTO chuyên biệt.

## 11. `@RequestPart` và validate metadata của multipart

WebFlux cho phép:

```java
@PostMapping(
    path = "/uploads",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
Mono<ResponseEntity<?>> upload(
        @Valid @RequestPart("metadata") Mono<UploadMetadata> metadata,
        @RequestPart("file") FilePart file) {
    // Validate metadata trước khi lưu file.
    return metadata.map(meta ->
            ResponseEntity.accepted().build());
}
```

Đây là **method fragment**, không phải hệ thống upload production. Để bind JSON part thành `UploadMetadata`, client cần gửi part `metadata` với Content-Type phù hợp. `FilePart` cần được consume/transfer/release đúng; đoạn trên cố ý chỉ giải thích signature, **không dùng mẫu đó để lưu file**.

`@Valid`/`@Validated` trên `@RequestPart` DTO có thể dẫn đến `WebExchangeBindException`. Ngoài ra multipart có những lỗi khác: file quá lớn, part thiếu, MIME giả, disk quota, path traversal, malware. Bean Validation trên metadata **không xác minh nội dung binary file**.

Ví dụ record:

```java
record UploadMetadata(
        @NotBlank String description,
        @Min(1) int retentionDays) {}
```

Nếu metadata có `retentionDays=0`, đó là validation lỗi; nếu file là EXE ngụy trang JPG, đó là nội dung file/risk policy phải kiểm tra riêng.

## 12. Validation groups và các bước tạo/cập nhật

Mỗi operation có thể có constraints khác nhau. Ví dụ create yêu cầu password hoặc title bắt buộc, update cho phép thiếu field nhưng giới hạn length nếu có.

Khai báo marker interfaces:

```java
interface OnCreate {}
interface OnUpdate {}

record NoteCommand(
        @NotBlank(groups = OnCreate.class)
        @Size(max = 80, groups = {
                OnCreate.class, OnUpdate.class})
        String title) {}
```

Chọn group tại parameter:

```java
@PostMapping("/commands")
Mono<Void> create(
        @Validated(OnCreate.class)
        @RequestBody Mono<NoteCommand> body) {
    return body.then();
}
```

Và:

```java
@PatchMapping("/commands/{id}")
Mono<Void> update(
        @Validated(OnUpdate.class)
        @RequestBody Mono<NoteCommand> body) {
    return body.then();
}
```

Đây là **method fragments** để giải thích group selection, không phải runnable controller đầy đủ. `@Validated(Group.class)` là annotation Spring hỗ trợ chọn validation hints/groups, trong khi `@Valid` kích hoạt default cascade validation.

Lưu ý:

- Nếu chỉ validate group `OnUpdate`, các constraints thuộc group mặc định **Default** không mặc nhiên được chạy, trừ khi đã chọn/định nghĩa group sequences đúng.
- `@NotBlank` trên create sẽ không áp với update nếu không thuộc OnUpdate; nhưng nếu client **gửi title=""**, chỉ `@Size(max=80)` có thể cho qua. Khi cần ràng buộc “nếu có thì không blank”, thêm constraint/phân biệt null vs empty.
- PATCH có semantics **absent vs null vs blank** khác nhau. DTO thông thường có thể không phân biệt property không được gửi với property JSON null; cần thiết kế patch model phù hợp.
- Không dùng groups để thay authorization hoặc state machine domain.

### 12.1. Nested group conversion

Khi validate nested structures, có thể dùng `@ConvertGroup(from=Default.class,to=OnCreate.class)` trên cascade field để thay group cho nested bean. Đây là Bean Validation capability nâng cao; dùng khi có yêu cầu mô hình rõ và test nested group behavior, đừng thêm theo quán tính.

## 13. Custom Validator: khi annotation đơn lẻ không diễn đạt được

Ví dụ form có `startDate <= endDate` hoặc yêu cầu ít nhất một trong hai fields được điền. Các ràng buộc nhiều trường thường cần **class-level constraint** hoặc Spring `Validator`.

### 13.1. Spring Validator

```java
class SearchFormValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return SearchForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        SearchForm form = (SearchForm) target;
        if (form.getTerm() != null
                && form.getTerm().length() > 100) {
            errors.rejectValue("term", "term.tooLong",
                    "Search term is too long");
        }
    }
}
```

Đây là **class fragment** cần `org.springframework.validation.Validator` và `Errors` cùng `SearchForm`. Có thể thêm validator qua `@InitBinder`/`WebDataBinder#addValidators` cho phù hợp. Nếu đã có `@Size(max=100)`, đừng lặp constraint trong hai nơi làm người dùng nhận hai lỗi khác nhau.

### 13.2. Jakarta custom constraint

Một custom constraint thường gồm:

1. Annotation `@Constraint(validatedBy=...)` với `@Target`, `@Retention(RUNTIME)`, `message/groups/payload`.
2. Class `ConstraintValidator<MyConstraint,T>` với `isValid`.
3. Có tests cho null, empty, boundary, nested cascade và validation groups.
4. Không giữ mutable per-request state trong singleton validator.
5. Không truy vấn blocking database hoặc gọi HTTP sync trong `isValid` trên event-loop.

Nếu cần uniqueness async, viết:

```java
return repository.existsByTitle(input.title())
        .flatMap(exists -> exists
                ? Mono.error(new NoteConflictException())
                : repository.save(new NoteEntity(input.title())));
```

Đây là **fragment**, giả định repository reactive và domain exceptions đã khai báo. Nó không giải quyết race condition “check then insert” giữa concurrent requests; cần **DB unique constraint** và map conflict phù hợp, không chỉ pre-check.

## 14. Trả lỗi validation theo contract mà không che giấu lỗi server

Khi DTO bị từ chối, client cần nhận được **status** và một **mã lỗi ổn định**, có thể thêm danh sách field lỗi đã sanitize. Hai lựa chọn hợp lý: JSON envelope do ứng dụng định nghĩa hoặc `ProblemDetail` theo RFC 9457.

```json
{
  "code": "INPUT_VALIDATION",
  "errors": [
    {"field": "title", "message": "must not be blank"}
  ],
  "requestId": "server-generated-correlation"
}
```

Đây là **mẫu contract**, không phải output mặc định của Spring. Khi dùng `WebExchangeBindException`, lấy field names/mã lỗi đã được phép công bố; đừng echo nguyên rejected value vì có thể chứa bí mật. Với `HandlerMethodValidationException`, xác định lỗi thuộc request input hay invariant ở return value trước khi chọn status; không nên chuyển bug/DB outage thành 400.

`ProblemDetail` là model có các trường chuẩn như `type`, `title`, `status`, `detail` và `instance`. Có thể thêm extension field `code` theo policy. Không tạo schema tùy tiện bằng cách ghép raw exception message hoặc stacktrace vào response; lựa chọn contract cần nhất quán qua controller, filter và gateway khi có thể.

Ví dụ **không đúng**:

```java
@ExceptionHandler(Exception.class)
ResponseEntity<String> all(Exception ex) {
    return ResponseEntity.badRequest().body("invalid input");
}
```

Nó biến cả lỗi lập trình, storage/DB hoặc upstream timeout thành HTTP 400. Bài kế tiếp về `@ExceptionHandler`, `@RestControllerAdvice` và `ProblemDetail` là nơi xử lý chi tiết Spring `ErrorResponse`, error handler precedence, security/filter boundaries và response đã commit; bài này chỉ phân biệt **dữ liệu input không hợp lệ** với **sự cố server**.

## 15. Ví dụ runnable: Java 17 + Spring Boot 3.5 WebFlux

Demo bao gồm:

- `POST /api/notes` nhận `@Valid @RequestBody Mono<CreateNote>`; validate nested contact, trả 201 với ID trong URL.
- `GET /api/notes/{id}` trả 200/404 theo ID.
- `GET /api/notes/page?number=...` áp `@Min`/`@Max` **trực tiếp vào parameter**, để minh họa method validation.
- `GET /api/notes/search?term=...&page=...` dùng `@Valid @ModelAttribute` + `BindingResult`, phân biệt form binding errors.
- `@RestControllerAdvice` map hai exception validation thành JSON riêng, map input decode errors thành 400 không lộ raw input.
- Tests dùng `WebTestClient` trên HTTP server thật (RANDOM_PORT).

Đây là ứng dụng **demo in-memory**. Không có Spring Security, persistent DB, conflict handling giữa nodes, rate limits, localization đầy đủ hay audit logs; không dùng trực tiếp trong production.

### 15.1. Cấu trúc dự án

```text
webflux-validation-demo/
├─ build.gradle                         (Spring Initializr + dependencies)
└─ src/
   ├─ main/
   │  ├─ java/demo/input/
   │  │  └─ InputValidationApplication.java
   │  └─ resources/
   │     └─ application.properties
   └─ test/
      └─ java/demo/input/
         └─ InputValidationApplicationTests.java
```

Trong `src/main/resources/application.properties` có thể để:

```properties
spring.application.name=webflux-validation-demo
# Không bật raw request-body/headers logging.
logging.level.org.springframework.web=INFO
```

Không thêm `spring-boot-starter-web` vào build của demo, vì có cả MVC và WebFlux starter có thể khiến Boot 3.x chọn Servlet MVC application type mặc định.

### 15.2. Source Java đầy đủ

Tạo **`src/main/java/demo/input/InputValidationApplication.java`**:

```java
package demo.input;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class InputValidationApplication {
    public static void main(String[] args) {
        SpringApplication.run(InputValidationApplication.class, args);
    }
}

record Contact(
        @NotBlank @Email String email) {}

record CreateNote(
        @NotBlank @Size(min = 3, max = 80) String title,
        @NotNull @Valid Contact contact) {}

record Note(long id, String title, String contactEmail) {}

record PageInfo(int number) {}

record SearchResult(String term, int page) {}

record Violation(String field, String message) {}

record ApiError(String code, List<Violation> errors) {}

class SearchForm {
    @NotBlank
    private String term;

    @Min(1)
    private int page = 1;

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}

@RestController
@RequestMapping("/api/notes")
class NoteController {
    private final AtomicLong sequence = new AtomicLong(100);
    private final ConcurrentMap<Long, Note> notes =
            new ConcurrentHashMap<>();

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Note>> create(
            @Valid @RequestBody Mono<CreateNote> input) {

        return input.map(body -> {
            long id = sequence.incrementAndGet();
            Note created = new Note(
                    id, body.title().trim(),
                    body.contact().email());

            notes.put(id, created);

            URI location = URI.create("/api/notes/" + id);
            return ResponseEntity.created(location).body(created);
        });
    }

    @GetMapping(path = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Note> find(@PathVariable long id) {
        return Mono.justOrEmpty(notes.get(id))
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Note not found")));
    }

    @GetMapping(path = "/page",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<PageInfo> page(
            @RequestParam("number")
            @Min(1) @Max(20) int number) {
        return Mono.just(new PageInfo(number));
    }

    @GetMapping(path = "/search",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> search(
            @Valid @ModelAttribute("searchForm")
            SearchForm form,
            BindingResult errors) {

        if (errors.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError(
                        "FORM_BINDING",
                        errors.getFieldErrors().stream()
                            .map(error -> new Violation(
                                error.getField(), "invalid"))
                            .distinct()
                            .toList()));
        }

        return ResponseEntity.ok(new SearchResult(
                form.getTerm(), form.getPage()));
    }
}

@RestControllerAdvice
class InputErrorAdvice {

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiError> objectValidation(
            WebExchangeBindException error) {

        List<Violation> fields =
                error.getFieldErrors().stream()
                    .map(f -> new Violation(
                            f.getField(), "invalid"))
                    .distinct()
                    .toList();

        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "INPUT_VALIDATION", fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> methodValidation(
            HandlerMethodValidationException error) {

        // Do not expose raw validated argument values.
        // If return-value validation fails, it is a server invariant,
        // not a 400 error attributed to the caller.
        if (error.isForReturnValue()) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                    "SERVER_VALIDATION", List.of()));
        }

        return ResponseEntity.badRequest()
                .body(new ApiError(
                    "METHOD_VALIDATION",
                    List.of(new Violation(
                            "request", "invalid"))));
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ApiError> inputDecode(
            ServerWebInputException error) {
        return ResponseEntity.badRequest()
                .body(new ApiError(
                    "INPUT_DECODE",
                    List.of(new Violation(
                            "request", "malformed input"))));
    }
}
```

Đây là **một file Java 17 hợp lệ về cấu trúc**: chỉ `InputValidationApplication` là public; records, model, controller, advice đều package-private trong `demo.input`. Spring component scan tìm controllers/advice vì cùng package dưới application class.

Cần hiểu:

1. **`create`** nhận `Mono<CreateNote>`, validation diễn ra trong quá trình decode/publisher consume. Nếu title/email sai, body phát validation error và **`notes.put` không được chạy**.
2. `@NotNull @Valid Contact contact` bảo đảm nested contact khác null và cascade xuống email. Nếu email chỉ là whitespace, `@NotBlank` lỗi; nếu format không đúng, `@Email` lỗi.
3. `notes` là in-memory map nên GET ở node khác có thể 404. Không có transaction/DB.
4. `URI.create("/api/notes/"+id)` tạo **relative Location** phù hợp demo tại context root. Với production có context path/gateway version prefix, cần URI builder hoặc public URL strategy đáng tin để sinh Location đúng.
5. **`page`** có direct `@Min/@Max` → built-in method validation. Không gắn class-level `@Validated` lên controller.
6. **`search`** dùng non-reactive `SearchForm` + `BindingResult` ngay sau argument; WebFlux hỗ trợ cách này. Body thành công là object, không bắt buộc trả `Mono<?>` nếu xử lý không blocking.
7. `InputErrorAdvice` dùng **hai handlers tách biệt** cho WebExchangeBindException và HandlerMethodValidationException. Nếu lỗi JSON decode, handler thứ ba trả **INPUT_DECODE** theo contract, không phản chiếu parser exception.
8. `Violation.message` là static text `"invalid"` nên không lộ rejected value, nhưng UI sản phẩm có thể muốn default constraint messages được localize; xem phần 18.
9. `@Min/@Max` trên path/query không là authorization. Nếu app có Security, cần bảo vệ endpoints đúng nghiệp vụ.

### 15.3. Chạy ví dụ và kết quả dự kiến

Khởi chạy:

```bash
./gradlew bootRun
```

**POST hợp lệ:**

```bash
curl -i -X POST \
  'http://localhost:8080/api/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":"WebFlux validation","contact":{"email":"reader@example.org"}}'
```

Nếu đây là request tạo đầu tiên, response dự kiến:

```http
HTTP/1.1 201 Created
Location: /api/notes/101
Content-Type: application/json

{"id":101,"title":"WebFlux validation","contactEmail":"reader@example.org"}
```

ID 101 chỉ là ví dụ khi counter bắt đầu ở 100. Status Location là **relative path**, không được giả định host/proxy/port. GET `/api/notes/101` sẽ trả resource trong cùng instance nếu chưa restart.

**POST có ràng buộc sai:**

```bash
curl -i -X POST \
  'http://localhost:8080/api/notes' \
  -H 'Content-Type: application/json' \
  -d '{"title":" ","contact":{"email":"not-an-email"}}'
```

Kỳ vọng **400 Bad Request**, code `INPUT_VALIDATION` và lỗi field `title`, `contact.email`; thứ tự các field không được cam kết:

```json
{
  "code": "INPUT_VALIDATION",
  "errors": [
    {"field":"title","message":"invalid"},
    {"field":"contact.email","message":"invalid"}
  ]
}
```

**Method constraint thất bại:**

```bash
curl -i 'http://localhost:8080/api/notes/page?number=0'
```

Kỳ vọng 400 và:

```json
{
  "code":"METHOD_VALIDATION",
  "errors":[{"field":"request","message":"invalid"}]
}
```

**Đúng kiểu nhưng lỗi form:**

```bash
curl -i 'http://localhost:8080/api/notes/search?term=&page=0'
```

Kỳ vọng 400 và code `FORM_BINDING`, fields `term` và `page` (thứ tự không cố định).

**Form page không parse được:**

```bash
curl -i 'http://localhost:8080/api/notes/search?term=spring&page=abc'
```

`BindingResult` có thể ghi type mismatch và trả 400 `FORM_BINDING`, thay vì controller bị lỗi không rõ ràng.

**JSON syntax hỏng:**

```bash
curl -i -X POST \
  'http://localhost:8080/api/notes' \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":'
```

Kỳ vọng 400 **INPUT_DECODE** trong cấu hình demo; không kết luận là `@NotBlank` đã chạy.

Các kết quả trên là **hợp đồng dự kiến của mã mẫu đã rà soát**, **không phải kết quả kiểm thử thực tế**.

## 16. Test với WebTestClient trên HTTP server thật

Tạo **`src/test/java/demo/input/InputValidationApplicationTests.java`**:

```java
package demo.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InputValidationApplicationTests {

    @Autowired
    WebTestClient client;

    @Test
    void validBodyCreatesResource() {
        client.post()
            .uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "title": "Reactive API",
                  "contact": {"email": "a@example.org"}
                }
                """)
            .exchange()
            .expectStatus().isCreated()
            .expectHeader().exists("Location")
            .expectBody()
            .jsonPath("$.title").isEqualTo("Reactive API")
            .jsonPath("$.contactEmail")
                .isEqualTo("a@example.org");
    }

    @Test
    void blankTitleAndBadEmailFailObjectValidation() {
        client.post()
            .uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "title": " ",
                  "contact": {"email": "wrong-address"}
                }
                """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("INPUT_VALIDATION")
            .jsonPath("$.errors").isArray();
    }

    @Test
    void missingContactFailsNestedNotNullConstraint() {
        client.post()
            .uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"title":"Valid title"}
                """)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("INPUT_VALIDATION");
    }

    @Test
    void directQueryConstraintTriggersMethodValidation() {
        client.get()
            .uri("/api/notes/page?number=0")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("METHOD_VALIDATION");
    }

    @Test
    void validPageWorks() {
        client.get()
            .uri("/api/notes/page?number=5")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.number").isEqualTo(5);
    }

    @Test
    void formBindingAndValidationErrorsHandledLocally() {
        client.get()
            .uri("/api/notes/search?term=&page=0")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("FORM_BINDING");
    }

    @Test
    void malformedFormIntegerProducesBindingError() {
        client.get()
            .uri("/api/notes/search?term=reactor&page=abc")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("FORM_BINDING");
    }

    @Test
    void validFormReturnsConvertedValues() {
        client.get()
            .uri("/api/notes/search?term=reactor&page=2")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.term").isEqualTo("reactor")
            .jsonPath("$.page").isEqualTo(2);
    }

    @Test
    void malformedJsonIsDecodeErrorNotBeanValidation() {
        client.post()
            .uri("/api/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"title\":")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo("INPUT_DECODE");
    }
}
```

Chạy:

```bash
./gradlew test
```

**Chín tests được thiết kế để pass** trên Spring Boot 3.5 WebFlux + Jakarta Validation provider. Đây là kỳ vọng sau khi rà soát API và flow, **chưa phải báo cáo Gradle đã chạy**.

Những điều test đo:

- Codec + Bean Validation cùng tham gia khi xử lý `@RequestBody Mono<CreateNote>`.
- Nested cascade + `@NotNull` không bị bỏ qua.
- Method-level direct `@Min` được phân loại riêng.
- `@ModelAttribute` có `BindingResult` xử lý type mismatch lẫn constraint violation.
- Malformed JSON không bị nhầm thành field validation error.
- HTTP status/body contract quan sát ở embedded Netty server, không chỉ gọi controller trực tiếp.

**Giới hạn:** tests chưa chứng minh DB persistence, Security filters, multipart parser, proxy size caps, default error serialization, race conditions hoặc localization. Khi thêm các lớp này, bổ sung integration tests tương ứng.

## 17. Những trường hợp không nên dùng Bean Validation như công cụ duy nhất

### 17.1. Uniqueness và transactions

Một controller nhận input hợp lệ có thể vẫn thất bại khi title trùng database key. Đừng truy cập DB sync trong `ConstraintValidator` trên event-loop. Dùng reactive repository, unique DB constraint và translate conflict thành HTTP 409, chống race.

### 17.2. Authorization và tenant boundary

`@NotNull Long ownerId` không chứng minh user có quyền đặt ownerId đó. Lấy owner từ authenticated principal/server-side context, validate quyền truy cập bằng security service/DB. Không tin field `role=admin` hoặc `tenantId` do client gửi.

### 17.3. Content security và upload

`@Size(max=100)` trên filename không kiểm tra path traversal, MIME, malware hoặc archive bombs. Với multipart dùng `FilePart`/`PartEvent`, đặt upload byte/disk/part limits, file format validation và quarantine/AV nếu nghiệp vụ cần.

### 17.4. Rate limits và resource exhaustion

Một JSON array gồm hàng triệu phần tử có thể gây áp lực decoder và service dù mỗi item qua `@Valid`. Hãy dùng `spring.http.codecs.max-in-memory-size` đúng Boot 3.5, body size caps, pagination, query complexity/timeouts và rate limits. Bean Validation không phải hệ thống chống DDoS.

### 17.5. Reactive cancellation

Nếu client ngắt kết nối khi validation/DB operation còn chạy, publisher có thể nhận cancel. Giải phóng resources và kiểm tra transaction cancellation semantics; `@Valid` không tự xử lý resource cleanup, rollback hoặc idempotency.

## 18. Bản địa hóa và error codes

Default Bean Validation messages có thể được Spring/Bean Validation interpolator giải quyết qua `MessageSource` và locale. Bạn có thể dùng:

```properties
# src/main/resources/messages_vi.properties
NotBlank.createNote.title=Tiêu đề không được để trống
Size.createNote.title=Tiêu đề phải có độ dài hợp lệ
```

Đây là **ví dụ cơ chế message bundles**, không khẳng định các message codes chính xác cho mọi binding object naming scheme; code thực tế cần xem `FieldError#getCodes()` để chọn keys và tests localization.

Một annotation có message template:

```java
@NotBlank(message = "{note.title.required}")
String title
```

có thể được resolve bằng message source/validator theo cấu hình. Thông báo locale cần tránh lộ rejected value hoặc template injection. Với API công khai, stable **machine-readable error code** nên độc lập ngôn ngữ, còn human message phụ thuộc locale/`Accept-Language`.

### 18.1. Có nên trả chính xác defaultMessage?

Có thể, nếu policy xác nhận message an toàn, đã escape/encode đúng và không chứa dữ liệu nhạy cảm. Với Bean Validation standard messages (`must not be blank`, `size must be between...`) thường hữu ích. Nhưng một custom validator có thể tạo message chứa raw user input; **không tự log/return mọi message một cách mù quáng**. Ví dụ runnable trả `"invalid"` cố ý để giữ ranh giới bảo mật.

## 19. ProblemDetail / RFC 9457 trong WebFlux

Spring Framework 6.2 hỗ trợ `ProblemDetail` và `ErrorResponse` để thể hiện các HTTP errors dưới dạng `application/problem+json`. Khi API phức tạp, bạn có thể tạo lỗi:

```java
ProblemDetail problem =
        ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
problem.setTitle("Input validation failed");
problem.setDetail("One or more fields are invalid");
problem.setProperty("code", "INPUT_VALIDATION");
```

Đây là **fragment** cần import `org.springframework.http.ProblemDetail`/`HttpStatus`; dùng trong `@ExceptionHandler` và thêm fields đã kiểm tra khi cần.

Một body kiểu:

```json
{
  "type": "about:blank",
  "title": "Input validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "code": "INPUT_VALIDATION"
}
```

là representation minh họa, **không tự mặc định mọi WebFlux app trả y như vậy**. Chọn **một** error schema nhất quán cho cùng API version hoặc có migration plan, thay vì endpoint A trả `{"code":...}` và endpoint B trả HTML/stack trace tùy lỗi.

## 20. Khác biệt với Spring MVC Servlet

| Chủ đề | Spring MVC | Spring WebFlux |
| --- | --- | --- |
| HTTP body converter | `HttpMessageConverter` | `HttpMessageReader`/`HttpMessageWriter` |
| Input object annotation | `@RequestBody`, `@ModelAttribute` | Có cả hai, hỗ trợ reactive wrappers |
| Bean validation namespace trên Spring 6 | `jakarta.validation` | `jakarta.validation` |
| Object validation exception | `MethodArgumentNotValidException` | **`WebExchangeBindException`** |
| Method validation exception | `HandlerMethodValidationException` | **`HandlerMethodValidationException`** |
| Bind form errors | `BindingResult` sau non-reactive argument | Tương tự; có hỗ trợ reactive model attributes |
| Mono body | MVC có hỗ trợ vài reactive types nhưng Servlet writer khác | Native `Mono<DTO>` và `Flux<DTO>` pipeline |
| Error infrastructure | MVC `HandlerExceptionResolver`, advice | WebFlux reactive exception infrastructure/advice |
| Server threads | Servlet worker threads | Reactor Netty event loops/non-blocking I/O |
| Blocking DB | Thường phù hợp worker thread tùy tải | Tránh blocking trên event loop; R2DBC hoặc boundedElastic |

**Không sử dụng exception class của MVC để bắt lỗi WebFlux body binding**, và không mặc định một `@ControllerAdvice` viết cho Servlet MVC có thể dùng nguyên trong reactive app.

Spring Framework 6.1/6.2 tích hợp method validation cho controllers; nếu nâng từ Spring 5.x/Boot 2.x, cả exception types và class-level `@Validated` semantics có thể khác. Đọc migration guide và kiểm thử controller signatures.

## 21. Troubleshooting: biểu hiện → nguyên nhân → cách sửa

| Biểu hiện | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `@Valid` không chạy | Thiếu Hibernate Validator/provider trên classpath | Thêm `spring-boot-starter-validation`, test invalid DTO |
| `@Email` cho phép null | Constraint không tương đương required | Kết hợp `@NotBlank` hoặc `@NotNull` |
| Nested DTO không validate | Thiếu `@Valid` trên nested field | Thêm cascade annotation và test nested |
| POST JSON hỏng mà handler WebExchangeBindException không chạy | Lỗi decode xảy ra trước Bean Validation | Xử lý `ServerWebInputException`/codec error riêng |
| Query number=0 vào method trả 400 nhưng handler object validation không bắt | Direct `@Min` kích hoạt method validation | Xử lý `HandlerMethodValidationException` |
| Controller có class `@Validated` và method validation khác docs | AOP proxy path | Bỏ class-level `@Validated` để dùng built-in WebFlux validation |
| `BindingResult` không nhận lỗi với `Mono<DTO>` | Không đúng cách dùng reactive wrapper | Dùng `onError*`/advice, hoặc nonreactive DTO + adjacent result |
| Form có page=abc nhưng API trả 500 | Type mismatch không được xử lý như client input | Dùng BindingResult/error mapping đúng |
| Spring tự bind isAdmin=true | Mass assignment vào entity | Dùng DTO + allowedFields/constructor binding |
| Title unique constraint chỉ validate bằng pre-check | Race condition giữa requests | Database unique constraint, map 409 |
| Error body lộ mật khẩu/rejectedValue | Log/return raw BindingResult | Chỉ chọn field/code/messages an toàn |
| Validation runs nhưng service vẫn save record invalid | Validation annotations trên wrong parameter/group | Test exact signature, group and provider |
| One field reports nhiều errors theo thứ tự thay đổi | Validator/provider order không cam kết | Assert tập field/codes, sort nếu API đòi |
| `@Size` cho phép null | `@Size` không phải required | Thêm `@NotNull`/`@NotBlank` |
| Method validation báo 500 cho return value | Vi phạm server invariant | Sửa code/business, không trả 400 cho caller |
| 400 default response khác schema API | Chưa có centralized error handling | `@RestControllerAdvice`/ProblemDetail |
| Upstream HTTP call từ validator làm chậm app | Blocking trên event loop | Chuyển business rule vào reactive service |
| Validation chỉ áp JSON body, bỏ qua multipart metadata | Thiếu `@Valid @RequestPart` | Validate metadata, giới hạn file riêng |
| Unit test pass nhưng WebTestClient fail | Codec/binding khác khi qua HTTP | RANDOM_PORT integration tests |
| Giới hạn size JSON không như tưởng | Boot codec/config/total request limits khác nhau | Dùng đúng property + gateway caps và test |

## 22. Checklist trước khi công bố WebFlux API

- [ ] Tách request DTO khỏi entities; chỉ bind fields được phép.
- [ ] Dùng `jakarta.validation.*` và có implementation thực tế trong build.
- [ ] Không gắn `@Validated` lên controller class khi muốn built-in method validation.
- [ ] `@Valid` cascade nested DTOs; thêm `@NotNull` khi nested object bắt buộc.
- [ ] Phân biệt missing/invalid type/decode với Bean Validation violations.
- [ ] Cả `WebExchangeBindException` lẫn `HandlerMethodValidationException` đều có policy.
- [ ] Return-value validation error được xử lý như server invariant.
- [ ] Method parameter constraints `@Min/@Max` được test qua WebFlux handler pipeline.
- [ ] Form binding `BindingResult` luôn ngay sau non-reactive `@ModelAttribute`/body khi dùng.
- [ ] Reactive `Mono<DTO>` giữ error signaling; không “subscribe rồi bỏ qua lỗi”.
- [ ] Validation group create/update/patch rõ và có tests cho absent/null/empty.
- [ ] Validation liên quan DB/business chạy trong reactive service, không block event-loop.
- [ ] Response errors có code và status nhất quán, không lộ rejected values/stack traces.
- [ ] Content-type/Accept/malformed JSON/missing param có error contract riêng.
- [ ] Upload metadata validation không thay file content scanning/size/quota.
- [ ] Không coi CORS hay Bean Validation là authentication/authorization.
- [ ] Limit codec/max request/time/rate/multipart được cấu hình ở nhiều tầng.
- [ ] WebTestClient integration tests test cả invalid/success, nested body, form conversion và method validation.
- [ ] Có observability theo route template/error code, không log PII/token.
- [ ] Dependency/provider/Spring Framework version và Bean Validation groups đã được kiểm tra khi nâng cấp.

## 23. Bài tập tự đánh giá

1. Trong JSON `{"title":"  "}`, lỗi là JSON syntax, binding conversion hay Bean Validation? Phụ thuộc những annotations nào?
2. Vì sao `@Email` chưa chứng minh email tồn tại, và vì sao phải thêm `@NotBlank` nếu bắt buộc có giá trị?
3. Khi client gửi `page=abc` cho `int page`, `@Min` đã chạy chưa? Phân biệt conversion với method validation.
4. So sánh `@Valid @RequestBody Mono<CreateNote>` và `@Valid @RequestBody CreateNote, BindingResult`.
5. Vì sao `BindingResult` phải đứng ngay sau model attribute? Có nên thêm sau `Mono<DTO>` không?
6. Một method có `@Valid` trên DTO, sau đó thêm `@NotNull` lên cùng parameter. Kiểu validation và exception có thể đổi thế nào?
7. Vì sao global advice cho WebFlux nên xử lý cả `WebExchangeBindException` và `HandlerMethodValidationException`?
8. Class-level `@Validated` trên WebFlux controller ảnh hưởng controller method validation Spring 6.2 ra sao?
9. `@Valid` trên nested field null có tự thất bại không? Phải thêm gì?
10. Với record DTO title và contact.email, thiết kế tests assert hai field violations mà không phụ thuộc order.
11. Vì sao `@ModelAttribute` bind một entity có `isAdmin` setter có thể nguy hiểm? Dùng DTO/allowedFields thế nào?
12. Thiết kế validation groups OnCreate/OnUpdate: update cho phép omitted title nhưng không chấp nhận blank string phải làm sao?
13. Một custom ConstraintValidator gọi blocking JDBC; WebFlux event-loop gặp nguy cơ gì và chuyển sang reactive service ra sao?
14. Nếu URL path có ID không tồn tại, nên trả 400 hay 404? Nếu path ID có định dạng sai thì sao?
15. Nếu title trùng DB, phải dùng 409 và DB unique constraint vì sao, thay vì chỉ `@Pattern`?
16. Một client POST multipart kèm metadata JSON hợp lệ nhưng file chứa malware. Bean Validation đã đủ chưa?
17. Error handler trả `ex.getRejectedValue()` trong response JSON: rủi ro bảo mật gì?
18. Dùng `ProblemDetail` tạo một 400 error với field code ổn định và localized detail.
19. Khi ứng dụng chạy Boot 3.5 nhưng thiếu `spring-boot-starter-validation`, vì sao annotations có thể không enforce lúc runtime?
20. Khi đưa một `Flux<DTO>` vào controller, validation per-item và stream errors khác với một `Mono<DTO>` ở những điểm nào?
21. Tạo WebTestClient case JSON syntax sai và case JSON đúng format nhưng title blank; so sánh error codes.
22. Khi response streaming đã trả status 200 và gửi bytes, một validation/business error đến sau có còn chuyển thành HTTP 400 không?
23. Dữ liệu do user gửi chứa secret trong message; thiết kế log error/field violations mà không để lộ secret.
24. Với API trả về annotated method output constraint, vì sao invalid return value là lỗi server chứ không nên trả 400?
25. Nêu lộ trình migration Boot 2/Framework 5 sang Boot 3.5: javax→jakarta, runtime provider, method validation, WebFlux exception mapping và tests.

## 24. Tài liệu chính thức

- [Spring Framework 6.2 — WebFlux Controller Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-validation.html): phân biệt object validation với method validation và hai exception types.
- [Spring Framework 6.2 — @RequestBody in WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/requestbody.html): `@Valid`, `Mono<DTO>`, `WebExchangeBindException`, `Errors`.
- [Spring Framework 6.2 — @ModelAttribute WebFlux](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/modelattrib-method-args.html): binding, `BindingResult`, security.
- [Spring Framework 6.2 — WebFlux Multipart](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/multipart-forms.html): `@RequestPart` validation và FilePart.
- [Spring Framework 6.2 — WebFlux Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/ann-rest-exceptions.html): `ProblemDetail`, `ErrorResponse`, validation error messages.
- [Spring Framework 6.2 — WebFlux Config Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html): global `Validator`/`WebFluxConfigurer`/`@InitBinder`.
- [Spring Boot 3.5 — Validation](https://docs.spring.io/spring-boot/3.5/reference/io/validation.html): validation starter/provider, service method validation, MessageSource.
- [Spring Framework 6.2 — HandlerMethodValidationException Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/method/annotation/HandlerMethodValidationException.html): parameter results/visitor/return value errors.
- [Spring Framework 6.2 — WebExchangeBindException Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/support/WebExchangeBindException.html): BindingResult/field errors.
- [Spring Framework 6.2 — ParameterValidationResult Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/validation/method/ParameterValidationResult.html): field and method parameter errors.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): test WebFlux HTTP binding, validation status and bodies.
- [Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/): constraints, nested validation, groups, Bean Validation semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-005">Multipart, FilePart, PartEvent và xử lý tệp theo luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Multipart, FilePart, PartEvent và xử lý tệp theo luồng trong Spring WebFlux

## 1. Mục tiêu và phạm vi

Trong Spring WebFlux, tải file lên HTTP thường sử dụng `multipart/form-data`, nhưng việc nhận một file đơn lẻ và xử lý hàng chục gigabyte theo luồng là hai bài toán khác nhau. **`FilePart`** thuận tiện khi truy cập part đã được parse theo tên và lưu vào đích, trong khi **`PartEvent`** cho phép nhận các mảnh dữ liệu multipart tuần tự bằng `Flux<PartEvent>`.

Kết thúc bài này, bạn cần hiểu:

- Cấu trúc multipart HTTP (boundary, field name, filename, media type, form fields và file).
- Phân biệt Spring WebFlux `Part`, `FilePart`, `FormFieldPart`, `PartEvent`, `FilePartEvent`, `FormPartEvent` với Servlet MVC `MultipartFile`.
- Cách dùng `@RequestPart`, `@RequestBody Mono<MultiValueMap<String, Part>>`, `ServerWebExchange.getMultipartData()`.
- Cách dùng `@RequestBody Flux<PartEvent>`, `windowUntil(PartEvent::isLast)`, `switchOnFirst`, `concatMap`, `DataBufferUtils.release`.
- Cách lưu bằng `FilePart.transferTo(Path)` hoặc `DataBufferUtils.write`, quản lý ownership của pooled buffers.
- Giới hạn `maxInMemorySize`, `maxDiskUsagePerPart`, `maxParts`, tổng dung lượng upload, timeouts và concurrent uploads.
- Cách triển khai, kiểm thử và bảo vệ endpoint upload với **Java 17+, Spring Boot 3.x, Spring Framework 6.x**, bao gồm kiểm thử HTTP multipart bằng `WebTestClient`.
- Những giới hạn của streaming/backpressure; không nhầm non-blocking với vô hạn memory, disk hay network throughput.

**Lưu ý phạm vi:** Bài tập dùng Spring WebFlux reactive stack (`spring-boot-starter-webflux`), không phải `spring-boot-starter-web`/Spring MVC. Đối với Boot 4/Framework 7, cần kiểm tra lại version API.

## 2. HTTP multipart: một body có nhiều parts

Request trình duyệt:

~~~html
<form action="/api/uploads/file" method="post" enctype="multipart/form-data">
  <input type="file" name="file" required>
  <button type="submit">Upload</button>
</form>
~~~

Một request tương đương dạng MIME được trình bày đơn giản:

~~~http
POST /api/uploads/file HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data; boundary=DemoBoundary

--DemoBoundary
Content-Disposition: form-data; name="note"

report for Q4
--DemoBoundary
Content-Disposition: form-data; name="file"; filename="report.csv"
Content-Type: text/csv

id,name
1,Spring
--DemoBoundary--
~~~

Các boundary lines thực tế có các quy tắc CRLF riêng; snippet chỉ minh họa format, không phải dump nhị phân chính xác.

Từng part có **tên field** (`name="file"`), có thể có **filename** và `Content-Type` riêng. Filename và Content-Type **do client gửi**, không được dùng làm quyền truy cập, MIME validation hay filesystem path tin cậy.

Server chọn part qua `@RequestPart("file")`, không phải qua đuôi file. Nếu client gửi `name="attachment"` thì `@RequestPart("file")` không khớp và thường bị lỗi 4xx.

Client HTTP nên tự tạo boundary đúng qua `curl -F`, `MultipartBodyBuilder`, trình duyệt hoặc WebClient multipart writer. Đừng chỉ gán header `Content-Type: multipart/form-data` rồi gửi raw bytes mà thiếu multipart separators.

## 3. Phân biệt các abstraction và phương thức

| Kiểu | Package | Dùng cho |
| --- | --- | --- |
| `Part` | `org.springframework.http.codec.multipart` | Multipart part: `name()`, `headers()`, `content()`, `delete()` |
| `FilePart` | như trên | File đã được parse: `filename()`, `transferTo(Path)` |
| `FormFieldPart` | như trên | Field text có `value()` |
| `PartEvent` | như trên | Event chứa headers, `content()`, `isLast()` |
| `FilePartEvent` | như trên | Event thuộc file; `filename()` |
| `FormPartEvent` | như trên | Event thuộc form field; `value()` |
| `DataBuffer` | `org.springframework.core.io.buffer` | Một buffer bytes, có thể pooled/ref-counted |
| `DataBufferUtils` | như trên | Read/write/release buffers |

Trong Spring MVC Servlet, `MultipartFile` có API `getBytes()`, `getInputStream()` và `transferTo(File)` mang phong cách imperative. Trong WebFlux, `FilePart.content()` trả **`Flux<DataBuffer>`** còn `transferTo(Path)` trả **`Mono<Void>`**. Không sao chép code MVC sang WebFlux rồi coi hai type là một.

### Chọn API đúng nhiệm vụ

| Nhu cầu | Lựa chọn |
| --- | --- |
| Một file hay vài named fields | `@RequestPart FilePart` |
| File cùng một JSON metadata part | `@RequestPart FilePart` và `@RequestPart Mono<Meta>` |
| Tra cứu toàn bộ parts theo tên | `@RequestBody Mono<MultiValueMap<String, Part>>` hoặc `exchange.getMultipartData()` |
| Xử lý một file theo từng chunk/event | `@RequestBody Flux<PartEvent>` |
| Streaming relay multipart sang service khác | `PartEvent` + WebClient multipart writer, kèm quản lý buffers |
| Chỉ lưu file đã được parse | `FilePart.transferTo(Path)` |
| Upload rất lớn và cần resume theo chunk | Protocol resumable chuyên dụng/object storage, không chỉ một `FilePart` |

Không có lựa chọn luôn nhanh nhất. Streaming API giảm nhu cầu giữ toàn bộ file trong memory nhưng tăng trách nhiệm quản lý DataBuffer và cancellation.

## 4. `@RequestPart`: truy cập part theo tên

Handler cơ bản:

~~~java
@PostMapping(
        path = "/api/documents",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Mono<Void> receive(
        @RequestPart("file") FilePart file,
        @RequestPart("metadata") Mono<DocumentMetadata> meta) {

    return meta.flatMap(m ->
            validateMetadata(m)
                    .then(file.transferTo(chooseServerPath(m))));
}
~~~

**Đây là snippet minh họa, chưa chạy độc lập** vì `DocumentMetadata`, `validateMetadata`, `chooseServerPath` chưa được định nghĩa. Ý chính: Spring WebFlux decode JSON của part `metadata` khi đúng media type/codec; file đi theo `FilePart`. `@Valid` có thể dùng với DTO metadata khi có Bean Validation dependency và constraint annotations, với cơ chế lỗi binding WebFlux tương ứng.

Khi cần tất cả parts:

~~~java
Mono<MultiValueMap<String, Part>> parts = exchange.getMultipartData();
~~~

hoặc trong controller:

~~~java
@PostMapping(consumes="multipart/form-data")
public Mono<Void> receiveAll(
        @RequestBody Mono<MultiValueMap<String, Part>> parts) {
    return parts.then();
}
~~~

Ví dụ thứ hai **chỉ minh họa binding**, không được coi là cách xử lý file an toàn trong sản phẩm: nó không lưu file và không có validation/cleanup nghiệp vụ. Với `getMultipartData()` hoặc map access, framework **parse các parts thành map** trước khi bạn truy cập, có thể buffer file nhỏ và spool file lớn ra temporary disk. Một khi raw body đã được parse/cache thành map thì không coi body là một stream có thể subscribe nhiều lần.

### Vì sao FilePart không nhất thiết là toàn bộ bytes trong heap?

`FilePart` mô tả part sau parser, không có nghĩa Spring đọc file 1 GiB thành một `byte[]`. Theo Spring 6.x default part reader, `maxInMemorySize` là threshold để file parts được spool sang disk; non-file parts bị giới hạn memory. Vậy vẫn phải theo dõi **temporary disk**, open files, cổng nhận HTTP, thời gian upload và số requests đồng thời.

## 5. `FilePart.transferTo(Path)` và tính lazy

~~~java
Mono<Void> write = filePart.transferTo(destination);
~~~

Tác vụ ghi thực hiện khi reactive Publisher được subscribe; framework sẽ subscribe khi controller trả Mono. Vì vậy **sai** nếu gọi `file.transferTo(dest)` nhưng bỏ Mono rồi return ngay `"success"`.

Đúng hơn:

~~~java
return filePart.transferTo(destination)
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                .body("stored"));
~~~

`thenReturn` chỉ gửi business status khi transfer thành công. Nếu transfer lỗi, pipeline phát error chứ không báo file đã lưu xong.

**Không dùng trực tiếp filename client làm đường dẫn:**

~~~java
// ANTI-PATTERN: nguy cơ path traversal và ghi đè.
Path unsafe = uploadRoot.resolve(filePart.filename());
return filePart.transferTo(unsafe);
~~~

Filename có thể là `../secret.txt`, absolute path, ký tự điều khiển, Unicode spoofing hoặc tên trùng. Chính Javadoc `FilePart.filename()` khuyến cáo không sử dụng mù quáng. Chọn storage ID do server quản lý:

~~~java
String storageKey = UUID.randomUUID() + ".bin";
Path safe = controlledRoot.resolve(storageKey);
return filePart.transferTo(safe);
~~~

`UUID` giảm xác suất collision nhưng không thay hẳn atomic `CREATE_NEW` khi threat model cần ngăn mọi ghi đè. `transferTo` có thể truncate file đích đã tồn tại. Trong production cần storage isolation, quyền OS, kiểm soát symlink/race, staging/quarantine, checksum/scanning và cleanup khi lỗi.

### Không consume FilePart hai lần vô điều kiện

Nếu đã tự đọc `filePart.content()` để xử lý/hash, không mặc định `transferTo()` sau đó sẽ đọc lại một stream repeatable. Nếu cần hash và write, dùng một pass qua source buffers hoặc staging trước rồi đọc file đã lưu theo thiết kế rõ ràng.

## 6. `PartEvent`: xử lý streaming thay vì map access

Một file upload lớn có thể phát **nhiều `FilePartEvent`**, không phải một event duy nhất. Mỗi event có `content()` là `DataBuffer`; event cuối của part có `isLast() == true`. Form field thường có một `FormPartEvent`.

Đúng pattern:

~~~java
allEvents
    .windowUntil(PartEvent::isLast)
    .concatMap(window ->
        window.switchOnFirst((signal, events) -> {
            // signal đầu tiên giúp phân loại FormPartEvent/FilePartEvent.
            // Toàn bộ events của window vẫn phải được xử lý.
            return events;
        }));
~~~

Snippet chỉ minh họa chuỗi Reactor, **chưa phải handler hoàn chỉnh**. Giải thích:

1. `windowUntil(PartEvent::isLast)` chia stream thành từng window tương ứng một part.
2. `switchOnFirst` xem loại event đầu, nhưng không làm mất event đầu khỏi chuỗi phía sau.
3. `concatMap` xử lý windows tuần tự, giữ thứ tự multipart parts và tránh mở nhiều writes đồng thời không cần thiết.
4. Với `FilePartEvent`, lấy `events.map(PartEvent::content)` để chuyển tiếp/ghi/đếm theo streaming.
5. Với `FormPartEvent`, đọc `value()`, nhưng **nếu tự sở hữu buffer**, vẫn phải consume/release đúng.
6. **Mỗi DataBuffer nhận vào phải được consume, relay hoặc release**. Nếu lọc bỏ events mà không release buffers, pooled native memory có thể leak.

Anti-pattern:

~~~java
// KHÔNG LÀM: chỉ lấy event đầu tiên, bỏ dữ liệu cuối file.
return events.filter(e -> e instanceof FilePartEvent).next();
~~~

Nếu file chia nhiều chunks thì `next()` lấy quá ít bytes. Cancellation cũng có thể khiến những buffers chưa được downstream nhận phải được xử lý theo discard/cleanup semantics.

## 7. DataBuffer ownership, release và write

Trong Reactor Netty, `DataBuffer` có thể backed bởi pooled reference-counted buffers. Nếu framework decoder hoàn toàn xử lý bytes, nó thường quản lý lifecycle. Nhưng nếu custom code trực tiếp lấy `PartEvent.content()` thì bạn phải quản lý.

Ví dụ **đếm bytes rồi giải phóng**:

~~~java
long length;
try {
    length = buffer.readableByteCount();
} finally {
    DataBufferUtils.release(buffer);
}
~~~

Đây là snippet trong method đã có `DataBuffer buffer`. `finally` đảm bảo release khi code ném exception. Không release một buffer trước khi writer/consumer khác hoàn tất đọc; cũng không truyền buffer đã release tiếp downstream.

Spring cung cấp:

~~~java
Mono<Void> write = DataBufferUtils.write(
        sourceBuffers, destination,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
~~~

Đây là overload ghi vào **`Path`**; `sourceBuffers` và `destination` cần được khai báo. **Các overload khác trả `Flux<DataBuffer>` khi ghi vào `OutputStream`/`WritableByteChannel` và không tự release buffers**; cần đọc Javadoc overload chính xác và dùng `DataBufferUtils.releaseConsumer()`/doOnNext release khi phù hợp.

Không đồng thời cho hai consumers giữ cùng một `DataBuffer` rồi cả hai release nếu không xử lý reference count có chủ đích. `doOnDiscard` hữu ích cho những operators hỗ trợ discard, nhưng không thay thế việc release trên chính nhánh xử lý thông thường; cancellation, timeout và error đều cần được kiểm thử.

**Tránh gom toàn bộ bytes:**

~~~java
// ANTI-PATTERN với file lớn
Mono<List<DataBuffer>> huge = inputBuffers.collectList();
~~~

Không giới hạn kích thước file nhưng gom buffer references có thể làm memory tăng nhanh và gây leak khi lỗi. Streaming giúp tránh bước này, miễn downstream thực sự tiêu thụ dần và không bí mật aggregate lại.

## 8. Multipart readers và các giới hạn cần biết

Theo Spring Framework 6.2:

- `MultipartHttpMessageReader` cung cấp access theo dạng `MultiValueMap<String, Part>`; nó dùng một part reader bên dưới.
- `DefaultPartHttpMessageReader` phục vụ parsed parts và có `setMaxInMemorySize`, `setMaxDiskUsagePerPart`, `setMaxParts`.
- `PartEventHttpMessageReader` đọc trực tiếp `Flux<PartEvent>` cho event-stream path.

### Bảng giới hạn

| Limit | Ý nghĩa | Điều không nên suy diễn |
| --- | --- | --- |
| `maxInMemorySize` | Giới hạn non-file fields trong memory, threshold spool file parts sang disk | Không có nghĩa file tổng bị giới hạn bằng ngưỡng đó |
| `maxDiskUsagePerPart` | Giới hạn disk per file part trên parsed reader tương ứng | Không tự giới hạn mọi file trong PartEvent path |
| `maxParts` | Giới hạn số parts trên parsed reader | Không thay per-user request quota |
| Max HTTP body | Tổng bytes tại server/gateway | Không đồng nghĩa `maxInMemorySize` |
| Idle/body timeout | Client truyền rất chậm | Không thay size quota |
| Temp disk quota | Không cho staging disk đầy | Không đạt được chỉ bằng giới hạn heap |
| Concurrent upload limit | Số clients đang upload cùng lúc | Non-blocking không có nghĩa vô hạn |

Đặt `maxInMemorySize=-1` có thể tắt giới hạn và khiến data được giữ hoàn toàn trong memory ở reader tương ứng. Không “sửa” `DataBufferLimitException` bằng giá trị -1 mà không đặt hard limits khác.

### Cấu hình cho `FilePart` path

~~~java
@Configuration
public class UploadCodecConfig implements WebFluxConfigurer {
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        DefaultPartHttpMessageReader partReader =
                new DefaultPartHttpMessageReader();

        partReader.setMaxInMemorySize(64 * 1024);
        partReader.setMaxDiskUsagePerPart(10L * 1024 * 1024);
        partReader.setMaxParts(5);

        configurer.defaultCodecs().multipartReader(
                new MultipartHttpMessageReader(partReader));
    }
}
~~~

Đây là class mô phỏng, file đầy đủ với imports nằm trong ví dụ tiếp. **Giới hạn per-file disk và maxParts của cấu hình này thuộc parsed reader `@RequestPart`**, không được mặc định áp dụng cùng mức cho `@RequestBody Flux<PartEvent>`. Với event-based route, tự đếm bytes, áp ingress limit, timeouts và giới hạn parts ngoài application nếu cần.

Khi parser từ chối request, status có thể là 400/413 tùy exception/version/middleware. Nếu API yêu cầu luôn 413 cho size overflow, thêm exception mapping được kiểm thử end-to-end, không tự khẳng định một status mà chưa kiểm tra.

## 9. Security: upload là untrusted input

Ngay cả người dùng có tài khoản cũng có thể upload file nguy hiểm hoặc sai định dạng. Cần các lớp độc lập:

- **Authentication/authorization** per user/tenant, upload policy, allowlist định dạng, quota/retention.
- **Server-generated storage keys**, không dùng `filename()`, raw path param hoặc `getOriginalFilename` làm Path.
- **MIME sniffing/validation** dựa trên nội dung thật cùng allowlist; extension và part `Content-Type` là hint client.
- **Đếm bytes thật**; không tin `Content-Length` nếu request dùng streaming/chunked, proxy rewriting hoặc client nói dối.
- **File scanner/virus scanner**, kiểm tra archive/zip bomb nếu nghiệp vụ xử lý compressed contents.
- **Staging/quarantine** trước publish; chỉ public khi scanning, metadata DB, owner ACL đều thành công.
- **Giới hạn parts/file/disk/HTTP timeouts/concurrency**, quota per user, rate limiting và slow upload protection.
- **Filesystem isolation**, không lưu trong web root/code directory, root không writable bởi client, ngăn symlink/TOCTOU.
- **Cookie/CORS/CSRF**: CORS không thay CSRF nếu dùng cookie session; WebFlux Spring Security chain phải bảo vệ POST upload.
- **Download ACL** độc lập; tệp được nhận không mặc nhiên được public/inline.
- **Audit** bằng opaque ID, không log session cookies, bearer tokens, raw filename hoặc nội dung file nhạy cảm.
- **Cleanup** khi file ghi dở, upload hủy, scan thất bại, metadata DB lỗi, session/retry trùng.

Nếu cần tệp nhiều GiB hoặc resumable uploads, xem presigned object-storage upload / tus/chunked protocols thay vì buộc một HTTP multipart request giữ connection quá dài. `FilePart` không tự xử lý resume.

## 10. Ví dụ hoàn chỉnh Java 17+ và Spring Boot 3.x

Demo có **hai endpoints**:

- `POST /api/uploads/file` nhận `FilePart`, dùng tên opaque UUID và `transferTo` ghi file local. Trả **201 Created** khi transfer hoàn thành.
- `POST /api/uploads/events/inspect` nhận `Flux<PartEvent>`, duyệt tuần tự các parts, chỉ đếm bytes của file và release mỗi buffer; không lưu file và không tạo byte array lớn.
- Cấu hình parsed multipart reader với memory threshold 64 KiB, tối đa 10 MiB disk/file và tối đa 5 parts; event route có kiểm tra 10 MiB bytes/file trong pipeline.
- Kiểm thử HTTP bằng `WebTestClient` với multipart builder; xác minh bytes lưu và receipt.

**Giới hạn demo:** không có virus scanning, authentication/authorization, atomic publish, quota toàn request, cleanup file bị bỏ dở hoặc hard proxy limits. Không triển khai nguyên code demo lên public internet.

### 10.1. Project và dependencies

Tạo project Spring Initializr (Maven, Java 17+, Spring Boot 3.x) với:

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

Đây là đoạn **trong pom.xml** do Spring Initializr tạo, không phải file Maven độc lập. Tránh thêm `spring-boot-starter-web` vì với cả MVC và WebFlux starters Boot thường ưu tiên MVC mặc định.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/UploadCodecConfig.java
src/main/java/com/example/demo/UploadController.java
src/test/java/com/example/demo/UploadControllerTest.java
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

### 10.3. UploadCodecConfig.java

~~~java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class UploadCodecConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        DefaultPartHttpMessageReader parsed =
                new DefaultPartHttpMessageReader();
        parsed.setMaxInMemorySize(64 * 1024);
        parsed.setMaxDiskUsagePerPart(10L * 1024 * 1024);
        parsed.setMaxParts(5);
        configurer.defaultCodecs()
                .multipartReader(new MultipartHttpMessageReader(parsed));
    }
}
~~~

### 10.4. UploadController.java

~~~java
package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private final Path root;

    public UploadController() throws IOException {
        // Chỉ chạy một lần khi khởi tạo application bean.
        root = Path.of("target", "demo-uploads")
                .toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @PostMapping(
            path = "/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<StoredFile>> upload(
            @RequestPart("file") FilePart part) {

        // Không dùng part.filename() để chọn đường dẫn.
        String key = UUID.randomUUID() + ".bin";
        Path destination = root.resolve(key);

        return part.transferTo(destination)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(new StoredFile(key, "stored")));
    }

    @PostMapping(
            path = "/events/inspect",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<List<StreamReceipt>> inspect(
            @RequestBody Flux<PartEvent> all) {

        return all.windowUntil(PartEvent::isLast)
                .concatMap(window ->
                        window.switchOnFirst((signal, events) -> {
                            if (!signal.hasValue()) {
                                return events.then(
                                        Mono.<StreamReceipt>empty());
                            }

                            PartEvent first = signal.get();

                            if (first instanceof FilePartEvent) {
                                AtomicLong bytes = new AtomicLong();

                                return events
                                        .map(PartEvent::content)
                                        .map(buffer -> {
                                            try {
                                                long total = bytes.addAndGet(
                                                        buffer.readableByteCount());
                                                if (total > MAX_FILE_BYTES) {
                                                    throw new ResponseStatusException(
                                                        HttpStatus.PAYLOAD_TOO_LARGE,
                                                        "file too large");
                                                }
                                                return total;
                                            } finally {
                                                DataBufferUtils.release(buffer);
                                            }
                                        })
                                        .then(Mono.fromSupplier(() ->
                                                new StreamReceipt(
                                                        "file", bytes.get())));
                            }

                            // Field metadata không dùng: vẫn phải release.
                            return events.doOnNext(event ->
                                    DataBufferUtils.release(event.content()))
                                    .then(Mono.<StreamReceipt>empty());
                        }))
                .collectList();
    }

    public record StoredFile(String key, String status) {}

    public record StreamReceipt(String type, long bytes) {}
}
~~~

Giải thích kỹ những quyết định:

1. Constructor tạo thư mục khi khởi động, không chạy blocking filesystem setup ở mỗi HTTP request. Thư mục chỉ để demo: production root phải được cấu hình/khóa quyền và **không** được người dùng tùy ý ghi symlink.
2. `upload` tạo key UUID, không sử dụng `FilePart.filename()`. `transferTo` được **return/compose**, Spring subscribe và trả 201 sau khi hoàn tất.
3. `inspect` đọc `Flux<PartEvent>` trực tiếp, không yêu cầu multipart map và không lưu file.
4. `windowUntil(PartEvent::isLast)` bảo đảm một file nhiều chunks được nhóm thành một window; `switchOnFirst` phân loại, `concatMap` xử lý theo thứ tự.
5. `AtomicLong` là biến **per-part**, không shared giữa requests. Giá trị được cộng từ `readableByteCount()`; nếu quá 10 MiB, phát `ResponseStatusException(PAYLOAD_TOO_LARGE)`.
6. `finally` release DataBuffer đã nhận, ngay cả khi phát lỗi giới hạn. Form-field buffers được consume/release thay vì bị filter bỏ.
7. `collectList()` chỉ gom `StreamReceipt` nhỏ, không gom bytes upload. Với request nhiều parts, ingress/reader limits và quotas phải bổ sung; đây không phải giải pháp chống mọi nguồn dữ liệu vô hạn.
8. Với cancellation/error ở upstream hoặc giữa pipeline, muốn bảo đảm mọi pooled buffer được giải phóng và file dở được xóa cần additional discard handling, integration tests và cleanup staging design. Đừng coi demo là hoàn chỉnh production.
9. Đường `upload` theo parsed reader có giới hạn disk 10 MiB/part; đường `events/inspect` không được bảo vệ tự động bởi cùng cấu hình, nên tự giới hạn bytes và cần hard request cap bên ngoài.
10. `ResponseEntity` trả key opaque, không public file path. Endpoint download riêng vẫn phải kiểm tra ACL nếu bổ sung.

### 10.5. UploadControllerTest.java

~~~java
package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive
        .AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UploadControllerTest {

    @Autowired
    WebTestClient client;

    private static ByteArrayResource testFile(byte[] body) {
        return new ByteArrayResource(body) {
            @Override
            public String getFilename() {
                return "client-name.txt";
            }
        };
    }

    @Test
    void filePartTransferStoresExactBytes() throws Exception {
        byte[] data = "spring reactive\n"
                .getBytes(StandardCharsets.UTF_8);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", testFile(data))
                .contentType(MediaType.TEXT_PLAIN);

        UploadController.StoredFile receipt = client.post()
                .uri("/api/uploads/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadController.StoredFile.class)
                .returnResult().getResponseBody();

        assertNotNull(receipt);
        assertTrue(receipt.key().endsWith(".bin"));
        assertTrue(!receipt.key().contains("client-name.txt"));

        Path stored = Path.of("target", "demo-uploads")
                .toAbsolutePath().normalize().resolve(receipt.key());

        try {
            assertTrue(Files.isRegularFile(stored));
            assertArrayEquals(data, Files.readAllBytes(stored));
        } finally {
            Files.deleteIfExists(stored);
        }
    }

    @Test
    void streamedPartEventsCountActualBytes() {
        byte[] data = "abcd".getBytes(StandardCharsets.UTF_8);
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("note", "demo");
        builder.part("file", testFile(data))
                .contentType(MediaType.TEXT_PLAIN);

        client.post()
                .uri("/api/uploads/events/inspect")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].type").isEqualTo("file")
                .jsonPath("$[0].bytes").isEqualTo(4);
    }

    @Test
    void missingRequiredNamedPartProducesClientError() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("note", "without file");

        client.post()
                .uri("/api/uploads/file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().is4xxClientError();
    }
}
~~~

**Điểm kiểm thử:**
- `WebTestClient` dùng embedded server trên random port, tạo multipart form request thực.
- `ByteArrayResource` chỉ dùng ở **client test** để có bytes/filename nhất quán, không chứng minh upload backend đọc toàn bộ file trong memory.
- Kiểm tra file được lưu dưới opaque key của server và **bytes thực tế khớp**.
- Route events bỏ qua form field và release buffer, receipt `bytes=4` cho file `abcd`.
- Thiếu `@RequestPart("file")` trả 4xx. Exact error schema/status có thể phụ thuộc auto-configuration của Boot/Framework.
- Test không đo performance, virus scan hoặc concurrency/slow clients; bổ sung integration/load/security tests trước production.

Chạy Maven:

~~~bash
./mvnw test
~~~

Trên Windows dùng `mvnw.cmd test`; nếu không có wrapper, dùng `mvn test` khi Maven đã cài.

## 11. Thử bằng curl

Chạy ứng dụng:

~~~bash
./mvnw spring-boot:run
~~~

### Lưu một file

~~~bash
curl -i -F "file=@sample.txt;type=text/plain"   http://localhost:8080/api/uploads/file
~~~

Kết quả **201 Created** với body dạng:

~~~json
{"key":"00000000-0000-0000-0000-000000000000.bin","status":"stored"}
~~~

UUID trong ví dụ là **placeholder mô tả định dạng**, kết quả thực được random, không phải giá trị cố định. File được tạo dưới thư mục `target/demo-uploads`.

### Đọc multipart như PartEvent stream

~~~bash
curl -i   -F "note=demo"   -F "file=@sample.txt;type=text/plain"   http://localhost:8080/api/uploads/events/inspect
~~~

Response có dạng:

~~~json
[{"type":"file","bytes":4}]
~~~

`4` là ví dụ nếu `sample.txt` thực chứa 4 bytes, như chữ `abcd`. Với file thực, kích thước kết quả bằng số bytes content đã đọc, **không** nhất thiết bằng số Unicode characters nhìn thấy. Kiểm tra bằng `wc -c sample.txt`.

### Field sai tên

~~~bash
curl -i -F "attachment=@sample.txt"   http://localhost:8080/api/uploads/file
~~~

Handler đòi named part `file`, vì thế request chỉ có `attachment` không phù hợp và thường trả 4xx.

**Lưu ý shell:** các dấu nối dòng `\` ở trên dành cho Linux/macOS/Git Bash. Trên Windows PowerShell dùng `curl.exe` trên một dòng, hoặc syntax line continuation của PowerShell. Không ghi password/API token vào lệnh hoặc logs public.

## 12. Bổ sung WebClient multipart và PartEvent outbound

Khi ứng dụng cần gửi file đến upstream, Spring WebFlux WebClient hỗ trợ `MultipartBodyBuilder`:

~~~java
MultipartBodyBuilder builder = new MultipartBodyBuilder();
builder.part("description", "demo");
builder.part("file", new FileSystemResource("/safe/path/data.bin"));

Mono<String> result = webClient.post()
        .uri("/ingest")
        .body(BodyInserters.fromMultipartData(builder.build()))
        .retrieve()
        .bodyToMono(String.class);
~~~

Đây là snippet trong service với `webClient` được cấu hình và storage file đã được server cho phép. Với remote domain, kiểm tra host allowlist, HTTPS, authentication, timeouts, retries, outbound quotas và việc có truyền nhầm untrusted headers/filenames hay không.

Có thể gửi multipart bằng **PartEvent**:

~~~java
Flux<PartEvent> outgoing = Flux.concat(
        FormPartEvent.create("description", "demo"),
        FilePartEvent.create("file", resource));

return webClient.post()
        .uri("/ingest")
        .body(outgoing, PartEvent.class)
        .retrieve()
        .bodyToMono(Void.class);
~~~

Đây là snippet minh họa có `resource` và `webClient` cần khai báo. `FilePartEvent.create` có thể tạo nhiều events cho file lớn. Không gọi `collectList()` toàn bộ events rồi tạo multipart request khác nếu mục tiêu là streaming.

### Relay inbound đến outbound mà không gom cả file

Một endpoint nhận `Flux<PartEvent>` có thể relay cho WebClient outbound, nhưng phải kiểm soát:
1. Headers and filenames có thể chứa input không tin cậy.
2. Pooled buffers: xác định ai là consumer cuối và ownership/release.
3. Nếu upstream lỗi hoặc client disconnect, cancel inbound đúng cách.
4. Limit bytes, parts, rate, connections và timeout ở cả hai đầu.
5. Không tự gọi `.subscribe()` trong controller rồi return 200 trước khi upload upstream hoàn thành.
6. Không gọi `.block()` trên Reactor Netty event loop.
7. Nếu phản hồi upstream không thành công, dịch status và cleanup staging/metadata theo contract.

## 13. Vòng đời upload production: staging → scan → publish

Một hệ thống có thể cần workflow:

~~~text
Client
  |
  v
Ingress/WAF: authentication, maximum body size, timeout, rate limit
  |
  v
Spring WebFlux: validate named parts + metadata, quota
  |
  v
Staging file/object (opaque key, isolated storage)
  |
  +-- stream bytes, count, compute hash, enforce hard limit
  |
  +-- file format and malware scanning
  |
  v
Atomic publish / object-store versioning
  |
  v
Database metadata: owner, tenant, ID, size, hash, MIME, state
  |
  v
201 Created / async job status
~~~

**Dọn dẹp khi lỗi là trách nhiệm bắt buộc:** file có thể đã được ghi nhưng DB metadata insert thất bại; hoặc DB commit trước rồi object publish bị lỗi. Tạo transaction/compensation/garbage-collection workflow, như trạng thái `PENDING`, `SCANNING`, `READY`, `FAILED`. Filesystem rename atomic chỉ hoạt động theo điều kiện filesystem; object-storage copy/move không phải transaction POSIX.

Nếu có concurrent uploads, kiểm tra collision, symlink, path traversal, race và partition/quota theo tenant. Với dữ liệu lớn và yêu cầu resume, cân nhắc tus hoặc presigned chunked uploads với checksum/version/idempotency key. Không mặc định một HTTP multipart POST là resumable.

### Timeout và status

- `400`: multipart malformed, part name không hợp lệ, metadata sai theo contract.
- `401/403`: thiếu authentication/quyền.
- `413`: request hoặc file vượt quota được ứng dụng quy định.
- `415`: content type không được hỗ trợ.
- `422`: file định dạng hợp lệ về HTTP nhưng không đạt validation nghiệp vụ (tùy API).
- `429`: vượt rate limit/concurrency.
- `503`: backend/storage đang quá tải.

Mã cụ thể cho parser overflow phụ thuộc framework/container; cần **test thực tế**, không bịa rằng mọi exception từ multipart reader đều tự map 413. Khi response đã commit, server có thể không thay sang JSON lỗi được; client phải phát hiện incomplete response/connection failure.

## 14. Lỗi thường gặp và hướng xử lý

| Vấn đề | Sai ở đâu | Cách làm đúng |
| --- | --- | --- |
| Dùng `MultipartFile` trong WebFlux | Servlet/MVC API khác | `FilePart` hoặc `PartEvent` |
| Bỏ Mono của `transferTo` | Pipeline không thực thi | Return/compose Mono |
| Dùng `filename()` làm path | Path traversal/overwrite | Server-generated opaque key |
| Tin `Content-Type` client | Fake MIME/extension | Magic-byte/decoder scan |
| Chỉ đọc event đầu | File bị cắt khi nhiều chunks | `windowUntil(isLast)`, đọc cả window |
| `filter` bỏ buffer mà không release | Netty pooled memory leak | Consume/release/relay |
| Release buffer trước khi write hoàn tất | Use-after-release | Ownership rõ và đúng overload |
| `collectList()` file buffers | Memory giữ tỷ lệ file size | Stream/write từng buffer |
| Mở `maxInMemorySize=-1` | Heap bị dùng không giới hạn | Threshold và disk quotas |
| Chỉ set memory threshold | Temp disk vẫn có thể đầy | maxDiskUsagePerPart, quotas |
| Coi maxParts parsed reader áp cho PartEvent | Event route dùng reader khác | Tự hard-limit events/bytes + ingress |
| Dùng `Files.readAllBytes` trong event loop | Blocking + memory | API async/chọn scheduler hoặc stream |
| Client không gửi cookie/auth với CORS | Preflight/SameSite/CSRF mismatch | Security/CORS config đúng |
| Test raw bytes thay multipart | Missing boundary/part | `curl -F`/MultipartBodyBuilder |
| Không dọn partial files | Disk orphan | Staging + doFinally/error cleanup |
| Response 201 trước khi storage hoàn tất | Publisher không được compose | `transferTo(...).thenReturn(...)` |
| “Non-blocking” là unlimited uploads | Pool/IO/disk vẫn hữu hạn | Concurrency budgets, timeouts |
| Cho file được truy cập công khai sau upload | Thiếu scanning/download ACL | Publish theo state và owner |

## 15. Câu hỏi ôn tập

**1. `FilePart` khác `MultipartFile` như thế nào?**  
`FilePart` là API reactive của WebFlux; `transferTo` trả `Mono<Void>`. `MultipartFile` là API thường dùng ở MVC Servlet.

**2. Khi gọi `file.transferTo(path)` nhưng không return/subscribe Mono, chuyện gì xảy ra?**  
Việc chuyển file không được đảm bảo chạy; cần đưa Mono vào pipeline mà WebFlux subscribe.

**3. Vì sao `PartEvent::isLast` quan trọng?**  
Một file có thể gồm nhiều FilePartEvents; `isLast` đánh dấu event cuối của mỗi part.

**4. `switchOnFirst` giúp gì?**  
Nhìn event đầu để chọn xử lý form/file mà vẫn giữ toàn bộ events cho consumer.

**5. Vì sao phải release DataBuffer?**  
Pooled buffers có reference count; thiếu release có thể làm rò bộ nhớ ngoài heap.

**6. Nếu đặt `maxInMemorySize=64KiB` thì có nghĩa file 65KiB bị từ chối không?**  
Không: với default parsed file reader đây thường là ngưỡng spool disk; cần maxDiskUsagePerPart và request-size limit riêng.

**7. Có nên dùng filename gốc để resolve filesystem path?**  
Không. Nó do client kiểm soát; dùng storage key server-generated.

**8. Event streaming có tự bị chặn bởi maxParts của DefaultPartHttpMessageReader không?**  
Không nên suy luận như vậy. PartEvent reader và parsed multipart reader khác nhau; cần giới hạn tương ứng.

**9. Vì sao test receipt bytes phải so số byte thay vì ký tự?**  
Vì UTF-8/nhị phân có độ dài bytes khác số ký tự Unicode.

**10. PartEvent luôn nhanh hơn FilePart?**  
Không. Nó đổi cách truy cập và thêm quản lý buffer; cần benchmark đầy đủ latency, memory, disk I/O và concurrency.

## 16. Checklist trước khi đưa lên production

1. Chọn API đúng: named parts → `@RequestPart FilePart`; streaming tuần tự → `Flux<PartEvent>`.
2. Giữ publisher trong return chain; không tự subscribe/block trên event loop.
3. Generate storage ID ở server, tránh raw filename, phân quyền filesystem và chống symlink/race.
4. Đọc/ghi DataBuffers có ownership rõ, release trên success, error/cancel/discard.
5. Kiểm tra hạn mức bytes/file, parts/request, memory/disk, temp storage, request duration, max concurrency.
6. Không cho input có `Content-Type`/filename giả bypass MIME/scan rules.
7. Staging/quarantine trước scan/publish, cleanup partial/orphan files và quản lý idempotency.
8. Với outbound WebClient relay, đặt timeout, connection pool limits, trusted target, authentication/headers.
9. Với download, kiểm tra ACL và Content-Disposition/Range/cache policy riêng.
10. Đo p95/p99 upload latency, HTTP status/error rates, disk usage, memory/Netty buffers, cleanup failures, proxy size rejection.
11. Test HTTP multipart với WebTestClient/curl, nhiều chunks, file rỗng, malformed multipart, metadata JSON, missing parts, oversized uploads và disconnects.
12. Không coi ví dụ demo hai endpoint là bộ upload security hoàn chỉnh.

## Tài liệu chính thức

- [Spring Framework 6.2 — Multipart Content / annotated controllers](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/multipart-forms.html)
- [Spring Framework 6.2 — WebFlux Reactive Core và Multipart Limits](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/reactive-spring.html)
- [Spring Framework 6.2 — FilePart API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/multipart/FilePart.html)
- [Spring Framework 6.2 — PartEvent API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/multipart/PartEvent.html)
- [Spring Framework 6.2 — DefaultPartHttpMessageReader](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/codec/multipart/DefaultPartHttpMessageReader.html)
- [Spring Framework 6.2 — DataBufferUtils](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/buffer/DataBufferUtils.html)
- [Spring Framework 6.2 — WebClient Multipart request](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-webclient/client-body.html)
- [Spring Framework 6.2 — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html)
- [RFC 7578 — multipart/form-data](https://www.rfc-editor.org/rfc/rfc7578)
- [OWASP — File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-006">@ExceptionHandler, @RestControllerAdvice và ProblemDetail trong WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

# Spring WebFlux — @ExceptionHandler, @RestControllerAdvice và ProblemDetail

## 1. Mục tiêu

Spring WebFlux cần một hợp đồng lỗi HTTP nhất quán ngay cả khi exception đến từ một `Mono`, `Flux`, binding request, validation, business service hoặc framework handler. Một API tốt phải truyền đạt lỗi cho client theo **HTTP status + error code + body an toàn**, thay vì gửi stacktrace hoặc trả 200 cho mọi thất bại.

Bài này tập trung vào annotated WebFlux controllers:

- Xử lý riêng một controller bằng `@ExceptionHandler`.
- Tái sử dụng chính sách xử lý lỗi giữa controllers bằng `@RestControllerAdvice`.
- Dùng `ProblemDetail` theo **RFC 9457 — Problem Details for HTTP APIs** và media type `application/problem+json`.
- Hiểu `ErrorResponse`, `ErrorResponseException`, `ResponseStatusException` và `ResponseEntityExceptionHandler`.
- Phân biệt `WebExchangeBindException` với `HandlerMethodValidationException`; bảo toàn HTTP 400, 404, 405, 409, 415, 500 và 504.
- Hiểu thứ tự giữa local exception methods, nhiều advice, `@Order`, root/cause matches.
- Biết giới hạn của controller advice đối với `WebFilter`, WebExceptionHandler, Spring Security, reverse proxy và response đã committed.
- Tạo ví dụ chạy độc lập **Java 17 / Spring Boot 3.5.0 / Spring Framework 6.2 / Maven**, kiểm bằng `WebTestClient`.
- Thiết kế error contract, i18n, requestId/traceId, logging và bảo vệ thông tin nhạy cảm.

Đề bài thuộc nhóm **Controller WebFlux**. Spring MVC cũng có những annotations cùng tên nhưng dùng Servlet `DispatcherServlet`; WebFlux dùng reactive `DispatcherHandler` và các types tương ứng. Không copy WebMVC-specific exception-handler signatures sang WebFlux mà không đối chiếu Javadoc.

## 2. Request đi qua những lớp xử lý nào?

Mô hình khái quát:

~~~text
Browser / API client
        |
        v
Reactor Netty / HTTP server
        |
        v
WebHttpHandlerAdapter + WebFilter chain
        |
        v
DispatcherHandler
        |
        +--> HandlerMapping (chọn route)
        |
        +--> HandlerAdapter (gọi controller)
        |
        +--> Mono / Flux / return value
        |
        +--> @ExceptionHandler tại controller
        |             hoặc @RestControllerAdvice
        |
        +--> HandlerResultHandler / codecs
        |
        v
HTTP status, headers, response body
~~~

Ngoài annotated controller resolution còn có các `WebExceptionHandler` trong WebHandler chain, lỗi được Spring Boot mặc định xử lý, phản hồi do Spring Security reactive tạo, và gateway/proxy failures. Chúng **không đồng nhất** với controller advice.

| Phạm vi | Cơ chế | Thích hợp |
| --- | --- | --- |
| Một annotated controller | `@ExceptionHandler` | Lỗi có hợp đồng đặc biệt của controller |
| Nhiều annotated controllers | `@RestControllerAdvice` | Lỗi domain, validation, API error schema |
| Built-in WebFlux exceptions | `ResponseEntityExceptionHandler` | Chuẩn hóa ErrorResponse của Spring |
| Web handler/filter chain | `WebExceptionHandler` | Lỗi nằm ngoài annotated controller processing |
| Boot reactive fallback | `ErrorWebExceptionHandler` | Lỗi chưa được các tầng trên xử lý |
| Reactive Spring Security | `SecurityWebFilterChain`, entry point/access denied handler | 401/403 liên quan authentication/authorization |
| Edge/gateway | Envoy/Nginx/CDN | Có thể tự trả 502/504/429 |

**Không giả định** một `@RestControllerAdvice` sẽ bắt tất cả exceptions từ `WebFilter`. WebFilter thường chạy ngoài `DispatcherHandler`, do vậy lỗi có thể được xử lý bởi WebExceptionHandler/Boot error layer. Và nếu proxy tự trả 504 thì request thậm chí chưa từng tới controller.

### 2.1. Exception và Reactor error signal

~~~java
@GetMapping("/api/items/{id}")
Mono<Item> get(@PathVariable long id) {
    return service.find(id);
}
~~~

Nếu `service.find(id)` trả một `Mono.error(new ItemNotFoundException(...))` trước khi response committed, Spring WebFlux có thể định tuyến lỗi tới controller-local hoặc global exception handler thích hợp.

Một ngoại lệ **được throw đồng bộ** trong quá trình handler invocation hoặc một **onError signal** từ publisher thuộc lifecycle request đều có thể tham gia cơ chế xử lý WebFlux, nhưng chi tiết và thời điểm phụ thuộc response state.

Tránh gọi `subscribe()` ngay trong controller chỉ để chạy một task rồi return `Mono.just("ok")`: tác vụ fire-and-forget sẽ ra ngoài request lifecycle, lỗi/cancellation không còn do HTTP handler quản lý tự nhiên.

## 3. @ExceptionHandler trong controller

Ví dụ:

~~~java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    Mono<OrderDto> get(@PathVariable long id) {
        return orders.find(id);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ProblemDetail> missing(
            OrderNotFoundException exception) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng");
        p.setProperty("code", "ORDER_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(p);
    }
}
~~~

Đây là **fragment**: `OrderService`, `OrderDto`, exception và imports chưa được định nghĩa; dự án độc lập nằm ở mục 13.

### 3.1. Annotation matching

Có thể khai báo exception type trong annotation:

~~~java
@ExceptionHandler(OrderNotFoundException.class)
ProblemDetail handle(OrderNotFoundException error) { ... }
~~~

Hoặc để framework suy từ method parameter:

~~~java
@ExceptionHandler
ProblemDetail handle(OrderNotFoundException error) { ... }
~~~

Nên ưu tiên một exception type rõ ràng và chữ ký cụ thể, tránh bắt mọi `RuntimeException` nếu bạn thật sự chỉ cần xử lý một lỗi domain.

Spring có thể match root exception hoặc một cause trong wrapper exception. Một wrapped exception không có nghĩa handler luôn nhận lớp wrapper; hãy test nested causes và chọn rõ ràng loại exception phù hợp.

### 3.2. Tham số và return types

WebFlux exception handler có thể nhận `Throwable` tương ứng, `ServerWebExchange`, request/response interfaces và một số context arguments. Có thể trả:

- `ProblemDetail`.
- `ResponseEntity<ProblemDetail>`.
- `Mono<ProblemDetail>` hoặc `Mono<ResponseEntity<ProblemDetail>>`.
- `ErrorResponse` hoặc DTO lỗi khác mà codecs hỗ trợ.

Ví dụ asynchronous return:

~~~java
@ExceptionHandler(InventoryUnavailableException.class)
Mono<ResponseEntity<ProblemDetail>> unavailable(
        InventoryUnavailableException exception) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Dịch vụ kiểm tra tồn kho đang bận");
    p.setProperty("code", "INVENTORY_UNAVAILABLE");
    return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE).body(p));
}
~~~

Đây là fragment. Nếu không thực sự cần compose non-blocking operations, trả trực tiếp `ProblemDetail`/`ResponseEntity` đơn giản hơn. Không thực hiện blocking network/database calls trong exception handler chỉ để làm đẹp nội dung lỗi.

Các `@ExceptionHandler` methods không hỗ trợ tất cả arguments của `@RequestMapping`, đặc biệt request-body và `@ModelAttribute`-related method arguments; không coi đây là một handler input-binding method thứ hai.

## 4. @RestControllerAdvice và phạm vi áp dụng

`@RestControllerAdvice` kết hợp `@ControllerAdvice` với `@ResponseBody` để các handler methods có body được serialize qua HttpMessageWriter. Nó phù hợp cho một API thống nhất toàn hệ thống.

~~~java
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail missing(OrderNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy tài nguyên");
        p.setProperty("code", "ORDER_NOT_FOUND");
        return p;
    }
}
~~~

**Khác biệt:** `@ControllerAdvice` cũng có thể dùng với `@ExceptionHandler`, nhưng `@RestControllerAdvice` thể hiện rõ ý định trả response body, không view template.

### 4.1. Local handler trước global handler

Nếu một controller có method `@ExceptionHandler(OrderNotFoundException.class)` và một global advice cũng bắt loại đó, local controller method thường được xét trước global advice cho lỗi phát sinh tại controller tương ứng.

Điều này có ích cho exception cần xử lý rất đặc thù. Nhưng lạm dụng local handlers có thể làm cùng error code trả schema/status khác nhau ở nhiều endpoints. Nên chuẩn hóa domain error catalog và chỉ override khi thật sự có một hợp đồng khác.

### 4.2. Giới hạn advice bằng selectors

Có thể chỉ áp dụng advice cho một package:

~~~java
@RestControllerAdvice(basePackages = "com.example.api")
class PublicApiErrors {
}
~~~

Hoặc theo controller annotation:

~~~java
@RestControllerAdvice(annotations = RestController.class)
class RestOnlyErrors {
}
~~~

Có thể dùng `basePackageClasses` thay chuỗi package để refactor an toàn hơn. Các selector có runtime matching cost, vì thế không thêm nhiều selector phức tạp nếu một global advice đủ.

### 4.3. Nhiều advice và @Order

Ví dụ:

~~~java
@Order(-1)
@RestControllerAdvice
class DomainErrors {
}

@Order(100)
@RestControllerAdvice
class OptionalFallbackErrors {
}
~~~

Đây là fragments. Advice được xét theo `Ordered`/`@Order`. Với một advice, root match ưu tiên cause match, nhưng **cause match trong advice có thứ tự ưu tiên cao hơn** có thể thắng root match của advice thấp hơn.

Rủi ro thường gặp: một advice ưu tiên cao có:

~~~java
@ExceptionHandler(Exception.class)
ProblemDetail everything(Exception ex) {
    return ProblemDetail.forStatus(
            HttpStatus.INTERNAL_SERVER_ERROR);
}
~~~

Nó có thể vô tình đổi những framework errors đúng 400/405/415 thành 500. Với Spring Boot, đây còn có thể can thiệp vào ProblemDetails handler được auto-configure. Chỉ viết catch-all khi bạn thực sự quản lý **toàn bộ** HTTP error semantics và có regression tests đầy đủ.

## 5. RFC 9457 — Problem Details

RFC 9457 cung cấp định dạng error response có thể đọc bởi người và máy.

~~~http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Không tìm thấy tài nguyên",
  "instance": "/api/widgets/42"
}
~~~

### 5.1. Năm trường cơ bản

| Field | Ý nghĩa | Best practice |
| --- | --- | --- |
| `type` | URI xác định problem type | URI ổn định, có thể dẫn đến tài liệu; mặc định `about:blank` |
| `title` | Nhãn ngắn gọn của loại lỗi | Cố gắng ổn định theo type, có thể i18n |
| `status` | HTTP status | Đồng nhất status line thực tế |
| `detail` | Thông tin dễ hiểu cho occurrence | Không chứa SQL, secret, stacktrace, PII |
| `instance` | URI reference đến occurrence | Có thể là request path, tránh đưa token/query nhạy cảm |

Các extension properties có thể được bổ sung theo hợp đồng API: `code`, `requestId`, `invalidFields` và các fields khác. RFC không bắt buộc tất cả clients dùng fields mở rộng.

### 5.2. Tạo ProblemDetail trong Spring

~~~java
ProblemDetail p = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Trạng thái đơn hàng không cho phép thao tác");
p.setTitle("Conflict");
p.setProperty("code", "ORDER_CONFLICT");
~~~

Khi return trực tiếp `ProblemDetail`, Spring có thể lấy `status` để đặt HTTP status và ưu tiên `application/problem+json`. Với `ResponseEntity`, phải bảo đảm status line không mâu thuẫn:

~~~java
return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
~~~

Đừng trả HTTP 200 với body có `status=409`. HTTP clients/proxy/metrics sẽ nhìn status line trước.

### 5.3. type URI

Khi cần stable machine-readable distinction:

~~~java
p.setType(URI.create(
        "https://api.example.com/problems/order-conflict"));
p.setTitle("Order conflict");
p.setProperty("code", "ORDER_CONFLICT");
~~~

URL chỉ là ví dụ. Nếu sử dụng type URIs thật, nên cung cấp tài liệu cho clients, giữ URI ổn định qua thời gian và không xây nó từ request `Host` chưa tin cậy.

### 5.4. Extension properties ra JSON top-level

~~~java
p.setProperty("code", "WIDGET_NOT_FOUND");
p.setProperty("requestId", "example-7ab...");
~~~

Spring kết hợp Jackson `ProblemDetailJacksonMixin`, cho phép map properties được unwrapped thành JSON fields top-level:

~~~json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Không tìm thấy tài nguyên",
  "instance": "/api/widgets/42",
  "code": "WIDGET_NOT_FOUND",
  "requestId": "example-7ab..."
}
~~~

`requestId` trong ví dụ không phải giá trị thật. Không tái sử dụng một singleton `ProblemDetail` rồi mutate cho nhiều requests: nó là object có trạng thái có thể chỉnh, dễ gây data race và lẫn metadata giữa clients.

### 5.5. Giữ meaning của status và code khác nhau

- HTTP status 404: loại vấn đề protocol.
- `code=WIDGET_NOT_FOUND`: loại vấn đề của ứng dụng.
- `requestId`: tìm request cụ thể trong logs, không dùng làm error type.
- `traceId`: liên kết nhiều service spans.
- `detail/title`: văn bản hiển thị, có thể được dịch.

Client phải phân nhánh theo status/code/type, không parse chuỗi Vietnamese trong detail để ra quyết định nghiệp vụ.

## 6. ErrorResponse và ErrorResponseException

Spring Framework 6.2 cung cấp:

- `ProblemDetail`: model biểu diễn response body.
- `ErrorResponse`: contract chứa status, headers, body và message codes/arguments.
- `ErrorResponseException`: exception có thể mang error response.
- `ResponseStatusException`: dùng khi request handler muốn biểu diễn HTTP status/optional reason.
- `ResponseEntityExceptionHandler`: base class dành cho global advice xử lý built-in WebFlux exceptions theo RFC 9457.

Ví dụ `ResponseStatusException`:

~~~java
return repository.findById(id)
        .switchIfEmpty(Mono.error(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found")));
~~~

Đây là fragment. Với một ứng dụng nhiều use cases, domain service thường nên ném `WidgetNotFoundException` không gắn HTTP status, còn tầng WebFlux advice quyết định 404 + code.

### 6.1. ErrorResponseException khi muốn exception mang metadata

~~~java
ProblemDetail p = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Version mismatch");

throw new ErrorResponseException(
        HttpStatus.CONFLICT, p, null);
~~~

Đây là fragment với Spring 6.2 API. Cần nhất quán với error catalog, tránh trộn nhiều loại exception cho cùng business failure mà không có chính sách.

### 6.2. WebFlux ResponseEntityExceptionHandler

Import của WebFlux (khác Servlet MVC):

~~~java
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
~~~

Một cách triển khai:

~~~java
@RestControllerAdvice
class FrameworkErrorAdvice extends ResponseEntityExceptionHandler {
}
~~~

Spring base class xử lý `ErrorResponse` exceptions và hỗ trợ i18n/message code resolution. Khi override protected methods, dùng đúng **WebFlux 6.2 signatures**, không copy methods từ MVC `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler`.

Không bắt buộc vừa tự kế thừa base class vừa để Boot tạo handler trùng mà không định nghĩa precedence.

### 6.3. Spring Boot Problem Details

Với Spring Boot 3.5 WebFlux:

~~~properties
spring.webflux.problemdetails.enabled=true
~~~

Boot có thể auto-configure một `ResponseEntityExceptionHandler` xử lý built-in WebFlux errors theo ProblemDetail. Điều này phù hợp với mô hình:

1. Boot giải quyết những errors của framework như malformed body, unsupported media type, method not allowed.
2. Một custom `@RestControllerAdvice` chỉ xử lý specific domain errors.
3. Khi custom validation body format, đặt advice phù hợp `@Order` và test interaction với Boot handler.

Spring docs ghi nhận advice Boot cho trường hợp này có order 0; custom advice có thể dùng `@Order(-1)` để được chọn trước cho specific exceptions.

**Không có bảo đảm** property này đồng nhất phản hồi của Security filter, reverse proxy, raw WebFilter và streaming transport failures. Chúng thuộc những layers khác.

## 7. Validation trong WebFlux: hai loại exception

Spring WebFlux 6.2 phân biệt validation theo từng argument và method validation.

### 7.1. WebExchangeBindException

Thông thường với `@Valid @RequestBody` trên DTO:

~~~java
record CreateWidgetRequest(
        @NotBlank @Size(max = 80) String name) {
}

@PostMapping("/widgets")
Mono<Widget> create(
        @Valid @RequestBody CreateWidgetRequest request) {
    return service.create(request);
}
~~~

Khi body không đạt Bean Validation, WebFlux có thể ném `WebExchangeBindException`. Để hoạt động, classpath cần Jakarta Bean Validation provider, ví dụ `spring-boot-starter-validation`.

Exception có các field/global errors để app map thành ProblemDetail phù hợp. Không gửi rejected value của password, token hoặc raw body về client.

### 7.2. HandlerMethodValidationException

Khi annotation constraint đặt **trực tiếp** trên method parameter:

~~~java
@GetMapping("/widgets/{id}")
Mono<Widget> get(@PathVariable @Min(1) long id) {
    return service.find(id);
}
~~~

Spring WebFlux built-in method validation có thể ném `HandlerMethodValidationException` nếu `id=0`. Loại này cũng có thể phát sinh khi return-value constraints sai.

**Không gộp mọi HandlerMethodValidationException thành 400.** Với return-value validation violation, status có thể là 500 vì lỗi nằm phía server; dùng `ex.getStatusCode()`/validation source để bảo toàn semantics.

Nếu class controller có `@Validated` mức class, method validation có thể qua AOP path thay vì built-in support. Với Spring 6.2, đừng thêm class-level `@Validated` chỉ để kích hoạt constraints nếu chưa kiểm cơ chế.

### 7.3. Ví dụ validation error body an toàn

~~~json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Dữ liệu đầu vào không hợp lệ",
  "code": "VALIDATION_FAILED",
  "invalidFields": ["email", "name"]
}
~~~

Trường `invalidFields` nên dùng safe field names/violation codes, sort/deduplicate và có giới hạn. Không trả:

~~~json
{
  "field": "password",
  "rejectedValue": "plain-text-user-password"
}
~~~

Nếu cần localized field messages, dùng `MessageSource` và mã message được whitelist. Raw validation messages có thể chứa interpolated input; hãy review trước khi public.

### 7.4. Những lỗi input khác vẫn thuộc framework

| Lỗi | HTTP status điển hình |
| --- | --- |
| Body không có hoặc JSON sai | 400 |
| Bean validation input | 400 |
| Method parameter constraint | 400 |
| Method return constraint | 500 |
| Unsupported Content-Type | 415 |
| Không thỏa Accept | 406 |
| HTTP method không được hỗ trợ | 405 |
| Query/header bắt buộc bị thiếu | 400 |

Không để một `@ExceptionHandler(Exception.class)` nuốt các status này và trả 500 chung.

## 8. Error status và domain code

| Điều kiện | Status | Code ví dụ |
| --- | --- | --- |
| Validation/input sai | 400 | `VALIDATION_FAILED` |
| Chưa đăng nhập | 401 | `AUTH_REQUIRED` |
| Không có quyền | 403 | `ACCESS_DENIED` |
| Không có resource | 404 | `WIDGET_NOT_FOUND` |
| Method không hỗ trợ | 405 | Built-in handling |
| Accept không phù hợp | 406 | Built-in handling |
| Business conflict | 409 | `WIDGET_CONFLICT` |
| ETag precondition sai | 412 | `PRECONDITION_FAILED` |
| Content-Type không phù hợp | 415 | Built-in handling |
| Rate limit | 429 | `RATE_LIMITED` |
| Lỗi nội bộ | 500 | `INTERNAL_ERROR` |
| Service phụ trợ tạm ngừng | 503 | `UPSTREAM_UNAVAILABLE` |
| Upstream timeout (gateway role) | 504 | `UPSTREAM_TIMEOUT` |
| Client đóng socket/cancel | Không tự có một HTTP status chuẩn mới | Cleanup/cancel, không gửi thêm body |

Nginx có thể ghi 499 cho client closed request trong access logs; đó là **non-standard proxy log status**, không phải status chuẩn mà WebFlux nên cố gửi tới một client đã ngắt.

Status/code mapping phải gắn với semantics API. Không auto-map mọi timeout thành 504 nếu dịch vụ không hoạt động như gateway/upstream proxy; với use case khác, 503 hoặc contract riêng có thể hợp lý.

## 9. Reactive exception flow

### 9.1. Throw và Mono.error

Trong một reactive service:

~~~java
return repository.findById(id)
        .switchIfEmpty(Mono.error(
                new WidgetNotFoundException()));
~~~

`Mono.error` truyền lỗi vào subscriber; WebFlux có thể xử lý trong controller exception resolution khi request chưa committed.

Không dùng:

~~~java
remoteOperation().subscribe(
        value -> log.info("done"),
        ex -> log.warn("error", ex));
return Mono.just("started");
~~~

trong controller cho công việc thuộc request. Subscription được tạo thủ công có lifecycle riêng; `@ExceptionHandler` không còn bảo đảm xử lý kết quả/error của nó như HTTP response.

### 9.2. Service recovery vs HTTP error rendering

~~~java
return primary.fetch(id)
        .onErrorResume(TransientUpstreamException.class,
                error -> cache.find(id));
~~~

Nếu recovery thành công, advice không cần chạy. `onErrorResume` **không** “quay lại tiếp tục cùng Flux nguồn” ở vị trí lỗi; nó chuyển sang publisher thay thế. Dùng fallback khi API contract cho phép cache/stale data.

### 9.3. Translate technical error

~~~java
return remote.fetch(id)
        .timeout(Duration.ofSeconds(2))
        .onErrorMap(TimeoutException.class,
                UpstreamTimeoutException::new);
~~~

Một advice có thể map `UpstreamTimeoutException` sang `504 UPSTREAM_TIMEOUT`. Đừng để service trực tiếp quyết định `ResponseEntity` nếu muốn tái sử dụng domain code.

### 9.4. Cancellation khác onError

Browser đóng SSE hoặc subscriber gọi `cancel()` không nhất thiết phát `onError`. Quản lý cleanup bằng `using/usingWhen/doFinally` theo publisher lifecycle; không gửi HTTP 500 chỉ vì downstream không còn cần dữ liệu.

## 10. Error boundary ngoài ControllerAdvice

### 10.1. WebFilter

Trong WebFlux, `WebFilter` chạy ở web handler chain, có thể trước annotated controller resolution:

~~~java
@Component
class ExampleFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             WebFilterChain chain) {
        return chain.filter(exchange);
    }
}
~~~

Đây là fragment minh họa. Nếu filter phát exception, controller advice không bảo đảm nhận nó. Cần đánh giá `WebExceptionHandler` hoặc Boot ErrorWebExceptionHandler nếu thực sự quản lý filter-level errors. Custom handlers phải tính đến ordering, cancellation và committed status.

### 10.2. Spring Security

Reactive Security tạo 401/403 qua `SecurityWebFilterChain`, `ServerHttpSecurity`, authentication entry points và access denied handlers. Trạng thái auth có thể được xử lý **trước khi controller chạy**, do vậy không dùng domain advice như công cụ duy nhất để chuẩn hóa Security errors.

Không nới lỏng auth chỉ để CORS preflight “hết báo lỗi”; thiết kế Reactive CORS/security đúng các tầng.

### 10.3. WebClient upstream errors

`WebClient.retrieve()` có thể phát `WebClientResponseException` cho 4xx/5xx response từ upstream. HTTP 404 của upstream **không mặc định** có nghĩa client API của bạn phải nhận 404. Hãy map theo domain:

- Upstream authoritative catalog không có item → có thể 404.
- Upstream optional service không có cache → có thể fallback.
- Upstream 503/network timeout → có thể 503/504.
- Upstream 401 do backend credential lỗi → không được tùy tiện leak body/credential details.

Không echo raw upstream body hoặc query URLs chứa tokens vào public ProblemDetail.

### 10.4. Gateway/proxy

Một CDN, gateway hoặc ingress có thể tạo 502/504 bên ngoài Spring. Controller advice không thể áp dụng cho response đó. Khi cần hợp đồng lỗi thống nhất cross-service, phải thỏa thuận schema/status giữa services và gateway, rồi test ở external ingress.

## 11. HTTP response đã committed: giới hạn không thể bỏ qua

Với Server-Sent Events:

~~~http
HTTP/1.1 200 OK
Content-Type: text/event-stream

event: progress
data: {"percent":20}
~~~

Sau khi status/headers đã gửi, một `Flux` error **không thể đổi cùng response thành**:

~~~http
HTTP/1.1 500 Internal Server Error
Content-Type: application/problem+json

{"status":500,"detail":"Internal server error"}
~~~

Nếu stream vẫn usable và lỗi là business-level, có thể gửi một **SSE application error event**:

~~~text
event: app-error
data: {"code":"JOB_FAILED","message":"Không thể tiếp tục"}

~~~

Status HTTP vẫn là 200. Frontend phải phân biệt transport success với application event outcome.

Khi transport write lỗi hoặc client đã disconnect, **không cố write ProblemDetail JSON hay error SSE event thứ hai**. Dừng publisher, cleanup resource, ghi log/metrics thích hợp. Một exception handler không thể đảo ngược bytes đã gửi; đây là HTTP protocol constraint.

## 12. Thiết kế an toàn, i18n, correlation

### 12.1. Contract gợi ý

~~~json
{
  "type": "https://api.example.com/problems/widget-not-found",
  "title": "Widget not found",
  "status": 404,
  "detail": "Không tìm thấy tài nguyên yêu cầu",
  "instance": "/api/widgets/123",
  "code": "WIDGET_NOT_FOUND",
  "requestId": "example-2b7..."
}
~~~

HTTP status, `code/type` mới là machine-readable source of truth; `detail/title` có thể dịch hoặc cải tiến nội dung.

### 12.2. Redact sensitive data

Không trả:

- Exception stacktrace / exception class + Java package nội bộ.
- SQL query, database URL, username/password, access token/cookie.
- File system paths, internal DNS names, private IP/port.
- Raw upstream response body có thể chứa customer records.
- Validation rejected values của password, API key hoặc personal data.

Tránh:

~~~java
problem.setDetail(exception.getMessage());
~~~

với generic unexpected errors. Thay bằng public message constant an toàn và log thông tin hữu ích **server-side**. Logs cũng phải bảo vệ secrets và có retention/access policy.

### 12.3. Request ID và trace ID

- Request ID phân biệt request cụ thể.
- Trace ID liên kết nhiều downstream spans trong distributed tracing.
- Error code mô tả loại lỗi ổn định.

Với cross-origin SPA đọc response header `X-Request-ID`, cần CORS exposedHeaders thích hợp. Không tin raw header do client tự gửi làm authority cho log correlation nếu không validate/normalize.

### 12.4. ErrorMessage internationalization

Spring `ErrorResponse` có message codes/arguments cho `type/title/detail`; `ResponseEntityExceptionHandler` hỗ trợ giải quyết thông qua `MessageSource`. Bạn có thể dịch title/detail theo locale, trong khi `code` và HTTP status không đổi.

Một ứng dụng i18n cần xác định locale resolution, fallback language và safe interpolation. Không để error detail locale-dependent trở thành điều kiện rẽ nhánh của frontend SDK.

## 13. Dự án Maven độc lập: WebFlux ProblemDetail API

Demo gồm:

- POST `/api/widgets` tạo Widget, dùng `@Valid @RequestBody`.
- GET `/api/widgets/{id}`, dùng direct constraint `@Min(1)`.
- GET `/api/demo/conflict` trả 409.
- GET `/api/demo/timeout` dùng Reactor timeout và trả 504.
- GET `/api/demo/crash` trả 500 generic, không lộ exception message có secret.
- Built-in 405, 415 và malformed JSON được Boot Problem Details xử lý.
- WebTestClient tests chứng minh HTTP status, code, response schema và redaction.

### 13.1. Cấu trúc

~~~text
webflux-problem-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/WebFluxProblemApplication.java
    │   └── resources/application.properties
    └── test/java/demo/WebFluxProblemTest.java
~~~

### 13.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>webflux-problem-demo</artifactId>
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
            <artifactId>spring-boot-starter-validation</artifactId>
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

### 13.3. application.properties

~~~properties
server.port=8080
spring.webflux.problemdetails.enabled=true

# Production: configure authentication, monitoring and safe logging.
~~~

### 13.4. WebFluxProblemApplication.java

Lưu tất cả code dưới đây trong **một file** `src/main/java/demo/WebFluxProblemApplication.java`. Chỉ có một top-level public class, các record/class khác package-private; Java 17 hỗ trợ cấu trúc này.

~~~java
package demo;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class WebFluxProblemApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxProblemApplication.class, args);
    }
}

record CreateWidgetRequest(
        @NotBlank @Size(max = 80) String name) {
}

record Widget(long id, String name) {
}

class WidgetNotFoundException extends RuntimeException {
}

class WidgetConflictException extends RuntimeException {
}

class UpstreamTimeoutException extends RuntimeException {
    UpstreamTimeoutException(Throwable cause) {
        super("Upstream timeout", cause);
    }
}

class SystemFaultException extends RuntimeException {
    SystemFaultException(String privateDetail) {
        super(privateDetail);
    }
}

@RestController
@RequestMapping("/api")
class WidgetController {
    private final AtomicLong ids = new AtomicLong(100);
    private final Map<Long, Widget> widgets = new ConcurrentHashMap<>();

    @GetMapping(path = "/widgets/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Widget> get(@PathVariable @Min(1) long id) {
        return Mono.defer(() -> {
            Widget widget = widgets.get(id);
            return widget != null
                    ? Mono.just(widget)
                    : Mono.error(new WidgetNotFoundException());
        });
    }

    @PostMapping(path = "/widgets",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<Widget>> create(
            @Valid @RequestBody CreateWidgetRequest request) {
        return Mono.fromSupplier(() -> {
            long id = ids.incrementAndGet();
            Widget widget = new Widget(id, request.name());
            widgets.put(id, widget);
            URI location = URI.create("/api/widgets/" + id);
            return ResponseEntity.created(location).body(widget);
        });
    }

    @GetMapping("/demo/conflict")
    Mono<String> conflict() {
        return Mono.error(new WidgetConflictException());
    }

    @GetMapping("/demo/timeout")
    Mono<String> timeout() {
        return Mono.delay(Duration.ofMillis(300))
                .map(tick -> "late")
                .timeout(Duration.ofMillis(40))
                .onErrorMap(TimeoutException.class,
                        UpstreamTimeoutException::new);
    }

    @GetMapping("/demo/crash")
    Mono<String> crash() {
        return Mono.error(new SystemFaultException(
                "jdbc-password=super-secret-value"));
    }
}

@Order(-1)
@RestControllerAdvice
class WidgetApiErrors {

    @ExceptionHandler(WidgetNotFoundException.class)
    ProblemDetail missing(WidgetNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "WIDGET_NOT_FOUND", "Không tìm thấy tài nguyên");
    }

    @ExceptionHandler(WidgetConflictException.class)
    ProblemDetail conflict(WidgetConflictException ex) {
        return problem(HttpStatus.CONFLICT,
                "WIDGET_CONFLICT",
                "Tài nguyên không ở trạng thái phù hợp");
    }

    @ExceptionHandler(UpstreamTimeoutException.class)
    ProblemDetail timeout(UpstreamTimeoutException ex) {
        return problem(HttpStatus.GATEWAY_TIMEOUT,
                "UPSTREAM_TIMEOUT",
                "Dịch vụ phụ trợ không phản hồi kịp thời");
    }

    @ExceptionHandler(SystemFaultException.class)
    ProblemDetail internal(SystemFaultException ex) {
        // Không trả ex.getMessage() cho HTTP client.
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Đã xảy ra lỗi hệ thống");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail invalidBody(WebExchangeBindException ex) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED", "Dữ liệu đầu vào không hợp lệ");
        p.setProperty("invalidFields",
                ex.getFieldErrors().stream()
                        .map(error -> error.getField())
                        .distinct()
                        .sorted()
                        .toList());
        return p;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidMethod(
            HandlerMethodValidationException ex) {
        HttpStatusCode status = ex.getStatusCode();
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                status,
                status.is4xxClientError()
                        ? "Dữ liệu đầu vào không hợp lệ"
                        : "Đã xảy ra lỗi hệ thống");
        p.setProperty("code", status.is4xxClientError()
                ? "VALIDATION_FAILED" : "INTERNAL_ERROR");
        return p;
    }

    private ProblemDetail problem(
            HttpStatusCode status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                status, detail);
        p.setProperty("code", code);
        return p;
    }
}
~~~

### 13.5. Giải thích code

- `@Valid @RequestBody CreateWidgetRequest` tạo validation errors theo WebFlux argument binding.
- `@Min(1)` trên path parameter là một method-level constraint, có thể gây `HandlerMethodValidationException`.
- Advice có `@Order(-1)` nhằm ưu tiên các handlers cụ thể hơn Boot RFC 9457 handler order 0.
- Không có `@ExceptionHandler(Exception.class)` để framework 405/415/406 vẫn hoạt động.
- `ProblemDetail.forStatusAndDetail` giữ status HTTP chính xác khi return directly từ handler.
- `setProperty("code")` tạo extension property được Jackson serialize top-level trong môi trường Spring chuẩn.
- `invalidFields` chỉ gồm safe names, không trả rejectedValue.
- `SystemFaultException` minh họa một exception message chứa secret, nhưng public body chỉ có detail generic.
- `HandlerMethodValidationException.getStatusCode()` giúp không biến return-value validation issue thành client 400.
- `Mono.defer` và `Mono.fromSupplier` tạo kết quả khi subscribe, nhưng map in-memory ở đây không thay database/transaction.
- `URI.create("/api/widgets/" + id)` là relative Location, không hardcode hostname nội bộ.
- `Duration.ofMillis(40)` là timeout rất ngắn dành riêng demo, không phù hợp production SLA.
- Demo chưa có authentication/authorization, quotas hoặc persistence; đây là ví dụ hạ tầng error handling.

### 13.6. Khởi chạy

~~~bash
mvn spring-boot:run
~~~

Tạo resource:

~~~bash
curl -i -X POST http://localhost:8080/api/widgets \
  -H 'Content-Type: application/json' \
  --data '{"name":"WebFlux course"}'
~~~

Với lần tạo đầu:

~~~http
HTTP/1.1 201 Created
Location: /api/widgets/101
Content-Type: application/json

{"id":101,"name":"WebFlux course"}
~~~

ID được tăng sau từng lần POST, đừng hardcode 101 trong integration tests.

Get theo Location:

~~~bash
curl -i http://localhost:8080/api/widgets/101
~~~

Resource 999999 thường chưa tồn tại:

~~~bash
curl -i http://localhost:8080/api/widgets/999999
~~~

Status 404, ProblemDetail tương đương:

~~~json
{
  "type":"about:blank",
  "title":"Not Found",
  "status":404,
  "detail":"Không tìm thấy tài nguyên",
  "instance":"/api/widgets/999999",
  "code":"WIDGET_NOT_FOUND"
}
~~~

JSON order không cố định; `instance` có thể được framework bổ sung.

### 13.7. Kiểm validation

~~~bash
curl -i -X POST http://localhost:8080/api/widgets \
  -H 'Content-Type: application/json' \
  --data '{"name":""}'
~~~

Kỳ vọng 400 và các fields:

~~~json
{
  "status":400,
  "detail":"Dữ liệu đầu vào không hợp lệ",
  "code":"VALIDATION_FAILED",
  "invalidFields":["name"]
}
~~~

Đây là trích đoạn, response thực còn type/title/instance. Path validation:

~~~bash
curl -i http://localhost:8080/api/widgets/0
~~~

Kỳ vọng 400 với code `VALIDATION_FAILED`.

### 13.8. Lỗi domain và hệ thống

~~~bash
curl -i http://localhost:8080/api/demo/conflict
curl -i http://localhost:8080/api/demo/timeout
curl -i http://localhost:8080/api/demo/crash
~~~

| Endpoint | HTTP status | code |
| --- | --- | --- |
| `/api/demo/conflict` | 409 | `WIDGET_CONFLICT` |
| `/api/demo/timeout` | 504 | `UPSTREAM_TIMEOUT` |
| `/api/demo/crash` | 500 | `INTERNAL_ERROR` |

Public body của 500 không được chứa `jdbc-password=super-secret-value`.

### 13.9. Lỗi built-in của framework

~~~bash
curl -i -X DELETE http://localhost:8080/api/widgets/101
curl -i -X POST http://localhost:8080/api/widgets \
  -H 'Content-Type: text/plain' \
  --data 'plain text'
curl -i -X POST http://localhost:8080/api/widgets \
  -H 'Content-Type: application/json' \
  --data '{"name":'
~~~

Tương ứng kỳ vọng HTTP 405, 415 và 400; error body sẽ do Boot/framework xử lý, không phải generic `INTERNAL_ERROR`.

## 14. WebTestClient kiểm thử

Tạo `src/test/java/demo/WebFluxProblemTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class WebFluxProblemTest {

    @Autowired
    WebTestClient http;

    @Test
    void createReturns201AndLocation() {
        http.post().uri("/api/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateWidgetRequest("Reactive"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches(
                        HttpHeaders.LOCATION,
                        "/api/widgets/[0-9]+")
                .expectBody()
                .jsonPath("$.name").isEqualTo("Reactive");
    }

    @Test
    void missingReturnsProblem404() {
        http.get().uri("/api/widgets/999999").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("WIDGET_NOT_FOUND");
    }

    @Test
    void invalidJsonBodyReturnsSafe400() {
        http.post().uri("/api/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateWidgetRequest(""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.invalidFields[0]").isEqualTo("name");
    }

    @Test
    void pathConstraintIs400() {
        http.get().uri("/api/widgets/0").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void domainConflictIs409() {
        http.get().uri("/api/demo/conflict").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("WIDGET_CONFLICT");
    }

    @Test
    void timeoutIs504() {
        http.get().uri("/api/demo/timeout").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UPSTREAM_TIMEOUT");
    }

    @Test
    void serverErrorDoesNotLeakSecret() {
        http.get().uri("/api/demo/crash").exchange()
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(body -> {
                    assertNotNull(body);
                    assertFalse(body.contains("jdbc-password"));
                    assertFalse(body.contains("super-secret-value"));
                });
    }

    @Test
    void frameworkMethodErrorStill405() {
        http.delete().uri("/api/widgets/42").exchange()
                .expectStatus().isEqualTo(405);
    }

    @Test
    void unsupportedMediaTypeStill415() {
        http.post().uri("/api/widgets")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("text")
                .exchange()
                .expectStatus().isEqualTo(415);
    }

    @Test
    void malformedJsonIs400() {
        http.post().uri("/api/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Tests dùng WebTestClient và Boot RANDOM_PORT để đi qua HTTP server, codecs, controller advice, validation và serialization thật; có giá trị hơn chỉ unit-test trực tiếp handler methods.

**Giới hạn:** đây là static sample, phải chạy Maven trên môi trường có dependencies để xác minh patch-version behavior. Nếu test validation/ProblemDetail content type sai, xem actual response và phiên bản Spring Boot/Framework trước khi sửa assertions. Không bỏ các negative tests chỉ để đạt green build.

## 15. ResponseEntityExceptionHandler vs Boot-first

Có hai cách phổ biến:

**Boot-first:**

~~~properties
spring.webflux.problemdetails.enabled=true
~~~

Kèm một `@RestControllerAdvice` khai báo specific domain/validation handlers và giữ built-in 400/405/415 behavior cho Boot.

**Framework-first:** một advice extends WebFlux `ResponseEntityExceptionHandler` và tùy biến framework-specific protected hooks theo Spring Framework 6.2 Javadoc.

Không thêm cả hai theo kiểu "bắt cho chắc" khi chưa có thiết kế ordering, vì có thể duplicate handling và khác message codes.

### 15.1. i18n với MessageSource

`ErrorResponse`/`ResponseEntityExceptionHandler` hỗ trợ message codes cho type/title/detail, được resolve qua `MessageSource`. Có thể dịch detail cho tiếng Việt/tiếng Anh nhưng giữ `code=VALIDATION_FAILED` không đổi.

Tránh dùng exception message làm translation key chưa sanitize. Với validation, nội dung interpolated values phải được kiểm privacy.

### 15.2. ErrorResponse interceptor

Spring Framework 6.2 WebFlux documentation đề cập khả năng đăng ký `ErrorResponse` interceptors qua WebFlux Configurer để bổ sung hoặc ghi nhận RFC 9457 responses. Khi cần attach requestId/correlation chung, cân nhắc official extension points; không phải lúc nào cũng cần tự viết low-level `WebExceptionHandler`.

## 16. Các lỗi phổ biến

1. Dùng generic catch-all `Exception.class` ở advice priority cao rồi làm mọi 400/405/415 thành 500.
2. Trả ProblemDetail.status 404 nhưng HTTP response status line là 200.
3. Lấy `ex.getMessage()` cho 500, vô tình lộ SQL/credentials.
4. Chỉ bắt WebExchangeBindException mà không biết HandlerMethodValidationException.
5. Ép mọi method validation exception thành 400 dù có return-value validation.
6. Dùng MVC Servlet exception handler signatures trong WebFlux.
7. Tin @RestControllerAdvice bắt mọi lỗi ở WebFilter/Spring Security/gateway.
8. Tin advice có thể đổi status sau khi SSE response committed.
9. Xem cancellation của client là onError luôn luôn.
10. Đặt quá nhiều domain logic/status trực tiếp trong @ResponseStatusException ở service.
11. Tái sử dụng mutable ProblemDetail instance chung giữa requests.
12. Trả rejected password/token values trong validation errors.
13. Dùng raw query/Host header tạo problem type/instance.
14. Thêm @EnableWebFlux trong Boot chỉ để “bật WebFlux”, vô tình vô hiệu hóa auto-config ProblemDetails.
15. Không test built-in 405/406/415 sau khi thêm global advice.
16. Dùng blocking I/O trong error handler trên Reactor Netty event-loop.
17. Gọi subscribe() trong controller làm errors thoát HTTP lifecycle.
18. Nuốt mọi lỗi bằng onErrorResume/onErrorComplete rồi trả 200 success.
19. Lạm dụng @Order để che conflict thay vì thiết kế loại exception riêng.
20. Coi HTTP 499 của proxy là chuẩn WebFlux phải gửi.
21. Coi `detail` là error code ổn định cho frontend.
22. Thêm requestId vào metric labels gây high cardinality.
23. Cho rằng bật Boot ProblemDetails có thể chỉnh mọi 401/403/Security error.
24. Không quản lý thông tin nhạy cảm trong logs dù public payload đã redact.
25. Không có integration tests cho JSON codecs, content negotiation và handler order.

## 17. Debug theo triệu chứng

| Hiện tượng | Nguyên nhân khả dĩ | Kiểm |
| --- | --- | --- |
| Invalid DTO trả 500 | Catch-all hoặc mapping sai | WebExchangeBindException, advice ordering |
| @Min không chạy | Method validation chưa active/signature khác | Jakarta provider, Boot/Framework, annotations |
| Trả 400 cho return-value invalid | Bắt HandlerMethodValidationException và hardcode 400 | ex.getStatusCode |
| @RestControllerAdvice không chạy | Sai component scan/scope hoặc lỗi ở filter | basePackages, order, WebFilter |
| 404 body khác các lỗi domain | Route not found qua Boot fallback | ControllerAdvice vs error handler |
| POST text/plain trả 500 | Generic handler nuốt UnsupportedMediaType | 415 defaults |
| code không ở top-level | Jackson ProblemDetail mixin bị thay đổi | Codecs, content type |
| ProblemDetails property không hiệu quả | Boot WebFlux auto-config không còn active | @EnableWebFlux, app type, property |
| CORS/Security 401/403 khác | Security filter layer | ServerHttpSecurity / gateway policy |
| WebClient error body lộ hostname | Raw upstream error được echo | Domain translation, redaction |
| SSE lỗi sau 200 không có ProblemDetail 500 | Response đã committed | In-band error hoặc cleanup |
| Timeout 500 thay vì 504 | Chưa map TimeoutException | onErrorMap/domain advice |
| Test controller unit pass nhưng HTTP fail | Bỏ qua codec/validation/error resolver | WebTestClient HTTP integration |

## 18. Bài tập

**Bài 1.** Tạo local @ExceptionHandler và global @RestControllerAdvice cùng xử lý WidgetNotFoundException. Handler nào chạy? Khi nào nên giữ một chỗ duy nhất?

**Bài 2.** Tạo ProblemDetail 409 với type, title, detail, instance, code; kiểm HTTP status line trùng status trong body.

**Bài 3.** Viết DTO có @NotBlank và @Size, bật starter-validation, trả invalidFields nhưng không trả rejected password.

**Bài 4.** Gắn @Min lên @PathVariable; phân biệt WebExchangeBindException và HandlerMethodValidationException.

**Bài 5.** Một return-value method constraint sai. Vì sao mặc định có thể là HTTP 500 thay vì 400?

**Bài 6.** Bật spring.webflux.problemdetails.enabled và kiểm 405/415/400. Tại sao @EnableWebFlux có thể làm behavior khác Boot defaults?

**Bài 7.** Hai advice khác @Order; một handler match cause, một match root. Trình bày quy tắc precedence.

**Bài 8.** Dùng onErrorMap để chuyển TimeoutException thành UpstreamTimeoutException; map sang ProblemDetail 504 với error code.

**Bài 9.** Một WebFilter tự phát lỗi. Vì sao controller advice có thể không xử lý? Tầng nào phù hợp?

**Bài 10.** Browser hủy SSE sau khi nhận 200 và vài events. Tại sao không thể gửi JSON 500 mới? Khi nào in-band app-error event có ích?

**Bài 11.** Xây catalog domain error codes cho 400, 404, 409, 429, 503; mô tả versioning và backward compatibility của extensions.

**Bài 12.** Thiết kế requestId/traceId cho public error body và logging, với privacy/correlation validation.

**Bài 13.** Thêm reactive Spring Security. Xác minh 401/403 thuộc error handler nào và CORS/CSRF phối hợp ra sao.

**Bài 14.** Viết WebTestClient kiểm create, validation, missing, conflict, timeout, 500 redaction, 405 và 415.

**Bài 15.** Dịch title/detail sang hai locale nhưng không đổi status/type/code; kiểm clients vẫn xử lý được.

## 19. Checklist production

- [ ] Hợp đồng RFC 9457 ProblemDetail nhất quán và có docs.
- [ ] Status line, ProblemDetail.status và domain code không mâu thuẫn.
- [ ] Local/global advice có phân công rõ và ordering được kiểm bằng tests.
- [ ] Không có catch-all ưu tiên cao phá Spring built-in errors.
- [ ] Boot ProblemDetails config không conflict với custom ResponseEntityExceptionHandler.
- [ ] Hai loại validation exception được xử lý hoặc được Boot xử lý đúng.
- [ ] Lỗi method return validation không bị trả thành 400 client.
- [ ] Không public secrets/stacktraces/raw rejected values.
- [ ] Logs có correlation ID và privacy controls, metrics tránh high cardinality.
- [ ] 401/403 của Security được kiểm ở đúng reactive filter layer.
- [ ] 404/405/406/415/429/500/503/504 semantics được kiểm.
- [ ] Streaming post-commit errors dùng SSE event/cancel, không gửi HTTP response mới.
- [ ] Client cancellation và Reactor onError được phân biệt.
- [ ] Errors từ WebFilter/WebClient/gateway có contract rõ.
- [ ] Code/type ổn định qua locale và version; extensions additive.
- [ ] WebTestClient integration tests chạy trong CI, có negative cases.
- [ ] Không blocking Netty event-loop trong exception handler.
- [ ] Production authentication, authorization, CSRF, CORS và deployment proxy policy đã được kiểm.

## 20. Tổng kết

- `@ExceptionHandler` xử lý lỗi tại annotated controller, `@RestControllerAdvice` dùng chung giữa controllers.
- `ProblemDetail` theo RFC 9457 cung cấp các fields type/title/status/detail/instance và có thể mở rộng code/requestId.
- `ResponseEntityExceptionHandler` hỗ trợ framework ErrorResponse; Spring Boot WebFlux có property bật Problem Details.
- `WebExchangeBindException` và `HandlerMethodValidationException` phục vụ hai phạm vi validation khác nhau; không hardcode mọi method validation thành 400.
- Local advice, global advice, `@Order` và root/cause matching quyết định handler được chọn.
- WebFilter, Security, gateway và response đã committed nằm ngoài lời hứa “mọi lỗi đều qua @RestControllerAdvice”.
- Reactor cancellation không phải onError; lỗi business khi stream đã committed cần protocol xử lý in-band hoặc cleanup.
- API lỗi phải có đúng status, safe message, mã ổn định và tests regression; không leak internal exception messages.

## 21. Tài liệu tham khảo

- [Spring Framework 6.2 — WebFlux Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/ann-rest-exceptions.html).
- [Spring Framework 6.2 — WebFlux Controller Advice](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-advice.html).
- [Spring Framework 6.2 — Controller Exception Handling](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-exceptions.html).
- [Spring Framework 6.2 — Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-validation.html).
- [Spring Framework 6.2 — ResponseEntityExceptionHandler API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/reactive/result/method/annotation/ResponseEntityExceptionHandler.html).
- [Spring Framework 6.2 — ProblemDetail API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ProblemDetail.html).
- [Spring Boot 3.5 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/reactive.html).
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457).
- [Project Reactor — Error Handling](https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html).
- [Spring Security — Reactive Authorization](https://docs.spring.io/spring-security/reference/6.5/reactive/authorization/authorize-http-requests.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-007">Trả view, Model và tích hợp template engine reactive</a>

<details>
<summary>Xem chi tiết</summary>

# Trả view, Model và tích hợp template engine reactive trong Spring WebFlux

## 1. Khi WebFlux không chỉ trả JSON

Spring WebFlux thường được giới thiệu qua `@RestController` trả `Mono<Product>`, `Flux<Product>` và JSON. Nhưng WebFlux cũng phục vụ **HTML render phía server**: trình duyệt gửi GET, `@Controller` chuẩn bị **Model**, trả **tên view logic** hoặc một đối tượng `Rendering`, và một template engine như Thymeleaf tạo HTML.

Ba thành phần cần phân biệt:

- **View**: mẫu thể hiện đầu ra, ví dụ template `src/main/resources/templates/catalog.html`, được tìm thông qua logical view name `"catalog"`.
- **Model**: bản đồ tên → dữ liệu để template đọc, ví dụ `title = "Sản phẩm"`, `products = Flux<Product>`.
- **Reactive view resolution/rendering**: framework nhận return value, chọn `ViewResolver`, giải reactive model attributes phù hợp và ghi HTML thành HTTP response. Reactor `Mono`/`Flux` không tự biến template thành luồng HTML không giới hạn.

Mục tiêu bài:

1. Hiểu luồng `DispatcherHandler → HandlerResult → ViewResolutionResultHandler → ViewResolver → View → ServerHttpResponse`.
2. Trả `String` logical view name qua `@Controller` và truyền dữ liệu qua `Model`.
3. Phân biệt `@Controller` trả view với `@RestController`/`@ResponseBody` trả JSON hay text body.
4. Hiểu `Mono<String>` tên view, `Mono<Rendering>` và `Model` có thuộc tính `Mono<T>` hoặc `Flux<T>`.
5. Nắm sự khác nhau giữa thuộc tính `Flux<T>` (thường được gom thành `List<T>` trước render view) và HTTP SSE/streaming.
6. Cấu hình Thymeleaf tích hợp WebFlux bằng Spring Boot 3 và so sánh FreeMarker/Mustache với reactive template engine.
7. Tạo ứng dụng Java 17 chạy được, có trang danh sách, chi tiết, 404, redirect, model reactive, API JSON và tests WebTestClient.
8. Chẩn đoán lỗi mất template, HTML không được render, template chặn event loop, dữ liệu không được escape, cache, CSRF và những hiểu nhầm Servlet/WebFlux.

**Phạm vi:** Spring Framework 6.x, Spring Boot 3.x, Java 17+ và **Spring WebFlux** reactive stack. Những ví dụ chạy thử dùng Boot 3.3.13 với Reactor Netty mặc định, `spring-boot-starter-webflux` và `spring-boot-starter-thymeleaf`. Không dùng JSP/Servlet-specific ViewResolver hoặc `org.springframework.web.servlet.ModelAndView` cho ví dụ WebFlux.

## 2. Tại sao view rendering cũng có thể là reactive?

Hình dung trang danh mục cần dữ liệu sản phẩm từ remote API. Nếu controller dùng WebClient hoặc reactive repository, nó có thể trả hoặc đặt `Mono<List<Product>>` vào model **mà không chặn Servlet worker đợi dữ liệu**. WebFlux dùng các publishers để chờ kết quả và sau đó render view.

Luồng tổng quát:

```text
GET /pages/home
   │
   ▼
Reactor Netty HTTP server
   │
   ▼
DispatcherHandler (Spring WebFlux, không phải DispatcherServlet)
   │
   ├─ HandlerMapping: xác định @Controller method
   └─ HandlerAdapter: gọi method, nhận view name và Model
         │
         │ model["title"] = "Danh mục"
         │ model["products"] = Flux<Product>
         │ model["featured"] = Mono<Product>
         ▼
HandlerResult
   │
   ▼
ViewResolutionResultHandler
   ├─ chọn logical view name "home"
   ├─ hỏi các ViewResolver thích hợp
   ├─ resolve model attributes kiểu Mono/Flux trước view rendering
   │      ├─ Mono<Product> → Product hoặc không có thuộc tính
   │      └─ Flux<Product> → List<Product> (có thể cần toàn bộ sequence)
   └─ render qua Thymeleaf reactive integration
           │
           ▼
      ServerHttpResponse: text/html
```

Spring Framework cho phép model chứa reactive wrappers và giải chúng trước khi view thực sự sử dụng dữ liệu. Đây là điểm khác biệt quan trọng với model thông thường của Spring MVC.

### 2.1. Reactive không có nghĩa HTML đã xuất ngay khi Flux emit

Giả sử `Flux<Product>` phát 10.000 dòng. Nếu view renderer cần một collection hoàn chỉnh trước khi mở vòng lặp HTML, framework có thể **gom chúng thành List**. Khi đó:

- Template phải chờ Flux complete.
- Có thể giữ nhiều item trong memory.
- Infinite Flux không bao giờ complete → view có thể không bao giờ kết thúc.
- Error giữa quá trình resolve có thể ngăn HTML bình thường được dựng.

Một số template technologies (đặc biệt Thymeleaf reactive integration) có khả năng rendering/chunking theo cấu hình riêng, nhưng **không suy từ việc model chứa Flux rằng browser nhận một item HTML cho mỗi onNext**. Nếu mục tiêu là tiến trình cập nhật liên tục, cân nhắc SSE, WebSocket, HTML fragments streaming hoặc thiết kế phân trang.

### 2.2. View rendering vs JSON serialization

| Yêu cầu | Return phổ biến | Thành phần cuối |
| --- | --- | --- |
| GET trang HTML | `String` hoặc `Rendering` trong `@Controller` | `ViewResolver` + template engine |
| GET dữ liệu JSON | `Mono<Product>` trong `@RestController` | `HttpMessageWriter` / JSON codec |
| GET nhiều dữ liệu JSON | `Flux<Product>` với `@ResponseBody` | JSON encoding, có thể thành array |
| SSE cập nhật | `Flux<ServerSentEvent<?>>` với `text/event-stream` | Streamed response writer |
| Redirect browser | `"redirect:/pages/home"` | Redirect view/status và Location |
| Không có nội dung | `Mono<ResponseEntity<Void>>` trả 204 | HTTP status/headers, không template |

Content negotiation còn có thể tham gia view choice, nhưng một controller view-oriented không đồng nghĩa tự động hỗ trợ cả HTML lẫn JSON cho mọi Accept header.

## 3. @Controller, @RestController và @ResponseBody: khác nhau rất lớn

### 3.1. @Controller trả String là tên template

```java
@Controller
class WelcomeController {
    @GetMapping("/welcome")
    String welcome(Model model) {
        model.addAttribute("message", "Xin chào!");
        return "welcome";
    }
}
```

Spring WebFlux hiểu `"welcome"` là **logical view name**, không phải response text literal. Với Thymeleaf auto-config mặc định, view resolver tìm `classpath:/templates/welcome.html` rồi render.

### 3.2. @RestController trả String là body

```java
@RestController
class WelcomeApi {
    @GetMapping("/api/welcome")
    String welcome() {
        return "welcome";
    }
}
```

Spring ghi chuỗi **welcome** vào HTTP response body; không tìm `welcome.html`. `@RestController` có semantics `@Controller + @ResponseBody`.

### 3.3. @ResponseBody trên một method của @Controller

```java
@Controller
class MixedController {
    @GetMapping("/page")
    String page() {
        return "welcome"; // tên view
    }

    @ResponseBody
    @GetMapping("/raw")
    String raw() {
        return "welcome"; // body literal
    }
}
```

**Sai lầm kinh điển:** dùng `@RestController` rồi return `"home"`, kỳ vọng trang `home.html`. Kết quả là text "home". Khi mục đích là render HTML qua template, dùng `@Controller` và view name.

### 3.4. @ModelAttribute không tự biến method thành REST

Trong HTML controller, một object trả về (không phải kiểu đơn giản đặc biệt) có thể được xem như model attribute. `@ModelAttribute("product")` cũng cho phép đặt tên cho attribute. Nhưng nếu method trả `String`, Framework thường diễn giải thành **view name**; cần dùng `@ModelAttribute` tường minh nếu muốn coi chính String đó là model attribute, hoặc gọi `model.addAttribute(...)` để dễ đọc.

## 4. Model: dữ liệu truyền vào template

`org.springframework.ui.Model` là interface dùng trong cả MVC và WebFlux. Ví dụ:

```java
@GetMapping("/pages/hello")
String hello(Model model) {
    model.addAttribute("title", "Khóa học WebFlux");
    model.addAttribute("user", new UserView("Lan"));
    return "hello";
}
```

Đoạn trên là **snippet**; cần class UserView và @Controller hoàn chỉnh. Model là tập attributes theo tên. Với template:

```html
<h1 th:text="${title}">Tiêu đề mẫu</h1>
<p th:text="${user.name()}">Tên mẫu</p>
```

`th:text` đưa dữ liệu qua cơ chế escaping HTML của Thymeleaf; khác với `th:utext` vốn render unescaped markup. Đừng đưa dữ liệu user vào `th:utext` trừ khi nguồn thật sự trusted/sanitized với chính sách rõ.

### 4.1. Mono trong Model

Spring WebFlux cho phép:

```java
@GetMapping("/pages/profile")
String profile(Model model) {
    Mono<Profile> profileMono = service.loadProfile();
    model.addAttribute("profile", profileMono);
    return "profile";
}
```

Trước view rendering, model attribute reactive có thể được resolve thành giá trị `Profile`. Không gọi `profileMono.block()` trong WebFlux handler.

### 4.2. Flux trong Model

```java
@GetMapping("/pages/items")
String items(Model model) {
    Flux<Item> all = service.findAll();
    model.addAttribute("items", all);
    return "items";
}
```

WebFlux view infrastructure có thể resolve `Flux<Item>` thành `List<Item>` trước render. Nếu nguồn không complete hoặc có một triệu items, đây là vấn đề. Vì vậy list HTML nên phân trang và áp dụng limit tại repository/query, **không** dùng `collectList` vô hạn hoặc giả định template tự giải quyết backpressure UI.

### 4.3. Model chứa Mono.empty()

Nếu `profileMono` complete empty, model attribute có thể không có giá trị tương ứng. Template viết `${profile.name}` có thể gây lỗi hoặc không hiển thị như mong muốn. Hãy định nghĩa semantics rõ:

- Nếu resource không tồn tại: trả 404 với view phù hợp trước khi render.
- Nếu thông tin là tùy chọn: dùng fallback hoặc hiển thị có điều kiện.
- Nếu remote service lỗi: không tự chuyển mọi `onError` thành empty, vì che giấu outage.

Một service trả `Mono.empty()` không có nghĩa framework phải tự chọn HTTP 404 cho trang.

### 4.4. @ModelAttribute method

```java
@ModelAttribute
void common(Model model) {
    model.addAttribute("siteName", "Học Spring");
}
```

Các `@ModelAttribute` methods có thể chạy trước request handler trong cùng controller và chuẩn bị dữ liệu dùng chung. Tuy nhiên, nếu đặt I/O blocking trực tiếp trong `@ModelAttribute`, nó vẫn có thể chặn request path. Dùng reactive publisher/async style và kiểm soát lỗi phù hợp.

### 4.5. Tránh giữ state giữa nhiều requests

`@Controller` mặc định là singleton Spring bean. Không dùng một field mutable kiểu `private String currentUserName` để lưu model data của request hiện tại. Hai requests concurrent có thể làm lộ dữ liệu người này sang người khác. Sử dụng local method variables, scoped request/session phù hợp hoặc context có kiểm soát.

## 5. ViewResolutionResultHandler và ViewResolver hoạt động ra sao?

Spring WebFlux sử dụng `DispatcherHandler` làm front controller. Khi handler trả kết quả, các `HandlerResultHandler` xử lý từng loại return value. Với view rendering, `ViewResolutionResultHandler` chọn một `View` từ registered `ViewResolver`.

Các bước khái quát:

1. `HandlerMapping` chọn method/controller.
2. `HandlerAdapter` thực thi và tạo `HandlerResult` có return value và Model.
3. `ViewResolutionResultHandler` xác định logical view name từ String/Rendering/default translation.
4. `ViewResolver` tương ứng tìm `View` ứng với tên và locale phù hợp.
5. Model attributes reactive được giải theo contract view.
6. View engine tạo HTML và ghi vào `ServerHttpResponse` dưới một publisher phản ánh completion.

**Đừng import nhầm các lớp Servlet:**

| Spring MVC Servlet | Spring WebFlux |
| --- | --- |
| `DispatcherServlet` | `DispatcherHandler` |
| `org.springframework.web.servlet.ViewResolver` | `org.springframework.web.reactive.result.view.ViewResolver` |
| `org.springframework.web.servlet.View` | `org.springframework.web.reactive.result.view.View` |
| `ModelAndView` (Servlet) | `Rendering` (reactive view scenarios) |
| `HttpServletRequest` | `ServerHttpRequest`/`ServerWebExchange` |
| `HttpMessageConverter` | `HttpMessageReader`/`HttpMessageWriter` |
| Servlet `Filter` | WebFlux `WebFilter` |

Spring MVC có hỗ trợ reactive return types trong một số trường hợp, nhưng đó **không** khiến MVC chuyển thành WebFlux. Cần xác định application type khi debug.

### 5.1. String logical name

```java
return "catalog";
```

Với Thymeleaf mặc định, resolver thường tra `classpath:/templates/catalog.html`. Đừng trả cả đường dẫn filesystem hoặc URL absolute do user kiểm soát: logical names cần thuộc allowlist/trust boundary của ứng dụng.

### 5.2. Tên view mặc định

Nếu view controller không nêu tên và handler result phù hợp, WebFlux có thể suy tên view theo request path (bỏ slash đầu/cuối). Ví dụ `/pages/default` tương ứng logical view `pages/default`, và template `templates/pages/default.html`.

Đây là khả năng Framework, nhưng thường **return tên view tường minh** sẽ ít gây hiểu nhầm và dễ refactor hơn. Với REST controller, void/null semantics khác tùy response handling.

### 5.3. Redirect

```java
@GetMapping("/pages/old")
String old() {
    return "redirect:/pages/home";
}
```

Prefix `redirect:` được view infrastructure nhận diện để trả redirect đến route mục tiêu. Status có thể là 303 See Other trong WebFlux RedirectView mặc định; test redirect nên assert 3xx + Location thay vì hardcode 302 nếu không có policy status được cấu hình.

Không dùng `forward:` như Servlet MVC: **WebFlux không hỗ trợ Servlet FORWARD dispatch**. Redirect tạo thêm request từ client, không phải server-side internal forward.

### 5.4. Content negotiation và default views

`ViewResolutionResultHandler` có thể so `Accept` với media types của views. WebFlux cung cấp `HttpMessageWriterView` để render model thành JSON/XML thông qua codecs khi muốn cấu hình default views.

Tuy nhiên, đây là cấu hình nâng cao. Với đa số ứng dụng, nên tách rõ:

- `GET /pages/products` → HTML.
- `GET /api/products` → JSON.

Trộn HTTP HTML/JSON vào cùng một endpoint đòi hỏi quy tắc Content-Type, caching, error handling và test Accept chặt chẽ.

## 6. Tích hợp Thymeleaf reactive vào Spring Boot

### 6.1. Dependencies cần thiết

Với Maven và Spring Boot 3:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

Starter thứ nhất chọn WebFlux/reactive HTTP runtime, starter thứ hai tự cấu hình Thymeleaf tương thích WebFlux. Không cần đồng thời thêm `spring-boot-starter-web`: khi MVC và WebFlux starters cùng có mặt, Spring Boot thường chọn **MVC/Servlet application** theo mặc định.

### 6.2. Vị trí templates mặc định

```text
src/main/resources/
├── templates/
│   ├── home.html
│   ├── detail.html
│   └── missing.html
└── static/
    └── css/site.css
```

Boot tự tìm templates từ `classpath:/templates/`. Với view name `"home"` và suffix `.html`, tên resource tương ứng là `classpath:/templates/home.html`.

Không đặt templates cần server render vào `src/main/resources/static/` rồi kỳ vọng expressions `th:text` được thực thi. Static handler gửi nguyên nội dung file.

### 6.3. Các properties thường gặp

```properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.cache=false
```

Đây là một **profile phát triển** để template thay đổi dễ quan sát. Trong production nên cân nhắc bật cache template (thường default là true) để tránh chi phí parse/lắp lại template quá thường xuyên. Cache template **khác** HTTP response cache và **không tự làm data/model được cache**.

### 6.4. Các class đặc trưng của Thymeleaf WebFlux

Tích hợp Thymeleaf reactive (do Thymeleaf project quản lý) có các khái niệm như:

- `SpringResourceTemplateResolver`.
- `SpringWebFluxTemplateEngine`.
- `ThymeleafReactiveViewResolver`.
- `ThymeleafReactiveView` và reactive view support.

Với Boot auto-configuration, **không phải tự viết tất cả các bean trên**. Chỉ cấu hình thủ công khi thật sự thay template resolver path/encoding/engine/dialect hoặc có nhiều engines. Nếu tự khai báo resolver sai, có thể mất auto-config defaults và templates trả 500.

### 6.5. Thymeleaf template cú pháp

Các expressions thường gặp:

| Cú pháp | Công dụng |
| --- | --- |
| `th:text="${title}"` | Escape rồi hiển thị model attribute |
| `th:each="item : ${items}"` | Lặp qua collection model |
| `th:if="${item != null}"` | Render có điều kiện |
| `th:href="@{/pages/products/{id}(id=${item.id()})}"` | Dựng URL từ path variable |
| `th:object="${command}"` | Chọn form-backing bean |
| `th:field="*{title}"` | Gắn field trong form |
| `#{message.key}` | Đọc message bundle theo locale |
| `th:utext="${rawHtml}"` | Không escape HTML; cần kiểm soát XSS |

Khi dùng Java record trong template, cách chắc chắn đọc dữ liệu là gọi accessor `item.name()`, `item.id()` theo SpringEL/Thymeleaf phù hợp. Chỉ dùng `th:utext` với HTML trusted hoặc đã sanitize chặt.

## 7. Rendering: view name, model, status trong một giá trị

Spring WebFlux có interface `org.springframework.web.reactive.result.view.Rendering` để xây return value thể hiện view, model, status và headers.

```java
@GetMapping("/products/{id}")
Mono<Rendering> detail(@PathVariable long id) {
    return service.findById(id)
        .map(item -> Rendering.view("detail")
                .modelAttribute("product", item)
                .build())
        .switchIfEmpty(Mono.just(
            Rendering.view("missing")
                .status(HttpStatus.NOT_FOUND)
                .modelAttribute("id", id)
                .build()));
}
```

Đây là **snippet** cần controller/service và imports; ví dụ hoàn chỉnh ở mục 11. Điểm chính: HTTP 404 được quyết định **trước render**, chứ không kỳ vọng Thymeleaf tự biến `Mono.empty()` thành 404.

### 7.1. Mono<Rendering> khác Mono<DTO>

- `Mono<Rendering>`: kết quả khi subscribe là thông tin về **view để render**.
- `Mono<Product>` trong `@RestController`: khi complete thành công, framework serialize một Product ra JSON.
- `Mono<String>` trong `@Controller`: String thường là **view name**, không phải body text.
- `Mono<ResponseEntity<Product>>` trong REST: quyết định HTTP status/body theo object.
- `Mono<Void>`: phản ánh completion không payload, hoặc có thể cho view name mặc định trong các tình huống đặc biệt; không dùng để đại diện view tùy ý.

### 7.2. Rendering status và redirects

Nếu cần redirect có status cụ thể, có thể dùng API `Rendering.redirectTo(...)` và cấu hình status/headers theo API version. Tuy nhiên route đơn giản `return "redirect:/pages/home";` thường dễ hiểu.

Không truyền external redirect target trực tiếp từ query param không kiểm soát như `return "redirect:" + request.getParameter("next")`; đó là rủi ro **open redirect**. Dùng allowlist internal routes hoặc kiểm tra hosts/paths.

## 8. Reactive Model: giải quyết nguồn dữ liệu trước khi render

### 8.1. Gán trực tiếp Mono/Flux vào model

```java
@GetMapping("/catalog")
String catalog(Model model) {
    model.addAttribute("featured", service.featured()); // Mono<Product>
    model.addAttribute("products", service.findAll());   // Flux<Product>
    model.addAttribute("count", service.findAll().count()); // Mono<Long>
    return "catalog";
}
```

Khi view được render, các publishers cần được subscribe/resolve thành dữ liệu dùng trong template. Không cần tự `block()`. Tuy nhiên lưu ý:

- Mỗi lần gọi `service.findAll()` có thể tạo source mới; danh sách và count trong ví dụ có thể là **hai truy vấn** nếu service thực có R2DBC.
- Nếu muốn snapshot nhất quán, hãy thiết kế một service call trả DTO tổng hợp thích hợp thay vì gọi nhiều publishers không đồng bộ lẫn nhau.
- Nếu `Flux<Product>` lớn, cần phân trang trước khi đưa vào view.
- `Mono.empty()` có thể tạo attribute absent; có fallback/conditional view logic đúng nghiệp vụ.
- Model attributes có side effects không idempotent tuyệt đối không nên phụ thuộc vào số lần subscribe không kiểm soát.

### 8.2. Chuẩn bị dữ liệu rồi mới chọn view

Một cách khác: controller compose publisher trước, rồi emit `Rendering`:

```java
@GetMapping("/catalog/summary")
Mono<Rendering> summary() {
    return service.findAll()
        .collectList()
        .map(items -> Rendering.view("summary")
            .modelAttribute("total", items.size())
            .modelAttribute("items", items)
            .build());
}
```

Ưu: dữ liệu đã rõ, có thể xử lý empty/error và status. Nhược: `collectList()` buffer toàn bộ Flux, không dùng với vô hạn hoặc data lớn. Trong ví dụ production, query nên có pagination và hard limit.

### 8.3. Đặt một Mono vào Model không nhất thiết cho HTML streaming

Nhận một `Mono<Product>` hay `Flux<Product>` không làm template trở thành kết nối push updates liên tục. View thường cần model concrete values trước khi render. Nếu muốn giữ trang HTML mở và gửi cập nhật từng phần, phải thiết kế riêng theo Thymeleaf reactive modes hoặc WebFlux fragment streaming/SSE.

### 8.4. Model và context reactive

WebFlux không phải Servlet application nếu chạy Reactor Netty. Vì vậy:

- Không tự ép kiểu `ServerWebExchange` thành `HttpServletRequest`.
- Không dựa vào một ThreadLocal do Servlet filter set để render đúng user/locale khi pipeline đổi thread.
- Dùng Spring Security reactive context, Reactor Context và các APIs tương ứng khi cần principal, locale, request data.
- Khi render template, tránh gọi trực tiếp dịch vụ blocking thông qua custom dialect/expression trong event-loop; đưa data vào model bằng service đã được cấu hình phù hợp.

## 9. Template engine reactive khác REST response streaming

Một số luồng thường bị nhầm:

| Luồng | Dữ liệu đầu vào | Response |
| --- | --- | --- |
| Thymeleaf thông thường | Model đã resolve (có thể từ Mono/Flux) | HTML document |
| Thymeleaf chunked/data-driven | Reactive model + cấu hình/mode hỗ trợ | Các HTML chunks theo policy engine |
| FreeMarker/Mustache | Model để dựng view | HTML document theo engine integration |
| `Flux<ServerSentEvent<?>>` | Các events phát liên tục | `text/event-stream`, browser nhận events |
| `Flux<DTO>` / JSON | Reactive elements | JSON array hoặc streaming JSON tùy media type |
| `Flux<Fragment>` / fragment renderer | Các fragments HTML | HTML fragments theo API hỗ trợ và media type |

**Quyết định theo UX:** Nếu người dùng cần trang HTML có thông tin để đọc/submit form, server-side render thường phù hợp. Nếu cần cập nhật realtime, dùng SSE/WebSocket hoặc HTML-over-the-wire streaming với contract/DOM update rõ. Không chọn Flux vô hạn làm model cho trang HTML thông thường.

### 9.1. FreeMarker và Mustache

Spring Boot hỗ trợ FreeMarker, Thymeleaf và Mustache với WebFlux. Khi dùng starter hoặc dependencies đúng, templates được tìm từ `src/main/resources/templates` theo conventions của engine. FreeMarker có phần hỗ trợ WebFlux riêng; không mặc nhiên mọi tính năng Servlet form macros đều tồn tại giống MVC.

Thyemleaf có native WebFlux integration do chính Thymeleaf project cung cấp. Sửa lỗi template nên xác định engine thực đang được Boot auto-configure và resolver bean nào xử lý view name.

### 9.2. JSP và Servlet-only engines

JSP dựa vào Servlet/JSP container và không phải engine view phù hợp cho WebFlux chạy Reactor Netty. Không đặt `/WEB-INF/jsp/*.jsp` và cấu hình MVC `InternalResourceViewResolver` trong một project WebFlux reactive rồi mong chạy như Servlet MVC.

### 9.3. HTML fragments và HTMX

Ở các phiên bản Spring WebFlux mới, có hỗ trợ fragment-oriented responses như `Fragment` và `FragmentsRendering`. Mục tiêu là trả một hoặc nhiều phần HTML để frontend (HTMX/Hotwire) chèn vào DOM, có thể qua streaming. Các API này có thay đổi theo Framework minor/major version; trước khi copy snippet từ tài liệu phiên bản khác, cần xác minh class/package và media type phù hợp với 6.x/7.x đang sử dụng.

Bài runnable dưới đây giữ mô hình **whole-page view** để rõ và ổn định trên Boot 3.3.

## 10. Những lỗi liên quan template và bảo mật

### 10.1. Cross-site scripting (XSS)

Nếu user đặt tên:

```text
<script>alert("hello")</script>
```

Và template dùng:

```html
<p th:text="${name}">name</p>
```

Thymeleaf escape text HTML, trình duyệt hiển thị ký tự như văn bản, không thực thi script. Ngược lại:

```html
<p th:utext="${name}">name</p>
```

Ký tự HTML được render theo markup; **nguy hiểm** khi name từ input không tin cậy. Luôn validate/sanitize rich HTML nếu có yêu cầu thực sự, dùng escaping phù hợp ngữ cảnh HTML/attribute/JavaScript/URL, thêm CSP khi cần. Escaping của một template expression không thay toàn bộ XSS defenses.

### 10.2. Template name injection

Không bao giờ làm:

```java
@GetMapping("/unsafe")
String unsafe(@RequestParam String template) {
    return template; // user tự quyết định tên view
}
```

Đoạn trên là **anti-pattern**. Template engine có thể truy cập Spring application beans hoặc resources trong trust boundary; khi template names/content do user điều khiển, hệ thống có nguy cơ template injection, resource disclosure hoặc open redirect. Dùng whitelist server-controlled names, không cho user upload/chỉnh sửa arbitrary templates.

### 10.3. Form POST/CSRF

Server-rendered HTML thường đi cùng session cookie. Nếu có POST/PUT/DELETE qua browser với ambient credentials, cần cấu hình CSRF trong **Spring Security WebFlux**. Reactive security sử dụng `SecurityWebFilterChain` chứ không phải Servlet `SecurityFilterChain`. Form có thể cần CSRF hidden input theo dialect/security integration tương ứng; không bỏ CSRF chỉ vì ứng dụng đang dùng reactive.

### 10.4. Authorization từng resource

Chỉ ẩn nút “Xóa” với `th:if` **không** bảo vệ endpoint DELETE. Backend cần kiểm tra quyền theo object ID/owner/tenant. Model data nên chỉ chứa fields đã được phép lộ cho request hiện tại; nếu cache HTML response, đảm bảo không trộn nội dung của user A vào user B.

### 10.5. Content Security Policy và URL escaping

Không nối raw HTML bằng StringBuilder trong controller rồi gửi mà bỏ escaping. Dùng template engine và CSP phù hợp. URL sinh từ template cần an toàn/encoded; tránh mở redirect tới external host không kiểm soát. Không gắn access token hoặc dữ liệu riêng tư vào query string/logs/HTML source.

### 10.6. Caching template khác caching HTTP

- **Template cache**: lưu parsed/compiled template structures để render nhanh hơn, **không** giữ nguyên response HTML của từng user.
- **HTTP cache**: browser/CDN có thể lưu response theo Cache-Control, ETag và Vary; cần cân nhắc privacy.
- **Service/data cache**: lưu dữ liệu nghiệp vụ theo key/TTL và invalidation.

Một trang dashboard user-specific không an toàn để gắn `Cache-Control: public` chỉ vì Thymeleaf template đã cache.

## 11. Ứng dụng runnable: Spring Boot 3 + Java 17 + WebFlux + Thymeleaf

Dự án minh họa:

- `GET /pages/home`: template `home.html` có title và ba thuộc tính model reactive.
- `GET /pages/products/1`: dùng `Mono<Rendering>`, render `detail.html` cùng HTTP 200.
- `GET /pages/products/999`: không tìm thấy, render `missing.html` và HTTP 404.
- `GET /pages/summary`: `Mono<String>` chọn tên template sau khi count đã xong.
- `GET /pages/greeting?name=...`: HTML escaping qua `th:text`.
- `GET /pages/old`: redirect tới trang mới.
- `GET /api/products`: cùng service nhưng `@RestController` trả Flux JSON, để so sánh view và body.
- `GET /pages/default`: template path mặc định khi handler không trả tên view tường minh.

Không có database/remote network để làm ví dụ tự chứa; `CatalogService` cố tình chỉ dùng `List` nhỏ, `Mono` và `Flux`. Khi thay service bằng R2DBC/WebClient, phải quản lý subscription/errors/timeout.

### 11.1. Cấu trúc dự án

```text
webflux-reactive-views/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/
    │   │   ├── ReactiveViewsApplication.java
    │   │   ├── Product.java
    │   │   ├── CatalogService.java
    │   │   ├── PageController.java
    │   │   └── CatalogApiController.java
    │   └── resources/
    │       ├── application.properties
    │       └── templates/
    │           ├── home.html
    │           ├── detail.html
    │           ├── missing.html
    │           ├── summary.html
    │           ├── greeting.html
    │           └── pages/default.html
    └── test/
        └── java/example/ReactiveViewsTest.java
```

### 11.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>webflux-reactive-views</artifactId>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

Chỉ có `spring-boot-starter-webflux` để Boot không chuyển ngầm sang Servlet MVC. Thymeleaf starter auto-configure view engine; không cần `@EnableWebFlux` vì Boot đã cung cấp WebFlux auto-configuration.

### 11.3. ReactiveViewsApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveViewsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveViewsApplication.class, args);
    }
}
```

### 11.4. Product.java

```java
package example;

public record Product(long id, String name, long priceVnd) {}
```

Record giữ dữ liệu hiển thị, không expose entity persistence với fields nhạy cảm.

### 11.5. CatalogService.java

```java
package example;

import java.util.List;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CatalogService {

    private final List<Product> products = List.of(
        new Product(1L, "Spring WebFlux", 120000L),
        new Product(2L, "Spring Boot", 99000L),
        new Product(3L, "Project Reactor", 85000L)
    );

    public Flux<Product> all() {
        return Flux.defer(() -> Flux.fromIterable(products));
    }

    public Mono<Product> find(long id) {
        return all()
            .filter(item -> item.id() == id)
            .next();
    }
}
```

Cả `all()` và `find()` là cold publishers. Ví dụ cố tình không truy cập database: từ đó thấy rõ model resolver không cần `block()`. Ứng dụng thật nên có các giới hạn phân trang, caching/transactions và xử lý sự cố nguồn dữ liệu.

### 11.6. PageController.java

```java
package example;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;

import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/pages")
public class PageController {

    private final CatalogService catalog;

    public PageController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("title", "Danh mục sản phẩm");
        model.addAttribute("products", catalog.all());
        model.addAttribute("featured", catalog.find(1L));
        model.addAttribute("count", catalog.all().count());
        return "home";
    }

    @GetMapping("/products/{id}")
    public Mono<Rendering> detail(@PathVariable long id) {
        return catalog.find(id)
            .map(product -> Rendering.view("detail")
                .modelAttribute("product", product)
                .build())
            .switchIfEmpty(Mono.just(
                Rendering.view("missing")
                    .status(HttpStatus.NOT_FOUND)
                    .modelAttribute("id", id)
                    .build()));
    }

    @GetMapping("/summary")
    public Mono<String> summary(Model model) {
        return catalog.all()
            .count()
            .map(count -> {
                model.addAttribute("count", count);
                return "summary";
            });
    }

    @GetMapping("/greeting")
    public String greeting(
            @RequestParam(defaultValue = "Khách") String name,
            Model model) {
        model.addAttribute("name", name);
        return "greeting";
    }

    @GetMapping("/old")
    public String oldPage() {
        return "redirect:/pages/home";
    }

    @GetMapping("/default")
    public void defaultView(Model model) {
        model.addAttribute("message", "Trang được chọn theo đường dẫn");
        // Không trả view name tường minh; WebFlux có thể suy
        // logical view name "pages/default" theo request path.
    }
}
```

Ý nghĩa:

- `home(Model)` dùng reactive attributes; template engine sẽ nhận concrete values khi render.
- `detail()` dùng reactive service rồi chọn view tương ứng; không chặn event-loop. 404 không phụ thuộc `Mono.empty` default.
- `summary()` trả `Mono<String>`, khi số lượng sẵn sàng mới emit logical view name "summary".
- `greeting()` render text có escape qua template.
- `oldPage()` chuyển hướng theo prefix `redirect:`.
- `defaultView()` minh họa convention. Trong production, trả `"pages/default"` tường minh thường rõ hơn và ít phụ thuộc path.

### 11.7. CatalogApiController.java

```java
package example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class CatalogApiController {

    private final CatalogService catalog;

    public CatalogApiController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public Flux<Product> products() {
        return catalog.all();
    }
}
```

Đường `/api/products` dùng HTTP response writer JSON, không dùng template `home.html`. Cùng một CatalogService có thể phục vụ HTML và JSON mà không duplicate business logic.

### 11.8. application.properties

```properties
server.port=8080
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.encoding=UTF-8
spring.thymeleaf.cache=false
```

Đây là config development. Chỉnh `spring.thymeleaf.cache=true` cho production sau khi kiểm tra hot reload và deployment pipeline.

### 11.9. home.html

File `src/main/resources/templates/home.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${title}">Danh mục</title>
</head>
<body>
    <h1 th:text="${title}">Danh mục</h1>
    <p id="count" th:text="|Số sản phẩm: ${count}|">
        Số sản phẩm: 0
    </p>

    <h2>Sản phẩm nổi bật</h2>
    <p id="featured" th:if="${featured != null}"
       th:text="${featured.name()}">Sản phẩm</p>

    <h2>Danh sách</h2>
    <ul id="products">
        <li th:each="item : ${products}">
            <a th:href="@{/pages/products/{id}(id=${item.id()})}"
               th:text="${item.name()}">Tên sản phẩm</a>
            <span th:text="${item.priceVnd()}">0</span>
        </li>
    </ul>

    <p><a th:href="@{/pages/summary}">Xem tổng kết</a></p>
</body>
</html>
```

Thymeleaf dùng `th:each` qua danh sách sau khi model Flux được resolve. `th:text` escape nội dung. URL `@{...}` tạo link ứng dụng từ record accessor thay vì nối chuỗi không được encode.

### 11.10. detail.html

File `src/main/resources/templates/detail.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Chi tiết sản phẩm</title>
</head>
<body>
    <h1>Chi tiết sản phẩm</h1>
    <p id="item-id" th:text="${product.id()}">0</p>
    <p id="item-name" th:text="${product.name()}">Tên</p>
    <p id="item-price" th:text="${product.priceVnd()}">0</p>
    <a th:href="@{/pages/home}">Quay lại danh sách</a>
</body>
</html>
```

HTTP status của trang này là 200 vì `Rendering.view("detail")` không override status.

### 11.11. missing.html

File `src/main/resources/templates/missing.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Không tìm thấy</title>
</head>
<body>
    <h1>Không tìm thấy sản phẩm</h1>
    <p>Mã sản phẩm:
        <span id="missing-id" th:text="${id}">0</span>
    </p>
    <a th:href="@{/pages/home}">Danh mục</a>
</body>
</html>
```

Nội dung HTML “Không tìm thấy” và HTTP status 404 là hai thứ riêng. `Rendering.status(HttpStatus.NOT_FOUND)` làm status đúng, không chỉ có message trên trang.

### 11.12. summary.html

File `src/main/resources/templates/summary.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Tổng kết</title>
</head>
<body>
    <h1>Tổng kết danh mục</h1>
    <p id="summary-count"
       th:text="|Số sản phẩm: ${count}|">Số sản phẩm: 0</p>
</body>
</html>
```

`summary(Model)` trả `Mono<String>` đã tạo `count` trước khi emit view name. Không có `block()` hoặc `subscribe()` thủ công.

### 11.13. greeting.html

File `src/main/resources/templates/greeting.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Chào mừng</title>
</head>
<body>
    <h1>Chào mừng</h1>
    <p id="greeting-name" th:text="${name}">Khách</p>
</body>
</html>
```

Trường hợp tên là `<script>alert(1)</script>` thì `th:text` escape thành văn bản an toàn trong HTML context. Ví dụ này không kiểm thử tất cả XSS vectors, chỉ minh họa khác nhau giữa escaping và raw output.

### 11.14. pages/default.html

File `src/main/resources/templates/pages/default.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Trang mặc định</title>
</head>
<body>
    <h1>Trang mặc định</h1>
    <p id="default-message"
       th:text="${message}">Nội dung</p>
</body>
</html>
```

Nếu `defaultView(Model)` không có return value và framework chọn implicit logical view name theo path, template này được tìm nhờ `pages/default`. Với một cấu hình router/view tùy biến, nên test hoặc trả tên view tường minh để không phụ thuộc vào convention.

## 12. Chạy và quan sát kết quả

Ở thư mục có `pom.xml`, với JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Gọi trang HTML:

```bash
curl -i http://localhost:8080/pages/home
```

Kỳ vọng HTTP 200, `Content-Type: text/html` (có thể có charset) và HTML có:

```html
<h1>Danh mục sản phẩm</h1>
<p id="count">Số sản phẩm: 3</p>
```

Danh sách đã render có các tên `Spring WebFlux`, `Spring Boot` và `Project Reactor`. HTML thực tế có thể được format/indent khác template, nên không so byte-for-byte.

Chi tiết:

```bash
curl -i http://localhost:8080/pages/products/1
```

Kỳ vọng 200 và nội dung tên `Spring WebFlux`.

Missing:

```bash
curl -i http://localhost:8080/pages/products/999
```

Kỳ vọng HTTP 404 cùng **HTML của missing.html**, không phải chỉ JSON error mặc định.

Summary:

```bash
curl -i http://localhost:8080/pages/summary
```

Kỳ vọng 200 và `Số sản phẩm: 3`.

Redirect:

```bash
curl -i http://localhost:8080/pages/old
```

Kỳ vọng 3xx với `Location: /pages/home`. Dùng `curl -L` để theo chuyển hướng.

REST JSON:

```bash
curl -i http://localhost:8080/api/products
```

Kỳ vọng JSON array:

```json
[
  {"id":1,"name":"Spring WebFlux","priceVnd":120000},
  {"id":2,"name":"Spring Boot","priceVnd":99000},
  {"id":3,"name":"Project Reactor","priceVnd":85000}
]
```

JSON key order/indent không phải contract; test nên theo fields. JSON endpoint dùng dữ liệu cùng service nhưng **không** chạy Thymeleaf.

### 12.1. Nhìn rõ sự khác nhau giữa @Controller và @RestController

Nếu thay `@Controller` ở PageController thành `@RestController`, method `home()` trả chuỗi `"home"` dưới dạng response body; trang sẽ không được render như kỳ vọng. Đây là phép thử tư duy tốt để tránh lỗi controller trả tên view thành raw text.

## 13. WebTestClient: kiểm thử reactive views qua HTTP

Kiểm thử dùng Spring Boot context thật và embedded reactive web server. Kỳ vọng HTML theo những vùng text ổn định, không assert toàn bộ HTML đã render chính xác từng whitespace.

File `src/test/java/example/ReactiveViewsTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ReactiveViewsTest {

    @Autowired
    WebTestClient client;

    @Test
    void homeRendersHtmlWithResolvedReactiveAttributes() {
        client.get().uri("/pages/home")
            .accept(MediaType.TEXT_HTML)
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
                .value(html -> {
                    assertTrue(html.contains("Danh mục sản phẩm"));
                    assertTrue(html.contains("Số sản phẩm: 3"));
                    assertTrue(html.contains("Spring WebFlux"));
                    assertTrue(html.contains("Spring Boot"));
                    assertTrue(html.contains("Project Reactor"));
                });
    }

    @Test
    void detailUsesReactiveRenderingAndReturns200() {
        client.get().uri("/pages/products/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
                .value(html -> {
                    assertTrue(html.contains("Chi tiết sản phẩm"));
                    assertTrue(html.contains("Spring WebFlux"));
                });
    }

    @Test
    void missingProductIsExplicitHtml404() {
        client.get().uri("/pages/products/999")
            .exchange()
            .expectStatus().isNotFound()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
                .value(html -> {
                    assertTrue(html.contains("Không tìm thấy sản phẩm"));
                    assertTrue(html.contains("999"));
                });
    }

    @Test
    void monoViewNameRendersSummary() {
        client.get().uri("/pages/summary")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
                .value(html -> assertTrue(
                    html.contains("Số sản phẩm: 3")));
    }

    @Test
    void greetingEscapesUntrustedHtml() {
        client.get().uri(builder -> builder
                .path("/pages/greeting")
                .queryParam("name", "<script>alert(1)</script>")
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
                .value(html -> {
                    assertFalse(html.contains("<script>"));
                    assertTrue(html.contains("&lt;script&gt;"));
                });
    }

    @Test
    void redirectHasLocationHeader() {
        client.get().uri("/pages/old")
            .exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().valueEquals(
                HttpHeaders.LOCATION, "/pages/home");
    }

    @Test
    void jsonApiDoesNotRenderThymeleaf() {
        client.get().uri("/api/products")
            .exchange()
            .expectStatus().isOk()
            .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
                .jsonPath("$[0].id").isEqualTo(1)
                .jsonPath("$[1].name").isEqualTo("Spring Boot")
                .jsonPath("$[2].priceVnd").isEqualTo(85000);
    }

    @Test
    void implicitPathViewCanResolveTemplate() {
        client.get().uri("/pages/default")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
                .value(html -> assertTrue(
                    html.contains("Trang được chọn theo đường dẫn")));
    }

    @Test
    void homeDoesNotExposeThymeleafExpressionsLiterally() {
        client.get().uri("/pages/home")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
                .value(html -> {
                    assertFalse(html.contains("th:text="));
                    assertFalse(html.contains("th:each="));
                });
    }
}
```

**Vì sao test vừa HTML vừa JSON?** Đây là cách rõ nhất chứng minh `ViewResolutionResultHandler` và `HttpMessageWriter` là hai hướng xử lý khác nhau trong cùng WebFlux application. Test `homeRendersHtmlWithResolvedReactiveAttributes` cũng giúp phát hiện việc model được render nguyên `Mono/Flux.toString()` thay vì dữ liệu concrete.

### 13.1. Kiểm thử có thể thất bại vì version/encoding, không nên đoán

- Nếu test HTML fail 500, xem exception khi resolver không tìm template hoặc Thymeleaf expression lỗi.
- Nếu HTML thực xuất ra khác spacing, dùng HTML parser/assert structural semantics thay vì so raw whitespace.
- Nếu running app sử dụng Servlet MVC do vô tình thêm `spring-boot-starter-web`, kiểm tra actual application type trước khi debug view.
- Nếu test implicit view fail vì framework/config đã tùy biến, có thể chủ động trả `"pages/default"` ở project production; test ở đây cố tình minh họa convention WebFlux 6.x.
- Nếu assert escaping fail ở một version engine, kiểm tra cách output được escape và DOM behavior thay vì vô hiệu hóa escaping.

Các test trên là **mã được cung cấp**; chỉ được tuyên bố “test PASS” sau khi chạy Maven và có kết quả thực.

## 14. Mô hình tối ưu dữ liệu cho trang HTML reactive

### 14.1. Tránh gọi ba lần database

Trong demo home:

```java
model.addAttribute("products", catalog.all());
model.addAttribute("featured", catalog.find(1L));
model.addAttribute("count", catalog.all().count());
```

Với List in-memory thì rất nhẹ. Nhưng nếu `catalog.all()` là R2DBC query thực, màn home có thể chạy **hai truy vấn danh sách** và một truy vấn nổi bật.

Trong production, hướng tốt hơn là xây view model trong service:

```java
record CatalogPage(
        String title,
        long count,
        List<Product> products,
        Product featured) {}

Mono<CatalogPage> page = service.loadCatalogPage(limit, pageIndex);
```

Đây là concept snippet, không phải method standalone. Service có thể trả đúng một snapshot DTO đã phân trang. Controller dùng:

```java
return page.map(p ->
    Rendering.view("home")
        .modelAttribute("title", p.title())
        .modelAttribute("count", p.count())
        .modelAttribute("products", p.products())
        .modelAttribute("featured", p.featured())
        .build());
```

Đoạn code là phần return của `Mono<Rendering>` handler với biến `page` đã tạo ở trên. Nó tránh việc view resolver phải resolve nhiều reactive attributes riêng rẽ và giúp định nghĩa data consistency.

### 14.2. Giới hạn dữ liệu trước render

Nếu danh sách 1 triệu hàng, browser cũng không cần HTML cho mọi item ngay. Thường nên:

1. Nhận page/cursor với limit bounded.
2. Validate page size trên server.
3. Thực hiện DB query có limit, không chỉ `flux.take(20)` sau một query đã tải hết.
4. Render những records của trang hiện tại.
5. Cho client load next page hoặc dùng HTMX/infinite scroll với contract cụ thể.

### 14.3. Đặt timeout trước giai đoạn render

Một reactive data source không bao giờ complete sẽ khiến HTML view có thể treo. Đặt timeout business trước khi truyền model:

```java
Mono<Product> featured =
    service.featured()
        .timeout(Duration.ofSeconds(2));
```

Đây là snippet, cần import `Duration` và policy lỗi phù hợp. Nếu timeout, có thể cho view fallback sản phẩm nổi bật rỗng hoặc trả error page, nhưng không nên lặng lẽ coi timeout của database là “không có sản phẩm”.

### 14.4. Template processing có dùng CPU

Reactive I/O giúp tránh chờ thread khi kết nối remote, nhưng template engine phải parse expressions, lặp records, escape HTML và tạo bytes. Nếu trang rất lớn, việc render có thể tốn CPU và allocations, ngay cả khi model là reactive.

Theo dõi event-loop lag/CPU, latency template, memory, encoder buffers và GC. Nếu cần xử lý CPU/blocking nặng, thiết kế scheduler/bulkhead phù hợp hoặc đẩy sang service chuyên biệt; không mặc định mọi việc bên trong template là non-blocking.

### 14.5. Caching danh mục và model

Dữ liệu công khai ít đổi có thể cache ở service level. Nhưng cache theo user/session phải có key và TTL/invalidation chính xác. Không đặt `Mono.cache()` vô hạn cho một publisher chứa thông tin riêng tư rồi dùng chung bean/controller cho nhiều requests.

## 15. Forms, binding và validation trong WebFlux view

Template engine giúp dựng form:

```html
<form th:action="@{/pages/products/create}" method="post">
    <label for="name">Tên sản phẩm</label>
    <input id="name" type="text" name="name">
    <button type="submit">Tạo</button>
</form>
```

Form HTML `application/x-www-form-urlencoded` cần data binding và validation. Trong annotated WebFlux controller, có thể dùng `@ModelAttribute` và `BindingResult` với quy tắc thứ tự arguments phù hợp. Với reactive binder/validator, cần hiểu `WebExchangeBindException` khi có lỗi binding/validation.

Ví dụ minh họa:

```java
@PostMapping("/pages/products/create")
Mono<String> create(
        @Valid @ModelAttribute ProductForm form,
        BindingResult errors,
        Model model) {
    if (errors.hasErrors()) {
        model.addAttribute("form", form);
        return Mono.just("create-form");
    }
    return service.create(form)
                  .thenReturn("redirect:/pages/home");
}
```

Đây là **snippet không chạy độc lập**: cần `ProductForm`/service/validator imports, template create-form và cấu hình Security/CSRF. Với WebFlux, validation còn phụ thuộc method signatures và reactive input; không sao chép Servlet MVC form code rồi giả định mọi annotation/method overload hoạt động giống hệt. Nếu dùng `BindingResult`, đặt ngay sau argument `@ModelAttribute` được validate theo contract Framework.

### 15.1. Chống over-posting/mass assignment

Không bind request form trực tiếp vào JPA entity có `role`, `ownerId`, `isAdmin` hay trường giá trị do server kiểm soát. Dùng dedicated command DTO, giới hạn fields được bind và gán identity từ principal. Template hidden field không được tin cậy vì client có thể tự sửa.

### 15.2. Hiển thị lỗi validation

Thymeleaf có `th:errors` và `#fields` cho form binding theo integration. Luồng thực tế phải:

1. Validate input.
2. Trả lại form view khi lỗi, giữ binding result trong model đúng convention.
3. Escape mọi giá trị đã submit.
4. Không commit transaction/sửa DB khi form invalid.
5. Thiết kế CSRF token trong form khi login/session cần.
6. Trên thành công, redirect (PRG: Post/Redirect/Get) để giảm accidental duplicate POST.

## 16. Những lỗi thường gặp và hướng khắc phục

| Triệu chứng | Nguyên nhân khả dĩ | Xử lý |
| --- | --- | --- |
| Browser chỉ hiện chữ `home` | `@RestController` trả String body | Dùng `@Controller` cho view |
| 500 TemplateInputException | Sai logical view name hoặc thiếu `templates/home.html` | Kiểm tra prefix/suffix/classpath |
| HTML còn `th:text`/`th:each` | Template file bị phục vụ dưới `static` hoặc không qua resolver | Đặt dưới `templates` và return view name |
| IDE import Servlet ModelAndView | Nhầm MVC và WebFlux | Dùng `Rendering` reactive |
| Trang HTML đợi vô hạn | Model chứa Flux không complete | Pagination/take/timeout/streaming |
| Tạo được Mono nhưng block() gây lỗi | Blocking trên Netty event-loop | Compose operators/return Publisher |
| Flux trong Model không stream từng item | View resolver giải toàn List | Chọn SSE/fragments nếu cần incremental |
| Trang chi tiết rỗng không 404 | `Mono.empty()` không tự set HTTP status | Dùng `Rendering.status(NOT_FOUND)` |
| Trang 404 chỉ có text nhưng status 200 | Render missing view nhưng không set HTTP 404 | Dùng Rendering status |
| `Model` có `Mono<User>` dạng chuỗi | Custom view engine không hỗ trợ reactive attrs | Verify view resolver/engine integration |
| XSS qua field name | Dùng `th:utext` cho untrusted input | Dùng `th:text`/escaping |
| Open redirect qua URL param | Return `redirect:` + user input | Allowlist server-controlled target |
| Template được user upload tùy ý | Template expression có thể truy cập beans | Không dùng editable templates từ untrusted users |
| Form POST 403 | Spring Security CSRF/roles | Bổ sung token và security policy đúng |
| Form tự submit nhưng over-posting | Binding trực tiếp entity | DTO với allowed fields |
| Trang quá nặng CPU | Template render/loop khối lượng lớn | Page limit, caching, profiling |
| Reactors Mono cache lẫn user | Reuse publisher của session A cho B | Partition by principal, TTL/invalidation |
| 404 khi /pages/default | Default name/path không có template | Thêm pages/default.html hoặc return explicit |
| Redirect status không phải 302 | WebFlux RedirectView default semantics | Assert 3xx+Location hoặc cấu hình status rõ |
| WebFlux project chạy DispatcherServlet | Thêm cả MVC starter | Kiểm tra app type/dependencies |
| JSP template không được resolve trên Netty | JSP cần Servlet/JSP infrastructure | Thymeleaf/FreeMarker/Mustache |
| Server 200 nhưng text/html không có | `@ResponseBody` hoặc converter khác | Kiểm tra controller annotations |
| Invalid HTML chứa raw script | Unescaped expression/custom dialect | Escape/sanitize, test XSS |
| API JSON phát sai key/format | DTO/Jackson contract hoặc Content-Type | JSON tests, schema, negotiation |

### 16.1. Quy trình debug từng tầng

1. **Runtime:** kiểm tra Boot chạy WebFlux reactive (Reactor Netty mặc định) hay MVC Servlet.
2. **Mapping:** route có match `GET /pages/home` và được xử lý bởi `@Controller` không?
3. **Return value:** `"home"` được xem là tên view hay String body?
4. **View resolver:** Thymeleaf reactive view resolver bean có tồn tại không, template path là gì?
5. **Model:** những keys được thêm có đúng tên template dùng? Mono/Flux complete/error ra sao?
6. **Render:** kiểm tra Thymeleaf syntax, SpringEL, escaping, fragments, locale và message bundles.
7. **HTTP:** status, `Content-Type: text/html`, caching/security/redirect headers có đúng không?
8. **Metrics:** template rendering latency, memory, codec buffering và event-loop CPU dưới tải thực.

## 17. Checklist thiết kế trước khi triển khai

- [ ] Chọn `@Controller` cho HTML, `@RestController` cho REST API.
- [ ] Return view name từ allowlist server-controlled; không dùng untrusted URL làm template name.
- [ ] Các template ở đúng `classpath:/templates/` và engine phù hợp Spring WebFlux.
- [ ] Model data chỉ gồm các fields cần hiển thị, đã được authorize.
- [ ] `Mono.empty()` có semantics rõ (missing, optional hoặc error), status được set đúng.
- [ ] `Flux`/collections được giới hạn theo pagination, không render vô hạn.
- [ ] Không `block()` hoặc gọi blocking clients trực tiếp trong request event-loop.
- [ ] Template expressions dùng escaping; không `th:utext` với user input không tin cậy.
- [ ] Cấu hình CSRF/AuthN/AuthZ nếu dùng session và form mutations.
- [ ] HTTP caching không chia sẻ HTML riêng tư qua shared proxies.
- [ ] Resource URLs/CSP/redirect targets được validate.
- [ ] Tests xác minh HTML 200, HTML 404, redirect, escaping, JSON API.
- [ ] Theo dõi template render time, codec buffers, event-loop lag và GC.
- [ ] Không thêm Servlet-only JSP resolver vào Reactor Netty app.
- [ ] Biết rõ view cache khác HTTP response cache và data cache.

## 18. Câu hỏi ôn tập

**1.** WebFlux có thể render HTML template phía server không?  
**Đáp án:** Có. Nó có view resolution qua ViewResolutionResultHandler và view engines như Thymeleaf.

**2.** String `"home"` trong `@Controller` thường là gì?  
**Đáp án:** Logical view name.

**3.** String `"home"` trong `@RestController` thường là gì?  
**Đáp án:** Response body literal.

**4.** `Model` nằm ở package nào?  
**Đáp án:** `org.springframework.ui.Model`.

**5.** `ViewResolutionResultHandler` làm gì?  
**Đáp án:** Chọn View qua ViewResolver và điều phối render theo Model.

**6.** Thymeleaf Boot default template directory ở đâu?  
**Đáp án:** `src/main/resources/templates/`.

**7.** Với view name `"detail"`, default Thymeleaf tìm file nào?  
**Đáp án:** `classpath:/templates/detail.html`.

**8.** `Mono<Product>` đặt trong model có được resolve trước render không?  
**Đáp án:** Có theo Spring WebFlux view model resolution.

**9.** `Flux<Product>` trong model có nhất thiết stream từng item HTML ngay không?  
**Đáp án:** Không; framework có thể collect thành List trước view rendering.

**10.** Một Flux không bao giờ complete sẽ ảnh hưởng normal view thế nào?  
**Đáp án:** View có thể không render xong nếu đang chờ toàn bộ model.

**11.** Mono.empty từ repository có tự trả 404 cho HTML page không?  
**Đáp án:** Không; nên chọn Missing view + 404 tường minh.

**12.** WebFlux dùng Servlet ModelAndView cho view controller không?  
**Đáp án:** Không; Rendering là API phù hợp của reactive stack.

**13.** `Mono<Rendering>` biểu diễn điều gì?  
**Đáp án:** Async decision về template, model, status và headers.

**14.** `Mono<String>` trong HTML controller có thể biểu diễn gì?  
**Đáp án:** Tên view được cung cấp bất đồng bộ.

**15.** Vì sao `@RestController` không hiển thị Thymeleaf `home.html` khi trả "home"?  
**Đáp án:** Vì return value được ghi như body bởi @ResponseBody semantics.

**16.** `th:text` khác `th:utext` thế nào?  
**Đáp án:** th:text escape HTML, th:utext không escape.

**17.** Dùng untrusted template name có an toàn không?  
**Đáp án:** Không; cần allowlist tên template và kiểm soát redirect targets.

**18.** Return `redirect:/pages/home` khác `forward:...` thế nào?  
**Đáp án:** Redirect là phản hồi 3xx tạo request mới, WebFlux không hỗ trợ Servlet forward dispatch.

**19.** Template cache có lưu trang user A để gửi user B không?  
**Đáp án:** Template cache chủ yếu cache template structures, không nên trộn với HTTP response cache.

**20.** View rendering reactive có làm CPU template biến mất không?  
**Đáp án:** Không; render vẫn dùng CPU, memory và HTML encoding.

**21.** Form POST dùng cookie session có cần xét CSRF không?  
**Đáp án:** Có, cần policy Spring Security WebFlux phù hợp.

**22.** Nếu cả WebFlux và MVC starters cùng tồn tại, Boot mặc định chọn stack nào?  
**Đáp án:** Thường chọn MVC/Servlet.

**23.** Tại sao dùng Flux lớn để render HTML cần pagination?  
**Đáp án:** Tránh đợi quá lâu và giữ nhiều dữ liệu trong heap.

**24.** Test HTML nên so string y hệt từng whitespace không?  
**Đáp án:** Không; ưu tiên kiểm tra status, media type, cấu trúc và text quan trọng.

**25.** Nếu muốn cập nhật HTML từng phần khi dữ liệu mới đến, phải cân nhắc gì?  
**Đáp án:** Thymeleaf reactive streaming modes, fragments, SSE/HTML-over-the-wire, proxy buffering và browser update logic.

## 19. Tài liệu chính thức

- [Spring Framework 6.2 — DispatcherHandler / View Resolution](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/dispatcher-handler.html): `ViewResolutionResultHandler`, logical view names, reactive model attributes, redirects.
- [Spring Framework 6.2 — View Technologies](https://docs.spring.io/spring-framework/reference/6.2/web/webflux-view.html): Thymeleaf, FreeMarker, Script Views, fragments và HTTP message writer views.
- [Spring Framework 6.2 — Controller Arguments](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/arguments.html): `Model`, model binding, request handling.
- [Spring Framework 6.2 — Model](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-modelattrib-methods.html): reactive attributes, `@ModelAttribute` lifecycle.
- [Spring Framework 6.2 — Controller Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/controller/ann-methods/return-types.html): `String` view names, Rendering, Model và HTTP body cases.
- [Spring Framework 6.2 — WebFlux Configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webflux/config.html): cấu hình ViewResolver/Content Negotiation.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): template engines, WebFlux/Boot auto-configuration, Reactor Netty runtime.
- [Thymeleaf — Spring Integration](https://www.thymeleaf.org/doc/tutorials/3.1/thymeleafspring.html): Spring expressions, forms, reactive integration guidance.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/6.2/testing/webtestclient.html): reactive HTTP testing.

**Kết luận:** WebFlux không chỉ trả Mono/Flux JSON. Với `@Controller`, `Model` và một view resolver như Thymeleaf reactive, bạn có thể render HTML phía server mà không tự chặn event-loop để đợi data. Tuy nhiên **reactive attributes có thể được gom trước render**, nên phải thiết kế pagination, timeout, escaping, authorization và streaming strategy theo đúng yêu cầu. Phân biệt rõ **view name**, **model data** và **HTTP response body** là chìa khóa để không nhầm WebFlux với REST-only hoặc Servlet MVC.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="webflux-controllers-008">Cấu hình CORS, tài nguyên tĩnh và HTTP caching trong WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình CORS, tài nguyên tĩnh và HTTP caching trong Spring WebFlux

## 1. Mục tiêu và ba vấn đề HTTP thường đi cùng nhau

Một ứng dụng Spring WebFlux có thể đồng thời phục vụ **API JSON**, **website/tài nguyên tĩnh** và **browser frontend ở origin khác**. Ba cơ chế thường bị nhầm vì đều liên quan đến response headers:

1. **CORS (Cross-Origin Resource Sharing):** browser có được phép cho JavaScript ở origin A đọc response từ origin B không?
2. **Static resources:** URL nào được ánh xạ tới file CSS/JS/image/HTML, file được tìm theo thứ tự nào, và tài nguyên được gửi bằng HTTP ra sao?
3. **HTTP caching:** cache trình duyệt/CDN có được lưu hoặc dùng lại response không, trong bao lâu, và khi nào sử dụng `ETag`/`Last-Modified` để kiểm tra lại?

Một API có thể có **CORS đúng nhưng caching sai**, hoặc **cache đúng nhưng browser vẫn từ chối đọc vì CORS**. Ví dụ:

~~~http
GET /api/public/catalog HTTP/1.1
Origin: https://app.example.test
Accept: application/json
~~~

Server có thể trả:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Vary: Origin
Cache-Control: public, max-age=60
ETag: "catalog-2026-a"
Content-Type: application/json

{"edition":"2026-A","subjects":["WebFlux","HTTP"]}
~~~

`Access-Control-Allow-Origin` điều khiển quyền đọc qua **CORS trong browser**. `Vary: Origin` cho cache biết representation/response headers có thể thay đổi theo origin. `Cache-Control` quyết định cách dùng cache. `ETag` giúp client gửi conditional request và nhận 304 nếu response chưa đổi.

Bài tập trung **Spring WebFlux reactive stack**, không phải Spring MVC Servlet. Ví dụ đầy đủ dùng **Java 17**, **Spring Boot 3.3.5** và `spring-boot-starter-webflux`; không thêm `spring-boot-starter-web`. Một số trang tài liệu Spring “current” là Framework 7, trong khi Boot 3.3.5 quản lý Spring Framework 6.1.x; các code mẫu chỉ dùng API phù hợp Spring 6.1.

### Sau bài này bạn sẽ

- Giải thích origin, same-origin policy và CORS preflight.
- Phân biệt simple CORS requests, non-simple requests, credentials, preflight và actual response.
- Cấu hình `@CrossOrigin`, `WebFluxConfigurer.addCorsMappings`, `CorsWebFilter` khi phù hợp; không mở CORS tùy tiện.
- Biết CORS phải được phối hợp với **Spring Security WebFlux**, không thay authentication/authorization/CSRF.
- Phục vụ file từ `classpath:/static/` và tùy chỉnh `ResourceHandlerRegistry`.
- Đặt Cache-Control cho tài nguyên tĩnh, tránh cache HTML entrypoint quá lâu trong khi hashed JS/CSS có thể cache dài.
- Dùng `ResponseEntity.cacheControl(...)`, `eTag(...)`, `lastModified(...)` và conditional GET trả 304.
- Phân biệt `Cache-Control: no-cache`, `no-store`, `private`, `public`, `max-age`, `s-maxage`, `immutable`.
- Nhận biết sự khác nhau giữa `Access-Control-Max-Age` với `Cache-Control: max-age`.
- Viết kiểm thử WebTestClient cho preflight, origin bị từ chối, 200/304, file tĩnh, private caching.
- Chẩn đoán các lỗi thường gặp qua browser Network, curl, CDN, proxy và headers.

## 2. Nền tảng: Origin và same-origin policy

Một **origin** được xác định từ **scheme + host + port**. Ví dụ:

| URL | Origin | So với `https://app.example.test` |
| --- | --- | --- |
| `https://app.example.test/dashboard` | `https://app.example.test` | Cùng origin |
| `https://app.example.test:443/page` | Default HTTPS 443 | Cùng origin |
| `http://app.example.test/page` | HTTP thay vì HTTPS | Khác origin |
| `https://api.example.test/v1` | Subdomain khác | Khác origin |
| `https://app.example.test:8443` | Port khác | Khác origin |
| `https://cdn.example.test/app.js` | Host khác | Khác origin |

Browser thực thi **same-origin policy** để giới hạn JavaScript truy cập response từ origin khác. **CORS là giao thức để server nói với browser rằng một origin cụ thể được phép đọc response**, không phải phương thức xác thực user.

Ví dụ frontend:

~~~javascript
const result = await fetch(
  "https://api.example.test/api/public/catalog"
);
const data = await result.json();
~~~

Nếu trang đang chạy ở `https://app.example.test` thì đây là cross-origin fetch. Browser có thể gửi `Origin: https://app.example.test`. Server phải cung cấp CORS response headers phù hợp thì JavaScript mới đọc được JSON.

**CORS không ngăn tất cả HTTP requests được gửi đến server.** curl, Postman và server-to-server HTTP clients thường không áp dụng cùng kiểm tra CORS của browser. Nếu một endpoint DELETE không có authorization, việc CORS từ chối frontend độc hại **không làm endpoint an toàn** trước một HTTP client trực tiếp.

### 2.1. Origin không phải site, và khác URL path

Cookie SameSite dùng khái niệm **site** (schemeful site) chứ không hoàn toàn trùng origin. Hai subdomains có thể là same-site nhưng cross-origin. CORS quyết định browser JS có được đọc cross-origin response không; SameSite quyết định một phần cách cookie được gửi; CSRF chống việc lạm dụng authenticated browser requests. **Không thay thế ba cơ chế này cho nhau.**

### 2.2. Đừng nhầm CORS với CSP

- CORS là hợp đồng **resource server → browser** về quyền truy cập cross-origin.
- Content Security Policy (CSP) như `connect-src`, `script-src` là chính sách **document/app frontend** về nguồn nào được load/connect.
- Browser có thể yêu cầu cả hai đều phù hợp. `Access-Control-Allow-Origin` hợp lệ không làm `connect-src 'self'` tự cho phép gọi API ngoài origin.
- Các headers `Cross-Origin-Resource-Policy` (CORP) và `Cross-Origin-Embedder-Policy` (COEP) phục vụ use cases bảo vệ embedding/cross-origin isolation khác; chúng không phải alias của CORS.

## 3. Một CORS request thông thường diễn ra thế nào?

Một GET không gắn request header đặc biệt có thể được browser gửi trực tiếp:

~~~http
GET /api/public/catalog HTTP/1.1
Host: api.example.test
Origin: https://app.example.test
Accept: application/json
~~~

Server:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Vary: Origin
Content-Type: application/json

{"edition":"2026-A"}
~~~

Browser xem xét `Access-Control-Allow-Origin`. Nếu khớp origin và chính sách credentials phù hợp, JavaScript có thể đọc body. Nếu không, browser báo CORS error cho script dù server có thể đã xử lý request và gửi HTTP status/body.

**Không cố “sửa CORS” bằng cách thay `mode: "no-cors"`** trong fetch. Nó thường tạo opaque response mà JavaScript không đọc body/headers như một API JSON thông thường. Proxy browser extension tắt bảo mật không phải giải pháp triển khai production.

### 3.1. Request header Origin khác header Referer

`Origin` chứa nguồn yêu cầu theo browser semantics; `Referer` có thể bị rút gọn/ẩn theo Referrer-Policy và không phải thay thế chính xác cho Origin. Server CORS phải dùng cấu hình CORS chuẩn, không tự so sánh chuỗi bằng `request.getHeaders().getFirst("Referer")`.

### 3.2. Truy cập tài nguyên công khai bằng curl

~~~bash
curl -i 'http://localhost:8080/api/public/catalog' \
  -H 'Origin: https://app.example.test'
~~~

Bạn có thể thấy CORS headers từ server. Nhưng curl không tự áp dụng same-origin policy: **việc curl đọc được JSON không chứng minh browser cũng đọc được**. Dùng browser DevTools và test preflight/actual response khi xác nhận CORS.

## 4. Preflight: khi browser hỏi trước bằng OPTIONS

Nếu frontend gửi một request không thuộc nhóm CORS-safelisted, ví dụ:

~~~javascript
await fetch("https://api.example.test/api/public/echo", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-Client-Trace": "ui-demo"
  },
  body: JSON.stringify({name: "An"})
});
~~~

thì browser thường gửi **preflight OPTIONS** trước actual POST:

~~~http
OPTIONS /api/public/echo HTTP/1.1
Origin: https://app.example.test
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type,x-client-trace
~~~

Server chấp thuận ví dụ:

~~~http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: https://app.example.test
Access-Control-Allow-Methods: GET,POST
Access-Control-Allow-Headers: Content-Type,X-Client-Trace
Access-Control-Max-Age: 600
~~~

Sau preflight hợp lệ, browser mới gửi actual POST và kiểm CORS **trên response actual**. Không coi preflight thành công là đã xác thực user hoặc bảo đảm POST thành công.

### 4.1. Khi nào có preflight?

Những điều kiện hay kích hoạt preflight:
- HTTP method không phải một trong GET/HEAD/POST với các ràng buộc Fetch.
- Gửi custom headers như `X-Client-Trace`, `Authorization`.
- POST với `Content-Type: application/json` (không phải một trong các Content-Types được safelist trong tình huống chuẩn).
- Một số giá trị/kiểu headers và chế độ request khác có ràng buộc của Fetch.

Không đưa ra quy tắc đơn giản sai rằng “mọi POST đều preflight” hoặc “GET không bao giờ preflight”. Ví dụ GET có `Authorization` thường cần preflight khi cross-origin.

### 4.2. Preflight cache khác response cache

| Header | Ai dùng? | Ý nghĩa |
| --- | --- | --- |
| `Access-Control-Max-Age: 600` | Browser CORS-preflight cache | Cho phép tái sử dụng kết quả permission check một thời gian (browser có thể giới hạn mức tối đa) |
| `Cache-Control: max-age=600` | Browser HTTP cache / shared cache theo quy tắc | Freshness của HTTP representation, không phải permission preflight |
| `Expires` | HTTP caching | Thời điểm hết fresh theo semantics HTTP |
| `ETag`, `If-None-Match` | Conditional HTTP request | Có thể giảm tải body qua 304 |
| `Vary: Origin` | HTTP caches | Chỉ reuse response khi request headers phù hợp variant key |

Một server có thể đặt `Access-Control-Max-Age: 3600` mà API JSON vẫn có `Cache-Control: no-store`. Không suy từ một header sang header còn lại.

### 4.3. Đừng tự viết một OPTIONS handler trả dấu sao cho mọi origin

Tự gắn:

~~~http
Access-Control-Allow-Origin: *
Access-Control-Allow-Credentials: true
~~~

là một tổ hợp **không hợp lệ cho credentialed CORS requests**. Với credentials, Access-Control-Allow-Origin phải là origin cụ thể được cho phép; không dùng wildcard `*`. Spring CORS sẽ giúp validate cấu hình phù hợp nhưng security review vẫn cần thiết.

## 5. Bốn response headers CORS quan trọng

### 5.1. `Access-Control-Allow-Origin`

Cho phép origin cụ thể hoặc `*` khi thật sự phù hợp cho tài nguyên công khai không credentialed. Với `allowCredentials(true)`, không được dùng `allowedOrigins("*")` theo hợp đồng và Spring validation. Có thể dùng `allowedOriginPatterns` theo các pattern kiểm soát nếu use case thực sự yêu cầu, nhưng tốt hơn là allowlist một tập origins hữu hạn.

### 5.2. `Access-Control-Allow-Methods`

Dùng trong preflight để khai báo methods được phép đối với browser cross-origin request. Nó **không tự mở method mapping** trên server: CORS cho POST không biến một controller chỉ có GET thành POST endpoint. Cũng không cấp user permission.

### 5.3. `Access-Control-Allow-Headers`

Cho phép các **request headers** mà browser định dùng trong actual request, ví dụ `Content-Type`, `Authorization` hoặc `X-Client-Trace`. Không nhầm với `Access-Control-Expose-Headers`.

### 5.4. `Access-Control-Expose-Headers`

Chỉ định các **response headers không mặc nhiên CORS-safelisted** mà frontend JS được phép đọc.

~~~javascript
const response = await fetch("https://api.example.test/api/public/catalog");
console.log(response.headers.get("ETag"));
~~~

Nếu muốn browser JavaScript đọc `ETag`, có thể đặt `Access-Control-Expose-Headers: ETag`. Việc header tồn tại trên network không đảm bảo fetch JS đọc được nếu chưa được expose phù hợp.

**Lưu ý:** `Set-Cookie` là forbidden response header name và không thể biến thành header JS đọc được chỉ bằng `exposedHeaders("Set-Cookie")`. Cookie vẫn có policies riêng.

## 6. Ba cách cấu hình CORS trong Spring WebFlux

### 6.1. `@CrossOrigin` trên annotated controller

Dùng khi chỉ một hoặc vài endpoints đặc biệt:

~~~java
@CrossOrigin(
        origins = "https://app.example.test",
        methods = {RequestMethod.GET},
        maxAge = 600)
@GetMapping("/api/public/catalog")
public Mono<Catalog> catalog() {
    return Mono.just(catalogService.current());
}
~~~

Đây là **fragment**, không độc lập: cần class controller, `Catalog` và service. `@CrossOrigin` ở class áp dụng cho các methods được phép và có thể kết hợp với cấu hình global; quy tắc combine giữa class/method/global cần được kiểm nếu có nhiều lớp policy.

Ưu điểm: policy nằm gần endpoint, dễ hiểu nếu chỉ vài exception. Nhược điểm: khó audit khi hàng trăm controllers gắn origins khác nhau và mỗi môi trường có frontend origins khác nhau.

### 6.2. `WebFluxConfigurer.addCorsMappings`: cấu hình chung cho URL pattern

Đây là lựa chọn chuẩn để quản lý CORS cho nhóm annotated APIs:

~~~java
@Configuration
public class CorsWebConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/public/**")
                .allowedOrigins("https://app.example.test")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Client-Trace")
                .exposedHeaders("ETag", "X-Request-Id")
                .allowCredentials(false)
                .maxAge(600);
    }
}
~~~

**Đối với Spring Boot**, chỉ thêm `@Configuration implements WebFluxConfigurer`, **không gắn `@EnableWebFlux`** nếu muốn giữ các WebFlux auto-configurations của Boot. `@EnableWebFlux` dùng khi bạn có chủ đích tiếp quản cấu hình framework và chấp nhận cấu hình lại những tích hợp Boot cần thiết.

Giải thích:
- `addMapping("/api/public/**")` là path pattern áp dụng.
- `allowedOrigins` chỉ cho origin frontend được công bố.
- `allowedMethods` gồm GET và POST.
- `allowedHeaders` cho phép JSON + header custom của client.
- `exposedHeaders` cho JavaScript đọc ETag hoặc correlation header do server gửi.
- `allowCredentials(false)` không bật credentialed CORS.
- `maxAge(600)` là thời gian **preflight cache**, theo giây.

Không dùng cấu hình origin production `"*".allowCredentials(true)`. Nếu API có auth cookie, cân nhắc explicit origins, secure cookies, CSRF và Spring Security integration.

### 6.3. `CorsWebFilter` cho functional routes / xử lý sớm toàn stack

Với WebFlux.fn hoặc muốn CORS được xử lý trong WebFilter chain, Spring cung cấp `CorsWebFilter`:

~~~java
@Bean
CorsWebFilter corsWebFilter() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of("https://app.example.test"));
    cors.setAllowedMethods(List.of("GET", "POST"));
    cors.setAllowedHeaders(
            List.of("Content-Type", "X-Client-Trace"));
    cors.setAllowCredentials(false);
    cors.setMaxAge(600L);

    UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/public/**", cors);
    return new CorsWebFilter(source);
}
~~~

Đây là **fragment**: class config và imports phù hợp còn thiếu. Servlet `CorsFilter` không phải bean của WebFlux reactive stack; dùng đúng `org.springframework.web.cors.reactive.CorsWebFilter` và `org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource`.

**Chọn một nguồn chính sách CORS rõ ràng.** Đừng vô ý bật cả global registry, `@CrossOrigin`, một CorsWebFilter và thêm CORS headers trong gateway mà không xác định thứ tự/chồng lấn. Kết quả có thể là duplicate/contradictory headers hoặc việc kiểm soát không đồng nhất giữa preflight và actual responses.

### 6.4. Spring Security WebFlux phải xử lý preflight trước auth

Preflight thường **không mang cùng cookie authentication** như actual request. Nếu authentication filter chặn OPTIONS trước khi CORS xử lý, browser báo lỗi CORS dù API POST sau đó có thể hợp lệ.

Khi thêm `spring-boot-starter-security` và `SecurityWebFilterChain`:
- Đảm bảo CORS integration được thực hiện **trước security authentication/authorization**, theo hướng dẫn Spring Security reactive.
- Có thể cấu hình `CorsWebFilter` hoặc `CorsConfigurationSource` phù hợp để Spring Security tích hợp.
- `http.cors(...)` ở reactive security nên được cấu hình theo phiên bản Security 6.x đang dùng.
- Không disable toàn bộ CSRF chỉ vì frontend cross-origin.
- Actual GET/POST phải vẫn qua authentication/authorization/object ownership.
- Test 200/401/403/OPTIONS bằng `WebTestClient` với filter chain thật.

Không copy snippet Servlet `HttpSecurity`/`SecurityFilterChain` sang reactive `ServerHttpSecurity`/`SecurityWebFilterChain`.

## 7. CORS không phải authorization và không ngăn CSRF

Một endpoint có `Access-Control-Allow-Origin` rộng vẫn phải kiểm:
- Ai là caller, token/session có hợp lệ không?
- User có được đọc tài nguyên ID đó hay không?
- Tenant hiện tại là tenant nào?
- Browser credentialed requests có CSRF token cho thao tác state-changing không?
- Có rate limits và protections khác không?

CORS phần lớn ngăn JavaScript ở origin không được phép **đọc** cross-origin responses; nó không đảm bảo request gây side effect không bao giờ tới server. Form submission, cross-site requests và HTTP clients trực tiếp là những threat models khác.

### 7.1. `credentials: "include"` đòi hỏi nhiều điều kiện

Browser request:

~~~javascript
fetch("https://api.example.test/api/account/me", {
  credentials: "include"
});
~~~

Nếu server muốn hỗ trợ credentialed CORS:
- Chỉ cho **origins xác định**.
- `Access-Control-Allow-Credentials: true` trên response phù hợp.
- Cookie attributes phải cho phép gửi cross-site khi cần (`SameSite=None; Secure` khi phù hợp), và browser có thể có third-party cookie restrictions.
- CSRF/session security vẫn cần đúng.
- Không thể dùng `Access-Control-Allow-Origin: *` với credentialed response.

Ngay cả khi `allowCredentials(true)` trong Spring CORS, nó không tạo auth session, không cấp role và không bảo đảm cookie sẽ được browser gửi; các cấu hình khác vẫn quyết định.

### 7.2. CORS với CDN

Nếu server thay `Access-Control-Allow-Origin` theo incoming `Origin`, cần tránh CDN trả response có ACAO của origin A cho origin B. Framework có thể phát `Vary: Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers` khi phù hợp; CDN phải tôn trọng `Vary` hoặc có custom cache key an toàn.

**Không dùng `Vary: Origin` thay `Cache-Control: no-store/private` cho tài nguyên chứa dữ liệu user.** Hai clients cùng Origin không phải cùng một user. CORS và cache key không là authentication mechanism.

## 8. Spring Boot WebFlux phục vụ tài nguyên tĩnh mặc định

Spring Boot hỗ trợ static resources trong các thư mục classpath quen thuộc:
- `classpath:/static/`
- `classpath:/public/`
- `classpath:/resources/`
- `classpath:/META-INF/resources/`

Ví dụ file:

~~~text
src/main/resources/static/index.html
src/main/resources/static/css/app.css
src/main/resources/static/js/app.js
~~~

Có thể được phục vụ tại:
- `GET /` cho welcome page `index.html` khi không có route khác ưu tiên.
- `GET /css/app.css`.
- `GET /js/app.js`.

Spring Boot WebFlux dùng `ResourceWebHandler` để phục vụ các files đó. Mapping mặc định thường `/**`; có thể thay bằng `spring.webflux.static-path-pattern` và `spring.web.resources.static-locations` theo phiên bản Boot tương ứng.

**Không coi `src/main/webapp` của Servlet WAR là nguồn file chuẩn cho Boot WebFlux executable JAR**. WebFlux không phụ thuộc Servlet WAR deployment; đặt files dưới `src/main/resources` để đóng gói đúng.

### 8.1. Static file không phải view template

- `src/main/resources/static/index.html` thường là HTML **nguyên bản**, không tự xử lý biến Thymeleaf như một template.
- Template engines dùng thư mục riêng `templates/` và view resolvers.
- JS/CSS bundler có thể xuất files vào `static/assets/`; cần quản lý cache busting/versioned names.
- SPA client-side routes như `/app/settings` không được mặc định tự map về `index.html` trong mọi setup WebFlux. Nếu cần SPA fallback, cấu hình có kiểm soát và tránh bắt nhầm `/api/**`, file assets hoặc status 404.

### 8.2. Tùy chỉnh static path trong properties

~~~properties
spring.webflux.static-path-pattern=/assets/**
~~~

Đây là ví dụ cấu hình thay mapping static tài nguyên mặc định, **không phải ví dụ dự án runnable bên dưới**. Nếu dùng, phải cập nhật HTML asset paths và hiểu welcome page behavior; không gắn thêm properties này vào demo nếu đã chủ động cấu hình resource handler `/docs/**`.

### 8.3. Static resources có thể chịu CORS không?

Có. Browser fetch một file tĩnh bằng JavaScript qua origin khác có thể cần CORS, trong khi một thẻ HTML như `<img src>` hoặc `<script src>` tuân theo các cơ chế tải/hiển thị liên quan khác. Nếu muốn một frontend fetch fonts/assets qua CDN, cấu hình CORS cho các URL assets cần thiết hoặc tại CDN. Không tự cho rằng CORS mapping `/api/**` áp dụng cho `/assets/**`.

## 9. `ResourceHandlerRegistry`: custom route cho tài nguyên tĩnh

WebFlux có `WebFluxConfigurer.addResourceHandlers`:

~~~java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/docs/")
            .setCacheControl(
                    CacheControl.maxAge(Duration.ofMinutes(5))
                            .cachePublic());
}
~~~

Khi ứng dụng đóng gói `src/main/resources/docs/guide.txt`, request `GET /docs/guide.txt` sẽ đọc resource tương ứng. `max-age=300` giới hạn freshness cho HTTP cache trong ví dụ; `public` cho phép shared caches dùng response nếu không có ràng buộc khác.

### 9.1. Các thành phần quan trọng

- `addResourceHandler("/docs/**")`: URL pattern do server tiếp nhận.
- `addResourceLocations("classpath:/docs/")`: root thư mục nguồn, không phải URL client dùng.
- `setCacheControl(...)`: policy HTTP cache cho các tài nguyên được phục vụ bởi handler đó.
- Resource resolvers và transformers có thể tham gia cache-busting/versioned URLs, compressed resources, path resolution.

Không truyền file system path từ query parameter trực tiếp sang resource loader mà không có path validation. Các path traversal protections của Spring vẫn nên được giữ; không tự disable normalization checks để “fix” một đường dẫn khó.

### 9.2. Metadata, 304 và `Last-Modified`

Resource handler có thể dùng metadata như last-modified để hỗ trợ conditional requests: client gửi `If-Modified-Since`, server có thể trả **304 Not Modified** nếu file không đổi và preconditions phù hợp. Không cam kết mọi loại resource (file trong JAR, cloud object, dynamic generated file) luôn có last-modified timestamp chính xác như nhau.

`Cache-Control` chỉ nói cache policy; `Last-Modified` và `ETag` cung cấp validators. Browser có thể dùng fresh cached asset mà **không gửi request**; khi stale, nó có thể gửi conditional request thay vì tải full bytes lại.

### 9.3. Không đặt cache lâu cho tài nguyên có URL không versioned

Nếu `/js/app.js` vẫn cùng URL sau deploy nhưng bạn đặt `Cache-Control: public, max-age=31536000, immutable`, client có thể dùng JS cũ trong thời gian dài. Với files thay nội dung thường xuyên, dùng max-age ngắn và validators, hoặc triển khai filenames/hashes.

## 10. Versioned assets và chính sách cache dài hạn

Frontend build tools thường tạo:

~~~text
/assets/app.f2a21c6a.js
/assets/app.83e4d9cd.css
/assets/logo.9da430ef.svg
~~~

Khi filename phản ánh content hash và build/deployment bảo đảm assets cũ vẫn truy cập được trong giai đoạn chuyển phiên bản, có thể đặt policy dài:

~~~http
Cache-Control: public, max-age=31536000, immutable
~~~

Nhưng `index.html` có thể chỉ nên:

~~~http
Cache-Control: no-cache
ETag: "index-build-20261021"
~~~

để mỗi lần browser revalidate entrypoint, nó có thể nhận references mới tới hashed assets.

**`no-cache` không có nghĩa "cấm lưu".** Nó cho phép lưu nhưng yêu cầu revalidation trước reuse. `no-store` mới là chỉ thị cấm lưu response vào HTTP caches tuân thủ quy tắc, phù hợp dữ liệu bí mật/nhạy cảm hơn.

### 10.1. `VersionResourceResolver` của WebFlux

Spring WebFlux có resource chain và `VersionResourceResolver` có thể tạo versioned resource URLs theo content hash hoặc fixed strategies:

~~~java
registry.addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .resourceChain(true)
        .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"));
~~~

Đây là **fragment** cho một use case khác, không đặt chung với custom `/docs/**` demo bên dưới. Nếu frontend tham chiếu `/assets/app.js` mà server kỳ vọng `/assets/app-<hash>.js`, cần quy trình build/URL rewriting hoặc `ResourceUrlProvider` tương ứng. **Đừng chỉ bật resolver và cho rằng HTML/JS links tự động được rewrite đúng trong mọi WebFlux view**.

Khi kết hợp compressed resolvers (`EncodedResourceResolver`) và versioning, Spring docs yêu cầu chú ý thứ tự để hash được tính từ bytes **chưa nén**. Hãy dùng resource chain được test đúng trên cả file gzip/Brotli và CDN; không gán ETag của representation khác nhau tùy tiện.

### 10.2. CDN cache lâu cần chiến lược giữ phiên bản cũ

Một HTML cũ có thể vẫn tham chiếu `app.oldhash.js` ngay sau deploy mới. Nếu server/CDN xóa asset cũ ngay, người dùng đang mở tab cũ có thể lỗi. Chiến lược triển khai assets hashed nên bảo đảm:
- Files cũ còn sẵn trong cửa sổ tương thích.
- CDN purge không xóa nhầm content-addressed files vẫn được client dùng.
- HTML entrypoint nhận validators hoặc freshness ngắn.
- Build atomic và rollback không dẫn tới “HTML mới + assets cũ không còn”.
- Security patch nguy cấp có kế hoạch invalidation đặc biệt.

## 11. HTTP caching: khái niệm nền tảng

Một cache có thể là:
- Browser private cache.
- Shared proxy/CDN cache.
- Reverse proxy ứng dụng.
- Service worker cache (cơ chế khác biệt, có script quản lý riêng).
- Application-level cache như Spring Cache/Redis (không tự tuân HTTP headers).

Các đối tượng này **không mặc nhiên có cùng cache key/eviction policy**. Một `@Cacheable` ở service không tự gửi `Cache-Control`/ETag ra HTTP. Ngược lại, `Cache-Control: no-store` ở response không tự xóa mọi entry trong Redis hoặc database.

### 11.1. `Cache-Control` directives

| Directive | Diễn giải cốt lõi | Dùng khi |
| --- | --- | --- |
| `no-store` | HTTP cache không được lưu response/request theo quy tắc | Dữ liệu nhạy cảm hoặc không muốn HTTP caching |
| `no-cache` | Có thể lưu, nhưng phải revalidate trước reuse | HTML entrypoint, nội dung cần luôn kiểm tính mới |
| `private` | Shared cache không được lưu; private cache có thể lưu | Dữ liệu cá nhân khi browser caching được phép |
| `public` | Cho phép shared cache lưu khi các điều kiện khác đáp ứng | Public assets/data |
| `max-age=N` | Freshness theo giây | Public resource ít đổi |
| `s-maxage=N` | Freshness cho shared cache, override max-age cho shared cache | CDN/proxy policy riêng |
| `must-revalidate` | Hạn chế dùng stale khi cần revalidation | Nội dung cần xử lý stale nghiêm |
| `immutable` | Gợi ý không revalidate khi fresh vì representation bất biến | Content-hashed assets |
| `no-transform` | Yêu cầu intermediaries không transform representation | Nội dung cần giữ nguyên |

Không tự kết luận một response với `Cache-Control: public` luôn được CDN cache: request Authorization, cookie, status, cache key, CDN configuration, `Vary` và headers khác vẫn ảnh hưởng. Không đưa dữ liệu user vào public cache do hiểu sai `public`.

### 11.2. Fresh vs stale

Giả sử:

~~~http
Cache-Control: public, max-age=60
~~~

Trong khoảng fresh, cache có thể trả response mà không liên hệ origin (nếu cache thực sự lưu và các điều kiện còn đúng). Sau đó response stale, client/cache có thể cần conditional revalidation.

Một response với `no-cache` có thể được **lưu** nhưng phải kiểm lại để dùng; không coi nó là “disable browser cache” một cách tuyệt đối.

### 11.3. HTTP cache key và query strings

Đường dẫn `/api/public/catalog?lang=vi` và `?lang=en` là hai target URIs khác nhau trong cache model thông thường, nhưng CDN có thể cấu hình bỏ query params. Với response phụ thuộc `Accept-Language` hoặc `Origin`, cần `Vary` thích hợp. Không dựa vào cache key lý thuyết khi gateway/CDN đã override key.

## 12. Conditional GET: `ETag`, `Last-Modified` và 304

### 12.1. Chuỗi 200 → If-None-Match → 304

Lần đầu:

~~~http
GET /api/public/catalog HTTP/1.1
~~~

Response:

~~~http
HTTP/1.1 200 OK
ETag: "catalog-2026-a"
Cache-Control: public, max-age=60
Content-Type: application/json

{"edition":"2026-A","subjects":["WebFlux","HTTP"]}
~~~

Khi cache cần revalidate:

~~~http
GET /api/public/catalog HTTP/1.1
If-None-Match: "catalog-2026-a"
~~~

Nếu server xác định representation tương ứng chưa thay đổi:

~~~http
HTTP/1.1 304 Not Modified
ETag: "catalog-2026-a"
Cache-Control: public, max-age=60
~~~

304 không có response body JSON mới; cache/browser giữ body đã lưu nếu còn có thể dùng nó với metadata cập nhật. **304 không phải 200 với body rỗng**. Một client tự viết bằng HTTP library không có cache vẫn có thể nhận 304 mà không có bytes JSON để parse — cần có cached representation trước đó hoặc xử lý khác.

### 12.2. ETag mạnh và yếu

- Strong ETag thường dạng `"abc123"`, biểu thị validator mạnh theo quy tắc byte-for-byte của representation.
- Weak ETag dạng `W/"abc123"`, hữu ích khi muốn phản ánh tương đương ngữ nghĩa nhưng bytes có thể khác.
- ETag là **opaque token**, không phải hash được browser tự hiểu. Không để lộ private state hoặc internal database secrets trong token.
- Nếu cùng ETag được dùng cho hai JSON representations khác nhau theo ngôn ngữ/media type, bạn có thể gặp sai semantics; tính/định nghĩa ETag theo representation hợp lệ và `Vary`.

Trong Spring WebFlux, `ResponseEntity.BodyBuilder.eTag("catalog-2026-a")` sẽ chuẩn hóa dấu ngoặc kép để tạo ETag hợp lệ. Đừng tự nối dấu quote nhiều lần không kiểm.

### 12.3. Last-Modified

~~~http
Last-Modified: Thu, 01 Oct 2026 00:00:00 GMT
~~~

Client có thể gửi `If-Modified-Since`. Timestamp có độ phân giải theo giây trong HTTP date, nên thay đổi nhiều lần trong cùng một giây khó biểu diễn chính xác; một ETag theo version/resource representation thường đáng tin hơn cho granular updates.

Nếu client gửi cả `If-None-Match` và `If-Modified-Since`, HTTP semantics quy định thứ tự ưu tiên cho conditional evaluation; **không tự cộng hai điều kiện thành AND tùy ý**.

### 12.4. 304 không có nghĩa bỏ qua authorization

Nếu resource tùy user/tenant, server vẫn cần kiểm authentication/authorization ở request revalidation. Một attacker không được phép không thể nhận 304 rồi lợi dụng cache/body cũ của người khác. 304 và ETag giảm bandwidth, không phải skip permission checks.

### 12.5. Precondition cho writes

`If-Match`/ETag còn có thể dùng optimistic concurrency cho PUT/PATCH/DELETE; server có thể trả **412 Precondition Failed** khi tài nguyên đã đổi. Đây là mục đích khác với GET `If-None-Match` → 304. Với update cần so sánh version trong transaction an toàn, không chỉ dựa trên ETag rồi update database ở bước sau không đồng bộ.

## 13. Dùng `CacheControl` và `ResponseEntity` trong Spring WebFlux

Spring `CacheControl` xây response header đúng các use cases phổ biến:

~~~java
CacheControl cache = CacheControl.maxAge(Duration.ofSeconds(60))
        .cachePublic();
~~~

Controller trả:

~~~java
@GetMapping("/api/public/catalog")
public ResponseEntity<Catalog> catalog() {
    return ResponseEntity.ok()
            .cacheControl(cache)
            .eTag("catalog-2026-a")
            .body(new Catalog("2026-A", List.of("WebFlux", "HTTP")));
}
~~~

Đây là fragment (class `Catalog` và biến `cache` chưa có); demo runnable ở mục 17 khai báo đầy đủ. Với WebFlux có thể dùng cả `Mono<ResponseEntity<T>>` nếu metadata/status/body chỉ biết sau công việc bất đồng bộ. Không gọi `.block()` để chuẩn bị cache policy trong controller.

### 13.1. Spring hỗ trợ conditional requests khi ResponseEntity có validators

Spring WebFlux có thể xử lý conditional GET/HEAD dựa trên ETag/lastModified được trả từ controller và đưa ra 304 theo điều kiện. Cấu hình cần đúng contract, method và version. Dùng WebTestClient gửi `If-None-Match` để xác minh behavior thay vì đoán.

### 13.2. API cá nhân hóa: `no-store`

~~~java
@GetMapping("/api/example-sensitive")
public ResponseEntity<Map<String,String>> sample() {
    return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(Map.of("sample", "not real user data"));
}
~~~

Đây là fragment. `no-store` thích hợp khi HTTP response rất nhạy cảm; nhưng nó không tự ngăn server app logs hoặc người dùng tự sao chép thông tin. Nếu response thực sự chứa dữ liệu cá nhân, authentication + object/tenant authorization vẫn là điều kiện tiên quyết.

### 13.3. Shared cache với `Authorization`

RFC 9111 có các hạn chế/ngoại lệ cho shared caches với response của request có Authorization. Không đặt `Cache-Control: public` hoặc `s-maxage` cho tài nguyên credentialed có body cá nhân hóa khi chưa có kiến trúc cache key/security được review kỹ.

Một user có thể truy cập cùng `/api/me` với user khác; nếu CDN cache theo URI đơn thuần, cả hai có nguy cơ nhận cùng body. `Vary: Origin`, `Vary: Accept` hoặc `Vary: Authorization` không phải giải pháp thay thế đầy đủ cho tenant/user isolation và safe caching policy. Dùng `private/no-store` theo độ nhạy cảm.

## 14. CORS và cache có tương tác phức tạp

### 14.1. Public API phụ thuộc Origin

Server có thể phản hồi một whitelist origin từ request và gửi `Vary: Origin`. Nếu một shared cache bỏ qua `Vary` hoặc CDN custom key không xét Origin, nó có thể phục vụ response của origin A cho origin B, dẫn đến browser lỗi CORS hoặc chính sách ngoài ý muốn.

CORS chỉ nói origin nào được JS đọc. Một response có ACAO cho app.example.test **không** có nghĩa dữ liệu an toàn để lưu vào shared cache. Nếu dữ liệu tùy user, đánh giá Cache-Control độc lập.

### 14.2. Vary: Accept-Encoding và versioned assets

Compressed resources có các variants gzip/Brotli; khi representation phụ thuộc `Accept-Encoding`, `Vary: Accept-Encoding` liên quan đến cache selection. Không phục vụ cùng ETag mạnh cho bytes nén và chưa nén nếu vi phạm strong validator semantics.

### 14.3. Browser preflight cache không thay ETag validation

CORS preflight permission có thể được cache 600 giây, nhưng cùng client có thể gửi conditional GET bất cứ lúc nào cần revalidation HTTP. Browser có thể không gọi OPTIONS lần nữa nhưng vẫn gọi GET và nhận 304. Hai hệ cache độc lập, không nên dùng chung invalidation assumption.

### 14.4. CORS headers trong 304 response

Một conditional request từ frontend khác origin vẫn chịu CORS. Nếu response 304 được gửi nhưng mất `Access-Control-Allow-Origin`/`Vary` phù hợp qua gateway/cache, browser có thể không dùng response như mong muốn. Kiểm 200 **và** 304 qua Origin header trong integration/CDN tests.

## 15. Cấu hình nào áp dụng cho file tĩnh và API?

| Phạm vi URL | Loại response | CORS | HTTP cache gợi ý |
| --- | --- | --- | --- |
| `/api/public/**` | JSON public, không bí mật | Allowlist frontend origins cần dùng | Freshness ngắn + ETag |
| `/api/account/**` | Dữ liệu xác thực | Explicit origin + credentials nếu cần | `no-store` hoặc `private` theo policy |
| `/docs/**` | File docs public | Chỉ cần CORS nếu browser JS đọc cross-origin | `max-age` ngắn, validators |
| `/assets/**` hashed | JS/CSS/images public | Theo CDN/font/fetch needs | `public, max-age=31536000, immutable` khi filenames thật sự content-addressed |
| `/index.html` | Entrypoint cập nhật thường | Thường cùng origin | `no-cache` hoặc freshness ngắn + validators |
| `/api/admin/**` | Quản trị | Hạn chế origin, không mở từ mọi site | `no-store`, auth và authorization bắt buộc |

**Không sử dụng một global `Cache-Control: public` cho mọi URL**, vì nó có thể vô tình áp lên 401/403/PII/error pages. Cũng không dùng `@CrossOrigin(origins="*")` trên tất cả controllers chỉ để tránh lỗi frontend development.

## 16. Demo runnable: Java 17 + Spring Boot 3.3.5 WebFlux

Ứng dụng nhỏ triển khai:
- `GET /api/public/catalog`: response JSON ổn định, `Cache-Control: public, max-age=60`, `ETag: "catalog-2026-a"`, `Last-Modified` cố định.
- `POST /api/public/echo`: nhận JSON nhỏ từ `@RequestBody Mono<HelloRequest>` và trả JSON; ví dụ để test CORS preflight.
- `GET /api/example-sensitive`: fixture không chứa PII nhưng minh họa `Cache-Control: no-store`.
- `GET /docs/guide.txt`: file public từ classpath, cache 5 phút qua `ResourceHandlerRegistry`.
- `GET /`: trang `static/index.html` do Spring Boot auto-config phục vụ.
- `WebFluxConfigurer`: allowlist CORS chính xác cho `/api/public/**`, custom static handler cho `/docs/**`.
- `WebTestClient`: kiểm valid/invalid origins, preflight POST, ETag/304, static content và no-store.

Ứng dụng không có auth/database, vì mục tiêu là CORS/static/cache. Không xem endpoint `example-sensitive` là ví dụ bảo vệ dữ liệu người dùng thật; ở production thêm Spring Security và resource-level authorization.

~~~text
webflux-cors-static-cache/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/example/cacheweb/
    │   │   ├── CacheWebApplication.java
    │   │   ├── config/WebFluxHttpConfig.java
    │   │   └── web/CatalogController.java
    │   └── resources/
    │       ├── static/index.html
    │       └── docs/guide.txt
    └── test/java/com/example/cacheweb/
        └── CorsStaticCacheIntegrationTest.java
~~~

### 16.1. Gradle và Java

`settings.gradle`:

~~~groovy
rootProject.name = 'webflux-cors-static-cache'
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

**Không thêm** `spring-boot-starter-web`: khi cả MVC và WebFlux starters có mặt, Spring Boot thường chọn MVC application type; ví dụ trên cần WebFlux với Reactor Netty. Gradle wrapper nên được tạo bằng phiên bản Gradle tương thích với Java 17/Boot 3.3.5 nếu project chưa có `gradlew`.

### 16.2. Main application

`src/main/java/com/example/cacheweb/CacheWebApplication.java`:

~~~java
package com.example.cacheweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CacheWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CacheWebApplication.class, args);
    }
}
~~~

### 16.3. WebFlux HTTP config: CORS và file tĩnh

`src/main/java/com/example/cacheweb/config/WebFluxHttpConfig.java`:

~~~java
package com.example.cacheweb.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxHttpConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/public/**")
                .allowedOrigins("https://app.example.test")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Client-Trace")
                .exposedHeaders("ETag", "X-Request-Id")
                .allowCredentials(false)
                .maxAge(600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/docs/**")
                .addResourceLocations("classpath:/docs/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofMinutes(5))
                                .cachePublic());
    }
}
~~~

Không dùng `@EnableWebFlux` để giữ Boot auto-config. CORS mapping chỉ áp dụng cho `/api/public/**`, không mở origin cho `/api/example-sensitive` hoặc `/docs/**`. Nếu browser JavaScript cần fetch `/docs/**` cross-origin, phải thêm CORS policy riêng sau khi review, không tự suy nó từ public cache.

### 16.4. Controller: ResponseEntity, ETag, Last-Modified, no-store

`src/main/java/com/example/cacheweb/web/CatalogController.java`:

~~~java
package com.example.cacheweb.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class CatalogController {

    public record Catalog(String edition, List<String> subjects) {}
    public record HelloRequest(String name) {}
    public record HelloResponse(String greeting) {}

    private static final String CATALOG_VERSION = "catalog-2026-a";
    private static final Instant UPDATED =
            Instant.parse("2026-10-01T00:00:00Z");

    @GetMapping(
            path = "/public/catalog",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Catalog> catalog() {
        Catalog data = new Catalog(
                "2026-A", List.of("WebFlux", "HTTP"));

        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.maxAge(Duration.ofSeconds(60))
                                .cachePublic())
                .eTag(CATALOG_VERSION)
                .lastModified(UPDATED)
                .body(data);
    }

    @PostMapping(
            path = "/public/echo",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<HelloResponse>> echo(
            @RequestBody Mono<HelloRequest> body) {

        return body.map(request -> {
            String name = request.name();

            if (name == null || name.isBlank()
                    || name.length() > 50) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid name");
            }

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new HelloResponse(
                            "Hello, " + name.trim()));
        });
    }

    @GetMapping(
            path = "/example-sensitive",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> noStoreExample() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "note", "Fixture only; no personal data"));
    }
}
~~~

**Điểm đáng kiểm trong code:**
- `CATALOG_VERSION` và `UPDATED` là metadata giả định **ổn định vì catalog fixture cố định**. Khi catalog thật thay, bắt buộc thay ETag/version và lastModified cho phù hợp nội dung.
- `.eTag(CATALOG_VERSION)` được Spring chuẩn hóa thành `"catalog-2026-a"`.
- `.cacheControl(...)` tạo Cache-Control response header; **không** tự tạo `Vary: Origin`, việc đó thuộc CORS processing.
- `.lastModified(UPDATED)` thêm Last-Modified, tạo cơ sở `If-Modified-Since` khi phù hợp.
- `ResponseEntity<Catalog>` có status/headers/body quyết định đồng bộ; nó phù hợp vì dữ liệu fixture không có call bất đồng bộ. Với reactive service, dùng `Mono<ResponseEntity<Catalog>>` khi metadata đến từ reactive publisher.
- POST `echo` trả response `no-store` vì đó là input-driven response; thực tế echo có thể chứa PII nên không cache.
- `@RequestBody Mono<HelloRequest>` để codec Spring đọc JSON; không đọc request `Flux<DataBuffer>` lần thứ hai.
- Không dùng `block()`, `Thread.sleep` hoặc JDBC blocking trên event loop.
- Error body từ `ResponseStatusException` phụ thuộc Spring Boot error handling; sample tests chỉ kiểm status 400, không khẳng định JSON schema cố định.
- Vì không có security dependency, sample `example-sensitive` **không có auth**; nó chỉ dạy HTTP cache directive.

### 16.5. Trang HTML mặc định

`src/main/resources/static/index.html`:

~~~html
<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <title>WebFlux HTTP demo</title>
</head>
<body>
  <h1>WebFlux CORS / Static / Cache</h1>
  <p>Trang này được phục vụ từ classpath:/static/index.html.</p>
</body>
</html>
~~~

Spring Boot có welcome page detection; khi không có handler `GET "/"` ưu tiên hơn, `/` sẽ trả nội dung của `index.html`. Đây là **file HTML tĩnh**, không xử lý Thymeleaf expressions.

### 16.6. File docs phục vụ bởi handler tùy chỉnh

`src/main/resources/docs/guide.txt`:

~~~text
WebFlux resource guide
~~~

`GET /docs/guide.txt` sẽ đọc file theo path registration trong `WebFluxHttpConfig`. `Cache-Control` được set ở ResourceWebHandler cho route này; nếu cần CORS cho docs tải bằng browser cross-origin fetch, cấu hình nó riêng.

## 17. Integration tests với WebTestClient

Dùng embedded Reactor Netty với port ngẫu nhiên để kiểm cả HTTP routing, CORS và response headers. Đây không phải Servlet MockMvc tests.

`src/test/java/com/example/cacheweb/CorsStaticCacheIntegrationTest.java`:

~~~java
package com.example.cacheweb;

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
class CorsStaticCacheIntegrationTest {

    private static final String FRONTEND =
            "https://app.example.test";

    @Autowired
    WebTestClient client;

    @Test
    void publicCatalogHasExplicitCorsAndCacheHeaders() {
        client.get().uri("/api/public/catalog")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
                .expectHeader().valueEquals(
                        HttpHeaders.ETAG, "\"catalog-2026-a\"")
                .expectHeader().valueContains(
                        HttpHeaders.CACHE_CONTROL, "max-age=60")
                .expectBody()
                .jsonPath("$.edition").isEqualTo("2026-A")
                .jsonPath("$.subjects[0]").isEqualTo("WebFlux");
    }

    @Test
    void conditionalGetReturns304WithoutNewBody() {
        client.get().uri("/api/public/catalog")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .header(HttpHeaders.IF_NONE_MATCH, "\"catalog-2026-a\"")
                .exchange()
                .expectStatus().isNotModified()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
                .expectHeader().valueEquals(
                        HttpHeaders.ETAG, "\"catalog-2026-a\"")
                .expectBody().isEmpty();
    }

    @Test
    void allowedPreflightPermitsJsonPostAndCustomHeader() {
        client.options().uri("/api/public/echo")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type,x-client-trace")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
                .expectHeader().valueContains(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST");
    }

    @Test
    void forbiddenOriginIsRejectedByCorsProcessor() {
        client.get().uri("/api/public/catalog")
                .header(HttpHeaders.ORIGIN, "https://evil.example.test")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void actualJsonPostIsHandledAndNotCached() {
        client.post().uri("/api/public/echo")
                .header(HttpHeaders.ORIGIN, FRONTEND)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "An"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND)
                .expectHeader().valueContains(
                        HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.greeting").isEqualTo("Hello, An");
    }

    @Test
    void personalizedPolicyExampleSaysNoStore() {
        client.get().uri("/api/example-sensitive")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueContains(
                        HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody()
                .jsonPath("$.note").exists();
    }

    @Test
    void customDocsResourceIsServedWithShortPublicCache() {
        client.get().uri("/docs/guide.txt")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueContains(
                        HttpHeaders.CACHE_CONTROL, "max-age=300")
                .expectBody(String.class)
                .isEqualTo("WebFlux resource guide\n");
    }

    @Test
    void defaultBootWelcomePageServesHtml() {
        client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(
                        MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(html -> {
                    if (!html.contains("WebFlux CORS / Static / Cache")) {
                        throw new AssertionError("Missing welcome content");
                    }
                });
    }
}
~~~

### 17.1. Vì sao test 304 quan trọng?

Nếu chỉ kiểm GET thông thường, bạn chưa biết `If-None-Match` được Spring xử lý đúng. Test trên gửi cùng ETag từ lần trước và kỳ vọng:
- HTTP 304.
- Body trống.
- ETag vẫn nhất quán.
- CORS header có mặt cho frontend origin hợp lệ.

**Trong mạng có CDN**, kiểm lại qua CDN vì shared caches có thể sửa headers/cache key hoặc tự trả 304 mà không tới origin. Embedded server test chỉ chứng minh hành vi application.

### 17.2. Kiểm preflight khác kiểm actual POST

Preflight test có `OPTIONS`, `Origin`, `Access-Control-Request-Method` và `Access-Control-Request-Headers`. Actual POST test gửi JSON body và kiểm echo. Browser phải được phép ở **cả hai bước**, và security logic vẫn được chạy trên actual request theo kiến trúc.

### 17.3. Vì sao không assert từng byte headers khác?

Server/container có thể thêm Date, Content-Length, transfer-encoding hoặc thay thứ tự fields. Tests nên tập trung HTTP semantics:
- ACAO đúng origin.
- ETag quote format.
- Cache-Control chứa max-age/no-store phù hợp.
- 304 không có body.
- GET static file thành công.
- Không cho phép origin ngoài allowlist.
- HTML content và content type.

Một vài header như `Vary` có thể gồm nhiều values; khi muốn kiểm `Origin`, dùng match contains thay vì giả định chỉ một dòng và thứ tự cố định.

### 17.4. Chạy dự án

Ở folder project có Gradle wrapper hợp lệ:

~~~bash
./gradlew test
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat test
.\gradlew.bat bootRun
~~~

Nếu project chưa có Gradle wrapper, dùng bản Gradle đang cài để tạo wrapper phù hợp; đừng nghĩ các file `settings.gradle` và `build.gradle` tự sinh `gradlew`. Demo trình bày đầy đủ source/build, không bao gồm wrapper binary/scripts.

## 18. Thử bằng curl và browser

### 18.1. Request CORS thành công

~~~bash
curl -i 'http://localhost:8080/api/public/catalog' \
  -H 'Origin: https://app.example.test'
~~~

Dự kiến HTTP 200 cùng `Access-Control-Allow-Origin: https://app.example.test`, Cache-Control `max-age=60`, ETag `"catalog-2026-a"`.

### 18.2. Revalidate catalog

~~~bash
curl -i 'http://localhost:8080/api/public/catalog' \
  -H 'Origin: https://app.example.test' \
  -H 'If-None-Match: "catalog-2026-a"'
~~~

Dự kiến HTTP 304 và body rỗng. `curl` không tự quản lý HTTP cache tương tự browser thông thường, nên khi trực tiếp gọi 304, đừng mong nó in lại JSON body đã nhận từ lần trước.

### 18.3. CORS preflight

~~~bash
curl -i -X OPTIONS \
  'http://localhost:8080/api/public/echo' \
  -H 'Origin: https://app.example.test' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-client-trace'
~~~

Dự kiến 200 với CORS preflight approval headers. Thử origin không được phép:

~~~bash
curl -i -X OPTIONS \
  'http://localhost:8080/api/public/echo' \
  -H 'Origin: https://evil.example.test' \
  -H 'Access-Control-Request-Method: POST'
~~~

Server WebFlux CORS processor dự kiến từ chối bằng 403. Nếu đặt thêm Spring Security hoặc gateway, có thể có thứ tự/layer error khác, cần test deployment thực.

### 18.4. Actual POST

~~~bash
curl -i -X POST \
  'http://localhost:8080/api/public/echo' \
  -H 'Origin: https://app.example.test' \
  -H 'Content-Type: application/json' \
  -H 'X-Client-Trace: sample' \
  -d '{"name":"An"}'
~~~

Dự kiến HTTP 200:

~~~json
{"greeting":"Hello, An"}
~~~

và `Cache-Control: no-store`. Lưu ý browser sẽ preflight cho request này trong ngữ cảnh cross-origin; curl không tự gửi OPTIONS trước.

### 18.5. Static files

~~~bash
curl -i 'http://localhost:8080/docs/guide.txt'
curl -i 'http://localhost:8080/'
~~~

`/docs/guide.txt` trả nội dung text file với max-age 300. `/` trả HTML từ static index.html. Không cần viết controller thủ công chỉ để phục vụ hai files này.

## 19. Debugging CORS bằng Chrome/Firefox DevTools

Khi browser báo “blocked by CORS policy”, hãy lần lượt:
1. Xác định **Origin thật** của frontend (scheme/host/port), không dùng URL backend thay.
2. Xem Network: browser có gửi OPTIONS preflight không?
3. Nếu có OPTIONS, kiểm HTTP status và `Access-Control-Allow-Origin/Methods/Headers/Max-Age`.
4. Kiểm CORS headers trên **actual response**, kể cả 304/401/403/500/error paths khi relevant.
5. Kiểm cookies có gửi không, `credentials` mode, `SameSite`, `Secure`, third-party cookie policy.
6. Với Spring Security, xác nhận CORS xử lý trước authentication/authorization.
7. Kiểm gateway/CDN có strip `Origin` hoặc response ACAO không.
8. Nếu response cacheable, kiểm `Vary: Origin`/cache key.
9. Kiểm CSP `connect-src` của frontend và TLS/mixed-content: chúng có thể chặn request độc lập CORS.
10. So sánh browser với curl OPTIONS/actual requests có đầy đủ headers tương ứng.

**CORS errors đôi khi che lỗi thực sự** như 401, 502 hoặc reverse proxy trả HTML error không có ACAO. Đừng chỉ thêm `Access-Control-Allow-Origin: *` toàn cục; hãy sửa upstream error path hoặc auth/CORS integration.

## 20. HTTP caching troubleshooting

| Triệu chứng | Nguyên nhân hay gặp | Hướng sửa |
| --- | --- | --- |
| Browser luôn tải lại JS dù unchanged | Không có freshness hoặc validator | Cache-Control + ETag/Last-Modified |
| JS mới deploy nhưng browser vẫn chạy bản cũ | URL không versioned, max-age quá dài | Content-hashed filenames hoặc freshness ngắn |
| HTML cũ trỏ tới JS đã bị xóa | Purge tài nguyên cũ quá sớm | Giữ assets cũ qua cửa sổ tương thích |
| 304 không có JSON body nên app báo parse error | HTTP client không có cache nhưng tự gửi If-None-Match | Không parse 304 như 200, dùng cache store đúng |
| Thay nội dung nhưng ETag không đổi | Validator gắn sai version/representation | Tính/đổi ETag cùng dữ liệu |
| Cùng URL hai ngôn ngữ bị cache nhầm | Thiếu `Vary: Accept-Language` hoặc CDN key sai | Correct Vary/custom cache key |
| CORS origin A thành origin B sau cache HIT | CDN bỏ qua `Vary: Origin` | Cấu hình Vary/cache key đúng |
| Private profile trả data user khác | `Cache-Control: public`/key không theo user | no-store/private + security review |
| GET public không được cache dù max-age | Request/response cache constraints, auth/cookie/CDN policy | Xem cache logs/headers và gateway rules |
| `no-cache` vẫn hiện trong browser storage | Hiểu nhầm no-cache | no-store nếu thực sự cấm lưu |
| 304 qua CDN nhưng ACAO mất | CDN/proxy rewrite headers | Test CORS 200/304 và preserve headers |
| Last-Modified sai lệch timestamp | File/JAR metadata hoặc server clocks | ETag phù hợp, kiểm resource source |
| Cached POST response không như mong đợi | Cache behavior của unsafe methods không tự như GET | Dùng HTTP semantics và policy cụ thể |
| Multiple Content-Encoding variants dùng sai ETag | Representation/validator không nhất quán | ETag/Vary theo bytes/semantics |
| Static resource handler không tìm file | Classpath location sai hoặc package resource thiếu | Kiểm JAR và addResourceLocations |
| `@EnableWebFlux` làm mất Boot behavior | Tự tiếp quản framework config | Bỏ annotation khi chỉ cần WebFluxConfigurer |
| Spring Boot chạy MVC thay vì WebFlux | Có cả starter-web và starter-webflux | Chọn đúng starter/application type |

### 20.1. `Vary` không giải quyết privacy tự thân

`Vary: Origin` tách variants theo origin. User Alice và Bob ở cùng một frontend origin, nên cache vẫn có thể trộn user-specific bodies nếu dùng một cache key chỉ gồm URI và Origin. Dữ liệu bí mật cần security + `private/no-store` đúng policy và kiểm CDN.

### 20.2. `Access-Control-Max-Age` không đảm bảo browser luôn cache đủ giây

Browser có thể cap, evict hoặc tách preflight cache theo network partition/security context. Không thiết kế hệ thống bắt buộc “OPTIONS chỉ một lần mỗi giờ”. Nếu proxy/edge muốn tối ưu preflight traffic, đo thực tế, đừng dựa vào giá trị cấu hình như một SLA.

## 21. Production checklist

- [ ] Biết frontend origins thực cho từng môi trường: dev/stage/prod, scheme/host/port.
- [ ] Chỉ allow origins cần thiết; không đặt credentials=true cùng wildcard origin.
- [ ] Allowed methods/headers/exposed headers là allowlist có mục đích, không mở tất cả mặc định.
- [ ] Preflight xử lý trước Security auth khi sử dụng credentials/Authorization.
- [ ] CORS không thay authentication, authorization, CSRF hay object ownership.
- [ ] Tránh trùng/chồng lấn `@CrossOrigin`, WebFluxConfigurer, CorsWebFilter và gateway.
- [ ] Kiểm actual + OPTIONS responses, cùng các 304/401/403/error paths.
- [ ] CDN/cache tôn trọng Origin/Vary và không serve private data cross-user.
- [ ] Có HTTP cache policy riêng cho API public, API private, HTML entrypoint và hashed assets.
- [ ] Content-hashed JS/CSS mới được cache max-age dài/immutable; giữ assets cũ khi deploy.
- [ ] Static resources nằm trong classpath đúng, resource handlers không expose private files.
- [ ] Không bật `@EnableWebFlux` nếu chỉ muốn mở rộng Boot defaults.
- [ ] Dùng `ResponseEntity.eTag`/`lastModified` nhất quán với representation thật.
- [ ] Kiểm If-None-Match/If-Modified-Since và behavior 304 body rỗng.
- [ ] 304 và cached response vẫn tôn trọng CORS + authorization.
- [ ] Không dùng no-cache đồng nghĩa no-store; chọn theo sensitivity.
- [ ] Không để raw private headers/cookies/tokens vào CDN logs hoặc cache metadata.
- [ ] Viết WebTestClient integration tests cho config và contract headers.
- [ ] Kiểm staging với gateway/CDN/browser thực, vì embedded tests không phản ánh mọi edge config.
- [ ] Giám sát CORS preflight failures, 304 ratio, cache HIT/MISS, stale responses và deployment cache busting.

### Câu hỏi tự kiểm tra

**1.** Origin trong CORS gồm những phần nào?

**Trả lời:** Scheme, host và port (kể cả cổng mặc định theo URL semantics). Path không là một phần của origin.

**2.** CORS preflight có phải request POST thật không?

**Trả lời:** Không. Browser gửi OPTIONS để hỏi server method/headers dự định có được chấp nhận cross-origin không, rồi mới gửi actual request khi hợp lệ.

**3.** Vì sao `Access-Control-Allow-Origin: *` không dùng cùng credentialed CORS?

**Trả lời:** Browser CORS rules không cho wildcard ACAO trong credentialed responses; phải dùng origin cụ thể được phép và `Access-Control-Allow-Credentials: true` khi phù hợp.

**4.** `Access-Control-Max-Age` có giống `Cache-Control: max-age` không?

**Trả lời:** Không. Header thứ nhất về browser preflight permission cache; thứ hai về freshness HTTP representations.

**5.** `Cache-Control: no-cache` có cấm lưu body không?

**Trả lời:** Không. Nó cho lưu nhưng phải revalidate trước reuse. `no-store` mới yêu cầu không lưu trong HTTP caches tuân thủ.

**6.** `ETag` đem lại điều gì khi có `If-None-Match`?

**Trả lời:** Client/cache revalidate; nếu representation chưa đổi thì server có thể trả 304 không có body, giúp tránh truyền lại payload.

**7.** Tại sao tài nguyên hashed có thể cache lâu hơn `index.html`?

**Trả lời:** URL chứa content hash đổi khi bytes đổi; browser có thể coi asset URL cũ immutable. Entrypoint index.html cần được cập nhật để trỏ tới phiên bản asset mới.

**8.** Dùng `@EnableWebFlux` trong mọi Boot WebFlux app có nên không?

**Trả lời:** Không. Khi chỉ muốn bổ sung config, dùng `@Configuration implements WebFluxConfigurer` không có `@EnableWebFlux` để giữ Boot auto-config.

**9.** CORS cho `/api/public/**` có tự áp dụng cho `/docs/**` không?

**Trả lời:** Không. Path mapping cần được định nghĩa cho docs/assets riêng nếu cross-origin browser JS phải truy cập.

**10.** Một GET authenticated có `Vary: Origin` đã an toàn cho CDN shared caching chưa?

**Trả lời:** Chưa. Origin không đại diện cho user/tenant; authorization và private/no-store caching policy phải độc lập.

**11.** Khi body đã được browser cache fresh, nó có phải gửi `If-None-Match` không?

**Trả lời:** Không nhất thiết. Fresh cache có thể được dùng trực tiếp. Khi cần revalidate, client có thể gửi conditional request.

## 22. Tài liệu chính thức và chuẩn HTTP

- [Spring Framework — WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html): `@CrossOrigin`, `CorsRegistry`, CorsWebFilter, credentials, origins.
- [Spring Framework — WebFlux Configuration](https://docs.spring.io/spring-framework/reference/web/webflux/config.html): `WebFluxConfigurer`, static resources, `ResourceHandlerRegistry`, resource chain.
- [Spring Framework — WebFlux HTTP Caching](https://docs.spring.io/spring-framework/reference/web/webflux/caching.html): `CacheControl`, `ResponseEntity.eTag`, `Last-Modified`, 304.
- [Spring Framework — WebFlux ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responseentity.html): status/headers/body sync và reactive combinations.
- [Spring Boot 3.3 — Reactive Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/reactive.html): auto configuration, static paths, web application type.
- [Spring Security — Reactive CORS Integration](https://docs.spring.io/spring-security/reference/reactive/integrations/cors.html): preflight trước security, CorsWebFilter và security integration.
- [Spring Framework — ResourceHandlerRegistration Javadoc 6.1](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/reactive/config/ResourceHandlerRegistration.html): add locations, cache control và resource chain.
- [Spring Framework — CacheControl Javadoc 6.1](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/http/CacheControl.html): maxAge/noStore/noCache/cachePublic/cachePrivate.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html): conditional requests, validators, 304, 412, ETag.
- [RFC 9111 — HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html): Cache-Control, shared/private caches, Vary, freshness, revalidation.
- [MDN — Cross-Origin Resource Sharing](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS): preflight, ACAO, credentials và browser behavior.
- [MDN — HTTP Caching](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Caching): fresh/stale, validators, cache busting.
- [Spring Framework — WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): reactive HTTP integration tests.

**Ghi nhớ:** CORS quản lý **quyền đọc cross-origin trong browser**, static resource handlers quản lý **file-to-URL mapping**, HTTP caching quản lý **lưu/tái sử dụng/revalidate representation**. Chúng cùng xuất hiện trong Spring WebFlux nhưng giải quyết ba vấn đề độc lập. Hãy dùng cấu hình origins có kiểm soát, quản lý tài nguyên tĩnh qua WebFluxConfigurer, versioned assets khi cache dài và `ResponseEntity`/`CacheControl`/`ETag` cho API; kiểm chứng bằng WebTestClient cùng browser/CDN tests khi triển khai thật.


</details>

- [Quay lại đầu trang](#back-to-top)
