# Java Operators

## <a id="numeric-promotion">Numeric promotion</a>
Arithmetic does not always run in the apparent operand type. Unary/binary numeric promotion can widen smaller integral operands to `int`, combine wider types, and influence both result type and overflow behavior.

```java
byte a = 1, b = 2;
int c = a + b; // byte + byte produces int
```

## <a id="short-circuit-operators">Short-circuit boolean operators</a>
`&&` and `||` evaluate the right operand only when necessary. `&` and `|` on booleans evaluate both operands. This affects side effects, expensive work, and null-safe guards. Short-circuiting is a control-flow property, not merely an optimization.

## <a id="bitwise-shift">Bitwise and shift operators</a>
Integral bitwise operators manipulate bit patterns. `<<` shifts left, `>>` performs sign-propagating right shift, and `>>>` performs zero-fill right shift. Shift distances are masked according to operand width after promotion.

## <a id="precedence-side-effects">Precedence, evaluation order and side effects</a>
Precedence determines grouping, while Java also specifies evaluation order. Parentheses should communicate intent even when precedence is known. Expressions combining increment, calls, assignments, and other side effects are legal but often needlessly hard to reason about.
