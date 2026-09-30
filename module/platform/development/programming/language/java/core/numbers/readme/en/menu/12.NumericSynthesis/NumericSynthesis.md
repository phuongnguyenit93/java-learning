# Choosing the Right Numeric Model

Knowing each numeric API separately is not enough. Real code usually fails because the representation or policy was chosen from habit instead of from the problem requirement.

This chapter combines the module into one decision model:

```text
problem requirement
        ↓
choose a representation
        ↓
identify its failure mode
        ↓
make comparison / overflow / rounding policy explicit
        ↓
check boundaries to neighboring modules
```

The goal is not to memorize one universally "best" type. The goal is to explain **why this representation and these policies are correct for this value**.

## <a id="numeric-decision-model">A Decision Model for Numeric Values</a>

Start from the meaning of the value, not from a Java type name.

```text
Is the value always a whole number?
        │
        ├─ yes → can the valid range fit in int/long?
        │           ├─ yes → fixed-width integer
        │           └─ no  → BigInteger
        │
        └─ no → is exact decimal behavior part of the contract?
                    ├─ yes → BigDecimal
                    └─ no  → float/double, usually double
```

That first choice is only the beginning. Ask the second set of questions immediately:

```text
fixed-width integer
→ what happens if the range is exceeded?
→ is wraparound acceptable or must overflow be detected?

floating point
→ what approximation is acceptable?
→ how is comparison tolerance defined?

BigInteger
→ is the extra allocation/computation cost acceptable?
→ what happens when converting back to primitives?

BigDecimal
→ what do scale and precision mean for this domain?
→ where is rounding allowed?
→ is identity based on representation or numerical quantity?
```

The important habit is to pair every representation with its **known failure mode**.

## <a id="numeric-policy-boundaries">Representation and Policy Are Separate Decisions</a>

Choosing the right type does not finish the design.

For example, this is not a complete requirement:

```text
"Money uses BigDecimal"
```

The domain may still need to define:

```text
input scale
intermediate precision
rounding mode
rounding point
equality/normalization rule
storage boundary
```

Likewise, choosing `long` does not answer whether overflow should wrap, throw, or be prevented by validation. Choosing `double` does not define the comparison tolerance. Choosing `SecureRandom` does not define a cryptographic protocol.

Think in two layers:

```text
representation
→ what values can be represented and with what arithmetic model?

policy
→ what behavior does this application require at risky boundaries?
```

Some concerns belong primarily to neighboring modules:

```text
equals / hashCode / ordering laws
→ Object Contract

hash-based / sorted collection behavior
→ Collection

localized number/currency presentation
→ Localization

keys / IVs / nonces / encryption / signatures
→ Security & Cryptography
```

Numbers should establish the numeric consequence and then hand off the deeper concern instead of duplicating another module.

## <a id="numeric-synthesis-cases">End-to-End Examples</a>

### Counter with a known upper bound

Requirement:

```text
whole number
maximum below 2 billion
overflow would indicate a bug
```

Reasoning:

```text
int
→ range is sufficient
→ arithmetic is exact inside that range
→ validate the bound or use checked arithmetic where overflow is unacceptable
```

### Scientific measurement

Requirement:

```text
fractional values
large dynamic range
small representation error is acceptable
```

Reasoning:

```text
double
→ binary approximation is acceptable
→ comparison uses a domain-defined error strategy
→ NaN / infinity must be handled if inputs or operations can produce them
```

### Monetary calculation

Requirement:

```text
decimal meaning must be preserved
rounding rules are part of the business contract
```

Reasoning:

```text
BigDecimal
→ construct from decimal intent, not an already-rounded binary value
→ define scale / precision / rounding boundaries
→ decide whether equality uses representation or numerical quantity
```

### Integer beyond long

Requirement:

```text
whole number
valid values may exceed long
```

Reasoning:

```text
BigInteger
→ no fixed-width overflow for valid values
→ arithmetic uses methods and returns new objects
→ primitive conversion becomes an explicit narrowing boundary
```

### Security-sensitive random value

Requirement:

```text
an attacker must not be able to predict future output
```

Reasoning:

```text
SecureRandom
→ unpredictability is part of the requirement
→ allow the platform/provider to manage seeding in ordinary application code
→ hand cryptographic protocol design to Security & Cryptography
```

The final checklist for numeric code is therefore:

```text
1. What does the value mean?
2. What range is valid?
3. Must representation be exact, decimal-exact, or only approximate?
4. What failure mode is acceptable?
5. What comparison rule is correct?
6. Where may rounding happen?
7. Does conversion cross a lossy boundary?
8. Is randomness only statistical/reproducible, or security-sensitive?
9. Does the next concern belong to another Java module?
```

If these questions can be answered explicitly, numeric behavior is being designed rather than guessed.
