# Comparator

`Comparable` đặt một natural order bên trong type. `Comparator<T>` tách chính sách sắp xếp ra **bên ngoài type**, nhờ đó cùng một dữ liệu có thể được nhìn theo nhiều thứ tự khác nhau mà không sửa domain class.

Đây là khác biệt thiết kế quan trọng:

```text
Comparable
→ type sở hữu một natural order

Comparator
→ caller/context sở hữu một ordering policy
```

## <a id="external-order">Thứ tự tùy chỉnh</a>

Ví dụ cùng một danh sách `Book` có thể được sắp xếp theo:

- title;
- publication date;
- price;
- rating;
- nhiều field kết hợp.

Không nên sửa domain class mỗi khi cần thêm một góc nhìn.

```java
Comparator<Book> byTitle =
        Comparator.comparing(Book::title);

Comparator<Book> byPrice =
        Comparator.comparing(Book::price);
```

Một `Comparator` đúng cũng phải áp đặt một **thứ tự toàn phần** trên tập value mà nó được thiết kế để so sánh. Nếu policy cho phép `null`, chính comparator phải nói rõ `null` nằm ở đâu trong thứ tự đó.

### THỨ TỰ TỰ NHIÊN VÀ GÓC NHÌN NGHIỆP VỤ KHÔNG PHẢI MỘT

Một type có thể có natural order nhưng caller vẫn cần comparator khác.

Ví dụ `Version` có natural order theo semantic version, nhưng UI có thể cần hiển thị theo release date. `Comparable` không nên bị sửa chỉ để phục vụ một screen cụ thể.

## <a id="comparator-composition">Kết hợp Comparator</a>

Java cung cấp factory/composition helper giúp mô tả ordering policy theo từng bước.

```java
Comparator<Book> byTitleThenDate =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt);
```

### TIÊU CHÍ PHÂN ĐỊNH KHI BẰNG NHAU (TIE-BREAKER)

Nếu chỉ sort theo title:

```text
Book("Java", 2022)
Book("Java", 2024)
```

comparator có thể trả `0` dù hai book là object/domain value khác nhau. Với list sorting, điều này chỉ nói chúng cùng vị trí theo comparator. Với `TreeSet`/`TreeMap`, `compare(...) == 0` còn có thể làm chúng được xem như cùng ordering key.

Vì vậy tie-breaker cần được thiết kế có chủ ý:

```java
Comparator<Book> byTitleThenDateThenId =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt)
                  .thenComparing(Book::id);
```

### HELPER CHUYÊN BIỆT CHO PRIMITIVE

Khi key là primitive, có thể tránh unnecessary boxing và biểu đạt intent rõ hơn:

```java
Comparator<Book> byPages =
        Comparator.comparingInt(Book::pages);

Comparator<Book> bySales =
        Comparator.comparingLong(Book::sales);

Comparator<Book> byRating =
        Comparator.comparingDouble(Book::rating);
```

### `reversed()` VÀ THỨ TỰ KẾT HỢP

```java
Comparator<Book> newestFirst =
        Comparator.comparing(Book::publishedAt)
                  .reversed();
```

Cần đọc expression như một pipeline policy. Đặt `reversed()` ở đâu ảnh hưởng phần ordering nào bị đảo, đặc biệt khi chain nhiều comparator.

### XỬ LÝ NULL LÀ MỘT POLICY CẦN NÓI RÕ

```java
Comparator<Book> byTitleNullLast =
        Comparator.comparing(
                Book::title,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
```

`nullsFirst` / `nullsLast` giúp caller nói rõ semantics thay vì để null gây lỗi bất ngờ.

## <a id="comparator-contract">Hợp đồng Comparator</a>

Comparator phải tạo ra một ordering nhất quán.

### ĐỐI XỨNG NGƯỢC VỀ DẤU

Nếu:

```text
compare(a, b) < 0
```

thì chiều ngược lại phải cho:

```text
compare(b, a) > 0
```

Tổng quát hơn, dấu của hai phép so sánh đảo đối số phải đối nhau. Nếu không, comparator có thể đồng thời nói “a đứng trước b” và “b cũng đứng trước a”.

