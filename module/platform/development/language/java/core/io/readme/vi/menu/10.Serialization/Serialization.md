# Object Serialization

Đến đây ta đã chủ yếu di chuyển byte hoặc text. Java còn có một cơ chế khác: biến trạng thái của một **đồ thị đối tượng (object graph)** thành luồng byte rồi dựng lại graph đó sau này. Object graph là object gốc cùng các object có thể đi tới thông qua field/reference của nó; graph có thể chứa reference dùng chung và cả chu trình. Cơ chế biến graph này thành byte và dựng lại về sau thường được gọi là **Java native serialization**.

Tên gọi dễ khiến người mới suy ra rằng đây là cách mặc định để lưu object vào database, cache hoặc gửi object qua network. Đó là suy luận nguy hiểm. Native serialization gắn chặt với class Java, version compatibility, **classpath** — tập các class/resource mà JVM có thể tìm và nạp khi chạy — và quá trình **deserialize**, tức đọc serialized bytes để dựng lại object graph; vì thế nó là một cơ chế có ranh giới lớn, không phải định dạng persistence/network tổng quát nên mặc định chọn.

## <a id="java-serialization-model">Mô hình Java Serialization</a>

Một class tham gia native serialization thường implements marker interface `Serializable`:

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

`Serializable` không khai báo method nào. Nó là **marker interface**: class dùng nó để nói với cơ chế serialization rằng instance của class này được phép tham gia quá trình.

Ghi object:

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

Mental model không phải:

~~~text
object
→ JSON-like field map
~~~

mà gần hơn với:

~~~text
Java object graph
→ ObjectOutputStream ghi protocol serialization của Java
→ byte stream chứa type/class descriptors + state + graph references
→ ObjectInputStream đọc protocol
→ JVM dựng lại graph của Java objects
~~~

Vì vậy byte kết quả không nên được xem là một contract dễ đọc, độc lập ngôn ngữ hoặc ổn định lâu dài.

## <a id="serializable-graph">Serializable Object Graph</a>

Serialization không dừng ở object đầu tiên. Nó đi qua các object reachable từ các field được serialize.

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

Khi serialize một `Article`, object `Author` mà field `author` trỏ tới cũng thuộc graph cần serialize. Nếu một object reachable không serializable và field đó không bị loại khỏi serialization, runtime có thể ném `NotSerializableException`.

Object stream còn giữ quan hệ identity trong graph. Nếu hai field cùng trỏ tới một object, protocol có thể ghi reference tới object đã xuất hiện thay vì tạo hai object độc lập khi deserialize. Cycle trong graph vì thế cũng có thể được biểu diễn.

~~~text
Article A ─┐
           ├─→ cùng một Author instance
Article B ─┘
~~~

Sau deserialize, quan hệ chia sẻ reference trong graph được khôi phục theo protocol.

Một điểm runtime quan trọng: khi deserialize một class serializable, constructor thông thường của chính class serializable không được gọi như lúc ta dùng `new`. Phần non-serializable superclass gần nhất cần constructor no-arg có thể truy cập để state superclass được khởi tạo theo quy tắc serialization. Điều này cho thấy deserialize không chỉ là “parse dữ liệu rồi gọi constructor”.

Chính khả năng dựng lại cả graph và tham gia lifecycle đặc biệt này là lý do native serialization phải được xem như một boundary mạnh.

## <a id="serialversionuid">serialVersionUID và tương thích phiên bản</a>

Serialized bytes có thể sống lâu hơn version class đã tạo ra chúng. Java cần một cách kiểm tra class lúc đọc có tương thích với class lúc ghi hay không. `serialVersionUID` là version identifier phục vụ kiểm tra này:

~~~java
final class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    String title;
    String body;
}
~~~

Nếu không khai báo, runtime có thể tính một UID từ chi tiết class. Khi class thay đổi, UID tính tự động có thể thay đổi theo những cách khiến dữ liệu cũ không đọc được.

Vì vậy class cố ý dùng native serialization thường khai báo UID rõ ràng:

~~~java
private static final long serialVersionUID = 1L;
~~~

Nhưng cần hiểu giới hạn: **giữ cùng UID không tự động làm mọi thay đổi class trở nên an toàn hoặc có nghĩa**. Nó chỉ là một phần của kiểm tra compatibility của serialization runtime. Thay đổi field, hierarchy, invariant hoặc custom serialization logic vẫn có thể tạo dữ liệu không còn phù hợp về mặt nghiệp vụ dù runtime không chặn ngay.

Ngược lại, đổi UID chủ động có thể dùng để tuyên bố stream cũ không còn compatible; lúc đọc dữ liệu cũ, `InvalidClassException` có thể xuất hiện.

Vì serialized form bị buộc vào evolution của class, lưu native serialization như dữ liệu persistence sống nhiều năm thường tạo chi phí migration và compatibility lớn hơn các format có schema/contract được thiết kế rõ ràng.

