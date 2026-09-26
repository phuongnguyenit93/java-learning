# Wrapper và Boxing

Primitive không phải object. Nhưng nhiều Java API, đặc biệt generic collection, làm việc với reference type. Wrapper type tạo cầu nối như `int ↔ Integer`, `double ↔ Double`.

## <a id="wrapper-types">Wrapper Type</a>

Mỗi primitive có wrapper tương ứng:

```text
byte    ↔ Byte
short   ↔ Short
int     ↔ Integer
long    ↔ Long
float   ↔ Float
double  ↔ Double
char    ↔ Character
boolean ↔ Boolean
```

Wrapper là object immutable với value ngữ nghĩa riêng. Vì là reference type, wrapper có thể là `null`, tham gia generic API và có identity khác với primitive value.

## <a id="boxing-unboxing">Boxing và Unboxing</a>

**Boxing** chuyển primitive value sang wrapper; **unboxing** lấy primitive value từ wrapper.

```java
Integer boxed = 10; // autoboxing
int value = boxed;  // unboxing
```

Compiler chèn chuyển đổi tương ứng trong ngữ cảnh hợp lệ.

Autoboxing tiện nhưng không làm primitive và wrapper trở thành cùng một type. Overload resolution, `null`, identity và performance vẫn có thể khác.

## <a id="wrapper-caching">Wrapper Cache</a>

Một số wrapper value có thể được cache, khiến demo dùng `==` đôi khi cho kết quả bất ngờ:

```java
Integer a = 100;
Integer b = 100;
a == b // có thể true do cache
```

nhưng với value khác:

```java
Integer x = 1000;
Integer y = 1000;
x == y // không nên dựa vào kết quả identity
```

Khi muốn so wrapper value, dùng `equals` hoặc unbox theo hợp đồng phù hợp; không dựa vào cache identity.

## <a id="unboxing-null">Unboxing null</a>

Wrapper có thể là `null`:

```java
Integer boxed = null;
int value = boxed; // NullPointerException
```

Unboxing cần một object wrapper thực sự để lấy primitive value. Vì vậy API dùng nullable wrapper có thêm một lỗi mode mà primitive không có.

chương tiếp theo xem các toán tử kết hợp value và những chuyển đổi ngầm có thể xảy ra trong biểu thức.
