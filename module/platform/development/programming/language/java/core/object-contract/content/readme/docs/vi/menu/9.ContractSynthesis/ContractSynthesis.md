# Tổng hợp các quy ước của Object

## <a id="contract-synthesis">Tổng hợp các quy ước của Object</a>

Các phương thức và interface trong mô-đun này trả lời **những câu hỏi khác nhau về cùng một đối tượng**. Thiết kế đúng bắt đầu bằng việc xác định API đang cần câu trả lời nào, thay vì xem mọi kiểu so sánh là cùng một việc.

### MỘT ĐỐI TƯỢNG, NHIỀU QUY ƯỚC

Hãy giữ mô hình sau:

```text
==
→ có cùng định danh tham chiếu không?

equals
→ có cùng giá trị hoặc thực thể về mặt logic không?

hashCode
→ cung cấp tín hiệu tra cứu theo mã băm, nhất quán với equals

toString
→ cung cấp biểu diễn văn bản hữu ích cho chẩn đoán

Comparable
→ một thứ tự tự nhiên do kiểu dữ liệu sở hữu

Comparator
→ chính sách sắp xếp bên ngoài hoặc thay thế
```

Các phương thức và interface này không chỉ là mã mà IDE có thể sinh tự động. Chúng là **những quy ước mà cấu trúc dữ liệu, thuật toán, hệ thống log, công cụ phát triển và đoạn mã sử dụng phía ngoài tin tưởng**.

Chẳng hạn, hai biến có thể giữ **hai tham chiếu khác nhau** (nên `==` là `false`) nhưng vẫn biểu diễn cùng mã người dùng (nên `equals` là `true`). Khi đó `hashCode` **bắt buộc bằng nhau**; chiều ngược lại không đúng vì hai đối tượng không bằng nhau vẫn có thể trùng mã băm. Tương tự, kết quả so sánh thứ tự bằng `0` nói rằng hai giá trị ở **cùng vị trí theo chính sách thứ tự đang chọn**, chưa chắc `equals` trả về `true`.

### API JAVA NÀO QUAN SÁT QUY ƯỚC NÀO?

```text
HashSet / HashMap
→ hashCode thu hẹp nhóm ứng viên
→ equals xác nhận bằng nhau về mặt logic

TreeSet / TreeMap
→ compareTo hoặc Comparator xác định quan hệ thứ tự

các thao tác sắp xếp
→ compareTo hoặc Comparator xác định vị trí trước/sau

log / trình gỡ lỗi / thông báo kiểm tra (`assertion`)
→ toString cung cấp văn bản chẩn đoán
```

Đó là lý do cùng một đối tượng có thể hoạt động đúng trong một API nhưng gây bất ngờ trong API khác khi các quy ước không nhất quán. Một kiểu dữ liệu cố ý để `equals` và thứ tự trả lời hai câu hỏi nghiệp vụ khác nhau vẫn có thể hợp lệ, nhưng sự khác biệt đó phải được hiểu rõ trước khi chọn cấu trúc dữ liệu dựa trên mã băm hay cấu trúc có thứ tự.

### DÒ THEO HÀNH VI CỦA `UserId` VÀ `Book`

Giả sử ứng dụng tra tên người dùng theo `UserId` và quản lý hai cuốn `Book` có cùng tiêu đề nhưng khác mã sách. Ta muốn **tra được tên bằng một đối tượng ID mới có cùng giá trị**, đồng thời biết vì sao sắp xếp danh sách và giữ phần tử trong `TreeSet` không luôn cho cùng số lượng.

Ví dụ Java 21 sau có thể lưu thành `ContractSynthesisDemo.java` rồi biên dịch và chạy. Hai khai báo `record` là cách viết ngắn cho dữ liệu giá trị: Java tạo `equals`, `hashCode` và `toString` dựa trên các thành phần record. Các thành phần trong ví dụ đều là `String` hoặc `int`, nên giá trị dùng để so sánh không bị sửa sau khi tạo. `UserId` vì thế có cùng **ngữ nghĩa bằng nhau theo `value`** như lớp `UserId` đã cài đặt ở bài trước; `Book` ở ví dụ này bằng nhau khi tất cả thành phần record bằng nhau.