### TÍNH BẮC CẦU

Nếu comparator nói:

```text
a < b
b < c
```

thì nó phải duy trì:

```text
a < c
```

Một comparator “rock-paper-scissors” kiểu vòng tròn không tạo được total ordering hợp lệ cho sorting/tree structure.

### CÁC PHẦN TỬ COMPARE BẰNG 0 PHẢI HÀNH XỬ NHẤT QUÁN

Nếu:

```text
compare(a, b) == 0
```

thì với một phần tử thứ ba `c`, dấu của:

```text
compare(a, c)
```

phải nhất quán với dấu của:

```text
compare(b, c)
```

Quy tắc này giúp nhóm các phần tử “bằng nhau theo ordering” thực sự tạo thành một lớp thứ tự nhất quán.

### TÍNH NHẤT QUÁN VỚI EQUALITY

Ordering do `Comparator` tạo ra được gọi là **consistent với `equals`** khi:

```text
(compare(a, b) == 0)
        ↕ tương đương
a.equals(b)
```

Comparator không bắt buộc tuyệt đối phải giữ consistency này, nhưng nếu phá nó thì caller phải hiểu hậu quả, đặc biệt với `TreeSet` và `TreeMap`.

Case thứ nhất:

```text
a.equals(b) == false
compare(a, b) == 0
```

sorted collection có thể xem hai logical value khác nhau như cùng ordering key.

Case ngược lại:

```text
a.equals(b) == true
compare(a, b) != 0
```

sorted collection có thể giữ cả hai value dù `equals` nói chúng bằng nhau.

Nếu cố ý định nghĩa ordering không consistent với `equals`, nên document điều đó rõ ràng để caller không nhầm semantics của sorted collection với logical equality của object.

```java
Set<Book> books = new TreeSet<>(Comparator.comparing(Book::title));
```

Nếu hai book khác nhau nhưng cùng title, comparator trên có thể làm `TreeSet` chỉ giữ một ordering key. Đó không phải bug của `TreeSet`; comparator đã nói với collection rằng chúng bằng nhau theo ordering.

### TRẠNG THÁI SO SÁNH CÓ THỂ THAY ĐỔI

Comparator dựa trên field mutable có thể làm ordered collection mất tính nhất quán nếu field thay đổi sau insertion.

```text
insert với price = 10
        ↓
tree placement theo price 10
        ↓
mutate price = 1000
        ↓
object state không còn khớp vị trí ordering cũ
```

Rủi ro tương tự xảy ra nếu comparator phụ thuộc vào mutable external configuration, thời gian hiện tại hoặc state bên ngoài có thể thay đổi giữa các lần compare. Hãy ưu tiên ordering-relevant state và comparison policy ổn định khi object nằm trong tree-based structure.

## <a id="sorting-stability-boundary">Tính ổn định khi sắp xếp</a>

Stable sort giữ nguyên encounter order tương đối của những phần tử mà comparator xem là bằng nhau.

Ví dụ input:

```text
[A(priority=1), B(priority=1), C(priority=2)]
```

Nếu sort chỉ theo `priority` và thuật toán/API có stable guarantee, `A` vẫn đứng trước `B` sau khi sort vì comparator xem chúng tie.

### PHÂN BIỆT HAI HỢP ĐỒNG

```text
sort stability
→ property của sorting algorithm/API

comparator consistency
→ property của ordering function
```

Comparator tốt không tự bảo đảm sort stable. Ngược lại, stable sorting không sửa được comparator sai transitivity.

Không nên giả định mọi API sorting đều ổn định nếu contract của API không nói rõ.

### MÔ HÌNH TƯ DUY TOÀN MODULE

Sau module này, hãy giữ mô hình sau:

```text
==
→ same reference identity?

equals
→ same logical value/entity?

hashCode
→ hash-based lookup signal consistent with equals

toString
→ useful diagnostic representation

Comparable
→ one natural ordering owned by the type

Comparator
→ external/alternative ordering policy
```

Các method/interface này không chỉ là code IDE có thể generate. Chúng là **hợp đồng mà collection, algorithm, log/tooling và code gọi phía ngoài tin tưởng**.
