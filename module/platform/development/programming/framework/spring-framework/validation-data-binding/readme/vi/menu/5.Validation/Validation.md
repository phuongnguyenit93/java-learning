<a id="back-to-top"></a>

# Validation và báo lỗi trong Spring

## Menu
- [Hợp đồng Validator và supports(...)](#validator-contract)
- [Errors là nơi thu thập lỗi Validation](#errors-contract)
- [Validation cho object lồng nhau](#nested-validation)
- [ValidationUtils là hạ tầng hỗ trợ](#validation-utils)
- [SmartValidator và Validation Hints](#smart-validator-hints)
- [Phân giải Message Code](#message-code-resolution)
- [Ranh giới MessageSource và i18n](#validation-i18n-boundary)

## <a id="validator-contract">Hợp đồng Validator và supports(...)</a>

<details>
<summary>Xem chi tiết</summary>

`Validator` của Spring tồn tại để quy tắc kiểm tra hợp lệ không bị gắn chặt với HTTP, UI framework hay công nghệ lưu trữ dữ liệu. Hợp đồng này có hai trách nhiệm: `supports(Class<?>)` cho biết validator có phù hợp với một kiểu hay không, còn `validate(Object, Errors)` kiểm tra object và ghi lỗi vào `Errors` được truyền vào.

`supports(...)` là kiểm tra tương thích kiểu, không phải quy tắc nghiệp vụ. Cách viết điển hình là `SomeType.class.isAssignableFrom(clazz)` để cả subtype cũng có thể được validate. Quy tắc thực sự nằm trong `validate(...)`; với dữ liệu đầu vào không hợp lệ thông thường, validator nên ghi error code ổn định thay vì ném exception.

```java
final class AccountValidator implements Validator {
    @Override
    public boolean supports(Class<?> type) {
        return Account.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Account account = (Account) target;
        if (account.username() == null || account.username().isBlank()) {
            errors.rejectValue("username", "username.required");
        }
        if (account.age() < 18) {
            errors.rejectValue("age", "age.minimum", new Object[] {18}, null);
        }
    }
}
```

Cách hình dung hữu ích là **object chứa quy tắc + bộ thu thập lỗi**. Validator quyết định điều gì không hợp lệ; `Errors` quyết định cách biểu diễn lỗi. Nhờ tách hai vai trò này, cùng một validator có thể chạy trong `DataBinder`, tầng dịch vụ hoặc unit test độc lập.

Spring Framework 6.1 còn bổ sung `Validator.validateObject(Object)` cho trường hợp cần validate trực tiếp một object mà không tham gia luồng binding. Method này tạo và trả về một `Errors` đơn giản để bên gọi tự kiểm tra hoặc chuyển thành exception bằng `failOnError(...)`:

```java
Errors errors = accountValidator.validateObject(account);

if (errors.hasErrors()) {
    // kiểm tra các lỗi validation có cấu trúc
}

accountValidator.validateObject(account)
        .failOnError(IllegalArgumentException::new);
```

Đường tắt này cố ý dùng mô hình kết quả nhỏ hơn một `BindingResult` có khả năng binding: cách triển khai `Errors` đơn giản của `validateObject(...)` không hỗ trợ nested path. Khi validation cần trạng thái nested path hoặc bằng chứng riêng của binding, hãy gọi `validate(Object, Errors)` thông thường với một `Errors` phù hợp như `BeanPropertyBindingResult`.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `Validator`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="errors-contract">Errors là nơi thu thập lỗi Validation</a>

<details>
<summary>Xem chi tiết</summary>

`Errors` là ngữ cảnh dùng để ghi kết quả validation. Nó giữ object name, nested-path hiện tại, khả năng đọc giá trị field và các thao tác như `reject(...)`, `rejectValue(...)` để đăng ký lỗi ở mức object hoặc field. Validator thường nhận một `Errors` đã tồn tại và bổ sung lỗi vào đó.

`BindingResult` mở rộng `Errors` và thêm thông tin phục vụ data binding như object đích, giá trị field gốc, suppressed fields và cơ chế phân giải message code. `DataBinder.getBindingResult()` trả về kết quả giàu thông tin này. Điểm quan trọng là binding có thể thất bại **trước** validation nghiệp vụ: ví dụ đổi `"abc"` sang `int` có thể tạo `typeMismatch`, sau đó validator tiếp tục ghi lỗi vào cùng kết quả.

```java
DataBinder binder = new DataBinder(new Account("", 0), "account");
binder.addValidators(new AccountValidator());
binder.validate();

BindingResult result = binder.getBindingResult();
if (result.hasErrors()) {
    result.getFieldErrors().forEach(error ->
        System.out.println(error.getField() + " -> " + error.getCode()));
}
```

Không nên coi `BindingResult` là nơi chứa message đã bản địa hóa. Nó giữ lỗi có cấu trúc và các message code ứng viên. Việc đổi các code đó thành văn bản theo locale thuộc bước xử lý sau của `MessageSource`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-validation">Validation cho object lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Validation thường đi theo object graph thay vì chỉ một object phẳng. Spring hỗ trợ việc này bằng nested path trên `Errors`. Validator cha có thể tạm chuyển ngữ cảnh lỗi sang một property con rồi chuyển việc kiểm tra cho validator chuyên trách kiểu con đó.

```java
final class OrderValidator implements Validator {
    private final AddressValidator addressValidator = new AddressValidator();

    @Override
    public boolean supports(Class<?> type) {
        return Order.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Order order = (Order) target;
        if (order.shippingAddress() == null) {
            errors.rejectValue("shippingAddress", "address.required");
            return;
        }

        errors.pushNestedPath("shippingAddress");
        try {
            addressValidator.validate(order.shippingAddress(), errors);
        }
        finally {
            errors.popNestedPath();
        }
    }
}
```

Khi nested path đang là `shippingAddress`, lời gọi `rejectValue("street", ...)` sẽ tạo lỗi cho `shippingAddress.street`. Mẫu `try/finally` quan trọng vì trạng thái nested path có thể thay đổi; nếu không khôi phục, lỗi tiếp theo có thể bị gắn sai field.

Nên tách validator riêng khi object con sở hữu quy tắc riêng của nó. Các invariant cần dữ liệu từ nhiều object nên nằm ở validator có đủ ngữ cảnh để đánh giá, ví dụ quy tắc so sánh billing country với shipping country.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-utils">ValidationUtils là hạ tầng hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

`ValidationUtils` là lớp tiện ích, không phải một mô hình validation mới. Nó cung cấp helper nhỏ cho hai nhóm việc phổ biến: gọi một Spring `Validator` khác và reject field khi rỗng hoặc chỉ chứa whitespace.

```java
@Override
public void validate(Object target, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(
        errors, "username", "username.required");

    ValidationUtils.rejectIfEmpty(
        errors, "countryCode", "country.required");
}
```

`ValidationUtils.invokeValidator(...)` thực hiện kiểm tra `supports(...)` cần thiết trước khi gọi validator và có overload truyền validation hints cho `SmartValidator`. Cách này hữu ích khi một validator kết hợp validator khác và muốn dùng kiểm tra tương thích chuẩn thay vì tự cast rồi gọi trực tiếp.

Chỉ dùng helper khi nó làm ý định của code rõ hơn. Quy tắc nghiệp vụ có nhiều field, nhiều nhánh hoặc nhiều thuật ngữ nghiệp vụ thường dễ đọc và dễ test hơn khi viết bằng Java thông thường trong validator.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `ValidationUtils`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="smart-validator-hints">SmartValidator và Validation Hints</a>

<details>
<summary>Xem chi tiết</summary>

`SmartValidator` mở rộng `Validator` bằng **validation hints** theo ngữ cảnh. Overload chính là `validate(Object, Errors, Object... validationHints)`. Hợp đồng Spring gốc không quy định hint phải có nghĩa gì; validator cụ thể quyết định có hiểu và sử dụng chúng hay không.

Trường hợp phổ biến nhất là Jakarta Bean Validation groups. `SpringValidatorAdapter` và `LocalValidatorFactoryBean` có thể hiểu hint kiểu `Class<?>` như Bean Validation group, giúp bên gọi chọn tập quy tắc cho một tình huống mà không thay đổi kiểu của object đích.

```java
interface RegistrationChecks {}

DataBinder binder = new DataBinder(command, "command");
binder.addValidators(localValidatorFactoryBean);
binder.validate(RegistrationChecks.class);
```

Hint cố ý được thiết kế khá mở để validator Spring có thể hỗ trợ các mô hình ngữ cảnh khác. Validator cũng được phép bỏ qua hint và hành xử như `validate(...)` thông thường. Vì vậy code chỉ nên phụ thuộc vào ý nghĩa của hint khi validator được chọn có tài liệu mô tả rõ ý nghĩa đó.

`SmartValidator` còn hỗ trợ validate một giá trị field ứng viên mà không cần thay đổi một object hoàn chỉnh. Đây vẫn là validation, không phải data binding.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="message-code-resolution">Phân giải Message Code</a>

<details>
<summary>Xem chi tiết</summary>

Error code trong Spring là định danh, không phải message cuối cùng. Khi gọi `errors.rejectValue("age", "tooYoung")`, `BindingResult` dùng `MessageCodesResolver` để mở rộng code ổn định đó thành nhiều ứng viên từ cụ thể đến tổng quát.

Với `DefaultMessageCodesResolver`, một field error có thể tạo chuỗi ứng viên như:

```text
tooYoung.account.age
tooYoung.age
tooYoung.int
tooYoung
```

Thứ tự này cho phép ứng dụng định nghĩa message rất cụ thể khi cần và dùng message tổng quát hơn làm phương án dự phòng. Object error cũng theo cùng ý tưởng với code gắn object rồi code chung. `BindingResult.resolveMessageCodes(...)` cho phép dùng cùng cơ chế này bằng code.

Lớp này quan trọng vì validator không cần biết locale, đồng thời lỗi binding như `typeMismatch` có thể đi qua cùng chiến lược tra cứu message với lỗi validation nghiệp vụ.

Nên dùng code mang nghĩa ổn định như `customer.email.invalid` thay vì cả một câu tiếng Anh. Câu chữ và locale có thể đổi mà code không cần đổi.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `MessageCodesResolver` và `DefaultMessageCodesResolver`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-i18n-boundary">Ranh giới MessageSource và i18n</a>

<details>
<summary>Xem chi tiết</summary>

Module này dừng ở **lỗi có cấu trúc cùng các message code có thể phân giải**. `MessageSource` mới là thành phần ánh xạ code và arguments thành văn bản theo `Locale`; vòng đời và cấu hình của `MessageSource` thuộc module Spring Core Container.

Có thể hình dung luồng chuyển giao như sau:

```text
Validator / DataBinder
        ↓
ObjectError / FieldError
        ↓
message-code candidates + arguments
        ↓
MessageSource (Core Container)
        ↓
localized text
```

Có một điểm nối tích hợp: `LocalValidatorFactoryBean` cho phép `setValidationMessageSource(...)` để Bean Validation message interpolation dùng Spring `MessageSource`. Điều đó không chuyển trách nhiệm sở hữu `MessageSource` sang validation; nó chỉ nối validation provider với hạ tầng bản địa hóa của ứng dụng.

Tương tự, cách trình bày lỗi đã bản địa hóa thành HTTP response, form hay reactive handler không thuộc chapter này. MVC và WebFlux sở hữu vòng đời trình bày phụ thuộc tầng truyền tải tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)
