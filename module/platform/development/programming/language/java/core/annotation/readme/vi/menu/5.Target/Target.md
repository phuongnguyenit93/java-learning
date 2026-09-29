# Annotation Target

Retention trả lời **metadata sống bao lâu**. Target trả lời một câu hỏi độc lập:

> Annotation được phép xuất hiện ở những context nào?

Giới hạn target không chỉ giúp code đẹp hơn. Nó biến một phần **semantic contract** thành điều compiler có thể enforce.

Nếu `@Audit` chỉ có nghĩa trên method, việc cho phép đặt nó lên field hoặc package sẽ tạo metadata vô nghĩa.

## <a id="elementtype-targets">Các ElementType</a>

`@Target` nhận một hoặc nhiều giá trị từ enum `ElementType`:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Audit {
    String action();
    int level() default 1;
}
```

Trong Java 21, các target chính gồm:

| `ElementType` | Context điển hình |
| --- | --- |
| `TYPE` | class, interface, enum, record, annotation interface |
| `FIELD` | field, enum constant |
| `METHOD` | method |
| `PARAMETER` | formal parameter |
| `CONSTRUCTOR` | constructor |
| `LOCAL_VARIABLE` | local variable |
| `ANNOTATION_TYPE` | annotation interface declaration |
| `PACKAGE` | package declaration |
| `MODULE` | module declaration |
| `TYPE_PARAMETER` | declaration của type parameter như `<T>` |
| `TYPE_USE` | vị trí một type được sử dụng |
| `RECORD_COMPONENT` | record component |

Ví dụ:

```java
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}

class PaymentService {
    @Audit(action = "PAYMENT")
    void pay() {
    }

    // @Audit
    // String status; // compile-time error
}
```

### `PACKAGE` và `MODULE` nằm ở đâu trong code?

Hai target này dễ gây khó hiểu vì annotation không được đặt trong một class thông thường.

Package annotation thường nằm trong `package-info.java`:

```java
@InternalPackage
package com.example.payment;
```

Module annotation nằm trong `module-info.java`:

```java
@InternalModule
module com.example.payment {
    exports com.example.payment.api;
}
```

Chúng vẫn tuân theo cùng mental model: annotation đang mô tả **package declaration** hoặc **module declaration**, và chỉ có ý nghĩa khi có consumer hiểu metadata đó.

### Khi không khai báo `@Target`

Không có `@Target` **không có nghĩa là “mọi vị trí kể cả TYPE_USE”**. Theo Java 21, annotation đó áp dụng cho **tất cả declaration contexts** (bao gồm cả type parameter và record component), nhưng **không áp dụng cho type contexts**. Muốn annotate một lần sử dụng type, contract phải cho phép `TYPE_USE`.

Nếu annotation chỉ là helper type bên trong một annotation khác và không muốn được dùng trực tiếp, có thể viết:

```java
@Target({})
public @interface InternalMember {
    String value();
}
```

Empty target làm annotation không có direct annotation context hợp lệ.

## <a id="type-use-annotation">TYPE_USE</a>

`TYPE_USE` mở rộng annotation từ “declaration nào?” sang “**type nào ở vị trí sử dụng này?**”.

Ví dụ:

```java
List<@NonNull String> names;

@NonNull String findName() {
    return "Java";
}

Object value = (@NonNull String) input;

List<@Readonly String> copy = new ArrayList<>();
```

Type annotation có thể xuất hiện trong nhiều type context như:

- generic type argument;
- return/receiver/type occurrence;
- cast;
- `new` expression;
- array component/dimension context;
- type trong `throws` hoặc `implements` tùy annotation applicability.

`TYPE_PARAMETER` khác `TYPE_USE`:

```java
class Box<@TypeParameterRule T> {
    List<@TypeUseRule T> values;
}
```

Annotation đầu mô tả **declaration của `T`**; annotation sau mô tả một **use của type `T`**.

Theo Java language model, `TYPE_USE` còn bao phủ một số declaration context liên quan type như type declaration/type-parameter declaration để thuận tiện cho type-checker design. Vì vậy cần đọc contract cụ thể thay vì suy luận chỉ từ vị trí ký tự `@`.

### Runtime inspection của type annotation

Nếu type annotation có `RUNTIME` retention, runtime code thường quan sát nó qua `AnnotatedType` và các subtype như `AnnotatedParameterizedType`, không chỉ qua API declaration annotation thông thường.

Chi tiết reflection sâu hơn thuộc module `reflection`; ở đây điểm cần nhớ là **declaration metadata và type-use metadata có mô hình lookup khác nhau**.

## <a id="target-design">Thiết kế target đúng semantic</a>

Target là một phần của public contract. Hãy chọn **tập context nhỏ nhất vẫn diễn đạt đúng ý nghĩa**.

Ví dụ:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    int level() default 1;
}
```

rõ hơn:

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action();
    int level() default 1;
}
```

nếu consumer chỉ xử lý method.

Lợi ích:

- compiler chặn usage vô nghĩa;
- IDE autocomplete/context rõ hơn;
- consumer không phải xử lý nhiều case không cần thiết;
- annotation contract dễ đọc và dễ tiến hóa.

### Multiple targets là API surface

Khi thêm `FIELD` hoặc `TYPE` vào một annotation đã public, bạn đang mở rộng nơi metadata có thể xuất hiện. Consumer cần có semantics rõ cho những context mới đó.

Đặc biệt với `RECORD_COMPONENT`, Java có các rule ánh xạ record component sang field/accessor/constructor parameter. Annotation trên record component chỉ được propagate sang phần tử **được compiler khai báo ngầm** khi annotation target cũng cho phép context tương ứng. Nó không tự được copy sang một accessor được developer khai báo tường minh; với canonical constructor khai báo tường minh, annotation trên formal parameter có thể khác annotation trên record component. Vì vậy không nên giả định “đặt một lần thì tự xuất hiện ở mọi nơi”.

### Chuyển sang meta-annotation

`@Target` và `@Retention` có một điểm thú vị: chúng cũng chính là annotation, nhưng được dùng để **cấu hình annotation type khác**. Những annotation như vậy được gọi là meta-annotations.
