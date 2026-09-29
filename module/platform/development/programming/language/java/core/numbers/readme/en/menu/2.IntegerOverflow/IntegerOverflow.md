# Integer Overflow

Primitive integers feel exact, but they are exact only **inside a fixed range**. Crossing `MIN_VALUE` or `MAX_VALUE` does not automatically switch Java to `BigInteger` or throw an exception.

## <a id="integer-overflow-wraparound">Overflow and Wraparound</a>

### WHAT

An `int` is a signed 32-bit integer:

```text
Integer.MIN_VALUE = -2^31
Integer.MAX_VALUE =  2^31 - 1
```

Arithmetic outside that range wraps:

```java
int value = Integer.MAX_VALUE;
value++;

System.out.println(value); // Integer.MIN_VALUE
```

### WHY is silent wraparound dangerous?

```text
valid input
    ↓
arithmetic overflow
    ↓
wrapped value is still a valid int
    ↓
business logic continues with the wrong value
```

If the value represents quantity, cents, counters, offsets, timeouts, or capacity, the visible failure may occur far from the arithmetic that caused it.

### HOW — focus on intermediate results

```java
int quantity = 1_000_000;
int unitPrice = 10_000;

long total = quantity * unitPrice;
```

The multiplication is still `int * int`. Assignment to `long` happens afterward.

Use a wider operand before the operation:

```java
long total = (long) quantity * unitPrice;
```

And `long` itself can overflow:

```java
long a = 3_000_000_000L;
long b = 4_000_000_000L;

long product = a * b;
```

Moving from `int` to `long` increases the range; it does not eliminate overflow.

## <a id="checked-arithmetic">Checked Arithmetic</a>

When overflow is a **contract failure**, use exact helpers:

```java
Math.addExact(a, b);
Math.subtractExact(a, b);
Math.multiplyExact(a, b);
Math.incrementExact(a);
Math.decrementExact(a);
Math.negateExact(a);
Math.absExact(a);
Math.divideExact(a, b);
Math.toIntExact(longValue);
```

They turn overflow into `ArithmeticException`.

```java
try {
    int next = Math.addExact(Integer.MAX_VALUE, 1);
} catch (ArithmeticException ex) {
    System.out.println("overflow detected");
}
```

This does not mean every arithmetic operation must be checked. Some low-level algorithms intentionally rely on wraparound. The domain contract decides.

With the repository's Java 21 baseline, `absExact` and `divideExact` are especially useful for the boundary cases below: `MIN_VALUE` has no positive counterpart in the same primitive type, and `MIN_VALUE / -1` overflows.

## <a id="boundary-values">MIN/MAX Boundary Reasoning</a>

Two's-complement signed integers have one more negative value than positive values:

```text
int
min = -2147483648
max =  2147483647
```

So:

```java
int abs = Math.abs(Integer.MIN_VALUE);
System.out.println(abs); // still Integer.MIN_VALUE
```

`Math.abs` cannot produce `2147483648` as an `int`. For this `int` overload, the result overflows back to `Integer.MIN_VALUE` rather than automatically throwing.

Similarly:

```java
int x = Integer.MIN_VALUE / -1;
System.out.println(x); // Integer.MIN_VALUE
```

Mathematically this requires `2147483648`, which does not fit in `int`. Java defines this special overflow case to return `Integer.MIN_VALUE`; integer division throws `ArithmeticException` for a zero divisor, not for this overflow.

Boundary-oriented tests should include:

```text
MIN_VALUE
MIN_VALUE + 1
-1
0
1
MAX_VALUE - 1
MAX_VALUE
```

If the domain can legitimately exceed `long`, the better representation is `BigInteger` rather than an ever-growing set of overflow checks.

The next chapter examines a different trade-off: floating point avoids this exact wraparound model but cannot represent every decimal fraction exactly.
