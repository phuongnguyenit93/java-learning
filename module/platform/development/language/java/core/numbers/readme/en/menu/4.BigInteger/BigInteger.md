# BigInteger

When `long` cannot cover the required range, the correct solution is often a representation that is not limited by primitive fixed width.

## <a id="big-integer-model">What Is BigInteger?</a>

`BigInteger` represents integers with arbitrary precision, limited mainly by available memory.

```java
BigInteger value =
        new BigInteger("123456789012345678901234567890");
```

Common construction options:

```java
BigInteger a = BigInteger.valueOf(123456789L);
BigInteger b = new BigInteger("FF", 16);
BigInteger zero = BigInteger.ZERO;
BigInteger one = BigInteger.ONE;
```

### WHY

`BigInteger` is useful when:

- combinatorial values grow beyond primitive limits;
- counters or domain values can exceed `long`;
- arbitrary-precision integer arithmetic is part of an algorithm;
- some cryptographic arithmetic needs very large integers.

The trade-off is clear:

```text
primitive integer
→ fixed size
→ very fast
→ little allocation

BigInteger
→ arbitrary precision
→ object based
→ arithmetic cost grows with value size
```

Do not use `BigInteger` merely because it sounds "safer" if `long` already satisfies the contract and primitive performance/interoperability matters.

## <a id="big-integer-immutability">BigInteger Is Immutable</a>

`BigInteger` does not overload arithmetic operators. Operations are methods that return new values:

```java
BigInteger a = BigInteger.TEN;

a.add(BigInteger.ONE); // result ignored
System.out.println(a); // 10

a = a.add(BigInteger.ONE);
System.out.println(a); // 11
```

Mental model:

```text
BigInteger object
→ value does not change

reference variable
→ may point to a new BigInteger object
```

Immutability simplifies reasoning and sharing, but long arithmetic chains can create intermediate objects.

## <a id="big-integer-operations">Operations and Conversion Boundaries</a>

Core operations include:

```java
a.add(b);
a.subtract(b);
a.multiply(b);
a.divide(b);
a.remainder(b);
a.mod(b);
a.pow(3);
a.gcd(b);
```

### Integer division is still integer division

```java
BigInteger seven = BigInteger.valueOf(7);
BigInteger two = BigInteger.valueOf(2);

System.out.println(seven.divide(two)); // 3
```

`BigInteger` solves range. It does not turn integer arithmetic into decimal arithmetic.

### `remainder` and `mod` are not interchangeable

`remainder` follows integer-remainder semantics and can be negative when the dividend is negative. `mod(m)` models modular arithmetic, requires a positive modulus, and returns a non-negative result in the range `0 <= result < m`.

That distinction matters in number-theory and cryptographic arithmetic; do not substitute one for the other merely because both resemble "%".

### Comparison

```java
int cmp = a.compareTo(b);
boolean same = a.equals(b);
```

Unlike `BigDecimal`, BigInteger equality has no scale distinction.

### Primitive conversion is a narrowing boundary

```java
BigInteger huge = new BigInteger("999999999999999999999");
int truncated = huge.intValue();
```

`intValue()` may discard high-order bits. If fitting the primitive range is part of the contract:

```java
int exact = huge.intValueExact();
long exactLong = huge.longValueExact();
```

The exact conversions throw `ArithmeticException` when the value does not fit.

If the real problem is **decimal exactness + rounding policy**, move to `BigDecimal`.
