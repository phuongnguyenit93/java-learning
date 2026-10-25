<a id="back-to-top"></a>

# Truy cập thuộc tính và trạng thái Binding

## Menu
- [Property Path và truy cập object lồng nhau](#property-path-model)
- [BeanWrapper và abstraction truy cập thuộc tính](#bean-wrapper-role)
- [BindingResult và Errors](#binding-result-model)
- [ObjectError và FieldError](#object-field-error-model)
- [Lỗi trực tiếp và các trường hợp Binding phụ thuộc chính sách](#binding-error-policy)

## <a id="property-path-model">Property Path và truy cập object lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

Binding cần một cách ổn định để mô tả *giá trị sẽ được ghi vào đâu*. Spring dùng property path cho mục đích đó. Path đơn giản như `name` trỏ tới một property trên object đích; path lồng nhau như `address.city` đi xuyên qua object graph. Indexed/mapped path có thể trỏ tới phần tử container khi cách triển khai property-access hỗ trợ.

Mô hình tư duy hữu ích là một đường dẫn qua trạng thái có thể ghi:

```text
customer
  └─ address
       └─ city

property path: "address.city"
```

Property path chỉ mô tả vị trí đích; nó không tự là conversion hay validation rule. Trước khi gán giá trị, Spring vẫn phải xác định property có tồn tại không, có writable không, nested graph có truy cập được không và dữ liệu đầu vào có convert được sang declared type của property hay không.

```java
public final class CustomerForm {
    private Address address = new Address();

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
}

public final class Address {
    private String city;

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
```

Trong ví dụ này, `address.city` hợp lệ vì cả hai segment đều có thể được duyệt. Nếu `address` là `null`, hành vi phụ thuộc cấu hình property-access/binder. Một số accessor của Spring có thể auto-grow nested path; `DataBinder` cũng có chính sách cho invalid nested path. Không nên mặc định rằng mọi object trung gian `null` đều được tự động tạo.

Lợi ích thực tế của property path là binding, error reporting và validation có thể dùng chung một field identity ổn định. `FieldError` cho `address.city` ở bước sau vì vậy có thể chỉ chính xác vị trí trạng thái mà binding đã cố ghi dữ liệu vào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="bean-wrapper-role">BeanWrapper và abstraction truy cập thuộc tính</a>

<details>
<summary>Xem chi tiết</summary>

`BeanWrapper` là abstraction trung tâm ở mức thấp của Spring để làm việc với JavaBeans chuẩn. Nó có thể đọc property descriptor, kiểm tra property readable/writable, get/set value, duyệt nested property, thực hiện type conversion và tham gia đăng ký `PropertyEditor`.

Code ứng dụng thường không nên coi `BeanWrapper` là quy trình binding cấp cao. `DataBinder` thường dùng hạ tầng property-access này ở bên dưới. Hai vai trò khác nhau: `BeanWrapper` thao tác property; `DataBinder` thêm chính sách binding, kết quả binding, validation integration, allowed/disallowed fields, xử lý required field và các bước điều phối khác.

```java
CustomerForm form = new CustomerForm();

BeanWrapper wrapper =
        PropertyAccessorFactory.forBeanPropertyAccess(form);

wrapper.setPropertyValue("address.city", "Da Nang");

String city = (String) wrapper.getPropertyValue("address.city");
boolean writable = wrapper.isWritableProperty("address.city");
```

Vì `BeanWrapper` kế thừa các hợp đồng property-access và type-conversion có thể cấu hình của Spring, nó cũng có thể dùng `ConversionService`. Điều đó giải thích vì sao property assignment có thể kéo theo conversion nhưng không biến `BeanWrapper` thành nơi sở hữu chính sách conversion của ứng dụng.

Cần tách hai nhóm lỗi dễ nhầm:

- path nhìn có vẻ hợp lệ nhưng thực tế trỏ tới property không tồn tại hoặc không readable/writable;
- path trỏ tới nested property có thật nhưng fail vì intermediate object không truy cập được.

Đây là vấn đề property-access. Binder sẽ bỏ qua hay báo một số trường hợp trong đó hay không là chính sách ở tầng `DataBinder`, được làm rõ ở phần DataBinder về sau.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `BeanWrapper`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/beans/BeanWrapper.html)
- [Spring Framework 6.1.14 `PropertyAccessor`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/beans/PropertyAccessor.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-result-model">BindingResult và Errors</a>

<details>
<summary>Xem chi tiết</summary>

`Errors` là hợp đồng dùng chung của Spring để lưu và cung cấp lỗi data binding lẫn validation. `BindingResult` mở rộng `Errors` bằng thông tin phân tích và tích hợp riêng cho binding như object đích, raw field value, suppressed field, property editor đã đăng ký, hỗ trợ xây mô hình dữ liệu và phân giải message code.

Quan hệ kế thừa này tạo ra một cách phân vai rõ ràng:

```text
Errors
  └─ mô hình đăng ký/query error

BindingResult extends Errors
  └─ kết quả binding + thông tin phân tích riêng của binding
```

Validator có thể chỉ phụ thuộc `Errors` vì hợp đồng dùng chung đó đã đủ để ghi và truy vấn lỗi validation. Code cần kết quả cùng thông tin tích hợp chi tiết hơn của một thao tác bind có thể làm việc với `BindingResult`.

```java
CustomerForm form = new CustomerForm();
BindingResult result =
        new BeanPropertyBindingResult(form, "customer");

result.rejectValue(
        "address.city",
        "city.required",
        "City is required"
);

if (result.hasFieldErrors("address.city")) {
    FieldError error = result.getFieldError("address.city");
    System.out.println(error.getCode());
}
```

Ý quan trọng là Spring giữ lỗi dưới dạng dữ liệu có cấu trúc thay vì lập tức biến tất cả thành string hoặc throw exception. Cấu trúc này hỗ trợ message-code resolution, localization, cách hiển thị theo tầng truyền tải hoặc quyết định bằng code ở bước sau.

Vì vậy nên hiểu `BindingResult` là *mô hình kết quả* của binding và validation. Nó không quyết định một web controller lấy dữ liệu đầu vào từ đâu; việc điều phối đó thuộc web stack tương ứng.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `Errors`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/Errors.html)
- [Spring Framework 6.1.14 `BindingResult`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/BindingResult.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-field-error-model">ObjectError và FieldError</a>

<details>
<summary>Xem chi tiết</summary>

Spring biểu diễn phần lớn lỗi validation và binding bằng `ObjectError` và `FieldError`.

`ObjectError` áp dụng cho toàn object. Dùng nó khi quy tắc không thể gắn hợp lý vào một property, ví dụ "start date phải trước end date" hoặc "tổ hợp các field đang mâu thuẫn". `FieldError` cụ thể hơn: nó gắn vấn đề với field path như `quantity` hoặc `address.city`.

```java
errors.reject(
        "dateRange.invalid",
        "Start date must be before end date"
);

errors.rejectValue(
        "quantity",
        "quantity.positive",
        "Quantity must be positive"
);
```

Cả hai loại error đều là `MessageSourceResolvable`. Chúng có thể mang một hoặc nhiều message code, arguments và message mặc định. Vì vậy mô hình lỗi có thể giữ ổn định trong khi phần hiển thị được `MessageSource` bản địa hóa về sau.

Với binding failure, `FieldError` còn có thể giữ rejected value và binding-failure flag. Nhờ đó đoạn mã sử dụng phân biệt được field lỗi trong lúc áp dữ liệu bên ngoài hay lỗi ở validation rule sau đó.

Không nên dùng một câu đã bản địa hóa đầy đủ làm định danh duy nhất của quy tắc. Nên ưu tiên error code ổn định như `quantity.positive`, rồi để message resolution ánh xạ code đó thành nội dung hiển thị cho người dùng. Cơ chế bản địa hóa thuộc Core Container/i18n; module này sở hữu phía structured error và message code của hợp đồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="binding-error-policy">Lỗi trực tiếp và các trường hợp Binding phụ thuộc chính sách</a>

<details>
<summary>Xem chi tiết</summary>

Không phải mọi tên đầu vào đáng ngờ đều tạo cùng một loại lỗi. Spring cung cấp các tùy chọn chính sách vì mức độ chặt chẽ của property binding khác nhau giữa các ứng dụng.

Ba trường hợp rất dễ bị nhầm:

- **type mismatch**: property tồn tại nhưng submitted value không convert được sang kiểu cần thiết. Đây là lỗi trực tiếp của property binding.
- **unknown field**: dữ liệu đầu vào gọi tên một property không tồn tại trên object đích. `DataBinder` mặc định bỏ qua unknown field (`ignoreUnknownFields = true`), nhưng ứng dụng có thể tắt hành vi này.
- **invalid field**: path tương ứng với trạng thái đích nhưng hiện không truy cập được, ví dụ nested path không thể tiếp tục duyệt hoặc auto-grow. `DataBinder` chuẩn mặc định bật auto-grow cho nested path khi có thể, nên chỉ riêng việc object trung gian đang `null` chưa tự động biến nó thành invalid field. Nếu việc truy cập vẫn thất bại, `DataBinder` mặc định không bỏ qua invalid field (`ignoreInvalidFields = false`).

Required field là một chính sách khác. Field chỉ tham gia phép kiểm tra này khi binder đã được cấu hình qua `setRequiredFields(...)`; Java nullability không tự động biến một bind value thành required. Phép kiểm tra chặt hơn việc chỉ có tên field: value vắng mặt, `null`, `String` rỗng hoặc chỉ có khoảng trắng, và `String[]` rỗng hoặc có phần tử đầu tiên rỗng/chỉ có khoảng trắng đều bị xem là thiếu và mặc định tạo lỗi `required`.

```java
DataBinder binder = new DataBinder(new CustomerForm(), "customer");
binder.setRequiredFields("address.city");
binder.setIgnoreUnknownFields(false);
binder.setIgnoreInvalidFields(false);
```

Các tùy chọn trên áp dụng cho property binding qua `bind(PropertyValues)`. Chúng không phải quy tắc constructor binding. Constructor binding chỉ hỏi `ValueResolver` những value cần cho constructor parameter, nên "unknown field" không phải mô hình tư duy phù hợp cho luồng đó.

Điểm này quan trọng ở safe binding: xử lý chặt unknown input không đồng nghĩa đã định nghĩa bề mặt được phép ghi. Allowed/disallowed field và declarative binding giải quyết bài toán riêng đó.

### Tài liệu tham khảo

- [Spring Framework 6.1.14 `DataBinder`](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/validation/DataBinder.html)

</details>

- [Quay lại đầu trang](#back-to-top)
