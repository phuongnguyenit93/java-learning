# String Concatenation

Toán tử `+` làm việc rất tiện với String, nhưng cần phân biệt **ngữ nghĩa ngôn ngữ** với **chi tiết triển khai compiler/runtime**.

## <a id="concat-semantics">Nối String bằng +</a>

Khi một operand là String trong ngữ cảnh concatenation, Java tạo ra String result biểu diễn nội dung đã nối.

```java
String message = "Hello " + name;
```

String cũ không bị mutate; result là một String value mới về mặt ngữ nghĩa.

Khi biểu thức trộn số và String, evaluation order ảnh hưởng kết quả:

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

## <a id="runtime-concat">Concatenation ở Runtime</a>

Với runtime values, compiler/JVM có thể dùng các strategy khác nhau tùy Java version, ví dụ builder-like lowering hoặc `invokedynamic` concat machinery.

mã ứng dụng nên phụ thuộc vào **language ngữ nghĩa**, không phụ thuộc vào việc bytecode hiện tại dùng đúng class helper nào.

## <a id="loop-concat-cost">Chi phí khi nối lặp lại</a>

Trong loop lớn:

```java
String result = "";
for (...) {
    result += part;
}
```

mỗi bước có thể tạo thêm intermediate String/value-copy cost.

Nếu đang xây một chuỗi tăng dần qua nhiều bước, `StringBuilder` thể hiện intent rõ hơn và thường hiệu quả hơn.

chương tiếp theo đi vào chính mutable buffer đó.
