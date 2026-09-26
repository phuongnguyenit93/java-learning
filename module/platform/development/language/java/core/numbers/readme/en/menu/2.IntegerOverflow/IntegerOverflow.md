# Integer Overflow

Primitive integers feel exact, but they are exact only **inside a fixed range**. When arithmetic crosses `MIN_VALUE` or `MAX_VALUE`, Java does not automatically switch to `BigInteger` or throw by default.

## <a id="integer-overflow-wraparound">Overflow and Wraparound</a>

```java
int value = Integer.MAX_VALUE;
value++;
```

The result becomes `Integer.MIN_VALUE` because fixed-width two's-complement arithmetic wraps around.

The dangerous part is that execution continues. If the value represents quantity, a counter, cents, or an offset, downstream logic may receive a completely wrong value without an exception.

Test around boundaries, not only ordinary values:

```text
MIN_VALUE
MIN_VALUE + 1
-1 / 0 / 1
MAX_VALUE - 1
MAX_VALUE
```

## <a id="checked-arithmetic">Checked Arithmetic</a>

`Math` provides helpers such as:

```java
Math.addExact(a, b)
Math.subtractExact(a, b)
Math.multiplyExact(a, b)
Math.incrementExact(a)
```

They throw `ArithmeticException` on overflow instead of silently wrapping.

Use them when overflow should be treated as a contract failure rather than valid arithmetic behavior.

## <a id="boundary-values">MIN/MAX Boundary Reasoning</a>

Fixed-width boundaries break some mathematical intuitions. For example, `Math.abs(Integer.MIN_VALUE)` cannot produce `+2147483648` as an `int` because that positive value lies outside the range.

If the domain can legitimately exceed `long`, do not patch individual overflow cases forever; move to `BigInteger`.

Before that, the next chapter examines a different trade-off: floating point avoids the same wraparound model but cannot represent every decimal fraction exactly.
