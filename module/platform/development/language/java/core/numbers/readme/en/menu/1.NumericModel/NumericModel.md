# Java Numeric Model

## <a id="numeric-type-model">Java numeric type model</a>
Java separates fixed-width primitive numeric types from arbitrary-precision object types. `byte`, `short`, `int`, and `long` are signed two's-complement integers; `float` and `double` follow IEEE 754 binary floating-point. `BigInteger` and `BigDecimal` are immutable objects for values that exceed primitive range or require decimal arithmetic.

## <a id="integer-vs-floating">Integer vs floating-point semantics</a>
Integer arithmetic is exact within range and wraps on overflow unless an exact-checking method is used. Floating-point arithmetic has a much larger dynamic range but represents most decimal fractions approximately and includes special values such as NaN and infinities.

## <a id="numeric-conversions">Numeric conversions and promotion</a>
Arithmetic applies Java's promotion/conversion rules before an operation runs. Smaller integral operands often become `int`; mixed numeric expressions tend toward a wider type. A cast can narrow the result but cannot recover information already lost during the operation.
