# Các Annotation có sẵn và cách trình biên dịch/công cụ sử dụng chúng

Java cung cấp nhiều Annotation chuẩn. Chương này tập trung vào năm Annotation thường xuyên giúp trình biên dịch kiểm tra **ý định của lập trình viên**: `@Override`, `@Deprecated`, `@SuppressWarnings`, `@SafeVarargs` và `@FunctionalInterface`.

Điểm chung không phải là chúng “chạy cùng một cơ chế”. Mỗi Annotation có quy tắc riêng; có Annotation chủ yếu tồn tại để trình biên dịch kiểm tra, có Annotation còn được công cụ hoặc mã chạy ở thời gian chạy quan sát được.

## <a id="override-annotation">@Override và quy tắc ghi đè</a>

Trong trường hợp phổ biến nhất, `@Override` nói với trình biên dịch:

> “Method này được viết với ý định override hoặc implement một method từ supertype; hãy báo lỗi nếu điều đó không đúng.”

Ví dụ:

```java
class Parent {
    void process() {
    }
}

class Child extends Parent {
    @Override
    void process() {
    }
}
```

Giá trị lớn nhất của `@Override` xuất hiện khi mã nguồn thay đổi hoặc có lỗi gõ tên:

```java
class Child extends Parent {
    @Override
    void proccess() { // lỗi khi biên dịch
    }
}
```

Không có `@Override`, method bị viết sai tên vẫn có thể trở thành một method mới hoàn toàn hợp lệ. Với Annotation, ý định trở thành một **quy tắc được trình biên dịch kiểm tra**.

### Điều `@Override` không làm

Nó không tạo ra polymorphism và không biến một method thành virtual. Ngữ nghĩa ghi đè của Java tồn tại độc lập; Annotation chỉ yêu cầu trình biên dịch xác nhận phần khai báo đáp ứng một trường hợp mà `@Override` được Java cho phép. Ngoài override/implement method của supertype, Java 21 còn cho phép `@Override` trên accessor method được khai báo tường minh cho một record component.

Vì vậy nên dùng `@Override` cho method override/implement bất cứ khi nào phù hợp. Nó vừa là tài liệu vừa là cơ chế bảo vệ chống sai sót.

## <a id="deprecated-annotation">@Deprecated và vòng đời API</a>

`@Deprecated` đánh dấu phần tử chương trình mà lập trình viên được khuyến nghị **không tiếp tục sử dụng**.

```java
@Deprecated(since = "2.0", forRemoval = true)
public void oldCheckout() {
}
```

Từ Java 9, Annotation có hai phần tử quan trọng:

- `since`: phiên bản mà API bị deprecate;
- `forRemoval`: cho biết API được dự kiến loại bỏ trong tương lai hay không.

Khi mã nguồn sử dụng API đã deprecated, trình biên dịch/công cụ có thể phát cảnh báo. Tuy nhiên deprecation **không tự xóa API** và cũng không đảm bảo API sẽ biến mất ở một phiên bản cụ thể.

### `@Deprecated` và Javadoc `@deprecated`

Hai cơ chế bổ sung cho nhau:

```java
/**
 * @deprecated Use {@link #newCheckout()} instead.
 */
@Deprecated(since = "2.0")
public void oldCheckout() {
}
```

`@Deprecated` là Annotation có cấu trúc để công cụ có thể đọc. Javadoc tag giải thích hướng chuyển đổi cho con người. API public nên thường có cả hai khi cần tài liệu chuyển đổi rõ ràng.

## <a id="suppresswarnings">@SuppressWarnings có trách nhiệm</a>

`@SuppressWarnings` yêu cầu trình biên dịch bỏ qua những nhóm cảnh báo cụ thể trong phạm vi được gắn Annotation:

```java
@SuppressWarnings("unchecked")
List<String> cast(Object value) {
    return (List<String>) value;
}
```

Điểm quan trọng là việc bỏ qua cảnh báo **không làm thao tác trở nên an toàn kiểu**. Nó chỉ nói rằng lập trình viên đã xem cảnh báo và chấp nhận rủi ro tại vị trí đó.

Thực hành tốt:

```text
cảnh báo xuất hiện
→ hiểu nguyên nhân
→ sửa mã nguồn nếu có thể
→ chỉ bỏ qua cảnh báo khi quy tắc thực sự đảm bảo an toàn
→ đặt phạm vi bỏ qua cảnh báo ở mức nhỏ nhất
```

Không nên đặt:

```java
@SuppressWarnings({
    "unchecked",
    "deprecation",
    "removal",
    "preview"
})
class EntireModule {
}
```

chỉ để “quá trình build sạch cảnh báo”. Phạm vi quá rộng như vậy có thể che luôn những cảnh báo mới thuộc các nhóm đã bị bỏ qua, kể cả khi chúng xuất hiện ở phần mã nguồn hoàn toàn khác trong class.

