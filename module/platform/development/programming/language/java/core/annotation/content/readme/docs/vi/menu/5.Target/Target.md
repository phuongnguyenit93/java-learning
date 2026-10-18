# Phạm vi áp dụng và vị trí sử dụng Annotation

`@Retention` trả lời **siêu dữ liệu sống bao lâu**. `@Target` trả lời một câu hỏi độc lập:

> Annotation được phép xuất hiện ở những ngữ cảnh nào?

Giới hạn bằng `@Target` không chỉ giúp mã nguồn rõ ràng hơn. Nó biến một phần **quy tắc ngữ nghĩa** thành điều trình biên dịch có thể kiểm tra.

Nếu `@Audit` chỉ có nghĩa trên method, việc cho phép đặt nó lên field hoặc package sẽ tạo siêu dữ liệu vô nghĩa.

## <a id="annotation-use-sites">Khai báo và vị trí sử dụng type</a>

Một khác biệt quan trọng là Annotation có thể mô tả **phần tử được khai báo** hoặc **type đang được sử dụng**.

Annotation trên phần khai báo gắn siêu dữ liệu vào chính phần tử được khai báo:

```java
@ComponentInfo
class PaymentService {

    @Audit(action = "PAYMENT")
    void pay(@RequestId String request) {
    }
}
```

Mỗi kiểu Annotation có thể có phạm vi áp dụng khác nhau: `@ComponentInfo` cho class, `@Audit` cho method, `@RequestId` cho parameter, hoặc nhiều ngữ cảnh nếu quy tắc cho phép.

Type-use annotation gắn siêu dữ liệu vào **một lần sử dụng type**:

```java
List<@NonNull String> names;

@NonNull String loadName() {
    return "Java";
}

String @NonNull [] values;
```

Đây là nền tảng để bộ kiểm tra kiểu hoặc công cụ phân tích tĩnh có thể diễn đạt các thuộc tính như nullness trên chính vị trí sử dụng type thay vì chỉ trên phần khai báo.

Mô hình tư duy:

```text
Annotation trên khai báo
→ mô tả phần tử được khai báo

TYPE_USE Annotation
→ mô tả type ở vị trí nó đang được sử dụng
```

Nếu một Annotation được cấu hình để cùng một vị trí trong mã nguồn hợp lệ ở cả ngữ cảnh khai báo và ngữ cảnh sử dụng type, vị trí đó có thể mang cả hai vai trò theo quy tắc của Java. Vì vậy Reflection tại thời gian chạy tách hai hướng quan sát: Annotation trên khai báo thường đi qua `AnnotatedElement`, còn type annotation đi qua họ `AnnotatedType`.

`@Target` là cơ chế quyết định những vị trí nào trong số đó thực sự hợp lệ cho từng kiểu Annotation.

## <a id="elementtype-targets">Các ElementType</a>

`@Target` nhận một hoặc nhiều giá trị từ enum `ElementType`:

```java
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface FeatureFlag {
    String value();
}
```

Trong Java 21, các giá trị `ElementType` chính gồm:

| `ElementType` | Ngữ cảnh điển hình |
| --- | --- |
| `TYPE` | class, interface, enum, record, annotation interface |
| `FIELD` | field, enum constant |
| `METHOD` | method |
| `PARAMETER` | formal parameter |
| `CONSTRUCTOR` | constructor |
| `LOCAL_VARIABLE` | biến cục bộ |
| `ANNOTATION_TYPE` | khai báo annotation interface |
| `PACKAGE` | khai báo package |
| `MODULE` | khai báo module |
| `TYPE_PARAMETER` | khai báo type parameter như `<T>` |
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
    // String status; // lỗi khi biên dịch
}
```

### `PACKAGE` và `MODULE` nằm ở đâu trong mã nguồn?

Hai loại vị trí này dễ gây khó hiểu vì Annotation không được đặt trong một class thông thường.

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

Chúng vẫn tuân theo cùng mô hình tư duy: Annotation đang mô tả **khai báo package** hoặc **khai báo module**, và chỉ có ý nghĩa khi có thành phần đọc hiểu siêu dữ liệu đó.

### Khi không khai báo `@Target`

Không có `@Target` **không có nghĩa là “mọi vị trí kể cả TYPE_USE”**. Theo Java 21, Annotation đó áp dụng cho **tất cả ngữ cảnh khai báo**, bao gồm cả type parameter và record component, nhưng **không áp dụng cho ngữ cảnh sử dụng type**. Muốn gắn Annotation lên một lần sử dụng type thì phải cho phép `TYPE_USE`.

Nếu Annotation chỉ là kiểu phụ trợ bên trong một Annotation khác và không muốn được dùng trực tiếp, có thể viết:

```java
@Target({})
public @interface InternalMember {
    String value();
}
```

`@Target({})` làm Annotation không có vị trí gắn trực tiếp hợp lệ.

## <a id="type-use-annotation">TYPE_USE</a>

`TYPE_USE` mở rộng câu hỏi từ “Annotation gắn lên **khai báo nào?**” sang “Annotation mô tả **type nào ở vị trí sử dụng này?**”.

Ví dụ:

```java
List<@NonNull String> names;

