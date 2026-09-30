# Định nghĩa Annotation tùy chỉnh

Annotation tùy chỉnh cho phép ứng dụng hoặc framework định nghĩa **cấu trúc siêu dữ liệu của riêng mình**. Nhưng việc tạo một Annotation mới không nên bắt đầu từ câu hỏi “cú pháp `@interface` viết thế nào?”, mà từ câu hỏi:

> Thành phần nào sẽ đọc siêu dữ liệu này, và nó cần biết điều gì?

Ví dụ xuyên suốt của module:

```java
@Audit(action = "TRANSFER", level = 2)
void transfer() {
}
```

## <a id="declare-annotation">Khai báo kiểu Annotation</a>

Custom annotation được khai báo bằng `@interface`:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

Mỗi khai báo có dạng giống method bên trong định nghĩa một **phần tử Annotation (annotation element)**.

Annotation interface có ngữ nghĩa riêng, không phải một interface thường chỉ vì cú pháp có chữ `interface`. Nó có superinterface trực tiếp là `java.lang.annotation.Annotation`; lập trình viên không tự viết `extends Annotation` để biến một interface thường thành kiểu Annotation.

Các phần tử cũng có giới hạn riêng:

- không có parameter;
- không có type parameter;
- không có `throws`;
- return type phải thuộc nhóm type hợp lệ dành cho phần tử Annotation.

Annotation interface bản thân cũng không generic và không khai báo một `extends` clause tùy ý. Các method đại diện cho phần tử Annotation có quy tắc chuyên biệt tương đương accessor của siêu dữ liệu, không phải nơi định nghĩa hành vi `static`/`default`/`private` như interface thường.

Ví dụ sử dụng:

```java
@Audit(action = "TRANSFER", level = 2)
public void transfer() {
}
```

Trình biên dịch kiểm tra tên phần tử, type của giá trị và việc đã cung cấp các phần tử bắt buộc hay chưa.

### Kiểu Annotation chỉ định nghĩa cấu trúc siêu dữ liệu

Khai báo `@Audit` không tự ghi log:

```text
khai báo @Audit
→ định nghĩa cấu trúc siêu dữ liệu

vị trí sử dụng @Audit
→ gắn siêu dữ liệu cụ thể vào mã nguồn

thành phần đọc
→ đọc siêu dữ liệu và quyết định làm gì
```

Thành phần đọc tại thời gian chạy có thể là framework/Reflection. Thành phần đọc tại thời điểm biên dịch có thể là annotation processor. Nếu không có thành phần đọc, siêu dữ liệu vẫn chỉ là siêu dữ liệu.

## <a id="annotation-restrictions">Kiểu dữ liệu và ràng buộc của phần tử Annotation</a>

Annotation không cho phép phần tử mang bất kỳ đối tượng tùy ý nào. Java giới hạn type của phần tử để siêu dữ liệu có thể được biểu diễn ổn định trong class file và mô hình mã nguồn.

Các nhóm type hợp lệ là:

- primitive type như `int`, `boolean`, `double`;
- `String`;
- `Class` hoặc dạng parameterized của `Class`, ví dụ `Class<? extends Handler>`;
- enum type;
- annotation type khác;
- mảng một chiều của một trong các type hợp lệ trên.

Không chỉ return type bị giới hạn; **giá trị được ghi khi sử dụng Annotation cũng phải là dạng Java có thể biểu diễn như giá trị Annotation tại thời điểm biên dịch**: constant expression phù hợp, class literal, enum constant, Annotation lồng nhau hoặc mảng các giá trị hợp lệ. Không thể dùng `new SomeObject()` hay gọi một method tùy ý để tính giá trị Annotation.

Ví dụ:

```java
public @interface EndpointPolicy {
    String name();
    int timeoutSeconds() default 30;
    Class<? extends Runnable> handler();
    Mode mode() default Mode.SYNC;
    Tag tag() default @Tag("default");
    String[] roles() default {};
}
```

Các dạng sau không hợp lệ:

