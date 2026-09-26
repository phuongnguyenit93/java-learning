# Operators

Operators are not merely shorthand symbols. Java applies promotion, grouping, evaluation-order, and short-circuit rules around them.

## <a id="numeric-promotion">Numeric Promotion</a>

In arithmetic expressions, `byte`, `short`, and `char` are commonly promoted to `int`:

```java
byte a = 1;
byte b = 2;
int c = a + b;
```

Reason about the type of the **whole expression**, not only the operand types.

## <a id="short-circuit-operators">Short-circuit Boolean Operators</a>

`&&` and `||` short-circuit:

```java
user != null && user.isActive()
```

If the left side is false, the right side is not evaluated.

That is often part of correctness, not only an optimization. Boolean `&` and `|` evaluate both sides and therefore have different semantics.

## <a id="bitwise-shift">Bitwise and Shift Operators</a>

Integer types support `&`, `|`, `^`, `~`, `<<`, `>>`, and `>>>`.

`>>` performs sign-preserving arithmetic right shift; `>>>` zero-fills. Shift distances are also masked according to the operand width.

Bit-level code benefits from explicit tests around sign and width boundaries.

## <a id="precedence-side-effects">Precedence and Evaluation Order</a>

Precedence determines how an expression groups; evaluation order determines when operands are evaluated.

Java specifies left-to-right operand evaluation in many expression contexts, but complex side effects still make code difficult to reason about.

Use parentheses or smaller statements when understanding intent requires memorizing a long precedence table.

The next chapter moves from implicit conversions in expressions to explicit casting.
