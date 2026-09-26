# Floating Point

`float` and `double` are designed for **wide range + efficient real-number computation**. The trade-off is binary floating-point, where many familiar decimal values have no finite binary representation.

## <a id="binary-floating-point">Binary Floating-point</a>

### WHAT

IEEE 754 floating point can be viewed as:

```text
sign
 + exponent
 + significand
```

`float` uses binary32 and `double` uses binary64. In ordinary Java application code, `double` is usually the default floating-point choice because it offers greater range and precision.

### WHY does 0.1 not fit exactly?

Some fractions that terminate in base 10 repeat forever in base 2:

```text
0.5 decimal
→ 0.1 binary
→ finite representation

0.1 decimal
→ repeating binary fraction
→ nearest representable value is stored
```

```java
double x = 0.1 + 0.2;
System.out.println(x); // 0.30000000000000004
```

This is a property of finite binary floating point, not a Java-specific bug.

## <a id="precision-rounding-error">Precision and Rounding Error</a>

### HOW does error appear?

```text
mathematical exact result
        ↓
nearest representable binary value
        ↓
next operation
        ↓
round again
```

For ordinary finite results inside the representable range, arithmetic generally maps the mathematical result to a nearby representable floating-point value according to IEEE 754/Java rounding semantics.

Not every out-of-range result merely becomes another finite approximation:

```text
magnitude too large
→ overflow
→ may become +Infinity / -Infinity

magnitude too small
→ underflow
→ may become a subnormal value or signed zero
```

Small errors can accumulate:

```java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total);
```

Floating-point arithmetic is also not always associative in the mathematical sense:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

System.out.println((a + b) + c);
System.out.println(a + (b + c));
```

`double` is often appropriate for measurements, statistics, graphics, scientific computation, and simulation where a tolerance is explicit. It is often the wrong representation when exact decimal behavior is itself part of the domain contract.

## <a id="nan-infinity-negative-zero">NaN, Infinity and Negative Zero</a>

IEEE 754 includes special values:

```text
NaN
→ a non-ordinary numeric result

Infinity / -Infinity
→ some overflow/division-by-zero results

+0.0 / -0.0
→ zero values with different signs in the representation
```

```java
double nan = 0.0 / 0.0;
double inf = 1.0 / 0.0;

System.out.println(Double.isNaN(nan));      // true
System.out.println(Double.isInfinite(inf)); // true
```

NaN propagates through many operations:

```java
double result = Double.NaN + 10;
System.out.println(result); // NaN
```

Use `Double.isNaN(value)` instead of comparing NaN with `==`.

Signed zero compares equal:

```java
System.out.println(0.0 == -0.0); // true
```

but can still affect arithmetic:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

## <a id="floating-point-comparison">Comparing Floating-point Values</a>

Use `==` only when exact represented-value equality is really the contract.

Approximate computation often needs a tolerance:

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double epsilon = 1e-9;

boolean close = Math.abs(actual - expected) <= epsilon;
```

But one absolute epsilon is not universal. The correct strategy depends on scale, magnitude, and the domain error budget.

Useful APIs also include:

```java
Double.compare(a, b);
Double.isFinite(value);
Double.isNaN(value);
```

`Double.compare` does not have exactly the same semantics as `==`. It provides an ordering useful for sorting and defines behavior for NaN and signed zero, so choose the API that matches the question the domain is asking.

If the problem is unlimited integer range, move to `BigInteger`. If it is exact decimal behavior, move to `BigDecimal`.
