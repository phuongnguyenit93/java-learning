# Precision và Scale

Hai thuật ngữ này mô tả hai khía cạnh khác nhau của `BigDecimal`. Dùng chúng như từ đồng nghĩa sẽ dẫn đến policy sai.

## <a id="precision-vs-scale">Precision và Scale</a>

Với:

```text
123.45
```

ta có:

```text
precision = 5
→ tổng số chữ số của unscaled value

scale = 2
→ số mũ thập phân dùng để đặt decimal point
```

Trong code:

```java
BigDecimal value = new BigDecimal("123.45");

System.out.println(value.precision()); // 5
System.out.println(value.scale());     // 2
```

### Scale không chỉ là “số chữ số sau dấu phẩy” trong mọi representation

Scale có thể bằng 0, dương hoặc âm:

```java
BigDecimal value =
        new BigDecimal("1000")
                .stripTrailingZeros();

System.out.println(value);       // có thể 1E+3
System.out.println(value.scale()); // có thể -3
```

Với scale âm, decimal point về mặt representation được dịch sang phải.

Mental model chính xác hơn:

```text
value = unscaledValue × 10^(-scale)
```

## <a id="scale-transformations">Thay đổi Scale</a>

`setScale` tạo BigDecimal mới với scale mong muốn.

```java
BigDecimal value = new BigDecimal("12.345");
BigDecimal rounded =
        value.setScale(2, RoundingMode.HALF_UP);
```

Nếu tăng scale:

```java
new BigDecimal("12.3").setScale(4)
// 12.3000
```

thường không cần mất information.

Nếu giảm scale:

```java
new BigDecimal("12.345").setScale(2)
```

thì có discarded digits; nếu không cung cấp rounding mode phù hợp, operation có thể fail.

### `movePointLeft` / `movePointRight`

```java
BigDecimal x = new BigDecimal("123.45");

x.movePointLeft(2);  // 1.2345
x.movePointRight(2); // 12345
```

Các method này thay numerical value bằng power of ten, khác với việc đơn thuần “format số”.

### `stripTrailingZeros`

```java
BigDecimal x = new BigDecimal("1.2300");
BigDecimal normalized = x.stripTrailingZeros();
```

Numerical value giữ nguyên nhưng representation/scale có thể thay đổi. Điều này quan trọng với `equals`, hash collections và normalization policy.

## <a id="math-context">MathContext</a>

`MathContext` kiểm soát **precision của arithmetic operation** cùng rounding mode:

```java
MathContext context =
        new MathContext(4, RoundingMode.HALF_EVEN);

BigDecimal result =
        new BigDecimal("123.45").multiply(
                new BigDecimal("9.876"),
                context
        );
```

### `MathContext` khác `setScale`

```text
setScale
→ kiểm soát scale của result
→ thường liên quan decimal places/domain unit

MathContext
→ kiểm soát tổng precision của operation
→ có thể round significant digits
```

Ví dụ:

```java
BigDecimal x = new BigDecimal("12345.67");

BigDecimal byScale =
        x.setScale(1, RoundingMode.HALF_UP);
// 12345.7

BigDecimal byPrecision =
        x.round(new MathContext(4, RoundingMode.HALF_UP));
// 1.235E+4
```

`MathContext.UNLIMITED` về cơ bản không giới hạn precision của arithmetic, nhưng các operation như non-terminating division vẫn cần policy phù hợp.

Chương tiếp theo đi sâu vào rounding: **làm tròn là thay đổi numerical result theo một policy cụ thể, không chỉ là format output**.