## <a id="transient-field">Field transient</a>

Không phải mọi instance field đều nên đi vào serialized form. Keyword `transient` loại một field khỏi default serialization:

~~~java
final class SessionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String temporaryToken;
}
~~~

Sau deserialize bằng cơ chế mặc định, field `temporaryToken` nhận giá trị mặc định của type, ở đây là `null`.

`transient` phù hợp với dữ liệu:

- có thể tính lại từ field khác;
- chỉ có ý nghĩa trong runtime hiện tại;
- trỏ tới object không nên/không thể serialize;
- không nên trở thành một phần của serialized form.

Field `static` cũng không phải state của từng instance nên không thuộc default serialized object state.

Không nên hiểu `transient` như một cơ chế bảo mật hoàn chỉnh. Nó chỉ nói field nào không đi vào default serialized form. Nếu dữ liệu nhạy cảm được copy sang field khác, custom serialization ghi nó ra, hoặc design tổng thể vẫn để secret tồn tại trong artifact khác, keyword này không giải quyết vấn đề.

Java còn cho phép custom `writeObject/readObject` và các hook liên quan. Các hook này tăng quyền kiểm soát nhưng cũng tăng compatibility và security surface. Chỉ nên thêm khi thực sự cần một serialized-form contract có chủ ý.

## <a id="serialization-security-risk">Rủi ro bảo mật và ranh giới sử dụng</a>

Deserialize native Java data không giống parse một cấu trúc dữ liệu thụ động đơn giản. Trong quá trình dựng object graph, serialization runtime có thể kích hoạt hành vi đặc biệt của các class trên classpath, như custom `readObject`, `readResolve` và validation hooks.

Vì vậy **không deserialize Java native serialization từ nguồn không tin cậy**. Byte stream do client tùy ý gửi, file upload không đáng tin hoặc message từ boundary bên ngoài không nên được đưa trực tiếp vào `ObjectInputStream.readObject()`.

Trong các cuộc tấn công deserialize, một **gadget** là class/method đã có sẵn trên classpath có hành vi đặc biệt có thể bị lợi dụng khi object được dựng lại. Một **gadget chain** là chuỗi nhiều gadget được dữ liệu độc hại kích hoạt nối tiếp để dẫn tới hành vi nguy hiểm. Đây là ý nghĩa của cụm “gadget/deserialization attack” khi nói về lịch sử rủi ro của native serialization.

~~~text
untrusted bytes
    ↓
ObjectInputStream.readObject()
    ↓
object graph reconstruction + class-specific hooks
    ↓
risk
~~~

`ObjectInputFilter` có thể giới hạn class, graph depth, số reference hoặc kích thước trong hệ thống bắt buộc phải hỗ trợ serialization:

~~~java
ObjectInputFilter filter =
        ObjectInputFilter.Config.createFilter(
                "com.example.Note;java.base/*;!*"
        );

objectIn.setObjectInputFilter(filter);
~~~

Chuỗi filter trên cho phép class `com.example.Note`, cho phép các class thuộc module `java.base`, rồi dùng `!*` để từ chối các class còn lại. Filter phải được gắn vào `ObjectInputStream` **trước lần đọc object đầu tiên** như `readObject()`/`readUnshared()`, để giới hạn được áp dụng ngay từ lúc graph bắt đầu được dựng lại.

Filter là **lớp bảo vệ bổ sung (defense in depth)**, không phải lý do để biến việc deserialize native data không tin cậy thành lựa chọn mặc định.

Native serialization cũng mang các ranh giới ngoài security:

| Ranh giới | Hệ quả |
| --- | --- |
| Gắn với Java class/classpath | Khó làm contract giữa ngôn ngữ khác nhau |
| Gắn với class evolution | Cần quản lý compatibility/version |
| Binary protocol khó quan sát | Debug/inspect kém trực tiếp hơn text/schema format |
| Dựng object graph | Boundary có hành vi runtime lớn hơn parse DTO đơn giản |
| Lịch sử gadget chain/deserialization attack | Input không tin cậy là rủi ro nghiêm trọng |

Với network API, message contract hoặc persistence dài hạn, thường nên ưu tiên format có contract rõ như JSON, Protocol Buffers hoặc format/schema phù hợp hệ thống, kết hợp DTO/versioning có chủ ý. Việc chọn format cụ thể phụ thuộc bài toán, nhưng **Java native serialization không nên là khuyến nghị tổng quát**.

Nếu một codebase cũ đã dùng serialization, cần biết cơ chế để đọc, bảo trì và thu hẹp boundary của nó. Chương cuối sẽ đặt serialization bên cạnh stream, reader/writer, Files và channel để hình thành quy tắc chọn abstraction I/O đơn giản và đúng nhất.