```java
public @interface Invalid {
    // Object value();          // không hợp lệ
    // List<String> names();   // không hợp lệ
    // String[][] matrix();    // mảng lồng nhau không hợp lệ
}
```

Phần tử Annotation cũng không dùng `null` làm giá trị. Nếu miền nghiệp vụ cần biểu diễn “không có giá trị”, quy tắc Annotation phải thiết kế một giá trị mặc định/giá trị đặc biệt rõ ràng hoặc tách siêu dữ liệu thành cấu trúc khác.

### Phần tử Annotation không được tạo vòng phụ thuộc

Mặc dù kiểu Annotation khác là một type hợp lệ cho phần tử, annotation interface không được tự tham chiếu qua phần tử theo kiểu trực tiếp hoặc gián tiếp:

```java
// Không hợp lệ: tự tham chiếu trực tiếp
@interface A {
    A value();
}

// Không hợp lệ: vòng A -> B -> A
@interface B {
    C value();
}

@interface C {
    B value();
}
```

Lý do là cấu trúc Annotation phải hữu hạn để trình biên dịch/class file có thể biểu diễn; khai báo phần tử Annotation tạo vòng phụ thuộc là lỗi tại thời điểm biên dịch.

### Phần tử không được xung đột với quy tắc của `Object` / `Annotation`

Phần tử Annotation nhìn giống method không tham số, nhưng không được có signature tương đương về ghi đè với method `public` hoặc `protected` của `Object` hay `java.lang.annotation.Annotation`.

Ví dụ:

```java
public @interface InvalidMetadata {
    // int hashCode();          // không hợp lệ
    // String toString();       // không hợp lệ
    // Class annotationType();  // không hợp lệ
}
```

Các method như `equals(...)`, `hashCode()`, `toString()` và `annotationType()` đã thuộc quy tắc chung của một Annotation instance; chúng không phải phần tử siêu dữ liệu mà người định nghĩa Annotation được phép định nghĩa lại.

### Vì sao các giới hạn này quan trọng?

Siêu dữ liệu Annotation không phải một đồ thị đối tượng tùy ý ở thời gian chạy. Nó phải có thể được trình biên dịch ghi, công cụ đọc và class loader/JVM biểu diễn theo định dạng đã xác định. Vì vậy Annotation phù hợp với **siêu dữ liệu khai báo nhỏ, ổn định**, không phù hợp để chứa đối tượng nghiệp vụ phức tạp.

## <a id="annotation-elements-defaults">Phần tử và giá trị mặc định</a>

Phần tử không có giá trị mặc định là bắt buộc. Giữ nguyên ví dụ xuyên suốt: `action` không có giá trị mặc định nên bắt buộc, còn `level` có giá trị mặc định nên có thể bỏ qua khi sử dụng:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

Cách sử dụng tối thiểu chỉ cần cung cấp `action`:

```java
@Audit(action = "TRANSFER")
```

Có thể khai báo giá trị mặc định:

```java
public @interface Audit {
    String action();
    int level() default 1;
}
```

Khi đó:

```java
@Audit(action = "TRANSFER")
```

sẽ dùng `level = 1`.

Phần tử dạng mảng cũng có thể có giá trị mặc định. Ví dụ:

```java
public @interface Labels {
    String[] value() default {};
}
```

### Giá trị mặc định không phải `default method`

Từ khóa `default` ở phần tử Annotation chỉ định **giá trị siêu dữ liệu mặc định**, không có ngữ nghĩa giống `default method` của interface thường.

Một chi tiết quan trọng: giá trị mặc định thuộc kiểu Annotation, không được sao chép trực tiếp vào từng vị trí sử dụng Annotation thiếu giá trị đó. Khi Annotation được đọc, giá trị mặc định hiện tại của kiểu Annotation được áp dụng. Vì vậy thay đổi giá trị mặc định có thể thay đổi giá trị quan sát được từ class đã biên dịch trước đó nếu nơi sử dụng không ghi giá trị tường minh.

Đây là lý do giá trị mặc định cũng là một phần của **quy tắc tương thích**.

