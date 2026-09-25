# BigDecimal Comparison

## <a id="big-decimal-equals">BigDecimal equals includes scale</a>
`BigDecimal.equals` compares both numerical value and representation scale. Therefore `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is false. This is intentional and affects hash-based collections because equal/hashCode consistency includes scale.

## <a id="big-decimal-compareto">compareTo numerical comparison</a>
`compareTo` compares numerical magnitude and ignores representational scale for values such as `1.0` and `1.00`; it returns zero for them. Use it when the domain asks whether decimal quantities have the same numerical value.

## <a id="big-decimal-collections">BigDecimal keys/sorting implications</a>
Hash-based collections use `equals/hashCode`, while sorted collections can use `compareTo`/Comparator. Because BigDecimal's natural ordering is inconsistent with `equals`, a `HashSet` and `TreeSet` can treat scale variants differently. Decide and normalize representation when a stable key identity is required.
