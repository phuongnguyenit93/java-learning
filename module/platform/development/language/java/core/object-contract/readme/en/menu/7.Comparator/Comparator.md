# Comparator

## <a id="external-order">External/custom ordering</a>
`Comparator<T>` defines ordering outside the compared type, allowing multiple sort strategies without changing domain classes. This is ideal for views such as by name, date, priority, or compound business keys.

## <a id="comparator-composition">thenComparing/reversed/null handling</a>
Comparator factory/composition methods express ordering declaratively: `comparing`, primitive-specialized comparators, `thenComparing`, `reversed`, `nullsFirst`, and `nullsLast`. Compose tie-breakers deliberately so distinct values do not collapse unexpectedly in sorted sets/maps.

## <a id="comparator-contract">Comparator transitivity/consistency</a>
A comparator must provide a coherent ordering. Non-transitive or state-changing comparators can make sorting fail or sorted collections behave unpredictably. The comparator should not depend on mutable state that changes while elements are stored in an ordered structure.

## <a id="sorting-stability-boundary">Sorting behavior and stability boundary</a>
A stable sort preserves the encounter order of elements that compare equal. Stability matters when earlier ordering stages act as tie-break context. Do not infer stability for every algorithm/API without checking its contract, and do not confuse sort stability with comparator consistency.
