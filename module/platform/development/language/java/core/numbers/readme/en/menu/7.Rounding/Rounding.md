# Rounding

Rounding is required when the exact result cannot or should not be kept in the target representation. In financial/domain code, rounding is often a **business policy**, not merely presentation formatting.

## <a id="rounding-modes">RoundingMode</a>

Common modes include:

```text
UP / DOWN
CEILING / FLOOR
HALF_UP / HALF_DOWN / HALF_EVEN
UNNECESSARY
```

`HALF_EVEN` can reduce aggregate bias in repeated rounding, but it is not automatically the correct policy for every domain.

## <a id="rounding-at-boundaries">Where Rounding Happens</a>

Rounding may happen when:

- reducing scale with `setScale`;
- applying a limited-precision `MathContext`;
- dividing a decimal with no finite result;
- enforcing a business rule for the smallest allowed unit.

Round at intentional boundaries rather than after every operation without a domain reason.

## <a id="financial-rounding-policy">Rounding as Domain Policy</a>

A money/tax/rate policy may need to define:

- line-item vs final-total rounding;
- scale;
- rounding mode;
- intermediate precision.

Without an explicit policy, two implementations can both look reasonable and still disagree.

The next chapter shows another BigDecimal subtlety: equal numerical value does not always mean `equals` returns true.
