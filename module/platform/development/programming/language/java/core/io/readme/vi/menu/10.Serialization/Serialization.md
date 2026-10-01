# Ranh giới của tuần tự hóa đối tượng Java

Đến đây ta đã chủ yếu di chuyển byte hoặc văn bản. Java còn có một cơ chế khác: biến trạng thái của một **đồ thị đối tượng (object graph)** thành luồng byte rồi dựng lại đồ thị đó sau này. Đồ thị đối tượng gồm đối tượng gốc cùng các đối tượng có thể đi tới thông qua trường/tham chiếu; nó có thể chứa tham chiếu dùng chung và cả chu trình. Cơ chế này là **tuần tự hóa đối tượng nguyên bản của Java (`Java Object Serialization`)**.

Tên gọi dễ khiến người mới suy ra rằng đây là cách mặc định để lưu đối tượng vào cơ sở dữ liệu, bộ nhớ đệm hoặc gửi đối tượng qua mạng. Đó là suy luận nguy hiểm. Java Object Serialization gắn chặt với lớp Java, khả năng tương thích phiên bản, **classpath** — tập các lớp/tài nguyên mà JVM có thể tìm và nạp khi chạy — và quá trình **giải tuần tự hóa (deserialization)**, tức đọc các byte đã tuần tự hóa để dựng lại đồ thị đối tượng. Vì thế đây là một cơ chế có ranh giới lớn, không phải định dạng lưu trữ hay trao đổi qua mạng dùng chung cho mọi trường hợp.

## <a id="java-serialization-model">Mô hình tuần tự hóa Java</a>

Một lớp tham gia **tuần tự hóa đối tượng nguyên bản của Java (`Java Object Serialization`)** thường triển khai giao diện đánh dấu (marker interface) `Serializable`:

~~~java
final class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String title;
    private final String body;

    Note(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
~~~

`Serializable` không khai báo phương thức nào. Nó là **giao diện đánh dấu (marker interface)**: lớp dùng nó để nói với cơ chế tuần tự hóa rằng các **đối tượng (instance)** của lớp này được phép tham gia quá trình.

Ghi đối tượng:

~~~java
Path file = Files.createTempFile("note-", ".ser");

try (
        OutputStream out = Files.newOutputStream(file);
        ObjectOutputStream objectOut = new ObjectOutputStream(out)
) {
    objectOut.writeObject(
            new Note("I/O", "Java serialization demo")
    );
}
~~~

Đọc lại:

~~~java
try (
        InputStream in = Files.newInputStream(file);
        ObjectInputStream objectIn = new ObjectInputStream(in)
) {
    Note note = (Note) objectIn.readObject();
}
~~~

Mô hình cần tránh là:

~~~text
đối tượng
→ bản đồ trường kiểu JSON
~~~

mà gần hơn với:

~~~text
đồ thị đối tượng Java
→ ObjectOutputStream ghi giao thức tuần tự hóa của Java
→ luồng byte chứa mô tả kiểu/lớp + trạng thái + tham chiếu trong đồ thị
→ ObjectInputStream đọc giao thức
→ JVM dựng lại đồ thị đối tượng Java
~~~

Vì vậy dãy byte kết quả không nên được xem là một quy ước dễ đọc, độc lập ngôn ngữ hoặc tự động ổn định lâu dài.

## <a id="serializable-graph">Đồ thị đối tượng có thể tuần tự hóa</a>

Tuần tự hóa không dừng ở đối tượng đầu tiên. Nó đi qua các đối tượng có thể truy cập được từ những trường tham gia tuần tự hóa.

Ví dụ:

~~~java
final class Author implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
}

final class Article implements Serializable {
    private static final long serialVersionUID = 1L;
    String title;
    Author author;
}
~~~

Khi tuần tự hóa một `Article`, đối tượng `Author` mà trường `author` trỏ tới cũng thuộc đồ thị cần tuần tự hóa. Nếu một đối tượng có thể truy cập được không hỗ trợ tuần tự hóa và trường đó không bị loại khỏi quá trình, cơ chế tuần tự hóa khi chạy có thể ném `NotSerializableException`.

Luồng đối tượng (`ObjectOutputStream`/`ObjectInputStream`) còn giữ quan hệ định danh trong đồ thị. Nếu hai trường cùng trỏ tới một đối tượng, giao thức có thể ghi tham chiếu tới đối tượng đã xuất hiện thay vì tạo hai đối tượng độc lập khi giải tuần tự hóa. Chu trình trong đồ thị vì thế cũng có thể được biểu diễn.

~~~text
Article A ─┐
           ├─→ cùng một đối tượng Author
Article B ─┘
~~~

Sau khi giải tuần tự hóa, quan hệ chia sẻ tham chiếu trong đồ thị được khôi phục theo giao thức.

