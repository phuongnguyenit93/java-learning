# Comparing BigDecimal

`BigDecimal` supports two different questions: **is the representation equal?** and **is the numerical value equal?**

## <a id="big-decimal-equals">equals Includes Scale</a>

`BigDecimal.equals` considers both value and scale:

```java
new BigDecimal("1.0").equals(new BigDecimal("1.00")) // false
```

The values represent the same numerical quantity but not the same representation.

This matters in hash collections because `equals` and `hashCode` use that representation-sensitive equality.

## <a id="big-decimal-compareto">compareTo Uses Numerical Value</a>

`compareTo` compares numerical value:

```java
new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0
```

```text
equals
→ value + scale

compareTo
→ numerical ordering
```

This is a famous exception to the usual recommendation that `compareTo == 0` should agree with `equals`.

## <a id="big-decimal-collections">Collection Consequences</a>

`HashSet<BigDecimal>` uses `equals/hashCode`, while `TreeSet<BigDecimal>` uses ordering by default.

Therefore `1.0` and `1.00` may be treated as two distinct hash values but the same sorted key.

Choose normalization and comparison policy deliberately if `BigDecimal` is a domain key.

The next chapter returns to standard numeric helper APIs in `Math`.
