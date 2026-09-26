# Math and StrictMath

Java already provides many standard numeric helpers. Using them communicates intent clearly and avoids reimplementing subtle edge cases.

## <a id="math-core-functions">Core Math Functions</a>

Useful groups include:

```text
basic selection
→ abs, min, max

power/root
→ pow, sqrt, cbrt

rounding helpers
→ floor, ceil, round, rint

trigonometry/logarithm
→ sin, cos, tan, log, exp

integer helpers
→ floorDiv, floorMod, exact arithmetic

floating-point helpers
→ ulp, nextUp, nextDown, copySign...
```

### `floor`, `ceil`, and `round`

```java
Math.floor(2.7);  // 2.0
Math.ceil(2.1);   // 3.0
Math.round(2.5);  // 3

Math.floor(-2.1); // -3.0
Math.ceil(-2.9);  // -2.0
```

`floor` means toward negative infinity, not simply "remove the decimal part".

### `floorDiv` and `floorMod`

Ordinary integer division truncates toward zero:

```java
System.out.println(-7 / 3); // -2
```

`Math.floorDiv` uses floor-division semantics:

```java
System.out.println(Math.floorDiv(-7, 3)); // -3
```

That distinction matters for algorithms that require mathematical floor behavior with negative numbers.

## <a id="exact-arithmetic-methods">Exact Integer Arithmetic Helpers</a>

Helpers include:

```java
Math.addExact
Math.subtractExact
Math.multiplyExact
Math.incrementExact
Math.decrementExact
Math.negateExact
Math.absExact
Math.divideExact
Math.toIntExact
```

They turn silent overflow into `ArithmeticException`.

```java
long total =
        Math.multiplyExact(
                (long) quantity,
                unitPrice
        );
```

Exact helpers do not create arbitrary precision. If a valid domain value can exceed `long`, `BigInteger` is still the appropriate representation.

On Java 21, `Math.absExact(Integer.MIN_VALUE)` and `Math.divideExact(Integer.MIN_VALUE, -1)` throw `ArithmeticException` instead of returning the silent overflow result produced by their unchecked counterparts.

## <a id="strictmath-boundary">Math vs StrictMath</a>

`Math` is the normal choice for application code.

`StrictMath` exists for cases that require more strictly specified/reproducible behavior for relevant floating-point mathematical functions.

```text
Math
→ default numeric helper API

StrictMath
→ prioritizes specified reproducibility for relevant functions
```

Do not read `StrictMath` as "perfectly exact Math". It still operates on floating-point values and inherits the representation limits of `double/float`.

The boundary matters when cross-platform reproducibility is itself a requirement.

The final two chapters move from number representation to a different contract: **randomness**.
