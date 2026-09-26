# BigDecimal

`BigDecimal` phù hợp khi domain cần **decimal semantics rõ ràng**, ví dụ money, tax, rates hoặc các phép tính mà decimal `0.1` phải mang đúng ý nghĩa thập phân.

Nó không đơn giản là “double nhưng chính xác hơn”. BigDecimal có model và policy riêng.

## <a id="big-decimal-model">Mô hình BigDecimal</a>

Có thể hiểu một `BigDecimal` bằng:

```text
 unscaled value (BigInteger arbitrary precision)
      ×
10^(-scale)

scale
→ int 32-bit
```

Ví dụ:

```text
123.45

unscaled value = 12345
scale          = 2
```

`1.0` và `1.00` có cùng numerical value nhưng scale khác nhau. Scale là một phần của representation và sẽ quan trọng ở chapter comparison.

Vì vậy “arbitrary precision” của BigDecimal chủ yếu nói về **unscaled integer / số lượng chữ số**, không có nghĩa scale cũng là một integer không giới hạn.

### WHY BigDecimal thuộc Numbers?

Với money/rate/tax, câu hỏi không chỉ là:

> giá trị gần đúng có đủ tốt không?

mà còn là:

> domain muốn giữ bao nhiêu decimal places, khi nào round, round theo mode nào, equality có xét scale không?

BigDecimal đưa các policy này ra rõ ràng thay vì giấu chúng trong floating-point approximation.

## <a id="big-decimal-construction">Khởi tạo BigDecimal</a>

### Construction từ String

Khi có decimal literal/text exact:

```java
BigDecimal rate = new BigDecimal("0.1");
```

### `valueOf(double)`

```java
BigDecimal value = BigDecimal.valueOf(0.1);
```

`valueOf(double)` dùng decimal string representation của `double`, nên với literal như `0.1` nó thường cho result đúng với decimal intent mà programmer mong đợi.

### Pitfall: `new BigDecimal(double)`

```java
BigDecimal bad = new BigDecimal(0.1);
```

Constructor nhận **binary floating-point value thực tế**, nên result có thể giống:

```text
0.10000000000000000555...
```

### Quan trọng: BigDecimal không thể hồi phục intent đã mất

```java
double x = 0.1 + 0.2;
BigDecimal value = BigDecimal.valueOf(x);
```

Ở đây arithmetic floating-point đã xảy ra trước:

```text
0.1 + 0.2
→ binary floating-point approximation
→ 0.30000000000000004
→ BigDecimal.valueOf(...)
```

BigDecimal không biết programmer “muốn” decimal `0.3`.

Nếu exact decimal semantics là contract, hãy giữ pipeline ở BigDecimal từ đầu:

```java
BigDecimal result =
        new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
```

## <a id="big-decimal-arithmetic">Arithmetic và Division</a>

BigDecimal immutable:

```java
BigDecimal amount = new BigDecimal("10.00");
BigDecimal fee = new BigDecimal("1.25");

amount.add(fee); // amount không đổi
amount = amount.add(fee);
```

Core operations:

```java
amount.add(fee);
amount.subtract(discount);
amount.multiply(rate);
amount.divide(divisor);
```

### Arithmetic cũng mang theo scale semantics

Không chỉ numerical value thay đổi; preferred scale của result còn phụ thuộc operation. Ví dụ, không dùng `MathContext`:

```text
add/subtract
→ preferred scale thường là max(scale trái, scale phải)

multiply
→ preferred scale = scale trái + scale phải

divide exact
→ preferred scale bắt đầu từ scale trái - scale phải,
  nhưng exact quotient có thể cần scale lớn hơn
```

Vì vậy scale của result không nên được đoán chỉ bằng cách nhìn input text. Nếu domain yêu cầu output scale cố định, policy đó vẫn cần được áp dụng rõ ràng ở boundary thích hợp.

### Division có thể cần rounding policy

```java
BigDecimal.ONE.divide(new BigDecimal("3"));
```

`1 / 3` không có decimal representation hữu hạn, nên exact division không thể hoàn thành và có thể throw `ArithmeticException`.

Bạn phải đưa policy:

```java
BigDecimal result =
        BigDecimal.ONE.divide(
                new BigDecimal("3"),
                4,
                RoundingMode.HALF_UP
        );
```

### BigDecimal không tự quyết định business rule

Nó không biết:

- currency cần scale 2 hay 0;
- tax phải round từng line item hay tổng cuối;
- rate intermediate giữ bao nhiêu precision;
- legal rule dùng HALF_UP hay mode khác.

Đây là feature, không phải thiếu sót:

```text
BigDecimal
→ cung cấp decimal arithmetic

domain policy
→ quyết định precision / scale / rounding boundary
```

Chương tiếp theo phân biệt **precision và scale**, hai khái niệm rất dễ bị dùng lẫn.