Với một **lớp Serializable thông thường**, khi giải tuần tự hóa, **hàm tạo (constructor)** thông thường của chính lớp đó không được gọi như lúc ta dùng `new`. Lớp cha gần nhất không hỗ trợ tuần tự hóa cần một hàm tạo không tham số có thể truy cập để trạng thái lớp cha được khởi tạo theo quy tắc tuần tự hóa. Điều này cho thấy **giải tuần tự hóa (deserialization)** không chỉ là “đọc dữ liệu rồi gọi hàm tạo”.

**Record có thể tuần tự hóa là ngoại lệ được Java quy định riêng** cho quy tắc trên: khi giải tuần tự hóa record, Java dựng lại các giá trị **thành phần (component)** rồi gọi **hàm tạo chuẩn (canonical constructor)** của record. Record còn có thêm các quy tắc tuần tự hóa đặc biệt khác, vì vậy không nên áp mọi quy tắc về hàm tạo của lớp `Serializable` thông thường sang record.

Chính khả năng dựng lại cả đồ thị qua vòng đời đặc biệt này là lý do Java Object Serialization phải được xem như một ranh giới mạnh.

## <a id="serialversionuid">serialVersionUID và tương thích phiên bản</a>

Dữ liệu đã tuần tự hóa có thể tồn tại lâu hơn phiên bản lớp đã tạo ra nó. Java cần một cách kiểm tra lớp lúc đọc có tương thích với lớp lúc ghi hay không. `serialVersionUID` là mã định danh phiên bản phục vụ kiểm tra này:

~~~java
final class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    String title;
    String body;
}
~~~

Với một **lớp Serializable thông thường**, nếu không khai báo `serialVersionUID`, cơ chế tuần tự hóa có thể tính UID từ chi tiết lớp. Khi lớp thay đổi, UID tính tự động có thể thay đổi theo những cách khiến dữ liệu cũ không đọc được.

Vì vậy lớp cố ý dùng Java Object Serialization thường khai báo UID rõ ràng:

~~~java
private static final long serialVersionUID = 1L;
~~~

Nhưng cần hiểu giới hạn: **giữ cùng UID không tự động làm mọi thay đổi lớp trở nên an toàn hoặc có nghĩa**. Nó chỉ là một phần của kiểm tra tương thích trong cơ chế tuần tự hóa. Thay đổi trường, hệ phân cấp, bất biến hoặc cơ chế tuần tự hóa tùy chỉnh vẫn có thể tạo dữ liệu không còn phù hợp về mặt nghiệp vụ dù cơ chế khi chạy không chặn ngay.

Ngược lại, đổi UID chủ động có thể dùng để tuyên bố dữ liệu tuần tự hóa cũ không còn tương thích; lúc đọc dữ liệu cũ, `InvalidClassException` có thể xuất hiện.

Record lại có ranh giới riêng: record hỗ trợ tuần tự hóa có `serialVersionUID` mặc định là `0L` nếu không tự khai báo, và Java **không yêu cầu UID trong luồng phải khớp UID của lớp record hiện tại**. Quy tắc đặc biệt này tiếp tục cho thấy Java Object Serialization có quy tắc phụ thuộc loại lớp, không phải một công thức áp dụng giống hệt cho mọi kiểu.

Vì dạng dữ liệu tuần tự hóa bị buộc vào quá trình tiến hóa của lớp, lưu Java Object Serialization như dữ liệu dài hạn nhiều năm thường tạo chi phí chuyển đổi và tương thích lớn hơn các định dạng có lược đồ (schema) hoặc quy ước được thiết kế rõ ràng.

## <a id="transient-field">Trường transient</a>

Không phải mọi **trường của đối tượng (instance field)** đều nên đi vào dạng tuần tự hóa. Từ khóa `transient` loại một trường khỏi cơ chế tuần tự hóa mặc định:

~~~java
final class SessionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String temporaryToken;
}
~~~

Sau khi giải tuần tự hóa bằng cơ chế mặc định, trường `temporaryToken` nhận giá trị mặc định của kiểu, ở đây là `null`.

`transient` phù hợp với dữ liệu:

- có thể tính lại từ trường khác;
- chỉ có ý nghĩa trong lần chạy hiện tại;
- trỏ tới đối tượng không nên/không thể tuần tự hóa;
- không nên trở thành một phần của dạng dữ liệu đã tuần tự hóa.

Trường `static` là trạng thái cấp lớp chứ không phải trạng thái của từng đối tượng nên không thuộc trạng thái đối tượng được tuần tự hóa mặc định.

Không nên hiểu `transient` như một cơ chế bảo mật hoàn chỉnh. Nó chỉ nói trường nào không đi vào dạng tuần tự hóa mặc định. Nếu dữ liệu nhạy cảm được sao chép sang trường khác, cơ chế tuần tự hóa tùy chỉnh ghi nó ra, hoặc thiết kế tổng thể vẫn để bí mật xuất hiện ở thành phần khác, từ khóa này không giải quyết vấn đề.

