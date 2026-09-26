# BigDecimal

`BigDecimal` is useful when the domain needs **explicit decimal semantics**, such as money, rates, or calculations where decimal `0.1` must mean exactly that decimal quantity.

## <a id="big-decimal-model">BigDecimal Model</a>

A `BigDecimal` can be understood through an unscaled value and a scale.

For `123.45`:

```text
unscaled value = 12345
scale = 2
```

Scale is part of the representation, so `1.0` and `1.00` can have the same numerical value but different representations.

## <a id="big-decimal-construction">Constructing BigDecimal</a>

For exact decimal literals, prefer:

```java
new BigDecimal("0.1")
```

or commonly:

```java
BigDecimal.valueOf(0.1)
```

Avoid `new BigDecimal(0.1)` when you expect exact decimal `0.1`, because the constructor receives the already-approximate binary `double` value.

## <a id="big-decimal-arithmetic">Arithmetic and Division</a>

`BigDecimal` is immutable:

```java
amount = amount.add(fee);
```

Some decimal divisions do not terminate:

```java
BigDecimal.ONE.divide(new BigDecimal("3"));
```

Without an explicit rounding/precision policy, such a division may throw `ArithmeticException`.

`BigDecimal` deliberately does not invent the business rounding rule for you. The next chapter separates precision from scale.
