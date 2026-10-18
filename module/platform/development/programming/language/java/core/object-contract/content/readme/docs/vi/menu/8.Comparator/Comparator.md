# Thứ tự tùy biến với Comparator

`Comparable` đặt một thứ tự tự nhiên bên trong kiểu dữ liệu. `Comparator<T>` tách chính sách sắp xếp ra **bên ngoài kiểu dữ liệu**, nhờ đó cùng một dữ liệu có thể được nhìn theo nhiều thứ tự khác nhau mà không sửa lớp nghiệp vụ.

Đây là khác biệt thiết kế quan trọng:

```text
Comparable
→ kiểu dữ liệu sở hữu một thứ tự tự nhiên

Comparator
→ đoạn mã sử dụng/ngữ cảnh sở hữu một chính sách sắp xếp
```

## <a id="external-order">Thứ tự tùy chỉnh</a>

Ví dụ cùng một danh sách `Book` có thể được sắp xếp theo:

- tiêu đề;
- ngày xuất bản;
- giá;
- đánh giá;
- nhiều trường dữ liệu kết hợp.

Không nên sửa lớp nghiệp vụ mỗi khi cần thêm một góc nhìn.

```java
Comparator<Book> byTitle =
        Comparator.comparing(Book::title);

Comparator<Book> byPrice =
        Comparator.comparing(Book::price);
```

Chọn `byTitle`, `byPrice` hay một `Comparator` khác chỉ thay đổi **cách đoạn mã sử dụng sắp thứ tự các `Book`**. Nó không viết lại `Book.equals` và cũng không tự thay đổi quy tắc bằng nhau của nghiệp vụ. Thứ tự và phép bằng nhau về mặt logic là hai chính sách riêng, dù khi dùng cấu trúc dữ liệu có thứ tự ta có thể quan sát rõ sự tương tác giữa chúng.

Một `Comparator` đúng cũng phải áp đặt một **thứ tự toàn phần** trên tập giá trị mà nó được thiết kế để so sánh. Nếu chính sách cho phép `null`, chính comparator phải nói rõ `null` nằm ở đâu trong thứ tự đó.

### THỨ TỰ TỰ NHIÊN VÀ GÓC NHÌN NGHIỆP VỤ KHÔNG PHẢI MỘT

Một kiểu dữ liệu có thể có thứ tự tự nhiên nhưng đoạn mã sử dụng vẫn cần comparator khác.

Ví dụ `Version` có thứ tự tự nhiên theo phiên bản ngữ nghĩa, nhưng giao diện người dùng có thể cần hiển thị theo ngày phát hành. `Comparable` không nên bị sửa chỉ để phục vụ một màn hình cụ thể.

## <a id="comparator-composition">Kết hợp Comparator</a>

Java cung cấp các phương thức tạo và kết hợp `Comparator` giúp mô tả chính sách sắp xếp theo từng bước.

```java
Comparator<Book> byTitleThenDate =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt);
```

### TIÊU CHÍ PHÂN ĐỊNH KHI TIÊU CHÍ TRƯỚC CHO KẾT QUẢ 0 (TIE-BREAKER)

Nếu chỉ sắp xếp theo tiêu đề:

```text
Book("Java", 2022)
Book("Java", 2024)
```

comparator có thể trả `0` dù hai quyển sách là hai đối tượng hoặc giá trị nghiệp vụ khác nhau. Với sắp xếp danh sách, điều này chỉ nói chúng cùng vị trí theo comparator. Với `TreeSet`/`TreeMap`, `compare(...) == 0` còn có thể làm chúng được xem như cùng một khóa theo thứ tự.

Vì vậy tiêu chí phân định khi bằng nhau cần được thiết kế có chủ ý:

```java
Comparator<Book> byTitleThenDateThenId =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt)
                  .thenComparing(Book::id);
```

### PHƯƠNG THỨC HỖ TRỢ CHUYÊN BIỆT CHO KIỂU NGUYÊN THỦY

Khi khóa là kiểu nguyên thủy, có thể tránh thao tác đóng hộp (`boxing`) không cần thiết và biểu đạt ý định rõ hơn:

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

Cần đọc biểu thức như một chuỗi chính sách. Đặt `reversed()` ở đâu sẽ quyết định phần nào của thứ tự bị đảo, đặc biệt khi kết hợp nhiều comparator.

Ví dụ hai chính sách sau **không giống nhau**:

```java
// Đảo toàn bộ chuỗi: tiêu đề giảm dần, rồi ngày giảm dần.
Comparator<Book> allDescending =
        Comparator.comparing(Book::title)
                  .thenComparing(Book::publishedAt)
                  .reversed();

// Giữ tiêu đề tăng dần, chỉ đảo tiêu chí ngày dùng để phân định khi tiêu đề bằng nhau.
Comparator<Book> titleAscDateDesc =
        Comparator.comparing(Book::title)
                  .thenComparing(
                          Comparator.comparing(Book::publishedAt).reversed()
                  );
```

Vì vậy phép kết hợp phải thể hiện rõ **tiêu chí nào là chính, tiêu chí nào chỉ dùng khi tiêu chí trước bằng nhau, và chính xác phần nào của chính sách bị đảo chiều**. Đảo toàn bộ `Comparator` đã kết hợp không giống với chỉ đảo một bước so sánh bên trong.

### XỬ LÝ NULL LÀ MỘT CHÍNH SÁCH CẦN NÓI RÕ

