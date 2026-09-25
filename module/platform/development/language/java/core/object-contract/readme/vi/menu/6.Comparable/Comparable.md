# Comparable

## <a id="natural-order">Natural ordering</a>
`Comparable<T>` cho phép type định nghĩa một natural ordering qua `compareTo`. Natural ordering nên là default sort dễ đoán của value type; nếu có nhiều order hợp lệ ngang nhau thì external `Comparator` thường design tốt hơn.

## <a id="compareto-contract">Contract compareTo</a>
Sign của `compareTo` định nghĩa less/equal/greater và nên giữ sign antisymmetry, transitivity cùng ordering consistency cần thiết cho sort/search structure. Không nên return phép trừ như `this.id - other.id` vì có thể overflow; dùng comparison helper.

## <a id="compareto-equals-consistency">Consistency với equals và sorted collection</a>
Khuyến nghị mạnh `compareTo(x) == 0` consistent với `equals(x)`. Nếu không, sorted set/map có thể xem hai unequal object như cùng ordering key. `BigDecimal` là deliberate exception nổi tiếng nên caller phải hiểu collection semantics.
