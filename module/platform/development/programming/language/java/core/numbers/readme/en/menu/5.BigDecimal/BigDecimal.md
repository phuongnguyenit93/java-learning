# BigDecimal

`BigDecimal` is appropriate when the domain requires **explicit decimal semantics**, such as money, tax, rates, or calculations where decimal `0.1` must mean that decimal quantity.

It is not simply "a more accurate double". BigDecimal has its own representation and policy model.

## <a id="big-decimal-model">BigDecimal Model</a>

A useful mental model is:

```text
 unscaled value (arbitrary-precision BigInteger)
      ×
10^(-scale)

scale
→ 32-bit int
```

For:

```text
123.45

unscaled value = 12345
scale          = 2
```

`1.0` and `1.00` have the same numerical quantity but different scales. Scale is part of the representation and later affects equality semantics.

So BigDecimal's "arbitrary precision" primarily refers to the **unscaled integer / number of digits**; the scale itself is not an unbounded integer.

### WHY BigDecimal matters

Money/rate/tax code often needs to answer more than "is the approximation close enough?"

It may need:

- an exact decimal quantity;
- a specific scale;
- a defined rounding boundary;
- a defined rounding mode;
- predictable comparison semantics.

BigDecimal exposes those policies explicitly instead of hiding them inside binary floating-point approximation.

## <a id="big-decimal-construction">Constructing BigDecimal</a>

### From String

For an exact decimal literal or text value:

```java
BigDecimal rate = new BigDecimal("0.1");
```

### `valueOf(double)`

```java
BigDecimal value = BigDecimal.valueOf(0.1);
```

`valueOf(double)` uses the decimal string representation of the `double`. For a literal such as `0.1`, that commonly preserves the decimal intent a programmer expects.

### Pitfall: `new BigDecimal(double)`

```java
BigDecimal bad = new BigDecimal(0.1);
```

This constructor receives the actual binary floating-point value, so the BigDecimal can expose a long decimal approximation such as:

```text
0.10000000000000000555...
```

### BigDecimal cannot recover intent that was already lost

```java
double x = 0.1 + 0.2;
BigDecimal value = BigDecimal.valueOf(x);
```

The floating-point arithmetic has already happened:

```text
0.1 + 0.2
→ binary approximation
→ 0.30000000000000004
→ BigDecimal.valueOf(...)
```

BigDecimal cannot infer that the original desired decimal quantity was `0.3`.

If exact decimal semantics are the contract, keep the pipeline decimal from the beginning:

```java
BigDecimal result =
        new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
```

## <a id="big-decimal-arithmetic">Arithmetic and Division</a>

BigDecimal is immutable:

```java
BigDecimal amount = new BigDecimal("10.00");
BigDecimal fee = new BigDecimal("1.25");

amount.add(fee); // amount unchanged
amount = amount.add(fee);
```

Core arithmetic:

```java
amount.add(fee);
amount.subtract(discount);
amount.multiply(rate);
amount.divide(divisor);
```

### Arithmetic also carries scale semantics

Operations affect more than the numerical quantity. Without a `MathContext`, preferred result scale follows operation-specific rules:

```text
add/subtract
→ preferred scale is generally max(left scale, right scale)

multiply
→ preferred scale = left scale + right scale

exact divide
→ preferred scale starts from left scale - right scale,
  although an exact quotient may require a larger scale
```

Do not infer the result scale only from how the input text looks. If the domain requires a fixed output scale, apply that policy explicitly at the appropriate boundary.

### Division may require a rounding policy

```java
BigDecimal.ONE.divide(new BigDecimal("3"));
```

`1 / 3` has no finite decimal representation, so exact division cannot complete and may throw `ArithmeticException`.

Supply the policy explicitly:

```java
BigDecimal result =
        BigDecimal.ONE.divide(
                new BigDecimal("3"),
                4,
                RoundingMode.HALF_UP
        );
```

BigDecimal does not know whether a currency uses scale 2 or 0, whether tax rounds per line or at the final total, or which legal rounding rule applies.

That is intentional:

```text
BigDecimal
→ decimal arithmetic mechanism

domain policy
→ precision / scale / rounding boundary
```

The next chapter separates **precision** from **scale**.
