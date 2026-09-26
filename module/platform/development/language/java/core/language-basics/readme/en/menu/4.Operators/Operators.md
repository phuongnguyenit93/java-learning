# Operators

Operators are not merely shorthand symbols. Java applies promotion, grouping, evaluation-order, and short-circuit rules around them.

Common groups include:

- arithmetic: `+`, `-`, `*`, `/`, `%`;
- unary: `+`, `-`, `++`, `--`, `!`, `~`;
- comparison: `<`, `<=`, `>`, `>=`, `==`, `!=`;
- logical: `&&`, `||`, `!`;
- bitwise/shift: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`;
- assignment: `=`, `+=`, `-=`, `*=`, ...;
- conditional: `condition ? a : b`.

## <a id="numeric-promotion">Numeric Promotion</a>

In arithmetic expressions, `byte`, `short`, and `char` are commonly promoted to `int`:

```java
byte a = 1;
byte b = 2;
int c = a + b;
```

Reason about the type of the **whole expression**, not only the operand types.

### Arithmetic semantics depend on operand types

```java
int a = 5 / 2;       // 2
double b = 5 / 2;    // 2.0: integer division happened first
double c = 5 / 2.0;  // 2.5
```

Compound assignment has special conversion behavior:

```java
byte b = 1;
// b = b + 1; // does not compile: b + 1 is int
b += 1;        // compiles
```

So `x += y` is not always equivalent to mechanically expanding it as `x = x + y`.

## <a id="short-circuit-operators">Short-circuit Boolean Operators</a>

`&&` and `||` short-circuit:

```java
user != null && user.isActive()
```

If the left side is false, the right side is not evaluated.

That is often part of correctness, not only an optimization. Boolean `&` and `|` evaluate both sides and therefore have different semantics.

Ordering matters for guards:

```java
if (user != null && user.isActive()) { }
```

Reversing the operands may dereference `user` before the null check. Avoid placing required side effects in a right-hand operand that may be skipped.

### Conditional operator `?:`

The conditional operator is an **expression** that produces a value:

```java
String label = active ? "ACTIVE" : "INACTIVE";
```

Its execution model is:

```text
evaluate condition
├─ true  → evaluate only the second operand
└─ false → evaluate only the third operand
```

Like short-circuit boolean operators, Java evaluates only **one** result expression:

```java
String name = user != null ? user.getName() : "anonymous";
```

The compiler determines the type of the whole conditional expression from both result operands using the applicable type/conversion rules. For multi-step decisions or deeply nested conditionals, `if/else` is usually easier to read.

## <a id="bitwise-shift">Bitwise and Shift Operators</a>

Integer types support `&`, `|`, `^`, `~`, `<<`, `>>`, and `>>>`.

`>>` performs sign-preserving arithmetic right shift; `>>>` zero-fills. Shift distances are also masked according to the operand width.

Bit-level code benefits from explicit tests around sign and width boundaries.

```java
int flags = 0b0101;
int mask  = 0b0001;
boolean enabled = (flags & mask) != 0;
```

Bitmasks are appropriate for low-level/protocol/compact representations; ordinary business state is often clearer with enums or sets.

## <a id="precedence-side-effects">Precedence and Evaluation Order</a>

Precedence determines how an expression groups; evaluation order determines when operands are evaluated.

Java specifies left-to-right operand evaluation in many expression contexts, but complex side effects still make code difficult to reason about.

Use parentheses or smaller statements when understanding intent requires memorizing a long precedence table.

Precedence, associativity, and evaluation order are separate concepts:

```java
int result = 2 + 3 * 4; // 14
int left = 20 / 5 / 2;  // 2 through left associativity
```

Prefix/postfix increment also differ in the expression value they produce:

```java
int i = 1;
int a = i++; // a=1, i=2
int b = ++i; // i=3, b=3
```

For equality, primitive `==` compares primitive values after applicable conversions; reference `==` compares identity/reference relationships rather than domain value equality.

The next chapter moves from implicit conversions in expressions to explicit casting.