```java
import java.util.*;

public class ContractSynthesisDemo {
    record UserId(String value) {}
    record Book(String id, String title, int year) {}

    public static void main(String[] args) {
        UserId a = new UserId("U-100");
        UserId b = new UserId("U-100");
        Map<UserId, String> names = new HashMap<>();
        names.put(a, "An");

        System.out.println("sameRef=" + (a == b));
        System.out.println("sameValue=" + a.equals(b));
        System.out.println("sameHash=" + (a.hashCode() == b.hashCode()));
        System.out.println("debug=" + b);
        System.out.println("lookup=" + names.get(b));

        Book first = new Book("B-1", "Java", 2022);
        Book second = new Book("B-2", "Java", 2024);
        Set<Book> hashBooks = new HashSet<>(List.of(first, second));
        Comparator<Book> byTitle = Comparator.comparing(Book::title);
        Set<Book> titleOnly = new TreeSet<>(byTitle);
        titleOnly.add(first);
        titleOnly.add(second);
        Set<Book> titleThenId = new TreeSet<>(
                byTitle.thenComparing(Book::id));
        titleThenId.add(first);
        titleThenId.add(second);

        List<Book> sortedBooks = new ArrayList<>(List.of(second, first));
        sortedBooks.sort(byTitle.thenComparing(Book::id));

        System.out.println("hashBooks=" + hashBooks.size());
        System.out.println("titleOnly=" + titleOnly.size());
        System.out.println("titleThenId=" + titleThenId.size());
        System.out.println("sortedBookIds=" +
                sortedBooks.stream().map(Book::id).toList());
    }
}
```

Kết quả:

```text
sameRef=false
sameValue=true
sameHash=true
debug=UserId[value=U-100]
lookup=An
hashBooks=2
titleOnly=1
titleThenId=2
sortedBookIds=[B-1, B-2]
```

Dò theo từng đoạn:

1. `a` và `b` được tạo bằng hai lần `new`, nên không cùng tham chiếu. `equals` của `UserId` so `value`, vì vậy cả hai đều đại diện `"U-100"`; mã băm của chúng cũng bằng nhau. Khi gọi `names.get(b)`, `HashMap` có thể tìm nhóm ứng viên theo mã băm rồi kiểm tra khóa bằng `equals`, nên lấy lại `"An"` dù khóa được `put` bằng `a`.
2. Biểu thức `"debug=" + b` gọi biểu diễn `toString` của record và in `UserId[value=U-100]`. Đây là thông tin đọc log, **không quyết định** phép bằng nhau, mã băm hay thứ tự.
3. Hai `Book` có mã và năm khác nhau nên không bằng nhau theo `equals` của record: `HashSet` giữ cả hai. Nhưng `byTitle` chỉ đọc `title`: khi so `"Java"` với `"Java"`, `Comparator` trả `0`; `TreeSet` dùng kết quả ấy để coi cuốn thứ hai trùng khóa thứ tự và chỉ giữ một phần tử.
4. Thêm `thenComparing(Book::id)` phân biệt `"B-1"` và `"B-2"`, nên `titleThenId` có hai phần tử **với hai cuốn sách đang xét**. Chính comparator đó khi dùng với `List.sort` **chỉ thay đổi vị trí**, không bỏ cuốn sách nào; danh sách từ `[B-2, B-1]` thành `[B-1, B-2]`.

**Giới hạn của tiêu chí `title` rồi `id`:** `record Book(id, title, year)` sinh `equals` có xét cả `year`, nhưng comparator ở trên bỏ qua `year`. Nếu thay bằng `new Book("B-1", "Java", 2022)` và `new Book("B-1", "Java", 2024)`, hai sách có `equals == false` nhưng `byTitle.thenComparing(Book::id).compare(...)` trả `0`; `TreeSet` vẫn chỉ giữ một cuốn. Khi cần thứ tự **nhất quán với `equals` của record trong ví dụ** (các trường `title` và `id` khác `null`), có thể dùng `byTitle.thenComparing(Book::id).thenComparingInt(Book::year)` để phân biệt nốt `year`. Việc thêm `id` trong chương trình chỉ chứng minh hai sách **khác ID** được giữ riêng, không chứng minh comparator `title` rồi `id` luôn nhất quán với `equals`.

Đây là quyết định thiết kế: `Book` không nhất thiết có **một thứ tự tự nhiên duy nhất** để triển khai `Comparable`. Nhiều màn hình có thể cần sắp theo tiêu đề, năm hay giá, vì vậy `Comparator` đặt chính sách ở nơi sử dụng. Với `TreeSet` hoặc `TreeMap`, cần đặc biệt kiểm tra khi nào comparator trả `0`: nếu hai `Book` khác nhau về nghiệp vụ nhưng bị so là `0`, cấu trúc có thứ tự sẽ coi chúng là một khóa. Thêm đủ tiêu chí phân định cho những khác biệt cần giữ lại (chẳng hạn ID và năm); không sửa `equals` chỉ để chạy theo một màn hình sắp xếp.

