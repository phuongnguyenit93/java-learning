<a id="back-to-top"></a>

# Exception Resolution và Problem Response trong MVC

## Menu
- [Chuỗi HandlerExceptionResolver](#handler-exception-resolver-chain)
- [@ExceptionHandler và @ControllerAdvice](#exception-handler-and-controller-advice)
- [ResponseStatusException và ánh xạ exception sang status](#response-status-errors)
- [ErrorResponse và ProblemDetail](#error-response-and-problem-detail)
- [ResponseEntityExceptionHandler](#response-entity-exception-handler)
- [Validation error trong MVC error pipeline](#validation-error-responses)
- [Ranh giới Spring Boot và module global-handler của repository](#mvc-error-boundaries)

## <a id="handler-exception-resolver-chain">Chuỗi HandlerExceptionResolver</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC xem exception handling như một stage resolution có thứ tự trong request pipeline. Khi handler processing ném exception, `DispatcherServlet` hỏi các `HandlerExceptionResolver` đã cấu hình xem component nào có thể chuyển failure thành MVC response outcome.

Standard resolver set bao gồm support cho `@ExceptionHandler`, status-based exception mapping và Framework default cho các MVC exception đã biết.

```text
exception
→ resolver 1
→ resolver 2
→ ...
→ response đã resolve HOẶC propagate
```

Ordering có ý nghĩa. Custom resolver quá rộng đặt trước có thể nuốt exception mà Framework resolver chuyên biệt sẽ xử lý tốt hơn.

Resolver cũng chịu giới hạn của HTTP response. Nếu response đã commit, error handling có thể không còn thay status/header/body một cách sạch sẽ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handler-and-controller-advice">@ExceptionHandler và @ControllerAdvice</a>

<details>
<summary>Xem chi tiết</summary>

`@ExceptionHandler` cho controller định nghĩa method xử lý exception phát sinh từ request handler. `@ControllerAdvice` có thể đóng góp handler dùng chung giữa nhiều controller, phù hợp cho API error policy thống nhất.

Local handler và advice phù hợp được chọn theo rule của MVC, bao gồm độ cụ thể của exception type và ordering của advice.

Handler nên tập trung chuyển failure thành HTTP-facing outcome. Không nên che programming bug hoặc biến mọi exception bất ngờ thành `200 OK`.

`@ControllerAdvice` là global infrastructure mạnh. Nếu policy không áp dụng toàn ứng dụng, nên giới hạn advice theo package, annotation hoặc controller type.

Exception handler vẫn có thể trả `ResponseEntity`, `ProblemDetail`, view và các outcome MVC khác nên vẫn đi qua return-value processing bình thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="response-status-errors">ResponseStatusException và ánh xạ exception sang status</a>

<details>
<summary>Xem chi tiết</summary>

`ResponseStatusException` mang HTTP status cùng reason tùy chọn. Nó hữu ích khi web layer cần phát sinh failure có status rõ mà không cần tạo exception class riêng cho mọi status.

Spring MVC cũng hỗ trợ `@ResponseStatus` trên exception type. Resolver infrastructure chuyển các declaration đó thành HTTP response.

Nên dùng status-bearing exception có chủ đích. HTTP status là transport contract; ném `ResponseStatusException` sâu trong domain model sẽ làm domain phụ thuộc HTTP.

Domain/application exception có thể giữ transport-neutral rồi được map ở MVC advice.

Status mapping cũng không thay thế structured error body. Client thường cần một error shape ổn định ngoài numeric status.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="error-response-and-problem-detail">ErrorResponse và ProblemDetail</a>

<details>
<summary>Xem chi tiết</summary>

`ProblemDetail` là representation của Spring cho standardized HTTP problem-details body, với các field như status, title, detail, type và instance; application có thể bổ sung property.

`ErrorResponse` là contract cho exception cung cấp HTTP status, header và `ProblemDetail` body. Nhiều Spring web exception tham gia model này, giúp application có điểm chung để customize error response.

```text
nhiều MVC exception khác nhau
→ common error-response contract
→ problem response nhất quán
```

Không đưa stack trace, database detail, credential hoặc identifier nhạy cảm vào problem property. Error contract công khai nên giúp client xử lý nhưng không làm lộ implementation.

Problem detail chuẩn hóa representation chứ không tự định nghĩa business error taxonomy. Khi client cần phân biệt business case, application vẫn cần error code ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="response-entity-exception-handler">ResponseEntityExceptionHandler</a>

<details>
<summary>Xem chi tiết</summary>

`ResponseEntityExceptionHandler` là base class thuận tiện cho `@ControllerAdvice` muốn xử lý standard Spring MVC exception theo style `ResponseEntity`/problem detail thống nhất.

Nó centralize dispatch tới các protected `handle...` method cho failure phổ biến rồi tạo response qua shared hook. Application có thể override handler hẹp cho một exception hoặc common body-building path.

Cách này tốt hơn việc copy toàn bộ danh sách exception handler của Framework vào advice riêng.

Override đồng nghĩa application nhận trách nhiệm giữ đúng status, header và semantics. Custom error envelope không nên vô tình xóa header quan trọng hoặc biến client error thành server error.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-error-responses">Validation error trong MVC error pipeline</a>

<details>
<summary>Xem chi tiết</summary>

Validation có thể đi vào MVC error pipeline bằng nhiều exception type.

`MethodArgumentNotValidException` thường biểu diễn validation failure của một argument do argument resolver xử lý, ví dụ validated request body hoặc bindable object.

Spring Framework 6.1 còn có `HandlerMethodValidationException` cho method-level constraint trên controller parameter hoặc return value.

Nếu API dùng cả hai style, global error handler nên hiểu cả hai shape. Field/object error có thông tin khác parameter-level method-validation result.

Không chỉ trả chuỗi "validation failed" cho mọi trường hợp. Nên giữ đủ structured information để client biết input nào sai nhưng không làm lộ cấu trúc nội bộ không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-error-boundaries">Ranh giới Spring Boot và module global-handler của repository</a>

<details>
<summary>Xem chi tiết</summary>

Spring MVC sở hữu Framework exception-resolution mechanics. Spring Boot thêm default ở application level quanh error dispatch/error endpoint; còn module `project-build/springboot-runtime/exception-handler-servlet` của repository là support/runtime artifact, không phải curriculum owner.

Module này dạy:

- `HandlerExceptionResolver`;
- `@ExceptionHandler`/`@ControllerAdvice`;
- status mapping;
- `ErrorResponse`, `ProblemDetail` và Framework exception handling.

Nó không biến Boot error-controller model, Feign handler, Mongo failure hay utility convention riêng của repository thành core Spring MVC.

Khi application dùng nhiều layer, cần xác định layer nào thực sự tạo final response trước khi sửa MVC configuration.

</details>

- [Quay lại đầu trang](#back-to-top)
