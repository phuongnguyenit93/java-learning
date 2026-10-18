# Vòng đời của Annotation với @Retention

`@Retention` trả lời một câu hỏi về **vòng đời của siêu dữ liệu**:

> Annotation cần tồn tại đến lúc xử lý mã nguồn, class file hay thời gian chạy?

Java có ba `RetentionPolicy`: `SOURCE`, `CLASS`, `RUNTIME`. Chọn chính sách lưu giữ không nên dựa trên suy nghĩ “RUNTIME mạnh hơn”, mà dựa trên **thành phần nào thực sự cần đọc Annotation và ở giai đoạn nào**.

Nếu kiểu Annotation không khai báo `@Retention`, chính sách mặc định là **`CLASS`**.

Chương này chỉ tập trung vào **vòng đời**. Để tránh trộn hai câu hỏi quá sớm, các ví dụ dưới đây chưa thêm `@Target`; chương kế tiếp sẽ quyết định Annotation được phép đặt ở đâu.

## <a id="retention-source">Chính sách SOURCE</a>

```java
@Retention(RetentionPolicy.SOURCE)
public @interface CompileNote {
}
```

Annotation có chính sách `SOURCE` chỉ cần tồn tại trong mã nguồn/mô hình lúc biên dịch và bị trình biên dịch loại bỏ khỏi biểu diễn trong class file.

Phù hợp khi siêu dữ liệu phục vụ:

- kiểm tra của trình biên dịch;
- phân tích tĩnh;
- công cụ làm việc trên mã nguồn;
- annotation processing không cần siêu dữ liệu tồn tại trong class file đầu ra.

Mô hình tư duy:

```text
.java
  @CompileNote
      ↓ trình biên dịch/bộ xử lý có thể đọc
.class
  siêu dữ liệu không còn
      ↓
Reflection ở thời gian chạy không thể đọc
```

Một annotation processor vẫn có thể xử lý Annotation `SOURCE` vì processor chạy **trong quá trình biên dịch**, trước khi siêu dữ liệu bị loại khỏi class file đầu ra.

Không nên chọn `SOURCE` nếu ứng dụng cần `getAnnotation(...)` ở thời gian chạy.

## <a id="retention-class">Chính sách CLASS</a>

`CLASS` giữ Annotation trong class file nhưng JVM **không bắt buộc** phải giữ nó để Reflection chuẩn ở thời gian chạy truy cập.

```java
@Retention(RetentionPolicy.CLASS)
public @interface BytecodeMetadata {
}
```

Đây cũng là chính sách mặc định nếu không viết `@Retention`.

Trường hợp sử dụng điển hình:

- công cụ xử lý bytecode;
- phân tích/chèn mã sau khi biên dịch;
- siêu dữ liệu cần đi cùng class file nhưng không cần Reflection ở thời gian chạy.

Mô hình tư duy:

```text
.java
  annotation
      ↓
.class
  annotation vẫn được ghi
      ↓
Reflection ở thời gian chạy
  không quan sát được như Annotation RUNTIME
```

Sai sót phổ biến là quên `@Retention(RUNTIME)` rồi thắc mắc vì sao Reflection không thấy Annotation tùy chỉnh.

## <a id="retention-runtime">Chính sách RUNTIME</a>

`RUNTIME` giữ Annotation trong class file và JVM giữ siêu dữ liệu để Reflection chuẩn ở thời gian chạy có thể đọc:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Ví dụ:

```java
Method method = PaymentService.class.getDeclaredMethod("transfer");
Audit audit = method.getAnnotation(Audit.class);

if (audit != null) {
    System.out.println(audit.action());
}
```

Đây là chính sách phù hợp khi framework hoặc mã ứng dụng ở thời gian chạy cần đọc siêu dữ liệu.

Nhưng:

> `RUNTIME` chỉ làm siêu dữ liệu **có thể được đọc ở thời gian chạy**; nó không tự kích hoạt interception, validation hay dependency injection.

Một thành phần đọc vẫn phải chủ động đọc và xử lý siêu dữ liệu.

Với Annotation trên **type use**, việc kiểm tra ở thời gian chạy thường đi qua các API thuộc họ `AnnotatedType` thay vì chỉ các API Annotation trên khai báo quen thuộc của `AnnotatedElement`.

## <a id="retention-use-case">Chọn chính sách lưu giữ theo thành phần đọc</a>

Thay vì mặc định dùng `RUNTIME`, hãy bắt đầu từ thành phần đọc:

| Thành phần đọc cần siêu dữ liệu ở đâu? | Chính sách phù hợp |
| --- | --- |
| Chỉ mã nguồn/trình biên dịch/phân tích tĩnh | `SOURCE` |
| Class file/công cụ bytecode | `CLASS` |
| Reflection/framework ở thời gian chạy | `RUNTIME` |

Ví dụ:

```text
@GeneratedHint cho bộ xử lý mã nguồn
→ SOURCE có thể đủ

@BytecodeRule cho class-file transformer
→ CLASS

@Audit được interceptor/mã chạy ở thời gian chạy đọc
→ RUNTIME
```

### Đánh đổi thiết kế

Giữ siêu dữ liệu lâu hơn mức cần thiết không tự động tốt hơn. Nó làm quy tắc rộng hơn và có thể khiến người dùng hiểu rằng kiểm tra lúc chạy là một phần của API.

Ngược lại, chính sách lưu giữ quá ngắn làm siêu dữ liệu biến mất trước khi thành phần đọc cần nó.

Quy tắc thực dụng:

```text
xác định thành phần đọc
→ xác định giai đoạn thành phần đó chạy
→ chọn chính sách lưu giữ ngắn nhất vẫn đáp ứng nhu cầu
```

### @Retention và xử lý Annotation tại thời điểm biên dịch

Annotation processor chạy trong quá trình biên dịch nên có thể xử lý Annotation mà mã ở thời gian chạy sẽ không bao giờ thấy. Đây là khác biệt nền tảng:

```text
xử lý tại thời điểm biên dịch
≠
Reflection tại thời gian chạy
```

Ta sẽ đi sâu vào xử lý Annotation tại thời điểm biên dịch ở một chương phía sau. Chương tiếp theo giải quyết một khía cạnh độc lập còn lại: **Annotation được phép xuất hiện ở đâu?**