### KHI PHÉP BẰNG NHAU VÀ THỨ TỰ CỐ Ý KHÁC NHAU

`BigDecimal` là một ví dụ liên kết tốt với mô-đun Kiểu số vì phép bằng nhau và thứ tự tự nhiên của nó được thiết kế để trả lời hai câu hỏi khác nhau. Khác `Book` dùng comparator bên ngoài ở trên, `BigDecimal` tự triển khai `Comparable` và cung cấp thứ tự tự nhiên qua `compareTo`. Đoạn sau đặt trong `main`, với các import `java.math.BigDecimal` và `java.util.*`:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0

Set<BigDecimal> hashValues = new HashSet<>();
hashValues.add(a);
hashValues.add(b);

Set<BigDecimal> sortedValues = new TreeSet<>();
sortedValues.add(a);
sortedValues.add(b);

System.out.println(hashValues.size());   // 2
System.out.println(sortedValues.size()); // 1
```

Lần lượt, `a.equals(b)` trả `false` vì ngoài giá trị số, `equals` của `BigDecimal` còn xét **scale** (mức tỉ lệ thập phân); `"1.0"` có scale `1`, `"1.00"` có scale `2`. Nhưng `a.compareTo(b)` trả `0` vì cả hai biểu diễn cùng giá trị số. `HashSet` quan sát `equals` + `hashCode` nên giữ `2` phần tử; `TreeSet` quan sát `compareTo` nên chỉ giữ `1`. Đây là khác biệt **có chủ đích** của lớp, không phải lỗi ở hai cấu trúc dữ liệu.

Vì thứ tự tự nhiên của `BigDecimal` không nhất quán với `equals`, một `TreeSet<BigDecimal>` có thể thể hiện ngữ nghĩa khác với khái niệm bằng nhau được mô tả trong quy tắc chung của `Set`. Mô-đun Kiểu số chịu trách nhiệm giải thích sâu hơn mô hình giá trị và scale của `BigDecimal`; bài học ở đây là **các quy ước khác nhau của cùng một đối tượng trở nên quan sát được qua những API Java khác nhau**.

### TRẠNG THÁI ỔN ĐỊNH LÀ YÊU CẦU XUYÊN SUỐT

Nhiều lỗi trong mô-đun này có cùng một nguyên nhân sâu hơn: **trạng thái được dùng bởi một quy ước thay đổi trong khi API vẫn đang dựa vào kết quả cũ**.

```text
trạng thái dùng cho equals/hashCode thay đổi
→ tra cứu theo mã băm có thể không tìm lại được khóa

trạng thái dùng cho so sánh thay đổi
→ vị trí trong cây có thể không còn khớp thứ tự hiện tại

