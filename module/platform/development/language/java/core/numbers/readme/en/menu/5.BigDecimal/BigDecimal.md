# BigDecimal

## <a id="big-decimal-model">BigDecimal unscaled value and scale</a>
`BigDecimal` models a decimal value as an arbitrary-precision integer plus a scale. Conceptually, value = unscaledValue × 10^-scale. Scale is part of representation, so `1.0` and `1.00` can be numerically equal while having different representations.

## <a id="big-decimal-construction">String/valueOf/double construction</a>
Prefer decimal text or `BigDecimal.valueOf(double)` when starting from a decimal-looking floating value. `new BigDecimal(0.1)` captures the exact binary `double` value, which is usually not the decimal value a human intended.

```java
new BigDecimal("0.1");     // exact decimal 0.1
BigDecimal.valueOf(0.1);   // uses canonical double string form
new BigDecimal(0.1);       // exact binary-double value converted to decimal
```

## <a id="big-decimal-arithmetic">Arithmetic and non-terminating division</a>
BigDecimal arithmetic is exact unless a precision/rounding policy is requested. Division whose decimal expansion is non-terminating throws `ArithmeticException` when no rounding mode or `MathContext` is supplied. Monetary/business code should make scale and rounding rules explicit rather than relying on accidental defaults.
