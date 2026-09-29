# Repeatable và Inherited Annotation

Hai meta-annotation `@Repeatable` và `@Inherited` không chỉ thay đổi cú pháp khai báo. Chúng làm thay đổi **cách metadata được biểu diễn và tìm kiếm**.

Đây là nơi dễ xảy ra hiểu nhầm nhất:

```text
annotation xuất hiện trong source
≠
mọi API reflection đều trả về nó theo cùng một cách
```

Chapter này tập trung vào lookup semantics cần thiết để dùng repeatable/inherited annotation đúng.

## <a id="repeatable-container">Repeatable annotation và container</a>

Nếu `@Audit` không có `@Repeatable`, việc viết cùng annotation type hai lần tại cùng context là compile-time error. Vì vậy repeatability là **một phần explicit của annotation contract**, không phải chỉ là cách viết đẹp hơn.

Khai báo repeatable annotation:

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

Khi cùng một repeatable annotation xuất hiện nhiều lần, Java sử dụng **containing annotation** (`Audits`) để biểu diễn tập giá trị theo contract của `@Repeatable`.

Mental model:

```text
source
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")

logical/container representation
@Audits({
    @Audit(action = "SECURITY"),
    @Audit(action = "COMPLIANCE")
})
```

Container không phải chi tiết có thể bỏ qua hoàn toàn, vì API reflection “raw” và API “by type” có semantics khác nhau.

Các repeatable values giữ thứ tự source theo contract biểu diễn của container. Không nên trộn tùy tiện explicit container và repeated annotation ở cùng context; ngoài việc khó đọc, có những combination tạo duplicate-container compile-time error.

## <a id="get-annotations-by-type">getAnnotationsByType()</a>

Trước khi nhìn API, cần phân biệt bốn khái niệm lookup mà Java dùng:

```text
directly present
→ annotation T được gắn trực tiếp lên element

indirectly present
→ annotation T nằm bên trong container của một repeatable annotation

present
→ directly present
→ hoặc, với class + @Inherited, được tìm thấy qua superclass theo rule của Java

associated
→ directly present hoặc indirectly present
→ hoặc, với class + @Inherited, fallback lên superclass khi local element không có associated annotation của T
```

Từ đó các API quen thuộc có thể đọc như sau:

| API | Container repeatable | Superclass `@Inherited` |
| --- | --- | --- |
| `getDeclaredAnnotation(T)` | không unwrap container | không |
| `getAnnotation(T)` | không unwrap container | có với class khi `T` là `@Inherited` |
| `getDeclaredAnnotationsByType(T)` | có | không |
| `getAnnotationsByType(T)` | có | có với class khi `T` là `@Inherited` |
| `isAnnotationPresent(T)` | cùng semantics single lookup như `getAnnotation(T) != null` | tương ứng |

Đây là lý do không nên dùng `getAnnotation(T)` rồi mong nó “tự hiểu” mọi repeatable value.

Khi làm việc với repeatable annotation, API phù hợp thường là:

```java
Method method = PaymentService.class.getDeclaredMethod("transfer");

Audit[] audits = method.getAnnotationsByType(Audit.class);
```

`getAnnotationsByType(Audit.class)` hiểu repeatable/container semantics và trả về các `Audit` riêng lẻ.

Ngược lại:

```java
Audit audit = method.getAnnotation(Audit.class);
```

không phải API tốt để đọc một repeatable annotation. Khi chỉ có một `@Audit` trực tiếp, call có thể trả về annotation đó. Sau khi thêm annotation thứ hai, metadata có thể được chứa qua container và `getAnnotation(Audit.class)` không còn cho kết quả như code cũ mong đợi.

Đây là một compatibility pitfall quan trọng:

> Nếu annotation type là repeatable, consumer nên dùng API `*AnnotationsByType` thay vì giả định chỉ có một instance.

### Declared vs inherited lookup

`getDeclaredAnnotationsByType(T)`:

- chỉ xem metadata khai báo tại element hiện tại;
- unwrap container;
- không đi lên superclass.

`getAnnotationsByType(T)` trên `Class`:

- unwrap container;
- có thể áp dụng `@Inherited` semantics;
- chỉ fallback lên superclass khi class hiện tại không có associated annotation của type đó.

Điều này có nghĩa repeatable inherited annotations **không được cộng dồn từ mọi superclass**.

## <a id="inherited-class-only">@Inherited chỉ áp dụng cho class</a>

Ví dụ:

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

Lookup:

```java
AuditedType annotation =
    PaymentService.class.getAnnotation(AuditedType.class);
```

có thể tìm annotation từ `BaseService`.

Nhưng:

```java
@AuditedType
interface AuditedContract {
}

class PaymentService implements AuditedContract {
}
```

`@Inherited` **không** làm `PaymentService` nhận annotation từ interface.

Rule cần ghi nhớ:

```text
@Inherited
→ superclass chain của class declaration
→ không phải interface graph
```

## <a id="annotation-inheritance-boundaries">Ranh giới inheritance của annotation</a>

Tên `@Inherited` dễ khiến người học suy luận quá rộng. Nó không có nghĩa “metadata được kế thừa giống method”.

Ví dụ:

```java
class BaseService {
    @Audit(action = "BASE")
    public void process() {
    }
}

class PaymentService extends BaseService {
    @Override
    public void process() {
    }
}
```

Method `PaymentService.process()` không tự mang `@Audit(action = "BASE")` chỉ vì nó override method của superclass. Method annotation lookup không dùng `@Inherited`.

Tương tự:

- field annotation không tự truyền xuống subclass field;
- constructor annotation không truyền;
- interface annotation không đi sang implementing class qua `@Inherited`;
- class-level inherited lookup chỉ hoạt động nếu annotation type có `@Inherited` và metadata còn tồn tại ở runtime.

### Repeatable + inherited không phải merge-all

Giả sử superclass có:

```java
@Tag("base-a")
@Tag("base-b")
class Base {
}
```

và subclass có:

```java
@Tag("child")
class Child extends Base {
}
```

Nếu `@Tag` là repeatable + inherited, lookup “by type” trên `Child` dùng associated annotations local; nó không mặc định trả `child + base-a + base-b` như một phép merge hierarchy.

Đây là khác biệt giữa:

```text
inheritance fallback
và
hierarchical accumulation
```

Java annotation inheritance là fallback lookup có rule xác định, không phải một config-merging framework.

### Ranh giới với reflection module

Module Annotation cần hiểu các API lookup trên để giải thích semantics của `@Repeatable` và `@Inherited`. Các chủ đề sâu hơn như `Class`, `Method`, access control và reflective invocation thuộc module `reflection`.

Chapter cuối chuyển sang một consumer hoàn toàn khác: **annotation processor chạy lúc compile**, trước khi application được khởi động.
