# Floating Point

`float` and `double` are designed for wide range and efficient real-number computation. The trade-off is **binary floating-point**, where many familiar decimal values have no finite binary representation.

## <a id="binary-floating-point">Binary Floating-point</a>

A `double` does not store the decimal text a user typed. IEEE 754 represents a value through sign, exponent, and significand.

Because decimal `0.1` repeats in binary, the stored value is the nearest representable binary floating-point number.

```java
double x = 0.1 + 0.2;
System.out.println(x); // commonly 0.30000000000000004
```

That is a property of the representation, not a Java-specific bug.

## <a id="precision-rounding-error">Precision and Rounding Error</a>

Each operation may round to the nearest representable floating-point value. Small errors can accumulate across many operations.

Floating point is appropriate for many scientific, sensor, graphics, and engineering problems where tolerances are explicit. It is often the wrong representation when exact decimal semantics are part of the domain contract.

## <a id="nan-infinity-negative-zero">NaN, Infinity and Negative Zero</a>

IEEE 754 includes special values:

```text
NaN
→ “not a number” results such as 0.0 / 0.0

Infinity / -Infinity
→ certain overflow/division-by-zero results

+0.0 / -0.0
→ zero values with different signs in the representation
```

`NaN` also has unusual comparison semantics: `NaN == NaN` is `false`.

## <a id="floating-point-comparison">Comparing Floating-point Values</a>

Use `==` only when exact represented-value equality is actually the contract.

For approximate computation, a tolerance may be more appropriate:

```java
Math.abs(actual - expected) <= epsilon
```

But `epsilon` is not universal; it depends on scale, magnitude, and the domain's acceptable error.

If the next problem is unlimited integer range, use `BigInteger`. If it is exact decimal behavior, move to `BigDecimal`.
