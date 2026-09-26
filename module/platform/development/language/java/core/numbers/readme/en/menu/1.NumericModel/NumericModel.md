# Numeric Model in Java

Java does not have one numeric representation that is ideal for every problem. Each representation trades off **range, exactness, performance, memory footprint, and rounding behavior**.

The core mental model is:

```text
int / long
→ fast, compact, exact integers inside a fixed range

float / double
→ very wide range and efficient scientific/engineering arithmetic,
  but many decimal fractions are only approximations

BigInteger
→ integer arithmetic not limited by primitive fixed width

BigDecimal
→ explicit decimal value + scale,
  useful when decimal semantics and rounding policy matter
```

No representation gives unlimited range, exact decimal behavior, primitive speed, and a tiny footprint at the same time. Learning Java numbers therefore starts with **matching the representation to the problem**.

Roadmap:

```text
Which representation fits the problem?
Numeric Model
        ↓
How can fixed-width integers fail?
Integer Overflow
        ↓
Why does 0.1 + 0.2 surprise people?
Floating Point
        ↓
What if integer range is not enough?
BigInteger
        ↓
What if exact decimal semantics matter?
BigDecimal
        ↓
How do precision and scale differ?
Precision & Scale
        ↓
When and how should values be rounded?
Rounding
        ↓
Why do BigDecimal equals and compareTo differ?
BigDecimal Comparison
        ↓
Which standard numeric helpers already exist?
Math
        ↓
How do pseudo-random and security randomness differ?
Random → SecureRandom
```

## <a id="numeric-type-model">Numeric Type Families</a>

Java has fixed-width primitive numeric types:

```text
byte, short, int, long
→ signed integers

float, double
→ IEEE 754 floating point
```

and important immutable object types:

```text
BigInteger
→ arbitrary-precision integer

BigDecimal
→ decimal value with explicit scale
```

Start from the domain requirement: counters/IDs, sensor values, scientific computation, money, rates, and so on.

## <a id="integer-vs-floating">Integer vs Floating-point</a>

Integer arithmetic is **exact inside its representable range**. When the range is exceeded, primitive integers wrap rather than automatically expanding.

Floating-point values have a very wide dynamic range but use binary representation, so many familiar decimal fractions cannot be represented exactly.

```text
integer
→ exact within range
→ overflow beyond the range

floating-point
→ approximate representation
→ NaN / infinity / negative zero are part of the model
```

These are different failure modes: integers can silently wrap to a wrong value; floating point usually gives a nearby approximation.

## <a id="numeric-conversions">Conversions and Promotion</a>

Before arithmetic runs, Java applies conversion and promotion rules.

Smaller integer types are commonly promoted to `int`:

```java
byte a = 10;
byte b = 20;
int result = a + b;
```

Mixed expressions move toward a wider representation according to language rules.

A cast can narrow the result type, but it **cannot recover information already lost** through overflow or rounding.

The next chapter begins with the easiest integer failure to miss: overflow that does not automatically throw.
