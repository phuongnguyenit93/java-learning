# Meta-Annotation

Sau khi tự tạo `@Audit`, Java vẫn cần biết thêm:

```text
@Audit được phép đặt ở đâu?
→ method, class, field...?

@Audit phải tồn tại đến lúc nào?
→ source, class file hay runtime?

Nó có xuất hiện trong documentation không?
Nó có tham gia superclass lookup không?
Nó có được phép lặp lại không?
```

Nếu các rule này chỉ nằm trong comment thì compiler/tool không có contract chuẩn để kiểm tra. Vì vậy **chính annotation type cũng cần metadata mô tả nó**.

Meta-annotation là **annotation dùng để mô tả hoặc cấu hình một annotation interface khác**.

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

Ở đây `@Retention`, `@Target` và `@Documented` không mô tả business method; chúng mô tả chính contract của `@Audit`.

## <a id="retention-meta">@Retention</a>

`@Retention` quyết định annotation được giữ đến giai đoạn nào:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();
    int level() default 1;
}
```

Ba policy là:

```text
SOURCE  → source/compile-time
CLASS   → class file, không có runtime-reflection contract
RUNTIME → class file + runtime reflection
```

Nếu annotation interface không có `@Retention`, mặc định là `CLASS`.

Điểm thiết kế quan trọng là retention thuộc **annotation type đang được meta-annotate**. Nếu một annotation có element chứa nested annotation, retention của outer annotation không đơn giản “truyền xuống” thành retention policy độc lập cho nested annotation type.

Hãy chọn retention theo consumer, không theo thói quen.

## <a id="target-meta">@Target</a>

`@Target` giới hạn context mà annotation được dùng:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FeatureFlag {
}
```

Compiler enforce rule này:

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

`@Target` bản thân chỉ dùng trên annotation interface declaration (`ANNOTATION_TYPE`).

Không khai báo `@Target` không tương đương với `@Target(ElementType.TYPE_USE)` hay “mọi syntax location”. Vì vậy custom annotation public nên thường khai báo target explicit để contract không mơ hồ.

### Custom meta-annotation và giới hạn composition

Không chỉ các annotation chuẩn như `@Target` hay `@Retention` mới có thể annotate một annotation type. Ta cũng có thể tạo metadata dành riêng cho annotation type:

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

Ở đây `@FrameworkStereotype` mô tả chính annotation type `@AuditedComponent`.

Nhưng Java Core **không tự biến meta-annotation thành annotation composition/transitive lookup**:

```java
@AuditedComponent
class PaymentService {
}
```

`PaymentService` không vì thế mà tự trở thành “directly annotated với `@FrameworkStereotype`”. Nếu một framework muốn hỗ trợ composed annotation, stereotype hoặc recursive meta-annotation lookup thì framework đó phải tự định nghĩa và triển khai semantics.

Mental model:

```text
Java Core meta-annotation
→ metadata được gắn lên annotation type

framework composition
→ framework chủ động traverse/interpret meta-annotations
→ không phải behavior mặc định của Java
```

## <a id="documented-meta">@Documented</a>

`@Documented` nói rằng annotation usage nên được xem là một phần của public API contract khi documentation tool tạo tài liệu:

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StableApi {
}
```

Nếu một public type được annotate bằng `@StableApi`, generated API docs có thể trình bày annotation đó như một phần contract.

`@Documented` **không**:

- làm annotation có `RUNTIME` retention;
- làm annotation được kế thừa;
- làm framework tự đọc annotation.

Nó giải quyết dimension documentation, không phải lifecycle hay lookup.

## <a id="inherited-meta">@Inherited</a>

`@Inherited` thay đổi cách runtime annotation lookup hoạt động cho **class declaration**:

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

Khi query annotation trên `PaymentService.class` bằng API có inherited lookup semantics, Java có thể tìm `@AuditedType` từ superclass nếu subclass không có annotation tương ứng.

Ranh giới cực kỳ quan trọng:

```text
superclass class annotation
→ có thể được lookup qua @Inherited

implemented interface annotation
→ không được @Inherited tìm

method/field/constructor annotation
→ @Inherited không áp dụng
```

Ngoài ra, muốn runtime reflection quan sát được annotation thì annotation vẫn cần retention phù hợp, thường là `RUNTIME`. `@Inherited` không tự kéo metadata đã bị loại bỏ vào runtime.

## <a id="repeatable-meta">@Repeatable</a>

### VÌ SAO — tại sao cần `@Repeatable`?

Giả sử một method cần mang nhiều giá trị audit độc lập:

```java
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")
void transfer() {
}
```

Nếu `@Audit` **không được khai báo repeatable**, việc viết nhiều annotation cùng type tại cùng context sẽ bị compiler từ chối.

Một cách thủ công là bắt developer tự dùng container:

```java
@Audits({
    @Audit(action = "SECURITY"),
    @Audit(action = "COMPLIANCE")
})
void transfer() {
}
```

`@Repeatable` tồn tại để cho phép syntax tự nhiên hơn trong khi Java vẫn có một container contract rõ ràng phía dưới.

`@Repeatable` cho phép cùng một annotation type xuất hiện nhiều lần tại một vị trí hợp lệ:

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

Usage:

```java
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")
void transfer() {
}
```

`Audits` là **container annotation**. Java đặt các constraint để container tương thích với repeatable annotation, trong đó quan trọng nhất:

- container phải có `value()` trả về `Audit[]`;
- element khác của container phải có default;
- retention của container không được ngắn hơn repeatable annotation;
- repeatable annotation phải applicable ít nhất trên các declaration/type-use kind mà container hỗ trợ; container có thể hẹp hơn, và khi đó nơi được phép repeat cũng bị hẹp theo;
- nếu repeatable annotation là `@Documented` hoặc `@Inherited` thì container cũng phải đáp ứng contract tương ứng.

### Chuyển sang lookup semantics

`@Repeatable` và `@Inherited` nhìn đơn giản ở declaration, nhưng điểm khó nằm ở **lookup**: annotation nào được xem là directly present, indirectly present hay inherited? Chapter tiếp theo tập trung vào chính ranh giới đó.
