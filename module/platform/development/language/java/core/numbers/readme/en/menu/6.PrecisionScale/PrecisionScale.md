# Precision and Scale

## <a id="precision-vs-scale">Precision vs scale</a>
Precision is the number of significant digits in a `BigDecimal`; scale is the number of digits to the right of the decimal point when scale is non-negative. They answer different questions and should not be used interchangeably.

```java
BigDecimal x = new BigDecimal("123.4500");
// precision = 7, scale = 4
```

## <a id="scale-transformations">setScale, movePoint and normalization</a>
`setScale` changes representation and may require rounding when reducing scale. `movePointLeft`/`movePointRight` change the numerical value by powers of ten. `stripTrailingZeros` removes representational trailing zeros and can produce a negative scale, so code should not assume normalized values always have scale >= 0.

## <a id="math-context">MathContext and precision control</a>
`MathContext` sets significant-digit precision plus rounding mode for operations that accept it. This is different from fixing the number of decimal places. Choose the policy from domain requirements, and avoid applying one global context blindly to calculations with different meaning.
