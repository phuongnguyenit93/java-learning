# Rounding

Rounding appears when the exact result cannot or should not remain in the target representation. In financial/domain code, rounding is a **business policy**, not merely a formatting detail.

## <a id="rounding-modes">RoundingMode</a>

### WHAT — what decision does each mode make?

When digits are discarded, Java needs a direction for the result.

| Mode | Mental model |
| --- | --- |
| `UP` | away from zero |
| `DOWN` | toward zero |
| `CEILING` | toward +∞ |
| `FLOOR` | toward -∞ |
| `HALF_UP` | nearest; tie → away from zero |
| `HALF_DOWN` | nearest; tie → toward zero |
| `HALF_EVEN` | nearest; tie → choose the neighbor with an even last digit |
| `UNNECESSARY` | rounding is forbidden; fail if it would be required |

Here a **tie** means the exact value is exactly halfway between the two rounding candidates, such as `2.5` when rounding to an integer. Outside a tie, all `HALF_*` modes choose the nearer candidate.

### WHY are UP/DOWN different from CEILING/FLOOR?

They can look similar for positive values but diverge for negative values:

```text
value = -2.1

UP      → -3   (away from zero)
DOWN    → -2   (toward zero)
CEILING → -2   (toward +∞)
FLOOR   → -3   (toward -∞)
```

Choose a mode from its mathematical direction and the domain policy, not from the name alone.

### Tie cases

```text
 2.5

HALF_UP   → 3
HALF_DOWN → 2
HALF_EVEN → 2

 3.5

HALF_UP   → 4
HALF_DOWN → 3
HALF_EVEN → 4
```

`HALF_EVEN` can reduce aggregate bias in some workloads with repeated ties, but it is not automatically correct for every domain.

The same tie rules apply to negative values according to each mode's direction. For example, `-2.5` becomes `-3` with `HALF_UP`, while `HALF_DOWN` and `HALF_EVEN` produce `-2`.

## <a id="rounding-at-boundaries">Where Rounding Happens</a>

Rounding may happen when:

- reducing scale with `setScale`;
- using a limited-precision `MathContext`;
- dividing a decimal with no terminating representation;
- converting a result to a fixed domain unit;
- applying legal/business rules.

```java
BigDecimal tax =
        new BigDecimal("10.235")
                .setScale(2, RoundingMode.HALF_UP);

System.out.println(tax); // 10.24
```

### Round at deliberate boundaries

```text
price × quantity
        ↓
subtotal
        ↓
discount
        ↓
tax
        ↓
final payable amount
```

Rounding after every operation can produce a different result from keeping intermediate precision and rounding only at the final business boundary.

A policy must answer:

```text
what is rounded?
when is it rounded?
to which scale?
with which mode?
```

### Double rounding

Rounding twice can differ from rounding directly to the final target:

```text
2.445

round to 2 decimals HALF_UP
→ 2.45

then to 1 decimal HALF_UP
→ 2.5

but directly to 1 decimal from 2.445
→ 2.4
```

Intermediate rounding therefore needs an explicit domain reason.

## <a id="financial-rounding-policy">Rounding as Domain Policy</a>

A money/tax/rate policy often needs to define:

- currency scale;
- line-item vs final-total rounding;
- rounding mode;
- intermediate precision;
- what happens when exact representation at the target scale is impossible.

Two valid-looking implementations can disagree:

```text
Policy A
→ round every line item
→ sum rounded values

Policy B
→ sum exact/intermediate values
→ round the final total
```

### `UNNECESSARY` as an assertion

```java
BigDecimal exact =
        new BigDecimal("10.00")
                .setScale(2, RoundingMode.UNNECESSARY);
```

`UNNECESSARY` is useful when the contract says rounding must not happen. Java throws `ArithmeticException` if digits would need to be discarded.

The next chapter covers another BigDecimal subtlety: equal numerical quantity does not always mean `equals` returns true.
