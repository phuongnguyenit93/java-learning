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

Một `Comparator<T>` là một quy tắc nhận **hai giá trị kiểu `T`** và trả về số âm, `0` hoặc số dương để nói giá trị thứ nhất đứng trước, ngang hàng hay đứng sau giá trị thứ hai. Khác với `compareTo` của `Comparable`, quy tắc này được truyền từ **nơi cần sắp xếp**, nên lớp `Book` không phải chọn một thứ tự mặc định cho mọi tình huống.

Ví dụ cùng một danh sách `Book` có thể được sắp xếp theo:

- tiêu đề;
- ngày xuất bản;
- giá;
- đánh giá;
- nhiều trường dữ liệu kết hợp.

Không nên sửa lớp nghiệp vụ mỗi khi cần thêm một góc nhìn. Chạy ví dụ Java 21 sau:

```java
import java.util.Comparator;
import java.util.List;

public class ComparatorViews {
    record Book(int id, String title, int year, int pages) {}

    public static void main(String[] args) {
        List<Book> books = List.of(
                new Book(1, "Java", 2022, 300),
                new Book(2, "SQL", 2018, 220),
                new Book(3, "Docker", 2024, 180)
        );

        Comparator<Book> byTitle = Comparator.comparing(Book::title);
        Comparator<Book> byYear = Comparator.comparingInt(Book::year);

        System.out.println(books.stream().sorted(byTitle).map(Book::title).toList());
        System.out.println(books.stream().sorted(byYear).map(Book::title).toList());
        System.out.println(books.stream().map(Book::title).toList());
    }
}
```

Kết quả:

```text
[Docker, Java, SQL]
[SQL, Java, Docker]
[Java, SQL, Docker]
```

`Book::title` lấy khóa là chuỗi tiêu đề, còn `Book::year` lấy khóa là năm xuất bản. Với `byTitle`, “Docker” đứng trước “Java”; với `byYear`, năm `2018` đưa “SQL” lên đầu. Hai lần `sorted(...)` tạo kết quả theo hai chính sách khác nhau; danh sách đầu vào vẫn giữ thứ tự ban đầu như dòng thứ ba. Nếu chọn `List.sort(comparator)` thì chính danh sách đó sẽ được sắp xếp tại chỗ.

Chọn `byTitle`, `byYear` hay một `Comparator` khác chỉ thay đổi **cách đoạn mã sử dụng sắp thứ tự các `Book`**. Nó không viết lại `Book.equals` và cũng không tự thay đổi quy tắc bằng nhau của nghiệp vụ. Với `record Book` ở trên, `equals` vẫn xét các thành phần của bản ghi; quy tắc sắp theo tiêu đề không làm thay đổi điều đó.

Một `Comparator` đúng phải tạo ra quan hệ thứ tự toàn phần nhất quán trên các giá trị mà nó chấp nhận: mọi cặp hợp lệ đều so sánh được, và kết quả `0` cho biết **ngang hàng theo thứ tự này**, không nhất thiết là bằng nhau theo `equals`. Nếu chính sách cho phép `null`, cần xác định rõ `null` nằm ở đâu.

### THỨ TỰ TỰ NHIÊN VÀ GÓC NHÌN NGHIỆP VỤ KHÔNG PHẢI MỘT

Một kiểu dữ liệu có thể có thứ tự tự nhiên nhưng đoạn mã sử dụng vẫn cần comparator khác. Ví dụ `Version` có thể so sánh tự nhiên theo `major → minor → patch`, trong khi màn hình phát hành sắp các phiên bản theo ngày ra mắt. `Comparable` trả lời “kiểu dữ liệu có thứ tự mặc định nào?”, còn `Comparator` trả lời “ngữ cảnh này cần nhìn dữ liệu theo thứ tự nào?”. Hai cơ chế có thể cùng tồn tại.

## <a id="comparator-composition">Kết hợp Comparator</a>

Khi chỉ một tiêu chí không đủ để quyết định thứ tự, Java cho phép **nối các tiêu chí ưu tiên**. `thenComparing` chỉ chạy tiêu chí sau **nếu tiêu chí trước trả về `0`**. Các đoạn Java ngắn trong chương này là **những phần bổ sung**, không phải chương trình độc lập: để chạy, hãy đặt chúng bên trong `main` của lớp `ComparatorViews` ở ví dụ trước, sau phần khai báo `books`, `byTitle`, `byYear`; giữ `record Book` nằm trong lớp đó. Mỗi đoạn dùng lại các biến đã khai báo ở những đoạn ngay trước nó.

```java
Comparator<Book> byTitleThenYear =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year);
```

### TIÊU CHÍ PHÂN ĐỊNH KHI TIÊU CHÍ TRƯỚC CHO KẾT QUẢ 0 (TIE-BREAKER)

