# Comparator

`Comparable` đặt một natural order bên trong type. `Comparator<T>` tách chính sách sắp xếp ra **bên ngoài type**, nhờ đó cùng một dữ liệu có thể được nhìn theo nhiều thứ tự khác nhau.

## <a id="external-order">Thứ tự tùy chỉnh</a>

Ví dụ cùng một danh sách `Book` có thể được sắp xếp theo:

- title;
- publication date;
- price;
- priority;
- nhiều field kết hợp.

Không nên sửa domain class mỗi khi cần thêm một góc nhìn. `Comparator` cho phép định nghĩa ordering ở nơi sử dụng.

## <a id="comparator-composition">Kết hợp Comparator</a>

Java cung cấp nhiều factory/composition helper:

```java
Comparator.comparing(Book::title)
          .thenComparing(Book::publishedAt)
          .reversed();
```

Cùng với:

- primitive-specialized comparator;
- `thenComparing`;
- `reversed`;
- `nullsFirst`;
- `nullsLast`.

Tie-breaker nên được thiết kế có chủ ý, đặc biệt khi comparator được dùng trong `TreeSet`/`TreeMap`, nơi `compare(...) == 0` có thể khiến hai giá trị bị xem như cùng ordering key.

## <a id="comparator-contract">Hợp đồng Comparator</a>

Comparator phải tạo ra một ordering nhất quán.

Comparator không transitive hoặc phụ thuộc vào mutable trạng thái đang thay đổi có thể làm sorting cho kết quả khó đoán hoặc làm ordered collection hoạt động sai kỳ vọng.

Nếu một field tham gia comparator, hạn chế thay đổi field đó khi object đang nằm trong ordered structure.

## <a id="sorting-stability-boundary">Tính ổn định khi sắp xếp</a>

ổn định sort giữ nguyên encounter order tương đối của những phần tử mà comparator xem là bằng nhau.

Stability hữu ích khi một lần sắp xếp trước đó đã tạo tie-break ngữ cảnh. Nhưng cần phân biệt:

```text
sort stability
→ property của sorting algorithm/API

comparator consistency
→ property của ordering function
```

Không nên giả định mọi API sorting đều ổn định nếu hợp đồng không nói rõ.

Sau module này, hãy nhớ: `equals`, `hashCode`, `Comparable` và `Comparator` không chỉ là method để IDE generate. Chúng là **hợp đồng mà collection và thuật toán Java tin tưởng**.
