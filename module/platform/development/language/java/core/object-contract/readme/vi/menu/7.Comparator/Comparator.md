# Comparator

## <a id="external-order">External/custom ordering</a>
`Comparator<T>` định nghĩa ordering bên ngoài compared type, cho phép nhiều sort strategy mà không sửa domain class. Phù hợp với view như by name, date, priority hoặc compound business key.

## <a id="comparator-composition">thenComparing/reversed/null handling</a>
Comparator factory/composition method diễn đạt ordering declarative: `comparing`, primitive-specialized comparator, `thenComparing`, `reversed`, `nullsFirst`, `nullsLast`. Cần compose tie-breaker có chủ ý để distinct value không collapse bất ngờ trong sorted set/map.

## <a id="comparator-contract">Comparator transitivity/consistency</a>
Comparator phải tạo coherent ordering. Comparator non-transitive hoặc phụ thuộc state thay đổi có thể làm sort fail hay sorted collection behave khó đoán. Không nên phụ thuộc mutable state thay đổi khi element đang nằm trong ordered structure.

## <a id="sorting-stability-boundary">Boundary sorting stability</a>
Stable sort giữ encounter order của các element compare equal. Stability quan trọng khi ordering trước đó đóng vai tie-break context. Không assume mọi algorithm/API stable nếu contract không nói, và không nhầm sort stability với comparator consistency.
