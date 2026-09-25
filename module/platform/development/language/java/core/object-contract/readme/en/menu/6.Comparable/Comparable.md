# Comparable

## <a id="natural-order">Natural ordering</a>
`Comparable<T>` lets a type define one natural ordering through `compareTo`. Natural ordering should represent the default, unsurprising sort for that value type; if several equally valid orders exist, external `Comparator`s may be a better design.

## <a id="compareto-contract">compareTo ordering contract</a>
The sign of `compareTo` defines less/equal/greater ordering and should be antisymmetric in sign, transitive, and consistent enough for sorting/search structures. Returning arbitrary subtraction such as `this.id - other.id` can overflow; use comparison helpers.

## <a id="compareto-equals-consistency">Consistency with equals and sorted collections</a>
It is strongly recommended that `compareTo(x) == 0` agree with `equals(x)`. When it does not, sorted sets/maps may treat two unequal objects as one ordering key. `BigDecimal` is a well-known deliberate exception, so callers must understand the collection semantics.