Phần tử Annotation không nhận `null`. Nếu cần trạng thái “không có giá trị”, hãy thiết kế giá trị mặc định/giá trị đặc biệt có nghĩa rõ ràng, ví dụ:

```java
// Ví dụ giá trị đặc biệt nếu miền nghiệp vụ thật sự cần:
// enum AuditLevel { DEFAULT, LOW, HIGH }
```

Tránh các giá trị đặc biệt khó hiểu như `"N/A"` hoặc `-1` nếu thành phần đọc phải tự đoán ngữ nghĩa.

## <a id="marker-annotation">Annotation đánh dấu (marker annotation)</a>

Annotation đánh dấu là annotation interface **không có phần tử**:

```java
public @interface Audited {
}
```

Cách sử dụng:

```java
@Audited
class PaymentService {
}
```

Ý nghĩa thường chỉ dựa trên việc Annotation có xuất hiện hay không:

```text
có @Audited
→ tham gia vào một quy tắc

không có @Audited
→ không tham gia
```

Cần phân biệt kiểu Annotation đánh dấu với **cú pháp viết dạng marker**. Một Annotation có các phần tử nhưng tất cả đều có giá trị mặc định vẫn có thể được viết như:

```java
@Feature
```

nhưng kiểu Annotation đó không phải Annotation đánh dấu thực sự vì cấu trúc vẫn có phần tử.

Marker phù hợp khi siêu dữ liệu thật sự chỉ cần biểu diễn một trạng thái có/không. Nếu bắt đầu xuất hiện nhiều biến thể, nên dùng một cấu trúc có nghĩa thay vì tạo hàng loạt marker gần giống nhau.

## <a id="custom-annotation-design">Thiết kế siêu dữ liệu có ý nghĩa</a>

Một Annotation tùy chỉnh tốt nên trả lời bốn câu hỏi:

1. **Thành phần đọc là ai?** annotation processor, framework lúc chạy hay công cụ khác?
2. **Siêu dữ liệu mô tả điều gì?** chính sách, ánh xạ, khả năng hay quy tắc?
3. **Nó hợp lệ ở đâu?** class, method, field, parameter, type use?
4. **Nó cần tồn tại bao lâu?** mã nguồn, class file hay thời gian chạy?

Với ví dụ xuyên suốt, giả sử một thành phần audit ở thời gian chạy sẽ đọc các method được đánh dấu:

```java
@Audit(action = "TRANSFER", level = 2)
void transfer() {
}
```

Từ yêu cầu đó, ta có thể nhìn thấy các câu hỏi thiết kế tiếp theo mà chưa cần học cú pháp của chúng ngay:

```text
thành phần đọc → thành phần lúc chạy
→ siêu dữ liệu phải tồn tại đến thời gian chạy

quy tắc audit chỉ áp dụng cho method
→ Annotation nên bị giới hạn vào method

action + level
→ cấu trúc siêu dữ liệu của @Audit
```

Hai câu hỏi “tồn tại đến bao giờ?” và “được đặt ở đâu?” sẽ lần lượt được biểu diễn bằng `@Retention` và `@Target` ở các chương tiếp theo.

### Java kiểm tra cấu trúc, không kiểm tra toàn bộ ý nghĩa nghiệp vụ

Trình biên dịch có thể biết `level()` là `int` hay một phần tử khác có type cụ thể, nhưng không tự biết:

```text
timeout phải > 0
action phải thuộc quy ước đặt tên nội bộ
hai phần tử này không được xuất hiện cùng nhau
```

Những ràng buộc nghiệp vụ như vậy phải được thành phần đọc/bộ xử lý kiểm tra và nên được tài liệu hóa rõ.

### Chuyển sang @Retention

Ta đã có cấu trúc siêu dữ liệu. Câu hỏi tiếp theo là: **thành phần đọc cần siêu dữ liệu tồn tại đến giai đoạn nào?** Đây chính là trách nhiệm của chính sách lưu giữ với `@Retention`.
