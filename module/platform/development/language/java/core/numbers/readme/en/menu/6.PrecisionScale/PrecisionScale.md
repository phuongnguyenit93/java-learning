# Precision and Scale

These terms describe different aspects of a `BigDecimal` and should not be used interchangeably.

## <a id="precision-vs-scale">Precision vs Scale</a>

For `123.45`:

```text
precision = 5
→ total significant digits

scale = 2
→ digits to the right of the decimal point
```

Scale may be zero, positive, or even negative in some representations.

Precision is not another word for “decimal places”; that is the role of scale.

## <a id="scale-transformations">Scale Transformations</a>

`setScale(...)` may require rounding when reducing decimal places:

```java
value.setScale(2, RoundingMode.HALF_UP);
```

`movePointLeft`/`movePointRight` shift the decimal point by powers of ten. `stripTrailingZeros()` can change representation/scale while preserving numerical value.

## <a id="math-context">MathContext</a>

`MathContext` controls **operation precision** plus a rounding mode:

```java
MathContext context = new MathContext(4, RoundingMode.HALF_EVEN);
```

That is different from `setScale`: one controls overall arithmetic precision, while the other controls result scale.

The next chapter treats rounding as a deliberate policy rather than a formatting afterthought.