Xét hai giá trị dùng `record Book` ở phần trước. Đặt đoạn này sau khai báo `byTitle` đã có trong `main` (không khai báo lại `byTitle`):

```java
Book first = new Book(1, "Java", 2022, 300);
Book second = new Book(2, "Java", 2024, 220);

System.out.println(first.equals(second));           // false
System.out.println(byTitle.compare(first, second));  // 0
```

Hai sách cùng tiêu đề nên `byTitle` trả `0`, dù `id` và năm khác nhau nên `equals` trả `false`. Khi nối `thenComparingInt(Book::year)`, phép so sánh đầu tiên trả `0` và Java chuyển sang so năm: `2022 < 2024`, nên `first` đứng trước `second`. Nếu tiêu đề khác nhau, tiêu chí năm **không được xét**.

Với sắp xếp danh sách, hai phần tử ngang hàng vẫn có thể cùng xuất hiện. Nhưng `TreeSet`/`TreeMap` dùng `compare(...) == 0` để nhận ra **cùng một khóa theo thứ tự**:

```java
var withoutTieBreaker = new java.util.TreeSet<>(byTitle);
System.out.println(withoutTieBreaker.add(first));   // true
System.out.println(withoutTieBreaker.add(second));  // false
System.out.println(withoutTieBreaker.size());       // 1

Comparator<Book> byTitleThenYearThenId =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year)
                  .thenComparingInt(Book::id);

var withTieBreaker = new java.util.TreeSet<>(byTitleThenYearThenId);
withTieBreaker.add(first);
withTieBreaker.add(second);
System.out.println(withTieBreaker.size());          // 2
```

Ở lần thêm thứ hai, `TreeSet` so tiêu đề và nhận `0` nên không thêm sách mới. Khi có tiêu chí năm (và `id` để tiếp tục phân định các trường hợp trùng năm), hai sách so sánh khác `0` nên cùng được lưu. Chỉ thêm tie-breaker nếu miền nghiệp vụ **muốn** phân biệt các khóa đó; mỗi tiêu chí bổ sung sẽ thay đổi cách cấu trúc có thứ tự nhận diện khóa.

### PHƯƠNG THỨC HỖ TRỢ CHUYÊN BIỆT CHO KIỂU NGUYÊN THỦY

Khi khóa là kiểu nguyên thủy, có thể tránh thao tác đóng hộp (`boxing`) không cần thiết và biểu đạt ý định rõ hơn:

```java
Comparator<Book> byPages =
        Comparator.comparingInt(Book::pages);
```

`comparingLong` và `comparingDouble` phục vụ khóa kiểu `long` và `double` tương ứng. Chọn phương thức đúng với kiểu dữ liệu; đừng viết phép trừ hai khóa số để tự tạo kết quả so sánh.

### `reversed()` VÀ THỨ TỰ KẾT HỢP

Cần đọc biểu thức như một chuỗi chính sách. `reversed()` đảo dấu hướng so sánh của **toàn bộ comparator mà nó được gọi trên**. Đặt nó ở đâu sẽ quyết định phần nào của thứ tự bị đảo.

Ví dụ hai chính sách sau **không giống nhau**:

```java
// Đảo toàn bộ chuỗi: tiêu đề giảm dần, rồi năm giảm dần.
Comparator<Book> allDescending =
        Comparator.comparing(Book::title)
                  .thenComparingInt(Book::year)
                  .reversed();

// Giữ tiêu đề tăng dần, chỉ đảo tiêu chí năm khi tiêu đề bằng nhau.
Comparator<Book> titleAscYearDesc =
        Comparator.comparing(Book::title)
                  .thenComparing(
                          Comparator.comparingInt(Book::year).reversed()
                  );
```

Cho dữ liệu `Java#1 (2022)`, `Java#2 (2024)`, `SQL#3 (2021)`, `SQL#4 (2023)` theo đúng `id` trong `Book`, hai biểu thức tạo kết quả:

```text
allDescending    → [SQL#4, SQL#3, Java#2, Java#1]
titleAscYearDesc → [Java#2, Java#1, SQL#4, SQL#3]
```

Trong cả hai cách, sách cùng tiêu đề có năm mới đứng trước; nhưng chỉ cách đầu đảo cả thứ tự tiêu đề, nên nhóm “SQL” đứng trước “Java”. Muốn “mới nhất trước” bất kể tiêu đề, có thể dùng `Comparator.comparingInt(Book::year).reversed()`.

### XỬ LÝ NULL LÀ MỘT CHÍNH SÁCH CẦN NÓI RÕ

```java
Comparator<Book> byTitleNullLast =
        Comparator.comparing(
                Book::title,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
```

