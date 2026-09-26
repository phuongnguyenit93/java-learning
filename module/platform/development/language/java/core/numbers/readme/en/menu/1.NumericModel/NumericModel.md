# Numeric Model in Java

Java does not have one numeric representation that is ideal for every problem. Each representation trades off **range, exactness, performance, memory footprint, and rounding behavior**.

The core mental model is:

```text
int / long
→ fast, compact, exact integers inside a fixed range

float / double
→ wide range and efficient scientific/engineering arithmetic,
  but many decimal fractions are only approximations

BigInteger
→ integer arithmetic not limited by primitive fixed width

BigDecimal
→ explicit decimal value + scale,
  useful when decimal semantics and rounding policy matter
```

No representation gives unlimited range, exact decimal behavior, primitive speed, and a tiny footprint at the same time. Learning Java numbers therefore starts with **matching the representation to the problem contract**.

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

Three recurring value families are used throughout the module:

```text
counter / quantity / ID
→ integer semantics and overflow

sensor / scientific measurement
→ floating-point approximation and tolerance

money / rate / tax
→ BigDecimal, precision, scale and rounding policy
```

## <a id="numeric-type-model">Numeric Type Families</a>

### WHAT — which numeric representations does Java provide?

Fixed-width integer primitives:

| Type | Bits | Minimum | Maximum |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32,768 | 32,767 |
| `int` | 32 | -2³¹ | 2³¹ - 1 |
| `long` | 64 | -2⁶³ | 2⁶³ - 1 |

`char` is also an integral primitive type, but it primarily models a UTF-16 code unit. For arithmetic in this module, the main focus is `byte/short/int/long`.

Floating-point primitives:

```text
float   → IEEE 754 binary32
double  → IEEE 754 binary64
```

Important object types:

```text
BigInteger
→ arbitrary-precision integer

BigDecimal
→ arbitrary-precision unscaled integer + int scale
→ explicit decimal semantics and rounding policy
```

### WHY — why are several representations necessary?

Start with the domain requirement:

| Problem | Common representation | Primary reason |
| --- | --- | --- |
| small quantities, indexes, counters | `int` | simple and efficient |
| large counters/timestamps | `long` | wider range |
| scientific/sensor/graphics values | `double` | wide range + hardware support |
| integers beyond `long` | `BigInteger` | no fixed-width limit |
| money, tax, decimal rates | `BigDecimal` | explicit decimal semantics and rounding |

A database identifier may use `long` because range matters, not because arithmetic is central. A temperature measurement can use `double` because tolerance is acceptable. Money often needs `BigDecimal` because decimal behavior is part of the contract.

### HOW — choose from the problem contract

Ask:

```text
1. Is a fractional value required?
        ↓
2. Is exact decimal behavior required?
        ↓
3. What range is required?
        ↓
4. Is overflow/rounding acceptable?
        ↓
5. Do performance and footprint matter?
```

Do not choose `double` merely because it can represent large magnitudes, and do not use `BigDecimal` everywhere merely because it sounds "more precise". Each representation solves a different contract.

## <a id="integer-vs-floating">Integer vs Floating-point</a>

Integer and floating-point arithmetic fail in different ways:

```text
integer
→ exact inside the representable range
→ overflow/wraparound beyond it

floating-point
→ wide dynamic range
→ many values are approximations
→ NaN / infinity / signed zero are part of the model
```

```java
int count = Integer.MAX_VALUE;
count++;
System.out.println(count); // Integer.MIN_VALUE

double total = 0.1 + 0.2;
System.out.println(total); // 0.30000000000000004
```

The first result comes from a finite integer range. The second comes from finite binary representation. The important question is not which family is universally "more accurate", but which semantics fit the problem.

## <a id="numeric-conversions">Conversions and Promotion</a>

### Operation type is decided before assignment

Before arithmetic runs, Java applies conversion and numeric-promotion rules.

Small integer types are usually promoted to `int`:

```java
byte a = 10;
byte b = 20;

int result = a + b;
// byte invalid = a + b; // compile error
```

Mixed expressions are promoted according to language rules:

```java
int i = 10;
long l = 20L;
double d = 1.5;

long x = i + l;
double y = l + d;
```

### Pitfall: a wider assignment target does not rescue an earlier overflow

```java
int quantity = 1_000_000;
int price = 10_000;

long wrong = quantity * price;
```

The real flow is:

```text
int * int
    ↓
arithmetic runs as int
    ↓
overflow may happen
    ↓
the already-wrong int result
    ↓
is converted to long
```

Widen before the arithmetic if the operation must run as `long`:

```java
long correct = (long) quantity * price;
```

### Casting cannot restore lost information

Once overflow or floating-point rounding has happened, a later cast or wrapper type cannot reconstruct the original mathematical intent.

The next chapter starts with the easiest integer failure to miss: **overflow that allows execution to continue**.
