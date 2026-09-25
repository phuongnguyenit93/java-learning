# Wrapper, Boxing và Unboxing

## <a id="wrapper-types">Wrapper type và object semantics</a>
Mỗi primitive type có wrapper class tương ứng như `Integer`, `Long`, `Double`, `Boolean`. Wrapper là immutable object nên có thể là `null`, dùng trong generics/collection, có identity và cung cấp helper parse/convert.

## <a id="boxing-unboxing">Boxing và unboxing</a>
Autoboxing chuyển primitive sang wrapper khi language rule cho phép; unboxing lấy primitive value từ wrapper. Compiler chèn các conversion này và chúng còn tham gia overload resolution.

```java
Integer boxed = 42;
int n = boxed;
```

Syntax tiện lợi không làm mất khác biệt semantic giữa primitive và object.

## <a id="wrapper-caching">Wrapper cache và bẫy identity</a>
Một số wrapper factory/autoboxing reuse cached object cho các range bắt buộc/phổ biến. Vì vậy boxed value đôi khi có cùng identity và đôi khi không. Không dùng `==` để so sánh numeric value của wrapper; dùng `.equals()` hoặc chủ động unbox.

## <a id="unboxing-null">Unboxing null và NullPointerException</a>
Unboxing cần wrapper object thực. Nếu reference là `null`, unboxing ném `NullPointerException`. Lỗi này có thể xuất hiện gián tiếp trong arithmetic, comparison, ternary expression hoặc API trộn primitive/wrapper.
