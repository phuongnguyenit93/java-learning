# Precision and Scale

These terms describe different aspects of a `BigDecimal`. Treating them as synonyms leads to incorrect policies.

## <a id="precision-vs-scale">Precision vs Scale</a>

For:

```text
123.45
```

we have:

```text
precision = 5
→ total number of digits in the unscaled value

scale = 2
→ decimal scaling exponent used to place the point
```

In code:

```java
BigDecimal value = new BigDecimal("123.45");

System.out.println(value.precision()); // 5
System.out.println(value.scale());     // 2
```

### Scale is not always just "digits after the decimal point"

Scale may be zero, positive, or negative:

```java
BigDecimal value =
        new BigDecimal("1000")
                .stripTrailingZeros();

System.out.println(value);        // may be 1E+3
System.out.println(value.scale()); // may be -3
```

The more general model is:

```text
value = unscaledValue × 10^(-scale)
```

## <a id="scale-transformations">Scale Transformations</a>

`setScale` creates a value with the requested scale:

```java
BigDecimal value = new BigDecimal("12.345");
BigDecimal rounded =
        value.setScale(2, RoundingMode.HALF_UP);
```

Increasing scale can add trailing zeros without losing information:

```java
new BigDecimal("12.3").setScale(4);
// 12.3000
```

Reducing scale may discard digits and therefore require rounding.

### Moving the decimal point

```java
BigDecimal x = new BigDecimal("123.45");

x.movePointLeft(2);  // 1.2345
x.movePointRight(2); // 12345
```

These operations change the numerical value by powers of ten; they are not merely formatting operations.

### `stripTrailingZeros`

```java
BigDecimal x = new BigDecimal("1.2300");
BigDecimal normalized = x.stripTrailingZeros();
```

The numerical value is preserved, but representation and scale may change. That matters for equality, hash collections, and normalization policy.

## <a id="math-context">MathContext</a>

`MathContext` controls **operation precision** together with a rounding mode:

```java
MathContext context =
        new MathContext(4, RoundingMode.HALF_EVEN);

BigDecimal result =
        new BigDecimal("123.45").multiply(
                new BigDecimal("9.876"),
                context
        );
```

### MathContext vs setScale

```text
setScale
→ controls result scale
→ often tied to decimal places/domain units

MathContext
→ controls total arithmetic precision
→ may round significant digits
```

Example:

```java
BigDecimal x = new BigDecimal("12345.67");

BigDecimal byScale =
        x.setScale(1, RoundingMode.HALF_UP);
// 12345.7

BigDecimal byPrecision =
        x.round(new MathContext(4, RoundingMode.HALF_UP));
// 1.235E+4
```

`MathContext.UNLIMITED` does not limit arithmetic precision, but operations such as non-terminating division can still require an explicit policy.

The next chapter treats rounding as a **numerical policy**, not just output formatting.