Comparator phụ thuộc dữ liệu bên ngoài có thể thay đổi
→ các lần so sánh lặp lại có thể cho kết quả mâu thuẫn
```

Đối tượng không bắt buộc lúc nào cũng phải bất biến, nhưng trạng thái liên quan tới phép bằng nhau, mã băm và thứ tự nên ổn định trong suốt thời gian đối tượng tham gia cấu trúc dữ liệu đang phụ thuộc vào quy ước đó.

### CHẨN ĐOÁN LỖI TỪ TRIỆU CHỨNG

Khi một kết quả gây bất ngờ, hãy xác định **API đang dựa vào quy ước nào** trước, sau đó tái hiện với hai giá trị nhỏ và kiểm tra từng bước:

| Triệu chứng | Dấu vết cần kiểm tra | Sửa theo nguyên nhân |
| --- | --- | --- |
| `HashSet.contains(new UserId("U-100"))` trả `false` dù nghĩ rằng ID giống nhau | So `equals` và `hashCode` của hai ID. Nếu `equals == true` nhưng hash khác nhau, `HashSet` có thể không đến được bước xác nhận `equals`. | Giữ quy tắc `equals`/`hashCode` nhất quán; kiểm tra cả các trường dùng xác định giá trị nghiệp vụ. |
| Khóa từng tìm được trong `HashMap` nay tra cứu thất bại | Xem trường dùng trong `equals`/`hashCode` có bị sửa **sau** `put` hay không. Khóa có thể đang ở vị trí được tính từ mã băm cũ. | Ưu tiên khóa bất biến; nếu buộc đổi khóa, loại bỏ mục cũ đúng cách rồi chèn bằng khóa mới. |
| Thêm hai `Book` mà `TreeSet` chỉ tăng một phần tử | In kết quả `comparator.compare(first, second)`: nếu bằng `0`, cấu trúc cây coi hai sách là cùng khóa thứ tự, dù `equals` là `false`. | Chọn đủ tiêu chí phân định hợp với yêu cầu; nếu cần phù hợp với `equals` của record thì xét cả `title`, `id`, `year`. Kiểm tra tính bắc cầu và nhất quán. |
| Log không phân biệt được đối tượng hoặc vô tình lộ thông tin riêng | Đọc trực tiếp kết quả `toString()`: có đủ dấu hiệu chẩn đoán và có chứa mật khẩu/token không? | Chỉ đưa dữ liệu cần thiết, an toàn vào `toString`; không dựa vào nó làm định dạng lưu trữ. |

Ví dụ nếu dùng một khóa có trường `value` thay đổi được: lúc `put`, `value = "U-100"` quyết định mã băm và vị trí lưu. Sau đó đổi thành `"U-200"`, phép `get` có thể tìm theo **mã băm mới** và bỏ lỡ vị trí ban đầu; đổi một đối tượng trong `TreeSet` theo trường dùng để so sánh cũng khiến vị trí đã lưu không còn phù hợp. `UserId` dạng record bất biến trong ví dụ trước tránh được kiểu lỗi đổi trường này. Vấn đề cốt lõi không nằm ở việc `HashMap` hay `TreeSet` “quên” dữ liệu, mà nằm ở việc **quy ước bị thay đổi trong lúc cấu trúc dữ liệu đang dựa vào kết quả cũ**.

### DANH SÁCH KIỂM TRA KHI THIẾT KẾ

Trước khi dùng một kiểu nghiệp vụ làm khóa, phần tử của `Set`, giá trị cần sắp xếp hoặc đối tượng dùng để chẩn đoán, hãy tự hỏi:

1. Điều gì thực sự xác định hai đối tượng bằng nhau về mặt logic trong nghiệp vụ này?
2. `hashCode` có sử dụng trạng thái nhất quán với định nghĩa bằng nhau đó không?
3. Trạng thái dùng cho `equals`/`hashCode` có thể thay đổi khi đối tượng đang nằm trong cấu trúc dữ liệu dựa trên mã băm không?
4. Kiểu dữ liệu có thật sự có một thứ tự tự nhiên hay nên để `Comparator` sở hữu chính sách sắp xếp?
5. Quan hệ thứ tự có bắc cầu và nhất quán nội tại không?
6. Nếu thứ tự không nhất quán với `equals`, đó có phải chủ đích và đã được ghi rõ không?
7. `toString` có cung cấp đủ thông tin chẩn đoán mà không làm lộ dữ liệu nhạy cảm hoặc biến thành định dạng tuần tự hóa không?

Mục tiêu cuối cùng không phải học thuộc năm API. Mục tiêu là thiết kế đối tượng có **ngữ nghĩa về định danh, bằng nhau, mã băm, biểu diễn văn bản và thứ tự đủ nhất quán để các API Java sử dụng chúng một cách có thể dự đoán**.

### TỰ KIỂM TRA SAU KHI GHÉP CÁC QUY ƯỚC

1. Nếu tạo hai `new UserId("U-100")` riêng biệt, vì sao `==` là `false` nhưng `HashMap.get` bằng ID mới vẫn ra `"An"`? **Đối chiếu:** hai tham chiếu khác nhau; `equals` cùng giá trị và `hashCode` nhất quán nên tra cứu được.
2. Vì sao hai `Book` cùng tiêu đề có thể còn đủ trong danh sách đã `sort`, nhưng chỉ còn một trong `TreeSet` dùng `byTitle`? **Đối chiếu:** sắp xếp danh sách không loại phần tử; `TreeSet` dùng kết quả comparator bằng `0` làm quan hệ trùng khóa.
3. Với `BigDecimal("1.0")` và `BigDecimal("1.00")`, dự đoán số phần tử của `HashSet` và `TreeSet`. **Đối chiếu:** lần lượt `2` và `1`, vì `equals` xét scale nhưng `compareTo` so giá trị số.
4. Một khóa `HashMap` tìm thấy trước khi sửa trường `value`, nhưng không tìm thấy sau đó. Cần kiểm tra gì trước? **Đối chiếu:** trường ấy có tham gia `equals`/`hashCode` không, mã băm có thay đổi sau `put` không, và có thể thay bằng khóa bất biến không.
