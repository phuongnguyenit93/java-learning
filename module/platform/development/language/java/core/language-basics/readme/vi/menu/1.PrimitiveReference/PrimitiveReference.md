# Kiểu nguyên thủy và kiểu tham chiếu

## <a id="primitive-vs-reference-model">Mô hình giá trị primitive và reference</a>
Biến trong Java luôn chứa **giá trị**. Biến primitive chứa trực tiếp giá trị như `int`, `double`, `char`, `boolean`. Biến reference chứa một giá trị tham chiếu có thể nhận diện object/array, hoặc nhận `null`.

Copy primitive là copy chính giá trị. Copy reference là copy giá trị tham chiếu, vì vậy hai biến có thể cùng trỏ tới một mutable object. Mental model này là nền tảng cho pass-by-value, aliasing, object identity, defensive copy và nullability.

```java
int a = 10;
int b = a;          // primitive value độc lập
List<String> x = new ArrayList<>();
List<String> y = x; // copy reference, cùng một object
```

## <a id="primitive-ranges-and-defaults">Phạm vi, literal và giá trị mặc định của primitive</a>
Java có tám primitive types. Các integer type có độ rộng cố định; floating-point theo IEEE 754; `char` là một UTF-16 code unit không dấu; `boolean` biểu diễn giá trị logic. Field có default value theo ngôn ngữ, còn local variable phải được definite assignment trước khi đọc.

Numeric literal cũng có type ở compile time. Suffix như `L`, `F`, `D`, radix prefix, dấu `_` và constant expression có thể ảnh hưởng việc assignment có compile hay không.

## <a id="reference-value-semantics">Reference value thực sự biểu diễn gì</a>
Java không yêu cầu programmer xem reference như một địa chỉ bộ nhớ vật lý. Mental model cần dùng là reference có thể nhận diện object, được copy, so sánh identity bằng `==`, được dereference hoặc mang `null`.

Một object có thể có nhiều alias và lifetime của nó không gắn với một biến cụ thể. Mất một reference không đồng nghĩa object bị hủy nếu vẫn còn reference reachable khác. Reachability/GC thuộc JVM; code Java thông thường nên suy luận bằng reference và identity thay vì địa chỉ bộ nhớ.
