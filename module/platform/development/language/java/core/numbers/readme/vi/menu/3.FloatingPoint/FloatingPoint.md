# Floating-point

`float` và `double` được thiết kế cho phạm vi lớn và tính toán số thực hiệu quả. Đổi lại, chúng dùng **binary floating-point**, nên nhiều giá trị thập phân quen thuộc không có biểu diễn nhị phân hữu hạn.

## <a id="binary-floating-point">Biểu diễn Binary Floating-point</a>

Một `double` không lưu chuỗi decimal mà người dùng nhập. Nó lưu một giá trị theo mô hình IEEE 754 gồm sign, exponent và significand.

Vì `0.1` trong hệ thập phân là một phân số lặp trong hệ nhị phân, giá trị lưu thực tế chỉ là số gần nhất mà `double` biểu diễn được.

```java
double x = 0.1 + 0.2;
System.out.println(x); // thường thấy 0.30000000000000004
```

Đây không phải bug của Java mà là đặc tính của cách biểu diễn.

## <a id="precision-rounding-error">Sai số Precision và Rounding</a>

Mỗi arithmetic thao tác có thể phải làm tròn về representable floating-point value gần nhất. Sai số nhỏ có thể tích lũy qua nhiều phép tính.

Floating-point phù hợp với nhiều bài toán scientific/sensor/graphics, nơi sai số có tolerance rõ ràng. Nó thường không phù hợp khi domain yêu cầu decimal ngữ nghĩa chính xác như money hoặc tax calculation.

## <a id="nan-infinity-negative-zero">NaN, Infinity và Negative Zero</a>

IEEE 754 còn có các special value:

```text
NaN
→ kết quả “not a number”, ví dụ 0.0 / 0.0

Infinity / -Infinity
→ overflow hoặc division by zero trong floating-point context

+0.0 / -0.0
→ hai zero có sign khác nhau ở representation
```

`NaN` đặc biệt ở chỗ so sánh thông thường có ngữ nghĩa khác trực giác: `NaN == NaN` là `false`.

## <a id="floating-point-comparison">So sánh Floating-point</a>

So sánh `==` chỉ đúng khi domain thật sự yêu cầu **exact represented value equality**.

Với computation gần đúng, thường cần tolerance phù hợp với domain:

```java
Math.abs(actual - expected) <= epsilon
```

Nhưng `epsilon` không phải một hằng số thần kỳ dùng cho mọi bài toán. Nó phải phù hợp với scale, magnitude và error tolerance của domain.

Nếu bài toán yêu cầu số nguyên không giới hạn phạm vi, chương tiếp theo dùng `BigInteger`. Nếu yêu cầu decimal chính xác, ta sẽ tới `BigDecimal` ngay sau đó.
