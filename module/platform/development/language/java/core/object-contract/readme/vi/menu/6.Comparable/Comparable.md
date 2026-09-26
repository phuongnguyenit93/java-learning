# Comparable

Nếu một type có **một thứ tự tự nhiên rõ ràng**, chính type đó có thể công bố cách so sánh thông qua `Comparable<T>`.

## <a id="natural-order">Thứ tự tự nhiên</a>

`Comparable<T>` yêu cầu type cung cấp:

```java
int compareTo(T other);
```

Natural ordering nên là cách sắp xếp mặc định mà người dùng của type có thể dự đoán hợp lý.

Ví dụ một `Version` có thể có thứ tự tự nhiên theo major/minor/patch. Nếu có nhiều cách sắp xếp ngang nhau về ý nghĩa, `Comparator` thường phù hợp hơn.

## <a id="compareto-contract">Hợp đồng compareTo</a>

Dấu của kết quả `compareTo` mang ý nghĩa:

```text
< 0  → this đứng trước other
  0  → cùng vị trí theo ordering
> 0  → this đứng sau other
```

Ordering phải giữ các tính chất như antisymmetry về dấu, transitivity và consistency cần thiết cho sort/search structure.

Không nên viết:

```java
return this.id - other.id;
```

vì phép trừ có thể overflow. Dùng `Integer.compare`, `Long.compare` hoặc comparator helper an toàn hơn.

## <a id="compareto-equals-consistency">compareTo và equals</a>

Khuyến nghị mạnh là:

```text
compareTo(other) == 0
→ nên phù hợp với equals(other) == true
```

Nếu không, `TreeSet` hoặc `TreeMap` có thể xem hai object mà `equals` cho là khác nhau như cùng một ordering key.

`BigDecimal` là ví dụ nổi tiếng cố ý không hoàn toàn nhất quán ở điểm này, nên bên gọi phải hiểu ngữ nghĩa của collection đang dùng.

Nếu một type cần nhiều cách sắp xếp khác nhau, ta không nên nhét tất cả vào `compareTo`; đó là vai trò của `Comparator`.