@NonNull String findName() {
    return "Java";
}

Object value = (@NonNull String) input;

List<@Readonly String> copy = new ArrayList<>();
```

Type annotation có thể xuất hiện trong nhiều ngữ cảnh sử dụng type như:

- generic type argument;
- return type, receiver type hoặc một lần xuất hiện của type;
- phép ép kiểu;
- `new` expression;
- thành phần/chiều của mảng;
- type trong `throws` hoặc `implements` tùy phạm vi áp dụng của Annotation.

`TYPE_PARAMETER` khác `TYPE_USE`:

```java
class Box<@TypeParameterRule T> {
    List<@TypeUseRule T> values;
}
```

Annotation đầu mô tả **khai báo của `T`**; Annotation sau mô tả một **lần sử dụng type `T`**.

Theo mô hình ngôn ngữ Java, `TYPE_USE` còn bao phủ một số ngữ cảnh khai báo liên quan tới type như khai báo type/type parameter để thuận tiện cho công cụ kiểm tra kiểu. Vì vậy cần đọc quy tắc cụ thể thay vì suy luận chỉ từ vị trí ký tự `@`.

### Kiểm tra type annotation tại thời gian chạy

> **Chỉ cần nhận diện ở module này:** Reflection có API riêng để quan sát type annotation. Không cần học sâu `AnnotatedType` tại đây; phần Reflection chi tiết thuộc module `reflection`.

Nếu type annotation có chính sách `RUNTIME`, mã chạy ở thời gian chạy thường quan sát nó qua `AnnotatedType` và các subtype như `AnnotatedParameterizedType`, không chỉ qua API Annotation trên khai báo thông thường.

Chi tiết Reflection sâu hơn thuộc module `reflection`; ở đây điểm cần nhớ là **siêu dữ liệu trên khai báo và siêu dữ liệu TYPE_USE có mô hình tra cứu khác nhau**.

### Trường hợp đặc biệt — khai báo cục bộ và type annotation là hai kênh khác nhau

> **Edge case để tránh suy luận sai:** không cần ghi nhớ class-file representation chi tiết; chỉ cần biết cùng xuất hiện gần một biến cục bộ nhưng Annotation trên khai báo và Annotation trên type không phải cùng một kênh siêu dữ liệu.

Annotation trên **khai báo biến cục bộ** và formal parameter của lambda không được lưu trong binary theo cơ chế dành cho Annotation trên khai báo, kể cả khi kiểu Annotation khai báo `CLASS` hoặc `RUNTIME`.

Trong khi đó, type annotation đặt lên **type được sử dụng** ở các ngữ cảnh tương ứng đi qua kênh siêu dữ liệu dành cho type annotation và có quy tắc lưu trữ riêng.

Vì vậy `RUNTIME` không có nghĩa mọi ký hiệu `@...` trong mã nguồn đều trở thành Annotation trên khai báo có thể đọc lúc chạy. Phải luôn đọc **`@Retention` cùng với `@Target` và vị trí sử dụng thực tế**.

## <a id="target-design">Thiết kế @Target đúng ngữ nghĩa</a>

`@Target` là một phần của quy tắc công khai của Annotation. Hãy chọn **tập ngữ cảnh nhỏ nhất vẫn diễn đạt đúng ý nghĩa**.

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

nếu thành phần đọc chỉ xử lý method.

Lợi ích:

- trình biên dịch chặn vị trí sử dụng vô nghĩa;
- IDE gợi ý và hiển thị ngữ cảnh rõ hơn;
- thành phần đọc không phải xử lý nhiều trường hợp không cần thiết;
- quy tắc của Annotation dễ đọc và dễ tiến hóa.

### Nhiều target làm mở rộng bề mặt API

Khi thêm `FIELD` hoặc `TYPE` vào một Annotation đã public, bạn đang mở rộng nơi siêu dữ liệu có thể xuất hiện. Thành phần đọc cần có ngữ nghĩa rõ cho những ngữ cảnh mới đó.

Đặc biệt với `RECORD_COMPONENT`, Java có các quy tắc ánh xạ record component sang component field, accessor và parameter của canonical constructor. Annotation trên record component có thể được truyền sang component field tương ứng và sang accessor/canonical-constructor parameter **được khai báo ngầm** khi kiểu Annotation cũng áp dụng được ở ngữ cảnh tương ứng. Nó không tự được sao chép sang accessor do lập trình viên khai báo tường minh; với canonical constructor khai báo tường minh, Annotation trên formal parameter có thể khác Annotation trên record component. Vì vậy không nên giả định “đặt một lần thì tự xuất hiện ở mọi nơi”.

### Chuyển sang Meta-Annotation

`@Target` và `@Retention` có một điểm thú vị: chúng cũng chính là annotation, nhưng được dùng để **cấu hình annotation type khác**. Những annotation như vậy được gọi là meta-annotations.