Không nên dùng một chuỗi như `"all"` rồi giả định mọi trình biên dịch Java đều hiểu nó. Tập khóa ngoài các khóa chuẩn phụ thuộc vào trình biên dịch; ví dụ `javac` có các nhóm lint riêng và không coi `"all"` là một khóa `@SuppressWarnings` dùng để bật/tắt toàn bộ cảnh báo.

Java Language Specification yêu cầu trình biên dịch nhận biết ít nhất bốn khóa cảnh báo chuẩn:

- `"unchecked"`;
- `"deprecation"`;
- `"removal"`;
- `"preview"`.

Trình biên dịch có thể hỗ trợ thêm khóa riêng, ví dụ `javac` có những nhóm cảnh báo lint như `"rawtypes"`. Vì vậy không nên coi mọi chuỗi khóa cảnh báo đều là một tập chuẩn cố định do `@SuppressWarnings` tự định nghĩa.

## <a id="safevarargs">@SafeVarargs và varargs với Generics</a>

> **Ranh giới cho người mới:** mục này chỉ cần hiểu `@SafeVarargs` là một lời cam kết về tính an toàn của generic varargs. Cơ chế sâu của type erasure, reifiable type và heap pollution thuộc module Generics.

Varargs của generic/non-reifiable type có thể tạo **heap pollution** vì varargs được biểu diễn bằng mảng ở thời gian chạy trong khi thông tin generic type có thể bị type erasure.

Ví dụ:

```java
@SafeVarargs
static <T> List<T> combine(List<T>... lists) {
    return Arrays.stream(lists)
        .flatMap(List::stream)
        .toList();
}
```

`@SafeVarargs` là lời **khẳng định của lập trình viên** rằng phần triển khai không thực hiện thao tác không an toàn trên varargs parameter.

Nó không:

- làm mọi thao tác generic varargs tự động an toàn;
- ngăn một phần triển khai xấu gây heap pollution;
- thay thế việc rà soát mã nguồn.

Annotation chỉ hợp lệ trên **variable-arity constructor/method** và, với method, phải là `static`, `final` hoặc `private` instance method để lời khẳng định không bị một phần triển khai override phá vỡ.

Nếu method ghi một đối tượng có type không tương thích vào mảng nền (backing array), hoặc làm mảng đó thoát ra nơi có thể bị sửa sai type, gắn `@SafeVarargs` chỉ đang **che cảnh báo sai cách**.

### Khác gì với `@SuppressWarnings("unchecked")`?

Đây là một khác biệt quan trọng:

```text
@SuppressWarnings("unchecked")
→ việc bỏ qua cảnh báo mang tính cục bộ trong phạm vi khai báo

@SafeVarargs
→ bỏ qua cảnh báo liên quan non-reifiable varargs ở phần khai báo
→ đồng thời bỏ qua cảnh báo tương ứng tại nơi gọi
```

Vì vậy `@SafeVarargs` là một phần của **quy tắc API với bên gọi**, không chỉ là “tắt cảnh báo bên trong method”.

## <a id="functionalinterface">@FunctionalInterface và quy tắc SAM</a>

> **Ranh giới cho người mới:** ở module Annotation, mục tiêu là hiểu `@FunctionalInterface` biến ý định thiết kế thành điều trình biên dịch kiểm tra. Lambda, method reference và function type sẽ được đào sâu ở module Functional Programming.

Functional interface là interface có đúng một **abstract method** theo định nghĩa của Java, nên có thể là target type của lambda/method reference.

```java
@FunctionalInterface
interface PriceRule {
    long apply(long amount);
}
```

Annotation yêu cầu trình biên dịch xác nhận interface vẫn đáp ứng quy tắc của functional interface:

```java
@FunctionalInterface
interface BrokenRule {
    long apply(long amount);
    boolean enabled(); // lỗi khi biên dịch
}
```

`default` và `static` methods không tự làm mất tính functional:

```java
@FunctionalInterface
interface PriceRule {
    long apply(long amount);

    default boolean enabled() {
        return true;
    }
}
```

Các `private` interface methods cũng không trở thành function descriptor. Ngoài ra, abstract methods tương ứng với public methods của `Object` không được tính như một SAM độc lập; những abstract declarations tương đương về ghi đè có thể cùng biểu diễn một function descriptor theo quy tắc của Java.

### Annotation không bắt buộc để dùng lambda

Một interface thỏa quy tắc functional interface vẫn có thể dùng với lambda dù không có `@FunctionalInterface`. Annotation hữu ích vì nó biến **ý định thiết kế API** thành quy tắc mà trình biên dịch kiểm tra được khi interface tiến hóa.

### Chuyển sang Annotation tùy chỉnh

Các Annotation có sẵn cho thấy một mô hình quan trọng:

```text
quy tắc siêu dữ liệu
+ thành phần đọc hiểu quy tắc
= giá trị thực tế
```

Tiếp theo ta sẽ tự định nghĩa một cấu trúc siêu dữ liệu bằng Annotation tùy chỉnh thay vì chỉ sử dụng các quy tắc Java cung cấp sẵn.
