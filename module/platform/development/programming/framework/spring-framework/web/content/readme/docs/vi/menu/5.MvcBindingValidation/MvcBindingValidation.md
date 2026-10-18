<a id="back-to-top"></a>

# Tích hợp Binding và Validation trong MVC

## Menu
- [Vòng đời data binding, conversion và formatting trong MVC](#mvc-binding-lifecycle)
- [WebDataBinder và tùy biến với @InitBinder](#web-data-binder-and-init-binder)
- [Object validation và BindingResult](#validation-and-binding-result)
- [Built-in MVC Method Validation trong Spring Framework 6.1](#mvc-method-validation-6-1)
- [MethodArgumentNotValidException và HandlerMethodValidationException](#validation-exception-paths)
- [Class-level @Validated và ranh giới AOP method validation](#validated-aop-boundary)
- [Safe data binding và allowed fields](#safe-binding)

## <a id="mvc-binding-lifecycle">Vòng đời data binding, conversion và formatting trong MVC</a>

<details>
<summary>Xem chi tiết</summary>

MVC data binding chuyển input dạng text/untyped từ request thành Java value mà handler có thể dùng. Pipeline kết hợp property binding, type conversion/formatting và validation tùy chọn.

Với bindable object, MVC tạo `WebDataBinder`, áp dụng global binding initialization, chạy `@InitBinder` phù hợp, bind request value, convert type rồi validate khi được yêu cầu.

```text
request value
→ WebDataBinder
→ conversion / formatting
→ target object
→ validation
→ handler method
```

Binding error và validation error liên quan nhưng không giống nhau: conversion có thể thất bại trước khi constraint được đánh giá. Không nên giả định mọi invalid input đều đi cùng một exception/error shape.

Binder/conversion/validation abstraction dùng chung thuộc module validation/data-binding; chapter này tập trung vào cách MVC đặt chúng trong controller lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-data-binder-and-init-binder">WebDataBinder và tùy biến với @InitBinder</a>

<details>
<summary>Xem chi tiết</summary>

`WebDataBinder` là binder web-aware của MVC cho controller input. Nó kiểm soát property access, field marker/default, allowed/disallowed field, conversion, validation và các behavior binding liên quan.

Method `@InitBinder` tùy biến binder trong phạm vi controller. Nó phù hợp khi một controller cần formatter, validator hoặc allowed-field policy riêng mà không muốn đổi binding rule toàn ứng dụng.

Nên giữ binder customization hẹp. Date format cục bộ hoặc allowlist là hợp lý; business rule cần repository/remote service thường thuộc application/domain validation hơn là binder setup.

`@ControllerAdvice` cũng có thể đóng góp binder initialization dùng chung, nhưng global binder rule rất mạnh. Cần giữ nó dễ dự đoán vì handler method có thể bị ảnh hưởng dù không trực tiếp nhắc tới advice.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-and-binding-result">Object validation và BindingResult</a>

<details>
<summary>Xem chi tiết</summary>

Object validation thường chạy khi bindable argument có `@Valid` hoặc `@Validated`. Sau binding, MVC gọi validation infrastructure đã cấu hình và ghi nhận failure.

Với các argument được validation như `@ModelAttribute`, `@RequestBody` hoặc `@RequestPart`, parameter `Errors` hoặc `BindingResult` được khai báo **ngay sau** argument đó cho phép controller xử lý cục bộ lỗi binding/validation thay vì luôn ném exception ra ngoài.

```text
binding error
→ request value không bind/convert được

validation error
→ Java value đã tồn tại nhưng vi phạm rule
```

Nếu có ích cho người dùng, response nên giữ khác biệt này. Date sai format khác với date đúng format nhưng nằm ngoài khoảng cho phép.

Không nên biến `BindingResult` handling thành cách thay thế thủ công cho toàn bộ error policy; REST API thường phù hợp hơn với centralized error response.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-method-validation-6-1">Built-in MVC Method Validation trong Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung built-in MVC method validation cho parameter và return value của controller method. Nó khác với việc validate riêng một object trong argument resolver.

Method validation trở nên liên quan khi constraint annotation được đặt trực tiếp trên method parameter hoặc trên method cho return value. MVC có thể validate toàn method invocation và báo violation qua `HandlerMethodValidationException`. Khi method validation được kích hoạt, nó thay thế đường individual argument validation đối với các parameter tham gia và đồng thời đánh giá nested constraint đi qua `@Valid`.

Với **method parameter**, chỉ có `@Valid` thì chưa kích hoạt method validation; nó chủ yếu yêu cầu nested/object validation tại argument resolver sở hữu parameter đó. Direct constraint như `@NotNull` hoặc `@Min` trên parameter mới làm method trở thành candidate cho argument method validation. Return-value validation có rule liên quan nhưng khác một chút: constraint annotation **hoặc `@Valid` trên method return value** đều có thể làm return value trở thành candidate cho MVC method validation.

Method validation vẫn có thể phối hợp với `Errors`/`BindingResult` cục bộ. Controller method chỉ được gọi khi **toàn bộ** validation error đều thuộc các method parameter mà ngay sau mỗi parameter đó có một `Errors` hoặc `BindingResult`; nếu không, MVC ném `HandlerMethodValidationException`.

Đường built-in này nằm trong handler invocation của MVC và không cần AOP proxy cho controller method validation thông thường ở Spring 6.1.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-exception-paths">MethodArgumentNotValidException và HandlerMethodValidationException</a>

<details>
<summary>Xem chi tiết</summary>

Hai exception validation dễ bị nhầm nhưng xuất hiện ở lifecycle khác nhau.

`MethodArgumentNotValidException` thường được tạo khi một argument resolver validate bindable/request-body argument và riêng argument đó không hợp lệ.

`HandlerMethodValidationException` biểu diễn các failure của method-level validation **không được xử lý cục bộ** trên controller parameter hoặc return value trong Spring MVC 6.1. Nó có thể giữ validation result theo loại method parameter. Nếu mọi lỗi method validation đều thuộc các parameter có `Errors` hoặc `BindingResult` ngay sau chúng, MVC có thể đưa lỗi vào các object đó và vẫn gọi controller method.

```text
individual argument validation không có Errors/BindingResult cục bộ
→ MethodArgumentNotValidException

method-level validation có violation không được xử lý hết cục bộ
→ HandlerMethodValidationException
```

Error handler nên tính đến cả hai nếu API dùng cả hai style. Không nên flatten quá sớm nếu ứng dụng cần phân biệt field/object error với parameter-level constraint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validated-aop-boundary">Class-level @Validated và ranh giới AOP method validation</a>

<details>
<summary>Xem chi tiết</summary>

Cơ chế method validation tổng quát của Spring dùng `@Validated` cùng AOP method validation. Nó vẫn phù hợp với service bean và component được proxy khác.

Với controller trong Spring Framework 6.1, class-level `@Validated` làm đổi đường xử lý: method validation được giao cho AOP proxy thay vì built-in MVC method validation. Nếu mục tiêu là dùng behavior tích hợp mới của MVC thì controller không nên dựa vào class-level `@Validated`.

Khác biệt này ảnh hưởng exception type, lifecycle position và component nào sở hữu validation.

```text
built-in MVC controller method validation
→ handler-method lifecycle

generic @Validated method validation
→ AOP proxy lifecycle
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-binding">Safe data binding và allowed fields</a>

<details>
<summary>Xem chi tiết</summary>

Data binding có thể tạo mass-assignment vulnerability nếu request parameter bên ngoài được phép ghi vào property mà client không bao giờ nên kiểm soát.

Thiết kế an toàn là dùng input model riêng chỉ chứa field thực sự writable cho endpoint. Khi dùng property binding, `WebDataBinder` có thể giới hạn field được ghi bằng allowlist và các binding control khác.

Không bind request trực tiếp vào persistence entity hoặc rich domain object chỉ vì tên property trùng nhau. Các field như `role`, `status`, `ownerId` hay pricing flag có thể vô tình trở thành attacker-controlled.

```text
HTTP input model
→ dữ liệu được phép rõ ràng
→ application validation/mapping
→ domain model
```

Binding safety bổ sung cho authorization chứ không thay thế authorization. Allowlist không trả lời câu hỏi user hiện tại có quyền thực hiện operation hay không.

</details>

- [Quay lại đầu trang](#back-to-top)
