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

### WHY - Vì sao wrapper tồn tại?

Nhiều API Java cần **reference type**, trong khi primitive không phải object. Generic type parameter là ví dụ quen thuộc:

```java
List<Integer> numbers = new ArrayList<>();
// List<int> không hợp lệ
```

Wrapper còn cung cấp utility API như parse, conversion và constants:

```java
int value = Integer.parseInt("42");
Integer boxed = Integer.valueOf(42);
```

Khi API không cần nullable/object semantics, primitive thường đơn giản hơn và tránh boxing allocation/overhead không cần thiết.

## <a id="boxing-unboxing">Boxing và Unboxing</a>

**Boxing** chuyển primitive value sang wrapper; **unboxing** lấy primitive value từ wrapper.

```java
Integer boxed = 10; // autoboxing
int value = boxed;  // unboxing
```

Compiler chèn chuyển đổi tương ứng trong ngữ cảnh hợp lệ.

Autoboxing tiện nhưng không làm primitive và wrapper trở thành cùng một type. Overload resolution, `null`, identity và performance vẫn có thể khác.

### Boxing có thể xảy ra ở đâu?

```java
Integer a = 10;          // assignment context
List<Integer> xs = List.of(1, 2, 3); // primitive arguments được box

void accept(Integer x) { }
accept(10);              // method invocation context
```

Unboxing thường xuất hiện khi wrapper được dùng trong primitive expression:

```java
Integer count = 10;
int next = count + 1; // count được unbox trước arithmetic
```

Vì conversion có thể được compiler chèn ngầm, hãy đặc biệt cẩn thận ở boundary có `null`, overload hoặc hot path nhiều iteration.

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

Đặc biệt, với `Integer`, Java bảo đảm cache ít nhất cho khoảng `-128..127` khi boxing theo các API/quy tắc chuẩn liên quan. Tuy nhiên code business không nên phụ thuộc vào identity cache:

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b);      // thường true theo cache bắt buộc
System.out.println(a.equals(b)); // true theo value
```

Mental model đúng là: wrapper là object có value semantics; `==` trên hai reference kiểm tra identity, không phải numeric equality.

## <a id="unboxing-null">Unboxing null</a>

Wrapper có thể là `null`:

```java
Integer boxed = null;
int value = boxed; // NullPointerException
```

Unboxing cần một object wrapper thực sự để lấy primitive value. Vì vậy API dùng nullable wrapper có thêm một lỗi mode mà primitive không có.

Pitfall này có thể ẩn trong biểu thức tưởng như vô hại:

```java
Integer count = null;

// cả ba đều có thể trigger unboxing
int x = count;
int y = count + 1;
if (count > 0) { }
```

Nếu `null` mang ý nghĩa business hợp lệ, code nên xử lý absence trước khi unbox. Nếu `null` không hợp lệ, validate sớm tại boundary thay vì để NPE xuất hiện xa nguồn.

chương tiếp theo xem các toán tử kết hợp value và những chuyển đổi ngầm có thể xảy ra trong biểu thức.
