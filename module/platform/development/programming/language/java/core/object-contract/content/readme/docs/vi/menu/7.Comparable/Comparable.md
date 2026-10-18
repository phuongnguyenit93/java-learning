# Thứ tự tự nhiên với Comparable

Nếu một kiểu dữ liệu có **một thứ tự tự nhiên rõ ràng**, chính kiểu đó có thể công bố cách so sánh thông qua `Comparable<T>`.

`Comparable` không chỉ phục vụ `Collections.sort`. Quy tắc về thứ tự còn ảnh hưởng tới các thuật toán tìm kiếm nhị phân, `TreeSet`, `TreeMap` và mọi API dựa trên thứ tự tự nhiên.

## <a id="natural-order">Thứ tự tự nhiên</a>

`Comparable<T>` yêu cầu kiểu dữ liệu cung cấp:

```java
int compareTo(T other);
```

`Comparable` công bố một **thứ tự toàn phần (total order)** cho các giá trị hợp lệ của kiểu dữ liệu: với hai giá trị có thể so sánh, quan hệ thứ tự phải xác định chúng đứng trước, đứng sau hoặc cùng vị trí. Thứ tự tự nhiên nên là cách sắp xếp mặc định mà người dùng của kiểu dữ liệu có thể dự đoán hợp lý.

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

Nếu một kiểu dữ liệu có nhiều cách sắp xếp ngang nhau về ý nghĩa, việc tùy ý chọn một cách làm thứ tự tự nhiên có thể gây bất ngờ.

Ví dụ `Book` có thể sắp xếp theo tiêu đề, ngày xuất bản, giá hoặc đánh giá. Không cách nào hiển nhiên là “bản chất tự nhiên duy nhất” của `Book`. Khi đó `Comparator` thường phù hợp hơn.

## <a id="compareto-contract">Quy tắc của compareTo</a>

Dấu của kết quả `compareTo` mang ý nghĩa:

```text
< 0  → this đứng trước other
  0  → cùng vị trí theo thứ tự
> 0  → this đứng sau other
```

Đoạn mã sử dụng chỉ nên dựa vào **dấu**, không dựa vào giá trị cụ thể như `-1`, `0`, `1`.

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

thì quan hệ thứ tự phải cho:

```text
a < c
```

Nếu quan hệ so sánh không có tính bắc cầu, thuật toán sắp xếp và cấu trúc cây không còn một thứ tự nhất quán để dựa vào.

### TÍNH NHẤT QUÁN CỦA THỨ TỰ

Nếu hai đối tượng không thay đổi trạng thái liên quan tới thứ tự, kết quả so sánh không nên thay đổi ngẫu nhiên theo thời gian hoặc theo trạng thái bên ngoài.

Quy tắc còn cần một tính chất tinh tế nhưng quan trọng: nếu `a.compareTo(b) == 0`, thì `a` và `b` phải có cùng quan hệ thứ tự đối với một phần tử thứ ba `c`.

```text
a.compareTo(b) == 0
        ↓
sign(a.compareTo(c))
phải giống
sign(b.compareTo(c))
```

Nếu không, hai giá trị vừa được tuyên bố là cùng vị trí trong thứ tự lại hành xử khác nhau khi đặt cạnh phần tử thứ ba, khiến quan hệ thứ tự không còn nhất quán.

### ĐỪNG SO SÁNH BẰNG PHÉP TRỪ

Không nên viết:

```java
return this.id - other.id;
```

vì tràn số nguyên có thể đảo dấu kết quả.

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

hoặc các phương thức hỗ trợ của `Comparator` sẽ biểu đạt ý định an toàn hơn.

### `compareTo(null)`

Theo quy tắc của `Comparable`, gọi `x.compareTo(null)` phải ném `NullPointerException` dù `x.equals(null)` trả `false`. Thứ tự tự nhiên không dùng `null` như một giá trị ngang hàng hợp lệ. Nếu đoạn mã sử dụng cần chính sách “null đứng trước/sau”, đó là việc phù hợp hơn cho `Comparator.nullsFirst` hoặc `Comparator.nullsLast`.

## <a id="compareto-equals-consistency">compareTo và equals</a>

Khi thứ tự tự nhiên **nhất quán với `equals`**, hai biểu thức sau phải có cùng giá trị `boolean`:

```text
(compareTo(other) == 0)
        ↕ tương đương
equals(other)
```

Java khuyến nghị mạnh sự nhất quán này, dù bản thân `Comparable` không bắt buộc `compareTo(...) == 0` phải đồng nghĩa với `equals(...) == true`. Lý do không chỉ mang tính thẩm mỹ: cấu trúc dữ liệu có thứ tự dùng phép so sánh để xác định khóa theo thứ tự, trong khi quy tắc chung của `Set`/`Map` được diễn đạt theo `equals`.

### CẤU TRÚC DỮ LIỆU DỰA TRÊN MÃ BĂM VÀ CẤU TRÚC CÂY HIỂU “GIỐNG NHAU” KHÁC NHAU

Mô hình tư duy:

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

thì `HashSet` có thể giữ cả hai trong khi `TreeSet` có thể xem chúng là cùng một khóa theo thứ tự.

Chiều ngược lại cũng gây bất ngờ:

```text
a.equals(b) == true
a.compareTo(b) != 0
```

thì một `Set` có thứ tự có thể giữ cả hai giá trị dù `equals` nói chúng bằng nhau, khiến hành vi của cấu trúc có thứ tự lệch khỏi kỳ vọng thông thường của `Set`.

Ví dụ nổi tiếng là `BigDecimal`:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0
```

Kiểu dữ liệu được phép công bố thứ tự như vậy, nhưng đoạn mã sử dụng phải hiểu hậu quả khi chọn cấu trúc dữ liệu.

Nếu một lớp cố ý để thứ tự tự nhiên không nhất quán với `equals`, hành vi đó nên được ghi rõ trong tài liệu. `TreeSet`/`TreeMap` vẫn có hành vi xác định vì chúng dùng thứ tự để nhận diện khóa, nhưng cấu trúc dữ liệu khi đó có thể không còn tuân thủ đầy đủ quy tắc chung của `Set`/`Map`, nơi khái niệm bằng nhau được diễn đạt bằng `equals`.

### TRẠNG THÁI SẮP XẾP CÓ THỂ THAY ĐỔI

Giống khóa băm có thể thay đổi, việc thay đổi trường dữ liệu tham gia thứ tự khi đối tượng đang nằm trong `TreeSet`/`TreeMap` có thể làm cấu trúc cây không còn khớp với trạng thái thứ tự hiện tại của đối tượng.

Vì vậy khóa dùng trong cấu trúc có thứ tự nên có trạng thái liên quan tới thứ tự đủ ổn định.

Nếu một kiểu dữ liệu cần nhiều cách sắp xếp khác nhau, ta không nên nhét tất cả vào `compareTo`; đó là vai trò của `Comparator`.
