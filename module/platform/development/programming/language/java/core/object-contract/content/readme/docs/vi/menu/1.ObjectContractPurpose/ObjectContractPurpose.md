# Quy ước của Object là gì và vì sao cần nó?

## <a id="object-contract-purpose">Quy ước của Object là gì?</a>

Giả sử một yêu cầu HTTP mang mã người dùng `U-100`, còn chương trình vừa đọc mã `U-100` từ nơi lưu trữ. Đây là **hai lần tạo đối tượng** nhưng chúng cùng biểu diễn **một mã người dùng**. Nếu chương trình đưa cả hai vào tập hợp không chứa phần tử trùng, bạn mong đợi kết quả là một hay hai phần tử? Để trả lời, thư viện Java cần biết lập trình viên định nghĩa chữ “trùng” như thế nào; nó không thể tự suy đoán từ tên lớp `UserId`.

Trong mô-đun này, **quy ước của Object (Object Contract)** là tên gọi bao quát cho **những cam kết về ý nghĩa và hành vi của đối tượng mà mã sử dụng có thể tin cậy**. Nó không phải một từ khóa, annotation, interface hay một cấu trúc ngôn ngữ riêng tên `ObjectContract`. Chẳng hạn: nếu `equals` cho rằng hai đối tượng bằng nhau thì `hashCode` của chúng bắt buộc bằng nhau; nếu một chính sách so sánh nói `A` đứng trước `B` và `B` đứng trước `C`, chính sách đó phải nhất quán khi xét `A` với `C`.

**Phân biệt tên gọi bao quát với lớp `java.lang.Object`:** `Object` là lớp gốc của các lớp Java và cung cấp những phương thức như `equals(Object)`, `hashCode()` và `toString()`. Các lớp có thể ghi đè chúng để cho đối tượng ý nghĩa phù hợp. `Comparable<T>` và `Comparator<T>` là **hai interface riêng** trong Java dành cho quan hệ thứ tự; chúng **không phải phương thức do `Object` cung cấp** và một lớp không tự có thứ tự tự nhiên chỉ vì nó kế thừa `Object`. Mô-đun học những quy ước này cùng nhau vì mã sử dụng thường phải phối hợp phép bằng nhau, mã băm, văn bản chẩn đoán và thứ tự của cùng một kiểu dữ liệu.

### Các API cần biết điều gì về đối tượng?

Hãy tách các câu hỏi mà một đoạn mã có thể đặt ra cho `UserId` hoặc `Book`:

| Câu hỏi | Cơ chế hoặc quy ước | Nơi kết quả được dùng |
| --- | --- | --- |
| Hai tham chiếu đang chỉ đến **chính cùng một đối tượng**? | `==` khi so sánh tham chiếu; đây là phép kiểm tra **định danh**, không phải phương thức của `Object`. | Kiểm tra hai biến có cùng trỏ đến một instance. |
| Hai đối tượng có cùng **giá trị logic** theo quy tắc của kiểu dữ liệu? | `equals(Object)`; nếu không ghi đè, cách triển khai của `Object` so theo định danh. | `HashSet`, `HashMap` và mã nghiệp vụ cần nhận ra giá trị tương đương. |
| Nên đưa giá trị vào nhóm tra cứu theo mã băm nào? | `hashCode()`, phải phù hợp với `equals`; mã băm **không phải** mã định danh duy nhất. | Các cấu trúc dựa trên bảng băm như `HashSet` và `HashMap`. |
| Nên hiển thị đối tượng thế nào cho người đọc? | `toString()`, một biểu diễn văn bản phục vụ quan sát, không mặc nhiên là định dạng lưu trữ ổn định. | Log, thông báo kiểm tra, gỡ lỗi. |
| Đối tượng nào đứng trước hoặc sau? | `Comparable.compareTo` cho **thứ tự tự nhiên** do kiểu sở hữu; `Comparator.compare` cho **thứ tự được cung cấp từ bên ngoài**. | Sắp xếp danh sách; `TreeSet`, `TreeMap` dùng thứ tự để xác định phần tử/khóa tương đương. |

Điểm nối giữa các hàng không phải là mọi cơ chế đều trả lời cùng một câu hỏi. Chúng **trả lời những câu hỏi khác nhau nhưng phải giữ đúng lời hứa riêng**. Ví dụ, hai `UserId` có cùng mã cần cùng kết quả `equals` và `hashCode` nếu lớp chọn so sánh theo mã; trong khi hai `Book` khác nhau vẫn có thể đứng cạnh nhau khi sắp xếp theo tên sách. Riêng `TreeSet`/`TreeMap` còn xem kết quả so sánh bằng `0` là tương đương theo *thứ tự đang dùng*, nên lựa chọn thứ tự có thể ảnh hưởng việc giữ hay loại phần tử.

### Quan sát trên cùng một ví dụ Java 21

