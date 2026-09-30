# Meta-Annotation và cách cấu hình kiểu Annotation

Sau khi tự tạo `@Audit`, Java vẫn cần biết thêm:

```text
@Audit được phép đặt ở đâu?
→ method, class, field...?

@Audit phải tồn tại đến lúc nào?
→ mã nguồn, class file hay thời gian chạy?

Nó có xuất hiện trong tài liệu không?
Nó có tham gia tra cứu qua superclass không?
Nó có được phép lặp lại không?
```

Nếu các quy tắc này chỉ nằm trong chú thích mã nguồn thì trình biên dịch/công cụ không có cấu trúc chuẩn để kiểm tra. Vì vậy **chính kiểu Annotation cũng cần siêu dữ liệu mô tả nó**.

Meta-Annotation là **Annotation dùng để mô tả hoặc cấu hình một annotation interface khác**.

Ví dụ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Audit {
    String action();
    int level() default 1;
}
```

Ở đây `@Retention`, `@Target` và `@Documented` không mô tả method nghiệp vụ; chúng mô tả chính quy tắc của `@Audit`.

## <a id="retention-meta">@Retention</a>

`@Retention` quyết định Annotation được giữ đến giai đoạn nào:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Ba chính sách là:

```text
SOURCE  → mã nguồn/thời điểm biên dịch
CLASS   → class file, không có quy tắc Reflection ở thời gian chạy
RUNTIME → class file + Reflection ở thời gian chạy
```

Nếu annotation interface không có `@Retention`, mặc định là `CLASS`.

Điểm thiết kế quan trọng là chính sách lưu giữ thuộc **kiểu Annotation đang được gắn Meta-Annotation**. Nếu một Annotation có phần tử chứa Annotation lồng nhau, chính sách của Annotation bên ngoài không đơn giản “truyền xuống” thành chính sách độc lập cho kiểu Annotation bên trong.

Hãy chọn chính sách lưu giữ theo thành phần đọc, không theo thói quen.

## <a id="target-meta">@Target</a>

`@Target` giới hạn ngữ cảnh mà Annotation được dùng:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FeatureFlag {
}
```

Trình biên dịch kiểm tra quy tắc này:

```java
@FeatureFlag
class CheckoutService {

    @FeatureFlag
    void checkout() {
    }

    // @FeatureFlag
    // String state; // lỗi nếu FIELD không nằm trong @Target
}
```

`@Target` bản thân chỉ dùng trên khai báo annotation interface (`ANNOTATION_TYPE`).

Không khai báo `@Target` không tương đương với `@Target(ElementType.TYPE_USE)` hay “mọi vị trí cú pháp”. Vì vậy Annotation tùy chỉnh public nên thường khai báo `@Target` tường minh để quy tắc không mơ hồ.

### Meta-Annotation tùy chỉnh và giới hạn kết hợp

Không chỉ các Annotation chuẩn như `@Target` hay `@Retention` mới có thể gắn lên một kiểu Annotation. Ta cũng có thể tạo siêu dữ liệu dành riêng cho kiểu Annotation:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface FrameworkStereotype {
}

@FrameworkStereotype
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedComponent {
}
```

Ở đây `@FrameworkStereotype` mô tả chính kiểu Annotation `@AuditedComponent`.

Nhưng Java Core **không tự biến Meta-Annotation thành cơ chế kết hợp Annotation hay tra cứu bắc cầu**:

```java
@AuditedComponent
class PaymentService {
}
```

`PaymentService` không vì thế mà tự trở thành “được gắn trực tiếp `@FrameworkStereotype`”. Nếu một framework muốn hỗ trợ Annotation kết hợp, stereotype hoặc tra cứu Meta-Annotation đệ quy thì framework đó phải tự định nghĩa và triển khai ngữ nghĩa.

Mô hình tư duy:

```text
Meta-Annotation của Java Core
→ siêu dữ liệu được gắn lên kiểu Annotation

cơ chế kết hợp của framework
→ framework chủ động duyệt/diễn giải các Meta-Annotation
→ không phải hành vi mặc định của Java
```

## <a id="documented-meta">@Documented</a>

`@Documented` nói rằng việc sử dụng Annotation nên được xem là một phần của quy tắc API public khi công cụ tài liệu tạo tài liệu:

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StableApi {
}
```

Nếu một type public được gắn `@StableApi`, tài liệu API được sinh ra có thể trình bày Annotation đó như một phần của quy tắc.

`@Documented` **không**:

- làm Annotation có chính sách `RUNTIME`;
- làm Annotation được kế thừa;
- làm framework tự đọc Annotation.

Nó giải quyết khía cạnh tài liệu, không phải vòng đời hay tra cứu.

## <a id="inherited-meta">@Inherited</a>

`@Inherited` khai báo rằng một Annotation trên **class** có thể tham gia cơ chế tra cứu đi lên superclass ở thời gian chạy:

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedType {
}

@AuditedType
class BaseService {
}

class PaymentService extends BaseService {
}
```

Điều `@Inherited` cấu hình ở đây chỉ là **khả năng tham gia tra cứu theo chuỗi kế thừa class**. Nó không biến Annotation thành siêu dữ liệu tự sao chép sang mọi subtype/member:

```text
class annotation + @Inherited
→ có thể được tra cứu qua superclass

method / field / constructor / parameter / interface
→ không nhận quy tắc kế thừa này chỉ vì có @Inherited
```

Muốn Reflection quan sát được kết quả ở thời gian chạy, Annotation vẫn phải có chính sách lưu giữ phù hợp, thường là `RUNTIME`. Chương tiếp theo sẽ đi sâu vào API tra cứu, cơ chế tìm ngược lên superclass và ranh giới method/interface.

## <a id="repeatable-meta">@Repeatable</a>

`@Repeatable` cấu hình một kiểu Annotation để cùng Annotation đó có thể xuất hiện nhiều lần tại một vị trí hợp lệ. Java yêu cầu chỉ rõ **Annotation chứa** dùng để biểu diễn tập giá trị lặp:

```java
@Repeatable(Audits.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audits {
    Audit[] value();
}
```

Sau đó người dùng có thể viết:

```java
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")
void transfer() {
}
```

Ở chương này chỉ cần giữ mô hình tư duy: `@Repeatable` cấu hình **mối quan hệ giữa Annotation lặp lại và Annotation chứa**. Các ràng buộc tương thích của Annotation chứa, cách Java biểu diễn nhiều giá trị và sự khác nhau giữa API tra cứu một Annotation với API `...AnnotationsByType(...)` thuộc chương tiếp theo.
