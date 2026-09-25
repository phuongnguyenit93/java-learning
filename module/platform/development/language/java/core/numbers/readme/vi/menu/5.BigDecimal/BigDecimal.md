# BigDecimal

## <a id="big-decimal-model">BigDecimal gồm unscaled value và scale</a>
`BigDecimal` model decimal value bằng arbitrary-precision integer cộng scale. Có thể hiểu value = unscaledValue × 10^-scale. Scale là một phần representation nên `1.0` và `1.00` có thể bằng nhau về numerical value nhưng representation khác nhau.

## <a id="big-decimal-construction">Khởi tạo từ String/valueOf/double</a>
Ưu tiên decimal text hoặc `BigDecimal.valueOf(double)` khi bắt đầu từ floating value mang ý nghĩa decimal. `new BigDecimal(0.1)` capture chính xác binary `double` value, thường không phải decimal value mà con người mong muốn.

```java
new BigDecimal("0.1");
BigDecimal.valueOf(0.1);
new BigDecimal(0.1);
```

## <a id="big-decimal-arithmetic">Arithmetic và non-terminating division</a>
BigDecimal arithmetic exact trừ khi có precision/rounding policy. Division có decimal expansion không kết thúc sẽ throw `ArithmeticException` nếu không cung cấp rounding mode hoặc `MathContext`. Monetary/business code nên explicit scale và rounding rule.
