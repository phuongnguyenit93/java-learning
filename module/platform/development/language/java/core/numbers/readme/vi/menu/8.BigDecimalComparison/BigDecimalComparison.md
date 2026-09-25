# So sánh BigDecimal

## <a id="big-decimal-equals">BigDecimal equals có tính scale</a>
`BigDecimal.equals` so sánh cả numerical value lẫn representation scale. Vì vậy `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là false. Điều này ảnh hưởng hash-based collection vì `equals/hashCode` cùng tính scale.

## <a id="big-decimal-compareto">compareTo so sánh numerical value</a>
`compareTo` so sánh magnitude và bỏ qua representation scale cho value như `1.0` với `1.00`; nó trả zero. Dùng khi domain hỏi hai decimal quantity có cùng numerical value hay không.

## <a id="big-decimal-collections">Hệ quả với key và sorting</a>
Hash collection dùng `equals/hashCode`, còn sorted collection có thể dùng `compareTo`/Comparator. Vì natural ordering của BigDecimal không consistent với `equals`, `HashSet` và `TreeSet` có thể xử lý scale variant khác nhau. Khi cần key identity ổn định, hãy định nghĩa/normalize representation rõ ràng.
