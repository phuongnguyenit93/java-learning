# So sánh BigDecimal

`BigDecimal` có hai câu hỏi so sánh khác nhau: **cách biểu diễn có giống nhau không** và **numerical value có bằng nhau không**.

## <a id="big-decimal-equals">equals và Scale</a>

`BigDecimal.equals` xét cả value và scale:

```java
new BigDecimal("1.0").equals(new BigDecimal("1.00")) // false
```

Hai value này biểu diễn cùng lượng số học nhưng cách biểu diễn khác nhau.

Điều này quan trọng khi BigDecimal được dùng làm key trong hash collection, vì `equals` và `hashCode` phản ánh cách biểu diễn ngữ nghĩa này.

## <a id="big-decimal-compareto">compareTo và Numerical Value</a>

`compareTo` so numerical value:

```java
new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0
```

Vì vậy:

```text
equals
→ value + scale

compareTo
→ numerical ordering
```

Đây là exception nổi tiếng đối với khuyến nghị `compareTo == 0` nên nhất quán với `equals`.

## <a id="big-decimal-collections">Hệ quả với Collection</a>

`HashSet<BigDecimal>` dựa trên `equals/hashCode`, còn `TreeSet<BigDecimal>` mặc định dựa trên `compareTo`.

Do đó `1.0` và `1.00` có thể được xem là:

```text
hai phần tử khác nhau theo ngữ nghĩa của hash-based collection
nhưng
cùng khóa sắp xếp theo ngữ nghĩa của sorted collection
```

Hãy chọn normalization/comparison chính sách rõ ràng nếu BigDecimal là domain key.

Sau các cách biểu diễn phức tạp, chương tiếp theo quay lại những helper số học chuẩn trong `Math`.
