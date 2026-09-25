# Math API

## <a id="math-core-functions">Core Math functions</a>
`Math` provides common numeric functions such as absolute value, min/max, powers, roots, logarithms, trigonometry, rounding helpers, and conversions. These methods operate mainly on primitive numeric types and inherit their overflow/floating-point semantics.

## <a id="exact-arithmetic-methods">Exact integer arithmetic helpers</a>
Methods such as `addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact`, `negateExact`, and exact narrowing conversions turn silent integer overflow into `ArithmeticException`. Use them when overflow violates the domain contract.

## <a id="strictmath-boundary">Math vs StrictMath boundary</a>
Modern Java specifies `Math` results tightly and many methods delegate to intrinsified/native implementations. `StrictMath` historically exists for fully reproducible algorithms across platforms. Treat this distinction as a numerical reproducibility boundary, not a reason to replace normal `Math` usage blindly.
