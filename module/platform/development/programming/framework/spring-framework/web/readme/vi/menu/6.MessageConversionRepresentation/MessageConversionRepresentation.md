<a id="back-to-top"></a>

# HTTP Message Conversion và Representation

## Menu
- [Mô hình HttpMessageConverter](#http-message-converters)
- [Đọc request body và ghi response body](#request-response-body-conversion)
- [Content Negotiation và media type](#content-negotiation-and-media-types)
- [ResponseEntity, status và header](#response-entity-status-and-headers)
- [RequestBodyAdvice và ResponseBodyAdvice](#request-response-body-advice)

## <a id="http-message-converters">Mô hình HttpMessageConverter</a>

<details>
<summary>Xem chi tiết</summary>

`HttpMessageConverter` là strategy của Spring để chuyển đổi giữa Java value và HTTP message body. Converter khai báo nó có thể đọc/ghi Java type nào với media type nào, sau đó thực hiện serialization hoặc deserialization.

Spring MVC dùng converter cho body argument/return value của annotated controller và trong functional endpoint infrastructure. Cùng abstraction này cũng được synchronous HTTP client của Spring tái sử dụng.

```text
HTTP body + media type
→ HttpMessageConverter phù hợp
→ Java value

Java value + media type đã chọn
→ HttpMessageConverter phù hợp
→ HTTP body
```

Converter là representation infrastructure, không phải business mapper. JSON converter biến JSON thành input DTO; quyết định DTO đó có được thay đổi order hay không thuộc application logic.

Việc chọn converter phụ thuộc cả Java type và media type. Converter custom claim quá rộng có thể che converter chuyên biệt và tạo representation bất ngờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-response-body-conversion">Đọc request body và ghi response body</a>

<details>
<summary>Xem chi tiết</summary>

`@RequestBody` yêu cầu MVC đọc request body qua message conversion và đưa giá trị đã decode vào handler argument. `@ResponseBody` yêu cầu MVC ghi handler result qua message converter thay vì coi nó là view name. `@RestController` áp dụng response-body semantics cho handler method.

```text
request Content-Type
→ converter nào có thể đọc body

response type + media type đã negotiate
→ converter nào có thể ghi body
```

Validation có thể chạy sau khi request body được decode nếu argument yêu cầu. Conversion failure khác validation failure: JSON sai cú pháp có thể fail trước khi Java object tồn tại.

Không nên trộn direct Servlet response write với normal response-body conversion nếu endpoint không chủ động chiếm toàn quyền response. Khi response đã commit, stage sau của MVC không thể an toàn thay status/header.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="content-negotiation-and-media-types">Content Negotiation và media type</a>

<details>
<summary>Xem chi tiết</summary>

Media type mô tả format của representation. Với request vào, `Content-Type` cho biết body đang gửi ở format nào. Với response ra, header `Accept` cùng producible media type của handler giúp MVC chọn representation.

Request mapping có thể giới hạn media type được consume/produce, nên representation support tham gia cả routing lẫn converter selection.

```text
body media type không đọc được
→ 415 Unsupported Media Type

không có representation phù hợp với Accept
→ 406 Not Acceptable
```

Content negotiation cần dễ dự đoán. Không nên giả định mọi object tự động thành JSON; cần converter phù hợp và media-type selection đúng.

Nếu hỗ trợ nhiều representation, mỗi representation là public contract. Thêm converter có thể làm thay đổi những format negotiate được, nên converter và negotiation configuration cần được xem xét cùng nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="response-entity-status-and-headers">ResponseEntity, status và header</a>

<details>
<summary>Xem chi tiết</summary>

`ResponseEntity<T>` biểu diễn HTTP response có status, header và body tùy chọn. Nó hữu ích khi endpoint result bao gồm HTTP metadata mà body đơn thuần không diễn đạt đủ.

```java
return ResponseEntity
    .created(location)
    .body(createdOrder);
```

MVC vẫn áp dụng message conversion cho body. `ResponseEntity` không bypass converter; nó cung cấp status/header còn body vẫn theo representation processing bình thường.

Dùng nó khi HTTP control là một phần rõ của contract: `201 Created` kèm `Location`, conditional header, `204` có chủ đích hoặc caching metadata riêng.

Không cần trả `ResponseEntity` từ mọi controller chỉ để trông "RESTful". Nếu endpoint chỉ trả body thành công thông thường, direct value thường dễ đọc hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-response-body-advice">RequestBodyAdvice và ResponseBodyAdvice</a>

<details>
<summary>Xem chi tiết</summary>

`RequestBodyAdvice` và `ResponseBodyAdvice` là extension point của MVC quanh message conversion. Chúng cho phép infrastructure inspect/transform body-processing behavior mà không lặp code ở mọi controller.

`RequestBodyAdvice` tham gia quanh việc đọc request body; `ResponseBodyAdvice` có thể điều chỉnh value/header trước khi `HttpMessageConverter` đã chọn ghi response. Implementation có thể được discovery qua controller-advice infrastructure.

Use case tốt là representation concern thật sự dùng chung. Use case xấu là giấu business transformation, âm thầm thay DTO không liên quan hoặc gọi remote service nặng trong mọi body conversion.

Advice cần `supports` hẹp và rõ. Advice claim mọi controller/type tạo global behavior ẩn và làm content negotiation/error response khó debug.

Đây là MVC body-processing advice, không phải Spring AOP advice và cũng không phải Servlet filter.

</details>

- [Quay lại đầu trang](#back-to-top)