Ở đây `nullsLast` áp dụng lên **giá trị `title`**: một `Book` có tiêu đề `null` được đặt sau sách có tiêu đề khác `null`. Nó **không** tự cho phép cả đối tượng `Book` là `null`; nếu danh sách có phần tử `Book` bằng `null`, phải bao toàn bộ comparator bằng `Comparator.nullsLast(byTitleNullLast)`. `nullsFirst` thực hiện chiều ngược lại.

## <a id="comparator-contract">Quy tắc của Comparator</a>

`Comparator.compare(a, b)` dùng dấu của kết quả, tương tự `Comparable.compareTo`: âm nghĩa là `a` đứng trước `b`, `0` nghĩa là hai giá trị ngang hàng theo chính sách này, dương nghĩa là `a` đứng sau `b`. Không cần trả đúng `-1` và `1`; **dấu** mới là hợp đồng mà API sử dụng. Để sắp xếp và tra cứu theo thứ tự hoạt động đáng tin cậy, dấu đó phải tuân thủ các quy tắc sau.

### ĐỐI XỨNG NGƯỢC VỀ DẤU

Nếu:

```text
compare(a, b) < 0
```

thì chiều ngược lại phải cho:

```text
compare(b, a) > 0
```

Tổng quát hơn, dấu của hai phép so sánh đảo đối số phải đối nhau, kể cả khi một chiều trả `0`. Nếu không, comparator có thể đồng thời nói “a đứng trước b” và “b cũng đứng trước a”.

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

### CẠM BẪY TRÀN SỐ KHI SO SÁNH BẰNG PHÉP TRỪ

Một cách viết dễ mắc lỗi là `(a, b) -> a - b` khi khóa là `int`. Phép trừ có thể **tràn miền `int`**, khiến dấu bị đảo. Chạy đoạn Java 21 sau (có thể đặt trong `main`):

```java
Comparator<Integer> broken = (a, b) -> a - b;
Comparator<Integer> correct = Integer::compare;

int high = Integer.MAX_VALUE; // 2147483647
int low = -1;
System.out.println(broken.compare(high, low));
System.out.println(broken.compare(low, high));
System.out.println(correct.compare(high, low));
System.out.println(correct.compare(low, high));
```

Kết quả:

```text
-2147483648
-2147483648
1
-1
```

Theo toán học, `2147483647 - (-1) = 2147483648` và `-1 - 2147483647 = -2147483648`. Nhưng `int` chỉ chứa giá trị từ `-2147483648` đến `2147483647`, nên **phép trừ đầu bị tràn thành số âm**. `broken` tuyên bố `high` đứng trước `low` và `low` cũng đứng trước `high`: vi phạm quy tắc đối dấu.

Tràn số còn có thể phá tính bắc cầu. Với `a = 2147483647`, `b = -1`, `c = 0`, comparator dùng phép trừ cho:

```text
compare(a, b) < 0   // tràn số: a bị xếp trước b
compare(b, c) < 0   // -1 - 0 = -1
compare(a, c) > 0   // 2147483647 - 0 > 0
```

Nó đồng thời nói `a < b` và `b < c`, nhưng `a > c`. Sửa bằng `Integer.compare(a, b)` hoặc `Comparator.comparingInt(...)`; tương tự, với `long` dùng `Long.compare` / `comparingLong`. Đừng trông chờ một thuật toán sắp xếp tự khắc phục được quan hệ so sánh sai.

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

Quy tắc này giúp nhóm các phần tử “bằng nhau theo thứ tự” thực sự tạo thành một lớp thứ tự nhất quán. Với `byTitle` ở phần trước, hai sách “Java” cùng nhận dấu âm khi so với một sách “SQL”, dù `id` hay năm của chúng khác nhau. Nếu một comparator trả `0` cho hai sách nhưng lại đặt một sách trước “SQL” và sách kia sau “SQL”, cấu trúc có thứ tự không thể xem chúng là cùng một khóa một cách đáng tin cậy.

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

Nếu cố ý định nghĩa thứ tự không nhất quán với `equals`, nên ghi rõ điều đó trong tài liệu để đoạn mã sử dụng không nhầm ngữ nghĩa của cấu trúc dữ liệu có thứ tự với phép bằng nhau về mặt logic của đối tượng. Ví dụ hai `Book` ở phần kết hợp comparator có `equals == false` nhưng `byTitle.compare(...) == 0`: `TreeSet` từ chối lần thêm thứ hai, trong khi một `HashSet` có thể giữ cả hai nhờ quy tắc `equals` / `hashCode` của `record`.

