# Floating-point

`float` và `double` được thiết kế cho **wide range + efficient real-number computation**. Đổi lại, chúng dùng binary floating-point nên nhiều decimal value quen thuộc không có representation nhị phân hữu hạn.

## <a id="binary-floating-point">Biểu diễn Binary Floating-point</a>

### WHAT

Theo IEEE 754, floating-point có thể hình dung bằng ba phần:

```text
sign
 + exponent
 + significand
```

`float` dùng binary32; `double` dùng binary64. Trong Java application code, `double` thường là lựa chọn mặc định khi cần floating-point vì có precision/range tốt hơn `float`.

### WHY 0.1 không “vừa” trong binary?

Một số fraction hữu hạn trong hệ 10 lại lặp vô hạn trong hệ 2.

```text
0.5 decimal
→ 0.1 binary
→ biểu diễn hữu hạn

0.1 decimal
→ binary fraction lặp
→ chỉ lưu được giá trị gần nhất
```

Vì vậy:

```java
double x = 0.1 + 0.2;
System.out.println(x); // 0.30000000000000004
```

Đây không phải bug riêng của Java mà là hệ quả của finite binary floating-point representation.

## <a id="precision-rounding-error">Sai số Precision và Rounding</a>

### HOW sai số xuất hiện?

Với ordinary finite result nằm trong representable range, arithmetic thường phải đưa mathematical result về floating-point value gần nhất theo rounding semantics của IEEE 754/Java.

```text
mathematical exact result
        ↓
nearest representable binary value
        ↓
next operation
        ↓
round again
```

Nhưng không phải mọi result đều chỉ trở thành một finite approximation:

```text
magnitude quá lớn
→ overflow
→ có thể thành +Infinity / -Infinity

magnitude quá nhỏ
→ underflow
→ có thể thành subnormal value hoặc signed zero
```

Sai số nhỏ có thể tích lũy:

```java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total);
```

Floating-point arithmetic cũng không phải lúc nào associative theo trực giác toán học:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

System.out.println((a + b) + c); // có thể khác
System.out.println(a + (b + c)); // với cách nhóm khác
```

### Khi nào phù hợp?

`double` thường phù hợp cho:

- sensor/measurement;
- statistics;
- graphics;
- scientific/engineering computation;
- simulation;
- các domain có tolerance rõ ràng.

Nó thường không phù hợp nếu contract yêu cầu **decimal semantics exact**, ví dụ nhiều workflow money/tax/rate.

## <a id="nan-infinity-negative-zero">NaN, Infinity và Negative Zero</a>

IEEE 754 có special values:

```text
NaN
→ kết quả không phải numerical value thông thường

Infinity / -Infinity
→ overflow hoặc một số division-by-zero case

+0.0 / -0.0
→ hai representation của zero có sign khác nhau
```

Ví dụ:

```java
double nan = 0.0 / 0.0;
double inf = 1.0 / 0.0;
double negInf = -1.0 / 0.0;

System.out.println(Double.isNaN(nan));      // true
System.out.println(Double.isInfinite(inf)); // true
```

### NaN propagation

```java
double result = Double.NaN + 10;
System.out.println(result); // NaN
```

`NaN == NaN` là `false`. Vì vậy khi cần kiểm tra NaN, dùng:

```java
Double.isNaN(value)
```

### Signed zero

```java
System.out.println(0.0 == -0.0); // true
```

nhưng sign vẫn có thể ảnh hưởng arithmetic:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

## <a id="floating-point-comparison">So sánh Floating-point</a>

`==` chỉ nên dùng khi contract thực sự là **exact represented-value equality**.

Với approximate computation, thường cần tolerance:

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double epsilon = 1e-9;

boolean close = Math.abs(actual - expected) <= epsilon;
```

Nhưng một epsilon tuyệt đối không phù hợp cho mọi magnitude:

```text
giá trị quanh 0.000001
và
giá trị quanh 1_000_000_000

→ có thể cần tolerance strategy khác nhau
```

Trong code nghiêm túc, tolerance nên đến từ domain/error budget, không phải “magic epsilon”.

Ngoài ra Java có:

```java
Double.compare(a, b);
Double.isFinite(value);
Double.isNaN(value);
```

`Double.compare` không có semantics giống hệt toán tử `==`: nó cung cấp total ordering hữu ích cho sorting, trong đó NaN và signed zero được xử lý theo rule của API. Vì vậy hãy chọn API theo câu hỏi domain đang cần trả lời.

Nếu vấn đề là **integer range**, chương sau dùng `BigInteger`. Nếu vấn đề là **exact decimal semantics**, ta sẽ tới `BigDecimal`.
