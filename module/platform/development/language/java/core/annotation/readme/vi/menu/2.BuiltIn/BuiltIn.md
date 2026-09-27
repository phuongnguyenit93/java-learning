# Built-in Annotation

Java cung cấp nhiều annotation chuẩn. Chapter này tập trung vào năm annotation thường xuyên giúp compiler kiểm tra **ý định của developer**: `@Override`, `@Deprecated`, `@SuppressWarnings`, `@SafeVarargs` và `@FunctionalInterface`.

Điểm chung không phải là chúng “chạy cùng một cơ chế”. Mỗi annotation có contract riêng; có annotation chủ yếu tồn tại để compiler kiểm tra, có annotation còn được tool/runtime nhìn thấy.

## <a id="override-annotation">@Override và contract override</a>

Trong trường hợp phổ biến nhất, `@Override` nói với compiler:

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

Giá trị lớn nhất của `@Override` xuất hiện khi code thay đổi hoặc có typo:

```java
class Child extends Parent {
    @Override
    void proccess() { // compile-time error
    }
}
```

Không có `@Override`, method bị viết sai tên vẫn có thể trở thành một method mới hoàn toàn hợp lệ. Với annotation, ý định trở thành một **compiler-checked contract**.

### Điều `@Override` không làm

Nó không tạo ra polymorphism và không biến một method thành virtual. Java overriding semantics tồn tại độc lập; annotation chỉ yêu cầu compiler xác nhận declaration đáp ứng một trường hợp mà `@Override` được Java cho phép. Ngoài override/implement method của supertype, Java 21 còn cho phép `@Override` trên accessor method được khai báo tường minh cho một record component.

Vì vậy nên dùng `@Override` cho method override/implement bất cứ khi nào phù hợp. Nó vừa là documentation vừa là guardrail.

## <a id="deprecated-annotation">@Deprecated và lifecycle API</a>

`@Deprecated` đánh dấu program element mà developer được khuyến nghị **không tiếp tục sử dụng**.

```java
@Deprecated(since = "2.0", forRemoval = true)
public void oldCheckout() {
}
```

Từ Java 9, annotation có hai element quan trọng:

- `since`: phiên bản mà API bị deprecate;
- `forRemoval`: cho biết API được dự kiến loại bỏ trong tương lai hay không.

Khi code sử dụng API deprecated, compiler/tool có thể phát warning. Tuy nhiên deprecation **không tự xóa API** và cũng không đảm bảo API sẽ biến mất ở một phiên bản cụ thể.

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

`@Deprecated` là machine-readable annotation. Javadoc tag giải thích migration path cho con người. API public nên thường có cả hai khi cần tài liệu chuyển đổi rõ ràng.

## <a id="suppresswarnings">@SuppressWarnings có trách nhiệm</a>

`@SuppressWarnings` yêu cầu compiler bỏ qua những nhóm cảnh báo cụ thể trong phạm vi được annotate:

```java
@SuppressWarnings("unchecked")
List<String> cast(Object value) {
    return (List<String>) value;
}
```

Điểm quan trọng là việc suppress **không làm thao tác trở nên type-safe**. Nó chỉ nói rằng developer đã xem cảnh báo và chấp nhận rủi ro tại vị trí đó.

Thực hành tốt:

```text
cảnh báo xuất hiện
→ hiểu nguyên nhân
→ sửa code nếu có thể
→ chỉ suppress khi contract thực sự đảm bảo an toàn
→ đặt suppression ở phạm vi nhỏ nhất
```

Không nên đặt:

```java
@SuppressWarnings("all")
class EntireModule {
}
```

chỉ để “build sạch cảnh báo”. Cách này che luôn cảnh báo mới không liên quan.

Java Language Specification yêu cầu compiler nhận biết ít nhất bốn suppression key chuẩn:

- `"unchecked"`;
- `"deprecation"`;
- `"removal"`;
- `"preview"`.

Compiler có thể hỗ trợ thêm key riêng, ví dụ `javac` có những lint category như `"rawtypes"`. Vì vậy không nên coi mọi chuỗi warning key đều là một tập chuẩn cố định do `@SuppressWarnings` tự định nghĩa.

## <a id="safevarargs">@SafeVarargs và generic varargs</a>

Varargs của generic/non-reifiable type có thể tạo **heap pollution** vì varargs được biểu diễn bằng array ở runtime trong khi generic type information có thể bị erasure.

Ví dụ:

```java
@SafeVarargs
static <T> List<T> combine(List<T>... lists) {
    return Arrays.stream(lists)
        .flatMap(List::stream)
        .toList();
}
```

`@SafeVarargs` là lời **khẳng định của programmer** rằng phần triển khai không thực hiện thao tác không an toàn trên varargs parameter.

Nó không:

- làm mọi thao tác generic varargs tự động an toàn;
- ngăn một phần triển khai xấu gây heap pollution;
- thay thế việc review code.

Annotation chỉ hợp lệ trên **variable-arity constructor/method** và, với method, phải là `static`, `final` hoặc `private` instance method để lời khẳng định không bị một phần triển khai override phá vỡ.

Nếu method ghi một object có type không tương thích vào backing array, hoặc làm array thoát ra nơi có thể bị sửa sai type, gắn `@SafeVarargs` chỉ đang **che warning sai cách**.

### Khác gì với `@SuppressWarnings("unchecked")`?

Đây là một khác biệt quan trọng:

```text
@SuppressWarnings("unchecked")
→ suppression mang tính cục bộ trong phạm vi declaration

@SafeVarargs
→ suppress warning liên quan non-reifiable varargs ở declaration
→ đồng thời suppress warning tương ứng tại nơi gọi
```

Vì vậy `@SafeVarargs` là một phần của **API contract với bên gọi**, không chỉ là “tắt warning bên trong method”.

## <a id="functionalinterface">@FunctionalInterface và SAM contract</a>

Functional interface là interface có đúng một **abstract method** theo định nghĩa của Java, nên có thể là target type của lambda/method reference.

```java
@FunctionalInterface
interface PriceRule {
    long apply(long amount);
}
```

Annotation yêu cầu compiler xác nhận interface vẫn đáp ứng functional-interface contract:

```java
@FunctionalInterface
interface BrokenRule {
    long apply(long amount);
    boolean enabled(); // compile-time error
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

Các `private` interface methods cũng không trở thành function descriptor. Ngoài ra, abstract methods tương ứng với public methods của `Object` không được tính như một SAM độc lập; những abstract declarations override-equivalent có thể cùng biểu diễn một function descriptor theo rule của Java.

### Annotation không bắt buộc để dùng lambda

Một interface thỏa functional-interface rules vẫn có thể dùng với lambda dù không có `@FunctionalInterface`. Annotation hữu ích vì nó biến **ý định thiết kế API** thành contract compiler kiểm tra được khi interface tiến hóa.

### Chuyển sang custom annotation

Built-in annotations cho thấy pattern quan trọng:

```text
metadata contract
+ consumer hiểu contract
= giá trị thực tế
```

Tiếp theo ta sẽ tự định nghĩa một metadata vocabulary bằng custom annotation thay vì chỉ sử dụng contract Java cung cấp sẵn.