Ví dụ sau dùng `record` để biểu diễn hai loại dữ liệu ngắn gọn. Một `record` tự tạo `equals`, `hashCode` và `toString` dựa trên các thành phần khai báo; đây là **hành vi được Java định nghĩa riêng cho `record`**, không phải mặc định của mọi lớp thông thường. Tạm thời chỉ cần quan sát lời hứa của các phương thức, chưa cần tự cài đặt chúng.

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class ObjectContractIntro {
    record UserId(String value) {}
    record Book(String id, String title) {}

    public static void main(String[] args) {
        UserId fromRequest = new UserId("U-100");
        UserId fromStorage = new UserId("U-100");

        System.out.println(fromRequest == fromStorage);
        System.out.println(fromRequest.equals(fromStorage));
        System.out.println(new HashSet<>(List.of(fromRequest, fromStorage)).size());

        List<Book> books = new ArrayList<>(List.of(
                new Book("B-02", "Java"),
                new Book("B-01", "Algorithms")
        ));
        books.sort(Comparator.comparing(Book::title));
        System.out.println(List.of(books.get(0).id(), books.get(1).id()));
        System.out.println(books.get(0));
    }
}
```

Kết quả:

```text
false
true
1
[B-01, B-02]
Book[id=B-01, title=Algorithms]
```

Đọc kết quả theo trình tự. `new UserId(...)` chạy hai lần nên `fromRequest == fromStorage` là `false`: hai biến trỏ đến hai đối tượng riêng. Nhưng hai `record` có cùng thành phần `value = "U-100"`, nên `equals` trả `true`; mã băm mà `record` sinh ra cũng phù hợp với phép bằng nhau đó. Khi `HashSet` thêm phần tử thứ hai, nó dùng mã băm để tìm vùng ứng viên rồi dùng phép bằng nhau để xác nhận: hai giá trị được xem là trùng, vì vậy kích thước tập hợp là `1`.

Hai `Book` có mã `B-02` và `B-01` riêng. Trong `Comparator.comparing(Book::title)`, cách viết `Book::title` chỉ cho Java lấy tiêu đề từ từng cuốn sách để so sánh; chi tiết cách tự tạo `Comparator` sẽ được dạy ở chương 8. Vì `Algorithms` đứng trước `Java` theo thứ tự này, hai lời gọi `books.get(0).id()` và `books.get(1).id()` tạo danh sách mã `[B-01, B-02]`. Thao tác sắp xếp **không thay đổi** `Book.equals`; nó chỉ chọn thứ tự khi hiển thị danh sách. Lệnh cuối in biểu diễn `toString` mà `record` tạo ra, hữu ích để xem sách nào đang ở đầu danh sách. Cùng một ví dụ đã cho thấy phép bằng nhau, mã băm, thứ tự và biểu diễn văn bản **phục vụ những mục đích khác nhau**.

Thử đổi `UserId` thành một **lớp thông thường không ghi đè `equals` và `hashCode`**, dù lớp vẫn có trường `value = "U-100"`. Khi đó, với hai instance tạo riêng, `equals` kế thừa từ `Object` trả `false` và `HashSet` giữ **hai phần tử**. Lỗi không nằm ở `HashSet`: kiểu dữ liệu chưa cung cấp quy tắc “cùng mã là cùng giá trị”. Các chương sau sẽ hướng dẫn tự định nghĩa và kiểm tra quy tắc đó mà không cần phụ thuộc vào `record`.

### Vì sao gọi đây là một cam kết?

**Trình biên dịch có thể kiểm tra chữ ký, kiểu trả về và cú pháp, nhưng không chứng minh được đầy đủ các quy tắc ngữ nghĩa** mà thư viện mong đợi. Một `equals` viết sai tính đối xứng vẫn có thể biên dịch; một lớp chỉ ghi đè `equals` mà để `hashCode` không phù hợp vẫn có thể chạy. Sai sót thường xuất hiện ở nơi sử dụng đối tượng, chẳng hạn:

- `HashSet` giữ hai `UserId` mà nghiệp vụ muốn coi là trùng, hoặc `HashMap` không tìm lại được khóa tương đương vì mã băm không nhất quán.
- Một khóa bị sửa sau khi thêm vào cấu trúc dựa trên mã băm có thể không được tìm thấy theo giá trị mới dù nó vẫn còn trong cấu trúc.
- Một chính sách so sánh sai tính bắc cầu khiến kết quả sắp xếp khó dự đoán; `TreeSet` cũng có thể xem hai giá trị khác nhau theo `equals` là cùng một phần tử khi phép so sánh trả `0`.
- `toString` khó đọc khiến việc chẩn đoán kém hiệu quả, hoặc vô tình đưa thông tin nhạy cảm vào log.

Vì vậy, **hành vi biên dịch được chưa đủ để làm thành một quy ước đúng**. Phải biết ai tiêu thụ kết quả, họ giả định điều gì, và đầu vào nào sẽ bộc lộ việc vi phạm. `equals` và `hashCode` có quan hệ bắt buộc một chiều: **bằng nhau theo `equals` thì phải có cùng mã băm**, nhưng hai đối tượng không bằng nhau vẫn *có thể* trùng mã băm. Với thứ tự, `compareTo`/`Comparator` cần quan hệ so sánh nhất quán, có tính bắc cầu; việc kết quả so sánh `0` khớp hoàn toàn với `equals` là điều rất nên có ở cấu trúc có thứ tự, nhưng không phải mọi kiểu đều chọn như vậy.

### Lộ trình học qua `UserId` và `Book`

Điểm xuất phát là kiến thức cơ bản về lớp, đối tượng, biến tham chiếu và lời gọi phương thức trong Java. Nếu chưa quen **ghi đè (override)**, hãy hiểu tạm là lớp cung cấp cách thực hiện riêng cho phương thức được kế thừa; chương `equals` sẽ sử dụng thao tác này cụ thể. Bạn chưa cần biết thuật toán băm, cấu trúc cây hay tự viết `Comparator`. Đích đến là **tự giải thích, dự đoán và kiểm tra** một kiểu dữ liệu sẽ hoạt động thế nào khi được đặt trong các API Java khác nhau.

| Chặng theo Menu đã duyệt | Năng lực cần đạt và lý do học tiếp |
| --- | --- |
| **1. Quy ước của Object** | Nhìn tình huống hai `UserId` và nói được *API đang cần cam kết nào*; từ đây nhận ra phải phân biệt hai câu hỏi về “giống nhau”. |
| **2. Định danh và bằng nhau về mặt logic** | Dự đoán `a == b` và `a.equals(b)` khi hai `UserId` tạo riêng nhưng có cùng mã; sau đó cần một cách định nghĩa phép bằng nhau logic đáng tin cậy. |
| **3. Quy tắc của `equals`** | Giải thích và kiểm tra phản xạ, đối xứng, bắc cầu, nhất quán và xử lý `null`; có phép bằng nhau đúng rồi mới hỏi bảng băm tìm được đối tượng bằng nhau ra sao. |
| **4. `hashCode` và tra cứu theo mã băm** | Hiểu mã băm giúp thu hẹp nơi tìm ứng viên, không chứng minh hai đối tượng bằng nhau; điều này chuẩn bị cho quy tắc liên kết với `equals`. |
| **5. Nhất quán giữa `equals` và `hashCode`** | Dự đoán việc thêm/tìm `UserId` trong `HashSet`/`HashMap`, thử hình dung hậu quả của khóa có trạng thái bị sửa; hoàn thiện phần tra cứu trước khi chuyển sang quan sát đối tượng. |
| **6. Biểu diễn văn bản với `toString`** | Tạo mô tả `UserId`/`Book` hữu ích khi kiểm tra hoặc ghi log, đồng thời tránh rò rỉ dữ liệu; sau đó chuyển từ **mô tả đối tượng** sang **xếp thứ tự đối tượng**. |
| **7. Thứ tự tự nhiên với `Comparable`** | Đọc dấu âm/0/dương của `compareTo` và giải thích thứ tự mà *kiểu* tự sở hữu; nếu một danh sách `Book` cần các góc nhìn khác nhau, phải tách chính sách sắp xếp ra ngoài. |
| **8. Thứ tự tùy biến với `Comparator`** | Sắp `Book` theo tiêu đề hoặc tiêu chí khác, kết hợp tiêu chí để xử lý trường hợp bằng nhau; đồng thời nhận ra kết quả `0` đặc biệt quan trọng khi dùng `TreeSet`. |
| **9. Tổng hợp các quy ước** | So sánh cách cùng một kiểu hành xử trong tập hợp dựa trên mã băm, cấu trúc có thứ tự và log; xét thêm `BigDecimal` như trường hợp phép bằng nhau và thứ tự tự nhiên cố ý khác nhau (phần kiểu số được đào sâu ở mô-đun Kiểu số). |

Bạn có thể tự kiểm tra mức sẵn sàng trước chương 2 bằng ba câu hỏi: **Vì sao** hai `UserId` trong ví dụ có `==` là `false` nhưng `equals` là `true`? **Vì sao** `HashSet` còn một phần tử và trường hợp lớp không ghi đè `equals` lại có hai? **Sắp `Book` theo tên** có làm các sách trở thành bằng nhau về mặt nghiệp vụ hay không? Nếu giải thích được ba kết quả bằng hành vi mà Java nhìn thấy, bạn đã có mô hình ban đầu để nghiên cứu các quy tắc chi tiết.

### Tài liệu tham khảo

- [Java SE 21 — `Object`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html): quy tắc của `equals`, `hashCode` và mục đích của `toString`.
- [Java SE 21 — `Comparable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html) và [Java SE 21 — `Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html): quy tắc và cách sở hữu quan hệ thứ tự.
- [Java SE 21 — `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html): tập hợp được hỗ trợ bởi bảng băm.
