# Math and StrictMath

Java already provides many standard numeric helpers. Using them often communicates intent better and avoids reimplementing subtle edge cases.

## <a id="math-core-functions">Core Math Functions</a>

`Math` includes operations such as:

- `abs`, `min`, `max`;
- `sqrt`, `pow`;
- `floor`, `ceil`, `round`;
- trigonometric/logarithmic functions;
- exact integer helpers;
- floating-point utilities.

`Math` does not make floating-point arithmetic exact; `double`/`float` representation rules still apply.

## <a id="exact-arithmetic-methods">Exact Integer Arithmetic Helpers</a>

Methods such as `addExact`, `subtractExact`, `multiplyExact`, and `incrementExact` turn silent integer overflow into `ArithmeticException`.

They are appropriate when overflow represents invalid state or a failed contract.

## <a id="strictmath-boundary">Math vs StrictMath</a>

`StrictMath` prioritizes strictly specified/reproducible behavior for certain floating-point mathematical functions.

For ordinary application code, `Math` is usually the default. The boundary matters when cross-platform reproducibility of mathematical results is itself a requirement.

The final two chapters move from number representation to randomness contracts.
