# Floating-Point Numbers

## <a id="binary-floating-point">Binary floating-point representation</a>
`float` and `double` represent values as sign, significand, and exponent in binary IEEE 754 form. Many simple decimal fractions have repeating binary expansions and therefore cannot be represented exactly.

## <a id="precision-rounding-error">Precision and rounding error</a>
Operations round to the nearest representable floating-point value. Small representation errors can accumulate, so `0.1 + 0.2` is not exactly decimal `0.3`. This is expected behavior, not JVM randomness.

## <a id="nan-infinity-negative-zero">NaN, infinity and negative zero</a>
Floating-point includes positive/negative infinity, NaN, and signed zero. NaN is unordered: comparisons such as `x == Double.NaN` are wrong; use `Double.isNaN`. `0.0 == -0.0` is true, but some operations and wrapper comparison/hash semantics can distinguish their bit patterns.

## <a id="floating-point-comparison">Floating-point comparison strategies</a>
Exact `==` is appropriate when exact binary identity is the contract, not as a universal test for computed measurements. Domain comparisons often use an absolute/relative tolerance chosen from the problem scale. Never invent a single epsilon that is correct for every magnitude and domain.
