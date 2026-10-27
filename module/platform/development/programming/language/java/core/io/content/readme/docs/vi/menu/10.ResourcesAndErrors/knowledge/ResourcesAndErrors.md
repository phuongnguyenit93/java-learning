<a id="back-to-top"></a>

# Tài nguyên và lỗi

## Menu
- [IOException và các lỗi thường gặp khi mở, đọc hoặc ghi](#resources-and-errors-001)
- [EOF bình thường khác dữ liệu bị cắt cụt](#resources-and-errors-002)
- [Closeable, AutoCloseable và try-with-resources](#resources-and-errors-003)
- [Thứ tự đóng tài nguyên và suppressed exception](#resources-and-errors-004)
- [Ai mở, ai đóng: quy tắc sở hữu trong thiết kế API](#resources-and-errors-005)
- [Đóng stream bọc, tài nguyên chia sẻ và tài nguyên do bên gọi truyền vào](#resources-and-errors-006)
- [Cleanup khi tạo hoặc ghép tài nguyên thất bại](#resources-and-errors-007)
- [UncheckedIOException khi kết hợp I/O với Stream API](#resources-and-errors-008)
- [Đọc hoặc ghi dở dang khi có ngoại lệ](#resources-and-errors-009)
- [Ngắt, đóng từ luồng khác và hành vi phụ thuộc loại I/O](#resources-and-errors-010)

## <a id="resources-and-errors-001">IOException và các lỗi thường gặp khi mở, đọc hoặc ghi</a>

<details>
<summary>Xem chi tiết</summary>

# IOException và các lỗi thường gặp khi mở, đọc hoặc ghi trong Java I/O

## 1. Mục tiêu: hiểu lỗi I/O xảy ra ở đâu và xử lý đúng

Một chương trình Java đọc tệp tưởng như chỉ gồm ba bước: mở, đọc, đóng. Thực tế, mỗi bước có thể thất bại độc lập vì dữ liệu I/O nằm ngoài quyền kiểm soát hoàn toàn của JVM: tệp bị di chuyển, quyền truy cập thay đổi, thiết bị hết dung lượng, kết nối bị cắt, đường dẫn trỏ tới thư mục thay vì tệp, hoặc thao tác đóng phát hiện dữ liệu chưa thể ghi.

`IOException` là ngoại lệ kiểm tra (*checked exception*) nền tảng của `java.io` và nhiều API `java.nio.file`, báo rằng một thao tác nhập/xuất đã thất bại hoặc bị gián đoạn theo hợp đồng của API. Bài này giúp phân biệt:

- **Lỗi mở**: không thể có được một luồng/kênh hoặc tạo tệp theo yêu cầu.
- **Lỗi đọc**: tài nguyên đã mở, nhưng không thể lấy dữ liệu tiếp hoặc dữ liệu không phù hợp với hợp đồng đọc.
- **Lỗi ghi**: không thể đưa dữ liệu tới đích, có thể sau khi một phần đã được ghi.
- **Lỗi flush/close**: kết thúc thao tác có thể thất bại; không được coi “đã gọi write” đồng nghĩa dữ liệu đã lưu an toàn.
- **Phản ứng của chương trình**: ghi nhận nguyên nhân, dọn tài nguyên, quyết định có thử lại, kiểm tra hậu điều kiện và tránh làm hỏng dữ liệu.

Một chuỗi xử lý thường có dạng:

~~~text
Path / nguồn I/O
    |
    v
OPEN  --IOException--> phân loại lỗi mở (không tồn tại, quyền, trùng tên...)
    |
    v
READ / WRITE --IOException--> biết đã xử lý được một phần hay chưa?
    |
    v
FLUSH / CLOSE --IOException--> không được đánh mất lỗi chính
    |
    v
Đánh giá hậu điều kiện / ghi log / báo lỗi cho người dùng
~~~

Không phải mọi lỗi xảy ra cạnh thao tác I/O đều là `IOException`. Ví dụ, `NullPointerException` khi truyền tham số null, `IllegalArgumentException` khi cấu hình không hợp lệ, và `SecurityException` do chính sách truy cập có thể là ngoại lệ runtime. Không bắt `IOException` rồi cho rằng đã xử lý được mọi lỗi của chương trình.

## 2. Phân cấp ngoại lệ cần biết

`IOException` kế thừa `Exception`, nên là **checked exception**: phương thức phát sinh thường phải được xử lý bằng `try/catch` hoặc khai báo `throws`. Một số lớp thường gặp:

| Loại | Khi nào hay gặp | Ghi chú |
| --- | --- | --- |
| `IOException` | Lỗi I/O không có kiểu con cụ thể hoặc cần trừu tượng hóa | Không nên suy diễn nguyên nhân chỉ từ thông điệp |
| `FileNotFoundException` | Mở tệp qua `FileInputStream`/`FileOutputStream` thất bại | Có thể do không tồn tại **hoặc** nguyên nhân khác như quyền, đường dẫn không hợp lệ theo ngữ cảnh |
| `NoSuchFileException` | `java.nio.file.Files` không tìm thấy tệp/thành phần cần thiết | Là kiểu con của `FileSystemException` |
| `AccessDeniedException` | Từ chối truy cập ở tầng hệ thống tệp | Là kiểu con của `FileSystemException`; môi trường có thể báo kiểu khác |
| `FileAlreadyExistsException` | Cố tạo tệp đã tồn tại trong thao tác không cho phép ghi đè | Thường liên quan `CREATE_NEW` hoặc `Files.createFile` |
| `FileSystemException` | Lỗi hệ thống tệp có thông tin tệp, tệp khác, lý do tùy môi trường | Phù hợp khi chẩn đoán lỗi NIO |
| `EOFException` | API yêu cầu một lượng dữ liệu bắt buộc, nhưng kết thúc quá sớm | Khác với EOF bình thường là `read() == -1` |
| `UTFDataFormatException` | Lỗi dữ liệu UTF theo định dạng mà API cụ thể mong đợi | Hay gặp khi `DataInput.readUTF()` gặp dữ liệu modified UTF-8 không hợp lệ |
| `CharacterCodingException` | Lỗi mã hóa/giải mã ký tự theo chính sách báo lỗi | Có thể có các lớp con như `MalformedInputException` |
| `SocketException` | Lỗi socket trong quá trình giao tiếp mạng | Phụ thuộc ngữ cảnh và trạng thái kết nối |
| `InterruptedIOException` | I/O bị gián đoạn theo hợp đồng một số API | Không đánh đồng mọi gián đoạn thread với ngoại lệ này |

Các lớp nói trên không thay thế việc đọc hợp đồng của phương thức gọi. Với cùng một tình huống “tệp không tồn tại”, `Files.newInputStream(path)` thường ném `NoSuchFileException`, còn `new FileInputStream(file)` thường ném `FileNotFoundException`. Loại cụ thể và thông điệp có thể khác giữa provider hệ thống tệp, OS và API.

### 2.1. `throws IOException` hay `catch IOException`?

Phương thức ở tầng thấp thường **khai báo `throws IOException`** để tầng gọi biết có thể thất bại:

~~~java
static byte[] load(Path path) throws IOException {
    return Files.readAllBytes(path);
}
~~~

Đây là đoạn phương thức minh họa; cần import `java.nio.file.Files`, `java.nio.file.Path` và `java.io.IOException`; nó không phải chương trình độc lập. Dù ngắn, đây vẫn là một quyết định về API: caller phải xử lý lỗi, không âm thầm trả byte[] rỗng khi việc đọc thất bại.

Ở tầng điều phối ứng dụng, nên **bắt nơi có đủ thông tin để quyết định**: thử lại, trả lỗi HTTP, hiển thị thông báo, hoặc ngừng xử lý. Không cần thêm catch ở mọi lớp chỉ để in stack trace rồi ném lại.

### 2.2. Một lỗi thường không có “mã lỗi” bất biến xuyên nền tảng

Không nên viết logic nghiệp vụ bằng cách tìm chuỗi `"Permission denied"` hay `"The system cannot find the file specified"` trong `getMessage()`. Thông điệp phụ thuộc hệ điều hành, provider, ngôn ngữ và phiên bản.

Nếu API trả kiểu con ổn định như `NoSuchFileException` hoặc `FileAlreadyExistsException`, hãy bắt theo kiểu cụ thể. Nếu chỉ nhận `IOException` tổng quát, giữ nguyên `cause` và log đầy đủ ngữ cảnh để chẩn đoán thay vì đoán từ thông điệp.

## 3. Lỗi trong giai đoạn mở tài nguyên

### 3.1. Đường dẫn không tồn tại hoặc sai thành phần

Với NIO:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    // Đọc tệp...
} catch (NoSuchFileException e) {
    // Không tìm thấy tệp hoặc thành phần cần thiết.
}
~~~

Đây là đoạn minh họa, với `Path path` đã có và các import `java.io.InputStream`, `java.nio.file.Files`, `java.nio.file.NoSuchFileException`. Một đường dẫn chưa tồn tại không phải lúc nào cũng là lỗi: khi **ghi để tạo tệp mới**, chương trình có thể chủ động dùng `CREATE` hoặc `CREATE_NEW`. Điều quan trọng là phân biệt **mở để đọc** với **mở để ghi/tạo**.

Không dùng `Files.exists(path)` rồi coi đó là bảo đảm tệp sẽ còn tồn tại lúc `open`. Giữa hai thao tác, tiến trình khác có thể xóa hoặc di chuyển tệp. Đây là vấn đề **time-of-check to time-of-use (TOCTOU)**. Hãy xử lý ngoại lệ của thao tác thực sự mở tài nguyên, thay vì chỉ dựa vào lần kiểm tra trước đó.

### 3.2. Truy cập bị từ chối

`AccessDeniedException` có thể xuất hiện khi người dùng chạy chương trình không có quyền cần thiết, thư mục không cho truy cập, hoặc chính sách filesystem từ chối thao tác. Tuy nhiên, quyền thực tế còn phụ thuộc ACL, quyền sở hữu, sandbox, file lock và provider.

Cách xử lý tốt thường là báo đường dẫn liên quan và yêu cầu quyền hợp lệ; **không** tự động đổi quyền của hàng loạt tệp hoặc chạy chương trình với đặc quyền cao hơn chỉ vì thao tác thất bại. Trong môi trường server, log nội bộ có thể chứa đường dẫn chi tiết, còn thông báo cho người dùng nên tránh tiết lộ cấu trúc thư mục nhạy cảm.

### 3.3. Tệp đã tồn tại và lựa chọn ghi đè

`Files.createFile(path)` và `Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)` yêu cầu tạo đối tượng mới; nếu đã tồn tại, thường nhận `FileAlreadyExistsException`.

~~~java
try (OutputStream out = Files.newOutputStream(
        path, StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE)) {
    out.write(data);
} catch (FileAlreadyExistsException e) {
    // Không ghi đè dữ liệu đang có.
}
~~~

Đoạn minh họa sử dụng `Path path` và `byte[] data` có sẵn, cùng import từ `java.io`, `java.nio.file`. Với ứng dụng cần bảo vệ tệp hiện có, `CREATE_NEW` tốt hơn mô hình “kiểm tra `exists` rồi tự mở” vì điều kiện không tồn tại và thao tác tạo được yêu cầu thực hiện nguyên tử theo hợp đồng của API đối với provider hỗ trợ.

Ngược lại, khi dùng `Files.newOutputStream(path)` **không truyền option**, mặc định thường tương đương tạo nếu cần và cắt ngắn tệp nếu tồn tại. Đây có thể là hành vi phá dữ liệu nếu người lập trình nhầm với “mở để nối thêm”.

### 3.4. Tệp là thư mục, đường dẫn vô hiệu và các vấn đề filesystem

Nguồn lỗi mở không chỉ là thiếu tệp:

- Đích mong đợi tệp thường nhưng lại là thư mục.
- Thành phần thư mục cha chưa tồn tại.
- Đường dẫn quá dài hoặc bị provider không hỗ trợ.
- Tệp bị khóa hoặc đang được ứng dụng khác sử dụng theo cách không tương thích.
- Ổ đĩa ngoài bị ngắt kết nối hay filesystem chuyển sang trạng thái chỉ đọc.

Loại ngoại lệ cụ thể không nhất thiết giống nhau trên mọi OS. Thay vì viết nhiều điều kiện phỏng đoán cho mọi trường hợp, xử lý nguyên nhân hữu ích theo kiểu khi có thể và để `IOException` còn lại đi theo đường báo lỗi đầy đủ.

## 4. Ví dụ Java 17: phân biệt tệp không tồn tại và tệp đã có

Lưu thành tệp **OpenFileFailuresDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class OpenFileFailuresDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("io-errors-demo-");
        Path file = directory.resolve("data.txt");

        try {
            try {
                Files.readString(file, StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                System.out.println("Missing file: true");
            }

            Files.createFile(file);
            try {
                Files.createFile(file);
            } catch (FileAlreadyExistsException e) {
                System.out.println("Already exists: true");
            }

            Files.writeString(file, "Java I/O", StandardCharsets.UTF_8);
            System.out.println("Content: " +
                    Files.readString(file, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 OpenFileFailuresDemo.java
java OpenFileFailuresDemo
~~~

Kết quả trên filesystem thông thường hỗ trợ các thao tác nêu trên:

~~~text
Missing file: true
Already exists: true
Content: Java I/O
~~~

Chương trình tự tạo thư mục tạm mới, nên tên cụ thể của thư mục không ảnh hưởng output. Lần đọc đầu mở một tệp chưa từng được tạo trong thư mục đó, minh họa `NoSuchFileException`. Lần tạo thứ hai dùng `Files.createFile` trên tệp đã tồn tại, minh họa `FileAlreadyExistsException`. Sau cùng, chương trình ghi rồi đọc nội dung bình thường.

`finally` dọn tài nguyên thử nghiệm. Nếu quá trình xóa thất bại, nó cũng có thể ném `IOException`; ví dụ nhỏ này chấp nhận để caller thấy lỗi dọn dẹp. Trong hệ thống sản xuất, cần quản lý ngoại lệ dọn dẹp để không làm mất dấu nguyên nhân chính, như phần `try-with-resources` phía dưới.

## 5. Lỗi khi đọc: EOF bình thường khác IOException

### 5.1. EOF không phải lỗi trong vòng lặp đọc hết

Với `InputStream.read()`, giá trị `-1` biểu diễn **hết dữ liệu** (EOF). Giá trị `0..255` là byte hợp lệ. Một vòng lặp:

~~~java
int value;
while ((value = input.read()) != -1) {
    // Xử lý value.
}
~~~

Đây là đoạn minh họa với `InputStream input` đã mở. EOF bình thường không yêu cầu ném `EOFException`: nó là điều kiện kết thúc hợp lệ khi mục tiêu là “đọc cho tới hết”.

Với `read(byte[])`, hàm trả số byte thực tế hoặc `-1` khi hết dữ liệu. **Đừng xem một lần đọc ngắn hơn buffer là lỗi**: luồng tệp, socket và wrapper có thể trả ít byte hơn yêu cầu dù vẫn còn dữ liệu. Luôn dựa vào `count` để xử lý phần hợp lệ.

### 5.2. EOFException: dữ liệu bắt buộc bị thiếu

Trường hợp khác là giao thức cần đọc **đúng N byte**, ví dụ 4 byte đầu biểu diễn số nguyên. `DataInputStream.readInt()` cần đủ bốn byte; nếu nguồn chỉ có hai byte rồi EOF, phương thức sẽ ném `EOFException`.

~~~java
try (DataInputStream in = new DataInputStream(
        new ByteArrayInputStream(new byte[] {0x01, 0x02}))) {
    int number = in.readInt(); // Thiếu hai byte: EOFException.
}
~~~

Đoạn minh họa cần import `java.io.DataInputStream` và `ByteArrayInputStream`, cùng xử lý `IOException`. Đây không phải lỗi của `read() == -1` theo nghĩa thông thường: **hợp đồng đọc đủ dữ liệu bắt buộc không được đáp ứng**.

`InputStream.readNBytes(n)` có thể trả mảng ngắn hơn `n` nếu EOF sớm; `readNBytes(byte[], off, len)` trả số byte thực sự đọc. Nếu nghiệp vụ đòi chính xác N byte, caller phải kiểm tra số lượng và tự ném `EOFException` hoặc thông báo lỗi thiếu dữ liệu. Không nên tự suy ra mọi API “read N” đều ném cùng loại ngoại lệ.

### 5.3. IOException giữa chừng không phải EOF

Một nguồn có thể đọc thành công vài byte rồi thất bại vì kết nối bị cắt hoặc thiết bị lỗi. Ví dụ:

~~~text
Lượt 1: read(buffer) → 100 byte hợp lệ
Lượt 2: read(buffer) → 72 byte hợp lệ
Lượt 3: read(buffer) → IOException
~~~

Nếu chương trình bắt ngoại lệ và coi như đã EOF, nó sẽ giữ một dữ liệu **bị cắt cụt** mà không báo lỗi. Đây là lỗi logic nguy hiểm khi đọc ảnh, file cấu hình, bản ghi giao dịch hoặc gói dữ liệu có định dạng.

Một chiến lược đúng:

- Đọc đến `-1` nếu hợp đồng cho phép dữ liệu có độ dài không biết trước.
- Nếu cần kiểm tra kích thước, checksum hoặc dấu kết thúc, so sánh chúng **sau khi đọc thành công**.
- Khi đọc ném `IOException`, giữ trạng thái kết quả là **thất bại**, không “thành công với ít dữ liệu”.
- Chỉ sử dụng prefix dữ liệu đã đọc nếu nghiệp vụ định nghĩa rõ kết quả một phần có giá trị và cung cấp cờ/trạng thái tương ứng.

### 5.4. read() có thể chặn

Với nguồn sống như socket, `read()` có thể chờ dữ liệu. Việc chưa trả byte không phải `IOException`, và một số API có cơ chế timeout riêng. Đừng dùng `available() == 0` để quyết định EOF: đó là số byte có thể đọc ngay theo hợp đồng lớp, **không** nhất thiết là toàn bộ nội dung còn lại. Với I/O không chặn, giá trị 0 còn có thể nghĩa là tạm thời chưa có dữ liệu, khác với `-1` là kết thúc.

## 6. Ví dụ Java 17: lỗi trong quá trình đọc sau khi đã có dữ liệu

Để kiểm tra logic mà không phụ thuộc vào thiết bị thật, có thể dùng một `InputStream` tự tạo luôn đọc được hai byte rồi cố ý ném `IOException`. Demo chứng minh lỗi không bị coi như EOF.

Lưu thành **ReadFailureDemo.java**:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ReadFailureDemo {
    static class FailingInput extends InputStream {
        private int position;

        @Override
        public int read() throws IOException {
            if (position == 0) {
                position++;
                return 'O';
            }
            if (position == 1) {
                position++;
                return 'K';
            }
            throw new IOException("Simulated read failure");
        }
    }

    public static void main(String[] args) {
        try (InputStream input = new FailingInput();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            int next;
            while ((next = input.read()) != -1) {
                output.write(next);
            }
            System.out.println("Read completed");
        } catch (IOException e) {
            System.out.println("Read failed: " + e.getMessage());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReadFailureDemo.java
java ReadFailureDemo
~~~

Kết quả:

~~~text
Read failed: Simulated read failure
~~~

Hai byte `O` và `K` đã được đọc trước khi lỗi, nhưng chương trình không in `Read completed` vì vòng lặp chưa kết thúc bằng EOF. Trong ứng dụng thật, chính sách có thể trả một đối tượng kết quả mang trạng thái `PARTIAL_FAILURE`, hoặc hủy toàn bộ việc xử lý để tránh trình bày dữ liệu chưa hoàn chỉnh như thành công.

**Lưu ý:** Đây là `InputStream` mô phỏng lỗi được thiết kế cho kiểm thử, không phải một cách gây lỗi hệ điều hành. Nó cũng chỉ ghi một byte mỗi lần để demo dễ kiểm chứng; khi đọc lượng lớn, thường nên dùng mảng buffer.

## 7. Lỗi khi ghi: dữ liệu có thể đã được ghi một phần

### 7.1. write không trả số byte đã ghi

`OutputStream.write(byte[], off, len)` theo API trả về `void`. Khi hoàn tất thành công, nó được kỳ vọng ghi đúng đoạn được yêu cầu theo hợp đồng; khi thất bại, nó có thể ném `IOException`. Nếu lỗi xảy ra sau một phần dữ liệu, **API tổng quát không trả về thông số đáng tin cậy “đã ghi được bao nhiêu byte”**.

Vì vậy, không nên:

~~~java
try {
    output.write(payload);
} catch (IOException e) {
    output.write(payload); // Sai: có thể ghi trùng một phần đã thành công.
}
~~~

Đây là đoạn minh họa **cách làm không an toàn**, với `output` và `payload` đã có. Nếu lần thứ nhất đã đẩy được một số byte rồi lỗi, ghi lại toàn bộ sẽ có nguy cơ trùng dữ liệu. Trên mạng, ngay cả việc client có thấy IOException không cũng chưa đủ kết luận server đã hay chưa nhận thông điệp.

Thử lại cần dựa vào **đặc tính thao tác và giao thức**:

- **Idempotent**: lặp lại yêu cầu nhiều lần vẫn cho hiệu quả cuối tương đương. Ví dụ thay thế nội dung tệp tạm hoàn chỉnh rồi đổi tên theo giao thức xác định, khi được thiết kế đúng.
- **Không idempotent**: mỗi lần ghi là một sự kiện mới hoặc append có thể nhân đôi dữ liệu. Không tự thử lại toàn bộ.
- **Có xác nhận**: giao thức có mã yêu cầu, vị trí/offset, checksum hoặc xác nhận để đối chiếu trạng thái và khôi phục an toàn.

### 7.2. Thiết bị hết dung lượng, tệp chỉ đọc, kết nối đóng

Các nguyên nhân phổ biến:

- Filesystem hết dung lượng hoặc vượt quota.
- Không còn quyền ghi tại thời điểm thao tác.
- Ổ đĩa/thiết bị bị tháo, tệp trở nên không khả dụng.
- Đích là socket đã bị đóng hoặc kết nối mạng lỗi.
- Tên đường dẫn mới không hợp lệ hoặc cấu trúc thư mục không tồn tại.
- Luồng/kênh đã đóng trước đó (tùy API có thể ném `IOException` hoặc ngoại lệ khác).

Một ví dụ dễ nhầm là `Files.writeString(path, text)`: lệnh gọi thành công cho biết API đã hoàn tất thao tác ghi theo hợp đồng, **không nhất thiết** là dữ liệu đã được đồng bộ tới bộ lưu trữ bền vững vật lý. Nếu bài toán yêu cầu tính bền vững khi mất điện, cần thảo luận mức độ đồng bộ và hợp đồng filesystem riêng.

### 7.3. Ghi đè hay append là quyết định nghiệp vụ

Một thao tác ghi cần được thiết kế rõ ràng: tạo mới duy nhất, thay thế toàn bộ hay nối thêm:

~~~java
Files.writeString(path, "hello", StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
~~~

Đoạn minh họa có `Path path` và import NIO cần thiết. Với lựa chọn trên, tồn tại sẵn thường gây `FileAlreadyExistsException`. Nếu muốn nối thêm:

~~~java
Files.writeString(path, "append", StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
~~~

Đây cũng là đoạn minh họa, không độc lập. `APPEND` không tự thêm dấu xuống dòng và không làm mỗi lần ghi trở thành một giao dịch nguyên tử đối với mọi filesystem. Nếu logic yêu cầu “mỗi bản ghi nguyên vẹn hoặc không có”, cần giải pháp phù hợp hơn là đơn thuần gọi append.

## 8. Buffered I/O: flush() và close() cũng có thể báo lỗi

`BufferedWriter` và `BufferedOutputStream` thường giữ dữ liệu trong bộ nhớ trước khi chuyển tới đích bên dưới. Vì thế:

1. Một lời gọi `write` có thể chỉ ghi vào buffer của ứng dụng và thành công.
2. `flush()` yêu cầu chuyển phần dữ liệu đang đệm xuống luồng bên dưới; ở bước này mới có thể xuất hiện `IOException`.
3. `close()` có thể flush/đóng tài nguyên và báo lỗi.
4. Không đồng nhất `flush()` với bảo đảm dữ liệu **bền vững trên ổ đĩa**. Việc đồng bộ vật lý, nếu cần, liên quan API và filesystem như `FileChannel.force(...)`, cũng không tạo bảo đảm tuyệt đối cho mọi ngữ cảnh phần cứng.

Ví dụ:

~~~java
try (BufferedWriter writer =
        Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("Tài liệu quan trọng");
} // writer.close() được gọi ở đây và có thể ném IOException.
~~~

Đoạn minh họa với `Path path` đã có. Nếu `writer.write(...)` thành công nhưng close thất bại, không được ghi nhận toàn bộ thao tác là thành công chắc chắn; cần xử lý lỗi từ cả lúc viết và lúc đóng.

### 8.1. Đừng tự close trong try-with-resources

Không cần:

~~~java
try (OutputStream out = Files.newOutputStream(path)) {
    out.write(payload);
    out.close(); // Thừa; làm code khó quản lý vòng đời.
}
~~~

Đây là đoạn minh họa. `try-with-resources` tự động gọi close cuối phạm vi, kể cả khi thân `try` ném ngoại lệ. Gọi sớm có thể không phù hợp nếu còn sử dụng stream sau đó và có thể khiến xử lý lỗi khó theo dõi.

## 9. try-with-resources: bảo toàn lỗi chính và suppressed exceptions

Cú pháp `try-with-resources` ưu tiên cho tài nguyên triển khai `AutoCloseable`, trong đó `InputStream`, `OutputStream`, `Reader` và `Writer` thường áp dụng được.

~~~java
try (InputStream input = Files.newInputStream(source);
     OutputStream output = Files.newOutputStream(target)) {
    input.transferTo(output);
}
~~~

Đây là đoạn minh họa với `Path source` và `Path target`. Các tài nguyên được đóng **theo thứ tự ngược với thứ tự khai báo**: `output` rồi `input`.

Giả sử:

- Trong thân `try` xảy ra lỗi đọc `IOException("read failed")`.
- Khi đóng output, lại xảy ra lỗi `IOException("close failed")`.

Nếu dùng `try-with-resources`, ngoại lệ từ thân `try` là lỗi chính, còn lỗi đóng được gắn vào danh sách **suppressed exceptions** thông qua `Throwable.getSuppressed()`. Điều này giúp chẩn đoán cả hai nguyên nhân mà không đánh mất lỗi đầu tiên.

### 9.1. Ví dụ Java 17 tự tạo lỗi khi đóng

Lưu **SuppressedExceptionDemo.java**:

~~~java
import java.io.IOException;

public class SuppressedExceptionDemo {
    static class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("read failed");
        } catch (IOException e) {
            System.out.println("Primary: " + e.getMessage());
            System.out.println("Suppressed count: " +
                    e.getSuppressed().length);
            for (Throwable problem : e.getSuppressed()) {
                System.out.println("Suppressed: " + problem.getMessage());
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 SuppressedExceptionDemo.java
java SuppressedExceptionDemo
~~~

Kết quả:

~~~text
Primary: read failed
Suppressed count: 1
Suppressed: close failed
~~~

Đây là phép mô phỏng có kiểm soát, không phụ thuộc lỗi thiết bị thật. Nó chứng minh một ngoại lệ khi `close()` vẫn được giữ trong thông tin chẩn đoán ngay cả khi thân `try` đã thất bại trước đó.

Nếu **thân `try` hoàn tất bình thường** nhưng `close()` ném ngoại lệ, lỗi đóng trở thành ngoại lệ chính. Khi mở nhiều tài nguyên, có thể xuất hiện nhiều suppressed exception. Khi xử lý và log, nên giữ stack trace/cause đầy đủ thay vì chỉ lưu `getMessage()`.

### 9.2. Tại sao không nên viết finally với close một cách bất cẩn?

Mẫu cũ:

~~~java
InputStream in = null;
try {
    in = Files.newInputStream(path);
    // Đọc...
} finally {
    if (in != null) {
        in.close();
    }
}
~~~

Đoạn minh họa này có thể làm lỗi từ `close()` trong `finally` **che khuất** lỗi gốc trong thân `try`. Việc xử lý thủ công để giữ nguyên cả hai ngoại lệ rất dễ thiếu sót. Với Java hiện đại, ưu tiên `try-with-resources` khi có thể. Nếu buộc phải dùng `finally`, cần chủ động bảo toàn exception chính, suppressed, và bảo đảm mọi tài nguyên đều có cơ hội được đóng.

## 10. Thiết kế catch: khi nào bắt cụ thể, khi nào ném tiếp?

### 10.1. Bắt kiểu cụ thể trước kiểu tổng quát

Ví dụ API cần phân biệt ba tình huống:

~~~java
try {
    Files.readString(path, StandardCharsets.UTF_8);
} catch (NoSuchFileException e) {
    // Thiếu tệp: có thể báo mã lỗi "not found".
} catch (AccessDeniedException e) {
    // Không có quyền: báo "permission denied".
} catch (IOException e) {
    // Những lỗi I/O khác.
}
~~~

Đây là đoạn minh họa với `Path path` đã khai báo và import Java NIO. Hai lớp đầu đều là kiểu con của `IOException`, nên phải bắt trước. Nếu đặt `catch(IOException)` lên đầu, các catch bên dưới trở thành không thể tới và Java sẽ từ chối biên dịch.

Mỗi catch cần **hành động phù hợp**; không bắt nhiều kiểu chỉ để in cùng một thông báo rồi lặng lẽ cho chương trình tiếp tục như thể không có lỗi.

### 10.2. Ném tiếp nhưng bổ sung ngữ cảnh

Một lớp xử lý tệp biết tên tài nguyên hoặc thao tác đang làm có thể bổ sung ngữ cảnh:

~~~java
static byte[] readDocument(Path path) throws IOException {
    try {
        return Files.readAllBytes(path);
    } catch (IOException e) {
        throw new IOException("Failed to read document: " + path, e);
    }
}
~~~

Đây là đoạn phương thức minh họa; chưa phải chương trình độc lập. Cú pháp constructor `new IOException(message, cause)` giữ lại ngoại lệ gốc thông qua `getCause()`. Lưu ý: nếu tầng trên cần phân biệt `NoSuchFileException` bằng `catch`, bọc mọi lỗi thành `IOException` tổng quát sẽ làm mất **kiểu cụ thể ở tầng ngoài**. Vì vậy chỉ bọc khi muốn thay đổi ranh giới trừu tượng, hoặc dùng chiến lược giữ nguyên loại ngoại lệ khi cần.

Đừng bọc rồi vứt nguyên nhân:

~~~java
catch (IOException e) {
    throw new RuntimeException("Failed"); // Thiếu e làm cause.
}
~~~

Đây là ví dụ không nên làm: thông tin stack trace gốc khó truy tìm hơn. Nếu API buộc dùng unchecked exception, nên dùng `new UncheckedIOException("...", e)` cho `IOException` hoặc một exception miền nghiệp vụ có `cause`.

### 10.3. Kiểm tra log và thông báo

Log hữu ích nên chứa **thao tác**, **đường dẫn/đích đã được xử lý an toàn**, **loại ngoại lệ**, **stack trace/cause** và khi cần **số byte đã xử lý**. Tránh ghi lại toàn bộ nội dung dữ liệu nhạy cảm chỉ để chẩn đoán.

Ví dụ thông báo cho người dùng: “Không thể lưu tệp báo cáo. Hãy kiểm tra dung lượng và quyền thư mục.” Còn trong log có thể giữ `IOException` đầy đủ. Không cần tiết lộ đường dẫn hệ thống nội bộ cho người dùng cuối nếu môi trường có yêu cầu bảo mật.

## 11. Thử lại (retry) có phù hợp không?

Retry chỉ phù hợp khi lỗi có tính **tạm thời** và thao tác có thể lặp an toàn. Bảng gợi ý:

| Tình huống | Retry tự động? | Lý do |
| --- | --- | --- |
| `NoSuchFileException` vì người dùng chọn sai tên tệp | Thường không | Cần sửa đầu vào hoặc kiểm tra nguồn |
| `AccessDeniedException` do quyền cố định | Thường không | Lặp lại không tạo quyền mới |
| `FileAlreadyExistsException` khi `CREATE_NEW` | Không nên ghi đè tự động | Vi phạm mục tiêu bảo vệ tệp hiện hữu |
| Mất kết nối mạng tạm thời trước khi truyền gì | Có thể, tùy giao thức | Cần xác minh trạng thái và điều kiện thử lại |
| Ghi append thất bại giữa chừng | Không retry toàn bộ nếu chưa có giao thức chống trùng | Có thể chèn dữ liệu lặp |
| Đọc tệp không đổi từ đầu sau lỗi tạm thời | Có thể, nếu nguồn/phiên bản được kiểm soát | Không được giả định tệp vẫn giữ nội dung cũ |
| Không đủ dung lượng ổ đĩa | Thường không ngay lập tức | Cần giải phóng dung lượng hoặc đổi đích |

Nếu thử lại, nên:

1. Giới hạn số lần và thời gian tổng.
2. Có khoảng chờ phù hợp; với dịch vụ phân tán có thể dùng exponential backoff và jitter.
3. Xác định **điều kiện an toàn/idempotence**.
4. Ghi nhận nguyên nhân của lần thất bại và kết quả cuối.
5. Không retry khi đã bị hủy/cần dừng, và không dùng retry để che sai định dạng hoặc thiếu quyền.

Ví dụ nếu ghi tệp bằng một tệp tạm rồi thay thế đích, mỗi lần thử cần tạo tệp tạm mới hoặc xử lý chính xác trạng thái tệp tạm cũ. Nếu gửi dữ liệu cho server, cần mã yêu cầu idempotency hoặc xác nhận theo giao thức để tránh nhân đôi tác vụ.

## 12. Ghi tệp an toàn hơn khi lỗi xuất hiện giữa chừng

Trực tiếp ghi đè tệp đích có rủi ro: nếu tiến trình lỗi sau khi tệp đã bị cắt ngắn mà trước khi hoàn tất, dữ liệu cũ đã mất, dữ liệu mới chưa đầy đủ.

Một chiến lược thường dùng cho **một tệp**:

1. Tạo **tệp tạm riêng** trong cùng thư mục/ filesystem với đích.
2. Ghi toàn bộ nội dung mới vào tệp tạm; đóng tài nguyên và kiểm tra hậu điều kiện.
3. Chỉ khi quá trình ghi/đóng hoàn tất, thử **di chuyển tệp tạm sang đích**.
4. Khi cần cam kết đổi tên nguyên tử, yêu cầu `StandardCopyOption.ATOMIC_MOVE` và xử lý `AtomicMoveNotSupportedException` thay vì lặng lẽ hạ cấp khi hợp đồng nghiệp vụ không cho phép.
5. Nếu cần cam kết bền vững khi mất điện, xem xét `FileChannel.force`, đồng bộ dữ liệu/metadata và đặc tính hệ thống tệp; **atomic rename không đồng nghĩa với fsync hoặc transaction toàn hệ thống**.
6. Nếu bất kỳ bước nào lỗi, báo kết quả thất bại và dọn tệp tạm theo chiến lược bảo toàn thông tin lỗi.

Ví dụ minh họa phần chuyển tệp:

~~~java
Files.move(temp, target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
~~~

Đây là đoạn minh họa với `Path temp`, `Path target`. `ATOMIC_MOVE` chỉ có thể dùng khi provider hỗ trợ, và tác động khi đích tồn tại mang sắc thái phụ thuộc provider theo hợp đồng API. Đừng tự suy ra rằng câu lệnh bảo đảm mọi nội dung và metadata được ghi bền vững khi sự cố nguồn điện xảy ra.

Đây là **mô hình thiết kế** chứ không phải thay thế cơ chế transaction của cơ sở dữ liệu hay bảo đảm cập nhật nhiều tệp nguyên tử cùng lúc. Chọn mức độ an toàn tương ứng tính quan trọng của dữ liệu.

## 13. Ví dụ Java 17: sao chép bằng try-with-resources và kiểm tra kết quả

Lưu thành **SafeCopyDemo.java**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class SafeCopyDemo {
    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("safe-copy-");
        Path source = folder.resolve("source.bin");
        Path target = folder.resolve("target.bin");

        try {
            byte[] original = "Java IO 17".getBytes(
                    StandardCharsets.US_ASCII);
            Files.write(source, original);

            long copied;
            try (InputStream in = Files.newInputStream(source);
                 OutputStream out = Files.newOutputStream(target)) {
                copied = in.transferTo(out);
            }

            byte[] result = Files.readAllBytes(target);
            System.out.println("Copied bytes: " + copied);
            System.out.println("Matches: " +
                    Arrays.equals(original, result));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(folder);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 SafeCopyDemo.java
java SafeCopyDemo
~~~

Kết quả:

~~~text
Copied bytes: 10
Matches: true
~~~

Điểm quan trọng:

- `transferTo` trả số byte đã chuyển và không tự đóng luồng; `try-with-resources` đóng `out` rồi `in`.
- Sau khi đóng thành công, chương trình đọc đích và so sánh byte thực tế, tránh tin rằng “đã gọi write” tương đương “đã hoàn thành toàn bộ nội dung” khi có lỗi.
- Ví dụ không tạo lỗi I/O cố ý; nếu filesystem không cho tạo/xóa/ghi trong thư mục tạm, ngoại lệ sẽ được ném ra thay vì bị nuốt.
- Hàm `finally` ở ví dụ chỉ dọn tệp tạm. Với hệ thống sản xuất, nếu cleanup cũng có thể thất bại khi lỗi chính đã tồn tại, nên thiết kế báo cáo riêng hoặc ghép suppressed exception để bảo toàn nguyên nhân đầu tiên.
- Đây là thao tác sao chép demo; nó **không** bảo đảm ghi file đích theo chiến lược thay thế nguyên tử. Với nội dung nghiệp vụ quan trọng, áp dụng quy trình tệp tạm và di chuyển phù hợp.

## 14. FileInputStream/FileOutputStream và Files/NIO: khác nhau khi báo lỗi

Các API Java truyền thống và NIO có thể biểu đạt lỗi theo kiểu khác nhau:

~~~java
new FileInputStream("missing.txt");   // Thường FileNotFoundException.
Files.newInputStream(Path.of("missing.txt")); // Thường NoSuchFileException.
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. Tên tệp ví dụ được giả định không tồn tại; trong thực tế điều kiện môi trường quyết định kết quả. Cần import tương ứng.

`FileNotFoundException` không nhất thiết chỉ mang nghĩa tệp thiếu; nó cũng có thể được dùng khi không mở được vì quyền hoặc các nguyên nhân tương tự. Khi muốn phân biệt lý do ở hệ thống tệp, các lớp NIO như `NoSuchFileException`, `AccessDeniedException`, `FileAlreadyExistsException` và `FileSystemException` thường giúp xử lý rõ hơn, nhưng không được giả định mọi provider luôn báo đúng cùng kiểu.

Khi chuyển mã cũ sang NIO, hãy kiểm tra lại các `catch`: nếu chỉ bắt `FileNotFoundException`, có thể bỏ sót `NoSuchFileException`. Nếu dùng `catch(IOException)` ở tầng hợp lý, vẫn xử lý được phần lớn lỗi checked I/O.

## 15. IOException với văn bản: lỗi charset không giống thiếu tệp

Đọc tệp văn bản có ít nhất **hai bước logic**: lấy byte từ nguồn và **giải mã** byte theo charset. Nếu nguồn chứa byte không hợp lệ với charset và API/decoder đang ở chế độ báo lỗi, có thể phát sinh `MalformedInputException` (kiểu con của `CharacterCodingException` và `IOException`).

Ví dụ:

~~~java
String text = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa với `Path path`. Nếu tệp có dữ liệu không hợp lệ theo UTF-8, cách đọc này có thể ném ngoại lệ giải mã. Không nên bắt mọi `IOException` rồi báo “Không tìm thấy tệp”; tệp có thể tồn tại và đọc byte được, nhưng văn bản không hợp lệ.

Sự khác biệt:

- **NoSuchFileException**: tài nguyên không tìm thấy theo thao tác hệ thống tệp.
- **AccessDeniedException**: quyền truy cập không đủ.
- **MalformedInputException**: chuỗi byte không hợp lệ theo charset, khi dùng giải mã báo lỗi.
- **EOFException**: không đủ dữ liệu cho một trường bắt buộc theo hợp đồng đọc định dạng.

Khi dùng `InputStreamReader` hoặc `CharsetDecoder`, hành vi dữ liệu sai còn phụ thuộc `CodingErrorAction.REPORT`, `REPLACE` hoặc `IGNORE`. Nếu cần phân biệt lỗi dữ liệu, hãy chọn chính sách `REPORT` phù hợp; nếu dùng chế độ thay thế, cần biết dữ liệu có thể được sửa thành `�` thay vì ném lỗi.

Không dùng `new String(bytes)` với charset mặc định khi nguồn quy định UTF-8: lỗi đọc “ký tự lạ” đôi khi là lỗi chọn charset, không phải lỗi thiếu tệp hoặc I/O thiết bị.

## 16. IOException trong hệ thống mạng và luồng được bọc

`IOException` cũng có thể phát sinh từ socket, stream nén, stream giải mã, wrapper buffering hoặc nguồn I/O khác. Với TCP, các thông điệp như “connection reset” hoặc “broken pipe” có thể xuất hiện tùy OS và thời điểm. Không nên dựa trên một chuỗi thông báo cố định để suy ra server đã thực sự xử lý bao nhiêu dữ liệu.

Một số quy tắc:

- `read() == -1` trên socket stream biểu diễn kết thúc đầu vào theo hợp đồng; `IOException` là thất bại, không phải kết thúc bình thường.
- Kết nối có thể bị đóng giữa lúc ghi; kết quả trên phía nhận còn cần giao thức xác nhận.
- `flush()` chỉ đẩy dữ liệu trong các lớp đệm mà nó quản lý, không đồng nghĩa phía bên kia đã xử lý giao dịch.
- Nếu có timeout ở API, hãy xử lý timeout theo **trạng thái nghiệp vụ**, không mặc định retry mù.
- Khi bọc luồng (`BufferedInputStream`, `GZIPInputStream`, `InputStreamReader`), thông thường đóng wrapper sẽ đóng nguồn bên dưới. Xác định quyền sở hữu trước khi dùng `try-with-resources`.

Đối với luồng nén, dữ liệu không hoàn chỉnh có thể được phát hiện sau khi đọc khá xa hoặc khi kết thúc, tùy định dạng/checksum. Vì vậy cần để luồng báo lỗi qua hết vòng đời và không xem một phần đầu giải nén được là bằng chứng toàn bộ dữ liệu hợp lệ.

## 17. Thiết kế API để không mất thông tin lỗi

Một phương thức đọc tệp thường trả kết quả theo một trong hai cách rõ ràng:

**Cách A — checked exception:**

~~~java
byte[] load(Path source) throws IOException
~~~

Caller bắt hoặc truyền tiếp `IOException`. Đây là chữ ký minh họa, không phải phương thức đầy đủ.

**Cách B — kết quả tường minh:** đối tượng kết quả có thể chứa trạng thái thành công/thất bại và metadata cần thiết, phù hợp khi xử lý hàng loạt nhiều tệp mà không muốn một lỗi dừng toàn bộ batch. Dù dùng kiểu nào, đừng trả mảng rỗng hoặc chuỗi rỗng để biểu diễn cả “tệp rỗng hợp lệ” lẫn “đọc thất bại”.

Ví dụ cách làm cần tránh:

~~~java
static String readOrEmpty(Path path) {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        return ""; // Không phân biệt tệp rỗng thật với đọc lỗi.
    }
}
~~~

Đây là đoạn phương thức minh họa **anti-pattern**. Nó biến lỗi hạ tầng thành dữ liệu nghiệp vụ giả, làm việc debug và kiểm tra kết quả khó hơn.

Khi bắt `IOException` chỉ để đổi sang exception miền nghiệp vụ, hãy giữ `cause`. Nếu caller cần biết loại lỗi cụ thể, bổ sung mã lỗi có cấu trúc hoặc để ngoại lệ gốc đi xuyên tầng phù hợp.

## 18. Checklist kiểm tra lỗi I/O

Trước khi đánh dấu một thao tác hoàn tất, hãy tự kiểm tra:

1. **Nguồn/đích nào?** Tệp, mạng, bộ nhớ hay wrapper? Có biết quyền sở hữu tài nguyên không?
2. **Thao tác nào thất bại?** Open, read, write, flush hay close? Có ghi lại giai đoạn và ngữ cảnh không?
3. **Kiểu ngoại lệ là gì?** NoSuchFile, AccessDenied, AlreadyExists, EOFException, CharacterCodingException hay IOException tổng quát?
4. **Đã xử lý được một phần dữ liệu?** Một phần đó có được phép dùng hay phải bỏ?
5. **Có dọn tài nguyên?** `try-with-resources` đã bảo toàn ngoại lệ đóng như suppressed chưa?
6. **Có nhận nhầm EOF thành lỗi hoặc ngược lại?** `-1` là kết thúc bình thường của vòng lặp đọc hết; `EOFException` khác.
7. **Có vô tình ghi đè dữ liệu cũ?** Chế độ mở và option đã được chỉ định rõ?
8. **Có kiểm tra hậu điều kiện?** Kích thước, checksum, nội dung, giao thức xác nhận hoặc trạng thái tệp đích khi phù hợp?
9. **Retry có an toàn?** Có giới hạn thử lại, cơ chế idempotency hoặc quyết định dựa trên trạng thái?
10. **Log có ích và an toàn?** Có ngữ cảnh, stack trace/cause nhưng không tiết lộ dữ liệu nhạy cảm?
11. **Có quy tắc về bền vững?** Nếu cần chống mất điện, đã xem xét fsync/force và giới hạn bảo đảm của hệ thống tệp?
12. **Có kiểm thử tình huống lỗi?** Thiếu tệp, tệp trùng, đọc ngắn, EOF sớm, lỗi đọc sau một phần dữ liệu, lỗi close và lỗi charset.

## 19. Lỗi phổ biến cần tránh

| Lỗi của người lập trình | Hệ quả | Hướng sửa |
| --- | --- | --- |
| Bắt `IOException` rồi trả `""` hoặc byte[] rỗng | Che lỗi bằng kết quả trông hợp lệ | Ném tiếp hoặc trả trạng thái lỗi riêng |
| `catch(IOException)` chỉ `printStackTrace()` rồi tiếp tục như thành công | Trạng thái chương trình sai | Tầng xử lý phải quyết định phục hồi hoặc kết thúc |
| Xem `read() == -1` là ngoại lệ | Bắt lỗi không cần thiết | Dùng -1 làm EOF bình thường |
| Xem `IOException` giữa chừng là EOF bình thường | Chấp nhận dữ liệu bị cắt | Báo trạng thái thất bại và không giả định payload đủ |
| Tin rằng `read(buffer)` luôn đầy | Đọc thiếu hoặc xử lý dữ liệu cũ | Dùng giá trị `count` |
| Dùng `available() == 0` để kết thúc | Dừng sớm với luồng sống | Đọc cho tới -1 hoặc theo giao thức |
| Tin rằng write thành công là đã fsync ổ đĩa | Đánh giá sai tính bền vững | Xác định rõ mức bảo đảm cần thiết |
| Retry toàn bộ thao tác append sau IOException | Có thể ghi trùng một phần | Chỉ retry khi có cơ chế an toàn |
| `finally` gọi close làm che lỗi chính | Mất nguyên nhân ban đầu | Ưu tiên `try-with-resources` |
| Chỉ log `e.getMessage()` | Thiếu stack trace/cause/suppressed | Giữ thông tin exception đầy đủ |
| Bắt `FileNotFoundException` cho mọi API NIO | Bỏ sót các loại lỗi NIO khác | Bắt lớp con cụ thể đúng API hoặc IOException |
| Dùng `Files.exists` để “bảo đảm” mở thành công | TOCTOU khiến điều kiện thay đổi | Xử lý ngoại lệ khi mở thực sự |
| Dùng `CREATE_NEW` nhưng tự ghi đè nếu đã có | Mất mục tiêu bảo vệ dữ liệu | Quyết định rõ chính sách conflict |
| Dùng sai charset rồi báo “file hỏng” không kiểm tra | Nhầm lỗi dữ liệu/mã hóa | Chọn charset đúng, kiểm tra chính sách decoder |
| Che ngoại lệ cleanup hoàn toàn | Không phát hiện lỗi dọn dẹp có ảnh hưởng | Giữ lỗi phụ bằng suppressed hoặc log có ngữ cảnh |

## 20. Bài tập tự kiểm tra

**Bài 1 — EOF hay lỗi?** Một vòng lặp `InputStream.read()` đọc được 10 byte rồi trả -1. Có cần ném `EOFException` không?

**Đáp án:** không, nếu mục tiêu là đọc hết luồng. EOF là điều kiện dừng hợp lệ. Nếu giao thức bắt buộc 20 byte, phải báo lỗi thiếu dữ liệu vì dữ liệu chưa đủ.

**Bài 2 — Tệp không tồn tại.** Hai phương thức `new FileInputStream(path)` và `Files.newInputStream(path)` có bắt buộc ném cùng một kiểu con không?

**Đáp án:** không. API cũ thường ném `FileNotFoundException`; NIO thường ném `NoSuchFileException`. Chọn catch theo API thực tế và vẫn có đường xử lý `IOException` tổng quát.

**Bài 3 — Sau khi write lỗi, có được tự ghi lại toàn bộ payload?**

**Đáp án:** không mặc định. Một phần payload có thể đã được gửi hoặc lưu, và ghi lại từ đầu có thể gây trùng. Cần giao thức idempotent/offset/ack hoặc quy trình thay thế an toàn.

**Bài 4 — Hai lỗi cùng lúc.** Trong `try-with-resources`, thân ném `IOException("read failed")` và close ném `IOException("close failed")`. Ngoại lệ chính là gì?

**Đáp án:** `read failed` là lỗi chính; `close failed` nằm trong `getSuppressed()`, như ví dụ `SuppressedExceptionDemo`.

**Bài 5 — Đọc UTF-8.** Tệp tồn tại và có quyền đọc, nhưng byte bên trong không tạo thành văn bản UTF-8 hợp lệ. Vì sao vẫn nhận `IOException`?

**Đáp án:** lỗi phát sinh ở bước **giải mã ký tự**, ví dụ `MalformedInputException` với chính sách REPORT, chứ không nhất thiết ở bước mở file. Không báo sai thành “tệp không tồn tại”.

**Bài 6 — Ghi thành công có đủ bảo đảm dữ liệu bền vững không?**

**Đáp án:** chưa đủ cho mọi yêu cầu. `write`, `flush`, `close` và đồng bộ dữ liệu vật lý có các hợp đồng khác nhau; nếu hệ thống yêu cầu tính bền vững qua mất điện, cần thiết kế riêng và tìm hiểu bảo đảm của filesystem/thiết bị.

### Bài tập thực hành nâng cao

1. Sửa `OpenFileFailuresDemo` để bắt thêm `IOException` tổng quát và in tên **lớp ngoại lệ** nếu phát sinh, không phân loại bằng chuỗi thông điệp.
2. Thêm `FileAlreadyExistsException` vào một chương trình tạo báo cáo: nếu đích đã có, ứng dụng phải dừng với thông báo rõ ràng, không xóa/ghi đè.
3. Viết một `InputStream` kiểm thử ném IOException ở byte thứ N và xác nhận kết quả được đánh dấu **thất bại**, không phải thành công với payload ngắn.
4. Thêm một tài nguyên thứ hai cũng lỗi khi `close()` vào `SuppressedExceptionDemo`; kiểm tra thứ tự đóng ngược và danh sách suppressed.
5. Thiết kế pseudocode cho “ghi tệp tạm → xác minh → di chuyển sang đích” với nhánh xử lý khi `ATOMIC_MOVE` không hỗ trợ, nhánh cleanup lỗi và quy tắc không làm mất dữ liệu cũ.

## Kết luận

`IOException` không chỉ có nghĩa “không tìm thấy tệp”: nó có thể xuất hiện ở **mở, đọc, ghi, flush hoặc close**, mỗi giai đoạn mang một kiểu rủi ro khác nhau. Một ứng dụng Java I/O đáng tin cậy cần:

- Phân biệt **EOF bình thường** với **thiếu dữ liệu bắt buộc** và **lỗi đọc thật**.
- Bắt lớp con cụ thể khi hữu ích, nhưng giữ đường xử lý `IOException` tổng quát và nguyên nhân gốc.
- Dùng `try-with-resources` để đóng tài nguyên, xem `getSuppressed()` khi có nhiều lỗi.
- Không âm thầm biến thất bại thành kết quả rỗng; không retry ghi mù vì đầu ra có thể đã được ghi một phần.
- Đặt chính sách rõ về ghi đè, append, dữ liệu tạm, hậu điều kiện, độ bền dữ liệu và an toàn khi lỗi.

### Tài liệu Java SE 17

- [IOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html)
- [InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html)
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [FileSystemException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemException.html)
- [NoSuchFileException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/NoSuchFileException.html)
- [AccessDeniedException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/AccessDeniedException.html)
- [FileAlreadyExistsException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileAlreadyExistsException.html)
- [Throwable.getSuppressed()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed())
- [Java try-with-resources — Oracle Tutorial](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-002">EOF bình thường khác dữ liệu bị cắt cụt</a>

<details>
<summary>Xem chi tiết</summary>

# EOF bình thường khác dữ liệu bị cắt cụt trong Java I/O

Khi đọc file, socket hoặc một nguồn dữ liệu bằng Java, việc gặp **EOF** (*end of file/end of stream*) có thể là điều hoàn toàn bình thường: nguồn không còn phần tử để đọc. Nhưng EOF cũng có thể xuất hiện **quá sớm** so với lượng dữ liệu một giao thức, định dạng file hay bản ghi yêu cầu; khi đó ứng dụng phải nhận diện **dữ liệu bị cắt cụt** (*truncated data*).

Điểm cốt lõi là **EOF chỉ nói “không còn dữ liệu từ nguồn”; nó không tự xác nhận “thông điệp đã đầy đủ”**. Muốn phân biệt EOF hợp lệ với cắt cụt, chương trình phải biết **điều kiện hoàn tất ở tầng ứng dụng**: kích thước kỳ vọng, dấu kết thúc, số bản ghi, framing, checksum, cấu trúc định dạng hoặc quy tắc được xác định trước.

Bài học thuộc nhóm **Tài nguyên và lỗi** của Java Core I/O. Nội dung hướng dẫn các dấu hiệu EOF trong API, cách đọc đủ dữ liệu, phân biệt kết thúc hợp lệ với kết thúc sớm, các ví dụ Java 17 chạy độc lập, những bẫy với dữ liệu văn bản và mạng, cùng chiến lược kiểm tra tính toàn vẹn.

## 1. Định nghĩa: EOF và truncation không phải hai trạng thái đối lập hoàn toàn

**EOF** (*end of file/end of stream*) là trạng thái cho biết không còn dữ liệu tiếp theo trong nguồn đọc. Trong Java:

- `InputStream.read()` trả **`-1`** khi không còn byte để đọc.
- `Reader.read()` trả **`-1`** khi không còn đơn vị ký tự để đọc.
- `BufferedReader.readLine()` trả **`null`** khi không còn dòng để đọc.
- `DataInputStream` có những thao tác bắt buộc đủ byte, như `readInt()` và `readFully(...)`, có thể ném **`EOFException`** nếu thiếu dữ liệu trước khi hoàn tất.
- `ByteBuffer` và `CharBuffer` không tự đồng nghĩa “EOF”; chúng là bộ đệm. API kênh hoặc bộ giải mã quyết định cách báo trạng thái kết thúc.

**Truncation** là tình huống nội dung **ngắn hơn hoặc thiếu thành phần** so với một định dạng/contract. Ví dụ một khung tin khai báo payload 100 byte nhưng thực tế chỉ nhận được 60 byte trước EOF.

Một nguồn byte có thể kết thúc bình thường ở tầng hệ điều hành nhưng vẫn là dữ liệu **không hợp lệ ở tầng ứng dụng**. Ngược lại, một file ngắn có thể là file hoàn toàn hợp lệ nếu định dạng của nó cho phép độ dài đó.

| Tình huống | Có EOF? | Có bị cắt cụt? | Lý do |
|---|---|---|---|
| File text hợp lệ kết thúc sau dòng cuối không có newline | Có | **Không nhất thiết** | Nhiều quy ước cho phép dòng cuối kết thúc bởi EOF |
| Byte stream không hứa độ dài; caller đọc đến `-1` | Có | **Không thể kết luận chỉ từ EOF** | Không có kích thước/sentinel kỳ vọng |
| Header ghi “payload dài 100 byte” nhưng chỉ có 60 byte | Có | **Có** | Thiếu 40 byte theo contract |
| Header 4 byte mới nhận được 2 byte rồi EOF | Có | **Có**, nếu header 4 byte bắt buộc | Không thể giải mã trọn header |
| File định dạng có footer bắt buộc nhưng footer biến mất | Có | **Có**, nếu footer bắt buộc | Thiếu cấu trúc hoàn tất |
| Kết nối mạng tạm thời chưa có byte để đọc | Chưa chắc | Chưa chắc | Thiếu dữ liệu ngay lúc đó không đồng nghĩa EOF |
| Đọc `read(buffer)` được ít hơn dung lượng buffer | Chưa chắc | Chưa chắc | Một lần đọc ngắn không chứng minh EOF hoặc truncation |

**Chỉ caller hiểu contract mới xác định được lúc nào EOF là bình thường.** API I/O có thể cung cấp dấu hiệu, nhưng không tự biết một bản ghi nghiệp vụ hoàn chỉnh gồm những gì.

## 2. EOF hiển thị như thế nào trong Java?

### `InputStream.read()` và `Reader.read()`

`InputStream.read()` trả về `int` trong khoảng **`0..255`** cho byte thực tế và **`-1`** khi EOF. Vì byte là kiểu có dấu trong Java, việc dùng `int` tránh nhầm byte `0xFF` với giá trị đặc biệt `-1`.

`Reader.read()` trả về `int` trong khoảng **`0..65535`** cho một UTF-16 `char`, hoặc **`-1`** khi EOF. Nó không nhất thiết đọc nguyên một Unicode code point cho mỗi lần gọi; ký tự ngoài BMP có thể gồm hai code unit.

Ví dụ vòng lặp đọc byte đến EOF:

```java
int value;
while ((value = input.read()) != -1) {
    // Xử lý byte từ 0 đến 255.
}
```

Đây là **đoạn minh họa**, không phải chương trình hoàn chỉnh: `input` phải là một `InputStream` đã khởi tạo và phương thức cần xử lý `IOException`. Mẫu vòng lặp đúng là so sánh `value != -1`; không ép về `byte` rồi so `-1`, bởi byte dữ liệu `0xFF` sẽ bị nhầm thành EOF.

`read()` có thể **chặn** (block) để đợi byte, trả byte, báo EOF hoặc ném `IOException` theo đặc tính nguồn. **Đang đợi dữ liệu** không có nghĩa đã tới EOF.

### `read(byte[])` và `read(char[])`

`int n = input.read(buffer)` trả về **số byte thực sự đọc được** hoặc `-1` nếu EOF (với mảng có độ dài dương và theo hợp đồng chuẩn). `Reader.read(char[])` tương tự ở đơn vị UTF-16 code unit.

Không suy luận:

- “Buffer dài 4096, lần này được 100 byte, vậy chắc dữ liệu đã bị cắt.”
- “Mỗi lần đọc phải lấp đầy cả buffer.”
- “Đọc ngắn chính là EOF.”

Một lần đọc có thể hợp lệ dù trả ít hơn số phần tử được yêu cầu. Với mảng/đoạn đọc có `len == 0`, Java có thể trả **`0`**; đừng xem `0` như EOF.

Vòng lặp đọc một luồng không biết trước kích thước:

```java
byte[] buffer = new byte[4096];
int n;
while ((n = input.read(buffer)) != -1) {
    output.write(buffer, 0, n);
}
```

Đây là **mã minh họa**: cần có `input` và `output` đã khởi tạo, quản lý đóng và `IOException`. Mục tiêu là sao chép đến EOF. Nếu có một số byte cần đạt, **phải đếm và xác thực thêm** trước khi coi kết quả thành công.

### Các cách đọc theo dòng

`BufferedReader.readLine()` trả `null` khi không còn dòng; một dòng rỗng hợp lệ trả `""`. Không được dùng `line.isEmpty()` để xác định EOF, vì dòng rỗng là dữ liệu.

Ví dụ:

```java
String line;
while ((line = reader.readLine()) != null) {
    // Có thể là chuỗi rỗng nhưng vẫn là một dòng hợp lệ.
}
```

Đoạn trên cần `reader` là `BufferedReader` đã mở. Cần chú ý `readLine()` **loại dấu kết thúc dòng** (LF, CRLF hoặc CR); vì thế nó có thể đọc cùng một chuỗi dòng cuối dù file có hay không có terminal newline.

## 3. EOF là bình thường khi nào?

EOF có thể là **dấu hiệu hoàn tất hợp lệ** khi giao thức/định dạng yêu cầu đọc cho đến hết nguồn mà **không có điều kiện hoàn tất khác**. Một vài ví dụ:

1. Đọc toàn bộ file text tự do, không yêu cầu dấu xuống dòng cuối.
2. Đọc hết một file nhị phân bất kỳ chỉ để sao chép **toàn bộ nội dung hiện có**, không có số byte kỳ vọng và không cần khẳng định tính nguyên vẹn nguồn ban đầu.
3. Một giao thức ứng dụng được thiết kế dùng **đóng kết nối làm dấu kết thúc thông điệp**, với các điều kiện an toàn phù hợp. Cần phân biệt đây là một lựa chọn framing được quy định, không phải quy tắc của mọi giao thức mạng.
4. Đọc một collection/nguồn hữu hạn được định nghĩa là “tất cả phần tử đến khi không còn phần tử”.

Tại đây, ứng dụng thực thi một contract dạng: *“Lấy những gì nguồn đang cung cấp cho đến EOF.”* EOF là điều kiện dừng của thao tác, không tự báo lỗi.

**Giới hạn:** “Sao chép đến EOF thành công” không chứng minh file hay thông điệp ở nguồn **không bị thiếu so với bản gốc của tác giả**. Nếu không có kích thước, checksum hoặc cấu trúc chuẩn đáng tin cậy để đối chiếu, caller có thể không có cách phát hiện thiếu dữ liệu.

## 4. EOF trở thành dấu hiệu cắt cụt khi nào?

EOF là bất thường khi xuất hiện **trước một điều kiện hoàn tất bắt buộc**. Các dạng thường gặp:

- **Độ dài cố định:** cần đúng 16 byte để đọc một trường nhị phân nhưng chỉ lấy được 11.
- **Độ dài khai báo trong header:** header nói body có 500 byte nhưng nguồn hết khi mới có 200.
- **Dấu kết thúc bắt buộc:** định dạng yêu cầu footer, marker, checksum hoặc thẻ đóng, nhưng đọc hết nguồn mà không gặp.
- **Số lượng phần tử khai báo:** file chứa header “10 bản ghi” nhưng mới parse được 9.
- **Bản ghi nhiều trường:** đã đọc trường đầu nhưng còn thiếu trường bắt buộc.
- **Mã hóa ký tự không hoàn chỉnh:** chuỗi UTF-8 cần thêm continuation byte, nhưng dòng byte kết thúc giữa một code point.
- **Luồng nén hoặc container:** cấu trúc nội bộ yêu cầu footer/CRC/độ dài hoặc block kết thúc mà dữ liệu đã bị cắt bỏ.

Từng trường hợp cần **một tiêu chí kỹ thuật rõ** để xác định thiếu gì. Không nên ném một `EOFException` tùy tiện chỉ vì đạt EOF trong một chương trình vốn thiết kế đọc đến hết file.

### Ví dụ khung dữ liệu độ dài cố định

Giả sử một giao thức quy định:

```text
+--------------------+-------------------------+
| Length (4 byte)    | Payload (length byte)   |
| big-endian signed  |                         |
+--------------------+-------------------------+
```

Nếu byte stream chỉ còn hai byte trong header, **không thể đọc đủ `int`**. Nếu header có đủ nhưng payload ngắn hơn `length`, cũng là cắt cụt. Nếu đủ header và đủ payload, frame là hoàn chỉnh **theo điều kiện độ dài** (dù nội dung payload vẫn có thể không hợp lệ theo nghiệp vụ hoặc charset).

Một điểm quan trọng: header khai báo độ dài phải được kiểm tra **không âm** và **không vượt giới hạn** để tránh cấp phát mảng khổng lồ từ dữ liệu không đáng tin. Khi dữ liệu đến từ mạng, kiểm tra giới hạn kích thước trước khi cấp phát hay đọc đủ payload.

## 5. Các API đọc đến EOF so với API đòi hỏi đủ dữ liệu

| API | Khi hết nguồn | Ý nghĩa |
|---|---|---|
| `InputStream.read()` | Trả `-1` | EOF bình thường ở mức nguồn |
| `InputStream.read(byte[])` | Trả `-1` nếu không còn byte và len dương | Mỗi lần đọc trước đó có thể ngắn |
| `InputStream.readNBytes(int len)` | Trả mảng có **tối đa** `len` byte; có thể ít hơn nếu EOF | **Caller phải kiểm tra** kích thước nếu cần đủ |
| `InputStream.readNBytes(byte[], off, len)` | Trả số byte đã đọc, có thể nhỏ hơn `len` ở EOF | Caller cần so sánh với số byte kỳ vọng |
| `InputStream.readAllBytes()` | Trả mọi byte còn lại đến EOF | Không biết độ dài kỳ vọng nếu caller không cung cấp |
| `InputStream.transferTo(output)` | Trả số byte chuyển được đến EOF | Sao chép đến EOF, không tự xác nhận số byte mong muốn |
| `DataInputStream.readFully(byte[])` | **Ném `EOFException`** nếu chưa điền đủ mảng | Cực kỳ tiện cho trường có độ dài bắt buộc |
| `DataInputStream.readInt()` | Ném `EOFException` nếu chưa có đủ 4 byte | Đọc trường nhị phân cố định |
| `BufferedReader.readLine()` | Trả `null` khi không còn dòng | Không tự kiểm tra newline cuối |
| `Reader.read()` | Trả `-1` | EOF ở mức ký tự |
| `FileChannel.read(ByteBuffer)` | Trả `-1` khi kênh đã tới end-of-stream | Không tự biết số byte định dạng cần |
| `SocketChannel.read(ByteBuffer)` | Trả `-1` nếu kênh đã tới end-of-stream | Chế độ non-blocking còn có thể trả `0` khi chưa đọc được byte |

**Lưu ý:** `EOFException` là lớp con của `IOException`. Nó thường xuất hiện từ API cần một số byte xác định (ví dụ `DataInputStream`), không có nghĩa mọi thao tác `InputStream.read()` đều ném ngoại lệ tại EOF.

Nếu đọc một file mà chỉ biết “đọc đến khi hết”, việc dùng `readAllBytes()` có thể phù hợp cho dữ liệu nhỏ, nhưng gây dùng nhiều RAM với file lớn. Với dữ liệu lớn, ưu tiên đọc khối có giới hạn, stream hoặc các cơ chế phù hợp. Tránh coi `readAllBytes()` là bằng chứng rằng file có cấu trúc đúng.

## 6. Ví dụ 1 — `-1` là EOF, còn đọc thiếu kích thước kỳ vọng phải được kiểm tra

Tạo tệp `StreamEofDemo.java` (Java 17+):

```java
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

public class StreamEofDemo {
    public static void main(String[] args) throws Exception {
        byte[] bytes = {10, 20};
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            System.out.println("first=" + in.read());
            System.out.println("second=" + in.read());
            System.out.println("EOF=" + in.read());
            System.out.println("EOF again=" + in.read());
        }

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            byte[] result = in.readNBytes(4);
            System.out.println("requested=4, received=" + result.length);
            System.out.println("data=" + Arrays.toString(result));
            System.out.println("complete fixed-size field=" + (result.length == 4));
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 StreamEofDemo.java
java StreamEofDemo
```

Đầu ra:

```text
first=10
second=20
EOF=-1
EOF again=-1
requested=4, received=2
data=[10, 20]
complete fixed-size field=false
```

**Giải thích:**

1. Hai lời gọi `read()` đầu lấy byte 10 và 20; lời gọi tiếp theo trả `-1` vì mảng hết dữ liệu. Đó là **EOF bình thường ở tầng stream**. Ví dụ gọi tiếp một lần trên `ByteArrayInputStream` vẫn nhận `-1`.
2. `readNBytes(4)` được yêu cầu **tối đa** 4 byte nhưng mảng chỉ có 2. Nó trả mảng dài 2 **mà không tự ném `EOFException`**.
3. Nếu ứng dụng chỉ muốn “đọc mọi byte đang có”, 2 byte có thể là kết quả hoàn chỉnh. Nếu trường giao thức yêu cầu **chính xác 4 byte**, biểu thức `result.length == 4` là `false` và dữ liệu là **cắt cụt theo contract**.

Đừng coi một giá trị `-1` luôn là lỗi, cũng đừng coi việc `readNBytes` trả về thành công là bằng chứng đã đủ dữ liệu.

## 7. `DataInputStream.readFully` — đọc chính xác độ dài bắt buộc

`DataInputStream` bọc một `InputStream` để đọc các kiểu dữ liệu nhị phân theo quy ước của Java. Hai phương thức rất hữu ích:

- `readInt()` yêu cầu đúng **4 byte**, đọc số nguyên có dấu theo thứ tự big-endian. Nếu hết nguồn khi chưa đủ 4 byte, ném `EOFException`.
- `readFully(byte[])` cố đọc cho đến khi **điền đủ mảng**, nguồn báo EOF hoặc có lỗi I/O. Nếu EOF trước khi điền đủ, ném `EOFException`. Lưu ý không có lời hứa rằng nó sẽ nhanh hay chắc chắn hoàn thành nếu nguồn vẫn mở mà không gửi đủ dữ liệu.

Có thể tự viết vòng lặp đọc đúng n byte và so sánh số lượng, nhưng `readFully` đã cung cấp semantics “phải đủ” rõ ràng cho các trường byte cố định.

### Ví dụ 2 — dữ liệu length-prefixed đầy đủ, thiếu header và thiếu payload

Tạo tệp `FramedPayloadDemo.java` (Java 17+):

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class FramedPayloadDemo {
    static byte[] frame(String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            data.writeInt(payload.length);
            data.write(payload);
        }
        return out.toByteArray();
    }

    static String readFrame(byte[] bytes) throws IOException {
        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = in.readInt();
            if (size < 0 || size > 1024) {
                throw new IOException("Invalid length");
            }
            byte[] payload = new byte[size];
            in.readFully(payload);
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    static void verify(String label, byte[] frame) throws IOException {
        try {
            System.out.println(label + "=" + readFrame(frame));
        } catch (EOFException e) {
            System.out.println(label + "=TRUNCATED (" + e.getClass().getSimpleName() + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] complete = frame("OK");
        verify("complete", complete);
        verify("short header", new byte[] {0, 0});
        verify("short payload", Arrays.copyOf(complete, complete.length - 1));
    }
}
```

```bash
javac -encoding UTF-8 --release 17 FramedPayloadDemo.java
java FramedPayloadDemo
```

```text
complete=OK
short header=TRUNCATED (EOFException)
short payload=TRUNCATED (EOFException)
```

**Diễn giải ba ca kiểm thử:**

1. **`complete`:** chương trình ghi header `int` bằng độ dài byte của `"OK"` theo UTF-8, rồi ghi đúng payload. `readInt()` và `readFully` thành công.
2. **`short header`:** chỉ hai byte tồn tại trong khi `readInt()` cần bốn; `EOFException` được ném.
3. **`short payload`:** header vẫn khai báo đủ hai byte payload, nhưng mảng đã bị cắt mất một byte cuối. `readFully` phát hiện EOF trước khi đọc đủ.

Trong ví dụ, `size` được giới hạn `0..1024` **trước khi cấp phát mảng**. Đó là một bước an toàn bắt buộc với input không tin cậy; ứng dụng thực tế còn cần ràng buộc bộ nhớ, timeout và tổng số frame.

**Điểm cần chú ý:** ví dụ xác nhận đã nhận đủ **một frame** theo header, nhưng không kiểm tra dữ liệu dư sau frame vì một kết nối thật có thể chứa **nhiều frame nối tiếp**. Nếu định dạng yêu cầu “toàn file chứa đúng một frame và không còn byte thừa”, hãy bổ sung kiểm tra EOF **sau** frame theo hợp đồng đó.

Ngoài ra, `new String(payload, UTF_8)` là bước giải mã text và có thể thay byte UTF-8 không hợp lệ. Việc đủ **số byte** và việc payload là **văn bản UTF-8 hợp lệ** là hai tiêu chí khác nhau, cần kiểm tra riêng nếu định dạng yêu cầu strict Unicode.

## 8. Phân biệt dấu EOF với lỗi I/O khác

Không phải mọi thao tác đọc thất bại đều là EOF:

| Tín hiệu hoặc ngoại lệ | Điều nói được | Không nên kết luận |
|---|---|---|
| `read() == -1` | Nguồn đã báo hết dữ liệu | Toàn thông điệp nghiệp vụ chắc chắn đầy đủ |
| `EOFException` khi `readFully` | Chưa đủ dữ liệu cho thao tác bắt buộc | Bản thân nguồn luôn “có lỗi”; thiếu có thể do file cắt hoặc contract sai |
| `SocketTimeoutException` | Thao tác socket vượt thời gian chờ theo cấu hình | Peer đã đóng và chắc chắn không gửi thêm |
| `IOException` khác | Có vấn đề I/O cụ thể | Mọi lỗi đều chỉ do hết file |
| `read(buffer)` trả số dương nhỏ | Đọc được một phần dữ liệu | Đã EOF hoặc đã bị cắt |
| Đọc non-blocking `SocketChannel` trả `0` | Hiện chưa lấy được byte từ lời gọi này | EOF |
| `BufferedReader.readLine() == null` | Không còn dòng | Dòng cuối phải có newline hay không |

**Timeout khác truncation.** Nếu ứng dụng đang đợi thêm byte của một frame và vượt deadline, có thể báo thao tác **không hoàn tất đúng hạn**; chưa đủ bằng chứng rằng peer đã kết thúc luồng. Có thể retry hoặc hủy theo chính sách, nhưng không được tự coi timeout là EOF.

**Kết nối TCP đóng không đồng nghĩa thông điệp hợp lệ.** Một TCP FIN khiến phía đọc tiến tới EOF sau khi tiêu thụ dữ liệu còn lại, nhưng thông điệp có đủ header/body hay không là trách nhiệm framing ở tầng ứng dụng. TCP reset có thể xuất hiện dưới dạng lỗi I/O chứ không phải EOF sạch. Phải xử lý cả hai đường.

**Đóng nguồn bởi code của chính ứng dụng khác việc nguồn đã tự hết dữ liệu.** Gọi `close()` để giải phóng tài nguyên không phải cơ chế phát hiện bản ghi đã hoàn chỉnh.

## 9. Văn bản: dòng cuối không có newline có phải bị cắt?

Không có quy tắc chung rằng file text **phải** kết thúc bằng LF. Nhiều chương trình đọc được dòng cuối mà không có dấu xuống dòng. `BufferedReader.readLine()` có thể trả lại dòng cuối ở cả hai trường hợp:

```text
"alpha\nbeta"   (không có LF ở cuối)
"alpha\nbeta\n" (có LF ở cuối)
```

Cả hai đều có hai nội dung dòng là `alpha` và `beta`. Nếu gọi `readLine()` lần thứ ba, cả hai đều trả `null`. Nhưng **byte gốc khác nhau** và hợp đồng của một định dạng cụ thể có thể yêu cầu newline cuối.

### Ví dụ 3 — `readLine` không tự xác nhận terminal newline

Tạo tệp `TextLineEofDemo.java`:

```java
import java.io.BufferedReader;
import java.io.StringReader;

public class TextLineEofDemo {
    static void show(String label, String text) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            System.out.println(label + " line1=" + reader.readLine());
            System.out.println(label + " line2=" + reader.readLine());
            System.out.println(label + " EOF=" + (reader.readLine() == null));
            System.out.println(label + " terminal LF=" + text.endsWith("\n"));
        }
    }

    public static void main(String[] args) throws Exception {
        show("no LF", "alpha\nbeta");
        show("with LF", "alpha\nbeta\n");
    }
}
```

```bash
javac -encoding UTF-8 --release 17 TextLineEofDemo.java
java TextLineEofDemo
```

```text
no LF line1=alpha
no LF line2=beta
no LF EOF=true
no LF terminal LF=false
with LF line1=alpha
with LF line2=beta
with LF EOF=true
with LF terminal LF=true
```

`readLine()` chỉ cho biết nội dung và EOF theo logic dòng, nên không cung cấp dấu terminator đã bị loại. Trong ví dụ, chương trình còn giữ `text` gốc nên tự kiểm tra `endsWith("\n")` được. Nếu dữ liệu có thể dùng CR hoặc CRLF, kiểm tra trailing terminator phải bao gồm quy tắc tương ứng thay vì chỉ LF.

Khi một định dạng bắt buộc **mọi bản ghi phải kết thúc bằng LF**, thiếu LF cuối có thể là lỗi format hoặc dấu hiệu truncation. Nếu format cho phép dòng cuối kết thúc bởi EOF, thiếu LF **không phải lỗi**. Không thể quyết định chỉ qua việc nhìn thấy `readLine() == null`.

### Định dạng có cấu trúc và dòng

Một parser CSV, JSON hoặc log có thể có cú pháp kết thúc riêng. Ví dụ CSV cho phép newline bên trong trường trích dẫn theo quy tắc parser; đọc `readLine()` không đủ để xác định bản ghi đã hoàn tất. JSON có thể kết thúc hợp lệ ở EOF nếu đối tượng/cấu trúc đã đóng đầy đủ; đọc được nửa một object trước EOF là văn bản **chưa hoàn chỉnh về cú pháp**.

**Dòng vật lý** và **bản ghi logic** là hai khái niệm khác nhau. Cần parser đúng định dạng, không chỉ logic đếm newline.

## 10. EOF giữa một UTF-8 code point: malformed input

Một file văn bản có thể đủ số byte của một khối theo logic nào đó nhưng lại kết thúc giữa **chuỗi mã hóa nhiều byte**. Chẳng hạn ký tự `€` là UTF-8 `E2 82 AC` (ba byte). Nếu file chỉ còn `E2 82` ở cuối, chuỗi UTF-8 chưa hoàn chỉnh.

`CharsetDecoder` ở chế độ strict có thể ném `MalformedInputException` khi được thông báo đã đến cuối input mà còn thiếu continuation byte. Trong khi đó, các API dễ dùng như `new String(bytes, StandardCharsets.UTF_8)` thường **thay** đầu vào sai bằng `U+FFFD` thay vì ném ngoại lệ.

### Ví dụ 4 — UTF-8 kết thúc giữa code point

Tạo tệp `TruncatedUtf8Demo.java`:

```java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TruncatedUtf8Demo {
    public static void main(String[] args) throws Exception {
        byte[] valid = "A\u20ac".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        System.out.println("complete matches="
                + "A\u20ac".equals(new String(valid, StandardCharsets.UTF_8)));

        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(truncated));
        } catch (CharacterCodingException e) {
            System.out.println("strict decoder=" + e.getClass().getSimpleName());
        }

        String lenient = new String(truncated, StandardCharsets.UTF_8);
        System.out.println("lenient decoder="
                + lenient.replace("\uFFFD", "<U+FFFD>"));
    }
}
```

```bash
javac -encoding UTF-8 --release 17 TruncatedUtf8Demo.java
java TruncatedUtf8Demo
```

```text
complete matches=true
strict decoder=MalformedInputException
lenient decoder=A<U+FFFD>
```

**Diễn giải:**

- `valid` chứa byte UTF-8 cho `A€`. Bỏ byte cuối của `€` tạo chuỗi byte không hợp lệ.
- Decoder strict gặp input chưa hoàn chỉnh khi đã tới cuối và ném `MalformedInputException`. Đây là lỗi **mã hóa văn bản**, độc lập với việc giao thức bên ngoài có khai báo tổng số byte hay không.
- `new String(...)` trong trường hợp này chèn replacement `U+FFFD`. Để stdout ổn định bất kể font console, ví dụ đổi riêng ký tự replacement thành chuỗi `<U+FFFD>` trước khi in.
- **Không phải mọi byte bị thiếu đều khiến decoder phát hiện được.** Nếu một byte cuối biến mất nhưng phần byte còn lại vẫn là UTF-8 hợp lệ, bộ giải mã có thể không báo gì; chỉ framing/độ dài/checksum mới xác định được đoạn text bị thiếu theo contract.

Với đọc từng chunk, đừng đánh dấu `endOfInput=true` chỉ vì một **chunk** chưa đủ byte. Một mã UTF-8 hợp lệ có thể bị chia giữa hai lần đọc; phải giữ state của decoder cho đến khi nguồn thực sự kết thúc.

## 11. Dùng framing để biết đâu là “đã nhận đủ”

Các mô hình framing phổ biến có ưu/nhược điểm khác nhau:

### 11.1 Độ dài cố định

Một bản ghi có đúng **N byte**. Caller phải đọc đủ N bằng `DataInputStream.readFully` hoặc vòng lặp tích lũy đến N. Nếu EOF trước N thì báo thiếu. Phù hợp với header cố định hoặc record binary có layout định trước.

### 11.2 Length-prefix

Header chứa độ dài của payload tiếp theo. Sau khi đọc header **đủ**, kiểm tra giới hạn rồi đọc đúng số byte đó. Nếu EOF trước số byte đã khai báo, đây là truncation. Cần quyết định header tính theo **byte** hay **ký tự**; trong giao thức byte stream, kích thước payload thường là số byte và phải được tính từ byte đã mã hóa.

**Không dùng `String.length()` làm số byte UTF-8.** Ký tự tiếng Việt hoặc emoji thường dùng nhiều byte, khác độ dài UTF-16. Ví dụ truyền string `"😊"` có `String.length() == 2` nhưng UTF-8 cần **4 byte**; nếu header ghi độ dài 2, receiver sẽ đọc sai framing.

### 11.3 Delimiter hoặc marker kết thúc

Đọc đến ký hiệu phân tách (ví dụ LF, sentinel byte hay terminator chuyên dụng). Nếu contract yêu cầu marker, EOF trước marker có thể là lỗi. Nhưng phải có quy tắc escaping/quoting nếu marker có thể xuất hiện trong payload. Khi delimiter là LF, có thể cần hỗ trợ CRLF tùy đặc tả, không suy luận từ nền tảng.

### 11.4 Đóng luồng là dấu kết thúc

Một số protocol dùng “đến khi peer đóng kết nối” làm dấu kết thúc body. Khi đó EOF **có thể** là điều kiện hoàn tất theo framing đó. Tuy nhiên, nếu upstream cắt dữ liệu rồi đóng sạch, receiver có thể **không phát hiện** nếu không có độ dài/checksum/marker ngoài. Vì thế cần cân nhắc khả năng chẩn đoán thiếu dữ liệu.

### 11.5 Checksum, hash hoặc footer

Checksum có thể giúp phát hiện dữ liệu đã nhận bị thay đổi hoặc thiếu, **nếu** giá trị kỳ vọng được truyền/lưu theo cách đáng tin và thuật toán phù hợp. Với file nén hoặc container, footer/CRC và cấu trúc cuối có thể giúp parser phát hiện kết thúc bất thường.

**Lưu ý:** checksum thông thường như CRC không cung cấp tính xác thực chống giả mạo; nếu đối thủ có thể sửa cả nội dung và CRC, cần MAC hoặc chữ ký phù hợp. Kiểm tra số byte và checksum còn phải tách khỏi **xác thực ngữ nghĩa** của dữ liệu.

## 12. Một quy trình đọc frame đáng tin cậy

Với một protocol có header độ dài và payload, quy trình điển hình:

1. **Xác định contract**: header dài bao nhiêu byte, thứ tự byte (endianness), độ dài body tính theo byte hay ký tự, số lượng frame, encoding, cách kết thúc kết nối.
2. **Đọc đủ header**. Nếu EOF khi chưa có phần nào của header, kiểm tra contract: đó có thể là kết thúc hợp lệ của *danh sách frame*; nếu EOF giữa header thì thường là truncation. Trong ví dụ `readFrame` cho **một frame bắt buộc**, EOF ngay trước header cũng là lỗi.
3. **Validate header**: từ chối length âm, vượt giới hạn, header không hợp lệ. Không cấp phát array theo số không kiểm soát.
4. **Đọc đủ body** theo length đã xác thực. Dùng `readFully` hoặc tích lũy byte đến đúng length. Nếu EOF trước length thì báo lỗi thiếu dữ liệu.
5. **Kiểm tra integrity**: checksum, footer, số record, schema, giải mã strict khi cần.
6. **Xử lý phần còn lại** theo protocol: nếu có nhiều frame, lặp đến điều kiện dừng. Nếu chỉ cho đúng một frame, kiểm tra byte dư theo quy định.
7. **Đóng tài nguyên** bằng `try-with-resources` khi caller sở hữu nguồn; xử lý timeout/hủy sao cho không giữ file descriptor hoặc kết nối vô thời hạn.
8. **Báo lỗi có ngữ cảnh**: tên record, độ dài kỳ vọng và byte đã đọc; hạn chế ghi nội dung bí mật thô vào log.

### Ví dụ thuật toán kiểm tra số byte

Đây là **mã minh họa** dùng trong một phương thức có `InputStream in`, `int expected` và xử lý `IOException`:

```java
if (expected < 0 || expected > 1_000_000) {
    throw new IllegalArgumentException("Invalid length");
}
byte[] payload = in.readNBytes(expected);
if (payload.length != expected) {
    throw new java.io.EOFException(
            "Expected " + expected + " bytes, got " + payload.length);
}
// Đọc đủ số byte; tiếp theo cần validate nội dung theo format.
```

Cách trên dùng `readNBytes` và kiểm tra độ dài. `DataInputStream.readFully` giúp chuyển ngay điều kiện thiếu byte thành `EOFException`; nhưng sau exception từ `readFully`, không phải lúc nào bạn cũng biết chính xác đã đọc được bao nhiêu byte từ mảng nếu chưa đếm riêng.

Nếu body lớn, không nhất thiết cấp phát toàn bộ vào một mảng; có thể lặp với buffer giới hạn, tích lũy số byte nhận được và truyền sang parser/đích streaming.

## 13. Không dùng `available()` để đoán EOF hoặc completeness

`InputStream.available()` trả **ước lượng số byte có thể đọc mà không chặn** theo hợp đồng của stream. Nó không phải tổng kích thước nguồn, số byte còn lại chắc chắn trong file hay độ dài message ở tầng ứng dụng.

Sai lầm phổ biến:

```java
// KHÔNG nên dùng available() làm điều kiện hoàn tất:
while (input.available() > 0) {
    input.read();
}
```

Đây là **đoạn minh họa sai**, không phải quy trình đọc đúng. Với network stream, `available() == 0` có thể chỉ là chưa có byte sẵn **ngay lúc kiểm tra**, chứ không phải peer đã đóng. Chương trình có thể dừng quá sớm và coi một message dở dang là hoàn chỉnh.

Đúng hơn:

- Nếu đọc “cho đến EOF”, gọi `read()`/`read(buffer)` đến `-1`.
- Nếu cần đủ N byte, thực hiện phép đọc đủ và so sánh với N.
- Nếu protocol dùng delimiter, parser phải tìm delimiter theo quy tắc.
- Nếu cần timeout, sử dụng timeout/deadline như điều kiện lỗi/hủy độc lập, không giả làm EOF.

## 14. Trường hợp tương tự trong NIO và xử lý phi đồng bộ

Đối với `ReadableByteChannel`/`FileChannel` và `SocketChannel`, ý tưởng vẫn vậy nhưng tín hiệu cụ thể cần xem tài liệu của từng loại kênh.

- Với một channel được đọc đến EOF, `read(ByteBuffer)` có thể trả **`-1`** khi tới end-of-stream.
- Với `SocketChannel` ở chế độ **non-blocking**, một lần gọi `read` trả **`0`** có thể chỉ nói “hiện chưa đọc được byte nào”; nó không đồng nghĩa EOF. Caller phải đợi readiness hoặc sự kiện phù hợp rồi tiếp tục.
- `ByteBuffer.remaining()` là số phần tử còn có thể đọc/ghi trong **buffer**, không phải kích thước byte còn lại trên socket hoặc tính hoàn chỉnh của frame.
- Nếu một callback/event cung cấp 300 byte nhưng message yêu cầu 500, phải **giữ state của frame** và tích lũy ở các sự kiện tiếp theo. Không ném truncation chỉ vì một lần đọc chưa đủ.
- Khi kênh thực sự báo end-of-stream, so sánh trạng thái parser hiện tại với quy tắc hoàn tất. Nếu còn dang dở header, body, delimiter, thì báo incomplete/truncated.

**Ranh giới chunk không phải ranh giới message.** TCP là byte stream: số lần `write` ở sender không có quan hệ một-một với số lần `read` ở receiver. Đây là lý do thiết kế protocol cần framing rõ ràng, không phụ thuộc vào “mỗi lần read là một bản ghi”.

## 15. File I/O, độ dài và tính nhất quán khi đọc

Với file có sẵn, `Files.size(path)` có thể giúp biết kích thước hiện tại trước khi đọc, nhưng **không phải bằng chứng tuyệt đối** rằng số byte đó sẽ luôn sẵn trong một file bị sửa đồng thời. File có thể thay đổi sau khi đo kích thước.

Một quy trình an toàn hơn là xác định yêu cầu:

- Nếu cần **snapshot nhất quán**, sử dụng cơ chế snapshot, khóa hoặc phương án thiết kế phù hợp với hệ thống file và môi trường.
- Nếu định dạng file có **length nội bộ**, kiểm tra số byte thực đọc so với length đó.
- Nếu cần bảo đảm **đúng bản phát hành**, so sánh checksum/hash với nguồn kỳ vọng đáng tin.
- Với file đang được ghi tiếp (ví dụ log append-only), EOF hiện tại thường chỉ có nghĩa **chưa có byte mới tại thời điểm đọc**, chứ không có nghĩa producer sẽ không ghi thêm vào tương lai. Nếu muốn “follow” file, phải dùng cơ chế polling/watch/tailing riêng.
- Nếu dữ liệu bị cắt bởi thao tác sao chép/download, kiểm tra kích thước kỳ vọng, HTTP metadata có độ tin cậy phù hợp, checksum hoặc định dạng kết thúc file.

**Đừng biến một lần đọc ngắn thành việc ghi đè file.** Trước hết phải xác định nguồn có phải đang được cập nhật, read có bị timeout hay không, và contract nào xác nhận sự thiếu hụt.

## 16. Nguyên tắc xử lý exception, tài nguyên và retry

### Nhận diện đúng loại thất bại

- **EOF hợp lệ:** kết thúc vòng lặp hoặc trạng thái parse hoàn chỉnh theo contract.
- **Dữ liệu bị cắt cụt:** báo lỗi cấp ứng dụng (có thể dùng `EOFException` khi thiếu byte bắt buộc), ghi rõ expected vs actual nếu biết.
- **I/O failure:** xử lý `IOException`/lỗi mạng tương ứng, không đánh đồng với byte stream kết thúc.
- **Hết hạn:** xử lý timeout và chính sách hủy/retry riêng, không tự chuyển thành kết quả EOF thành công.
- **Định dạng sai:** ví dụ length âm, UTF-8 malformed hoặc cấu trúc payload lỗi, cần thông báo rõ nguyên nhân thay vì chỉ ghi “EOF”.

Trong Java, `EOFException` là subtype của `IOException`. Khi cần phân nhánh cụ thể:

```java
try {
    data.readFully(payload);
} catch (java.io.EOFException e) {
    // Dữ liệu đầu vào thiếu so với payload.length.
    throw e;
} catch (java.io.IOException e) {
    // Lỗi I/O khác.
    throw e;
}
```

Đây là **đoạn minh họa**, cần `data` là `DataInputStream`, `payload` là `byte[]` đã khởi tạo và phương thức khai báo `throws IOException` hoặc xử lý tiếp. Tránh bắt `Exception` chung rồi trả một chuỗi trống, vì như vậy không còn phân biệt “bản ghi trống hợp lệ” với “bản ghi bị lỗi”.

### Retry và khả năng đọc lại

Retry hợp lý nếu nguyên nhân có thể là lỗi tạm thời và caller có nguồn có thể khôi phục hoặc yêu cầu lại. Với stream đã đọc dở, **không tự giả định** có thể tua lại rồi đọc lần nữa. `mark/reset` chỉ khả dụng theo hợp đồng cụ thể, và socket thường không cho tua ngược byte đã tiêu thụ.

Nếu đã ghi một phần output trước khi phát hiện truncation, cần quản lý **atomicity**: ghi vào file tạm, kiểm tra hoàn tất/integrity rồi mới đổi tên hoặc commit, thay vì công bố ngay file dở dang như kết quả thành công. Chính sách atomic move còn tùy nền tảng và filesystem.

### Đóng tài nguyên

Dùng `try-with-resources` với nguồn/đích mà bạn sở hữu. Đóng tài nguyên giúp không rò rỉ file descriptor/socket, nhưng **không phải kiểm tra tính hoàn chỉnh**. Kiểm tra length, delimiter, checksum hay trạng thái parser **trước khi** đánh dấu tác vụ thành công.

## 17. Những lỗi lập trình thường gặp

1. **Đọc `read() == -1` là luôn xảy ra lỗi.** Nếu contract là đọc đến EOF, đây là kết thúc bình thường.
2. **Gặp EOF rồi trả thành công cho mọi message.** Nếu frame yêu cầu N byte thì EOF trước N là truncation.
3. **Coi read ngắn là kết thúc.** Một lần `read(buffer)` không phải lời hứa sẽ điền đầy buffer.
4. **Không so sánh `readNBytes(n).length` với `n`.** API có thể trả ít hơn n nếu EOF.
5. **Dùng `available() == 0` để kết luận EOF.** Nó chỉ biểu thị số byte đọc được không chặn theo ước lượng, không phải thông điệp kết thúc.
6. **Quên `readFully`/header length validation.** Dữ liệu thiếu hoặc header độc hại có thể gây lỗi, cấp phát bộ nhớ không kiểm soát.
7. **Dùng `String.length()` làm độ dài byte UTF-8.** Một emoji ngoài BMP chiếm hai UTF-16 code unit nhưng bốn byte UTF-8.
8. **Giả định `readLine()` phân biệt dòng cuối có newline.** Phương thức này bỏ terminator.
9. **Coi file không có LF cuối là truncation trong mọi trường hợp.** Phải xem định dạng có yêu cầu LF cuối không.
10. **Dùng decoder thay thế mặc định để xác thực văn bản nghiêm ngặt.** Dữ liệu UTF-8 cuối file bị cắt có thể biến thành `U+FFFD` thay vì lỗi.
11. **Không phân biệt timeout và EOF.** Timeout nghĩa là chưa hoàn tất đúng thời gian; không chứng minh nguồn đã đóng.
12. **Giả định một lần `SocketChannel.read` là một message.** TCP có thể phân đoạn hoặc gộp byte bất kỳ.
13. **Giả định checksum luôn đủ.** Checksum kiểm tra một lớp integrity theo mô hình tin cậy cụ thể; không thay thế framing hoặc xác thực schema.
14. **Bỏ qua dữ liệu dư sau message duy nhất.** Nếu format quy định đúng một frame thì byte thừa cũng là vi phạm contract.
15. **Coi `readAllBytes` thành công là format file hợp lệ.** Nó chỉ đã đọc đến EOF; parser/validator vẫn phải kiểm tra nội dung.
16. **Giữ file kết quả dở dang như file hoàn chỉnh.** Chỉ công bố output sau khi mọi điều kiện hoàn tất đã được xác nhận.

## 18. Bảng quyết định nhanh

| Mục tiêu | Dấu hiệu thành công | Cách làm |
|---|---|---|
| Duyệt byte đến hết nguồn | Gặp `read() == -1` sau khi xử lý mọi byte trước đó | Vòng lặp `read` đến EOF |
| Đọc đúng trường N byte | Đã tích lũy **đúng N byte** | `readFully` hoặc `readNBytes` + kiểm tra length |
| Đọc header 4 byte | `readInt` đọc đủ | Bắt `EOFException` khi thiếu |
| Đọc frame length-prefixed | Header hợp lệ và body đủ length | Validate length, `readFully`, validate payload |
| Đọc file text tự do | Parser chấp nhận hết input | `BufferedReader`/`Files`, kiểm tra quy tắc định dạng |
| Xác nhận newline cuối | Có terminator theo contract | Phân tích text thô/byte thô, không chỉ `readLine()` |
| Xác nhận text UTF-8 hợp lệ | Decoder strict chấp nhận toàn bộ byte | `CharsetDecoder` với `REPORT` |
| Xử lý socket non-blocking | Parser đã nhận đủ frame, không chỉ một lần `read` có dữ liệu | Tích lũy state qua các lần đọc |
| Phát hiện file bị cắt khi tải xuống | Đủ số byte và integrity theo metadata đáng tin | Length + checksum/hash khi có |
| Xử lý lỗi tạm thời | Retry/hủy theo policy rõ ràng | Phân biệt timeout, EOF, IOException |

## 19. Tự kiểm tra

**Câu 1.** Một `InputStream` chỉ chứa 2 byte. Gọi `read()` ba lần trả 10, 20, -1. Có thể khẳng định dữ liệu bị cắt không?  
**Đáp án:** không. EOF bình thường cho nguồn 2 byte; chỉ bị cắt nếu có contract yêu cầu nhiều hơn.

**Câu 2.** `input.readNBytes(100)` trả mảng có 73 byte mà không ném exception. Điều đó có hợp lệ không?  
**Đáp án:** có theo API, nếu EOF đến sớm. Nhưng nếu caller **bắt buộc** phải có 100 byte thì phải báo thiếu 27 byte.

**Câu 3.** Vì sao `DataInputStream.readFully` có thể ném `EOFException` nhưng `InputStream.read()` lại trả `-1`?  
**Đáp án:** `readFully` có điều kiện hoàn tất là **đủ số byte**, nên EOF trước khi đủ là ngoại lệ; `read()` đơn lẻ chỉ báo tình trạng nguồn.

**Câu 4.** `BufferedReader.readLine()` trả `"last"` rồi lần sau trả `null`. File có LF cuối không?  
**Đáp án:** không thể biết chỉ từ `readLine()`, vì terminator đã bị bỏ.

**Câu 5.** Một message có header khai báo 64 byte body nhưng chỉ nhận được 30 byte rồi TCP FIN. Đó là gì?  
**Đáp án:** EOF ở byte stream; **truncation ở tầng framing** do thiếu 34 byte so với khai báo.

**Câu 6.** `SocketChannel.read` non-blocking trả `0`. Đây có phải EOF?  
**Đáp án:** không. Thao tác hiện chưa đọc được byte nào; EOF được biểu thị bằng `-1` theo contract tương ứng.

**Câu 7.** Chuỗi byte UTF-8 kết thúc `E2 82` rồi EOF. Có thể coi là text UTF-8 hợp lệ không?  
**Đáp án:** không. Bộ giải mã strict phát hiện chuỗi mã hóa nhiều byte chưa hoàn tất ở cuối input.

**Câu 8.** Một file text kết thúc ngay sau ký tự cuối của dòng không có LF. Có mặc nhiên lỗi không?  
**Đáp án:** không; tùy định dạng có yêu cầu newline cuối hay không.

## Kết luận

**EOF là tín hiệu hết dữ liệu của nguồn, còn “dữ liệu bị cắt cụt” là kết luận dựa trên hợp đồng đầy đủ của định dạng hoặc giao thức.** Java dùng `-1` hay `null` để báo kết thúc bình thường ở nhiều API đọc; các phép đọc đủ như `DataInputStream.readFully` có thể ném `EOFException` khi thiếu byte bắt buộc. Để không chấp nhận nhầm một thông điệp dở dang, hãy quy định rõ length, delimiter, footer, checksum hoặc trạng thái parser; kiểm tra đủ dữ liệu, xử lý riêng timeout/I/O failure và quản lý tài nguyên đúng vòng đời.

### Tài liệu tra cứu Java SE 17

- [`InputStream`: `read`, `readNBytes`, `readAllBytes`, `transferTo`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html)
- [`DataInputStream`: `readInt`, `readFully`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html)
- [`EOFException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/EOFException.html)
- [`BufferedReader.readLine`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)
- [`SocketChannel.read`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html)
- [`CharsetDecoder` và `CodingErrorAction`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-003">Closeable, AutoCloseable và try-with-resources</a>

<details>
<summary>Xem chi tiết</summary>

# `Closeable`, `AutoCloseable` và `try-with-resources`

## Mục tiêu bài học

Khi một chương trình Java mở file, socket, stream, reader, channel hoặc kết nối cơ sở dữ liệu, tài nguyên phía sau đối tượng có thể là **file descriptor, bộ đệm, kết nối, khóa hoặc trạng thái hệ thống**. Garbage collector quản lý bộ nhớ của object Java, nhưng **không phải cơ chế đóng tài nguyên kịp thời** theo yêu cầu ứng dụng.

Java cung cấp `AutoCloseable`, giao diện chuyên biệt `Closeable` và cú pháp `try-with-resources` (TWR) để quản lý vòng đời tài nguyên có thể đóng. Mấu chốt là: **tài nguyên thuộc về ai, được đóng khi nào, theo thứ tự nào và điều gì xảy ra nếu cả thân `try` lẫn `close()` cùng phát sinh lỗi**.

Sau bài này, bạn có thể:

- Phân biệt `java.lang.AutoCloseable` và `java.io.Closeable` qua chữ ký `close()`, phạm vi áp dụng và đặc tính đóng lặp.
- Dùng `try-with-resources` với một hoặc nhiều tài nguyên, hiểu thứ tự khởi tạo **trái → phải** và đóng **phải → trái**.
- Viết chương trình xử lý chính xác ngoại lệ phát sinh trong thân `try`, khi khởi tạo, và khi đóng; biết đọc `Throwable.getSuppressed()`.
- Vận dụng cú pháp Java 9+ cho biến resource đã khai báo và quy tắc *effectively final*.
- Chọn đúng chiến lược sở hữu tài nguyên khi bọc stream/reader, dùng `System.in/out`, nhận tài nguyên từ thư viện hoặc quản lý transaction.
- Kiểm thử vòng đời tài nguyên bằng các ví dụ Java 17 độc lập, có đầu ra kiểm chứng được.

## 1. Vì sao phải đóng tài nguyên?

Xét đoạn code:

```java
InputStream in = Files.newInputStream(path);
byte[] data = in.readAllBytes();
```

Đây là **đoạn minh họa**: `path` là `Path` hợp lệ; có import `java.io.InputStream`, `java.nio.file.Files`, `java.nio.file.Path` và xử lý `IOException`. Đoạn này chưa đóng `in`. Nếu chương trình chạy lâu, lặp mở nhiều file mà không đóng, có thể hết số lượng file descriptor hoặc giữ tài nguyên hệ thống lâu hơn mong muốn. Nếu `readAllBytes()` ném ngoại lệ, không có đường đi nào trong đoạn mã đảm bảo đóng stream.

**Không nên trông chờ garbage collector**: GC có thể chưa chạy khi bạn cần giải phóng tài nguyên; một số tài nguyên không tương ứng trực tiếp một vùng heap. Việc `System.gc()` được gọi cũng không biến thành lời hứa các tài nguyên sẽ đóng đúng lúc. Hãy **gắn phạm vi sử dụng với phạm vi đóng**.

TWR cho phép viết lại:

```java
try (InputStream in = Files.newInputStream(path)) {
    byte[] data = in.readAllBytes();
    // Xử lý data khi in còn mở.
}
```

Đây là **đoạn minh họa**; biến `path` và các import/checked exception giống trên. Khi rời khối `try`, Java tự gọi `in.close()` — kể cả khi phần thân ném ngoại lệ hoặc `return`. Đối với I/O thông thường, đó là cách mặc định nên dùng.

### Vòng đời thường gặp

```text
Tạo/mở tài nguyên → Sử dụng → Đóng (kể cả nếu sử dụng thất bại)
        │                  │
        └── lỗi mở?         └── lỗi khi đóng?
                ↘       ↙
           Xử lý ngoại lệ có ngữ cảnh
```

**Đóng tài nguyên không có nghĩa dữ liệu được lưu bền vững trên ổ đĩa.** Chẳng hạn `BufferedWriter.close()` thường flush buffer và đóng underlying writer, nhưng không tương đương với việc bảo đảm filesystem đã đồng bộ dữ liệu qua `fsync`/`FileChannel.force()`. Bài này tập trung vào **vòng đời và lỗi**, không biến `close()` thành cam kết durability.

## 2. `AutoCloseable`: hợp đồng đóng tài nguyên tổng quát

`java.lang.AutoCloseable` là giao diện có phương thức:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Đây là **chữ ký rút gọn minh họa**, không phải mã cần tự khai báo thay thế lớp trong JDK. Bất kỳ kiểu đối tượng nào triển khai `AutoCloseable` đều có thể xuất hiện trong phần khai báo tài nguyên của `try-with-resources`.

Đối tượng có thể đại diện file, database connection, lock scope, session, hoặc tài nguyên không thuộc filesystem. Một object không sở hữu tài nguyên hệ điều hành vẫn có thể triển khai `AutoCloseable` nếu muốn cung cấp thao tác kết thúc vòng đời có cấu trúc.

Ví dụ về các kiểu liên quan:

| Kiểu / API | Quan hệ | Tài nguyên thường quản lý |
|---|---|---|
| `AutoCloseable` | Giao diện gốc | Tài nguyên có vòng đời đóng |
| `Closeable` | Kế thừa `AutoCloseable` | I/O thường phát sinh `IOException` |
| `InputStream`, `OutputStream`, `Reader`, `Writer` | Triển khai `Closeable` | File, luồng byte/ký tự hoặc lớp bao |
| `java.nio.channels.Channel` | Kế thừa `Closeable` | Channel I/O |
| `java.sql.Connection` | Triển khai `AutoCloseable` | Kết nối database |
| `java.sql.Statement`, `ResultSet` | Triển khai `AutoCloseable` | Statement và kết quả truy vấn |
| `Stream<String>` trả bởi `Files.lines(path)` | `Stream` là `AutoCloseable` | Stream có liên kết reader/file còn mở |

`AutoCloseable.close()` khai báo `throws Exception`, nghĩa là một triển khai có thể phát sinh checked exception. Nhưng **triển khai cụ thể được phép thu hẹp hoặc bỏ checked exception** trong chữ ký `close()`. Vì thế loại ngoại lệ bạn phải khai báo/catch tùy theo **kiểu tĩnh của biến** và các lệnh trong thân `try`.

Ví dụ:

```java
class TaskScope implements AutoCloseable {
    @Override
    public void close() {
        System.out.println("release");
    }
}
```

Đây là **đoạn định nghĩa lớp minh họa**, có thể dùng trong một file Java thích hợp. Mặc dù `AutoCloseable` gốc có `throws Exception`, `TaskScope.close()` ở đây không ném checked exception, nên code gọi với kiểu `TaskScope` không buộc catch `Exception` chỉ bởi thao tác đóng.

**Lưu ý về đóng nhiều lần:** `AutoCloseable` không bắt buộc `close()` phải idempotent (gọi nhiều lần có cùng hiệu quả như một lần). Một tài nguyên có thể ném lỗi hoặc làm thay đổi trạng thái khi bị đóng lại. Không tự gọi `close()` trong thân TWR rồi mặc định Java đóng lần thứ hai sẽ luôn vô hại.

## 3. `Closeable`: giao diện chuyên biệt cho I/O

`java.io.Closeable` **kế thừa** `AutoCloseable` và thu hẹp checked exception:

```java
public interface Closeable extends AutoCloseable {
    @Override
    void close() throws IOException;
}
```

Đây cũng là **chữ ký rút gọn**, với `IOException` thuộc `java.io`.

| Tiêu chí | `AutoCloseable` | `Closeable` |
|---|---|---|
| Package | `java.lang` | `java.io` |
| Quan hệ | Giao diện cha | `extends AutoCloseable` |
| Checked exception từ `close()` | Khai báo `Exception` | Khai báo `IOException` |
| Phạm vi | Đóng tài nguyên nói chung | Tài nguyên I/O, đặc biệt streams/readers/channels |
| Đóng lặp | Không bắt buộc idempotent | Hợp đồng của `Closeable`: đóng đối tượng đã đóng không có tác dụng |
| Dùng trong TWR? | Có | Có |

`Closeable` nêu quy tắc: nếu stream đã đóng, gọi `close()` lần nữa không có tác dụng. Tuy nhiên điều đó **không** đồng nghĩa mọi kiểu `AutoCloseable` khác cũng có cùng cam kết; cũng không bảo đảm một lần đóng **đầu tiên** chắc chắn thành công.

Khi tự thiết kế API:

- Nếu abstraction thuần I/O, `Closeable` và `IOException` có thể giúp caller xử lý lỗi đúng miền.
- Nếu scope tài nguyên rộng hơn I/O (ví dụ session/transaction context), `AutoCloseable` phù hợp hơn. Có thể định nghĩa `close()` với checked exception hẹp, hoặc không ném checked exception khi hợp lý.
- Với cleanup phức tạp, không nên tùy tiện ném `Exception` rất rộng nếu có thể cung cấp một loại ngoại lệ ổn định, rõ nghĩa.

## 4. `try-with-resources`: cú pháp và thứ tự thực thi

Cú pháp cơ bản từ Java 7:

```java
try (ResourceType first = openFirst();
     ResourceType second = openSecond()) {
    use(first, second);
} catch (SomeException ex) {
    handle(ex);
} finally {
    afterClosing();
}
```

Đây là **mã giả Java**, không phải chương trình độc lập: `ResourceType` phải triển khai `AutoCloseable`, còn `openFirst`, `openSecond`, `use`, `handle`, `afterClosing` và `SomeException` là các tên mô tả. `catch` và `finally` là tùy chọn; bạn có thể viết TWR mà không cần một trong hai.

Trình tự chính:

1. Đánh giá và **khởi tạo tài nguyên từ trái sang phải**.
2. Khi tất cả khởi tạo thành công, chạy phần thân `try`.
3. Trước khi chạy `catch` hoặc `finally` của câu lệnh TWR, **đóng tài nguyên từ phải sang trái**.
4. Nếu có lỗi, áp dụng quy tắc **ngoại lệ chính** và **ngoại lệ bị suppress** (phần dưới).
5. `catch` bắt ngoại lệ còn lại sau khi TWR đóng tài nguyên; `finally` chạy theo quy tắc của câu lệnh try/finally.

TWR cũng đóng tài nguyên khi thoát khối bằng `break`, `continue`, `return` hoặc ngoại lệ, theo cơ chế thực thi Java bình thường. Đừng diễn giải thành bảo đảm tuyệt đối khi tiến trình bị cưỡng bức kết thúc, `Runtime.halt()`, mất điện hoặc JVM crash: khi đó chương trình có thể không thực thi cleanup.

### Ví dụ 1 — thứ tự mở, dùng và đóng tài nguyên

Lưu **`ResourceOrderDemo.java`**:

```java
public class ResourceOrderDemo {
    static final class Trace implements AutoCloseable {
        private final String name;

        Trace(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Trace first = new Trace("first");
             Trace second = new Trace("second")) {
            System.out.println("work");
        }
        System.out.println("after");
    }
}
```

Biên dịch/chạy Java 17+:

```bash
javac ResourceOrderDemo.java
java ResourceOrderDemo
```

Stdout chính xác:

```text
open first
open second
work
close second
close first
after
```

Giải thích: constructor `first` chạy trước `second`. Khi rời phần thân, Java gọi `second.close()` trước rồi `first.close()`, ngược thứ tự khởi tạo. Như vậy một lớp ngoài có thể tiếp tục tồn tại đến khi lớp trong đã đóng xong — thường phù hợp với trường hợp wrapper phụ thuộc vào stream bên dưới.

Nếu `second` không khởi tạo thành công, TWR vẫn đóng `first` đã khởi tạo xong; nó không cố đóng biến chưa nhận được tài nguyên hợp lệ.

### Khai báo resource và tính `final`

Các biến được khai báo trong header của TWR là **implicitly final**: bạn không thể gán sang đối tượng khác trong thân `try`.

Từ Java 9, có thể dùng **biến đã khai báo trước** nếu biến đó là `final` hoặc *effectively final*:

```java
InputStream input = Files.newInputStream(path);
try (input) {
    System.out.println(input.read());
}
```

Đây là **đoạn minh họa**, giả sử `path` là `Path`, có import `InputStream`/`Files` và xử lý `IOException`. `input` không bị gán lại, nên effectively final và hợp lệ trong Java 9+. Sau khối, biến vẫn nằm trong scope bên ngoài nhưng **stream đã đóng**; không dùng lại để đọc tiếp.

Ví dụ **không hợp lệ** vì biến resource bị gán lại:

```java
InputStream input = Files.newInputStream(firstPath);
input = Files.newInputStream(secondPath);
try (input) {
    // Compiler error: input is not final or effectively final.
}
```

Đây là **phản ví dụ**, với `firstPath`, `secondPath` là `Path` đã khai báo. Code không biên dịch theo cú pháp resource-variable của Java 9+. Cách thiết kế đúng là có một biến sở hữu tài nguyên duy nhất, hoặc khai báo tài nguyên trực tiếp trong header TWR. Không dùng đoạn phản ví dụ trong dự án thật vì việc ghi đè reference còn có nguy cơ làm rò rỉ stream đầu tiên.

Từ Java 10, bạn có thể dùng `var` cho tài nguyên khai báo ngay trong header:

```java
try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    System.out.println(reader.readLine());
}
```

Đoạn minh họa này yêu cầu có `path`, import `Files`/`StandardCharsets` và xử lý `IOException`. Dùng `var` không thay đổi quy tắc đóng; kiểu tĩnh được suy ra từ initializer.

## 5. Thân `try` và `close()` đều lỗi: suppressed exceptions

Đây là lý do quan trọng nên dùng TWR thay vì viết cleanup thủ công không cẩn thận.

Giả sử:

1. Phần thân ném `IllegalStateException("work failed")`.
2. Khi đóng tài nguyên thứ hai, `close()` ném `Exception("close second")`.
3. Khi đóng tài nguyên thứ nhất, `close()` ném `Exception("close first")`.

Nếu cleanup ở `finally` được viết sơ sài, một lỗi `close` có thể che khuất ngoại lệ trong thân — làm mất nguyên nhân gốc. TWR có quy tắc:

- Khi **thân `try` đã ném**, ngoại lệ ở thân là **primary exception**.
- Các ngoại lệ `close()` xảy ra lúc cleanup được gắn vào primary qua `Throwable.addSuppressed()`.
- Nếu **thân không ném** nhưng nhiều `close()` ném, ngoại lệ từ tài nguyên bị đóng **đầu tiên** theo thứ tự đóng ngược trở thành primary; những ngoại lệ đóng về sau bị suppress.
- `getSuppressed()` trả mảng các ngoại lệ được đính kèm; **đó không phải `getCause()`**. Cause thường mô tả chuỗi nguyên nhân; suppressed mô tả các lỗi xảy ra đồng thời lúc dọn dẹp.

### Ví dụ 2 — quan sát primary và suppressed exceptions

Lưu **`SuppressedExceptionsDemo.java`**:

```java
public class SuppressedExceptionsDemo {
    static final class FailingResource implements AutoCloseable {
        private final String name;

        FailingResource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() throws Exception {
            System.out.println("close " + name);
            throw new Exception("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource first = new FailingResource("first");
             FailingResource second = new FailingResource("second")) {
            throw new IllegalStateException("work failed");
        } catch (Exception ex) {
            System.out.println("primary: " + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println(
                    "suppressed: " + suppressed.getMessage()
                );
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac SuppressedExceptionsDemo.java
java SuppressedExceptionsDemo
```

Stdout chính xác:

```text
open first
open second
close second
close first
primary: work failed
suppressed: close second failed
suppressed: close first failed
```

Điểm cần nhớ: TWR **vẫn cố đóng `first`** ngay cả khi `second.close()` đã ném lỗi. Ngoại lệ chính là `IllegalStateException("work failed")`, vì thế cả hai lỗi `close` được ghi ở `getSuppressed()` theo thứ tự xảy ra.

Nếu bỏ lệnh `throw new IllegalStateException("work failed")` và phần thân hoàn thành bình thường, thì **lỗi đóng `second`** là primary và lỗi đóng `first` là suppressed.

Không nên chỉ log `ex.getMessage()` rồi bỏ qua suppressed exceptions. Khi phân tích lỗi I/O, có thể cần xem `ex.getSuppressed()`; nhiều framework logging hiển thị chúng khi log toàn bộ stack trace của ngoại lệ, nhưng mức chi tiết tùy cấu hình.

### Điều gì nếu mở tài nguyên thứ hai thất bại?

Một trường hợp khác:

```java
try (InputStream first = openFirst();
     InputStream second = openSecond()) {
    // ...
}
```

Đây là **đoạn mô tả**: `openFirst()` và `openSecond()` là hàm minh họa trả `InputStream`. Nếu `openFirst()` thành công nhưng `openSecond()` ném ngoại lệ, Java sẽ **đóng `first`**. Ngoại lệ khởi tạo `second` là primary; nếu `first.close()` cũng ném, lỗi đóng của `first` là suppressed. Phần thân `try` **không được chạy**, vì không khởi tạo đủ các tài nguyên.

Với tài nguyên đã tạo **ngoài** header trước khi vào TWR, caller phải đảm bảo chúng được đưa vào scope đóng hoặc có kế hoạch cleanup cho trường hợp khởi tạo các đối tượng khác thất bại. TWR không tự quản lý những tài nguyên mà nó chưa được giao sở hữu.

## 6. Vì sao TWR tốt hơn `finally` đóng thủ công?

Cách thủ công đơn giản sau trông có vẻ đúng:

```java
Resource resource = open();
try {
    doWork(resource);
} finally {
    resource.close();
}
```

Đây là **mã giả**, với `Resource`, `open`, `doWork` là các tên minh họa. Vấn đề: nếu `doWork` và `close` **đều ném ngoại lệ**, ngoại lệ từ `finally` có thể thay thế ngoại lệ chính, khiến debug khó hơn. Để viết cleanup thủ công đúng với nhiều resource và suppressed exceptions, phải có logic try/catch phức tạp, dễ sai thứ tự.

TWR tương đương một cơ chế `try/finally` do compiler sinh ra, có xử lý ngoại lệ và cleanup một cách hệ thống. Với tài nguyên triển khai `AutoCloseable`, hãy ưu tiên TWR để làm rõ ownership và giảm mã lặp.

Lưu ý: **TWR không thay thế catch hợp lý**. Nó tự đóng; lỗi đọc/ghi hay lỗi đóng vẫn cần được xử lý, khai báo hoặc chuyển thành exception miền nghiệp vụ tại ranh giới phù hợp.

## 7. Quản lý file bằng TWR trong Java 17

Với `Files`, `Path` và các stream thông dụng:

- `Files.newInputStream(path)` trả về `InputStream` cần đóng.
- `Files.newOutputStream(path, ...)` trả về `OutputStream` cần đóng.
- `Files.newBufferedReader(path, charset)` và `Files.newBufferedWriter(...)` trả về reader/writer có thể đóng.
- `FileChannel.open(path, ...)` trả về `FileChannel` có thể đóng.
- `Files.lines(path, charset)` trả về `Stream<String>` có liên kết đến file; **phải đóng stream**.
- `Files.list(path)`, `Files.walk(path)`, `Files.find(...)()` cũng là các luồng duyệt filesystem cần đóng sau khi dùng. Với `Files.find`, dùng chính xác cú pháp API phù hợp khi lập trình: `Files.find(path, maxDepth, matcher)`; không gọi phương thức `Files.find(...)()` theo cách in mô tả này.

Một quy trình đọc/ghi thường có **hai tài nguyên**: input và output. TWR sẽ đóng output trước input khi khai báo input trước output.

### Ví dụ 3 — sao chép nội dung qua hai stream

Lưu **`CopyWithResourcesDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CopyWithResourcesDemo {
    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("twr-src-", ".txt");
        Path target = Files.createTempFile("twr-dst-", ".txt");

        try {
            Files.writeString(
                source, "Xin chào", StandardCharsets.UTF_8
            );

            try (InputStream in = Files.newInputStream(source);
                 OutputStream out = Files.newOutputStream(
                     target,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING
                 )) {
                in.transferTo(out);
            }

            System.out.println(
                "copied: " +
                Files.readString(target, StandardCharsets.UTF_8)
            );
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac CopyWithResourcesDemo.java
java CopyWithResourcesDemo
```

Stdout khi các thao tác filesystem thành công:

```text
copied: Xin chào
```

Chương trình tạo hai file tạm, ghi văn bản UTF-8 vào nguồn, sau đó dùng `InputStream.transferTo(OutputStream)` (Java 9+) sao chép byte sang đích. Nếu sao chép thất bại, TWR vẫn cố đóng cả `out` và `in`. Khi TWR kết thúc, lệnh đọc lại đích có thể mở file một lần mới. Phần `finally` dọn file tạm.

**Giới hạn của ví dụ:** cleanup ở `finally` là thao tác hệ thống độc lập, có thể ném `IOException` và làm che khuất lỗi trước đó nếu không thiết kế chính sách xử lý phù hợp. Ví dụ ưu tiên minh họa TWR trên hai stream; trong sản phẩm cần công cụ dọn temp/log lỗi có cơ chế không mất primary exception. Ngoài ra, lệnh `Files.createTempFile` thứ hai có thể thất bại sau khi tạo file thứ nhất nhưng trước khi vào `try/finally`, nên chương trình minh họa chưa bao phủ mọi tình huống thất bại của **quá trình chuẩn bị file tạm**. Thiết kế production phải bao cả bước tạo tài nguyên trong vùng dọn dẹp thích hợp.

`transferTo()` không gọi `close()` thay cho bạn; vì vậy TWR rất phù hợp để đảm bảo luồng được đóng dù thao tác đọc/ghi hoặc sao chép thất bại.

### Đóng `Files.lines` và các stream filesystem

Một cú pháp đúng:

```java
try (Stream<String> lines =
         Files.lines(path, StandardCharsets.UTF_8)) {
    long count = lines.filter(s -> !s.isBlank()).count();
    System.out.println(count);
}
```

Đây là **đoạn minh họa**. `path` là `Path`; cần `import java.util.stream.Stream;`, `java.nio.file.Files`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Stream được đóng khi thoát TWR.

Đừng đồng nhất `stream.count()` hoặc một terminal operation khác với `stream.close()`. Java Stream thông thường không nhất thiết phải đóng, nhưng stream có nguồn từ file hoặc directory cần được đóng theo hợp đồng của API. Nếu dùng `Files.walk`, đóng stream cũng giải phóng các handle truy cập thư mục có liên quan.

## 8. Bọc stream: ai chịu trách nhiệm đóng?

Các kiểu như `BufferedInputStream`, `BufferedOutputStream`, `InputStreamReader`, `OutputStreamWriter`, `BufferedReader` và `BufferedWriter` thường là **wrapper/decorator** quanh một tài nguyên bên dưới:

```text
FileInputStream ──► InputStreamReader(UTF-8) ──► BufferedReader
                                              ▲
                                          caller dùng
                                              │
                                       caller close()
                                              ↓
                                       đóng các tầng dưới
```

Với các lớp wrapper I/O chuẩn nói trên, `close()` ở **wrapper ngoài cùng** thông thường đóng cả underlying stream/reader. Do đó:

```java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    String line = reader.readLine();
}
```

Đây là **đoạn minh họa**, với `path` là `Path`, các import phù hợp và xử lý `IOException`. Caller sở hữu `reader` và chỉ cần đóng reader. Khi `Files.newBufferedReader` tự tạo tầng file bên dưới, việc đóng reader sẽ đảm nhiệm vòng đời theo hợp đồng đó.

Không nên tùy tiện quản lý cùng một tài nguyên bằng **nhiều owner độc lập**. Ví dụ, một thư viện nhận `OutputStream` do caller đưa vào có thể chỉ có nhiệm vụ ghi dữ liệu mà không nên đóng nó. Phải xác định rõ trong API:

- **Caller owns**: thư viện không đóng stream nhận được; người gọi đóng khi mọi người dùng đã xong.
- **Callee owns**: sau khi nhận stream, thư viện quản lý và đóng nó; caller không tiếp tục sử dụng.
- **Borrowed resource**: một đối tượng chỉ mượn tài nguyên trong thời gian xử lý và không có quyền đóng nguồn chung.

Không giả định mọi lớp wrapper thứ ba đều có hành vi đóng giống nhau; đọc hợp đồng của lớp cụ thể.

### `System.in`, `System.out`, `System.err`

Các standard stream là tài nguyên toàn cục của tiến trình. Ví dụ:

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
    System.out.println(reader.readLine());
}
```

Đây là **đoạn minh họa**, nhưng **cần cân nhắc kỹ**: khi TWR đóng `reader`, nó cũng đóng `System.in`. Nếu chương trình còn cần stdin cho phần khác, các thao tác đọc về sau có thể bị ảnh hưởng. Trong CLI chỉ có một lần đọc rồi kết thúc, điều này có thể được chấp nhận; trong thư viện, REPL hoặc chương trình đọc nhiều phần, đừng đóng một standard stream **được mượn** khi không sở hữu vòng đời của nó.

Tương tự, đóng `PrintWriter` hoặc `BufferedWriter` tự tạo quanh `System.out` có thể dẫn tới đóng luôn stdout, làm các lần in về sau thất bại. Với stream do caller cung cấp, API nên nói rõ có close hay chỉ flush hay không.

### `close()` so với `flush()`

`flush()` của buffered writer/stream đẩy dữ liệu đang đệm **xuống tầng bên dưới**, cho phép tiếp tục sử dụng. `close()` kết thúc vòng đời và thường flush dữ liệu còn chờ trong lúc đóng. **Không dùng `flush()` để thay `close()`** khi cần giải phóng descriptor; và **không coi `close()` là `FileChannel.force()`** để khẳng định dữ liệu đã ổn định trên thiết bị.

Nếu một giao thức yêu cầu gửi một message ngay trong khi stream còn mở, bạn có thể cần flush ở đúng ranh giới nghiệp vụ trước thời điểm close.

## 9. TWR và JDBC: đóng tài nguyên không phải commit transaction

Các tài nguyên JDBC như `Connection`, `Statement`, `PreparedStatement`, `ResultSet` có thể dùng với TWR vì triển khai `AutoCloseable`. Khai báo thứ tự thường là **Connection → Statement → ResultSet**, nhờ đó close diễn ra **ResultSet → Statement → Connection**.

Ví dụ hình thức:

```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt =
         conn.prepareStatement("SELECT id FROM items");
     ResultSet result = stmt.executeQuery()) {
    while (result.next()) {
        System.out.println(result.getLong("id"));
    }
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập: `dataSource`, bảng `items`, JDBC driver, import `java.sql.*` và xử lý `SQLException` phải có trong ứng dụng. Không thể dùng ví dụ này nếu chưa kết nối được database phù hợp.

Một điều rất quan trọng: **đóng Connection không phải cam kết transaction đã commit**. Với `autoCommit=false`, ứng dụng phải có chính sách commit/rollback rõ ràng. Hành vi khi đóng connection còn transaction chưa được giải quyết có thể tùy driver, pool và DBMS; không nên xây dựng quy trình tài chính dựa vào việc “close sẽ tự commit”.

Mẫu quản lý transaction thường:

```text
Mở connection → bắt đầu transaction
    → thực hiện thao tác
    ├─ thành công → commit
    └─ thất bại  → rollback (nếu còn có thể)
Cuối cùng → close / trả connection về pool
```

TWR giúp giải phóng tài nguyên; logic transaction vẫn thuộc về tầng nghiệp vụ/database.

Với connection pool, `Connection.close()` có thể trả logical connection về pool thay vì đóng hẳn kết nối vật lý. Đó là **chi tiết theo hợp đồng của pool/driver**, càng cho thấy `close()` là thao tác vòng đời abstraction, không phải luôn “đóng socket vật lý ngay”.

## 10. Xử lý ngoại lệ khi đóng

### Checked exceptions trong header và body

Nếu biến trong header có kiểu `AutoCloseable`, compiler biết rằng `close()` **có thể ném `Exception`**. Vì vậy phương thức chứa TWR phải khai báo hoặc bắt ngoại lệ phù hợp, ngay cả khi thân `try` có vẻ không ném:

```java
static void use(AutoCloseable resource) throws Exception {
    try (resource) {
        System.out.println("using");
    }
}
```

Đây là một **phương thức minh họa** (Java 9+) trong một lớp Java hợp lệ. `resource` là biến tham số effectively final, và `throws Exception` giải quyết chữ ký đóng tổng quát. Trong API ứng dụng, thường nên dùng kiểu resource cụ thể có `close()` khai báo ngoại lệ hẹp hơn thay vì yêu cầu mọi caller bắt `Exception` tổng quát.

Ngược lại, `Closeable` thường thu hẹp xuống `IOException`, nên chương trình có thể khai báo `throws IOException` khi dùng stream với các thao tác phù hợp.

### Khi nào `catch` và `finally` của TWR chạy?

```java
try (SomeResource r = create()) {
    work(r);
} catch (Exception ex) {
    report(ex);
} finally {
    markFinished();
}
```

Đây là **mã giả** mô tả thứ tự: tài nguyên `r` đã được đóng xong hoặc đã cố đóng xong **trước** khi chuyển tới `catch` bên ngoài phần thân resource và `finally` của câu lệnh. Nếu TWR đóng ném ngoại lệ khi thân không ném, `catch` có thể bắt lỗi đóng đó. Nếu thân đã ném, `catch` nhận primary với suppressed tương ứng.

Không đưa thêm một lời gọi `r.close()` không cần thiết trong `finally`: nó có thể đóng lại tài nguyên, gây lỗi hoặc làm khó hiểu ownership. Dùng `finally` cho hành động khác với vòng đời resource khi thật sự cần, và **tránh ném lỗi mới che khuất ngoại lệ chính**.

### Giới hạn của cleanup

TWR bảo đảm về **luồng điều khiển của ngôn ngữ Java trong thực thi bình thường**. Nó không bảo đảm:

- Máy không thể tắt điện hoặc tiến trình không thể bị kill trước khi `close()` hoàn thành.
- `close()` luôn thành công. Lỗi ở filesystem hoặc mạng vẫn có thể phát sinh.
- Một phương thức `close()` bên thứ ba sẽ không block vô hạn hoặc không có side effect.
- Khi resource được đóng, dữ liệu đã commit transaction, đồng bộ xuống đĩa hoặc xác nhận từ remote service.
- Resource có thể tái sử dụng sau khi đóng. Nhiều reader/stream/channel sẽ ném lỗi nếu tiếp tục dùng.

Vì vậy, nếu yêu cầu hệ thống là khả năng khôi phục sau crash, đảm bảo commit bền vững hoặc tính nguyên tử giữa các file, bạn vẫn cần thiết kế theo hợp đồng của filesystem, database hoặc giao thức tương ứng.

## 11. Những lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách xử lý |
|---|---|---|
| Mở stream nhưng không đóng khi lỗi | Có thể rò file descriptor/handle | Khai báo resource trong TWR |
| Nghĩ GC tự đóng đúng lúc | GC không có lịch đảm bảo cleanup tài nguyên | Đóng chủ động khi kết thúc phạm vi |
| Chỉ đóng resource đầu tiên khi có nhiều resource | Resource còn lại có thể rò | TWR khai báo tất cả resource do scope sở hữu |
| Quên thứ tự đóng | Lớp ngoài phụ thuộc tầng dưới có thể cần đóng trước | TWR đóng ngược thứ tự khai báo |
| Gọi `close()` bằng tay bên trong TWR | Có nguy cơ đóng hai lần, `AutoCloseable` không bắt buộc idempotent | Để TWR đóng một lần trừ khi hợp đồng yêu cầu khác |
| Không quan sát `getSuppressed()` | Có thể bỏ sót lỗi khi cleanup | Log đầy đủ ngoại lệ khi cần chẩn đoán |
| Gọi `catch` xong nuốt hết lỗi | Có thể tiếp tục khi đọc/ghi/cleanup thất bại | Xác định retry, rollback, propagate hoặc báo lỗi |
| Dùng `finally` ném lỗi mới | Có thể che khuất lỗi gốc | TWR, tránh cleanup ném không kiểm soát |
| TWR quanh `System.in` trong thư viện | Có thể đóng stdin toàn cục của caller | Quản lý resource ownership |
| Đóng connection đồng nghĩa commit | Không đúng với transaction thủ công | Commit/rollback tường minh |
| Quên đóng `Files.lines`/`Files.walk` | File/directory handle có thể còn mở | `try (Stream<?> s = Files...)` |
| Dùng `flush()` thay `close()` | Flush không giải phóng mọi tài nguyên | Dùng TWR và flush chỉ khi cần đẩy dữ liệu sớm |
| Dùng `close()` để bảo đảm crash durability | Close không thay thế sync/fsync | Thiết kế durability riêng |
| Trả về resource đã được TWR đóng | Caller nhận object không còn dùng được | Không đặt vòng đời TWR bao resource cần trả |
| Bắt `Exception` quá rộng không cần thiết | Dễ giấu lỗi và làm API khó dùng | Chọn kiểu resource và checked exception phù hợp |
| Cấp tài nguyên cho nhiều owner | Một người đóng khiến người khác dùng lỗi | Quy định quyền sở hữu và chuyển giao rõ ràng |

### Lỗi tinh vi: trả object ra khỏi TWR

```java
static InputStream broken(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
        return in;
    }
}
```

Đây là **phản ví dụ về thiết kế**. Phương thức biên dịch, nhưng Java **đóng `in` trước khi hoàn tất trả về**. Caller nhận một stream đã đóng và thường không thể đọc. Có hai thiết kế đúng tùy ý định:

1. **Caller sẽ đọc**: trả về stream mới mở, ghi rõ caller sở hữu và phải đóng bằng TWR.
2. **Phương thức tự đọc**: đặt `InputStream` trong TWR, đọc đủ dữ liệu hoặc tạo đối tượng kết quả **không phụ thuộc** resource trước khi trả về.

Tương tự, không nên trả JDBC `ResultSet` ra khỏi một scope đã đóng `Statement` hoặc `Connection`.

## 12. Chiến lược thiết kế resource scope

Hãy phân loại resource theo ownership:

```text
Hàm hiện tại tự tạo tài nguyên
    └── Hàm chịu trách nhiệm đóng, nên dùng TWR.

Hàm nhận tài nguyên đang thuộc về caller
    └── Thường chỉ mượn để sử dụng, không tự đóng
        (trừ khi API nói rõ chuyển giao ownership).

Hàm tạo resource để caller dùng về sau
    └── Trả resource còn mở + tài liệu hướng dẫn caller đóng.

Wrapper sở hữu underlying resource
    └── Close wrapper ngoài cùng theo hợp đồng của wrapper.
```

Một mô hình phổ biến là **resource sống ngắn nhất có thể nhưng đủ cho toàn bộ thao tác cần nó**. Đừng mở connection từ đầu một request rồi giữ qua nhiều bước chờ không liên quan; cũng đừng đóng quá sớm khi lazy iterator, reader hoặc parser vẫn đang dùng.

Trong mã có callback hay asynchronous task, TWR có thể **đóng quá sớm** nếu phần thân chỉ *lên lịch* tác vụ rồi thoát, trong khi tác vụ nền vẫn cần resource. Khi đó, việc quản lý lifetime phải gắn với sự hoàn tất của tác vụ bất đồng bộ; không giả định callback sẽ chạy trước khi khối TWR kết thúc.

Một số thư viện cung cấp resource có thể đóng nhưng việc `close()` không idempotent, có side effect, có thể block hoặc ném exception; kiểm tra hợp đồng và đặt timeout/monitoring ở tầng thích hợp nếu có nguy cơ kẹt tài nguyên.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `Closeable` khác `AutoCloseable` ở điểm cốt lõi nào?

**Đáp án:** `Closeable extends AutoCloseable`, `close()` khai báo `IOException` thay vì `Exception`, phù hợp nhiều loại I/O. `Closeable` có hợp đồng đóng lặp không hiệu lực; `AutoCloseable` không bắt buộc idempotent.

**Câu 2.** `try (A a = ...; B b = ...)` mở và đóng theo thứ tự nào?

**Đáp án:** Khởi tạo **a rồi b**, đóng **b rồi a**. Nếu khởi tạo b lỗi, a đã khởi tạo vẫn được đóng.

**Câu 3.** Thân `try` và `close()` đều ném lỗi, ngoại lệ nào là primary?

**Đáp án:** Lỗi thân là primary; lỗi lúc đóng được thêm vào `getSuppressed()`. Nếu thân thành công nhưng nhiều lần đóng lỗi, lỗi từ resource đóng đầu tiên là primary.

**Câu 4.** `Throwable.getCause()` có giống `getSuppressed()` không?

**Đáp án:** Không. Cause biểu thị nguyên nhân của một exception; suppressed chứa những exception được đính kèm, như lỗi phát sinh lúc đóng tài nguyên trong TWR.

**Câu 5.** Có thể dùng một biến khai báo bên ngoài TWR trong `try (resource)` ở Java 17 không?

**Đáp án:** Có, nếu biến là `final` hoặc effectively final (tính năng từ Java 9). Sau TWR, biến còn scope nhưng resource đã đóng.

**Câu 6.** Vì sao không đóng `System.in` trong library?

**Đáp án:** Đây thường là tài nguyên toàn cục do ứng dụng/caller sở hữu; thư viện đóng wrapper trên `System.in` có thể đóng luôn input chuẩn, làm phần khác không thể tiếp tục đọc.

**Câu 7.** `Files.lines(path)` có nên đưa trong TWR dù có terminal operation `count()` không?

**Đáp án:** Có. Stream đọc file có nguồn tài nguyên cần đóng; terminal operation không tự thay thế vòng đời close của stream.

**Câu 8.** Một `Connection` được TWR đóng có bảo đảm các lệnh ghi database đã commit không?

**Đáp án:** Không. Transaction phải theo chính sách commit/rollback riêng; close chỉ kết thúc vòng đời logical/physical connection theo driver và pool.

**Câu 9.** Khi nào không nên dùng TWR quanh một resource mới tạo?

**Đáp án:** Khi mục tiêu là **trả resource còn mở** cho caller để sử dụng về sau; lúc đó caller phải nhận ownership và có trách nhiệm đóng. Không trả một stream đã bị TWR đóng.

**Câu 10.** Có thể dùng TWR như bằng chứng file đã được lưu bền vững sau mất điện không?

**Đáp án:** Không. Đóng stream/writer là vấn đề vòng đời; durability cần đánh giá cơ chế đồng bộ xuống filesystem và thiết bị cùng yêu cầu nghiệp vụ.

**Ghi nhớ:** `AutoCloseable` định nghĩa khả năng đóng tài nguyên; `Closeable` chuyên biệt cho I/O; `try-with-resources` đưa việc đóng vào cấu trúc ngôn ngữ, theo **thứ tự ngược khi khởi tạo** và bảo toàn ngoại lệ chính qua **suppressed exceptions**. Muốn dùng đúng, hãy xác định **ai sở hữu tài nguyên, phạm vi sử dụng, chính sách xử lý lỗi và thời điểm đóng** — không dựa vào garbage collector hoặc đồng nhất close với commit/durability.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-004">Thứ tự đóng tài nguyên và suppressed exception</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự đóng tài nguyên và `suppressed exception` trong Java

Trong Java I/O, file, socket, stream, reader, writer và nhiều đối tượng truy cập tài nguyên phải được **đóng đúng lúc** để tránh rò rỉ handle, khóa file, kết nối hoặc dữ liệu còn trong bộ đệm. Việc đóng cũng có thể thất bại: `close()` có thể ném exception khi flush, hoàn tất một định dạng hoặc giải phóng tài nguyên.

`try-with-resources` giải quyết hai vấn đề quan trọng:

1. **Đóng tự động theo thứ tự ngược với thứ tự khai báo** các resource đã được khởi tạo thành công.
2. **Giữ lỗi chính (primary exception)** và đưa các lỗi phát sinh khi đóng vào danh sách **suppressed exceptions**, để lỗi gốc không bị che mất.

Hiểu đúng quy tắc này giúp giải thích exception nào được `catch` nhận, vì sao một lần thất bại có nhiều lỗi liên quan, và khi nào không nên khai báo đồng thời wrapper với luồng bên dưới.

## 1. `try-with-resources` quản lý những đối tượng nào?

Một đối tượng dùng được trong phần khai báo resource của `try` khi kiểu của nó implements `java.lang.AutoCloseable`.

- `AutoCloseable` khai báo `void close() throws Exception`; một implementation có thể thu hẹp kiểu exception hoặc không ném checked exception.
- `java.io.Closeable` kế thừa `AutoCloseable` và khai báo `close() throws IOException`, nên thường gặp ở `InputStream`, `OutputStream`, `Reader`, `Writer`.
- `Connection`, `Statement`, `ResultSet` trong JDBC cũng có API đóng tài nguyên, nhưng cơ chế transaction cần xem thêm hợp đồng riêng: `close()` **không được coi mặc định là `commit()`**.
- Một resource tùy biến có thể quản lý lock, file tạm, vùng nhớ hoặc trạng thái ứng dụng; không nhất thiết phải là một luồng I/O.

Ví dụ cú pháp cơ bản:

```java
try (ResourceType resource = openResource()) {
    use(resource);
} // Java tự gọi resource.close() khi rời khỏi khối.
```

Đây là **mẫu cú pháp minh họa**, không phải chương trình độc lập: `ResourceType`, `openResource()` và `use(...)` đại diện cho kiểu/phương thức do ứng dụng định nghĩa.

`try-with-resources` được giới thiệu trong Java 7. Từ Java 9 có thể sử dụng một biến resource đã tồn tại nếu biến đó là `final` hoặc *effectively final*:

```java
// Mã minh họa, reader đã được tạo và không bị gán lại.
BufferedReader reader = createReader();
try (reader) {
    System.out.println(reader.readLine());
}
```

Trong ví dụ này, biến `reader` không được gán lại nên hợp lệ ở Java 17+. Dù biến được tạo bên ngoài phần khai báo `try`, **resource vẫn sẽ bị đóng khi rời khối**; đừng sử dụng cách này nếu caller cần giữ reader mở.

## 2. Thứ tự mở từ trái sang phải, thứ tự đóng từ phải sang trái

Với:

```java
try (A a = openA();
     B b = openB();
     C c = openC()) {
    doWork();
}
```

Java **đánh giá các biểu thức khởi tạo từ trái sang phải**: `a`, rồi `b`, rồi `c`. Khi thoát khối, Java **đóng các resource đã tạo thành công theo thứ tự ngược**: `c.close()`, `b.close()`, `a.close()`.

```text
Khởi tạo:  A  -->  B  -->  C
                           |
Chạy thân try               v
Đóng:      A  <--  B  <--  C
```

Nếu một `close()` ném exception, Java **vẫn cố đóng các resource còn lại** đã được đăng ký trong cùng `try-with-resources`. Cơ chế này khác cách viết `finally` sơ sài, nơi một exception tại lần `close()` đầu tiên có thể làm những lần đóng phía sau bị bỏ qua.

### Ví dụ chạy được: quan sát thứ tự đóng

```java
// CloseOrderDemo.java
public class CloseOrderDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("A");
             Resource second = new Resource("B");
             Resource third = new Resource("C")) {
            System.out.println("work");
        }
    }
}
```

Lưu mã vào `CloseOrderDemo.java`, chạy bằng JDK 17+:

```bash
javac CloseOrderDemo.java
java CloseOrderDemo
```

Stdout chính xác:

```text
open A
open B
open C
work
close C
close B
close A
```

Tương tự khi thân `try` dùng `return`, `break` hoặc ném exception: resource vẫn được đóng trước khi luồng điều khiển thực sự rời khỏi vùng quản lý. Riêng kết quả cuối cùng có thể thay đổi nếu `close()` thất bại (xem phần lỗi).

### Hai ý nghĩa của “thứ tự đóng” cần phân biệt

**Thứ tự giữa các resource khai báo** là quy tắc ngược thứ tự của `try-with-resources`. **Thứ tự đóng các tầng wrapper** lại do logic `close()` của wrapper quyết định.

Ví dụ:

```text
BufferedReader (ngoài cùng)
      |
InputStreamReader
      |
FileInputStream (tầng giữ descriptor)
```

Nếu chỉ khai báo **`BufferedReader` ngoài cùng** là resource, khi nó đóng, các lớp chuẩn tương ứng sẽ chuyển yêu cầu đóng xuống tầng được bọc. Đó là **chuỗi đóng lan truyền** bên trong một lần `close()`, không phải ba biến độc lập được Java tự đóng ngược thứ tự.

Tránh vừa khai báo `FileInputStream`, vừa khai báo các wrapper bọc cùng file khi không cần: wrapper đã đóng tầng dưới; TWR sau đó có thể đóng raw stream thêm lần nữa. Nhiều `Closeable` chuẩn chịu được việc đóng lặp, nhưng không nên giả định mọi implementation tùy biến đều idempotent.

## 3. Primary exception và suppressed exception là gì?

Một khối `try-with-resources` có thể phát sinh lỗi ở **nhiều chỗ**:

1. Khi mở/khởi tạo resource.
2. Khi thực hiện nghiệp vụ bên trong thân `try` (`read`, `write`, parse, xử lý dữ liệu...).
3. Khi đóng một hoặc nhiều resource.

Nếu thân `try` đã thất bại, exception đó thường là **primary exception**: nguyên nhân trực tiếp khiến tác vụ thất bại. Nếu sau đó các lệnh `close()` cũng thất bại, Java không làm mất lỗi chính; nó gắn các lỗi đóng vào `primary.getSuppressed()`.

API để kiểm tra:

```java
Throwable primary = ...; // Mã minh họa: giá trị do catch bắt được.
Throwable[] secondaryFailures = primary.getSuppressed();
```

`getSuppressed()` trả một mảng `Throwable` (có thể rỗng). Các suppressed exceptions **không đồng nghĩa** với cause của exception:

- `getCause()` mô tả quan hệ nguyên nhân được một exception dùng để *wrap* hoặc liên kết một lỗi khác.
- `getSuppressed()` mô tả những lỗi bổ sung bị giữ lại trong quá trình xử lý lỗi/cleanup.
- Cùng một `Throwable` có thể vừa có cause vừa có suppressed exceptions; đó là hai quan hệ **khác nhau**.

Java tự tạo quan hệ suppressed khi thực thi `try-with-resources`; ứng dụng hiếm khi cần gọi thủ công `addSuppressed()` cho các đoạn quản lý tài nguyên thông thường.

## 4. Nếu thân `try` lỗi và `close()` cũng lỗi: lỗi thân là primary

Xem chương trình sau. Cả hai resource đều ném `IOException` khi đóng; thân `try` cũng ném một `IOException` khác.

```java
// BodyAndCloseFailureDemo.java
import java.io.IOException;

public class BodyAndCloseFailureDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            System.out.println("work");
            throw new IOException("work failed");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac BodyAndCloseFailureDemo.java
java BodyAndCloseFailureDemo
```

Stdout:

```text
work
close B
close A
primary=work failed
suppressed=close B failed
suppressed=close A failed
```

Diễn tiến:

1. `A` được tạo, sau đó `B` được tạo.
2. Thân `try` ném `IOException("work failed")`: đây là **primary**.
3. Java gọi `B.close()` trước; nó ném `"close B failed"` và lỗi này được **suppressed** dưới lỗi chính.
4. Java tiếp tục gọi `A.close()`; `"close A failed"` được **suppressed** tiếp.
5. `catch (IOException ex)` bên ngoài khối nhận **`work failed`**, không phải exception của `close()`. `getSuppressed()` cung cấp cả hai lỗi phụ.

Đây là lý do `try-with-resources` đáng tin cậy hơn việc để `finally` ném lỗi mới và làm che mất nguyên nhân ban đầu.

## 5. Nếu thân `try` thành công nhưng nhiều lần `close()` lỗi

Không phải lúc nào primary cũng xuất phát từ thân `try`. Nếu phần nghiệp vụ chạy **bình thường**, nhưng các thao tác đóng thất bại thì:

- Exception đầu tiên được ném trong **trình tự đóng** trở thành primary.
- Các exception từ những lần đóng tiếp theo sẽ là suppressed của primary đó.
- Tất cả resource đã đăng ký vẫn được thử đóng.

### Ví dụ chạy được

```java
// CloseOnlyFailureDemo.java
import java.io.IOException;

public class CloseOnlyFailureDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    public static void main(String[] args) {
        try (Resource a = new Resource("A");
             Resource b = new Resource("B")) {
            System.out.println("work completed");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac CloseOnlyFailureDemo.java
java CloseOnlyFailureDemo
```

Stdout:

```text
work completed
close B
close A
primary=close B failed
suppressed=close A failed
```

`B.close()` chạy trước nên lỗi `"close B failed"` thành primary. Dù xảy ra lỗi đóng `B`, `A.close()` vẫn được gọi; lỗi `"close A failed"` được suppressed. Vì thế **không được kết luận thành công chỉ từ việc thân `try` chạy hết**. Một lần ghi file có thể thất bại ở `close()` khi dữ liệu còn đệm được đẩy ra.

### Bảng quyết định ngắn

| Tình huống | Primary exception | Suppressed exceptions |
| --- | --- | --- |
| Thân `try` thành công, mọi `close()` thành công | Không có | Không có |
| Thân `try` lỗi, mọi `close()` thành công | Lỗi thân `try` | Rỗng |
| Thân `try` lỗi, nhiều `close()` lỗi | Lỗi thân `try` | Các lỗi `close()` theo thứ tự xảy ra |
| Thân `try` thành công, nhiều `close()` lỗi | Lỗi từ `close()` đầu tiên trong **thứ tự đóng** | Những lỗi đóng tiếp theo |
| Khởi tạo một resource lỗi sau khi resource trước đã tạo | Lỗi khởi tạo | Lỗi khi đóng những resource trước, nếu có |

Khi không có primary do thân hoặc khởi tạo, lỗi của `close()` có thể thoát ra dù ứng dụng đã xử lý nghiệp vụ xong.

## 6. Lỗi khởi tạo resource giữa chừng: chỉ đóng những đối tượng đã tạo thành công

Phần khai báo trong `try-with-resources` cũng có thể ném exception. Nếu mở `A` thành công nhưng việc tạo `B` thất bại:

- Thân `try` **không chạy**.
- Chỉ `A` là resource đã được khởi tạo/đăng ký thành công, nên Java cố đóng `A`.
- Lỗi mở `B` là primary; lỗi khi đóng `A` (nếu có) trở thành suppressed.
- Không được cố đóng `B` như thể việc khởi tạo đã hoàn tất.

### Ví dụ chạy được: lỗi lúc mở B

```java
// InitializationFailureDemo.java
import java.io.IOException;

public class InitializationFailureDemo {
    static final class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close " + name);
            throw new IOException("close " + name + " failed");
        }
    }

    static Resource open(String name) throws IOException {
        System.out.println("open " + name);
        if ("B".equals(name)) {
            throw new IOException("open B failed");
        }
        return new Resource(name);
    }

    public static void main(String[] args) {
        try (Resource a = open("A");
             Resource b = open("B")) {
            System.out.println("body");
        } catch (IOException ex) {
            System.out.println("primary=" + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed=" + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac InitializationFailureDemo.java
java InitializationFailureDemo
```

Stdout:

```text
open A
open B
close A
primary=open B failed
suppressed=close A failed
```

Không có dòng `body`, không có `close B`. Biến `b` không nhận một resource thành công để đăng ký. Trong code thật, nếu **bản thân constructor/factory** đã giữ được một tài nguyên nội bộ rồi thất bại *trước khi trả đối tượng*, factory phải tự bảo đảm cleanup phần đã giữ; `try-with-resources` của caller chỉ quản lý các resource **đã khởi tạo và đăng ký xong**.

Đây là một khác biệt quan trọng giữa **tài nguyên tạo thành công rồi lỗi về sau** với **tài nguyên chưa bao giờ được tạo thành công**.

## 7. Nếu resource bằng `null`, hoặc khối có `return`?

Một resource expression hợp lệ có thể có giá trị `null` (dù nên tránh nếu có thể); trong trường hợp đó TWR **bỏ qua lời gọi `close()`** trên giá trị `null`.

```java
// Mã minh họa: Resource là kiểu implement AutoCloseable.
try (Resource first = maybeNullResource();
     Resource second = openResource()) {
    use(second);
}
```

Nếu `first == null`, chỉ `second` được đóng. Nếu `second` lỗi khi khởi tạo, `first` chỉ được đóng nếu nó đã khác `null`.

Ngoài ra, `close()` xảy ra trước khi phương thức hoàn tất `return` của thân `try`. Nếu code chuẩn bị `return` nhưng `close()` ném exception, lời gọi phương thức có thể **kết thúc bằng exception thay vì trả giá trị**. Quy tắc primary/suppressed vẫn áp dụng cho những exception thực sự phát sinh. Vì thế không nên thiết kế phương thức “trả thành công” và bỏ qua lỗi đóng writer nếu tính toàn vẹn của dữ liệu quan trọng.

## 8. Vì sao `finally` viết thủ công dễ làm mất lỗi gốc?

Xét mã minh họa **không nên áp dụng nguyên trạng**:

```java
InputStream input = openInput();
try {
    process(input); // Có thể ném IOException("read failed")
} finally {
    input.close();   // Cũng có thể ném IOException("close failed")
}
```

Mã trên giả định `openInput()` và `process(...)` đã tồn tại. Nếu cả `process(input)` và `input.close()` cùng ném exception, exception từ `close()` trong `finally` có thể **thay thế** exception từ thân `try` khi thoát ra ngoài. Ngoài ra, nếu phải mở nhiều resource thủ công và một lần `close()` thất bại, các lệnh đóng còn lại trong `finally` có thể bị bỏ qua nếu không được bao trong các khối `try` riêng.

Dạng tương đương an toàn hơn:

```java
try (InputStream input = openInput()) {
    process(input);
}
```

TWR giữ `read failed` làm primary và thêm `close failed` vào suppressed. Điều này không có nghĩa lỗi đóng “không quan trọng”; nó được giữ để log và điều tra mà không lấn át lỗi ban đầu.

Nếu phải quản lý resource theo cách đặc biệt (chẳng hạn cleanup thủ công có nhiều bước), hãy chủ động bảo toàn exception gốc và thử cleanup từng bước. `Throwable.addSuppressed(Throwable)` có thể dùng khi thật sự cần tự xây dựng cơ chế tương tự, nhưng phải tránh gắn chính exception đó làm suppressed của chính nó và tránh lạm dụng với flow thông thường.

## 9. Quan hệ giữa wrapper I/O và thứ tự đóng

Một hệ thống I/O thường dùng wrapper:

```java
// Mã minh họa: path là java.nio.file.Path đã xác định.
try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.UTF_8))) {
    writer.write("Xin chào");
}
```

Các import cần thiết là `java.io.BufferedWriter`, `java.io.OutputStreamWriter`, `java.nio.file.Files`, `java.nio.charset.StandardCharsets`. Trong chuỗi này:

1. `Files.newOutputStream(path)` mở luồng byte với tài nguyên file.
2. `OutputStreamWriter` chuyển ký tự sang byte UTF-8.
3. `BufferedWriter` giữ bộ đệm ký tự.
4. TWR đóng **`BufferedWriter` ngoài cùng**; các wrapper chuẩn hoàn tất phần dữ liệu chưa ghi và đóng resource được bọc.

Đây là **một biến TWR nhưng nhiều tầng I/O**. Không cần khai báo riêng `OutputStream` raw rồi thêm `BufferedWriter` vào danh sách resource để mong “đóng đúng thứ tự”; có thể tạo tình huống đóng tầng raw hai lần. Ngược lại, nếu có **hai resource độc lập** (ví dụ luồng đọc và luồng ghi không bọc nhau), hãy khai báo cả hai và nhớ chúng được đóng **ngược thứ tự khai báo**.

### `flush()` khác `close()`

- `flush()` yêu cầu đẩy dữ liệu trong bộ đệm xuống tầng tiếp theo, nhưng resource **vẫn mở**.
- `close()` kết thúc vòng đời và với wrapper ghi chuẩn thường hoàn tất việc flush/encode trước khi đóng tầng dưới; các thao tác này **có thể ném lỗi**.
- `flush()`/`close()` thành công không tự bảo đảm dữ liệu đã được ghi bền vững xuống thiết bị lưu trữ; đó là mức bảo đảm khác của I/O/hệ điều hành.

Một ví dụ gần thực tế: thân `try` ghi qua `BufferedWriter` mà không lỗi; khi thoát khối, `close()` mới buộc dữ liệu còn đệm xuống file. Nếu thao tác ghi thực tế thất bại ở thời điểm này, `close()` có thể ném `IOException`. Đừng chỉ log lỗi từ `writer.write(...)` và giả định phần đóng luôn thành công.

## 10. Ownership: tài nguyên của ai thì người đó quyết định đóng

`try-with-resources` **luôn đóng** những resource mà bạn đưa vào danh sách quản lý khi rời khối. Điều đó chỉ đúng về thiết kế nếu phạm vi mã ấy **sở hữu** chúng.

| Tình huống | Cách xử lý |
| --- | --- |
| Hàm tự mở file để đọc/ghi | Dùng TWR, thường trên wrapper ngoài cùng do hàm tạo |
| Hàm nhận `InputStream` từ caller để xử lý tạm | Không tự đóng nếu hợp đồng không chuyển ownership |
| Dùng `System.in`/`System.out` từ nhiều nơi | Tránh đóng wrapper bọc standard stream trong helper vì `close()` có thể lan xuống stream dùng chung |
| Một `Closeable` dùng chung bởi nhiều tác vụ | Chỉ owner đóng sau khi các tác vụ hoàn tất |
| Một object do caller trả về và yêu cầu hàm đóng | Có thể dùng TWR, nhưng phải ghi rõ hợp đồng chuyển quyền sở hữu |

Ví dụ minh họa:

```java
// Hàm mượn reader: caller quản lý vòng đời.
static String firstLine(BufferedReader borrowed) throws IOException {
    return borrowed.readLine();
}
```

Không đưa `borrowed` vào `try (borrowed)` trong helper trừ khi được phép đóng. Ngược lại, với resource tạo nội bộ, TWR giúp cleanup dù có lỗi.

**Lưu ý đối với transaction:** Đóng `Connection` hoặc session truy cập dữ liệu có thể giải phóng tài nguyên, nhưng xử lý commit/rollback và lỗi của chúng phải tuân theo hợp đồng API. Không coi quy tắc tự động đóng của TWR là cơ chế commit giao dịch.

## 11. `AutoCloseable` và `Closeable`: phạm vi exception

Hai interface liên quan nhưng khác chữ ký:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

```java
public interface Closeable extends AutoCloseable {
    void close() throws IOException;
}
```

Đây là **chữ ký rút gọn để giải thích**, không phải mã cần tự định nghĩa lại trong dự án.

Vì `AutoCloseable.close()` có thể khai báo `Exception`, đoạn code dùng một resource `AutoCloseable` tổng quát có thể cần `catch (Exception)` hoặc `throws Exception`. `Closeable.close()` giới hạn checked exception ở `IOException`, thuận lợi khi xử lý Java I/O. Nếu phương thức `close()` cụ thể không khai báo checked exception, compiler có thể không yêu cầu `throws/catch` chỉ cho thao tác đóng.

Tài liệu `AutoCloseable` **không đòi hỏi mọi implementation đều idempotent**. Tài liệu `Closeable` có hợp đồng đóng lại không gây hiệu lực mới, nhưng đừng suy điều đó cho một wrapper tự viết chỉ implement `AutoCloseable`. Tài nguyên tùy biến cần định nghĩa rõ hành vi đóng nhiều lần, trạng thái sau khi đóng và exception khi cleanup.

### Exception có thể bị suppression không?

Thông thường, exception sinh ra khi đóng sẽ được thêm vào primary nhờ cơ chế TWR. Tuy nhiên, lớp `Throwable` có constructor chuyên biệt cho phép **vô hiệu hóa suppression** đối với một instance exception. Vì vậy `getSuppressed()` có thể trống ngay cả khi có quá trình thêm suppressed trong một số cấu hình khác thường. Với `IOException` và những exception thông dụng trong ví dụ, suppression được bật như bình thường.

Đó là ngoại lệ nâng cao, không làm thay đổi quy tắc sử dụng `try-with-resources` của Java I/O.

## 12. Ghi log, bắt lỗi và kiểm thử đúng cách

### `catch` bên ngoài TWR mới thấy lỗi đóng

Một cấu trúc điển hình:

```java
try (BufferedReader reader = openReader()) {
    consume(reader);
} catch (IOException e) {
    log(e);
}
```

Đây là mã minh họa với `openReader()`, `consume()` và `log()` do ứng dụng cung cấp. Khi `catch` bao ngoài toàn bộ `try-with-resources`, nó nhận cả lỗi thân `try` lẫn lỗi đóng (tùy loại exception).

Nếu đặt `catch` bên **trong** thân `try` để chỉ bao các thao tác đọc, nó không thể xử lý lỗi đóng xảy ra **sau** khi thân đó kết thúc. Muốn quan sát lỗi cleanup, đặt bộ xử lý ở ngoài vùng đóng hoặc để exception đi lên caller.

### Đừng chỉ xem `getMessage()`

Dùng `printStackTrace()` hoặc logger có khả năng log **nguyên đối tượng `Throwable`** thường giúp hiển thị suppressed exceptions. Nếu hệ thống log chỉ lưu message, hãy duyệt `getSuppressed()`:

```java
// Mã minh họa nằm bên trong catch, với e là Throwable.
System.err.println("primary: " + e);
for (Throwable suppressed : e.getSuppressed()) {
    System.err.println("suppressed: " + suppressed);
}
```

Khi cần điều tra, phân biệt lỗi đọc/ghi ban đầu, lỗi flush/close, lỗi khi mở resource tiếp theo và thứ tự cleanup. Nếu có lỗi đóng lồng nhau trong một wrapper tự viết, suppressed exceptions cũng có thể xuất hiện ở các cấp khác nhau trong cây exception; công cụ log nên giữ cấu trúc Throwable.

### Bộ test tối thiểu

1. Mở và đóng ba resource không lỗi: kiểm tra thứ tự mở trái→phải và đóng phải→trái.
2. Thân `try` ném exception, tất cả `close()` thành công: kiểm tra primary là lỗi thân.
3. Thân `try` lỗi và hai `close()` lỗi: kiểm tra primary vẫn là lỗi thân, suppressed đủ hai lỗi đóng.
4. Thân `try` thành công nhưng hai `close()` lỗi: kiểm tra lỗi đóng đầu tiên là primary, lỗi còn lại suppressed.
5. Tạo `A` thành công, tạo `B` thất bại: kiểm tra thân không chạy, `A` đóng và lỗi đóng `A` là suppressed.
6. Resource `null`: xác minh không có lời gọi `close()` trên null.
7. Wrapper writer có bộ đệm: bảo đảm đóng wrapper ngoài cùng hoàn tất dữ liệu và lỗi đóng được nhận diện.
8. Caller sở hữu stream: xác minh helper không đóng resource ngoài phạm vi quyền sở hữu.

## 13. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Nghĩ thứ tự đóng giống thứ tự khai báo | Dễ hiểu sai luồng cleanup | TWR đóng **ngược thứ tự khai báo**. |
| Nghĩ `close()` thất bại sẽ khiến các resource còn lại không được thử đóng | Bỏ qua cơ chế TWR | TWR vẫn thử đóng các resource còn lại đã khởi tạo. |
| Nghĩ mọi lỗi đóng đều là primary | Mất ý nghĩa lỗi nghiệp vụ | Khi thân/khởi tạo đã có lỗi, close failures thường được **suppressed**. |
| Chỉ đọc `getCause()` để tìm mọi lỗi | Bỏ sót suppressed errors | Kiểm tra cả `getSuppressed()`. |
| Dùng `finally` đơn giản để đóng rồi ném lỗi khác | Có thể che mất lỗi thân `try` | Ưu tiên TWR. |
| Khai báo wrapper và luồng raw cùng là resource dù wrapper đóng raw | Đóng lặp và khó quản lý ownership | TWR trên wrapper ngoài cùng khi nó sở hữu nguồn. |
| Đóng một reader do caller truyền vào | Làm hỏng luồng dùng chung | Chỉ đóng resource mình sở hữu hoặc đã nhận chuyển ownership. |
| Cho rằng thân `try` thành công là ghi file thành công | Lỗi có thể phát sinh lúc flush/close | Xử lý lỗi đóng và quan sát exception. |
| Cho rằng `close()` cũng là `commit()` | Có thể sai ngữ nghĩa giao dịch | Dùng API transaction theo hợp đồng. |
| Chỉ log `exception.getMessage()` | Không thấy các lỗi suppressed | Log nguyên `Throwable` hoặc duyệt suppressed. |

## 14. Tự kiểm tra

**Câu 1.** `try (A a = ...; B b = ...; C c = ...)` đóng theo thứ tự nào khi cả ba đã tạo thành công?

**Đáp án:** `c.close()`, `b.close()`, `a.close()`.

**Câu 2.** Thân `try` ném `IOException("body")`, `B.close()` rồi `A.close()` đều ném lỗi. `catch` nhận primary nào?

**Đáp án:** Lỗi `"body"`; lỗi đóng `B`, `A` nằm trong `getSuppressed()` theo thứ tự phát sinh.

**Câu 3.** Thân `try` thành công, `B.close()` ném lỗi rồi `A.close()` cũng ném lỗi. Lỗi nào là primary?

**Đáp án:** Lỗi đóng `B`, vì B được đóng trước; lỗi đóng A suppressed dưới lỗi B.

**Câu 4.** Tạo A xong nhưng tạo B thất bại. B có được tự động đóng không?

**Đáp án:** Không có B được khởi tạo thành công để đóng; Java đóng A, có thể ghi lỗi đóng A vào suppressed của lỗi mở B.

**Câu 5.** `getCause()` và `getSuppressed()` có cùng ý nghĩa không?

**Đáp án:** Không. Cause diễn tả quan hệ nguyên nhân/wrapping; suppressed giữ những lỗi bổ sung xảy ra trong cleanup hoặc các trường hợp ứng dụng chủ động bổ sung.

**Câu 6.** `BufferedWriter` bọc `OutputStreamWriter` bọc `FileOutputStream`. Khi hàm sở hữu cả chuỗi, khai báo tài nguyên nào trong TWR?

**Đáp án:** Thường chỉ cần writer **ngoài cùng**, nếu close của những wrapper chuẩn lan xuống và đóng các tầng bên dưới.

**Câu 7.** Helper chỉ mượn `InputStream` của caller thì có đưa nó vào TWR không?

**Đáp án:** Không, trừ khi hợp đồng giao quyền đóng cho helper.

**Câu 8.** Vì sao cần quan tâm đến lỗi `close()` dù `write()` không ném lỗi?

**Đáp án:** Writer có thể còn dữ liệu trong buffer hoặc trạng thái mã hóa; việc flush/hoàn tất khi đóng có thể mới làm lỗi I/O xuất hiện.

**Ghi nhớ:** **TWR khởi tạo từ trái sang phải và đóng từ phải sang trái.** Nếu đã có lỗi ở thân `try` hoặc lúc khởi tạo, các lỗi `close()` được giữ dưới dạng **suppressed exceptions** (trong trường hợp suppression thông thường). Nếu thân thành công và nhiều lần đóng lỗi, **lỗi đóng xảy ra đầu tiên** là primary, những lỗi đóng sau là suppressed. Đóng đúng resource mình sở hữu và luôn xem cả `getSuppressed()` khi điều tra thất bại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-005">Ai mở, ai đóng: quy tắc sở hữu trong thiết kế API</a>

<details>
<summary>Xem chi tiết</summary>

# Ai mở, ai đóng: quy tắc sở hữu tài nguyên trong thiết kế API Java I/O

> **Ngữ cảnh:** Java Core → I/O → **Tài nguyên và lỗi**. Bài này trả lời câu hỏi tưởng chừng đơn giản nhưng dễ gây rò rỉ và lỗi khó dò: **ai chịu trách nhiệm gọi `close()`, khi nào được chuyển trách nhiệm, và một hàm đọc/ghi có nên đóng stream mà caller truyền vào không?** Kiến thức áp dụng cho `InputStream`, `OutputStream`, `Reader`, `Writer`, kênh/tệp và các đối tượng I/O mở. Các ví dụ chạy độc lập dùng Java 17; phần lớn hợp đồng nền tảng đã có từ trước.

## 1. Sở hữu tài nguyên là một hợp đồng, không chỉ là thao tác gọi close

Tài nguyên I/O như file descriptor, socket, directory handle hoặc channel thường có **vòng đời độc lập** với đối tượng Java đang tham chiếu nó. Khi mở một tệp, JVM/OS có thể cấp phát handle hữu hạn; bỏ quên `close()` có thể khiến cạn handle, file vẫn bị khóa, buffer không được flush đúng lúc hoặc tài nguyên tồn tại lâu hơn dự kiến.

**Resource owner (chủ sở hữu)** là thành phần chịu trách nhiệm quyết định vòng đời và **đóng** tài nguyên đúng lúc, kể cả lúc có exception. Quyền sở hữu cần được thể hiện trong **hợp đồng API**: parameter là nguồn được mượn hay nguồn được chuyển giao, kết quả trả về đã mở có cần caller đóng hay method đã tự dọn xong?

Quy tắc thường dùng là:

- **Hàm tự mở để dùng nội bộ → hàm đó thường tự đóng** trước khi trả kết quả đã tính.
- **Hàm nhận stream do caller cung cấp → mặc định nên coi stream là được mượn, chỉ sử dụng chứ không đóng**, trừ khi API nói rõ việc chuyển ownership.
- **Hàm trả về một stream/channel còn mở → caller nhận quyền sở hữu và chịu trách nhiệm đóng**.
- **Hàm bọc stream (decorator/wrapper) → phải đọc hợp đồng `close()` của wrapper**: đóng lớp ngoài thường đóng luôn nguồn bên trong.
- **Tài nguyên dùng chung hoặc do framework/JVM quản lý → không tự ý đóng từ một hàm tiện ích**.

Những câu trên là **quy ước thiết kế API**, không phải luật cứng của ngôn ngữ Java. Việc bạn gọi `new` hay nhận reference **không tự chứng minh** quyền sở hữu. Một factory có thể mở resource rồi **chuyển** quyền đóng cho caller; một constructor có thể nhận resource do caller mở nhưng hợp đồng quy định wrapper được sở hữu nó. Cần ghi rõ và nhất quán.

### Điều gì xảy ra nếu ownership không rõ?

| Sai lệch vòng đời | Hậu quả |
| --- | --- |
| Không ai gọi `close()` | Rò rỉ file descriptor, socket, directory handle; giải phóng muộn và không xác định |
| Hai bên tưởng mình có quyền đóng và một bên đóng sớm | `IOException: Stream closed` hoặc thao tác đọc/ghi thất bại ở bên còn dùng |
| Một hàm tiện ích đóng stream caller đang dùng | Hỏng pipeline, file/socket chung không dùng tiếp được |
| Method mở stream rồi trả `Stream` lazy mà không ghi trách nhiệm đóng | Caller tưởng terminal operation tự đóng; file/directory vẫn mở |
| Wrapper bị đóng sau một lần xử lý, làm đóng luôn underlying stream | Bên khác dùng nguồn gốc bị lỗi dù không gọi `close()` trực tiếp |
| Không flush/close output theo đúng vòng đời | Dữ liệu còn trong buffer, tệp có thể thiếu nội dung sau khi tiến trình kết thúc bất thường |
| Resource phục vụ tác vụ async bị đóng trước khi tác vụ chạy xong | Race giữa `close()` và I/O, lỗi không ổn định theo timing |

## 2. Ba kiểu ownership nên định nghĩa rõ trong Java API

### 2.1. Borrowed: hàm chỉ mượn resource

Caller vẫn sở hữu `InputStream` hoặc `OutputStream`. Hàm tiện ích được **đọc/ghi**, nhưng **không đóng** chúng và không giữ reference để dùng sau khi trả về, trừ khi có thỏa thuận riêng.

~~~java
// Hợp đồng: borrowed input/output; method không close hai stream.
static long copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    long copied = 0;
    int n;
    while ((n = in.read(buffer)) != -1) {
        out.write(buffer, 0, n);
        copied += n;
    }
    return copied;
}
~~~

Đây là fragment bên trong một class, cần import `java.io.InputStream`, `java.io.OutputStream` và `java.io.IOException`. Hàm chỉ truyền byte, **không đóng** hoặc flush `out`: caller có thể còn muốn ghi thêm dữ liệu, hoặc output này thuộc request/transaction lớn hơn. Nếu hợp đồng ứng dụng yêu cầu flush tại một điểm cụ thể, điều đó cũng phải ghi rõ; không âm thầm flush trên mọi lần gọi.

### 2.2. Owned: hàm nhận trách nhiệm đóng resource

Nếu hàm nhận `Path` và **tự mở** tệp trong thân hàm, nó thường sở hữu reader vừa mở:

~~~java
// Hợp đồng: method tự mở và tự đóng BufferedReader.
static long countLines(Path path) throws IOException {
    try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        long count = 0;
        while (reader.readLine() != null) {
            count++;
        }
        return count;
    }
}
~~~

Đây là fragment với import `java.io.BufferedReader`, `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets`. Caller chỉ truyền đường dẫn, không quản lý reader nội bộ. `try-with-resources` đóng reader trên cả đường thành công lẫn ngoại lệ.

### 2.3. Transferred: hàm giao quyền đóng cho bên nhận

~~~java
// Hàm mở rồi trả một resource còn sống.
// Caller trở thành owner khi return thành công.
static InputStream openDocument(Path path) throws IOException {
    return Files.newInputStream(path);
}

// Cách gọi đúng:
try (InputStream stream = openDocument(path)) {
    // Đọc stream...
}
~~~

Đây là fragment, cần import tương ứng. Trả về `InputStream` **không tự động** làm stream được đóng; hãy ghi trong Javadoc rằng **caller must close returned stream**. Caller phải đóng kể cả khi chỉ đọc vài byte hoặc sau khi parse thất bại. Nếu method nhận một stream và **nhận chuyển giao ownership** vào đối tượng giữ lâu, hợp đồng phải ghi rõ thời điểm đối tượng sở hữu sẽ đóng nó.

## 3. Bảng quyết định: ai đóng trong những thao tác I/O quen thuộc?

| Nguồn hoặc hành động | Ai mở / tạo | Ai nên đóng | Ghi chú |
| --- | --- | --- | --- |
| `Files.newInputStream(path)` | Method `Files` mở handle; caller nhận return | **Caller** | Dùng try-with-resources bao quanh đoạn sử dụng. |
| `Files.newBufferedReader(path, UTF_8)` | `Files` mở reader | **Caller** | Trả `BufferedReader` mở, gọi `close()` sau đọc. |
| `Files.readString(path)` hoặc `Files.readAllBytes(path)` | API tự thực hiện đọc tệp | **API** quản lý handle nội bộ | Caller nhận `String`/`byte[]`, không nhận resource mở để đóng. |
| `Files.lines(path)` | API mở file và trả stream lazy | **Caller** đóng `Stream<String>` | `count()`, `findFirst()`, `forEach()` **không tự thay thế** `close()`. |
| `Files.list(dir)` / `Files.walk(dir)` | API mở directory handle | **Caller** đóng stream | Dùng try-with-resources ngay cả khi đã xử lý xong terminal operation. |
| `Files.newDirectoryStream(dir)` | API mở directory stream | **Caller** | Không để directory handle sống quá phạm vi liệt kê. |
| `new BufferedInputStream(input)` | Wrapper tạo buffer, nguồn `input` có trước | **Phụ thuộc hợp đồng sở hữu wrapper** | `close()` wrapper gọi close xuống nguồn bên trong. |
| `new BufferedReader(reader)` | Wrapper mới, reader có trước | **Phụ thuộc ownership nguồn** | Đóng wrapper cũng đóng reader được bọc. |
| `new Scanner(input)` | Scanner mới dùng nguồn caller truyền | Phải quyết định rõ trước khi đóng scanner | Khi `Scanner.close()` và nguồn implement `Closeable`, scanner cũng đóng nguồn. |
| `System.in` / `System.out` / `System.err` | JVM/môi trường cung cấp standard streams | Thường **không đóng** ở hàm tiện ích | Nếu đóng, những phần khác của ứng dụng có thể mất stdin/stdout/stderr. |
| `InputStream.transferTo(output)` | Hai stream do các bên cung cấp | **Caller/owner của từng stream** | Method sao chép không tự đóng input/output. |
| `ByteArrayInputStream` / `ByteArrayOutputStream` | Bộ nhớ trong JVM | Theo contract, close có thể không làm gì | Vẫn nhất quán về ownership khi đổi sang nguồn file/socket trong tương lai. |
| Stream/channel do server framework cấp | Runtime hoặc container | Theo **hợp đồng framework** | Không suy ra caller phải đóng chỉ vì kiểu là `InputStream`. |

**Phân biệt quan trọng:** một object implement `Closeable` không nhất thiết là nơi cần close; đó chỉ là **khả năng đóng**. Ownership được quyết định bởi **hợp đồng** và vòng đời. Ngược lại, không phải thứ cần đóng đều có kiểu `InputStream`: `Stream<String>` từ `Files.lines` cũng phải đóng vì giữ file handle.

## 4. Ví dụ độc lập: phương thức mượn stream không tự đóng

Lưu file **`BorrowedCopyDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BorrowedCopyDemo {
    // Bộ đếm giúp quan sát chính xác ai gọi close().
    static final class TrackedInput extends FilterInputStream {
        int closes;

        TrackedInput(byte[] source) {
            super(new ByteArrayInputStream(source));
        }

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }
    }

    // API borrowed: method không sở hữu input/output.
    static long copyBorrowed(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[8];
        long total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            total += n;
        }
        return total;
    }

    public static void main(String[] args) throws IOException {
        TrackedInput in = new TrackedInput(
                "abc".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (in) { // Caller sở hữu và đóng input.
            System.out.println("bytes=" + copyBorrowed(in, out));
            System.out.println("closesInside=" + in.closes);
        }

        System.out.println("closesAfter=" + in.closes);
        System.out.println("text=" +
                new String(out.toByteArray(), StandardCharsets.UTF_8));
    }
}
~~~

~~~bash
javac --release 17 BorrowedCopyDemo.java
java BorrowedCopyDemo
~~~

Stdout:

~~~text
bytes=3
closesInside=0
closesAfter=1
text=abc
~~~

`TrackedInput` chỉ phục vụ bài học: bộ đếm tăng mỗi lần wrapper nhận `close()`, giúp chứng minh hàm `copyBorrowed` **không đóng stream**. Tài nguyên thật trong demo là `ByteArrayInputStream` đã nằm trong bộ nhớ, nên close của nguồn đó không có hiệu ứng giải phóng file descriptor. Ở file/socket thực, việc đóng sớm có thể ảnh hưởng mạnh hơn.

Đặc biệt, `ByteArrayOutputStream.close()` không làm vô hiệu hóa các phương thức của lớp này theo contract, nhưng **không được** dùng tính chất đó để suy ra `FileOutputStream` hoặc network stream sẽ hành xử tương tự.

### Vì sao không đặt try-with-resources bên trong copyBorrowed?

Đây là **sai hợp đồng** khi parameter là borrowed:

~~~java
// Phản ví dụ thiết kế: không dùng khi caller vẫn sở hữu input.
static long wrongCopy(InputStream input, OutputStream output)
        throws IOException {
    try (input; output) { // TỰ ĐỘNG đóng cả hai khi method kết thúc!
        return input.transferTo(output);
    }
}
~~~

Trong Java 9+, `try (input; output)` hợp lệ nếu hai biến effectively final, nhưng nó **chuyển hành vi thành đóng cả input và output** khi thoát method. Thậm chí nếu `transferTo` tự nó không đóng hai stream, try-with-resources đã làm điều đó. Hàm này chỉ hợp lệ nếu contract **nêu rõ nhận ownership của cả hai**. Đừng chọn đóng chỉ vì nhìn thấy interface `AutoCloseable`.

## 5. Ví dụ độc lập: hai kiểu API đọc tệp và hai điểm đóng

Lưu file **`OpenVsReturnDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class OpenVsReturnDemo {
    // Method tiêu thụ nội bộ: tự mở, tự đóng.
    static long countLines(Path path) throws IOException {
        try (BufferedReader reader =
                     Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            long count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
    }

    // Factory chuyển quyền đóng Stream<String> sang caller.
    static Stream<String> openLines(Path path) throws IOException {
        return Files.lines(path, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("ownership-", ".txt");
        try {
            Files.writeString(
                    file, "one\ntwo\n", StandardCharsets.UTF_8);

            System.out.println("count=" + countLines(file));

            // Stream từ Files.lines giữ file handle đến lúc close.
            try (Stream<String> lines = openLines(file)) {
                System.out.println(
                        "first=" + lines.findFirst().orElse("none"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

~~~bash
javac --release 17 OpenVsReturnDemo.java
java OpenVsReturnDemo
~~~

Stdout:

~~~text
count=2
first=one
~~~

Bài học chính:

- `countLines(Path)` mở `BufferedReader` **và tự đóng** trước khi return `long`. Không có resource mở được trả về caller.
- `openLines(Path)` trả `Stream<String>` có file handle. Caller phải dùng `try (Stream<String> lines = ...)` dù chỉ gọi `findFirst()`. Tập tin cần được đóng **trước** khi đoạn `finally` cố xóa tệp tạm.
- `Files.writeString` tự quản lý thao tác mở/đóng file khi nó trả về; `Files.createTempFile` tạo tệp, nhưng xóa **tệp vật lý** là trách nhiệm cleanup khác với close handle. Đừng nhầm `close()` với `Files.deleteIfExists()`.
- Với file có dữ liệu tùy ý lớn, `countLines` chỉ đọc từng dòng bằng buffer, không gom toàn bộ vào một `List` hoặc `String`.

## 6. API nhận callback: mượn resource trong một phạm vi được kiểm soát

Một lựa chọn hữu ích là **lifetime-scoped callback**: method mở/đóng resource, callback chỉ được sử dụng trong thời gian method gọi nó. Ví dụ:

~~~java
@FunctionalInterface
interface ReaderTask<T> {
    T apply(BufferedReader reader) throws IOException;
}

// Fragment: hàm tự mở và tự đóng reader.
static <T> T withReader(Path path, ReaderTask<T> task)
        throws IOException {
    try (BufferedReader reader =
                 Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        return task.apply(reader);
    }
}

// Cách dùng minh họa:
String first = withReader(path, BufferedReader::readLine);
~~~

Đây là **fragment**, cần đặt trong class, import `java.io.BufferedReader`, `java.io.IOException`, `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.charset.StandardCharsets`; biến `path` là `Path` hợp lệ. Callback được **mượn** reader; sau callback (dù thành công hay thất bại), `withReader` đóng nó.

Điểm mạnh: caller không cần nhớ close, method giới hạn tài nguyên tồn tại trong một lexical scope, lỗi I/O/close có đường đi rõ ràng. Điểm cần nêu trong hợp đồng: callback **không được đóng**, giữ reference, trả resource đang mở ra ngoài hoặc bắt đầu tác vụ nền tiếp tục dùng reader sau khi callback return. Java generic không tự ngăn caller trả reference ra khỏi scope, nên đây vẫn là **ràng buộc contract**; có thể thiết kế kiểu trả về giới hạn để giảm khả năng vi phạm.

Với callback xử lý `OutputStream`, tương tự: method tạo stream và đóng sau callback; callback phải hoàn tất ghi **trong** callback. Nếu asynchronous I/O cần sống sau khi hàm return, không dùng thiết kế callback ngắn hạn như thể công việc đã hoàn tất đồng bộ.

## 7. Wrapper và decorator: đóng lớp ngoài thường đóng lớp trong

Ví dụ:

~~~java
// Method tạo cả input stream và reader, nên sở hữu cả chuỗi wrapper.
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8))) {
    String firstLine = reader.readLine();
}
~~~

Đoạn này là **fragment** nằm trong method xử lý `IOException` với các import tương ứng. `BufferedReader.close()` gọi close của reader bên trong; `InputStreamReader.close()` đóng underlying input stream. Như vậy **đóng wrapper ngoài cùng** có thể giải phóng cả chuỗi nguồn. Không cần khai báo mọi tầng bằng nhiều biến trong try-with-resources chỉ để gọi close lặp lại.

Nhưng nếu tài nguyên bên trong **được caller cho mượn**, cùng cơ chế lan truyền close đó trở thành bẫy:

~~~java
// Phản ví dụ khi 'borrowed' còn được caller dùng:
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(borrowed, StandardCharsets.UTF_8))) {
    return reader.readLine();
} // reader.close() kéo theo borrowed.close().
~~~

Nếu method muốn đóng wrapper nhưng giữ nguồn sống, cần một **hợp đồng chia sẻ thực sự**, thiết kế lớp adapter không đóng underlying theo yêu cầu cụ thể, hoặc để owner tạo wrapper và chuyển ownership. Không đơn giản bỏ `close()` của wrapper rồi cho rằng mọi thứ ổn: buffer có thể đã **đọc trước** nhiều byte, làm vị trí nguồn của caller thay đổi ngoài dự kiến. Chọn **một owner rõ ràng cho chuỗi wrapper** là thiết kế dễ kiểm chứng nhất.

Một ví dụ khác là `Scanner`: tài liệu chuẩn nêu rằng nếu Scanner được đóng, nó sẽ đóng nguồn underlying nếu nguồn đó implement `Closeable`. Vì vậy:

~~~java
// Nguy hiểm trong ứng dụng còn cần tiếp tục đọc System.in:
try (java.util.Scanner scanner =
             new java.util.Scanner(System.in)) {
    String command = scanner.nextLine();
} // Scanner.close() có thể đóng System.in.
~~~

Nếu scanner đọc `System.in` cho toàn bộ vòng đời CLI, thường để thành phần **đầu vào cấp ứng dụng** sở hữu scanner và quyết định khi nào đóng, thay vì tạo/đóng scanner mỗi lần đọc một token. Không tạo nhiều lớp scanner/reader có buffer trên cùng input để đọc xen kẽ.

## 8. Nên flush, close hay cả hai?

`flush()` và `close()` có mục đích khác nhau:

- **`flush()`** trên một output có buffering nhằm đẩy dữ liệu đang nằm trong buffer của lớp đó về sink phía dưới theo contract; **không đồng nghĩa** bảo đảm OS đã ghi bền vững xuống ổ đĩa, và **không giải phóng** handle.
- **`close()`** kết thúc vòng đời resource, giải phóng những tài nguyên nó nắm giữ. Nhiều buffered output có thể flush dữ liệu còn lại trong quá trình close; **close có thể thất bại**. Không được coi lời gọi close thành công chỉ vì vòng lặp ghi đã xong.
- Với API borrowed, method copy thường **không tự flush/close output** trừ khi được yêu cầu; owner quyết định khi dữ liệu đã đầy đủ và bao giờ kết thúc.
- Với API owned, hãy đóng output trong try-with-resources. Nếu cần bảo đảm độ bền dữ liệu trên storage, đó là vấn đề khác: `FileChannel.force(...)` hoặc `FileDescriptor.sync()` có contract riêng và phụ thuộc hệ thống tệp.

Ví dụ:

~~~java
// Fragment: method sở hữu writer vì tự mở file.
try (BufferedWriter writer =
         Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("Đã hoàn thành\n");
} // BufferedWriter được close dù body có lỗi.
~~~

Không diễn giải `writer.flush()` là thay thế được `writer.close()`. Ngược lại, **không gọi `close()` của một output caller mượn** chỉ để “chắc đã flush”, vì nó chấm dứt quyền sử dụng của owner.

## 9. Try-with-resources: ai đóng, đóng khi nào?

`try-with-resources` dùng với object implement `AutoCloseable`; block thoát bằng return, exception hoặc chạy hết thì resource được close theo thứ tự ngược với khai báo.

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    in.transferTo(out);
} // out.close() trước, sau đó in.close().
~~~

Đây là fragment; `source`/`target` là `Path` được cung cấp, cùng import I/O và Files. Code **sở hữu hai stream** vì vừa mở cả hai trong block. Từ Java 9, `InputStream.transferTo` không tự đóng hai stream; khối try đảm nhiệm trách nhiệm đóng.

Các trường hợp quan trọng:

1. Khi khởi tạo resource thứ hai ném lỗi, resource thứ nhất đã tạo thành công vẫn được đóng tự động.
2. Nếu code trong body ném exception **và** close cũng ném, exception của body là **primary exception**; lỗi close được gắn vào `getSuppressed()`.
3. Nếu body thành công nhưng close ném exception, exception từ close được truyền ra ngoài theo loại ngoại lệ tương ứng. Không bỏ qua nó nếu kết quả ghi cần được xác nhận.
4. Khi có nhiều resource, Java đóng theo **thứ tự ngược** lúc khai báo; đừng giả định dependency được đóng theo thứ tự tùy ý.
5. `try (resourceVariable)` từ Java 9 cho phép dùng biến local/parameter final hoặc effectively final đã tồn tại, nhưng **đó vẫn là yêu cầu đóng resource đó**. Dùng cú pháp này với parameter mượn sẽ chuyển đổi contract một cách âm thầm.
6. `Closeable.close()` theo hợp đồng chuẩn có tính idempotent khi gọi lại; `AutoCloseable` tổng quát **không bắt buộc** mọi implementation đều idempotent. Không dựa vào double-close như chiến lược ownership.

`try-with-resources` là **cơ chế thực hiện việc đóng**; nó không tự quyết định **ai có quyền đóng**. Quyết định ownership phải có trước khi đặt đối tượng vào dấu ngoặc `try (...)`.

## 10. Ví dụ độc lập: lỗi trong body và lỗi khi đóng

Lưu file **`SuppressedCloseDemo.java`**:

~~~java
public class SuppressedCloseDemo {
    static final class Faulty implements AutoCloseable {
        private final String name;

        Faulty(String name) {
            this.name = name;
        }

        @Override
        public void close() throws Exception {
            throw new Exception("close-" + name);
        }
    }

    public static void main(String[] args) {
        try (Faulty a = new Faulty("A");
             Faulty b = new Faulty("B")) {
            throw new IllegalStateException("body");
        } catch (Exception ex) {
            System.out.println("main=" + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println(
                        "suppressed=" + suppressed.getMessage());
            }
        }
    }
}
~~~

~~~bash
javac --release 17 SuppressedCloseDemo.java
java SuppressedCloseDemo
~~~

Stdout:

~~~text
main=body
suppressed=close-B
suppressed=close-A
~~~

**Giải thích:** body thất bại với `IllegalStateException("body")`. Java vẫn gọi `close()` trên `b` rồi `a`, nhưng không đánh mất exception gốc; hai lỗi đóng trở thành **suppressed exceptions**. Nếu logger chỉ in `ex.getMessage()` mà bỏ stack trace/suppressed, có thể mất thông tin quan trọng về việc release tài nguyên thất bại. Với ứng dụng server, cần log/report cả exception chính và suppressed theo chính sách bảo mật/logging phù hợp.

Ví dụ dùng lớp tự tạo `Faulty` để bảo đảm output xác định và không cần file; đây là tình huống minh họa chủ đích, không phải cách implement `close()` thông thường.

## 11. Kiểu trả về và tên hàm nên diễn đạt ownership

Nhìn vào chữ ký:

~~~java
byte[] readAll(Path path);        // Trả dữ liệu đã đọc, resource nội bộ đóng.
InputStream open(Path path);      // Trả resource mở, caller đóng.
void copy(InputStream in, OutputStream out); // Borrowed cả hai, không đóng.
~~~

Ba dòng này là **API sketch**, không có implementation, chưa khai báo `IOException`; dùng để so sánh contract chứ không phải mã chạy độc lập.

Tên `readAll` gợi ý xử lý kết thúc trong method; `open` gợi ý caller nhận handle đang sống; `copy` gợi ý thao tác trên các handle được cho sẵn. Tuy vậy tên chỉ hỗ trợ người đọc, **không thay thế Javadoc**.

Mẫu Javadoc rõ ràng cho API borrowed:

~~~java
/**
 * Copies remaining bytes from input to output.
 *
 * <p>Both streams are owned by the caller. This method neither closes
 * nor flushes either stream; caller controls their lifetimes.
 *
 * @param input an open, caller-owned source stream
 * @param output an open, caller-owned destination stream
 * @return number of bytes copied
 * @throws IOException if an I/O operation fails
 */
public static long copyBorrowed(InputStream input,
                                OutputStream output)
        throws IOException {
    return input.transferTo(output); // Java 9+; transferTo không đóng stream.
}
~~~

Method này có thể nằm trong class Java 17 và cần import I/O tương ứng. Nếu muốn hỗ trợ Java 8, thay `transferTo` bằng vòng lặp buffer như ví dụ trên. Có thể xác nhận `null` không được phép bằng `Objects.requireNonNull` nếu muốn fail sớm theo contract.

Mẫu Javadoc cho API transferred:

~~~java
/**
 * Opens an input stream. The returned stream is open and its
 * caller MUST close it, preferably with try-with-resources.
 */
public static InputStream open(Path path) throws IOException {
    return Files.newInputStream(path);
}
~~~

Các phương án khác cũng hợp lệ: method nhận `Supplier` tạo nguồn bên trong, method nhận callback tiêu thụ trong một scope, hoặc object có `close()` sở hữu nhiều resource. Hãy chọn một contract nhất quán và nói rõ khi nào owner thay đổi.

## 12. Những trường hợp đặc biệt cần thận trọng

### 12.1. Standard streams và tài nguyên dùng chung

`System.in`, `System.out`, `System.err` thường phục vụ **toàn bộ tiến trình**. Một hàm tiện ích nhận `System.in` thường chỉ nên đọc và để caller/runtime sở hữu vòng đời. Ví dụ `new BufferedReader(new InputStreamReader(System.in, UTF_8))` rồi đóng reader có thể đóng luôn `System.in`. Đóng `System.out` cũng có thể làm những lần xuất tiếp theo thất bại; `PrintStream` không nhất thiết ném ngoại lệ ghi theo cách bạn mong đợi.

Nếu thực sự có trường hợp chương trình cần đóng standard stream, đó là quyết định cấp ứng dụng có chủ đích, không nên xảy ra bên trong helper đọc một dòng. Nếu chỉ muốn chắc prompt xuất ra, `System.out.flush()` không có nghĩa đóng `System.out`.

### 12.2. Resource do framework/container cấp

Một framework có thể trao request body stream, response writer hoặc connection được quản lý theo vòng đời request/transaction. Cho dù object có `close()`, thư viện nhận nó không nên tự đóng nếu framework giữ ownership. Phải theo tài liệu chính thức của framework cụ thể; cùng kiểu `InputStream` không nói lên ownership. Nếu phương thức API của bạn **nhận quyền sở hữu** thay vì mượn, hãy ghi rõ và bảo đảm lỗi/cleanup đúng trong mọi đường đi.

### 12.3. Shared resource giữa nhiều thành phần

Hai service cùng dùng một stream không nên đều tin rằng mình là owner độc lập. Có thể để một owner cấp reference mượn, dùng cơ chế lease hoặc một service có trách nhiệm quản lý vòng đời. Nếu resource có thể được nhiều thread dùng đồng thời, cần thêm hợp đồng **thread safety** và đồng bộ hóa: ownership không tự làm việc đọc/ghi đồng thời an toàn.

### 12.4. Async: thời gian sống của task lớn hơn thời gian sống của method

~~~java
// PHẢN VÍ DỤ THIẾT KẾ — không thực thi như code production.
try (InputStream in = Files.newInputStream(path)) {
    CompletableFuture.runAsync(() -> consume(in));
} // in.close() có thể xảy ra trước khi task consume(in) đọc xong.
~~~

Đây là **sketch** minh họa lifecycle, không phải chương trình chạy độc lập; `consume` là hàm do ứng dụng định nghĩa và cần xử lý `IOException`. Vấn đề là `runAsync` trả về trước khi task hoàn thành. Nếu callback còn đang đọc khi khối try kết thúc, resource đã đóng quá sớm.

Cách đúng là để **task sở hữu việc mở/đóng** bên trong chính tác vụ, hoặc để owner **chờ task hoàn tất** trước khi đóng, có xử lý timeout/cancel/exception. Không đặt try-with-resources bên ngoài rồi phát task nền với resource bị ràng buộc vào scope ngắn hơn task.

### 12.5. Kết thúc xử lý và đóng nguồn là hai việc khác nhau

Một parser có thể dừng sau vài byte header; điều đó **không** cho phép nó đóng stream borrowed. Một reader đọc hết EOF vẫn cần owner quyết định khi đóng handle. Một file reader được đóng không có nghĩa xóa file trên filesystem. Ngược lại, file có thể vẫn tồn tại sau khi mọi handle đã đóng.

## 13. Kiểm thử quy tắc sở hữu

Đừng chỉ kiểm tra rằng dữ liệu được copy đúng. Kiểm thử cũng cần trả lời **ai gọi close và gọi bao nhiêu lần**.

- Với API borrowed, dùng stream bọc có biến `closeCalls`; gọi hàm rồi xác nhận **không bị đóng** khi hàm return, kể cả trường hợp exception. Demo `BorrowedCopyDemo` là ví dụ dễ quan sát.
- Với API owned, dùng fake `Closeable` hoặc bộ đếm close; bảo đảm resource được đóng cả khi thành công lẫn khi body ném lỗi.
- Với API trả resource, nhận stream rồi dùng try-with-resources ở caller; kiểm tra ví dụ/test documentation thể hiện nghĩa vụ của caller.
- Với wrapper, kiểm tra đóng outer có đóng delegate không, dựa trên contract từng lớp. Nếu delegate là resource borrowed, không “sửa” bằng cách đóng outer một cách vô thức.
- Với output, kiểm tra việc flush/close theo đúng thời điểm; test dữ liệu đã ghi là cần thiết nhưng không thay thế kiểm tra resource release.
- Nếu không có fake, có thể dùng file tạm và kiểm tra handle/resource phù hợp OS hoặc công cụ theo dõi file descriptors, nhưng không suy ra mọi OS xử lý file đang mở giống nhau.
- Kiểm tra đường exception và suppressed close errors khi release cũng có thể thất bại.

**Không cần test rằng mọi object Java đều phải bị close chính xác một lần.** Ví dụ `ByteArrayInputStream.close()` được tài liệu quy định không có tác dụng; điều quan trọng là **contract ownership** của API được tuân thủ và không tạo hành vi bất ngờ khi implementation đổi từ bộ nhớ sang tệp hoặc socket.

## 14. Checklist thiết kế API I/O trước khi công bố

| Câu hỏi | Quyết định cần ghi rõ |
| --- | --- |
| Resource được **tạo ở đâu**? | Hàm tự mở, caller mở, factory chuyển ownership hay framework quản lý? |
| Resource được **dùng trong bao lâu**? | Chỉ trong method, sau return, vòng đời của object, request, hay tác vụ async? |
| Ai có quyền **close**? | Callee, caller, callback boundary, framework hay owner cấp ứng dụng? |
| Method trả về dữ liệu hay **resource còn mở**? | `String/byte[]` đã đọc xong khác `InputStream/Stream<String>` lazy. |
| Có wrapper không? | `close()` lớp ngoài có lan tới nguồn bên trong không? |
| Method có **flush** không? | Nếu có, khi nào; không coi flush đồng nghĩa với close hoặc sync đĩa. |
| Khi exception xảy ra? | Ai vẫn đóng resource, lỗi close được báo thế nào, suppressed được giữ không? |
| Có shared access/thread khác dùng không? | Ai điều phối đóng, tránh đóng khi còn người đang đọc/ghi? |
| Resource được giao **ownership mới** như thế nào? | Factory return, constructor transfer, explicit close-on-completion hoặc callback scope? |
| Người gọi sẽ biết contract ở đâu? | Tên method, Javadoc, kiểu trả về, test và ví dụ gọi minh bạch. |

## 15. Những lỗi phổ biến trong code review

1. **“Ai tạo thì luôn phải đóng.”** Không hoàn toàn đúng: factory tạo rồi trả resource có thể chuyển ownership cho caller; framework có thể tạo resource vẫn giữ trách nhiệm dọn.
2. **“Method nhận `InputStream` thì cứ `try (in)`.”** Sai nếu input chỉ borrowed; try tự đóng stream được mượn.
3. **“Không thấy `close()` trong helper nghĩa là leak.”** Có thể owner ở caller; xem toàn bộ vòng đời và contract.
4. **“Đóng BufferedReader chỉ đóng buffer của nó.”** Sai với wrapper chuẩn: thường đóng luôn reader/stream bên trong.
5. **“Scanner đóng không ảnh hưởng System.in.”** Sai nếu Scanner được tạo từ `System.in`; đóng Scanner sẽ đóng nguồn `Closeable`.
6. **“`findFirst()`/`count()` làm `Files.lines` tự đóng.”** Sai: đóng `Stream` có resource bằng try-with-resources.
7. **“`transferTo` tự đóng input và output.”** Sai: phương thức copy không nhận ownership hai stream.
8. **“`flush()` là đủ, không cần close.”** Flush không kết thúc vòng đời hoặc giải phóng handle.
9. **“close không thể ném lỗi.”** Sai: `IOException` hoặc exception trong `AutoCloseable.close()` có thể xuất hiện lúc cleanup.
10. **“Khi body ném lỗi, close không chạy.”** Sai với try-with-resources; close vẫn diễn ra và lỗi phụ nằm trong `getSuppressed()`.
11. **“Dùng resource sau khi method callback return vẫn được.”** Sai nếu scope owner đã đóng tài nguyên; đây là lỗi lifetime escape.
12. **“Chạy async trong try, rồi return là ổn.”** Task có thể sống lâu hơn scope; resource bị đóng khi task còn dùng.
13. **“Cứ gọi close hai lần cho chắc.”** `Closeable` nói close lại không có hiệu lực, nhưng `AutoCloseable` rộng hơn không bắt buộc idempotent; cần một owner rõ ràng.
14. **“Đóng file đồng nghĩa xóa file.”** Hai thao tác độc lập, resource close và file deletion có vòng đời khác.
15. **“Bỏ qua lỗi đóng tệp vì thao tác write đã thành công.”** Close có thể là lúc buffer flush hoặc lỗi được phát hiện; phải xử lý theo mức bảo đảm dữ liệu yêu cầu.

## 16. Câu hỏi tự kiểm tra

1. Hàm `copy(InputStream in, OutputStream out)` do thư viện cung cấp, caller truyền vào socket output còn dùng sau đó. Ai nên đóng `out` và vì sao?
2. `Files.readString(path)` và `Files.lines(path)` khác nhau thế nào về ownership?
3. Khi bạn đóng `BufferedReader` được tạo từ `InputStreamReader(System.in)`, điều gì có thể xảy ra với `System.in`?
4. Một factory `openLog(Path)` trả `InputStream`. Javadoc nào phải có để caller không quên close?
5. Vì sao `try (InputStream in = callerInput)` bên trong method có thể vi phạm contract?
6. Nếu body ném exception và hai resource close cũng ném, chúng được báo ra sao?
7. Tại sao `new Scanner(System.in)` rồi close scanner sau một lần đọc có thể phá CLI còn câu hỏi tiếp theo?
8. Callback được mượn reader trong `withReader(path, callback)` có được giữ reader vào field để thread nền dùng sau không?
9. Nếu resource được mở ở main nhưng truyền vào một task async, ai phải quyết định thời điểm đóng?
10. Có nên sửa resource leak bằng cách đóng mọi `AutoCloseable` nhìn thấy trong code không? Hãy nêu hợp đồng nào cần xác nhận trước.

**Gợi ý đáp án:** (1) Caller/owner vì helper chỉ mượn stream; (2) readString tự quản lý handle, lines trả stream lazy caller phải close; (3) wrapper close có thể đóng stdin; (4) mô tả returned stream đang mở và caller phải đóng; (5) try sẽ close biến borrowed; (6) exception body là primary, lỗi close được suppressed và thứ tự close ngược khai báo; (7) scanner có thể đóng source; (8) không nếu owner đóng khi callback kết thúc; (9) owner đóng khi task đã xong hoặc task tự nhận quyền sở hữu; (10) không, phải biết ownership và wrapper propagation.

## 17. Tài liệu API chuẩn

- [Oracle Java SE 21 — Closeable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Closeable.html): close giải phóng tài nguyên I/O, contract về close lại và lỗi IOException.
- [Oracle Java SE 21 — AutoCloseable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/AutoCloseable.html): try-with-resources và lưu ý về close có thể ném exception.
- [Oracle Java SE 21 — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): phương thức tự đọc dữ liệu, phương thức trả stream còn mở, và yêu cầu đóng `Files.lines/list/walk`.
- [Oracle Java SE 21 — Scanner](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Scanner.html): đóng scanner cũng đóng nguồn đầu vào nếu nguồn implement `Closeable`.
- [Oracle Java SE 21 — InputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html): `close()`, `transferTo()` và phân biệt hành vi copy với đóng resource.
- [Oracle Java SE 21 — BufferedReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html) và [InputStreamReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStreamReader.html): quan hệ giữa wrapper và nguồn bên dưới.

**Ghi nhớ thực hành:** **“Ai mở, ai đóng” là câu hỏi về contract ownership, không phải về vị trí của từ khóa `new`.** Nếu bạn tạo resource để dùng nội bộ, hãy dùng try-with-resources và dọn nó trong scope. Nếu mượn resource từ caller, đừng tự đóng. Nếu trả resource còn mở, hãy chuyển nghĩa vụ đóng một cách tường minh. Với wrapper, callback và async task, luôn kiểm tra vòng đời thực của underlying resource.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-006">Đóng stream bọc, tài nguyên chia sẻ và tài nguyên do bên gọi truyền vào</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng stream bọc, tài nguyên chia sẻ và tài nguyên do bên gọi truyền vào

## 1. Vấn đề cốt lõi: ai có trách nhiệm đóng tài nguyên?

Trong Java I/O, ta thường viết một chuỗi các lớp bọc quanh tệp, socket hoặc một nguồn/đích khác:

```text
Ứng dụng
   │ đọc ký tự theo dòng
   ▼
BufferedReader
   │ đọc ký tự
   ▼
InputStreamReader (giải mã UTF-8)
   │ đọc byte
   ▼
FileInputStream / Files.newInputStream
   │
   ▼
Tệp do hệ điều hành quản lý
```

Mỗi lớp có một nhiệm vụ: `FileInputStream` đọc byte từ tệp; `InputStreamReader` chuyển byte thành ký tự; `BufferedReader` bổ sung đệm và `readLine()`. Nhưng những đối tượng này **không độc lập hoàn toàn về vòng đời**. Đóng lớp bọc ngoài thường sẽ đóng lớp bên trong, rồi cuối cùng giải phóng tệp hoặc kết nối gốc.

Câu hỏi quan trọng không đơn thuần là “stream có cần `close()` không?”, mà là:

- **Ai tạo và sở hữu** tài nguyên gốc?
- **Ai được phép kết thúc** việc sử dụng tài nguyên?
- Một wrapper **có chuyển tiếp `close()`** xuống tài nguyên bên dưới không?
- Nếu cùng tài nguyên được nhiều thành phần sử dụng, **thành phần nào được đóng nó**?
- Nếu một phương thức được truyền `InputStream`, `OutputStream`, `Reader` hoặc `Writer` từ bên gọi, hợp đồng của phương thức có **chuyển quyền sở hữu** hay chỉ **mượn để dùng tạm**?

Đối với một luồng gắn với tệp hoặc socket, quên đóng có thể rò rỉ tài nguyên hệ điều hành hoặc làm dữ liệu đệm chưa được hoàn tất. Ngược lại, đóng quá sớm một luồng chia sẻ có thể khiến các phần còn lại của ứng dụng không thể đọc/ghi tiếp.

**Nguyên tắc mặc định để thiết kế API:** bên **sở hữu tài nguyên** chịu trách nhiệm đóng; phương thức chỉ **mượn stream** thường đọc/ghi theo hợp đồng rồi trả về, không tự ý đóng. Ngoại lệ phải được ghi rõ trong tài liệu API.

## 2. Phân biệt resource, wrapper và ownership

Một *resource* là đối tượng có vòng đời cần kiểm soát, chẳng hạn handle tệp, kết nối mạng, vùng nhớ native hoặc cấu trúc I/O cần hoàn tất trước khi kết thúc. *Wrapper* là đối tượng bổ sung hành vi cho resource hoặc stream khác: buffering, chuyển byte/ký tự, giải nén, mã hóa, ghi kiểu dữ liệu v.v.

Ví dụ:

```java
InputStream raw = Files.newInputStream(path);
InputStream buffered = new BufferedInputStream(raw);
```

Đây là **đoạn minh họa**, cần `java.io.InputStream`, `BufferedInputStream`, `java.nio.file.Files`, `Path` và xử lý `IOException`. Nếu code tạo cả `raw` và `buffered`, code đó thường sở hữu luồng và phải đóng chúng theo một chiến lược an toàn. Với cách bọc phổ biến này, đóng `buffered` sẽ đóng `raw`.

Cùng một stream gốc có thể được coi theo các cách khác nhau:

| Tình huống | Ai sở hữu? | Ai đóng? |
| --- | --- | --- |
| Hàm tự mở `Files.newInputStream(path)` để xử lý tại chỗ | Hàm mở stream | Hàm đóng, thường bằng try-with-resources |
| Hàm nhận `InputStream in` từ caller chỉ để parse | Caller | Caller đóng; hàm parse không đóng nếu hợp đồng nói mượn |
| Factory mở file, trả một `BufferedReader` cho caller | Caller nhận quyền sở hữu thông qua đối tượng trả về | Caller đóng reader trả về |
| Ứng dụng dùng `System.in`, `System.out`, `System.err` | Runtime/ứng dụng cấp cao | Không đóng tùy tiện trong thư viện |
| Hai component cùng dùng một stream do ứng dụng quản lý | Thành phần điều phối cấp cao | Đóng khi mọi người dùng đã kết thúc |
| `GZIPOutputStream` do hàm tự tạo và sở hữu để sinh định dạng GZIP | Hàm hoặc caller nhận stream, tùy API | Chủ sở hữu phải hoàn tất `finish`/`close` đúng lúc |

**Một wrapper không tự động trở thành chủ sở hữu hợp pháp chỉ vì nó giữ tham chiếu đến stream bên dưới.** Nhưng nhiều wrapper chuẩn **có hành vi chuyển tiếp close**, nên nếu API chỉ được phép mượn tài nguyên, ta phải thiết kế để không vô ý gọi `close()` trên wrapper ấy.

## 3. Close lan từ ngoài vào trong như thế nào?

Nhiều wrapper của `java.io` tuân theo mô hình: khi đóng wrapper ngoài, nó flush/xử lý trạng thái của chính mình và đóng đối tượng delegate bên trong.

```text
close(BufferedOutputStream)
    ├─ flush dữ liệu byte đang đệm
    └─ close(OutputStream bên dưới)
          └─ giải phóng tài nguyên thật (nếu có)
```

Một số wrapper quen thuộc:

| Wrapper | Hành vi vòng đời cần chú ý |
| --- | --- |
| `BufferedInputStream` | `close()` đóng luồng đầu vào được bọc |
| `BufferedOutputStream` | `close()` có thể flush dữ liệu đệm rồi đóng luồng bên dưới |
| `InputStreamReader` | `close()` đóng luồng byte được bọc |
| `OutputStreamWriter` | `close()` hoàn tất việc ghi/mã hóa rồi đóng luồng bên dưới |
| `BufferedReader` / `BufferedWriter` | `close()` đóng reader/writer được bọc |
| `DataInputStream` / `DataOutputStream` | `close()` thường chuyển đến stream delegate |
| `GZIPInputStream` / `GZIPOutputStream` | `close()` kết thúc tài nguyên bọc; đầu ra GZIP cần hoàn tất cấu trúc nén |
| `PrintWriter` / `PrintStream` | Có vòng đời ghi/flush/close và cơ chế báo lỗi riêng, có thể đóng đích bên dưới |

Hành vi cụ thể phải dựa vào **API của lớp cụ thể** chứ không suy ra từ tên. Ví dụ, `ByteArrayInputStream` và `ByteArrayOutputStream` có `close()` không làm mất khả năng sử dụng; `StringReader.close()` lại khiến thao tác đọc sau đó không hợp lệ. Việc một test dùng memory stream vẫn chạy sau khi close **không chứng minh** cách đóng sẽ an toàn với file hoặc socket.

### Một chuỗi được sở hữu trọn vẹn: đóng lớp ngoài là đủ

```java
Path path = Path.of("data.txt");
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý.
    }
}
```

Đây là **đoạn minh họa**, cần import `java.io.BufferedReader`, `InputStreamReader`, `java.nio.file.Files`, `Path`, `StandardCharsets` và xử lý `IOException`. Khi reader được đóng, nó đóng `InputStreamReader`, rồi đến input stream của tệp. Không cần gọi `reader.close()` lần nữa ở `finally`.

Lưu ý về **ngoại lệ trong khi xây dựng chuỗi wrapper**: nếu một phép tạo wrapper có thể ném exception sau khi đã mở resource gốc nhưng trước khi wrapper trở thành tài nguyên của `try`, resource gốc cần được bảo vệ riêng. Một mẫu xây dựng an toàn hơn trong trường hợp constructor có rủi ro là khai báo resource gốc ngay trong danh sách `try`, dù việc đóng cả wrapper lẫn gốc lúc kết thúc có thể tạo lời gọi close lặp lại. Cần chọn cấu trúc phù hợp với API và xem xét tính an toàn khi đóng lặp.

## 4. Try-with-resources: phương thức khuyến nghị cho resource do mình sở hữu

`try-with-resources` (Java 7+, cải tiến cú pháp từ Java 9) gọi `close()` tự động cho từng biến resource implement `AutoCloseable` hoặc `Closeable`, kể cả khi khối xử lý ném exception.

```java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    in.transferTo(out);
}
// out.close() được gọi trước in.close().
```

Đây là **đoạn minh họa**, giả định có `Path source`, `Path target` và các import phù hợp. Vì `in` và `out` là hai tài nguyên **độc lập**, khai báo cả hai trong `try` là hợp lý. Khi có nhiều resource, Java đóng chúng theo **thứ tự ngược với thứ tự khai báo**.

Nếu một tài nguyên **bọc tài nguyên khác**, ví dụ `BufferedInputStream(raw)`, đặt cả `raw` lẫn wrapper ở danh sách `try` sẽ khiến luồng gốc có khả năng nhận **hai lời gọi close**:

```java
// raw được đưa trực tiếp vào wrapper; minh họa close kép.
try (InputStream raw = Files.newInputStream(source);
     InputStream buffered = new BufferedInputStream(raw)) {
    // ...
}
// buffered.close() sẽ đóng raw; sau đó try-with-resources đóng raw nữa.
```

Một số stream được thiết kế để đóng lặp mà không ảnh hưởng, nhưng **không thể giả định mọi `AutoCloseable` đều idempotent**. Nếu constructor wrapper không ném lỗi sau khi mở resource (như các constructor wrapper đơn giản với đối số hợp lệ), một lựa chọn gọn là khai báo **chỉ wrapper ngoài** làm tài nguyên ở `try`. Nếu cần bảo vệ nhiều giai đoạn khởi tạo có thể thất bại, bảo vệ resource từ lúc mở và xử lý close nhiều lần theo hợp đồng cụ thể.

### Lỗi trong phần xử lý và lỗi lúc close

`try-with-resources` giữ ngoại lệ chính ném từ thân `try`; lỗi phát sinh khi đóng resource được gắn dưới dạng **suppressed exception**. Đây là khác biệt lớn so với `finally` tự viết không cẩn thận, nơi exception từ `close()` có thể che mất lỗi gốc.

```java
try (InputStream in = Files.newInputStream(source)) {
    // Nếu phần xử lý ném exception...
} // ...và close cũng lỗi, lỗi close được gắn là suppressed.
```

Vì tài nguyên có thể lỗi cả trong khi dùng lẫn khi đóng, thông tin suppressed exception rất hữu ích để chẩn đoán. Không nên bắt `IOException` rồi bỏ qua chỉ vì code đang “dọn dẹp”.

## 5. Ví dụ chạy được: xác minh wrapper đóng stream bên dưới

Ví dụ dùng `ByteArrayOutputStream` được mở rộng để ghi nhận số lần gọi `close()`. Điều này cho phép kiểm tra hành vi đóng mà không cần tệp thực.

**Tệp `WrapperCloseDemo.java`:**

```java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WrapperCloseDemo {
    static final class TrackingOutput extends ByteArrayOutputStream {
        private int closes;

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }

        int closeCount() {
            return closes;
        }
    }

    public static void main(String[] args) throws IOException {
        TrackingOutput underlying = new TrackingOutput();

        try (BufferedOutputStream buffered = new BufferedOutputStream(underlying)) {
            buffered.write("ABC".getBytes(StandardCharsets.US_ASCII));
            System.out.println("Before close, bytes: " + underlying.size());
        }

        System.out.println("After close, text: "
                + underlying.toString(StandardCharsets.US_ASCII));
        System.out.println("Underlying close calls: "
                + underlying.closeCount());
    }
}
```

Lệnh với JDK 17+:

```bash
javac WrapperCloseDemo.java
java WrapperCloseDemo
```

**Stdout chính xác:**

```text
Before close, bytes: 0
After close, text: ABC
Underlying close calls: 1
```

`BufferedOutputStream` có buffer mặc định đủ chứa ba byte, nên trước khi đóng, `underlying` chưa nhận byte nào. Khi `try` kết thúc, wrapper được đóng, làm các byte đệm được ghi xuống `underlying`, rồi gọi `close()` của nó đúng một lần trong ví dụ. Ta vẫn đọc được nội dung `ByteArrayOutputStream` sau close vì lớp bộ nhớ này cho phép như vậy; không nên suy ra rằng **mọi** output stream cho phép truy cập dữ liệu sau khi đóng.

**Ý nghĩa quan trọng:** nếu `underlying` là một socket hay file stream do caller truyền vào và còn cần sử dụng, việc đóng `BufferedOutputStream` trên nó có thể cắt đứt công việc của caller.

## 6. Resource do caller truyền vào: mượn hay nhận quyền sở hữu?

Trong thư viện Java, một phương thức như sau rất thường gặp:

```java
static void encode(Data record, OutputStream output) throws IOException {
    // Ghi record vào output...
}
```

Đây là **chữ ký minh họa**; `Data` đại diện kiểu dữ liệu ứng dụng chưa định nghĩa. Chữ ký **không tự cho biết** ai phải đóng `output`. Cần công bố hợp đồng:

- **Borrowed input/output:** caller tiếp tục sở hữu; hàm không đóng stream. Nếu hàm tự tạo wrapper để dùng tạm thì cũng không được đóng tài nguyên bên dưới ngoài ý muốn.
- **Transferred ownership:** caller bàn giao quyền sở hữu; hàm được phép đóng stream và phải tài liệu hóa rõ, kể cả khi có ngoại lệ.
- **Resource returned by factory:** hàm tạo stream và trả về một `Closeable` mới, caller nhận trách nhiệm đóng khi dùng xong.
- **Shared resource:** nhiều thành phần cùng sử dụng; một thành phần điều phối quy định thời điểm đóng, không để từng consumer đóng tùy ý.

Quy ước **mượn** đặc biệt hữu ích khi một API nhận `InputStream`/`OutputStream`, vì caller có thể ghép nhiều thao tác trên cùng stream:

```text
Caller tạo output
  ├─ writeHeader(output)
  ├─ writeRecords(output)
  ├─ writeFooter(output)
  └─ caller flush/close(output) sau cùng
```

Nếu `writeRecords(output)` đóng output khi xong, `writeFooter(output)` có thể thất bại. Đó là lỗi **ownership**, ngay cả khi mỗi đoạn code chạy riêng lẻ vẫn đúng.

### Một API mượn OutputStream nên làm gì?

- Ghi dữ liệu đúng định dạng, xử lý `IOException` theo hợp đồng.
- Không gọi `output.close()` trừ khi nhận chuyển quyền sở hữu một cách rõ ràng.
- Thống nhất có `flush()` hay không. Thường caller sở hữu tài nguyên sẽ quyết định thời điểm flush; một giao thức tương tác có thể yêu cầu flush sau mỗi thông điệp.
- Nếu dùng `BufferedOutputStream` hay `OutputStreamWriter` bọc output mượn, cần flush dữ liệu đã ghi trước khi trả về mà **không đóng delegate**.
- Nếu wrapper sử dụng bộ mã hóa hoặc định dạng đòi “kết thúc luồng” mới hoàn tất (GZIP, ZIP, một số mã hóa có trạng thái), không thể tùy tiện thay close bằng flush. Cần dùng API `finish()` khi được hỗ trợ hoặc thiết kế lại quyền sở hữu.

### Một API mượn InputStream nên làm gì?

- Đọc theo số byte cần thiết, hoặc đến EOF nếu hợp đồng nói vậy.
- Không tự đóng input nếu caller còn sử dụng.
- Cân nhắc buffering và vị trí stream khi trả về: wrapper có thể **đọc trước** nhiều byte vào buffer, khiến caller đọc từ stream gốc sau đó không thấy dữ liệu theo cách dự đoán.
- Nếu cần phối hợp nhiều parser/consumer trên một stream, thống nhất framing, giới hạn đọc và việc chia sẻ **chính cùng một wrapper** thay vì tạo nhiều wrapper đọc trước độc lập.

**Đọc trước và đóng stream là hai vấn đề khác nhau:** ngay cả khi không gọi close, một `BufferedReader` riêng có thể giữ những byte/ký tự đã nạp trước trong buffer. Caller không nên lập tức tiếp tục với stream gốc nếu không có thiết kế về bộ đệm/vị trí.

## 7. Ví dụ chạy được: hàm mượn stream không được đóng nó

Chương trình sau chứng minh code xử lý chỉ ghi dữ liệu và để caller đóng. `TrackingOutput` ghi nhận nếu một lệnh `close()` đã xảy ra.

**Tệp `BorrowedStreamDemo.java`:**

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BorrowedStreamDemo {
    static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }

    // Hợp đồng: borrowed OutputStream; method không close và không flush.
    static void writePart(OutputStream output, String value)
            throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        TrackingOutput shared = new TrackingOutput();

        try {
            writePart(shared, "A");
            writePart(shared, "B");

            System.out.println("Closed before caller: " + shared.isClosed());
            System.out.println("Accumulated: "
                    + shared.toString(StandardCharsets.UTF_8));
        } finally {
            shared.close(); // Caller là owner.
        }

        System.out.println("Closed by caller: " + shared.isClosed());
    }
}
```

Biên dịch/chạy:

```bash
javac BorrowedStreamDemo.java
java BorrowedStreamDemo
```

**Stdout chính xác:**

```text
Closed before caller: false
Accumulated: AB
Closed by caller: true
```

Điểm cần nhìn là **caller gọi `writePart` hai lần trên cùng đích**, và chỉ caller đóng đích sau cùng. Nếu `writePart` tự ý đóng một file stream thật, lần gọi tiếp theo có thể không còn ghi được. `ByteArrayOutputStream` vốn cho phép ghi sau close, nên ví dụ dùng cờ theo dõi để không dựa vào tính chất đó.

Khi hàm cần trao đổi nhiều phần dữ liệu trên một kết nối, ownership càng quan trọng: một parser riêng lẻ không được ngắt socket ngay sau khi xử lý xong message nếu giao thức còn dùng kết nối cho message tiếp theo.

## 8. Khi wrapper tự tạo lại đóng tài nguyên mượn: giải pháp nào?

Đây là một lỗi dễ gặp:

```java
// Mã minh họa NGUY HIỂM nếu output do caller sở hữu:
static void badWrite(OutputStream output) throws IOException {
    try (BufferedOutputStream buffered = new BufferedOutputStream(output)) {
        buffered.write(42);
    } // buffered.close() đóng cả output do caller truyền vào.
}
```

`BufferedOutputStream` là wrapper được tạo trong hàm nhưng delegate lại thuộc caller. `try-with-resources` trên wrapper có vẻ hợp lý ở bề mặt, nhưng vi phạm hợp đồng mượn.

Có ba hướng thiết kế tùy mục đích:

**Cách 1 — Không tạo wrapper trong hàm.** Để caller tạo một buffered stream và truyền sẵn:

```java
static void writeBytes(OutputStream output, byte[] bytes)
        throws IOException {
    output.write(bytes);
}
```

Caller quyết định khi nào flush/close; hàm dùng trực tiếp stream được cho. Cách này thường đơn giản nhất.

**Cách 2 — Tạo wrapper tạm và flush, nhưng không close.** Ví dụ:

```java
static void writeBuffered(OutputStream borrowed, byte[] bytes)
        throws IOException {
    BufferedOutputStream buffered = new BufferedOutputStream(borrowed);
    buffered.write(bytes);
    buffered.flush();
    // Không close buffered vì close sẽ đóng borrowed.
}
```

Chỉ sử dụng mẫu này khi **không cần bước finish/close để hoàn tất định dạng** và không giữ lại wrapper cho các lần gọi sau. Nếu caller kiểm soát cả vòng đời, tốt hơn là caller giữ một wrapper lâu dài để tránh tạo nhiều lớp đệm và tránh việc mỗi lời gọi flush không cần thiết.

**Cách 3 — Dùng wrapper có chính sách “không đóng delegate”** nếu phải đóng/hoàn tất wrapper ở phạm vi cục bộ. Có thể tạo `FilterOutputStream` override `close()` để chỉ flush mà không chuyển tiếp close, rồi bọc nó bằng wrapper cần đóng:

```java
final class NonClosingOutputStream extends FilterOutputStream {
    NonClosingOutputStream(OutputStream delegate) {
        super(delegate);
    }

    @Override
    public void close() throws IOException {
        flush(); // Cố ý không gọi super.close().
    }
}
```

Đây là **class minh họa**; cần import `java.io.FilterOutputStream`, `OutputStream`, `IOException`. Nó bảo vệ **việc close delegate** khi wrapper bên ngoài gọi close. Tuy nhiên:

- Nó **không ngăn code khác giữ tham chiếu đến delegate rồi tự đóng delegate**.
- Nó không biến một tài nguyên có quyền sở hữu khác trở thành thread-safe.
- Nó không thay thế yêu cầu quản lý buffer/encoding/định dạng của wrapper.
- Nếu wrapper khác đã sử dụng riêng resource gốc trước đó, vấn đề thứ tự/đọc trước vẫn tồn tại.
- Một số thư viện cung cấp sẵn các biến thể non-closing; cần hiểu chính xác hợp đồng của implementation được chọn.

### Ví dụ Java 17 hoàn chỉnh: đóng BufferedOutputStream mà vẫn giữ delegate

**Tệp `NonClosingWrapperDemo.java`:**

```java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class NonClosingWrapperDemo {
    static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean wasClosed() {
            return closed;
        }
    }

    static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream target) {
            super(target);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    static void writeBorrowed(OutputStream borrowed) throws IOException {
        try (BufferedOutputStream buffered =
                     new BufferedOutputStream(
                             new NonClosingOutputStream(borrowed))) {
            buffered.write("Hello".getBytes(StandardCharsets.US_ASCII));
        }
    }

    public static void main(String[] args) throws IOException {
        TrackingOutput shared = new TrackingOutput();
        try {
            writeBorrowed(shared);
            System.out.println("Closed after wrapper: " + shared.wasClosed());

            shared.write('!');
            System.out.println("Content: "
                    + shared.toString(StandardCharsets.US_ASCII));
        } finally {
            shared.close();
        }
        System.out.println("Closed by caller: " + shared.wasClosed());
    }
}
```

Chạy:

```bash
javac NonClosingWrapperDemo.java
java NonClosingWrapperDemo
```

**Stdout chính xác:**

```text
Closed after wrapper: false
Content: Hello!
Closed by caller: true
```

Ở đây `BufferedOutputStream.close()` đẩy byte xuống `NonClosingOutputStream`, rồi gọi `close()` của lớp bảo vệ. `NonClosingOutputStream.close()` chỉ `flush()`, không đóng stream `shared`. Sau khi `writeBorrowed` trả về, caller tiếp tục ghi ký tự `!` và cuối cùng tự đóng resource. Đây là cách kiểm thử trực tiếp **ownership không bị chuyển vô tình**.

## 9. Tài nguyên chia sẻ: System.in, System.out, System.err

`System.in` là `InputStream`; `System.out` và `System.err` là `PrintStream` do môi trường JVM cung cấp. Chúng thường được dùng chung bởi nhiều đoạn code trong cùng tiến trình.

Một lỗi phổ biến:

```java
// Mã minh họa: nếu chương trình còn dùng stdin, không nên làm thế.
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
    System.out.println(reader.readLine());
} // Đóng reader => InputStreamReader => System.in.
```

Nếu những phần khác cần tiếp tục đọc `System.in`, chúng có thể không đọc được nữa. Tương tự, đóng một `PrintWriter` bọc `System.out` có thể đóng stdout, khiến `System.out.println` sau đó không hiển thị như mong đợi. `PrintStream` có thể ghi trạng thái lỗi thay vì ném `IOException` ra người gọi, nên lỗi dễ bị bỏ qua.

**Chiến lược hợp lý:** ứng dụng cấp cao quyết định vòng đời các luồng chuẩn; hàm thư viện chỉ mượn chúng. Nếu tạo wrapper để đọc stdin liên tục, giữ wrapper ở một chỗ và chuyển cho các component cần dùng theo hợp đồng. Tránh nhiều `Scanner`/`BufferedReader` độc lập cùng tiêu thụ stdin, vì buffering có thể làm mỗi component đọc trước và cản trở nhau.

### Scanner cũng có vấn đề quyền sở hữu

```java
Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
// ...
scanner.close(); // Đồng thời đóng System.in bên dưới.
```

Đây là đoạn minh họa, cần `java.util.Scanner` và `StandardCharsets`. `Scanner` implements `Closeable`; đóng Scanner sẽ đóng nguồn nếu nguồn implement `Closeable`. Dùng `try-with-resources` với Scanner trên **file stream do mình sở hữu** thì đúng, nhưng dùng trên `System.in` cần xem xét vòng đời chương trình.

### PrintWriter, PrintStream và lỗi khi đóng/ghi

`PrintWriter` và `PrintStream` cung cấp API `print`/`println` thuận tiện, nhưng thường **không ném checked IOException trong các phương thức in**. Chúng ghi nhận lỗi I/O, có thể được kiểm tra qua `checkError()`. Vì vậy ngay cả khi đã tuân thủ ownership, nếu kết quả đầu ra quan trọng, code cần chiến lược phát hiện lỗi thích hợp; `close()` trong `try-with-resources` của các lớp này không nhất thiết bộc lộ lỗi như các `Writer` khác.

Với output phải đảm bảo báo lỗi `IOException`, dùng `OutputStream`/`Writer` phù hợp (và kiểm tra lỗi trong quá trình ghi/flush/close) có thể rõ ràng hơn `PrintWriter` cho các nhu cầu nghiêm ngặt.

## 10. Shared resource không phải tự động an toàn để nhiều thread dùng chung

Có hai vấn đề độc lập:

1. **Lifecycle safety:** ai được đóng và vào thời điểm nào?
2. **Concurrency safety:** nếu hai thread cùng đọc/ghi một stream, dữ liệu và thứ tự có được giữ đúng giao thức không?

Việc không đóng một stream mượn giải quyết phần lớn rủi ro lifecycle, nhưng **không đảm bảo** nhiều thread cùng ghi sẽ không xen kẽ byte, nhiều reader cùng đọc sẽ không tranh dữ liệu, hoặc wrapper có buffer sẽ xử lý đồng thời an toàn.

Ví dụ, hai thành phần dùng chung một `OutputStream` để xuất JSON; nếu một thành phần ghi header và phần kia chen thông điệp vào giữa, dữ liệu có thể hỏng dù không ai gọi `close()`. Cần đồng bộ/gom toàn bộ một message ở cấp protocol, hoặc đưa việc ghi vào một thành phần điều phối.

Khi nhiều component cần dùng chung resource:

- Quy định **chủ sở hữu duy nhất** để đóng.
- Quy định **phạm vi mượn** và thời điểm không còn consumer hoạt động.
- Thiết kế giao thức đọc/ghi rõ framing và thứ tự.
- Không để mỗi consumer tự tạo wrapper buffering độc lập nếu chúng cùng thao tác một vị trí stream.
- Nếu có xử lý bất đồng bộ, đảm bảo resource còn sống đến khi tất cả tác vụ đã hoàn tất.

Trong các kiến trúc phức tạp, có thể dùng một **owner/coordinator** hoặc một abstraction chỉ cung cấp thao tác cần thiết, thay vì đưa thẳng stream cho nhiều nơi rồi trông cậy từng nơi không đóng.

## 11. Ví dụ lỗi close: suppressed exception trong try-with-resources

Một stream có thể thất bại khi đọc/ghi, đồng thời `close()` cũng có thể thất bại. Java giữ lỗi chính và gắn lỗi close thành suppressed.

**Tệp `SuppressedCloseDemo.java`:**

```java
import java.io.IOException;

public class SuppressedCloseDemo {
    static final class FaultyResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FaultyResource resource = new FaultyResource()) {
            throw new IOException("work failed");
        } catch (IOException failure) {
            System.out.println("Main failure: " + failure.getMessage());
            System.out.println(
                    "Suppressed count: " + failure.getSuppressed().length);
            for (Throwable suppressed : failure.getSuppressed()) {
                System.out.println(
                        "Suppressed: " + suppressed.getMessage());
            }
        }
    }
}
```

Lệnh:

```bash
javac SuppressedCloseDemo.java
java SuppressedCloseDemo
```

**Stdout chính xác:**

```text
Main failure: work failed
Suppressed count: 1
Suppressed: close failed
```

Nếu không có lỗi trong thân `try`, nhưng `close()` ném lỗi, lỗi close sẽ là lỗi chính. Nếu có nhiều tài nguyên và nhiều lần đóng lỗi, Java có thể giữ nhiều suppressed exception; thứ tự đóng vẫn là **ngược thứ tự khai báo**.

Điểm thực hành là **không che khuất lỗi gốc**. Tránh mẫu thủ công sau nếu không xử lý ngoại lệ đúng:

```java
// Mã minh họa NGUY HIỂM: exception từ close có thể che exception ban đầu.
try {
    doWork();
} finally {
    resource.close();
}
```

`doWork()` và `resource` ở đây là placeholder khái niệm trong mẫu giải thích, không phải chương trình độc lập. Khi có thể, ưu tiên `try-with-resources`; khi cần xử lý ngoại lệ phức tạp, giữ lại nguyên nhân gốc và suppressed thay vì bỏ qua.

## 12. Flush, finish và close: ba hành động không đồng nghĩa

**`flush()`** yêu cầu dữ liệu đang đệm của một luồng ghi được đẩy xuống tầng dưới. Nó thường không đóng luồng và không cam kết byte đã bền vững trên ổ đĩa.

**`finish()`** tồn tại ở một số wrapper định dạng, như `GZIPOutputStream` hoặc `ZipOutputStream`, nhằm hoàn tất cấu trúc dữ liệu đã tạo **mà không đóng underlying stream**, theo hợp đồng implementation. Nó không có trong `OutputStream` chung.

**`close()`** kết thúc vòng đời của resource/wrapper; wrapper ngoài thường flush/finish phần còn lại rồi đóng delegate.

Ví dụ về GZIP:

```java
// Đoạn minh họa, output do caller sở hữu, GZIP wrapper là cục bộ.
GZIPOutputStream gzip = new GZIPOutputStream(output);
gzip.write(payload);
gzip.finish(); // Hoàn tất dữ liệu GZIP.
gzip.flush();  // Nếu cần đẩy dữ liệu xuống output.
                // Không gọi gzip.close() nếu phải giữ output mở.
```

Đây là **đoạn minh họa**, cần `GZIPOutputStream`, `OutputStream output`, `byte[] payload` và xử lý `IOException`. `finish()` có thể hoàn tất GZIP member mà không đóng delegate, nhưng thiết kế cần xác định có dùng wrapper tiếp không và ai giải phóng wrapper. Không nên áp dụng tùy tiện cách này cho mọi định dạng; wrapper khác có thể **không có `finish()`** hoặc cần close để hoàn tất.

**Lưu ý:** ngay cả khi giữ underlying output mở, dữ liệu có thể chưa hiển thị tại đích cho đến khi flush. Ngược lại, flush không bảo đảm tính bền vững trên đĩa; một yêu cầu thật sự về durability cần cơ chế đồng bộ file thích hợp.

## 13. Trả stream từ một factory: chuyển quyền sở hữu cho caller

Một helper có thể tạo và trả lại luồng để code gọi dùng:

```java
static BufferedReader openUtf8Reader(Path path) throws IOException {
    return new BufferedReader(
            new InputStreamReader(
                    Files.newInputStream(path), StandardCharsets.UTF_8));
}
```

Đây là **phương thức minh họa**, cần import tương ứng. Nó mở luồng file và trả wrapper ngoài. Vì factory **chuyển quyền sở hữu qua giá trị trả về**, caller chịu trách nhiệm đóng:

```java
try (BufferedReader reader = openUtf8Reader(path)) {
    // Dùng reader.
}
```

Nếu caller quên đóng thì luồng file có thể bị rò rỉ. Trái lại, **không nên** làm:

```java
// Mã minh họa sai:
static BufferedReader brokenFactory(Path path) throws IOException {
    try (BufferedReader reader = openUtf8Reader(path)) {
        return reader; // Trả một reader đã bị đóng khi rời try.
    }
}
```

Ví dụ này thể hiện một lỗi vòng đời: trả đối tượng còn tham chiếu nhưng tài nguyên đã đóng. Nếu muốn đảm bảo caller không cần đóng, đừng trả về resource sống; thay vào đó có thể đọc hết dữ liệu trong phạm vi có giới hạn rồi trả một **giá trị bất biến** hoặc giá trị sở hữu dữ liệu, chấp nhận đánh đổi về bộ nhớ.

### Cách tránh nhầm quyền sở hữu trong API

Có thể đặt tên làm rõ ý nghĩa như `openReader()`, `withReader(...)` hoặc ghi Javadoc:

```java
/**
 * Reads records from a borrowed InputStream.
 * This method does not close the supplied stream.
 */
static void parse(InputStream borrowed) throws IOException {
    // ...
}
```

Đây là **khung tài liệu API**, thân phương thức được lược bỏ để minh họa hợp đồng. Nếu một API thực sự đóng stream truyền vào, ghi rõ “takes ownership and closes the stream” và xác định hành vi khi lỗi. Đừng chỉ dựa vào tên phương thức để người gọi đoán.

## 14. Thiết kế API bằng callback để đóng tài nguyên đúng phạm vi

Trong một số trường hợp, ta không cần trả resource sống cho caller. Có thể sở hữu việc mở/đóng ngay tại biên và nhận callback xử lý:

```java
@FunctionalInterface
interface ReaderAction<T> {
    T apply(BufferedReader reader) throws IOException;
}

static <T> T withUtf8Reader(Path path, ReaderAction<T> action)
        throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(
            path, StandardCharsets.UTF_8)) {
        return action.apply(reader);
    }
}
```

Đây là **đoạn minh họa**, cần import `BufferedReader`, `IOException`, `Files`, `Path`, `StandardCharsets`. Phương thức `withUtf8Reader` sở hữu stream và đảm bảo close trước khi trả kết quả. Callback chỉ **mượn reader trong phạm vi chạy**, không nên giữ tham chiếu rồi dùng sau khi callback kết thúc.

Ví dụ ngữ nghĩa:

```java
long count = withUtf8Reader(path, reader ->
        reader.lines().count());
```

Với `BufferedReader.lines()`, stream dòng phụ thuộc reader bên dưới; terminal operation `count()` thực thi **trong callback** nên khi callback kết thúc, toàn bộ việc đọc đã xong trước lúc reader đóng. Tránh trả `Stream<String>` lazy từ callback rồi mong đọc sau, vì reader lúc đó đã đóng.

Mẫu “with resource” giúp ràng buộc vòng đời trong API, nhưng chỉ phù hợp với xử lý đồng bộ trong phạm vi callback. Với tác vụ bất đồng bộ, cần mô hình quản lý ownership và completion phù hợp.

## 15. Stream<String> từ Files.lines và vấn đề đóng tài nguyên gián tiếp

Tên `Stream` trong Java có thể chỉ:

1. **I/O streams** trong `java.io`, ví dụ `InputStream`/`OutputStream`.
2. **Stream API** trong `java.util.stream`, ví dụ `Stream<String>`.

Hai khái niệm khác nhau. Tuy nhiên một số Stream API **được liên kết với resource I/O**:

```java
try (Stream<String> lines =
             Files.lines(path, StandardCharsets.UTF_8)) {
    long count = lines.filter(s -> !s.isBlank()).count();
    System.out.println(count);
}
```

Đây là **đoạn minh họa**, cần import `java.util.stream.Stream`, `java.nio.file.Files`, `Path`, `StandardCharsets`, xử lý `IOException`. `Files.lines` mở resource liên quan đến tệp và trả một stream lazy có `onClose` thích hợp. **Terminal operation như `count()` không mặc định đóng Stream API**; `try-with-resources` mới bảo đảm close khi xong.

Ngược lại, không phải mọi `Stream<T>` đều có tài nguyên hệ điều hành cần đóng, ví dụ `List.of("a", "b").stream()`. Đừng hiểu lầm rằng mọi đối tượng Stream API đều giống `FileInputStream`; cần biết nó được tạo từ đâu và quy định đóng của API tương ứng.

## 16. Nếu thực sự cần chia sẻ một stream cho nhiều consumer

Có thể thiết kế một lớp owner duy nhất, mọi consumer nhận một **giao diện hẹp** thay vì quyền truy cập đến `close()`.

Ví dụ khái niệm:

```java
interface MessageSink {
    void send(String message) throws IOException;
}
```

Chủ sở hữu tạo một `OutputStream` và cung cấp `MessageSink` cho các consumer. Chỉ chủ sở hữu giữ quyền đóng output. Interface không hề công bố phương thức `close()` nên giảm khả năng consumer tự đóng tài nguyên. Owner có thể đảm bảo đồng bộ, framing và flush trong một chỗ.

Với tài nguyên có nhiều người dùng bất đồng bộ, cần cơ chế biết khi nào người dùng cuối cùng đã xong: ví dụ lifecycle scope, reference counting được thiết kế cẩn thận, hoặc cơ chế shutdown của ứng dụng. **Không được đoán rằng một thread kết thúc có nghĩa không thread nào còn dùng stream**.

Nếu API buộc phải nhận một `Closeable` nhưng consumer không được đóng delegate, cân nhắc non-closing wrapper ở biên hoặc hợp đồng borrowed rõ ràng. Non-closing wrapper chỉ bảo vệ hành động close đi qua wrapper, **không bảo vệ chống ghi/đọc sai thứ tự**.

## 17. Những lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Hướng sửa |
| --- | --- | --- |
| `try (BufferedReader r = ... System.in ...)` trong từng hàm | Đóng stdin sau khi hàm xong, code sau không đọc được | Caller/app sở hữu stdin; giữ wrapper dùng chung hoặc không đóng khi chỉ mượn |
| `try (BufferedOutputStream b = new BufferedOutputStream(callerOut))` | Đóng output của caller ngoài ý muốn | Truyền wrapper từ caller hoặc dùng non-closing delegate/flush phù hợp |
| Mỗi parser tự tạo `BufferedReader` trên cùng input | Buffer có thể đọc trước dữ liệu dành cho parser khác | Dùng một wrapper phối hợp chung hoặc thiết kế framing rõ ràng |
| Đặt cả `raw` và wrapper vào try mà không suy nghĩ | Có thể gọi close lặp với delegate | Hiểu chuỗi ownership và hành vi close của từng lớp |
| Trả reader từ trong `try-with-resources` | Trả đối tượng đã đóng | Factory chuyển ownership, caller đóng |
| Quên đóng stream được factory trả về | Rò rỉ file/socket hoặc tài nguyên liên quan | Caller dùng `try-with-resources` |
| `close()` trong `finally` che lỗi ở thân try | Mất dấu nguyên nhân chính | Dùng `try-with-resources`, xem suppressed |
| Coi `flush()` thay cho `close()` ở GZIP | Thiếu trailer/dữ liệu định dạng cuối | Dùng `finish()`/ `close()` theo API |
| Đóng một resource chia sẻ trong một worker | Consumer khác bị lỗi | Owner cấp cao quyết định thời điểm đóng |
| Cho rằng một `ByteArrayOutputStream` vẫn dùng được sau close thì file cũng vậy | Unit test che mất lỗi lifecycle | Test bằng tracking stream/fake close |
| Dùng `PrintWriter.println` mà nghĩ sẽ nhận checked `IOException` | Lỗi ghi có thể bị ghi nhận bên trong | Xem `checkError()` hoặc chọn API báo lỗi rõ |
| Đóng Scanner trên `System.in` dù còn chức năng nhập khác | Stdin đóng theo Scanner | Giữ scanner trong vòng đời ứng dụng hoặc ghi rõ ownership |
| Đọc các dòng từ `Files.lines` mà không close | Resource file có thể giữ mở | `try (Stream<String> lines = Files.lines(...))` |
| Trả Stream API lazy phụ thuộc reader đã đóng | Lỗi khi dùng ở ngoài scope | Thực thi terminal operation trong scope hoặc chuyển quyền sở hữu rõ |
| Nhiều thread cùng dùng output và không ai đóng | Vẫn có thể xen kẽ byte và hỏng protocol | Điều phối thứ tự/đồng bộ, framing; close sau khi các tác vụ xong |

## 18. Checklist khi viết hoặc review một API nhận stream

Trước khi quyết định `try-with-resources`, xác định:

1. **Nguồn gốc:** stream được hàm mở, caller truyền vào hay lấy từ singleton/global?
2. **Chủ sở hữu:** thành phần nào có quyền kết thúc resource thật?
3. **Wrapper:** đóng wrapper nào sẽ đóng delegate nào? Có ngoại lệ như memory stream?
4. **Người dùng sau đó:** có hàm, task hoặc thread nào còn cần stream sau khi thao tác hiện tại kết thúc?
5. **Buffer và vị trí:** wrapper có đọc trước, giữ byte/ký tự chưa xử lý, hoặc ghi đệm chưa flush?
6. **Giao thức kết thúc:** format có yêu cầu `finish()`, footer/trailer hoặc stateful encoder trước khi đóng không?
7. **Lỗi:** lỗi close có cần được báo ra? Nếu nhiều lỗi, suppressed exception được giữ thế nào?
8. **API contract:** Javadoc có nêu borrowed/takes ownership/returns owned closeable không?
9. **Bằng chứng kiểm thử:** có test stream theo dõi close, lỗi giả lập và trường hợp dùng tiếp sau khi hàm trả về?
10. **Cấu trúc thực thi:** dùng đồng bộ hay bất đồng bộ; ai bảo đảm resource còn sống đến cuối tác vụ?

Nếu hàm chỉ cần thực hiện một phép biến đổi ngắn trên stream do caller truyền vào, hợp đồng **mượn và không đóng** thường hợp lý. Nếu hàm tự mở stream từ tệp, `try-with-resources` thường là cách an toàn nhất. Nếu luồng được trả từ factory, hãy ghi rõ caller là owner.

## 19. Bài tập thực hành

1. Chạy `WrapperCloseDemo`; giải thích vì sao trước khi đóng số byte bằng 0 và sau khi đóng nội dung trở thành `ABC`.
2. Thay `ByteArrayOutputStream` trong ví dụ bằng file stream tự tạo. Quan sát tác động của `close()` và vì sao không nên tiếp tục ghi sau close.
3. Trong `BorrowedStreamDemo`, thay `writePart` để tự đóng output, rồi dùng tracking stream phát hiện vi phạm ownership (không dựa vào việc `ByteArrayOutputStream` có thể ghi sau close).
4. Chạy `NonClosingWrapperDemo`; bỏ override `close()` của `NonClosingOutputStream` để xem khác biệt về số lần đóng delegate.
5. Chạy `SuppressedCloseDemo`; lần lượt bỏ lỗi từ thân try và bỏ lỗi từ `close()`. Xác định exception nào trở thành lỗi chính.
6. Viết một hàm `copy(InputStream, OutputStream)` nhận hai stream borrowed, không đóng, chỉ ghi `count` byte đọc thực tế. Dùng hai fixture có cờ `closed` để đảm bảo caller vẫn dùng tiếp được.
7. Tạo hai `BufferedReader` trên cùng `InputStream` chứa nhiều dòng và lý giải vì sao đọc trước khiến chia sẻ hai reader trở nên khó kiểm soát. Thiết kế lại để chỉ có một reader owner.
8. Viết factory `openUtf8Reader` rồi sử dụng bằng `try-with-resources`; thêm trường hợp mở tệp không tồn tại và kiểm tra không để resource bị treo.
9. Dùng `Files.lines` với một tệp nhỏ và `try-with-resources`, sau đó viết một callback `withUtf8Reader` thực hiện đọc trong phạm vi còn mở.
10. Thiết kế Javadoc cho hai phương thức: một phương thức **borrows an OutputStream**, phương thức còn lại **takes ownership**. Làm rõ hành vi khi ném `IOException`.
11. Mô tả cách xử lý khi một task bất đồng bộ đang ghi nhưng ứng dụng nhận yêu cầu shutdown: ai chờ hoàn tất, ai flush/close, và làm sao tránh đóng giữa message.
12. Với một output stream do caller sở hữu và định dạng GZIP cần hoàn tất, chọn giữa `finish()`, non-closing wrapper và việc chuyển quyền sở hữu; giải thích hợp đồng được chọn.

## 20. Tổng kết

Quản lý tài nguyên trong Java I/O cần đồng thời hiểu **vòng đời thực của stream**, **quan hệ wrapper–delegate** và **hợp đồng quyền sở hữu**.

- **Tự mở và sở hữu:** dùng `try-with-resources` để đóng đúng lúc; với wrapper chain, thường đóng lớp ngoài là đủ.
- **Được truyền vào để mượn:** không tự đóng delegate; chú ý wrapper có thể chuyển tiếp `close()`, buffering có thể đọc trước, và việc flush/finish cần quy ước.
- **Dùng chung:** một owner/coordinator quyết định shutdown; không để consumer tùy tiện đóng `System.in/out/err`, socket hoặc stream chung.
- **Trả resource sống:** caller nhận quyền sở hữu và trách nhiệm đóng; đừng trả stream đã bị đóng trong try.
- **Khi xử lý thất bại:** `try-with-resources` giữ lỗi chính và suppressed exceptions để chẩn đoán; đừng che lỗi.
- **Kiểm thử ownership:** dùng tracking stream để chứng minh phương thức mượn không đóng, wrapper đóng đúng delegate và caller còn dùng được sau khi hàm trả về.

**Câu hỏi quan trọng nhất mỗi khi viết `close()`: “Tôi có thực sự sở hữu tài nguyên này không, và có người khác còn cần nó sau đây không?”**

### Tài liệu tham khảo

- Java SE 17: [`AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html), [`Closeable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html), [`InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html), [`OutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html).
- Java SE 17: [`BufferedInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html), [`BufferedOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedOutputStream.html), [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html), [`OutputStreamWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html).
- Java SE 17: [`Scanner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html), [`PrintWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/PrintWriter.html), [`Files.lines`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`GZIPOutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPOutputStream.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-007">Cleanup khi tạo hoặc ghép tài nguyên thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Cleanup khi tạo hoặc ghép tài nguyên thất bại trong Java I/O

## 1. Vấn đề: đã mở một phần nhưng chưa xây dựng xong

Trong Java I/O, tài nguyên có thể là file, socket, stream, reader, writer, temporary file, channel hay một đối tượng quản lý nhiều tài nguyên. Chúng thường giữ file descriptor/handle hoặc bộ đệm cần được đóng. Nếu mã nguồn mở nhiều tài nguyên hoặc ghép các wrapper mà **bước sau ném exception**, những tài nguyên mở thành công trước đó vẫn cần được dọn dẹp.

Ví dụ luồng công việc:

~~~text
mở file A           [thành công]
mở file B           [thất bại]
xử lý A và B        [chưa chạy]
cleanup file A      [bắt buộc]
báo lỗi mở B        [giữ nguyên nguyên nhân]
~~~

Nếu không cleanup file A, ứng dụng có thể rò rỉ descriptor, giữ khóa file hoặc không giải phóng kết nối dù phương thức chưa trả về đối tượng kết quả. Đây là **partial initialization / partial acquisition**: một số tài nguyên đã được cấp phát, nhưng thao tác tạo tổng thể chưa thành công.

Vấn đề có hai trường hợp phải phân biệt:

1. **Tài nguyên A đã được tạo thành công, rồi lúc mở tài nguyên B có lỗi:** try-with-resources (TWR) có thể tự đóng A.
2. **Constructor/factory của chính B đã mở tài nguyên nội bộ rồi mới lỗi:** B chưa được tạo thành công nên bên gọi chưa có tham chiếu B để đóng. Constructor/factory phải tự cleanup phần tài nguyên nội bộ đã mở.

Một lỗi nữa có thể xảy ra **ngay trong cleanup**: phương thức close() có thể ném IOException hoặc exception khác. Code đúng phải bảo toàn lỗi gốc và tránh che khuất nó bằng lỗi đóng tài nguyên.

## 2. Hợp đồng sở hữu tài nguyên (ownership)

Để cleanup nhất quán, mỗi API cần có quy định rõ:

- **Ai mở thì ai có trách nhiệm đóng**, trừ khi giao quyền sở hữu qua hợp đồng đã xác định.
- Một phương thức **trả về thành công** một stream/wrapper có thể chuyển trách nhiệm đóng cho bên gọi.
- Một phương thức **thất bại trước khi trả về** phải dọn các tài nguyên nó đang sở hữu, kể cả những tài nguyên đã mở thành công một phần.
- Khi wrapper nhận ownership của stream bên dưới, **đóng wrapper thường sẽ đóng stream nền**. Nếu vừa đóng wrapper vừa đóng riêng stream nền, tài nguyên có thể bị close nhiều lần.
- Đừng ngầm giả định close luôn không lỗi hay luôn idempotent. Cần xem hợp đồng cụ thể của tài nguyên.

Ví dụ khi gọi **new BufferedInputStream(raw)**, wrapper sẽ sử dụng raw; lúc close wrapper, nó đóng raw theo hợp đồng lớp. Với một decorator do ứng dụng tự xây dựng, hợp đồng phải nói rõ wrapper **sở hữu** hay chỉ **mượn** stream. Tránh cả hai thái cực: không ai đóng gây leak, hoặc nhiều bên cùng đóng dẫn tới double-close và lỗi trạng thái.

**Giữ tài nguyên sống** không tương đương **giữ reference Java**: garbage collector có thể thu gom đối tượng, nhưng không nên dựa vào GC hoặc finalizer để giải phóng tài nguyên I/O đúng thời điểm. Dùng try-with-resources hoặc close trong finally.

## 3. TWR: các tài nguyên được mở trái sang phải, đóng phải sang trái

Cú pháp Java 17:

~~~java
try (Resource a = openA();
     Resource b = openB();
     Resource c = openC()) {
    use(a, b, c);
}
~~~

Các biểu thức initializer chạy **từ trái sang phải**: A rồi B rồi C. Khi rời khối try, các tài nguyên **đã khởi tạo thành công** được close **theo thứ tự ngược lại**: C, B, A.

Nếu **openB() ném exception**:

~~~text
openA()  -> thành công
openB()  -> ném IOException
openC()  -> KHÔNG được gọi
use(...) -> KHÔNG được chạy
a.close()-> được gọi tự động
ngoại lệ từ openB() được truyền ra
~~~

Không có đối tượng b hoàn chỉnh để close; TWR chỉ chịu trách nhiệm với những resource initializer **đã trả về thành công**. Nếu openB() kịp tạo một raw stream bên trong rồi lỗi trước khi trả về b, **openB() phải cleanup raw stream đó**.

TWR áp dụng cho đối tượng triển khai **AutoCloseable** hoặc **Closeable**. Phương thức close() được gọi kể cả khi body throw, return hoặc có exception; **try-with-resources tự quản thứ tự đóng và suppressed exception**. Từ Java 9, có thể đặt biến đã khai báo trước trong ngoặc try nếu biến final hoặc effectively final, nhưng điều đó không tự làm thay đổi quyền sở hữu của biến.

## 4. Chương trình runnable: mở B lỗi, A vẫn được đóng

Tạo file **AcquisitionFailureDemo.java** (mã nguồn UTF-8):

~~~java
import java.io.IOException;

public class AcquisitionFailureDemo {
    static final class Resource implements AutoCloseable {
        private final String name;
        private final boolean failClose;

        Resource(String name, boolean failClose) {
            this.name = name;
            this.failClose = failClose;
        }

        @Override
        public void close() throws IOException {
            System.out.println("close:" + name);
            if (failClose) {
                throw new IOException("close-" + name + " failed");
            }
        }
    }

    static Resource open(String name, boolean failClose)
            throws IOException {
        System.out.println("open:" + name);
        if ("B".equals(name)) {
            throw new IOException("open-B failed");
        }
        return new Resource(name, failClose);
    }

    public static void main(String[] args) {
        try (Resource a = open("A", true);
             Resource b = open("B", false)) {
            System.out.println("body");
        } catch (IOException primary) {
            System.out.println("primary=" + primary.getMessage());
            for (Throwable other : primary.getSuppressed()) {
                System.out.println("suppressed=" + other.getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 AcquisitionFailureDemo.java
java AcquisitionFailureDemo
~~~

Stdout dự kiến, đúng theo thứ tự gọi:

~~~text
open:A
open:B
close:A
primary=open-B failed
suppressed=close-A failed
~~~

Điểm cần quan sát:

- **open(A)** thành công nên A được đưa vào danh sách tài nguyên cần đóng.
- **open(B)** ném IOException; body chưa chạy (không có dòng body).
- A vẫn được close tự động ngay cả khi không vào body.
- close A **cũng thất bại**, nhưng exception của B vẫn là **primary**.
- IOException khi đóng A được gắn vào **primary.getSuppressed()**, nên có thể tìm lại cả hai vấn đề.

Nếu close A không thất bại thì danh sách suppressed rỗng; điều đó không ảnh hưởng cơ chế tự đóng. Nếu open B trả về thành công, B sẽ được đóng trước A lúc rời try.

## 5. Primary exception và suppressed exception

Khi hai lỗi cùng xuất hiện, phải quyết định **lỗi nào giải thích nguyên nhân chính**:

~~~text
Lỗi từ khởi tạo / body  -> primary exception
Lỗi trong close()       -> suppressed exception
~~~

Với TWR:

- Nếu initializer hoặc body ném lỗi và close cũng ném, lỗi ban đầu được giữ làm **primary**; các lỗi close được thêm vào danh sách suppressed.
- Nếu không có lỗi nào từ initializer/body nhưng close ném, lỗi close của tài nguyên đóng đầu tiên có thể trở thành primary; các lỗi close sau được suppressed.
- Các tài nguyên TWR còn lại vẫn được cố gắng close ngay cả khi close một tài nguyên trước đó thất bại.

Đây là ưu điểm lớn so với code thủ công kiểu:

~~~java
// SAI nếu muốn giữ nguyên lỗi nghiệp vụ:
try {
    work();
} finally {
    resource.close();
}
~~~

Nếu work() ném lỗi và resource.close() cũng ném, exception cuối có thể **che mất** exception từ work(). Ngoài ra, nếu close một tài nguyên trong finally ném lỗi, các lệnh cleanup phía sau nó có thể bị bỏ qua. Với TWR, Java thực hiện xử lý nhiều close và suppressed một cách nhất quán.

Để điều tra, log hoặc hiển thị cả **primary exception**, **cause chain** và **suppressed exceptions**. Chỉ lấy exception cuối cùng mà bỏ suppressed dễ khiến mất bằng chứng về resource cleanup thất bại.

**Suppressed không có nghĩa là lỗi không quan trọng**: một lần close thất bại có thể liên quan đến dữ liệu chưa flush hoặc tài nguyên chưa được giải phóng. Ứng dụng cần quy tắc xử lý phù hợp mức độ quan trọng của thao tác, nhưng vẫn giữ primary để định vị nguyên nhân đầu tiên.

## 6. Khi constructor của wrapper thất bại, TWR bên ngoài chưa đủ

Xét factory tạo raw stream, sau đó tạo wrapper:

~~~java
// Mảnh code minh họa ý tưởng, KHÔNG an toàn nếu bước wrap có thể lỗi.
InputStream raw = Files.newInputStream(path);
return createCustomWrapper(raw);
~~~

Nếu createCustomWrapper(raw) ném exception, phương thức không trả được wrapper và raw vẫn đang mở. Bên gọi không có wrapper để close. Cần đảm bảo **nếu chuyển ownership không thành công thì người mở raw vẫn tự đóng raw**.

Một kỹ thuật là giữ ownership ở factory trong lúc xây dựng, chỉ chuyển ownership sau khi tạo wrapper thành công:

~~~text
factory mở raw        -> factory đang sở hữu raw
factory tạo wrapper   -> có thể thành công/thất bại
  thành công          -> trả wrapper, chuyển ownership cho caller
  thất bại            -> factory close raw rồi rethrow lỗi gốc
caller close wrapper  -> wrapper close raw theo hợp đồng
~~~

Đối với Java wrapper chuẩn như BufferedInputStream, constructor đơn giản có thể không có IOException checked; nhưng một decorator tùy chỉnh có thể kiểm tra header, đọc metadata, mở file phụ hoặc ném exception. **Không dựa vào việc một constructor có vẻ đơn giản để bỏ qua nguyên tắc xử lý partial acquisition**.

## 7. Chương trình runnable: factory rollback khi wrapper lỗi

Tạo file **WrapperFactoryFailureDemo.java**:

~~~java
import java.io.IOException;

public class WrapperFactoryFailureDemo {
    static final class Handle implements AutoCloseable {
        Handle() {
            System.out.println("acquire:raw");
        }

        @Override
        public void close() throws IOException {
            System.out.println("close:raw");
            throw new IOException("close-raw failed");
        }
    }

    static final class Wrapper implements AutoCloseable {
        private final Handle raw;

        Wrapper(Handle raw, boolean fail) throws IOException {
            System.out.println("construct:wrapper");
            if (fail) {
                throw new IOException("wrapper failed");
            }
            this.raw = raw;
        }

        @Override
        public void close() throws IOException {
            raw.close();
        }
    }

    static Wrapper create(boolean fail) throws IOException {
        Handle raw = new Handle();
        try {
            // Ownership chỉ chuyển cho Wrapper khi constructor trả về.
            return new Wrapper(raw, fail);
        } catch (IOException | RuntimeException | Error primary) {
            try {
                raw.close();
            } catch (Throwable cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
            throw primary;
        }
    }

    public static void main(String[] args) {
        try (Wrapper wrapper = create(true)) {
            System.out.println("body");
        } catch (IOException primary) {
            System.out.println("primary=" + primary.getMessage());
            for (Throwable other : primary.getSuppressed()) {
                System.out.println("suppressed=" + other.getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 WrapperFactoryFailureDemo.java
java WrapperFactoryFailureDemo
~~~

Stdout dự kiến:

~~~text
acquire:raw
construct:wrapper
close:raw
primary=wrapper failed
suppressed=close-raw failed
~~~

**Vì sao đúng?**

1. Handle được mở: factory đang giữ ownership.
2. Constructor Wrapper bắt đầu rồi ném IOException, nên **không có Wrapper hoàn chỉnh**; try-with-resources ở main không có resource để đóng.
3. Factory bắt lỗi mở/wrap, cố close Handle trước khi trả lỗi cho caller.
4. Nếu close Handle cũng ném, factory thêm lỗi cleanup vào **suppressed của exception gốc**, không thay thế nguyên nhân thất bại wrapper.
5. Nếu Wrapper được tạo thành công, factory trả Wrapper và caller có trách nhiệm đóng nó; factory không đóng raw ở đường thành công vì wrapper đã sở hữu raw.

Trong factory này, nhánh catch bao gồm IOException, RuntimeException, Error phát sinh từ construction; `Throwable` ở catch nội bộ chỉ nhằm **giữ lại lỗi cleanup** làm suppressed. Trong ứng dụng thực tế cần cân nhắc các lỗi nghiêm trọng như VirtualMachineError, ThreadDeath và quy định exception của API. Đây là mẫu giải thích ownership/cleanup, không phải lời khuyên bắt mọi Throwable rồi tiếp tục chạy bình thường.

**Ràng buộc thiết kế:** ví dụ giả định wrapper **không tự đóng raw khi constructor thất bại**. Nếu một wrapper đã sở hữu rồi tự cleanup raw trước khi ném, factory và wrapper phải thống nhất hợp đồng tránh double-close. Tránh mặc định close() của mọi tài nguyên đều gọi lặp an toàn.

## 8. Tự cleanup khi constructor mở nhiều tài nguyên nội bộ

Một constructor có thể mở A rồi B trước khi object tạo thành công:

~~~text
new Composite():
    open A          -> thành công
    open B          -> lỗi
    chưa có Composite để caller.close()
~~~

Bên gọi không thể dùng try-with-resources để đóng một instance Composite **chưa bao giờ được trả về**. Constructor hoặc factory xây dựng Composite phải đặt ownership của A trong một vùng cleanup trước khi thử B.

Một giải pháp đáng tin hơn trong nhiều thiết kế là tách **factory acquire resources** khỏi **constructor gắn các reference đã hợp lệ**, hoặc dùng TWR có phạm vi rõ. Mẫu pseudo-code có cấu trúc:

~~~java
// Mảnh code: createComposite nhận trách nhiệm sở hữu tài nguyên
// trong lúc xây dựng. Kiểu A/B/Composite giả định có API tương ứng.
A a = openA();
try {
    B b = openB();
    try {
        return new Composite(a, b); // Chuyển ownership nếu thành công
    } catch (IOException | RuntimeException | Error primary) {
        try {
            b.close();
        } catch (Throwable cleanup) {
            primary.addSuppressed(cleanup);
        }
        throw primary;
    }
} catch (IOException | RuntimeException | Error primary) {
    try {
        a.close();
    } catch (Throwable cleanup) {
        primary.addSuppressed(cleanup);
    }
    throw primary;
}
~~~

Đây là **mảnh code minh họa**, các kiểu A, B, Composite và openA/openB được ứng dụng định nghĩa. Mục đích là thấy vùng sở hữu A và B, và cơ chế rollback khi từng bước thất bại. Với API thực tế, nên đóng gói logic này thành factory rõ contract, test đủ failure path, tránh lặp nhiều khối catch rối.

Quan trọng: nếu constructor của B **tự mở một handle rồi ném** trước khi trả về B, chính constructor/factory của B phải cleanup handle ấy. Vòng catch phía ngoài chỉ đóng được A hoặc những B đã trả về thành công.

### Cách dùng TWR khi kết quả không cần sống ngoài block

Nếu các tài nguyên chỉ phục vụ một thao tác cục bộ, TWR là lựa chọn đơn giản nhất:

~~~java
// Mảnh code trong phương thức có Path source, target.
try (var input = java.nio.file.Files.newInputStream(source);
     var output = java.nio.file.Files.newOutputStream(target)) {
    input.transferTo(output);
}
~~~

Nếu mở output thất bại, input được đóng tự động. Nếu quá trình copy lỗi, cả output và input vẫn được thử đóng theo thứ tự ngược lại. Trong trường hợp này **không cần chuyển ownership của stream ra ngoài**.

Ngược lại, nếu cần **trả wrapper ra ngoài** để caller dùng sau khi factory kết thúc, không được đặt raw stream trong TWR mà luôn close khi rời factory. Ví dụ trả `new BufferedInputStream(raw)` từ bên trong `try (raw)` sẽ trả một wrapper đã có stream nền bị đóng. Cần dùng mẫu factory transfer ownership đã mô tả.

## 9. Khi close() của wrapper sở hữu stream bên dưới

Nhiều wrapper chuẩn của Java đóng luôn stream được bọc:

~~~java
// Mảnh code: BufferedInputStream sở hữu raw bên dưới.
InputStream raw = Files.newInputStream(path);
BufferedInputStream wrapped = new BufferedInputStream(raw);
wrapped.close(); // Thường đóng cả raw qua wrapper.
~~~

Khi ownership đã chuyển vào wrapped, caller nên **đóng wrapped** thay vì đóng cả wrapped và raw một cách độc lập. TWR có hai resource lồng nhau có thể double-close raw nếu wrapper đã đóng nó trước:

~~~java
// Cần hiểu rõ contract vì raw có thể bị close hai lần:
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream wrapped = new BufferedInputStream(raw)) {
    // ...
}
~~~

Trong thư viện chuẩn có loại stream chịu được close lặp, nhưng với custom AutoCloseable, double-close có thể không an toàn hoặc gây side effect. Nếu mục tiêu là khắc phục thất bại lúc tạo wrapper, hãy dùng contract ownership và factory rollback rõ ràng.

**Không nên thiết kế custom wrapper mà đóng tài nguyên của caller nếu caller chỉ cho mượn tài nguyên**, trừ khi API ghi rõ điều này. Nếu wrapper chỉ borrow raw, caller vẫn đóng raw theo hợp đồng của mình; chọn một convention và dùng nhất quán.

## 10. Dọn file tạm và side effect khi tạo nhiều tài nguyên

Với Java I/O, resource không chỉ là stream. Một thao tác có thể:

1. Tạo thư mục/file tạm.
2. Mở output stream để ghi.
3. Tạo stream bọc nén hoặc mã hóa.
4. Ghi dữ liệu.
5. Đổi tên file tạm sang file đích.

Nếu bước 2–4 lỗi, đóng stream thôi **chưa chắc xóa file tạm**. Cần **compensating cleanup** cho side effect: xóa file tạm của chính thao tác đó, nhưng chỉ khi chắc chắn nó do thao tác tạo và chưa được chuyển quyền cho nơi khác.

Ví dụ mô hình:

~~~text
tạo file temp -> ghi vào temp -> đóng thành công
                   |                 |
                   | lỗi             v
                   v              publish/rename
              đóng stream             |
              xóa temp nếu còn        v
                                 file kết quả
~~~

Sử dụng `Files.deleteIfExists(temp)` trong cleanup thích hợp; nếu thao tác xóa thất bại, cũng cần giữ lại lỗi gốc và ghi nhận lỗi dọn dẹp. Tránh xóa file đích hoặc dữ liệu đã tồn tại trước khi tác vụ bắt đầu. Với thao tác publish/rename, yêu cầu tính nguyên tử và độ bền dữ liệu còn phụ thuộc filesystem cùng thiết kế riêng.

**Cleanup không đồng nghĩa rollback mọi thay đổi:** close của một OutputStream không khôi phục nội dung file đã ghi một phần. Nếu dữ liệu cần tính toàn vẹn, nên xây dựng bằng file tạm, xác minh thành công rồi mới publish.

## 11. Các lỗi thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Mở A, B rồi mới bắt đầu try/finally | Nếu B lỗi, A có thể leak | Bắt đầu TWR từ resource A hoặc có cleanup rollback |
| Tưởng TWR đóng cả object constructor chưa trả về | Handle nội bộ vẫn có thể leak | Constructor/factory tự cleanup các resource đã tạo |
| Close trong finally ném và che mất lỗi gốc | Mất nguyên nhân lỗi chính | Ưu tiên TWR và suppressed exceptions |
| Trả wrapper từ factory trong TWR đang sở hữu raw | Raw bị đóng trước khi caller dùng | Transfer ownership khi factory trả thành công |
| Wrapper + caller cùng close stream nền | Có thể double-close | Công bố một owner rõ ràng |
| Không close nguồn khi tạo wrapper ném exception | Resource leak | Catch, close nguồn, rethrow primary |
| Bỏ qua getSuppressed() | Mất thông tin lỗi close | Kiểm tra primary, cause và suppressed |
| Catch exception rồi chỉ log, tiếp tục trả object nửa khởi tạo | Object không hợp lệ | Rollback rồi rethrow |
| Coi close không thể thất bại | Mất lỗi flush, đóng file/socket | Có chính sách xử lý IOException khi close |
| Đóng resource sai thứ tự phụ thuộc | Wrapper dùng raw đã đóng | Đóng theo reverse ownership/dependency |
| Chỉ close stream nhưng giữ file tạm hỏng | Rác file hoặc dữ liệu dở dang | Cleanup cả side effect do tác vụ sở hữu |
| Dựa vào GC để đóng I/O | Không bảo đảm thời điểm giải phóng | TWR/close chủ động |

## 12. Quy trình thiết kế và kiểm tra

Khi viết code xây dựng/ghép tài nguyên, tự trả lời:

1. **Mỗi bước tạo cái gì?** File descriptor, stream, buffer, file tạm, socket, lock...
2. **Ai sở hữu sau khi bước tạo thành công?** Factory hay đối tượng wrapper?
3. **Điều gì xảy ra nếu bước tiếp theo lỗi?** Tài nguyên tạo trước được close ở đâu?
4. **Nếu close cũng lỗi thì sao?** Primary exception được giữ nguyên, cleanup exception được suppressed hay báo cáo riêng theo contract?
5. **Khi thành công, ai đóng cuối cùng?** Caller nhận ownership qua return hay phương thức tự đóng trong scope?
6. **Có side effect khác ngoài tài nguyên đóng được không?** File tạm, transaction, rename, trạng thái external...
7. **Có test mô phỏng từng điểm thất bại không?** Lỗi mở A, lỗi mở B, lỗi wrap, lỗi body, lỗi close A/B, và trường hợp hai lỗi đồng thời.

Với tài nguyên nhiều tầng, vẽ **sơ đồ ownership** trước khi tối ưu. Trong một luồng đơn giản, TWR có thể xử lý hoàn chỉnh; trong factory trả wrapper ra ngoài, cần có thời điểm chuyển quyền rõ ràng. Không nên thêm catch chỉ để đóng resource đã được TWR đóng, vì có thể tạo double-close và che lỗi.

### Kiểm tra hành vi theo tình huống

| Tình huống | Hành vi cần có |
| --- | --- |
| Mở A thất bại trước khi A được tạo | Không có A để đóng; A factory phải tự dọn phần đã mở nội bộ |
| Mở A thành công, mở B lỗi | A được đóng, lỗi B được giữ |
| Body lỗi, close B và A đều lỗi | Lỗi body là primary, lỗi close của B và A là suppressed |
| Body thành công, close B lỗi, close A thành công | Lỗi close B được báo, A vẫn được close |
| Wrapper construction lỗi sau khi raw mở | Raw được đóng theo quyền sở hữu của factory |
| Wrapper construction thành công | Caller đóng wrapper; wrapper đóng raw nếu sở hữu |
| Tạo file tạm thành công nhưng ghi thất bại | Stream được đóng; file tạm của tác vụ được cleanup |
| Cleanup cũng lỗi | Giữ đầy đủ primary và cleanup failure; không tuyên bố hoàn tất thành công |

## 13. Bài tập tự kiểm tra

**Câu 1.** Trong `try (A a = openA(); B b = openB())`, nếu openB ném IOException thì A có được đóng không?  
**Đáp án:** Có, nếu openA đã trả về A thành công. TWR đóng A khi initializer B thất bại.

**Câu 2.** Nếu constructor B đã mở một Socket rồi ném trước khi trả về B thì TWR bên gọi có đóng Socket được không?  
**Đáp án:** Không tự động. B chưa được tạo xong, nên constructor/factory B phải cleanup Socket nội bộ.

**Câu 3.** Body ném IOException, A.close cũng ném IOException: lỗi nào là primary trong TWR?  
**Đáp án:** Lỗi từ body vẫn là primary; lỗi close được đặt trong getSuppressed().

**Câu 4.** Factory mở InputStream rồi tạo wrapper. Nếu wrapper khởi tạo thất bại thì owner nào phải đóng InputStream?  
**Đáp án:** Factory vẫn sở hữu cho đến khi wrapper được trả về thành công; factory phải đóng InputStream và truyền lại lỗi gốc.

**Câu 5.** Có nên dùng `try (InputStream raw = openRaw()) { return new BufferedInputStream(raw); }` không?  
**Đáp án:** Không nếu wrapper được trả về để caller đọc sau đó; raw đã bị đóng khi rời try, nên wrapper không còn nguồn mở.

**Câu 6.** Đóng wrapper và raw cùng lúc có luôn an toàn không?  
**Đáp án:** Không. Wrapper có thể đã đóng raw; custom resource có thể không hỗ trợ double-close. Cần một quy tắc ownership rõ.

**Câu 7.** Nếu tác vụ ghi dở file tạm rồi IOException, chỉ close OutputStream đã đủ rollback chưa?  
**Đáp án:** Chưa. Close không xóa hoặc khôi phục nội dung file; có thể cần xóa file tạm và chỉ publish khi ghi hoàn chỉnh.

## 14. Tài liệu Java SE 17 để tra cứu

- [AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng close; có loại resource không hỗ trợ close lặp an toàn.
- [Closeable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html): giao diện close cho I/O.
- [Java Tutorial — try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): đóng tài nguyên theo thứ tự ngược, suppressed exceptions.
- [Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): xem lỗi phụ xảy ra trong cleanup.
- [BufferedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html): ví dụ wrapper sở hữu input stream bên dưới.

**Ghi nhớ:** TWR tự đóng các resource **đã khởi tạo thành công** khi những bước sau thất bại, đồng thời giữ primary và suppressed exceptions. Nhưng nếu một constructor/factory mở resource nội bộ rồi thất bại trước khi trả về object, phần **cleanup nội bộ là trách nhiệm của chính constructor/factory**. Khi ghép stream, hãy quy định rõ lúc nào ownership được chuyển; khi cleanup thất bại, bảo toàn lỗi gốc cùng thông tin về lỗi close.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-008">UncheckedIOException khi kết hợp I/O với Stream API</a>

<details>
<summary>Xem chi tiết</summary>

# UncheckedIOException khi kết hợp I/O với Stream API

## 1. Vì sao cần UncheckedIOException?

Trong Java, nhiều thao tác I/O có thể ném **`IOException`**, một *checked exception*. Trình biên dịch yêu cầu phương thức gọi phải bắt (`catch`) hoặc khai báo (`throws IOException`) ngoại lệ ấy. Nhưng các functional interface thông dụng của **Stream API** như `Function<T,R>`, `Predicate<T>`, `Consumer<T>` không khai báo checked exception ở phương thức trừu tượng.

Do đó, cách viết có vẻ tự nhiên này **không biên dịch**:

```java
// Ví dụ cố ý SAI: Files.readString có thể throws IOException.
List<String> contents = paths.stream()
        .map(path -> Files.readString(path))
        .toList();
```

`Stream.map` nhận một `Function`; `Function.apply` không khai báo `throws IOException`. Dù phương thức bao quanh có `throws IOException`, bản thân lambda vẫn bị ràng buộc bởi chữ ký `Function.apply`.

`java.io.UncheckedIOException` giải quyết ranh giới đó: đây là một **`RuntimeException` chuyên dùng để bao bọc `IOException`**. Ta có thể chuyển một lỗi I/O checked thành lỗi unchecked tại chỗ lambda, cho phép ngoại lệ thoát qua `map`, `filter`, `forEach` hoặc phép biến đổi khác, rồi quyết định xử lý ở ranh giới thích hợp.

```text
File / network / Reader
      │
      ▼
Phương thức I/O throws IOException  (checked)
      │
      ▼
catch (IOException cause)
      │
      ▼
throw new UncheckedIOException(cause)
      │
      ▼
Stream pipeline / terminal operation  (unchecked)
      │
      ▼
catch (UncheckedIOException ex)
      └─ ex.getCause()  → IOException ban đầu
```

**Mục tiêu của UncheckedIOException không phải che giấu lỗi.** Nó bảo toàn **nguyên nhân `IOException`** để code có thể báo lỗi, huỷ xử lý, retry có kiểm soát, hoặc chuyển ngoại lệ về API checked ở lớp trên.

### Phân biệt các khái niệm

| Khái niệm | Ý nghĩa |
| --- | --- |
| `IOException` | Checked exception mô tả lỗi đọc/ghi hay nguồn dữ liệu |
| `UncheckedIOException` | Runtime exception bọc một `IOException`, tiện cho nơi không khai báo checked exception |
| `RuntimeException` | Nhóm exception unchecked rộng hơn; không riêng cho I/O |
| Stream pipeline | Chuỗi các bước biến đổi thường chạy **lazy** |
| Terminal operation | `toList()`, `collect()`, `count()`, `forEach()`, `findFirst()`... kích hoạt thực thi |
| Resource-owning stream | Một số Stream API giữ file/directory handle và cần `close()` |

Một pipeline thuần xử lý dữ liệu trong bộ nhớ có thể hoàn toàn không cần `UncheckedIOException`; nó xuất hiện khi **một thao tác I/O thật** được thực hiện hoặc khi API Java chuẩn sử dụng chính kiểu ngoại lệ này để truyền lỗi trong quá trình duyệt stream.

## 2. API và quan hệ với IOException

`UncheckedIOException` nằm trong `java.io`. Nó kế thừa `RuntimeException`, nên trình biên dịch không yêu cầu caller bắt hoặc khai báo. Các constructor quan trọng:

```java
new UncheckedIOException(IOException cause)
new UncheckedIOException(String message, IOException cause)
```

Đoạn này là **biểu thức minh họa**, cần import `java.io.UncheckedIOException`, `java.io.IOException`; `cause` phải là một `IOException` không null. Đây **không phải constructor nhận Throwable bất kỳ**. Đừng dùng kiểu này để bọc `IllegalArgumentException` hoặc `NullPointerException` vì chúng không biểu thị lỗi I/O.

Các thao tác:

- `getCause()` trả chính `IOException` gốc.
- `getMessage()` trả thông điệp của wrapper (có thể thêm ngữ cảnh).
- Stack trace cho biết nơi bọc và chuỗi `cause` giúp lần về điểm phát sinh I/O.
- Vì là unchecked, `UncheckedIOException` có thể truyền qua `Function.apply`, `Predicate.test`, `Consumer.accept` mà không đòi khai báo `throws`.

Một lựa chọn tốt khi bọc là thêm **ngữ cảnh thực tế** như đường dẫn tương đối, thao tác đang làm, ID bản ghi, hoặc định dạng cần đọc:

```java
try {
    return Files.readString(path, StandardCharsets.UTF_8);
} catch (IOException cause) {
    throw new UncheckedIOException(
            "Cannot read: " + path, cause);
}
```

Đây là đoạn nằm trong **một phương thức trả String** có `Path path` và import cần thiết. Không trả `""` chỉ để tránh lỗi trừ khi ứng dụng đã định nghĩa rõ “không đọc được thì coi là chuỗi rỗng”, vì như vậy sẽ trộn lỗi I/O với dữ liệu hợp lệ.

## 3. Checked exception và chữ ký của lambda

Xét `java.util.function.Function<T,R>`:

```java
@FunctionalInterface
public interface Function<T, R> {
    R apply(T value);
}
```

Đây là **hình thức rút gọn để giải thích chữ ký**, không sao chép đầy đủ nội dung interface chuẩn. `apply` không khai báo checked exceptions. Một lambda dùng làm `Function<Path, String>` vì vậy không thể để `IOException` từ `Files.readString` thoát trực tiếp.

### Cách phổ biến: catch ngay trong lambda rồi bọc

```java
List<String> texts = paths.stream()
        .map(path -> {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException cause) {
                throw new UncheckedIOException(
                        "Cannot read " + path, cause);
            }
        })
        .toList();
```

Đây là đoạn minh họa; `paths` là `List<Path>` đã có. Cần `Files`, `StandardCharsets`, `IOException`, `UncheckedIOException`, `List`. Lời gọi `toList()` là terminal operation và có từ **Java 16**.

Nếu mọi tệp đều đọc được, `texts` chứa nội dung lần lượt theo encounter order của một list-stream tuần tự. Nếu một tệp gây `IOException`, lambda bọc nó thành `UncheckedIOException`, phép `toList()` thất bại và không trả danh sách hoàn chỉnh.

**Không có tính giao dịch tự động:** nếu lambda còn ghi file, cập nhật cơ sở dữ liệu hoặc thay đổi trạng thái, các phần đã xử lý trước lỗi có thể vẫn có side effect. Stream API không rollback những tác động đó.

### Cách khác: đưa I/O vào phương thức adapter có tên

```java
static String readUtf8Unchecked(Path path) {
    try {
        return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException cause) {
        throw new UncheckedIOException(
                "Failed to read " + path, cause);
    }
}
```

Đây là **phương thức minh họa**; caller dùng `paths.stream().map(MyClass::readUtf8Unchecked)`. Đặt logic bọc trong một method giúp code stream dễ đọc và dễ kiểm thử. Một thư viện tổng quát có thể có adapter cho “throwing function”, nhưng cũng cần chính sách rõ rằng **chỉ `IOException` được bọc bằng `UncheckedIOException`**; lỗi lập trình khác giữ nguyên kiểu.

## 4. Ví dụ Java 17 chạy được: wrapper trong pipeline

Chương trình mô phỏng nguồn I/O xác định để thấy sự khác biệt giữa exception gốc và wrapper. Nó không cần tệp bên ngoài, nên output ổn định.

**Tệp `UncheckedIoPipelineDemo.java`:**

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

public class UncheckedIoPipelineDemo {
    static String load(String name) throws IOException {
        if ("broken".equals(name)) {
            throw new IOException("synthetic read failure");
        }
        return name.toUpperCase(Locale.ROOT);
    }

    static String loadUnchecked(String name) {
        try {
            return load(name);
        } catch (IOException cause) {
            throw new UncheckedIOException("Loading " + name, cause);
        }
    }

    public static void main(String[] args) {
        List<String> good = List.of("alpha", "beta").stream()
                .map(UncheckedIoPipelineDemo::loadUnchecked)
                .toList();
        System.out.println("Success: " + good);

        try {
            List.of("alpha", "broken", "beta").stream()
                    .map(UncheckedIoPipelineDemo::loadUnchecked)
                    .toList();
        } catch (UncheckedIOException failure) {
            System.out.println("Wrapper: "
                    + failure.getClass().getSimpleName());
            System.out.println("Cause: "
                    + failure.getCause().getClass().getSimpleName());
            System.out.println("Cause message: "
                    + failure.getCause().getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac UncheckedIoPipelineDemo.java
java UncheckedIoPipelineDemo
```

**Stdout chính xác:**

```text
Success: [ALPHA, BETA]
Wrapper: UncheckedIOException
Cause: IOException
Cause message: synthetic read failure
```

Điểm cần quan sát:

1. `load` khai báo `throws IOException`; nó vẫn giữ API checked nếu được gọi trực tiếp.
2. `loadUnchecked` bắt và bọc lỗi; có thể dùng làm method reference cho `map`.
3. `toList()` mới thực thi pipeline. Khi một phần tử ném lỗi, kết quả list không được trả về.
4. `getCause()` giữ `IOException` gốc với thông điệp ban đầu.

Không nhất thiết mọi test phải tạo file lỗi thực. Test bằng phương thức giả lập như trên giúp xác minh **chính sách truyền ngoại lệ** một cách rõ ràng, còn integration test nên kiểm tra quyền truy cập, tệp biến mất, ổ đĩa hoặc network khi cần.

## 5. Stream API thực thi lazy: lỗi xảy ra khi nào?

Các phép biến đổi trung gian như `map`, `filter`, `peek`, `flatMap` thường xây dựng pipeline **mà chưa xử lý phần tử nào**. Vì vậy việc khai báo pipeline có thể không gây lỗi; lỗi có thể xuất hiện khi gọi terminal operation:

```java
Stream<String> pipeline = paths.stream()
        .map(MyClass::readUtf8Unchecked); // Chưa nhất thiết đọc tệp.

// Đây mới là thời điểm các phần tử thực sự được đọc:
List<String> result = pipeline.toList();
```

Đoạn minh họa trên giả định method `readUtf8Unchecked` có sẵn và các import tương ứng. Không nên kết luận “pipeline tạo thành công nên I/O thành công”.

**Short-circuit operation** cũng thay đổi phạm vi lỗi có thể xảy ra:

- `findFirst()`, `anyMatch()`, `limit(n)` có thể chỉ đọc một phần nguồn.
- Một phần tử lỗi ở phía sau có thể **không được chạm tới**.
- `count()` trên một số nguồn và pipeline có thể được tối ưu, không gọi mọi intermediate operation không ảnh hưởng kết quả. Không đặt I/O side effects vào `peek` rồi cho rằng chúng luôn chạy.

Nếu nghiệp vụ cần đảm bảo **mọi tệp đều được xác minh**, phải chọn terminal operation và cấu trúc xử lý thích hợp. Stream API là công cụ xử lý dữ liệu, không phải engine giao dịch đảm bảo mọi side effect đã xảy ra đúng một lần.

### Đưa I/O vào map có luôn là lựa chọn tốt?

Không. Khi quá trình có nhiều trạng thái, cơ chế retry, yêu cầu rollback hoặc nhiều ngoại lệ cần thu thập, một vòng `for` thông thường có thể rõ ràng hơn. `UncheckedIOException` là công cụ kỹ thuật khi **checked exception chạm biên functional interface**, không phải lý do để ép mọi công việc I/O vào một pipeline.

## 6. Files.lines: IOException ở lúc mở và UncheckedIOException khi đọc lười

`Files.lines(Path, Charset)` trả `Stream<String>` đọc dòng từ file theo cách lazy. Có **hai thời điểm lỗi** cần phân biệt:

1. **Lúc mở**: `Files.lines(path, charset)` có thể ném **`IOException` (checked)** nếu không mở được tệp.
2. **Lúc tiêu thụ**: sau khi stream được tạo, nếu xảy ra lỗi I/O trong lúc đọc các dòng, lỗi thường được truyền ra dưới dạng **`UncheckedIOException`** từ operation đang duyệt stream.

```java
try (Stream<String> lines =
             Files.lines(path, StandardCharsets.UTF_8)) {
    long count = lines.filter(s -> !s.isBlank()).count();
    System.out.println(count);
} catch (UncheckedIOException duringRead) {
    // Lỗi xảy ra khi duyệt stream.
} catch (IOException duringOpenOrClose) {
    // Lỗi checked lúc mở hoặc tại một lời gọi I/O khai báo checked.
}
```

Đây là **đoạn minh họa**, cần import `Stream`, `Files`, `Path`, `StandardCharsets`, `UncheckedIOException`, `IOException`. `Stream.close()` không khai báo checked `IOException`, vì vậy không thể giả định một lỗi giải phóng file luôn đi ra ở nhánh catch checked. Quan trọng nhất là luôn dùng `try-with-resources` để đóng `Stream<String>` do `Files.lines` trả về.

**Không phải terminal operation tự đóng stream.** Gọi `count()`, `toList()` hoặc `forEach()` hoàn thành không thay thế `close()` khi stream sở hữu file handle.

### Ví dụ Java 17 chạy được: Files.lines và quản lý tài nguyên

**Tệp `FileLinesDemo.java`:**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-lines-", ".txt");

        try {
            Files.writeString(file, "alpha\n\nbeta\n",
                    StandardCharsets.UTF_8);

            try (Stream<String> lines =
                         Files.lines(file, StandardCharsets.UTF_8)) {
                long nonBlank = lines.filter(s -> !s.isBlank()).count();
                System.out.println("Nonblank lines: " + nonBlank);
            }

            try (Stream<String> lines =
                         Files.lines(file, StandardCharsets.UTF_8)) {
                String joined = lines
                        .filter(s -> !s.isBlank())
                        .map(String::toUpperCase)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                System.out.println("Joined: " + joined);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Lệnh:

```bash
javac FileLinesDemo.java
java FileLinesDemo
```

**Stdout chính xác khi thao tác tệp thành công:**

```text
Nonblank lines: 2
Joined: ALPHA,BETA
```

Một stream đã dùng terminal operation **không được sử dụng lại**; ví dụ mở `Files.lines` lần thứ hai cho phép chạy một phép tính khác. Tệp tạm được xóa trong `finally`. Nếu thao tác xóa lỗi, chương trình có thể ném `IOException`, vì thế output nêu trên là trường hợp chạy thành công.

Với tệp có encoding lỗi hoặc nội dung đọc trong lúc tệp bị thay đổi, cách thức báo lỗi phụ thuộc API và charset; ứng dụng cần xử lý `UncheckedIOException` theo chính sách của mình, không chỉ bắt `IOException` checked ở lời gọi mở.

## 7. BufferedReader.lines và tài nguyên thuộc về ai?

`BufferedReader.lines()` cũng trả `Stream<String>` lấy dòng từ reader. Khi có lỗi I/O trong lúc đọc, stream có thể ném `UncheckedIOException`. Tuy nhiên cần phân biệt rõ ownership:

```java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    long lines = reader.lines().count();
    System.out.println(lines);
}
```

Đây là đoạn minh họa. Ở đây `BufferedReader` là tài nguyên được mở và **chính try-with-resources trên reader** chịu trách nhiệm đóng. Đừng tự suy luận rằng chỉ vì một đối tượng có kiểu `Stream<String>`, việc đóng nó luôn đóng reader bên dưới; **`BufferedReader.lines()` không phải cùng hợp đồng tài nguyên với `Files.lines()`**.

Cần lưu ý: khi một Stream đang tiêu thụ `BufferedReader`, tránh thao tác trực tiếp trên reader từ luồng khác hoặc giữa các bước stream. Reader là nguồn có trạng thái, dễ dẫn đến dữ liệu bị bỏ qua hoặc tiêu thụ không theo ý.

### So sánh nhanh

| API | Ai mở nguồn? | Ai cần đóng? | Lỗi khi duyệt |
| --- | --- | --- | --- |
| `Files.lines(path, charset)` | API mở file | Caller đóng `Stream<String>` trả về | Có thể là `UncheckedIOException` |
| `reader.lines()` | Caller đã có `BufferedReader` | Caller quản lý và đóng reader | Có thể là `UncheckedIOException` |
| `Files.readAllLines(path, charset)` | API đọc trong lời gọi | Không trả stream sống; trả `List<String>` | `IOException` checked từ lời gọi |
| `Files.readString(path, charset)` | API đọc trong lời gọi | Không trả stream sống; trả `String` | `IOException` checked từ lời gọi |

API trả về toàn bộ dữ liệu thường đơn giản về ownership, nhưng tiêu tốn bộ nhớ theo kích thước file. API lazy hữu ích cho dữ liệu lớn nhưng cần quản lý đúng thời điểm đóng.

## 8. Files.list, Files.walk và Files.find

Không chỉ `Files.lines`, các API duyệt thư mục cũng thường trả **Stream API gắn với resource I/O**:

- `Files.list(directory)`: trả một stream duyệt các phần tử trực tiếp của thư mục.
- `Files.walk(start, maxDepth, ...)`: duyệt cây tệp và thư mục theo độ sâu quy định.
- `Files.find(start, maxDepth, matcher, ...)`: duyệt cây và lọc qua `BiPredicate<Path,BasicFileAttributes>`.

Lời gọi **mở** những stream này có thể ném `IOException` checked. Trong khi duyệt lazy, nếu phát sinh lỗi khi đọc directory hoặc attributes, có thể ném `UncheckedIOException`. Các stream đó **phải được đóng** để giải phóng tài nguyên dùng để duyệt thư mục.

```java
try (Stream<Path> entries = Files.list(directory)) {
    List<Path> names = entries.sorted().toList();
    // Dùng names ở đây.
}
```

Đây là đoạn minh họa, giả định `Path directory` đã có. Việc `toList()` trả về danh sách các `Path` không kéo theo việc giữ directory stream mở sau khi khối `try` kết thúc.

### Lồng I/O trong Files.walk

Một pattern hay gặp là vừa duyệt file, vừa đọc nội dung:

```java
try (Stream<Path> files = Files.walk(root)) {
    List<String> texts = files
            .filter(Files::isRegularFile)
            .map(UncheckedFileReader::readUtf8Unchecked)
            .toList();
}
```

Đây là đoạn minh họa: class `UncheckedFileReader` giả định có `readUtf8Unchecked(Path)` như phương thức adapter ở mục 3. Có **hai nguồn lỗi**:

1. Bản thân `Files.walk` khi duyệt hệ thống tệp (và truy vấn thuộc tính), có thể ném `UncheckedIOException`.
2. `readUtf8Unchecked` do ứng dụng tự viết cũng có thể ném `UncheckedIOException`.

Để chẩn đoán, nên thêm ngữ cảnh thao tác/đường dẫn ngay khi bọc nguyên nhân. **Không chạy song song tùy tiện** trên cây tệp nếu chưa kiểm soát file descriptor, tác động từ việc file bị thay đổi, quyền đọc và giới hạn bộ nhớ khi tích lũy mọi nội dung.

## 9. Ví dụ thực tế: đọc nhiều tệp và trả lỗi có ngữ cảnh

Một hàm nhận danh sách đường dẫn muốn dùng Stream API nhưng vẫn khai báo **`throws IOException`** với caller. Có thể bọc trong lambda rồi **gỡ wrapper tại ranh giới public API**.

**Tệp `ReadFilesWithStream.java`:**

```java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ReadFilesWithStream {
    static List<String> readAll(List<Path> paths) throws IOException {
        try {
            return paths.stream()
                    .map(path -> {
                        try {
                            return Files.readString(
                                    path, StandardCharsets.UTF_8);
                        } catch (IOException cause) {
                            throw new UncheckedIOException(
                                    "Reading " + path.getFileName(), cause);
                        }
                    })
                    .toList();
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("io-batch-", ".txt");
        try {
            Files.writeString(file, "hello",
                    StandardCharsets.UTF_8);

            System.out.println("Read: " + readAll(List.of(file)));

            Path missing = file.resolveSibling(
                    "missing-" + file.getFileName());
            try {
                readAll(List.of(missing));
            } catch (IOException failure) {
                System.out.println("Missing file reported: "
                        + (failure instanceof java.nio.file.NoSuchFileException));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ReadFilesWithStream.java
java ReadFilesWithStream
```

**Stdout trong tình huống chuẩn (tệp missing thực sự không tồn tại):**

```text
Read: [hello]
Missing file reported: true
```

Đầu vào `file` là một tệp tạm được tạo ở runtime. Biến `missing` trỏ tới một tên khác trong cùng thư mục, có tiền tố `missing-`; ví dụ giả định nó không tồn tại để sinh `NoSuchFileException`. Trong hệ thống thật, đối tượng file có thể bị tạo bởi một quá trình khác đồng thời, vì vậy khi viết test nghiêm ngặt nên chọn một thư mục tạm riêng và chủ động kiểm soát fixture.

### Một đánh đổi đáng lưu ý

Trong `readAll`, việc `throw failure.getCause()` giữ nguyên kiểu `IOException` gốc, nhưng **loại bỏ thông điệp ngữ cảnh của wrapper** khỏi exception được ném ra cho caller. Nếu ngữ cảnh đường dẫn là thông tin quan trọng, có thể:

- Khai báo API trả `UncheckedIOException` và để wrapper được truyền nguyên vẹn.
- Ném một `IOException` mới với message chứa ngữ cảnh và `failure.getCause()` làm cause.
- Ghi ngữ cảnh qua logging/diagnostics thích hợp trước khi quyết định lan truyền lỗi.

Không nên “unwrap” theo thói quen nếu nó khiến người vận hành không biết file nào lỗi. Chọn kiểu exception ở **ranh giới API** phải phù hợp với hợp đồng công khai và nhu cầu chẩn đoán.

## 10. Một policy thiết kế: unchecked bên trong, checked ở bên ngoài

Có ba phong cách API phổ biến:

**Phong cách A — Giữ checked IOException ở API public.** Bên trong Stream API có thể tạm bọc; trước khi trả ra, unwrap hoặc tạo một `IOException` mới có thêm ngữ cảnh.

```java
static List<String> process(List<Path> paths) throws IOException {
    try {
        return paths.stream()
                .map(MyClass::readUtf8Unchecked)
                .toList();
    } catch (UncheckedIOException ex) {
        throw new IOException("Batch read failed", ex.getCause());
    }
}
```

Đây là phương thức minh họa; `MyClass::readUtf8Unchecked` cần được định nghĩa tương ứng.

**Phong cách B — API public cho phép UncheckedIOException thoát ra.** Một số API chuẩn như `Files.lines` khi duyệt lazy tuân theo cách này. Caller phải biết rằng lỗi có thể xảy ra **muộn** khi tiêu thụ stream.

**Phong cách C — Xử lý lỗi trên từng phần tử.** Thay vì để lỗi đầu tiên dừng toàn pipeline, hệ thống trả kết quả như `Success(value)` hoặc `Failure(path, error)`. Khi đó tất cả phần tử có thể được xét và báo nhiều lỗi cùng lúc. Không nên tự thay lỗi bằng `null`, `""`, `Optional.empty()` nếu nghiệp vụ cần phân biệt “file rỗng”, “không có dữ liệu” và “đọc lỗi”.

Lựa chọn phụ thuộc câu hỏi nghiệp vụ: **một lỗi có làm thất bại toàn bộ batch không**, hay người dùng muốn xem danh sách thành công và thất bại riêng?

## 11. Biến một phương thức IOException thành Function tái sử dụng

Nếu codebase có nhiều lambda cần đọc/ghi, có thể định nghĩa interface riêng khai báo `IOException`, rồi tạo adapter:

```java
@FunctionalInterface
interface IOFunction<T, R> {
    R apply(T value) throws IOException;
}

static <T, R> java.util.function.Function<T, R> unchecked(
        IOFunction<T, R> operation) {
    return value -> {
        try {
            return operation.apply(value);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    };
}
```

Đây là **đoạn minh họa trong một lớp Java**, cần `java.io.IOException`, `java.io.UncheckedIOException`. Có thể dùng:

```java
List<String> texts = paths.stream()
        .map(unchecked(path -> Files.readString(path,
                StandardCharsets.UTF_8)))
        .toList();
```

Adapter gom logic catch/bọc về một nơi. Tuy nhiên nó cũng có giới hạn:

- Không tự bổ sung ngữ cảnh về đường dẫn/record; có thể dùng adapter nhận thông điệp từ caller nếu cần.
- Chỉ bọc `IOException`. Không được bắt toàn bộ `Exception` rồi biến lỗi lập trình thành “I/O problem”.
- Nếu I/O có side effect và retry, adapter không làm thao tác trở thành idempotent.
- Về mặt đọc code, adapter giúp ngắn hơn nhưng đôi khi che mất vị trí đọc/ghi; dùng khi thực sự làm kiến trúc dễ hiểu.

Một adapter tương tự có thể xây cho `IOConsumer`, `IOPredicate` hoặc `IOBiFunction`. Không cần xây thư viện wrapper quá lớn cho vài chỗ catch riêng lẻ.

## 12. Không dùng UncheckedIOException cho mọi ngoại lệ

`UncheckedIOException` chỉ biểu đạt “đã gặp `IOException` nhưng API ở đây không khai báo checked exception”. Các trường hợp sau cần phân biệt:

| Sự cố | Loại lỗi tự nhiên | Có nên tự bọc UncheckedIOException? |
| --- | --- | --- |
| `Files.readString` bị từ chối quyền đọc | `IOException`/subclass | Có, khi phải qua lambda không throws checked |
| File biến mất lúc đang mở | `NoSuchFileException` (subclass `IOException`) | Có thể bọc như trên |
| Parser thấy số trong văn bản không hợp lệ | `NumberFormatException` | Không; đây là lỗi dữ liệu/parse, không phải IOException |
| `String.substring` dùng index sai | `IndexOutOfBoundsException` | Không; lỗi logic hoặc input |
| Null bất ngờ trong lambda | `NullPointerException` | Không; xử lý nguyên nhân null |
| Java heap hết bộ nhớ | `OutOfMemoryError` | Không; không phải IOException |
| `Files.lines` lỗi khi duyệt nội dung | `UncheckedIOException` từ API | Giữ/catch ở ranh giới phù hợp, không cần bọc lồng thêm vô ích |

Nếu một luồng báo `UncheckedIOException`, luôn xem `getCause()` để hiểu lỗi gốc, nhưng cũng giữ context của wrapper nếu có. Đừng chỉ log `failure.getMessage()` rồi bỏ stack trace và cause khi chẩn đoán lỗi khó.

## 13. Tài nguyên và ngoại lệ: try-with-resources bao quanh terminal operation

Một lỗi I/O có thể xảy ra **giữa quá trình duyệt**. Nếu stream được mở từ file hoặc directory, resource vẫn phải được đóng khi operation ném exception.

```java
try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
    lines.map(this::transform)
         .forEach(this::accept);
} catch (UncheckedIOException failure) {
    // Resource của Files.lines đã được đóng trước khi vào catch.
    handleIoError(failure.getCause());
}
```

Đây là **mã minh họa có các method theo ứng dụng** (`transform`, `accept`, `handleIoError`) chưa định nghĩa; không phải chương trình độc lập. `try-with-resources` đảm bảo gọi `close()` cho Stream, kể cả khi `map` hoặc `forEach` bị lỗi. Còn việc **close có thành công tuyệt đối hay không** vẫn tùy resource và hành vi API.

Nếu chỉ đóng stream trong nhánh thành công:

```java
// Sai về lifecycle nếu terminal operation ném exception.
Stream<String> lines = Files.lines(path);
lines.forEach(System.out::println);
lines.close();
```

thì lúc `forEach` ném lỗi, câu lệnh `close()` không chạy. Hãy mở và dùng trong cùng một `try-with-resources`.

### Suppressed exception

Khi thân `try` phát sinh exception, rồi việc đóng resource cũng phát sinh exception, Java gắn lỗi đóng thành **suppressed** của lỗi chính theo quy tắc try-with-resources. Nếu bọc hoặc tạo mới exception tùy tiện ở tầng cao mà không giữ nguyên chain, thông tin dọn dẹp có thể bị mất. Khi điều tra lỗi I/O nhiều lớp, xem cả `getCause()` và `getSuppressed()`.

## 14. Stream tuần tự, parallel stream và khả năng quan sát lỗi

Trong một **sequential stream**, các thao tác thực hiện theo cơ chế duyệt tuần tự của pipeline. Khi lỗi đầu tiên được ném, terminal operation có thể dừng; các phần tử sau không nhất thiết được xử lý.

Trong **parallel stream**:

- Nhiều file có thể được đọc đồng thời.
- Thứ tự bắt đầu/hoàn tất I/O có thể khác encounter order.
- Một exception ở một nhánh không có nghĩa mọi task đã dừng tức thời; một số thao tác khác có thể đã tạo side effect.
- Cần cân nhắc tài nguyên như file descriptor, ổ đĩa, network, và giới hạn concurrency.

Không nên chuyển sang `parallel()` chỉ vì I/O chậm. Với I/O có số lượng task lớn, chọn mô hình concurrency/queue phù hợp, giới hạn số request đồng thời và xác định rõ cơ chế hủy/đóng resource.

Các pipeline kết hợp `parallel`, side effects, retry và khôi phục dữ liệu thường dễ kiểm soát bằng một kiến trúc executor/service rõ ràng hơn là dùng lambda bắt lỗi ở khắp nơi.

## 15. Ghi lỗi có ngữ cảnh và tránh rò rỉ thông tin

Một thông điệp exception hữu ích cần mô tả **thao tác nào** thất bại:

```java
throw new UncheckedIOException(
        "Reading configuration: " + path.getFileName(), cause);
```

Đây là ví dụ minh họa, không phải class độc lập. Không phải mọi đường dẫn đầy đủ, tên người dùng hoặc nội dung file đều phù hợp để in ra log công khai. Khi logging trong ứng dụng nhiều người dùng, xem xét việc tránh lộ path tuyệt đối hoặc dữ liệu nhạy cảm.

Log **một lần ở nơi có ngữ cảnh thích hợp** và giữ nguyên exception cause. Nếu mọi tầng vừa log vừa ném lại, cùng một lỗi có thể xuất hiện nhiều lần mà không thêm thông tin hữu ích.

Một chiến lược hợp lý:

- Tầng I/O thấp: bọc `IOException` với thao tác/nguồn hữu ích.
- Tầng nghiệp vụ: quyết định retry, fail-fast hoặc ghi nhận thất bại theo từng phần tử.
- Tầng API/CLI: chuyển lỗi thành response/error code và thông điệp dành cho người dùng; giữ stack trace cho kênh diagnostics phù hợp.

## 16. Những lỗi thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Gọi `Files.readString(path)` trực tiếp trong `map` | Lambda không biên dịch vì checked IOException | catch/bọc `UncheckedIOException` hoặc xử lý I/O ngoài stream |
| Chỉ catch `IOException` bên ngoài terminal operation | Có thể không bắt lỗi `UncheckedIOException` từ quá trình duyệt lazy | Bắt đúng kiểu ở ranh giới tương ứng |
| Cho rằng `map` chạy ngay khi pipeline được khai báo | Lỗi có thể chỉ xuất hiện khi `toList()`/terminal operation | Hiểu lazy evaluation |
| Không đóng `Files.lines`/`Files.walk` | Có thể rò rỉ file/directory handles | `try-with-resources` quanh toàn bộ terminal operation |
| Nghĩ `count()` tự đóng stream | Resource vẫn cần close | Dùng `try-with-resources` |
| Dùng `reader.lines()` rồi chỉ đóng Stream API | Reader do caller sở hữu vẫn cần đóng | Quản lý vòng đời `BufferedReader` |
| Bọc bất kỳ `Exception` vào `UncheckedIOException` | Sai kiểu hoặc che lỗi không thuộc I/O | Chỉ bọc `IOException` |
| Catch rồi trả chuỗi rỗng cho lỗi đọc file | Mất phân biệt giữa file rỗng và đọc lỗi | Truyền lỗi hoặc model hóa kết quả lỗi |
| Unwrap và vứt hết message chứa path | Khó biết tệp nào lỗi | Giữ context trong wrapper hoặc `IOException` mới có cause |
| Dùng `peek` cho một side effect I/O bắt buộc | Có thể không chạy do short-circuit/optimization | Dùng phép xử lý rõ ràng, không dựa vào peek |
| Dùng `parallel()` không giới hạn khi duyệt file | Quá nhiều I/O đồng thời, khó đoán side effects | Giới hạn đồng thời, chọn kiến trúc phù hợp |
| Cho rằng xử lý thành công vài phần tử đầu là batch thành công | Lỗi giữa chừng làm pipeline dừng | Kiểm tra terminal operation hoàn tất; quyết định rollback/partial results |
| Đóng file stream chỉ sau `forEach` | Ngoại lệ ngăn câu lệnh close | Đặt cả xử lý và resource trong try-with-resources |
| Chỉ log wrapper message rồi bỏ cause | Mất vị trí lỗi gốc | Truyền/ghi cause và suppressed đúng cách |

## 17. Khi nào nên dùng vòng for thay vì Stream API?

Stream API phù hợp khi phép biến đổi dữ liệu gọn, các phần tử phần lớn độc lập và chính sách lỗi đơn giản. Nhưng nếu yêu cầu:

- Retry từng file với backoff rõ ràng;
- Tổng hợp mọi lỗi và vẫn tiếp tục phần tử khác;
- Transaction, rollback hoặc tài nguyên liên quan nhiều bước;
- Phải đóng nhiều nguồn theo thứ tự phức tạp;
- Phải bảo đảm tất cả phần tử được xử lý, kể cả khi một phần tử lỗi;
- Cần báo tiến độ chính xác, xử lý hủy hoặc resource contention;

thì một vòng `for` thường đơn giản và minh bạch hơn:

```java
List<String> contents = new ArrayList<>();
for (Path path : paths) {
    contents.add(Files.readString(path, StandardCharsets.UTF_8));
}
```

Đây là **đoạn minh họa** nằm trong phương thức có `throws IOException`, cần `List`, `ArrayList`, `Path`, `Files` và `StandardCharsets`. Không cần wrapper unchecked vì vòng `for` cho phép phương thức khai báo `throws IOException` trực tiếp.

Không có yêu cầu rằng dùng Stream API sẽ nhanh hơn, gọn hơn hay đúng hơn trong mọi bài toán I/O. Chọn công cụ giúp **thể hiện chính xác hành vi lỗi và vòng đời tài nguyên**.

## 18. Bài tập thực hành

1. Viết lambda `path -> Files.readString(path)` trong `Stream.map` rồi biên dịch. Giải thích vì sao compiler yêu cầu xử lý checked exception.
2. Chạy `UncheckedIoPipelineDemo` và thêm `"gamma"` sau `"broken"`. Giải thích vì sao kết quả list thứ hai không được trả về khi lỗi xảy ra.
3. Tạo một phương thức `readUtf8Unchecked(Path)`, thêm message chứa tên file và kiểm tra `getCause()` vẫn là `IOException`.
4. Chạy `FileLinesDemo` trên Java 17 và bổ sung dòng `"  "`; so sánh `isBlank()` với `isEmpty()`.
5. Sửa `FileLinesDemo` để đọc một tệp không tồn tại; phân biệt lỗi xuất hiện lúc `Files.lines` với lỗi xuất hiện khi terminal operation duyệt source đã mở.
6. Viết một chương trình `Files.list(directory)` có `try-with-resources`, in tên file sau khi `sorted()`, rồi so sánh với việc không đóng stream.
7. Thực hành `BufferedReader.lines()` bên trong một `try (BufferedReader reader = ...)`; giải thích owner là reader hay stream trả về.
8. Chạy `ReadFilesWithStream`, thay `throw failure.getCause()` bằng `throw new IOException("Batch failed", failure)` và quan sát cause chain trong stack trace.
9. Viết adapter `IOFunction<T,R>`/ `unchecked`, dùng cho `Files.readString`, sau đó thêm unit test bảo đảm lỗi `IllegalArgumentException` không bị đổi thành `UncheckedIOException`.
10. Thiết kế một kiểu `Result<T>` chứa thành công hoặc `IOException` theo từng file. So sánh hành vi với pipeline fail-fast khi một trong mười tệp không đọc được.
11. Dùng `limit(1)` và `findFirst()` để chứng minh pipeline có thể không đọc mọi phần tử; giải thích tại sao không dùng `peek` cho I/O bắt buộc.
12. Đề xuất cách giới hạn đồng thời nếu cần kiểm tra 10.000 tệp nhưng chỉ được phép mở tối đa 16 tệp một lúc.

## 19. Tóm tắt

`UncheckedIOException` là cầu nối hợp lý giữa thế giới **I/O checked exceptions** và **Stream API/lambda không khai báo checked exceptions**. Nó giữ lại `IOException` gốc qua `getCause()` mà vẫn cho phép lỗi lan truyền qua `Function`, `Predicate`, `Consumer` hoặc các pipeline lazy.

Ba nguyên tắc quan trọng:

1. **Bọc đúng biên:** chỉ chuyển `IOException` sang `UncheckedIOException` khi cần, kèm ngữ cảnh hữu ích; tại API bên ngoài, chọn giữ unchecked hay chuyển lại checked theo hợp đồng.
2. **Nhớ tính lazy:** lỗi có thể xảy ra khi terminal operation chạy, không phải lúc pipeline được tạo; short-circuit và parallelism ảnh hưởng phần dữ liệu được xử lý.
3. **Giữ vòng đời resource:** `Files.lines`, `Files.list`, `Files.walk`, `Files.find` có thể mở tài nguyên cần đóng. `try-with-resources` bao quanh **toàn bộ thời gian tiêu thụ stream**; với `BufferedReader.lines()`, owner thường là reader mà caller đã mở.

Nếu một quy trình I/O cần báo cáo nhiều lỗi, retry, rollback hoặc quản lý resource phức tạp, hãy cân nhắc dùng vòng `for` hay một abstraction riêng. Mục tiêu là **đúng ngữ nghĩa dữ liệu, chính sách lỗi và ownership**, không phải sử dụng Stream API bằng mọi giá.

### Tài liệu tham khảo

- Java SE 17 API: [`UncheckedIOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/UncheckedIOException.html), [`IOException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html).
- Java SE 17 API: [`Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`BufferedReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html).
- Java SE 17 API: [`Stream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html), [`Function`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html), [`AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-009">Đọc hoặc ghi dở dang khi có ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc hoặc ghi dở dang khi có ngoại lệ trong Java I/O

## 1. “Đã đọc/ghi một phần” nghĩa là gì?

Một thao tác I/O thường trải qua nhiều bước: lấy byte từ nguồn, đi qua các buffer hoặc bộ mã hóa, rồi tới tệp, socket hoặc thiết bị. Nếu một bước gặp lỗi, dữ liệu có thể đã được **tiêu thụ hoặc gửi đi một phần** trước khi phương thức báo ngoại lệ.

Ví dụ:

- Đọc bản ghi dự kiến 100 byte, nhận 40 byte rồi kết nối gặp lỗi.
- Ghi 5 byte vào một luồng, ba byte đầu đến đích rồi byte thứ tư gây `IOException`.
- Ghi văn bản vào `BufferedWriter` chưa báo lỗi, nhưng `flush()` hoặc `close()` mới phát hiện đích đã hỏng.
- Tạo một tệp đầu ra rồi ứng dụng lỗi giữa chừng, khiến tệp có nội dung nhưng **chưa hoàn chỉnh**.

**Nguyên tắc:** `IOException` không tự động hoàn tác mọi byte đã đọc hoặc ghi. “Phương thức ném ngoại lệ” **không có nghĩa** “chưa có byte nào thay đổi”. Ngược lại, một lệnh `write()` trả về bình thường cũng không nhất thiết chứng minh dữ liệu đã được lưu bền vững hay phía nhận đã xử lý.

Vì vậy phải tách ba trạng thái:

| Trạng thái | Điều đã biết | Điều chưa được bảo đảm |
| --- | --- | --- |
| Đọc thành công một phần | Một lượng dữ liệu đã được đưa lên ứng dụng | Chưa chắc có đủ bản ghi/thông điệp |
| Ghi đã trả về | API đã chấp nhận thao tác theo hợp đồng tầng đó | Chưa chắc đã flush, lưu bền vững hoặc nhận được ACK |
| Ngoại lệ I/O | Thao tác không hoàn thành theo cách đã yêu cầu | Chưa chắc mọi tác động trước đó đã bị đảo ngược |

Đây là vấn đề **tính toàn vẹn dữ liệu và quản lý lỗi**, không chỉ là việc thêm một `catch`.

## 2. Đọc theo khối: trả ít byte không phải lúc nào cũng là lỗi

Một lời gọi `InputStream.read(byte[])` hoặc `read(byte[], offset, length)` có thể trả về **ít byte hơn số byte được yêu cầu**, ngay cả khi chưa hết nguồn. Chẳng hạn gọi đọc 1.024 byte nhưng nhận kết quả `37` là tình huống hợp lệ: 37 byte đầu mảng vừa được điền, các byte còn lại không thuộc dữ liệu mới đọc.

- Kết quả **dương**: số byte thực tế vừa đọc.
- Kết quả **`-1`**: EOF, nguồn báo hết dữ liệu.
- Có **`IOException`**: lỗi I/O. Không được đồng nhất lỗi này với EOF.
- Với yêu cầu độ dài **0**, kết quả có thể là `0`; không thể coi mọi `0` là EOF.

Mẫu đọc đến EOF, xử lý đúng số byte nhận được:

```java
byte[] block = new byte[4096];
int count;

while ((count = input.read(block)) != -1) {
    consume(block, 0, count);
}
```

Đây là **đoạn mã minh họa**: `input` là `InputStream` đã mở; `consume` là hàm của ứng dụng và có thể ném lỗi; cần quản lý nguồn bằng `try-with-resources` ở cấp thích hợp.

Nếu chỉ cần một bản ghi độ dài cố định, **đọc đến EOF** lại có thể là cách sai. Phải biết số byte kỳ vọng và lặp cho đủ (hoặc dùng `DataInputStream.readFully(...)`). Đọc ít byte vì chưa sẵn sàng khác với một bản ghi **bị cắt cụt**.

**Lưu ý về lỗi giữa một lần đọc khối:** Hợp đồng `InputStream` gốc cho phép một số cách cài đặt `read(byte[], off, len)` trả số byte đã nhận nếu xảy ra ngoại lệ **sau byte đầu tiên** trong cùng lần gọi; cũng có lớp đọc ném ngoại lệ trực tiếp. Do đó không giả định rằng ngoại lệ luôn xuất hiện ở vị trí chính xác byte lỗi nếu dùng đọc theo khối. Với dữ liệu quan trọng, quản lý ranh giới bản ghi và kiểm chứng độ dài của cả thông điệp.

## 3. Ví dụ Java 17+: nguồn đọc lỗi sau ba byte

Tạo file **`PartialReadDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.util.StringJoiner;

public class PartialReadDemo {
    static class FailingInputStream extends InputStream {
        private final byte[] data = {10, 20, 30, 40};
        private int position;

        @Override
        public int read() throws IOException {
            if (position == 3) {
                throw new IOException("simulated connection loss");
            }
            if (position >= data.length) {
                return -1;
            }
            return data[position++] & 0xFF;
        }
    }

    public static void main(String[] args) {
        StringJoiner received = new StringJoiner(" ");

        try (InputStream input = new FailingInputStream()) {
            int value;
            while ((value = input.read()) != -1) {
                received.add(Integer.toString(value));
            }
        } catch (IOException ex) {
            System.out.println("Lỗi đọc: " + ex.getMessage());
        }

        System.out.println("Đã nhận: " + received);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 PartialReadDemo.java
java PartialReadDemo
```

Đầu ra:

```text
Lỗi đọc: simulated connection loss
Đã nhận: 10 20 30
```

Luồng cho phép đọc ba byte đầu; lần đọc thứ tư ném `IOException`. Không có `-1`: chương trình **chưa thấy EOF hợp lệ**. Ba byte đã được ứng dụng tiếp nhận vẫn tồn tại trong `StringJoiner`; Java không tự rút lại chúng.

Nếu dữ liệu là một bản ghi gồm bốn byte thì không được âm thầm coi `10 20 30` là bản ghi hợp lệ. Cần thông báo bản ghi thiếu, hủy kết quả xử lý chưa commit hoặc đợi quy trình phục hồi theo giao thức.

## 4. Ghi dở dang: lỗi không có nghĩa là đích vẫn nguyên vẹn

`OutputStream.write(int)` ghi một byte hoặc ném `IOException`. Khi ứng dụng ghi **nhiều byte**, những byte đầu có thể đã được ghi trước khi sự cố xuất hiện. Hợp đồng của `OutputStream` không cung cấp rollback.

Ví dụ độc lập **`PartialWriteDemo.java`** tạo đích mô phỏng chỉ chấp nhận ba byte:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;

public class PartialWriteDemo {
    static class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream destination;
        private final int limit;
        private int written;

        LimitedOutputStream(ByteArrayOutputStream destination, int limit) {
            this.destination = destination;
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            if (written >= limit) {
                throw new IOException("simulated disk failure");
            }
            destination.write(value);
            written++;
        }
    }

    public static void main(String[] args) {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (OutputStream output = new LimitedOutputStream(destination, 3)) {
            output.write(new byte[]{1, 2, 3, 4, 5});
        } catch (IOException ex) {
            System.out.println("Lỗi ghi: " + ex.getMessage());
        }

        System.out.println("Byte còn lại: "
                + HexFormat.of().formatHex(destination.toByteArray()));
    }
}
```

Lệnh chạy:

```bash
javac -encoding UTF-8 PartialWriteDemo.java
java PartialWriteDemo
```

Kết quả:

```text
Lỗi ghi: simulated disk failure
Byte còn lại: 010203
```

Trong ví dụ, `OutputStream.write(byte[])` kế thừa từ `OutputStream` gọi `write(int)` lần lượt; sau byte thứ ba, byte thứ tư gây lỗi. **Ba byte `01 02 03` vẫn còn trong đích**. Đây là mô phỏng để thấy một thao tác “ghi mảng” không đồng nghĩa với transaction ghi cả mảng hoặc không ghi gì.

Các triển khai chuyên biệt có thể có cách ghi theo khối khác. Không suy từ ví dụ rằng mọi `write(byte[])` luôn thực hiện từng byte, hoặc ngoại lệ luôn cho biết chính xác số byte đã ghi. Với tệp hoặc socket thật, trạng thái phía đích có thể chỉ xác định được qua kiểm chứng hoặc giao thức xác nhận.

## 5. Buffer khiến thời điểm phát hiện lỗi bị trễ

`BufferedOutputStream` và `BufferedWriter` có thể giữ dữ liệu trong RAM. Khi gọi `write(...)`, dữ liệu được đưa vào **buffer** trước; lỗi ở đích bên dưới đôi khi chỉ xuất hiện khi:

- Buffer đầy và tự đẩy xuống.
- Chương trình gọi `flush()`.
- Chương trình gọi `close()`, thường có bước đẩy dữ liệu còn lại xuống trước khi đóng.

Ví dụ độc lập **`BufferedFailureDemo.java`**:

```java
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;

public class BufferedFailureDemo {
    static class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream destination;
        private int written;

        LimitedOutputStream(ByteArrayOutputStream destination) {
            this.destination = destination;
        }

        @Override
        public void write(int value) throws IOException {
            if (written == 3) {
                throw new IOException("simulated device failure");
            }
            destination.write(value);
            written++;
        }
    }

    public static void main(String[] args) {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (BufferedOutputStream output = new BufferedOutputStream(
                new LimitedOutputStream(destination), 8)) {
            output.write(new byte[]{1, 2, 3, 4, 5});
            System.out.println("write() chưa báo lỗi");
            output.flush();
        } catch (IOException ex) {
            System.out.println("Lỗi: " + ex.getMessage());
        }

        System.out.println("Byte đã xuống đích: "
                + HexFormat.of().formatHex(destination.toByteArray()));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 BufferedFailureDemo.java
java BufferedFailureDemo
```

Đầu ra:

```text
write() chưa báo lỗi
Lỗi: simulated device failure
Byte đã xuống đích: 010203
```

Vì buffer có sức chứa 8 byte và ứng dụng mới ghi 5 byte, `write()` hoàn tất mà chưa gọi luồng dưới. `flush()` bắt đầu chuyển byte tới `LimitedOutputStream` và gặp lỗi sau 3 byte.

**Hệ quả:** Thành công của `BufferedWriter.write(...)` hay `BufferedOutputStream.write(...)` có thể mới chỉ là thành công **ghi vào bộ đệm**. Phải xử lý ngoại lệ ở cả `flush()` và `close()`. `flush()` cũng không đồng nghĩa dữ liệu đã được đồng bộ an toàn xuống thiết bị lưu trữ hoặc phía nhận đã xử lý: đây là các cam kết khác nhau ở những tầng khác nhau.

Nếu lỗi xảy ra lúc flush, buffer có thể vẫn giữ dữ liệu chưa được chuyển, hoặc lớp dưới đã chấp nhận một phần. Gọi lại cùng `write(...)` mù quáng dễ tạo **dữ liệu lặp**.

## 6. Bản ghi có cấu trúc: đủ byte mới được coi là hoàn chỉnh

Xét định dạng nhị phân:

```text
[int payloadLength: 4 byte][payload: đúng payloadLength byte]
```

Phía đọc phải có chính xác bốn byte cho `int`, sau đó có chính xác `payloadLength` byte cho payload. Cả hai bước có thể thất bại dở dang.

`DataInputStream` hỗ trợ:

- `readInt()`: cần đủ bốn byte để tạo `int`, nếu nguồn hết sớm sẽ ném `EOFException`.
- `readFully(byte[])`: cố đọc đủ độ dài mảng, nếu EOF trước khi đủ sẽ ném `EOFException`. **Mảng có thể đã được ghi một phần trước khi có ngoại lệ**.
- `read(byte[])`: có thể trả ít byte hơn số yêu cầu, nên không dùng một lần gọi thay cho `readFully`.

Ví dụ **`TruncatedRecordDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.HexFormat;

public class TruncatedRecordDemo {
    public static void main(String[] args) throws IOException {
        byte[] truncated = {0, 0, 0, 3, 0x41, 0x42};

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(truncated))) {
            int length = input.readInt();
            if (length < 0 || length > 1024) {
                throw new IOException("Invalid payload length");
            }

            byte[] payload = new byte[length];
            try {
                input.readFully(payload);
                System.out.println("Bản ghi hoàn chỉnh");
            } catch (EOFException ex) {
                System.out.println("Thiếu byte của payload");
                System.out.println("Không commit: "
                        + HexFormat.of().formatHex(payload));
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 TruncatedRecordDemo.java
java TruncatedRecordDemo
```

Đầu ra:

```text
Thiếu byte của payload
Không commit: 414200
```

Payload yêu cầu 3 byte nhưng trong luồng chỉ còn `41 42`. `readFully` đã ghi hai byte vào mảng trước khi gặp EOF; phần tử thứ ba giữ giá trị mặc định `00`. **Không được dùng mảng `41 42 00` làm dữ liệu thật**: byte `00` ở đây là mặc định bộ nhớ chứ không phải byte thứ ba từ nguồn.

Điểm quan trọng: `readFully` giúp phát hiện bản ghi chưa đủ, nhưng **không hoàn tác** những thay đổi đã có trong mảng đích. Chỉ công bố bản ghi sang phần còn lại của ứng dụng **sau khi** kiểm tra xong độ dài và tính hợp lệ.

## 7. Phân biệt EOF, IOException và dữ liệu sai nhưng đọc không báo lỗi

| Tình huống | Ý nghĩa | Hành động phù hợp |
| --- | --- | --- |
| `read() == -1` | Nguồn báo đã hết byte | Kiểm tra EOF có được schema cho phép ở vị trí này không |
| `EOFException` | Phương thức cần thêm byte cho một giá trị/bản ghi nhưng không còn | Coi là dữ liệu cắt cụt trừ khi giao thức định nghĩa khác |
| `IOException` | Trục trặc I/O, ví dụ thiết bị hoặc socket lỗi | Giữ nguyên bối cảnh lỗi; xác định phần dữ liệu đã xử lý và chính sách phục hồi |
| Đủ số byte nhưng checksum sai | Dữ liệu đã đến nhưng không đạt kiểm tra toàn vẹn | Bác bỏ bản ghi, không công bố dữ liệu |
| Đủ byte nhưng trường sai thứ tự | Parser diễn giải sai, có thể không ném ngoại lệ | Kiểm tra schema, version, kiểu và offset |

Không viết:

```java
try {
    parseRecord(input);
} catch (IOException ex) {
    // bỏ qua và tiếp tục dùng kết quả đã parse một nửa
}
```

Đây là **mã minh họa một anti-pattern**. Nếu bộ phân tích đã cập nhật đối tượng nghiệp vụ trước khi đọc đủ bản ghi, việc bỏ qua ngoại lệ có thể để lại trạng thái không nhất quán.

Cách tốt hơn là đọc vào **biến cục bộ/đối tượng tạm**, xác thực hết các trường và checksum (nếu có), rồi mới thêm vào danh sách hoặc thay đổi trạng thái chính thức. Không công bố một phần bản ghi khi chưa đạt điều kiện hoàn chỉnh.

## 8. Ghi tệp an toàn hơn: tệp tạm rồi công bố

Ghi trực tiếp vào đường dẫn đích có thể khiến người đọc nhìn thấy **tệp mới chỉ ghi một phần** nếu chương trình hoặc thiết bị lỗi. Với trường hợp cần thay thế trọn vẹn nội dung tệp, cách thường dùng là:

1. Tạo **tệp tạm trong cùng thư mục/hệ thống tệp** với đích.
2. Ghi **toàn bộ** nội dung mới vào tệp tạm.
3. Hoàn tất writer và kiểm tra dữ liệu nếu cần.
4. Chuyển tên (rename/move) tệp tạm thành tên đích khi dữ liệu đã sẵn sàng.
5. Nếu có lỗi trước bước công bố, xóa tệp tạm; giữ tệp đích cũ nếu vẫn có thể.

Đoạn minh họa Java 17+:

```java
Path target = Path.of("result.txt").toAbsolutePath();
Path directory = target.getParent();
Path temporary = Files.createTempFile(directory, ".result-", ".tmp");

try {
    Files.writeString(temporary, text, StandardCharsets.UTF_8);
    // Khi định dạng yêu cầu, kiểm tra nội dung tệp temporary tại đây.
    Files.move(temporary, target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
} finally {
    Files.deleteIfExists(temporary);
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập. Cần import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.StandardCopyOption`, `java.nio.charset.StandardCharsets`; khai báo `String text`, xử lý `IOException` và quyết định chính sách ghi đè theo nghiệp vụ. Đừng chạy với đường dẫn dữ liệu quan trọng khi chưa kiểm tra quyền sở hữu và nhu cầu thay thế.

**Giới hạn phải hiểu:**

- `ATOMIC_MOVE` **có thể không được filesystem hỗ trợ**, khi đó có thể nhận `AtomicMoveNotSupportedException`. Không âm thầm bỏ cờ atomic rồi tuyên bố dữ liệu vẫn được công bố nguyên khối.
- Khi `ATOMIC_MOVE` đi cùng các option khác, chi tiết thay thế đích tồn tại có thể phụ thuộc filesystem/provider. Cần kiểm tra hợp đồng và thử nghiệm trên môi trường triển khai.
- Atomic rename liên quan đến **tính nhìn thấy nguyên khối của tên tệp**, không tự bảo đảm **durability khi mất điện**. Khi yêu cầu độ bền dữ liệu chặt chẽ, cần cơ chế sync/force và quản lý thư mục phù hợp với hệ điều hành/filesystem.
- Nếu ứng dụng viết nhiều tệp hoặc đồng thời thay đổi database, rename **không tạo transaction xuyên nhiều tài nguyên**.
- Khi move lỗi hoặc có tác nhân ghi đồng thời, phải bảo đảm cách xử lý không xóa nhầm tệp người khác và không ghi đè phiên bản mới.

Với tệp nhỏ và không yêu cầu tính nguyên khối, `Files.writeString(...)` trực tiếp có thể đủ. Nhưng khi yêu cầu “hoặc tệp cũ nguyên vẹn, hoặc tệp mới hoàn chỉnh”, nên thiết kế đường commit rõ ràng thay vì chỉ dựa vào `try-with-resources`.

## 9. `try-with-resources` và lỗi trong `close()`

`try-with-resources` giúp đóng tài nguyên ngay cả khi mã bên trong ném ngoại lệ. Tuy nhiên:

- Đóng tài nguyên **không rollback** byte đã ghi.
- `close()` tự nó có thể ném `IOException`, đặc biệt khi đẩy buffer đang chờ xuống tầng dưới.
- Nếu khối `try` đang có một ngoại lệ chính và `close()` cũng lỗi, Java giữ ngoại lệ của khối làm **primary exception**, còn lỗi đóng thường nằm trong `getSuppressed()`.
- Không nên ghi đè hoặc bỏ mất primary exception vì chỉ quan tâm lỗi close.

Ví dụ độc lập **`SuppressedCloseDemo.java`**:

```java
import java.io.IOException;

public class SuppressedCloseDemo {
    static class FailingResource implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource resource = new FailingResource()) {
            throw new IOException("write failed");
        } catch (IOException ex) {
            System.out.println("Primary: " + ex.getMessage());
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("Suppressed: "
                        + suppressed.getMessage());
            }
        }
    }
}
```

Chạy:

```bash
javac SuppressedCloseDemo.java
java SuppressedCloseDemo
```

Đầu ra:

```text
Primary: write failed
Suppressed: close failed
```

Khi gỡ lỗi thực tế, lưu cả primary exception và suppressed exceptions trong log phù hợp. Đừng ghi log trùng lặp hoặc vô tình làm lộ dữ liệu nhạy cảm, token hoặc toàn bộ payload khách hàng.

## 10. Retry và nguy cơ nhân đôi dữ liệu

Khi ghi sang socket, database gateway hoặc dịch vụ từ xa, không phải lúc nào phía gửi cũng biết **đã gửi/nhận thành công bao nhiêu byte** sau lỗi:

```text
Client                       Server
  | ---- message bytes ---->  |
  | ---- message bytes --X    |  lỗi kết nối ở phía client
  |                            |
  |  Server có thể đã nhận/    |
  |  xử lý một phần hoặc đủ.   |
```

Gửi lại toàn bộ request mà không có cơ chế chống trùng có thể tạo hai giao dịch. Ngược lại, chỉ gửi phần còn thiếu mà không biết chính xác offset đã được xác nhận cũng có thể làm hỏng stream.

Các cách tiếp cận tùy giao thức:

- **Framing**: có tiền tố độ dài, message ID, version và checksum/hash để nhận biết ranh giới và tính hợp lệ.
- **Idempotency key**: phía nhận ghi nhận ID duy nhất và không thực thi cùng một tác vụ hai lần nếu nhận lại request. Khả năng chống trùng phải được triển khai phía nhận.
- **ACK theo thông điệp**: chỉ coi request hoàn tất khi nhận được phản hồi theo giao thức; một ACK cũng có thể bị mất, nên vẫn cần cơ chế chống trùng.
- **Resume với offset đã xác nhận**: chỉ thực hiện khi giao thức hỗ trợ vị trí byte, checksum và trạng thái phiên rõ ràng; không tự đoán offset từ số byte client đã đưa cho `write()`.
- **Retry có giới hạn**: thời gian chờ, backoff và quy tắc lỗi được phép retry. Một exception không đủ để xác định thao tác trước có an toàn để lặp lại không.

Với việc chuyển file lớn, có thể ghi ra một tên tạm ở server, kiểm tra độ dài và checksum rồi mới công bố; không cho phía khác đọc tài liệu đang nhận dở.

## 11. Khi dùng `Reader`, `Writer` và charset

Dữ liệu ký tự cũng có thể dở dang:

- `Reader.read(char[])` trả số `char` thực đọc. Nó không bảo đảm lấp đầy mảng.
- `BufferedReader.readLine()` có thể trả một dòng cuối không có newline nếu **EOF hợp lệ** xảy ra sau nội dung dòng. Nhưng nếu nguồn gặp `IOException` giữa dòng, không được tự coi đó là một record hoàn chỉnh.
- `OutputStreamWriter` chuyển `char` sang byte theo charset. Một ký tự Unicode có thể tạo nhiều byte UTF-8, nên đứt luồng có thể để lại **chuỗi byte UTF-8 không hoàn chỉnh**.
- `BufferedWriter` có thể giữ ký tự chưa gửi xuống bộ mã hóa. Lỗi ở `close()` hoặc `flush()` có thể để lại đích chưa hoàn chỉnh.
- Với `CharsetDecoder` cấu hình `CodingErrorAction.REPORT`, chuỗi byte không hợp lệ có thể gây lỗi mã hóa/giải mã được báo rõ, thay vì âm thầm chèn ký tự thay thế.

Một dữ liệu text là một **tài liệu hoặc bản ghi có cấu trúc**, không chỉ là tập hợp các ký tự. Ví dụ CSV có thể đang ở giữa chuỗi có dấu ngoặc kép; JSON có thể thiếu dấu đóng ngoặc. Bản thân việc đọc được `String` chưa chứng minh tài liệu hợp lệ. Hãy parse và chỉ công bố dữ liệu khi cú pháp và kiểm tra nghiệp vụ đã hoàn tất.

Với nguồn mạng, dòng cuối chưa có newline có thể vẫn là nội dung hợp lệ nếu protocol dùng EOF để đánh dấu kết thúc; nhưng nếu luồng bị lỗi giữa dòng, không thể khẳng định đó là EOF chuẩn. Ranh giới hợp lệ phải do **giao thức** định nghĩa.

## 12. Theo dõi tiến độ và phục hồi có kiểm soát

Khi ghi/đọc khối lớn, theo dõi số byte xử lý **đã được xác nhận ở tầng thích hợp**, ví dụ:

```java
long totalRead = 0;
byte[] buffer = new byte[8192];
int count;

while ((count = input.read(buffer)) != -1) {
    process(buffer, 0, count);
    totalRead += count;
}
```

Đây là **đoạn minh họa**, cần `input` và `process` hợp lệ. `totalRead` chỉ cho biết **byte ứng dụng đã xử lý sau `process` thành công**, không tự chứng minh dữ liệu đã được commit ở nơi khác. Nếu `process` ném lỗi sau khi xử lý một phần block, biến đếm có thể chưa phản ánh đúng mọi tác động đã diễn ra. Thiết kế cập nhật offset và commit phải bám vào ngữ nghĩa thao tác thực tế.

Khi có ngoại lệ, nên thu thập đủ ngữ cảnh để gỡ lỗi:

1. Tên thao tác: đọc header, đọc payload, flush output, close, rename...
2. Bản ghi/chunk số mấy, offset kỳ vọng, số byte xác nhận đã xử lý.
3. Độ dài và checksum mong đợi, kết quả kiểm tra thực tế.
4. Nguyên nhân ngoại lệ và suppressed exception nếu có.
5. Có thể tiếp tục trên cùng stream hay phải mở lại phiên mới.
6. Kết quả đang là **tạm thời**, đã **commit**, hay **không xác định**.

Không nhất thiết log nội dung byte thô hoặc toàn bộ file, nhất là khi dữ liệu có thông tin riêng tư. Thông thường metadata kỹ thuật và mã lỗi đã đủ hỗ trợ chẩn đoán.

## 13. Sai lầm phổ biến và hướng khắc phục

| Sai lầm | Rủi ro | Cách xử lý |
| --- | --- | --- |
| `catch (IOException)` rồi coi như EOF | Dùng dữ liệu thiếu thành bản ghi hợp lệ | Phân biệt kết thúc đúng schema với thất bại |
| Tin `read(byte[])` luôn lấp đầy mảng | Byte cũ/byte mặc định lẫn vào kết quả | Dùng số byte trả về hoặc `readFully` |
| Bắt `EOFException` và vẫn dùng mảng đang đọc | Mảng có thể đã bị sửa một phần | Chỉ công bố payload sau khi hoàn tất |
| Tin `write(byte[])` ném lỗi nghĩa là 0 byte đã ghi | File/socket có thể đã đổi một phần | Xem kết quả là không xác định; kiểm chứng hoặc thiết kế commit |
| Bỏ qua lỗi `flush()`/`close()` | Dữ liệu bị kẹt hoặc mất ở buffer | Xử lý lỗi ở tất cả ranh giới hoàn tất |
| Retry `write` toàn bộ ngay sau lỗi | Có thể nhân đôi bản ghi | Dùng message ID, ACK, idempotency hoặc resume có xác nhận |
| Ghi thẳng vào file đích quan trọng | File mới có thể bị cắt cụt | Ghi tạm rồi công bố theo điều kiện filesystem |
| Coi atomic rename là đồng nghĩa fsync | Mất điện vẫn có thể ảnh hưởng độ bền | Phân biệt atomic visibility với durability |
| Bỏ qua chiều dài khai báo | Cấp phát quá lớn hoặc chấp nhận bản ghi thiếu | Xác thực độ dài và byte thực đọc |
| Parser cập nhật đối tượng thật từng field | Ngoại lệ để lại trạng thái nửa chừng | Parse vào trạng thái tạm và commit khi hợp lệ |
| Mất primary exception vì lỗi `close` | Chẩn đoán sai nguyên nhân | Dùng `try-with-resources` và đọc suppressed exception |
| Đếm số byte “đã viết” bằng số lần gọi `write` | Không biết chính xác phía đích đã nhận bao nhiêu | Dựa trên xác nhận của giao thức/đích |

## 14. Checklist và bài tự luyện

**Trước khi đọc:**

- [ ] Xác định chính xác độ dài, delimiter, EOF marker hoặc quy tắc framing.
- [ ] Dự kiến và xử lý trường hợp `read(...)` trả ít hơn yêu cầu.
- [ ] Không công bố dữ liệu cho nghiệp vụ khi chưa đọc đủ và validate.
- [ ] Phân biệt EOF hợp lệ với `IOException` và EOF giữa bản ghi.
- [ ] Giới hạn độ dài/record count để tránh cấp phát không kiểm soát.

**Trước khi ghi:**

- [ ] Xác định dữ liệu có cần “all-or-nothing” ở mức file/message hay không.
- [ ] Không giả định một `write(...)` là transaction.
- [ ] Hoàn tất writer, kiểm tra `flush`, `close` và mọi ngoại lệ.
- [ ] Có cơ chế commit (temp file/rename) hoặc ACK theo giao thức nếu cần.
- [ ] Xây chiến lược retry tránh trùng và tránh mất dữ liệu.
- [ ] Phân biệt “byte đã được API nhận”, “đã đến phía bên kia”, “đã ghi bền vững” và “đã được xử lý”.

**Bài tự luyện 1 — nguồn bị cắt:** Với định dạng `[int length=5][5 byte payload]` nhưng nguồn chỉ chứa 3 byte payload, dùng `DataInputStream.readFully` và kiểm chứng `EOFException`. Giải thích vì sao mảng 5 byte có thể chứa ba byte thật và hai byte mặc định.

**Bài tự luyện 2 — đích bị lỗi:** Tạo `OutputStream` ném `IOException` sau `N` byte. Ghi dữ liệu qua `BufferedOutputStream`, thử các kích thước buffer khác nhau và dự đoán lỗi xuất hiện ở `write`, `flush` hay `close`. Không gọi lại `write` mù quáng sau khi lỗi.

**Bài tự luyện 3 — cập nhật file:** Thiết kế việc xuất một báo cáo lớn sao cho người đọc khác không nhìn thấy bản báo cáo dở dang. Mô tả rõ tệp tạm, thời điểm kiểm tra, cách công bố, chính sách khi `ATOMIC_MOVE` không hỗ trợ và yêu cầu durability có hay không.

**Ghi nhớ:** I/O là quá trình có thể **thành công một phần**. Java báo lỗi qua ngoại lệ nhưng không tự hoàn tác các tác động trước đó. Muốn an toàn, phải xác định ranh giới bản ghi, kiểm tra đủ byte, tách kết quả tạm khỏi dữ liệu đã commit, xử lý lỗi buffer/close, và thiết kế phục hồi hoặc retry theo giao thức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-and-errors-010">Ngắt, đóng từ luồng khác và hành vi phụ thuộc loại I/O</a>

<details>
<summary>Xem chi tiết</summary>

# Ngắt, đóng từ luồng khác và hành vi phụ thuộc loại I/O trong Java

Một luồng Java có thể đang bị chặn trong `read()`, `write()`, `accept()` hoặc `select()` khi luồng khác yêu cầu dừng chương trình. Lúc này có nhiều cơ chế dễ bị nhầm:

- **`Thread.interrupt()`** đặt cờ ngắt của thread và có thể đánh thức một số thao tác chặn theo hợp đồng API.
- **`close()` từ luồng khác** đóng tài nguyên, có thể khiến thao tác I/O đang chờ kết thúc hoặc ném ngoại lệ.
- **Timeout** giới hạn thời gian chờ cho những thao tác hỗ trợ, thường không đồng nghĩa đóng tài nguyên.
- **EOF hoặc half-close** mô tả kết thúc luồng dữ liệu, không giống ngắt thread.
- **Cancellation** ở `Future`/`ExecutorService` là yêu cầu dừng tác vụ; hiệu lực của nó phụ thuộc loại tác vụ và API I/O.

**Không tồn tại một quy tắc chung rằng gọi `interrupt()` sẽ hủy ngay mọi `InputStream.read()`.** Java NIO có hợp đồng cụ thể cho `InterruptibleChannel`, trong khi một số luồng `java.io`, socket, console, file và thao tác selector hoạt động khác nhau. Bài này tập trung vào Java 17+, đặc biệt các platform thread truyền thống; những biến thể triển khai và virtual thread ở JDK mới hơn cần xem tài liệu đúng phiên bản.

## 1. Ba câu hỏi phải xác định trước khi hủy I/O

Trước khi viết logic dừng, hãy xác định:

1. **Luồng đang chặn trong API cụ thể nào?** Chẳng hạn `Socket.getInputStream().read()`, `SocketChannel.read()`, `Files.newInputStream(path).read()`, `ServerSocket.accept()` hay `Selector.select()`.
2. **Tài nguyên có thể được đóng an toàn bởi ai?** Thread xử lý có sở hữu tài nguyên không, hay tài nguyên dùng chung? Đóng tài nguyên dùng chung có thể làm hỏng các công việc khác.
3. **Điều gì là kết quả hợp lệ?** Dừng bằng ngoại lệ, trả EOF `-1`, trả số byte đã đọc, trả `0` hoặc thức dậy rồi tiếp tục vòng lặp đều mang ý nghĩa khác nhau.

Một thiết kế hủy đáng tin cậy nên mô tả rõ **tín hiệu hủy**, **cách giải phóng blocking call**, **cách làm sạch tài nguyên** và **cách báo nguyên nhân kết thúc**.

## 2. Thread.interrupt() thật sự làm gì?

`Thread.interrupt()` không ép thread chết và không “ném ngoại lệ” tùy ý vào mã đang chạy. Nó đặt **trạng thái interrupted**; với một số thao tác chờ có hợp đồng hỗ trợ ngắt, thao tác ấy sẽ dừng chờ. Ví dụ `Thread.sleep(...)`, `Object.wait(...)` và `Thread.join(...)` có thể ném `InterruptedException`, đồng thời **xóa trạng thái interrupted** khi ném ngoại lệ đó.

~~~java
// Mảnh mã minh họa: worker là một Thread đã tạo và đang chạy.
worker.interrupt();
~~~

Sau khi gọi, thread kia có dừng hay không còn phụ thuộc việc nó đang làm. Với vòng lặp CPU-bound, ứng dụng thường phải **tự kiểm tra cờ ngắt**:

~~~java
// Mảnh mã minh họa: điều kiện lặp và công việc được xác định ở nơi khác.
while (!Thread.currentThread().isInterrupted()) {
    // Thực hiện một đơn vị công việc có giới hạn.
}
~~~

`Thread.currentThread().isInterrupted()` chỉ **kiểm tra**, không xóa cờ. Ngược lại, `Thread.interrupted()` là phương thức tĩnh kiểm tra **và xóa** cờ ngắt của thread hiện tại. Vì vậy tránh gọi `Thread.interrupted()` ngẫu nhiên ở nơi không muốn tiêu thụ tín hiệu hủy.

Nếu phải bắt `InterruptedException` mà không thể truyền tiếp theo hợp đồng phương thức, cách thông dụng là phục hồi cờ và kết thúc công việc:

~~~java
try {
    worker.join();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Hủy hoặc trả quyền điều khiển cho tầng gọi thích hợp.
}
~~~

Đây là **mảnh mã minh họa**. Không nên nuốt `InterruptedException` rồi tiếp tục chờ vô hạn mà không chủ động xử lý hủy.

**Khác biệt cốt lõi:** có thể `interrupt()` khiến thao tác chặn trên một **channel** bị hủy theo hợp đồng NIO, nhưng điều đó **không chứng minh** mọi triển khai `InputStream` đều tuân theo cùng cách.

## 3. java.nio.channels.InterruptibleChannel: hợp đồng rõ ràng

`InterruptibleChannel` thuộc Java NIO đại diện cho channel vừa **có thể bị đóng từ luồng khác**, vừa **hỗ trợ ngắt khi thao tác I/O đang chặn**. Các lớp như `SocketChannel`, `ServerSocketChannel`, `FileChannel`, `Pipe.SourceChannel`, `Pipe.SinkChannel` triển khai giao diện này.

Hai trường hợp được đặc tả phân biệt rõ:

| Sự kiện tác động lên một thao tác blocking I/O trên InterruptibleChannel | Hành vi theo hợp đồng |
|---|---|
| **Thread khác gọi `channel.close()`** khi thao tác đang chặn | Thread đang chặn nhận `AsynchronousCloseException` |
| **Thread khác gọi `blockedThread.interrupt()`** khi thao tác đang chặn | Channel bị đóng; thread nhận `ClosedByInterruptException`; cờ interrupted được đặt |
| Thread đã có cờ interrupted trước khi bắt đầu blocking I/O trên channel | Channel bị đóng, nhận `ClosedByInterruptException` ngay; cờ vẫn được đặt |

`ClosedByInterruptException` **kế thừa** `AsynchronousCloseException`, còn `AsynchronousCloseException` kế thừa `ClosedChannelException`, và cả ba thuộc họ `IOException`. Nếu cần phân biệt nguyên nhân, hãy catch ngoại lệ **cụ thể hơn trước**:

~~~java
try {
    // Một lời gọi blocking read trên InterruptibleChannel đã mở.
} catch (ClosedByInterruptException ex) {
    // Được ngắt: channel đã đóng, cờ interrupt đặt.
} catch (AsynchronousCloseException ex) {
    // Bị đóng từ luồng khác trong khi chặn.
} catch (IOException ex) {
    // Những lỗi I/O còn lại.
}
~~~

Đây là **mảnh mã minh họa**, không phải chương trình độc lập. Bắt `AsynchronousCloseException` trước `ClosedByInterruptException` sẽ khiến nhánh riêng cho interrupt không bao giờ chạy.

Lưu ý:

- Ngoại lệ `ClosedByInterruptException` từ channel không phải `InterruptedException`. Đừng máy móc dự đoán trạng thái cờ qua tên ngoại lệ; trong hợp đồng `InterruptibleChannel`, cờ ngắt **được giữ ở trạng thái đặt**.
- Nếu channel đã đóng **trước** khi thread gọi `read()`, thông thường có thể nhận `ClosedChannelException` thay vì `AsynchronousCloseException`. Đây không phải lỗi logic của chương trình; hai thread thực thi cạnh tranh thời gian.
- Ngắt một channel chặn có thể **đóng hẳn channel**, không chỉ dừng một lời gọi `read` để có thể tái sử dụng ngay.
- Với channel ở chế độ **non-blocking**, lời gọi `read()`/`write()` có thể trả `0` khi chưa sẵn sàng, nên cách chờ/hủy phải xem logic vòng lặp hoặc `Selector`; không áp dụng mù quáng một kỳ vọng về blocking exception.

## 4. Ví dụ Java 17+: ngắt một Pipe.SourceChannel đang chờ đọc

Sử dụng `Pipe` trong NIO để tạo một nguồn đọc chưa có dữ liệu. Ta giữ sink mở nhưng không ghi byte nào; nhờ vậy read không gặp EOF từ việc đóng phía ghi. Thread chính yêu cầu ngắt worker, khiến channel đọc bị đóng theo hợp đồng `InterruptibleChannel`.

Tạo tệp **`InterruptChannelDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class InterruptChannelDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {
            CountDownLatch ready = new CountDownLatch(1);

            Thread worker = new Thread(() -> {
                ready.countDown();

                try {
                    source.read(ByteBuffer.allocate(1));
                    System.out.println("read completed");
                } catch (ClosedByInterruptException ex) {
                    System.out.println("ClosedByInterruptException");
                    System.out.println("interrupted="
                            + Thread.currentThread().isInterrupted());
                } catch (IOException ex) {
                    System.out.println("Unexpected I/O: "
                            + ex.getClass().getSimpleName());
                }
            });

            worker.start();
            ready.await();
            worker.interrupt();
            worker.join();

            System.out.println("channelOpen=" + source.isOpen());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 InterruptChannelDemo.java
java InterruptChannelDemo
~~~

Stdout theo hợp đồng NIO:

~~~text
ClosedByInterruptException
interrupted=true
channelOpen=false
~~~

**Tại sao kết quả ổn định?** `CountDownLatch` đảm bảo worker đã bắt đầu tác vụ trước khi main yêu cầu ngắt. Nếu main kịp `interrupt()` trước khi worker thực sự vào `source.read()`, cờ interrupted đã được đặt nên khi bắt đầu blocking read channel sẽ đóng và nhận cùng `ClosedByInterruptException`. Nếu worker đã blocking, interrupt làm thao tác ấy kết thúc với ngoại lệ tương tự.

- `source.read(ByteBuffer.allocate(1))` cần chờ vì pipe chưa có byte nào và `sink` còn mở.
- `interrupt()` không chỉ gây ngoại lệ; nó còn làm `source.isOpen()` trở thành `false`.
- `worker.join()` giúp main chờ worker kết thúc trước khi kiểm tra trạng thái channel.
- `try-with-resources` giải phóng cả hai phía pipe. Không dùng `Thread.sleep(...)` để đoán worker đã vào read; latch tránh phụ thuộc thời điểm lên lịch cho logic của demo.

Trong chương trình thật, cần bảo vệ `await()` và `join()` khỏi hủy của chính thread đang chờ: nếu bắt `InterruptedException`, thường nên phục hồi trạng thái ngắt và thực hiện cleanup theo chính sách ứng dụng.

## 5. Đóng channel từ luồng khác và vấn đề race

Khi luồng A gọi `source.close()` trong lúc luồng B chờ trên `source.read()`, `InterruptibleChannel` quy định B nhận `AsynchronousCloseException`. Nhưng nếu A đóng **trước khi** B bắt đầu `read()`, B có thể nhận `ClosedChannelException`. Vì vậy code thực tế không nên đòi một tên ngoại lệ cố định cho mọi cách sắp xếp thread.

Ví dụ độc lập **`CloseChannelDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class CloseChannelDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {
            CountDownLatch ready = new CountDownLatch(1);

            Thread worker = new Thread(() -> {
                ready.countDown();

                try {
                    source.read(ByteBuffer.allocate(1));
                    System.out.println("Read completed");
                } catch (ClosedChannelException ex) {
                    System.out.println("Stopped by channel close");
                } catch (IOException ex) {
                    System.out.println("Unexpected I/O");
                }
            });

            worker.start();
            ready.await();
            source.close();
            worker.join();

            System.out.println("channelOpen=" + source.isOpen());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CloseChannelDemo.java
java CloseChannelDemo
~~~

Stdout trong hai cách sắp xếp hợp lệ của thao tác đóng/đọc:

~~~text
Stopped by channel close
channelOpen=false
~~~

`ClosedChannelException` là lớp cha của `AsynchronousCloseException`; do đó chương trình minh họa bắt chung **cả trường hợp đóng trước đọc lẫn đóng trong lúc blocking read** và tránh phụ thuộc scheduler. `sink` còn mở cho đến cuối khối `try` nên không tạo EOF ngoài ý muốn.

Việc gọi `close()` nhiều lần trên `Channel` thông thường không có thêm tác dụng sau khi đã đóng. Tuy vậy, quyền đóng tài nguyên phải được phân định rõ: khi nhiều task dùng chung channel, việc một task hủy bằng `close` làm ảnh hưởng đến tất cả.

## 6. Socket kiểu java.net: close, read timeout và interrupt không giống nhau

`java.net.Socket` có hợp đồng riêng; không suy ra từ `FileInputStream` hay từ một bất kỳ subclass `InputStream`.

### 6.1. Đóng Socket từ thread khác

Trong Java 17, `Socket.close()` được đặc tả khiến thread đang bị chặn trong thao tác I/O trên socket đó ném `SocketException`. Đóng socket cũng đóng input và output stream gắn với nó, và socket đó không thể kết nối lại.

~~~java
// Mảnh mã minh họa: socket là java.net.Socket đang dùng bởi thread khác.
socket.close(); // Có thể đánh thức read đang bị chặn trên socket.
~~~

Đây là chiến lược hủy hữu ích khi task **sở hữu socket**. Nếu socket thuộc kết nối dùng chung, quyết định đóng phải thuộc thành phần quản lý kết nối; không để một task tùy tiện hủy công việc của task khác.

### 6.2. SO_TIMEOUT chỉ giới hạn lần chờ đọc

Với `Socket.setSoTimeout(milliseconds)` có giá trị dương, một lần `read()` qua input stream của socket chỉ chờ tối đa khoảng thời gian đó theo quy tắc socket. Khi hết thời gian, thao tác ném `SocketTimeoutException` nhưng **socket vẫn hợp lệ** và có thể tiếp tục dùng.

~~~java
// Mảnh mã minh họa: socket là Socket đang kết nối hợp lệ.
socket.setSoTimeout(2_000);

try {
    int value = socket.getInputStream().read();
    // value nằm trong 0..255 hoặc -1 ở EOF.
} catch (java.net.SocketTimeoutException ex) {
    // Hết thời gian chờ đọc; socket không tự bị đóng.
}
~~~

Đặt timeout trước thao tác blocking. Giá trị `0` của `SO_TIMEOUT` nghĩa là **không có giới hạn thời gian đọc** theo option này. `SocketTimeoutException` là subclass của `InterruptedIOException` nhưng **không nên đồng nhất timeout với việc thread bị `interrupt()`**.

Timeout của một lần read không tự thiết lập **deadline cho toàn bộ request**, bởi ứng dụng có thể thực hiện nhiều lượt đọc và mỗi lượt đều có một cửa sổ chờ mới. Muốn giới hạn tổng thời gian, phải theo dõi deadline hoặc sử dụng abstraction có thời hạn ở tầng thích hợp.

### 6.3. Socket qua SocketChannel có quy tắc NIO

Một `Socket` được tạo từ `SocketChannel` (chẳng hạn qua `SocketChannel.socket()`) có channel liên kết. Khi stream của nó ủy quyền thao tác cho channel, hành vi blocking interruption cũng tuân theo channel: ngắt thread đọc có thể đóng underlying channel và gây `ClosedByInterruptException`.

Ngược lại, không được khẳng định **mọi socket stream truyền thống trên mọi phiên bản Java đều hủy ngay bởi `Thread.interrupt()`**. Trên Java 17 với platform thread và socket không gắn channel, hãy dùng những biện pháp được tài liệu xác nhận như `Socket.close()`, `SO_TIMEOUT`, quản lý kết nối và kiểm thử trên môi trường đích. Các JDK có virtual thread từ Java 21 bổ sung những hành vi ngắt khác tùy loại socket/triển khai, cần tra tài liệu của JDK đó.

### 6.4. Half-close và EOF

`Socket.shutdownInput()` đặt phía đầu vào về trạng thái kết thúc, các lần đọc sau trả `-1` theo hợp đồng. `Socket.shutdownOutput()` dừng phía xuất, với TCP báo kết thúc gửi theo giao thức trong khi phía nhận vẫn có thể hoạt động. Đây là các thao tác **half-close**, không giống đóng toàn bộ socket bằng `close()`.

Một lần `read()` trả `-1` có nghĩa là **hết dữ liệu đầu vào**, không phải mặc nhiên thread bị ngắt. Đối tác mạng đóng phía gửi đúng quy trình cũng có thể khiến phía nhận thấy EOF. Phải tách kết thúc bình thường của giao thức khỏi lỗi và hủy.

## 7. ServerSocket.accept() và việc ngừng server

`ServerSocket.accept()` chặn để chờ kết nối đến. Với `ServerSocket` Java 17, luồng khác gọi `serverSocket.close()` sẽ khiến thread đang chờ `accept()` nhận `SocketException`. Đây là cách thường dùng để thoát vòng lặp chấp nhận kết nối khi shutdown.

~~~java
// Mảnh mã minh họa, không phải chương trình chạy độc lập.
try {
    while (!stopping) {
        Socket client = serverSocket.accept();
        // Giao client cho thành phần xử lý, xác định rõ ownership.
    }
} catch (java.net.SocketException ex) {
    if (!stopping) {
        throw ex; // Lỗi bất thường, không che giấu.
    }
}
~~~

Biến `stopping` là trạng thái dừng được chia sẻ đúng cách, thường dùng `volatile` hoặc primitive đồng bộ phù hợp. Khi shutdown: cập nhật trạng thái dừng rồi đóng `serverSocket`. Tuy nhiên một cờ boolean đơn thuần **không đánh thức** `accept()` đang chờ; chính `close()`/timeout hoặc một cơ chế được hỗ trợ mới đảm nhiệm việc đó.

`ServerSocket.setSoTimeout(...)` có thể giới hạn thời gian chờ `accept()`, khiến `SocketTimeoutException` xuất hiện nhưng server socket vẫn còn hợp lệ. Với một server lâu dài, không nhất thiết cần polling liên tục nếu đã có lối dừng an toàn bằng close.

## 8. Selector và non-blocking I/O lại là một cơ chế khác

`Selector.select()` không phải `SocketChannel.read()` đang chặn. Một thread đợi các channel sẵn sàng trong selector có thể được đánh thức bằng:

- `selector.wakeup()`: đánh thức thao tác select mà **không cần đóng** selector.
- `selector.close()`: đóng selector và đánh thức thao tác chọn đang chờ.
- `selectingThread.interrupt()`: đặt cờ interrupt và đánh thức select thông qua cơ chế wakeup.

**Đừng nhầm**: ngắt thread đang ở `Selector.select()` không có nghĩa là mọi channel đăng ký trong selector đều bị đóng như khi ngắt blocking I/O trực tiếp trên `InterruptibleChannel`.

Ví dụ một vòng event loop có cơ chế shutdown:

~~~java
// Mảnh mã minh họa: selector đã mở, running là cờ chia sẻ an toàn.
while (running) {
    selector.select();
    if (!running) {
        break;
    }
    // Xử lý các khóa đã sẵn sàng, sau đó loại/clear selected keys.
}

// Một thread quản lý yêu cầu dừng:
running = false;
selector.wakeup();
~~~

Nếu có nhiều luồng cùng thao tác với key/channel, cần tuân theo quy tắc đồng bộ của `Selector` và `SelectionKey`, đồng thời quản lý trạng thái đóng và key không còn hợp lệ.

### Non-blocking SocketChannel

Trong chế độ non-blocking, `SocketChannel.read(ByteBuffer)` có thể trả `0` khi chưa có dữ liệu. Đừng viết vòng `while (read(...) == 0) { }` quay CPU vô hạn để “chờ” dữ liệu. Dùng `Selector` hoặc cơ chế chờ phù hợp, tách riêng tín hiệu hủy khỏi trạng thái “chưa sẵn sàng”.

## 9. File I/O, bộ nhớ, pipe và console: không đánh đồng

| Loại nguồn/đích | Điều chắc chắn hoặc cần kiểm tra khi hủy |
|---|---|
| `FileChannel` (NIO) | Triển khai `InterruptibleChannel`; khi có thao tác blocking I/O thuộc hợp đồng này, interrupt có thể đóng channel và gây `ClosedByInterruptException` |
| `SocketChannel` / `ServerSocketChannel` blocking | NIO interruptible; phân biệt close từ thread khác và thread interrupt |
| `java.net.Socket` stream trên Java 17 | `Socket.close()` làm blocking socket I/O kết thúc bằng `SocketException`; `SO_TIMEOUT` giới hạn read |
| `java.net.ServerSocket.accept()` | `close()` từ thread khác đánh thức với `SocketException` |
| `ByteArrayInputStream`/`ByteArrayOutputStream` | Nằm hoàn toàn trong bộ nhớ, không đại diện cho thao tác chờ I/O bên ngoài; `interrupt()` không có nghĩa hủy một phép đọc buffer tức thời |
| `FileInputStream`/`FileOutputStream` truyền thống | Không có hợp đồng chung rằng `Thread.interrupt()` luôn hủy blocking I/O; hành vi phụ thuộc lời gọi và hệ thống/triển khai |
| `PipedInputStream`/`PipedOutputStream` | Các thao tác chờ và `close` có cơ chế riêng; có thể liên quan `InterruptedIOException` và đồng bộ, không đồng nhất với NIO Pipe |
| `System.in` hoặc console | Có thể phụ thuộc terminal/OS và wrapper; không bảo đảm ngắt hay đóng từ thread khác giải phóng mọi lần đọc |

Đừng dùng file I/O để minh họa một phép blocking chờ vô hạn rồi suy ra mọi loại file đều chờ như socket: đọc một file thường chỉ chặn ngắn, và thao tác cụ thể có thể hoàn tất trước khi yêu cầu hủy đến. Đồng thời, `close()` của một luồng I/O bất kỳ không mặc nhiên có tác dụng **đánh thức ngay** mọi thao tác đang bị chặn; cần xem tài liệu của loại tài nguyên thật đang sử dụng.

Nếu đang dùng wrapper như `BufferedInputStream`, `BufferedReader` hay `InputStreamReader`, phải xét cả **lớp bọc**, **đối tượng nguồn** và **cơ chế đồng bộ bên trong**. Đóng/interrupt ở một tầng không phải lúc nào cũng đi theo cùng đường truyền như kỳ vọng. Có API đồng bộ `close` với thao tác đọc, khiến việc đóng từ thread khác không phải lối hủy nhanh như trên `InterruptibleChannel`.

## 10. InterruptedIOException và xử lý phần dữ liệu đã truyền

`java.io.InterruptedIOException` báo một thao tác I/O bị gián đoạn theo hợp đồng API; trường `bytesTransferred` có thể cho biết số byte đã truyền thành công trước khi gián đoạn. `SocketTimeoutException` cũng kế thừa lớp này.

Nhưng phải nhớ:

- Không phải mọi hành vi ngắt đều tạo `InterruptedIOException`; NIO channel có `ClosedByInterruptException`.
- Không phải mọi `InterruptedIOException` đồng nghĩa thread có cờ interrupted; `SocketTimeoutException` có thể do timeout thông thường.
- Nếu thao tác ghi bị gián đoạn **sau khi đã ghi một phần**, gửi lại **toàn bộ** payload mà không xử lý tiến độ có thể tạo dữ liệu trùng. Nếu không biết chính xác phía nhận đã nhận gì, ứng dụng còn cần giao thức có sequence number, idempotency hoặc cách xác nhận riêng.
- Nếu thao tác đọc kết thúc sớm, không được giả định buffer chứa đủ số byte yêu cầu. Phải dựa vào giá trị trả về và hợp đồng của từng API.

Đọc ngoại lệ theo ngữ cảnh và trạng thái tài nguyên, thay vì chỉ kiểm tra `ex instanceof IOException` rồi thử lại không giới hạn.

## 11. Future.cancel(true) không phải nút dừng mọi I/O

Với `ExecutorService`, một lệnh như:

~~~java
// Mảnh mã minh họa: future đại diện cho task đã gửi.
future.cancel(true);
~~~

có ý nghĩa yêu cầu hủy tác vụ và **có thể ngắt thread đang thực hiện** nếu tác vụ đang chạy. Nó không bảo đảm một lời gọi `InputStream.read()` tùy ý sẽ lập tức kết thúc. Với blocking I/O không đáp ứng interrupt, task có thể vẫn giữ tài nguyên dù `Future` đã chuyển trạng thái cancelled.

Các bước thiết kế thường cần kết hợp:

1. Đặt **trạng thái hủy** mà vòng xử lý có thể quan sát.
2. Gọi `cancel(true)` hoặc `interrupt()` với những thao tác hỗ trợ.
3. **Đóng tài nguyên mà task sở hữu** nếu loại tài nguyên có hợp đồng đánh thức blocking call khi close.
4. Thiết lập **timeout/deadline** để giới hạn chờ, nếu API hỗ trợ.
5. Đợi task kết thúc và giải phóng tài nguyên với một giới hạn thời gian hợp lý, không đánh đổi bằng việc chờ vô hạn trên thread shutdown.

Đừng dùng `Thread.stop()` để giải quyết một lần đọc bị chặn: phương thức này không an toàn và đã bị loại khỏi cách thực hành hiện đại.

### Mẫu quản lý tài nguyên có trách nhiệm

Một phương án phổ biến là để task tự đóng nguồn bằng `try-with-resources` khi hoàn tất hoặc gặp lỗi; thành phần quản lý chỉ được gọi `close()` từ thread khác khi **được giao quyền sở hữu** để hủy một task cụ thể.

~~~java
// Mảnh mã minh họa: socket được tạo riêng cho task này,
// executor và Future<?> đã được quản lý ở tầng gọi.
Socket socket = new Socket();

try {
    // Thiết lập kết nối qua socket.connect(endpoint, connectTimeout).
    // Truyền socket vào task đọc và đặt read timeout nếu phù hợp.
    // Khi hủy, tầng quản lý có thể đóng socket do chính task sở hữu.
} finally {
    socket.close();
}
~~~

Chỉ riêng `Thread.interrupt()` thường không đủ để xác nhận tài nguyên đã đóng, đặc biệt với các loại I/O không implement `InterruptibleChannel`. Đồng thời cần tránh cạnh tranh khiến một thread đóng stream/channel khi thread khác vẫn đang sử dụng mà không được thiết kế cho việc đó.

## 12. Phân loại kết quả khi một read bị dừng

| Quan sát | Ý nghĩa cần cân nhắc | Cách xử lý |
|---|---|---|
| `read()` trả `-1` | Hết dữ liệu (EOF), phía gửi có thể đã half-close | Xử lý kết thúc thông thường theo giao thức |
| `read()` trả số byte dương | Đã nhận dữ liệu hợp lệ | Xử lý đúng số byte thực đọc |
| `read()` trả `0` | Có thể xảy ra với non-blocking channel hoặc lời gọi độ dài 0 | Không coi `0` là EOF; tránh busy-spin |
| `ClosedByInterruptException` | Blocking I/O trên interruptible channel bị ngắt, channel đóng | Dừng sử dụng channel, bảo toàn ý định hủy |
| `AsynchronousCloseException` | Channel bị đóng trong khi thao tác đang chặn | Kiểm tra shutdown hợp lệ hay lỗi bất ngờ |
| `ClosedChannelException` | Channel không còn mở cho thao tác | Kiểm tra lifecycle và race |
| `SocketException` khi đóng socket | Có thể là shutdown chủ đích hoặc lỗi mạng | Dựa vào trạng thái shutdown của ứng dụng để phân biệt |
| `SocketTimeoutException` | Hết thời gian chờ đọc/accept được cấu hình | Áp dụng retry/deadline theo nghiệp vụ; socket thường vẫn dùng được |
| `InterruptedException` | Một thao tác chờ có khả năng bị interrupt đã bị đánh thức | Truyền tiếp hoặc phục hồi cờ và kết thúc thích hợp |

Tránh viết code catch toàn bộ `IOException` rồi in “Đã hủy” vì có thể che giấu mất kết nối, quyền truy cập, hỏng thiết bị và lỗi dữ liệu. Ngược lại, một ngoại lệ sau khi shutdown chủ đích không nhất thiết là lỗi cần báo động: cần biết ngữ cảnh đóng tài nguyên.

## 13. Kiểm thử đúng cơ chế và tránh flaky test

Kiểm thử tình huống đồng thời phải có **đồng bộ rõ ràng**, không dựa vào `Thread.sleep(100)` để hy vọng thread kia đã blocking.

- Với `Pipe.SourceChannel`, giữ sink mở và không ghi byte để read có lý do chờ.
- Dùng `CountDownLatch` xác nhận thread xử lý đã chạy đến điểm chuẩn bị đọc; hợp đồng NIO còn cho phép xử lý trường hợp interrupt được đặt ngay trước khi gọi read.
- Đợi `join()`/`Future.get()` có giới hạn thích hợp trong test; nếu không kết thúc, dọn tài nguyên và thất bại có kiểm soát thay vì khiến bộ test treo.
- Khi test `close` từ thread khác, cho phép cả `AsynchronousCloseException` lẫn `ClosedChannelException` tùy close đến trước hay trong lúc blocking.
- Với socket, tạo kết nối loopback được kiểm soát và thử riêng timeout, close, EOF và half-close; không lấy thời gian phản hồi mạng bên ngoài làm bằng chứng cho thứ tự các thread.
- Kiểm tra **trạng thái** (`isOpen()`, `isClosed()`, cờ interrupted, dữ liệu đã nhận) bên cạnh loại exception.

Nếu chương trình vận hành trên nhiều JDK hoặc hệ điều hành, kiểm thử đúng **loại luồng đang sử dụng trong production**; một test với NIO `SocketChannel` không đại diện cho mọi wrapper `java.io` hoặc API mạng thế hệ khác.

## 14. Bảng quyết định nhanh

| Nhu cầu thực tế | Cơ chế thường phù hợp | Lưu ý |
|---|---|---|
| Hủy blocking NIO channel riêng của task | `interrupt()` hoặc `close()` theo chính sách tài nguyên | Interrupt cũng **đóng** `InterruptibleChannel` đang blocking |
| Dừng tác vụ đang `Socket.read()` (socket riêng) | `Socket.close()`; kết hợp `setSoTimeout` | `close` thường khiến `SocketException`; timeout không tự đóng |
| Dừng `ServerSocket.accept()` khi shutdown | Đặt cờ dừng và `ServerSocket.close()` | Không mong `volatile` flag tự đánh thức accept |
| Đánh thức event loop trong `Selector.select()` | `selector.wakeup()` | Không cần đóng selector/channel nếu chỉ muốn kiểm tra nhiệm vụ mới |
| Hủy task trên executor | `Future.cancel(true)` + cleanup/timeout | Cancellation không phải bảo đảm hủy mọi I/O |
| Xử lý tệp dài | Các khối đọc/ghi có điểm kiểm tra hủy | Đừng giả định file stream đáp ứng interrupt như NIO |
| Nhận EOF từ peer | Xử lý kết thúc giao thức | EOF không đồng nghĩa bị interrupt |
| Ứng dụng cần deadline toàn phần | Theo dõi thời hạn xuyên suốt các lần read/write | Timeout của từng read chưa chắc giới hạn toàn bộ nhiệm vụ |

## 15. Ghi nhớ cốt lõi

1. **`interrupt` là tín hiệu phối hợp hủy**, không phải cơ chế ép mọi Java thread hoặc I/O dừng ngay.
2. **`InterruptibleChannel`** có hợp đồng mạnh: ngắt thread đang blocking → channel đóng, `ClosedByInterruptException`, cờ interrupted đặt; close từ thread khác trong lúc blocking → `AsynchronousCloseException`.
3. **`Socket.close()`** và **`ServerSocket.close()`** trên Java 17 có quy tắc đánh thức blocking I/O tương ứng; `SO_TIMEOUT` ném timeout mà thường vẫn giữ socket hợp lệ.
4. **`Selector.select()`** có thể được đánh thức bằng `wakeup()` hoặc interrupt mà không mặc nhiên đóng tất cả channel đã đăng ký.
5. **`EOF (-1)`, timeout, interrupt, asynchronous close và lỗi I/O** có ý nghĩa khác nhau; không gộp mọi trường hợp thành “đã hủy”.
6. **Hủy tác vụ và đóng tài nguyên phải tuân theo ownership**: tránh đóng một tài nguyên dùng chung chỉ vì một worker không còn cần nó.
7. Xác minh chính xác hợp đồng của API/JDK đang dùng và kiểm thử bằng đồng bộ xác định thay vì `sleep` ngẫu nhiên.

### Tài liệu tham khảo chính thức

- [Java 17 — InterruptibleChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/InterruptibleChannel.html)
- [Java 17 — ClosedByInterruptException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ClosedByInterruptException.html)
- [Java 17 — Socket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html)
- [Java 17 — ServerSocket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/ServerSocket.html)
- [Java 17 — Selector](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html)
- [Java 17 — InterruptedIOException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InterruptedIOException.html)


</details>

- [Quay lại đầu trang](#back-to-top)
