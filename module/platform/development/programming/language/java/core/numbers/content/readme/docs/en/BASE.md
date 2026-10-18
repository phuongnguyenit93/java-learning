# Numbers

This module builds the mental model for **numeric representation and arithmetic semantics** in Java: integer overflow, floating-point approximation, arbitrary precision, decimal scale/rounding, and random-number choices.

## Learning flow

1. Java's numeric model;
2. integer overflow;
3. floating-point;
4. `BigInteger`;
5. `BigDecimal`;
6. precision and scale;
7. rounding;
8. `BigDecimal` comparison;
9. `Math`;
10. `Random`;
11. `SecureRandom`;
12. a final numeric decision model.

## Mental model to retain

No single numeric type fits every problem. Primitive integers have fixed ranges; floating point uses binary approximation; `BigDecimal` fits decimal arithmetic where scale and rounding are part of the contract; security-sensitive randomness requires `SecureRandom`.

The module focuses on representation semantics and choosing the appropriate numeric model rather than turning every calculation into arbitrary-precision arithmetic.
