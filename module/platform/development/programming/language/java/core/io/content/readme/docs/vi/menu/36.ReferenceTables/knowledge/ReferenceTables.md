<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng chọn InputStream, Reader, Files hoặc Channel](#reference-tables-001)
- [Bảng charset và quy tắc chuyển byte sang ký tự](#reference-tables-002)
- [Bảng biến đổi trạng thái Buffer](#reference-tables-003)
- [Bảng OpenOption, CopyOption, LinkOption và FileVisitOption](#reference-tables-004)
- [Bảng trách nhiệm đóng tài nguyên theo từng API](#reference-tables-005)
- [Bảng lỗi I/O thường gặp và thông tin cần kiểm tra](#reference-tables-006)
- [Bảng API truyền thống, NIO, NIO.2 và các mốc phiên bản](#reference-tables-007)
- [Tra cứu Javadoc, Serialization Specification và khả năng của provider](#reference-tables-008)

## <a id="reference-tables-001">Bảng chọn InputStream, Reader, Files hoặc Channel</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng chọn InputStream, Reader, Files hoặc Channel trong Java

Khi làm việc với I/O, đừng bắt đầu bằng câu hỏi “API nào nhanh nhất?”. Hãy xác định **dữ liệu là byte hay văn bản, nguồn/đích là gì, có cần truy cập ngẫu nhiên không, và kích thước dữ liệu có thể tăng đến đâu**. Bốn cái tên trong tiêu đề giải quyết các nhu cầu khác nhau; nhiều trường hợp chúng được kết hợp với nhau.

> Phạm vi: Java 17+, chủ yếu là I/O đồng bộ. Ví dụ dùng `java.nio.file.Files`, `java.io.InputStream`/`Reader` và `java.nio.channels.Channel`.

## 1. Bảng chọn nhanh theo tình huống

| Nhu cầu thực tế | Ưu tiên | Vì sao | Tránh nhầm |
| --- | --- | --- | --- |
| Đọc toàn bộ **file văn bản nhỏ**, biết charset | `Files.readString(path, UTF_8)` | Mở, đọc, đóng tài nguyên trong một lệnh | File quá lớn sẽ tốn nhiều bộ nhớ |
| Ghi một chuỗi văn bản xuống file | `Files.writeString(path, text, UTF_8)` | Đơn giản, kiểm soát charset, có tùy chọn ghi | Mặc định có thể tạo mới hoặc ghi đè nội dung cũ |
| Duyệt từng dòng của file văn bản | `Files.newBufferedReader` + `BufferedReader.readLine`, hoặc `Files.lines` | Xử lý tăng dần, không cần nạp hết file | Phải đóng reader/stream; `readLine` bỏ ký tự xuống dòng |
| Đọc/ghi **byte thô** từ file, socket, HTTP body hoặc nguồn bất kỳ | `InputStream` / `OutputStream` | API byte liên tục, phổ biến cho dữ liệu nhị phân | Không chuyển ảnh/ZIP/PDF thành `Reader` |
| Sao chép file có sẵn sang file khác | `Files.copy(source, target, options)` | Ngắn gọn, xử lý đường dẫn và file | Không tự viết vòng lặp copy nếu chỉ cần sao chép file |
| Đọc file nhị phân nhỏ thành mảng byte | `Files.readAllBytes(path)` | Thuận tiện khi dữ liệu đủ nhỏ | Mảng lớn có thể hết bộ nhớ |
| Cần đọc/ghi ở **vị trí byte cụ thể** trong file | `SeekableByteChannel` hoặc `FileChannel` | Có `position(long)`; dùng `ByteBuffer` | `InputStream` thường là luồng đọc tiến, không hỗ trợ seek tổng quát |
| Cần khóa file, ánh xạ bộ nhớ, hoặc thao tác chuyên biệt trên file | `FileChannel` | Có `lock`, `map`, `transferTo`, `transferFrom` | Nhiều tính năng phụ thuộc hệ điều hành/file system |
| I/O mạng kiểu non-blocking, dùng selector | `SocketChannel` + `Selector` | Có thể làm việc với `java.nio` non-blocking | Không dùng channel chỉ vì tên “NIO”; API phức tạp hơn |
| Dữ liệu xuất phát từ một `InputStream` nhưng cần **đọc ký tự** | `InputStreamReader(in, charset)`, thường bọc `BufferedReader` | Giải mã byte sang ký tự đúng charset | Không giả định charset mặc định của máy |
| Chỉ cần kiểm tra, tạo, liệt kê, đổi tên, xóa đường dẫn/file | `Path` + phương thức `Files` | Đây là các thao tác file system, không cần tự mở stream | `Path` biểu diễn đường dẫn; nó không chứa dữ liệu của file |
| Nguồn rất lớn hoặc chưa biết kích thước | Stream/Reader/Channel đọc theo khối | Giữ lượng bộ nhớ gần như cố định | Tránh `readAllBytes`, `readString`, `readAllLines` không giới hạn |

**Quy tắc ngắn:** Nếu bài toán mô tả **thao tác trên file và đường dẫn**, bắt đầu từ `Path`/`Files`. Nếu bài toán mô tả **luồng byte**, chọn `InputStream`. Nếu nó mô tả **ký tự/văn bản**, chọn `Reader` cùng charset. Nếu cần **vị trí, buffer, kênh file hoặc I/O non-blocking**, xem `Channel`.

## 2. Hiểu chính xác bốn nhóm API

### `InputStream`: đọc byte tuần tự

`java.io.InputStream` cung cấp `read()`, `read(byte[])`, `read(byte[], off, len)`, `readAllBytes()` và `transferTo(OutputStream)`. Kết quả đọc là các **byte**, không phải ký tự. `read(...)` trả về số byte thực đọc, hoặc `-1` khi hết dữ liệu; không được giả định một lần đọc sẽ lấp đầy buffer.

Các lớp thường gặp là `FileInputStream` (file), `ByteArrayInputStream` (bộ nhớ), `BufferedInputStream` (đệm) và những stream do thư viện HTTP/ZIP/socket trả về. `InputStream` phù hợp ảnh, âm thanh, ZIP, PDF, định dạng giao thức, và cả văn bản **khi mục tiêu chỉ là vận chuyển byte không thay đổi**. Để tạo luồng từ `Path`, dùng `Files.newInputStream(path)`.

`OutputStream` là phía ghi byte. `BufferedInputStream`/`BufferedOutputStream` giảm số lần truy cập nguồn/đích; chúng không tự giải mã văn bản.

### `Reader`: đọc ký tự sau khi giải mã byte

`java.io.Reader` cung cấp `read()` và `read(char[])`. `InputStreamReader` là cầu nối **byte → ký tự** theo charset được chỉ định. `BufferedReader` thêm `readLine()` và `lines()`; `Files.newBufferedReader(path, charset)` mở reader cho file.

Khi làm việc với log, CSV, JSON, cấu hình hay mã nguồn dưới dạng văn bản, cần quyết định **charset** trước (thường là `StandardCharsets.UTF_8`). Một ký tự Unicode có thể chiếm nhiều byte UTF-8, nên **số byte không bằng số ký tự**. Trong Java, `char` là một đơn vị mã UTF-16; một ký tự Unicode ngoài BMP có thể gồm hai `char`. Không chia byte tùy tiện rồi chuyển từng mảnh độc lập thành `String` vì có thể cắt ngang một chuỗi byte UTF-8.

`Reader` chỉ xử lý ký tự; không tự phân tích JSON/CSV theo đúng quy tắc định dạng. Khi cần parse, dùng parser phù hợp sau lớp đọc.

### `Files` và `Path`: thao tác file ở mức tiện ích

`Path` (`java.nio.file.Path`) biểu diễn đường dẫn; `Files` (`java.nio.file.Files`) có nhiều phương thức tĩnh: `readString`, `readAllBytes`, `writeString`, `write`, `copy`, `move`, `delete`, `createDirectories`, `newInputStream`, `newBufferedReader`, `newByteChannel` và `lines`. Nhờ đó `Files` có thể là API cấp cao **hoặc** điểm mở stream/channel cấp thấp.

Lưu ý phương thức “đọc hết” giữ dữ liệu trong bộ nhớ, còn `newBufferedReader`, `newInputStream` và `lines` thích hợp khi cần đọc tăng dần. Với `Files.lines`, đối tượng `Stream<String>` được liên kết với file đang mở và cần đóng qua `try-with-resources`.

### `Channel`: I/O qua buffer, có khả năng chuyên biệt

`java.nio.channels.Channel` là họ giao diện rộng. `ReadableByteChannel`/`WritableByteChannel` đọc/ghi với `ByteBuffer`; `SeekableByteChannel` hỗ trợ `position` và `size`; `FileChannel` hỗ trợ thao tác file nâng cao; `SocketChannel` có thể dùng chế độ non-blocking.

Với `ByteBuffer`:
1. `put` hoặc channel `read` làm **position** tiến lên.
2. Gọi `flip()` để chuyển từ ghi vào buffer sang đọc dữ liệu vừa nhận.
3. Đọc/ghi dữ liệu ra; nếu tiếp tục nhận thêm dữ liệu, dùng `clear()` hoặc `compact()` tùy còn byte chưa tiêu thụ hay không.

`Channel` không đồng nghĩa với “tự động nhanh hơn”. Với tác vụ file thông thường, `Files` hoặc stream có đệm thường đơn giản và đủ tốt. Một lần `channel.read(buffer)` cũng có thể chỉ trả về một phần dữ liệu; vòng lặp vẫn phải xử lý số byte trả về và EOF.

## 3. Sơ đồ quyết định khi cầm một yêu cầu I/O mới

```text
Yêu cầu liên quan tới file/path?
  ├─ Có: chỉ tạo/copy/move/xóa/kiểm tra → Path + Files
  └─ Có: cần đọc/ghi nội dung
       ├─ Văn bản, nội dung nhỏ → Files.readString / writeString + charset
       ├─ Văn bản, đọc tăng dần → BufferedReader / Files.lines
       ├─ Byte nhỏ → Files.readAllBytes / write
       ├─ Byte lớn/luồng liên tục → Files.newInputStream / newOutputStream
       └─ Truy cập vị trí, map/lock/buffer đặc thù → SeekableByteChannel / FileChannel

Nguồn không phải file (HTTP response, socket, dữ liệu nén, bộ nhớ...)?
  ├─ Chỉ cần byte → InputStream (hoặc channel nếu hệ thống cung cấp)
  ├─ Cần văn bản → InputStreamReader(stream, charset) → BufferedReader
  └─ Cần non-blocking/multiplexing qua selector → SocketChannel + Selector
```

Cây trên là điểm bắt đầu, không phải quy tắc loại trừ. Chẳng hạn, `Files.newInputStream(path)` **trả về** `InputStream`; `Files.newByteChannel(path)` **trả về** `SeekableByteChannel`. `Files` là nơi mở tài nguyên, còn stream/reader/channel là cách **tiêu thụ** dữ liệu.

## 4. Ví dụ Java 17 chạy độc lập: chọn đúng theo cùng một tập file

Chương trình sau tự tạo thư mục tạm, viết file văn bản UTF-8 và file nhị phân, rồi đọc mỗi loại theo yêu cầu. Lệnh và kết quả đầu ra ổn định trên các nền tảng hỗ trợ Java 17.

Tạo file `IoChoiceDemo.java`:

```java
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IoChoiceDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("io-choice-");
        Path textFile = dir.resolve("notes.txt");
        Path binaryFile = dir.resolve("payload.bin");

        try {
            Files.writeString(textFile, "Xin chào\nJava IO\n",
                    StandardCharsets.UTF_8);
            Files.write(binaryFile, new byte[] {65, 66, 67});

            // Files: đọc toàn bộ file nhỏ thành String.
            String text = Files.readString(textFile, StandardCharsets.UTF_8);
            System.out.println("Files: " + text.split("\\R")[1]);

            // Reader: đọc văn bản theo dòng và charset đã biết.
            try (BufferedReader reader = Files.newBufferedReader(
                    textFile, StandardCharsets.UTF_8)) {
                System.out.println("Reader: " + reader.readLine());
            }

            // InputStream: đọc byte đầu tiên của dữ liệu nhị phân.
            try (InputStream in = Files.newInputStream(binaryFile)) {
                System.out.println("InputStream: " + (char) in.read());
            }

            // Channel: di chuyển tới offset 1 rồi đọc byte tại vị trí đó.
            try (SeekableByteChannel channel = Files.newByteChannel(
                    binaryFile, StandardOpenOption.READ)) {
                channel.position(1);
                ByteBuffer buffer = ByteBuffer.allocate(1);
                int count = channel.read(buffer);
                if (count != 1) {
                    throw new IllegalStateException("Cannot read byte at offset 1");
                }
                buffer.flip();
                System.out.println("Channel: " + (char) buffer.get());
            }
        } finally {
            Files.deleteIfExists(binaryFile);
            Files.deleteIfExists(textFile);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file mã nguồn:

```bash
javac -encoding UTF-8 IoChoiceDemo.java
java IoChoiceDemo
```

Kết quả chuẩn:

```text
Files: Java IO
Reader: Xin chào
InputStream: A
Channel: B
```

**Giải thích:** `Files.readString` trả về một `String` vì file văn bản nhỏ; `BufferedReader` đọc dòng đầu mà không cần giữ cả file; `InputStream.read()` trả về byte 65 (ASCII `A`); channel đặt vị trí tại byte thứ hai, nhận byte 66 (`B`) trong `ByteBuffer` rồi `flip()` trước khi lấy ra. `try-with-resources` đóng mọi reader/stream/channel; `finally` dọn các file tạm đã tạo.

Ví dụ dùng `throws Exception` để giữ sự tập trung vào cách chọn API. Trong ứng dụng thực tế, xử lý `IOException` theo ngữ cảnh, kèm tên đường dẫn và hành động đang thực hiện.

## 5. Ba mẫu thường dùng khi dữ liệu tăng lớn

### 5.1 Sao chép file: ưu tiên API trực tiếp

Nếu đầu vào và đầu ra đều là `Path`, không cần tự tạo vòng lặp:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

Path source = Path.of("data", "source.bin");
Path target = Path.of("data", "backup.bin");
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
```

Đây là **đoạn mã minh họa** đặt trong phương thức có xử lý/khai báo `IOException`; file nguồn và thư mục đích phải tồn tại. `REPLACE_EXISTING` cho phép thay file đích đang có. Nếu không muốn thay, bỏ tùy chọn này và xử lý `FileAlreadyExistsException`. API không bảo đảm mọi hệ file đều có cùng hành vi đối với metadata hay symbolic link; xem tùy chọn `CopyOption` khi cần.

### 5.2 Vận chuyển byte từ nguồn bất kỳ

Khi nguồn là `InputStream` (ví dụ response body, ZIP entry, socket), đọc dữ liệu byte và chuyển sang `OutputStream`:

```java
import java.io.InputStream;
import java.io.OutputStream;

// Đoạn mã minh họa nằm trong phương thức throws IOException.
// sourceStream là InputStream đã được mở bởi phía gọi.
try (InputStream in = sourceStream;
     OutputStream out = Files.newOutputStream(Path.of("download.bin"))) {
    in.transferTo(out);
}
```

Cần import thêm `java.nio.file.Files` và `java.nio.file.Path` nếu đưa đoạn này vào chương trình riêng. `transferTo` (Java 9+) chuyển tất cả byte tới EOF; không giải mã charset. Khi stream được sở hữu bởi thư viện/framework, kiểm tra **ai chịu trách nhiệm đóng** trước khi đặt nó trong `try-with-resources`. Với dữ liệu không tin cậy hoặc upload lớn, thêm giới hạn dung lượng, timeout và quy tắc hủy theo ngữ cảnh.

### 5.3 Xử lý file văn bản lớn theo dòng

```java
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

try (BufferedReader reader = Files.newBufferedReader(
        Path.of("events.log"), StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        if (line.contains("ERROR")) {
            System.out.println(line);
        }
    }
}
```

Đoạn mã minh họa nằm trong phương thức khai báo/xử lý `IOException`. Nó xử lý từng dòng thay vì gọi `Files.readAllLines`. `readLine` **không trả lại** ký tự phân cách dòng; nếu cần bảo tồn byte gốc, loại newline hoặc charset gốc, phải chọn chiến lược khác. Dòng riêng lẻ quá lớn vẫn có thể tiêu tốn nhiều bộ nhớ.

Có thể dùng:

```java
try (var lines = Files.lines(Path.of("events.log"),
        StandardCharsets.UTF_8)) {
    long errors = lines.filter(line -> line.contains("ERROR")).count();
    System.out.println(errors);
}
```

Đoạn thứ hai cũng cần import `Files`, `Path`, `StandardCharsets` và khai báo/xử lý `IOException`; đối tượng `Stream` phải được đóng. `Files.lines` là lazy, nhưng thao tác terminal (`count`, `forEach`, v.v.) mới kích hoạt đọc. Lỗi I/O trong lúc tiêu thụ stream có thể được bọc thành `UncheckedIOException`.

## 6. Nếu phải dùng Channel: hai tình huống thật sự có lợi

**Truy cập ngẫu nhiên trong file:** ví dụ đọc header tại offset 0, sau đó đọc một record tại offset N. `SeekableByteChannel.position(N)` phù hợp vì một `InputStream` tổng quát không có thao tác đặt vị trí bất kỳ. `FileChannel` bổ sung API như `read(ByteBuffer, position)` và `write(ByteBuffer, position)`.

**I/O mạng non-blocking:** `SocketChannel.configureBlocking(false)` kết hợp `Selector` cho phép quản lý nhiều kết nối. Cơ chế đó đòi hỏi vòng đời kết nối, quản lý partial read/write và xử lý trạng thái sẵn sàng. Không áp dụng sơ đồ selector cho một tác vụ đọc file văn bản đơn giản.

Ví dụ minh họa đọc theo khối bằng channel (đặt trong phương thức `throws IOException`):

```java
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

try (FileChannel channel = FileChannel.open(
        Path.of("large.bin"), StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    long total = 0;

    while (true) {
        int n = channel.read(buffer);
        if (n == -1) break;
        if (n == 0) continue;
        total += n;

        buffer.flip();
        // Tiêu thụ các byte trong [position, limit) ở đây.
        buffer.position(buffer.limit());
        buffer.clear();
    }
    System.out.println("Total bytes: " + total);
}
```

`channel.read` có thể trả `0` ngay cả khi chưa EOF, nên phải xử lý giá trị đó. `flip` làm vùng byte vừa nhận sẵn sàng cho đọc; `clear` sau khi **đã tiêu thụ hết** vùng này. Trong ứng dụng thực tế, thay dòng `buffer.position(buffer.limit())` bằng xử lý dữ liệu và dùng `compact` nếu còn byte chưa xử lý. Không suy diễn rằng `FileChannel.transferTo` luôn thực hiện zero-copy: tối ưu hóa phụ thuộc môi trường và kênh đích.

## 7. Bộ nhớ, charset, tài nguyên và các lỗi chọn API hay gặp

| Sai lầm | Hậu quả | Cách chọn/điều chỉnh |
| --- | --- | --- |
| Dùng `Reader` để đọc ảnh, ZIP, PDF | Giải mã byte làm thay đổi dữ liệu | Dùng `InputStream`, `Files.readAllBytes` nếu nhỏ, hoặc channel khi cần |
| Dùng `InputStream` rồi ép từng byte sang `char` để đọc tiếng Việt | UTF-8 nhiều byte nên văn bản lỗi | Dùng `InputStreamReader(in, UTF_8)`/`Files.newBufferedReader` |
| Gọi `readString`/`readAllBytes` cho file không giới hạn | Nguy cơ dùng nhiều heap hoặc `OutOfMemoryError` | Đọc từng dòng hoặc từng khối với buffer |
| Dựa vào charset mặc định không chủ đích | Có thể đọc khác nhau giữa hệ thống hoặc nguồn dữ liệu | Truyền `StandardCharsets.UTF_8` hoặc charset thật của nguồn |
| Nghĩ `read(buffer)` luôn trả đủ `buffer.length` | Mất dữ liệu khi xử lý sai partial read | Lặp tới đủ số byte cần hoặc tới `-1` |
| Quên `flip()` trước khi lấy byte từ buffer vừa ghi | Position/limit sai, đọc sai vùng | `flip` khi chuyển pha ghi → đọc; `clear`/`compact` cho vòng tiếp theo |
| Không đóng `Files.lines`/reader/stream/channel | Rò file descriptor, khóa tài nguyên | `try-with-resources` |
| Dùng channel chỉ để đọc chuỗi nhỏ | Mã phức tạp, tự xử lý buffer/charset | `Files.readString(path, charset)` |
| Kiểm tra `Files.exists` rồi tin file vẫn tồn tại | File có thể thay đổi giữa kiểm tra và thao tác | Thực hiện thao tác và xử lý exception |
| Đo hiệu năng chỉ qua tên API | Quyết định sai vì cache, bộ đệm, kích thước và thiết bị | Benchmark theo tải thực tế; dùng API đơn giản trước |

**Giới hạn bộ nhớ:** `readAllBytes`/`readString`/`readAllLines` giữ toàn bộ dữ liệu hoặc cấu trúc kết quả trong RAM. `BufferedReader` và `InputStream` với buffer cố định thường giữ mức dùng RAM ổn định hơn; tuy nhiên một dòng đơn lẻ rất dài hoặc logic tự gom kết quả vẫn có thể gây phình bộ nhớ.

**Charset và lỗi giải mã:** UTF-8 là lựa chọn phổ biến, nhưng file thực tế có thể dùng charset khác hoặc có BOM. Xác nhận format trước khi đọc; không suy diễn mọi dãy byte đều là UTF-8 hợp lệ. Nếu cần chính sách nghiêm ngặt khi gặp byte lỗi, dùng `CharsetDecoder` cấu hình `CodingErrorAction.REPORT`, thay vì âm thầm chấp nhận ký tự thay thế.

**Tài nguyên:** `Files.readString`, `Files.readAllBytes`, `Files.copy(Path, Path, ...)` tự quản lý thao tác mở/đóng nội bộ. API trả về tài nguyên đang mở như `Files.newInputStream`, `Files.newBufferedReader`, `Files.lines` hay `FileChannel.open` thì phía gọi phải đóng. Với pipeline vừa đọc vừa ghi, xác định rõ quyền sở hữu stream; `flush` không thay thế `close`.

**Đường dẫn và quyền:** `NoSuchFileException`, `AccessDeniedException`, `FileAlreadyExistsException` và `IOException` cung cấp thông tin thất bại hữu ích. Không tự kết luận nguyên nhân từ `Files.exists`; quyền truy cập, liên kết tượng trưng và thay đổi đồng thời có thể ảnh hưởng kết quả. Nếu thao tác phải an toàn về ghi đè hay thay thế file, xác định rõ `OpenOption`/`CopyOption` và yêu cầu tính nguyên tử.

## 8. Checklist áp dụng trước khi viết code

1. **Nguồn/đích** là file (`Path`), bộ nhớ, HTTP body, socket hay luồng do thư viện cấp?
2. **Ý nghĩa dữ liệu** là byte nguyên trạng hay ký tự cần giải mã? Nếu là văn bản, charset nào?
3. **Dung lượng** có giới hạn chắc chắn không? Nếu không, xử lý theo dòng/khối và giới hạn đầu vào.
4. **Mẫu truy cập** là đọc từ đầu tới cuối, đọc từng dòng, hay cần seek/lock/mapping/non-blocking?
5. **Quyền sở hữu tài nguyên** thuộc ai; cần đóng gì; ghi đè/tạo mới/append theo tùy chọn nào?
6. **Xử lý lỗi** nên báo lại, retry, ghi log hay hủy? Có cần kiểm tra integrity của dữ liệu không?

Nếu một tác vụ chỉ cần “đọc file cấu hình UTF-8 vài KB”, lựa chọn dễ hiểu thường là `Files.readString`. Nếu cần “lọc 30 GB log theo dòng”, dùng `BufferedReader` hoặc `Files.lines` với vòng đời rõ ràng. Nếu cần “stream một file ZIP từ HTTP xuống đĩa”, dùng byte stream. Nếu cần “cập nhật trường nhị phân tại offset cụ thể”, chọn `SeekableByteChannel`/`FileChannel`. Đó là cách chọn dựa trên **hình dạng bài toán** thay vì tên API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-002">Bảng charset và quy tắc chuyển byte sang ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng charset và quy tắc chuyển byte sang ký tự trong Java I/O

## 1. Mục đích và mô hình cần nhớ

Khi đọc tệp, luồng mạng hoặc dữ liệu nhị phân chứa văn bản, chương trình nhận **byte**, còn ứng dụng muốn xử lý **ký tự Unicode**. Muốn chuyển đúng phải biết **charset (bộ mã ký tự)** mà bên tạo dữ liệu đã dùng. Một chuỗi byte có thể cho ra văn bản khác nhau tùy charset; nhiều trường hợp không thể suy ra charset một cách chắc chắn chỉ từ byte.

Luồng chuyển đổi:

~~~text
Văn bản Unicode --[encoder + charset]--> dãy byte
Dãy byte        --[decoder + charset]--> văn bản Unicode
~~~

- **Charset** quy định cách mã hóa (encode) ký tự thành byte và giải mã (decode) byte thành ký tự.
- **Unicode code point** là số định danh một ký tự trừu tượng, ví dụ U+0041 (A), U+00E9 (é), U+4E2D (中), U+1F600 (😀).
- **Java String và char** biểu diễn chuỗi bằng các đơn vị mã **UTF-16**. Một code point ngoài BMP, chẳng hạn 😀, dùng **hai char** (surrogate pair); vì vậy số byte, số char và số code point có thể khác nhau.
- **CharsetDecoder** biến byte thành chuỗi đơn vị UTF-16. **CharsetEncoder** làm chiều ngược lại.
- **Malformed input** là dãy byte sai cấu trúc charset; **unmappable character** là đầu vào không biểu diễn được ở đích. Hai loại lỗi cần được phân biệt và xử lý có chủ đích.

Công thức tư duy: **byte + charset + quy tắc xử lý lỗi → văn bản**. Không có phép tra cứu đúng cho mọi byte khi thiếu charset.

## 2. Bảng tra cứu các charset thường gặp

| Charset trong Java | Quy tắc và miền biểu diễn | Một ký tự / code point chiếm bao nhiêu byte? | Cần chú ý |
|---|---|---|---|
| **US-ASCII** (StandardCharsets.US_ASCII) | Mã 0x00–0x7F ánh xạ sang U+0000–U+007F | 1 byte cho ký tự biểu diễn được | Byte từ 0x80 trở lên không hợp lệ trong ASCII chuẩn |
| **ISO-8859-1** (StandardCharsets.ISO_8859_1) | Mỗi byte 0x00–0xFF ánh xạ trực tiếp tới code point U+0000–U+00FF | Luôn 1 byte khi mã hóa ký tự thuộc tập này | Byte 0x80 là U+0080 (ký tự điều khiển), **không phải** dấu € |
| **windows-1252** (Charset.forName("windows-1252")) | Mã 8-bit kiểu Windows Tây Âu; nhiều vị trí 0x80–0x9F khác Latin-1 | 1 byte cho ký tự biểu diễn được | Byte 0x80 là €; một số byte như 0x81 không được gán |
| **UTF-8** (StandardCharsets.UTF_8) | Biến độ dài, giữ nguyên ASCII 0x00–0x7F | 1–4 byte/code point Unicode hợp lệ | Byte tiếp diễn 0x80–0xBF không thể tự đứng đầu một ký tự |
| **UTF-16BE** (StandardCharsets.UTF_16BE) | UTF-16 theo thứ tự byte cao trước | 2 hoặc 4 byte/code point | Không tự yêu cầu BOM; thứ tự byte phải được biết |
| **UTF-16LE** (StandardCharsets.UTF_16LE) | UTF-16 theo thứ tự byte thấp trước | 2 hoặc 4 byte/code point | Khác UTF-16BE ở thứ tự byte |
| **UTF-16** (StandardCharsets.UTF_16) | UTF-16 có quy tắc nhận diện BOM để xác định thứ tự byte khi decode | 2 hoặc 4 byte/code point, cộng BOM nếu có | Decoder nhận BOM; nếu không có BOM, mặc định big-endian theo Java |

**UTF-8 và UTF-16 là các Unicode Transformation Format**, có thể biểu diễn toàn bộ Unicode scalar values hợp lệ. Ngược lại, ASCII/Latin-1/Windows-1252 chỉ chứa những tập ký tự hữu hạn; không thể encode mọi chuỗi Unicode vào chúng mà không có xử lý lỗi.

Cách viết "ANSI" trong tài liệu cũ không xác định một charset duy nhất. Cần hỏi rõ mã trang (ví dụ windows-1252, windows-1258) và môi trường đã tạo dữ liệu.

## 3. Bảng ví dụ: cùng ký tự, byte khác nhau

Các chuỗi hex dưới đây mô tả **riêng byte của ký tự**, không kèm BOM:

| Ký tự | Unicode | UTF-8 | ISO-8859-1 | windows-1252 | UTF-16BE | UTF-16LE |
|---|---|---|---|---|---|---|
| A | U+0041 | 41 | 41 | 41 | 00 41 | 41 00 |
| é | U+00E9 | C3 A9 | E9 | E9 | 00 E9 | E9 00 |
| € | U+20AC | E2 82 AC | Không biểu diễn | 80 | 20 AC | AC 20 |
| 中 | U+4E2D | E4 B8 AD | Không biểu diễn | Không biểu diễn | 4E 2D | 2D 4E |
| 😀 | U+1F600 | F0 9F 98 80 | Không biểu diễn | Không biểu diễn | D8 3D DE 00 | 3D D8 00 DE |

**Giải thích các trường hợp dễ nhầm:**

1. UTF-8 mã hóa A bằng 41 giống ASCII, nhưng **é** cần hai byte C3 A9.
2. Byte **E9** trong ISO-8859-1/windows-1252 là é; một byte E9 đứng đơn lẻ **không phải UTF-8 hợp lệ**.
3. Byte **80** trong windows-1252 là €, trong ISO-8859-1 là U+0080, còn trong UTF-8 là byte tiếp diễn đứng sai vị trí.
4. UTF-16 biểu diễn 😀 bằng cặp surrogate **D83D DE00**. Java **String.length()** trả 2 cho ký tự này, trong khi **codePointCount(...)** trả 1.
5. “Không biểu diễn” có nghĩa encoder chuẩn với chính sách **REPORT** phải báo lỗi, không có byte hợp lệ để điền vào bảng. Encoder ở chế độ thay thế có thể phát ra ký tự thay thế như dấu hỏi.

**Tra ngược từ byte cần biết charset:** byte 0xE9 không mặc nhiên là é trong UTF-8; chuỗi E2 82 AC chỉ là € khi giải bằng UTF-8, không phải khi giải bằng Latin-1.

## 4. Quy tắc giải mã UTF-8 theo bit

UTF-8 mã hóa một Unicode code point bằng 1–4 byte. Các mẫu bit:

| Số byte | Byte đầu | Byte tiếp theo | Phạm vi code point |
|---|---|---|---|
| 1 | 0xxxxxxx | Không có | U+0000–U+007F |
| 2 | 110xxxxx | 10xxxxxx | U+0080–U+07FF |
| 3 | 1110xxxx | 10xxxxxx 10xxxxxx | U+0800–U+FFFF, trừ vùng surrogate |
| 4 | 11110xxx | 10xxxxxx 10xxxxxx 10xxxxxx | U+10000–U+10FFFF |

Quy trình giải mã một code point:

1. Xem byte đầu để xác định cần 1, 2, 3 hay 4 byte; kiểm tra còn đủ byte.
2. Với chuỗi đa byte, mọi byte sau phải có tiền tố bit **10**.
3. Bỏ các bit tiền tố và ghép các bit dữ liệu theo thứ tự từ trái sang phải.
4. Kiểm tra giá trị tối thiểu cho độ dài đó, không cho phép dạng **overlong**, code point surrogate U+D800–U+DFFF hoặc giá trị vượt U+10FFFF.
5. Chuyển code point thu được sang một hoặc hai đơn vị UTF-16 để đặt vào Java String.

Ví dụ **é = U+00E9**: C3 (11000011) và A9 (10101001) có các bit dữ liệu **00011 101001** → nhị phân 11101001 → U+00E9. Ví dụ **€ = U+20AC**: E2 82 AC → U+20AC.

Một số lỗi UTF-8 phổ biến: byte đầu 0x80–0xBF (chỉ hợp lệ ở vị trí tiếp diễn); chuỗi thiếu byte tiếp diễn; C0/C1 dẫn tới mã hóa quá dài; F5–FF không hợp lệ; mẫu ba byte mã hóa surrogate; mẫu bốn byte vượt U+10FFFF. Decoder nghiêm ngặt phải báo lỗi thay vì tự ý đoán ký tự.

## 5. Quy tắc giải mã UTF-16, thứ tự byte và BOM

UTF-16 gồm các **đơn vị 16 bit**:

- Code point U+0000–U+D7FF và U+E000–U+FFFF mã hóa bằng một đơn vị 16 bit.
- Code point U+10000–U+10FFFF mã hóa bằng hai đơn vị: **high surrogate** (D800–DBFF) rồi **low surrogate** (DC00–DFFF).
- Một surrogate đứng đơn lẻ không phải Unicode scalar value hợp lệ.
- Với **UTF-16BE**, đơn vị 0x0041 thành 00 41; với **UTF-16LE**, thành 41 00.

**BOM (Byte Order Mark)** là dấu chỉ thứ tự byte ở đầu dữ liệu:

| Dữ liệu | Byte BOM | Ý nghĩa |
|---|---|---|
| UTF-8 | EF BB BF (tùy chọn) | Không cần cho thứ tự byte; nếu xuất hiện, cần có quy tắc xử lý nhất quán |
| UTF-16BE | FE FF | Thường dùng làm dấu hiệu big-endian |
| UTF-16LE | FF FE | Thường dùng làm dấu hiệu little-endian |

Trong Java, **StandardCharsets.UTF_16** hiểu BOM khi giải mã; nếu không có BOM, bộ giải mã UTF-16 mặc định big-endian. Các charset **UTF_16BE** và **UTF_16LE** có thứ tự byte cố định: không dựa vào BOM để đổi thứ tự. Khi encode bằng **UTF_16** của Java, output thường bắt đầu bằng BOM FE FF; muốn byte không BOM theo thứ tự đã biết thì chọn **UTF_16BE** hoặc **UTF_16LE**.

BOM UTF-8 không phải dấu bắt buộc, cũng không phải cách nhận diện charset chắc chắn cho mọi tệp. Tùy bộ đọc và định dạng dữ liệu, EF BB BF có thể trở thành U+FEFF ở đầu String; nếu chuẩn đầu vào yêu cầu bỏ BOM, hãy xử lý có chủ đích. Không xóa U+FEFF ở giữa văn bản một cách mù quáng.

## 6. Java 17+: giải mã đúng và kiểm tra kết quả

Ví dụ sau **độc lập, biên dịch và chạy được** bằng Java 17+. Mỗi lần giải mã đều dùng charset được chỉ định và **REPORT** để tránh âm thầm thay byte hỏng bằng ký tự khác.

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class CharsetLookupDemo {
    private static String decodeStrict(byte[] bytes, Charset charset)
            throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    public static void main(String[] args) throws CharacterCodingException {
        byte[] utf8 = HexFormat.of().parseHex("41C3A9E4B8ADF09F9880");
        String text = decodeStrict(utf8, StandardCharsets.UTF_8);

        System.out.println("text=" + text);
        System.out.println("utf8Bytes=" + utf8.length);
        System.out.println("utf16Units=" + text.length());
        System.out.println("codePoints=" + text.codePointCount(0, text.length()));

        byte[] latin1 = HexFormat.of().parseHex("E9");
        System.out.println("latin1=" +
                decodeStrict(latin1, StandardCharsets.ISO_8859_1));

        byte[] cp1252 = HexFormat.of().parseHex("80");
        System.out.println("cp1252=" +
                decodeStrict(cp1252, Charset.forName("windows-1252")));

        String hex = HexFormat.of().withUpperCase()
                .formatHex(text.getBytes(StandardCharsets.UTF_8));
        System.out.println("hex=" + hex);

        byte[] invalidUtf8 = HexFormat.of().parseHex("C328");
        try {
            decodeStrict(invalidUtf8, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            System.out.println("invalidUtf8=" +
                    e.getClass().getSimpleName());
        }
    }
}
~~~

Lưu với tên **CharsetLookupDemo.java** trong UTF-8, chạy:

~~~bash
javac -encoding UTF-8 CharsetLookupDemo.java
java CharsetLookupDemo
~~~

Đầu ra:

~~~text
text=Aé中😀
utf8Bytes=10
utf16Units=5
codePoints=4
latin1=é
cp1252=€
hex=41C3A9E4B8ADF09F9880
invalidUtf8=MalformedInputException
~~~

Chú ý **HexFormat.parseHex(...)** nhận chuỗi hex liền nhau trong ví dụ trên. Nếu đầu vào có dấu cách như “41 C3 A9”, cần bỏ dấu cách hoặc phân tích theo delimiter phù hợp; không truyền trực tiếp rồi giả định hàm luôn bỏ mọi whitespace.

So sánh **new String(bytes, StandardCharsets.UTF_8)**: tiện lợi với dữ liệu đã xác thực, nhưng constructor dạng này có thể thay dãy malformed/unmappable bằng chuỗi thay thế. Khi yêu cầu tính toàn vẹn (cấu hình, giao thức, nội dung cần xác nhận byte chính xác), hãy dùng **CharsetDecoder** với **CodingErrorAction.REPORT** để nhận **CharacterCodingException**.

## 7. Áp dụng trong I/O: đọc/ghi luôn nêu charset

Với tệp văn bản UTF-8, các API mức cao của Java nhận charset rõ ràng:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextFileUtf8Demo {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("sample-utf8.txt");
        Files.writeString(path, "Tiếng Việt và 😀",
                StandardCharsets.UTF_8);

        String result = Files.readString(path, StandardCharsets.UTF_8);
        System.out.println(result);
    }
}
~~~

Lưu **TextFileUtf8Demo.java** dưới UTF-8, chạy **javac -encoding UTF-8 TextFileUtf8Demo.java** rồi **java TextFileUtf8Demo**. Stdout là:

~~~text
Tiếng Việt và 😀
~~~

Mẫu đọc luồng bằng Reader khi xử lý dần: **new InputStreamReader(inputStream, StandardCharsets.UTF_8)**. Đối với dữ liệu cần kiểm tra nghiêm ngặt, dùng constructor nhận **CharsetDecoder** đã đặt REPORT. Bởi vì Reader có thể giữ một phần dãy UTF-8 ở cuối buffer để chờ byte tiếp theo, không tự cắt mỗi chunk thành một String riêng bằng **new String(chunk, UTF_8)**: nếu cắt giữa các byte của cùng ký tự, ký tự sẽ hỏng. Giữ một decoder xuyên suốt luồng hoặc đọc qua Reader phù hợp.

Nếu dữ liệu là **binary** (ZIP, ảnh, protobuf, file byte thuần), không decode toàn bộ như văn bản. Chỉ những trường được đặc tả là text mới dùng charset.

Đừng để việc đọc/ghi phụ thuộc charset mặc định của hệ thống: **new String(bytes)** và **String.getBytes()** không truyền charset có thể phụ thuộc runtime/cấu hình. Từ JDK 18, default charset theo JEP 400 thông thường là UTF-8, nhưng dữ liệu và môi trường vẫn có các ngoại lệ; với Java 17 đặc biệt không nên suy đoán. Viết rõ charset mà định dạng tệp/giao thức yêu cầu.

## 8. Xử lý lỗi: REPORT, REPLACE và IGNORE

| Hành động | Khi dữ liệu sai | Khi nên dùng |
|---|---|---|
| **CodingErrorAction.REPORT** | Ném lỗi (ví dụ MalformedInputException, UnmappableCharacterException) | Đọc giao thức/cấu hình, import cần phát hiện dữ liệu hỏng, kiểm thử |
| **CodingErrorAction.REPLACE** | Chèn chuỗi thay thế do decoder/encoder cấu hình | Hiển thị dữ liệu có thể chấp nhận mất thông tin, cần cảnh báo người dùng |
| **CodingErrorAction.IGNORE** | Bỏ qua phần không hợp lệ | Chỉ khi đặc tả cho phép mất dữ liệu; khó dùng cho dữ liệu quan trọng |

Ví dụ C3 28 không phải dãy UTF-8 hợp lệ: C3 là byte đầu cần thêm một byte dạng 10xxxxxx nhưng 28 là dấu mở ngoặc ASCII. Nếu decoder thay thế lỗi, kết quả thông thường là **�(** (U+FFFD rồi dấu ngoặc); với REPORT, Java báo **MalformedInputException**.

Trường hợp encode cũng có lỗi: chuỗi chứa **€** hoặc **中** không biểu diễn được trong ISO-8859-1. Encoder với REPORT báo **UnmappableCharacterException**; nếu thay thế, byte thu được không còn tái tạo nguyên văn Unicode ban đầu. Bất kỳ pipeline cần round-trip chính xác đều nên dùng charset đủ khả năng biểu diễn dữ liệu, thường là UTF-8, và quy định xử lý lỗi rõ ràng.

## 9. Mojibake: ký tự bị biến thành “Ã©”, “â‚¬” và cách chẩn đoán

**Mojibake** là văn bản bị rối do byte tạo bởi charset này nhưng giải bằng charset khác. Ví dụ chữ **é** có UTF-8 hex **C3 A9**. Nếu hai byte ấy được giải bằng windows-1252, chúng thành hai ký tự **Ã©**, không còn é. Tương tự **€** có UTF-8 **E2 82 AC**, đọc nhầm windows-1252 thường hiển thị **â‚¬**.

Quy trình tìm nguyên nhân:

1. Giữ **byte gốc**; kiểm tra các byte hex ở vùng lỗi và nguồn phát sinh dữ liệu (tệp, HTTP, database, message queue).
2. Lấy charset từ **quy ước/metadata đáng tin** (đặc tả file, header Content-Type có charset hợp lệ, schema/giao thức, cấu hình bên ghi).
3. Giải thử với charset được khai báo và kiểm tra cả ký tự đại diện ngoài ASCII; đừng chỉ nhìn đoạn tiếng Anh vì ASCII 0x00–0x7F giống nhau ở nhiều charset.
4. Nếu dữ liệu đã bị decode sai và encode lại nhiều lần, cần biết **từng bước chuyển đổi** mới có thể phục hồi; chỉ “đổi charset lúc hiển thị” có thể không đủ.
5. Khi thấy U+FFFD hoặc dấu hỏi đã được lưu thành byte, có thể thông tin gốc **đã mất**. Sửa đúng lỗi ở nơi đọc/ghi và tạo lại dữ liệu từ byte nguồn nếu còn.

Không có bộ dò charset nào bảo đảm đúng cho mọi byte. Dùng heuristic chỉ khi thiếu đặc tả và chấp nhận khả năng đoán sai; với dữ liệu quan trọng, yêu cầu nguồn cung cấp charset chính xác.

**Đừng nhầm charset với normalization.** Chữ nhìn giống **é** có thể là U+00E9 hoặc tổ hợp U+0065 U+0301; UTF-8 sẽ tạo byte khác nhau dù cả hai đều giải mã đúng. Charset không tự bảo đảm NFC/NFD, và việc chuẩn hóa Unicode là bước xử lý riêng khi nghiệp vụ cần.

## 10. Checklist tra cứu nhanh

- Xác định **byte gốc** và **charset nguồn** trước khi viết lệnh decode.
- Nếu cần chuyển trực tiếp byte → String trong Java: **new String(bytes, charset)** cho trường hợp thông thường; **charset.newDecoder() + REPORT** khi muốn phát hiện lỗi.
- Nếu cần String → byte: **text.getBytes(charset)**; dùng **CharsetEncoder + REPORT** khi sợ mất ký tự.
- Đọc/ghi tệp: ưu tiên **Files.readString/writeString** với **StandardCharsets.UTF_8** hoặc charset đặc tả.
- Gặp dữ liệu UTF-16: kiểm tra thứ tự byte, BOM, surrogate pair.
- Gặp lỗi tiếng Việt: kiểm tra charset phía tạo byte và phía decode, không tự sửa bằng phép replace các ký tự hiển thị.
- Khi ghi log kiểm tra: in hex byte, code point (ví dụ **text.codePoints()**), charset và cách xử lý lỗi; phân biệt **String.length()** với số code point.
- Với dữ liệu đa chunk hoặc streaming: giữ decoder qua các lần đọc để không làm đứt một ký tự UTF-8.
- Chỉ convert văn bản; dữ liệu nhị phân không có charset tổng quát.

## 11. Nguồn tra cứu chuẩn

- [Java 17 — Charset](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html): quy tắc encode/decode và charset.
- [Java 17 — CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html): malformed/unmappable và CodingErrorAction.
- [Java 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): các charset chuẩn được hỗ trợ.
- [Unicode — UTF-8 / UTF-16 (Unicode Standard)](https://www.unicode.org/versions/latest/core-spec/chapter-3/): định dạng và kiểm tra tính hợp lệ của encoding.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-003">Bảng biến đổi trạng thái Buffer</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng biến đổi trạng thái Buffer trong Java NIO

`Buffer` trong `java.nio` là mô hình **cửa sổ truy cập dữ liệu có trạng thái**. Khi đọc hoặc ghi qua `ByteBuffer`, `CharBuffer` hay các buffer kiểu nguyên thủy khác, dữ liệu được quản lý bằng bốn mốc: `capacity`, `limit`, `position` và `mark`. Bảng dưới đây giúp dự đoán chính xác trạng thái trước và sau mỗi thao tác, đặc biệt trong chu trình **ghi dữ liệu → `flip()` → đọc dữ liệu → `clear()` hoặc `compact()`**.

## 1. Quy ước đọc bảng trạng thái

| Ký hiệu | Ý nghĩa | Quy tắc |
|---|---|---|
| `C` = `capacity()` | Tổng số phần tử mà buffer chứa được | Không đổi trong suốt vòng đời của buffer đó |
| `L` = `limit()` | Biên trên *loại trừ* của vùng hiện được phép truy cập | `0 ≤ L ≤ C` |
| `P` = `position()` | Chỉ số của phần tử kế tiếp khi đọc/ghi tương đối | `0 ≤ P ≤ L` |
| `M` = `mark` | Vị trí đã đánh dấu bằng `mark()`; không có getter công khai | Chưa đặt (ký hiệu `—`), hoặc `0 ≤ M ≤ P` |
| `R` = `remaining()` | Số phần tử còn lại từ vị trí hiện tại đến giới hạn | `R = L − P` |

**Bất biến:** nếu mark đang tồn tại thì `0 ≤ M ≤ P ≤ L ≤ C`. `P` chỉ là con trỏ logic: nó **không nhất thiết** phản ánh phần tử nào trong bộ nhớ đã được khởi tạo. Các thao tác thay đổi trạng thái có thể làm mất mark; Java sẽ ném `InvalidMarkException` khi gọi `reset()` mà không còn mark hợp lệ.

Ví dụ: `ByteBuffer.allocate(8)` tạo `(P=0, L=8, C=8, M=—)`; `remaining() = 8`. Vùng thao tác tương đối là `[P, L)`. Phần tử ở chỉ số `L` **không** thuộc vùng hợp lệ, kể cả khi `L < C`.

## 2. Bảng biến đổi trạng thái chuẩn

Trong bảng: `P₀, L₀, C₀, M₀` là trạng thái trước thao tác; `P₁, L₁, M₁` là trạng thái sau. `C` luôn bằng `C₀`. “Giữ nguyên” nghĩa là không đổi con trỏ/mark, không khẳng định dữ liệu bộ nhớ giống nhau.

| Thao tác | Điều kiện/ý nghĩa | Trạng thái sau | Tác động đến mark và dữ liệu |
|---|---|---|---|
| `allocate(C)` | Tạo buffer mới với `C ≥ 0` | `P₁=0, L₁=C` | `M₁=—`; phần tử ban đầu bằng giá trị mặc định của kiểu |
| `put(value)` | Ghi **tương đối** một phần tử, yêu cầu `P₀ < L₀` | `P₁=P₀+1, L₁=L₀` | Mark giữ nguyên; ghi dữ liệu tại vị trí `P₀` |
| `get()` | Đọc **tương đối** một phần tử, yêu cầu `P₀ < L₀` | `P₁=P₀+1, L₁=L₀` | Mark giữ nguyên; đọc dữ liệu tại `P₀` |
| `put(src)` / `get(dst)` | Ghi/đọc tương đối `n` phần tử; cần `n ≤ L₀−P₀` | `P₁=P₀+n, L₁=L₀` | Mark giữ nguyên nếu thao tác thành công; vùng dữ liệu liên quan được ghi/đọc |
| `get(i)` / `put(i, value)` | Truy cập **tuyệt đối**; yêu cầu `0 ≤ i < L₀` | `P₁=P₀, L₁=L₀` | Mark giữ nguyên; `put` thay đổi dữ liệu tại `i` |
| `flip()` | Chuyển từ giai đoạn ghi sang đọc **phần đã ghi** | `P₁=0, L₁=P₀` | Xóa mark; **không** sao chép/xóa dữ liệu |
| `clear()` | Chuẩn bị ghi lại toàn bộ vùng chứa | `P₁=0, L₁=C₀` | Xóa mark; **không** xóa byte/ký tự cũ |
| `rewind()` | Đọc lại từ đầu trong giới hạn hiện hành | `P₁=0, L₁=L₀` | Xóa mark; dữ liệu không đổi |
| `compact()` | Giữ phần **chưa đọc** `[P₀,L₀)`, chuẩn bị ghi nối tiếp | `P₁=L₀−P₀, L₁=C₀` | Xóa mark; dồn `L₀−P₀` phần tử chưa đọc về đầu buffer |
| `mark()` | Ghi nhớ vị trí hiện tại | `P₁=P₀, L₁=L₀, M₁=P₀` | Đặt/ghi đè mark; dữ liệu không đổi |
| `reset()` | Quay về mark đã đặt | `P₁=M₀, L₁=L₀, M₁=M₀` | Giữ mark; nếu không có mark thì ném `InvalidMarkException` |
| `position(p)` | Đặt vị trí mới, cần `0 ≤ p ≤ L₀` | `P₁=p, L₁=L₀` | Nếu `M₀ > p` thì xóa mark; ngược lại giữ |
| `limit(l)` | Đặt giới hạn mới, cần `0 ≤ l ≤ C₀` | `L₁=l, P₁=min(P₀,l)` | Nếu `M₀ > l` thì xóa mark; ngược lại giữ |
| `remaining()` / `hasRemaining()` | Truy vấn `L−P` / `P<L` | Mọi mốc giữ nguyên | Không làm thay đổi dữ liệu |

**Lưu ý về API:** `flip()`, `clear()`, `rewind()`, `mark()`, `reset()`, `position(int)` và `limit(int)` có ở `Buffer`. `get`, `put` và `compact` là phương thức trên các buffer có kiểu như `ByteBuffer`; `compact()` không phải phương thức dùng trực tiếp trên biến chỉ có kiểu `Buffer`. Không phải buffer nào cũng hỗ trợ ghi; buffer chỉ đọc sẽ từ chối `put` và `compact`.

## 3. Bảng diễn tiến thực tế: ghi, đọc một phần, dồn và ghi tiếp

Với buffer chứa tám byte, ví dụ sau cho thấy **mark**, phần dữ liệu chưa đọc và trạng thái sau `compact()`. Ký hiệu `—` là chưa có mark; chuỗi trong cột “Dữ liệu liên quan” chỉ mô tả phần đã ghi/còn cần đọc, không cam kết nội dung các ô ngoài phạm vi đó.

| Bước | Thao tác | P | L | C | M | R=L−P | Dữ liệu liên quan |
|---:|---|---:|---:|---:|---:|---:|---|
| 1 | `allocate(8)` | 0 | 8 | 8 | — | 8 | Chưa ghi dữ liệu ứng dụng |
| 2 | `put("ABCD")` | 4 | 8 | 8 | — | 4 | `[0..3] = ABCD` |
| 3 | `flip()` | 0 | 4 | 8 | — | 4 | Cửa sổ đọc `ABCD` |
| 4 | `get(2 byte)` → `AB` | 2 | 4 | 8 | — | 2 | Chưa đọc: `CD` |
| 5 | `mark()` | 2 | 4 | 8 | 2 | 2 | Đánh dấu trước `C` |
| 6 | `get()` → `C` | 3 | 4 | 8 | 2 | 1 | Chưa đọc theo vị trí hiện tại: `D` |
| 7 | `reset()` | 2 | 4 | 8 | 2 | 2 | Quay lại trước `CD` |
| 8 | `compact()` | 2 | 8 | 8 | — | 6 | Dồn `CD` về chỉ số `0,1` |
| 9 | `put("EF")` | 4 | 8 | 8 | — | 4 | `[0..3] = CDEF` |
| 10 | `flip()` | 0 | 4 | 8 | — | 4 | Cửa sổ đọc `CDEF` |
| 11 | `get(4 byte)` → `CDEF` | 4 | 4 | 8 | — | 0 | Hết dữ liệu hợp lệ trong cửa sổ |
| 12 | `rewind()` | 0 | 4 | 8 | — | 4 | Có thể đọc lại `CDEF` |
| 13 | `clear()` | 0 | 8 | 8 | — | 8 | Dữ liệu cũ còn có thể nằm trong bộ nhớ, nhưng không còn được xem là một bản ghi hợp lệ |

Điểm dễ nhầm ở bước 8: `compact()` lấy **phần chưa đọc tính từ position hiện tại**, chứ không lấy toàn bộ dữ liệu từng ghi. Nếu không gọi `reset()` ở bước 7, `compact()` chỉ dồn `D` và cho `P=1`.

## 4. Chương trình Java 17+ chạy độc lập

Lưu tệp đúng tên `BufferStateDemo.java`:

~~~java
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BufferStateDemo {
    private static void state(String action, ByteBuffer buffer) {
        System.out.printf(
                "%s: P=%d, L=%d, C=%d, R=%d%n",
                action,
                buffer.position(),
                buffer.limit(),
                buffer.capacity(),
                buffer.remaining()
        );
    }

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        state("allocate", buffer);

        buffer.put("ABCD".getBytes(StandardCharsets.US_ASCII));
        state("put ABCD", buffer);

        buffer.flip();
        state("flip", buffer);

        byte[] firstTwo = new byte[2];
        buffer.get(firstTwo);
        System.out.println("read 2 = " + new String(firstTwo, StandardCharsets.US_ASCII));
        state("get 2", buffer);

        buffer.mark();
        state("mark", buffer);

        System.out.println("next = " + (char) buffer.get());
        state("get C", buffer);

        buffer.reset();
        state("reset", buffer);

        buffer.compact();
        state("compact", buffer);

        buffer.put("EF".getBytes(StandardCharsets.US_ASCII));
        state("put EF", buffer);

        buffer.flip();
        state("flip 2", buffer);

        byte[] finalBytes = new byte[buffer.remaining()];
        buffer.get(finalBytes);
        System.out.println("final = " + new String(finalBytes, StandardCharsets.US_ASCII));
        state("get all", buffer);

        buffer.rewind();
        state("rewind", buffer);

        buffer.clear();
        state("clear", buffer);
    }
}
~~~

Chạy trong thư mục chứa tệp:

~~~bash
javac BufferStateDemo.java
java BufferStateDemo
~~~

Stdout dự kiến chính xác:

~~~text
allocate: P=0, L=8, C=8, R=8
put ABCD: P=4, L=8, C=8, R=4
flip: P=0, L=4, C=8, R=4
read 2 = AB
get 2: P=2, L=4, C=8, R=2
mark: P=2, L=4, C=8, R=2
next = C
get C: P=3, L=4, C=8, R=1
reset: P=2, L=4, C=8, R=2
compact: P=2, L=8, C=8, R=6
put EF: P=4, L=8, C=8, R=4
flip 2: P=0, L=4, C=8, R=4
final = CDEF
get all: P=4, L=4, C=8, R=0
rewind: P=0, L=4, C=8, R=4
clear: P=0, L=8, C=8, R=8
~~~

Kết quả minh họa việc `reset()` khôi phục `P=2` nhưng không đổi `L=4`, sau đó `compact()` giữ chính xác `CD`; `clear()` chỉ đặt lại giới hạn logic.

## 5. Những nhánh biến đổi thường bị bỏ sót

### Giảm position hoặc limit có thể vô hiệu hóa mark

Giả sử trạng thái là `(P=5, L=8, C=8, M=4)`.

- Gọi `position(2)` ⇒ `(P=2, L=8, M=—)` vì mark `4 > 2`. Gọi `reset()` lúc này ném `InvalidMarkException`.
- Gọi `limit(3)` **trên trạng thái gốc** ⇒ `(P=3, L=3, M=—)`; position bị ép xuống `3` và mark `4` bị bỏ.
- Gọi `limit(6)` **trên trạng thái gốc** ⇒ `(P=5, L=6, M=4)`; mark vẫn hợp lệ.
- Gọi `position(6)` **trên trạng thái gốc** ⇒ `(P=6, L=8, M=4)`; mark vẫn hợp lệ.

Hai setter chỉ thay đổi trạng thái nếu đối số trong khoảng hợp lệ. `position(p)` với `p > limit`, hoặc `limit(l)` với `l > capacity`, sẽ ném `IllegalArgumentException`.

### Truy cập tuyệt đối và truy cập tương đối

Giả sử `(P=2, L=5, C=8)`:

- `get()` lấy chỉ số `2` rồi tăng `P` lên `3`.
- `get(4)` lấy chỉ số `4` và **không tăng `P`**.
- `put(1, (byte) 7)` ghi chỉ số `1` và **không tăng `P`**.
- `get(5)` không hợp lệ vì `limit` là biên loại trừ, mặc dù `5 < capacity`.

Với `ByteBuffer`, đọc tương đối khi không đủ dữ liệu ném `BufferUnderflowException`; ghi tương đối khi không đủ chỗ ném `BufferOverflowException`; chỉ số tuyệt đối ngoài `[0, limit)` thường ném `IndexOutOfBoundsException`. Ghi vào buffer chỉ đọc ném `ReadOnlyBufferException`. Kiểm tra `hasRemaining()` hoặc `remaining()` giúp tránh đọc/ghi vượt cửa sổ.

### `slice()`, `duplicate()` và trạng thái của view

Các view chia sẻ **nội dung lưu trữ** nhưng có các mốc vị trí độc lập:

| Tạo từ buffer nguồn | Position của view | Limit / Capacity của view | Quan hệ với dữ liệu và mark |
|---|---|---|---|
| `slice()` | `0` | Bằng `remaining()` của nguồn tại lúc tạo | Chỉ ánh xạ vùng `[P,L)` của nguồn; mark mới chưa xác định; dùng chung dữ liệu |
| `duplicate()` | Bằng `P` của nguồn | Bằng `L/C` của nguồn | Copy trạng thái gồm mark (nếu có); dùng chung dữ liệu, từ đó các mốc được cập nhật độc lập |
| `asReadOnlyBuffer()` trên `ByteBuffer` | Bằng `P` của nguồn | Bằng `L/C` của nguồn | Chia sẻ dữ liệu và sao chép mốc/mark ban đầu; cấm ghi qua view |

Ví dụ: nguồn có `(P=2, L=5, C=8)` thì `slice()` tạo view `(P=0, L=3, C=3)`. Sau khi `slice()`, thay đổi position của view không di chuyển position của nguồn. Thay đổi **nội dung** qua một view cho phép ghi vẫn có thể nhìn thấy từ view khác vì chúng chia sẻ vùng lưu trữ.

## 6. Chọn đúng thao tác trong luồng I/O

| Nhu cầu | Thao tác | Lý do |
|---|---|---|
| Ghi vào buffer trống và chuẩn bị tiêu thụ dữ liệu vừa ghi | `flip()` | Giới hạn vùng đọc theo `position` cũ |
| Đọc lại dữ liệu trong giới hạn hiện tại | `rewind()` | Giữ `limit`, đưa `position` về `0` |
| Đã xử lý hết dữ liệu, muốn nhận mẻ mới từ đầu | `clear()` | Mở toàn bộ sức chứa để ghi; không cần giữ phần chưa đọc |
| Đã tiêu thụ một phần, cần nối mẻ mới vào phần chưa xử lý | `compact()` | Dồn phần `remaining()` xuống đầu và mở chỗ cho mẻ tiếp theo |
| Muốn thử đọc rồi quay lại | `mark()` → đọc → `reset()` | Khôi phục position nếu mark chưa bị vô hiệu hóa |
| Chỉ muốn nhìn phần tử tại chỉ số xác định | `get(index)` | Không làm lệch position của lần đọc tương đối tiếp theo |

Khi nhận dữ liệu phân mảnh từ `SocketChannel`, thường dùng **ghi từ channel → `flip()` → tiêu thụ các thông điệp hoàn chỉnh → `compact()` → đọc tiếp**. Nếu một thông điệp còn thiếu byte, `compact()` giữ phần dở dang để ghép với lần đọc tiếp. Chỉ dùng `clear()` khi chắc chắn không cần phần còn lại. Với việc ghi buffer ra channel, một lần `write(buffer)` có thể chưa tiêu thụ hết `remaining()`; phải dựa vào trạng thái sau ghi để xử lý phần còn lại, không giả định mọi byte đã được gửi.

**Quy tắc nhớ nhanh:** `flip` đặt `limit = position cũ` để đọc; `clear` đặt `limit = capacity` để ghi lại; `compact` giữ `remaining` và đặt `position = số phần tử vừa dồn`. Không thao tác nào trong ba thao tác này tự thực hiện I/O với tệp/socket.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-004">Bảng OpenOption, CopyOption, LinkOption và FileVisitOption</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng OpenOption, CopyOption, LinkOption và FileVisitOption trong Java NIO.2

## 1. Mục tiêu và bản đồ các nhóm option

Trong `java.nio.file` (NIO.2), cùng một đối tượng `Path` có thể được **mở để đọc/ghi**, **sao chép/di chuyển**, **kiểm tra symbolic link** hoặc **duyệt cây thư mục**. Mỗi thao tác nhận một họ option khác nhau. Đọc đúng chữ ký API quan trọng hơn việc nhớ tên một enum.

| Nhóm | Kiểu Java | Tác dụng chính | API điển hình |
| --- | --- | --- | --- |
| `OpenOption` | **Interface** | Quy định cách mở/tạo tệp hoặc channel | `Files.newOutputStream`, `Files.newByteChannel`, `FileChannel.open`, `Files.writeString` |
| `StandardOpenOption` | **Enum implements OpenOption** | `READ`, `WRITE`, `APPEND`, `CREATE`, ... | Các API nhận `OpenOption` tương ứng |
| `CopyOption` | **Interface** | Quy định cách copy/move | `Files.copy(Path, Path, CopyOption...)`, `Files.move` |
| `StandardCopyOption` | **Enum implements CopyOption** | `REPLACE_EXISTING`, `COPY_ATTRIBUTES`, `ATOMIC_MOVE` | `Files.copy` / `Files.move` tùy option |
| `LinkOption` | **Enum implements OpenOption, CopyOption** | `NOFOLLOW_LINKS`: không dereference symbolic link ở API hỗ trợ | `Files.exists`, `Files.readAttributes`, `Files.copy`, `Files.newByteChannel` |
| `FileVisitOption` | **Enum riêng** | `FOLLOW_LINKS`: đi qua symlink khi duyệt cây | `Files.walkFileTree`, `Files.walk` |

**Chú ý:** `OpenOption` và `CopyOption` *không phải enum*; không tồn tại `OpenOption.READ` hay `CopyOption.REPLACE_EXISTING`. Hằng số nằm trong `StandardOpenOption` và `StandardCopyOption`. `FileVisitOption` cũng không phải tham số cho `Files.copy`, còn `LinkOption.NOFOLLOW_LINKS` không phải tùy chọn cho `Files.walkFileTree`.

Các import thường gặp:

```java
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.CopyOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.FileVisitOption;
```

## 2. Bảng tra cứu OpenOption / StandardOpenOption

`OpenOption` là kiểu tham số; `StandardOpenOption` cung cấp 10 giá trị chuẩn. Các provider có thể hỗ trợ option bổ sung, và từng phương thức có thể cho phép tập con khác nhau.

| `StandardOpenOption` | Ý nghĩa | Khi nào dùng / lưu ý |
| --- | --- | --- |
| `READ` | Mở tệp để đọc | Mặc định của `Files.newInputStream(path)` và `Files.newByteChannel(path)` nếu không chỉ định quyền ghi; tệp cần tồn tại. |
| `WRITE` | Mở tệp để ghi | Không tự hàm ý ghi nối cuối hoặc cắt nội dung; thường kết hợp `CREATE`, `TRUNCATE_EXISTING`. |
| `APPEND` | Mỗi lần ghi vào cuối tệp | Hàm ý quyền ghi ở các channel chuẩn; với `newByteChannel` không kết hợp `READ` hoặc `TRUNCATE_EXISTING`. Ghi nối cuối đồng thời không được bảo đảm atomic trên mọi hệ tệp. |
| `TRUNCATE_EXISTING` | Cắt tệp hiện có về độ dài 0 khi mở để ghi | Chỉ có tác dụng với quyền ghi; **nguy cơ mất dữ liệu** nếu dùng nhầm. |
| `CREATE` | Tạo tệp nếu chưa tồn tại, nếu đã có thì mở tệp đó | Hữu ích với ghi có thể tạo mới; bị bỏ qua khi đồng thời dùng `CREATE_NEW`, hoặc chỉ mở đọc. |
| `CREATE_NEW` | Chỉ tạo tệp mới, thất bại nếu đường dẫn đã có | Kiểm tra tồn tại và tạo tệp được thực hiện atomic đối với thao tác hệ tệp; hữu ích để tránh ghi đè ngoài ý muốn. |
| `DELETE_ON_CLOSE` | Nỗ lực xóa tệp lúc đóng handle | Là *best effort*, không phải lời hứa xóa an toàn; tránh dùng cho dữ liệu phải lưu. Có thể không cho mở symlink tùy provider. |
| `SPARSE` | Gợi ý tạo sparse file khi tạo tệp mới | Chỉ là hint; provider/hệ tệp không hỗ trợ có thể bỏ qua, không phải cách bảo đảm tiết kiệm dung lượng. |
| `SYNC` | Yêu cầu cập nhật nội dung **và metadata** đồng bộ xuống thiết bị lưu trữ | Có thể tốn chi phí I/O; khác với bảo đảm giao dịch hoặc ghi nguyên tử. |
| `DSYNC` | Yêu cầu đồng bộ các cập nhật **nội dung** xuống thiết bị lưu trữ | Phạm vi bảo đảm hẹp hơn `SYNC`; hiệu năng và hỗ trợ phụ thuộc hệ thống. |

### 2.1. Mặc định tùy theo phương thức

| Gọi API | Mặc định khi không truyền option | Hệ quả |
| --- | --- | --- |
| `Files.newInputStream(path)` | `READ` | Mở luồng đọc; lỗi nếu không đọc được. |
| `Files.newOutputStream(path)` | `CREATE + TRUNCATE_EXISTING + WRITE` | Ghi đè nội dung tệp cũ hoặc tạo tệp mới. |
| `Files.newByteChannel(path)` | `READ` | Channel chỉ đọc nếu không truyền option. |
| `Files.writeString(path, text)` | `CREATE + TRUNCATE_EXISTING + WRITE` | Lời gọi lần sau mặc định thay nội dung trước. |
| `Files.write(path, bytes)` | `CREATE + TRUNCATE_EXISTING + WRITE` | Hành vi tương tự khi ghi mảng byte. |

Hãy nhìn **chữ ký của API** khi áp dụng: `newByteChannel(Path, OpenOption...)` và `FileChannel.open` hỗ trợ tổ hợp quyền đọc/ghi, còn `newOutputStream` không nhận `READ`; `Files.newInputStream` không phải công cụ để tạo/ghi file. Tập option bổ sung không đồng nhất cho mọi overload.

### 2.2. Các tổ hợp điển hình

```java
Path path = Path.of("data.txt");

// Tạo mới duy nhất, lỗi nếu data.txt đã tồn tại:
Files.writeString(path, "first\n",
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

// Ghi nối cuối tệp hiện có, không tự tạo mới:
Files.writeString(path, "next\n", StandardOpenOption.APPEND);

// Mở nối cuối, tạo mới nếu chưa có:
try (var out = Files.newOutputStream(path,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
    out.write("tail\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
}

// Chủ động thay toàn bộ nội dung:
Files.writeString(path, "replaced\n",
        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
```

Đoạn trên là *snippet* minh họa, với `path` nằm trong thư mục được phép ghi. Lệnh cuối đòi tệp đã tồn tại; để đồng thời cho phép tạo mới thì thêm `CREATE`. Dùng `try-with-resources` khi trực tiếp mở stream/channel để đóng tài nguyên kể cả khi có exception.

**So sánh `CREATE` và `CREATE_NEW`:** `CREATE` không ngăn việc mở tệp cũ; `CREATE_NEW` báo thất bại ngay khi đường dẫn đã tồn tại (kể cả symlink). `CREATE_NEW` hữu ích hơn chuỗi `if (!Files.exists(path)) Files.writeString(...)` vì phép kiểm tra rồi hành động tách rời có race condition (TOCTOU).

**So sánh `APPEND` và `TRUNCATE_EXISTING`:** `APPEND` bảo toàn byte hiện có và thêm byte vào cuối; `TRUNCATE_EXISTING` xóa byte hiện có ngay khi mở để ghi. Đừng kết hợp hai option này. `APPEND` không đồng nghĩa khóa tệp hoặc ghi một bản ghi log nguyên tử giữa nhiều process.

## 3. Bảng tra cứu CopyOption / StandardCopyOption

`CopyOption` là interface. `StandardCopyOption` chỉ có **ba** hằng số chuẩn:

| `StandardCopyOption` | Đối với `Files.copy` | Đối với `Files.move` |
| --- | --- | --- |
| `REPLACE_EXISTING` | Cho phép thay target đã có; target là thư mục không rỗng có thể không thay được. | Cho phép thay target đã có với giới hạn hệ tệp/tệp đích. |
| `COPY_ATTRIBUTES` | Thử sao chép thuộc tính của source (ví dụ last-modified time); nhóm thuộc tính và độ chính xác phụ thuộc hệ tệp/provider. | **Không phải** option chuẩn được `Files.move` liệt kê để cấu hình move; move có quy tắc giữ thuộc tính riêng. |
| `ATOMIC_MOVE` | **Không dùng cho `Files.copy`**; đây là option của move. | Yêu cầu move như một thao tác hệ tệp nguyên tử; nếu không hỗ trợ sẽ ném `AtomicMoveNotSupportedException`. Các option khác bị bỏ qua khi dùng `ATOMIC_MOVE`. |

Ví dụ:

```java
Path source = Path.of("source.txt");
Path target = Path.of("backup.txt");

// Sao chép, cho phép thay thế và cố gắng giữ metadata:
Files.copy(source, target,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES);

// Đổi tên hoặc chuyển sang vị trí khác:
Files.move(target, Path.of("archive.txt"),
        StandardCopyOption.REPLACE_EXISTING);

// Chỉ yêu cầu chuyển nguyên tử khi hệ tệp cho phép:
Files.move(Path.of("draft.txt"), Path.of("published.txt"),
        StandardCopyOption.ATOMIC_MOVE);
```

Đây là *snippet*, yêu cầu các đường dẫn nguồn tồn tại. Khi dùng `ATOMIC_MOVE`, đừng giả định rằng ghép thêm `REPLACE_EXISTING` sẽ bắt buộc thay thế target: tài liệu JDK quy định các option khác bị bỏ qua, và nếu target đã tồn tại thì provider có thể thay hoặc báo lỗi. Với ứng dụng cần chính sách ghi đè rõ ràng, hãy thiết kế xử lý ngoại lệ và xác minh hành vi trên hệ tệp triển khai.

### 3.1. Ba ngộ nhận thường gặp với copy/move

1. **Copy tệp khác với copy thư mục đệ quy.** `Files.copy(sourceDir, targetDir)` tạo thư mục đích rỗng, không tự sao chép toàn bộ con. Muốn copy cây thì kết hợp `walkFileTree` với xử lý tạo thư mục và copy từng file.
2. **`REPLACE_EXISTING` không biến copy thành một giao dịch atomic.** Nếu I/O thất bại giữa chừng, target có thể dở dang. Bảo đảm cần đến quy trình ghi file tạm + xác nhận + move thích hợp, và chỉ dựa vào `ATOMIC_MOVE` khi provider thực sự hỗ trợ.
3. **`Files.move` không phải lúc nào cũng là rename đơn giản.** Trong cùng file store có thể rename trực tiếp; qua file store khác có thể cần copy-xóa và có thể thất bại hoặc không nguyên tử. Không suy ra thao tác là atomic chỉ vì một lệnh Java trả về.

### 3.2. Symbolic link khi copy và move

Theo mặc định, `Files.copy(Path, Path, ...)` **đi theo symlink ở source** và copy nội dung *đối tượng mà link trỏ tới*. Thêm `LinkOption.NOFOLLOW_LINKS` để copy **chính đối tượng symbolic link** (nếu hệ thống hỗ trợ). Nếu target đã là symlink và dùng `REPLACE_EXISTING`, chính link ở target bị thay, không tự động đi theo link target.

`Files.move` di chuyển **chính symbolic link** nếu source là link. Không mặc nhiên giải tham chiếu link và di chuyển file mà link trỏ tới. Vì vậy dùng `NOFOLLOW_LINKS` cho thao tác copy có ý nghĩa khác hoàn toàn `FOLLOW_LINKS` khi duyệt cây.

## 4. LinkOption: NOFOLLOW_LINKS và ý nghĩa từng API

`LinkOption` chỉ có **`NOFOLLOW_LINKS`**. Nó implements *cả* `OpenOption` và `CopyOption`, nhưng khả năng chấp nhận option vẫn do từng API quyết định.

| Tác vụ | Mặc định | Khi truyền `LinkOption.NOFOLLOW_LINKS` |
| --- | --- | --- |
| `Files.exists(link)` / `notExists(link)` | Theo link đến target | Kiểm tra sự tồn tại của **link**; dangling symlink vẫn có thể là một link đang tồn tại. |
| `Files.isDirectory(link)` / `isRegularFile(link)` | Xét loại của target | Xét chính link; symlink không được coi là thư mục hoặc regular file do target thuộc loại đó. |
| `Files.readAttributes(link, BasicFileAttributes.class)` | Đọc thuộc tính target | Đọc thuộc tính chính symlink. |
| `Files.getLastModifiedTime(link)` | Thời điểm của target | Thời điểm của chính symlink khi provider hỗ trợ. |
| `Files.copy(link, target, NOFOLLOW_LINKS)` | Mặc định copy target mà link trỏ tới | Copy bản thân symlink, nếu hỗ trợ. |
| `Files.newByteChannel(link, ..., NOFOLLOW_LINKS)` | Có thể theo symlink khi mở | Chỉ mở khi đối tượng được mở đáp ứng quy tắc không theo link; có thể thất bại khi path là symlink. |

Phân biệt **symlink** với **hard link**: symlink là một entry trỏ tới path khác và có thể bị đứt; hard link là một tên khác của cùng file trong cùng file system. `NOFOLLOW_LINKS` nhắm vào symbolic link, không biến hard link thành một đối tượng đặc biệt để bỏ qua.

Ví dụ kiểm tra bản thân link:

```java
Path candidate = Path.of("alias");
boolean entryExists = Files.exists(candidate, LinkOption.NOFOLLOW_LINKS);
boolean isLink = Files.isSymbolicLink(candidate);
System.out.println("entry exists: " + entryExists);
System.out.println("symbolic link: " + isLink);
```

Kết quả hai dòng trên **phụ thuộc hệ tệp thực tế**, nên không gán stdout cố định. `Files.exists` và `Files.notExists` đều có thể trả `false` khi không xác định được trạng thái; chúng không phải hai phép phủ định logic hoàn hảo. Đừng dùng check-then-open để bảo đảm tuyệt đối chống thay thế link trong thao tác nhạy cảm; hãy cân nhắc API thao tác tương đối với directory handle (`SecureDirectoryStream` khi được hỗ trợ), quyền truy cập và xử lý ngoại lệ.

## 5. FileVisitOption: FOLLOW_LINKS khi duyệt cây

`FileVisitOption` chỉ có **`FOLLOW_LINKS`** trong Java 17/21.

| Gọi API | Không truyền `FOLLOW_LINKS` | Có `FOLLOW_LINKS` |
| --- | --- | --- |
| `Files.walkFileTree(start, visitor)` | Duyệt sâu nhưng **không đi vào thư mục mà symlink trỏ tới**. | Overload 4 tham số mới nhận tập option; có thể đi qua thư mục đích của symlink. |
| `Files.walkFileTree(start, options, maxDepth, visitor)` | `EnumSet.noneOf(FileVisitOption.class)`: không theo symlink. | `EnumSet.of(FileVisitOption.FOLLOW_LINKS)`: theo symlink đến mức sâu cho phép. |
| `Files.walk(start, maxDepth, options...)` | Không theo symlink, trả `Stream<Path>`. | Theo symlink, vẫn cần đóng stream sau khi dùng. |

Ví dụ cú pháp chuẩn:

```java
Files.walkFileTree(
    root,
    java.util.EnumSet.of(FileVisitOption.FOLLOW_LINKS),
    3,
    visitor
);
```

`root` và `visitor` là các biến đã được khai báo ở ngữ cảnh gọi; snippet này không phải chương trình độc lập. Tham số `3` là độ sâu tối đa: root ở độ sâu `0`, con trực tiếp ở `1`. `Integer.MAX_VALUE` thực tế có nghĩa không chủ động giới hạn độ sâu.

### 5.1. Callback của FileVisitor và kết quả điều khiển

Đây là kiến thức đi cùng `FileVisitOption`: option chọn cách duyệt, còn `FileVisitor` quyết định cách phản ứng với từng entry.

| Callback | Khi nào được gọi | Điểm cần nhớ |
| --- | --- | --- |
| `preVisitDirectory(dir, attrs)` | Trước khi duyệt con của một thư mục | Trả `SKIP_SUBTREE` để không xuống subtree. |
| `visitFile(file, attrs)` | Khi gặp file thường hoặc entry được xử lý như file | Hữu ích cho tìm kiếm hoặc copy từng file. |
| `visitFileFailed(file, exc)` | Khi không đọc được thuộc tính/mở thư mục hoặc phát hiện vòng lặp symlink | Có thể nhận `FileSystemLoopException` khi bật `FOLLOW_LINKS`. |
| `postVisitDirectory(dir, exc)` | Sau khi duyệt xong directory hoặc lỗi khi duyệt các entry | Phù hợp để xóa thư mục sau khi đã xử lý con; kiểm tra `exc`. |

| `FileVisitResult` | Ý nghĩa |
| --- | --- |
| `CONTINUE` | Tiếp tục duyệt. |
| `SKIP_SUBTREE` | Bỏ qua descendants của thư mục hiện tại (có ý nghĩa khi trả ở `preVisitDirectory`). |
| `SKIP_SIBLINGS` | Bỏ qua các entry anh em tiếp theo. |
| `TERMINATE` | Kết thúc duyệt ngay. |

Nếu return `null` từ visitor thì `walkFileTree` ném `NullPointerException`. Thứ tự các anh em trong directory **không được bảo đảm**; nếu cần thứ tự ổn định cho báo cáo, cần tự thiết kế cách sắp xếp.

### 5.2. Lý do FOLLOW_LINKS cần cẩn thận

Nếu trong cây có symlink trỏ ngược lên một thư mục tổ tiên, `FOLLOW_LINKS` có thể tạo vòng lặp. `walkFileTree` nhận diện chu trình và gọi `visitFileFailed` với `FileSystemLoopException`; đừng giả định nó sẽ đi vô hạn hoặc mọi symlink đều duyệt thành công. Với tác vụ xóa, sao lưu hoặc scan dữ liệu không đáng tin cậy, mặc định **không follow** thường giúp tránh đi ra ngoài cây mong muốn. Ngay cả với `maxDepth` hữu hạn, vẫn phải xử lý lỗi quyền truy cập và symlink thay đổi trong khi duyệt.

`Files.walk` trả về stream có thể giữ directory handles; luôn dùng `try-with-resources`:

```java
try (var paths = Files.walk(root, 2)) {
    long count = paths.filter(Files::isRegularFile).count();
    System.out.println("regular files: " + count);
}
```

Đây là snippet phụ thuộc thư mục `root`. Với `FOLLOW_LINKS`, lỗi I/O trong lúc stream đang tiêu thụ có thể bọc thành `UncheckedIOException`, khác với callback báo lỗi của `walkFileTree`. Lưu ý `Files.isRegularFile` mặc định theo symbolic link ở entry đang xét; nếu muốn chính symlink không được tính là file thường, truyền `LinkOption.NOFOLLOW_LINKS`.

## 6. Ví dụ Java 17+ chạy độc lập: mở, copy, move

Tạo tệp `OptionDemo.java`:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardCopyOption.*;

public class OptionDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("nio-option-demo-");
        Path source = dir.resolve("source.txt");
        Path copy = dir.resolve("copy.txt");
        Path moved = dir.resolve("moved.txt");

        try {
            Files.writeString(source, "alpha\n", StandardCharsets.UTF_8,
                    CREATE_NEW, WRITE);
            Files.writeString(source, "beta\n", StandardCharsets.UTF_8, APPEND);

            Files.copy(source, copy, COPY_ATTRIBUTES);
            Files.writeString(copy, "gamma\n", StandardCharsets.UTF_8,
                    WRITE, TRUNCATE_EXISTING);
            Files.move(copy, moved);

            System.out.println(
                    Files.readString(source, StandardCharsets.UTF_8)
                            .replace("\n", "|"));
            System.out.println(
                    Files.readString(moved, StandardCharsets.UTF_8).trim());
            System.out.println(Files.exists(copy));
        } finally {
            Files.deleteIfExists(moved);
            Files.deleteIfExists(copy);
            Files.deleteIfExists(source);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa `OptionDemo.java`:

```bash
javac -encoding UTF-8 OptionDemo.java
java OptionDemo
```

Kết quả chính xác:

```text
alpha|beta|
gamma
false
```

Giải thích: `CREATE_NEW` tạo source và bảo vệ khỏi mở nhầm tệp cũ; `APPEND` thêm `beta` sau `alpha`; `COPY_ATTRIBUTES` yêu cầu giữ thuộc tính khi copy; `TRUNCATE_EXISTING` thay nội dung bản copy bằng `gamma`; `move` đổi tên tệp copy thành moved, khiến đường dẫn `copy` không còn tồn tại. Ví dụ tự dọn các tệp/tên thư mục tạm khi kết thúc bình thường; nếu có can thiệp từ tiến trình khác hoặc lỗi dọn dẹp, `IOException` vẫn có thể xuất hiện.

## 7. Ví dụ Java 17+ chạy độc lập: walkFileTree không theo symlink

Tạo `WalkDemo.java`:

```java
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

public class WalkDemo {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("nio-walk-demo-");
        Path sub = Files.createDirectory(root.resolve("sub"));
        Path one = root.resolve("a.txt");
        Path two = sub.resolve("b.txt");

        try {
            Files.writeString(one, "a");
            Files.writeString(two, "b");

            AtomicInteger directories = new AtomicInteger();
            AtomicInteger files = new AtomicInteger();

            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class),
                    Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            directories.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs) {
                            files.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }
                    });

            System.out.printf("dirs=%d, files=%d%n",
                    directories.get(), files.get());
        } finally {
            Files.deleteIfExists(two);
            Files.deleteIfExists(one);
            Files.deleteIfExists(sub);
            Files.deleteIfExists(root);
        }
    }
}
```

```bash
javac -encoding UTF-8 WalkDemo.java
java WalkDemo
```

Kết quả chính xác trên cấu trúc mà chương trình tự tạo:

```text
dirs=2, files=2
```

Tệp gốc và một thư mục con tạo ra `2` lượt `preVisitDirectory`; hai tệp tạo ra `2` lượt `visitFile`. Chuyển `EnumSet.noneOf(FileVisitOption.class)` thành `EnumSet.of(FileVisitOption.FOLLOW_LINKS)` sẽ cho phép theo symlink, nhưng ví dụ không tạo symlink nên số đếm không thay đổi.

## 8. Chọn option theo tình huống

| Yêu cầu thực tế | Lựa chọn gợi ý | Vì sao |
| --- | --- | --- |
| Đọc tệp hiện có | `Files.readString(path)` hoặc mở `READ` | Không tạo/ghi file. |
| Ghi đè bằng nội dung hoàn toàn mới | `Files.writeString(path, text)` hoặc `WRITE + CREATE + TRUNCATE_EXISTING` | Cắt nội dung cũ trước khi ghi. |
| Ghi nối log, tự tạo nếu cần | `CREATE + APPEND` | Không cắt dữ liệu đã có, nhưng không bảo đảm bản ghi atomic liên tiến trình. |
| Chỉ tạo khi chưa có | `CREATE_NEW + WRITE` | Tránh cuộc đua “check rồi create”. |
| Sao lưu một tệp, ghi đè bản sao | `Files.copy(a, b, REPLACE_EXISTING, COPY_ATTRIBUTES)` | Sao chép nội dung cùng các thuộc tính được hỗ trợ. |
| Copy chính symbolic link | `Files.copy(link, dst, LinkOption.NOFOLLOW_LINKS)` | Không dereference link ở source. |
| Kiểm tra một entry là symlink | `Files.isSymbolicLink(path)` | Xem loại entry của path, không phụ thuộc target tồn tại. |
| Xem thuộc tính **chính link** | `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)` | Tránh đọc nhầm metadata của target. |
| Rename/move yêu cầu atomic | `Files.move(src, dst, ATOMIC_MOVE)`, bắt `AtomicMoveNotSupportedException` | Không giả định provider hỗ trợ. |
| Duyệt directory mặc định | `Files.walkFileTree(root, visitor)` | Không follow symlink. |
| Duyệt cả thư mục qua symlink | `Files.walkFileTree(root, EnumSet.of(FOLLOW_LINKS), maxDepth, visitor)` | Có thể đi ngoài cây logic và phải xử lý vòng lặp. |

### Các cặp dễ nhầm

- `NOFOLLOW_LINKS` và `FOLLOW_LINKS` **không phải hai hằng số đối xứng trong một enum**: cái đầu thuộc `LinkOption` cho API làm việc với link/thuộc tính/copy; cái sau thuộc `FileVisitOption` cho duyệt cây. Không truyền `FOLLOW_LINKS` cho `Files.copy`.
- `REPLACE_EXISTING` là thao tác **với đường dẫn đích khi copy/move**; `TRUNCATE_EXISTING` là thao tác **với nội dung của file đang mở để ghi**. Không thể hoán đổi.
- `CREATE`/**`CREATE_NEW`** dùng khi **mở để ghi/tạo file**; `COPY_ATTRIBUTES` dùng khi **copy file**; `ATOMIC_MOVE` chỉ dùng khi **move**.
- `FileVisitOption` chỉ thay cách duyệt cây. Nó không tự quyết định sẽ copy/xóa file nào, không thay cho `FileVisitor`, và không thay đổi cách hoạt động của `Files.exists`.

## 9. Lỗi phổ biến và cách xử lý

| Hiện tượng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| `FileAlreadyExistsException` | `CREATE_NEW` tạo file đã có hoặc `Files.copy` gặp target đã tồn tại | Đổi tên, kiểm tra yêu cầu nghiệp vụ, hoặc sử dụng `REPLACE_EXISTING` chỉ khi thực sự muốn ghi đè. |
| `NoSuchFileException` | Source không tồn tại, parent directory chưa có, hoặc dùng `APPEND` mà file chưa có | Chuẩn bị parent; với mục tiêu tạo nếu thiếu, cân nhắc `CREATE + APPEND`. |
| `IllegalArgumentException` khi mở channel | Option không hợp lệ, ví dụ `READ + APPEND` hoặc `APPEND + TRUNCATE_EXISTING` | Chọn tổ hợp hợp lệ theo API và hành vi mong muốn. |
| `UnsupportedOperationException` | Provider không hỗ trợ option cụ thể | Chọn cách hỗ trợ bởi provider, có nhánh xử lý phù hợp. |
| `AtomicMoveNotSupportedException` | `ATOMIC_MOVE` không được hệ tệp/provider hỗ trợ | Quyết định rõ có cho phép fallback không nguyên tử hay báo lỗi. |
| `FileSystemLoopException` khi walk | Bật `FOLLOW_LINKS` và gặp chu trình symlink | Xử lý trong `visitFileFailed`; cân nhắc không follow link hoặc giới hạn độ sâu. |
| Dữ liệu cũ biến mất | Mặc định `writeString`/`newOutputStream` cắt file cũ | Chỉ dùng `APPEND` khi muốn nối; dùng `TRUNCATE_EXISTING` một cách có chủ ý. |
| `Files.exists` là `false` dù nghi ngờ path tồn tại | Dangling symlink, quyền truy cập hoặc trạng thái không xác định | Dùng `NOFOLLOW_LINKS` nếu cần xét link, và vẫn xử lý lỗi tại thao tác thực tế. |

Khi thao tác I/O thật, ngoài option còn phải tính tới quyền hệ điều hành, file system provider, symbolic link có thể thay đổi đồng thời, độ bền ghi xuống thiết bị, đóng tài nguyên và cách khôi phục sau lỗi. Tránh dùng option như một thay thế cho transaction, locking hoặc kiểm tra bảo mật.

## 10. Tự kiểm tra

1. Vì sao `OpenOption.READ` không biên dịch? **Vì `OpenOption` là interface; dùng `StandardOpenOption.READ`.**
2. Muốn vừa tạo log khi chưa có, vừa nối khi đã có? **Dùng `CREATE + APPEND`.**
3. `Files.copy(dirA, dirB)` có sao chép mọi file con không? **Không; cần duyệt cây và copy từng entry.**
4. Muốn copy chính symbolic link? **Dùng `Files.copy(link, target, LinkOption.NOFOLLOW_LINKS)` nếu provider hỗ trợ.**
5. Muốn walk qua link đến thư mục? **Dùng `FileVisitOption.FOLLOW_LINKS` trong overload cho phép option.**
6. Có được chắc chắn một lệnh `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` sẽ luôn ghi đè đích? **Không; với `ATOMIC_MOVE`, option khác bị bỏ qua và chính sách target tồn tại phụ thuộc implementation.**
7. `SYNC` có đồng nghĩa `ATOMIC_MOVE`? **Không; `SYNC` liên quan đồng bộ cập nhật file xuống storage, `ATOMIC_MOVE` liên quan tính nguyên tử khi đổi vị trí file.**

## Tài liệu đối chiếu (JDK 21)

- [OpenOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/OpenOption.html)
- [StandardOpenOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [CopyOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/CopyOption.html)
- [StandardCopyOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html)
- [LinkOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/LinkOption.html)
- [FileVisitOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitOption.html)
- [Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-005">Bảng trách nhiệm đóng tài nguyên theo từng API</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng trách nhiệm đóng tài nguyên theo từng API trong Java I/O

## 1. Cách đọc bảng: ai mở, ai đóng?

Trong Java, **quyền sở hữu tài nguyên (resource ownership)** quyết định thành phần nào chịu trách nhiệm gọi **close()**. Một đối tượng có phương thức close() chưa đủ để kết luận rằng đoạn mã đang sử dụng nó được phép đóng: đối tượng có thể được tạo tại đây, được mượn từ bên ngoài hoặc được chuyển giao quyền sở hữu theo hợp đồng API.

| Tình huống | Ai phải đóng? | Nguyên tắc |
|---|---|---|
| Phương thức tự mở file, socket, channel, directory stream… | Phương thức đã mở | Dùng try-with-resources để đóng trên mọi đường đi, kể cả khi có ngoại lệ. |
| Phương thức **nhận** một InputStream, OutputStream, Reader, Writer… do caller truyền vào | Thông thường caller | Callee dùng rồi trả lại; chỉ đóng khi API công khai rằng nó nhận quyền sở hữu. |
| Phương thức trả về một đối tượng còn giữ tài nguyên đang mở | Caller nhận kết quả | Viết rõ trong hợp đồng rằng caller phải đóng; tránh trả về tài nguyên ẩn, khó theo dõi. |
| Đặt wrapper quanh stream/channel | Bên sở hữu wrapper **và** tài nguyên nền theo hợp đồng | Đóng wrapper thường đóng luôn tài nguyên nền. Nếu nền là tài nguyên mượn, không tự ý đóng wrapper. |
| API đọc/ghi trọn vẹn trong một lời gọi, chỉ trả về dữ liệu/Path/kết quả | Chính API | Không còn đối tượng tài nguyên để caller đóng; việc xóa file hoặc dọn dữ liệu là trách nhiệm khác. |

**Quy tắc mặc định để tra cứu nhanh:** nếu mã của bạn tự tạo tài nguyên có vòng đời mở/đóng, hãy đưa nó vào try-with-resources; nếu chỉ được truyền vào một tài nguyên có sẵn, **không đóng** khi chưa có hợp đồng chuyển giao. Các ngoại lệ cụ thể cần đọc theo từng API ở dưới.

## 2. Bảng tra cứu java.io: stream, reader, writer và wrapper

| API hoặc cách tạo | Chủ sở hữu thông thường | Cần làm khi xong? | Tác động và lưu ý |
|---|---|---|---|
| **new FileInputStream(file)**, **new FileOutputStream(file)** | Mã gọi constructor | **close()** | Giải phóng file descriptor; OutputStream.close() đóng luồng ghi, không thay thế cơ chế bảo đảm dữ liệu đã bền vững trên đĩa. |
| **new FileReader(file, charset)**, **new FileWriter(file, charset)** | Mã mở file | **close()** | Đóng reader/writer và tài nguyên file; chỉ rõ charset nếu nội dung cần tái lập ổn định. |
| **new BufferedInputStream(input)**, **new BufferedOutputStream(output)** | Mã sở hữu wrapper theo hợp đồng | **close() wrapper** | Đóng wrapper sẽ đóng stream nền; với luồng ghi, close() còn flush dữ liệu được đệm. |
| **new BufferedReader(reader)**, **new BufferedWriter(writer)** | Mã sở hữu wrapper | **close() wrapper** | Đóng wrapper kéo theo đóng reader/writer bên dưới; với writer, cần đóng hoặc flush để đẩy dữ liệu đệm. |
| **new InputStreamReader(input, charset)**, **new OutputStreamWriter(output, charset)** | Mã sở hữu wrapper | **close() wrapper** | Đóng tầng chuyển đổi byte ↔ ký tự và đóng stream nền. |
| **new DataInputStream(input)**, **new DataOutputStream(output)** | Mã sở hữu wrapper | **close() wrapper** | Đóng stream nền; close() của DataOutputStream cũng kết thúc đường ghi. |
| **new ObjectInputStream(input)**, **new ObjectOutputStream(output)** | Mã sở hữu wrapper | **close() wrapper** | Đóng stream nền; phải hoàn tất/flush chuỗi đối tượng ghi ra trước khi kết thúc. Không dùng ObjectOutputStream cho dữ liệu Java Serialization không đáng tin. |
| **new PrintStream(output)**, **new PrintWriter(writer/output)** | Mã sở hữu wrapper | **close() nếu sở hữu đầu ra** | Đóng wrapper sẽ đóng đích ghi. Hai lớp này có thể ghi nhận lỗi thay vì ném IOException trong một số thao tác; kiểm tra **checkError()** khi cần phát hiện lỗi ghi. |
| **new RandomAccessFile(file, mode)** | Mã mở file | **close()** | Giải phóng file; **getChannel()** trả về channel liên kết cùng file, đóng một phía có thể ảnh hưởng phía còn lại. |
| **new ByteArrayInputStream(bytes)**, **new ByteArrayOutputStream()** | Mã tạo đối tượng | Không có handle hệ điều hành bắt buộc đóng | close() không có tác dụng đối với hai lớp này; cần phân biệt với các wrapper có tài nguyên nền thật. |
| **new StringReader(text)**, **new StringWriter()** | Mã tạo đối tượng | Không có file/socket cần giải phóng | StringReader.close() đánh dấu đóng, đọc tiếp không hợp lệ; StringWriter.close() không có tác dụng. Không suy từ hai lớp này ra toàn bộ Reader/Writer. |

Ví dụ, đóng BufferedReader được tạo từ FileReader sẽ lan xuống FileReader; không cần đóng từng lớp riêng theo cách thủ công. Ngược lại, một BufferedWriter bọc **System.out** không được tự ý đóng, vì hành động đó sẽ đóng luôn stream chuẩn đang được các thành phần khác sử dụng.

## 3. Bảng tra cứu java.nio.file.Files

Phân biệt **phương thức trả về tài nguyên mở** với **phương thức tự thực hiện toàn bộ I/O rồi trả dữ liệu**.

| API | Kết quả | Trách nhiệm đóng |
|---|---|---|
| **Files.newInputStream(path)**, **Files.newOutputStream(path)** | InputStream/OutputStream đang mở | **Caller đóng** stream trả về. |
| **Files.newBufferedReader(path, charset)**, **Files.newBufferedWriter(path, charset, options…)** | BufferedReader/BufferedWriter đang mở | **Caller đóng** reader/writer trả về. |
| **Files.newByteChannel(path, options…)** | SeekableByteChannel đang mở | **Caller đóng** channel. |
| **Files.newDirectoryStream(dir)**, biến thể có glob/filter | DirectoryStream đang mở | **Caller đóng** sau khi duyệt; kể cả khi ngừng duyệt sớm hoặc xảy ra lỗi. |
| **Files.lines(path, charset)** | Stream&lt;String&gt; gắn với file đang mở | **Caller đóng Stream** bằng try-with-resources. count(), forEach(), findFirst()… **không tự đóng** nguồn. |
| **Files.list(dir)** | Stream&lt;Path&gt; có thể giữ tài nguyên thư mục | **Caller đóng Stream**. |
| **Files.walk(start, …)**, **Files.find(start, …)** | Stream&lt;Path&gt; duyệt cây thư mục | **Caller đóng Stream**; nguồn có thể giữ các directory handle trong lúc duyệt. |
| **Files.readAllBytes(path)**, **Files.readAllLines(path, charset)**, **Files.readString(path, charset)** | byte[], List&lt;String&gt;, String | API tự quản lý I/O bên trong; **caller không có stream phải đóng**. Cân nhắc bộ nhớ nếu file lớn. |
| **Files.write(path, bytes/lines, …)**, **Files.writeString(path, text, charset, …)** | Path | API tự mở/đóng tài nguyên nội bộ; **caller không phải đóng Path**. |
| **Files.copy(sourcePath, targetPath, …)**, **Files.move(source, target, …)** | Path | API tự quản lý handle nội bộ; không có stream trả về để đóng. |
| **Files.copy(inputStream, targetPath, …)** | long (số byte) | API **không đóng inputStream** do caller truyền vào; caller giữ trách nhiệm. |
| **Files.copy(sourcePath, outputStream)** | long (số byte) | API **không đóng outputStream** do caller truyền vào; caller giữ trách nhiệm. |
| **Files.walkFileTree(start, visitor)** | Path | API quản lý việc mở/đóng directory trong quá trình duyệt; nhưng mọi tài nguyên mà visitor tự mở vẫn phải được visitor đóng. |
| **Files.exists/isRegularFile/size/delete/deleteIfExists/createTempFile** | Giá trị/Path, không trả stream | Không có handle để caller đóng; **file tạm vẫn phải được xóa nếu ứng dụng chịu trách nhiệm dọn dẹp**. |

**Lỗi dễ mắc:** viết Files.lines(path).filter(...).count() mà không đặt Stream trả về vào try-with-resources. Một terminal operation chỉ kết thúc phép tính Stream; nó không đồng nghĩa với close(). Đối với Stream lấy từ collection thuần bộ nhớ như list.stream(), không có tài nguyên ngoài cần giải phóng trong trường hợp thông thường.

## 4. Bảng tra cứu channel, filesystem và các đối tượng NIO khác

| API | Ai đóng? | Ghi chú quan trọng |
|---|---|---|
| **FileChannel.open(path, options…)** | Caller đóng FileChannel | Đóng channel giải phóng file descriptor. |
| **AsynchronousFileChannel.open(path, options…)** | Caller đóng AsynchronousFileChannel | Đóng channel sau khi xử lý/đồng bộ các tác vụ bất đồng bộ theo thiết kế; tránh sử dụng sau khi đóng. |
| **Files.newByteChannel(path)** | Caller đóng SeekableByteChannel | Khi cắt qua lớp abstraction, quyền sở hữu vẫn đi theo đối tượng trả về. |
| **Channels.newInputStream(channel)** / **Channels.newOutputStream(channel)** | Thành phần sở hữu wrapper | Đóng stream chuyển đổi sẽ đóng channel nền; không làm vậy nếu channel được mượn. |
| **FileLock** từ FileChannel.lock()/tryLock() | Bên nhận lock giải phóng | Dùng release() hoặc AutoCloseable.close(); đóng channel cũng giải phóng lock liên quan, nhưng không nên dùng thay cho vòng đời lock rõ ràng. |
| **FileChannel.map(…) → MappedByteBuffer** | Không có phương thức close() công khai trên buffer | Đóng FileChannel **không bảo đảm** vùng nhớ ánh xạ được unmap ngay; việc unmap thường phụ thuộc vòng đời buffer/GC. Không giả định delete/truncate thành công tức thời trên mọi hệ điều hành. |
| **FileSystems.newFileSystem(…)** (ví dụ ZIP filesystem) | Mã tạo FileSystem | Đóng FileSystem khi dùng xong để giải phóng các tài nguyên của provider. |
| **FileSystems.getDefault()** | Runtime JVM | **Không đóng** default filesystem. |
| **FileSystems.getFileSystem(uri)** | Tùy hợp đồng nguồn gốc | Đây là filesystem đang tồn tại; không đóng nếu đang mượn hoặc dùng chung. |
| **FileSystems.getDefault().newWatchService()** | Mã tạo WatchService | Đóng WatchService để giải phóng hệ thống theo dõi thư mục. |
| **Path**, **Files.readAttributes(…)**, **BasicFileAttributes** | Không áp dụng | Path và thông tin thuộc tính không phải handle đang mở; không có close() để gọi. |

Channel và các stream được tạo trên cùng một nguồn có thể **chia sẻ vòng đời**. Không mở đồng thời nhiều wrapper rồi giả định có thể đóng một wrapper mà những wrapper còn lại vẫn hoạt động.

## 5. Bảng tra cứu API I/O thường dùng ngoài java.io/nio

| API | Trách nhiệm | Góc cạnh cần tránh |
|---|---|---|
| **new Scanner(path, charset)**, **new Scanner(inputStream/reader)** | Đóng Scanner nếu nó sở hữu đầu vào; Scanner.close() sẽ đóng nguồn nếu nguồn là Closeable | Scanner(System.in).close() cũng sẽ đóng **System.in**; thông thường không đóng đầu vào chuẩn dùng chung. |
| **new ZipInputStream(input)**, **new GZIPInputStream(input)** | Chủ sở hữu wrapper đóng wrapper | Việc đóng lan xuống input bên dưới; mỗi entry trong ZipInputStream kết thúc bằng closeEntry() để chuyển entry, nhưng **closeEntry() không thay thế close() toàn stream**. |
| **new ZipOutputStream(output)**, **new GZIPOutputStream(output)** | Chủ sở hữu wrapper đóng wrapper | Có thể dùng finish() để hoàn tất định dạng nén mà vẫn giữ stream nền mở theo thiết kế API; **close()** của wrapper còn đóng output nền. |
| **new ZipFile(path)**, **new JarFile(path)** | Mã mở file archive | Đóng ZipFile/JarFile; InputStream lấy qua getInputStream(entry) cũng nên được đóng trong phạm vi sử dụng, trước khi đóng archive. |
| **Socket**, **ServerSocket** do ứng dụng tạo/accept | Mã nhận quyền sở hữu socket đóng socket | Đóng socket hoặc stream của socket thường kéo theo đóng kết nối; không đóng một stream của socket khi cần tái sử dụng kết nối. |
| **URL.openStream()** | Mã gọi | Đóng InputStream nhận được; việc quản lý kết nối HTTP chuyên biệt cần theo hợp đồng thư viện đang dùng. |
| **Process.getInputStream/getErrorStream/getOutputStream** | Mã quản lý Process và các stream của nó | Đóng stream khi không sử dụng, tiêu thụ stdout/stderr tránh nghẽn và quản lý vòng đời Process riêng; chỉ close stream không đồng nghĩa tiến trình kết thúc. |

Đối với tài nguyên mượn từ framework, container, thư viện hoặc callback, **hợp đồng API cụ thể có thể thay đổi quyền sở hữu mặc định**. Ví dụ framework có thể gọi callback với một OutputStream mà framework sẽ tự đóng sau khi callback trả về.

## 6. Mẫu đúng/sai theo tình huống

### 6.1. Tự mở: tự đóng

~~~java
try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String firstLine = reader.readLine();
    // Sử dụng firstLine tại đây.
} // reader.close() chạy cả khi readLine() ném IOException.
~~~

Với FileInputStream bọc BufferedInputStream, đối tượng cần quản lý ở lớp ứng dụng thường là **wrapper ngoài cùng**: đóng wrapper sẽ đóng stream gốc. Nếu việc khởi tạo các lớp phức tạp có thể thất bại giữa chừng, thiết kế từng bước acquisition trong các scope try-with-resources phù hợp để nguồn đã mở không bị rò rỉ.

### 6.2. Mượn OutputStream: ghi nhưng không đóng

~~~java
static void copyToCallerOwnedDestination(Path source, OutputStream destination)
        throws IOException {
    Files.copy(source, destination);
    destination.flush(); // Chỉ khi hợp đồng cần dữ liệu được đẩy ra lúc này.
    // Không đóng destination: caller sở hữu nó.
}
~~~

Ví dụ này thể hiện **hợp đồng caller-owned**; cần ghi rõ điều đó trong JavaDoc của phương thức thực tế. flush() không phải close(), và không bảo đảm dữ liệu đã được đồng bộ bền vững lên thiết bị lưu trữ. Bọc destination trong try-with-resources với BufferedOutputStream rồi đóng wrapper tại đây sẽ vô tình đóng cả destination do caller đang sở hữu.

### 6.3. Stream trả về: người nhận phải đóng

~~~java
static long countLines(Path file) throws IOException {
    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
        return lines.count();
    }
}
~~~

Sai về vòng đời tài nguyên:

~~~java
long count = Files.lines(file).count(); // Stream chứa tài nguyên file không được đóng.
~~~

Khi thiết kế API trả về Stream gắn file, nên tài liệu hóa trách nhiệm close của caller. Nếu dữ liệu nhỏ và không cần lazy evaluation, cân nhắc trả về List đã đọc xong trong một scope try-with-resources để đơn giản hóa ownership.

### 6.4. Hệ thống chuẩn và tài nguyên dùng chung

- **System.in, System.out, System.err** thường là tài nguyên chung của ứng dụng/JVM; tránh đóng trong một helper hoặc bài thực hành. Chỉ flush đầu ra chuẩn khi cần.
- **Wrapper đóng lan xuống nguồn:** Scanner(System.in), BufferedWriter(new OutputStreamWriter(System.out, …)) và PrintWriter(System.out) đều có thể đóng nguồn chuẩn khi wrapper bị close().
- **Tài nguyên chia sẻ hoặc pooled:** không đóng nguồn được mượn nếu thư viện/owner chưa chuyển quyền sở hữu. Một adapter không đóng nguồn phải được thiết kế rõ ràng; việc chỉ bỏ qua close() trên một BufferedWriter có thể làm dữ liệu đệm chưa được xuất ra.

## 7. Chương trình Java 17+ có thể chạy

Đây là một ví dụ độc lập chứng minh khác biệt giữa API tự quản lý tài nguyên (**readAllLines**, **writeString**), API trả về handle (**newBufferedReader**, **newDirectoryStream**) và Stream cần đóng (**Files.lines**). File tạm được xóa ở finally; thao tác xóa độc lập với close().

**Tệp: ResourceCloseTableDemo.java**

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ResourceCloseTableDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("close-table-", ".txt");

        try {
            Files.writeString(file, "alpha\nbeta\n", StandardCharsets.UTF_8);

            // Files tự mở/đóng nguồn; kết quả List không cần close().
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            System.out.println("readAllLines=" + all.size());

            // Caller sở hữu BufferedReader, nên dùng try-with-resources.
            try (BufferedReader reader =
                    Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                System.out.println("first=" + reader.readLine());
            }

            // count() không đóng Stream: try-with-resources mới đóng.
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                long count = lines.filter(s -> s.startsWith("b")).count();
                System.out.println("b-lines=" + count);
            }

            // DirectoryStream có handle cần đóng sau khi duyệt.
            try (DirectoryStream<Path> entries =
                    Files.newDirectoryStream(
                            file.getParent(), file.getFileName().toString())) {
                int matches = 0;
                for (Path entry : entries) {
                    if (entry.equals(file)) {
                        matches++;
                    }
                }
                System.out.println("matching-files=" + matches);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac ResourceCloseTableDemo.java
java ResourceCloseTableDemo
~~~

**stdout dự kiến:**

~~~text
readAllLines=2
first=alpha
b-lines=1
matching-files=1
~~~

Giải thích: Files.writeString() và Files.readAllLines() đóng các tài nguyên nội bộ của chúng; Files.newBufferedReader(), Files.lines() và Files.newDirectoryStream() trả về đối tượng cần caller đóng. try-with-resources đóng tài nguyên trước khi finally xóa file tạm, tránh xóa khi handle vẫn đang mở.

## 8. Ngoại lệ, close() và thứ tự giải phóng

~~~java
try (InputStream input = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(input)) {
    // Đọc dữ liệu từ buffered.
}
~~~

Đây là ví dụ thể hiện **thứ tự đóng ngược với thứ tự khai báo**: buffered.close() trước, input.close() sau. Vì đóng BufferedInputStream vốn đóng InputStream bên dưới, nguồn có thể bị đóng lần nữa trong ví dụ này; mẫu này hữu ích khi cần bảo đảm InputStream đã mở vẫn được đóng nếu việc tạo wrapper thất bại. Khi chọn thiết kế, kiểm tra tính an toàn của thao tác close nhiều lần ở API cụ thể; nếu không có nhu cầu xử lý lỗi khởi tạo wrapper, thường chỉ cần quản lý wrapper ngoài cùng trong try-with-resources.

Nếu phần thân try ném ngoại lệ và close() cũng ném, try-with-resources giữ ngoại lệ của thân làm **ngoại lệ chính** và ghi ngoại lệ lúc đóng dưới dạng **suppressed exception** (xem Throwable.getSuppressed()). Khi nhiều tài nguyên đóng, Java thử đóng theo thứ tự ngược dù một lần close có lỗi. Tránh tự viết finally khiến lỗi close che mất lỗi gốc hoặc bỏ qua lỗi mà không có chủ đích.

**Không phụ thuộc vào GC hay finalize() để đóng file/socket/channel.** Thời điểm thu gom không xác định. Tương tự, deleteIfExists() dọn tên file trong hệ thống file; nó không thay thế việc đóng stream/channel đang giữ file.

## 9. Checklist tra cứu trước khi gọi close()

1. **Nguồn gốc:** Tài nguyên được tạo tại đây, được caller truyền vào, hay do framework/connection pool cấp?
2. **Giá trị trả về:** API trả về dữ liệu thuần (byte[], String, List, Path) hay trả về đối tượng đang mở (Stream, Reader, Writer, Channel, DirectoryStream, Socket…)?
3. **Wrapper:** close() có lan xuống stream/channel bên dưới không? Nếu có, mình thực sự được quyền đóng nguồn đó chứ?
4. **Tính lazy:** Stream từ Files.lines/list/walk/find có được đóng trong try-with-resources, kể cả khi duyệt sớm dừng?
5. **Lỗi và hoàn tất ghi:** Tài nguyên có được đóng khi có ngoại lệ? Dữ liệu buffered/ZIP/object output có được hoàn tất theo hợp đồng API? Có cần checkError() cho PrintStream/PrintWriter?
6. **Phạm vi chuyển giao:** Nếu trả tài nguyên đang mở cho caller, trách nhiệm close đã được ghi trong hợp đồng? Nếu nhận tài nguyên mượn, phương thức có giữ nguyên trạng thái đóng/mở của nó không?
7. **Dọn dẹp khác close:** Việc xóa temp file, release lock, quản lý process hoặc đồng bộ bền vững dữ liệu có được xử lý riêng khi cần không?

Khi cần xác minh một API chưa có trong bảng, tìm kiểu trả về, phần mô tả **close/ownership** trong JavaDoc và hành vi đóng nguồn nền của wrapper. Nguyên tắc quyết định là **hợp đồng vòng đời**, không phải chỉ dựa vào tên phương thức hoặc việc đối tượng có implements AutoCloseable hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-006">Bảng lỗi I/O thường gặp và thông tin cần kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng lỗi I/O thường gặp và thông tin cần kiểm tra trong Java

## Mục đích và cách sử dụng

Tài liệu này là bảng **tra cứu nhanh nhưng có giải thích** cho lỗi Java Core I/O: đọc/ghi tệp bằng java.io và java.nio.file, xử lý byte/character stream, charset, channel, socket, nén ZIP và Java object serialization. Khi thấy lỗi, hãy xác định thao tác vừa thực hiện, tìm tên exception hoặc thông điệp gần nhất trong bảng, kiểm tra bằng chứng tối thiểu và chọn cách sửa theo nguyên nhân đã xác nhận.

**Nguyên tắc quan trọng:** tên exception cho biết nhóm vấn đề, chưa chứng minh nguyên nhân. Cùng một thao tác đọc tệp có thể phát sinh IOException, một lớp con cụ thể hơn hoặc RuntimeException tùy API, hệ điều hành, file-system provider, quyền truy cập và thời điểm lỗi xuất hiện. Thông điệp như "Permission denied", "The process cannot access the file" và "Too many open files" thường do môi trường cung cấp; không được giả định chúng luôn ứng với cùng một lớp exception trên mọi hệ thống.

### Cần thu thập thông tin gì trước tiên?

Với một sự cố, ghi lại có kiểm soát:

1. **Thao tác và API:** Files.readString, Files.newInputStream, Files.move, FileInputStream, BufferedReader, Socket.connect, ZipInputStream, ObjectInputStream.readObject…; đọc hay ghi; chạy đồng bộ hay bất đồng bộ.
2. **Exception đầy đủ:** loại ngoại lệ cụ thể, message, nguyên nhân lồng nhau (cause), các suppressed exceptions nếu có, và stack trace tại điểm ném lỗi đầu tiên liên quan ứng dụng.
3. **Đối tượng đích:** đường dẫn tuyệt đối đã chuẩn hóa về mặt cú pháp, loại path (file/thư mục/link), endpoint host:port hoặc định dạng dữ liệu. Che các thông tin nhạy cảm trong log; không ghi token, mật khẩu, nội dung tệp riêng tư.
4. **Môi trường:** OS, JDK, working directory, container/mount, encoding/charset, trạng thái ổ đĩa, quota, file descriptor/handle, quyền của **tài khoản đang chạy**.
5. **Thời điểm và phạm vi:** xảy ra ở bước mở, đọc, ghi, flush hay close; một lần hay lặp lại; một file hay mọi file; trước/sau khi deploy; có tiến trình đồng thời sửa/xóa/đổi tên dữ liệu không.
6. **Kết quả cần làm rõ:** lỗi có khả năng khắc phục bằng thay đường dẫn/quyền/schema không, có cần retry không, retry có an toàn hoặc khiến ghi trùng không, và dữ liệu đã được ghi một phần hay chưa.

Không ép toàn bộ lỗi vào một khối catch(IOException) chỉ để in "I/O failed": điều đó làm mất ngữ cảnh. Giữ nguyên exception gốc, thêm tên thao tác và đường dẫn/endpoint đã che thông tin nhạy cảm.

## 1. Bảng tra cứu lỗi hệ thống tệp: java.nio.file và java.io

Đây là nhóm gặp thường xuyên khi dùng Files, Path, InputStream, OutputStream, FileChannel và thao tác với thư mục. Những lớp lỗi cụ thể của NIO phần lớn kế thừa IOException (đặc biệt FileSystemException), nhưng không phải mọi lỗi đều là checked exception.

| Lỗi/triệu chứng | Ý nghĩa và nguyên nhân thường gặp | Thông tin cần kiểm tra | Hướng xử lý |
| --- | --- | --- | --- |
| NoSuchFileException | Tệp hoặc thành phần trong đường dẫn không tồn tại tại lúc thao tác; có thể đã bị xóa/di chuyển sau kiểm tra trước đó | Path hiệu lực, working directory, tên/ký tự hoa-thường, đường dẫn cha, mount, thao tác của tiến trình khác | Xác nhận đúng file và lifecycle; tạo file nếu hợp đồng cho phép; xử lý vắng mặt như trạng thái nghiệp vụ khi phù hợp |
| FileNotFoundException | API java.io cũ không mở được tệp; có thể do tệp thiếu, quyền bị từ chối hoặc đường dẫn chỉ tới thư mục | API gọi, getMessage(), sự tồn tại và loại file, quyền mở, đường dẫn tương đối/tuyệt đối | Kiểm tra nguyên nhân hệ điều hành; đừng suy luận chắc chắn là file không tồn tại từ tên exception |
| AccessDeniedException | Quyền truy cập tệp/thư mục bị từ chối; thường từ NIO provider | Tài khoản thực thi, ACL/quyền file và thư mục cha, mount read-only, trạng thái chính sách bảo mật | Cấp quyền cần thiết hoặc đổi vị trí lưu; không chạy ứng dụng bằng quyền quản trị chỉ để che lỗi |
| SecurityException | Chính sách quyền của runtime hoặc môi trường từ chối hành động; là unchecked exception | Stack trace, security policy/runtime, sandbox, ứng dụng có quyền gọi API không | Điều chỉnh cấu hình/phạm vi cho phép theo chính sách; phân biệt với AccessDeniedException |
| FileAlreadyExistsException | Cố tạo đối tượng đã tồn tại, thường với CREATE_NEW hoặc Files.createFile/createDirectory | Đường dẫn và loại mục hiện tại, tên có bị tiến trình khác chiếm không, cần tính duy nhất không | Đặt tên duy nhất hoặc xử lý conflict rõ ràng; CREATE_NEW cung cấp kiểm tra/tạo mang tính nguyên tử tại provider hỗ trợ |
| DirectoryNotEmptyException | Thao tác xóa/di chuyển thư mục khi nội dung bên trong khiến operation không thể hoàn tất | Các mục bên trong (kể cả hidden), chương trình nào đang sử dụng, policy recursive-delete | Chỉ xóa đệ quy khi đó là yêu cầu nghiệp vụ đã xác nhận; tránh xóa nhầm dữ liệu |
| NotDirectoryException | Một thành phần được kỳ vọng là thư mục nhưng thực tế không phải thư mục | Từng phần của path, thuộc tính đường dẫn cha, symbolic link | Sửa đường dẫn hoặc cấu trúc thư mục; không tiếp tục bằng cách nối path tùy tiện |
| FileSystemLoopException | Duyệt link gây vòng khi follow symbolic links, ví dụ qua Files.walkFileTree | Cấu trúc liên kết tượng trưng, chế độ FOLLOW_LINKS, đường dẫn tạo vòng | Không follow link nếu không cần; phát hiện và bỏ qua vòng hoặc báo lỗi rõ ràng |
| FileSystemException | Lỗi file-system không có lớp con chuyên biệt, ví dụ không thể rename/move, file đang bị giữ, thiết bị lỗi | getFile(), getOtherFile(), getReason(), OS/provider và quyền, trạng thái ổ đĩa/lock | Dùng reason/cause để chẩn đoán; không coi FileSystemException là một nguyên nhân cụ thể |
| IOException: "No space left on device" / "Disk full" | Vùng lưu trữ không còn dung lượng hoặc vượt quota; lớp exception có thể thay đổi | Dung lượng, inode/quota (nếu có), vị trí file thực tế, temp directory, log/backup | Giải phóng/nâng quota, dự phòng dung lượng, kiểm tra ghi dở và giao dịch tương ứng |
| IOException: "Too many open files" | Cạn file descriptors/handles hoặc giới hạn OS; hay do không đóng stream/channel/socket | Số file mở của tiến trình, giới hạn hệ thống, xu hướng tăng, nhánh không close | Dùng try-with-resources, sửa leak, cân nhắc giới hạn concurrent I/O; chỉ tăng OS limit khi có lý do |
| IOException: "Read-only file system" | Môi trường không cho ghi trên mount hoặc file system hiện tại | Mount flags, volume container, vị trí output, môi trường deploy | Chuyển sang volume writable được cấu hình hoặc điều chỉnh mount hợp lệ |
| IOException: "Broken pipe" / "Connection reset" | Kết nối truyền dữ liệu đã bị đóng/reset khi chương trình ghi/đọc; là thông điệp từ network/pipe | Đầu nhận đã đóng? timeout? proxy/load balancer? số byte đã truyền? | Không tự retry việc ghi không idempotent; điều tra lifecycle kết nối và cơ chế xác nhận |
| InvalidPathException | Chuỗi path không hợp lệ theo hệ thống/provider hiện tại, ví dụ ký tự bị cấm; đây là unchecked exception | Dữ liệu đầu vào, OS, cú pháp path, Path.of(...) | Validate cấu trúc đầu vào, chuyển đổi format đúng theo hệ điều hành; không thay ký tự tùy ý làm sai định danh |
| ClosedFileSystemException | Truy cập FileSystem đã đóng, ví dụ hệ tệp ZIP được mount qua FileSystems; unchecked | Thời gian close, code giữ Path, lifecycle FileSystem provider | Đảm bảo các thao tác cần thiết diễn ra trước khi đóng và không dùng lại Path sau khi provider đã close |

**Lưu ý về checked/unchecked:** NoSuchFileException, AccessDeniedException, FileAlreadyExistsException và FileSystemException kế thừa IOException; InvalidPathException, ClosedFileSystemException và SecurityException kế thừa RuntimeException. Vì vậy catch(IOException) không bắt được mọi loại thất bại được liệt kê.

**Lưu ý về tồn tại và quyền:** Files.exists(path) trả false cả khi không thể xác định tình trạng tồn tại trong một số trường hợp; Files.notExists(path) cũng có thể trả false khi trạng thái không rõ. Đừng biến false thành bằng chứng chắc chắn file không tồn tại. Thao tác thực tế vẫn có thể thất bại do thay đổi đồng thời, dù ngay trước đó kiểm tra thành công.

## 2. Bảng lỗi khi đọc/ghi stream, buffer và channel

| Lỗi/triệu chứng | Thường xuất hiện khi nào | Thông tin cần kiểm tra | Hướng xử lý |
| --- | --- | --- | --- |
| EOFException | API yêu cầu đọc đủ một cấu trúc nhưng hết dữ liệu trước khi hoàn thành, ví dụ DataInputStream.readInt | Độ dài dữ liệu thực, format/thứ tự read-write, có header/version/checksum không, nguồn đã ghi xong chưa | Kiểm tra framing và dữ liệu cắt dở; phân biệt EOF hợp lệ ở vòng đọc byte và EOF bất thường khi đọc record |
| IOException: "Stream closed" | Đọc/ghi sau khi stream đã đóng; đôi khi wrapper khác đóng stream gốc | try-with-resources nào sở hữu resource, ai đóng trước, luồng sử dụng | Xác định ownership, không chia sẻ stream đã đóng; tránh đóng resource ở tầng không sở hữu |
| ClosedChannelException | Thao tác channel khi channel đã bị đóng | Thứ tự close, xử lý đa luồng, shutdown/cancel, channel lifecycle | Điều phối lifecycle, không tái sử dụng channel đã đóng |
| AsynchronousCloseException | Luồng khác đóng channel đang bị block trong một thao tác I/O | Thread nào close, có stop/cancel/shutdown không, stack trace tác vụ | Coi là hủy thao tác khi shutdown có chủ ý; sửa cạnh tranh đóng sớm nếu bất thường |
| ClosedByInterruptException | Thread bị interrupt khi đang block trên interruptible channel; channel cũng bị đóng | Thread interruption, executor shutdownNow, cancellation, trạng thái channel | Phân biệt hủy tác vụ với lỗi mạng/đĩa; tuân thủ chính sách interrupt và không retry mù |
| NonReadableChannelException / NonWritableChannelException | Dùng channel ở mode không cho đọc hoặc ghi; unchecked | Mode mở channel, OpenOption.READ/WRITE/APPEND, buffer và nhánh logic | Mở channel đúng mode và chỉ thực hiện thao tác được hỗ trợ |
| OverlappingFileLockException | Một lock trên cùng vùng file chồng lấn lock đã giữ trong cùng JVM, hoặc đang chờ lock chồng lấn; unchecked | Các lock đã được JVM giữ, vùng start/size, quản lý vòng đời FileLock | Điều phối lock trong cùng tiến trình, tránh khóa trùng; luôn release lock thích hợp |
| FileLockInterruptionException | Thread bị interrupt khi đang chờ lock của FileChannel | Chính sách interrupt, lịch sử lock, trạng thái worker/task | Điều tra tranh chấp khóa hoặc yêu cầu hủy; xử lý cancel đúng cách |
| InterruptedIOException | Một thao tác I/O bị ngắt và có thể đã chuyển một phần dữ liệu; có trường bytesTransferred | bytesTransferred, loại stream, thao tác hiện tại, trạng thái thread | Không tự suy luận toàn bộ thao tác chưa làm gì; quyết định resume/retry theo giao thức |
| IOException khi flush/close | Dữ liệu đệm chưa đẩy hết hoặc lỗi đóng resource, có thể xảy ra sau các lần write thành công | flush/close thực sự có chạy không, exception gốc và suppressed, FS/đường truyền còn sống? | Không công bố giao dịch thành công chỉ vì write trả về; kiểm tra lỗi close và đồng bộ/commit khi cần |
| BufferOverflowException / BufferUnderflowException | Ứng dụng ghi/đọc quá giới hạn còn lại của ByteBuffer; unchecked | position/limit/capacity, flip/clear/compact, kích thước record | Kiểm tra remaining() và giao thức byte; sửa vòng đời buffer, không tăng buffer tùy tiện |
| ReadOnlyBufferException | Ghi vào ByteBuffer chỉ đọc; unchecked | Buffer được tạo bởi asReadOnlyBuffer, map READ_ONLY hay cơ chế chia sẻ | Dùng buffer writable khi cần ghi hoặc tách vùng nhớ đúng mục đích |

### Ba điểm gây nhầm lẫn

- InputStream.read() trả **-1** khi đạt EOF thông thường. Đây là kết quả hợp lệ; không nhất thiết có EOFException. Ngược lại, DataInputStream.readFully/readInt có thể báo EOFException nếu không đủ byte cho cấu trúc được yêu cầu.
- Đối với InputStream.read(byte[]), số byte trả về có thể ít hơn độ dài buffer ngay cả khi **chưa** tới EOF. Cần lặp cho tới khi đủ dữ liệu theo framing/record, hoặc dùng readNBytes/readFully thích hợp.
- OutputStream.close() có thể flush phần đệm, nhưng không nên mặc định đó là cam kết dữ liệu đã được lưu bền vững trên đĩa. Nếu ứng dụng cần durability, phải xác định cơ chế sync/transaction cụ thể, ví dụ FileChannel.force(...) và giới hạn bảo đảm của môi trường.

## 3. Bảng lỗi charset, văn bản và dữ liệu nhị phân

| Lỗi/triệu chứng | Nguyên nhân thường gặp | Kiểm tra gì? | Hướng xử lý |
| --- | --- | --- | --- |
| MalformedInputException | Dãy byte không hợp lệ theo charset đã chọn, như UTF-8 có byte tiếp nối sai | Nguồn byte, BOM, charset ghi/đọc, vị trí lỗi, file có bị cắt? | Chọn đúng charset theo hợp đồng dữ liệu; chỉ thay thế ký tự lỗi nếu nghiệp vụ chấp nhận mất thông tin |
| UnmappableCharacterException | Ký tự Unicode không thể biểu diễn trong charset đích hoặc chuỗi chuyển mã không ánh xạ được | Encoder/decoder, charset đích, dữ liệu gốc, coding-error action | Dùng UTF-8/Unicode phù hợp, hoặc xử lý lỗi chuyển mã minh bạch |
| CharacterCodingException | Nhóm checked exception cho lỗi mã hóa/giải mã ký tự | IOException cause, decoder/encoder, byte mẫu đã ẩn dữ liệu nhạy cảm | Giữ dữ liệu gốc để chẩn đoán; xác định charset trước khi decode |
| UTFDataFormatException | Đọc/ghi định dạng modified UTF-8 của DataInput/DataOutput (ví dụ readUTF/writeUTF) không hợp lệ hoặc giới hạn format bị vi phạm | Có đúng cặp writeUTF/readUTF không, format/số byte đã ghi, dữ liệu bị cắt không | Không dùng readUTF để đọc UTF-8 text bất kỳ; theo đúng giao thức DataInput/DataOutput |
| Ký tự thành "�" (replacement character) nhưng không throw | Decoder dùng hành vi thay thế lỗi hoặc dữ liệu đã bị mã hóa sai trước đó | Từng bước encode/decode, CharsetDecoder.onMalformedInput, lưu dữ liệu nguồn | Chọn chính sách REPORT để phát hiện sớm khi cần giữ nguyên dữ liệu |
| Ký tự "mojibake" (ví dụ "Ã©") | Byte dùng một charset nhưng giải mã bằng charset khác | Charset gốc/đích, BOM, default charset và thông tin truyền tải | Chỉ định charset tường minh ở cả hai đầu; không encode lại chuỗi đã decode sai để vá bằng mẹo |
| File trông như rỗng hoặc thiếu dòng | Buffer chưa flush, Reader/Writer chưa close, logic đọc bỏ mất phần tử cuối | flush/close, vòng while, line separators, length trên đĩa và trong RAM | Đóng stream đúng, kiểm tra code đọc đến EOF và xử lý phần dữ liệu cuối |

**Phân biệt:** UTF-8 thường dùng với Files.readString(path, StandardCharsets.UTF_8), InputStreamReader(..., UTF_8), OutputStreamWriter(..., UTF_8). DataInputStream.readUTF và DataOutputStream.writeUTF sử dụng **modified UTF-8 có tiền tố độ dài**, không tương đương việc đọc/ghi văn bản UTF-8 thông thường.

## 4. Bảng lỗi mạng: Socket, timeout và TLS

Nhóm này thuộc java.net và javax.net.ssl; một số lỗi là lớp con của IOException. Khi chẩn đoán, phải biết lỗi xảy ra ở **DNS, TCP connect, TLS handshake, đọc, ghi hay đóng**.

| Lỗi | Dấu hiệu/ý nghĩa thường gặp | Kiểm tra thực tế | Cách tiếp cận |
| --- | --- | --- | --- |
| UnknownHostException | Không phân giải được hostname hoặc hostname không có địa chỉ phù hợp | Hostname, DNS từ môi trường chạy, search domain, cấu hình container/proxy, độ ổn định DNS | Sửa DNS/host; chỉ retry giới hạn khi lỗi thực sự tạm thời |
| ConnectException | Kết nối TCP bị từ chối hoặc thất bại ở bước connect, như "Connection refused" | Host:port, service đang lắng nghe, firewall, routing, IPv4/IPv6, proxy | Xác nhận endpoint và trạng thái dịch vụ; không nhầm với read timeout |
| SocketTimeoutException | Vượt timeout khi socket connect hoặc read (theo thao tác cấu hình) | Connect timeout hay SO_TIMEOUT? Mạng có kết nối chưa? server phản hồi chậm? | Đặt timeout có chủ đích, đo latency, retry theo idempotency/backoff |
| SocketException | Lỗi socket chung, như reset, broken pipe, socket closed | Peer đã đóng chưa, local close, idle timeout, HTTP proxy, keepalive | Xác định lifecycle và phần dữ liệu có thể đã chuyển; không coi mọi reset là lỗi mạng có thể retry an toàn |
| BindException | Không thể bind địa chỉ/cổng, thường cổng đã được dùng hoặc địa chỉ không phù hợp | Socket local address/port, tiến trình đang giữ, cấu hình bind, quyền OS | Chọn port/địa chỉ hợp lệ, dừng tiến trình xung đột nếu được phép |
| SSLHandshakeException | Bắt tay TLS không hoàn thành (chứng chỉ, protocol, cipher, hostname, trust) | Cause chain, certificate chain/truststore, hostname, expiry, TLS version, đồng hồ hệ thống | Sửa trust/identity/protocol; không tắt xác thực chứng chỉ để bỏ qua lỗi |
| SSLException | Lỗi TLS tổng quát khi handshake hoặc truyền dữ liệu | Stack/cause, vị trí handshake hay application data, đóng kết nối | Tìm nguyên nhân gốc, kiểm tra giao thức và cấu hình hai đầu |

Các điều cần ghi nhớ:

- Timeout connect và timeout read là hai pha khác nhau. Connect thành công không chứng minh server sẽ trả dữ liệu đúng hạn.
- Retry thao tác đọc trạng thái thường khác retry lệnh ghi/thanh toán: nếu request đã đến server mà phản hồi mất, client có thể không biết thao tác đã hoàn thành hay chưa. Dùng request-id, idempotency key hay quy trình xác nhận theo ứng dụng.
- Không log payload, credential, private key, access token hoặc nội dung chứng chỉ nhạy cảm khi thu thập trace. Chỉ ghi thông tin kỹ thuật cần thiết cho việc chẩn đoán.

## 5. Bảng lỗi ZIP và object serialization

| Lỗi/triệu chứng | Vị trí và ý nghĩa | Thông tin cần kiểm tra | Hướng xử lý |
| --- | --- | --- | --- |
| ZipException | java.util.zip gặp dữ liệu ZIP/GZIP/DEFLATE không hợp lệ hoặc không thể xử lý | Định dạng thực tế, header/magic, file đã tải đủ chưa, CRC/entry, phương thức nén | Xác thực nguồn và tính toàn vẹn, đảm bảo giải nén đúng định dạng; hạn chế kích thước giải nén để chống zip bomb |
| EOFException khi giải nén | Stream nén bị cắt hoặc cấu trúc chưa đủ | Số byte tải/nhận so với mong đợi, checksum, close của nguồn | Truyền/tải lại có kiểm soát, xác nhận độ đầy đủ trước xử lý |
| StreamCorruptedException | ObjectInputStream thấy header, token hoặc cấu trúc Java serialized stream sai | Bên ghi có dùng ObjectOutputStream không, thứ tự đọc/ghi, byte bị chèn/cắt, protocol mismatch | Đồng nhất định dạng và thứ tự, tránh trộn dữ liệu văn bản/thô vào luồng object |
| InvalidClassException | Lớp không tương thích với luồng (serialVersionUID, kiểu/class descriptor), hoặc ObjectInputFilter từ chối | Full message/cause, serialVersionUID, thay đổi class, filter policy, tên lớp nhị phân | Sửa tương thích/schema hoặc allowlist đúng; không vô hiệu hóa filter chỉ để chạy qua lỗi |
| NotSerializableException | Ghi một lớp không Serializable trong đồ thị đối tượng | Kiểu được nêu trong message, trường có chứa resource/đối tượng khác không serializable | Dùng DTO serializable, tách state không được serialize bằng transient khi đúng ngữ nghĩa |
| OptionalDataException | ObjectInputStream đọc theo kiểu không khớp dữ liệu primitive/đối tượng trong luồng, hoặc tới cuối dữ liệu custom | Thứ tự writeObject/writeInt/readObject/readInt, custom readObject/writeObject | Khớp format và thứ tự, quản lý version rõ ràng |
| InvalidObjectException | Dữ liệu đối tượng không hợp lệ trong validation/readResolve hoặc logic khôi phục | Cause, invariants của class, dữ liệu gốc, mã kiểm tra trong custom deserialization | Kiểm tra và từ chối dữ liệu không hợp lệ, sửa nguồn hoặc migration được xác minh |
| ClassNotFoundException | Bên đọc không tìm được class mô tả trong stream; là checked exception, không kế thừa IOException | Classpath/module path, tên package/class, version triển khai, custom class loader | Bảo đảm class tương ứng hiện diện hoặc thay định dạng trao đổi dữ liệu rõ schema |

**Giới hạn bảo mật của serialization:** ObjectInputFilter có thể giới hạn lớp, độ sâu, số tham chiếu, bytes và kích thước mảng khi deserialization. Phải cài filter **trước readObject()**, ưu tiên allowlist theo nguồn tin cậy và vẫn giới hạn kích thước đầu vào bên ngoài. Filter không xác thực người gửi, không thay mã hóa/chữ ký, không ngăn mọi rủi ro trong custom readObject. Với đầu vào không tin cậy, cân nhắc định dạng trao đổi có schema và validation rõ ràng.

## 6. Ví dụ Java 17+ chạy độc lập: tạo và phân biệt ba lỗi I/O

Ví dụ này tạo một thư mục tạm, minh họa ba exception cụ thể theo trình tự rồi dọn tệp tạm. Mỗi lỗi được bắt bằng kiểu ngoại lệ phù hợp để người học nhìn thấy cách đọc nguyên nhân. Không dùng đường dẫn nhạy cảm hoặc tài nguyên bên ngoài.

Tạo tệp **IoErrorLookupDemo.java**:

~~~java
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IoErrorLookupDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("io-error-lookup-");
        Path missing = dir.resolve("missing.txt");
        Path existing = dir.resolve("existing.txt");
        Path invalidUtf8 = dir.resolve("invalid-utf8.txt");

        try {
            // 1. Không thể đọc tệp chưa tồn tại.
            try {
                Files.readString(missing, StandardCharsets.UTF_8);
            } catch (NoSuchFileException ex) {
                System.out.println("NoSuchFileException: missing.txt");
            }

            // 2. CREATE_NEW chỉ tạo file khi đường dẫn chưa tồn tại.
            Files.writeString(existing, "Hello", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            try {
                Files.writeString(existing, "Again", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ex) {
                System.out.println("FileAlreadyExistsException: existing.txt");
            }

            // 3. Hai byte dưới đây không tạo thành chuỗi UTF-8 hợp lệ.
            Files.write(invalidUtf8, new byte[] {(byte) 0xC3, (byte) 0x28});
            try {
                Files.readString(invalidUtf8, StandardCharsets.UTF_8);
            } catch (MalformedInputException ex) {
                System.out.println("MalformedInputException: invalid UTF-8");
            }
        } finally {
            Files.deleteIfExists(invalidUtf8);
            Files.deleteIfExists(existing);
            Files.deleteIfExists(missing);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac IoErrorLookupDemo.java
java IoErrorLookupDemo
~~~

Kết quả mong đợi trong môi trường có thể tạo/đọc/ghi/xóa thư mục tạm:

~~~text
NoSuchFileException: missing.txt
FileAlreadyExistsException: existing.txt
MalformedInputException: invalid UTF-8
~~~

**Vì sao ví dụ hoạt động?**

1. Files.readString(missing, UTF_8) gặp file chưa tồn tại nên trên default file-system provider phổ biến ném NoSuchFileException. Không có bước kiểm tra exists trước, vì thao tác đọc là nơi trạng thái thật được xác định.
2. Files.writeString(existing, ..., CREATE_NEW) tạo file lần đầu. Lần thứ hai, chính tùy chọn CREATE_NEW yêu cầu không ghi đè đường dẫn đang tồn tại, nên ném FileAlreadyExistsException. Đây là lựa chọn thường phù hợp khi tránh ghi đè vô ý.
3. Byte 0xC3 là ký tự mở đầu của một chuỗi hai byte UTF-8, nhưng byte tiếp theo 0x28 không phải continuation byte hợp lệ. Files.readString(..., UTF_8) báo MalformedInputException, cho thấy lỗi ở **giải mã byte thành ký tự**, không phải tệp thiếu hay quyền bị từ chối.
4. finally dọn các đường dẫn được tạo trong bài. Nếu bản thân việc cleanup thất bại, IOException có thể được ném từ finally; trong ứng dụng lớn, nên giữ lại exception gốc và bổ sung lỗi cleanup như suppressed/log riêng thay vì vô tình che mất nguyên nhân đầu tiên.

**Giới hạn của ví dụ:** Nếu thư mục tạm không ghi được, hết dung lượng hoặc một tiến trình khác can thiệp, chương trình có thể thất bại bằng ngoại lệ khác trước khi in đủ ba dòng. Điều đó là hành vi I/O thực tế cần chẩn đoán, không phải lý do bắt mọi exception rồi in kết quả giả.

## 7. Mẫu chẩn đoán lỗi và ghi log có ngữ cảnh

Không cần ghi toàn bộ dữ liệu khách hàng để debug I/O. Ghi thông tin đủ để tìm ra **thao tác nào**, **đích nào** và **lý do hệ điều hành/provider báo gì**.

Ví dụ minh họa một method trong ứng dụng đã có logger và path:

~~~java
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

static String loadConfig(Path path) throws IOException {
    try {
        return Files.readString(path, StandardCharsets.UTF_8);
    } catch (NoSuchFileException ex) {
        // File được yêu cầu không tồn tại; chính sách có thể là trả 404.
        throw new IOException("Config is missing: " + path.getFileName(), ex);
    } catch (AccessDeniedException ex) {
        // Kiểm tra service account, ACL và quyền trên thư mục cha.
        throw new IOException("Cannot read config: access denied", ex);
    } catch (FileSystemException ex) {
        // getReason() có thể null; nội dung có thể phụ thuộc OS/provider.
        throw new IOException("Cannot read config: " + ex.getReason(), ex);
    }
}
~~~

Đây là **đoạn method minh họa**, không phải một chương trình độc lập vì chưa có class và main. Trong môi trường thực:

- Ghi stack trace/cause vào kênh log nội bộ có quyền phù hợp; chỉ trả thông báo an toàn cho người dùng. Không tự động in absolute path chứa tên tài khoản, bí mật hoặc cấu trúc hạ tầng ra API công khai.
- Giữ nguyên cause khi bọc lỗi. Không chuyển một lỗi quyền sang "file not found" trong log vận hành; tầng HTTP có thể trả mã thông báo khác nhưng cần giữ bằng chứng gốc.
- Với FileSystemException, dùng getFile(), getOtherFile(), getReason() nếu hữu ích; một số giá trị có thể null và exception message có thể mang thông tin không nên lộ công khai.
- Đừng bắt SecurityException bằng IOException. Nếu cần xử lý lỗi chính sách runtime, bắt theo hợp đồng riêng và đừng làm mờ dấu hiệu vi phạm chính sách.
- Khi dùng try-with-resources, lỗi từ close của resource có thể nằm trong getSuppressed() của exception chính. Đọc cả suppressed khi chuỗi thao tác có nhiều resource.

### Mẫu ghi nhận sự cố I/O

~~~text
Operation      : READ_TEXT / WRITE_BYTES / MOVE / SOCKET_CONNECT / DESERIALIZE
API            : Files.readString(Path, UTF_8)
Resource       : <path alias hoặc endpoint đã ẩn thông tin nhạy cảm>
Environment    : JDK version, OS, container/mount, service account
Exception      : <fully-qualified exception class>
Message/Reason : <thông điệp rút gọn, đã loại thông tin nhạy cảm>
Cause          : <cause chain, stack trace nội bộ>
Suppressed     : <các lỗi cleanup/close nếu có>
Input format   : UTF-8 / binary protocol / serialized object / ZIP / ...
Timing         : Thời điểm xảy ra, pha open/read/write/flush/close
Observed state : Exists? Directory? Permissions? Free space? Host:port reachable?
Concurrency    : Có writer/deleter/closer đồng thời? Có retry trước đó?
Next action    : Fix path / permissions / schema / storage / connectivity / code
~~~

Thông tin exists/isDirectory/isReadable chỉ là ảnh chụp tại một thời điểm, có thể thay đổi ngay sau đó. Không sử dụng chúng làm bảo đảm có thể mở/đọc/ghi thành công; vẫn cần xử lý lỗi từ thao tác thật.

## 8. Quy trình điều tra theo thứ tự ít rủi ro

1. **Xác định điểm thất bại:** đọc 1–3 stack frame thuộc mã của bạn cùng exception class, cause, suppressed. Đừng bắt đầu bằng việc thay charset hay cấp thêm quyền khi chưa biết pha lỗi.
2. **Đối chiếu loại API:** FileInputStream có thể báo FileNotFoundException rộng; Files có thể cung cấp NoSuchFileException hoặc AccessDeniedException cụ thể hơn. Với mạng, xác định connect/read/write/TLS trước khi đặt timeout.
3. **Kiểm tra dữ kiện không thay đổi hệ thống:** version JDK/OS, đường dẫn thực tế, working directory, isRegularFile/isDirectory, thông tin permission/mount nếu được phép; không chạy lệnh xóa, chmod đệ quy hoặc kill tiến trình để thử.
4. **Kiểm tra tài nguyên:** còn dung lượng/quota không, có bị leak stream/channel/socket không, file handle/lock có bị giữ không, pool và số lượng tác vụ I/O song song có bất thường không.
5. **Kiểm tra hợp đồng dữ liệu:** bên ghi và bên đọc có chung charset, cách phân khung record, thứ tự field, version/schema, checksum/length không; với ZIP và serialized stream, có đúng định dạng không.
6. **Tái hiện có kiểm soát:** dùng tệp tạm và dữ liệu không nhạy cảm; kiểm tra thành công + đầu vào thiếu + quyền bị từ chối + dữ liệu cắt dở + file đang bị sửa đồng thời khi các tình huống đó thực sự liên quan.
7. **Sửa nguyên nhân, kiểm thử case âm:** sửa đường dẫn/permissions/resource lifecycle/charset/framing; xác nhận exception cụ thể được xử lý đúng. Với thao tác ghi, kiểm tra trạng thái trước retry để tránh trùng hoặc hỏng dữ liệu.
8. **Bổ sung quan sát:** nếu sự cố chưa tái hiện, log thêm thao tác, số byte kỳ vọng/đã đọc (không log nội dung), thời gian connect/read/write, path alias, exception cause và correlation id.

### Câu hỏi quyết định nhanh

| Câu hỏi | Nếu câu trả lời là "Có" | Kiểm tra kế tiếp |
| --- | --- | --- |
| Lỗi xảy ra ngay khi mở file? | Khả năng cao liên quan path, type, permission hoặc resource | Path resolve, working directory, quyền của service account |
| Mở được nhưng fail sau nhiều lần đọc? | Dữ liệu bị cắt, framing sai, lỗi kết nối hoặc nguồn thay đổi | Độ dài/record boundary, EOF, bytes đã đọc, writer/peer |
| Chỉ lỗi với tiếng Việt/ký tự đặc biệt? | Charset hoặc chuyển mã không đồng nhất | UTF-8, BOM, decoder policy, byte gốc |
| Chỉ lỗi khi tải nhiều/đồng thời? | Leak, lock, contention hoặc race condition | try-with-resources, file handles, channel close, pool size |
| Chỉ lỗi khi deploy/container? | Working directory, mount, account, DNS hoặc permission đổi | Environment diff, volume, path, service user, network route |
| Chỉ lỗi với dữ liệu lưu từ bản cũ? | Format/schema/serialVersionUID thay đổi | Version, migration, classpath, backward compatibility |
| Chỉ lỗi sau thời gian idle của socket? | Idle timeout, peer/proxy close hoặc connection reset | Timeout cấu hình, keepalive, lifecycle, request replay safety |

## 9. Ngăn lỗi ngay khi thiết kế chương trình

### Quản lý vòng đời tài nguyên

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

static void printNonEmptyLines(Path path) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(
            path, StandardCharsets.UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                System.out.println(line);
            }
        }
    }
}
~~~

Đây là method minh họa. try-with-resources đóng BufferedReader kể cả khi đọc thất bại và giúp tránh leak. Không mở stream ngoài try rồi quên close ở một nhánh return hoặc catch.

### Chọn phương án mở/ghi theo hợp đồng

- **Chỉ đọc:** Files.newInputStream(path, READ) hoặc Files.newBufferedReader(path, UTF_8); xử lý file thiếu, quyền và lỗi dữ liệu theo mục đích.
- **Tạo file mới, tuyệt đối không ghi đè:** Files.write(..., CREATE_NEW) hoặc Files.createFile(path), bắt FileAlreadyExistsException để xử lý xung đột.
- **Ghi thay thế có kiểm soát:** Ghi dữ liệu đầy đủ vào file tạm cùng file system, kiểm tra flush/close, rồi move thay thế nếu hợp đồng cho phép. ATOMIC_MOVE không được mọi provider hỗ trợ; phải xử lý AtomicMoveNotSupportedException và quyết định fallback theo yêu cầu về atomicity, không âm thầm giả vờ atomic.
- **Đọc dữ liệu lớn:** Dùng stream/channel và bộ đệm với giới hạn; tránh Files.readAllBytes cho file không kiểm soát kích thước vì có thể gây áp lực bộ nhớ.
- **Đọc text:** Chỉ định UTF-8 hoặc charset theo tài liệu dữ liệu, không dựa vào default charset khi trao đổi với hệ thống khác.
- **I/O mạng:** Cấu hình timeout từng pha, giới hạn kích thước payload và sử dụng retry có điều kiện/idempotent; ghi nhận kết quả một phần khi giao thức có yêu cầu.
- **Serialization:** Đặt ObjectInputFilter phù hợp trước khi gọi ObjectInputStream.readObject; validate dữ liệu sau khi khôi phục; hạn chế input bytes từ đầu.

### Khi nào nên retry?

| Loại tình huống | Retry tự động? | Lý do |
| --- | --- | --- |
| File chưa tồn tại do path gõ sai | Thường không | Lặp lại không sửa path |
| AccessDeniedException do ACL cố định | Thường không | Cần sửa quyền hoặc chính sách |
| FileAlreadyExistsException với CREATE_NEW | Không với cùng tên và cùng yêu cầu | Việc lặp lại sẽ tiếp tục xung đột |
| SocketTimeoutException trên truy vấn idempotent | Có thể, có giới hạn và backoff | Đôi lúc lỗi mạng tạm thời, nhưng vẫn cần giới hạn |
| Lệnh ghi có tác dụng phụ đã gửi, phản hồi timeout | Chỉ nếu có giao thức đảm bảo/idempotency key | Có nguy cơ thực thi hai lần |
| Hết dung lượng hoặc hết file handles | Không retry gấp vô hạn | Cần phục hồi tài nguyên và xử lý phần dữ liệu đã ghi |
| MalformedInputException do file sai charset cố định | Không | Cần sửa format/charset, retry không thay đổi byte |
| EOF bất thường của dữ liệu tải dở | Có thể tải lại nếu nguồn/hợp đồng cho phép | Phải kiểm tra checksum/length và tránh ghép sai phiên |

## 10. Kiểm tra kiến thức

1. Vì sao FileNotFoundException không đủ để kết luận file không tồn tại?
2. Files.exists(path) vừa trả true, nhưng ngay sau đó Files.readString(path) vẫn ném NoSuchFileException. Điều này có thể xảy ra như thế nào?
3. Khi DataInputStream.readInt ném EOFException, tại sao cách xử lý khác với vòng while(InputStream.read() != -1)?
4. Làm thế nào để nhận biết lỗi charset khác với lỗi stream byte bị cắt?
5. Khi thao tác Files.move thất bại, thông tin nào của FileSystemException giúp tìm nguyên nhân?
6. Tại sao đặt CREATE_NEW thường an toàn hơn chuỗi "kiểm tra exists rồi mới tạo" khi có nhiều writer?
7. SocketTimeoutException trong lúc connect khác gì trong lúc read? Vì sao không retry mọi lệnh gửi một cách vô điều kiện?
8. Lỗi IOException từ close ảnh hưởng thế nào đến khẳng định "ghi thành công"?
9. Những ngoại lệ nào trong bảng là unchecked, và vì sao catch(IOException) không bao phủ chúng?
10. InvalidClassException có thể biểu thị hai tình huống rất khác nhau nào trong Java serialization?

**Tiêu chí đạt:** Bạn chỉ ra được thao tác đang lỗi, exception và nguyên nhân có thể kiểm chứng; chọn được API/check cụ thể để phân biệt giả thuyết; xử lý tài nguyên và lỗi đúng, tránh retry nguy hiểm; không tiết lộ thông tin nhạy cảm khi ghi log.

## Tài liệu tham khảo Java SE 17

- java.io.IOException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/IOException.html
- java.io.EOFException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/EOFException.html
- java.io.FileNotFoundException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileNotFoundException.html
- java.nio.file.Files: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- java.nio.file.FileSystemException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystemException.html
- java.nio.charset.MalformedInputException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/MalformedInputException.html
- java.nio.channels.ClosedChannelException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ClosedChannelException.html
- java.net.SocketTimeoutException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/SocketTimeoutException.html
- java.io.ObjectInputFilter: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html
- Java tutorials — Basic I/O: https://docs.oracle.com/javase/tutorial/essential/io/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-007">Bảng API truyền thống, NIO, NIO.2 và các mốc phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng API truyền thống, NIO, NIO.2 và các mốc phiên bản Java

Java có nhiều lớp I/O cùng tồn tại vì nền tảng được mở rộng qua các phiên bản JDK. **`java.io` truyền thống**, **NIO (Java 1.4)** và **NIO.2 (Java 7)** là các nhóm API có điểm mạnh khác nhau; NIO.2 bổ sung hệ thống tệp hiện đại và một số API bất đồng bộ, **không thay thế toàn bộ `java.io` hoặc NIO**. Bảng tra cứu này giúp tìm đúng lớp, hiểu mốc phiên bản và chọn API phù hợp cho chương trình Java 17+.

## 1. Bản đồ ba thế hệ API

| Nhóm | Xuất hiện | Gói/lớp tiêu biểu | Mô hình chính | Bài toán phù hợp |
|---|---|---|---|---|
| I/O truyền thống | Java 1.0; bổ sung Reader/Writer ở Java 1.1 | `java.io.InputStream`, `OutputStream`, `Reader`, `Writer`, `File`, `RandomAccessFile` | Luồng byte hoặc ký tự, xử lý tuần tự; các lệnh đọc/ghi thông thường có thể chặn | Đọc/ghi stream, protocol đơn giản, bộ lọc/đệm, API tương thích lâu đời |
| NIO (New I/O) | Java 1.4 | `java.nio.Buffer`/`ByteBuffer`, `java.nio.channels.FileChannel`/`SocketChannel`/`Selector`, `java.nio.charset.Charset` | Buffer, channel, charset; hỗ trợ multiplexing và non-blocking cho **một số** channel | Dữ liệu nhị phân, I/O dùng buffer, nhiều kết nối mạng với selector, thao tác channel |
| NIO.2 (thường gọi NIO.2/JSR 203) | Java 7 | `java.nio.file.Path`, `Files`, `FileSystem`, `WatchService`, `FileVisitor`; `java.nio.channels.AsynchronousFileChannel` | Hệ thống tệp giàu chức năng, thuộc tính tệp, duyệt cây, giám sát thay đổi, I/O bất đồng bộ | Quản lý file/directory, copy/move/delete, metadata, symbolic link, watch directory, async channel |

**Phân biệt tên:** NIO nghĩa là *New I/O*, không phải lời hứa mọi thao tác đều *non-blocking*. `FileChannel` có các thao tác đọc/ghi thường mang tính blocking; `SocketChannel` có thể cấu hình blocking hoặc non-blocking. `Selector` chỉ dùng với `SelectableChannel` phù hợp (ví dụ `SocketChannel`), không đăng ký `FileChannel` để theo dõi file thường.

**Hai thứ cùng có thể đúng:** `Files.newInputStream(path)` thuộc tiện ích NIO.2 nhưng trả về kiểu `java.io.InputStream`; một ứng dụng có thể kết hợp `Path`, `Files`, stream và channel trong cùng luồng xử lý. Cách phân loại theo *API được bổ sung ở mốc nào*, không phải các công nghệ loại trừ lẫn nhau.

## 2. Dòng thời gian các mốc quan trọng

| Mốc Java | API/đặc điểm tiêu biểu | Ý nghĩa đối với mã nguồn |
|---|---|---|
| **JDK 1.0 (1996)** | `InputStream`, `OutputStream`, `File`, `FileInputStream`, `FileOutputStream`, `RandomAccessFile` | Nền tảng byte stream và đường dẫn file truyền thống |
| **JDK 1.1 (1997)** | Họ `Reader`/`Writer`, `InputStreamReader`, `OutputStreamWriter` | Tầng chuyển đổi byte ↔ ký tự theo character encoding |
| **JDK 1.4 (2002)** | `ByteBuffer`/`CharBuffer`, `Channel`, `FileChannel`, `SocketChannel`, `Selector`, `Charset` | NIO 1: buffer/channel, mã hóa ký tự, multiplexing socket |
| **Java 7 (2011)** | `Path`, `Paths`, `Files`, `FileSystem`, `WatchService`, `FileVisitor`, `StandardOpenOption`; `AsynchronousFileChannel`/`AsynchronousSocketChannel` | NIO.2: API hệ thống tệp mới, tệp bất đồng bộ và quan sát thư mục |
| **Java 8 (2014)** | `Files.list`, `Files.walk`, `Files.find`, `Files.lines` | Tích hợp `Stream` để liệt kê/tìm/đọc dòng theo nhu cầu; stream giữ tài nguyên cần đóng |
| **Java 9 (2017)** | `InputStream.readAllBytes`, `readNBytes`, `transferTo` | Thêm tiện ích cho stream truyền thống; **khác** `Files.readAllBytes` vốn có từ Java 7 |
| **Java 11 (2018)** | `Path.of`; `Files.readString`, `Files.writeString` | Thuận tiện khi đọc/ghi tệp văn bản UTF-8; `Paths.get` vẫn là lựa chọn tương thích Java 7 |
| **Java 12 (2019)** | `Files.mismatch` | Trả chỉ số byte khác đầu tiên giữa hai file, hoặc `-1` nếu không có khác biệt |
| **Java 21 (2023)** | Virtual threads chuẩn hóa (JEP 444), dùng được với nhiều cách viết I/O blocking | Cải thiện mô hình lập trình đồng thời; **không tạo ra một thế hệ API “NIO.3”** |

Ngày/phiên bản ở bảng chỉ ra **lần đầu API xuất hiện**, không phải ngày một lớp cũ ngừng hoạt động. Trong Java 17/21, `java.io`, NIO và NIO.2 vẫn được hỗ trợ. Hãy kiểm tra chính xác **phương thức** chứ không chỉ **tên lớp**: `InputStream` có từ Java 1.0 nhưng `InputStream.transferTo` phải tới Java 9; `Path` có từ Java 7 nhưng `Path.of` chỉ có từ Java 11.

## 3. Bảng tra cứu lớp/phương thức theo nhu cầu

| Nhu cầu | `java.io` truyền thống | NIO (Java 1.4) | NIO.2 (Java 7+) và bổ sung sau đó |
|---|---|---|---|
| Biểu diễn một đường dẫn | `File` | — | `Path`/`Paths.get` (7), `Path.of` (11) |
| Tạo thư mục, kiểm tra, lấy kích thước | `File.mkdir`/`mkdirs`, `exists`, `length` | — | `Files.createDirectory`/`createDirectories`, `exists`, `size` |
| Mở luồng byte từ file | `FileInputStream`/`FileOutputStream`, đệm bằng `BufferedInputStream`/`BufferedOutputStream` | `FileChannel`, `ByteBuffer` | `Files.newInputStream`/`newOutputStream`, `Files.newByteChannel` |
| Đọc/ghi ký tự có charset | `InputStreamReader`/`OutputStreamWriter`, `BufferedReader`/`BufferedWriter` | `Charset`, `CharsetDecoder`/`CharsetEncoder` | `Files.newBufferedReader`/`newBufferedWriter` (7); `readString`/`writeString` (11) |
| Đọc toàn bộ file nhỏ | `FileInputStream` và vòng lặp đọc | Channel và buffer | `Files.readAllBytes` (7); `Files.readString` (11) |
| Đọc các dòng file lớn | `BufferedReader.readLine` | `FileChannel` + decoder tự quản lý | `Files.lines` (8) tạo stream dòng; phải đóng stream |
| Đọc/ghi dữ liệu tại vị trí bất kỳ | `RandomAccessFile.seek` | `FileChannel.position`, `read/write(ByteBuffer, position)`, `map` | `Files.newByteChannel` → `SeekableByteChannel` |
| Sao chép/di chuyển/đổi tên file | Sao chép bằng stream; `File.renameTo` có hành vi phụ thuộc nền tảng | `FileChannel.transferTo`/`transferFrom` | `Files.copy`, `Files.move`, các `CopyOption` |
| Xóa file và biết nguyên nhân lỗi | `File.delete` trả `boolean` | — | `Files.delete` ném lỗi cụ thể; `deleteIfExists` |
| Liệt kê hoặc duyệt thư mục | `File.listFiles` | — | `DirectoryStream`, `Files.walkFileTree` (7); `Files.list/walk/find` (8) |
| Đọc metadata/permission/symbolic link | `File.lastModified`, `canRead` ... giới hạn | — | `Files.readAttributes`, `BasicFileAttributes`, `PosixFileAttributes`, `FileOwnerAttributeView`, `Files.readSymbolicLink` |
| Phát hiện file xuất hiện/thay đổi | Tự polling `File` | — | `WatchService` và `WatchKey` (7) |
| Mạng socket truyền thống | `java.net.Socket`/`ServerSocket` + stream | `SocketChannel`, `ServerSocketChannel`, `Selector` | `AsynchronousSocketChannel`/`AsynchronousServerSocketChannel` (7) |
| File I/O bất đồng bộ | Không có API tương ứng trực tiếp trong họ stream cổ điển | `FileChannel` thường đọc/ghi đồng bộ | `AsynchronousFileChannel` (7), `Future` hoặc `CompletionHandler` |

**Không suy ra hiệu năng chỉ từ tên API.** `Files.copy` có thể tiện và ít lỗi; `FileChannel.transferTo` có thể cho phép tối ưu ở hệ điều hành nhưng không đảm bảo zero-copy mọi nền tảng; `ByteBuffer.allocateDirect` có chi phí cấp phát và quản lý riêng. Đo theo tải thực tế, kích thước dữ liệu và hệ thống tệp.

## 4. Mốc phiên bản theo đúng phương thức: tránh nhầm khi build

| Câu lệnh/tính năng | Java tối thiểu | Ghi chú |
|---|---:|---|
| `new File("a.txt")`, `new FileInputStream(...)` | 1.0 | `File` chỉ đại diện đường dẫn; không tự mở stream |
| `new InputStreamReader(in, charset)` | 1.1 (họ Reader/Writer) | Luôn nên chỉ định charset thay vì mặc định nền tảng |
| `ByteBuffer.allocate(1024)`, `FileInputStream.getChannel()` | 1.4 | `FileChannel`/`ByteBuffer` thuộc NIO 1 |
| `Paths.get("a.txt")`, `Files.readAllBytes(path)` | 7 | Thuộc NIO.2; toàn bộ bytes có thể tốn nhiều RAM |
| `Files.copy(source, target, ...)`, `Files.walkFileTree(root, visitor)` | 7 | Hoạt động theo `Path`; có các tùy chọn và ngoại lệ riêng |
| `FileChannel.open(path, options...)` | 7 | Lớp `FileChannel` cũ hơn phương thức tiện ích `open` này |
| `Files.lines(path)`, `Files.walk(root)` | 8 | Stream thường gắn với tài nguyên I/O, cần `try-with-resources` |
| `in.transferTo(out)`, `in.readAllBytes()` trên `InputStream` | 9 | Không nhầm `InputStream.transferTo` (9) với `FileChannel.transferTo` (1.4) |
| `Path.of("a.txt")`, `Files.readString(path)`, `Files.writeString(path, text)` | 11 | Mặc định UTF-8 nếu dùng overload không truyền charset |
| `Files.mismatch(path1, path2)` | 12 | Kết quả là chỉ số byte đầu tiên khác, không phải boolean |
| Virtual threads cho tác vụ I/O | 21 | Tính năng concurrency; không đổi lịch sử xuất hiện của NIO.2 |

Khi xây ứng dụng cho nhiều JDK, nên dùng `javac --release 8` (hoặc cấu hình build tương đương) để compiler kiểm tra cả ngôn ngữ lẫn API có trong Java 8; chỉ đặt `-source`/`-target` bằng 8 trên JDK mới không cung cấp cùng mức kiểm tra API. Ví dụ `Files.readString` không biên dịch được với `--release 8` vì cần Java 11.

## 5. Quan hệ giữa stream, channel, buffer, file system

~~~text
java.io
  InputStream / OutputStream        Reader / Writer
           │                              │
           └────────── dữ liệu ──────────┘
                         │
java.nio                 │
  ByteBuffer ←→ Channel  │   Charset: giải mã/mã hóa byte ↔ ký tự
  SelectableChannel → Selector (khi dùng socket phù hợp)
                         │
java.nio.file (NIO.2)
  Path → Files (copy, move, attributes, tree, stream factories)
             │
             ├── Files.newInputStream(...) → java.io.InputStream
             ├── Files.newByteChannel(...) → SeekableByteChannel
             ├── Files.newBufferedReader(...) → java.io.BufferedReader
             └── FileSystem / WatchService / FileVisitor
~~~

- **Stream** mô tả dòng dữ liệu tuần tự. `InputStream`/`OutputStream` xử lý **byte**; `Reader`/`Writer` xử lý **char** (ký tự UTF-16 ở mức Java). Chuyển giữa byte và text cần charset; `BufferedReader` là bộ đệm ở tầng ký tự, không thay cho decoder.
- **Channel + Buffer** mô tả trao đổi qua một vùng bộ nhớ có `position`, `limit`, `capacity`. Sau khi ghi vào buffer để đọc lại, thường `flip()`; sau khi tiêu thụ, dùng `clear()` hoặc `compact()` tùy có cần giữ bytes chưa xử lý.
- **Path + Files** mô tả đối tượng trong hệ thống tệp và thao tác với nó. `Path` có thể chỉ tới tệp chưa tồn tại; `Files` thực hiện các hành động có thể ném `IOException`.
- **Bridge**: `File.toPath()` và `Path.toFile()` hỗ trợ chuyển giữa mã truyền thống và NIO.2 với file system mặc định. `Path.toFile()` không nhất thiết khả dụng cho các provider tùy biến (chẳng hạn hệ thống tệp ZIP).
- **Blocking / non-blocking / asynchronous** là tính chất của **cơ chế thao tác cụ thể**. Không thể kết luận chỉ từ package: NIO chứa `FileChannel` thường blocking; NIO.2 chứa `Files.readAllBytes` đồng bộ; channel mạng có thể non-blocking; asynchronous channel dùng kết quả tương lai hoặc callback.

## 6. Ví dụ Java 17+: một file, bốn cách truy cập theo mốc API

Chương trình này tạo tệp tạm và viết dữ liệu bằng `FileOutputStream`, sau đó đọc bằng (1) stream truyền thống, (2) `FileChannel` + `ByteBuffer` của NIO 1, (3) `Files.readAllBytes` của NIO.2 Java 7 và (4) `Files.readString` Java 11. Tệp và thư mục tạm được xóa trong `finally`.

Lưu file thành **`IoApiTimelineDemo.java`**:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoApiTimelineDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("io-api-demo-");
        Path file = dir.resolve("example.txt");
        try {
            try (OutputStream out = new FileOutputStream(file.toFile())) {
                out.write("Hello IO!\n".getBytes(StandardCharsets.UTF_8));
            }

            ByteArrayOutputStream legacy = new ByteArrayOutputStream();
            try (InputStream in = new FileInputStream(file.toFile())) {
                byte[] bytes = new byte[4];
                int count;
                while ((count = in.read(bytes)) != -1) {
                    legacy.write(bytes, 0, count);
                }
            }
            System.out.println("java.io: "
                    + new String(legacy.toByteArray(), StandardCharsets.UTF_8).trim());

            ByteArrayOutputStream nio = new ByteArrayOutputStream();
            try (FileInputStream in = new FileInputStream(file.toFile());
                 FileChannel channel = in.getChannel()) {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        nio.write(buffer.get());
                    }
                    buffer.clear();
                }
            }
            System.out.println("NIO 1.4: "
                    + new String(nio.toByteArray(), StandardCharsets.UTF_8).trim());

            System.out.println("NIO.2/JDK 7: "
                    + new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim());
            System.out.println("Java 11+: "
                    + Files.readString(file, StandardCharsets.UTF_8).trim());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac IoApiTimelineDemo.java
java IoApiTimelineDemo
~~~

Stdout:

~~~text
java.io: Hello IO!
NIO 1.4: Hello IO!
NIO.2/JDK 7: Hello IO!
Java 11+: Hello IO!
~~~

Cùng một dữ liệu được đọc qua các API thuộc các mốc khác nhau. Chương trình **chạy trên Java 17+**: bản thân file nguồn không có nghĩa nó chạy nguyên vẹn trên Java 7, vì phương thức `Files.readString` ở ví dụ cuối đòi Java 11. Phần demo NIO tự dùng buffer bốn byte để thấy cách xử lý nhiều lần `read`; không cần buffer khi chỉ muốn đọc văn bản nhỏ bằng `Files.readString`.

## 7. Chọn API nào trong từng tình huống?

| Tình huống | Ưu tiên | Lý do/quy tắc triển khai |
|---|---|---|
| Đọc một file cấu hình nhỏ dạng UTF-8 trên Java 11+ | `Files.readString(path, UTF_8)` | Ngắn, rõ charset; chỉ dùng khi kích thước đủ nhỏ để nạp vào bộ nhớ |
| Ghi một file văn bản nhỏ trên Java 11+ | `Files.writeString(path, text, UTF_8)` | Dễ dùng; kiểm tra `OpenOption` nếu muốn append hoặc tránh ghi đè |
| Xử lý file văn bản lớn theo dòng | `Files.newBufferedReader` hoặc `Files.lines` | Đọc tuần tự; đóng reader/stream bằng `try-with-resources` |
| Sao chép, di chuyển, tạo thư mục | `Files.copy`, `Files.move`, `Files.createDirectories` | Lỗi có ngoại lệ cụ thể; xem xét replace, atomicity, symlink |
| Quét cây thư mục lớn/điều khiển lỗi từng nhánh | `Files.walkFileTree` + `FileVisitor` | Chủ động xử lý lỗi, rẽ nhánh và thời điểm thăm file |
| Theo dõi thay đổi trong thư mục | `WatchService` | Nhận sự kiện, xử lý `OVERFLOW` và đăng ký lại khi cần; không coi là log tuyệt đối chính xác |
| Đọc file ở vị trí xác định hoặc map file | `FileChannel` / `SeekableByteChannel` | Có thao tác position và truy cập byte phù hợp |
| Nhiều kết nối socket trên ít thread | `SocketChannel` + `Selector` | Cần cấu hình non-blocking và tự quản lý tình trạng đọc/ghi chưa xong |
| Muốn dùng callbacks cho I/O bất đồng bộ | `AsynchronousFileChannel` / `AsynchronousSocketChannel` | Tận dụng `Future`/`CompletionHandler`; kiểm tra hỗ trợ nền tảng |
| Ứng dụng phụ thuộc thư viện cũ nhận `InputStream`/`Reader` | Giữ `java.io` interface; tạo từ `Files` nếu thuận tiện | Không cần đổi chữ ký API chỉ để “chuyển sang NIO.2” |

**Bộ quy tắc lựa chọn:** dùng `Path`/`Files` cho phần lớn tác vụ *hệ thống tệp* mới; dùng `InputStream`/`OutputStream` hoặc `Reader`/`Writer` khi cần *luồng*; dùng channel và buffer khi cần vị trí byte hoặc đặc tính mạng/I/O cụ thể. “Mới hơn” không đồng nghĩa “nhanh hơn”, và việc nâng JDK không buộc xóa code `java.io` hợp lệ.

## 8. Các bẫy tương thích và hành vi thường gặp

1. **Đường dẫn khác với nội dung:** `new File(path)` và `Path.of(path)` không tạo tệp thật. `Files.exists(path)` còn có thể trả `false` khi không xác định được trạng thái tồn tại; đừng mặc định `false` luôn nghĩa là chắc chắn không có file. Các thao tác mở/đọc vẫn phải xử lý `IOException`.

2. **Charset:** `FileInputStream` chỉ tạo byte stream, không tự “đọc UTF-8”. Khi biến bytes thành text, nêu rõ `StandardCharsets.UTF_8`. NIO `Charset` là bộ mã hóa/giải mã; mặc định charset có thể khác giữa JDK/cấu hình chạy. `Files.readString`/`writeString` không truyền charset sử dụng UTF-8.

3. **Tài nguyên:** đóng stream, reader, channel, `DirectoryStream` và stream do `Files.list`/`walk`/`lines` trả về. `Files.readAllBytes`/`readString` tự đóng tệp sau thao tác; đổi lại toàn bộ dữ liệu được nạp vào bộ nhớ.

4. **Tùy chọn copy/move:** `Files.copy`, `Files.move` có quy tắc mặc định và `CopyOption`; `REPLACE_EXISTING` là yêu cầu cho hành vi thay thế. `ATOMIC_MOVE` chỉ là yêu cầu di chuyển nguyên tử và có thể thất bại với `AtomicMoveNotSupportedException`, nhất là khi khác file system. Đừng khẳng định các bước copy/move hoàn toàn transaction-safe.

5. **Symbolic link và thuộc tính tệp:** nhiều thao tác `Files` đi theo liên kết tượng trưng theo mặc định, nhưng một số API hoặc option có quy tắc khác. Dùng `LinkOption.NOFOLLOW_LINKS` khi API hỗ trợ và ngữ nghĩa yêu cầu. Quyền truy cập POSIX/ACL phụ thuộc provider/hệ điều hành; không áp quy tắc POSIX cho mọi ổ đĩa Windows.

6. **WatchService:** sự kiện có thể gộp, mất do overflow hoặc xảy ra khi writer vẫn đang cập nhật. Thường phải kiểm tra lại trạng thái thư mục khi nhận `OVERFLOW`. Không kỳ vọng WatchService tự đệ quy xuống mọi thư mục con.

7. **Non-blocking không phải asynchronous:** `SocketChannel.configureBlocking(false)` yêu cầu ứng dụng tự xử lý partial read/write và `Selector`; `AsynchronousSocketChannel` đưa kết quả qua `Future`/`CompletionHandler`. Kết quả bất đồng bộ không mặc nhiên chứng minh một lời gọi kernel non-blocking trên mọi hệ điều hành.

8. **Không lấy kích thước file để gọi `read` đúng một lần:** cả stream và channel có thể trả ít byte hơn số yêu cầu. Khi cần đủ byte, lặp có kiểm tra số byte đọc/EOF. Ghi qua channel cũng có thể chỉ tiêu thụ một phần buffer. Nếu xử lý mạng phân mảnh, dùng `compact()` để giữ byte chưa giải mã.

9. **`File` và `Path` không có quan hệ kế thừa:** chuyển qua `file.toPath()` hoặc `path.toFile()` (với default file-system provider). Các provider khác như ZIP file system có thể không hỗ trợ `toFile()`.

10. **Khi cần build cho JDK cũ:** phân biệt API tồn tại trên **JDK biên dịch** và API tồn tại trên **JDK chạy**; xác minh mốc *phương thức*, không chỉ mốc package. Không gọi `InputStream.readAllBytes` khi target Java 8, dù chính `InputStream` đã tồn tại lâu.

## 9. Tóm tắt để tra cứu nhanh

| Nếu nhìn thấy mã… | Nhận diện đúng |
|---|---|
| `new FileInputStream(file)`, `BufferedReader`, `OutputStream` | Truyền thống `java.io`; vẫn dùng tốt trong JDK mới |
| `ByteBuffer.flip()`, `FileChannel`, `Selector` | NIO từ Java 1.4; buffer/channel, chỉ một số channel non-blocking |
| `Path`, `Files.copy`, `Files.walkFileTree`, `WatchService` | NIO.2 từ Java 7; hệ thống tệp và API bổ sung |
| `Files.lines`, `Files.walk` | API convenience dựa trên Stream được thêm ở Java 8 |
| `InputStream.transferTo` | Tiện ích `java.io` bổ sung ở Java 9 |
| `Files.readString`, `Path.of` | Tiện ích `java.nio.file` bổ sung ở Java 11 |
| `Files.mismatch` | Bổ sung ở Java 12 |

**Nguyên tắc mấu chốt:** tra **lớp + phương thức + phiên bản tối thiểu**; chọn theo bài toán (stream, buffer/channel, hay filesystem). Java 7 là mốc NIO.2, nhưng các API trong `java.nio.file` tiếp tục được bổ sung ở Java 8/11/12.

### Tài liệu API chính thức

- [`java.io` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/package-summary.html)
- [`java.nio` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/package-summary.html)
- [`java.nio.channels` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/package-summary.html)
- [`java.nio.file` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/package-summary.html)
- [`java.nio.file` — Java SE 7](https://docs.oracle.com/javase/7/docs/api/java/nio/file/package-summary.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reference-tables-008">Tra cứu Javadoc, Serialization Specification và khả năng của provider</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu Javadoc, Serialization Specification và khả năng của provider trong Java I/O

## 1. Mục tiêu và cách chọn nguồn tài liệu

Khi sử dụng Java I/O, câu hỏi quan trọng không chỉ là “gọi phương thức nào?” mà còn là “Java bảo đảm hành vi nào, hành vi nào tùy hệ điều hành hoặc provider, và lỗi có thể xảy ra ở đâu?”. Ba nguồn thông tin giải quyết ba lớp câu hỏi khác nhau:

| Nguồn | Trả lời điều gì? | Ví dụ câu hỏi |
| --- | --- | --- |
| **Javadoc của Java SE** | Chữ ký API, ngữ nghĩa, tham số, giá trị trả về, ngoại lệ, bảo đảm và giới hạn của từng lớp/phương thức. | `Files.move` có bảo đảm atomic không? `InputStream.read` trả về gì khi hết dữ liệu? |
| **Java Object Serialization Specification** | Quy tắc chuẩn của cơ chế tuần tự hóa đối tượng: đồ thị đối tượng, trạng thái được lưu, định dạng luồng, phục hồi, tương thích phiên bản, hook và bảo mật. | `transient` được xử lý thế nào? Khi đổi trường hoặc `serialVersionUID` thì việc đọc dữ liệu cũ ra sao? |
| **Tài liệu và khả năng của provider đang chạy** | Những gì một triển khai thực tế hỗ trợ, như file attribute view, symbolic link, atomic move, ZIP filesystem hoặc tùy chọn mở tệp. | Provider của `Path` này có hỗ trợ POSIX permissions không? |

**Nguyên tắc:** Javadoc và specification là hợp đồng để lập luận; phát hiện capability tại runtime giúp chọn nhánh xử lý phù hợp. Một tính năng “optional” trong Javadoc không trở thành tính năng luôn có chỉ vì hoạt động trên máy phát triển.

Cần chốt **phiên bản JDK mục tiêu** (ví dụ Java 17 hoặc 21) trước khi tra cứu. Chọn đúng tài liệu Java SE cho phiên bản đó; API mới hơn có thể chưa tồn tại ở JDK đang chạy. Bài này minh họa bằng Java 17+ và liên kết tài liệu Java SE 21 để tra cứu.

## 2. Đọc Javadoc hiệu quả

### 2.1. Xác định đúng lớp, package, module và overload

Bắt đầu từ lớp sở hữu thao tác:

- Byte stream: `java.io.InputStream`, `OutputStream`, `BufferedInputStream`, `DataInputStream`.
- Character stream và charset: `Reader`, `Writer`, `InputStreamReader`, `OutputStreamWriter`, `java.nio.charset.Charset`.
- Filesystem/NIO.2: `java.nio.file.Path`, `Paths`, `Files`, `FileSystem`, `FileStore`, `FileSystems`.
- Object serialization: `Serializable`, `Externalizable`, `ObjectOutputStream`, `ObjectInputStream`, `ObjectStreamClass`.

Mỗi trang Javadoc chỉ rõ **module**, **package**, `Since`, thông tin kế thừa và các overload. `Files.copy(Path, Path, CopyOption...)` có hợp đồng khác `Files.copy(InputStream, Path, CopyOption...)`; không kết luận theo tên phương thức đơn thuần.

### 2.2. Đọc phần mô tả theo thứ tự

1. Đọc mô tả cấp lớp trước: tài nguyên, thread-safety, đặc điểm hoặc điều kiện của implementor.
2. Chọn đúng signature/overload: kiểu tham số, varargs, generic, return type và `throws`.
3. Đọc toàn bộ mô tả chi tiết: mặc định khi không truyền option, chính sách xử lý symbolic link, trạng thái khi thao tác thất bại.
4. Đọc `Parameters`, `Returns`, `Throws`, `Implementation Requirements/Notes`, `API Note` và `See Also` nếu có.
5. Ghi ra: **điều được bảo đảm**, **điều tùy triển khai**, **thao tác optional**, **ngoại lệ cần xử lý**, và **cách đóng tài nguyên**.

Ví dụ ở `InputStream.read()`: kết quả là một `int` biểu diễn byte hợp lệ từ 0 đến 255, hoặc `-1` khi kết thúc luồng; không ép ngay về `byte` trước khi kiểm tra `-1`. Một lời gọi `read(byte[])` cũng không có nghĩa là luôn lấp đầy cả mảng; phải xem số byte thực đọc được.

Ví dụ ở `Files.newOutputStream(path)`: nếu không chỉ định option, mặc định tương đương `CREATE`, `TRUNCATE_EXISTING` và `WRITE`. Vì thế “mở để ghi” có thể xóa nội dung cũ. Nếu cần không ghi đè, tra `CREATE_NEW` và xử lý `FileAlreadyExistsException`; không chọn option bằng suy đoán.

### 2.3. “Có API” khác “mọi triển khai đều hỗ trợ”

Trong Javadoc, các cụm như “optional operation”, “implementation specific”, “if supported” và “not specified” là một phần quan trọng của hợp đồng.

- `Files.move(source, target, ATOMIC_MOVE)`: yêu cầu di chuyển nguyên tử. Nếu không thể đáp ứng, `AtomicMoveNotSupportedException` có thể được ném; không nên âm thầm coi di chuyển thông thường cũng atomic. Khi có `ATOMIC_MOVE`, cách xử lý mục tiêu đã tồn tại là tùy triển khai và các option khác bị bỏ qua.
- `Files.copy(..., COPY_ATTRIBUTES)`: các thuộc tính nào được sao chép phụ thuộc filesystem/provider; thời gian sửa đổi cuối cùng là thuộc tính tối thiểu được cố gắng sao chép nếu cả hai bên hỗ trợ, và có thể mất độ chính xác.
- `Files.createSymbolicLink` và `FileSystem.newWatchService` là những chức năng có thể không được hỗ trợ.
- `Files.getFileAttributeView(path, SomeView.class)` có thể trả về `null` khi view không khả dụng; các thao tác đọc thuộc tính tương ứng cũng có thể ném `UnsupportedOperationException`.

Cũng cần phân biệt kết quả kiểm tra: `Files.exists(path) == false` **không khẳng định** rằng tệp chắc chắn không tồn tại; API có thể không xác định được do lỗi quyền truy cập hoặc I/O. Tương tự `Files.notExists(path) == false` không tự chứng minh tệp tồn tại.

## 3. Tra cứu đúng Java Object Serialization Specification

Object serialization trong Java là cơ chế chuyển **đồ thị đối tượng** thành luồng để ghi/trao đổi và phục hồi theo quy tắc riêng. Nó không đồng nghĩa với đọc/ghi byte thông thường bằng `Files` hoặc với JSON. Những quy tắc không thể suy ra đầy đủ từ một phương thức `ObjectOutputStream.writeObject` được đặc tả trong **Java Object Serialization Specification**.

### 3.1. Bản đồ những phần thường cần đọc

| Câu hỏi | Phần cần mở trong Serialization Specification |
| --- | --- |
| Trường nào được lưu, object graph, reference chia sẻ và chu trình? | Chương 1 — System Architecture, đặc biệt 1.2–1.7 và 1.14 |
| `Serializable` khác `Externalizable` thế nào? | Mục 1.10–1.11, 2.4 và 3.6 |
| Tự điều chỉnh ghi/đọc đối tượng bằng `writeObject`/`readObject`? | Chương 2–3, đặc biệt 2.3 và 3.4 |
| `serialVersionUID`, mô tả lớp, serial form? | Chương 4, đặc biệt 4.3 và 4.6 |
| Đổi tên/xóa/thêm trường, đổi kiểu, tương thích dữ liệu đã lưu? | Chương 5 — Versioning of Serializable Objects |
| Dữ liệu nhị phân của stream và phiên bản giao thức? | Chương 6 — Object Serialization Stream Protocol |
| Bề mặt tấn công và lỗi deserialization? | Phụ lục A/B và Javadoc `ObjectInputFilter` |

Nên đọc đồng thời Javadoc của `Serializable`, `ObjectInputStream`, `ObjectOutputStream` và phần specification có liên quan. Specification quy định cơ chế; Javadoc cung cấp cách gọi API cùng exception cụ thể.

### 3.2. Các quy tắc cốt lõi cần nắm

- Một lớp khai báo `implements Serializable` để tham gia cơ chế serialization mặc định. Marker interface này **không có phương thức** cần implement.
- Mặc định, các trường instance đủ điều kiện được đưa vào serial form; `static` không biểu diễn trạng thái riêng từng instance và `transient` không được lưu theo cơ chế mặc định. Giá trị của trường `transient` sau phục hồi là giá trị mặc định của kiểu, trừ khi có logic khôi phục khác.
- Một trường tham chiếu đến đối tượng không serializable có thể khiến quá trình ghi ném `NotSerializableException` nếu đối tượng đó thực sự được duyệt trong đồ thị serialization.
- `ObjectOutputStream` ghi mô tả lớp và dữ liệu đối tượng; `ObjectInputStream` đọc theo protocol, tái dựng reference chia sẻ/chu trình. Không giả định constructor của lớp `Serializable` được gọi như tạo đối tượng mới; constructor không tham số có thể truy cập của lớp cha đầu tiên **không Serializable** được gọi khi phục hồi.
- Khai báo `private static final long serialVersionUID = ...L;` cho lớp serializable thông thường giúp kiểm soát bộ định danh phiên bản. UID khác nhau có thể gây `InvalidClassException`; **UID giống nhau không chứng minh mọi thay đổi trong lớp đều tương thích**. Phải xem quy tắc tiến hóa lớp ở Chương 5.
- `private writeObject(ObjectOutputStream)`/`private readObject(ObjectInputStream)`, `writeReplace`/`readResolve`, `serialPersistentFields` và `Externalizable` thay đổi giao thức/serial form; cần đọc chính xác điều kiện khai báo và thứ tự xử lý trong đặc tả. `Externalizable` chịu trách nhiệm ghi/đọc dữ liệu của chính nó và cần constructor `public` không tham số cho việc phục hồi.
- Enum có quy tắc serialization riêng; record serializable cũng có quy tắc riêng. Không suy diễn mọi hook của lớp thông thường sang enum/record.
- Java serialization không phải định dạng trao đổi tổng quát lý tưởng cho API lâu dài. Nếu cần hợp đồng đa ngôn ngữ hoặc schema tiến hóa độc lập, hãy cân nhắc một định dạng được xác định cho mục đích đó.

### 3.3. Ví dụ Java 17+ có thể biên dịch: `transient` và serial form

Lưu mã sau vào tệp `SerializationProbe.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class SerializationProbe {
    private static final class Profile implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String name;
        private transient String sessionToken;

        private Profile(String name, String sessionToken) {
            this.name = name;
            this.sessionToken = sessionToken;
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] saved;

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Profile("Lan", "temporary-token"));
            out.flush();
            saved = bytes.toByteArray();
        }

        try (ObjectInputStream in =
                     new ObjectInputStream(new ByteArrayInputStream(saved))) {
            Profile restored = (Profile) in.readObject();
            System.out.println(restored.name);
            System.out.println(restored.sessionToken == null);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SerializationProbe.java
java SerializationProbe
```

Kết quả:

```text
Lan
true
```

`name` được lưu; `sessionToken` bị đánh dấu `transient` nên giá trị khôi phục bằng `null`. Ví dụ dùng mảng byte do chính chương trình tạo ra, **không đọc stream không tin cậy**. `@Serial` giúp trình biên dịch kiểm tra một số khai báo phục vụ serialization, nhưng không thay đổi yêu cầu bảo mật hay làm dữ liệu tương thích tự động.

### 3.4. Bảo mật khi deserialization

Đừng dùng `ObjectInputStream.readObject()` với dữ liệu do bên không tin cậy kiểm soát chỉ vì đã có `serialVersionUID`, `transient` hoặc kiểm tra kiểu sau khi đọc. Quá trình dựng đồ thị đối tượng diễn ra trước khi ép kiểu và có thể kích hoạt logic của các lớp trên classpath.

Nếu bắt buộc dùng Java native serialization cho một nguồn dữ liệu được kiểm soát, hãy áp dụng chính sách cho phép kiểu rõ ràng và giới hạn kích thước/độ sâu/độ phức tạp bằng `ObjectInputFilter` (bao gồm filter cho từng stream), đồng thời xác thực nguồn và quản lý phiên bản. Javadoc cảnh báo rằng deserialization dữ liệu không tin cậy là nguy hiểm ngay cả khi có filtering; ưu tiên tránh cơ chế này ở ranh giới không tin cậy.

## 4. Provider trong Java NIO.2: ai thực hiện thao tác trên `Path`?

Một `Path` thuộc về **một `FileSystem` cụ thể**; `FileSystem` đó do một `FileSystemProvider` cung cấp. Lớp tiện ích `Files` thường ủy nhiệm hoạt động cho provider tương ứng của `Path`. Vì vậy cùng một lời gọi `Files` có thể có giới hạn khác nhau trên filesystem mặc định, ZIP filesystem hoặc một implementation bên thứ ba.

Quan hệ tra cứu:

```text
Path ──getFileSystem()──> FileSystem ──provider()──> FileSystemProvider
  │                         │                              │
  └── Files.* thao tác ──────┴── triển khai thao tác <─────┘
```

Provider mặc định thường có scheme `file`. Provider khác có scheme riêng (ví dụ `jar` cho ZIP/JAR filesystem khi module/provider tương ứng có sẵn). Sự hiện diện của một provider phải được kiểm chứng trên JVM đang chạy, không thể suy ra chỉ từ extension `.zip` hoặc từ sự hiện diện của một lớp API.

### 4.1. API kiểm tra khả năng ở từng cấp

- `FileSystemProvider.installedProviders()`: liệt kê các provider được cài đặt mà API tìm thấy; `provider.getScheme()` xác định URI scheme.
- `path.getFileSystem().provider()`: biết provider thực sự sở hữu `Path`; `FileSystems.getDefault()` chỉ đại diện filesystem mặc định.
- `fileSystem.supportedFileAttributeViews()`: tập tên các view mà **filesystem** hỗ trợ, ví dụ `basic`, `posix`, `dos`, `acl`, `user` (tập thực tế tùy provider).
- `Files.getFileStore(path).supportsFileAttributeView(...)`: kiểm tra view tại **file store** chứa đường dẫn; chuẩn `basic` luôn được hỗ trợ. Kết quả trên một số lưu trữ không cục bộ vẫn có thể không chính xác hoàn toàn.
- `Files.getFileAttributeView(path, PosixFileAttributeView.class)`: trả về đối tượng view nếu có, hoặc `null` nếu không hỗ trợ kiểu view đó.
- Các chức năng optional khác (atomic move, symbolic/hard link, file locking, watch service, option riêng của provider) phải được tra trong **Javadoc của thao tác tương ứng** và kiểm tra bằng thao tác thật có xử lý ngoại lệ. Không có một hàm `isEverythingSupported()` tổng quát.

Lưu ý: **tên view hỗ trợ không nói lên quyền thực hiện thao tác**. Dù hệ thống hỗ trợ POSIX view, việc thay đổi permission vẫn có thể thất bại do quyền người dùng hoặc trạng thái read-only. Hơn nữa, `FileStore` chứa `Path` cụ thể có thể khác capability tổng quát của `FileSystem`.

### 4.2. Ví dụ Java 17+ chạy được: tìm provider và file attribute view

Lưu `ProviderProbe.java`:

```java
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.spi.FileSystemProvider;

public class ProviderProbe {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(".").toAbsolutePath().normalize();
        FileSystem fs = path.getFileSystem();
        FileStore store = Files.getFileStore(path);

        System.out.println("Provider của Path: "
                + fs.provider().getScheme());
        System.out.println("Attribute views của FileSystem: "
                + fs.supportedFileAttributeViews());
        System.out.println("FileStore hỗ trợ POSIX: "
                + store.supportsFileAttributeView(PosixFileAttributeView.class));
        System.out.println("POSIX view tại Path khả dụng: "
                + (Files.getFileAttributeView(
                        path, PosixFileAttributeView.class) != null));

        System.out.println("Các provider được cài đặt:");
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            System.out.println("  " + provider.getScheme());
        }
    }
}
```

Chạy:

```bash
javac ProviderProbe.java
java ProviderProbe
```

Kết quả sẽ **khác nhau giữa Windows, Linux, macOS, filesystem và JDK**. Với đường dẫn thuộc filesystem mặc định, scheme thường là `file`; view `posix` thường có trên hệ thống POSIX nhưng không nên giả định luôn có. Danh sách có thể có provider `jar` khi ZIP filesystem provider sẵn có. Không lấy đầu ra của một máy làm bảo đảm di động cho mọi máy.

### 4.3. Quyết định theo capability và dự phòng có chủ đích

Giả sử muốn di chuyển một tệp kết quả sang vị trí đích:

```java
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MoveExample {
    public static void moveWithFallback(Path source, Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
```

Đây là **đoạn minh họa phương thức**, không phải chương trình độc lập có `main`. Nhánh fallback chấp nhận ngữ nghĩa **không atomic**; dùng nó **chỉ khi nghiệp vụ cho phép**. Nếu cần công bố tệp mà độc giả tuyệt đối không được thấy trạng thái trung gian, không thể coi fallback này là tương đương. Khi `ATOMIC_MOVE` được yêu cầu, xử lý đích đã tồn tại có thể khác nhau theo implementation, nên phải thiết kế riêng quy tắc ghi đè. `IOException` khác vẫn cần được đưa lên hoặc xử lý; không bắt mọi lỗi rồi đoán “provider không hỗ trợ”.

Với permission POSIX, có thể chọn nhánh chỉ khi `Files.getFileAttributeView(path, PosixFileAttributeView.class) != null`, rồi vẫn xử lý `IOException` hoặc lỗi quyền khi thực thi. Nếu cần chức năng bắt buộc mà provider không hỗ trợ, giải pháp đúng có thể là **từ chối thao tác và thông báo rõ giới hạn**, thay vì mô phỏng sai bảo đảm.

### 4.4. Khi nào cần đọc tài liệu riêng của provider?

Hãy đọc tài liệu triển khai khi ứng dụng sử dụng ZIP/JAR filesystem, filesystem từ thư viện, filesystem mạng/cloud, hoặc open option và metadata mở rộng. Cần xác minh:

1. URI scheme và cách khởi tạo `FileSystem` (qua `FileSystems.newFileSystem`, map cấu hình, lifecycle `close()`).
2. Quy tắc đường dẫn, phân biệt chữ hoa/thường, separator, quyền truy cập và trạng thái read-only.
3. File attribute views, symbolic link/hard link, atomic move, watch service và đặc điểm nhất quán.
4. Giới hạn kích thước, thao tác đa luồng, xử lý lỗi, thời gian chặn I/O và hành vi khi đóng tài nguyên.

Javadoc chuẩn mô tả giao diện chung; không tự gán tính năng của provider mặc định cho `Path` thuộc provider khác. Khi có nhiều nguồn `Path`, phải truy xuất provider **từ từng `Path`** trước khi quyết định.

## 5. Phân loại lỗi đúng khi tra cứu

| Hiện tượng hoặc exception | Điểm cần kiểm chứng | Hướng xử lý |
| --- | --- | --- |
| `NotSerializableException` | Thành phần nào trong object graph không đáp ứng quy tắc serialization? | Xem kiểu trường và đường tham chiếu; thiết kế DTO/serial form phù hợp. |
| `InvalidClassException` | UID, mô tả lớp và thay đổi serial form có tương thích Chương 5 không? | Quản lý phiên bản, migration hoặc format riêng; không sửa UID tùy tiện để “qua lỗi”. |
| `StreamCorruptedException` | Luồng có đúng object stream protocol không, có bị cắt/hỏng không? | Xác minh nguồn, framing và cách ghi; không coi file thường là object stream. |
| `ProviderNotFoundException` | Provider cho scheme/file type có được cài đặt và discover không? | Kiểm tra `FileSystemProvider.installedProviders()`, classpath/module và cách mở. |
| `UnsupportedOperationException` | API/option/view có được đánh dấu optional hoặc không được hỗ trợ không? | Đọc đúng overload và capability, thay thế hợp lệ hoặc báo không hỗ trợ. |
| `AtomicMoveNotSupportedException` | `ATOMIC_MOVE` có được filesystem/store đích hỗ trợ không? | Chỉ fallback khi chấp nhận mất tính atomic. |
| `AccessDeniedException` / `IOException` | Quyền, path thực tế, thiết bị, kết nối, store hoặc giới hạn implementation? | Ghi lại ngữ cảnh an toàn, xử lý lỗi I/O cụ thể; không nhầm mọi `IOException` thành thiếu capability. |

Khi cần phân biệt lỗi, ưu tiên ngoại lệ được Javadoc tuyên bố; đừng phân tích chuỗi thông báo lỗi theo từng hệ điều hành để quyết định nhánh logic.

## 6. Quy trình tra cứu từ câu hỏi tới quyết định kỹ thuật

**Tình huống A — “Đọc file Unicode đúng trên mọi máy”:**

1. Xác định bài toán là text: tìm `Files.newBufferedReader(Path, Charset)` hoặc `Files.readString(Path, Charset)`.
2. Đọc Javadoc để biết charset, ngoại lệ và cách đóng reader; truyền rõ `StandardCharsets.UTF_8` thay vì ngầm dựa vào default charset ở code cũ.
3. Nếu cần streaming file lớn, ưu tiên `BufferedReader` hoặc stream và `try-with-resources`; đừng dùng `readAllBytes` chỉ vì code ngắn hơn.
4. Phân biệt lỗi encoding với lỗi do provider, permission và path.

**Tình huống B — “Lưu đối tượng rồi đọc lại sau khi deploy bản mới”:**

1. Tra Javadoc `Serializable`, `ObjectOutputStream`, `ObjectInputStream` để xác định API.
2. Đọc Serialization Specification Chương 4–5 và serial form của từng lớp để đánh giá thay đổi.
3. Xác định dữ liệu đầu vào có đáng tin không; nếu không, tránh Java deserialization. Nếu thật sự cần, thiết kế lọc lớp và giới hạn tài nguyên.
4. Tạo test tương thích thực sự: ghi dữ liệu bằng phiên bản cũ, đọc bằng phiên bản mới (và chiều ngược lại nếu sản phẩm cần). Test một vòng serialize–deserialize trong cùng phiên bản không kiểm chứng tương thích nâng cấp.

**Tình huống C — “Copy/move tệp qua ổ đĩa, ZIP hoặc filesystem mạng”:**

1. Tra Javadoc `Files.copy`/`Files.move` và option cần dùng; đặc biệt atomic, replacement, link và attributes.
2. Từ mỗi `Path` tìm `FileSystem`/provider, dùng `FileStore` và attribute views để xác định những khả năng có thể kiểm tra trước.
3. Thử hành vi bắt buộc trong môi trường đích, xử lý các exception đặc thù và xác nhận tính toàn vẹn sau thao tác.
4. Không coi “chạy được trên máy mình” là chứng cứ provider khác cũng làm giống hệt.

Một ghi chép tra cứu hữu ích cho mỗi quyết định gồm: **JDK/version → lớp & overload → bảo đảm Javadoc → câu trong specification (nếu có) → provider/capability → trường hợp lỗi → kết quả thử nghiệm trên môi trường đích**. Cách này giúp reviewer lần lại lập luận mà không phải đoán nguồn.

## 7. Lỗi học tập thường gặp và cách tự kiểm tra

- **Chỉ xem tên phương thức:** bỏ qua `Throws`, tùy chọn mặc định và ngữ nghĩa partial read/write. Cách sửa: đọc mô tả của đúng overload tới hết.
- **Nhầm Javadoc với Serialization Specification:** tin rằng tất cả quy tắc tương thích có ở `writeObject`. Cách sửa: đối chiếu Chương 4–5 specification.
- **Cho rằng `Serializable` tự bảo đảm an toàn, tương thích:** đây là hai vấn đề riêng. Cách sửa: đánh giá object graph, serial form/versioning và ranh giới tin cậy.
- **Coi `serialVersionUID` là phiên bản schema hoàn chỉnh:** UID chỉ tham gia kiểm tra tương thích, không thay thế kiểm tra thay đổi trường/hook.
- **Cho rằng Linux/Windows và mọi `Path` giống nhau:** xem capability ở `FileSystem`/`FileStore`/provider thực tế.
- **Coi `supportsFileAttributeView("posix")` là chắc chắn có quyền `setPermissions`:** khả năng API không phải quyền thực hiện thao tác.
- **Coi atomic move và fallback là tương đương:** fallback thay đổi bảo đảm khi xảy ra lỗi hoặc có độc giả đồng thời.
- **Chỉ đọc ví dụ blog trên JDK khác:** cách sửa là mở đúng Javadoc/specification của phiên bản mục tiêu, rồi dùng ví dụ để kiểm chứng.

Tự kiểm tra: (1) giải thích vì sao `read(byte[])` phải xử lý số byte đã đọc; (2) nêu vì sao cùng `serialVersionUID` vẫn có thể mất tương thích; (3) phân biệt `FileSystem.supportedFileAttributeViews()`, `FileStore.supportsFileAttributeView(...)` và `Files.getFileAttributeView(...)`; (4) giải thích khi nào được phép fallback từ `ATOMIC_MOVE`.

## 8. Nguồn chính thức để tiếp tục tra cứu

- [Java SE 21 API Javadoc — tổng quan](https://docs.oracle.com/en/java/javase/21/docs/api/index.html)
- [`Files` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)
- [`FileSystem` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystem.html)
- [`FileStore` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileStore.html)
- [`FileSystems` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystems.html)
- [`FileSystemProvider` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html)
- [`Serializable` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/Serializable.html)
- [`ObjectInputFilter` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html)
- [Java Object Serialization Specification — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/)

Nếu hệ thống chạy Java 17, hãy mở cây tài liệu `/en/java/javase/17/...` cho chính các lớp và specification tương ứng. Không thay đổi kết luận hợp đồng API dựa trên bài viết mô tả một JDK khác.


</details>

- [Quay lại đầu trang](#back-to-top)
