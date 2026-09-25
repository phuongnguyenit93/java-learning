# Integer Arithmetic and Overflow

## <a id="integer-overflow-wraparound">Integer overflow and wraparound</a>
Primitive integer operations do not automatically throw on overflow. Results wrap modulo the type width using two's-complement semantics.

```java
int x = Integer.MAX_VALUE;
int y = x + 1; // Integer.MIN_VALUE
```

Overflow is predictable language behavior, but it is often a bug when the value represents money, counters, sizes, or identifiers.

## <a id="checked-arithmetic">Checked arithmetic with exact methods</a>
`Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact`, and related conversion helpers throw `ArithmeticException` when the primitive result cannot be represented. Use them when silent wraparound is unacceptable.

## <a id="boundary-values">MIN/MAX boundary reasoning</a>
Every fixed-width integer type has `MIN_VALUE` and `MAX_VALUE`. Beware asymmetric signed ranges: for example `-Integer.MIN_VALUE` is still `Integer.MIN_VALUE` because the positive counterpart cannot be represented. Boundary tests should include zero, one step around limits, and sign changes.
