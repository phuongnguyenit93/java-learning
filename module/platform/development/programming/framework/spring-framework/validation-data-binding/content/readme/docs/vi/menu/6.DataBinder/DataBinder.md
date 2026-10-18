<a id="back-to-top"></a>

# DataBinder và luồng điều phối Binding

## Menu
- [DataBinder điều phối những gì?](#data-binder-role)
- [Luồng Binding từ dữ liệu đầu vào tới BindingResult](#data-binding-flow)
- [Property và Setter Binding](#property-binding-model)
- [Constructor Binding](#constructor-binding-model)
- [Declarative Binding trong Spring Framework 6.1](#declarative-binding-61)
- [Required, Unknown, Invalid, Allowed và Disallowed Fields](#binding-field-policies)
- [Conversion, Custom Editor, Validator và Error Processing](#data-binder-extension-points)

## <a id="data-binder-role">DataBinder điều phối những gì?</a>

<details>
<summary>Xem chi tiết</summary>

`DataBinder` là bộ điều phối dùng chung để biến mô hình giá trị đầu vào thành trạng thái object có kiểu và đồng thời thu thập lỗi trong `BindingResult`. Nó không định nghĩa ý nghĩa của HTTP request; MVC `WebDataBinder` và WebFlux xây vòng đời phụ thuộc tầng truyền tải trên hợp đồng mức thấp hơn này.

Binder kết nối các mảnh đã học ở những chương trước:

```text
input values
   ↓
chính sách field
   ↓
conversion / formatting / property access
   ↓
target object
   ↓
BindingResult
   ↓
Validator tùy chọn
```

```java
AccountForm form = new AccountForm();
DataBinder binder = new DataBinder(form, "account");
binder.setConversionService(conversionService);
binder.addValidators(accountValidator);
```

Binder sở hữu việc điều phối chứ không sở hữu mọi quy tắc. Converter quyết định cách đổi kiểu, lớp property access quyết định cách ghi vào object đích, validator quyết định trạng thái kết quả có hợp lệ hay không, còn `BindingResult` giữ cả lỗi binding lẫn lỗi validation.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `DataBinder`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-binding-flow">Luồng Binding từ dữ liệu đầu vào tới BindingResult</a>

<details>
<summary>Xem chi tiết</summary>

Với property binding, `bind(PropertyValues)` là điểm vào chính. Trước khi giá trị được ghi vào object đích, `DataBinder` kiểm tra required fields và lọc theo allowed/disallowed fields. Sau đó nó giao conversion và property assignment cho hạ tầng binding. Lỗi conversion hoặc property access trở thành field error thay vì một lần gán thành công thông thường.

```java
MutablePropertyValues values = new MutablePropertyValues();
values.add("name", "Ada");
values.add("age", "not-a-number");

DataBinder binder = new DataBinder(new AccountForm(), "account");
binder.bind(values);
binder.validate();

BindingResult result = binder.getBindingResult();
```

Binding và validation là hai giai đoạn riêng. `bind(...)` có thể ghi lỗi như `required` hoặc `typeMismatch`; `validate(...)` sau đó gọi các validator đã cấu hình và bổ sung lỗi quy tắc nghiệp vụ vào cùng kết quả. Chỉ gọi `bind(...)` không đồng nghĩa validation đã chạy.

Constructor binding dùng điểm vào khác là `construct(ValueResolver)` vì constructor arguments phải được lấy trước khi object đích tồn tại. Cả hai luồng đều hội tụ ở `getBindingResult()` để bên gọi kiểm tra lỗi.

Nên xem `BindingResult` là bằng chứng của toàn bộ bước điều phối; object đích bị thay đổi một phần không chứng minh rằng toàn bộ dữ liệu đầu vào đã được chấp nhận đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="property-binding-model">Property và Setter Binding</a>

<details>
<summary>Xem chi tiết</summary>

Property binding bắt đầu với object đích đã tồn tại rồi áp giá trị đầu vào qua các writable property. Với mô hình bean property mặc định, Spring theo ngữ nghĩa JavaBean, hỗ trợ nested path và convert từng giá trị đầu vào về declared type của property trước khi gán.

```java
AccountForm form = new AccountForm();
DataBinder binder = new DataBinder(form, "account");
binder.setAllowedFields("name", "age");

binder.bind(new MutablePropertyValues(Map.of(
    "name", "Ada",
    "age", "37"
)));
```

Mô hình này thuận tiện cho mutable command object, nhưng bề mặt đầu vào chính là tập writable properties mà chính sách binding cho phép. Vì vậy chính sách field là mối quan tâm về an toàn chứ không chỉ là tinh chỉnh cấu hình.

`initDirectFieldAccess()` chuyển binder sang ghi field trực tiếp thay vì JavaBean property access. Đây là lựa chọn có chủ đích cho mô hình đặc biệt, không phải mặc định nên dùng: nó thay đổi member nào được xem là writable và có thể bỏ qua hành vi trong setter.

Property binding còn khác constructor binding ở thời điểm áp giá trị. Object đã tồn tại nên một số lần gán có thể thành công trước khi field khác thất bại. Hãy dùng `BindingResult` để đánh giá cả thao tác binding thay vì giả định việc thay đổi object là atomic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructor-binding-model">Constructor Binding</a>

<details>
<summary>Xem chi tiết</summary>

Constructor binding tạo object đích từ giá trị đầu vào thay vì thay đổi một object đã tạo sẵn. Trong Spring Framework 6.1, `DataBinder` cung cấp `construct(DataBinder.ValueResolver)`. Trước khi gọi, binder cần biết target type và thông thường chưa có object đích.

```java
DataBinder binder = new DataBinder(null, "account");
binder.setTargetType(ResolvableType.forClass(AccountCommand.class));

binder.construct(new DataBinder.ValueResolver() {
    @Override
    public Object resolveValue(String name, Class<?> type) {
        return input.get(name);
    }

    @Override
    public Set<String> getNames() {
        return input.keySet();
    }
});

BindingResult result = binder.getBindingResult();
AccountCommand command = (AccountCommand) result.getTarget();
```

Spring resolve constructor argument theo tên, hỗ trợ metadata như `@ConstructorProperties` và parameter name được giữ trong bytecode. `DataBinder.NameResolver` là extension point khi tên đầu vào cần ánh xạ. Lỗi conversion/validation trong luồng constructor được phản ánh qua `BindingResult`.

Đặc tính an toàn quan trọng là constructor binding chỉ hỏi các giá trị mà constructor path được chọn thực sự cần. Các thiết lập như unknown fields, required fields, allowed/disallowed fields là chính sách của property binding; chúng không định nghĩa lại constructor argument.

Constructor binding đặc biệt hợp với immutable hoặc input model được thiết kế riêng vì bề mặt được chấp nhận thể hiện ngay trên constructor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="declarative-binding-61">Declarative Binding trong Spring Framework 6.1</a>

<details>
<summary>Xem chi tiết</summary>

Spring Framework 6.1 bổ sung `setDeclarativeBinding(true)` để bề mặt binding được chấp nhận trở nên tường minh. Trong declarative mode, constructor binding vẫn luôn khả dụng, còn property binding chỉ chạy khi `allowedFields` đã được cấu hình.

```java
DataBinder binder = new DataBinder(command, "command");
binder.setDeclarativeBinding(true);
binder.setAllowedFields("displayName", "timezone");
```

Cách hình dung là **chỉ cho phép những writable property được chọn tường minh**. Nếu không bật declarative mode, property binding mặc định được phép theo các chính sách field thông thường. Khi mode này bật, writable property không được tự động xem là một phần của hợp đồng property binding nếu chưa nằm trong allowed fields.

Declarative binding không phải validation. Một field có thể cố ý được bind nhưng value vẫn invalid; ngược lại, một domain value hoàn toàn hợp lệ cũng không nên trở thành bindable nếu hợp đồng đầu vào không muốn expose nó.

Đây cũng không phải tính năng chỉ của MVC. MVC/WebFlux có thể cấu hình binder trong vòng đời controller, nhưng thiết lập nền tảng thuộc core `DataBinder`.

### Tài liệu tham khảo

- Spring Framework 6.1.14 API — `DataBinder.setDeclarativeBinding(boolean)`

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-field-policies">Required, Unknown, Invalid, Allowed và Disallowed Fields</a>

<details>
<summary>Xem chi tiết</summary>

Các chính sách field trả lời những câu hỏi khác nhau, không nên gộp thành một "chế độ nghiêm ngặt" duy nhất:

- **required fields**: field đã được cấu hình là required mặc định tạo binding error code `required` khi value vắng mặt hoặc bị phép kiểm tra required-field xem là rỗng; trường hợp này gồm `null`, `String` rỗng/chỉ có khoảng trắng, và `String[]` rỗng hoặc có phần tử đầu tiên rỗng/chỉ có khoảng trắng;
- **unknown fields**: tên đầu vào không có property tương ứng mặc định bị bỏ qua (`ignoreUnknownFields=true`); tắt flag này để biến chúng thành lỗi binding;
- **invalid fields**: tên có property tương ứng nhưng không truy cập được, ví dụ nested path không thể tiếp tục duyệt hoặc auto-grow, mặc định không bị bỏ qua (`ignoreInvalidFields=false`); `DataBinder` chuẩn mặc định auto-grow nested path khi có thể, nên chỉ riêng object trung gian đang `null` chưa chắc đã là invalid field;
- **allowed fields**: mẫu property được phép bind; mặc định là tất cả field;
- **disallowed fields**: mẫu property bị loại khỏi binding; mặc định không có field nào bị cấm.

```java
binder.setRequiredFields("name");
binder.setIgnoreUnknownFields(false);
binder.setAllowedFields("name", "profile.*");
```

Các thiết lập này áp dụng cho property binding qua `bind(PropertyValues)`, không áp dụng cho constructor binding qua `construct(...)`, vì luồng constructor chỉ resolve những giá trị nó cần.

Với dữ liệu đầu vào bên ngoài không đáng tin cậy, allow-list dễ suy luận hơn deny-list: writable property mới thêm vào sẽ không âm thầm trở thành dữ liệu được chấp nhận chỉ vì không ai nhớ cập nhật danh sách chặn. Required field cũng không phải quy tắc validation nghiệp vụ; nó có nghĩa "thao tác binding này phải cung cấp value không bị phép kiểm tra required-field xem là rỗng", không phải "object cuối cùng thỏa bất biến nghiệp vụ".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="data-binder-extension-points">Conversion, Custom Editor, Validator và Error Processing</a>

<details>
<summary>Xem chi tiết</summary>

`DataBinder` có nhiều extension point vì các ứng dụng khác nhau về cách biểu diễn dữ liệu và chính sách validation. Mỗi extension point có trách nhiệm riêng:

- `setConversionService(...)` cung cấp type conversion/formatting hiện đại;
- `registerCustomEditor(...)` tích hợp JavaBeans `PropertyEditor` legacy khi cần;
- `addValidators(...)`, `setValidator(...)` và các method liên quan cấu hình validation sau binding;
- `BindingErrorProcessor` quyết định cách missing required value và property-access exception trở thành binding error;
- `MessageCodesResolver` mở rộng error code thành các ứng viên để tra cứu.

Binding error processor mặc định ánh xạ required field bị thiếu thành code `required` và chuyển lỗi property access như lỗi type conversion thành field error. Có thể thay cách triển khai này, nhưng việc đó thay đổi cách biểu diễn lỗi nên cần một nhu cầu tích hợp cụ thể.

```java
DataBinder binder = new DataBinder(target, "target");
binder.setConversionService(conversionService);
binder.addValidators(domainValidator);
binder.setMessageCodesResolver(new DefaultMessageCodesResolver());
```

Ưu tiên `ConversionService` cho typed conversion có thể tái sử dụng. Custom editor chủ yếu hữu ích khi tích hợp code cũ đã dựa trên `PropertyEditor`. Giữ validation trong validator và chính sách bề mặt đầu vào trong cấu hình binder; trộn các trách nhiệm này khiến lỗi khó giải thích hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
