# Comparable

Nếu một type có **một thứ tự tự nhiên rõ ràng**, chính type đó có thể công bố cách so sánh thông qua `Comparable<T>`.

`Comparable` không chỉ phục vụ `Collections.sort`. Ordering contract còn ảnh hưởng tới binary-search-style algorithm, `TreeSet`, `TreeMap` và mọi API dựa trên natural order.

## <a id="natural-order">Thứ tự tự nhiên</a>

`Comparable<T>` yêu cầu type cung cấp:

```java
int compareTo(T other);
```

`Comparable` công bố một **thứ tự toàn phần (total order)** cho các value hợp lệ của type: với hai value có thể so sánh, ordering phải xác định chúng đứng trước, đứng sau hoặc cùng vị trí theo thứ tự. Natural ordering nên là cách sắp xếp mặc định mà người dùng của type có thể dự đoán hợp lý.

Ví dụ một `Version` có thể có thứ tự tự nhiên theo:

```text
major
  ↓ nếu bằng nhau
minor
  ↓ nếu bằng nhau
patch
```

```java
record Version(int major, int minor, int patch)
        implements Comparable<Version> {

    @Override
    public int compareTo(Version other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) return byMajor;

        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) return byMinor;

        return Integer.compare(patch, other.patch);
    }
}
```

### KHI NÀO KHÔNG NÊN CÓ THỨ TỰ TỰ NHIÊN?

Nếu một type có nhiều cách sắp xếp ngang nhau về ý nghĩa, việc chọn ngẫu nhiên một cách làm natural order có thể gây bất ngờ.

Ví dụ `Book` có thể sort theo title, published date, price hoặc rating. Không cách nào hiển nhiên là “bản chất tự nhiên duy nhất” của `Book`. Khi đó `Comparator` thường phù hợp hơn.

## <a id="compareto-contract">Hợp đồng compareTo</a>

Dấu của kết quả `compareTo` mang ý nghĩa:

```text
< 0  → this đứng trước other
  0  → cùng vị trí theo ordering
> 0  → this đứng sau other
```

Caller chỉ nên dựa vào **dấu**, không dựa vào giá trị cụ thể như `-1`, `0`, `1`.

### ĐỐI XỨNG NGƯỢC VỀ DẤU

Nếu:

```text
sign(a.compareTo(b)) < 0
```

thì phải có quan hệ ngược lại:

```text
sign(b.compareTo(a)) > 0
```

### TÍNH BẮC CẦU

Nếu:

```text
a < b
b < c
```

thì ordering phải cho:

```text
a < c
```

Nếu comparator/order không transitive, sorting algorithm và tree-based structure không còn một thứ tự nhất quán để dựa vào.

### TÍNH NHẤT QUÁN CỦA THỨ TỰ

Nếu hai object không đổi ordering-relevant state, kết quả so sánh không nên thay đổi ngẫu nhiên theo thời gian hoặc external state.

Contract còn cần một tính chất tinh tế nhưng quan trọng: nếu `a.compareTo(b) == 0`, thì `a` và `b` phải có cùng quan hệ thứ tự đối với một phần tử thứ ba `c`.

```text
a.compareTo(b) == 0
        ↓
sign(a.compareTo(c))
phải giống
sign(b.compareTo(c))
```

Nếu không, hai value vừa được tuyên bố là cùng vị trí trong ordering lại hành xử khác nhau khi đặt cạnh phần tử thứ ba, khiến ordering không còn nhất quán.

### ĐỪNG SO SÁNH BẰNG PHÉP TRỪ

Không nên viết:

```java
return this.id - other.id;
```

vì integer overflow có thể đảo dấu kết quả.

Ví dụ:

```java
int a = Integer.MAX_VALUE;
int b = -1;

int result = a - b; // overflow
```

Dùng:

```java
Integer.compare(this.id, other.id)
Long.compare(this.id, other.id)
```

hoặc comparator helper sẽ biểu đạt intent an toàn hơn.

### `compareTo(null)`

Theo contract của `Comparable`, gọi `x.compareTo(null)` phải ném `NullPointerException` dù `x.equals(null)` trả `false`. Natural ordering không dùng `null` như một peer value hợp lệ. Nếu caller cần policy “null đứng trước/sau”, đó là việc phù hợp hơn cho `Comparator.nullsFirst` hoặc `Comparator.nullsLast`.

## <a id="compareto-equals-consistency">compareTo và equals</a>

Khi natural ordering **consistent với `equals`**, hai biểu thức sau phải có cùng giá trị boolean:

```text
(compareTo(other) == 0)
        ↕ tương đương
equals(other)
```

Java khuyến nghị mạnh consistency này, dù không bắt buộc. Lý do không chỉ mang tính thẩm mỹ: sorted collection dùng comparison để xác định ordering key, trong khi contract chung của `Set`/`Map` được diễn đạt theo `equals`.

### HASH-BASED VÀ TREE-BASED COLLECTION HIỂU “GIỐNG NHAU” KHÁC NHAU

Mental model:

```text
HashSet / HashMap
→ dựa vào equals + hashCode

TreeSet / TreeMap
→ dựa vào compareTo hoặc Comparator
```

Nếu:

```text
a.equals(b) == false
a.compareTo(b) == 0
```

thì `HashSet` có thể giữ cả hai trong khi `TreeSet` có thể xem chúng là cùng một ordering key.

Chiều ngược lại cũng gây bất ngờ:

```text
a.equals(b) == true
a.compareTo(b) != 0
```

thì một sorted set có thể giữ cả hai value dù logical equality nói chúng bằng nhau, khiến behavior của sorted collection lệch khỏi expectation thông thường của `Set`.

Ví dụ nổi tiếng là `BigDecimal`:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0
```

Điều này hợp lệ vì class công bố semantics của nó, nhưng caller phải hiểu consequence khi chọn collection.

Consistency với `equals` là **khuyến nghị mạnh chứ không phải yêu cầu bắt buộc** của `Comparable`. Nếu một class cố ý để natural ordering không consistent với `equals`, behavior đó nên được document rõ ràng vì sorted collection sẽ dùng ordering semantics chứ không dùng logical equality để xác định key trùng.

### TRẠNG THÁI SẮP XẾP CÓ THỂ THAY ĐỔI

Giống mutable hash key, thay đổi field tham gia ordering khi object đang nằm trong `TreeSet`/`TreeMap` có thể làm cấu trúc cây không còn khớp với ordering state hiện tại của object.

Vì vậy natural-order key tốt thường có ordering-relevant state ổn định.

Nếu một type cần nhiều cách sắp xếp khác nhau, ta không nên nhét tất cả vào `compareTo`; đó là vai trò của `Comparator`.
