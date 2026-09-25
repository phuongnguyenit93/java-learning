# String Concatenation

## <a id="concat-semantics">String concatenation semantics</a>
The `+` operator concatenates when either relevant operand is a String. Primitive/object operands are converted to textual form according to Java rules. Operator association matters: `"x" + 1 + 2` produces `x12`, while `1 + 2 + "x"` produces `3x`.

## <a id="compile-time-concat">Compile-time constant concatenation</a>
A concatenation made entirely from compile-time constants can be folded by the compiler and behave as one pooled literal. This affects identity observations but should never be used as a reason to compare strings with `==`.

## <a id="runtime-concat">Runtime concatenation and implementation boundary</a>
Runtime concatenation may be implemented with `StringBuilder`, `invokedynamic` string-concat strategies, or other JVM/compiler optimizations depending on Java version. Source-level code should rely on the resulting String semantics, not a specific generated mechanism.

## <a id="loop-concat-cost">Repeated concatenation cost</a>
Repeatedly creating new immutable strings in a loop can copy growing content many times. Use `StringBuilder` for incremental construction when the number of pieces is dynamic. For a small fixed expression, ordinary `+` is clear and the compiler/JVM can optimize it effectively.