```java
Comparator<Book> byTitleNullLast =
        Comparator.comparing(
                Book::title,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
```

`nullsFirst` / `nullsLast` giúp đoạn mã sử dụng nói rõ ngữ nghĩa thay vì để `null` gây lỗi bất ngờ.

## <a id="comparator-contract">Quy tắc của Comparator</a>

Comparator phải tạo ra một quan hệ thứ tự nhất quán.

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

Một comparator kiểu vòng tròn “kéo-búa-bao” không tạo được thứ tự toàn phần hợp lệ cho thuật toán sắp xếp hoặc cấu trúc cây.

### CÁC PHẦN TỬ SO SÁNH BẰNG 0 PHẢI HÀNH XỬ NHẤT QUÁN

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

Quy tắc này giúp nhóm các phần tử “bằng nhau theo thứ tự” thực sự tạo thành một lớp thứ tự nhất quán.

### TÍNH NHẤT QUÁN VỚI PHÉP BẰNG NHAU

Thứ tự do `Comparator` tạo ra được gọi là **nhất quán với `equals`** khi:

```text
(compare(a, b) == 0)
        ↕ tương đương
a.equals(b)
```

Comparator không bắt buộc tuyệt đối phải giữ sự nhất quán này, nhưng nếu phá nó thì đoạn mã sử dụng phải hiểu hậu quả, đặc biệt với `TreeSet` và `TreeMap`. Hai cấu trúc này vẫn có hành vi xác định dựa trên thứ tự, nhưng có thể không còn tuân thủ đầy đủ quy tắc chung của `Set`/`Map` nếu quan hệ “bằng nhau theo thứ tự” không khớp với `equals`.

Hai dạng không nhất quán đã thấy ở `Comparable` vẫn áp dụng khi thứ tự đến từ `Comparator`:

```text
a.equals(b) == false
compare(a, b) == 0

a.equals(b) == true
compare(a, b) != 0
```

Trường hợp đầu có thể làm cấu trúc dữ liệu có thứ tự xem hai giá trị logic khác nhau như cùng một khóa theo thứ tự; trường hợp sau có thể làm nó giữ cả hai giá trị dù `equals` nói chúng bằng nhau. Điểm khác biệt ở đây là chính sách so sánh do đoạn mã sử dụng cung cấp và có thể thay đổi theo ngữ cảnh, thay vì nằm cố định trong kiểu dữ liệu như `Comparable`.

Nếu cố ý định nghĩa thứ tự không nhất quán với `equals`, nên ghi rõ điều đó trong tài liệu để đoạn mã sử dụng không nhầm ngữ nghĩa của cấu trúc dữ liệu có thứ tự với phép bằng nhau về mặt logic của đối tượng.

```java
Set<Book> books = new TreeSet<>(Comparator.comparing(Book::title));
```

Nếu hai quyển sách khác nhau nhưng cùng tiêu đề, comparator trên có thể làm `TreeSet` chỉ giữ một khóa theo thứ tự. Đó không phải lỗi của `TreeSet`; comparator đã nói với cấu trúc dữ liệu rằng chúng bằng nhau theo quan hệ thứ tự.

### TRẠNG THÁI SO SÁNH CÓ THỂ THAY ĐỔI

Comparator dựa trên trường dữ liệu có thể thay đổi có thể làm cấu trúc dữ liệu có thứ tự mất tính nhất quán nếu trường đó thay đổi sau khi phần tử được đưa vào.

```text
đưa vào với price = 10
        ↓
vị trí trong cây dựa trên price 10
        ↓
đổi price = 1000
        ↓
trạng thái đối tượng không còn khớp vị trí cũ trong thứ tự
```

Rủi ro tương tự xảy ra nếu comparator phụ thuộc vào cấu hình bên ngoài có thể thay đổi, thời gian hiện tại hoặc trạng thái bên ngoài thay đổi giữa các lần so sánh. Hãy ưu tiên trạng thái liên quan tới thứ tự và chính sách so sánh ổn định khi đối tượng nằm trong cấu trúc cây.

## <a id="sorting-stability-boundary">Tính ổn định khi sắp xếp</a>

Phép sắp xếp ổn định giữ nguyên thứ tự xuất hiện tương đối của những phần tử mà comparator xem là bằng nhau.

Ví dụ đầu vào:

```text
[A(priority=1), B(priority=1), C(priority=2)]
```

Nếu chỉ sắp xếp theo `priority` và thuật toán/API bảo đảm tính ổn định, `A` vẫn đứng trước `B` sau khi sắp xếp vì comparator xem chúng ngang nhau.

### PHÂN BIỆT HAI QUY ƯỚC

```text
tính ổn định khi sắp xếp
→ thuộc tính của thuật toán/API sắp xếp

tính nhất quán của comparator
→ thuộc tính của hàm xác định thứ tự
```

Comparator tốt không tự bảo đảm phép sắp xếp ổn định. Ngược lại, một thuật toán sắp xếp ổn định cũng không sửa được comparator vi phạm tính bắc cầu.

Không nên giả định mọi API sắp xếp đều ổn định nếu tài liệu API không nói rõ.

Sau khi đã hiểu cả thứ tự tự nhiên lẫn thứ tự do đoạn mã sử dụng định nghĩa, chương cuối sẽ nối phần sắp xếp trở lại với định danh, phép bằng nhau, mã băm, biểu diễn văn bản và rủi ro do trạng thái thay đổi để tạo thành một mô hình quyết định thống nhất cho toàn mô-đun.
