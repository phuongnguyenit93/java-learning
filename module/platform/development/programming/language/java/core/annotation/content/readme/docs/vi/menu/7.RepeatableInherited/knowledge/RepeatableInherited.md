# Annotation lặp lại và kế thừa

Hai Meta-Annotation `@Repeatable` và `@Inherited` không chỉ thay đổi cú pháp khai báo. Chúng làm thay đổi **cách siêu dữ liệu được biểu diễn và tra cứu**.

Đây là nơi dễ xảy ra hiểu nhầm nhất:

```text
Annotation xuất hiện trong mã nguồn
≠
mọi API Reflection đều trả về nó theo cùng một cách
```

Chương này tập trung vào ngữ nghĩa tra cứu cần thiết để dùng Annotation lặp lại và kế thừa đúng cách.

## <a id="repeatable-container">Annotation lặp lại và Annotation chứa</a>

Nếu `@Audit` không có `@Repeatable`, việc viết cùng một kiểu Annotation hai lần tại cùng ngữ cảnh là lỗi khi biên dịch. Vì vậy khả năng lặp lại là **một phần được khai báo tường minh trong quy tắc của Annotation**, không phải chỉ là cách viết đẹp hơn.

Khai báo Annotation lặp lại:

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

Cách sử dụng:

```java
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")
void transfer() {
}
```

Khi cùng một Annotation lặp lại xuất hiện nhiều lần, Java sử dụng **Annotation chứa** (`Audits`) để biểu diễn tập giá trị theo quy tắc của `@Repeatable`.

Java kiểm tra sự tương thích giữa Annotation lặp lại và Annotation chứa. Các ràng buộc quan trọng gồm:

- Annotation chứa phải có `value()` trả về `Audit[]`;
- các phần tử khác của Annotation chứa phải có giá trị mặc định;
- chính sách lưu giữ của Annotation chứa không được ngắn hơn Annotation lặp lại;
- Annotation lặp lại phải áp dụng được ít nhất trên các loại khai báo/vị trí sử dụng type mà Annotation chứa hỗ trợ; Annotation chứa có thể hẹp hơn và khi đó nơi được phép lặp cũng hẹp theo;
- nếu Annotation lặp lại có `@Documented` hoặc `@Inherited` thì Annotation chứa cũng phải đáp ứng quy tắc tương ứng.

Mô hình tư duy:

```text
mã nguồn
@Audit(action = "SECURITY")
@Audit(action = "COMPLIANCE")

biểu diễn logic qua Annotation chứa
@Audits({
    @Audit(action = "SECURITY"),
    @Audit(action = "COMPLIANCE")
})
```

Annotation chứa không phải chi tiết có thể bỏ qua hoàn toàn, vì API Reflection mức thấp và API tra cứu “theo type” có ngữ nghĩa khác nhau.

Các giá trị lặp giữ thứ tự trong mã nguồn theo quy tắc biểu diễn của Annotation chứa. Không nên trộn tùy tiện Annotation chứa viết tường minh với các Annotation lặp ở cùng ngữ cảnh; ngoài việc khó đọc, một số tổ hợp còn tạo lỗi trùng Annotation chứa khi biên dịch.

## <a id="get-annotations-by-type">getAnnotationsByType()</a>

> **Ranh giới cho người mới:** bạn cần hiểu vì sao API `...ByType(...)` tồn tại và cách `@Inherited` ảnh hưởng kết quả. Không cần học toàn bộ Reflection API; module `reflection` sẽ sở hữu phần đó.

Trước khi nhìn API, cần phân biệt bốn khái niệm tra cứu mà Java dùng:

```text
directly present (xuất hiện trực tiếp)
→ Annotation T được gắn trực tiếp lên phần tử

indirectly present (xuất hiện gián tiếp)
→ Annotation T nằm bên trong Annotation chứa của một Annotation lặp lại

present (được xem là hiện diện)
→ directly present
→ hoặc, với class + @Inherited, được tìm thấy qua superclass theo quy tắc của Java

associated (được liên kết với phần tử khi tra cứu theo type)
→ directly present hoặc indirectly present
→ hoặc, với class + @Inherited, tìm ngược lên superclass khi phần tử hiện tại không có associated annotation của T
```

Từ đó các API quen thuộc có thể đọc như sau:

