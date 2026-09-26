# Varargs

Varargs cho phép bên gọi truyền số đối số thay đổi mà không phải tự tạo array trong source. Nhưng ở method body, varargs vẫn có mô hình tư duy dựa trên array.

## <a id="varargs-array-model">Varargs là Array</a>

```java
void log(String... messages) {
    System.out.println(messages.length);
}
```

Trong body, `messages` có type `String[]`.

bên gọi có thể viết:

```java
log("a", "b");
```

hoặc truyền một array tương thích.

Varargs parameter phải là parameter cuối cùng của method.

### HOW - Caller được chuyển thành array như thế nào?

```java
log();
log("a");
log("a", "b");
```

Trong mental model của method body, ba call trên tương ứng việc nhận một `String[]` có length 0, 1 và 2. Caller cũng có thể truyền array có sẵn:

```java
String[] messages = {"a", "b"};
log(messages);
```

Varargs chỉ là syntax convenience ở call site; API vẫn nên suy nghĩ về array semantics, allocation và mutability boundary.

### `null` là một case dễ nhầm

```java
log((String[]) null);
```

Method nhận `messages == null`, khác hoàn toàn với `log()` vốn nhận array rỗng. Public API thường nên quyết định rõ có chấp nhận null array hay không.

## <a id="varargs-overload">Varargs và Overload</a>

Varargs thường được xét như fallback sau các fixed-arity candidate phù hợp.

Do đó thêm một varargs overload có thể ảnh hưởng overload set theo cách không hiển nhiên, đặc biệt khi kết hợp boxing, widening hoặc `null`.

API public có nhiều overload + varargs nên được test với representative call sites để tránh ambiguity ngoài ý muốn.

Ví dụ fixed arity thường thắng varargs:

```java
void send(String value) { }
void send(String... values) { }

send("one"); // fixed-arity overload
```

Nhưng `null` có thể làm overload set khó đọc vì `null` tương thích nhiều reference/array type. Đừng thêm overload chỉ để "tiện" nếu nó làm call-site semantics mơ hồ.

## <a id="varargs-generics-warning">Generic Varargs và Heap Pollution</a>

Varargs được triển khai qua array, còn generic type parameter bị type erasure. Kết hợp hai cơ chế có thể tạo **heap pollution** warning với non-reifiable type:

```java
static <T> void addAll(List<T>... lists) { }
```

`@SafeVarargs` chỉ nên dùng khi cách triển khai thực sự không thực hiện thao tác unsafe trên varargs array; annotation là một lời cam kết, không phải nút tắt warning tùy ý.

### Vì sao generic varargs nguy hiểm?

Array biết runtime component type, còn generic parameter thường bị type erasure. Với `List<String>...`, runtime không thể có một array thực sự biết đầy đủ parameterization `List<String>` theo cách source type thể hiện.

Unsafe write qua một alias array có thể làm compiler tin một element là `List<String>` trong khi runtime structure chứa parameterization khác. Đó là một dạng **heap pollution**.

Practical rule:

- ưu tiên API collection khi số lượng phần tử biến đổi là data structure thật sự;
- dùng varargs khi call-site ergonomics có giá trị rõ;
- với generic varargs, không ghi unsafe vào array và không expose array cho code có thể mutate sai kiểu;
- chỉ dùng `@SafeVarargs` khi bạn có thể giải thích vì sao implementation thực sự safe.

chương tiếp theo giải thích chính xác **thứ gì được copy khi đối số đi vào method**.
