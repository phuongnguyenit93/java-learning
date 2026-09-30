# Độ chính xác (precision) và scale

Hai thuật ngữ này mô tả hai khía cạnh khác nhau của `BigDecimal`. Dùng chúng như từ đồng nghĩa sẽ dẫn đến chính sách xử lý sai.

## <a id="precision-vs-scale">Độ chính xác (precision) và scale</a>

Với:

```text
123.45
```

ta có:

```text
precision = 5
→ tổng số chữ số của giá trị chưa áp dụng scale

scale = 2
→ số mũ thập phân dùng để đặt dấu thập phân
```

Trong mã:

```java
BigDecimal value = new BigDecimal("123.45");

System.out.println(value.precision()); // 5
System.out.println(value.scale());     // 2
```

### Scale không chỉ là “số chữ số sau dấu phẩy” trong mọi cách biểu diễn

Scale có thể bằng 0, dương hoặc âm:

```java
BigDecimal value =
        new BigDecimal("1000")
                .stripTrailingZeros();

System.out.println(value);       // có thể 1E+3
System.out.println(value.scale()); // có thể -3
```

Với scale âm, dấu thập phân trong cách biểu diễn được dịch sang phải.

Cách ghi nhớ chính xác hơn:

```text
giá trị = unscaledValue × 10^(-scale)
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

thường không làm mất thông tin.

Nếu giảm scale:

```java
new BigDecimal("12.345").setScale(2)
```

thì sẽ có chữ số bị loại bỏ; nếu không cung cấp chế độ làm tròn phù hợp, phép toán có thể thất bại.

### `movePointLeft` / `movePointRight`

```java
BigDecimal x = new BigDecimal("123.45");

x.movePointLeft(2);  // 1.2345
x.movePointRight(2); // 12345
```

Các phương thức này thay đổi giá trị số theo lũy thừa của 10, khác với việc đơn thuần “định dạng số”.

### `stripTrailingZeros`

```java
BigDecimal x = new BigDecimal("1.2300");
BigDecimal normalized = x.stripTrailingZeros();
```

Giá trị số giữ nguyên nhưng cách biểu diễn/scale có thể thay đổi. Điều này quan trọng với `equals`, các collection dựa trên giá trị băm và chính sách chuẩn hóa.

## <a id="math-context">MathContext</a>

`MathContext` kiểm soát **độ chính xác của phép toán** cùng chế độ làm tròn:

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
→ kiểm soát scale của kết quả
→ thường liên quan số chữ số thập phân/đơn vị của bài toán

MathContext
→ kiểm soát tổng độ chính xác của phép toán
→ có thể làm tròn các chữ số có nghĩa
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

`MathContext.UNLIMITED` về cơ bản không giới hạn độ chính xác của phép toán, nhưng các phép toán như phép chia thập phân không kết thúc vẫn cần chính sách phù hợp.

Chương tiếp theo đi sâu vào làm tròn: **làm tròn là thay đổi kết quả số theo một chính sách cụ thể, không chỉ là định dạng đầu ra**.