Java còn cho phép tùy chỉnh `writeObject/readObject` và các **điểm mở rộng đặc biệt (hook)** liên quan. Các điểm mở rộng này tăng quyền kiểm soát nhưng cũng mở rộng bề mặt tương thích và bảo mật. Chỉ nên thêm khi thực sự cần một quy ước dạng tuần tự hóa có chủ ý.

## <a id="serialization-security-risk">Rủi ro bảo mật và ranh giới sử dụng</a>

Giải tuần tự hóa bằng Java Object Serialization không giống việc đọc một cấu trúc dữ liệu thụ động đơn giản. Trong quá trình dựng đồ thị đối tượng, cơ chế tuần tự hóa khi chạy có thể kích hoạt hành vi đặc biệt của các lớp trên classpath, như `readObject`, `readResolve` tùy chỉnh và các **điểm móc xác thực (validation hook)**.

Vì vậy **không giải tuần tự hóa bằng Java Object Serialization từ nguồn không tin cậy**. Luồng byte do client tùy ý gửi, tệp tải lên không đáng tin hoặc thông điệp từ ranh giới bên ngoài không nên được đưa trực tiếp vào `ObjectInputStream.readObject()`.

Trong các **cuộc tấn công giải tuần tự hóa (deserialization attack)**, một **gadget** là lớp/phương thức đã có sẵn trên classpath có hành vi đặc biệt có thể bị lợi dụng khi đối tượng được dựng lại. Một **chuỗi gadget (gadget chain)** là nhiều gadget được dữ liệu độc hại kích hoạt nối tiếp để dẫn tới hành vi nguy hiểm. Đây là ý nghĩa của cụm “gadget/deserialization attack” khi nói về lịch sử rủi ro của Java Object Serialization.

~~~text
byte không tin cậy
    ↓
ObjectInputStream.readObject()
    ↓
dựng lại đồ thị đối tượng + điểm móc đặc thù của lớp
    ↓
rủi ro
~~~

`ObjectInputFilter` có thể giới hạn lớp, độ sâu đồ thị, số tham chiếu hoặc kích thước trong hệ thống bắt buộc phải hỗ trợ Java Object Serialization:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "com.example.Note;java.base/*;!*"
        );

objectIn.setObjectInputFilter(filter);
~~~

Chuỗi bộ lọc trên cố ý khá rộng để minh họa: nó cho phép lớp `com.example.Note`, cho phép mọi lớp thuộc module `java.base`, rồi dùng `!*` để từ chối các lớp chưa khớp khác. Trong hệ thống thực tế, `ObjectInputFilter` nên chỉ cho phép các lớp/module thật sự cần cho đồ thị đối tượng mong đợi và thường cần thêm giới hạn phù hợp về độ sâu, số tham chiếu, độ dài mảng hoặc kích thước luồng. Bộ lọc phải được gắn vào `ObjectInputStream` **trước lần đọc đối tượng đầu tiên** như `readObject()` hoặc `readUnshared()`.

`ObjectInputFilter` là **lớp bảo vệ bổ sung (defense in depth)**, không biến việc giải tuần tự hóa dữ liệu Java Object Serialization không tin cậy thành lựa chọn mặc định được khuyến nghị.

Java Object Serialization cũng có các ranh giới ngoài bảo mật:

| Ranh giới | Hệ quả |
| --- | --- |
| Gắn với lớp Java/classpath | Khó làm quy ước giữa các ngôn ngữ khác nhau |
| Gắn với quá trình tiến hóa của lớp | Cần quản lý tương thích/phiên bản |
| Giao thức nhị phân khó quan sát | Khó gỡ lỗi/kiểm tra trực tiếp hơn định dạng văn bản/lược đồ (schema) |
| Dựng đồ thị đối tượng | Bề mặt hành vi khi chạy lớn hơn việc đọc DTO đơn giản |
| Lịch sử chuỗi gadget/tấn công giải tuần tự hóa | Dữ liệu đầu vào không tin cậy là rủi ro nghiêm trọng |

Với API mạng, quy ước thông điệp hoặc lưu trữ dài hạn, thường nên ưu tiên định dạng có quy ước rõ như JSON, Protocol Buffers hoặc định dạng/lược đồ phù hợp hệ thống, kết hợp DTO và quản lý phiên bản có chủ ý. Việc chọn định dạng cụ thể phụ thuộc bài toán, nhưng **Java Object Serialization không nên là khuyến nghị tổng quát**.

Nếu một cơ sở mã cũ đã dùng Java Object Serialization, cần biết cơ chế để đọc, bảo trì và thu hẹp ranh giới của nó. Chương cuối sẽ đặt cơ chế này bên cạnh luồng byte, Reader/Writer, `Files` và Channel để hình thành quy tắc chọn mức trừu tượng I/O đơn giản và đúng nhất.
