# String Concatenation

Toán tử `+` làm việc rất tiện với String, nhưng cần phân biệt **ngữ nghĩa ngôn ngữ** với **chi tiết triển khai compiler/runtime**.

## <a id="concat-semantics">Nối String bằng +</a>

Khi một operand là String trong ngữ cảnh concatenation, Java tạo ra String result biểu diễn nội dung đã nối.

```java
String message = "Hello " + name;
```

String cũ không bị mutate; result là một String value mới về mặt ngữ nghĩa.

### String conversion xảy ra trong expression

Concatenation có thể nhận primitive hoặc reference value:

```java
String message = "count=" + 42;       // "count=42"
String flag = "active=" + true;       // "active=true"
Object value = null;
String text = "value=" + value;       // "value=null"
```

Java thực hiện string conversion cho operand phù hợp; application không cần gọi `toString()` thủ công cho mọi phần.

Nhưng điều này cũng có thể che bug: nếu `null` là invalid domain state, hãy validate state thay vì để concatenation biến nó thành text `"null"` rồi đi xa hơn.

Khi biểu thức trộn số và String, evaluation order ảnh hưởng kết quả:

Java evaluate operand từ trái sang phải. Đây là lý do numeric addition có thể xảy ra trước khi expression bước vào String concatenation.

Tránh nhét side effect phức tạp vào cùng expression; tách bước giúp reasoning và debugging rõ hơn.

```java
1 + 2 + "x"   // "3x"
"x" + 1 + 2   // "x12"
```

## <a id="compile-time-concat">Concatenation ở Compile Time</a>

Nếu toàn bộ biểu thức là compile-time constant, compiler có thể gộp ngay:

```java
String value = "ja" + "va";
```

về ngữ nghĩa có thể tương đương literal `"java"` và tham gia constant pool.

Đây là lý do một số demo `==` với concatenated literal cho `true`, nhưng không được suy rộng sang runtime concatenation.

Constant variable cũng có thể tham gia:

```java
final String left = "ja";
String a = left + "va";
String b = "java";

System.out.println(a == b); // true
```

Điểm quyết định là **compile-time constant expression**, không chỉ keyword `final`.

## <a id="runtime-concat">Concatenation ở Runtime</a>

Với runtime values, compiler/JVM có thể dùng các strategy khác nhau tùy Java version, ví dụ builder-like lowering hoặc `invokedynamic` concat machinery.

mã ứng dụng nên phụ thuộc vào **language ngữ nghĩa**, không phụ thuộc vào việc bytecode hiện tại dùng đúng class helper nào.

Vì vậy không nên học rule kiểu:

```text
mọi dấu + với String
→ compiler luôn tạo StringBuilder
```

Đó không phải language contract. Java hiện đại có thể dùng concat strategy khác, và implementation có quyền thay đổi.

## <a id="loop-concat-cost">Chi phí khi nối lặp lại</a>

Trong loop lớn:

```java
String result = "";
for (...) {
    result += part;
}
```

mỗi bước có thể tạo thêm intermediate String/value-copy cost.

Mental model:

```text
""
 + part1 → result 1
 + part2 → result 2 lớn hơn
 + part3 → result 3 lớn hơn
 ...
```

Khi text tăng dần qua nhiều iteration, cùng prefix có thể bị copy nhiều lần. Với workload đủ lớn, tổng copying tăng đáng kể so với dùng một mutable buffer.

Nếu đang xây một chuỗi tăng dần qua nhiều bước, `StringBuilder` thể hiện intent rõ hơn và thường hiệu quả hơn.

Điều đó **không có nghĩa mọi dấu + đều xấu**:

```java
String fullName = firstName + " " + lastName;
```

Một expression nhỏ, rõ intent thường nên giữ đơn giản. Builder có giá trị nhất khi construction là **incremental**, đặc biệt trong loop hoặc branch phức tạp.

chương tiếp theo đi vào chính mutable buffer đó.
