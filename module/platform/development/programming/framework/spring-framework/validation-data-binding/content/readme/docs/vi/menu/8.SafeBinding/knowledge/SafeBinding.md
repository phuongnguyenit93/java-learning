<a id="back-to-top"></a>

# Binding an toàn và quyết định End-to-End

## Menu
- [Binding Security là bài toán kiểm soát bề mặt Input](#binding-security-model)
- [Input Model có phạm vi rõ ràng](#safe-input-models)
- [Constructor, Declarative và Allowed-Field Controls](#safe-binding-controls)
- [Binding Security khác Validation Rule](#binding-validation-separation)
- [Chọn Conversion, Formatting, Validator, Bean Validation và DataBinder](#mechanism-selection)
- [Luồng Binding và Validation End-to-End](#validation-binding-end-to-end)
- [Chuyển giao sang Core Container, MVC và WebFlux](#validation-binding-handoffs)

## <a id="binding-security-model">Binding Security là bài toán kiểm soát bề mặt Input</a>

<details>
<summary>Xem chi tiết</summary>

Binding security trước hết là câu hỏi **dữ liệu đầu vào nào được phép ảnh hưởng tới trạng thái object**. Validation trả lời câu hỏi sau đó: trạng thái đã tạo có hợp lệ hay không. Nếu bên gửi dữ liệu có thể bind một field vốn không nên được ghi từ bên ngoài, validator vẫn có thể thấy giá trị đó hoàn toàn hợp lệ trong domain trong khi lỗi an toàn đã xảy ra.

Hãy nghĩ tới domain type có các property như `role`, `creditLimit` hay `approved`. Đây có thể là trạng thái domain hợp lệ nhưng không phù hợp để mass assignment từ dữ liệu bên ngoài. Vì vậy câu hỏi an toàn không phải "giá trị này có qua validation không?" mà là "kênh đầu vào này có được quyền ghi member này không?"

```text
tên/giá trị từ bên ngoài
        ↓
quyết định bề mặt binding  ← ranh giới an toàn
        ↓
trạng thái object có kiểu
        ↓
quy tắc validation         ← ranh giới tính đúng đắn
```

Phân biệt này áp dụng cả ngoài HTTP. Bất kỳ cơ chế tổng quát nào ánh xạ name/value kém tin cậy hơn vào object ứng dụng có thể ghi đều cần bề mặt đầu vào tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-input-models">Input Model có phạm vi rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

Input model chuyên biệt thu hẹp bề mặt binding ngay trước khi xét tới cấu hình binder. Thay vì bind trực tiếp vào domain entity có nhiều trạng thái, hãy định nghĩa command/DTO mà constructor hoặc writable property chỉ biểu diễn những giá trị use case thực sự chấp nhận.

```java
public final class ProfileUpdateForm {
    private String displayName;
    private ZoneId timezone;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }
}
```

Việc type này không có `role`, `accountStatus` hay internal identifier là một bảo đảm mạnh: property binding thông thường không thể vô tình làm lộ member vốn không tồn tại trên input model.

Mô hình đầu vào có giới hạn còn giúp validation dễ suy luận hơn. Quy tắc về hình dạng đầu vào có thể áp dụng lên form, trong khi domain object tiếp tục chịu trách nhiệm cho invariant của chính nó. Bước ánh xạ form đã validate sang domain model trở thành bước ứng dụng tường minh thay vì tác dụng phụ của việc gán property tổng quát.

Đánh đổi là thêm type và code ánh xạ. Chi phí này thường đáng giá tại ranh giới tin cậy vì hợp đồng đầu vào được chấp nhận hiện rõ trong Java thay vì chỉ tồn tại dưới cấu hình runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-binding-controls">Constructor, Declarative và Allowed-Field Controls</a>

<details>
<summary>Xem chi tiết</summary>

Spring 6.1 cung cấp nhiều cơ chế kiểm soát bổ trợ cho input model có giới hạn:

1. **constructor binding** chỉ resolve giá trị mà constructor path được chọn cần;
2. **declarative binding** (`setDeclarativeBinding(true)`) luôn cho constructor binding nhưng chỉ cho property binding khi đã cấu hình `allowedFields`;
3. **allowed fields** tạo allow-list cho property binding.

```java
DataBinder binder = new DataBinder(target, "profile");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
```

Các cơ chế này giải quyết vấn đề liên quan nhưng không giống nhau. Constructor binding biểu diễn input qua hình dạng constructor. Declarative mode thay đổi trạng thái mặc định của property binding. `allowedFields` xác định bề mặt property còn được phép ghi.

`disallowedFields` có thể chặn các property nhạy cảm đã biết, nhưng allow-list thường dễ duy trì hơn với dữ liệu đầu vào không đáng tin cậy: thêm writable property mới sau này không tự động làm nó bindable. Nên coi các mẫu này là một phần của hợp đồng đầu vào và test các trường hợp quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-validation-separation">Binding Security khác Validation Rule</a>

<details>
<summary>Xem chi tiết</summary>

Chính sách binding và chính sách validation hoạt động ở các điểm khác nhau trong luồng và nên được giữ tách biệt.

```text
"role=ADMIN" đi vào
        ↓
`role` có bindable không?  → chính sách binding
        ↓ có
giá trị gốc convert được?  → conversion/binding
        ↓ có
ADMIN có hợp lệ?           → chính sách validation/domain
```

Nếu use case tuyệt đối không cho phép đổi role, đáp án đúng là loại `role` khỏi bề mặt binding, không phải viết validator cố nhận diện giá trị "không được phép" sau khi property đã bị expose.

Tương tự, `requiredFields` là quy tắc binding: nó yêu cầu property được chỉ định phải được cung cấp với value vượt qua phép kiểm tra sự hiện diện/giá trị rỗng của `DataBinder`. Value vắng mặt hoặc value bị phép kiểm tra đó xem là rỗng sẽ tạo lỗi `required`; điều này vẫn khác quy tắc validator như "end date bắt buộc khi status là CLOSED", vì validator đánh giá ý nghĩa của object sau binding.

Tách các lớp trách nhiệm còn giúp báo lỗi rõ hơn. Lỗi binding có thể dùng code `required` hoặc `typeMismatch`, còn validation nghiệp vụ dùng code mang nghĩa như `period.end.requiredWhenClosed`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mechanism-selection">Chọn Conversion, Formatting, Validator, Bean Validation và DataBinder</a>

<details>
<summary>Xem chi tiết</summary>

Hãy chọn cơ chế theo bài toán mà nó sở hữu, không phải theo API nào đang nằm gần code nhất:

| Bài toán | Cơ chế chính |
| --- | --- |
| Biến đổi kiểu có thể tái sử dụng | `ConversionService` / `Converter` |
| Parse/print phụ thuộc locale hoặc presentation | `Formatter` / `FormatterRegistry` |
| Quy tắc object/domain viết bằng code | Spring `Validator` |
| Constraint/group dựa trên annotation/provider | Jakarta Bean Validation qua Spring integration |
| Áp giá trị đầu vào, chính sách field, thu lỗi binding | `DataBinder` |

Sai lầm phổ biến là đặt conversion vào validator. Nếu `"37"` cần thành `int`, đó là conversion; validator nên nhận trạng thái đã có kiểu rồi quyết định `37` có hợp lệ hay không. Sai lầm ngược lại là bắt converter từ chối một giá trị đúng kiểu nhưng sai nghĩa như age `-1`; đó là validation.

`DataBinder` điều phối các cơ chế này nhưng không thay thế chúng. Nó có thể chứa `ConversionService` và validators vì luồng binding cần cả hai; từng abstraction vẫn nên giữ đúng trách nhiệm riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-binding-end-to-end">Luồng Binding và Validation End-to-End</a>

<details>
<summary>Xem chi tiết</summary>

Một luồng end-to-end an toàn sẽ dễ suy luận hơn khi mỗi giai đoạn chỉ có một trách nhiệm:

```text
giá trị gốc từ bên ngoài
        ↓
chọn bề mặt đầu vào được chấp nhận
        ↓
chọn luồng constructor và/hoặc property binding
        ↓
phân giải rồi conversion / formatting từng giá trị ngay trong quá trình binding
        ↓
tạo / gán trạng thái object có kiểu
        ↓
BindingResult ghi lỗi binding
        ↓
Spring Validator / Bean Validation
        ↓
ObjectError / FieldError + message codes
```

```java
ProfileUpdateForm command = new ProfileUpdateForm();
DataBinder binder = new DataBinder(command, "profile");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
binder.setConversionService(conversionService);
binder.addValidators(profileValidator);

binder.bind(propertyValues);
if (!binder.getBindingResult().hasErrors()) {
    binder.validate();
}

BindingResult result = binder.getBindingResult();
```

Có chạy validation khi binding đã có error hay không là quyết định của ứng dụng/hạ tầng; điểm quan trọng là hai loại lỗi vẫn phân biệt được trong cùng kết quả có cấu trúc. Không nên bỏ bằng chứng binding rồi validate một object chỉ được điền một phần như thể quá trình xử lý đầu vào đã thành công.

Luồng dừng tại đây với lỗi có cấu trúc và các code có thể phân giải. Bản địa hóa và trình bày theo tầng truyền tải là bước chuyển giao rõ ràng cho lớp sau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="validation-binding-handoffs">Chuyển giao sang Core Container, MVC và WebFlux</a>

<details>
<summary>Xem chi tiết</summary>

Module này sở hữu các hợp đồng có thể tái sử dụng cho validation, binding, conversion, formatting và tích hợp Bean Validation. Ba module lân cận tiếp quản sau khi các hợp đồng đó hoàn thành công việc:

- **Spring Core Container** sở hữu `MessageSource` và hạ tầng i18n rộng hơn để phân giải error code thành văn bản đã bản địa hóa.
- **Spring MVC** sở hữu `WebDataBinder`, `@InitBinder`, controller argument binding, vòng đời HTTP request-to-object và hành vi validation/error riêng của MVC.
- **Spring WebFlux** sở hữu vòng đời reactive controller binding/validation tương ứng.

Method validation có thêm một ranh giới: module này giải thích `MethodValidator`, `MethodValidationAdapter`, `MethodValidationPostProcessor` như hạ tầng validation, còn **Spring AOP/Aspect** sở hữu cơ chế proxy/interceptor.

Đây không phải ranh giới tùy ý. Nó giúp mô hình lõi có thể tái sử dụng hoạt động trong service, UI, batch, test hoặc hạ tầng tùy biến mà không phụ thuộc web stack, đồng thời cho phép từng module tầng truyền tải dạy sâu vòng đời riêng.

Khi lần theo lỗi sang các bước sau, nên đi theo chuỗi trách nhiệm sở hữu thay vì lặp lại hành vi tại đây: tạo lỗi binding/validation có cấu trúc trong module này, bản địa hóa qua Core Container rồi để module tầng truyền tải quyết định cách trình bày lỗi cho client.

</details>

- [Quay lại đầu trang](#back-to-top)