Chiều ngược lại cũng có thể xảy ra với comparator phụ thuộc dữ liệu ngoài những trường mà `equals` dùng: hai giá trị được `equals` xem bằng nhau nhưng comparator lại đặt ở hai vị trí khác nhau; `TreeSet` có thể giữ cả hai. Cần phân biệt **bằng nhau về mặt logic** và **ngang hàng theo tiêu chí sắp xếp** trước khi chọn khóa cho `TreeSet` hoặc `TreeMap`.

### TRẠNG THÁI SO SÁNH CÓ THỂ THAY ĐỔI

Comparator dựa trên trường dữ liệu có thể thay đổi có thể làm cấu trúc dữ liệu có thứ tự mất tính nhất quán nếu trường đó thay đổi sau khi phần tử được đưa vào. Đây là rủi ro **theo thời gian**, khác với `compare(...) == 0` khi hai phần tử cùng tiêu chí.

```text
đưa vào với price = 10
        ↓
vị trí trong cây dựa trên price 10
        ↓
đổi price = 1000
        ↓
trạng thái đối tượng không còn khớp vị trí cũ trong thứ tự
```

Khi cây đã đặt khóa ở vị trí tương ứng giá `10`, việc đối tượng tự đổi thành giá `1000` **không tự sắp xếp lại cây**. Lần tìm kiếm tiếp theo có thể đi theo nhánh dựa vào giá mới nhưng cấu trúc cũ vẫn còn, dẫn đến kết quả tra cứu không như mong đợi. Rủi ro tương tự xảy ra nếu comparator phụ thuộc vào cấu hình bên ngoài có thể thay đổi, thời gian hiện tại hoặc trạng thái bên ngoài thay đổi giữa các lần so sánh. Hãy ưu tiên trạng thái liên quan tới thứ tự và chính sách so sánh ổn định khi đối tượng nằm trong cấu trúc cây; nếu phải đổi khóa so sánh, loại phần tử ra, thay đổi rồi thêm lại theo chính sách phù hợp.

## <a id="sorting-stability-boundary">Tính ổn định khi sắp xếp</a>

Phép **sắp xếp ổn định** (*stable sort*) giữ nguyên thứ tự tương đối ban đầu của **những phần tử mà comparator trả `0`**. Đây là thuộc tính của **thuật toán hoặc API sắp xếp**, không phải thuộc tính mà bản thân `Comparator` có thể tự bảo đảm.

Trong Java 21, `List.sort(Comparator)` bảo đảm ổn định. Đoạn sau dùng danh sách công việc có tên và mức ưu tiên (số nhỏ được xếp trước):

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StableSortExample {
    record Task(String name, int priority) {}

    public static void main(String[] args) {
        List<Task> tasks = new ArrayList<>(List.of(
                new Task("C", 2), new Task("B", 1),
                new Task("A", 1), new Task("D", 2)
        ));

        tasks.sort(Comparator.comparingInt(Task::priority));
        System.out.println(tasks.stream().map(Task::name).toList());

        tasks.sort(Comparator.comparingInt(Task::priority)
                             .thenComparing(Task::name));
        System.out.println(tasks.stream().map(Task::name).toList());
    }
}
```

Kết quả:

```text
[B, A, C, D]
[A, B, C, D]
```

Lần sắp xếp thứ nhất đưa mức ưu tiên `1` lên trước mức `2`. Trong từng mức, `B` vẫn trước `A` và `C` vẫn trước `D` đúng như thứ tự đầu vào, vì `compare(B, A) == 0` và `compare(C, D) == 0`. Lần thứ hai bổ sung `thenComparing(Task::name)`, làm `B` và `A` **không còn ngang hàng** nữa: tên “A” phải đứng trước “B”, nên đầu ra đổi thành `[A, B, C, D]`. Đây là kết quả của **thay đổi chính sách so sánh**, không phải `List.sort` mất tính ổn định.

### PHÂN BIỆT HAI QUY ƯỚC

```text
tính ổn định khi sắp xếp
→ thuộc tính của thuật toán/API sắp xếp

tính nhất quán của comparator
→ thuộc tính của hàm xác định thứ tự
```

Comparator tuân thủ đối dấu và bắc cầu vẫn không tự bảo đảm một API sắp xếp sẽ ổn định; ta phải kiểm tra hợp đồng của API. Ngược lại, `List.sort` ổn định cũng không sửa được comparator vi phạm tính bắc cầu, chẳng hạn `(a, b) -> a - b` ở trên khi bị tràn số.

Không nên giả định mọi API sắp xếp đều ổn định nếu tài liệu API không nói rõ.

Sau khi đã hiểu cả thứ tự tự nhiên lẫn thứ tự do đoạn mã sử dụng định nghĩa, chương cuối sẽ nối phần sắp xếp trở lại với định danh, phép bằng nhau, mã băm, biểu diễn văn bản và rủi ro do trạng thái thay đổi để tạo thành một mô hình quyết định thống nhất cho toàn mô-đun.
