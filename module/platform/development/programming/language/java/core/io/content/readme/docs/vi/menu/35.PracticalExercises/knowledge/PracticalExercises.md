<a id="back-to-top"></a>

# Thực hành

## Menu
- [Sao chép tệp nhị phân với buffer giới hạn](#practical-exercises-001)
- [Chuyển văn bản giữa hai charset có kiểm tra lỗi](#practical-exercises-002)
- [Đọc file lớn theo dòng và thống kê dữ liệu](#practical-exercises-003)
- [Thiết kế định dạng nhị phân có version và length](#practical-exercises-004)
- [Xây dựng công cụ duyệt cây và lọc tệp](#practical-exercises-005)
- [Cập nhật tệp bằng ghi tạm và thay thế](#practical-exercises-006)
- [So sánh RandomAccessFile với FileChannel trên bản ghi cố định](#practical-exercises-007)
- [Xây dựng vòng selector cho giao tiếp TCP đơn giản](#practical-exercises-008)
- [Theo dõi thư mục bằng WatchService có xử lý overflow](#practical-exercises-009)
- [Nén, giải nén archive có giới hạn và kiểm tra đường dẫn](#practical-exercises-010)
- [Minh họa serialization và cấu hình ObjectInputFilter](#practical-exercises-011)
- [Hoàn thiện ví dụ tích hợp với kiểm thử các nhánh thất bại](#practical-exercises-012)

## <a id="practical-exercises-001">Sao chép tệp nhị phân với buffer giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Sao chép tệp nhị phân với buffer giới hạn trong Java Core I/O

## 1. Mục tiêu bài thực hành

Bài thực hành này yêu cầu **sao chép chính xác từng byte của một tệp nhị phân** từ đường dẫn nguồn sang đường dẫn đích, nhưng **không đọc toàn bộ tệp vào bộ nhớ**. Tệp có thể nhỏ vài byte hoặc lớn nhiều gigabyte; chương trình chỉ sử dụng một buffer có kích thước cố định, tái sử dụng buffer đó cho đến khi gặp EOF.

Kỹ năng cần nắm:

1. Phân biệt sao chép byte với đọc/ghi văn bản và giải mã charset.
2. Sử dụng `FileChannel`, `ByteBuffer`, `clear()`, `flip()` và `hasRemaining()`.
3. Đọc dữ liệu theo từng khối, nhận đúng giá trị trả về của `read()` và không nhầm `0` với EOF `-1`.
4. **Xử lý partial write**: một lần `write()` không bắt buộc ghi hết vùng dữ liệu đang chờ.
5. Chỉ cần bộ nhớ **O(B)**, trong đó `B` là buffer capacity, không phụ thuộc tuyến tính vào kích thước file.
6. Đóng channel khi hoàn tất hoặc gặp lỗi; tránh ghi đè đích ngoài ý muốn.
7. Kiểm chứng cả **số byte** lẫn **nội dung** của hai tệp.
8. Biết khi nào nên dùng `Files.copy`, stream truyền thống, `FileChannel.transferTo()` hoặc thiết kế ghi file tạm để an toàn hơn.

Bối cảnh: **Java Core - io → Thực hành → Sao chép tệp nhị phân với buffer giới hạn**. Bài có chương trình **Java 17+ hoàn chỉnh**, có lệnh biên dịch/chạy và stdout xác định.

## 2. Vì sao không dùng `Files.readAllBytes()` cho file lớn?

Cách đơn giản sau phù hợp để thử với file nhỏ nhưng không phải giải pháp streaming bộ nhớ giới hạn:

```java
byte[] all = Files.readAllBytes(source);
Files.write(destination, all);
```

Đây là đoạn minh họa, cần import `java.nio.file.Files` và hai biến `Path` thích hợp.

Nhược điểm:

- `readAllBytes()` cố đưa **toàn bộ nội dung file** vào một mảng Java, khiến nhu cầu heap tăng theo kích thước tệp.
- Mảng Java có giới hạn chỉ số `int`, nên không thể là container tùy ý cho file nhiều GiB.
- Dễ gặp `OutOfMemoryError` khi tệp lớn hoặc nhiều thao tác sao chép chạy cùng lúc.
- Mỗi lần thực hiện có thể tạo một mảng rất lớn, tăng áp lực GC và cạnh tranh bộ nhớ với công việc khác.

**Buffer giới hạn** giải quyết vấn đề bằng một vòng lặp:

```text
Nguồn (có thể rất lớn)
  │
  │ đọc tối đa B byte/lượt
  ▼
┌──────────────────────────┐
│ ByteBuffer cố định: B    │
└──────────────────────────┘
  │
  │ ghi hết những byte thực nhận
  ▼
Đích

Lặp tới khi read() trả -1.
```

Ví dụ file 10.257 byte với buffer capacity 4.096 byte có thể cần nhiều lượt đọc. Buffer luôn chỉ có sức chứa 4.096 byte. Không được giả định số lượt thực tế bằng đúng 3, vì `read` có thể trả ít hơn capacity; chương trình phải xử lý mọi lượt đọc hợp lệ.

**Kết luận:** không cần tạo mảng bằng kích thước file, không cần giữ tất cả byte trong RAM, và không cần biết trước file size để hoàn thành bản sao tuần tự.

## 3. Sao chép nhị phân khác sao chép văn bản

**Sao chép nhị phân** phải giữ nguyên mọi byte, bao gồm:

- Byte `0x00` (NUL);
- Byte `0xFF` (Java `byte` biểu diễn là `-1` nếu xem như số có dấu);
- Byte xuống dòng `0x0A`, carriage return `0x0D`;
- Mọi chuỗi byte không phải UTF-8 hợp lệ;
- Header, compressed data, checksum, trailing zero hoặc binary metadata.

Không làm thế này cho dữ liệu nhị phân:

```java
String text = Files.readString(source);
Files.writeString(destination, text);
```

Lý do: việc **giải mã rồi mã hóa lại** dựa trên charset có thể không định nghĩa được đối với dữ liệu bất kỳ, chuyển đổi byte hoặc ném lỗi. Ngoài ra, các thao tác theo dòng có thể thay đổi newline/terminator. Tệp `.png`, `.zip`, `.pdf`, `.class`, `.dat`, file thực thi và tệp nhị phân tự định nghĩa không cần chuyển qua `String`.

**Quy tắc:** lấy byte từ nguồn rồi ghi đúng byte sang đích. Nếu yêu cầu là **biến đổi encoding**, đó là một bài toán khác và kết quả không còn là bản sao byte-đúng-byte.

## 4. Các thành phần Java NIO cần biết

| Thành phần | Vai trò | Điều phải nhớ |
| --- | --- | --- |
| `Path` | Đường dẫn tệp | Phân biệt source và destination; kiểm tra chính sách ghi đè |
| `FileChannel` | Channel có thể đọc/ghi byte và quản lý vị trí trong tệp | `read`/`write` trả số byte thực sự chuyển |
| `ByteBuffer` | Vùng byte cố định cùng trạng thái `position/limit/capacity` | Không tự hiểu đâu là “byte mới” nếu dùng sai trạng thái |
| `buffer.clear()` | Chuẩn bị buffer cho lượt **ghi từ channel vào buffer** | Không xóa byte cũ trong storage |
| `buffer.flip()` | Chuẩn bị buffer cho lượt **đọc từ buffer ra channel đích** | Đặt `limit` bằng số byte đã nhận |
| `buffer.hasRemaining()` | Có byte chưa được channel đích lấy hay không | Bắt buộc xét partial write |
| `StandardOpenOption.READ` | Mở nguồn chỉ để đọc | Không cần quyền sửa nguồn |
| `StandardOpenOption.CREATE_NEW` | Tạo đích mới, thất bại nếu tên đã tồn tại | Tránh ghi đè ngoài ý muốn |
| `StandardOpenOption.WRITE` | Mở đích để ghi | Đi cùng chính sách tạo đích |

**Cảnh báo quan trọng:** `FileChannel.read(ByteBuffer)` có thể trả số byte dương nhỏ hơn `buffer.remaining()` ban đầu. `FileChannel.write(ByteBuffer)` cũng có thể lấy ít byte hơn số còn lại. Các vòng lặp phải dựa vào **kết quả thực** và trạng thái buffer, không dựa vào kỳ vọng một lần đọc/ghi sẽ hoàn tất cả khối.

## 5. Luồng trạng thái ByteBuffer trong một lượt

Giả sử buffer có capacity 8 và lần này chỉ đọc được 3 byte `A`, `B`, `C`.

```text
Mới cấp / sau clear():
  position=0, limit=8, capacity=8
  [ _ _ _ _ _ _ _ _ ]

Sau source.read(buffer) trả 3:
  position=3, limit=8
  [ A B C ? ? ? ? ? ]
        ^
        position

Sau buffer.flip():
  position=0, limit=3
  [ A B C | các byte ngoài limit không hợp lệ cho lượt này ]

Trong khi destination.write(buffer):
  Có thể ghi 1 byte, rồi 2 byte;
  hoặc ghi cả 3 byte trong một lần.
  Khi hết: position=3, limit=3, hasRemaining()=false.

Lượt kế:
  clear() → position=0, limit=8;
  buffer được tái sử dụng, không cấp lại.
```

Không gọi `clear()` khi buffer còn byte chưa được đích ghi. Nếu làm vậy, bạn **mất trạng thái phần dữ liệu còn lại**; ở lượt sau nguồn có thể ghi đè byte chưa chép xong.

Ngược lại, không quên `clear()` trước lượt đọc nguồn kế. Nếu buffer vẫn ở `position==limit`, nó có `remaining()==0`, làm việc đọc không tiến triển mặc dù nguồn có thể còn dữ liệu.

## 6. Thuật toán chuẩn: đọc → flip → ghi hết → lặp

```text
buffer = allocate(fixedCapacity)
totalCopied = 0

mở source READ
mở destination CREATE_NEW + WRITE

lặp:
    buffer.clear()
    n = source.read(buffer)

    nếu n == -1:
        break                  # EOF của file nguồn
    nếu n == 0:
        áp dụng chính sách no-progress
        không coi là EOF

    buffer.flip()              # Chỉ n byte mới là hợp lệ
    trong khi buffer.hasRemaining():
        written = destination.write(buffer)
        nếu written == 0:
            áp dụng chính sách no-progress
            không bỏ byte chưa ghi

    totalCopied += n

đóng cả hai channel
kiểm tra kích thước và nội dung khi cần
```

### 6.1. Vì sao phải lặp `hasRemaining()`?

`destination.write(buffer)` cập nhật `buffer.position()` bằng **đúng số byte đã ghi**. Nếu đích chỉ nhận một phần, phần dư vẫn nằm trong buffer và có thể ghi tiếp. Điều kiện `hasRemaining()` giữ cho vòng lặp chạy đến khi **toàn bộ khối đã được chuyển**.

Mã sai:

```java
buffer.flip();
destination.write(buffer); // Có thể ghi thiếu!
buffer.clear();            // Phần chưa ghi đã mất trạng thái.
```

Mã đúng:

```java
buffer.flip();
while (buffer.hasRemaining()) {
    int written = destination.write(buffer);
    if (written == 0) {
        throw new IOException("No progress while writing");
    }
}
```

Đây là đoạn minh họa trong một phương thức, giả định đã có `ByteBuffer buffer` và `FileChannel destination`, cùng import `java.io.IOException`. Với `FileChannel` ghi ra regular file trong tình huống phổ biến, `0` khi còn byte là bất thường; bản thực hành chọn **báo lỗi** thay vì busy-spin vô hạn. Với socket non-blocking, `write()==0` có thể bình thường, và phải dùng `Selector`/`OP_WRITE` hay cơ chế chờ phù hợp thay vì ném lỗi mặc định.

### 6.2. Vì sao `read()==0` khác EOF?

- `n > 0`: nguồn đã chuyển **n byte**, dù n nhỏ hơn capacity.
- `n == 0`: lượt gọi chưa chuyển byte, **không khẳng định nguồn đã hết**.
- `n == -1`: nguồn báo **end-of-stream**.
- Exception: lỗi I/O thực tế, không được ngầm coi là EOF.

Trong bản thực hành với `FileChannel` và buffer dung lượng dương luôn được `clear()`, `read()==0` sẽ được coi là **no-progress error** để tránh vòng lặp tiêu thụ CPU. Nếu triển khai trên `SocketChannel` non-blocking, chính sách này cần thay đổi: `0` có thể là tình huống chờ dữ liệu hoàn toàn hợp lệ.

## 7. Chương trình Java 17+ hoàn chỉnh: chép file nhị phân bằng 4 KiB

Ví dụ dưới đây:

1. Tạo một thư mục tạm.
2. Tạo file nguồn **10.257 byte** với mẫu nhị phân xác định, có nhiều giá trị bao gồm `0x00` và `0xFF`.
3. Sao chép bằng **một `ByteBuffer` 4.096 byte** được tái sử dụng.
4. Mở file đích bằng `CREATE_NEW`, để không âm thầm ghi đè một file có sẵn.
5. Xác minh `Files.size()` và `Files.mismatch()` sau khi copy xong.
6. In kết quả không phụ thuộc vào tên file tạm.
7. Đóng stream/channel bằng `try-with-resources` và dọn file tạm trong `finally`.

Tạo tệp **`BoundedBinaryCopyDemo.java`**:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class BoundedBinaryCopyDemo {
    private static final int BUFFER_BYTES = 4096;
    private static final int FILE_BYTES = 10_257;
    private static final int MAX_BUFFER_BYTES = 1024 * 1024;

    static void createFixture(Path source) throws IOException {
        byte[] block = new byte[257];
        for (int i = 0; i < block.length; i++) {
            block[i] = (byte) ((i * 31 + 7) & 0xFF);
        }

        try (OutputStream out = Files.newOutputStream(
                source, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            int remaining = FILE_BYTES;
            while (remaining > 0) {
                int n = Math.min(remaining, block.length);
                out.write(block, 0, n);
                remaining -= n;
            }
        }
    }

    static long copy(Path source, Path destination, int capacity)
            throws IOException {

        if (capacity <= 0 || capacity > MAX_BUFFER_BYTES) {
            throw new IllegalArgumentException(
                    "Buffer must be between 1 and " + MAX_BUFFER_BYTES);
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source is not a regular file");
        }

        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        long totalCopied = 0L;

        try (FileChannel in = FileChannel.open(
                     source, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(
                     destination, StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {

            while (true) {
                buffer.clear();
                int n = in.read(buffer);

                if (n == -1) {
                    break;
                }
                if (n == 0) {
                    throw new IOException(
                            "Read made no progress");
                }

                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = out.write(buffer);
                    if (written == 0) {
                        throw new IOException(
                                "Write made no progress");
                    }
                }
                totalCopied += n;
            }
        }
        return totalCopied;
    }

    public static void main(String[] args) throws IOException {
        Path directory =
                Files.createTempDirectory("bounded-binary-copy-");
        Path source = directory.resolve("source.bin");
        Path destination = directory.resolve("destination.bin");

        try {
            createFixture(source);

            long copied = copy(source, destination, BUFFER_BYTES);
            boolean identical =
                    Files.mismatch(source, destination) == -1L;

            System.out.println("Source bytes: " + Files.size(source));
            System.out.println("Copied bytes: " + copied);
            System.out.println("Target bytes: "
                    + Files.size(destination));
            System.out.println("Buffer capacity: " + BUFFER_BYTES);
            System.out.println("Content identical: " + identical);
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BoundedBinaryCopyDemo.java
java BoundedBinaryCopyDemo
```

Stdout dự kiến khi filesystem hỗ trợ tạo/đọc/ghi và xóa tệp tạm:

```text
Source bytes: 10257
Copied bytes: 10257
Target bytes: 10257
Buffer capacity: 4096
Content identical: true
```

### 7.1. Giải thích chương trình theo thứ tự

**A. Dữ liệu nguồn là nhị phân thực sự.** Vòng khởi tạo `block` tính `(i * 31 + 7) & 0xFF`, rồi ép sang `byte`. Với các giá trị khác nhau, block có thể chứa byte 0, byte 255 và nhiều giá trị không có nghĩa như ký tự văn bản. `createFixture()` ghi đủ 10.257 byte, chỉ dùng mảng 257 byte và không cấp mảng bằng toàn bộ file.

**B. Kích thước buffer được kiểm soát.** Hàm `copy` nhận `capacity`, kiểm tra giới hạn dương và tối đa 1 MiB, sau đó tạo **một** `ByteBuffer.allocate(capacity)` trước vòng đọc. Ở lời gọi demo, capacity đúng 4.096.

**C. Nguồn được mở chỉ đọc.** `FileChannel.open(source, READ)` không yêu cầu quyền sửa nội dung nguồn. Ví dụ kiểm tra `isRegularFile(..., NOFOLLOW_LINKS)` để từ chối source symbolic link ở giai đoạn kiểm tra ban đầu. Tuy vậy, đây **không phải một cơ chế chống TOCTOU tuyệt đối** nếu tác nhân khác có thể thay đổi path giữa lúc kiểm tra và lúc mở file.

**D. Đích được tạo mới.** `CREATE_NEW` từ chối tên file đích đã tồn tại (kể cả nếu đó là symbolic link theo quy tắc API). Demo không ghi đè một file cũ. Khi gặp lỗi sau khi tạo đích, **có thể đã tồn tại file đích sao chép dở**; khối `finally` trong demo chủ động xóa file tạm. Ứng dụng thật phải có chính sách cleanup riêng.

**E. `n` không phải giá trị byte.** `in.read(buffer)` trả số byte thực nhận. Nếu nhận byte `0xFF` hay `0x00`, kết quả `n` vẫn là **số lượng**, không phải `-1` hoặc `0` tương ứng nội dung.

**F. `flip()` rồi ghi hết.** `buffer.flip()` giới hạn vùng dữ liệu hợp lệ về đúng `n` byte. `while (buffer.hasRemaining())` xử lý mọi trường hợp đích chỉ ghi một phần. Nếu lần ghi đầu lấy 1 KiB của khối 4 KiB, ba KiB còn lại vẫn được giữ cho lượt tiếp theo.

**G. Dùng `long` cho tổng.** `totalCopied` là `long` nên không bị giới hạn ở 2 GiB như `int`; đây là cách đếm an toàn cho file lớn. `capacity` vẫn là `int` vì API `ByteBuffer` dùng capacity kiểu `int`.

**H. Kiểm tra kết quả.** `Files.size()` phản ánh kích thước file, còn `Files.mismatch(source,destination)` trả `-1L` khi không tìm thấy byte khác nhau; nếu khác, nó trả vị trí byte đầu tiên không khớp. So sánh kích thước đơn thuần **không đủ** khẳng định nội dung giống nhau. `Files.mismatch()` có từ Java 12 và dùng được với Java 17.

**I. Đóng tài nguyên.** Hai `FileChannel` được đóng kể cả khi `read` hoặc `write` ném `IOException`. Sau khi đóng, demo mới thực hiện kiểm tra và xóa dữ liệu tạm. Lưu ý việc xóa cũng có thể ném `IOException` tùy quyền truy cập và trạng thái filesystem.

## 8. Bất biến để tự chứng minh thuật toán không mất byte

Trong một lượt đọc, gọi:

- `p`: tổng số byte nguồn đã đọc trước lượt;
- `n`: số byte trả về bởi `in.read(buffer)`;
- `w`: tổng số byte thực sự đích đã ghi từ buffer ở lượt này.

Trước khi gọi `flip()`, `buffer.position()` đã tăng `n`. Sau `flip()`, `limit=n`, `position=0`.

Khi vòng `while (hasRemaining())` kết thúc bình thường:

```text
w == n
buffer.position() == buffer.limit() == n
totalCopied_new == p + n
```

Do đó, mỗi byte của khối đọc được chuyển sang đích **đúng một lần** theo vị trí của buffer, miễn là không có tác nhân ngoài cùng can thiệp vào channel/file. Sau đó vòng lặp đặt lại buffer để nhận byte nguồn tiếp theo.

Khi EOF:

```text
in.read(buffer) == -1
→ không có byte mới
→ không cần flip/ghi thêm
→ kết thúc vòng đọc
```

Nếu file nguồn chứa `N` byte và không thay đổi trong lúc copy, khi thành công thì `totalCopied == N`, file đích chứa byte theo đúng thứ tự và `Files.mismatch(source,dest) == -1L`.

**Không được suy luận** rằng `n < capacity` là EOF. Ví dụ `n=1` khi capacity=4096 vẫn là một lượt đọc dữ liệu thành công; tiếp tục cho tới `-1`.

## 9. Tại sao `CREATE_NEW` quan trọng?

Mặc định nếu sử dụng một số API mở ghi với tùy chọn khác, ứng dụng có thể tạo/ghi đè/truncate file đích, gây mất dữ liệu.

So sánh chính sách:

| Tùy chọn mở đích | Hành vi có ý nghĩa |
| --- | --- |
| `CREATE_NEW, WRITE` | Chỉ tạo file mới; tên đã tồn tại thì lỗi, tránh ghi đè ngoài ý muốn |
| `CREATE, WRITE, TRUNCATE_EXISTING` | Có thể tạo hoặc xóa nội dung file cũ; chỉ dùng khi ứng dụng có quyền overwrite rõ ràng |
| `APPEND, WRITE` | Thêm dữ liệu vào cuối file; **không phù hợp** với yêu cầu sao chép thay thế toàn bộ |
| `READ` | Chỉ đọc, không dùng cho output |
| `SYNC`/`DSYNC` hoặc `force()` | Liên quan tới mức độ đồng bộ bền vững, không thay thế kiểm tra đúng nội dung |

**Không sao chép vào chính file nguồn.** Nếu mở nguồn trước rồi mở đích với `TRUNCATE_EXISTING` nhưng thực chất cùng file, việc truncate có thể phá dữ liệu nguồn. Trong demo, `CREATE_NEW` cùng đường dẫn đích mới giúp tránh trường hợp đơn giản này.

Ở ứng dụng thực, nếu nguồn và đích có thể trùng qua hard link, symbolic link hoặc đường dẫn tương đương, hãy dùng `Files.isSameFile()` khi cả hai đã tồn tại và thiết kế chính sách kiểm soát race. Kiểm tra trước thao tác không đủ mạnh nếu một tác nhân khác có thể thay đổi cây thư mục đồng thời.

## 10. Phiên bản `InputStream` / `OutputStream` dùng mảng `byte[]`

Không bắt buộc dùng NIO. Nếu mục tiêu chỉ là sao chép byte với buffer giới hạn, API stream có thể dễ đọc hơn:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

static long copyWithStreams(
        Path source, Path destination, int bufferBytes)
        throws IOException {

    if (bufferBytes <= 0 || bufferBytes > 1024 * 1024) {
        throw new IllegalArgumentException("Invalid buffer size");
    }

    byte[] buffer = new byte[bufferBytes];
    long total = 0L;

    try (InputStream in = Files.newInputStream(source);
         OutputStream out = Files.newOutputStream(
                 destination, StandardOpenOption.CREATE_NEW,
                 StandardOpenOption.WRITE)) {

        int n;
        while ((n = in.read(buffer)) != -1) {
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
            out.write(buffer, 0, n);
            total += n;
        }
    }
    return total;
}
```

Đây là **phương thức để đặt trong một class**, không phải chương trình độc lập có `main`. Nó có cùng đặc tính cấp phát **một `byte[]` cố định**, dùng `long` để đếm byte, mở đích mới và tự đóng streams.

**Khác biệt quan trọng:** `OutputStream.write(byte[], 0, n)` có hợp đồng ghi đúng `n` byte hoặc ném exception; API không trả số lượng partial write như `FileChannel.write(ByteBuffer)`. Vì vậy không cần vòng `hasRemaining()` ở tầng `OutputStream`, nhưng vẫn cần xử lý `IOException`, hỏng dữ liệu đích một phần và flush/close đúng thời điểm.

Nếu chỉ cần chuyển byte, `InputStream`/`OutputStream` là lựa chọn hợp lý; dùng `FileChannel` để thực hành sâu cơ chế `ByteBuffer`/NIO, truy cập vị trí hoặc tối ưu đặc thù khi được đo.

## 11. `Files.copy()` và vì sao vẫn học cách copy có buffer

Java đã cung cấp:

```java
long copied = Files.copy(
        sourceInputStream, destinationPath);
```

Trong đó `sourceInputStream` là một `InputStream` đang mở và `destinationPath` là `Path`. Cách này thuận tiện cho nhiều use case. Với `Files.copy(Path,Path,CopyOption...)`, API còn hỗ trợ copy tệp trực tiếp, quy tắc xử lý symbolic link và lựa chọn sao chép thuộc tính tùy provider.

Nhưng trong **bài thực hành buffer giới hạn**, ta tự quản lý vòng đọc/ghi vì muốn:

- Chỉ định kích thước buffer của code ứng dụng;
- Đếm byte và giám sát tiến độ theo từng khối;
- Thêm checksum/hash trong lúc copy;
- Áp quota đọc/ghi, deadline, cancellation hoặc rate limit;
- Xử lý partial writes và hiểu `ByteBuffer` state;
- So sánh hiệu năng các capacity khác nhau có kiểm chứng.

Đừng hiểu `Files.copy()` là API tốn RAM theo kích thước file; các triển khai có thể dùng streaming hoặc tối ưu riêng. Khi **không cần kiểm soát loop**, thường ưu tiên API thư viện chuẩn thay vì tự viết một bản copy chưa được kiểm thử.

## 12. Tệp lớn trên 2 GiB: tránh giới hạn `int`

Một `ByteBuffer` có `capacity()` kiểu `int`, nhưng file position và `FileChannel.size()` dùng `long`. Không cần một buffer tương đương file size.

**Cách sai:**

```java
long size = Files.size(source);
ByteBuffer all = ByteBuffer.allocate((int) size);
```

Đây là snippet **cố ý sai**, vì `(int) size` có thể overflow và ngay cả một `int` dương rất lớn cũng có thể vượt ngân sách heap. File 5 GiB không thể nhét vào một `ByteBuffer` duy nhất có capacity 5 GiB.

**Cách đúng trong bài thực hành:**

```java
final int bufferBytes = 64 * 1024;
ByteBuffer buffer = ByteBuffer.allocate(bufferBytes);
long copied = 0L;

// Mỗi lượt đọc/ghi xử lý tối đa bufferBytes;
// copied tăng theo n thực tế và vẫn là long.
```

Tệp rỗng, tệp nhỏ hơn buffer, tệp không chia hết cho buffer hoặc tệp hàng chục GiB đều đi qua **cùng logic**. File lớn làm tăng số vòng lặp/thời gian, không khiến buffer phình theo file size.

Cũng đừng dùng `while (copied < Files.size(source))` làm điều kiện dừng duy nhất: kích thước file có thể thay đổi khi đang đọc, và `read()==-1` mới là dấu EOF của channel hiện tại. Nếu yêu cầu “snapshot byte tại thời điểm bắt đầu”, phải thiết kế chính sách đồng bộ/immutable file riêng.

## 13. Chọn buffer size theo mục tiêu và phép đo

Các ứng viên khởi đầu thường là **4, 16, 64, 256 KiB hoặc 1 MiB**; nhưng không có kích thước tối ưu cố định cho mọi file và hệ thống.

| Kích thước | Ưu điểm tiềm năng | Chi phí cần cân nhắc |
| --- | --- | --- |
| 4 KiB | Nhỏ, dễ thực hành, ít RAM per job | Nhiều lượt I/O hơn |
| 16–64 KiB | Thường là vùng thử hợp lý cho copy tuần tự | Cần benchmark workload thật |
| 256 KiB | Có thể giảm số lần gọi so với buffer nhỏ | Tăng bộ nhớ/connection |
| 1 MiB | Hữu ích nếu throughput còn tăng trong workload cụ thể | Không chắc nhanh hơn; tăng heap/direct memory |

Ví dụ 1.000 tác vụ cùng giữ buffer 64 KiB: tổng vùng buffer khoảng **62,5 MiB**. Nếu 1.000 buffer đều 1 MiB thì là khoảng **1.000 MiB** (gần 0,98 GiB), chưa tính object, socket, file descriptor và queue.

Đo:

1. Thời gian copy tệp thật (không chỉ thời gian đọc source);
2. MiB/s end-to-end;
3. Memory và GC khi nhiều tác vụ cùng chạy;
4. Heap `allocate` so với `allocateDirect`, nếu đang cân nhắc;
5. Warm-up, OS page cache, tải thiết bị, độ trễ p95/p99;
6. Kết quả đúng theo `Files.mismatch()` hay checksum phù hợp.

Nếu một buffer 64 KiB đạt gần bằng throughput buffer 1 MiB trong sai số, cấu hình nhỏ hơn có thể có lợi cho bộ nhớ. **Không tự kết luận 1 MiB tốt hơn chỉ vì nó lớn hơn.**

## 14. Heap và direct ByteBuffer có làm thay đổi tính đúng?

Dòng trong chương trình:

```java
ByteBuffer buffer = ByteBuffer.allocate(capacity);
```

có thể được thử thay bằng:

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
```

Cả hai vẫn hỗ trợ `clear()`, `flip()`, `hasRemaining()`, `get/put` và `FileChannel.read/write` theo giao thức buffer. Nếu thao tác đúng, **nội dung sao chép giống nhau**.

Điểm khác:

- Heap buffer có thể cấp phát nhanh hơn, dùng bộ nhớ heap.
- Direct buffer thường dùng vùng nhớ ngoài heap; JVM có thể tối ưu native I/O, nhưng không hứa nhanh hơn trong mọi workload.
- Direct allocation/deallocation thường tốn chi phí hơn, nên đặc biệt cần tái sử dụng và kiểm soát số buffer đồng thời.
- `buffer.clear()` **không xóa dữ liệu nhạy cảm trong vùng nhớ** và không giải phóng direct memory.
- Với direct buffer, không được giả định gọi được `buffer.array()`.

Chọn kiểu buffer bằng phép đo thực tế chứ không phải một nhận định “direct luôn nhanh hơn”.

## 15. Kiểm tra byte-đúng-byte bằng cách nào?

### 15.1. So sánh kích thước

```java
boolean sameSize = Files.size(source) == Files.size(destination);
```

Điều này hữu ích để phát hiện chép thiếu hoặc chép thừa, nhưng **chưa đủ**: hai file cùng số byte có thể khác nội dung.

### 15.2. `Files.mismatch()` (Java 12+)

```java
long firstMismatch = Files.mismatch(source, destination);

if (firstMismatch == -1L) {
    System.out.println("Identical bytes");
} else {
    System.out.println("Different at offset " + firstMismatch);
}
```

Theo hợp đồng API, `-1L` nghĩa là không có byte nào khác (và kích thước phù hợp). Nếu khác, hàm trả vị trí byte khác đầu tiên hoặc vị trí bằng kích thước file ngắn hơn khi một file là prefix của file kia.

Đây là **snippet để đặt trong phương thức** sau khi cả hai file đã đóng và ổn định. `Files.mismatch()` không phải một thao tác atomic trước thay đổi đồng thời: nếu có process khác sửa file giữa lúc so sánh, kết quả không đại diện cho một snapshot bất biến.

### 15.3. Hash để đối chiếu file lớn hoặc lưu kiểm toán

Có thể tính SHA-256 bằng `MessageDigest` khi đọc từng block. Hash giúp báo kết quả kiểm chứng độc lập với một lần so sánh byte đối byte, nhưng:

- Hash **không thay thế** một giao thức xác thực nguồn khi file đến từ bên không tin cậy, trừ khi giá trị hash kỳ vọng được cung cấp qua kênh tin cậy.
- Nếu tính digest lúc copy, hãy chú ý checksum nguồn vừa đọc và checksum đích sau ghi, hoặc đối chiếu kết quả bằng `Files.mismatch()` để phát hiện lỗi đường ghi.
- Không dùng CRC32 như chữ ký chống sửa đổi có chủ ý.

Để kiểm thử demo hoặc một file local ổn định, `Files.mismatch()` là công cụ dễ hiểu, trực tiếp và có trong JDK 17.

## 16. Xử lý lỗi: file đích đang sao chép dở

Nếu chương trình chép được 8 MiB rồi gặp hết dung lượng ổ đĩa, mất quyền ghi hoặc bị interrupt, **file đích có thể chứa 8 MiB dữ liệu chưa hoàn chỉnh**. `try-with-resources` đóng channel nhưng **không tự xóa file đích bị chép dở**.

Có ba chính sách phổ biến:

1. **Xóa file đích khi thất bại:** phù hợp khi ta vừa tạo file mới và chắc chắn ứng dụng sở hữu nó.
2. **Ghi vào tệp tạm trong cùng thư mục rồi chuyển tên sau khi thành công:** tránh công bố file đích dở dưới tên chính thức; cần lưu ý `ATOMIC_MOVE` có thể không được provider hỗ trợ.
3. **Giữ phần đã chép để resume:** phải có metadata tiến độ, kiểm tra source unchanged, offset, checksum và xử lý crash consistency. Không thể chỉ nhìn file đích đã có rồi mặc định resume an toàn.

### 16.1. Tại sao dùng file tạm để publish?

Sơ đồ:

```text
source.bin
   |
   | bounded-buffer copy
   v
destination.bin.part-<random>  (tệp tạm, cùng filesystem)
   |
   | verify bytes/size; close channels
   v
Files.move(temp, destination, ATOMIC_MOVE nếu hỗ trợ)
```

Nếu copy thất bại, ứng dụng xóa temp và tên đích chính thức chưa bị tạo do quy trình này. Nếu copy thành công, ứng dụng chỉ **công bố** file sau khi verify.

**Chú ý:** `ATOMIC_MOVE` có thể ném `AtomicMoveNotSupportedException`. Hành vi đối với target đã tồn tại khi dùng `ATOMIC_MOVE` có khác biệt theo provider; không dựa vào việc bỏ `REPLACE_EXISTING` như một bảo đảm tuyệt đối chống overwrite trong mọi tình huống. Cần quyết định policy đích tồn tại và kiểm tra/đồng bộ race phù hợp. Muốn bảo đảm dữ liệu đã bền trên thiết bị sau mất điện còn cần cân nhắc `force()`/đồng bộ thư mục theo filesystem/provider.

Cách copy vào temp nên có quota đĩa, cleanup, rollback và logging rõ ràng. Một thao tác `Files.move()` tự nó không biến mọi bước kiểm tra/copy thành giao dịch atomic toàn phần.

## 17. Tên file, symbolic link và các trường hợp đồng thời

Với đường dẫn do người dùng cung cấp:

- Không đưa source/destination tùy ý vào thư mục không được phép; giới hạn root, canonicalization và quyền truy cập theo threat model.
- `Files.isRegularFile(path, NOFOLLOW_LINKS)` chỉ kiểm tra ở một thời điểm. Nếu tác nhân khác thay đường dẫn thành symlink giữa check và open, đó là **TOCTOU**; cần cơ chế mở an toàn hơn trên nền tảng nếu dữ liệu không tin cậy.
- `CREATE_NEW` hữu ích để bảo vệ file đích đã tồn tại; vẫn cần chính sách dọn file mới tạo nếu thất bại.
- Tránh nguồn và đích chỉ là hai tên khác nhau của **cùng inode/file**; khi hai file đã tồn tại có thể dùng `Files.isSameFile()`.
- Nếu nguồn đang được tiến trình khác sửa/truncate trong khi copy, bản sao có thể không tương ứng một snapshot nhất quán. `Files.size()` ở một thời điểm không giải quyết được điều đó.
- `FileLock` có thể giúp phối hợp trong một hệ thống nhưng tính bắt buộc/advisory và hỗ trợ trên filesystem khác nhau. Không giả định nó thay thế toàn bộ cơ chế versioning hoặc quiescence.

Với bài thực hành tại chỗ, dùng **thư mục tạm riêng** như demo. Trong dịch vụ thực cần quyết định rõ ai được phép tạo/xóa nguồn và đích, và khi nào dữ liệu được coi là immutable.

## 18. Các trường hợp biên cần kiểm thử

| Tình huống | Hành vi mong đợi |
| --- | --- |
| File nguồn rỗng | `read` trả `-1`; tạo file đích rỗng; `copied=0` |
| File 1 byte | Chép đúng một byte |
| File nhỏ hơn buffer | Đọc một hoặc nhiều lượt hợp lệ rồi EOF, không chép byte rác |
| File có kích thước đúng bội của buffer | Không thêm khối rỗng; dừng ở EOF |
| File kích thước không chia hết cho buffer | Khối cuối ngắn hơn, vẫn ghi đúng số byte |
| File có `0x00`, `0xFF`, dữ liệu UTF-8 không hợp lệ | Chép nguyên byte không decode |
| Tệp lớn hơn 2 GiB | `long totalCopied`; không allocate theo file size |
| Đích đã tồn tại | `CREATE_NEW` báo lỗi thay vì ghi đè |
| Nguồn không tồn tại | Lỗi I/O/kiểm tra nguồn, không tạo bản sao thành công |
| Hết đĩa giữa chừng | Lỗi ghi; cần cleanup file đích dở |
| Một lần write ghi ít hơn buffer | Vòng `hasRemaining()` hoàn tất phần dư |
| Source bị thay đổi trong lúc copy | Không có snapshot guarantee; cần chính sách ứng dụng |
| Thiếu quyền đọc/ghi | `IOException` hoặc lỗi quyền, đóng tài nguyên |
| Direct vs heap | Copy cùng nội dung nếu code đúng, hiệu năng cần đo |

### 18.1. Tự thêm bài test đổi một byte

Sau khi copy xong, nếu cố tình sửa byte đầu ở file đích:

```java
try (FileChannel out = FileChannel.open(
        destination, StandardOpenOption.WRITE)) {
    ByteBuffer one = ByteBuffer.wrap(new byte[] {(byte) 123});
    out.write(one, 0L);
}

long mismatch = Files.mismatch(source, destination);
```

Đây là **snippet thử lỗi**, không nằm trong chương trình demo chính; giả sử byte đầu của nguồn không phải 123, và đã import API tương ứng. Kết quả `mismatch` sẽ là vị trí byte khác đầu tiên (với fixture trong demo, byte đầu nguồn là `7`, nên `mismatch==0L`). Sau test này hai file **không còn giống nhau**; không dùng kết quả sau sửa để phủ nhận chức năng copy gốc.

## 19. Những lỗi triển khai phổ biến

| Lỗi | Hậu quả | Cách đúng |
| --- | --- | --- |
| `readAllBytes()` cho file nhiều GiB | Tốn heap hoặc vượt giới hạn mảng | Buffer cố định, streaming |
| `new String(bytes)` cho file nhị phân | Sai/không biểu diễn được dữ liệu | Copy byte trực tiếp |
| `new byte[(int) Files.size(path)]` | Tràn `int`, OOM | Capacity nhỏ và tổng kiểu `long` |
| `buffer.flip()` bị quên | Ghi 0 byte hoặc đọc phạm vi sai | `clear` → `read` → `flip` → `write` |
| Chỉ gọi `write` một lần | Có thể mất phần chưa ghi | `while (buffer.hasRemaining())` |
| `buffer.clear()` khi đích chưa ghi hết | Mất trạng thái byte còn tồn | Chỉ clear ở lượt source read kế |
| Coi `read()==0` là EOF | Dừng quá sớm trên channel nonblocking | `-1` mới là EOF |
| Coi `read() < capacity` là EOF | Bỏ sót dữ liệu | Lặp tới `-1` |
| Chỉ kiểm tra kích thước đích | Nội dung sai vẫn có thể cùng size | `Files.mismatch` hoặc hash |
| Đích mở với `TRUNCATE_EXISTING` ngoài ý muốn | Mất nội dung file cũ | `CREATE_NEW`/chính sách overwrite rõ |
| Không đóng channel khi lỗi | Rò file descriptors, dữ liệu chưa flush | `try-with-resources` |
| Không dọn file copy dở | File đích hỏng bị dùng nhầm | Temp/publish hoặc cleanup |
| Nguồn/đích là cùng file | Có thể tự phá nội dung | Kiểm tra path/identity và ownership |
| Tạo buffer mới trong mỗi vòng đọc | Allocation, GC/native overhead | Tái sử dụng một buffer |
| Khẳng định buffer 1 MiB luôn nhanh nhất | Không dựa trên phép đo | Benchmark trên workload thực |
| Sao chép file đang bị writer khác thay đổi | Kết quả không phải snapshot chắc chắn | Lock/version/immutable input theo policy |

## 20. Bài tập thực hành và lời giải

**Câu 1.** Tại sao không thể dùng `BufferedReader.readLine()` để sao chép `.png` hoặc `.zip`?

**Đáp án:** Nó thao tác văn bản và charset, đồng thời tách dòng; dữ liệu nhị phân tùy ý không bảo toàn byte khi decode/encode. Dùng byte stream hoặc byte channel.

**Câu 2.** Source 10.257 byte, buffer capacity 4.096. Khối cuối nếu các lần trước đều lấp đầy là bao nhiêu byte?

**Đáp án:** `10257 - 2 * 4096 = 2065` byte. Nhưng thực tế các lượt trước **không được bảo đảm** luôn đầy buffer, nên thuật toán dựa vào `read()` trả số byte thực, không dựa vào số khối dự kiến.

**Câu 3.** Sau `read` trả 3 vào buffer capacity 8, `flip()` cho giá trị `position`, `limit` bao nhiêu?

**Đáp án:** `position=0`, `limit=3`, nên chỉ ba byte mới được dùng cho lượt ghi.

**Câu 4.** Sau `write()` lần đầu, `buffer.hasRemaining()==true`. Có thể `clear()` luôn không?

**Đáp án:** Không. Cần tiếp tục ghi phần còn lại; `clear()` sẽ xóa ranh giới dữ liệu chưa ghi.

**Câu 5.** Byte `0xFF` có làm `FileChannel.read(ByteBuffer)` trả `-1` không?

**Đáp án:** Không. `read` trả số byte đã chuyển; `-1` là EOF chứ không phải một byte dữ liệu.

**Câu 6.** Vì sao `Files.size(src)==Files.size(dst)` chưa đủ chứng minh copy đúng?

**Đáp án:** Hai file cùng độ dài có thể có byte khác nhau. Kiểm tra bằng `Files.mismatch(src,dst)` (trả `-1L` nếu trùng) hoặc so hash khi phù hợp.

**Câu 7.** Với file 5 GiB, `long totalCopied` và `ByteBuffer.allocate(65536)` có dùng được không?

**Đáp án:** Có, nếu hệ thống tệp/tài nguyên cho phép. Mỗi vòng xử lý 64 KiB tối đa; tổng byte đếm bằng `long`, không phải `int`.

**Câu 8.** `CREATE_NEW` giúp giải quyết gì?

**Đáp án:** Không âm thầm mở/truncate tệp đích đã tồn tại. Nếu đích đã có tên, thao tác tạo mới thất bại.

**Câu 9.** Vì sao cần cleanup đích nếu lỗi xảy ra giữa chừng?

**Đáp án:** File đích có thể mới chứa một phần dữ liệu. Nếu coi đó là kết quả hoàn chỉnh sẽ làm người dùng hoặc tác vụ sau đọc dữ liệu bị cắt.

**Câu 10.** Nếu chuyển từ heap `ByteBuffer` sang direct `ByteBuffer`, copy có nhất thiết nhanh hơn không?

**Đáp án:** Không. Phải benchmark trong môi trường thực; direct memory vẫn tốn tài nguyên và có chi phí allocation/lifecycle riêng.

**Câu 11.** `Files.copy()` có luôn đọc nguyên tệp vào RAM không?

**Đáp án:** Không. Đây là API thư viện chuẩn có thể dùng chiến lược streaming/triển khai tối ưu. Tự viết vòng buffer chủ yếu để học cơ chế hoặc tùy biến tiến độ, giới hạn, hash, transform.

**Câu 12.** File nguồn đang thay đổi trong khi copy. `Files.mismatch` sau đó có bảo đảm bản sao của một snapshot tại thời điểm bắt đầu không?

**Đáp án:** Không. Việc copy và so sánh không atomic với writer ngoài. Cần bảo vệ hoặc đóng băng version dữ liệu theo yêu cầu nghiệp vụ.

## 21. Checklist trước khi dùng trong ứng dụng

1. Dùng API truyền byte (`FileChannel`, `InputStream`, `OutputStream`), không qua `Reader`/`Writer` với binary.
2. Đặt giới hạn buffer dương, phù hợp memory budget; cấp đúng **một lần** rồi tái sử dụng.
3. Khi đọc, kiểm tra `-1` cho EOF; không coi short read hoặc `0` là EOF.
4. `clear()` trước source read, `flip()` trước destination write, `hasRemaining()` để ghi hết khối.
5. Dùng `long` cho số byte tổng và offset của file lớn.
6. Mở source chỉ để đọc; xác định chính sách destination (CREATE_NEW, overwrite, staging temp).
7. Dùng `try-with-resources` và xử lý `IOException`, no-progress, hết đĩa, bị ngắt.
8. Xử lý file output một phần khi thất bại; chỉ publish sau khi hoàn tất và verify nếu cần.
9. Kiểm tra size và nội dung bằng `Files.mismatch` hoặc checksum phù hợp; không tin size một mình.
10. Đảm bảo source/destination khác nhau, xem xét symlink/hardlink và nguồn bị thay đổi đồng thời.
11. Thử file rỗng, một byte, khối cuối ngắn, binary có 0x00/0xFF, file lớn và lỗi ghi.
12. Đo throughput/latency/memory theo workload thật trước khi tăng buffer tùy tiện.

## Kết luận

Sao chép file nhị phân an toàn về **nội dung và bộ nhớ** đòi hỏi ba nguyên tắc:

- **Chuyển byte nguyên vẹn**, không decode hoặc chuyển đổi text.
- **Dùng buffer có giới hạn và quản lý trạng thái đúng**: `clear → read → flip → write until !hasRemaining`.
- **Kiểm chứng và xử lý lỗi có chủ đích**: EOF, partial write, đích đã tồn tại, file đích dở, nguồn thay đổi và kiểm tra byte-đúng-byte.

Một buffer cố định vài KiB đến vài trăm KiB, được tái sử dụng đúng cách, có thể xử lý file nhiều GiB mà không cần mảng bằng kích thước file. Kích cỡ cụ thể phải được chọn bằng phép đo và hạn mức bộ nhớ của ứng dụng.

## Tài liệu tham khảo chính thức

- Java SE 17 — `FileChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 17 — `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 — `ReadableByteChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/ReadableByteChannel.html
- Java SE 17 — `WritableByteChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/WritableByteChannel.html
- Java SE 17 — `Files` (`mismatch`, `copy`, `size`, `newInputStream`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html
- Java SE 17 — `StandardOpenOption`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html
- Java SE 17 — `InputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html
- Java SE 17 — `OutputStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-002">Chuyển văn bản giữa hai charset có kiểm tra lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển văn bản giữa hai charset có kiểm tra lỗi (Java 17+)

## 1. Bài toán và kết quả mong muốn

Một tệp văn bản chứa **bytes**, không chứa các Java `char` theo nghĩa trực tiếp. Khi di chuyển dữ liệu từ một hệ thống cũ sử dụng ISO-8859-1, Windows-1252, Shift_JIS hoặc UTF-16 sang hệ thống mới dùng UTF-8, ứng dụng cần **giải mã** byte nguồn thành các ký tự Unicode rồi **mã hóa** các ký tự đó thành bytes của charset đích.

Bài toán thực hành:

> Chuyển nội dung văn bản từ charset nguồn xác định sang charset đích xác định, nhưng **không âm thầm thay ký tự lỗi**. Nếu bytes nguồn sai cấu trúc, hoặc có ký tự không thể biểu diễn trong charset đích, phải báo lỗi và **không công bố tệp kết quả dở dang**.

Ví dụ có đầu vào ISO-8859-1:

```text
Bytes nguồn (hex):       43 61 66 E9
Charset nguồn:           ISO-8859-1
Ký tự Unicode sau decode: C a f é
Charset đích:            UTF-8
Bytes đích (hex):        43 61 66 C3 A9
```

Đây là quá trình **transcoding** chứ không phải đổi đuôi file, copy bytes nguyên trạng hay gọi `String.replace`.

Luồng đúng:

```text
                  DECODE                         ENCODE
byte[] nguồn ───────────────────▶ ký tự Unicode ─────────────────▶ byte[] đích
  ISO-8859-1        CharsetDecoder      chars       CharsetEncoder       UTF-8
                      REPORT                           REPORT
                       │                                │
                       └── lỗi malformed                └── lỗi unmappable
                           hoặc unmappable                  hoặc malformed
```

Hai vị trí kiểm tra lỗi là **độc lập**. Byte đầu vào có thể giải mã hợp lệ nhưng ký tự thu được vẫn không thể mã hóa sang charset đích; ngược lại, charset đích có thể hỗ trợ mọi ký tự hợp lệ trong khi input bytes đã hỏng trước bước mã hóa.

## 2. Các khái niệm cần phân biệt

| Thuật ngữ | Ý nghĩa | Ví dụ |
|---|---|---|
| `Charset` | Quy tắc ánh xạ giữa bytes và ký tự Unicode | `StandardCharsets.UTF_8` |
| `CharsetDecoder` | Chuyển bytes sang chars với chính sách xử lý lỗi rõ ràng | UTF-8 → Unicode |
| `CharsetEncoder` | Chuyển chars sang bytes với chính sách xử lý lỗi rõ ràng | Unicode → ISO-8859-1 |
| `CharacterCodingException` | Ngoại lệ gốc cho các lỗi mã hóa/giải mã | Cha của hai ngoại lệ dưới |
| `MalformedInputException` | Input không hợp lệ về mặt cấu trúc mã hóa | UTF-8 byte `C3 28` |
| `UnmappableCharacterException` | Một ký tự/đơn vị đầu vào không có mapping hợp lệ trong charset đích hoặc charset nguồn | Unicode `é` sang US-ASCII |
| `CodingErrorAction.REPORT` | Báo lỗi thay vì âm thầm thay/bỏ | Phù hợp dữ liệu cần chính xác |
| `CodingErrorAction.REPLACE` | Thay phần lỗi bằng chuỗi/byte thay thế của codec | Có thể làm mất thông tin |
| `CodingErrorAction.IGNORE` | Bỏ qua phần lỗi | Dễ mất dữ liệu âm thầm |

**Phân biệt lỗi của decoder và encoder:**

- **Malformed ở decoder**: byte source không tạo thành chuỗi mã hợp lệ. Ví dụ hai byte `C3 28` không phải một code point UTF-8 hợp lệ.
- **Unmappable ở decoder**: một dãy byte hợp lệ theo định dạng nguồn nhưng không có ký tự Unicode tương ứng theo mapping của charset đó; sự khác biệt tùy charset.
- **Unmappable ở encoder**: Unicode có ký tự mà bảng mã đích không thể lưu; chẳng hạn `é` hoặc `☃` vào US-ASCII.
- **Malformed ở encoder**: char sequence không hợp lệ, ví dụ một UTF-16 surrogate lẻ trong `CharBuffer`, ngay cả khi charset đích là UTF-8.

Không dùng ngoại lệ duy nhất như `IOException` rồi diễn giải mọi thứ thành “tệp hỏng”. Hãy giữ thông tin: charset nào, bước nào, mã lỗi nào, và vị trí dữ liệu nếu đã được theo dõi.

## 3. Vì sao phải khai báo charset rõ ràng?

Một file bytes không tự mang metadata chuẩn chung bảo đảm decoder nào đúng. Những câu lệnh dùng **charset mặc định** có thể tạo kết quả khác nhau theo JDK, cấu hình runtime hoặc nền tảng:

```java
// Tránh phụ thuộc default charset trong giao thức và dữ liệu lưu trữ.
new InputStreamReader(inputStream);
new OutputStreamWriter(outputStream);
```

Đây là đoạn minh họa API với stream đã được khai báo. Kể từ Java 18, default charset của JDK hướng tới UTF-8 theo JEP 400, nhưng vẫn có ngoại lệ/cấu hình; ứng dụng đọc dữ liệu lịch sử hoặc trao đổi với hệ thống khác không được trông đợi default sẽ tự nhận ra ISO-8859-1, Windows-1252 hay Shift_JIS.

**Luôn xác định nguồn gốc charset** từ contract, metadata đáng tin, user lựa chọn được kiểm chứng hoặc quy tắc giao thức.

```java
Charset source = StandardCharsets.ISO_8859_1;
Charset target = StandardCharsets.UTF_8;
```

Hai hằng số này nằm trong `java.nio.charset.StandardCharsets` và luôn được hỗ trợ trong Java SE. Nếu thực tế file nguồn dùng Windows-1252, bạn có thể chọn `Charset.forName("windows-1252")` nếu JDK/provider hỗ trợ; **không tự coi Windows-1252 và ISO-8859-1 hoàn toàn giống nhau**, đặc biệt tại các byte 0x80–0x9F.

### 3.1. Có thể nhận diện charset tự động tuyệt đối không?

Không có thuật toán tổng quát nào bảo đảm suy ra đúng charset cho **mọi** dãy bytes. Một chuỗi bytes có thể hợp lệ trong nhiều charset khác nhau nhưng sinh văn bản khác nhau.

Ví dụ byte UTF-8 của `é` là `C3 A9`. Nếu giải mã nhầm thành ISO-8859-1, chương trình nhận `Ã©`; cả hai byte đều hợp lệ trong ISO-8859-1 nên **decoder REPORT không báo lỗi**.

```text
43 61 66 C3 A9
   UTF-8      → Café
   ISO-8859-1 → CafÃ©
```

Vì vậy “không ném exception” chỉ chứng minh **input hợp lệ theo charset đã chọn**, **không chứng minh charset đó là charset gốc đúng**. Với nguồn không rõ charset, cần metadata, quy trình thẩm định hoặc xác nhận con người; heuristic chỉ cung cấp độ tin cậy có giới hạn.

## 4. Vì sao các API tiện lợi có thể che giấu dữ liệu lỗi?

Các lệnh ngắn gọn như:

```java
String text = new String(bytes, StandardCharsets.UTF_8);
byte[] converted = text.getBytes(StandardCharsets.US_ASCII);
```

là cú pháp Java hợp lệ trong method có `bytes` là `byte[]` và import `StandardCharsets`. Nhưng các convenience API kiểu này **mặc định có chính sách thay thế** đối với phần không thể giải mã/mã hóa; chúng không cung cấp kênh báo lỗi chi tiết như `CharsetDecoder` và `CharsetEncoder` với `REPORT`.

Với file cần chuyển mã **không mất thông tin**, không nên chỉ dựa vào chúng. Tương tự:

```java
Files.readString(path, StandardCharsets.UTF_8);
Files.writeString(target, text, StandardCharsets.US_ASCII);
```

Các phương thức cấp cao có hợp đồng xử lý lỗi riêng (chẳng hạn `readString` có thể báo `MalformedInputException` theo API); **không suy luận mọi tiện ích đều REPLACE**. Điểm quan trọng là phải đọc contract của **chính overload** và chọn cơ chế chủ động `REPORT` cả **đầu vào lẫn đầu ra** khi yêu cầu kiểm chứng nghiêm ngặt.

### 4.1. Ba chính sách và tác động dữ liệu

Giả sử input UTF-8 chứa `C3 28`:

- `REPORT`: decoder báo `MalformedInputException`; không tạo kết quả “sửa hộ”.
- `REPLACE`: codec dùng ký tự thay thế, thường là `U+FFFD` (ký hiệu `�`), rồi tiếp tục xử lý những byte hợp lệ khác. Nội dung đã **thay đổi**.
- `IGNORE`: codec bỏ qua phần lỗi; nội dung có thể **mất byte/ký tự**.

Chính sách nào được phép là quyết định nghiệp vụ. Với backup, dữ liệu tài chính, định danh, file nguồn dùng cho build, hoặc migration cần kiểm chứng, `REPORT` thường là mặc định hợp lý; nếu phải REPLACE/IGNORE theo yêu cầu, cần ghi lại việc mất thông tin.

## 5. `CharsetDecoder`: từ bytes sang Unicode

Tạo decoder mới với chính sách lỗi tường minh:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
```

Đây là đoạn code hợp lệ trong method, cần import `CharsetDecoder`, `CodingErrorAction` và `StandardCharsets`.

Giải mã một `ByteBuffer` đầy đủ:

```java
byte[] source = {
        0x43, 0x61, 0x66, (byte) 0xC3, (byte) 0xA9
};

CharBuffer chars = decoder.decode(ByteBuffer.wrap(source));
System.out.println(chars);
```

Đây là snippet trong method có `decoder` từ bước trước và imports `ByteBuffer`/`CharBuffer`. Kết quả hiển thị ký tự `Café` khi đầu vào đúng UTF-8. Phương thức `decode(ByteBuffer)` dạng convenience của **decoder được cấu hình** sẽ báo lỗi theo `CodingErrorAction.REPORT`.

**Ví dụ input sai:**

```java
byte[] invalid = { (byte) 0xC3, (byte) 0x28 };

try {
    decoder.reset(); // Reset nếu tái dùng sau lượt decode trước.
    decoder.decode(ByteBuffer.wrap(invalid));
} catch (MalformedInputException ex) {
    System.out.println("UTF-8 malformed");
}
```

Đây là snippet trong method có decoder phù hợp, cần import `MalformedInputException`. Với chương trình production, **tạo decoder mới cho mỗi operation** thường đơn giản hơn việc chia sẻ/tái dùng decoder có state; không dùng cùng `CharsetDecoder` đồng thời trên nhiều thread.

### 5.1. Decoder có trạng thái

Một số charset có chuyển trạng thái (stateful), và ngay cả UTF-8 có thể có chuỗi nhiều byte bị chia qua ranh giới I/O. Vì vậy một decoder xử lý stream phải giữ được trạng thái giữa các chunk:

```text
Chunk 1: ... E2 82
Chunk 2: AC ...
                └─ ba byte E2 82 AC tạo ký tự €
```

Nếu giải mã hai chunk UTF-8 **độc lập** với hai decoder mới, chunk 1 có thể bị báo malformed/truncated còn chunk 2 cũng hỏng. Không được chia file tùy tiện thành byte chunks rồi gọi `new String(chunk, UTF_8)` theo từng chunk khi cần giữ nguyên văn bản.

`InputStreamReader` được xây trên `CharsetDecoder` xử lý việc tiếp tục đọc và lưu trạng thái decoder qua các byte chunks. Đây thường là lựa chọn đơn giản hơn việc tự quản lý `CoderResult`/`ByteBuffer.compact` cho bài thực hành streaming.

## 6. `CharsetEncoder`: từ Unicode sang charset đích

Đầu ra cũng có lỗi riêng. Ví dụ muốn mã hóa một `String` thành ISO-8859-1:

```java
CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

CharBuffer chars = CharBuffer.wrap("Caf\u00E9");
ByteBuffer bytes = encoder.encode(chars);
```

Đây là snippet trong method có import `CharsetEncoder`, `CharBuffer`, `ByteBuffer`; Java xử lý escape `\u00E9` như ký tự é.

Trường hợp đích không biểu diễn được ký tự:

```java
CharsetEncoder ascii = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try {
    ascii.encode(CharBuffer.wrap("Caf\u00E9"));
} catch (UnmappableCharacterException ex) {
    System.out.println("ASCII unmappable");
}
```

Với Java 17+, kết quả lỗi đúng là `UnmappableCharacterException`, vì US-ASCII không mã hóa được `é`.

**Quy tắc:** nguồn đọc được hợp lệ **không** có nghĩa mọi ký tự có thể đưa tới đích. UTF-8 có thể mã hóa mọi Unicode scalar value hợp lệ; các charset khác thường có tập ký tự hẹp hơn. Chuyển từ UTF-8 sang US-ASCII hoặc ISO-8859-1 có khả năng mất thông tin dù input hoàn toàn tốt.

### 6.1. `canEncode` có thay thế error handling không?

`encoder.canEncode(...)` có thể trả lời liệu một ký tự hoặc chuỗi có thể được mã hóa với encoder tương ứng. Nhưng nếu convert file rất lớn, **không cần đọc toàn bộ file thành String** chỉ để kiểm tra canEncode trước. Một `CharsetEncoder` cấu hình `REPORT` ở luồng ghi sẽ phát hiện lỗi khi encoder thực sự xử lý input.

Ngoài ra, giữa bước precheck và bước encode, nếu dữ liệu nguồn bị thay đổi, kết quả precheck không còn đáng tin. Cách đơn giản, chắc chắn là xử lý đúng **một chuỗi chuyển mã**, bắt và báo lỗi ở nơi xảy ra, chỉ công bố bản đích sau khi toàn bộ decode/encode thành công.

## 7. Từ demo nhỏ tới chuyển mã file lớn

Có hai chiến lược chính:

| Chiến lược | Ưu điểm | Giới hạn |
|---|---|---|
| `Files.readAllBytes` → `decoder.decode` → `encoder.encode` → `Files.write` | Rõ từng bước, dễ học về lỗi, phù hợp payload nhỏ đã giới hạn | Cần giữ toàn bộ bytes/chars; không hợp file lớn |
| `InputStreamReader` dùng decoder `REPORT` + `OutputStreamWriter` dùng encoder `REPORT` | Xử lý streaming bằng mảng char cố định, không cần giữ toàn file | Phải quản lý lifecycle/error/staging; báo offset lỗi chi tiết cần bộ đếm/decoder riêng |

**Ví dụ bản đơn giản cho dữ liệu nhỏ:**

```java
byte[] sourceBytes = Files.readAllBytes(sourcePath);

CharsetDecoder decoder = sourceCharset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

CharsetEncoder encoder = targetCharset.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

CharBuffer characters = decoder.decode(
        ByteBuffer.wrap(sourceBytes));

ByteBuffer encoded = encoder.encode(characters);

byte[] result = new byte[encoded.remaining()];
encoded.get(result);

Files.write(targetPath, result);
```

Đoạn này **chỉ là minh họa trong method** với imports tương ứng, Paths/Charsets đã khai báo và checked exceptions được xử lý. Nó không an toàn cho file tùy ý lớn; cũng **không bảo đảm** target không bị ghi dở nếu lỗi xuất hiện khi `Files.write`. Muốn giữ file đích cũ và không công bố output lỗi, dùng staging như ví dụ đầy đủ bên dưới.

## 8. Ví dụ thực hành hoàn chỉnh: stream + strict decode/encode + staging

Ứng dụng dưới đây chuyển từ **ISO-8859-1 sang UTF-8**, sau đó kiểm tra riêng hai nhánh lỗi:

1. File nguồn UTF-8 malformed: chuyển sang UTF-8 bị **từ chối**.
2. File nguồn UTF-8 hợp lệ nhưng chứa ký tự không có trong US-ASCII: mã hóa ra US-ASCII bị **từ chối**.

Mỗi lần chuyển mã:

- Tạo `CharsetDecoder` và `CharsetEncoder` mới với cả hai lỗi `REPORT`.
- Dùng `InputStreamReader`/`OutputStreamWriter` cho **streaming**.
- Đọc/ghi theo khối `char[]`, không nạp toàn file.
- Ghi vào **tệp staging trong cùng thư mục đích**.
- Chỉ `Files.move(staging, target)` **sau khi** đóng thành công các stream và toàn bộ decoder/encoder không báo lỗi.
- Xóa staging khi conversion thất bại; đích chưa được tạo sẽ không xuất hiện.

**Tên tệp:** `StrictTranscodeDemo.java`

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class StrictTranscodeDemo {

    public static void convert(
            Path source,
            Path target,
            Charset inputCharset,
            Charset outputCharset) throws IOException {

        CharsetDecoder decoder = inputCharset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        CharsetEncoder encoder = outputCharset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        // Đặt staging tại parent đích để tránh tạo output dở dang
        // dưới tên target khi decode/encode thất bại.
        Path parent = target.toAbsolutePath().getParent();
        Path staging = Files.createTempFile(
                parent, ".transcoding-", ".tmp");

        boolean published = false;

        try {
            try (Reader reader = new BufferedReader(
                        new InputStreamReader(
                                Files.newInputStream(source), decoder));
                 Writer writer = new BufferedWriter(
                        new OutputStreamWriter(
                                Files.newOutputStream(
                                        staging,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.TRUNCATE_EXISTING),
                                encoder))) {

                char[] chunk = new char[4096];
                int n;

                while ((n = reader.read(chunk)) != -1) {
                    writer.write(chunk, 0, n);
                }
            }

            // Chỉ publish sau khi cả reader/writer đã close thành công.
            // Ở demo này target chưa tồn tại; move không có option
            // không phải bảo đảm atomic replacement/durability.
            Files.move(staging, target);
            published = true;

        } finally {
            if (!published) {
                Files.deleteIfExists(staging);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("transcode-");

        try {
            Path latin1 = directory.resolve("latin1.txt");
            Path utf8 = directory.resolve("utf8.txt");

            Files.write(
                    latin1,
                    "Caf\u00E9".getBytes(StandardCharsets.ISO_8859_1));

            convert(
                    latin1, utf8,
                    StandardCharsets.ISO_8859_1,
                    StandardCharsets.UTF_8);

            System.out.println(
                    "converted exact: "
                    + Files.readString(
                            utf8, StandardCharsets.UTF_8)
                            .equals("Caf\u00E9"));

            System.out.println(
                    "input bytes: " + Files.size(latin1));
            System.out.println(
                    "output bytes: " + Files.size(utf8));

            Path invalid = directory.resolve("invalid.bin");
            Path invalidTarget = directory.resolve("invalid-out.txt");

            // C3 28: dãy UTF-8 malformed.
            Files.write(invalid, new byte[]{
                    (byte) 0xC3, (byte) 0x28
            });

            try {
                convert(
                        invalid, invalidTarget,
                        StandardCharsets.UTF_8,
                        StandardCharsets.UTF_8);
            } catch (MalformedInputException ex) {
                System.out.println(
                        "malformed input: "
                        + ex.getClass().getSimpleName());
            }

            System.out.println(
                    "invalid target exists: "
                    + Files.exists(invalidTarget));

            Path unicode = directory.resolve("unicode.txt");
            Path asciiTarget = directory.resolve("ascii.txt");

            Files.writeString(
                    unicode, "Caf\u00E9 \u2603",
                    StandardCharsets.UTF_8);

            try {
                convert(
                        unicode, asciiTarget,
                        StandardCharsets.UTF_8,
                        StandardCharsets.US_ASCII);
            } catch (UnmappableCharacterException ex) {
                System.out.println(
                        "unmappable output: "
                        + ex.getClass().getSimpleName());
            }

            System.out.println(
                    "unmappable target exists: "
                    + Files.exists(asciiTarget));

        } finally {
            try (Stream<Path> files = Files.list(directory)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch/chạy trên JDK 17+:

```bash
javac StrictTranscodeDemo.java
java StrictTranscodeDemo
```

**Stdout kỳ vọng (đã kiểm chứng bằng JDK):**

```text
converted exact: true
input bytes: 4
output bytes: 5
malformed input: MalformedInputException
invalid target exists: false
unmappable output: UnmappableCharacterException
unmappable target exists: false
```

### 8.1. Giải thích từng bước

1. `latin1.txt` được ghi bằng `ISO_8859_1`, chứa byte `E9` cho ký tự `é`; kích thước 4 byte.
2. `InputStreamReader` nhận `CharsetDecoder` đã bật `REPORT` và đọc thành ký tự Unicode. `OutputStreamWriter` nhận `CharsetEncoder` cũng bật `REPORT`, ghi theo UTF-8: `é` thành hai byte `C3 A9`, tổng 5 byte.
3. Code dùng buffer `char[4096]` nhưng vẫn xử lý được ký tự nhiều byte vì **Reader/Writer giữ trạng thái mã hóa qua các lần read/write**. Không tạo decoder mới cho từng chunk.
4. Nếu bytes đầu vào hỏng, `InputStreamReader` ném `MalformedInputException` trong lúc đọc. Khối try-with-resources đóng tài nguyên; vì move chưa chạy nên target không xuất hiện.
5. Với input Unicode có `é`/`☃` nhưng target `US_ASCII`, encoder `REPORT` ném `UnmappableCharacterException` trong lúc ghi (hoặc khi flush/close).
6. Biến `published` giúp `finally` xóa staging trong mọi nhánh lỗi trước publish. Nếu đã move thành công, staging không còn tại đường dẫn cũ.
7. `Files.move(staging,target)` không có `REPLACE_EXISTING`; demo dùng target chưa tồn tại. **Không** gọi hành vi đó là “atomic commit” hay “durable commit” trên mọi OS.
8. Bản demo dọn file trong thư mục riêng do chính nó tạo, không sửa tệp người dùng. Nếu filesystem bất thường gây lỗi cleanup, exception được báo; demo không giấu thất bại.

### 8.2. Vì sao có escape `\u00E9` và `\u2603` trong mã nguồn?

Đó là cú pháp Unicode escape của Java, lần lượt cho `é` và `☃`, giúp file nguồn của ví dụ có thể lưu dưới ASCII khi môi trường terminal/editor có charset khác. Chương trình không “ép ký tự thành ASCII”; các escape được Java compiler xử lý thành đúng ký tự Unicode trước khi chương trình chạy.

### 8.3. Điều ví dụ **không** bảo đảm

- Không có xác thực nguồn tệp hay giới hạn tổng số byte/chars của input.
- Không ghi checksum hoặc manifest nội dung.
- Không bảo đảm source không bị tác nhân khác thay đổi trong lúc đọc.
- Không tự có cơ chế giao dịch/độ bền sau mất điện.
- Không thay thế file target đã tồn tại một cách nguyên tử.
- Không báo chính xác **byte offset** của ký tự lỗi trong file; exception cho biết loại lỗi, muốn offset cần bộ đếm bytes hoặc xử lý trực tiếp `CoderResult`.
- Không kiểm soát symlink/path races nếu path do người dùng không tin cậy cung cấp.

Đây là một ví dụ **chuyển mã nghiêm ngặt và ngăn công bố output dở dang do lỗi mã hóa**, không phải một công cụ ETL production đầy đủ.

## 9. Chuyển file thật từ command line

Khi áp dụng vào bài toán thực tế, nên thiết kế chương trình nhận:

```text
transcode <sourcePath> <targetPath> <sourceCharset> <targetCharset>
```

Ở tầng CLI cần:

- Parse và xác nhận đúng số tham số.
- Dùng `Charset.forName(name)` cùng xử lý `IllegalCharsetNameException`/`UnsupportedCharsetException` hoặc `IllegalArgumentException` phù hợp.
- Không tự chọn charset nguồn nếu nghiệp vụ đã định rõ.
- Kiểm tra source là tệp thường, đường dẫn target thuộc khu vực được phép, không tự cho phép ghi đè nhầm.
- Gọi logic `convert` trong ví dụ sau khi đã validate.
- In thông báo lỗi rõ: input malformed, output unmappable, permissions, hết dung lượng, target tồn tại hoặc đường dẫn không hợp lệ.
- Trả exit code thất bại và không báo kết quả đã công bố nếu `Files.move` chưa xong.

### 9.1. Helper xử lý charset names

```java
static Charset parseCharset(String name) {
    try {
        return Charset.forName(name);
    } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
                "Unsupported or illegal charset: " + name, ex);
    }
}
```

Đây là một helper trong class có `import java.nio.charset.Charset`. Trong chương trình CLI thực, thông báo lỗi không nên ghép những input tùy ý vào log nhạy cảm mà không xử lý; helper dùng để minh họa cách fail-fast với charset không hỗ trợ.

Một số tên hợp lệ ví dụ `UTF-8`, `ISO-8859-1`, `US-ASCII`, `UTF-16LE`; với Windows-1252 có thể dùng `Charset.forName("windows-1252")` khi provider của runtime hỗ trợ. Một tên charset **hợp lệ** không chứng minh tệp đó thật sự dùng charset ấy.

## 10. UTF-8 và UTF-16: BOM, byte order và lựa chọn charset

### 10.1. Byte Order Mark (BOM) là gì?

Một số tệp văn bản mở đầu bằng dấu BOM:

| Encoding thường gặp | BOM bytes (hex) |
|---|---|
| UTF-8 | `EF BB BF` |
| UTF-16BE | `FE FF` |
| UTF-16LE | `FF FE` |
| UTF-32BE | `00 00 FE FF` |
| UTF-32LE | `FF FE 00 00` |

BOM có thể cung cấp dấu hiệu về mã hóa hoặc thứ tự byte, nhưng **không phải mọi file đều có BOM**, và không được dựa vào BOM để kết luận encoding chắc chắn nếu file từ nguồn không đáng tin.

### 10.2. `UTF_16` khác `UTF_16LE` và `UTF_16BE`

- `StandardCharsets.UTF_16` có semantics xử lý BOM/byte-order theo contract Java. Khi encode, nó có thể phát sinh BOM theo quy tắc charset; khi decode, nó có thể dùng BOM để xác định byte order, còn mặc định nếu thiếu BOM theo quy tắc riêng.
- `StandardCharsets.UTF_16LE` và `UTF_16BE` cố định byte order. Không tự biến file thành UTF-8 hoặc tự suy đoán từ BOM tùy ý.
- Nếu protocol yêu cầu UTF-16LE **không BOM**, chọn đúng charset và định nghĩa BOM policy.

Không trộn bytes ở UTF-16LE với decoder UTF-16BE chỉ vì cùng có “UTF-16” trong tên. Kết quả có thể thành ký tự sai hoặc báo malformed.

### 10.3. UTF-8 BOM có thể trở thành U+FEFF

Nếu stream UTF-8 có đầu `EF BB BF`, nhiều decoder UTF-8 sẽ thể hiện nó thành ký tự Unicode `U+FEFF` trong dữ liệu. Trong Java, `StandardCharsets.UTF_8` **không được coi là BOM-stripping API tổng quát**.

Nếu muốn loại UTF-8 BOM của file đầu vào, hãy định nghĩa điều đó trong format contract và chỉ xử lý ở **đầu stream**, trước khi dùng text. Không xóa mọi ký tự `\uFEFF` ở bất kỳ vị trí nào vì có thể làm thay đổi dữ liệu hợp lệ theo định dạng của ứng dụng.

### 10.4. Newline cũng cần chính sách riêng

Trong quá trình chuyển charset, chương trình có thể cần giữ nguyên `\r\n`, `\n` hay `\r`. Ví dụ `StrictTranscodeDemo` dùng Reader/Writer với `read(char[])` và `write(char[],0,n)`, **không dùng `readLine()`**, nên nó không tự chuyển mọi xuống dòng thành separator của OS.

Nếu dùng `BufferedReader.readLine()` rồi `BufferedWriter.newLine()`, bạn đang thực hiện thêm **chuyển đổi newline**, có thể làm file output khác về mặt ký tự ngay cả khi encoding hợp lệ.

## 11. Một Unicode character có thể dài bao nhiêu?

Cần phân biệt:

- **Byte**: đơn vị lưu của charset.
- **UTF-16 code unit (`char`)**: Java `char` là 16 bit, không phải luôn một ký tự Unicode hoàn chỉnh.
- **Unicode code point**: giá trị Unicode, một code point ngoài BMP có thể dùng **hai char** (surrogate pair).
- **Grapheme cluster**: ký tự nhìn thấy trên màn hình có thể bao gồm nhiều code points, chẳng hạn chữ + dấu kết hợp.

Ví dụ:

```java
String snowman = "\u2603";
String emoji = "\uD83D\uDE00";

System.out.println(snowman.length()); // 1 UTF-16 code unit.
System.out.println(emoji.length());   // 2 UTF-16 code units.
```

Đoạn này là snippet có thể chạy trong method; ký tự `☃` ở BMP dùng một char, emoji 😀 ngoài BMP dùng hai char.

### 11.1. Vì sao ranh giới chunk có thể cắt giữa surrogate pair?

Một lần `Reader.read(char[])` có thể kết thúc ngay sau high surrogate của một code point ngoài BMP. `Writer`/`CharsetEncoder` giữ state phù hợp để xử lý phần char còn lại khi có lần `write` kế tiếp. Tuy nhiên, **nếu bạn tự gọi `CharsetEncoder.encode(CharBuffer)` độc lập trên từng chunk**, chunk kết thúc bằng high surrogate lẻ có thể bị báo malformed dù ký tự hoàn chỉnh trải sang chunk tiếp theo.

Do đó:

- Dùng **một** pipeline Reader/Writer tồn tại xuyên các chunk, hoặc
- Tự triển khai state machine của `CharsetDecoder.decode`/`CharsetEncoder.encode` với `endOfInput` và `flush` đúng API.

Không “sửa lỗi” bằng cách thay high surrogate cuối chunk thành dấu hỏi — đó là mất dữ liệu do ứng dụng tự gây ra.

## 12. Khi nào cần quản lý `CoderResult` thủ công?

Ở mức nâng cao, bạn có thể gọi:

```java
CoderResult result = decoder.decode(
        inputBytes, outputChars, false);
```

Đây là đoạn minh họa: `decoder` đã tạo, `inputBytes` là ByteBuffer và `outputChars` là CharBuffer; cần import `CoderResult`.

`CoderResult` có các tình huống:

- `UNDERFLOW`: decoder cần thêm input hoặc đã xử lý hết input hiện tại; nếu cuối stream, phải xử lý end-of-input đúng contract.
- `OVERFLOW`: CharBuffer output không còn chỗ; cần flip/consume rồi tiếp tục.
- `isMalformed()`: dãy bytes không hợp lệ.
- `isUnmappable()`: input hợp lệ nhưng không có character mapping.
- `throwException()`: chuyển kết quả lỗi thành ngoại lệ `CharacterCodingException` phù hợp.

Khi dùng `REPORT` và API streaming low-level, bạn có thể theo dõi `inputBytes.position()` để tính vị trí byte gây lỗi tương đối trong cửa sổ, nhưng cần cộng **offset file** và giữ các byte chưa xử lý giữa chunks. Đây là con đường tốt khi hệ thống cần log “UTF-8 malformed tại byte offset 1.234.567” hoặc xử lý dữ liệu rất lớn với diagnostics chuyên sâu.

### 12.1. State machine đúng ở cuối input

Một chu kỳ decoder low-level thường cần:

1. `decode(in,out,false)` cho các chunk trước cuối.
2. `compact()` các byte chưa xử lý, đọc thêm bytes vào phần buffer còn chỗ.
3. Khi EOF: `decode(in,out,true)` để xác nhận input kết thúc thật.
4. `flush(out)` cho phần trạng thái còn lại.
5. Xử lý `OVERFLOW` bằng cách consume output và gọi lại.
6. Khi tạo encoder low-level, cũng thực hiện chu kỳ `encode(...,endOfInput)` và `flush` tương ứng.

Nếu bỏ `endOfInput=true` ở EOF, một chuỗi UTF-8 thiếu byte cuối có thể không được đánh giá chính xác là malformed. Nếu quên preserve bytes underflow, ký tự chia giữa hai lần đọc có thể bị lỗi giả hoặc bị thay thế.

Với mục tiêu bài thực hành thông thường, `InputStreamReader`/`OutputStreamWriter` cung cấp state machine này và ít rủi ro hơn tự viết decoder loop.

## 13. Xử lý lỗi với độ chính xác phù hợp

### 13.1. Exception nào thường xuất hiện?

| Bước | Dấu hiệu | Ví dụ |
|---|---|---|
| Input decoder | `MalformedInputException` | UTF-8 `C3 28` |
| Input decoder | `UnmappableCharacterException` | Charset có byte sequence hợp lệ nhưng không map sang Unicode |
| Output encoder | `UnmappableCharacterException` | `é` → US-ASCII |
| Output encoder | `MalformedInputException` | Unpaired UTF-16 surrogate |
| I/O của source | `NoSuchFileException`, `AccessDeniedException`, `IOException` | Không mở được file |
| I/O của target/staging | `IOException` | Hết dung lượng, thiếu quyền, filesystem lỗi |
| Publish staging | `FileAlreadyExistsException` hoặc `IOException` | Target tồn tại hoặc không move được |

**Không** giả định mọi `MalformedInputException` là “file đang dùng charset khác” — có thể nguồn thực sự hỏng, thiếu bytes do truyền bị cắt, hoặc ứng dụng chọn charset sai.

### 13.2. Có nên thử charset khác sau lỗi?

Nếu format nguồn đã khai báo UTF-8, input malformed nên được **từ chối** hoặc chuyển vào hàng xử lý lỗi, không tự đoán một charset khác rồi đọc tiếp.

Chỉ dùng fallback khi **nghiệp vụ quy định** một tập charset được phép và có cách phân biệt kết quả hợp lệ. Ví dụ hệ thống cũ cho phép Windows-1252/ISO-8859-1 với metadata nguồn; code cần ghi nhận charset đã áp dụng và các trường hợp mơ hồ.

Không tự thay mọi byte malformed bằng `?` rồi tuyên bố convert thành công trọn vẹn, vì dữ liệu sẽ không còn phản ánh bản gốc.

### 13.3. Nếu cần chính sách thay thế có kiểm soát

Một số ứng dụng trình bày log có thể chấp nhận replacement. Ví dụ:

```java
CharsetDecoder permissive = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith("\uFFFD");
```

Đây là snippet trong method, với các import tương ứng. Với encoder, replacement bytes phải hợp lệ trong charset đích và thường sử dụng byte sequence thay thế mặc định; chọn `?` có thể được chấp nhận trong US-ASCII, nhưng không thể coi đó là biểu diễn gốc của Unicode không hỗ trợ.

Nếu chọn REPLACE, nên đo số trường hợp bị thay hoặc ghi nhận ở tầng nghiệp vụ, bởi `CodingErrorAction.REPLACE` **không tự cung cấp báo cáo từng vị trí lỗi**.

## 14. Tính nguyên vẹn dữ liệu: phải xác minh điều gì?

Một bản chuyển mã **đúng** không có nghĩa bytes trước/sau giống nhau. Ngược lại, bytes thường khác ngay cả với cùng văn bản.

| Kiểm tra | Điều xác nhận | Điều không xác nhận |
|---|---|---|
| Không exception từ decoder `REPORT` | Input hợp lệ theo charset nguồn đã chọn | Charset nguồn thật sự đúng về ngữ nghĩa |
| Không exception từ encoder `REPORT` | Unicode đã decode có thể biểu diễn bởi charset đích | Nội dung nghiệp vụ hợp lệ |
| Decode output bằng charset đích | Text đầu ra có thể được đọc lại | Có đảm bảo byte-for-byte giống nguồn không |
| So sánh Unicode nguồn sau decode với Unicode output sau decode | Mức giữ nguyên ký tự theo chính sách | Normalization/newline/metadata của file có thể khác |
| So sánh bytes đầu vào/đầu ra | Chỉ phù hợp nếu cùng encoding và không biến đổi | Không dùng cho transcoding giữa charset khác nhau |
| Checksum bytes | Phát hiện byte khác khi có mẫu chuẩn | Không chứng minh nguồn bytes được giải mã với đúng charset |

Với migration quan trọng, có thể ghi manifest gồm source charset, target charset, size, checksum SHA-256, số record, thời gian và kết quả validation. Nếu nghiệp vụ cho phép normalization thì phải áp dụng quy tắc Unicode normalization minh bạch, không tự diễn ra khi chỉ thay encoding.

### 14.1. Round trip để kiểm tra

Mô hình test:

```text
sourceBytes --decode(sourceCharset)--> characters
characters --encode(targetCharset)--> targetBytes
targetBytes --decode(targetCharset)--> restoredCharacters
assert characters.equals(restoredCharacters)
```

Đây là test tốt khi cả hai encoder/decoder là nghiêm ngặt, input không bị sửa đồng thời và không có transformation khác. Tuy nhiên một round trip đúng **không bảo đảm** charset nguồn đã chọn đúng về mặt lịch sử (một chuỗi bị mojibake vẫn có thể round trip như chính nó).

### 14.2. Chuyển đổi văn bản khác sao chép binary

Không dùng transcoding với ảnh, ZIP, PDF, class file hoặc byte stream bất kỳ chỉ vì muốn đổi đuôi. CharsetDecoder giải thích bytes như ký tự; dữ liệu binary có thể chứa byte sequences bất hợp lệ và bị lỗi hoặc bị biến dạng.

Nếu mục tiêu là giữ nguyên bytes, chọn `Files.copy` hoặc byte streams/FileChannel; nếu mục tiêu là giữ nguyên **ký tự** mà đổi byte encoding, dùng decoder/encoder.

## 15. Memory và performance khi chuyển file lớn

### 15.1. Chi phí theo kích thước file

- `Files.readAllBytes` làm bộ nhớ tạm tăng theo kích thước file, cộng CharBuffer và ByteBuffer đích có thể lớn.
- Pipeline `Reader`/`Writer` với `char[4096]` giữ lượng buffer ứng dụng gần như cố định, không phụ thuộc tổng số ký tự trong file.
- Output byte size có thể **nhỏ hơn hoặc lớn hơn** source: `é` là một byte Latin-1 nhưng hai byte UTF-8; ngược lại, những case chuyển UTF-16LE sang UTF-8 có tỷ lệ khác.
- `BufferedReader`/`BufferedWriter` có thêm char buffers nội bộ; có thể tăng kích thước chunk nếu benchmark cho thấy lợi ích, nhưng không coi càng to càng nhanh.
- Việc encode/decode tiêu thụ CPU, đặc biệt khi phải kiểm tra malformed input; throughput còn tùy disk/network và loại charset.

### 15.2. Nếu file chỉ có một dòng cực dài?

Ví dụ dùng `char[]` đọc theo chunk, **không gọi `readLine()`**, nên một dòng dài hàng trăm MB không tạo một `String` khổng lồ chỉ vì không có newline. Đó là lợi thế cho file lớn có dòng không giới hạn.

Nếu ứng dụng phải parse records sau transcoding, cần tự đặt record length limit và xử lý phân cách dòng/CSV quoting theo format, không trông cậy vào việc chuyển charset tự giới hạn record size.

### 15.3. Theo dõi progress khác độ chính xác của byte offset

Khi dùng `Reader`, số phần tử `n` trả về là **UTF-16 char units**, không phải số byte nguồn đã đọc. Với UTF-8, không thể cộng số char để biết exact byte offset khi gặp lỗi.

Nếu cần tiến độ theo byte, có thể wrap `InputStream` bằng một counter; nhưng do buffering/read-ahead, số byte stream đã lấy không nhất thiết trùng **byte đang được decoder xử lý** tại thời điểm exception. Muốn offset lỗi chính xác, phải quản lý ByteBuffer position/CoderResult và phần bytes chưa xử lý, hoặc thiết kế một decoder có tracking riêng.

## 16. Công bố file đích an toàn và xử lý lỗi

### 16.1. Vì sao không ghi thẳng target quan trọng?

Nếu mở target với `TRUNCATE_EXISTING` rồi bắt đầu viết, mà nửa chừng decoder phát hiện UTF-8 sai, file đích đã bị cắt ngắn hoặc chứa nửa văn bản hợp lệ. Một file migration thất bại có thể làm hỏng bản gốc/đích quan trọng.

```text
Ghi thẳng:
target (bản cũ) → truncate → viết 50% → MALFORMED → target bị dở dang

Ghi staging:
target (bản cũ) ─────────── giữ nguyên trong lúc convert
                       staging (bản mới) → lỗi → xóa staging
                       hoặc
                       staging hoàn chỉnh → publish theo policy
```

**Staging** giúp không đặt tên kết quả cuối lên nội dung đang convert, nhưng bản thân nó không tự giải quyết **atomicity, concurrency hoặc durability**. Cần chính sách riêng cho overwrite, rename, sync file và sync directory nếu ứng dụng yêu cầu.

### 16.2. Tệp đích đã tồn tại thì sao?

Ví dụ `StrictTranscodeDemo` dùng `Files.move(staging,target)` không có `REPLACE_EXISTING` và tạo các tên target mới. Trong ứng dụng thật:

- Nếu **cấm ghi đè**, dùng chính sách create/move phù hợp và xử lý `FileAlreadyExistsException`. Đừng tự chạy `Files.delete(target)` trước rồi move, vì có khoảng thời gian mất target.
- Nếu **được thay thế**, cân nhắc `Files.move(staging,target,REPLACE_EXISTING)` nhưng **không** được bảo đảm move là atomic.
- Nếu cần **atomic replacement**, có thể thử `Files.move(staging,target,ATOMIC_MOVE)` trên provider hỗ trợ; khi chỉ định `ATOMIC_MOVE`, Java bỏ qua các option khác và với target đã tồn tại, chính sách replace có thể **implementation-specific**.
- Nếu cần **crash durability**, sau khi hoàn tất nội dung staging còn cần sync/force tệp và có thể sync directory theo hợp đồng OS trước khi tuyên bố committed; `Files.move` không phải fsync.
- Nếu có nhiều process ghi cùng đích, phải có ownership/locking/versioning; check `Files.exists` riêng lẻ có race.

### 16.3. Cleanup không được xóa đích hợp lệ

Khi convert thất bại, chỉ xóa staging do operation tạo, không xóa target đang tồn tại của phiên bản trước. Khi publish thành công, đổi trạng thái `published` rồi không cố xóa staging như thể nó còn ở tên cũ.

Trong ứng dụng production, nếu cleanup staging cũng thất bại, cần log và lên lịch dọn file tồn đọng. Java try-with-resources có thể giữ lỗi close dưới dạng suppressed exception; khi xử lý, đừng đánh mất exception gốc của decoder.

## 17. Kiểm thử các trường hợp quan trọng

Một bộ test hữu ích nên có:

| Input / tình huống | Source → Target | Mong đợi |
|---|---|---|
| ASCII thuần | UTF-8 → UTF-8 | Bytes và characters giống nhau |
| `é` | ISO-8859-1 → UTF-8 | Byte `E9` thành `C3 A9` |
| Tiếng Việt có dấu | UTF-8 → UTF-16LE | Giữ Unicode sau decode lại |
| Emoji ngoài BMP | UTF-8 → UTF-8 | Không mất surrogate pair dù split chunk |
| UTF-8 `C3 28` | UTF-8 → UTF-8, REPORT | `MalformedInputException` |
| UTF-8 có dãy multi-byte thiếu phần cuối | UTF-8 → UTF-8, REPORT | `MalformedInputException` tại EOF |
| `é` | UTF-8 → US-ASCII, REPORT | `UnmappableCharacterException` |
| String chứa surrogate lẻ (đầu vào char tùy tạo) | Unicode → UTF-8, REPORT | `MalformedInputException` ở encoder |
| UTF-8 bytes nhưng khai nhầm Latin-1 | Latin-1 → UTF-8 | Có thể **không lỗi** nhưng cho mojibake |
| File rỗng | Charset A → Charset B | Tệp đích rỗng theo contract; xem BOM nếu charset đích phát sinh BOM |
| Target đã tồn tại | Không cho phép replace | Phải báo lỗi và giữ đích |
| Disk full/permission | Bất kỳ | Không công bố staging hỏng |
| Input lớn | UTF-8 → UTF-16 | Bộ nhớ không tăng theo kích thước file toàn bộ |
| Source bị thay đổi khi đọc | Bất kỳ | Cần test theo snapshot/ownership policy |
| Source có BOM | UTF-8/UTF-16 | Xử lý theo format contract đã chọn |

### 17.1. Test tách đôi UTF-8 multi-byte

Chuẩn bị chuỗi có emoji hoặc `€` tại các vị trí gần ranh giới của buffer đọc. Chuyển bằng Reader/Writer theo chunk nhỏ và so sánh Unicode sau khi đọc lại. Nếu tự decode **từng mảng bytes độc lập**, test đó sẽ bộc lộ lỗi ở chỗ code point bị chia qua hai lượt I/O.

### 17.2. Test lỗi sau khi đã ghi một phần staging

Đặt một ký tự không thể mã hóa ở gần cuối tệp (ví dụ hơn 10 MB ASCII hợp lệ rồi `☃` và đích US-ASCII). Encoder có thể đã ghi rất nhiều byte staging trước khi nhận ra lỗi. Yêu cầu test:

- Method phải ném exception.
- **Target** chưa có hoặc vẫn là bản cũ.
- Staging của operation được dọn hoặc ghi nhận rõ nếu cleanup thất bại.
- Không có log “SUCCESS/COMMITTED”.
- Không tự retry với `REPLACE`/`IGNORE` nếu policy là strict.

## 18. Những lỗi thường gặp

1. **Đổi tên `.txt` thành `.utf8` và coi đã chuyển charset.** Không thay bytes.
2. **Copy bytes nguyên trạng bằng `Files.copy` dù nguồn/đích có charset khác.** Đó là binary copy, không transcoding.
3. **Dùng charset mặc định của hệ thống** thay vì charset đã quy định.
4. **Nhầm ISO-8859-1 với Windows-1252** ở vùng byte có khác biệt.
5. **Cho rằng detector luôn biết đúng charset nguồn.** Byte sequences có thể hợp lệ trong nhiều bảng mã.
6. **Dùng `new String(bytes, charset)` rồi `getBytes(other)` và kỳ vọng tự báo mọi lỗi.** Nhiều convenience overload thay ký tự không hợp lệ.
7. **Chỉ đặt `REPORT` ở decoder mà quên encoder.** Ký tự không biểu diễn được ở đích vẫn có nguy cơ bị thay.
8. **Chỉ đặt `REPORT` ở encoder mà quên decoder.** Dữ liệu nguồn malformed có thể bị mất từ bước đọc.
9. **Dùng `IGNORE` để “vượt lỗi”** khi yêu cầu chính xác dữ liệu.
10. **Dùng `REPLACE` rồi ghi log “100% không mất ký tự”.** Replacement chính là sự thay đổi dữ liệu.
11. **Giải mã mỗi byte chunk thành một String riêng** làm đứt UTF-8 multi-byte sequence.
12. **Mã hóa từng char[] chunk bằng encoder mới**, làm hỏng surrogate pair nằm qua ranh giới.
13. **Không gọi end-of-input/flush ở low-level decoder** khiến EOF malformed không được phát hiện đúng.
14. **Quên `ByteBuffer.flip()`/`compact()` khi tự triển khai `CoderResult` loop.**
15. **Đếm byte từ `char[].length`.** UTF-8 có số byte khác UTF-16 code units.
16. **Cho rằng `String.length()` là số Unicode code points hoặc ký tự nhìn thấy.** Không đúng cho surrogate pairs/combining marks.
17. **Cho rằng UTF-8 BOM luôn tự bị strip.** Phải định nghĩa BOM policy riêng.
18. **Dùng `UTF_16LE` khi file thật là UTF-16BE.** Sai byte order.
19. **Sử dụng `readLine`/`newLine` trong pipeline muốn bảo toàn chính xác newline.** Có thể chuẩn hóa CRLF/LF ngoài ý muốn.
20. **Ghi trực tiếp target rồi khi decode lỗi vẫn để target dở dang.** Dùng staging và công bố sau thành công.
21. **Xóa target cũ trước khi convert.** Nếu conversion lỗi thì mất phiên bản hợp lệ.
22. **Cho rằng `Files.move` không option là atomic replacement/durable.** Không được API cam kết.
23. **Không kiểm tra target đã tồn tại hoặc bị ghi concurrent.** Có race và rủi ro overwrite.
24. **Không đóng Writer khi có exception.** State encoder/các byte đã buffer có thể chưa xử lý xong, tài nguyên bị rò rỉ.
25. **Không kiểm tra output sau conversion.** Nên có round-trip/semantic verification phù hợp.
26. **So byte nguồn và byte đích rồi báo sai vì khác nhau.** Đó là điều bình thường khi chuyển charset.
27. **Chỉ xem không có exception là chọn đúng charset nguồn.** Mojibake có thể hoàn toàn hợp lệ về cấu trúc.
28. **Đọc toàn file hàng chục GB vào heap** khi chỉ cần chuyển mã theo stream.
29. **Chia sẻ `CharsetDecoder`/`CharsetEncoder` giữa threads** mà không có ownership và reset đúng.
30. **Lẫn lộn UTF-8 malformed với unmappable ở charset đích**, khiến hướng sửa sai.
31. **Không test ký tự ngoài BMP**, chỉ test ASCII nên bỏ sót lỗi surrogate boundary.
32. **Trả về success dù cleanup/publish có lỗi**, khiến job status không phản ánh tệp thực.
33. **Tưởng charset conversion tự bảo vệ khỏi dữ liệu không tin cậy**, bỏ qua giới hạn input size, path validation và các lớp bảo mật.
34. **Lấy `InputStream.available()` làm kích thước file/network còn lại**, rồi cấp buffer tùy tiện.
35. **Cho rằng `UTF-16` và `UTF-16LE` cùng hành vi BOM.** Chọn rõ policy.
36. **Tự replace ký tự không đọc được bằng dấu ? mà không ghi nhận**, gây mất dấu lỗi dữ liệu.
37. **Để file nguồn bị sửa concurrent trong lúc convert**, tạo output không thuộc một phiên bản nhất quán.
38. **Chỉ catch `IOException` và che mất loại lỗi cụ thể**, khiến người dùng không biết sửa charset nguồn hay thay charset đích.
39. **Không xử lý file rỗng, file thiếu newline, text có ký tự NUL**, rồi giả định đều là text lỗi.
40. **Dùng raw bytes CRC/checksum trước/sau để kết luận Unicode giữ nguyên.** Hash bytes thay đổi khi encoding đổi; cần kiểm tra theo ý nghĩa của migration.

## 19. Câu hỏi tự kiểm tra

**1.** Tại sao chuyển ISO-8859-1 → UTF-8 không phải `Files.copy`?  
**Trả lời:** `Files.copy` giữ nguyên bytes, còn transcoding phải decode bytes nguồn thành Unicode rồi encode thành bytes đích.

**2.** `C3 28` được giải mã theo UTF-8 với `REPORT` dẫn tới gì?  
**Trả lời:** `MalformedInputException` vì dãy bytes không hợp lệ theo UTF-8.

**3.** String `Café` hợp lệ nhưng ghi sang US-ASCII với encoder `REPORT` sẽ thế nào?  
**Trả lời:** `UnmappableCharacterException` vì ký tự `é` không có mapping US-ASCII.

**4.** Cần cấu hình `REPORT` ở đâu?  
**Trả lời:** Cả `CharsetDecoder` và `CharsetEncoder`, cho cả malformed và unmappable khi yêu cầu conversion nghiêm ngặt.

**5.** Nếu bytes nguồn đã là UTF-8 của `é` nhưng chọn nhầm ISO-8859-1, decoder REPORT có luôn phát hiện không?  
**Không.** Nó có thể đọc thành `Ã©` mà không lỗi vì từng byte hợp lệ theo Latin-1.

**6.** Vì sao mã hóa từng chunk bằng encoder mới là lỗi tiềm ẩn?  
**Vì** surrogate pair hoặc trạng thái charset có thể trải qua ranh giới chunk; tạo encoder mới làm mất state.

**7.** `Reader.read(char[])` trả `n` tính bằng gì?  
**Trả lời:** Số UTF-16 `char` units đã đọc, không nhất thiết số bytes nguồn hay số Unicode code points.

**8.** `CodingErrorAction.REPLACE` có giữ nguyên mọi Unicode character không?  
**Không.** Nó dùng replacement và có thể mất thông tin gốc.

**9.** Chuyển Latin-1 `Café` từ 4 bytes sang UTF-8 cần bao nhiêu bytes?  
**Trả lời:** 5 bytes (`é` từ E9 thành C3 A9).

**10.** Nếu nguồn có byte UTF-8 lỗi sau khi writer đã ghi nhiều bytes staging, nên làm gì?  
**Trả lời:** Fail, đóng stream, xóa/giữ staging theo cleanup policy, không publish target dở dang.

**11.** `Files.move(staging,target)` không option có bảo đảm atomic replace và power-loss durability không?  
**Không.** Cần contract riêng cho atomicity và sync/durability.

**12.** `UTF_16LE` có giống hoàn toàn `UTF_16` về BOM không?  
**Không.** Một bên cố định little-endian; bên kia có semantics BOM/byte-order riêng.

**13.** Vì sao ví dụ dùng `char[4096]` mà không bị cắt hỏng emoji khi streaming?  
**Trả lời:** Reader và Writer với decoder/encoder giữ state xuyên qua các lần đọc/ghi trong cùng pipeline.

**14.** Sau khi chuyển UTF-8 sang UTF-16LE, `Files.mismatch(source,target)` nên bằng `-1` không?  
**Không.** Hai byte streams có định dạng encoding khác nhau; cần so Unicode sau decode.

**15.** Có thể dùng `Files.readAllBytes` để chuyển file hàng trăm GB không?  
**Không nên.** Nó cấp phát theo toàn bộ file và có giới hạn heap/array; ưu tiên streaming.

**16.** Muốn báo chính xác offset byte lỗi trong một file UTF-8 rất lớn, làm sao?  
**Trả lời:** Tự quản lý bộ đếm bytes và `CoderResult`/ByteBuffer vị trí với xử lý underflow/overflow đúng; exception ở tầng Reader không tự cung cấp absolute byte offset.

## 20. Tổng kết và tài liệu chính thức

**Công thức chuẩn:**

```text
[Bytes nguồn + charset nguồn đã xác định]
     → CharsetDecoder (malformed=REPORT, unmappable=REPORT)
     → Unicode chars (giữ state qua chunks)
     → CharsetEncoder (malformed=REPORT, unmappable=REPORT)
     → Bytes đích trong staging
     → Close thành công + validate + publish theo policy
```

Điểm phải nhớ: **transcoding là bảo toàn văn bản chứ không bảo toàn bytes**; kiểm tra lỗi ở cả *decode* lẫn *encode*. Khi nguồn không hợp lệ hoặc charset đích không biểu diễn được ký tự, **báo lỗi và tránh công bố tệp kết quả dở dang**. Muốn dữ liệu chính xác cần cả charset metadata đúng, xử lý BOM/newline hợp đồng, streaming có state, validation nghiệp vụ và chính sách file lifecycle phù hợp.

Nguồn Java SE 21:

- `Charset` — mô tả charset, `forName`, các conversion helpers: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/Charset.html
- `CharsetDecoder` — `onMalformedInput`, `onUnmappableCharacter`, `decode`, `CoderResult`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html
- `CharsetEncoder` — `encode`, `canEncode`, handling malformed/unmappable chars: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetEncoder.html
- `CodingErrorAction` — `REPORT`, `REPLACE`, `IGNORE`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CodingErrorAction.html
- `InputStreamReader` — stream decoder và charset decoder constructor: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStreamReader.html
- `OutputStreamWriter` — stream encoder và charset encoder constructor: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/OutputStreamWriter.html
- `MalformedInputException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/MalformedInputException.html
- `UnmappableCharacterException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/UnmappableCharacterException.html
- `Files.move` và `Files.readString/writeString`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html
- Unicode Standard, UTF encoding forms và BOM: https://www.unicode.org/versions/latest/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-003">Đọc file lớn theo dòng và thống kê dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc file lớn theo dòng và thống kê dữ liệu trong Java

Khi xử lý log, dữ liệu giao dịch, kết quả đo hoặc một file CSV có dung lượng hàng trăm MB đến nhiều GB, cách làm `Files.readAllLines(path)` rồi duyệt danh sách có thể tạo áp lực heap rất lớn: mỗi dòng là một chuỗi, còn có danh sách tham chiếu, đối tượng trung gian và chi phí giải mã. Đa số bài toán thống kê **không cần giữ toàn bộ dòng trong bộ nhớ**. Chúng chỉ cần đọc từng dòng, kiểm tra bản ghi rồi cập nhật vài bộ đếm, tổng, giá trị nhỏ nhất/lớn nhất hoặc thống kê theo nhóm.

Bài thực hành này hướng dẫn đọc một file lớn theo dòng bằng `BufferedReader` và `Files.lines()`, thiết kế phép thống kê theo kiểu **streaming / one-pass**, xử lý dòng lỗi, encoding, tràn số, tài nguyên và hiệu năng. Một chương trình Java 17 hoàn chỉnh sẽ đọc dữ liệu định dạng `level,durationMs`, đếm các bản ghi hợp lệ/không hợp lệ, số lượng theo mức log, tổng, min, max, average mà không tạo danh sách tất cả dòng.

**Mục tiêu:** Chi phí bộ nhớ chủ yếu phụ thuộc kích thước **dòng lớn nhất** và lượng trạng thái thống kê đang giữ, không tỷ lệ tuyến tính với tổng số dòng nếu chỉ lưu các bộ đếm và nhóm cố định.

## 1. Phân biệt đọc toàn bộ file với đọc từng dòng

| Cách đọc | Hành vi | Phù hợp |
| --- | --- | --- |
| `Files.readString(path)` | Đọc nội dung file thành một `String` | File nhỏ, cần nội dung nguyên vẹn trong RAM |
| `Files.readAllLines(path, charset)` | Đọc tất cả dòng và tạo `List<String>` | File nhỏ/vừa, muốn truy cập lại mọi dòng |
| `Files.newBufferedReader(path, charset)` | Đọc tuần tự bằng `BufferedReader.readLine()` | File lớn, xử lý từng dòng, kiểm soát luồng và lỗi |
| `Files.lines(path, charset)` | Trả về `Stream<String>` lười, đọc khi terminal operation chạy | Xử lý pipeline filter/map, vẫn phải đóng stream |
| `FileChannel.read(ByteBuffer)` | Đọc các khối byte, **không tự tách dòng hoặc giải mã charset** | Giao thức nhị phân, parser tùy biến hoặc cần kiểm soát byte buffer |

Đối với file văn bản lớn, `BufferedReader` là điểm khởi đầu tốt: dễ quản lý dòng, xử lý ngoại lệ checked `IOException`, kiểm soát vòng đời. `Files.lines()` hợp lý khi pipeline rõ ràng và không vô tình gom mọi dòng vào bộ nhớ bằng `collect(toList())`.

Độ lớn file **không đồng nghĩa** với việc phải dùng `MappedByteBuffer` hoặc tự viết bộ giải mã byte: streaming text qua `BufferedReader` thường đủ đúng, dễ bảo trì và dễ benchmark.

## 2. Bộ dữ liệu đầu vào và yêu cầu thống kê

Giả sử mỗi dòng dữ liệu là một bản ghi có hai cột, phân cách bởi dấu phẩy:

```text
level,durationMs
INFO,120
WARN,300
ERROR,450
INFO,80
WARN,200
broken
INFO,-1
ERROR,150
```

Quy tắc nghiệp vụ của bài thực hành:

1. Dòng đầu tiên **phải đúng** `level,durationMs`, là header và không tính vào thống kê.
2. Mỗi dòng tiếp theo phải có đúng một dấu phẩy, hai trường không rỗng.
3. `level` thuộc ba giá trị `INFO`, `WARN`, `ERROR`; so khớp phân biệt chữ hoa/thường sau khi bỏ khoảng trắng đầu/cuối mỗi trường.
4. `durationMs` phải là số nguyên kiểu `long` **không âm**.
5. Dòng không hợp lệ được **đếm vào `invalid`** và bỏ qua khi tính tổng/min/max/trung bình. Không bỏ cả file vì một dòng bẩn.
6. Các chỉ số cần in: tổng số dòng dữ liệu, valid, invalid, số dòng của từng level, tổng thời lượng, min, max, average.
7. File có thể rất lớn; chương trình không lưu `List<String>`, không giữ toàn bộ bản ghi hợp lệ và không cần tạo file trung gian.

Với dữ liệu mẫu: có **8 dòng dữ liệu**, 6 dòng hợp lệ, 2 dòng không hợp lệ (`broken` và `INFO,-1`). Mỗi level có 2 dòng hợp lệ; tổng thời lượng là **1300 ms**, min **80 ms**, max **450 ms**, trung bình **1300 / 6 = 216,67 ms**.

Đây là **CSV đơn giản do bài thực hành quy định**, **không phải parser CSV tổng quát theo RFC 4180**: nó không hỗ trợ dấu phẩy nằm trong field được quote, newline bên trong field hoặc quy tắc escape. Khi xử lý CSV thực tế có quoted fields, dùng một parser CSV chuyên dụng hoặc triển khai state machine có giới hạn tài nguyên, không sử dụng `split(",")` một cách mù quáng.

## 3. BufferedReader.readLine(): đọc dòng mà không gom cả file

Mẫu cơ bản:

```java
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

Path file = Path.of("events.csv");

try (BufferedReader reader =
        Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý ngay từng line, cập nhật các accumulator.
    }
}
```

Đây là **đoạn minh họa**: file đầu vào cần tồn tại và ứng dụng phải xử lý `IOException`; code chưa có phương thức `main`. Điểm chính:

- `readLine()` trả về một chuỗi chứa nội dung dòng, **không gồm ký tự kết thúc dòng**; hoặc `null` khi kết thúc file.
- Các dạng xuống dòng thông dụng như LF (`\n`), CRLF (`\r\n`) và CR được nhận diện làm dấu kết thúc dòng theo hợp đồng `BufferedReader`.
- **Dòng rỗng là `""`, không phải `null`.** Nếu dữ liệu có dòng rỗng, cần quyết định bỏ qua, đếm lỗi hay coi là bản ghi trống.
- `readLine()` có thể ném `IOException`, ví dụ lỗi thiết bị hoặc decode input không hợp lệ.
- `try-with-resources` đóng reader ngay cả khi vòng xử lý dừng vì lỗi. Nó cũng đóng stream bên dưới do `Files.newBufferedReader()` tạo.

**Cần phân biệt “không nạp cả file” với “giới hạn tuyệt đối RAM”.** `readLine()` phải tạo chuỗi cho **toàn bộ một dòng**. Nếu file có **một dòng dài hàng GB** mà không có newline, chương trình vẫn có nguy cơ thiếu bộ nhớ. Đọc theo dòng an toàn về bộ nhớ **khi độ dài từng dòng được kiểm soát**; với đầu vào không tin cậy phải có parser có giới hạn số ký tự/byte trước khi dựng chuỗi quá dài.

### Mã hóa ký tự và charset

Dùng charset rõ ràng:

```java
Files.newBufferedReader(path, StandardCharsets.UTF_8);
```

Không dựa vào default charset khi file là dữ liệu chia sẻ giữa máy. Một file thực tế có thể dùng UTF-8, UTF-16 hoặc encoding khác; **chỉ định đúng encoding của nguồn**. `UTF-8 BOM` ở đầu file có thể xuất hiện trong nội dung đọc như ký tự `\uFEFF`, khiến kiểm tra header bằng `equals()` thất bại. Nếu quy ước chấp nhận BOM, xử lý rõ ràng đúng **đầu file**, không tự xóa ký tự đầu của mọi dòng.

## 4. One-pass aggregation: giữ trạng thái nhỏ và cập nhật tăng dần

Thay vì:

```java
List<String> lines = Files.readAllLines(path);
long valid = lines.stream().filter(...).count();
```

(đoạn này chỉ minh họa anti-pattern, `...` không phải Java code chạy được), hãy duy trì accumulator:

```text
rows           = 0      // Tổng số dòng sau header
valid          = 0      // Số dòng được chấp nhận
invalid        = 0      // Số dòng bị loại
sumDurationMs  = 0      // Tổng thời lượng hợp lệ
minDurationMs  = +∞     // Min của các dòng hợp lệ
maxDurationMs  = -∞     // Max của các dòng hợp lệ
count[INFO]    = 0
count[WARN]    = 0
count[ERROR]   = 0
```

Với mỗi dòng:

1. Tăng `rows`.
2. Kiểm tra số cột, level và duration.
3. Nếu sai: tăng `invalid`, xử lý dòng kế tiếp.
4. Nếu đúng: tăng `valid`, tăng bộ đếm level, cộng duration vào tổng, cập nhật min/max.
5. Khi tới EOF: tính average nếu `valid > 0`; nếu không có dòng hợp lệ, dùng `n/a` để tránh chia cho 0.

**Không cần biết trước tổng số dòng hoặc dung lượng file** để tính các thống kê này. Với ba level cố định, số bộ đếm cũng cố định. Chi phí thời gian gần tương ứng tổng số ký tự/byte được đọc cộng chi phí parse, còn bộ nhớ không phải một `List` phình to cùng kích thước file.

### Tràn số và độ chính xác

Không nên dùng `int` cho tổng duration khi file có thể chứa hàng triệu bản ghi. Dùng `long` và nếu tổng có thể vượt `Long.MAX_VALUE`, kiểm tra bằng `Math.addExact()` (ném `ArithmeticException` khi overflow) hoặc dùng `BigInteger` nếu nghiệp vụ thực sự cần dải số lớn hơn.

Với trung bình:

```java
double average = valid == 0 ? Double.NaN
        : (double) sumDurationMs / valid;
```

Đây là công thức minh họa, biến phải khai báo ở context ứng dụng. `double` phù hợp cho báo cáo trung bình; nếu là tiền hoặc nghiệp vụ cần decimal precision tuyệt đối, dùng `BigDecimal` với chính sách làm tròn. Không cộng giá trị `long` vào `int` rồi mong phép chia cuối sửa được overflow đã xảy ra.

## 5. Chương trình Java 17 hoàn chỉnh: thống kê file lớn bằng một lượt đọc

Tạo file **`LargeFileStatsDemo.java`**. Nếu chạy **không có đối số**, chương trình tạo dữ liệu mẫu trong file tạm, phân tích và xóa file mẫu. Nếu truyền **một đường dẫn**, chương trình đọc file đó và **không sửa/xóa file do người dùng chỉ định**.

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class LargeFileStatsDemo {
    private LargeFileStatsDemo() {}

    private enum Level { INFO, WARN, ERROR }

    private static final class Stats {
        long rows;
        long valid;
        long invalid;
        long sumMs;
        long minMs = Long.MAX_VALUE;
        long maxMs = Long.MIN_VALUE;
        final long[] byLevel = new long[Level.values().length];

        void accept(Level level, long durationMs, long lineNumber)
                throws IOException {
            try {
                sumMs = Math.addExact(sumMs, durationMs);
                valid = Math.incrementExact(valid);
                byLevel[level.ordinal()] =
                        Math.incrementExact(byLevel[level.ordinal()]);
            } catch (ArithmeticException overflow) {
                throw new IOException(
                        "Statistics overflow at line " + lineNumber,
                        overflow);
            }
            minMs = Math.min(minMs, durationMs);
            maxMs = Math.max(maxMs, durationMs);
        }

        void print() {
            System.out.println("rows=" + rows);
            System.out.println("valid=" + valid);
            System.out.println("invalid=" + invalid);
            for (Level level : Level.values()) {
                System.out.println(level + "=" + byLevel[level.ordinal()]);
            }
            System.out.println("totalMs=" + sumMs);
            if (valid == 0) {
                System.out.println("minMs=n/a");
                System.out.println("maxMs=n/a");
                System.out.println("avgMs=n/a");
            } else {
                System.out.println("minMs=" + minMs);
                System.out.println("maxMs=" + maxMs);
                System.out.printf(Locale.ROOT, "avgMs=%.2f%n",
                        (double) sumMs / valid);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.err.println("Usage: java LargeFileStatsDemo [file.csv]");
            return;
        }

        if (args.length == 1) {
            analyze(Path.of(args[0])).print();
            return;
        }

        Path example = Files.createTempFile("large-file-stats-", ".csv");
        try {
            String sample = String.join("\n",
                    "level,durationMs",
                    "INFO,120",
                    "WARN,300",
                    "ERROR,450",
                    "INFO,80",
                    "WARN,200",
                    "broken",
                    "INFO,-1",
                    "ERROR,150") + "\n";

            // Chỉ ghi đè file tạm vừa được chính chương trình tạo.
            Files.writeString(example, sample, StandardCharsets.UTF_8);
            analyze(example).print();
        } finally {
            Files.deleteIfExists(example);
        }
    }

    private static Stats analyze(Path file) throws IOException {
        Stats stats = new Stats();

        try (BufferedReader reader =
                Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!"level,durationMs".equals(header)) {
                throw new IOException("Missing or invalid CSV header");
            }

            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                stats.rows++;

                int comma = line.indexOf(',');
                if (comma <= 0
                        || comma == line.length() - 1
                        || line.indexOf(',', comma + 1) >= 0) {
                    stats.invalid++;
                    continue;
                }

                Level level;
                long duration;
                try {
                    level = Level.valueOf(
                            line.substring(0, comma).trim());
                    duration = Long.parseLong(
                            line.substring(comma + 1).trim());
                } catch (IllegalArgumentException invalidRow) {
                    stats.invalid++;
                    continue;
                }

                if (duration < 0) {
                    stats.invalid++;
                    continue;
                }

                stats.accept(level, duration, lineNumber);
            }
        }

        return stats;
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac LargeFileStatsDemo.java
java LargeFileStatsDemo
```

**Stdout chính xác cho dữ liệu mẫu tích hợp:**

```text
rows=8
valid=6
invalid=2
INFO=2
WARN=2
ERROR=2
totalMs=1300
minMs=80
maxMs=450
avgMs=216.67
```

Để đọc file của bạn:

```bash
java LargeFileStatsDemo /path/to/events.csv
```

Đây chỉ là ví dụ cú pháp đường dẫn trên hệ Unix; hãy thay bằng đường dẫn thật phù hợp hệ điều hành của bạn. File phải tuân thủ định dạng `level,durationMs` nêu trên; output sẽ phản ánh dữ liệu của file được truyền vào.

### Phân tích cách ví dụ hoạt động

- `Files.newBufferedReader()` chỉ giữ một buffer đọc và chuỗi của dòng hiện tại, thay vì danh sách mọi dòng.
- Header được kiểm tra trước để tránh nhầm cột hoặc thống kê một file hoàn toàn sai định dạng.
- `line.indexOf(',')` xác định dấu phẩy duy nhất và kiểm tra không có dấu phẩy thứ hai; phù hợp **định dạng hai cột đơn giản**, không có quoted CSV.
- `Level.valueOf()` kiểm tra level hợp lệ; `Long.parseLong()` kiểm tra duration; cả hai có thể ném `IllegalArgumentException` (bao gồm `NumberFormatException` là lớp con) và được ghi nhận như dòng không hợp lệ.
- `stats.accept()` chỉ chạy với bản ghi hợp lệ; `Math.addExact()` ngăn số tổng im lặng tràn `long`.
- `minMs/maxMs` khởi tạo bằng các sentinel nhưng **không in sentinel nếu valid=0**.
- `Locale.ROOT` giữ dấu thập phân `.` trong kết quả mẫu, tránh phụ thuộc thiết lập vùng địa lý trên máy.
- Nhánh chạy với tham số chỉ **đọc** file người dùng, còn nhánh không tham số chỉ tạo và xóa **file tạm do chương trình sở hữu**.

Bài tập cố ý đếm dòng sai nhưng không in toàn bộ nội dung dòng vì file lớn có thể làm log lỗi phình to hoặc lộ dữ liệu. Với dịch vụ thật, có thể xuất **một số lượng chẩn đoán có giới hạn**, ví dụ tối đa 10 lỗi đầu, kèm số dòng, nguyên nhân và không lộ field nhạy cảm.

## 6. Files.lines(): cùng mục tiêu nhưng dùng Stream API

`Files.lines(path, StandardCharsets.UTF_8)` trả `Stream<String>` đọc lười. Một ví dụ đơn giản đếm những dòng chứa từ khóa:

```java
// Minh họa trong phương thức cho phép ném IOException.
try (java.util.stream.Stream<String> lines =
        Files.lines(file, StandardCharsets.UTF_8)) {
    long errors = lines.filter(s -> s.startsWith("ERROR,")).count();
    System.out.println(errors);
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập: `file` phải là `Path` hợp lệ, có import `Files` và xử lý ngoại lệ. `Files.lines()` không có nhu cầu tạo danh sách trước khi `count()`. Nó đọc và kiểm tra các dòng khi terminal operation chạy.

**Đóng stream là bắt buộc.** Stream trả về từ `Files.lines()` gắn với tài nguyên file đang mở; không trả nó ra khỏi khối `try-with-resources` rồi để caller sử dụng sau khi file đã bị đóng.

Trong quá trình stream vận hành, lỗi I/O phát sinh sau khi stream được tạo có thể được đóng gói thành `UncheckedIOException` bởi pipeline. Điều này khác cách `BufferedReader.readLine()` khai báo checked `IOException`. Hãy lựa chọn cách xử lý lỗi phù hợp và không nuốt exception trong terminal operation.

### Không biến streaming thành eager collection

Một anti-pattern:

```java
try (var lines = Files.lines(path)) {
    var all = lines.toList();
    // all bây giờ chứa mọi dòng trong RAM!
}
```

Đoạn này là **minh họa**, cần biến `path` kiểu `Path` và xử lý `IOException`. `Files.lines()` chỉ có tính chất lazy **cho đến khi** bạn dùng terminal operation. `toList()`, `collect(toList())` hoặc `groupingBy(..., toList())` có thể tích lũy lượng dữ liệu lớn, đánh mất lợi ích bộ nhớ.

Có thể dùng collector để đếm nhóm:

```java
// Đoạn minh họa: chỉ phù hợp sau khi đã xác thực dữ liệu dòng.
try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
    var counts = lines
            .filter(s -> !s.isBlank())
            .collect(java.util.stream.Collectors.groupingBy(
                    s -> s.substring(0, s.indexOf(',')),
                    java.util.stream.Collectors.counting()));
}
```

Nhưng **mã này không an toàn với input thiếu dấu phẩy**, và header sẽ bị đếm nếu không bỏ qua. Nó chỉ minh họa collector, **không thay thế code hoàn chỉnh đã kiểm tra dữ liệu**. Hơn nữa, bộ nhớ của `groupingBy()` tăng theo số nhóm khác nhau. Nếu mỗi dòng có một khóa duy nhất, map có thể tăng gần bằng tổng số dòng; khi đó cần giới hạn cardinality hoặc external aggregation.

## 7. Thiết kế thống kê theo nhóm trong file lớn

Bộ đếm ba level cố định của bài thực hành giữ bộ nhớ O(1) theo số dòng. Với dữ liệu thực tế thường cần thống kê theo ngày, khu vực hoặc mã trạng thái:

| Bài toán | State tối thiểu | Bộ nhớ |
| --- | --- | --- |
| Đếm toàn bộ dòng và dòng sai | Một vài bộ đếm `long` | Gần O(1) |
| Tổng, min, max, average | `sum, count, min, max` | Gần O(1) |
| Đếm theo level có 3 giá trị | Mảng ba phần tử / `EnumMap` | Gần O(1) |
| Đếm theo mã trạng thái HTTP có miền hữu hạn | Mảng hoặc map giới hạn số mã | Tỷ lệ số mã có thể xuất hiện |
| Đếm theo user ID không giới hạn | `Map<userId, count>` | Tỷ lệ số user ID khác nhau, có thể rất lớn |
| Tính số giá trị phân biệt chính xác | `HashSet` | Tỷ lệ số giá trị khác nhau |
| Top-K với độ lớn K cố định | Min-heap và bộ đếm thích hợp | Thường O(K) cho lựa chọn top-K khi đã có tần suất, nhưng tính chính xác tần suất mọi khóa còn cần state khác |
| Median/p95 chính xác | Thường cần dữ liệu hoặc cấu trúc chỉ mục/thuật toán thích hợp | Không phải luôn O(1) |
| Median/p95 gần đúng | Sketch/histogram phù hợp | Có thể giới hạn bộ nhớ nhưng có sai số |

Không nên tuyên bố “đọc từng dòng ⇒ mọi thống kê đều O(1) memory”. Điều đúng là **cơ chế đọc không tự lưu toàn bộ file**; phần aggregation vẫn có thể giữ nhiều dữ liệu tùy yêu cầu. Nếu cardinality cao, cân nhắc chia bucket theo khóa, external sort, cơ sở dữ liệu hoặc công cụ xử lý dữ liệu theo khối.

### Giữ đủ thông tin để tính trung bình đúng

Muốn tổng hợp trung bình giữa nhiều file, không lưu và cộng trung bình mỗi file một cách trực tiếp, vì file có số dòng hợp lệ khác nhau. Hãy lưu **`sum` và `count`**, sau đó gộp:

```text
File A: sum=100, count=1      --> avg=100
File B: sum=300, count=3      --> avg=100
Combined: (100+300)/(1+3)    --> avg=100
```

Trường hợp khác sẽ cho thấy rõ lỗi “average of averages”:

```text
File A: sum=100, count=1       --> avg=100
File B: sum=0, count=9         --> avg=0
Incorrect: (100+0)/2          --> 50
Correct:   (100+0)/(1+9)      --> 10
```

Nếu gộp trên nhiều máy, phải định nghĩa overflow, thứ tự kết hợp và chính sách xử lý dòng không hợp lệ trước khi tổng hợp.

## 8. Tối ưu I/O: buffer, số lần cấp phát và charset decoder

`Files.newBufferedReader()` dùng buffered character reader phù hợp nhiều trường hợp. Nếu cần kích thước buffer cụ thể, có thể kết hợp:

```java
// Minh họa, file là Path hợp lệ; IOException được xử lý ở caller.
try (BufferedReader reader = new BufferedReader(
        new java.io.InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8),
        64 * 1024)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Thống kê trực tiếp.
    }
}
```

Không nên kết luận tăng buffer từ 8 KiB lên 64 KiB luôn nhanh hơn. Kết quả phụ thuộc chiều dài dòng, ổ đĩa, page cache, giải mã charset, số lượng file và chi phí parse.

Một số lựa chọn tối ưu cần benchmark có kiểm soát:

1. **Hạn chế tạo đối tượng không cần thiết.** `String.split(regex)` tạo mảng và nhiều chuỗi; nếu định dạng chỉ có một dấu phân cách, `indexOf()` và `substring()` có thể đơn giản hơn. Tuy nhiên đừng đánh đổi tính đúng của CSV phức tạp để lấy tốc độ.
2. **Đếm bằng `long` thay vì `int`** cho file rất lớn.
3. **Tách I/O và parsing trong benchmark.** Đo đọc đơn thuần khác đo đọc + kiểm tra logic + tạo bản ghi + tổng hợp.
4. **Làm nóng JVM/JIT và file cache công bằng.** Lượt thứ hai có thể nhanh hơn do OS page cache dù API không đổi; không so một lượt cold với một lượt warm rồi kết luận thuật toán tốt hơn.
5. **Không tạo `StringBuilder` khổng lồ cho tất cả dòng** chỉ để thống kê vài chỉ số.
6. **Không parallelize bừa `Files.lines()`**: splitting dòng và thứ tự, tài nguyên chung và mutable accumulator có thể làm code phức tạp mà không tăng tốc.
7. **Tránh ghi log trên mỗi dòng** khi xử lý file hàng trăm triệu dòng; bản thân stdout/log có thể chậm hơn I/O đọc.
8. **Chọn charset đúng** và xử lý encoding error; tốc độ không quan trọng nếu dữ liệu bị parse sai.

Có thể benchmark với JMH cho những bước thuần CPU/parse, nhưng **JMH không tự kiểm soát cache hệ điều hành**. Nếu muốn đánh giá throughput end-to-end, còn cần đo thời gian mở file, đọc, decode, parse, GC và đóng tài nguyên theo phạm vi thật của ứng dụng.

## 9. Xử lý dữ liệu lỗi và giới hạn an toàn

File lớn thường không hoàn toàn sạch. Chính sách **skip, fail-fast, quarantine** cần được thống nhất trước khi chạy:

| Tình huống | Gợi ý xử lý |
| --- | --- |
| Header sai hoặc file trống | Fail-fast, vì có thể đọc nhầm loại dữ liệu |
| Dòng sai số cột | Đếm invalid, ghi một số diagnostic được giới hạn |
| Level lạ | Đếm invalid hoặc map sang nhóm OTHER nếu nghiệp vụ cho phép |
| Duration không phải số nguyên | Đếm invalid hoặc fail-fast tùy yêu cầu |
| Duration âm | Từ chối nếu miền hợp lệ bắt buộc không âm |
| Giá trị duration gây tràn tổng | Dừng với lỗi rõ ràng hoặc dùng `BigInteger` |
| File có charset sai | Báo lỗi decode; không tự chuyển encoding và tiếp tục mà không có quy tắc |
| Một dòng cực dài | Áp dụng giới hạn ký tự/byte trong parser trước khi cấp phát quá nhiều bộ nhớ |
| File thay đổi trong lúc đang đọc | Kết quả có thể không phản ánh snapshot nhất quán |
| Ổ đĩa/network filesystem lỗi | `IOException`; không báo hoàn thành với thống kê một phần nếu không có checkpoint |

Nếu thống kê phục vụ nghiệp vụ nhạy cảm (tài chính, kiểm toán), cần lưu cả policy parse, schema version, checksum của file đầu vào và cách xử lý lỗi để kết quả có thể truy vết. Không nên tự động bỏ dòng lỗi rồi công bố thống kê như thể toàn bộ dữ liệu đã được xử lý.

### Không chấp nhận một dòng vô hạn chỉ vì đã streaming

`BufferedReader.readLine()` không cung cấp tham số “maximum line length”. Nếu đầu vào do bên thứ ba không tin cậy tạo, bộ giới hạn phải chạy **trong lúc tiêu thụ ký tự/byte**, không chỉ:

```java
String line = reader.readLine();
if (line.length() > 10000) { ... }
```

vì tại thời điểm kiểm tra, `readLine()` **đã tạo chuỗi rất dài**. Một parser giới hạn an toàn hơn có thể đọc từng chunk/chữ cái, dừng/đánh dấu khi vượt ngưỡng và tiếp tục bỏ phần còn lại tới newline mà không tích lũy. Nếu cần dữ liệu CSV đúng chuẩn, nên chọn thư viện parser có khả năng giới hạn chiều dài record/field.

## 10. Xử lý file có nhiều GB, nhiều ngày hoặc nhiều nguồn

Đối với **một file lớn**, streaming one-pass thường đủ. Với **nhiều file**, có thể chạy vòng lặp từng file, đóng `BufferedReader` sau mỗi file, cộng thống kê vào accumulator tổng bằng phép cộng có kiểm tra overflow. Không cần mở hàng nghìn file descriptor đồng thời.

Với file được cập nhật liên tục như log đang chạy, một lượt đọc có thể gặp EOF trước khi log writer ghi tiếp. **Đọc file tới EOF khác với theo dõi file (tail -f).** Nếu ứng dụng cần nhận dữ liệu mới theo thời gian thực, phải có cơ chế polling, rotation handling hoặc watcher theo thiết kế, không để `readLine()` quay trong vòng lặp busy-wait vô hạn.

Khi file đang bị ghi cùng lúc, không mặc nhiên có ảnh chụp (snapshot) nhất quán. Có thể lựa chọn:

- Chỉ xử lý file được producer **đóng hoàn toàn** rồi publish bằng đổi tên phù hợp.
- Tạo bản snapshot/immutable input với checksum hoặc version rõ ràng.
- Nếu phải đọc file live, quy định khi nào một dòng dở chưa kết thúc newline được coi là hoàn chỉnh, xử lý log rotation và retry.
- Nếu muốn đọc song song nhiều phần của cùng file, phải tìm **ranh giới dòng thật** tại các điểm chia; chia chỉ theo byte offset rồi mỗi worker đọc tùy ý dễ đếm thiếu hoặc trùng dòng.

**Lưu ý:** Java `BufferedReader.readLine()` có thể trả về dòng cuối ngay cả khi file không kết thúc bằng newline. Với file bất biến, đây là dòng hợp lệ theo quy tắc đọc văn bản; với log đang ghi, nó có thể là **bản ghi dở**. Nên định nghĩa giao thức publish/snapshot thay vì suy đoán.

## 11. Kiểm thử thống kê trước khi dùng trên dữ liệu thật

Các ca cần có:

1. **File chỉ có header:** `rows=0`, `valid=0`, `invalid=0`, min/max/average là `n/a`.
2. **File rỗng:** Báo lỗi header, không chấp nhận thành tập dữ liệu hợp lệ.
3. **Dòng malformed:** Thiếu dấu phẩy, thừa dấu phẩy, chỉ có một cột, level lạ, duration không phải số.
4. **Dòng trống:** Với chính sách ví dụ, dòng trống là `invalid`.
5. **Duration âm:** Bị loại, không ảnh hưởng `sum` hay average.
6. **Duration bằng 0:** Là hợp lệ, ảnh hưởng `count` và có thể ảnh hưởng `minMs`.
7. **Duration lớn:** Kiểm tra parsing `long`, xử lý tổng vượt `Long.MAX_VALUE`.
8. **UTF-8/BOM:** Kiểm tra encoding và chính sách BOM của file thực tế.
9. **CRLF/LF:** Đảm bảo cách đếm dòng không phụ thuộc kiểu newline.
10. **Không có newline cuối file:** Dòng cuối vẫn được đọc.
11. **File rất lớn:** Theo dõi heap/GC, tránh tăng memory theo số dòng.
12. **File bị thay đổi giữa lượt:** Đảm bảo có chính sách snapshot/retry hoặc ghi rõ kết quả không phải snapshot.
13. **Lỗi giữa quá trình đọc:** Không xuất một báo cáo “thành công” khi `IOException` xảy ra.
14. **File do người dùng chỉ định:** Chương trình không xóa hoặc sửa nội dung đầu vào.

Với bài mẫu ở mục 5, sáu bản ghi hợp lệ có duration `120, 300, 450, 80, 200, 150`. Kiểm tra thủ công: tổng bằng `1300`, min `80`, max `450`, average `216.666...` nên in `216.67`. Mỗi level xuất hiện đúng hai lần, còn tổng số dòng sau header là tám.

## 12. Bài tập mở rộng

Sau khi chạy được chương trình, có thể tự mở rộng theo các hướng sau:

**Bài 1 — Thống kê theo ngày.** Thêm cột `date` theo ISO `yyyy-MM-dd`, đếm theo ngày và level. Dùng `LocalDate.parse()` để xác minh ngày, rồi cân nhắc `Map<LocalDate, Stats>`. Phân tích bộ nhớ nếu file có hàng triệu giá trị ngày/khóa khác nhau.

**Bài 2 — Đọc nhiều file.** Truyền danh sách đường dẫn từ CLI, xử lý từng file qua cùng một phương thức `analyze()` rồi tổng hợp. Có chính sách đối với một file thất bại: dừng tất cả hay báo trạng thái từng file; đừng im lặng bỏ file lỗi.

**Bài 3 — Báo tỷ lệ lỗi.** Tính `invalid / rows × 100%` nếu `rows > 0`, in bằng `Locale.ROOT`. Đặt ngưỡng (ví dụ tỷ lệ lỗi vượt 1%) để cảnh báo hoặc làm job thất bại.

**Bài 4 — Phân biệt thống kê exact và approximate.** Với hàng tỷ duration, thử thiết kế histogram cố định các bucket `0–99`, `100–499`, `500+`. Bộ đếm các bucket có bộ nhớ gần O(1), nhưng percentile từ bucket là **ước lượng**, không phải giá trị chính xác.

**Bài 5 — So sánh hai phương án.** Viết thêm bản dùng `Files.lines()`, đảm bảo đóng stream, parse giống hệt và so sánh tổng byte/dòng, checksum, số dòng lỗi; benchmark nhiều lượt với trạng thái page cache công bằng. Đừng chỉ đo một lần rồi kết luận API nào nhanh hơn.

## 13. Lỗi phổ biến và cách tránh

| Lỗi | Hậu quả | Khắc phục |
| --- | --- | --- |
| `Files.readAllLines()` với file nhiều GB | Heap tăng mạnh, GC/OutOfMemoryError | Dùng `BufferedReader.readLine()` |
| `Files.lines().toList()` | Gom toàn bộ dòng vào RAM | Dùng terminal operation không materialize mọi dòng |
| Quên đóng `Stream` từ `Files.lines()` | Rò rỉ file descriptor/tài nguyên | `try-with-resources` |
| So sánh `line == null` với dòng rỗng | Nhầm EOF và bản ghi trống | `null` chỉ ở EOF |
| Mặc định một file lớn chỉ có dòng ngắn | Một dòng cực dài vẫn làm tăng RAM | Parser có giới hạn record length |
| Không chỉ định charset | Đọc sai tiếng Việt/cột header | Dùng charset phù hợp, thường UTF-8 |
| `split(",")` cho CSV có quoted commas | Tách sai bản ghi | Dùng CSV parser đúng chuẩn |
| Cộng duration trong `int` | Tràn số im lặng | `long` + kiểm tra overflow |
| Tính average khi `count=0` | Chia 0 hoặc output không đúng | Quy ước `n/a` |
| Dùng `groupingBy()` với khóa duy nhất cho mỗi dòng | Map tăng gần số dòng | Giới hạn cardinality hoặc external aggregation |
| In log mỗi bản ghi | I/O ghi log lấn át công việc thống kê | Aggregate lỗi và giới hạn diagnostics |
| Dùng `parallel()` với accumulator dùng chung không đồng bộ | Race, tổng đếm sai | Aggregator thread-safe hoặc thiết kế partition/merge |
| Không kiểm tra header và số cột | Đọc nhầm schema nhưng vẫn có “kết quả” | Validate cấu trúc đầu vào |
| Đọc file đang thay đổi mà coi như snapshot bất biến | Số liệu thiếu/không nhất quán | Publish immutable file hoặc snapshot |
| Cho rằng một `readLine()` luôn đọc nguyên vẹn bản ghi nghiệp vụ | CSV quoted multiline có thể trải nhiều dòng vật lý | Parser CSV có state đúng chuẩn |

**Ghi nhớ:** Với file văn bản lớn, hãy **đọc từng dòng bằng `BufferedReader` hoặc `Files.lines()`**, kiểm tra schema và dữ liệu ngay, cập nhật accumulator thay vì tích lũy mọi bản ghi. Bộ nhớ có thể giữ gần cố định khi chỉ tính count/sum/min/max và số nhóm được giới hạn, nhưng vẫn phải kiểm soát **độ dài dòng, cardinality của nhóm, charset, lỗi I/O, overflow và vòng đời stream**. Đó là nền tảng của một job thống kê Java vừa có khả năng mở rộng vừa kiểm chứng được kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-004">Thiết kế định dạng nhị phân có version và length</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Thiết kế định dạng nhị phân có version và length trong Java

## 1. Bài toán và mục tiêu

Giả sử ứng dụng Java cần lưu hàng nghìn bản ghi vào một tệp nhị phân hoặc truyền chúng liên tiếp qua TCP. Mỗi bản ghi có mã định danh và tên; ở phiên bản mới, bản ghi có thêm độ ưu tiên. Ta cần một định dạng mà chương trình có thể:

- Xác định **ranh giới** của từng bản ghi mà không phải đoán byte kết thúc.
- Biết **phiên bản cấu trúc** để giải mã đúng trường dữ liệu.
- Đọc tuần tự nhiều bản ghi trong cùng file/luồng TCP.
- Phát hiện đầu vào không hợp lệ, bị cắt cụt hoặc có checksum sai.
- Giới hạn kích thước trước khi cấp phát bộ nhớ.
- Có chính sách rõ ràng khi gặp bản ghi thuộc phiên bản chưa được hỗ trợ.
- Chạy được giữa các tiến trình/JDK mà không lệ thuộc serialization mặc định của class Java.

Đây là vấn đề **binary protocol design** và **record framing**. Không nên đơn giản ghi một loạt số nguyên/chuỗi rồi kỳ vọng bên đọc tự biết lúc nào bản ghi kết thúc.

~~~text
Tệp nhị phân hoặc luồng TCP
  |
  +-- [HEADER record 1][PAYLOAD 1][CHECKSUM 1]
  +-- [HEADER record 2][PAYLOAD 2][CHECKSUM 2]
  +-- [HEADER record 3][PAYLOAD 3][CHECKSUM 3]
                 |
                 v
        Parser theo từng frame
                 |
       validate -> decode theo version
~~~

**Cặp version + length giải quyết hai câu hỏi khác nhau:**

- **Version:** byte bên trong payload phải được diễn giải theo cấu trúc nào?
- **Length:** byte của frame hiện tại kết thúc ở đâu và frame kế tiếp bắt đầu tại vị trí nào?

Version không thay thế length, và length không tự nói cho decoder biết dữ liệu bên trong có cấu trúc gì.

## 2. Trước tiên hãy viết đặc tả định dạng byte

Một giao thức tốt không chỉ có code Java. Nó cần mô tả chính xác **thứ tự trường, độ dài, byte order, encoding, giới hạn và điều kiện lỗi**. Ta chọn một định dạng minh họa tên `JBIN`:

| Offset trong frame | Độ dài | Trường | Quy tắc |
| ---: | ---: | --- | --- |
| 0 | 4 byte | `magic` | ASCII `JBIN`, tương đương hex `4A 42 49 4E` |
| 4 | 1 byte | `version` | Số nguyên không dấu 0–255; hỗ trợ version 1 và 2 |
| 5 | 4 byte | `payloadLength` | Số nguyên signed 32-bit, **big-endian**, chỉ chấp nhận 0 tới 65.536 |
| 9 | `payloadLength` byte | `payload` | Cấu trúc theo version |
| 9 + length | 4 byte | `crc32` | Giá trị CRC-32 **chỉ của payload**, ghi thành 32-bit big-endian |

Tổng độ dài một frame:

\[
\mathrm{frameBytes} = 4 + 1 + 4 + \mathrm{payloadLength} + 4
= 13 + \mathrm{payloadLength}.
\]

Sơ đồ byte:

~~~text
      4B       1B          4B                 L bytes         4B
+-----------+---------+--------------+-----------------------+----------+
|   JBIN    | version | payloadLength|       payload         |  CRC32   |
+-----------+---------+--------------+-----------------------+----------+
  magic       u8          i32 BE        bytes theo version      u32 BE

Frame 1 | Frame 2 | Frame 3 | ... | EOF tại ranh giới frame
~~~

Đây là **định dạng tự thiết kế để thực hành**, không phải tiêu chuẩn JDK. Hai chương trình khác ngôn ngữ vẫn có thể trao đổi dữ liệu nếu tuân thủ cùng đặc tả.

### Vì sao cần magic?

Magic giúp phát hiện sớm đầu vào không đúng định dạng. Nếu bốn byte đầu không bằng `JBIN`, parser từ chối trước khi diễn giải length hoặc payload.

Magic **không phải chứng thực bảo mật** và không bảo đảm tự động tìm lại ranh giới khi file hỏng. Việc quét tùy tiện để tìm chuỗi `JBIN` trong payload có thể nhận nhầm byte dữ liệu là header thật. Khi cần khả năng phục hồi, hãy xây cơ chế resynchronization/commit/index có quy tắc rõ ràng.

### Vì sao dùng length dạng số nguyên cố định?

Length-prefix cho phép xác định frame tiếp theo mà không cần ký tự phân cách. Điều đó quan trọng với payload nhị phân, nơi mọi giá trị byte từ 0 tới 255 đều có thể hợp lệ.

Nhưng đừng tin giá trị length đến từ file hoặc mạng. Bốn byte có thể được diễn giải thành một số âm hoặc hàng tỷ byte. **Kiểm tra giới hạn trước khi tạo `new byte[length]`.**

Ở bài này giới hạn bài tập là 65.536 byte cho **mỗi payload**, không phải tổng kích thước file. Production cần một mức tối đa phù hợp với nghiệp vụ, bộ nhớ và số connection đồng thời.

## 3. Thiết kế payload theo version

Chọn hai phiên bản khác nhau có chủ đích:

### Version 1

~~~text
+------------+----------------+-----------------------+
| id: i32 BE | nameLen: u16 BE| nameUtf8: nameLen B   |
+------------+----------------+-----------------------+
     4B             2B             0..65535 B
~~~

Ví dụ một message có `id=7` và `name="Alpha"`. Tên được mã hóa **UTF-8**, còn `nameLen` là **số byte UTF-8**, không phải `String.length()`.

### Version 2

Giữ nguyên các trường v1 và **thêm cuối payload** một số nguyên `priority`:

~~~text
+------------+----------------+----------------------+------------------+
| id: i32 BE | nameLen: u16 BE| nameUtf8: nameLen B | priority: i32 BE |
+------------+----------------+----------------------+------------------+
     4B             2B            n bytes                4B
~~~

Với version 2, decoder phải đọc thêm đúng bốn byte priority. Nếu không có đủ bốn byte, frame không hợp lệ. Nếu decoder gặp byte dư không được đặc tả, **không tự im lặng bỏ qua** đối với version đã biết: có thể đã encode sai schema hoặc parser bị lệch.

### Version chưa hỗ trợ

Nếu reader hiện hỗ trợ version 1 và 2 nhưng bắt gặp version 99:

- Nó **vẫn đọc và giới hạn length** theo header chung.
- Nó **đọc và kiểm tra toàn bộ frame** theo những quy tắc chung mà nó hiểu.
- Nó **không tự đoán** cấu trúc payload version 99.
- Nó có thể **bỏ qua frame nguyên vẹn** rồi đọc frame kế tiếp, nếu chính sách giao thức cho phép; hoặc **từ chối toàn bộ file/kết nối** nếu mọi message đều bắt buộc phải hiểu.

Hai chính sách đều có thể đúng. Việc skip chỉ hợp lệ khi mỗi frame được đóng gói độc lập và không mang trạng thái làm thay đổi ý nghĩa frame sau. Nếu version mới thay đổi **cấu trúc header chung**, reader cũ có thể không skip được; khi đó cần protocol negotiation hoặc file-format major version ở cấp container.

## 4. Byte order: Java phải ghi và đọc nhất quán

Với số nguyên nhiều byte, thứ tự byte (*endianness*) phải được đặc tả. Ta chọn **big-endian**:

~~~text
Số int 0x01020304
Big-endian:    01 02 03 04
Little-endian: 04 03 02 01
~~~

`DataOutputStream.writeInt()` và `DataInputStream.readInt()` dùng thứ tự **big-endian**. `writeShort/readUnsignedShort` cũng trao đổi theo thứ tự byte từ cao xuống thấp.

`ByteBuffer` mặc định là big-endian nhưng có thể đổi bằng `order(ByteOrder.LITTLE_ENDIAN)`; đừng giả định ByteBuffer được truyền từ code khác vẫn giữ byte order mặc định. Nếu triển khai cùng định dạng bằng `ByteBuffer`, nên khai báo `buffer.order(ByteOrder.BIG_ENDIAN)` có chủ đích.

Một reader C/Python/Go phải tuân thủ cùng endianness. Không ghi cấu trúc `struct` native tùy ý rồi coi padding/alignment của C giống Java.

### Unsigned và signed

- Version: 1 byte không dấu, Java dùng `readUnsignedByte()` để nhận 0..255.
- `nameLen`: 2 byte không dấu, Java dùng `readUnsignedShort()` để nhận 0..65.535.
- `payloadLength`: 4 byte signed. Ta quy định **0..65.536 hợp lệ**, âm hoặc vượt trần là lỗi.
- CRC32: 32-bit bit pattern, được ghi bằng `writeInt((int) crc.getValue())` và so sánh theo bit pattern khi đọc.

Java `byte` là signed; nếu cần in hex của một byte, dùng `value & 0xFF` để tránh sign extension.

## 5. UTF-8 và các bẫy độ dài chuỗi

Ví dụ chuỗi `"A"` dài 1 ký tự UTF-16, có 1 byte UTF-8. Chuỗi `"Đ"` dài 1 UTF-16 code unit nhưng có **2 byte UTF-8**; các ký tự ngoài BMP có thể cần 4 byte UTF-8 và 2 UTF-16 code unit.

Do đó, không làm thế này:

~~~java
out.writeShort(name.length()); // SAI nếu độ dài phải tính theo byte UTF-8
out.write(name.getBytes(StandardCharsets.UTF_8));
~~~

Mảnh code trên minh họa lỗi. Cách đúng:

~~~java
byte[] encodedName = name.getBytes(StandardCharsets.UTF_8);
if (encodedName.length > 65535) {
    throw new IOException("Name too long");
}
out.writeShort(encodedName.length);
out.write(encodedName);
~~~

`writeShort` ghi 16 bit thấp; **nó không tự từ chối** tên dài hơn 65.535 byte theo ý định giao thức, vì vậy phải kiểm tra trước.

Không dùng `DataOutputStream.writeUTF()` như thay thế tùy tiện cho định dạng vừa thiết kế. Phương thức đó dùng **modified UTF-8** với framing riêng của Java và các giới hạn riêng; nó không tương đương “u16 length + UTF-8 chuẩn” của đặc tả này. Để tương thích đa ngôn ngữ, hãy tự ghi byte UTF-8 chuẩn kèm độ dài rõ ràng.

Decoder cũng cần chính sách cho UTF-8 lỗi. `new String(bytes, UTF_8)` có thể thay byte không hợp lệ bằng ký tự thay thế; nếu giao thức yêu cầu **từ chối dữ liệu UTF-8 hỏng**, dùng `CharsetDecoder` với `CodingErrorAction.REPORT`.

## 6. Vì sao cần checksum? CRC32 kiểm tra được gì?

Ta thêm CRC-32 để phát hiện **nhiều dạng hỏng byte ngẫu nhiên** trong payload, ví dụ lỗi lưu trữ/truyền dữ liệu. CRC32 không phải encryption, không có key bí mật và **không chống giả mạo có chủ đích**: một kẻ tấn công có thể sửa payload rồi tính lại CRC.

Ví dụ:

~~~text
Payload gốc         → CRC32 0x12345678 (giá trị minh họa)
Một byte bị thay đổi → CRC32 tính lại thường khác
Reader so sánh CRC được lưu với CRC tính lại → báo lỗi
~~~

Ở định dạng bài tập này, CRC **chỉ bảo vệ payload**, không bảo vệ magic/version/length. Parser vẫn kiểm tra header hợp lệ và giới hạn length, nhưng nếu nghiệp vụ đòi phát hiện header bị sửa hoặc xác thực nguồn dữ liệu, cần bổ sung checksum bao phủ trường thích hợp hoặc sử dụng cơ chế xác thực mật mã như MAC/AEAD.

Đối với frame rất lớn, có thể tính CRC khi streaming từng chunk mà không giữ toàn bộ payload trong RAM. Trong ví dụ dưới, vì `MAX_PAYLOAD` chỉ 65.536 byte, việc giữ payload từng frame trong một mảng là hợp lý để minh họa.

## 7. Chương trình Java 17+ hoàn chỉnh: ghi và đọc ba frame

Ví dụ dưới đây tự xây serializer/deserializer với `DataOutputStream` và `DataInputStream`. Nó ghi ba frame vào một luồng byte:

1. Version 1 chứa `id=7`, `name="Alpha"`.
2. Version 99 là một frame **không hiểu payload**, nhưng có header/length/CRC hợp lệ, để thử chính sách skip.
3. Version 2 chứa `id=8`, `name="Beta"`, `priority=42`.

Sau đó chương trình cố ý cắt cụt một frame và sửa một byte payload để kiểm tra hai dạng lỗi. Mọi test dùng mảng byte trong RAM để stdout không phụ thuộc filesystem hay thời gian.

Lưu thành `VersionedBinaryFormatDemo.java`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public class VersionedBinaryFormatDemo {

    private static final int MAGIC = 0x4A42494E; // ASCII "JBIN"
    private static final int MAX_PAYLOAD = 65_536;

    record Frame(int version, byte[] payload) {}

    record Message(int id, String name, Integer priority) {}

    static byte[] encodeMessage(
            int version, int id, String name, Integer priority)
            throws IOException {

        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("Unsupported writer version");
        }
        if (version == 2 && priority == null) {
            throw new IllegalArgumentException("V2 needs priority");
        }

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 65_535) {
            throw new IOException("Name length exceeds u16");
        }

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(raw)) {
            payload.writeInt(id);
            payload.writeShort(nameBytes.length);
            payload.write(nameBytes);
            if (version == 2) {
                payload.writeInt(priority);
            }
        }

        byte[] result = raw.toByteArray();
        if (result.length > MAX_PAYLOAD) {
            throw new IOException("Payload exceeds maximum");
        }
        return result;
    }

    static void writeFrame(
            DataOutputStream out, int version, byte[] payload)
            throws IOException {

        if (version < 0 || version > 255
                || payload.length > MAX_PAYLOAD) {
            throw new IOException("Invalid version or payload size");
        }

        CRC32 crc = new CRC32();
        crc.update(payload);

        out.writeInt(MAGIC);
        out.writeByte(version);
        out.writeInt(payload.length);
        out.write(payload);
        out.writeInt((int) crc.getValue());
    }

    static Frame readFrame(DataInputStream in) throws IOException {
        // EOF hợp lệ CHỈ khi chưa đọc byte nào của header kế tiếp.
        int first = in.read();
        if (first == -1) {
            return null;
        }

        int magic = (first << 24)
                | (in.readUnsignedByte() << 16)
                | (in.readUnsignedByte() << 8)
                | in.readUnsignedByte();
        if (magic != MAGIC) {
            throw new IOException("Invalid magic");
        }

        int version = in.readUnsignedByte();
        int length = in.readInt();

        if (length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("Invalid payload length");
        }

        byte[] payload = new byte[length];
        in.readFully(payload); // EOF giữa payload => EOFException.

        int storedCrc = in.readInt(); // EOF giữa trailer => EOFException.
        CRC32 calculated = new CRC32();
        calculated.update(payload);

        if ((int) calculated.getValue() != storedCrc) {
            throw new IOException("CRC32 mismatch");
        }
        return new Frame(version, payload);
    }

    static Message decodeMessage(Frame frame) throws IOException {
        if (frame.version() != 1 && frame.version() != 2) {
            throw new IOException("Unknown message schema");
        }

        try (DataInputStream payload = new DataInputStream(
                new ByteArrayInputStream(frame.payload()))) {

            int id = payload.readInt();
            int nameLength = payload.readUnsignedShort();
            int extraBytes = frame.version() == 2 ? 4 : 0;

            if (nameLength > payload.available() - extraBytes) {
                throw new IOException("Invalid name length");
            }

            byte[] nameBytes = new byte[nameLength];
            payload.readFully(nameBytes);

            String name = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(nameBytes)).toString();

            Integer priority =
                    frame.version() == 2 ? payload.readInt() : null;

            if (payload.available() != 0) {
                throw new IOException("Unexpected trailing payload bytes");
            }

            return new Message(id, name, priority);
        }
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(destination)) {
            writeFrame(out, 1,
                    encodeMessage(1, 7, "Alpha", null));
            writeFrame(out, 99, new byte[] {1, 2, 3});
            writeFrame(out, 2,
                    encodeMessage(2, 8, "Beta", 42));
        }

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(destination.toByteArray()))) {
            Frame frame;
            while ((frame = readFrame(in)) != null) {
                if (frame.version() != 1 && frame.version() != 2) {
                    System.out.println(
                            "unknown version=" + frame.version()
                                    + " skipped");
                    continue;
                }

                Message message = decodeMessage(frame);
                String priority = message.priority() == null
                        ? "n/a"
                        : message.priority().toString();

                System.out.println("v" + frame.version()
                        + ": id=" + message.id()
                        + " name=" + message.name()
                        + " priority=" + priority);
            }
        }

        ByteArrayOutputStream one = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(one)) {
            writeFrame(out, 1,
                    encodeMessage(1, 1, "Test", null));
        }

        byte[] original = one.toByteArray();

        // Giả lập file bị cắt mất hai byte ở cuối CRC.
        byte[] truncated = Arrays.copyOf(
                original, original.length - 2);
        boolean truncationDetected = false;
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(truncated))) {
            readFrame(in);
        } catch (EOFException expected) {
            truncationDetected = true;
        }
        System.out.println(
                "truncated detected=" + truncationDetected);

        // Header dài 9 byte; byte đầu của payload ở offset 9.
        byte[] corrupted = original.clone();
        corrupted[9] ^= 1;
        boolean crcDetected = false;
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(corrupted))) {
            readFrame(in);
        } catch (IOException ex) {
            crcDetected = "CRC32 mismatch".equals(ex.getMessage());
        }
        System.out.println("crc mismatch detected=" + crcDetected);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac VersionedBinaryFormatDemo.java
java VersionedBinaryFormatDemo
~~~

Stdout:

~~~text
v1: id=7 name=Alpha priority=n/a
unknown version=99 skipped
v2: id=8 name=Beta priority=42
truncated detected=true
crc mismatch detected=true
~~~

### Phân tích chương trình theo từng giai đoạn

**Bước 1 — Ghi payload:** `encodeMessage` tạo một byte array theo schema v1/v2: `id`, `nameLen` và byte UTF-8, thêm `priority` nếu là v2. Hàm kiểm tra `nameLen` và tổng payload không vượt giới hạn.

**Bước 2 — Ghi frame:** `writeFrame` ghi magic, version, length, payload và CRC32. `DataOutputStream` quy định big-endian cho số nguyên nhiều byte. Một lời gọi tương ứng một frame có ranh giới rõ ràng.

**Bước 3 — Đọc header:** `readFrame` cho phép trả `null` **chỉ khi EOF ở đúng đầu frame tiếp theo**. Nếu chỉ còn 1–3 byte của magic hoặc thiếu version/length, các lệnh `readUnsignedByte`/`readInt` ném `EOFException`; đó là dữ liệu cắt cụt, không phải kết thúc hợp lệ.

**Bước 4 — Bảo vệ cấp phát:** `length < 0` hoặc `length > MAX_PAYLOAD` bị từ chối **trước** `new byte[length]`. Parser không tin một length do đối tác cung cấp.

**Bước 5 — Đọc payload và CRC:** `readFully` buộc đọc đủ đúng `length` byte; `readInt` buộc có đủ trailer. CRC32 tính lại khác checksum lưu sẽ ném `IOException`.

**Bước 6 — Phân nhánh theo version:** `decodeMessage` chỉ hiểu v1/v2. Version 99 đã được đọc trọn frame và kiểm tra CRC nên có thể bỏ qua như một đơn vị độc lập; v2 tiếp theo vẫn đọc đúng.

**Bước 7 — Kiểm tra schema bên trong payload:** Decoder dùng `readUnsignedShort` để nhận name length, kiểm tra còn đủ byte cho name **và trường priority nếu là v2**, giải mã UTF-8 strict, sau đó yêu cầu không còn byte dư.

**Bước 8 — Test sai dữ liệu:** Trường hợp cắt mất hai byte cuối CRC sinh `EOFException`. Trường hợp sửa một byte payload nhưng giữ nguyên trailer CRC sinh `IOException` có thông báo `CRC32 mismatch`. Không có dựa vào network timing hoặc thứ tự thread.

Ví dụ đã cố tình sử dụng `ByteArrayInputStream` để tái lập kết quả. Khi chuyển sang file hoặc socket thật, logic framing giữ nguyên, còn việc đệm, xử lý I/O và lifecycle phải thích ứng với nguồn.

## 8. Những quyết định thiết kế nên chốt trước khi triển khai

### Header cấp file hay header mỗi record?

Hai mô hình có thể được chọn:

**Mô hình A — header một lần cho cả file:**

~~~text
[FILE_MAGIC][FILE_FORMAT_VERSION]
[RECORD_LENGTH][RECORD]
[RECORD_LENGTH][RECORD]
...
~~~

Phù hợp khi toàn file luôn có cùng schema/định dạng hoặc khi ứng dụng muốn negotiate một phiên bản file trước khi đọc nội dung. Reader phải biết liệu mỗi record có cùng version hay có type/version riêng.

**Mô hình B — header mỗi frame**, như bài thực hành:

~~~text
[MAGIC][VERSION][LENGTH][PAYLOAD][CRC]
[MAGIC][VERSION][LENGTH][PAYLOAD][CRC]
...
~~~

Phù hợp khi cần trộn các version record hoặc nối frame độc lập. Đổi lại, overhead header và CRC lặp lại trên mỗi record.

Không có lựa chọn duy nhất đúng cho mọi ứng dụng. Điều quan trọng là **đặc tả nhất quán** và tính độc lập của từng frame nếu muốn skip những phiên bản không hiểu.

### Version cấp giao thức hay version cấp payload?

Một **protocol major version** có thể mô tả cú pháp header chung; một **payload schema version** mô tả trường bên trong một loại message. Trong ví dụ chúng cùng biểu diễn bằng một byte `version` vì định dạng đơn giản.

Nếu sau này muốn đổi cả cấu trúc frame (ví dụ length từ 32-bit thành 64-bit), reader cũ không nhất thiết còn nhận ra ranh giới. Hãy xem đó là **thay đổi giao thức lớn**, không chỉ tăng version payload từ 2 lên 3.

### Type và flags có cần không?

Nếu file chứa nhiều loại record, có thể mở rộng header thành:

~~~text
[MAGIC][FORMAT_VERSION][RECORD_TYPE][FLAGS][PAYLOAD_LENGTH][PAYLOAD][CRC]
~~~

Khi đó đặc tả phải bổ sung:

- Các giá trị type/flag hợp lệ.
- Unknown type/flag có được skip không.
- Những flag bắt buộc người đọc hiểu để giải mã đúng payload.
- Quy tắc dành riêng bit tương lai và thứ tự xử lý compression/encryption.
- Kích thước tối đa sau giải nén nếu payload được nén.

Không thêm type/flag một cách tùy tiện mà quên cập nhật parser và test tương thích.

## 9. DataInputStream so với ByteBuffer/FileChannel

Java cung cấp nhiều mức trừu tượng:

| Công cụ | Hữu ích khi nào? | Điểm cần chú ý |
| --- | --- | --- |
| `DataOutputStream` / `DataInputStream` | Ghi/đọc primitive trên OutputStream/InputStream | Big-endian; `readFully` phù hợp với frame length |
| `ByteBuffer` | Đóng/gỡ byte từ mảng/direct buffer | Phải quản lý `position/limit/flip` và byte order |
| `FileChannel` | Random access, đọc/ghi theo offset cho file lớn | `read`/`write` có thể chuyển một phần; phải kiểm tra vòng lặp |
| `SocketChannel` non-blocking | Giao thức mạng với Selector | Có thể trả 0 byte; state machine phải tích lũy header/payload qua nhiều lượt |
| `BufferedInputStream` | Giảm lần gọi I/O nhỏ khi đọc stream | Không làm thay đổi protocol framing |
| `CRC32` | Phát hiện nhiều dạng lỗi byte ngẫu nhiên | Không xác thực/chống giả mạo |

Trong demo, `DataInputStream` rất phù hợp vì `readFully` đợi/đọc đủ số byte yêu cầu hoặc ném EOF. Với non-blocking `SocketChannel`, không được giả định một lần `read(buffer)` sẽ trả đủ 9 byte header hay đủ payload. Cần một **state machine**:

~~~text
NEED_HEADER
    |
    | nhận đủ 9 byte
    v
VALIDATE_LENGTH
    |
    | length hợp lệ, cấp bộ đệm có giới hạn
    v
NEED_PAYLOAD_AND_CRC
    |
    | nhận đủ length + 4 byte
    v
VERIFY_CRC
    |
    v
DECODE_OR_SKIP
    |
    v
NEED_HEADER (frame tiếp theo)
~~~

Một request TCP có thể bị chia thành nhiều packet, hoặc nhiều frame có thể đến trong một lần `read`. Giao thức dựa trên length làm rõ cách tích lũy, không phụ thuộc ranh giới packet.

## 10. File rất lớn: nên streaming theo frame

Một file có thể lớn hàng chục gigabyte nhưng mỗi frame chỉ dài vài kilobyte. Đừng dùng `Files.readAllBytes` để nạp cả file chỉ vì định dạng là nhị phân.

Với `InputStream`/`DataInputStream`, parser có thể đọc từng frame và xử lý ngay. Với `FileChannel`, có thể đọc vào buffer có giới hạn, giữ offset và phân tích incremental.

Quy tắc:

- **Giới hạn payload của mỗi frame**, tổng số frame và tổng byte xử lý theo yêu cầu.
- Đừng cấp phát `new byte[fileSize]`; xử lý từng frame.
- Bảo vệ số frame đồng thời khi nhiều client gửi.
- Không đưa các tham chiếu tới buffer có thể bị tái sử dụng vào job asynchronous mà không có ownership rõ ràng.
- Theo dõi bytes read, frames rejected và nguyên nhân lỗi để có thể vận hành.
- Khi frame lớn thật sự cần streaming, tính CRC incremental và dùng bounded input để không đọc lấn sang frame tiếp theo.

Trong ví dụ, ta dùng bộ đệm payload tối đa 65.536 byte, đủ nhỏ để minh họa độc lập mà không cần cơ chế xử lý hàng gigabyte.

## 11. Truncation, crash và giao thức append

Có một khác biệt quan trọng giữa **EOF hợp lệ** và **file bị cắt giữa record**:

~~~text
Hợp lệ:
[frame 1 hoàn chỉnh][frame 2 hoàn chỉnh][EOF]

Không hợp lệ:
[frame 1 hoàn chỉnh][nửa header frame 2][EOF]

Không hợp lệ:
[frame 1 hoàn chỉnh][header frame 2][nửa payload][EOF]
~~~

Vì vậy `readFrame` trả `null` chỉ khi **chưa đọc byte nào của frame kế tiếp**. Sau khi đã thấy bất kỳ byte header nào, thiếu dữ liệu còn lại phải là lỗi hoặc tình trạng cần đợi thêm byte nếu đang đọc mạng chưa đóng.

Nếu ứng dụng append record vào file và bị mất điện/JVM crash giữa một lần ghi, file có thể kết thúc bằng một frame chưa hoàn chỉnh. Những chiến lược production thường gặp:

1. Đọc các frame hoàn chỉnh từ đầu và ghi nhận **offset commit hợp lệ cuối cùng**.
2. Nếu phần cuối bị cắt, từ chối toàn file hoặc cô lập phần đuôi dở theo chính sách.
3. Với cấu trúc append-only log, có thể phục hồi bằng cách **truncate về offset cuối hợp lệ**, nhưng chỉ khi đã kiểm chứng độc lập, không có writer khác đang hoạt động và có cơ chế lưu trạng thái phù hợp.
4. Nếu cần durability, dùng `FileChannel.force(...)` theo chính sách và xem xét thứ tự ghi, metadata và filesystem.
5. Nếu cần cập nhật atomic cả file, cân nhắc ghi tệp tạm hoàn chỉnh rồi move/rename phù hợp trên cùng filesystem, với lưu ý khả năng `ATOMIC_MOVE` phụ thuộc provider.

**Một length-prefix không tự biến nhiều write thành giao dịch nguyên tử.** Nó giúp phát hiện record thiếu dữ liệu; khả năng phục hồi và durability là quyết định khác.

## 12. Khả năng tương thích: backward, forward và schema evolution

Có ba tình huống cần kiểm thử độc lập:

| Tình huống | Kỳ vọng nếu thiết kế hỗ trợ |
| --- | --- |
| Reader mới đọc record v1 | Đọc được bằng decoder v1 |
| Reader mới đọc record v2 | Đọc được bằng decoder v2 |
| Reader cũ gặp record v2/v99 | Skip an toàn nếu protocol cho phép; hoặc lỗi rõ ràng |

### Backward compatibility

Reader mới có thể hiểu các version cũ. Trong demo, decoder xử lý v1 và v2. Đó là **khả năng đọc dữ liệu cũ**, không đồng nghĩa mọi writer cũ sẽ hiểu dữ liệu do writer mới tạo.

### Forward compatibility

Reader cũ có thể chịu được các thay đổi mới trong phạm vi được đặc tả. Ví dụ parser đọc length, kiểm tra checksum, **skip unknown version** để tiếp tục frame sau. Nhưng nếu unknown frame thay đổi state, như thiết lập dictionary/encryption cho frame tiếp theo, skip có thể **không an toàn**.

### Migrations và defaults

Nếu cần đưa record v1 lên data model mới, một cách là chọn giá trị default hợp lý cho `priority` khi đọc v1, hoặc để `null` nghĩa là “chưa có”. **Không lấy 0 làm default mà không nói rõ semantics**, vì 0 có thể là một mức priority hợp lệ.

Tương tự, khi bỏ một field ở v3, nên viết rõ v1/v2/v3 là schema nào, không chỉ coi tất cả byte sau tên là field tùy chọn không có marker.

## 13. An toàn trước đầu vào không tin cậy

Một parser nhị phân cần giả định file/input có thể bị lỗi hoặc được tạo bởi đối tác không tin cậy. Thứ tự xử lý an toàn:

1. **Kiểm tra magic** trước khi hiểu payload.
2. **Đọc version có giới hạn** và xác định chính sách unknown.
3. **Kiểm tra length** không âm, không vượt max, không gây integer overflow khi tính `header + length + trailer`.
4. **Giới hạn cấp phát** trước khi tạo buffer.
5. **Đọc đầy đủ đúng length** và xử lý EOF giữa frame như lỗi.
6. **Kiểm tra checksum** theo đúng phạm vi byte đã định nghĩa.
7. **Kiểm tra schema nội bộ**, length của chuỗi/mảng và UTF-8 hợp lệ.
8. **Từ chối byte dư** nếu schema version đã biết không cho phép.
9. **Giới hạn tổng tải**: số frame, tốc độ, deadline, nesting, số kết nối, memory budget và CPU.
10. **Không xem CRC như xác thực**: nếu cần chống attacker giả mạo, dùng MAC/AEAD/chữ ký theo giao thức an toàn phù hợp.

Đặc biệt, đoạn mã:

~~~java
int length = in.readInt();
byte[] payload = new byte[length]; // NGUY HIỂM nếu chưa kiểm tra
~~~

là anti-pattern. Một length âm gây lỗi cấp phát; một length cực lớn có thể dẫn đến `OutOfMemoryError`. Trong bài demo, `MAX_PAYLOAD` được kiểm tra trước allocation. Trong production, còn phải hạn chế số frame đang được giữ đồng thời.

### Length lồng nhau

Frame đã có `payloadLength`, nhưng payload có thể lại chứa `nameLen`, `arrayLength` hoặc danh sách element. **Mọi độ dài lồng nhau** đều phải được kiểm tra so với byte còn lại và giới hạn nghiệp vụ; không vì frame ngoài hợp lệ mà tin mọi trường trong payload đều hợp lệ.

Ví dụ decoder demo xác nhận `nameLength <= remaining - extraBytes`. Nếu thêm một danh sách bản ghi con, parser phải giới hạn count và kiểm tra số byte cần thiết trước khi lặp hoặc cấp phát.

## 14. ByteBuffer: ví dụ ngắn về header big-endian

Nếu muốn dựng header bằng `ByteBuffer` thay vì `DataOutputStream`:

~~~java
ByteBuffer header = ByteBuffer.allocate(9)
        .order(ByteOrder.BIG_ENDIAN);

header.putInt(0x4A42494E); // "JBIN"
header.put((byte) 1);      // version
header.putInt(123);       // payloadLength

header.flip();             // Chuẩn bị cho channel.write(header)
~~~

Đây là **mảnh code minh họa**; import `java.nio.ByteBuffer` và `java.nio.ByteOrder`, và không tự gửi dữ liệu.

`flip()` là bước quan trọng: sau `put`, position đứng ở cuối vùng đã ghi; nếu quên `flip` mà gọi `channel.write(header)`, có thể truyền 0 byte hoặc dữ liệu không mong muốn. Với `SocketChannel` non-blocking, ngay cả sau `flip`, `write` có thể gửi một phần header; cần giữ buffer chưa gửi hết.

Reader phía ByteBuffer:

~~~java
header.order(ByteOrder.BIG_ENDIAN);
int magic = header.getInt();
int version = Byte.toUnsignedInt(header.get());
int length = header.getInt();
~~~

Chỉ được gọi các lệnh `get` sau khi **đã nhận đủ 9 byte**; nếu không, `BufferUnderflowException` có thể xuất hiện. Đọc và kiểm tra length trước khi cấp phát buffer payload. Những byte này cùng đặc tả với `DataInputStream` demo.

## 15. Những sai lầm phổ biến

**Sai lầm 1 — Không có length-prefix.** Khi ghép nhiều record nhị phân, parser không biết ranh giới. Giải pháp: đặc tả framing theo độ dài hoặc một cơ chế đóng gói đủ chặt khác.

**Sai lầm 2 — Chỉ có length mà không version.** Khi thêm một field, parser cũ có thể diễn giải nhầm byte. Giải pháp: schema version và chính sách tương thích.

**Sai lầm 3 — Quên quy định byte order.** Java, C và hệ điều hành có thể dùng cách biểu diễn khác. Giải pháp: định nghĩa big-endian/little-endian trong đặc tả và test bytes.

**Sai lầm 4 — Dùng `String.length()` làm độ dài UTF-8.** Ký tự nhiều byte khiến reader lệch offset. Giải pháp: encode UTF-8 rồi ghi độ dài byte.

**Sai lầm 5 — Dùng `writeUTF/readUTF` cho giao thức UTF-8 chuẩn.** Đó là modified UTF-8 có framing riêng. Giải pháp: ghi u16/u32 byteLength rồi ghi byte UTF-8 chuẩn.

**Sai lầm 6 — Cấp phát theo length chưa kiểm tra.** Có thể tạo mảng khổng lồ hoặc âm. Giải pháp: kiểm tra phạm vi trước allocation.

**Sai lầm 7 — Coi EOF giữa payload là kết thúc file hợp lệ.** Đó là frame bị cắt cụt. Giải pháp: phân biệt EOF đúng ranh giới và EOF giữa frame.

**Sai lầm 8 — Gặp unknown version thì đọc payload bằng schema gần giống.** Có thể sai hoàn toàn. Giải pháp: skip toàn frame nếu giao thức cho phép, hoặc lỗi rõ ràng.

**Sai lầm 9 — Không kiểm tra byte dư của schema đã biết.** Parser có thể chấp nhận record encode lệch schema. Giải pháp: yêu cầu payload được tiêu thụ trọn vẹn khi version cố định.

**Sai lầm 10 — Nghĩ CRC bảo vệ chống sửa giả mạo.** CRC32 không phải MAC. Giải pháp: dùng xác thực mật mã nếu có attacker.

**Sai lầm 11 — Giả định một lần đọc TCP trả đủ một frame.** TCP là byte stream; cần state machine tích lũy header và payload.

**Sai lầm 12 — Không test nhiều frame nối tiếp.** Parser có thể đọc lấn sang frame sau hoặc không reset state đúng. Giải pháp: round-trip nhiều version, unknown version ở giữa, frame cuối, EOF và dữ liệu hỏng.

## 16. Bài tập thực hành và câu hỏi tự kiểm tra

### Bài tập 1 — Đọc byte theo đặc tả

Với header:

~~~text
4A 42 49 4E 01 00 00 00 0B
~~~

Hãy xác định:

- Magic: **`JBIN`**.
- Version: **1**.
- Payload length: **11 byte**.
- Tổng frame length theo cấu trúc bài học: **13 + 11 = 24 byte**, gồm CRC32 4 byte ở cuối.

Đây chỉ là **header**, chưa có payload/CRC đi kèm để kết luận cả frame hợp lệ.

### Bài tập 2 — Version 1 và version 2

Nếu v1 có id=7 và name `"Alpha"`:

- id: 4 byte.
- name length: 2 byte.
- UTF-8 `Alpha`: 5 byte.
- Tổng payload v1: **11 byte**.
- Tổng frame: **24 byte**.

Nếu v2 có cùng name/id và thêm priority i32 thì payload là **15 byte**, frame là **28 byte**. Không lấy số ký tự Java thay cho số byte UTF-8 khi có ký tự ngoài ASCII.

### Bài tập 3 — Bổ sung loại message

Thêm trường type vào header: version chung, type 1 byte, length 4 byte. Cần:

1. Cập nhật header size từ 9 thành 10 byte.
2. Viết lại encoder/decoder đồng bộ.
3. Xác định type không được hỗ trợ phải skip hay reject.
4. Cân nhắc cách phân biệt tệp dùng header cũ với header mới (ví dụ thay format major version/magic), không âm thầm đọc sai.

### Bài tập 4 — Trường hợp length không hợp lệ

Viết test với `payloadLength = -1`, `65_537`, `0`, `65_536`. Trong demo:

- -1 và 65.537: **từ chối**.
- 0 và 65.536: **nằm trong phạm vi length cho phép** ở cấp frame; tuy vậy payload 0 byte không đủ schema v1/v2 và sẽ bị decoder của các version đó từ chối.

### Câu hỏi

1. Version dùng để làm gì? **Chọn cấu trúc/ý nghĩa trường payload**.
2. Length dùng để làm gì? **Xác định ranh giới frame, giới hạn cấp phát và hỗ trợ skip**.
3. Có thể chỉ dùng magic để tìm frame kế tiếp không? **Không an toàn theo mặc định**, vì magic có thể xuất hiện trong payload.
4. Byte order của `DataOutputStream.writeInt` là gì? **Big-endian**.
5. UTF-8 byte length có luôn bằng `String.length()`? **Không**.
6. Reader gặp unknown version mà vẫn biết frame length, có luôn được skip không? **Chỉ nếu hợp đồng giao thức cho phép và frame độc lập**.
7. `readFully` có trả ít byte mà không báo lỗi khi EOF giữa payload không? **Không**, nó ném `EOFException` khi không đọc đủ.
8. CRC32 có bảo mật trước attacker không? **Không**, chỉ phát hiện nhiều dạng lỗi dữ liệu ngẫu nhiên.
9. Đọc tới EOF ở đúng ranh giới giữa hai frame khác gì EOF sau vài byte header? **Trường hợp đầu có thể hợp lệ; trường hợp sau là frame bị cắt cụt**.
10. Nếu length = 2 GiB và reader có ít RAM thì nên làm gì? **Từ chối theo giới hạn** hoặc thiết kế streaming có budget rõ ràng; không cấp phát trực tiếp.
11. Khi thêm field tùy chọn cần mô tả gì? **Version, presence flag/schema, thứ tự, kích thước, default và chính sách unknown**.
12. Để lưu file có thể bị mất điện giữa lần append, cần gì ngoài length? **Phát hiện record dở, commit/recovery rule và durability policy nếu cần**.

**Ghi nhớ:** một định dạng nhị phân có **magic + version + length** giúp parser nhận biết loại dữ liệu và ranh giới record; thêm schema byte order/UTF-8, giới hạn độ dài, checksum và quy tắc lỗi thì mới thành một giao thức có thể vận hành. Luôn **kiểm tra length trước khi cấp phát**, phân biệt EOF hợp lệ với dữ liệu bị cắt cụt, và chỉ skip phiên bản chưa biết khi có cơ sở từ đặc tả. Một ví dụ round-trip đúng phải được kiểm thử bằng nhiều phiên bản, nhiều frame, đầu vào sai và khôi phục sau lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-005">Xây dựng công cụ duyệt cây và lọc tệp</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Xây dựng công cụ duyệt cây và lọc tệp bằng Java NIO.2

## 1. Bài toán và mục tiêu

Bạn cần một công cụ dòng lệnh để duyệt một thư mục gốc cùng các thư mục con, chỉ in những **tệp thông thường** đáp ứng điều kiện lọc. Công cụ phải hữu ích với cây thư mục lớn, dễ xác minh kết quả và chịu được một số lỗi truy cập mà không làm mất mọi kết quả đã tìm được.

Trong bài thực hành này, chúng ta sẽ viết chương trình `TreeFilter` bằng Java 17+ với các chức năng:

- Nhận **thư mục gốc** từ đối số dòng lệnh.
- Duyệt đệ quy bằng `Files.walkFileTree` và `SimpleFileVisitor`.
- Lọc theo **glob tên tệp**, kích thước nhỏ nhất/lớn nhất (byte), giới hạn **độ sâu**, và tùy chọn có/không xét mục ẩn.
- **Không đi theo symbolic link mặc định**; chỉ theo link khi người dùng bật `--follow-links`.
- Hiển thị đường dẫn tương đối cùng kích thước byte trên stdout, thống kê và cảnh báo trên stderr.
- Tiếp tục sau lỗi truy cập từng nhánh nếu có thể, ghi nhận số lỗi và trả mã thoát phù hợp.
- Tạo dữ liệu kiểm thử nhỏ để xác minh đầu ra, sau đó thảo luận giới hạn thực tế với tệp lớn và cây nhiều nhánh.

Đây là công cụ **chỉ đọc nội dung cấu trúc tệp trong lúc duyệt**; nó không xóa, sửa, di chuyển hay đổi tên tệp. Chương trình sẽ xem metadata (loại tệp, kích thước, thuộc tính ẩn) và tên tệp; nó **không mở/đọc toàn bộ nội dung** để tìm từ khóa. Nếu muốn lọc theo nội dung, cần bổ sung một bước streaming riêng.

## 2. Thiết kế giao diện CLI

Cú pháp đề xuất:

~~~text
java TreeFilter <root> [options]

Options:
  --glob <pattern>     Glob áp dụng trên TÊN TỆP (mặc định: *)
  --min-bytes <number> Kích thước tối thiểu, đơn vị byte (mặc định: 0)
  --max-bytes <number> Kích thước tối đa, đơn vị byte (mặc định: Long.MAX_VALUE)
  --max-depth <number> Giới hạn độ sâu duyệt, số nguyên >= 1
  --include-hidden     Cho phép cả tệp/thư mục được OS nhận diện là ẩn
  --follow-links       Cho phép đi theo symbolic link; mặc định không
~~~

Các lựa chọn được **AND** với nhau. Ví dụ lệnh:

~~~bash
java TreeFilter project --glob "*.java" --min-bytes 100 --max-bytes 200000 --max-depth 10
~~~

Chương trình tìm file Java có tên khớp `*.java`, từ 100 đến 200.000 byte, trong phạm vi cây bắt đầu tại `project` với độ sâu tối đa 10. Mẫu glob được áp dụng lên **`file.getFileName()`** (chỉ thành phần tên cuối). Vì vậy `*.java` có thể khớp `src/App.java`, còn `src/*.java` **không** khớp tên `App.java`; muốn lọc theo đường dẫn tương đối thì phải đổi cách áp dụng `PathMatcher`.

**Quy ước độ sâu:** gốc có độ sâu 0, tệp con trực tiếp có độ sâu 1, tệp trong thư mục con có độ sâu 2, v.v. `--max-depth 1` chỉ xét các mục trực tiếp bên dưới gốc. Thư mục ở độ sâu bằng giới hạn không được đi sâu thêm. Đây là giới hạn duyệt cây của `walkFileTree`, không phải số cấp thư mục **được in**.

**Định dạng đầu ra:** mỗi dòng kết quả chứa `đường_dẫn_tương_đối<TAB>kích_thước_byte`, tính theo `Path.toString()` của hệ điều hành. Dấu phân cách thư mục có thể là `/` trên Unix hoặc `\` trên Windows. Cách xuất TSV đơn giản thích hợp để học và thao tác nhanh; tên tệp chứa TAB hoặc xuống dòng cần escape/định dạng JSON nếu xây dựng công cụ tích hợp máy với máy.

## 3. Chọn API duyệt cây

Java NIO.2 trong `java.nio.file` hỗ trợ nhiều cách duyệt:

| API | Phạm vi | Đặc điểm cần nhớ |
| --- | --- | --- |
| `Files.list(dir)` | Một cấp | Trả `Stream<Path>`; phải đóng stream sau khi dùng |
| `Files.walk(root, depth)` | Nhiều cấp | `Stream<Path>` lazy; cần `try-with-resources` để đóng |
| `Files.find(root, depth, matcher)` | Nhiều cấp | Predicate nhận `Path` và `BasicFileAttributes`; phải đóng stream |
| `Files.walkFileTree(root, visitor)` | Nhiều cấp | Callback theo sự kiện; kiểm soát `SKIP_SUBTREE`, `TERMINATE`, lỗi truy cập và link |

`Files.walkFileTree` phù hợp khi muốn điều khiển traversal rõ ràng mà không phải giữ toàn bộ danh sách file trong RAM. `SimpleFileVisitor<Path>` cung cấp các phương thức mặc định cho bốn mốc chính:

1. `preVisitDirectory(dir, attrs)`: trước khi vào một thư mục; có thể trả `SKIP_SUBTREE` để bỏ cả nhánh.
2. `visitFile(file, attrs)`: khi gặp một mục được xử lý như file; `attrs.isRegularFile()` cho biết đó có phải tệp thông thường hay không.
3. `visitFileFailed(file, exception)`: lỗi lấy thuộc tính hoặc không thể duyệt mục, trong đó có tình huống symbolic link tạo chu trình.
4. `postVisitDirectory(dir, exception)`: sau khi xử lý thư mục; `exception` khác null nếu traversal của thư mục gặp lỗi.

Các giá trị `FileVisitResult` phổ biến là `CONTINUE`, `SKIP_SUBTREE`, `SKIP_SIBLINGS` và `TERMINATE`. Trong bài này ta dùng `CONTINUE` và `SKIP_SUBTREE`; nếu không cần hủy toàn bộ quá trình thì không trả `TERMINATE` chỉ vì một file không khớp filter.

## 4. Triển khai `TreeFilter.java` đầy đủ

Lưu nguyên mã sau vào **`TreeFilter.java`**. Chương trình dùng các API chuẩn của JDK, không cần Maven, Gradle hoặc thư viện bên thứ ba.

~~~java
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Locale;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

public class TreeFilter {
    private record Config(
            Path root,
            PathMatcher nameMatcher,
            long minBytes,
            long maxBytes,
            int maxDepth,
            boolean includeHidden,
            boolean followLinks) {}

    private static final class Stats {
        long directories;
        long regularFiles;
        long matches;
        long errors;
    }

    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            Stats stats = new Stats();

            EnumSet<FileVisitOption> options = config.followLinks()
                    ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                    : EnumSet.noneOf(FileVisitOption.class);

            Files.walkFileTree(
                    config.root(),
                    options,
                    config.maxDepth(),
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            // Giữ quyền duyệt root; các thư mục con ẩn
                            // được loại bỏ cùng toàn bộ nhánh của chúng.
                            if (!dir.equals(config.root())
                                    && !config.includeHidden()) {
                                try {
                                    if (Files.isHidden(dir)) {
                                        return SKIP_SUBTREE;
                                    }
                                } catch (IOException e) {
                                    stats.errors++;
                                    reportError(dir, e);
                                    return SKIP_SUBTREE;
                                }
                            }
                            stats.directories++;
                            return CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile()) {
                                return CONTINUE;
                            }
                            stats.regularFiles++;

                            if (!config.includeHidden()) {
                                try {
                                    if (Files.isHidden(file)) {
                                        return CONTINUE;
                                    }
                                } catch (IOException e) {
                                    stats.errors++;
                                    reportError(file, e);
                                    return CONTINUE;
                                }
                            }

                            if (!config.nameMatcher().matches(file.getFileName())) {
                                return CONTINUE;
                            }

                            long size = attrs.size();
                            if (size < config.minBytes()
                                    || size > config.maxBytes()) {
                                return CONTINUE;
                            }

                            Path relative = config.root().relativize(file);
                            System.out.printf(Locale.ROOT, "%s\t%d%n",
                                    relative, size);
                            stats.matches++;
                            return CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                Path file, IOException error) {
                            stats.errors++;
                            reportError(file, error);
                            return CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(
                                Path dir, IOException error) {
                            if (error != null) {
                                stats.errors++;
                                reportError(dir, error);
                            }
                            return CONTINUE;
                        }
                    });

            System.err.printf(Locale.ROOT,
                    "Visited directories=%d, regular files=%d, matches=%d, errors=%d%n",
                    stats.directories, stats.regularFiles,
                    stats.matches, stats.errors);

            if (stats.errors != 0) {
                System.exit(2); // Có kết quả từng phần kèm lỗi truy cập.
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Traversal failed: " + e);
            System.exit(2);
        }
    }

    private static Config parseArgs(String[] args) {
        if (args.length == 0 || args[0].startsWith("--")) {
            throw new IllegalArgumentException("Root directory is required");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        // Bài này yêu cầu root là thư mục thực, không phải symlink.
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "Not a directory: " + root);
        }

        String glob = "*";
        long minBytes = 0L;
        long maxBytes = Long.MAX_VALUE;
        int maxDepth = Integer.MAX_VALUE;
        boolean includeHidden = false;
        boolean followLinks = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--glob" ->
                    glob = nextValue(args, ++i, "--glob");
                case "--min-bytes" ->
                    minBytes = Long.parseLong(
                            nextValue(args, ++i, "--min-bytes"));
                case "--max-bytes" ->
                    maxBytes = Long.parseLong(
                            nextValue(args, ++i, "--max-bytes"));
                case "--max-depth" ->
                    maxDepth = Integer.parseInt(
                            nextValue(args, ++i, "--max-depth"));
                case "--include-hidden" -> includeHidden = true;
                case "--follow-links" -> followLinks = true;
                default -> throw new IllegalArgumentException(
                        "Unknown option: " + args[i]);
            }
        }

        if (minBytes < 0 || maxBytes < 0 || maxBytes < minBytes) {
            throw new IllegalArgumentException("Invalid size range");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException(
                    "max-depth must be >= 1");
        }

        // Glob match trên tên file, không match toàn bộ relative path.
        PathMatcher matcher = FileSystems.getDefault()
                .getPathMatcher("glob:" + glob);
        return new Config(root, matcher, minBytes, maxBytes,
                maxDepth, includeHidden, followLinks);
    }

    private static String nextValue(
            String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(
                    "Missing value for " + option);
        }
        return args[index];
    }

    private static void reportError(Path path, IOException error) {
        System.err.println("WARN " + path + ": " + error);
    }

    private static void printUsage() {
        System.err.println(
                "Usage: java TreeFilter <root> "
                + "[--glob <pattern>] "
                + "[--min-bytes <n>] [--max-bytes <n>] "
                + "[--max-depth <n>] "
                + "[--include-hidden] [--follow-links]");
    }
}
~~~

Biên dịch và chạy với Java 17 hoặc mới hơn:

~~~bash
javac TreeFilter.java
java TreeFilter . --glob "*.java" --max-depth 5
~~~

Chạy ở thư mục bất kỳ chứa mã Java để xem kết quả thực tế; số file và kích thước phụ thuộc thư mục đó. Dùng dấu ngoặc kép quanh glob để tránh shell Unix tự mở rộng `*.java` thành danh sách file trước khi Java nhận tham số.

### 4.1. Giải thích cơ chế chính

**`Path` và `PathMatcher`.** `Path.of(args[0])` tạo đối tượng đường dẫn theo filesystem provider mặc định. `toAbsolutePath().normalize()` chuyển sang đường dẫn tuyệt đối và đơn giản hóa các thành phần `.`, `..`; nó **không** kiểm chứng real path hoặc ngăn link thoát khỏi root. `PathMatcher` nhận cú pháp `glob:...`, hỗ trợ wildcard như `*`, `?` và `{java,kt}` tùy cú pháp glob của provider. Ta cố ý match `file.getFileName()` để option có ý nghĩa thống nhất: filter theo *tên* ở mọi độ sâu.

**Duyệt theo sự kiện.** Visitor không gom mọi `Path` vào `List` rồi lọc sau; khi tìm thấy một file khớp, nó in ngay. Bộ nhớ tăng chủ yếu theo độ sâu traversal và trạng thái của provider, không theo số lượng kết quả đã in. Output **không bảo đảm thứ tự từ điển** vì thứ tự duyệt do filesystem provider quyết định.

**Metadata.** `BasicFileAttributes attrs` được cung cấp khi visitor gặp file; `attrs.isRegularFile()` loại directory và các loại mục không phải regular file; `attrs.size()` lấy kích thước trong metadata. Nếu file bị sửa trong lúc duyệt, kích thước và nội dung thực tế có thể thay đổi sau thời điểm đọc attributes.

**Hidden.** Khi `includeHidden` bằng false, `preVisitDirectory` dùng `Files.isHidden(dir)` để bỏ cả nhánh thư mục ẩn và `visitFile` bỏ tệp ẩn. Quy tắc hidden khác giữa các hệ điều hành/provider: Unix thường dựa vào tên bắt đầu bằng dấu chấm, Windows có thuộc tính hidden. Do đó “hidden” là thuộc tính môi trường, không phải quy tắc Unicode/tên file do công cụ tự suy đoán. Thư mục gốc được duyệt ngay cả khi bản thân gốc được đánh dấu hidden; quy tắc bỏ hidden áp dụng các mục bên dưới gốc.

**Lọc kích thước và tên.** Ta áp dụng so khớp tên rồi kiểm tra `attrs.size()` trong khoảng bao gồm cả hai đầu mút: `minBytes <= size <= maxBytes`. Tệp có kích thước 0 hợp lệ nếu không bị điều kiện min loại. Với CLI cơ bản này, các đối số size là số nguyên hệ thập phân biểu diễn byte, chưa hỗ trợ hậu tố KB/MiB.

**Xử lý lỗi.** Nếu không được phép duyệt một nhánh, filesystem mất kết nối, hoặc symbolic link tạo vòng, visitor ghi cảnh báo vào stderr, tăng `errors` và thường tiếp tục duyệt phần khác. Mã thoát `2` báo đã có lỗi I/O, kể cả khi một số kết quả đã in; `1` báo đối số sai, còn `0` là hoàn tất không có lỗi được ghi nhận. Một số lỗi unchecked, lỗi tiến trình hoặc tác dụng phụ của filesystem provider có thể chấm dứt chương trình ngoài các nhánh `IOException` đã xử lý.

## 5. Tạo fixture nhỏ để tự kiểm tra

Để không phụ thuộc nội dung thư mục hiện có, tạo thêm một chương trình Java độc lập **`CreateDemoTree.java`**. Nó tạo cây tệp nhỏ trong thư mục `demo-tree` tương đối với working directory hiện tại.

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreateDemoTree {
    public static void main(String[] args) throws IOException {
        Path root = Path.of("demo-tree");

        Files.createDirectories(root.resolve("logs/nested"));
        Files.createDirectories(root.resolve("docs"));

        Files.writeString(root.resolve("logs/a.log"), "ERROR");
        Files.writeString(root.resolve("logs/c.log"), "INFO\n");
        Files.writeString(root.resolve("logs/nested/b.log"), "WARN");
        Files.writeString(root.resolve("docs/readme.txt"), "Hello");

        System.out.println("Created demo-tree");
    }
}
~~~

Tệp `a.log` và `c.log` có đúng **5 byte** với nội dung ASCII/UTF-8 trong ví dụ, `b.log` có 4 byte và `readme.txt` có 5 byte. Chạy trong **thư mục làm việc sạch, chưa có `demo-tree` hoặc file khác trong cây** để các thống kê chính xác:

~~~bash
javac TreeFilter.java CreateDemoTree.java
java CreateDemoTree
java TreeFilter demo-tree --glob "*.log" --min-bytes 5 --max-bytes 5 --max-depth 4
~~~

Stdout của `CreateDemoTree`:

~~~text
Created demo-tree
~~~

Stdout của `TreeFilter` gồm **hai dòng sau**, nhưng thứ tự có thể đảo vì traversal không sắp xếp:

~~~text
logs/a.log	5
logs/c.log	5
~~~

Trên Windows, dấu phân cách trong hai đường dẫn là `\` thay vì `/`. Dòng thống kê stderr trên một filesystem thông thường, khi fixture đầy đủ và không có lỗi truy cập, là:

~~~text
Visited directories=4, regular files=4, matches=2, errors=0
~~~

Phân tích: bốn thư mục gồm root, `logs`, `logs/nested` và `docs`. Có bốn tệp thông thường; chỉ `a.log` và `c.log` đồng thời thỏa glob `*.log` và khoảng size `[5,5]`. `b.log` bị loại vì 4 byte, `readme.txt` vì không khớp glob. Tất cả thuộc độ sâu không vượt 4.

### 5.1. Thử từng bộ lọc riêng

**Chỉ xem log không giới hạn kích thước:**

~~~bash
java TreeFilter demo-tree --glob "*.log"
~~~

Dự kiến `matches=3`, các file `a.log`, `b.log`, `c.log` đều được in (thứ tự không cố định).

**Giới hạn độ sâu:**

~~~bash
java TreeFilter demo-tree --glob "*.log" --max-depth 2
~~~

Dự kiến `matches=2`: bỏ `logs/nested/b.log` vì nó nằm ở độ sâu 3. Theo semantics của `walkFileTree` với `maxDepth=2`, thư mục ở depth 2 không được descend để thăm file depth 3.

**Giới hạn kích thước:**

~~~bash
java TreeFilter demo-tree --glob "*.log" --min-bytes 4 --max-bytes 4
~~~

Dự kiến `matches=1`, chỉ còn `logs/nested/b.log` (4 byte).

**Đường dẫn gốc không tồn tại:**

~~~bash
java TreeFilter does-not-exist
~~~

Dự kiến in `Invalid arguments: Not a directory: ...` kèm usage trên stderr và thoát mã `1`. Phần đường dẫn tuyệt đối tùy máy. Để tự kiểm tra mã thoát, dùng `echo $?` trong Bash hoặc `$LASTEXITCODE` trong PowerShell.

### 5.2. Lưu ý về fixture

`Files.writeString` tạo mới hoặc ghi đè/truncate tệp có sẵn theo hành vi mặc định. Chỉ chạy fixture trong thư mục thực hành do bạn kiểm soát; nếu thư mục `demo-tree` đã tồn tại với tệp bổ sung, số file và số thư mục sẽ khác. Mã demo cố ý không tự xóa dữ liệu cũ để tránh một thao tác phá hủy ngoài mong đợi.

Bản thân `TreeFilter` chỉ đọc metadata. Việc chạy với root là thư mục source code hay dữ liệu thật không làm thay đổi file, nhưng người dùng vẫn cần quyền xem và duyệt những thư mục đó.

## 6. Symbolic link, chu trình và ranh giới root

### 6.1. Mặc định không đi theo symbolic link

`Files.walkFileTree` khi không đặt `FileVisitOption.FOLLOW_LINKS` không đi xuống directory link. Với symlink đến file, attributes được đọc theo chính sách không follow của traversal, vì vậy `attrs.isRegularFile()` thường không nhận nó là regular file để in ra.

Khi bật:

~~~bash
java TreeFilter demo-tree --glob "*.log" --follow-links
~~~

Java được phép đi qua symbolic link. Điều này có hai hệ quả:

1. **Có thể gặp chu trình**: `A/link -> A` hoặc liên kết gián tiếp giữa các thư mục. Visitor có thể nhận `FileSystemLoopException` thông qua `visitFileFailed`, báo lỗi và tiếp tục.
2. **Có thể đi ra ngoài cây root về mặt thực thể**: ví dụ `root/external -> /data/shared`. `Path.relativize` vẫn tạo được đường dẫn hình thức `external/...` vì walker đi theo link từ đường dẫn nằm dưới root, nhưng file vật lý thực sự có thể ở ngoài root.

Không thể coi `toAbsolutePath().normalize()` là sandbox. Nếu cần bảo đảm không rời khỏi root khi theo link, phải thiết kế policy và kiểm tra real path/canonical path cẩn thận, đồng thời xét **race condition** khi symlink bị đổi giữa lúc kiểm tra và lúc sử dụng. Một công cụ duyệt dữ liệu không tin cậy thường nên để `--follow-links` tắt và giới hạn phạm vi ở mức OS permissions.

### 6.2. Hard link và số lượng kết quả

Hard link là nhiều tên trong filesystem trỏ cùng một file identity. Công cụ hiện tại liệt kê **đường dẫn**, không deduplicate theo file identity. Vì vậy hai hard link đáp ứng filter sẽ được in hai lần; đây là hành vi phù hợp với nhiệm vụ tìm đường dẫn.

Nếu mục tiêu là đếm **file vật lý duy nhất**, cần một chính sách khác: dựa vào `BasicFileAttributes.fileKey()` khi provider hỗ trợ, nhận diện giới hạn của khóa và filesystem, rồi quản lý tập identity đã gặp. Điều này làm tăng dùng bộ nhớ. Không dùng tên đường dẫn làm bằng chứng một nội dung là duy nhất.

## 7. Vì sao không dùng `File.listFiles()` và đệ quy thủ công?

`java.io.File.listFiles()` vẫn hữu ích cho tình huống đơn giản, nhưng giá trị trả về có thể `null` khi lỗi đọc thư mục, và bạn phải tự quản lý stack/depth, symbolic link, metadata và xử lý lỗi khi viết recursive traversal. `Files.walkFileTree` cung cấp callback dành riêng cho các trường hợp đó.

Đoạn mã hay thấy:

~~~java
for (File child : directory.listFiles()) {
    visit(child);
}
~~~

Đây là **minh họa sai/thiếu**, không phải ví dụ chạy độc lập: `listFiles()` có thể trả `null` và gây `NullPointerException`; recursion sâu có thể gây `StackOverflowError`; nếu tự theo symbolic link thiếu kiểm soát có thể gặp chu trình. Dùng visitor giúp biểu diễn ý định rõ hơn, nhưng không miễn trừ việc chẩn đoán lỗi I/O.

Một lựa chọn Java NIO.2 gọn hơn cho bài toán chỉ cần stream kết quả:

~~~java
try (var paths = Files.walk(root)) {
    paths.filter(Files::isRegularFile)
         .forEach(System.out::println);
}
~~~

Đây là **đoạn mã minh họa** trong phương thức có `Path root` và xử lý `IOException`. `Files.walk` tạo stream đóng được nên cần `try-with-resources`; nếu iteration gặp I/O error, lỗi có thể được gói thành `UncheckedIOException`. Ngoài ra, việc bỏ nguyên subtree hidden hoặc thu thập lỗi rồi tiếp tục các nhánh khác không thuận tiện như visitor.

## 8. Hiệu năng và giới hạn trên cây tệp lớn

### 8.1. Bộ nhớ và độ phức tạp

Nếu có `N` mục trong phần cây được duyệt, một lượt traversal thường có chi phí thời gian cỡ `O(N)` **theo số mục được thăm**; nhưng độ trễ thực tế chịu chi phối của metadata I/O, directory enumeration, network mount, filesystem và quyền truy cập. Kiểm tra glob/size trên từng file thường rẻ hơn đọc toàn bộ nội dung.

Chương trình in ngay kết quả nên **không giữ tất cả `N` đường dẫn trong một collection**. Trạng thái của traversal phụ thuộc độ sâu cây và cách provider triển khai, còn bộ đếm chỉ dùng vài biến `long`. Nếu cần sort kết quả, thông thường phải lưu hoặc sort ngoài (external sort), khiến memory/IO trade-off khác đi.

### 8.2. Cách cải thiện khi nhu cầu tăng

- **Lọc sớm thư mục:** nếu chỉ muốn duyệt các nhánh `src` hoặc bỏ `node_modules`/`build`, bổ sung quy tắc `preVisitDirectory` trả `SKIP_SUBTREE` trước khi xuống sâu; hiệu quả hơn nhận mọi file rồi bỏ.
- **Lọc nhiều điều kiện:** mở rộng `Config` và tách các predicate rõ ràng; ví dụ loại extension khác, timestamp theo `attrs.lastModifiedTime()`, loại file lớn hơn ngưỡng.
- **Tìm nội dung:** sau khi file qua lọc metadata, đọc streaming từng chunk để tìm pattern. Quy định encoding, giới hạn bytes/size, có hỗ trợ binary hay không; đừng dùng `Files.readAllBytes` cho mọi tệp lớn.
- **Xuất máy đọc được:** thay TSV thô bằng JSON Lines với escaping chính xác hoặc viết vào database, nhất là khi tên chứa ký tự đặc biệt.
- **Sắp xếp ổn định:** để có output từ điển, dùng một cơ chế sắp xếp có giới hạn RAM, hoặc lấy collection chỉ khi tập kết quả nhỏ. Không mặc định traversal trả sorted paths.
- **Hủy sớm:** thêm tùy chọn giới hạn số kết quả và trả `TERMINATE` khi đạt ngưỡng; làm rõ đây là kết quả một phần, không còn là quét toàn bộ cây.
- **Song song:** không mặc định nhiều thread sẽ nhanh hơn, đặc biệt trên ổ đĩa/network share vì metadata I/O và contention. Khi thực sự cần song song, phải kiểm soát queue, giới hạn số worker, lỗi, thứ tự và quota tài nguyên.

### 8.3. Rủi ro khi filesystem thay đổi lúc duyệt

Directory traversal không tạo một snapshot giao dịch. Có thể xảy ra:

- Một file bị xóa/đổi tên sau khi parent directory đã liệt kê.
- Metadata kích thước thay đổi trong khi chương trình in dữ liệu.
- Quyền truy cập bị thu hồi khiến `visitFileFailed` xuất hiện giữa lượt quét.
- Symbolic link bị sửa làm kết quả khác ngay trong cùng quá trình.

Vì vậy hãy mô tả kết quả là **những đường dẫn quan sát được trong một lần quét**, không tuyên bố chứng minh toàn bộ filesystem ở đúng một thời điểm. Với tác vụ bảo mật, backup hoặc xóa file, cần thiết kế kiểm tra và cơ chế nhất quán riêng thay vì dựa hoàn toàn vào một lượt `walkFileTree`.

## 9. Kiểm thử có chủ đích

Ngoài fixture cơ bản, hãy kiểm tra các nhóm sau:

| Trường hợp | Hành vi mong đợi |
| --- | --- |
| Thư mục gốc rỗng | Không có dòng kết quả, thống kê root là một thư mục |
| `--min-bytes 0 --max-bytes 0` | Chỉ nhận regular file rỗng nếu tên khớp |
| `--glob "*.TXT"` | Phụ thuộc quy tắc case sensitivity của filesystem/provider; không giả định khớp `.txt` trên mọi hệ thống |
| `--max-depth 1` | Không vào file nằm trong thư mục con sâu hơn |
| Thư mục con hidden | Bỏ cả subtree nếu không có `--include-hidden` theo `Files.isHidden` |
| Symlink đến thư mục | Mặc định không đi theo; khi bật follow có thể duyệt mục tiêu |
| Symlink tạo vòng | Báo lỗi/cảnh báo và kết thúc hữu hạn, không đệ quy vô hạn |
| Một thư mục không có quyền đọc | Ghi nhận lỗi khi provider báo, cố tiếp tục các nhánh khác, mã thoát khác 0 |
| Root không phải thư mục | Thông báo đối số không hợp lệ, không duyệt |
| `--min-bytes` lớn hơn `--max-bytes` | Báo lỗi đối số trước khi duyệt |
| Glob sai cú pháp | `IllegalArgumentException`/lỗi pattern; hiển thị usage |
| Tên file có Unicode/space | Xử lý qua `Path`, không chia đường dẫn bằng dấu cách |
| File bị xóa khi đang quét | Ghi nhận lỗi tương ứng nếu provider phát hiện; không khẳng định snapshot |

Kiểm thử hành vi symbolic link/hidden cần tạo fixture riêng tùy nền tảng và quyền tạo symlink; máy Windows có thể yêu cầu quyền hoặc chế độ cấu hình phù hợp. Không chạy test permission hoặc link vòng trên cây thư mục công việc đang dùng chung nếu không kiểm soát được hậu quả.

Nếu cần kiểm chứng một hàm filter độc lập, bạn có thể tách predicate nhận `Path` và `BasicFileAttributes` ra thành method thuần để unit test. Kiểm thử tích hợp dùng thư mục tạm (`Files.createTempDirectory`) và `try/finally` để dọn **đúng fixture do test tạo**, tránh xóa đường dẫn do người dùng nhập. Với công cụ dòng lệnh, kiểm tra cả **stdout, stderr và exit code**, vì một lượt quét có thể có kết quả đúng nhưng đồng thời có lỗi ở nhánh khác.

## 10. Những lỗi thường gặp khi nộp bài thực hành

**Lỗi 1 — coi mọi mục là tệp.** Trong `visitFile`, khi đạt max depth, visitor có thể được gọi với directory không được descend. Vì vậy cần kiểm tra `attrs.isRegularFile()` thay vì giả định mọi mục trong callback là file thường.

**Lỗi 2 — sử dụng `Files.walk` nhưng quên đóng stream.** Stream giữ tài nguyên mở để duyệt directory; bao bằng `try-with-resources` nếu chọn API đó.

**Lỗi 3 — lọc đường dẫn sai cấp.** `PathMatcher.matches(file.getFileName())` chỉ lọc tên file. Mẫu đường dẫn `src/**/*.java` cần match trên relative path và phải kiểm chứng semantics của glob trên từng provider.

**Lỗi 4 — nhầm `Files.isHidden` với tên bắt đầu bằng dấu chấm.** Hệ điều hành có chính sách riêng, đặc biệt trên Windows; không ép giả định Unix lên môi trường khác.

**Lỗi 5 — mặc định follow symbolic link.** Điều này có thể mở rộng phạm vi quét ngoài root và tạo chu trình. Giữ lựa chọn explicit, có test vòng lặp và xử lý `FileSystemLoopException`.

**Lỗi 6 — dừng khi gặp file không khớp.** File không khớp nên `CONTINUE`; chỉ dùng `SKIP_SUBTREE` khi chủ đích loại **cả thư mục con**.

**Lỗi 7 — gom toàn bộ file vào RAM trước khi lọc.** Với hàng triệu mục, list quá lớn gây memory pressure. Ưu tiên streaming callback và chỉ tích lũy nếu cần sort hoặc aggregate.

**Lỗi 8 — cho rằng số liệu là snapshot nhất quán.** Cây tệp có thể thay đổi trong quá trình duyệt; thao tác cleanup/destructive yêu cầu kiểm tra kỹ bổ sung.

**Lỗi 9 — in warning vào cùng stdout với dữ liệu.** Pipeline parse sẽ khó xử lý; dành stdout cho kết quả và stderr cho warnings/statistics.

**Lỗi 10 — coi exit code 0 khi đã có lỗi truy cập.** Mã thoát có ý nghĩa với script; chương trình trên trả 2 khi một hoặc nhiều lỗi I/O đã được ghi nhận dù vẫn in kết quả tìm thấy.

## 11. Bài tập mở rộng có hướng dẫn

1. **Thêm lọc ngày sửa đổi:** nhận `--modified-after 2026-01-01`, parse bằng `LocalDate` theo timezone do người dùng xác định rồi so với `attrs.lastModifiedTime()`. Ghi rõ boundary là thời điểm UTC hay giờ địa phương.
2. **Loại các thư mục không cần thiết:** thêm `--exclude-dir target` để trong `preVisitDirectory` bỏ nhánh `target` dù nó nằm ở độ sâu bất kỳ; xác định so khớp tên hay relative path.
3. **Xuất CSV an toàn:** có header, escape dấu ngoặc kép, xuống dòng và dấu phân cách theo RFC 4180; chú ý Windows path separator và encoding UTF-8.
4. **Tìm tệp lớn nhất:** giữ top K bằng priority queue kích thước K thay vì thu tất cả N file; chỉ cần O(K) bộ nhớ cho tập kết quả.
5. **Lọc theo nội dung:** với các file dưới ngưỡng cho phép, dùng `BufferedReader` và `Charset` xác định để tìm dòng chứa từ khóa; phân biệt text với binary và giới hạn kích thước.
6. **Viết test tích hợp:** tạo file và thư mục tạm, chạy `TreeFilter` dưới root đó, kiểm tra số kết quả; mock/thiết lập error theo nền tảng nếu cần và không phụ thuộc thứ tự duyệt.

## 12. Câu hỏi tự đánh giá

**Câu 1.** Vì sao `Files.walkFileTree` phù hợp hơn đệ quy `File.listFiles()` cho công cụ có thể bỏ subtree?

**Đáp án:** Visitor cung cấp `preVisitDirectory` và `SKIP_SUBTREE`, cơ chế lỗi rõ ràng cùng tùy chọn depth/link, giúp kiểm soát traversal mà không phải tự viết recursion.

**Câu 2.** Gọi `java TreeFilter root --glob "*.java"` có khớp `root/src/Main.java` không?

**Đáp án:** Có, nếu mục đó là regular file hợp lệ và không bị các filter khác loại; matcher áp dụng vào tên `Main.java`, không phụ thuộc folder `src`.

**Câu 3.** Có thể gọi `root.toAbsolutePath().normalize()` là một lớp cách ly an toàn ngăn symlink vượt root không?

**Đáp án:** Không. Đó là chuẩn hóa **cú pháp đường dẫn**, không ràng buộc mục tiêu vật lý của symbolic link. Giữ `--follow-links` tắt khi không cần và dùng policy an toàn hơn cho cây không tin cậy.

**Câu 4.** Một tệp 5 byte có đạt `--min-bytes 5 --max-bytes 5`?

**Đáp án:** Có. Khoảng giới hạn của chương trình bao gồm hai đầu mút.

**Câu 5.** Chương trình quét 100 thư mục, tìm được 50 kết quả nhưng một nhánh bị lỗi đọc. Exit code nên biểu đạt điều gì?

**Đáp án:** Có thể giữ 50 kết quả đã quan sát, nhưng phải báo lỗi và trả mã khác 0 để script gọi biết quá trình quét **không hoàn chỉnh**. Trong ví dụ mã thoát là `2`.

**Câu 6.** Muốn đầu ra luôn được sắp xếp theo đường dẫn nhưng không được giữ triệu đường dẫn trong RAM, hướng xử lý nào hợp lý?

**Đáp án:** Thu kết quả ra định dạng có thể xử lý bằng external sort (hoặc database/streaming sorted pipeline phù hợp); tính toán chi phí I/O bổ sung, không giả định iterator filesystem có thứ tự cố định.

## 13. Tài liệu chính thức

- [Java 17 Files.walkFileTree](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#walkFileTree(java.nio.file.Path,java.util.Set,int,java.nio.file.FileVisitor)): giao thức traversal, `maxDepth`, link và visitor.
- [Java 17 SimpleFileVisitor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SimpleFileVisitor.html): các callback và hành vi mặc định.
- [Java 17 FileVisitResult](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitResult.html): `CONTINUE`, `SKIP_SUBTREE`, `SKIP_SIBLINGS`, `TERMINATE`.
- [Java 17 FileSystem.getPathMatcher](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)): glob/regex và phạm vi phụ thuộc provider.
- [Java 17 BasicFileAttributes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html): loại tệp, kích thước và thời gian.

Một công cụ lọc tệp đáng tin cần quy định rõ **đối tượng đang được duyệt**, **quy tắc lọc áp dụng ở cấp nào**, **hành vi khi gặp symbolic link/lỗi**, và **ý nghĩa của kết quả khi cây đang thay đổi**. Dựa trên `Files.walkFileTree` cùng bộ lọc metadata rõ ràng, bạn có thể mở rộng từ bài tập đơn giản thành công cụ quét tệp có thể sử dụng trong thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-006">Cập nhật tệp bằng ghi tạm và thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# Cập nhật tệp bằng ghi tạm và thay thế trong Java NIO.2

## 1. Bài toán thực hành

Giả sử ứng dụng đang có `config.txt` chứa một bản cấu hình hoạt động ổn định. Muốn lưu cấu hình mới, cách đơn giản là ghi đè trực tiếp:

~~~java
Files.writeString(target, newContent, StandardCharsets.UTF_8);
~~~

Đây là **mảnh mã minh họa** có biến `target` và `newContent` được khai báo bên ngoài. Nếu ứng dụng bị ngắt sau khi truncate file cũ nhưng trước khi ghi đủ nội dung mới, file đích có thể rỗng hoặc chỉ chứa một phần bản mới. Nguy cơ tương tự xảy ra khi thiếu dung lượng, serialization lỗi giữa chừng hoặc quá trình bị dừng.

Mẫu **write-temp-then-replace** tách việc chuẩn bị dữ liệu với việc công bố dữ liệu:

~~~text
Ban đầu:
  config.txt = VERSION_1

Bước 1, tạo ứng viên:
  .update-xxxxx.tmp = (trống)
  config.txt        = VERSION_1

Bước 2, ghi/kiểm tra bản mới:
  .update-xxxxx.tmp = VERSION_2
  config.txt        = VERSION_1

Bước 3, thay tên bằng move nguyên tử khi provider hỗ trợ:
  config.txt        = VERSION_2
  .update-xxxxx.tmp = không còn tên cũ
~~~

**Nếu lỗi xảy ra trước bước publish và không có writer khác**, target cũ vẫn chưa bị thao tác thay thế ảnh hưởng. Nếu thao tác move được thực hiện nguyên tử theo hợp đồng và thành công, reader mở theo tên target sẽ không phải quan sát quá trình ghi bản mới từng byte dưới tên đó.

Nhưng cần hiểu bốn bảo đảm khác nhau:

1. **Hoàn tất serialize:** bản ứng viên có đủ byte và format hợp lệ.
2. **Atomic visibility:** việc công bố tên target diễn ra như một thao tác nguyên tử, nếu được filesystem hỗ trợ.
3. **Durability:** dữ liệu và thay đổi tên có tồn tại đúng sau crash/mất điện hay không.
4. **Concurrency control:** nhiều writer có được phép vô tình ghi đè cập nhật của nhau không.

Một lời gọi `Files.move` chỉ góp phần giải quyết mục tiêu thứ hai. Không mặc nhiên coi nó là transaction nhiều file, fsync toàn bộ metadata hoặc cơ chế khóa giữa các tiến trình.

## 2. Luồng cập nhật đúng thứ tự

Với một file đơn, quy trình nền tảng là:

1. Chọn `target` có parent đáng tin cậy và đã kiểm tra quyền của caller.
2. Tạo file tạm **cùng thư mục đích** bằng `Files.createTempFile(parent, prefix, suffix)`.
3. Ghi **toàn bộ** nội dung/serialized representation vào file tạm; nếu dùng `FileChannel` thì lặp theo số byte thực ghi.
4. Kiểm tra tính hợp lệ của bản ứng viên: cú pháp, kích thước, checksum/version nếu nghiệp vụ cần.
5. Yêu cầu đồng bộ nội dung temp theo mức durability mong muốn bằng `FileChannel.force(...)` và **đóng channel**.
6. Gọi `Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)` để yêu cầu publish nguyên tử.
7. Nếu move thành công, coi bản mới đã công bố theo semantics của filesystem. Nếu thất bại, không xóa target cũ để “giúp move chạy”; dọn temp trong `finally`.
8. Nếu `ATOMIC_MOVE` không được hỗ trợ, áp dụng **chính sách đã quyết định**: fail closed hoặc cố ý cho phép fallback non-atomic, không đánh đồng hai mức bảo đảm.

Không nên đặt `Files.delete(target)` giữa bước 5 và bước 6. Thao tác đó phá tính liên tục: ứng dụng chết sau delete sẽ để target biến mất dù temp đã chuẩn bị xong.

### 2.1. Chọn đúng vị trí cho temp

~~~java
Path absolute = target.toAbsolutePath().normalize();
Path parent = absolute.getParent();
Path temp = Files.createTempFile(parent, ".update-", ".tmp");
~~~

Đây là mảnh mã, không chạy độc lập nếu chưa có biến `target`, import và xử lý lỗi. Tạo cùng parent thường giúp temp và target nằm cùng filesystem, cần thiết cho atomic rename trong nhiều hệ thống. Tạo temp bằng `Files.createTempFile("update-", ".tmp")` không có parent có thể đưa nó sang thư mục tạm mặc định trên **filesystem khác**, khiến atomic move không khả thi.

`createTempFile` tạo entry mới với tên không trùng, nhưng không loại bỏ mọi rủi ro nếu thư mục cho phép attacker xóa/đổi entry; thư mục staging phải thuộc quyền kiểm soát phù hợp.

### 2.2. Không đặt tên temp cố định

~~~java
// Anti-pattern khi nhiều tiến trình cập nhật đồng thời:
Path temp = target.resolveSibling("config.tmp");
~~~

Tên cố định dễ gây tranh chấp, bị chiếm sẵn hoặc bị hai lượt ghi đè lên nhau. Dùng `createTempFile` để mỗi lượt có ứng viên riêng và dọn đúng Path của lượt đó.

## 3. Ghi đầy đủ dữ liệu và xử lý short write

Một file tạm chỉ an toàn khi **không được publish trước lúc toàn bộ dữ liệu mới đã ghi xong**. Có thể dùng `Files.writeString` hoặc `Files.write` cho dữ liệu nhỏ. Với nội dung lớn hoặc cần đồng bộ trực tiếp, `FileChannel` cho quyền kiểm soát rõ:

~~~java
static void writeCandidate(Path temp, byte[] bytes) throws IOException {
    try (FileChannel file = FileChannel.open(
            temp,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {

        ByteBuffer src = ByteBuffer.wrap(bytes);
        while (src.hasRemaining()) {
            int n = file.write(src);
            if (n == 0) {
                throw new IOException("Ghi file tạm không tiến triển");
            }
        }

        file.force(true);
    }
}
~~~

Đây là **phương thức minh họa**, cần import `IOException`, `Path`, `ByteBuffer`, `FileChannel` và `StandardOpenOption`.

- `FileChannel.write(src)` trả số byte thực chuyển và tăng `src.position()` tương ứng; không giả định mỗi lần luôn ghi hết.
- `src.hasRemaining()` xác định khi toàn bộ byte trong bản ứng viên đã được tiêu thụ.
- `force(true)` yêu cầu đồng bộ thay đổi file theo hợp đồng JDK; mục đích là chuẩn bị dữ liệu ứng viên trước khi publish.
- Đóng channel bằng `try-with-resources` đảm bảo không còn code serializer tiếp tục ghi vào temp sau bước chuẩn bị.

Nếu deserialize/serialize cần giữ byte liên tục rất lớn, hãy đưa dữ liệu qua `InputStream`/`OutputStream` để không giữ toàn bộ trong RAM, đồng thời xác định rõ bước nào flush, sync, verify và close.

### 3.1. Ghi vào temp khác với ghi vào target

~~~java
// Nguy hiểm với mục tiêu bảo toàn file cũ nếu serialize lỗi:
try (OutputStream out = Files.newOutputStream(
        target, StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    serializer.writeTo(out);
}
~~~

Đây là pseudocode, `serializer` do ứng dụng cung cấp. `TRUNCATE_EXISTING` đã thay đổi file đích ngay khi bắt đầu. Nếu `serializer` ném lỗi giữa chừng, file cũ không còn nguyên vẹn.

Cách đúng trong mô hình này là thực hiện toàn bộ thao tác ghi trên `temp`, sau đó dùng move để thay tên vào cuối.

## 4. ATOMIC_MOVE không đồng nghĩa REPLACE_EXISTING

API `Files.move(source, target, options...)` hỗ trợ các option phổ biến:

| Option | Ý nghĩa | Giới hạn cần nhớ |
| --- | --- | --- |
| `StandardCopyOption.ATOMIC_MOVE` | Yêu cầu move được thực hiện như thao tác filesystem nguyên tử. | Có thể ném `AtomicMoveNotSupportedException`. |
| `StandardCopyOption.REPLACE_EXISTING` | Cho phép thay target đã tồn tại trong move thông thường. | Không tự làm thao tác thành nguyên tử. |
| `StandardCopyOption.COPY_ATTRIBUTES` | Chủ yếu dùng với `Files.copy` cho metadata. | Không phải lời giải cho atomic publication. |

Ví dụ:

~~~java
Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
~~~

Điều rất dễ bỏ qua trong **Javadoc Java 17**: khi có `ATOMIC_MOVE`, **các option khác bị bỏ qua**; nếu target đã tồn tại, việc nó được thay thế hay thao tác thất bại là **implementation-specific**. Do đó:

~~~java
Files.move(
        temp, target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
~~~

không chứng minh mọi provider đều hỗ trợ **atomic replacement của target đã tồn tại**, dù source và target cùng thư mục. Lệnh trên thường được dùng trên filesystem có atomic rename replacement, nhưng production phải kiểm tra hành vi thực tế và có chính sách xử lý khi provider từ chối.

**Không dùng `Files.deleteIfExists(target)` trước move** để giải quyết `FileAlreadyExistsException`. Điều đó tạo một khoảng trống mà reader có thể thấy target không tồn tại; nếu tiến trình chết sau delete, bản cũ cũng mất.

### 4.1. Khi không hỗ trợ ATOMIC_MOVE

~~~java
try {
    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
} catch (AtomicMoveNotSupportedException unsupported) {
    if (!allowNonAtomicFallback) {
        throw unsupported;
    }

    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
}
~~~

Đây là mảnh code, cần `Path`, `AtomicMoveNotSupportedException`, `StandardCopyOption` và biến `allowNonAtomicFallback`.

Hai lựa chọn có ý nghĩa khác nhau:

- **Fail closed:** nếu nguyên tử là yêu cầu bắt buộc (cấu hình quan trọng, file trạng thái), báo lỗi và không publish. Target cũ được giữ theo giả định không có writer khác.
- **Fallback có chủ đích:** chỉ với tài nguyên cho phép mức bảo đảm thấp hơn (ví dụ cache tái tạo được), mới move thông thường với `REPLACE_EXISTING`. Thao tác fallback **không được bảo đảm nguyên tử**, có thể thất bại giữa các bước do provider.

Không catch **mọi IOException** rồi fallback vào `Files.copy(temp, target, REPLACE_EXISTING)`. Một lỗi permission/storage/target đang mở không đồng nghĩa thiếu atomic support; copy thẳng có thể làm target bị cập nhật dở dang.

## 5. Phân biệt atomic visibility và crash durability

**Atomic visibility**: thao tác publish thay entry target một cách nguyên tử khi filesystem đáp ứng yêu cầu.

**Durability**: thay đổi dữ liệu và tên còn đúng sau khi máy mất điện, kernel crash hoặc thiết bị gặp sự cố.

~~~text
write temp
    |
    v
file.force(true)    <- yêu cầu đồng bộ dữ liệu/metadata của TEMP
    |
    v
Files.move(temp, target, ATOMIC_MOVE)
    |
    v
Metadata entry trong THƯ MỤC có durable sau crash không?
    |
    v
Phụ thuộc OS, filesystem, storage và protocol đồng bộ.
~~~

`FileChannel.force(boolean metaData)` trên file tạm:

- `force(false)` yêu cầu đồng bộ nội dung file (không nhất thiết mọi metadata).
- `force(true)` yêu cầu đồng bộ nội dung và metadata theo hợp đồng.
- Bảo đảm API mạnh nhất áp dụng cho file trên thiết bị lưu trữ **cục bộ**; remote filesystem/provider có giới hạn riêng.

Nhưng **`force(true)` trên file tạm không tự đảm bảo rename của directory entry đã durable**. Trong Java 17 không có một thủ tục portable chung buộc mọi directory provider đồng bộ metadata tên sau rename. Nếu cần crash-safety nghiêm ngặt, phải thiết kế và kiểm chứng trên OS/filesystem mục tiêu hoặc chọn tầng lưu trữ transactional.

`OutputStream.flush()` chỉ đẩy bộ đệm Java đến downstream, không phải cam kết dữ liệu đã xuống thiết bị. `close()` cũng không thay thế một giao thức crash-consistency hoàn chỉnh.

## 6. Chương trình hoàn chỉnh Java 17+: thử lỗi trước publish, rồi cập nhật thành công

Chương trình sử dụng **thư mục tạm thuộc sở hữu ứng dụng**. Nó tạo file `config.txt` chứa `OLD`, cố tình gây lỗi **trước move** để xác minh target chưa bị thay, rồi ghi `NEW` và kiểm tra cleanup.

Để ví dụ có thể chạy trên nhiều provider, phương thức nhận cờ `allowNonAtomicFallback`. Trong demo đặt `true` nhằm cho phép fallback nếu `ATOMIC_MOVE` không được hỗ trợ; **đây là lựa chọn cho bài thực hành, không phải khuyến nghị mặc định cho file quan trọng**. Khi sản phẩm bắt buộc atomicity, truyền `false`.

Lưu thành `AtomicFileUpdateDemo.java`:

~~~java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class AtomicFileUpdateDemo {

    static void updateFile(
            Path target,
            byte[] newBytes,
            boolean allowNonAtomicFallback,
            boolean failBeforeMove) throws IOException {

        Path absolute = target.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        if (directory == null) {
            throw new IOException("Target không có thư mục cha");
        }

        Path temp = Files.createTempFile(
                directory, ".update-", ".tmp");

        try {
            try (FileChannel file = FileChannel.open(
                    temp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer bytes = ByteBuffer.wrap(newBytes);
                while (bytes.hasRemaining()) {
                    int n = file.write(bytes);
                    if (n == 0) {
                        throw new IOException("Ghi file tạm không tiến triển");
                    }
                }

                file.force(true);
            }

            if (failBeforeMove) {
                throw new IOException("Injected failure before move");
            }

            try {
                Files.move(
                        temp, absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                if (!allowNonAtomicFallback) {
                    throw unsupported;
                }

                Files.move(
                        temp, absolute,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("file-update-");
        Path target = dir.resolve("config.txt");

        try {
            Files.writeString(
                    target, "OLD", StandardCharsets.UTF_8);

            boolean injected = false;
            try {
                updateFile(
                        target,
                        "IGNORED".getBytes(StandardCharsets.UTF_8),
                        true,
                        true);
            } catch (IOException expected) {
                injected = true;
            }

            System.out.println("failureInjected=" + injected);
            System.out.println("afterFailure=" +
                    Files.readString(target, StandardCharsets.UTF_8));

            updateFile(
                    target,
                    "NEW".getBytes(StandardCharsets.UTF_8),
                    true,
                    false);

            System.out.println("afterSuccess=" +
                    Files.readString(target, StandardCharsets.UTF_8));

            long leftovers;
            try (Stream<Path> entries = Files.list(dir)) {
                leftovers = entries.filter(p ->
                        p.getFileName().toString().endsWith(".tmp"))
                        .count();
            }
            System.out.println("tempFiles=" + leftovers);
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 AtomicFileUpdateDemo.java
java AtomicFileUpdateDemo
~~~

**Stdout dự kiến trên filesystem cho phép thay thế target đã tồn tại bằng atomic move hoặc nhánh fallback đã bật:**

~~~text
failureInjected=true
afterFailure=OLD
afterSuccess=NEW
tempFiles=0
~~~

Nếu provider không cho phép thay thế target đang tồn tại bằng `ATOMIC_MOVE` và ném ngoại lệ khác `AtomicMoveNotSupportedException`, hoặc filesystem/permission không cho move, chương trình có thể ném `IOException` thay vì in bốn dòng trên. Không hứa hẹn behavior như nhau trên mọi nền tảng.

### 6.1. Vì sao sau injected failure, target vẫn là OLD?

Lỗi được tạo ra **sau khi đã ghi và đóng temp, nhưng trước move**. Quy trình chưa hề mở target để truncate hay ghi, vì vậy trong bài demo không có tác nhân khác, `config.txt` vẫn chứa `OLD`. `finally` dọn temp đã ghi dở hoặc ghi đủ mà chưa được publish.

Đây là phép thử **lỗi xử lý khi tiến trình còn chạy**, không phải phép mô phỏng mất điện; không suy ra file chắc chắn còn tồn tại với đúng nội dung sau mọi crash OS.

### 6.2. Vì sao có thể chạy mà không in phương án move?

Chương trình không in `atomic=true/false` để stdout không gắn với một provider cụ thể. Nếu `ATOMIC_MOVE` được hỗ trợ thì nhánh đó chạy; nếu chỉ ném `AtomicMoveNotSupportedException` và cho phép fallback thì dùng `REPLACE_EXISTING`. Hai nhánh có **mức bảo đảm khác nhau** dù kết quả kiểm tra file trong một lượt chạy đơn giản giống nhau.

Ứng dụng production nên log/metric sự kiện hạ cấp này và thường không cho fallback với dữ liệu quan trọng.

### 6.3. Cleanup trong finally

`Files.deleteIfExists(temp)` sau move thành công thường không tìm thấy đường dẫn temp vì entry đã được chuyển. Nếu bước ghi/move thất bại, nó cố dọn file ứng viên.

**Điểm cần cải thiện trong production:** nếu cleanup ném IOException trong `finally`, nó có thể che lỗi gốc. Một implementation cứng cáp nên thu lỗi cleanup, gắn như **suppressed exception** vào lỗi chính khi có thể, và không để cleanup làm chương trình báo nhầm trạng thái publish. Cũng cần xử lý trường hợp tiến trình chết trước khi chạy finally: nên có quy trình dọn temp mồ côi theo tuổi/version một cách an toàn.

## 7. Reader quan sát gì khi replace?

Khi provider có atomic rename replacement:

~~~text
Trước publish: target -> bản A
Đang ghi temp: target -> bản A, temp -> bản B
Sau publish:   target -> bản B
~~~

Reader mở `target` **sau khi** publish thành công có thể đọc bản B. Reader đã mở một handle tới bản A trước đó **không tự chuyển** handle sang bản B. Trên nhiều filesystem kiểu POSIX, reader cũ có thể đọc tiếp inode cũ; trên Windows, quyền sharing của open handles còn có thể làm move thất bại.

Không dùng giả định “mọi reader trên mọi hệ điều hành luôn thấy bản A hoặc B theo cùng cách”; behavior cụ thể phụ thuộc provider, phương thức reader mở file và lifetime file handle.

Nếu client đọc nửa file, đóng handle, rồi mở lại và đọc tiếp theo offset, hai nửa có thể thuộc hai phiên bản khác nhau. Hãy đọc toàn bộ một phiên bản qua **một handle** hoặc sử dụng version ID/giao thức nhất quán.

## 8. Hai writer vẫn có thể gây lost update

~~~text
target đang là A
Writer 1: đọc A -> tính B1 -> ghi temp1
Writer 2: đọc A -> tính B2 -> ghi temp2
Writer 1: atomic move temp1 -> target = B1
Writer 2: atomic move temp2 -> target = B2

B1 bị thay bởi B2, dù từng move có thể atomic.
~~~

`ATOMIC_MOVE` không có semantics “chỉ thay nếu target vẫn là bản mà tôi vừa đọc”. Vì vậy nó **không tự giải quyết concurrency control**.

Có thể chọn:

- **Single writer**: một process/thread sở hữu file và tuần tự hóa mọi cập nhật.
- **File lock hợp tác**: tất cả writer tuân thủ cùng giao thức khóa trong toàn bộ đoạn read → compute → publish. Tính bắt buộc/khả dụng tùy OS/filesystem.
- **Versioned files + manifest**: ghi các file bất biến theo version, rồi publish một pointer/manifest; vẫn cần giải quyết việc hai writer cùng cập nhật manifest.
- **Transactional storage/CAS**: khi cần ngăn lost update một cách chắc chắn ở cấp hệ thống, sử dụng kho dữ liệu hỗ trợ compare-and-swap hoặc transaction thực sự.

`Files.size()`, `getLastModifiedTime()` hoặc checksum kiểm tra trước move chỉ mô tả trạng thái tại thời điểm đọc. Writer khác vẫn có thể đổi file **sau check nhưng trước move** (*TOCTOU*). Không biến check-then-move thành CAS chỉ bằng một câu lệnh if.

## 9. Giữ nguyên metadata, quyền và ownership

Một bản update bằng rename thường đưa một **object file mới** vào tên target. Vì thế thông tin của file cũ có thể không còn được giữ nguyên tự động:

- Quyền POSIX (rwx), owner, group.
- ACL của filesystem/provider.
- Modified time, creation time, extended attributes, security labels.
- Hard links: các tên khác trỏ inode cũ có thể vẫn giữ bản cũ.

Ví dụ chỉ sao chép permission khi provider thực sự hỗ trợ POSIX:

~~~java
Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(target);

Files.setPosixFilePermissions(temp, permissions);
~~~

Đây là **mảnh mã minh họa**, cần `PosixFilePermission` và import `Files`/`Set`. API có thể ném `UnsupportedOperationException` trên provider không có POSIX view. Sao chép mode bit **không đủ** bảo toàn ACL, owner, xattrs và chính sách bảo mật. Nếu file thuộc dữ liệu nhạy cảm, lựa chọn tốt có thể là đặt permission theo cấu hình đáng tin **khi tạo temp**, thay vì mù quáng thừa kế từ target không tin cậy.

Đọc metadata của target rồi áp vào temp cũng có TOCTOU nếu actor khác có thể đổi target giữa hai bước. Đảm bảo parent directory tin cậy và authorization theo quy trình của hệ thống.

## 10. Symlink, hard link và vùng thư mục không tin cậy

Mẫu ghi tạm không tự tạo sandbox đường dẫn:

1. Nếu user nhập `../../config.txt`, `Path.normalize()` đơn độc không đủ chứng minh thao tác bị giữ trong vùng cho phép.
2. Nếu attacker có thể sửa parent directory, họ có thể thay thế entry, symlink hoặc temp giữa hai lời gọi API.
3. Khi target có symbolic link hoặc hard link, ngữ nghĩa thay entry với ngữ nghĩa sửa nội dung inode không giống nhau. Cần xác định chính sách của ứng dụng, không suy diễn tất cả provider như nhau.
4. `Files.exists(...)`/`isRegularFile(...)` rồi `Files.move(...)` không phải một giao dịch kiểm tra-và-thay thế nguyên tử.
5. Nếu cần chống path-race trong thư mục chia sẻ không tin cậy, kiểm tra `SecureDirectoryStream`, quyền thư mục, directory-handle-relative API và khả năng provider; nếu không đáp ứng bảo đảm cần thiết phải fail closed hoặc đổi kiến trúc.

**Phạm vi bảo vệ chính của mẫu này là không công bố output mới khi còn đang ghi**, không tự chặn mọi actor có quyền sửa tên tệp.

## 11. Cập nhật file rất lớn hoặc định dạng phức tạp

Khi file có thể lớn hơn RAM:

~~~text
Ứng dụng cung cấp dữ liệu mới
     |
     v
Stream serializer -> temporary file
     |
     v
Parse/validate lại temp khi cần
     |
     v
Sync temp theo hợp đồng
     |
     v
Đóng writer -> atomic move -> target
~~~

Ví dụ serializer streaming:

~~~java
try (OutputStream out = Files.newOutputStream(
        temp,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {

    // serializer.writeTo(out) là API của ứng dụng.
    serializer.writeTo(out);
}
~~~

Đây là **pseudocode** vì `serializer` chưa được khai báo. Sau khi đóng output, cần có bước đồng bộ file temp phù hợp nếu yêu cầu durability, rồi mới công bố; không được xem việc đóng stream là bằng chứng mọi dữ liệu đã nằm bền vững trên đĩa.

Lưu ý phương án có temp phải giữ đồng thời **bản cũ và bản mới**, gây chi phí dung lượng lớn. `Files.getFileStore(path).getUsableSpace()` chỉ cung cấp thông tin tại thời điểm gọi và không đặt chỗ độc quyền; vẫn phải bắt `IOException` nếu disk-full.

## 12. Ma trận sự cố

| Lỗi xảy ra ở đâu? | Ảnh hưởng trong tiến trình còn chạy, nếu không có writer khác | Cách xử lý |
| --- | --- | --- |
| Trước tạo temp | Target cũ chưa bị thay. | Báo lỗi. |
| Đang ghi temp | Target cũ chưa publish thay đổi; temp có thể dở dang. | Không move, dọn temp. |
| Kiểm tra candidate thất bại | Target cũ chưa publish. | Bỏ candidate, dọn temp. |
| `force(temp)` ném lỗi | Chưa đi tới publish theo flow đúng. | Không move, giữ target cũ. |
| Fault injection trước move | Target cũ còn nguyên trong demo. | Bắt lỗi, kiểm tra cleanup. |
| `ATOMIC_MOVE` không được hỗ trợ | Bảo đảm yêu cầu chưa được đáp ứng. | Fail closed hoặc fallback được cho phép trước. |
| Move thành công nhưng caller chưa nhận response | Target có thể đã publish bản mới. | Retry cần idempotency/version. |
| Mất điện/cra​​sh | Không thể suy luận đầy đủ chỉ từ Java API. | Thử nghiệm recovery và durability theo filesystem. |

Nếu response cho caller bị mất **sau khi commit**, caller có thể retry cùng yêu cầu. Thiết kế cần cân nhắc request ID, version token hoặc cơ chế xử lý lặp thay vì giả định lần trước chắc chắn thất bại.

### 12.1. Atomic move không phải transaction nhiều file

Muốn cập nhật `users.json` và `permissions.json` cùng một phiên bản, hai lệnh atomic rename **không tạo giao dịch nguyên tử cho cả hai file**. Reader có thể quan sát một file đã đổi, file kia chưa đổi.

Có thể giải quyết bằng file phiên bản hóa và một manifest/pointer tới phiên bản hoàn chỉnh, hoặc lưu dữ liệu trong một transaction store. Bản thân manifest cũng cần quy trình publish và recovery phù hợp.

## 13. Kiểm thử đúng bảo đảm

### 13.1. Unit test serialization bằng stream bộ nhớ

Tách core serializer khỏi phần mở file:

~~~java
void writeRepresentation(OutputStream out, Config config)
        throws IOException {
    // API của ứng dụng: ghi theo thứ tự định dạng đã định.
}
~~~

Đây là **pseudocode**, chưa đủ để biên dịch vì `Config` và phần thân chưa có. Kiểm thử serializer bằng `ByteArrayOutputStream` hoặc `StringWriter` cho trường hợp dữ liệu rỗng, Unicode, bản ghi lớn, lỗi encode và invalid input.

### 13.2. Integration test file tạm

Với `Files.move`, phải chạy trên filesystem thật. Các tình huống quan trọng:

- Target ban đầu chứa `OLD`, lỗi trước move: vẫn là `OLD`.
- Update bình thường: target là `NEW` và không có temp mồ côi.
- Atomic move unsupported: với flag fail-closed phải ném lỗi, không thay target.
- Fallback được cho phép: ghi nhận chất lượng bảo đảm thấp hơn, không gọi nó atomic.
- Metadata permissions/ACL sau replace đúng chính sách.
- Writer đồng thời: test quy tắc chống last-writer-wins.
- Reader mở trước và sau publish: hành vi thực tế theo OS.
- Không đủ dung lượng, không có quyền, target đang mở, directory read-only, lỗi cleanup.

JUnit Jupiter có `@TempDir` để tạo thư mục test riêng. Test unit stream bộ nhớ không đủ kiểm chứng OS-specific rename, ACL, lock và crash recovery.

### 13.3. Kiểm tra crash durability cần hạ tầng riêng

Một phép thử Java thành công không chứng minh điều gì xảy ra khi thiết bị mất điện ở microsecond giữa các bước. Với yêu cầu độ bền mạnh, cần protocol đã kiểm chứng cho filesystem/OS mục tiêu hoặc hệ thống có giao dịch, cùng test recovery và fault injection cô lập. Không xem `ATOMIC_MOVE` + `force(true)` là bằng chứng tuyệt đối cho mọi storage.

## 14. Các lỗi phổ biến

### Lỗi 1: ghi trực tiếp vào file gốc

`TRUNCATE_EXISTING` làm target mất nội dung cũ trước khi ghi đủ. **Sửa:** ghi candidate trong temp, validate, sync rồi mới publish.

### Lỗi 2: tạo temp ở ổ khác

Temp nằm trên `java.io.tmpdir` có thể khác filesystem. **Sửa:** dùng `Files.createTempFile(parent,...)`.

### Lỗi 3: dùng filename tạm cố định

Tạo `config.tmp` cố định dễ va chạm/race. **Sửa:** createTempFile tạo entry mới và luôn cleanup.

### Lỗi 4: xóa target rồi mới ATOMIC_MOVE

Tạo khoảng trống và có thể mất bản cũ. **Sửa:** không xóa target trước, dùng semantics replace được provider hỗ trợ.

### Lỗi 5: cho rằng REPLACE_EXISTING đảm bảo atomic replace khi đi kèm ATOMIC_MOVE

Khi có `ATOMIC_MOVE`, option khác bị bỏ qua; target đã có hành vi implementation-specific. **Sửa:** xử lý exception, xác minh provider thực tế.

### Lỗi 6: catch mọi IOException rồi copy vào target

Có thể làm target dở dang và che mất lỗi chính. **Sửa:** fallback chỉ theo chính sách rõ ràng, không được tự động hạ bảo đảm.

### Lỗi 7: không xử lý short write

`FileChannel.write` không hứa luôn ghi đủ buffer trong một lần. **Sửa:** lặp theo `remaining()`.

### Lỗi 8: tưởng force trước move đảm bảo crash durability

Sync temp không mặc định sync directory rename. **Sửa:** có chiến lược crash-consistency riêng nếu cần.

### Lỗi 9: không xét permission của file mới

Temp có metadata riêng; replace không nhất thiết giữ ACL/owner của target cũ. **Sửa:** áp policy metadata lên temp trước publish và kiểm tra.

### Lỗi 10: hai writer cùng ghi mà không phối hợp

Atomic rename không ngăn lost update. **Sửa:** single-writer, lock hoặc version/CAS/transaction.

### Lỗi 11: bỏ qua symlink và path traversal

Ghi tạm cùng directory không tự chặn attacker thay directory entry. **Sửa:** root tin cậy, validate tên, kiểm soát quyền và cơ chế secure directory handle khi cần.

### Lỗi 12: dọn temp sau khi ghi thất bại nhưng che lỗi gốc

`finally` có thể ném lỗi dọn. **Sửa:** giữ ngoại lệ gốc và thêm ngoại lệ cleanup vào `addSuppressed(...)` khi thích hợp; log temp mồ côi.

### Lỗi 13: không giới hạn dung lượng temp

Cần chỗ cho cả bản cũ và bản mới. **Sửa:** hạn mức dung lượng/size và xử lý disk-full.

### Lỗi 14: báo success trước khi publish

Nếu move thất bại sau khi server gửi ACK, caller có thông tin sai. **Sửa:** chỉ báo sau khi đạt điều kiện commit theo nghiệp vụ, có idempotency khi retry.

## 15. Bài tập tự kiểm tra

1. **Tại sao không nên dùng `Files.writeString(target, text)` trực tiếp với cấu hình quan trọng?** Có thể truncate/ghi dở nếu lỗi.
2. **Tại sao temp nên ở cùng parent?** Tăng cơ hội cùng filesystem để atomic move.
3. **`Files.createTempFile` tốt hơn filename cố định như thế nào?** Tạo entry mới không trùng, tránh nhiều lượt ghi vào cùng temp.
4. **`ATOMIC_MOVE` có luôn được hỗ trợ không?** Không; có thể ném `AtomicMoveNotSupportedException`.
5. **`ATOMIC_MOVE` đi cùng `REPLACE_EXISTING` có chắc thay target đã có?** Không; các option khác bị bỏ qua theo Java 17, việc thay target là implementation-specific.
6. **Có nên xóa target trước khi move?** Không, vì tạo khoảng không có target.
7. **Nếu atomicity là yêu cầu bắt buộc, xử lý unsupported thế nào?** Fail closed, không hạ xuống non-atomic.
8. **Vì sao cần vòng lặp ghi FileChannel?** `write` trả số byte thực ghi, có thể ít hơn remaining.
9. **`force(true)` trước rename có chứng minh rename durable khi mất điện không?** Không.
10. **Hai writer có thể gây lost update dù mỗi move atomic không?** Có.
11. **File mới có chắc kế thừa ACL/owner của target cũ không?** Không.
12. **Reader đã mở bản cũ có tự đổi sang bản mới sau rename không?** Không nên giả định.
13. **Fallback bằng Files.copy có bảo đảm tránh file dở không?** Không, có thể cập nhật từng phần.
14. **Nếu update ba file, ba atomic move có tạo transaction không?** Không.
15. **Nếu serializer thất bại, target phải ở trạng thái nào trước publish?** Vẫn là bản cũ theo giả định không có writer khác.
16. **Vì sao cần xác thực đường dẫn user nhập?** Temp strategy không ngăn path traversal/symlink race.
17. **Muốn kiểm thử lỗi trước publish nên làm thế nào?** Fault injection ở điểm sau khi ghi temp, trước move; đối chiếu file target và cleanup.
18. **Tại sao mất điện sau move nhưng trước response khó xử lý?** Caller có thể không biết commit đã xảy ra; cần version/idempotency.
19. **Khi nào nên chọn database?** Khi cần transaction nhiều đối tượng, optimistic locking/CAS hoặc durability mạnh.
20. **Việc file mới lớn gấp đôi gây vấn đề gì?** Cần thêm dung lượng tạm thời để giữ cả bản cũ và bản mới.

## 16. Tài liệu Java 17 tham khảo

- [Files.move](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)): hợp đồng move và ATOMIC_MOVE.
- [StandardCopyOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardCopyOption.html): ATOMIC_MOVE, REPLACE_EXISTING.
- [Files.createTempFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#createTempFile(java.nio.file.Path,java.lang.String,java.lang.String,java.nio.file.attribute.FileAttribute...)): tạo temp trong directory chỉ định.
- [FileChannel.force](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean)): yêu cầu đồng bộ dữ liệu và metadata file.
- [FileChannel.write](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html#write(java.nio.ByteBuffer)): số byte thực ghi.
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): thao tác theo directory handle khi provider hỗ trợ.

**Ghi nhớ:** Để cập nhật một file mà không đưa bản đang ghi dở tới reader, hãy **tạo temp cùng thư mục → ghi/verify/force phù hợp → đóng → move nguyên tử nếu được hỗ trợ → dọn temp**. Không tự xóa target trước, không tự hạ bảo đảm sang copy/non-atomic và không đồng nhất atomic visibility với crash durability, transaction hay kiểm soát multi-writer.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-007">So sánh RandomAccessFile với FileChannel trên bản ghi cố định</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh RandomAccessFile với FileChannel trên bản ghi cố định

## 1. Bài toán thực hành và mục tiêu

Giả sử một chương trình Java lưu nhiều bản ghi tài khoản trong một tệp nhị phân. Mỗi bản ghi có đúng **16 byte**, nên ta biết ngay vị trí của bản ghi thứ `i` mà không phải đọc mọi bản ghi đứng trước. Chương trình cần:

- Tạo tệp chứa một số bản ghi theo cùng một layout.
- Đọc một bản ghi bất kỳ theo chỉ số.
- Sửa giá trị trong một bản ghi mà không phải viết lại toàn bộ tệp.
- Phát hiện chỉ số sai, EOF và bản ghi cuối bị thiếu byte.
- So sánh hai cách thao tác: **`RandomAccessFile`** và **`FileChannel`** với **`ByteBuffer`**.
- Hiểu khác biệt giữa **file pointer** của `RandomAccessFile`, **position** của channel, và **position/limit** của buffer.
- Không tự suy ra phương án nào nhanh hơn khi chưa đo trên workload thật.

**Kết luận trước khi viết code:** cả hai API đều có thể thực hiện random access tới bản ghi có độ dài cố định. `RandomAccessFile` tiện với các phương thức đọc/ghi primitive và `seek()`; `FileChannel` tiện với positional read/write và các thao tác NIO. **Cùng đọc đúng 16 byte ở cùng offset** mới là tiền đề của một phép so sánh hợp lệ.

Bài dùng **Java 17+**, chỉ sử dụng Java SE. Mọi ví dụ chạy độc lập sẽ có tên tệp/class tương ứng, lệnh `javac`/`java` và stdout không phụ thuộc đường dẫn tệp tạm.

## 2. Bản ghi cố định là gì?

Một **fixed-length record** là một bản ghi mà số byte và thứ tự trường được định nghĩa trước; mọi bản ghi của cùng phiên bản định dạng có cùng chiều dài.

Trong bài này, một bản ghi gồm:

| Offset trong bản ghi | Trường | Kiểu nhị phân | Số byte |
| --- | --- | --- | --- |
| 0–3 | `id` | `int` signed 32-bit | 4 |
| 4–11 | `balance` | `long` signed 64-bit | 8 |
| 12–15 | `status` | `int` signed 32-bit | 4 |
| **Tổng** | | | **16 byte** |

Cả hai cách ghi đều dùng **big-endian**: byte có trọng số cao nhất xuất hiện trước. `RandomAccessFile.writeInt/writeLong` dùng big-endian theo hợp đồng `DataOutput`; `ByteBuffer` có default order là `BIG_ENDIAN` nhưng nên thiết lập tường minh để tránh lỗi khi code được refactor.

Ví dụ bản ghi `id=1002`, `balance=7250`, `status=1` có các nhóm byte:

```text
Offset:     0          4                     12
            |          |                      |
Field:      id:int     balance:long           status:int
Bytes:      00 00 03 EA  00 00 00 00 00 00 1C 52  00 00 00 01
Size:       < 4 bytes > <      8 bytes      > < 4 bytes >
```

Vì `1002 = 0x000003EA` và `7250 = 0x0000000000001C52`, hai API phải diễn giải chính xác cùng byte nếu dùng cùng layout và byte order.

**Không phải Java object serialization:** một tệp fixed-record chứa các byte do ứng dụng quy định. Nó không dùng `ObjectOutputStream`, không mang class descriptor và không khôi phục object graph tự động. Chương trình tự chịu trách nhiệm về schema và tính tương thích phiên bản.

## 3. Công thức xác định offset

Gọi:

- `R`: số byte mỗi bản ghi (ở đây `R=16`).
- `i`: chỉ số bản ghi bắt đầu từ **0**.
- `base`: vị trí bắt đầu vùng dữ liệu, bằng 0 khi file không có header.
- `offset`: vị trí byte đầu tiên của bản ghi trong file.

Công thức:

\[
\text{offset}(i)=\text{base}+i\times R
\]

Với `base=0`:

| Record index | Byte offset | Vùng byte của bản ghi |
| --- | --- | --- |
| 0 | 0 | 0–15 |
| 1 | 16 | 16–31 |
| 2 | 32 | 32–47 |
| 3 | 48 | 48–63 |

Tệp gồm ba bản ghi chính xác phải dài `3 × 16 = 48` byte. Nếu tệp dài 50 byte, hai byte cuối không tạo thành bản ghi hợp lệ; đó là tình huống **partial/truncated/corrupt record**, không phải một bản ghi thứ tư.

### Tránh overflow khi tính offset

Không dùng:

```java
long offset = index * RECORD_BYTES;
```

nếu cả `index` và `RECORD_BYTES` là `int`: phép nhân được thực hiện trong `int` **trước khi gán sang `long`**, nên có thể overflow. Cũng phải kiểm tra chỉ số âm và phép nhân `long` quá lớn.

Dùng:

```java
static long offsetOf(long index, int recordBytes) {
    if (index < 0 || recordBytes <= 0) {
        throw new IllegalArgumentException("Invalid index/size");
    }
    return Math.multiplyExact(index, (long) recordBytes);
}
```

Đây là **đoạn helper minh họa**; `Math.multiplyExact` ném `ArithmeticException` nếu phép nhân không biểu diễn được trong `long`. Với file có header, cần thêm `base` bằng `Math.addExact(base, product)`.

Công thức offset chỉ hợp lệ nếu **mọi bản ghi thực sự có đúng R byte**. Không thể áp dụng trực tiếp nếu bạn chèn chuỗi UTF-8 độ dài thay đổi, metadata tùy ý hoặc marker không cố định vào giữa các record.

## 4. RandomAccessFile: con trỏ đọc/ghi duy nhất

`java.io.RandomAccessFile` cung cấp API đọc/ghi dữ liệu ở những vị trí tùy chọn trên file. Một số phương thức cốt lõi:

```java
RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
file.seek(byteOffset);
long position = file.getFilePointer();
int id = file.readInt();
long amount = file.readLong();
file.writeInt(newStatus);
long size = file.length();
```

Đây là đoạn minh họa: cần khai báo `path` và `byteOffset`, xử lý `IOException` và đóng tài nguyên.

**`seek(long pos)`** đặt file pointer tới một byte offset **tuyệt đối tính từ đầu tệp**, không phải chỉ số bản ghi. Sau đó các phương thức đọc/ghi tiếp tục tại file pointer hiện tại:

- `readInt()` đọc đúng 4 byte, trả `int` và tăng file pointer 4.
- `readLong()` đọc đúng 8 byte, trả `long` và tăng file pointer 8.
- `writeInt(x)` ghi 4 byte và tăng file pointer 4.
- `writeLong(x)` ghi 8 byte và tăng file pointer 8.
- `readFully(byte[])` cố đọc đủ byte của mảng, ném `EOFException` nếu gặp EOF trước khi đủ.
- `length()` lấy kích thước tệp theo byte.
- `setLength(newLength)` thay đổi chiều dài tệp, cần xem xét truncate/extend.

Ví dụ đọc bản ghi `i=1`:

```java
file.seek(1L * 16);
int id = file.readInt();       // Pointer: 20
long balance = file.readLong();// Pointer: 28
int status = file.readInt();   // Pointer: 32
```

**File pointer đã bị thay đổi.** Nếu method khác gọi `file.readInt()` ngay sau đó mà không `seek`, nó đọc từ offset 32, tức đầu bản ghi tiếp theo, không tự quay về offset 16.

Điểm mạnh là code đọc primitive ngắn gọn. Điểm cần quản lý là **trạng thái vị trí đọc/ghi chung trong cùng một `RandomAccessFile`**.

### Chế độ mở

| Mode | Ý nghĩa |
| --- | --- |
| `"r"` | Chỉ đọc, không cho phép ghi |
| `"rw"` | Đọc và ghi |
| `"rws"` | Đọc/ghi, yêu cầu đồng bộ dữ liệu và metadata theo hợp đồng |
| `"rwd"` | Đọc/ghi, yêu cầu đồng bộ nội dung file theo hợp đồng, có khác biệt so với `"rws"` về metadata |

Các mode có đồng bộ cao hơn có thể làm chi phí ghi khác đi đáng kể; **không dùng `"rws"` để benchmark đối đầu với FileChannel không force**, vì đó là hai mức bảo đảm dữ liệu không tương đương.

## 5. FileChannel: positional read/write với ByteBuffer

`java.nio.channels.FileChannel` cũng có thể đọc/ghi theo offset tùy ý:

```java
int read(ByteBuffer dst, long position) throws IOException;
int write(ByteBuffer src, long position) throws IOException;
```

Hai overload này có tính chất quan trọng:

1. `position` là **byte offset tuyệt đối** trên file, không phải index bản ghi.
2. `read(dst, position)` không làm thay đổi `channel.position()`.
3. `write(src, position)` không làm thay đổi `channel.position()`.
4. **`ByteBuffer.position()` vẫn thay đổi** theo số byte đọc/ghi thực tế.
5. Một lời gọi có thể trả **ít byte hơn yêu cầu**; phải loop cho đến khi đủ hoặc nhận EOF/lỗi.
6. `FileChannel` cho phép quản lý chiều dài bằng `size()`/`truncate(...)` và các thao tác NIO khác.

Ví dụ ý tưởng:

```java
ByteBuffer buffer = ByteBuffer.allocate(16);
buffer.order(ByteOrder.BIG_ENDIAN);

long offset = 1L * 16;
int received = channel.read(buffer, offset);

// channel.position() giữ nguyên;
// buffer.position() có thể tăng theo số byte received.
```

Đoạn này chỉ minh họa, chưa phải chương trình độc lập; `channel` cần được mở và `received` phải được kiểm tra. Không được gọi ngay `buffer.getInt()` khi buffer còn ở pha ghi sau `read` mà chưa `flip()`/quản lý vị trí phù hợp.

### Buffer position khác channel position

| Thành phần | `RandomAccessFile` | `FileChannel` positional overload |
| --- | --- | --- |
| Vị trí I/O | `getFilePointer()`; thay đổi qua `seek/read/write` | Offset truyền trực tiếp trong mỗi lời gọi |
| Position nội tại | File pointer thay đổi | `channel.position()` **không đổi** khi dùng overload offset |
| Buffer trung gian | Có thể dùng primitive methods | `ByteBuffer` với `position/limit/capacity` |
| Đọc primitive | `readInt()/readLong()` tự tiến con trỏ | `ByteBuffer.getInt()/getLong()` tiến buffer position |
| Partial read | `readInt`/`readLong` đòi đủ số byte, nếu thiếu ném EOFException | `read(ByteBuffer,offset)` có thể trả số byte nhỏ; ứng dụng tự loop |
| Byte order | Các primitive DataInput/DataOutput theo big-endian | Cấu hình `buffer.order(...)` tường minh |

**Sai lầm hay gặp:** nghe “positional I/O không đổi position” rồi tưởng cả `ByteBuffer.position()` cũng không đổi. Thực tế chỉ vị trí nội tại của **FileChannel** được giữ; buffer vẫn được tiêu thụ/điền dữ liệu.

## 6. Vòng đời ByteBuffer: clear, flip, get/put

Để mã hóa một bản ghi 16 byte:

```java
ByteBuffer bytes = ByteBuffer.allocate(16);
bytes.order(ByteOrder.BIG_ENDIAN);
bytes.putInt(1002);
bytes.putLong(7250L);
bytes.putInt(1);
bytes.flip();
```

Trước `flip()`, buffer đã có:

```text
position = 16
limit    = 16
capacity = 16
```

Sau `flip()`:

```text
position = 0
limit    = 16
capacity = 16
```

Lúc này `channel.write(bytes, offset)` lấy dữ liệu từ vùng `[position, limit)`. Sau mỗi lần ghi, `bytes.position` tăng tương ứng. Muốn gửi tiếp phần còn lại, **không gọi `clear()`**, vì `clear` có thể làm cho chương trình gửi lại byte cũ chưa đúng phạm vi.

Khi đọc:

```java
ByteBuffer bytes = ByteBuffer.allocate(16);
bytes.order(ByteOrder.BIG_ENDIAN);

// Lặp channel.read(bytes, ...) đến khi buffer đầy.
bytes.flip();
int id = bytes.getInt();
long balance = bytes.getLong();
int status = bytes.getInt();
```

Đoạn này chưa đủ độc lập vì thiếu phần đọc/lỗi và `channel`. `flip()` sau khi nhận đủ 16 byte chuẩn bị cho các phương thức `get`. Ba lần get tiêu thụ đúng 4+8+4=16 byte.

`ByteBuffer.clear()` **không xóa vật lý byte** trong backing array; nó chỉ thay position/limit. Nếu buffer được dùng lại cho dữ liệu nhạy cảm, cần chính sách làm sạch riêng, không coi `clear` là thao tác bảo mật.

## 7. Bảng so sánh trực tiếp

| Tiêu chí | `RandomAccessFile` | `FileChannel` |
| --- | --- | --- |
| Package | `java.io` | `java.nio.channels` |
| Mở tệp | `new RandomAccessFile(file, "r"/"rw")` | `FileChannel.open(path, StandardOpenOption...)` |
| Truy cập offset | `seek(offset)` | `read/write(buffer, offset)` |
| Thao tác I/O tiến vị trí đối tượng | Có, sau `seek/read/write` | Positional overload **không đổi** `channel.position` |
| Đọc/ghi số nguyên | `readInt/readLong/writeInt/writeLong` | `ByteBuffer.putInt/getInt` và `putLong/getLong` |
| Big-endian | Cố định cho primitive DataInput/Output | Cấu hình buffer, default BIG_ENDIAN |
| Khối lượng code cho record nhỏ | Thường ít | Nhiều thao tác buffer và vòng partial I/O |
| Buffer reuse/direct buffer | Không phải mô hình chính của API | Có thể tái sử dụng heap/direct `ByteBuffer` |
| Truy cập theo vị trí trong nhiều tác vụ | Phải quản lý file pointer, đồng bộ nếu dùng chung đối tượng | Có overload offset không dùng position chung; vẫn cần đồng bộ dữ liệu |
| Quản lý độ dài | `length/setLength` | `size/truncate` |
| File lock, transfer, mapping | Có `getChannel()` để dùng NIO | Cung cấp trực tiếp các phương thức NIO |
| Dữ liệu được cập nhật nguyên tử cả record? | Không bảo đảm | Không bảo đảm |
| Hiệu năng tuyệt đối | Phụ thuộc workload | Phụ thuộc workload |

**Không phải hai thế giới tách rời:** `RandomAccessFile.getChannel()` trả về một `FileChannel` gắn với chính file đó. Theo hợp đồng API, file pointer của `RandomAccessFile` và **position của channel trả về từ `getChannel()` được liên kết**. Vì thế nếu trộn `raf.seek`, `raf.readInt` và các phương thức channel **không positional**, một thao tác có thể ảnh hưởng vị trí mà thao tác tiếp theo của bên kia nhìn thấy.

Overload `channel.read(buffer, absoluteOffset)` vẫn không đổi position nội tại của channel; tuy nhiên chia sẻ stream và update record cần hiểu rõ ownership và concurrency. Ví dụ chính dưới đây mở **hai file riêng biệt** bằng hai API để so sánh kết quả byte một cách công bằng và tránh can thiệp file pointer ngoài ý muốn.

## 8. Ví dụ hoàn chỉnh Java 17: cùng layout, cùng thao tác, kết quả byte giống nhau

Ví dụ tự tạo một thư mục tạm và hai file chứa cùng ba bản ghi 16 byte. File thứ nhất được tạo/đọc/cập nhật **chỉ bằng RandomAccessFile**; file thứ hai được tạo/đọc/cập nhật **chỉ bằng FileChannel positional I/O**.

Chuỗi thực hiện:

1. Ghi ba bản ghi:
   - `(1001, 5000, 1)`.
   - `(1002, 7250, 1)`.
   - `(1003, 9000, 0)`.
2. Đọc record `index=1` ở offset 16 bằng hai API.
3. Đặt `channel.position(7)` để chứng minh positional I/O không làm đổi giá trị ấy.
4. Thay balance của record `index=1` thành `7777` bằng cả hai API.
5. Quan sát `raf.getFilePointer()==32` và `channel.position()==7`.
6. Đọc lại bản ghi đã sửa, so sánh nội dung toàn file qua `Files.mismatch`.

### Tệp `FixedRecordComparisonDemo.java`

```java
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FixedRecordComparisonDemo {
    static final int RECORD_BYTES = Integer.BYTES
            + Long.BYTES + Integer.BYTES; // 16

    record Entry(int id, long balance, int status) { }

    static long offset(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Negative record index");
        }
        return Math.multiplyExact(index, (long) RECORD_BYTES);
    }

    static void requireReadable(long offset, long size)
            throws IOException {
        if (size % RECORD_BYTES != 0) {
            throw new IOException("Misaligned record file");
        }
        if (offset > size - RECORD_BYTES) {
            throw new EOFException("Record is outside the file");
        }
    }

    static void requireWritable(long offset, long size)
            throws IOException {
        if (size % RECORD_BYTES != 0 || offset > size) {
            throw new IOException("Invalid write position");
        }
    }

    static void writeRaf(RandomAccessFile file, long index, Entry e)
            throws IOException {
        long pos = offset(index);
        requireWritable(pos, file.length());
        file.seek(pos);
        file.writeInt(e.id());
        file.writeLong(e.balance());
        file.writeInt(e.status());
    }

    static Entry readRaf(RandomAccessFile file, long index)
            throws IOException {
        long pos = offset(index);
        requireReadable(pos, file.length());
        file.seek(pos);
        int id = file.readInt();
        long balance = file.readLong();
        int status = file.readInt();
        return new Entry(id, balance, status);
    }

    static void writeChannel(FileChannel channel, long index, Entry e)
            throws IOException {
        long pos = offset(index);
        requireWritable(pos, channel.size());

        ByteBuffer bytes = ByteBuffer.allocate(RECORD_BYTES);
        bytes.order(ByteOrder.BIG_ENDIAN);
        bytes.putInt(e.id());
        bytes.putLong(e.balance());
        bytes.putInt(e.status());
        bytes.flip();

        int written = 0;
        while (bytes.hasRemaining()) {
            int n = channel.write(bytes, pos + written);
            if (n <= 0) {
                throw new IOException("Channel write made no progress");
            }
            written += n;
        }
    }

    static Entry readChannel(FileChannel channel, long index)
            throws IOException {
        long pos = offset(index);
        requireReadable(pos, channel.size());

        ByteBuffer bytes = ByteBuffer.allocate(RECORD_BYTES);
        bytes.order(ByteOrder.BIG_ENDIAN);

        int received = 0;
        while (bytes.hasRemaining()) {
            int n = channel.read(bytes, pos + received);
            if (n == -1) {
                throw new EOFException("Incomplete record");
            }
            if (n == 0) {
                throw new IOException("Channel read made no progress");
            }
            received += n;
        }

        bytes.flip();
        return new Entry(
                bytes.getInt(), bytes.getLong(), bytes.getInt());
    }

    static String describe(Entry e) {
        return "id=" + e.id() + ", balance=" + e.balance()
                + ", status=" + e.status();
    }

    public static void main(String[] args) throws IOException {
        Path folder = Files.createTempDirectory("records-");
        Path rafPath = folder.resolve("raf.bin");
        Path channelPath = folder.resolve("channel.bin");

        try {
            try (RandomAccessFile raf =
                         new RandomAccessFile(rafPath.toFile(), "rw");
                 FileChannel channel = FileChannel.open(
                         channelPath,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.READ,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {

                Entry[] initial = {
                        new Entry(1001, 5000L, 1),
                        new Entry(1002, 7250L, 1),
                        new Entry(1003, 9000L, 0)
                };

                for (int i = 0; i < initial.length; i++) {
                    writeRaf(raf, i, initial[i]);
                    writeChannel(channel, i, initial[i]);
                }

                System.out.println("Record bytes: " + RECORD_BYTES);
                System.out.println("Before RAF: "
                        + describe(readRaf(raf, 1)));

                channel.position(7);
                System.out.println("Before FileChannel: "
                        + describe(readChannel(channel, 1)));

                Entry updated = new Entry(1002, 7777L, 1);
                writeRaf(raf, 1, updated);
                writeChannel(channel, 1, updated);

                System.out.println("RAF pointer after update: "
                        + raf.getFilePointer());
                System.out.println("FileChannel position after update: "
                        + channel.position());

                System.out.println("After RAF: "
                        + describe(readRaf(raf, 1)));
                System.out.println("After FileChannel: "
                        + describe(readChannel(channel, 1)));
            }

            System.out.println("Byte identical: "
                    + (Files.mismatch(rafPath, channelPath) == -1));
            System.out.println("File bytes: " + Files.size(rafPath));
        } finally {
            Files.deleteIfExists(channelPath);
            Files.deleteIfExists(rafPath);
            Files.deleteIfExists(folder);
        }
    }
}
```

### Biên dịch và chạy

```bash
javac FixedRecordComparisonDemo.java
java FixedRecordComparisonDemo
```

**Stdout khi các thao tác file hoàn tất thành công:**

```text
Record bytes: 16
Before RAF: id=1002, balance=7250, status=1
Before FileChannel: id=1002, balance=7250, status=1
RAF pointer after update: 32
FileChannel position after update: 7
After RAF: id=1002, balance=7777, status=1
After FileChannel: id=1002, balance=7777, status=1
Byte identical: true
File bytes: 48
```

### Phân tích những điểm cần nắm

**Thứ nhất — layout giống nhau.** `RandomAccessFile.writeInt/writeLong` và `ByteBuffer.putInt/putLong` với `BIG_ENDIAN` tạo cùng thứ tự 16 byte, nên `Files.mismatch(...) == -1` khi hai file có cùng trạng thái cuối.

**Thứ hai — offset tính theo byte.** Record 1 bắt đầu ở offset `1*16=16`. Đọc bản ghi index 1 không có nghĩa gọi `seek(1)` hoặc `read(buffer,1)`.

**Thứ ba — pointer khác nhau.** `writeRaf(raf,1,...)` gọi `seek(16)` rồi ghi 16 byte, nên `raf.getFilePointer()==32`. Ngược lại, `writeChannel(channel,1,...)` chỉ dùng overload có offset, nên `channel.position()` giữ nguyên **7**.

**Thứ tư — partial I/O.** `FileChannel.read/write` trả về số byte thực truyền. Ví dụ theo dõi `received`/`written` để tự tăng file offset và không bỏ mất phần còn lại. Nếu channel trả 0 khi cần tiến triển, ví dụ báo lỗi thay vì quay CPU vô hạn.

**Thứ năm — kiểm tra kích thước trước khi truy cập.** `requireReadable` kiểm tra file có số byte chia hết cho 16 và index còn trong vùng. `requireWritable` chỉ cho phép ghi đè record có sẵn hoặc append đúng cuối file, không vô tình tạo khoảng trống nếu offset nằm ngoài file.

**Thứ sáu — kiểm chứng chức năng khác benchmark.** Ví dụ kiểm tra ba cách phản ánh cùng nội dung, **không đo thời gian** và không chứng minh cách nào nhanh hơn.

### Vì sao FileChannel dùng positional overload?

Nếu dùng `channel.position(offset); channel.read(buffer);`, code vẫn có thể đúng nhưng nó thay đổi channel position chung. Trong ví dụ này, positional overload thể hiện rõ hợp đồng **đọc/ghi ở offset chỉ định mà không thay đổi vị trí hiện tại của channel**. Nó tiện khi các thao tác cần nhảy giữa các record bất kỳ, đặc biệt nếu không muốn thao tác này làm lệch vị trí tuần tự của thao tác khác.

Vẫn cần chú ý đồng bộ hóa cập nhật dữ liệu, vì “không đổi channel position” **không có nghĩa** đọc và sửa nhiều field của một record trở thành transaction nguyên tử.

## 9. Một chương trình khác: phát hiện file chứa bản ghi dở dang

Một lỗi thường gặp là file kết thúc **giữa** bản ghi. Ví dụ đã định nghĩa record 16 byte nhưng file dài 31 byte: có một record hoàn chỉnh và 15 byte của record thứ hai, không phải hai record đầy đủ.

Chương trình dưới đây tạo file 32 byte, xác nhận đọc được record index 1, từ chối index 2 và một index quá lớn, sau đó thu ngắn file còn 31 byte. Nó dùng cả `RandomAccessFile.length()` lẫn `FileChannel.size()` để chỉ ra hai API cùng nhìn thấy kích thước file thực.

### Tệp `FixedRecordValidationDemo.java`

```java
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FixedRecordValidationDemo {
    private static final int RECORD_BYTES = 16;

    static long checkedOffset(long index, long fileSize)
            throws IOException {
        if (fileSize < 0 || fileSize % RECORD_BYTES != 0) {
            throw new IOException("Misaligned file");
        }
        if (index < 0) {
            throw new IOException("Negative record index");
        }

        long offset;
        try {
            offset = Math.multiplyExact(index, (long) RECORD_BYTES);
        } catch (ArithmeticException ex) {
            throw new IOException("Record offset overflow", ex);
        }

        if (offset > fileSize - RECORD_BYTES) {
            throw new EOFException("Record out of range");
        }
        return offset;
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("validate-records-", ".bin");

        try {
            Files.write(file, new byte[32]);

            try (RandomAccessFile raf =
                         new RandomAccessFile(file.toFile(), "rw");
                 FileChannel channel = FileChannel.open(
                         file, StandardOpenOption.READ)) {

                System.out.println("RAF length: " + raf.length());
                System.out.println("Channel size: " + channel.size());

                System.out.println("Record 1 offset: "
                        + checkedOffset(1, raf.length()));

                try {
                    checkedOffset(2, channel.size());
                } catch (EOFException ex) {
                    System.out.println("Out of range: "
                            + ex.getClass().getSimpleName());
                }

                try {
                    checkedOffset(Long.MAX_VALUE, channel.size());
                } catch (IOException ex) {
                    System.out.println("Huge index: " + ex.getMessage());
                }

                raf.setLength(31);
                System.out.println("Aligned after truncate: "
                        + (channel.size() % RECORD_BYTES == 0));

                try {
                    checkedOffset(0, channel.size());
                } catch (IOException ex) {
                    System.out.println("Truncated record: "
                            + ex.getMessage());
                }
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FixedRecordValidationDemo.java
java FixedRecordValidationDemo
```

**Stdout khi I/O thành công:**

```text
RAF length: 32
Channel size: 32
Record 1 offset: 16
Out of range: EOFException
Huge index: Record offset overflow
Aligned after truncate: false
Truncated record: Misaligned file
```

**Vì sao thứ tự kiểm tra quan trọng?** Với `index=Long.MAX_VALUE`, phép nhân `index*16` không thể biểu diễn bằng `long`. `Math.multiplyExact` phát hiện lỗi trước khi chương trình đưa offset âm hoặc tràn vào `seek`/`read`. Với file 31 byte, phép `size % 16 != 0` phát hiện layout hỏng trước khi đọc, thay vì diễn giải 15 byte dở dang như dữ liệu hợp lệ.

Bài học: **fixed-length** không tự làm file luôn hợp lệ. File có thể bị cắt do crash, ghi dở, thao tác truncate, format không tương thích hoặc hỏng dữ liệu. Phải kiểm tra cấu trúc trước khi truy cập và có chiến lược phục hồi/chặn sử dụng file bất thường.

## 10. Đọc một record: EOF và partial read

**`RandomAccessFile.readInt/readLong`** cố lấy đủ số byte cho primitive tương ứng. Nếu không đủ do EOF, nó ném `EOFException`. Tuy nhiên, thành công sau khi đọc một trường **không chứng minh toàn bộ record hợp lệ**: có thể đọc được `id` nhưng thiếu vài byte của `balance` hoặc `status`.

Với **`FileChannel.read(ByteBuffer,long)`**, kết quả có thể là:

| Kết quả | Ý nghĩa |
| --- | --- |
| `n > 0` | Đã đọc `n` byte, buffer position tăng `n` |
| `n == 0` | Lời gọi hiện tại không đọc được byte; có thể liên quan buffer, trạng thái I/O hoặc điều kiện tiến triển |
| `n == -1` | Gặp EOF |

Đừng gọi một lần `read(buffer,offset)` rồi giả định buffer 16 byte đã đầy. Vòng lặp đúng:

```java
int total = 0;

while (buffer.hasRemaining()) {
    int n = channel.read(buffer, recordOffset + total);

    if (n == -1) {
        throw new EOFException("Incomplete fixed record");
    }
    if (n == 0) {
        throw new IOException("No progress");
    }
    total += n;
}

buffer.flip();
// Giải mã từ buffer.
```

Đoạn mã minh họa này cần `channel`, `recordOffset`, `buffer`, import và cơ chế đóng. Khi I/O xảy ra trên file **đang bị ghi/truncate đồng thời**, kiểm tra size ban đầu không loại bỏ race — vẫn cần xử lý EOF hoặc lỗi khác khi đọc.

**Không dùng `available()` để tính số byte còn lại của một RandomAccessFile:** `RandomAccessFile` không dùng API `InputStream.available()` cho hợp đồng này. Dùng `length` và `getFilePointer` hoặc kiểm tra theo frame/layout, nhưng vẫn có thể gặp thay đổi do tiến trình khác.

## 11. Ghi một record: partial write, append và holes

### Với RandomAccessFile

`seek(offset)` sau đó `writeInt/writeLong/writeInt` ghi các primitive theo thứ tự. Nếu ghi thất bại giữa chừng, file có thể bị **cập nhật một phần**, ví dụ `id` đã đổi nhưng `balance` chưa đổi. Cần xử lý kết quả nghiệp vụ như thất bại, không mặc định toàn record đã được cập nhật.

### Với FileChannel

`write(buffer,offset)` có thể ghi ít hơn số byte `remaining`. Vì vậy cần:

```java
int sent = 0;
while (buffer.hasRemaining()) {
    int n = channel.write(buffer, recordOffset + sent);
    if (n <= 0) {
        throw new IOException("Partial write made no progress");
    }
    sent += n;
}
```

Hai cách đều có thể viết tới offset lớn hơn kích thước file. **Ghi vượt EOF có thể mở rộng file và tạo một khoảng trống**; nội dung khoảng trống phụ thuộc file system/API và không nên được coi là một vùng record hợp lệ tự động. Nếu ứng dụng chỉ cho append hoặc update, cần quy tắc:

- **Update:** `0 <= index < recordCount` và size phải chia hết `RECORD_BYTES`.
- **Append:** `index == recordCount` và chỉ ghi một record hoàn chỉnh.
- **Không cho phép:** `index > recordCount` nếu không có quy trình cấp phát/khởi tạo các record trung gian.

Ví dụ chính chọn `offset <= fileSize` cho write; nếu file size là bội số 16, điều đó cho phép update hoặc append mà không tạo hole.

**Chú ý:** hai thao tác đồng thời có thể cùng thấy kích thước ban đầu rồi đều cố append tại cùng offset. Cần đồng bộ hóa/lock hoặc một cơ chế cấp offset nguyên tử theo kiến trúc ứng dụng. Một hàm kiểm tra size đơn lẻ **không** bảo đảm tính an toàn concurrent.

## 12. Vị trí chia sẻ khi dùng `RandomAccessFile.getChannel()`

`RandomAccessFile.getChannel()` tạo/trả channel liên kết với cùng file. Theo hợp đồng:

- `raf.getFilePointer()` và `raf.getChannel().position()` biểu diễn vị trí chung cho thao tác tuần tự.
- `raf.seek(100)` ảnh hưởng position của channel liên kết.
- `channel.position(200)` ảnh hưởng file pointer của RAF.
- `raf.readInt()` sẽ tiến con trỏ tương ứng, channel thấy vị trí mới.
- `channel.read(buffer, absoluteOffset)` là positional operation, nên không làm đổi channel position; vì vậy không làm đổi shared pointer do chính positional call.
- Thay chiều dài tệp qua một interface cũng được phản ánh qua interface khác.

Ví dụ minh họa:

```java
try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
    FileChannel linked = raf.getChannel();

    raf.seek(8);
    System.out.println(linked.position()); // 8

    linked.position(16);
    System.out.println(raf.getFilePointer()); // 16
}
```

Đây là đoạn minh họa chứ không phải chương trình độc lập, vì `file` chưa được khai báo và thiếu import. Không đồng thời đóng `linked` và tiếp tục dùng `raf` rồi kỳ vọng file vẫn mở: channel/RAF liên kết cùng tài nguyên. Khi một bên bị đóng, các thao tác còn lại có thể không dùng được.

**Điểm dễ sai trong multithreading:** thread A gọi `raf.seek(offsetA)`, thread B gọi `raf.seek(offsetB)`, rồi thread A `raf.readInt()` có thể đọc nhầm offset nếu không đồng bộ toàn bộ chuỗi seek+read. Positional FileChannel giúp **tránh cạnh tranh về position** của channel, nhưng **không** tự ngăn nhiều thread cập nhật chồng lên cùng 16 byte hoặc đảm bảo snapshot nhất quán.

## 13. Vấn đề byte order và tính tương thích định dạng

### Primitive methods của RAF

`RandomAccessFile` triển khai `DataInput` và `DataOutput`, theo đó `writeInt`, `writeLong`, `readInt` và `readLong` dùng big-endian cho byte nhiều byte. Nó không có một `order(LITTLE_ENDIAN)` tương đương trực tiếp với `ByteBuffer.order`.

### ByteBuffer của FileChannel

`ByteBuffer` dùng big-endian mặc định khi mới tạo, nhưng có thể cấu hình:

```java
ByteBuffer buffer = ByteBuffer.allocate(16)
        .order(ByteOrder.LITTLE_ENDIAN);
```

Nếu viết file little-endian bằng `ByteBuffer` rồi đọc primitive bằng RAF big-endian, số nguyên sẽ bị diễn giải sai. Phải **định nghĩa endian trong schema của file**, không để mặc định ngầm.

Với file dùng nhiều kiểu, đừng suy rằng có thể gọi `ByteBuffer.order(LITTLE_ENDIAN)` mà `RandomAccessFile.readInt()` tự thay đổi. Nếu muốn dùng RAF đọc little-endian, cần tự kết hợp byte hoặc dùng lớp hỗ trợ phù hợp.

### File format nên có version

Bản ghi cố định 16 byte trong ví dụ không có file header. Đối với ứng dụng thật, nên cân nhắc:

```text
Header:
  magic            4 bytes
  version          2 bytes
  record size      2 bytes
  record count     8 bytes (nếu cần)
Records:
  16 bytes * count
```

Khi thêm header, offset là `headerSize + index * recordSize`, phải tính bằng `long` và kiểm tra tràn. Những thông số trong header do file không tin cậy cung cấp cần được xác thực trước khi cấp phát buffer hoặc tính offset.

Nếu bản schema thay đổi từ 16 sang 24 byte, code cũ không thể đọc file mới bằng phép `i*16` và mong đúng. Tên API không tự bảo vệ bạn khỏi sai phiên bản định dạng.

## 14. Thêm tên hoặc chuỗi vào bản ghi cố định

Với `int/long`, chiều dài trường là hằng số. Với `String`, **số ký tự không phải số byte**:

- Chuỗi `"ABC"` trong UTF-8 dài 3 byte.
- Chuỗi tiếng Việt có dấu có thể dài nhiều byte hơn số ký tự hiển thị.
- Emoji có thể chiếm 4 byte UTF-8 nhưng 2 UTF-16 code units.

Nếu muốn mỗi tên chiếm chính xác 20 byte, phải xác định:

1. Charset, thường là UTF-8.
2. Chính sách nếu tên vượt 20 byte (từ chối hoặc cắt tại ranh giới ký tự, không tự cắt byte gây chuỗi sai).
3. Cách padding phần còn thiếu (ví dụ byte 0).
4. Cách xác định độ dài thực khi đọc (prefix length hoặc cắt trailing padding có kiểm soát).
5. Chính sách với NUL byte trong nội dung nếu dùng NUL làm marker.
6. Cách chuẩn hóa Unicode nếu hệ thống so sánh tên theo ký tự.

`RandomAccessFile.writeUTF()` **không phù hợp để tự động tạo field 20 byte cố định**, vì nó dùng *modified UTF-8* với prefix độ dài và số byte biến thiên. `ByteBuffer.put(text.getBytes(UTF_8))` cũng không tự làm trường cố định.

Trong bài này, record chỉ dùng số nguyên để cô lập phần quan trọng: **cùng 16 byte, cùng endian, cùng offset**.

## 15. Atomicity, lock và tính nhất quán

Một bản ghi 16 byte bao gồm nhiều field. Việc gọi ba `raf.write...` hoặc nhiều `FileChannel.write` không tạo transaction nguyên tử. Nếu chương trình crash giữa update, file có thể mang trạng thái hỗn hợp.

Các tình huống cần kiểm soát:

- Hai thread cập nhật cùng record.
- Một thread đọc record trong lúc thread khác đang ghi.
- Hai tiến trình cùng append vào cuối file.
- Tiến trình bị tắt đột ngột sau khi ghi một phần.
- File bị truncate hoặc đổi format bởi tiến trình khác.

**`FileLock`** (qua channel) có thể hỗ trợ phối hợp quyền truy cập, nhưng giới hạn/phạm vi và việc tuân thủ lock phụ thuộc hệ điều hành/file system/tiến trình; lock không biến nhiều lời ghi thành transaction chống crash. Không dùng “có khóa file” như lời hứa độ bền dữ liệu sau mất điện.

Các giải pháp tùy yêu cầu:

- Với một process đơn giản, đồng bộ hóa toàn bộ cập nhật record trong cùng critical section.
- Với nhiều process, dùng file locking và protocol nhất quán.
- Với update cần crash consistency, dùng write-ahead log/journal, hai bản sao record kèm version/checksum, hoặc transactional database.
- Với read cần snapshot, dùng version number và xác minh lại trước/sau đọc (kèm giao thức phù hợp).
- Với công việc thực tế mang tính giao dịch, cân nhắc dùng database thay vì tự xây engine record nhị phân.

**Positional I/O chỉ tránh thay đổi channel position. Nó không tự bảo đảm atomicity của record, isolation hay durability.**

## 16. Tính bền vững dữ liệu: khác biệt sync/force

Khi `write...` hoàn tất, dữ liệu đã được chuyển qua API theo hợp đồng I/O, nhưng **không mặc định đã nằm bền vững trên thiết bị lưu trữ** nếu ứng dụng/OS có cache.

Với RandomAccessFile:

- `"rw"` không giống `"rwd"`/`"rws"` về chính sách đồng bộ.
- `raf.getFD().sync()` hoặc chính sách cụ thể có thể dùng khi cần yêu cầu đồng bộ.
- `raf.getChannel().force(true)` là cách tương ứng ở NIO.

Với FileChannel:

```java
channel.force(true);
```

Đoạn này giả sử channel được mở có quyền phù hợp. `force(boolean metaData)` yêu cầu đẩy các cập nhật theo hợp đồng; `true` quan tâm cả metadata liên quan, còn `false` có thể chỉ tập trung nội dung tùy API. Các giới hạn với filesystem từ xa/thiết bị đặc biệt vẫn phải được xét.

**Không so benchmark `RandomAccessFile("rws")` với `FileChannel.open(..., WRITE)` không gọi `force` rồi tuyên bố FileChannel nhanh hơn.** Hai phương án đang yêu cầu mức đồng bộ khác nhau. Hãy định nghĩa cùng yêu cầu durability trước khi đo.

## 17. Hiệu năng: bản ghi nhỏ, random access và nhiều lượt truy cập

So sánh hai API về performance nên xét workload:

| Workload | RAF có thể có ưu thế | FileChannel có thể có ưu thế |
| --- | --- | --- |
| Đọc vài primitive từ một record | `readInt/readLong` ngắn gọn, ít lớp buffer do ứng dụng quản lý | Positional read giúp điều phối offset tường minh |
| Nhiều record liên tiếp | Đọc tuần tự bằng pointer, code đơn giản | Buffer reuse hoặc đọc theo block để giảm số lời gọi |
| Random read theo index | `seek` rồi đọc primitive | `read(buffer, absoluteOffset)` không cần thay channel position |
| Nhiều thread đọc vùng khác nhau | RAF dùng chung cần kiểm soát seek+read | Positional read không đụng position chung, nhưng vẫn bị giới hạn I/O thật |
| Cập nhật record cùng file | `seek + write...` dễ hiểu | `write(buffer, offset)` với buffer encode và retry partial |
| Ghi số lượng lớn | Có thể dùng API nhị phân trực tiếp | Buffer reuse, batching, scattering/gathering khi thích hợp |
| File cực lớn | Offset `long` cho phép seek rộng | `long` offset, size, transfer/mapping và nhiều lựa chọn |
| Dễ đọc/dễ kiểm chứng code | Primitive read/write rõ ràng | Có thêm position/limit/flip/order cần đúng |

**Không có kết luận tổng quát ai nhanh hơn.** FileChannel có thể cần nhiều mã ứng dụng hơn cho record 16 byte vì phải đóng gói primitive vào buffer. Ngược lại, nếu đọc nhiều record trong một block, `FileChannel` có thể giảm chi phí lời gọi cấp thấp so với từng lượt `RAF.readInt()`, tùy cách thiết kế.

Cả hai đều chịu ảnh hưởng bởi OS page cache, SSD/HDD, filesystem, JDK, độ dài file, pattern truy cập và concurrency. Với truy cập random nhiều record nhỏ, chi phí seek vật lý trên HDD có thể chi phối; với file trong page cache, overhead CPU và cách cấp phát buffer có thể nổi bật hơn.

## 18. Benchmark công bằng cho bài thực hành

Để đo tốc độ một cách có ý nghĩa, cần:

1. Tạo **cùng tập record**, cùng layout, cùng endianness và cùng kích thước file.
2. Có cùng số lượng thao tác và cùng mẫu index (sequential, uniform random, Zipf/hotspot...).
3. Với RAF, **không** so từng `readInt` trên mỗi field với FileChannel đọc một block 1 MiB rồi suy ra API nhanh hơn; đó là hai chiến lược batching khác nhau.
4. Với FileChannel, tránh cấp `ByteBuffer.allocate(16)` mỗi lần nếu mục đích đo throughput steady state; thử thêm buffer reuse.
5. Kiểm tra kết quả mỗi phương án giống nhau, không bỏ sót partial read/write.
6. Warmup JVM và chạy nhiều lượt, đảo thứ tự thử nghiệm.
7. Phân biệt **cold vs warm page cache**; thuật toán chạy sau có thể hưởng file đã được thuật toán trước làm nóng.
8. Báo môi trường: JDK, OS, filesystem, thiết bị, heap, buffer size, số records và threads.
9. Tách chi phí mở file, tạo buffer, encode/decode, thao tác I/O và kiểm tra kết quả nếu cần phân tích bottleneck.
10. Đo cả throughput, p50/p95 latency, allocation/GC và áp lực bộ nhớ thay vì chỉ milliseconds một lượt.

Một bộ benchmark mini có thể kiểm tra:
- 1.000 bản ghi và 1 triệu random reads lặp lại.
- 1 triệu bản ghi và random read không đều.
- 1 triệu bản ghi và scan tuần tự.
- 100.000 cập nhật record phân tán.
- 1 thread so với 4/16 thread; từng thread có handle/buffer riêng hoặc dùng shared channel với hợp đồng rõ.

**Đó chỉ là workload ví dụ, không phải số liệu đo có sẵn.** Nếu chạy trên SSD với file đã nằm trong cache, kết quả có thể khác hẳn mạng/NFS/HDD. Không công bố “NIO nhanh hơn 2 lần” chỉ từ một ví dụ 48 byte.

## 19. Nếu cần đọc nhiều record cùng lúc

Thiết kế ở ví dụ chính tạo một `ByteBuffer` 16 byte cho mỗi lần đọc. Đối với bài học, nó giúp phân tách rõ thao tác. Nhưng nếu ứng dụng scan hàng triệu records, nên cân nhắc **batching**:

- Một buffer chứa, chẳng hạn, `4096` record 16 byte = 65.536 byte.
- Đọc một khối lớn rồi lặp `getInt/getLong/getInt` để giải mã record trong RAM.
- Đặt lại buffer theo `clear/flip` khi chuyển block.
- Xử lý cuối file nếu khối trả về không tròn record.
- Giữ cùng byte order và ranh giới schema.
- Benchmark buffer heap/direct, kích thước block và số lượng record.

`RandomAccessFile` cũng có thể `readFully(byte[])` một khối lớn, rồi giải mã bằng `ByteBuffer.wrap(array).order(BIG_ENDIAN)` hoặc parser riêng. Vì vậy **batching không độc quyền của FileChannel**.

Một ví dụ tính toán:

```text
16 bytes/record * 4096 records = 65536 bytes
65536 bytes = 64 KiB
```

Với workload tuần tự, số lần đọc khối có thể ít hơn rất nhiều so với đọc một record/lần. Nhưng nếu ứng dụng thường chỉ cần một record ở offset ngẫu nhiên, đọc nguyên block 64 KiB mỗi lần có thể lãng phí I/O và băng thông cache.

## 20. Khi nào chọn RandomAccessFile?

`RandomAccessFile` là lựa chọn dễ hiểu khi:

- Bạn cần ví dụ nhập môn về file pointer/`seek`/primitive read/write.
- Schema chứa các giá trị nhị phân cố định, big-endian.
- Ứng dụng ít luồng, ít tranh chấp, cập nhật theo index đơn giản.
- Không cần thao tác NIO đặc biệt như `transferTo` hoặc mapped buffer.
- Muốn code ngắn, ít chi tiết `ByteBuffer.position/limit`.

Nhược điểm cần nhớ:

- `seek` và read/write sau nó tạo **một chuỗi thao tác phụ thuộc shared pointer**.
- Primitive big-endian cố định, không có `ByteOrder` của buffer.
- Một record gồm nhiều lệnh ghi có thể bị ghi dở.
- Khi cần song song hóa hoặc tích hợp pipeline buffer NIO, có thể phải quản lý thêm `getChannel` hoặc chuyển sang API khác.

Không nên loại bỏ RAF chỉ vì nó nằm trong package `java.io`. Trong một bài toán record đơn giản, nó có thể **dễ kiểm tra và bảo trì hơn**.

## 21. Khi nào chọn FileChannel?

`FileChannel` thường phù hợp khi:

- Cần positional I/O theo offset một cách rõ ràng, độc lập với channel position.
- Cần đọc/ghi theo `ByteBuffer` và điều phối với NIO API.
- Muốn tái sử dụng buffer để quét các record theo khối lớn.
- Cần `FileLock`, `force`, `transferTo/transferFrom` hoặc `map`.
- Thiết kế chia nhiều tác vụ theo offset mà không muốn từng tác vụ thay shared file position.

Nhược điểm cần nhớ:

- Phải quản lý `ByteBuffer.order` và trạng thái `position/limit/flip`.
- Đọc/ghi có thể partial; phải dùng vòng lặp theo `remaining`.
- Nếu tạo buffer mỗi record, có thể làm tăng allocation pressure.
- Nếu dùng `FileChannel.map`, lại có thêm chi phí và vấn đề vòng đời mapping; mapping **không tự mặc định nhanh hơn**.
- Positional read/write không giải quyết race khi nhiều thread cập nhật cùng record.

**Chọn FileChannel vì semantics/capabilities phù hợp, rồi tối ưu bằng đo đạc**, thay vì chọn chỉ vì tưởng NIO được bảo đảm nhanh.

## 22. Lỗi thường gặp trong bài tập

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| `seek(index)` thay vì `seek(index*16)` | Đọc giữa record | Dùng byte offset có công thức |
| Dùng `int index*recordSize` rồi ép sang long | Integer overflow | `Math.multiplyExact(index, (long) recordSize)` |
| Không kiểm tra index âm | Offset không hợp lệ | Validate trước |
| Tưởng `getFilePointer()` luôn là index record | Sai đơn vị | Pointer tính bằng **byte** |
| Dùng `raf.readInt()` sau seek và tưởng pointer không đổi | Đọc sai lần kế tiếp | Rõ ràng vị trí trước mỗi record |
| Tưởng `FileChannel.read(buffer, offset)` luôn lấy đủ 16 byte | Record decode thiếu dữ liệu | Retry partial read đến đủ/EOF |
| Tưởng positional I/O không đổi `ByteBuffer.position()` | Buffer state bị xử lý sai | Phân biệt channel position và buffer position |
| Quên `flip()` trước khi write buffer sau `putInt` | Không còn `remaining` để ghi | `flip` sau encode |
| Quên `flip()` trước `getInt` sau read | Đọc sai pha | `flip` sau nhận đủ |
| RAF big-endian, channel little-endian | Giá trị số khác nhau | Cố định byte order |
| Dùng `writeUTF` làm trường chuỗi fixed-size | Trường có độ dài biến thiên | Tự định nghĩa field theo byte |
| Tệp dài không chia hết cho 16 | Record cuối bị cắt | Validate `size % 16` |
| Ghi offset ngoài EOF và tin record gap tự hợp lệ | Hole hoặc dữ liệu không xác định | Chỉ update/append ở offset hợp lệ |
| Cập nhật 16 byte và tin atomically | Torn record khi crash/race | Lock/protocol/journal tùy yêu cầu |
| Dùng chung RAF và channel rồi không hiểu shared position | Seek/read ảnh hưởng nhau | Ownership rõ hoặc positional overload |
| Quên đóng file | Rò rỉ descriptor | `try-with-resources` |
| So tốc độ khi cách ghi khác mức durability | Benchmark không tương đương | Cùng mode sync/force |
| Dùng `FileChannel.map` và tin sẽ luôn nhanh hơn | Page faults và cleanup có chi phí | Benchmark theo workload |
| Không kiểm tra checksum/kết quả khi benchmark | Đo phương án thiếu dữ liệu | Verify byte/record count |
| Đổi schema mà không version file | Mã cũ giải mã sai offset | Header/magic/version/record size |

## 23. Câu hỏi tự kiểm tra

**Câu 1.** Record dài 16 byte, chỉ số bắt đầu từ 0. Record số 25 bắt đầu ở offset bao nhiêu?

**Đáp án:** `25 × 16 = 400` byte. `seek(25)` là sai vì offset không phải record index.

**Câu 2.** `RandomAccessFile.seek(16)`, tiếp theo `readInt()`, `readLong()` và `readInt()`. Pointer ở đâu?

**Đáp án:** `16 + 4 + 8 + 4 = 32`. Đọc một record đủ 16 byte làm pointer chuyển tới đầu record kế tiếp.

**Câu 3.** `FileChannel.position()==7`. Gọi `channel.read(buffer, 16)` và đọc được 16 byte. `channel.position()` có thành 23 không?

**Đáp án:** **Không.** Với overload positional, channel position vẫn là 7; **buffer position** tăng theo 16 byte nhận được.

**Câu 4.** File dài 48 byte, record size 16. Đọc index 3 có hợp lệ không?

**Đáp án:** **Không.** File có 3 records, index hợp lệ là 0,1,2. Offset 48 là vị trí ngay sau EOF, không chứa 16 byte record mới.

**Câu 5.** Để append một record mới vào file hợp lệ dài 48 byte, bắt đầu ghi ở offset nào?

**Đáp án:** **48**. Khi ghi đủ 16 byte, kích thước file sẽ thành 64 byte. Nếu index là 4 (offset 64) khi file đang dài 48, ứng dụng có thể tạo hole; bài này từ chối thao tác ấy.

**Câu 6.** RAF ghi balance bằng `writeLong(7250L)`; ByteBuffer đặt `LITTLE_ENDIAN` để đọc bằng `getLong()`. Có được cùng số không?

**Đáp án:** Không được bảo đảm; thông thường sẽ **khác**. RAF ghi big-endian. Muốn cùng kết quả phải đọc với big-endian hoặc chủ động chuyển đổi.

**Câu 7.** Hai thread gọi `raf.seek` và `raf.readInt` trên cùng một RAF. Không có đồng bộ thì có nguy cơ gì?

**Đáp án:** Thread khác có thể thay đổi shared file pointer ở giữa các bước, làm đọc nhầm offset. Cần đồng bộ cả chuỗi hoặc thiết kế I/O theo offset không dùng chung con trỏ, đồng thời kiểm soát cập nhật dữ liệu.

**Câu 8.** Một file 32 byte bị truncate còn 31 byte. Có thể dùng `file.length()/16` rồi âm thầm bỏ 15 byte cuối không?

**Đáp án:** Đó là **cách làm mất dấu hiệu hỏng dữ liệu**. Với format fixed 16 byte, `length % 16 != 0` là tín hiệu bất thường cần báo/khôi phục theo chính sách, không tự coi file hợp lệ.

**Câu 9.** Tại sao chương trình so sánh hai file trả “Byte identical: true” nhưng không kết luận được hiệu năng?

**Đáp án:** Vì đây là **kiểm chứng correctness**. Không có phép đo latency/throughput, warmup, kiểm soát page cache hoặc số lượt chạy. Kết quả bằng byte chỉ chứng minh hai API đã thực hiện cùng thay đổi.

## 24. Checklist áp dụng cho hệ thống thực tế

Trước khi đọc/ghi:

- Định nghĩa record schema và version, mỗi field có offset/byte order/độ dài rõ.
- Xác thực index, phép tính offset và giới hạn `long`.
- Kiểm tra file size và `size % recordBytes == 0`.
- Xác định policy cho update, append và file corrupt.
- Chọn API theo nhu cầu: primitive `seek` hay positional buffer I/O.

Khi thực hiện:

- Với RAF: `seek` đúng offset, đọc/ghi đúng thứ tự, kiểm soát shared pointer.
- Với FileChannel: cấu hình ByteBuffer order, loop partial I/O, `flip` đúng pha, không nhầm buffer/channel position.
- Khi append hoặc update đồng thời: thiết kế khóa, thứ tự thao tác, retry và consistency.
- Nếu cần durability: xác định chính sách sync/force phù hợp trước khi benchmark.
- Khi file rất lớn: dùng buffer reuse/batching nếu workload cho phép, nhưng không giả định luôn nhanh.

Sau khi thực hiện:

- Kiểm tra kích thước, byte count, decoded fields và trường hợp EOF.
- Đóng stream/channel bằng `try-with-resources`.
- Có kế hoạch khôi phục torn record do crash hoặc ghi dở.
- Đo benchmark với input và điều kiện giống nhau trước khi quyết định tối ưu.
- Test ranh giới: file trống, một record, record cuối, index âm, index == recordCount, chỉ số tràn, record bị cắt, byte order sai, update đồng thời.

## 25. Tổng kết

**`RandomAccessFile` và `FileChannel` đều phù hợp để đọc/ghi bản ghi cố định theo offset.**

- `RandomAccessFile` dùng `seek(byteOffset)` và primitive `readInt/readLong/writeInt/writeLong`. Nó có **file pointer chung** và pointer tăng theo số byte I/O.
- `FileChannel` dùng `read/write(ByteBuffer, absoluteOffset)`. Positional overload giữ nguyên `channel.position()`, nhưng vẫn tiến `ByteBuffer.position()`; cần xử lý partial I/O, `flip` và byte order.
- Công thức offset `base + index * recordSize` phải dùng số học an toàn; phải kiểm tra EOF và kích thước file chia hết cho record size.
- Fixed-length record không tự cung cấp transaction, chống crash, concurrency safety hay khả năng tương thích schema.
- Kết quả byte giống nhau là điều kiện đúng để so sánh; **benchmark mới quyết định hiệu năng với workload cụ thể**.

Với record chỉ gồm primitive đơn giản và ít concurrency, **RAF thường là code dễ hiểu**. Với nhu cầu offset rõ ràng, buffer batching và tích hợp NIO, **FileChannel thường thuận tiện hơn**. Đánh giá sự phù hợp theo semantics, độ đúng đắn và đặc tính vận hành trước khi tối ưu.

**Tài liệu chính thức — Java SE 17:**

1. [RandomAccessFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/RandomAccessFile.html) — `seek`, `getFilePointer`, `readInt`, `writeLong`, `getChannel`.
2. [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html) — positional `read/write`, `size`, `position`, `force` và lock.
3. [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html) — `putInt/getInt`, `putLong/getLong`, `flip`, `position/limit`.
4. [ByteOrder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteOrder.html) — `BIG_ENDIAN` và `LITTLE_ENDIAN`.
5. [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html) — `mismatch`, tệp tạm và truy cập đường dẫn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-008">Xây dựng vòng selector cho giao tiếp TCP đơn giản</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành Java NIO: xây dựng vòng Selector cho giao tiếp TCP đơn giản

Mục tiêu của bài thực hành là **tạo một TCP echo server sử dụng `Selector`**, không dùng một thread riêng cho từng kết nối. Máy chủ lắng nghe TCP bằng `ServerSocketChannel`; mỗi client được đại diện bởi một `SocketChannel` ở chế độ non-blocking. Một **event-loop thread** gọi `selector.select()` để lần lượt xử lý `OP_ACCEPT`, `OP_READ` và `OP_WRITE`. Khi đọc được một dòng kết thúc bằng `\n`, máy chủ trả về dòng `echo:<nội dung>\n`.

Phần thực hành dùng **Java 17+**, chạy hoàn toàn trên địa chỉ loopback, tự chọn cổng trống, có một TCP client nhỏ ngay trong chương trình để kiểm tra đường đi của dữ liệu. Code minh họa có chủ đích **xử lý một request rồi dừng** để người học thấy rõ vòng đời của selector, socket, key và buffer; các phần mở rộng sau đó giải thích cách chuyển thành server nhiều client phục vụ lâu dài.

Điều cần nhớ trước khi bắt đầu: **readiness không phải completion**. `OP_READ` không đảm bảo đủ một dòng; `OP_WRITE` không đảm bảo gửi hết response. Chúng ta phải giữ trạng thái trên từng kết nối, kiểm tra số byte thực sự chuyển, cập nhật `interestOps` và dọn resource đúng thời điểm.

## 1. Mục tiêu học tập và kết quả cần đạt

Sau bài này, bạn có thể:

1. Mở `Selector`, `ServerSocketChannel`, bind TCP loopback và đăng ký `OP_ACCEPT`.
2. Chấp nhận kết nối bằng `accept()`, đặt `SocketChannel.configureBlocking(false)` rồi đăng ký `OP_READ`.
3. Viết vòng event loop `select() → selectedKeys() → iterator.remove() → handle event`.
4. Lưu `ByteBuffer`, dữ liệu đọc dở và buffer ghi chờ trong `SelectionKey.attachment()`.
5. Tích lũy bytes cho đến delimiter `\n`; không nhầm một TCP `read` với một message.
6. Bật `OP_WRITE` khi thực sự có response; giữ buffer với `hasRemaining()` và tắt quan tâm write khi hết dữ liệu.
7. Xử lý `read()==0`, `read()==-1`, `accept()==null`, `IOException`, key invalid và đóng channel.
8. Chạy test end-to-end qua loopback, kiểm tra stdout và sửa lỗi thường gặp.
9. Phân biệt mô hình server một vòng selector với một thread-per-connection, và xác định giới hạn của ví dụ.

**Đầu ra mong đợi:** chương trình in `reply=echo:ping`, chứng minh ít nhất hai lượt đọc và ba lượt ghi nhờ buffer nhỏ có chủ ý, và xác nhận `roundtrip=true`.

## 2. Chuẩn bị môi trường

Yêu cầu:

- JDK **17 trở lên**, với `java.nio.channels` và `java.net` của Java SE.
- Terminal có lệnh `javac` và `java` trên `PATH`, có quyền mở kết nối loopback nội bộ.
- Không cần thư viện bên ngoài, Maven, Gradle hay cơ sở dữ liệu.
- File nguồn nên được lưu dưới UTF-8; tên file trùng với public class `SelectorTcpEchoDemo.java`.

Kiểm tra:

```bash
java -version
javac -version
```

Các lệnh trên là lệnh môi trường; phiên bản in ra phụ thuộc JDK bạn cài. Với máy không cho phép loopback hoặc bị chặn bởi policy bảo mật, chương trình có thể ném `IOException`, không phải lỗi của API Selector.

Để tránh xung đột cổng, chương trình **bind cổng 0**. Hệ điều hành sẽ cấp một cổng TCP khả dụng. Client lấy địa chỉ thật qua `listener.getLocalAddress()`, nên không cần ghi cứng số cổng 8080 và không cần mở port trên mạng công cộng.

## 3. Bức tranh kiến trúc

```text
                  Java process: hai luồng trong bản demo
┌──────────────────────────────────────────────────────────────┐
│ Main thread (client thử nghiệm)                               │
│                                                              │
│   Socket.connect(loopback, ephemeralPort)                     │
│        │                                                     │
│        ├── send bytes: "ping\n" ────────────────┐            │
│        │                                         │            │
│        └── read response until server closes ◄──┼──────┐     │
│                                                  │      │     │
│ Event-loop thread                                ▼      │     │
│   Selector.select(3000)                          │      │     │
│        │                                         │      │     │
│        ├── OP_ACCEPT → ServerSocketChannel.accept()     │     │
│        │                  └── register peer: OP_READ    │     │
│        ├── OP_READ → SocketChannel.read(ByteBuffer)     │     │
│        │              → accumulate until newline       │     │
│        │              → set OP_WRITE                    │     │
│        └── OP_WRITE → SocketChannel.write(reply) ──────┘     │
│                       → repeat until !hasRemaining()         │
│                       → close peer, exit loop                 │
└──────────────────────────────────────────────────────────────┘
```

**Phân biệt số luồng:** chương trình hoàn chỉnh có một *main thread* làm TCP client kiểm thử và một *event-loop thread* làm server. **Phía server không tạo một thread cho mỗi client**. Nó sử dụng đúng một selector để quan sát các channel đã đăng ký. Không nên mô tả demo này là “toàn chương trình chỉ có một thread”.

**Phân biệt handle:**

| Thành phần | Thực hiện việc gì? | Trạng thái quan trọng |
| --- | --- | --- |
| `ServerSocketChannel` | Bind và chấp nhận TCP clients. | `OP_ACCEPT`; socket server không trực tiếp đọc payload client. |
| `SocketChannel` | Kết nối TCP đã được accept; đọc/ghi bytes. | `OP_READ`, `OP_WRITE`; `ByteBuffer` ứng với client. |
| `Selector` | Đợi và chọn các thao tác I/O có khả năng tiến triển. | `keys()` và `selectedKeys()`. |
| `SelectionKey` | Quan hệ đăng ký giữa channel và selector. | `interestOps()`, `readyOps()`, `attachment()`, `isValid()`. |
| `State` | Dữ liệu dở dang của từng TCP peer. | `input`, `request` và `output`. |
| `ByteBuffer` | Buffer bytes với `position/limit/remaining`. | Phải `flip/clear` đúng pha. |

## 4. Chuỗi sự kiện và interest ops

Vòng đời một request:

```text
   server.bind(...)
      │
      ▼
   server.register(selector, OP_ACCEPT)
      │
      ▼
   Selector.select() ──────► key.isAcceptable()
                                      │
                             listener.accept() != null?
                               ├── no: return to select
                               └── yes:
                                   peer.configureBlocking(false)
                                   peer.register(OP_READ, State)
                                      │
                                 key.isReadable()
                                      │
                               peer.read(input)
                              /       |       \
                            >0       =0       -1
                            │         │        │
                     parse bytes    select   EOF/close
                            │
                     newline reached?
                       no       yes
                       │         │
                     select   create output
                                │
                           interestOps(OP_WRITE)
                                │
                           key.isWritable()
                                │
                           peer.write(output)
                             /        \
                       remaining     drained
                           │            │
                        select       close peer
                                       │
                                   stop demo
```

`interestOps` là **điều ứng dụng muốn theo dõi**; `readyOps` là **readiness đã quan sát được** ở một lần selection. Chúng không phải số lượng byte đã truyền. Phải kiểm tra kết quả `read/write/accept` thực tế.

Ví dụ server không quan tâm `OP_WRITE` trước khi có response. Nếu bật `OP_WRITE` ngay từ đầu và giữ liên tục, socket thường luôn “có thể ghi” dù queue trống, dẫn đến vòng chọn được kích hoạt liên tục và tốn CPU.

## 5. Chương trình Java 17+ đầy đủ, biên dịch và chạy được

Lưu toàn bộ thành **`SelectorTcpEchoDemo.java`**. Chương trình gồm:

- `State`: một input `ByteBuffer` nhỏ (3 byte), một vùng tích lũy request và một output `ByteBuffer`.
- `serve`: server event loop với `OP_ACCEPT/READ/WRITE`, tự loại selected key sau xử lý.
- `main`: mở selector/server, bind loopback, tạo đúng một event-loop thread, sử dụng blocking `Socket` làm client thử nghiệm, đợi thread kết thúc và assert kết quả.
- Bộ đếm nhằm chứng minh **ít nhất** hai lượt read và ba lượt write; trong demo, giới hạn buffer được đặt rõ để làm hiệu ứng partial I/O có thể tái hiện.

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class SelectorTcpEchoDemo {
    private static final class State {
        final ByteBuffer input = ByteBuffer.allocate(3);
        final ByteArrayOutputStream request = new ByteArrayOutputStream();
        ByteBuffer output;
    }

    private static final class Stats {
        int readCalls;
        int writeCalls;
    }

    private static void serve(Selector selector, Stats stats) throws IOException {
        boolean finished = false;
        while (!finished) {
            if (selector.select(3000) == 0) {
                throw new IOException("Server timed out");
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (!key.isValid()) continue;

                if (key.isAcceptable()) {
                    ServerSocketChannel listener =
                            (ServerSocketChannel) key.channel();
                    SocketChannel peer;
                    while ((peer = listener.accept()) != null) {
                        peer.configureBlocking(false);
                        peer.register(selector, SelectionKey.OP_READ,
                                new State());
                    }
                    continue;
                }

                SocketChannel peer = (SocketChannel) key.channel();
                State state = (State) key.attachment();

                if (key.isReadable()) {
                    int n = peer.read(state.input);
                    if (n == -1) {
                        peer.close();
                        throw new IOException("EOF before request completed");
                    }
                    if (n > 0) {
                        stats.readCalls++;
                        state.input.flip();
                        while (state.input.hasRemaining()) {
                            byte b = state.input.get();
                            if (b == '\n') {
                                String message = state.request.toString(
                                        StandardCharsets.UTF_8);
                                state.output = ByteBuffer.wrap(
                                        ("echo:" + message + "\n")
                                                .getBytes(StandardCharsets.UTF_8));
                                key.interestOps(SelectionKey.OP_WRITE);
                                break;
                            }
                            if (state.request.size() >= 4096) {
                                throw new IOException("Request is too long");
                            }
                            state.request.write(b & 0xff);
                        }
                        state.input.clear();
                    }
                }

                if (key.isValid() && key.isWritable()) {
                    // Only four bytes per call, to demonstrate partial writes.
                    int previousLimit = state.output.limit();
                    state.output.limit(Math.min(previousLimit,
                            state.output.position() + 4));
                    try {
                        int n = peer.write(state.output);
                        if (n > 0) stats.writeCalls++;
                    } finally {
                        state.output.limit(previousLimit);
                    }
                    if (!state.output.hasRemaining()) {
                        key.cancel();
                        peer.close();
                        finished = true;
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Stats stats = new Stats();
        try (Selector selector = Selector.open();
             ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0));
            listener.configureBlocking(false);
            listener.register(selector, SelectionKey.OP_ACCEPT);

            Thread worker = new Thread(() -> {
                try {
                    serve(selector, stats);
                } catch (Throwable t) {
                    failure.set(t);
                }
            }, "selector-event-loop");
            worker.start();

            String reply;
            try {
                try (Socket client = new Socket()) {
                    client.connect(listener.getLocalAddress(), 3000);
                    client.setSoTimeout(3000);
                    client.getOutputStream().write(
                            "ping\n".getBytes(StandardCharsets.UTF_8));
                    reply = new String(client.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8);
                }
            } finally {
                worker.join(5000);
                if (worker.isAlive()) {
                    selector.close();
                    worker.join(5000);
                    throw new IOException("Event loop did not terminate");
                }
            }
            if (failure.get() != null) {
                throw new IOException("Event loop failed", failure.get());
            }
            System.out.println("reply=" + reply.stripTrailing());
            System.out.println("read-calls>=2=" + (stats.readCalls >= 2));
            System.out.println("write-calls>=3=" + (stats.writeCalls >= 3));
            System.out.println("roundtrip=" + reply.equals("echo:ping\n"));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 SelectorTcpEchoDemo.java
java SelectorTcpEchoDemo
```

Stdout đã được xác nhận bằng biên dịch và chạy thực tế trên JDK 17+ trong môi trường cho phép TCP loopback:

```text
reply=echo:ping
read-calls>=2=true
write-calls>=3=true
roundtrip=true
```

**Vì sao test không in số lần đọc/ghi chính xác?** Với mạng và selector, lịch trình của OS có thể khác nhau. Demo cố ý đặt input buffer chỉ 3 byte, trong khi request `"ping\n"` dài 5 byte, nên cần **ít nhất hai lời gọi read thành công**. Response `"echo:ping\n"` có 10 byte, còn mỗi lần ghi bị tạm hạn chế tối đa 4 byte, nên cần **ít nhất ba lời gọi write thành công**. Số lời gọi thực tế có thể lớn hơn; kiểm tra cận dưới là assertion đúng và ổn định hơn.

Lưu ý đoạn này thực hiện **một request đơn, một lần kết nối**. Nó không phải echo server production, không giữ trạng thái cho nhiều request trên cùng socket, không giải quyết graceful half-close, TLS, authentication hoặc chính sách tổng bộ nhớ. Các bước mở rộng cuối bài chỉ ra nơi cần thay đổi.

## 6. Phân tích phần khởi tạo TCP listener

Đoạn trong `main`:

```java
try (Selector selector = Selector.open();
     ServerSocketChannel listener = ServerSocketChannel.open()) {
    listener.bind(new InetSocketAddress(
            InetAddress.getLoopbackAddress(), 0));
    listener.configureBlocking(false);
    listener.register(selector, SelectionKey.OP_ACCEPT);
}
```

Đây là đoạn trích từ chương trình mẫu. Các điểm kỹ thuật:

1. `Selector.open()` tạo selector của provider hiện tại. Ứng dụng không cần điều khiển trực tiếp `epoll/kqueue/poll`.
2. `ServerSocketChannel.open()` tạo channel phục vụ `accept`; trước khi bind nó chưa lắng nghe một cổng tùy chọn của bạn.
3. `InetAddress.getLoopbackAddress()` chỉ phục vụ kết nối nội bộ. Không bind `0.0.0.0` cho bài demo nếu không muốn máy khác có thể thử kết nối.
4. Cổng `0` yêu cầu OS chọn port đang khả dụng. `listener.getLocalAddress()` sau bind là địa chỉ/port đã có thật.
5. `configureBlocking(false)` **bắt buộc** trước khi `register` một channel với selector.
6. `OP_ACCEPT` là thao tác hợp lệ của `ServerSocketChannel`; nó không phải `OP_READ`. Đọc payload sẽ do `SocketChannel` được accept thực hiện.
7. `try-with-resources` đóng selector và listener khi rời scope, bao gồm khi exception xảy ra.

**Sai lầm thường gặp:** viết `listener.register(selector, OP_ACCEPT)` khi channel vẫn ở chế độ blocking, gây `IllegalBlockingModeException`. Hoặc đăng ký `OP_READ` cho `ServerSocketChannel`, không thuộc `validOps()` của loại channel đó.

## 7. OP_ACCEPT: từ server socket sang client SocketChannel

Handler của một selected key có `isAcceptable()`:

```java
ServerSocketChannel listener =
        (ServerSocketChannel) key.channel();
SocketChannel peer;
while ((peer = listener.accept()) != null) {
    peer.configureBlocking(false);
    peer.register(selector, SelectionKey.OP_READ, new State());
}
```

Đây là đoạn chạy trong event loop của chương trình đầy đủ.

**Vì sao `while (accept() != null)`?** Với server channel non-blocking, một `OP_ACCEPT` có thể tương ứng nhiều kết nối chờ được xử lý. Sau khi nhận hết những kết nối hiện sẵn, `accept()` trả `null`, khi ấy quay về vòng chọn. Không giả định có đúng một kết nối cho mỗi ready key.

**Vì sao `peer.configureBlocking(false)`?** Socket mới được accept không tự đảm bảo đang non-blocking theo cấu hình mong muốn; phải đặt rõ chế độ trước khi đăng ký với selector.

**Vì sao `new State()` được attach cho từng peer?** Mỗi client cần input buffer và dữ liệu chờ riêng; nếu các peer cùng chia sẻ một `ByteBuffer` mutable mà không có protocol ownership, bytes từ những kết nối khác nhau có thể lẫn vào nhau.

Trong server thực, một đợt kết nối dồn dập có thể cần giới hạn **số lần accept mỗi vòng**, quota clients, timeout, backpressure và xử lý lỗi khi register thất bại. Demo giữ vòng đơn giản vì chỉ có một client thử nghiệm.

## 8. OP_READ: một request có thể đến thành nhiều phần

TCP cung cấp **luồng byte**, không giữ nguyên ranh giới `write` của phía gửi. Client gửi `"ping\n"` trong một lệnh `Socket.getOutputStream().write(...)`, nhưng server có thể đọc nhiều chunk:

```text
Client sends five UTF-8 bytes:
70 69 6e 67 0a   = "ping\n"

Server input buffer has capacity 3:
1st read: 70 69 6e  -> "pin"
2nd read: 67 0a     -> "g\n"

Accumulated bytes before newline: 70 69 6e 67 = "ping"
Only after 0A is found: frame complete.
```

Với non-blocking `SocketChannel.read(ByteBuffer)`:

| Kết quả | Nghĩa | Hướng xử lý |
| --- | --- | --- |
| `n > 0` | Đã đọc đúng `n` byte và cập nhật `ByteBuffer.position()`. | Parse các bytes vừa đọc, giữ state nếu frame chưa đủ. |
| `n == 0` | Hiện chưa chuyển bytes. | Không quay CPU spin; đợi `OP_READ` khác. |
| `n == -1` | EOF ở phía input (peer đóng phía gửi). | Quyết định đóng, xử lý half-close hoặc phản hồi theo protocol. |

Demo dùng `ByteBuffer.allocate(3)`, gọi `flip()` sau khi đọc để tiêu thụ tất cả byte vừa nhận, rồi `clear()`. Do mỗi byte đã được đưa sang `ByteArrayOutputStream request`, nó **không cần `compact()` trong thiết kế này**. Nếu dùng parser trực tiếp trên input buffer có frame dở mà chưa được copy sang state khác, phải `compact()` để giữ bytes chưa xử lý.

Khi gặp `\n`, chương trình chuyển bytes đã tích lũy thành `String` UTF-8. Đây là điểm đúng để decode vì một ký tự UTF-8 có thể bị tách giữa hai lần đọc, đặc biệt nếu request chứa tiếng Việt hoặc emoji. **Không** gọi `new String(chunkBytes, UTF_8)` riêng cho mỗi chunk rồi nối kết quả; cách đó có thể làm hỏng ký tự đa byte bị cắt.

Giới hạn `4096` byte trước newline giúp tránh một client gửi mãi không có delimiter làm bộ nhớ tăng vô hạn. Đây chỉ là giới hạn **một request**; server thật còn cần giới hạn thời gian gửi từng dòng và tổng bộ nhớ của mọi peer.

## 9. OP_WRITE: giữ tiến độ và chỉ kích hoạt khi cần

Khi đã thấy newline, server chuẩn bị:

```java
state.output = ByteBuffer.wrap(
        ("echo:" + message + "\n").getBytes(StandardCharsets.UTF_8));
key.interestOps(SelectionKey.OP_WRITE);
```

Mẫu này chọn chuyển hẳn interest từ `OP_READ` sang `OP_WRITE` vì **demo chỉ nhận đúng một dòng**, sau đó gửi response và đóng socket. Một echo server nhiều request thường giữ `OP_READ` cùng lúc với `OP_WRITE` khi có outbound pending, hoặc tạm dừng đọc có kiểm soát khi cần backpressure.

Với non-blocking socket, một lần `write(ByteBuffer)` có thể:

- Ghi toàn bộ bytes còn lại.
- Chỉ ghi **một phần**.
- Ghi **0 byte**, ngay cả khi trước đó key được báo writable.

Do vậy phải giữ `ByteBuffer.position()` qua nhiều lần ghi, chỉ coi response được viết hết khi `!state.output.hasRemaining()`:

```java
// Snippet: output đã chuẩn bị (pha đọc), peer non-blocking.
int n = peer.write(state.output);
if (n == 0) {
    // Không bận quay vòng; đợi readiness khác.
}
if (!state.output.hasRemaining()) {
    // Toàn bộ buffer đã được peer.write tiêu thụ.
}
```

Trong demo, để luôn quan sát partial writes, ta **tạm giảm `limit` của output** trước mỗi lời gọi:

```java
int oldLimit = state.output.limit();
state.output.limit(Math.min(
        oldLimit, state.output.position() + 4));
try {
    peer.write(state.output);
} finally {
    state.output.limit(oldLimit);
}
```

Đây là snippet rút từ demo. Việc thu hẹp `limit` không yêu cầu OS thực tế chỉ ghi đúng 4 byte: nó chỉ đặt một **cận trên** cho phép ghi của lời gọi ấy. `FileChannel/SocketChannel.write` vẫn có thể ghi ít hơn. `finally` khôi phục limit cũ, và position đã tăng giữ nguyên.

Khi pending đã hết, demo hủy key/đóng peer và dừng; server phục vụ lâu dài thường **gỡ `OP_WRITE`**:

```java
// Bật khi queue có dữ liệu:
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// Gỡ khi queue trống, giữ OP_READ hoặc flag khác:
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

Đây là snippet mở rộng cho server thường trực. **Không giữ `OP_WRITE` bật khi không có data**: socket thường writable dẫn tới các vòng `select` được đánh thức liên tục, làm CPU tăng dù không có request.

## 10. selectedKeys(): đọc đúng, xóa đúng

Mẫu cốt lõi của event loop:

```java
while (!finished) {
    int selected = selector.select(3000);
    if (selected == 0) {
        // Demo: timeout. Server thật cần policy timers/tasks.
        continue;
    }

    Iterator<SelectionKey> iterator =
            selector.selectedKeys().iterator();
    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove();

        if (!key.isValid()) continue;
        // Kiểm tra isAcceptable/isReadable/isWritable và xử lý.
    }
}
```

Đoạn này là **snippet minh họa cách duyệt**, không phải phần thay thế nguyên xi của hàm `serve`: demo đầy đủ cố ý ném lỗi nếu quá thời gian 3 giây mà không có sự kiện.

- `selector.keys()` là tập key đăng ký; `selectedKeys()` là tập key đã được chọn có readiness.
- `iterator.remove()` xóa **key khỏi selected-key set** sau khi lấy ra. Tập này không được runtime tự xóa chỉ vì code đã gọi handler.
- Không giữ một key cũ rồi cho rằng `readyOps` luôn còn đúng ở thời điểm tương lai; sau thao tác I/O readiness có thể thay đổi.
- `key.cancel()` đánh dấu hủy đăng ký nhưng **không thay cho `SocketChannel.close()`** khi bạn sở hữu socket.
- `key.isValid()` cần được kiểm tra trước các thao tác dễ ném `CancelledKeyException`. Handler có thể cancel/close key ngay trong một sự kiện.
- Một key có thể ready nhiều ops cùng lúc; code demo dùng `if (key.isReadable())` rồi `if (key.isValid() && key.isWritable())` để không giả định chỉ một bit ready.

`Selector.select()` có thể trả 0 vì timeout, wakeup hoặc những điều kiện khác được API cho phép; trong ứng dụng thật không coi mọi lần 0 là lỗi disconnect. Một timeout cấp ứng dụng phải tính theo thời điểm deadline, không nên reset vô hạn mỗi khi có bất kỳ event không liên quan.

## 11. Thử nghiệm client: blocking để kiểm tra server non-blocking

Đoạn client test nằm trong `main`:

```java
try (Socket client = new Socket()) {
    client.connect(listener.getLocalAddress(), 3000);
    client.setSoTimeout(3000);
    client.getOutputStream().write(
            "ping\n".getBytes(StandardCharsets.UTF_8));
    reply = new String(client.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
}
```

Đây là snippet lấy từ chương trình. Client dùng `java.net.Socket` blocking **có chủ đích** để giữ bài đơn giản: mục tiêu là viết **server event loop sử dụng Selector**, không yêu cầu cả client cũng non-blocking.

Phương thức `readAllBytes()` trên socket có thể chờ tới EOF, vì TCP là stream. Demo **đóng peer phía server sau khi ghi hết response**, nên client đạt EOF và phép đọc trả về toàn bộ response. Trong giao thức keep-alive/multi-request, không dùng `readAllBytes()` để chờ một dòng phản hồi nếu server giữ socket mở; client phải đọc đến delimiter `\n` hoặc theo length-prefix.

`setSoTimeout(3000)` giới hạn thời gian chờ đọc trên socket blocking để demo không treo vô hạn khi lỗi, nhưng timeout ở môi trường CI quá chậm có thể cần tăng theo chính sách test. Cổng chỉ mở trên loopback, không có authentication và không nên đưa nguyên demo ra internet.

Biến `AtomicReference<Throwable>` chuyển lỗi từ event-loop thread về main thread sau `join()`. Đây là lựa chọn đơn giản cho demo; ứng dụng lớn hơn thường có lifecycle service, futures hoặc cơ chế giám sát exception có cấu trúc.

## 12. Quản lý vòng đời và giải phóng tài nguyên

Bốn nhóm tài nguyên cần chú ý:

| Tài nguyên | Ai sở hữu? | Đóng khi nào? |
| --- | --- | --- |
| `Selector` | Thành phần server/event loop. | Sau khi event loop dừng hoặc bị điều phối shutdown. |
| `ServerSocketChannel` | Thành phần listener. | Khi ngừng nhận kết nối mới, hoặc khi server dừng hoàn toàn. |
| `SocketChannel` của client | Handler/connection manager. | Khi request hoàn tất theo demo, EOF, lỗi, timeout hoặc chính sách đóng. |
| `Socket` blocking client | Phần test. | Sau khi nhận đủ response và kiểm tra, hoặc khi xảy ra exception. |

Demo dùng `try-with-resources` cho selector, listener và blocking client. Ở server, `peer.close()` đóng socket sau khi output buffer đã được tiêu thụ hết.

**Quan trọng:** `ByteBuffer.hasRemaining() == false` chỉ có nghĩa các byte từ buffer được lời gọi `write` chấp nhận theo hợp đồng, **không** chứng minh ứng dụng phía bên kia đã xử lý response thành công. Với protocol cần xác nhận nghiệp vụ, cần ACK ở tầng ứng dụng.

Trong server nhiều client, khi dừng cần đóng **tất cả** accepted channels còn mở, không chỉ listener và selector. Đóng selector làm key invalid nhưng không nên dựa vào nó như cơ chế tự đóng mọi socket đang đăng ký. Duy trì danh sách/ownership rõ ràng, hoặc đóng peer khi xử lý key và bảo đảm cleanup ở một khối `finally`.

## 13. Những giới hạn có chủ ý của chương trình mẫu

Chương trình minh họa hoạt động thật, nhưng một số lựa chọn chỉ phục vụ việc học:

1. **Một request, một response, rồi đóng:** khi gửi xong một dòng, `finished=true`. Để phục vụ nhiều client, bỏ điều kiện stop toàn server; chỉ đóng peer tương ứng.
2. **Không xử lý nhiều frame trong cùng một lượt đọc:** nếu client gửi hai dòng cùng một chunk, demo chỉ xử lý dòng đầu và có thể bỏ phần sau. Production phải giữ bytes còn lại và lặp parser.
3. **Không có queue outbound nhiều buffer:** một connection chỉ cần một `output`; server nhiều request phải có `Deque<ByteBuffer>` và xử lý từng buffer cho đến khi queue trống.
4. **Gửi tối đa 4 byte mỗi lần** là cách kiểm thử partial write, không phải tối ưu hiệu năng.
5. **Input buffer 3 byte** để ép partial read; production thường dùng buffer lớn hơn, memory pooling và quota theo load.
6. **Đọc UTF-8 qua `new String(bytes, UTF_8)` sau newline:** với dữ liệu không tin cậy cần strict decoder khi giao thức bắt buộc UTF-8 hợp lệ, và quy tắc CRLF nếu hỗ trợ.
7. **Giới hạn 4096 byte** chỉ cho request trước delimiter. Không có timeout cho client gửi quá chậm từng byte ngoài timeout `select` chung.
8. **Một thread client blocking** để kiểm tra server; nhiều client thật cần load test riêng.
9. **Lỗi I/O làm dừng toàn server:** demo để người học dễ nhìn thấy lỗi. Production nên đóng kết nối lỗi và tiếp tục phục vụ client khác, trừ lỗi hệ thống không thể phục hồi.
10. **Không có TLS, authentication, metrics, logging hoặc graceful shutdown thực thụ:** không đưa thẳng ra production.

Những giới hạn này không làm ví dụ không chính xác; chúng xác định rõ **phạm vi lab** để bạn không nhầm minh họa event loop tối thiểu với máy chủ chịu tải lớn.

## 14. Mở rộng bài lab thành server nhiều kết nối

Mô hình duy trì nhiều connections:

```text
Selector server
  ├─ ServerSocketChannel => OP_ACCEPT
  ├─ SocketChannel #1 => State(input, parser, outbound queue)
  ├─ SocketChannel #2 => State(input, parser, outbound queue)
  └─ SocketChannel #N => State(input, parser, outbound queue)

Với mỗi key:
  ACCEPT: accept tối đa quota rồi register OP_READ
  READ: đọc tối đa byte budget, decode nhiều frame, enqueue responses
        nếu queue có data => bật OP_WRITE
  WRITE: write từng buffer, giữ partial, gỡ OP_WRITE khi queue trống
  EOF/ERROR: flush theo protocol nếu cần, sau đó close socket
```

Một `State` nhiều request có thể trông như:

```java
// Snippet thiết kế, không phải bản demo chạy độc lập.
final class ConnectionState {
    ByteBuffer inbound = ByteBuffer.allocate(8192);
    ArrayDeque<ByteBuffer> outbound = new ArrayDeque<>();
    long lastActivityNanos = System.nanoTime();
    boolean peerInputClosed;
}
```

Cần import `ArrayDeque` và `ByteBuffer`. Event loop thường **sở hữu** các state này trên một thread. Nếu thread khác muốn gửi dữ liệu vào queue, phải dùng cơ chế đồng bộ và wakeup phù hợp.

### Bước đổi từ single request sang multiple requests

- Thay `finished=true` bằng logic đóng **riêng** `SocketChannel` hoàn tất, còn server event loop chạy.
- Sau khi output queue trống, giữ/re-enable `OP_READ` cho connection nếu protocol cho phép request mới.
- Dùng parser xử lý nhiều newline trong cùng chunk; không bỏ bytes sau newline thứ nhất.
- Giữ một buffer/tập byte dở dang cho request chưa kết thúc, tránh decode UTF-8 theo từng chunk.
- Khi nhận được một frame, enqueue response nhưng tiếp tục tôn trọng hạn mức memory/outbound queue.
- Dùng deadline cho idle connection, request incomplete và shutdown.
- Đóng tất cả accepted channels khi stop service.

**Không lấy cùng một `State` gắn cho mọi key**. Buffer và parser phải gắn với đúng connection để không lẫn request từ hai client.

## 15. Backpressure và tránh vòng quay CPU bận

Một server non-blocking có thể bị client đọc chậm. Khi output queue còn byte nhưng `socket.write` trả 0, không quay vòng:

```java
// Snippet cho SocketChannel non-blocking.
while (!out.hasRemaining()) {
    // Không nên dùng vòng này để giả lập chờ.
}
```

Đoạn trên là **phản ví dụ**: có thể bận CPU hoặc không có ý nghĩa khi output còn bytes. Thay bằng:

```java
// Snippet: key còn valid, out là ByteBuffer đang chờ.
if (out.hasRemaining()) {
    int n = peer.write(out);
    if (n == 0) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        // Trở lại vòng select, không retry vô hạn tại đây.
    }
}
```

Mẫu cho queue:

1. Khi có response, đưa `ByteBuffer` đã `flip()` (hoặc `wrap`) vào `outbound`.
2. Bật `OP_WRITE`.
3. Khi ready, cố ghi buffer đầu. Nếu ghi chưa hết, giữ lại `position`. Nếu hết, bỏ buffer đó khỏi queue và tiếp tục buffer sau trong giới hạn workload.
4. Nếu queue trống, **gỡ `OP_WRITE`**. Nếu còn bytes, giữ interest.
5. Giới hạn tổng bytes pending trên từng client và toàn server. Nếu vượt, tạm ngừng đọc từ client đó, báo quá tải hoặc đóng kết nối theo chính sách.

Backpressure không chỉ là vấn đề hiệu năng: không có hạn mức, hàng nghìn client đọc chậm có thể giữ lượng lớn bytes đã encode trong heap cho đến khi ứng dụng hết bộ nhớ.

## 16. select(), selectNow(), wakeup() và thread ownership

Các phương thức:

| API | Tác dụng | Ghi chú |
| --- | --- | --- |
| `selector.select()` | Chờ selection sẵn sàng hoặc cơ chế đánh thức/đóng. | Phù hợp vòng server liên tục, cần cơ chế shutdown và task queue. |
| `selector.select(timeoutMs)` | Chờ tối đa khoảng timeout dương, `0` nghĩa chờ không giới hạn theo hợp đồng API. | Demo dùng 3000 ms để fail-fast. |
| `selector.selectNow()` | Kiểm tra trạng thái selection không blocking. | Không nên spin liên tục khi không có việc. |
| `selector.wakeup()` | Báo thức một vòng select đang hoặc sắp chặn. | Không tự làm socket read/write sẵn sàng. |
| `key.interestOps(mask)` | Cập nhật bitmask interest. | Đừng vô tình tắt `OP_READ` khi bật `OP_WRITE`. |

Khi event loop có thread riêng và producer muốn gửi thêm bytes:

```java
// Snippet: từ thread producer; tasks là queue thread-safe.
tasks.add(() -> enqueueOutbound(connectionId, bytes));
selector.wakeup();
```

Event loop khi thức dậy lấy tasks rồi cập nhật trạng thái của những connection mà nó sở hữu. `wakeup()` chỉ đánh thức lựa chọn, **không gửi byte** thay application. Nếu producer thao tác trực tiếp `ByteBuffer` hoặc `ArrayDeque` thuộc event loop từ một thread khác, cần đồng bộ rõ để tránh race.

**Thread safety của Selector/Channel không thay được tính nhất quán của protocol state.** Một chiến lược dễ giải thích là mọi register, cancel, queue mutation và parse/write của một connection đều do một event-loop thread sở hữu; những thread khác gửi message/task qua queue.

## 17. Lỗi thường gặp khi làm bài thực hành

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| `IllegalBlockingModeException` khi register | Quên `configureBlocking(false)` trước `register`. | Cấu hình non-blocking cho listener và accepted socket. |
| `IllegalArgumentException` ở interest mask | `OP_READ` trên `ServerSocketChannel` hoặc flag không thuộc `validOps()`. | Register `OP_ACCEPT` cho listener, `OP_READ/WRITE` cho peer. |
| Server không nhận request sau accept | Quên register `SocketChannel` mới. | `peer.register(selector, OP_READ, new State())`. |
| Handler cứ chạy lại key cũ | Không `iterator.remove()` selected key. | Xóa key khỏi selected set trong vòng duyệt. |
| CPU cao khi không có dữ liệu | Luôn giữ `OP_WRITE` hoặc quay vòng khi `read/write==0`. | Chỉ bật OP_WRITE khi có pending, quay về `select`. |
| Client nhận thiếu response | `write` chỉ chuyển được một phần; buffer bị reset/clear. | Giữ position, lặp qua nhiều readiness đến `!hasRemaining()`. |
| Server coi một chunk là một dòng | TCP không bảo toàn ranh giới request. | Tích lũy bytes đến newline/length-prefix. |
| Tiếng Việt/emoji thành `�` | Decode UTF-8 khi còn thiếu byte giữa các chunk. | Giữ bytes và decode sau đủ frame hoặc dùng decoder incremental. |
| Server gửi response quá sớm | Chưa gặp newline, nhưng tưởng OP_READ là full request. | Parser giữ state đến delimiter. |
| Client `readAllBytes()` chờ mãi | Server giữ socket open kiểu keep-alive, không gửi EOF. | Client đọc theo framing, hoặc server đóng sau response theo protocol. |
| `CancelledKeyException` | Key đã cancel nhưng handler tiếp tục truy cập ready/interest ops. | Kiểm tra `isValid()` và dừng handler khi close/cancel. |
| `ClosedChannelException` | Dùng channel sau `close()` hoặc chọn sai vòng đời. | Quản lý ownership và cleanup. |
| Cổng cố định báo `BindException` | Một process khác đang chiếm port. | Với lab dùng bind port 0 và `getLocalAddress()`. |
| Server chỉ chạy trong máy mình | Loopback không cho máy khác kết nối. | Đây là chính sách an toàn chủ đích; production cần bind theo yêu cầu và bảo vệ mạng. |
| Một client chậm làm server đơ | Handler blocking hoặc xử lý không giới hạn. | Budget mỗi key; offload công việc blocking/CPU nặng. |
| Server tiếp tục mở sockets sau stop | Chỉ đóng selector mà không quản lý accepted channels. | Đóng từng peer, listener, selector theo lifecycle. |

Để debug, log thời điểm, ID kết nối, số bytes `read/write`, `position/limit/remaining`, ready ops, interest ops, số bytes outbound pending và nguyên nhân close. Không log raw payload chứa dữ liệu nhạy cảm hoặc tạo logging blocking nặng trong event loop.

## 18. Quy trình kiểm thử thực tế

### Kiểm thử A — chạy demo và đọc stdout

1. Lưu file theo UTF-8 đúng tên.
2. Chạy `javac -encoding UTF-8 SelectorTcpEchoDemo.java`, kiểm tra exit code.
3. Chạy `java SelectorTcpEchoDemo`.
4. Xác minh `reply=echo:ping` và `roundtrip=true`.
5. Xác minh `read-calls>=2=true`, `write-calls>=3=true`.
6. Nếu timeout, kiểm tra chính sách TCP loopback/antivirus, JVM và cổng; không tự đổi mục tiêu test thành “chạy không exception” mà bỏ qua round-trip.

### Kiểm thử B — payload thay đổi

Đổi lời gửi client từ `"ping\n"` thành `"hello\n"`; đồng thời cập nhật expected reply thành `"echo:hello\n"`. Bảo đảm parser đợi newline và response không bị mất byte. Nếu muốn đo ít nhất ba lượt write, giữ giới hạn 4 byte từ demo.

### Kiểm thử C — không có newline

Cho client gửi `"partial"` mà không đóng hoặc không gửi newline, rồi áp dụng timeout. Server không được tuyên bố frame hoàn tất; test phải có deadline. Khi peer đóng trước newline, demo báo `EOF before request completed`.

### Kiểm thử D — quá dài

Gửi hơn 4096 byte không có delimiter. Server demo sẽ ném `IOException("Request is too long")`. Trong server production, nên đóng **connection vi phạm** và tiếp tục phục vụ các connections khác thay vì dừng toàn service.

### Kiểm thử E — Unicode/UTF-8

Gửi `"Việt 😀\n"` dưới dạng UTF-8, giữ input buffer ba byte. Bên nhận phải tích lũy đủ bytes rồi mới decode. Đầu ra logic `"echo:Việt 😀\n"`. Nếu dùng strict decoder, lỗi malformed phải được từ chối có kiểm soát.

### Kiểm thử F — nhiều client đồng thời

Sau khi mở rộng server để không dừng sau client đầu, chạy 10–100 client thử nghiệm trên loopback, mỗi client gửi nhiều dòng với nhịp khác nhau. Kiểm tra không lẫn state/response, không starvation, không leak sockets và không bị busy loop khi tất cả idle. Thêm quota và timeouts trước khi tăng tải lớn.

### Kiểm thử G — graceful shutdown

Khi đang có connections, đặt cờ stop và `selector.wakeup()`; ngừng accept, xả queue có deadline, đóng accepted peers, đóng listener, rồi đóng selector. Kiểm tra thread sự kiện không kẹt, không bỏ task ngoài chính sách và đóng tất cả resource.

## 19. Bài tập nâng cao sau lab

**Bài 1 — Bật/tắt OP_WRITE thay vì đóng connection.** Giữ kết nối sau một response để nhận dòng tiếp. Khi queue empty, thay vì đóng peer hãy chuyển về `OP_READ` hoặc giữ `OP_READ` liên tục, gỡ `OP_WRITE` bằng bitmask. Chứng minh CPU không cao khi client idle.

**Bài 2 — Xử lý nhiều dòng trong một read.** Cho client gửi `"one\ntwo\n"` cùng một lần write. Thay logic `break` sau newline bằng parser có thể tạo hai responses. Giữ bytes sau newline thay vì bỏ qua; test đúng thứ tự `echo:one`, `echo:two`.

**Bài 3 — Outbound queue và partial write.** Thay `State.output` bằng `ArrayDeque<ByteBuffer>`. Gửi ba response nhỏ và giới hạn mỗi write tối đa hai byte. Kiểm tra dữ liệu cuối không bị mất/trùng dù response được chia thành nhiều lần ghi.

**Bài 4 — Header length-prefix.** Thay framing newline bằng 4 byte đầu chứa chiều dài payload theo `ByteOrder.BIG_ENDIAN`. Tích lũy đủ header, kiểm tra length không âm/không quá 4096, rồi đọc đúng số bytes; test trường hợp header bị cắt giữa hai read.

**Bài 5 — Non-blocking client.** Viết client dùng `SocketChannel` với `OP_CONNECT`. Khi `isConnectable()`, gọi `finishConnect()`; chỉ bật `OP_READ/WRITE` cho application protocol sau khi connect thành công. Xử lý exception khi connect thất bại.

**Bài 6 — Worker pool.** Khi gặp frame hoàn chỉnh, gửi công việc CPU tốn thời gian qua executor, rồi trả response về event loop bằng queue thread-safe + `selector.wakeup()`. Không cho worker mutate `ByteBuffer` thuộc event-loop thread trực tiếp.

**Bài 7 — Backpressure.** Giới hạn 64 KiB outbound pending mỗi connection. Khi client đọc quá chậm, tạm gỡ `OP_READ` hoặc reject/quarantine theo protocol; phục hồi đọc khi queue giảm dưới ngưỡng thấp hơn để tránh bật/tắt liên tục.

**Bài 8 — Protocol error và timeout.** Thêm deadline cho idle connection, frame chưa đầy đủ và thời gian xả response. Client gửi bytes không kết thúc dòng hoặc malformed UTF-8 phải nhận xử lý lỗi rõ, không giữ socket vô hạn.

**Bài 9 — Metrics và load test.** Đo số active clients, bytes read/write, pending buffers, số vòng `select` trả 0 và p95 response latency. Phân biệt throughput, readiness và số request hoàn tất; không suy diễn thành công chỉ từ số event.

**Bài 10 — Dừng server có kiểm soát.** Tạo điều khiển stop thread-safe, gọi `selector.wakeup()`, hủy keys, đóng channels trong thread sở hữu selector rồi `join` với deadline. Kiểm tra không rò file descriptors và không block shutdown.

## 20. Những câu hỏi cần trả lời sau khi chạy

1. `ServerSocketChannel` và `SocketChannel` khác vai trò gì? Tại sao listener dùng `OP_ACCEPT` thay vì `OP_READ`?
2. Vì sao phải gọi `configureBlocking(false)` trước `register()`?
3. Khi `selector.select()` trả về, số key ready có nói được tổng số bytes sẽ đọc/ghi không?
4. Vì sao `iterator.remove()` phải xuất hiện trong vòng duyệt `selectedKeys()`?
5. Vì sao handler đọc từng byte đến newline thay vì coi một `read` là một request?
6. Vì sao input buffer ba byte vẫn đọc được request năm byte?
7. Vì sao thay `key.interestOps(OP_WRITE)` chỉ đúng với single-request demo, nhưng có thể làm mất OP_READ trong server nhiều request?
8. Khi `write(output)` trả 0, tại sao không lặp liên tục cho đến khi nó ghi được?
9. `ByteBuffer.flip()` và `clear()` thay đổi position/limit thế nào? Khi nào phải dùng `compact()`?
10. `key.cancel()` có tự đóng `SocketChannel` và giải phóng mọi memory state không?
11. Nếu client giữ kết nối mở sau response, `readAllBytes()` có thể chờ gì và giao thức nên xử lý ra sao?
12. Tại sao phần server của demo dùng **một event-loop thread**, dù chương trình vẫn có main thread đóng vai client?
13. Khi server gửi xong bytes, nó có biết ứng dụng client đã xử lý response và commit nghiệp vụ chưa?
14. Khi chuyển sang production, cần bổ sung những gì cho nhiều client, backpressure, timeouts, TLS và graceful shutdown?

## 21. Checklist trước khi coi thực hành thành công

- `javac` báo thành công, chạy `java SelectorTcpEchoDemo` không ném exception trong môi trường loopback hỗ trợ.
- Server bind địa chỉ loopback và port OS cấp, không tự mở listener công cộng.
- Listener non-blocking được đăng ký `OP_ACCEPT`.
- Accepted `SocketChannel` non-blocking được đăng ký `OP_READ` với `State` riêng.
- Mỗi selected key được xóa bằng `iterator.remove()`.
- Request chỉ được coi hoàn tất khi gặp delimiter `\n` và không vượt quota.
- Output được gửi theo `ByteBuffer.position()/remaining()` qua nhiều lần write.
- Server không giữ `OP_WRITE` bật khi không có pending response trong kiến trúc server nhiều request.
- Client nhận chính xác `echo:ping\n`, bộ test xác nhận `roundtrip=true`.
- Mỗi socket, listener và selector đều có owner và lộ trình close.
- Hiểu rằng demo một request chưa phải server nhiều client production.
- Biết cách mở rộng parser/queue, xử lý `read==0/-1`, timeouts, lỗi kết nối và shutdown.

## 22. Tài liệu tham khảo

- [Java SE 21 — Selector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html): selected-key set, selection, `selectNow`, `wakeup`, close.
- [Java SE 21 — SelectionKey](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectionKey.html): interest/ready ops, attachments, cancellation.
- [Java SE 21 — ServerSocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ServerSocketChannel.html): bind và accept non-blocking.
- [Java SE 21 — SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html): read/write, blocking mode, connection lifecycle.
- [Java SE 21 — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): `flip/clear/compact`, `hasRemaining` và semantics của buffer.
- [Java SE 21 — StandardCharsets](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html): UTF-8 và charset tường minh.

**Ghi nhớ:** Vòng Selector dùng một event-loop thread để điều phối nhiều TCP channels theo **readiness**, không tự truyền dữ liệu hay hoàn tất request. Xây server đúng bằng cách `register` channel non-blocking, `select`, xóa selected keys đã xử lý, giữ per-connection state cho bytes dở dang, bật `OP_WRITE` đúng lúc, xử lý partial I/O và đóng tài nguyên theo lifecycle. Sau khi lab chạy đúng, bước quan trọng tiếp theo là nhiều request, queue có quota, timeouts và graceful shutdown.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-009">Theo dõi thư mục bằng WatchService có xử lý overflow</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Theo dõi thư mục bằng WatchService có xử lý OVERFLOW

## 1. Kết quả cần đạt

Bài thực hành xây dựng một bộ theo dõi thư mục với Java NIO.2, sử dụng `WatchService` để nhận **tín hiệu thay đổi** và bổ sung cơ chế **rescan/reconciliation** để khôi phục trạng thái khi sự kiện bị mất, đặc biệt khi có `StandardWatchEventKinds.OVERFLOW`.

Sau bài này, bạn có thể:

1. Tạo và đăng ký `WatchService` theo dõi các mục trực tiếp trong một thư mục.
2. Nhận `WatchKey` bằng `take()` hoặc `poll(timeout, unit)`.
3. Xử lý `ENTRY_CREATE`, `ENTRY_MODIFY`, `ENTRY_DELETE` và `OVERFLOW`.
4. Dùng `WatchEvent.context()` đúng như **đường dẫn tương đối** so với thư mục đã đăng ký.
5. Gọi `pollEvents()`, xử lý và `reset()` cho từng key.
6. Xây dựng snapshot trạng thái thư mục bằng `Files.newDirectoryStream` và `BasicFileAttributes`.
7. Khi nhận `OVERFLOW`, quét lại trạng thái hiện tại để hòa giải cache thay vì tưởng rằng có thể tái tạo từng thao tác đã mất.
8. Viết ví dụ Java 17+ độc lập, có stdout dự đoán được mà **không phải giả định thứ tự/số lượng sự kiện ổn định trên mọi OS**.
9. Biết cách mở rộng thành watcher chạy lâu dài, hỗ trợ nhiều thư mục, dừng sạch, kiểm thử và bảo vệ tài nguyên.

> **Ý chính:** WatchService không phải một transaction log hay audit log đáng tin cậy. Nó cung cấp tín hiệu để ứng dụng biết **nên kiểm tra lại filesystem khi nào**. Trạng thái thực tế của thư mục, không phải từng notification riêng lẻ, mới là nguồn sự thật khi cần đồng bộ nội dung hiện thời.

## 2. Chuẩn bị môi trường

Ví dụ sử dụng **JDK 17 trở lên** và các API trong Java Standard Library, không cần Maven/Gradle hay dependency ngoài.

Kiểm tra môi trường:

```bash
java -version
javac -version
```

Cần có quyền tạo, đọc, sửa và xóa trong thư mục tạm mà JVM đang dùng. Hầu hết phần thực hành có thể chạy trên Windows, Linux hoặc macOS, nhưng:

- Nền tảng/provider có thể khác nhau về thời điểm, thứ tự và số lượng `ENTRY_MODIFY`.
- WatchService có thể không hỗ trợ đầy đủ trên network filesystem, mount đặc biệt hoặc môi trường sandbox.
- Không cố ép hệ điều hành phát `OVERFLOW` thật bằng cách tạo một lượng tệp khổng lồ; điều đó có thể gây quá tải mà không cho kết quả lặp lại.
- Các bài kiểm tra quan trọng cần chạy trên **filesystem triển khai thực tế**, không chỉ trên máy phát triển.

Các loại import cần thiết thường từ `java.nio.file`, `java.nio.file.attribute`, `java.util` và `java.util.concurrent`.

## 3. Nhắc lại WatchService hoạt động ra sao

Một `WatchService` giữ hàng đợi **WatchKey đã được signalled**. Khi ứng dụng đăng ký một thư mục, filesystem/provider có thể ghi nhận thay đổi trực tiếp bên trong nó và đưa key tương ứng vào hàng đợi.

```text
                   Filesystem
          create / modify / delete
                      |
                      v
              Đăng ký thư mục
                WatchKey K1
                      |
              key được signalled
                      |
                      v
              WatchService queue
                      |
                      v
         take() / poll() / timed poll()
                      |
                      v
              K1.pollEvents()
                      |
             +--------+---------+
             |                  |
         ENTRY_*             OVERFLOW
             |                  |
         event hint         mất sự kiện?
             |                  |
             +--------+---------+
                      |
                      v
             Rescan / reconcile
                      |
                      v
                key.reset()
                      |
           true: tiếp tục theo dõi
           false: registration invalid
```

Với `Path.register(watcher, kinds...)`, một `WatchKey` đại diện cho registration của path đã theo dõi. Một key có thể gom **nhiều events**; một lần `take()` trả **key**, không nhất thiết trả đúng một thay đổi.

### Các event kinds

| Event kind | Nghĩa thường gặp | Việc nên làm |
|---|---|---|
| `ENTRY_CREATE` | Một mục trực tiếp trong thư mục xuất hiện | Kiểm tra trạng thái mục ở thời điểm xử lý |
| `ENTRY_MODIFY` | Provider quan sát có sửa đổi | Đọc lại metadata/nội dung nếu nghiệp vụ yêu cầu |
| `ENTRY_DELETE` | Mục trực tiếp không còn tồn tại dưới tên đã theo dõi | Cập nhật cache từ trạng thái hiện tại |
| `OVERFLOW` | Provider báo có thể đã mất/bỏ một số events | **Quét lại thư mục**, đối chiếu cache; không cố khôi phục thứ tự mọi thay đổi |

**Không có bảo đảm một lần user nhấn Save = một `ENTRY_MODIFY`.** Editor có thể tạo file tạm rồi rename; một lần ghi lớn có thể tạo nhiều modify events; hệ thống có thể gộp chúng.

## 4. OVERFLOW thực sự có ý nghĩa gì?

`OVERFLOW` là tín hiệu cho biết event stream **có thể không còn đầy đủ**. Những thay đổi đã bị bỏ không nhất thiết có thể được hỏi lại từ WatchService.

Ví dụ:

```text
Trạng thái ứng dụng nghĩ đang có:
  A.txt, B.txt

Filesystem thay đổi rất nhanh:
  delete A.txt
  create C.txt
  modify B.txt
  create D.txt
  delete C.txt

Hàng đợi sự kiện bị tràn:
  ... OVERFLOW ...

Filesystem hiện tại:
  B.txt, D.txt
```

Nếu chỉ dựa vào các events còn sót, cache có thể vẫn tin `A.txt` tồn tại hoặc bỏ lỡ `D.txt`. Khi thấy overflow, cần quét thư mục để biết **cái gì đang tồn tại hiện nay**.

Không thể dùng một snapshot cuối để suy luận đầy đủ chuỗi thao tác trung gian. Ví dụ file `C.txt` từng được tạo rồi xóa trước lúc rescan: trạng thái cuối không còn dấu vết của C. Nếu nghiệp vụ yêu cầu audit **mọi sự kiện**, cần nhật ký giao dịch/journal ở tầng nguồn hoặc một hệ thống theo dõi có hợp đồng bền, không thể chỉ thêm `rescan` vào WatchService là đạt exactly-once.

### Chính sách phục hồi phù hợp

- Đánh dấu registration/thư mục cần **full rescan**.
- Lấy snapshot mới từ filesystem.
- So với cache ứng dụng ở trạng thái trước đó.
- Xử lý các mục xuất hiện/biến mất/thay đổi theo logic **idempotent**.
- Thay cache bằng snapshot mới sau khi các bước cần thiết hoàn tất.
- Tiếp tục theo dõi bằng `reset()` nếu key còn hợp lệ.
- Ghi metrics/log về overflow, thời gian rescan, số mục và lỗi I/O.

Với hệ thống chỉ quan tâm **trạng thái hiện tại**, đây là một chiến lược hợp lý. Với hệ thống cần lịch sử đầy đủ, phải dùng nguồn sự kiện có độ tin cậy cao hơn.

## 5. Thiết kế giải pháp: notification là hint, snapshot là trạng thái

Để bài thực hành có thể phục hồi cả trường hợp mất notification, ta chọn một thiết kế đơn giản nhưng vững:

1. Tạo `WatchService`, đăng ký thư mục trước.
2. Tạo snapshot ban đầu từ các mục trực tiếp trong thư mục.
3. Chờ WatchKey.
4. Lấy danh sách events. `OVERFLOW` được nhận diện **trước** khi ép `context` thành `Path`.
5. Với **bất kỳ key nào có event**, ta **rescan toàn bộ một thư mục** thay vì chỉ cập nhật từng item dựa trên event. Nhờ vậy, nhánh overflow đương nhiên thực hiện reconciliation.
6. So snapshot cũ và mới, xác định mục mới, mục đã mất và mục có metadata khác.
7. Reset key; nếu key invalid, dừng hoặc lên lịch đăng ký lại theo policy.
8. Định kỳ rescan cả khi không có notification nếu yêu cầu phát hiện trạng thái chệch do sự cố hoặc mất event.

**Đánh đổi:** full rescan cho mỗi notification dễ hiểu, hữu ích cho thư mục nhỏ/vừa và bài học; nó có thể tốn tài nguyên với thư mục hàng triệu entries. Trong production có thể tối ưu cập nhật từng entry cho event bình thường, chỉ full rescan khi OVERFLOW, lúc khởi động và định kỳ. Dù tối ưu, vẫn phải giữ cơ chế reconciliation đầy đủ.

### Snapshot bao gồm gì?

Ví dụ lưu một dấu vết metadata (**fingerprint**) cho mỗi mục:

- Tên/`Path` tương đối.
- `size()`.
- `lastModifiedTime()`.
- `isDirectory()`.

Đây là **heuristic quan sát trạng thái**, không phải cryptographic digest. Có thể không phát hiện nội dung bị thay đổi nếu kích thước và mtime cuối vẫn giống nhau hoặc filesystem có độ phân giải thời gian thấp. Nếu ứng dụng cần biết nội dung có đổi một cách chắc hơn, phải thêm hash/checksum hoặc version do producer cung cấp, cân nhắc chi phí và race khi file đang ghi.

## 6. Trình tự đăng ký và snapshot ban đầu

Mẫu cơ bản:

```java
try (WatchService watcher =
         FileSystems.getDefault().newWatchService()) {

    root.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE);

    Map<Path, Stamp> known = scanDirectory(root);
    // Vòng lấy keys và đối chiếu snapshots...
}
```

Đây là **mã minh họa**, cần khai báo `root` và các helper/import. Lý do đăng ký **trước** lần scan đầu tiên: nếu quét trước, sau đó một file thay đổi trong khoảng chưa đăng ký, bạn có thể không nhận được notification cho thay đổi ấy.

Tuy nhiên, đăng ký trước rồi scan **chưa biến hai bước thành một transaction nguyên tử**. Một mục có thể đổi trong khi đang quét; các event được queue trong lúc đó cần được tiêu thụ tiếp, và đối với dữ liệu quan trọng nên có thêm periodic reconciliation hoặc một snapshot protocol chuyên biệt.

Trong ví dụ bài học, mọi thay đổi được tạo trong cùng tiến trình sau khi đã đăng ký, nên trình tự dễ kiểm soát hơn.

## 7. WatchKey: take, poll và reset

Ba cách lấy key:

```java
WatchKey a = watcher.take();                          // Chờ.
WatchKey b = watcher.poll();                          // Không chờ.
WatchKey c = watcher.poll(1, TimeUnit.SECONDS);       // Chờ tối đa 1 s.
```

Các dòng là minh họa, **không gọi liên tiếp cả ba như một thủ tục bắt buộc**. Nếu `take()` được gọi khi không có event, thread có thể chờ không giới hạn.

- `take()` phù hợp watcher thread chuyên dụng, có shutdown qua interrupt/close.
- `poll()` phù hợp tích hợp vào một loop đã có công việc khác; tránh busy spin.
- `poll(timeout, unit)` thuận tiện cho watcher cần thời gian kiểm tra cờ dừng, metrics và periodic rescan.
- `pollEvents()` đọc/drain các events tích lũy cho key.
- `reset()` chuyển key về trạng thái sẵn sàng nếu registration hợp lệ.

Cần đặc biệt tránh quên `reset()`: một key đã được lấy có thể không tiếp tục cung cấp tín hiệu như mong đợi. Nếu `reset()` trả `false`, registration đã mất hiệu lực; không tiếp tục chờ nó như thể vẫn hoạt động.

## 8. Ví dụ Java 17+ hoàn chỉnh: WatchService và phục hồi snapshot

Ví dụ này được thiết kế **tự chạy trong một tiến trình**:

- Tạo thư mục riêng trong temp.
- Đăng ký sự kiện tạo/sửa/xóa.
- Chụp snapshot trống.
- Tạo file `item.txt`.
- Dùng `WatchService.poll(timeout)` để có cơ hội tiêu thụ WatchKey; nếu chưa nhận notification trong thời gian chờ, vẫn rescan để đối chiếu.
- Cố ý xóa **cache trong bộ nhớ** để mô phỏng việc chương trình đã bỏ lỡ thông báo; một full rescan tìm lại file.
- Xóa file, rescan xác nhận thư mục trở về trống.
- Dọn tài nguyên.

**Điểm chính:** bài thử **không giả mạo một `OVERFLOW` thật**, vì không thể ép provider phát overflow một cách ổn định. Nhưng nhánh khi OVERFLOW xuất hiện thật sử dụng **cùng cơ chế full rescan**, và bài thử chủ động xác minh chức năng reconciliation độc lập với tín hiệu OS.

Tạo file **`WatchOverflowRecoveryDemo.java`**:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WatchOverflowRecoveryDemo {

    record Stamp(long size, long modifiedMillis, boolean directory) {}

    record KeyResult(
            Map<Path, Stamp> snapshot,
            boolean valid,
            boolean overflow) {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("watch-overflow-");
        Path item = directory.resolve("item.txt");

        try {
            try (WatchService watcher =
                         FileSystems.getDefault().newWatchService()) {

                directory.register(
                        watcher,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                Map<Path, Stamp> known = scan(directory);
                System.out.println("Initial entries: " + known.size());

                Files.writeString(
                        item, "v1\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);

                WatchKey key = watcher.poll(1500, TimeUnit.MILLISECONDS);
                if (key != null) {
                    KeyResult eventResult = consumeAndRescan(
                            directory, known, key);
                    known = eventResult.snapshot();

                    if (!eventResult.valid()) {
                        throw new IOException("Watch registration invalid");
                    }

                    // Thông tin overflow có thể được log/đếm trong
                    // ứng dụng thật; bài demo không in vì khác OS.
                } else {
                    // Không có event trong deadline: vẫn có thể
                    // đối chiếu trạng thái hiện tại.
                    known = scan(directory);
                }

                System.out.println("Exists after create: "
                        + known.containsKey(item.getFileName()));

                // Mô phỏng cache mất dữ liệu/sự kiện bị bỏ lỡ.
                // KHÔNG tuyên bố OS đã phát một OVERFLOW thật.
                Map<Path, Stamp> stale = Map.of();
                Map<Path, Stamp> recovered = scan(directory);
                System.out.println("Rescan restored entry: "
                        + (!stale.containsKey(item.getFileName())
                           && recovered.containsKey(item.getFileName())));

                Files.delete(item);
                Map<Path, Stamp> afterDelete = scan(directory);
                System.out.println("Entries after delete: "
                        + afterDelete.size());
            }
        } finally {
            Files.deleteIfExists(item);
            Files.deleteIfExists(directory);
        }
    }

    private static Map<Path, Stamp> scan(Path directory)
            throws IOException {

        Map<Path, Stamp> result = new HashMap<>();

        try (DirectoryStream<Path> entries =
                     Files.newDirectoryStream(directory)) {

            for (Path entry : entries) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(
                            entry,
                            BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);

                    result.put(
                            entry.getFileName(),
                            new Stamp(
                                    attrs.size(),
                                    attrs.lastModifiedTime().toMillis(),
                                    attrs.isDirectory()));

                } catch (NoSuchFileException ignored) {
                    // Mục biến mất giữa list và readAttributes:
                    // vòng rescan khác sẽ nhìn trạng thái mới.
                }
            }
        }

        return result;
    }

    private static KeyResult consumeAndRescan(
            Path directory,
            Map<Path, Stamp> oldSnapshot,
            WatchKey key) throws IOException {

        boolean overflow = false;

        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind()
                    == StandardWatchEventKinds.OVERFLOW) {
                overflow = true;
                continue;
            }

            if (event.context() instanceof Path relativeName) {
                // Chỉ là gợi ý; có thể đã sửa/xóa/rename
                // trước khi ta thực sự truy cập tới mục này.
                Path candidate = directory.resolve(relativeName);
                // Không cần mở candidate trong bài demo.
            }
        }

        Map<Path, Stamp> latest;
        boolean valid;

        try {
            // Quét đầy đủ cho mọi key; nếu OVERFLOW,
            // đây chính là nhánh recovery.
            latest = scan(directory);
        } finally {
            // Trả key về trạng thái ready khi có thể,
            // kể cả khi scan ném IOException.
            valid = key.reset();
        }

        // oldSnapshot có thể so với latest để tìm
        // added/removed/changed tùy bài toán.
        return new KeyResult(latest, valid, overflow);
    }
}
```

Chạy:

```bash
javac WatchOverflowRecoveryDemo.java
java WatchOverflowRecoveryDemo
```

**Stdout dự kiến khi filesystem hỗ trợ watch, tiến trình có quyền I/O trong temp directory và không xảy ra lỗi:**

```text
Initial entries: 0
Exists after create: true
Rescan restored entry: true
Entries after delete: 0
```

Chương trình không in `ENTRY_CREATE` hay số lượng `ENTRY_MODIFY`, vì số lần/số kiểu event **không ổn định giữa các provider/OS**. `watcher.poll(1500, MILLISECONDS)` có thể nhận key hoặc trả null; cả hai nhánh vẫn lấy snapshot hiện thời nên các dòng output trên có ý nghĩa độc lập với việc notification tới đúng lúc hay không.

### Tại sao code vẫn có giới hạn?

- `Map<Path,Stamp>` là **cache trạng thái**, không phải nhật ký mọi thao tác.
- Fingerprint chỉ dựa trên `size`, `lastModifiedTime`, `isDirectory`; không phát hiện chắc chắn mọi sửa đổi nếu metadata không đổi.
- Việc scan có thể bị ảnh hưởng bởi concurrent rename/delete. Chỉ có một writer trong bài demo nên dễ kiểm chứng.
- Nếu directory bị xóa hoặc WatchKey trở thành invalid, chương trình ném lỗi thay vì im lặng treo.
- Đoạn code **không xử lý đệ quy** thư mục con.
- Demo dùng polling trong thời hạn hữu hạn để chạy xong. Watcher server thực tế thường có event loop lâu dài và hệ thống shutdown/cancel rõ ràng.
- Code không gây OVERFLOW thật; nhánh `event.kind() == OVERFLOW` đã được triển khai, còn cơ chế mất trạng thái được thử bằng cách làm cache `stale` rỗng.

## 9. Giải thích bước một: đăng ký trước rồi scan

Trong `main`:

```java
directory.register(watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE);

Map<Path, Stamp> known = scan(directory);
```

Việc đăng ký trước giúp WatchService có cơ hội ghi nhận những thay đổi đến sau lúc registration thành công. Khi lấy initial snapshot, ta biết các mục **đang có ở thời điểm quá trình quét quan sát**.

Điểm cần phân biệt:

- `WatchService` là nguồn notification.
- `scan(directory)` là cách đọc trạng thái hiện thời của các directory entries.
- Khi một event đến sau scan, xử lý key sẽ rescan.
- Khi đã mất một event, periodic rescan có thể sửa lại cache nếu thay đổi còn được phản ánh ở trạng thái hiện tại.
- Cả scan lẫn WatchService đều không tạo một snapshot filesystem **nguyên tử toàn thư mục** trước writer khác. Nếu yêu cầu snapshot nhất quán tuyệt đối, cần đồng bộ writer hoặc thiết kế lưu trữ/version phù hợp.

## 10. Giải thích bước hai: nhận key và kiểm tra overflow

Mã cốt lõi:

```java
WatchKey key = watcher.poll(1500, TimeUnit.MILLISECONDS);

if (key != null) {
    KeyResult result = consumeAndRescan(directory, known, key);
    known = result.snapshot();
}
```

`poll(timeout, unit)` chờ có giới hạn, thuận tiện để demo không đứng chờ vô hạn nếu platform không phát notification. Khi không có key, nó trả null; điều đó **không chứng minh thư mục không thay đổi**.

Trong `consumeAndRescan`, phải kiểm tra:

```java
if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
    overflow = true;
    continue;
}
```

**Trước** khi ép `event.context()` sang `Path`. Với `OVERFLOW`, không giả định `context` chứa tên file có thể resolve. `OVERFLOW` không xác định chính xác file nào đã thay đổi; không thể suy ra trạng thái từ nó.

`ENTRY_*` bình thường thì `context()` đối với Path watcher thường là **tên/path tương đối**, nên:

```java
Path candidate = directory.resolve(relativeName);
```

chỉ là candidate tại thời điểm xử lý. File có thể đã bị rename/delete, nên mọi thao tác mở/đọc phải sẵn sàng xử lý `NoSuchFileException` và các lỗi khác.

## 11. Giải thích bước ba: quét directory an toàn hơn

Trong helper `scan`:

```java
try (DirectoryStream<Path> entries =
         Files.newDirectoryStream(directory)) {
    for (Path entry : entries) {
        BasicFileAttributes attrs = Files.readAttributes(
                entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
}
```

Đây là đoạn trích đã rút gọn; trong chương trình hoàn chỉnh có bắt `NoSuchFileException` cho từng entry.

Tại sao dùng `DirectoryStream`?

- Không bắt buộc đưa tất cả Path vào một danh sách trước khi bắt đầu vòng lặp.
- Dễ đóng tài nguyên bằng try-with-resources.
- Có thể kết hợp với `readAttributes` để lấy metadata cần so sánh.

### TOCTOU trong lúc rescan

Giữa `Files.newDirectoryStream` và `Files.readAttributes`, một mục có thể bị xóa. Demo bắt `NoSuchFileException` và bỏ mục đó trong lượt quét hiện tại. Một rescan sau sẽ giúp tiến gần trạng thái mới.

Nếu không đọc được directory vì permission hoặc lỗi I/O, **không tự đánh dấu thư mục rỗng**. Một cache chuyển từ “có 1.000 file” sang “rỗng” chỉ vì bị `AccessDeniedException` là lỗi nghiêm trọng. Nên giữ cache cũ ở trạng thái cần retry, báo metrics/error và có policy phục hồi. Code demo cho phép lỗi khác propagate.

### NOFOLLOW_LINKS

`LinkOption.NOFOLLOW_LINKS` yêu cầu thao tác metadata trên **mục link** thay vì tự đi theo symbolic link. Điều này giúp tránh vô tình đi theo symlink tới mục nằm ngoài root trong giai đoạn snapshot metadata. Nhưng nó không tự tạo một sandbox hoàn chỉnh; nếu ứng dụng sau đó mở nội dung file theo path, vẫn cần chính sách path/symlink và quyền OS phù hợp.

## 12. So sánh snapshot: mục mới, mục biến mất, mục có dấu vết khác

Giả sử trước:

```text
Snapshot cũ:
  A.txt -> size=10, mtime=100
  B.txt -> size=20, mtime=100
```

Và sau rescan:

```text
Snapshot mới:
  B.txt -> size=35, mtime=110
  C.txt -> size=8,  mtime=105
```

Đối chiếu:

- `A.txt`: không còn trong snapshot mới → **removed**.
- `B.txt`: vẫn tồn tại nhưng metadata khác → **changed**.
- `C.txt`: chỉ có trong snapshot mới → **added**.

Ví dụ helper minh họa:

```java
static void describeDifference(
        Map<Path, Stamp> previous,
        Map<Path, Stamp> current) {

    for (var entry : current.entrySet()) {
        Stamp old = previous.get(entry.getKey());

        if (old == null) {
            System.out.println("ADDED " + entry.getKey());
        } else if (!old.equals(entry.getValue())) {
            System.out.println("CHANGED " + entry.getKey());
        }
    }

    for (Path name : previous.keySet()) {
        if (!current.containsKey(name)) {
            System.out.println("REMOVED " + name);
        }
    }
}
```

Đây là một **method minh họa bổ sung** dùng record `Stamp` của chương trình chính, không thuộc stdout cố định của demo. `HashMap` không quy định thứ tự iteration, nên nếu in danh sách thay đổi có thể sort tên trước khi in cho test deterministic.

### Không khẳng định phát hiện mọi sửa đổi

Metadata fingerprint không bảo đảm phát hiện mọi thay đổi:

- Writer có thể ghi nội dung khác với **cùng kích thước**.
- Độ phân giải `mtime` có thể không đủ tinh trong một số filesystem.
- Thao tác restore timestamp có thể khiến metadata trông như cũ.
- File có thể bị thay bằng object khác mà dấu metadata tương tự.
- Entry có thể bị tạo rồi xóa giữa hai lần scan mà không xuất hiện trong snapshot.

Nếu nghiệp vụ cần phát hiện nội dung thực, có thể hash file với `MessageDigest`; nhưng hash tốn I/O, và file có thể thay đổi ngay trong khi hash. Muốn snapshot bất biến, nên yêu cầu producer công bố file đã đóng/immutable, dùng versioned directory hoặc một cơ chế giao dịch phù hợp.

## 13. Vì sao cần reconcile ngay cả khi không có OVERFLOW?

Có hai cách triển khai watcher:

**A. Event-driven incremental:** chỉ cập nhật mục được event nhắc đến. Nhanh hơn với thư mục lớn, nhưng nhạy cảm hơn với việc bỏ sót, coalescing và race.

**B. Rescan-driven:** event chỉ là tín hiệu để quét lại directory. Chậm hơn, nhưng dễ suy luận về **trạng thái cuối cùng** và phù hợp với bài học.

Khi filesystem provider phát `OVERFLOW`, cả hai phương án phải có cơ chế **full rescan**. Với A, có thể dùng snapshot hiện tại rồi diff; với B, handler đã dùng full rescan nên chỉ cần đánh dấu/log lý do và bảo đảm cache được cập nhật.

Một watcher production có thể dùng:

```text
START
  -> register
  -> initial full scan
  -> loop:
       wait timed poll
       if key:
         drain events
         if OVERFLOW:
           full rescan
         else:
           process changed paths as hints
         reset key
       if periodic interval elapsed:
         full rescan
       if invalid key:
         remove/re-register per policy
```

Periodic full scan là lớp **chống lệch trạng thái** bổ sung; nó không biến WatchService thành log đầy đủ hay bảo đảm thấy mọi trạng thái trung gian.

## 14. Xây watcher chạy liên tục

Ví dụ event loop minh họa (không phải class độc lập):

```java
while (running.get()) {
    WatchKey key;

    try {
        key = watcher.poll(1, TimeUnit.SECONDS);
    } catch (ClosedWatchServiceException ex) {
        break;
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
    }

    if (key != null) {
        boolean overflow = false;

        try {
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    overflow = true;
                    continue;
                }
                // Thu thập path hints; không mở file mù quáng.
            }

            if (overflow) {
                // Full rescan + reconcile.
            } else {
                // Incremental handling hoặc rescan theo thiết kế.
            }
        } finally {
            if (!key.reset()) {
                // Hủy/loại bỏ registration invalid.
            }
        }
    }

    // Theo lịch: kiểm tra health, cờ dừng, periodic rescan.
}
```

Đây là **mã minh họa**, biến `running` (ví dụ `AtomicBoolean`), `watcher` và các hành động nội bộ cần được khai báo. Khi chạy lâu dài, phải có chính sách kiểm soát ngoại lệ I/O, backpressure và state.

### Dừng watcher

- Với `take()`, shutdown thường cần `Thread.interrupt()` hoặc đóng `WatchService` từ thread điều khiển.
- Với `poll(timeout)`, thread có thể kiểm tra cờ dừng sau timeout; vẫn nên đóng service khi thoát.
- `ClosedWatchServiceException` sau chủ động `close()` thường là tình huống shutdown hợp lệ, không nhất thiết là lỗi.
- `InterruptedException` cần được xử lý đúng, thường khôi phục interrupt flag nếu không có lý do nuốt tín hiệu.
- `finally` không chạy khi mất điện/kill tiến trình đột ngột; khi khởi động lại nên **rescan ban đầu**, không tin rằng events thời gian downtime còn được lưu.

## 15. WatchKey invalid: phải phục hồi registration

Nếu `key.reset()` trả `false`, `WatchKey` không còn hợp lệ. Một số nguyên nhân:

- Thư mục được theo dõi bị xóa hoặc trở nên không còn truy cập được theo cơ chế provider.
- Ứng dụng hủy registration bằng `key.cancel()`.
- `WatchService` đã đóng.
- Provider mất khả năng theo dõi mục.

Đừng chỉ `continue` vòng lặp và mong có thêm events từ key đó. Với watcher nhiều thư mục, cần loại key khỏi map và xác định có phải đăng ký lại hay không.

Nếu thư mục bị xóa rồi được tạo lại cùng đường dẫn, **registration cũ không phải lúc nào cũng tự phục hồi**. Một ứng dụng cần giám sát liên tục phải có timer kiểm tra parent/root, re-register path mới sau khi nó tồn tại trở lại và rescan lại.

Chú ý race: thư mục có thể thay đổi ngay giữa lúc kiểm tra tồn tại và register; luôn xử lý ngoại lệ và retry có giới hạn.

## 16. Khi theo dõi nhiều thư mục

Có thể đăng ký nhiều `Path` vào cùng `WatchService`. Gắn `WatchKey` với thư mục:

```java
Map<WatchKey, Path> registrations = new HashMap<>();

WatchKey key = directory.register(
        watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_MODIFY,
        StandardWatchEventKinds.ENTRY_DELETE);

registrations.put(key, directory);
```

Đây là đoạn minh họa cần biến `directory`, `watcher` và imports tương ứng.

Khi xử lý:

```java
Path root = registrations.get(key);
if (root != null) {
    // Resolve event context với đúng root tương ứng.
    // Nếu có OVERFLOW: rescan đúng root này.
}

if (!key.reset()) {
    registrations.remove(key);
}
```

Một sự kiện từ thư mục A **không** được resolve trên root B. Nếu muốn một cache snapshot cho mỗi thư mục, dùng `Map<Path, Snapshot>` hoặc state object gắn key.

Không dùng cùng một `WatchService` với nhiều consumer threads mà giả định **mọi thread đều nhận cùng một event**. Hàng đợi WatchKey thông thường được tiêu thụ bởi một trong các consumer; nên chọn một consumer loop có ownership rõ, rồi phân phối công việc nặng tới worker pool nếu cần.

## 17. Theo dõi cây thư mục đệ quy

Trong Java NIO.2 chuẩn, đăng ký thư mục thường chỉ nhận sự kiện với **các mục trực tiếp**:

```text
root/                  <- đăng ký root
  a.txt                <- quan sát trực tiếp
  sub/                 <- biết sub xuất hiện
    b.txt              <- KHÔNG tự nhận modify b.txt
```

Để theo dõi đệ quy:

1. Duyệt toàn bộ thư mục con hiện có và đăng ký **từng thư mục**.
2. Khi `ENTRY_CREATE` báo tên mới, kiểm tra mục đó có là directory không (và xử lý symlink policy).
3. Đăng ký directory mới và rescan nó.
4. Khi `OVERFLOW` ở một directory, rescan directory đó. Nếu không chắc cây đăng ký còn đầy đủ, cần tái duyệt cây để phát hiện thư mục con mới/đã mất.
5. Khi registration invalid, loại bỏ khỏi map và có kế hoạch tái đăng ký.
6. Giới hạn số watch registrations và chi phí I/O trên cây lớn.

Có race giữa việc duyệt và đăng ký thư mục con; một watcher recursive **không** có transaction bảo đảm nhìn thấy mọi biến động xảy ra cùng lúc. Đối với nghiệp vụ cần phát hiện trạng thái, periodic tree rescan là một lớp phục hồi hữu ích.

## 18. Debounce và file chưa ghi xong

Một `ENTRY_CREATE` không bảo đảm producer đã đóng file. Ví dụ:

```text
Producer:
  create report.csv
  write header
  write 10.000 rows
  close file

Consumer:
  thấy ENTRY_CREATE ngay sau create
  đọc report.csv lúc producer mới ghi 10 dòng
```

Nếu nghiệp vụ import file cần nhận nội dung hoàn chỉnh, có thể thỏa thuận **giao thức công bố**:

- Producer ghi vào file tạm trong thư mục staging.
- Producer hoàn tất write/flush/close và kiểm tra.
- Producer rename/move sang tên đích chính thức khi sẵn sàng; nếu yêu cầu atomicity, chỉ tuyên bố bảo đảm khi provider thực sự hỗ trợ.
- Consumer theo dõi tên file final, không parse temp file đang ghi.

Một chiến lược khác là **debounce** `ENTRY_MODIFY`: đợi file không thay đổi metadata trong một khoảng thời gian nhất định trước khi đọc. Nhưng debounce không chứng minh file đã hoàn chỉnh trong mọi trường hợp: producer có thể tạm ngừng lâu giữa các batch.

Điều này liên quan trực tiếp tới OVERFLOW: khi rescan thấy một file mới, vẫn cần xác định **file đã sẵn sàng** theo giao thức nghiệp vụ trước khi xử lý.

## 19. Idempotency: xử lý trùng tín hiệu không gây kết quả sai

WatchService có thể báo cùng file nhiều lần. Nếu mỗi event dẫn tới “gửi email” hoặc “trừ tiền” thì kết quả sẽ sai.

Thiết kế idempotency:

- Với mục tiêu đồng bộ cache, cập nhật theo trạng thái cuối cùng có thể lặp lại an toàn.
- Với import tệp, lưu file/version/hash/transaction ID đã xử lý để không import hai lần.
- Với một tác vụ có side effect, sử dụng queue/job state có trạng thái `PENDING/RUNNING/COMPLETED` và nguyên tắc retry rõ ràng.
- Nếu file bị thay dưới cùng tên, phân biệt phiên bản file dựa trên metadata đáng tin hoặc content version do producer công bố.
- Không coi `event.count()` là số lần nghiệp vụ cần thực hiện. Count có thể thể hiện sự gộp events, không phải số thao tác logic.

Khi overflow khiến mất thứ tự sự kiện, một state-based/idempotent consumer thường phục hồi dễ hơn một consumer cố thực hiện side effect trên từng notification.

## 20. Test OVERFLOW một cách xác định: mô phỏng mất sự kiện ở tầng xử lý

`OVERFLOW` thật phụ thuộc giới hạn hệ thống/provider; ép tạo hàng chục nghìn tệp vừa không portable vừa rủi ro. Thay vì đó, kiểm thử **hành vi phục hồi** bằng cách inject trạng thái “cache stale” hoặc một abstraction event handler.

Trong chương trình hoàn chỉnh, ta cố ý làm:

```java
Map<Path, Stamp> stale = Map.of();       // Cache mất một entry.
Map<Path, Stamp> recovered = scan(dir);  // Trạng thái thực tế.

boolean restored = recovered.containsKey(Path.of("item.txt"));
```

Đây là snippet minh họa, biến `dir` được khai báo trong bối cảnh phù hợp; ví dụ độc lập dùng `directory`.

Cách này **không chứng minh OS phát OVERFLOW**, nhưng chứng minh logic rescan có thể tái phát hiện file hiện hữu khi cache thiếu mục. Có thể mở rộng test:

1. Cache cũ có `deleted.txt` nhưng file thực tế đã bị xóa → rescan phải loại nó.
2. Cache cũ thiếu `new.txt` → rescan phải thêm nó.
3. Cache cũ có metadata cũ của `edited.txt` → rescan thấy fingerprint mới.
4. Một số file vừa tạo rồi xóa trước rescan → không thể tái tạo lịch sử, phải chấp nhận giới hạn này.
5. Scan thất bại vì `AccessDeniedException` → giữ cache ở trạng thái **không chắc chắn** và báo lỗi; không chuyển sang empty map.
6. Event handler nhận synthetic `OVERFLOW` qua abstraction test/stub (không giả là event thật từ OS) → đảm bảo gọi rescan và metrics.

Điểm cần kiểm thử là **reconciliation được kích hoạt và tính đúng của trạng thái cuối**, không phải “bao nhiêu events” mà một OS tình cờ phát trong vài mili-giây.

## 21. Kiểm thử thực tế khi thay đổi diễn ra liên tục

Một bộ tích hợp có thể:

- Tạo/sửa/xóa file giữa nhiều lượt `poll`.
- Rename file trong cùng thư mục.
- Tạo file ở thư mục con (xác nhận không tự watch recursive).
- Tạo nhiều file theo batch.
- Thay file trong khi handler đang quét.
- Khởi động watcher lại sau khi thay đổi đã xảy ra.
- Rút quyền đọc thư mục **chỉ trong test environment**, xác nhận không ghi nhận trạng thái rỗng giả.
- Dừng watcher bằng interrupt/close rồi kiểm tra giải phóng tài nguyên.
- Thực thi trên Linux/Windows/macOS và filesystem thật nếu sản phẩm hỗ trợ.

**Không viết assertion** rằng một lần `Files.writeString` luôn phát chính xác một `ENTRY_CREATE` và một `ENTRY_MODIFY` theo thứ tự cố định. Kiểm thử integration nên chờ **eventual state** trong một deadline hợp lý; kiểm thử thuật toán diff/recovery thì dùng snapshot giả lập để có output deterministic.

### Chuỗi thao tác ví dụ

```text
t0  register root
t1  initial scan: {}
t2  create a.txt
t3  event signal / rescan: {a.txt}
t4  modify a.txt
t5  event signal / rescan: {a.txt updated}
t6  delete a.txt
t7  rescan: {}
```

`t3` và `t5` có thể không tương ứng đúng một WatchKey trên mọi provider. Do đó test nên tập trung vào việc cuối cùng cache phản ánh `{a.txt}`, rồi `{}` khi file đã bị xóa, thay vì cố so event count.

## 22. Khi OVERFLOW nhiều lần: hiệu năng và backpressure

Một `OVERFLOW` lặp lại thường là tín hiệu rằng ứng dụng không theo kịp cường độ thay đổi, event processing quá chậm hoặc provider có giới hạn riêng.

Các biện pháp:

1. **Rút ngắn công việc trong watch loop:** chỉ thu nhận event hint và đưa công việc nặng sang queue có giới hạn.
2. **Gộp công việc theo đường dẫn:** nếu cùng file xuất hiện 100 modify events, chỉ lên lịch một lần kiểm tra trạng thái cuối theo policy.
3. **Thêm debouncing hợp lý** khi input producer có nhiều thao tác nhỏ.
4. **Giới hạn concurrency** của worker để không làm quá tải ổ đĩa/CPU.
5. **Rescan có điều phối:** không để 1.000 overflow events khởi tạo đồng thời 1.000 full scans cùng directory. Coalesce thành một “dirty” flag và một rescan đang chạy.
6. **Đo metrics:** overflow count, queue delay, scan duration, number of watched dirs, number of entries, error rate.
7. **Chạy periodic reconciliation** để bù đắp missed events hoặc downtime.
8. **Đánh giá kiến trúc:** nếu một directory có hàng triệu thay đổi/giây, WatchService + full rescan có thể không phải công cụ đáp ứng đúng nhu cầu. Chọn message queue, database change log hoặc upstream protocol thích hợp.

Không cố giải quyết overflow chỉ bằng cách **tăng kích thước ByteBuffer** trong Java; cơ chế queue/watch notification phụ thuộc OS/provider và không có một nút cấu hình portable cho mọi giới hạn.

## 23. Xử lý lỗi và trạng thái không chắc chắn

Một watcher chạy production cần phân biệt:

| Tình huống | Hành vi đề xuất |
|---|---|
| `watcher.poll(...) == null` | Chưa có key trong thời hạn; có thể periodic rescan |
| `OVERFLOW` | Đánh dấu dirty, full rescan, reconcile |
| `key.reset() == false` | Registration invalid; loại bỏ, cân nhắc đăng ký lại |
| `Files.newDirectoryStream` thất bại | Không tự coi thư mục rỗng; giữ cache cũ và báo lỗi |
| Mục biến mất khi `readAttributes` | Bỏ qua mục đó trong lượt hiện tại, retry/reconcile theo policy |
| `AccessDeniedException` | Kiểm tra process OS permissions, parent directory ACL, mount |
| `ClosedWatchServiceException` | Thường là shutdown; nếu không chủ đích, điều tra lifecycle |
| `InterruptedException` | Phục hồi interrupt flag và thoát/retry theo policy |
| `ENTRY_CREATE` trên file chưa hoàn tất | Chờ publish protocol, retry có giới hạn hoặc debounce |
| Hết bộ nhớ vì lưu snapshot lớn | Giới hạn dữ liệu, dùng index/state store, phân vùng watcher |
| File bị sửa trong khi hash | Dữ liệu không phải snapshot nguyên tử; cần producer contract |

Nếu full rescan thất bại, một thiết kế chặt nên đưa watcher vào trạng thái **DEGRADED/NEEDS_RESCAN**, không cập nhật cache theo một snapshot chưa đầy đủ. Để chạy ổn định, log lỗi và lập lịch retry có backoff; đừng loop nóng khiến lỗi I/O liên tục chiếm CPU.

## 24. Bảo mật khi xử lý file từ thư mục được theo dõi

Một số nguy cơ mà OVERFLOW recovery không giải quyết:

- **Path traversal / symlink escape:** `directory.resolve(event.context())` không phải ranh giới an ninh nếu attacker có thể thay namespace; xác thực root và giới hạn quyền process.
- **Symlink races:** file có thể bị thay giữa lúc kiểm tra metadata và lúc mở. `NOFOLLOW_LINKS` trong một lệnh readAttributes không đảm bảo mọi I/O sau đó an toàn.
- **Không tin file vừa xuất hiện:** một producer không tin cậy có thể tạo content sai, quá lớn, zip bomb hoặc file chứa script; phải validate định dạng và quota.
- **Các quyền OS:** WatchService không cấp quyền đọc file. Process có thể nhận notification nhưng bị từ chối đọc nội dung.
- **Tệp bí mật:** đừng in toàn bộ nội dung hoặc path chứa dữ liệu nhạy cảm trong logs.
- **Multi-tenant:** xác nhận quyền nghiệp vụ trước khi xử lý file của người khác. OS chỉ biết process identity, không tự biết tenant của từng request.
- **Chạy code từ watcher:** tuyệt đối không tự thực thi file mới được phát hiện mà không có kiểm soát nguồn tin cậy, chữ ký/approval và isolation.
- **Xóa nhầm:** reconciliation không phải lệnh `delete` tự động. Khi một entry vắng mặt trong scan, trước khi xóa dữ liệu tại database/backup nên có policy xác nhận trạng thái và xử lý lỗi I/O hợp lý.

File system events là **tín hiệu đầu vào không đáng tin về mặt nghiệp vụ**. Hãy validate nguồn, schema và trạng thái trước khi tạo side effects.

## 25. Mở rộng: thiết kế theo mô hình “dirty directory”

Đối với watcher có nhiều thay đổi liên tục, một cách tổ chức tốt là quản lý **dirty flag**:

```text
WatchService consumer:
  on ENTRY_*:
      đánh dấu path/dir cần kiểm tra
  on OVERFLOW:
      đánh dấu directory FULL_RESCAN_REQUIRED
  reset key

Reconciliation worker:
  if FULL_RESCAN_REQUIRED:
      scan directory
      so snapshot
      apply idempotent changes
      clear flag khi scan + commit thành công
  else:
      kiểm tra paths dirty
      apply state changes
```

Quy tắc quan trọng với đồng thời:

- Không xóa cờ dirty khi một thay đổi mới đã đến **trong lúc** rescan/commit. Cần version/generation counter hoặc cơ chế queue có khóa để không làm mất tín hiệu.
- Không chạy nhiều full rescans đồng thời cùng một directory nếu không có lý do.
- Retry scan/commit thất bại mà không tuyên bố dữ liệu đã đồng bộ.
- Có lịch periodic scan độc lập để bắt missed events.
- Nếu một entry được tạo và xóa trong lúc scan, không suy ra lịch sử đầy đủ từ snapshot cuối.
- Backpressure: khi công việc downstream chậm, không để queue unbounded gây OOM.

Đây là bước nâng cao từ demo single-thread. Không cần triển khai toàn bộ concurrency framework để hiểu trọng tâm WatchService và overflow.

## 26. Các lỗi phổ biến trong bài thực hành

1. **Không xử lý `OVERFLOW`**: cache có thể lệch vĩnh viễn sau khi mất event.
2. **Ép `event.context()` sang `Path` trước khi kiểm tra OVERFLOW**: dữ liệu overflow không đại diện cho file path hợp lệ.
3. **Tin rằng từng event là lịch sử chính xác**: có thể bị gộp hoặc mất, không có exactly-once.
4. **Không gọi `key.reset()`**: bỏ lỡ các tín hiệu tiếp theo.
5. **Không kiểm tra `reset() == false`**: registration invalid vẫn bị giữ trong cấu trúc quản lý.
6. **Dùng `take()` trong thread chính mà không có shutdown**: chờ vô hạn khi không còn sự kiện.
7. **Dùng `poll()` trong vòng busy loop**: CPU tăng cao khi không có key.
8. **Đăng ký sau khi đã scan rồi tưởng không có race**: thay đổi ở khoảng trước registration có thể bị bỏ lỡ.
9. **Đọc `ENTRY_CREATE` như “file đã ghi xong”**: producer có thể đang ghi nội dung.
10. **Chạy tác vụ nặng trực tiếp trong watcher event loop**: backlog tăng và dễ overflow.
11. **Dùng snapshot metadata để tuyên bố mọi nội dung luôn phát hiện được**: hash/version có thể cần thiết.
12. **Nếu scan lỗi thì trả `Map.of()` và xóa toàn bộ cache**: nhầm “không đọc được” với “thư mục rỗng”.
13. **Coi watch root là recursive**: directory con cần đăng ký riêng.
14. **Giải quyết OVERFLOW bằng cách tăng read buffer**: không sửa giới hạn provider event queue.
15. **Tạo hàng triệu file để “unit test overflow”**: không portable, gây áp lực máy; test recovery bằng injection/synthetic state.
16. **Chỉ tin output test ở một OS**: số lượng/thứ tự events thay đổi theo provider.
17. **Không quản lý symlink/TOCTOU**: có thể đọc nhầm file ngoài phạm vi.
18. **Không dùng idempotency**: một file modify có thể kích hoạt xử lý nhiều lần.
19. **Không khôi phục khi restart**: notification thời gian downtime không được WatchService lưu bền.
20. **Đóng watcher nhưng không xử lý ClosedWatchServiceException**: báo lỗi shutdown không cần thiết.
21. **Giữ các keys invalid trong map**: rò reference và mất khả năng theo dõi đúng.
22. **Dùng WatchService làm audit log tài chính/pháp lý**: không có bảo đảm đầy đủ mọi thao tác trung gian.
23. **Không giới hạn queue/concurrency**: memory/CPU exhaustion khi thư mục đổi nhanh.
24. **Xem kết quả scan là snapshot nguyên tử**: filesystem có thể đổi trong lúc duyệt, cần giao thức writer.
25. **Hòa giải cache xong nhưng không reset key đúng lúc**: mất khả năng nhận notification tiếp theo.

## 27. Bài tập mở rộng

### Bài 1 — Diff added/removed/changed

Viết hàm `diff(oldSnapshot, newSnapshot)` trả ba tập `added`, `removed`, `changed`. Dùng record `Stamp` trong ví dụ. Thử dữ liệu giả lập để stdout không phụ thuộc WatchService.

### Bài 2 — Force reconciliation khi mất sự kiện

Tạo file `A.txt` rồi không đưa event vào cache; gọi `scan` và cập nhật cache. Tiếp tục xóa file và quét lại để loại mục bị mất. Giải thích tại sao đây là test recovery nhưng **không khẳng định OS đã phát OVERFLOW**.

### Bài 3 — Watch loop chạy 30 giây

Dùng `poll(500, TimeUnit.MILLISECONDS)`, trong 30 giây cho phép bạn tạo/sửa/xóa file thủ công. Đừng in mỗi event không kiểm soát; tổng hợp số key, số overflow và số lần rescan.

### Bài 4 — Debounce

Thêm map `Path → lastHintTime`, gộp nhiều modify hints trong 300–500 ms và chỉ xử lý khi đã đủ thời gian; sau cùng vẫn validate file complete bằng producer contract.

### Bài 5 — Theo dõi đệ quy

Duyệt cây và đăng ký từng thư mục. Khi `ENTRY_CREATE` là directory, đăng ký rồi scan nội dung. Khi key invalid, loại khỏi map. Thêm periodic full tree rescan để phục hồi khoảng race.

### Bài 6 — Khôi phục sau restart

Chạy watcher, tạo file, tắt tiến trình, sửa/xóa/thêm file trong lúc watcher tắt, bật lại. Chứng minh initial scan tái lập **trạng thái cuối**, nhưng không cho biết chính xác mỗi thao tác xảy ra trong downtime.

### Bài 7 — Giới hạn tài nguyên

Giả lập 10.000 event hints cho cùng một tên file bằng unit test abstraction (không tạo 10.000 file thật). Đảm bảo scheduling coalesce thành một công việc kiểm tra trạng thái cuối và queue không tăng không giới hạn.

## 28. Câu hỏi tự kiểm tra

**1.** `OVERFLOW` có cung cấp tên những file đã bị mất event không?  
**Không.** Nó chỉ báo khả năng mất notification; phải rescan trạng thái hiện tại.

**2.** Sau full rescan, có thể biết file nào đã được tạo rồi xóa giữa hai lần scan không?  
**Không chắc.** Trạng thái cuối có thể không lưu dấu vết thao tác trung gian.

**3.** Vì sao event handler cần gọi `key.reset()`?  
**Để key có thể tiếp tục nhận báo hiệu** nếu registration còn hợp lệ.

**4.** `key.reset() == false` nghĩa là gì?  
**Registration invalid**, cần loại khỏi watcher state và cân nhắc đăng ký lại.

**5.** `poll(timeout)` trả null có chứng minh không có file đổi không?  
**Không.** Chỉ là chưa có key được lấy trong khoảng đợi, với mọi giới hạn của provider.

**6.** Vì sao snapshot nên đọc trực tiếp từ filesystem thay vì tin vào danh sách events?  
**Vì events có thể gộp, đến trễ hoặc bị mất**, còn snapshot phản ánh trạng thái được quan sát tại thời điểm scan.

**7.** Có thể xử lý `ENTRY_CREATE` bằng cách parse file ngay không?  
**Không nên mặc định.** Producer có thể còn ghi; dùng publish protocol/validation/debounce phù hợp.

**8.** `Files.newDirectoryStream(root)` có tự quét đệ quy không?  
**Không.** Nó liệt kê các mục trực tiếp trong root.

**9.** Fingerprint size + mtime có phát hiện mọi lần thay nội dung không?  
**Không.** Có thể cần digest/version/snapshot protocol khi yêu cầu cao hơn.

**10.** Vì sao không nên stress tạo hàng triệu file chỉ để unit test overflow?  
**Vì kết quả phụ thuộc provider và có thể làm quá tải môi trường**; nên test recovery độc lập với nguồn event.

**11.** Khi full rescan ném `AccessDeniedException` có nên gán cache bằng empty map không?  
**Không.** “Không đọc được” không đồng nghĩa “không có entry”; giữ trạng thái không chắc chắn và retry.

**12.** `WatchService` có thể dùng làm exactly-once audit log không?  
**Không.** Nó không bảo đảm lưu bền và phát lại mọi thay đổi.

## 29. Checklist trước khi triển khai

- [ ] Xác định directory root và file types cần theo dõi.
- [ ] Xác định permission của process và filesystem/provider hỗ trợ.
- [ ] Đăng ký root trước initial scan khi phù hợp.
- [ ] Định nghĩa snapshot cache và mức chính xác cần thiết của metadata/hash/version.
- [ ] Nhận `WatchKey` bằng take hoặc timed poll, có shutdown policy.
- [ ] Drain `pollEvents()` và **xử lý OVERFLOW trước context cast**.
- [ ] Với OVERFLOW, full rescan và reconcile theo trạng thái thực tế.
- [ ] Có periodic reconciliation/initial scan khi cần phục hồi missed events.
- [ ] Bảo đảm `key.reset()` và xử lý key invalid.
- [ ] Định nghĩa registration map nếu theo dõi nhiều thư mục.
- [ ] Không giả định recursive watch; đăng ký subdirectories riêng.
- [ ] Dùng idempotent processing, queue có giới hạn và debounce phù hợp.
- [ ] Xử lý file chưa hoàn tất, rename, symlink và TOCTOU.
- [ ] Không biến scan failure thành trạng thái rỗng giả.
- [ ] Thống kê overflow, latency, rescan errors, queue depth.
- [ ] Kiểm thử đúng/tái lập bằng synthetic state và trên OS thật cho integration.
- [ ] Không xác nhận số lượng hay thứ tự `ENTRY_MODIFY` cố định.
- [ ] Thử restart để xác nhận cache được xây lại từ snapshot.
- [ ] Đóng WatchService, xử lý interrupt, cancel keys khi không cần nữa.
- [ ] Nếu yêu cầu mọi thao tác chính xác, chọn journal/transaction log khác.

## 30. Tổng kết

- **WatchService là hệ thống thông báo thay đổi thư mục**, không phải log đầy đủ hay snapshot nhất quán của filesystem.
- **`OVERFLOW` là tín hiệu rằng một số events có thể đã bị mất**. Không thể khôi phục trực tiếp danh sách event bị bỏ; cần **quét lại trạng thái hiện tại**.
- Một thiết kế đơn giản đáng tin hơn cho bài thực hành là **register → scan → poll/take → drain events → rescan/reconcile → reset → lặp**.
- **WatchEvent.context()` với `ENTRY_*` thường là Path tương đối**; phải gắn đúng thư mục đã đăng ký. Với `OVERFLOW`, không ép context sang Path.
- **Snapshot metadata chỉ phát hiện được sự khác biệt mà nó lưu**; size/mtime không đủ cho mọi yêu cầu dữ liệu.
- **Unit test recovery bằng synthetic stale cache**, không phụ thuộc tạo overflow thật; integration test kiểm tra eventual state, không đếm số lượng events cố định.
- Khi cần production-grade watcher: thêm shutdown, re-registration, periodic rescan, xử lý race, idempotency, giới hạn tài nguyên và giám sát.

### Tài liệu tham khảo Java 17

- [WatchService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html)
- [WatchKey](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchKey.html)
- [WatchEvent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchEvent.html)
- [StandardWatchEventKinds](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardWatchEventKinds.html)
- [Path.register](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html#register(java.nio.file.WatchService,java.nio.file.WatchEvent.Kind...))
- [Files.newDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newDirectoryStream(java.nio.file.Path))
- [BasicFileAttributes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/BasicFileAttributes.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-010">Nén, giải nén archive có giới hạn và kiểm tra đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành Java 17: nén, giải nén ZIP có giới hạn và kiểm tra đường dẫn entry

## 1. Mục tiêu và kết quả thực hành

Bài thực hành này xây dựng một **ZIP packer/extractor** bằng Java Core I/O, không phụ thuộc thư viện ngoài. Chương trình nén một cây thư mục thành `.zip`, giải nén sang thư mục đích mới và chủ động từ chối dữ liệu vượt chính sách.

Sau khi làm xong, bạn có thể:

1. Dùng `Files.walkFileTree` duyệt cây file **không đi theo symbolic link** theo mặc định.
2. Dùng `ZipOutputStream` tạo archive gồm file và cả **thư mục rỗng**.
3. Dùng `ZipInputStream` xử lý lần lượt từng `ZipEntry`, không load tất cả file vào RAM.
4. Đặt giới hạn về **số entry, độ sâu, độ dài tên, số byte mỗi file và tổng số byte giải nén**.
5. Phát hiện **Zip Slip** qua entry tên `../escape.txt`, tên tuyệt đối hoặc các separator không an toàn.
6. Dùng `Path.resolve`, `normalize`, `Path.startsWith(Path)`, `NOFOLLOW_LINKS` và `CREATE_NEW` đúng phạm vi.
7. Tạo thư mục cha cho file con dù ZIP không có directory entry riêng.
8. Phân biệt thành công của việc ghi dữ liệu, kiểm tra đường dẫn, **toàn vẹn của archive** và khả năng chống các race trên filesystem.
9. Chạy test **round trip** rồi thử hai archive có vấn đề: traversal và quá giới hạn giải nén.

Kết quả mong đợi:

~~~text
packedBytes=9
unpackedBytes=9
sameA=true
emptyDirectory=true
zipSlip=Unsafe entry name
limit=Uncompressed byte limit
outsideCreated=false
~~~

**Lưu ý:** Đây là bài thực hành thiết kế nền tảng và phòng vệ nhiều lớp. Nó **không tự xưng là sandbox hoàn chỉnh trước đối thủ có quyền sửa filesystem đồng thời**. Phần cuối giải thích cách nâng lên thành giải pháp production với staging, kiểm soát quyền và xử lý TOCTOU.

## 2. Bài toán cụ thể

Giả sử input là:

~~~text
source/
  docs/
    a.txt       (Alpha)
    b.txt       (Beta)
  empty/
~~~

Chương trình tạo:

~~~text
bundle.zip
  docs/
  docs/a.txt
  docs/b.txt
  empty/
~~~

Sau đó giải nén vào **thư mục output mới**, ta mong:

~~~text
output/
  docs/
    a.txt       (Alpha)
    b.txt       (Beta)
  empty/
~~~

Cùng lúc, extractor phải từ chối:

~~~text
../escape.txt              -> ghi ra ngoài output
/srv/app/config.txt        -> đường dẫn tuyệt đối
C:/Windows/Temp/a.txt      -> tên có dấu ':' kiểu Windows drive
docs\..\escape.txt         -> backslash traversal
docs/./a.txt               -> tên không chuẩn theo chính sách
docs/../a.txt              -> thành phần '..'
docs/a.txt (lặp)           -> duplicate/overwrite
large.txt (bung quá quota) -> zip bomb / disk exhaustion
~~~

ZIP là **archive có nhiều entry mang tên** chứ không phải một byte stream nén GZIP. `ZipOutputStream` và `ZipInputStream` làm việc lần lượt với `ZipEntry`. Định dạng ZIP có thể lưu từng entry bằng `DEFLATED` hoặc `STORED`; ZIP **không đồng nghĩa mọi entry đều bị nén**.

## 3. Thiết kế chính sách trước khi viết code

Không nên viết hàm `extract(zip, target)` rồi mới tìm cách vá các tên nguy hiểm. Hãy xác định rõ chính sách:

| Ràng buộc | Giá trị trong ví dụ | Ý nghĩa |
|---|---:|---|
| `maxEntries` | 20 | Giới hạn tổng số file và directory entry |
| `maxDepth` | 8 | Giới hạn số thành phần tên entry |
| `maxName` | 100 | Giới hạn độ dài chuỗi tên entry |
| `maxFileBytes` | 128 | Giới hạn số byte **chưa nén** của mỗi file |
| `maxTotalBytes` | 256 | Giới hạn tổng số byte **chưa nén** |
| Output dir | Chưa tồn tại | Không merge/overwrite dữ liệu cũ |
| Symlink đầu vào | Từ chối | Không pack nội dung ngoài cây được phép |
| Symlink trên output | Không theo khi duyệt cha | Giảm nguy cơ ghi lệch đích |
| File trùng đường dẫn | Từ chối | Tránh thay thế dữ liệu |
| Entry name bất thường | Từ chối | Tránh traversal/platform-specific path |
| Bộ nhớ đọc/ghi | Buffer 8192 byte | Không `readAllBytes()` với file không tin cậy |

Các ngưỡng `128`/`256` byte cực nhỏ để dễ thử **bài thực hành**; chúng **không phải gợi ý quota production**. Trong hệ thống thật, chọn quota theo yêu cầu nghiệp vụ, memory/disk budget, số người dùng đồng thời, thời gian và giới hạn hệ thống.

Chúng ta dùng một Java `record` để chứa chính sách:

~~~java
record Limits(
        int maxEntries,
        int maxDepth,
        int maxName,
        long maxFileBytes,
        long maxTotalBytes) {
}
~~~

Đây là **mã trích từ class chính ở mục 6**, không phải file chạy độc lập. Trong code production, nên validate những giá trị record nhận vào đều dương/hợp lệ ở constructor/factory; ví dụ này khởi tạo bằng hằng số đã kiểm soát.

## 4. Bước 1 — duyệt nguồn để nén mà không đi ra ngoài cây

`Files.walkFileTree(root, visitor)` mặc định **không theo symbolic link** khi không truyền `FOLLOW_LINKS`. Với `SimpleFileVisitor`:

- `preVisitDirectory`: gặp directory, có thể ghi một directory entry kết thúc bằng dấu `/`.
- `visitFile`: gặp file thường, ghi một file entry và stream từng chunk.
- Nếu gặp symlink hoặc file type không được hỗ trợ, ví dụ **từ chối** thay vì silently follow/copy.
- Nếu tệp nguồn thay đổi trong lúc pack, cần xử lý kiểm thử/threat model riêng; `walkFileTree` không tạo snapshot bất biến của cả cây thư mục.

### Lấy tên entry độc lập máy chạy

`Path` trên Windows có thể biểu diễn separator khác Linux. Tên trong ZIP nên được chuẩn hóa thành **relative name dùng `/`**:

~~~java
static String relativeName(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
}
~~~

Đây là method minh họa. Không ghi tên tuyệt đối của file nguồn vào ZIP: người giải nén chỉ cần tên tương đối dưới một thư mục đích do họ chọn.

**Không tạo ZIP ngay trong cây nguồn đang duyệt.** Nếu output archive nằm trong source directory, traversal có thể gặp chính file ZIP đang tăng kích thước và làm cho input không ổn định. Ví dụ sẽ từ chối `archivePath.startsWith(sourceRoot)` sau khi canonicalize root.

### Giữ directory rỗng

Nếu chỉ tạo entry cho file, một directory `empty/` không có file con có thể bị mất. Vì vậy với directory (ngoại trừ root), tạo `ZipEntry("relative/path/")`. Phần directory entry **không mang nội dung file thường** trong demo.

## 5. Bước 2 — định nghĩa tên entry được phép và đường dẫn đích

### 5.1. Vì sao `entry.getName()` là dữ liệu không đáng tin?

Tên ZIP do người tạo archive quyết định. Java `ZipInputStream.getNextEntry()` trả `ZipEntry` nhưng **không tự kiểm tra rằng tên file đó an toàn để ghi vào một output directory**.

Phản ví dụ:

~~~java
Path target = outputRoot.resolve(entry.getName());
Files.copy(zipInputStream, target); // KHÔNG được làm với tên chưa kiểm tra.
~~~

Đây là **mã phản ví dụ**, không chạy độc lập. Với tên `../escape.txt`, thao tác resolve có thể đưa file ra ngoài `outputRoot`. Đây là **Zip Slip/path traversal**.

### 5.2. Kiểm tra từng thành phần tên

Policy của demo:

- Từ chối tên rỗng hoặc quá `maxName` ký tự.
- Từ chối tên bắt đầu bằng `/`.
- Từ chối dấu `\` (tránh Windows traversal/UNC và sự nhập nhằng separator).
- Từ chối ký tự NUL.
- Loại dấu `/` cuối **chỉ khi kiểm tra directory entry**.
- Chia theo `/` và từ chối thành phần rỗng, `.`, `..` hoặc chứa dấu `:`.
- Giới hạn số thành phần theo `maxDepth`.
- Sau đó mới `root.resolve(name).normalize()` và xác nhận `target.startsWith(root)` theo **thành phần Path**.

~~~java
static Path targetFor(Path root, String name, Limits limits)
        throws IOException {
    validateName(name, limits);
    Path target = root.resolve(name).normalize();
    if (target.equals(root) || !target.startsWith(root)) {
        throw new IOException("Unsafe entry name");
    }
    return target;
}
~~~

`validateName` và `Limits` được triển khai trong code đầy đủ bên dưới. **Không dùng `target.toString().startsWith(root.toString())`** vì chuỗi `/safe/output-other` có thể trông như con của `/safe/output` dù `Path` nói khác.

### 5.3. Phân biệt normalize với real path

- `normalize()` là xử lý **cú pháp đường dẫn**, loại thành phần `.`/`..`; không kiểm tra symbolic link thực tế trên disk.
- `toRealPath()` xác định path thực và cần tài nguyên tồn tại; nó có thể resolve symlink theo hành vi được yêu cầu.
- `Path.startsWith(Path)` so sánh theo các thành phần đường dẫn trên provider tương ứng, không đơn thuần là tiền tố chuỗi.
- `Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)` giúp từ chối một symlink khi đang xét một component có phải directory thật hay không.

Một symlink trong thư mục cha vẫn có thể đưa I/O ra ngoài root nếu chỉ kiểm tra `normalize()+startsWith`. Demo tạo **output directory mới** dưới parent do ứng dụng kiểm soát và kiểm tra các thành phần khi tạo directory. Nhưng nếu attacker có thể sửa parent/concurrent subdirectory, còn rủi ro **TOCTOU**, giải thích ở mục 10.

## 6. Chương trình Java 17+ hoàn chỉnh: pack, unpack và hai trường hợp bị chặn

**Tên file: `ArchivePracticeDemo.java`**

Chương trình dưới đây tự tạo thư mục tạm và dữ liệu test nên không cần bạn chuẩn bị file bên ngoài. Code sử dụng **Java 17 standard library**; không cần Maven, Gradle hay JUnit để chạy bài lab.

~~~java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

public class ArchivePracticeDemo {
    record Limits(int maxEntries, int maxDepth, int maxName,
                  long maxFileBytes, long maxTotalBytes) {
    }

    static final Limits NORMAL = new Limits(20, 8, 100, 128, 256);

    // Cấm relative path nguy hiểm và đường dẫn không portable.
    static void validateName(String name, Limits limits)
            throws IOException {
        if (name.isEmpty() || name.length() > limits.maxName()
                || name.charAt(0) == '/' || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe entry name");
        }

        String value = name.endsWith("/")
                ? name.substring(0, name.length() - 1)
                : name;

        String[] pieces = value.split("/", -1);
        if (pieces.length > limits.maxDepth()) {
            throw new IOException("Unsafe entry name");
        }

        for (String part : pieces) {
            if (part.isEmpty() || part.equals(".")
                    || part.equals("..") || part.indexOf(':') >= 0) {
                throw new IOException("Unsafe entry name");
            }
        }
    }

    static String relativeName(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    // Duyệt file nguồn, tạo ZIP theo entry và giới hạn byte được đọc.
    static long pack(Path sourceDir, Path archive, Limits limits)
            throws IOException {
        if (Files.isSymbolicLink(sourceDir)) {
            throw new IOException("Symlink source");
        }

        Path root = sourceDir.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || archive.toAbsolutePath().normalize().startsWith(root)) {
            throw new IOException("Archive path invalid");
        }

        final long[] total = {0};
        final int[] entries = {0};

        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(
                        archive,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE),
                StandardCharsets.UTF_8)) {

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                void begin(String name) throws IOException {
                    validateName(name, limits);
                    if (++entries[0] > limits.maxEntries()) {
                        throw new IOException("Entry limit");
                    }
                    out.putNextEntry(new ZipEntry(name));
                }

                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir, BasicFileAttributes attrs)
                        throws IOException {

                    if (!dir.equals(root)) {
                        begin(relativeName(root, dir) + "/");
                        out.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attrs)
                        throws IOException {

                    if (!attrs.isRegularFile()) {
                        throw new IOException(
                                "Unsupported source type");
                    }

                    begin(relativeName(root, file));
                    long size = 0;

                    try (InputStream in = Files.newInputStream(
                            file, LinkOption.NOFOLLOW_LINKS)) {
                        byte[] buffer = new byte[8192];

                        for (int n; (n = in.read(buffer)) != -1; ) {
                            if (n == 0) {
                                throw new IOException(
                                        "No input progress");
                            }

                            if (n > limits.maxFileBytes() - size
                                    || n > limits.maxTotalBytes()
                                           - total[0]) {
                                throw new IOException(
                                        "Uncompressed byte limit");
                            }

                            out.write(buffer, 0, n);
                            size += n;
                            total[0] += n;
                        }
                    }

                    out.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException | RuntimeException ex) {
            // Không để lại ZIP pack dở.
            try {
                Files.deleteIfExists(archive);
            } catch (IOException cleanup) {
                ex.addSuppressed(cleanup);
            }
            throw ex;
        }

        return total[0];
    }

    static Path targetFor(Path root, String name, Limits limits)
            throws IOException {
        validateName(name, limits);

        Path target = root.resolve(name).normalize();
        if (target.equals(root) || !target.startsWith(root)) {
            throw new IOException("Unsafe entry name");
        }
        return target;
    }

    // Tạo từng thư mục cha, không chấp nhận symbolic link làm directory.
    static void directories(Path root, Path destDir) throws IOException {
        Path at = root;

        for (Path part : root.relativize(destDir)) {
            at = at.resolve(part);

            if (Files.exists(at, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(
                        at, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException(
                            "Non-directory or symlink parent");
                }
            } else {
                Files.createDirectory(at);
            }
        }
    }

    // Giải nén ZIP vào thư mục MỚI, chặn path traversal và byte bomb.
    static long unpack(Path archive, Path newOutput, Limits limits)
            throws IOException {
        Path root = Files.createDirectory(newOutput).toRealPath();
        Set<Path> names = new HashSet<>();
        int entries = 0;
        long total = 0;

        try (ZipInputStream in = new ZipInputStream(
                Files.newInputStream(archive),
                StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > limits.maxEntries()) {
                    throw new IOException("Entry limit");
                }

                Path target = targetFor(
                        root, entry.getName(), limits);

                if (!names.add(target)) {
                    throw new IOException("Duplicate entry");
                }

                if (entry.isDirectory()) {
                    directories(root, target);
                    in.closeEntry();
                    continue;
                }

                directories(root, target.getParent());
                boolean created = false;

                try {
                    long size = 0;

                    try (OutputStream out = Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)) {
                        created = true;
                        byte[] buffer = new byte[8192];

                        for (int n; (n = in.read(buffer)) != -1; ) {
                            if (n == 0) {
                                throw new IOException(
                                        "No output progress");
                            }

                            if (n > limits.maxFileBytes() - size
                                    || n > limits.maxTotalBytes()
                                           - total) {
                                throw new IOException(
                                        "Uncompressed byte limit");
                            }

                            out.write(buffer, 0, n);
                            size += n;
                            total += n;
                        }
                    }

                    in.closeEntry();

                } catch (IOException | RuntimeException ex) {
                    // Nếu đang giải nén dở file hiện tại, xóa file đó.
                    if (created) {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException cleanup) {
                            ex.addSuppressed(cleanup);
                        }
                    }
                    throw ex;
                }
            }
        }

        // Các file entry trước đó vẫn còn nếu unpack lỗi ở entry sau.
        return total;
    }

    // Chỉ phục vụ bài kiểm thử: tạo một ZIP chứa một entry tùy chọn.
    static void makeSingleZip(
            Path archive, String name, byte[] content)
            throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(
                        archive,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE),
                StandardCharsets.UTF_8)) {

            out.putNextEntry(new ZipEntry(name));
            out.write(content);
            out.closeEntry();
        }
    }

    // Chỉ xóa directory tạm do demo tạo và sở hữu.
    static void clean(Path work) throws IOException {
        if (!Files.exists(work)) {
            return;
        }

        try (var paths = Files.walk(work)) {
            for (Path p : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory(
                "archive-practice-");

        try {
            Path source = Files.createDirectory(
                    work.resolve("source"));
            Path docs = Files.createDirectory(
                    source.resolve("docs"));

            Files.writeString(
                    docs.resolve("a.txt"), "Alpha",
                    StandardCharsets.UTF_8);
            Files.writeString(
                    docs.resolve("b.txt"), "Beta",
                    StandardCharsets.UTF_8);
            Files.createDirectory(source.resolve("empty"));

            // Happy path: source -> ZIP -> output mới.
            Path zip = work.resolve("bundle.zip");
            System.out.println(
                    "packedBytes=" + pack(source, zip, NORMAL));

            Path output = work.resolve("output");
            System.out.println(
                    "unpackedBytes=" + unpack(zip, output, NORMAL));

            System.out.println("sameA=" + Arrays.equals(
                    Files.readAllBytes(docs.resolve("a.txt")),
                    Files.readAllBytes(
                            output.resolve("docs/a.txt"))));
            System.out.println("emptyDirectory=" +
                    Files.isDirectory(
                            output.resolve("empty")));

            // Negative test 1: ZIP có tên thoát ra ngoài root.
            Path escape = work.resolve("escape.zip");
            makeSingleZip(
                    escape, "../escape.txt", new byte[]{1});

            try {
                unpack(escape, work.resolve("reject-escape"), NORMAL);
            } catch (IOException ex) {
                System.out.println(
                        "zipSlip=" + ex.getMessage());
            }

            // Negative test 2: entry nhỏ khi nén nhưng bung quá limit.
            Path huge = work.resolve("huge.zip");
            makeSingleZip(
                    huge, "large.txt", new byte[200]);

            try {
                unpack(
                        huge,
                        work.resolve("reject-large"),
                        new Limits(20, 8, 100, 32, 64));
            } catch (IOException ex) {
                System.out.println(
                        "limit=" + ex.getMessage());
            }

            System.out.println("outsideCreated=" +
                    Files.exists(
                            work.resolve("escape.txt")));

        } finally {
            clean(work);
        }
    }
}
~~~

Chạy tại thư mục chứa `ArchivePracticeDemo.java`:

~~~bash
javac ArchivePracticeDemo.java
java ArchivePracticeDemo
~~~

**Stdout đã kiểm tra bằng Java:**

~~~text
packedBytes=9
unpackedBytes=9
sameA=true
emptyDirectory=true
zipSlip=Unsafe entry name
limit=Uncompressed byte limit
outsideCreated=false
~~~

### Giải thích kết quả

- `packedBytes=9`: hai file chứa `Alpha` (5 byte UTF-8) và `Beta` (4 byte UTF-8). Đó là **số byte dữ liệu gốc đã đưa vào ZIP**, không phải `Files.size(bundle.zip)`. ZIP có header và central directory nên kích thước archive khác.
- `unpackedBytes=9`: tổng số byte thật sự giải nén thành file, độc lập metadata size chưa chắc đáng tin.
- `sameA=true`: so sánh bằng `Arrays.equals(byte[], byte[])` xác nhận file nguồn và file khôi phục có bytes bằng nhau.
- `emptyDirectory=true`: directory entry `empty/` đã được pack và tạo lại đúng tên.
- `zipSlip=Unsafe entry name`: `../escape.txt` bị từ chối trước khi được dùng làm đường dẫn ghi.
- `limit=Uncompressed byte limit`: entry gồm 200 byte `0` bị từ chối khi giới hạn mỗi file chỉ 32 byte và tổng 64 byte.
- `outsideCreated=false`: không có file `escape.txt` bị tạo ở parent của directory đích.

Chương trình sử dụng `Files.createTempDirectory` và chỉ xóa **cây tạm do chính nó tạo**, kể cả khi test throw. Nếu chạy trong môi trường không cho tạo file hoặc thiếu quyền truy cập filesystem, thao tác có thể ném `IOException`; đó là giới hạn môi trường chứ không phải output của happy path.

## 7. Bước 3 — kiểm tra ZIP thực tế có gì?

Khi pack, `ZipOutputStream.putNextEntry` ghi tên entry và bắt đầu luồng dữ liệu của entry. `closeEntry` hoàn tất entry hiện tại, còn `ZipOutputStream.close` hoàn tất cả ZIP archive và đóng output. **Không mở ZIP output để đọc trước khi stream được đóng/finish**: central directory có thể chưa được ghi đủ.

Hai cách đọc ZIP có mục tiêu khác nhau:

| API | Dùng khi nào? |
|---|---|
| `ZipInputStream` | Duyệt lần lượt những entry khi input có dạng stream, ví dụ upload, và kiểm tra tên **ngay trước khi ghi** |
| `ZipFile` | Mở một file ZIP trên đĩa, liệt kê/tìm entry theo tên và lấy stream đọc entry |
| `ZipOutputStream` | Tạo archive bằng cách thêm nhiều `ZipEntry` |
| `ZipEntry` | Metadata/tên của từng mục trong archive |

Lưu ý `ZipInputStream` làm việc chủ yếu theo các **local entry records**; `ZipFile` có thể tận dụng **central directory**. Một archive chế tạo không nhất quán giữa local headers và central directory có thể có những khác biệt khi được nhiều công cụ phân tích. Đối với input không tin cậy, **xác thực entry thực tế mà extractor sẽ ghi**, không chỉ scan một danh sách tên bằng một parser rồi mặc nhiên tin rằng parser khác sẽ giải nén giống hệt.

### Tại sao đọc data theo chunk?

Code:

~~~java
byte[] buffer = new byte[8192];
for (int n; (n = zip.read(buffer)) != -1; ) {
    // Kiểm tra quota theo n, sau đó ghi n byte.
}
~~~

Đây là đoạn mô tả cấu trúc, không phải loop độc lập. `ZipInputStream.read` trả dữ liệu **đã giải nén của entry hiện tại**. Giá trị `-1` ở đây là hết entry, không có nghĩa đã hết archive. Sau khi xử lý xong, gọi `closeEntry()` rồi `getNextEntry()` để nhận entry tiếp.

**Không dùng `zip.readAllBytes()` cho archive không tin cậy**, vì entry nhỏ trên disk có thể bung thành dữ liệu lớn và gây hết heap.

## 8. Bước 4 — chống Zip Slip: kiểm tra tên trước khi tạo file

Trong code `unpack`, mỗi entry trải qua:

~~~text
getNextEntry()
     |
     v
++entryCount, kiểm tra maxEntries
     |
     v
validateName: length / absolute / slash / backslash / . / .. / colon
     |
     v
root.resolve(name).normalize()
     |
     v
target.startsWith(root) và target != root
     |
     v
Set<Path>.add(target): từ chối duplicate
     |
     v
tạo directory cha an toàn
     |
     +-- directory entry --> tạo thư mục, closeEntry
     |
     +-- file entry -------> CREATE_NEW, copy có byte quota, closeEntry
~~~

### Tại sao không chỉ `Path.isAbsolute`?

Trên Linux, tên `C:\Windows\file.txt` có thể được parse như một tên tương đối có backslash, trong khi trên Windows nó mang nghĩa path khác. Bài dùng **chính sách tên portable**: cấm backslash và colon trước khi resolve, thay vì chỉ dựa vào cách parse path của hệ điều hành hiện tại.

Tương tự, `\\server\share\...` có thể là đường dẫn UNC trên Windows. Chặn backslash giúp giảm một nhóm ambiguity.

### Tại sao cấm dấu `..` ngay cả khi normalize không thoát root?

Ví dụ `docs/../a.txt` sau normalize có thể trở thành `a.txt` và vẫn nằm trong root. Tuy nhiên, chấp nhận traversal components làm chính sách khó kiểm toán, nhất là khi có symlink/logic giải nén khác. Bài chọn **từ chối mọi `..` và `.`** để có một quy tắc nhất quán.

### Vì sao so sánh Path chứ không so chuỗi?

Nếu root là `/var/data/out`, path `/var/data/outside.txt` cũng bắt đầu với chuỗi `"/var/data/out"`. Nhưng `Path.startsWith(root)` xem các path components và không coi `outside.txt` là con của `out`. Dù vậy, Path comparison là một **lớp kiểm tra cú pháp**, không phải giải pháp duy nhất chống symlink.

## 9. Bước 5 — đặt giới hạn theo byte đã giải nén, không chỉ metadata

### Đừng tin `ZipEntry.getSize()`

Một số entry chưa biết kích thước hoặc có metadata không đáng tin, nên `ZipEntry.getSize()` có thể là `-1` hoặc không phản ánh chính xác dữ liệu cần giải nén. Nếu dùng metadata này để cấp phát mảng rồi đọc hết một lần, chương trình có thể bị **zip bomb**.

Ví dụ kiểm tra trong loop:

~~~java
if (n > limits.maxFileBytes() - size
        || n > limits.maxTotalBytes() - total) {
    throw new IOException("Uncompressed byte limit");
}

out.write(buffer, 0, n);
size += n;
total += n;
~~~

Đây là đoạn trích từ chương trình hoàn chỉnh. Hai biến `size`/`total` chỉ tăng sau khi kiểm tra và ghi chunk. Cách so `n > limit - used` tránh overflow do cộng số lớn trước rồi mới kiểm tra, với điều kiện limits dương và used chưa vượt limit.

**Quotas không chỉ là byte:**

- `maxEntries` chặn archive có hàng triệu directory/file entry rỗng.
- `maxName` giảm khả năng tên quá dài.
- `maxDepth` giới hạn tầng thư mục.
- `maxFileBytes` chặn một entry bung quá lớn.
- `maxTotalBytes` chặn nhiều entry nhỏ nhưng tổng lớn.
- Production còn cần **deadline, CPU quota, số inode/file được tạo, disk quota, số thư mục và tổng input compressed bytes**.

Không xem giới hạn tỉ lệ nén (uncompressed/compressed) là hàng rào duy nhất: metadata compressed size có thể không đáng tin hoặc tính toán không phù hợp khi streaming. Luôn kiểm đếm dữ liệu thật.

### Nếu vượt quota sau khi file đã được tạo?

Trong demo, file mới tạo qua `Files.newOutputStream(..., CREATE_NEW, WRITE)` có thể đã được ghi một phần trước khi một chunk vượt quota. `catch` sẽ cố **xóa file hiện tại**.

Nhưng nếu trước đó đã giải nén các entry khác thành công, chúng **có thể vẫn nằm trong thư mục output**. Toàn bộ `unpack` **không phải transaction nguyên tử**. Ở production, dùng staging directory do ứng dụng sở hữu, chỉ publish khi mọi entry đã xử lý và validate xong.

## 10. Thư mục đích và symbolic link: phạm vi an toàn thực tế

### 10.1. Vì sao bắt buộc tạo thư mục đích mới?

`Files.createDirectory(newOutput)` yêu cầu path chưa tồn tại, khác `Files.createDirectories` vốn có thể chấp nhận một cây có sẵn.

Trong bài:

~~~java
Path root = Files.createDirectory(newOutput).toRealPath();
~~~

Lợi ích:

- Không vô tình trộn file từ archive vào dữ liệu cũ.
- Không overwrite tên file có sẵn.
- Không có các subdirectory/symlink được đặt trước bên trong **output mới**, với giả định parent đáng tin cậy và không ai khác được ghi vào cây output.
- Dễ định nghĩa ownership và cleanup nếu extraction thất bại.

Điều kiện tiên quyết: **parent của `newOutput` phải đáng tin**, và actor không tin cậy không có quyền sửa cây output trong lúc giải nén. Demo tự tạo parent bằng `Files.createTempDirectory` để có môi trường luyện tập.

### 10.2. Kiểm tra symlink ở parent components

Khi tạo đường dẫn `root/docs/a.txt`, code `directories` kiểm tra từng component:

~~~java
if (Files.exists(at, LinkOption.NOFOLLOW_LINKS)) {
    if (!Files.isDirectory(at, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Non-directory or symlink parent");
    }
} else {
    Files.createDirectory(at);
}
~~~

Một symlink hiện hữu thường không thỏa `isDirectory(..., NOFOLLOW_LINKS)`, nên bị từ chối thay vì âm thầm theo link để ghi ra ngoài root. `CREATE_NEW` ở file cuối giúp tránh ghi đè file/symlink đã tồn tại.

### 10.3. TOCTOU chưa được giải quyết hoàn toàn

**TOCTOU (Time Of Check To Time Of Use)** là khoảng giữa lúc kiểm tra path và lúc mở/tạo file. Nếu attacker có quyền đổi directory sang symlink trong khoảng đó, kiểm tra Java trước đó có thể không còn đúng.

Do đó **`normalize()`, `NOFOLLOW_LINKS` và `CREATE_NEW` không tự tạo atomic filesystem sandbox**.

Khi threat model cho phép tiến trình khác sửa tree output, có thể cần:

- Tạo/staging dưới parent private với OS permission/ACL hạn chế.
- Dùng descriptor-relative operations, ví dụ `SecureDirectoryStream` nếu filesystem provider hỗ trợ, để giảm những race cụ thể.
- Dùng OS-level isolation/sandbox, container, quyền process tối thiểu.
- Cẩn thận với junction/reparse points trên Windows và cấu trúc path đặc thù provider.
- Không phục hồi symlink/hardlink từ metadata archive nếu chưa xác thực target theo chính sách rất rõ.

**Không mô tả demo là chống mọi Zip Slip/TOCTOU tuyệt đối**. Nó là nền tảng kiểm soát tên/path và quyền ghi trên môi trường đích mới đáng tin.

## 11. Tính di động: Windows, Unicode và tên có thể va chạm

`validateName` cố ý có một policy portable **cơ bản**, nhưng không xử lý mọi tên filesystem đặc biệt.

Các trường hợp cần cân nhắc khi nâng cấp:

1. **Case-insensitive collision:** `docs/A.txt` và `docs/a.txt` có thể cùng đích trên một số filesystem.
2. **Unicode normalization:** Hai tên trông như nhau có thể được chuẩn hóa về cùng đường dẫn.
3. **Windows reserved names:** `CON`, `PRN`, `AUX`, `NUL`, `COM1`... có quy tắc đặc biệt, kể cả một số tên có phần mở rộng.
4. **Trailing dot/space:** Tên `a.txt.` hay `a.txt ` có thể bị normalize/collision theo OS.
5. **Colon/NTFS ADS:** `a.txt:stream` bị policy ví dụ từ chối dấu `:`.
6. **Slash/backslash:** ZIP tên thường dùng `/`; policy không cho phép `\` để tránh hành vi khác nhau giữa OS.
7. **Symlink/junction:** Cần phát hiện cả khi tên hợp lệ về cú pháp.
8. **Tên quá dài tính theo byte:** Giới hạn `String.length()` trong demo là UTF-16 code units; OS còn có thể giới hạn tên/path theo **byte** hoặc representation riêng.

`CREATE_NEW` là một lớp phòng vệ thực tế trước collision của final path, nhưng hệ thống có nhu cầu cross-platform nghiêm ngặt nên có **validator tên filesystem riêng**, test trên các OS mục tiêu.

## 12. Thực hành mở rộng: thêm các negative tests

Bài lab đã có hai negative tests: traversal và byte bomb. Để luyện tập đầy đủ, hãy mở rộng `makeSingleZip` hoặc tạo helper tạo ZIP nhiều entry và thử thêm các trường hợp:

| Test case | Kết quả mong đợi |
|---|---|
| `/tmp/outside.txt` | Từ chối absolute entry |
| `C:/temp/out.txt` | Từ chối dấu colon/drive |
| `docs\..\outside.txt` | Từ chối backslash |
| `docs/../outside.txt` | Từ chối `..` |
| `docs//a.txt` | Từ chối component rỗng |
| Hai entry `same.txt` | Từ chối duplicate |
| `docs/a.txt` rồi `docs/a.txt/b.txt` | Từ chối vì parent là file, không phải directory |
| Hàng trăm entry rỗng | Chặn `maxEntries` |
| Entry quá nhiều tầng | Chặn `maxDepth` |
| Một entry nở lớn | Chặn `maxFileBytes` |
| Nhiều entry tổng nở lớn | Chặn `maxTotalBytes` |
| Trùng tên chỉ khác chữ hoa/thường | Tùy filesystem; không được overwrite |
| Symlink trong cây nguồn | Packer từ chối loại nguồn không hỗ trợ |
| File ZIP/đích thiếu quyền | Ném IOException; đóng stream và cleanup phù hợp |
| Archive bị truncate/CRC lỗi | Phải báo lỗi, không publish output dở |

Các assertions quan trọng không chỉ là “ném exception”:

- **Không file nào được tạo ngoài root**.
- Không file có sẵn bị overwrite.
- File vừa tạo dở bị xóa khi lỗi entry đang xử lý.
- Những file khác trước đó có thể còn lại: test phải **biết rõ giới hạn rollback**.
- Sau lỗi, tài nguyên đã mở qua TWR vẫn được đóng.
- Quota đếm được dựa trên byte **sau giải nén**.
- Với test ngắt việc pack giữa chừng, file ZIP mới tạo phải được cleanup.

### Dùng unit test assertion thay vì dựa vào log

Một JUnit 5 test có thể viết theo ý tưởng:

~~~java
@Test
void rejectsZipSlip() throws Exception {
    Path archive = createArchiveWithEntry(
            "../escape.txt", new byte[]{1});
    Path root = newTemporaryDestination();

    assertThrows(
            IOException.class,
            () -> ArchivePracticeDemo.unpack(
                    archive, root, TEST_LIMITS));

    assertFalse(Files.exists(
            root.getParent().resolve("escape.txt")));
}
~~~

Đây là **pseudocode JUnit 5**, không compile độc lập: `createArchiveWithEntry`, `newTemporaryDestination`, `TEST_LIMITS` là fixtures do test project định nghĩa. `root` phải **chưa tồn tại** khi truyền cho `unpack`. Chương trình đầy đủ mục 6 không yêu cầu JUnit và đã tạo testcase tương đương.

## 13. Khi nào cần ZipFile thay ZipInputStream?

Đối với file ZIP đã nằm trên disk, `ZipFile` có thể tra cứu entry theo tên:

~~~java
try (ZipFile zip = new ZipFile(
        archivePath.toFile(), StandardCharsets.UTF_8)) {

    ZipEntry entry = zip.getEntry("docs/a.txt");
    if (entry != null) {
        try (InputStream in = zip.getInputStream(entry)) {
            // Đọc nội dung entry này và giới hạn bytes như phần unpack.
        }
    }
}
~~~

Đây là mã minh họa, cần `Path archivePath` và các import. `ZipFile` có thể dùng central directory; còn `ZipInputStream` tiện cho upload/stream. **Không đổi sang ZipFile rồi bỏ kiểm tra đường dẫn, quota và ownership**.

Nếu archive mang **ZIP encryption/password**, Java `java.util.zip` không cung cấp một API tổng quát giải mã mọi biến thể ZIP có mật khẩu. Với ZIP features ngoài phạm vi JDK, cần thư viện được đánh giá phù hợp. Đừng coi exception “unsupported method” là tín hiệu để vô hiệu hóa path validation.

## 14. Cấu trúc production: staging rồi mới publish

Dùng hai giai đoạn:

~~~text
archive.zip
    |
    v
Private staging directory (new)
    |
    +-- từng entry: validate path, quota, no-follow, CREATE_NEW
    |
    +-- kiểm tra toàn archive / format / checksum / malware policy nếu cần
    |
    v
Publish sang destination cuối theo policy
    |
    v
Cleanup staging khi lỗi
~~~

Trong production, lý tưởng cần:

1. Đặt staging cùng filesystem với destination nếu muốn có khả năng rename atomic theo điều kiện filesystem cho phép.
2. Không cho người dùng khác ghi vào staging hoặc thay directory/symlink.
3. Giới hạn compressed input bytes, uncompressed bytes, entry count, depth, CPU time và số inode.
4. Không mở các entry dạng executable/script nhạy cảm nếu product không cần.
5. Kiểm tra tên filesystem reserved/collision trên OS thực tế.
6. Làm rõ merge/overwrite policy; demo chỉ hỗ trợ **new destination**.
7. Khi publish, kiểm tra hỗ trợ `StandardCopyOption.ATOMIC_MOVE` nếu muốn atomic move; có thể có `AtomicMoveNotSupportedException` và thao tác đổi tên không là transaction trên nhiều filesystem.
8. Cleanup cả staging khi fail giữa chừng và cleanup orphan staging sau crash.
9. Đối với nội dung nhạy cảm, CRC32 ZIP chỉ phát hiện hỏng ngẫu nhiên, **không phải chữ ký số chống giả mạo**.
10. Ghi log lỗi đủ phân biệt “invalid path”, “quota exceeded”, “archive corrupted”, “permission denied”, “I/O interrupted” mà không làm lộ path hệ thống ngoài mong muốn.

**Đừng trả về thành công chỉ vì `unpack` đã tạo vài file.** Thành công phải nghĩa là mọi entry được đọc, validate, trích xuất, các stream được đóng thành công và output được publish theo hợp đồng.

## 15. Các sai lầm dễ mắc

1. **Ghi thẳng `entry.getName()` vào thư mục output mà không kiểm tra.** Dẫn tới Zip Slip.
2. **Chỉ dùng `String.startsWith` để kiểm tra containment.** Có thể nhầm đường dẫn có cùng tiền tố chuỗi.
3. **Tin `Path.normalize()` giải quyết symlink.** Sai: normalize chỉ xử lý cú pháp.
4. **Tin `NOFOLLOW_LINKS` loại được mọi race.** Sai nếu attacker có thể sửa cây file đồng thời.
5. **Dùng `CREATE`/`TRUNCATE_EXISTING` với entry không tin cậy.** Có thể ghi đè file cũ; dùng `CREATE_NEW` khi không cho overwrite.
6. **Giải nén vào directory đã có nội dung mà không có merge policy.** Dễ gây collision/overwrite.
7. **Bỏ qua directory entries.** Mất thư mục rỗng.
8. **Tin mọi ZIP đều có directory entry `docs/` trước `docs/a.txt`.** Không được bảo đảm; tự tạo parent sau khi validate.
9. **Không giới hạn số entry.** Archive hàng triệu file rỗng vẫn phá hệ thống dù tổng bytes thấp.
10. **Tin `entry.getSize()` để cấp phát mảng.** Có thể -1 hoặc không đáng tin.
11. **Dùng `zip.readAllBytes()` cho upload không tin cậy.** Dễ hết heap khi zip bomb.
12. **Chỉ giới hạn dung lượng ZIP nén.** Một file vài KiB có thể bung rất lớn.
13. **Quên `ZipOutputStream.close()`.** Central directory có thể chưa hoàn tất.
14. **Hiểu `ZipInputStream.read() == -1` là hết archive.** Đó là hết entry hiện tại.
15. **Để output ZIP nằm trong source tree đang walk.** Dễ có input biến động/tự chứa ZIP.
16. **Follow symlink nguồn không kiểm soát.** Có thể pack tài liệu ngoài thư mục đã định.
17. **Dùng dấu backslash trong entry theo cách tự tiện.** Hành vi khác nhau giữa hệ điều hành.
18. **Không kiểm tra duplicate/case-collision.** Có thể mất hoặc ghi đè file.
19. **Cho rằng IOException tự rollback mọi file đã tạo trước đó.** Sai: cần staging/cleanup.
20. **Cho rằng CRC32 chứng minh archive đáng tin.** Sai: checksum không có xác thực mật mã.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Tại sao packer phải viết directory entries như `empty/`?

**Đáp án:** Để giữ thư mục rỗng. Nếu ZIP chỉ có file entries, một thư mục không có file con có thể biến mất khi giải nén.

**Câu 2.** Vì sao chỉ kiểm tra `zipEntry.getSize() <= maxBytes` không đủ chống zip bomb?

**Đáp án:** Metadata có thể thiếu/không đáng tin, và tổng nhiều entry vẫn lớn. Cần kiểm đếm byte đã giải nén thật trong mỗi vòng `read` trước khi ghi.

**Câu 3.** Nếu ZIP chứa `docs/a.txt` nhưng không có `docs/`, extractor có tạo được không?

**Đáp án:** Có. Sau khi tên được validate và nằm trong root, tạo các thư mục cha an toàn rồi tạo file mới.

**Câu 4.** Vì sao `Path.startsWith(root)` tốt hơn `String.startsWith(root.toString())`?

**Đáp án:** Nó so sánh theo thành phần Path, tránh nhầm `/safe/output2` là con của `/safe/output`.

**Câu 5.** `Files.newOutputStream(..., CREATE_NEW)` giải quyết được vấn đề gì?

**Đáp án:** Ngăn ghi đè/truncate một file hoặc symlink có sẵn ở path cuối; không tự ngăn attacker đổi directory cha khi có race.

**Câu 6.** Vì sao thử `../escape.txt` phải kiểm tra cả exception và file thực tế?

**Đáp án:** Có exception không chứng minh file ngoài root chưa từng được tạo. Assertion cần xác nhận không có file vi phạm sau thao tác thất bại.

**Câu 7.** Nếu entry thứ mười vượt quota nhưng chín entry trước hợp lệ đã được giải nén, các file trước có tự biến mất không?

**Đáp án:** Không. Demo chỉ cố dọn file entry đang xử lý; cần staging + cleanup/publish riêng để đạt quy trình trọn gói.

**Câu 8.** Vì sao Java `ZipInputStream` không nên được dùng với một luồng GZIP đơn `.gz`?

**Đáp án:** ZIP archive có `ZipEntry` và cấu trúc ZIP, còn GZIP là định dạng nén stream khác; dù cả hai có thể dùng DEFLATE, header/container không giống nhau.

**Câu 9.** Chương trình được chạy trên Linux nhưng archive có tên `C:/tmp/a.txt`. Vì sao chính sách của bài vẫn từ chối?

**Đáp án:** Để giữ tính portable và tránh tên chuyển nghĩa thành đường dẫn drive khi archive được giải nén trên Windows hoặc qua tool khác.

### Tài liệu Java 17 chính thức

- [ZipOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipOutputStream.html)
- [ZipInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipInputStream.html)
- [ZipEntry](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipEntry.html)
- [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html)
- [Files.walkFileTree và Files.newOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [Path.resolve, normalize, startsWith](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html)
- [LinkOption.NOFOLLOW_LINKS](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html)
- [StandardOpenOption.CREATE_NEW](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html)
- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-011">Minh họa serialization và cấu hình ObjectInputFilter</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành Java I/O: Serialization và cấu hình ObjectInputFilter

## Mục tiêu

Sau bài thực hành, bạn có thể: (1) tuần tự hóa đối tượng Java thành luồng byte và khôi phục lại đối tượng; (2) hiểu vai trò của Serializable, serialVersionUID và transient; (3) cấu hình ObjectInputFilter để giới hạn kiểu đối tượng và quy mô đồ thị được giải tuần tự hóa; (4) phân biệt luồng hợp lệ, luồng bị bộ lọc từ chối và luồng hỏng; (5) biết những giới hạn bảo mật của Java serialization.

**Bối cảnh:** Đây là bài thực hành trong Java Core I/O. Dùng JDK 17 trở lên, không cần framework hay thư viện ngoài JDK.

## 1. Cơ chế cần hiểu trước khi chạy

**Serialization (tuần tự hóa)** chuyển trạng thái đủ điều kiện của một đối tượng thành dạng byte bằng ObjectOutputStream. **Deserialization (giải tuần tự hóa)** dùng ObjectInputStream để đọc luồng byte và tái dựng đồ thị đối tượng. Cùng một luồng có thể chứa nhiều đối tượng; các tham chiếu dùng chung và một số quan hệ vòng được giao thức serialization quản lý.

Luồng xử lý trong ví dụ:

~~~text
Account("Lan", token="SECRET")
  -> ObjectOutputStream.writeObject(...)
  -> byte[] chứa Java serialization stream
  -> ByteArrayInputStream
  -> ObjectInputStream + ObjectInputFilter
  -> readObject()
      -> ALLOWED  : Account được đọc, token transient thành null
      -> REJECTED : lớp không nằm trong allowlist bị InvalidClassException
~~~

Các điểm cần nhớ:

- Một lớp tự định nghĩa phải triển khai Serializable để được ghi theo cơ chế mặc định; đây là interface đánh dấu, không chứa phương thức cần override. Đối tượng trong các trường không transient cũng phải có khả năng được serialize nếu chúng được duyệt đến; nếu không có thể phát sinh NotSerializableException.
- ObjectOutputStream ghi một định dạng nhị phân đặc thù của Java. ObjectInputStream đọc lại định dạng này; không thể coi nó là JSON, văn bản UTF-8 hay cách trao đổi dữ liệu đa ngôn ngữ.
- serialVersionUID xác định phiên bản lớp phục vụ kiểm tra tương thích khi đọc. Tự khai báo giá trị ổn định giúp kiểm soát thay đổi lớp, nhưng **không** bảo đảm mọi thay đổi schema đều tương thích.
- Trường instance được đánh dấu transient không được serialize theo cơ chế mặc định; khi đọc lại nó nhận giá trị mặc định của kiểu (ví dụ null đối với String). Trường static cũng không thuộc trạng thái instance được serialize.
- ObjectInputFilter được gọi **trong quá trình deserialization** để xét lớp, kích thước mảng, độ sâu đồ thị, số tham chiếu và số byte đã đọc. Nó không được áp dụng khi chỉ gọi writeObject và không phải công cụ mã hóa hay xác thực dữ liệu.

## 2. Chương trình đầy đủ: chấp nhận đúng lớp, từ chối lớp khác

Tạo một tệp duy nhất mang tên **SerializationFilterDemo.java** với nội dung dưới đây. Hai lớp dữ liệu được khai báo lồng bên trong lớp main để toàn bộ bài chạy bằng một tệp.

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

public class SerializationFilterDemo {

    private static final class Account implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String owner;
        private final transient String sessionToken;

        Account(String owner, String sessionToken) {
            this.owner = owner;
            this.sessionToken = sessionToken;
        }

        @Override
        public String toString() {
            return "Account[owner=" + owner
                    + ", sessionToken=" + sessionToken + "]";
        }
    }

    private static final class Unexpected implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String label;

        Unexpected(String label) {
            this.label = label;
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(value);
        }
        return buffer.toByteArray();
    }

    private static Object deserializeWithFilter(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes))) {

            String pattern = "maxdepth=5;maxrefs=20;maxbytes=4096;"
                    + "maxarray=100;SerializationFilterDemo$Account;!*";
            ObjectInputFilter filter =
                    ObjectInputFilter.Config.createFilter(pattern);

            // Thiết lập filter trước lệnh readObject() đầu tiên.
            in.setObjectInputFilter(filter);
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] acceptedBytes =
                serialize(new Account("Lan", "SECRET"));
        Object accepted = deserializeWithFilter(acceptedBytes);
        System.out.println("Accepted: " + accepted);

        byte[] rejectedBytes =
                serialize(new Unexpected("not allowed"));
        try {
            deserializeWithFilter(rejectedBytes);
            System.out.println("Unexpected object accepted");
        } catch (InvalidClassException rejected) {
            System.out.println("Rejected by ObjectInputFilter");
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac SerializationFilterDemo.java
java SerializationFilterDemo
~~~

Kết quả mong đợi với JDK 17+ mặc định:

~~~text
Accepted: Account[owner=Lan, sessionToken=null]
Rejected by ObjectInputFilter
~~~

**Giải thích kết quả:**

1. serialize(...) tạo ByteArrayOutputStream, bọc bằng ObjectOutputStream rồi writeObject(value). Kết quả byte[] là dữ liệu thật đã tuần tự hóa, chưa được giải mã và không phải chuỗi ký tự.
2. deserializeWithFilter(...) tạo ObjectInputStream, cài bộ lọc bằng setObjectInputFilter(...) **trước khi** gọi readObject().
3. Lớp Account được allowlist nên được tái dựng. owner có giá trị "Lan"; sessionToken là transient nên nhận null, dù trước lúc ghi mang giá trị "SECRET".
4. Luồng Unexpected vẫn được serialize thành công vì Unexpected implements Serializable; khi giải tuần tự hóa, bộ lọc gặp lớp Unexpected, quy tắc cuối !* từ chối, ObjectInputStream phát sinh InvalidClassException.
5. Code chỉ bắt InvalidClassException cho trường hợp bộ lọc từ chối. Trong ứng dụng thực, IOException, ClassNotFoundException và lỗi dữ liệu khác phải được xử lý riêng theo ngữ cảnh; không quy mọi lỗi readObject thành lỗi lọc.

**Lưu ý về lớp lồng:** tên nhị phân của Account là SerializationFilterDemo$Account, không phải SerializationFilterDemo.Account. Vì vậy chuỗi pattern phải sử dụng dấu $. Nếu chuyển Account sang package com.example.io với lớp top-level, pattern cần dùng đúng tên đầy đủ com.example.io.Account.

## 3. Phân tích ObjectInputFilter từng bước

ObjectInputFilter là một interface được JDK cung cấp để tham gia quyết định khi ObjectInputStream tạo/đọc đối tượng từ luồng. Nó kiểm tra thông tin của đối tượng đang được đọc qua FilterInfo:

| Thuộc tính FilterInfo | Ý nghĩa | Cách dùng |
| --- | --- | --- |
| serialClass() | Class đang được kiểm tra; đôi lúc null ở các lần kiểm tra không gắn với lớp mới | Chỉ quyết định theo class khi khác null |
| arrayLength() | Độ dài mảng nếu đang xét mảng; thường -1 nếu không phải mảng | Chặn mảng quá lớn |
| depth() | Độ sâu hiện tại của đồ thị đối tượng | Hạn chế đồ thị lồng quá sâu |
| references() | Số tham chiếu mà luồng đã xử lý | Hạn chế số đối tượng/tham chiếu |
| streamBytes() | Lượng byte đã tiêu thụ tại thời điểm kiểm tra | Hạn chế dung lượng đã đọc |

Bộ lọc trả về một trong ba giá trị:

- **ALLOWED**: cho phép đối tượng/lớp đang xét đi tiếp.
- **REJECTED**: từ chối; quá trình giải tuần tự hóa sẽ thất bại (thường dưới dạng InvalidClassException).
- **UNDECIDED**: bộ lọc chưa quyết định. Giá trị này **không mặc nhiên có nghĩa là cấm**; nếu các bộ lọc khác hoặc chính sách hiệu lực không từ chối, việc đọc có thể vẫn tiếp diễn.

Mẫu cài đặt bằng pattern trong chương trình:

~~~text
maxdepth=5;maxrefs=20;maxbytes=4096;maxarray=100;SerializationFilterDemo$Account;!*
~~~

Bốn giới hạn được kiểm tra trước các luật lớp:

| Thành phần | Vai trò |
| --- | --- |
| maxdepth=5 | Từ chối khi độ sâu vượt 5 |
| maxrefs=20 | Từ chối khi số tham chiếu vượt 20 |
| maxbytes=4096 | Từ chối khi số byte đã đọc vượt 4.096 |
| maxarray=100 | Từ chối khi độ dài một mảng vượt 100 |
| SerializationFilterDemo$Account | Cho phép lớp Account trong chương trình |
| !* | Từ chối tất cả lớp còn lại phù hợp ký tự đại diện * |

**Thứ tự luật lớp quan trọng.** Với cùng một lớp, mẫu khớp đầu tiên quyết định. Nếu đặt !* ở đầu, Account cũng bị từ chối. Các giới hạn maxdepth/maxrefs/maxbytes/maxarray được kiểm tra độc lập với vị trí luật lớp. Ký tự khoảng trắng trong pattern là có ý nghĩa; không tự ý viết khoảng trắng quanh dấu chấm phẩy hoặc tên lớp.

Trong ví dụ, whitelist hẹp phù hợp vì đồ thị Account chỉ chứa trường String và các giá trị đơn giản. Lời gọi bộ lọc không áp dụng cho các primitive hoặc String được mã hóa trực tiếp theo giao thức serialization; vì vậy **đừng suy diễn rằng whitelist lớp đủ kiểm soát mọi dữ liệu của mọi kiểu**. Khi mô hình có List, Map, mảng hay lớp lồng, phải kiểm tra cả những lớp thực sự xuất hiện trong luồng, các dạng nội bộ và tài nguyên có thể được phân bổ.

## 4. Thử nghiệm có kiểm soát

### 4.1. Thấy tác dụng của transient

Trong chương trình, đổi giá trị khi tạo đối tượng:

~~~java
serialize(new Account("Minh", "TOKEN-123"))
~~~

Khi chạy lại, phần Account của output sẽ cho thấy owner=Minh và sessionToken=null. transient giúp không lưu **trường đó** trong luồng theo cơ chế mặc định; nó không xóa dữ liệu đang nằm ở nơi khác, và các phương thức writeObject tùy biến có thể thay đổi dữ liệu được ghi. Tuyệt đối không dùng transient làm biện pháp bảo vệ duy nhất cho bí mật.

### 4.2. Chặn cả Account bằng chính sách lớp

Thay pattern thành:

~~~java
String pattern = "maxdepth=5;maxrefs=20;maxbytes=4096;"
        + "maxarray=100;!*";
~~~

Lúc này ngay cả Account cũng bị từ chối. Trong chương trình hiện tại, lệnh deserializeWithFilter(acceptedBytes) đầu tiên sẽ phát sinh InvalidClassException trước khi tới phần catch dành cho Unexpected. Để quan sát thông báo thân thiện, bắt exception cho nhánh đầu hoặc hoàn nguyên pattern sau thí nghiệm.

### 4.3. Thử giới hạn kích thước mảng

Ví dụ độc lập về một mảng 101 phần tử:

~~~java
byte[] arrayBytes = serialize(new int[101]);
~~~

Để thử mảng này với cùng hàm deserializeWithFilter(...), có thể truyền arrayBytes; maxarray=100 sẽ chặn mảng quá dài. Nhưng hàm hiện dùng quy tắc !* nên mảng cũng có thể bị chặn bởi luật lớp, kể cả khi chưa vượt giới hạn. Để kiểm tra **riêng** maxarray, dùng một filter minh họa như:

~~~java
ObjectInputFilter arrayFilter =
        ObjectInputFilter.Config.createFilter("maxarray=100;*");
~~~

Cài arrayFilter vào ObjectInputStream mới **trước readObject()**, sau đó đọc các luồng int[100] và int[101]. Với JDK 17 mặc định, mảng 100 phần tử được chấp nhận và mảng 101 phần tử bị từ chối. Mẫu * cho phép mọi lớp, nên **chỉ dùng trong thí nghiệm giới hạn mảng**, không dùng làm chính sách bảo vệ dữ liệu không tin cậy.

### 4.4. Đổi ngưỡng bytes và depth

Giảm maxbytes từ 4096 xuống một giá trị nhỏ như 16 thường khiến luồng Account trong ví dụ bị từ chối trước khi khôi phục thành công. Tuy nhiên, giá trị bytes tại từng lần callback phụ thuộc thời điểm đọc; không coi con số ngưỡng này là độ dài tuyệt đối của mảng byte được serialize. Tương tự, với một đồ thị lồng nhiều lớp, giảm maxdepth có thể chặn việc đọc sâu. Không dùng một ngưỡng demo cố định cho mọi mô hình dữ liệu.

### 4.5. Phân biệt các nguyên nhân thất bại

| Hiện tượng | Nguyên nhân thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| InvalidClassException với thông điệp filter status | Bộ lọc từ chối một lớp hay giới hạn | Kiểm tra allowlist, giới hạn, thứ tự luật |
| InvalidClassException do serialVersionUID | Phiên bản lớp phía đọc và phía ghi không tương thích | Kiểm tra serialVersionUID và thay đổi schema |
| NotSerializableException khi ghi | Một đối tượng trong đồ thị không serializable | Kiểm tra các trường và kiểu liên quan |
| StreamCorruptedException / EOFException khi đọc | Luồng sai định dạng, thiếu hoặc bị cắt | Xác nhận đầu vào đúng Java serialized bytes |
| ClassNotFoundException khi đọc | Bên đọc thiếu lớp có tên trong luồng | Kiểm tra classpath/module và triển khai ứng dụng |

Một lỗi có thể được bao bọc bởi exception ở tầng gọi. Không nên phân loại chỉ dựa vào tên exception mà không xem thông điệp, cause và trạng thái chương trình.

## 5. Hai cách cấu hình ObjectInputFilter

### 5.1. Theo từng ObjectInputStream: phù hợp với bài thực hành

~~~java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "maxdepth=10;maxrefs=100;maxbytes=65536;maxarray=1000;"
                + "com.example.io.SafeMessage;!*");
try (ObjectInputStream in = new ObjectInputStream(inputStream)) {
    in.setObjectInputFilter(filter);
    Object value = in.readObject();
}
~~~

Đoạn trên chỉ minh họa phần đọc bên trong ứng dụng đã có inputStream, cùng lớp com.example.io.SafeMessage thật sự tồn tại. Nó **không phải chương trình độc lập**. Pattern cụ thể cần được xây dựng theo đồ thị đối tượng thực tế. Thiết lập filter trước readObject, close stream bằng try-with-resources và kiểm tra kiểu dữ liệu sau khi đọc.

### 5.2. Bộ lọc JVM-wide và filter factory

JDK hỗ trợ bộ lọc toàn JVM thông qua system property jdk.serialFilter hoặc security property tương ứng; ví dụ khởi chạy ứng dụng với giới hạn tổng quát:

~~~bash
java -Djdk.serialFilter=maxdepth=20;maxrefs=200;maxbytes=1048576 SerializationFilterDemo
~~~

**Chú ý shell:** dấu ; thường là ký tự đặc biệt của shell (đặc biệt trong PowerShell/Bash). Đặt toàn bộ tham số JVM trong dấu nháy để truyền nguyên giá trị:

~~~bash
java "-Djdk.serialFilter=maxdepth=20;maxrefs=200;maxbytes=1048576" SerializationFilterDemo
~~~

Các cấu hình này có phạm vi áp dụng rộng, vì vậy phải đánh giá ảnh hưởng tới tất cả luồng đọc đối tượng trong ứng dụng. Từ JDK 17, filter factory có thể kết hợp bộ lọc toàn JVM với bộ lọc trên từng stream; cách kết hợp thực tế phụ thuộc cấu hình factory. Không mặc định rằng setObjectInputFilter luôn ghi đè hoàn toàn một chính sách JVM-wide đã cài đặt. Để phân tích hành vi, kiểm tra tài liệu của đúng JDK và cấu hình runtime đang chạy.

**Nguyên tắc:** policy ứng dụng phải có allowlist và giới hạn tài nguyên phù hợp với từng luồng dữ liệu; policy JVM-wide là lớp hỗ trợ, không thay thế việc xác định mô hình dữ liệu mong đợi của từng điểm nhận input.

## 6. Khi cần filter viết bằng Java thay cho pattern

Pattern tiện cho luật theo tên lớp và giới hạn cơ bản. Nếu cần điều kiện ngữ cảnh, triển khai trực tiếp ObjectInputFilter. Đây là **đoạn minh họa** áp dụng cho lớp SafeMessage ở package com.example.io:

~~~java
ObjectInputFilter customFilter = info -> {
    if (info.depth() > 10
            || info.references() > 100
            || info.streamBytes() > 65_536
            || info.arrayLength() > 1_000) {
        return ObjectInputFilter.Status.REJECTED;
    }

    Class<?> type = info.serialClass();
    if (type == null) {
        return ObjectInputFilter.Status.UNDECIDED;
    }

    while (type.isArray()) {
        type = type.getComponentType();
    }

    if (type == com.example.io.SafeMessage.class) {
        return ObjectInputFilter.Status.ALLOWED;
    }
    return ObjectInputFilter.Status.REJECTED;
};
~~~

Ở đây, biểu thức arrayLength() > 1_000 không chặn trường hợp -1 (không phải mảng). serialClass() == null có thể xuất hiện trong các callback chỉ dùng để cập nhật giới hạn; trả UNDECIDED sau khi kiểm tra giới hạn là phù hợp trong nhánh này. Khi type khác null và không được phép, trả REJECTED để tránh khoảng trống allowlist.

Đây là mã minh họa logic lọc, cần lớp com.example.io.SafeMessage được khai báo trong dự án. Nếu kiểu hợp lệ chứa các phần tử bổ trợ (chẳng hạn một số collection, enum, mảng hoặc lớp lồng), phải lập allowlist đầy đủ trên cơ sở luồng thật và giới hạn tài nguyên; nếu không, chính dữ liệu hợp lệ sẽ bị từ chối.

## 7. Những hiểu lầm và lỗi phổ biến

1. **Chỉ kiểm tra instanceof sau readObject().** Quá muộn cho một số rủi ro: Java đã tiến hành giải tuần tự hóa trước khi phép kiểm tra kiểu trả về diễn ra. Đặt ObjectInputFilter trước readObject để từ chối sớm hơn trong phạm vi filter hỗ trợ.
2. **Cho rằng implements Serializable là dấu hiệu an toàn.** Interface này chỉ cho biết đối tượng có thể tham gia serialization, không khẳng định nguồn byte đáng tin hay nội dung hợp lệ.
3. **Dùng filter chỉ với maxdepth nhưng không giới hạn lớp.** Một policy tốt cần xét cả kiểu, kích thước mảng, số tham chiếu và byte; tùy trường hợp còn cần giới hạn ở tầng truyền tải trước khi tạo ObjectInputStream.
4. **Cho rằng UNDECIDED nghĩa là REJECTED.** Khi dùng rule pattern mà không có luật loại trừ cuối cùng, các lớp không khớp có thể vẫn được đọc.
5. **Dùng tên lớp sai.** Nested class sử dụng dấu $ trong tên nhị phân; package và tên module (nếu ghi trong pattern) phải chính xác.
6. **Viết !* trước allowlist.** Bộ lọc xét pattern theo thứ tự nên rule từ chối chung đặt trước sẽ che hết các rule cho phép phía sau.
7. **Tin rằng bộ lọc đảm bảo an toàn tuyệt đối.** Filter giảm bề mặt tấn công nhưng không kiểm chứng chữ ký, không ngăn mọi hành vi trong custom readObject/readResolve và không sửa lỗi trong thư viện. Dữ liệu không tin cậy cần được xem là nguồn rủi ro, kể cả khi có filter.
8. **Dùng serialize làm cách lưu dữ liệu ổn định lâu dài.** Thay đổi tên lớp, serialVersionUID, trường và classpath có thể gây mất tương thích. Với dữ liệu trao đổi lâu dài, cân nhắc schema rõ ràng như JSON/Protocol Buffers cùng validate, giới hạn kích thước và xác thực phù hợp.
9. **Log hoặc công khai bí mật trong đối tượng.** Dù trường transient không được ghi mặc định, việc in toString() trong ứng dụng có thể tiết lộ thông tin hiện có. Chỉ hiện giá trị demo phi nhạy cảm.

## 8. Checklist tự kiểm tra và câu hỏi mở rộng

Tự chạy lại chương trình, sau đó xác nhận:

- Tệp có tên SerializationFilterDemo.java, lệnh javac biên dịch thành công trên JDK 17+.
- ObjectOutputStream ghi được cả Account và Unexpected.
- Account được khôi phục khi policy cho phép; owner vẫn là Lan và sessionToken thành null.
- Unexpected bị từ chối khi readObject() dưới policy chứa allowlist Account và !*.
- Đổi thứ tự !* lên trước khiến Account cũng bị chặn.
- Bạn có thể giải thích maxdepth, maxrefs, maxbytes, maxarray và sự khác nhau giữa ALLOWED / REJECTED / UNDECIDED.
- Các thao tác đọc đối tượng từ nguồn không tin cậy trong ứng dụng đều có policy thích hợp, với giới hạn đầu vào ở lớp truyền tải và xử lý lỗi riêng.

**Câu hỏi suy ngẫm:** (1) Vì sao ép kiểu Account sau readObject không thay thế được ObjectInputFilter? (2) Vì sao trường transient trở thành null và khi nào cần custom serialization? (3) Bộ lọc theo tên lớp có thể thiếu những lớp hợp lệ nào khi Account chứa List hoặc mảng? (4) Vì sao maxbytes và maxrefs giúp giảm rủi ro tiêu thụ tài nguyên nhưng không chứng minh luồng đáng tin?

## Tài liệu tham khảo chính thức

- Oracle JDK 17 API — Serializable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Serializable.html
- Oracle JDK 17 API — ObjectInputStream: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputStream.html
- Oracle JDK 17 API — ObjectInputFilter: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html
- Oracle JDK 17 API — ObjectInputFilter.Config và cú pháp pattern: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.Config.html
- OpenJDK JEP 290 — Filter Incoming Serialization Data: https://openjdk.org/jeps/290
- OpenJDK JEP 415 — Context-Specific Deserialization Filters: https://openjdk.org/jeps/415


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-012">Hoàn thiện ví dụ tích hợp với kiểm thử các nhánh thất bại</a>

<details>
<summary>Xem chi tiết</summary>

# Hoàn thiện ví dụ tích hợp Java I/O và kiểm thử các nhánh thất bại

## 1. Mục tiêu và tiêu chí hoàn thành

Bài thực hành tích hợp các thành phần đã học của Java I/O/NIO.2 thành một luồng xử lý có thể chạy và kiểm tra được: **đọc byte → giải mã UTF-8 → đọc theo dòng → xác thực dữ liệu → tính toán → ghi tệp tạm → đưa kết quả vào vị trí đích**. Ngoài luồng thành công, mỗi điểm có khả năng thất bại đều phải có một kiểm thử cho thấy lỗi được phát hiện, tài nguyên được đóng và kết quả hợp lệ cũ được giữ nguyên khi chưa tới bước hoàn tất.

Sau bài này, bạn cần giải thích được sự khác biệt giữa lỗi định dạng dữ liệu và lỗi I/O, vai trò của `try-with-resources`, lý do không ghi trực tiếp đè lên báo cáo cũ, cách tạo lỗi giả lập có kiểm soát và cách kiểm thử **cả ngoại lệ lẫn trạng thái tệp sau ngoại lệ**.

**Đề bài mẫu:** Đọc tệp UTF-8 `orders.csv` có hai cột `id,quantity`, tính số bản ghi và tổng số lượng, xuất `report.txt` chứa `records=...` và `totalQuantity=...`. Bài dùng **CSV đơn giản có chính xác một dấu phẩy mỗi dòng**, không hỗ trợ trường trong dấu nháy hoặc dấu phẩy nằm trong giá trị; hệ thống CSV thực tế cần parser chuyên dụng.

Quy ước dữ liệu: dòng đầu tiên phải chính xác `id,quantity` (không BOM); mỗi dòng tiếp theo phải có ID khớp `[A-Za-z][A-Za-z0-9_-]{0,31}`, ID không trùng, `quantity` là số nguyên `long` dương. Dòng trống, số không hợp lệ, số âm/0, ID trùng, dữ liệu UTF-8 lỗi hoặc tổng bị tràn đều làm thao tác thất bại. Tệp chỉ có header là hợp lệ và tạo báo cáo 0 bản ghi, tổng 0.

Ví dụ đầu vào:

```csv
id,quantity
apple,2
banana,7
```

Kết quả nội dung `report.txt`:

```text
records=2
totalQuantity=9
```

## 2. Thiết kế luồng và ranh giới trách nhiệm

1. `generate` nhận `Path` đầu vào/đầu ra, không cho dùng cùng một tệp và điều phối hai pha.
2. `parse` sử dụng `Files.newInputStream`, `InputStreamReader` với bộ giải mã UTF-8 ở chế độ `REPORT`, rồi `BufferedReader` để xử lý tuần tự. Toàn bộ phân tích và kiểm tra diễn ra **trước khi tạo báo cáo mới**.
3. `writeSafely` viết kết quả ra tệp tạm trong **cùng thư mục** với đích, đóng writer trước khi di chuyển; dùng `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` để yêu cầu đổi tên nguyên tử nếu hệ thống tệp hỗ trợ. Không hỗ trợ atomic move thì trả lỗi rõ ràng, không âm thầm chuyển sang thao tác kém bảo đảm hơn.
4. `finally` xóa tệp tạm nếu nó còn tồn tại. `BeforeCommit` là điểm chèn lỗi dành cho kiểm thử, giúp ép luồng lỗi ngay sau khi ghi tệp tạm mà không lệ thuộc vào quyền tệp/hệ điều hành.
5. Bộ kiểm thử dùng thư mục tạm độc lập cho mỗi ca và xóa sau khi chạy. Assertions xác minh giá trị đầu ra, **loại lỗi mong đợi** và tác động lên tệp cũ/tệp tạm.

Trong ví dụ đơn luồng này, “an toàn” có nghĩa là **không cố ý thay báo cáo cũ trước khi dữ liệu mới được đọc, kiểm tra và ghi xong**; tính nguyên tử của bước đổi tên phải được hệ thống tệp bảo đảm. Đây không phải cơ chế giao dịch nhiều tệp, chống ghi đồng thời hoặc bảo đảm dữ liệu đã được đồng bộ vật lý xuống thiết bị lưu trữ.

## 3. Mã hoàn chỉnh: Java 17 trở lên

Lưu toàn bộ chương trình bên dưới trong **`IntegratedIoDemo.java`** (một tệp, không dùng thư viện bên thứ ba). `Summary` dùng `record`, các hàm kiểm thử nằm cùng lớp để chạy trực tiếp bằng JDK.

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class IntegratedIoDemo {
    record Summary(int records, long totalQuantity) {}

    static final class DataFormatException extends IOException {
        DataFormatException(String message) { super(message); }
        DataFormatException(String message, Throwable cause) { super(message, cause); }
    }

    @FunctionalInterface
    interface BeforeCommit {
        void run(Path temporaryFile) throws IOException;
    }

    static void generate(Path input, Path output) throws IOException {
        generate(input, output, temporaryFile -> {});
    }

    static void generate(Path input, Path output, BeforeCommit beforeCommit)
            throws IOException {
        Path target = output.toAbsolutePath().normalize();
        if (Files.exists(input) && Files.exists(target)
                && Files.isSameFile(input, target)) {
            throw new IOException("Input and output must be different files");
        }
        Summary summary = parse(input);
        writeSafely(target, summary, beforeCommit);
    }

    private static Summary parse(Path input) throws IOException {
        Set<String> seenIds = new HashSet<>();
        int records = 0;
        long total = 0;

        // Stream bytes -> strict UTF-8 decoding -> buffered text lines.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(input),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            if (!"id,quantity".equals(reader.readLine())) {
                throw new DataFormatException("Line 1: expected id,quantity header");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] fields = line.split(",", -1);
                if (fields.length != 2
                        || !fields[0].matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
                    throw new DataFormatException("Line " + lineNumber + ": invalid CSV row");
                }
                long quantity;
                try {
                    quantity = Long.parseLong(fields[1]);
                } catch (NumberFormatException error) {
                    throw new DataFormatException(
                            "Line " + lineNumber + ": quantity is not a long", error);
                }
                if (quantity <= 0) {
                    throw new DataFormatException(
                            "Line " + lineNumber + ": quantity must be positive");
                }
                if (!seenIds.add(fields[0])) {
                    throw new DataFormatException(
                            "Line " + lineNumber + ": duplicate id " + fields[0]);
                }
                try {
                    total = Math.addExact(total, quantity);
                    records = Math.incrementExact(records);
                } catch (ArithmeticException error) {
                    throw new DataFormatException(
                            "Line " + lineNumber + ": aggregate overflow", error);
                }
            }
        }
        return new Summary(records, total);
    }

    private static void writeSafely(Path output, Summary result,
                                    BeforeCommit beforeCommit) throws IOException {
        Path parent = output.getParent();
        if (!Files.isDirectory(parent)) {
            throw new IOException("Output parent directory does not exist: " + parent);
        }
        if (Files.isDirectory(output)) {
            throw new IOException("Output is a directory: " + output);
        }

        // A temp file in the same directory enables an atomic rename where supported.
        Path temporary = Files.createTempFile(parent, ".report-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("records=" + result.records());
                writer.newLine();
                writer.write("totalQuantity=" + result.totalQuantity());
                writer.newLine();
            }
            beforeCommit.run(temporary); // Inject a failure here in a test.
            try {
                Files.move(temporary, output,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("Atomic move unsupported; output not replaced", error);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    interface TestCase { void run(Path directory) throws Exception; }

    @FunctionalInterface
    interface ThrowingAction { void run() throws Exception; }

    static <T extends Throwable> T expect(Class<T> expected, ThrowingAction action)
            throws Exception {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return expected.cast(actual);
            throw new AssertionError("Expected " + expected.getSimpleName()
                    + ", got " + actual, actual);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void test(String name, TestCase body) throws Exception {
        Path directory = Files.createTempDirectory("io-lab-");
        try {
            body.run(directory);
            System.out.println("PASS " + name);
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    static Path input(Path directory, String contents) throws IOException {
        Path path = directory.resolve("orders.csv");
        Files.writeString(path, contents, StandardCharsets.UTF_8);
        return path;
    }

    static void runTests() throws Exception {
        test("success", dir -> {
            Path source = input(dir, "id,quantity\napple,2\nbanana,7\n");
            Path result = dir.resolve("report.txt");
            generate(source, result);
            check(Files.readAllLines(result, StandardCharsets.UTF_8)
                    .equals(List.of("records=2", "totalQuantity=9")), "wrong report");
        });
        test("missing-input", dir -> {
            expect(NoSuchFileException.class,
                    () -> generate(dir.resolve("missing.csv"), dir.resolve("out.txt")));
        });
        test("bad-header", dir -> {
            Path source = input(dir, "wrong,header\na,1\n");
            expect(DataFormatException.class,
                    () -> generate(source, dir.resolve("out.txt")));
        });
        test("bad-row", dir -> {
            Path source = input(dir, "id,quantity\na,nope\n");
            expect(DataFormatException.class,
                    () -> generate(source, dir.resolve("out.txt")));
        });
        test("duplicate-id", dir -> {
            Path source = input(dir, "id,quantity\na,1\na,2\n");
            expect(DataFormatException.class,
                    () -> generate(source, dir.resolve("out.txt")));
        });
        test("sum-overflow", dir -> {
            Path source = input(dir,
                    "id,quantity\na,9223372036854775807\nb,1\n");
            expect(DataFormatException.class,
                    () -> generate(source, dir.resolve("out.txt")));
        });
        test("malformed-utf8", dir -> {
            Path source = dir.resolve("orders.csv");
            Files.write(source, new byte[] {(byte) 0xC3, (byte) 0x28});
            expect(MalformedInputException.class,
                    () -> generate(source, dir.resolve("out.txt")));
        });
        test("output-is-directory", dir -> {
            Path source = input(dir, "id,quantity\na,1\n");
            Path result = Files.createDirectory(dir.resolve("out.txt"));
            expect(IOException.class, () -> generate(source, result));
            check(Files.isDirectory(result), "directory changed");
        });
        test("old-report-survives-bad-input", dir -> {
            Path source = input(dir, "id,quantity\na,0\n");
            Path result = dir.resolve("out.txt");
            Files.writeString(result, "KEEP", StandardCharsets.UTF_8);
            expect(DataFormatException.class, () -> generate(source, result));
            check("KEEP".equals(Files.readString(result)), "old output changed");
        });
        test("failure-before-commit", dir -> {
            Path source = input(dir, "id,quantity\na,1\n");
            Path result = dir.resolve("out.txt");
            Files.writeString(result, "KEEP", StandardCharsets.UTF_8);
            expect(IOException.class, () -> generate(source, result,
                    temporary -> { throw new IOException("injected failure"); }));
            check("KEEP".equals(Files.readString(result)), "old output changed");
            try (Stream<Path> files = Files.list(dir)) {
                check(files.noneMatch(p -> p.getFileName().toString()
                        .startsWith(".report-")), "temporary file leaked");
            }
        });
        test("same-input-and-output", dir -> {
            Path source = input(dir, "id,quantity\na,1\n");
            expect(IOException.class, () -> generate(source, source));
            check(Files.readString(source).contains("a,1"), "input changed");
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runTests();
        } else if (args.length == 2) {
            generate(Path.of(args[0]), Path.of(args[1]));
            System.out.println("Created report: " + args[1]);
        } else {
            throw new IllegalArgumentException(
                    "Usage: java IntegratedIoDemo [input.csv output.txt]");
        }
    }
}
```

## 4. Biên dịch, chạy và quan sát kết quả

Trong thư mục chứa tệp Java, thực hiện:

```bash
javac -encoding UTF-8 IntegratedIoDemo.java
java IntegratedIoDemo
```

Với hệ thống tệp hỗ trợ atomic rename, chương trình in đúng 11 dòng sau (đã chạy kiểm tra trên JDK 21 với mã Java 17 tương thích):

```text
PASS success
PASS missing-input
PASS bad-header
PASS bad-row
PASS duplicate-id
PASS sum-overflow
PASS malformed-utf8
PASS output-is-directory
PASS old-report-survives-bad-input
PASS failure-before-commit
PASS same-input-and-output
```

Muốn chạy như công cụ thật, tạo tệp `orders.csv` từ ví dụ ở mục 1, rồi chạy:

```bash
java IntegratedIoDemo orders.csv report.txt
```

Lệnh này báo `Created report: report.txt` khi thành công. Nội dung tệp báo cáo giống ví dụ; ký tự xuống dòng của tệp phụ thuộc nền tảng nhưng `Files.readAllLines` kiểm tra nội dung theo dòng, tránh gắn kiểm thử với CRLF/LF.

## 5. Ma trận kiểm thử nhánh thành công và thất bại

| Ca kiểm thử | Kích hoạt | Kết quả cần chứng minh |
|---|---|---|
| `success` | Dữ liệu đúng, hai bản ghi | Có báo cáo `records=2`, `totalQuantity=9` |
| `missing-input` | Đường dẫn nguồn không có tệp | `NoSuchFileException`; không tạo kết quả |
| `bad-header` | Header không chính xác | `DataFormatException` với vị trí dòng đầu |
| `bad-row` | Số lượng `nope` | `DataFormatException` thay vì bỏ qua dòng |
| `duplicate-id` | ID `a` xuất hiện hai lần | Phát hiện ID lặp và dừng |
| `sum-overflow` | Tổng vượt `Long.MAX_VALUE` | `Math.addExact` phát hiện tràn, không cho báo cáo sai |
| `malformed-utf8` | Byte `C3 28` không tạo chuỗi UTF-8 hợp lệ | `MalformedInputException` từ decoder ở chế độ `REPORT` |
| `output-is-directory` | Đường dẫn đích là thư mục | Trả `IOException`; thư mục còn nguyên |
| `old-report-survives-bad-input` | Báo cáo cũ là `KEEP`, dữ liệu mới sai | Báo cáo cũ vẫn đúng từng ký tự |
| `failure-before-commit` | Hook ném `IOException` sau khi đã đóng writer | Báo cáo cũ vẫn là `KEEP`; không còn tệp `.report-*.tmp` |
| `same-input-and-output` | Trỏ nguồn và đích tới một tệp | Từ chối xử lý; nguồn không bị ghi đè |

**Vì sao cần xác minh trạng thái sau lỗi?** Chỉ kiểm tra `expect(IOException.class, ...)` là chưa đủ: một chương trình vẫn có thể ném ngoại lệ *sau khi đã làm hỏng output*. Hai ca `old-report-survives-bad-input` và `failure-before-commit` kiểm tra trực tiếp tính bất biến của dữ liệu cũ; ca sau kiểm tra thêm cleanup. Việc tạo thư mục tạm cho mỗi test khiến kết quả ít phụ thuộc trạng thái còn sót từ lần chạy khác.

## 6. Cơ chế xử lý lỗi và vòng đời tài nguyên

**Đường đọc:** `Files.newInputStream` có thể ném `NoSuchFileException`, `AccessDeniedException` hoặc các `IOException` khác. `InputStreamReader` đổi byte thành ký tự bằng decoder chỉ định; chế độ `REPORT` bảo đảm byte UTF-8 sai bị báo thay vì thay thầm bằng ký tự `�`. `BufferedReader.readLine()` tiện cho dữ liệu theo dòng nhưng không thích hợp khi cần giữ nguyên ký tự xuống dòng hoặc xử lý bản ghi nhiều dòng. Đóng `BufferedReader` trong `try-with-resources` đóng luôn reader bên trong và input stream, kể cả khi parser ném lỗi.

**Đường kiểm tra:** `DataFormatException extends IOException` biểu diễn nội dung đầu vào không đáp ứng hợp đồng, với số dòng có thể dùng để chẩn đoán. `Long.parseLong` xử lý lỗi văn bản/số vượt phạm vi; `Math.addExact` và `Math.incrementExact` ngăn tràn khi cộng dồn. `Set` phát hiện ID trùng. Bài chọn chính sách **fail fast**: chỉ một dòng sai đã làm cả lượt xử lý thất bại; không tạo báo cáo một phần. Nếu nghiệp vụ muốn gom nhiều lỗi để trả về cùng lúc, phải thiết kế kết quả validation riêng.

**Đường ghi:** Ghi vào `output` trực tiếp bằng `TRUNCATE_EXISTING` có thể xóa nội dung cũ trước khi lỗi xảy ra. Tệp tạm cùng thư mục giảm nguy cơ đổi tên xuyên hệ thống tệp. Writer được đóng trước `move`; nếu hook ném lỗi, `finally` dọn tệp tạm. Nếu rename nguyên tử không được hỗ trợ, phương thức phát sinh `IOException` bọc `AtomicMoveNotSupportedException`. Với `ATOMIC_MOVE`, cách xử lý đích đã tồn tại có khác biệt theo provider; cần kiểm chứng trên hệ thống tệp triển khai, và lỗi truy cập/quyền ghi không nên được nuốt rồi coi như thành công.

**Lưu ý giới hạn thực tế:** Ví dụ không theo dõi dung lượng đĩa/quota, không gọi `FileChannel.force` để tăng bảo đảm bền vững sau mất điện, không giải quyết nhiều tiến trình cùng cập nhật báo cáo và không bảo vệ khỏi thay đổi đường dẫn/symlink giữa lúc kiểm tra và ghi. Ở dịch vụ thật, bổ sung giới hạn kích thước file, chính sách path/permission, log có ngữ cảnh và cơ chế khóa/phối hợp khi cần. Không dùng `catch (Exception) { }`, không làm mất nguyên nhân lỗi trong lúc chuyển thành `DataFormatException`.

## 7. Tự đánh giá kết quả và mở rộng có kiểm soát

Một bài hoàn chỉnh cần đạt đồng thời: chạy xanh ca hợp lệ; mỗi nhánh sai thất bại theo đúng loại/nhóm ngoại lệ; nội dung hợp lệ cũ còn nguyên khi validation hoặc hook lỗi; không rò rỉ temp; input/output khác nhau; file handle được đóng; output không được công bố trước bước commit.

Để kiểm tra hiểu sâu, hãy chủ động đổi một ca test thành lượng `0` hoặc `-1`, một dòng thiếu dấu phẩy, thêm dấu phẩy thứ hai, một header rỗng, một ID không hợp lệ và một output parent chưa tồn tại: xác định lỗi do pha parse hay pha write, và vì sao. Thử đổi decoder sang chính sách `REPLACE` để thấy lý do cần chế độ strict khi chất lượng dữ liệu có ý nghĩa. Khi đưa sang CI, có thể chuyển các kiểm thử thành JUnit 5 với `@TempDir`, `assertThrows` và `assertEquals`; giữ nguyên các assertion về dữ liệu và cleanup.

Phần quan trọng của thực hành là **chứng minh hành vi khi thất bại**, không chỉ chứng minh một đường chạy thành công.

</details>

- [Quay lại đầu trang](#back-to-top)
