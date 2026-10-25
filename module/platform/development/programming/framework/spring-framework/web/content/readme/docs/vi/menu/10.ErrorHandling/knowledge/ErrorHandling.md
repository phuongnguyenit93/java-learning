<a id="back-to-top"></a>

# Xử lý lỗi

## Menu
- [Phân loại lỗi đầu vào, lỗi nghiệp vụ và lỗi hệ thống](#error-handling-001)
- [@ExceptionHandler ở controller và quy tắc chọn phương thức xử lý](#error-handling-002)
- [@ControllerAdvice, @RestControllerAdvice và phạm vi áp dụng](#error-handling-003)
- [HandlerExceptionResolver và ResponseEntityExceptionHandler](#error-handling-004)
- [ProblemDetail, ErrorResponse và định dạng lỗi theo RFC 9457](#error-handling-005)
- [Biểu diễn lỗi từng trường, thông tin mở rộng và mã lỗi ổn định](#error-handling-006)
- [Phân biệt ngoại lệ trong controller với lỗi ở Filter hoặc Servlet container](#error-handling-007)
- [Ẩn thông tin nhạy cảm, gắn mã đối chiếu và tránh trả stack trace cho người dùng](#error-handling-008)

## <a id="error-handling-001">Phân loại lỗi đầu vào, lỗi nghiệp vụ và lỗi hệ thống</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Phân loại lỗi đầu vào, lỗi nghiệp vụ và lỗi hệ thống

## 1. Mục tiêu

Một REST API tốt cần trả lỗi có ý nghĩa cho client mà vẫn giữ kín chi tiết nội bộ. Trong Spring MVC, cùng một HTTP 400 hay 500 có thể xuất phát từ những tầng xử lý khác nhau. **Phân loại lỗi theo nguyên nhân và trách nhiệm khắc phục** giúp chọn HTTP status, vị trí bắt exception, dữ liệu trả về, log và cơ chế retry phù hợp.

Sau bài này, bạn có thể:

- Phân biệt **lỗi đầu vào** (request không hợp lệ), **lỗi nghiệp vụ** (request hiểu được nhưng vi phạm quy tắc/trạng thái domain) và **lỗi hệ thống** (hạ tầng, lỗi lập trình, dependency không đáp ứng).
- Chọn HTTP status có cơ sở: 400, 401, 403, 404, 409, 415, 422, 429, 500, 502, 503, 504; tránh coi mọi lỗi là 400 hoặc 500.
- Xác định điểm phát sinh lỗi trong luồng Spring MVC và xử lý với `@RestControllerAdvice`, `@ExceptionHandler`, `ResponseEntity`, `ProblemDetail` hoặc `ResponseEntityExceptionHandler`.
- Thiết kế JSON error contract có `code` ổn định, `message` an toàn, danh sách lỗi field và thông tin tương quan để quan sát.
- Tách lỗi validation/binding khỏi lỗi domain/service, không làm rò dữ liệu nhạy cảm; đặt retry đúng chỗ và kiểm thử các tình huống.
- Xây dựng ví dụ Spring Boot 3.5.0, Spring Framework 6.2, Java 17+ có lỗi 400/404/409/422/503 và test MockMvc.

**Phạm vi:** Spring MVC Servlet stack. Lỗi bảo mật do Spring Security, sự cố mạng tại gateway và Spring WebFlux có các điểm xử lý bổ sung, sẽ được phân biệt khi cần. Đây là tài liệu kỹ thuật về xử lý lỗi, không phải bộ mã trạng thái bắt buộc cho mọi API: hợp đồng HTTP phải khớp ngữ nghĩa tài nguyên và nghiệp vụ thực tế.

## 2. Ba nhóm lỗi: nhận diện bằng nguyên nhân

| Nhóm | Bản chất | Ví dụ cụ thể | Ai thường khắc phục? | HTTP thường gặp |
| --- | --- | --- | --- | --- |
| **Lỗi đầu vào** | HTTP request sai cú pháp, định dạng, kiểu dữ liệu hoặc ràng buộc của API | JSON hỏng, query `page=abc`, body thiếu field bắt buộc, số lượng âm, sai Content-Type | Client cần sửa request | 400, 415, đôi khi 422 |
| **Lỗi nghiệp vụ** | Request đọc được, nhưng hành động không được phép theo quy tắc/trạng thái domain, hoặc resource không tồn tại | Hủy đơn đã hủy, vượt hạn mức đơn hàng, mã coupon hết hiệu lực, id không tồn tại | Client điều chỉnh hành động/dữ liệu hoặc đợi trạng thái thay đổi | 404, 409, 422 (tùy tình huống) |
| **Lỗi hệ thống** | Dịch vụ không thể thực hiện vì lỗi bất ngờ hoặc hạ tầng/downstream | DB không kết nối được, timeout service khác, lỗi lập trình, cạn tài nguyên | Server/operations; client có thể retry có điều kiện | 500, 502, 503, 504 |

**Hai nhận xét quan trọng:**

1. **HTTP 4xx không đồng nghĩa tất cả đều là "validation error".** 401/403 là authentication/authorization; 404 là resource không tìm thấy; 409 là trạng thái xung đột; 415 là media type.
2. **Lỗi domain không tự động là 422.** Nếu hai thao tác cùng sửa một resource tạo xung đột trạng thái, 409 thường diễn tả tốt hơn. Nếu resource không tồn tại, 404 hợp lý. Một rule không thể đáp ứng dù request có cú pháp hợp lệ có thể chọn 422. Hãy quy định ổn định theo contract.

Ví dụ luồng đặt hàng:

- `POST /orders` với `{"sku":"ABC","quantity":"abc"}` → không chuyển quantity thành số được → **đầu vào** (400).
- `POST /orders` với `{"sku":"ABC","quantity":0}`, trong DTO có `@Min(1)` → **đầu vào** (400).
- `POST /orders` với `{"sku":"ABC","quantity":11}`, khi quy tắc đơn tối đa 10 sản phẩm → **nghiệp vụ** (có thể 422).
- `POST /orders/15/cancel` nhưng đơn đã hủy → **xung đột trạng thái nghiệp vụ** (409).
- Không kết nối được database khi tạo đơn → **hệ thống** (500 hoặc 503 nếu dịch vụ tạm thời không sẵn sàng và có chính sách phù hợp).

Phân loại phải dựa trên quy tắc đã xác định, không suy luận tùy tiện từ exception class. Một `IllegalArgumentException` có thể đến từ bug hoặc kiểm tra dữ liệu có chủ ý; đừng tự động ánh xạ tất cả sang 400.

## 3. Điểm phát sinh lỗi trong Spring MVC

~~~text
Client
  |
  v
Servlet Filters / Security Filters
  |  authentication / authorization
  v
DispatcherServlet -> Handler Mapping
  |  404, 405, 415, 406 tùy route, method, media type
  v
Argument Resolution + HttpMessageConverter / Data Binding
  |  JSON parse, missing parameter, type mismatch
  v
Bean Validation (@Valid, constraints)
  |  input field errors / method validation
  v
Controller -> Service -> Domain
  |  resource missing, rule violation, state conflict
  v
Repository / Database / External Service
  |  timeout, unavailable, unexpected errors
  v
Return Value Handling + Response Serialization
  |  JSON serialization, content negotiation failures
  v
HTTP Response
~~~

Từ sơ đồ trên:

- Lỗi parse JSON có thể xảy ra **trước khi controller method được gọi**, nên `try/catch` bên trong handler không bắt được lỗi đó.
- `@RestControllerAdvice` xử lý nhiều exception từ Spring MVC handler invocation, nhưng không thay thế xử lý exception phát sinh trực tiếp trong Security filter chain.
- Không nên catch exception ở mọi layer rồi trả 200 với `{"success":false}`; như vậy phá nghĩa HTTP, metrics và nhiều HTTP client.
- Service không nên phụ thuộc chặt vào `ResponseEntity` hoặc mã status HTTP; service có thể ném **domain exception có ý nghĩa**, lớp web ánh xạ nó thành status.
- Lỗi hệ thống phát sinh trong quá trình **ghi response body** có thể xảy ra khi headers đã gửi, lúc đó server không bảo đảm còn đổi response thành JSON lỗi chuẩn được.

## 4. Lỗi đầu vào: phân tầng rõ ràng

### 4.1. Sai cú pháp HTTP/JSON và media type

Ví dụ request hỏng:

~~~http
POST /api/orders HTTP/1.1
Content-Type: application/json

{"sku":"JAVA-17","quantity":
~~~

Đây là JSON chưa hoàn chỉnh, Jackson không thể deserialize. Spring MVC thường ném `HttpMessageNotReadableException` và trả **400 Bad Request**.

Nếu endpoint khai báo `consumes="application/json"` nhưng client gửi `text/plain`, Spring có thể trả **415 Unsupported Media Type**. Nếu client yêu cầu response `Accept: application/xml` mà API chỉ có JSON, có thể là **406 Not Acceptable**. Không gộp 415 và 406 vào 400.

### 4.2. Sai kiểu dữ liệu

~~~java
@GetMapping("/api/orders/{id}")
public OrderDto find(@PathVariable long id) {
    return service.find(id);
}
~~~

Request `GET /api/orders/abc` khớp route có biến id, nhưng không thể chuyển `abc` sang `long`. Spring MVC thường dùng `MethodArgumentTypeMismatchException` trong tình huống này, dẫn đến HTTP 400. Đây là **lỗi binding đầu vào**, không phải "không tìm thấy đơn id abc".

Với `@RequestBody` JSON `{"quantity":"many"}` khi target là `Integer`, lỗi thường xuất hiện từ Jackson và được Spring bọc trong `HttpMessageNotReadableException`. Phân biệt nguồn lỗi dù cả hai đều có thể trả 400.

### 4.3. Validation sau khi bind thành công

DTO:

~~~java
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity) {
}
~~~

Handler:

~~~java
@PostMapping("/api/orders")
public OrderDto create(@Valid @RequestBody CreateOrderRequest request) {
    return service.create(request);
}
~~~

Import `jakarta.validation.Valid`. Đây là đoạn handler minh họa, project chạy độc lập nằm ở mục 10.

- `quantity=0`: JSON parse thành Integer thành công, nhưng không qua `@Min(1)`.
- `quantity=null` hoặc thiếu field: không qua `@NotNull`.
- `sku=""`: không qua `@NotBlank`.
- `quantity="abc"`: có thể thất bại ngay ở Jackson deserialization, trước khi tới Bean Validation.

Trong Spring MVC, `@Valid @RequestBody` thường phát sinh `MethodArgumentNotValidException` khi object validation thất bại. Khi validation áp dụng lên các method arguments khác (ví dụ constraint trực tiếp trên parameter), Spring MVC từ 6.1 có thể dùng `HandlerMethodValidationException`. Với method validation trong Spring MVC, cần thiết kế exception handling tính tới cả hai thay vì mặc định luôn bắt một class. Controller có class-level `@Validated` có thể kích hoạt đường AOP validation khác; xem tài liệu framework theo phiên bản.

### 4.4. Những lỗi validation thực tế cần tránh

- Dùng primitive `int` khi cần phân biệt "không gửi" và `0`; DTO có `Integer` + `@NotNull` biểu diễn rõ hơn.
- Chỉ dùng `@NotNull` cho String rồi tưởng từ chối `""`; sử dụng `@NotBlank` khi cần chữ không rỗng/không toàn khoảng trắng.
- Cho rằng Bean Validation thay thế kiểm tra nghiệp vụ hoặc phân quyền.
- Trả rejected values nguyên văn trong response error: có thể chứa token, thông tin cá nhân hoặc chuỗi quá dài.
- Dùng message validation làm code lỗi ổn định cho client; nội dung message có thể được nội địa hóa hoặc thay đổi.

## 5. Lỗi nghiệp vụ: diễn tả vi phạm domain một cách tường minh

Ví dụ các quy tắc ở service:

~~~java
public OrderDto cancel(long orderId) {
    OrderDto order = repository.find(orderId);
    if (order == null) {
        throw new OrderNotFoundException("Order not found");
    }
    if ("CANCELLED".equals(order.status())) {
        throw new OrderConflictException("Order is already cancelled");
    }
    return repository.cancel(orderId);
}
~~~

Đây là pseudocode Java trong ngữ cảnh service có `repository`; dự án đầy đủ ở mục 10 dùng memory store có thể chạy. Cách tách exception:

| Domain condition | Exception có ý nghĩa | HTTP mapping có thể dùng |
| --- | --- | --- |
| Không tìm thấy order id | `OrderNotFoundException` | 404 |
| Hủy order đã hủy | `OrderConflictException` | 409 |
| Số lượng vượt hạn mức business | `OrderRuleException` | 422 |
| Thao tác được phép nhưng actor không có quyền | Authorization failure | 403, tùy Spring Security |
| Resource bị thay đổi theo version và cập nhật cũ | Version/optimistic lock conflict | 409 hoặc precondition 412 nếu dùng `If-Match` |
| Duplicate unique key đã được xác định là business conflict | Duplicate/conflict exception | 409 |

**Ranh giới trách nhiệm:** service biết "đơn đã hủy" là trạng thái trái quy tắc; web layer biết cách biểu diễn thành HTTP 409. Domain không nên import `HttpStatus` chỉ để ném lỗi.

**Không mapping mọi `DataIntegrityViolationException` thành 409.** Database constraint error có thể biểu thị nhiều nguyên nhân khác nhau, bao gồm bug/data corruption. Chỉ quy đổi sang mã lỗi business sau khi xác định đúng constraint/ngữ cảnh, nếu không có thể che lỗi hệ thống.

### 5.1. 409 và 422 khác nhau thế nào?

- **409 Conflict:** yêu cầu xung đột với *trạng thái hiện tại* của tài nguyên, ví dụ cố hủy đơn đã hủy, tạo resource trùng định danh, cập nhật dựa trên version cũ.
- **422 Unprocessable Content:** server hiểu media type và cú pháp hợp lệ, nhưng không thể xử lý nội dung mang nghĩa không hợp lệ, ví dụ số lượng vượt quy tắc cụ thể. Không nhất thiết mọi rule đều phải là 422; API có thể chọn 400 cho một số business validations nếu contract giải thích rõ.
- **404 Not Found:** resource theo URL không tồn tại hoặc không được phép tiết lộ sự tồn tại trong một số chính sách truy cập.

Sự khác biệt quan trọng là **tính nhất quán của hợp đồng**, không phải việc dùng nhiều mã HTTP để trông phức tạp.

## 6. Lỗi hệ thống: 500, 502, 503, 504 và retry

Ví dụ hệ thống:

- Database driver ném lỗi kết nối bất ngờ: ứng dụng chưa thực hiện được thao tác → có thể trả 500 hoặc 503 theo nguyên nhân và chính sách "temporarily unavailable".
- Service downstream trả response bất thường làm gateway/proxy không nhận response hợp lệ: **502 Bad Gateway** thường phù hợp ở **gateway**.
- Gateway chờ upstream quá lâu: **504 Gateway Timeout** thường được phát ra bởi **gateway/proxy**.
- Application chủ động từ chối vì đang bảo trì hoặc thiếu tài nguyên tạm thời: **503 Service Unavailable** (có thể kèm `Retry-After` khi biết thời điểm thích hợp).
- Lỗi lập trình bất ngờ, NullPointerException, assumption sai: **500 Internal Server Error**.

Không gắn nhãn `503` cho mọi `RuntimeException`, cũng không tự chọn 504 cho bất kỳ `SocketTimeoutException` phát sinh trong service: vai trò của server trong chuỗi HTTP và ý nghĩa trạng thái phải được xác định.

### 6.1. Thông tin trả cho client

Response error hệ thống nên chứa mô tả tổng quát:

~~~json
{
  "code": "INTERNAL_ERROR",
  "message": "Đã xảy ra lỗi máy chủ. Vui lòng thử lại sau.",
  "status": 500,
  "path": "/api/orders"
}
~~~

Tránh đưa vào public response: stack trace, SQL statement, connection URL, mật khẩu, bearer token, class/package nội bộ, địa chỉ hạ tầng, thông tin PII hoặc nguyên văn `exception.getMessage()` từ dependency. Log nội bộ cần stacktrace và correlation/trace id để kỹ sư điều tra, nhưng **đừng log bí mật hay toàn bộ request body vô điều kiện**.

### 6.2. Retry đúng loại lỗi

- 400/422 do input hoặc domain rule cố định: thường **không retry y nguyên**.
- 401: refresh/login lại nếu phù hợp; 403: không tự retry để "thử quyền" vô hạn.
- 409 do optimistic lock: có thể đọc version mới và thử lại với cập nhật đã được kiểm soát; không blind retry lệnh nguy hiểm.
- 429: tuân theo `Retry-After` và rate limit policy nếu có.
- 503/504 do lỗi tạm thời: có thể retry **bounded exponential backoff + jitter**, timeout, circuit breaker.
- Với POST không idempotent, retry sau timeout có thể tạo **đơn trùng**. Dùng **idempotency key**, deduplication hoặc điều kiện bảo đảm an toàn trước khi tự động retry.

Retry phải có giới hạn, quan sát được và tôn trọng deadline; tránh "retry storm" khiến hệ thống đang lỗi càng quá tải.

## 7. Chuẩn hóa JSON error response

API có thể dùng **custom DTO** hoặc chuẩn **RFC 9457 Problem Details**. Không buộc phải trả cả hai trong cùng endpoint.

### 7.1. Custom API error DTO

Một dạng rõ ràng:

~~~json
{
  "code": "INVALID_INPUT",
  "message": "Dữ liệu yêu cầu không hợp lệ",
  "status": 400,
  "path": "/api/orders",
  "violations": [
    {"field": "sku", "reason": "NotBlank"},
    {"field": "quantity", "reason": "Min"}
  ]
}
~~~

Các field có vai trò:

- `code`: mã máy đọc ổn định như `INVALID_INPUT`, `ORDER_CONFLICT`, `DEPENDENCY_UNAVAILABLE`. Client nên phụ thuộc vào code, không parse tiếng Việt trong message.
- `message`: thông điệp an toàn, dễ hiểu; có thể được localize, nên không phải định danh máy.
- `status`: số HTTP status để dễ quan sát; phải **khớp status HTTP thực**.
- `path`: URL path, tránh nhúng query chứa token hoặc dữ liệu nhạy cảm.
- `violations`: danh sách tên field và mã constraint; không bắt buộc trả raw rejected value.

Trong production thường thêm `requestId`/`traceId` có thể tìm trong log hoặc distributed tracing, thời gian ISO-8601 và tài liệu về error codes. Chỉ tin request id client cung cấp nếu đã được xác thực/giới hạn/chuẩn hóa; thường hệ thống cần tạo hoặc kiểm soát correlation id ở gateway/filter.

### 7.2. ProblemDetail theo RFC 9457

Spring Framework 6 hỗ trợ `ProblemDetail` / `ErrorResponse`. Ví dụ:

~~~java
@ExceptionHandler(OrderConflictException.class)
public ProblemDetail handleConflict(OrderConflictException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    pd.setTitle("Order state conflict");
    pd.setDetail("Đơn hàng không thể hủy trong trạng thái hiện tại");
    pd.setProperty("code", "ORDER_CONFLICT");
    return pd;
}
~~~

Đoạn này minh họa method trong `@RestControllerAdvice`, cần import `org.springframework.http.ProblemDetail`, `org.springframework.http.HttpStatus` và exception tương ứng. Spring có thể điền `instance` theo request URL khi chưa được set; response thường dùng `application/problem+json`. Hình dạng thường gặp:

~~~json
{
  "type": "about:blank",
  "title": "Order state conflict",
  "status": 409,
  "detail": "Đơn hàng không thể hủy trong trạng thái hiện tại",
  "instance": "/api/orders/1/cancel",
  "code": "ORDER_CONFLICT"
}
~~~

Nếu muốn xử lý nhiều Spring MVC built-in exceptions thống nhất theo Problem Details, xem `ResponseEntityExceptionHandler` hoặc cấu hình lỗi chuẩn do framework cung cấp. **Không đơn giản kéo `Exception.class` về 500 rồi vô tình che mất 400/405/406/415**. Custom DTO và ProblemDetail đều chấp nhận được nếu API contract chọn rõ một cách và kiểm thử thực tế.

## 8. @RestControllerAdvice và phạm vi @ExceptionHandler

### 8.1. Chọn đúng exception và HTTP status

~~~java
@RestControllerAdvice
public class OrderAdvice {
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> notFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ORDER_NOT_FOUND", "Không tìm thấy đơn"));
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<ApiError> conflict(OrderConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ORDER_CONFLICT", "Đơn đã hủy"));
    }
}
~~~

Đây là ví dụ rút gọn về mapping (cần class `ApiError`, exception và imports phù hợp; project hoàn chỉnh ở mục 10). Spring chọn handler theo exception type; annotation `@RestControllerAdvice` có hiệu lực cho nhiều controllers trong scope áp dụng. Khi exception được bắt tại controller và xử lý hoàn toàn, nó không tự đi qua advice nữa.

Các lỗi do framework dựng ra thường có status riêng. Nếu kế thừa `ResponseEntityExceptionHandler`, dùng các protected hook thích hợp để override cách trả body thay vì khai báo `@ExceptionHandler` mới trùng với mappings đã được class cơ sở xử lý, vì có thể gây ambiguous exception handler mapping.

### 8.2. Không nuốt exception thành thành công giả

Sai:

~~~java
try {
    service.chargeOrder(order);
} catch (Exception ex) {
    return ResponseEntity.ok(Map.of("success", false));
}
~~~

Nếu lỗi hệ thống, response **HTTP 200** đánh lừa monitoring và client. Ngoài ra, catch mọi exception có thể che bug, làm mất stacktrace hoặc để transaction đi tiếp sai trạng thái.

Nên để lỗi domain có ý nghĩa đi lên advice; lỗi không mong đợi được log/quan sát và xử lý bằng fallback phù hợp. Khi bắt exception để **bổ sung ngữ cảnh**, giữ lại cause:

~~~java
try {
    paymentGateway.charge(order);
} catch (GatewayTimeoutException ex) {
    throw new PaymentDependencyException(
            "Payment provider temporarily unavailable", ex);
}
~~~

Đoạn minh họa giả định các class/service; không phải code độc lập. Không phân loại lỗi bằng so khớp chuỗi `ex.getMessage()` vì message có thể thay đổi theo phiên bản hoặc ngôn ngữ.

### 8.3. Ranh giới Spring Security

Khi có Spring Security, Authentication và AccessDenied exceptions thường được xử lý bởi **security filter chain** thông qua `AuthenticationEntryPoint` và `AccessDeniedHandler`. Việc dùng `@RestControllerAdvice` không bảo đảm bắt được lỗi xảy ra **trước DispatcherServlet**. Trong REST API có security, hãy chuẩn hóa response 401/403 qua cấu hình Security tương ứng.

- **401 Unauthorized**: request chưa có thông tin xác thực hợp lệ; chuẩn thực tế thường cần `WWW-Authenticate` tùy scheme.
- **403 Forbidden**: đã xác định quyền không đủ hoặc bị cấm.
- **404** đôi khi được dùng có chủ ý để tránh tiết lộ sự tồn tại resource nhưng phải có policy nhất quán.

## 9. Phân tầng exception và transaction

Một kiến trúc dễ duy trì:

~~~text
Controller / Web
  - @RequestBody, @Valid, parameter binding
  - HTTP mapping, @RestControllerAdvice
      |
      v
Application Service
  - authorization + orchestration
  - domain exceptions (NotFound, Conflict, RuleViolation)
      |
      v
Domain Model
  - invariants, state transitions
      |
      v
Repository / External Adapter
  - translate known infrastructure failures if needed
  - propagate unexpected exceptions with causal context
~~~

Không cho domain model phải biết `HttpStatus.CONFLICT`. Domain ném `OrderAlreadyCancelledException`; web mapping thành 409. Lỗi adapter đi lên dưới dạng exception có ngữ nghĩa hoặc nguyên nhân gốc được bảo toàn.

Với transaction Spring, **unchecked exceptions** mặc định khiến `@Transactional` rollback; checked exceptions có quy tắc rollback khác trừ khi cấu hình. Đừng bắt một exception nghiêm trọng bên trong service rồi tiếp tục trả thành công, vì có thể khiến transaction commit dữ liệu nửa vời. Một business rejection có thể không cần ghi gì và vẫn rollback; một conflict đã được xử lý an toàn thì response phù hợp tùy nghiệp vụ.

Với concurrent updates, nghiệp vụ có thể cần optimistic locking, DB unique constraint và tính atomic; kiểm tra "đơn đang NEW" trước rồi update hai bước không đồng nghĩa không có race condition. Ví dụ in-memory ở mục 10 dùng `ConcurrentHashMap.compute` để hủy nguyên tử ở phạm vi map.

## 10. Dự án chạy được: phân loại và trả lỗi qua HTTP

**Yêu cầu:** Java 17+, Maven. Ví dụ dùng Spring Boot 3.5.0, Spring MVC và Bean Validation. Dữ liệu lưu trong RAM, seed đơn số 1 để thử 404/409; có endpoint mô phỏng 503 cho mục đích học. Bài thực hành độc lập, không yêu cầu database.

Cấu trúc:

~~~text
error-demo/
├── pom.xml
└── src/
    ├── main/java/demo/ErrorHandlingApplication.java
    └── test/java/demo/OrderApiTest.java
~~~

### 10.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>error-demo</artifactId>
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
~~~

### 10.2. ErrorHandlingApplication.java

Toàn bộ mã dưới đây thuộc **một file** `src/main/java/demo/ErrorHandlingApplication.java`: một public class trùng tên file, các record/class khác package-private. Những endpoint đã chỉ rõ JSON để dễ thử bằng curl.

~~~java
package demo;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Service;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@SpringBootApplication
public class ErrorHandlingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ErrorHandlingApplication.class, args);
    }
}

record CreateOrderRequest(
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity) {
}

record OrderDto(long id, String sku, int quantity, String status) {
}

record FieldViolation(String field, String reason) {
}

record ApiFailure(
        String code,
        String message,
        int status,
        String path,
        List<FieldViolation> violations) {
}

class OrderMissingException extends RuntimeException {
    OrderMissingException() { super("Order not found"); }
}

class OrderConflictException extends RuntimeException {
    OrderConflictException() { super("Order is already cancelled"); }
}

class OrderRuleException extends RuntimeException {
    OrderRuleException() { super("Quantity exceeds business limit"); }
}

class DependencyUnavailableException extends RuntimeException {
    DependencyUnavailableException() { super("Dependency unavailable"); }
}

@Service
class OrderService {
    private final Map<Long, OrderDto> store = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);

    OrderService() {
        store.put(1L, new OrderDto(1L, "JAVA-17", 2, "NEW"));
    }

    OrderDto find(long id) {
        OrderDto found = store.get(id);
        if (found == null) {
            throw new OrderMissingException();
        }
        return found;
    }

    OrderDto create(CreateOrderRequest request) {
        // Validation đơn giản đã xảy ra ở controller.
        // Rule max 10 là quy tắc nghiệp vụ tại service.
        if (request.quantity() > 10) {
            throw new OrderRuleException();
        }
        long id = nextId.incrementAndGet();
        OrderDto created = new OrderDto(
                id,
                request.sku().trim().toUpperCase(Locale.ROOT),
                request.quantity(),
                "NEW");
        store.put(id, created);
        return created;
    }

    OrderDto cancel(long id) {
        // compute thao tác atomically cho một key, không read-then-write.
        return store.compute(id, (key, old) -> {
            if (old == null) {
                throw new OrderMissingException();
            }
            if ("CANCELLED".equals(old.status())) {
                throw new OrderConflictException();
            }
            return new OrderDto(
                    old.id(), old.sku(), old.quantity(), "CANCELLED");
        });
    }
}

@RestController
@RequestMapping(path = "/api/orders",
                produces = MediaType.APPLICATION_JSON_VALUE)
class OrderController {
    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    OrderDto one(@PathVariable long id) {
        return service.find(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OrderDto> create(
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @PostMapping("/{id}/cancel")
    OrderDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    // Chỉ mô phỏng lỗi tạm thời để học/kiểm thử.
    @GetMapping("/dependency-demo")
    OrderDto dependencyDemo() {
        throw new DependencyUnavailableException();
    }
}

@RestControllerAdvice
class ApiErrorAdvice {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiFailure> invalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                "Không đọc được JSON của yêu cầu",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiFailure> invalidParameter(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Tham số đường dẫn hoặc query không đúng kiểu",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiFailure> invalidBody(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldViolation> fields =
                ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldViolation(
                            error.getField(),
                            error.getCode() == null
                                    ? "Invalid"
                                    : error.getCode()))
                    .toList();
        return fail(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "Dữ liệu yêu cầu không hợp lệ",
                request, fields);
    }

    @ExceptionHandler(OrderMissingException.class)
    ResponseEntity<ApiFailure> missing(
            OrderMissingException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                "Không tìm thấy đơn hàng", request, List.of());
    }

    @ExceptionHandler(OrderConflictException.class)
    ResponseEntity<ApiFailure> conflict(
            OrderConflictException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.CONFLICT, "ORDER_CONFLICT",
                "Đơn hàng đã hủy", request, List.of());
    }

    @ExceptionHandler(OrderRuleException.class)
    ResponseEntity<ApiFailure> rule(
            OrderRuleException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.UNPROCESSABLE_ENTITY, "ORDER_RULE",
                "Mỗi đơn được tối đa 10 sản phẩm",
                request, List.of());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<ApiFailure> dependency(
            DependencyUnavailableException ex,
            HttpServletRequest request) {
        return fail(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "Dịch vụ tạm thời không khả dụng",
                request, List.of());
    }

    private ResponseEntity<ApiFailure> fail(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        return ResponseEntity.status(status)
                .body(new ApiFailure(code, message, status.value(),
                        request.getRequestURI(), violations));
    }
}
~~~

**Giải thích những quyết định trong dự án:**

- `@Valid @RequestBody` tách Bean Validation ra khỏi logic domain; lỗi parse JSON/type có handler riêng.
- `OrderService` ném domain exceptions, không import `HttpStatus`; advice chịu trách nhiệm ánh xạ 404/409/422.
- Mẫu `ApiFailure` có code ổn định, message an toàn và path; không trả exception message thô hoặc stacktrace.
- Chỉ giả lập `DependencyUnavailableException` cho 503. Trong ứng dụng thật, lỗi dependency phải được phát hiện và phân loại từ adapter/circuit breaker theo contract, không tạo endpoint lỗi cố ý.
- Chưa khai báo catch-all `Exception.class` để tránh can thiệp vô tình vào status của lỗi MVC mặc định như 405/406/415. Khi cần response 500 chung, cấu hình error handling ở mức phù hợp và **giữ nguyên** nghĩa của framework exceptions.
- `List<FieldViolation>` có thể có nhiều lỗi với cùng một field; không mặc định thứ tự lỗi là hợp đồng cho client.
- Không có database hoặc xác thực trong demo. Production cần transaction, data integrity và Spring Security theo yêu cầu.

### 10.3. Chạy và thử từng nhóm lỗi

Từ thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Một số lời gọi với curl (ví dụ dùng Bash):

**Thành công:**

~~~bash
curl -i 'http://localhost:8080/api/orders/1'
~~~

HTTP 200, JSON tương đương:

~~~json
{"id":1,"sku":"JAVA-17","quantity":2,"status":"NEW"}
~~~

**Lỗi đầu vào — id sai kiểu:**

~~~bash
curl -i 'http://localhost:8080/api/orders/abc'
~~~

HTTP 400, body tương đương:

~~~json
{
  "code":"INVALID_PARAMETER",
  "message":"Tham số đường dẫn hoặc query không đúng kiểu",
  "status":400,
  "path":"/api/orders/abc",
  "violations":[]
}
~~~

**Lỗi đầu vào — body thiếu/sai constraint:**

~~~bash
curl -i -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  --data '{"sku":"","quantity":0}'
~~~

HTTP 400, `code=INVALID_INPUT`; `violations` thường chứa `sku/NotBlank` và `quantity/Min`. Thứ tự field lỗi không bảo đảm.

**Lỗi đầu vào — JSON sai cú pháp:**

~~~bash
curl -i -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  --data '{"sku":"JAVA-17","quantity":'
~~~

HTTP 400, `code=INVALID_JSON`.

**Lỗi nghiệp vụ — rule vượt hạn mức:**

~~~bash
curl -i -X POST 'http://localhost:8080/api/orders' \
  -H 'Content-Type: application/json' \
  --data '{"sku":"JAVA-17","quantity":11}'
~~~

HTTP 422, `code=ORDER_RULE`, vì request hợp lệ theo DTO nhưng vượt business limit.

**Lỗi nghiệp vụ — không tìm thấy đơn:**

~~~bash
curl -i 'http://localhost:8080/api/orders/999'
~~~

HTTP 404, `code=ORDER_NOT_FOUND`.

**Lỗi nghiệp vụ — state conflict:**

~~~bash
curl -i -X POST 'http://localhost:8080/api/orders/1/cancel'
curl -i -X POST 'http://localhost:8080/api/orders/1/cancel'
~~~

Lần đầu HTTP 200 với `status=CANCELLED`; lần hai HTTP 409 với `code=ORDER_CONFLICT`.

**Lỗi hệ thống mô phỏng — dependency unavailable:**

~~~bash
curl -i 'http://localhost:8080/api/orders/dependency-demo'
~~~

HTTP 503 với `code=DEPENDENCY_UNAVAILABLE`. Trong ví dụ, lỗi này được phát ra có chủ đích; service thật có thể cân nhắc `Retry-After` và circuit breaker theo điều kiện.

Nếu chạy trong PowerShell, nên dùng `curl.exe` thay vì alias `curl`; dấu `\` nối dòng dùng cho shell Unix, có thể viết lệnh trên một dòng để tránh khác biệt shell.

### 10.4. Test MockMvc cho HTTP status và error code

Tạo file `src/test/java/demo/OrderApiTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void invalidPathVariableIsBadRequest() throws Exception {
        mvc.perform(get("/api/orders/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void validationErrorIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"","quantity":0}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.violations.length()").value(2));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"JAVA-17\",\"quantity\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void quantityOverBusinessLimitIs422() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"JAVA-17","quantity":11}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ORDER_RULE"));
    }

    @Test
    void missingResourceIs404() throws Exception {
        mvc.perform(get("/api/orders/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void repeatedCancelIsConflict() throws Exception {
        mvc.perform(post("/api/orders/1/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/orders/1/cancel"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }

    @Test
    void temporaryDependencyFailureIs503() throws Exception {
        mvc.perform(get("/api/orders/dependency-demo"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Test đo **kết quả HTTP thực tế** thay vì chỉ so sánh mã trong phương thức handler. Dùng `$.violations.length()` để kiểm tra số lỗi, không phụ thuộc thứ tự. Dữ liệu demo lưu RAM trong một application context, test hủy id 1 chỉ chạy một lần trong suite này; nếu viết nhiều test thay đổi cùng resource thì nên tách dữ liệu/reset fixture để các test độc lập.

## 11. Hướng production: giữ ranh giới và quan sát lỗi

### 11.1. Dùng ResponseEntityExceptionHandler khi muốn thống nhất lỗi framework

Spring MVC cung cấp `ResponseEntityExceptionHandler` để xử lý một nhóm lỗi có sẵn theo `ErrorResponse`/`ProblemDetail`. Ví dụ khung:

~~~java
@RestControllerAdvice
class GlobalProblemAdvice extends ResponseEntityExceptionHandler {
    @ExceptionHandler(OrderConflictException.class)
    public ProblemDetail conflict(OrderConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setTitle("Order conflict");
        pd.setDetail("Đơn hàng đang ở trạng thái không cho phép");
        pd.setProperty("code", "ORDER_CONFLICT");
        return pd;
    }
}
~~~

Đoạn này là minh họa **phương án thay thế** cho `ApiErrorAdvice` ở mục 10, không đặt cả hai advice cạnh tranh trong cùng ứng dụng. Cần imports `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler`, `RestControllerAdvice`, `ExceptionHandler`, `ProblemDetail`, `HttpStatus` và domain exception. Khi tùy biến built-in exceptions, ưu tiên override các protected handler methods đúng chữ ký trong phiên bản Spring sử dụng; tránh tạo hai mappings @ExceptionHandler cho cùng exception.

### 11.2. 500 chung không nên che status sẵn có

Một catch-all `@ExceptionHandler(Exception.class)` có thể hữu ích để tạo body 500 và log stacktrace, nhưng cũng có thể chặn các exception mà framework vốn sẽ map thành 400, 404, 405, 406, 415. Nếu cần fallback toàn cục, thiết kế rõ phạm vi và xử lý/ủy quyền cho `ErrorResponse` của Spring trước, hoặc dùng `ResponseEntityExceptionHandler` để kế thừa xử lý mặc định. Không ghi toàn bộ exception message vào client.

### 11.3. Logging, metrics và tracing

Đề xuất thông tin tối thiểu có cấu trúc cho server logs/metrics:

| Trường | Ý nghĩa |
| --- | --- |
| `requestId / traceId` | Gắn response lỗi với log/traces |
| `route / method` | Phân biệt endpoint, tránh high-cardinality từ raw path chứa id |
| `http.status` | Quan sát tỷ lệ lỗi theo 4xx/5xx |
| `error.code` | Gom nhóm lỗi ổn định theo hợp đồng |
| `exception.class` | Phục vụ chẩn đoán server, không đưa vào public JSON |
| `durationMs` | Nhận diện timeout/chậm |
| `downstream.name` | Xác định dependency liên quan |

4xx thường phản ánh request/client/domain state; 5xx thường cần alert server. Tuy nhiên tăng 409 do bug concurrency hay 401 đột biến có thể cũng là dấu hiệu cần điều tra. Tránh log mật khẩu, token, cookie hoặc request/response body chứa dữ liệu cá nhân; masking/redaction theo policy.

### 11.4. Trạng thái HTTP và tính an toàn của retry

Quy tắc thực hành:

- Chỉ trả **200/201** khi thao tác thực sự thành công theo contract.
- Trả **4xx** khi request không đáp ứng điều kiện client cần sửa hoặc một trạng thái domain ngăn việc thực hiện.
- Trả **5xx** khi server/upstream không hoàn tất yêu cầu.
- Với 503 tạm thời, có thể thêm `Retry-After` hợp lệ nếu biết khoảng chờ hợp lý.
- Với thao tác ghi có thể được gửi lại, dùng idempotency key hoặc optimistic concurrency để giảm rủi ro trùng.
- Với ứng dụng async, có thể trả **202 Accepted** nếu công việc mới chỉ được nhận vào hàng đợi; cần mô tả cách client kiểm tra kết quả và trạng thái lỗi về sau. Không coi 202 là "đã hoàn tất".

## 12. Bài tập tự kiểm tra

**Bài 1 — Phân nhóm:** Với mỗi tình huống, chọn lỗi đầu vào/nghiệp vụ/hệ thống và HTTP status phù hợp: `page=abc`, JSON thiếu dấu ngoặc, đơn đã hủy, database mất kết nối, `Accept: application/xml` nhưng API chỉ trả JSON.

**Bài 2 — Thiết kế contract:** Đề xuất response `ApiFailure` cho `INVALID_INPUT` gồm hai field errors. Lý giải tại sao không đưa rejected password/token value vào JSON.

**Bài 3 — Domain exception:** Tạo `OrderAlreadyPaidException` tại service và ánh xạ 409 tại advice; thay đổi business rule nếu đã thanh toán thì không được hủy.

**Bài 4 — Framework exceptions:** So sánh `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `HttpMessageNotReadableException` và `MethodArgumentTypeMismatchException`. Lỗi nào xảy ra trước khi method controller chạy?

**Bài 5 — Hệ thống:** Với timeout payment gateway sau khi request thanh toán đã gửi đi, giải thích vì sao client retry POST ngay lập tức có thể gây tính tiền hai lần; đề xuất idempotency key và tracing.

**Bài 6 — Test:** Mở rộng suite kiểm tra HTTP 415 với `Content-Type: text/plain`, 404 resource, 409 cancel trùng và 503 dependency; không so sánh thứ tự field JSON object.

**Bài 7 — Spring Security:** Giải thích tại sao `@RestControllerAdvice` không bảo đảm bắt được authentication failure từ filter chain, và xác định nơi nên cấu hình body lỗi 401/403.

## 13. Ghi nhớ

- **Đầu vào:** request sai cú pháp/kiểu/constraint → sửa request; Spring có thể phát sinh lỗi trước handler, thường dùng HTTP 400.
- **Nghiệp vụ:** yêu cầu hiểu được nhưng xung đột trạng thái hoặc vi phạm rule → domain exception có ý nghĩa; web mapping 404/409/422 theo contract.
- **Hệ thống:** sự cố server/dependency → 5xx thích hợp, log và trace nội bộ, trả client message an toàn; retry phải có điều kiện và bảo vệ tính idempotent.
- **Spring MVC:** `@RestControllerAdvice`, `@ExceptionHandler`, `ResponseEntity`, `ProblemDetail` và `ResponseEntityExceptionHandler` là các công cụ thiết kế error boundary; không "bắt tất cả thành 500" mà đánh mất trạng thái framework vốn có.
- API lỗi tốt có **HTTP status đúng + stable error code + safe message + context quan sát**, được kiểm thử bằng request thực.

## 14. Tài liệu chính thức

- [Spring Framework 6.2 — Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html): `ErrorResponse`, `ProblemDetail`, `ResponseEntityExceptionHandler`.
- [Spring Framework 6.2 — Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html): `MethodArgumentNotValidException` và `HandlerMethodValidationException`.
- [Spring Framework 6.2 — @ExceptionHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-exceptionhandler.html): ngoại lệ ở controller/advice.
- [Spring Framework 6.2 — @ControllerAdvice](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-advice.html): chia sẻ cấu hình cho nhiều controllers.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): ý nghĩa 4xx/5xx và status codes.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457): error object chuẩn machine-readable.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-002">@ExceptionHandler ở controller và quy tắc chọn phương thức xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# @ExceptionHandler ở controller và quy tắc chọn phương thức xử lý trong Spring MVC

> **Phạm vi:** Spring Web MVC trên Servlet stack, dùng Java 17+ và ví dụ Spring Boot 3.x / Spring Framework 6.2.x. Bài học tập trung vào `@ExceptionHandler` **đặt trong controller** và các quy tắc Spring chọn handler theo **phạm vi local/global, loại exception, root/cause, thứ tự advice và media type**. Những phần liên quan đến `@ControllerAdvice` được giải thích để hiểu thứ tự lựa chọn, không thay thế trọng tâm local `@ExceptionHandler`.

## 1. Bài toán: một exception cần biến thành HTTP response có ý nghĩa

Giả sử API truy cập sách:

```http
GET /api/books/13
Accept: application/json
```

Nếu sách không tồn tại, service có thể ném:

```java
throw new BookNotFoundException(13);
```

Nếu không chủ động xử lý, kết quả phụ thuộc các `HandlerExceptionResolver` mặc định, cấu hình Spring Boot và kiểu exception; với exception runtime không được nhận diện, thường đi đến lỗi 500. Nhưng "sách không tồn tại" là tình huống nghiệp vụ: API nên trả **404 Not Found** và body giải thích an toàn.

`@ExceptionHandler` cho phép một controller khai báo phương thức chuyên xử lý exception:

```java
@RestController
class BookController {

    @GetMapping("/api/books/{id}")
    public BookDto find(@PathVariable long id) {
        throw new BookNotFoundException(id);
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<String> bookNotFound(BookNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Book not found");
    }
}
```

Đây là **đoạn minh họa**, cần định nghĩa `BookDto` và `BookNotFoundException` để biên dịch. Exception phát sinh khi Spring xử lý handler thuộc `BookController` sẽ được xem xét bởi local exception handler này trước các global advice thích hợp. Client nhận status 404 và body, thay vì một lỗi hệ thống không rõ nghĩa.

**Ba ý chính:**
1. `@ExceptionHandler` là annotation cho **method xử lý exception**, không phải annotation để ném exception.
2. Phương thức được chọn theo **exception được ném và cấu hình mapping**, không dựa vào tên method hay thứ tự các method xuất hiện trong file Java.
3. Method xử lý tự quyết định phản hồi: thường dùng `ResponseEntity<?>`, `ProblemDetail`, `@ResponseStatus` hoặc body/view tương ứng.

## 2. Cơ chế Spring MVC gọi @ExceptionHandler

Một luồng đơn giản:

```text
HTTP request
    |
    v
DispatcherServlet chọn @GetMapping/@PostMapping...
    |
    v
Gọi controller / xử lý đối số / conversion / validation
    |
    +------ thành công --------> return value -> HTTP response
    |
    +------ exception
               |
               v
       HandlerExceptionResolver chain
               |
               v
       ExceptionHandlerExceptionResolver
               |
               +-- tìm @ExceptionHandler local ở controller phù hợp
               |
               +-- nếu chưa xử lý: tìm @ExceptionHandler ở @ControllerAdvice
               |
               +-- áp dụng loại exception, cause, advice order, media type
               |
               v
          handler được chọn -> response
               |
       Nếu không xử lý được:
       các resolver khác / servlet container
```

`ExceptionHandlerExceptionResolver` là một `HandlerExceptionResolver` do Spring MVC cung cấp; còn có cơ chế khác như `ResponseStatusExceptionResolver` và `DefaultHandlerExceptionResolver`. Một exception không được method `@ExceptionHandler` nào nhận vẫn có thể được resolver khác xử lý hoặc đi tiếp ra servlet container.

Phạm vi xử lý là **vòng đời MVC request và những exception mà resolver nhìn thấy**. `@ExceptionHandler` trong controller không tự động bắt mọi lỗi phát sinh bên ngoài DispatcherServlet, chẳng hạn lỗi bị chặn hoàn toàn tại servlet filter trước khi vào MVC. Spring Security có cơ chế `AuthenticationEntryPoint` và `AccessDeniedHandler` trong filter chain; không mặc định giả định lỗi 401/403 đều đến controller advice.

## 3. Viết @ExceptionHandler local trong cùng controller

### 3.1. Chỉ định exception ở annotation

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<String> badArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body("Bad input");
}
```

`@ExceptionHandler(IllegalArgumentException.class)` đăng ký method xử lý `IllegalArgumentException` và các subtype phù hợp; đối số `ex` cho phép đọc exception đã khớp. Nếu controller có `@RestController`, response body được ghi như REST body; `ResponseEntity` cũng cho phép đặt status/header/body trực tiếp.

### 3.2. Suy luận exception type từ đối số

```java
@ExceptionHandler
public ResponseEntity<String> badArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body("Bad input");
}
```

Khi annotation không chỉ rõ loại, Spring có thể lấy loại exception từ đối số method. Cách này gọn nếu handler đúng **một loại exception rõ ràng**. Tránh viết handler không có exception type có thể suy luận hoặc đối số quá chung khiến mapping không đúng mong đợi.

### 3.3. Một handler xử lý nhiều exception type

```java
@ExceptionHandler({
        IllegalArgumentException.class,
        IllegalStateException.class
})
public ResponseEntity<String> invalidState(RuntimeException ex) {
    return ResponseEntity.badRequest().body("Invalid request");
}
```

Đây là **một phương thức minh họa**, gộp hai trường hợp nếu chúng thật sự có cùng HTTP semantics. Loại đối số `RuntimeException` là superclass chung; đừng khai báo một đối số `IllegalArgumentException` khi annotation cũng đăng ký `IllegalStateException` rồi cho rằng Spring luôn có thể truyền exception khác loại vào tham số đó.

Giữ handler hẹp theo nghiệp vụ thường tốt hơn:

```java
@ExceptionHandler(BookNotFoundException.class)
ResponseEntity<ApiError> missing(BookNotFoundException ex) { /* ... */ }
```

Các dấu `/* ... */` biểu thị phần chưa triển khai trong **snippet thiết kế**, không phải chương trình chạy độc lập. Tránh catch `Exception.class` trong controller rồi biến mọi lỗi lập trình thành HTTP 400 hoặc 404.

## 4. Quy tắc 1: local handler được xét trước global advice

- **Local:** method `@ExceptionHandler` được khai báo trong chính `@Controller`/`@RestController` hoặc trong cấu trúc lớp cha áp dụng cho controller đó.
- **Global:** method `@ExceptionHandler` được khai báo trong bean `@ControllerAdvice` hay `@RestControllerAdvice`, có thể áp dụng cho nhiều controller theo selector.
- Với một controller đã được chọn để xử lý request, Spring MVC **xem xét các local exception handler trước** các handler toàn cục có thể áp dụng. Một local match có thể được chọn dù một advice khai báo handler cho cùng exception.

Ví dụ hai nơi đều xử lý `BookNotFoundException`:

```java
@RestController
class BookController {
    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<String> local(BookNotFoundException ex) {
        return ResponseEntity.status(404).body("LOCAL");
    }
}

@RestControllerAdvice
class GlobalErrors {
    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<String> global(BookNotFoundException ex) {
        return ResponseEntity.status(404).body("GLOBAL");
    }
}
```

Đây là **hai lớp minh họa**, không gồm các route gây lỗi. Khi lỗi `BookNotFoundException` phát sinh từ handler trong `BookController`, local method thường trả `LOCAL`; cùng lỗi phát sinh ở controller khác không có local match có thể được global method trả `GLOBAL`.

Điểm cần hiểu: `@Order` trên global advice **không đẩy advice lên trước local handler phù hợp**. Muốn thay policy cho controller cụ thể, cần sửa local handler hoặc phạm vi advice; không dùng `@Order` để tranh quyền với local match.

### 4.1. @ControllerAdvice và @RestControllerAdvice khác gì?

```java
@ControllerAdvice
class ErrorViews {
    @ExceptionHandler(BookNotFoundException.class)
    String page(BookNotFoundException ex) {
        return "book-not-found";
    }
}
```

Với `@ControllerAdvice`, chuỗi có thể là **tên view** (cần view resolver/template). Còn `@RestControllerAdvice` kết hợp `@ControllerAdvice` với `@ResponseBody`: kết quả method được ghi thành **HTTP response body** trừ các kiểu trả về có cơ chế riêng. Cả hai đều tham gia quy trình tìm exception handler. Đừng dùng `@ControllerAdvice` rồi tưởng `return "error"` luôn gửi body text `error`.

### 4.2. Thu hẹp advice theo phạm vi

```java
@RestControllerAdvice(basePackages = "com.example.api")
class ApiErrorAdvice {
    // Các @ExceptionHandler dành cho controller thuộc package mục tiêu.
}
```

Có thể dùng `basePackageClasses`, `annotations` hoặc `assignableTypes` để giới hạn tập controller áp dụng. Nếu đồng thời cung cấp nhiều selector, chúng được kết hợp theo **OR**: phù hợp ít nhất một selector là đủ, không phải bắt buộc thỏa tất cả. Advice mặc định áp dụng rộng; chỉ thêm selector khi có lý do kiến trúc rõ ràng.

## 5. Quy tắc 2: kiểu exception cụ thể hơn được ưu tiên trong cùng tập handler

Giả sử một controller có:

```java
@ExceptionHandler(RuntimeException.class)
ResponseEntity<String> general(RuntimeException ex) {
    return ResponseEntity.internalServerError().body("GENERAL");
}

@ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<String> invalid(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body("SPECIFIC");
}
```

Khi `IllegalArgumentException` được ném trực tiếp, cả hai mapping có thể khớp theo quan hệ kế thừa, nhưng `IllegalArgumentException` là loại **cụ thể hơn**, nên Spring chọn handler trả `SPECIFIC`. Không có quy tắc "method viết trước được chạy trước". `ExceptionDepthComparator` được Spring dùng để so sánh mức độ gần với exception.

Ví dụ hierarchy:

```text
Throwable
  └── Exception
      └── RuntimeException
          └── IllegalArgumentException
```

Nếu có hai handler riêng cho `RuntimeException` và `Exception`, một `IllegalArgumentException` cũng thường chọn `RuntimeException` vì gần subtype hơn. Nếu có handler chính xác `IllegalArgumentException`, handler đó thắng trong cùng tập ứng viên hợp lệ.

**Tuy nhiên:** không định nghĩa hai handler trùng **chính xác một exception mapping và cùng media type** trong một controller/advice. Ví dụ:

```java
@ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<String> first(IllegalArgumentException ex) { /* ... */ }

@ExceptionHandler(IllegalArgumentException.class)
ResponseEntity<String> second(IllegalArgumentException ex) { /* ... */ }
```

Đây là snippet **cố ý sai**, Spring có thể báo **ambiguous @ExceptionHandler method mapping** khi khởi tạo resolver/controller advice. Việc Java cho phép tên method khác nhau không giải quyết được trùng mapping. Phân biệt trường hợp này với hai handler có exception type **khác mức kế thừa** như ví dụ hợp lệ phía trên.

## 6. Quy tắc 3: root exception và cause exception

Một exception có thể bọc exception khác:

```java
throw new IllegalStateException(
        "Load failed",
        new BookNotFoundException(13));
```

Ở đây:
- **Root/top-level exception** được ném lên MVC là `IllegalStateException`.
- **Cause** nằm trong `getCause()` là `BookNotFoundException`.
- Từ Spring Framework 5.3, `@ExceptionHandler` có thể tìm match ở các mức cause lồng nhau, không chỉ mức gần nhất.

Giả sử controller có hai phương thức:

```java
@ExceptionHandler(IllegalStateException.class)
ResponseEntity<String> top(IllegalStateException ex) {
    return ResponseEntity.status(409).body("ROOT");
}

@ExceptionHandler(BookNotFoundException.class)
ResponseEntity<String> nested(BookNotFoundException ex) {
    return ResponseEntity.status(404).body("CAUSE");
}
```

Với ví dụ wrapper `IllegalStateException` phía trên, handler cho **root** được ưu tiên trong cùng controller/advice: trả `ROOT`. Ngược lại, nếu không có local handler nào khớp root, handler cho cause `BookNotFoundException` có thể được chọn và trả `CAUSE`.

Một góc cạnh quan trọng: một handler rất rộng như `@ExceptionHandler(Exception.class)` vẫn là **root match** đối với `IllegalStateException`, nên trong cùng advice nó có thể được ưu tiên hơn một handler khớp cause sâu dù latter là exception type cụ thể hơn. Đừng thiết kế dựa trên giả định "loại cụ thể nhất trong toàn bộ chuỗi cause luôn thắng". Spring đánh giá root và cause theo quy tắc lựa chọn thực tế.

### 6.1. Exception method nhận object nào?

Nếu method `@ExceptionHandler(BookNotFoundException.class)` được chọn nhờ cause, tham số `BookNotFoundException ex` thông thường chính là exception tương ứng trong cause chain. Khi dùng mapping nhiều loại hoặc một đối số superclass rất rộng, đối tượng được truyền có thể là root/wrapper trong những trường hợp hợp lệ. Tốt nhất cho handler có chữ ký **riêng, cụ thể** để giảm mơ hồ.

Không đặt business decisions chỉ dựa vào chuỗi `exception.getMessage()`; nên định nghĩa loại exception/domain code và thuộc tính có cấu trúc như ID bị thiếu.

## 7. Quy tắc 4: thứ tự giữa nhiều @ControllerAdvice

Khi các controller advice đều áp dụng cho request, Spring xử lý theo thứ tự ưu tiên của advice bean:

```java
@RestControllerAdvice
@Order(10)
class HighPriorityAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> handle(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("HIGH");
    }
}

@RestControllerAdvice
@Order(100)
class LowPriorityAdvice {
    @ExceptionHandler(Exception.class)
    ResponseEntity<String> handle(Exception ex) {
        return ResponseEntity.internalServerError().body("LOW");
    }
}
```

Đây là **hai advice minh họa độc lập**; khi `IllegalArgumentException` đến và controller không có local match, advice có `@Order(10)` được xem trước advice `@Order(100)`. **Giá trị thứ tự nhỏ hơn có độ ưu tiên cao hơn.**

Quy tắc root/cause tinh tế:

- **Bên trong một controller/advice**: root exception match được ưu tiên hơn cause match, cùng với độ cụ thể của type.
- **Giữa hai advice có priority khác nhau**: một **cause match trong advice ưu tiên cao hơn** có thể được chọn trước **root match ở advice ưu tiên thấp hơn**.
- Vì vậy nên đặt những handler xử lý root business exceptions chủ yếu trong advice có thứ tự phù hợp và thiết kế phạm vi advice tránh xung đột.

Bảng minh họa lựa chọn, giả định controller không có local handler:

| Tình huống | Ưu tiên thường được chọn |
| --- | --- |
| Advice A `@Order(1)` khớp `IllegalArgumentException` trực tiếp, Advice B `@Order(10)` khớp `Exception` | Advice A |
| Advice A `@Order(1)` khớp **cause** `BookNotFoundException`, Advice B `@Order(10)` khớp **root** `IllegalStateException` | Advice A có thể thắng nhờ thứ tự bean |
| Trong **cùng** advice có handler `Exception` và `BookNotFoundException`, root là `IllegalStateException` bọc cause `BookNotFoundException` | Root-match `Exception` thường thắng |
| Controller có local handler khớp, mọi advice đều có match | Local controller handler được xét trước |

Lưu ý các trường hợp đặc biệt như scoped advice beans có thể ảnh hưởng cách Spring áp dụng interface `Ordered`; không dựa vào may rủi về thứ tự quét class. Thiết lập explicit `@Order` khi nhiều advice có thể cùng xử lý ngoại lệ và dùng integration test xác minh kết quả.

## 8. Quy tắc 5: media type của exception handler (Spring Framework 6.2+)

Từ **Spring Framework 6.2**, annotation `@ExceptionHandler` có thuộc tính `produces`. Nhờ đó có thể đăng ký **hai handler cho cùng exception type** khi biểu diễn phản hồi khác nhau:

```java
// Snippet độc lập minh họa API Spring Framework 6.2+.
@ExceptionHandler(
        value = BookNotFoundException.class,
        produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ErrorDto> json(BookNotFoundException ex) {
    return ResponseEntity.status(404)
            .body(new ErrorDto("NOT_FOUND", "Book not found"));
}

@ExceptionHandler(
        value = BookNotFoundException.class,
        produces = MediaType.TEXT_PLAIN_VALUE)
public ResponseEntity<String> text(BookNotFoundException ex) {
    return ResponseEntity.status(404)
            .body("Book not found");
}
```

Hai method trên **không bị coi là trùng hoàn toàn** vì mỗi method công bố một media type riêng. Header `Accept: application/json` sẽ hướng đến handler JSON; `Accept: text/plain` hướng đến handler text nếu content negotiation thỏa mãn. Với `Accept: */*` hoặc tập media type phức tạp, cần kiểm thử kết quả cụ thể theo thứ tự và negotiation hiện hành, không tùy tiện cam kết một handler.

`@ExceptionHandler(produces=...)` **không tồn tại** trong các phiên bản Spring Framework cũ hơn 6.2. Nếu dùng Boot 3.x nhưng thực tế phụ thuộc Spring Framework 6.0/6.1, code trên có thể **không compile**. Hãy kiểm tra dependency/version thực tế; nếu chưa thể nâng, dùng một handler quyết định response hợp lệ theo API sẵn có thay vì sao chép annotation mới.

Phân biệt:

- `@GetMapping(produces=...)`: chọn **controller endpoint** trước khi thực thi.
- `@ExceptionHandler(produces=...)`: chọn **error representation handler** trong quá trình xử lý exception (Spring 6.2+).
- `ResponseEntity.contentType(...)`: đặt **header response**; không đăng ký một exception mapping mới.
- `@RestControllerAdvice`: ảnh hưởng phạm vi/global body semantics, không thay thế chọn media type.

Nếu request `Accept` không tương thích với bất kỳ error representation nào, Spring có thể cần tới cơ chế fallback khác; đừng mặc định rằng exception luôn được trả dưới một JSON cụ thể bất kể `Accept`.

## 9. Quy tắc 6: rethrow để từ chối xử lý trường hợp đang xét

Một handler có thể nhận exception type rộng nhưng phát hiện instance hiện tại không thuộc phạm vi mong muốn:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<String> selective(IllegalStateException ex) {
    if (!"known-state".equals(ex.getMessage())) {
        throw ex; // Ném lại chính object ban đầu.
    }
    return ResponseEntity.status(409).body("Known conflict");
}
```

Đây là **snippet minh họa**. Việc ném lại **cùng exception instance** cho phép Spring xử lý như thể handler đã từ chối match, và phần còn lại của cơ chế exception resolution có thể tiếp tục. Không tạo exception mới chỉ để chuyển tiếp rồi giả định mọi metadata stacktrace/cause vẫn nguyên vẹn.

Tuy nhiên, rethrow dựa vào message là thiết kế mỏng manh; production nên có exception subtype hoặc error code có cấu trúc:

```java
class InventoryConflictException extends RuntimeException {
    InventoryConflictException(String message) {
        super(message);
    }
}
```

Tạo loại exception rõ nghĩa rồi viết `@ExceptionHandler(InventoryConflictException.class)` là cách dễ dự đoán hơn. Không sử dụng rethrow như một vòng lặp thử từng handler; nên biết rõ resolver nào chịu trách nhiệm.

## 10. @ExceptionHandler có thể nhận đối số và trả kiểu gì?

Các method này có cách resolve tham số và return value tương tự handler MVC, nhưng không phải tất cả input của handler bình thường đều có thể lấy lại. Request body có thể đã được đọc trước lúc phát sinh lỗi.

Các đối số hữu ích:

| Tham số | Dùng để làm gì |
| --- | --- |
| `BookNotFoundException ex` / `Exception ex` | Đọc object exception phù hợp |
| `HttpServletRequest request` | Xem URI, method, request headers; lưu ý namespace `jakarta.servlet.http.*` của Boot 3 |
| `WebRequest` / `NativeWebRequest` | Ngữ cảnh request mà không phụ thuộc servlet API ở mọi thao tác |
| `Locale locale` | Lấy locale theo resolver để quốc tế hóa error message |
| `Principal principal` | Xem identity nếu có; không tự đảm bảo quyền |
| `HandlerMethod handlerMethod` | Thông tin về controller handler gây lỗi khi có |
| `Model model` | Gắn dữ liệu cho error view (phương thức kiểu view) |

Return value phổ biến:

```java
@ExceptionHandler(BookNotFoundException.class)
ResponseEntity<ApiError> error(BookNotFoundException ex) {
    return ResponseEntity.status(404)
            .body(new ApiError("NOT_FOUND", "Book not found"));
}
```

Hoặc `ProblemDetail`:

```java
@ExceptionHandler(BookNotFoundException.class)
ProblemDetail problem(BookNotFoundException ex) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, "Book not found");
    body.setTitle("Requested book not found");
    return body;
}
```

Trong Spring Framework 6+, `ProblemDetail` được hỗ trợ để render lỗi theo **RFC 9457**; status trong `ProblemDetail` quyết định HTTP status khi trả theo đường xử lý chuẩn của Spring. Có thể chọn `ResponseEntity<ProblemDetail>` nếu cần đặt header hoặc status tường minh. Với `@ResponseStatus(HttpStatus.NOT_FOUND)` và body DTO, annotation có thể đặt status cố định. Nếu method trả `String` trong `@Controller` thông thường không có `@ResponseBody`, chuỗi thường là tên view; trong `@RestControllerAdvice` hoặc method có `@ResponseBody` nó đại diện body text.

Đừng cố thêm `@RequestBody` vào exception handler để đọc lại toàn bộ request: stream có thể đã bị tiêu thụ hoặc lỗi parse body chính là nguyên nhân exception.

## 11. Ví dụ Spring Boot 3.x chạy được: local, global, generic và cause

Ví dụ chạy được với **Java 17+**, **Spring Boot 3.x + Spring Web**, dùng Spring Framework 6.x. Nó không phụ thuộc database, Security hay validation starter; tất cả response lỗi do code chủ động trả. Mục tiêu là kiểm tra **method nào được Spring chọn**, thể hiện rõ qua header `X-Error-Source` và body JSON.

Tạo dự án tại [Spring Initializr](https://start.spring.io/), chọn Java 17+ và dependency **Spring Web**. Thay toàn bộ nội dung file `src/main/java/com/example/demo/DemoApplication.java` bằng mã sau:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record ItemDto(long id, String name) {
}

record ApiError(String code, String message, String path) {
}

class ItemNotFoundException extends RuntimeException {
    private final long id;

    ItemNotFoundException(long id) {
        super("Item " + id + " not found");
        this.id = id;
    }

    long getId() {
        return id;
    }
}

@RestController
@RequestMapping("/api/items")
class ItemController {

    @GetMapping("/{id}")
    public ItemDto find(@PathVariable("id") long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (id == 13) {
            throw new ItemNotFoundException(id);
        }
        if (id == 99) {
            throw new IllegalStateException(
                    "wrapped lookup failure",
                    new ItemNotFoundException(id));
        }
        return new ItemDto(id, "item-" + id);
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ApiError> localNotFound(
            ItemNotFoundException ex,
            HttpServletRequest request) {

        ApiError body = new ApiError(
                "ITEM_NOT_FOUND",
                "Item " + ex.getId() + " does not exist",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("X-Error-Source", "controller")
                .body(body);
    }
}

@RestController
@RequestMapping("/api/other")
class OtherController {

    @GetMapping("/items/{id}")
    public ItemDto find(@PathVariable("id") long id) {
        if (id == 13) {
            throw new ItemNotFoundException(id);
        }
        if (id == 99) {
            throw new IllegalStateException("internal simulated failure");
        }
        return new ItemDto(id, "other-" + id);
    }
}

@RestControllerAdvice
class GlobalErrorAdvice {

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ApiError> missing(
            ItemNotFoundException ex,
            HttpServletRequest request) {

        ApiError body = new ApiError(
                "ITEM_NOT_FOUND",
                "Item " + ex.getId() + " does not exist",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("X-Error-Source", "advice")
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        ApiError body = new ApiError(
                "BAD_ARGUMENT",
                "id must be positive",
                request.getRequestURI());

        return ResponseEntity.badRequest()
                .header("X-Error-Source", "advice")
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internal(
            Exception ex,
            HttpServletRequest request) {

        // Không gửi ex.getMessage() cho lỗi hệ thống.
        ApiError body = new ApiError(
                "INTERNAL_ERROR",
                "Unexpected server error",
                request.getRequestURI());

        return ResponseEntity.status(
                        HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Error-Source", "advice")
                .body(body);
    }
}
```

**Điểm quan trọng trong code:**
- `ItemController` có **một local** `@ExceptionHandler(ItemNotFoundException.class)`.
- `GlobalErrorAdvice` có ba handler: `ItemNotFoundException`, `IllegalArgumentException` và `Exception`. Các mapping này **không mơ hồ** vì chúng khác loại, có quan hệ specificity và Spring chọn method phù hợp.
- `OtherController` không có local exception handler, nên khi ném `ItemNotFoundException`, handler global sẽ nhận.
- `find(99)` trong `ItemController` cố ý ném wrapper `IllegalStateException` chứa cause `ItemNotFoundException`: local cause match được xét trước global handler rộng, nên vẫn trả lỗi từ controller.
- Handler generic `Exception.class` trả 500 với thông điệp đã kiểm soát; đây là ví dụ có chủ đích để phân biệt lỗi chưa biết. Trong ứng dụng thực tế, cần log exception với correlation ID và chính sách bảo mật; không trả stacktrace hoặc thông điệp nội bộ.
- Không có Security hoặc data store; tính đúng đắn business/persistence và authorization không nằm trong demo này.

### 11.1. Chạy chương trình

Trong thư mục gốc Spring Initializr project:

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Sau khi server khởi động trên port mặc định 8080, thử từng request. Dùng `curl -i` để thấy status và custom header; trong PowerShell dùng `curl.exe`.

### 11.2. Kết quả 1: request bình thường

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/items/7
```

HTTP status **200 OK**, body JSON dự kiến:

```json
{"id":7,"name":"item-7"}
```

Không có `X-Error-Source` vì không xảy ra exception và exception handler không chạy.

### 11.3. Kết quả 2: controller có local handler

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/items/13
```

Status **404 Not Found**, header:

```http
X-Error-Source: controller
```

Body:

```json
{"code":"ITEM_NOT_FOUND","message":"Item 13 does not exist","path":"/api/items/13"}
```

Dù global advice cũng có handler cho `ItemNotFoundException`, handler local thắng. Không phải do nó nằm phía trên trong source code Java.

### 11.4. Kết quả 3: lỗi cùng loại ở controller khác

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/other/items/13
```

Status **404**, `X-Error-Source: advice`; body:

```json
{"code":"ITEM_NOT_FOUND","message":"Item 13 does not exist","path":"/api/other/items/13"}
```

`OtherController` không có local handler phù hợp, nên advice xử lý.

### 11.5. Kết quả 4: exception đặc hiệu hơn generic

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/items/0
```

Status **400 Bad Request**, `X-Error-Source: advice`, body:

```json
{"code":"BAD_ARGUMENT","message":"id must be positive","path":"/api/items/0"}
```

Advice có cả `@ExceptionHandler(IllegalArgumentException.class)` và `@ExceptionHandler(Exception.class)`. Handler cụ thể hơn được chọn cho top-level `IllegalArgumentException`.

### 11.6. Kết quả 5: local match trên nested cause

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/items/99
```

Status **404**, `X-Error-Source: controller` và body:

```json
{"code":"ITEM_NOT_FOUND","message":"Item 99 does not exist","path":"/api/items/99"}
```

Top-level exception là `IllegalStateException`, cause là `ItemNotFoundException`. Controller chỉ có local mapping cho cause nên local handler khớp. Nếu thêm một local handler cho top-level `IllegalStateException` trong cùng controller, kết quả sẽ thay đổi theo quy tắc root match ưu tiên cause match.

### 11.7. Kết quả 6: fallback generic cho lỗi chưa biết

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/other/items/99
```

Status **500 Internal Server Error**, `X-Error-Source: advice`, body:

```json
{"code":"INTERNAL_ERROR","message":"Unexpected server error","path":"/api/other/items/99"}
```

Không có local handler tại `OtherController` và không có global handler cụ thể cho `IllegalStateException`, nên `@ExceptionHandler(Exception.class)` khớp root exception. Nội dung thật của exception `"internal simulated failure"` không bị gửi ra client.

Các header khác như `Date`, `Connection`, `Content-Type` và thứ tự field/headers phụ thuộc servlet container, message converter và phiên bản. JSON trên là **giá trị payload dự kiến**, không cần so khớp byte-for-byte thứ tự header.

## 12. Kiểm thử quy tắc lựa chọn bằng MockMvc

Để tránh "thêm handler mới rồi vô tình đổi method được chọn", test nên kiểm tra **status + header phân biệt nguồn xử lý + body**. Thêm dependency `spring-boot-starter-test` qua Spring Initializr hoặc `pom.xml`, rồi tạo file `src/test/java/com/example/demo/ExceptionSelectionTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExceptionSelectionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void localHandlerWins() throws Exception {
        mvc.perform(get("/api/items/13"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Error-Source", "controller"))
                .andExpect(jsonPath("$.code")
                        .value("ITEM_NOT_FOUND"));
    }

    @Test
    void globalHandlerHandlesOtherController() throws Exception {
        mvc.perform(get("/api/other/items/13"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Error-Source", "advice"))
                .andExpect(jsonPath("$.path")
                        .value("/api/other/items/13"));
    }

    @Test
    void specificExceptionBeatsGenericInAdvice() throws Exception {
        mvc.perform(get("/api/items/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("BAD_ARGUMENT"));
    }

    @Test
    void localHandlerMatchesNestedCause() throws Exception {
        mvc.perform(get("/api/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Error-Source", "controller"));
    }

    @Test
    void genericAdviceHandlesUnknownFailure() throws Exception {
        mvc.perform(get("/api/other/items/99"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code")
                        .value("INTERNAL_ERROR"));
    }
}
```

Chạy bằng:

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Test giả định dùng Spring Boot 3.x, không thêm Spring Security filter yêu cầu đăng nhập. Đây là kiểm thử cho **quy tắc chọn method**, không chứng minh mọi tình huống bảo mật hay tất cả lỗi framework. Khi đổi version Spring hoặc thêm advice, hãy bổ sung test cho trường hợp overlap mới.

## 13. Tình huống dễ nhầm: exception phát sinh trước khi controller method chạy

Không phải mọi lỗi HTTP đều do code `throw` trong thân method. Ví dụ:

- `@RequestBody` không parse được JSON có thể gây `HttpMessageNotReadableException` trong bước đọc body.
- `@Valid` thất bại có thể gây `MethodArgumentNotValidException`.
- Thiếu request parameter cần thiết có thể gây `MissingServletRequestParameterException`.
- Không khớp `consumes`/`produces` hoặc không tìm được handler có thể phát sinh lỗi trong các pha mapping/dispatch khác nhau.

Một exception resolver phù hợp **có thể** xử lý những lỗi này, nhưng local `@ExceptionHandler` không nhất thiết luôn áp dụng được: khi framework chưa xác định controller method, có thể không có handler cụ thể để tra cứu local mapping. Global advice hoặc resolver mặc định thường phù hợp cho error policy chung.

Khi muốn thay đổi **đúng một nhóm lỗi framework**, nên dùng `@RestControllerAdvice` hoặc mở rộng `ResponseEntityExceptionHandler` ở mức API. Nếu kế thừa `ResponseEntityExceptionHandler`, xem những method `protected` mà framework cung cấp để override; đừng thêm tùy tiện một `@ExceptionHandler` trùng với mapping rộng đã khai báo trong lớp cha, vì có thể gây mơ hồ.

Với `ResponseStatusException` hoặc exception type gắn `@ResponseStatus`, Spring có resolver chuyên dụng; một `@ExceptionHandler` phù hợp có thể can thiệp sớm hơn trong chuỗi resolver mặc định. Đừng coi annotation `@ResponseStatus` trên exception là ưu tiên tuyệt đối trong mọi tình huống có custom/global exception handlers.

## 14. Lựa chọn HTTP error response an toàn

| Trường hợp | Status phù hợp thường gặp | Gợi ý |
| --- | --- | --- |
| Tài nguyên không tồn tại | 404 | `ItemNotFoundException` → 404 |
| Tham số nghiệp vụ không hợp lệ | 400 hoặc 422 theo contract | Trả error code và field context an toàn |
| Trạng thái tài nguyên xung đột | 409 | `ConflictException` → 409 |
| Client thiếu xác thực | 401 | Thường xử lý qua Spring Security entry point nếu có |
| Client không được phép | 403 | Authorization handler; tránh lộ thông tin tài nguyên |
| Lỗi parse JSON / request body | 400 | Error advice chuyên cho parse/validation |
| Lỗi server không xác định | 500 | Thông điệp tổng quát, log nội bộ có correlation ID |

Không trả `ex.getMessage()` từ `SQLException`, `NullPointerException` hay stacktrace trực tiếp cho client. Với exception dự kiến có thông điệp an toàn, có thể lấy mã lỗi/ngữ cảnh đã lọc; với lỗi hệ thống, dùng message tổng quát. Đừng bắt mọi `Exception` rồi trả 200 hoặc 400 vì như vậy che giấu bug và làm sai semantics của HTTP.

Tạo error response thống nhất, ví dụ:

```json
{
  "code": "ITEM_NOT_FOUND",
  "message": "Item 13 does not exist",
  "path": "/api/items/13"
}
```

Hoặc theo chuẩn Problem Details:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Item 13 does not exist",
  "instance": "/api/items/13"
}
```

Đây là **ví dụ cấu trúc**, không cam kết mọi field xuất hiện mặc định nếu chưa đặt thông tin tương ứng. Với ứng dụng đa ngôn ngữ, thông điệp lỗi có thể lấy từ `MessageSource` dựa vào `Locale`; exception handler giữ vai trò chuyển lỗi thành HTTP response, không nên chứa toàn bộ business logic.

## 15. Các lỗi phổ biến khi khai báo @ExceptionHandler

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Handler local không chạy ở controller khác | Local chỉ áp cho controller/lớp cha tương ứng | Dùng advice nếu cần phạm vi chung |
| Global handler không chạy dù khớp type | Có local handler xử lý trước | Kiểm tra local mapping và inheritance |
| Handler `Exception.class` xử lý lỗi cause ngoài mong đợi | Root-match generic trong cùng advice ưu tiên cause | Thu hẹp generic hoặc thiết kế advice order |
| Một advice ưu tiên cao xử lý cause thay vì advice khác xử lý root | `@Order` giữa advice có ưu tiên riêng | Thiết kế priority có chủ đích, thêm test |
| Ứng dụng báo ambiguous handler mapping | Hai method trùng exception type và media type | Gộp/đổi mapping; dùng `produces` 6.2+ nếu biểu diễn khác |
| Chép `@ExceptionHandler(produces=...)` nhưng compile fail | Spring Framework dưới 6.2 | Nâng version hoặc dùng API được hỗ trợ |
| `return "error"` khiến tìm view | `@ControllerAdvice` không có `@ResponseBody` | `@RestControllerAdvice` hoặc `ResponseEntity` |
| Exception handler có status 200 khi trả body lỗi | Quên status | Dùng `ResponseEntity.status(...)` / `ProblemDetail` |
| Handler đọc `@RequestBody` lần nữa rồi lỗi | Body đã bị tiêu thụ | Dùng exception, request metadata và input được lưu có kiểm soát |
| Thêm Spring Security nhưng handler không bắt 401/403 | Lỗi bị xử lý ở filter chain trước MVC | Cấu hình Security entry point/denied handler |
| Handler không bắt lỗi do filter ném | Lỗi ngoài phạm vi DispatcherServlet | Xử lý tại filter hoặc chuyển giao đúng cơ chế |
| Đổi `@ExceptionHandler` nhưng status vẫn lạ | Handler resolver khác, error response có config riêng | Xem log resolver, test HTTP thực |

### 15.1. Trình tự debug một handler không được chọn

1. Xác định **root exception type** được ném vào MVC và đầy đủ `getCause()` chain (trong log server).
2. Xác định controller method có thực sự được match hay exception xảy ra trước mapping.
3. Liệt kê local `@ExceptionHandler` trong controller và hierarchy, đối chiếu class assignability.
4. Nếu không có local match, liệt kê global advices **áp dụng cho controller đó**, kể cả selectors.
5. Kiểm tra `@Order` giữa advice; trong từng advice xét root/cause/specificity.
6. Kiểm tra `produces` (Framework 6.2+), request `Accept`, response converter.
7. Kiểm tra trùng exception mappings, phương thức method signature có exception argument phù hợp.
8. Viết MockMvc test cố định status, error code và custom header để ngăn regression.

Nếu method xử lý **rethrow** chính exception, hãy tiếp tục xem resolver phía sau thay vì chỉ đọc log "handler đã được chọn" rồi kết luận lỗi đã xử lý xong.

## 16. Tự kiểm tra kiến thức

**Câu 1.** Controller và một `@RestControllerAdvice` đều có `@ExceptionHandler(IllegalArgumentException.class)`. Exception được ném từ controller đó; handler nào được thử trước?

**Đáp án:** Handler **local** trong controller được xét trước nếu khớp.

**Câu 2.** Cùng controller có mapping `RuntimeException` và `IllegalArgumentException`. Root exception là `IllegalArgumentException`. Ai được chọn?

**Đáp án:** Handler `IllegalArgumentException`, do mapping cụ thể hơn.

**Câu 3.** Root `IllegalStateException` chứa cause `ItemNotFoundException`; cùng advice có mapping `Exception` và `ItemNotFoundException`. Điều gì có thể gây bất ngờ?

**Đáp án:** `Exception` khớp **root**, còn `ItemNotFoundException` khớp **cause**. Root match trong một advice thường được ưu tiên, mặc dù loại của cause cụ thể hơn.

**Câu 4.** Advice A `@Order(1)` xử lý cause, Advice B `@Order(100)` xử lý root. Controller không có local handler. Có chắc B được chọn vì root không?

**Đáp án:** Không. Advice A ưu tiên cao hơn có thể xử lý cause trước, dù B có root mapping.

**Câu 5.** Hai method trong cùng controller đều khai báo `@ExceptionHandler(IllegalArgumentException.class)` với cùng media type có hợp lệ không?

**Đáp án:** Không; chúng có mapping mơ hồ. Từ Framework 6.2 có thể tách theo `produces` khác nhau nếu API thực sự cần nhiều error representations.

**Câu 6.** Vì sao `@ExceptionHandler(Exception.class)` trả 400 cho mọi exception là nguy hiểm?

**Đáp án:** Nó nuốt cả lỗi server, làm client hiểu sai và khiến monitoring không phân biệt được bug với input không hợp lệ.

**Câu 7.** Filter của Spring Security chặn request trước khi tới DispatcherServlet. Handler local có tự xử lý 403 đó không?

**Đáp án:** Không mặc định. Security filter thường xử lý bằng cơ chế authentication/authorization của nó; `@ExceptionHandler` hoạt động trong phạm vi xử lý exception của Spring MVC.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — Exceptions / @ExceptionHandler](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-exceptionhandler.html): mapping types, root/cause, rethrow, `produces`, method arguments và return values.
- [Spring Framework 6.2 — Controller Advice](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-advice.html): local và global scope, `@RestControllerAdvice`, selector.
- [Spring Framework 6.2 — DispatcherServlet Exception Resolvers](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/exceptionhandlers.html): `HandlerExceptionResolver` chain và MVC default resolvers.
- [Spring Framework 6.2 — Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html): `ProblemDetail`, `ErrorResponse` và `ResponseEntityExceptionHandler`.
- [Spring Framework 6.2 — `@ExceptionHandler` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/bind/annotation/ExceptionHandler.html): annotation parameters `value`, `exception` và `produces`.
- [Spring Framework — `@Order` and advice ordering](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/web/bind/annotation/ControllerAdvice.html): ưu tiên giữa các advice bean.

**Ghi nhớ:** Muốn dự đoán method `@ExceptionHandler` nào chạy, hãy xét theo đúng trình tự: **handler trong controller trước → global advice phù hợp theo thứ tự ưu tiên → đối chiếu root/cause và exception specificity trong từng nhóm → media type phù hợp → method trả HTTP response**. Viết exception types có ý nghĩa nghiệp vụ, tránh global catch-all trả sai status và kiểm thử cả các trường hợp trùng mapping, nested cause và advice overlap.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-003">@ControllerAdvice, @RestControllerAdvice và phạm vi áp dụng</a>

<details>
<summary>Xem chi tiết</summary>

# @ControllerAdvice, @RestControllerAdvice và phạm vi áp dụng trong Spring MVC

## Mục tiêu bài học

Một REST API có hàng chục controller thường cần cùng một cách trả lỗi 400, 404 hoặc 500. Nếu mỗi controller tự viết `try/catch` hoặc lặp `@ExceptionHandler`, định dạng lỗi dễ thiếu nhất quán. **`@ControllerAdvice`** và **`@RestControllerAdvice`** giúp chia sẻ các callback MVC cho nhiều controller, đồng thời cho phép **giới hạn phạm vi áp dụng** theo package, annotation hoặc loại controller.

Sau bài học, bạn sẽ:

- Giải thích `@ControllerAdvice` dùng được cho những callback nào và `@RestControllerAdvice` bổ sung điều gì.
- Phân biệt view rendering với HTTP response body khi xử lý exception.
- Biết advice **mặc định áp dụng rộng**, và chủ động giới hạn bằng `basePackages`, `basePackageClasses`, `assignableTypes`, `annotations`; hiểu quy tắc kết hợp selector là **OR**.
- Hiểu mức ưu tiên giữa `@ExceptionHandler` cục bộ và global, `@Order` giữa nhiều advice, root exception và cause exception.
- Viết Spring Boot 3 / Spring MVC REST API với hai advice có phạm vi tách biệt và kiểm thử bằng MockMvc.
- Tránh handler nuốt toàn bộ `Exception`, làm rò rỉ chi tiết nhạy cảm hoặc tưởng advice xử lý mọi lỗi ở mọi tầng.

**Phạm vi kỹ thuật:** Java 17+, Spring Framework 6.x / Spring Boot 3.x, MVC chạy trên **Servlet stack**. Spring WebFlux có annotation tương tự nhưng luồng xử lý reactive và exception infrastructure khác; ví dụ dưới đây dành cho MVC.

## 1. Trước khi dùng advice: xử lý lỗi cục bộ

Trong một controller, Spring MVC cho phép khai báo `@ExceptionHandler` để xử lý ngoại lệ phát sinh trong các handler của **controller đó**:

~~~java
@RestController
@RequestMapping("/api/books")
class BookController {

    @GetMapping("/{id}")
    public String find(@PathVariable long id) {
        if (id != 1L) {
            throw new BookNotFoundException("Book " + id + " not found");
        }
        return "Spring MVC";
    }

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<String> handleLocal(BookNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Book not found");
    }
}
~~~

Đây là **đoạn minh họa**, cần định nghĩa `BookNotFoundException` và import các class tương ứng. Nó cho thấy controller quản lý được lỗi thuộc phạm vi của mình. Tuy nhiên nếu hàng chục controller đều xử lý lỗi không tìm thấy hoặc validation như nhau, việc sao chép handler làm tăng chi phí bảo trì.

Ở mức MVC, exception được chuyển qua chuỗi `HandlerExceptionResolver`. `ExceptionHandlerExceptionResolver` tìm `@ExceptionHandler` phù hợp; ngoài ra Spring còn có resolver cho một số loại lỗi tiêu chuẩn, status được khai báo và những cơ chế xử lý lỗi tiếp theo. Vì thế không phải **mọi** response lỗi đều chắc chắn bắt nguồn từ một advice.

## 2. `@ControllerAdvice` là gì?

`@ControllerAdvice` đánh dấu một Spring bean chứa các phương thức áp dụng cho **nhiều controller**. Có ba nhóm callback thông dụng:

| Callback trong advice | Mục đích |
| --- | --- |
| `@ExceptionHandler` | Chuyển exception thành status, body, headers hoặc view lỗi |
| `@InitBinder` | Tùy chỉnh `WebDataBinder`: conversion, editor, formatter, validator và allowed fields |
| `@ModelAttribute` trên method | Bổ sung hoặc khởi tạo model dùng chung cho các handler MVC phù hợp |

Ví dụ xử lý ngoại lệ dùng chung:

~~~java
@ControllerAdvice
public class SharedErrorAdvice {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(
            BookNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "BOOK_NOT_FOUND"));
    }
}
~~~

Đây cũng là **đoạn minh họa**, cần import và một `BookNotFoundException`. Khi một controller thuộc phạm vi advice ném ngoại lệ tương ứng, Spring MVC có thể gọi method `handle` và viết response 404.

`@ControllerAdvice` được meta-annotated với `@Component`, nên class thường được tìm thấy bằng **component scanning**; cũng có thể đăng ký trực tiếp thành bean. Nếu advice nằm ngoài những package Spring đang scan, nó sẽ không tự hoạt động chỉ vì bạn đã tạo file Java.

**Phạm vi mặc định:** nếu không khai báo bất kỳ selector nào, advice có thể áp dụng cho **mọi controller MVC phù hợp**, gồm cả `@Controller` và `@RestController`. Annotation không mặc định phân loại controller HTML hay REST.

## 3. `@RestControllerAdvice` khác gì?

`@RestControllerAdvice` là annotation kết hợp tương đương về ngữ nghĩa với:

~~~java
@ControllerAdvice
@ResponseBody
public class RestErrorAdvice {
    // @ExceptionHandler methods...
}
~~~

Nó kế thừa chức năng và selector của `@ControllerAdvice`, đồng thời cho các method xử lý ngoại lệ mặc định mang ngữ nghĩa **`@ResponseBody`**: giá trị trả về bình thường được chuyển thành HTTP response body bằng `HttpMessageConverter` (ví dụ Jackson viết JSON), thay vì được diễn giải như tên view.

Ví dụ:

~~~java
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorBody notFound(BookNotFoundException ex) {
        return new ErrorBody("BOOK_NOT_FOUND", "Book does not exist");
    }

    public record ErrorBody(String code, String message) {}
}
~~~

Đoạn trên là minh họa: `ErrorBody` sẽ thành JSON object với các property `code` và `message` khi có JSON converter, với status 404 do `@ResponseStatus`.

Với `@ControllerAdvice` thông thường:

~~~java
@ControllerAdvice
public class HtmlErrors {

    @ExceptionHandler(BookNotFoundException.class)
    public String notFound(Model model) {
        model.addAttribute("message", "Book does not exist");
        return "errors/not-found";
    }
}
~~~

Đây là **minh họa cho ứng dụng HTML có ViewResolver và template `errors/not-found`**; không phải file độc lập. `String` thông thường được diễn giải như **tên view**. Nếu đặt logic kiểu đó trong `@RestControllerAdvice`, `String` có thể được ghi **thành text response body** `errors/not-found` thay vì chọn template.

**Một điểm dễ nhầm:** `@ControllerAdvice` **vẫn có thể xử lý REST**. Nếu method gắn `@ResponseBody`, trả `ResponseEntity<SomeDto>`, hoặc dùng loại return value mang ngữ nghĩa body thích hợp, nó vẫn gửi JSON/body mà không phải đổi annotation class thành `@RestControllerAdvice`. Sự khác biệt là **giá trị trả về mặc định của các handler**, không phải phạm vi REST/HTML mà advice áp dụng.

| Đặc điểm | `@ControllerAdvice` | `@RestControllerAdvice` |
| --- | --- | --- |
| Là Spring bean được MVC phát hiện | Có | Có |
| Chia sẻ `@ExceptionHandler`, `@InitBinder`, `@ModelAttribute` | Có | Có |
| Scope mặc định khi không selector | Các controller phù hợp nói chung | Các controller phù hợp nói chung |
| Body serialization mặc định cho `@ExceptionHandler` trả object | Không có `@ResponseBody` implicit ở class | Có `@ResponseBody` implicit |
| Trả `String` từ exception handler | Thường là view name nếu không có `@ResponseBody` | Thường là response body |
| Trả `ResponseEntity<Dto>` | Hỗ trợ | Hỗ trợ |
| Nên dùng | MVC HTML, hoặc REST có return type rõ | REST JSON/problem details |

`@RestControllerAdvice` **không** đồng nghĩa “chỉ áp dụng cho `@RestController`”. Nếu muốn riêng REST, bạn phải dùng selector, ví dụ `@RestControllerAdvice(annotations = RestController.class)`.

## 4. Phạm vi áp dụng: 4 nhóm selector

Cả `@ControllerAdvice` lẫn `@RestControllerAdvice` đều hỗ trợ những thuộc tính quan trọng:

### 4.1. `basePackages`

~~~java
@RestControllerAdvice(basePackages = "com.example.demo.api")
public class ApiErrorAdvice {
    // ...
}
~~~

Áp dụng cho các controller thuộc package `com.example.demo.api` hoặc những subpackage bên dưới. Đối với dự án đổi tên/refactor package, chuỗi literal có thể trở nên sai mà compiler không cảnh báo.

Thuộc tính `value` là alias của `basePackages`, ví dụ `@ControllerAdvice("com.example.demo.api")`.

### 4.2. `basePackageClasses`

~~~java
@RestControllerAdvice(basePackageClasses = ApiPackageMarker.class)
public class ApiErrorAdvice {
    // ...
}
~~~

Đây là **đoạn minh họa**, cần có class/interface `ApiPackageMarker` nằm trong package API và khai báo method của advice. Selector dùng **package chứa class được chỉ định** (và package con), không giới hạn chính class marker. Cách này dễ refactor và được compiler kiểm tra tốt hơn chuỗi package.

### 4.3. `assignableTypes`

~~~java
@RestControllerAdvice(assignableTypes = {
        CatalogController.class, BillingController.class
})
public class BusinessErrorAdvice {
    // ...
}
~~~

Ví dụ này chọn những controller mà kiểu của chúng **assignable** tới một trong các class/interface liệt kê (có tính đến phân cấp kế thừa). Có thể dùng controller cụ thể hoặc interface/base controller chung. Đây không phải filter theo tên URL hoặc tên exception.

### 4.4. `annotations`

~~~java
@RestControllerAdvice(annotations = RestController.class)
public class RestOnlyErrorAdvice {
    // ...
}
~~~

Selector tìm controller mang annotation được chỉ định (và cách Spring tìm annotation kết hợp/phân cấp phù hợp). Ví dụ trên là một cách hướng advice tới controller REST, nhưng cần nhớ controller có thể dùng `@Controller` và `@ResponseBody` ở handler để trả API body mà **không** mang `@RestController`; nếu vậy selector này không bao phủ nó.

### 4.5. Quy tắc OR: hai selector không phải phép AND

~~~java
@RestControllerAdvice(
        basePackages = "com.example.demo.api",
        annotations = RestController.class
)
public class MixedScopeAdvice {
    // ...
}
~~~

Khi nhiều selector cùng được khai báo, Spring ghép chúng theo **OR**: controller khớp **ít nhất một** selector là đủ.

Vì vậy ví dụ trên có thể áp dụng cho:
- `@Controller` HTML nằm trong package `com.example.demo.api` dù không mang `@RestController`.
- `@RestController` ở package hoàn toàn khác.

Nếu mục tiêu là “chỉ `@RestController` nằm **trong** package api”, không thể giả định hai thuộc tính trong cùng một advice tạo thành AND. Hãy tổ chức một tập controller có marker annotation hoặc type phù hợp và dùng selector diễn đạt chính xác; nếu cần, tách các nhóm advice riêng.

Các selector được đánh giá tại runtime. Sử dụng rất nhiều selector hoặc quá nhiều advice có thể làm hệ thống khó hiểu hơn và ảnh hưởng chi phí chọn handler. Trong hệ thống đơn giản, nên dùng phạm vi rõ theo package hoặc loại controller; trong hệ thống có nhiều API version, nên chủ động tách bộ xử lý lỗi theo nhóm API.

## 5. Thứ tự ưu tiên và vòng đời callback

### 5.1. `@ExceptionHandler` trong controller được xét trước global advice

Nếu cùng một exception có `@ExceptionHandler` cục bộ trong controller và global handler trong một advice áp dụng, **handler cục bộ của controller được ưu tiên**. Điều này rất hữu ích khi chỉ một controller cần ngoại lệ response đặc thù.

~~~java
@RestController
class OneController {

    @GetMapping("/conflict")
    String action() {
        throw new ItemConflictException("Conflict");
    }

    @ExceptionHandler(ItemConflictException.class)
    ResponseEntity<String> localHandler(ItemConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("local");
    }
}
~~~

Đây là minh họa; cần khai báo `ItemConflictException` và import. Dù có `@RestControllerAdvice` chung xử lý cùng exception, local handler phù hợp thường được chọn trước.

### 5.2. Nhiều advice: `@Order` và `Ordered`

~~~java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SpecificErrors {
    // ...
}

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class FallbackErrors {
    // ...
}
~~~

Đây là đoạn mô tả thứ tự (chưa có các method handler). Spring xét các advice theo thứ tự ưu tiên. `@Order` có giá trị **nhỏ hơn thì ưu tiên cao hơn**; `Ordered` là một cơ chế ưu tiên tương ứng. Với một exception, Spring có thể chọn **advice ưu tiên cao nhất có handler khớp**, không tự chờ một advice ưu tiên thấp hơn dù handler ở đó trông có vẻ cụ thể hơn.

**Root exception vs cause exception:** bên trong **cùng một advice**, matching ở exception gốc thường được ưu tiên hơn matching ở cause. Nhưng một **cause match ở advice ưu tiên cao** vẫn có thể thắng **root match ở advice ưu tiên thấp**. Hãy đặt các mapping exception nghiệp vụ cụ thể trong advice được ưu tiên rõ ràng. Tránh phân tán handler cùng exception vào nhiều advice mà không kiểm soát thứ tự.

Một vài chi tiết của Spring Framework 6.x:
- Bean advice implement `Ordered` có tính ưu tiên so với metadata `@Order`/`@Priority` theo quy tắc framework.
- `PriorityOrdered` trên advice không tự bảo đảm ưu tiên trên mọi `Ordered` như bạn có thể kỳ vọng từ nơi khác.
- `Ordered` **không được tôn trọng** đối với scoped advice bean (ví dụ bean request-scoped hoặc session-scoped). Dùng advice singleton thông thường khi cần điều phối ưu tiên ổn định.
- Không suy đoán thứ tự giữa nhiều handler mơ hồ. Nếu cấu hình có thể dẫn tới ambiguous mapping cho cùng exception và scope, hãy tách rõ kiểu exception/phạm vi/ưu tiên và kiểm thử.

### 5.3. Với `@ModelAttribute` và `@InitBinder`, thứ tự khác

Ngược với `@ExceptionHandler`: `@ModelAttribute` và `@InitBinder` từ **global advice** được áp dụng **trước** method tương ứng trong controller (nếu cùng ngữ cảnh). Điều này hữu ích khi cấu hình binder/model chung phải có trước, rồi controller mới tinh chỉnh.

Ví dụ `@InitBinder` trong advice có thể trim form input cho một nhóm controller; binder cục bộ đặt `allowedFields` cho một model cụ thể. Không kỳ vọng `@InitBinder` chuyển đổi toàn bộ JSON `@RequestBody` đã được Jackson deserialization. Scope advice không thay thế sự khác nhau giữa **WebDataBinder** và **HttpMessageConverter**.

### 5.4. Exception handler không phải blanket `try/catch` cho ứng dụng

`@ControllerAdvice` hoạt động trong luồng xử lý MVC. Ngoại lệ trong authentication filter hoặc tầng proxy có thể được xử lý **trước khi tới DispatcherServlet** và không được advice bắt; với Spring Security, các exception authentication/access liên quan filter chain có cơ chế riêng. Lỗi bất đồng bộ, exception từ task ngoài request hoặc việc response đã commit cũng cần xử lý riêng.

Đừng bắt `Exception.class` toàn cục rồi biến mọi lỗi thành 200/400. Điều đó che lỗi server, khó theo dõi và có thể phá những status Spring MVC xử lý chính xác cho lỗi input.

## 6. Ví dụ chạy được: hai nhóm controller có advice riêng

Ta xây dựng hai endpoint nhóm **catalog** và **billing** cùng ném `RecordMissingException`, nhưng trả body lỗi riêng. Đồng thời catalog có **local `@ExceptionHandler`** cho lỗi conflict để chứng minh local được ưu tiên hơn global.

Dự án Maven dùng **Java 17+**, **Spring Boot 3.x**, dependency **Spring Web** (starter `spring-boot-starter-web`) và **Spring Boot Starter Test** (`spring-boot-starter-test`). Tạo bằng Spring Initializr, package `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/RecordMissingException.java
src/main/java/com/example/demo/CatalogConflictException.java
src/main/java/com/example/demo/CatalogController.java
src/main/java/com/example/demo/BillingController.java
src/main/java/com/example/demo/CatalogErrorAdvice.java
src/main/java/com/example/demo/BillingErrorAdvice.java
src/test/java/com/example/demo/AdviceScopeTest.java
~~~

### 6.1. `DemoApplication.java`

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

### 6.2. Hai exception nghiệp vụ

**`RecordMissingException.java`:**

~~~java
package com.example.demo;

public class RecordMissingException extends RuntimeException {
    public RecordMissingException(String message) {
        super(message);
    }
}
~~~

**`CatalogConflictException.java`:**

~~~java
package com.example.demo;

public class CatalogConflictException extends RuntimeException {
    public CatalogConflictException(String message) {
        super(message);
    }
}
~~~

Không gắn `@ResponseStatus` lên hai exception này: status được quyết định bởi handler được chọn theo scope, giúp chúng ta dễ quan sát sự khác nhau.

### 6.3. `CatalogController.java` có local handler

~~~java
package com.example.demo;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    @GetMapping("/{id}")
    public CatalogItem get(@PathVariable long id) {
        if (id != 1L) {
            throw new RecordMissingException("Catalog item not found");
        }
        return new CatalogItem(1L, "Spring MVC");
    }

    @GetMapping("/conflict")
    public String conflict() {
        throw new CatalogConflictException("Catalog state conflict");
    }

    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<Map<String, String>> localConflict(
            CatalogConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "LOCAL_CATALOG_CONFLICT"));
    }

    public record CatalogItem(long id, String title) {
    }
}
~~~

Khi `/api/catalog/conflict` ném `CatalogConflictException`, local handler trong chính controller này trả **409 Conflict**. Ta sẽ tạo global handler cho cùng exception ở `CatalogErrorAdvice` nhưng nó **không được chọn** cho trường hợp này.

### 6.4. `BillingController.java`

~~~java
package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @GetMapping("/{id}")
    public BillingRecord get(@PathVariable long id) {
        if (id != 1L) {
            throw new RecordMissingException("Billing record not found");
        }
        return new BillingRecord(1L, 750);
    }

    public record BillingRecord(long id, int amount) {
    }
}
~~~

Hai controller cùng ném một **class exception**. Nếu handler global không được scope rõ, rất dễ áp dụng sai format lỗi giữa catalog và billing.

### 6.5. `CatalogErrorAdvice.java`: REST body và ProblemDetail

~~~java
package com.example.demo;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogErrorAdvice {

    @ExceptionHandler(RecordMissingException.class)
    public ResponseEntity<ProblemDetail> missing(
            RecordMissingException ex) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Catalog item not found");
        problem.setTitle("Catalog lookup failed");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem);
    }

    // Chỉ minh họa: nếu controller KHÔNG có local handler,
    // advice này có thể xử lý CatalogConflictException.
    @ExceptionHandler(CatalogConflictException.class)
    public ResponseEntity<Map<String, String>> globalConflict(
            CatalogConflictException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "ADVICE_CATALOG_CONFLICT"));
    }
}
~~~

`ProblemDetail` là kiểu Spring Framework 6.x hỗ trợ phản hồi lỗi theo **RFC 9457**. Khi Jackson và MVC cấu hình thông thường, response ưu tiên `application/problem+json`, có các field như `type`, `title`, `status`, `detail`, có thể có `instance`. Đừng ghi mã lỗi nội bộ hoặc dữ liệu nhạy cảm nguyên vẹn vào `detail` cho client.

Advice này chỉ áp dụng cho `CatalogController` theo `assignableTypes`. `@Order(HIGHEST_PRECEDENCE)` thể hiện độ ưu tiên giữa các advice khi phạm vi chồng nhau; trong ví dụ, hai advice được scope tách biệt nên sự chính xác đến chủ yếu từ selector.

### 6.6. `BillingErrorAdvice.java`: `@ControllerAdvice` vẫn trả JSON

~~~java
package com.example.demo;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = BillingController.class)
public class BillingErrorAdvice {

    @ExceptionHandler(RecordMissingException.class)
    public ResponseEntity<Map<String, String>> missing(
            RecordMissingException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "BILLING_NOT_FOUND",
                        "message", "Billing record not found"));
    }
}
~~~

Đây là bằng chứng rằng `@ControllerAdvice` **không chỉ dành cho HTML view**: method trả `ResponseEntity<Map<...>>` được Spring xử lý như HTTP body bình thường. Nếu thay return type thành một `String` không có `@ResponseBody`, việc xử lý lại có thể theo cơ chế view name.

Hai advice đều nhìn thấy cùng class `RecordMissingException` nhưng **không đụng nhau** vì `assignableTypes` tách controller catalog với billing.

## 7. Chạy và đối chiếu HTTP status/body

Tại thư mục dự án Maven, chạy:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper, dùng `mvn spring-boot:run` sau khi cài Maven.

### Catalog thiếu bản ghi

~~~bash
curl -i http://localhost:8080/api/catalog/99
~~~

Kết quả kỳ vọng **404 Not Found**, media type tương thích `application/problem+json`, JSON có nội dung tương đương:

~~~json
{
  "type": "about:blank",
  "title": "Catalog lookup failed",
  "status": 404,
  "detail": "Catalog item not found"
}
~~~

`instance` có thể được Spring bổ sung từ path tùy ngữ cảnh; không cố định toàn bộ JSON body và thứ tự property. Nếu server có custom Jackson/advice khác thì representation cũng có thể thay đổi.

### Billing thiếu bản ghi

~~~bash
curl -i http://localhost:8080/api/billing/99
~~~

Kết quả kỳ vọng **404 Not Found** với JSON:

~~~json
{
  "error": "BILLING_NOT_FOUND",
  "message": "Billing record not found"
}
~~~

Cùng exception, cùng status 404 nhưng **body khác** vì advice khác nhau được chọn theo controller.

### Local exception handler thắng advice

~~~bash
curl -i http://localhost:8080/api/catalog/conflict
~~~

Kết quả kỳ vọng **409 Conflict** và JSON:

~~~json
{"error":"LOCAL_CATALOG_CONFLICT"}
~~~

Nếu local handler không được xét trước, advice catalog có thể trả **422 Unprocessable Entity** với `ADVICE_CATALOG_CONFLICT`. Vì local handler tồn tại, nhánh global đó không chạy trong request này.

### Trường hợp thành công

~~~bash
curl -i http://localhost:8080/api/catalog/1
curl -i http://localhost:8080/api/billing/1
~~~

Hai response đều có status **200 OK**, lần lượt chứa:

~~~json
{"id":1,"title":"Spring MVC"}
~~~

~~~json
{"id":1,"amount":750}
~~~

Vì ví dụ không dùng database, dữ liệu hoàn toàn cố định để thuận tiện kiểm thử. Trên PowerShell Windows, dùng `curl.exe` để tránh alias `curl` ở một số môi trường.

## 8. Kiểm thử phạm vi bằng MockMvc

Thêm dependency `spring-boot-starter-test`. Tạo file **`src/test/java/com/example/demo/AdviceScopeTest.java`**:

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CatalogController.class, BillingController.class
})
@Import({CatalogErrorAdvice.class, BillingErrorAdvice.class})
class AdviceScopeTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void catalogErrorUsesCatalogAdvice() throws Exception {
        mockMvc.perform(get("/api/catalog/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title")
                        .value("Catalog lookup failed"))
                .andExpect(jsonPath("$.detail")
                        .value("Catalog item not found"));
    }

    @Test
    void billingErrorUsesBillingAdvice() throws Exception {
        mockMvc.perform(get("/api/billing/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("BILLING_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Billing record not found"));
    }

    @Test
    void localExceptionHandlerWinsOverGlobalAdvice() throws Exception {
        mockMvc.perform(get("/api/catalog/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("LOCAL_CATALOG_CONFLICT"));
    }

    @Test
    void successfulRequestsStillReturnNormalResponses() throws Exception {
        mockMvc.perform(get("/api/catalog/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring MVC"));

        mockMvc.perform(get("/api/billing/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(750));
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Các test này xác nhận **hành vi observable**, không chỉ kiểm tra class có annotation. Cụ thể:
1. Catalog lỗi 404 dùng RFC 9457 `ProblemDetail`, không nhận JSON của billing.
2. Billing lỗi 404 dùng body của billing, không nhận `ProblemDetail` catalog.
3. Local handler cho `CatalogConflictException` nhận 409, không bị advice global trả 422.
4. Các đường không lỗi vẫn phản hồi như bình thường.

`@WebMvcTest` chỉ dựng web MVC test slice; `@Import` bảo đảm hai advice có mặt trong context. Nếu dự án thực có Spring Security và CSRF, cần bổ sung cấu hình xác thực phù hợp ở các test có yêu cầu đó. Trong ví dụ chỉ dùng Spring Web + Starter Test nên không cần mở HTTP server thật.

## 9. Một advice HTML riêng với `ModelAndView`

Ứng dụng vừa có web page vừa có REST API có thể dùng advice dành riêng cho HTML controller:

~~~java
@ControllerAdvice(basePackageClasses = PageController.class)
public class HtmlPageAdvice {

    @ExceptionHandler(RecordMissingException.class)
    public ModelAndView missingPage(RecordMissingException ex) {
        ModelAndView mav = new ModelAndView("errors/not-found");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("message", "Page does not exist");
        return mav;
    }
}
~~~

**Đây là mẫu minh họa**, không thuộc ứng dụng chạy đầy đủ ở phần 6: bạn cần tạo `PageController`, có view resolver/template `errors/not-found` và import `ModelAndView`, `HttpStatus`, annotation liên quan.

`basePackageClasses = PageController.class` chọn **cả package chứa** `PageController`, không chỉ class đó. Nếu cả controller API và HTML chung package, selector này có thể bao gồm cả API; khi cần giới hạn chính một controller, dùng `assignableTypes = PageController.class`.

Một cách tổ chức thường gặp:

- HTML controllers trong `com.example.web.pages` được `@ControllerAdvice(basePackages="com.example.web.pages")` xử lý bằng views.
- REST controllers trong `com.example.web.api` được `@RestControllerAdvice(basePackages="com.example.web.api")` xử lý bằng JSON/`ProblemDetail`.

Cấu trúc package này làm scope dễ đọc, nhưng cần kiểm thử vì package con và các selector OR có thể mở phạm vi rộng hơn dự tính.

## 10. `ResponseEntityExceptionHandler` và lỗi sẵn có của Spring MVC

Không phải tất cả lỗi HTTP đều là exception nghiệp vụ tự định nghĩa. Spring MVC còn phát sinh lỗi khi thiếu request parameter, sai method, JSON malformed, media type sai, validation thất bại, binding sai và những tình huống khác.

Spring Framework 6.x cung cấp **`ResponseEntityExceptionHandler`**, một lớp nền có thể được `@ControllerAdvice` kế thừa để xử lý nhiều exception của Spring MVC và lỗi có kiểu `ErrorResponse` theo hướng thống nhất. Nếu dùng nó, bạn có thể override những protected hook thích hợp để tùy chỉnh status/body, và khai báo thêm `@ExceptionHandler` cho exception nghiệp vụ của mình.

**Không nên** tạo một method `@ExceptionHandler(Exception.class)` ở mức ưu tiên cao chỉ để đổi mọi lỗi thành 400. Điều đó dễ che lỗi hệ thống thật, có thể can thiệp các status framework đã ánh xạ đúng, và tạo JSON contract sai cho client.

### `ProblemDetail` và RFC 9457

`ProblemDetail` có các field chuẩn như `type`, `title`, `status`, `detail`, `instance`. Spring MVC có thể lấy status từ `ProblemDetail`/response, và dùng media type `application/problem+json` khi Jackson xử lý dạng này.

Khi muốn thêm trường nghiệp vụ, có thể dùng `problem.setProperty("code", "CATALOG_NOT_FOUND")` và kiểm thử JSON render; không tự ý chèn stack trace hoặc SQL. Ví dụ:

~~~java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, "Resource does not exist");
problem.setTitle("Resource not found");
problem.setProperty("code", "RESOURCE_NOT_FOUND");
~~~

Đoạn này là **minh họa trong method** (cần import). Spring có hỗ trợ các field mở rộng được serialize bên cạnh các field chuẩn của RFC 9457.

Spring Boot có cấu hình `spring.mvc.problemdetails.enabled` ở những phiên bản 3.x phù hợp để hỗ trợ Problem Details cho nhiều lỗi tích hợp. Tuy nhiên cách Boot tự cấu hình advice, thứ tự handler và những cấu hình đã có phụ thuộc phiên bản. **Không bật một property rồi mặc định mọi exception custom đều được map sang body mong muốn**; cần test hợp đồng API cụ thể.

### REST error JSON bằng `@ControllerAdvice` thuần có hợp lệ không?

Có. Trong ví dụ phần 6, `BillingErrorAdvice` dùng `@ControllerAdvice` mà vẫn trả `ResponseEntity<Map<String,String>>`. Return value được Spring xử lý thành body JSON nhờ `ResponseEntity`. `@RestControllerAdvice` chủ yếu giảm việc gắn `@ResponseBody` cho những method trả DTO/String làm body.

## 11. Khi exception handler không xử lý được

Các trường hợp sau cần xem xét luồng xử lý trước khi kết luận advice “không chạy”:

| Trường hợp | Vì sao advice có thể không xử lý | Cách xác minh |
| --- | --- | --- |
| Lỗi được ném trong Servlet filter hoặc Spring Security filter trước MVC | Không đi qua chính exception resolution của controller MVC | Kiểm tra Security filter chain, authentication entry point, access denied handler |
| Client ngắt kết nối hoặc response đã commit | Không còn khả năng ghi lại status/body bình thường | Xem log, thời điểm commit và cách truyền response |
| Lỗi task nền ngoài request | Không thuộc MVC handler execution | Dùng error handling của async executor, message consumer, scheduler |
| Controller không khớp selector | Advice không áp dụng cho handler đó | Kiểm tra package/type/annotation trên controller và quy tắc OR |
| Advice không được Spring đăng ký bean | Class nằm ngoài component scan hoặc profile/config loại bỏ | Kiểm tra application context và startup logs |
| Có local `@ExceptionHandler` | Local handler được ưu tiên trước advice | Kiểm tra controller/hierarchy |
| Advice khác có ưu tiên cao hơn | Handler ở advice ưu tiên trước đã khớp | Kiểm tra `@Order`, `Ordered`, exception root/cause |
| Handler trả view name trong REST | Dùng `@ControllerAdvice` + `String` thiếu body semantics | Trả `ResponseEntity`, gắn `@ResponseBody` hoặc dùng `@RestControllerAdvice` |

Một `@ExceptionHandler` có thể ném lại **chính exception** mà nó nhận được để từ chối xử lý trường hợp cụ thể; Spring MVC có cơ chế cho các resolver tiếp tục xem xét. Chỉ sử dụng kỹ thuật này khi cần, vì nó gây khó đọc và nên được kiểm thử.

## 12. Các sai lầm phổ biến về phạm vi

### 12.1. Nghĩ `@RestControllerAdvice` chỉ bắt lỗi từ `@RestController`

Sai: **phạm vi mặc định không phân biệt** REST hay HTML. Nếu muốn chỉ áp dụng cho `@RestController`, đặt `annotations = RestController.class` hoặc tổ chức package rồi cấu hình selector rõ.

### 12.2. Nghĩ hai selector được ghép AND

Sai: `basePackages="..."` cùng `annotations=RestController.class` tạo điều kiện OR. Điều này có thể làm advice rộng hơn mong muốn và trả JSON cho controller HTML. Cách khắc phục là dùng tiêu chí đơn nhất đủ chính xác hoặc tách controller/advice theo marker/nhóm.

### 12.3. Gắn `@Order` mà tưởng override local handler

Không. `@Order` dùng phân loại ưu tiên **giữa các advice**; một `@ExceptionHandler` cục bộ phù hợp trong controller được xét trước global advice. Nếu muốn thay đổi hành vi cục bộ, cần sửa controller/hierarchy tương ứng.

### 12.4. Handler generic bắt mọi lỗi và trả 200

Ví dụ phản diện:

~~~java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> catchEverything(Exception ex) {
    return ResponseEntity.ok("Oops");
}
~~~

Đây là đoạn minh họa **không nên dùng**. Nó biến lỗi hệ thống thành thành công giả, ảnh hưởng monitor/client. Nếu xây fallback handler thực sự, chỉ nên đặt ở mức ưu tiên thấp, trả 500 khi thích hợp, log correlation ID, và không tiết lộ bí mật cho client.

### 12.5. Dùng exception message nội bộ làm public JSON

`ex.getMessage()` đôi khi chứa ID nội bộ, đường dẫn tệp, SQL, thông tin người dùng hoặc stack context. Trả message public đã kiểm duyệt, đồng thời ghi log có cấu trúc an toàn với trace/correlation ID riêng. `@RestControllerAdvice` không tự loại bỏ nội dung nhạy cảm của chuỗi bạn trả.

### 12.6. Tưởng advice biến mọi validation error thành cùng exception

Lỗi `@Valid @RequestBody`, `@Validated` trên method, thiếu `@RequestParam`, không parse được JSON và lỗi binding có thể tạo **những class exception khác nhau**. Nếu chuẩn hóa lỗi, xem exception thực tế và các hook của `ResponseEntityExceptionHandler`; tránh một handler bắt chung và bỏ qua status.

### 12.7. Dùng `@ControllerAdvice` cho lỗi của bất kỳ thread nào

Advice gắn với cơ chế Spring MVC. Scheduled job, `@Async` chạy tách khỏi request hoặc message listener có error handler riêng. Cần xử lý exception theo đúng execution context.

## 13. Câu hỏi ôn tập

**1. `@RestControllerAdvice` có tự động chỉ áp dụng cho `@RestController` không?**

Không. Nó có cùng scope mặc định của `@ControllerAdvice` và thêm ngữ nghĩa `@ResponseBody`. Muốn chỉ chọn REST controllers, dùng selector phù hợp.

**2. Nếu cả controller và advice có `@ExceptionHandler` xử lý cùng một exception, Spring ưu tiên cái nào?**

Handler cục bộ của controller được ưu tiên. Đây là hành vi quan trọng được test bằng `/api/catalog/conflict` trong ví dụ.

**3. Nếu một advice khai báo đồng thời `basePackages` và `annotations` thì Spring chọn controller thế nào?**

Ghép theo OR: khớp ít nhất một selector là đủ. Không phải AND.

**4. Tại sao `BillingErrorAdvice` dùng `@ControllerAdvice` mà vẫn trả JSON?**

Vì exception handler trả `ResponseEntity<Map<...>>`, vốn có ngữ nghĩa HTTP response body rõ ràng, không cần ngữ nghĩa mặc định `@ResponseBody` từ class.

**5. Nếu hai advice xử lý cùng exception và cùng áp dụng, yếu tố nào cần kiểm tra?**

Thứ tự `@Order`/`Ordered`, handler khớp root/cause, kiểu exception cụ thể, khả năng có local handler và việc advice thật sự nằm trong application context.

**6. Muốn chia sẻ `@InitBinder` nhưng không muốn mọi controller bị ảnh hưởng thì sao?**

Đặt `@InitBinder` trong advice có selector hẹp, hoặc giữ binder method trực tiếp tại controller. Kiểm thử các model attribute và converter/formatter được dùng thực tế.

## 14. Checklist áp dụng trong dự án

1. Phân nhóm rõ controller REST, HTML, API version và các domain nếu format lỗi khác nhau; đặt scope advice tương ứng.
2. Với REST trả DTO hoặc ProblemDetail, dùng `@RestControllerAdvice` hoặc `@ControllerAdvice` trả `ResponseEntity` rõ ràng.
3. Với view HTML, cân nhắc `@ControllerAdvice` + view resolver/`ModelAndView`.
4. Luôn nhớ selector mặc định là toàn cục và nhiều selector là OR; chọn `basePackageClasses` hoặc `assignableTypes` khi muốn tránh chuỗi package khó refactor.
5. Tránh handler cùng exception nằm rải rác mà không có quy tắc `@Order` và test rõ; local handler vẫn được ưu tiên trước global.
6. Phân biệt exception nghiệp vụ với exception của framework, filter/security và task nền; dùng đúng tầng xử lý.
7. Test đồng thời **status, content type, response JSON và phạm vi advice**; test trường hợp controller không bị áp dụng sai.
8. Trả thông tin lỗi public tối thiểu, không tiết lộ stack trace/SQL/secret; dùng correlation ID và log an toàn khi cần.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Controller Advice](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-advice.html)
- [Spring Framework 6.2 — `@ExceptionHandler`](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- [Spring Framework 6.2 — `@InitBinder`](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-initbinder.html)
- [Spring Framework 6.2 — REST Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring Framework 6.2 Javadoc — `ControllerAdvice`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/ControllerAdvice.html)
- [Spring Framework 6.2 Javadoc — `RestControllerAdvice`](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/RestControllerAdvice.html)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-004">HandlerExceptionResolver và ResponseEntityExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

# HandlerExceptionResolver và ResponseEntityExceptionHandler trong Spring MVC

## 1. Hai thành phần giải quyết vấn đề gì?

Trong Spring MVC, một request có thể thất bại **trước khi controller được gọi**, **trong lúc xử lý controller**, hoặc **khi ghi phản hồi**. Ví dụ: JSON sai cú pháp, query parameter không chuyển được sang số, vi phạm Bean Validation, resource không tồn tại hoặc exception từ service.

Spring MVC cung cấp hai tầng liên quan nhưng có vai trò khác nhau:

- **`HandlerExceptionResolver`** là **contract hạ tầng** do `DispatcherServlet` sử dụng để nhờ các resolver xử lý exception. Có thể có nhiều implementation và một chuỗi được gọi theo thứ tự.
- **`ResponseEntityExceptionHandler`** là **base class cho `@ControllerAdvice`** để trả về `ResponseEntity` với phản hồi lỗi chuẩn cho nhiều exception quen thuộc của Spring MVC. Bản thân class này **không phải implementation của `HandlerExceptionResolver`**: Spring gọi các method `@ExceptionHandler` của nó thông qua `ExceptionHandlerExceptionResolver`.

Đây là phân biệt quan trọng nhất của bài. Thông thường, ứng dụng REST cần tùy chỉnh lỗi bằng **`@RestControllerAdvice` + kế thừa `ResponseEntityExceptionHandler`** hoặc các method `@ExceptionHandler` riêng. Chỉ viết `HandlerExceptionResolver` tùy biến nếu thật sự cần kiểm soát ở mức chuỗi resolver.

Ví dụ lỗi khác nhau cần phản hồi thích hợp:

| Lỗi | Exception tiêu biểu | HTTP status thông dụng |
| --- | --- | --- |
| JSON body không đọc được | `HttpMessageNotReadableException` | 400 Bad Request |
| `@Valid @RequestBody` không hợp lệ | `MethodArgumentNotValidException` | 400 Bad Request |
| Constraint trực tiếp trên tham số controller, Spring 6.1+ | `HandlerMethodValidationException` | 400 cho lỗi input; có thể 500 cho lỗi return value |
| Thiếu query parameter bắt buộc | `MissingServletRequestParameterException` | 400 Bad Request |
| Request sử dụng HTTP method không được phép | `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed |
| Không có biểu diễn chấp nhận được theo `Accept` | `HttpMediaTypeNotAcceptableException` | 406 Not Acceptable |
| Request body có media type không hỗ trợ | `HttpMediaTypeNotSupportedException` | 415 Unsupported Media Type |
| Nghiệp vụ không tìm thấy entity | Exception domain do ứng dụng định nghĩa | Thường 404 Not Found |
| Bug không được xử lý trong ứng dụng | `NullPointerException` hoặc tương tự | 500 Internal Server Error |

Trạng thái cuối phụ thuộc cấu hình resolver, `@ControllerAdvice`, filter, Spring Security, Servlet container và ứng dụng. Không nên xem bảng này là cam kết tự động cho mọi exception.

## 2. Vòng đời lỗi bên trong `DispatcherServlet`

Luồng rút gọn:

```text
HTTP request
      |
Servlet Filter chain (Security, logging, ...)
      |
DispatcherServlet
      |
HandlerMapping -> HandlerAdapter -> Controller
      |                                |
      |                           exception được ném
      +------------------------------->|
      |
      v
HandlerExceptionResolver chain
      |
      +-- ExceptionHandlerExceptionResolver
      |     @ExceptionHandler local / @ControllerAdvice
      |     ResponseEntityExceptionHandler (qua advice)
      |
      +-- ResponseStatusExceptionResolver
      |     @ResponseStatus / ResponseStatusException
      |
      +-- DefaultHandlerExceptionResolver
      |     lỗi chuẩn của Spring MVC -> HTTP status
      |
      +-- resolver khác nếu ứng dụng đăng ký
      |
      v
HTTP error response / ModelAndView
      |
      +-- không ai xử lý? Có thể chuyển về Servlet container,
          error dispatch, Spring Boot /error (tùy trường hợp)
```

**Thứ tự trên là cấu hình MVC mặc định điển hình**, không phải thứ tự bất biến khi ứng dụng thêm resolver và `Ordered`. Một resolver xử lý thành công sẽ dừng chuỗi tại đó; những resolver sau thường không được gọi.

`HandlerExceptionResolver` có method:

```java
ModelAndView resolveException(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex);
```

Đây là chữ ký API minh họa, **không phải một method độc lập để chạy bằng `javac`**. Trong các phiên bản Spring hiện đại, tham số `handler` có thể `null` khi chưa chọn được handler. Contract có ba trường hợp:

- Trả **`ModelAndView` không rỗng**: exception đã xử lý, Spring có thể render view được chỉ định.
- Trả **`new ModelAndView()` rỗng**: exception đã xử lý và response đã được chuẩn bị; không cần render view.
- Trả **`null`**: resolver **không xử lý** exception; `DispatcherServlet` thử resolver tiếp theo.

**Không trả `null` sau khi tự ghi response**, nếu mục đích của bạn là kết thúc xử lý. Làm thế có thể khiến resolver khác tiếp tục ghi response, gây double-write hoặc sai status.

Exception ném từ Servlet Filter **trước khi vào `DispatcherServlet`** không mặc nhiên đi qua chuỗi resolver MVC. Tương tự, lỗi xảy ra sau khi response đã committed không thể tùy ý đổi thành body lỗi JSON mới.

## 3. Các `HandlerExceptionResolver` quan trọng

### 3.1. `ExceptionHandlerExceptionResolver`

Thực thi các method được gắn `@ExceptionHandler` trong controller và các bean `@ControllerAdvice`/`@RestControllerAdvice`.

Ví dụ khai báo cục bộ:

```java
@RestController
class ProfileController {

    @GetMapping("/profiles/{id}")
    String profile(@PathVariable long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("invalid id");
        }
        return "profile-" + id;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> badId(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("Invalid profile id");
    }
}
```

Đây là class minh họa trong Spring MVC, cần các imports Spring. Handler này **chỉ áp dụng cho controller tương ứng**; khi muốn tái sử dụng toàn ứng dụng, chuyển sang global advice.

Các method `@ExceptionHandler` cục bộ được xét trước global advice. Trong nhiều advice, Spring sử dụng `@Order`/`Ordered` để quyết định mức ưu tiên; **root exception match** thường được ưu tiên trước cause match trong **cùng advice**, nhưng cause match trong advice ưu tiên cao hơn có thể thắng root match ở advice thấp hơn. Vì vậy, tránh các handler quá rộng như `@ExceptionHandler(Exception.class)` có độ ưu tiên cao nếu không có chiến lược rõ.

### 3.2. `ResponseStatusExceptionResolver`

Đọc `@ResponseStatus` trên exception hoặc `ResponseStatusException` và chuyển thành trạng thái HTTP phù hợp.

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
class BookNotFoundException extends RuntimeException {
    BookNotFoundException(long id) {
        super("Book " + id + " not found");
    }
}
```

Khi exception này được ném, `ResponseStatusExceptionResolver` có thể map thành HTTP 404 **nếu resolver trước chưa xử lý nó**. `@ResponseStatus` không tự cung cấp schema JSON lỗi ổn định, trường `code` tùy biến hay bộ field errors.

Spring 6+ hỗ trợ `ProblemDetail` và `ErrorResponse`; trong ứng dụng muốn output lỗi thống nhất, một `@RestControllerAdvice` trả `ProblemDetail`/`ResponseEntity` thường thuận tiện hơn.

### 3.3. `DefaultHandlerExceptionResolver`

Xử lý nhiều lỗi chuẩn do Spring MVC sinh ra (ví dụ method không được hỗ trợ, thiếu request parameter, media type không phù hợp) và chuyển sang HTTP status tương ứng. Nó **không tự hiểu mọi exception nghiệp vụ** như `CustomerNotFoundException` hay `InventoryConflictException`.

Với API JSON, base class `ResponseEntityExceptionHandler` có thể bắt nhiều lỗi MVC và tạo lỗi dạng `ProblemDetail` trước khi đến default resolver. Nếu không có advice phù hợp, response thực tế có thể do default resolver và lớp xử lý lỗi của Servlet container/Spring Boot hoàn thiện.

### 3.4. `SimpleMappingExceptionResolver`

Resolver ánh xạ tên loại exception sang **view name**. Thường hữu ích với ứng dụng MVC render JSP/Thymeleaf và các trang HTML lỗi truyền thống.

Đối với API REST trả JSON, `@RestControllerAdvice` hoặc `ResponseEntityExceptionHandler` thường phù hợp hơn. Đừng đặt một HTML error view resolver bắt mọi lỗi cho endpoint JSON rồi để client nhận HTML không mong đợi.

## 4. `ResponseEntityExceptionHandler` giải quyết phần nào?

Class `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler` cung cấp một `@ExceptionHandler` xử lý nhiều exception chuẩn của Spring MVC, dựng `ResponseEntity<Object>` theo status, headers, body. Spring Framework 6+ dùng hỗ trợ `ProblemDetail`; trong Framework 6.2, định dạng lỗi được mô tả theo **RFC 9457**.

Cách dùng chuẩn:

```java
@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {
    // Override protected handleXxx(...) khi cần tùy chỉnh
    // hoặc khai báo @ExceptionHandler cho lỗi domain riêng.
}
```

Đây là fragment cần imports; `ResponseEntityExceptionHandler` xử lý tập exception Spring MVC đã biết, còn những exception riêng do ứng dụng định nghĩa **cần thêm `@ExceptionHandler` phù hợp**.

Những hook điển hình có thể override trong **Spring Framework 6.1/6.2**:

| Protected method | Khi nào nên dùng |
| --- | --- |
| `handleMethodArgumentNotValid` | Điều chỉnh lỗi Bean Validation trên DTO |
| `handleHandlerMethodValidationException` | Điều chỉnh lỗi native method validation (Spring 6.1+) |
| `handleHttpMessageNotReadable` | Chuẩn hóa JSON malformed hoặc không deserialize được |
| `handleHttpRequestMethodNotSupported` | 405 và metadata về HTTP methods được phép |
| `handleHttpMediaTypeNotSupported` | 415 Unsupported Media Type |
| `handleHttpMediaTypeNotAcceptable` | 406 Not Acceptable |
| `handleMissingServletRequestParameter` | Query parameter bắt buộc bị thiếu |
| `handleTypeMismatch` | Đầu vào không thể chuyển sang kiểu mong đợi |
| `handleNoResourceFoundException` | Không tìm thấy static resource, Spring 6.1+ |
| `handleExceptionInternal` | Điểm xử lý chung cho một số exception |
| `createResponseEntity` | Điểm cuối để tạo `ResponseEntity` từ body/status/headers |

**Không viết thêm `@ExceptionHandler(MethodArgumentNotValidException.class)` bên trong subclass này** khi base class đã đăng ký exception đó qua `handleException`. Hai handler trùng có thể gây **ambiguous exception handler mapping** lúc khởi tạo.

Thay vào đó, override **protected `handleMethodArgumentNotValid(...)`**. Với lỗi nghiệp vụ mới (chưa nằm trong tập exception base class xử lý), hãy khai báo `@ExceptionHandler(MyDomainException.class)`.

### Chữ ký của override và khác biệt phiên bản

Trong Framework 6.x, hook dùng `HttpStatusCode`:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
    // custom xử lý
}
```

Các tên type ở đây thuộc Spring MVC, cần imports phù hợp. Đừng sao chép override từ Spring Framework 5.x có tham số `HttpStatus` rồi mong nó override thành công trong 6.x. Framework 7 có thêm tiến hóa API, nên khi nâng version hãy xem Javadoc tương ứng thay vì tự sửa signature theo suy đoán.

## 5. `ProblemDetail`: chuẩn hóa body lỗi

RFC 9457 định nghĩa cấu trúc **Problem Details for HTTP APIs**. Spring Framework 6+ có `org.springframework.http.ProblemDetail` với những trường chuẩn:

| JSON field | Ý nghĩa |
| --- | --- |
| `type` | URI xác định loại vấn đề; mặc định thường là `about:blank` |
| `title` | Mô tả ngắn, dùng như nhãn loại lỗi |
| `status` | Mã HTTP tương ứng |
| `detail` | Diễn giải cụ thể cho instance lỗi |
| `instance` | URI chỉ instance xảy ra vấn đề |
| Extension fields | Thông tin tùy biến như `code`, `fields`, `traceId` |

Ví dụ response API có schema rõ:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Book not found",
  "status": 404,
  "detail": "Book was not found",
  "code": "BOOK_NOT_FOUND"
}
```

Đây là **response minh họa**, không khẳng định mọi cấu hình sẽ phát chính xác các headers/field như vậy. Jackson hỗ trợ serialize `ProblemDetail`, và Spring có cơ chế đưa các extension properties từ map thành các JSON fields.

Tạo thủ công:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, "Book was not found");
problem.setTitle("Book not found");
problem.setProperty("code", "BOOK_NOT_FOUND");
return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
```

Đây là fragment trong handler có imports `ProblemDetail`, `HttpStatus` và `ResponseEntity`. Khi body là `ProblemDetail`, Spring MVC ưu tiên media type `application/problem+json` khi có converter phù hợp; vẫn phải kiểm tra negotiated response trong ứng dụng thật.

### Phân biệt `status`, `detail` và exception message

Không trả trực tiếp `ex.getMessage()` cho mọi lỗi:

- Exception message có thể chứa SQL, path, implementation details, user input, token hoặc stack trace.
- `title` và `code` nên ổn định để client xử lý; `detail` nên hữu ích nhưng an toàn.
- Stack trace và thông tin nội bộ đưa vào **structured logs/tracing**, không phơi ra client.
- Field validation errors nên trả **tên field public** và mã lỗi được chọn, tránh trả lại password/rejectedValue nhạy cảm.
- `ProblemDetail` không có nghĩa tự động bảo đảm localization hay security; ứng dụng cần `MessageSource` và chính sách dữ liệu phù hợp.

## 6. Ví dụ Spring Boot 3.2+ / Spring Framework 6.1+ chạy được

Bài này dùng **Java 17+, Spring Boot 3.2+ với Spring MVC 6.1+**, để có cả hai loại lỗi validation. Ví dụ sử dụng in-memory map, không cần database. Spring Boot 3.x dùng `jakarta.validation.*`.

### 6.1. Dependencies

Trong dự án Spring Boot Gradle đã cấu hình plugin Boot, repositories và Java toolchain, cần:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây chỉ là **mảnh `build.gradle`**; khi dùng Maven thêm các starter tương ứng. `starter-validation` đảm bảo có provider Jakarta Bean Validation; chỉ có annotation API trong classpath thì chưa chứng minh validation hoạt động.

### 6.2. Application và controller

Tạo **`src/main/java/demo/errorhandling/ErrorHandlingApplication.java`**:

```java
package demo.errorhandling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ErrorHandlingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErrorHandlingApplication.class, args);
    }
}

record CreateBookRequest(@NotBlank String title) {}

record BookResponse(long id, String title) {}

class BookMissingException extends RuntimeException {
    BookMissingException(long id) {
        super("Book id " + id + " does not exist");
    }
}

@RestController
@RequestMapping("/api/books")
class BookController {

    private final AtomicLong nextId = new AtomicLong(1000);
    private final ConcurrentMap<Long, BookResponse> books =
            new ConcurrentHashMap<>();

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request) {

        long id = nextId.incrementAndGet();
        BookResponse saved = new BookResponse(id, request.title());
        books.put(id, saved);

        return ResponseEntity
                .created(URI.create("/api/books/" + id))
                .body(saved);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    BookResponse get(@PathVariable long id) {
        BookResponse book = books.get(id);
        if (book == null) {
            throw new BookMissingException(id);
        }
        return book;
    }

    @GetMapping(path = "/page", produces = MediaType.APPLICATION_JSON_VALUE)
    java.util.Map<String, Integer> page(@RequestParam @Min(1) int number) {
        return java.util.Map.of("number", number);
    }
}
```

Đây là **một file Java hợp lệ khi dùng classpath Boot 3**, do chỉ `ErrorHandlingApplication` là public; các kiểu còn lại package-private. Spring Boot component scan từ package `demo.errorhandling` sẽ nhận controller và advice ở phần tiếp theo.

- POST hợp lệ sẽ lưu sách, trả `201 Created` cùng `Location: /api/books/{id}`.
- POST `{"title":""}` bị `@NotBlank` từ chối, thường là `MethodArgumentNotValidException`.
- POST JSON sai cú pháp bị converter từ chối, thường là `HttpMessageNotReadableException`; validation chưa bắt đầu nếu JSON chưa chuyển được thành object.
- GET sách không tồn tại ném exception nghiệp vụ `BookMissingException`.
- GET `/api/books/page?number=0` vi phạm `@Min(1)` trực tiếp trên tham số method. Từ Framework 6.1+, đây là trường hợp native method validation (`HandlerMethodValidationException`) khi controller không đặt `@Validated` ở cấp class.

Ứng dụng dùng map trong bộ nhớ nên mất dữ liệu khi restart, không chia sẻ state giữa nhiều node và không có transaction DB. Trong production, exception nghiệp vụ nên phát sinh từ service/domain sau khi xét repository, authentication và quyền truy cập.

### 6.3. Global advice kế thừa `ResponseEntityExceptionHandler`

Tạo **`src/main/java/demo/errorhandling/ApiErrorAdvice.java`**:

```java
package demo.errorhandling;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiErrorAdvice extends ResponseEntityExceptionHandler {

    // Exception nghiệp vụ riêng: base class không tự biết điều này.
    @ExceptionHandler(BookMissingException.class)
    ResponseEntity<ProblemDetail> bookMissing(BookMissingException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Book was not found");
        problem.setTitle("Book not found");
        problem.setProperty("code", "BOOK_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // Lỗi trên DTO @Valid @RequestBody: override protected hook.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ex.getBody();
        problem.setTitle("Invalid request");
        problem.setProperty("code", "INVALID_FIELDS");

        List<String> fields = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();

        problem.setProperty("fields", fields);
        return handleExceptionInternal(
                ex, problem, headers, status, request);
    }

    // Lỗi cú pháp hoặc kiểu JSON trước khi DTO được bind thành công.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "Request JSON cannot be read");
        problem.setTitle("Malformed request");
        problem.setProperty("code", "INVALID_JSON");

        return handleExceptionInternal(
                ex, problem, headers, status, request);
    }

    // Native method validation từ Spring MVC 6.1+.
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ex.getBody();
        problem.setProperty("code", "METHOD_VALIDATION_FAILED");

        return handleExceptionInternal(
                ex, problem, headers, status, request);
    }
}
```

Các điểm kỹ thuật:

1. `@RestControllerAdvice` là global advice để trả body trực tiếp. `ResponseEntityExceptionHandler` đã có phương thức handler chung cho lỗi MVC được biết đến.
2. `bookMissing(...)` dùng `@ExceptionHandler` **chỉ cho exception domain riêng**. Handler này trả 404 và code ổn định.
3. `handleMethodArgumentNotValid(...)` **override** đúng hook thay vì đăng ký lại cùng exception qua `@ExceptionHandler`; trả danh sách **tên field** đã lỗi, không gửi lại rejected values.
4. `handleHttpMessageNotReadable(...)` trả detail cố định an toàn; **không truyền `ex.getMostSpecificCause().getMessage()` cho client** vì có thể lộ dữ liệu không nên công khai.
5. `handleHandlerMethodValidationException(...)` **giữ nguyên `status` do Spring quyết định**. Điều này quan trọng: lỗi input thường là 400, nhưng vi phạm return value có thể là 500; ép 400 cho cả hai sẽ che giấu bug.
6. Mọi protected override truyền tiếp qua `handleExceptionInternal(...)` để giữ quy trình chung của base class.
7. `ProblemDetail` do `ex.getBody()` cung cấp có status/detail từ exception. Việc chỉnh title/extension là có chủ đích; không tự đặt status mới không khớp với HTTP response.

**Không khai báo** `@Validated` ở cấp class `BookController` trong ví dụ này. Với Spring MVC 6.1+, nếu đặt ở cấp class, method validation có thể được thực hiện qua AOP proxy với đường exception khác native MVC. `@Validated` vẫn có vai trò hợp lệ trong service layer và trên tham số DTO để chọn nhóm.

### 6.4. Chạy thử với curl

Chạy:

```bash
./gradlew bootRun
```

Ở terminal Bash khác:

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Spring MVC"}'

curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":""}'

curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":'

curl -i 'http://localhost:8080/api/books/999999'
curl -i 'http://localhost:8080/api/books/page?number=0'
```

Kỳ vọng status trong ứng dụng mẫu, không có filter/security thay đổi hành vi:

| Request | Status | Extension `code` trong body nếu có |
| --- | --- | --- |
| POST JSON hợp lệ | 201 Created | Không phải body ProblemDetail; body là `BookResponse` |
| POST `title=""` | 400 Bad Request | `INVALID_FIELDS`; `fields` chứa `title` |
| POST JSON lỗi cú pháp | 400 Bad Request | `INVALID_JSON` |
| GET ID không tồn tại | 404 Not Found | `BOOK_NOT_FOUND` |
| GET `number=0` | 400 Bad Request | `METHOD_VALIDATION_FAILED` |
| GET `number=2` | 200 OK | JSON `{"number":2}` |

**Không dựa vào thứ tự field JSON hay thứ tự constraint violations**. Nếu bật Spring Security, client có thể nhận 401/403 trước khi vào MVC. Nếu custom filter thay đổi body/status, cần điều chỉnh kiểm thử theo hệ thống thật.

## 7. Kiểm thử tích hợp với `MockMvc`

Trong dự án ví dụ, tạo **`src/test/java/demo/errorhandling/ErrorHandlingApplicationTests.java`**:

```java
package demo.errorhandling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void successfulCreationReturns201() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Spring MVC"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Spring MVC"));
    }

    @Test
    void invalidDtoUsesOverriddenHook() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FIELDS"))
            .andExpect(jsonPath("$.fields[0]").value("title"));
    }

    @Test
    void malformedJsonGetsSafeProblemDetail() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void domainExceptionGets404() throws Exception {
        mvc.perform(get("/api/books/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
            .andExpect(jsonPath("$.title").value("Book not found"));
    }

    @Test
    void methodValidationUsesSeparateHook() throws Exception {
        mvc.perform(get("/api/books/page").param("number", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("METHOD_VALIDATION_FAILED"));

        mvc.perform(get("/api/books/page").param("number", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.number").value(2));
    }
}
```

Chạy:

```bash
./gradlew test
```

Với Spring Framework 6.1+/Boot 3.2+ tương thích, `spring-boot-starter-validation` và các class như trên, **năm test được thiết kế để pass**. Đây là kỳ vọng phân tích từ mã mẫu; bài không tuyên bố đã thực thi Gradle.

Bộ test chứng minh **đường error handling end-to-end bên trong MVC**. Để khẳng định body luôn là `application/problem+json`, thêm assertion `contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)` nếu đó là hợp đồng API được công bố và Spring phiên bản đó hỗ trợ media type. Không yêu cầu mọi API lỗi phải dùng JSON khi client chỉ chấp nhận HTML.

Những kiểm thử khác nên có: thiếu query parameter, JSON sai kiểu, 405/406/415, method validation return value và tương tác với Spring Security. Đặc biệt cần kiểm tra có rule global advice nào khác vô tình “cướp” lỗi cùng loại không.

## 8. Chọn lớp nào cho từng loại tùy chỉnh?

| Nhu cầu | Công cụ phù hợp | Lý do |
| --- | --- | --- |
| Một controller cần cách xử lý lỗi cục bộ | `@ExceptionHandler` trong controller | Đơn giản, scope hẹp |
| Toàn REST API dùng schema lỗi chung | `@RestControllerAdvice` | Xử lý tập trung |
| Muốn custom lỗi chuẩn MVC (JSON malformed, validation, unsupported method...) | Kế thừa `ResponseEntityExceptionHandler` | Có hooks và status/headers mặc định |
| Cần ánh xạ exception nghiệp vụ sang HTTP | `@ExceptionHandler(DomainException.class)` trong advice | Rõ status và body |
| REST API cần chuẩn hóa lỗi RFC 9457 | `ProblemDetail`/`ErrorResponse` | JSON schema chuẩn |
| MVC render trang HTML lỗi | `SimpleMappingExceptionResolver` hoặc `@ControllerAdvice` trả view | Gắn với view resolution |
| Thay đổi cơ chế resolve ở hạ tầng MVC | `HandlerExceptionResolver` tùy biến | Kiểm soát sâu thứ tự/giải quyết lỗi |
| Exception ném trước `DispatcherServlet` từ filter | Cơ chế error handling ở filter/container/security phù hợp | Không mặc nhiên thuộc chuỗi MVC resolver |

**Quy tắc thực hành:** nếu bạn có thể giải quyết bằng `@ExceptionHandler` hoặc override của `ResponseEntityExceptionHandler`, không cần tự viết custom `HandlerExceptionResolver`. Việc tự viết resolver đòi hỏi xử lý response output, thứ tự, null/empty result và trường hợp response đã committed.

## 9. Khi nào tự implement `HandlerExceptionResolver`?

Trong các hệ thống phải can thiệp sâu (ví dụ MVC hỗn hợp HTML/REST, quản lý exception tại tầng framework riêng hoặc tích hợp legacy resolver), có thể tự tạo một resolver. Mẫu sau **chỉ xử lý một exception cụ thể** và trả `null` cho mọi exception khác:

```java
package demo.errorhandling;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

@Component
class LegacyQuotaResolver
        implements HandlerExceptionResolver, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        if (!(ex instanceof LegacyQuotaExceededException)) {
            return null; // Resolver khác có thể tiếp tục xử lý.
        }

        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        return new ModelAndView(); // Đánh dấu đã xử lý; body rỗng.
    }
}

class LegacyQuotaExceededException extends RuntimeException {}
```

Đây là **ví dụ resolver độc lập về cấu trúc** nhưng phải tích hợp cẩn thận với cách Spring MVC xây resolver chain. Chỉ đặt `@Component` không phải bảo đảm thứ tự/đăng ký mong muốn trong mọi cấu hình Boot hay custom MVC; hãy kiểm tra danh sách resolver thực tế. Mẫu trả body rỗng cho `429 Too Many Requests`, chưa có thông tin `Retry-After` hoặc `ProblemDetail`, nên **không xem là endpoint production hoàn chỉnh**.

Nhiều trường hợp tốt hơn viết:

```java
@ExceptionHandler(LegacyQuotaExceededException.class)
ResponseEntity<ProblemDetail> quotaExceeded(
        LegacyQuotaExceededException ex) {

    ProblemDetail p = ProblemDetail.forStatus(
            HttpStatus.TOO_MANY_REQUESTS);
    p.setTitle("Too many requests");
    p.setProperty("code", "QUOTA_EXCEEDED");
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(p);
}
```

Đây là fragment `@RestControllerAdvice`, chưa đặt header `Retry-After` vì cần chính sách quota thực tế. Khi lỗi này thuộc MVC/controller, cách viết advice giúp nhất quán với các exception handler khác và tránh phải trực tiếp ghi Servlet response.

**Nếu cần điều chỉnh resolver chain**, Spring MVC có các hook `WebMvcConfigurer#configureHandlerExceptionResolvers(...)` và `extendHandlerExceptionResolvers(...)`. Trong Spring 6.x, method `configureHandlerExceptionResolvers` với list đã được thêm custom resolver có thể khiến Spring bỏ đăng ký chuỗi default thông thường; còn `extendHandlerExceptionResolvers` phù hợp khi chỉ cần sửa/bổ sung list sẵn có. Trước khi thay thứ tự, đọc đúng Javadoc bản Spring đang chạy và đảm bảo `ExceptionHandlerExceptionResolver` cùng các default resolvers vẫn hiện diện.

## 10. Thứ tự `@ControllerAdvice` và trường hợp “handler không chạy”

Một ứng dụng có thể có nhiều advice:

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class DomainAdvice {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handle(DomainException ex) {
        // ...
    }
}
```

Đây là fragment minh họa, cần import `@Order` và `Ordered`, định nghĩa `DomainException`; **không phải class runnable khi dấu `...` còn trong thân method**.

Các nguyên tắc:

- `@ExceptionHandler` trong controller được xét trước global advice.
- Giữa các global advice, `@Order`, `Ordered` hoặc `@Priority` ảnh hưởng độ ưu tiên. Trong một advice, Spring chọn mapping exception phù hợp nhất với root/cause.
- Một advice ưu tiên cao bắt cause có thể thắng một advice thấp bắt root. Không dựa vào giả định “root luôn thắng bất kể order”.
- Một handler bắt `Exception.class` quá rộng có thể nuốt `ResponseStatusException`, 405/415, lỗi framework hoặc logic riêng và làm mất HTTP status đúng.
- Class kế thừa `ResponseEntityExceptionHandler` vốn đã có handler cho rất nhiều loại exception MVC. Tránh tạo advice thứ hai có catch-all ưu tiên cao mà không đánh giá xung đột.
- Nếu annotation selector như `basePackages`/`assignableTypes` hạn chế advice, controller nằm ngoài vùng đó không được áp dụng advice.
- Với exception được wrap, Spring có thể xét cause chain; kiểm tra root/cause và thứ tự advice trong test thay vì chỉ dựa vào tên exception.
- Nếu handler rethrow exception nguyên bản, việc xử lý có thể tiếp tục qua chuỗi resolution; không nên rethrow hoặc return null tùy tiện khi response đã bị ghi.

Một global advice kế thừa `ResponseEntityExceptionHandler` ở vị trí hợp lý cùng handler domain cụ thể thường dễ bảo trì hơn nhiều advice có `@Order` chồng chéo.

## 11. Lỗi validation: vì sao đôi khi khác exception?

Hai API nhìn tương tự nhưng đường xử lý khác:

```java
// Validate DTO bằng @Valid trên argument:
@PostMapping("/users")
void create(@Valid @RequestBody UserDto body) {}

// Constraint trực tiếp trên method parameter, Spring MVC 6.1+:
@GetMapping("/users/page")
void page(@RequestParam @Min(1) int number) {}
```

Các đoạn trên là fragment trong controller, `UserDto` cần định nghĩa.

- Trường hợp thứ nhất thường gây **`MethodArgumentNotValidException`** khi object DTO không hợp lệ và không có `BindingResult` theo ngay sau tham số.
- Trường hợp thứ hai kích hoạt **native method validation** và có thể gây **`HandlerMethodValidationException`**.
- Khi có constraint trực tiếp trên tham số phương thức (ví dụ `@NotNull` ở method parameter), Spring MVC 6.1+ có thể áp dụng method validation bao trùm cả constraints lồng nhau; lỗi DTO lúc đó có thể đi qua method validation.
- Nếu đặt `@Validated` ở **class level** của controller, method validation có thể do Spring AOP proxy thực hiện, với exception/đường gọi khác. Để tận dụng native method validation, Spring khuyến nghị bỏ class-level `@Validated` khỏi controller trong trường hợp thích hợp.
- Với Spring Framework 6.0 trở xuống, native method validation của MVC từ 6.1 **chưa tồn tại**; cần cấu hình/hành vi AOP tương ứng nếu muốn validate trực tiếp method parameters.
- `BindingResult` phải theo ngay sau argument được validate. Với method validation, controller chỉ có thể chạy nếu mọi lỗi liên quan được xử lý ở các tham số có `Errors`/`BindingResult` thích hợp; vi phạm ở tham số khác vẫn có thể ném exception.

**Không hardcode “mọi exception validation đều là 400”**. Ví dụ vi phạm return value constraint là dấu hiệu server code trả về sai contract và có thể được Spring 6.1+ xử lý là 500. Sử dụng status có sẵn từ `HandlerMethodValidationException` và log tình huống 5xx.

## 12. `ResponseStatusException` và `ErrorResponseException`

Khi muốn controller/service báo lỗi HTTP có chủ đích mà không tạo exception domain riêng, Spring cung cấp `ResponseStatusException`:

```java
throw new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Book was not found");
```

Spring 6+ tích hợp nhiều exception dạng `ErrorResponse`, có thể cung cấp `ProblemDetail` và status. Ví dụ `ResponseStatusException` thường được xử lý bởi resolver phù hợp; khi có `ResponseEntityExceptionHandler` trong advice, tập exception nền tảng của nó có thể nhận trước.

Ưu điểm: dễ dùng cho lỗi đơn giản. Đánh đổi: service/domain có thể bị phụ thuộc vào ngữ nghĩa HTTP nếu ném từ sâu bên trong business layer. Với nghiệp vụ lớn, thường tốt hơn định nghĩa `BookMissingException` độc lập rồi map ở web layer bằng `@ExceptionHandler`.

`ErrorResponseException` trong Spring 6+ cung cấp cơ chế biểu diễn exception cùng `ErrorResponse`/`ProblemDetail`. Không nên xem nó là base class buộc phải kế thừa cho mọi exception nghiệp vụ; hãy chọn theo kiến trúc và hợp đồng lỗi đã thiết kế.

## 13. Header HTTP phải được bảo toàn khi tùy chỉnh lỗi

Một số exception không chỉ có status. Ví dụ:

- **405 Method Not Allowed**: header **`Allow`** có thể liệt kê các HTTP methods được phép.
- **415 Unsupported Media Type**: response có thể mang thông tin media type hỗ trợ theo logic MVC.
- **429 Too Many Requests**: nếu áp dụng rate limiting, `Retry-After` có ích theo policy thực tế.
- **401 Unauthorized**: các challenge **`WWW-Authenticate`** phải được giữ khi giao thức xác thực yêu cầu.
- **413 Content Too Large** hoặc các lỗi container/network: có thể xảy ra trước controller, cần xem layer thực sự sinh response.

Khi override `handleHttpRequestMethodNotSupported`, `handleHttpMediaTypeNotSupported` hoặc những hook tương tự, Spring truyền vào `HttpHeaders headers` cùng `status` đã chọn. **Đừng vô tình đánh rơi header chuẩn** bằng cách dựng response mới bỏ qua `headers`. Sử dụng `handleExceptionInternal(ex, body, headers, status, request)` hoặc xây `ResponseEntity` có headers thích hợp.

Nếu response đã committed (đã flush hoặc truyền một phần body), Spring không thể biến nó một cách đáng tin cậy thành ProblemDetail mới. Các exception khi stream/file download, SSE hoặc async response cần chiến lược riêng.

## 14. Spring Boot `/error` và các resolver MVC là hai tầng khác nhau

Spring Boot Servlet app thường có endpoint lỗi fallback `/error` cùng `BasicErrorController`/`ErrorAttributes` tùy auto-configuration. Nó phục vụ xử lý lỗi qua **Servlet container error dispatch** và những trường hợp không được giải quyết bằng response của MVC resolver/advice.

Đường đi ví dụ:

```text
Controller throws exception
   |
DispatcherServlet HandlerExceptionResolvers
   |
   +-- Advice xây ResponseEntity<ProblemDetail> thành công
   |      -> trả JSON trực tiếp, không cần BasicErrorController
   |
   +-- Exception chưa xử lý hoặc sendError / container error dispatch
          -> có thể vào Spring Boot /error
          -> BasicErrorController / ErrorAttributes / custom error page
```

Do đó cấu hình `server.error.*` trong Spring Boot có thể ảnh hưởng fallback `/error`, nhưng **không nên giả định nó biến đổi body do custom `@ExceptionHandler` đã trả**. Tương tự, việc cấu hình `spring.mvc.problemdetails.enabled` ở một số dòng Boot 3 có thể bật cơ chế lỗi ProblemDetail tích hợp, còn một advice tự trả `ProblemDetail` vẫn có hợp đồng riêng. Xác nhận property và default theo đúng version Boot.

Nếu ứng dụng có hai cách xử lý lỗi, cần test để bảo đảm **schema nhất quán** ở các đường controller, missing handler/static resource, filter/security và container errors. Tuy nhiên, không cố ép mọi lỗi container hoặc stream failure thành một JSON body khi response đã bắt đầu gửi.

## 15. Kết hợp với Spring Security và filter chain

`HandlerExceptionResolver` MVC **không phải cơ chế thay thế hoàn chỉnh cho Spring Security**.

- Spring Security filter xử lý nhiều lỗi authentication/authorization trước khi request đến `DispatcherServlet`; `AuthenticationEntryPoint` và `AccessDeniedHandler` có trách nhiệm tạo 401/403 tương ứng.
- Nếu exception được ném từ một custom Servlet filter trước MVC, global `@RestControllerAdvice` **không tự bắt được**.
- Có thể chủ động tích hợp filter với một bean `HandlerExceptionResolver` được inject để delegate exception, nhưng cần cẩn thận request/response lifecycle, thứ tự, double-write và security semantics. Đây là quyết định hạ tầng có chủ đích, không phải behavior mặc định.
- Chỉ nên đưa public error code và correlation ID an toàn ra response; chi tiết về token, quyền, SQL hoặc stack trace thuộc log nội bộ.
- Nếu reverse proxy/gateway cũng biến đổi status/body, hãy kiểm thử end-to-end để phát hiện lỗi do layer bên ngoài trả.

Ví dụ một client không có quyền GET resource có thể nhận 403 ngay từ Security, dù controller nếu được gọi sẽ ném `BookMissingException` 404. Để xác định trách nhiệm, kiểm tra HTTP status, filter chain và logs với correlation ID.

## 16. Version compatibility và migration

| Spring Framework / Boot thường gặp | Cách xử lý cần chú ý |
| --- | --- |
| Framework 5.x / Boot 2.x | Có `HandlerExceptionResolver` và `ResponseEntityExceptionHandler`; custom signature thường dùng `HttpStatus` thay vì `HttpStatusCode`; Jakarta namespace trong nhiều API còn là `javax.*`; chưa có `ProblemDetail` built-in như Spring 6. |
| Framework 6.0 / Boot 3.0–3.1 | Java 17+, Jakarta namespace, `ProblemDetail` và `ErrorResponse` được bổ sung; `ResponseEntityExceptionHandler` xử lý lỗi theo cơ chế mới. |
| Framework 6.1+ / Boot 3.2+ | Native MVC method validation và `HandlerMethodValidationException`; thêm các tình huống như `NoResourceFoundException` cho static resources. |
| Framework 6.2 / Boot 3.4–3.5 | Đường `ResponseEntityExceptionHandler` hỗ trợ định dạng lỗi RFC 9457 và các protected hooks cần thiết cho ví dụ. |
| Framework 7.x / Boot 4.x | Hạ tầng `HandlerExceptionResolver` và base advice tiếp tục tồn tại; cần kiểm tra lại signatures, error formatting, Jackson 3/Boot module changes khi chuyển code hiện hữu. |

Bản chạy ví dụ nên dùng **Boot 3.4 hoặc 3.5** để có Spring Framework 6.2 tương thích với các protected overrides đã nêu, Java 17+. Nếu ứng dụng override BOM, version thực tế của `spring-webmvc` mới quyết định API cần dùng.

Không tự suy luận rằng copy một advice từ Boot 2 lên Boot 3 sẽ biên dịch: `javax.servlet` đã đổi sang `jakarta.servlet`, tham số `HttpStatus` trong nhiều hooks được thay bằng `HttpStatusCode`, và tập exception framework hỗ trợ đã phát triển.

## 17. Chẩn đoán khi API lỗi không đúng định dạng

### Trường hợp 1: controller ném exception nhưng advice không chạy

Kiểm tra:

1. Exception có thực sự ném **trong `DispatcherServlet`**? Nếu xảy ra ở security/filter hoặc servlet khác, MVC advice không mặc nhiên tham gia.
2. `@RestControllerAdvice` có được component scan? Package của advice có nằm dưới root `@SpringBootApplication` hoặc đã được import?
3. Có `@ExceptionHandler` cục bộ hoặc global advice khác bắt lỗi trước không?
4. Có tự đăng ký `HandlerExceptionResolver` ưu tiên cao và trả non-null quá sớm?
5. Response đã committed hoặc xử lý async/stream khác thường chưa?

### Trường hợp 2: có 400 nhưng body lại là JSON `/error` hoặc HTML

Kiểm tra whether exception đã được resolver xử lý trực tiếp, error dispatch đến container, và content negotiation theo `Accept`. Quan sát header `Content-Type`, `ProblemDetail` content và logs. Trong Boot, cấu hình mặc định `/error` có thể tạo JSON structure khác advice. Đừng chỉ dựa vào status code.

### Trường hợp 3: thêm handler validation bị lỗi ambiguous mapping

Nếu đang `extends ResponseEntityExceptionHandler` mà tự khai báo `@ExceptionHandler(MethodArgumentNotValidException.class)` hoặc `@ExceptionHandler(HttpMessageNotReadableException.class)` trên cùng advice, có thể trùng handler của base class. Hãy **override đúng protected hook**, gỡ handler trùng và viết integration test context starts.

### Trường hợp 4: mọi lỗi thành 500

Rà soát một catch-all handler `@ExceptionHandler(Exception.class)` hoặc custom resolver ép status 500 và đánh rơi thông tin `ErrorResponse`. Bảo toàn `HttpStatusCode` từ các framework exceptions đã biết, và để lỗi nghiệp vụ map theo loại cụ thể. Chỉ dùng 500 cho lỗi phía server thật sự.

### Trường hợp 5: lỗi xảy ra nhưng client nhận 200

Một resolver có thể đã ghi body mà quên set status; một `@ExceptionHandler` trả object bình thường mà không dùng `@ResponseStatus`/`ResponseEntity.status(...)` sẽ không tự chuyển sang mã lỗi mong muốn. Kiểm tra status được set ở layer xử lý exception.

### Trường hợp 6: content negotiation hỏng khi trả ProblemDetail

Nếu client gửi `Accept` chỉ cho một media type không hỗ trợ, Spring có thể không serialize được body ProblemDetail theo ý định. Kiểm tra converters thực tế (Jackson 2 ở Boot 3, Jackson 3 ở Boot 4), `Accept` và những endpoint hỗn hợp HTML/JSON. Không cố ghi JSON vào response đã committed.

## 18. Checklist triển khai production

- [ ] Có bảng ánh xạ rõ **domain errors → HTTP status → public error code**, không leak technical exception names.
- [ ] Có một đường xử lý tập trung cho lỗi Spring MVC thông dụng bằng `ResponseEntityExceptionHandler` hoặc cơ chế tương đương.
- [ ] Các override giữ `status`, `headers`, content negotiation và hành vi 5xx thích hợp.
- [ ] Có `@ExceptionHandler` cụ thể cho lỗi domain; tránh catch-all nuốt lỗi framework.
- [ ] `ProblemDetail`/schema JSON có title, detail, code, traceId ở mức an toàn và có versioning khi contract đổi.
- [ ] Error body không chứa token, password, SQL, path nội bộ hay stack trace.
- [ ] Handler validation đúng theo Framework version, phân biệt `MethodArgumentNotValidException` và `HandlerMethodValidationException`.
- [ ] Filter/Security/container error paths được đánh giá riêng; các lỗi 401/403 không bị MVC advice vô tình thay thế.
- [ ] Có integration tests cho 400, 404, 405, 415, 500 và content negotiation thực tế.
- [ ] Có correlation ID và structured logging riêng cho vận hành; lỗi dự kiến 4xx không bị log full stack trace ồ ạt.
- [ ] Các lỗi chưa xử lý vẫn được log và giám sát; không che tất cả thành `200 OK`.
- [ ] Sau nâng cấp Spring/Boot, rà soát protected override signatures và resolver ordering.

## 19. Bài tập tự kiểm tra

1. Trình bày khác biệt giữa `HandlerExceptionResolver` và `ResponseEntityExceptionHandler`. Class nào được `DispatcherServlet` gọi trực tiếp, và class nào được `ExceptionHandlerExceptionResolver` gọi gián tiếp?
2. Một custom resolver gặp exception không biết thì nên trả `null` hay `new ModelAndView()`? Hãy giải thích tác động tới resolver tiếp theo.
3. Khi `@Valid @RequestBody` thất bại và bạn kế thừa `ResponseEntityExceptionHandler`, vì sao cần override `handleMethodArgumentNotValid` thay vì thêm `@ExceptionHandler(MethodArgumentNotValidException.class)`?
4. Spring Framework 6.1+, vì sao `@RequestParam @Min(1) int page` có thể đi qua `HandlerMethodValidationException`, trong khi `@Valid @RequestBody UserDto` thường đi qua `MethodArgumentNotValidException`?
5. Xây dựng một `@RestControllerAdvice` map `OrderMissingException` sang 404 và `OrderConflictException` sang 409 bằng `ProblemDetail` với `code` ổn định.
6. Nếu một exception handler 405 làm mất header `Allow`, điều gì có thể bị ảnh hưởng? Hãy giữ `headers` do Spring chuyển vào protected hook.
7. Một custom filter ném lỗi trước `DispatcherServlet`, còn global `@RestControllerAdvice` không chạy. Đâu là layer phù hợp để xử lý và vì sao?
8. Ứng dụng có hai advice: advice ưu tiên cao bắt cause `IOException`, advice thấp bắt root `IllegalStateException`. Advice nào có thể thắng? Giải thích theo thứ tự và matching.
9. Hãy viết test chứng minh `ProblemDetail` validation không chứa rejected password nhưng có public error code và field name.
10. Nâng Boot 2 lên Boot 3: những imports/chữ ký nào phải đổi khi override `handleMethodArgumentNotValid`, và tại sao không thể bê nguyên cơ chế cũ?

## 20. Tài liệu chính thức

- [Spring Framework — Servlet MVC Exception Resolvers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html): `HandlerExceptionResolver`, chuỗi resolver và semantics của `ModelAndView`.
- [Spring Framework — `@ExceptionHandler`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html): mapping ngoại lệ, root/cause và return values.
- [Spring Framework — `@ControllerAdvice`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html): global scope và độ ưu tiên.
- [Spring Framework — REST Exceptions and Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html): `ProblemDetail`/`ErrorResponse` và `ResponseEntityExceptionHandler`.
- [Spring Framework 6.2 — ResponseEntityExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html): các protected override và signatures.
- [Spring Framework 6.2 — HandlerExceptionResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/HandlerExceptionResolver.html): contract resolver.
- [Spring Framework — MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html): phân biệt `MethodArgumentNotValidException` và `HandlerMethodValidationException`.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html): semantics và các members chuẩn của problem response.
- [Spring Boot — Error Handling](https://docs.spring.io/spring-boot/reference/web/servlet.html): `/error`, `BasicErrorController` và error fallback.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-005">ProblemDetail, ErrorResponse và định dạng lỗi theo RFC 9457</a>

<details>
<summary>Xem chi tiết</summary>

# ProblemDetail, ErrorResponse và định dạng lỗi theo RFC 9457 trong Spring MVC

## 1. Vấn đề: tại sao REST API cần một hợp đồng lỗi thống nhất?

Một API thành công có thể trả DTO JSON rõ ràng nhưng lại trả nhiều kiểu lỗi không nhất quán:

```json
{"error":"not_found"}
```

```json
{"message":"Cannot find order","timestamp":"2026-10-21T10:00:00Z"}
```

```json
{"success":false,"data":null,"code":404}
```

Client phải viết nhiều nhánh xử lý và không biết đâu là thông tin dành cho máy, đâu là lời giải thích dành cho người. Việc trả HTTP `200 OK` nhưng đặt `"code":404` trong body còn làm sai HTTP semantics: bộ nhớ đệm, monitoring và client HTTP có thể hiểu đây là thành công.

**RFC 9457 — Problem Details for HTTP APIs** (xuất bản tháng 7/2023, thay thế RFC 7807) đề xuất một biểu diễn lỗi máy đọc được, dựa trên HTTP status và các field chung. Spring Framework 6.x hỗ trợ trực tiếp bằng:

- `org.springframework.http.ProblemDetail` — đối tượng biểu diễn một problem.
- `org.springframework.web.ErrorResponse` — hợp đồng để exception mô tả status, headers và `ProblemDetail`.
- `org.springframework.web.ErrorResponseException` — lớp exception nền thuận tiện triển khai `ErrorResponse`.
- `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler` — base class cho `@ControllerAdvice` xử lý các lỗi thường gặp trong Spring MVC thành error response.

Bài này tập trung vào **Spring Web MVC (Servlet stack)**, không phải Spring WebFlux. Phần ví dụ cố định **Java 17, Spring Boot 3.3.5 và Spring Framework 6.1.x**, tránh dùng nhầm extension points chỉ xuất hiện ở những phiên bản mới hơn.

Mục tiêu học:

1. Hiểu ý nghĩa chuẩn RFC 9457, các field và media type `application/problem+json`.
2. Biết khi nào dùng `ProblemDetail`, `ErrorResponse` và `ErrorResponseException`.
3. Xử lý lỗi nghiệp vụ, lỗi validation và các lỗi MVC thường gặp theo một dạng thống nhất.
4. Dùng `@RestControllerAdvice` / `ResponseEntityExceptionHandler` một cách có phạm vi, không làm mất HTTP status.
5. Thiết kế extensions như `code`, `errors`, `traceId` an toàn, ổn định, dễ quốc tế hóa và kiểm thử.
6. Nhận biết giới hạn: không phải mọi exception, filter, security response hay streaming error đều tự động thành Problem Detail.

## 2. Đọc một response theo RFC 9457

Ví dụ một client hỏi về đơn hàng không tồn tại:

```http
GET /api/orders/999 HTTP/1.1
Host: localhost:8080
Accept: application/problem+json, application/json
```

Response theo hợp đồng minh họa:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://api.example.com/problems/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "No order exists with id 999",
  "instance": "/api/orders/999",
  "code": "ORDER_NOT_FOUND"
}
```

Các field chuẩn:

| Field | Kiểu JSON | Ý nghĩa cần ghi nhớ |
| --- | --- | --- |
| `type` | URI string | Định danh **loại vấn đề**: cùng loại lỗi nên dùng cùng một URI ổn định |
| `title` | String | Mô tả ngắn, dễ đọc về **loại lỗi**; không nên đổi tùy từng occurrence, ngoại trừ ngôn ngữ |
| `status` | Number | Mã trạng thái HTTP của occurrence khi problem được tạo; **actual HTTP status vẫn là thông tin giao thức quan trọng nhất** |
| `detail` | String | Giải thích cụ thể cho **lần xảy ra lỗi**, giúp client/người dùng hiểu |
| `instance` | URI string | Xác định **lần xảy ra cụ thể**; thường là request path hoặc URI do hệ thống cấp |

RFC 9457 không bắt buộc mọi JSON problem chứa đủ cả năm field trong mọi trường hợp. `type` mặc định mang nghĩa `about:blank` khi không khai báo; `status` hữu ích nhất khi cùng giá trị với HTTP response status; `title` có thể mặc định từ HTTP reason phrase với `about:blank`. Các nội dung như `detail` và `instance` có thể không xuất hiện nếu không được cung cấp.

**`type` khác `instance`:** `type` cho biết lỗi thuộc *loại gì* (ví dụ sản phẩm không tồn tại), còn `instance` cho biết *lần gặp lỗi nào* (ví dụ request `/api/orders/999`). Với URI `https://api.example.com/problems/order-not-found`, tên miền `example.com` chỉ để **minh họa**; trong hệ thống thật nên dùng URI do tổ chức kiểm soát, ổn định và lý tưởng là có trang tài liệu giải thích cách xử lý.

### Extension members không phải field chuẩn

`code` trong JSON trên là **extension** của ứng dụng. RFC cho phép thêm tên/value tùy theo problem type, ví dụ:

```json
{
  "type": "https://api.example.com/problems/validation-error",
  "title": "Invalid request",
  "status": 400,
  "detail": "Some submitted fields are invalid",
  "errors": [
    {"field":"quantity","message":"must be greater than or equal to 1"},
    {"field":"sku","message":"must not be blank"}
  ]
}
```

Ở đây `errors` cũng là extension, không phải schema validation chính thức bắt buộc từ RFC. Khi muốn client dựa trên `code` hoặc `errors`, server phải tài liệu hóa kiểu, ý nghĩa và tính ổn định của chúng. Với lỗi trên object JSON lồng nhau, có thể dùng đường dẫn theo **JSON Pointer** như `"/customer/address/postalCode"` thay vì chỉ một tên field không phân biệt được nested location.

### Media type và HTTP status

- JSON representation chuẩn dùng **`application/problem+json`**.
- RFC cũng xác định representation XML `application/problem+xml` khi dùng XML, nhưng Spring Boot Web mặc định không nhất thiết có XML converter; không cam kết XML chỉ vì RFC định nghĩa.
- Với `400`, `404`, `409`, `422`, `500`, **header/status line thực** không được mâu thuẫn với body `status`.
- Không biến mọi lỗi thành `200` chỉ để frontend dễ parse. API client nên kiểm tra status trước rồi đọc body.
- `Problem Details` thường dùng cho lỗi 4xx/5xx, nhưng RFC không giới hạn máy móc chỉ cho những mã này. Không dùng nó để thay thế mọi representation thành công.

## 3. Ba abstraction của Spring: mối quan hệ và lựa chọn

### 3.1. ProblemDetail là dữ liệu của một lỗi

`ProblemDetail` không phải một exception. Đây là object chứa thông tin RFC; có các factory và setter:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND, "No order exists with id 999");
problem.setType(URI.create(
        "https://api.example.com/problems/order-not-found"));
problem.setTitle("Order not found");
problem.setInstance(URI.create("/api/orders/999"));
problem.setProperty("code", "ORDER_NOT_FOUND");
```

Đây là snippet Java cần imports `java.net.URI`, `org.springframework.http.HttpStatus` và `org.springframework.http.ProblemDetail`. Khi được trả từ `@ExceptionHandler` trong MVC, Spring biết render nó và đặt HTTP status phù hợp từ `ProblemDetail.status`. Nếu chưa gán `instance`, Spring MVC có thể đặt request path khi render `ProblemDetail`.

`ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)` tạo lỗi chỉ với status và các defaults. `ProblemDetail.forStatusAndDetail(...)` tiện khi đã có thông điệp ngắn cho client. Lưu ý `detail` **không phải** chỗ đổ nguyên `exception.getMessage()` từ database, stack trace, tokens hoặc câu SQL.

#### Dynamic properties trong JSON

`problem.setProperty("code", "ORDER_NOT_FOUND")` lưu extension trong một `Map` của `ProblemDetail`. Khi dùng Jackson với Spring MVC, `ProblemDetailJacksonMixin` đưa extension lên **top-level JSON** như `"code"`, không tạo `"properties":{"code":...}`. Tránh extension trùng tên các field chuẩn (`type`, `title`, `status`, `detail`, `instance`) vì gây mơ hồ.

### 3.2. ErrorResponse là hợp đồng cho exception có thông tin HTTP

`ErrorResponse` là interface ở package `org.springframework.web`, cho phép Spring xem exception như một phản hồi HTTP có cấu trúc. Các member cốt lõi:

```java
HttpStatusCode getStatusCode();
HttpHeaders getHeaders();
ProblemDetail getBody();
```

Các chữ ký trên rút gọn để giải thích, không phải file interface độc lập có imports đầy đủ. Interface còn có tiện ích và các message codes giúp xử lý quốc tế hóa.

Nhiều exception của Spring MVC, chẳng hạn loại lỗi argument/validation hoặc media type, mang `ErrorResponse` và có thể được chuyển thành `ProblemDetail` thông qua hệ xử lý exception của MVC. Điều đó **không có nghĩa** chỉ cần ném mọi `RuntimeException` là tự động có thông điệp RFC an toàn; exception tùy ý vẫn cần chính sách map.

### 3.3. ErrorResponseException là một cách chủ động ném lỗi

`ErrorResponseException` (Spring Framework từ 6.0) là `RuntimeException` implement `ErrorResponse`. Ví dụ:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "This order cannot be placed while the product is locked.");
problem.setType(URI.create(
        "https://api.example.com/problems/product-locked"));
problem.setTitle("Product locked");
problem.setProperty("code", "PRODUCT_LOCKED");

throw new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
```

Đây là **snippet** trong một method, không phải chương trình `main`. Exception đã mang status `409 Conflict` và body problem. Khi có `ResponseEntityExceptionHandler` hoạt động, exception này được xử lý theo RFC. Constructor nhận `status, ProblemDetail, cause` có sẵn ở Spring 6.1.

Không sử dụng `ErrorResponseException` để ném bừa mọi lỗi: nếu cần một domain exception riêng dễ hiểu, hãy khai báo `OrderNotFoundException` rồi map ở `@ExceptionHandler`; cả hai đều hợp lệ tùy thiết kế.

### 3.4. ResponseEntityExceptionHandler là cơ sở tái sử dụng

`ResponseEntityExceptionHandler` có các method xử lý nhiều exception do Spring MVC tạo ra. Subclass dùng `@ControllerAdvice` hoặc `@RestControllerAdvice` để:

- Giữ cách xử lý tương đối thống nhất cho `400`, `405`, `406`, `415` và các lỗi MVC khác.
- Override một handler cụ thể, ví dụ `handleMethodArgumentNotValid`, để bổ sung `errors`.
- Tạo `ProblemDetail` tùy biến và điều chỉnh logic xây `ResponseEntity`.
- Kết hợp `MessageSource` nhằm lấy message theo locale.

Đối với Spring MVC 6.1, đây là base class thích hợp để chuẩn hóa lỗi đến từ framework. Không coi nó là “bắt mọi `Exception` không phân biệt”: chưa bắt các lỗi nghiệp vụ custom trừ khi bạn cung cấp handler/kiểu phù hợp; các lỗi xảy ra **ngoài DispatcherServlet** (như phần của Spring Security filter chain) cũng có con đường xử lý khác.

## 4. Luồng lỗi đi qua Spring MVC

```text
HTTP request
    |
    v
DispatcherServlet / HandlerMapping
    |
    v
Handler method / argument resolution / validation
    |
    +---- thành công -----------------> controller response
    |
    +---- lỗi: exception
                  |
                  v
        HandlerExceptionResolver chain
                  |
          +-------+--------------------------+
          |                                  |
   @ExceptionHandler ở controller     @ControllerAdvice
          |                            / ResponseEntityExceptionHandler
          |                                  |
          +--------------+-------------------+
                         |
                         v
                 ProblemDetail / ResponseEntity
                         |
                         v
                   HttpMessageConverter
                         |
                         v
             HTTP 4xx/5xx + application/problem+json
```

Đường đi trên là mô hình khái quát. Các resolver của Spring có thứ tự; `@ExceptionHandler` gần controller và advice có điều kiện match/order. Một `@ControllerAdvice` quá rộng hoặc có nhiều handler trùng nhau có thể làm exception bị giải quyết khác dự kiến. Khi response đã commit, framework có thể không còn thay status/body được nữa.

| Nguồn lỗi | Ví dụ | Status điển hình | Cách xử lý |
| --- | --- | --- | --- |
| JSON body sai cú pháp | `{"sku":` | `400` | `HttpMessageNotReadableException` |
| Bean Validation thất bại | `quantity=0` với `@Min(1)` | `400` | `MethodArgumentNotValidException` cho `@Valid @RequestBody` |
| Route/method không hỗ trợ | `PUT` vào endpoint chỉ `POST` | `405` | `HttpRequestMethodNotSupportedException` |
| Không nhận media type | `text/plain` gửi vào JSON endpoint | `415` | `HttpMediaTypeNotSupportedException` |
| Client không chấp nhận output | `Accept: application/xml` nhưng chỉ trả JSON | `406` | `HttpMediaTypeNotAcceptableException` |
| Tài nguyên nghiệp vụ không tồn tại | Order id không có | `404` | Custom exception + `@ExceptionHandler` |
| Trạng thái nghiệp vụ xung đột | Product đang locked | `409` | `ErrorResponseException`/domain mapping |
| Lỗi hệ thống ngoài dự kiến | DB timeout, NPE | `500` | Logging nội bộ; body an toàn, không lộ stack |

**Lưu ý:** status cụ thể có thể chịu ảnh hưởng bởi annotations, custom exception handlers và cấu hình. Ví dụ `422 Unprocessable Content` đôi khi được API dùng cho validation thay vì `400`; chọn đúng semantics theo hợp đồng và giữ nhất quán. Không mặc định nói `@Valid` luôn trả `422` hoặc mọi controller luôn trả JSON RFC khi chưa cấu hình.

### Spring Boot 3.3 và lựa chọn bật problem details

Trong Spring Boot 3.3, thuộc tính:

```properties
spring.mvc.problemdetails.enabled=true
```

cho phép auto-configuration xử lý các lỗi MVC được hỗ trợ thành problem details. **Giá trị mặc định của thuộc tính ở Boot 3.3 là `false`**; không được giả định Boot tự bật chỉ vì project có `spring-boot-starter-web`.

Có hai hướng triển khai phổ biến:

- **Hướng A:** bật property trên và thêm advice khác cho exception nghiệp vụ, chú ý thứ tự của advice khi tùy biến built-in errors.
- **Hướng B:** tự tạo một `@RestControllerAdvice extends ResponseEntityExceptionHandler` để xử lý các lỗi MVC và custom errors tập trung.

**Ví dụ chạy ở phần 5 chọn hướng B**, vì vậy **không bật** property trên trong `application.properties`. Tránh tạo nhiều bean xử lý cùng nhóm lỗi với cùng độ ưu tiên rồi giả định chúng tự phối hợp; cần xác định chủ sở hữu chính sách lỗi và kiểm tra thực tế.

## 5. Ví dụ hoàn chỉnh: Java 17 + Spring MVC + RFC 9457

Ứng dụng demo xử lý đơn hàng:

- `POST /api/orders` với JSON `{"sku":"BOOK-1","quantity":2}` trả `201 Created`.
- `sku="MISSING"` ném custom `ProductNotFoundException`, map sang `404` và mã `PRODUCT_NOT_FOUND`.
- `sku="LOCKED"` ném `ErrorResponseException`, trả `409` và mã `PRODUCT_LOCKED`.
- `quantity=0` hoặc `sku=""` gây Bean Validation `400`, trả extension `errors`.
- JSON malformed gây lỗi converter, được base handler xử lý thành RFC response.

Không có database, authentication, giao dịch hay persistence; các lỗi là **giả lập có kiểm soát** để nhìn rõ vòng đời HTTP.

### 5.1. Cấu trúc Maven

```text
problem-details-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/demo/problems/
    │       ├── ProblemDetailsApplication.java
    │       ├── OrderController.java
    │       ├── ProductNotFoundException.java
    │       └── ApiErrorAdvice.java
    └── test/
        └── java/demo/problems/
            └── ApiErrorContractTest.java
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
    <artifactId>problem-details-demo</artifactId>
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

**`src/main/java/demo/problems/ProblemDetailsApplication.java`**

```java
package demo.problems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProblemDetailsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProblemDetailsApplication.class, args);
    }
}
```

### 5.2. Domain exception tách biệt HTTP

**`ProductNotFoundException.java`**

```java
package demo.problems;

public class ProductNotFoundException extends RuntimeException {
    private final String sku;

    public ProductNotFoundException(String sku) {
        super("Product not found");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
```

Exception này không biết HTTP status hay JSON schema. `ApiErrorAdvice` quyết định khi nó đi qua boundary REST thì status `404` và problem type nào được dùng. Với ứng dụng nhiều transport (REST, batch job, messaging), giữ domain exception tách với Spring Web có thể giúp tái sử dụng.

### 5.3. Controller tạo dữ liệu thành công và hai loại lỗi nghiệp vụ

**`OrderController.java`**

```java
package demo.problems;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record CreateOrderRequest(
            @NotBlank String sku,
            @Min(1) int quantity) {}

    public record OrderReceipt(long orderId, String sku, int quantity) {}

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderReceipt> create(
            @Valid @RequestBody CreateOrderRequest input) {

        if ("MISSING".equals(input.sku())) {
            throw new ProductNotFoundException(input.sku());
        }

        if ("LOCKED".equals(input.sku())) {
            ProblemDetail body = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "This product cannot be ordered while locked.");
            body.setType(URI.create(
                    "https://api.example.com/problems/product-locked"));
            body.setTitle("Product locked");
            body.setProperty("code", "PRODUCT_LOCKED");
            throw new ErrorResponseException(
                    HttpStatus.CONFLICT, body, null);
        }

        OrderReceipt receipt = new OrderReceipt(
                1001L, input.sku(), input.quantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receipt);
    }
}
```

- `@Valid @RequestBody`: JSON converter đọc request thành record, sau đó validation kiểm tra các constraints.
- `@Min(1)` trên `int quantity`: `0` là giá trị không hợp lệ, nhưng **field vắng mặt** có thể trở thành `0` vì primitive default; ở API thực cần tách missing và zero nếu contract quan tâm (dùng `Integer` + `@NotNull`).
- Hai nhánh `MISSING` và `LOCKED` mô phỏng các trạng thái để test. Trong production phải kiểm tra quyền sở hữu, tồn tại và trạng thái tại tầng service/database.
- `ResponseEntity<OrderReceipt>` của nhánh thành công không dùng RFC error schema; response là JSON DTO thường.
- `ErrorResponseException` chỉ tạo khi đã có tình huống HTTP `409` rõ ràng. Nó không nên chứa thông tin nội bộ nhạy cảm.

### 5.4. Advice: custom 404 và extension errors cho Bean Validation

**`ApiErrorAdvice.java`**

```java
package demo.problems;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail notFound(
            ProductNotFoundException exception,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Requested product was not found.");
        problem.setType(URI.create(
                "https://api.example.com/problems/product-not-found"));
        problem.setTitle("Product not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "PRODUCT_NOT_FOUND");

        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "One or more request fields are invalid.");
        problem.setType(URI.create(
                "https://api.example.com/problems/invalid-request"));
        problem.setTitle("Invalid request");
        problem.setProperty("code", "INVALID_REQUEST");

        List<Map<String, String>> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.toString(
                                error.getDefaultMessage(), "Invalid value")))
                .sorted(Comparator.comparing(item -> item.get("field")))
                .toList();

        problem.setProperty("errors", errors);

        return handleExceptionInternal(
                exception, problem, headers, status, request);
    }
}
```

**Giải thích từng lựa chọn:**

- `@RestControllerAdvice` làm các `@ExceptionHandler` trả body JSON; kế thừa `ResponseEntityExceptionHandler` xử lý lỗi framework.
- `notFound` được gọi với exception nghiệp vụ riêng, trả `ProblemDetail`. Spring MVC dùng `problem.status=404` làm HTTP status và ưu tiên `application/problem+json` cho JSON problem.
- `setInstance(URI.create(request.getRequestURI()))` cung cấp request path để nhận diện occurrence; không đưa query string chứa token/password vào instance.
- `handleMethodArgumentNotValid` override đúng chữ ký của Spring 6.1. Trong trường hợp validation đầu vào trên `@RequestBody`, status mặc định thường là `400`.
- `getFieldErrors()` đưa ra lỗi theo field; `sorted(...)` làm mảng ổn định khi test. Dùng `Objects.toString(...,"Invalid value")` để không ném NullPointerException khi default message vắng mặt.
- `handleExceptionInternal(...)` giữ đường xử lý chung của base class thay vì tự dựng mọi thứ từ đầu.
- Snippet chỉ trả **tên field + message**, không đưa `FieldError.getRejectedValue()` vào body, tránh vô ý echo password/PII.
- URI `https://api.example.com/...` trong bài là địa chỉ minh họa. Khi triển khai thật, thay bằng URI do hệ thống sở hữu và tài liệu hóa ổn định.
- Lỗi media type, malformed JSON và một số lỗi MVC khác do các method sẵn có của `ResponseEntityExceptionHandler` giải quyết. Không cần viết `catch (Exception)` cho mọi trường hợp ở controller.

### 5.5. Chạy và quan sát response

Yêu cầu JDK 17+ và Maven. Từ thư mục `problem-details-demo` chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Đây là hai lệnh chạy **tuần tự**, không phải một lệnh. Khi Spring Boot khởi động cổng `8080`, hãy gửi các request dưới đây.

**Thành công — HTTP 201, không phải Problem Detail:**

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"sku":"BOOK-1","quantity":2}'
```

Phần quan trọng của response:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{"orderId":1001,"sku":"BOOK-1","quantity":2}
```

**Không tìm thấy sản phẩm — HTTP 404:**

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"MISSING","quantity":2}'
```

Body minh họa theo mapping chính xác trong example:

```json
{
  "type": "https://api.example.com/problems/product-not-found",
  "title": "Product not found",
  "status": 404,
  "detail": "Requested product was not found.",
  "instance": "/api/orders",
  "code": "PRODUCT_NOT_FOUND"
}
```

Đường `instance` là `/api/orders` do `HttpServletRequest.getRequestURI()` trong advice. Thứ tự key và indentation ở trên chỉ phục vụ đọc; JSON serializer thường trả một dòng.

**Sản phẩm bị khóa — HTTP 409:**

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"LOCKED","quantity":2}'
```

Kết quả mong đợi gồm status `409 Conflict`, `Content-Type: application/problem+json`, và body có:

```json
{
  "type": "https://api.example.com/problems/product-locked",
  "title": "Product locked",
  "status": 409,
  "detail": "This product cannot be ordered while locked.",
  "instance": "/api/orders",
  "code": "PRODUCT_LOCKED"
}
```

`instance` có thể được Spring gán theo request path khi body problem chưa có instance. Không suy luận HTTP headers khác, thứ tự key hoặc JSON whitespace là contract.

**Validation lỗi — HTTP 400 cùng mảng errors:**

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"sku":"","quantity":0}'
```

Ví dụ kết quả:

```json
{
  "type": "https://api.example.com/problems/invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/orders",
  "code": "INVALID_REQUEST",
  "errors": [
    {
      "field": "quantity",
      "message": "must be greater than or equal to 1"
    },
    {
      "field": "sku",
      "message": "must not be blank"
    }
  ]
}
```

Thông điệp `message` thực tế do Bean Validation provider và locale quyết định; phần body minh họa ngôn ngữ tiếng Anh thường gặp. Contract test nên kiểm tra **tên field, mã lỗi và HTTP status**, tránh phụ thuộc wording cụ thể nếu ứng dụng hỗ trợ i18n.

**JSON hỏng — HTTP 400 nhưng không nhất thiết cùng `errors`:**

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  --data-binary '{"sku":'
```

Spring có thể ném `HttpMessageNotReadableException` ở bước parse body, trước Bean Validation. Base class sẽ xử lý lỗi này theo behavior của framework, nên response thường là `400` với problem body nhưng **không có mảng `errors` do override validation ở trên**. Không tự chế tên field khi JSON hỏng và chưa tạo được DTO.

### 5.6. Kiểm thử hợp đồng lỗi với MockMvc

**`src/test/java/demo/problems/ApiErrorContractTest.java`**

```java
package demo.problems;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractTest {

    @Autowired
    MockMvc mvc;

    @Test
    void validRequestReturns201() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"BOOK-1\",\"quantity\":2}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.orderId").value(1001))
           .andExpect(jsonPath("$.sku").value("BOOK-1"));
    }

    @Test
    void productNotFoundReturnsProblemDetail() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"MISSING\",\"quantity\":1}"))
           .andExpect(status().isNotFound())
           .andExpect(content().contentTypeCompatibleWith(
                    MediaType.parseMediaType("application/problem+json")))
           .andExpect(jsonPath("$.status").value(404))
           .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
           .andExpect(jsonPath("$.instance").value("/api/orders"));
    }

    @Test
    void lockedProductReturnsConflict() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"LOCKED\",\"quantity\":1}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.status").value(409))
           .andExpect(jsonPath("$.code").value("PRODUCT_LOCKED"));
    }

    @Test
    void invalidRequestReportsFields() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"\",\"quantity\":0}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
           .andExpect(jsonPath("$.errors[0].field").value("quantity"))
           .andExpect(jsonPath("$.errors[1].field").value("sku"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":"))
           .andExpect(status().isBadRequest());
    }
}
```

Chạy kiểm thử:

```bash
mvn test
```

Bộ test có **5 test methods**. Nếu setup đúng và tất cả đều pass, Maven kết thúc exit code `0`; dòng tổng kết có thể hiển thị `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`, còn thời gian/log phụ thuộc môi trường.

`MockMvc` kiểm thử response thông qua MVC framework mà không cần server port thật. Tuy nhiên contract tests cần bổ sung trường hợp media type `415`, `405`, `404` của resource khác, i18n, quyền truy cập, lỗi không mong đợi và headers tùy contract. Tránh test toàn bộ lỗi bằng so sánh raw JSON string, vì thứ tự JSON object keys không phải ngữ nghĩa của RFC.

## 6. @ExceptionHandler, @RestControllerAdvice và lựa chọn phạm vi

Một handler cục bộ có thể dành cho riêng controller:

```java
@ExceptionHandler(ProductNotFoundException.class)
public ProblemDetail notFound(ProductNotFoundException ex) {
    ProblemDetail p = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    p.setTitle("Product not found");
    return p;
}
```

Đây là snippet cần đặt trong controller/advice cùng imports. Nó không phải file chạy độc lập. Nếu chỉ một controller có error contract đặc thù, local handler có thể đủ.

Khi có nhiều controller, dùng `@RestControllerAdvice` cho một tập API hoặc module. Một số tham số selector của `@ControllerAdvice`:

- `basePackages` hoặc `basePackageClasses` — giới hạn controller theo package.
- `assignableTypes` — giới hạn controller theo class/interface.
- `annotations` — áp dụng cho controller có annotation nhất định.
- `@Order` — quyết định ưu tiên giữa nhiều advice phù hợp.

Đừng tạo một `@ExceptionHandler(Exception.class)` toàn cục mà trả `400` cho mọi lỗi. NPE, lỗi database hoặc lỗi serialization nội bộ không phải lỗi client. Một handler 500 fallback nếu cần phải **log exception server-side** và trả detail an toàn, không che khuất telemetry/incident. Nếu ứng dụng có Spring Security, các lỗi `AuthenticationException` và `AccessDeniedException` có thể được `AuthenticationEntryPoint`/`AccessDeniedHandler` xử lý trong filter chain, không chắc đi qua cùng MVC advice.

### @ResponseStatus và ResponseStatusException

`@ResponseStatus(HttpStatus.NOT_FOUND)` trên exception hoặc controller method có thể điều khiển status, nhưng không tự thiết kế đầy đủ RFC error schema. `ResponseStatusException` là một loại `ErrorResponseException` có thể hữu ích cho một trạng thái HTTP cụ thể; vẫn cần cân nhắc định nghĩa `type`, `code` và thông tin công khai. Với domain exception như `ProductNotFoundException`, handler riêng thường diễn đạt hợp đồng rõ ràng hơn.

### Tránh xung đột khi bật Spring Boot problem details

Nếu `spring.mvc.problemdetails.enabled=true`, Boot có thể đăng ký advice của chính nó để xử lý nhiều lỗi built-in. Khi muốn override behavior một nhóm exception này bằng advice khác, phải xác định thứ tự (`@Order`) và cách chọn exception handler. Trong ví dụ ở phần 5 đã chủ động kế thừa `ResponseEntityExceptionHandler` và không bật property, nên có một nơi sở hữu chính sách MVC errors của ứng dụng.

## 7. Chọn mã HTTP status đúng với lỗi

| Status | Tình huống thường gặp | Phân biệt quan trọng |
| --- | --- | --- |
| `400 Bad Request` | JSON malformed, field/type không hợp lệ, validation request | Request không phù hợp hợp đồng |
| `401 Unauthorized` | Thiếu hoặc không hợp lệ thông tin xác thực | Thường cần `WWW-Authenticate` phù hợp cơ chế |
| `403 Forbidden` | Đã xác thực nhưng không đủ quyền | Không đổi thành 200; tránh lộ chi tiết policy |
| `404 Not Found` | Resource không có hoặc không tiết lộ sự tồn tại | Có thể cần tránh lộ enumeration |
| `405 Method Not Allowed` | Dùng HTTP method không được route hỗ trợ | HTTP `Allow` header có ý nghĩa |
| `406 Not Acceptable` | Không tạo được representation client chấp nhận | Phân biệt từ `Content-Type` của request |
| `409 Conflict` | Xung đột trạng thái hiện tại của resource | Không phải tất cả validation errors |
| `415 Unsupported Media Type` | Request body format không hỗ trợ | Ví dụ gửi `text/plain` cho endpoint chỉ nhận JSON |
| `422 Unprocessable Content` | Server hiểu media/type/cú pháp, nhưng không xử lý nội dung theo semantics | Tùy contract phân biệt với `400` |
| `429 Too Many Requests` | Client vượt giới hạn tần suất | Cân nhắc `Retry-After` phù hợp |
| `500 Internal Server Error` | Lỗi server không đoán trước | Detail phải tránh tiết lộ nội bộ |
| `503 Service Unavailable` | Server tạm thời không phục vụ | Có thể dùng `Retry-After` nếu biết |

Không nên viết `status=404` trong JSON trong khi HTTP response thật vẫn `200`. Tương tự, nếu `ResponseEntity` chủ động đặt HTTP status khác `ProblemDetail.status`, cần sửa ngay sự bất nhất.

## 8. Thiết kế problem type và extension thành hợp đồng dài hạn

### 8.1. Chọn URI type ổn định

Một URI như `https://api.example.com/problems/order-not-found` đại diện một **loại lỗi**. Nó không phải occurrence ID và không nên chứa dữ liệu user-specific. Khi sử dụng namespace thực:

1. Công bố mô tả lỗi, status, các extension, nguyên nhân và hướng khắc phục.
2. Giữ URI ổn định qua các bản deploy.
3. Tránh tạo type mới cho từng tham số/sản phẩm/id.
4. Không tái sử dụng cùng URI cho semantics hoàn toàn khác.
5. Nếu thay đổi breaking error schema, cập nhật contract theo chính sách versioning.

RFC 9457 khuyến nghị type URI khi dereference có thể trả tài liệu giúp người đọc hiểu lỗi, nhưng không yêu cầu client phải truy cập mạng vào URI để xử lý response. Client nên map stable type/code theo dữ liệu nó có, không bắt buộc fetch tài liệu ở runtime.

### 8.2. Type, code và title khác nhau như thế nào?

Ví dụ:

```json
{
  "type": "https://api.example.com/problems/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "No order exists with id 999",
  "code": "ORDER_NOT_FOUND"
}
```

- **`type`:** định danh machine-readable theo tinh thần RFC.
- **`code`:** tiện ích tùy chọn của ứng dụng, hữu ích nếu organization đã có error codes và SDK dùng enum. Không bắt buộc bởi RFC.
- **`title`:** nhãn ngắn để con người đọc, có thể được bản địa hóa; không nên dùng title như mã ổn định.
- **`detail`:** nội dung lần xảy ra, có thể thay đổi theo ngôn ngữ/giá trị cụ thể; client không nên parse text message để quyết định hành vi.

Nếu API đã có `type` ổn định và consumer dùng nó, đừng thêm `code` chỉ để lặp lại cùng thông tin mà không có ích. Nếu cần cả hai, định nghĩa consistency rule và test để tránh mismatch.

### 8.3. Validation errors theo trường

RFC 9457 cho phép extension `errors` theo contract của từng problem type; dạng sau hữu ích với form UI:

```json
{
  "type": "https://api.example.com/problems/invalid-request",
  "title": "Invalid request",
  "status": 400,
  "errors": [
    {
      "pointer": "/shippingAddress/postalCode",
      "code": "INVALID_FORMAT",
      "message": "Invalid postal code"
    }
  ]
}
```

`pointer` ở đây là JSON Pointer theo **đề xuất hợp đồng ứng dụng**; không mặc định Spring tự biến `FieldError.getField()` thành JSON Pointer. Với nested Java Bean path như `shippingAddress.postalCode`, phải chuyển đổi escape RFC 6901 cẩn thận; không đơn giản `replace(".", "/")` nếu key có ký tự đặc biệt, dấu slash hoặc mảng.

Nên giữ field errors nhất quán về shape và tránh trả giá trị `rejectedValue` chứa dữ liệu nhạy cảm. Một số constraint trên class-level hoặc cross-field chỉ có global errors, không có field path; nếu hợp đồng cần báo chúng, thiết kế loại extension tương ứng, thay vì âm thầm bỏ qua.

### 8.4. Trace ID và correlation ID

Để người dùng báo lỗi và bộ phận hỗ trợ tra log, có thể bổ sung `traceId` từ hệ thống tracing:

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected server error occurred.",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Đây chỉ là JSON minh họa. `traceId` là extension, không bắt buộc theo RFC. Đừng tự phát sinh “trace ID” tùy tiện từ UUID trong từng exception rồi tuyên bố có thể tra trong log nếu log không gắn cùng ID. Nếu dùng Micrometer Tracing/OpenTelemetry, lấy ID từ active trace/observation và bảo đảm logger, exporter, response liên kết được.

Không trả stack trace, đường dẫn file nội bộ, SQL syntax, user email, access token, connection string hoặc secrets cho client.

## 9. Quốc tế hóa và xử lý thông điệp một cách an toàn

Problem Details hỗ trợ human-readable `title` và `detail` theo ngôn ngữ; client có thể gửi `Accept-Language`. Tuy nhiên `type`, `code` và HTTP status phải giữ **nghĩa ổn định** khi ngôn ngữ thay đổi.

Spring hỗ trợ `MessageSource`. `ResponseEntityExceptionHandler` có thể tra message codes cho `type`, `title` và `detail` của `ErrorResponse`. Mẫu code mặc định thường gồm:

```text
problemDetail.type.<fully-qualified-exception-class>
problemDetail.title.<fully-qualified-exception-class>
problemDetail.<fully-qualified-exception-class>
```

Ví dụ chính sách translation của tổ chức có thể map `INVALID_REQUEST` thành title Việt/Anh. Với custom exceptions và `ProblemDetail` xây thủ công, bạn cần chủ động dùng `MessageSource` hoặc policy riêng, không mong string viết cứng trong controller tự dịch.

Những nguyên tắc nên tuân thủ:

- Đừng để locale đổi `type URI`/`code`.
- Đừng dùng `detail` làm format máy parse cố định; nó có thể chứa văn bản bản địa hóa.
- Message dành cho client khác với log message dành cho dev/ops.
- Client cần hiển thị UX có thể dựa vào `code` hoặc `type` để tự trình bày lời giải thích phù hợp, tránh buộc backend trả thông tin quá chi tiết.
- Không đưa thông điệp từ exception nội bộ hoặc database thẳng vào `detail` nếu chưa kiểm tra an toàn.

## 10. Những giới hạn của ProblemDetail trong triển khai thực tế

### 10.1. Không tự động bao trùm toàn bộ stack

Những lỗi trong Servlet filter, Spring Security, reverse proxy, API gateway, container, servlet asynchronous timeout, SSE/websocket hoặc response stream đã commit có thể bypass MVC advice. Nếu yêu cầu một lỗi JSON thống nhất xuyên lớp, phải phối hợp nhiều tầng và test riêng. Không giả định `@RestControllerAdvice` sẽ bắt exception authentication/authorization từ security filter chain.

### 10.2. Không “bọc” ProblemDetail thêm một envelope lạ

Nếu global `ResponseBodyAdvice` tự biến mọi response thành:

```json
{"success":false,"data":{"type":"...","title":"...","status":400}}
```

thì body đó **không còn là Problem Detail top-level** theo schema `application/problem+json` mà client RFC đang mong đợi. Trường hợp cần envelope riêng phải đổi media type/contract cho phù hợp hoặc loại trừ `ProblemDetail` khỏi wrapper. Với Spring MVC, converter được chọn **trước** hook `ResponseBodyAdvice.beforeBodyWrite`; thay kiểu object tùy tiện có thể gây lỗi serialize.

### 10.3. Chuyển lỗi 500 không nên làm mất telemetry

Một fallback handler toàn cục kiểu:

```java
@ExceptionHandler(Exception.class)
public ProblemDetail fallback(Exception ex) {
    return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected server error occurred.");
}
```

Đây là **snippet minh họa**, không phải file đầy đủ. Nó cho client response an toàn nhưng có thể làm lỗi được xem như đã xử lý, ảnh hưởng logging và observation nếu không có policy rõ. Trong thực tế phải log đầy đủ server-side theo chuẩn không lộ secrets, tích hợp tracing/metrics, và đảm bảo không “nuốt” lỗi mà vận hành cần phát hiện.

### 10.4. Không thay thế validation và authorization

`ProblemDetail` chỉ là **biểu diễn lỗi**. Nếu server chấp nhận request thiếu quyền, trả JSON đẹp không sửa được lỗ hổng. Tương tự, DTO validation phải chạy độc lập; conversion/validation errors chỉ được ánh xạ thành problem sau khi phát hiện. Tránh sử dụng `ProblemDetail` như một kho tùy ý cho dữ liệu lỗi kinh doanh không có HTTP semantics rõ ràng.

### 10.5. Status/body nhất quán với RFC

- Dùng `ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,...)` và trả từ `@ExceptionHandler` thường khiến Spring chọn status tương ứng.
- Nếu dùng `ResponseEntity<ProblemDetail>`, phải bảo đảm `ResponseEntity.status` và `ProblemDetail.status` bằng nhau.
- Media type `application/problem+json` là định dạng JSON chuyên dụng; giữ body object top-level, không trả chuỗi JSON tự ghép.
- Không nên đưa ProblemDetail body vào `204 No Content`, `304 Not Modified` hay phản hồi `HEAD` trái semantics body.
- Error response có thể cần cache control, retry, rate-limit headers; Problem Details không tự tạo mọi header chính sách.

### 10.6. Không nạp các thông tin nhạy cảm vào extensions

`ProblemDetail.setProperty("debug", exception)` hoặc `setProperty("params", request.getParameterMap())` có thể vô tình serialize thông tin nội bộ, PII hoặc secret. Hãy duyệt whitelist field/giá trị an toàn và giới hạn độ dài. Bởi extensions được Jackson đưa lên top-level, việc “gửi thêm cho tiện debug” có tác động công khai trực tiếp.

## 11. Những lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| `400` nhưng body không phải RFC | Boot problem-details chưa bật, không có handler phù hợp | Kiểm tra `spring.mvc.problemdetails.enabled` hoặc advice tự định nghĩa |
| `ProblemDetail` nhưng HTTP status `200` | Trả DTO JSON tùy biến/ResponseEntity status sai | Kiểm tra status line và `ProblemDetail.status` |
| Có `"properties":{"code":...}` thay vì top-level `code` | Không sử dụng Spring Jackson mixin/config tương ứng | Kiểm tra `ProblemDetailJacksonMixin` và converter |
| `type` luôn là `about:blank` | Chưa gán type URI | Nếu cần machine-readable problem riêng, đặt type ổn định |
| Lỗi validation có `400` nhưng không có `errors` | Base handler không định nghĩa extension tùy chỉnh | Override đúng `handleMethodArgumentNotValid` |
| Exception nghiệp vụ trả `500` | Chưa map custom exception | Thêm `@ExceptionHandler` với status/type rõ |
| Security error có HTML hoặc response khác | Exception xảy ra trong filter chain trước MVC | Cấu hình security handler/gateway riêng |
| `@RestControllerAdvice` không chạy | Package scan hoặc scope, thứ tự/xung đột handler | Kiểm tra bean, `@Order`, stack trace |
| `406` hoặc `415` ngoài ý muốn | `Accept`/`Content-Type` không phù hợp | Kiểm tra media type, converter và endpoint mapping |
| `500` lộ SQL hoặc stack trace | Trả `ex.getMessage()` thẳng vào `detail` | Thay thông điệp chung, ghi exception ở log nội bộ |
| Client parse lỗi theo `detail` không ổn định | Client dựa vào văn bản bản địa hóa | Chuyển sang `type`/`code` |
| Lỗi RFC bị double-wrap | Global `ResponseBodyAdvice` tác động cả `ProblemDetail` | Loại trừ ErrorResponse/ProblemDetail đúng phạm vi |
| `instance` lộ tokens | Gắn nguyên URL và query vào response | Dùng path/opaque occurrence ID an toàn |
| Thêm extension làm SDK cũ hỏng | Client closed schema không bỏ qua field mới | Có contract test và versioning chính sách |

Một quy trình kiểm tra nhanh:

1. Gửi request bằng `curl -i` để xem **HTTP status và `Content-Type` thực**.
2. Xác định lỗi sinh từ argument resolver, validation, controller/service, MVC handler hay filter/gateway.
3. Kiểm tra bean `@ControllerAdvice`, thứ tự và handlers của `ResponseEntityExceptionHandler`.
4. Kiểm tra `type`, `title`, `status`, `detail`, `instance` và extensions có đúng contract không.
5. Kiểm tra nguyên nhân server-side qua log/traces không chứa bí mật; không phơi stack trace.
6. Chạy contract tests cho ít nhất một lỗi nghiệp vụ, validation, malformed JSON và unexpected 500.
7. Kiểm tra cả client cũ với error schema mới, đặc biệt nếu từng dùng error DTO tự định nghĩa.

## 12. Quy tắc thiết kế hợp đồng lỗi bền vững

Một API dùng Problem Details nên có ít nhất:

- **Danh mục problem types:** URI, title và HTTP status phù hợp với từng loại vấn đề; các extension field được công bố.
- **Quy tắc status rõ:** `400` validation, `404` không tìm thấy, `409` xung đột, `500` lỗi server — điều chỉnh theo nghiệp vụ nhưng nhất quán.
- **Quy tắc nội dung bảo mật:** chi tiết công khai an toàn, không leak secrets/stack/internal identifiers.
- **Mã ổn định cho machine consumers:** ưu tiên `type`; bổ sung `code` nếu contract cần.
- **Validation extension schema:** dùng `errors` một cách nhất quán, tránh khác shape giữa các endpoint.
- **Locale policy:** title/detail có thể bản địa hóa, type/code không đổi.
- **Correlation ID:** nếu cần, liên kết response với log/traces; không bịa ID không thể tra.
- **Kiểm thử ngược:** giữ fixtures hoặc OpenAPI schema đã phát hành; tránh làm hỏng clients đọc `application/problem+json`.
- **Ngoại lệ có chủ đích:** proxy/security/streaming/legacy response không theo cùng MVC pipeline cần chính sách riêng.
- **Một nơi sở hữu policy:** tránh nhiều advice toàn cục cùng rewrite lỗi không dự đoán được.

## 13. Bài tập kiểm tra kiến thức

1. `ProblemDetail.type` và `ProblemDetail.instance` khác nhau ở điểm nào?
2. Một endpoint trả `HTTP 200 OK` nhưng body `{"status":404}`. Vì sao đây là hợp đồng sai?
3. `ErrorResponseException` khác gì với `ProblemDetail` chỉ là dữ liệu?
4. Lỗi `@Valid @RequestBody` nên được bắt bởi method nào của `ResponseEntityExceptionHandler` trong ví dụ?
5. Vì sao `@RestControllerAdvice` không bảo đảm thay thế mọi response lỗi của Spring Security/gateway?
6. `setProperty("code", "...")` xuất hiện ở đâu trong JSON khi dùng Jackson/Spring?
7. `detail` và `title` có nên là mã ổn định mà frontend parse bằng cách so chuỗi không?
8. Khi nào nên dùng `409 Conflict` thay vì `400 Bad Request`?
9. Vì sao `ProblemDetail` không thể thay validation, authorization hay business rules?
10. Nếu bật `spring.mvc.problemdetails.enabled=true` đồng thời có advice kế thừa `ResponseEntityExceptionHandler`, bạn cần kiểm tra điều gì?
11. Nếu API hỗ trợ đa ngôn ngữ, field nào nên thay đổi theo locale, field nào không?
12. Vì sao không nên trả `FieldError.getRejectedValue()` và `Exception.getMessage()` tùy ý trong body lỗi?

**Gợi ý trả lời:** (1) `type` định danh loại lỗi, `instance` định danh occurrence. (2) HTTP status thật báo thành công trong khi body báo lỗi. (3) Exception mang status/headers/body theo `ErrorResponse`; `ProblemDetail` là object biểu diễn. (4) `handleMethodArgumentNotValid`. (5) Lỗi có thể xảy ra ngoài MVC handler resolver chain. (6) Extension top-level JSON. (7) Không, dùng type/code. (8) Khi xung đột trạng thái tài nguyên khiến request hiện tại không được thực hiện. (9) Đây chỉ là error representation. (10) Thứ tự và xung đột handler. (11) title/detail có thể dịch; type/code/status giữ ngữ nghĩa. (12) Có thể lộ dữ liệu nhạy cảm và implementation details.

## 14. Tài liệu tham khảo

- [RFC 9457 — Problem Details for HTTP APIs, IETF](https://www.rfc-editor.org/rfc/rfc9457.html)
- [Spring Framework — Error Responses (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Spring Framework 6.1.21 — ProblemDetail Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/http/ProblemDetail.html)
- [Spring Framework 6.1.21 — ErrorResponse Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/ErrorResponse.html)
- [Spring Framework 6.1.21 — ErrorResponseException Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/ErrorResponseException.html)
- [Spring Framework 6.1 — ResponseEntityExceptionHandler API](https://docs.spring.io/spring-framework/docs/6.1.10/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html)
- [Spring Boot — Servlet Web Applications: Problem Details](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [Spring Boot 3.3 — Common Application Properties](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/index.html)
- [Spring Framework — @ExceptionHandler](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)

Tất cả code thuộc ví dụ Spring MVC/Jackson, không áp dụng nguyên vẹn cho WebFlux. HTTP payloads là ví dụ hợp đồng có thể kiểm tra bằng `curl`/`MockMvc`; status mặc định, error text và headers bên ngoài code ví dụ vẫn có thể khác theo môi trường, locale và cấu hình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-006">Biểu diễn lỗi từng trường, thông tin mở rộng và mã lỗi ổn định</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu diễn lỗi từng trường, thông tin mở rộng và mã lỗi ổn định trong Spring MVC

## 1. Bài toán và kết quả mong đợi

Một REST endpoint nhận request không hợp lệ cần trả lại nhiều hơn trạng thái HTTP 400. Frontend phải biết **trường nào lỗi**, **vì sao lỗi** và hiển thị thông điệp dễ hiểu; client tự động cần **mã lỗi ổn định** để phân nhánh mà không phụ thuộc vào ngôn ngữ hiển thị, tên exception hay cấu trúc nội bộ của Java.

Ví dụ request:

~~~http
POST /api/registrations HTTP/1.1
Content-Type: application/json
Accept: application/problem+json

{"name":"","email":"not-an-email","seats":0}
~~~

Một hợp đồng phản hồi hữu ích:

~~~json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/registrations",
  "code": "REQUEST_VALIDATION_FAILED",
  "violations": [
    {"field":"email","code":"INVALID_FORMAT","message":"Invalid field format"},
    {"field":"name","code":"REQUIRED","message":"This field is required"},
    {"field":"seats","code":"OUT_OF_RANGE","message":"Value is outside the allowed range"}
  ]
}
~~~

Đây là **hợp đồng JSON do ứng dụng định nghĩa** dựa trên RFC 9457. Spring không tự thêm mã `REQUEST_VALIDATION_FAILED` hay mảng `violations`; người viết API phải xây dựng, duy trì và kiểm thử chúng. `instance` có thể được Spring điền theo request path khi còn thiếu; giá trị và cách serialize phụ thuộc cấu hình ứng dụng.

Bài học tập trung vào Spring MVC Servlet stack, Java 17+, Spring Boot 3.x / Spring Framework 6.x, bao gồm:
- Cấu trúc HTTP status, RFC 9457 `ProblemDetail` và phần mở rộng.
- Chuyển `FieldError`, `ObjectError` và validation results thành lỗi từng trường.
- Mã máy đọc được ổn định, nội dung hiển thị có thể dịch.
- Phân biệt lỗi đọc JSON, validation DTO, method validation, lỗi nghiệp vụ và lỗi server.
- Tạo `@RestControllerAdvice` đúng cơ chế, tránh exception handler xung đột.
- Kiểm thử contract bằng MockMvc; tránh tiết lộ dữ liệu hoặc exception nội bộ.

## 2. Tách HTTP status, loại lỗi, vị trí lỗi và thông điệp

| Trường | Dành cho | Cam kết |
| --- | --- | --- |
| HTTP `status` | Giao thức HTTP, middleware/client | 400 input lỗi; 404 tài nguyên thiếu; 409 xung đột; 500 lỗi server tùy chính sách |
| `type` của Problem Details | Nhận diện loại problem theo URI | Có thể dùng URI tài liệu ổn định, hoặc `about:blank` |
| Extension `code` | Logic máy, telemetry, hỗ trợ vận hành | Mã public như `REQUEST_VALIDATION_FAILED`, không đổi tùy locale |
| `violations[].field` | UI ánh xạ tới input field | Dùng public DTO field/path, không dùng Java method name |
| `violations[].code` | UI và client tự động hiểu loại lỗi | `REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`... |
| `title` / `detail` / `message` | Người đọc | Có thể dịch, thay câu chữ; client không parse làm điều kiện logic |
| `requestId` | Correlation/tracing khi được triển khai | Lấy từ infrastructure đáng tin, không phản chiếu token |

Ví dụ, lỗi email trống và email sai format đều thường dẫn đến 400 nhưng hành động hiển thị khác. Client có thể dựa vào `field=email` và `code=REQUIRED` hoặc `INVALID_FORMAT`. Client không nên tìm substring tiếng Anh `"is required"` vì thông điệp có thể được dịch sang tiếng Việt.

Mã top-level `REQUEST_VALIDATION_FAILED` cho biết **nhóm vấn đề của request**; mã `REQUIRED` nằm trong violation chỉ rõ cách sửa. Một API có thể công bố ít mã top-level nhưng nhiều mã field-level nếu nó cần UX chi tiết.

## 3. RFC 9457 và `ProblemDetail`

### 3.1. Năm trường chuẩn

RFC 9457 Problem Details for HTTP APIs có năm thành phần thông dụng:

| Trường | Ý nghĩa |
| --- | --- |
| `type` | URI reference nhận diện loại vấn đề. `about:blank` diễn giải theo status mặc định |
| `title` | Tên ngắn của loại lỗi, thích hợp cho UI/log |
| `status` | Mã HTTP status khi tạo problem; phải tương thích status thực tế |
| `detail` | Thông tin mô tả riêng cho lần lỗi này, không chứa bí mật |
| `instance` | URI reference của lần xảy ra lỗi/request liên quan |

RFC 9457 cho phép trường bổ sung (*extension members*) như `code`, `violations`, `requestId`, `retryable` hay `documentation`. Không thêm phần mở rộng chỉ vì có thông tin trong exception; cân nhắc lợi ích cho client và quyền riêng tư.

Nếu dùng `type` là URI tài liệu, backend phải sở hữu và duy trì URI đó. Với một API nhỏ, `about:blank` cùng mã `code` ổn định cũng có thể là quy ước hợp lý. Chọn một cơ chế để client định danh lỗi và ghi rõ trong OpenAPI.

### 3.2. Cách Spring MVC xử lý

Spring Framework 6 cung cấp:
- `org.springframework.http.ProblemDetail`: biểu diễn body theo RFC 9457.
- `org.springframework.web.ErrorResponse`: contract về status, headers và problem body của một lỗi HTTP.
- `org.springframework.web.ErrorResponseException`: exception cơ bản mang thông tin ErrorResponse.
- `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler`: lớp nền cho advice xử lý nhiều exception có sẵn của Spring MVC.

Khi trả `ProblemDetail`, Spring MVC có thể ghi body với media type `application/problem+json`. Trong cấu hình Jackson thông thường của Spring, các phần tử trong `ProblemDetail.properties` được **unwrap thành trường JSON ở top-level**.

Ví dụ minh họa (chưa đầy đủ imports và biến `violations`):

~~~java
ProblemDetail body = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
body.setTitle("Request validation failed");
body.setDetail("One or more fields are invalid.");
body.setProperty("code", "REQUEST_VALIDATION_FAILED");
body.setProperty("violations", violations);
~~~

Kết quả sẽ có `code` và `violations` ngang hàng `title` và `status`, thay vì mặc nhiên nằm bên trong `"properties":{...}`. Tránh đặt extension tên `type`, `status`, `title`, `detail` hoặc `instance` gây nhập nhằng với trường chuẩn.

Spring Boot có cấu hình `spring.mvc.problemdetails.enabled` để auto-configure xử lý một số lỗi built-in thành problem details. Khi tự triển khai subclass `ResponseEntityExceptionHandler`, bạn sở hữu response của các nhánh override và cần xác nhận thứ tự advice với configuration Boot hiện hữu.

## 4. Thiết kế mảng lỗi từng trường (`violations`)

Một phần tử nên có tối thiểu:

~~~json
{"field":"profile.email","code":"INVALID_FORMAT","message":"Invalid field format"}
~~~

Trong đó:
- `field` là tên hoặc đường dẫn public của dữ liệu request; nếu có object lồng, có thể là `profile.email`; với mảng có thể là `items[0].quantity`.
- `code` là mã máy đọc được trong tập mã do API công bố.
- `message` là thông điệp an toàn dành cho người dùng, có thể bản địa hóa.

Nếu lỗi không thuộc trường nào, ví dụ constraint liên quan hai field, có thể quy ước `field:"$"` để nói lỗi ở mức object. Không bắt client đoán lúc nào `field` null, lúc nào chuỗi rỗng, lúc nào không có thuộc tính.

**Cho phép nhiều lỗi trên một field.** Một giá trị có thể vi phạm nhiều constraint. Backend có thể trả nhiều violation hoặc chọn lỗi ưu tiên, nhưng phải quyết định quy tắc và test. Đặc biệt, thứ tự errors từ Bean Validation provider không nên tự được coi là thứ tự UI; nếu API công bố thứ tự, hãy tự sort theo một quy tắc định nghĩa.

### 4.1. Mã field-level công khai

| Constraint/binding nội bộ | Mã public ví dụ | Ý nghĩa |
| --- | --- | --- |
| `NotBlank`, `NotNull`, `NotEmpty` | `REQUIRED` | Không được vắng/rỗng theo quy tắc áp dụng |
| `Email`, `Pattern` | `INVALID_FORMAT` | Không đáp ứng định dạng |
| `Min`, `Max`, `Positive` | `OUT_OF_RANGE` | Không đáp ứng khoảng giá trị |
| `Size` | `INVALID_LENGTH` | Độ dài/số phần tử không phù hợp |
| Constraint khác | `INVALID_VALUE` | Một quy tắc không được đáp ứng |
| Query/parameter method invalid | `INVALID_PARAMETER` | Input không thỏa điều kiện method |

`FieldError.getCode()` trả mã do Spring/validation tạo như `NotBlank`, `Email`, `Min`. Các mã trong `getCodes()` còn có thể gắn tên object và field Java. Đó là dữ liệu **nội bộ** phục vụ binding và MessageSource, không nhất thiết phù hợp làm hợp đồng API.

Dùng tầng mapping để giữ `INVALID_FORMAT` ổn định dù validator chuyển từ `@Email` sang custom constraint. Ngược lại, nếu đổi public code hay ý nghĩa của nó, hãy coi đó là thay đổi API cần versioning/review.

### 4.2. Không xuất giá trị bị từ chối (`rejectedValue`)

`FieldError` có thể chứa `getRejectedValue()`, nhưng đây có thể là password, token, email, số giấy tờ hoặc object rất dài. **Không đưa rejectedValue, stack trace, SQL exception, tên package, raw parser message ra public response.** Frontend đã có giá trị người dùng nhập; server chỉ cần báo field và lỗi.

Với lỗi JSON parser, có trường hợp server chưa tạo ra DTO nên không có field name chính xác. Đừng bịa một violation cụ thể nếu không đủ bằng chứng.

## 5. Những nhánh lỗi thực sự gặp trong Spring MVC

Một pipeline đơn giản:

~~~text
HTTP request
  |
  +-- Không đọc được body / JSON sai
  |     -> HttpMessageNotReadableException
  |
  +-- Thiếu query param hoặc đổi kiểu thất bại
  |     -> MissingServletRequestParameterException
  |     -> MethodArgumentTypeMismatchException / TypeMismatchException
  |
  +-- Có @Valid @RequestBody DTO, không có direct method constraint
  |     -> MethodArgumentNotValidException (individual argument validation)
  |
  +-- Có constraint trực tiếp trên method parameter, Spring MVC 6.1+
  |     -> HandlerMethodValidationException (input: 400)
  |
  +-- Method return value vi phạm constraint
        -> HandlerMethodValidationException (server: 500)
~~~

Sơ đồ chỉ các **nhánh có thể xảy ra**, không phải toàn bộ exception của MVC. Với controller có `@Validated` ở cấp class và cấu hình AOP method validation, đường đi có thể qua proxy và ném `ConstraintViolationException` hoặc `MethodValidationException`. Spring MVC 6.1+ khuyến nghị bỏ class-level `@Validated` nếu bạn muốn dùng built-in MVC method validation.

| Lỗi | Dữ liệu có thể dùng để tạo contract |
| --- | --- |
| `MethodArgumentNotValidException` | `getBindingResult().getFieldErrors()`, `getGlobalErrors()` |
| `HandlerMethodValidationException` | `getAllValidationResults()`, `visitResults(Visitor)`; có thể có `ParameterErrors` cho nested DTO |
| `HttpMessageNotReadableException` | Phân loại body không đọc được; thường không có field-level details đáng tin |
| `MissingServletRequestParameterException` | Tên tham số cần thiết bị thiếu |
| `MethodArgumentTypeMismatchException` | Tham số HTTP và kiểu Java expected; không phơi raw input nhạy cảm |
| Domain exception như not found/conflict | Public domain error code và status theo chính sách |
| Exception nội bộ không dự kiến | `INTERNAL_ERROR`, status 500, log nguyên nhân an toàn phía server |

**Đừng map tất cả lỗi thành HTTP 400.** Vi phạm ràng buộc trên **kết quả trả về của server** nên là 500. Xung đột nghiệp vụ có thể là 409. Request hợp lệ nhưng một tài nguyên không tồn tại thường là 404.

## 6. Ví dụ ứng dụng chạy được: Spring Boot 3.3 / Java 17

Ví dụ minh họa cách triển khai một API nhận đăng ký đơn giản, tự tạo error body không phụ thuộc database. Dùng Spring Boot 3.3.5 (nền Spring Framework 6.1.x khi dùng BOM mặc định), Java 17+, Spring MVC và Hibernate Validator.

Cấu trúc:

~~~text
registration-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/registration/
    │   ├── RegistrationApplication.java
    │   ├── api/RegistrationInput.java
    │   └── web/
    │       ├── RegistrationController.java
    │       └── ApiErrorAdvice.java
    └── test/java/com/example/registration/web/
        └── RegistrationControllerTest.java
~~~

### 6.1. Build

`settings.gradle`:

~~~groovy
rootProject.name = 'registration-demo'
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
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` cung cấp Spring MVC/Jackson. `spring-boot-starter-validation` là phần cần thiết để Bean Validation constraints hoạt động (thường qua Hibernate Validator). `spring-boot-starter-test` hỗ trợ JUnit và MockMvc. Ví dụ giả định project có Gradle wrapper tương thích; code Spring không thể chạy đơn lẻ chỉ bằng `javac` nếu không khai báo classpath Spring.

### 6.2. Main application

`src/main/java/com/example/registration/RegistrationApplication.java`:

~~~java
package com.example.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RegistrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegistrationApplication.class, args);
    }
}
~~~

Lớp đặt tại package root để component scan phát hiện `web` và `api`.

### 6.3. Input DTO có constraints

`src/main/java/com/example/registration/api/RegistrationInput.java`:

~~~java
package com.example.registration.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationInput(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Email String email,
        @Min(1) int seats) {
}
~~~

`@NotBlank` kiểm tra chuỗi không chỉ có whitespace; `@Email` kiểm định định dạng cơ bản nhưng không xác minh quyền sở hữu email; `@Min(1)` yêu cầu seats ít nhất 1. Với kiểu primitive `int`, body vắng thuộc tính seats có thể được deserialize thành giá trị mặc định theo cấu hình, rồi vi phạm `@Min`. Nếu cần phân biệt không gửi và gửi 0, dùng `Integer` kết hợp `@NotNull`.

### 6.4. Controller có hai con đường validation

`src/main/java/com/example/registration/web/RegistrationController.java`:

~~~java
package com.example.registration.web;

import com.example.registration.api.RegistrationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    public record Receipt(String name, int seats) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Receipt submit(@Valid @RequestBody RegistrationInput input) {
        return new Receipt(input.name(), input.seats());
    }

    @GetMapping("/check")
    public Map<String, Integer> check(
            @RequestParam("minimumSeats") @Min(1) int minimumSeats) {
        return Map.of("minimumSeats", minimumSeats);
    }
}
~~~

`submit` có individual argument validation cho `@Valid @RequestBody`; khi dữ liệu vi phạm có thể ném `MethodArgumentNotValidException`. `check` có direct `@Min(1)` trên tham số phương thức nên với Spring MVC **6.1+** built-in method validation có thể ném `HandlerMethodValidationException`.

Không gắn `@Validated` ở **cấp class** trong demo: class-level `@Validated` có thể chuyển sang AOP method validation và loại exception khác. Ví dụ này không lưu dữ liệu; success status mặc định 200 và response receipt chỉ minh họa đường HTTP thành công. Trong API tạo tài nguyên thực, chọn 201/Location và persistence theo business contract.

### 6.5. Advice map hai exception thành cùng error contract

`src/main/java/com/example/registration/web/ApiErrorAdvice.java`:

~~~java
package com.example.registration.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrorAdvice extends ResponseEntityExceptionHandler {

    public record Violation(String field, String code, String message) {}

    private static Violation fieldViolation(FieldError error) {
        String stableCode = switch (
                error.getCode() == null ? "" : error.getCode()) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Min", "Max", "Positive", "PositiveOrZero" -> "OUT_OF_RANGE";
            case "Size" -> "INVALID_LENGTH";
            default -> "INVALID_VALUE";
        };

        String safeMessage = switch (stableCode) {
            case "REQUIRED" -> "This field is required";
            case "INVALID_FORMAT" -> "Invalid field format";
            case "OUT_OF_RANGE" -> "Value is outside the allowed range";
            case "INVALID_LENGTH" -> "Invalid field length";
            default -> "Invalid field value";
        };

        return new Violation(error.getField(), stableCode, safeMessage);
    }

    private static List<Violation> sorted(List<Violation> errors) {
        return errors.stream()
                .sorted(Comparator.comparing(Violation::field)
                        .thenComparing(Violation::code))
                .toList();
    }

    private ResponseEntity<Object> problem(
            HttpStatusCode status, HttpHeaders headers, String code,
            String title, String detail, List<Violation> violations) {

        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setTitle(title);
        body.setDetail(detail);
        body.setProperty("code", code);

        if (violations != null) {
            body.setProperty("violations", sorted(violations));
        }
        return new ResponseEntity<>(body, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<Violation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> violations.add(fieldViolation(e)));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> violations.add(new Violation(
                        "$", "INVALID_OBJECT", "Object-level constraint failed")));

        return problem(status, headers, "REQUEST_VALIDATION_FAILED",
                "Request validation failed",
                "One or more fields are invalid.", violations);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (ex.isForReturnValue()) {
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, headers,
                    "INTERNAL_ERROR", "Internal server error",
                    "The server could not complete the request.", null);
        }

        List<Violation> violations = new ArrayList<>();

        for (ParameterValidationResult result
                : ex.getAllValidationResults()) {

            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(e ->
                        violations.add(fieldViolation(e)));
                errors.getGlobalErrors().forEach(e ->
                        violations.add(new Violation(
                                "$", "INVALID_OBJECT",
                                "Object-level constraint failed")));
            } else {
                MethodParameter parameter = result.getMethodParameter();
                RequestParam query =
                        parameter.getParameterAnnotation(RequestParam.class);

                String field = query == null || query.name().isBlank()
                        ? "argument" + parameter.getParameterIndex()
                        : query.name();

                String code =
                        parameter.hasParameterAnnotation(Min.class)
                        || parameter.hasParameterAnnotation(Max.class)
                        ? "OUT_OF_RANGE" :
                        parameter.hasParameterAnnotation(NotBlank.class)
                        ? "REQUIRED" : "INVALID_PARAMETER";

                for (var ignored : result.getResolvableErrors()) {
                    violations.add(new Violation(
                            field, code,
                            "Parameter does not satisfy constraints"));
                }
            }
        }

        return problem(status, headers, "REQUEST_VALIDATION_FAILED",
                "Request validation failed",
                "One or more parameters are invalid.", violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return problem(status, headers, "MALFORMED_REQUEST_BODY",
                "Request body cannot be read",
                "Request body is missing or malformed.", null);
    }
}
~~~

Điểm quan trọng:
1. Kế thừa `ResponseEntityExceptionHandler` rồi **override method tương ứng**; không đặt thêm `@ExceptionHandler(MethodArgumentNotValidException.class)` trùng với superclass.
2. `fieldViolation()` chuyển mã Bean Validation nội bộ thành mã public. Message được kiểm soát, không trả `getRejectedValue()`.
3. `handleMethodArgumentNotValid()` lấy field errors và object-level errors từ `BindingResult`.
4. `handleHandlerMethodValidationException()` phân biệt **input constraints** và **return-value violation**; riêng lỗi return trả 500 với body an toàn.
5. Với `ParameterErrors` (ví dụ DTO cascade bên trong method validation), advice lấy field paths. Với direct query constraints, nó dùng `@RequestParam` để lấy tên public.
6. Ví dụ chỉ map tốt một tập annotation và tham số: `@RequestParam`, `@Min`, `@Max`, `@NotBlank`, DTO field errors. Với `@PathVariable`, `@RequestHeader`, cross-parameter/nested container constraint, nên triển khai mapper đầy đủ qua `HandlerMethodValidationException.Visitor` hoặc một abstraction domain; fallback `argumentN` không nên dùng như field name public lâu dài.
7. `handleHttpMessageNotReadable()` không giả tạo violation trên một field khi JSON chưa thể parse.
8. `problem()` giữ HTTP status và JSON `status` nhất quán; extension `violations` chỉ có trong những nhánh biết violation.

**Giới hạn phiên bản:** code này sử dụng class/API của Spring MVC 6.1+. Nếu dự án chạy Spring 6.0, nhánh `HandlerMethodValidationException` chưa có cùng cách hoạt động. Nếu controller có class-level `@Validated`, AOP có thể ném `ConstraintViolationException` hoặc `MethodValidationException` và cần mapper khác. Chỉ đổi cơ chế khi có kiểm thử regression.

## 7. Ví dụ request/response và kết quả dự kiến

Trong project đã có Gradle wrapper:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell/CMD dùng `.\gradlew.bat bootRun`. Với Spring dependencies, `javac` thuần không đủ classpath; Gradle plugin xử lý compile/run.

### 7.1. DTO vi phạm ba constraints

~~~bash
curl -i -X POST 'http://localhost:8080/api/registrations' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/problem+json' \
  -d '{"name":"","email":"not-an-email","seats":0}'
~~~

Đường `MethodArgumentNotValidException` được advice chuyển thành HTTP 400. Các field chủ yếu trong JSON:

~~~json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "code": "REQUEST_VALIDATION_FAILED",
  "violations": [
    {"field":"email","code":"INVALID_FORMAT","message":"Invalid field format"},
    {"field":"name","code":"REQUIRED","message":"This field is required"},
    {"field":"seats","code":"OUT_OF_RANGE","message":"Value is outside the allowed range"}
  ]
}
~~~

`instance` có thể được thêm tự động theo đường dẫn request. Bảng lỗi được sort theo `field` nên email trước name rồi seats; đó là **quy tắc do ứng dụng viết**, không phụ thuộc Hibernate Validator duyệt constraints theo thứ tự nào.

### 7.2. Đầu vào hợp lệ

~~~bash
curl -i -X POST 'http://localhost:8080/api/registrations' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Lan","email":"lan@example.com","seats":2}'
~~~

Response demo mặc định HTTP 200:

~~~json
{"name":"Lan","seats":2}
~~~

Demo không lưu database, không tuyên bố đăng ký đã được persist. API tạo tài nguyên thực nên quyết định status 201/Location, transactional behavior và exception xung đột riêng.

### 7.3. Direct method validation

~~~bash
curl -i 'http://localhost:8080/api/registrations/check?minimumSeats=0'
~~~

Với Spring MVC 6.1+, `@Min(1)` trên method parameter thường cho HTTP 400 và các extension:

~~~json
{
  "code":"REQUEST_VALIDATION_FAILED",
  "violations":[
    {
      "field":"minimumSeats",
      "code":"OUT_OF_RANGE",
      "message":"Parameter does not satisfy constraints"
    }
  ]
}
~~~

Đây là **phần extension được trích** từ body, response đầy đủ còn có các trường RFC 9457. `minimumSeats` được lấy từ tên khai báo rõ ràng trong `@RequestParam`.

### 7.4. JSON sai cú pháp

~~~bash
curl -i -X POST 'http://localhost:8080/api/registrations' \
  -H 'Content-Type: application/json' \
  -d '{"name":'
~~~

Parser không tạo được DTO; advice trả HTTP 400 với `code=MALFORMED_REQUEST_BODY`, không có `violations`. Không mô tả đây là lỗi field `name` nếu parser chưa xác định chắc được.

### 7.5. Tham số query không chuyển kiểu được

`GET /api/registrations/check?minimumSeats=abc` thường bị Spring MVC từ chối trước method validation bằng lỗi conversion, cũng là HTTP 400 nhưng **advice demo chưa map sang custom `code`**. Nếu hệ thống yêu cầu **mọi** 400 đều có stable code, cần override `handleTypeMismatch(...)` và `handleMissingServletRequestParameter(...)`, đặt `QUERY_PARAMETER_INVALID` / `REQUIRED_PARAMETER_MISSING` và test. Không khẳng định các override ở mục 6 đã chuẩn hóa mọi exception Spring.

## 8. MockMvc kiểm thử contract

`src/test/java/com/example/registration/web/RegistrationControllerTest.java`:

~~~java
package com.example.registration.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void invalidDtoReturnsStableFieldErrors() throws Exception {
        mvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.valueOf("application/problem+json"))
                        .content("{\"name\":\"\",\"email\":\"not-an-email\","
                                + "\"seats\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(3))
                .andExpect(jsonPath("$.violations[0].field").value("email"))
                .andExpect(jsonPath("$.violations[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.violations[1].field").value("name"))
                .andExpect(jsonPath("$.violations[1].code").value("REQUIRED"))
                .andExpect(jsonPath("$.violations[2].field").value("seats"))
                .andExpect(jsonPath("$.violations[2].code").value("OUT_OF_RANGE"));
    }

    @Test
    void directMethodConstraintIsRepresented() throws Exception {
        mvc.perform(get("/api/registrations/check")
                        .param("minimumSeats", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("minimumSeats"))
                .andExpect(jsonPath("$.violations[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void invalidJsonHasNoFakeFieldErrors() throws Exception {
        mvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    void validDtoReturnsSuccessResponse() throws Exception {
        mvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lan\",\"email\":\"lan@example.com\","
                                + "\"seats\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lan"))
                .andExpect(jsonPath("$.seats").value(2));
    }

    @Test
    void invalidQueryConversionIsStillBadRequest() throws Exception {
        mvc.perform(get("/api/registrations/check")
                        .param("minimumSeats", "abc"))
                .andExpect(status().isBadRequest());
    }
}
~~~

Chạy:

~~~bash
./gradlew test
~~~

Test này chú trọng **HTTP status, mã public, field path và structure**, không assert thông điệp tiếng Anh vì message có thể được thay bằng bản dịch mà contract vẫn hợp lệ. `violations[0]` được assert vì code advice đã sort. Nếu không muốn hứa thứ tự lỗi, hãy kiểm tra theo tập; **không dựa vào thứ tự từ Bean Validation provider**.

Với dự án có Spring Security, custom MVC config, Jackson modules hay advice khác, MockMvc cần setup tương ứng. Ví dụ không tự động chứng minh mọi project Spring Boot có cùng error JSON; xác nhận cả integration test của hệ thống thực.

## 9. Lỗi mức object, nested field và constraints phức tạp

Một request DTO phức tạp có thể chứa:

~~~json
{
  "contact": {"email":"invalid"},
  "items": [
    {"sku":"A","quantity":0},
    {"sku":"B","quantity":2}
  ]
}
~~~

Để validate nested objects, cần `@Valid` trên các thuộc tính cần cascade và constraint thực tế trong record/class lồng nhau. Field errors từ Spring binding có thể có path `contact.email` hoặc `items[0].quantity`. Đây là **nội dung minh họa**, không phải payload được xử lý bởi demo registration phẳng.

Cần quyết định field path là:
- **Dotted/bracket notation**: `items[0].quantity`, gần cách Spring biểu diễn.
- **JSON Pointer** theo RFC 6901: `/items/0/quantity`, thuận tiện ánh xạ cấu trúc JSON; phải escape các ký tự đặc biệt `~` và `/`.

Đừng trộn hai kiểu path giữa các endpoint. Nếu public DTO field bị đổi từ `contact.email` thành `emailAddress`, error contract cũng có thể phá frontend dù HTTP 400 vẫn không đổi. Có thể dùng lớp mapping từ internal binding paths sang public schema paths.

Các constraint liên quan nhiều field có thể tạo `ObjectError`; ví dụ `startDate` phải trước `endDate`. Response có thể dùng `field="$"` kèm `code=INVALID_OBJECT` hoặc dùng extension `fields:["startDate","endDate"]`, nếu muốn tô đỏ cả hai. Demo chọn dấu `"$"` cho lỗi object; đây là quy ước phải ghi rõ.

## 10. Quốc tế hóa: code bất biến, message linh hoạt

Một frontend tiếng Việt có thể nhận:

~~~json
{
  "code":"REQUEST_VALIDATION_FAILED",
  "violations":[
    {"field":"email","code":"INVALID_FORMAT","message":"Địa chỉ email không hợp lệ"}
  ]
}
~~~

Với locale tiếng Anh, message có thể trở thành `"Invalid email address"` nhưng `INVALID_FORMAT` và `REQUEST_VALIDATION_FAILED` vẫn giữ nguyên. Có hai lựa chọn:
1. Backend dùng `MessageSource` và locale request để resolve message theo mã, trả chuỗi đã dịch.
2. Backend trả code kèm tham số công khai cần thiết; frontend tự dịch theo catalog UI.

`FieldError` là `MessageSourceResolvable` có `getCodes()`, `getArguments()` và default message. MessageSource của Spring hỗ trợ các key như `NotBlank.registrationInput.name` khi object/field names tương ứng, nhưng **tên key thực tế phụ thuộc binding name**. Để tách API khỏi Java DTO refactor, có thể dùng một catalog riêng với key `api.required`, `api.invalidFormat`, `api.outOfRange`.

Một message cần tham số:

~~~json
{
  "field":"name",
  "code":"INVALID_LENGTH",
  "message":"Name must not exceed 80 characters",
  "maxLength":80
}
~~~

`maxLength` là extension có thể hữu ích cho frontend; chỉ xuất tham số công khai. **Advice demo mục 6 chưa xuất `maxLength`**, vì record `Violation` chỉ có field/code/message. Nếu bổ sung field mới, update DTO serializer, OpenAPI và test. Không xuất raw regex, SQL constraint hay policy bảo mật nội bộ.

### Không dùng thông báo làm mã lỗi

Đoạn frontend không nên viết:

~~~javascript
if (error.message.includes("must not be blank")) {
  showRequiredHint();
}
~~~

Khi đổi ngôn ngữ hoặc Bean Validation provider, logic sẽ hỏng. Cách đúng:

~~~javascript
const violations = problem.violations ?? [];
for (const violation of violations) {
  if (violation.code === "REQUIRED") {
    showFieldError(violation.field, violation.message);
  }
}
~~~

Đây là fragment JavaScript dành cho client; ``showFieldError` phải là hàm do giao diện định nghĩa. `problem` là JSON response đã parse thành object.

## 11. Thông tin mở rộng: cái gì hữu ích, cái gì nên tránh

| Extension | Khi có ích | Điều kiện |
| --- | --- | --- |
| `code` | Hầu hết lỗi cần client xử lý | Mã ổn định, có tài liệu |
| `violations` | Validation theo field | Path/code/message, cho phép nhiều lỗi trên một field |
| `requestId` / `traceId` | Support, correlation | ID tạo/xác minh bởi tracing infrastructure |
| `retryable` | API có chính sách retry | Không đánh dấu 400 validation retryable |
| `retryAfterSeconds` | 429/503 có chiến lược retry | Phải nhất quán với header `Retry-After` nếu có |
| `documentation` | Có tài liệu giải thích lỗi | URI ổn định, đúng phiên bản |
| `min`, `max`, `allowedValues` | Điều kiện sửa input công khai | Không lộ quy tắc nội bộ nhạy cảm |
| `resource` hoặc `operation` | Ngữ cảnh nghiệp vụ cần thiết | Không để lộ tenant/identifier không có quyền |

Với 500, body nên an toàn:

~~~json
{
  "type":"about:blank",
  "title":"Internal server error",
  "status":500,
  "detail":"The server could not complete the request.",
  "code":"INTERNAL_ERROR"
}
~~~

Server log exception class, stack trace và diagnostics cần thiết qua kênh có kiểm soát. Nếu có `requestId`, người dùng/support có thể tra sự cố mà không cần public exception details. Không tin một `X-Request-Id` tùy ý do client gửi nếu dùng nó làm bằng chứng an ninh hoặc khóa truy cập log.

**Không xuất:** `getRejectedValue()`, password, OTP, token, session IDs, connection strings, SQL query đầy đủ, raw exception message, stack trace, tên tệp server, đường dẫn hệ thống hoặc nội dung upload. Việc ghi raw input vào log cũng có rủi ro tương tự response.

### Lỗi xung đột thông tin cá nhân

API đăng ký có thể cân nhắc mã `EMAIL_ALREADY_REGISTERED` với HTTP 409. Nhưng trên hệ thống mà việc xác nhận email có tồn tại là dữ liệu nhạy cảm, phản hồi cụ thể có thể tạo rủi ro enumeration. Chọn policy phù hợp: có những luồng nên trả thông điệp trung tính. Đây là quyết định nghiệp vụ/bảo mật, không phải mặc định Spring Validation.

## 12. Error registry và tính tương thích của mã

Một bảng registry có thể xác định:

| Top-level `code` | HTTP | Mô tả | Có `violations`? |
| --- | --- | --- | --- |
| `REQUEST_VALIDATION_FAILED` | 400 | Input đã bind nhưng constraint vi phạm | Có |
| `MALFORMED_REQUEST_BODY` | 400 | Body thiếu/sai cú pháp/không parse được | Không bắt buộc |
| `REQUIRED_PARAMETER_MISSING` | 400 | Query/header/part bắt buộc thiếu | Tùy contract |
| `QUERY_PARAMETER_INVALID` | 400 | Query không thể chuyển kiểu hoặc không hợp lệ | Tùy contract |
| `REGISTRATION_NOT_FOUND` | 404 | Tài nguyên không tồn tại | Không |
| `EMAIL_ALREADY_REGISTERED` | 409 nếu policy cho phép | Xung đột nghiệp vụ | Tùy contract |
| `INTERNAL_ERROR` | 500 | Lỗi server không công khai nguyên nhân | Không |

Bảng này là **thiết kế đầy đủ hơn** so với demo. Code ở mục 6 hiện thực các nhánh `REQUEST_VALIDATION_FAILED`, `MALFORMED_REQUEST_BODY` và `INTERNAL_ERROR` cho return-value violation. Các mã còn lại cần handlers/domain logic khác nếu muốn dùng. Không hứa client nhận mọi mã ở registry khi server chưa triển khai.

Quy tắc quản lý:
- Một mã không đổi ý nghĩa giữa các version chỉ vì đổi Java exception.
- Client phải có fallback `UNKNOWN_ERROR` khi backend thêm mã mới.
- Thay public code, HTTP status của cùng lỗi, schema `violations` hoặc field path có thể là **breaking change**.
- Thay câu chữ message/locale không nhất thiết là breaking change nếu client dùng code cho hành vi.
- Bổ sung extension mới thường an toàn hơn nếu client cho phép unknown fields, nhưng vẫn phải document và test.
- Cần ghi nguyên tắc deprecate/remove codes và giữ compatibility khi nhiều client chưa cập nhật cùng lúc.

Đưa taxonomy vào OpenAPI schema và test JSON contract. Không để raw `Exception.getClass().getSimpleName()` trở thành mã public; tên class thường bị đổi qua nâng cấp thư viện/refactor.

## 13. Khi lỗi thuộc domain thay vì validation

Validation chỉ kiểm tra cấu trúc và điều kiện đầu vào. Một request hoàn toàn hợp lệ vẫn có thể không được xử lý:

~~~text
GET  /api/registrations/999 -> 404 REGISTRATION_NOT_FOUND
POST /api/registrations     -> 409 EMAIL_ALREADY_REGISTERED
POST /api/registrations     -> 400 REQUEST_VALIDATION_FAILED
~~~

Đó là **ví dụ hợp đồng nghiệp vụ**, không phải những endpoint khác đã tồn tại trong demo. Nếu thực thi, tạo exception/domain result chuyên biệt. Không bắt toàn bộ `IllegalArgumentException` rồi trả HTTP 400: có `IllegalArgumentException` là bug trong code server chứ không liên quan client.

Đoạn Java minh họa một handler riêng, cần import và định nghĩa `RegistrationNotFoundException` trong project thực tế:

~~~java
@ExceptionHandler(RegistrationNotFoundException.class)
public ResponseEntity<ProblemDetail> notFound(
        RegistrationNotFoundException ex) {

    ProblemDetail body = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    body.setTitle("Registration not found");
    body.setDetail("The requested registration does not exist.");
    body.setProperty("code", "REGISTRATION_NOT_FOUND");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
}
~~~

Nếu thêm vào subclass `ResponseEntityExceptionHandler`, handler này chỉ xử lý custom domain exception đã xác định, không trùng với built-in exception hooks. Trong sản phẩm thực, giữ detail an toàn, không lộ định danh bí mật hoặc nguyên nhân authorization mà client không được biết.

## 14. Logging, observability và hỗ trợ vận hành

Một log có cấu trúc có thể gồm:
- HTTP method, route template, status.
- Public top-level error code.
- Số lượng violations theo loại code hữu hạn.
- Trace/request ID do hạ tầng cung cấp.
- Exception class và stack trace ở hệ thống log bảo vệ, khi cần.

Tránh dùng raw input, email, user-supplied message hoặc URL nguyên bản làm metric label: cardinality cao và nguy cơ lộ thông tin. Dùng route template và error-code registry cho metrics. Client có thể đính kèm request ID khi liên hệ support; backend tra bằng ID đó trong hệ thống có phân quyền.

`ErrorResponse`/`ProblemDetail` giúp chuẩn hóa output, nhưng **không tự bảo vệ** khỏi việc bạn gắn extension nhạy cảm. Mọi property thêm vào JSON đều phải qua review tương tự field API bình thường.

## 15. Lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Client so sánh tiếng Anh trong `message` | Thiếu mã public | Dùng `code` ổn định; dịch message độc lập |
| Response chỉ có `"Validation failed"` | Không map `FieldError` | Tạo `violations` theo field + code |
| Trả raw `rejectedValue` | Sao chép object validation nội bộ | Loại khỏi public response/log |
| JSON sai cú pháp nhưng báo field lỗi | Chưa có DTO để validate | Dùng `MALFORMED_REQUEST_BODY` |
| Controller Spring 6.1 trả exception khác trước | Built-in method validation hoạt động | Xử lý cả `MethodArgumentNotValidException` và `HandlerMethodValidationException` |
| AOP controller có `@Validated` cấp class | Proxy method validation đổi đường exception | Kiểm tra class annotation và Spring version |
| Lỗi trên server return value trả 400 | Gộp input/server constraint | Trả 500, log nguyên nhân an toàn |
| `BindingResult` có lỗi object nhưng JSON bỏ qua | Chỉ lặp `getFieldErrors()` | Xử lý `getGlobalErrors()` với quy tắc field đặc biệt |
| Hai handlers cùng xử lý built-in exception | Override và `@ExceptionHandler` trùng | Dùng đúng hook trong `ResponseEntityExceptionHandler` |
| Lỗi nested field không tô đúng UI | Path format không thống nhất | Công bố dotted path hoặc JSON Pointer và map rõ |
| Test hỏng vì đổi ngôn ngữ | Assert message thay vì code | Test status/code/path; test localization riêng |
| Upgrading Spring Data/Validator đổi mã lỗi | Expose internal exception/constraint code | Mapping sang public registry code |
| 500 lộ SQL exception | Trả `ex.getMessage()` thẳng | Safe detail + requestId và log nội bộ |
| Các endpoint có `code` ở các vị trí khác nhau | Không có schema chung | Chọn top-level extension và kiểm tra JSON |
| Dự án chỉ xử lý một số 400 | Bỏ sót missing/type mismatch/multipart | Lập bảng exception chính, mở rộng advice và test |

## 16. Checklist triển khai

1. Chọn RFC 9457 hoặc một schema khác nhất quán; document status và Content-Type.
2. Tạo registry cho mã lỗi top-level và mã field-level.
3. Xác định `violations`: field path, code, message, object error và chính sách nhiều lỗi trên một field.
4. Tách lỗi parsing/conversion khỏi Bean Validation và business conflict.
5. Override `ResponseEntityExceptionHandler` đúng nhánh; không thêm handler trùng.
6. Với Spring MVC 6.1+, hỗ trợ `HandlerMethodValidationException` và phân biệt input 400/return 500.
7. Nếu có AOP `@Validated` cấp class, kiểm tra đường proxy và exceptions thực tế.
8. Không phản chiếu sensitive input/rejectedValue/stack traces.
9. Dùng MessageSource hoặc catalog UI để dịch text, giữ code bất biến.
10. Kiểm thử MockMvc cho valid request, nhiều field errors, method constraint, parse error, missing/type mismatch và domain errors.
11. Gắn correlation ID đáng tin vào observability khi cần.
12. Ghi schema/field path và policy versioning để frontend không phụ thuộc implementation.

### Câu hỏi tự kiểm tra

**1.** Tại sao không dùng `FieldError.getDefaultMessage()` làm key xử lý trong frontend?

**Trả lời:** Message có thể thay đổi theo locale, provider hoặc nội dung UX. Client phải dựa vào mã public ổn định.

**2.** `ProblemDetail.setProperty("code","...")` xuất JSON ra sao?

**Trả lời:** Với Jackson support của Spring, property mở rộng thường được unwrap thành top-level field `code` ngang với `status`, không lồng bắt buộc dưới `properties`.

**3.** `@Valid @RequestBody` lỗi thường dùng exception nào?

**Trả lời:** `MethodArgumentNotValidException` trong individual argument validation. Khi method có direct constraint ở Spring MVC 6.1+, `HandlerMethodValidationException` có thể bao cả DTO cascade.

**4.** JSON không parse được thì có nên tạo violation `field=name` không?

**Trả lời:** Không nếu parser không xác định được field chính xác. Phân loại `MALFORMED_REQUEST_BODY`, không bịa nguyên nhân field.

**5.** Khi method trả về một giá trị vi phạm return-value constraint, nên trả 400 hay 500?

**Trả lời:** 500 vì đây là lỗi contract phía server, không phải input mà client sửa được.

**6.** `requestId` nên lấy từ đâu?

**Trả lời:** Từ tracing infrastructure/servlet filter có chính sách phát sinh/xác minh, không coi input request ID tùy ý của client là bằng chứng tin cậy.

## 17. Tài liệu chính thức

- [Spring Framework — Error Responses (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html): `ProblemDetail`, `ErrorResponse`, `ResponseEntityExceptionHandler`, extension members và MessageSource.
- [Spring Framework — Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html): individual argument validation, method validation, `BindingResult` và các MVC exceptions.
- [Spring Framework — RequestBody validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): HTTP message conversion, `@Valid` và lỗi input.
- [Spring Framework — `ProblemDetail` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ProblemDetail.html): `forStatus` và `setProperty`.
- [Spring Framework — `ResponseEntityExceptionHandler` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html): các hook để override.
- [Jakarta Bean Validation Specification 3.0](https://jakarta.ee/specifications/bean-validation/3.0/): constraint, cascade và executable validation.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html): cấu trúc chuẩn và extension members.

**Ghi nhớ:** HTTP status phân loại lỗi ở mức giao thức; mã `code` của API giúp máy quyết định hành vi; `violations[].field/code` chỉ ra chỗ cần sửa; còn `message`/`detail` giúp con người hiểu vấn đề. Giữ bốn lớp thông tin này tách bạch để API dễ kiểm thử, dịch, nâng cấp và bảo vệ dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-007">Phân biệt ngoại lệ trong controller với lỗi ở Filter hoặc Servlet container</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngoại lệ trong controller với lỗi ở Filter hoặc Servlet container

## 1. Bài toán: cùng HTTP 500 nhưng có thể đi qua ba cơ chế khác nhau

Trong một ứng dụng Spring MVC chạy trên Servlet stack, request không đi thẳng vào `@RestController`. Servlet container (Tomcat, Jetty hoặc Undertow tùy cấu hình), một chuỗi `Filter`, `DispatcherServlet`, HandlerMapping, HandlerAdapter và controller có các vùng chịu trách nhiệm khác nhau. Khi lỗi xảy ra, **nơi lỗi phát sinh và đường đi của request** quyết định thành phần nào có cơ hội xử lý.

Mục tiêu bài học:

- Vẽ được luồng `client → Servlet container → FilterChain → DispatcherServlet → controller` và luồng phản hồi quay lại.
- Phân biệt ngoại lệ ném **bên trong MVC dispatch**, ngoại lệ ném **trước/sau `chain.doFilter` trong Filter**, và lỗi do **Servlet container** tự phát hiện.
- Hiểu phạm vi `@ExceptionHandler`, `@RestControllerAdvice`, `HandlerExceptionResolver`, `ResponseEntityExceptionHandler`, `/error`, `ErrorController`.
- Phân biệt `response.setStatus`, `response.sendError`, ném exception, `forward` và `ERROR dispatch`; không đánh đồng mọi status 4xx/5xx với Java exception.
- Giải thích ngoại lệ Spring Security, thứ tự Filter, `DispatcherType`, lỗi response đã commit và lỗi xử lý async.
- Tự dựng ví dụ Java 17+, Spring Boot 3.x/Spring Framework 6.x, kiểm thử MockMvc và HTTP thực, đồng thời biết giới hạn của từng loại test.

**Phạm vi:** Spring MVC trên Jakarta Servlet stack. Spring WebFlux dùng WebFilter/WebExceptionHandler và cơ chế khác; nội dung này không áp dụng nguyên xi cho WebFlux.

## 2. Sơ đồ đường đi của request và ba “biên giới”

```text
Client
  |
  v
HTTP connector / Servlet container
  |   - parse request line, headers
  |   - giới hạn request, chọn Servlet/filter mapping
  v
Servlet Filter A  ----> có thể chặn request / trả response / throw
  |
  v
Servlet Filter B  ----> có thể chặn request / trả response / throw
  |
  v
Spring Security FilterChainProxy (nếu bật)
  |   - authentication, authorization, CSRF, ...
  v
DispatcherServlet
  |   HandlerMapping -> HandlerAdapter -> Controller
  |                 \-> HandlerInterceptor
  |   HandlerExceptionResolver chain khi MVC dispatch lỗi
  v
Controller -> Service -> Repository
  |
  v
HttpMessageConverter / view rendering
  |
  +---- Response đi ngược qua filters về container/client

Nếu exception/status không được xử lý tại lớp hiện tại:
  -> Servlet container error handling / ERROR dispatch
  -> endpoint /error của Spring Boot (nếu đã cấu hình và match)
  -> HTML/JSON/error body hoặc container fallback tùy ứng dụng
```

Sơ đồ đơn giản hóa: filter ngoài security và filter bên trong `SecurityFilterChain` không nhất thiết xuất hiện theo một thứ tự cố định giống hình; thứ tự đăng ký và matching mới quyết định. `HandlerInterceptor` nằm **bên trong** vòng đời `DispatcherServlet`, còn Servlet `Filter` nằm **bên ngoài** vòng đời xử lý MVC thông thường.

### Ba trường hợp cần nhận diện

1. **MVC exception:** `@GetMapping` gọi service rồi ném `OrderBlockedException`. Nó nảy sinh khi `DispatcherServlet` đang điều phối handler; Spring MVC có thể chọn `@ExceptionHandler`/`@RestControllerAdvice`.
2. **Filter exception:** một custom Servlet Filter ném `ServletException` trước khi gọi `chain.doFilter`. MVC chưa nhận request, nên `@RestControllerAdvice` **không tự động bắt được ngoại lệ ban đầu**.
3. **Container-level error:** Tomcat từ chối request không hợp lệ, giới hạn kích thước, timeout/connection lỗi hoặc Servlet dispatch thất bại; một số sự cố xảy ra **trước khi có Servlet request/filter**. Không có bảo đảm chúng đi qua controller hay Spring MVC advice.

**Nguyên tắc chẩn đoán:** HTTP status chỉ cho biết kết quả giao thức; nó không chứng minh đã có Java exception, cũng không xác định lớp tạo response. Ví dụ 403 có thể do Spring Security, filter tự `sendError`, controller chủ động trả 403 hoặc gateway ở phía trước.

## 3. Ngoại lệ bên trong DispatcherServlet: HandlerExceptionResolver

Trong một MVC dispatch, `DispatcherServlet` phối hợp các bộ giải quyết ngoại lệ (`HandlerExceptionResolver`). Khi exception trong quá trình mapping/handling/rendering được xử lý qua đường MVC thích hợp, một resolver có thể tạo response, error view hoặc báo không xử lý được.

Các resolver phổ biến:

| Cơ chế | Vai trò | Ghi chú |
| --- | --- | --- |
| `ExceptionHandlerExceptionResolver` | Tìm `@ExceptionHandler` trong controller hoặc `@ControllerAdvice` / `@RestControllerAdvice` | Thường dùng để định dạng lỗi JSON |
| `ResponseStatusExceptionResolver` | Xử lý `@ResponseStatus` hoặc `ResponseStatusException` và cơ chế `ErrorResponse` liên quan | Status và error handling tùy phiên bản |
| `DefaultHandlerExceptionResolver` | Ánh xạ các exception Spring MVC phổ biến sang status HTTP | Ví dụ sai method, media type, input |
| `ResponseEntityExceptionHandler` | Lớp cơ sở tiện lợi cho `@ControllerAdvice` để tùy chỉnh các lỗi MVC chuẩn | Không phải Servlet Filter toàn cục |

Đây là **chuỗi các resolver**, có độ ưu tiên và khả năng thay thế/tùy chỉnh. Một resolver có thể trả `null` để nhường cho resolver tiếp theo, trả `ModelAndView` hoặc báo ngoại lệ đã được xử lý.

Ví dụ controller ném business exception:

```java
@GetMapping("/orders/{id}")
public OrderResponse read(@PathVariable long id) {
    if (id == 13) {
        throw new OrderBlockedException("Order blocked");
    }
    return new OrderResponse(id, "READY");
}
```

Đoạn này thuộc controller minh họa, không phải tệp Java độc lập. `@RestControllerAdvice` có `@ExceptionHandler(OrderBlockedException.class)` có thể trả JSON lỗi và `409 Conflict` cho lỗi này. Service cũng có thể ném exception và vẫn được advice xử lý **nếu exception lan tới MVC dispatch mà chưa bị xử lý ở nơi khác**.

Không nên nói advice chỉ bắt `throw` trực tiếp trong method controller: exception phát sinh ở service, argument resolution, interceptor hoặc phần xử lý MVC thích hợp cũng có thể được các resolver xử lý. Ranh giới chính là **MVC dispatch và việc exception thực sự đi qua resolver chain**, không đơn thuần là tên package hay file Java.

## 4. Ngoại lệ ở Filter: lý do advice không tự động bắt

Servlet API chạy các `Filter` được đăng ký trên một URL và `DispatcherType` phù hợp. Filter có thể:

- Kiểm tra request rồi **gọi** `chain.doFilter(request, response)` để chuyển tiếp.
- Kiểm tra và **không gọi chain**; tự trả status/body, chuyển hướng hoặc từ chối.
- Ném `IOException`, `ServletException` hoặc unchecked exception trong xử lý trước/sau chain.
- Bọc request/response, đặt header, ghi metrics, giải nén, kiểm tra rate limit hoặc authentication.

Ví dụ:

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
    if (request.getRequestURI().endsWith("/blocked")) {
        throw new ServletException("Failure in outer filter");
    }
    chain.doFilter(request, response);
}
```

Đây là **minh họa** trong subclass `OncePerRequestFilter` đã có imports; không tự biên dịch một mình. Nếu exception bị ném **trước `chain.doFilter`**, `DispatcherServlet` chưa được gọi và `@RestControllerAdvice` không nằm trên call stack để xử lý exception đó. Nếu exception nảy sinh **sau khi `chain.doFilter` trả về**, code đang chạy trở lại trong Filter và cũng nằm ngoài MVC dispatch.

Đặc biệt: filter có thể bắt ngoại lệ từ `chain.doFilter` bằng `try/catch`. Nhưng controller exceptions thường đã được MVC resolver xử lý trước khi quay lại filter. Vì vậy **filter không chắc nhìn thấy mọi ngoại lệ controller**, và advice không chắc thấy mọi ngoại lệ filter.

`OncePerRequestFilter` giúp kiểm soát thực thi trong những kiểu dispatch theo hợp đồng của lớp, nhưng **không đồng nghĩa chỉ chạy một lần trong toàn bộ đời sống request** với mọi tổ hợp thread/async/error dispatcher. Kiểm tra cách đăng ký và các hook `shouldNotFilterAsyncDispatch()`, `shouldNotFilterErrorDispatch()` nếu có dispatch lại.

### Không “đẩy” exception vào controller bằng cách gọi chain lần nữa

Sau khi filter gặp lỗi, việc gọi `chain.doFilter` thêm lần nữa có thể chạy lại handler, gây tác dụng phụ hoặc ghi response hai lần. Nếu muốn xử lý filter error thành API JSON, hãy chọn một trong các cách sau **một cách có chủ đích**:

1. Cho filter dùng một **error writer** riêng biết đúng HTTP status/body.
2. Từ filter gọi `HandlerExceptionResolver.resolveException(...)` mà Spring MVC cung cấp **một cách chủ động**; cách này cần kiểm soát resolver, `handler=null`, nội dung exception và response chưa commit.
3. Sử dụng `response.sendError(...)` để kích hoạt **container error handling** (nếu có mapping), sau đó để `/error`/container xây response.
4. Đối với authentication/authorization, để **Spring Security** xử lý qua `AuthenticationEntryPoint` và `AccessDeniedHandler` đúng vị trí.

Các hướng trên phục vụ mục đích khác nhau. Đừng thêm một `catch (Exception e)` trong mọi filter rồi tự trả 200 hay nuốt lỗi, và đừng gọi `@RestControllerAdvice` như một Java utility class: nó là thành phần có vòng đời và ngữ cảnh của Spring MVC.

## 5. setStatus, sendError, throw và response đã commit

Ba hành động có semantics khác nhau:

### 5.1. `response.setStatus(503)`

Đặt mã status cho response; **không tự bảo đảm** có container error dispatch. Ứng dụng có thể tiếp tục ghi JSON/body của riêng mình, hoặc hoàn tất response rỗng. Chỉ status 503 chưa chứng tỏ Java exception đã xảy ra.

### 5.2. `response.sendError(503, "reason")`

Theo Servlet API, đây là tín hiệu gửi error status cho container để thực hiện error handling tương ứng, nếu response chưa committed. Với Spring Boot servlet app có default error mapping, sau `sendError` server thật thường dùng cơ chế error page và có thể chuyển tới `/error` dưới dạng `DispatcherType.ERROR`. Nội dung/body phụ thuộc container, Boot, content negotiation và cấu hình bảo mật.

Đừng mặc định `sendError` luôn cho ra một JSON duy nhất trên Tomcat/Jetty/MockMvc, và đừng gọi `sendError` sau khi đã ghi/commit response.

### 5.3. `throw new ServletException(...)`

Ném exception lên thành phần gọi `Filter` hoặc Servlet. Nếu không ai xử lý, container có thể xử lý error page, ghi lỗi và trả 500. Nhưng ngoại lệ có thể bị filter khác/interceptor/container xử lý trước; không có bảo đảm Spring MVC advice sẽ được gọi.

### 5.4. Response committed

Response có thể được **commit** khi flush buffer, gửi header/body, stream dữ liệu hoặc vượt buffer. Sau đó status/header thường không còn có thể thay đổi như trước. `sendError` trên response đã commit có thể ném `IllegalStateException`; cố ghi lỗi JSON sau khi gửi một phần body có thể tạo response lỗi hoặc cắt kết nối.

Ví dụ anti-pattern:

```java
response.getWriter().write("partial data");
response.flushBuffer();
response.sendError(500, "failure"); // có thể IllegalStateException
```

Đây là đoạn minh họa trong Servlet/Filter method; **không** dùng production. Với streaming (`StreamingResponseBody`, `SseEmitter`, servlet output stream), nếu thất bại giữa chừng thường không còn cơ hội thay thành JSON lỗi chuẩn. Thiết kế protocol phát sự kiện/error riêng hoặc đóng stream và log, cùng telemetry phù hợp.

## 6. ERROR dispatch và endpoint /error của Spring Boot

Servlet container có thể ánh xạ lỗi tới một error page/resource. Khi error handling **dispatch** đến error page, request hiện tại có `DispatcherType.ERROR` và có thể mang các Servlet request attributes mô tả lỗi gốc.

Các thuộc tính thường dùng:

| Attribute | Nội dung |
| --- | --- |
| `jakarta.servlet.error.status_code` | HTTP status khởi phát error dispatch |
| `jakarta.servlet.error.request_uri` | URI của request ban đầu |
| `jakarta.servlet.error.exception` | Exception gây lỗi, nếu có |
| `jakarta.servlet.error.message` | Thông điệp lỗi khi có |
| `jakarta.servlet.error.servlet_name` | Servlet gốc khi có |

Các attribute là cơ chế **container error dispatch**. Chúng không bắt buộc xuất hiện ở mọi response 4xx/5xx — chẳng hạn controller trả `ResponseEntity.status(404)` trực tiếp và không có error dispatch.

Trong Spring Boot Servlet MVC, auto-configuration thường cung cấp endpoint `/error`, `BasicErrorController` và `ErrorAttributes` để tạo HTML hoặc machine-readable error response cho những tình huống đi qua error handling path. Nó không có nghĩa **mọi** exception đã nảy sinh từ `@Controller` sẽ đến `/error`: nếu `@ExceptionHandler`/resolver đã xử lý và ghi response, không cần một error dispatch khác.

Một đường đi điển hình:

```text
REQUEST dispatcher
  -> Filter
  -> response.sendError(503) hoặc unresolved exception
  -> container xử lý error-page mapping

ERROR dispatcher (nếu cấu hình và còn xử lý được)
  -> các Filter áp dụng cho ERROR (có thể khác REQUEST)
  -> DispatcherServlet
  -> /error (Boot BasicErrorController nếu được đăng ký)
  -> error response
```

Spring Boot có `ErrorPageFilter` cho một số trường hợp triển khai WAR với Servlet container bên ngoài, nhưng **không nhầm nó với mọi application filter** hay mặc định cơ chế mà mọi ứng dụng embedded Tomcat phải đi qua. Cách cụ thể phải xem hình thức deployment.

Trong Spring Boot 3, tên thuộc tính Servlet dùng namespace `jakarta.servlet.*`, không phải `javax.servlet.*` của dự án Boot 2 cũ.

## 7. Filter khi ERROR dispatch: tránh xử lý lặp

`DispatcherType` trong Jakarta Servlet có các giá trị `REQUEST`, `FORWARD`, `INCLUDE`, `ASYNC`, `ERROR`. Container chọn filter dựa trên mapping và dispatcher types đã đăng ký.

Nếu một filter kiểm tra authentication/logging cho `REQUEST` và được đăng ký cả `ERROR`, nó có thể chạy lần thứ hai trên error dispatch. Tình huống thường thấy:

- Original request `/api/payments` bị từ chối trước MVC.
- Container tạo `ERROR` dispatch tới `/error`.
- Authentication filter lại chặn `/error` hoặc custom filter lại ném exception.
- Client thấy 403/500 khác hẳn lỗi ban đầu; log có thể bị ghi hai lần hoặc vòng lặp error handling.

Cần quyết định rõ:

1. Filter nào áp dụng `REQUEST`? Filter nào cần `ASYNC` hoặc `ERROR`?
2. Với `OncePerRequestFilter`, override `shouldNotFilterErrorDispatch()`/`shouldNotFilterAsyncDispatch()` phù hợp với mục đích và thiết kế bảo mật.
3. Nếu error endpoint phải phục vụ cho request bị từ chối, cần cấu hình security authorization phù hợp; không mở các endpoint nhạy cảm ngoài phạm vi.
4. Khi đặt trace/correlation ID, sử dụng request attribute hoặc context truyền tiếp thích hợp qua các dispatch; tránh tạo ID mới cho cùng một request một cách vô tình.

**Không nên cho mọi filter chạy ERROR bằng mặc định**. Đồng thời không loại bỏ mọi security check trên ERROR dispatch nếu nó có thể dẫn đến lộ lỗi nội bộ. Chọn theo hợp đồng và kiểm thử end-to-end.

## 8. Spring Security: exception thuộc filter chain riêng

Spring Security Servlet dựa trên `FilterChainProxy` và các security filters. Hai luồng lỗi trọng yếu:

- **Authentication failure / người gọi chưa xác thực:** `AuthenticationEntryPoint` thường khởi động cơ chế yêu cầu đăng nhập hoặc trả 401. Tùy cơ chế có thể redirect, Basic challenge hoặc JSON; không mặc định mọi ứng dụng trả JSON 401.
- **Authenticated nhưng không có quyền:** `AccessDeniedHandler` thường trả 403.
- `ExceptionTranslationFilter` dịch `AuthenticationException` và `AccessDeniedException` từ phần thích hợp của **security filter chain phía sau nó** thành hành vi HTTP tương ứng.

Không được kết luận rằng `@ExceptionHandler(AuthenticationException.class)` trong controller advice sẽ xử lý tất cả lỗi Spring Security: nhiều lỗi xảy ra **trước MVC dispatch**. Ngược lại, một business exception trong controller có thể do advice xử lý mà `ExceptionTranslationFilter` không tham gia.

Đặc biệt:

- `ExceptionTranslationFilter` không bắt tùy ý mọi lỗi `RuntimeException`; nó dành cho nhóm exception authentication/authorization và các đường xử lý hỗ trợ.
- Không mọi Filter trong `SecurityFilterChain` đều chạy **sau** `ExceptionTranslationFilter`. Thứ tự quan trọng. Authentication filter phía trước có thể tự gọi failure handler; dùng architecture/debug logs để xác định thực tế.
- `CsrfFilter` có thể chặn POST trước khi controller; response 403 khi thiếu CSRF token không phải lỗi từ `@Valid @RequestBody`.
- CORS preflight `OPTIONS` có thể bị chặn ở security/filter khi cấu hình sai; không suy ra controller không hỗ trợ OPTIONS.
- Những exception từ method security trong controller/service có thể đi qua cơ chế riêng tùy proxy/advice/MVC và vị trí gọi. Chẩn đoán bằng **stack trace**, filter order và resolved exception; đừng gắn mọi AccessDeniedException với một công đoạn duy nhất.
- Nếu API định nghĩa cấu trúc lỗi JSON đồng nhất, cấu hình `AuthenticationEntryPoint` và `AccessDeniedHandler` (hoặc error writer chuyên dụng) theo contract, đồng thời giữ header liên quan authentication theo chuẩn.

## 9. Xây ví dụ Java 17+ để quan sát ba nguồn lỗi

Ứng dụng demo minh họa cụ thể:

- `GET /demo/ok` → controller trả 200.
- `GET /demo/controller-fail` → controller ném `DemoBusinessException`, `@RestControllerAdvice` bắt và trả JSON `409`.
- `GET /demo/filter-send-error` → Filter **không gọi MVC**, dùng `sendError(503)`; container error handling có thể xử lý `/error`.
- `GET /demo/filter-throw` → Filter ném `ServletException` **trước DispatcherServlet**; advice không bắt trực tiếp lỗi gốc; container có thể trả 500 khi xử lý HTTP thực.
- `GET /demo/missing` → URL không có mapping chuyên biệt, thường 404, không nhất thiết có business exception.

Dùng dự án Maven Spring Boot **3.x + Spring Web**, Java 17+ và starter test. Trên Boot 3, import Servlet từ `jakarta.servlet.*`. Đặt các lớp sau cùng package `com.example.demo`.

### 9.1. Lớp khởi động

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

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

### 9.2. Exception nghiệp vụ

Tệp `src/main/java/com/example/demo/DemoBusinessException.java`:

```java
package com.example.demo;

public class DemoBusinessException extends RuntimeException {
    public DemoBusinessException(String message) {
        super(message);
    }
}
```

Đây là lỗi dùng riêng cho demo, không phải tên exception chuẩn của Spring. Trong ứng dụng thật, không nên quyết định status HTTP chỉ bằng việc mọi exception đều là `RuntimeException`; cần mã lỗi nghiệp vụ cụ thể.

### 9.3. Controller và DTO trả về

Tệp `src/main/java/com/example/demo/DemoController.java`:

```java
package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @GetMapping("/demo/ok")
    public Result ok() {
        return new Result("OK");
    }

    @GetMapping("/demo/controller-fail")
    public Result controllerFail() {
        throw new DemoBusinessException("Order cannot be changed");
    }

    @GetMapping("/demo/filter-send-error")
    public Result filterSendError() {
        // Nếu Filter chặn đúng, method này không được gọi.
        return new Result("SHOULD_NOT_REACH_CONTROLLER");
    }

    @GetMapping("/demo/filter-throw")
    public Result filterThrow() {
        // Nếu Filter ném lỗi trước chain, method này không được gọi.
        return new Result("SHOULD_NOT_REACH_CONTROLLER");
    }

    public record Result(String state) {}
}
```

### 9.4. @RestControllerAdvice cho lỗi MVC

Tệp `src/main/java/com/example/demo/DemoExceptionAdvice.java`:

```java
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DemoExceptionAdvice {

    @ExceptionHandler(DemoBusinessException.class)
    public ResponseEntity<ApiError> onBusinessError(
            DemoBusinessException ex,
            HttpServletRequest request) {

        ApiError body = new ApiError(
                "ORDER_BLOCKED",
                ex.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    public record ApiError(
            String code,
            String message,
            String path) {}
}
```

Với `/demo/controller-fail`, response JSON thể hiện cấu trúc `ApiError` và status `409 Conflict`. Không làm `@ExceptionHandler(Exception.class)` bắt tất cả lỗi mà tự động trả 400/409; việc đó che lỗi lập trình nghiêm trọng và làm mất đúng semantics 500.

Trong ứng dụng thật, `ex.getMessage()` có thể chứa dữ liệu nội bộ hoặc thông tin người dùng; chỉ sử dụng thông điệp đã được thiết kế an toàn công khai. Ở demo, chuỗi exception cố định do code đặt, không lấy từ stack trace hay SQL.

### 9.5. Filter nằm trước DispatcherServlet

Tệp `src/main/java/com/example/demo/DemoBoundaryFilter.java`:

```java
package com.example.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoBoundaryFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        if ("/demo/filter-send-error".equals(path)) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Simulated failure in servlet filter");
            return;
        }

        if ("/demo/filter-throw".equals(path)) {
            throw new ServletException("Failure before MVC dispatcher");
        }

        chain.doFilter(request, response);
    }
}
```

Giải thích:

- `@Component` đăng ký một Servlet Filter bean trong Spring Boot, `@Order` đặt thứ tự. Ứng dụng thật có security/filter khác; thứ tự chính xác cần xem các registration.
- Cả hai nhánh lỗi xảy ra **trước** `chain.doFilter`, nên controller không chạy.
- Với `sendError`, Filter không ném exception; container thực có thể kích hoạt error dispatch tới `/error`.
- Với `throw`, exception thoát Filter; MVC resolver không được gọi cho lỗi gốc.
- `substring(contextPath.length())` phù hợp cho demo đơn giản; với nhiều servlet mapping/forwarded path, nên dùng API/matcher path chính xác thay vì so khớp chuỗi tùy ý.
- `@Order(Ordered.HIGHEST_PRECEDENCE)` dùng để làm ví dụ sớm trong chain, **không phải khuyến nghị áp dụng vô điều kiện** cho authentication hoặc logging filter production.
- `OncePerRequestFilter` mặc định có cơ chế quyết định bỏ qua async/error dispatch; app container/test có thể có các dispatcher registrations khác nhau.

## 10. Thử bằng curl trên Servlet container thực

Chạy Maven Wrapper từ dự án Boot:

```bash
./mvnw spring-boot:run
```

Trên Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Các file Java ở trên cần Spring Boot Web trên classpath; không thể `javac DemoController.java` độc lập khi thiếu dependency. Cổng mặc định là 8080 nếu `server.port` chưa đổi.

### 10.1. Controller thành công

```bash
curl -i http://localhost:8080/demo/ok
```

Dự kiến 200 với body JSON:

```json
{"state":"OK"}
```

### 10.2. Controller exception được advice xử lý

```bash
curl -i http://localhost:8080/demo/controller-fail
```

Dự kiến `409 Conflict` và JSON tương đương:

```json
{
  "code": "ORDER_BLOCKED",
  "message": "Order cannot be changed",
  "path": "/demo/controller-fail"
}
```

Tên/trật tự field do Jackson serialize có thể khác; hãy so sánh giá trị thay vì byte-for-byte. Đây là đường lỗi MVC được `DemoExceptionAdvice` xử lý trực tiếp; thông thường không cần container `ERROR` dispatch.

### 10.3. Filter sendError

```bash
curl -i http://localhost:8080/demo/filter-send-error
```

Dự kiến **HTTP 503 Service Unavailable** do Filter phát. Body có thể là JSON Boot `/error`, HTML hoặc response rỗng tùy cấu hình/Accept. Đừng kỳ vọng body `ORDER_BLOCKED` của `DemoExceptionAdvice`: advice không xử lý tình huống gốc này. Khi container khởi tạo error dispatch, có thể đọc `jakarta.servlet.error.status_code` và `jakarta.servlet.error.request_uri` trong handler error.

### 10.4. Filter throw

```bash
curl -i http://localhost:8080/demo/filter-throw
```

Với cấu hình container mặc định, exception không xử lý thường dẫn tới **HTTP 500 Internal Server Error**; body do container hoặc Boot error handling cung cấp, nếu còn khả năng tạo response. Không cam kết mọi deployment sẽ có cùng error body và log. Nếu một Filter khác bắt `ServletException` thì kết quả có thể khác.

### 10.5. Không có route

```bash
curl -i http://localhost:8080/demo/missing
```

Thường 404, có thể do MVC/resource handler/Boot error pathway tạo ra. **Không suy ra** rằng 404 luôn do `DemoBusinessException` hoặc controller advice gây ra. Nếu reverse proxy can thiệp, 404 có thể xảy ra trước khi request vào Spring app.

## 11. Kiểm thử MockMvc: mức độ mô phỏng có giới hạn

MockMvc chạy Spring MVC và các filters được đăng ký với môi trường test, nhưng **không tương đương mọi cơ chế error dispatch của một Servlet container đang lắng nghe cổng HTTP thực**. Đặc biệt, nếu Filter gọi `sendError`, MockMvc có thể xác nhận status nhưng **không tự dispatch tới Boot `/error` giống Tomcat**. Exception ném từ Filter có thể được đẩy ra thẳng cho test thay vì được container chuyển thành HTTP 500.

Tạo `src/test/java/com/example/demo/DemoBoundaryTest.java` với dependency `spring-boot-starter-test`:

```java
package com.example.demo;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoBoundaryTest {

    @Autowired
    MockMvc mvc;

    @Test
    void controllerBusinessExceptionGoesThroughAdvice() throws Exception {
        mvc.perform(get("/demo/controller-fail"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_BLOCKED"));
    }

    @Test
    void filterCanEndRequestBeforeMvc() throws Exception {
        mvc.perform(get("/demo/filter-send-error"))
                .andExpect(status().isServiceUnavailable());
        // MockMvc không nhất thiết tự chạy container /error dispatch.
    }

    @Test
    void rawFilterExceptionEscapesMockMvcDispatch() {
        assertThrows(ServletException.class,
                () -> mvc.perform(get("/demo/filter-throw")));
        // Nếu một filter khác/custom exception handler đã bao lỗi,
        // hãy chỉnh assertion theo cấu hình thực tế.
    }

    @Test
    void normalRequestStillReachesController() throws Exception {
        mvc.perform(get("/demo/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"));
    }
}
```

Chạy:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

Kiểm thử trên là **mã nguồn mẫu dự kiến chạy với Boot 3.x/Web/Test mặc định**, không phải báo cáo rằng đã thực thi ở máy của bạn. Nếu app dùng Spring Security, custom filter registration, phương thức async hoặc thay đổi converter, kiểm thử phải thích nghi.

### Khi cần chứng minh container error dispatch

Dùng bài test tích hợp **HTTP thực** với `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` và client HTTP (ví dụ `TestRestTemplate` trong Boot 3 hoặc Java `HttpClient`), hoặc chạy ứng dụng thật rồi dùng `curl`. Điều này giúp thấy:

- Status cuối cùng sau `sendError` hoặc unresolved Filter exception.
- Response body/media type do `/error` tạo ra.
- `ERROR` dispatch và filter registration thực tế.
- Sự khác biệt giữa app embedded và WAR trên container bên ngoài.

Không dựa một bài test MockMvc đơn lẻ để khẳng định quá trình container forwarding/error page là đúng trên production.

## 12. Có nên tự gọi HandlerExceptionResolver trong Filter?

Đôi khi API muốn mọi lỗi filter trả cùng JSON với MVC. Filter có thể **inject** bean `HandlerExceptionResolver` của MVC và gọi rõ ràng:

```java
// Minh họa bên trong custom Filter với resolver đã inject.
try {
    chain.doFilter(request, response);
} catch (DomainRejectedException ex) {
    resolver.resolveException(request, response, null, ex);
}
```

Đây là **đoạn mô tả pattern**, không phải tệp độc lập. Chỉ hợp lý khi:

1. `resolver` là bean phù hợp (thường cần chỉ định `@Qualifier("handlerExceptionResolver")` nếu có nhiều bean).
2. Error type thực sự cần cơ chế MVC, đã có handler tương thích, và handler có thể hoạt động khi `handler=null`.
3. Response **chưa commit**, không có body bị ghi một phần.
4. Đã kiểm tra kết quả `resolveException` có được giải quyết hay không; trả `null` có nghĩa vẫn chưa xử lý, không được âm thầm nuốt exception.
5. Đã kiểm soát không trùng log, không lộ nguyên exception và không nuốt lỗi security/hệ thống.

Một số `@ExceptionHandler` phụ thuộc vào controller cụ thể nên không xử lý được khi `handler=null`; advice toàn cục phù hợp hơn. Cách này tạo coupling từ Filter sang MVC, nên cân nhắc lại việc xử lý ngay ở security layer, dùng writer dùng chung hoặc dựa vào container `/error`.

**Cảnh báo:** Không bọc mọi `Exception` từ toàn bộ `chain.doFilter` và gọi resolver lần hai vô điều kiện: MVC có thể đã xử lý lỗi, response có thể committed, và việc ghi tiếp sẽ làm hỏng output. Ví dụ chỉ nêu cách tích hợp khi đã chủ động thiết kế.

## 13. Quan sát log và dấu vết request

Một response 500 đơn độc chưa đủ để biết nguồn. Khi debug:

1. Xác nhận request có tới app chưa bằng gateway/access log.
2. Tìm correlation/request ID qua ingress, Filter, `DispatcherServlet`, controller và error dispatch.
3. Ghi log **đúng nơi bắt lỗi**: filter log lỗi của nó; business exception handler log theo severity thích hợp; container log unresolved exception.
4. So sánh `request.getDispatcherType()`: `REQUEST`, `ERROR` hay `ASYNC`.
5. Trong `ERROR` dispatch, xem `jakarta.servlet.error.request_uri`, `jakarta.servlet.error.status_code` và exception (nếu có).
6. Xem response có committed chưa trước khi cố thay status/body.
7. Xem filter registration và order; `@Order` của filter bean, `FilterRegistrationBean` và security chain đều có thể ảnh hưởng.
8. Xem stack trace cẩn thận: Filter/Servlet container/MVC resolver xuất hiện ở đâu? Spring controller method có được gọi không?
9. Nếu response 401/403, xem authentication entry point/denied handler/CSRF; không vội sửa controller advice.
10. Dùng một request test tái hiện rồi thay đổi **một yếu tố mỗi lần** (Accept, credentials, URL, body size, dispatcher type).
11. Kiểm tra error body đến từ `/error`, `@RestControllerAdvice`, gateway hay reverse proxy.
12. Tránh log token, cookie, Authorization header hoặc nguyên request body chỉ để debug exception.

Có thể tạm bật logging phù hợp trong môi trường test để xem MVC handler mapping, security filter chain và controller advice, rồi hạ log level trước production. Thông điệp lỗi công khai nên dùng code ổn định và thông tin an toàn; stack trace để server-side logging.

## 14. Bảng so sánh phân loại trách nhiệm

| Nguồn lỗi | Đã tới DispatcherServlet? | @RestControllerAdvice bắt gốc theo mặc định? | Thành phần thường quyết định response |
| --- | --- | --- | --- |
| Controller/service ném exception trong MVC dispatch | Có | **Có thể**, nếu handler phù hợp | HandlerExceptionResolver, advice, hoặc fallback |
| Sai JSON trong `@RequestBody` | Có (ở argument resolution) | **Có thể**, qua handler cho exception MVC | Resolver/advice, thường 400 |
| Sai HTTP method/Accept/media type trong MVC mapping | Có nếu tới MVC | **Có thể**, tùy handler/resolver | MVC resolver, 405/406/415 thường gặp |
| Custom Filter ném trước `chain.doFilter` | Chưa | **Không tự động** | Filter khác, container error handling |
| Custom Filter `sendError` rồi dừng chain | Chưa | **Không tự động** | Servlet container và `/error` nếu có |
| Custom Filter ném sau khi downstream trả | Downstream có thể đã hoàn tất | **Không tự động** | Filter/container; chú ý committed |
| Spring Security authentication/authorization chặn trước MVC | Thường chưa | **Không tự động** | EntryPoint/AccessDeniedHandler/filter |
| Container từ chối malformed HTTP request trước Servlet | Không | Không | Container/gateway |
| Container timeout/connection reset hoặc lỗi ghi response | Tùy thời điểm | Không chắc; thường không còn body có thể đổi | Container/network |
| Async task ném sau khi request thread rời | Tùy cách async được quản lý | **Tùy cơ chế async** | MVC async dispatch resolver hoặc container |

Đừng dùng bảng như một tuyên bố rằng advice chỉ xử lý exception phát sinh ngay trong method controller. Resolver chain hoạt động trong vòng đời `DispatcherServlet` và có thể giải quyết một số lỗi do mapping, argument binding, rendering hoặc interceptor.

## 15. Lỗi async và deferred processing

Spring MVC hỗ trợ return types như `Callable<T>`, `DeferredResult<T>`, `WebAsyncTask<T>` và streaming. Khi `Callable` do Spring MVC quản lý ném exception, framework có thể dispatch lại vào servlet với kết quả lỗi và sử dụng MVC exception handling; đây **khác** một thread tự spawn bên ngoài quản lý lifecycle của request.

Đừng giả định `@RestControllerAdvice` bắt mọi exception của `CompletableFuture.runAsync` tự chạy, callback ngoài request hay message listener. Một task độc lập có thể thất bại sau khi request đã hoàn tất; cần xử lý bằng future completion, executor error handling và telemetry riêng.

Các lưu ý:

- Cấu hình Servlet Filter theo `ASYNC` dispatch nếu cần bảo toàn tracing/security logic, nhưng tránh xử lý lặp ngoài ý muốn.
- Khi response đã commit và đang stream, exception handler thường không thể đổi nội dung thành ProblemDetail mới.
- Khi timeout async, Spring MVC có cơ chế timeout/interceptor/exception riêng; status có thể khác theo cấu hình.
- Không gửi một response mới từ background thread sau khi container đã đóng request.
- MockMvc async test có thể cần `asyncDispatch` để kiểm tra lần dispatch tiếp theo; chỉ gọi `mvc.perform(...)` lần đầu không bao phủ toàn bộ lifecycle.

## 16. Thiết kế error contract: đồng nhất nhưng vẫn đúng tầng

API thực tế có thể muốn cùng một format lỗi cho MVC, Security và container:

```json
{
  "code": "REQUEST_REJECTED",
  "message": "Request cannot be processed",
  "traceId": "2e1d8f..."
}
```

**Định dạng giống nhau không có nghĩa một handler bắt mọi nguồn lỗi.** Có thể dùng:

- **MVC layer:** `@RestControllerAdvice` hoặc `ResponseEntityExceptionHandler` cho validation, conversion, service/business exception; có thể dùng `ProblemDetail`.
- **Security layer:** `AuthenticationEntryPoint`/`AccessDeniedHandler` cho 401/403. Chú ý WWW-Authenticate khi dùng Basic/Bearer theo hợp đồng.
- **Filter layer:** error writer chuyên dụng hoặc xử lý có chủ đích, không viết response hai lần.
- **Container/error path:** cấu hình Boot `ErrorAttributes`/`ErrorController` hoặc server error pages để có fallback an toàn.

Một writer dùng chung có thể giúp giữ `code`, `message`, `traceId` nhất quán, nhưng hãy truyền rõ status và không để tầng business tự quyết định Servlet API. Không biến lỗi 500 thành 400, không coi 401/403 là 500, không lộ raw exception/stack trace ra client.

Với Spring 6+, `ProblemDetail` giúp thể hiện các thuộc tính chuẩn `type`, `title`, `status`, `detail`, `instance` và extension fields. RFC 9457 là chuẩn Problem Details hiện đại. Nếu chọn dạng này, đảm bảo HTTP status ngoài response khớp nội dung `status` và content type phù hợp `application/problem+json` khi ghi JSON. Error page của container/Boot không tự động đồng nhất với mọi ProblemDetail do MVC tạo ra.

## 17. Các bẫy thường gặp

1. **“@RestControllerAdvice bắt mọi lỗi web”:** Sai. Nó là cơ chế MVC; filter/container có thể lỗi trước hoặc sau dispatch.
2. **“Mọi 500 đều là exception controller”:** Sai. Container, filter, gateway, async hoặc serializer cũng có thể gây 500.
3. **“Chỉ cần Filter ném ResponseStatusException là thành JSON giống MVC”:** Không mặc định. Exception ném trước DispatcherServlet không tự vào MVC resolver chain.
4. **“sendError và setStatus giống nhau”:** Không. sendError liên quan Servlet error mechanism, setStatus chỉ đặt mã.
5. **“status 404 luôn do exception”:** Không. Có thể response có status mà không có exception.
6. **“Một Filter chạy đúng một lần mỗi HTTP request trong mọi dispatch”:** Không nên giả định. Kiểm tra REQUEST/ERROR/ASYNC và behavior của OncePerRequestFilter.
7. **“MockMvc thể hiện y hệt container error page”:** Không. MockMvc không thay thế test HTTP thực cho ERROR dispatch.
8. **“Có ExceptionTranslationFilter thì mọi RuntimeException thành 401/403”:** Không. Nó tập trung vào authentication/authorization exceptions trong security chain.
9. **“Lỗi JSON syntax là 500 do Jackson”:** Sai trong đa số API MVC thông thường; input không đọc được thường 400 khi framework xử lý.
10. **“HandlerExceptionResolver luôn trả JSON”:** Không. Có resolver tạo view, đặt status, hoặc nhường cho resolver/container khác.
11. **“Ghi body trước rồi catch lỗi trả JSON mới”:** Không an toàn khi response đã commit.
12. **“Filter có thể gọi chain lại để controller xử lý exception”:** Dễ double execution/double write; không làm vậy.
13. **“Thiết lập /error nghĩa là lỗi MVC đã xử lý vẫn chạy /error”:** Không nhất thiết; cần phân biệt MVC resolver xử lý và container error dispatch.
14. **“Tắt bảo mật với mọi ERROR dispatch là vô hại”:** Cần đánh giá rò rỉ thông tin và policy; chỉ cấu hình đúng phạm vi.
15. **“Log mọi request body để tìm nguyên nhân”:** Có thể lộ PII/token; log có chọn lọc và ẩn dữ liệu nhạy cảm.
16. **“Tăng thứ tự filter cao nhất luôn đúng”:** Filter order phụ thuộc mục tiêu; cấu hình sai khiến security, CORS, request wrappers và tracing không làm việc mong muốn.
17. **“ResponseEntity.status(500) chứng tỏ container đã gặp exception”:** Không. Controller có thể chủ động trả status.
18. **“@ExceptionHandler(Exception.class) trả 200 để client không lỗi”:** Phá HTTP semantics và observability; phải phân loại status đúng.

## 18. Quy trình điều tra thực tế

Khi nhận log “500 Internal Server Error” hoặc client nói error body không giống chuẩn:

1. **Xem request đã vào container chưa** bằng access log và tracing từ gateway. Nếu không, xử lý ở ingress/network trước.
2. **Xem filter có chạy không:** thêm log an toàn có correlation id ở đầu một Filter chuyên observability, không log token/body.
3. **Xem DispatcherServlet có chọn handler không:** bật log mapping phù hợp, hoặc quan sát `HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE` khi cần và còn request context.
4. **Xem controller/service có được gọi không:** một breakpoint/log ở entrypoint không đủ để kết luận lỗi binding/argument conversion vì chúng xảy ra trước khi method chạy.
5. **Xem exception resolver có chạy không:** log các MVC resolver/advice; nếu exception từ Filter trước chain, thông thường không.
6. **Xem `getDispatcherType()` là REQUEST hay ERROR:** cùng URL có thể xuất hiện hai dispatch trong một lỗi.
7. **Xem response đã commit chưa:** nếu đã commit, lỗi body/status có thể không sửa được.
8. **Xem Security exception handling:** với 401/403, phân tích filter order, exception translation, entrypoint/denied handler, CSRF và CORS.
9. **Xem fallback /error:** Boot ErrorAttributes và container error page có thể quyết định body cuối.
10. **Kiểm tra hai kiểu test:** MockMvc để kiểm chứng MVC advice/filter interaction, HTTP server thật để kiểm tra Servlet ERROR dispatch.
11. **Kiểm tra cấu hình deployment:** embedded Tomcat/Jetty so với WAR ngoài container; có thể khác registration/error page.
12. **Chốt root cause theo ranh giới:** sửa ở lớp thực sự sở hữu lỗi — validation trong MVC, authentication ở security, filter-specific error handling hoặc giới hạn/container configs.

Ghi nhớ: **nơi exception xảy ra**, **ai có thể nhìn thấy nó**, **ai viết response cuối** là ba câu hỏi riêng. Việc phân tách ba câu hỏi giúp tránh viết một `@RestControllerAdvice` cực rộng nhưng vẫn bỏ sót lỗi Filter và container.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Một `@RestController` ném `DemoBusinessException` và advice có `@ExceptionHandler` phù hợp. Ai xử lý?

**Đáp án:** Thông thường `ExceptionHandlerExceptionResolver` của MVC gọi advice để tạo response. Controller method đã được gọi.

**Câu 2.** Custom Filter ném `ServletException` trước khi gọi `chain.doFilter`. Advice có tự bắt không?

**Đáp án:** Không. `DispatcherServlet` chưa xử lý request, nên lỗi gốc không tự tới resolver của MVC. Filter khác hoặc Servlet container có thể xử lý.

**Câu 3.** Filter gọi `response.sendError(503)` và return. Response có luôn giống JSON của advice không?

**Đáp án:** Không. Error handling của container/Boot `/error` tùy cấu hình quyết định, và không có guarantee về body.

**Câu 4.** Tại sao `mvc.perform` đôi khi ném trực tiếp exception Filter trong test, còn gọi HTTP qua Tomcat trả 500?

**Đáp án:** MockMvc mô phỏng MVC/filter nhưng không hoàn toàn thực thi cơ chế Servlet container error page/ERROR dispatch. HTTP server thực xử lý lỗi chưa bắt từ Servlet/Filter.

**Câu 5.** Status 403 trước controller có chắc là `@Valid` thất bại không?

**Đáp án:** Không. Nó thường liên quan security/CSRF/authorization/filter/gateway. Validation input điển hình gây 400, không tự động 403.

**Câu 6.** Status `200 OK` có thể đi kèm một Java exception đã bị xử lý không?

**Đáp án:** Có nếu custom handler viết sai status hoặc cố tình trả 200; nhưng đó thường là thiết kế API kém. Không dùng 200 cho lỗi nghiệp vụ cần được biểu diễn bằng status lỗi thích hợp.

**Câu 7.** Vì sao `response.sendError` sau `flushBuffer()` là nguy hiểm?

**Đáp án:** Response đã committed; không còn an toàn để đổi status/body, có thể gây `IllegalStateException` hoặc mất kết nối.

**Câu 8.** Khi nào ERROR dispatch có thể kích hoạt Filter lần nữa?

**Đáp án:** Nếu container dispatch tới error page và filter được đăng ký cho `DispatcherType.ERROR`, trừ khi logic Filter bỏ qua dispatch phù hợp.

**Câu 9.** `ExceptionTranslationFilter` dùng để chuyển mọi `RuntimeException` thành 401?

**Đáp án:** Không. Nó xử lý chủ yếu `AuthenticationException` và `AccessDeniedException` từ vị trí phù hợp trong Spring Security chain.

**Câu 10.** Nếu muốn API có lỗi đồng nhất ở cả controller, security và filter, có nên chỉ viết một `@RestControllerAdvice` không?

**Đáp án:** Không đủ. Cần thiết kế các điểm tạo response phù hợp ở MVC, Spring Security, filter và container fallback, thống nhất error schema và status.

## 20. Tài liệu tham khảo

- Spring Framework — MVC exception resolver chain và container error page: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html
- Spring Framework 6.2 — Controller Advice: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-advice.html
- Spring Framework — Error Responses và ProblemDetail: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Framework — DispatcherServlet: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html
- Spring Security — Servlet FilterChainProxy và ExceptionTranslationFilter: https://docs.spring.io/spring-security/reference/servlet/architecture.html
- Spring Security — Exception Handling: https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter
- Spring Boot — Servlet Web Error Handling: https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.error-handling
- Spring Boot — ErrorAttributes API: https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/servlet/error/ErrorAttributes.html
- Spring Boot — ErrorPageFilter API (WAR/non-embedded): https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/servlet/support/ErrorPageFilter.html
- Jakarta Servlet specification và DispatcherType: https://jakarta.ee/specifications/servlet/
- Jakarta Servlet 6.0 DispatcherType API: https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/dispatchertype
- RFC 9457 — HTTP API Problem Details: https://www.rfc-editor.org/rfc/rfc9457.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-handling-008">Ẩn thông tin nhạy cảm, gắn mã đối chiếu và tránh trả stack trace cho người dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC — Ẩn thông tin nhạy cảm, gắn mã đối chiếu và tránh trả stack trace cho người dùng

## 1. Mục tiêu

Khi request thất bại, API phải giúp client hiểu **điều gì cần làm tiếp**, còn người vận hành phải tìm được **nguyên nhân kỹ thuật**. Hai đối tượng này cần mức thông tin khác nhau.

Ví dụ phản hồi nguy hiểm:

~~~json
{
  "error": "SQLException: password for jdbc://db.internal:5432/app is secret123",
  "stackTrace": "at com.example.payment.internal.DatabaseAdapter.save(DatabaseAdapter.java:84)",
  "authorization": "Bearer eyJ..."
}
~~~

Phản hồi an toàn hơn:

~~~json
{
  "code": "INTERNAL_ERROR",
  "message": "Không thể xử lý yêu cầu. Vui lòng thử lại sau.",
  "requestId": "6efdbf4c-55a5-4d3c-a04a-4f0f9abce235",
  "violations": []
}
~~~

HTTP status thực vẫn là 500. Mã đối chiếu giúp đội vận hành tìm sự kiện tương ứng trong log/tracing có kiểm soát; client không cần thấy exception class, source path, SQL hoặc stack trace.

Sau bài này, bạn có thể:

- Phân biệt **thông tin công khai trong HTTP response** với **dữ liệu chẩn đoán nội bộ**.
- Liệt kê những nguồn rò rỉ thường gặp: exception message, stack trace, error page, URL/query, validation rejected value, logs, headers, framework defaults và proxy.
- Thiết kế mã lỗi ổn định (`code`) và mã đối chiếu từng request (`requestId` hoặc `traceId`); hiểu hai mã phục vụ hai mục đích khác nhau.
- Tạo request ID do server kiểm soát, trả ở header `X-Request-ID` và error JSON, đưa vào MDC để đối chiếu log; dọn MDC bằng `finally` hoặc `MDC.putCloseable`.
- Dùng `@RestControllerAdvice` và `ResponseEntityExceptionHandler` để trả thông điệp an toàn cho các lỗi đã phân loại, giữ nguyên HTTP status cần thiết.
- Cấu hình Spring Boot tránh xuất stack trace, exception message và binding errors trong đường xử lý lỗi mặc định.
- Kiểm thử rằng response không chứa thông tin giả lập nhạy cảm và request ID trong body khớp header.

**Bối cảnh:** Spring MVC Servlet stack, Java 17+, Spring Boot 3.5.0 / Spring Framework 6.2. Một số ví dụ là mã độc lập trong dự án Maven đầy đủ ở mục 9; các đoạn ngắn khác được đánh dấu "minh họa".

## 2. Điều gì có thể bị lộ khi API trả lỗi?

| Dữ liệu | Rủi ro nếu lộ trong response | Cách xử lý |
| --- | --- | --- |
| Stack trace, source file, line number | Tiết lộ cấu trúc ứng dụng, thư viện, luồng xử lý | Ghi vào kênh chẩn đoán nội bộ phù hợp; không nhúng vào body |
| `exception.getMessage()` | Có thể chứa SQL, host, token, thông tin người dùng hoặc tham số nội bộ | Dùng public message cố định/đã kiểm duyệt |
| SQL và connection string | Lộ schema, host, account, database topology | Không gửi cho client; lọc secret cả ở log |
| Password, API key, bearer token, cookie | Đánh cắp quyền truy cập | Không echo/log raw value; redact hoặc loại bỏ |
| HTTP query/body | Có thể chứa email, mã xác nhận, token, dữ liệu cá nhân | Không log toàn bộ mặc định; whitelist metadata thật sự cần |
| Validation rejected value | Có thể mang PII/secret hoặc payload độc hại, rất dài | Trả tên field và mã lỗi an toàn; không trả giá trị thô |
| Internal exception class/package | Lộ chi tiết triển khai mà client không cần | Dùng `code` ổn định ở cấp API |
| Trace/span metadata nội bộ | Có thể tiết lộ kiến trúc hay định danh nội bộ | Chỉ trả ID đối chiếu đã được đánh giá an toàn |
| Response headers | Version server, thông tin debug hoặc token | Kiểm tra cả headers, không chỉ JSON body |
| HTML error page | Có thể hiển thị stack trace hoặc detail | Cấu hình error handling cho cả browser/HTML route |

**Nguyên tắc:** response là **giao diện công khai**; mọi chuỗi từ exception, request, database và dependency đều phải được coi là không đáng tin trước khi đưa ra ngoài. Một lỗi 4xx cũng có thể rò rỉ thông tin, không riêng 5xx.

## 3. Phân biệt error code, request ID và trace ID

Ba loại định danh thường bị nhầm:

| Tên | Nghĩa | Độ ổn định | Ai sử dụng |
| --- | --- | --- | --- |
| HTTP status | Nhóm/ý nghĩa kết quả HTTP | Theo HTTP semantics | HTTP client, proxy, monitor |
| `code` / error code | Loại lỗi nghiệp vụ/kỹ thuật trong contract, ví dụ `INVALID_INPUT`, `ACCOUNT_CONFLICT` | Ổn định qua nhiều request | Client hiển thị/xử lý lỗi |
| `requestId` / correlation ID | Định danh một lần xử lý request | Khác nhau giữa các request | Support, log search |
| Distributed `traceId` | Định danh trace gồm nhiều span qua các dịch vụ | Chung trong cùng distributed trace | Tracing/observability |

Ví dụ hai request đều có code `INTERNAL_ERROR` nhưng có hai `requestId` khác nhau. Người dùng cung cấp request ID để support tìm đúng lần thất bại; client dùng error code để biết cách xử lý tự động. Không sử dụng exception class làm error code công khai.

Một contract đơn giản:

~~~json
{
  "code": "INVALID_INPUT",
  "message": "Dữ liệu yêu cầu không hợp lệ.",
  "requestId": "9e9912e6-f2c3-44c7-a6e2-118b6fbfcf4c",
  "violations": [
    {"field": "email", "code": "EMAIL_INVALID"}
  ]
}
~~~

Trong ví dụ này, `message` là thông điệp đã chọn cho client; `violations` chỉ cho biết trường và loại lỗi, **không echo địa chỉ email sai**. HTTP status đi trong response HTTP (400), không cần lặp trong body nếu API contract không yêu cầu.

### 3.1. Request ID do ai sinh ra?

Có hai chiến lược phổ biến:

1. **Server-generated:** server tạo UUID ngẫu nhiên và dùng nhất quán trong request. Đơn giản và phù hợp demo.
2. **Trusted gateway-generated:** gateway tạo ID, bảo đảm format/độ dài/nguồn tin cậy, truyền vào backend; backend có thể giữ và đưa vào MDC.

Không lấy tùy ý `X-Request-ID` từ một client không tin cậy rồi đưa thẳng vào log/header. Dữ liệu do client điều khiển có thể quá dài, chứa ký tự điều khiển, newline hoặc gây trùng/spoof ID. Nếu cần nhận ID từ upstream, phải giới hạn nguồn, độ dài, bảng ký tự và chính sách thay thế khi không hợp lệ.

**Request ID khác authorization token.** Nó nên là mã đối chiếu ngẫu nhiên không mang PII, secret, user ID, internal host hoặc thời gian đoán được. Tuy nhiên ID vẫn có thể làm lộ việc hai log cùng một request; quyết định phạm vi hiển thị hợp lý và không cho phép chỉ dựa vào ID để truy cập log của người khác.

### 3.2. Correlation ID xuyên dịch vụ

Nếu A gọi B và B gọi C, có thể truyền context tracing chuẩn W3C như `traceparent` theo hệ thống tracing đang dùng. `traceId` của distributed trace không nhất thiết là UUID hoặc bằng custom `requestId`; đừng khởi tạo hệ ID cạnh tranh nếu hệ thống đã có Micrometer Tracing/OpenTelemetry.

Một mô hình:

~~~text
Browser ── request ──> Gateway ──> Service A ──> Service B
                            │           │              │
                        traceId       traceId        traceId
                        requestId     spanId         spanId
                            │
                public X-Request-ID response
~~~

Một trace có nhiều span; riêng request ID có thể được dùng theo từng request hoặc edge request theo chính sách. Nếu kết nối logs với traces, cấu hình log pattern/structured logging đọc MDC/tracing context ở từng service thay vì giả định tự động lan truyền.

## 4. HTTP lỗi nên tiết lộ thông tin ở mức nào?

| Tình huống | HTTP | Public `code` ví dụ | Public message hợp lý | Không nên trả |
| --- | --- | --- | --- | --- |
| JSON sai cú pháp | 400 | `INVALID_JSON` | Không đọc được dữ liệu JSON | Nội dung parser stacktrace, toàn bộ body |
| DTO sai validation | 400 | `INVALID_INPUT` | Dữ liệu yêu cầu không hợp lệ | Raw rejected password/token |
| Không tìm thấy resource | 404 | `RESOURCE_NOT_FOUND` | Không tìm thấy tài nguyên | DB table/key nội bộ |
| Xung đột trạng thái domain | 409 | `STATE_CONFLICT` | Thao tác không hợp lệ ở trạng thái hiện tại | Tên service/method nội bộ |
| Resource thiếu quyền | 403 hoặc policy-specific 404 | `ACCESS_DENIED` | Không có quyền thực hiện | Thông tin tài khoản/role bí mật |
| Rate limit | 429 | `RATE_LIMITED` | Đã vượt giới hạn, vui lòng thử lại sau | Cấu hình nội bộ không cần công bố |
| DB hoặc dependency tạm hỏng | 503 nếu phù hợp | `SERVICE_UNAVAILABLE` | Dịch vụ tạm thời không khả dụng | DB host, credentials, raw driver error |
| Exception bất ngờ | 500 | `INTERNAL_ERROR` | Đã xảy ra lỗi máy chủ | Stack trace, source path, runtime details |

Lỗi đăng nhập và đăng ký cũng có thể cần **câu trả lời có độ chi tiết giới hạn** để tránh user/account enumeration. Ví dụ yêu cầu khôi phục mật khẩu nên dùng thông điệp chung khi chính sách bảo mật cần che giấu việc email có tồn tại. Cách đó thuộc thiết kế security flow; không thể giải quyết bằng việc ẩn stacktrace.

Đừng bao giờ đổi lỗi hệ thống thành HTTP 200 chỉ để client có một JSON "đẹp":

~~~java
// Phản ví dụ: HTTP status không phản ánh kết quả.
try {
    return service.update();
} catch (Exception ex) {
    return ResponseEntity.ok(Map.of(
            "success", false,
            "error", ex.getMessage()));
}
~~~

Đoạn này chỉ minh họa anti-pattern; vừa sai status, vừa có nguy cơ lộ message. Hãy tách exception semantics và mapping HTTP phù hợp.

## 5. Spring Boot: cấu hình lỗi mặc định an toàn

Với Spring Boot 3.5, trong `src/main/resources/application.properties`:

~~~properties
server.error.include-stacktrace=never
server.error.include-message=never
server.error.include-binding-errors=never
server.error.include-exception=false
server.error.include-path=never
server.error.whitelabel.enabled=false
~~~

Ý nghĩa:

- `include-stacktrace=never`: không cho đường xử lý lỗi mặc định thêm thuộc tính trace vào response, kể cả khi request cố kích hoạt qua query parameter.
- `include-message=never`: không đưa exception message ra body mặc định.
- `include-binding-errors=never`: không thêm binding errors nguyên dạng.
- `include-exception=false`: không thêm exception class name.
- `include-path=never`: không tự thêm request path (có thể có token/PII ở path).
- `whitelabel.enabled=false`: tắt trang lỗi HTML mặc định dạng Whitelabel của Boot; **không có nghĩa sẽ không có bất kỳ HTML error page nào** vì có thể do servlet container/proxy/frontend cung cấp.

Đây là cấu hình **đường lỗi Boot mặc định**, không tự động lọc response bạn tự dựng bằng `@ExceptionHandler` hoặc `ResponseEntity`. Nếu code gọi `ex.getMessage()` đưa vào JSON, các cấu hình trên không cứu được. Cần kiểm tra proxy, framework error handlers, Security filter chain và exception ở tầng response serialization.

**Không dùng** trong production:

~~~properties
# Phản ví dụ: tăng rủi ro lộ thông tin.
server.error.include-stacktrace=always
server.error.include-message=always
server.error.include-binding-errors=always
~~~

Tùy phiên bản, các thuộc tính include dạng `on_param` cho phép client kích hoạt chi tiết qua request parameter. Tránh chọn kiểu này cho môi trường public. Không coi việc tắt `spring.mvc.problemdetails.enabled` là cơ chế bảo mật; ProblemDetail vẫn có thể được tự dựng với detail nguy hiểm.

## 6. Xử lý exception mà không phơi thông tin

Trong Spring MVC:

- `@RestControllerAdvice` gom logic chuyển **exception → HTTP response** cho controllers thuộc scope.
- `@ExceptionHandler` xử lý domain/system exception mà ứng dụng chủ động định nghĩa.
- `ResponseEntityExceptionHandler` cung cấp các handler cho nhiều lỗi Spring MVC có sẵn (body unreadable, validation, 405/415…).
- `ProblemDetail` chuẩn RFC 9457 là một lựa chọn error contract khác; không bắt buộc dùng song song với custom DTO.
- `@ResponseStatus` có thể map exception/status, nhưng nếu cần kiểm soát JSON an toàn, advice thường rõ ràng hơn.

Ví dụ minh họa handler domain:

~~~java
@ExceptionHandler(DuplicateUsernameException.class)
ResponseEntity<SafeError> duplicate(
        DuplicateUsernameException ex,
        HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new SafeError(
                    "USERNAME_UNAVAILABLE",
                    "Không thể đăng ký tên người dùng này.",
                    requestId(request),
                    List.of()));
}
~~~

Ở đây **không** nối `ex.getMessage()` vào body. Đoạn code này là method trong advice, chưa là file độc lập; dự án hoàn chỉnh ở mục 9.

### 6.1. Có nên dùng catch-all Exception.class?

Nếu `@ExceptionHandler(Exception.class)` trả 500 cho mọi exception, nó có thể vô tình **che mất HTTP status hợp lệ** của framework (405, 406, 415 hoặc các exception đã là `ErrorResponse`). Nếu dùng `ResponseEntityExceptionHandler`, tránh khai báo exception handler trùng với mapping sẵn có vì có thể gây ambiguous mapping.

Có thể chọn một trong các cách sau:

- Kế thừa `ResponseEntityExceptionHandler` và override các hook chính xác, giữ HTTP status gốc nhưng thay body thành DTO an toàn.
- Định nghĩa handler rõ cho các domain/system exceptions đã xác định; để Boot/framework xử lý lỗi không dự đoán được với cấu hình giấu chi tiết.
- Nếu cần một catch-all public 500 thống nhất, thiết kế fallback không đè lên các exception HTTP có status sẵn có và kiểm thử đủ 400/404/405/406/415/422/500.

**Lưu ý:** exceptions sinh ra trong servlet filters (đặc biệt Spring Security filter chain) hoặc khi response đã commit không nhất thiết đi qua controller advice. Security 401/403 thường do `AuthenticationEntryPoint` và `AccessDeniedHandler` xử lý; cần cùng chính sách ẩn lỗi và correlation ID tại đó.

## 7. Logging: đủ để debug, ít dữ liệu nhất có thể

Log nội bộ có thể ghi thông tin mà client không được thấy, nhưng **log cũng là nơi lưu trữ nhạy cảm**. Cần kiểm soát truy cập, retention, redaction và transmission.

Một mẫu log cấu trúc:

~~~text
level=ERROR event=checkout_failed errorCode=INTERNAL_ERROR
requestId=6efdbf4c-55a5-4d3c-a04a-4f0f9abce235
route=/api/checkouts method=POST status=500
exceptionClass=PaymentAdapterException
~~~

Dữ liệu trong ví dụ là minh họa. Stack trace nội bộ có thể hữu ích nhưng có nguy cơ chứa dữ liệu trong exception messages và stack context. Chỉ log exception đầy đủ ở nơi đã có policy xử lý secret, phân quyền, retention và scrub thích hợp.

**Nên log:**

- Event name, stable error code, HTTP status, route template, method, thời lượng, request ID / trace ID.
- Exception class và stacktrace trong kênh bảo vệ (nếu cần, theo mức log phù hợp).
- Dependency name **đã được whitelist**, timeouts, retry count, outcome.

**Không log mặc định:**

- `Authorization`, `Cookie`, `Set-Cookie`, API key, password, OTP, token; đặc biệt không log toàn bộ HTTP headers.
- Toàn bộ query/body JSON nếu chứa PII hoặc thông tin thanh toán.
- JDBC URL mang password, SQL statements có giá trị nhạy cảm, raw third-party response.
- Định danh người dùng/địa chỉ IP không cần thiết cho case; nếu có nhu cầu pháp lý/vận hành phải tuân theo chính sách bảo vệ dữ liệu.

**Whitelist tốt hơn blacklist:** log các metadata được định nghĩa rõ thay vì ghi mọi trường rồi cố thay tất cả secret bằng dấu sao. Secrets có thể xuất hiện ở key không đoán trước, nested JSON, exception cause, URL và cả file path.

### 7.1. Tránh log injection và cardinality bùng nổ

Khi log dữ liệu không tin cậy (nếu thực sự cần), cấu trúc hóa và sanitize control characters để một giá trị chứa newline không giả tạo thêm log entry. Không lấy raw URL/query làm metric label (các id khác nhau gây high cardinality). Dùng route template như `/api/orders/{id}`.

Nếu có log correlation ID, hãy đặt kích thước tối đa hợp lý; ví dụ này tự sinh UUID nên không phải xử lý chuỗi tùy ý từ client.

## 8. MDC và request-scoped correlation ID

MDC (Mapped Diagnostic Context) của SLF4J lưu các cặp key/value gắn với **thread hiện tại**. Trong Servlet MVC đồng bộ, filter có thể thêm `requestId` để các log statements trong cùng thread đọc được:

~~~java
try (MDC.MDCCloseable ignored =
        MDC.putCloseable("requestId", requestId)) {
    filterChain.doFilter(request, response);
}
~~~

`MDC.putCloseable` tự gỡ entry khi thoát scope, kể cả exception. Nếu dùng `MDC.put(...)`, phải có `finally { MDC.remove("requestId"); }` để không rò context qua thread pool.

Ví dụ log pattern:

~~~properties
logging.pattern.level=%5p [rid:%X{requestId}]
~~~

Đây chỉ minh họa đưa MDC vào console pattern; nếu dùng structured JSON logging, cần cấu hình field tương ứng. Với Spring Boot + Micrometer Tracing, log pattern và MDC có thể đã có traceId/spanId; đừng ghi đè các key tracing đang được framework quản lý.

### 8.1. MDC có giới hạn quan trọng

MDC thường dựa trên ThreadLocal; **không tự lan truyền an toàn qua** `@Async`, `CompletableFuture`, thread pool, scheduled tasks, virtual threads hoặc reactor contexts. Nếu request chuyển thread, cần cơ chế context propagation phù hợp với công nghệ đang dùng (Micrometer Context Propagation, executor task decorator, OpenTelemetry context), rồi dọn context đúng scope.

Một request ID dùng cho cả chuỗi request không có nghĩa là mỗi async task tự thấy MDC đó. Nếu servlet request bước vào ERROR hoặc ASYNC dispatch mới, `OncePerRequestFilter` có cờ `shouldNotFilterErrorDispatch()` / `shouldNotFilterAsyncDispatch()` và dispatcher registration cần được kiểm tra; demo ở mục 9 thiết kế cho synchronous MVC handlers thông thường.

### 8.2. Khi nào trả mã cho client?

- HTTP response header, ví dụ `X-Request-ID: <uuid>`, giúp support kiểm tra bằng DevTools và client logs.
- Error body field `requestId` với **đúng ID trong header**; test tự động phải đối chiếu hai chỗ.
- Thành công cũng có thể trả header để hỗ trợ điều tra mọi lần gọi; đừng bắt client phải tạo lỗi mới lấy được ID.
- Nếu đã dùng W3C Trace Context, cân nhắc publish `traceId` được phép sử dụng, phân biệt với internal span id và policy observability.

Trong browser, nếu cần JavaScript đọc `X-Request-ID` qua CORS, server cần cấu hình `Access-Control-Expose-Headers: X-Request-ID` với origin policy phù hợp. Không mở CORS bừa hoặc dùng `*` với credentials nếu không phù hợp.

## 9. Dự án Spring Boot Java 17 chạy được

Dự án hoàn chỉnh minh họa **server-generated correlation ID**, **safe error response**, **validation và JSON parse errors**, **domain conflict**, lỗi 500 giả lập và test không rò stacktrace.

Cấu trúc:

~~~text
safe-error-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/SafeErrorApplication.java
    │   └── resources/application.properties
    └── test/java/demo/SafeErrorApiTest.java
~~~

### 9.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>safe-error-demo</artifactId>
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
~~~

### 9.2. application.properties

~~~properties
server.error.include-stacktrace=never
server.error.include-message=never
server.error.include-binding-errors=never
server.error.include-exception=false
server.error.include-path=never
server.error.whitelabel.enabled=false

logging.pattern.level=%5p [rid:%X{requestId}]
~~~

### 9.3. SafeErrorApplication.java

Đặt tất cả code sau trong **một file** `src/main/java/demo/SafeErrorApplication.java`. Chỉ có một top-level `public class`, các thành phần còn lại package-private.

~~~java
package demo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@SpringBootApplication
public class SafeErrorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeErrorApplication.class, args);
    }
}

@Component
class RequestIdFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    static final String HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        // Không tin cậy X-Request-ID tự gửi từ client.
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);

        try (MDC.MDCCloseable ignored =
                     MDC.putCloseable("requestId", requestId)) {
            chain.doFilter(request, response);
        }
    }
}

record RegistrationRequest(
        @NotBlank String username,
        @NotBlank @Email String email) {
}

record FieldIssue(String field, String code) {
}

record SafeError(
        String code,
        String message,
        String requestId,
        List<FieldIssue> violations) {
}

class UsernameUnavailableException extends RuntimeException {
    UsernameUnavailableException() {
        super("Username is unavailable");
    }
}

class InternalDemoException extends RuntimeException {
    InternalDemoException() {
        // Đây chỉ là giá trị giả dùng để kiểm thử chống rò thông tin.
        super("SYNTHETIC_DEMO_SECRET: connection failed at db.internal");
    }
}

@RestController
@RequestMapping("/api/demo")
class RegistrationController {
    // Kho dữ liệu trong RAM, chỉ dành cho demo.
    private final Set<String> usedNames = ConcurrentHashMap.newKeySet();

    RegistrationController() {
        usedNames.add("taken");
    }

    @PostMapping(path = "/register",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegistrationRequest request) {

        String key = request.username().toLowerCase(Locale.ROOT);
        if (!usedNames.add(key)) {
            throw new UsernameUnavailableException();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "created"));
    }

    @GetMapping(path = "/unexpected",
                produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> unexpected() {
        throw new InternalDemoException();
    }

    @GetMapping(path = "/ping",
                produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}

@RestControllerAdvice
class SafeErrorAdvice extends ResponseEntityExceptionHandler {
    private static final Logger LOG =
            LoggerFactory.getLogger(SafeErrorAdvice.class);

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return id instanceof String s ? s : "unavailable";
    }

    private static String requestId(WebRequest request) {
        Object id = request.getAttribute(
                RequestIdFilter.ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        return id instanceof String s ? s : "unavailable";
    }

    private static SafeError error(
            String code, String message, String id, List<FieldIssue> issues) {
        return new SafeError(code, message, id, issues);
    }

    @ExceptionHandler(UsernameUnavailableException.class)
    ResponseEntity<SafeError> usernameUnavailable(
            UsernameUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error(
                        "USERNAME_UNAVAILABLE",
                        "Không thể đăng ký tên người dùng này.",
                        requestId(request), List.of()));
    }

    @ExceptionHandler(InternalDemoException.class)
    ResponseEntity<SafeError> unexpected(
            InternalDemoException ex, HttpServletRequest request) {
        String id = requestId(request);

        // Log nội bộ theo policy, không trả ex.getMessage() cho client.
        LOG.error("Unexpected internal error requestId={}", id, ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR",
                        "Không thể xử lý yêu cầu. Vui lòng thử lại sau.",
                        id, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        SafeError body = error(
                "INVALID_JSON",
                "Không đọc được dữ liệu JSON.",
                requestId(request),
                List.of());
        return super.handleExceptionInternal(
                ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<FieldIssue> issues = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(SafeErrorAdvice::fieldIssue)
                .distinct()
                .toList();

        SafeError body = error(
                "INVALID_INPUT",
                "Dữ liệu yêu cầu không hợp lệ.",
                requestId(request),
                issues);

        return super.handleExceptionInternal(
                ex, body, headers, status, request);
    }

    private static FieldIssue fieldIssue(FieldError error) {
        // Whitelist mã constraint, không trả rejectedValue hay raw message.
        String code = switch (error.getCode() == null
                ? "" : error.getCode()) {
            case "NotBlank" -> "REQUIRED_TEXT";
            case "Email" -> "INVALID_EMAIL";
            default -> "INVALID_VALUE";
        };
        return new FieldIssue(error.getField(), code);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // Với lỗi MVC chưa tùy biến: giữ nguyên HTTP status/headers.
        // Không tái sử dụng body/error detail do exception cung cấp.
        SafeError safe = error(
                status.is5xxServerError()
                        ? "INTERNAL_ERROR" : "REQUEST_ERROR",
                status.is5xxServerError()
                        ? "Không thể xử lý yêu cầu."
                        : "Yêu cầu không được chấp nhận.",
                requestId(request),
                List.of());

        return super.handleExceptionInternal(
                ex, safe, headers, status, request);
    }
}
~~~

**Lưu ý kỹ thuật khi đọc code:**

- `handleMethodArgumentNotValid` và `handleHttpMessageNotReadable` gọi thẳng `super.handleExceptionInternal(...)`, nên giữ body an toàn đã chọn mà không bị `handleExceptionInternal` override chung thay thế.
- Override `handleExceptionInternal` dùng cho các lỗi MVC khác do base class chuyển đến, giữ **status và headers framework** (ví dụ `Allow` của 405), đồng thời thay body bằng thông điệp trung tính. Tùy xử lý content negotiation, một số lỗi như 406 có thể không có response body; status vẫn là điều quan trọng.
- Không khai báo `@ExceptionHandler(Exception.class)` chung khiến status framework bị làm thành 500.
- Ví dụ internal error cố ý có chuỗi `SYNTHETIC_DEMO_SECRET`; chỉ nhằm xác minh chuỗi đó **không xuất hiện ở HTTP response**. Đây không phải một mật khẩu thật.
- Filter sinh UUID mới, không nhận header do client gửi, giảm spoof/log injection; `MDC.putCloseable` dọn context khi kết thúc.
- `Set` trong RAM được thêm atomically cho demo; trong sản phẩm thật dùng persistence uniqueness constraint/transaction và quy tắc bảo mật chống username enumeration.

### 9.4. Chạy và thử bằng curl

Chạy ở thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Thử thành công:

~~~bash
curl -i 'http://localhost:8080/api/demo/ping'
~~~

HTTP 200, body tương đương:

~~~json
{"status":"ok"}
~~~

Ngoài body, header `X-Request-ID` chứa một UUID do server sinh.

Thử JSON body hợp lệ:

~~~bash
curl -i -X POST 'http://localhost:8080/api/demo/register' \
  -H 'Content-Type: application/json' \
  --data '{"username":"lan","email":"lan@example.com"}'
~~~

HTTP 201, body `{"status":"created"}`. Nếu gọi lại cùng username (không phân biệt hoa/thường), HTTP 409, response tương đương:

~~~json
{
  "code":"USERNAME_UNAVAILABLE",
  "message":"Không thể đăng ký tên người dùng này.",
  "requestId":"6efdbf4c-55a5-4d3c-a04a-4f0f9abce235",
  "violations":[]
}
~~~

UUID ở đây chỉ là **ví dụ về định dạng**; runtime sinh UUID mới mỗi request. Request ID trong header và body của **cùng một request** phải khớp chính xác.

Thử lỗi JSON cú pháp:

~~~bash
curl -i -X POST 'http://localhost:8080/api/demo/register' \
  -H 'Content-Type: application/json' \
  --data '{"username":"lan","email":'
~~~

HTTP 400 với `code=INVALID_JSON`, message an toàn và requestId; response không chứa đoạn JSON raw client gửi.

Thử validation với email không hợp lệ:

~~~bash
curl -i -X POST 'http://localhost:8080/api/demo/register' \
  -H 'Content-Type: application/json' \
  --data '{"username":"","email":"not-an-email"}'
~~~

HTTP 400, `code=INVALID_INPUT` và violations tương đương:

~~~json
[
  {"field":"username","code":"REQUIRED_TEXT"},
  {"field":"email","code":"INVALID_EMAIL"}
]
~~~

Thứ tự lỗi không được đảm bảo. Đặc biệt không có `"not-an-email"` trong body, tránh phản chiếu giá trị mà client gửi.

Thử lỗi hệ thống giả lập:

~~~bash
curl -i 'http://localhost:8080/api/demo/unexpected'
~~~

HTTP **500**, public body có `code=INTERNAL_ERROR` và thông điệp trung tính; **không có** chuỗi `SYNTHETIC_DEMO_SECRET`, `db.internal`, class name hoặc Java stacktrace. Console/log được bảo vệ có thể nhận exception stacktrace cùng requestId để chẩn đoán.

Các lệnh trên dùng cú pháp Bash. Trên Windows PowerShell, dùng `curl.exe` và thay dấu nối dòng `\` bằng cú pháp thích hợp hoặc viết cùng một dòng.

### 9.5. Kiểm thử MockMvc: header và body khớp ID, không rò exception

File `src/test/java/demo/SafeErrorApiTest.java`:

~~~java
package demo;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SafeErrorApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode assertCorrelation(MvcResult result) throws Exception {
        String header = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(header);
        UUID.fromString(header); // Ném lỗi nếu không phải UUID hợp lệ.

        JsonNode body = mapper.readTree(
                result.getResponse().getContentAsString());
        assertEquals(header, body.path("requestId").asText());
        return body;
    }

    @Test
    void malformedJsonReturnsSafe400() throws Exception {
        MvcResult result = mvc.perform(post("/api/demo/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"lan\",\"email\":"))
            .andExpect(status().isBadRequest())
            .andReturn();

        JsonNode body = assertCorrelation(result);
        assertEquals("INVALID_JSON", body.path("code").asText());
        assertFalse(result.getResponse().getContentAsString()
                .contains("HttpMessageNotReadableException"));
    }

    @Test
    void validationDoesNotEchoRejectedValues() throws Exception {
        MvcResult result = mvc.perform(post("/api/demo/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"","email":"private-input@example.invalid!"}
                    """))
            .andExpect(status().isBadRequest())
            .andReturn();

        JsonNode body = assertCorrelation(result);
        assertEquals("INVALID_INPUT", body.path("code").asText());
        assertFalse(result.getResponse().getContentAsString()
                .contains("private-input@example.invalid!"));
    }

    @Test
    void internalErrorHidesStacktraceAndExceptionMessage() throws Exception {
        MvcResult result = mvc.perform(get("/api/demo/unexpected"))
            .andExpect(status().isInternalServerError())
            .andReturn();

        JsonNode body = assertCorrelation(result);
        assertEquals("INTERNAL_ERROR", body.path("code").asText());

        String serialized = result.getResponse().getContentAsString();
        assertFalse(serialized.contains("SYNTHETIC_DEMO_SECRET"));
        assertFalse(serialized.contains("db.internal"));
        assertFalse(serialized.contains("InternalDemoException"));
        assertFalse(serialized.contains("SafeErrorApplication.java"));
        assertFalse(serialized.contains("stackTrace"));
        assertFalse(serialized.contains("\"trace\""));
    }

    @Test
    void clientCannotChooseCorrelationId() throws Exception {
        MvcResult result = mvc.perform(get("/api/demo/unexpected")
                .header("X-Request-ID", "attacker-controlled"))
            .andExpect(status().isInternalServerError())
            .andReturn();

        JsonNode body = assertCorrelation(result);
        assertFalse("attacker-controlled".equals(
                body.path("requestId").asText()));
    }

    @Test
    void successAlsoHasRequestIdHeader() throws Exception {
        MvcResult result = mvc.perform(get("/api/demo/ping"))
            .andExpect(status().isOk())
            .andReturn();
        String header = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(header);
        UUID.fromString(header);
        assertEquals("{\"status\":\"ok\"}",
                result.getResponse().getContentAsString());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Các test xác nhận **hành vi HTTP**, không phụ thuộc UUID cố định. Test JSON so sánh field code và ID; riêng body của endpoint thành công chỉ có một field status nên có thể so sánh trực tiếp. Với API thực tế, nên dùng JSON tree/JSONPath và kiểm tra response headers toàn bộ.

**Giới hạn của bộ kiểm thử:** Demo bao phủ synchronous MVC handlers trong `MockMvc`. Nó chưa chứng minh error dispatch qua container, filter chain có Spring Security, async thread context, gateway reverse proxy, response bị commit một phần, hoặc lỗi từ actuator. Với ứng dụng thật, viết thêm integration tests chạy embedded server + security/proxy, kiểm tra cả JSON, HTML error, headers và logs.

## 10. Điều gì vẫn có thể rò dù đã có @RestControllerAdvice?

### 10.1. Error trước khi vào DispatcherServlet

Authentication failures xảy ra trong Spring Security filters có thể bỏ qua controller advice. Nếu muốn response JSON thống nhất, cấu hình `AuthenticationEntryPoint` cho 401 và `AccessDeniedHandler` cho 403, rồi áp dụng cùng safe error code và request ID. Security layer cần xác định error detail nào có thể công bố; không trả stacktrace hay chi tiết lý do xác thực mật khẩu thất bại.

### 10.2. Error trong serializer sau khi handler trả kết quả

Controller đã trả một object, nhưng Jackson không serialize được vì object graph có vòng hoặc getter ném exception. Nếu output đã ghi/headers đã commit, exception handler không thể bảo đảm thay bằng JSON lỗi 500. Vì vậy phải tránh serialize entity graph không kiểm soát; dùng response DTO, test các loại content. Với streaming response/file download/SSE, mô hình báo lỗi khác với JSON body thông thường.

### 10.3. Proxy, CDN, container error pages

Nginx, API gateway, load balancer hoặc servlet container có thể tự tạo trang lỗi HTTP khi backend chết hoặc timeout. Cấu hình lỗi của Spring Boot **không kiểm soát toàn bộ các tầng này**. Kiểm tra các mã 502/503/504 từ gateway; đảm bảo trang HTML không tiết lộ upstream host, nội dung debug hay exception details.

### 10.4. Error details trong observability và alerting

- APM/tracing có thể tự thu request attributes và exception events; cấu hình tắt/cắt PII, bearer token, password.
- Log aggregation và third-party alerting là các điểm truyền dữ liệu; có quyền truy cập và retention riêng.
- Thông điệp alert nên chứa error code/trace ID và liên kết dashboard, không copy toàn bộ request payload.
- Khi phân tích sự cố, không gửi log chứa dữ liệu thật lên issue công khai; redact trước khi chia sẻ.

### 10.5. Correlation ID và bảo mật

Không bao giờ coi `X-Request-ID` như một "mật khẩu" cho endpoint tra log công khai. Chỉ nhân viên được phân quyền mới được tra log phù hợp; rate limit endpoint hỗ trợ nếu có. Nếu exposure của ID tạo khả năng liệt kê, dùng ID ngẫu nhiên đủ entropy như UUIDv4, không dùng id tăng dần hoặc user id thô.

## 11. Tình huống trước/sau

### Sai: đưa dữ liệu dependency thẳng cho client

~~~java
try {
    database.save(entity);
} catch (Exception ex) {
    return ResponseEntity.status(500).body(Map.of(
            "error", ex.getMessage(),
            "trace", Arrays.toString(ex.getStackTrace())));
}
~~~

Đoạn minh họa có `database`, `entity` chưa định nghĩa; đây là anti-pattern. Nó làm lộ stack trace và exception details.

### Đúng: thông điệp an toàn + mã tra cứu

~~~java
try {
    database.save(entity);
} catch (KnownDatabaseOutage ex) {
    String requestId = currentRequestId();
    safeLogger.error("Database operation unavailable; rid={}",
            requestId, ex);
    throw new DependencyUnavailableException();
}
~~~

Đây là pseudo-code trong service/adapter (các helper và exception minh họa), cho thấy adapter phân loại **lỗi đã xác định** và giữ cause ở kênh nội bộ. Web advice ánh xạ nó thành status thích hợp và safe body; không tự in message dependency ra HTTP. Với lỗi bất ngờ, đừng chuyển bừa tất cả thành `DependencyUnavailableException`; giữ nguyên cause để vận hành thấy bug.

**Cẩn thận:** việc log `ex` có thể bao gồm dữ liệu nhạy cảm trong message hoặc cause. Chỉ log exception đầy đủ nơi đã có chính sách che secret, hạn chế người xem, thời hạn lưu và logging middleware phù hợp. Nếu dependency đưa secret vào exception, điều chỉnh/redact từ adapter thay vì đẩy nguyên văn vào log chung.

## 12. Một checklist cho API production

1. **Error contract:** 4xx/5xx đúng nghĩa, `code` ổn định, `message` an toàn, request/trace ID nhất quán, field errors không echo raw values.
2. **Default error safety:** cấu hình Boot `include-stacktrace/message/binding-errors` về `never` và `include-exception=false`; kiểm tra cả HTML và proxy errors.
3. **Explicit handlers:** không chuyển `ex.getMessage()`, `Throwable.toString()`, `StackTraceElement[]` hoặc raw SQL sang body.
4. **Correlation:** sinh ID đủ ngẫu nhiên hoặc chỉ chấp nhận từ trusted gateway; kiểm tra header/body khớp và log có thể tra cứu.
5. **MDC lifecycle:** dọn MDC ngay khi kết thúc; kiểm tra context propagation khi async/virtual threads/tracing.
6. **Logs:** whitelist metadata; redact secret/PII; bảo vệ quyền truy cập, thời hạn lưu, truyền tải, backup.
7. **Security:** 401/403 filter chain có error response an toàn; không lộ việc account tồn tại khi policy yêu cầu.
8. **Validation:** phân biệt JSON parse và Bean Validation; không trả raw rejectedValue hoặc toàn bộ body.
9. **Integration:** test thành công, 400, 404, 405, 415, 409, 500, errors trước controller, errors trong serialization và gateway; không bỏ qua response headers.
10. **Review:** xem error contract như API công khai, ghi rõ các error codes trong tài liệu và kiểm tra security review trước khi triển khai.

## 13. Bài tập

**Bài 1 — Audit response:** Phát hiện dữ liệu nên ẩn trong:

~~~json
{
  "message": "org.postgresql.util.PSQLException: password authentication failed for service-account",
  "path": "/reset?token=abc123",
  "debug": "at com.acme.account.ResetService.reset(ResetService.java:77)"
}
~~~

Thiết kế một body 500 an toàn có `code` và `requestId`.

**Bài 2 — Correlation:** Một browser gửi hai HTTP requests lỗi 500. Giải thích vì sao chúng nên có hai request IDs khác nhau nhưng có thể cùng error code; viết test khẳng định body requestId = response header.

**Bài 3 — MDC:** Cho một request dùng servlet thread pool. Hệ quả khi quên `MDC.remove("requestId")` trong `finally` là gì? Viết phiên bản dùng `MDC.putCloseable`.

**Bài 4 — Async:** Handler `CompletableFuture.supplyAsync(...)` log không có requestId. Giải thích vì sao ThreadLocal MDC không tự đi qua executor; đề xuất context propagation phù hợp.

**Bài 5 — Validation:** Request có `password` chưa đạt constraint. Đề xuất JSON errors chỉ nêu field `password`, error code phù hợp, không phản chiếu password. Kiểm tra test không chứa rejectedValue.

**Bài 6 — Framework:** So sánh behavior của `@RestControllerAdvice` với lỗi `HttpMessageNotReadableException` và lỗi xác thực phát sinh trong Spring Security filter. Xác định handler chính xác cho mỗi đường.

**Bài 7 — Observability:** Thiết kế log event cho database timeout gồm error code, requestId, route template, status và duration; chỉ ra chỗ không nên ghi SQL arguments hoặc authentication header.

**Bài 8 — Production integration:** Dùng embedded server test GET nonexistent path, sai HTTP method, sai Content-Type và lỗi dependency; xác minh mọi response từ tầng web/proxy không có stack trace và có correlation ID nếu hợp đồng yêu cầu.

## 14. Tóm tắt kiến thức

- Lỗi trả cho client phải **dễ hiểu nhưng tối giản**; thông tin kỹ thuật sâu thuộc kênh quan sát nội bộ được bảo vệ.
- **Error code** cho biết nhóm lỗi; **requestId/traceId** chỉ đúng lần thực hiện để đối chiếu; các mã này không thay thế authentication/authorization.
- `@RestControllerAdvice` và `ResponseEntityExceptionHandler` giúp chuẩn hóa các lỗi MVC, nhưng không tự bao phủ Spring Security filters, proxies, async hoặc response đã commit.
- Spring Boot có cấu hình ngăn mặc định xuất `trace`, exception message và binding details. Custom handlers vẫn phải tự tuân thủ nguyên tắc an toàn.
- Không trả `exception.getMessage()`/stacktrace cho client; ngay cả nội bộ cũng cần chính sách redaction và kiểm soát log.
- Kiểm thử **không rò dữ liệu nhạy cảm** bằng các chuỗi giả, đồng thời so khớp request ID giữa header và body.

## 15. Tài liệu tham khảo

- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): các thuộc tính `server.error.include-*`, `server.error.whitelabel.enabled`.
- [Spring Framework 6.2 — Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html): Spring MVC error handling, `ErrorResponse`, `ProblemDetail`.
- [Spring Framework 6.2 — ResponseEntityExceptionHandler API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html): chữ ký override chính xác của các exception hooks.
- [Spring Framework 6.2 — OncePerRequestFilter API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html): request, error, async dispatch.
- [SLF4J — MDC](https://www.slf4j.org/manual.html): mapped diagnostic context, putCloseable và logging.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/): `traceparent` và distributed tracing.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457): định dạng phản hồi lỗi chuẩn.


</details>

- [Quay lại đầu trang](#back-to-top)
