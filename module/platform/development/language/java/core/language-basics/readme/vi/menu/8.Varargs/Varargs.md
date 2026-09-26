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

## <a id="varargs-overload">Varargs và Overload</a>

Varargs thường được xét như fallback sau các fixed-arity candidate phù hợp.

Do đó thêm một varargs overload có thể ảnh hưởng overload set theo cách không hiển nhiên, đặc biệt khi kết hợp boxing, widening hoặc `null`.

API public có nhiều overload + varargs nên được test với representative call sites để tránh ambiguity ngoài ý muốn.

## <a id="varargs-generics-warning">Generic Varargs và Heap Pollution</a>

Varargs được triển khai qua array, còn generic type parameter bị type erasure. Kết hợp hai cơ chế có thể tạo **heap pollution** warning với non-reifiable type:

```java
static <T> void addAll(List<T>... lists) { ... }
```

`@SafeVarargs` chỉ nên dùng khi cách triển khai thực sự không thực hiện thao tác unsafe trên varargs array; annotation là một lời cam kết, không phải nút tắt warning tùy ý.

chương tiếp theo giải thích chính xác **thứ gì được copy khi đối số đi vào method**.