| API | Annotation chứa của dạng lặp | Tra cứu superclass với `@Inherited` |
| --- | --- | --- |
| `getDeclaredAnnotation(T)` | không mở Annotation chứa | không |
| `getAnnotation(T)` | không mở Annotation chứa | có với class khi `T` là `@Inherited` |
| `getDeclaredAnnotationsByType(T)` | có | không |
| `getAnnotationsByType(T)` | có | có với class khi `T` là `@Inherited` |
| `isAnnotationPresent(T)` | cùng ngữ nghĩa tra cứu một Annotation như `getAnnotation(T) != null` | tương ứng |

Đây là lý do không nên dùng `getAnnotation(T)` rồi mong nó “tự hiểu” mọi giá trị lặp.

Khi làm việc với Annotation lặp lại, API phù hợp thường là:

```java
Method method = PaymentService.class.getDeclaredMethod("transfer");

Audit[] audits = method.getAnnotationsByType(Audit.class);
```

`getAnnotationsByType(Audit.class)` hiểu ngữ nghĩa Annotation lặp lại/Annotation chứa và trả về các `Audit` riêng lẻ.

Ngược lại:

```java
Audit audit = method.getAnnotation(Audit.class);
```

không phải API tốt để đọc một Annotation lặp lại. Khi chỉ có một `@Audit` trực tiếp, lời gọi có thể trả về Annotation đó. Sau khi thêm Annotation thứ hai, siêu dữ liệu có thể được chứa qua Annotation chứa và `getAnnotation(Audit.class)` không còn cho kết quả như mã nguồn cũ mong đợi.

Đây là một rủi ro tương thích quan trọng:

> Nếu kiểu Annotation có thể lặp lại, thành phần đọc nên dùng API `*AnnotationsByType` thay vì giả định chỉ có một instance.

### Tra cứu khai báo trực tiếp và tra cứu kế thừa

`getDeclaredAnnotationsByType(T)`:

- chỉ xem siêu dữ liệu khai báo tại phần tử hiện tại;
- mở Annotation chứa;
- không đi lên superclass.

`getAnnotationsByType(T)` trên `Class`:

- mở Annotation chứa;
- có thể áp dụng ngữ nghĩa `@Inherited`;
- chỉ tìm ngược lên superclass khi class hiện tại không có associated annotation của type đó.

Điều này có nghĩa Annotation vừa lặp lại vừa kế thừa **không được cộng dồn từ mọi superclass**.

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

Tra cứu:

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

Quy tắc cần ghi nhớ:

```text
@Inherited
→ chuỗi superclass của khai báo class
→ không phải đồ thị interface
```

## <a id="annotation-inheritance-boundaries">Ranh giới kế thừa của Annotation</a>

Tên `@Inherited` dễ khiến người học suy luận quá rộng. Nó không có nghĩa “siêu dữ liệu được kế thừa giống method”.

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

Method `PaymentService.process()` không tự mang `@Audit(action = "BASE")` chỉ vì nó override method của superclass. Tra cứu Annotation trên method không dùng `@Inherited`.

Tương tự:

- field annotation không tự truyền xuống subclass field;
- constructor hoặc parameter annotation không tự truyền;
- interface annotation không đi sang implementing class qua `@Inherited`;
- tra cứu kế thừa ở cấp class chỉ hoạt động nếu kiểu Annotation có `@Inherited` và siêu dữ liệu còn tồn tại ở thời gian chạy.

### Lặp lại + kế thừa không có nghĩa là gộp toàn bộ

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

Nếu `@Tag` vừa lặp lại vừa có `@Inherited`, tra cứu “theo type” trên `Child` dùng tập Annotation associated tại class hiện tại; nó không mặc định trả `child + base-a + base-b` như một phép gộp toàn bộ hierarchy.

Đây là khác biệt giữa:

```text
kế thừa theo cơ chế tìm ngược
và
cộng dồn toàn bộ chuỗi kế thừa
```

Kế thừa Annotation của Java là cơ chế tra cứu tìm ngược theo quy tắc xác định, không phải cơ chế framework tự gộp cấu hình.

### Ranh giới với module Reflection

Module Annotation cần hiểu các API tra cứu trên để giải thích ngữ nghĩa của `@Repeatable` và `@Inherited`. Các chủ đề sâu hơn như `Class`, `Method`, kiểm soát truy cập và gọi method bằng Reflection thuộc module `reflection`.

Chương tiếp theo chuyển sang một thành phần đọc hoàn toàn khác: **bộ xử lý Annotation chạy khi biên dịch**, trước khi ứng dụng được khởi động.
