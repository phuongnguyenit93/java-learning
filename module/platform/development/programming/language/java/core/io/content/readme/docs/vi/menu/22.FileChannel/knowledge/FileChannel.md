<a id="back-to-top"></a>

# FileChannel

## Menu
- [Mở FileChannel bằng Path và OpenOption](#file-channel-001)
- [Đọc ghi theo vị trí hiện tại](#file-channel-002)
- [Đọc ghi tại vị trí tuyệt đối mà không đổi position](#file-channel-003)
- [size, truncate và giới hạn thay đổi độ dài](#file-channel-004)
- [transferTo, transferFrom và xử lý số byte chuyển thực tế](#file-channel-005)
- [Zero-copy là khả năng tối ưu, không phải bảo đảm cho mọi tình huống](#file-channel-006)
- [force và phạm vi bảo đảm với thiết bị lưu trữ](#file-channel-007)
- [Quan hệ với FileInputStream, FileOutputStream và RandomAccessFile](#file-channel-008)
- [Dùng đồng thời: vị trí chia sẻ và thao tác có vị trí tường minh](#file-channel-009)
- [Append và các hành vi phụ thuộc hệ điều hành](#file-channel-010)
- [FileChannel không phải selectable channel](#file-channel-011)

## <a id="file-channel-001">Mở FileChannel bằng Path và OpenOption</a>

<details>
<summary>Xem chi tiết</summary>

# Mở FileChannel bằng Path và OpenOption trong Java NIO

`FileChannel` là kênh truy cập tệp có thể đọc, ghi, di chuyển vị trí, lấy kích thước, cắt ngắn tệp và thực hiện một số thao tác nâng cao như `transferTo`, `transferFrom` hoặc `map`. Trong Java NIO.2, cách mở trực tiếp phổ biến là `FileChannel.open(Path, OpenOption...)`. Các `OpenOption` xác định **quyền truy cập, việc tạo tệp mới, cách xử lý tệp đã tồn tại và yêu cầu đồng bộ ghi**.

Chọn sai option có thể khiến ứng dụng âm thầm mở chỉ để đọc, ghi đè dữ liệu ngoài ý muốn, cắt ngắn tệp ngay khi mở, thất bại nếu tệp không tồn tại hoặc gặp tổ hợp option không hợp lệ. Bài này tập trung vào việc **mở đúng tệp và đúng chế độ**, kiểm tra hậu quả của từng option và quản lý vòng đời `FileChannel`.

## 1. Hai dạng `FileChannel.open`

Các phương thức tĩnh chính trong `java.nio.channels.FileChannel`:

```java
public static FileChannel open(
        Path path, OpenOption... options) throws IOException;

public static FileChannel open(
        Path path,
        Set<? extends OpenOption> options,
        FileAttribute<?>... attrs) throws IOException;
```

Đây là chữ ký API để tham khảo, không phải một lớp Java độc lập.

- `path` là đường dẫn tệp, biểu diễn bởi `java.nio.file.Path`, ví dụ `Path.of("data", "events.bin")`. Đường dẫn tương đối được giải quyết theo thư mục làm việc của chương trình tại thời điểm thao tác; không nhất thiết là thư mục chứa file `.java`.
- `options` là những hành vi mở tệp, phần lớn chọn từ enum `java.nio.file.StandardOpenOption`. Đối với dạng varargs, truyền nhiều option cách nhau bằng dấu phẩy.
- `attrs` là thuộc tính tùy chọn được áp dụng khi **tạo** tệp mới, ví dụ quyền POSIX ở những hệ thống hỗ trợ. Dạng nhận `Set` cho phép truyền attributes và options một cách có cấu trúc.
- Giá trị trả về là một `FileChannel` đang mở. Mọi thao tác I/O phải xử lý `IOException` và đóng channel khi sử dụng xong.

Ví dụ mở tệp đã tồn tại ở chế độ đọc:

```java
Path path = Path.of("data", "events.bin");
try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.READ)) {
    System.out.println("size=" + channel.size());
}
```

Đây là đoạn minh họa trong chương trình có các import `Path`, `FileChannel`, `StandardOpenOption`, và phương thức cho phép ném `IOException`. Tệp phải tồn tại và truy cập được; output `size` phụ thuộc nội dung tệp.

**Quy tắc mặc định dễ quên:** Nếu không truyền bất kỳ option nào, `FileChannel.open(path)` mở **chỉ để đọc**, tương đương ý định `READ`. Nó không tự tạo tệp và không tự cho phép ghi.

## 2. Những `StandardOpenOption` cần biết

Các option dưới đây thường được kết hợp khi mở `FileChannel`:

| Option | Ý nghĩa |
| --- | --- |
| `READ` | Mở tệp để đọc; `read(...)` được phép |
| `WRITE` | Mở tệp để ghi; `write(...)` được phép |
| `CREATE` | Tạo tệp nếu chưa tồn tại, khi mở có quyền ghi; nếu đã tồn tại thì mở tệp đó |
| `CREATE_NEW` | Tạo tệp mới và thất bại nếu đường dẫn đã tồn tại; bước kiểm tra tồn tại và tạo là thao tác nguyên tử theo hợp đồng API |
| `TRUNCATE_EXISTING` | Khi mở **để ghi**, cắt kích thước tệp có sẵn về 0 ngay lúc mở |
| `APPEND` | Khi ghi, đưa dữ liệu về cuối tệp theo ngữ nghĩa append; không dùng cùng `READ` hoặc `TRUNCATE_EXISTING` |
| `SYNC` | Yêu cầu đồng bộ nội dung và metadata liên quan tới thiết bị lưu trữ cho các cập nhật theo hợp đồng của API |
| `DSYNC` | Yêu cầu đồng bộ nội dung tệp; phạm vi metadata cần đồng bộ hẹp hơn `SYNC` |
| `DELETE_ON_CLOSE` | Yêu cầu xóa tệp khi đóng channel (best effort khi thất bại hoặc tiến trình kết thúc bất thường); cần hiểu hệ quả trên từng hệ thống |
| `SPARSE` | Gợi ý tạo tệp thưa khi tạo mới; hệ thống có thể bỏ qua gợi ý, không bảo đảm file sẽ thật sự sparse |

`READ`, `WRITE`, `CREATE`, `CREATE_NEW`, `TRUNCATE_EXISTING` và `APPEND` là các option quan trọng nhất để hiểu hành vi mở tệp. Những option đồng bộ ảnh hưởng độ bền dữ liệu và có thể làm tăng độ trễ ghi; chỉ chọn theo yêu cầu lưu trữ.

Lưu ý tinh tế: `CREATE`, `CREATE_NEW` và `TRUNCATE_EXISTING` không tự làm một lần mở **chỉ đọc** trở thành mở để ghi. Với `FileChannel.open`, các tùy chọn này được bỏ qua khi tệp không được mở cho ghi; muốn tạo, cập nhật hay truncate phải yêu cầu `WRITE` rõ ràng. Cũng không nên trông cậy `APPEND` sẽ tự mang lại một cấu hình đọc/ghi tùy ý: cấu hình dễ hiểu và phổ biến là `WRITE, APPEND`.

## 3. Những cấu hình mở thông dụng

### 3.1. Đọc tệp hiện có

```java
try (FileChannel channel =
        FileChannel.open(path, StandardOpenOption.READ)) {
    // Đọc từ channel.
}
```

Đây là đoạn minh họa với `path` kiểu `Path`. Nếu file không có, thao tác thường ném `NoSuchFileException`. Đọc không làm tệp dài thêm, và `channel.write(...) `trên channel chỉ đọc ném `NonWritableChannelException`.

### 3.2. Đọc và sửa tệp đã có

```java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {
    // Đọc và ghi, không tự cắt ngắn.
}
```

Nếu không có `CREATE` hoặc `CREATE_NEW`, tệp cần tồn tại. Có `WRITE` không có nghĩa tự xóa dữ liệu cũ: việc ghi bắt đầu từ vị trí hiện hành của channel, mặc định 0 khi mới mở; ghi byte vào giữa tệp sẽ thay thế byte tại vùng tương ứng.

### 3.3. Tạo nếu chưa có, giữ nội dung nếu đã có

```java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE)) {
    // File được tạo nếu thiếu; nội dung file cũ không bị truncate khi mở.
}
```

**Cảnh báo:** "Không truncate" không đồng nghĩa với "ghi nối cuối". Vị trí mặc định của channel mới mở là 0; ghi ngay lập tức sẽ **ghi đè từ đầu tệp**. Muốn ghi nối cuối, dùng `APPEND` hoặc chủ động tìm EOF khi phù hợp, đồng thời cân nhắc tính đồng thời.

### 3.4. Tạo mới duy nhất, không chấp nhận ghi đè file cũ

```java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE_NEW)) {
    // Chỉ đến đây khi tệp mới đã được tạo thành công.
}
```

Khi đường dẫn đã tồn tại, việc mở thất bại, thường với `FileAlreadyExistsException`. `CREATE_NEW` phù hợp để bảo vệ file kết quả mới hoặc tên file duy nhất và tránh lỗi "kiểm tra `exists` rồi mới tạo" giữa các luồng/tiến trình. Một symbolic link đã tồn tại tại đường dẫn cũng được xem là tồn tại cho việc `CREATE_NEW`, kể cả khi đích của nó không còn.

### 3.5. Tạo mới hoặc xóa sạch nội dung file cũ

```java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
    // Nếu file tồn tại, size đã về 0 ngay sau khi mở thành công.
}
```

Đây là lựa chọn dùng khi cố ý **thay thế toàn bộ dữ liệu file**. Truncation xảy ra ngay khi mở, trước khi code của khối `try` ghi dữ liệu. Nếu ứng dụng crash sau khi mở nhưng trước khi ghi xong, nội dung cũ đã mất. Không dùng cách này khi muốn cập nhật một vài byte, thêm bản ghi hoặc cần quy trình thay thế file an toàn.

### 3.6. Ghi nối vào cuối file

```java
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND)) {
    // Ghi các byte mới ở cuối tệp.
}
```

Khi đang ở chế độ `APPEND`, thao tác ghi được thực hiện theo ngữ nghĩa nối cuối tệp. Tuy nhiên hợp đồng `FileChannel` **không bảo đảm trên mọi hệ điều hành** rằng bước định vị EOF và ghi luôn diễn ra nguyên tử đối với các tiến trình/luồng khác cùng ghi. Nếu cần nhiều writer cập nhật cùng một file, cần thiết kế phối hợp thích hợp; đừng coi `APPEND` là một transaction hoặc thay thế hoàn toàn locking.

Không kết hợp `APPEND` với `READ` hoặc `TRUNCATE_EXISTING`; đó là tổ hợp bất hợp lệ theo API.

## 4. Chương trình Java 17+ hoàn chỉnh: mở, ghi, đọc, cập nhật, append và truncate

Ví dụ tạo **thư mục tạm riêng** rồi dùng một tệp `sample.bin` trong thư mục đó. Các byte là ASCII để số lượng byte và kết quả không phụ thuộc charset mặc định. Toàn bộ channel được đóng bằng `try-with-resources`, sau đó chương trình dọn tệp/thư mục tạm.

Tạo file **`FileChannelOpenDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileChannelOpenDemo {
    private FileChannelOpenDemo() {}

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("file-channel-demo-");
        Path file = directory.resolve("sample.bin");
        try {
            // CREATE_NEW: tạo mới; WRITE: cho phép ghi.
            try (FileChannel out = FileChannel.open(file,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                writeAll(out, ByteBuffer.wrap(
                        "Java NIO".getBytes(StandardCharsets.US_ASCII)));
                System.out.println("created bytes=" + out.size());
            }

            // Không có option: mặc định chỉ đọc.
            try (FileChannel in = FileChannel.open(file)) {
                ByteBuffer buffer = ByteBuffer.allocate(8);
                while (buffer.hasRemaining()) {
                    int n = in.read(buffer);
                    if (n < 0) {
                        break;
                    }
                    if (n == 0) {
                        throw new IOException("No read progress");
                    }
                }
                buffer.flip();
                System.out.println("default read="
                        + StandardCharsets.US_ASCII.decode(buffer));
                System.out.println("readable=" + in.isOpen());
            }

            // Đọc/ghi file cũ mà không xóa dữ liệu.
            try (FileChannel edit = FileChannel.open(file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {
                edit.position(5); // Chữ 'N' ở byte thứ 5.
                writeAll(edit, ByteBuffer.wrap(
                        "API".getBytes(StandardCharsets.US_ASCII)));
                System.out.println("edited size=" + edit.size());
            }

            // APPEND: nối vào cuối, không thay phần đầu.
            try (FileChannel append = FileChannel.open(file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                writeAll(append, ByteBuffer.wrap(
                        "!".getBytes(StandardCharsets.US_ASCII)));
                System.out.println("after append size=" + append.size());
            }

            System.out.println("content="
                    + Files.readString(file, StandardCharsets.US_ASCII));

            // TRUNCATE_EXISTING: file đã có và được cắt về 0.
            try (FileChannel truncate = FileChannel.open(file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                System.out.println("after truncate=" + truncate.size());
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void writeAll(FileChannel channel, ByteBuffer source)
            throws IOException {
        while (source.hasRemaining()) {
            int n = channel.write(source);
            if (n == 0) {
                throw new IOException("No write progress");
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac FileChannelOpenDemo.java
java FileChannelOpenDemo
```

Kết quả khi các thao tác tệp tạm đều thành công:

```text
created bytes=8
default read=Java NIO
readable=true
edited size=8
after append size=9
content=Java API!
after truncate=0
```

Giải thích:

1. `CREATE_NEW + WRITE` tạo tệp chưa tồn tại và ghi 8 byte `Java NIO`; `size()` là 8.
2. `FileChannel.open(file)` không có option được mở ở chế độ `READ`. Vòng đọc nạp tối đa 8 byte vào buffer rồi `flip()` để giải mã. `isOpen()` là `true` bên trong khối `try`.
3. `READ + WRITE` mở tệp hiện có. Vị trí byte 5 là `N`; ghi `API` thay ba byte `NIO` và giữ kích thước bằng 8.
4. `WRITE + APPEND` nối byte `!` tại cuối, kích thước trở thành 9 và nội dung `Java API!`.
5. `WRITE + TRUNCATE_EXISTING` xóa nội dung về kích thước 0 ngay khi mở. Việc đọc lại sau đó sẽ cho EOF nếu chưa ghi tiếp.

**Phân biệt rõ:** `FileChannel.write(ByteBuffer)` có thể ghi *ít hơn* số byte còn lại trong buffer. Hàm `writeAll` lặp cho đến khi đã tiêu thụ toàn bộ buffer; nhờ đó ví dụ không phụ thuộc việc một lần ghi hoàn tất hết dữ liệu. Đây là tệp tạm do chương trình sở hữu, không can thiệp vào tệp dữ liệu của người học.

## 5. Dạng `Set<OpenOption>` và `FileAttribute`

Khi option được xây dựng có điều kiện hoặc khi cần thuộc tính lúc tạo file, dùng overload nhận `Set`:

```java
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

// Đoạn minh họa trong phương thức xử lý IOException; path đã được khai báo.
Set<OpenOption> options = Set.of(
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);

try (FileChannel channel = FileChannel.open(path, options)) {
    // Ghi vào file vừa được tạo.
}
```

Overload thực tế nhận `Set<? extends OpenOption>` và có varargs `FileAttribute<?>...` ở cuối. Nếu môi trường có filesystem POSIX, có thể yêu cầu quyền truy cập khi tạo:

```java
// Minh họa riêng cho hệ thống tệp hỗ trợ POSIX attributes.
var permissions = java.nio.file.attribute.PosixFilePermissions
        .asFileAttribute(
                java.nio.file.attribute.PosixFilePermissions
                        .fromString("rw-------"));

try (FileChannel channel = FileChannel.open(
        path,
        java.util.Set.of(StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE),
        permissions)) {
    // File tạo mới với POSIX permission được yêu cầu.
}
```

Đây là đoạn mã minh họa cần `path` kiểu `Path`, import cần thiết và xử lý `IOException`. Trên Windows hoặc provider không hỗ trợ thuộc tính POSIX, yêu cầu này có thể thất bại; không áp dụng mù quáng như một cấu hình tương thích mọi hệ điều hành. Quyền và chính sách bảo mật thực tế còn tùy filesystem, môi trường chạy và các hạn chế của hệ điều hành.

Các thuộc tính tạo tệp này khác với `StandardOpenOption`: `OpenOption` xác định *cách mở*, trong khi `FileAttribute` mô tả *thuộc tính cho tệp được tạo*.

## 6. Xử lý ngoại lệ theo chế độ mở

Không phải mọi lỗi mở tệp đều là "file không tồn tại". Các lỗi phổ biến:

| Tình huống | Kết quả hoặc ngoại lệ cần chuẩn bị |
| --- | --- |
| `READ` một đường dẫn không tồn tại | Thường `NoSuchFileException` |
| `CREATE_NEW + WRITE` với tên file đã tồn tại | Thường `FileAlreadyExistsException` |
| Đường dẫn sai, thiếu quyền hoặc file bị khóa bởi tác nhân khác | `IOException` phù hợp với nền tảng, có thể `AccessDeniedException` |
| Kết hợp `APPEND` và `READ`, hoặc `APPEND` và `TRUNCATE_EXISTING` | `IllegalArgumentException` |
| Đưa option mà provider không hỗ trợ | Có thể `UnsupportedOperationException` |
| Gọi `write(...)` trên channel chỉ đọc | `NonWritableChannelException` |
| Gọi `read(...)` trên channel chỉ ghi | `NonReadableChannelException` |
| Gọi thao tác trên channel đã đóng | Có thể `ClosedChannelException` |
| Dung lượng lưu trữ hết hoặc thiết bị gặp sự cố trong khi ghi | `IOException` trong quá trình I/O, không chỉ tại lúc `open` |

Tên exception chi tiết phụ thuộc provider và nền tảng; `IOException` cần được xử lý/báo lại khi ứng dụng cần biết file có thực sự được ghi thành công hay không. Đừng bắt `Exception` rồi giả vờ đã tạo/ghi xong file.

Ví dụ xử lý tình huống tạo mới khi file đã tồn tại:

```java
// Đoạn minh họa trong phương thức đã khai báo Path file.
try (FileChannel output = FileChannel.open(file,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE_NEW)) {
    // Tiến hành ghi dữ liệu mới.
} catch (java.nio.file.FileAlreadyExistsException e) {
    System.out.println("File already exists: " + file);
} catch (java.io.IOException e) {
    // Báo lỗi I/O thực sự cho tầng gọi.
    throw e;
}
```

Chỉ xử lý `FileAlreadyExistsException` theo cách trên nếu "tên đã có" là một trường hợp nghiệp vụ đã dự kiến. Nếu bảo vệ dữ liệu người dùng, không tự chuyển sang `TRUNCATE_EXISTING` để ghi đè khi gặp lỗi đó.

## 7. Vị trí kênh và việc ghi đè sau khi mở

Ngay sau khi mở `FileChannel` thông thường, vị trí hiện tại bắt đầu ở **byte 0**. Với kiểu I/O tương đối:

```java
int bytesRead = channel.read(buffer);
int bytesWritten = channel.write(buffer);
long where = channel.position();
```

Đây là đoạn minh họa trong đó `channel` và `buffer` phải có sẵn, channel cần mở với đúng quyền tương ứng; không được dùng chính buffer một cách tùy tiện cho cả đọc và ghi khi chưa quản lý `flip()/clear()`. Các thao tác tương đối thay đổi vị trí hiện hành theo số byte đã thực sự đọc/ghi.

Với channel mở `WRITE + CREATE` nhưng file đã tồn tại, `channel.write(buffer)` tại position 0 sẽ **ghi đè từ đầu**, không tự append và không tự truncate phần đuôi. Ví dụ file cũ `ABCDEFGH` dài 8 byte, ghi ba byte `xyz` từ vị trí 0 sẽ tạo nội dung `xyzDEFGH`, vẫn dài 8 byte.

Nếu cần cập nhật tại offset cụ thể mà không thay đổi vị trí hiện tại, `FileChannel` còn có các phương thức positional I/O:

```java
// Đoạn minh họa, buffer đã được chuẩn bị và channel có quyền ghi.
long offset = 4;
int written = channel.write(buffer, offset);
```

Lời gọi `write(buffer, offset)` sử dụng offset trong tệp cho lần ghi đó, **không thay đổi `channel.position()`**, nhưng vẫn làm tăng `buffer.position()` theo số byte đã ghi. Cần xử lý số byte `written` thực tế. Kiểu này phù hợp với cập nhật bản ghi ngẫu nhiên, trong khi `APPEND` dùng cho luồng ghi nối tiếp.

Không nên kết hợp suy đoán dựa trên `position()` và `APPEND`: khi append, ý định ghi là cuối tệp và việc định vị EOF có thể được thực hiện khi ghi. Hãy tách rõ trường hợp cập nhật vị trí và trường hợp ghi nối.

## 8. Đóng tài nguyên, độ bền dữ liệu và ghi file an toàn

`FileChannel` triển khai `AutoCloseable`, nên sử dụng `try-with-resources`. Đóng channel giải phóng tài nguyên hệ điều hành gắn với lần mở; nó không có nghĩa một thao tác ghi trước đó đã thỏa mãn mọi yêu cầu durability của ứng dụng trên mọi thiết bị lưu trữ.

`channel.force(true)` yêu cầu đẩy cập nhật nội dung và metadata liên quan đến thiết bị lưu trữ theo hợp đồng của `FileChannel`, trong khi `force(false)` chỉ buộc nội dung và metadata cần cho việc lấy lại nội dung; phạm vi đảm bảo phụ thuộc loại tệp/thiết bị/provider. Với kênh không phải tệp trên thiết bị lưu trữ cục bộ, các đảm bảo có thể yếu hơn. `SYNC` và `DSYNC` là những yêu cầu được chọn **ngay từ lúc mở** để đồng bộ các cập nhật trong suốt vòng đời kênh. Các cơ chế này có chi phí I/O, nên chọn theo mức an toàn dữ liệu thật sự cần thiết.

Nếu mục tiêu là **thay thế toàn bộ một file quan trọng**, `TRUNCATE_EXISTING` mở thẳng file đích có rủi ro mất nội dung cũ khi ghi lỗi. Một thiết kế thường dùng là:

1. Tạo file tạm **trong cùng filesystem/thư mục thích hợp** bằng `CREATE_NEW + WRITE`.
2. Ghi đầy đủ và kiểm tra lỗi, bảo đảm channel được đóng; thực hiện `force` nếu yêu cầu độ bền dữ liệu.
3. Thay thế tên file đích bằng `Files.move(...)` với chiến lược phù hợp, có thể yêu cầu `ATOMIC_MOVE` khi filesystem hỗ trợ.
4. Xử lý trường hợp `AtomicMoveNotSupportedException`, thiếu quyền, tệp đích đang được mở hoặc sự cố I/O, cùng chính sách cleanup rõ ràng.

Đây là **quy trình thiết kế** chứ không phải bảo đảm rằng mọi filesystem hỗ trợ thay thế nguyên tử hoặc `Files.move` tự động bền vững trước mất điện. Việc ghi file tạm và thao tác thay thế tên có những ràng buộc durability/phối hợp khác nhau tùy nền tảng. Khi nội dung cực kỳ quan trọng, cần quy trình kiểm thử failure/recovery trên hệ thống triển khai thật.

## 9. Cân nhắc quyền truy cập và cấu hình an toàn

- **Chỉ đọc khi chỉ cần đọc:** Mở `READ` khiến code không thể tình cờ ghi qua channel này; nó cũng thể hiện đúng ý định.
- **Bảo vệ file đã tồn tại:** Sử dụng `CREATE_NEW` nếu không được phép ghi đè. Không dựa vào `Files.notExists()` rồi mở `CREATE` như một bước "tạo nếu chưa có" an toàn khỏi race.
- **Tránh truncate ngoài ý muốn:** Chỉ thêm `TRUNCATE_EXISTING` khi nghiệp vụ đòi hỏi làm rỗng file cũ ngay lúc mở.
- **Không tin mặc định đường dẫn:** `Path.of("result.bin")` dựa vào working directory; với ứng dụng thực tế cần xác định root, quyền và cách xử lý đường dẫn người dùng nhập.
- **Đừng cho rằng CREATE tạo cha:** `CREATE` tạo tệp, **không tạo những thư mục cha còn thiếu**. Nếu cần, tạo các thư mục cha được phép bằng `Files.createDirectories(parent)` trước khi mở.
- **Symlink và đường dẫn không tin cậy:** `CREATE` / `WRITE` có thể tác động đến một file khác theo cách đường dẫn được hệ thống tệp phân giải. Với thư mục có thể bị tác nhân không tin cậy sửa, phải có thiết kế bảo vệ đường dẫn và quyền truy cập thích hợp; kiểm tra `exists` trước khi mở không loại bỏ race.
- **Giới hạn quyền theo tác vụ:** Không cho phép `WRITE` hoặc `DELETE_ON_CLOSE` chỉ vì "có thể sẽ cần"; cấu hình càng rộng càng dễ phá dữ liệu.

Nếu cần mở `FileChannel` từ một `RandomAccessFile` có sẵn, có thể dùng `randomAccessFile.getChannel()`. Trường hợp đó khác với `FileChannel.open(Path, ...)`: channel lấy từ `RandomAccessFile` **chia sẻ trạng thái vị trí tệp** với đối tượng `RandomAccessFile` của cùng lần mở. Còn mở channel mới trực tiếp qua `Path` tạo một lần mở với trạng thái vị trí riêng, dù hai channel có thể tham chiếu đến cùng nội dung tệp.

## 10. Những lỗi thực hành dễ mắc

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Gọi `FileChannel.open(path)` rồi `write()` | Mặc định chỉ đọc, ném `NonWritableChannelException` | Thêm `WRITE` khi thật sự cần ghi |
| Dùng `WRITE + CREATE` và nghĩ luôn ghi nối | Ghi đè từ đầu tệp hiện có | Dùng `APPEND` khi cần nối hoặc đặt vị trí có chủ đích |
| Thêm `TRUNCATE_EXISTING` để "cập nhật" file | File bị làm rỗng ngay khi mở | Bỏ truncate khi chỉ sửa vùng dữ liệu |
| Dùng `CREATE` khi không được phép thay nội dung cũ | File tồn tại vẫn được mở để ghi | Dùng `CREATE_NEW` và xử lý file đã có |
| Truyền `READ + APPEND` | Tổ hợp bất hợp lệ | Thiết kế luồng đọc và ghi nối riêng, tránh option xung đột |
| Không đóng channel | Giữ file descriptor/tài nguyên, có thể gây lỗi file lock hoặc cạn tài nguyên | Dùng `try-with-resources` |
| Gọi `write` một lần và giả định đã ghi mọi byte | Dữ liệu có thể thiếu | Lặp đến khi `ByteBuffer.hasRemaining()==false` |
| Tin rằng `CREATE` tự tạo thư mục cha | Mở file thất bại nếu thư mục cha chưa có | Chủ động tạo thư mục cha hợp lệ |
| Giả định `SYNC/DSYNC` miễn phí hoặc luôn bảo đảm chống mọi kiểu lỗi | Chậm bất ngờ hoặc đánh giá sai durability | Đo và kiểm tra hợp đồng trên hệ thống triển khai |
| Cắt ngắn trực tiếp file cấu hình quan trọng khi thay thế | Crash giữa ghi có thể để file trống hoặc một phần | Ghi file tạm, xác minh và thay thế theo chính sách recovery |

**Quy tắc lựa chọn:** Dùng `READ` cho tệp hiện có chỉ cần đọc; `READ + WRITE` cho cập nhật tại chỗ; `WRITE + CREATE_NEW` khi tạo file mới bắt buộc không trùng tên; `WRITE + CREATE` khi được phép tạo nếu thiếu; thêm `TRUNCATE_EXISTING` chỉ khi thực sự muốn làm rỗng tệp cũ; thêm `APPEND` để ghi nối cuối. Viết rõ các option giúp chương trình dễ kiểm tra, tránh sai lệch dữ liệu và làm rõ chính sách xử lý lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-002">Đọc ghi theo vị trí hiện tại</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel: đọc ghi theo vị trí hiện tại trong Java NIO

## 1. Bản chất của current file position

`java.nio.channels.FileChannel` là kênh đọc/ghi tệp trong Java NIO. Mỗi đối tượng channel có một **vị trí hiện tại** (`current file position`) kiểu `long`, đo bằng **byte** từ đầu tệp. Hai phương thức quan trọng:

- `position()`: trả offset hiện tại.
- `position(long newPosition)`: đặt offset cho các thao tác I/O tương đối tiếp theo.

Khi gọi `read(ByteBuffer)` hay `write(ByteBuffer)` **không truyền offset**, channel xử lý byte bắt đầu từ position hiện tại, rồi tự tiến theo **số byte thực tế** đã đọc/ghi. Đây chính là **đọc ghi theo vị trí hiện tại**.

Ngược lại, `read(ByteBuffer, long position)` và `write(ByteBuffer, long position)` làm việc ở offset chỉ định mà **không thay đổi current file position**. Hai cơ chế đều cho phép truy cập ngẫu nhiên; khác nhau ở việc có dùng/tiến con trỏ chung của channel hay không.

**Mục tiêu:** biết mở channel đúng quyền, điều khiển position, thực hiện vòng lặp đọc/ghi đủ byte, phân biệt position của channel và `ByteBuffer`, nhận biết EOF, ghi đè giữa tệp, đọc lại từ đầu, đồng thời tránh tranh chấp khi nhiều luồng sử dụng cùng channel.

## 2. Hình dung tệp và con trỏ

Giả sử tệp có sáu byte ASCII `ABCDEF`:

```text
Byte offset: 0 1 2 3 4 5 6
Nội dung:   A B C D E F |EOF|
            ^
          position=0
```

Sau khi `read(dst)` đọc thành công 2 byte từ offset 0:

```text
Byte offset: 0 1 2 3 4 5 6
Nội dung:   A B C D E F |EOF|
                ^
              position=2
```

Nếu position đang là 2, `write(ByteBuffer.wrap(new byte[]{'X','Y'}))` (ghi đủ hai byte) sẽ thay `CD` bằng `XY`, tạo nội dung `ABXYEF`, position tăng lên 4. Ghi giữa tệp là **ghi đè**, không tự chèn byte hay dịch các byte phía sau.

Đặc điểm cần nhớ:

1. Vị trí là **byte offset**, không phải chỉ số ký tự trong chuỗi UTF-8.
2. `size()` trả độ dài tệp; `position()` là nơi I/O tương đối tiếp theo bắt đầu. Chúng có thể khác nhau.
3. `position(newPosition)` không tự đọc, ghi, cắt ngắn hay mở rộng tệp.
4. Cho phép đặt position vượt kích thước tệp. Đọc ở nơi chưa có byte sẽ gặp EOF; ghi có thể mở rộng file và tạo khoảng trống phụ thuộc filesystem/provider.
5. Các `FileChannel` mở **riêng** vào cùng một tệp có con trỏ position riêng, dù dữ liệu tệp vẫn được chia sẻ.
6. Nhiều thành phần sử dụng **cùng một đối tượng FileChannel** chia sẻ cùng current position của đối tượng ấy.

## 3. Mở FileChannel đúng mục đích

Java 17+ thường dùng `FileChannel.open(Path, OpenOption...)`, kết hợp try-with-resources:

```java
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

Path path = Path.of("data.bin");

try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {
    // channel.position(), channel.read(...), channel.write(...)
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập.

| OpenOption | Tác dụng |
|---|---|
| `READ` | Cho phép đọc tệp |
| `WRITE` | Cho phép ghi tệp |
| `CREATE` | Tạo tệp nếu chưa có khi mở để ghi |
| `CREATE_NEW` | Tạo mới, lỗi nếu đường dẫn đã tồn tại |
| `TRUNCATE_EXISTING` | Nếu mở để ghi, cắt rỗng nội dung tệp hiện hữu |
| `APPEND` | Các thao tác ghi tuân theo semantics nối cuối tệp; không dùng để sửa offset tùy ý |

Mở để đọc đơn thuần:

```java
try (FileChannel channel =
             FileChannel.open(path, StandardOpenOption.READ)) {
    // Chỉ đọc.
}
```

Đừng thêm `TRUNCATE_EXISTING` nếu mục tiêu là chỉnh sửa tệp đang chứa dữ liệu. Với `APPEND`, không kết hợp `READ`: tổ hợp không hợp lệ có thể ném `IllegalArgumentException`. Để đọc và ghi tại offset tùy ý, chọn `READ` + `WRITE` thay vì `APPEND`.

Khi mở channel theo cách thông thường, vị trí ban đầu là 0. Trong các tình huống cần rõ ràng, gọi `channel.position(0L)` để xác lập điểm bắt đầu. Luôn đóng channel bằng try-with-resources.

Ngoài `FileChannel.open`, có thể lấy channel từ `RandomAccessFile.getChannel()`. Nếu làm như vậy, **`RandomAccessFile` và channel liên kết dùng chung file position**: `raf.seek(...)` tác động tới `channel.position()` và ngược lại. Tránh phối hợp tùy tiện hai API trên cùng đối tượng.

## 4. Phân biệt hai loại position

`FileChannel.position()` và `ByteBuffer.position()` **không phải một biến**:

| Đối tượng | `position()` biểu thị | Đơn vị |
|---|---|---|
| FileChannel | Offset của thao tác I/O tương đối tiếp theo trong **tệp** | Byte của tệp |
| ByteBuffer | Vị trí đọc/ghi tiếp theo trong **vùng nhớ buffer** | Byte của buffer |

Giả sử channel position=100, buffer position=3 và remaining=5. Nếu `channel.read(buffer)` đọc thành công 4 byte:

- Channel position từ 100 thành **104**.
- Buffer position từ 3 thành **7**.
- Bốn byte của tệp được đặt trong buffer tại các index 3, 4, 5, 6.

Một lệnh relative I/O có thể làm **cả hai position tăng cùng lượng**, nhưng việc gọi `buffer.flip()`, `buffer.clear()` hay `buffer.rewind()` **không di chuyển position của channel**. Tương tự, `channel.position(0)` không đổi position của buffer.

Chu trình trạng thái buffer:

| Phương thức | Ý nghĩa |
|---|---|
| `put(...)` | Đưa byte vào buffer và tiến buffer position |
| `flip()` | Đặt limit = position cũ, position = 0; chuẩn bị đọc phần byte vừa ghi |
| `clear()` | Đặt position = 0 và limit = capacity; **không xóa nội dung bộ nhớ** |
| `rewind()` | Đưa position buffer về 0, giữ limit |
| `remaining()` | Số byte còn có thể đọc/ghi trong buffer (`limit - position`) |
| `hasRemaining()` | Kiểm tra còn byte chưa xử lý hay không |

Sai lầm thường gặp là sau khi `put` vào buffer lại `write(buffer)` ngay mà **quên `flip()`**, khiến buffer đang ở cuối phần dữ liệu hoặc ghi cả vùng không mong muốn.

## 5. Đọc theo vị trí hiện tại: `read(ByteBuffer)`

Chữ ký:

```java
int bytesRead = channel.read(destination);
```

Quy trình:

1. Bắt đầu từ `channel.position()`.
2. Đọc byte từ file, đưa vào `destination` từ `destination.position()`.
3. Xử lý tối đa `destination.remaining()` byte trong một lần gọi.
4. Trả số byte **thực tế** đọc, có thể ít hơn remaining hoặc bằng 0; nếu đã đến EOF và không đọc được byte, trả `-1`.
5. Với `n > 0`, channel position và destination position đều tăng `n`.

Ví dụ minh họa (giả định channel đã mở để đọc):

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
int n = channel.read(buffer);
if (n > 0) {
    buffer.flip();
    while (buffer.hasRemaining()) {
        byte b = buffer.get();
        // Xử lý byte b.
    }
}
```

**Một lời gọi `read` không đảm bảo lấp đầy buffer.** Nếu định dạng yêu cầu đọc chính xác N byte, dùng vòng lặp kiểm tra số byte đã nhận và phát hiện EOF trước khi đủ N byte:

```java
static void readFully(FileChannel channel, ByteBuffer destination)
        throws IOException {
    while (destination.hasRemaining()) {
        int n = channel.read(destination);
        if (n == -1) {
            throw new EOFException("Tệp kết thúc trước số byte yêu cầu");
        }
        if (n == 0) {
            throw new IOException("Lần đọc không tiến thêm byte");
        }
    }
}
```

Đây là **phương thức minh họa**, cần các import `java.io.EOFException`, `java.io.IOException`, `java.nio.ByteBuffer` và `java.nio.channels.FileChannel`. Chính sách báo lỗi khi `n == 0` của ví dụ tránh lặp vô hạn. Ứng dụng có provider đặc biệt có thể chọn retry có giới hạn và quản lý liveness khác. Vẫn phải xử lý trường hợp tệp bị thu ngắn hay dữ liệu bị thay đổi bởi tiến trình khác trong lúc đọc.

Nếu muốn đọc toàn bộ đến EOF (không biết trước kích thước), thường dùng vòng lặp `read`, xử lý mỗi khối sau `flip` và chuẩn bị lại buffer. Khi `read` trả `-1` thì dừng; không coi buffer capacity là số byte dữ liệu hợp lệ.

## 6. Ghi theo vị trí hiện tại: `write(ByteBuffer)`

Chữ ký:

```java
int bytesWritten = channel.write(source);
```

Với `n > 0` byte thực sự ghi được:

- Tệp được ghi tại current position, có thể đè byte đã tồn tại.
- Channel position tăng `n`.
- `source.position()` tăng `n` vì `write` đã tiêu thụ byte từ buffer.
- Kích thước tệp có thể tăng nếu phần ghi vượt vị trí EOF cũ.

Ví dụ gửi ba byte ASCII (đoạn minh họa, channel phải có quyền ghi):

```java
ByteBuffer source = ByteBuffer.wrap(new byte[]{'A', 'B', 'C'});
while (source.hasRemaining()) {
    int n = channel.write(source);
    if (n == 0) {
        throw new IOException("Lần ghi không tiến thêm byte");
    }
}
```

Khi vòng lặp kết thúc thành công, buffer không còn remaining; position channel tăng đúng 3 byte so với lúc bắt đầu. Không dựa vào giả định một lần `write(source)` luôn ghi đủ toàn bộ nội dung.

Mẫu ghi nhiều primitive:

```java
ByteBuffer record = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
record.putInt(2026);
record.putLong(1000L);
record.flip();
while (record.hasRemaining()) {
    channel.write(record);
}
```

Ở đây buffer chứa 12 byte (4 + 8). Nếu file format cần little-endian, đặt `record.order(ByteOrder.LITTLE_ENDIAN)` **trước** khi put và dùng cùng byte order lúc giải mã. Offset channel vẫn đo bằng byte, không đo bằng số lượng giá trị `int`/`long`.

## 7. Ví dụ Java 17+ hoàn chỉnh

Chương trình tạo tệp tạm, ghi `ABCDEF`, di chuyển về đầu và đọc hai byte, đọc riêng byte ở offset 4 bằng positional I/O, sau đó ghi đè `CD` bằng `XY` từ **current position=2**.

Lưu thành **`FileChannelPositionDemo.java`**:

```java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelPositionDemo {

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("file-channel-position-", ".bin");

        try {
            try (FileChannel channel = FileChannel.open(
                    file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer initial = ByteBuffer.wrap(
                        "ABCDEF".getBytes(StandardCharsets.US_ASCII));

                writeFully(channel, initial);
                System.out.println("After write: position="
                        + channel.position() + ", size=" + channel.size());

                channel.position(0);
                ByteBuffer firstTwo = ByteBuffer.allocate(2);
                readFully(channel, firstTwo);
                firstTwo.flip();
                System.out.println("Read: "
                        + StandardCharsets.US_ASCII.decode(firstTwo)
                        + ", position=" + channel.position());

                ByteBuffer atFour = ByteBuffer.allocate(1);
                readFullyAt(channel, atFour, 4L);
                atFour.flip();
                System.out.println("Absolute read: "
                        + StandardCharsets.US_ASCII.decode(atFour)
                        + ", position=" + channel.position());

                ByteBuffer change = ByteBuffer.wrap(
                        "XY".getBytes(StandardCharsets.US_ASCII));
                writeFully(channel, change);
                System.out.println("After overwrite: position="
                        + channel.position());

                channel.position(0);
                ByteBuffer whole = ByteBuffer.allocate(6);
                readFully(channel, whole);
                whole.flip();
                System.out.println("Final: "
                        + StandardCharsets.US_ASCII.decode(whole)
                        + ", position=" + channel.position());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // Relative I/O: channel position và buffer position cùng tiến.
    private static void readFully(FileChannel channel, ByteBuffer target)
            throws IOException {
        while (target.hasRemaining()) {
            int n = channel.read(target);
            if (n < 0) {
                throw new EOFException("Not enough bytes");
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source)
            throws IOException {
        while (source.hasRemaining()) {
            int n = channel.write(source);
            if (n == 0) {
                throw new IOException("Write made no progress");
            }
        }
    }

    // Positional I/O không đổi channel.position().
    private static void readFullyAt(
            FileChannel channel, ByteBuffer target, long offset)
            throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int n = channel.read(target, position);
            if (n < 0) {
                throw new EOFException("Not enough bytes at offset " + offset);
            }
            if (n == 0) {
                throw new IOException("Positional read made no progress");
            }
            position += n;
        }
    }
}
```

Biên dịch và chạy tại thư mục chứa mã nguồn:

```bash
javac FileChannelPositionDemo.java
java FileChannelPositionDemo
```

**Stdout chính xác khi có quyền tạo/đọc/ghi/xóa tệp tạm:**

```text
After write: position=6, size=6
Read: AB, position=2
Absolute read: E, position=2
After overwrite: position=4
Final: ABXYEF, position=6
```

Từng bước:

1. Ghi sáu byte bắt đầu tại vị trí 0, nên `channel.position() == 6` và `size() == 6`.
2. `position(0)` đưa con trỏ về đầu, không làm mất dữ liệu. Đọc `AB` làm con trỏ tăng lên 2.
3. `readFullyAt(...,4L)` đọc byte `E`, **giữ nguyên vị trí channel=2**.
4. Ghi `XY` bằng relative write từ vị trí 2, thay hai byte `CD`, khiến position=4.
5. Di chuyển về 0 rồi đọc sáu byte cho ra `ABXYEF`; sau đọc position=6.

Ví dụ dùng vòng lặp đầy đủ vì `read/write` có thể trả ít byte hơn kích thước buffer. Hàm positional tăng biến `position` cục bộ sau từng lượt để nếu đọc phân mảnh vẫn lấy tiếp các byte kế tiếp, thay vì đọc đi đọc lại cùng offset.

## 8. Relative và positional I/O: khác biệt về position

```java
int n1 = channel.read(dst);         // Dùng current position.
int n2 = channel.read(dst, 128L);   // Đọc từ offset 128.

int n3 = channel.write(src);        // Dùng current position.
int n4 = channel.write(src, 256L);  // Ghi từ offset 256.
```

Đây là đoạn minh họa; giả định các biến được khai báo và channel có quyền tương ứng.

| Phương thức, với n byte thành công | Điểm bắt đầu trong file | FileChannel.position sau gọi | ByteBuffer.position sau gọi |
|---|---|---|---|
| `read(dst)` | Current position trước gọi | Tăng n | Tăng n |
| `write(src)` | Current position trước gọi | Tăng n | Tăng n |
| `read(dst, offset)` | Offset truyền vào | **Không đổi** | Tăng n |
| `write(src, offset)` | Offset truyền vào | **Không đổi** | Tăng n |
| `position(offset)` | Không có I/O | Đặt bằng offset | Không đổi |

Với positional read/write, **buffer vẫn tiến position**, dù channel không tiến. Đây là nhầm lẫn thường gặp khi tái sử dụng một buffer qua nhiều offset.

Nếu ứng dụng đang quét một tệp bằng relative read nhưng cần cập nhật header ở offset 0, positional write có thể phù hợp để không làm mất điểm đang quét. Vẫn cần xử lý ghi đè đúng cấu trúc tệp, quyền truy cập và độ dài dữ liệu.

## 9. EOF, short I/O và số byte thực tế

### EOF khi đọc

Khi file position đã ở cuối tệp và buffer còn sức chứa, `read(buffer)` thường trả `-1`. Nó **không tự cuộn position về đầu**. Muốn đọc lại, phải `channel.position(0)` và chuẩn bị trạng thái buffer đúng.

Nếu `read` trả `0`, không nên vội coi đó là EOF: đây là “chưa chuyển byte nào trong lời gọi đó”, có thể xảy ra khi buffer không còn remaining hoặc tình huống provider cụ thể. Khi `remaining()==0`, không cần gọi read.

### Short read / short write

Ví dụ cần đọc 4096 byte nhưng chỉ nhận 1024 trong lượt đầu. Đó **không nhất thiết là lỗi**. Hàm tiếp tục khi buffer còn remaining, sử dụng position đã tự tiến. Tương tự khi ghi: `write` trả về số byte thực ghi, nên luôn kiểm tra `hasRemaining()` nếu giao thức yêu cầu đủ khối.

Với positional I/O cần tự cập nhật offset:

```java
long offset = 1000L;
while (buffer.hasRemaining()) {
    int n = channel.read(buffer, offset);
    if (n == -1) {
        throw new EOFException("Unexpected EOF");
    }
    if (n == 0) {
        throw new IOException("No progress");
    }
    offset += n;  // Bắt buộc: current file position không tự tiến.
}
```

Đây là đoạn minh họa, yêu cầu buffer đã được chuẩn bị với đúng remaining. **Không** dùng nguyên offset 1000 cho mọi vòng lặp; nếu vậy sẽ lặp lại dữ liệu đầu đoạn khi lần đọc đầu chỉ xử lý một phần.

## 10. Ghi đè, nối cuối và thay đổi kích thước

### Ghi đè ở giữa tệp

Nếu tệp là `ABCDE`, gọi `channel.position(1)` rồi ghi đủ `XY`:

```text
Trước: A B C D E
Offset 0 1 2 3 4

Sau:   A X Y D E
             ^
        position = 3
```

Kích thước vẫn là 5. `FileChannel.write` không tự chèn hai byte và đẩy `BCDE` sang phải. Khi định dạng yêu cầu chèn thật sự, ứng dụng phải tự di chuyển/viết lại phần đuôi.

### Ghi ở cuối

Trong trường hợp một writer đơn giản:

```java
channel.position(channel.size());
ByteBuffer tail = ByteBuffer.wrap(new byte[]{'F', 'G'});
while (tail.hasRemaining()) {
    channel.write(tail);
}
```

Đoạn này không bảo đảm **append nguyên tử** với nhiều writer khác vì `size()`, `position(...)` và `write(...)` là các thao tác riêng. Nếu cần semantics append, cân nhắc mở channel với `StandardOpenOption.APPEND` và ràng buộc môi trường; APPEND không thích hợp cho sửa tệp tại offset tùy ý và không kết hợp với READ.

### Position vượt EOF

Giả sử size=10, `channel.position(100)` là hợp lệ và **không** làm size thành 100. Đọc từ đó có thể trả `-1`; ghi ở vị trí đó có thể làm tệp dài ra, tạo khoảng trống từ EOF cũ tới vùng vừa ghi. Nội dung khoảng trống phụ thuộc hệ thống và không nên dựa vào nó như phần dữ liệu được khởi tạo bởi ứng dụng.

### `truncate`, `size` và `force`

- `size()`: lấy số byte hiện có, không thay position.
- `truncate(newSize)`: cắt phần đuôi nếu size cũ lớn hơn newSize; nếu position hiện tại lớn hơn newSize thì position được giảm xuống newSize. Nếu size cũ đã nhỏ hơn newSize, `truncate` **không mở rộng** tệp.
- `force(metaData)`: yêu cầu đồng bộ nội dung tệp (và metadata nếu `true`) xuống hệ lưu trữ theo ngữ nghĩa API; không phải thao tác thay position và không tự biến nhiều cập nhật thành một giao dịch nguyên tử.

```java
channel.position(18);
channel.truncate(10);
// Khi file trước truncate lớn hơn 10:
// channel.position() trở thành 10 nếu trước đó là 18.
```

Đoạn này mang tính minh họa; channel cần quyền ghi và filesystem phải cho phép thao tác.

## 11. An toàn khi nhiều luồng cùng dùng một channel

Một `FileChannel` có một current position chung đối với các thành phần cầm **cùng instance**. Ví dụ hai tác vụ:

```text
Tác vụ A: channel.position(100)
Tác vụ B: channel.position(500)
Tác vụ A: channel.read(bufferA)  ← có thể đọc từ offset 500.
```

Dù các thao tác I/O trên FileChannel có các quy tắc phối hợp nội bộ, **chuỗi nhiều lời gọi** `position(offset)` rồi `read/write(buffer)` của ứng dụng **không tự động nguyên tử** trước luồng khác.

Các cách giải quyết phụ thuộc nhu cầu:

- Chỉ có **một owner** thực hiện các thao tác relative I/O tuần tự.
- Khóa cả cụm `position + read/write` khi buộc chia sẻ cùng channel.
- Dùng `read(buffer, offset)` / `write(buffer, offset)` cho các tác vụ có offset riêng, để không tranh chấp current position.
- Có thể mở channel riêng nếu kiến trúc phù hợp; mỗi channel có position riêng, nhưng vẫn chia sẻ tệp và không bảo đảm chống ghi trùng hay truncate đồng thời.

Ngay cả positional I/O cũng cần đồng bộ khi các tác vụ cùng sửa một vùng, thay đổi độ dài file, hoặc đọc dữ liệu phải nhất quán trong nhiều bước. Dùng offset rõ ràng giảm rủi ro về con trỏ; nó không thay thế thiết kế đồng thời.

## 12. Các lỗi thường gặp

1. **Quên đưa channel về đầu trước khi đọc lại.** Sau khi ghi, current position thường ở cuối; gọi `position(0)` khi cần đọc từ đầu.
2. **Cho rằng read/write luôn xử lý đủ buffer.** Kiểm tra return value, `hasRemaining()` và lặp khi cần.
3. **Không xử lý EOF.** `-1` khác `0`; với bản ghi cố định, thiếu byte là điều kiện lỗi hoặc recovery rõ ràng.
4. **Quên `flip()` trước khi đưa buffer vừa put ra channel.** Buffer.position/limit sai dẫn tới ghi sai lượng byte.
5. **Gọi `clear()` rồi tưởng dữ liệu cũ bị xóa.** `clear()` chỉ thay trạng thái buffer.
6. **Nhầm position channel với position buffer.** Chúng độc lập dù một lần relative I/O có thể tăng cả hai.
7. **Nhầm hai overload read/write.** Overload có offset không thay current position, nhưng vẫn tiến buffer position.
8. **Dùng một offset cố định khi lặp positional read/write.** Phải tăng offset bằng số byte đã xử lý.
9. **Tưởng ghi giữa file là chèn.** Nó ghi đè byte hiện hữu.
10. **Nhầm position và size.** `position(size)` không thể bảo đảm append nguyên tử với writer khác.
11. **Tưởng `truncate` có thể mở rộng file.** Nó chỉ cắt nếu size cũ lớn hơn tham số.
12. **Mở với `TRUNCATE_EXISTING` khi muốn giữ dữ liệu.** Chế độ mở này có thể làm rỗng tệp.
13. **Dùng APPEND khi cần random overwrite.** Append có semantics riêng, không phù hợp đặt offset ghi tùy ý.
14. **Coi offset bằng chỉ số ký tự UTF-8.** Offset tính bằng byte, một ký tự có thể nhiều byte.
15. **Cho nhiều thread tự `position + read/write` trên cùng channel.** Cặp thao tác không nguyên tử; cần owner, khóa hoặc positional I/O.
16. **Quên đóng channel.** Dùng try-with-resources và xử lý `IOException`, `ClosedChannelException` theo tình huống.

## 13. Tình huống áp dụng

**Đọc tuần tự tệp log:** relative `read(buffer)` phù hợp vì mỗi lần đọc tự tiến qua phần đã xử lý. Chuẩn bị buffer đúng cách và phân tích ranh giới bản ghi/charset riêng.

**Ghi lần lượt header và payload:** ghi đủ buffer header rồi buffer payload bằng relative `write`. Position tự tiến đúng số byte thực ghi; không cần tính offset cộng tay nếu chỉ ghi nối theo thứ tự trong một writer.

**Sửa header khi đang đọc payload:** dùng positional `write(src, headerOffset)` để không làm đổi con trỏ đọc payload. Nhưng vẫn cần quản lý tính nhất quán của format nếu thao tác đọc/ghi xen kẽ.

**Đọc nhiều trang tệp theo offset độc lập:** positional I/O giúp mỗi tác vụ có offset tường minh, tránh `position + read` trên một con trỏ chung; chú ý việc tăng offset khi read ngắn.

**Tạo file record fixed-size:** có thể dùng `channel.position(headerSize + index * recordSize)` rồi relative `read/write` trong logic một owner. Nếu có nhiều tác vụ truy cập các record song song, positional I/O thường làm quan hệ offset rõ hơn. Tính toán offset bằng `long` và tránh tràn số khi index đến từ input không tin cậy.

## 14. Câu hỏi tự kiểm tra

**1.** Tệp dài 10 byte, channel position=3, buffer còn chỗ cho 4 byte. Nếu relative `read` thực sự đọc đủ 4 byte, position mới là bao nhiêu?  
**Đáp án:** 7; buffer position cũng tăng 4 so với trước gọi.

**2.** Sau đó `channel.read(dst, 1L)` đọc 2 byte. Current position channel có đổi không?  
**Đáp án:** Không. Vẫn 7. Chỉ buffer position tăng 2.

**3.** Tệp `ABCDE`, position=1, ghi tương đối đủ `XY`. Kết quả là gì?  
**Đáp án:** `AXYDE`, position=3, kích thước vẫn 5.

**4.** `buffer.flip()` có đưa current file position về 0 không?  
**Đáp án:** Không. Nó chỉ chuyển chế độ của buffer. Muốn channel về 0, gọi `channel.position(0L)`.

**5.** Đã ghi đến cuối và `read(dst)` trả `-1`. `dst.capacity()` có cho biết số byte đọc thành công không?  
**Đáp án:** Không. Kích thước buffer không phải số byte dữ liệu hợp lệ. Phải dựa vào return value và position/limit trước–sau.

**6.** Vì sao không nên để hai thread tự gọi `position(offset)` rồi `read(buffer)` trên cùng channel?  
**Đáp án:** Thread khác có thể đổi position giữa hai lời gọi; cụm lệnh không nguyên tử. Sử dụng positional read hoặc đồng bộ phù hợp.

## 15. Tóm tắt

- **`FileChannel.read(ByteBuffer)` / `write(ByteBuffer)` đọc ghi tại current position và tiến con trỏ theo byte xử lý thực tế.**
- **`position(long)` chỉ di chuyển nơi thao tác tiếp theo bắt đầu; `size()` là kích thước tệp.**
- **FileChannel và ByteBuffer có position riêng.** `flip/clear` tác động buffer, không tác động con trỏ file.
- **`read/write(buffer, offset)` không đổi channel position** nhưng vẫn làm buffer position tiến theo số byte xử lý.
- **Short I/O và EOF phải được xử lý tường minh**; các vòng lặp đọc/ghi theo đúng độ dài là nền tảng của xử lý nhị phân đáng tin.
- **Tránh tranh chấp con trỏ chung khi đa luồng** bằng mô hình một owner, đồng bộ phù hợp hoặc positional I/O.

### Tham khảo

- Java SE 17 `FileChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 17 `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 `StandardOpenOption`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/StandardOpenOption.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-003">Đọc ghi tại vị trí tuyệt đối mà không đổi position</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel: đọc/ghi tại vị trí tuyệt đối mà không đổi position

## 1. Bài toán: truy cập một vùng tệp mà vẫn giữ nguyên vị trí hiện tại

Trong Java NIO, `java.nio.channels.FileChannel` cung cấp hai kiểu thao tác đọc/ghi:

- **Theo vị trí hiện tại của channel**: `read(ByteBuffer dst)` và `write(ByteBuffer src)`.
- **Theo vị trí tuyệt đối được chỉ định** (*positional I/O*): `read(ByteBuffer dst, long position)` và `write(ByteBuffer src, long position)`.

Điểm khác biệt cốt lõi: **hai phương thức có đối số `long position` thực hiện I/O tại offset byte được truyền vào, nhưng không sửa vị trí hiện tại của chính `FileChannel`**. Chúng rất hữu ích khi cần đọc header, cập nhật một bản ghi hoặc lấy một đoạn dữ liệu ở offset xác định mà không làm lệch luồng xử lý tuần tự đang dùng chung channel.

Phải phân biệt ba giá trị khác nhau:

1. **`channel.position()`**: offset byte hiện tại của *tệp* trên channel; được sử dụng và thường thay đổi bởi `read(dst)`/`write(src)` không truyền offset.
2. **Tham số `position` trong `read(dst, position)` hoặc `write(src, position)`**: offset byte *tuyệt đối* cho lần thao tác đó; **không phải** số byte dịch tương đối so với `channel.position()`.
3. **`buffer.position()`**: vị trí tiếp theo bên trong `ByteBuffer`; **vẫn tăng** khi có byte thực sự được đọc vào hoặc ghi ra buffer, kể cả trong I/O có offset tuyệt đối.

Ví dụ channel đang ở offset 100. Gọi `channel.read(dst, 20)` sẽ tìm đọc từ byte thứ 21 của tệp (offset 20). Sau khi đọc được 8 byte, `channel.position()` vẫn là **100**, trong khi `dst.position()` tăng **8** từ vị trí ban đầu của buffer.

## 2. API và điều kiện sử dụng

Các phương thức trọng tâm của `FileChannel`:

```java
int read(ByteBuffer dst) throws IOException;
int read(ByteBuffer dst, long position) throws IOException;

int write(ByteBuffer src) throws IOException;
int write(ByteBuffer src, long position) throws IOException;

long position() throws IOException;
FileChannel position(long newPosition) throws IOException;
long size() throws IOException;
```

Đây là chữ ký API rút gọn để minh họa, không phải chương trình hoàn chỉnh.

| Phương thức | Vị trí bắt đầu trên tệp | Thay đổi `channel.position()`? | Thay đổi `ByteBuffer.position()`? |
| --- | --- | --- | --- |
| `read(dst)` | Vị trí hiện tại của channel | Có khi đọc được byte | Có khi đọc được byte |
| `write(src)` | Vị trí hiện tại của channel | Có khi ghi được byte | Có khi ghi được byte |
| `read(dst, offset)` | Offset truyền vào | **Không** | **Có** khi đọc được byte |
| `write(src, offset)` | Offset truyền vào | **Không** | **Có** khi ghi được byte |
| `channel.position(newPosition)` | Đổi vị trí channel cho I/O tiếp theo | **Có** | Không |

**Offset là số byte bắt đầu từ 0.** Truyền offset âm cho thao tác positional I/O ném `IllegalArgumentException`. Đừng nhầm offset 0 là “byte thứ 0” theo cách đếm người dùng: đó là **byte thứ nhất** của tệp.

`FileChannel` có thể được tạo bằng `FileChannel.open(path, options...)` hoặc lấy từ các API liên quan như `FileInputStream.getChannel()`, `FileOutputStream.getChannel()`, `RandomAccessFile.getChannel()`. Khả năng đọc/ghi phụ thuộc quyền và mode mở, không chỉ kiểu Java.

Ví dụ mở kênh có quyền đọc và ghi:

```java
try (FileChannel channel = FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
    // read/write với offset tuyệt đối
}
```

Đoạn mã này giả định `path` là một `Path` hợp lệ và đã import các lớp cần thiết. Chế độ `READ`/`WRITE` cần được chỉ định phù hợp với hoạt động; đọc channel không được mở đọc có thể ném `NonReadableChannelException`, ghi channel không được mở ghi có thể ném `NonWritableChannelException`.

## 3. Minh họa hai loại position độc lập

Giả sử tệp chứa tám byte ASCII:

```text
Tệp:      A   B   C   D   E   F   G   H
Offset:   0   1   2   3   4   5   6   7
                                  ^
                         channel.position() = 6
```

Ta tạo `ByteBuffer.allocate(3)` với `buffer.position() == 0` và gọi:

```java
int count = channel.read(buffer, 2);
```

Nếu lời gọi đọc được đủ ba byte:

```text
Tệp được lấy từ offset 2: C D E
Tổng byte đã đọc: 3
channel.position(): 6       (không thay đổi)
buffer.position(): 3        (tăng từ 0 lên 3)
buffer.limit(): 3           (không thay đổi, vì allocate(3))
```

Nếu chỉ đọc được một byte, `count=1` và `buffer.position()=1`. **Không được khẳng định một lần gọi luôn đọc đủ ba byte.**

Với thao tác ghi, giả sử `src` có dữ liệu `X Y`, `src.position()==0`, `src.limit()==2`. Gọi `channel.write(src, 4)` để ghi vào offset 4 sẽ thay byte `E, F` bằng `X, Y` nếu đã ghi đủ hai byte. `channel.position()` vẫn là 6; `src.position()` trở thành 2.

**Positional I/O không làm thay đổi vị trí channel**, nhưng **vẫn có thể thay đổi nội dung tệp, kích thước tệp, và vị trí của buffer**. Nó cũng không tự khóa dữ liệu để ngăn luồng hoặc tiến trình khác sửa đồng thời.

## 4. Ví dụ Java 17+ chạy độc lập: đối chiếu position trước và sau

Ví dụ này tạo tệp tạm chứa năm byte ASCII `ABCDE`, đặt `FileChannel.position(2)`, đọc hai byte từ offset 0 và ghi `XY` vào offset 3.

Để bảo đảm đúng số byte cần xử lý, chương trình lặp khi thao tác chưa hoàn tất. Mỗi lần đọc/ghi tiếp phải tính **offset tuyệt đối ban đầu + tổng số byte đã xử lý**, vì bản thân channel không tự tăng position khi dùng positional I/O.

**Tệp `PositionalFileChannelDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PositionalFileChannelDemo {
    static int readExactly(FileChannel channel, ByteBuffer dst, long offset)
            throws IOException {
        int total = 0;
        while (dst.hasRemaining()) {
            int n = channel.read(dst, offset + total);
            if (n == -1) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
            total += n;
        }
        return total;
    }

    static int writeExactly(FileChannel channel, ByteBuffer src, long offset)
            throws IOException {
        int total = 0;
        while (src.hasRemaining()) {
            int n = channel.write(src, offset + total);
            if (n == 0) {
                throw new IOException("Write made no progress");
            }
            total += n;
        }
        return total;
    }

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("positional-", ".txt");

        try {
            Files.writeString(path, "ABCDE", StandardCharsets.US_ASCII);

            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                channel.position(2);
                System.out.println("Vi tri kenh ban dau: "
                        + channel.position());

                ByteBuffer dst = ByteBuffer.allocate(2);
                int read = readExactly(channel, dst, 0);
                System.out.println("So byte da doc: " + read);
                System.out.println("Vi tri kenh sau doc: "
                        + channel.position());
                System.out.println("Vi tri buffer sau doc: "
                        + dst.position());

                dst.flip();
                System.out.println("Noi dung doc: "
                        + StandardCharsets.US_ASCII.decode(dst));

                ByteBuffer src = ByteBuffer.wrap(
                        "XY".getBytes(StandardCharsets.US_ASCII));
                int written = writeExactly(channel, src, 3);

                System.out.println("So byte da ghi: " + written);
                System.out.println("Vi tri kenh sau ghi: "
                        + channel.position());
                System.out.println("Vi tri buffer sau ghi: "
                        + src.position());
                System.out.println("Kich thuoc tep: " + channel.size());
            }

            System.out.println("Noi dung tep: "
                    + Files.readString(path, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy tại thư mục chứa mã nguồn:

```bash
javac PositionalFileChannelDemo.java
java PositionalFileChannelDemo
```

**Stdout chính xác nếu các thao tác I/O thành công:**

```text
Vi tri kenh ban dau: 2
So byte da doc: 2
Vi tri kenh sau doc: 2
Vi tri buffer sau doc: 2
Noi dung doc: AB
So byte da ghi: 2
Vi tri kenh sau ghi: 2
Vi tri buffer sau ghi: 2
Kich thuoc tep: 5
Noi dung tep: ABCXY
```

Giải thích:

1. `channel.position(2)` đặt offset hiện tại của channel bằng 2.
2. `readExactly(channel, dst, 0)` đọc hai byte từ offset 0 và 1, không tác động tới position=2 của channel. Buffer đọc nhận dữ liệu nên position của **`dst`** tăng từ 0 lên 2.
3. `dst.flip()` làm position của buffer về 0 và limit bằng 2, để decoder đọc đúng `AB`. Phương thức này cũng không đổi vị trí channel.
4. `writeExactly(channel, src, 3)` ghi hai byte `X Y` tại offset 3 và 4. Position của channel vẫn 2; position của **`src`** tăng từ 0 lên 2.
5. Lệnh ghi **thay thế** `D E` trong tệp `ABCDE`, nên tệp thành `ABCXY` và vẫn dài 5 byte.

Như vậy trong cùng một chương trình có **ba vị trí không bị trộn lẫn**: `channel.position()` (đang là 2), `dst.position()` và `src.position()` (đều đã tăng trong các thao tác tương ứng).

## 5. Vì sao phải xử lý partial read và partial write?

Các phương thức `read(ByteBuffer, long)` và `write(ByteBuffer, long)` trả về **số byte thực sự được chuyển trong lần gọi**, không bảo đảm tiêu thụ hết `buffer.remaining()`.

### Đọc theo offset

Một hàm đọc đủ `N` byte từ offset cố định cần duy trì:

\[
nextOffset = startOffset + bytesReadSoFar
\]

Giả sử cần đọc 6 byte từ offset 100 nhưng ba lần `read` lần lượt trả về 2, 3 và 1:

| Lần | Offset truyền vào | Số byte đọc | Tổng đã đọc | Channel position |
| --- | ---: | ---: | ---: | --- |
| 1 | 100 | 2 | 2 | Giữ nguyên |
| 2 | 102 | 3 | 5 | Giữ nguyên |
| 3 | 105 | 1 | 6 | Giữ nguyên |

Nếu **liên tục gọi `read(dst, 100)` với cùng offset 100**, lần thứ hai có thể đọc lại byte đã đọc trước đó vào phần kế tiếp của buffer. Đây là một trong những lỗi dễ mắc nhất khi chuyển từ API đọc tuần tự sang positional I/O.

Đối với `read`:

- `n > 0`: đã nhận `n` byte.
- `n == 0`: lần đó không có tiến độ, cần xem lại khả năng buffer đã hết chỗ, trạng thái tệp hoặc điều kiện I/O.
- `n == -1`: EOF, không còn byte tại vị trí yêu cầu để đọc. Nếu muốn đúng `N` byte, phải xử lý thiếu dữ liệu.

Nếu buffer có `remaining()==0`, không cần gọi I/O: số byte cần chuyển là 0. Các ví dụ `readExactly`/`writeExactly` ở trên dùng vòng `while (hasRemaining())` nên dừng ngay khi đã đủ.

### Ghi theo offset

Với `write(src, startOffset)`, nếu chỉ ghi được `n` byte thì `src.position()` tăng `n`, nhưng lần gọi tiếp theo vẫn phải cung cấp `startOffset + n`. Không làm vậy có thể **ghi đè** những byte vừa chuyển ở đầu vùng đích.

Giả sử cần ghi 5 byte vào offset 200, hai lần ghi trả về 2 rồi 3:

```text
write(src, 200)  --> ghi 2 byte đầu, src.position tăng 2
write(src, 202)  --> ghi 3 byte còn lại
```

Gọi `write(src, 200)` hai lần sẽ không tự biến offset thành 202, vì vị trí tham số là tuyệt đối.

Mẫu vòng lặp an toàn cho bài toán **yêu cầu ghi đủ**:

```java
long startOffset = 200L;
long transferred = 0L;

while (src.hasRemaining()) {
    int n = channel.write(src, startOffset + transferred);
    if (n == 0) {
        throw new IOException("No write progress");
    }
    transferred += n;
}
```

Đây là đoạn mã minh họa cần `src` và `channel` được khai báo, không phải chương trình độc lập. Với `FileChannel` và tệp thường, lỗi không có tiến độ thường cần được xử lý như tình huống bất thường. Cơ chế backoff/đợi hoặc giới hạn số lần thử phải được thiết kế theo lớp channel và yêu cầu ứng dụng, thay vì quay vòng vô hạn.

### Tại sao không dùng `channel.position(...)` trước mỗi thao tác?

Có thể gọi `channel.position(offset); channel.read(buffer);`, nhưng cách đó **thay đổi trạng thái position dùng chung**. Khi cần truy cập một vùng bất kỳ mà bảo toàn position cho thao tác tuần tự khác, dùng trực tiếp `channel.read(buffer, offset)` hoặc `channel.write(buffer, offset)` rõ mục đích hơn.

## 6. Đọc header khi channel vẫn tiếp tục xử lý tuần tự

Một tình huống phổ biến là:

1. Reader đã đọc tuần tự đến offset 512.
2. Ứng dụng muốn kiểm tra lại 16 byte header ở offset 0.
3. Reader sau đó cần tiếp tục từ offset 512.

Nếu tạm gọi `channel.position(0)`, đọc header rồi `channel.position(512)`, chương trình phải tự lưu/khôi phục vị trí và kiểm soát việc truy cập đồng thời. Với positional read:

```java
long previous = channel.position(); // ví dụ 512

ByteBuffer header = ByteBuffer.allocate(16);
int read = channel.read(header, 0);

System.out.println(channel.position() == previous);
```

Lệnh in là `true` nếu những tác động khác không thay đổi position đồng thời. Nhưng **`read` có thể trả về ít hơn 16**, và `header.position()` chỉ tăng bằng số byte đọc được. Nếu cần đủ header, dùng vòng lặp có offset tăng theo lượng đã nhận, kèm kiểm tra EOF như `readExactly` ở ví dụ chạy được.

**Lợi ích chính** là thao tác đọc header không tự gây thay đổi `channel.position()`. Tuy nhiên nội dung tệp vẫn có thể bị tiến trình khác sửa giữa các lần đọc; positional I/O **không bảo đảm snapshot nhất quán**.

## 7. Cập nhật bản ghi cố định bằng positional write

Giả sử một file chứa nhiều bản ghi độ dài cố định:

| Trường | Cách mã hóa | Số byte |
| --- | --- | ---: |
| `id` | `int` big-endian | 4 |
| `quantity` | `int` big-endian | 4 |
| Tổng |  | **8** |

Bản ghi thứ `index` (tính từ 0) bắt đầu tại:

\[
recordOffset = (long) index \times 8
\]

Trường `quantity` bắt đầu ở `recordOffset + 4`. Nếu muốn đổi số lượng của bản ghi có index=2, offset cần ghi là `2 * 8 + 4 = 20`.

Bằng `ByteBuffer` ta có thể mã hóa số nguyên rồi ghi 4 byte tại đúng offset mà không thay đổi channel position.

**Tệp `PositionalRecordDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PositionalRecordDemo {
    private static final int RECORD_SIZE = 8;

    private static void writeAll(
            FileChannel channel, ByteBuffer src, long offset)
            throws IOException {
        long written = 0;
        while (src.hasRemaining()) {
            int n = channel.write(src, offset + written);
            if (n == 0) {
                throw new IOException("Write made no progress");
            }
            written += n;
        }
    }

    private static void readAll(
            FileChannel channel, ByteBuffer dst, long offset)
            throws IOException {
        long read = 0;
        while (dst.hasRemaining()) {
            int n = channel.read(dst, offset + read);
            if (n == -1) {
                throw new IOException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("Read made no progress");
            }
            read += n;
        }
    }

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("records-", ".bin");

        try {
            try (FileChannel channel = FileChannel.open(
                    path, StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {

                ByteBuffer records = ByteBuffer.allocate(3 * RECORD_SIZE);
                records.order(ByteOrder.BIG_ENDIAN);
                records.putInt(101).putInt(5);
                records.putInt(102).putInt(7);
                records.putInt(103).putInt(9);
                records.flip();
                writeAll(channel, records, 0);

                channel.position(6);
                System.out.println("Vi tri truoc: "
                        + channel.position());

                long quantityOffset = 2L * RECORD_SIZE + 4;
                ByteBuffer update = ByteBuffer.allocate(4);
                update.order(ByteOrder.BIG_ENDIAN);
                update.putInt(90).flip();
                writeAll(channel, update, quantityOffset);

                ByteBuffer result = ByteBuffer.allocate(RECORD_SIZE);
                result.order(ByteOrder.BIG_ENDIAN);
                readAll(channel, result, 2L * RECORD_SIZE);
                result.flip();

                System.out.println("Ban ghi index 2: "
                        + result.getInt() + ", " + result.getInt());
                System.out.println("Vi tri sau: "
                        + channel.position());
                System.out.println("Do dai file: " + channel.size());
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PositionalRecordDemo.java
java PositionalRecordDemo
```

**Stdout chính xác trong điều kiện I/O thành công:**

```text
Vi tri truoc: 6
Ban ghi index 2: 103, 90
Vi tri sau: 6
Do dai file: 24
```

Lý do:

- Ba bản ghi, mỗi bản ghi 8 byte, tạo file 24 byte.
- Ban đầu bản ghi index 2 là `id=103, quantity=9` tại offset 16–23.
- `writeAll(..., 20)` thay chính xác 4 byte của `quantity` bằng số nguyên 90 theo big-endian.
- `readAll(..., 16)` đọc lại toàn bộ bản ghi để lấy `103,90`.
- Cả hai thao tác có offset tường minh đều không làm thay đổi `channel.position()` đang là 6.

**Giới hạn của ví dụ:** việc ghi 4 byte và đọc lại chưa tạo thành giao dịch nguyên tử trong trường hợp nhiều writer cùng truy cập. Với file có dữ liệu quan trọng, cần thiết kế kiểm soát khóa, phối hợp writer, xử lý crash và cấu trúc nhất quán riêng. `channel.position()==6` chỉ chứng minh trạng thái con trỏ được bảo toàn; **không chứng minh thao tác cập nhật là atomic**.

## 8. ByteBuffer vẫn thay đổi position, limit không tự thay đổi

Hợp đồng `FileChannel.read(dst, offset)` đọc byte vào buffer từ `dst.position()` hiện tại tới `dst.limit()`:

```text
dst ban đầu: capacity=10, position=3, limit=7
remaining = 4

Nếu read(dst, 100) trả về 2:
dst.position() trở thành 5
dst.limit() vẫn 7
dst.capacity() vẫn 10
channel.position() không đổi
```

Tương tự, `write(src, offset)` tiêu thụ dữ liệu từ `src.position()` đến `src.limit()`, làm position của buffer tăng theo số byte ghi thành công.

Khi chuẩn bị dữ liệu cho channel:

- `put(...)` ghi vào buffer và tăng `buffer.position()`.
- `flip()` chuyển sang chế độ lấy dữ liệu ra: `limit=position` cũ, `position=0`.
- `write(buffer, offset)` tiêu thụ byte từ buffer, tăng `buffer.position()`.
- `clear()` chỉ thiết lập lại trạng thái để dùng lại buffer, **không xóa byte**.
- `compact()` dùng khi cần giữ phần chưa tiêu thụ rồi ghi thêm.

Đặc biệt, **`ByteBuffer.get(index)` / `put(index, value)`** cũng là thao tác tuyệt đối nhưng áp dụng với **chỉ số trong buffer**. Chúng thường không đổi `buffer.position()`; đừng nhầm với `FileChannel.read(dst, fileOffset)`, vốn vẫn thay đổi `dst.position()`. Hai API có chữ “absolute” nhưng trạng thái mà chúng bảo toàn **khác nhau**.

## 9. Những trường hợp biên cần thiết kế rõ

### 9.1. Offset bằng hoặc vượt EOF

Giả sử file dài 10 byte, offset hợp lệ của byte hiện có là 0–9. `channel.read(dst, 10)` với buffer còn chỗ sẽ gặp EOF và thông thường trả về `-1`. `channel.read(dst, 20)` cũng không thể đọc ra byte từ vùng chưa có dữ liệu. **Positional read không tự kéo dài file.**

Cần chú ý khi `dst.remaining()==0`: không có yêu cầu truyền byte. Đừng dùng buffer trống để kiểm tra EOF; hãy kiểm tra `size()` hoặc thử đọc với một buffer còn chỗ, xử lý giá trị trả về theo hợp đồng. Không nên dựa vào giá trị cố định của một lời gọi đọc độ dài 0 làm cách kết luận tệp đã kết thúc.

### 9.2. Ghi vượt EOF

`channel.write(src, offset)` với offset lớn hơn kích thước file có thể mở rộng file nếu có byte thực sự được ghi. Ví dụ file dài 10 và ghi một byte tại offset 20 có thể khiến kích thước mới thành 21. Các byte nằm giữa EOF cũ và vùng vừa ghi **không được bảo đảm có giá trị xác định bởi hợp đồng `FileChannel`**.

Nếu định dạng tệp yêu cầu vùng trống được khởi tạo bằng 0 hoặc giá trị khác, chương trình phải thực hiện việc khởi tạo theo thiết kế. Việc chỉ di chuyển position tuyệt đối mà không ghi byte **không mở rộng tệp**.

### 9.3. Không truy cập offset âm

`read(dst, -1)` hoặc `write(src, -1)` vi phạm điều kiện tham số và ném `IllegalArgumentException`. Nên xác thực offset và độ dài trước khi đưa vào API; đặc biệt chú ý phép cộng `startOffset + bytesTransferred` và phép nhân `recordIndex * recordSize` để không tràn `long`.

Nếu đọc một bản ghi đã tồn tại, kiểm tra dữ liệu còn trong giới hạn, ví dụ:

```java
if (offset < 0 || length < 0
        || offset > channel.size()
        || length > channel.size() - offset) {
    throw new IOException("Record outside file");
}
```

Đây là đoạn mã minh họa, giả định `offset` và `length` là số nguyên `long`, `channel` đã mở. Dạng `length > size - offset` tránh tràn số khi tính `offset + length`, nhưng chỉ kiểm tra **trạng thái tại thời điểm gọi**; một tiến trình khác vẫn có thể sửa file sau đó.

### 9.4. Channel mở với mode không tương thích

`FileChannel.open(path, READ)` không cho phép `write`; `FileChannel.open(path, WRITE)` không cho phép `read`. Các ngoại lệ có thể là `NonWritableChannelException`/`NonReadableChannelException`.

Nếu mở file ở chế độ `APPEND`, hành vi của **positional write** có thể phụ thuộc hệ thống: đặc tả Java nêu hiệu ứng của positional write trong khi mở append là không xác định theo triển khai. **Tránh kết hợp `APPEND` với positional write khi cần bảo đảm ghi đúng offset**; dùng mode ghi thích hợp và tự quy định chiến lược append nếu nghiệp vụ yêu cầu.

### 9.5. Buffer chỉ đọc, channel đóng và lỗi I/O

Đọc byte vào `ByteBuffer` chỉ đọc là không hợp lệ và có thể gây `ReadOnlyBufferException`. Channel đóng có thể gây `ClosedChannelException`; hoạt động I/O cũng có thể gặp lỗi hệ thống, khóa hoặc ngắt luồng.

Dù `isOpen()` trả về `true`, channel có thể bị đóng trước thao tác tiếp theo. Xử lý lỗi tại chính lần `read` hoặc `write` và đóng channel bằng `try-with-resources`.

## 10. Positional I/O và truy cập đồng thời

Positional I/O đặc biệt hữu ích khi nhiều thao tác cần vùng offset khác nhau mà không muốn chạy cặp lệnh `channel.position(x)` → `read/write` qua một con trỏ channel dùng chung.

**Nhưng nó không tự biến mọi thao tác thành an toàn luồng hoặc nguyên tử.**

Cần phân biệt:

1. **Vị trí channel được bảo toàn:** mỗi lệnh `read(buffer, offset)`/`write(buffer, offset)` không sửa `channel.position()`.
2. **Buffer vẫn là trạng thái thay đổi được:** nếu nhiều luồng dùng chung **một `ByteBuffer`**, position/limit của buffer có thể bị thay đổi xen kẽ.
3. **Nội dung tệp vẫn dùng chung:** hai writer cùng ghi vào vùng chồng lấn có thể cạnh tranh; reader có thể gặp dữ liệu thay đổi trong lúc xử lý.
4. **Mức song song của file operation tùy triển khai:** hợp đồng `FileChannel` không bảo đảm rằng tất cả positional I/O chạy đồng thời hoặc được nguyên tử hóa theo bản ghi.

Khi xử lý nhiều luồng, mỗi tác vụ nên có buffer và phạm vi offset riêng, kèm cơ chế phối hợp/khóa khi có vùng dữ liệu chồng lấn. `FileLock` có thể hỗ trợ một số mô hình phối hợp giữa tiến trình, nhưng cần hiểu phạm vi và giới hạn khóa theo hệ điều hành và hệ thống tệp; khóa cũng không thay thế xử lý crash, flush, hoặc giao dịch cập nhật tệp.

**Tại sao quan trọng?** Nếu luồng A gọi `channel.position(100)` rồi chưa kịp `read(dst)`, luồng B thay position thành 200, luồng A có thể đọc sai offset nếu thiếu đồng bộ. Thay bằng `read(dst, 100)` giúp luồng A truyền đúng offset cho thao tác của mình. Tuy vậy, vẫn phải giữ buffer riêng và kiểm soát người ghi dữ liệu.

## 11. Khi nào nên dùng positional I/O?

Các tình huống phù hợp:

| Tình huống | Ý nghĩa của offset tuyệt đối |
| --- | --- |
| Đọc header trong khi vẫn xử lý tuần tự phần thân | Không phải lưu/khôi phục channel position |
| Đọc bản ghi có độ dài cố định | Tính `offset = index * recordSize` |
| Cập nhật một trường nhị phân tại offset biết trước | Ghi đè đúng vùng cần sửa |
| Đọc nhiều đoạn khác nhau theo chỉ mục | Mỗi đoạn dùng offset tường minh |
| Nhiều tác vụ độc lập xử lý các vùng tệp tách biệt | Tránh thay đổi con trỏ tệp do từng lệnh positional I/O |

Không phải lúc nào positional I/O cũng cần thiết. Nếu ứng dụng chỉ tuần tự đọc toàn bộ file một lần, `channel.read(buffer)` trong một vòng lặp đơn giản có thể dễ hiểu hơn. Nếu cần thao tác giống file pointer chung, `channel.position(...)` vẫn là API phù hợp khi có kiểm soát luồng truy cập.

Positional I/O không bắt buộc nhanh hơn. Hiệu năng phụ thuộc hệ thống tệp, thiết bị lưu trữ, cache, kích thước buffer, kiểu truy cập, mức độ song song và bản chất dữ liệu. Khi tối ưu, nên đo với khối lượng công việc thực tế.

## 12. So sánh với `RandomAccessFile.seek` và ByteBuffer absolute get/put

| API | Vị trí chỉ định thuộc về | Tác động lên trạng thái |
| --- | --- | --- |
| `RandomAccessFile.seek(offset)` | File pointer của `RandomAccessFile` | **Đổi** vị trí tệp dùng cho thao tác tiếp theo |
| `FileChannel.position(offset)` | Vị trí hiện tại của channel | **Đổi** vị trí tệp dùng cho thao tác tiếp theo |
| `FileChannel.read(dst, offset)` | Offset tệp riêng của lần đọc | **Giữ** channel position; tăng `dst.position()` khi đọc được byte |
| `FileChannel.write(src, offset)` | Offset tệp riêng của lần ghi | **Giữ** channel position; tăng `src.position()` khi ghi được byte |
| `ByteBuffer.get(index)` | Chỉ số trong buffer | Thường **giữ** `buffer.position()` |
| `ByteBuffer.put(index, byte)` | Chỉ số trong buffer | Thường **giữ** `buffer.position()` |

Nếu `FileChannel` lấy qua `RandomAccessFile.getChannel()`, hai đối tượng có quan hệ về file position. Positional I/O không đổi file position đó, nhưng gọi `seek` hoặc `position(long)` trên một bên có thể ảnh hưởng tới vị trí quan sát từ bên kia. Tránh trộn các API rồi suy diễn chúng có hai con trỏ tệp độc lập.

## 13. Các lỗi phổ biến

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Tưởng `read(dst, offset)` cập nhật `channel.position()` | Lần đọc tuần tự sau vẫn bắt đầu tại offset cũ | Hiểu rõ hai dạng read; chỉ dùng `position(long)` khi thật sự muốn đổi vị trí channel |
| Tưởng positional I/O không đổi **bất kỳ** position nào | Buffer position vẫn thay đổi; xử lý sai dữ liệu | Kiểm tra riêng `channel.position()` và `buffer.position()` |
| Lặp `read(dst, sameOffset)` dù đã đọc được một phần | Dữ liệu bị đọc trùng từ cùng offset | Cộng dồn `bytesRead` vào offset mỗi lần |
| Lặp `write(src, sameOffset)` khi ghi một phần | Byte sau ghi đè byte trước | Dùng `startOffset + bytesWritten` |
| Không `flip` buffer sau khi tự `put` trước lúc ghi ra channel | Ghi sai vùng buffer | `flip()` trước pha tiêu thụ |
| Tưởng một lần `read/write` luôn chuyển hết remaining | Đọc thiếu/ghi thiếu | Lặp có kiểm tra progress, EOF và điều kiện I/O |
| Dùng tham số offset là chỉ số bản ghi | Truy cập sai byte | Nhân `index * recordSize` với số học `long` |
| Ghi offset vượt EOF và tưởng vùng trống tự bằng zero | Dữ liệu trung gian không có giá trị bảo đảm | Khởi tạo byte theo định dạng |
| Dùng `APPEND` với positional write để bảo đảm offset | Hành vi phụ thuộc triển khai | Mở channel với các option đọc/ghi thích hợp, tránh APPEND |
| Dùng chung một `ByteBuffer` từ nhiều luồng | Dễ race trên buffer position/limit | Mỗi tác vụ dùng buffer riêng hoặc đồng bộ |
| Tin positional write là giao dịch nguyên tử | Có thể gặp ghi một phần hoặc dữ liệu chồng lấn | Thiết kế phối hợp writer, phục hồi và nhất quán |

## 14. Bài tập tự kiểm tra

Giả sử file có 10 byte, `channel.position()==7`, và `ByteBuffer dst` có `capacity=6`, `position=1`, `limit=5`. Chương trình gọi:

```java
int n = channel.read(dst, 2);
```

Nếu lần gọi trả về `n==3`, kết quả chắc chắn nào có thể kết luận?

- **Channel position** vẫn là **7**.
- **Buffer position** tăng từ **1** lên **4**.
- **Buffer limit** vẫn là **5**, capacity vẫn 6.
- Ba byte được yêu cầu bắt đầu từ offset **2** của tệp và được đặt vào buffer bắt đầu tại index **1**.
- `dst.remaining()` lúc này bằng `5 - 4 = 1`.

Nếu muốn đọc nốt một byte nữa của đoạn tệp liền kề vào buffer, offset lần đọc tiếp theo phải là **`2 + 3 = 5`**. Lệnh `read(dst, 2)` lần nữa sẽ quay lại offset 2, dù buffer position lúc ấy đã là 4.

Đây là sự khác biệt quan trọng giữa **offset đầu vào cố định của tệp** và **position tự dịch của ByteBuffer**.

## 15. Ghi nhớ

1. **`FileChannel.read(dst, offset)` và `write(src, offset)` thao tác tại offset byte tuyệt đối mà không thay đổi `FileChannel.position()`.**
2. **`ByteBuffer.position()` vẫn di chuyển** theo số byte đã đọc/ghi thành công. Dữ liệu phải được chuẩn bị đúng `position/limit`.
3. Positional I/O có thể chỉ chuyển **một phần** buffer; nếu cần làm đủ, lặp với **`offset + tổng byte đã chuyển`**, xử lý EOF và lỗi không có tiến độ.
4. Positional write có thể thay đổi kích thước tệp, và dùng positional I/O không tạo tính nhất quán hay nguyên tử tự động.
5. Chú ý quyền mở channel, offset không âm, tràn số, EOF, APPEND và các truy cập đồng thời.
6. Khi muốn **đổi vị trí hiện tại** của channel, dùng `position(long)`; khi muốn **truy cập một offset mà giữ vị trí channel**, dùng các overload có tham số `long position`.

**Tài liệu tham khảo:** Java SE 17 API — `java.nio.channels.FileChannel`, `java.nio.ByteBuffer`, `java.nio.file.StandardOpenOption` và `java.io.RandomAccessFile`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-004">size, truncate và giới hạn thay đổi độ dài</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel: size, truncate và giới hạn thay đổi độ dài tệp

## 1. Kích thước tệp và vị trí channel là hai khái niệm khác nhau

Trong Java NIO, `java.nio.channels.FileChannel` cho phép truy cập tệp theo byte, đọc và ghi tại các vị trí nhất định, đồng thời truy vấn hoặc thu nhỏ kích thước. Ba khái niệm chính:

- `size()`: **độ dài tệp hiện tại**, tính bằng byte, trả về `long`.
- `position()` và `position(long)`: **vị trí hiện tại của channel** để đọc/ghi tiếp; vị trí không nhất thiết trùng độ dài.
- `truncate(long)`: **cắt phần đuôi** nếu tệp lớn hơn kích thước yêu cầu; không mở rộng tệp.

Ví dụ tệp có tám byte ASCII:

~~~text
Index:        0 1 2 3 4 5 6 7 | 8
Content:      A B C D E F G H | EOF
Size = 8
Position có thể là 0, 3, 8, 100...
~~~

**Đặt `position(100)` không tạo thêm 92 byte.** Position là con trỏ logic. Độ dài thường thay đổi do ghi dữ liệu vượt EOF hoặc do cắt tệp.

`FileChannel` triển khai `SeekableByteChannel` và bổ sung các thao tác đọc/ghi tại offset tường minh, mapping, file lock, và đồng bộ dữ liệu (`force`). Bài này tập trung vào **kích thước và việc thay đổi kích thước** của tệp, không dùng position làm đại diện cho size.

## 2. Mở FileChannel đúng chế độ để thay đổi tệp

Ví dụ mở một tệp đã có, vừa đọc vừa ghi:

~~~java
Path path = Path.of("data.bin");
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {
    System.out.println(channel.size());
}
~~~

Đây là mảnh mã minh họa, cần import `java.nio.file.*` và `java.nio.channels.FileChannel`, cũng như một tệp `data.bin` đang tồn tại.

Các tùy chọn cần phân biệt:

| Tùy chọn | Tác dụng |
| --- | --- |
| `READ` | Cho phép đọc; không đủ quyền để thay đổi độ dài. |
| `WRITE` | Cho phép ghi/truncate khi filesystem và quyền hệ điều hành cho phép. |
| `CREATE` | Tạo tệp nếu chưa có khi đi cùng tùy chọn ghi thích hợp. |
| `CREATE_NEW` | Chỉ tạo mới; không vô tình dùng tệp đã tồn tại. |
| `TRUNCATE_EXISTING` | Khi mở để ghi, tệp có sẵn bị cắt còn **0 byte ngay lúc mở**. |
| `APPEND` | Ghi nối cuối theo ngữ nghĩa provider; không phù hợp khi cần kiểm soát offset ghi. |

Không truyền options cho `FileChannel.open(path)` thì mặc định là `READ`. Muốn sửa nội dung hoặc gọi `truncate`, phải mở channel có quyền `WRITE` và bảo đảm hệ thống tệp không cấm thao tác.

**`TRUNCATE_EXISTING` khác `channel.truncate(n)`:** option thứ nhất tác động **khi mở**, còn phương thức thứ hai tác động **tại thời điểm được gọi**. Nếu cần sửa vài byte đầu của tệp có nội dung quan trọng, tránh thêm `TRUNCATE_EXISTING` theo thói quen vì nó có thể xóa sạch nội dung ngay lập tức.

Quản lý vòng đời bằng `try-with-resources` để đóng channel sau khi dùng. Khi thao tác với tệp đang được nhiều tiến trình truy cập, việc chỉ đóng channel không thay thế đồng bộ hóa và bảo đảm nhất quán của ứng dụng.

## 3. `size()` trả về số byte nào?

Chữ ký:

~~~java
long size() throws IOException;
~~~

`size()` trả độ dài **logic hiện tại** của tệp mà channel đại diện, không phải vị trí đọc/ghi, số phần tử đang có trong ByteBuffer hay dung lượng đĩa đã cấp phát.

~~~text
Tệp ASCII "HELLO"
size() = 5
position() = 2
~~~

Độ dài có đơn vị **byte**. Với UTF-8, chuỗi tiếng Việt thường có số byte lớn hơn số ký tự nhìn thấy. Không dùng `String.length()` hoặc số dòng văn bản để dự đoán chính xác `size()`, trừ khi đã xác định encoding và tính độ dài byte thực sự.

Kiểu trả về `long` cho phép biểu diễn kích thước tệp lớn hơn giới hạn `int`. Tuy vậy, **hỗ trợ API không bảo đảm hệ thống tệp chấp nhận mọi kích thước trong phạm vi của `long`**. Kích thước tối đa, quota, dung lượng vật lý và quyền ghi phụ thuộc filesystem/provider/hệ điều hành.

Một tệp sparse có thể báo `size()` lớn nhưng không chiếm cùng từng ấy byte lưu trữ vật lý. Tương tự, `size()` là giá trị **ở thời điểm gọi**; tác vụ khác có thể kéo dài hoặc cắt file ngay sau đó. Không coi `size()` là snapshot giao dịch hay cơ chế khóa.

Khi chỉ có `Path`, `Files.size(path)` cũng có thể đọc metadata kích thước. Khi đang thao tác trên một `FileChannel` đã mở, `channel.size()` gắn trực tiếp với cùng kênh đang sử dụng nhưng vẫn không loại bỏ race condition với người ghi khác.

## 4. `position()` có thể khác `size()`, kể cả vượt EOF

FileChannel có hai phương thức:

~~~java
long position() throws IOException;
FileChannel position(long newPosition) throws IOException;
~~~

Ví dụ:

~~~text
File content: ABCDEF
size = 6
position(2) → đọc/ghi tương đối tiếp theo bắt đầu ở offset 2
position(6) → đang ở EOF
position(100) → vượt EOF; file vẫn có size = 6
~~~

Đặt vị trí lớn hơn EOF là hợp lệ. Gọi `read(buffer)` ở vị trí sau EOF của file thông thường sẽ trả `-1` nếu không có dữ liệu; riêng việc tìm vị trí không tự làm file lớn hơn. Đặt vị trí âm sẽ ném `IllegalArgumentException`.

Mỗi lần `channel.read(dst)` thành công với `n` byte, channel position tăng `n`. `channel.write(src)` thành công với `n` byte cũng đẩy channel position tiến tương ứng. Đồng thời, `ByteBuffer.position()` cũng tăng theo số byte đã chuyển. Đây là **hai đối tượng, hai vị trí khác nhau**.

`channel.position(0)` đưa điểm đọc tệp về đầu nhưng không tự gọi `buffer.clear()` hoặc `buffer.flip()`. Ngược lại, `buffer.flip()` chỉ chuẩn bị đọc byte đã nhận vào buffer, không chuyển position của channel.

## 5. Hợp đồng `truncate(long)`: chỉ thu nhỏ

Chữ ký:

~~~java
FileChannel truncate(long size) throws IOException;
~~~

Gọi `truncate(newSize)` áp dụng các quy tắc:

1. Nếu `newSize < oldSize`, tệp mất các byte nằm từ offset `newSize` trở về sau và kích thước thành `newSize`.
2. Nếu `newSize >= oldSize`, độ dài file **không đổi**. Phương thức không điền thêm dữ liệu.
3. Nếu vị trí hiện tại lớn hơn `newSize`, vị trí sẽ được đặt về `newSize`; nếu không lớn hơn thì giữ nguyên.
4. `newSize < 0` gây `IllegalArgumentException`.
5. Không có quyền ghi gây `NonWritableChannelException`; filesystem, thiết bị, quota hoặc các điều kiện I/O khác có thể gây `IOException`.

Ví dụ file `ABCDEFGH` (8 byte):

~~~text
Offset:     0 1 2 3 4 5 6 7
Before:     A B C D E F G H
            position=3; size=8

truncate(5)

After:      A B C D E
            position=3; size=5
~~~

Nếu trước khi gọi `truncate(5)`, channel đang ở `position=8` thì vị trí sẽ bị đưa về 5. Nếu gọi `truncate(100)` trên tệp 5 byte, **tệp vẫn 5 byte**.

### Cắt tệp về 0 byte

`truncate(0)` cắt toàn bộ nội dung. Đây là thao tác mất dữ liệu thực sự, không phải “đặt position về 0”. Nó đặc biệt nguy hiểm khi nhắm nhầm file log, file dữ liệu nhị phân hoặc file đang được ứng dụng khác sử dụng.

`truncate(n)` trả về chính channel để có thể gọi tiếp phương thức nếu phù hợp; việc trả về đối tượng không có nghĩa thao tác được đặt lịch chạy bất đồng bộ.

## 6. Muốn tăng độ dài: ghi dữ liệu sau EOF

Để tạo thêm byte ở cuối, có thể đặt position tại EOF rồi ghi:

~~~java
channel.position(channel.size());
ByteBuffer bytes = ByteBuffer.wrap(new byte[] {'X', 'Y'});
while (bytes.hasRemaining()) {
    int n = channel.write(bytes);
    if (n == 0) {
        throw new IOException("Write made no progress");
    }
}
~~~

Mảnh minh họa trên cần có `channel` đã mở quyền `WRITE`, import `java.nio.ByteBuffer` và `java.io.IOException`. Chỉ dùng vòng lặp có chính sách xử lý lỗi/tiến độ thích hợp; ví dụ chọn ném lỗi nếu ghi 0 byte thay vì quay mãi không tiến triển.

Nếu tệp dài 4 byte và đặt `position(10)` rồi ghi thành công đúng 1 byte:

~~~text
Before:        A B C D
Offsets:       0 1 2 3 | EOF=4

write 'Z' at offset 10

After:         A B C D ? ? ? ? ? ? Z
Offsets:       0 1 2 3 4 5 6 7 8 9 10 | EOF=11
position=11, size=11
~~~

**Không giả định các byte đánh dấu `?` luôn bằng 0.** Giá trị byte trong khoảng từ EOF cũ đến điểm bắt đầu ghi mới không được hợp đồng chung của `FileChannel` bảo đảm. Filesystem có thể sử dụng sparse holes hoặc cách lưu trữ khác. Nếu dữ liệu yêu cầu khoảng trống chứa byte cụ thể, hãy **ghi rõ nội dung đệm** vào khoảng đó.

Ghi tại offset đã tồn tại là **ghi đè**, không phải chèn làm dịch chuyển phần đuôi:

~~~text
Before: ABCDEFGH   size=8
position(2), write "xy"
After:  ABxyEFGH   size=8
~~~

Do đó, size chỉ tăng khi ghi qua EOF (hoặc tác vụ khác thay đổi file). Những lần ghi thay thế byte bên trong tệp có thể không thay đổi size.

## 7. Ví dụ Java 17+: size, truncate, vị trí vượt EOF và mở rộng

Chương trình dùng tệp tạm, chỉ chứa byte ASCII để không có nhầm lẫn giữa ký tự và byte. Nó không in tên file ngẫu nhiên và không đọc vùng gap chưa xác định, nên stdout ổn định trên default filesystem thông dụng.

Lưu thành `FileChannelLengthDemo.java`:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelLengthDemo {

    static void writeAscii(FileChannel ch, String text) throws IOException {
        ByteBuffer src = ByteBuffer.wrap(
                text.getBytes(StandardCharsets.US_ASCII));
        while (src.hasRemaining()) {
            if (ch.write(src) == 0) {
                throw new IOException("No write progress");
            }
        }
    }

    static String readExactly(FileChannel ch, int bytes) throws IOException {
        ByteBuffer dst = ByteBuffer.allocate(bytes);
        while (dst.hasRemaining()) {
            int n = ch.read(dst);
            if (n == -1) {
                throw new EOFException("Unexpected EOF");
            }
            if (n == 0) {
                throw new IOException("No read progress");
            }
        }
        dst.flip();
        return StandardCharsets.US_ASCII.decode(dst).toString();
    }

    static void state(String title, FileChannel ch) throws IOException {
        System.out.println(title + ": position=" + ch.position()
                + ", size=" + ch.size());
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("length-demo-", ".bin");
        try {
            try (FileChannel ch = FileChannel.open(
                    file,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                state("initial", ch);

                writeAscii(ch, "ABCDEFGH");
                state("written", ch);

                ch.position(3);
                ch.truncate(5);
                state("truncate 5", ch);

                ch.position(100);
                state("seek 100", ch);

                ch.truncate(4);
                state("truncate 4", ch);

                ch.truncate(20);
                state("truncate 20", ch);

                ch.position(0);
                System.out.println("prefix=" + readExactly(ch, 4));
                state("after read", ch);

                ch.position(6);
                writeAscii(ch, "Z");
                state("write beyond EOF", ch);

                ch.truncate(2);
                state("truncate 2", ch);

                ch.position(0);
                System.out.println("final=" + readExactly(ch, 2));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac FileChannelLengthDemo.java
java FileChannelLengthDemo
~~~

Stdout:

~~~text
initial: position=0, size=0
written: position=8, size=8
truncate 5: position=3, size=5
seek 100: position=100, size=5
truncate 4: position=4, size=4
truncate 20: position=4, size=4
prefix=ABCD
after read: position=4, size=4
write beyond EOF: position=7, size=7
truncate 2: position=2, size=2
final=AB
~~~

Diễn giải các mốc:

1. Tệp tạm mới có size 0; mở `READ, WRITE` không phá hủy nội dung. Position ban đầu bằng 0.
2. Ghi đủ 8 byte `ABCDEFGH`: position tăng lên 8, size thành 8.
3. Seek đến offset 3 rồi `truncate(5)`: còn `ABCDE`, size=5, position vẫn 3 vì nằm trước giới hạn mới.
4. `position(100)` không tăng size; phép in tiếp theo cho thấy size vẫn bằng 5.
5. `truncate(4)` làm mất `E` và đưa position từ 100 về 4.
6. `truncate(20)` không làm tệp dài ra; size và position trong trường hợp này đều vẫn 4.
7. Đọc từ đầu được `ABCD`. Hàm `readExactly` đọc đúng số byte yêu cầu, kiểm tra EOF, rồi `flip` buffer trước khi decode.
8. Ghi `Z` tại offset 6: size thành 7, position thành 7. Ví dụ không suy đoán nội dung ở offset 4 và 5.
9. `truncate(2)` cắt tệp còn `AB`, kéo position về 2; đọc lại từ đầu xác nhận `AB`.
10. `try-with-resources` đóng channel; `finally` xóa file tạm ngay cả khi thao tác I/O thất bại.

Chương trình dùng cách ném `IOException` khi đọc/ghi không tiến triển. Với kênh không chặn trong hệ thống ứng dụng thực tế, cần cơ chế chờ/tái thử thích hợp. Phần lõi của bài là hợp đồng `FileChannel` với tệp thông thường, không phải thiết kế xử lý non-blocking I/O.

## 8. Các overload có offset riêng và tác động tới size

Ngoài `read(dst)` và `write(src)` sử dụng position chung, `FileChannel` hỗ trợ:

~~~java
int read(ByteBuffer dst, long position);
int write(ByteBuffer src, long position);
~~~

Ở đây `position` truyền vào là offset **riêng của lần gọi**, không làm đổi `channel.position()`. Tuy nhiên `ByteBuffer.position()` vẫn tiến theo số byte đọc/ghi.

Với ghi theo offset:

~~~java
ch.position(2);
ByteBuffer payload = ByteBuffer.wrap(new byte[] {'X', 'Y'});
int count = ch.write(payload, 100);
// ch.position() vẫn là 2.
// ch.size() có thể tăng nếu ghi thành công vượt EOF.
~~~

Đây là mảnh mã minh họa với biến `ch` đã tồn tại và có quyền ghi. `count` có thể nhỏ hơn 2; nếu cần ghi hết, hãy kiểm tra số byte thực tế và lặp với offset mới dựa trên tổng số byte đã ghi. Không lặp cố định cùng offset 100 nếu mục tiêu là hoàn thành phần byte còn lại.

Bảng tác động:

| Phương thức | Đổi position chung? | Có thể đổi size? |
| --- | --- | --- |
| `position(newPosition)` | Có | Không |
| `size()` | Không | Không |
| `read(dst)` | Có khi đọc được byte | Bản thân đọc không mở rộng |
| `write(src)` | Có khi ghi được byte | Có nếu ghi vượt EOF |
| `read(dst, offset)` | Không | Bản thân đọc không mở rộng |
| `write(src, offset)` | Không | Có nếu ghi vượt EOF |
| `truncate(newSize)` | Có thể kéo position xuống | Có thể giảm; không tăng |

Tác vụ khác vẫn có thể thay đổi tệp đồng thời, nên bảng mô tả **tác động trực tiếp** của mỗi lời gọi.

## 9. Những giới hạn thật sự của việc thay đổi độ dài

### Giới hạn filesystem, quota và quyền

Các phương thức dùng `long` không bảo đảm bạn có thể tạo tệp dài tùy ý. Kích thước tối đa phụ thuộc filesystem, runtime, provider, quota, dung lượng, quyền ghi, giới hạn của thiết bị và tài nguyên tiến trình.

Nếu không thể ghi hoặc cắt tệp, thao tác có thể ném `IOException` hoặc các ngoại lệ được API xác định như `NonWritableChannelException`. Tệp trên filesystem chỉ đọc và tệp đang bị kiểm soát bởi tiến trình khác có thể có những hạn chế khác nhau.

### Dữ liệu bị mất khi truncate

`truncate` có thể cắt qua giữa record của file nhị phân/log. Nếu cấu trúc file gồm header, độ dài, checksum, index, footer thì việc thu nhỏ có thể làm cả file không còn hợp lệ. Trước khi truncate cần hiểu **ranh giới dữ liệu hợp lệ** và có cơ chế backup/phục hồi nếu dữ liệu quan trọng.

~~~text
[record A][record B][record C][record D đang ghi dở]
                           ^
                  cuối record C hợp lệ
~~~

Một chiến lược phục hồi có thể xác minh bản ghi hợp lệ cuối cùng và cắt phần dở sau đó. Vị trí truncate phải là offset **theo byte** đã kiểm chứng, không phải số dòng hay số ký tự hiển thị.

### Đồng thời và trạng thái size không ổn định

Hai thread dùng chung một channel có thể tác động lên position chung; hai channel/tiến trình khác nhau có thể cùng sửa tệp. Mẫu:

~~~java
long end = ch.size();
ch.position(end);
ch.write(data);
~~~

là mảnh minh họa và **không phải một giao dịch append nguyên tử** khi có writer khác. Hai bên có thể cùng nhìn một `end` và ghi đè nhau. Cần chiến lược sở hữu channel, đồng bộ truy cập, file lock khi thích hợp hoặc cơ chế append đáp ứng yêu cầu nền tảng.

Tương tự, đọc size rồi dùng nó như một giới hạn bất biến sẽ sai nếu tiến trình khác truncate file ngay sau đó. Nếu cần tính nhất quán cao, hãy phối hợp tại cấp độ ứng dụng và quản lý dữ liệu theo transaction/protocol phù hợp.

### Mapping và các vùng tệp đã cắt bỏ

Nếu ứng dụng đang sử dụng `MappedByteBuffer` trên vùng bị truncate, việc truy cập vùng map không còn được file backing hỗ trợ có thể dẫn tới lỗi nghiêm trọng hoặc ngoại lệ tùy nền tảng/runtime. Không cắt file bên dưới các vùng mapping đang được thread khác sử dụng mà chưa xử lý vòng đời và đồng bộ của mapping.

### Bền vững của dữ liệu

`truncate()` trả về không đồng nghĩa mọi thay đổi đã bền vững trên thiết bị sau mất điện. `FileChannel.force(boolean metaData)` cung cấp yêu cầu đồng bộ các cập nhật phù hợp theo hợp đồng API; mức độ bảo đảm phụ thuộc provider, OS và thiết bị. `force` không biến nhiều thao tác `write` và `truncate` thành một giao dịch nguyên tử.

## 10. Các lỗi phổ biến và cách sửa

**Nhầm 1 — Dùng `truncate` để tăng độ dài.** `truncate(1024)` trên tệp 10 byte không tạo 1.014 byte mới. Phải ghi vượt EOF nếu thực sự cần tăng size.

**Nhầm 2 — Coi position là size.** Đặt `position(100)` không thay đổi `size()`. Hỏi trực tiếp `channel.size()` và nhớ nó có thể thay đổi đồng thời.

**Nhầm 3 — Mở nhầm `TRUNCATE_EXISTING`.** Khi muốn cập nhật tệp cũ, option này có thể xóa nội dung ngay trước khi bạn seek tới offset cần sửa.

**Nhầm 4 — Mở channel chỉ đọc rồi gọi `truncate`.** Cần quyền `WRITE` và filesystem hỗ trợ. Đừng bắt mọi exception như “file không tồn tại”; phải phân biệt lỗi quyền, IO, giá trị âm và trạng thái đóng.

**Nhầm 5 — Đếm offset theo ký tự.** FileChannel làm việc với byte; ký tự UTF-8 có thể chiếm nhiều byte. Với dữ liệu text, cần mã hóa thành byte và xác định ranh giới hợp lệ.

**Nhầm 6 — Ghi giữa file và mong dữ liệu tự chèn.** `write` ghi đè; muốn chèn phải dịch phần đuôi hoặc tạo file mới bằng thuật toán phù hợp.

**Nhầm 7 — Tưởng vùng gap sau khi ghi vượt EOF chắc chắn chứa 0.** Nội dung gap không được API bảo đảm chung; nếu cần định dạng deterministic, ghi byte đệm rõ ràng.

**Nhầm 8 — Bỏ qua kết quả `write`.** Một lần write có thể chưa tiêu thụ toàn bộ buffer. Dùng `hasRemaining()`, số byte trả về, và xử lý trường hợp không tiến triển.

**Nhầm 9 — Cắt file đang được ghi bởi tác vụ khác.** Không có phối hợp, có thể mất record, sai checksum hoặc khiến reader đang đọc phần đuôi thất bại.

## 11. Tự kiểm tra

1. Tệp 12 byte, position=3; gọi `truncate(8)`. Kết quả: **size=8, position=3**.
2. Tệp 12 byte, position=11; gọi `truncate(8)`. Kết quả: **size=8, position=8**.
3. Tệp 4 byte; gọi `truncate(20)`. Kết quả: **size vẫn 4**.
4. Tệp 4 byte; gọi `position(10)`. Kết quả: **size vẫn 4** nếu chưa ghi hoặc bị thay đổi bên ngoài.
5. Tệp 4 byte; ghi thành công một byte ở offset 10. Kết quả: **size=11**; không được giả định byte ở offset 4–9 bằng 0.
6. Tệp `ABCDEFGH`, ghi `xy` tại offset 2. Kết quả **`ABxyEFGH`**, size vẫn 8.
7. Tại sao không dùng `TRUNCATE_EXISTING` khi sửa một header? Vì nó có thể cắt tệp còn 0 byte ngay lúc mở.
8. `write(payload, 100)` có thay đổi `channel.position()` không? **Không**. Có thể thay đổi size nếu ghi qua EOF.
9. Gọi `size()` trước khi một writer khác thao tác có bảo đảm kích thước giữ nguyên không? **Không**; đây chỉ là một phép quan sát.

**Ghi nhớ:** `size()` đo **độ dài hiện tại theo byte**; `position()` xác định **vị trí đọc/ghi tiếp theo**; `truncate(n)` **thu nhỏ** file khi cần và có thể kéo position xuống, nhưng **không mở rộng** file. Muốn tăng độ dài phải ghi dữ liệu ngoài EOF, đồng thời xử lý byte gap, giới hạn môi trường, tính nhất quán và quyền I/O.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-005">transferTo, transferFrom và xử lý số byte chuyển thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel: `transferTo`, `transferFrom` và xử lý số byte chuyển thực tế

## 1. Mục tiêu

`FileChannel` thuộc `java.nio.channels`, cho phép đọc/ghi, truy cập vị trí ngẫu nhiên và chuyển dữ liệu giữa channel. Hai phương thức `transferTo` và `transferFrom` đặc biệt hữu ích khi sao chép dữ liệu từ hoặc vào tệp mà không phải tự tổ chức vòng lặp `ByteBuffer` trong đường đi thông thường.

Điểm cần nhớ ngay: **giá trị `count` là số byte yêu cầu chuyển tối đa, không phải cam kết số byte thực sự đã chuyển**. Mỗi lần gọi trả về một giá trị `long` là **số byte đã chuyển thực tế**. Chương trình muốn chuyển đủ dữ liệu phải kiểm tra giá trị này, cập nhật độ lệch rồi gọi tiếp khi phù hợp.

Sau bài học, bạn có thể:

- Chọn chiều chuyển dữ liệu đúng giữa `transferTo` và `transferFrom`.
- Phân biệt tham số `position` với `FileChannel.position()` và vị trí của channel còn lại.
- Viết vòng lặp xử lý kết quả dương nhỏ hơn yêu cầu và kết quả 0.
- Giải thích giới hạn từ hệ điều hành, EOF, channel không chặn và các điều kiện cạnh tranh.
- Kiểm tra số byte, nội dung, đóng tài nguyên và phát hiện sao chép thiếu dữ liệu.

## 2. API và chiều dữ liệu

Hai chữ ký cốt lõi của Java NIO.2:

~~~java
// Phương thức instance của FileChannel:
long transferTo(long position, long count, WritableByteChannel target)
        throws IOException;

long transferFrom(ReadableByteChannel src, long position, long count)
        throws IOException;
~~~

Đây là **chữ ký API**, không phải một chương trình Java độc lập.

| Phương thức được gọi trên | Chiều dữ liệu | `position` thuộc về | Đối tượng channel còn lại |
| --- | --- | --- | --- |
| `sourceFile.transferTo(position, count, target)` | **Từ** tệp nguồn **đến** channel đích | Vị trí bắt đầu **trong tệp nguồn** `sourceFile` | `WritableByteChannel target` nhận dữ liệu |
| `destinationFile.transferFrom(src, position, count)` | **Từ** channel nguồn **vào** tệp đích | Vị trí bắt đầu **trong tệp đích** `destinationFile` | `ReadableByteChannel src` cung cấp dữ liệu |

Ví dụ sơ đồ:

~~~text
transferTo:
[FileChannel nguồn] --(position trong tệp nguồn, count)--> [WritableByteChannel đích]

transferFrom:
[ReadableByteChannel nguồn] --(count)--> [FileChannel đích ở position]
~~~

**Không hoán đổi ý nghĩa `position`:** Với `transferTo`, `position` là offset đọc; với `transferFrom`, `position` là offset ghi. Cả hai offset này dùng đơn vị **byte** tính từ đầu tệp, không phải chỉ số ký tự.

Các tham số `position` và `count` không được âm; giá trị âm gây `IllegalArgumentException`. Ngoài ra các phương thức có thể ném `IOException` khi truyền dữ liệu thất bại, `ClosedChannelException` khi channel cần thiết đã đóng, hoặc các ngoại lệ khác theo loại channel và trạng thái. Đừng chỉ dựa vào một loại exception duy nhất.

### Giá trị trả về có ý nghĩa gì?

~~~java
long actual = sourceFile.transferTo(offset, requested, target);
~~~

`actual` là lượng dữ liệu thực sự chuyển thành công trong **lần gọi đó**:

- `0 < actual < requested`: chỉ chuyển được một phần; có thể còn dữ liệu.
- `actual == requested`: lần gọi đã chuyển đủ đúng lượng yêu cầu.
- `actual == 0`: không có byte nào được chuyển trong lần này. **Không tự động suy ra đã tới EOF**, nhất là khi channel không chặn hoặc triển khai bị giới hạn.
- Kết quả chuyển thành công nằm trong `[0, requested]`; `actual` không phải tổng byte từ các lần gọi trước và không phải vị trí cuối cùng.

Nếu `requested == 0`, kết quả bằng 0 là bình thường. Khi `position` đọc của `transferTo` nằm ở hoặc vượt quá kích thước tệp hiện tại, cũng không còn byte tại vị trí đó để chuyển.

## 3. Vị trí tệp và vị trí channel sau lệnh chuyển

Điều này dễ gây lỗi hơn chính thao tác sao chép.

Với `source.transferTo(fromOffset, count, sink)`:

- `fromOffset` xác định điểm bắt đầu **đọc từ tệp nguồn**, độc lập với vị trí hiện tại của `source`.
- Lời gọi **không thay đổi** `source.position()`.
- Nếu `sink` là một channel có vị trí, chẳng hạn một `FileChannel`, dữ liệu được ghi từ vị trí hiện tại của `sink` và vị trí của `sink` tăng theo lượng dữ liệu ghi.
- Vì thế lần gọi tiếp theo phải truyền `fromOffset + actual` nếu cần nối tiếp vùng nguồn; gọi lại với cùng offset sẽ đọc lặp byte cũ.

Với `destination.transferFrom(src, toOffset, count)`:

- `toOffset` xác định nơi **ghi vào tệp đích**, độc lập với `destination.position()`.
- Lời gọi **không thay đổi** `destination.position()`.
- Nếu `src` có vị trí (ví dụ `FileChannel`), vị trí đọc của `src` thường tiến lên theo số byte đã đọc.
- Vì thế lần tiếp theo phải truyền `toOffset + actual` vào `transferFrom`; còn vị trí đọc của `src` thường đã được cập nhật và không cần tự tăng lần nữa.

| Thao tác | `position` của FileChannel được gọi | Vị trí của channel đối diện |
| --- | --- | --- |
| `fileSource.transferTo(...)` | **Không đổi** | Channel đích có vị trí sẽ tiến lên khi nhận byte |
| `fileDestination.transferFrom(...)` | **Không đổi** | Channel nguồn có vị trí sẽ tiến lên khi cung cấp byte |

**Ví dụ tính vị trí:** `source.transferTo(100, 40, target)` trả về 15. Lần sau muốn tiếp tục vùng nguồn phải gọi `source.transferTo(115, 25, target)`. Tương tự, `destination.transferFrom(src, 100, 40)` trả về 15 thì offset ghi tiếp là 115. Nếu channel đích ghi của `transferTo` đã được sử dụng trước đó, hãy đặt `target.position(...)` theo mục tiêu thật sự trước khi bắt đầu sao chép.

## 4. Tại sao một lần gọi có thể chuyển ít hơn `count`?

Theo hợp đồng API, `transferTo` và `transferFrom` **có thể chuyển ít hơn lượng yêu cầu**. Các lý do điển hình:

1. **Không đủ dữ liệu:** vùng đọc từ tệp chỉ còn vài byte; với `transferFrom`, channel nguồn có thể đã hết dữ liệu (EOF).
2. **Giới hạn của hệ điều hành hoặc bộ cung cấp channel:** một lời gọi chuyển dữ liệu có thể bị giới hạn kích thước, kể cả khi còn rất nhiều byte. Truyền file lớn không được giả định thành công trong một lời gọi.
3. **Channel không chặn:** đầu đọc chưa có dữ liệu hoặc đầu ghi chưa sẵn sàng nhận thêm, nên có thể chuyển ít hoặc trả 0.
4. **Trạng thái tệp thay đổi đồng thời:** tác vụ khác rút ngắn, mở rộng hoặc chỉnh sửa tệp trong lúc chuyển, khiến kích thước dự kiến không còn phản ánh dữ liệu thực.
5. **Lỗi I/O:** channel có thể thất bại và ném exception giữa quá trình; việc chuyển một số byte trước đó không đồng nghĩa thao tác toàn bộ đã thành công.

Một số triển khai có thể tối ưu transfer bằng đường đi của hệ điều hành (thường gọi là **zero-copy**), giảm việc sao chép qua bộ đệm trong user space. Tuy nhiên, **API không cam kết luôn dùng zero-copy hoặc mọi lần đều chuyển đủ `count`**. Hành vi và hiệu năng phụ thuộc source/target, provider, hệ điều hành và môi trường.

## 5. Vòng lặp chuyển đủ số byte với `transferTo`

Với một tệp nguồn ổn định và channel đích ghi đồng bộ, mẫu xử lý cơ bản:

~~~java
long remaining = source.size();
long offset = 0;

while (remaining > 0) {
    long transferred = source.transferTo(offset, remaining, target);

    if (transferred == 0) {
        throw new IOException("Không có tiến triển khi transferTo");
    }

    offset += transferred;
    remaining -= transferred;
}
~~~

Đây là **đoạn minh họa**, sử dụng các biến `source` (`FileChannel`) và `target` (`WritableByteChannel`) đã được mở, cùng `java.io.IOException`.

**Tại sao cập nhật cả hai biến?** `offset` tăng theo số byte vừa đọc để lần gọi sau không sao chép lại đoạn đã chuyển; `remaining` giảm tương ứng để không đọc vượt yêu cầu. Nếu yêu cầu ban đầu bằng `0`, vòng lặp không chạy.

**Tại sao không `while` vô hạn khi nhận 0?** Nếu gọi liên tục khi không có tiến triển, CPU có thể bị tiêu tốn vô ích. Với ví dụ tệp thường và channel ghi đồng bộ, ném `IOException` làm thao tác lỗi được báo rõ ràng. Trong thiết kế không chặn, phải chờ channel sẵn sàng thông qua cơ chế thích hợp rồi thử lại; không dùng vòng lặp quay liên tục.

Nếu muốn chỉ chuyển một vùng `[start, start + length)` của tệp, đặt `offset = start` và `remaining = length`. Trước khi sao chép, kiểm tra `start >= 0`, `length >= 0` và vùng cần thiết có trong dữ liệu thực; tránh phép cộng `start + length` bị tràn `long` bằng cách so sánh với kích thước theo hướng an toàn (ví dụ `length <= size - start` khi `start <= size`).

## 6. Vòng lặp chuyển đủ số byte với `transferFrom`

Đảo chiều API: phương thức được gọi trên **FileChannel đích**, và `position` là offset ghi.

~~~java
long remaining = expectedBytes;
long destinationOffset = 0;

while (remaining > 0) {
    long transferred = destination.transferFrom(
            source, destinationOffset, remaining);

    if (transferred == 0) {
        throw new IOException(
                "Nguồn hết dữ liệu hoặc không sẵn sàng trong transferFrom");
    }

    destinationOffset += transferred;
    remaining -= transferred;
}
~~~

Đây là **đoạn minh họa**, trong đó `destination` là `FileChannel` ghi, `source` là `ReadableByteChannel` mở để đọc, `expectedBytes` đã được xác định, và đã import `IOException`.

Khi `source` là `FileChannel`, `transferFrom` dùng vị trí hiện tại của nguồn và làm vị trí đó tiến lên. Do đó vòng lặp chỉ tự cập nhật `destinationOffset`; nếu tự gọi thêm `source.position(source.position() + transferred)` thì sẽ **bỏ qua dữ liệu**. Khi `source` là network channel, dữ liệu đến từng đợt có thể khiến `transferred` bằng 0 dù kết nối chưa kết thúc.

**Khác biệt với `transferTo`:** việc ghi của `transferFrom` vào một offset có thể mở rộng tệp đích khi offset nằm trong hoặc sát cuối tệp. Nếu `position` lớn hơn kích thước tệp hiện có, API cho phép không chuyển byte nào. Muốn tạo lỗ trống hoặc ghi ngẫu nhiên, hãy dùng chiến lược định vị/ghi phù hợp và kiểm tra hệ thống tệp.

## 7. Ví dụ Java 17+ hoàn chỉnh: chạy cả hai chiều và xác minh nội dung

Chương trình sau tạo tệp nguồn với dữ liệu ASCII có độ dài cố định 11 byte (`Java NIO.2\n`), sao chép theo cả hai hướng, xác minh số byte đã chuyển, trạng thái vị trí và so sánh nội dung. Nó cố tình xử lý trường hợp **mỗi lời gọi chỉ chuyển một phần** bằng vòng lặp.

Lưu dưới tên **`FileChannelTransferDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;

public class FileChannelTransferDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("channel-transfer-");
        Path sourceFile = directory.resolve("source.txt");
        Path toFile = directory.resolve("copy-to.txt");
        Path fromFile = directory.resolve("copy-from.txt");

        try {
            Files.writeString(
                    sourceFile, "Java NIO.2\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            long transferredTo;
            long sourcePositionAfterTo;
            try (FileChannel source = FileChannel.open(
                         sourceFile, StandardOpenOption.READ);
                 FileChannel target = FileChannel.open(
                         toFile, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {

                long required = source.size();
                long offset = 0;

                while (offset < required) {
                    long n = source.transferTo(
                            offset, required - offset, target);
                    if (n == 0) {
                        throw new IOException(
                                "transferTo không có tiến triển tại " + offset);
                    }
                    offset += n;
                }

                transferredTo = offset;
                sourcePositionAfterTo = source.position();
            }

            long transferredFrom;
            long destinationPositionAfterFrom;
            try (FileChannel source = FileChannel.open(
                         sourceFile, StandardOpenOption.READ);
                 FileChannel target = FileChannel.open(
                         fromFile, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {

                long required = source.size();
                long destinationOffset = 0;

                while (destinationOffset < required) {
                    long n = target.transferFrom(
                            source, destinationOffset,
                            required - destinationOffset);
                    if (n == 0) {
                        throw new IOException(
                                "transferFrom không có tiến triển tại "
                                        + destinationOffset);
                    }
                    destinationOffset += n;
                }

                transferredFrom = destinationOffset;
                destinationPositionAfterFrom = target.position();
            }

            boolean equalCopies = Files.mismatch(sourceFile, toFile) == -1
                    && Files.mismatch(sourceFile, fromFile) == -1;

            System.out.println("Source bytes: " + Files.size(sourceFile));
            System.out.println("transferTo bytes: " + transferredTo);
            System.out.println("transferFrom bytes: " + transferredFrom);
            System.out.println(
                    "Source position after transferTo: "
                            + sourcePositionAfterTo);
            System.out.println(
                    "Destination position after transferFrom: "
                            + destinationPositionAfterFrom);
            System.out.println("Copies match: " + equalCopies);
        } finally {
            Files.deleteIfExists(toFile);
            Files.deleteIfExists(fromFile);
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 FileChannelTransferDemo.java
java FileChannelTransferDemo
~~~

Khi việc chuyển tệp thông thường hoàn tất, **stdout** là:

~~~text
Source bytes: 11
transferTo bytes: 11
transferFrom bytes: 11
Source position after transferTo: 0
Destination position after transferFrom: 0
Copies match: true
~~~

### Đọc kỹ ví dụ

1. Chuỗi `"Java NIO.2\n"` có 11 byte UTF-8: 10 ký tự ASCII hiển thị và một ký tự xuống dòng. Trong ví dụ này, số ký tự và byte trùng nhau vì toàn bộ là ASCII; **điều đó không đúng với mọi chuỗi Unicode**.
2. `source.size()` được lấy làm số byte dự kiến. Với nguồn ổn định, vòng lặp sao chép cho đến khi tổng `n` đạt `required`; nếu một lần trả 0, chương trình báo lỗi thay vì coi là thành công hoặc lặp vô hạn.
3. `transferTo(offset, remaining, target)` giữ nguyên `source.position()`. Vì nguồn mới mở ở vị trí 0 và không có thao tác nào thay đổi nó, giá trị in ra bằng 0.
4. `target.transferFrom(source, destinationOffset, remaining)` dùng offset ghi tường minh và không thay đổi `target.position()`. Vì channel đích mới mở ở vị trí 0, giá trị in ra vẫn là 0, **dù tệp đích đã chứa 11 byte**.
5. `Files.mismatch(a, b) == -1` xác nhận byte nội dung bằng nhau, bổ sung cho kiểm tra tổng byte.
6. `try-with-resources` đóng các channel ngay cả khi phát sinh lỗi; khối `finally` dọn các tệp tạm được tạo trong ví dụ.

Các giá trị byte và vị trí ở trên không phụ thuộc vào tên thư mục tạm. Nếu môi trường từ chối một thao tác I/O, chương trình báo exception và có thể không in đủ stdout; đây là đường lỗi có chủ đích.

## 8. Đừng nhầm EOF với việc tạm thời chưa có tiến triển

Giả sử cần nhận 1.000 byte từ một `ReadableByteChannel` mạng không chặn và gọi:

~~~java
long n = destination.transferFrom(networkSource, offset, 1000 - offset);
~~~

Nếu `n == 0`, có nhiều khả năng: hiện chưa có byte để đọc, phía gửi đã đóng sau khi hết dữ liệu, hoặc cơ chế transfer trực tiếp tạm thời không chuyển được. **Kết quả 0 riêng lẻ không cung cấp đủ thông tin để kết luận EOF**. Với channel hỗ trợ chế độ không chặn, việc chờ thông qua `Selector` hoặc cơ chế đồng bộ/sự kiện phù hợp cần được thiết kế theo loại channel và nghiệp vụ, có thể kết hợp timeout hoặc hủy.

Với **tệp nguồn thường đã biết kích thước**, chương trình có thể yêu cầu chuyển đúng số byte đã xác định. Nếu nguồn đã bị rút ngắn hoặc không còn byte ở offset đọc, việc nhận 0 trước khi đủ yêu cầu nên được coi là lỗi chuyển thiếu; không đánh dấu thành công.

Khi chuyển dữ liệu từ **nguồn không biết tổng độ dài**, không thể chỉ đặt `expectedBytes` tùy ý lớn rồi coi 0 là EOF. Cần giao thức xác định kết thúc (ví dụ tổng độ dài từ header hoặc tín hiệu kết thúc rõ ràng), hay dùng vòng lặp `read` cho phép phân biệt `-1` (EOF) với `0` (chưa đọc byte), tùy giao thức.

Một mẫu **fallback** cho FileChannel-to-FileChannel khi tối ưu transfer không tiến triển có thể dùng `ByteBuffer` để đọc từ một offset cụ thể và ghi vào vị trí đích; khi đó vẫn phải xử lý số byte thực tế của cả `read` lẫn `write`. Fallback phức tạp hơn nên chỉ thêm khi thực sự cần đáp ứng môi trường cụ thể, và tuyệt đối không viết vòng lặp retry liên tục không giới hạn.

## 9. Sao chép vùng tệp: offset, số byte và giới hạn

Để sao chép 4 KiB bắt đầu tại byte thứ 8 KiB của tệp nguồn:

~~~java
long start = 8L * 1024;
long requested = 4L * 1024;
long size = source.size();

if (start > size || requested > size - start) {
    throw new IOException("Vùng yêu cầu vượt dữ liệu hiện có");
}

long copied = 0;
while (copied < requested) {
    long n = source.transferTo(
            start + copied, requested - copied, target);
    if (n == 0) {
        throw new IOException("Chuyển thiếu dữ liệu trong vùng yêu cầu");
    }
    copied += n;
}
~~~

Đây là đoạn minh họa sử dụng `source` (`FileChannel`), `target` (`WritableByteChannel`) đã mở và import `IOException`. Nếu không muốn lặp đến đủ, phải **chủ động chấp nhận** rằng kết quả chỉ là số byte đã chuyển; API không tự hoàn thiện phần còn thiếu.

Phép kiểm tra `requested > size - start` (sau khi chắc chắn `start <= size`) tránh tính `start + requested` quá sớm gây tràn `long`. Trong ứng dụng thật, xác minh cả `start` và `requested` không âm.

**Với tệp rất lớn:** biến tổng, offset và remaining nên dùng kiểu `long`; không ép `size` về `int`. Một yêu cầu hàng GiB có thể được API/hệ điều hành thực hiện qua nhiều lần chuyển ngắn. Chỉ dừng khi đã đạt tổng mong muốn hoặc gặp tình trạng không thể hoàn thành một cách có kiểm soát.

## 10. Ghi vào tệp đích và tránh dữ liệu thừa

`transferTo` ghi từ **vị trí hiện tại của channel đích** nếu đích là FileChannel. Nếu mở tệp đích cũ mà không truncate và nội dung mới ngắn hơn nội dung cũ, phần đuôi cũ có thể vẫn còn trong tệp. Để sao chép thành một tệp mới, nên:

- Mở tệp chưa tồn tại bằng `CREATE_NEW, WRITE`, hoặc
- Mở tệp đã tồn tại có chủ đích bằng `CREATE, WRITE, TRUNCATE_EXISTING` nếu được phép thay thế nội dung, rồi bảo đảm vị trí ghi là 0.

Với `transferFrom`, offset ghi được truyền trực tiếp; ghi vào một tệp cũ cũng có thể để lại dữ liệu thừa ngoài phạm vi ghi. Tùy hợp đồng ứng dụng, `FileChannel.truncate(expectedLength)` sau khi hoàn thành có thể dùng để giới hạn kích thước đích, nhưng **phải hiểu rõ dữ liệu nào được phép xóa**. Không dùng truncate tùy tiện nếu tệp còn chứa các vùng dữ liệu khác.

Tránh hai luồng cùng thay đổi vị trí của chung một channel trong khi chuyển. Vị trí có thể là trạng thái dùng chung, nên việc xen kẽ với `position()` hoặc hoạt động ghi khác có thể làm kết quả khác mong đợi. Tổ chức ownership channel rõ ràng và đồng bộ nếu thực sự chia sẻ channel.

## 11. Độ đúng, lỗi I/O và tính toàn vẹn dữ liệu

Một vòng lặp cộng tổng byte là **cần thiết nhưng chưa đủ** để chứng minh bản sao chính xác trong mọi trường hợp.

- **Nguồn thay đổi trong quá trình đọc:** `source.size()` lấy trước có thể không còn đúng. Nếu cần bản sao nhất quán, phải phối hợp khóa/đóng băng dữ liệu, snapshot hoặc một cơ chế quản lý phiên bản phù hợp; `FileChannel.lock()` có hạn chế về tính chất và sự hợp tác của các tiến trình.
- **Đích gặp lỗi sau một phần dữ liệu:** file đích có thể chứa bản sao chưa hoàn chỉnh. Dùng file tạm, kiểm tra rồi đổi tên/di chuyển theo chính sách ứng dụng để hạn chế việc công bố bản sao lỗi.
- **Tổng byte đúng nhưng nội dung không đúng:** cùng độ dài chưa đủ bảo đảm cùng dữ liệu. Có thể so sánh byte với `Files.mismatch`; với hệ thống phân tán hoặc cần kiểm tra về sau có thể thêm checksum/hash và cơ chế tin cậy thích hợp.
- **`force()` và độ bền dữ liệu:** chuyển đủ byte vào FileChannel không đồng nghĩa tất cả byte đã bền vững trên thiết bị lưu trữ. Nếu yêu cầu durability, cân nhắc `FileChannel.force(true)` trên đích và quy trình ghi/đổi tên phù hợp với nền tảng.
- **Tài nguyên:** dùng `try-with-resources` để đóng nguồn và đích; không che mất exception gốc nếu cleanup cũng thất bại.

Trong quy trình copy file quan trọng, báo cáo kết quả nên chứa số byte dự kiến, số byte thực tế, cờ kiểm tra toàn vẹn và thông tin lỗi nếu thao tác thất bại.

## 12. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Gọi `transferTo(0, size, target)` một lần rồi mặc định xong | Có thể thiếu byte trên một số hệ thống | Đọc giá trị trả về; lặp với offset tăng theo byte thực tế |
| Gọi `transferFrom(src, 0, count)` nhiều lần khi có partial transfer | Ghi đè phần đã chuyển vào đầu file đích | Tăng `position` ghi theo tổng byte thực |
| Tự tăng `source.position()` sau `transferFrom` với FileChannel nguồn | Đọc bỏ qua dữ liệu | Để channel nguồn tiến vị trí theo thao tác đọc |
| Giả định `FileChannel.position()` của đối tượng gọi sẽ tăng | Đọc/ghi tiếp từ vị trí sai | Dùng offset truyền vào API và theo dõi riêng |
| Coi `0` luôn là EOF | Kết thúc sớm với channel không chặn | Phân biệt nguồn/file/giao thức; chờ readiness hoặc báo lỗi đúng |
| Retry vô hạn khi `0` | Busy-spin, CPU cao | Có chính sách chờ, timeout, fallback hoặc fail fast |
| Dùng `int` để đếm byte của file lớn | Tràn số, cắt ngắn dữ liệu | Dùng `long` |
| Bỏ qua file đích tồn tại và vị trí ghi | Dữ liệu cũ còn sót hoặc bị ghi ở vị trí không mong muốn | Quy định rõ create/truncate và offset |
| Chỉ so kích thước hai file | Không phát hiện byte nội dung sai | So sánh nội dung hoặc checksum phù hợp |
| Cho rằng transfer luôn là zero-copy | Phụ thuộc đặc tính không được API đảm bảo | Đo đạc hiệu năng thực và có chiến lược xử lý partial |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Tệp nguồn dài 1.000 byte. `source.transferTo(0, 1000, target)` trả về 600. Nên chuyển tiếp thế nào?

**Đáp án:** Chưa thể coi bản sao hoàn chỉnh. Nếu dữ liệu nguồn và đích còn sẵn sàng, gọi `source.transferTo(600, 400, target)`, tiếp tục dựa vào giá trị trả về.

**Câu 2.** `destination.transferFrom(source, 100, 50)` trả về 20. `destination.position()` có chắc tăng 20 không?

**Đáp án:** Không. Vị trí channel đích được gọi **không đổi**. Lần chuyển tiếp ghi tại offset `120`; nguồn có vị trí thường đã tiến theo dữ liệu được đọc.

**Câu 3.** `transferFrom` trả về 0 từ một `SocketChannel` không chặn. Có thể kết luận peer đã đóng kết nối chưa?

**Đáp án:** Không. Có thể hiện chưa có dữ liệu hoặc chưa thể tiến hành transfer; phải dựa vào cơ chế sẵn sàng và giao thức đọc để xác định EOF.

**Câu 4.** Khi sao chép file 8 GiB, có thể yêu cầu `count = 8 GiB` một lần rồi không kiểm tra kết quả không?

**Đáp án:** Không. Dù `count` dùng `long`, giới hạn nền tảng có thể khiến một lần chỉ chuyển được một phần. Luôn đếm tổng byte thực tế.

**Câu 5.** Hai bản sao đều dài 11 byte có chắc cùng nội dung không?

**Đáp án:** Không. Tổng byte bằng nhau chỉ là một điều kiện; ví dụ dùng `Files.mismatch` để so sánh byte.

## 14. Ghi nhớ

- **`transferTo`:** `FileChannel` nguồn → `WritableByteChannel` đích; `position` là offset **nguồn**.
- **`transferFrom`:** `ReadableByteChannel` nguồn → `FileChannel` đích; `position` là offset **đích**.
- Cả hai trả về **số byte chuyển thực tế**, không mặc định bằng `count`, và không tự thay đổi vị trí của `FileChannel` mà phương thức được gọi trên đó.
- Muốn chuyển đủ dữ liệu, dùng vòng lặp `long`, tăng offset theo kết quả từng lần, kiểm soát tình huống `0` và kiểm tra toàn vẹn sau khi hoàn thành.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-006">Zero-copy là khả năng tối ưu, không phải bảo đảm cho mọi tình huống</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel và zero-copy: khả năng tối ưu, không phải bảo đảm cho mọi tình huống

## 1. Zero-copy trong I/O nghĩa là gì?

Khi truyền nội dung một tệp sang một channel khác, cách thực hiện quen thuộc là đọc byte từ tệp vào một `ByteBuffer` trong tiến trình Java rồi ghi những byte đó sang đích. Với tệp lớn, quá trình chuyển dữ liệu có thể tạo thêm bước sao chép giữa các vùng nhớ và tiêu thụ CPU.

**Zero-copy** là tên gọi của một nhóm kỹ thuật giúp **tránh một số bước sao chép dữ liệu không cần thiết**, đặc biệt những lần phải chuyển toàn bộ nội dung qua bộ nhớ ứng dụng. Trên một số hệ điều hành, đường truyền từ tệp sang socket có thể dùng một cơ chế nhân hệ điều hành như `sendfile` để giảm lượng dữ liệu phải đi qua không gian người dùng.

Trong Java NIO, `java.nio.channels.FileChannel` có các phương thức có thể tạo điều kiện cho tối ưu này:

- `transferTo(long position, long count, WritableByteChannel target)`: chuyển byte từ một vùng của **FileChannel nguồn** sang channel đích.
- `transferFrom(ReadableByteChannel src, long position, long count)`: chuyển byte từ channel nguồn vào một vùng của **FileChannel đích**.

**Nhưng gọi `transferTo` hoặc `transferFrom` không chứng minh rằng hệ thống đã thực sự sử dụng zero-copy.** Java định nghĩa kết quả truyền dữ liệu ở cấp API; cách thực hiện phụ thuộc JDK, hệ điều hành, loại channel, hệ thống tệp và tình huống vận hành. Một triển khai có thể dùng đường truyền tối ưu trong nhân hệ điều hành, dùng một vùng đệm trung gian hoặc kết hợp nhiều cách.

> **Nguyên tắc học bài:** Zero-copy là *khả năng tối ưu của một đường dữ liệu cụ thể*, không phải lời hứa “không có bất kỳ bản sao byte nào”, “luôn nhanh hơn” hoặc “chỉ cần gọi một lần là truyền xong”.

## 2. Phân biệt sao chép qua ByteBuffer và cơ hội zero-copy

### Luồng sao chép qua bộ đệm ứng dụng

Một phương án thông thường:

```text
File nguồn
    |
    v
FileChannel.read(ByteBuffer)
    |
    v
ByteBuffer do ứng dụng quản lý
    |
    v
WritableByteChannel.write(ByteBuffer)
    |
    v
Đích (tệp, socket, ...)
```

Dữ liệu được đưa đến vùng lưu trữ mà Java có thể thao tác. Ứng dụng có thể kiểm tra, biến đổi, giải mã, mã hóa hoặc xử lý từng khối byte trước khi gửi tiếp.

### Đường truyền có thể tối ưu với FileChannel

Với `source.transferTo(offset, count, target)`:

```text
Ứng dụng Java:
  yêu cầu chuyển vùng byte [offset, offset + count)
           |
           v
  FileChannel.transferTo(...)
           |
           v
  Triển khai JDK / hệ điều hành
           |
           +-- Có khả năng: đường truyền tối ưu trong kernel
           |
           +-- Trường hợp khác: sao chép qua buffer trung gian
           |
           v
  WritableByteChannel đích
```

Sơ đồ chỉ mô tả **các khả năng triển khai**. Không được khẳng định rằng mọi đường truyền qua `transferTo` đều bỏ qua không gian người dùng, không cần bộ đệm hay không tiêu thụ tài nguyên CPU/bộ nhớ. Cache của hệ điều hành, đường DMA, lớp mạng, mã hóa TLS và driver có thể tạo các bước xử lý hoặc sao chép khác nhau.

Điểm được API bảo đảm là **ý nghĩa chuyển byte**, số byte báo đã chuyển và các quy tắc trạng thái liên quan; tối ưu vật lý nằm ngoài hợp đồng chức năng của chương trình.

## 3. Hợp đồng `FileChannel.transferTo`

Chữ ký:

```java
public abstract long transferTo(
    long position,
    long count,
    WritableByteChannel target
) throws IOException;
```

Ý nghĩa:

- **`position`**: offset byte tuyệt đối, bắt đầu từ 0, trong FileChannel nguồn.
- **`count`**: số byte tối đa yêu cầu chuyển, không phải số byte chắc chắn sẽ được chuyển.
- **`target`**: channel đích có khả năng ghi.
- **Giá trị trả về**: số byte **thực sự** chuyển, kiểu `long`, nằm trong phạm vi từ 0 đến `count` khi thao tác thành công.

Không chấp nhận `position` hoặc `count` âm. Thao tác có thể phát sinh `IOException`, bao gồm những ngoại lệ liên quan channel không được mở đúng mode hay bị đóng.

### Vị trí của FileChannel nguồn có thay đổi không?

**Không.** Tham số `position` là vị trí riêng của lần truyền. Theo hợp đồng của `transferTo`, **`source.position()` không đổi** sau lời gọi đó.

Nếu channel đích có một vị trí hiện tại (ví dụ `FileChannel` đích), dữ liệu thường được ghi từ vị trí của **đích**, và vị trí đích có thể tiến theo số byte ghi được. Với đích là socket, không có một file offset công khai để người dùng quan sát tương tự.

Ví dụ:

```text
Nguồn "ABCDEFGHIJ" (10 byte)
source.position() = 7

transferTo(2, 3, target)
          |
          +-- yêu cầu chuyển "CDE"

source.position() sau chuyển: vẫn 7
target: tiến theo lượng byte nhận được, nếu có position
```

Không sử dụng `source.position()` để tìm “offset tiếp theo” trong vòng lặp positional transfer; cần tự theo dõi offset bắt đầu và tổng số byte đã chuyển.

### Khi nào `transferTo` có thể trả về 0?

Có nhiều nguyên nhân tùy hoàn cảnh:

1. `count == 0`.
2. `position` đã ở cuối hoặc vượt quá kích thước nguồn.
3. Đích chưa thể nhận thêm dữ liệu, chẳng hạn socket non-blocking đang chịu backpressure.
4. Một đường truyền/giới hạn triển khai cụ thể chưa chuyển được byte trong lượt gọi đó.

**`0` không đồng nghĩa chắc chắn với EOF hoặc “đã xong”.** Cần phân biệt dựa trên số byte kỳ vọng, trạng thái nguồn, loại đích và cơ chế chờ sẵn sàng. Không viết vòng `while` gọi lại `transferTo` liên tục khi kết quả bằng 0 mà không có điều kiện tiến độ.

## 4. Vì sao một lần `transferTo` có thể chuyển ít hơn `count`?

Hợp đồng cho phép **partial transfer**. Dù file nguồn còn đủ dữ liệu và đích có khả năng nhận, một lời gọi vẫn có thể chuyển ít hơn số byte yêu cầu do giới hạn hệ thống, I/O, điều kiện đích hoặc quyết định của triển khai.

Giả sử muốn chuyển 10.000 byte từ offset 1.000:

| Lần gọi | Offset truyền vào | Count yêu cầu | Byte thực chuyển | Tổng byte đã chuyển |
| --- | ---: | ---: | ---: | ---: |
| 1 | 1.000 | 10.000 | 4.096 | 4.096 |
| 2 | 5.096 | 5.904 | 4.096 | 8.192 |
| 3 | 9.192 | 1.808 | 1.808 | 10.000 |

Đây là **tình huống giả định** để học cách tính offset; không phải số lượng byte bảo đảm của một hệ điều hành.

Công thức đúng sau mỗi lượt có `n > 0`:

\[
nextPosition = startPosition + transferredSoFar
\]

\[
remaining = requestedCount - transferredSoFar
\]

Vòng lặp phải tăng vị trí **nguồn** một cách tường minh:

```java
long offset = startOffset;
long remaining = byteCount;

while (remaining > 0) {
    long n = source.transferTo(offset, remaining, target);
    if (n == 0) {
        // Không có tiến độ: kiểm tra điều kiện, chờ readiness
        // hoặc dùng fallback phù hợp; không spin vô hạn.
        break;
    }

    offset += n;
    remaining -= n;
}
```

Đoạn mã này **không phải chương trình độc lập**; giả định `source`, `target`, `startOffset` và `byteCount` đã có. Nó minh họa cơ chế lặp, nhưng sau vòng lặp người gọi vẫn phải kiểm tra `remaining` và quyết định xử lý thiếu byte. Khi phép cộng offset có nguy cơ tràn `long`, cần kiểm tra giới hạn trước.

**Sai lầm phổ biến:** lặp `source.transferTo(startOffset, byteCount, target)` với cùng offset ban đầu mà không tăng offset. Lần sau có thể chuyển lại cùng đoạn byte và khiến tệp hoặc luồng đích chứa dữ liệu trùng lặp.

## 5. Ví dụ Java 17+: `transferTo` với fallback qua ByteBuffer

Ví dụ tạo hai tệp tạm để không cần mạng hoặc tài nguyên ngoài. Chương trình trước tiên **đề nghị** triển khai sử dụng `transferTo`; nếu một lượt không đạt tiến độ trong khi vẫn còn byte cần chuyển, nó thực hiện một lượt sao chép bằng `ByteBuffer` và các lời gọi `read`/`write` thông thường. Do vậy, chương trình minh họa đúng thông điệp “zero-copy là lựa chọn tối ưu, còn tính đúng đắn của sao chép là yêu cầu bắt buộc”.

Vì một lần gọi `transferTo` không bảo đảm truyền hết, cả đường chính và đường fallback đều theo dõi số byte thực tế.

**Tệp `TransferToWithFallbackDemo.java`:**

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TransferToWithFallbackDemo {
    static long copy(FileChannel source, WritableByteChannel target)
            throws IOException {
        long offset = 0;
        long end = source.size();
        ByteBuffer fallback = ByteBuffer.allocate(8);

        while (offset < end) {
            long count = end - offset;
            long n = source.transferTo(offset, count, target);

            if (n > 0) {
                offset += n;
                continue;
            }

            // Không có tiến độ: dùng đường đọc/ghi thông thường
            // cho một khối nhỏ, giữ nguyên position của source.
            fallback.clear();
            fallback.limit((int) Math.min(
                    fallback.capacity(), end - offset));

            int bytesRead = source.read(fallback, offset);
            if (bytesRead <= 0) {
                throw new IOException(
                    "Source ended or read made no progress");
            }

            fallback.flip();

            while (fallback.hasRemaining()) {
                int bytesWritten = target.write(fallback);
                if (bytesWritten <= 0) {
                    throw new IOException(
                        "Target write made no progress");
                }
            }

            offset += bytesRead;
        }
        return offset;
    }

    public static void main(String[] args) throws IOException {
        Path sourcePath = Files.createTempFile("nio-source-", ".txt");
        Path targetPath = Files.createTempFile("nio-target-", ".txt");

        try {
            Files.writeString(sourcePath, "Hello NIO!",
                    StandardCharsets.US_ASCII);

            try (FileChannel source = FileChannel.open(
                        sourcePath, StandardOpenOption.READ);
                 FileChannel target = FileChannel.open(
                        targetPath, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                source.position(2); // Vị trí nguồn đang sử dụng.
                long copied = copy(source, target);

                System.out.println("So byte da chuyen: " + copied);
                System.out.println("Vi tri nguon: "
                        + source.position());
                System.out.println("Vi tri dich: "
                        + target.position());
            }

            System.out.println("Noi dung dich: "
                    + Files.readString(targetPath,
                            StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(sourcePath);
            Files.deleteIfExists(targetPath);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac TransferToWithFallbackDemo.java
java TransferToWithFallbackDemo
```

**Stdout chính xác trong điều kiện các thao tác I/O hoàn tất:**

```text
So byte da chuyen: 10
Vi tri nguon: 2
Vi tri dich: 10
Noi dung dich: Hello NIO!
```

Giải thích:

1. `"Hello NIO!"` gồm đúng **10 byte ASCII**.
2. `source.position(2)` thiết lập con trỏ nguồn trước khi sao chép.
3. `source.transferTo(offset, ..., target)` sử dụng offset truyền vào, **không di chuyển** con trỏ nguồn đang là 2.
4. Nếu `transferTo` báo không có tiến độ, `source.read(fallback, offset)` là một lệnh **positional read**, cũng không sửa `source.position()`. Phần vừa đọc được gửi qua `target.write(fallback)` với vòng lặp để không bỏ sót byte.
5. `target.position()` tăng theo các byte được ghi vào tệp đích, cuối cùng bằng 10.
6. Đường tối ưu hoặc fallback có thể được chọn tùy máy; **stdout không có dòng nào khẳng định zero-copy đã xảy ra**. Kết quả người học cần kiểm tra là nội dung và số byte đã chuyển.

Đây là ví dụ cho **FileChannel tệp thường với đích FileChannel có khả năng ghi**. Với một `SocketChannel` non-blocking, `transferTo == 0` hoặc `write == 0` có thể là trạng thái backpressure bình thường: chương trình phải giữ offset/remaining, đăng ký readiness phù hợp rồi tiếp tục ở lượt sau. Không nên dùng chính sách “ném ngoại lệ ngay khi write trả 0” của ví dụ blocking để xử lý non-blocking socket.

## 6. Hợp đồng `FileChannel.transferFrom`

Chữ ký:

```java
public abstract long transferFrom(
    ReadableByteChannel src,
    long position,
    long count
) throws IOException;
```

`transferFrom` lấy tối đa `count` byte từ channel `src` rồi đưa chúng vào **FileChannel đích** bắt đầu từ offset `position`.

Bảng so sánh trực tiếp:

| Tính chất | `sourceFile.transferTo(pos, count, target)` | `targetFile.transferFrom(src, pos, count)` |
| --- | --- | --- |
| FileChannel là | **Nguồn** | **Đích** |
| `position` là offset của | FileChannel nguồn | FileChannel đích |
| Vị trí của FileChannel gọi phương thức | **Không thay đổi** | **Không thay đổi** |
| Channel đối tác | `WritableByteChannel` | `ReadableByteChannel` |
| Vị trí của channel đối tác | Có thể tiến theo số byte ghi, nếu đối tác có position | Có thể tiến theo byte đã đọc, nếu nguồn có position |
| Giá trị trả về | Số byte thực chuyển | Số byte thực chuyển |
| Được bảo đảm dùng zero-copy? | **Không** | **Không** |

**Đừng lẫn vị trí của FileChannel gọi phương thức với vị trí của channel đối tác.** Trong `transferTo`, FileChannel nguồn giữ nguyên position; đích nhận byte có thể thay đổi position. Trong `transferFrom`, FileChannel đích giữ nguyên position; nguồn bị tiêu thụ dữ liệu có thể thay đổi position.

Một giới hạn đáng chú ý của `transferFrom`: nếu offset đích **lớn hơn kích thước hiện tại của tệp đích**, hợp đồng API không chuyển byte trong lần gọi đó. Nếu cần ghi vào một vùng sau EOF và tạo khoảng trống, phải thiết kế cách mở rộng tệp thích hợp, không mặc định rằng `transferFrom` tự tạo vùng trống xa EOF.

`transferFrom` cũng có thể chuyển ít hơn `count`, kể cả khi các điều kiện khác có vẻ thuận lợi; gọi lại với `position + bytesTransferred` khi còn cần chuyển tiếp. Vì nguồn là `ReadableByteChannel`, có thể gặp nguồn chưa sẵn sàng hoặc đã EOF; `0` cần được xử lý theo trạng thái cụ thể.

## 7. Ví dụ Java 17+: `transferFrom` không đổi vị trí FileChannel đích

Ví dụ nhận dữ liệu từ một `ReadableByteChannel` chuyển đổi từ `ByteArrayInputStream`. Chương trình đặt `destination.position(2)` nhưng truyền dữ liệu vào offset **0** bằng `transferFrom`.

**Tệp `TransferFromDemo.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TransferFromDemo {
    public static void main(String[] args) throws IOException {
        byte[] payload = "DATA".getBytes(StandardCharsets.US_ASCII);
        Path path = Files.createTempFile("transfer-from-", ".txt");

        try {
            try (ReadableByteChannel input = Channels.newChannel(
                        new ByteArrayInputStream(payload));
                 FileChannel destination = FileChannel.open(
                        path, StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                destination.position(2);

                long copied = 0;
                while (copied < payload.length) {
                    long n = destination.transferFrom(
                            input, copied, payload.length - copied);
                    if (n == 0) {
                        throw new IOException("No transfer progress");
                    }
                    copied += n;
                }

                System.out.println("So byte da chuyen: " + copied);
                System.out.println("Vi tri dich: "
                        + destination.position());
                System.out.println("Kich thuoc tep: "
                        + destination.size());
            }

            System.out.println("Noi dung tep: "
                    + Files.readString(path,
                            StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac TransferFromDemo.java
java TransferFromDemo
```

**Stdout chính xác nếu các thao tác I/O hoàn tất:**

```text
So byte da chuyen: 4
Vi tri dich: 2
Kich thuoc tep: 4
Noi dung tep: DATA
```

Bốn byte `DATA` được ghi bắt đầu tại offset 0. `destination.position()` vẫn bằng **2**, vì đối số `position` của `transferFrom` là offset riêng của lần chuyển. Số byte được chuyển được lấy từ **giá trị trả về thực tế**, không mặc định bằng `count`.

Ví dụ này có nguồn là stream trong bộ nhớ được bọc bằng channel. **Không thể dựa vào nó để tuyên bố có zero-copy**: đây là cách sử dụng đúng hợp đồng API, còn đường triển khai thực tế do JDK và hệ điều hành quyết định.

## 8. Các tình huống khiến zero-copy không được sử dụng hoặc kém hiệu quả

### 8.1. Loại nguồn và đích khác nhau

Đường truyền file → socket có những cơ hội tối ưu ở cấp hệ điều hành khác với file → file, socket → file hoặc channel tự tạo. Từng tổ hợp có thể đi qua các lớp adapter hoặc cơ chế đệm khác nhau.

Ví dụ `Channels.newChannel(OutputStream)` cho phép dùng `transferTo` với một `WritableByteChannel` được bọc từ `OutputStream`, nhưng không bảo đảm kernel có thể chuyển trực tiếp từ tệp sang stream bên dưới mà không có sao chép qua Java.

### 8.2. Mã hóa, nén hoặc biến đổi nội dung

Nếu ứng dụng cần **giải mã/mã hóa TLS**, nén gzip, thay đổi cấu trúc dữ liệu, kiểm tra và sửa byte hoặc chèn header giữa các đoạn, quá trình truyền có thể bắt buộc đi qua lớp xử lý trung gian. Một số nền tảng có offload hoặc tích hợp đặc biệt; nhưng không được mặc định cơ chế đơn giản của `transferTo` tự xử lý mọi biến đổi mà vẫn giữ tối ưu zero-copy.

Với giao thức HTTP, có thể ứng dụng gửi header theo cách riêng rồi dùng đường tối ưu cho phần thân tệp **nếu** đích và stack thực thi hỗ trợ. Cần phân biệt loại kênh và giao thức cụ thể.

### 8.3. Hạn chế hệ điều hành và JDK

Các giới hạn về số byte truyền mỗi lần, kích thước tệp, thiết bị, filesystem, kiểu handle, bộ nhớ kernel, cờ mở tệp hoặc tình trạng I/O có thể ảnh hưởng đến cách chuyển dữ liệu. Có thể xảy ra một số lần truyền thành công rồi lần tiếp theo trả 0, hoặc chỉ chuyển một phần số byte yêu cầu.

Không giả định một giá trị tối đa cố định, ví dụ “mỗi lần luôn truyền được 2 GB”, áp dụng trên tất cả JDK/hệ điều hành. Hãy dùng số byte trả về và viết vòng lặp tiến độ, đồng thời đo trên môi trường thực tế.

### 8.4. Backpressure ở mạng và non-blocking I/O

Nếu đích là socket non-blocking, bộ đệm gửi có thể đầy. Khi đó `transferTo` có thể trả về **0** do tạm thời không thể gửi; tiếp tục thử liên tục sẽ gây *busy spin*. Cách đúng là giữ offset và số byte còn lại, chờ sự kiện sẵn sàng ghi phù hợp, sau đó thử lại khi có tiến độ.

Không dùng mẫu `while (remaining > 0) { transferTo(...); }` không có xử lý `0`, vì vòng lặp có thể không bao giờ kết thúc.

### 8.5. Bản sao trong hệ thống vẫn có thể tồn tại

Ngay cả khi một đường truyền tránh sao chép qua buffer ứng dụng, dữ liệu vẫn có thể được đặt trong page cache, socket buffer, vùng DMA hoặc các lớp I/O khác. **Zero-copy không đồng nghĩa tổng số bản sao vật lý bằng 0** trong toàn bộ vòng đời dữ liệu. “Zero” thường nói đến một lớp bước sao chép cụ thể được tối ưu.

### 8.6. Tệp nhỏ có thể không hưởng lợi rõ rệt

Với tệp rất nhỏ, thời gian mở tệp, tạo tài nguyên, syscall, dispatch, thiết lập kết nối và xử lý giao thức có thể chi phối tổng thời gian. Giảm số lần sao chép chưa chắc giảm độ trễ đầu cuối một cách đáng kể. Hiệu năng cần được đo theo trường hợp sử dụng, không suy ra từ tên API.

## 9. Bảo toàn tính đúng đắn quan trọng hơn chọn đường tối ưu

Một hàm truyền file đáng tin cậy cần xác định **điều kiện hoàn tất** và **trạng thái khi chưa hoàn tất** trước khi quan tâm đến zero-copy.

Các điểm phải làm rõ:

1. **Nguồn dữ liệu có kích thước cố định trong suốt thao tác không?** Nếu tệp bị sửa, cắt ngắn hoặc mở rộng giữa chừng, `size()` ban đầu có thể không phản ánh dữ liệu đang có. Với dữ liệu cần nhất quán, cần thiết kế snapshot/khóa/phiên bản hoặc quy trình tái thử và xác minh phù hợp.
2. **Đang muốn chuyển bao nhiêu byte?** Dùng `long` cho offset, count và tổng chuyển; đảm bảo không âm, không tràn khi cộng.
3. **Khi `n>0`:** tăng offset và giảm remaining dựa trên `n` thực tế.
4. **Khi `n==0`:** không tự coi là đã xong; xác định EOF, backpressure, giới hạn triển khai hay chuyển đường fallback.
5. **Khi lỗi I/O:** báo lỗi/đóng tài nguyên; không công bố thành công khi file đích mới được ghi một phần.
6. **Khi cần bảo đảm dữ liệu bền vững:** việc sao chép hoàn tất không đồng nghĩa dữ liệu đã được đồng bộ xuống thiết bị lưu trữ. `FileChannel.force(boolean)` hoặc quy trình lưu an toàn khác có thể cần thiết tùy yêu cầu, nhưng cũng có giới hạn theo thiết bị và filesystem.

Trong một hệ thống tạo file đầu ra chính thức, có thể ghi vào file tạm, kiểm tra kích thước/hash rồi đổi tên theo chiến lược phù hợp thay vì để người khác nhìn thấy file đang được ghi dở. Chi tiết đổi tên nguyên tử phụ thuộc khả năng hỗ trợ của file system và option được dùng; không mặc định mọi thao tác move là atomic.

## 10. So sánh `transferTo`, `transferFrom` với sao chép thủ công

| Đặc điểm | `transferTo` | `transferFrom` | `read` + `write` với `ByteBuffer` |
| --- | --- | --- | --- |
| Hướng sử dụng | FileChannel → WritableByteChannel | ReadableByteChannel → FileChannel | Channel nguồn → buffer → channel đích |
| Offset tệp tuyệt đối | Offset nguồn | Offset đích | Tùy API đọc/ghi lựa chọn |
| Position FileChannel gọi phương thức | Giữ nguyên | Giữ nguyên | Phụ thuộc dạng read/write |
| Partial transfer | Có thể xảy ra | Có thể xảy ra | Có thể xảy ra |
| Zero-copy | Có thể được tối ưu | Có thể được tối ưu | Thường có byte đi qua buffer ứng dụng |
| Thích hợp khi cần biến đổi từng byte | Không trực tiếp | Không trực tiếp | Phù hợp hơn |
| Có bảo đảm nhanh hơn? | Không | Không | Không |
| Có bảo đảm atomic hay durable? | Không | Không | Không |

Không dùng “ít dòng code hơn” hay “API có tên transfer” làm bằng chứng về số lần sao chép và tốc độ. Đường truyền kernel phù hợp có thể giảm CPU đáng kể với tệp lớn, nhưng mức lợi ích là kết quả đo đạc và điều kiện triển khai cụ thể.

## 11. Đo và xác minh tối ưu một cách có ý nghĩa

Để kết luận zero-copy có lợi trong một ứng dụng, nên phân biệt hai câu hỏi:

### A. Truyền dữ liệu đúng chưa?

Kiểm tra các thuộc tính chức năng:

- Tổng số byte thực chuyển bằng lượng dữ liệu cần chuyển, hoặc có lý do dừng rõ ràng.
- Byte đầu ra giống byte đầu vào ở đúng phạm vi, bằng so sánh byte hoặc hash nếu phù hợp.
- Position của các channel và buffer đúng như hợp đồng.
- Không mất dữ liệu khi partial transfer; không nhân đôi đoạn byte khi thử lại.
- Xử lý được EOF, thiếu byte, đóng sớm và lỗi giữa chừng.

### B. Đường truyền có thực sự được tối ưu không?

Sau khi chức năng đúng, dùng đo đạc trên môi trường thực tế: thời gian xử lý, thông lượng, CPU, số byte cấp phát trong tiến trình, số lần syscall hoặc số liệu profiler/tracing nếu có. Hệ điều hành có thể cung cấp công cụ quan sát syscall/kernel (ví dụ theo dõi đường `sendfile` trên hệ thống có hỗ trợ). Tuy nhiên, **nhìn thấy một lời gọi syscall chưa đủ để chứng minh không có sao chép ở mọi tầng**.

Khi benchmark:

1. Dùng cùng loại nguồn/đích, cùng dữ liệu, chế độ blocking, kích thước buffer và chính sách đóng/flush.
2. Chạy nhiều lần, tách giai đoạn warmup khỏi số liệu đo, xem phân phối thời gian chứ không chỉ một lần chạy.
3. Chú ý tác động của OS page cache: lần đọc đầu và lần đọc lại có thể khác nhau đáng kể.
4. Với truyền mạng, phân biệt thông lượng đo từ ứng dụng, tải CPU và tốc độ bên nhận; áp lực mạng/backpressure có thể che hiệu quả sao chép.
5. Không so sánh đường truyền có xác minh/mã hóa với đường không làm công việc tương đương rồi kết luận nguyên nhân nằm hoàn toàn ở zero-copy.

**Không có API tiêu chuẩn `FileChannel` nào trả về cờ “lần vừa rồi đã sử dụng zero-copy”.** Muốn xác định đường đi của dữ liệu cần quan sát và hiểu triển khai thực tế.

## 12. Các lỗi thường gặp

| Giả định hoặc cách làm sai | Vấn đề | Cách xử lý |
| --- | --- | --- |
| “Gọi `transferTo` là chắc chắn zero-copy” | Triển khai có thể dùng copy fallback | Xem đó là cơ hội tối ưu và đo trên nền tảng thực |
| “Zero-copy là zero byte được sao chép ở mọi tầng” | Còn page cache, driver, mạng, thiết bị | Nêu chính xác bước sao chép nào được giảm |
| “Một `transferTo` sẽ gửi hết `count`” | Partial transfer làm thiếu dữ liệu | Kiểm tra `long n`, tăng offset và lặp có kiểm soát |
| Lặp lại offset nguồn cũ sau partial transfer | Gửi trùng đoạn | Dùng `offset + transferredSoFar` |
| `n == 0` nghĩa là đã gửi xong | Có thể do backpressure hoặc giới hạn | Phân biệt còn dữ liệu/EOF và cơ chế chờ |
| Gọi lại liên tục khi `n == 0` | Busy spin | Chờ readiness hoặc chuyển fallback phù hợp |
| Giả định `source.position()` sẽ tăng sau `transferTo` | Đọc sai vị trí tiếp theo | Tự duy trì offset; position nguồn vẫn nguyên |
| Giả định `destination.position()` tăng sau `transferFrom` | Dữ liệu kế tiếp đặt sai vị trí | Tự cập nhật offset đích khi lặp |
| `transferFrom` ghi vào offset xa quá EOF | Có thể chuyển 0 byte theo hợp đồng | Định nghĩa và chuẩn bị kích thước/offset phù hợp |
| Ghi một phần rồi công bố file đã hoàn tất | Người đọc thấy nội dung thiếu | Kiểm tra tổng byte và công bố sau khi xác minh |
| Dùng zero-copy để tự động mã hóa/biến đổi dữ liệu | Transfer không thay thế pipeline xử lý | Chọn đường xử lý phù hợp với định dạng/giao thức |
| Kết luận “NIO luôn nhanh hơn stream” | Phụ thuộc kích thước, cache, I/O và nền tảng | Benchmark công bằng theo workload |
| Tin `transferTo` bảo đảm atomic và durability | Không có cam kết giao dịch/lưu bền | Thiết kế phối hợp/flush/khôi phục riêng |

## 13. Tình huống thực hành: gửi một vùng tệp 8 KiB

Giả sử nguồn dài 100 KiB, cần chuyển chính xác vùng 8 KiB bắt đầu ở offset 20 KiB đến một `WritableByteChannel`.

Các giá trị:

```text
startOffset     = 20 * 1024 = 20480
requestedCount  =  8 * 1024 = 8192
currentOffset   = 20480
remaining       = 8192
```

Nếu lần đầu `transferTo` trả về 3.000:

```text
nextOffset = 20480 + 3000 = 23480
remaining  = 8192 - 3000 = 5192
```

Nếu lần hai trả 2.000:

```text
nextOffset = 23480 + 2000 = 25480
remaining  = 5192 - 2000 = 3192
```

Nếu lần ba trả về **0**, dù `remaining = 3192`, **không được công bố hoàn tất**. Với socket non-blocking, lưu trạng thái `(nextOffset=25480, remaining=3192)` để tiếp tục khi socket sẵn sàng; với một kênh blocking trong môi trường phù hợp, có thể dùng fallback/diagnostics hoặc xử lý tình trạng thiếu tiến độ.

Để hoàn tất vùng truyền, tổng byte **phải bằng 8192**, và byte đích phải trùng với đoạn nguồn `[20480, 28672)`. FileChannel nguồn có thể vẫn giữ nguyên `source.position()` trong toàn bộ các lần `transferTo`.

**Không suy ra** có bao nhiêu copy nội bộ chỉ từ ba giá trị `3000, 2000, 0`. Chúng nói về lượng dữ liệu đã chuyển, không phải cơ chế truyền byte của kernel.

## 14. Ghi nhớ

1. **`FileChannel.transferTo` và `transferFrom` có thể tạo điều kiện cho đường truyền zero-copy**, nhưng hợp đồng Java **không bảo đảm cơ chế đó được sử dụng**.
2. **`transferTo` giữ nguyên position của FileChannel nguồn**; **`transferFrom` giữ nguyên position của FileChannel đích**. Channel đối tác có thể thay đổi trạng thái/position tương ứng với byte đã tiêu thụ hoặc tiếp nhận.
3. **Kết quả là số byte thực chuyển**, không phải số yêu cầu. Phải xử lý partial transfer bằng cách tăng offset và giảm remaining theo số byte trả về.
4. **Kết quả 0 chưa chắc là EOF hay thành công**; cần xét độ dài, trạng thái channel, readiness hoặc fallback.
5. **Zero-copy không có nghĩa là tuyệt đối không còn bản sao dữ liệu**, và không thay thế kiểm tra byte, bền vững lưu trữ, tính nhất quán hay bảo mật.
6. **Tính đúng đắn trước, hiệu năng sau**: xác minh dữ liệu đích, xử lý lỗi, rồi benchmark/trace trên nền tảng mục tiêu để đánh giá lợi ích.

**Tài liệu tham khảo:** Java SE 17 API: `java.nio.channels.FileChannel#transferTo`, `FileChannel#transferFrom`, `java.nio.channels.ReadableByteChannel`, `WritableByteChannel`, `java.nio.ByteBuffer` và `java.nio.file.StandardOpenOption`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-007">force và phạm vi bảo đảm với thiết bị lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel.force(boolean) và phạm vi bảo đảm với thiết bị lưu trữ

Khi ghi tệp bằng Java, lời gọi `FileChannel.write(...)` trả về thành công **không đồng nghĩa** dữ liệu đã tới thiết bị lưu trữ bền vững. Hệ điều hành và thiết bị thường dùng các tầng bộ nhớ đệm để tăng tốc. Phương thức `FileChannel.force(boolean metaData)` yêu cầu đẩy các thay đổi thuộc phạm vi hợp đồng của channel xuống thiết bị lưu trữ chứa tệp. Đây là công cụ quan trọng khi ứng dụng lưu dữ liệu cần sống sót sau sự cố hệ thống, nhưng **không phải transaction, không phải khóa ghi, và không bảo đảm tuyệt đối trước mọi lỗi phần cứng hay lưu trữ từ xa**.

Mục tiêu sau bài học:

1. Hiểu `force(false)` và `force(true)` buộc ghi những gì.
2. Phân biệt dữ liệu file, metadata của file và mục tên file trong thư mục.
3. Xác định chính xác ranh giới bảo đảm giữa local storage, remote storage, OS, filesystem, controller và bộ nhớ đệm.
4. Sử dụng `FileChannel.force`, `MappedByteBuffer.force` và các tùy chọn `SYNC`/`DSYNC` đúng hoàn cảnh.
5. Thiết kế trình tự ghi và kiểm thử phù hợp khi cần cả độ bền (*durability*) lẫn tính nguyên tử (*atomicity*).

## 1. Vì sao đã gọi write mà dữ liệu vẫn có thể mất?

Một thao tác ghi file thường đi qua nhiều tầng:

```text
Ứng dụng Java
    |
    | FileChannel.write(ByteBuffer)
    v
JDK / lời gọi hệ điều hành
    |
    v
Bộ đệm của hệ điều hành / page cache
    |
    v
Filesystem / block layer / driver
    |
    v
Cache của controller hoặc thiết bị
    |
    v
Thiết bị lưu trữ thực tế
    (SSD, HDD, bộ nhớ bền vững, thiết bị qua mạng...)
```

Sơ đồ này là mô hình khái quát để hiểu các mức trách nhiệm; đường I/O thực tế phụ thuộc OS, filesystem và loại thiết bị. Một lệnh `write` có thể hoàn tất sau khi dữ liệu được nhận vào cache của OS, trước khi được xác nhận đã ghi bền vững trên thiết bị. Cache khiến đọc lại ngay sau ghi thường thấy dữ liệu mới, ngay cả khi mất điện sau đó có thể dẫn tới kết quả khác.

Phân biệt các trạng thái:

- **Dữ liệu đã được chấp nhận bởi thao tác ghi:** `channel.write(buffer)` trả số byte thực sự được xử lý; vẫn cần lặp khi ghi chưa hết buffer.
- **Dữ liệu nhìn thấy khi đọc lại:** các lần đọc qua cùng OS có thể đọc từ cache. Điều đó chứng minh nội dung logic hiện tại, **không chứng minh khả năng phục hồi sau mất điện**.
- **Dữ liệu đã được yêu cầu đồng bộ xuống thiết bị:** `channel.force(...)` hoàn tất theo hợp đồng Java cho file thuộc local storage và các thay đổi được bao phủ.
- **Ứng dụng phục hồi nhất quán sau sự cố:** ngoài flush, cần xử lý transaction/record dở, metadata thư mục và thứ tự ghi. `force` riêng lẻ không tạo ra giao dịch.

**`flush()` của `OutputStream` và `FileChannel.force(...)` không có cùng ý nghĩa.** `flush` đẩy dữ liệu còn trong lớp đệm của stream theo hợp đồng implementation; nó không hứa dữ liệu đã ở thiết bị lưu trữ. `FileChannel` không có phương thức `flush()` tương tự: dùng `force` cho yêu cầu đồng bộ file.

## 2. Hợp đồng chính thức của FileChannel.force

Chữ ký:

```java
public abstract void force(boolean metaData) throws IOException
```

Phương thức trả về **`void`**; việc hoàn tất bình thường thể hiện thao tác force được thực hiện theo hợp đồng, không cung cấp số byte đã flush hay vị trí vật lý của dữ liệu. `ClosedChannelException` có thể xuất hiện nếu channel đóng; lỗi I/O khác được báo bằng `IOException`.

Theo Javadoc Java SE 21:

- Nếu tệp thuộc **thiết bị lưu trữ cục bộ (local storage device)**, khi `force` trả về bình thường, các thay đổi trong phạm vi mà API cam kết từ khi tạo channel hoặc lần force gần nhất được yêu cầu ghi xuống thiết bị đó.
- Nếu file **không nằm trên local device**, Java **không đưa ra cùng bảo đảm**. Ổ mạng và một số hệ thống file ảo/phân tán là những tình huống cần khảo sát kỹ.
- `metaData=false`: chỉ **nội dung file** bắt buộc được force theo yêu cầu.
- `metaData=true`: cần force **nội dung và metadata** của file. Tác dụng cụ thể của tham số tùy OS và có thể phát sinh thêm I/O.
- Hợp đồng bảo đảm đối với những thay đổi được thực hiện qua **các phương thức của FileChannel**, hoặc qua `FileOutputStream`/`RandomAccessFile` liên quan nếu channel lấy bằng `getChannel()`. Những thay đổi qua `MappedByteBuffer` có quy tắc riêng.

Cụm “được ghi xuống thiết bị” nên hiểu **trong phạm vi giao diện OS/thiết bị thực thi đúng hợp đồng**. Java không thể tự xác nhận firmware đã thật sự bảo đảm dữ liệu chống được mọi trường hợp controller hỏng, firmware lỗi, mất điện ở hệ thống không có bảo vệ cache hoặc sự cố phá hủy thiết bị.

### `force(false)` so với `force(true)`

| Lệnh | Phần được yêu cầu đồng bộ | Phạm vi sử dụng và lưu ý |
| --- | --- | --- |
| `channel.force(false)` | Nội dung byte đã thay đổi thuộc phạm vi API. | Phù hợp khi chỉ cần xác nhận nội dung file, không yêu cầu đồng bộ toàn bộ metadata. **Không mặc định** kích thước file mới hoặc thông tin thư mục đã bền vững như nội dung. |
| `channel.force(true)` | Nội dung **và metadata của file**. | Có thể cần khi thay kích thước, thời điểm hay thông tin metadata đi kèm. Không có nghĩa mọi thông tin thư mục cha, rename hoặc toàn bộ filesystem đã được force. |
| `channel.close()` | Giải phóng tài nguyên. | Không phải API thay thế `force` với cùng hợp đồng đồng bộ bền vững. Đừng dựa vào riêng `close` cho yêu cầu chống sự cố. |
| `OutputStream.flush()` | Đẩy các byte còn đệm ở lớp stream. | Không mang hợp đồng độ bền của `FileChannel.force`. |
| `MappedByteBuffer.force()` | Yêu cầu force các thay đổi của *mapping* (xem mục 5). | Không đồng nhất với channel.force khi dữ liệu được sửa qua mapped memory. |

Ví dụ ứng dụng cập nhật một record có kích thước cố định trong file hiện có: `force(false)` thường diễn đạt rõ ý định đảm bảo nội dung. Khi vừa mở rộng độ dài file hoặc tạo file mới, `force(true)` thường là lựa chọn thận trọng để yêu cầu đồng bộ metadata liên quan. Tuy nhiên, **lựa chọn tham số không biến trình tự cập nhật nhiều file hoặc một thư mục thành transaction**.

## 3. Những gì FileChannel.force bao phủ — và những gì không

### 3.1. Các thay đổi qua channel và đối tượng I/O có liên hệ

`force` được xác định với những thay đổi tệp do `FileChannel` thực hiện. Nếu `FileOutputStream.getChannel()` hoặc `RandomAccessFile.getChannel()` tạo channel, các thay đổi thực hiện qua stream/file ngẫu nhiên gắn với channel thuộc phạm vi hợp đồng ghi force nêu trong Javadoc.

```java
// Minh họa API (không phải chương trình độc lập).
try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
    raf.seek(0);
    raf.writeInt(42);
    raf.getChannel().force(true);
}
```

Ở đây `path` là `Path` tồn tại/ghi được; `getChannel()` của `RandomAccessFile` liên hệ cùng file handle. Ngược lại, **không tự động suy ra** rằng gọi `force` trên một channel riêng sẽ bao phủ mọi thay đổi do chương trình khác, một handle độc lập, API khác hoặc tiến trình khác tạo ra. Cần hiểu chính xác các writer và giao thức phối hợp.

### 3.2. Không tự biến lần ghi thành nguyên tử

Ví dụ ghi một record 4 KB rồi gọi `force(true)`:

- Nếu writer đã ghi được **một phần record** và gặp exception trước khi đủ 4 KB, `force` không sửa dữ liệu còn thiếu.
- Nếu hai thread ghi chồng lấn lên cùng vùng byte mà không khóa, `force` không quyết định byte của thread nào là đúng.
- Nếu ứng dụng cần thay đổi hai file nhất quán cùng nhau, `force` từng file không cung cấp transaction gồm cả hai.
- Nếu thiết bị mất điện **trong khi** bản ghi đang được ghi/force, file có thể cần cơ chế phục hồi theo format: length + checksum, version, nhật ký hoặc nhiều bản sao có đánh dấu commit.

**Durability** nghĩa là bảo toàn những cập nhật đã cam kết theo chính sách. **Atomicity** nghĩa là cập nhật được quan sát như trọn vẹn hoặc không có. **Consistency** nghĩa là các ràng buộc dữ liệu vẫn hợp lệ. `force` hỗ trợ phần durability trong phạm vi cam kết, không tự cung cấp atomicity và consistency của ứng dụng.

### 3.3. Không đồng nghĩa force metadata của *thư mục*

Metadata file (ví dụ kích thước, thời điểm sửa) khác với **directory entry** ánh xạ tên thành file. Khi ứng dụng tạo file mới hoặc đổi tên file bằng `Files.move`, dữ liệu file và sự bền vững của bản ghi thư mục là hai vấn đề cần xem riêng.

Ví dụ lưu cấu hình an toàn theo mẫu tổng quát:

```text
1. Tạo file tạm trong thư mục đích.
2. Ghi đầy đủ nội dung mới vào file tạm.
3. FileChannel.force(true) trên file tạm; xử lý mọi IOException.
4. Đóng channel.
5. Đổi tên/replace sang đường dẫn chính bằng thao tác atomic nếu filesystem hỗ trợ.
6. Nếu yêu cầu bền vững qua mất điện áp dụng cả cho thao tác đổi tên:
   cần xem cơ chế đồng bộ thư mục theo OS/filesystem.
```

Đây là **phác thảo giao thức**, không phải một lời hứa đa nền tảng. `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` có nghĩa là yêu cầu thao tác move nguyên tử ở cấp filesystem; nó **không tự mang bảo đảm durability của directory entry**. Với `ATOMIC_MOVE`, hành vi thay đích đã tồn tại phụ thuộc implementation; không mặc định `REPLACE_EXISTING` kết hợp sẽ hoạt động giống trên mọi filesystem. Java chuẩn **không có một API di động bảo đảm fsync directory trên mọi hệ điều hành**. Nếu nghiệp vụ yêu cầu bảo vệ sau crash ở mức tên tệp, phải kiểm chứng kỹ platform và có thể cần API native/giao thức lưu trữ chuyên dụng.

## 4. Ví dụ chạy được: force sau khi hoàn thành ghi

Tạo file `ForceWriteDemo.java`:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ForceWriteDemo {
    private static void writeAll(FileChannel channel, String text) throws IOException {
        ByteBuffer data = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (data.hasRemaining()) {
            int n = channel.write(data);
            if (n == 0) throw new IOException("Write made no progress");
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("force-demo-", ".txt");
        try {
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                writeAll(channel, "first");
                channel.force(false);
                System.out.println("content-after-force-false=" +
                        Files.readString(file, StandardCharsets.UTF_8));
                System.out.println("size=" + channel.size());

                channel.truncate(0);
                channel.position(0);
                writeAll(channel, "second");
                channel.force(true);
                System.out.println("content-after-force-true=" +
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy trên Java 17+:

```bash
javac --release 17 ForceWriteDemo.java
java ForceWriteDemo
```

Kết quả đã kiểm tra:

```text
content-after-force-false=first
size=5
content-after-force-true=second
```

**Phân tích**:

1. `Files.createTempFile` tạo một tệp mới. `FileChannel.open` mở kênh đọc/ghi.
2. `writeAll` lặp cho tới khi toàn bộ `ByteBuffer` được tiêu thụ, tránh mặc định một lệnh `write` ghi đủ bytes. Nếu ghi không tiến triển, ví dụ báo lỗi.
3. Sau khi ghi `first`, `force(false)` yêu cầu flush phần nội dung thuộc phạm vi `FileChannel`.
4. `size()` trả `5` byte vì văn bản ở ví dụ chỉ chứa 5 ký tự ASCII.
5. `truncate(0)` rút file về độ dài 0; `position(0)` đặt lại vị trí để ghi `second`. `force(true)` yêu cầu đồng bộ cả nội dung và metadata.
6. Đọc lại file giúp xác minh byte logic, **không phải bài kiểm thử chứng minh dữ liệu sẽ sống sót sau crash**. `force(true)` cũng không được coi là chứng cứ directory entry của file mới đã bền vững sau mất điện.

Chương trình sẽ ném `IOException` khi thao tác ghi/force gặp lỗi; đừng bắt exception rồi in thông báo “lưu thành công”. Dùng `try-with-resources` để đóng channel và `finally` để xóa file tạm trong ví dụ; quy tắc xóa sau khi kiểm thử không áp dụng cho file ứng dụng cần lưu dài hạn.

### Vì sao `force` phải ở đúng điểm cam kết?

Nếu ghi dữ liệu `A`, gọi `force`, rồi ghi tiếp `B`, việc force trước đó không hứa phần `B` đã được đồng bộ. Trong hệ thống ghi nhiều record, điểm gọi `force` là ranh giới cam kết được lựa chọn:

```text
write(record1)
write(record2)
force(true)    <- yêu cầu đồng bộ cho batch này
ACK batch      <- chỉ báo đã cam kết nếu force không ném lỗi
write(record3)
...            <- record3 chưa được cam kết bởi lần force trên
```

Điều này giả định ứng dụng có giao thức record rõ ràng, không có writer khác làm hỏng dữ liệu và underlying local filesystem/thiết bị thực hiện đúng các yêu cầu đồng bộ. **ACK** không phải dữ liệu tự động được Java phát ra; đó là quyết định của hệ thống dùng `force`.

## 5. Với memory-mapped file: phải nghĩ tới MappedByteBuffer.force

`FileChannel.map(FileChannel.MapMode.READ_WRITE, position, size)` tạo `MappedByteBuffer` trỏ đến một vùng của file. Khi sửa nội dung qua buffer đã map, sự thay đổi đi qua cơ chế memory mapping; Javadoc `FileChannel.force` **không cam kết mọi thay đổi do thao tác `put` trên mapped buffer đều được force**.

Dùng:

```java
MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
mapped.put(0, (byte) 'A');
mapped.force(); // yêu cầu đồng bộ thay đổi qua mapping
```

Đây là **đoạn minh họa** với `channel` đã mở READ/WRITE và `size` hợp lệ. Không thay `mapped.force()` bằng `channel.force(false)` chỉ vì mapped buffer được tạo từ channel đó. Cần nhớ rằng mapping có vòng đời có thể **dài hơn** file channel; đóng channel không tự giải phóng mapping ngay trên mọi platform.

### Ví dụ độc lập: ghi qua mapped buffer

Tạo `MappedForceDemo.java`:

```java
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class MappedForceDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("mapped-force-", ".bin");
        try {
            Files.writeString(file, "0000", StandardCharsets.US_ASCII);
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                MappedByteBuffer mapped =
                        channel.map(FileChannel.MapMode.READ_WRITE, 0, 4);
                mapped.put(0, (byte) 'A');
                mapped.put(1, (byte) 'B');
                mapped.force();
                System.out.println("mapped-view=" +
                        StandardCharsets.US_ASCII.decode(mapped.duplicate().position(0)));
            }
        } finally {
            // Trên một số OS, mapping tồn tại lâu hơn channel.
            // File thử nghiệm được dọn khi hệ điều hành cho phép.
            try { Files.deleteIfExists(file); } catch (IOException ignored) { }
        }
    }
}
```

```bash
javac --release 17 MappedForceDemo.java
java MappedForceDemo
```

Kết quả đã kiểm tra:

```text
mapped-view=AB00
```

Ví dụ sửa hai byte trong mapping, gọi `mapped.force()` rồi đọc lại **view của chính buffer**. Kết quả `AB00` kiểm tra logic trong bộ nhớ ánh xạ; **không thể dùng output này chứng minh khả năng chống mất điện**. `duplicate().position(0)` đặt cursor của *buffer sao* về đầu, không tác động file pointer của channel. Khi dùng mapping trong ứng dụng dài hạn, kiểm soát kích thước vùng ánh xạ, tính nhất quán với writer khác, quyền truy cập và vòng đời tài nguyên.

## 6. SYNC, DSYNC và force khác nhau ở thời điểm yêu cầu đồng bộ

Java có tùy chọn mở file `StandardOpenOption.SYNC` và `StandardOpenOption.DSYNC`, phù hợp khi muốn đồng bộ thường xuyên:

| Cơ chế | Khi nào đồng bộ? | Ý nghĩa |
| --- | --- | --- |
| `FileChannel.force(false)` | **Tại điểm gọi force** | Yêu cầu đồng bộ nội dung file đã cập nhật thuộc phạm vi API. |
| `FileChannel.force(true)` | **Tại điểm gọi force** | Yêu cầu đồng bộ nội dung và metadata file. |
| `StandardOpenOption.DSYNC` | Theo mỗi cập nhật được thực hiện bằng kênh mở với option này | Yêu cầu cập nhật **nội dung** được ghi đồng bộ xuống thiết bị, theo hợp đồng NIO. |
| `StandardOpenOption.SYNC` | Theo mỗi cập nhật được thực hiện bằng kênh mở với option này | Yêu cầu cập nhật **nội dung và metadata** được ghi đồng bộ xuống thiết bị. |
| `MappedByteBuffer.force()` | **Tại điểm gọi force trên mapping** | Đồng bộ thay đổi của mapped buffer theo hợp đồng của nó. |

`SYNC` / `DSYNC` có thể tăng độ trễ và số thao tác I/O do cần đồng bộ liên tục. `force` cho phép gom nhiều ghi thành một batch trước khi đồng bộ (group commit) nhưng mở rộng số thay đổi chưa được force giữa hai lần cam kết. Không có lựa chọn “tốt nhất” độc lập với yêu cầu mất dữ liệu chấp nhận được, latency, throughput và filesystem.

```java
// Đoạn minh họa API: path là Path hợp lệ, writable.
try (FileChannel channel = FileChannel.open(path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.SYNC)) {
    ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3});
    while (data.hasRemaining()) {
        int n = channel.write(data);
        if (n == 0) throw new IOException("Write stalled");
    }
}
```

Tùy chọn `SYNC` cần môi trường/implementation hỗ trợ; có thể phát sinh `UnsupportedOperationException` hoặc `IOException` tùy nguyên nhân. Đoạn trên minh họa cách lựa chọn mode, **không phải source Java độc lập** (cần import các lớp và khai báo `path`). Không tự thêm `APPEND` hay `TRUNCATE_EXISTING` khi nghiệp vụ không yêu cầu: chúng thay đổi semantics của file và điều kiện phục hồi.

### Có nên gọi force sau mỗi record?

Tùy mức cam kết:

- **Một record cần xác nhận ngay:** ghi đủ, force, rồi mới trả thành công nếu hợp đồng dữ liệu yêu cầu độ bền tới điểm đó.
- **Nhiều record có thể xác nhận theo batch:** ghi record và metadata phục hồi, force theo batch, ACK những record đã được bao gồm trong lần force hoàn tất.
- **Log hiệu năng cao:** dùng write-ahead log hoặc cơ chế durability của database chuyên dụng với nhóm commit/CRC/checkpoint; không thay thế protocol bằng một `force` tùy ý.

Việc `force` nhiều lần thường đắt hơn khi yêu cầu sync làm tăng latency; đo đúng môi trường, kể cả ổ NVMe, filesystem mạng, container và volume orchestration. Không hứa trước mức nhanh/chậm tuyệt đối.

## 7. Phạm vi bảo đảm qua hệ điều hành và loại thiết bị

| Môi trường | Có thể kết luận gì? | Điều cần kiểm chứng |
| --- | --- | --- |
| **Tệp trên local filesystem** như ext4/XFS/APFS/NTFS với storage device cục bộ | Javadoc `FileChannel.force` đưa ra hợp đồng force với thiết bị local cho các thay đổi được hỗ trợ. | OS, filesystem, driver, controller, cơ chế flush/cache và thực tế thiết bị có tuân thủ lệnh đồng bộ hay không. |
| **NFS/SMB/ổ mạng** | Java **không bảo đảm tương đương local device**. | Server thực sự cam kết ghi bền hay chỉ nhận vào cache; network reconnect, mount options, cơ chế đồng bộ bên server. |
| **Ổ ảo/cloud volume/container mount** | Đường truyền ghi có thể đi qua hypervisor, driver, lớp overlay, mạng hay thiết bị khác. | Cái được xem là local theo implementation; hợp đồng durability của tầng hạ tầng và những giới hạn riêng. |
| **Filesystem trong memory** như RAM-backed tmpfs | Việc đồng bộ API không biến dữ liệu volatile thành dữ liệu còn sau mất điện. | Mục tiêu tồn tại sau crash hay chỉ sau restart process? Thiết kế storage bền vững phù hợp. |
| **Thiết bị có write-back cache** | Dữ liệu có thể phải đi qua các cache của controller/SSD. | Power-loss protection, firmware và cơ chế flush mà thiết bị thật sự thực hiện. |

Bảng mô tả cách **đánh giá mức rủi ro**, không khẳng định mọi cấu hình được xếp đúng vào một loại vật lý đơn giản. Ví dụ volume cloud có thể hiển thị là block device “local” trước JVM nhưng backing storage thực tế được cung cấp qua mạng; cần tra đúng cam kết của dịch vụ lưu trữ.

Với ổ đĩa có cache không được bảo vệ khi mất nguồn, `force` không thể tự tạo bộ lưu điện hay sửa firmware gian dối. Ngược lại, trên thiết bị và filesystem tuân thủ lệnh flush, `force` là cách chuẩn Java bày tỏ yêu cầu đồng bộ dữ liệu. Câu “dữ liệu chắc chắn không thể mất” là phát biểu mạnh hơn hợp đồng Java và thực tế phần cứng.

### Crash của ứng dụng, crash OS và mất điện khác nhau

- **Tiến trình Java đột ngột dừng:** OS vẫn chạy, cache OS có thể tiếp tục được ghi ra đĩa. Đây **không** là bài kiểm thử đầy đủ cho force.
- **Kernel/system crash:** cache OS có thể biến mất hoặc chưa kịp đồng bộ; cơ chế force và filesystem recovery trở nên quan trọng.
- **Mất điện hoặc mất thiết bị:** còn phụ thuộc controller cache, storage firmware, hệ thống nguồn và thiết bị backing storage.

Do đó kiểm thử bằng cách `System.exit` hoặc kill process rồi thấy dữ liệu còn **không chứng minh** dữ liệu sẽ còn khi máy mất điện đúng thời điểm.

## 8. Những giới hạn không được suy diễn từ force

### Không bao phủ mọi đối tượng trong một giao dịch

Gọi `force(true)` trên file A không có nghĩa file B đã được flush. Hai file được cập nhật theo trình tự A → B có thể được phục hồi ở trạng thái chỉ một bên đã thay đổi nếu hệ thống gặp sự cố giữa chừng. Muốn cập nhật nhiều file như một đơn vị logic phải có transaction protocol như WAL, manifest + version, commit marker hoặc giải pháp database.

### Không bảo đảm thứ tự logic tự động khi có nhiều writer

Nếu các thread hoặc process cùng ghi, cần lock/ownership/offset độc lập hoặc cấu trúc record để tránh chồng lấn. `FileChannel.force` không khóa file, không ngăn task khác sửa file trong khi force và không cam kết một thứ tự giao dịch cấp ứng dụng. Xây dựng rõ “ai là writer”, “thời điểm nào coi record đã commit”, “khi khởi động lại phát hiện record dở như thế nào”.

### Không đảm bảo đọc ra trạng thái nhất quán nếu bản ghi đang được sửa

`Files.readString` sau `force` có thể nhìn thấy dữ liệu hợp lệ ở lần chạy bình thường, nhưng reader chạy trong lúc writer cập nhật nhiều offset có thể đọc kết hợp dữ liệu cũ và mới. Dùng snapshot, file tạm + rename, khóa đọc/ghi, hoặc phiên bản record với checksum/sequence nếu logic đòi hỏi một snapshot nhất quán.

### Không chứng minh full durability bằng một output test

Không tồn tại output chuẩn kiểu `"durable=true"` do `force` trả về. Ngoại lệ hoặc hoàn tất bình thường chỉ cho biết kết quả lời gọi trong điều kiện hiện tại. Một bài kiểm thử thực tế có thể xác minh readback nội dung, lỗi I/O, thời gian sync và phục hồi sau fault injection, nhưng không thể biến một lần test thành bảo đảm cho mọi ổ đĩa.

## 9. Quy trình lưu một bản ghi bền vững hơn

Một định dạng log record đơn giản có thể gồm:

```text
[magic/version][length][payload][checksum][commit marker/sequence]
```

Định dạng này chỉ là phác thảo: bố cục, checksum và trình tự commit phải được thiết kế để phân biệt record hoàn chỉnh với record dở sau crash. Một hệ thống có thể:

1. Cấp phát offset record và ghi phần header/payload/checksum đầy đủ; kiểm tra mọi lần write, short write và lỗi.
2. `force` những byte cần thiết theo giao thức, rồi ghi commit marker hoặc cập nhật manifest nếu thiết kế yêu cầu.
3. `force` lần cuối cho dữ liệu đánh dấu commit và metadata liên quan.
4. Chỉ ACK bản ghi sau khi force hoàn tất và giao thức nhất quán; nếu force lỗi, đánh dấu trạng thái chưa xác định/không xác nhận.
5. Khi khởi động lại, quét record hợp lệ theo length, checksum, version hoặc marker; dừng ở record không hoàn chỉnh và có chính sách repair.

Thứ tự đồng bộ thực tế phụ thuộc giao thức; ví dụ việc ghi marker và payload cùng một lần force không đảm bảo ứng dụng có thể bỏ qua partial/torn write nếu mất điện giữa chừng. Nên dùng giải pháp database/WAL sẵn có khi yêu cầu độ bền và recovery phức tạp.

## 10. Lỗi phổ biến và cách xử lý

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `write` trả số byte dương nên báo “đã lưu bền vững”. | Byte có thể chỉ đang trong OS cache. | Ghi đủ, gọi `force` tại điểm cam kết phù hợp, chỉ ACK khi hoàn tất. |
| Một lần `channel.write(buffer)` được xem là đủ. | FileChannel có thể ghi ngắn; buffer còn dữ liệu. | Kiểm tra `buffer.hasRemaining()` và lặp; kiểm tra `0`/exception. |
| `channel.force(false)` được coi tương đương `force(true)`. | False chỉ yêu cầu force nội dung; metadata có phạm vi khác. | Chọn theo cập nhật thực tế; phân biệt metadata file và directory entry. |
| `Files.writeString` rồi mở một channel khác chỉ để force và coi mọi ghi đều được bảo đảm. | Hợp đồng `FileChannel.force` giới hạn nguồn thay đổi được cam kết. | Dùng một writer/channel được quản lý nhất quán hoặc cơ chế sync rõ ràng của API sử dụng. |
| Dữ liệu thay bằng `mapped.put(...)` rồi chỉ gọi `channel.force`. | Force của channel không nhất thiết bao phủ cập nhật mapped buffer. | Gọi `mapped.force()` theo hợp đồng mapping. |
| `close`/`flush` thay cho force. | Khác về hợp đồng độ bền. | Dùng `FileChannel.force` hoặc `SYNC`/`DSYNC` khi yêu cầu đồng bộ file. |
| Gọi force trên file tạm rồi rename và coi cả tên file đã an toàn sau mất điện. | Ghi metadata thư mục/rename có hợp đồng riêng. | Kiểm tra atomic rename, fsync directory/cơ chế OS cụ thể khi cần đảm bảo tên tệp. |
| Dùng force như khóa hoặc transaction. | Không bảo vệ vùng byte chồng lấn hay read-modify-write. | Dùng lock/protocol cập nhật riêng; force sau khi đạt trạng thái nhất quán. |
| Cho rằng remote/network filesystem có bảo đảm như local. | Javadoc không hứa cùng semantics. | Xác minh server, provider, mount, dịch vụ lưu trữ. |
| Bỏ qua `IOException` khi force. | Không thể tuyên bố commit thành công sau lỗi sync. | Trả lỗi, ghi log có ngữ cảnh, phục hồi/đánh dấu trạng thái không xác định. |

### Xử lý lỗi đúng tại điểm force

```java
// Đoạn minh họa: channel đã mở và dữ liệu đã được ghi đầy đủ.
try {
    channel.force(true);
    // Chỉ tại đây mới có thể báo commit theo phạm vi bảo đảm của force.
} catch (IOException e) {
    // Không báo thành công; chuyển sang xử lý I/O failure/recovery.
    throw e;
}
```

Nếu exception xảy ra giữa force, **không thể tự suy ra rằng chưa byte nào tới storage**, cũng không thể khẳng định tất cả đã tới. Phải áp dụng chính sách khôi phục theo log/checksum/transaction id để tránh nhân đôi hoặc mất giao dịch sau retry.

## 11. Checklist trước khi đưa vào production

1. **Nội dung nào phải bền vững?** Byte payload, kích thước file, metadata, tên file sau rename, một hay nhiều file?
2. **Điểm cam kết ở đâu?** Trước hay sau ACK? Batch kích thước bao nhiêu? Mức mất dữ liệu chấp nhận được?
3. **Writer dùng API nào?** `FileChannel`, `FileOutputStream`/`RandomAccessFile.getChannel`, mapped memory hay một handle khác? Chọn force đúng phạm vi.
4. **Có ghi đủ chưa?** Kiểm tra short write, `ByteBuffer`, offset, chồng lấn và thứ tự ghi; force không sửa race.
5. **Storage là gì?** Local file, NFS/SMB, tmpfs, cloud volume, container overlay? Đọc cam kết của platform thay vì suy từ OS name.
6. **Cần atomicity và recovery không?** Nếu cần, thiết kế WAL/manifest/checksum, atomic rename và đồng bộ directory nếu được hỗ trợ.
7. **Lỗi force được xử lý thế nào?** Không ACK giả; log context, đánh dấu transaction chưa xác định và phục hồi an toàn.
8. **Hiệu năng ra sao?** Đo p95/p99 latency, throughput và số lần sync; cân nhắc `SYNC`, `DSYNC` hoặc group commit.
9. **Kiểm thử những mức lỗi nào?** Unit test kiểm chứng bytes, integration test trên file store mục tiêu, fault injection/crash recovery cho dữ liệu nghiệp vụ. Không coi readback hoặc kill JVM là phép chứng minh power-loss durability.
10. **Ai sở hữu tài nguyên?** Dùng `try-with-resources`, không đóng channel sớm khi task khác đang force/ghi; kiểm soát vòng đời của mapping.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Sau `channel.write(buffer)`, gọi `Files.readAllBytes(path)` thấy bytes mới. Đã chứng minh dữ liệu chống mất điện chưa?

**Chưa.** Việc đọc lại có thể dùng cache của OS. Cần cơ chế force/sync có hợp đồng phù hợp và kiểm chứng tầng lưu trữ.

**Câu 2.** `force(false)` có yêu cầu force metadata file không?

**Không.** Chỉ nội dung phải được force theo hợp đồng. `force(true)` yêu cầu nội dung và metadata, nhưng tác dụng thực tế của tham số phụ thuộc OS.

**Câu 3.** File lưu trên NFS có thể nhận cùng cam kết độ bền với local device từ Javadoc `FileChannel.force` không?

**Không.** Javadoc nói rõ nếu không phải local storage device thì không đưa ra bảo đảm tương đương; phải khảo sát semantics của server/OS/provider.

**Câu 4.** Nếu nội dung được thay đổi qua `MappedByteBuffer.put` thì nên dùng phương thức force nào?

**`MappedByteBuffer.force()`.** `FileChannel.force` có thể hoặc không đồng bộ những thay đổi tạo ra qua mapping.

**Câu 5.** `force(true)` có đảm bảo sau crash thao tác `Files.move(temp, final, ATOMIC_MOVE)` vẫn tồn tại dưới tên `final` không?

**Không thể suy ra chỉ từ `force(true)` của file.** Atomic move và bền vững của directory entry là những vấn đề khác; cần quy trình và kiểm chứng đặc thù filesystem/OS.

**Câu 6.** Muốn giảm số lần force, có thể gom 100 record thành batch và force một lần không?

**Có, nếu nghiệp vụ chấp nhận ranh giới cam kết của batch.** Phải ghi đủ, duy trì record format nhất quán, và chỉ ACK những record được bảo đảm sau lần force hoàn tất. Nếu ứng dụng sụp trước khi force, batch chưa được cam kết theo chính sách đó.

**Câu 7.** `force` có giải quyết hai writer cập nhật cùng offset không?

**Không.** Phải phối hợp writer/lock/protocol dữ liệu; force chỉ giải quyết yêu cầu đồng bộ thay đổi thuộc phạm vi của nó.

## Tài liệu chính thức

- Oracle Java SE 21 — [`FileChannel`: `force(boolean)`, các phương thức write, map và lock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html).
- Oracle Java SE 21 — [`MappedByteBuffer`: `force()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/MappedByteBuffer.html).
- Oracle Java SE 21 — [`StandardOpenOption`: `SYNC` và `DSYNC`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html).
- Oracle Java SE 21 — [`Files`: `newByteChannel` / `move` và `ATOMIC_MOVE`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html).

**Kết luận cần vận dụng:** `FileChannel.force` là yêu cầu đồng bộ thay đổi file xuống thiết bị lưu trữ trong phạm vi hợp đồng của Java; `metaData` điều khiển phạm vi content/metadata. Để đạt một hệ thống lưu trữ bền vững thực sự, phải kết hợp force đúng điểm cam kết với ghi đầy đủ, định dạng có thể phục hồi, phối hợp writer, và kiểm chứng OS/filesystem/hạ tầng mà ứng dụng triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-008">Quan hệ với FileInputStream, FileOutputStream và RandomAccessFile</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa FileChannel với FileInputStream, FileOutputStream và RandomAccessFile

## 1. Một tệp, hai cách sử dụng API

Trong Java I/O, ba lớp `java.io.FileInputStream`, `java.io.FileOutputStream` và `java.io.RandomAccessFile` đã có từ trước Java NIO. NIO bổ sung `java.nio.channels.FileChannel`, cho phép thao tác với tệp thông qua `ByteBuffer`, vị trí byte, chuyển dữ liệu giữa channel, ánh xạ tệp và nhiều khả năng khác.

Ba lớp I/O truyền thống đều có phương thức **`getChannel()`**. Phương thức này trả về **`FileChannel` gắn với chính đối tượng đã mở tệp**, vì vậy bạn có thể làm việc với cùng một tệp và trạng thái liên quan bằng API cũ lẫn API channel.

~~~text
FileInputStream  ── getChannel() ──> FileChannel (READ)
FileOutputStream ── getChannel() ──> FileChannel (WRITE)
RandomAccessFile ── getChannel() ──> FileChannel (READ hoặc READ+WRITE)
       │
       └── Cùng tài nguyên, vị trí tệp và vòng đời đóng
~~~

**Điểm cốt lõi:** channel lấy qua `getChannel()` có quan hệ chặt với đối tượng nguồn. Di chuyển vị trí bên channel ảnh hưởng vị trí đọc/ghi của stream hoặc `RandomAccessFile` và ngược lại. Nội dung và độ dài của tệp cũng là cùng một tài nguyên. Đóng một bên làm bên còn lại không thể tiếp tục sử dụng.

Bài này phân biệt chính xác các quyền đọc/ghi, vị trí chia sẻ, quản lý vòng đời, ảnh hưởng của append/buffering và trường hợp nên dùng trực tiếp `FileChannel.open(...)`.

## 2. Bảng so sánh nhanh

| Đối tượng gốc | Cách lấy channel | Quyền channel nhận được | Phương thức I/O quen thuộc |
| --- | --- | --- | --- |
| `FileInputStream` | `input.getChannel()` | Đọc | `read()`, `read(byte[])` |
| `FileOutputStream` | `output.getChannel()` | Ghi | `write(int)`, `write(byte[])` |
| `RandomAccessFile(file, "r")` | `raf.getChannel()` | Đọc | `read(...)`, `seek(...)`, `getFilePointer()` |
| `RandomAccessFile(file, "rw")` | `raf.getChannel()` | Đọc và ghi | `read(...)`, `write(...)`, `seek(...)`, `setLength(...)` |

Với `RandomAccessFile`, các mode `"rws"` và `"rwd"` cũng cho phép đọc/ghi và yêu cầu đồng bộ cập nhật theo hợp đồng của mode; có sự khác biệt về metadata giữa hai mode. Việc đồng bộ thực tế còn phụ thuộc hệ thống lưu trữ.

`getChannel()` trả về đối tượng channel riêng được liên kết với **đối tượng stream/RAF đó**. Các lần gọi `getChannel()` trên cùng đối tượng trả về cùng channel đã liên kết; gọi lại không tạo thêm một descriptor hay một con trỏ độc lập.

Ví dụ:

~~~java
FileInputStream in = new FileInputStream("data.bin");
FileChannel first = in.getChannel();
FileChannel second = in.getChannel();
System.out.println(first == second); // true
in.close();
~~~

Đây là **đoạn code minh họa**, chỉ chạy khi `data.bin` tồn tại; cần import `java.io.FileInputStream` và `java.nio.channels.FileChannel`. Trong ứng dụng thật, ưu tiên `try-with-resources` như ví dụ hoàn chỉnh phía dưới.

**Đừng hiểu kiểu `FileChannel` tự động cấp mọi quyền.** Phương thức `write(...)` trên channel nhận từ `FileInputStream` không hợp lệ vì channel đó chỉ mở để đọc; tương tự `read(...)` trên channel của `FileOutputStream` không hợp lệ khi channel chỉ mở để ghi. Các lỗi điển hình lần lượt là `NonWritableChannelException` và `NonReadableChannelException`.

## 3. FileInputStream và FileChannel: chia sẻ vị trí đọc

`FileInputStream` cung cấp cách đọc từng byte hoặc mảng byte. `FileChannel` cùng tệp cho phép đọc vào `ByteBuffer`, thay đổi vị trí và sử dụng các khả năng NIO khác.

~~~java
try (FileInputStream input = new FileInputStream(path.toFile())) {
    FileChannel channel = input.getChannel();
    // ...
}
~~~

Đây là mảnh code minh họa: `path` phải là một `Path` hợp lệ; nhập các kiểu từ `java.io`, `java.nio.file` và `java.nio.channels`.

Khi stream đọc `n` byte thành công, vị trí của channel tăng theo `n`. Nếu đổi `channel.position(offset)`, lần `input.read()` tiếp theo sẽ bắt đầu tại vị trí đã đặt.

~~~text
File content: A B C D E
Offsets:      0 1 2 3 4

input.read()       → trả 'A', channel.position() = 1
channel.position(3)
input.read()       → trả 'D', channel.position() = 4
~~~

Phương thức `channel.position()` thể hiện con trỏ byte của tệp, không phải offset ký tự của văn bản. `input.read()` trả một byte dưới dạng số nguyên 0–255, hoặc `-1` ở EOF. Chuyển về `char` như `(char) input.read()` chỉ có ý nghĩa trực tiếp cho dữ liệu ASCII đơn byte.

### FileInputStream đọc, FileChannel có thể seek nhưng không ghi

`FileInputStream.getChannel()` trả về channel mở **READ**. Một số thao tác cho phép:

~~~java
FileChannel channel = input.getChannel();
long fileSize = channel.size();
channel.position(0);
ByteBuffer dst = ByteBuffer.allocate(16);
int read = channel.read(dst);
~~~

Đây cũng là mảnh code minh họa; nó sử dụng `input` đang mở và cần import `java.nio.ByteBuffer`. Sau `channel.read(dst)`, nếu cần lấy dữ liệu từ buffer phải gọi `dst.flip()`.

Các lời gọi ghi như `channel.write(src)`, `truncate(...)` hoặc `map(FileChannel.MapMode.READ_WRITE, ...)` không được phép chỉ vì bạn đã có `FileChannel`. Muốn ghi phải mở tệp bằng nguồn cho phép ghi.

### Có thể dùng `FileChannel.read(dst, offset)` không?

Có. `FileChannel` có overload `read(ByteBuffer dst, long position)` đọc tại **offset riêng** mà không thay đổi `channel.position()` chung. Vì channel và `FileInputStream` chia sẻ vị trí chung, cách đọc định vị này cũng không dịch con trỏ logic của `input`.

Tuy nhiên, `dst.position()` vẫn tăng theo số byte nhận. Đừng nhầm việc position của channel không đổi với việc ByteBuffer không thay đổi.

## 4. FileOutputStream và FileChannel: chia sẻ vị trí ghi

`FileOutputStream` ghi byte ra tệp. Channel của nó mở **WRITE**, và vị trí của stream/channel liên kết:

~~~java
try (FileOutputStream output = new FileOutputStream(path.toFile())) {
    FileChannel channel = output.getChannel();
    output.write(new byte[] {'A', 'B', 'C'});
    System.out.println(channel.position()); // 3
}
~~~

Đây là mảnh code minh họa; constructor `new FileOutputStream(file)` **cắt/ghi lại file cũ ngay lúc mở** nếu file đã tồn tại. Vì vậy, phải chọn đúng file và chế độ trước khi thực thi.

Khi đã có channel của output stream, có thể sử dụng `channel.position(index)` rồi gọi `output.write(...)` để ghi đè tại index được đặt:

~~~text
Ban đầu: A B C D E
Offset:  0 1 2 3 4

channel.position(2)
output.write('X')

Kết quả: A B X D E
          position = 3
~~~

**Ghi đè không phải chèn:** `C` được thay bằng `X`, các byte D, E không tự dịch sang phải.

Channel lấy từ `FileOutputStream` **không mở để đọc**, nên nếu muốn đọc ngược dữ liệu vừa ghi, không thể gọi `channel.read(dst)` một cách mặc nhiên. Hãy dùng `RandomAccessFile("rw")`, `FileChannel.open(path, READ, WRITE)` hoặc một kênh đầu vào độc lập với quy tắc phối hợp phù hợp.

### Chế độ append là ngoại lệ rất quan trọng

Có thể tạo stream ghi nối:

~~~java
FileOutputStream output = new FileOutputStream(file, true);
~~~

Đối số `true` nghĩa là append. Khi đó, vị trí khởi đầu của channel liên kết phản ánh **kích thước file**, và các lần ghi qua stream hướng đến cuối tệp. Tương tác giữa seek thủ công trên channel và hành vi append của provider/nền tảng có những ràng buộc; không dựa vào `channel.position(0)` để cố ghi đè đầu tệp khi stream được mở để append.

Nếu bài toán yêu cầu truy cập ngẫu nhiên và ghi đè offset bất kỳ, hãy **mở không ở chế độ append**. Nếu bài toán chỉ cần append log, sử dụng mode phù hợp và đồng bộ khi có nhiều writer. `append` không đồng nghĩa một chuỗi thao tác đọc-size → seek → ghi được bảo đảm nguyên tử.

## 5. RandomAccessFile và FileChannel: hai bộ API cho cùng con trỏ

`RandomAccessFile` khác input/output stream đơn hướng vì bản thân nó đã hỗ trợ **đọc, ghi, nhảy vị trí và đổi độ dài**. Đây là cầu nối dễ thấy nhất giữa API I/O truyền thống và `FileChannel`.

~~~java
try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
    FileChannel channel = raf.getChannel();
    raf.seek(10);
    System.out.println(channel.position()); // 10

    channel.position(3);
    System.out.println(raf.getFilePointer()); // 3
}
~~~

Đây là mảnh code minh họa; `file` phải là `java.io.File` hợp lệ và tệp được mở `"rw"`. `raf.seek(long)` và `channel.position(long)` điều khiển cùng vị trí tệp. `raf.getFilePointer()` và `channel.position()` phản ánh cùng offset logic.

Các tương ứng chính:

| RandomAccessFile | FileChannel | Quan hệ |
| --- | --- | --- |
| `raf.seek(pos)` | `channel.position(pos)` | Đặt con trỏ byte dùng chung |
| `raf.getFilePointer()` | `channel.position()` | Hỏi vị trí hiện tại |
| `raf.length()` | `channel.size()` | Hỏi độ dài logic của cùng tệp |
| `raf.read(...)` | `channel.read(buffer)` | Đọc tại vị trí hiện tại, làm con trỏ tiến |
| `raf.write(...)` | `channel.write(buffer)` | Ghi tại vị trí hiện tại, làm con trỏ tiến |
| `raf.setLength(n)` | `channel.truncate(n)` | Cùng liên quan tới độ dài, nhưng khác nhau khi **n lớn hơn size cũ** |

### Khác biệt lớn: `setLength` có thể mở rộng, `truncate` thì không

Đây là ranh giới dễ nhầm lẫn nhất.

- `RandomAccessFile.setLength(n)`: nếu `n` nhỏ hơn size cũ thì cắt; nếu `n` lớn hơn thì **mở rộng** tệp. Byte trong phần mới mở rộng **không được xác định** chung bởi API.
- `FileChannel.truncate(n)`: nếu `n` nhỏ hơn size cũ thì cắt; nếu `n` lớn hơn hoặc bằng size cũ thì **không** mở rộng tệp.

~~~text
Current size = 5

raf.setLength(8)       → new size = 8
channel.size()         → 8
bytes at offsets 5..7 → không được API định nghĩa

channel.truncate(4)    → new size = 4
raf.length()           → 4
~~~

Khi cắt tệp ngắn đi, con trỏ lớn hơn giới hạn mới bị điều chỉnh xuống. `setLength` cũng có thể điều chỉnh file pointer theo hợp đồng của phương thức. Các thao tác này thay đổi **cùng tệp**, vì thế `raf.length()` và `channel.size()` quan sát cùng độ dài sau cập nhật thành công.

Đừng dùng phần vừa mở rộng chưa được ghi giá trị như dữ liệu có ý nghĩa. Muốn định dạng file có padding xác định, hãy ghi byte padding rõ ràng.

## 6. Chương trình Java 17+ hoàn chỉnh: ba đối tượng cùng cách chia sẻ trạng thái

Chương trình sau sử dụng một tệp tạm và mở ba đối tượng gốc **lần lượt**, không mở đồng thời. Nhờ đó, ta có thể quan sát đầy đủ hành vi chia sẻ con trỏ mà không lẫn với các vấn đề cạnh tranh I/O.

Lưu thành `FileChannelLegacyBridgeDemo.java`:

~~~java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileChannelLegacyBridgeDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("channel-bridge-", ".bin");

        try {
            FileChannel outputChannel;

            try (FileOutputStream out =
                         new FileOutputStream(file.toFile())) {
                outputChannel = out.getChannel();

                out.write("ABCDE".getBytes(StandardCharsets.US_ASCII));
                System.out.println("output position after write="
                        + outputChannel.position());

                outputChannel.position(2);
                out.write('X');
                System.out.println("output position after seek/write="
                        + outputChannel.position());
            }

            System.out.println("output channel open after close="
                    + outputChannel.isOpen());

            try (FileInputStream in =
                         new FileInputStream(file.toFile())) {
                FileChannel inputChannel = in.getChannel();

                System.out.println("input first=" + (char) in.read());
                System.out.println("input channel position="
                        + inputChannel.position());

                inputChannel.position(3);
                System.out.println("input after channel seek="
                        + (char) in.read());
                System.out.println("input position after read="
                        + inputChannel.position());
            }

            try (RandomAccessFile raf =
                         new RandomAccessFile(file.toFile(), "rw")) {
                FileChannel randomChannel = raf.getChannel();

                raf.seek(1);
                System.out.println("raf.seek channel position="
                        + randomChannel.position());

                randomChannel.position(4);
                System.out.println("channel.position raf pointer="
                        + raf.getFilePointer());

                raf.writeByte('Z');
                System.out.println("raf write channel position="
                        + randomChannel.position());

                raf.setLength(7);
                System.out.println("after setLength: raf.length="
                        + raf.length() + ", channel.size="
                        + randomChannel.size());

                randomChannel.truncate(4);
                System.out.println("after truncate: raf.length="
                        + raf.length() + ", raf pointer="
                        + raf.getFilePointer());
            }

            System.out.println("final="
                    + Files.readString(file, StandardCharsets.US_ASCII));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac FileChannelLegacyBridgeDemo.java
java FileChannelLegacyBridgeDemo
~~~

Stdout mong đợi trên hệ thống tệp mặc định thông dụng:

~~~text
output position after write=5
output position after seek/write=3
output channel open after close=false
input first=A
input channel position=1
input after channel seek=D
input position after read=4
raf.seek channel position=1
channel.position raf pointer=4
raf write channel position=5
after setLength: raf.length=7, channel.size=7
after truncate: raf.length=4, raf pointer=4
final=ABXD
~~~

Giải thích từng bước:

1. `FileOutputStream` ghi `ABCDE`, position của channel thành 5. Di chuyển channel về offset 2 rồi gọi `out.write('X')` thay byte C bằng X, nhận `ABXDE`. Position chung thành 3.
2. Ra khỏi `try (out)`, stream được đóng; channel liên kết cũng đã đóng. `outputChannel.isOpen()` vì vậy trả `false`.
3. `FileInputStream` mở lại tệp từ đầu, đọc `A` ở offset 0 nên channel position = 1. `inputChannel.position(3)` làm lần `in.read()` sau đọc byte D, rồi position thành 4.
4. `RandomAccessFile("rw")` mở tệp và cho phép cả đọc lẫn ghi. `raf.seek(1)` phản ánh thành position 1 ở channel. `randomChannel.position(4)` phản ánh thành file pointer 4 ở RAF.
5. `raf.writeByte('Z')` thay E bằng Z, tệp thành `ABXDZ` và position thành 5.
6. `raf.setLength(7)` mở rộng tệp từ 5 lên 7 byte. `randomChannel.size()` lập tức phản ánh 7; ví dụ **không đọc** hai byte mới vì chúng không có giá trị được API bảo đảm.
7. `randomChannel.truncate(4)` cắt tệp từ 7 về 4 byte, làm file pointer đang ở 5 bị kéo về 4. Tệp cuối cùng chỉ còn `ABXD`.
8. `Files.readString` đọc sau khi RAF đã được đóng, để kiểm tra kết quả cuối; `finally` xóa tệp tạm.

Các chữ `A`, `D` được cast từ byte sang `char` an toàn trong ví dụ vì dữ liệu chỉ gồm ASCII. Với văn bản UTF-8 nhiều byte, hãy dùng charset decoder phù hợp thay vì cast từng byte.

## 7. Đóng stream hay đóng channel: một quan hệ vòng đời

Một channel lấy qua `getChannel()` **không phải handle độc lập cần được quản lý như một file thứ hai**.

- `input.close()` đóng channel liên kết với `FileInputStream`; `output.close()` và `raf.close()` cũng đóng channel tương ứng.
- Theo hợp đồng `FileChannel`, **đóng channel liên kết cũng đóng đối tượng stream hoặc RAF gốc**.
- Sau khi đã đóng, thao tác I/O tiếp trên channel sẽ thất bại, thường với `ClosedChannelException`; thao tác trên stream/RAF đã đóng cũng không thể dùng tiếp.
- Phương thức `getChannel()` nhiều lần trên cùng một đối tượng không phải lệnh mở lại sau khi đóng.

Mẫu ưu tiên:

~~~java
try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
    FileChannel channel = raf.getChannel();
    // Dùng cả raf và channel trong cùng phạm vi sở hữu.
}
~~~

Đây là mảnh code minh họa, với `file` kiểu `File` hợp lệ. Chỉ khai báo **nguồn sở hữu tài nguyên** trong `try-with-resources` là đủ cho vòng đời thông thường. Nếu code cố đóng `channel` giữa chừng rồi tiếp tục dùng `raf`, thiết kế sẽ lỗi do tài nguyên đã đóng.

Nếu chuyển channel từ một hàm nội bộ ra ngoài, phải thống nhất **ai có quyền đóng**. Ví dụ một hàm tiện ích nhận `FileChannel` của caller thường không nên tự đóng nó nếu caller còn sử dụng `FileInputStream` liên kết.

## 8. Hai cách mở FileChannel không có cùng quan hệ sở hữu

Có hai con đường:

**A. Lấy channel từ đối tượng Java I/O đã mở**

~~~java
FileChannel channel = stream.getChannel();
~~~

Khi dùng kiểu này, channel gắn với chính `FileInputStream`/`FileOutputStream`/`RandomAccessFile` được truyền vào, chia sẻ position và vòng đời.

**B. Mở trực tiếp từ Path**

~~~java
try (FileChannel channel = FileChannel.open(
        path,
        java.nio.file.StandardOpenOption.READ,
        java.nio.file.StandardOpenOption.WRITE)) {
    // Channel này được quản lý độc lập.
}
~~~

Khi mở trực tiếp, bạn không có một `FileInputStream`/`FileOutputStream`/`RandomAccessFile` nguồn để chia sẻ pointer theo `getChannel()`. Nếu một stream khác cũng mở cùng tệp, chúng có thể **cùng thấy nội dung file**, nhưng không phải cùng cặp liên kết stream–channel nói trên. Trên filesystem thông thường, các lần mở độc lập có con trỏ độc lập; đừng suy luận rằng vì hai đường dẫn giống nhau nên `channel.position(...)` tự điều khiển pointer của mọi stream khác đang mở file đó.

Nếu muốn đơn thuần đọc và ghi bằng `ByteBuffer`, `FileChannel.open(path, options)` thường rõ ràng. Nếu đã có stream hoặc RAF và cần phối hợp API mới, `getChannel()` giúp tránh mở thêm một tài nguyên không cần thiết.

## 9. ByteBuffer và mảng byte: chuyển đổi cách tư duy khi kết hợp API

Stream truyền thống nhận hoặc trả từng byte/mảng byte:

~~~java
int value = in.read();
out.write(new byte[] {'A', 'B'});
~~~

FileChannel chủ yếu làm việc với `ByteBuffer`:

~~~java
ByteBuffer buffer = ByteBuffer.allocate(1024);
int n = channel.read(buffer);
buffer.flip(); // Chuẩn bị tiêu thụ n byte vừa đọc.
~~~

Các mảnh code ở trên dùng biến `in`, `out`, `channel` đã được khởi tạo theo chế độ thích hợp; chúng không phải chương trình độc lập.

Phân biệt hai nhóm con trỏ:

- **Con trỏ file** của stream/RAF/channel liên kết: byte tiếp theo trên tài nguyên đang mở.
- **`position`/`limit` của ByteBuffer**: byte tiếp theo trong vùng nhớ đệm.

Khi `channel.read(buffer)` đọc được `n` byte, file position và buffer position đều tăng `n`. Lúc đó `buffer.flip()` chỉ đặt lại giới hạn để đọc từ buffer; nó không đưa file position quay về đầu.

Khi `channel.write(buffer)` ghi ra file, buffer position và file position tiến theo số byte đã ghi. Nếu `write` chỉ chuyển được một phần, không được `buffer.clear()` rồi bỏ qua phần chưa ghi.

**Thận trọng khi mix stream + channel với bộ đệm cấp cao.** Nếu bạn bọc `FileInputStream` bằng `BufferedInputStream` hoặc `InputStreamReader`, wrapper có thể đọc trước nhiều byte vào vùng nhớ nội bộ. Lúc đó file position vật lý đã vượt quá số byte ứng dụng nhìn thấy. Thay đổi vị trí bằng channel trong khi wrapper còn giữ dữ liệu đệm có thể khiến dữ liệu quan sát bị lặp, bỏ qua hoặc không như ý. Với `BufferedOutputStream`, byte đang chờ flush có thể được ghi sau khi code đã seek bằng channel, dẫn đến nội dung không mong đợi.

Khi cần truy cập ngẫu nhiên, hãy ưu tiên một đường I/O nhất quán; nếu bắt buộc phối hợp wrapper, quản lý `flush`/trạng thái buffer và vị trí tệp một cách tường minh, tránh giả định chúng tự đồng bộ.

## 10. FileChannel bổ sung điều gì so với ba lớp nguồn?

`getChannel()` đặc biệt hữu ích vì `FileChannel` có API mà ba lớp Java I/O không cung cấp cùng hình thức:

- `read(ByteBuffer, long)` và `write(ByteBuffer, long)`: đọc/ghi tại offset chỉ định mà không thay đổi position chung của channel.
- `transferTo(...)`, `transferFrom(...)`: truyền dữ liệu giữa các channel tương thích với khả năng tối ưu của provider.
- `map(...)`: ánh xạ vùng file vào bộ nhớ.
- `lock(...)`/`tryLock(...)`: khóa vùng file với các giới hạn nền tảng và việc hợp tác của ứng dụng.
- `force(boolean)`: yêu cầu đẩy các cập nhật xuống thiết bị lưu trữ theo hợp đồng API.
- `size()`, `truncate(long)` và `position(long)`: quan sát/điều chỉnh độ dài và vị trí byte qua API NIO.

Nếu chỉ cần `read()` tuần tự từng byte, `FileInputStream` có thể đủ. Nếu chỉ ghi một mảng byte, `FileOutputStream` đơn giản hơn. Nếu cần đọc và ghi record theo offset kết hợp các kiểu dữ liệu nguyên thủy, `RandomAccessFile` cung cấp `readInt`, `writeLong`, `seek`, v.v. Khi cần ByteBuffer, positional I/O hoặc chuyển dữ liệu giữa channel, `FileChannel` phù hợp hơn.

**Không thể xem `getChannel()` là một phép nâng quyền.** Channel của FileInputStream vẫn chỉ đọc; channel của FileOutputStream vẫn chỉ ghi. Mọi thao tác đặc biệt còn phụ thuộc quyền tệp và hỗ trợ của hệ điều hành/provider.

## 11. Đồng thời: chia sẻ pointer có thể tạo race condition

Quan hệ chia sẻ vị trí thuận tiện trong chương trình một luồng, nhưng cần rất cẩn thận nếu nhiều thread dùng chung stream/channel:

~~~text
Thread A: input.getChannel().position(100)
Thread B: input.read()              // Đọc byte ở vị trí 100
Thread A: input.read()              // Không chắc còn ở vị trí 100
~~~

Trong ví dụ, dòng thứ hai có thể làm con trỏ tiến trước khi thread A đọc. `position(offset)` rồi `read()` là **hai thao tác**, không mặc nhiên nguyên tử khi trộn API giữa thread.

Để hạn chế xung đột:

1. Giao một stream/channel liên kết cho một thành phần chịu trách nhiệm quản lý position.
2. Nếu cần chia sẻ, đồng bộ một chuỗi thao tác seek + read/write ở mức ứng dụng.
3. Cân nhắc overload `FileChannel.read(dst, offset)`/`write(src, offset)` để thao tác với offset riêng mà không làm đổi position chung, nếu quyền truy cập cho phép.
4. Phân biệt xung đột **con trỏ** với xung đột **nội dung file**: positional I/O giúp hạn chế việc giẫm lên shared position, nhưng không tự loại bỏ việc nhiều writer ghi lên cùng vùng dữ liệu.

Đóng channel từ thread này trong khi thread khác đang sử dụng stream có thể làm thao tác của thread kia thất bại. Thiết kế ownership đóng cũng quan trọng không kém ownership con trỏ.

## 12. Lỗi phổ biến

### Sai 1. Nghĩ `getChannel()` tạo file/channel mới độc lập

Nó trả channel gắn với nguồn đang mở. Cùng một con trỏ và vòng đời; seek bên channel ảnh hưởng stream/RAF. Mở tệp mới độc lập bằng `FileChannel.open(path, ...)` là tình huống khác.

### Sai 2. Lấy channel từ input rồi gọi write

`FileInputStream.getChannel()` tạo channel chỉ đọc. `write` trên channel đó sẽ ném `NonWritableChannelException`. Muốn đọc/ghi, chọn `RandomAccessFile("rw")` hoặc mở trực tiếp `FileChannel` với quyền thích hợp.

### Sai 3. Lấy channel từ output rồi gọi read

`FileOutputStream.getChannel()` là channel chỉ ghi. Nó không tự có quyền đọc vì đang có kiểu `FileChannel`; `read` sẽ thất bại với `NonReadableChannelException`.

### Sai 4. Đóng channel rồi vẫn dùng stream hoặc RAF

Vì hai đối tượng gắn với cùng vòng đời, đóng channel sẽ làm nguồn không thể tiếp tục thao tác. Dùng một `try-with-resources` rõ quyền sở hữu; không đóng tài nguyên do caller quản lý khi trả về từ utility.

### Sai 5. Tưởng RandomAccessFile và FileChannel có hai con trỏ

`raf.seek(offset)` làm `channel.position()` thay đổi; `channel.position(offset)` làm `raf.getFilePointer()` thay đổi. Nếu dùng cả hai, phải ghi nhận rằng chúng điều khiển một vị trí.

### Sai 6. Dùng `truncate` để làm tệp dài ra

`FileChannel.truncate(newSize)` chỉ thu nhỏ khi cần; ngược lại `RandomAccessFile.setLength(newSize)` có thể mở rộng file. Phần mở rộng không bảo đảm các byte có giá trị xác định.

### Sai 7. Cố ghi đè đầu tệp từ stream append

`new FileOutputStream(file, true)` dành cho nối cuối. Không thiết kế logic seek/overwrite dựa vào giả định append sẽ tôn trọng một con trỏ thủ công theo cách bạn mong muốn.

### Sai 8. Trộn buffered wrapper với channel và tưởng con trỏ ứng dụng khớp con trỏ tệp

Wrapper có thể đọc trước, giữ byte chưa flush hoặc giải mã thành ký tự. Chỉ số byte đã tiêu thụ ở wrapper khác vị trí channel. Cần chủ động quản lý buffer hoặc dùng một API truy cập nhất quán.

### Sai 9. Không để ý quyền và lỗi I/O

Các ngoại lệ quan trọng bao gồm `NonReadableChannelException`, `NonWritableChannelException`, `ClosedChannelException`, `IOException`. Ngoài ra còn vấn đề file không tồn tại, permission, bộ nhớ đệm, lock và filesystem không hỗ trợ. Chọn nguồn và mode đúng ngay từ lúc mở.

## 13. Câu hỏi tự kiểm tra

1. `FileInputStream.getChannel()` có thể ghi dữ liệu không? **Không**, channel được mở đọc.
2. Stream đã đọc 3 byte; `input.getChannel().position()` thường là bao nhiêu? **3**, nếu chưa có thao tác khác thay đổi vị trí.
3. Vì sao `channel.position(10)` có thể đổi byte kế tiếp của `in.read()`? Vì chúng chia sẻ vị trí trên cùng đối tượng nguồn.
4. `FileOutputStream.getChannel()` có thể đọc dữ liệu vừa ghi không? **Không**, channel được mở ghi.
5. `RandomAccessFile("rw")` gọi `raf.seek(7)`. `raf.getChannel().position()` bằng bao nhiêu? **7**.
6. Channel của RAF gọi `position(2)`; `raf.getFilePointer()` thay đổi không? **Có, về 2**.
7. Tệp dài 5 byte; `raf.setLength(8)` rồi `raf.getChannel().size()` bằng bao nhiêu? **8**. Nội dung ba byte thêm vào có xác định không? **Không**.
8. Tệp dài 5 byte; `raf.getChannel().truncate(8)` có làm size thành 8 không? **Không, vẫn 5**.
9. Sau khi đóng `FileOutputStream`, có thể tiếp tục gọi `out.getChannel().write(...)` không? **Không**, tài nguyên liên kết đã đóng.
10. Vì sao không nên để hai thread tự do gọi `raf.seek(...)` và `channel.read(...)` trên cùng RAF? Vì vị trí đọc được chia sẻ, thứ tự thực thi có thể làm thao tác đọc sai offset.
11. Khi dùng `FileChannel.read(dst, 100)`, vị trí channel/RAF có đổi không? **Không do lời gọi định vị riêng này**, nhưng `dst.position()` tăng theo số byte thực đọc.
12. Khi chỉ cần FileChannel độc lập từ `Path`, dùng API nào? **`FileChannel.open(path, options)`**, quản lý vòng đời channel bằng `try-with-resources`.

**Ghi nhớ:** `getChannel()` là cầu nối trực tiếp từ `FileInputStream`, `FileOutputStream` và `RandomAccessFile` tới NIO. Nó **giữ quyền I/O của nguồn**, đồng thời **chia sẻ vị trí, dữ liệu, độ dài và vòng đời đóng** với nguồn đó. `RandomAccessFile.setLength` có thể tăng kích thước trong khi `FileChannel.truncate` chỉ cắt ngắn; tách bạch hai hành vi này khi thiết kế truy cập tệp ngẫu nhiên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-009">Dùng đồng thời: vị trí chia sẻ và thao tác có vị trí tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng FileChannel đồng thời: vị trí chia sẻ và thao tác có vị trí tường minh

## 1. Vấn đề: một FileChannel, nhiều thread

`java.nio.channels.FileChannel` cung cấp hai nhóm thao tác đọc/ghi cơ bản:

- **Thao tác tương đối với vị trí hiện tại**: `read(ByteBuffer dst)`, `write(ByteBuffer src)` dùng **vị trí chung của channel**. Khi đọc/ghi thành công, vị trí này tiến lên theo số byte đã xử lý.
- **Thao tác có vị trí tường minh** (*positional I/O*): `read(ByteBuffer dst, long position)` và `write(ByteBuffer src, long position)` nhận offset bắt đầu bằng byte trong tệp; chúng **không thay đổi vị trí hiện tại của channel**.

Cả hai nhóm đều có thể được gọi từ nhiều thread, nhưng có cách quản lý trạng thái và khả năng chạy song song khác nhau. Theo hợp đồng của `FileChannel`, bản thân channel **có thể được sử dụng bởi nhiều thread đồng thời**. Điều này **không** đồng nghĩa mọi thao tác đều chạy cùng lúc, một chuỗi nhiều lời gọi tự động trở thành nguyên tử hoặc `ByteBuffer` truyền vào an toàn khi bị chia sẻ.

Câu hỏi cốt lõi là: *Các tác vụ có muốn dùng chung một con trỏ tệp để xử lý tuần tự, hay mỗi tác vụ cần truy cập một vị trí do nó tự xác định?*

## 2. Hai loại vị trí không được nhầm lẫn

Một phép đọc/ghi qua `FileChannel` có thể liên quan đến **hai loại position** độc lập:

1. **File position của channel**, lấy/đặt bằng `channel.position()` và `channel.position(long)`. Đây là chỉ số byte trong tệp; mỗi `FileChannel` có vị trí riêng của chính nó.
2. **Position của `ByteBuffer`**, lấy/đặt bằng `buffer.position()`. Đây là chỉ số byte **bên trong buffer**. Các phép đọc/ghi của channel vẫn thường di chuyển position của buffer theo số byte được truyền, kể cả khi gọi overload có offset tường minh.

Ví dụ giả sử tệp chứa `ABCDEFGHIJ`, channel đang ở offset 3, còn buffer mới cấp phát có `position=0`:

~~~java
ByteBuffer dst = ByteBuffer.allocate(2);
int n = channel.read(dst, 7L);
~~~

Đây là **mảnh mã minh họa**, giả sử `channel` đang mở để đọc và tệp đủ dài. Khi đọc được hai byte, `dst` chứa `HI` và `dst.position() == 2`; trong khi **vị trí của channel vẫn là 3**. Muốn đọc nội dung vừa ghi vào buffer, cần `dst.flip()` rồi sử dụng `get()` hoặc charset decoder.

Ngược lại, gọi `channel.read(dst)` dùng **file position hiện tại**. Nếu đọc được hai byte ở offset 3, channel tiến lên offset 5 và buffer tiến lên position 2.

| API | Vị trí bắt đầu trong tệp | Thay đổi file position của channel? | Thay đổi buffer position? |
| --- | --- | --- | --- |
| `channel.read(dst)` | `channel.position()` tại lúc đọc | **Có**, theo số byte đọc được | **Có** |
| `channel.write(src)` | `channel.position()` tại lúc ghi | **Có**, theo số byte ghi được | **Có** |
| `channel.read(dst, offset)` | `offset` được truyền vào | **Không** | **Có** |
| `channel.write(src, offset)` | `offset` được truyền vào | **Không** | **Có** |
| `channel.position(offset)` | Đặt vị trí dùng cho thao tác tương đối tiếp theo | **Có**, thay thế giá trị hiện tại | Không |
| `channel.size()` | Xem độ dài tệp | Không chủ động dịch con trỏ | Không |
| `channel.truncate(size)` | Giảm kích thước tệp nếu cần | Có thể điều chỉnh file position nếu vượt kích thước mới | Không |

Ở mỗi lần đọc/ghi, số byte thực tế có thể **nhỏ hơn** `dst.remaining()` hoặc `src.remaining()`; cần kiểm tra giá trị trả về. Với `read`, `-1` báo kết thúc tệp. Các ví dụ có vòng lặp sau đây không giả định một lệnh là đủ cho cả vùng dữ liệu.

## 3. FileChannel thread-safe: chính xác được bảo đảm điều gì?

Hợp đồng Java SE của `FileChannel` quy định:

- Các instance `FileChannel` dùng được từ nhiều thread.
- **Tại một thời điểm, chỉ một thao tác trên cùng channel liên quan đến vị trí channel hoặc có thể thay đổi kích thước tệp được phép đang thực hiện.** Thao tác thứ hai thuộc nhóm này có thể phải chờ thao tác thứ nhất kết thúc.
- Những thao tác khác, đặc biệt là thao tác nhận **vị trí tường minh**, *có thể* thực hiện đồng thời; việc chúng thực sự chạy song song hay bị tuần tự hóa còn tùy implementation.
- `close()` có thể được gọi từ thread khác theo hợp đồng `Channel`; nếu đóng giữa khi một thao tác còn đang chạy có thể phát sinh lỗi như `AsynchronousCloseException`.

Như vậy, câu “FileChannel thread-safe” nói về **hợp đồng phối hợp ở mức thao tác channel**, chứ không bảo đảm:

1. Hai lệnh đọc tương đối liên tiếp của thread A được xem như một giao dịch không thể xen kẽ với lệnh của B.
2. Một `channel.position(n)` sau đó `channel.read(dst)` luôn đọc tại `n` nếu thread khác có thể thay đổi vị trí giữa hai lệnh.
3. Hai writer ghi **cùng vùng byte** bằng positional I/O sẽ cho ra một record nhất quán hoặc “writer thắng” nào đó theo thứ tự bạn mong muốn.
4. Việc mở hai `FileChannel` khác nhau tới cùng một tệp tự tạo một khóa chung cho cả hai instance.
5. Mọi buffer được đưa vào channel đều thread-safe. `ByteBuffer` có position/limit mutable riêng và không tự bảo đảm an toàn cho việc dùng chung đồng thời.

Phân biệt **an toàn nội bộ của một lời gọi** với **tính đúng đắn của thao tác nghiệp vụ gồm nhiều lời gọi** là phần quan trọng nhất khi dùng `FileChannel` trong hệ thống đa luồng.

## 4. Lỗi khi phối hợp position() + read()

Giả sử hai thread muốn đọc tại hai offset khác nhau:

~~~java
// Thread A
channel.position(100);
channel.read(bufferA);

// Thread B
channel.position(200);
channel.read(bufferB);
~~~

Đây là **mã minh họa**. Không có bảo đảm các lời gọi của A sẽ thực hiện liên tục: một thứ tự có thể là:

~~~text
A: channel.position(100)
B: channel.position(200)
A: channel.read(bufferA)  // Có thể đọc ở offset 200
B: channel.read(bufferB)  // Tiếp tục sau phần A vừa đọc
~~~

Việc channel tuần tự hóa từng lời gọi liên quan đến file position **không** tự bao bọc cặp `position(...) + read(...)` thành một giao dịch. Cùng một tình huống xảy ra với `position(...) + write(...)`.

Nếu tác vụ cần đọc tại offset cụ thể, cách trực tiếp hơn là:

~~~java
channel.read(bufferA, 100L);
channel.read(bufferB, 200L);
~~~

Hai lời gọi này có offset độc lập và không thay đổi vị trí chung của channel. Chúng vẫn làm di chuyển **position của từng buffer**, vì thế tốt nhất mỗi tác vụ dùng buffer riêng. Nếu cần đọc đủ một record, lặp positional read và cộng offset theo số byte thực sự nhận được.

Khi thao tác nghiệp vụ thật sự cần con trỏ chung, có thể khóa một vùng lệnh:

~~~java
synchronized (channel) {
    channel.position(100);
    while (bufferA.hasRemaining()) {
        int n = channel.read(bufferA);
        if (n < 0) throw new java.io.EOFException();
        if (n == 0) throw new java.io.IOException("No read progress");
    }
}
~~~

Đây là **mảnh mã minh họa**, giả sử channel/buffer đã khai báo. Khóa chỉ phát huy tác dụng nếu **mọi code liên quan đến vị trí chung cùng tuân theo giao thức này**. Monitor trên `channel` không ngăn một thành phần khác gọi `channel.read(...)` mà không lấy monitor đó. Khi chỉ cần offset định trước, overload positional thường làm hợp đồng đơn giản hơn.

## 5. Ví dụ chạy được: chia sẻ con trỏ và đọc ở offset độc lập

Chương trình Java 17+ dưới đây tạo một tệp tạm có 10 byte ASCII, sau đó cho hai thread dùng **cùng một `FileChannel`** qua hai kiểu truy cập.

Phần A: hai thread đọc bằng `read(buffer)`, cùng đi trên một file position. `CountDownLatch` buộc A đọc trước 2 byte, B đọc tiếp 2 byte, rồi A đọc byte cuối. Ví dụ dùng `synchronized(channel)` trong helper để toàn bộ vòng lặp đọc một khối không bị chen ngang nếu `FileChannel.read` trả về ít byte hơn yêu cầu. Vì thế output không dựa vào may rủi của race.

Phần B: hai thread dùng `read(buffer, offset)` với các offset 0 và 5, mỗi thread giữ `ByteBuffer` riêng. Khi chỉ có concurrent reads trên một tệp không thay đổi, chúng thu được hai vùng đúng mong muốn, trong khi file position của channel không dịch.

Tạo tệp `ChannelPositionDemo.java`:

~~~java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class ChannelPositionDemo {
  static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  static String relative(FileChannel ch, int size) throws IOException {
    ByteBuffer b = ByteBuffer.allocate(size);
    synchronized (ch) {
      while (b.hasRemaining()) {
        int n = ch.read(b);
        if (n < 0) throw new EOFException();
        if (n == 0) throw new IOException("No read progress");
      }
    }
    return new String(b.array(), StandardCharsets.US_ASCII);
  }

  static String at(FileChannel ch, long p, int size) throws IOException {
    ByteBuffer b = ByteBuffer.allocate(size);
    while (b.hasRemaining()) {
      int n = ch.read(b, p);
      if (n < 0) throw new EOFException();
      if (n == 0) throw new IOException("No read progress");
      p += n;
    }
    return new String(b.array(), StandardCharsets.US_ASCII);
  }

  public static void main(String[] args) throws Exception {
    Path path = Files.createTempFile("nio-demo-", ".bin");
    try (FileChannel ch = FileChannel.open(path,
        StandardOpenOption.READ, StandardOpenOption.WRITE)) {
      ByteBuffer input = ByteBuffer.wrap(
          "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII));
      while (input.hasRemaining()) {
        int n = ch.write(input, (long) input.position());
        if (n <= 0) throw new IOException("No write progress");
      }
      System.out.println("Initial position: " + ch.position());

      CountDownLatch aDone = new CountDownLatch(1);
      CountDownLatch bDone = new CountDownLatch(1);
      String[] a = new String[2], b = new String[1];

      Thread ta = new Thread(() -> {
        try {
          a[0] = relative(ch, 2);
          aDone.countDown();
          await(bDone);
          a[1] = relative(ch, 1);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      Thread tb = new Thread(() -> {
        await(aDone);
        try {
          b[0] = relative(ch, 2);
          bDone.countDown();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      ta.start();
      tb.start();
      ta.join();
      tb.join();

      System.out.println("A relative: " + a[0] + " then " + a[1]);
      System.out.println("B relative: " + b[0]);
      System.out.println("Position after relative: " + ch.position());

      ch.position(0);
      String[] result = new String[2];
      Thread t1 = new Thread(() -> {
        try {
          result[0] = at(ch, 0, 5);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      Thread t2 = new Thread(() -> {
        try {
          result[1] = at(ch, 5, 5);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      t1.start();
      t2.start();
      t1.join();
      t2.join();

      System.out.println("At 0: " + result[0]);
      System.out.println("At 5: " + result[1]);
      System.out.println("Position after positional: " + ch.position());
    } finally {
      Files.deleteIfExists(path);
    }
  }
}
~~~

Biên dịch/chạy với JDK 17 trở lên:

~~~bash
javac ChannelPositionDemo.java
java ChannelPositionDemo
~~~

Stdout:

~~~text
Initial position: 0
A relative: AB then E
B relative: CD
Position after relative: 5
At 0: ABCDE
At 5: FGHIJ
Position after positional: 0
~~~

Code đã được chạy thử trên JDK với đầu ra như trên; phiên bản có thêm kiểm tra tiến độ trong vòng lặp giữ nguyên logic cho tệp thông thường.

**Cách đọc kết quả:**

1. Lần ghi `write(input, offset)` đi từ offset tường minh, nên sau khi ghi 10 byte, `ch.position()` vẫn là 0. Buffer `input` lại dịch position tới cuối dữ liệu.
2. Trong phần tương đối, thread A đọc `AB`; B tiếp tục đọc `CD`; A sau đó đọc `E`. Không thread nào có file position “của riêng mình”; `ch.position()` sau ba lượt bằng 5.
3. Sau khi chủ động đặt `ch.position(0)`, hai thread khác đọc trực tiếp các đoạn [0, 4] và [5, 9] bằng `read(buffer, offset)`. Cả hai thu được dữ liệu đúng, và file position vẫn là 0.
4. `CountDownLatch` tạo thứ tự xác định cho lượt đọc đầu. `Thread.join()` chờ thread hoàn tất và bảo đảm main nhìn thấy kết quả được ghi vào hai mảng trong ví dụ.
5. Mỗi lời gọi helper dùng buffer **mới**. Hai thread không có data race trên `ByteBuffer.position()`. Phần positional không có concurrent writer nên không phải giải quyết xung đột nội dung tệp.

**Lưu ý về xử lý lỗi ví dụ:** để tập trung vào file position, code dùng thread thường và in kết quả sau `join()`. Ứng dụng thực tế nên thu nhận lỗi phát sinh trong thread bằng `Future.get()`, `CompletableFuture` hoặc cơ chế tương đương, rồi đảm bảo latch/future thất bại đúng cách; không để một thread chờ vô hạn khi thread khác kết thúc vì exception.

## 6. Chọn thao tác tương đối hay có vị trí tường minh?

| Tình huống | Lựa chọn | Lý do |
| --- | --- | --- |
| Một luồng đọc tuần tự cả tệp | `read(dst)` | File position tự tiến, code đơn giản |
| Các thread xử lý record ở offset riêng | `read(dst, offset)` | Không tranh chấp file position chung |
| Nhiều worker ghi block không giao nhau | `write(src, offset)` + mỗi worker có buffer riêng | Mỗi worker biết rõ vị trí |
| Cần một con trỏ đọc tuần tự phục vụ nhiều consumer | Xác định rõ một owner hoặc dùng lock/queue | Tránh xen kẽ logic giữa các lời gọi |
| Phải thay đổi vị trí rồi đọc một giao dịch gồm nhiều lời gọi | Khóa bao trọn chuỗi, hoặc đổi sang positional | `position()+read()` không nguyên tử |
| Cần cập nhật một record nhiều trường cùng lúc | Thiết kế cơ chế lock/version/transaction ở tầng record | Positional write một mình không cho atomicity |
| Cần append từ nhiều producer | Một writer tập trung, cơ chế cấp phát offset hoặc chính sách append có kiểm chứng | Concurrent append phụ thuộc nền tảng và chính sách ghi |

Dùng **positional I/O** không có nghĩa có thể bỏ qua giới hạn file size, độ dài buffer, quyền truy cập hoặc phối hợp khi nhiều thread ghi trùng vùng.

## 7. Kích thước tệp và các thao tác thay đổi file size

`FileChannel` có quy tắc tuần tự hóa đặc biệt với thao tác **có thể thay đổi kích thước tệp**, ngay cả khi nó nhận vị trí tường minh. Chẳng hạn, ghi tại offset lớn hơn EOF hiện tại có thể làm file dài hơn. Vì vậy không được nói “mọi `write(buffer, offset)` chắc chắn chạy song song” hay dùng việc có tham số `offset` làm bằng chứng tối ưu hóa concurrency.

Một số trường hợp:

- **Đọc ở offset ngoài EOF**: `read(dst, offset)` trả về `-1` khi đang ở hoặc vượt cuối tệp (nếu buffer còn byte để ghi), không tự thêm dữ liệu.
- **Ghi vượt EOF**: `write(src, offset)` có thể mở rộng tệp. Khoảng trống giữa EOF cũ và offset ghi chịu quy tắc của file system; không suy diễn đây là một record hợp lệ.
- **`truncate(newSize)`** có thể làm tệp ngắn đi. Nếu file position đang lớn hơn size sau truncate, vị trí channel được điều chỉnh theo hợp đồng API. Reader cùng lúc có thể gặp EOF sớm hơn hoặc mất phần dữ liệu dự kiến.
- **`size()` trả trạng thái tại một thời điểm**. Phép `long pos = channel.size(); channel.write(src, pos);` không tự tạo append nguyên tử. Nhiều thread có thể cùng đọc một size rồi cùng chọn offset đó.
- **`force(true)`** yêu cầu đẩy các cập nhật xuống thiết bị lưu trữ theo bảo đảm API và thiết bị, nhưng không thay thế mutex, không cấp transaction và không khiến nhiều write thành một phép cập nhật nguyên tử.

Nếu thiết kế nhiều thread cần append, hãy cấp phát offset không trùng bằng một coordinator có đồng bộ; nếu file size thay đổi động, cần quản lý vòng đời/trạng thái cấu trúc của tệp. Hạn chế trộn `truncate` hoặc mở rộng file với những worker đang dùng offset cũ mà không có giao thức.

## 8. Buffer được truyền vào FileChannel cũng là tài nguyên có trạng thái

Các overload positional giữ nguyên **file position**, nhưng vẫn cập nhật **buffer position**. Ví dụ hai thread đều gọi:

~~~java
channel.read(sharedBuffer, 0L);
channel.read(sharedBuffer, 128L);
~~~

Đây là đoạn minh họa sai nếu hai thread dùng chung `sharedBuffer` mà thiếu đồng bộ/ownership. Dù offset file khác nhau, cả hai còn tranh chấp `sharedBuffer.position()`, có thể cùng ghi vào backing storage. `ByteBuffer` không mặc nhiên thread-safe.

Cách tổ chức thường dùng:

- Mỗi thread/tác vụ sở hữu `ByteBuffer` riêng và không giao buffer đó cho thread khác trước khi xong.
- Nếu dùng `duplicate()`/`slice()`, các view có cursor riêng nhưng **vẫn chia sẻ backing storage**. Chỉ ghi an toàn khi vùng nhớ không chồng lấn hoặc có đồng bộ phù hợp.
- Nếu đọc một dữ liệu bất biến, có thể dùng view chỉ đọc với safe publication và bảo đảm không alias nào đang ghi. `asReadOnlyBuffer()` ngăn ghi qua view, **không** làm byte nền bất biến.
- Một tác vụ bất đồng bộ còn sử dụng buffer thì không được đưa buffer về pool và `clear()` quá sớm.

Trong ví dụ đầy đủ, helper `at(...)` tạo `ByteBuffer.allocate(size)` mới cho từng lần đọc, tách trạng thái buffer một cách rõ ràng.

## 9. Xử lý partial read/write và offset theo số byte thực tế

Một lời gọi `read(dst, offset)` có thể trả về ít byte hơn `dst.remaining()`. Khi đọc cả record kích thước cố định, cần tăng **offset file tường minh** sau từng lần đọc:

~~~java
long current = recordOffset;
while (dst.hasRemaining()) {
    int n = channel.read(dst, current);
    if (n < 0) throw new java.io.EOFException("Truncated record");
    if (n == 0) throw new java.io.IOException("No progress");
    current += n;
}
~~~

Đây là **mảnh mã minh họa**, giả sử `recordOffset`, `channel` và `dst` hợp lệ. Quan trọng là tăng `current` theo `n`; nếu luôn đọc lại ở cùng `recordOffset` trong vòng lặp, dữ liệu có thể bị lặp trong buffer.

Tương tự với positional write:

~~~java
long current = recordOffset;
while (src.hasRemaining()) {
    int n = channel.write(src, current);
    if (n <= 0) throw new java.io.IOException("No write progress");
    current += n;
}
~~~

Ở đây `src.position()` cũng tăng theo `n`, nhưng **file position chung** không đổi. Khi tính offset cho record thứ N, dùng `long` và phép kiểm tra tràn:

~~~java
long offset = Math.addExact(
        headerSize, Math.multiplyExact(recordIndex, recordSize));
~~~

Đây là mảnh minh họa giả sử `headerSize`, `recordIndex` và `recordSize` là các giá trị kiểu `long` không âm hợp lệ. Nếu offset được tính sai hoặc hai worker nhận cùng index, positional I/O có thể ghi đè dữ liệu của nhau mặc dù hoàn toàn không tranh chấp file position.

## 10. Ghi đồng thời: không có bảo đảm một record là nguyên tử

Hai thread được giao các vùng không giao nhau như [0, 999] và [1000, 1999] có thể dùng cùng channel với positional I/O. Thiết kế đó tránh trùng vị trí dữ liệu nếu từng thread có buffer riêng, tệp đã được chuẩn bị kích thước phù hợp và không có thread khác `truncate` hoặc sửa chồng lấn.

Nhưng nếu hai thread ghi **cùng vùng**:

~~~java
// Writer A
channel.write(dataA, 4096L);

// Writer B
channel.write(dataB, 4096L);
~~~

Đây là **mảnh mã minh họa**. Không thể mặc định toàn bộ bytes cuối cùng chắc chắn là của A hoặc B, hay một lời gọi `write` luôn ghi đủ cả buffer. Việc các lời gọi được tuần tự hóa trong vài hoàn cảnh cũng không làm **toàn bộ chuỗi read-modify-write** thành giao dịch nguyên tử. Với thao tác cập nhật số dư, số phiên bản, trạng thái và checksum trong cùng một record, cần khóa/chốt phiên bản hoặc giao thức storage nhất quán ở tầng cao hơn.

Nếu cần cập nhật bền vững và khôi phục sau crash, cân nhắc copy-on-write, checksum + version, write-ahead log hoặc cơ sở dữ liệu có transaction. Tránh coi `channel.force(true)` hay `channel.lock()` là bằng chứng tự động có transaction.

## 11. FileLock và nhiều FileChannel đến cùng một tệp

`FileChannel.lock(...)` hoặc `tryLock(...)` hỗ trợ khóa vùng tệp trong những trường hợp cần điều phối truy cập. Nhưng cần biết:

- Khóa tệp được tổ chức ở tầng hệ điều hành/provider và có các khác biệt theo nền tảng (advisory hoặc mandatory, mức hỗ trợ vùng, thời điểm áp dụng).
- Khóa theo vùng byte, không phải luôn lock mọi dữ liệu. Phải định nghĩa rõ vùng cần bảo vệ.
- Khóa file **được giữ thay mặt cho toàn bộ JVM**. Khi cố lấy khóa chồng lấn với khóa JVM đã giữ hoặc yêu cầu đang chờ, có thể nhận `OverlappingFileLockException` thay vì khóa này trở thành mutex lý tưởng giữa hai thread cùng JVM.
- Đừng dùng `FileLock` làm cơ chế khóa thread nội bộ duy nhất. Dùng `synchronized`, `ReentrantLock` hoặc bộ quản lý ownership trong JVM khi cần phối hợp giữa worker; dùng file lock khi cần hợp tác với tiến trình khác và đã xác định rõ giới hạn nền tảng.
- Nếu nhiều channel khác nhau cùng mở một tệp, chúng có **file position riêng**, nhưng chia sẻ nội dung tệp. Hai channel ghi vào cùng vùng vẫn cần phối hợp.

Có thể sử dụng một channel dùng chung cho nhiều worker nếu hợp đồng owner và vùng byte rõ ràng; cũng có thể dùng nhiều channel riêng tùy chi phí quản lý và đặc tính I/O. Việc có nhiều channel không tự giúp cập nhật cùng vùng trở thành an toàn.

## 12. Các lỗi phổ biến và cách sửa

| Nhận định hoặc thao tác sai | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| “FileChannel thread-safe nên `position(100); read(dst)` luôn bắt đầu ở 100” | Thread khác có thể xen vào giữa hai lời gọi | Dùng `read(dst, 100L)` hoặc khóa cả chuỗi |
| “Hai thread dùng `read(dst)` nên mỗi thread có con trỏ riêng” | Một `FileChannel` có file position dùng chung | Một owner đọc tuần tự, hoặc positional I/O |
| “`read(dst, offset)` không đổi trạng thái nào cả” | Buffer position vẫn tăng | Mỗi tác vụ có buffer riêng hoặc đồng bộ |
| “Có offset nghĩa là FileChannel buộc chạy song song” | Hợp đồng cho phép implementation tuần tự hóa | Không dựa vào tính song song làm tính đúng đắn |
| “Ghi explicit offset không thể kéo dài file” | Ghi vượt EOF có thể tăng kích thước | Kiểm soát size, ranh giới và cấp phát offset |
| “Hai thread đọc `size()` rồi cùng append là ổn” | Cả hai có thể chọn cùng offset | Cấp phát offset bằng coordinator |
| “Gọi `read` một lần luôn điền đủ buffer” | Partial I/O được phép | Lặp, theo dõi bytes và xử lý EOF |
| “Dùng chung `ByteBuffer` là được vì channel có lock” | Buffer có cursor và backing memory riêng | Không chia sẻ mutable buffer ngoài hợp đồng |
| “`force(true)` làm các update nguyên tử” | Durability và atomicity khác nhau | Transaction/WAL/version tùy yêu cầu |
| “Một `FileLock` là mutex hoàn chỉnh giữa các thread trong JVM” | Cơ chế khóa file và lock overlap khác mutex nội bộ | Dùng khóa JVM và giao thức ownership |
| “Có thể `close()` channel khi thread khác còn I/O” | Có thể gây `AsynchronousCloseException`/đọc ghi thất bại | Một owner quản lý đóng sau khi worker kết thúc |

### Quy trình chọn API trong ứng dụng

1. Xác định tác vụ đọc/ghi **tuần tự** hay có **offset riêng**.
2. Xác định mỗi thread có `ByteBuffer` riêng hay có cùng backing storage.
3. Nếu thao tác có nhiều lời gọi, xác định cần nguyên tử ở **mức một lời gọi** hay **mức một record/giao dịch**.
4. Kiểm tra liệu ghi có làm thay đổi **file size** hoặc xung đột với truncate hay không.
5. Với nhiều writer, xác lập cơ chế cấp phát vùng không chồng lấn và chính sách retry/error.
6. Đóng channel chỉ sau khi mọi task sử dụng nó đã hoàn thành; truyền lỗi về nơi sở hữu để không bỏ sót I/O thất bại.

## 13. Tự kiểm tra

**Câu 1.** `channel.position() == 10`; thread A đọc được 3 byte bằng `read(dst, 100)`. File position của channel thành bao nhiêu?

**Đáp án:** 10. Overload có offset tường minh không thay đổi file position; `dst.position()` lại tăng thêm 3 nếu đọc được 3 byte.

**Câu 2.** Hai thread cùng gọi `channel.position(x)` rồi `channel.read(buffer)` theo hai offset khác nhau. Tại sao vẫn có lỗi dù channel thread-safe?

**Đáp án:** Vì hai lời gọi của một thread không được coi là một giao dịch. Thread khác có thể thay đổi vị trí chung giữa `position(x)` và `read(buffer)`.

**Câu 3.** Hai thread đọc hai vùng không chồng lấn với `read(dst, offset)`. Có cần dùng chung một `ByteBuffer` cho tiết kiệm bộ nhớ không?

**Đáp án:** Không. Nên dùng buffer riêng cho mỗi task để tách trạng thái cursor và byte nền; offset file không thay đổi không có nghĩa buffer position không thay đổi.

**Câu 4.** Hai `write(src, offset)` có offset khác nhau có luôn chạy song song không?

**Đáp án:** Không. Hợp đồng cho phép concurrency trong nhiều trường hợp, nhưng việc thực thi song song tùy implementation; nếu thao tác có thể đổi kích thước tệp còn có quy tắc tuần tự hóa liên quan.

**Câu 5.** Ghi block mới tại EOF bằng `long p = channel.size(); channel.write(src, p)` ở 10 thread có đảm bảo append không trùng không?

**Đáp án:** Không. Tính size và ghi là hai thao tác tách rời; nhiều thread có thể lấy cùng `p`. Cần cơ chế cấp phát offset hoặc writer điều phối.

**Câu 6.** `FileChannel.lock()` có thay thế luôn `ReentrantLock` dùng giữa các thread trong JVM không?

**Đáp án:** Không. File lock hướng đến phối hợp quyền truy cập vùng tệp và có đặc tính phụ thuộc hệ điều hành; khóa chồng lấn trong cùng JVM có thể ném `OverlappingFileLockException`.

## Tài liệu API

- [Java SE 17 — FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): hợp đồng concurrent access; overload read/write với vị trí chung và vị trí tường minh; file locking, truncate, force.
- [Java SE 17 — ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): buffer position/limit, chuyển byte khi read/write, thread safety của buffer.
- [Java SE 17 — FileLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileLock.html): khóa vùng, phạm vi JVM và các giới hạn theo nền tảng.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối thứ tự và quan hệ happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-010">Append và các hành vi phụ thuộc hệ điều hành</a>

<details>
<summary>Xem chi tiết</summary>

# FileChannel: Append và các hành vi phụ thuộc hệ điều hành

Khi viết vào cuối file bằng Java NIO, lựa chọn `StandardOpenOption.APPEND` ảnh hưởng trực tiếp đến **vị trí ghi**, **khả năng nhiều tiến trình cùng ghi** và **tính nhất quán dữ liệu**. Một chương trình chạy đúng trên máy phát triển chưa chắc đã có cùng đặc tính nguyên tử, khóa file, độ bền dữ liệu hay quyền truy cập khi chạy trên hệ điều hành và filesystem khác.

Bài này tập trung vào `java.nio.channels.FileChannel`: append khác gì `position(size())`, cách mở channel an toàn, vòng lặp ghi đủ byte, giới hạn atomicity, file locking, `force`/`SYNC`/`DSYNC` và các khác biệt thường gặp giữa filesystem cục bộ, mạng và các nền tảng triển khai.

## 1. Append thực sự có nghĩa gì?

`FileChannel` làm việc với hai vị trí khác nhau:

- **File position**: offset byte kế tiếp của channel, truy cập qua `channel.position()` và điều chỉnh bằng `position(long)`.
- **Buffer position**: byte kế tiếp trong `ByteBuffer` được đưa vào `write(buffer)`; tăng theo số byte thực sự đã ghi.

Trong chế độ thông thường (không có `APPEND`), một phép `write(buffer)` bắt đầu ở **vị trí hiện tại của FileChannel**. Ở chế độ `APPEND`, mỗi lần gọi **relative write** sẽ chuyển vị trí file tới **cuối file hiện tại** rồi ghi dữ liệu.

```text
File ban đầu:       [A][B][C]
                    0  1  2   EOF=3

WRITE thông thường, position(1), ghi "X":
                    [A][X][C]
                    Không tự động thêm ở cuối.

APPEND, ghi "X":
                    [A][B][C][X]
                    Phép ghi đi tới cuối file trước khi ghi.
```

Điều quan trọng: chuẩn API **không cam kết** việc “đặt vị trí tới EOF + ghi dữ liệu” diễn ra như **một thao tác nguyên tử** trên mọi hệ điều hành. Vì vậy không thể suy ra hai writer đồng thời sẽ tự động ghi thành những bản ghi độc lập, đầy đủ, không bị xen kẽ.

## 2. Mở FileChannel: APPEND và các OpenOption liên quan

Dạng thông dụng:

```java
try (FileChannel channel = FileChannel.open(
        path,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.APPEND)) {
    // Ghi vào cuối file bằng channel.write(buffer).
}
```

Đây là snippet: `path` là `Path` hợp lệ và cần import `java.nio.channels.FileChannel` cùng `java.nio.file.StandardOpenOption`. Tham số `APPEND` đã ngụ ý mở để ghi; thêm `WRITE` khiến ý định rõ ràng hơn.

| Tùy chọn | Hành vi | Lưu ý |
| --- | --- | --- |
| `WRITE` | Cho phép ghi file; thường bắt đầu ở offset 0 khi không APPEND. | Ghi từ đầu **ghi đè byte tương ứng**, không xóa tất cả nội dung cũ. |
| `APPEND` | Mỗi relative `write` ghi từ cuối file đang có. | Độ nguyên tử của thao tác đi tới EOF rồi ghi phụ thuộc hệ thống. |
| `CREATE` | Mở file có sẵn hoặc tạo nếu chưa có khi mở ghi. | **Không** có nghĩa truncate file đã tồn tại. |
| `CREATE_NEW` | Tạo file mới và thất bại nếu đã tồn tại. | Kiểm tra tồn tại và tạo mới là nguyên tử theo giao ước API/provider. |
| `TRUNCATE_EXISTING` | Xóa nội dung file đang có về độ dài 0 khi mở ghi. | **Không dùng cùng APPEND**. |
| `READ` | Cho phép đọc file. | **Không dùng cùng APPEND** theo hợp đồng `FileChannel.open`. |
| `SYNC` | Yêu cầu đồng bộ cả cập nhật nội dung và metadata xuống thiết bị lưu trữ. | Có thể chậm; tác dụng thực tế phụ thuộc môi trường I/O. |
| `DSYNC` | Yêu cầu đồng bộ cập nhật nội dung xuống thiết bị lưu trữ. | Không nên suy ra mọi metadata đã bền vững. |
| `DELETE_ON_CLOSE` | Cố gắng xóa file khi đóng channel. | Hành vi *best effort*, không phải cam kết xóa được trên mọi OS. |
| `SPARSE` | Gợi ý sử dụng sparse file khi tạo mới. | Chỉ là hint; provider/filesystem có thể bỏ qua. |

Các tổ hợp **không hợp lệ**:

```java
// Không hợp lệ: APPEND không đi kèm READ.
FileChannel.open(path, StandardOpenOption.APPEND,
        StandardOpenOption.READ);

// Không hợp lệ: APPEND không đi kèm TRUNCATE_EXISTING.
FileChannel.open(path, StandardOpenOption.APPEND,
        StandardOpenOption.TRUNCATE_EXISTING);
```

Đây là **ví dụ phản diện** để chỉ ra cấu hình sai, không được chạy trong chương trình sản xuất. Các tổ hợp không hợp lệ thường gây `IllegalArgumentException` khi mở channel. Nếu muốn đọc rồi append, hãy đọc qua một channel riêng, sau đó đóng/mở channel append; hoặc thiết kế luồng xử lý phù hợp mà không đưa `READ` vào cùng bộ tùy chọn APPEND.

**Lưu ý:** `CREATE_NEW` có ý nghĩa tránh ghi đè khi tạo **lần đầu**. Nó không giúp nhiều writer đồng thời serialize các lần append **sau khi file đã tồn tại**.

## 3. Ví dụ Java 17+: append hai dòng, rồi ghi đè đầu file

Chương trình dưới đây tạo file tạm riêng, đi qua hai cơ chế để thấy rõ chúng khác nhau:

1. Ghi nội dung ban đầu `seed\n`.
2. Dùng `APPEND` để thêm `one\n` và `two\n`.
3. Đóng channel, mở lại chỉ với `WRITE`, đặt file position về 0 và ghi `HEAD\n`; do có cùng số byte với `seed\n`, phần append phía sau được giữ nguyên.
4. Đọc lại nội dung, kiểm tra kích thước byte và xóa file sau khi các handle đã đóng.

Lưu thành **`FileChannelAppendDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelAppendDemo {

    private static ByteBuffer utf8(String value) {
        return ByteBuffer.wrap(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFully(FileChannel channel, ByteBuffer data)
            throws IOException {
        while (data.hasRemaining()) {
            int written = channel.write(data);
            if (written == 0) {
                throw new IOException("Ghi file không có tiến triển");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("filechannel-append-", ".txt");
        try {
            Files.writeString(file, "seed\n", StandardCharsets.UTF_8);

            try (FileChannel append = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                writeFully(append, utf8("one\n"));
                writeFully(append, utf8("two\n"));
                append.force(true);
            }

            // WRITE không có APPEND: có thể chọn offset để ghi đè.
            try (FileChannel overwrite = FileChannel.open(
                    file, StandardOpenOption.WRITE)) {
                overwrite.position(0);
                writeFully(overwrite, utf8("HEAD\n"));
            }

            String result = Files.readString(
                    file, StandardCharsets.UTF_8);
            System.out.println("content=" + result.replace('\n', '|'));
            System.out.println("bytes=" + Files.size(file));
            System.out.println("correct="
                    + "HEAD\none\ntwo\n".equals(result));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac FileChannelAppendDemo.java
java FileChannelAppendDemo
```

Stdout với một chương trình chạy đơn lẻ và filesystem hỗ trợ các tùy chọn đã dùng:

```text
content=HEAD|one|two|
bytes=13
correct=true
```

**Giải thích chi tiết:**

- `Files.createTempFile` cấp một tệp riêng; không có tiến trình thứ hai cùng ghi nên thứ tự các lần ghi trong demo là xác định.
- `Files.writeString` mặc định ghi/truncate dữ liệu cũ của tệp hiện có, tạo đúng 5 byte ASCII ban đầu.
- `FileChannel.open(..., APPEND)` khiến các relative `write` tiếp theo đi tới EOF, bất kể file position trước đó. `writeFully` xử lý trường hợp một lần `write` chỉ chuyển được một phần buffer; sau mỗi lần channel tự tăng `ByteBuffer.position()`.
- Hai phần append lần lượt thêm 4 + 4 byte, tổng 13 byte. Các chuỗi ở đây là ASCII nên số byte UTF-8 trùng số ký tự đơn vị code; với tiếng Việt, phải tính byte qua `getBytes(UTF_8)`.
- `force(true)` yêu cầu đẩy cập nhật do append channel thực hiện tới thiết bị trên file cục bộ, với ràng buộc từ hợp đồng API; nó **không** biến append thành giao dịch đa tiến trình.
- Channel `overwrite` không bật append, nên `position(0)` và ghi `HEAD\n` sẽ thay đúng 5 byte đầu. File vẫn dài 13 byte vì không dùng `TRUNCATE_EXISTING`.

**Điểm giới hạn của ví dụ:** ngoại lệ “không có tiến triển” của `writeFully` là chính sách *fail fast* để không vô tình lặp vô hạn; FileChannel ghi file thông thường không dùng cơ chế non-blocking của `SocketChannel`. Với các channel khác, giá trị 0 có thể đòi chờ readiness. Đừng sao chép chính sách này cho mọi loại channel.

## 4. APPEND không tương đương với position(size())

Một cách khác để ghi cuối file là:

```java
// Snippet: channel đã mở với WRITE, không có APPEND.
channel.position(channel.size());
while (buffer.hasRemaining()) {
    channel.write(buffer);
}
```

Cách này **có thể đúng khi chỉ một writer sở hữu file**, nhưng trong nhiều writer, việc đọc `size()`, đặt `position()` rồi ghi là **nhiều bước riêng**. Writer khác có thể mở rộng hoặc thay đổi file ở giữa, nên chương trình có thể ghi đè vùng vừa được thêm hoặc đặt sai offset.

Trong chế độ `APPEND`, provider cố gắng bảo đảm mỗi relative `write` đi tới EOF ngay trước khi ghi, nhưng hợp đồng Java vẫn để tính nguyên tử phụ thuộc hệ điều hành. Trên một số hệ thống, một lần gọi kernel append được thực thi nguyên tử đối với cập nhật offset; trên hệ thống khác, đặc biệt khi có filesystem mạng, ngữ nghĩa có thể khác. **Không nên suy ra hành vi đa writer từ một lần chạy trên laptop.**

Ngoài ra:

- `FileChannel.write(ByteBuffer src, long position)` là **positional write**. Khi channel bật `APPEND`, **tác dụng của positional write là không được định nghĩa thống nhất theo Javadoc**. Không dùng cặp này để “vá” một byte ở vị trí cố định trong append channel.
- `position(0)` trên append channel không phải chiến lược cập nhật header: các relative write vẫn theo append mode.
- Nếu muốn sửa một header tại offset 0, hãy dùng channel **không** bật `APPEND` và có quyền ghi, tách khỏi luồng append nếu cần.

Bảng đối chiếu:

| Cách ghi | Vị trí bắt đầu | Có nên xem là append an toàn nhiều writer? |
| --- | --- | --- |
| `WRITE` rồi `position(size())` | Offset EOF đọc ở thời điểm gọi `size` | Không; có thể race giữa các bước. |
| `WRITE + APPEND`, `write(buffer)` | EOF tại mỗi relative write | Không có cam kết atomicity mọi OS/provider. |
| `WRITE` rồi `write(buffer, offset)` | Offset cụ thể do ứng dụng quản lý | Chỉ an toàn nếu quản lý vùng ghi và writer đúng cách. |
| `APPEND` rồi `write(buffer, offset)` | **Không có ngữ nghĩa portable** | Tránh dùng. |

## 5. “Một bản ghi” có thể gồm nhiều write

Giả sử một bản ghi có header độ dài và payload:

```java
// Snippet: channel APPEND, buffer đã chuẩn bị đầy đủ.
writeFully(channel, header);
writeFully(channel, payload);
```

Ngay cả nếu từng `write` riêng lẻ là nguyên tử trên một filesystem cụ thể, **không có giao ước hai lời gọi liên tiếp sẽ được ghép thành một transaction**. Writer khác có thể chèn dữ liệu giữa header và payload. Hơn nữa, `writeFully` có thể phải gọi `write` nhiều lần nếu xảy ra partial write.

Khi yêu cầu mỗi record xuất hiện như một khối thống nhất:

1. Mã hóa record thành một `ByteBuffer` trước khi ghi khi kích thước cho phép; điều này giảm số lần gọi write nhưng **không tạo ra atomicity portable**.
2. Chỉ định **một writer** hoặc một hàng đợi ghi được điều phối trong một JVM; bảo đảm một record được xử lý trọn vẹn trước record kế tiếp.
3. Nếu có nhiều JVM/tiến trình, dùng một cơ chế phối hợp được kiểm thử trên **filesystem mục tiêu**, chẳng hạn một writer service hoặc protocol lock đã thỏa thuận; không giả định Java tự serialize mọi chương trình.
4. Đặt record framing rõ ràng (magic, length, checksum/CRC, version), kiểm tra khi đọc và có chiến lược phát hiện/cắt bỏ phần đuôi chưa hoàn tất.
5. Nếu cần giao dịch nhiều record, rollback hoặc concurrency mạnh, cân nhắc cơ sở dữ liệu hay append-only log có cơ chế commit riêng.

Một lời gọi `write(ByteBuffer[])` (gathering write) cũng có thể chuyển ít byte hơn tổng `remaining()` và không phải giao ước mọi OS sẽ ghi nguyên tử toàn bộ record. Vẫn phải kiểm tra giá trị trả về và vị trí mỗi buffer.

## 6. Đồng thời trong cùng một JVM: điều gì được tuần tự hóa?

`FileChannel` an toàn để các thread gọi phương thức đồng thời về mặt thread safety của đối tượng channel. Tuy nhiên, nó **không cung cấp transaction hoặc ranh giới record** cho ứng dụng. Theo hợp đồng JDK:

- Trên **một FileChannel**, tại một thời điểm chỉ có một thao tác liên quan đến file position hoặc có thể thay đổi kích thước file được thực hiện; thao tác thứ hai loại này phải đợi. Điều này **không** đồng nghĩa chuỗi nhiều lời gọi `write` của một record trở thành nguyên tử.
- Những thao tác positional theo offset cụ thể có thể tiến hành đồng thời; mức độ thực sự thực thi song song tùy implementation.
- Nhiều FileChannel khác nhau, và đặc biệt nhiều process khác nhau, cần cơ chế phối hợp riêng khi cùng ghi một file.

Ví dụ, nếu nhiều thread cùng gọi một hàm `appendRecord` gồm 3 lần write, các lời gọi từ các thread vẫn có thể xen kẽ theo ranh giới write. Hãy khóa **toàn bộ thao tác ghi record** hoặc chuyển record qua một hàng đợi cho một writer duy nhất. Không nên dùng một biến `long localOffset = channel.size()` để tự phân phối offset khi có writer khác mở file.

Khi phối hợp nhiều process, thử nghiệm concurrency phải bao gồm **nhiều tiến trình thật**, không chỉ nhiều thread cùng chia sẻ một đối tượng channel.

## 7. FileLock: hữu ích nhưng không thay thế transaction

`FileChannel.lock()` yêu cầu khóa độc quyền, thường bao phủ vùng `[0, Long.MAX_VALUE)`. Có thể dùng `tryLock()` để thử lấy lock mà không đợi; hàm có thể trả `null` nếu process khác đang giữ khóa vùng xung đột.

Ví dụ minh họa (không phải chương trình độc lập):

```java
// channel: FileChannel đã mở với WRITE.
// Chính sách là mọi writer hợp tác đều phải dùng cùng quy ước khóa.
try (FileLock ignored = channel.lock()) {
    writeFully(channel, recordBuffer);
}
```

Cần import `java.nio.channels.FileLock`; `writeFully` là hàm helper như ở ví dụ chương trình. Lưu ý:

- Lock trên một số hệ điều hành là **advisory**, còn ở nơi khác có thể được thực thi bắt buộc; đừng giả định các process không hợp tác đều sẽ tuân thủ.
- Một số filesystem có thể không hỗ trợ shared lock hoặc chuyển yêu cầu shared thành exclusive. Kiểm tra `FileLock.isShared()` khi điều đó quan trọng.
- Lock được giữ thay mặt **toàn JVM**, nên không thích hợp để làm mutex giữa các thread trong cùng JVM; dùng đồng bộ hóa cấp ứng dụng.
- `OverlappingFileLockException` có thể xuất hiện nếu JVM đã giữ hoặc đang chờ một lock trùng vùng. `lock()` có thể chờ; `tryLock()` không nhất thiết thay thế chiến lược timeout và retry an toàn.
- Với file sẽ tiếp tục dài ra, cần bảo đảm phạm vi lock đã chọn bao phủ phần có thể phát triển; lock vào một vài byte đầu không tự khóa các byte nằm ngoài phạm vi.
- Hành vi khóa trên filesystem mạng phụ thuộc protocol/mount/server và điều kiện mạng. Kiểm thử trên môi trường triển khai thực tế.

Một lock phối hợp tốt có thể bảo đảm writer hợp tác không xen kẽ khi ghi record; nó **không** tự rollback record nếu process chết giữa chừng.

## 8. force(true), force(false), SYNC và DSYNC

`FileChannel.force(boolean metaData)` yêu cầu các cập nhật được ghi ra thiết bị lưu trữ; trên **thiết bị cục bộ**, Javadoc đưa ra bảo đảm cho các cập nhật phù hợp do chính channel thực hiện. Với **thiết bị không cục bộ**, không có cùng mức cam kết đó.

| Cơ chế | Phạm vi theo hợp đồng API | Thời điểm gọi | Hạn chế |
| --- | --- | --- | --- |
| `force(false)` | Yêu cầu đẩy thay đổi **nội dung**. | Sau khi ghi những dữ liệu quan trọng. | Metadata có thể chưa được đồng bộ; hiệu lực chi tiết phụ thuộc nền tảng. |
| `force(true)` | Yêu cầu đẩy cả **nội dung và metadata**. | Khi cần thêm thông tin kích thước/timestamp liên quan được đồng bộ. | Không tạo atomicity cho nhiều lần ghi. |
| `SYNC` | Yêu cầu đồng bộ nội dung và metadata theo mỗi cập nhật. | Chọn khi mở file. | Chi phí I/O có thể lớn. |
| `DSYNC` | Yêu cầu đồng bộ nội dung theo mỗi cập nhật. | Chọn khi mở file. | Không nên hiểu là mọi metadata đều được đảm bảo bền vững. |

`force` chỉ cam kết các loại thay đổi được API quy định. Với một `MappedByteBuffer` được sửa trực tiếp, nên xem xét `MappedByteBuffer.force()`; không mặc định `FileChannel.force()` sẽ bảo đảm mọi thay đổi qua memory mapping.

**Độ bền khác với atomicity:** có thể đẩy một bản ghi **chưa hoàn chỉnh** xuống đĩa. `force(true)` không cứu được format thiếu framing/CRC và không phối hợp được nhiều writer. Trong một quy trình tạo **file mới rồi thay file cũ**, khả năng atomic move và độ bền metadata của thư mục là những vấn đề riêng, cần thiết kế phù hợp provider/OS.

Cả việc flush Java buffer, đóng `FileChannel`, gọi `force` và sử dụng `SYNC` đều không nên bị nhầm với giao dịch dữ liệu nhiều bước có khả năng rollback.

## 9. Hành vi phụ thuộc hệ điều hành và filesystem

Các khác biệt quan trọng khi đưa code vào production:

### 9.1 Append atomicity và hệ thống file mạng

Trên hệ thống hỗ trợ append nguyên tử ở một system call, mỗi lần ghi có thể được bảo vệ khỏi một số race giữa tiến trình. JDK vẫn không hứa điều đó trên **mọi** hệ thống. Hệ thống file mạng (ví dụ NFS/SMB tùy cấu hình) có thêm cache, đồng bộ giữa client và protocol; không suy rằng behavior ở ext4 trên một máy sẽ giống trên volume mạng.

### 9.2 Nhìn thấy dữ liệu mới ở process khác

Các FileChannel trong **cùng chương trình Java** có bảo đảm về tính nhất quán view theo mô tả JDK. Với process khác, dữ liệu nhìn thấy có thể chịu ảnh hưởng cache của OS hoặc trễ protocol filesystem mạng. Một reader đọc giữa lúc writer đang cập nhật vẫn có thể thấy **bản ghi chưa hoàn chỉnh** theo schema ứng dụng.

### 9.3 File đang mở và xóa, đổi tên, thay thế

Trên các nền tảng POSIX, việc unlink một file đang mở có thể hợp lệ: handle cũ vẫn truy cập object cho tới khi đóng. Trên Windows, việc xóa/đổi tên file đang mở có thể thất bại tùy sharing mode và các handle liên quan. Không thiết kế cơ chế rotate log/replace dựa trên giả định một platform. Hãy đóng handle không cần thiết, bắt `IOException` và kiểm thử thay thế/di chuyển đúng volume, filesystem, OS.

### 9.4 Quyền truy cập, dấu thời gian và symbolic link

Quyền POSIX, ACL của Windows, metadata timestamp, hành vi symbolic link và case sensitivity khác nhau theo provider và filesystem. `CREATE` không bỏ qua permission. `FileChannel.open` có thể thất bại vì thiếu quyền, đường dẫn tới thư mục, file đang bị process khác giữ theo chế độ chia sẻ, quota hoặc file system read-only.

Nếu đường dẫn được nhận từ người dùng, kiểm soát quyền truy cập, thư mục được phép và symbolic link theo chính sách bảo mật. Không dùng dữ liệu ngoài để tự tạo tên file log mà không normalize/kiểm tra đường dẫn.

### 9.5 File dài hơn, lỗ hổng dữ liệu và sparse file

`channel.position(channel.size() + gap)` **không tự mở rộng** file. Khi sau đó ghi vào vị trí mới, file được mở rộng và các byte ở khoảng trống giữa EOF cũ và vùng mới có giá trị **không được bảo đảm** theo hợp đồng FileChannel. Nếu cần định dạng nhị phân có vùng trống, hãy khởi tạo rõ dữ liệu và định nghĩa chính sách. `SPARSE` chỉ là hint khi tạo file, không phải cam kết sử dụng ít dung lượng.

### 9.6 DELETE_ON_CLOSE và dọn tài nguyên

`DELETE_ON_CLOSE` chỉ là nỗ lực tốt nhất. Tính khả dụng và thời điểm xóa có thể phụ thuộc platform, handle hoặc quyền truy cập. Đối với dữ liệu quan trọng cần dọn, dùng thư mục temp riêng, đóng mọi stream/channel, xử lý lỗi xóa và có quy trình cleanup bổ sung.

## 10. Tránh nhầm các khái niệm “nguyên tử”

Trong một hệ thống ghi log hoặc journal, có nhiều cấp độ độc lập:

| Khái niệm | Có thể đạt được bằng | Điều **chưa** được bảo đảm |
| --- | --- | --- |
| Tạo file mới nếu chưa tồn tại | `CREATE_NEW` | Một record append sẽ tự an toàn giữa nhiều writer. |
| Append mỗi lệnh write | `APPEND` | Việc seek EOF + write nguyên tử trên mọi OS. |
| Ngăn writer hợp tác ghi cùng lúc | Cùng protocol lock hoặc single writer | Process không hợp tác không thể sửa file; không có rollback. |
| Đẩy byte ra thiết bị | `force` hoặc `SYNC/DSYNC` | Record mang schema đúng, không rách, multi-record transaction. |
| Thay một đường dẫn bằng file khác | `Files.move(..., ATOMIC_MOVE)` nếu provider hỗ trợ | Mọi metadata/data đã bền vững hoặc atomic xuyên filesystem. |
| Xác nhận record hoàn chỉnh | Header/length/checksum/trailer, kiểm tra khi đọc | File không bị sửa bởi người không có quyền; checksum không thay xác thực. |

`Files.move(..., StandardCopyOption.ATOMIC_MOVE)` có thể ném `AtomicMoveNotSupportedException` và hành vi thay đích đang tồn tại còn phụ thuộc provider/OS. Nếu cần bảo đảm thật sự, không bỏ qua exception rồi tự tuyên bố thay file là nguyên tử.

## 11. Các chiến lược triển khai theo nhu cầu

**Ứng dụng đơn giản, chỉ một writer:** mở `APPEND + CREATE + WRITE`, dùng một buffer chứa record hoàn chỉnh, `while (hasRemaining())` để ghi hết, đóng channel. Chọn tần suất `force` theo yêu cầu durability, không dùng `force` sau từng record khi chi phí không chấp nhận được.

**Nhiều thread cùng JVM:** cho mọi record qua một hàng đợi có một thread ghi, hoặc khóa toàn bộ khối ghi record trong ứng dụng. Không dựa vào việc `FileChannel` thread-safe để biến 2–3 lời gọi write thành transaction.

**Nhiều process trên cùng máy:** phối hợp qua single-writer process hoặc lock được tất cả process tuân thủ, kèm framing và recovery. Kiểm tra chính xác filesystem; nếu gặp lỗi, phải xác định record nào đã commit theo format chứ không đoán dựa trên một lần `write` thành công.

**Nhiều host / network filesystem:** tránh dựa vào atomic append khi không có hợp đồng kiểm chứng rõ. Xem xét dịch vụ ghi tập trung, message broker hoặc database có transaction phù hợp, quan sát retry, network partition và phục hồi.

**Ghi file cấu hình hoặc snapshot để thay thế:** thông thường tạo file mới ở thư mục đích, ghi hết, flush/force tùy yêu cầu, sau đó di chuyển/thay thế có kiểm tra hỗ trợ atomicity và error handling. Đây là workflow **khác** với append log.

**Có nhiều lần ghi ngắn:** cân nhắc buffer ở tầng ứng dụng nhưng phải flush đúng lúc và quản lý crash recovery. Việc gom nhiều record vào một buffer có thể cải thiện throughput; không tự bảo đảm tất cả record cùng commit nguyên tử.

## 12. Các lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Mở channel báo `IllegalArgumentException` | Ghép `APPEND + READ` hoặc `APPEND + TRUNCATE_EXISTING`. | Dùng đúng bộ open options, tách read và append. |
| Nội dung cũ bị thay đổi | Mở `WRITE` rồi ghi từ offset 0 nhưng nghĩ là append. | Chọn `APPEND` khi cần thêm cuối file. |
| File không được làm rỗng | Có `CREATE` nhưng không có `TRUNCATE_EXISTING`. | Dùng truncate khi thực sự muốn ghi lại toàn bộ file, không kết hợp APPEND. |
| Ghi đè dữ liệu mới từ process khác | Tính `size()` rồi `position(size)` có race. | Single writer/lock; không xem seek EOF là append nguyên tử. |
| Log hỏng hoặc record lẫn nhau | Nhiều write/record hoặc nhiều writer xen kẽ. | Framing, điều phối writer, recovery khi khởi động lại. |
| Dữ liệu ghi bị thiếu | Chỉ gọi `write(buffer)` một lần. | Lặp đến `!buffer.hasRemaining()`. |
| Vừa bật APPEND vừa muốn ghi header ở offset cố định | Positional write khi APPEND có semantics không portable. | Dùng channel không APPEND cho positional update và có coordination. |
| File lock hoạt động khác máy phát triển | Advisory/mandatory khác nhau, mạng không hỗ trợ như mong đợi. | Thống nhất giao thức lock, thử nhiều process trên hệ thật. |
| Gọi `force(true)` nhưng reader thấy record dở dang | force không cung cấp transaction. | Thiết kế commit framing/checksum và đồng bộ hóa reader. |
| Xóa/rename file đang mở bị lỗi | OS/file-sharing mode/provider khác nhau. | Đóng handle, xử lý IOException, kiểm thử quy trình rotate. |
| Số byte file không bằng số ký tự | Dữ liệu UTF-8 chứa Unicode đa byte. | Đo `ByteBuffer.remaining()` hoặc `getBytes(UTF_8).length`. |

## 13. Thực hành và câu hỏi đánh giá

**Bài 1 — Quan sát overwrite so với append.** Chạy chương trình mẫu rồi bỏ `APPEND` ở lần mở channel đầu, giữ nguyên các lệnh `writeFully`. Dự đoán file ra sao: các lần ghi bắt đầu từ position 0 và tiếp tục theo position sau write; nội dung cũ có thể bị thay thế và kích thước file tùy số byte đã ghi. Giải thích vì sao `CREATE` không làm thay đổi hành vi vị trí.

**Bài 2 — Tách header khỏi log.** File có header version/count cố định ở 16 byte đầu và phần log ở sau. Thiết kế hai giai đoạn: cập nhật header bằng channel bình thường có `WRITE` (không APPEND), đóng channel; ghi record mới qua channel `APPEND`. Nêu rủi ro khi hai việc này diễn ra song song và tại sao cần protocol commit thay vì dựa vào 2 thao tác độc lập.

**Bài 3 — Kiểm thử concurrent writer.** Tạo hai chương trình độc lập, mỗi chương trình append 1.000 record với format `length + payload + CRC`. Chạy trên filesystem địa phương và sau đó trên filesystem mạng (nếu có). So sánh số record hợp lệ, các đoạn giao nhau, lần write không đủ và tính chất append; tuyệt đối không coi một lần thử thành bằng chứng atomicity portable.

**Bài 4 — Mô phỏng crash.** Viết header và payload bằng hai lần write, chủ động dừng chương trình giữa chừng trong môi trường thử nghiệm. Xây reader quét file đến record cuối cùng được xác nhận đầy đủ, phát hiện phần đuôi không hoàn chỉnh và đưa ra chính sách truncate/recover có kiểm soát.

**Bài 5 — So sánh `force`.** Dùng ba cấu hình: không force, `force(false)`, `force(true)`. Đo độ trễ ghi với file trên môi trường kiểm thử riêng; diễn giải vì sao hiệu năng, caching, metadata và thiết bị lưu trữ ảnh hưởng số liệu. Không thử crash/sập nguồn trên hệ thống chứa dữ liệu thật.

**Tự kiểm tra:**

1. Vì sao `APPEND` khác `channel.position(channel.size())` trong môi trường đa writer?
2. Vì sao `FileChannel.write(buffer, offset)` không nên dùng khi APPEND bật?
3. Gọi `force(true)` có bảo đảm 3 lần write trở thành transaction không?
4. `FileLock` có giải quyết được cạnh tranh giữa các thread trong cùng JVM không?
5. Tại sao một record lớn chia thành nhiều write vẫn cần framing và recovery?
6. Những khác biệt OS/filesystem nào có thể ảnh hưởng rotate log, shared locks và visibility?

## 14. Tham khảo

- [Java SE 21 — FileChannel API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): mô tả chính thức về APPEND, vị trí file, `force`, lock, concurrency và hành vi phụ thuộc hệ thống.
- [Java SE 21 — StandardOpenOption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html): định nghĩa các tùy chọn `APPEND`, `CREATE_NEW`, `SYNC`, `DSYNC` và các lựa chọn mở file.

**Ghi nhớ:** `APPEND` quy định **đi tới EOF trước mỗi relative write**, nhưng không cung cấp bảo đảm nguyên tử đa writer độc lập với OS. Kết hợp `writeFully`, protocol sở hữu file/lock phù hợp, định dạng bản ghi có thể phục hồi, chiến lược durability và kiểm thử trên filesystem triển khai thực tế mới tạo được một cơ chế ghi cuối file đáng tin cậy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-channel-011">FileChannel không phải selectable channel</a>

<details>
<summary>Xem chi tiết</summary>

# `FileChannel` không phải `SelectableChannel` trong Java NIO

## 1. Mục tiêu và vấn đề thực tế

`java.nio.channels.FileChannel` là API quan trọng để đọc/ghi tệp với `ByteBuffer`, di chuyển vị trí trong tệp, truy cập theo offset, truyền dữ liệu giữa các channel và ánh xạ file vào bộ nhớ. Nhưng **`FileChannel` không phải là `SelectableChannel`**, nên không thể đăng ký trực tiếp vào `Selector` để nhận sự kiện `OP_READ`, `OP_WRITE` như `SocketChannel` hoặc `Pipe.SourceChannel`.

Đây là giới hạn **về hợp đồng kiểu của Java NIO**, không phải thiếu import hay quên gọi một phương thức khởi tạo nào. Một số nhầm lẫn xuất phát từ việc cả hai đều chứa chữ “channel”, nhưng chúng giải quyết hai nhóm nhu cầu khác nhau.

Sau bài, bạn có thể:

1. Giải thích vì sao `FileChannel` không có `register(Selector, ops)` hay `configureBlocking(false)`.
2. Phân biệt file I/O dùng offset với readiness-based I/O trên socket/pipe.
3. Viết và chạy một chương trình chứng minh `FileChannel` không triển khai `SelectableChannel` nhưng có khả năng truy cập tệp ngẫu nhiên.
4. Đăng ký **channel thực sự selectable** với `Selector`, sử dụng `SelectionKey.OP_READ` đúng cách.
5. Chọn giải pháp phù hợp nếu cần xử lý file mà không khóa luồng nghiệp vụ: `AsynchronousFileChannel`, tác vụ thực thi ở thread khác hoặc `WatchService` cho thông báo thay đổi thư mục.
6. Tránh hiểu sai EOF của file, trạng thái “ready” trên selector và lời hứa của các hàm truyền dữ liệu tối ưu.

Ví dụ nhắm đến **Java 17+**, dùng Java SE và không cần Spring hoặc thư viện ngoài.

## 2. Hai họ channel có mục đích khác nhau

### `FileChannel`: làm việc với byte tại vị trí trong tệp

`FileChannel` thuộc `java.nio.channels`, kế thừa `AbstractInterruptibleChannel` và triển khai những giao diện liên quan đến byte I/O, trong đó có:

- `SeekableByteChannel`: `position()`, `position(long)`, `size()`, `truncate(long)`.
- `ReadableByteChannel` và `WritableByteChannel`: đọc/ghi qua `ByteBuffer` khi channel được mở với tùy chọn cho phép.
- `ScatteringByteChannel` và `GatheringByteChannel`: đọc/ghi qua nhiều `ByteBuffer`.
- Các khả năng riêng: `read(dst, position)`, `write(src, position)`, `transferTo`, `transferFrom`, `map`, `lock`, `tryLock`, `force`.

Một `FileChannel` thường được mở bằng:

```java
try (FileChannel file = FileChannel.open(
        path,
        StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    int n = file.read(buffer);
}
```

Đoạn này là snippet cần khai báo `path`, import `FileChannel`, `ByteBuffer`, `StandardOpenOption` và xử lý `IOException`. `n` là số byte thực đọc; có thể nhỏ hơn dung lượng buffer. Khi đã đến cuối file, `read` trả `-1`.

### `SelectableChannel`: chọn kênh có trạng thái sẵn sàng I/O

`SelectableChannel` là một lớp trừu tượng cho các channel có thể đăng ký với `Selector` để theo dõi một tập **operation readiness**. Nó cung cấp các API đặc trưng:

- `configureBlocking(boolean)` / `isBlocking()`: chọn blocking hoặc nonblocking, nếu kênh hỗ trợ.
- `validOps()`: tập bit operation có thể đăng ký cho channel cụ thể.
- `register(Selector, int)`: đăng ký nhận thông báo qua một `SelectionKey`.
- `keyFor(Selector)` và `isRegistered()`: kiểm tra đăng ký.

Những loại channel thường gặp có thể chọn là:

| Loại | Operation thường hợp lệ | Công dụng |
| --- | --- | --- |
| `SocketChannel` | `OP_CONNECT`, `OP_READ`, `OP_WRITE` | Kết nối TCP |
| `ServerSocketChannel` | `OP_ACCEPT` | Chấp nhận TCP connection mới |
| `DatagramChannel` | `OP_READ`, `OP_WRITE` | UDP |
| `Pipe.SourceChannel` | `OP_READ` | Đọc byte từ pipe |
| `Pipe.SinkChannel` | `OP_WRITE` | Ghi byte vào pipe |
| **`FileChannel`** | **Không có `SelectionKey`/`validOps()` của `SelectableChannel`** | **I/O tệp; không register selector** |

Các operation và trạng thái thực tế phụ thuộc kênh. Ví dụ `OP_ACCEPT` chỉ có ý nghĩa trên `ServerSocketChannel`, không phải chỉ vì mọi channel cùng thuộc package NIO mà đều nhận cùng mọi bit.

## 3. Sơ đồ quan hệ kiểu: tên giống nhau nhưng không cùng nhánh

```text
                      Channel (interface)
                         |
          +--------------+----------------------+
          |                                     |
   ByteChannel /                            các Channel khác
   SeekableByteChannel                         |
          |                              SelectableChannel
      FileChannel                            (abstract)
   (abstract class)                              |
     | không kế thừa                   AbstractSelectableChannel
     | SelectableChannel                       |
     +--------------------X              SocketChannel,
                                          ServerSocketChannel,
                                          DatagramChannel,
                                          Pipe.SourceChannel,
                                          Pipe.SinkChannel
```

Sơ đồ cố ý chỉ thể hiện những **mối quan hệ liên quan đến tính selectable**, không phải toàn bộ kế thừa của tất cả giao diện. `FileChannel` và `SelectableChannel` đều thuộc cơ chế `Channel`, nhưng `FileChannel` không kế thừa hay triển khai API của `SelectableChannel`.

Một channel **có thể vừa `ReadableByteChannel` vừa selectable** (ví dụ `SocketChannel`); vậy phân loại theo “có `read(ByteBuffer)`” là chưa đủ. Điều quyết định để `register` là đối tượng thuộc **`SelectableChannel`** và channel có operation phù hợp.

### Điều không thể làm

```java
FileChannel file = FileChannel.open(path, StandardOpenOption.READ);

// Các dòng sau CỐ Ý SAI, không biên dịch:
// file.configureBlocking(false);
// file.register(selector, SelectionKey.OP_READ);
// file.validOps();
```

Đoạn trên chỉ là **mã minh họa lỗi**: `FileChannel` không khai báo các phương thức ấy. Cast ép sang `SelectableChannel` cũng không biến đổi đối tượng, và sẽ thất bại nếu cố làm qua một tham chiếu kiểu `Channel` hoặc `Object`. Không có overload của `Selector.register` nhận `FileChannel`.

Đăng ký một `SelectableChannel` đang ở **blocking mode** cũng không đúng: thao tác `register` yêu cầu nonblocking và có thể ném `IllegalBlockingModeException`. Nhưng với `FileChannel`, bạn còn chưa đến bước kiểm tra blocking mode vì bản thân kiểu kênh đã không selectable.

## 4. “Blocking I/O” khác với “selector readiness”

**Blocking call** nghĩa là luồng gọi một thao tác có thể phải chờ thao tác hoàn thành hoặc gặp lỗi. Với file, hệ điều hành có thể cần đọc block từ thiết bị, xử lý cache, mạng filesystem hoặc đồng bộ metadata. Thời gian có thể rất ngắn, nhưng vẫn không thể coi mọi `FileChannel.read()/write()` là không bao giờ chờ.

**Selector readiness** là cơ chế chờ một hoặc nhiều `SelectableChannel` xuất hiện operation được báo là sẵn sàng; ví dụ socket có byte đang đợi để đọc. Readiness không hứa rằng một lệnh I/O bất kỳ sẽ xử lý hết dữ liệu ngay trong một lần, không thay thế kiểm tra `read()/write()`, và không tự cho biết một message của giao thức ứng dụng đã nhận đủ.

Đối với regular file, hệ điều hành có thể báo nó sẵn sàng đọc trong nhiều tình huống, nhưng khái niệm readiness thường không hữu ích theo cùng cách với socket. **Lý do có thể diễn giải như vậy không phải là hợp đồng thay cho API Java**: kết luận đúng, ổn định và đủ dùng để lập trình Java là **`FileChannel` không triển khai `SelectableChannel`**.

So sánh ba thuật ngữ dễ lẫn:

| Câu hỏi | Nơi trả lời | Ý nghĩa |
| --- | --- | --- |
| Tệp có bao nhiêu byte và đang đọc từ offset nào? | `FileChannel.size()`, `position()` | Trạng thái tệp/đường đọc |
| Socket/pipe có thể đọc ngay theo mô hình readiness không? | `Selector`, `SelectionKey.isReadable()` | Trạng thái sẵn sàng thao tác trên selectable channel |
| Một thao tác tệp hoàn thành khi nào? | `FileChannel.read()` trả về; hoặc `Future/CompletionHandler` của `AsynchronousFileChannel` | Kết quả/completion của thao tác |

Một sự kiện `OP_READ` **không** đồng nghĩa EOF, “file changed” hoặc “đọc đủ record”. Không thiết kế ứng dụng tail log bằng cách thử `fileChannel.register(selector, OP_READ)`.

## 5. Ví dụ Java 17+ chạy được: chứng minh `FileChannel` không selectable

Lưu dưới tên **`FileChannelNotSelectableDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileChannelNotSelectableDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("not-selectable-", ".txt");

        try {
            Files.writeString(file, "ABC", StandardCharsets.US_ASCII);

            try (FileChannel channel =
                         FileChannel.open(file, StandardOpenOption.READ)) {

                // Kiểm tra runtime qua giao diện chung Channel.
                Channel asChannel = channel;

                System.out.println("selectable="
                        + (asChannel instanceof SelectableChannel));
                System.out.println("seekable="
                        + (asChannel instanceof SeekableByteChannel));
                System.out.println("size=" + channel.size());

                channel.position(1);
                ByteBuffer buffer = ByteBuffer.allocate(2);
                int read = channel.read(buffer);

                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                System.out.println("bytesRead=" + read);
                System.out.println("text="
                        + new String(bytes, StandardCharsets.US_ASCII));
                System.out.println("position=" + channel.position());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac FileChannelNotSelectableDemo.java
java FileChannelNotSelectableDemo
```

Stdout khi các thao tác tệp thành công:

```text
selectable=false
seekable=true
size=3
bytesRead=2
text=BC
position=3
```

Lý giải:

1. `Files.writeString` tạo nội dung ASCII chính xác 3 byte: `A`, `B`, `C`.
2. Biến `asChannel` có kiểu giao diện chung `Channel`, nên phép `instanceof SelectableChannel` là kiểm tra hợp lệ tại runtime. Kết quả `false` vì `FileChannel` không có khả năng selectable.
3. `SeekableByteChannel` là giao diện mà `FileChannel` hỗ trợ, nên kết quả `true`.
4. `channel.position(1)` đưa vị trí đọc đến byte thứ hai (`B`). `read(buffer)` đọc hai byte `BC`, trả `2` và tăng file position lên `3`.
5. `buffer.flip()` đổi buffer từ pha nhận byte sang pha đọc byte. Sau đó `get(bytes)` lấy đúng số byte mới nạp.
6. `try-with-resources` đóng channel trước khi xóa file tạm.

Không có dòng nào cần `Selector` trong tác vụ đọc tệp này. Nếu mục tiêu là xử lý dữ liệu tệp theo vị trí, `FileChannel` đã cung cấp đúng API.

## 6. Ví dụ selectable thực sự: `Pipe.SourceChannel` và `Selector`

Để hiểu sự khác biệt, hãy dùng `Pipe.SourceChannel`. Nó là `SelectableChannel`, có thể đưa về nonblocking mode và đăng ký `OP_READ`.

Tạo file **`PipeSelectorDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class PipeSelectorDemo {
    public static void main(String[] args) throws Exception {
        Pipe pipe = Pipe.open();

        try (Pipe.SourceChannel source = pipe.source();
             Pipe.SinkChannel sink = pipe.sink();
             Selector selector = Selector.open()) {

            source.configureBlocking(false);
            source.register(selector, SelectionKey.OP_READ);

            // Ghi byte vào pipe để phía source có dữ liệu đọc.
            ByteBuffer outgoing = ByteBuffer.wrap(
                    "ready".getBytes(StandardCharsets.US_ASCII));
            while (outgoing.hasRemaining()) {
                sink.write(outgoing);
            }

            int n = selector.select(3000); // tối đa 3 giây
            if (n == 0) {
                throw new IOException("No readiness event in time");
            }

            Iterator<SelectionKey> it =
                    selector.selectedKeys().iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove(); // xử lý xong key của lượt chọn này

                if (key.isValid() && key.isReadable()) {
                    ByteBuffer incoming = ByteBuffer.allocate(16);
                    int count = source.read(incoming);
                    if (count > 0) {
                        incoming.flip();
                        byte[] data = new byte[incoming.remaining()];
                        incoming.get(data);

                        System.out.println("readable=true");
                        System.out.println("text="
                                + new String(data, StandardCharsets.US_ASCII));
                    }
                }
            }
        }
    }
}
```

```bash
javac PipeSelectorDemo.java
java PipeSelectorDemo
```

Stdout **khi thao tác pipe và selector bình thường**:

```text
readable=true
text=ready
```

Luồng xử lý:

1. `Pipe.open()` tạo một cặp channel: `SinkChannel` để ghi và `SourceChannel` để đọc.
2. `source.configureBlocking(false)` cho phép đăng ký với selector.
3. `source.register(selector, SelectionKey.OP_READ)` yêu cầu thông báo khi phía đọc có dữ liệu.
4. Lệnh ghi `"ready"` vào `sink` khiến dữ liệu sẵn sàng cho `source`; `selector.select(3000)` đợi sự kiện tối đa 3 giây. Nếu môi trường không báo sự kiện trong thời gian ấy, ví dụ báo lỗi thay vì in kết quả.
5. Khi có key readable, `source.read(incoming)` vẫn cần trả về số byte thực đã đọc. `incoming.flip()` chỉ cho phép giải mã những byte đã nhận.

Nếu bỏ dòng `source.configureBlocking(false)`, việc đăng ký kênh đang blocking với `Selector` có thể dẫn tới `IllegalBlockingModeException`. Đây là ví dụ về **readiness thực sự**. Thay `Pipe.SourceChannel` bằng `FileChannel` sẽ không biên dịch tại dòng `register`.

## 7. Các cách làm đúng nếu muốn xử lý tệp mà không chờ trên luồng chính

“Không muốn block event loop” không đồng nghĩa “phải dùng `Selector`”. Tùy nhu cầu, có nhiều cách phù hợp hơn.

### Cách A: `AsynchronousFileChannel` (Java NIO.2)

`AsynchronousFileChannel` cung cấp các overload đọc/ghi với `Future<Integer>` hoặc `CompletionHandler<Integer, A>`. Bạn chỉ định offset trong tệp cho mỗi lệnh, nhận kết quả khi thao tác hoàn tất. Nó **không phải `SelectableChannel`** và vẫn không thể đăng ký vào `Selector`.

Đặc điểm:

- `AsynchronousFileChannel.read(dst, position)` trả `Future<Integer>`.
- Một overload khác nhận attachment và `CompletionHandler` để được gọi khi thành công/thất bại.
- Cơ chế triển khai của provider/JDK có thể dùng I/O bất đồng bộ của hệ điều hành hoặc thread pool. API không bảo đảm mọi filesystem hay JVM đều có native kernel async I/O.
- `Future.get()` chặn thread **đang gọi get** cho đến khi Future hoàn tất; vì vậy nếu muốn giữ thread chính không bị chặn, hãy tổ chức luồng công việc qua callback, completion hoặc một executor thích hợp.
- Kết quả `read` là số byte thực đã đọc; không mặc định mỗi lần đều đầy `ByteBuffer`. `-1` biểu thị EOF.

### Ví dụ Java 17+ hoàn chỉnh với `AsynchronousFileChannel`

Lưu thành **`AsyncFileReadDemo.java`**:

```java
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;

public class AsyncFileReadDemo {
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("async-file-", ".txt");

        try {
            Files.writeString(path, "HELLO", StandardCharsets.US_ASCII);

            try (AsynchronousFileChannel file =
                         AsynchronousFileChannel.open(
                                 path, StandardOpenOption.READ)) {

                ByteBuffer dst = ByteBuffer.allocate(5);
                long offset = 0;

                while (dst.hasRemaining()) {
                    Future<Integer> future = file.read(dst, offset);
                    int n = future.get(); // demo đợi kết quả tại đây

                    if (n == -1) {
                        break;
                    }
                    if (n == 0) {
                        throw new IllegalStateException(
                                "Read did not make progress");
                    }
                    offset += n;
                }

                dst.flip();
                byte[] result = new byte[dst.remaining()];
                dst.get(result);

                System.out.println("bytes=" + result.length);
                System.out.println("text="
                        + new String(result, StandardCharsets.US_ASCII));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
```

```bash
javac AsyncFileReadDemo.java
java AsyncFileReadDemo
```

Stdout khi hoàn thành thành công:

```text
bytes=5
text=HELLO
```

Ở ví dụ học tập này, `future.get()` được gọi để quan sát completion dễ hiểu. Mô hình thật muốn giữ thread nhận request hoạt động không chặn thì **không gọi `get()` ngay trên event loop**; thay bằng cơ chế completion hoặc chuyển phần chờ sang executor. `AsynchronousFileChannel` không được biến thành một đối tượng có thể đăng ký selector chỉ vì tên chứa `Asynchronous`.

### Cách B: chạy file I/O trên worker thread

Nếu ứng dụng đang dùng event loop cho socket, nhưng đôi khi phải đọc tệp thông thường, bạn có thể chuyển các lệnh `FileChannel.read` / `write` sang một executor chuyên dụng và gửi kết quả xử lý về thành phần điều phối. Thread làm I/O vẫn có thể chờ, nhưng event loop không bị chiếm dụng bởi tác vụ file.

Trong Java 17, `ExecutorService` cùng `submit`/`Future` là cách nền tảng để minh họa. Bạn cần xác định kích thước pool, giới hạn số tác vụ chờ, timeout, xử lý hủy tác vụ, đóng channel và cách truyền kết quả an toàn. Đừng tạo vô hạn thread cho mọi byte/record hoặc dùng một pool không giới hạn khi dữ liệu đến nhanh hơn tốc độ ổ đĩa.

Cách này phù hợp khi bạn dùng một thư viện bắt buộc đọc file đồng bộ, không thể đổi sang API async một cách đơn giản.

### Cách C: `WatchService` để biết directory/file thay đổi

Nếu bài toán là “khi file log được sửa thì kiểm tra phần mới”, hãy tìm hiểu `java.nio.file.WatchService`:

- Đăng ký **thư mục** với `WatchService` bằng các event như `ENTRY_CREATE`, `ENTRY_MODIFY` và `ENTRY_DELETE`.
- Gọi `watcher.take()` hoặc `poll()` để lấy thông báo rồi kiểm tra các file có liên quan.
- Khi đã xác định nội dung cần đọc, dùng `FileChannel` hoặc API file phù hợp để lấy dữ liệu.

`WatchService` **không phải** là `Selector` cho nội dung file. Nó báo sự kiện thay đổi directory entry, có thể gộp sự kiện hoặc tràn hàng đợi (`OVERFLOW`), và hành vi phụ thuộc provider/filesystem. Bạn cần xác thực lại size, offset, trường hợp file bị xóa, đổi tên, truncate hoặc thay thế, cũng như khả năng sự kiện không đầy đủ trên mount từ xa.

Không thể suy ra “file đã có đủ một dòng log hoàn chỉnh” chỉ vì nhận được `ENTRY_MODIFY`; việc parse nội dung vẫn phải xử lý chunk chưa đủ và encoding.

### Cách D: đọc tệp bằng `FileChannel` đồng bộ khi thích hợp

Đối với CLI, batch job, import/export dữ liệu hoặc xử lý file theo block, một vòng `FileChannel.read(buffer)` đồng bộ thường là thiết kế đơn giản và đúng:

```java
try (FileChannel file =
             FileChannel.open(path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(8192);

    while (true) {
        int n = file.read(buffer);
        if (n == -1) {
            break;
        }
        if (n == 0) {
            continue;
        }

        buffer.flip();
        while (buffer.hasRemaining()) {
            byte value = buffer.get();
            // Xử lý value.
        }
        buffer.clear();
    }
}
```

Đây là snippet cần khai báo `path`, các import và xử lý `IOException`. Với file đọc theo block, code có thể tiến bộ mà không cần selector. Nếu quy mô lớn, nên giải mã/xử lý `ByteBuffer` theo chunk hoặc API streaming phù hợp, không cấp phát bộ nhớ tỷ lệ với kích thước file.

## 8. File, pipe, socket và async: bảng quyết định

| Bài toán | API phù hợp | Có đăng ký `Selector`? |
| --- | --- | --- |
| Đọc/ghi file tại offset bất kỳ | `FileChannel` | Không |
| `map` file vào memory để đọc/truy cập ngẫu nhiên | `FileChannel.map` | Không |
| Khóa vùng trong file, `force` dữ liệu | `FileChannel.lock/force` | Không |
| Nhiều socket hoạt động với readiness | `SocketChannel` + `Selector` | Có, khi cấu hình nonblocking |
| Chấp nhận kết nối TCP | `ServerSocketChannel` + `Selector` | Có, `OP_ACCEPT` |
| Chuyển byte qua pipe trong process | `Pipe.SourceChannel/SinkChannel` | Có, theo operation hợp lệ |
| Đọc/ghi file với completion callback hoặc Future | `AsynchronousFileChannel` | Không |
| Được thông báo khi directory entry thay đổi | `WatchService` | Không; dùng `take/poll` |
| Gọi thư viện blocking nhưng giữ event loop rảnh | `FileChannel` + executor / tác vụ riêng | Không cần selector |

**Chú ý về terminology:** “Channel của NIO” là khái niệm rộng. Một loại channel là `FileChannel`, một số loại khác là `SelectableChannel`. Từ “NIO” không có nghĩa mọi thao tác đều nonblocking, cũng không có nghĩa mọi channel đều được dùng với selector.

## 9. Những hiểu nhầm kỹ thuật cần sửa

### Nhầm 1: “Cùng là `Channel` nên cast `FileChannel` thành `SelectableChannel` được”

Phép ép kiểu không cài đặt thêm interface. Object thực tế của `FileChannel.open` không phải selectable. Nếu phải chuyển sang `SelectableChannel` bằng ép kiểu cưỡng bức qua tham chiếu chung, thao tác sẽ thất bại bằng `ClassCastException` ở runtime. Cách sửa: lựa chọn đúng API ngay từ đầu.

### Nhầm 2: “`FileChannel` phải có `configureBlocking(false)` nếu import đúng”

`configureBlocking` là method của `SelectableChannel`. `FileChannel` không có method đó; lỗi biên dịch là hợp lý. Không tồn tại cờ nonblocking portable cho `FileChannel` theo cùng hợp đồng selector như socket.

### Nhầm 3: “File có dữ liệu nên selector luôn báo readable”

`FileChannel` không có `register` để tạo `SelectionKey`, nên không có sự kiện readable của file trên Java `Selector`. Nếu muốn biết file lớn thêm hoặc đổi nội dung, đọc metadata, theo dõi directory với `WatchService`, hoặc phối hợp bằng giao thức riêng.

### Nhầm 4: “`Selector` giúp file I/O chạy nhanh hơn”

Selector phục vụ multiplex readiness trên các kênh hỗ trợ; nó không phải một nút bật tốc độ đọc ổ đĩa. Với file lớn, hãy cân nhắc kích thước buffer, chiến lược access, `FileChannel.transferTo/transferFrom`, batching và đặc tính thiết bị. Cần đo đạc thực tế thay vì suy đoán từ chữ “nonblocking”.

### Nhầm 5: “`FileChannel.transferTo` luôn zero-copy, không cần lặp”

`transferTo(position, count, target)` có thể tận dụng tối ưu đặc thù provider/OS, nhưng Java không cam kết mọi đường đi đều là zero-copy hoặc một lần gọi truyền toàn bộ `count`. Kết quả là số byte **thực sự truyền**, có thể nhỏ hơn yêu cầu; khi xây vòng truyền hoàn chỉnh phải cập nhật offset và xử lý trường hợp không tiến triển.

Ví dụ `transferTo` nhận `position` làm offset đọc rõ ràng; không đồng nghĩa nhất thiết thay đổi `position()` của source channel theo lời gọi ấy. Phần file mapping/transfer cần được kiểm chứng riêng, không lấy làm bằng chứng file selectable.

### Nhầm 6: “`read()==-1` trên file nghĩa là không có dữ liệu mãi mãi”

Khi `FileChannel.read()` đọc tại EOF hiện tại, kết quả có thể là `-1`. Nhưng nếu một chương trình khác nối thêm dữ liệu vào tệp sau đó, việc đọc lại ở offset phù hợp có thể thấy byte mới. Với log rotation, file có thể bị truncate hoặc thay thế bằng file khác, và channel cũ có thể tiếp tục gắn với đối tượng file cũ. Ứng dụng tail file cần xử lý offset, size, identity, sự kiện và vòng đời tài nguyên; không có selector nào tự giải quyết điều đó.

### Nhầm 7: “`AsynchronousFileChannel` cũng dùng `Selector`”

`AsynchronousFileChannel` thuộc API asynchronous file I/O có `Future`/`CompletionHandler` và không extends `SelectableChannel`. Hai mô hình **completion** và **readiness** khác nhau:

```text
Readiness:
  Selector.select() -> kênh báo sẵn sàng -> ứng dụng thử read/write

Completion:
  AsynchronousFileChannel.read(...) -> thao tác được khởi động
                                     -> Future/CompletionHandler nhận kết quả
```

Không nhầm callback báo tác vụ file hoàn tất với `SelectionKey.OP_READ`.

### Nhầm 8: “Nếu tách file I/O sang thread khác thì hoàn toàn không block”

Kỹ thuật đó giữ **thread gọi/event loop** khỏi chờ trực tiếp, nhưng worker thread vẫn có thể block. Nếu số file operation không được giới hạn, pool và queue có thể quá tải. Cần bảo vệ tài nguyên, backpressure và xử lý lỗi.

## 10. Diễn giải đúng về I/O file và tiến trình đọc

Ví dụ một tiến trình đang ghi file log tăng dần:

```text
time t0: file size=100 bytes
time t1: reader position=100, read() -> -1
time t2: writer append thêm 30 bytes, size=130
time t3: reader có thể đọc bytes tại offset 100..129
```

Điểm cần thiết:

- EOF ở t1 là kết quả tại **thời điểm đó**.
- Tệp thông thường không có cơ chế `Selector.OP_READ` qua `FileChannel` để đợi 30 byte mới.
- Có thể theo dõi `size` định kỳ ở khoảng hợp lý hoặc dùng `WatchService` cho thông báo thay đổi thư mục, sau đó xác minh và đọc phần mới.
- Việc tự thiết kế tailer còn phải xử lý truncate (size giảm), rotation (file path trỏ tới inode/đối tượng khác theo OS), duplicate event, encoding và record chưa hoàn chỉnh.
- Phân biệt xử lý phản hồi HTTP/socket nonblocking với xử lý file log dạng append; hai loại tài nguyên cần chiến lược chờ khác nhau.

Vì `FileChannel` có `read(dst, position)` và `position(long)`, việc giữ offset chính xác là lợi thế. Việc theo dõi **khi nào** offset mới có dữ liệu là trách nhiệm của ứng dụng hoặc `WatchService`, chứ không đến từ `Selector`.

## 11. Kiểm tra kiến thức về `SelectionKey`

Một `SelectionKey` đại diện cho một đăng ký của **`SelectableChannel`** với một `Selector`. Nó có:

- `interestOps()`: tập hoạt động ứng dụng muốn được thông báo.
- `readyOps()` và `isReadable()/isWritable()/isAcceptable()/isConnectable()`: hoạt động mà lần chọn báo sẵn sàng.
- `channel()`: selectable channel được đăng ký.

Đối với kênh đang được đăng ký:

1. Chuyển nó sang nonblocking mode trước khi `register`.
2. Chỉ đăng ký operation nằm trong `channel.validOps()`. Nếu đăng ký bit không hợp lệ, API có thể ném `IllegalArgumentException`.
3. Sau `selector.select(...)`, duyệt `selectedKeys()` rồi loại key đã xử lý khỏi tập được chọn (như `it.remove()`), nếu không logic xử lý sau có thể gặp lại key.
4. Không đồng nhất ready với đã thực hiện I/O; vẫn cần `read/write` và kiểm tra giá trị trả về.
5. Đóng channel hoặc hủy key cần được quản lý cùng tài nguyên ứng dụng.

Các quy tắc này **chỉ áp dụng cho channel selectable**; `FileChannel` không có `SelectionKey` để đem đi kiểm tra.

## 12. Lưu ý về kiểm thử và hiệu năng

Với một chức năng dùng `FileChannel`:

- Viết kiểm thử đọc/ghi một tệp tạm theo byte và offset cụ thể; kiểm tra `size()`, `position()`, EOF, partial transfer khi phù hợp.
- Không viết test mong `FileChannel` hỗ trợ `OP_READ`; xác nhận nó **không** selectable là một thuộc tính ổn định ở tầng API.
- Không dùng số lần `read/write` hay tốc độ máy phát triển làm bảo đảm xử lý trên mọi filesystem.
- Nếu kiểm thử bất đồng bộ, kiểm tra cả thành công, lỗi, hủy, timeout và vấn đề thứ tự; đừng dựa vào tốc độ hoàn thành cố định.
- Đảm bảo dọn `Path` tạm sau khi đóng mọi channel; đặc biệt trên Windows, file còn mở có thể ảnh hưởng khả năng xóa.
- Tải dữ liệu file lớn theo chunk hữu hạn, tránh nạp tất cả vào `byte[]` một lần; đo đạc trước khi tối ưu.
- Với dữ liệu cần độ bền khi ghi, `FileChannel.force(metaData)` có hợp đồng đồng bộ cập nhật tới thiết bị lưu trữ theo khả năng hệ thống, nhưng có các giới hạn về thiết bị, provider và filesystem. Đây là tính năng file channel, không liên quan selector.

## 13. Câu hỏi tự kiểm tra

1. Vì sao một `FileChannel` có `read(ByteBuffer)` nhưng không gọi được `register(selector, OP_READ)`?
2. `FileChannel` và `SocketChannel` có cùng interface `ReadableByteChannel` trong những trường hợp nào? Điều đó có đủ để cả hai selectable không?
3. Khi nào bạn dùng `FileChannel.position(100)`, và khi nào cần một `SelectionKey.OP_READ`? Hai khái niệm trả lời những câu hỏi gì khác nhau?
4. Nếu có 10.000 kết nối TCP, lựa chọn giữa `Selector` và 10.000 `FileChannel` có ý nghĩa gì? Vì sao cách đặt câu hỏi “tối ưu file bằng selector” bị nhầm phạm vi?
5. Muốn nhận thông báo một file log vừa đổi, bạn có thể dùng `WatchService` thế nào, và vì sao vẫn cần kiểm tra file sau khi nhận event?
6. `AsynchronousFileChannel.read(dst, offset)` trả gì? `Future.get()` được gọi trên event loop có còn lợi ích “không chặn” không?
7. Nếu `transferTo` chỉ truyền một phần byte được yêu cầu, ứng dụng phải làm gì trước khi kết luận đã sao chép file xong?
8. `Pipe.SourceChannel` cần gọi gì trước khi đăng ký selector? Có thể áp dụng cùng method đó cho `FileChannel` không?
9. Nếu `FileChannel.read()` trả `-1` nhưng tác giả file tiếp tục append, EOF đó có còn đúng vĩnh viễn không?
10. Sự khác nhau giữa **readiness** và **completion** trong mô hình NIO là gì?

## 14. Tài liệu tham khảo Java SE 17

- [`FileChannel` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): file position, scattering/gathering I/O, transfer, map, lock, force.
- [`SelectableChannel` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SelectableChannel.html): `register`, `configureBlocking`, `validOps`.
- [`Selector` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/Selector.html): chọn readiness của selectable channel.
- [`AsynchronousFileChannel` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/AsynchronousFileChannel.html): file read/write với `Future` và `CompletionHandler`.
- [`WatchService` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/WatchService.html): theo dõi thay đổi directory.

## 15. Ghi nhớ

**`FileChannel` là channel thao tác tệp, không phải `SelectableChannel`.** Nó hỗ trợ `ByteBuffer`, file position, random access, transfer và các tính năng riêng của file, nhưng không thể `configureBlocking(false)` hay `register` với `Selector`. **`Selector`** được dùng cùng các channel thực sự selectable như `SocketChannel`, `ServerSocketChannel` và `Pipe.SourceChannel`.

Nếu cần xử lý file mà không chặn luồng chính, có thể cân nhắc `AsynchronousFileChannel` hoặc chuyển `FileChannel` sang worker thread. Nếu cần thông báo file được sửa, `WatchService` hoặc cơ chế theo dõi phù hợp là hướng giải quyết. Hãy chọn API theo loại tài nguyên và kiểu tín hiệu cần nhận: **offset/size của file**, **readiness của socket/pipe**, **completion của thao tác async** hay **sự kiện thay đổi thư mục**.


</details>

- [Quay lại đầu trang](#back-to-top)
