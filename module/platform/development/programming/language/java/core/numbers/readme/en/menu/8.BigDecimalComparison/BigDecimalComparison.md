# Comparing BigDecimal

`BigDecimal` supports two different comparison questions:

```text
is the representation equal?
and
is the numerical quantity equal?
```

Confusing those questions often causes bugs in collections and domain-key logic.

## <a id="big-decimal-equals">equals Includes Scale</a>

`BigDecimal.equals` considers both numerical value **and scale**:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b)); // false
```

Their internal representations differ:

```text
a → unscaled 10,  scale 1
b → unscaled 100, scale 2
```

The mathematical quantity is the same, but the representation is not.

### WHY does equals behave this way?

Scale is part of the BigDecimal representation, and `equals/hashCode` preserve that distinction. Therefore the hash code for `1.0` does not have to match the hash code for `1.00`.

## <a id="big-decimal-compareto">compareTo Uses Numerical Value</a>

`compareTo` answers the numerical-ordering question:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b)); // 0
```

Mental model:

```text
equals
→ representation + scale

compareTo
→ numerical ordering
```

This is a well-known exception to the general recommendation that `compareTo == 0` should be consistent with `equals`.

### Comparing with zero

If the intent is numerical zero, this is often clearer:

```java
value.compareTo(BigDecimal.ZERO) == 0
```

because `0.00` is not `equals(BigDecimal.ZERO)` but is numerically zero.

## <a id="big-decimal-collections">Collection Consequences</a>

### HashSet

```java
Set<BigDecimal> values = new HashSet<>();
values.add(new BigDecimal("1.0"));
values.add(new BigDecimal("1.00"));

System.out.println(values.size()); // 2
```

`HashSet` uses `equals/hashCode`.

### TreeSet

```java
Set<BigDecimal> values = new TreeSet<>();
values.add(new BigDecimal("1.0"));
values.add(new BigDecimal("1.00"));

System.out.println(values.size()); // 1
```

`TreeSet` uses natural ordering through `compareTo` by default.

This creates an unusual situation where two collection families can treat the same pair of values differently.

### Normalization is not a universal fix

`stripTrailingZeros()` can canonicalize some values:

```java
BigDecimal normalized =
        new BigDecimal("1.00").stripTrailingZeros();
```

but normalization is itself a policy. It may change the scale, including to a negative scale for some values.

If BigDecimal is a domain key, decide explicitly whether identity is based on **representation** or **numerical quantity**.

The next chapter returns to standard numeric helpers in `Math`.
