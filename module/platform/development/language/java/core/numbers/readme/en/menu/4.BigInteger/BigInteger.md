# BigInteger

When `long` cannot cover the required range, the right solution is often a representation that is not fixed-width rather than hoping overflow never occurs.

## <a id="big-integer-model">What Is BigInteger?</a>

`BigInteger` represents integers with arbitrary precision, limited primarily by available memory.

```java
BigInteger value = new BigInteger("123456789012345678901234567890");
```

It is useful for large counters, combinatorial values, and some cryptographic arithmetic. The trade-off is object allocation and more expensive arithmetic than primitive integers.

## <a id="big-integer-immutability">BigInteger Is Immutable</a>

Operations return new objects instead of mutating the current value:

```java
BigInteger a = BigInteger.TEN;
a.add(BigInteger.ONE); // a is still 10
a = a.add(BigInteger.ONE);
```

The variable can be reassigned, but each `BigInteger` object keeps its value.

## <a id="big-integer-operations">Operations and Conversion Boundaries</a>

`BigInteger` provides arithmetic, `pow`, `gcd`, `mod`, bit operations, and primitive conversions.

Converting back to `int` or `long` is a boundary worth treating explicitly. `intValue()` may truncate, while `intValueExact()`/`longValueExact()` detect values that do not fit.

`BigInteger` solves integer range. It does not solve exact decimal semantics; that is the role of `BigDecimal`.
