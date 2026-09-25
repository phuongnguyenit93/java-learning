# BigInteger

## <a id="big-integer-model">Arbitrary-precision integer model</a>
`BigInteger` represents signed integers whose magnitude is limited mainly by available memory rather than a fixed primitive width. It is useful for cryptographic/math domains, very large counters, combinatorics, and exact integer calculations that exceed `long`.

## <a id="big-integer-immutability">BigInteger immutability</a>
All arithmetic returns a new `BigInteger`; the receiver is not modified.

```java
BigInteger a = new BigInteger("1000");
a.add(BigInteger.ONE);     // ignored result
BigInteger b = a.add(BigInteger.ONE); // 1001
```

## <a id="big-integer-operations">Core arithmetic and conversion boundaries</a>
`BigInteger` supports arithmetic, division/remainder, powers, gcd, bit operations, and primality-oriented helpers. Converting back to primitive types can truncate with methods such as `intValue`; use `intValueExact`/`longValueExact` when out-of-range values must fail instead of silently losing bits.
