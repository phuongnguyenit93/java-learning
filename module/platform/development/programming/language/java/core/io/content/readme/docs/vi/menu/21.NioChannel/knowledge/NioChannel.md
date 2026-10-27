<a id="back-to-top"></a>

# NIO Channel

## Menu
- [Channel khác InputStream và OutputStream](#nio-channel-001)
- [ReadableByteChannel, WritableByteChannel và ByteChannel](#nio-channel-002)
- [SeekableByteChannel và khả năng thay đổi vị trí](#nio-channel-003)
- [Đọc hoặc ghi không đủ toàn bộ buffer](#nio-channel-004)
- [Ý nghĩa của kết quả 0 và EOF -1](#nio-channel-005)
- [Vòng lặp đọc ghi dựa trên position và remaining](#nio-channel-006)
- [Scattering read và gathering write](#nio-channel-007)
- [Channels.newChannel, newInputStream và newOutputStream](#nio-channel-008)
- [Channels.newReader, newWriter và chuyển đổi charset](#nio-channel-009)
- [Đóng channel, khả năng ngắt và tài nguyên bên dưới](#nio-channel-010)

## <a id="nio-channel-001">Channel khác InputStream và OutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# Channel khác InputStream và OutputStream như thế nào trong Java?

Trong Java, **`InputStream` / `OutputStream`** thuộc `java.io` và **Channel** thuộc `java.nio.channels` đều có thể vận chuyển **byte**, nhưng chúng tổ chức thao tác I/O theo những mô hình khác nhau. Hiểu khác biệt giúp chọn API đúng khi đọc tệp, gửi dữ liệu qua socket, xử lý tệp lớn hoặc cần truy cập những vị trí cụ thể trong file.

- **Stream** biểu diễn luồng byte được đọc từ một nguồn (`InputStream`) hoặc ghi đến một đích (`OutputStream`), với các phương thức xoay quanh `int` và `byte[]`.
- **Channel** biểu diễn một kết nối I/O có các khả năng được thể hiện qua interface: đọc, ghi, cả hai, seek, truyền trực tiếp, hoặc dùng cơ chế lựa chọn sự kiện nếu *đúng loại channel*. API đọc/ghi byte thông thường dùng **`ByteBuffer`**.
- **Không phải mọi Channel đều hai chiều, không phải mọi Channel đều non-blocking, và Stream không có nghĩa là luôn chậm.** Tính năng phụ thuộc loại channel/stream cụ thể, nơi lưu dữ liệu và cách sử dụng.

Một tệp văn bản nhỏ thường rất phù hợp với `Files.readString`, `BufferedReader` hoặc `InputStream`. Trong khi đó, đọc một phần tệp theo offset, ghi một record tại vị trí xác định, chuyển byte giữa hai channel hoặc lập trình mạng với `Selector` là những trường hợp NIO channel phát huy khả năng riêng.

## 1. Nền tảng: Stream là hướng byte, Channel là khả năng I/O

### `InputStream` và `OutputStream`

`InputStream` là lớp trừu tượng **đọc byte**; `OutputStream` là lớp trừu tượng **ghi byte**:

```java
int one = in.read();                   // 0..255 hoặc -1 tại EOF
int n = in.read(data, 0, data.length); // số byte thực đọc hoặc -1 tại EOF
out.write(data);                       // cố ghi toàn bộ mảng hoặc ném IOException
out.flush();                           // đẩy dữ liệu buffered nếu stream có buffer
```

Đây là **đoạn minh họa API**, giả định `in`, `out` và `data` đã được khởi tạo. Đừng coi một lần `read(byte[])` luôn lấp đầy mảng: nó có thể trả ít byte hơn số yêu cầu. Dù `OutputStream.write(byte[])` không trả số byte, việc gọi hàm có thể thất bại bằng `IOException`; không nên diễn giải đó là giao dịch ghi nguyên tử hoặc bảo đảm dữ liệu đã nằm trên đĩa.

Stream thường được ghép các lớp wrapper:

- `BufferedInputStream` / `BufferedOutputStream` thêm bộ đệm giúp giảm số lời gọi đến nguồn/đích nền.
- `DataInputStream` / `DataOutputStream` đọc/ghi giá trị kiểu nguyên thủy theo giao thức riêng; không biến stream thành một kho random access.
- `InputStreamReader` / `OutputStreamWriter` chuyển byte ↔ ký tự theo `Charset`. `BufferedReader` xử lý theo dòng sau khi đã giải mã ký tự.
- `FileInputStream` / `FileOutputStream` dùng tệp; `ByteArrayInputStream` / `ByteArrayOutputStream` dùng dữ liệu trong bộ nhớ; socket cũng có thể cung cấp stream.

Có thể dùng stream từ đầu đến cuối mà không cần tạo hay quản lý `ByteBuffer`. Khả năng đọc liên tục và bọc filter/decoder là ưu điểm quan trọng của mô hình này.

### Channel

Các interface nằm trong `java.nio.channels` phân biệt chức năng rõ hơn:

| Interface / lớp | Hợp đồng chính | Ví dụ |
| --- | --- | --- |
| `Channel` | Có trạng thái mở/đóng, `isOpen()` và `close()`; **chưa hề hứa có thể đọc/ghi**. | Nền tảng cho channel khác. |
| `ReadableByteChannel` | `read(ByteBuffer dst)` đọc byte vào buffer. | `FileChannel`, `SocketChannel`, `Pipe.SourceChannel`. |
| `WritableByteChannel` | `write(ByteBuffer src)` ghi byte còn lại của buffer. | `FileChannel`, `SocketChannel`, `Pipe.SinkChannel`. |
| `ByteChannel` | Kết hợp khả năng đọc và ghi; **không tự thêm phương thức mới**. | `FileChannel`, `SocketChannel`. |
| `SeekableByteChannel` | `position()`, `position(long)`, `size()`, `truncate(long)` cho thực thể có dãy byte thay đổi độ dài. | `FileChannel` hoặc channel trả bởi `Files.newByteChannel` nếu hỗ trợ. |
| `FileChannel` | Channel gắn với file: truy cập offset tuyệt đối, `map`, `transferTo/transferFrom`, `force`, file lock... | Đọc/ghi tệp, random access. |
| `SelectableChannel` | Có thể đăng ký `Selector`; có thể cấu hình blocking/non-blocking khi loại channel hỗ trợ. | `SocketChannel`, `ServerSocketChannel`, `DatagramChannel`. |
| `AsynchronousFileChannel` | API bất đồng bộ với `Future` hoặc callback, khác `FileChannel` đồng bộ thông thường. | Truy cập file bất đồng bộ. |

Không lấy tên “NIO” rồi suy ra tất cả channel đều dùng `Selector` hoặc non-blocking. `FileChannel` không phải `SelectableChannel` và không đăng ký trực tiếp với `Selector`. Ngược lại, một `SocketChannel` có thể cấu hình `configureBlocking(false)` và kết hợp `Selector`.

## 2. So sánh chính xác những khác biệt quan trọng

| Tiêu chí | `InputStream` / `OutputStream` | NIO Channel |
| --- | --- | --- |
| Đơn vị API | Byte đơn lẻ hoặc mảng `byte[]`. | Thường là `ByteBuffer`, một số channel hỗ trợ nhiều buffer. |
| Hướng dữ liệu | Hai lớp độc lập: đọc hoặc ghi. | Chức năng do interface quyết định: read, write, both hoặc khả năng khác. |
| Kết quả đọc | `read()` trả byte `0..255` / `-1`; `read(byte[])` trả số byte hoặc `-1`. | `read(ByteBuffer)` trả số byte hoặc `-1`; có thể trả `0` tùy buffer/trạng thái/blocking. |
| Kết quả ghi | `write(...)` trả `void` theo hợp đồng stream. | `write(ByteBuffer)` trả số byte thực ghi; có thể ghi ngắn hoặc bằng `0`. |
| Quản lý bộ đệm | `byte[]` và optional `Buffered*Stream`. | `ByteBuffer` có `position`, `limit`, `capacity`; có heap/direct buffer. |
| Di chuyển offset | `InputStream` không có phương thức tổng quát “đặt vị trí tệp bằng offset” (một số stream có `skip` hoặc `mark/reset` theo điều kiện). | Chỉ **seekable** channel có `position(long)`; `FileChannel` còn hỗ trợ read/write theo offset tuyệt đối. |
| Non-blocking/selector | API stream căn bản thường được dùng theo cách blocking; không có `register(selector)`. | Một số **selectable** channel hỗ trợ non-blocking/`Selector`; không áp dụng chung. |
| Đặc tính tệp | Có thể đọc/ghi file qua stream, một vài file stream có `getChannel()`. | `FileChannel` chuyên cho file mapping, transfer, lock, force, positional I/O. |
| Thao tác thuận tiện | Stream wrappers, `readNBytes`, `transferTo(OutputStream)`, `Reader/Writer`. | Positional I/O, scattering/gathering, file channel transfer, event-driven socket I/O. |
| Đóng tài nguyên | `Closeable` / `AutoCloseable`; khuyến nghị `try-with-resources`. | `Channel extends Closeable`, cũng khuyến nghị `try-with-resources`. |

Hai cột chỉ so sánh **hợp đồng API**, không quyết định hiệu năng trong mọi bài toán. `BufferedInputStream` đọc tệp nhỏ có thể nhanh và đơn giản hơn việc cấp phát rồi quản lý `ByteBuffer` bằng tay. `FileChannel.transferTo` có thể được OS tối ưu để giảm bản sao dữ liệu, nhưng vẫn có thể chuyển ít hơn số byte yêu cầu hoặc dùng đường fallback tùy môi trường.

## 3. Khác biệt về bộ đệm: `byte[]` so với `ByteBuffer`

Với stream, buffer thường là một mảng do caller truyền vào:

```java
byte[] block = new byte[4096];
int n = in.read(block);
if (n > 0) {
    // Chỉ block[0..n-1] là dữ liệu vừa đọc.
}
```

Đây là đoạn minh họa, biến `in` được khai báo bên ngoài. Sau khi đọc, `n` là dữ liệu có nghĩa trong mảng; API không lưu `position/limit` trên chính mảng.

Với channel, bộ đệm mang theo cả **trạng thái vị trí**:

```java
ByteBuffer buffer = ByteBuffer.allocate(4096);
int n = channel.read(buffer); // ghi vào buffer từ position đến limit
buffer.flip();                // chuyển sang chế độ lấy dữ liệu đã đọc
// ... đọc dữ liệu từ buffer bằng get() / decoder ...
buffer.clear();               // chuẩn bị vùng chứa cho lần read tiếp theo
```

Đây là đoạn minh họa, biến `channel` là một `ReadableByteChannel` đã mở. Giải thích ba thuộc tính:

- `capacity`: sức chứa cố định của buffer.
- `position`: chỉ số đọc/ghi tiếp theo.
- `limit`: ranh giới có thể đọc/ghi của thao tác hiện tại.

Ví dụ buffer dung lượng `8` được channel đọc `3` byte: sau đọc, `position=3`, `limit=8`; sau `flip()`, `position=0`, `limit=3`; gọi `get()` ba lần thì `position=3`. `clear()` không xóa bytes về 0 mà chỉ đặt lại trạng thái để nhận dữ liệu mới. Nếu còn byte chưa xử lý khi muốn nhận thêm dữ liệu, dùng `compact()` để đưa phần còn lại về đầu rồi tiếp tục nhận.

**Lỗi rất phổ biến:** gọi `StandardCharsets.UTF_8.decode(buffer)` ngay sau `channel.read(buffer)` mà quên `flip()`; decoder sẽ xem vùng từ `position` hiện tại đến `limit`, thường gây thiếu dữ liệu hoặc kết quả rỗng/sai. Ngược lại, `flip()` không phải thứ cần gọi ở mọi bước: khi `ByteBuffer.wrap(bytes)`, buffer ban đầu đã có `position=0` và `limit=bytes.length`, sẵn sàng được channel **ghi ra**.

`ByteBuffer.allocate(...)` tạo heap buffer; `ByteBuffer.allocateDirect(...)` tạo direct buffer có thể hữu ích cho một số đường I/O native, nhưng cấp phát có chi phí và không được đảm bảo luôn nhanh hơn. Hãy đo bằng workload thực tế. Một `ByteBuffer` chia sẻ giữa nhiều thread cũng không tự thread-safe vì `position`/`limit` là mutable.

## 4. Khác biệt về vị trí: Stream tuần tự, channel có thể random access

`InputStream` tiến theo dữ liệu được đọc. Phương thức `skip(long)` chỉ yêu cầu bỏ qua byte theo kiểu tiến tới, có thể bỏ ít hơn yêu cầu; `mark/reset` không được mọi stream hỗ trợ và không đại diện cho offset tuyệt đối tùy ý trong file. `OutputStream` không có API chuẩn để “ghi ở byte thứ 100” của cùng một đối tượng stream.

`FileChannel` có thể:

```java
channel.position(100);        // đặt current file position
channel.read(buffer);         // đọc từ current position, vị trí tăng theo bytes đã đọc

channel.read(buffer, 500);    // đọc từ byte 500, KHÔNG đổi current position
channel.write(buffer, 200);   // ghi tại byte 200, KHÔNG đổi current position
```

Đây là đoạn minh họa khi `channel` là `FileChannel` và buffer đã chuẩn bị đúng trạng thái; lệnh có thể đọc/ghi ít byte hơn dự kiến. Cả `channel.position(100)` và positional overload chỉ có nghĩa với **đúng loại channel hỗ trợ**. `SocketChannel` không có phương thức `position(long)` vì socket là kết nối byte stream mạng chứ không phải một file seekable.

Khi dùng `FileInputStream.getChannel()` / `FileOutputStream.getChannel()`, channel gắn với stream đó và có liên hệ với vị trí file của cùng file descriptor: thay vị trí hoặc đọc/ghi từ một phía có thể ảnh hưởng vị trí quan sát qua phía kia. **Đừng giả định stream và channel lấy từ nó có hai cursor độc lập.** Nếu nhiều thao tác dùng chung current position, phải phối hợp theo yêu cầu ứng dụng. Mở hai handle riêng tạo trạng thái con trỏ riêng nhưng không làm dữ liệu file trở thành hai bản khác nhau.

### Ví dụ độc lập: Stream đọc tuần tự, FileChannel đọc/ghi theo offset

Tạo `StreamChannelComparison.java`:

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class StreamChannelComparison {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("nio-contrast-", ".txt");
        try {
            Files.writeString(file, "ABCDEFG", StandardCharsets.US_ASCII);

            try (InputStream in = Files.newInputStream(file)) {
                byte[] first = in.readNBytes(3);
                System.out.println("stream=" + new String(first, StandardCharsets.US_ASCII));
            }

            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                ByteBuffer receive = ByteBuffer.allocate(3);
                int n = channel.read(receive, 4);
                receive.flip();
                System.out.println("channel-bytes=" + n);
                System.out.println("channel-data=" +
                        StandardCharsets.US_ASCII.decode(receive));
                System.out.println("current-position=" + channel.position());

                ByteBuffer replacement = ByteBuffer.wrap(
                        "xy".getBytes(StandardCharsets.US_ASCII));
                long offset = 1;
                while (replacement.hasRemaining()) {
                    int written = channel.write(replacement, offset);
                    if (written == 0) throw new IOException("Write stalled");
                    offset += written;
                }
            }
            System.out.println("final=" + Files.readString(file, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Chạy trên Java 17+:

```bash
javac --release 17 StreamChannelComparison.java
java StreamChannelComparison
```

Kết quả:

```text
stream=ABC
channel-bytes=3
channel-data=EFG
current-position=0
final=AxyDEFG
```

**Vì sao có kết quả này?**

1. `Files.newInputStream` đọc ba byte liên tiếp bắt đầu từ đầu tệp, tạo chuỗi `ABC`.
2. `channel.read(receive, 4)` đọc ba byte tại offset `4..6` thành `EFG` nhưng **không di chuyển** current position của channel, nên vẫn là `0`.
3. `receive.flip()` cho phép decoder lấy ba byte vừa đọc.
4. `channel.write(replacement, offset)` ghi `xy` tại offset `1..2`; vòng lặp dự phòng khi một lần ghi chưa hết buffer. Nội dung cuối là `AxyDEFG`.
5. `try-with-resources` đóng stream và channel. File tạm được xóa ở `finally`.

Chương trình chủ động dùng ASCII để offset một byte trùng một ký tự. Với UTF-8, positional read/write phải xử lý ranh giới byte của ký tự đúng cách và chọn một giao thức record/encoding rõ ràng.

## 5. Blocking và non-blocking: khác biệt theo *loại channel*, không theo tên NIO

Một hiểu nhầm là “`java.io` blocking, `java.nio` luôn non-blocking”. Java NIO có cả channel **blocking** và channel có thể **non-blocking**.

- `FileChannel` thông thường thực hiện đọc/ghi đồng bộ với các lời gọi `read/write`; nó không phải `SelectableChannel` và không thể dùng `Selector` để chờ file sẵn sàng đọc như với socket.
- `SocketChannel` có thể ở chế độ blocking mặc định hoặc cấu hình `configureBlocking(false)`, khi đó `read/write` có thể trả `0` nếu chưa có dữ liệu hoặc chưa thể ghi. Kết hợp `Selector` để tránh tạo một thread chờ riêng cho mỗi kết nối, tùy kiến trúc server.
- `AsynchronousFileChannel` là lớp riêng, cung cấp thao tác file bất đồng bộ với completion callback hoặc `Future`; việc nhìn thấy “channel” trong tên không biến `FileChannel` thường thành API asynchronous.
- `InputStream.read` theo hợp đồng phổ biến chờ dữ liệu, EOF hoặc lỗi khi không có byte sẵn; tuy nhiên behavior cụ thể của stream wrapper/provider vẫn phải được hiểu từ API tương ứng.

`Selector` cũng **không tự tăng tốc đọc file** hoặc biến mọi luồng byte thành sự kiện. Đối với tệp lớn ở disk, `FileChannel` được chọn chủ yếu vì thao tác byte buffer, offset, transfer, mapping hoặc file control. Đối với hàng nghìn socket, selectable/non-blocking channel có thể cung cấp mô hình I/O rất khác stream kết hợp thread per connection.

## 6. Lệnh read/write không bảo đảm cùng lượng byte

Phân biệt hợp đồng I/O để tránh mất dữ liệu:

### Với `InputStream`

- `read()` trả `0..255` cho một byte hoặc `-1` khi EOF. Một byte giá trị `0xFF` trở thành số `255`, **khác** `-1`.
- `read(byte[])` trả số byte thực đọc hoặc `-1` khi EOF; không giả định một lời gọi luôn điền hết mảng.
- `readNBytes(length)` tiện khi muốn lấy tối đa một số byte; có thể trả ít nếu EOF đến trước. `readAllBytes()` tiện cho dữ liệu nhỏ đã có giới hạn, nhưng không thích hợp khi kích thước file/response không đáng tin và có thể quá lớn.
- Nếu cần đúng `N` byte trong một giao thức nhị phân, lặp cho đủ hoặc dùng API có nghĩa “đọc đủ hoặc lỗi”, như `DataInputStream.readFully(byte[])`; phải xử lý EOF phù hợp.

### Với Channel

- `ReadableByteChannel.read(ByteBuffer)` có thể trả số dương nhỏ hơn `remaining`, trả `0` khi không chuyển được byte trong một số trường hợp, hoặc `-1` ở EOF.
- `WritableByteChannel.write(ByteBuffer)` trả **số byte được tiêu thụ** từ buffer; có thể là `0` hoặc nhỏ hơn `remaining`.
- Sau khi đọc, cần `flip` trước khi tiêu thụ; sau khi xử lý một phần, cần `compact` nếu muốn giữ dữ liệu dư; khi ghi, loop tới khi `!buffer.hasRemaining()` hoặc áp chính sách non-blocking/backpressure phù hợp.
- Với channel non-blocking, không được busy-loop khi `read/write == 0`: đăng ký sự kiện với `Selector` hoặc lên lịch lại phù hợp.

Mẫu vòng lặp ghi đồng bộ (đoạn minh họa, `out` là `WritableByteChannel` đã mở):

```java
while (buffer.hasRemaining()) {
    int n = out.write(buffer);
    if (n == 0) {
        throw new IOException("Blocking write made no progress");
    }
}
```

Mẫu này phù hợp với ví dụ kênh blocking/dữ liệu nhỏ, nơi `0` bất thường. **Không sao chép nguyên xi chính sách “ném khi 0” vào network channel non-blocking**, nơi `0` là tình huống bình thường và cần quản lý `OP_WRITE`/backpressure thay vì ném lỗi hoặc quay CPU.

## 7. Stream và Channel kết nối với nhau được

Không cần viết lại toàn bộ ứng dụng để đổi giữa hai họ API: `java.nio.channels.Channels` cung cấp các adapter.

| Muốn có | API | Cần nhớ |
| --- | --- | --- |
| `ReadableByteChannel` từ `InputStream` | `Channels.newChannel(input)` | Adapter đọc qua stream gốc; không tự bổ sung seek/random access hay non-blocking. |
| `WritableByteChannel` từ `OutputStream` | `Channels.newChannel(output)` | Adapter ghi qua output stream gốc; không tạo file channel. |
| `InputStream` từ `ReadableByteChannel` | `Channels.newInputStream(channel)` | Đọc stream bằng channel phía dưới; quản lý trạng thái/đóng tài nguyên chung. |
| `OutputStream` từ `WritableByteChannel` | `Channels.newOutputStream(channel)` | Ghi stream bằng channel; không tự biến channel thành durable storage. |
| `Reader` / `Writer` từ byte channel | `Channels.newReader(...)` / `newWriter(...)` | Có giải mã/mã hóa ký tự theo charset; cẩn thận chữ tiếng Việt và ký tự cắt qua buffer. |

Một số adapter dùng channel có thể chọn non-blocking cần channel ở **blocking mode** cho lệnh bridge; nếu không, có thể gặp `IllegalBlockingModeException` tùy API. Dùng adapter không có nghĩa là bất đồng bộ hoặc non-blocking; khi chọn thiết kế mạng theo `Selector`, thường nên dùng trực tiếp `ByteBuffer`/`SocketChannel`.

**Ownership:** wrapper và channel/stream nền thường liên kết vòng đời. Đóng adapter có thể đóng channel/stream nguồn, vì vậy không được giả định có thể tiếp tục tái sử dụng tài nguyên gốc sau khi đóng wrapper. Tránh giao hai phần chương trình quyền tự ý đóng cùng một kết nối khi một phần vẫn đang dùng.

### Ví dụ độc lập: stream ↔ channel và UTF-8

Tạo `ChannelBridgeDemo.java`:

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class ChannelBridgeDemo {
    public static void main(String[] args) throws IOException {
        byte[] encoded = "học Java".getBytes(StandardCharsets.UTF_8);

        try (ReadableByteChannel input =
                Channels.newChannel(new ByteArrayInputStream(encoded))) {
            ByteBuffer buffer = ByteBuffer.allocate(encoded.length);
            while (buffer.hasRemaining()) {
                int n = input.read(buffer);
                if (n < 0) break;
                if (n == 0) throw new IOException("Read stalled");
            }
            System.out.println("bytes=" + buffer.position());
            buffer.flip();
            System.out.println("decoded=" + StandardCharsets.UTF_8.decode(buffer));
        }

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (WritableByteChannel output = Channels.newChannel(sink)) {
            ByteBuffer buffer = ByteBuffer.wrap(
                    "OK".getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                if (output.write(buffer) == 0) throw new IOException("Write stalled");
            }
        }
        System.out.println("sink=" + sink.toString(StandardCharsets.UTF_8));
    }
}
```

```bash
javac --release 17 ChannelBridgeDemo.java
java ChannelBridgeDemo
```

Kết quả:

```text
bytes=10
decoded=học Java
sink=OK
```

Cụm `học Java` có **8 ký tự** nhưng được UTF-8 mã hóa thành **10 byte**: chữ `ọ` dùng ba byte UTF-8; các ký tự còn lại ở ví dụ đều dùng một byte. `ByteBuffer.position()` sau đọc cho biết đã nhận 10 byte; `flip()` chuẩn bị để `UTF_8.decode(...)` tái tạo text. Ở chiều ghi, `Channels.newChannel(sink)` bọc `ByteArrayOutputStream` thành `WritableByteChannel`, rồi giải mã phần ghi không cần file thực.

Ví dụ dùng nguồn byte trong bộ nhớ để số byte và output xác định. Với socket thực hoặc stream có dữ liệu đến theo nhiều đợt, cách tích lũy và decode phải quản lý byte UTF-8 chưa hoàn chỉnh ở ranh giới từng lần đọc; không nên giải mã rời mỗi chunk bằng cách giả định nó luôn kết thúc ở ranh giới ký tự.

## 8. Tính năng thêm của một số Channel không có trực tiếp trên stream căn bản

### `FileChannel.transferTo / transferFrom`

`FileChannel.transferTo(position, count, target)` chuyển dữ liệu từ một vùng file sang `WritableByteChannel`. `transferFrom(src, position, count)` nhận dữ liệu từ một `ReadableByteChannel`. Đây là những API hữu ích khi chuyển phần lớn byte giữa tệp và các kênh I/O.

```java
long moved = sourceFileChannel.transferTo(offset, remaining, targetChannel);
```

Đoạn trên là minh họa, `sourceFileChannel`/`targetChannel` đã mở. **Không giả định `moved == remaining`**: kết quả có thể nhỏ hơn (kể cả `0`), nên ứng dụng phải xử lý phần dư, EOF và tình huống không thể tiến triển. Một số OS có tối ưu kiểu zero-copy, nhưng không có bảo đảm “luôn zero-copy” hay “luôn nhanh hơn mọi stream”.

### File mapping

`FileChannel.map(...)` tạo `MappedByteBuffer` cho một vùng file, có thể giảm thao tác copy hoặc hỗ trợ truy cập dữ liệu với mô hình memory mapping. Đây là năng lực **của FileChannel**, không phải của mọi `Channel`. Cần cân nhắc giới hạn map, vòng đời mapping, tính nhất quán với writer khác, flush và tài nguyên bộ nhớ ngoài heap; không chọn mapping mặc định cho mọi file.

### Scattering / gathering

Một `ScatteringByteChannel` có thể đọc lần lượt vào nhiều `ByteBuffer` trong một thao tác; `GatheringByteChannel` có thể ghi từ nhiều buffer. Ví dụ định dạng record gồm header và payload có thể dùng buffer riêng rồi kết hợp. Một lần gọi vẫn có thể xử lý ít byte hơn yêu cầu, do đó phải kiểm tra số byte thực và trạng thái từng buffer. Không phải mọi `ByteChannel` đều hỗ trợ các interface này.

### File locking và metadata

`FileChannel.lock`/`tryLock` hỗ trợ khóa vùng file khi môi trường thực thi và file store đáp ứng hợp đồng; chúng **không thay thế khóa logic trong JVM** để bảo vệ chuỗi cập nhật record từ nhiều thread. `Files`/`Path` và các file attribute views phụ trách phần lớn thao tác metadata; việc có `FileChannel` không có nghĩa mọi đặc tính tệp đều là method của channel.

## 9. Hiệu năng: chọn theo nghiệp vụ, không theo khẩu hiệu

Không có quy tắc “NIO luôn nhanh hơn IO”:

1. **Tệp nhỏ, luồng đơn, code đơn giản:** `Files.readString`, `Files.writeString`, `BufferedInputStream`, `BufferedReader` thường dễ đọc và hoàn toàn phù hợp.
2. **Dữ liệu lớn xử lý tuần tự:** xử lý từng khối với `BufferedInputStream` hoặc `FileChannel` đều có thể hiệu quả. Không nên `readAllBytes()` một nguồn có kích thước không bị giới hạn.
3. **Cần offset cố định/random access:** `FileChannel` hoặc `SeekableByteChannel` có `position`; `FileChannel` có overload đọc/ghi offset tường minh.
4. **Truyền vùng file qua channel:** đánh giá `transferTo` / `transferFrom`, xử lý trường hợp chuyển thiếu và đo trên OS mục tiêu.
5. **Nhiều socket đồng thời:** `SocketChannel` + `Selector`/non-blocking có thể hữu ích khi yêu cầu lập trình mạng thiên về event loop; độ phức tạp cao hơn với trạng thái đọc/ghi dở, backpressure và scheduling.
6. **Mã hóa text/đọc dòng:** `Reader`/`Writer`, `BufferedReader` hoặc `Channels.newReader/newWriter` giúp xử lý charset; ByteBuffer đơn thuần không biết đâu là ranh giới ký tự.

Muốn so hiệu năng cần xác định throughput, latency, số lần gọi I/O, kích thước buffer, heap/direct allocation, OS file cache, read-ahead, SSD/network và chi phí decode/copy. Một số API cấp cao trong `Files` có thể sử dụng cơ chế NIO bên dưới; so “package” không thay cho đo thực tế. Nếu thao tác chỉ là sao chép tệp, `Files.copy(source, target)` còn có thể phù hợp hơn cả viết vòng lặp stream/channel tùy chỉnh.

## 10. Lỗi phổ biến khi chuyển từ Stream sang Channel

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| `read` vào buffer xong lại giải mã ra chuỗi rỗng/sai | Quên `flip()`; `position` đã ở cuối vùng vừa ghi. | `flip()` trước khi đọc từ buffer; `compact()` khi cần giữ dữ liệu còn lại. |
| Chỉ ghi được một phần payload | Tưởng `channel.write(buffer)` xử lý hết dữ liệu trong một gọi. | Loop theo `buffer.hasRemaining()` và theo hợp đồng blocking/non-blocking. |
| CPU tăng cao khi socket chưa ghi/đọc được | Busy-loop khi non-blocking `read/write` trả `0`. | Dùng `Selector`/interest ops và backpressure đúng cách. |
| Đọc sai byte sau khi đổi offset | Chia sẻ current position của cùng channel hoặc stream lấy từ channel. | Positional read/write, lock, hoặc ownership rõ ràng cho từng kết nối. |
| `IllegalBlockingModeException` với adapter | Bọc selectable channel không đúng chế độ blocking mà adapter yêu cầu. | Hiểu mode, chọn direct channel API khi vận hành non-blocking. |
| Tưởng mọi channel hỗ trợ `position` | Chỉ `SeekableByteChannel` có hợp đồng seek. | Dùng đúng interface cho file; không seek `SocketChannel`. |
| File đọc Unicode bị lỗi | Dùng byte offset như chỉ số ký tự, cắt UTF-8 giữa code point, hoặc dùng charset ngầm. | Chọn `StandardCharsets.UTF_8` và decoder đúng ranh giới; dùng `Reader` khi bài toán theo ký tự. |
| Gọi `flush()` nhưng nghĩ đã sync disk | `OutputStream.flush` chỉ đẩy byte còn trong tầng buffer theo implementation. | Cân nhắc `FileChannel.force` hoặc API/FS phù hợp khi thực sự cần durability; kiểm chứng hợp đồng nền tảng. |
| Đóng stream khiến channel không dùng tiếp được | Wrapper và tài nguyên gốc chia sẻ ownership/vòng đời. | Có một owner cho tài nguyên; dùng `try-with-resources` ở đúng phạm vi. |
| Thấy `transferTo` chuyển ít byte hơn dự kiến | Hợp đồng cho phép partial transfer/`0` theo trạng thái nguồn và đích. | Loop có kiểm soát, kiểm tra EOF và xử lý thiếu tiến triển. |

Một số ngoại lệ hữu ích cần biết khi thao tác channel gồm `ClosedChannelException`, `NonReadableChannelException`, `NonWritableChannelException` và `IOException`. Khi đổi chế độ mạng, `IllegalBlockingModeException` liên quan cách phối hợp blocking và adapter/selector; đừng xử lý chúng bằng cách nuốt lỗi và giả định số byte đã truyền đủ.

## 11. Mẫu quyết định: chọn API nào?

| Nhu cầu | Lựa chọn có lý do |
| --- | --- |
| Đọc tệp cấu hình nhỏ thành text | `Files.readString(path, UTF_8)` — ngắn, rõ ý định. |
| Đọc file CSV theo dòng | `Files.newBufferedReader(path, UTF_8)` hoặc `BufferedReader` — giải mã ký tự và đọc dòng. |
| Đọc binary stream tuần tự từ network/library có sẵn `InputStream` | `InputStream` + buffer phù hợp — tận dụng API đang có. |
| Ghi binary stream đến API nhận `OutputStream` | `OutputStream` (có `BufferedOutputStream` nếu cần) — tránh adapter không cần thiết. |
| Thao tác tại byte offset hoặc đọc nhiều phần file rời nhau | `FileChannel.read/write(ByteBuffer, position)` hoặc `SeekableByteChannel`. |
| Cần đọc và ghi cùng file bằng một tài nguyên | `FileChannel` với `READ` và `WRITE` — hai hướng, có position rõ ràng. |
| Xử lý nhiều socket không blocking với event loop | `SocketChannel`/`ServerSocketChannel` + `Selector` — phối hợp readiness. |
| Chuyển vùng file sang writable channel | `FileChannel.transferTo(...)` — có thể tối ưu đường truyền, nhưng xử lý partial transfer. |
| Đã có `InputStream` nhưng thư viện chỉ nhận `ReadableByteChannel` | `Channels.newChannel(input)` — chuyển interface có kiểm soát vòng đời. |

### Câu hỏi tự kiểm tra

**1. Vì sao một `FileChannel` là `ByteChannel` nhưng một `Pipe.SourceChannel` không phải channel ghi được?**

Vì `ByteChannel` hợp nhất cả `ReadableByteChannel` và `WritableByteChannel`. Pipe source chỉ có nhiệm vụ nhận byte từ đầu source, không hứa khả năng ghi; chức năng phụ thuộc interface thực tế.

**2. `ByteBuffer.flip()` có nhiệm vụ gì?**

Nó đặt `limit` thành `position` hiện tại rồi đưa `position` về `0`, chuẩn bị tiêu thụ bytes vừa ghi vào buffer. Nó không thay đổi nội dung bytes và không phải lệnh flush xuống đĩa.

**3. Vì sao `write(ByteBuffer)` thường cần vòng lặp còn `OutputStream.write(byte[])` không trả số byte?**

Hai API có hợp đồng khác nhau. Writable channel trả số byte thực chuyển, có thể ghi ngắn hoặc chưa ghi; output stream không trả số byte và có thể báo lỗi bằng `IOException`. Không được từ điều đó suy ra output stream đảm bảo atomic/durable.

**4. `FileChannel` có non-blocking như `SocketChannel` không?**

Không theo nghĩa selectable non-blocking I/O. `FileChannel` thông thường là channel truy cập file đồng bộ và không đăng ký `Selector`; `SocketChannel` có thể cấu hình non-blocking.

**5. `Channels.newChannel(InputStream)` có làm InputStream thành file random access không?**

Không. Nó cung cấp adapter đọc kiểu channel trên nguồn có sẵn; không tự tạo `FileChannel` hoặc khả năng seek.

**6. Khi nào nên giữ `InputStream` thay vì đổi sang NIO?**

Khi bài toán là đọc tuần tự, giao diện thư viện đã phù hợp, performance chấp nhận được và không cần tính năng channel riêng; code đơn giản hơn thường dễ bảo trì và kiểm thử hơn.

## Tài liệu chính thức

- [Java SE 21 — `InputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html) và [`OutputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/OutputStream.html): hợp đồng byte stream.
- [Java SE 21 — `java.nio.channels` package](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/package-summary.html): hệ thống interface và mô hình channel.
- [Java SE 21 — `FileChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): file positioning, positional I/O, transfer, mapping và file locking.
- [Java SE 21 — `ByteBuffer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): `position`, `limit`, `flip`, `clear`, `compact`.
- [Java SE 21 — `Channels`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Channels.html): adapter giữa channel, stream, reader và writer.
- [Java SE 21 — `SocketChannel`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html): channel socket và blocking mode.

**Ghi nhớ:** chọn API theo khả năng cần dùng và hợp đồng về bytes, buffer, offset, blocking, vòng đời tài nguyên. Stream thuận tiện cho xử lý tuần tự; channel mở rộng khả năng thao tác trên `ByteBuffer` và những tính năng riêng của file/network channel, nhưng không có lựa chọn nào tự động tốt hơn trong mọi hoàn cảnh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-002">ReadableByteChannel, WritableByteChannel và ByteChannel</a>

<details>
<summary>Xem chi tiết</summary>

# ReadableByteChannel, WritableByteChannel và ByteChannel trong Java NIO

## 1. NIO Channel là gì?

Trong Java NIO, **channel** là một đối tượng đại diện cho một kết nối I/O đang mở với nguồn hoặc đích dữ liệu: tệp, kết nối mạng, pipe, hoặc một nguồn/đích được chuyển đổi từ `InputStream` và `OutputStream`. Khi làm việc với **byte channel**, chương trình trao đổi dữ liệu qua `ByteBuffer`.

Ba interface nền tảng trong package `java.nio.channels` là:

- **`ReadableByteChannel`**: cho phép đọc byte từ channel vào `ByteBuffer`.
- **`WritableByteChannel`**: cho phép ghi byte từ `ByteBuffer` ra channel.
- **`ByteChannel`**: kết hợp cả hai khả năng đọc và ghi qua cùng một đối tượng.

Một channel thường có trạng thái **open/closed** qua interface `Channel`. Channel không đồng nghĩa với stream, nhưng có thể thích ứng với stream qua `java.nio.channels.Channels`. Quan trọng hơn, cả ba interface được thiết kế để dùng với buffer: các thao tác làm thay đổi **position** của `ByteBuffer` theo số byte đã chuyển, chứ không tự quản lý văn bản, charset hay ranh giới message.

Bài này tập trung vào **hợp đồng của các interface**, cách xử lý kết quả đọc/ghi chưa đủ, và cách chuyển trạng thái buffer để không làm mất dữ liệu.

## 2. Quan hệ kế thừa

Sơ đồ interface:

```text
                         java.nio.channels.Channel
                         + isOpen()
                         + close()
                            ^
                            |
           +----------------+----------------+
           |                                 |
ReadableByteChannel                 WritableByteChannel
+ read(ByteBuffer dst)              + write(ByteBuffer src)
           ^                                 ^
           |                                 |
           +---------------+-----------------+
                           |
                       ByteChannel
              (kế thừa cả read và write)
```

`Channel` mở rộng `java.io.Closeable`, nên có thể dùng `try-with-resources`. `ByteChannel` là interface hợp thành, không khai báo thêm phương thức đọc/ghi nào ngoài các phương thức kế thừa.

Các chữ ký quan trọng:

```java
// ReadableByteChannel
int read(ByteBuffer dst) throws IOException;

// WritableByteChannel
int write(ByteBuffer src) throws IOException;

// Channel
boolean isOpen();
void close() throws IOException;
```

Đây là các chữ ký API rút gọn để học tập, không phải một chương trình chạy độc lập.

| Interface | Đọc? | Ghi? | Đặc điểm |
| --- | --- | --- | --- |
| `ReadableByteChannel` | Có | Không bắt buộc | Lấy byte từ nguồn vào vùng còn trống của `dst` |
| `WritableByteChannel` | Không bắt buộc | Có | Lấy byte còn lại của `src` và đẩy tới đích |
| `ByteChannel` | Có | Có | Một đối tượng có cả hai hợp đồng, vẫn tùy cách mở và triển khai |
| `SeekableByteChannel` | Có | Có | Mở rộng `ByteChannel` với `position()`, `position(long)`, `size()`, `truncate()` |

**Lưu ý:** Một lớp có thể triển khai `ByteChannel` nhưng thao tác thực tế còn tùy cấu hình/mode. Ví dụ `FileChannel` mở chỉ để đọc không cho phép ghi; gọi `write` có thể ném `NonWritableChannelException`. Việc có phương thức `write` trong kiểu Java chưa bảo đảm tài nguyên đang được mở với quyền ghi.

## 3. `ReadableByteChannel.read(ByteBuffer dst)`

Lệnh:

```java
int n = channel.read(dst);
```

giả định `channel` là `ReadableByteChannel` đang mở và `dst` là `ByteBuffer` có thể ghi.

Phương thức cố gắng chuyển byte từ channel vào **vùng còn lại của buffer**: từ `dst.position()` tới ngay trước `dst.limit()`. Khi chuyển được `n > 0` byte, `position` tăng đúng `n`; `limit` và `capacity` không đổi vì thao tác này chỉ đặt thêm byte vào buffer.

Giá trị trả về có ba tình huống:

| Giá trị | Ý nghĩa | Hành động tiếp theo |
| --- | --- | --- |
| `n > 0` | Đã đọc đúng `n` byte | Có thể tiếp tục đọc hoặc `flip()` để xử lý |
| `n == 0` | Lần gọi này chưa chuyển byte nào | Xem buffer còn chỗ không, mode blocking/non-blocking và readiness |
| `n == -1` | Đã tới **end-of-stream (EOF)** | Không chờ thêm dữ liệu từ nguồn đã kết thúc |

**Không hứa đọc đầy buffer trong một lần gọi.** Nếu `dst.remaining() == 100`, lệnh `read(dst)` có thể trả về 1, 24 hoặc một số dương khác tối đa 100. Buffer có `remaining()==0` thì không có chỗ để nhận byte; thường thao tác đọc trả về 0 khi không thể chuyển byte.

Với channel có chế độ **non-blocking**, `read` có thể trả về 0 khi chưa có dữ liệu sẵn sàng. Nếu channel blocking và buffer có chỗ, thao tác có thể phải chờ dữ liệu hoặc EOF theo hợp đồng của triển khai. Không viết vòng `while (read(...) == 0)` quay liên tục trên non-blocking channel: vòng lặp đó chiếm CPU mà không tạo tiến độ; cần phối hợp readiness/`Selector` hoặc cơ chế đợi phù hợp.

### Position trước và sau khi đọc

Giả sử `dst.capacity()==8`, `position==2`, `limit==6`, channel chuyển 3 byte:

```text
Trước: [ ? ][ ? ][ _ ][ _ ][ _ ][ _ ][ ? ][ ? ]
                    ^
                   pos=2
                                     ^
                                   limit=6

Sau:   [ ? ][ ? ][ A ][ B ][ C ][ _ ][ ? ][ ? ]
                                   ^
                                  pos=5
                                     ^
                                   limit=6
```

Chỉ vùng `[2,5)` được ghi mới. `n=3`; `dst.remaining()` sau đọc bằng 1. Byte cũ ngoài vùng này không tự bị xóa hay kiểm tra.

Một lệnh `read` cần buffer **có thể ghi**. Nếu `dst` chỉ đọc, thao tác có thể ném `ReadOnlyBufferException`; nếu channel đã đóng, có thể ném `ClosedChannelException` (đều cần phân biệt với kết quả EOF).

## 4. `WritableByteChannel.write(ByteBuffer src)`

Lệnh:

```java
int n = channel.write(src);
```

giả định `channel` là `WritableByteChannel` và `src` chứa dữ liệu cần chuyển ra ngoài ở vùng `[position, limit)`.

- `write` tiêu thụ **tối đa `src.remaining()` byte**, bắt đầu từ `position`.
- Trả về **số byte thực sự đã ghi**, không trả về `-1` như `read`.
- Sau khi ghi `n` byte, `src.position()` tăng đúng `n`; `limit` và `capacity` giữ nguyên.
- Nếu chưa ghi được byte nào, có thể trả về **0**, đặc biệt với channel không chặn hoặc khi `src` không còn byte.

**Không giả định một lần `write` ghi hết buffer.** Ví dụ nếu `src.remaining()==10` nhưng chỉ ghi được 4 byte, position tăng 4 và còn 6 byte chờ gửi. Gọi `clear()` lúc này sẽ làm mất trạng thái của phần chưa gửi; nếu tiếp tục sử dụng buffer, cần giữ nguyên position/limit rồi ghi tiếp cho đến khi hết `remaining` hoặc lưu lại phần dư theo chính sách.

### Quan hệ với `ByteBuffer.flip()`

Thông thường chương trình chuẩn bị byte để gửi như sau:

```java
ByteBuffer src = ByteBuffer.allocate(8);
src.put((byte) 65);
src.put((byte) 66);
src.put((byte) 67);
src.flip();   // position=0, limit=3
// WritableByteChannel.write(src) đọc 3 byte từ src nếu đích tiếp nhận đủ.
```

Đoạn mã trên là minh họa trạng thái; chưa chứa đối tượng channel. Quên `flip()` có thể khiến channel cố ghi từ offset 3 đến limit cũ 8, tức vùng không đại diện cho ba byte mới vừa chuẩn bị.

## 5. `ByteChannel`: một đối tượng có thể đọc và ghi

`ByteChannel` kế thừa cả `ReadableByteChannel` và `WritableByteChannel`. Vì thế biến có kiểu `ByteChannel` gọi được cả `read(ByteBuffer)` lẫn `write(ByteBuffer)`.

Nhưng **hai chiều thao tác không đồng nghĩa có file pointer, seek hoặc khả năng full-duplex luôn sẵn sàng**:

- `ByteChannel` **không định nghĩa** `position()` hay `position(long)`.
- Nếu cần di chuyển offset của tệp, dùng `SeekableByteChannel` hoặc `FileChannel`.
- Nếu cần channel mạng hỗ trợ đọc/ghi, `SocketChannel` là ví dụ triển khai có thêm cơ chế kết nối và non-blocking.
- Khả năng đọc và ghi tại cùng thời điểm, trạng thái kết nối, quyền I/O và khả năng thread-safe phụ thuộc vào lớp cụ thể.

Trong hệ thống tệp, `Files.newByteChannel(path, options...)` trả về `SeekableByteChannel`; đối tượng đó đồng thời là `ByteChannel`. Một handle có thể dùng để đọc và ghi nếu được mở với các `StandardOpenOption` phù hợp.

## 6. Ví dụ Java 17+ độc lập: sao chép dữ liệu qua hai channel

Chương trình tạo nguồn byte trong bộ nhớ và một đích byte trong bộ nhớ, rồi sử dụng hai interface riêng:

- `ReadableByteChannel` chuyển dữ liệu từ `ByteArrayInputStream` vào `ByteBuffer`.
- `WritableByteChannel` chuyển dữ liệu từ `ByteBuffer` vào `ByteArrayOutputStream`.
- `Channels.newChannel(...)` tạo adapter từ Java IO stream sang NIO channel.

Không cần tệp ngoài hoặc mạng; đầu ra xác định trên mọi môi trường Java 17+.

**Tệp `ChannelCopyDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public class ChannelCopyDemo {
    public static void main(String[] args) throws IOException {
        byte[] input = "Java NIO!".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        try (ReadableByteChannel source =
                     Channels.newChannel(new ByteArrayInputStream(input));
             WritableByteChannel target =
                     Channels.newChannel(sink)) {

            ByteBuffer buffer = ByteBuffer.allocate(4);
            int total = 0;

            while (true) {
                int count = source.read(buffer);

                if (count == -1) {
                    break; // EOF
                }
                if (count == 0) {
                    throw new IOException("No progress in blocking demo");
                }

                total += count;
                buffer.flip(); // Chuẩn bị vùng vừa nhận để ghi ra.

                while (buffer.hasRemaining()) {
                    int written = target.write(buffer);
                    if (written == 0) {
                        throw new IOException("No write progress in demo");
                    }
                }

                buffer.clear(); // Đã chuyển hết, chuẩn bị nhận vòng sau.
            }

            System.out.println("Da sao chep: " + total + " byte");
        }

        System.out.println(
            "Noi dung: " + sink.toString(StandardCharsets.UTF_8));
    }
}
```

Biên dịch và chạy:

```bash
javac ChannelCopyDemo.java
java ChannelCopyDemo
```

**Stdout chính xác khi thao tác thành công:**

```text
Da sao chep: 9 byte
Noi dung: Java NIO!
```

Giải thích bằng các vòng lặp buffer `capacity=4`:

| Lần nhận | Byte nhận | Position sau `read` | Sau `flip` | Đã ghi ra |
| --- | --- | ---: | --- | --- |
| 1 | `Java` (4 byte) | 4 | `position=0, limit=4` | `Java` |
| 2 | ` NIO` (4 byte) | 4 | `position=0, limit=4` | ` NIO` |
| 3 | `!` (1 byte) | 1 | `position=0, limit=1` | `!` |
| 4 | EOF | Không chuyển byte | Không `flip` | Thoát |

Với adapter trên `ByteArrayInputStream`, các lần đọc có đủ dữ liệu sẽ trả về số byte dương phù hợp kích thước buffer trong ví dụ. Trong **channel thực tế**, không nên dựa vào số byte nhận cố định như bảng minh họa; logic chính là dùng giá trị `count` thật sự, `flip` rồi ghi cho tới khi hết remaining.

Chương trình xử lý `count==0` và `written==0` bằng ngoại lệ vì đang dùng các adapter stream trong bộ nhớ mang tính blocking và luôn có buffer khác rỗng ở điểm thao tác. Đây **không phải chiến lược dùng cho mọi channel non-blocking**. Với `SocketChannel` non-blocking, cần cơ chế chờ readiness, giữ lại dữ liệu chưa gửi và tiếp tục khi channel sẵn sàng.

### Vì sao cần vòng lặp `while (buffer.hasRemaining())`?

`WritableByteChannel.write` có quyền tiêu thụ **một phần** dữ liệu. Nếu chỉ gọi `write` một lần rồi `clear`, phần chưa ghi có thể bị mất. Vòng lặp bảo đảm chương trình không bỏ qua phần dư; trong các ứng dụng non-blocking, vòng lặp sẽ được **tạm dừng khi write trả về 0** và tiếp tục sau khi có tín hiệu sẵn sàng, thay vì chạy quay liên tục.

## 7. Ví dụ Java 17+ độc lập: `ByteChannel` và đọc/ghi tệp

Ví dụ kế tiếp dùng `SeekableByteChannel` do `Files.newByteChannel` tạo ra. Vì interface này kế thừa `ByteChannel`, ta gán cùng đối tượng cho biến `ByteChannel io` để gọi `write` và `read`. Riêng thao tác đưa vị trí tệp về đầu phải gọi qua `SeekableByteChannel`.

**Tệp `ByteChannelFileDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ByteChannelFileDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("byte-channel-", ".bin");

        try {
            try (SeekableByteChannel file = Files.newByteChannel(
                    path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteChannel io = file;
                ByteBuffer outgoing =
                    ByteBuffer.wrap("ABC".getBytes(StandardCharsets.US_ASCII));

                while (outgoing.hasRemaining()) {
                    int n = io.write(outgoing);
                    if (n == 0) {
                        throw new IOException("No file write progress");
                    }
                }

                System.out.println("Kich thuoc tep: " + file.size());

                file.position(0); // ByteChannel không có position(long).
                ByteBuffer incoming = ByteBuffer.allocate(3);

                while (incoming.hasRemaining()) {
                    int n = io.read(incoming);
                    if (n == -1) {
                        break;
                    }
                    if (n == 0) {
                        throw new IOException("No file read progress");
                    }
                }

                incoming.flip();
                System.out.println(
                    "Doc lai: " + StandardCharsets.US_ASCII.decode(incoming));
                System.out.println("Channel dang mo: " + io.isOpen());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Lệnh:

```bash
javac ByteChannelFileDemo.java
java ByteChannelFileDemo
```

**Stdout chính xác nếu I/O thành công:**

```text
Kich thuoc tep: 3
Doc lai: ABC
Channel dang mo: true
```

Vì sao có thể đọc lại?

1. `write` chuyển ba byte `A B C` xuống tệp và làm position của channel tệp tiến tới offset 3.
2. `file.position(0)` đặt lại offset trước khi đọc. `ByteChannel` thuần túy **không có** phương thức di chuyển vị trí này.
3. `read` đọc ba byte từ tệp vào `incoming` (đã cấp 3 byte).
4. `incoming.flip()` thiết lập vùng có dữ liệu để decoder lấy đúng ba byte.
5. `io.isOpen()` trả về `true` tại thời điểm kiểm tra bên trong `try`. Khi ra khỏi `try-with-resources`, channel được đóng.

`SeekableByteChannel` và `FileChannel` có thêm trạng thái vị trí tệp, khác với `ByteBuffer.position()`. **Hai vị trí này là hai trạng thái khác nhau**: position của buffer điều khiển byte nào trong bộ nhớ được chuyển; position của tệp điều khiển offset tệp nơi I/O diễn ra.

## 8. ByteBuffer là phần không thể tách rời của hợp đồng channel

`ReadableByteChannel.read(dst)` và `WritableByteChannel.write(src)` đều làm việc với một `ByteBuffer` đã được chuẩn bị đúng:

```text
Đọc từ channel vào buffer:
  buffer.clear() --> channel.read(buffer) --> buffer.flip()
  --> ứng dụng gọi buffer.get(...)

Ghi từ buffer ra channel:
  buffer.clear() --> ứng dụng buffer.put(...) --> buffer.flip()
  --> channel.write(buffer) (lặp/đợi đến khi xử lý phần cần gửi)
```

Với **chu trình truyền tiếp từ nguồn sang đích**, ví dụ `ChannelCopyDemo`, mỗi lần nhận một phần dữ liệu cần:

1. Buffer có không gian ghi: `position < limit`.
2. Channel nguồn `read(buffer)` và trả về số byte nhận.
3. Nếu đã nhận byte, `flip()` để limit thành số byte đã chuẩn bị.
4. Channel đích `write(buffer)` tới khi tiêu thụ hết hoặc tạm ngừng do chưa sẵn sàng.
5. Khi đã xử lý toàn bộ, dùng `clear()`; nếu cần giữ byte chưa xử lý thì dùng `compact()`.

| Phương thức Buffer | Ảnh hưởng tới state | Khi nào dùng |
| --- | --- | --- |
| `flip()` | `limit=position` cũ, `position=0` | Chuyển từ chuẩn bị dữ liệu sang tiêu thụ dữ liệu |
| `clear()` | `position=0, limit=capacity` | Bỏ vùng dữ liệu cũ đã hoàn thành |
| `compact()` | Giữ bytes chưa tiêu thụ ở đầu, `position=remaining` cũ, `limit=capacity` | Cần tiếp tục ghi thêm mà giữ phần dư |
| `remaining()` | `limit-position` | Xác định byte có thể chuyển trong lần này |
| `hasRemaining()` | `position<limit` | Kiểm tra còn dữ liệu hay không |

**`clear()` không xóa byte thực tế**. Khi buffer được dùng để chứa dữ liệu nhạy cảm, việc tái sử dụng/giải phóng bộ nhớ cần chính sách riêng.

### Lỗi rất thường gặp: đọc liên tục mà quên giải phóng chỗ

Giả sử channel đọc đủ 8 byte vào `ByteBuffer.allocate(8)`. Lúc đó position=limit=8; lần gọi `read(buffer)` kế tiếp **không còn chỗ**. Muốn nhận thêm, phải xử lý các byte có sẵn rồi `clear()` hoặc `compact()`. Không kết luận channel đã EOF chỉ vì nó không đưa thêm dữ liệu khi buffer đã đầy.

## 9. Blocking, non-blocking và tình huống không có tiến độ

Bản thân `ReadableByteChannel`, `WritableByteChannel` và `ByteChannel` **không cung cấp API cấu hình non-blocking**. Những chức năng như `configureBlocking(false)` thuộc `SelectableChannel` và các triển khai liên quan, chẳng hạn `SocketChannel`. `FileChannel` dùng I/O tệp và không phải `SelectableChannel`.

Với blocking channel, lời gọi thường chờ được dữ liệu hoặc đủ điều kiện xử lý theo hợp đồng của lớp đó; **không có bảo đảm mỗi lần đọc lấp đầy buffer, hay mỗi lần ghi hoàn thành toàn bộ dữ liệu nguồn**.

Với non-blocking channel:

- `read(dst) == 0`: có thể chưa có byte nào sẵn sàng; nếu buffer có chỗ, đăng ký/readiness `OP_READ` hoặc cơ chế tương đương.
- `write(src) == 0`: phía nhận chưa tiếp nhận thêm; giữ nguyên phần chưa gửi và chờ readiness `OP_WRITE` khi thích hợp.
- `read(dst) == -1`: EOF, cần kết thúc/đóng nửa đọc theo giao thức.

Không biến cả ba trường hợp thành cùng một trạng thái “xong”. Đặc biệt, chờ readiness phải phù hợp điều kiện kết nối và giao thức; ví dụ `SocketChannel` có thể có trạng thái kết nối chưa hoàn tất và truyền dữ liệu không đồng bộ.

### Khung thuật toán gửi non-blocking (minh họa)

```java
// Pseudocode gần Java: src đã ở read mode, channel non-blocking.
// Phương thức này chưa tự xử lý Selector và không chạy độc lập.
while (src.hasRemaining()) {
    int n = channel.write(src);

    if (n == 0) {
        // Lưu src/position, đăng ký cơ hội ghi tiếp;
        // kết thúc lượt xử lý hiện tại, không spin.
        break;
    }
}
```

Không gọi `src.clear()` sau khi chỉ mới gửi một phần. Những byte còn lại nằm trong `[src.position(), src.limit())`. Sau khi được báo có thể ghi tiếp, dùng lại cùng buffer hoặc lưu trạng thái tương đương; nếu cần nhận thêm dữ liệu trước đó, phải phối hợp `compact()` và `flip()` đúng pha.

## 10. EOF, close và lỗi I/O

Hợp đồng **read** và **write** khác nhau:

- **`read` trả về `-1`** khi kết thúc nguồn; đó là một giá trị bình thường của API, không mặc nhiên là lỗi I/O.
- **`write` không dùng `-1` báo EOF**. Nếu phía nhận đóng hoặc xảy ra lỗi, thao tác có thể ném `IOException` hay ngoại lệ cụ thể hơn.
- **`close()`** đóng channel. `isOpen()` phản ánh trạng thái mở/đóng của channel; một channel đã đóng không được “mở lại” bằng cách gọi `read` hoặc `write`.
- **`ClosedChannelException`** có thể phát sinh nếu thao tác khi channel đã đóng. Trong vài trường hợp, đóng không đồng bộ hoặc ngắt luồng lúc đang I/O có thể dẫn đến `AsynchronousCloseException` hoặc `ClosedByInterruptException` tùy triển khai.
- **`NonReadableChannelException` / `NonWritableChannelException`** xuất hiện trên các loại channel hỗ trợ cấu hình quyền đọc/ghi nhưng được mở với mode không phù hợp.

Tốt nhất quản lý vòng đời channel bằng `try-with-resources`:

```java
try (ReadableByteChannel source = /* kênh đã khởi tạo */) {
    // Đọc từ source, xử lý EOF và IOException.
}
```

Đây là **khung minh họa** và không phải chương trình biên dịch độc lập vì thiếu biểu thức tạo channel. Việc đóng channel cũng có thể đóng luồng/tài nguyên gốc khi dùng adapter như `Channels.newChannel(stream)`; cần thiết kế ownership tài nguyên cho rõ.

Không nên dùng `isOpen()` làm kiểm tra bảo đảm lời gọi I/O tiếp theo thành công: giữa hai lần gọi, channel có thể được đóng bởi luồng khác. Luôn xử lý ngoại lệ tại chính thao tác đọc/ghi.

## 11. Channel và Stream: khi nào dùng cái nào?

Java IO cổ điển có `InputStream.read(byte[])` và `OutputStream.write(byte[])`; NIO channel đọc/ghi qua `ByteBuffer`. Cả hai đều có chỗ sử dụng.

| Khía cạnh | `InputStream` / `OutputStream` | Byte channels |
| --- | --- | --- |
| Giao diện dữ liệu | Byte đơn, `byte[]` | `ByteBuffer` |
| Điều khiển vùng chuyển | Offset và length trên mảng | `position`, `limit`, `remaining` |
| Phối hợp NIO | Thông qua adapter hoặc API khác | Tương tác tự nhiên với buffer, một số channel hỗ trợ selector |
| Đọc/ghi chưa đủ | Vẫn phải xử lý theo hợp đồng từng API | Phải xử lý số byte trả về, partial write, EOF |
| Vị trí ngẫu nhiên | Không là khả năng chung của mọi stream | `SeekableByteChannel`/`FileChannel` hỗ trợ khi cần |

Có thể chuyển đổi qua `Channels`:

- `Channels.newChannel(InputStream)` → `ReadableByteChannel`.
- `Channels.newChannel(OutputStream)` → `WritableByteChannel`.
- `Channels.newInputStream(ReadableByteChannel)` → `InputStream`.
- `Channels.newOutputStream(WritableByteChannel)` → `OutputStream`.

Các adapter giúp tái sử dụng thành phần IO, **không tự biến nguồn/đích thành non-blocking** hoặc cho phép mọi channel tham gia `Selector`. Việc dùng stream hay channel còn phụ thuộc định dạng, độ phức tạp, chính sách blocking, khả năng tái sử dụng buffer và đo đạc hiệu năng thực tế; không mặc định NIO luôn nhanh hơn IO truyền thống trong mọi tải công việc.

## 12. Các interface mở rộng trong hệ sinh thái NIO

Sau khi hiểu ba interface căn bản, có thể nhận diện thêm một số khả năng mở rộng:

- **`ScatteringByteChannel`**: đọc dữ liệu từ một channel vào nhiều `ByteBuffer` theo thứ tự.
- **`GatheringByteChannel`**: ghi dữ liệu từ nhiều `ByteBuffer` ra một channel theo thứ tự.
- **`SeekableByteChannel`**: bổ sung vị trí và kích thước của tài nguyên có thể truy cập ngẫu nhiên.
- **`FileChannel`**: channel cho I/O tệp, mở rộng các khả năng đọc/ghi và vị trí, có thêm thao tác truyền dữ liệu, khóa tệp và ánh xạ bộ nhớ.
- **`SocketChannel`**: channel cho kết nối socket, hỗ trợ cả đọc và ghi cùng cơ chế non-blocking/selector.

Những khả năng này có **hợp đồng riêng**; `ByteChannel` không tự cung cấp thao tác `position(long)`, `transferTo`, `map`, `configureBlocking` hoặc `register`. Khi chương trình cần chức năng cụ thể, khai báo kiểu interface/lớp phù hợp thay vì giả định mọi channel đều có cùng API.

## 13. Bẫy API và cách tránh

| Sai lầm | Tác động | Cách làm đúng |
| --- | --- | --- |
| Dùng `channel.read(buffer)` một lần rồi tin đã đọc đủ | Mất dữ liệu nếu chỉ nhận một phần | Theo dõi số byte trả về và lặp/đợi theo giao thức |
| Coi `read == 0` là EOF | Kết thúc sớm, đặc biệt ở non-blocking | **Chỉ `-1` báo EOF** |
| Dùng vòng lặp vô hạn khi `read/write == 0` | Busy spin, tốn CPU | Dùng readiness/selector hoặc cơ chế đợi phù hợp |
| Chỉ gọi `write` một lần rồi `clear` | Bỏ các byte chưa gửi | Giữ phần `remaining` và viết tiếp |
| Quên `flip` sau `put` hoặc sau `read` vào buffer | Sai vùng byte đưa ra ngoài hoặc giải mã | Chuyển sang read mode trước khi tiêu thụ |
| `read` vào buffer đang đầy | Không còn chỗ nhận byte | Xử lý trước rồi `clear` hoặc `compact` |
| Mong `ByteChannel` có `seek` | API không định nghĩa seek | Dùng `SeekableByteChannel`/`FileChannel` |
| Gọi `write` qua `FileChannel` mở READ only | `NonWritableChannelException` | Mở với `WRITE` nếu cần ghi |
| Nghĩ channel luôn an toàn khi dùng từ nhiều luồng | Phụ thuộc triển khai và cách chia sẻ buffer | Kiểm tra hợp đồng thread-safety và quản lý sở hữu buffer |
| Không đóng channel | Rò rỉ tài nguyên | `try-with-resources` |
| Gọi `isOpen` rồi tin thao tác I/O chắc chắn thành công | Channel có thể bị đóng đồng thời | Xử lý ngoại lệ tại `read`/`write` |
| Nghĩ ByteChannel tự giải mã UTF-8 | Channel chuyển **byte**, không chuyển văn bản | Dùng `CharsetDecoder`/`CharsetEncoder` hoặc codec phù hợp |

## 14. Câu hỏi tự kiểm tra

**Tình huống A.** Một `ReadableByteChannel` chuyển hai byte vào `ByteBuffer.allocate(8)` mới tạo. Hỏi `position` và `limit` của buffer sau `read`?

**Đáp án:** Nếu `read` thực sự trả về `2`, `position=2` và `limit=8`. Gọi `flip()` tạo `position=0`, `limit=2` để đọc đúng hai byte đã nhận. Không được mặc định hai byte này là một ký tự hoặc một thông điệp hoàn chỉnh.

**Tình huống B.** `WritableByteChannel.write(src)` trả về 3 trong khi trước đó `src.remaining()==9`. Hỏi còn bao nhiêu byte chưa gửi?

**Đáp án:** Còn `9 - 3 = 6` byte. `src.position()` tăng 3; `src.limit()` giữ nguyên. Không được gọi `clear` rồi quên phần dữ liệu chưa gửi.

**Tình huống C.** Một `SocketChannel` non-blocking có buffer còn chỗ nhưng `read(dst)` trả về 0. Có thể kết luận peer đã đóng kết nối không?

**Đáp án:** Không. Giá trị 0 có thể nghĩa là hiện chưa có byte sẵn sàng. `-1` mới biểu thị EOF trong hợp đồng đọc. Cần sử dụng readiness và trạng thái giao thức tương ứng.

**Tình huống D.** Biến kiểu `ByteChannel channel` có thể gọi `channel.position(0)` không?

**Đáp án:** Không, interface `ByteChannel` không có phương thức ấy. Nếu đối tượng là `SeekableByteChannel`, khai báo đúng kiểu hoặc dùng kiểu đã được kiểm chứng để gọi `position(long)`.

## 15. Ghi nhớ

- **`ReadableByteChannel` đọc từ nguồn vào `ByteBuffer`**: kết quả `>0` cho số byte đã nhận, `0` cho chưa tiến triển, **`-1` cho EOF**.
- **`WritableByteChannel` ghi từ `ByteBuffer` ra đích**: trả về số byte đã ghi, có thể nhỏ hơn `remaining()`. Không bỏ dữ liệu còn lại.
- **`ByteChannel` kết hợp đọc và ghi**, nhưng **không tự định nghĩa seek, non-blocking hay khả năng đảm bảo đọc/ghi đồng thời**.
- **`ByteBuffer.position/limit` quyết định phần byte được trao đổi**; `flip`, `clear`, `compact` là công cụ chuyển pha chính.
- Dùng `try-with-resources`, xử lý `IOException`, phân biệt **0 với EOF**, và lựa chọn lớp channel cụ thể theo nhu cầu.
- Với tệp cần truy cập vị trí, tìm đến **`SeekableByteChannel` hoặc `FileChannel`**; với socket non-blocking, phải tổ chức vòng đời readiness và dữ liệu chờ gửi đúng cách.

**Tài liệu chuẩn:** Java SE 17 API: `java.nio.channels.Channel`, `ReadableByteChannel`, `WritableByteChannel`, `ByteChannel`, `SeekableByteChannel`, `FileChannel`, `SocketChannel`, `Channels` và `java.nio.ByteBuffer`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-003">SeekableByteChannel và khả năng thay đổi vị trí</a>

<details>
<summary>Xem chi tiết</summary>

# SeekableByteChannel và khả năng thay đổi vị trí trong Java NIO

## 1. Vấn đề: đọc/ghi tuần tự chưa đủ

Khi đọc một luồng dữ liệu thông thường, chương trình tiêu thụ dữ liệu theo thứ tự và không nhất thiết có cách quay lại byte đã đọc. Với tệp, nhiều bài toán cần khả năng truy cập ngẫu nhiên (random access): đọc header ở đầu, nhảy tới một bản ghi ở cuối, sửa một trường nằm giữa tệp hoặc cắt bỏ phần cuối.

Trong Java NIO, **`SeekableByteChannel`** (`java.nio.channels.SeekableByteChannel`) cung cấp cơ chế đó: nó là một channel có thể đọc/ghi byte, **biết kích thước tài nguyên** và **cho phép thay đổi vị trí hiện tại**.

~~~text
Tệp:     a  b  c  d  e  f
Offset:  0  1  2  3  4  5
                               EOF tại offset 6

position = 2
            ↑
lệnh read tiếp theo bắt đầu từ byte 'c'
lệnh write tiếp theo bắt đầu ghi đè từ byte 'c'
~~~

**`position` không phải nội dung tệp.** Đó là con trỏ logic chỉ nơi thao tác đọc/ghi tương đối tiếp theo sẽ bắt đầu. Di chuyển con trỏ không tự thêm, xóa hoặc sao chép byte.

Phạm vi của bài học: hiểu hợp đồng `SeekableByteChannel`, quan hệ giữa `position` và `size`, cách đọc/ghi ở vị trí tùy ý, `truncate` và các tình huống dễ sai khi dùng API này.

## 2. Vai trò và các phương thức chính

`SeekableByteChannel` mở rộng `ByteChannel`, tức là thừa hưởng cả `ReadableByteChannel` và `WritableByteChannel`. Nó cũng là `Channel`, hỗ trợ đóng tài nguyên bằng `close()` và kiểm tra `isOpen()`. Tuy nhiên **chế độ mở** có thể cho phép chỉ đọc, chỉ ghi hoặc cả hai; việc một object có kiểu `SeekableByteChannel` không mặc nhiên có quyền thực hiện cả hai hướng.

Các phương thức thiết yếu:

| Phương thức | Tác dụng | Điều cần nhớ |
| --- | --- | --- |
| `long position()` | Lấy vị trí hiện tại | Đơn vị byte, offset bắt đầu từ 0 |
| `SeekableByteChannel position(long newPosition)` | Di chuyển vị trí đọc/ghi kế tiếp | `newPosition < 0` là không hợp lệ; có thể đặt vượt EOF |
| `long size()` | Lấy kích thước hiện tại của tài nguyên | Không phải vị trí; có thể thay đổi khi ghi/truncate |
| `SeekableByteChannel truncate(long size)` | Nếu kích thước mới nhỏ hơn kích thước hiện tại, cắt phần cuối | Không dùng để mở rộng tệp |
| `int read(ByteBuffer dst)` | Đọc byte từ vị trí hiện tại sang buffer | Trả số byte đọc, hoặc `-1` ở EOF |
| `int write(ByteBuffer src)` | Ghi byte từ buffer tại vị trí hiện tại | Có thể ghi đè byte cũ hoặc mở rộng tệp |
| `close()` | Đóng channel | Nên dùng `try-with-resources` |

Thông thường một `SeekableByteChannel` gắn với tệp, nhưng giao diện không hứa mọi implementation là một file trên ổ đĩa. `FileChannel` cũng triển khai `SeekableByteChannel` và bổ sung các tính năng riêng như thao tác định vị có chỉ số độc lập, memory mapping, file lock và `force`.

### Ba đại lượng dễ nhầm lẫn

Giả sử tệp có 6 byte:

~~~text
Nội dung: abcdef
size() = 6
position() = 2
ByteBuffer.remaining() = 4 (giả sử buffer có 4 chỗ đọc)
~~~

- `channel.size()`: kích thước dữ liệu trong **tệp/tài nguyên**.
- `channel.position()`: con trỏ hiện tại của **channel**.
- `buffer.position()`, `buffer.limit()`, `buffer.remaining()`: trạng thái của **buffer trong bộ nhớ**.

Mỗi lần `channel.read(buffer)` thành công với `n` byte, vị trí channel tăng `n` và vị trí buffer tăng `n`. Mỗi lần `channel.write(buffer)` thành công với `n` byte, hai vị trí cũng thường tăng `n` tương ứng. **Không nhầm vị trí của channel với vị trí của buffer.**

## 3. Tạo `SeekableByteChannel` và xác định quyền truy cập

Cách thông dụng với API `Files`:

~~~java
Path path = Path.of("data.bin");

// Chỉ đọc. Khi không truyền options, Files.newByteChannel mặc định là READ.
try (SeekableByteChannel channel = Files.newByteChannel(path)) {
    System.out.println(channel.size());
}
~~~

Đây là đoạn minh họa cần import `java.nio.file.*` và `java.nio.channels.SeekableByteChannel`; tệp `data.bin` phải tồn tại nếu muốn chạy trực tiếp.

Để vừa đọc vừa ghi một tệp đã có:

~~~java
try (SeekableByteChannel channel = Files.newByteChannel(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
    // Thao tác với channel trong khối try.
}
~~~

Một số `StandardOpenOption` cần phân biệt:

- **`READ`**: cho phép đọc dữ liệu.
- **`WRITE`**: cho phép ghi dữ liệu.
- **`CREATE`**: tạo tệp nếu chưa có khi dùng cùng tùy chọn ghi phù hợp.
- **`CREATE_NEW`**: chỉ tạo tệp mới; thất bại nếu mục tiêu đã tồn tại.
- **`TRUNCATE_EXISTING`**: khi mở để ghi, cắt tệp đã tồn tại về kích thước 0 ngay lúc mở; không sử dụng khi cần giữ dữ liệu để seek và sửa.
- **`APPEND`**: ghi nối vào cuối theo ngữ nghĩa provider; không thích hợp nếu mục tiêu là điều khiển vị trí ghi tùy ý.

Ví dụ: `Files.newByteChannel(path, READ, WRITE)` không tạo tệp nếu tệp chưa tồn tại. Có thể dùng `WRITE, CREATE` khi cần tạo mới hoặc tiếp tục ghi tùy nhu cầu.

**Lưu ý về `APPEND`:** trong chế độ append, trước mỗi thao tác ghi, channel có thể bị chuyển về cuối để nối thêm; việc kết hợp `APPEND` với `READ` hoặc `TRUNCATE_EXISTING` có thể không được provider hỗ trợ. Một số kết hợp được JDK mặc định từ chối. Không dùng `position(0)` để kỳ vọng ghi đè đầu tệp khi channel mở `APPEND`.

API dựa trên `Path` nên hệ thống tệp và provider của `path` quyết định cách mở channel, tính năng hỗ trợ và ngoại lệ thực tế.

## 4. `position(long)`: di chuyển con trỏ trước khi đọc/ghi

Ví dụ có tệp chứa `abcdef`:

~~~text
Offset     0  1  2  3  4  5
Byte       a  b  c  d  e  f
Size       6
Position   0
~~~

Đặt:

~~~java
channel.position(2);
~~~

Sau đó `channel.read(dst)` bắt đầu tại offset 2, tức là byte `c`. Nếu đọc được 3 byte, channel có `position() == 5`. Lệnh `channel.write(src)` cũng bắt đầu ghi tại vị trí hiện tại (nếu channel cho phép ghi).

Với tệp thông thường:

- `position(0)` quay về đầu tệp.
- `position(channel.size())` đặt con trỏ tại EOF để có thể ghi nối từ đó nếu mở ghi.
- `position(2)` cho phép đọc/ghi từ byte thứ ba.
- `position(-1)` ném `IllegalArgumentException`.
- `position(newPosition)` không thay đổi kích thước tệp chỉ vì vị trí lớn hơn `size()`.

### Đặt con trỏ vượt EOF có hợp lệ không?

Có. Ví dụ size = 6 và:

~~~java
channel.position(100);
~~~

`position()` lúc này có thể là 100 trong khi `size()` vẫn bằng 6. Với tệp có nội dung hữu hạn, một lời gọi `read` khi vị trí ở hoặc sau EOF thường trả `-1` mà không tự kéo dài tệp.

Nếu **ghi dữ liệu** tại offset 100, tệp có thể được mở rộng để chứa dữ liệu mới và khoảng trống giữa EOF cũ với vị trí ghi. Theo hợp đồng của `SeekableByteChannel`, **giá trị byte trong khoảng trống đó không được xác định chung cho mọi implementation**. Đừng tự giả định luôn được điền bằng byte `0`. Nếu định dạng tệp yêu cầu giá trị cụ thể, ứng dụng cần ghi/khởi tạo rõ ràng.

Lưu ý: vị trí dùng kiểu `long` để biểu diễn offset lớn; đừng ép về `int` khi làm việc với tệp dung lượng lớn.

## 5. Ghi tại vị trí tùy ý: ghi đè và mở rộng

Một channel mở `WRITE` dùng vị trí hiện tại để xác định nơi viết byte. Nếu vị trí nằm trong dữ liệu đã có, ghi sẽ **thay thế các byte nằm ở vùng đó**, không tự chèn byte làm các byte phía sau dịch sang phải.

~~~text
Tệp ban đầu:
Offset 0 1 2 3 4 5
       a b c d e f
size = 6

position(2)
write("XY")

Tệp sau ghi:
Offset 0 1 2 3 4 5
       a b X Y e f
size = 6
position = 4
~~~

Đây là **overwrite**, không phải thao tác chèn chuỗi tại index 2. Hai byte `c` và `d` đã bị thay bằng `X`, `Y`; các byte `e`, `f` còn nguyên.

Nếu ghi dài tới sau cuối tệp, kích thước tăng tới mức chứa byte cuối cùng được ghi. Nếu ghi trong phạm vi tệp và không có thay đổi đồng thời, `size()` có thể giữ nguyên.

### Tại sao không giả định một `write` ghi hết buffer?

Phương thức `int write(ByteBuffer src)` trả số byte đã ghi. Số đó có thể ít hơn `src.remaining()` trước lời gọi, tùy implementation và tình trạng I/O. Vì vậy, khi cần ghi toàn bộ payload, phải lặp tới khi `src.hasRemaining()` là `false` hoặc xử lý tình huống không có tiến triển.

~~~java
while (src.hasRemaining()) {
    int written = channel.write(src);
    if (written == 0) {
        // Tùy loại channel, đợi khả năng ghi rồi thử tiếp.
    }
}
~~~

Đây là mã minh họa cấu trúc vòng lặp: trong chương trình thực tế, cần cơ chế đợi/cancel/timeout phù hợp; vòng lặp gọi `write` mãi khi liên tục trả 0 sẽ tốn CPU.

Đối với channel tệp chặn thông thường và buffer hợp lệ, thao tác hay có tiến triển, nhưng vẫn nên lập trình theo số byte thực tế thay vì cho rằng một lần ghi luôn hoàn thành.

## 6. Đọc ở vị trí tùy ý và xử lý EOF

Với `read(ByteBuffer dst)`:

1. Byte được lấy từ vị trí hiện tại của channel.
2. Các byte chuyển vào buffer bắt đầu tại `dst.position()`.
3. `channel.position()` và `dst.position()` tăng theo số byte đọc thực sự.
4. Khi đến EOF trước khi đọc được byte nào, `read` trả `-1`; buffer không nhận byte.
5. `read` cũng có thể trả `0` trong hoàn cảnh không đọc được byte, ví dụ `dst` hết chỗ hoặc implementation không chặn chưa có dữ liệu.

Đọc lại từ đầu:

~~~java
channel.position(0);

ByteBuffer dst = ByteBuffer.allocate(16);
int count = channel.read(dst);
dst.flip();

// Lúc này dst chứa các byte đã đọc được từ channel,
// không nhất thiết đủ 16 byte.
~~~

Đây là mảnh minh họa sử dụng biến `channel` đã được mở phù hợp. `channel.position(0)` **không** tương đương `dst.clear()`: một bên di chuyển con trỏ của tài nguyên, bên kia đặt lại trạng thái buffer trong RAM.

Để đọc chính xác một lượng byte xác định, cần lặp và kiểm tra `-1`, `0` hoặc phần còn lại của buffer. Đặc biệt tránh gọi `new String(dst.array())` khi buffer chưa được điền đầy: việc đó có thể kèm byte chưa đọc từ tệp. Hãy `flip()` rồi giải mã đúng vùng dữ liệu hợp lệ, ví dụ `StandardCharsets.UTF_8.decode(dst)` nếu dữ liệu là UTF-8 hoàn chỉnh.

## 7. `size()` và `truncate(long)`

`size()` báo kích thước hiện tại của tệp/tài nguyên, độc lập với việc con trỏ đang ở đâu:

~~~text
size = 6; position = 2
size vẫn = 6 sau position(100)
~~~

`truncate(newSize)` có quy tắc quan trọng:

- Nếu `newSize < size()`, tệp bị cắt còn `newSize` byte, mất phần đuôi.
- Nếu `newSize >= size()`, kích thước **không tăng** chỉ nhờ `truncate`.
- Nếu vị trí hiện tại lớn hơn kích thước còn lại sau truncate, vị trí được đưa về kích thước mới; nếu vị trí đang nằm trong phạm vi giữ lại, nó được bảo toàn.
- `newSize < 0` là không hợp lệ và có thể ném `IllegalArgumentException`.
- Channel phải mở với quyền ghi phù hợp; nếu không, `NonWritableChannelException` có thể phát sinh.

Ví dụ:

~~~text
Ban đầu: "abcdef"
size = 6
position = 100

truncate(4)

Còn lại: "abcd"
size = 4
position = 4
~~~

**`truncate(100)` trên tệp 4 byte không kéo dài tệp thành 100 byte**. Nếu yêu cầu định dạng tệp cố định kích thước, ứng dụng phải ghi thêm dữ liệu (và xử lý mọi khoảng trống) theo quy tắc của mình.

Cần cẩn trọng với `TRUNCATE_EXISTING` khi mở channel: tùy chọn đó xóa nội dung ngay khi mở, khác với phương thức `truncate(...)` được gọi tại thời điểm do chương trình lựa chọn.

## 8. Ví dụ Java 17+ chạy độc lập: seek, overwrite, EOF và truncate

Chương trình sử dụng tệp tạm trên filesystem mặc định, vì thế chạy độc lập, không phụ thuộc đường dẫn cụ thể trên máy. Các chuỗi mẫu là ASCII để việc đếm offset theo byte hoàn toàn rõ ràng. Lưu thành `SeekableByteChannelDemo.java`:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SeekableByteChannelDemo {

    private static void writeFully(SeekableByteChannel channel, String data)
            throws IOException {
        ByteBuffer src = ByteBuffer.wrap(
                data.getBytes(StandardCharsets.US_ASCII));
        while (src.hasRemaining()) {
            int n = channel.write(src);
            if (n == 0) {
                throw new IOException("No write progress");
            }
        }
    }

    private static String readFully(SeekableByteChannel channel, int length)
            throws IOException {
        ByteBuffer dst = ByteBuffer.allocate(length);
        while (dst.hasRemaining()) {
            int n = channel.read(dst);
            if (n == -1) {
                break;
            }
            if (n == 0) {
                throw new IOException("No read progress");
            }
        }
        dst.flip();
        return StandardCharsets.US_ASCII.decode(dst).toString();
    }

    public static void main(String[] args) throws IOException {
        Path temp = Files.createTempFile("seekable-demo-", ".bin");

        try {
            try (SeekableByteChannel channel = Files.newByteChannel(
                    temp,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                System.out.println("initial: position=" + channel.position()
                        + ", size=" + channel.size());

                writeFully(channel, "abcdef");
                System.out.println("after write: position=" + channel.position()
                        + ", size=" + channel.size());

                channel.position(2);
                writeFully(channel, "XY");
                System.out.println("after overwrite: position=" + channel.position()
                        + ", size=" + channel.size());

                channel.position(0);
                System.out.println("content=" + readFully(channel, 6));
                System.out.println("after read: position=" + channel.position());

                channel.position(100);
                int result = channel.read(ByteBuffer.allocate(1));
                System.out.println("read beyond EOF=" + result);
                System.out.println("position after EOF=" + channel.position());

                channel.truncate(4);
                System.out.println("after truncate: position=" + channel.position()
                        + ", size=" + channel.size());

                channel.position(0);
                System.out.println("truncated content=" + readFully(channel, 4));

                channel.position(10);
                System.out.println("seek beyond EOF: position=" + channel.position()
                        + ", size=" + channel.size());
                writeFully(channel, "!");
                System.out.println("after extension: position=" + channel.position()
                        + ", size=" + channel.size());
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SeekableByteChannelDemo.java
java SeekableByteChannelDemo
~~~

Stdout khi chạy với provider tệp mặc định thông dụng hỗ trợ các thao tác trên:

~~~text
initial: position=0, size=0
after write: position=6, size=6
after overwrite: position=4, size=6
content=abXYef
after read: position=6
read beyond EOF=-1
position after EOF=100
after truncate: position=4, size=4
truncated content=abXY
seek beyond EOF: position=10, size=4
after extension: position=11, size=11
~~~

Cách đọc kết quả:

1. Tệp tạm mới có kích thước 0. `writeFully("abcdef")` ghi 6 byte và đưa `position` về 6.
2. `position(2)` rồi ghi `XY` đè đúng hai byte ở offset 2 và 3. Tệp vẫn dài 6 byte; sau ghi `position=4`.
3. Đưa `position` về 0 rồi đọc 6 byte để xác nhận nội dung `abXYef`. Hàm `readFully` chỉ giải mã vùng buffer đã thực sự nhận dữ liệu.
4. Đặt `position=100` dù tệp có 6 byte. Đọc thêm 1 byte trả `-1` vì EOF; vị trí vẫn là 100.
5. `truncate(4)` cắt bớt đuôi, đồng thời kéo `position` từ 100 về 4. Đọc từ đầu xác nhận phần còn lại là `abXY`.
6. `position(10)` không đổi `size=4`; ghi một dấu `!` tại offset 10 mới tăng `size` thành 11, `position` thành 11. Ví dụ **không đọc hay đoán** giá trị các byte ở offset 4–9 vì giá trị gap không được API bảo đảm trên mọi provider.
7. `try-with-resources` đóng channel, còn `finally` xóa tệp tạm.

Hai hàm trợ giúp ở ví dụ có chủ ý ném `IOException` nếu channel không tiến triển. Cách này phù hợp để tránh vòng lặp vô tận trong ví dụ tệp cục bộ; với channel có khả năng không chặn và hoạt động bất đồng bộ, cần cơ chế điều phối riêng thay vì mặc định 0 nghĩa là lỗi.

## 9. `SeekableByteChannel` và `FileChannel`: dùng cái nào?

`FileChannel` triển khai `SeekableByteChannel`, vì vậy cũng hỗ trợ `position()`, `position(long)`, `size()`, `truncate(long)`, `read(ByteBuffer)` và `write(ByteBuffer)`.

`FileChannel` bổ sung các API hữu ích:

- `read(ByteBuffer dst, long position)`: đọc bắt đầu từ offset được cung cấp mà **không thay đổi vị trí hiện tại của channel**.
- `write(ByteBuffer src, long position)`: ghi tại offset được cung cấp mà **không thay đổi vị trí hiện tại của channel**.
- `map(...)`: ánh xạ một vùng tệp vào bộ nhớ.
- `transferTo(...)`/`transferFrom(...)`: truyền dữ liệu giữa channel thích hợp.
- `lock(...)`/`tryLock(...)`: khóa vùng tệp với ngữ nghĩa phụ thuộc hệ thống.
- `force(boolean metaData)`: yêu cầu đẩy cập nhật ra thiết bị lưu trữ theo hợp đồng của API.

**Chọn `SeekableByteChannel`** khi chỉ cần tập thao tác chung, muốn code làm việc với nhiều implementation/provider. **Chọn `FileChannel`** khi thực sự cần một khả năng mở rộng của `FileChannel`.

Ví dụ minh họa một phương thức đọc theo offset mà không sửa vị trí toàn cục, khi đã có `FileChannel channel` đang mở và quyền đọc phù hợp:

~~~java
ByteBuffer dst = ByteBuffer.allocate(4);
int n = channel.read(dst, 128); // Không làm đổi channel.position().
dst.flip();
~~~

Đây là mảnh code minh họa, cần import `java.nio.ByteBuffer`, và không giả định tệp có đủ 4 byte tại offset 128. `dst.position()` vẫn tăng theo số byte được đọc; chỉ **vị trí của FileChannel** là không đổi do lệnh đọc có offset.

Với `SeekableByteChannel` nói chung, bạn không có overload `read(dst, offset)`; phải thao tác `position(offset)` rồi `read(dst)`. Nếu nhiều tác vụ cùng chia sẻ một channel, hai lệnh riêng có thể bị xen kẽ, gây đọc/ghi sai offset. Cần quản lý đồng bộ/ownership của channel hoặc cân nhắc API định vị độc lập của `FileChannel`.

## 10. Vấn đề đồng thời, sửa kích thước và tính nhất quán

Các thao tác seek và I/O có thể tạo race condition khi cùng sử dụng một channel:

~~~text
Thread A: position(100)
Thread B: position(0)
Thread A: read(buffer)   → có thể đọc từ vị trí không mong muốn
~~~

Vì vậy, không nên tách `position(offset)` và `read/write` trên một channel chia sẻ rồi mặc định đó là một thao tác nguyên tử. Hãy quản lý truy cập của các thread hoặc sử dụng API đọc/ghi theo offset của `FileChannel` khi phù hợp. Đặc tính đồng thời chi tiết tùy loại channel/provider và môi trường.

Tương tự, `size()` là thông tin về kích thước **tại thời điểm gọi**. Một tiến trình khác có thể sửa tệp ngay sau đó; logic `if (position < size()) ...` không phải bảo đảm rằng byte sẽ còn tồn tại đến lúc đọc. Nếu ứng dụng cần nhất quán giữa nhiều tiến trình, phải thiết kế cơ chế phối hợp tệp thích hợp.

`truncate` có thể làm mất dữ liệu không thể khôi phục nếu chưa có bản sao hoặc thông tin phục hồi. Trong ứng dụng thực tế, xác minh đúng tệp, đúng kích thước cắt và chiến lược backup trước khi dùng trên tài liệu quan trọng.

Một số provider có thể là read-only hoặc không hỗ trợ đủ chế độ mở/thao tác, và có thể báo `UnsupportedOperationException`, `NonWritableChannelException`, `IOException` hoặc các ngoại lệ riêng. Không suy luận toàn bộ tính năng từ tên tệp hay scheme URI; sử dụng API và xử lý lỗi đúng ngữ cảnh.

## 11. Lỗi phổ biến

### Nhầm `position` với `size`

**Sai:** cho rằng đặt `position(100)` sẽ tự tạo tệp dài 100 byte.

**Đúng:** chỉ đặt con trỏ; size chỉ tăng khi thực sự ghi dữ liệu vượt EOF (hoặc có cơ chế khác tác động).

### Quên `ByteBuffer.flip()`

Sau `read(buffer)`, buffer vẫn có `position` ở cuối vùng đã điền. Muốn đọc dữ liệu từ buffer phải `flip()` để đặt `limit=position cũ`, `position=0`. `channel.position(0)` chỉ thay vị trí **channel**, không tự flip buffer.

### Muốn chèn byte nhưng lại overwrite

Ghi `XY` tại offset 2 của `abcdef` tạo `abXYef`. Nếu cần chèn thêm byte làm dữ liệu phía sau dịch chuyển, phải xây giải thuật dịch phần đuôi hoặc tạo tệp mới; `write` không có ngữ nghĩa insert.

### Giả định `truncate` có thể mở rộng tệp

`truncate(100)` không làm tệp 6 byte thành 100 byte. Để mở rộng cần ghi hoặc cơ chế hỗ trợ cụ thể và xác định chính sách byte điền.

### Mở nhầm chế độ `TRUNCATE_EXISTING`

Trước khi seek/overwrite một file hiện hữu, không được thêm `TRUNCATE_EXISTING` theo thói quen; option này làm mất dữ liệu **ngay lúc mở**.

### Bỏ qua số byte thực tế đã đọc/ghi

Đọc/ghi với ByteBuffer có thể chuyển ít byte hơn `remaining()`. Luôn xem giá trị trả về và trạng thái buffer. Nếu mong đủ `n` byte, phải lặp, xử lý EOF hoặc điều kiện không tiến triển.

### Dùng `APPEND` rồi mong `position(long)` điều khiển vị trí ghi

`APPEND` được thiết kế cho ghi nối vào cuối; không dùng khi cần ghi đè offset bất kỳ. Hãy mở quyền `WRITE` phù hợp và bỏ `APPEND`.

### Đếm offset theo ký tự Unicode

`position` tính **byte**, không tính ký tự Java hay Unicode code point. Với UTF-8, chuỗi “Việt” chứa nhiều byte hơn số ký tự trông thấy. Khi seek theo record text, cần định dạng có byte offset được lưu/đánh chỉ mục rõ ràng, hoặc giải mã tuần tự; không lấy chỉ số ký tự làm offset byte một cách máy móc.

## 12. Bài tập tự kiểm tra

1. Tệp `0123456789` có size 10. Đặt `position(3)` rồi ghi 2 byte `AB`. Kết quả? **`012AB56789`, size 10, position 5**.
2. Tệp size 10 và position 4; gọi `truncate(7)`. Kết quả? **size 7, position 4**. Nếu position là 9 trước khi truncate thì sau đó position thành 7.
3. Tệp size 10; gọi `truncate(20)`. Kết quả? **size vẫn 10**.
4. Tệp size 5; gọi `position(12)` rồi đọc. Điều gì xảy ra? Với tệp thông thường, **`read` trả -1**, size không đổi. Vị trí không tự quay lại EOF.
5. Tệp `abcdef`, con trỏ ở 0 và buffer vừa `read` 4 byte. `position` của channel là bao nhiêu nếu đủ 4 byte? **4**. Sau đó `dst.flip()` có làm channel quay về 0 không? **Không**.
6. Vì sao `FileChannel.read(dst, 100)` giúp giảm rủi ro khi nhiều người đọc các offset khác nhau? Vì nó dùng **offset riêng cho lần đọc** mà không thay đổi vị trí chung của channel. Các yếu tố đồng thời khác vẫn cần được quản lý.
7. Muốn sửa metadata ở offset cố định của tệp nhị phân lớn mà giữ nguyên phần còn lại, bạn sẽ mở channel bằng mode nào và thực hiện những bước gì? **Mở READ/WRITE phù hợp, định vị offset byte chính xác, ghi byte đúng độ dài, kiểm tra kết quả và đóng channel**; không dùng `TRUNCATE_EXISTING` hoặc `APPEND`.

**Ghi nhớ:** `SeekableByteChannel` cho phép **đọc/ghi ở vị trí tùy ý** nhờ `position(long)`, quan sát kích thước với `size()` và cắt đuôi bằng `truncate(long)`. Di chuyển vị trí không đổi nội dung; đọc/ghi mới làm vị trí tiến lên. Luôn tách trạng thái **channel** khỏi trạng thái **ByteBuffer**, hiểu quyền mở và kiểm tra số byte I/O thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-004">Đọc hoặc ghi không đủ toàn bộ buffer</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc hoặc ghi không đủ toàn bộ buffer trong Java NIO Channel

## 1. Vấn đề: một lần read/write không đồng nghĩa hoàn tất

Các API channel của Java NIO truyền byte qua `ByteBuffer`. Một lời gọi `read(buffer)` hoặc `write(buffer)` có thể xử lý **ít byte hơn số byte còn lại trong buffer**, ngay cả khi không có lỗi. Hiện tượng này thường được gọi là **partial read** hoặc **partial write** (đọc/ghi một phần).

Ví dụ: buffer có 8 byte cần gửi nhưng `channel.write(buffer)` chỉ chấp nhận 3 byte trong lần gọi đầu. Phương thức trả về `3`, còn 5 byte chưa gửi. Nếu bỏ buffer đi hoặc gọi `clear()` ngay, ứng dụng sẽ mất dữ liệu.

~~~text
ByteBuffer trước lần write:
       position=0                    limit=8
            v                            v
         [ A B C D E F G H ]   remaining=8

channel.write(buffer) trả 3:
               position=3             limit=8
                    v                    v
         [ A B C D E F G H ]   remaining=5
           đã gửi ^ ^ ^
                 Các byte D..H vẫn đang chờ gửi.
~~~

Điểm cốt lõi là: **giá trị trả về** cho biết số byte thực sự chuyển trong lần gọi; **`position`** của buffer tiến tương ứng. Chương trình cần tiếp tục từ vị trí còn lại cho đến khi đạt điều kiện hoàn tất hoặc sự kiện cần xử lý như EOF, tạm thời chưa sẵn sàng, timeout, hủy thao tác hay lỗi I/O.

Chủ đề này nằm trong nhóm **NIO Channel**, không giới hạn ở đọc tệp: quan trọng nhất với `SocketChannel` ở chế độ không chặn, nhưng cũng phải tôn trọng hợp đồng API khi làm việc với `FileChannel`, `ReadableByteChannel`, `WritableByteChannel` và các channel tùy biến.

## 2. Hợp đồng read và write

### 2.1. Đọc từ ReadableByteChannel vào ByteBuffer

Với `int n = channel.read(dst)`:

| Kết quả | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `n > 0` | Chính xác `n` byte đã được chép vào `dst`; `position` tăng `n`. | Xử lý lượng đã nhận; đọc tiếp nếu còn thiếu thông điệp. |
| `n == 0` | Không byte nào được chuyển trong lượt gọi này. | Không suy ra EOF; với non-blocking channel, đợi tín hiệu sẵn sàng hoặc có cơ chế lập lịch lại. |
| `n == -1` | Channel báo **end-of-stream (EOF)**: không còn byte tiếp theo. | Nếu đang yêu cầu một thông điệp cố định chưa đủ byte, báo thiếu/truncated; nếu là luồng đọc đến EOF, kết thúc đúng lúc. |
| Ném `IOException` | Có lỗi I/O. | Xử lý/đóng tài nguyên theo hợp đồng ứng dụng. |

`read(dst)` có thể đọc tối đa `dst.remaining()` byte. Với `dst.remaining() == 0`, lời gọi không có chỗ để nhận byte và không nên dùng nó để dò trạng thái dữ liệu. Khi buffer có chỗ, vẫn **không có bảo đảm** lần đọc sẽ điền hết chỗ trống.

Với `SocketChannel` ở **blocking mode**, nếu còn chỗ trong buffer, phương thức thường chờ cho đến khi đọc được ít nhất một byte, gặp EOF hoặc lỗi, nhưng không có nghĩa chờ đủ toàn bộ buffer. Ở **non-blocking mode**, `read` có thể trả `0` khi socket hiện chưa có dữ liệu.

### 2.2. Ghi từ ByteBuffer ra WritableByteChannel

Với `int n = channel.write(src)`:

| Kết quả | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `n > 0` | Chính xác `n` byte đã được tiêu thụ từ `src`; `position` tăng `n`. | Nếu `src.hasRemaining()`, tiếp tục gửi. |
| `n == 0` | Lần này chưa ghi được byte nào. | Nếu non-blocking, đợi channel sẵn sàng ghi; tránh vòng lặp chạy liên tục không tiến triển. |
| Ném `IOException` | Lỗi truyền hoặc ghi dữ liệu. | Chọn retry có kiểm soát, xử lý thất bại hay đóng kết nối theo nghiệp vụ. |

**Không có giá trị `-1` thông thường cho `write` để biểu thị EOF.** Cách dùng `-1` là đặc trưng của các thao tác đọc. Ngay cả `write` trả số dương, điều đó có nghĩa byte đã được channel tiếp nhận theo hợp đồng của nó; không tự suy ra dữ liệu đã được lưu bền vững trên đĩa hoặc peer từ xa đã nhận/xử lý toàn bộ thông điệp.

Ví dụ, `WritableByteChannel.write` cũng được phép ghi ít hơn `src.remaining()` byte. Ở socket, lượng byte đi được còn phụ thuộc trạng thái bộ đệm mạng. Ở tệp, số byte ghi có thể thay đổi theo provider, môi trường hoặc lỗi I/O. **Không viết mã giả định mọi channel.write luôn ghi hết.**

## 3. ByteBuffer liên kết với tiến độ I/O như thế nào?

Các thao tác tương đối trên buffer dựa vào ba giá trị:

~~~text
0 <= position <= limit <= capacity
remaining() == limit - position
hasRemaining() == (remaining() > 0)
~~~

- Với `read(dst)`, channel **đưa dữ liệu vào buffer** ở vị trí hiện tại rồi tăng `position` theo số byte nhận.
- Với `write(src)`, channel **lấy dữ liệu từ buffer** bắt đầu ở vị trí hiện tại rồi tăng `position` theo số byte đã lấy.
- `limit` là ranh giới exclusive; `capacity` là sức chứa vật lý/logic của buffer. Một channel không tự hiểu đâu là số byte “hợp lệ” do ứng dụng vừa ghi trừ khi ứng dụng đặt trạng thái đúng.

Quy trình thường gặp:

~~~text
Đọc từ channel vào buffer:
  clear hoặc compact  -> read(...) -> flip() -> xử lý -> clear/compact

Ghi dữ liệu đã có ra channel:
  put(...) -> flip() -> write(...) -> nếu remaining > 0 thì giữ để ghi tiếp
~~~

**`flip()`** chuyển buffer từ trạng thái vừa được ghi vào sang trạng thái đọc: đặt `limit` bằng `position` cũ, sau đó đặt `position=0`. **`clear()`** đặt `position=0, limit=capacity` để chuẩn bị ghi đè dữ liệu mới; nó không xóa byte thật. **`compact()`** đưa phần byte chưa đọc về đầu rồi chuẩn bị cho lần ghi tiếp, nên hữu ích khi thông điệp chưa đủ hoặc ghi socket mới tiêu thụ một phần.

Không dùng `flip()` lặp đi lặp lại trên buffer **đang chờ write nốt**: sau partial write, buffer đã ở trạng thái đọc và `position` trỏ đúng byte tiếp theo. Nếu gọi `flip()` thêm lần nữa, vùng đọc bị thay đổi, dễ khiến dữ liệu bị lặp hoặc mất.

## 4. Đọc đầy đủ một lượng byte cố định

Giả sử giao thức quy định mỗi header dài chính xác 4 byte. **Một lần `read(header)` là chưa đủ**, vì có thể chỉ nhận được 1, 2 hoặc 3 byte. Với channel blocking mà ứng dụng kỳ vọng mỗi lần đọc có thể tiến triển, ta có thể lặp theo `hasRemaining()`:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

static void readFullyBlocking(ReadableByteChannel channel, ByteBuffer dst)
        throws IOException {
    while (dst.hasRemaining()) {
        int count = channel.read(dst);

        if (count == -1) {
            throw new EOFException("Đã hết luồng trước khi nhận đủ byte");
        }
        if (count == 0) {
            throw new IOException("Không có tiến triển; kiểm tra mode/channel");
        }
    }
}
~~~

Đây là **một phương thức tiện ích**, không phải chương trình Java chạy độc lập. Phương thức được viết cho những trường hợp **blocking có kỳ vọng tiến triển**, không dùng nguyên xi làm vòng lặp non-blocking hoặc cho các provider có thể trả `0` hợp lệ nhiều lần.

Ví dụ sử dụng khi `channel` và byte order đã được xác định:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);
readFullyBlocking(channel, header);

header.flip();
int payloadLength = header.getInt();
~~~

Nếu `readFullyBlocking` không báo lỗi, `header` đã chứa đủ 4 byte. Lệnh `flip` đặt limit đúng vùng nhận trước khi `getInt`. Số `payloadLength` chỉ là một số đọc từ dữ liệu: **cần xác thực nó** (không âm, không vượt kích thước tối đa, không gây tràn hoặc cấp phát quá lớn) trước khi dùng làm kích thước buffer tiếp theo.

### 4.1. Đọc đúng N byte khác với đọc cho đến EOF

Có hai mục tiêu khác nhau:

- **Đọc đúng N byte:** kết thúc khi đã nhận N byte; gặp `-1` trước đó là thông điệp bị thiếu.
- **Đọc cả luồng cho đến EOF:** nhận từng chunk tùy ý; `-1` có thể là kết thúc hoàn toàn hợp lệ.

Nếu đọc tệp chỉ để tính tổng số byte hoặc lưu vào một đích, không cần biết trước kích thước, dùng buffer theo từng chunk đến khi EOF. Nếu dữ liệu có framing (length-prefixed, delimiter, fixed-size record), phải thực hiện thêm bước ghép/giải mã khung; ranh giới giữa các lời gọi `read` **không** phải ranh giới thông điệp.

### 4.2. Đọc file theo từng buffer

Một dạng vòng lặp có thể dùng với `FileChannel`:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(4096);

    while (true) {
        int n = channel.read(buffer);
        if (n == -1) {
            break;
        }
        if (n == 0) {
            continue; // Phù hợp với ngữ cảnh file blocking ví dụ này.
        }

        buffer.flip();
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            // Xử lý byte b, ví dụ cập nhật checksum.
        }
        buffer.clear();
    }
}
~~~

Đây là **mảnh minh họa**, cần `java.nio.ByteBuffer`, `java.nio.channels.FileChannel`, `java.nio.file.Path`, `java.nio.file.StandardOpenOption` và xử lý `IOException`. Cách này giả định xử lý hết mọi byte của buffer ở mỗi vòng; khi cần giữ một phần frame đang dang dở, phải dùng `compact()` hoặc bộ tích lũy dữ liệu phù hợp.

Vòng `continue` khi `n==0` được nêu trong ngữ cảnh ví dụ **FileChannel kiểu blocking**; không sao chép vào non-blocking `SocketChannel` vì có thể gây busy-spin.

## 5. Ghi hết dữ liệu trong buffer

Với một channel **blocking** dự kiến ghi tiến triển, mẫu căn bản là:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

static void writeFullyBlocking(WritableByteChannel channel, ByteBuffer src)
        throws IOException {
    while (src.hasRemaining()) {
        int count = channel.write(src);

        if (count == 0) {
            throw new IOException("Không có tiến triển; kiểm tra mode/channel");
        }
    }
}
~~~

Giả sử trước khi gọi đã đặt buffer vào trạng thái đọc (ví dụ `flip()` sau `put`):

~~~java
ByteBuffer outgoing = ByteBuffer.wrap(
        "PING".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
writeFullyBlocking(channel, outgoing);
~~~

**Vì sao không gọi `clear` giữa các lần write?** Sau lần ghi thứ nhất, `src.position()` đã tự tăng đúng số byte được channel tiêu thụ. `src.hasRemaining()` cho biết còn gì phải gửi; lặp `write(src)` sẽ tiếp tục đúng từ phần chưa hoàn tất.

Nếu ứng dụng muốn sử dụng lại một buffer sau khi ghi **hết**, khi đó mới `clear()` để thêm dữ liệu mới. Nếu socket chỉ nhận được một phần và ứng dụng cần nối dữ liệu bổ sung ở cuối, `compact()` có thể giữ phần chưa gửi và chuyển sang trạng thái ghi để append tiếp; cần kiểm soát thứ tự byte khi làm việc này.

### 5.1. FileChannel và file output

`FileChannel.write(buffer)` cập nhật position của buffer theo số byte ghi. Mẫu lặp `while (buffer.hasRemaining())` vẫn là cách diễn đạt đúng khi cần chuyển **toàn bộ vùng dữ liệu còn lại** sang tệp.

Chú ý có sự khác nhau giữa:

1. Dữ liệu đã được `write` chấp nhận qua API.
2. Dữ liệu đã được hệ điều hành đưa xuống phương tiện lưu trữ.

Nếu có yêu cầu durability, hãy xem hợp đồng `FileChannel.force(boolean metaData)`. Lệnh này liên quan đến đồng bộ dữ liệu, **không** thay thế vòng lặp xử lý partial write, và không nên giả định mọi provider/phần cứng đều có cùng bảo đảm.

## 6. Non-blocking SocketChannel: 0 có thể là trạng thái bình thường

Khi `SocketChannel.configureBlocking(false)`, lời gọi `read` hoặc `write` có thể trả `0` dù kết nối chưa đóng và buffer vẫn còn chỗ/dữ liệu. Lý do thường là socket **chưa sẵn sàng ngay lúc đó**. Một vòng lặp liên tục gọi lại mà không chờ sẽ tốn CPU:

~~~java
// Không nên làm với non-blocking channel.
while (out.hasRemaining()) {
    socket.write(out); // Có thể liên tục trả 0 -> busy-spin.
}
~~~

Với NIO selector, cách tiếp cận là **giữ trạng thái buffer và đợi sự kiện phù hợp**:

- Khi có `OP_READ`, đọc những gì sẵn có, xử lý số byte trả về; nếu `read == 0` thì dừng lượt xử lý read hiện tại.
- Khi có dữ liệu đang chờ ghi, đăng ký `OP_WRITE`, thử ghi những byte còn lại. Nếu `write == 0`, giữ `position/limit` và đợi lượt `OP_WRITE` tiếp theo.
- Khi buffer ghi đã hết `remaining()`, bỏ quan tâm `OP_WRITE` cho đến khi lại có dữ liệu chờ; nếu giữ `OP_WRITE` liên tục, socket thường xuyên sẵn sàng ghi có thể khiến event loop thức dậy không cần thiết.
- Khi `read == -1`, peer đã báo kết thúc hướng đọc; xử lý phần dữ liệu còn tồn và trạng thái đóng kết nối theo giao thức (đối với TCP, cũng có khái niệm half-close).

Pseudocode ngắn minh họa xử lý một lần socket sẵn sàng ghi:

~~~java
// out đã ở trạng thái đọc, chứa dữ liệu cần gửi.
// key, socket là các biến thuộc event loop của ứng dụng.
if (key.isWritable()) {
    socket.write(out); // Có thể gửi 0, một phần hoặc toàn bộ.
    if (!out.hasRemaining()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        out.clear(); // Chỉ tái sử dụng khi không còn byte chờ gửi.
    }
}
~~~

Đây là **pseudocode tích hợp vào event loop**, không phải chương trình chạy độc lập; cần quản lý buffer hàng đợi, khóa/chủ sở hữu và hủy đăng ký phù hợp. Trong ứng dụng có nhiều thông điệp xếp hàng, thường phải lưu **một hoặc nhiều buffer chưa gửi hết** thay vì chỉ một buffer chung.

**Readiness không bảo đảm số byte:** tín hiệu `OP_READ` không có nghĩa có đủ cả frame; `OP_WRITE` không có nghĩa gửi được hết mọi byte. Selector chỉ thông báo điều kiện có thể thử thao tác, số byte thực chuyển vẫn đến từ `read`/`write`.

## 7. Ví dụ chạy được trên Java 17+: cố ý giới hạn mỗi lần read và write

Để kiểm chứng partial I/O chắc chắn, ví dụ này **không dựa vào may rủi của ổ đĩa hoặc mạng**. Nó cài hai channel nhỏ dựa trên các interface chuẩn:

- `ChunkedReadableChannel` cố ý cấp **tối đa 2 byte** cho mỗi lần `read`.
- `ChunkedWritableChannel` cố ý nhận **tối đa 3 byte** cho mỗi lần `write`.

Dữ liệu nguồn là chuỗi ASCII `HELLO-NIO`, có 9 byte. Do đó cần đúng **5 lần đọc có dữ liệu** (2+2+2+2+1) và **3 lần ghi** (3+3+3).

Lưu file `PartialChannelDemo.java`:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public class PartialChannelDemo {

    static final class ChunkedReadableChannel implements ReadableByteChannel {
        private final ByteBuffer source;
        private boolean open = true;
        private int calls;

        ChunkedReadableChannel(byte[] bytes) {
            this.source = ByteBuffer.wrap(bytes);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            if (!dst.hasRemaining()) {
                return 0;
            }
            if (!source.hasRemaining()) {
                return -1;
            }

            int n = Math.min(2, Math.min(dst.remaining(), source.remaining()));
            for (int i = 0; i < n; i++) {
                dst.put(source.get());
            }
            calls++;
            return n;
        }

        int calls() {
            return calls;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    static final class ChunkedWritableChannel implements WritableByteChannel {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean open = true;
        private int calls;

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            if (!src.hasRemaining()) {
                return 0;
            }

            int n = Math.min(3, src.remaining());
            byte[] batch = new byte[n];
            src.get(batch);
            output.write(batch);
            calls++;
            return n;
        }

        int calls() {
            return calls;
        }

        String text() {
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] payload = "HELLO-NIO".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length);

        try (ChunkedReadableChannel reader = new ChunkedReadableChannel(payload);
             ChunkedWritableChannel writer = new ChunkedWritableChannel()) {

            // Đọc đủ 9 byte: mỗi read chỉ trả tối đa 2 byte.
            while (buffer.hasRemaining()) {
                int n = reader.read(buffer);
                if (n == -1) {
                    throw new EOFException("Payload bị thiếu");
                }
                if (n == 0) {
                    throw new IOException("Read không tiến triển");
                }
            }

            System.out.println("readCalls=" + reader.calls());
            System.out.println("readPosition=" + buffer.position());

            buffer.flip();
            System.out.println("writeRemainingBefore=" + buffer.remaining());

            // Ghi đủ 9 byte: mỗi write chỉ nhận tối đa 3 byte.
            while (buffer.hasRemaining()) {
                int n = writer.write(buffer);
                if (n == 0) {
                    throw new IOException("Write không tiến triển");
                }
            }

            System.out.println("writeCalls=" + writer.calls());
            System.out.println("writeRemainingAfter=" + buffer.remaining());
            System.out.println("output=" + writer.text());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PartialChannelDemo.java
java PartialChannelDemo
~~~

**Stdout chính xác của chương trình:**

~~~text
readCalls=5
readPosition=9
writeRemainingBefore=9
writeCalls=3
writeRemainingAfter=0
output=HELLO-NIO
~~~

Giải thích kết quả:

1. Buffer ban đầu có `capacity=9`, `position=0` và `limit=9`.
2. Reader chỉ giao 2 byte mỗi lượt (lượt cuối 1 byte), nên cần đúng 5 lượt để `position=9`.
3. `flip()` chuyển buffer sang chế độ đọc: `position=0`, `limit=9`, `remaining=9`.
4. Writer tiêu thụ 3 byte mỗi lượt; sau 3 lần ghi, `position=9` và `remaining=0`.
5. `output=HELLO-NIO` chứng minh không mất, không lặp byte mặc dù mỗi lần I/O đều chỉ chuyển một phần payload.

Hai channel tùy biến chỉ dùng để **minh họa hợp đồng một cách tái lập được**. Chúng luôn tiến triển khi còn dữ liệu và không mô phỏng mạng non-blocking, selector, timeout hay lỗi từ xa. Các nhánh kiểm tra `n==0` cố ý coi đó là bất thường trong ví dụ blocking mang tính kiểm chứng này.

## 8. Khi frame chưa đủ: giữ byte đang dang dở

Dữ liệu mạng là một **dòng byte**, nên một thông điệp có thể được chia ra nhiều lần `read`; ngược lại một lần `read` có thể nhận nhiều thông điệp liên tiếp. Nếu protocol dùng **frame có độ dài cố định**, bộ giải mã cần giữ số byte còn thiếu đến lượt tiếp theo.

Ví dụ frame bắt đầu bằng 4 byte length big-endian rồi đến `length` byte payload:

~~~text
Trên wire:
[ 00 00 00 05 ][ H E L L O ]
   length=5       payload

Lượt read #1: [ 00 00 00 ]                -> header chưa đủ
Lượt read #2: [ 05 H E ]                   -> đủ header, payload chưa đủ
Lượt read #3: [ L L O ]                    -> hoàn tất payload
~~~

Nếu parser bỏ qua ba byte header nhận ở lượt đầu hoặc decode payload ngay khi chưa đủ, kết quả sẽ sai. Có hai chiến lược điển hình:

1. **State machine + buffer đọc:** giữ trạng thái “đang đọc header” hoặc “đang đọc payload”, chỉ chuyển trạng thái khi phần tương ứng đã đầy đủ. Với blocking channel có thể dùng `readFullyBlocking`; với non-blocking channel giữ trạng thái giữa các sự kiện sẵn sàng đọc.
2. **Một buffer tích lũy và `compact()`:** sau `flip()`, parser tiêu thụ những frame hoàn chỉnh, để lại phần cuối chưa đủ byte; `compact()` giữ chúng lại để lượt `read` tiếp theo ghi nối vào.

Phác thảo vòng lặp non-blocking cho hướng đọc:

~~~java
int n = socket.read(input);  // input đang ở chế độ ghi.
if (n == -1) {
    handleEndOfStream();    // Phân biệt EOF sạch và frame bị cắt.
} else if (n > 0) {
    input.flip();
    parseAllCompleteFrames(input); // Chỉ lấy frame hoàn chỉnh.
    input.compact();               // Giữ phần byte còn lại.
}
~~~

Các hàm `handleEndOfStream` và `parseAllCompleteFrames` là **hàm ứng dụng tự xây dựng**, không phải API Java; đoạn mã cũng phải xử lý buffer đầy mà không giải mã được frame nào (ví dụ yêu cầu cấp buffer lớn hơn trong giới hạn cho phép hoặc từ chối frame quá lớn).

### Lỗi nguy hiểm: cấp phát theo length không kiểm tra

Khi nhận length từ mạng, phải kiểm tra hợp lệ trước khi cấp phát hoặc định vị buffer. Bốn byte length có thể biểu diễn số âm hoặc số quá lớn theo giải mã `getInt()`. Hạn chế kích thước frame, quản lý backpressure, timeout và số kết nối đang giữ buffer là một phần của thiết kế I/O đúng đắn.

## 9. Scattering/Gathering và các API khác vẫn có partial I/O

Một số channel cung cấp thao tác đọc/ghi nhiều buffer:

~~~java
long n = scatteringChannel.read(new ByteBuffer[] {header, body});
long m = gatheringChannel.write(new ByteBuffer[] {prefix, payload});
~~~

Đây là **đoạn minh họa**: `scatteringChannel` thuộc loại hỗ trợ `ScatteringByteChannel`, `gatheringChannel` hỗ trợ `GatheringByteChannel`, và các buffer đã được chuẩn bị trạng thái đúng. `n` và `m` là **tổng số byte được chuyển**, không phải số buffer đã xử lý. Channel có thể chỉ điền một phần trong buffer đầu tiên hoặc chỉ ghi một phần payload; vẫn cần kiểm tra `position/remaining` của từng buffer.

Tương tự, các API như `FileChannel.transferTo(...)` và `transferFrom(...)` trả về **số byte thực chuyển**; không giả định một lần gọi sẽ chuyển hết `count` yêu cầu. Với `AsynchronousByteChannel`, giá trị kết quả hoàn tất của `Future` hoặc `CompletionHandler` cũng cần được diễn giải theo số byte, không tự suy ra rằng đã xong toàn bộ thông điệp.

Một thông điệp gửi thành công qua I/O có thể đòi hỏi *application-level acknowledgment* nếu ứng dụng cần chứng minh peer đã nhận và xử lý, vượt ngoài ý nghĩa của `write()`.

## 10. Phân biệt các điều kiện kết thúc

Để điều khiển vòng lặp an toàn, trước tiên phải xác định **điều kiện hoàn tất thực sự**.

| Mục tiêu | Điều kiện thành công | Điều kiện cần xử lý |
| --- | --- | --- |
| Đọc chính xác `N` byte | `dst` chứa đủ `N` byte; `remaining()==0`. | `read==-1` trước khi đủ ⇒ EOF/truncated. |
| Đọc tất cả byte đến EOF | Đã xử lý các chunk nhận được; sau đó `read==-1`. | `read==0` không phải EOF. |
| Ghi hết một `ByteBuffer` | `src.hasRemaining()==false`. | `write==0` cần cơ chế chờ/tiếp tục phù hợp mode. |
| Ghi frame gồm nhiều buffer | Mọi buffer đã hết `remaining()`, đúng thứ tự. | Partial write ở một buffer bất kỳ. |
| Non-blocking socket | Hoàn tất theo trạng thái ứng dụng và event loop. | `0`/readiness, backpressure, timeout, đóng kết nối. |

Trong production, đừng để yêu cầu “đọc cho đủ” vô thời hạn trên dữ liệu không tin cậy. Cần giới hạn kích thước, deadline hoặc timeout theo giao thức; việc dùng `SocketChannel` và `Selector` không tự cung cấp mọi chính sách timeout/cancel ở cấp ứng dụng.

## 11. Các lỗi thường gặp và cách sửa

### Quên lặp sau write

~~~java
out.flip();
socket.write(out);  // Có thể chỉ ghi một phần.
out.clear();         // SAI nếu vẫn còn byte chưa gửi!
~~~

Cách đúng với blocking channel có khả năng tiến triển là gửi tiếp tới khi `!out.hasRemaining()`. Với non-blocking socket, giữ `out` trong hàng đợi ghi và đăng ký `OP_WRITE` để gửi phần còn lại khi sẵn sàng.

### Dùng `remaining()` trước và sau không đúng thời điểm

Nếu buffer vừa được channel **đọc vào** và position đang nằm cuối dữ liệu, `remaining()` lúc đó chỉ cho biết **sức chứa còn trống để ghi thêm**, không phải số byte dữ liệu mới đọc. Sau `flip()`, `remaining()` mới biểu thị số byte trong vùng đã nhận đang sẵn sàng để ứng dụng đọc ra.

### Dùng `clear()` trên buffer còn dữ liệu

`clear()` thay đổi trạng thái logic, có thể khiến dữ liệu chưa tiêu thụ bị ghi đè trong lượt đọc tiếp. Nếu chỉ một phần frame đã được parser xử lý, dùng `compact()` để giữ phần còn lại.

### Gọi `flip()` hai lần trong chuỗi gửi

Gọi `flip()` giữa hai lần `write` sau partial write khiến `limit` đổi thành vị trí sau lần gửi đầu và `position` về 0, nên có nguy cơ gửi lại phần đã gửi. Sau partial write, giữ nguyên trạng thái buffer và viết tiếp.

### Lầm `0` là kết thúc stream

Một non-blocking socket trả `0` thường chỉ có nghĩa *lúc này chưa tiến triển*, không phải ngắt kết nối. Điều kiện EOF của `read` là `-1`. Ngược lại, vòng lặp liên tục gọi lại khi trả `0` có thể chiếm CPU vô ích.

### Giả định một TCP read là một thông điệp

TCP không giữ ranh giới của mỗi `write` phía người gửi. Một thông điệp có thể đến nhiều mảnh; nhiều thông điệp cũng có thể gộp trong cùng lần `read`. Cần giao thức framing (độ dài, delimiter, cấu trúc cố định...) và parser xử lý dữ liệu còn dang dở.

### Bỏ qua character encoding

Một ký tự UTF-8 có thể gồm nhiều byte và bị chia giữa hai lần đọc. Không gọi `new String(chunk, UTF_8)` riêng cho mỗi chunk tùy ý nếu mục tiêu là xử lý **một stream văn bản liên tục**: ký tự ở ranh giới chunk có thể bị giải mã sai. Dùng `CharsetDecoder` theo trạng thái với buffer byte còn dư, hoặc tích lũy đủ một đơn vị văn bản/frame trước khi decode.

### Nhầm tiến độ channel với thành công nghiệp vụ

`write` trả số dương chỉ báo lượng byte được channel chấp nhận; khi giao tiếp mạng, đó không phải ACK ở tầng ứng dụng. Nếu yêu cầu “đơn hàng đã được xử lý” hoặc “thông điệp đã lưu bền vững”, cần xác nhận riêng ở đúng tầng.

## 12. Thực hành kiểm tra

1. Buffer có `position=3` và `limit=12`. `remaining()` bằng bao nhiêu? **9 byte** nếu đó là `ByteBuffer`. Một lần `write` có thể nhận ít hơn 9 byte.
2. `write(buffer)` trả `4` khi trước đó `position=3`. Position mới là bao nhiêu? **7**, nếu không có thao tác buffer khác xen vào.
3. Non-blocking `read(buffer)` trả `0`. Có kết luận peer đã đóng kết nối được không? **Không.** Cần đợi readiness hoặc điều kiện tiếp theo; `-1` mới báo EOF.
4. Đang đọc header 4 byte, lần đầu chỉ đọc được 2 byte. Làm gì? **Giữ 2 byte trong buffer, đọc tiếp** cho đến khi đủ 4 byte hoặc gặp EOF/lỗi.
5. Khi đang gửi 10 byte, lần đầu write thành công 6 byte, lệnh tiếp theo là `flip()` hay `write(buffer)`? **`write(buffer)` tiếp**, với buffer giữ nguyên trạng thái và `remaining()==4`.
6. Sau khi parser tiêu thụ 7 byte trong tổng 10 byte đã nhận, muốn nhận thêm vào cùng buffer thì dùng gì? **`compact()`**, để giữ lại 3 byte chưa xử lý.
7. Tại sao cùng một payload có thể cần 1 lần read trên máy này nhưng 5 lần read trên máy khác? **API chỉ trả số byte thực chuyển mỗi lần**, phụ thuộc provider, tốc độ đến của dữ liệu, bộ đệm và điều kiện thời điểm.
8. Lệnh `write` đã gửi toàn bộ buffer có chứng minh peer xử lý xong không? **Không.** Nếu cần xác nhận xử lý, giao thức phải có acknowledgment ứng dụng riêng.
9. `ScatteringByteChannel.read(buffers)` trả `long n`. Có phải `n` là số buffer đã được điền? **Không.** Đó là tổng số byte được đọc.
10. Cần ngăn chương trình đọc vô hạn từ client ác ý bằng cách nào? **Quy định giới hạn frame, deadline/timeout, kiểm soát dung lượng bộ đệm và cơ chế hủy/đóng kết nối** theo giao thức.

**Ghi nhớ:** Java NIO Channel cung cấp **số byte thực chuyển trong mỗi lần gọi**; buffer giữ tiến độ qua `position`. Để không mất hay lặp dữ liệu, đọc/ghi theo `remaining()`, giữ trạng thái sau partial I/O, phân biệt `0` với `-1`, và kết hợp cơ chế chờ sự kiện phù hợp với blocking hay non-blocking mode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-005">Ý nghĩa của kết quả 0 và EOF -1</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa của kết quả `0` và EOF `-1` khi đọc NIO Channel

## 1. Câu hỏi cốt lõi

Khi gọi `int n = channel.read(buffer)` trong Java NIO, giá trị `n` không phải nội dung byte vừa đọc. Nó là **số byte đã chuyển từ channel vào `ByteBuffer` trong đúng lần gọi đó**, hoặc dấu hiệu kết thúc luồng vào (end-of-stream, thường gọi EOF). Theo hợp đồng `ReadableByteChannel.read(ByteBuffer)`:

- **`n > 0`**: đọc thành công đúng `n` byte; chỉ từng ấy byte vừa được thêm vào vùng còn lại của buffer.
- **`n == 0`**: lần gọi không chuyển được byte nào. Có thể vì buffer không còn chỗ (`remaining() == 0`), vì channel không có byte sẵn ngay lúc đó (đặc biệt ở chế độ non-blocking), hoặc điều kiện cụ thể khác được API cho phép. **`0` không phải EOF.**
- **`n == -1`**: channel báo đã đến **cuối luồng đọc**. Không có byte mới nào được chuyển trong lần gọi này.
- **Ngoại lệ**: lỗi I/O hoặc trạng thái channel không hợp lệ được báo qua exception, không được “mã hóa” thành `0` hay `-1`.

Bài học thuộc nhóm **Java Core I/O → NIO Channel**. Mục tiêu là đọc chính xác kết quả của `read`, phân biệt file với socket, blocking với non-blocking, và thiết kế vòng lặp không rơi vào mất dữ liệu hoặc busy loop.

## 2. Hợp đồng đầy đủ của `read(ByteBuffer)`

Cho `dst` là `ByteBuffer` nhận dữ liệu. Ngay trước lời gọi:

```text
p = dst.position()
r = dst.remaining() = dst.limit() - dst.position()
```

Channel cố đọc **tối đa `r` byte**. Nếu đọc được `n` byte (`0 <= n <= r`), các byte được ghi bắt đầu tại chỉ số `p`, `dst.position()` tăng thành `p + n`, và `dst.limit()` giữ nguyên. Nếu trả `-1`, lần đọc đã chạm EOF; không có byte mới nào được ghi.

Ví dụ giả định buffer còn 5 vị trí:

```text
Trước read:  position=3, limit=8, remaining=5
read trả 2:  position=5, limit=8, remaining=3
read trả 0:  position=3, limit=8, remaining=5   (nếu xuất phát từ trạng thái đầu)
read trả -1: không có byte mới; chưa chuyển sang một vùng dữ liệu mới
```

Không được suy luận “đọc được ít hơn dung lượng yêu cầu là EOF”. Channel có thể trả `1` byte dù sau đó vẫn còn nhiều dữ liệu. Mỗi lần `read` chỉ báo kết quả *của lần đọc đó*.

| Kết quả từ `read(dst)` | Dữ liệu mới | Ý nghĩa cần hiểu | Hướng xử lý thường gặp |
| --- | --- | --- | --- |
| `1 .. dst.remaining()` trước lời gọi | Có đúng `n` byte | Có tiến triển; chưa khẳng định đã đọc hết | `flip()`, xử lý byte nhận được hoặc tích lũy khung dữ liệu |
| `0` | Không có | Chưa kết luận hết dữ liệu | Kiểm tra `remaining`, trạng thái blocking và cơ chế chờ |
| `-1` | Không có | Đã đến cuối input stream tại thời điểm gọi | Kết thúc việc đọc; xử lý phần dữ liệu đang đệm |
| Ném `IOException` hoặc ngoại lệ liên quan | Không có số byte trả về bình thường | I/O thất bại hoặc channel bị đóng/ngắt | Xử lý lỗi, đóng tài nguyên theo chính sách |

`remaining()` được tính **trước khi gọi** nếu muốn biết độ dài tối đa lần đọc, và **sau khi gọi** để biết số vị trí còn trống. Khi `read` trả `0`, state của buffer không tự chuyển sang chế độ đọc: `flip()` và `clear()` vẫn phải do ứng dụng quyết định.

## 3. `0`: không có tiến triển, nhưng có hai tình huống rất khác nhau

### 3.1. Buffer hết chỗ

Nếu `buffer.remaining() == 0`, không có vị trí nào để channel ghi thêm byte. Một lần đọc với buffer không có chỗ có thể trả `0` ngay cả khi file hoặc socket còn dữ liệu. Phép đọc **không thử được xem sau đó có byte mới hay EOF hay không** theo cách có ý nghĩa cho việc điều khiển luồng.

```java
ByteBuffer dst = ByteBuffer.allocate(4);
dst.position(dst.limit()); // remaining() == 0
int n = channel.read(dst); // thường là 0 vì không có chỗ chứa
```

Đây là đoạn minh họa trong một phương thức có biến `channel`, không phải một chương trình độc lập. Đối với `ReadableByteChannel` cơ bản, kết quả khi yêu cầu 0 byte phụ thuộc quy tắc API và triển khai tại trạng thái cụ thể; **không dựa vào `read(emptyBuffer)` để phát hiện EOF**. Lỗi thường gặp là quên `clear()` hoặc `compact()` trước lượt nhận dữ liệu mới.

### 3.2. Chưa có dữ liệu sẵn trên non-blocking socket

`SocketChannel` ở chế độ non-blocking (`configureBlocking(false)`) có thể trả `0` khi socket đã sẵn sàng sử dụng nhưng **ngay lúc gọi không có byte nào có thể lấy được**. Kết quả này không đồng nghĩa phía bên kia đóng kết nối. Byte tiếp theo có thể đến sau.

```java
int n = socketChannel.read(buffer);

if (n > 0) {
    // Đã nhận n byte.
} else if (n == 0) {
    // Chưa đọc được byte nào ngay lúc này: chờ sự kiện hoặc thời điểm phù hợp.
} else { // -1
    // Input đã kết thúc.
}
```

Đoạn trên là mã minh họa, giả sử `socketChannel` đã mở và `buffer` đã sẵn sàng nhận dữ liệu. Trong ứng dụng dùng `Selector`, khi `0` xuất hiện, thường trả điều khiển về event loop và tiếp tục chờ `OP_READ`, đồng thời điều chỉnh interest ops khi cần. Đừng gọi `read` liên tục trong một `while` rỗng chỉ để đợi byte mới: điều đó dễ làm CPU tăng vọt.

### 3.3. Blocking channel khác non-blocking ra sao?

Hợp đồng `ReadableByteChannel` bảo đảm với channel ở **blocking mode** và `dst.remaining() > 0`, lời gọi sẽ chờ đến khi đọc được ít nhất một byte. Nếu kết thúc trước khi đọc được byte, việc kết thúc luồng sẽ được biểu diễn bằng `-1`; lỗi I/O được báo riêng bằng ngoại lệ.

Đối với `FileChannel`, chế độ non-blocking của `SocketChannel` không áp dụng trực tiếp: file thông thường được đọc theo vị trí của file và có thể nhanh chóng đạt EOF. Nếu buffer đã có chỗ và file còn dữ liệu, số byte đọc được có thể nhỏ hơn yêu cầu; không lấy `0` làm dấu hiệu “file đã hết”.

Lưu ý: nhiều phương thức API, loại channel và hệ thống tệp có khác biệt chi tiết. Luôn dùng hợp đồng của channel thực tế thay vì suy luận từ một demo socket.

## 4. `-1`: dấu hiệu end-of-stream (EOF)

`-1` là một **giá trị sentinel** nằm ngoài miền số byte đã đọc `0 .. remaining()`. Nó cho biết không thể nhận byte tiếp theo do đầu vào đã kết thúc trong lần đọc này.

Với file:

- Nếu file chứa ba byte `ABC`, một lần đọc buffer 2 byte trả `2` (`AB`), lần sau trả `1` (`C`), lần sau nữa trả `-1`.
- Đọc file rỗng bằng buffer còn chỗ thường nhận ngay `-1`.
- Vị trí đọc tệp đã chạm cuối dữ liệu hiện có; không có ý nghĩa “đang đợi có file mới”.
- Nếu một tiến trình khác bổ sung byte vào file đang mở sau đó, khả năng đọc tiếp phụ thuộc thời điểm, vị trí và tính nhất quán của file/provider. Không coi EOF của file đang bị cập nhật đồng thời là sự kiện bất biến của cả thế giới.

Với TCP socket:

- `-1` từ `SocketChannel.read` cho biết **phía nhận của stream đã tới cuối**; thường là peer đóng kênh gửi dữ liệu một cách có trật tự hoặc gọi `shutdownOutput()`.
- TCP hỗ trợ **half-close**: peer có thể ngừng gửi nhưng vẫn còn khả năng nhận dữ liệu. Do đó EOF ở chiều đọc **không khẳng định cả hai chiều kết nối đều đã đóng**.
- Nếu peer reset kết nối hoặc I/O lỗi, Java có thể ném `IOException` thay vì trả `-1`. Đây không phải EOF bình thường.
- Byte đã đến trước tín hiệu kết thúc vẫn phải được xử lý. Ví dụ nếu buffer còn một phần khung ứng dụng chưa hoàn chỉnh, khi gặp `-1` phải quyết định đó là bản tin hợp lệ cuối hay lỗi dữ liệu bị cắt, tùy giao thức.

**Phân biệt:** `0` là “không có byte được truyền trong lượt này”; `-1` là “đã chạm điểm kết thúc input”. Cả hai đều không thêm byte vào buffer, nhưng **dẫn tới hành động hoàn toàn khác nhau**.

## 5. Ví dụ Java 17+ chạy độc lập: `0`, số dương và EOF `-1`

Tạo file `ChannelReadResultDemo.java`. Chương trình sinh file tạm chứa đúng ba byte ASCII `ABC`, mở `FileChannel` đọc, thực hiện một phép đọc vào buffer có sức chứa 0 để minh họa `0`, rồi đọc file qua buffer có sức chứa 2 để thấy kết quả dương và `-1`.

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChannelReadResultDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("nio-read-result-", ".txt");

        try {
            Files.write(path, "ABC".getBytes(StandardCharsets.US_ASCII));

            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ)) {

                ByteBuffer noSpace = ByteBuffer.allocate(0);
                int zero = channel.read(noSpace);
                System.out.println("Empty buffer: " + zero);
                System.out.println("File position: " + channel.position());

                ByteBuffer buffer = ByteBuffer.allocate(2);
                while (true) {
                    buffer.clear();               // Chuẩn bị nhận byte mới
                    int n = channel.read(buffer);

                    if (n == -1) {
                        System.out.println("Read: -1 (EOF)");
                        break;
                    }
                    if (n == 0) {
                        System.out.println("Read: 0 (no progress)");
                        continue;
                    }

                    buffer.flip();                // Chỉ xử lý n byte vừa đọc
                    byte[] bytes = new byte[n];
                    buffer.get(bytes);
                    String chunk = new String(bytes, StandardCharsets.US_ASCII);
                    System.out.println("Read: " + n + " (" + chunk + ")");
                }
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ChannelReadResultDemo.java
java ChannelReadResultDemo
```

Stdout trên JDK 17+ với file tạm được tạo thành công:

```text
Empty buffer: 0
File position: 0
Read: 2 (AB)
Read: 1 (C)
Read: -1 (EOF)
```

**Diễn giải từng bước:**

1. `ByteBuffer.allocate(0)` có `remaining() == 0`; `FileChannel.read(noSpace)` trả `0` và file position vẫn là `0`. Vì thế `0` ở đây **không thể** được diễn giải thành EOF.
2. `ByteBuffer.allocate(2)` cho phép đọc tối đa 2 byte. `read` đầu tiên lấy `AB`, tăng `buffer.position()` lên 2, đồng thời tiến file position thêm 2.
3. Vòng lặp tiếp theo `clear()` để nhận lượt mới; file chỉ còn `C`, nên `read` trả `1` thay vì `2`. Vẫn chưa phải EOF trong lần đọc đó.
4. Sau khi tiêu thụ `C`, lời gọi tiếp theo với buffer còn chỗ trả `-1`, vòng lặp kết thúc.
5. `flip()` giới hạn phạm vi đọc buffer về đúng số byte mới nhận. Dữ liệu cũ có thể còn trong backing storage; không dùng toàn bộ `buffer.array()` như nội dung hợp lệ.

Vòng `continue` khi `n==0` trong ví dụ này chỉ dùng cho **FileChannel với buffer luôn có sức chứa 2 và đã được `clear()`**; nó không phải chiến lược chờ an toàn cho `SocketChannel` non-blocking. Với socket non-blocking phải dùng `Selector`, callback hoặc cơ chế chờ phù hợp.

## 6. Không đánh đồng kết quả `read` với giá trị byte dữ liệu

Một byte trong file có thể mang giá trị 0 (`0x00`), hoặc `0xFF`. Điều đó **không đồng nghĩa** `read` trả `0` hoặc `-1`.

Ví dụ tệp gồm các byte `[0x00, 0xFF]`:

```java
ByteBuffer buffer = ByteBuffer.allocate(4);
int n = channel.read(buffer);
```

Đây là đoạn minh họa, cần nằm trong phương thức xử lý `IOException` và `channel` đang đọc đúng tệp. Nếu `read` lấy được cả hai byte, nó trả **`2`**. Byte `0x00` không phải “không đọc được”; byte `0xFF` không phải EOF. Giá trị `-1` thuộc kết quả **`int` của phương thức đọc**, không phải byte mà `ByteBuffer` chứa.

Đây cũng là lý do nhiều API Java chọn `int` cho kết quả đọc: chúng cần một sentinel âm bên ngoài số lượng byte hợp lệ. Với `InputStream.read()` (đọc một byte), `0 .. 255` là dữ liệu, còn `-1` mới là EOF; đừng nhầm nó với `ReadableByteChannel.read(ByteBuffer)` trả **số lượng byte**.

## 7. Tại sao `flip()`, `clear()` và `remaining()` ảnh hưởng việc hiểu `0`?

`read(ByteBuffer)` ghi vào **phần còn lại từ `position` tới `limit`**. Do đó luôn kiểm tra trạng thái buffer trước khi phân tích `0`.

```text
Buffer capacity 8:
  position=8, limit=8 => remaining=0 => không có chỗ ghi
  clear()             => position=0, limit=8 => có chỗ ghi
  read() trả 3        => position=3, limit=8
  flip()              => position=0, limit=3 => chỉ 3 byte được đọc
  consume 3 bytes     => position=3, limit=3
  compact()           => position=0, limit=8 => sẵn sàng ghi tiếp
```

- `clear()`: reset để **ghi mới từ đầu**; không xóa storage cũ. Dùng khi buffer không còn byte cần giữ lại.
- `flip()`: chuyển sang **đọc những byte đã được ghi** bằng cách đặt `limit` bằng `position` hiện tại, rồi `position=0`.
- `compact()`: dồn byte **chưa được tiêu thụ** về đầu, chuẩn bị ghi tiếp vào phần trống. Dùng trong giao thức streaming khi khung dữ liệu có thể đến qua nhiều lần `read`.
- `hasRemaining()`: hỏi còn phần tử trong khoảng `position .. limit` hiện tại hay không; không cho biết channel còn byte bên ngoài hay chưa.
- `remaining()==0` sau `flip()` chỉ nói vùng đọc của buffer rỗng; không kết luận luồng nguồn đã EOF.

Sai lầm kinh điển: `read` được `2` byte, rồi gọi `clear()` trước khi xử lý; ứng dụng quên giới hạn 2 byte mới và có thể đọc nhầm phần còn lại trong storage. Ngược lại, quên `clear()`/`compact()` sau khi đọc hết buffer làm lần `channel.read(buffer)` tiếp theo trả `0` dù nguồn vẫn còn byte.

## 8. Vòng lặp file channel đúng và lý do không chờ vô hạn

Khi đọc một file thông thường bằng `FileChannel` vào buffer luôn có `remaining()>0`, mẫu dễ hiểu là:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

static long drain(FileChannel source) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    long total = 0;

    while (true) {
        buffer.clear();
        int n = source.read(buffer);

        if (n == -1) {
            return total;
        }
        if (n == 0) {
            // Với FileChannel và buffer còn chỗ, kiểm tra tình huống bất thường
            // hoặc chính sách phù hợp; không hiểu 0 là EOF.
            continue;
        }

        total += n;
        buffer.flip();
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            // Xử lý byte b; ở đây chỉ minh họa tiêu thụ dữ liệu.
        }
    }
}
```

Đây là **phương thức minh họa**, không phải file Java độc lập; đặt vào một class cùng import. Nó thống kê số byte đọc trong phiên đọc hiện tại và tiêu thụ byte trong buffer, không trả nội dung file. Trong ứng dụng thực tế, đừng giữ `continue` vô điều kiện nếu loại channel có thể liên tục trả `0`: cần cơ chế giới hạn/chờ phù hợp. Hàm gọi có trách nhiệm mở và đóng `FileChannel`; phương thức không tự đóng channel vì không sở hữu tài nguyên ấy.

Một số chi tiết quan trọng:

- `n < buffer.capacity()` **không có nghĩa** là EOF. Cứ lặp tiếp.
- Sau `-1`, dừng vòng `read`. Nếu còn byte đang giữ trong buffer ở logic cấp ứng dụng, phải hoàn tất xử lý hoặc báo lỗi khung dữ liệu không hoàn chỉnh.
- Đừng quên kiểm tra exception như `ClosedChannelException`, `AsynchronousCloseException`, `IOException`; `read` không dùng `0` làm mã báo lỗi.
- Nếu tài nguyên đọc có thể bị thay đổi, các lần đọc và vị trí file cần được điều phối; không suy diễn số lần gọi từ kích thước file đo trước.

## 9. Vòng lặp socket non-blocking: `0` dẫn tới cơ chế chờ, `-1` dẫn tới kết thúc input

Khác với file thông thường, socket kết nối tới luồng dữ liệu đến theo thời gian. Đặt `SocketChannel` ở chế độ non-blocking và đăng ký với `Selector` để tránh đợi bằng cách chiếm CPU.

Sơ đồ logic:

```text
Selector thông báo khả năng đọc
            |
      SocketChannel.read(dst)
         /       |         \
      n > 0     n == 0      n == -1
       |           |           |
  Có byte mới   Chưa có byte   EOF chiều đọc
  parse/tích    vào lúc này    xử lý phần đệm
  lũy dữ liệu   trở về loop    rồi kết thúc đọc
```

Khi `n>0`, đọc dữ liệu vào buffer, `flip()`, phân tích **chỉ những khung hoàn chỉnh**, sau đó `compact()` để giữ phần khung còn dở. Khi `n==0`, đừng xóa dữ liệu chưa xử lý trong buffer hoặc coi kết nối đã bị cắt; chỉ để event loop tiếp tục chờ. Khi `n==-1`, ứng dụng nên kết thúc việc chờ input trên kênh và quyết định cách đóng/half-close dựa vào giao thức.

Minh họa khung xử lý một sự kiện, **không phải một chương trình Java độc lập**:

```java
// socket đang mở, non-blocking; dst ở chế độ sẵn sàng để ghi.
// Phải quản lý chế độ đọc/ghi buffer giữa các callback.
int n = socket.read(dst);

if (n == -1) {
    // Peer đã kết thúc output; xử lý phần dữ liệu ứng dụng còn đệm.
    // Hủy đăng ký đọc / đóng theo quy tắc giao thức.
} else if (n == 0) {
    // Không tiến triển ngay lúc này; trả quyền cho event loop.
} else {
    dst.flip();
    // parseCompleteMessages(dst): chỉ tiêu thụ các thông điệp đủ byte.
    dst.compact();  // Giữ lại mọi byte của thông điệp chưa hoàn chỉnh.
}
```

Nếu `dst` đã đầy mà vẫn còn một thông điệp chưa hoàn chỉnh, `compact()` có thể vẫn để `remaining()==0` sau khi dồn. Lúc này cần **buffer lớn hơn hoặc chính sách giới hạn kích thước frame**, không gọi `read` vô hạn. `Selector` cũng có thể thức dậy nhưng lượt đọc thực tế vẫn trả `0`; readiness không phải lời hứa chắc chắn đọc được byte vào bất cứ thời điểm nào.

**Không xem `read()==0` như “server đã gửi packet rỗng”.** TCP là byte stream; không có một sự kiện read=0 tương đương “thông điệp kích thước 0”. Nếu giao thức ứng dụng có frame dài 0 byte, frame ấy phải được biểu diễn bằng header/độ dài riêng của giao thức.

## 10. Phân biệt `read` và `write`: `write()==0` cũng cần quan tâm

`WritableByteChannel.write(ByteBuffer)` trả số byte **đã lấy từ buffer để gửi/ghi** trong lần gọi, là giá trị từ `0` trở lên. Không dùng `-1` cho EOF ở thao tác `write`.

- `write() > 0`: đã tiêu thụ một phần hoặc toàn bộ byte còn lại trong `src`; `src.position()` tiến lên.
- `write() == 0`: lần đó không ghi được byte. Chẳng hạn `src.remaining()==0`, hoặc non-blocking socket chưa có khả năng ghi thêm ngay lúc đó.
- `write()` trả ít hơn `remaining()` ban đầu: **partial write**, không phải lỗi. Phần chưa ghi còn nằm trong buffer.
- Lỗi ghi, channel đóng hoặc trạng thái không phù hợp được báo bằng ngoại lệ thích hợp.

Với output channel kiểu blocking có tiến triển, mã thường viết:

```java
while (buffer.hasRemaining()) {
    destination.write(buffer);
}
```

Đây là đoạn minh họa. Với non-blocking socket, vòng lặp trên nếu `write` liên tục trả `0` sẽ **busy-spin**. Phải tạm dừng, đăng ký `OP_WRITE` khi còn dữ liệu cần gửi, và tiếp tục lúc socket có khả năng ghi. Không `clear()` khi còn byte chưa gửi, vì như vậy bạn đánh mất trạng thái những byte đang chờ.

## 11. EOF ở nhiều kiểu channel và API liên quan

| API / tình huống | `0` hoặc trường hợp không tiến triển | `-1` | Ghi chú |
| --- | --- | --- | --- |
| `ReadableByteChannel.read(ByteBuffer)` | Có thể `0`; phụ thuộc `remaining`, blocking, trạng thái channel | Cuối input stream | Hợp đồng cơ bản của các channel đọc |
| `FileChannel.read(ByteBuffer)` | Buffer rỗng có thể cho `0` | Vị trí đọc đã ở cuối file lúc gọi | Đọc ngắn vẫn là số dương |
| `SocketChannel.read(ByteBuffer)` non-blocking | Không có byte sẵn ngay lúc gọi có thể cho `0` | Input stream đã kết thúc | Cần event loop/selector |
| `SocketChannel.read(ByteBuffer)` blocking | Với buffer còn chỗ, lời gọi chờ cho tới khi đọc được byte hoặc EOF/lỗi | Input stream kết thúc | Không dùng `0` làm tín hiệu peer đóng |
| `ScatteringByteChannel.read(ByteBuffer[])` | Tổng số byte đọc có thể là `0` | Input stream kết thúc | Trả `long`, không phải `int`; các buffer lần lượt nhận dữ liệu |
| `AsynchronousFileChannel.read(...)` | Kết quả hoàn thành bất đồng bộ có thể `0` nếu không có chỗ hoặc không tiến triển | Đọc từ vị trí tại/cuối file | Nhận kết quả qua `Future<Integer>` hoặc callback, không nhận trực tiếp từ lệnh phát động |
| `WritableByteChannel.write(ByteBuffer)` | `0` = không ghi byte nào lượt này | Không dùng EOF `-1` | Ghi từng phần phải xử lý |
| `InputStream.read(byte[])` | Khi độ dài buffer bằng 0 hoặc theo hợp đồng stream cụ thể | EOF cũng là `-1` | API stream khác channel; xem hợp đồng mỗi overload |

### Với asynchronous channel, đọc khác về thời điểm nhận kết quả

`AsynchronousFileChannel.read(dst, position)` **trả `Future<Integer>`**, còn `read(dst, position, attachment, handler)` chuyển kết quả tới `CompletionHandler`. `0` và `-1` ở đây nằm trong **kết quả hoàn thành của hoạt động I/O**, không phải biến trả về trực tiếp từ việc phát lệnh.

Đối với `AsynchronousByteChannel`, nếu `dst.remaining()==0`, một lượt đọc bất đồng bộ có thể hoàn thành ngay với `0` mà không khởi động I/O thật. Nếu nhận `-1`, input đã hết. Với mọi asynchronous API, không được chỉnh trạng thái `ByteBuffer` tùy tiện trước khi tác vụ sử dụng buffer hoàn tất.

## 12. Những lỗi lập trình thường gặp

| Lỗi | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `if (n <= 0) break;` | Gộp `0` với `-1`; với non-blocking socket làm mất cơ hội đọc dữ liệu sẽ đến sau | Tách `n==-1`, `n==0`, `n>0` |
| `while (channel.read(buf) != -1) {}` | Không có xử lý buffer; nếu đầy sẽ tiếp tục trả `0` và lặp vô tận | `flip()`, tiêu thụ, `clear()`/`compact()` đúng lúc |
| `if (n < buf.capacity()) break;` | Đọc ngắn không bằng EOF | Chỉ dừng theo EOF, protocol hoặc điều kiện nghiệp vụ |
| `while (socket.read(buf)==0) {}` | Busy loop trên non-blocking I/O | Trả điều khiển về `Selector` hoặc scheduler |
| Nhận `0` thì đóng socket | `0` không báo đóng input | Tiếp tục theo dõi read readiness |
| Nhận `-1` rồi bỏ dữ liệu đã đọc ở lượt trước | Có thể còn partial frame trong buffer | Hoàn tất hoặc báo lỗi frame chưa đủ theo protocol |
| Đọc vào buffer đã đầy và kết luận file đã hết | Không còn chỗ nhận byte | Dùng `flip()`, tiêu thụ, `clear()` hoặc `compact()` |
| Lấy `buffer.array()` làm toàn bộ kết quả đọc | Mảng chứa cả byte cũ/không hợp lệ | Dùng `n`, `remaining()` sau `flip()` và offset thích hợp |
| Nhầm `0x00` là read `0`, `0xFF` là EOF | Một bên là giá trị byte, bên kia là lượng byte/sentinel | Đọc dữ liệu trong buffer; kiểm tra số trả về riêng |
| `write()==0` có nghĩa receiver đóng | Không tiến triển của write không phải bằng chứng kết nối chết | Đợi `OP_WRITE` hoặc xử lý exception |
| Bắt `IOException` và trả `-1` từ wrapper | Che giấu lỗi I/O thành EOF hợp lệ | Giữ loại lỗi hoặc chính sách retry/timeout minh bạch |

## 13. Tình huống thực tế

### A. Đọc toàn bộ file bất kỳ kích thước

Dùng buffer kích thước cố định, `read` nhiều lần, cộng số byte `n>0`, dừng khi `-1`. Không cần biết tổng dung lượng file trước khi đọc. Mỗi lần dùng `flip()` để xử lý dữ liệu mới nhận, `clear()` khi đã tiêu thụ hết.

### B. Giao thức mạng có độ dài thông điệp xác định

Giả sử header 4 byte mã hóa độ dài body. Một lần `read` có thể trả `1`, `2` hoặc `3` byte của header. Bất kỳ giá trị dương nào cũng là tiến triển; chưa đủ 4 byte thì phải tích lũy. Khi `read` trả `0`, chờ dữ liệu mới, không phát thông điệp sai; khi trả `-1` trước khi đủ header/body, báo frame bị cắt theo quy tắc ứng dụng.

### C. Đọc socket rồi ghi ra file

`SocketChannel.read` trả `0` trong non-blocking mode: để `Selector` chờ đợt tiếp theo. Khi `n>0`, `flip()` buffer và ghi đến khi toàn bộ dữ liệu được tiêu thụ (hoặc đặt phần còn lại vào hàng đợi output). Khi nhận `-1`, hoàn thành/đóng input và xử lý output còn tồn; không xóa dữ liệu chưa ghi chỉ vì nguồn đã EOF.

## 14. Câu hỏi tự kiểm tra

**1.** `channel.read(buffer)` trả `0`; có chắc đang ở cuối tệp?  
**Đáp án:** Không. Phải xem `buffer.remaining()`, loại channel, mode và trạng thái I/O. `0` chỉ cho biết lần gọi không đọc byte nào.

**2.** File có 5 byte, buffer dung lượng 8, lần đầu trả `5`; đã EOF chưa?  
**Đáp án:** Chưa thể kết luận từ riêng `5`. Một lần đọc tiếp với buffer có chỗ mới xác định EOF qua `-1` nếu không còn byte.

**3.** File có hai byte giá trị `0x00` và `0xFF`. `read(buffer)` lấy cả hai thì trả gì?  
**Đáp án:** `2`, vì kết quả là số byte, không phải nội dung byte.

**4.** `SocketChannel` non-blocking trả `0`; có nên gọi lại `read` hàng triệu lần ngay lập tức?  
**Đáp án:** Không. Trả điều khiển cho `Selector`/event loop hoặc cơ chế chờ phù hợp; kiểm soát buffer và đăng ký `OP_READ`.

**5.** `read` trả `-1` nhưng ứng dụng còn giữ một nửa bản tin trong buffer; có thể bỏ qua phần ấy không?  
**Đáp án:** Tùy đặc tả giao thức. Nếu một frame bắt buộc đầy đủ, phải báo truncation/protocol error; nếu nội dung cho phép kết thúc ở đó, xử lý phần còn lại theo luật nghiệp vụ.

**6.** Tại sao vòng lặp `while (channel.read(buffer) != -1)` nguy hiểm?  
**Đáp án:** Nó không tiêu thụ và tái chuẩn bị buffer; sau khi buffer đầy, lượt đọc có thể trả `0` liên tục. Không sử dụng giá trị dương đã đọc cũng có thể làm mất logic xử lý dữ liệu.

**7.** `write(src)` trả `0` nghĩa là gì?  
**Đáp án:** Lần gọi chưa ghi byte nào; không có sentinel `-1` cho EOF trong `write`. Có thể hết dữ liệu trong src hoặc channel non-blocking chưa sẵn sàng ghi.

## 15. Ghi nhớ

```text
READ:
    n > 0  -> đã nhận đúng n byte, xử lý dữ liệu mới
    n == 0 -> không có tiến triển; kiểm tra buffer và chờ nếu cần
    n == -1-> EOF input; dừng đọc và giải quyết dữ liệu đang đệm
    throws -> lỗi I/O hoặc trạng thái không hợp lệ; xử lý riêng

WRITE:
    n > 0  -> đã ghi n byte
    n == 0 -> không tiến triển; tránh busy loop
    không có -1 như tín hiệu EOF
```

Với NIO, **kết quả `0` là trạng thái của một lần gọi, còn `-1` là tín hiệu kết thúc luồng đọc**. Dữ liệu của ứng dụng vẫn phải được phân định bằng `ByteBuffer.position/limit` và quy tắc framing, thay vì chỉ dựa vào một lần đọc.

## Tài liệu tham khảo chính thức

- Java SE 21 — `ReadableByteChannel.read(ByteBuffer)`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html
- Java SE 21 — `FileChannel.read(ByteBuffer)`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 21 — `SocketChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html
- Java SE 21 — `WritableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/WritableByteChannel.html
- Java SE 21 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 21 — `AsynchronousByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousByteChannel.html
- Java SE 21 — `AsynchronousFileChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-006">Vòng lặp đọc ghi dựa trên position và remaining</a>

<details>
<summary>Xem chi tiết</summary>

# NIO Channel: vòng lặp đọc ghi dựa trên position và remaining

Trong Java NIO, một lời gọi `ReadableByteChannel.read(ByteBuffer)` hoặc `WritableByteChannel.write(ByteBuffer)` **không có nghĩa là toàn bộ dữ liệu đã được đọc hoặc ghi**. Channel chuyển một số byte, rồi cập nhật `position` của `ByteBuffer`. Vì vậy, chương trình cần lặp dựa trên **`position()`, `limit()`, `remaining()` và giá trị trả về của read/write**.

Đây là nền tảng để sao chép file chính xác, truyền thông điệp qua socket, xử lý bản ghi có kích thước cố định, và thiết kế chương trình không bị mất byte khi I/O trả về ít hơn yêu cầu.

## 1. Ba quy tắc quan trọng nhất

**Quy tắc 1: `read(buffer)` ghi vào buffer ở vị trí hiện tại.** Phương thức trả về số byte đã đọc `n`; nếu `n > 0` thì `position` tăng đúng `n`. Không được giả định mỗi lần `read` sẽ làm đầy buffer.

**Quy tắc 2: `write(buffer)` lấy byte từ vị trí hiện tại đến trước `limit`.** Phương thức trả về số byte đã ghi `n`; nếu `n > 0` thì `position` tăng đúng `n`. Không được bỏ những byte còn lại nếu một lời gọi ghi chưa tiêu thụ hết buffer.

**Quy tắc 3: Chuyển pha đúng cách.** Sau khi channel đọc **vào** buffer, gọi `flip()` để bắt đầu tiêu thụ dữ liệu. Sau khi tiêu thụ xong, gọi `clear()` để nhận đợt mới; hoặc `compact()` nếu còn dữ liệu chưa thể xử lý.

Phạm vi bài này là các channel đồng bộ trong `java.nio.channels`, đặc biệt `FileChannel` và `SocketChannel`. Với non-blocking channel, còn cần phối hợp `Selector` khi một lời gọi trả về 0.

## 2. Mô hình position, limit, capacity, remaining

Mỗi `Buffer` theo dõi:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `capacity()` | Dung lượng tối đa tính theo **phần tử buffer**; với `ByteBuffer` là byte. |
| `limit()` | Ranh giới *không bao gồm* phần tử được phép đọc/ghi kế tiếp. |
| `position()` | Vị trí kế tiếp cho thao tác tương đối, được channel cập nhật khi chuyển byte. |
| `remaining()` | Số phần tử trong vùng có thể xử lý: `limit() - position()`. |
| `hasRemaining()` | `true` khi `remaining() > 0`. |

Bất biến bắt buộc:

```text
0 <= position <= limit <= capacity
remaining = limit - position
```

Một `ByteBuffer.allocate(8)` có trạng thái ban đầu:

```text
capacity = 8, limit = 8, position = 0, remaining = 8
[ .  .  .  .  .  .  .  . ]
  ^
  byte kế tiếp do channel.read(buffer) ghi vào
```

Giả sử `read` trả về 3, ba byte được đặt vào buffer, `position = 3`. Sau `flip()`, trạng thái thành `position = 0, limit = 3, remaining = 3`. Nếu `write` tiêu thụ 2 byte, `position = 2, remaining = 1`: **byte cuối vẫn cần được ghi**.

```text
Sau read trả về 3:              Sau flip:                    Sau write trả về 2:
position=3, limit=8             position=0, limit=3          position=2, limit=3
[A  B  C  .  .  .  .  .]       [A  B  C | .  .  .  .  .]     [A  B | C  .  .  .  .  .]
         ^                      ^                                  ^
                              3 byte chờ ghi                  1 byte còn lại
```

Điều kiện đúng để ghi phần còn lại là `while (buffer.hasRemaining())`, **không** kiểm tra `buffer.position() < buffer.capacity()`: capacity có thể lớn hơn limit sau `flip()`.

## 3. Hiểu giá trị trả về của read và write

Với `ReadableByteChannel.read(ByteBuffer dst)`:

- **`n > 0`**: đọc được `n` byte, tăng `dst.position()` đúng `n`.
- **`n == 0`**: chưa chuyển byte trong lần gọi này. Trên non-blocking `SocketChannel`, điều này rất bình thường khi chưa có dữ liệu; cần chờ readiness, không chạy vòng lặp bận vô hạn.
- **`n == -1`**: kết thúc luồng đầu vào (EOF). Không có thêm byte để đọc. Với một file channel, EOF là điểm dừng thông thường; với protocol có framing, EOF trước khi đủ khung có thể là lỗi dữ liệu thiếu.

Đối với `WritableByteChannel.write(ByteBuffer src)`:

- **`n > 0`**: đã ghi `n` byte và tăng `src.position()` theo số byte đó.
- **`n == 0`**: chưa chuyển byte. Trường hợp này có thể xuất hiện khi channel non-blocking chưa sẵn sàng để ghi. Việc retry liên tục ngay lập tức sẽ đốt CPU.
- `write` không có giá trị trả về `-1` để báo EOF như `read`. Lỗi ghi được biểu đạt qua `IOException` hoặc trạng thái channel, tùy loại.

Nếu buffer không còn byte có thể ghi (`!src.hasRemaining()`) thì thường không cần gọi `write`. Nếu đích của `read` đã đầy (`!dst.hasRemaining()`), hãy tiêu thụ hoặc chuyển pha trước, thay vì tiếp tục đọc vào một buffer không còn chỗ.

**Blocking không đồng nghĩa mỗi lệnh làm xong cả buffer.** Một blocking channel có thể chỉ đọc một phần, hoặc ghi một phần; việc gọi blocking chỉ mô tả cơ chế chờ nhất định của channel, không hứa một lời gọi chuyển đúng `remaining()` byte. Ngược lại, non-blocking channel thường trả về 0 khi chưa sẵn sàng.

## 4. Mẫu vòng lặp ghi đúng cho blocking channel

Sau khi buffer đã ở **pha đọc** (thường sau `flip()`), mẫu cơ bản:

```java
// Snippet: channel là WritableByteChannel đang mở;
// buffer chứa byte cần gửi trong [position, limit).
while (buffer.hasRemaining()) {
    int n = channel.write(buffer);
    if (n == 0) {
        throw new IOException("Không có tiến triển khi ghi");
    }
}
```

Đoạn trên là snippet với `channel`, `buffer` được khai báo bên ngoài và `IOException` được xử lý. Nó **cố ý fail fast** nếu không có tiến triển, phù hợp với một tác vụ I/O blocking mà ứng dụng lựa chọn báo lỗi thay vì chờ. Nó **không phải thuật toán chung cho non-blocking channel**, nơi 0 có thể hợp lệ; ở phần non-blocking bên dưới sẽ chờ sự kiện `OP_WRITE`.

Vì sao cần vòng `while`? Giả sử `buffer.remaining() == 10`, lần `write` đầu trả 4. Channel đã tự tăng `position` thêm 4; còn 6 byte chưa gửi. Gọi `write` tiếp trên **chính buffer đó** sẽ bắt đầu ở byte thứ năm. Nếu chương trình gọi `clear()` ngay sau lần ghi thứ nhất, sáu byte còn lại sẽ bị mất khỏi luồng đầu ra của ứng dụng.

**Không tự cộng position theo n** sau `write(buffer)`: channel đã làm việc đó. Cộng lần nữa khiến ứng dụng bỏ qua byte. Không dùng `buffer.array()` để gửi thay cho `buffer` nếu muốn tôn trọng position, limit và hỗ trợ direct buffer.

## 5. Mẫu vòng lặp đọc đúng cho FileChannel

Với `FileChannel` đọc một file thông thường và dữ liệu được xử lý ngay:

```java
// Snippet: channel là FileChannel mở READ;
// consume nhận dữ liệu đang ở pha đọc [position, limit).
ByteBuffer buffer = ByteBuffer.allocate(8192);
while (true) {
    int n = channel.read(buffer);
    if (n == -1) {
        break;
    }
    if (n == 0) {
        throw new IOException("Đọc file không có tiến triển");
    }

    buffer.flip();
    consume(buffer);
    buffer.clear();
}
```

Đây là **mã minh họa**, không phải chương trình độc lập; nó giả sử hàm `consume` xử lý **hết mọi byte** trong buffer. Nếu `consume` chỉ tiêu thụ một phần vì chưa đủ một frame, thay `clear()` bằng `compact()` và quản lý state như phần 8. Khi xử lý file tĩnh thông thường, mỗi `read` có thể trả về số byte nhỏ hơn capacity, đặc biệt ở cuối file.

Cách viết tổng quát cho việc đọc **đủ N byte** là lưu số còn thiếu và tiếp tục gọi `read`. Không được dùng một lần `read` rồi kết luận dữ liệu đầu vào đã đủ N byte. Với NIO, `ByteBuffer.position()` là chỉ báo trực tiếp về số byte đã được thêm tính từ khi đặt position ban đầu.

## 6. Ví dụ Java 17+ đầy đủ: sao chép file và xác minh toàn bộ byte

Ví dụ dưới đây tạo hai file tạm, ghi `Hello NIO!` vào file nguồn, sao chép bằng `FileChannel` qua buffer chỉ **4 byte**, kiểm tra kết quả rồi dọn file. Dung lượng nhỏ làm lộ rõ việc phải lặp nhiều lần; mã vẫn xử lý đúng khi một lần `write` chỉ ghi được một phần dữ liệu.

Lưu thành **`ChannelLoopDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChannelLoopDemo {
    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("channel-source-", ".bin");
        Path target = Files.createTempFile("channel-target-", ".bin");

        try {
            Files.writeString(source, "Hello NIO!", StandardCharsets.UTF_8);

            long totalRead = 0;
            long totalWritten = 0;
            ByteBuffer buffer = ByteBuffer.allocate(4);

            try (FileChannel in = FileChannel.open(
                         source, StandardOpenOption.READ);
                 FileChannel out = FileChannel.open(
                         target, StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {

                while (true) {
                    int n = in.read(buffer);
                    if (n == -1) {
                        break;
                    }
                    if (n == 0) {
                        throw new IOException("Không có tiến triển khi đọc");
                    }

                    totalRead += n;
                    buffer.flip(); // position=0, limit=số byte vừa đọc

                    while (buffer.hasRemaining()) {
                        int written = out.write(buffer);
                        if (written == 0) {
                            throw new IOException("Không có tiến triển khi ghi");
                        }
                        totalWritten += written;
                    }

                    buffer.clear(); // đã chuyển hết byte; sẵn sàng đọc đợt sau
                }
            }

            System.out.println("read=" + totalRead
                    + " written=" + totalWritten);
            System.out.println("result="
                    + Files.readString(target, StandardCharsets.UTF_8));
            System.out.println("identical="
                    + (Files.mismatch(source, target) == -1));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        }
    }
}
```

Chạy:

```bash
javac ChannelLoopDemo.java
java ChannelLoopDemo
```

Stdout:

```text
read=10 written=10
result=Hello NIO!
identical=true
```

**Giải thích trình tự:**

1. `Files.writeString` tạo payload ASCII dài 10 byte; ứng dụng mở `FileChannel` đầu vào để đọc và đầu ra để ghi.
2. `in.read(buffer)` thêm từ 1 đến tối đa 4 byte vào vị trí hiện tại; `totalRead` cộng đúng số thực tế. Không dùng `buffer.capacity()` thay cho `n`.
3. `buffer.flip()` chuyển sang pha ghi **ra channel**: limit được đặt bằng position cũ (số byte vừa đọc), position về 0.
4. `out.write(buffer)` lấy byte từ position đến limit; một lần có thể chưa hết, nên `while (buffer.hasRemaining())` ghi đến khi buffer trống.
5. Chỉ khi **mọi byte đã được ghi**, `buffer.clear()` mới an toàn: nó reset state thành position 0, limit capacity để nhận block mới.
6. Khi `read` trả về `-1`, vòng lặp dừng; hai channel được đóng. `Files.mismatch(source, target) == -1` xác nhận hai file có nội dung byte giống nhau, không chỉ là chuỗi in ra giống nhau.

Trong ví dụ file blocking này, nếu read/write trả về 0 bất ngờ, chương trình báo lỗi rõ ràng để tránh busy loop. Với ứng dụng thực tế, chính sách no-progress phụ thuộc loại channel, deadline và nhu cầu chờ I/O; không nên suy diễn một vòng `while` spin là an toàn với mọi channel.

## 7. Sai lầm điển hình: một lần write là đủ

Mã sai sau có thể làm mất dữ liệu khi output channel chỉ nhận một phần:

```java
int n = in.read(buffer);
if (n > 0) {
    buffer.flip();
    out.write(buffer); // SAI: không bảo đảm ghi hết remaining()
    buffer.clear();    // Các byte chưa ghi có thể bị bỏ qua.
}
```

Sửa thành:

```java
int n = in.read(buffer);
if (n > 0) {
    buffer.flip();
    while (buffer.hasRemaining()) {
        int written = out.write(buffer);
        if (written == 0) {
            throw new IOException("Ghi không có tiến triển");
        }
    }
    buffer.clear();
}
```

Hai đoạn là **snippet có biến `in`, `out`, `buffer` trong ngữ cảnh**, không phải chương trình chạy độc lập; nếu không chạy blocking, điều chỉnh nhánh trả về 0 theo readiness thay vì ném lỗi. Khi `read` trả `-1`, cần thoát khỏi vòng đọc ở cấp trên.

Lỗi tương tự xảy ra khi đọc đúng một lần vào buffer rồi mặc định số byte đó là **một thông điệp hoàn chỉnh**. Trong giao thức trên TCP, một `read` có thể trả về một phần khung, nhiều khung hoặc không byte; TCP là luồng byte, không bảo toàn ranh giới thông điệp của các lệnh `write`.

## 8. compact khi chưa tiêu thụ hết một khung dữ liệu

Nếu buffer chứa dữ liệu dở dang, `clear()` sẽ bỏ qua các byte chưa xử lý về mặt logic. Dùng `compact()` để giữ phần `[position, limit)`, chuyển chúng về đầu buffer rồi chuẩn bị ghi thêm byte.

Ví dụ đầu vào là các bản ghi cố định **4 byte**:

```java
// Snippet: buffer là ByteBuffer ở pha ghi.
// Tùy channel và ứng dụng, cần xử lý n == 0/-1 bên ngoài.
buffer.flip();

while (buffer.remaining() >= Integer.BYTES) {
    int value = buffer.getInt(); // Tiêu thụ 4 byte.
    handle(value);
}

buffer.compact(); // Giữ 0..3 byte chưa tạo thành một int hoàn chỉnh.
```

Khi `compact()` kết thúc, phần dữ liệu chưa đọc nằm ở đầu buffer; `position` bằng số byte giữ lại và `limit` về capacity. Lần `read(buffer)` tiếp theo nối dữ liệu mới **sau phần còn giữ**, nên parser không làm mất bản ghi bị chia giữa hai lần đọc.

Một vòng lặp đầy đủ cho record cố định cần kiểm tra thêm:

- Trong pha đọc, chỉ gọi `getInt()` khi `remaining() >= 4`, tránh `BufferUnderflowException`.
- Nếu EOF mà vẫn còn 1–3 byte chưa ghép thành bản ghi, file/protocol bị cắt giữa chừng; quyết định rõ sẽ báo lỗi hay chấp nhận phần dư.
- Với parser khung có length-prefix, phải xác minh độ dài không âm, nằm trong quota và có đủ byte trước khi cấp phát/giải mã.
- Dung lượng buffer phải đủ để chứa tối thiểu một đơn vị header cần phân tích, hoặc parser cần hỗ trợ state để thu thập header dần. Nếu buffer không còn chỗ mà không xử lý được bản ghi quá lớn, phải mở rộng/stream payload theo chính sách.

**`compact()` khác `clear()`:** compact sao chép phần chưa đọc về đầu và thay position; clear chỉ reset state, không xóa byte nhưng sẽ cho phép lần đọc kế tiếp ghi đè vùng dữ liệu cũ.

## 9. Ví dụ về EOF khi đang tích lũy frame

Giả sử protocol định nghĩa mỗi frame gồm **4 byte độ dài + N byte payload**. Nếu `read` trả `-1` trước khi có đủ 4 byte header hoặc trước khi đủ N byte payload, đó là **frame chưa hoàn chỉnh**, không phải kết thúc hợp lệ của một frame.

Không thể xử lý bằng cách đọc `channel.read(headerBuffer)` một lần duy nhất. Mã phác thảo blocking channel:

```java
// Snippet: channel là ReadableByteChannel đồng bộ;
// header là ByteBuffer.allocate(4).
while (header.hasRemaining()) {
    int n = channel.read(header);
    if (n == -1) {
        throw new EOFException("Thiếu dữ liệu header");
    }
    if (n == 0) {
        throw new IOException("Đọc header không có tiến triển");
    }
}
header.flip();
int payloadSize = header.getInt();
if (payloadSize < 0 || payloadSize > 1_048_576) {
    throw new IOException("Payload size không hợp lệ");
}
```

Đây là **snippet** yêu cầu import `java.io.EOFException`, `java.io.IOException` và các biến được khởi tạo trước. Nếu EOF xảy ra **trước khi bắt đầu bất kỳ header mới nào**, một protocol có thể coi là kết thúc sạch; còn EOF giữa header/payload phải được báo là truncate. Phân biệt hai trường hợp bằng state của parser hoặc số byte đã thu thập cho frame.

Việc kiểm tra `payloadSize` ngăn cấp phát theo độ dài độc hại. Với payload lớn, thiết kế streaming theo chunk để giới hạn memory, không ép mọi thông điệp vào một buffer khổng lồ.

## 10. SocketChannel non-blocking: không quay vòng khi trả về 0

Sau `SocketChannel.configureBlocking(false)`:

- `read(buffer) == 0` nghĩa là hiện chưa có byte để đọc; quay lại làm việc khác hoặc đợi `SelectionKey.OP_READ` qua `Selector`.
- `write(buffer) == 0` nghĩa là hiện chưa gửi được byte; giữ nguyên buffer cùng vị trí, đợi khả năng ghi với `OP_WRITE`.
- `read(buffer) == -1` là đầu vào đã kết thúc; xử lý EOF/half-close và trạng thái protocol theo thiết kế.
- Sau khi đọc/ghi `n > 0`, cả hai API đã tự tăng `position`. Trước khi đọc lần nữa, parser phải xử lý dữ liệu vừa nhận, `compact()` hoặc `clear()` cho phù hợp.

Ví dụ phác thảo cho **một buffer outbound**:

```java
// Snippet: socket là SocketChannel không blocking,
// pending là ByteBuffer đã flip, dùng để gửi.
// Chạy khi Selector thông báo có thể ghi.
while (pending.hasRemaining()) {
    int n = socket.write(pending);
    if (n == 0) {
        break; // Giữ nguyên pending; chờ OP_WRITE lần tiếp theo.
    }
}
if (!pending.hasRemaining()) {
    // Bỏ quan tâm OP_WRITE nếu không còn dữ liệu chờ gửi.
}
```

Đây là **mã minh họa**, không bao gồm cấu hình Selector/SelectionKey và quản lý hàng đợi message. Một ứng dụng thực tế cần giữ `pending` qua các vòng event loop, không tạo mới nó khi write chưa xong; cập nhật interest ops theo backlog để tránh CPU bận vì `OP_WRITE` luôn sẵn sàng khi socket còn khả năng ghi.

**Tuyệt đối không dùng `while (socket.write(pending) == 0) { }`**: việc chờ readiness hay timeout là trách nhiệm của event loop. Với NIO, tránh busy spin là một phần của tính đúng đắn về vận hành.

## 11. FileChannel: vị trí file khác vị trí buffer

Cả `FileChannel` và `ByteBuffer` đều có khái niệm `position`, nhưng đó là **hai con trỏ khác nhau**:

| Con trỏ | Quản lý cái gì? | API ví dụ |
| --- | --- | --- |
| `buffer.position()` | Index byte tiếp theo trong buffer. | `buffer.position()`, `flip()`, `clear()` |
| `channel.position()` | Byte offset hiện tại trong file. | `FileChannel.position()`, `position(long)` |

Khi gọi `channel.read(buffer)` dạng không truyền offset, `FileChannel` thường cập nhật cả vị trí file theo số byte đọc được và vị trí buffer theo cùng số byte. Ngược lại, các lệnh positional:

```java
int n = channel.read(buffer, 100L);
int m = channel.write(buffer, 200L);
```

là **snippet** áp dụng với `FileChannel` đã mở có quyền phù hợp. Chúng sử dụng vị trí file **được chỉ định** và không thay đổi `channel.position()` hiện tại, nhưng **vẫn cập nhật `buffer.position()`** theo số byte thực tế. Không nhầm việc không dịch file position với việc không dịch buffer position.

`FileChannel.transferTo`/`transferFrom` có thể hỗ trợ truyền dữ liệu hiệu quả nhưng cũng **có thể chuyển ít byte hơn yêu cầu**; chương trình vẫn cần lặp, kiểm tra số đã chuyển và xử lý tình trạng 0 phù hợp. Không bỏ quy tắc kiểm soát partial I/O chỉ vì dùng một API ở mức cao hơn.

## 12. Điều kiện dừng, tiến triển và tổng số byte

Một vòng lặp I/O đáng tin cần có:

1. **Mục tiêu rõ ràng:** Đọc đến EOF, thu đủ N byte, gửi toàn bộ `remaining()`, hay xử lý đến khi parser không còn frame hoàn chỉnh.
2. **Điều kiện dừng hợp lệ:** `read == -1` với file, buffer hết dữ liệu cần ghi, payload length đã thỏa, hoặc kết thúc theo protocol.
3. **Điều kiện chờ:** `read/write == 0` thì không có tiến triển; non-blocking phải chờ readiness, blocking có chính sách timeout/fail-fast thích hợp.
4. **Xử lý ngoại lệ:** `IOException`, channel bị đóng và các lỗi payload bất thường; giải phóng resource qua `try-with-resources`.
5. **Giới hạn nguồn lực:** quota kích thước, thời gian, số frame, và tổng byte nếu xử lý đầu vào không tin cậy.

Nếu phải đọc đúng N byte, dùng bộ đếm `long` hoặc buffer có limit đã đặt phù hợp. Đừng dựa hoàn toàn vào việc một `read` trả số byte bằng `buffer.capacity()`; lần đọc cuối có thể trả ít hơn, và buffer còn chứa dữ liệu cũ ở vùng chưa được ghi đợt này.

Trong mạng thực tế, deadline/timeout cần áp dụng lên **toàn bộ thao tác** chứ không chỉ từng lần đọc/ghi; điều này tránh peer chậm gửi từng byte để kéo dài kết nối vô hạn. Với file lớn, ghi bộ đếm `long` để tránh tràn khi tổng số byte vượt `Integer.MAX_VALUE`.

## 13. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| File đích ngắn hơn nguồn | Chỉ gọi `write(buffer)` một lần trước `clear()`. | Lặp `while (hasRemaining())` để ghi hết buffer. |
| File đích chứa byte 0 hoặc byte cũ | Không `flip()` sau `read`, hoặc dùng capacity làm số byte hợp lệ. | `flip()` để giới hạn đúng vùng dữ liệu vừa nhận. |
| Lặp vô hạn, CPU tăng | Channel trả 0 liên tục, vòng loop không có wait/failfast. | Blocking có chính sách no-progress; non-blocking đợi Selector. |
| Parser mất một phần message | Gọi `clear()` khi vẫn còn byte chưa đọc. | Dùng `compact()` và parser tích lũy phần thiếu. |
| `BufferUnderflowException` | `getInt()` dù chưa đủ 4 byte. | Kiểm tra `remaining() >= Integer.BYTES`. |
| `BufferOverflowException` | Đọc vào buffer hết chỗ/ghi vượt limit. | Quản lý pha buffer và quota payload. |
| EOF bị xem là 0 | Không phân biệt `-1` và `0`. | `-1` kết thúc input; 0 là chưa chuyển byte. |
| Socket nhận thiếu thông điệp | Mặc định một `read` bằng một thông điệp. | Dùng frame: fixed-size, delimiter, hoặc length-prefix. |
| Byte bị lặp/mất | Tự cộng `buffer.position()` sau `channel.write/read`. | Để channel dịch position; chỉ đặt lại khi chuyển pha có chủ đích. |
| Đọc cùng byte nhiều lần ngoài ý muốn | `flip()` hoặc `rewind()` sai thời điểm. | Ghi rõ state machine và kiểm tra position/limit trước/sau. |

Một điều nên kiểm tra trong code review là: **mỗi lệnh chuyển pha buffer có dựa trên thực tế đã xử lý hết dữ liệu của pha trước chưa?** Đặc biệt, đặt `clear()` ngay sau một `write` không có vòng `remaining` là dấu hiệu dễ mất dữ liệu.

## 14. Bài tập và câu hỏi tự kiểm tra

**Bài tập 1 — Mô phỏng partial write.** Buffer chứa 10 byte, `position=0, limit=10`. Channel lần lượt ghi được 4, 3, 3 byte. Hãy ghi `position/remaining` sau mỗi lần: `(4,6)` → `(7,3)` → `(10,0)`. Giải thích vì sao `while (hasRemaining())` dừng đúng lúc.

**Bài tập 2 — Sao chép file bất kỳ.** Đổi ví dụ thành buffer dung lượng 7 hoặc 8192 và file nguồn chứa nhiều dòng Unicode. Xác minh bằng `Files.mismatch` rằng số byte bằng nhau. Vì sao không thể suy số byte UTF-8 chỉ từ `String.length()`?

**Bài tập 3 — Parser `int` với compact.** Chuyển một stream byte chứa ba số `int` big-endian bằng các lượt đọc trả lần lượt 3, 2, 4, 3 byte. Viết vòng `flip()` → `while (remaining() >= 4) getInt()` → `compact()`, theo dõi số byte còn lại sau mỗi đợt.

**Bài tập 4 — EOF giữa frame.** Thiết kế hàm `readExactly(channel, buffer)` cho blocking channel đã có header 4 byte, phân biệt EOF sạch trước khung mới và EOF bất ngờ khi mới thu 1–3 byte. Bổ sung kiểm tra no-progress.

**Bài tập 5 — Non-blocking send.** Giả sử socket chỉ gửi 2 byte, sau đó trả 0 rồi có sự kiện `OP_WRITE` mới. Hãy mô tả trạng thái buffer phải giữ lại giữa các event; vì sao tạo buffer từ đầu ở mỗi event làm gửi trùng dữ liệu?

**Câu hỏi kiểm tra nhanh:**

- Sau `read(buffer)` nhận 5 byte, vì sao phải `flip()` trước khi ghi các byte đó ra channel?
- `remaining()` khác `capacity()` ở điểm nào sau `flip()`?
- Khi `write` trả 2 trong lúc `remaining = 8`, chương trình còn bao nhiêu byte chưa gửi?
- Khi `read == 0`, có thể kết luận EOF không?
- Nếu parser chỉ xử lý 4 trên tổng 7 byte, nên `clear()` hay `compact()`?
- Vì sao `FileChannel.read(buffer, offset)` có thể không thay `channel.position()` nhưng vẫn thay `buffer.position()`?

**Ghi nhớ:** `position` và `remaining` cho biết **bao nhiêu byte còn cần xử lý**; giá trị trả về của `read/write` cho biết **bao nhiêu byte thực tế đã chuyển trong lượt này**. Ghép hai thông tin đó với `flip/clear/compact` và chính sách cho `-1`/`0` là nền tảng của mọi vòng đọc ghi NIO Channel đúng đắn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-007">Scattering read và gathering write</a>

<details>
<summary>Xem chi tiết</summary>

# Scattering read và gathering write trong Java NIO Channel

## 1. Mục tiêu và bài toán

Trong Java NIO, thông tin thường được vận chuyển qua **Channel** và đặt trong **Buffer**. Nếu một thông điệp gồm nhiều phần, chẳng hạn **header** và **payload**, ứng dụng có thể:

- **Scattering read:** đọc các byte liên tiếp từ một channel rồi **phân tán** chúng vào nhiều `ByteBuffer` theo thứ tự.
- **Gathering write:** lấy các byte còn lại trong nhiều `ByteBuffer` rồi **gom** thành một dãy byte liên tiếp để ghi ra channel.

Đây là cơ chế I/O theo **vector buffer**: một lời gọi xử lý một mảng buffer, giúp thể hiện rõ cấu trúc dữ liệu và có thể giảm số lời gọi I/O tùy hệ thống. Tuy nhiên, **một lời gọi không đảm bảo đọc/ghi đủ mọi byte**, không đại diện cho một thông điệp mạng hoàn chỉnh và cũng không tự tạo một transaction nguyên tử.

Hình dung một frame đơn giản có **4 byte header** chứa độ dài payload, sau đó là **3 byte payload** chứa ký tự ASCII `ABC`:

~~~text
Luồng byte trên file/socket:
  00 00 00 03  41 42 43
  └─ header ─┘  └ body ┘
  4 byte         3 byte

Scattering read:
  Channel ───────────────┬──> ByteBuffer header [00 00 00 03]
                         └──> ByteBuffer body   [41 42 43]
                             (theo thứ tự mảng, header trước body)

Gathering write:
  ByteBuffer header [00 00 00 03] ─┐
                                   ├──> Channel: 00 00 00 03 41 42 43
  ByteBuffer body   [41 42 43] ────┘
~~~

Điều kiện quan trọng: **kích thước vùng header và body phải được chuẩn bị đúng**. Khi độ dài body chưa biết cho đến lúc đọc xong header, chương trình thường cần **hai pha đọc** hoặc một parser lưu trạng thái, thay vì mặc định một lời gọi scattering có thể tự cấp đúng buffer cho payload.

## 2. API nào hỗ trợ?

Hai interface ở package `java.nio.channels`:

- `ScatteringByteChannel`: đọc tuần tự các byte từ channel vào **một mảng `ByteBuffer`**.
- `GatheringByteChannel`: ghi tuần tự dữ liệu từ **một mảng `ByteBuffer`** ra channel.

Các overload cơ bản:

~~~java
// Các chữ ký API (không phải chương trình độc lập):
long read(ByteBuffer[] dsts) throws IOException;
long read(ByteBuffer[] dsts, int offset, int length) throws IOException;

long write(ByteBuffer[] srcs) throws IOException;
long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
~~~

Lưu ý: các phương thức `read` thuộc `ScatteringByteChannel`, các phương thức `write` thuộc `GatheringByteChannel`; không phải mọi kiểu `Channel` đều hỗ trợ cả hai. Những kiểu tiêu biểu hỗ trợ là:

| Channel | Scattering read | Gathering write | Bối cảnh điển hình |
|---|---|---|---|
| `FileChannel` | Có | Có | Đọc/ghi tệp có header, payload, record nhị phân |
| `SocketChannel` | Có | Có | Đọc/ghi byte stream TCP, frame giao thức |
| Một `ReadableByteChannel` / `WritableByteChannel` bất kỳ | Chưa chắc | Chưa chắc | Phải xem interface cụ thể mà object triển khai |

Các ví dụ dùng `FileChannel` vì dễ tái lập kết quả; quy tắc buffer vẫn áp dụng khi dùng `SocketChannel`, nhưng I/O mạng có thể trả về ít byte hơn mong đợi, đặc biệt khi non-blocking.

### `offset` và `length` là vị trí trong **mảng buffer**

Với `ByteBuffer[] buffers = {a, b, c, d};`, lời gọi `read(buffers, 1, 2)` chỉ xem xét **b** rồi **c**:

- `offset = 1`: bắt đầu tại phần tử index 1 của **mảng**.
- `length = 2`: xử lý đúng hai phần tử của **mảng**; index kết thúc (exclusive) là 3.
- Không phải offset byte của file, không phải position của buffer và không phải số byte muốn đọc.

Nếu `offset < 0`, `length < 0` hoặc `offset + length` vượt kích thước mảng theo cách không hợp lệ, Java ném `IndexOutOfBoundsException`. Truyền `length=0` nghĩa là không chọn buffer nào và kết quả thao tác là 0. Khi cần truyền toàn bộ mảng, overload không có `offset,length` thuận tiện hơn.

## 3. Nhắc lại trạng thái ByteBuffer

Một `ByteBuffer` có ba chỉ số chính:

- `capacity`: tổng số byte có thể chứa, không đổi.
- `limit`: ranh giới cuối (exclusive) của miền hiện hành.
- `position`: index của thao tác tương đối tiếp theo.

Luôn có: `0 <= position <= limit <= capacity`. Số phần tử còn lại là `remaining() = limit - position`.

Hai trạng thái sử dụng phổ biến:

| Thời điểm | Buffer đích để **channel đọc vào** | Buffer nguồn để **channel ghi ra** |
|---|---|---|
| Chuẩn bị | Thường dùng `clear()` hoặc `compact()` để còn chỗ nhận dữ liệu | Đã `put(...)` rồi `flip()` để chỉ còn payload cần gửi |
| Trong lời gọi I/O | Channel đặt byte vào vùng `[position, limit)` và tăng `position` | Channel lấy byte từ vùng `[position, limit)` và tăng `position` |
| Sau lời gọi | Dùng `flip()` để đọc dữ liệu vừa nhận (khi phù hợp) | Nếu `hasRemaining()` thì vẫn còn dữ liệu chưa ghi |

Ví dụ:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);   // p=0, l=4, c=4
ByteBuffer body = ByteBuffer.allocate(8);     // p=0, l=8, c=8

// Cả hai có thể được dùng làm buffer đích cho scattering read.

ByteBuffer outgoing = ByteBuffer.allocate(4);
outgoing.putInt(3);                            // p=4, l=4
outgoing.flip();                               // p=0, l=4
// outgoing sẵn sàng làm buffer nguồn cho gathering write.
~~~

**Cẩn thận:** `flip()` đưa `limit` bằng `position` cũ và `position=0`. Nếu gọi `flip()` quá sớm trên buffer rỗng vừa cấp phát, `limit` thành 0 và channel sẽ không thể nạp/đọc byte nào vào buffer đích bằng thao tác tương đối.

## 4. Scattering read: phân tán dữ liệu vào nhiều buffer

Lời gọi:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);
ByteBuffer payload = ByteBuffer.allocate(3);

long bytesRead = channel.read(new ByteBuffer[] {header, payload});
~~~

Đây là **đoạn minh họa** giả định `channel` là một `ScatteringByteChannel` đang mở. Một chương trình Java 17 hoàn chỉnh nằm ở mục 7.

Cơ chế:

1. Channel bắt đầu lấy byte từ vị trí đọc hiện tại của nó.
2. Đặt các byte nhận được vào buffer đầu tiên, từ `header.position()` đến trước `header.limit()`.
3. Chỉ khi buffer đầu tiên đã đầy vùng `remaining()` thì mới chuyển sang buffer tiếp theo.
4. Kết quả `long bytesRead` là **tổng số byte thực sự đã chuyển vào các buffer**.
5. `position` của từng buffer nhận byte tăng tương ứng; `limit` không tự đổi.

Giả sử channel chứa 7 byte, việc đọc thực tế trả 5 byte:

~~~text
Trước read:
  header: p=0, l=4, còn 4 byte
  body:   p=0, l=3, còn 3 byte

Đã nhận 5 byte:
  header = [00 00 00 03], p=4, l=4
  body   = [41 .. ..],    p=1, l=3
  return = 5L

Còn thiếu 2 byte payload:
  header.remaining() = 0
  body.remaining()   = 2
~~~

Nếu gọi lại `channel.read(new ByteBuffer[]{header, body})` và 2 byte `42 43` còn trong nguồn, channel sẽ **bỏ qua header không còn chỗ** và tiếp tục nạp vào body. Chỉ khi đã nhận đủ mới `flip()` các buffer để cho parser đọc.

### Một lần read không đảm bảo “đọc đủ”

- Có thể trả về **số dương nhỏ hơn tổng remaining**; đây là hành vi bình thường.
- Có thể trả về **0** nếu không đọc được byte nào ngay lúc đó, đặc biệt với channel non-blocking.
- Có thể trả về **-1** khi gặp cuối luồng (EOF) trước khi nhận byte trong lời gọi đó.
- Với buffer đích chỉ đọc (read-only), việc truyền vào scattering read có thể gây `ReadOnlyBufferException`; buffer đích phải writable.
- Nếu đã chạm `limit` của buffer đầu tiên nhưng frame còn cần một lượng header lớn hơn kích thước của nó, dữ liệu có thể chảy vào buffer thứ hai dù về **ý nghĩa giao thức** đó vẫn là header. Scattering chỉ tuân theo **dung lượng buffer**, không tự hiểu header/payload.

Vì vậy, nếu giao thức nói header đúng 4 byte, hãy chuẩn bị buffer đầu tiên có **remaining đúng 4**, không phải 8, rồi tin rằng parser sẽ tự dừng sau 4 byte.

### Vòng đọc đến khi đủ các vùng bắt buộc

Với **FileChannel đọc blocking** và một file đã biết là có đủ nội dung, có thể lặp đến khi tất cả buffer đầy:

~~~java
static long readExactly(FileChannel channel, ByteBuffer[] dsts)
        throws IOException {
    long total = 0;

    while (hasRemaining(dsts)) {
        long count = channel.read(dsts);
        if (count < 0) {
            throw new EOFException("Tep ket thuc truoc khi doc du");
        }
        if (count == 0) {
            throw new IOException("Khong co tien trien khi doc tep");
        }
        total += count;
    }
    return total;
}

static boolean hasRemaining(ByteBuffer[] buffers) {
    for (ByteBuffer b : buffers) {
        if (b.hasRemaining()) return true;
    }
    return false;
}
~~~

Đây là đoạn phương thức minh họa, cần imports `java.io.*`, `java.nio.*` và `java.nio.channels.*`; mục 7 cung cấp chương trình độc lập.

Đối với **SocketChannel non-blocking**, **không** dùng vòng lặp chạy liên tục khi `read(...) == 0`. Phải tích hợp cơ chế readiness/`Selector` hoặc điều phối khi có dữ liệu, rồi tiếp tục đọc tại `position` đã lưu. Để hạn chế tiêu thụ bộ nhớ, luôn giới hạn kích thước frame/buffer nhận từ nguồn bên ngoài.

### Buffer không nhận byte vẫn giữ position

Nếu source hết dữ liệu trước khi ghi vào một buffer phía sau, `position` của buffer đó giữ nguyên. Khi một buffer đã `remaining() == 0`, nó không nhận thêm byte dù vẫn nằm trong mảng. Nếu tổng `remaining()` bằng 0, đọc scattering không có vùng đích nào để nhận dữ liệu; cần chuẩn bị lại buffer khi muốn tiếp tục.

## 5. Gathering write: gom nhiều buffer thành một luồng ghi

Lời gọi:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);
header.putInt(3);
header.flip(); // Header: 4 byte hợp lệ.

ByteBuffer payload =
        ByteBuffer.wrap(new byte[] {65, 66, 67}); // "ABC"

long bytesWritten = channel.write(
        new ByteBuffer[] {header, payload});
~~~

Đây là đoạn minh họa giả định `channel` là `GatheringByteChannel` có quyền ghi.

Cơ chế:

1. Đọc byte từ vùng `[position, limit)` của buffer đầu tiên.
2. Khi buffer đầu tiên hết byte còn lại, chuyển sang buffer thứ hai rồi tiếp tục.
3. Nối các byte theo **đúng thứ tự mảng** để ghi ra channel.
4. Kết quả `long bytesWritten` là số byte **đã thực sự được channel chấp nhận ghi trong lần gọi**.
5. `position` của những buffer đã tiêu thụ dữ liệu tăng; `limit` không đổi.

Giả sử buffer header có 4 byte và body có 3 byte, nhưng channel chỉ chấp nhận 5 byte:

~~~text
Trước write:
  header: p=0, l=4, remaining=4
  body:   p=0, l=3, remaining=3

Sau write trả 5:
  header: p=4, l=4, remaining=0
  body:   p=1, l=3, remaining=2
  bytes written: 00 00 00 03 41

Lần write tiếp theo bắt đầu:
  header không còn byte -> bỏ qua
  body tiếp tục ở 42, 43
~~~

Đây là đặc tính rất hữu ích: **giữ nguyên các buffer** sau một lần ghi dở rồi gọi lại, các `position` đã ghi nhớ chính xác phần chưa gửi. **Không `rewind()` buffer sau một lần partial write**, vì nó có thể dẫn đến ghi/gửi **lặp dữ liệu**.

### Vòng ghi cho FileChannel

~~~java
static long writeAll(FileChannel channel, ByteBuffer[] srcs)
        throws IOException {
    long total = 0;

    while (hasRemaining(srcs)) {
        long count = channel.write(srcs);
        if (count == 0) {
            throw new IOException("Khong co tien trien khi ghi tep");
        }
        total += count;
    }
    return total;
}
~~~

Với `FileChannel` dùng trong ví dụ, vòng này minh họa việc ghi đủ dữ liệu trước khi hoàn thành. Nếu dùng `SocketChannel` non-blocking, `write(...) == 0` là một tình huống bình thường; **đợi kênh có thể ghi** (ví dụ với `Selector.OP_WRITE`), giữ các buffer/position qua các sự kiện rồi gọi lại. Không đóng hoặc tái sử dụng buffer khi còn `hasRemaining()`.

### Không tự đảm bảo tính nguyên tử

Một lời gọi gathering write không tự tạo ranh giới thông điệp trên TCP. Dữ liệu có thể được truyền thành nhiều phần, và phía nhận có thể đọc theo bất kỳ cách chia nào. Hai bản ghi do hai tác nhân khác nhau ghi không mặc nhiên tạo nên một giao dịch được bảo vệ; nếu cần tránh xen kẽ, cần phối hợp quyền ghi/cấu trúc protocol phù hợp.

Không nên khẳng định một lời gọi gathering luôn tương ứng **đúng một system call** `writev`: Java đưa ra **hợp đồng dữ liệu và buffer**, còn triển khai và OS quyết định cách thực hiện thực tế.

## 6. Hành vi của `position`, `limit`, `mark` và kết quả trả về

| Đặc điểm | Scattering read | Gathering write |
|---|---|---|
| Hướng dữ liệu | Channel → nhiều buffer đích | Nhiều buffer nguồn → Channel |
| Byte hợp lệ được chuyển | Vào vùng `[position, limit)` của từng buffer | Ra từ vùng `[position, limit)` của từng buffer |
| Thứ tự | Buffer index thấp đến cao | Buffer index thấp đến cao |
| `position` | Tăng theo byte được nạp | Tăng theo byte đã gửi |
| `limit`, `capacity` | Không được điều chỉnh tự động | Không được điều chỉnh tự động |
| Mark | Có thể bị mất hiệu lực nếu vị trí thay đổi vượt qua quy tắc mark; I/O không đặt mark mới | Tương tự |
| Kết quả | `long` số byte nhận; 0 khi chưa có byte; -1 khi EOF | `long` số byte chấp nhận ghi; có thể 0 |
| Buffer read-only | Không thể dùng làm vùng đích để nạp byte | Có thể làm nguồn vì channel chỉ đọc nội dung buffer |
| Khi xong | Thường `flip()` các buffer cần parser đọc | Kiểm tra `hasRemaining()`; khi hết có thể `clear()`/nạp tiếp |

Các phương thức `read/write` không tự `flip()` hoặc `clear()` hộ ứng dụng.

**Chi tiết về mark:** vị trí sau I/O thường tiến về phía trước nên một mark cũ (nếu hợp lệ) thường vẫn có thể sử dụng. Tuy nhiên, đừng giả định một mark là điểm rollback cho dữ liệu đã tiêu thụ trên channel: `reset()` chỉ thay đổi vị trí của **buffer**, không tua ngược byte của socket hoặc channel nguồn. Việc quay lại buffer cũng không lấy lại các byte đã được hệ điều hành chấp nhận ghi ra ngoài.

## 7. Chương trình Java 17+ hoàn chỉnh

Chương trình thực hiện các bước:

1. Tạo file tạm.
2. Tạo buffer header bốn byte ghi số nguyên `3` (big-endian) và buffer payload ba byte chứa `ABC`.
3. Ghi bằng **gathering write** cho đến khi cả hai buffer hết `remaining()`.
4. Mở file để đọc; tạo hai buffer đích có dung lượng 4 và 3 byte.
5. Đọc bằng **scattering read** cho đến khi đủ 7 byte.
6. `flip()` các buffer đích rồi giải mã header và payload, kiểm tra kết quả.

Lưu file là **`ScatteringGatheringDemo.java`**:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ScatteringGatheringDemo {

    private static boolean hasRemaining(ByteBuffer[] buffers) {
        for (ByteBuffer b : buffers) {
            if (b.hasRemaining()) {
                return true;
            }
        }
        return false;
    }

    private static long writeAll(
            FileChannel channel, ByteBuffer[] sources)
            throws IOException {
        long total = 0;
        while (hasRemaining(sources)) {
            long count = channel.write(sources);
            if (count == 0) {
                throw new IOException("No progress while writing");
            }
            total += count;
        }
        return total;
    }

    private static long readExactly(
            FileChannel channel, ByteBuffer[] destinations)
            throws IOException {
        long total = 0;
        while (hasRemaining(destinations)) {
            long count = channel.read(destinations);
            if (count < 0) {
                throw new EOFException("Incomplete record");
            }
            if (count == 0) {
                throw new IOException("No progress while reading");
            }
            total += count;
        }
        return total;
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("gather-scatter-", ".bin");
        try {
            ByteBuffer header = ByteBuffer.allocate(Integer.BYTES);
            header.putInt(3);
            header.flip();

            ByteBuffer body = ByteBuffer.wrap(
                    "ABC".getBytes(StandardCharsets.UTF_8));

            try (FileChannel out = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                long count = writeAll(
                        out, new ByteBuffer[] {header, body});
                System.out.println("gathered=" + count);
            }

            System.out.println(
                    "source positions="
                    + header.position() + "/" + header.limit()
                    + "," + body.position() + "/" + body.limit());

            ByteBuffer readHeader =
                    ByteBuffer.allocate(Integer.BYTES);
            ByteBuffer readBody = ByteBuffer.allocate(3);

            try (FileChannel in = FileChannel.open(
                    file, StandardOpenOption.READ)) {
                long count = readExactly(
                        in, new ByteBuffer[] {readHeader, readBody});
                System.out.println("scattered=" + count);
            }

            System.out.println(
                    "target positions="
                    + readHeader.position() + "/" + readHeader.limit()
                    + "," + readBody.position() + "/" + readBody.limit());

            readHeader.flip();
            readBody.flip();

            int bodyLength = readHeader.getInt();
            byte[] bytes = new byte[readBody.remaining()];
            readBody.get(bytes);

            System.out.println("header length=" + bodyLength);
            System.out.println(
                    "payload=" + new String(bytes, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa source:

~~~bash
javac ScatteringGatheringDemo.java
java ScatteringGatheringDemo
~~~

**Stdout khi chương trình thành công:**

~~~text
gathered=7
source positions=4/4,3/3
scattered=7
target positions=4/4,3/3
header length=3
payload=ABC
~~~

**Giải thích:**

- Header là `00 00 00 03`; payload là `41 42 43` (UTF-8 cho ASCII `ABC`). Tổng số byte là `4 + 3 = 7`.
- `gathered=7` vì helper lặp đến khi ghi đủ; sau đó cả hai buffer nguồn có `position == limit`.
- Hai buffer đích bắt đầu ở `position=0`; `scattered=7` vì helper lặp đến khi nhận đủ 4 và 3 byte.
- Trước `flip()` các buffer đích có `position=4` và `position=3`. `flip()` đặt `position=0` để đọc chính xác phần vừa nạp.
- `getInt()` giải mã header theo big-endian mặc định của `ByteBuffer`, nhận giá trị 3; ba byte tiếp theo tạo chuỗi `ABC`.

Bài demo biết trước body dài 3 nên tạo `ByteBuffer.allocate(3)`. **Một parser giao thức thực tế không nên cấp phát dung lượng body chỉ từ dữ liệu không tin cậy mà chưa xác thực**, và cần xử lý EOF/timeout/thiếu dữ liệu.

## 8. Frame có độ dài động: khi nào không dùng một lần scattering?

Giả sử protocol gửi:

~~~text
[4 byte unsigned/signed length] [payload có N byte]
~~~

Kích thước payload chỉ biết **sau khi đọc header**. Nếu cố đoán `ByteBuffer.allocate(4096)` làm body trong một lần scattering, có ba vấn đề:

1. Body thực tế có thể **ngắn hơn 4096**; buffer không tự nhận diện ranh giới thông điệp. Nó có thể chứa cả byte của thông điệp kế tiếp.
2. Body thực tế có thể **dài hơn 4096**; bộ nhớ đích không đủ và cần bổ sung buffer.
3. Dữ liệu có thể đến thành nhiều phần, bất kể bên gửi dùng gathering write. Đây là **byte stream** (đặc biệt với TCP), không phải chuỗi “một `write` ↔ một `read`”.

Phương án phổ biến:

1. Tạo `ByteBuffer.allocate(4)` cho header, đọc **đến khi đủ 4 byte** bằng read bình thường hoặc scattering trong một state machine.
2. `flip()`, giải mã chiều dài (ví dụ `getInt()`) và **xác minh** `0 <= n <= MAX_FRAME_SIZE`.
3. Cấp buffer body theo `n` hoặc nhận theo từng phần có giới hạn; đọc body đến khi đủ.
4. Giải mã xong một frame mới bắt đầu frame kế tiếp, giữ phần byte chưa tiêu thụ nếu đã đọc vượt ranh giới.

Đoạn kiểm tra đơn giản:

~~~java
// Minh họa; header đã đọc đủ 4 byte và flip() để đọc.
int size = header.getInt();
int maxFrame = 1024 * 1024;
if (size < 0 || size > maxFrame) {
    throw new IOException("Invalid frame length: " + size);
}
ByteBuffer payload = ByteBuffer.allocate(size);
~~~

Nếu phải xử lý nhiều frame trong một luồng, có thể dùng một buffer tổng và parser riêng hoặc một state machine `READ_HEADER → READ_BODY → PROCESS_FRAME`. Scattering read rất có ích khi **biết trước các phần cố định** hoặc đã xác định dung lượng tương ứng; nó **không thay thế framing logic**.

### Xử lý trường hợp non-blocking bằng state machine

Với `SocketChannel.configureBlocking(false)`:

- `read(buffers) == 0`: chưa nhận thêm byte; **giữ `position`**, chờ `OP_READ` và thử lại sau.
- `read(buffers) > 0`: cập nhật số byte đã nhận, kiểm tra đủ header/body và chuyển trạng thái parser tương ứng.
- `read(buffers) == -1`: phía bên kia đã đóng luồng đầu vào; xử lý frame đang dở theo protocol.
- `write(buffers) == 0`: hệ điều hành chưa nhận thêm byte; lưu lại buffer trong hàng đợi outbound, đăng ký `OP_WRITE` khi cần, rồi tiếp tục từ `position` hiện tại.

Tránh giữ `OP_WRITE` được đăng ký liên tục khi không có pending outbound data, vì socket thường sẵn sàng ghi và selector có thể đánh thức liên tục gây lãng phí CPU.

## 9. Scattering/gathering so với nhiều lời gọi read/write

Hai cách có thể sinh cùng chuỗi byte:

~~~java
// A. Nhiều lệnh, sử dụng ByteBuffer riêng.
channel.write(header);
channel.write(body);

// B. Gathering write.
channel.write(new ByteBuffer[] {header, body});
~~~

**Đoạn minh họa** trên giả định buffer nguồn đã `flip()` đúng. Không chạy hai phương án liên tiếp trên **cùng buffer** nếu muốn so sánh, vì phương án A đã dịch `position` đến cuối, khiến B còn 0 byte.

Một API theo mảng buffer thường giúp:

- Tránh bước **tự ghép/copy** header và body thành một mảng lớn trước khi ghi.
- Giữ các thành phần trong buffer riêng, thuận tiện sửa từng thành phần khi tạo thông điệp.
- Có khả năng dùng cơ chế scatter/gather I/O được OS hỗ trợ, giảm overhead trong một số điều kiện.

Nhưng không nên kết luận:

- Mỗi lời gọi scattering/gathering luôn tạo một syscall duy nhất.
- Hiệu năng luôn cao hơn nhiều lời gọi đơn lẻ. Kích thước payload, số buffer, loại channel, OS, kernel, bộ đệm và trạng thái mạng đều ảnh hưởng kết quả.
- Có bảo đảm “all-or-nothing”: các buffer có thể chỉ được đọc/ghi một phần.
- Chỉ dùng gathering write là bên nhận tự tách được header/body. **Receiver phải áp dụng cùng giao thức và quy tắc framing**.

Đo benchmark trên dữ liệu thực tế nếu đang tối ưu hiệu năng; ưu tiên code chính xác và xử lý partial I/O trước.

## 10. Lỗi thường gặp và cách tránh

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| Gọi `write(srcs)` rồi mặc định tất cả đã gửi | Mất phần dữ liệu chưa ghi khi đóng/tái sử dụng buffer | Kiểm tra tổng remaining; tiếp tục khi channel sẵn sàng |
| Gọi `read(dsts)` một lần và parse ngay | Header/body thiếu byte, `BufferUnderflowException` hoặc dữ liệu sai | Duy trì state và đọc đủ theo giao thức |
| Quên `flip()` buffer nguồn sau `put(...)` | Gathering write lấy cả phần chưa ghi hợp lệ của buffer | `flip()` trước write |
| Quên `flip()` buffer đích sau read hoàn tất | Parser đọc từ cuối vùng mới nhận | `flip()` buffer đích trước parse |
| Gọi `rewind()` sau partial write | Gửi lặp các byte đã được channel tiêu thụ | Giữ `position`; chỉ viết tiếp phần remaining |
| Kích thước header buffer lớn hơn header thật | Header “ăn” sang body theo quy tắc lấp đầy | Đặt remaining đúng số byte header mong đợi |
| Nhầm `offset,length` với offset/byte-count | Đọc/ghi nhầm buffer trong mảng | Nhớ đó là **chỉ số mảng và số phần tử mảng** |
| Cho phép body size từ mạng quá lớn/âm | Cấp phát khổng lồ, lỗi bộ nhớ, parser sai | Giới hạn frame size trước `allocate` |
| Spin khi non-blocking read/write trả 0 | CPU tăng cao dù không có tiến triển | Dùng readiness/`Selector` và lưu buffer state |
| Gửi dữ liệu qua TCP rồi mong một read nhận đúng một frame | Đọc dở hoặc gộp nhiều frame | Dùng length-prefix/delimiter và state machine |
| Truyền buffer read-only làm đích | Lỗi khi cố nạp byte vào buffer | Dùng buffer có thể ghi cho scattering read |
| Dùng chung mảng buffer cho nhiều luồng không đồng bộ | `position` của mỗi buffer và thứ tự message bị cạnh tranh | Một owner cho session/buffer hoặc đồng bộ/queue rõ ràng |

## 11. Câu hỏi tự kiểm tra

1. Hai buffer đích có `remaining()` lần lượt 4 và 3. Scattering read nhận đúng 5 byte. `position` tăng bao nhiêu? **4 ở buffer đầu, 1 ở buffer sau**; còn 2 byte chờ đọc.
2. Gathering write với hai buffer nguồn có remaining 4 và 3, nhưng `write` trả 2. Byte nào sẽ được ghi lần sau? **Phần còn lại của buffer thứ nhất trước**, rồi đến buffer thứ hai.
3. Dùng `read(buffers, 1, 2)` với mảng bốn buffer, thao tác xét buffer nào? **Index 1 và index 2**, không xét 0 hay 3.
4. `write(new ByteBuffer[]{header, payload})` có đảm bảo một lời gọi truyền đủ frame qua TCP không? **Không.** Vẫn phải xử lý partial write và framing.
5. Khi `channel.read(dsts) == 0` ở non-blocking mode, có nên gọi lặp không chờ? **Không.** Giữ state và chờ readiness.
6. Scattering read tự tách header 4 byte và payload động dài N mà không cần biết N không? **Không.** Buffer chỉ quyết định dựa trên remaining; ứng dụng phải đọc header, giải mã và xác thực N.
7. Sau scattering read vào hai buffer đầy, cần làm gì trước khi `getInt()` từ header? **`header.flip()`** (và `body.flip()` nếu muốn đọc body).
8. Một lần gathering ghi dở; cần gọi `rewind()` vào tất cả source buffer để thử lại không? **Không.** Làm thế sẽ gửi trùng dữ liệu.
9. Gathering write có tự động xóa, đóng hay thay đổi `limit` của buffer không? **Không.** Nó chủ yếu dịch `position` theo số byte thực sự được chuyển.
10. Vì sao scattering/gathering có thể hữu ích mà vẫn cần benchmark? **Có thể giảm bước copy và overhead I/O**, nhưng chi phí và số syscall cụ thể phụ thuộc cách triển khai và môi trường.

## 12. Tham khảo API chính thức

- [Java SE 17 — ScatteringByteChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ScatteringByteChannel.html): contract về thứ tự buffer, kết quả `long` và partial read.
- [Java SE 17 — GatheringByteChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/GatheringByteChannel.html): contract về thứ tự ghi, `position` và partial write.
- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): API I/O tệp hỗ trợ read/write qua mảng buffer.
- [Java SE 17 — SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): I/O socket, blocking/non-blocking và các thao tác trên buffer.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): `position`, `limit`, `flip`, `remaining`, đọc/ghi binary.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-008">Channels.newChannel, newInputStream và newOutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# `Channels.newChannel()`, `newInputStream()` và `newOutputStream()` trong Java NIO

## 1. Mục tiêu và bối cảnh

Java cung cấp hai nhóm API I/O thường gặp:

- **`java.io.InputStream` / `OutputStream`:** làm việc với chuỗi byte bằng `read()`, `read(byte[])`, `write(int)`, `write(byte[])`.
- **`java.nio.channels.ReadableByteChannel` / `WritableByteChannel`:** làm việc với byte thông qua `ByteBuffer` bằng `read(ByteBuffer)` và `write(ByteBuffer)`.

Khi thư viện cũ yêu cầu stream nhưng ứng dụng đang giữ channel, hoặc ngược lại, bạn không cần chép toàn bộ tệp vào RAM để đổi kiểu API. Lớp tiện ích **`java.nio.channels.Channels`** cung cấp các adapter giữ kết nối tới nguồn/đích I/O gốc.

Bài này giúp bạn:

1. Chọn đúng overload `Channels.newChannel(InputStream/OutputStream)`, `Channels.newInputStream(ReadableByteChannel)` và `Channels.newOutputStream(WritableByteChannel)`.
2. Hiểu luồng byte, quy tắc `ByteBuffer.flip()/clear()` và hành vi đọc/ghi một phần.
3. Quản lý đúng vòng đời: đóng adapter sẽ đóng I/O nguồn/đích phía dưới.
4. Nhận diện giới hạn blocking/nonblocking, việc không có bộ đệm bổ sung, encoding khi chuyển từ byte sang text và việc một adapter không biến stream thành channel ngẫu nhiên truy cập.
5. Chạy ví dụ Java 17+ kết nối hai hướng, kể cả `FileChannel` và `ByteArrayInputStream`.

Đây là bài về **kết nối API byte I/O**, không phải tạo một kiểu hệ thống tệp mới hay so sánh toàn bộ kiến trúc stream với channel.

## 2. Bản đồ chuyển đổi hai chiều

```text
                    Channels.newChannel(...)
  InputStream  ---------------------------------->  ReadableByteChannel
   read(byte[])                                      read(ByteBuffer)

                    Channels.newChannel(...)
  OutputStream ---------------------------------->  WritableByteChannel
   write(byte[])                                     write(ByteBuffer)


                    Channels.newInputStream(...)
  ReadableByteChannel -------------------------->  InputStream
   read(ByteBuffer)                                  read(byte[])

                    Channels.newOutputStream(...)
  WritableByteChannel -------------------------->  OutputStream
   write(ByteBuffer)                                 write(byte[])
```

Các kiểu và phương thức:

| Đầu vào | Hàm adapter | Kết quả | Hướng byte |
| --- | --- | --- | --- |
| `InputStream` | `Channels.newChannel(in)` | `ReadableByteChannel` | Đọc từ stream vào `ByteBuffer` |
| `OutputStream` | `Channels.newChannel(out)` | `WritableByteChannel` | Ghi từ `ByteBuffer` tới stream |
| `ReadableByteChannel` | `Channels.newInputStream(ch)` | `InputStream` | Đọc từ channel vào byte array hoặc một byte |
| `WritableByteChannel` | `Channels.newOutputStream(ch)` | `OutputStream` | Ghi byte array hoặc một byte tới channel |

`Channels` là một **lớp tiện ích có phương thức static**; ví dụ `Channels.newChannel(input)`, không dùng `new Channels()`. Những phương thức này không tự di chuyển con trỏ đọc của tệp về đầu, không nhân bản tệp hay tạo kết nối mạng khác. Chúng tạo một đối tượng nhìn vào nguồn/đích đã có.

Các adapter vẫn tuân thủ hướng của giao diện gốc. `newChannel(InputStream)` chỉ cho bạn giao diện **đọc**; không thể gọi `write()` trên kết quả ấy. Tương tự, `newChannel(OutputStream)` chỉ trả giao diện **ghi**.

## 3. `Channels.newChannel(InputStream)`: stream thành channel đọc

Chữ ký:

```java
public static ReadableByteChannel newChannel(InputStream in)
```

Adapter cho phép code đang nhận `ReadableByteChannel` tiêu thụ một `InputStream` đã có. Thay vì đọc vào `byte[]`, bên gọi sẽ đọc vào `ByteBuffer`:

```java
byte[] bytes = "ABC".getBytes(StandardCharsets.US_ASCII);
InputStream source = new ByteArrayInputStream(bytes);
ReadableByteChannel channel = Channels.newChannel(source);

ByteBuffer buffer = ByteBuffer.allocate(8);
int count = channel.read(buffer); // 3 byte trong ví dụ
buffer.flip();
while (buffer.hasRemaining()) {
    System.out.print((char) buffer.get());
}
channel.close(); // đóng cả source
```

Đoạn mã này là **minh họa cần import** (không phải chương trình độc lập). Nó cho thấy đường đi của byte:

1. `InputStream` cung cấp các byte `A`, `B`, `C`.
2. `ReadableByteChannel.read(buffer)` đưa byte vào khoảng `[position, limit)` còn trống và tăng `position` theo số byte đã đưa vào.
3. `buffer.flip()` chuẩn bị đọc lại số byte vừa nạp bằng `get()`.
4. `channel.close()` đóng luôn stream nguồn theo hợp đồng adapter.

`read(ByteBuffer)` trả số byte thực đọc, có thể nhỏ hơn `buffer.remaining()` trước khi gọi. `-1` biểu thị đã hết dữ liệu đầu vào (EOF). Nếu buffer không còn vị trí để nhận dữ liệu, yêu cầu đọc 0 byte có thể trả 0; nó không có nghĩa đã gặp EOF. Đừng mặc định mỗi `read()` luôn lấp đầy buffer, kể cả khi nguồn có thêm dữ liệu.

**Adapter không thêm buffering riêng.** Nếu nguồn là `BufferedInputStream`, buffer của chính `BufferedInputStream` vẫn tồn tại; câu “không buffer” của adapter nghĩa là nó không tự giới thiệu thêm một lớp bộ đệm có chính sách lưu dữ liệu độc lập.

## 4. `Channels.newChannel(OutputStream)`: stream thành channel ghi

Chữ ký:

```java
public static WritableByteChannel newChannel(OutputStream out)
```

Khi một API muốn nhận `WritableByteChannel` nhưng bạn chỉ có `OutputStream`, tạo adapter:

```java
ByteArrayOutputStream destination = new ByteArrayOutputStream();
WritableByteChannel channel = Channels.newChannel(destination);

ByteBuffer buffer = ByteBuffer.wrap(new byte[] {65, 66, 67});
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written == 0) {
        throw new IOException("Write made no progress");
    }
}
channel.close(); // đóng cả destination
System.out.println(destination.toString(StandardCharsets.US_ASCII));
```

Đoạn này là minh họa cần import và xử lý `IOException`; với `ByteArrayOutputStream`, việc đóng không làm mất những byte đã ghi. Output:

```text
ABC
```

Chữ ký `write(ByteBuffer)` trả số byte được ghi thực tế và tăng position của buffer. Theo hợp đồng `WritableByteChannel` nói chung, bên gọi cần xử lý **partial write** khi kết quả nhỏ hơn số byte ban đầu còn lại. Dùng `while (buffer.hasRemaining())` và có chiến lược khi không có tiến triển, tránh vòng lặp vô hạn với channel không chặn. Adapter từ `OutputStream` chuyển hướng việc ghi vào stream gốc; nó không biến đối tượng `OutputStream` thành `FileChannel` có `position(long)`, `truncate()` hoặc `transferTo()`.

Nếu chương trình chỉ cần chép một `InputStream` vào một `OutputStream`, `InputStream.transferTo(OutputStream)` có thể đơn giản hơn. Adapter hữu ích khi **chữ ký của API phải dùng channel** hoặc bạn đang xử lý dữ liệu qua `ByteBuffer`.

## 5. Ví dụ Java 17+ hoàn chỉnh: stream → channel → stream đích

Lưu chương trình sau thành **`StreamToChannelDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public class StreamToChannelDemo {
    public static void main(String[] args) throws IOException {
        byte[] input = "Xin chào NIO".getBytes(StandardCharsets.UTF_8);

        ByteArrayInputStream source = new ByteArrayInputStream(input);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (ReadableByteChannel from = Channels.newChannel(source);
             WritableByteChannel to = Channels.newChannel(destination)) {

            ByteBuffer buffer = ByteBuffer.allocate(4);

            while (true) {
                int read = from.read(buffer);
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    throw new IOException("Unexpected read without progress");
                }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = to.write(buffer);
                    if (written == 0) {
                        throw new IOException("Write made no progress");
                    }
                }
                buffer.clear();
            }
        }

        System.out.println("bytes=" + destination.size());
        System.out.println("text="
                + destination.toString(StandardCharsets.UTF_8));
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac StreamToChannelDemo.java
java StreamToChannelDemo
```

Stdout (UTF-8):

```text
bytes=13
text=Xin chào NIO
```

Giải thích:

- Chuỗi `Xin chào NIO` chứa 12 ký tự Java ở mức code point thông thường, nhưng UTF-8 mã hóa `à` bằng hai byte; vì vậy dữ liệu có **13 byte**. Cách đếm dung lượng dựa vào `byte[]` thực tế chứ không giả định `String.length()` bằng số byte.
- Buffer chỉ 4 byte để minh họa **nhiều vòng lặp**; UTF-8 có thể bị cắt giữa ký tự khi vận chuyển các chunk. Điều này **không làm hỏng dữ liệu** vì chương trình ghép nguyên vẹn byte rồi mới giải mã ở cuối.
- `from.read(buffer)` thay đổi position; `flip()` đổi từ nạp vào buffer sang đọc từ buffer; `to.write(buffer)` tiêu thụ các byte; `clear()` chuẩn bị nhận chunk mới.
- `try-with-resources` đóng hai channel, từ đó đóng các stream gốc theo hợp đồng adapter. Các thao tác `ByteArrayOutputStream.size()` và `toString(...)` vẫn dùng được sau `close()` vì loại stream này duy trì bộ nhớ đã ghi.

**Không dùng `new String(buffer.array(), UTF_8)` trên toàn bộ capacity sau mỗi `read()`:** phần còn lại của mảng có thể không chứa byte vừa nhận, và ký tự UTF-8 có thể nằm vắt qua hai chunk. Nếu ứng dụng phải chuyển **byte thành text khi đang streaming**, dùng `CharsetDecoder` có trạng thái hoặc lớp `Reader` phù hợp.

## 6. `Channels.newInputStream(ReadableByteChannel)`: channel thành stream đọc

Chữ ký:

```java
public static InputStream newInputStream(ReadableByteChannel ch)
```

Adapter này hữu ích khi code mới đang mở dữ liệu bằng `FileChannel` hoặc một `ReadableByteChannel`, nhưng thư viện cần tham số `InputStream`:

```java
ReadableByteChannel channel = FileChannel.open(path, StandardOpenOption.READ);
try (InputStream input = Channels.newInputStream(channel)) {
    byte[] bytes = input.readAllBytes();
    // Xử lý bytes; đóng input cũng đóng channel.
}
```

Đây là minh họa không độc lập; giả định `path` đã khai báo, trỏ đến tệp phù hợp, có các import cần thiết và phương thức bao quanh xử lý `IOException`. `readAllBytes()` hợp lý cho **dữ liệu nhỏ, có giới hạn**; tránh dùng để kéo cả file lớn hoặc đầu vào mạng vô hạn vào bộ nhớ. Có thể thay bằng vòng đọc `byte[]` theo chunk cố định.

Hợp đồng quan trọng của stream adapter này:

- `read()` và `read(byte[], off, len)` vận chuyển byte từ channel; khi channel đến EOF, lời gọi `read()` theo hợp đồng stream trả `-1`.
- Stream kết quả **không có buffering bổ sung**.
- Stream kết quả **không hỗ trợ `mark()` và `reset()`**; `markSupported()` trả `false`. Nếu thực sự cần hỗ trợ đánh dấu, cân nhắc `BufferedInputStream` và hiểu giới hạn readlimit của nó.
- Stream được công bố là **an toàn khi nhiều thread truy cập các phương thức của adapter**; điều này không biến toàn bộ resource, giao thức hay việc phối hợp với I/O từ các object khác thành thread-safe.
- **Đóng stream sẽ đóng channel nền**. Không dùng lại channel đó sau khi stream đóng.

Nếu một `FileChannel` có vị trí hiện tại khác 0, adapter sẽ đọc từ vị trí hiện tại ấy; nó không chủ động `position(0)` hoặc giải thích cấu trúc record thay bạn. Nếu cùng channel bị thao tác qua nhiều đường tham chiếu, việc thay đổi position có thể ảnh hưởng đến byte tiếp theo adapter đọc.

## 7. `Channels.newOutputStream(WritableByteChannel)`: channel thành stream ghi

Chữ ký:

```java
public static OutputStream newOutputStream(WritableByteChannel ch)
```

Bây giờ thư viện chỉ biết `OutputStream.write(byte[])` vẫn có thể ghi tới một channel đang có:

```java
WritableByteChannel channel = FileChannel.open(
        path, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

try (OutputStream output = Channels.newOutputStream(channel)) {
    output.write("hello".getBytes(StandardCharsets.UTF_8));
}
```

Đoạn minh họa cần `path`, import và xử lý `IOException`. Quy tắc:

- `OutputStream.write(byte[], offset, length)` chuyển byte đến channel nền; adapter xử lý các thao tác ghi theo hợp đồng stream và byte channel.
- `newOutputStream` **không tự thêm buffering**, nên nếu muốn gom nhiều lệnh ghi nhỏ, có thể cân nhắc bọc `BufferedOutputStream` nhưng cần hiểu chính sách `flush()/close()`.
- **Đóng OutputStream đóng WritableByteChannel**.
- `flush()` ở tầng stream không đồng nghĩa file đã được đồng bộ bền vững ra thiết bị vật lý. Khi ứng dụng yêu cầu durability, cần xét `FileChannel.force(boolean)` hoặc API phù hợp, cũng như đặc tính hệ thống tệp.
- Nếu channel nền đang ở vị trí nào đó và có khả năng định vị, byte sẽ được ghi theo vị trí/trạng thái channel; adapter không tự mở tệp ở chế độ append hoặc reset vị trí.

`OutputStream` không có phương thức `position(long)`: adapter không biến channel có khả năng seek thành stream có khả năng seek. Nếu nghiệp vụ cần sửa byte tại offset, hãy sử dụng trực tiếp `SeekableByteChannel` hoặc `FileChannel`.

## 8. Ví dụ Java 17+ hoàn chỉnh: `FileChannel` → `OutputStream` / `InputStream`

Lưu dưới tên **`ChannelToStreamDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChannelToStreamDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("channel-stream-", ".txt");

        try {
            byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);

            FileChannel writable = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            try (OutputStream output = Channels.newOutputStream(writable)) {
                output.write(bytes);
            }
            System.out.println("write channel open=" + writable.isOpen());

            FileChannel readable = FileChannel.open(
                    file, StandardOpenOption.READ);

            byte[] copy;
            try (InputStream input = Channels.newInputStream(readable)) {
                copy = input.readAllBytes(); // chỉ vì ví dụ này rất nhỏ
            }
            System.out.println("read channel open=" + readable.isOpen());
            System.out.println("UTF-8 bytes=" + copy.length);
            System.out.println("text=" + new String(copy, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac ChannelToStreamDemo.java
java ChannelToStreamDemo
```

Stdout khi các thao tác I/O thành công:

```text
write channel open=false
read channel open=false
UTF-8 bytes=5
text=café
```

`café` có 5 byte UTF-8: ba chữ `c`, `a`, `f` chiếm mỗi chữ một byte, còn `é` chiếm hai byte. Hai dòng `isOpen=false` kiểm tra trực tiếp quan hệ đóng tài nguyên: đối tượng adapter là đối tượng được đóng trong `try-with-resources`, nhưng channel mà nó bọc cũng chuyển sang trạng thái đóng.

Mục đích của `Files.createTempFile` là tạo dữ liệu thử nghiệm ở vùng tạm, sau khi cả hai channel đóng mới xóa. Nếu quyền ghi, quyền đọc, xóa file hoặc dung lượng thiết bị không cho phép, `IOException` có thể xảy ra; đây là trường hợp lỗi I/O cần được xử lý ở ứng dụng thực tế.

## 9. Blocking và nonblocking: ranh giới quan trọng nhất

Một số channel hỗ trợ chế độ **nonblocking**, ví dụ `SocketChannel` hoặc `Pipe.SourceChannel` / `Pipe.SinkChannel` là những `SelectableChannel`. Trong nonblocking mode, lời gọi `read(ByteBuffer)` / `write(ByteBuffer)` có thể trả 0 khi hiện chưa có dữ liệu hoặc chưa thể ghi.

Trong khi đó, các adapter:

- `Channels.newInputStream(ReadableByteChannel)`
- `Channels.newOutputStream(WritableByteChannel)`

được thiết kế cho giao diện stream thông thường. Theo Java 17 API, các phương thức `read` / `write` của các adapter này sẽ ném **`IllegalBlockingModeException`** khi channel nền đang ở **nonblocking mode** tại thời điểm thao tác.

Vì vậy, không thể dùng `Channels.newInputStream(nonblockingSocketChannel)` như một cách né quy trình `Selector`. Nếu ứng dụng có event loop và channel nonblocking, tiếp tục dùng API channel theo đúng chiến lược readiness; đổi sang stream chỉ phù hợp khi lối truy cập blocking thật sự đúng cho thiết kế.

### Ví dụ xác nhận `IllegalBlockingModeException` (Java 17+)

Lưu thành **`NonBlockingAdapterDemo.java`**:

```java
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;

public class NonBlockingAdapterDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {

            source.configureBlocking(false);

            try (InputStream input = Channels.newInputStream(source)) {
                try {
                    input.read();
                    System.out.println("unexpected read");
                } catch (IllegalBlockingModeException expected) {
                    System.out.println("nonblocking channel rejected");
                }
            }
        }
    }
}
```

```bash
javac NonBlockingAdapterDemo.java
java NonBlockingAdapterDemo
```

Stdout theo hợp đồng khi chạy thành công:

```text
nonblocking channel rejected
```

`Pipe` được tạo nhưng không có byte nào được ghi vào sink; không hề cần đợi dữ liệu xuất hiện: adapter từ channel nonblocking từ chối thao tác đọc. `try-with-resources` đóng stream và đồng thời đóng source; việc đóng source lần nữa bởi outer `try` vẫn hợp lệ.

Cách xử lý đúng nếu ứng dụng thực sự cần chờ dữ liệu là dùng `source.configureBlocking(true)` trước khi truy cập kiểu stream (phải bảo đảm không xung đột với các đăng ký selector và luồng khác), hoặc tiếp tục đọc theo mô hình nonblocking với readiness/event loop thích hợp. **Không** vòng lặp gọi `input.read()` liên tục và bắt `IllegalBlockingModeException` để chờ I/O.

## 10. Overload cho `AsynchronousByteChannel`

Ngoài các adapter cho `ReadableByteChannel` và `WritableByteChannel`, `Channels` trong Java 17 còn cung cấp:

```java
InputStream  Channels.newInputStream(AsynchronousByteChannel ch)
OutputStream Channels.newOutputStream(AsynchronousByteChannel ch)
```

Ví dụ `AsynchronousSocketChannel` triển khai `AsynchronousByteChannel` và có thể dùng trực tiếp với các overload trên. Trái lại, **`AsynchronousFileChannel` không triển khai `AsynchronousByteChannel`**: các phương thức đọc/ghi bất đồng bộ của nó yêu cầu vị trí file (`read(buffer, position)` / `write(buffer, position)`), nên không thể truyền đối tượng này trực tiếp vào hai overload nhận `AsynchronousByteChannel`. Khi chương trình cần ghép một asynchronous byte channel với thư viện chỉ nhận `InputStream` / `OutputStream`, adapter sẽ cung cấp giao diện stream phù hợp.

Lưu ý về ý nghĩa: **API stream gọi đồng bộ từ thread sử dụng stream**; việc bọc một asynchronous channel không tự biến các lời gọi `InputStream.read()` / `OutputStream.write()` thành API nonblocking event-driven. Nếu muốn tận dụng callback hoặc `Future`, nên dùng trực tiếp các phương thức bất đồng bộ của channel.

Bốn điều vẫn cần nhớ với overload này: không có buffering bổ sung, `newInputStream` không hỗ trợ `mark/reset`, đóng stream đóng asynchronous channel, và phải xử lý lỗi I/O theo hợp đồng của stream. Không nhầm hai overload này với cách bọc một `SelectableChannel` đang cấu hình nonblocking ở mục trước.

## 11. Encoding và buffering khi chuyển đổi sang API text

`InputStream`, `OutputStream` và các byte channel **chỉ vận chuyển byte**. `Channels.newInputStream` / `newOutputStream` không nhận tham số charset và không giải mã hoặc mã hóa UTF-8 giùm ứng dụng.

Để đọc text UTF-8 khi thư viện cần `Reader`:

```java
Reader reader = new InputStreamReader(
        Channels.newInputStream(channel),
        StandardCharsets.UTF_8);
```

Đây là snippet minh họa: cần import `Reader`, `InputStreamReader`, `StandardCharsets`, khai báo `channel` là `ReadableByteChannel` và đóng `reader` đúng cách. Một lựa chọn khác là `Channels.newReader(channel, StandardCharsets.UTF_8)`.

Để ghi text UTF-8 khi thư viện cần `Writer`:

```java
Writer writer = new OutputStreamWriter(
        Channels.newOutputStream(channel),
        StandardCharsets.UTF_8);
```

Đây cũng là snippet minh họa cần import, channel là `WritableByteChannel`, và phải đóng/flush writer đúng vòng đời. Có thể dùng `Channels.newWriter(channel, StandardCharsets.UTF_8)` nếu phù hợp.

Lý do không tự `new String(chunk, UTF_8)` sau từng lần đọc channel: một ký tự nhiều byte có thể bị chia giữa hai chunk. `InputStreamReader`/`Channels.newReader` sử dụng decoder có trạng thái, xử lý các byte được nhận theo chuỗi.

Chú ý quan hệ nhiều lớp:

```text
Channel <- Channels.newInputStream(channel) <- InputStreamReader <- BufferedReader
                                         close theo chiều từ ngoài vào trong
```

Đóng `BufferedReader` sẽ đóng `InputStreamReader`, tiếp đó đóng adapter InputStream và cuối cùng channel nền. Nếu channel do thành phần khác vẫn cần sử dụng, không trao quyền đóng cho lớp ngoài hoặc phải thiết kế cơ chế sở hữu tài nguyên rõ ràng.

## 12. Sở hữu tài nguyên và hiệu ứng đóng lan truyền

Các phương thức `Channels.newChannel`, `newInputStream` và `newOutputStream` không tạo một bản sao độc lập của nguồn/đích. Chúng thường chia sẻ **cùng luồng byte và trạng thái nguồn/đích**; do đó thay đổi vị trí file, đọc trước, flush hay đóng ở một đường truy cập có thể ảnh hưởng đường khác.

Hợp đồng đóng:

| Adapter được tạo | Gọi `close()` trên | Hệ quả |
| --- | --- | --- |
| `Channels.newChannel(InputStream)` | Channel trả về | Đóng `InputStream` nền |
| `Channels.newChannel(OutputStream)` | Channel trả về | Đóng `OutputStream` nền |
| `Channels.newInputStream(ReadableByteChannel)` | Stream trả về | Đóng `ReadableByteChannel` nền |
| `Channels.newOutputStream(WritableByteChannel)` | Stream trả về | Đóng `WritableByteChannel` nền |
| `newInputStream/newOutputStream(AsynchronousByteChannel)` | Stream trả về | Đóng asynchronous channel nền |

Nếu một object đang được bên khác quản lý, hãy quyết định rõ **ai có quyền đóng**. Ví dụ, không bọc `System.out` bằng `Channels.newChannel(System.out)` rồi đóng channel trong thành phần nhỏ nếu chương trình vẫn cần in log bằng `System.out` sau đó. Đóng adapter theo thiết kế sẽ đóng luôn stream ấy.

Một vài nguyên tắc hữu ích:

- Dùng `try-with-resources` cho **đối tượng adapter ở tầng sở hữu cuối cùng**; tránh cố ý đóng tài nguyên nền quá sớm.
- Không giả định `flush()` trên stream tương đương ghi bền vững xuống ổ đĩa. Với dữ liệu quan trọng cần xem xét `FileChannel.force(...)` và chiến lược transaction/durability.
- Không gọi `read` hay `write` từ nhiều đường truy cập vào cùng channel/stream mà thiếu giao thức phối hợp, nhất là nếu vị trí đọc/ghi được chia sẻ.
- Việc stream adapter công bố an toàn cho nhiều thread **không bảo đảm** các object ở tầng trên hoạt động chính xác nếu đồng thời sửa trạng thái ứng dụng mà không đồng bộ.

## 13. Adapter giải quyết kiểu API, không cung cấp mọi tính năng channel

Bảng so sánh:

| Nhu cầu | Lựa chọn |
| --- | --- |
| API chỉ nhận `ReadableByteChannel` nhưng bạn có `InputStream` | `Channels.newChannel(input)` |
| API chỉ nhận `WritableByteChannel` nhưng bạn có `OutputStream` | `Channels.newChannel(output)` |
| API chỉ nhận `InputStream` nhưng bạn có `ReadableByteChannel` | `Channels.newInputStream(channel)` |
| API chỉ nhận `OutputStream` nhưng bạn có `WritableByteChannel` | `Channels.newOutputStream(channel)` |
| Đọc/ghi vị trí bất kỳ trong file | Dùng `FileChannel` hoặc `SeekableByteChannel` trực tiếp |
| Nonblocking socket và nhiều kết nối | Dùng `SelectableChannel` với `Selector` và xử lý readiness |
| Đọc/ghi text theo charset | Dùng `Reader`/`Writer` và `Charset` rõ ràng |
| Nạp cả file nhỏ vào RAM, xử lý đơn giản | `Files.readAllBytes` / `Files.readString` khi phù hợp giới hạn kích thước |
| Copy file lớn giữa các channel file | Cân nhắc `FileChannel.transferTo/transferFrom` khi điều kiện hỗ trợ phù hợp |

Adapter không tự chuyển một `InputStream` thành `SeekableByteChannel`, không tự cung cấp `FileLock`, không bảo đảm thao tác atomically hoặc tạo kênh có thể đăng ký `Selector`. Nó chỉ cung cấp giao diện đọc/ghi tương ứng, trên nguồn/đích gốc.

Nếu bạn cần dùng cả `InputStream` và channel để đọc một file, việc lựa chọn cách chia sẻ vị trí đọc phụ thuộc chính xác nguồn và adapter. Tránh giả định hai phía có hai con trỏ độc lập hoặc điều phối I/O bằng cách đổi API một cách tùy ý.

## 14. Lỗi phổ biến, dấu hiệu và cách khắc phục

### Lỗi 1: chọn nhầm hướng đọc/ghi

`Channels.newChannel(new FileInputStream(...))` trả `ReadableByteChannel`. Nếu code cần `WritableByteChannel`, hãy cung cấp `OutputStream` phù hợp. Trình biên dịch giúp phát hiện lỗi gán sai giao diện.

### Lỗi 2: quên `flip()` hoặc `clear()` khi chép qua `ByteBuffer`

Sau khi `read(buffer)`, position trỏ đến cuối dữ liệu nạp. Không `flip()` thì `write(buffer)` có thể ghi từ sai vùng. Sau khi ghi hết mà không `clear()`, lần đọc tiếp có thể thấy buffer không còn chỗ. Dùng quy trình `read → flip → write hết → clear` hoặc `compact` nếu cần giữ phần chưa tiêu thụ.

### Lỗi 3: giả định `write(buffer)` tiêu thụ hết

Một lần `WritableByteChannel.write(buffer)` có thể chỉ ghi một phần. Dùng:

```java
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written == 0) {
        // Với nonblocking channel: chờ OP_WRITE qua Selector.
        // Với channel chặn: xác định nguyên nhân thiếu tiến triển.
        throw new IOException("No write progress");
    }
}
```

Đây là snippet; thay logic khi `written == 0` bằng chính sách phù hợp với kiểu channel. Không áp dụng vòng quay bận CPU khi channel chưa sẵn sàng.

### Lỗi 4: bọc nonblocking channel thành stream rồi gọi `read()/write()`

`Channels.newInputStream` và `newOutputStream` cho `ReadableByteChannel` / `WritableByteChannel` ném `IllegalBlockingModeException` khi channel nền đang ở nonblocking mode. Tiếp tục dùng API channel và selector khi xử lý event-driven.

### Lỗi 5: tưởng adapter tự buffer hoặc chuyển mã

Adapter byte không tự thêm buffering, không mã hóa UTF-8. Muốn giảm số lời gọi I/O nhỏ, cân nhắc bộ đệm ở tầng thích hợp; muốn làm việc với ký tự, dùng `Reader`/`Writer` và charset chỉ định.

### Lỗi 6: đóng adapter rồi tiếp tục dùng nguồn/đích

`try-with-resources` đóng adapter và lan đến tài nguyên nền. Hãy kiểm soát vòng đời và quyền sở hữu, đặc biệt với `System.in`, `System.out`, socket hoặc kênh được chia sẻ.

### Lỗi 7: suy diễn an toàn đồng thời thành logic ứng dụng đúng

Việc adapter stream hỗ trợ truy cập từ nhiều thread không tự làm cho thứ tự record, trạng thái decoder, transaction hoặc khóa ứng dụng trở nên an toàn. Có thể cần một luồng đọc duy nhất hoặc cơ chế đồng bộ riêng.

### Lỗi 8: dùng `readAllBytes()` với file rất lớn hoặc nguồn vô hạn

`InputStream.readAllBytes()` có thể tiêu thụ lượng RAM lớn. Nếu kích thước không bị giới hạn, hãy xử lý chunk cố định hoặc dùng streaming parser.

### Lỗi 9: bọc qua nhiều lớp mà không kiểm tra ai flush/close

Cấu trúc `BufferedWriter(OutputStreamWriter(Channels.newOutputStream(ch)))` có nhiều tầng. `flush` của tầng ngoài đẩy dữ liệu đệm xuống tầng dưới, nhưng không hứa hẹn bền vững vật lý; `close` của tầng ngoài đóng dây chuyền đến channel. Chỉ một thành phần nên sở hữu quyết định đóng cuối cùng.

### Lỗi 10: kỳ vọng việc đổi adapter làm thay đổi trạng thái file

Bọc `FileChannel` thành `InputStream` không reset vị trí và không mở lại file. Khi cần đọc từ offset cụ thể, đặt vị trí bằng API channel (nếu được phép), hoặc mở resource mới theo ngữ nghĩa riêng.

## 15. Câu hỏi và bài tập thực hành

1. API nhận `ReadableByteChannel`, nhưng bạn đang có `BufferedInputStream`. Cần gọi phương thức nào? Đóng đối tượng trả về có ảnh hưởng gì đến stream cũ?
2. API nhận `OutputStream`, nhưng bạn đã có `FileChannel` mở với `WRITE`. Chọn adapter nào? Byte tiếp theo sẽ được ghi vào đâu?
3. Vì sao `Channels.newChannel(InputStream)` không tự tạo khả năng `seek`, `truncate` hoặc `FileLock`?
4. Nếu buffer capacity 4 chứa 4 byte UTF-8 đầu của một đoạn text, việc in riêng `new String(chunk)` có thể làm sai ký tự như thế nào?
5. Đoạn `while (buffer.hasRemaining()) channel.write(buffer)` có nguy cơ nào khi channel nonblocking không tiến triển? Bạn sẽ chờ theo cơ chế nào?
6. Điều gì xảy ra khi bạn đóng `Channels.newInputStream(fileChannel)` rồi gọi `fileChannel.read(...)`?
7. Vì sao stream adapter cho nonblocking `SocketChannel` không thay thế được `Selector`?
8. Đối với input 100 GiB, vì sao `InputStream.readAllBytes()` không phải thiết kế phù hợp? Hãy viết cách xử lý theo chunk hữu hạn.
9. `Channels.newInputStream(AsynchronousByteChannel)` có trả về API async để bên gọi await bằng `Future` không? Cần dùng interface nào nếu muốn callback/Future?
10. Thử giảm capacity buffer của `StreamToChannelDemo` từ 4 xuống 1. Kết quả text cuối có còn nguyên vẹn không? Giải thích điều kiện bảo toàn byte.

## 16. Tài liệu tham khảo

- [Java SE 17 API — `java.nio.channels.Channels`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Channels.html)
- [Java SE 17 API — package `java.nio.channels`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/package-summary.html)
- [Java SE 17 API — `java.nio.ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)

## 17. Ghi nhớ

**`Channels.newChannel(stream)`** chuyển stream byte sang byte channel cùng hướng; **`Channels.newInputStream(readableChannel)`** và **`newOutputStream(writableChannel)`** chuyển byte channel sang stream theo hướng tương ứng. Các adapter vận chuyển byte qua cùng tài nguyên nền, không tự thêm bộ đệm hoặc encoding, và đóng adapter sẽ đóng nguồn/đích bên dưới.

Khi kết hợp với NIO, hãy xử lý `ByteBuffer` bằng `flip()/clear()` đúng pha, kiểm tra kết quả `read/write`, tính đến partial write và không dùng adapter stream trên channel đang cấu hình nonblocking. Với text, dùng charset cùng `Reader/Writer`; với I/O có thể truy cập theo vị trí hoặc cần readiness/async thật sự, hãy giữ API channel chuyên dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-009">Channels.newReader, newWriter và chuyển đổi charset</a>

<details>
<summary>Xem chi tiết</summary>

# `Channels.newReader`, `Channels.newWriter` và chuyển đổi charset trong Java NIO

## 1. Bối cảnh: Channel vận chuyển byte, Reader/Writer xử lý ký tự

`ReadableByteChannel` và `WritableByteChannel` thao tác với **byte** qua `ByteBuffer`, trong khi `java.io.Reader` và `java.io.Writer` cung cấp giao diện **ký tự** (`char`/chuỗi Java). Khi dữ liệu là văn bản, giữa hai tầng cần có quá trình chuyển đổi theo **charset**:

```text
ĐỌC:
Nguồn (file/socket/byte stream)
    → ReadableByteChannel: byte
    → CharsetDecoder: byte → ký tự Unicode của Java
    → Reader: char / String

GHI:
Writer: char / String
    → CharsetEncoder: ký tự Java → byte
    → WritableByteChannel: byte
    → Đích (file/socket/byte stream)
```

`java.nio.channels.Channels` là lớp tiện ích làm cầu nối giữa I/O kiểu channel và các API stream/reader/writer truyền thống. Hai nhóm phương thức tập trung của bài:

- `Channels.newReader(...)`: tạo `Reader` để **giải mã** byte lấy từ `ReadableByteChannel` thành ký tự.
- `Channels.newWriter(...)`: tạo `Writer` để **mã hóa** ký tự thành byte rồi ghi vào `WritableByteChannel`.

Các adapter **không biến channel byte thành đối tượng lưu văn bản độc lập**: chúng dùng channel được truyền vào, giữ bộ đệm chuyển đổi riêng và có quan hệ vòng đời với channel. Khái niệm "charset" quyết định byte nào biểu diễn ký tự nào; nếu chọn sai, văn bản có thể lỗi dù đọc/ghi channel không báo lỗi I/O.

Sau bài học, bạn cần chọn đúng overload, quản lý charset minh bạch, kiểm soát lỗi giải mã/mã hóa, đóng tài nguyên và hiểu giới hạn khi ghép Reader/Writer với channel.

## 2. Các overload chính xác trong `java.nio.channels.Channels`

Java SE 17+ hỗ trợ sáu overload quan trọng sau:

| Đọc hoặc ghi | Chữ ký | Khi nên dùng |
|---|---|---|
| Đọc | `newReader(ReadableByteChannel ch, String csName)` | Tên charset ở dạng chuỗi, chẳng hạn `"UTF-8"` |
| Đọc | `newReader(ReadableByteChannel ch, Charset charset)` | Charset đã biết, thường dùng `StandardCharsets.UTF_8` |
| Đọc | `newReader(ReadableByteChannel ch, CharsetDecoder dec, int minBufferCap)` | Cần điều chỉnh chính sách lỗi khi giải mã hoặc cấp phát buffer |
| Ghi | `newWriter(WritableByteChannel ch, String csName)` | Tên charset ở dạng chuỗi |
| Ghi | `newWriter(WritableByteChannel ch, Charset charset)` | Chọn charset rõ ràng, không cần cấu hình encoder riêng |
| Ghi | `newWriter(WritableByteChannel ch, CharsetEncoder enc, int minBufferCap)` | Cần chính sách lỗi khi mã hóa hoặc cấu hình encoder |

Các overload nhận `Charset` có từ Java 10, nên dùng được trên Java 17+. Với overload nhận `Charset`:

```java
Reader reader = Channels.newReader(channel, StandardCharsets.UTF_8);
Writer writer = Channels.newWriter(channel, StandardCharsets.UTF_8);
```

*Hai dòng trên là minh họa độc lập: `channel` của dòng đầu phải là `ReadableByteChannel`; dòng thứ hai phải là `WritableByteChannel`. Chúng không nhất thiết là cùng một đối tượng.* Mỗi lời gọi tạo adapter trên channel cho trước, **không tự mở file** và không nhận một `Path`.

Về mặt hành vi, hai overload này tương đương với:

```java
Reader reader = Channels.newReader(
        readableChannel, StandardCharsets.UTF_8.newDecoder(), -1);

Writer writer = Channels.newWriter(
        writableChannel, StandardCharsets.UTF_8.newEncoder(), -1);
```

Đây là đoạn minh họa cần các biến channel thích hợp và import `java.io.Reader`, `java.io.Writer`, `java.nio.channels.Channels`, `java.nio.charset.StandardCharsets`. Tùy chọn `-1` ở đây có nghĩa dùng dung lượng buffer mặc định do implementation quyết định.

Với overload `String csName`, Java phải phân giải tên charset, ví dụ `"UTF-8"`. Tên không hợp lệ/không được hỗ trợ có thể gây `IllegalCharsetNameException` hoặc `UnsupportedCharsetException`. Nên ưu tiên `StandardCharsets.UTF_8`, `StandardCharsets.ISO_8859_1`… khi biết charset lúc biên dịch để tránh lỗi chính tả, kiểm soát môi trường và loại bỏ phụ thuộc vào charset mặc định.

**Không có overload `Channels.newReader(channel)` hoặc `Channels.newWriter(channel)` chỉ một tham số**. Việc chọn charset là một phần bắt buộc của giao diện này.

## 3. Chuyển đổi charset là gì?

Java `String` và phần lớn API ký tự lưu chuỗi theo đơn vị mã UTF-16 (`char` là một code unit 16-bit). Một ký tự Unicode có thể gồm một hoặc hai `char`, ví dụ nhiều emoji dùng **surrogate pair**. Channel chỉ vận chuyển byte; charset ánh xạ giữa chuỗi code unit hợp lệ và byte.

Ví dụ ký tự `é` (`U+00E9`):

| Charset | Byte mã hóa `é` (hex) | Số byte |
|---|---|---|
| UTF-8 | `C3 A9` | 2 |
| ISO-8859-1 | `E9` | 1 |
| UTF-16BE | `00 E9` | 2 |

Cùng byte nhưng dùng charset khác khi đọc có thể tạo ra văn bản khác:

```text
Byte thực tế (UTF-8): C3 A9
Giải mã UTF-8:          é
Giải mã ISO-8859-1:    Ã©
```

Trường hợp chọn sai nhưng mỗi byte vẫn được charset đích chấp nhận, kết quả là **mojibake** (chuỗi sai hiển thị) chứ không nhất thiết có ngoại lệ. Vì vậy, không thể dựa vào việc `Reader.read()` không lỗi để khẳng định đã chọn đúng charset.

Ngược lại, chuỗi byte `C3 28` không phải UTF-8 hợp lệ: sau byte mở đầu `C3` phải là một continuation byte, nhưng `28` không phải continuation byte. Khi `CharsetDecoder` ở chế độ `REPORT`, việc đọc có thể ném `MalformedInputException`.

Chiều ghi có lỗi khác: một số ký tự không có biểu diễn trong charset đích. Chẳng hạn `☕` (`U+2615`) không mã hóa được trong ISO-8859-1; encoder báo lỗi **unmappable character** nếu cấu hình `REPORT`.

## 4. Dùng `newReader` để đọc văn bản từ channel

Ví dụ đọc file UTF-8 bằng channel:

```java
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

Path path = Path.of("notes.txt");

try (ReadableByteChannel channel = Files.newByteChannel(path);
     Reader reader = Channels.newReader(
             channel, StandardCharsets.UTF_8)) {

    char[] chars = new char[1024];
    int count;
    while ((count = reader.read(chars)) != -1) {
        System.out.print(new String(chars, 0, count));
    }
}
```

Đây là **đoạn mã minh họa trong phương thức khai báo `throws IOException`**, không phải tệp Java độc lập có `main`. `Files.newByteChannel(path)` mở channel cho đọc; `newReader` dùng UTF-8 giải mã; `reader.read(chars)` trả số `char` thực sự đọc được, `-1` khi kết thúc.

Lưu ý:

- `reader.read(chars)` là thao tác trên **char buffer**; một lần đọc không tương ứng với một byte hay một ký tự Unicode hoàn chỉnh theo nghĩa code point.
- Một lần đọc có thể trả ít ký tự hơn kích thước mảng mà chưa đạt EOF. Với dữ liệu lớn, đọc theo vòng lặp.
- Dùng đúng phạm vi `0..count`, không in toàn bộ mảng nếu lần đọc cuối có ít dữ liệu.
- Nếu chỉ cần toàn bộ văn bản của một file nhỏ với charset cụ thể, `Files.readString(path, StandardCharsets.UTF_8)` đơn giản hơn. `Channels.newReader` có ích khi nguồn đã là channel hoặc cần một API nhận `Reader`.
- Đừng dùng `new String(bytes)` hoặc `new InputStreamReader(input)` mà không nêu charset khi muốn kết quả độc lập môi trường.

### 4.1. Đọc theo dòng

`Reader` từ `Channels.newReader` cung cấp thao tác đọc ký tự, không có `readLine()`. Để đọc theo dòng, bọc trong `BufferedReader`:

```java
try (var reader = new java.io.BufferedReader(
        Channels.newReader(
                Files.newByteChannel(Path.of("notes.txt")),
                StandardCharsets.UTF_8))) {

    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}
```

*Đoạn minh họa nằm trong method xử lý `IOException`; `Channels`/`StandardCharsets`/`Files`/`Path` đã được import.* `BufferedReader` thêm chức năng đọc dòng và cơ chế buffer ký tự; `Channels.newReader` tự có bộ đệm **byte** phục vụ decoder. `readLine()` không bao gồm dấu kết thúc dòng trong kết quả, nên khi tái tạo văn bản gốc từ các dòng cần chính sách giữ xuống dòng phù hợp.

## 5. Dùng `newWriter` để ghi văn bản ra channel

Ví dụ mở file output, ghi UTF-8:

```java
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path path = Path.of("notes.txt");

try (WritableByteChannel channel = Files.newByteChannel(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
     Writer writer = Channels.newWriter(
             channel, StandardCharsets.UTF_8)) {

    writer.write("Xin chào");
    writer.write(System.lineSeparator());
}
```

Đây là đoạn minh họa trong method xử lý `IOException`. `Writer` nhận ký tự từ Java, encoder chuyển thành byte UTF-8 và channel ghi ra file. Các option `CREATE`, `TRUNCATE_EXISTING`, `WRITE` có nghĩa: tạo nếu chưa có, xóa nội dung cũ khi mở, mở để ghi. Nếu muốn nối tiếp, thiết kế option khác như `APPEND` và bảo đảm cấu hình thích hợp; không dùng `TRUNCATE_EXISTING` khi muốn giữ file cũ.

**Thời điểm byte tới đích:** Writer có buffer byte nội bộ. `write` có thể giữ một phần dữ liệu trong buffer trước khi chuyển cho channel. `flush()` yêu cầu đẩy dữ liệu đã buffer sang channel; `close()` hoàn tất xử lý encoder, flush phần chưa ghi và đóng channel bên dưới. `flush()` không có nghĩa dữ liệu đã bền vững trên thiết bị lưu trữ. Nếu cần độ bền ghi file sau sự cố, đó là yêu cầu riêng, chẳng hạn `FileChannel.force(...)` với điều kiện áp dụng.

### 5.1. Có nên bọc `BufferedWriter`?

`BufferedWriter` thêm **buffer ký tự** ở lớp ngoài:

```java
try (var writer = new java.io.BufferedWriter(
        Channels.newWriter(
                Files.newByteChannel(
                        Path.of("notes.txt"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING),
                StandardCharsets.UTF_8))) {

    writer.write("Dòng 1");
    writer.newLine();
    writer.write("Dòng 2");
}
```

Đây là đoạn minh họa trong method xử lý `IOException`. Lớp ngoài có thể giảm số lời gọi nhỏ xuống lớp encoder và hỗ trợ `newLine()`. Không vì vậy mà kết luận mọi trường hợp phải thêm `BufferedWriter`: hiệu quả phụ thuộc kiểu ghi, buffer và sink. `Writer` từ `Channels` vốn đã có buffer byte nội bộ.

## 6. Mặc định xử lý lỗi của adapter và cách cấu hình

**Điểm khác biệt dễ bỏ qua:** overload `Channels.newReader(channel, Charset)` và `Channels.newWriter(channel, Charset)` dùng decoder/encoder mới với hành động mặc định là **`CodingErrorAction.REPORT`** cho cả lỗi malformed và unmappable. Có nghĩa chúng báo lỗi thay vì tự lặng lẽ thay bằng ký tự dự phòng.

Tùy chọn chính sách theo từng chiều:

| Hành động | Ý nghĩa khi gặp dữ liệu lỗi | Khi cân nhắc |
|---|---|---|
| `REPORT` | Báo lỗi (qua exception khi adapter đọc/ghi) | Dữ liệu cấu hình, giao thức chặt chẽ, kiểm thử tính toàn vẹn |
| `REPLACE` | Thay bằng ký tự/byte thay thế do decoder/encoder quy định | Hiển thị best-effort khi việc mất thông tin được chấp nhận |
| `IGNORE` | Bỏ qua phần dữ liệu không giải mã/mã hóa được | Chỉ khi nghiệp vụ chấp nhận mất dữ liệu mà không có ký hiệu thay thế |

Cấu hình decoder:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);

Reader reader = Channels.newReader(sourceChannel, decoder, -1);
```

Đoạn minh họa giả định `sourceChannel` là `ReadableByteChannel` đã mở; import `CharsetDecoder`, `CodingErrorAction`, `StandardCharsets`, `Reader` và `Channels`. Decoder UTF-8 mặc định thường thay lỗi bằng `U+FFFD` (ký tự thay thế `�`) khi đã chọn `REPLACE`; giá trị thay thế có thể cấu hình với `CharsetDecoder.replaceWith(...)`.

Cấu hình encoder:

```java
CharsetEncoder encoder = StandardCharsets.ISO_8859_1
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);

Writer writer = Channels.newWriter(outputChannel, encoder, -1);
```

Đoạn minh họa giả định `outputChannel` là `WritableByteChannel` đã mở, đã import các lớp phù hợp. Encoder ISO-8859-1 có byte thay thế mặc định là `?` (0x3F). Với encoder khác, không đoán byte thay thế: xem `replacement()` và contract của charset. `REPLACE` hoặc `IGNORE` tạo kết quả có thể mất thông tin, không dùng nếu yêu cầu byte round-trip chính xác.

`MalformedInputException` và `UnmappableCharacterException` đều là subtype của `CharacterCodingException`, kế thừa `IOException`. Việc chuyển lỗi ra ngoài diễn ra khi `read`/`write`/`flush`/`close` thực sự xử lý dữ liệu, không nhất thiết ngay lúc tạo Reader/Writer.

### 6.1. Phân biệt malformed và unmappable

- **Malformed input**: một chuỗi byte hoặc chuỗi `char` **sai quy tắc** của encoding được chọn, ví dụ byte UTF-8 `C3 28`; hoặc UTF-16 chứa high surrogate không có low surrogate tương ứng khi encode.
- **Unmappable character**: đầu vào hợp lệ về cấu trúc nhưng không biểu diễn được trong charset đích, ví dụ `☕` hợp lệ của Java String nhưng ISO-8859-1 không hỗ trợ.
- Charset mismatch có thể tạo văn bản sai mà **không có lỗi** nếu decoder vẫn chấp nhận mọi byte. Chính sách `REPORT` không tự phát hiện chọn sai charset khi byte vẫn hợp lệ theo charset đã chọn.

Giải pháp xử lý văn bản chính xác luôn cần biết định dạng nguồn (spec/protocol/metadata/user choice) và mã hóa đích.

## 7. Ví dụ Java 17+ chạy được: round-trip và báo lỗi charset

Đây là ví dụ hoàn chỉnh, không phụ thuộc file trên máy. Nó tạo channel từ `ByteArrayOutputStream` và `ByteArrayInputStream` bằng `Channels.newChannel`. Kết quả minh họa cả vòng khứ hồi UTF-8 lẫn lựa chọn `REPORT` / `REPLACE`.

**Tệp nguồn:** `ChannelCharsetDemo.java`

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;

public class ChannelCharsetDemo {

    private static String readAll(Reader reader) throws IOException {
        StringWriter result = new StringWriter();
        reader.transferTo(result); // Có từ Java 10.
        return result.toString();
    }

    public static void main(String[] args) throws IOException {
        // Unicode escapes giúp mã nguồn không phụ thuộc encoding khi copy.
        String message = "C\u00E0 ph\u00EA \u2615";
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();

        try (Writer writer = Channels.newWriter(
                Channels.newChannel(encoded), StandardCharsets.UTF_8)) {
            writer.write(message);
        }

        byte[] utf8 = encoded.toByteArray();
        try (Reader reader = Channels.newReader(
                Channels.newChannel(new ByteArrayInputStream(utf8)),
                StandardCharsets.UTF_8)) {
            System.out.println("UTF-8 round-trip: "
                    + message.equals(readAll(reader)));
        }

        // C3 phải đi với continuation byte; 28 không phải continuation.
        byte[] malformed = {(byte) 0xC3, (byte) 0x28};

        try (Reader reader = Channels.newReader(
                Channels.newChannel(new ByteArrayInputStream(malformed)),
                StandardCharsets.UTF_8)) {
            readAll(reader);
        } catch (MalformedInputException ex) {
            System.out.println("decode strict: MalformedInputException");
        }

        CharsetDecoder forgivingDecoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE);

        try (Reader reader = Channels.newReader(
                Channels.newChannel(new ByteArrayInputStream(malformed)),
                forgivingDecoder, -1)) {
            System.out.println("decode replace equals: "
                    + "\uFFFD(".equals(readAll(reader)));
        }

        try (Writer writer = Channels.newWriter(
                Channels.newChannel(new ByteArrayOutputStream()),
                StandardCharsets.ISO_8859_1)) {
            writer.write("\u2615");
        } catch (UnmappableCharacterException ex) {
            System.out.println("encode strict: UnmappableCharacterException");
        }

        ByteArrayOutputStream replaced = new ByteArrayOutputStream();
        CharsetEncoder forgivingEncoder = StandardCharsets.ISO_8859_1
                .newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        try (Writer writer = Channels.newWriter(
                Channels.newChannel(replaced), forgivingEncoder, -1)) {
            writer.write("\u2615");
        }

        System.out.println("encode replace: "
                + new String(replaced.toByteArray(),
                             StandardCharsets.ISO_8859_1));
    }
}
```

Biên dịch/chạy tại thư mục chứa file:

```bash
javac ChannelCharsetDemo.java
java ChannelCharsetDemo
```

**Stdout (ASCII, độc lập khả năng hiển thị emoji của terminal):**

```text
UTF-8 round-trip: true
decode strict: MalformedInputException
decode replace equals: true
encode strict: UnmappableCharacterException
encode replace: ?
```

Giải thích kết quả:

1. Writer mã hóa `"Cà phê ☕"` thành UTF-8; Reader dùng **cùng** charset giải mã, nên so sánh với chuỗi đầu là `true`.
2. Decoder UTF-8 mặc định trên `Channels.newReader` dùng `REPORT`. Khi gặp `C3 28`, Reader ném `MalformedInputException`; chương trình bắt riêng ngoại lệ này.
3. Decoder tùy biến dùng `REPLACE` tạo `"\uFFFD("`, nên phép so sánh in `true`.
4. ISO-8859-1 không thể biểu diễn `☕`; writer mặc định báo `UnmappableCharacterException`.
5. Encoder đã chuyển sang `REPLACE` ghi byte dự phòng; khi đọc lại bằng ISO-8859-1, kết quả là ký tự `?`.

`ByteArrayOutputStream` có thể lấy byte sau khi wrapper đóng; nguồn byte dùng trong ví dụ cũng không cần dọn tệp. Nếu thay bằng socket/file thật, đóng Reader/Writer sẽ đóng channel, nên phải quản lý quyền sở hữu tài nguyên phù hợp.

## 8. Chuyển mã văn bản: từ một charset sang charset khác

“Transcoding” văn bản là **decode từ charset nguồn rồi encode theo charset đích**. Không nên đổi nhãn charset của byte array và mong byte tự biến đổi. Sơ đồ:

```text
file ISO-8859-1 --[ReadableByteChannel]
                 --[CharsetDecoder ISO-8859-1]
                 --> char / String
                 --[CharsetEncoder UTF-8]
                 --[WritableByteChannel] --> file UTF-8
```

Phương thức dưới đây chuyển nội dung theo luồng, không giữ cả file trong RAM:

```java
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TextTranscoder {
    public static void transcode(
            Path source, Charset sourceCharset,
            Path target, Charset targetCharset) throws IOException {

        try (Reader reader = Channels.newReader(
                Files.newByteChannel(source), sourceCharset);
             Writer writer = Channels.newWriter(
                Files.newByteChannel(target,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE),
                targetCharset)) {

            char[] chunk = new char[4096];
            int count;

            while ((count = reader.read(chunk)) != -1) {
                writer.write(chunk, 0, count);
            }
        }
    }
}
```

Đây là **class helper biên dịch được** (`TextTranscoder.java`), không có `main` và không có stdout. Ví dụ gọi bên ngoài:

```java
TextTranscoder.transcode(
        Path.of("source-latin1.txt"),
        StandardCharsets.ISO_8859_1,
        Path.of("target-utf8.txt"),
        StandardCharsets.UTF_8);
```

Đoạn gọi minh họa yêu cầu các import `Path`, `StandardCharsets` và xử lý `IOException`; file đầu vào phải tồn tại. Nguồn và đích cần là **hai đường dẫn không trỏ tới cùng file**, bởi mở writer với `TRUNCATE_EXISTING` có thể phá dữ liệu nguồn nếu chúng trùng nhau. Với dữ liệu quan trọng, ghi vào file tạm rồi thay thế file đích theo quy trình an toàn sau khi thành công, đồng thời xử lý lỗi và quyền truy cập.

Lưu ý:

- Mỗi lần đọc có thể trả vài ký tự, không có quan hệ 1:1 với số byte đọc hoặc ghi.
- `Writer` thực hiện encode khi ghi và hoàn tất dữ liệu còn buffer khi đóng; không cần chuyển `char[]` thành byte thủ công.
- Số ký tự và số byte trước/sau có thể khác nhau. Ví dụ ISO-8859-1 `é` là một byte, UTF-8 `é` là hai byte.
- Nếu ký tự nguồn không có trong đích, encoder mặc định `REPORT` sẽ báo lỗi; việc “đọc được” không có nghĩa “ghi sang charset khác không mất thông tin”.
- Đây là chuyển đổi **văn bản**; không dùng để xử lý tệp nhị phân, ảnh, ZIP, hoặc byte protocol không chứa văn bản.
- Cần quyết định có giữ/chuẩn hóa xuống dòng, Unicode normalization và BOM hay không nếu nghiệp vụ yêu cầu. Các thao tác đó **không** tự xảy ra chỉ vì đổi charset.

## 9. `minBufferCap` và thực tế buffering

Hai overload chi tiết nhận `minBufferCap`:

```java
Channels.newReader(readable, decoder, minBufferCap);
Channels.newWriter(writable, encoder, minBufferCap);
```

`minBufferCap` mô tả **dung lượng tối thiểu của bộ đệm byte nội bộ** khi adapter đọc/ghi, hoặc `-1` để dùng mặc định do implementation quyết định. Với Writer, tài liệu API cho phép implementation **bỏ qua** giá trị này. Vì vậy:

- Tham số là capacity của **byte buffer**, không phải số `char` của `String`/`char[]`.
- Nó **không** giới hạn tổng kích thước file/văn bản, độ dài một lần `read` hoặc `write`, số byte cuối cùng trên đĩa, hay số byte trong charset.
- Giá trị lớn không đồng nghĩa chắc chắn nhanh hơn. Ảnh hưởng thực tế phụ thuộc cách adapter giải mã/mã hóa, kiểu channel, kích thước lời gọi và I/O bên dưới.
- Khi chưa có bằng chứng đo đạc hiệu năng, dùng overload `Charset` hoặc `-1`. Nếu muốn tối ưu, benchmark một workload thực tế với nhiều ngôn ngữ/độ dài chuỗi, tránh suy luận bằng mắt.

Bộ đệm nội bộ là một nguyên nhân dẫn tới vấn đề **vị trí channel**: Reader có thể đọc nhiều byte từ channel để chuẩn bị decode dù caller mới lấy một ít `char`. Nếu sau đó một phần code khác đọc trực tiếp từ cùng channel, nó có thể bắt đầu ở vị trí byte đã tiến xa hơn dự kiến.

## 10. Vòng đời tài nguyên và chia sẻ channel

### 10.1. Đóng wrapper cũng đóng channel

Theo contract `Channels.newReader` và `newWriter`, **`close()` của wrapper đóng channel bên dưới**. Đây là điểm quan trọng khi channel đến từ caller:

```java
ReadableByteChannel channel = Files.newByteChannel(Path.of("notes.txt"));
Reader reader = Channels.newReader(channel, StandardCharsets.UTF_8);

reader.close();
System.out.println(channel.isOpen()); // false
```

Đoạn minh họa giả định file tồn tại, import phù hợp, method xử lý `IOException`. Sau khi `reader.close()`, channel không còn dùng được; đừng ghi/đọc tiếp bằng channel như trước. Đóng Writer cũng đóng WritableByteChannel bên dưới.

**Khuyến nghị:** trong vùng sở hữu tài nguyên, dùng `try-with-resources`. Nếu API caller phải giữ channel mở để dùng tiếp, không tự ý tạo wrapper rồi đóng mà không thỏa thuận contract; cân nhắc tách lifecycle hoặc điều chỉnh API để người sở hữu quyết định việc đóng.

### 10.2. Tránh hai adapter cùng đọc/ghi trên một channel

Hai Reader riêng cùng dùng một `ReadableByteChannel` **không** sở hữu hai vị trí byte độc lập. Mỗi Reader có buffer và decoder state riêng; việc gọi xen kẽ tạo ra khả năng đọc lẫn byte, đặc biệt với mã hóa nhiều byte.

Tương tự, việc tạo hai Writer trên cùng một channel rồi ghi xen kẽ có thể làm thứ tự byte không khớp với thứ tự ký tự mong muốn, nhất là khi một Writer còn byte đang buffer. Trên `FileChannel` có file position dùng chung, trộn `channel.position(...)`, `channel.read(ByteBuffer)` và `Reader.read(...)` có thể phá các giả định về vị trí.

Nếu nghiệp vụ cần đọc các đoạn độc lập, hãy quản lý channel/stream và decoder riêng theo đoạn, xác định offset theo **byte** và không cắt giữa chuỗi byte nhiều phần. Đừng chia văn bản UTF-8 theo offset bất kỳ rồi decode mỗi khúc như một văn bản hợp lệ độc lập.

### 10.3. Non-blocking channel là một giới hạn thực tế

`Channels.newReader`/`newWriter` là adapter API ký tự đồng bộ. Theo tài liệu, nếu một **selectable channel** đang ở chế độ non-blocking vào lúc adapter cần thực hiện I/O, nó có thể ném `IllegalBlockingModeException`. Những wrapper này không tự biến quy trình sang selector/reactive/asynchronous I/O.

Ví dụ với `SocketChannel` cấu hình `configureBlocking(false)`, không dùng `Channels.newReader(socket, UTF_8)` như một lời giải tự động cho protocol non-blocking. Thay vào đó, với `Selector` đọc/ghi `ByteBuffer` và quản lý `CharsetDecoder`/`CharsetEncoder` theo trạng thái của connection và ranh giới thông điệp, gồm các lần decode thiếu byte và flush cuối luồng.

### 10.4. CharsetDecoder/CharsetEncoder giữ trạng thái

Các codec có thể là **stateful** và nói chung không an toàn cho việc nhiều thread cùng thao tác trên cùng instance. Với mỗi luồng dữ liệu logic, thường tạo decoder/encoder riêng bằng `charset.newDecoder()`/`charset.newEncoder()`. Không đưa cùng một decoder đang được Reader sử dụng cho Reader khác, hoặc gọi `decode(...)` thủ công xen kẽ với Reader sử dụng nó.

Khi bắt đầu một luồng văn bản mới, đừng giả định có thể tái sử dụng nguyên trạng một coder còn state của luồng trước. Thiết kế vòng đời codec riêng, hoặc reset một cách tường minh khi API/contract cho phép.

## 11. Phân biệt các API gần nhau

| Nhu cầu | API phù hợp | Lưu ý |
|---|---|---|
| Channel byte → ký tự | `Channels.newReader(ch, charset)` | Giải mã byte; wrapper đóng channel khi close |
| Ký tự → channel byte | `Channels.newWriter(ch, charset)` | Mã hóa ký tự; wrapper đóng channel khi close |
| InputStream byte → ký tự | `new InputStreamReader(input, charset)` | Không cần tạo channel trung gian khi đã có InputStream |
| Ký tự → OutputStream byte | `new OutputStreamWriter(output, charset)` | Không cần channel nếu chỉ làm việc với stream |
| File văn bản nhỏ → String | `Files.readString(path, charset)` | Tự xử lý mở/đóng file theo API |
| String → file | `Files.writeString(path, text, charset, options...)` | Dễ sử dụng hơn khi không cần channel |
| Channel byte → InputStream byte | `Channels.newInputStream(ch)` | Không thực hiện chuyển đổi charset |
| OutputStream byte → channel byte | `Channels.newChannel(output)` | Không thực hiện chuyển đổi charset |
| Channel byte → ByteBuffer | `channel.read(buffer)`/`write(buffer)` | Cấp byte thấp hơn; caller tự quyết định decode/encode |

Hai điểm thường gây nhầm:

1. **`Channels.newInputStream` không phải `Channels.newReader`.** Stream mang byte, Reader mang ký tự Unicode sau giải mã.
2. **`Charset.encode/decode` convenience methods và adapter `newReader/newWriter` có thể có chính sách lỗi khác nhau.** Đọc contract của API bạn thực sự gọi; với adapter dựa trên `Charset`, lỗi mặc định là `REPORT`.

## 12. BOM, xuống dòng và tính toàn vẹn nội dung

### 12.1. Byte Order Mark (BOM)

Một số tệp văn bản bắt đầu với BOM, ví dụ UTF-8 BOM bytes `EF BB BF`, UTF-16BE `FE FF`, UTF-16LE `FF FE`. BOM giúp mô tả thứ tự byte với một số định dạng, nhưng cách xử lý phụ thuộc charset và decoder cụ thể.

Không được cho rằng `Channels.newReader(..., UTF_8)` luôn “tự xóa mọi BOM”. Với UTF-8, BOM có thể được giải mã thành ký tự `U+FEFF` ở đầu nội dung. Nếu protocol quy định BOM tùy chọn, hãy xây dựng quy tắc đọc đầu vào cụ thể và kiểm tra bằng dữ liệu thật. Đừng xóa mọi `U+FEFF` giữa văn bản vì nó có thể xuất hiện với ý nghĩa khác ngoài marker đầu file.

### 12.2. Kết thúc dòng và normalization

`newReader`/`newWriter` **không tự chuyển** CRLF (`\r\n`) thành LF (`\n`), cũng không tự chuẩn hóa Unicode sang NFC/NFD. `BufferedReader.readLine()` có hành vi tách dòng, nhưng đó là tầng riêng. Khi chuyển mã tệp cấu hình, CSV hoặc protocol ký số, khác biệt của line endings, BOM và normalization có thể ảnh hưởng byte output hoặc chữ ký. Xác định chính sách chuyển đổi trước khi xử lý.

### 12.3. Round-trip cần điều kiện rõ

Đọc rồi ghi bằng cùng charset không phải phép bảo toàn byte cho mọi input. Điều kiện cần xét:

- Byte nguồn có hợp lệ với charset không?
- Decoder và encoder có thể ánh xạ qua Unicode rồi trở về đúng byte ban đầu không? Có encoding cho nhiều biểu diễn byte của cùng chuỗi ký tự.
- Có dùng `REPLACE`/`IGNORE` gây mất thông tin không?
- Có thêm/xóa BOM, đổi xuống dòng hoặc normalization không?
- Có dạng file không phải văn bản thuần mà chứa byte có ý nghĩa ngoài charset không?

Nếu yêu cầu **copy byte đúng 100%**, hãy copy bytes bằng channel/stream mà không qua `Reader`/`Writer`. Nếu yêu cầu **văn bản có thể đọc/ghi qua charset cụ thể**, dùng decoder/encoder và kiểm tra kết quả phù hợp.

## 13. Các lỗi phổ biến và cách khắc phục

1. **Quên charset** rồi tin rằng mọi môi trường dùng UTF-8. Khi dùng Channels adapter, truyền `StandardCharsets.UTF_8` hoặc charset đúng theo protocol.
2. **Nhầm hướng chuyển đổi.** `newReader` nhận bytes từ channel và decode; `newWriter` nhận chars và encode. Không gọi `newReader` cho WritableByteChannel chỉ có khả năng ghi.
3. **Dùng `newReader(ch)` hoặc `newWriter(ch)`.** Không có overload một tham số; phải nêu charset/decoder/encoder.
4. **Xem `Charset` và `CharsetDecoder` là cùng kiểu.** Overload decoder còn yêu cầu `minBufferCap`; overload Charset không có tham số buffer.
5. **Dùng nhầm mã hóa đích** khiến văn bản mojibake. Không có exception trong một số trường hợp charset mismatch; phải dựa vào thông số nguồn.
6. **Nghĩ codec luôn tự thay ký tự không hợp lệ.** Channels adapter với overload Charset dùng `REPORT`; xử lý `CharacterCodingException` hoặc tạo coder có `CodingErrorAction` cụ thể.
7. **Nhầm “malformed” với “unmappable”.** Một bên sai biểu diễn đầu vào, bên kia ký tự hợp lệ nhưng không encode/decode được theo bảng ánh xạ.
8. **Tin `REPLACE` là không mất dữ liệu.** Replacement bảo toàn khả năng tiếp tục xử lý nhưng có thể mất byte hoặc ký tự gốc.
9. **Chỉ gọi `write` mà không đóng/flush writer**. Dữ liệu có thể còn ở buffer. Dùng `try-with-resources` trong phạm vi sở hữu channel.
10. **Tưởng `flush()` bảo đảm file đã fsync.** Đẩy buffer Java xuống channel khác với cam kết durability của hệ thống lưu trữ.
11. **Đóng Reader/Writer rồi vẫn muốn dùng channel cũ.** `close` đóng cả channel; quy định rõ ownership.
12. **Gọi `readLine()` trực tiếp trên `Reader`.** Dùng `BufferedReader` cho xử lý theo dòng.
13. **Trộn đọc trực tiếp channel với Reader trên cùng channel**. Read-ahead và decoder state làm sai vị trí byte mong đợi.
14. **Gắn Reader/Writer đồng bộ lên non-blocking SocketChannel**. Có thể gặp `IllegalBlockingModeException`; non-blocking I/O cần quản lý ByteBuffer và decoder theo state machine.
15. **Chia byte UTF-8 bất kỳ rồi decode từng chunk riêng biệt**. Một ký tự có thể trải qua nhiều byte. Dùng decoder stateful trên chuỗi byte liên tục, hoặc adapter Reader quản lý phần byte chưa hoàn chỉnh.
16. **Dùng `minBufferCap` như giới hạn kích thước văn bản**. Nó chỉ liên quan buffer byte nội bộ và có thể bị implementation bỏ qua với Writer.
17. **Tưởng đóng channel chỉ giải phóng metadata mà không cần xử lý trạng thái encoder**. Việc đóng Writer hoàn tất mã hóa, flush, rồi đóng channel.
18. **Dùng chuyển mã ký tự để copy dữ liệu nhị phân**. Văn bản decode/encode có thể biến đổi bytes; copy binary theo bytes.
19. **Quên BOM và xuống dòng** khi cần byte giống file ban đầu. Charset không tự bảo đảm tính tương đương byte.
20. **Dùng chung CharsetDecoder/Encoder ở nhiều thread**. Các codec có state, không mặc định thread-safe.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Một `ReadableByteChannel` chứa byte UTF-8. Phương thức nào trả `Reader` với UTF-8?  
**Trả lời:** `Channels.newReader(channel, StandardCharsets.UTF_8)`.

**Câu 2.** Ghi ký tự `☕` qua `Channels.newWriter(channel, StandardCharsets.ISO_8859_1)` có thể xảy ra điều gì?  
**Trả lời:** Khi encode, mặc định `REPORT` nên có thể ném `UnmappableCharacterException`; charset này không biểu diễn được ký tự ấy.

**Câu 3.** Cần chấp nhận UTF-8 không hợp lệ và hiển thị `U+FFFD` thay thế, làm thế nào?  
**Trả lời:** Tạo `StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)` rồi dùng overload `Channels.newReader(channel, decoder, -1)`.

**Câu 4.** Đóng `Reader` do `Channels.newReader` tạo có đóng channel không?  
**Có.** Reader sở hữu việc đóng underlying channel theo contract của adapter.

**Câu 5.** Gọi `newReader` rồi đọc 10 ký tự có chắc channel mới tiêu thụ đúng 10 byte không?  
**Không.** Unicode và charset có số byte/ký tự khác nhau; adapter còn có buffer byte và có thể read-ahead.

**Câu 6.** Chỉ cần đọc toàn bộ file văn bản UTF-8 nhỏ thành `String`, có nên luôn dùng `Channels.newReader`?  
**Không cần.** `Files.readString(path, StandardCharsets.UTF_8)` thường gọn hơn. Chọn Channels khi nguồn/channel hoặc API Reader là yêu cầu thực tế.

**Câu 7.** Muốn convert ISO-8859-1 text sang UTF-8, chỉ đổi tên file hoặc chuyển `byte[]` sang newWriter có đủ không?  
**Không.** Phải decode theo ISO-8859-1 thành chars rồi encode theo UTF-8 (hoặc API transcoder tương đương).

**Câu 8.** `minBufferCap=8192` có bảo đảm Writer sẽ chỉ ghi mỗi lần đúng 8192 byte không?  
**Không.** Đó là yêu cầu/đề nghị về buffer nội bộ, implementation có thể bỏ qua, và số byte mỗi lần channel write phụ thuộc dữ liệu và thời điểm flush.

**Câu 9.** Nếu UTF-8 bytes giải mã theo ISO-8859-1 ra chuỗi `Ã©`, chế độ `REPORT` có nhất thiết báo lỗi?  
**Không.** ISO-8859-1 có thể giải mã các byte hợp lệ riêng lẻ, dù người dùng thấy văn bản sai.

**Câu 10.** Đọc text bằng Reader rồi ghi qua Writer có luôn giữ byte 100% không?  
**Không.** Decode/encode có thể đổi byte, nhất là khác charset, replacement, BOM, line ending hoặc mapping không có tính round-trip byte.

## 15. Ghi nhớ và tài liệu chính thức

**Cốt lõi:** `Channels.newReader` là adapter **channel byte → Reader ký tự** thông qua `CharsetDecoder`; `Channels.newWriter` là adapter **Writer ký tự → channel byte** thông qua `CharsetEncoder`. Nêu rõ charset, dùng chính sách lỗi thích hợp, quản lý vòng đời wrapper/channel, và tách việc chuyển mã văn bản khỏi sao chép byte nguyên trạng.

Tài liệu Java SE 21:

- `Channels` (six overloads, buffering, blocking mode, close behavior): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Channels.html
- `Charset`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/Charset.html
- `CharsetDecoder`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html
- `CharsetEncoder`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetEncoder.html
- `CodingErrorAction`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CodingErrorAction.html
- `StandardCharsets`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html
- `ReadableByteChannel`, `WritableByteChannel`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html và https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/WritableByteChannel.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nio-channel-010">Đóng channel, khả năng ngắt và tài nguyên bên dưới</a>

<details>
<summary>Xem chi tiết</summary>

# Đóng channel, khả năng ngắt và tài nguyên bên dưới trong Java NIO

## 1. Channel là tài nguyên có vòng đời

Trong `java.nio.channels`, một **channel** đại diện cho kết nối tới nguồn hoặc đích I/O như tệp, socket, pipe. Ví dụ: `FileChannel`, `SocketChannel`, `ServerSocketChannel`, `Pipe.SourceChannel` và `Pipe.SinkChannel`. Một channel thường giữ hoặc điều phối **tài nguyên hệ điều hành** như file descriptor, socket handle, file lock, vùng đệm và các thao tác I/O đang chờ.

Vì thế, sử dụng channel đúng không chỉ là gọi `read`/`write`: còn phải xác định **ai sở hữu và chịu trách nhiệm đóng**, hành vi khi **một luồng khác đóng trong lúc đang đọc/ghi**, và chuyện gì xảy ra khi **thread bị interrupt**.

Các điểm cốt lõi:

1. `Channel.close()` kết thúc vòng đời channel; sau đó `isOpen()` là `false` và các thao tác I/O tiếp theo không còn hợp lệ.
2. `InterruptibleChannel` bổ sung hợp đồng đặc biệt khi đang bị chặn trong I/O: close từ luồng khác thường dẫn tới `AsynchronousCloseException`; interrupt luồng thực hiện I/O dẫn tới `ClosedByInterruptException` **và đóng channel**.
3. Đóng channel không đồng nghĩa tự động đóng mọi tài nguyên **độc lập** liên quan: ví dụ vùng nhớ `MappedByteBuffer` đã map có vòng đời riêng; một socket bị đóng cũng không tự đóng các đối tượng tài nguyên khác của ứng dụng.
4. `close()` là thao tác quản lý tài nguyên, không phải phương thức đảm bảo dữ liệu đã được đồng bộ bền vững xuống thiết bị hoặc đảm bảo toàn bộ công việc logic thành công.

Bài này tập trung **vòng đời channel, interruptibility và sở hữu tài nguyên**, không đi sâu thuật toán multiplexing `Selector` hoặc giao thức mạng.

## 2. Hợp đồng cơ bản của Channel

Giao diện `Channel` mở rộng `java.io.Closeable`, nên cũng là `AutoCloseable`. Hai phương thức chung:

~~~java
boolean isOpen();
void close() throws IOException;
~~~

Hợp đồng Java 17 nêu rõ:

- Channel ở trạng thái **open** ngay sau khi tạo thành công. Khi đã closed, nó **không mở lại** được. Muốn tiếp tục I/O, phải tạo một channel mới.
- Các thao tác I/O trên channel đã đóng sẽ gây `ClosedChannelException` (hoặc ngoại lệ dẫn xuất trong tình huống liên quan).
- `close()` có thể được gọi nhiều lần: nếu đã đóng thì lần gọi sau **không có tác dụng**. Nếu một luồng khác đang thực hiện `close()`, lời gọi đóng tiếp theo có thể đợi đến khi lần trước hoàn tất.
- `close()` được phép ném `IOException` nếu có lỗi khi đóng hoặc giải phóng tài nguyên.
- `isOpen()` phản ánh trạng thái **ngay lúc kiểm tra**. Giữa `if (channel.isOpen())` và `channel.read(...)`, luồng khác vẫn có thể đóng channel.

Bởi vậy, `isOpen()` phù hợp để quan sát trạng thái hoặc debug; **không thể thay cho `try/catch` và kiểm soát vòng đời** trong mã chạy đồng thời.

Ví dụ đóng channel sở hữu bởi phạm vi hiện tại:

~~~java
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    // Đọc hoặc xử lý tệp khi channel còn open.
} // tự gọi channel.close(), kể cả khi thân try ném exception
~~~

Đây là **đoạn mã minh họa trong một phương thức**, với biến `Path path` đã được xác định. Với Java 7+, `try-with-resources` là cách thông thường để đóng channel do phạm vi này sở hữu. Nếu thân `try` và lời gọi `close()` đều lỗi, exception khi đóng sẽ được ghi vào danh sách **suppressed exceptions** của exception chính; không nên nuốt cả hai lỗi.

## 3. Ba ngoại lệ đóng channel thường bị nhầm

Các ngoại lệ này có quan hệ kế thừa:

~~~text
IOException
└── ClosedChannelException
    └── AsynchronousCloseException
        └── ClosedByInterruptException
~~~

| Trường hợp | Ngoại lệ đặc trưng | Ý nghĩa |
|---|---|---|
| Thực hiện I/O **sau khi channel đã đóng** | `ClosedChannelException` | Channel không còn được phép dùng cho I/O |
| Một luồng đang **bị chặn trong I/O**, luồng khác đóng `InterruptibleChannel` | `AsynchronousCloseException` | I/O bị kết thúc vì channel được đóng bất đồng bộ |
| Một luồng đang **bị chặn trong I/O** trên `InterruptibleChannel`, luồng khác gọi `interrupt()` lên nó | `ClosedByInterruptException` | Interrupt đóng channel và đánh dấu interrupt flag của thread |

**Lưu ý về kế thừa:** `ClosedByInterruptException` là lớp con của `AsynchronousCloseException`, và cả hai đều là `ClosedChannelException`. Nếu cần phân biệt nguyên nhân, bắt ngoại lệ **cụ thể trước**:

~~~java
try {
    channel.read(target); // đoạn minh họa, channel/target đã được tạo
} catch (ClosedByInterruptException ex) {
    // Thread bị interrupt trong I/O; channel đã đóng.
} catch (AsynchronousCloseException ex) {
    // Một thread khác đã đóng channel khi I/O đang diễn ra.
} catch (ClosedChannelException ex) {
    // Channel đã đóng trước lời gọi I/O hoặc tại thời điểm dùng.
} catch (IOException ex) {
    // Các lỗi I/O khác.
}
~~~

Khai báo `catch (ClosedChannelException)` đầu tiên sẽ làm các nhánh con phía sau trở thành **unreachable catch** và không biên dịch được.

Ngoại lệ thực tế chịu ảnh hưởng bởi **thời điểm cạnh tranh**: nếu một luồng đóng channel trước khi luồng khác bắt đầu `read`, `ClosedChannelException` có thể xuất hiện thay vì `AsynchronousCloseException`. Hãy đọc API theo điều kiện “**đang bị chặn** trong I/O” trước khi khẳng định một ngoại lệ luôn xuất hiện.

## 4. Channel nào thực sự hỗ trợ interrupt và asynchronous close?

`java.nio.channels.InterruptibleChannel` là giao diện mở rộng `Channel`. Hợp đồng của nó đặc biệt quan trọng khi một phương thức có thể **block vô thời hạn** để chờ dữ liệu hoặc tài nguyên.

Ví dụ các loại hỗ trợ giao diện này trong Java 17:

- `FileChannel`.
- `SocketChannel`, `ServerSocketChannel`, `DatagramChannel`.
- `Pipe.SourceChannel` và `Pipe.SinkChannel`.
- Các subclass của `AbstractInterruptibleChannel` phù hợp.

Kiểm tra tại runtime khi cần:

~~~java
boolean interruptible = channel instanceof InterruptibleChannel;
~~~

Đây là **đoạn mã minh họa** với biến `channel` đã tồn tại. **Không được suy luận mọi lớp triển khai `Channel` đều hỗ trợ chính xác semantics interrupt như `InterruptibleChannel`.** Ví dụ những asynchronous channels có mô hình tác vụ `Future` / `CompletionHandler` và vòng đời đặc thù phải đọc hợp đồng riêng.

### 4.1. Close từ một luồng khác

Giả sử thread A đang chờ `source.read(buffer)` và thread B gọi `source.close()`. Với một `InterruptibleChannel` đang thực sự bị chặn:

1. Close kết thúc channel và khiến thao tác chờ trả về bằng cách ném `AsynchronousCloseException`.
2. `isOpen()` trở thành `false`.
3. Đây **không phải** thao tác interrupt thread A và bản thân `close()` không có nghĩa là đặt interrupt flag của thread A.

Nếu thread A chưa kịp vào thao tác chặn, nó có thể thấy `ClosedChannelException`. Kết quả chi tiết khác nhau theo tiến độ và loại channel, nên logic ứng dụng cần xử lý ngoại lệ chung một cách đúng đắn.

### 4.2. Interrupt luồng đang chờ I/O

Với thread A đang chờ I/O qua `InterruptibleChannel`, thread B gọi:

~~~java
readerThread.interrupt();
~~~

Hợp đồng gồm **ba hiệu ứng gắn với nhau**:

1. Channel đang được sử dụng bị **đóng**.
2. Thread A nhận `ClosedByInterruptException`.
3. **Interrupt flag** của thread A được đặt thành `true`.

Đây không phải lỗi chỉ có thể “bắt rồi đọc tiếp trên cùng channel”. Sau `ClosedByInterruptException`, channel đó đã hỏng vòng đời và cần **tạo kênh mới** nếu muốn I/O lần sau.

Nếu interrupt flag đã được set **trước khi** thread bắt đầu một thao tác blocking I/O trên `InterruptibleChannel`, channel cũng sẽ bị đóng và lời gọi lập tức có thể ném `ClosedByInterruptException`. Điều này giải thích vì sao “đợi chắc chắn đã block mới interrupt” không cần thiết trong ví dụ dùng Pipe ở mục 6.

Ngược lại, `Thread.sleep()`, `Object.wait()` hoặc `BlockingQueue.take()` thường làm việc với `InterruptedException`, và những API này có semantics về interrupt flag khác. **Không gộp `InterruptedException` và `ClosedByInterruptException` làm một hành vi.**

### 4.3. Đừng tự xóa interrupt flag mà không có chủ đích

`Thread.interrupted()` là phương thức tĩnh kiểm tra **và xóa** interrupt flag của luồng hiện tại. `Thread.currentThread().isInterrupted()` đọc flag **không xóa**.

Vì `ClosedByInterruptException` đã đóng channel, phản ứng phù hợp thường là kết thúc tác vụ/ghi nhận cancellation và đóng những tài nguyên khác do tác vụ sở hữu. Không nên liên tục thử lại cùng channel vì nó đã closed. Khi bắt `InterruptedException` ở API khác, thường cần phục hồi interrupt flag nếu tầng hiện tại không thực sự xử lý cancellation; với `ClosedByInterruptException`, theo hợp đồng flag vốn đã được đặt.

## 5. Tài nguyên bên dưới và quan hệ sở hữu

**Đóng một channel** thường giải phóng kết nối/handle mà channel đang quản lý. Nhưng câu “đóng channel giải phóng mọi thứ” không đúng cho mọi quan hệ tài nguyên.

### 5.1. FileChannel, descriptor, stream và RandomAccessFile

`FileChannel` có thể được tạo bởi `FileChannel.open(...)` hoặc lấy từ `FileInputStream.getChannel()`, `FileOutputStream.getChannel()`, `RandomAccessFile.getChannel()`.

- Với `FileChannel.open(...)`, phạm vi tạo channel thường là **chủ sở hữu** và nên đóng bằng `try-with-resources`.
- Với `FileInputStream.getChannel()` hoặc `FileOutputStream.getChannel()`, channel và stream liên kết tới **cùng tệp và file position**. Đóng stream cũng đóng channel liên quan; đóng channel liên quan cũng ảnh hưởng stream gốc. Không được dùng đối tượng còn lại để I/O như thể tài nguyên vẫn độc lập.
- Với `RandomAccessFile.getChannel()`, file position và dữ liệu cũng liên hệ với `RandomAccessFile`; đóng đối tượng gốc hay associated channel ảnh hưởng đối tượng còn lại.

Ví dụ ý tưởng:

~~~java
try (FileInputStream input = new FileInputStream(file)) {
    FileChannel fc = input.getChannel();
    // input và fc không phải hai tài nguyên I/O hoàn toàn độc lập.
} // input.close() cũng đóng fc
~~~

**Đoạn mã minh họa**, biến `File file` cần tồn tại. Không được cấp quyền sở hữu đóng cả stream lẫn channel cho hai thành phần chạy độc lập mà không có thỏa thuận vì một bên có thể làm bên kia bất ngờ nhận `ClosedChannelException`.

### 5.2. SocketChannel và half-close

Đóng `SocketChannel` chấm dứt channel/socket connection mà nó đại diện. Nhưng đôi khi giao thức cần dừng **một chiều** mà vẫn để chiều kia hoạt động:

- `SocketChannel.shutdownOutput()`: kết thúc phần ghi của kết nối TCP (thông thường peer có thể nhận EOF sau khi dữ liệu đã gửi hết).
- `SocketChannel.shutdownInput()`: kết thúc phần đọc.
- `SocketChannel.close()`: đóng toàn bộ channel, không tương đương với chỉ shutdown một chiều.

Đây là tính năng riêng của socket, không áp dụng cho mọi channel. Nếu dùng `SocketChannel.socket()` để lấy đối tượng `Socket` liên kết, hãy hiểu rằng hai API cùng biểu diễn một kết nối và việc đóng phía này có thể làm phía kia không còn sử dụng được.

### 5.3. Pipe gồm hai channel

`Pipe.open()` tạo `Pipe.SourceChannel` và `Pipe.SinkChannel` **riêng biệt**. `SourceChannel` để đọc; `SinkChannel` để ghi. Chúng có vòng đời riêng:

- Đóng source **không tự đóng** sink do ứng dụng nắm giữ.
- Đóng sink làm bên đọc quan sát trạng thái kết thúc pipe khi không còn dữ liệu, theo hành vi của pipe/provider.
- Cần đóng **cả hai đầu** mà ứng dụng sở hữu; chỉ đóng một đầu sẽ để lại tài nguyên ở đầu kia.

Ví dụ chạy ở mục 6 sử dụng `try-with-resources` cho cả source và sink. Khi một thread bị interrupt và source đóng, sink vẫn phải được ứng dụng đóng sau đó.

### 5.4. File mapping tồn tại riêng với FileChannel

`MappedByteBuffer` được tạo bởi `FileChannel.map(...)` là một vùng **memory-mapped file**. Theo tài liệu Java 17, **mapping được thiết lập không phụ thuộc vào việc FileChannel còn open hay không**. Đóng `FileChannel` **không tự làm mapping vô hiệu**; mapping còn hiệu lực cho đến khi buffer được thu hồi, với các hạn chế phụ thuộc hệ điều hành (file bị truncate, vùng nhớ không còn hợp lệ, ...).

Hệ quả thực tiễn:

- Đóng channel **không đảm bảo giải phóng ngay toàn bộ native memory / file mapping**.
- Trên một số OS, mapping còn sống có thể khiến việc đổi tên hoặc xóa tệp gặp hạn chế.
- Nếu thay đổi nội dung qua mapped buffer cần yêu cầu flush, hãy tìm hiểu `MappedByteBuffer.force()`; `FileChannel.force(...)` không được bảo đảm flush mọi thay đổi thực hiện qua mapping.
- Không dựa vào `System.gc()` để có lịch giải phóng mapping xác định.

Ví dụ:

~~~java
MappedByteBuffer mapped;
try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
    mapped = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
}
// fc đã closed; mapping là tài nguyên có vòng đời riêng.
~~~

**Mã minh họa**: yêu cầu file và kích thước hợp lệ; các cách truy cập `mapped` sau đó vẫn phải tính tới các điều kiện OS và thay đổi ở file gốc. Không phải chương trình độc lập.

### 5.5. FileLock và Selector

- `FileLock` do `FileChannel.lock()` hoặc `tryLock()` tạo là tài nguyên cần quản lý. Đóng file channel làm các lock của channel đó không còn hợp lệ; cũng có thể dùng `FileLock.release()`/`close()` theo hợp đồng.
- `SelectableChannel` có thể đăng ký với `Selector`. Đóng channel làm các selection key tương ứng bị hủy; nhưng **Selector là tài nguyên riêng**, phải được đóng bởi thành phần sở hữu nó.
- `Selector.wakeup()` đánh thức thread đang chờ ở `select()`; đó là cơ chế điều phối, **không phải** thay thế cho `channel.close()` hoặc `selector.close()`.

Một thiết kế server thường có trách nhiệm đóng rõ ràng cho socket channel, selector, task executor và các tài nguyên liên quan. Đóng duy nhất một socket không đồng nghĩa executor hoặc selector của server tự dừng.

## 6. Chương trình Java 17+ chạy được: interrupt và channel đóng

Ví dụ dùng `Pipe.SourceChannel` có hỗ trợ `InterruptibleChannel`. Không gửi byte nào vào pipe nên thread đọc sẽ cần chờ. Thread chính gửi interrupt và đợi thread đọc kết thúc trước khi kiểm tra trạng thái.

**Tệp: `ChannelLifecycleDemo.java`**

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Pipe;
import java.util.concurrent.CountDownLatch;

public class ChannelLifecycleDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink()) {

            CountDownLatch started = new CountDownLatch(1);
            Thread reader = new Thread(() -> {
                try {
                    started.countDown();
                    source.read(ByteBuffer.allocate(1));
                    System.out.println("reader: completed");
                } catch (ClosedByInterruptException ex) {
                    System.out.println("reader: ClosedByInterruptException");
                    System.out.println("reader interrupted: " +
                            Thread.currentThread().isInterrupted());
                } catch (IOException ex) {
                    System.out.println("reader: " +
                            ex.getClass().getSimpleName());
                }
            });

            reader.start();
            started.await();
            reader.interrupt();
            reader.join();

            System.out.println("source open: " + source.isOpen());

            try {
                source.read(ByteBuffer.allocate(1));
            } catch (ClosedChannelException ex) {
                System.out.println("later: ClosedChannelException");
            }

            sink.close();
            System.out.println("sink open: " + sink.isOpen());
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~bash
javac ChannelLifecycleDemo.java
java ChannelLifecycleDemo
~~~

**Stdout:**

~~~text
reader: ClosedByInterruptException
reader interrupted: true
source open: false
later: ClosedChannelException
sink open: false
~~~

Giải thích:

1. `Pipe.open()` tạo hai channel. `source.read(...)` đọc 1 byte nhưng sink không gửi dữ liệu.
2. `started.await()` đảm bảo thread đọc đã đến đoạn sắp gọi `read`. Nếu interrupt đến trước khi thực sự block, hợp đồng `InterruptibleChannel` vẫn làm đóng channel khi blocking I/O bắt đầu với interrupt flag đã bật.
3. `reader.interrupt()` làm thread đọc nhận `ClosedByInterruptException`, interrupt flag `true`; source đã đóng.
4. Sau `join()`, thread chính kiểm tra `source.isOpen()` cho `false`. Lời gọi đọc tiếp nhận `ClosedChannelException` vì source đã đóng.
5. Đóng sink riêng biệt và in `sink.isOpen() == false`. Hai channel trong pipe có vòng đời riêng, và `try-with-resources` còn bảo đảm cleanup nếu có exception.

`CountDownLatch` làm bài toán có trật tự đủ rõ; chương trình không dựa vào `Thread.sleep(...)` hoặc thời gian thực thi tuyệt đối. Không sử dụng sleep để “chứng minh” kết quả ngắt theo một thời gian may rủi.

## 7. Asynchronous close từ thread khác: phân biệt với interrupt

Một trường hợp khác: thread A đang bị chặn ở `source.read(dst)`; thread B gọi `source.close()` thay vì `readerThread.interrupt()`.

Nếu thao tác đọc đã bị chặn trong `InterruptibleChannel`, thread A nhận `AsynchronousCloseException`. Đóng channel như vậy **không tự đặt interrupt flag** của A. Nếu A bắt đầu `read` sau khi channel đã bị đóng, có thể nhận `ClosedChannelException` trực tiếp.

Mô hình minh họa (không phải chương trình độc lập):

~~~java
// Thread A:
try {
    source.read(dst);
} catch (ClosedByInterruptException e) {
    // A bị interrupt, channel đóng, interrupt flag được set.
} catch (AsynchronousCloseException e) {
    // Thread khác đóng channel trong khi A đang ở blocking I/O.
}

// Thread B, khi muốn dừng I/O:
source.close();
~~~

Cách dừng phù hợp tùy thiết kế: `interrupt()` biểu thị yêu cầu hủy công việc của thread và có thể đóng các `InterruptibleChannel` đang được dùng; `close()` là hành động kết thúc **tài nguyên channel** mà nhiều tác vụ có thể đang chia sẻ. Không tùy tiện interrupt một worker đang sở hữu channel dùng chung nếu không định đóng channel đó.

## 8. Sự khác biệt với non-blocking mode và async channel

`SelectableChannel.configureBlocking(false)` cho phép một số channel như `SocketChannel`, `Pipe.SourceChannel` hoạt động **non-blocking**. Khi đó, `read` có thể trả `0` nếu hiện chưa có dữ liệu thay vì chờ. Điều này thay đổi cách tổ chức event loop nhưng **không loại bỏ vòng đời close** hay các ngoại lệ nếu channel bị đóng.

- Với non-blocking I/O, không nên kỳ vọng mọi lần gọi `interrupt` đều tạo `ClosedByInterruptException`: hợp đồng này nói tới **blocking I/O** trên `InterruptibleChannel`.
- `Selector.select()` có thể được đánh thức bằng `Selector.wakeup()` hoặc interrupt thread theo hợp đồng của Selector; đó là chuyện điều phối vòng lặp, không giống thread đang block trong `SocketChannel.read`.
- `AsynchronousFileChannel` và `AsynchronousSocketChannel` dùng `Future` hoặc `CompletionHandler`; cancel operation, close channel và interrupt worker không mặc nhiên đồng nghĩa. Phải đọc hợp đồng cụ thể của từng kiểu asynchronous channel.

Khi gặp I/O tưởng “treo”, trước tiên xác định đây là blocking channel operation, non-blocking event loop hay asynchronous task trước khi chọn cách dừng.

## 9. Quản lý tài nguyên và lỗi trong ứng dụng thực tế

### 9.1. Đặt quyền sở hữu rõ ràng

Một quy ước hữu ích:

| Ai tạo tài nguyên? | Ai nên đóng? | Ghi chú |
|---|---|---|
| Phương thức tự gọi `FileChannel.open` | Phương thức đó, qua `try-with-resources` | Trừ khi chuyển ownership rõ ràng |
| Service giữ channel dùng chung nhiều request | Service khi kết thúc vòng đời | Request không được tự ý đóng channel dùng chung |
| `FileInputStream` và `getChannel()` | Chủ sở hữu stream/channel liên kết | Cùng nguồn tài nguyên, đóng một bên ảnh hưởng bên còn lại |
| `Pipe.open()` | Chủ sở hữu pipe đóng cả source và sink | Hai channel riêng |
| Server giữ `Selector` và các socket channel | Server quản lý và đóng từng loại | Selector/executor không tự đóng khi socket đóng |

`Channels.newInputStream(channel)` và `Channels.newOutputStream(channel)` tạo adapter sang API stream; việc đóng adapter có liên quan đến channel bên dưới. Hãy đọc hợp đồng adapter trước khi trao quyền đóng cho thành phần khác, tránh “double ownership”.

### 9.2. Thực hiện shutdown có thứ tự

Với service đang giữ các task đọc/ghi, quy trình shutdown hợp lý có thể là:

1. Ngừng nhận tác vụ mới.
2. Báo hiệu dừng cho worker và đánh thức thread đang đợi ở tầng điều phối nếu cần.
3. Đóng channel mà service sở hữu để chấm dứt I/O đang chặn.
4. Đợi task kết thúc (có giới hạn thời gian và chính sách failure phù hợp); xử lý `AsynchronousCloseException` / `ClosedByInterruptException` như trạng thái shutdown nếu đó là điều đã chủ động yêu cầu.
5. Đóng những tài nguyên riêng khác: selector, streams, file locks, executor, mapping hoặc tài nguyên native được quản lý theo API tương ứng.

Không nên nuốt tất cả `IOException` một cách vô điều kiện: lỗi close xảy ra bất ngờ ngoài shutdown có thể chỉ ra mất dữ liệu hoặc sự cố backend.

### 9.3. Đóng không đồng nghĩa dữ liệu được lưu bền vững

`FileChannel.close()` giải phóng channel, nhưng nếu ứng dụng yêu cầu bảo đảm đẩy thay đổi xuống thiết bị, cần hiểu `FileChannel.force(boolean metaData)` và khả năng thực tế của file store. Trên thiết bị không phải local, Javadoc không bảo đảm mạnh tương đương. Nếu ghi qua `MappedByteBuffer`, xem `MappedByteBuffer.force()` cho phần dữ liệu thuộc mapping.

Ví dụ minh họa:

~~~java
try (FileChannel fc = FileChannel.open(path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE)) {
    // Ghi dữ liệu qua fc.
    fc.force(true); // yêu cầu flush dữ liệu và metadata theo hợp đồng
}
~~~

**Mã minh họa** với biến `path` và thao tác ghi chưa trình bày. `force(true)` không biến một tập hợp thao tác file thành transaction nguyên tử và không tự bảo vệ trước lỗi logic ứng dụng.

### 9.4. Hết use-scope vẫn có thể còn tài nguyên độc lập

Nếu trả về một `MappedByteBuffer` từ một hàm đã đóng `FileChannel`, mapping vẫn có thể còn tồn tại. Nếu giữ các `ByteBuffer` thường chứa dữ liệu đã đọc, chúng là đối tượng riêng, không vì channel đóng mà bị “xóa” dữ liệu. Một `FileLock`, selector và các stream/socket liên kết lại tuân theo hợp đồng riêng của chúng.

Khi debug lỗi `Too many open files` hoặc leak native memory, kiểm tra cả vòng đời file/channel, socket/selector, mmap và các stream adapter, thay vì chỉ nhìn `isOpen()` của một đối tượng.

## 10. Những lỗi phổ biến cần tránh

1. **Nghĩ `isOpen()` là bảo đảm thao tác ngay sau sẽ thành công.** Thread khác có thể đóng trước khi I/O diễn ra.
2. **Nghĩ `close()` của mọi `Channel` luôn đánh thức mọi kiểu blocking operation như nhau.** Chỉ khẳng định các semantics interrupt/asynchronous close khi API kiểu tương ứng bảo đảm, đặc biệt `InterruptibleChannel`.
3. **Bắt `AsynchronousCloseException` trước `ClosedByInterruptException`.** Khi đó không thể có nhánh catch cụ thể cho subclass phía sau.
4. **Bắt `ClosedByInterruptException` rồi retry trên cùng channel.** Channel đã bị đóng; cần dừng hoặc mở lại channel mới.
5. **Dùng `Thread.interrupted()` chỉ để log.** Phương thức này xóa interrupt flag; dùng `isInterrupted()` nếu chỉ cần đọc.
6. **Đóng channel dùng chung từ request riêng.** Các request khác đang I/O có thể thất bại; quy định ownership ở cấp service.
7. **Cho rằng đóng FileChannel làm `MappedByteBuffer` bị vô hiệu.** Mapping có vòng đời độc lập.
8. **Bỏ quên đầu còn lại của Pipe hoặc Selector.** Chúng là tài nguyên riêng.
9. **Cho rằng đóng channel = flush bền vững.** Xem `FileChannel.force` / `MappedByteBuffer.force` và giới hạn provider.
10. **Biến mọi IOException thành “cancelled”.** Phân loại ngoại lệ theo bối cảnh shutdown, lỗi truy cập hoặc hỏng backend.

## 11. Câu hỏi tự kiểm tra

**Câu 1:** Thread A đang `read` một `InterruptibleChannel` và B gọi `A.interrupt()`. A nhận ngoại lệ gì, channel còn dùng tiếp được không?

**Đáp án:** `ClosedByInterruptException`; interrupt flag của A được set và channel đóng. Muốn tiếp tục I/O cần tạo channel mới.

**Câu 2:** Thread B gọi `channel.close()` khi A đang blocked trong I/O. Có đảm bảo A nhận đúng `AsynchronousCloseException` nếu chưa chắc A đã vào thao tác blocking chưa?

**Đáp án:** Khi A đang blocked theo hợp đồng `InterruptibleChannel` thì có. Nếu B đóng trước khi A gọi `read`, A có thể nhận `ClosedChannelException`. Phải xử lý race đúng.

**Câu 3:** Có thể tiếp tục dùng một `MappedByteBuffer` đã tạo sau khi đóng `FileChannel` không?

**Đáp án:** Theo Javadoc, mapping không phụ thuộc vòng đời FileChannel sau khi được tạo. Tuy vậy, hiệu lực truy cập còn chịu giới hạn OS và thay đổi trên file; việc đóng channel không giải phóng mapping ngay.

**Câu 4:** `isOpen() == true` có chứng minh lệnh `write` kế tiếp chắc chắn thành công không?

**Đáp án:** Không. Thread khác có thể đóng channel hoặc I/O có thể lỗi vì những nguyên nhân khác.

### Tài liệu chính thức (Java 17)

- [Channel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Channel.html)
- [InterruptibleChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/InterruptibleChannel.html)
- [ClosedChannelException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ClosedChannelException.html)
- [AsynchronousCloseException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousCloseException.html)
- [ClosedByInterruptException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ClosedByInterruptException.html)
- [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html)
- [MappedByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html)
- [Pipe](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Pipe.html)


</details>

- [Quay lại đầu trang](#back-to-top)
