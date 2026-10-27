<a id="back-to-top"></a>

# Kiểm thử và chẩn đoán

## Menu
- [Kiểm thử bằng stream bộ nhớ trước khi phụ thuộc tệp thật](#testing-and-diagnostics-001)
- [Tạo thư mục tạm riêng cho từng kịch bản](#testing-and-diagnostics-002)
- [Kiểm tra nội dung byte chính xác và round-trip văn bản](#testing-and-diagnostics-003)
- [Dữ liệu Unicode, BOM và ký tự bị chia giữa các khối](#testing-and-diagnostics-004)
- [Tệp rỗng, tệp lớn, EOF sớm và bản ghi không hoàn chỉnh](#testing-and-diagnostics-005)
- [Mô phỏng partial read, partial write và lỗi giữa chừng](#testing-and-diagnostics-006)
- [Kiểm tra tài nguyên được đóng khi có lỗi](#testing-and-diagnostics-007)
- [Kiểm tra symbolic link, quyền và hành vi theo nền tảng](#testing-and-diagnostics-008)
- [Chẩn đoán sai working directory, charset và đường dẫn resource](#testing-and-diagnostics-009)
- [Chẩn đoán dữ liệu chưa flush, file handle còn mở và buffer sai trạng thái](#testing-and-diagnostics-010)
- [Kiểm tra event, callback và thao tác đồng thời theo cách xác định](#testing-and-diagnostics-011)

## <a id="testing-and-diagnostics-001">Kiểm thử bằng stream bộ nhớ trước khi phụ thuộc tệp thật</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử bằng stream bộ nhớ trước khi phụ thuộc tệp thật trong Java I/O

## 1. Mục tiêu của chiến lược kiểm thử

Khi một chức năng có nhiệm vụ **đọc dữ liệu, biến đổi nội dung rồi ghi kết quả**, lỗi trong thuật toán xử lý byte hoặc ký tự thường có thể được tìm ra mà không cần tạo tệp trên ổ đĩa. Thay vì gắn cứng logic vào `Path`, `FileInputStream` hay `FileOutputStream`, hãy tách **phần xử lý dữ liệu** khỏi **phần mở tài nguyên** và kiểm tra phần xử lý bằng stream lưu trữ trong RAM.

Ví dụ, muốn kiểm thử thao tác sao chép:

~~~text
Cách gắn chặt tệp:
  Path đầu vào -> Files.newInputStream -> copy -> Files.newOutputStream -> Path đích
                  ^                       ^     ^
                  |                       |     |
             khó cô lập              logic bị lẫn vào I/O thật

Cách tách phụ thuộc:
  InputStream  ----------------> copy(input, output) ----------------> OutputStream
       ^                                                               ^
       |                                                               |
  ByteArrayInputStream                                     ByteArrayOutputStream
  (kiểm thử trong RAM)                                      (kiểm thử trong RAM)

Khi triển khai thật:
  Files.newInputStream(path)                                Files.newOutputStream(path)
~~~

Với mô hình này, cùng một phương thức `copy(InputStream, OutputStream)` chạy trên **dữ liệu trong bộ nhớ** ở unit test và **tệp thực tế** ở integration test; không cần fork logic thành hai phiên bản.

Lợi ích thường thấy:

- **Nhanh, lặp lại được:** không phụ thuộc vị trí thư mục, quyền ghi, tên tệp bị trùng hoặc dung lượng ổ đĩa khi chỉ kiểm tra thuật toán.
- **Dễ định vị lỗi:** sai offset, thứ tự byte, encoding, EOF hoặc việc gọi `flush` thể hiện trực tiếp qua mảng byte/string kỳ vọng.
- **Đưa lỗi đặc biệt vào bài test:** có thể tạo stream giả giới hạn byte mỗi lần đọc, hoặc chủ động ném `IOException`.
- **Ít side effect:** test không tạo file rác, ít vấn đề dọn dẹp, tránh làm thay đổi dữ liệu người dùng.
- **Dễ song song hóa:** mỗi test sở hữu mảng byte và đối tượng stream độc lập.

Tuy vậy, kiểm thử trong RAM **không thay thế hoàn toàn** integration test với filesystem: quyền truy cập, symlink, symbolic link race, khóa, việc file bị sửa đồng thời, durability, khác biệt giữa các provider và xử lý tên đường dẫn vẫn cần được kiểm tra trong môi trường phù hợp.

## 2. Hiểu hai tầng: byte streams và character streams

Java I/O phân biệt hai hệ stream:

| Nhóm | Kiểu gốc | Triển khai bộ nhớ | Dùng kiểm thử |
| --- | --- | --- | --- |
| Byte-oriented | `InputStream` / `OutputStream` | `ByteArrayInputStream` / `ByteArrayOutputStream` | File nhị phân, giao thức byte, checksum, ZIP, dữ liệu mã hóa. |
| Character-oriented | `Reader` / `Writer` | `StringReader` / `StringWriter` | Nội dung văn bản đã decode, parser, chuẩn hóa dòng, template. |
| Character-oriented dạng mảng | `Reader` / `Writer` | `CharArrayReader` / `CharArrayWriter` | Tác vụ làm việc trực tiếp trên `char[]`, kiểm tra đoạn ký tự. |

**Byte không đồng nghĩa ký tự.** Một chữ Việt có dấu như `é` khi mã hóa UTF-8 dùng nhiều byte, nhưng khi thao tác qua `Reader`, chương trình thấy một hoặc nhiều **UTF-16 code unit** tùy code point. Nếu chức năng cần kiểm tra encoding, hãy dùng `ByteArrayInputStream` kết hợp `InputStreamReader` với charset rõ ràng, không dùng `StringReader` rồi tuyên bố đã kiểm thử UTF-8.

Với text, cần trả lời trước:

- Phần được kiểm thử có trách nhiệm **giải mã bytes thành characters** không?
- Hay phần được kiểm thử chỉ **xử lý text** sau khi charset đã được chọn?

Nếu chỉ kiểm thử parser ký tự, `StringReader` giúp cô lập logic. Nếu muốn phát hiện lỗi decode, malformed UTF-8 hoặc BOM, phải kiểm tra tại tầng byte-to-character.

## 3. ByteArrayInputStream: dựng đầu vào trong RAM

`ByteArrayInputStream` đọc từ một `byte[]` đã có:

~~~java
byte[] fixture = {10, 20, 30};
try (InputStream in = new ByteArrayInputStream(fixture)) {
    System.out.println(in.read()); // 10
    System.out.println(in.read()); // 20
    System.out.println(in.read()); // 30
    System.out.println(in.read()); // -1: EOF
}
~~~

Đây là **mảnh mã đặt trong phương thức**; cần import `java.io.InputStream` và `java.io.ByteArrayInputStream`. `read()` trả một `int` từ 0 đến 255 hoặc `-1` khi EOF, không dùng `byte` âm để biểu diễn EOF.

Các điểm cần nhớ:

1. Constructor `ByteArrayInputStream(byte[])` dùng **mảng được truyền vào**, không tự sao chép toàn bộ nó. Nếu test thay đổi mảng sau khi tạo stream, dữ liệu đọc được cũng có thể đổi.
2. `ByteArrayInputStream(byte[], offset, length)` có thể mô phỏng một đoạn dữ liệu bắt đầu từ vị trí khác 0.
3. Stream hỗ trợ `markSupported() == true`, `mark(...)` và `reset()`. Điều này hữu ích để test code cần đọc thử rồi quay lại, nhưng **không được suy ra mọi `InputStream` đều hỗ trợ mark/reset**.
4. `available()` cho biết số byte còn lại trong riêng memory stream này. Trong các nguồn I/O chung, `available()` **không** phải kích thước toàn bộ nội dung và không phải cách xác định EOF.
5. `close()` của `ByteArrayInputStream` không làm gì. Đừng dùng hành vi đó để kết luận rằng code đóng stream đầu vào của caller luôn vô hại.

Dùng chuỗi tiếng Việt làm fixture thì phải chọn charset cụ thể:

~~~java
byte[] inputBytes = "Tiếng Việt".getBytes(StandardCharsets.UTF_8);
InputStream in = new ByteArrayInputStream(inputBytes);
~~~

Đây là mảnh minh họa, cần import `java.nio.charset.StandardCharsets`. Không dùng `getBytes()` thiếu charset nếu kết quả byte phải ổn định giữa môi trường.

## 4. ByteArrayOutputStream: quan sát kết quả không cần tệp

`ByteArrayOutputStream` ghi byte vào một buffer có thể tăng dung lượng:

~~~java
ByteArrayOutputStream out = new ByteArrayOutputStream();
out.write(new byte[] {65, 66, 67});
byte[] written = out.toByteArray();
System.out.println(Arrays.toString(written)); // [65, 66, 67]
~~~

Mảnh mã trên cần `java.io.ByteArrayOutputStream` và `java.util.Arrays`. `toByteArray()` trả về **một bản sao độc lập** của nội dung hiện tại. Có thể gọi lại sau khi ghi thêm để quan sát phần mới.

Một số API hữu ích:

| API | Ý nghĩa |
| --- | --- |
| `new ByteArrayOutputStream()` | Tạo output buffer trong RAM. |
| `new ByteArrayOutputStream(initialCapacity)` | Chọn dung lượng ban đầu, không giới hạn độ dài cuối cùng. |
| `write(int b)` | Ghi một byte, sử dụng 8 bit thấp. |
| `write(byte[], off, len)` | Ghi đúng vùng mảng chỉ định. |
| `size()` | Số byte đã ghi. |
| `toByteArray()` | Trả bản sao nội dung byte hiện tại. |
| `toString(StandardCharsets.UTF_8)` | Decode byte theo charset, tiện cho bài test text. |
| `reset()` | Xóa nội dung logic để tái sử dụng đối tượng. |
| `writeTo(OutputStream out)` | Sao chép nội dung hiện tại sang output stream khác. |

**Giới hạn:** buffer này giữ toàn bộ output trong RAM. Với dữ liệu hàng gigabyte, việc kiểm thử bằng một `ByteArrayOutputStream` không phản ánh đặc tính bộ nhớ của tệp/stream streaming thật; có thể dùng output giả đếm byte hoặc tính checksum mà không tích lũy mọi byte.

`ByteArrayOutputStream.close()` không có hiệu lực; các lệnh ghi sau close vẫn có thể hoạt động. `FileOutputStream`/network output thông thường không có hành vi đó. Do vậy, nếu muốn kiểm tra đúng ownership/close semantics thì cần stream giả theo dõi close hoặc integration test thực.

## 5. Thiết kế mã dễ kiểm thử: nhận stream thay vì tự mở path

**Đừng đặt `Files.newInputStream(...)` trong chính hàm chứa thuật toán** nếu logic cần unit test độc lập với filesystem. Tách hai lớp:

~~~java
// Logic có thể kiểm thử trong RAM:
static long copy(InputStream source, OutputStream target)
        throws IOException {
    byte[] buffer = new byte[8192];
    long copied = 0;
    int n;

    while ((n = source.read(buffer)) != -1) {
        if (n == 0) {
            throw new IOException("InputStream không tiến triển");
        }
        target.write(buffer, 0, n);
        copied += n;
    }
    target.flush();
    return copied;
}

// Adapter mở file thực tế:
static long copyFile(Path from, Path to) throws IOException {
    try (InputStream source = Files.newInputStream(from);
         OutputStream target = Files.newOutputStream(to)) {
        return copy(source, target);
    }
}
~~~

Hai phương thức trên là **mảnh mã**, cần import `java.io.*`, `java.nio.file.Files` và `Path`.

Quy ước ownership:

- Hàm `copy(InputStream, OutputStream)` **không đóng** stream được caller truyền vào, bởi vì caller có thể muốn sử dụng tiếp hoặc truyền output qua một bước khác.
- Hàm `copyFile(Path, Path)` **sở hữu** hai resource mà nó vừa mở, nên đóng bằng `try-with-resources`.
- `flush()` được gọi khi copy thành công để hỗ trợ những output có bộ đệm; nhưng `flush` không đồng nghĩa dữ liệu đã được đồng bộ bền vững tới thiết bị lưu trữ.
- Nếu output nằm trên tệp đã tồn tại, `Files.newOutputStream(to)` mặc định có thể tạo/truncate theo API. Production cần quyết định riêng chuyện overwrite, atomic replace, backup, quyền và lỗi.
- Nếu input và output trỏ cùng một file, adapter trên có thể phá dữ liệu. Kiểm tra và xử lý theo yêu cầu; unit test trong RAM sẽ **không phát hiện** vấn đề này.

Vòng lặp `read(buffer)` thông thường trên InputStream với mảng có độ dài dương có hợp đồng chờ ít nhất một byte hoặc EOF; nhánh `n == 0` ở đây để phát hiện implementation bất thường hoặc wrapper không tuân thủ. Không dùng nó làm quy tắc cho `SocketChannel.read(ByteBuffer)` non-blocking, nơi `0` là kết quả hợp lệ.

## 6. Bài test cơ bản nên bao phủ những gì?

Một unit test cho sao chép/biến đổi stream không chỉ cần tình huống “hello world”. Tối thiểu nên kiểm tra:

| Trường hợp | Dữ liệu / tình huống | Cần xác nhận |
| --- | --- | --- |
| Empty input | `byte[0]` | Output rỗng, số byte 0, không exception. |
| Một byte | Một giá trị như `0xFF` | Bảo toàn byte không dấu, EOF đúng. |
| Buffer nhiều chunk | Input dài hơn buffer | Không thiếu/lặp byte, thứ tự giữ nguyên. |
| Đợt đọc ngắn | Stream trả tối đa 1–2 byte mỗi read | Code sử dụng `n`, không giả định một read đầy buffer. |
| Byte đặc biệt | `0x00`, `0x80`, `0xFF` | Không nhầm null byte với kết thúc dữ liệu. |
| UTF-8 đa byte | `"AéB"`, `"Tiếng Việt"` | Đối chiếu chính xác byte và charset. |
| Output lỗi | Wrapper ném `IOException` sau vài byte | Exception được truyền/giải quyết đúng, không tuyên bố thành công. |
| Input lỗi | Stream ném lỗi sau một số lần đọc | Không nuốt lỗi, không ghi output giả thành công. |
| Flush/close | Stream theo dõi số lần flush/close | Ownership theo hợp đồng. |
| Mark/reset | Stream hỗ trợ và không hỗ trợ | Code không phụ thuộc sai vào đặc tính ByteArrayInputStream. |
| Partial parsing | Header/payload chia chunk | Parser giữ dữ liệu còn thiếu và phát hiện EOF thiếu dữ liệu. |

Số byte nhận được từ một lần `InputStream.read(byte[])` **có thể nhỏ hơn chiều dài mảng**, kể cả khi vẫn còn dữ liệu để đọc. Test nên cố tình tạo điều kiện đó.

## 7. Chương trình kiểm thử độc lập Java 17+ trong RAM

Ví dụ dưới đây có:

- `copy(...)` là logic cần kiểm thử.
- `TwoByteInputStream`: wrapper cố tình cho mỗi lần đọc tối đa hai byte.
- `FailAfterThreeBytesOutputStream`: output giả phát sinh `IOException` ngay khi ứng dụng cố ghi byte thứ tư.
- Các phép xác nhận dùng `require(...)` ném `AssertionError` nếu sai; không cần thư viện test ngoài JDK.
- Test dữ liệu UTF-8 `AéB`, empty input, check read chunk, stream ký tự và DataInput/DataOutput nhị phân.

Lưu file `MemoryStreamTestDemo.java`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MemoryStreamTestDemo {

    static long copy(InputStream source, OutputStream target)
            throws IOException {
        byte[] buffer = new byte[4];
        long total = 0;
        int n;
        while ((n = source.read(buffer)) != -1) {
            if (n == 0) {
                throw new IOException("InputStream không tiến triển");
            }
            target.write(buffer, 0, n);
            total += n;
        }
        target.flush();
        return total;
    }

    static long copyChars(Reader source, Writer target)
            throws IOException {
        char[] chars = new char[3];
        long total = 0;
        int n;
        while ((n = source.read(chars)) != -1) {
            if (n == 0) {
                throw new IOException("Reader không tiến triển");
            }
            target.write(chars, 0, n);
            total += n;
        }
        target.flush();
        return total;
    }

    static final class TwoByteInputStream extends ByteArrayInputStream {
        TwoByteInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, 2));
        }
    }

    static final class FailAfterThreeBytesOutputStream
            extends OutputStream {
        private int accepted;

        @Override
        public void write(int b) throws IOException {
            if (accepted >= 3) {
                throw new IOException("Injected output error");
            }
            accepted++;
        }
    }

    static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] original =
                "AéB".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long count;
        try (InputStream input = new TwoByteInputStream(original)) {
            count = copy(input, output);
        }

        require(count == 4, "Số byte sao chép");
        require(Arrays.equals(original, output.toByteArray()),
                "Nội dung byte không giống input");
        System.out.println("copiedBytes=" + count);
        System.out.println("copiedText=" +
                output.toString(StandardCharsets.UTF_8));

        ByteArrayOutputStream emptyOutput =
                new ByteArrayOutputStream();
        long emptyCount = copy(
                new ByteArrayInputStream(new byte[0]), emptyOutput);
        require(emptyCount == 0 && emptyOutput.size() == 0,
                "Input rỗng phải cho output rỗng");
        System.out.println("emptyBytes=" + emptyCount);

        StringWriter writer = new StringWriter();
        long chars = copyChars(
                new StringReader("abc\nxyz"), writer);
        require(chars == 7 && writer.toString().equals("abc\nxyz"),
                "Character stream bị đổi nội dung");
        System.out.println("copiedChars=" + chars);
        System.out.println("textLines=" +
                writer.toString().replace("\n", "|"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream data =
                new DataOutputStream(bytes)) {
            data.writeInt(0x01020304);
            data.writeUTF("Hi");
        }

        try (DataInputStream data = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            int value = data.readInt();
            String text = data.readUTF();
            require(value == 0x01020304 && text.equals("Hi"),
                    "Sai định dạng dữ liệu nhị phân");
            System.out.println("dataBytes=" + bytes.size());
            System.out.println("dataRoundTrip=" +
                    Integer.toHexString(value) + ":" + text);
        }

        boolean writeErrorCaught = false;
        try {
            copy(
                    new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}),
                    new FailAfterThreeBytesOutputStream());
        } catch (IOException expected) {
            writeErrorCaught = true;
        }
        require(writeErrorCaught, "Lỗi output phải được báo");
        System.out.println("writeErrorCaught=" + writeErrorCaught);
        System.out.println("allChecksPassed=true");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 MemoryStreamTestDemo.java
java MemoryStreamTestDemo
~~~

**Stdout chính xác trên Java 17+ khi chạy chương trình với file nguồn UTF-8:**

~~~text
copiedBytes=4
copiedText=AéB
emptyBytes=0
copiedChars=7
textLines=abc|xyz
dataBytes=8
dataRoundTrip=1020304:Hi
writeErrorCaught=true
allChecksPassed=true
~~~

### 7.1. Tại sao `copiedBytes=4`, không phải 3?

Chuỗi `AéB` có ba ký tự hiển thị, nhưng `é` có mã Unicode U+00E9 được mã hóa thành **hai byte UTF-8**. Dãy byte là:

~~~text
'A'    'é'      'B'
41     C3 A9    42
     tổng: 4 byte
~~~

`TwoByteInputStream` khiến thuật toán không thể mặc định một lần `read` điền đầy buffer 4 byte. Sau cùng, `Arrays.equals` so sánh **dữ liệu byte** chính xác với bản gốc. Nếu lập trình viên ghi nhầm `target.write(buffer)` thay vì `write(buffer, 0, n)`, test với lượt đọc ngắn/lượt cuối ngắn sẽ phát hiện byte dư.

### 7.2. Tại sao `copiedChars=7`?

Dữ liệu `"abc\nxyz"` có bảy UTF-16 code unit: ba chữ, một newline và ba chữ. Trong `Reader`/`Writer`, phép đếm là số `char`, **không tự đồng nghĩa số byte UTF-8** và cũng không phải số Unicode code point trong trường hợp chứa supplementary characters.

### 7.3. Vì sao `dataBytes=8`?

`DataOutputStream.writeInt` ghi **4 byte** big-endian cho `0x01020304`. `writeUTF("Hi")` ghi độ dài modified UTF-8 theo định dạng `DataOutput` trong **2 byte** rồi ghi hai byte `'H'` và `'i'`. Tổng bằng `4 + 2 + 2 = 8`. `DataInputStream.readUTF` tương thích với định dạng modified UTF-8 này, khác với việc tùy tiện dùng một chuỗi UTF-8 thuần không có tiền tố độ dài.

### 7.4. Vì sao output lỗi được kiểm thử riêng?

`FailAfterThreeBytesOutputStream` nhận ba byte rồi cố ý ném lỗi khi ghi byte thứ tư. `OutputStream.write(byte[], off, len)` mặc định có thể gọi lần lượt `write(int)`, vì vậy lỗi được đẩy lên `copy`. Test xác nhận chương trình **không giả vờ báo copy thành công** khi output thất bại. Với ứng dụng thật, còn phải kiểm tra file đích ở trạng thái nào sau lỗi và chính sách xóa tệp tạm/rollback.

## 8. StringReader và StringWriter: kiểm tra parser ký tự

`StringReader` đọc ký tự từ một `String`, còn `StringWriter` ghi ký tự vào vùng nhớ tăng được. Không có thao tác charset conversion ở bên trong hai lớp này.

Ví dụ parser chỉ cần chuẩn hóa các dòng:

~~~java
static String linesToCsvLike(Reader reader) throws IOException {
    BufferedReader input = new BufferedReader(reader);
    StringJoiner result = new StringJoiner(",");
    String line;
    while ((line = input.readLine()) != null) {
        result.add(line.trim());
    }
    return result.toString();
}
~~~

Đây là **mảnh phương thức**, cần import `BufferedReader`, `Reader`, `IOException`, `StringJoiner`. Hàm không tự đóng `reader` vì resource được caller sở hữu.

Kiểm thử hoàn toàn trong bộ nhớ:

~~~java
String actual = linesToCsvLike(
        new StringReader("  blue \r\n green\nred"));
if (!actual.equals("blue,green,red")) {
    throw new AssertionError(actual);
}
~~~

Vì `BufferedReader.readLine()` loại bỏ ký tự xuống dòng theo các dấu kết thúc dòng mà API hỗ trợ, kết quả có thể kiểm chứng ổn định cho fixture có `\r\n` và `\n`.

**Chú ý:** `StringReader` nhận sẵn ký tự Java, nên test này **không phát hiện lỗi decode UTF-8** của file thật. Nếu parser phụ thuộc BOM, newline encoding hoặc malformed bytes, test riêng tầng decoder bằng `InputStreamReader`.

### 8.1. CharArrayReader và CharArrayWriter

Hai lớp này hữu ích nếu hệ thống đã có `char[]` hoặc chỉ muốn dùng một đoạn mảng ký tự:

~~~java
char[] chars = {'a', 'b', 'c', 'd'};
try (Reader reader = new CharArrayReader(chars, 1, 2);
     CharArrayWriter writer = new CharArrayWriter()) {
    reader.transferTo(writer);
    System.out.println(writer.toString()); // bc
}
~~~

Đây là mảnh mã cần import `java.io.*`. Chú ý `Reader.transferTo(Writer)` có từ Java 10, nên dùng được trên Java 17.

## 9. Kiểm thử cả tầng charset bằng stream byte

Một nguy cơ là lập trình viên phụ thuộc charset mặc định của máy, khiến code chạy trên CI và máy cá nhân cho kết quả khác nhau.

Nên truyền charset hoặc cố định `StandardCharsets.UTF_8`:

~~~java
static String readUtf8(InputStream source) throws IOException {
    try (Reader reader =
            new InputStreamReader(source, StandardCharsets.UTF_8)) {
        StringWriter writer = new StringWriter();
        reader.transferTo(writer);
        return writer.toString();
    }
}
~~~

Đây là **mảnh phương thức minh họa**; cần import `InputStreamReader`, `InputStream`, `StringWriter` và `StandardCharsets`. Ở đây phương thức **đóng Reader và vì vậy đóng InputStream đầu vào**: hành vi khác với `copy` trước đó. Hãy chọn hợp đồng ownership nhất quán với thiết kế, hoặc để caller quản lý Reader nếu caller sở hữu nguồn.

Unit test:

~~~java
byte[] utf8 = "Tiếng Việt".getBytes(StandardCharsets.UTF_8);
String actual = readUtf8(new ByteArrayInputStream(utf8));
if (!"Tiếng Việt".equals(actual)) {
    throw new AssertionError(actual);
}
~~~

Nếu cần test **malformed UTF-8**, `InputStreamReader` mặc định thường thay dữ liệu không hợp lệ bằng ký tự replacement theo cấu hình decoder. Muốn kiểm tra việc từ chối byte sai thay vì thay thế âm thầm, tạo `CharsetDecoder` với `CodingErrorAction.REPORT` và test nhánh xử lý `MalformedInputException`. Chính sách replace/report phải thuộc hợp đồng ứng dụng.

Đặc biệt, đọc từng chunk byte rồi gọi `new String(chunk, UTF_8)` độc lập có thể làm hỏng một ký tự UTF-8 nằm giữa hai chunk. Stream decoder giữ trạng thái là hướng tiếp cận đúng khi xử lý dữ liệu text theo nhiều đợt.

## 10. Tiêm lỗi để kiểm thử đường thất bại

Memory streams chuẩn thường thành công và nhanh đến mức có thể **che mất bug xử lý lỗi**. Có thể tạo test doubles bằng subclass/wrapper:

- `FilterInputStream`: giới hạn số byte trong mỗi lần read, ghi log số lần đọc, hoặc ném lỗi ở một lần gọi nhất định.
- `FilterOutputStream`: theo dõi `flush`/`close` và từ chối write sau một ngưỡng.
- Custom `InputStream`: kiểm thử EOF ngay sau header, stream chỉ trả một byte/lần, hoặc lỗi I/O giữa chừng.
- Custom `OutputStream`: giả disk-full, broken pipe hoặc permission error, nhưng cần làm rõ đó chỉ là **mô phỏng** của kết quả API.

### 10.1. Giới hạn số byte mỗi lượt đọc

~~~java
final class OneByteAtATime extends FilterInputStream {
    OneByteAtATime(InputStream inner) {
        super(inner);
    }

    @Override
    public int read(byte[] bytes, int offset, int length)
            throws IOException {
        return super.read(bytes, offset, Math.min(length, 1));
    }
}
~~~

Đây là mảnh khai báo class, cần `java.io.FilterInputStream`, `InputStream` và `IOException`. Trong trường hợp `length == 0`, wrapper vẫn truyền 0 về bên dưới, giữ hành vi zero-length read.

Lý do quan trọng: thuật toán đúng không được dựa vào “`read(buffer)` luôn trả `buffer.length`”; nhiều nguồn thật trả ít hơn do network, buffering hoặc EOF.

### 10.2. Theo dõi flush và close

~~~java
final class TrackingOutputStream extends ByteArrayOutputStream {
    int flushCount;
    int closeCount;

    @Override
    public void flush() throws IOException {
        flushCount++;
        super.flush();
    }

    @Override
    public void close() throws IOException {
        closeCount++;
        super.close();
    }
}
~~~

Đây là mảnh class có thể đặt trong test và cần import tương ứng. Với thiết kế `copy` ở phần 5, ta kỳ vọng:

- Sau copy thành công, `flushCount == 1`.
- `closeCount == 0` vì hàm `copy` không sở hữu output stream.
- Khi caller tự đóng output, `closeCount` mới tăng.

Nếu test muốn xác minh resource được đóng khi có lỗi, hãy tạo wrapper có close tracking và dùng code adapter có `try-with-resources`. Nhưng không nên biến mọi helper xử lý thành hàm tự đóng stream của caller.

### 10.3. Test lỗi InputStream giữa chừng

Một lớp có thể ném lỗi sau hai byte:

~~~java
final class FailAfterTwoBytes extends InputStream {
    private int position;

    @Override
    public int read() throws IOException {
        if (position == 2) {
            throw new IOException("Injected read error");
        }
        position++;
        return 'x';
    }
}
~~~

Đây là mảnh class test. Lưu ý `InputStream.read(byte[],off,len)` mặc định có thể **trả phần dữ liệu đã đọc thành công trước khi gặp IOException**, rồi ném lỗi ở lời gọi tiếp theo. Vì vậy đừng viết test đòi “lần gọi đầu tiên phải ném” khi wrapper có thể trả partial read trước.

Cần kiểm thử rằng lỗi cuối cùng được chuyển tới caller, không được silently ignore hoặc biến thành kết quả đã copy đủ dữ liệu.

## 11. In-memory test có thể bỏ sót điều gì?

Kiểm thử bằng `ByteArrayInputStream`/`ByteArrayOutputStream` hữu ích nhưng không mô phỏng hoàn chỉnh môi trường sản xuất:

| Hành vi quan trọng | In-memory test | Vì sao cần test tệp thật / môi trường khác |
| --- | --- | --- |
| Đọc/ghi bytes và transform | Rất phù hợp. | Algorithm có thể được test đầy đủ. |
| EOF, short read, lỗi giả lập | Có thể chủ động mô phỏng. | Stream giả chỉ mô phỏng các trường hợp đã nghĩ tới. |
| Charset encode/decode | Có thể test bằng byte fixture. | Provider, file BOM, dữ liệu thực tế có thể đa dạng hơn. |
| Quyền truy cập và ACL | Không mô phỏng thật. | Hệ điều hành/provider kiểm soát quyền. |
| Symlink/path traversal/TOCTOU | Không đủ. | Tác động đường dẫn và đối tượng tệp thật. |
| Kích thước/độ bền sau flush | Không kiểm chứng. | `flush` không tương đương `FileChannel.force`/fsync. |
| File lock và concurrent writer | Không thực tế. | Cần process/thread/filesystem test. |
| Tên tệp, separator, case sensitivity | Không có trong byte stream. | Khác nhau theo OS/provider. |
| Atomic move/rename | Không được kiểm tra. | Phụ thuộc filesystem. |
| File bị truncate trong lúc đọc | Có thể giả stream lỗi, không đủ. | Cần kiểm tra cơ chế file/ownership thật. |
| Buffering/close của file/network | Chỉ theo custom wrappers. | Runtime/hệ điều hành có semantics riêng. |
| ZIP/GZIP nén/giải nén | Có thể dùng byte array rất tốt. | Nếu phụ thuộc file metadata/quota/path extraction phải test riêng. |

Một phép kiểm tra memory stream thành công chứng minh **logic được test chạy đúng trên input và stream double đã dùng**, chứ không chứng minh tất cả resource thực sẽ hoạt động đúng.

## 12. Khi nào chuyển từ unit test sang integration test với file?

Một trình tự thực tế:

1. **Kiểm thử core** với `ByteArrayInputStream`/`ByteArrayOutputStream` hoặc `StringReader`/`StringWriter`: nội dung, empty, chunk, EOF, lỗi I/O được mô phỏng.
2. **Kiểm thử adapter** mở file bằng thư mục tạm `Files.createTempDirectory` hoặc JUnit `@TempDir`: xác nhận đường dẫn, quyền, đóng resource, file có nội dung kỳ vọng.
3. **Kiểm thử định dạng** với file/stream thật của XML, JSON, ZIP, GZIP hoặc binary protocol: charset, metadata, cấu trúc.
4. **Kiểm thử môi trường đặc biệt** khi yêu cầu: readonly filesystem, thiếu quyền, symbolic link, atomic move, file lock, file bị thay đổi đồng thời, networking.
5. **E2E / stress tests** cho dữ liệu lớn, cạnh tranh, tải cao, timeout, resource leaks và hành vi sau crash.

Không nhất thiết chạy tất cả integration tests ở mỗi chỉnh sửa nhỏ, nhưng nên có ít nhất một tập kiểm tra adapter tệp thật trong CI tương ứng những nền tảng mục tiêu.

### 12.1. Test với tệp tạm để xác nhận adapter

~~~java
Path input = Files.createTempFile("input-", ".bin");
Path output = Files.createTempFile("output-", ".bin");
try {
    byte[] original = {0, 1, 2, (byte) 0xFF};
    Files.write(input, original);

    long count = copyFile(input, output);
    if (count != original.length
            || !Arrays.equals(original, Files.readAllBytes(output))) {
        throw new AssertionError("File adapter sai");
    }
} finally {
    Files.deleteIfExists(input);
    Files.deleteIfExists(output);
}
~~~

Đây là **mảnh code minh họa tích hợp**, giả định hai phương thức `copy`/`copyFile` trong phần trước đã được khai báo, và các import `Files`, `Path`, `Arrays` có sẵn. Trong bài kiểm thử thật, nên dùng thư mục tạm riêng thay vì tên dự đoán được; JUnit có `@TempDir` để cung cấp thư mục riêng và dọn sau bài test.

**Không tạo file tạm trong một vị trí dùng chung rồi giả định không ai khác có thể thay đổi nó.** Khi dữ liệu không tin cậy, phải có chính sách quyền/ownership phù hợp. `Files.createTempFile` tạo tên khó đoán và tạo mới nguyên tử theo provider, nhưng những giả định về bảo mật vẫn phụ thuộc hệ điều hành.

## 13. Test bằng JUnit 5 thay cho main có AssertionError

Chương trình `MemoryStreamTestDemo` chạy được chỉ với Java 17, phù hợp ví dụ độc lập. Trong dự án thật, nên tách từng case thành test method bằng JUnit Jupiter và tích hợp Maven/Gradle.

Ví dụ một phương thức test (không phải mã độc lập nếu chưa có JUnit):

~~~java
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

@Test
void copyWorksWithShortReads() throws IOException {
    byte[] original = "AéB".getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream target = new ByteArrayOutputStream();
    try (InputStream source = new TwoByteInputStream(original)) {
        long copied = copy(source, target);
        assertEquals(original.length, copied);
    }

    assertArrayEquals(original, target.toByteArray());
}
~~~

Code trên giả định có phương thức `copy` và lớp `TwoByteInputStream` trong lớp test, cùng các import `java.io.*` và `StandardCharsets`. Cần dependency JUnit Jupiter và test runner tương ứng, khác với ví dụ main không có dependency ở phần 7.

### 13.1. Assert theo ý nghĩa hợp đồng

- **`assertArrayEquals(expected, actual)`:** chính xác byte-by-byte với payload binary.
- **`assertEquals(expectedText, actualText)`:** phù hợp với output text sau khi đã định nghĩa charset.
- **`assertThrows(IOException.class, ...)`:** xác minh đường thất bại đúng loại lỗi.
- **`assertTimeout...`:** cần dùng thận trọng; timeout test không phải bằng chứng mọi I/O trong production có deadline phù hợp.
- **`@TempDir`:** cho integration test filesystem mà không phải tự dự đoán tên file hoặc làm bẩn repository.

Đừng chỉ dùng `assertTrue(output.size() > 0)` cho thuật toán copy. Nó không phát hiện thiếu byte, lặp byte, sai thứ tự hay encoding sai.

## 14. Quy tắc tạo fixture ổn định và dễ chẩn đoán

Một test tốt nên **ít phụ thuộc môi trường**:

- Không dùng `Charset.defaultCharset()` hoặc `String.getBytes()` thiếu charset nếu test cần kết quả byte xác định.
- Không phụ thuộc line separator của OS: dùng ký tự `\n`/`\r\n` do test định nghĩa hoặc chuẩn hóa đầu ra trước khi so sánh, tùy yêu cầu.
- Không dùng `System.currentTimeMillis()` cho nội dung kỳ vọng khi có thể truyền clock giả hoặc giá trị cố định.
- Không dựa vào thứ tự trả về của `Files.list`/directory iteration nếu không có quy tắc sort.
- Không phụ thuộc port mạng cố định khi có thể để OS cấp port 0, nhưng đó đã là integration test network chứ không còn unit test memory.
- Không dùng mảng/shared stream mutable giữa các test chạy song song; tạo fixture riêng cho từng test.
- Khi thất bại, đưa vào thông điệp assert số byte kỳ vọng/thực tế và định dạng hex với binary payload.
- Đối với test Unicode, cần có fixture đa byte UTF-8 và supplementary code point khi logic xử lý code point.
- Không vô tình đưa dữ liệu thật nhạy cảm vào fixture hoặc log của CI.

## 15. Chọn stream giả nào cho từng trường hợp?

| Muốn kiểm tra | Gợi ý |
| --- | --- |
| Sao chép byte và đối chiếu tuyệt đối | `ByteArrayInputStream` + `ByteArrayOutputStream`. |
| Parser dòng văn bản | `StringReader` / `BufferedReader`. |
| Format text output | `StringWriter`. |
| Chuyển đổi UTF-8 | `ByteArrayInputStream` + `InputStreamReader(UTF_8)`. |
| Write bytes có độ dài và số nhị phân | `DataOutputStream` bọc `ByteArrayOutputStream`. |
| Read protocol theo số nhị phân | `DataInputStream` bọc `ByteArrayInputStream`. |
| Đọc chậm/chia chunk | `FilterInputStream` tùy chỉnh giới hạn read size. |
| Phát sinh IOException | Custom `InputStream` / `OutputStream`. |
| Kiểm tra flush/close | Wrapper ghi nhận số lần gọi. |
| Đếm byte output rất lớn mà không giữ toàn bộ trong RAM | `OutputStream` tùy chỉnh chỉ tăng counter/checksum. |
| Kiểm tra path/ACL/symlink/provider | Integration test với tệp thực. |

Một mô hình bền vững là **core stream-oriented + adapters resource-oriented**. Core chỉ biết `InputStream`/`OutputStream` hoặc `Reader`/`Writer`, còn adapter xử lý `Path`, file locks, buffer policy, charset và vòng đời OS resource.

## 16. Những sai lầm thường gặp

### 16.1. Tin ByteArrayInputStream có hành vi giống mọi nguồn thực

`ByteArrayInputStream` thường trả dữ liệu tức thì và hỗ trợ `mark/reset`, nên code có lỗi giả định về short read hoặc `available()` có thể vẫn “đúng” trong test cơ bản. Bổ sung stream giả giới hạn read, và không gọi `available()` để đo tổng chiều dài input chung.

### 16.2. Luôn truyền buffer.length thay cho n

Lượt đọc cuối có thể ít byte hơn buffer; ghi `output.write(buffer)` sẽ đưa byte cũ của lượt trước vào output. Dùng `write(buffer, 0, n)` với **n thực nhận**.

### 16.3. Chuyển text bằng charset mặc định

`new String(bytes)` và `text.getBytes()` thiếu charset có thể khác nhau theo runtime/OS. Chỉ rõ `StandardCharsets.UTF_8` (hoặc charset theo protocol) để test có tính tái lập.

### 16.4. Không tách xử lý khỏi mở file

Hàm chỉ nhận `Path` và tự mở tệp trong mọi nhánh khiến các kiểm thử chính phải tạo file tạm và phối hợp cleanup không cần thiết. Tách core thành hàm nhận stream, adapter mở file ở biên.

### 16.5. Đóng stream của caller trong core

Một phương thức helper tự đóng `OutputStream` được chia sẻ có thể khiến pipeline phía sau không tiếp tục ghi được. Hãy xác định **ai sở hữu stream** và kiểm thử hợp đồng đó. Test riêng với ByteArrayOutputStream có thể không phát hiện vì close của lớp đó vô hiệu.

### 16.6. Chỉ kiểm tra happy path

Không kiểm tra empty input, short read, encoding, dữ liệu nhị phân có `0x00`, IOException giữa chừng và xử lý output lỗi khiến phần lớn bug I/O bị bỏ sót.

### 16.7. Dùng memory output cho dữ liệu lớn không giới hạn

`ByteArrayOutputStream` giữ toàn bộ byte và có thể tăng nhiều bộ nhớ. Stress test cho stream dữ liệu lớn nên dùng sink đếm byte/checksum, hoặc môi trường tệp tạm và giới hạn cụ thể.

### 16.8. Coi test trong RAM là đủ cho filesystem semantics

Một method dùng `Files.move`, `Files.delete`, `FileChannel.force` hay file lock phụ thuộc OS/provider; stream memory không thể kiểm tra ý nghĩa của chúng. Cần integration tests thích hợp.

### 16.9. Nhầm write thành công với lưu dữ liệu bền vững

`ByteArrayOutputStream.flush()` không biểu diễn fsync. Trong tệp thật, `OutputStream.flush()` không mặc định bảo đảm bytes đã nằm bền vững trên thiết bị; với yêu cầu durability, kiểm tra API như `FileChannel.force` và tài liệu provider.

### 16.10. Tự viết file giả quá phức tạp

Chỉ giả lập **hành vi cần kiểm thử** (read chunk ngắn, lỗi, close tracking). Đừng tự tái tạo toàn bộ filesystem trong một mock khó chứng minh đúng; khi cần kiểm tra path/file attributes, chuyển sang tệp tạm và provider thật.

## 17. Câu hỏi tự kiểm tra

1. **Vì sao truyền InputStream/OutputStream thay vì chỉ truyền Path giúp test nhanh hơn?** Vì có thể thay nguồn/đích bằng memory streams, không cần truy cập filesystem cho thuật toán.
2. **`ByteArrayInputStream.read()` trả gì khi hết dữ liệu?** `-1`.
3. **`ByteArrayInputStream` có tự sao chép mảng được đưa vào không?** Không, nó dùng mảng đó làm backing data.
4. **`ByteArrayOutputStream.toByteArray()` có chia sẻ mảng nội bộ không?** Không, trả một bản sao.
5. **Một read nhận 2 byte vào buffer 8 byte thì write bao nhiêu byte?** Đúng 2 byte qua `write(buffer, 0, 2)`.
6. **`AéB` dưới UTF-8 có bao nhiêu byte?** 4 byte.
7. **`StringReader` có kiểm tra quá trình giải mã UTF-8 không?** Không; nó đọc ký tự đã có.
8. **Dùng gì để test decoder UTF-8 từ bytes?** `ByteArrayInputStream` cùng `InputStreamReader`/`CharsetDecoder` với charset và chính sách lỗi cụ thể.
9. **Vì sao mark/reset test chỉ dùng ByteArrayInputStream là chưa đủ?** Stream này hỗ trợ mark, nhưng nhiều stream khác không.
10. **Khi output ném IOException sau vài byte, copy có được báo thành công không?** Không; phải truyền/giải quyết lỗi đúng hợp đồng.
11. **Ai nên đóng stream đã được caller truyền cho helper?** Theo ownership đã định; nếu caller sở hữu thì helper không tự đóng.
12. **`flush()` của ByteArrayOutputStream có chứng minh dữ liệu đã nằm bền vững trên disk không?** Không.
13. **Bao giờ phải chuyển sang test tệp thật?** Khi kiểm tra mở/đóng file, permission, symlink, atomic move, metadata, file locks, durability hoặc provider behavior.
14. **Có nên dùng assert output không rỗng thay cho so sánh toàn bộ byte?** Không; thiếu/lặp/sai thứ tự có thể vẫn cho output không rỗng.
15. **Cách mô phỏng read chunk nhỏ trong unit test?** Dùng custom InputStream/FilterInputStream trả tối đa 1–2 byte mỗi lần.
16. **Vì sao một số trường hợp malformed UTF-8 cần CharsetDecoder với REPORT?** Vì decode mặc định có thể thay byte không hợp lệ bằng ký tự replacement, không ném lỗi theo mong muốn.
17. **Làm sao kiểm thử file copy với tệp tạm?** Viết input, gọi adapter, đọc output rồi so sánh byte và dọn resource, hoặc dùng JUnit `@TempDir`.
18. **Nếu logic dựa trên tính duy nhất của đường dẫn, memory streams có đủ không?** Không. Cần filesystem integration test và mô hình concurrency thích hợp.

## 18. Tài liệu Java 17 chính thức

- [ByteArrayInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayInputStream.html) và [ByteArrayOutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ByteArrayOutputStream.html): stream byte trong bộ nhớ.
- [StringReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/StringReader.html) và [StringWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/StringWriter.html): stream ký tự.
- [InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html) và [OutputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html): hợp đồng chung, EOF, read/write.
- [InputStreamReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html) và [CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html): decode byte thành char.
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): adapter tệp thật, tệp tạm và thao tác đường dẫn.

**Ghi nhớ:** trước khi kiểm thử trên file thật, hãy tạo **core nhận InputStream/OutputStream hoặc Reader/Writer** và kiểm chứng bằng ByteArrayInputStream/ByteArrayOutputStream, StringReader/StringWriter. Thêm fixture empty, Unicode, read chunk ngắn và IOException để kiểm tra đường thất bại. Sau khi core ổn định, dùng integration test với tệp tạm để kiểm chứng **filesystem semantics**, đừng coi memory stream là một filesystem giả hoàn chỉnh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-002">Tạo thư mục tạm riêng cho từng kịch bản</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo thư mục tạm riêng cho từng kịch bản kiểm thử trong Java 17+

## 1. Tại sao phải cô lập dữ liệu I/O của từng kịch bản?

Khi kiểm thử xử lý tệp, ứng dụng thường cần tạo file đầu vào, ghi file đầu ra, tạo cây thư mục hoặc mô phỏng đường lỗi. Nếu nhiều kịch bản cùng sử dụng một vị trí cố định như `./test-data/output.txt`, các lần chạy có thể ảnh hưởng lẫn nhau:

- Bài kiểm thử trước để lại file khiến lần chạy sau thành công hoặc thất bại sai.
- Hai bài kiểm thử chạy song song ghi cùng tên file và tạo **race condition**.
- Bài kiểm thử phụ thuộc dữ liệu tồn tại trên máy lập trình viên nhưng không có trên CI.
- Khi kiểm thử lỗi, dữ liệu ghi dở vẫn còn và tác động đến kịch bản kế tiếp.
- Lệnh dọn dẹp dùng đường dẫn cứng có thể xóa nhầm dữ liệu thật.

Một giải pháp mặc định tốt là **mỗi kịch bản có thư mục tạm riêng**, tạo ngay khi kịch bản bắt đầu và dọn khi kết thúc:

~~~text
Thư mục tạm của hệ điều hành
├── test-import-48297.../
│   ├── input/
│   │   └── data.csv
│   └── output/
│       └── result.json
└── test-export-12058.../
    ├── input/
    │   └── data.csv
    └── output/
        └── result.json
~~~

Các tên có phần ngẫu nhiên ở trên chỉ là minh họa. Hai kịch bản đều có thể dùng đường dẫn tương đối `input/data.csv` mà vẫn không đụng nhau vì **thư mục gốc hoàn toàn khác nhau**.

Sau bài này, bạn sẽ biết cách tạo thư mục tạm bằng `Files.createTempDirectory`, phân chia phạm vi ownership, xử lý cleanup khi test thất bại, kiểm tra tính portable trên CI và kết hợp với framework kiểm thử như JUnit Jupiter `@TempDir`.

## 2. API cốt lõi: `Files.createTempDirectory`

Java NIO.2 định nghĩa hai dạng phổ biến:

~~~java
Path root = Files.createTempDirectory("case-import-");

Path rootUnderParent = Files.createTempDirectory(
        parentDirectory, "case-import-");
~~~

Đây là **đoạn minh họa**, cần import `java.nio.file.Files`, `java.nio.file.Path`; `parentDirectory` phải là thư mục cha phù hợp, tồn tại và có quyền tạo mục.

- `Files.createTempDirectory(String prefix, FileAttribute<?>... attrs)` tạo một thư mục mới trong vùng tạm mặc định do môi trường/provider quyết định.
- `Files.createTempDirectory(Path dir, String prefix, FileAttribute<?>... attrs)` tạo một thư mục mới **ngay dưới `dir`**, phù hợp khi muốn tất cả artifact kiểm thử nằm trong workspace tạm dành riêng cho lần chạy.
- Hàm trả về `Path` của **thư mục vừa tạo**; `Path` chỉ là biểu diễn đường dẫn, **không phải tài nguyên `AutoCloseable`** và không tự xóa khi ra khỏi scope.
- Mỗi lần gọi được thiết kế để tạo tên chưa tồn tại trong vị trí đích. Bạn không cần tự tạo tên bằng timestamp hoặc ép xung đột bằng số thứ tự chia sẻ.
- `prefix` giúp nhìn log/trace thuận tiện; không nên dùng dữ liệu nhạy cảm như token, mật khẩu hoặc tên khách hàng trong prefix.
- Nếu không có quyền ghi, đường dẫn không hợp lệ hoặc provider không hỗ trợ cấu hình attributes, lời gọi có thể thất bại; test cần báo lỗi thay vì lặng lẽ chuyển sang xóa/thay thế một thư mục khác.

Ví dụ tạo file nằm trong vùng cô lập:

~~~java
Path root = Files.createTempDirectory("scenario-");
Path inputDir = Files.createDirectories(root.resolve("input"));
Path input = inputDir.resolve("data.txt");

Files.writeString(input, "sample", StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW);
~~~

Đây là ví dụ rời, cần import `Files`, `Path`, `StandardCharsets`, `StandardOpenOption` và cleanup khi hoàn thành. `CREATE_NEW` từ chối ghi đè đường dẫn file đã tồn tại; kết hợp với root mới là một kiểm tra tốt rằng test không phụ thuộc file cũ.

**Không nhầm `Files.createTempFile(...)` với `createTempDirectory(...)`.** Hàm đầu tạo file mới, hàm sau tạo thư mục mới. Với một kịch bản cần nhiều input/output, tạo một thư mục gốc rồi dùng `resolve` để tổ chức file bên trong thường thuận tiện hơn.

## 3. Phạm vi ownership: ai tạo thì ai dọn?

Một nguyên tắc đơn giản:

> Thành phần tạo root tạm sở hữu root đó, chịu trách nhiệm dọn nội dung bên trong và **chỉ** nội dung bên trong root đó.

Ví dụ phương thức test sở hữu vùng:

~~~text
testScenario():
  root = createTempDirectory()
  try:
      chuẩn bị input trong root
      chạy mã cần kiểm thử trên các path thuộc root
      assert output
  finally:
      xóa cây root
~~~

Nếu helper nhận `Path root` từ caller, helper **không nên tự xóa cả root** trừ khi hợp đồng nói rõ nó được giao ownership. Helper chỉ nên tạo/sửa những mục đã được caller cho phép.

Phạm vi thích hợp tùy bài toán:

| Phạm vi | Cách dùng | Khi nào hợp lý |
| --- | --- | --- |
| Một scenario/test method | Mỗi lần gọi tạo một root mới | Lựa chọn mặc định giúp test độc lập |
| Một nhóm test dùng chung fixture bất biến | Có thể tạo một root cho nhóm, chia thư mục con | Khi setup đắt và dữ liệu **thật sự không bị sửa** |
| Một lần chạy bộ test | Tạo parent duy nhất rồi tạo root con cho từng scenario | Dễ thu thập artifact và dọn cả lần chạy |
| Một tác vụ concurrent | Root riêng cho mỗi request/task | Khi nhiều tác vụ có thể ghi cùng tên file |
| Production job | Staging root riêng cho job | Khi cần không công bố dữ liệu ghi dở; thường có chính sách dọn và giám sát riêng |

**Cô lập root không tự động cô lập mọi thứ.** Hai test có root khác nhau vẫn có thể tranh chấp cổng TCP cố định, database, biến môi trường, static state, file ngoài root hay tài nguyên native. Nếu gặp vấn đề đó, phải cô lập hoặc điều phối từng tài nguyên tương ứng.

## 4. Không sử dụng đường dẫn cố định hoặc tên dựng từ thời gian

Mẫu dễ gây xung đột:

~~~java
Path root = Path.of("build/tmp/my-test");
Files.createDirectories(root);
~~~

Đây là **mẫu phản ví dụ** nếu nhiều kịch bản cùng dùng `root` mà không có cơ chế điều phối. `createDirectories` cho phép thư mục đã tồn tại, nên một test có thể vô tình dùng lại file hoặc dữ liệu của test trước.

Mẫu tốt hơn:

~~~java
Path base = Path.of("build", "test-scratch");
Files.createDirectories(base);

Path scenarioRoot = Files.createTempDirectory(base, "import-");
~~~

Đoạn này chỉ minh họa. Thư mục cha `build/test-scratch` có thể là workspace dùng chung; còn `scenarioRoot` luôn là thư mục con mới. Trong CI hoặc chạy parallel, cần bảo đảm `base` nằm trên phân vùng đủ dung lượng và có quyền ghi. Nếu dự án dùng hệ thống build khác, không bắt buộc sử dụng tên `build`.

**Không dựa vào timestamp `System.currentTimeMillis()` để tạo uniqueness tuyệt đối.** Hai tác vụ có thể khởi phát trong cùng mili giây. `UUID` hoặc một hàm tạo tên an toàn có thể dùng trong trường hợp đặc biệt, nhưng `Files.createTempDirectory` đã giải quyết trực tiếp nhu cầu tạo thư mục mới mà không cần tự thiết kế thuật toán.

## 5. Ví dụ Java 17+ hoàn chỉnh: hai kịch bản, tên file giống nhau

Chương trình độc lập dưới đây chạy hai kịch bản sử dụng chung tên file tương đối `input/data.txt` và `output/result.txt`. Mỗi kịch bản tự tạo root mới, chuẩn bị nội dung khác nhau, copy dữ liệu và kiểm tra. `finally` bảo đảm quy trình dọn được gọi dù assert hoặc thao tác I/O bị lỗi.

Lưu file **`ScenarioTempDirectoryDemo.java`**:

~~~java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;

public class ScenarioTempDirectoryDemo {

    public static void main(String[] args) throws IOException {
        runScenario("alpha", "NORTH");
        runScenario("beta", "SOUTH");
        System.out.println("All scenarios passed");
    }

    private static void runScenario(String name, String content)
            throws IOException {
        Path root = Files.createTempDirectory("io-" + name + "-");

        try {
            Path inputDirectory =
                    Files.createDirectory(root.resolve("input"));
            Path outputDirectory =
                    Files.createDirectory(root.resolve("output"));

            Path input = inputDirectory.resolve("data.txt");
            Path output = outputDirectory.resolve("result.txt");

            Files.writeString(
                    input, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);

            // Mô phỏng một thao tác I/O mà test muốn xác minh.
            Files.copy(input, output, StandardCopyOption.COPY_ATTRIBUTES);

            String actual =
                    Files.readString(output, StandardCharsets.UTF_8);
            if (!content.equals(actual)) {
                throw new AssertionError("Unexpected file content");
            }

            System.out.println(name + " output: " + actual);
        } finally {
            deleteTree(root);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path directory, IOException error)
                    throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**:

~~~bash
javac -encoding UTF-8 ScenarioTempDirectoryDemo.java
java ScenarioTempDirectoryDemo
~~~

**Stdout kỳ vọng khi môi trường cho phép tạo, sao chép và xóa file tạm:**

~~~text
alpha output: NORTH
beta output: SOUTH
All scenarios passed
~~~

### Giải thích mã

1. `runScenario` gọi `Files.createTempDirectory(...)` cho **mỗi** scenario, nên root của `alpha` và root của `beta` khác nhau. Prefix chỉ hỗ trợ truy vết; phần tên còn lại do provider sinh.
2. Trong mỗi root, chương trình tự tạo hai thư mục con bằng `Files.createDirectory`, sau đó dùng tên `data.txt` và `result.txt` giống nhau. Nếu dùng chung root, hai kịch bản có thể xung đột; dùng hai root giúp tránh phụ thuộc này.
3. `Files.writeString(..., CREATE_NEW)` tạo file input mới và không ghi đè file tồn tại. `Files.copy` copy sang đường dẫn output chưa tồn tại.
4. `Files.readString` đọc file output có nội dung rất nhỏ. Ở ví dụ này việc đọc toàn file là hợp lý; với file lớn trong bài học hiệu năng cần chọn đọc theo khối.
5. Kiểm tra `content.equals(actual)` xác minh từng kịch bản nhận chính xác dữ liệu của nó. `AssertionError` được dùng để báo test failure mà không cần thư viện ngoài.
6. `finally` chạy sau thao tác thành công hoặc lỗi. `deleteTree` dọn file trước, thư mục sau, phù hợp vì xóa một thư mục còn file bên trong sẽ thất bại.
7. `Files.walkFileTree` **không theo symbolic link theo mặc định**; code không truyền `FileVisitOption.FOLLOW_LINKS`. Vì vậy một symlink được phát hiện như mục trong cây sẽ được xóa như mục liên kết, thay vì cố truy cập cây đích của symlink.
8. Stdout không in tên root ngẫu nhiên, nên khi I/O thành công thì kết quả luôn như trên. Nếu môi trường chặn quyền ghi/xóa, chương trình báo lỗi và không giả vờ hoàn thành.

**Lưu ý:** `COPY_ATTRIBUTES` cố gắng sao chép một số thuộc tính cơ bản, nhưng khả năng hỗ trợ phụ thuộc file system. Với mục tiêu chỉ test byte content, có thể dùng `Files.copy(input, output)` đơn giản hơn. Nếu `COPY_ATTRIBUTES` không được hỗ trợ, ví dụ có thể báo lỗi thay vì in kết quả; đây là lý do nên bỏ tùy chọn không cần thiết trong test portable.

## 6. Dọn cây thư mục đúng: vì sao không chỉ dùng `Files.delete(root)`?

`Files.delete(path)` xóa một file hoặc **thư mục trống**. Nếu root tạm còn tệp đầu vào, tệp đầu ra hoặc thư mục con, xóa root trực tiếp có thể gặp `DirectoryNotEmptyException`.

Ví dụ không đúng khi root có nội dung:

~~~java
Files.delete(root); // Thất bại nếu root còn file/thư mục con.
~~~

Cách thường dùng trong Java Core là duyệt toàn cây rồi xóa **con trước, cha sau**, chẳng hạn `Files.walkFileTree` với `SimpleFileVisitor` như ví dụ đầy đủ ở phần 5.

Một mẫu khác có thể dùng `Files.walk(root)`, sắp xếp thứ tự ngược và xóa:

~~~java
try (var paths = Files.walk(root)) {
    for (Path p : paths.sorted(
            java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
    }
}
~~~

Đây là **đoạn minh họa**, yêu cầu `root` là vùng được phép xóa và import `Files`, `Path`. `Files.walk` tạo stream cần đóng bằng `try-with-resources`. `toList()` tích lũy tất cả path vào RAM; trên cây rất lớn, `walkFileTree` thường thích hợp hơn vì xử lý từng mục.

**Không dùng xóa đệ quy trên một đường dẫn không được kiểm soát.** Đặc biệt tránh “cleanup” bằng cách xóa trực tiếp thư mục tạm chung của hệ điều hành hoặc workspace dùng chung. Hàm dọn chỉ được nhận đúng root vừa tạo và thuộc quyền sở hữu của kịch bản.

### Xử lý lỗi khi cleanup

Nếu thao tác test phát sinh exception và cleanup cũng ném exception từ `finally`, exception cleanup có thể **che mất nguyên nhân test ban đầu**. Đó là hành vi của `finally` với exception mới.

Đối với framework hoặc helper nghiêm ngặt, có thể thiết kế để giữ lỗi gốc, rồi gắn lỗi cleanup bằng `Throwable.addSuppressed(...)`. Nếu cleanup thất bại, nên log root tạm và trạng thái còn sót để có thể điều tra; không nên nuốt lỗi hoàn toàn hoặc xóa file ngoài phạm vi nhằm “khắc phục”.

Ví dụ phương án giữ cả hai lỗi (minh họa cấu trúc, không độc lập):

~~~java
Throwable primary = null;
try {
    runTestBody(root);
} catch (Throwable failure) {
    primary = failure;
    throw failure;
} finally {
    try {
        deleteTree(root);
    } catch (IOException cleanupFailure) {
        if (primary != null) {
            primary.addSuppressed(cleanupFailure);
        } else {
            throw cleanupFailure;
        }
    }
}
~~~

Trong thực tế, kiểu `Throwable` và việc ném lại cần phù hợp chữ ký phương thức/cách xử lý checked exception; ví dụ chỉ minh họa ý tưởng giữ thông tin lỗi. `try-with-resources` hỗ trợ suppressed exception tự động cho các resource triển khai `AutoCloseable`, **không áp dụng trực tiếp cho `Path`** vì `Path` không có `close()`.

## 7. Đừng xóa file khi stream hoặc channel vẫn còn mở

Một kịch bản có thể mở `InputStream`, `OutputStream`, `FileChannel`, `RandomAccessFile` hoặc `DirectoryStream`. Cần đóng các resource đó **trước** khi xóa root.

Trên một số hệ điều hành, nhất là Windows, file đang mở có thể không cho phép xóa/đổi tên. Trên một số hệ thống khác, hành vi xóa file đang mở khác; không nên dùng đặc tính của máy hiện tại làm hợp đồng portable.

Cách tổ chức:

~~~java
Path root = Files.createTempDirectory("channel-test-");
try {
    Path file = root.resolve("payload.bin");

    try (var output = Files.newOutputStream(
            file, StandardOpenOption.CREATE_NEW)) {
        output.write(new byte[] { 1, 2, 3 });
    }

    try (var input = Files.newInputStream(file)) {
        int firstByte = input.read();
        if (firstByte != 1) {
            throw new AssertionError("Unexpected byte");
        }
    }
} finally {
    deleteTree(root);
}
~~~

Đây là đoạn minh họa, `deleteTree` đã được định nghĩa ở phần ví dụ độc lập. `try-with-resources` đảm bảo stream được đóng trước khi chạy cleanup. Nếu callback/worker bất đồng bộ còn ghi file, kịch bản còn phải đợi hoặc hủy và xác nhận các công việc đó đã kết thúc trước khi xóa.

## 8. Root tạm và thư mục làm việc: không được nhầm

Ba vị trí có thể có ý nghĩa khác nhau:

- **Current working directory:** thường liên quan `System.getProperty("user.dir")`, ví dụ thư mục khởi chạy Maven/Gradle. Không nên dùng làm nơi test ghi file nếu cần độc lập.
- **Thư mục tạm mặc định:** thường liên quan cấu hình môi trường của JVM/provider (ví dụ thuộc tính `java.io.tmpdir` trên provider mặc định).
- **Thư mục cha do test chọn:** ví dụ một workspace tạm do runner/CI cấp cho test job; gọi `createTempDirectory(parent, prefix)` để đặt scenario root ở đó.

Đừng tự cho rằng việc thay đổi `System.setProperty("java.io.tmpdir", ...)` trong lúc JVM đang chạy sẽ chuyển mọi thao tác `Files.createTempDirectory` sau đó sang nơi khác. Nhà cung cấp file system hoặc JVM có thể lưu cấu hình từ trước; nếu muốn chọn vị trí rõ ràng, **truyền `Path parent` trực tiếp**.

Ví dụ:

~~~java
Path parent = Path.of("/workspace/test-scratch");
Files.createDirectories(parent);
Path scenario = Files.createTempDirectory(parent, "parse-");
~~~

Đây là ví dụ đường dẫn chỉ phù hợp trên hệ thống có `/workspace/test-scratch`. Khi chạy cross-platform, nhận `parent` từ cấu hình build/test hoặc dựng từ `Path` thích hợp; đừng đưa nguyên đường dẫn POSIX vào chương trình dành cho Windows.

### Nên chọn vị trí tạm nào?

Nếu kịch bản cần kiểm tra file I/O thông thường, vùng tạm mặc định thường hợp lý. Nhưng khi kiểm thử những hành vi phụ thuộc file system, vị trí gốc quan trọng:

- **`Files.move(..., ATOMIC_MOVE)`:** có thể cần các đường dẫn trong cùng file system và provider hỗ trợ; không phải mọi hệ thống tệp bảo đảm thao tác này.
- **File locks, quyền file, symlink:** phụ thuộc hệ điều hành, mount option và quyền process.
- **Kiểm thử network file system:** muốn kiểm thử thực tế NFS/SMB thì cần chọn parent nằm trên mount NFS/SMB, không dùng mặc định vùng tạm local rồi suy ra đặc điểm remote.
- **Kích thước/quota:** một vùng tạm có thể có quota nhỏ hoặc nằm trên tmpfs dùng RAM; không giả định nó có không gian vô hạn.

## 9. Thư mục tạm hỗ trợ parallel test như thế nào?

Giả sử runner chạy hai test đồng thời:

~~~text
Thread A:
  rootA = createTempDirectory("case-A-")
  write rootA/input.txt
  assert rootA/output.txt
  cleanup rootA

Thread B:
  rootB = createTempDirectory("case-B-")
  write rootB/input.txt
  assert rootB/output.txt
  cleanup rootB
~~~

Vì `rootA` và `rootB` khác nhau, hai thread có thể dùng cùng tên file tương đối mà không ghi vào cùng path.

Tuy nhiên, muốn **thật sự độc lập**, test cũng không được dựa vào thứ tự thực thi. Ví dụ, test B không được cho rằng test A đã chạy để tạo dữ liệu nào đó. Nếu có fixture dùng chung bắt buộc, hãy chỉ định rõ setup/teardown và đồng bộ theo framework.

Những giới hạn vẫn phải tính:

1. Dung lượng đĩa của thư mục tạm dùng chung có thể bị nhiều test tiêu thụ.
2. Hai test có thể đụng tài nguyên ngoài root như port, database hoặc static state.
3. Một test lỗi có thể để lại file nếu process bị kill trước `finally`.
4. Chạy song song quá nhiều tác vụ I/O có thể gây contention và làm benchmark biến động.
5. Môi trường antivirus, file-indexer hoặc scanner khác có thể gây trễ file operations, nhất là trên CI Windows.

Tạo root riêng là **một lớp cô lập**, không phải sandbox bảo mật giữa những tiến trình không tin cậy.

## 10. Dùng JUnit Jupiter `@TempDir` khi đã có framework

Trong dự án dùng **JUnit Jupiter (JUnit 5)**, extension `@TempDir` cung cấp một thư mục tạm cho test mà bạn không cần tự viết vòng tạo/xóa trong mỗi test.

Ví dụ minh họa, **không thuộc JDK chuẩn**; cần dependency JUnit Jupiter thích hợp:

~~~java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProcessingTest {

    @Test
    void readsGeneratedFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("input.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        assertTrue(Files.exists(file));
        assertEquals("hello",
                Files.readString(file, StandardCharsets.UTF_8));
    }
}
~~~

Khi test runner/extension quản lý root, **không tự xóa vùng root của framework khi một test khác còn được quyền dùng nó**. Phạm vi và chính sách cleanup có thể phụ thuộc phiên bản/cấu hình framework; đọc tài liệu phiên bản thực tế. Mẫu parameter `@TempDir Path tempDir` giúp một test không cần dựa vào tên hard-coded hay working directory.

Đối với **JUnit 4**, một số dự án sử dụng `TemporaryFolder` rule; đó là API của framework khác và có quy tắc vòng đời khác. Không trộn lẫn với JUnit Jupiter chỉ vì tên tương tự.

### Khi nào dùng manual helper thay vì `@TempDir`?

- Đoạn mã chạy như ứng dụng Java độc lập, không phụ thuộc thư viện kiểm thử.
- Cần tạo nhiều root tạm có vòng đời khác nhau bên trong một test.
- Cần chọn parent/mount cụ thể để kiểm tra file system hoặc network share.
- Cần giữ lại artifact khi test thất bại theo chính sách debug riêng.
- Cần kiểm soát kỹ quy trình publish/cleanup trong integration test nhiều process.

Trong trường hợp tiêu chuẩn dùng JUnit Jupiter, `@TempDir` thường ngắn gọn hơn và giảm boilerplate.

## 11. Dữ liệu kiểm thử nhỏ, rõ và xác định

Thư mục tạm cô lập path, nhưng muốn test đáng tin còn phải tạo fixture **deterministic**:

- Dùng UTF-8 được khai báo rõ cho văn bản: `Files.writeString(path, text, StandardCharsets.UTF_8)`.
- Nếu quan trọng với giao thức byte, ghi `byte[]` cụ thể thay vì dùng platform default charset.
- Hạn chế dựa vào timestamp hay thời gian hệ điều hành để xác nhận file đã được xử lý; độ phân giải `lastModifiedTime` có thể khác nhau.
- Không in tên root ngẫu nhiên vào stdout nếu cần assert output cố định; có thể ghi root vào log riêng khi debug.
- Nếu kịch bản cần dữ liệu lớn, nên tạo dữ liệu theo chunk thay vì cấp phát một mảng cỡ file lớn.
- Nếu kiểm thử cách xử lý file đã tồn tại, tạo file theo setup **của chính kịch bản đó** thay vì phụ thuộc dữ liệu cũ từ test trước.
- Tách file `expected` và `actual` trong các thư mục con để tránh test tự ghi đè đáp án.

Ví dụ cấu trúc:

~~~text
scenario-root/
  fixtures/
    input.csv
    expected.json
  actual/
    result.json
  diagnostics/
    process.log
~~~

`expected.json` có thể được tạo từ một literal đã kiểm tra hoặc copy từ test resource **chỉ đọc**, rồi đặt vào root scenario trước khi chạy. Quan trọng là mỗi test có đầy đủ dữ liệu đầu vào và không thay đổi resource gốc dùng chung.

## 12. Xử lý trường hợp test cố tình phát sinh lỗi

Một số kịch bản cần xác minh phản ứng khi:

- File đầu vào thiếu.
- File đầu ra đã tồn tại.
- Tên file không hợp lệ.
- Nội dung file sai encoding.
- Thiếu quyền hoặc ổ đĩa đầy.
- Một luồng xử lý bị interrupt hoặc channel đóng giữa thao tác.

Trong các kịch bản này, **root riêng giúp không làm hỏng môi trường của các test khác**, nhưng không có nghĩa mọi lỗi file system đều mô phỏng được chỉ bằng cách tạo thư mục tạm.

Ví dụ để kiểm tra phương thức phải từ chối ghi đè:

~~~java
Path root = Files.createTempDirectory("collision-");
try {
    Path file = root.resolve("output.txt");
    Files.writeString(file, "already exists");

    boolean rejected = false;
    try {
        Files.newOutputStream(file, StandardOpenOption.CREATE_NEW).close();
    } catch (java.nio.file.FileAlreadyExistsException expected) {
        rejected = true;
    }

    if (!rejected) {
        throw new AssertionError("Expected overwrite protection");
    }
} finally {
    deleteTree(root);
}
~~~

Đây là đoạn minh họa, `deleteTree` đã được định nghĩa ở phần 5 và cần import `Files`, `Path`, `StandardOpenOption`. Nếu muốn test thiếu quyền, không nên giả định `setReadable(false)`/`setWritable(false)` luôn hoạt động giống nhau trên Windows, POSIX, container/root user và network share. Dùng environment phù hợp hoặc abstraction cho file I/O nếu cần một phép thử portable.

## 13. File tạm và bảo mật: cô lập logic không phải sandbox

`Files.createTempDirectory` giúp tránh trùng path nhưng **không cấp phép để xử lý dữ liệu tùy ý từ người dùng như thể nó an toàn**. Cần phân biệt hai tình huống:

**Kiểm thử dữ liệu do chính test sinh ra:** root tạm mặc định thường đủ để tránh ảnh hưởng chéo giữa scenario, miễn cleanup chỉ thao tác trong root đó.

**Giải nén/upload/dữ liệu không tin cậy:** phải có thêm giới hạn dung lượng, count, path traversal, symbolic link, quyền file và kiểm tra file output. Một tên tạm ngẫu nhiên không ngăn tất cả cuộc tấn công vào thư mục chứa.

Cân nhắc:

- **Không dùng đường dẫn nhập từ bên ngoài làm root xóa đệ quy.** Bảo đảm `root` được chính thành phần tạo ra và không bị thay bằng value không tin cậy.
- **Không theo symlink** khi cleanup nếu không có yêu cầu rõ ràng. `walkFileTree` mặc định không theo symlink, giúp giảm nguy cơ đi ra ngoài vùng cleanup.
- **Không chia sẻ root với tiến trình không tin cậy.** Nếu tiến trình khác có thể thay mục trong root khi cleanup, vẫn tồn tại rủi ro race/TOCTOU.
- **Không đưa bí mật vào tên prefix.** Tên thư mục tạm có thể xuất hiện trong log, file listing hoặc báo lỗi.
- **Quyền truy cập:** khi có dữ liệu nhạy cảm, đánh giá permission của root, parent và storage thực tế; sử dụng `FileAttribute` phù hợp provider khi yêu cầu, đồng thời xử lý nền tảng không hỗ trợ attributes.
- **Không dùng `deleteOnExit()` làm cơ chế quản lý chính** của một bộ test khối lượng lớn: danh sách path có thể tồn tại tới khi JVM kết thúc và cleanup không được bảo đảm khi process bị kill.

### Thư mục root và symlink

Nếu root là `/tmp/case-abc` và bên trong có symlink `external -> /home/user/documents`, hành vi `Files.walkFileTree(root, visitor)` **mặc định không duyệt xuyên symlink**; symlink thường được thăm như một file entry để xóa bản thân liên kết. Tuy nhiên, trong môi trường có actor khác sửa cây đồng thời, vẫn phải kiểm soát quyền và tránh mở file theo path không đáng tin sau thời điểm kiểm tra.

## 14. Giữ artifact để debug mà không làm bẩn các test khác

Đôi khi xóa tất cả ngay khi test fail sẽ khiến khó điều tra. Một chính sách thực tế:

1. **Mặc định cleanup luôn**, kể cả khi test thất bại.
2. Khi bật chế độ debug có chủ đích, giữ lại **chỉ root của kịch bản thất bại**, ghi đường dẫn trong log.
3. Đặt thời hạn lưu lại, quota dung lượng và cơ chế dọn định kỳ do runner/quản trị viên kiểm soát.
4. Không để tên root hoặc nội dung log chứa dữ liệu nhạy cảm không cần thiết.
5. Trong CI, khi cần xem artifact, có thể copy output/log đã chọn sang khu vực artifact chính thức rồi dọn root tạm; không phụ thuộc thư mục tạm còn tồn tại sau job.

Một biến cấu hình như `KEEP_TEST_ARTIFACTS` có thể điều khiển chính sách trong helper, nhưng chỉ giữ khi người vận hành chủ động bật. **Không bật mặc định chỉ để dễ debug**, vì các file lớn bị bỏ lại có thể khiến CI hết đĩa.

### Đo giới hạn tài nguyên

Mỗi kịch bản root riêng cũng giúp đo và hạn chế dung lượng dễ hơn: đếm số file tạo ra, tổng byte output, số open handle và thời gian cleanup. Tuy nhiên, phép đếm sau khi test kết thúc không phải bảo vệ chống disk full trong lúc ghi; với đầu vào không tin cậy, quota phải được thực thi **trong vòng ghi/đọc**.

## 15. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Mọi test ghi cùng `./output.txt` | Đè file, test phụ thuộc thứ tự | Root tạm riêng cho từng kịch bản |
| Dùng timestamp làm tên không kiểm tra va chạm | Parallel test đụng tên | `Files.createTempDirectory` |
| `Files.createDirectories(sharedRoot)` rồi coi root là riêng | Dùng lại dữ liệu cũ | Tạo **subdirectory mới** bằng `createTempDirectory` |
| Xóa trực tiếp root còn file | `DirectoryNotEmptyException` | Xóa con trước cha bằng `walkFileTree` |
| Cleanup xóa cả vùng tạm chung | Mất dữ liệu của tác vụ khác | Chỉ xóa root **do mình tạo và sở hữu** |
| Quên `finally` khi test fail | File/tệp tạm tồn đọng | Cleanup trên mọi đường chạy |
| Không đóng stream trước khi cleanup | Xóa thất bại, đặc biệt trên Windows | `try-with-resources` trước cleanup |
| Dùng `deleteOnExit()` cho mọi file | Rò danh sách path đến cuối JVM, không đảm bảo khi bị kill | Cleanup chủ động có quản lý |
| Dùng symlink-following tree walk không kiểm soát | Có thể đi khỏi root tạm | Không bật `FOLLOW_LINKS` khi không cần |
| In tên folder tạm rồi assert stdout y nguyên | Test không xác định | Chỉ assert nội dung/kết quả ổn định |
| Giả định OS/file system hỗ trợ mọi quyền và atomic move | CI khác nền tảng thất bại | Test môi trường thật, xử lý unsupported |
| Chia sẻ root giữa các test parallel có ghi dữ liệu | Race và nondeterminism | Per-test root hoặc cơ chế khóa rõ ràng |
| Cleanup có exception che lỗi test gốc | Khó điều tra nguyên nhân | Suppress/ghi nhận cả lỗi cleanup |
| Test sửa global `java.io.tmpdir` khi JVM đang chạy | Không portable, ảnh hưởng test khác | Truyền `Path parent` vào API |
| Tạo thư mục tạm nhưng vẫn ghi file ngoài root | Không còn cô lập | Truyền root vào mọi thành phần xử lý I/O |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Hai test chạy song song đều ghi `output/result.json`. Làm sao tránh xung đột mà vẫn dùng cùng tên tương đối?

**Đáp án:** Mỗi test gọi `Files.createTempDirectory(...)` để có root riêng, sau đó dùng `root.resolve("output/result.json")`. Tên tương đối giống nhau nhưng đường dẫn đầy đủ khác nhau.

**Câu 2.** `Files.createTempDirectory` trả về `Path`. Khi biến `root` ra khỏi scope thì thư mục có tự bị xóa không?

**Đáp án:** Không. `Path` không phải `AutoCloseable` và không tự xóa. Dùng `finally`, helper hoặc framework như JUnit Jupiter `@TempDir` để quản lý cleanup.

**Câu 3.** Tại sao `Files.delete(root)` có thể thất bại dù root do test tạo?

**Đáp án:** Vì root còn file hoặc thư mục con. Cần duyệt và xóa thành phần con trước khi xóa root.

**Câu 4.** Có thể dùng một parent chung cho nhiều test không?

**Đáp án:** Có, nếu mỗi test tạo **một root con mới** bằng `createTempDirectory(parent, prefix)`, không dùng chính parent đó làm nơi ghi/xóa bừa bãi.

**Câu 5.** Đóng `FileChannel` liên quan gì tới cleanup?

**Đáp án:** Channel đang mở có thể giữ handle/lock khiến file không xóa được trên một số hệ điều hành. Đóng bằng `try-with-resources` trước khi xóa cây.

**Câu 6.** Một kịch bản cố ý gây lỗi parse và ném exception. Thư mục tạm có nên vẫn được dọn?

**Đáp án:** Có theo chính sách mặc định. `finally` sẽ chạy khi method thoát vì exception thông thường; nếu muốn giữ để debug thì cần chế độ riêng có quota và dọn sau.

**Câu 7.** `@TempDir` là annotation của Java SE 17?

**Đáp án:** Không. Đó là cơ chế hỗ trợ bởi **JUnit Jupiter** và cần framework/dependency tương ứng. `Files.createTempDirectory` mới thuộc Java SE.

**Câu 8.** Tạo root tạm mới có bảo đảm chống mọi cuộc tấn công path traversal từ ZIP do người dùng gửi không?

**Đáp án:** Không. Vẫn cần kiểm tra entry path, xử lý symlink, quyền, dung lượng, concurrency và chính sách giải nén an toàn.

## 17. Ghi nhớ

- **Mỗi kịch bản I/O có một root tạm riêng** giúp giảm nhiễm bẩn dữ liệu, test phụ thuộc thứ tự và xung đột khi chạy song song.
- **`Files.createTempDirectory` tạo mới; `Path` không tự cleanup.** Người tạo root chịu trách nhiệm dọn.
- **Dọn con trước cha** bằng `Files.walkFileTree`, không theo symlink nếu không có nhu cầu; đóng stream/channel trước khi dọn.
- **Truyền root vào mã cần test** thay vì ghi vào working directory hoặc thay đổi cấu hình tạm toàn JVM.
- **Framework `@TempDir`** giúp tự động hóa trong JUnit Jupiter; với chương trình Java độc lập dùng helper và `try/finally`.
- **Cô lập root là nền tảng của test đáng tin**, nhưng không thay thế quota, bảo mật path, kiểm soát tài nguyên ngoài file system và test trên môi trường thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-003">Kiểm tra nội dung byte chính xác và round-trip văn bản</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra nội dung byte chính xác và round-trip văn bản trong Java I/O

Khi kiểm thử thao tác đọc/ghi file, copy stream, encode/decode text hoặc giao tiếp qua socket, thông báo “không có exception” **chưa đủ chứng minh dữ liệu đúng**. Một tác vụ I/O có thể hoàn thành nhưng làm thiếu byte cuối, thêm BOM, đổi `\n` thành `\r\n`, dùng sai charset, thay ký tự không biểu diễn được hoặc vô tình chuẩn hóa văn bản. Những lỗi ấy có thể không dễ nhìn thấy khi chỉ mở file trong editor.

Bài học này phân biệt **kiểm tra byte-for-byte** và **kiểm tra round-trip văn bản**; hướng dẫn thiết kế assertions, golden fixtures, diagnostics bằng hexadecimal và offset sai lệch, sử dụng `Arrays.equals`, `Arrays.mismatch`, `Files.mismatch`, `HexFormat`, `StandardCharsets` và bộ `CharsetDecoder/CharsetEncoder` trong Java 17+. Mục tiêu không chỉ là kiểm tra “đọc lại được”, mà còn biết **đang chứng minh tính chất nào, có thể mất thông tin ở đâu và kiểm tra sai lệch ra sao**.

## 1. Ba yêu cầu đúng đắn thường bị nhầm lẫn

Hãy phân biệt ba câu hỏi:

| Yêu cầu | Điều phải so sánh | Ví dụ |
| --- | --- | --- |
| **Byte exactness** | Toàn bộ dãy byte nguồn và dãy byte đích phải giống nhau từng byte, cùng thứ tự, cùng độ dài. | Copy file nhị phân, chuyển bytes từ mạng vào file, lưu artifact bất biến. |
| **Text round-trip** | Văn bản `String` ban đầu, sau khi encode → write → read → decode, phải giống văn bản ban đầu theo **tiêu chí so sánh đã chọn**. | Lưu rồi đọc lại văn bản UTF-8. |
| **Logical equivalence** | Hai đối tượng/text có cùng ý nghĩa theo schema hoặc quy tắc canonicalization, dù raw bytes có thể khác. | JSON khác spacing, Unicode NFC/NFD, file có LF/CRLF theo policy. |

Ba tính chất không tự suy ra nhau. Một văn bản khi đọc có thể “nhìn đúng” nhưng byte khác; ngược lại, hai file giống byte hoàn toàn không chứng minh nội dung là UTF-8 hợp lệ hay đúng schema nghiệp vụ.

Sơ đồ:

```text
String ban đầu
      |
      | encode(StandardCharsets.UTF_8)
      v
Bytes dự kiến ------ so sánh byte-for-byte ------> Bytes đọc từ file
      |                                               |
      | write                                         | decode(UTF-8)
      v                                               v
   File / stream                                String đọc lại
                                                      |
                                             String.equals(original)
```

**Quy tắc thiết kế test:** trước khi viết assertion, phải quyết định cần **byte exactness**, **text equality** hay **logical equivalence**. Không nên thay một tiêu chí bằng tiêu chí yếu hơn chỉ vì dễ kiểm thử.

## 2. Byte-for-byte nghĩa là gì?

Cho hai dãy byte `A` và `B`, bằng nhau từng byte khi:

1. `A.length == B.length`; và
2. Với mọi index `i` từ 0 đến length - 1, `A[i] == B[i]`.

Các khác biệt dù chỉ một byte đều phải làm test thất bại:

```text
Expected:  41 42 43 0a          "ABC\n" UTF-8
Actual:    41 42 43 0d 0a       "ABC\r\n" UTF-8
                     ^
                Từ index 3 đã khác.
```

Đừng kiểm thử copy nhị phân bằng `String.equals` hoặc `Files.readString`: file PNG, ZIP, protobuf hoặc ciphertext không phải chuỗi ký tự. Việc decode bytes nhị phân rồi encode lại có thể làm thay đổi dữ liệu, thậm chí khi không ném exception.

Với dữ liệu nhỏ trong test:

```java
byte[] expected = {0x00, 0x01, (byte) 0xFF};
byte[] actual   = Files.readAllBytes(path);

if (!Arrays.equals(expected, actual)) {
    throw new AssertionError("Bytes differ");
}
```

Đây là **snippet** với `path` được khai báo ngoài, cần import `java.nio.file.Files`, `java.util.Arrays`. Ép `(byte) 0xFF` vì Java `byte` là kiểu signed -128..127, nhưng biểu diễn trên file vẫn là byte unsigned `FF`.

Một lỗi dễ mắc: `Arrays.toString(byte[])` hiển thị số signed, ví dụ byte `FF` thành `-1`. Khi cần phân tích ở cấp byte, in hexadecimal bằng `HexFormat` hoặc dùng `Byte.toUnsignedInt(byte)`.

## 3. Arrays.equals, Arrays.mismatch và Files.mismatch

Ba API giúp kiểm tra nhanh:

| API | Input | Output | Khi phù hợp |
| --- | --- | --- | --- |
| `Arrays.equals(byte[], byte[])` | Hai mảng trong RAM | `boolean` | Fixture nhỏ và dữ liệu đã được đọc đầy đủ. |
| `Arrays.mismatch(byte[], byte[])` | Hai mảng trong RAM | Index khác đầu tiên, hoặc `-1` nếu giống nhau | Chẩn đoán vị trí sai; nếu một mảng là prefix của mảng khác, index là độ dài mảng ngắn hơn. |
| `Files.mismatch(Path, Path)` | Hai file | Offset byte đầu tiên khác, hoặc `-1L` nếu giống nhau | So sánh file trực tiếp, tránh tự viết vòng readAllBytes cho file lớn. |

Ví dụ:

```java
byte[] expected = {1, 2, 3, 4};
byte[] actual = {1, 2, 9, 4};

System.out.println(Arrays.equals(expected, actual));  // false
System.out.println(Arrays.mismatch(expected, actual)); // 2
```

Đây là snippet. `Arrays.mismatch` và `Files.mismatch` có từ các bản JDK trước Java 17 và sử dụng được trong Java 17+. `Files.mismatch` có thể ném `IOException`; nếu file đang bị process khác sửa trong lúc kiểm tra, kết quả có thể không phản ánh một snapshot ổn định.

**Chú ý riêng `Files.mismatch`:** trả `-1L` khi nội dung byte giống nhau; trả offset **0-based** khi khác. Với file chỉ khác phần đuôi, kết quả có thể bằng vị trí ngay sau byte cuối của file ngắn hơn.

Đừng chỉ so `Files.size(source) == Files.size(target)`: hai file khác nội dung vẫn có thể cùng số byte. Đừng chỉ so timestamp hoặc tên file. Để chứng minh toàn bộ byte giống nhau, dùng so sánh bytes thực, hoặc digest của nguồn và đích phù hợp threat model.

## 4. Diagnostics bằng hex, offset và context

Nếu một bài kiểm thử chỉ báo `expected != actual`, việc debug sẽ khó khi file dài vài MB. Nên báo:

- Đường dẫn hoặc ID file (đã được lọc để không lộ thông tin nhạy cảm).
- Tổng độ dài expected/actual.
- **Offset byte đầu tiên sai**, tính từ 0.
- Một cửa sổ hex xung quanh offset, ví dụ 16 byte trước và sau.
- Charset/encoding, newline và BOM policy liên quan.
- Chú thích rằng dump là byte gốc, không phải ký tự “trông giống”.

Java 17 có `HexFormat`:

```java
byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
System.out.println(HexFormat.of().formatHex(bytes)); // efbbbf
byte[] reconstructed = HexFormat.of().parseHex("efbbbf");
```

Snippet cần import `java.util.HexFormat`. Với dữ liệu nhạy cảm (token, private key, thông tin cá nhân), **không** log nguyên văn hex dump chỉ để debug: hex vẫn tiết lộ dữ liệu. Log offset, chiều dài và digest không bí mật theo chính sách; chỉ dump bytes nếu được phép và có cơ chế redact.

Ví dụ helper đưa ra lỗi có cấu trúc:

```java
static void assertExact(byte[] expected, byte[] actual) {
    int mismatch = Arrays.mismatch(expected, actual);
    if (mismatch >= 0) {
        throw new AssertionError(
                "Byte mismatch at offset=" + mismatch
                + "; expectedLength=" + expected.length
                + "; actualLength=" + actual.length);
    }
}
```

Đây là snippet trong class có import `java.util.Arrays`. Trong các bài test, helper này có thể được thay bằng `assertArrayEquals` của JUnit 5 và bổ sung hex context khi một case thất bại.

## 5. Charset phải được chỉ định tường minh

Khi chuyển giữa `String` và bytes, charset là một phần của hợp đồng dữ liệu:

```java
String original = "Tiếng Việt";
byte[] data = original.getBytes(StandardCharsets.UTF_8);
String restored = new String(data, StandardCharsets.UTF_8);

if (!original.equals(restored)) {
    throw new AssertionError("Text round-trip failed");
}
```

Đây là snippet; `StandardCharsets.UTF_8` cung cấp charset cụ thể. Tránh các overload như `str.getBytes()` hoặc `new String(bytes)` khi định dạng dữ liệu cần tồn tại lâu hoặc di chuyển giữa máy: charset mặc định phụ thuộc môi trường/JDK. Đặc biệt với Java 17 và những bản JDK trước thay đổi mặc định charset của Java 18, cùng code không chỉ định charset có thể xử lý bytes khác trên các máy.

Một số charset phổ biến:

| Charset | Hợp đồng | Rủi ro khi dùng sai |
| --- | --- | --- |
| `StandardCharsets.UTF_8` | Unicode biến độ dài, là lựa chọn mặc định phù hợp với dữ liệu text mới. | Dùng charset khác để decode sẽ gây mojibake hoặc lỗi. |
| `StandardCharsets.US_ASCII` | Chỉ mã hóa tập ASCII cơ bản. | Ký tự `đ`, `é`, emoji không biểu diễn được. |
| `StandardCharsets.ISO_8859_1` | Một byte cho các giá trị 0–255 của bảng mã đó. | Không chứa đầy đủ tiếng Việt hoặc emoji. |
| `UTF_16LE`/`UTF_16BE` | Mã hóa các UTF-16 code units theo thứ tự byte rõ ràng. | Dùng sai endian hoặc sai BOM protocol dẫn tới văn bản sai. |

**Không thể kết luận byte exactness chỉ từ** `String.equals`. Nếu code đã decode sai nhưng theo chính sách thay invalid bytes bằng ký tự thay thế, một phép so chuỗi vẫn không tìm lại được bytes gốc.

## 6. “Round-trip văn bản” có hai chiều cần kiểm tra riêng

### Chiều A: String → encode → decode → String

Với một chuỗi Java hợp lệ, charset có thể biểu diễn đầy đủ dữ liệu và không có bước biến đổi, ta mong đợi:

```text
originalText == decode(encode(originalText, charset), charset)
```

Với UTF-8 và dữ liệu Unicode hợp lệ, thao tác này thường bảo toàn chính xác chuỗi. Tuy nhiên, chuỗi Java có thể chứa **surrogate lẻ** (unpaired surrogate), vốn không phải Unicode scalar value hợp lệ; nếu dùng encoder có replacement policy, round-trip **có thể mất thông tin**. Hãy chọn chế độ báo lỗi khi format đòi strict encoding.

### Chiều B: raw bytes → decode → encode → raw bytes

Chiều này mạnh hơn:

```text
originalBytes == encode(decode(originalBytes, charset), charset)
```

Nó chỉ đúng khi byte sequence hợp lệ và phép giải mã/tái mã hóa theo protocol cho một biểu diễn byte tương ứng. Với invalid UTF-8, encoding lỗi, charset có nhiều biểu diễn, chính sách replacement hay canonicalization, không có bảo đảm raw bytes được tái tạo.

**Kiểm thử hai chiều khi thực sự cần cả hai**. Với file nhị phân, không có yêu cầu decode text thì chỉ so bytes. Với tài liệu chuẩn hóa theo policy, phải xác định tính tương đương logic thay vì đòi byte exactness.

## 7. Ví dụ Java 17+ chạy độc lập: file bytes, UTF-8 round-trip và các bẫy

Chương trình tự tạo file tạm, ghi văn bản có tiếng Việt, emoji và dấu newline. Nó copy file, kiểm tra bytes bằng `Arrays.equals` và `Files.mismatch`, sau đó đọc text rồi encode lại. Ví dụ cũng xác minh byte UTF-8 của `é`, so Unicode normalization, line ending và cơ chế từ chối UTF-8 lỗi.

Lưu đúng tên **`ByteExactRoundTripDemo.java`**:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HexFormat;

public class ByteExactRoundTripDemo {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws IOException {
        Path source = Files.createTempFile("roundtrip-source-", ".txt");
        Path copy = Files.createTempFile("roundtrip-copy-", ".txt");
        try {
            String text = "Tiếng Việt 😀\nDòng 2";
            byte[] expected = text.getBytes(StandardCharsets.UTF_8);

            Files.write(source, expected);
            Files.copy(source, copy,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            byte[] actual = Files.readAllBytes(copy);

            check(Arrays.equals(expected, actual), "Bytes changed");
            check(Files.mismatch(source, copy) == -1, "Files differ");

            String decoded = Files.readString(copy, StandardCharsets.UTF_8);
            check(text.equals(decoded), "Text changed");
            check(Arrays.equals(expected,
                    decoded.getBytes(StandardCharsets.UTF_8)),
                    "Re-encoding changed bytes");

            System.out.println("bytes-equal=true");
            System.out.println("files-mismatch=-1");
            System.out.println("text-roundtrip=true");
            System.out.println("reencode-equal=true");
            System.out.println("utf8-accent=" + HexFormat.of().formatHex(
                    "é".getBytes(StandardCharsets.UTF_8)));

            String composed = "café";
            String decomposed = "cafe\u0301";
            System.out.println("nfc-nfd-byte-equal=" + Arrays.equals(
                    composed.getBytes(StandardCharsets.UTF_8),
                    decomposed.getBytes(StandardCharsets.UTF_8)));
            System.out.println("nfc-nfd-normalized-equal=" +
                    Normalizer.normalize(composed, Normalizer.Form.NFC)
                            .equals(Normalizer.normalize(
                                    decomposed, Normalizer.Form.NFC)));

            System.out.println("lf-crlf-byte-equal=" + Arrays.equals(
                    "A\nB".getBytes(StandardCharsets.UTF_8),
                    "A\r\nB".getBytes(StandardCharsets.UTF_8)));

            boolean rejected = false;
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(
                                new byte[] {(byte) 0xC3, (byte) 0x28}));
            } catch (CharacterCodingException expectedError) {
                rejected = true;
            }
            check(rejected, "Malformed UTF-8 was accepted");
            System.out.println("malformed-rejected=true");
        } finally {
            Files.deleteIfExists(copy);
            Files.deleteIfExists(source);
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 ByteExactRoundTripDemo.java
java ByteExactRoundTripDemo
```

Stdout đã được kiểm chứng bằng cách biên dịch và thực thi:

```text
bytes-equal=true
files-mismatch=-1
text-roundtrip=true
reencode-equal=true
utf8-accent=c3a9
nfc-nfd-byte-equal=false
nfc-nfd-normalized-equal=true
lf-crlf-byte-equal=false
malformed-rejected=true
```

**Giải thích từng bước:**

1. `text` chứa tiếng Việt và emoji ngoài BMP; `text.getBytes(UTF_8)` tạo chuỗi bytes theo mã hóa tường minh.
2. `Files.write(source, expected)` ghi bytes gốc; `Files.copy` sao chép sang file tạm khác. `Arrays.equals` so mảng bytes; `Files.mismatch == -1` xác nhận cả hai file có byte content giống nhau.
3. `Files.readString(copy, UTF_8)` đọc văn bản. `String.equals` kiểm tra sequence UTF-16 code units; phép encode lại kiểm tra được cùng bytes trong trường hợp UTF-8 hợp lệ này.
4. `"é"` theo UTF-8 là `C3 A9`; `HexFormat` cho `c3a9`.
5. `"café"` dùng ký tự `é` dựng sẵn, còn `"cafe\u0301"` dùng `e` + combining acute accent. Bytes khác, nhưng sau `Normalizer.normalize(..., NFC)` chúng so bằng nhau.
6. `"A\nB"` và `"A\r\nB"` khác bytes: `0A` so với `0D 0A`. Editor có thể hiển thị hai tệp như nhau, nên text rendering không đủ chứng minh byte equality.
7. Dãy byte `C3 28` không phải UTF-8 hợp lệ. `CharsetDecoder` với `CodingErrorAction.REPORT` báo lỗi để test phát hiện thay vì âm thầm thay bằng `U+FFFD`.
8. `finally` dọn hai file tạm. Demo dùng dữ liệu nhỏ; với file rất lớn nên tránh `Files.readAllBytes` để giới hạn memory.

**Bài học quan trọng:** ví dụ chứng minh **ba mệnh đề độc lập**: copy bytes nguyên vẹn, text round-trip đúng khi charset phù hợp và decoder strict từ chối input không hợp lệ. Không dùng kết quả một mệnh đề để thay assertion cho mệnh đề khác.

## 8. Kiểm tra bytes sau FileChannel hoặc stream có partial I/O

Với `InputStream.read(buffer)`, giá trị trả về là số byte **thực sự được đọc** hoặc `-1` ở EOF. Không giả định một lần `read` sẽ đổ đầy mảng, đặc biệt khi đọc socket, pipe hoặc dữ liệu dài.

Khi copy theo chunk, nguyên tắc:

```java
// Snippet: in và out là InputStream/OutputStream đã mở.
byte[] buf = new byte[8192];
for (int n; (n = in.read(buf)) != -1; ) {
    out.write(buf, 0, n);
}
```

Đoạn trên là snippet, cần xử lý `IOException` và `try-with-resources` ở caller. Với `InputStream` thông thường, `read(byte[])` có thể trả ít byte hơn `buf.length`; dùng `out.write(buf)` thay vì `out.write(buf,0,n)` sẽ ghi thêm các bytes cũ ở cuối mảng.

Với `FileChannel` và `ByteBuffer`:

```java
// Snippet: source và target là FileChannel,
// buffer có dung lượng > 0.
while (true) {
    int n = source.read(buffer);
    if (n == -1) {
        break;
    }
    if (n == 0) {
        throw new IOException("Reading made no progress");
    }
    buffer.flip();
    while (buffer.hasRemaining()) {
        if (target.write(buffer) == 0) {
            throw new IOException("Writing made no progress");
        }
    }
    buffer.clear();
}
```

Đây là mẫu fail-fast cho file channel blocking trong ngữ cảnh do ứng dụng kiểm soát; **không** bê nguyên chính sách `n==0` sang `SocketChannel` non-blocking, nơi cần chờ readiness thay vì ném lỗi. Kiểm thử sau copy bằng `Files.mismatch` hoặc digest đáng tin có ý nghĩa thực tế hơn chỉ kiểm tra số byte đã gọi `write`.

Nếu code có dùng `ByteBuffer.array()`, chú ý mảng backing có thể lớn hơn vùng dữ liệu hợp lệ, buffer có thể là direct/read-only/sliced và không hỗ trợ `array()`. Dùng `position/limit/remaining` hoặc view đúng vùng, không giả định toàn bộ mảng luôn là payload.

## 9. Ký tự, code point, UTF-16 code units và số byte

Java `String` là chuỗi **UTF-16 code units**. Vì vậy:

- `String.length()` trả số code units, không phải số Unicode code points và không phải số byte UTF-8.
- Ký tự supplementary, ví dụ `😀`, chiếm **hai UTF-16 code units** và thường **bốn byte UTF-8**.
- Ký tự `é` dựng sẵn thường dài một code unit và hai byte UTF-8.
- Ký tự kết hợp có thể tạo một glyph từ nhiều code points/code units; số glyph hiển thị không bằng `String.length()`.

Ví dụ minh họa:

```java
String emoji = "😀";
System.out.println(emoji.length()); // 2 UTF-16 code units
System.out.println(emoji.codePointCount(0, emoji.length())); // 1
System.out.println(emoji.getBytes(StandardCharsets.UTF_8).length); // 4
```

Đây là snippet. Nếu protocol yêu cầu header ghi độ dài payload **theo byte**, dùng `data.length` của byte array đã encode, không dùng `str.length()`. Trong binary protocol, dùng byte count không đúng dẫn tới thiếu payload hoặc đọc quá phần dữ liệu tiếp theo.

Nếu muốn duyệt Unicode code points, cân nhắc `String.codePoints()`, nhưng ngay cả code point count cũng không bằng số grapheme clusters người dùng nhìn thấy. Với so sánh byte exactness, đơn vị phù hợp vẫn là **byte sequence**.

## 10. Unicode normalization: “nhìn như nhau” chưa chắc String.equals

Ví dụ biểu diễn chữ có dấu:

```text
NFC / composed: "é"           U+00E9        UTF-8 C3 A9
NFD / decomposed: "e◌́"        U+0065 U+0301 UTF-8 65 CC 81
```

Hai chuỗi có thể hiện giống nhau trong UI nhưng bytes khác, và Java `String.equals` cũng thường trả false vì các UTF-16 code units khác nhau. Nếu nghiệp vụ yêu cầu coi chúng tương đương theo Unicode canonical equivalence, cần **quy tắc normalize** rõ:

```java
String left = Normalizer.normalize(sourceText, Normalizer.Form.NFC);
String right = Normalizer.normalize(restoredText, Normalizer.Form.NFC);
boolean sameMeaningByPolicy = left.equals(right);
```

Đây là snippet với imports `java.text.Normalizer`, các biến string do ứng dụng cung cấp. Nên dùng **NFC** khi sản phẩm cần canonical normalization, nhưng việc normalize không phải luôn vô hại: nếu đang kiểm tra file raw, chữ ký số, protocol bytes, đường dẫn hoặc dữ liệu đòi byte-exact thì normalization thay đổi byte và **không được tự thực hiện**.

Các form `NFC`, `NFD`, `NFKC`, `NFKD` có mục đích khác nhau; compatibility normalization như NFKC có thể biến một số ký tự tương thích thành chuỗi khác về biểu diễn/ý nghĩa thị giác. Chọn theo hợp đồng, không dùng mặc định để “sửa lỗi encoding”.

## 11. Line endings và các API làm mất ranh giới dòng

Ba dạng line ending thường gặp:

| Dạng | Hex | Ứng dụng |
| --- | --- | --- |
| LF | `0A` | Thường dùng trong môi trường Unix/Linux và nhiều giao thức. |
| CRLF | `0D 0A` | Phổ biến trên Windows và một số protocol mạng. |
| CR | `0D` | Tồn tại trong dữ liệu cũ hoặc một số định dạng riêng. |

`BufferedReader.readLine()` trả nội dung dòng **không kèm ký tự kết thúc dòng**. Vì vậy việc đọc nhiều dòng bằng `readLine()` rồi ghi ra bằng `writer.newLine()` có thể thay đổi bytes của file gốc:

- File có `\n` nhưng máy đích tạo `\r\n`.
- File có hoặc không có newline ở cuối: phân biệt này có thể bị mất.
- Hai kiểu newline trộn lẫn trong cùng file không được bảo toàn.
- Các ranh giới CR/LF riêng có thể bị xử lý theo chính sách của reader.

Tương tự, các API dòng cao cấp như `Files.readAllLines` và `Files.lines` thích hợp cho xử lý **nội dung từng dòng**, nhưng không phải công cụ kiểm tra bảo toàn dấu ngắt dòng byte-for-byte.

Khi muốn giữ nguyên file, xử lý qua bytes hoặc bảo toàn chính xác các line separators. Khi nghiệp vụ muốn chuẩn hóa line ending, phải viết test riêng chứng minh **kết quả đã chuẩn hóa** và ghi rõ đây là chuyển đổi có chủ ý, không gọi đó là raw byte round-trip.

## 12. BOM và các byte vô hình ở đầu file

Một số text file có **Byte Order Mark (BOM)**. Ví dụ UTF-8 BOM là ba byte:

```text
EF BB BF
```

UTF-8 không cần BOM để xác định thứ tự byte; tuy nhiên một số phần mềm vẫn tạo file UTF-8 có BOM. Hai file có cùng nội dung ký tự hiển thị nhưng một file có BOM còn file kia không có **sẽ khác SHA-256, kích thước và byte exactness**.

Các điểm phải xác định:

1. Protocol có cho phép BOM không?
2. Parser có tự nhận diện/bỏ BOM không, hay coi nó là một code point `U+FEFF`?
3. Writer có luôn thêm BOM, luôn không thêm BOM, hay sao chép nguyên trạng?
4. UTF-16 sử dụng endian nào; có BOM hay đang chỉ định `UTF_16LE`/`UTF_16BE` rõ?

Nếu yêu cầu byte exactness, **BOM là dữ liệu cần kiểm tra như mọi byte khác**. Nếu yêu cầu semantic text round-trip, hai bên phải thống nhất quy tắc BOM trước khi so chuỗi. Không thêm hoặc bỏ BOM âm thầm chỉ vì editor của một hệ điều hành xử lý khác.

## 13. Strict decoding và lỗi dữ liệu không hợp lệ

Một số convenience API xử lý input malformed bằng **ký tự thay thế**, dẫn tới mất thông tin. Khi định dạng yêu cầu UTF-8 chính xác, nên dùng decoder có `CodingErrorAction.REPORT`:

```java
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try {
    CharBuffer result = decoder.decode(ByteBuffer.wrap(bytes));
    String restored = result.toString();
} catch (CharacterCodingException invalid) {
    throw new IOException("Malformed UTF-8 input", invalid);
}
```

Đây là snippet, cần imports `java.nio.ByteBuffer`, `java.nio.CharBuffer`, `java.nio.charset.CharsetDecoder`, `java.nio.charset.CodingErrorAction`, `java.nio.charset.CharacterCodingException` và các biến phù hợp.

**Malformed** thường là chuỗi byte sai cấu trúc encoding; **unmappable** là dữ liệu không có biểu diễn ký tự hợp lệ theo charset. Với UTF-8, một ví dụ malformed là `C3 28`: byte `C3` yêu cầu byte continuation phù hợp nhưng byte kế là `28`.

Tương tự khi encode text sang charset không đủ ký tự:

```java
CharsetEncoder strictAscii = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
try {
    ByteBuffer encoded = strictAscii.encode(CharBuffer.wrap("Tiếng Việt"));
} catch (CharacterCodingException unsupported) {
    // Dữ liệu có ký tự không biểu diễn được trong ASCII.
}
```

Đây là snippet. `String.getBytes(Charset)` có thể thay ký tự không biểu diễn được/chuỗi sai bằng bytes replacement theo API, nên để **phát hiện dữ liệu lỗi** thay vì thay thế âm thầm hãy dùng `CharsetEncoder` strict như trên. Tránh vô tình “test passed” vì cả phía ghi lẫn đọc đã cùng dùng chính sách thay thế mất mát.

## 14. Golden fixtures và các trường hợp kiểm thử bắt buộc

Test tốt không chỉ round-trip cùng một hàm encode/decode; nếu cả hai đều mắc cùng một lỗi, chúng có thể tự xác nhận sai. Cần có **golden bytes độc lập** với code đang được kiểm thử.

Bộ fixture khuyến nghị:

| Fixture | Byte đặc trưng hoặc nội dung | Điều cần bắt lỗi |
| --- | --- | --- |
| Empty file | Không byte nào. | Không thêm BOM/newline/header ngoài dự kiến. |
| ASCII ngắn | `abc` = `61 62 63` | Dữ liệu đơn giản và content length. |
| Byte null | `00` nằm giữa payload. | Không coi null là kết thúc chuỗi C-style. |
| Byte cao | `80 FF` | Signed byte, không decode text nhầm. |
| UTF-8 dấu tiếng Việt | `đ, ế, ộ` | Charset sai / replacement. |
| Emoji supplementary | `😀` | Surrogate pairs và byte count. |
| NFC so NFD | `C3 A9` so `65 CC 81` | Visual equality không đồng nghĩa byte equality. |
| LF và CRLF | `0A` so `0D 0A` | Line ending rewrite. |
| Có/không BOM | `EF BB BF` trước text | BOM policy. |
| Malformed UTF-8 | `C3 28` hoặc truncated multibyte | Strict decoder phải từ chối. |
| Unpaired UTF-16 surrogate | Ví dụ một high surrogate không có low surrogate | Strict encoder phải từ chối. |
| Nhiều chunk | Dữ liệu dài hơn buffer, chunk cuối ngắn | Partial reads và stale bytes. |
| File nhị phân thật | PNG/ZIP fixture nhỏ đã xác minh | Không dùng String làm trung gian. |
| File lớn | Nhiều MiB/GB theo hạ tầng test | Streaming, memory, timeout, chính sách bảo vệ. |

Đối với fixture tính bằng hex, tạo bytes kỳ vọng bằng `HexFormat.parseHex` từ một chuỗi đã được review hoặc chuẩn protocol. Tránh tạo expected bằng chính API encode bạn đang kiểm thử rồi tuyên bố đã kiểm tra encoding đúng.

## 15. Ví dụ JUnit 5: assertions chính xác (snippet phụ thuộc JUnit)

Trong một dự án đã cài JUnit Jupiter, có thể sử dụng:

```java
import static org.junit.jupiter.api.Assertions.*;

byte[] expected = HexFormat.of().parseHex("61c3a90a");
byte[] actual = "aé\n".getBytes(StandardCharsets.UTF_8);
assertArrayEquals(expected, actual);

String text = "Tiếng Việt 😀";
byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
assertEquals(text, new String(encoded, StandardCharsets.UTF_8));
```

Đây là **snippet cần JUnit 5**, không phải chương trình standalone. Nó giả định imports `HexFormat` và `StandardCharsets`, chạy trong test method có dependency `org.junit.jupiter:junit-jupiter` phù hợp.

Để chẩn đoán sai lệch:

```java
int offset = Arrays.mismatch(expected, actual);
assertEquals(-1, offset,
        () -> "Mismatch at byte index " + offset
                + "; expectedHex=" + HexFormat.of().formatHex(expected)
                + "; actualHex=" + HexFormat.of().formatHex(actual));
```

Đây là snippet test khác; không in payload nhạy cảm trong message. JUnit `assertArrayEquals` thường thuận tiện cho array equality, còn `mismatch` hữu ích nếu bạn cần báo đúng offset.

Với file lớn, tránh đưa cả file vào assertion message. Tách integration test so bằng `Files.mismatch`, lưu kích thước, offset đầu tiên sai và log context có giới hạn.

## 16. Round-trip với Reader/Writer và CharsetDecoder

Nếu ứng dụng thật sự đọc và ghi **văn bản**, các API `Reader/Writer` phù hợp vì chúng làm việc với ký tự thay vì bytes. Nhưng test phải hiểu rằng đọc text rồi ghi lại sẽ **encode dữ liệu một lần nữa**:

```java
// Snippet: source, target là Paths đã kiểm soát.
try (BufferedReader reader = Files.newBufferedReader(
         source, StandardCharsets.UTF_8);
     BufferedWriter writer = Files.newBufferedWriter(
         target, StandardCharsets.UTF_8)) {
    char[] chars = new char[4096];
    int n;
    while ((n = reader.read(chars)) != -1) {
        writer.write(chars, 0, n);
    }
}
```

Đây là snippet, cần imports `BufferedReader`, `BufferedWriter`, `Files`, `StandardCharsets` và xử lý IOException. Lưu ý khác biệt giữa copy **từng mảng char** và copy **từng dòng**: đọc/ghi char liên tục có thể giữ các ký tự newline như đã giải mã, trong khi `readLine/newLine` có thể thay đổi chúng.

Nhưng việc dùng `Reader/Writer` vẫn **không** tự bảo đảm byte-exact: decoder/encoder có thể xử lý BOM, malformed input, replacement, charset hoặc canonicalization khác nhau. Nếu bytes gốc là dữ liệu chuẩn cần giữ y nguyên, copy ở tầng byte trước, rồi kiểm tra bằng file mismatch.

Với stream mã hóa dài, `CharsetDecoder.decode(ByteBuffer, CharBuffer, boolean)` ở cấp thấp có cơ chế incremental state để xử lý ký tự bị cắt giữa hai chunk; đừng tạo `new String(chunk, UTF_8)` độc lập cho mỗi phần bytes nhận từ socket vì một ký tự multibyte có thể bị tách giữa các chunk.

## 17. Chẩn đoán Unicode bị cắt ngang các chunk

UTF-8 mã hóa `é` là:

```text
Chunk 1: C3
Chunk 2: A9
Toàn stream: C3 A9  -> U+00E9
```

Nếu dùng:

```java
String a = new String(new byte[] {(byte) 0xC3},
        StandardCharsets.UTF_8);
String b = new String(new byte[] {(byte) 0xA9},
        StandardCharsets.UTF_8);
String result = a + b;
```

đó **không** phải giải mã đúng toàn bộ sequence `C3 A9`. Từng chunk riêng không hợp lệ và decoder convenience có thể tạo replacement characters.

Cách đúng:

- Với dữ liệu ít, **tích lũy bytes** cho đến khi có toàn bộ message rồi decode một lần.
- Với stream lớn, dùng `InputStreamReader`/`BufferedReader` đọc từ stream liên tục để decoder duy trì trạng thái qua ranh giới buffer nội bộ.
- Hoặc quản lý `CharsetDecoder` incremental với `CoderResult`, trạng thái `endOfInput`, `flush()` và buffer có `compact()` khi còn bytes UTF-8 dang dở.

Nếu parser protocol xác định frame theo **byte length**, trước hết xác định bytes của frame đã đủ rồi mới decode; không cộng `String.length()` làm số bytes trong header.

## 18. Xử lý file lớn mà không chiếm RAM vô hạn

Các convenience APIs như `Files.readAllBytes` và `Files.readString` rất hữu ích cho **unit tests với fixture nhỏ**. Với file lớn, việc nạp tất cả bytes/text vào RAM có thể làm hết heap, kéo dài GC hoặc làm test không phản ánh code production streaming.

Một số chiến lược:

1. **File-to-file exact check:** dùng `Files.mismatch(pathA, pathB)` để có offset sai đầu tiên.
2. **Streaming compare:** mở hai input streams; đọc theo chunk, so sánh nội dung hữu hiệu từng lần đọc, đồng thời xử lý khác kích thước và partial reads.
3. **Hash theo luồng:** khi cần kiểm tra với một digest tham chiếu đã đáng tin, tính SHA-256 bằng `MessageDigest`/`DigestInputStream` mà không giữ tất cả bytes.
4. **Golden fixture nhỏ:** với protocol serializer, chọn test vectors đại diện cho boundary cases hơn là toàn bộ file nhiều GB.
5. **Boundary tests:** kích thước 0, 1, bufferSize-1, bufferSize, bufferSize+1, nhiều lần bufferSize+1; đặc biệt test chunk cuối ngắn.
6. **Resource closure:** dùng `try-with-resources` cho streams/channels và xử lý lỗi khi đóng.
7. **Concurrency:** nếu source đang bị process khác sửa, so sánh/hash có thể không tương ứng với snapshot nhất quán. Tách nguồn bất biến, dùng snapshot hoặc phối hợp writer trước khi kiểm tra.

Không nên dùng `MessageDigest.isEqual` để “thay thế so sánh nội dung file” khi cả hai digest đều tự tính từ các file có thể bị sửa đồng thời và ứng dụng cần bảo đảm phiên bản cụ thể. Digest comparison phù hợp khi đối tượng được đọc ổn định và tiêu chí trust/reference được định nghĩa rõ.

## 19. Lỗi thường gặp và nguyên nhân

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Test pass nhưng file có byte thừa ở cuối | So chuỗi sau trim, hoặc chỉ so prefix. | So byte length và toàn bộ byte bằng `Arrays.equals/Files.mismatch`. |
| Text giống trên editor nhưng mismatch | Khác BOM, line endings, Unicode normalization hoặc whitespace vô hình. | In hex/offset, xác nhận raw bytes trước. |
| Emoji làm sai byte length | Dùng `String.length()` như số byte UTF-8. | Encode và lấy `byte[].length`. |
| Chỉ chunk cuối bị sai | Ghi cả buffer thay vì đúng `n` byte mới đọc. | Dùng `write(buffer,0,n)` hoặc `ByteBuffer.limit` đúng. |
| Byte `FF` hiển thị `-1` | Java byte là signed. | Dùng `Byte.toUnsignedInt` và hex. |
| Sai charset chỉ trên máy khác | Dùng default charset thay vì chỉ định rõ. | Dùng `StandardCharsets.UTF_8` xuyên suốt. |
| Decode text lỗi nhưng test không fail | Decoder âm thầm replace invalid input. | Dùng `CharsetDecoder` với `CodingErrorAction.REPORT`. |
| Encode ASCII vẫn “chạy được” với tiếng Việt | Encoder thay ký tự không biểu diễn được. | Dùng strict `CharsetEncoder` và assert lỗi. |
| Text bị đổi `LF → CRLF` | `BufferedReader.readLine` rồi `newLine()`. | Copy bytes hoặc giữ line endings theo policy. |
| Dữ liệu mất khi decode stream theo chunks | UTF-8 multibyte bị cắt giữa hai lần read. | Decoder streaming có state hoặc tích lũy đúng. |
| Hash khớp nhưng file là sai version | Hash tham chiếu không gắn với identity/version. | Manifest đáng tin gồm algorithm, path, version, digest. |
| Copy qua text API làm ZIP hỏng | Cố decode bytes nhị phân thành String. | Dùng `InputStream/OutputStream` hoặc `FileChannel`. |
| Test không tái lập trên CI | Phụ thuộc locale, timezone, EOL, default charset hoặc path separator. | Fixture xác định và cấu hình explicit. |
| File đang bị process khác ghi nên test flake | So sánh không có snapshot, file đổi trong lúc đọc. | Đảm bảo file bất biến hoặc copy/snapshot trước khi kiểm tra. |
| In log toàn bộ hex để debug | Tiết lộ dữ liệu nhạy cảm hoặc làm log khổng lồ. | Chỉ log offset và vùng context có giới hạn khi được phép. |

## 20. Thiết kế bộ kiểm thử đáng tin

Một suite tốt nên có nhiều tầng:

**Unit tests cho encoder/decoder:** kiểm tra golden bytes độc lập; test UTF-8 hợp lệ và sai; assert được lỗi khi unpaired surrogate hoặc byte sequence malformed; test charset được chỉ định, không phụ thuộc default.

**Tests cho read/write helper:** dùng bytes có `00`, `FF`, dữ liệu ngẫu nhiên seeded deterministic, size qua nhiều ranh giới buffer; so bytes exact sau một vòng copy.

**Round-trip text tests:** kiểm tra tiếng Việt, emoji, combining marks, ký tự xuống dòng, dòng rỗng cuối, BOM policy; xác định test muốn exact `String.equals` hay normalization equivalence.

**Integration tests:** ghi file, đóng resource, mở lại từ disk, so `Files.mismatch`, kiểm tra behavior trên Windows/Linux khi cần; ghi rõ các giả định có thể khác OS.

**Regression tests:** mỗi lỗi encoding/copy đã phát hiện nên có fixture thể hiện đúng byte đã lỗi. Đừng “sửa” expected output để test pass mà không xác nhận hợp đồng dữ liệu thực.

**Negative tests:** dữ liệu truncate, invalid UTF-8, file không đọc được, disk full/permission khi môi trường test hỗ trợ, corrupt frame, duplicate bytes, source đổi trong quá trình đọc; kết quả phải thất bại rõ ràng, không trả thành công giả.

Đặc biệt, đối với serializer/protocol, tránh phép thử tự xác nhận duy nhất kiểu:

```text
actualBytes = encode(input)
restored = decode(actualBytes)
assertEquals(input, restored)
```

Nếu encode và decode cùng mắc một lỗi đối xứng, phép thử có thể vẫn pass. Bổ sung một **golden vector bytes** chuẩn và bài test độc lập cho cả encode và decode.

## 21. Bài tập thực hành

**Bài 1 — Golden UTF-8.** Dùng `HexFormat.parseHex("61c3a90a")` làm expected cho văn bản `"aé\n"`. Viết JUnit test so chính xác bằng `assertArrayEquals` và giải thích vì sao phải dùng hex fixture độc lập thay vì tạo expected bằng chính logic cần test.

**Bài 2 — Một byte hỏng.** Tạo `byte[]` chứa 1.024 byte, thay byte ở index 511 rồi dùng `Arrays.mismatch` xác nhận vị trí sai. Kiểm thử thêm trường hợp mảng dài khác nhau nhưng chung prefix.

**Bài 3 — BOM.** Tạo hai file UTF-8 chứa `abc`, một file có prefix `EF BB BF`. Dùng `Files.mismatch`, `Files.size` và hex để báo khác biệt. Quy định rõ test “raw exact” và test “semantic text” có chấp nhận BOM hay không.

**Bài 4 — Line endings.** Viết fixture với CRLF, LF và một file trộn cả hai. Copy với streams (không dùng readLine) rồi so nguyên byte; tiếp theo chủ động normalize newline và kiểm tra output theo policy mới.

**Bài 5 — NFC/NFD.** So hai chuỗi `"café"` và `"cafe\u0301"` bằng `equals`, byte arrays UTF-8 và `Normalizer.Form.NFC`. Giải thích vì sao kết luận khác nhau và khi nào normalization bị cấm.

**Bài 6 — Invalid UTF-8.** Dùng mảng `C3 28`. So hành vi decoder strict với API convenience có replacement. Đảm bảo một pipeline yêu cầu dữ liệu chính xác phải báo lỗi, không sửa byte âm thầm.

**Bài 7 — Multibyte bị cắt.** Gửi bytes `C3 A9` qua hai read chunks `C3` và `A9`. So kết quả decode từng chunk với decode toàn stream. Sửa bằng `InputStreamReader` hoặc `CharsetDecoder` incremental.

**Bài 8 — File lớn.** Sinh file dữ liệu có seed và kích thước lớn hơn nhiều lần buffer, rồi copy qua `FileChannel`; so `Files.mismatch == -1`. Chèn lỗi cố ý ở byte giữa file để in offset sai. Không log toàn bộ nội dung.

**Bài 9 — Text length và payload length.** Tạo protocol gửi `"Việt 😀"` dưới dạng UTF-8 với header 4 byte chiều dài; xác nhận dùng `encoded.length`, không dùng `String.length()`. Mô phỏng receiver đọc payload qua nhiều chunk.

**Bài 10 — Regression fixture.** Chọn một lỗi thật như BOM bị thêm hoặc LF bị đổi thành CRLF. Lưu fixture trước và sau dưới dạng hex kèm expected mismatch offset; viết test để lỗi không quay lại.

### Câu hỏi tự kiểm tra

1. `String.equals` chứng minh byte-for-byte equality được không?
2. `Files.mismatch` trả về giá trị gì khi hai file hoàn toàn giống nhau?
3. Khi source stream đọc 7 byte vào mảng 8.192 byte, nên ghi bao nhiêu byte?
4. Vì sao `getBytes()` không truyền charset có thể làm test không ổn định trên môi trường khác?
5. `String.length()` của một emoji supplementary là bao nhiêu và UTF-8 dùng mấy byte?
6. Khi nào hai chuỗi cùng hiển thị nhưng `Arrays.equals(utf8A, utf8B) == false`?
7. `readLine/newLine` tác động thế nào đến line ending raw bytes?
8. Vì sao decoder strict cần thiết với dữ liệu văn bản phải toàn vẹn?
9. Vì sao không nên decode một phần byte UTF-8 riêng lẻ cho mỗi chunk?
10. Một bộ test encode→decode pass có đủ chứng minh encoder tuân thủ golden wire format không?

## 22. Tham khảo

- [Java SE 21 — Arrays](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html): `equals`, `mismatch` với các mảng primitive.
- [Java SE 21 — Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): `readAllBytes`, `readString`, `write`, `copy`, `mismatch`.
- [Java SE 21 — StandardCharsets](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html): các charset chuẩn có sẵn.
- [Java SE 21 — CharsetDecoder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html): cách phát hiện malformed/unmappable input.
- [Java SE 21 — CharsetEncoder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetEncoder.html): kiểm soát lỗi khi encode String ra bytes.
- [Java SE 21 — HexFormat](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HexFormat.html): đọc/ghi byte dưới dạng hex.
- [Java SE 21 — Normalizer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.html): Unicode normalization NFC/NFD/NFKC/NFKD.
- [Java SE 21 — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): position/limit/remaining và thao tác NIO.
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/): assertions và data-driven tests.

**Ghi nhớ:** Với yêu cầu **byte-exact**, hãy kiểm tra từng byte thực tế qua `Arrays.equals/Arrays.mismatch/Files.mismatch` và báo offset khi lỗi. Với **text round-trip**, chỉ định charset tường minh, kiểm thử cả encode/decode lẫn golden bytes, và kiểm soát BOM, newline, Unicode normalization cùng lỗi malformed. “Nhìn đúng trên màn hình” và “không có exception” không thay thế bằng chứng nội dung đã được bảo toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-004">Dữ liệu Unicode, BOM và ký tự bị chia giữa các khối</a>

<details>
<summary>Xem chi tiết</summary>

# Dữ liệu Unicode, BOM và ký tự bị chia giữa các khối khi đọc/ghi Java I/O

## 1. Vấn đề cần giải quyết

Khi một ứng dụng Java đọc hoặc ghi văn bản, nó không thực sự chuyển trực tiếp “ký tự” qua ổ đĩa hay socket. Nó chuyển **byte**. Các byte ấy phải được mã hóa hoặc giải mã theo một **charset** nhất định, chẳng hạn UTF-8 hoặc UTF-16.

Một chương trình có thể xử lý đúng văn bản ASCII nhưng lại làm hỏng tiếng Việt, emoji hoặc tên người khi:

- Chia tệp/network stream thành các **khối byte** rồi gọi `new String(chunk, UTF_8)` cho **từng khối riêng lẻ**.
- Đọc các byte của một ký tự UTF-8 nhiều byte bằng những lượt đọc khác nhau.
- Nhầm **byte**, **UTF-16 code unit**, **Unicode code point** và **ký tự người dùng nhìn thấy**.
- Không nhận diện đúng **BOM** (Byte Order Mark) ở đầu file.
- Giải mã UTF-16 với byte order không phù hợp.
- Thay thế byte lỗi bằng ký tự `�` mà không phát hiện nguồn dữ liệu đã hỏng.
- Nhầm số byte trong network framing với `String.length()`.
- Cắt buffer ở vị trí bất kỳ rồi coi đó là ranh giới ký tự.

Bài này đặt trong nhóm **Kiểm thử và chẩn đoán** của Java Core I/O: ngoài cách giải mã đúng, chúng ta sẽ xây dựng các trường hợp kiểm thử ranh giới, in byte hex, kiểm tra chính sách lỗi, BOM và Unicode normalization.

**Nguyên tắc trung tâm:** Ranh giới một lần `InputStream.read(...)`, `FileChannel.read(...)` hoặc một network packet **không phải** ranh giới Unicode character. Việc giải mã phải duy trì **trạng thái liên tục** giữa các lần nhận byte.

## 2. Unicode không đồng nghĩa với UTF-8

### Unicode code point

Unicode định nghĩa các **code point**, thường được viết dưới dạng `U+XXXX`. Ví dụ:

| Nội dung | Unicode code point | UTF-8 | UTF-16 |
|---|---|---|---|
| `A` | `U+0041` | `41` | `0041` |
| `é` | `U+00E9` | `C3 A9` | `00E9` |
| `☕` | `U+2615` | `E2 98 95` | `2615` |
| `😀` | `U+1F600` | `F0 9F 98 80` | `D83D DE00` (surrogate pair) |

Các dãy UTF-16 trong bảng là **giá trị code unit**; byte thực tế của chúng còn phụ thuộc big-endian/little-endian.

### UTF-8 là encoding biến độ dài

Trong UTF-8, một Unicode code point hợp lệ có thể chiếm **1 đến 4 byte**. Các byte thuộc cùng một code point **có thể nằm ở hai khối đọc khác nhau**.

Ví dụ với `☕`:

```text
Unicode code point: U+2615

UTF-8 bytes: E2 98 95
             ^^ ^^ ^^
             1  2  3

Chunk #1: E2 98
Chunk #2: 95

Cả hai chunk riêng lẻ đều KHÔNG đủ để giải mã U+2615.
```

Nếu người lập trình dùng `new String(chunk1, UTF_8)` và `new String(chunk2, UTF_8)` rồi nối hai chuỗi, mỗi lần giải mã có thể xem phần byte của chunk đó là một chuỗi độc lập bị lỗi, làm xuất hiện ký tự thay thế `U+FFFD`.

### UTF-16 là encoding theo code unit 16 bit

Trong Java, `String` dùng chuỗi **UTF-16 code units** để biểu diễn nội dung (API `char` tương ứng một code unit). Một code point ở vùng bổ sung, ví dụ `😀`, cần **hai code unit**:

```text
U+1F600 -> high surrogate D83D + low surrogate DE00

Java "😀".length() == 2
"😀".codePointCount(0, "😀".length()) == 1
```

Trong file UTF-16, từng code unit gồm hai byte; muốn đọc đúng cần biết **endianness**, đồng thời phải xử lý surrogate pair. File còn có thể bị chia ngay **giữa hai byte của một code unit**, hoặc **giữa hai surrogate**.

## 3. Bốn đơn vị đếm hay bị nhầm

Xét chuỗi:

```java
String text = "A☕B😀C";
```

Các đại lượng khác nhau:

| Đại lượng | Giá trị | Giải thích |
|---|---:|---|
| `text.getBytes(UTF_8).length` | 10 | 1 + 3 + 1 + 4 + 1 byte UTF-8 |
| `text.length()` | 6 | Số UTF-16 code units; emoji chiếm 2 |
| `text.codePointCount(0,text.length())` | 5 | Số Unicode code points |
| Số grapheme clusters người dùng thấy | 5 với ví dụ này | Không phải lúc nào bằng code points |

**Không dùng `String.length()` làm kích thước byte cần gửi**, không cắt byte stream theo số ký tự, không coi một Java `char` luôn là một ký tự Unicode đầy đủ.

Ví dụ ghép ký tự có dấu:

```text
"é"        U+00E9            (một code point)
"e\u0301"  U+0065 U+0301     (hai code points)

Hai chuỗi có thể nhìn gần giống nhau nhưng có biểu diễn khác.
```

Hai cách biểu diễn này có thể có số byte UTF-8 khác nhau, `String.equals` không nhất thiết true, và normalization có thể được cân nhắc theo quy định nghiệp vụ.

Đối với emoji phức tạp có zero-width joiner, variation selector hoặc dấu tổ hợp, “một ký tự nhìn thấy” còn có thể gồm **nhiều** code points. Không giả định `codePointCount` luôn bằng số grapheme clusters.

## 4. Một lần read() không đảm bảo đủ một ký tự

API I/O thường trả về số byte thực nhận được:

```java
int n = inputStream.read(buffer);
```

Một lần read có thể trả **ít byte hơn độ dài buffer**, thậm chí khi nguồn còn nhiều dữ liệu, tùy kiểu stream và thời điểm. Với network socket, một send của peer không tương ứng một read của ứng dụng nhận.

Ví dụ tệp chứa byte UTF-8:

```text
41 E2 98 95 42 F0 9F 98 80 43
 A    ☕      B       😀        C
```

Người đọc lấy từng khối 2 byte:

```text
Khối 1: 41 E2
Khối 2: 98 95
Khối 3: 42 F0
Khối 4: 9F 98
Khối 5: 80 43
```

Không một sự kiện I/O nào nói “đây là một Unicode character”. Ranh giới khối chỉ do buffer, provider và thời điểm truyền dữ liệu quyết định.

### Phản ví dụ: decode từng chunk riêng

```java
byte[] buffer = new byte[2];
StringBuilder text = new StringBuilder();

int n;
while ((n = input.read(buffer)) != -1) {
    text.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
}
```

Đây là **mã minh họa sai về Unicode**, giả định biến `input` là `InputStream`. Từng lời gọi `new String` tạo **một phiên giải mã độc lập**, không nhớ rằng byte `E2` cuối khối trước còn cần `98 95` trong khối sau.

Việc chọn buffer lớn hơn **giảm xác suất** cắt ký tự trong một số bài thử nhưng **không giải quyết triệt để**. Bất kỳ buffer hữu hạn nào cũng có thể kết thúc giữa ký tự.

## 5. Cách dễ nhất: dùng Reader với charset tường minh

Nếu mục tiêu là **đọc văn bản** chứ không cần tự thao tác với byte framing, dùng `InputStreamReader` hoặc `Files.newBufferedReader` với charset đúng:

```java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {

    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý cả dòng Unicode đã được decoder ghép đúng.
    }
}
```

Đây là snippet minh họa, cần `BufferedReader`, `Files`, `Path` và `StandardCharsets`; `path` phải được khai báo.

`InputStreamReader` giữ **decoder state giữa những lần read ký tự**. Nếu một code point UTF-8 bị chia qua nhiều lần đọc byte ở dưới, reader có thể giữ phần chưa đủ và chờ byte tiếp theo thay vì giải mã từng byte chunk như một chuỗi hoàn chỉnh.

### Chọn charset một cách tường minh

```java
try (Reader reader = new InputStreamReader(
        Files.newInputStream(path), StandardCharsets.UTF_8)) {
    char[] chars = new char[256];
    int n;
    while ((n = reader.read(chars)) != -1) {
        // Sử dụng đúng n UTF-16 code units.
    }
}
```

Reader trả **UTF-16 code units** vào `char[]`, không trả “đúng n code points”. Dù decoder UTF-8 xử lý byte nhiều phần đúng cách, **một cặp surrogate trong char[] vẫn có thể bị chia qua các lần read ký tự** nếu buffer/khối đọc không đủ hoặc việc đọc dừng đúng ranh giới đó.

Nếu tác vụ chỉ hiển thị/ghi lại văn bản, có thể tiếp tục giữ ký tự trong reader/writer. Nếu cần đếm code point, gắn token/grapheme hoặc validate surrogate, phải xử lý theo API Unicode phù hợp chứ không xem mọi phần tử `char` độc lập.

## 6. CharsetDecoder: giải mã liên tục với ByteBuffer

Khi ứng dụng xử lý **binary protocol**, `ByteBuffer`, hoặc nhận stream theo những đoạn tùy ý, `java.nio.charset.CharsetDecoder` cho phép kiểm soát quá trình giải mã.

Các thành phần:

- `CharsetDecoder`: giữ trạng thái giải mã giữa các lần gọi.
- `ByteBuffer` input: chứa byte mới nhận **và byte dư chưa đủ một character từ lần trước**.
- `CharBuffer` output: nhận UTF-16 code units đã decode.
- `CoderResult.UNDERFLOW`: decoder đã tiêu thụ được những gì có thể từ input hiện tại; có thể còn byte chưa đủ cần giữ lại.
- `CoderResult.OVERFLOW`: output buffer hết chỗ, cần drain `CharBuffer` rồi decode tiếp.
- `CoderResult.isError()`: byte không hợp lệ hoặc unmappable theo charset/error policy.
- `decoder.decode(input, output, endOfInput)`: truyền `true` khi đã xác nhận byte stream thực sự kết thúc.
- `decoder.flush(output)`: hoàn tất phần trạng thái cuối **sau khi decode(..., true) đã trả UNDERFLOW**.

### Chu trình chuẩn với buffer

```text
ByteBuffer ở WRITE MODE
     |   (có thể còn byte từ cuối lượt trước)
     +-- append byte mới
     |
     v
input.flip()  -> READ MODE
     |
     v
decoder.decode(input, chars, eof)
     |
     +-- OVERFLOW -> chars.flip(), drain, chars.clear(), decode tiếp
     |
     +-- ERROR -> throwException / chính sách lỗi
     |
     +-- UNDERFLOW -> giữ byte chưa được tiêu thụ
     |
input.compact() -> dồn byte chưa đủ về đầu, quay WRITE MODE
     |
     +-- nếu chưa EOF: đọc thêm byte vào phần trống, lặp
     |
     +-- nếu EOF: decoder.flush(chars) tới UNDERFLOW
```

`ByteBuffer.compact()` là điểm cốt lõi. Nó **không bỏ phần còn lại**: nó sao chép phần byte ở `[position,limit)` chưa xử lý về đầu và đặt position sau số byte còn tồn. Lượt đọc tiếp chỉ append byte mới phía sau chúng.

Không dùng `input.clear()` sau mọi UNDERFLOW: `clear()` sẽ cho ghi đè toàn bộ input và **đánh mất byte đang chờ**.

## 7. Ví dụ Java 17+ hoàn chỉnh: đọc UTF-8 theo các khối 2 byte

Chương trình dưới đây:

1. Tạo tệp tạm chứa `A☕B😀C` được mã hóa bằng UTF-8.
2. Đọc mỗi lần **tối đa 2 byte**, cố tình cắt ở giữa biểu diễn của `☕` và `😀`.
3. Sử dụng một `CharsetDecoder` xuyên suốt, giữ byte dư bằng `compact()`.
4. Kiểm tra round-trip và so sánh số byte, số UTF-16 code units, số code points.
5. Ghi lại file với **UTF-8 BOM** ở đầu để chứng minh decoder thông thường nhìn thấy `U+FEFF` như một ký tự và cách xử lý BOM theo policy.
6. Đóng/dọn file tạm.

Lưu tên **`UnicodeBoundaryDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class UnicodeBoundaryDemo {

    public static void main(String[] args) throws IOException {
        String original = "A☕B😀C";
        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);
        Path file = Files.createTempFile("unicode-chunk-", ".txt");

        try {
            Files.write(file, utf8);
            String restored = decodeUtf8InTwoByteChunks(file, false);

            System.out.println("UTF-8 bytes: " + utf8.length);
            System.out.println("UTF-16 units: " + original.length());
            System.out.println("Code points: " +
                    original.codePointCount(0, original.length()));
            System.out.println("Split-safe equals: " +
                    original.equals(restored));

            byte[] withBom = new byte[utf8.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(utf8, 0, withBom, 3, utf8.length);
            Files.write(file, withBom);

            String rawWithBom =
                    decodeUtf8InTwoByteChunks(file, false);
            String clean =
                    decodeUtf8InTwoByteChunks(file, true);

            System.out.println("Leading BOM character: " +
                    rawWithBom.startsWith("\uFEFF"));
            System.out.println("After BOM policy: " + clean);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static String decodeUtf8InTwoByteChunks(
            Path file, boolean stripLeadingBom) throws IOException {

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        ByteBuffer inputBytes = ByteBuffer.allocate(8);
        CharBuffer outputChars = CharBuffer.allocate(4);
        StringBuilder result = new StringBuilder();

        try (InputStream stream = Files.newInputStream(file)) {
            boolean endOfInput = false;

            while (!endOfInput) {
                int bytesToRead = Math.min(2, inputBytes.remaining());
                if (bytesToRead == 0) {
                    throw new IOException("Input buffer made no progress");
                }

                int n = stream.read(
                        inputBytes.array(),
                        inputBytes.position(),
                        bytesToRead);

                if (n == -1) {
                    endOfInput = true;
                } else if (n == 0) {
                    throw new IOException("Input stream made no progress");
                } else {
                    inputBytes.position(inputBytes.position() + n);
                }

                inputBytes.flip();

                while (true) {
                    CoderResult step = decoder.decode(
                            inputBytes, outputChars, endOfInput);

                    outputChars.flip();
                    result.append(outputChars);
                    outputChars.clear();

                    if (step.isError()) {
                        step.throwException();
                    }
                    if (step.isUnderflow()) {
                        break;
                    }
                    // OVERFLOW: outputChars đã được drain, decode tiếp.
                }

                inputBytes.compact();
            }

            while (true) {
                CoderResult step = decoder.flush(outputChars);

                outputChars.flip();
                result.append(outputChars);
                outputChars.clear();

                if (step.isError()) {
                    step.throwException();
                }
                if (step.isUnderflow()) {
                    break;
                }
                // OVERFLOW: còn ký tự trong decoder, drain tiếp.
            }
        }

        String text = result.toString();
        if (stripLeadingBom && text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac UnicodeBoundaryDemo.java
java UnicodeBoundaryDemo
```

**Stdout dự kiến chính xác khi I/O file tạm thành công:**

```text
UTF-8 bytes: 10
UTF-16 units: 6
Code points: 5
Split-safe equals: true
Leading BOM character: true
After BOM policy: A☕B😀C
```

Vì sao ví dụ đúng?

- `inputBytes` trong trạng thái write mode nhận tối đa 2 byte mỗi lượt. Nếu gặp 2 byte đầu của `☕`, decoder trả UNDERFLOW và không xem chúng là lỗi vì chưa phải EOF.
- `inputBytes.compact()` giữ lại các byte chưa được decode. Lượt sau append byte thứ ba, tạo đủ chuỗi `E2 98 95`.
- Tương tự với `😀` có 4 byte UTF-8. Decoder có thể giữ những byte đầu qua nhiều lượt cho tới khi nhận đủ.
- `outputChars` chứa kết quả dạng UTF-16. `StringBuilder.append(CharBuffer)` thêm các code units đã giải mã.
- `CodingErrorAction.REPORT` khiến byte thực sự sai hoặc chuỗi cuối bị cắt dở gây lỗi thay vì lặng lẽ chèn `�`.
- `endOfInput=true` **chỉ khi `read` đã trả `-1`**. Nếu báo true sớm khi chunk chưa chứa đủ byte, decoder có thể coi byte dư là malformed.
- Sau decode cuối, `decoder.flush` hoàn tất trạng thái decoder theo đúng protocol.
- `rawWithBom` bắt đầu bằng ký tự `U+FEFF` được giải mã từ byte `EF BB BF`. Policy ví dụ chỉ bỏ **ký tự U+FEFF đầu tiên**; nó không xóa mọi ký tự tương tự giữa nội dung.

**Giới hạn có chủ đích:** ví dụ dùng buffer nhỏ, file nhỏ và `StringBuilder` để nhìn rõ cơ chế. Khi triển khai thật với file rất lớn hoặc nguồn không tin cậy, cần giới hạn output, quản lý tốc độ/timeout và xử lý trực tiếp phần ký tự đã decode thay vì tích lũy toàn bộ vào một StringBuilder.

## 8. Phân tích sâu các trạng thái CharsetDecoder

### UNDERFLOW

Kết quả UNDERFLOW thường có nghĩa decoder **không thể tiến thêm** với input đã cung cấp. Trong chế độ streaming, `ByteBuffer` có thể còn 1–3 byte đầu của một UTF-8 sequence chưa đủ. Giữ chúng lại bằng `compact()`.

Ví dụ:

```text
Bytes input: E2 98
Decoder: UNDERFLOW
input.remaining(): 2 (chưa tiêu thụ)
compact(): giữ E2 98
append next: 95
decode lại: -> ☕
```

Khi `endOfInput=true`, các byte thiếu ở EOF không còn được chờ tiếp: chúng phải bị xử lý theo malformed-input policy.

### OVERFLOW

`OVERFLOW` không có nghĩa file quá lớn hoặc byte sai. Nó có nghĩa **CharBuffer chưa đủ chỗ chứa các UTF-16 code units kế tiếp**. Có thể giải quyết bằng:

```java
outputChars.flip();
process(outputChars);
outputChars.clear();
```

Đây là đoạn minh họa, `process` do ứng dụng định nghĩa. Sau khi drain, gọi `decode` tiếp **trên input đang ở vị trí hiện tại**, không được bỏ input còn lại hoặc tạo decoder mới.

### ERROR

`CoderResult.isError()` gồm malformed-input hoặc unmappable-character theo bảng mã và chính sách:

- `REPORT`: nhận kết quả lỗi, `throwException()` chuyển thành checked exception phù hợp, ví dụ `MalformedInputException`.
- `REPLACE`: thay bằng một chuỗi/ký tự replacement của decoder và tiếp tục.
- `IGNORE`: bỏ input bị lỗi, có nguy cơ mất thông tin mà không để dấu vết.

`REPORT` thường thích hợp khi kiểm thử data pipeline, đọc file cấu hình, giải mã giao thức có schema hoặc nội dung cần bảo toàn chính xác. `REPLACE` có thể phù hợp với giao diện hiển thị dữ liệu bị hỏng nếu sản phẩm muốn phục hồi “best effort”, nhưng phải có telemetry để biết đã mất byte. `IGNORE` hiếm khi thích hợp cho dữ liệu có ý nghĩa bảo mật, identifier hoặc chữ ký.

## 9. InputStreamReader có giải quyết tất cả không?

`InputStreamReader` là lựa chọn đơn giản và rất tốt cho **byte-to-character streaming** với charset đã biết. Nó giữ `CharsetDecoder` nội bộ và không làm sai Unicode chỉ vì tầng InputStream cung cấp các byte chia nhỏ.

Nhưng cần nhớ:

1. Nó không tự đoán chính xác charset của mọi file; nên quy định rõ.
2. Với UTF-8, nó không tự động loại mọi BOM theo một chính sách ứng dụng có thể dự đoán; nếu cần bỏ BOM đầu file, phải có logic riêng.
3. Nó chuyển byte thành `char`/UTF-16 units. Nếu ứng dụng xử lý các đơn vị `char[]` riêng và cắt surrogate pair, lỗi có thể xuất hiện ở **tầng sau** dù byte decoder đã đúng.
4. Nó không tự parse JSON, XML hay giao thức có length prefix.
5. Nó không giới hạn tổng lượng ký tự được đọc, không tự bảo vệ memory nếu ứng dụng gọi `readAllBytes` hoặc ghép chuỗi không kiểm soát.
6. Với API parser yêu cầu byte offset chính xác, `Reader` có thể không tiện vì một `char` không ánh xạ 1:1 sang một byte.
7. Với các codec và yêu cầu error policy khác, tạo `CharsetDecoder` chủ động có thể giúp báo lỗi thay vì thay ký tự.

### Cắt UTF-16 surrogate pair ở tầng ký tự

Ví dụ:

```java
String emoji = "😀";
char high = emoji.charAt(0);
char low = emoji.charAt(1);

System.out.println(emoji.length()); // 2
System.out.println(Character.isHighSurrogate(high)); // true
System.out.println(Character.isLowSurrogate(low));   // true
```

Đây là một đoạn Java minh họa có thể đặt trong `main`. Nếu sau khi đã decode UTF-8 đúng, một chương trình lại xử lý từng `char` như một code point độc lập, nó vẫn có thể làm hỏng emoji.

Giải pháp ở tầng text: sử dụng `String.codePoints()`, `Character.codePointAt`, hoặc một iterator giữ high/low surrogate giữa các khối `char[]`. Với việc phân đoạn ký tự hiển thị, cần thêm xử lý grapheme clusters (Unicode segmentation) chứ không chỉ code points.

## 10. BOM là gì?

**Byte Order Mark (BOM)** có gốc là ký hiệu Unicode `U+FEFF` được dùng ở đầu một số stream, chủ yếu để chỉ **endianness** hoặc nhận diện encoding. Không phải mọi file Unicode đều có BOM và BOM không phải metadata bảo mật.

Các dãy byte mở đầu phổ biến:

| Encoding/đặc điểm | BOM ở đầu stream |
|---|---|
| UTF-8 | `EF BB BF` |
| UTF-16 big-endian | `FE FF` |
| UTF-16 little-endian | `FF FE` |
| UTF-32 big-endian | `00 00 FE FF` |
| UTF-32 little-endian | `FF FE 00 00` |

**Chú ý:** UTF-8 không có vấn đề byte order; UTF-8 BOM là **dấu hiệu nhận diện** tùy chọn, không phải yêu cầu. Nhiều định dạng/giao thức cấm, bỏ qua hoặc xử lý BOM theo các quy tắc riêng.

### BOM phải ở đầu stream

Nếu ứng dụng quyết định bỏ BOM, chỉ nhận diện BOM ở **đầu nội dung** (sau khi xác định source framing phù hợp). Đừng dùng `text.replace("\uFEFF","")` trên toàn bộ văn bản: bạn sẽ xóa mọi `U+FEFF` xuất hiện trong nội dung, dù nó không được dùng làm BOM đầu stream.

BOM có thể bị chia qua chunk boundaries:

```text
UTF-8 BOM: EF BB BF
Chunk 1: EF
Chunk 2: BB
Chunk 3: BF 41 ...

Không được quyết định “không có BOM” chỉ vì chunk 1 chưa đủ ba byte.
```

Vì vậy BOM detector tự viết cũng phải **giữ lại số byte đầu đủ dài** rồi mới quyết định, hoặc giải mã liên tục từ đầu và xử lý ký tự `U+FEFF` đầu tiên khi định dạng cho phép.

## 11. UTF-8 BOM và Java StandardCharsets.UTF_8

`StandardCharsets.UTF_8` khi giải mã byte `EF BB BF` thông thường trả về **ký tự `U+FEFF`** trong chuỗi. Ví dụ:

```java
byte[] bytes = {
    (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
    (byte) 0x41
};

String text = new String(bytes, StandardCharsets.UTF_8);
System.out.println(text.startsWith("\uFEFF")); // true
System.out.println(text.substring(1));          // A
```

Đây là đoạn code minh họa cần `StandardCharsets` và có thể đặt trong `main`.

Đây là nguồn của một lỗi khó thấy: file cấu hình có BOM UTF-8, ứng dụng kỳ vọng header `"id,name"` nhưng String thực là `"\uFEFFid,name"`. So sánh tên cột đầu tiên thất bại dù mắt thường thấy giống nhau.

Cách xử lý tùy định dạng:

- Nếu format **cho phép BOM**, nhận diện và bỏ BOM **một lần ở đầu**.
- Nếu format **cấm BOM**, từ chối input với thông báo rõ thay vì tự ý sửa.
- Nếu format cần bảo toàn chính xác byte gốc, không loại BOM âm thầm.
- Nếu ứng dụng chỉ hiển thị, nên có quyết định nhất quán để tránh ký tự zero-width ẩn đầu text.

Không tự chèn UTF-8 BOM cho mọi file bằng mặc định; UTF-8 chuẩn không cần BOM để giải mã đúng.

## 12. UTF-16 BOM và byte order

UTF-16 có hai endian-specific charset là `UTF_16BE` và `UTF_16LE`; Java cũng có `StandardCharsets.UTF_16`. Chúng không được sử dụng như nhau trong mọi trường hợp.

- `UTF_16BE`: hai byte đầu của code unit `0x0041` là `00 41`.
- `UTF_16LE`: hai byte đầu là `41 00`.
- `UTF_16`: Java hỗ trợ cơ chế nhận diện BOM để xác định byte order (khi có), dùng byte order mặc định của charset này khi không có BOM theo hợp đồng implementation/documentation.

Ví dụ:

```text
Văn bản "A"

UTF-16BE bytes (không BOM): 00 41
UTF-16LE bytes (không BOM): 41 00

Có BOM:
UTF-16BE: FE FF 00 41
UTF-16LE: FF FE 41 00
```

Nếu code dùng `new String(bytes, UTF_16LE)` để đọc file có BOM UTF-16BE, kết quả sẽ sai; bạn đã chỉ định little-endian trong khi byte thực tế là big-endian. Nếu dùng `UTF_16` trên dữ liệu có BOM chuẩn, decoder có thể nhận diện endian phù hợp.

### Không chỉ kiểm tra hai byte đầu một cách cứng nhắc

Nếu hệ thống hỗ trợ cả UTF-16/UTF-32, thứ tự nhận diện BOM cần chú ý vì `FF FE 00 00` của UTF-32LE bắt đầu bằng `FF FE` (trùng prefix của UTF-16LE). Detector tự viết nên kiểm tra **BOM dài nhất trước**, kết hợp charset được protocol cho phép. Java SE có các charset UTF-8/UTF-16 chuẩn; UTF-32 không nằm trong `StandardCharsets` constant set bắt buộc ở mọi môi trường, nên không tự assume `StandardCharsets.UTF_32LE` tồn tại.

Một ứng dụng tốt thường **không cần tự động dò mọi Unicode encoding**. Nếu format đã quy định UTF-8 thì xử lý như UTF-8 và chỉ quản lý BOM theo spec của format đó.

## 13. Không nhầm BOM với mã hóa file từ nguồn không rõ

“Có BOM” không có nghĩa nội dung phía sau chắc chắn hợp lệ. “Không có BOM” cũng không đủ để kết luận file là ASCII hay UTF-8.

Một số nguồn lỗi:

- Ứng dụng dùng `Charset.defaultCharset()` thay vì charset của giao thức.
- File ghi bằng Windows-1252 hoặc một legacy code page nhưng code đọc bằng UTF-8.
- File UTF-16LE không BOM bị đọc bằng UTF-8, tạo chuỗi có NUL xen kẽ hoặc ký tự không mong đợi.
- File UTF-8 BOM bị ứng dụng xử lý như tên cột CSV thông thường.
- File có byte sai hoặc bị cắt cuối, decoder với `REPLACE` làm mất bằng chứng lỗi.
- Network protocol có length prefix tính theo **số byte**, nhưng writer lại dùng `String.length()`.
- HTTP/content metadata và nội dung thực không nhất quán; ứng dụng vẫn phải theo spec để xử lý.

Không dùng phép suy đoán từ một vài byte để gán charset cho dữ liệu mang ý nghĩa bảo mật hoặc cần trọn vẹn. Nếu không có metadata đáng tin, cần một **hợp đồng/chiến lược phát hiện encoding rõ ràng** và xác nhận ở tầng nghiệp vụ.

## 14. CodingErrorAction: REPORT, REPLACE, IGNORE

Java `CharsetDecoder` cho phép thiết lập:

```java
CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
```

Đây là đoạn minh họa, cần import `CharsetDecoder`, `StandardCharsets` và `CodingErrorAction`.

Bảng so sánh:

| Chính sách | Hành vi | Nên dùng khi |
|---|---|---|
| `REPORT` | Trả lỗi dạng `CoderResult` / ném exception khi xử lý | Dữ liệu cần đúng byte, cấu hình, protocol, kiểm thử |
| `REPLACE` | Chèn replacement do decoder cung cấp | Hiển thị best-effort được phép mất thông tin |
| `IGNORE` | Bỏ phần input lỗi | Chỉ khi yêu cầu nghiệp vụ cho phép bỏ dữ liệu mà không phá ý nghĩa |

### Malformed UTF-8

Một chuỗi byte có thể sai vì:

- Byte continuation xuất hiện khi chưa có lead byte hợp lệ.
- Lead byte cần thêm continuation nhưng gặp byte không phù hợp.
- Một sequence bị cắt ngay khi EOF.
- Byte dùng encoding **overlong** cho code point (bị UTF-8 chuẩn cấm).
- Sequence tạo surrogate code points hoặc code point ngoài phạm vi Unicode.
- Byte trên một charset khác bị giải mã nhầm UTF-8.

Ví dụ byte thiếu:

```java
byte[] incomplete = {(byte) 0xE2, (byte) 0x98};
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT);

try {
    decoder.decode(ByteBuffer.wrap(incomplete));
} catch (MalformedInputException ex) {
    System.out.println("Truncated UTF-8 detected");
}
```

Đoạn này là **mã minh họa**, cần import `ByteBuffer`, `CharsetDecoder`, `CodingErrorAction`, `MalformedInputException`. Overload `decode(ByteBuffer)` thực hiện một lần giải mã hoàn chỉnh, nên phần 2-byte chưa đủ bị coi là malformed khi kết thúc input. Trong streaming, gọi `decode(input, output, false)` trước EOF sẽ **chờ** byte tiếp theo thay vì báo sai ngay.

### Unicode replacement character không chỉ là chuyện thẩm mỹ

Nếu chương trình lưu `�` vào tên file, key hoặc chữ ký thay vì dữ liệu gốc, nhiều thứ có thể thay đổi:

- Hash/checksum byte.
- So sánh tên user/resource.
- JSON field name hoặc CSV header.
- Truy vấn database và khóa deduplication.
- Nội dung văn bản có ý nghĩa pháp lý, tài chính, y tế hoặc bảo mật.

Vì vậy ở các trường hợp yêu cầu chính xác, nên xử lý lỗi bằng `REPORT` hoặc xác minh encoding nguồn thay vì tự biến input lỗi thành “đã decode thành công”.

## 15. ByteBuffer: flip, compact, clear và các lỗi boundary

`ByteBuffer` có ba trạng thái quan trọng: `capacity`, `position` và `limit`.

### Sau read byte từ I/O

Buffer write mode:

```text
capacity = 8
position = 5
limit = 8
[41 E2 98 95 42 | ...]
```

`flip()` đặt limit bằng position hiện tại, position về 0 để decoder đọc byte hợp lệ:

```text
position = 0
limit = 5
[41 E2 98 95 42 | ...]
```

### Khi decoder chưa xử lý hết bytes

Giả sử cuối buffer có `E2 98` thiếu byte `95`. Sau decode, position trỏ tới hai byte chưa được consume.

`compact()` giữ hai byte đó, position chuyển tới 2, limit về capacity. Lượt I/O kế tiếp ghi nối thêm byte sau vị trí 2.

`clear()` trong tình huống này thì **không giữ nội dung chưa decode**, có thể khiến byte cũ bị ghi đè; đây là nguyên nhân phổ biến của ký tự `�` hoặc mất dữ liệu không ổn định theo buffer size.

### Decoder và CharBuffer cũng có ranh giới riêng

Với `CharBuffer` output quá nhỏ, `decode` trả OVERFLOW. Drain output và gọi `decode` tiếp. Đừng giải thích OVERFLOW là “file nén quá lớn” hay “byte UTF-8 sai”.

Nếu output CharBuffer có chỗ cho một UTF-16 unit nhưng code point kế tiếp cần surrogate pair gồm hai units, decoder có thể phải trả OVERFLOW thay vì đặt nửa cặp không hợp lệ. Chọn buffer đủ lớn để có thể chứa ít nhất cặp surrogate và luôn xử lý OVERFLOW.

## 16. Tự chẩn đoán bằng hex dump

Khi một ký tự bị hỏng, log chuỗi đã được `new String(...)` có thể đánh mất bằng chứng vì byte không hợp lệ đã bị thay. Hãy quan sát **byte gốc** ở một môi trường test/an toàn:

```java
byte[] data = "A☕B😀C".getBytes(StandardCharsets.UTF_8);
StringBuilder hex = new StringBuilder();

for (byte b : data) {
    if (!hex.isEmpty()) {
        hex.append(' ');
    }
    hex.append(String.format("%02X", b & 0xFF));
}

System.out.println(hex);
```

Snippet cần import `StandardCharsets`; có thể đặt trong `main`. `StringBuilder.isEmpty()` là phương thức có trên Java 17 (qua `CharSequence`/`StringBuilder` API tương ứng tùy runtime); để tránh ràng buộc đối với môi trường cũ, có thể thay bằng `hex.length() > 0`.

Đầu ra:

```text
41 E2 98 95 42 F0 9F 98 80 43
```

**Không log raw bytes của password, access token hay dữ liệu cá nhân trong production**. Với lỗi thực tế, ưu tiên log metadata có giới hạn, offset, charset dự kiến và sample bytes đã redacted theo policy.

### Thông tin chẩn đoán nên có

- Encoding/charset đã cấu hình ở cả writer và reader.
- File path/source ID, kích thước file và byte offset nơi phát hiện lỗi.
- Kích thước từng chunk và `ByteBuffer.position/limit`.
- CoderResult là `UNDERFLOW`, `OVERFLOW` hay `MALFORMED`.
- Có BOM hay không; BOM thuộc phần đầu file hay sau một prefix protocol khác.
- Chính sách `REPORT/REPLACE/IGNORE`.
- Dữ liệu có bị gzip/decompress, decrypt hoặc transform trước khi decode không.
- Có đoạn nào tự tạo `new String(chunk)` với charset mặc định không.
- Có logic xóa mọi `\uFEFF` hoặc normalize Unicode không kiểm soát không.

## 17. Chẩn đoán byte offset và char index

Nếu lỗi xảy ra ở byte offset 127, bạn không thể suy ra nó chính là Java `String.charAt(127)` khi dữ liệu dùng UTF-8.

Ví dụ:

```text
Text: A☕B
UTF-8:
  A: byte offset 0, length 1
  ☕: byte offset 1, length 3
  B: byte offset 4, length 1

Java UTF-16 char indexes:
  A index 0
  ☕ index 1
  B index 2
```

Với emoji vùng bổ sung, chênh lệch còn khác hơn vì 4 UTF-8 bytes → 2 UTF-16 units → 1 code point.

Khi báo lỗi parser, hãy nêu rõ offset là:

- **Byte offset** trong file/wire.
- **UTF-16 char index** trong Java String.
- **Code point index** của nội dung.
- **Dòng/cột logic** nếu parser đã định nghĩa chuẩn newline và Unicode.

Nếu một binary protocol có length prefix bằng byte, phải encode trước để tính length, và reader đọc đủ length byte rồi decode đúng **phần payload**, không tính bằng `String.length()`.

## 18. Byte length trong giao thức mạng

Sai lầm điển hình:

```java
String body = "Cà phê ☕";
int declaredLength = body.length();   // Sai nếu protocol yêu cầu byte count.
byte[] wire = body.getBytes(StandardCharsets.UTF_8);
```

Nếu giao thức có frame `[4-byte payload length][payload bytes]`, length phải là `wire.length`, không phải `body.length()`.

Mẫu đúng (mã minh họa):

```java
byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
if (bodyBytes.length > MAX_PAYLOAD_BYTES) {
    throw new IOException("Frame too large");
}
frame.putInt(bodyBytes.length);
frame.put(bodyBytes);
```

Các biến `body`, `MAX_PAYLOAD_BYTES`, `frame` cần được khai báo/cấp đủ capacity trước, và `frame` phải tuân theo `ByteOrder` đã thống nhất với peer. Ở phía đọc, sử dụng đúng số byte length đã công bố, đọc đủ frame (xử lý partial I/O), rồi decode **sau khi đủ byte**. Dù frame bị chia qua TCP packets hoặc file chunks, encoding vẫn có ranh giới logic rõ ràng.

Nếu giao thức dùng text delimiter như newline, còn phải quyết định cách escape, việc nhận `\r\n` bị chia qua buffers và giới hạn dòng. **Ranh giới network I/O không tự biến thành ranh giới record hoặc ký tự Unicode**.

## 19. Unicode normalization: giống về hình thức chưa chắc bằng bytes

Hai chuỗi có thể được hiển thị tương tự nhưng khác mã hóa Unicode:

```java
String a = "\u00E9";
String b = "e\u0301";

System.out.println(a.equals(b)); // false

String na = Normalizer.normalize(a, Normalizer.Form.NFC);
String nb = Normalizer.normalize(b, Normalizer.Form.NFC);
System.out.println(na.equals(nb)); // true
```

Đây là đoạn minh họa cần import `java.text.Normalizer`, chạy được trong method Java 17.

- **NFC** cố gắng tổ hợp canonical equivalents theo chuẩn Unicode.
- **NFD** tách canonical decomposition.
- NFKC/NFKD có compatibility mapping, có thể thay đổi ý nghĩa hoặc kiểu hiển thị của một số ký tự hơn canonical normalization.
- Không normalize âm thầm mọi dữ liệu như identifier, password hoặc chuỗi đã ký nếu protocol không quy định.
- Không coi normalization là phòng chống mọi homograph, spoofing hoặc confusable characters.

Để kiểm thử dữ liệu tiếng Việt, nên có cả dạng precomposed và combining marks, kiểm tra byte round-trip khi cần bảo toàn nguyên bản, và kiểm tra normalization riêng theo rule nghiệp vụ.

## 20. Test cases tối thiểu cho Unicode streaming

Một bộ kiểm thử nên có các chuỗi và tình huống sau:

| Trường hợp | Điều muốn chứng minh |
|---|---|
| ASCII `Hello` | Không cần multibyte, baseline |
| Tiếng Việt `Cà phê` | Unicode ngoài ASCII |
| `☕` chia sau byte 1 và byte 2 | Decoder giữ 1–2 byte còn thiếu |
| `😀` chia sau byte 1, 2 và 3 | Decoder giữ tối đa 3 byte đầu |
| UTF-8 BOM `EF BB BF` chia làm 3 chunks | Policy BOM áp dụng đúng một lần |
| Không BOM | Không vô tình xóa ký tự đầu tiên |
| U+FEFF ở giữa nội dung | Không xóa mọi U+FEFF |
| UTF-16BE/LE với BOM | Đúng endian theo format |
| UTF-16LE không BOM | Chỉ decode đúng khi charset đã được biết |
| Input UTF-8 thiếu byte cuối | `REPORT` phát hiện truncated input khi EOF |
| Byte continuation lẻ `80` | Malformed input đúng |
| Buffer output đầy | `OVERFLOW` được drain, không mất ký tự |
| Decoder chia byte với kích thước chunk 1, 2, 3, 4, 7... | Output bất biến theo cách phân khối |
| String có combining accents | Không lẫn equals với canonical equivalence |
| Emoji với surrogate pair | Không cắt đôi khi xử lý Java char[] |
| Giao thức length-prefixed UTF-8 | Length tính theo byte, không theo String.length |

### Property-style test: bất biến theo ranh giới chunk

Một nguyên tắc mạnh:

> Với cùng một dãy byte UTF-8 hợp lệ, output của decoder stateful phải giống nhau bất kể input được chia thành các chunk 1, 2, 3,... byte như thế nào (miễn là giữ đúng thứ tự byte và báo EOF đúng lúc).

Có thể viết test lặp các kích thước chunk cố định khác nhau, sau đó thêm kiểm thử random partition. Không cần “một packet = một message” để decode text đúng.

Tương tự, với chuỗi UTF-8 không hợp lệ, **chính sách lỗi** phải nhất quán: `REPORT` phát hiện input malformed/truncated, còn `REPLACE` tạo output được định nghĩa theo chính sách replacement; không âm thầm chấp nhận dữ liệu chỉ vì sự cố xảy ra ở boundary khác.

## 21. BOM và đầu vào có prefix/header nhị phân

Trong một số protocol, byte stream bắt đầu bằng **binary header** rồi mới đến một trường text. Khi đó BOM (nếu spec cho phép) phải được kiểm tra **tại đầu trường text**, không nhất thiết ở offset 0 của toàn bộ stream.

Ví dụ:

```text
[magic: 4 bytes][version: 1][textLength: 4][text UTF-8 bytes]
```

Nếu `text UTF-8 bytes` bắt đầu bằng `EF BB BF`, policy BOM áp dụng sau khi parser đã đọc đúng header và xác định vị trí payload. Nếu parser đọc toàn bộ byte stream qua `InputStreamReader` ngay từ byte 0, binary header có thể bị giải mã thành ký tự sai và làm lệch byte offset.

Vì vậy:

1. Parse **binary structure** theo byte trước.
2. Xác định byte range của text payload.
3. Chọn charset từ spec/metadata.
4. Áp policy BOM phù hợp tại **đầu payload**.
5. Decode đầy đủ range bằng stateful decoder hoặc đọc đủ bytes của field rồi decode một lần.

Không tự kết luận mọi `EF BB BF` xuất hiện giữa payload là BOM; nó cũng có thể là byte biểu diễn `U+FEFF` có chủ ý trong nội dung.

## 22. Khi nào decode từng chunk độc lập là hợp lệ?

Không phải mọi phân khối đều sai. Nếu **giao thức định nghĩa từng chunk là một chuỗi UTF-8 độc lập và hoàn chỉnh**, có length prefix rõ và sender bảo đảm không cắt một encoding sequence, thì có thể giải mã từng message riêng.

Ví dụ:

```text
Frame 1: [length=3]["☕" UTF-8 3 bytes]
Frame 2: [length=4]["😀" UTF-8 4 bytes]
```

Reader phải đọc đủ 3 hoặc 4 byte rồi mới tạo String. Nếu tầng network lại chia frame thành nhiều lần read, phải gom **đủ frame bytes** trước. Phần được phép decode độc lập là **message boundary do protocol định nghĩa**, không phải boundary do socket/file buffer ngẫu nhiên.

Một số text protocols như JSON Lines có delimiter rõ; cần parser xác định delimiter đúng theo charset và bảo vệ line length. Với các ký tự Unicode nhiều byte, một byte không thể được tự do hiểu là newline nằm giữa UTF-8 multibyte sequence; nhưng cần đảm bảo parser không nhầm framing và encoding theo một byte order khác.

## 23. Sai lầm thường gặp và cách sửa

1. **`new String(buffer, 0, n, UTF_8)` mỗi lần read byte rồi nối chuỗi.** Sai khi code point bị chia; dùng `InputStreamReader` hoặc `CharsetDecoder` stateful.
2. **Dùng `String.length()` làm số byte ghi lên wire.** Sai với UTF-8; encode trước rồi dùng `byte[].length`.
3. **Coi Java `char` là code point.** Không đúng với surrogate pair; dùng `codePoints()` hoặc API Character thích hợp.
4. **Gọi `ByteBuffer.clear()` khi decoder trả UNDERFLOW còn byte chưa đủ.** Làm mất bytes; dùng `compact()`.
5. **Quên `flip()` trước khi decode.** Decoder có thể đọc sai phạm vi hay không đọc gì.
6. **Không drain `CharBuffer` sau OVERFLOW.** Vòng lặp không tiến hoặc mất output.
7. **Gọi `decode(..., true)` sau mỗi chunk.** Khi chunk kết thúc giữa code point, decoder tưởng đó là EOF và báo malformed.
8. **Không gọi `flush` sau `decode(..., true)`.** Với các decoder stateful, có thể bỏ mất output/bước kết thúc.
9. **Sử dụng một CharsetDecoder cho nhiều stream cùng lúc.** Decoder có state và không được chia sẻ tùy tiện giữa các stream/thread.
10. **Không `reset()` decoder trước khi tái sử dụng cho một stream độc lập mới.** State và quy trình decoding có thể không còn hợp lệ.
11. **Coi `CodingErrorAction.REPLACE` là truyền dữ liệu chính xác.** Replacement đã làm mất thông tin, không dùng cho nội dung cần round-trip.
12. **Bỏ qua malformed bytes (`IGNORE`) mà không đánh giá tác động nghiệp vụ.** Có thể làm thay đổi identifiers/headers.
13. **Mặc định mọi file Unicode có BOM.** UTF-8 BOM là tùy chọn và nhiều format cấm BOM.
14. **Xóa U+FEFF ở mọi vị trí.** Nếu cần strip, chỉ xử lý phần đầu đúng theo spec.
15. **Nhầm BOM UTF-16BE/LE.** Endian sai làm nội dung vô nghĩa.
16. **Nhận diện UTF-16 trước UTF-32 khi hỗ trợ cả hai.** UTF-32LE BOM có prefix FF FE giống UTF-16LE; cần quy tắc match dài nhất.
17. **Cho rằng raw UTF-8 bytes của NFC và NFD giống nhau.** Có thể hiển thị giống nhưng byte/code point khác.
18. **Dựa vào log chuỗi đã decode để xác định byte lỗi.** Replacement có thể che mất byte gốc; quan sát hex an toàn.
19. **Đọc cả file không tin cậy bằng `readAllBytes` trước khi decode.** Dễ cạn bộ nhớ; dùng streaming với quota.
20. **Tưởng `InputStreamReader` trả grapheme clusters nguyên vẹn ở mỗi lần read.** Reader trả `char` units; tầng sau vẫn phải quản lý surrogate/grapheme khi chia char[].
21. **Không kiểm tra EOF cuối file.** UTF-8 bị cắt một nửa chỉ hiện lỗi khi biết chắc không còn byte nữa.
22. **Parser JSON/XML/CSV không thống nhất charset giữa writer/reader.** Cần xác định từ spec, không từ môi trường ngẫu nhiên.
23. **Normalize tất cả text mà không xem hợp đồng.** Có thể thay chữ ký, identifier hoặc dữ liệu cần bảo toàn bytes.
24. **Đoán charset từ extension file.** Tên file không cam kết content encoding.
25. **Cho rằng read của network trùng message boundary.** TCP là byte stream, phải framing riêng.

## 24. Bài tập thực hành

**Bài 1 — Đổi kích thước chunk.** Sửa ví dụ `UnicodeBoundaryDemo` từ 2 byte/lượt thành 1, 3, 4, 5 byte/lượt. Chứng minh `Split-safe equals: true` trong mọi lần nếu file và charset đúng. Không tạo decoder mới cho mỗi vòng.

**Bài 2 — Hỏng byte cuối.** Lấy UTF-8 của `"😀"` và bỏ byte cuối. Dùng `CharsetDecoder` với `REPORT`, gọi `decode(..., true)` khi EOF và xác nhận lỗi malformed. Sau đó thử `REPLACE` để thấy output không còn nguyên bản.

**Bài 3 — UTF-8 BOM bị chia ba lượt.** Ghi byte `EF BB BF 41`, dùng InputStream chỉ trả từng byte một. Đảm bảo detector không nhầm `EF` đơn lẻ là BOM hoàn chỉnh, và output sau policy là `A`.

**Bài 4 — UTF-16BE và UTF-16LE.** Tạo byte sequences của `"A☕"` với `UTF_16BE` và `UTF_16LE`. So sánh hex, giải mã bằng charset đúng, sau đó giải mã nhầm để quan sát khác biệt.

**Bài 5 — Cặp surrogate.** Đọc Unicode emoji thành Java char[]. Chia char[] ngay giữa high và low surrogate, sau đó thiết kế bộ xử lý codepoint giữ high surrogate tới lần nhận tiếp theo.

**Bài 6 — Binary length prefix.** Viết protocol `int32 byteLength + UTF-8 payload`. Truyền text `"Cà phê ☕"`, thử chia payload thành từng read ngẫu nhiên, kiểm tra cuối cùng String bằng bản gốc.

**Bài 7 — Normalization.** So sánh `"\u00E9"` và `"e\u0301"` về `equals`, số code point, byte UTF-8 và kết quả sau `Normalizer.Form.NFC`.

**Bài 8 — Diagnostics.** Khi phát hiện malformed UTF-8, log charset dự kiến, byte offset và chuỗi byte hex **đã redacted**, không log toàn bộ nội dung nhạy cảm. Viết test cho bytes `80` và `E2 98`.

## 25. Câu hỏi tự kiểm tra

**Câu 1.** Đọc hai byte `E2 98` rồi kết thúc **một lần read**, có đủ lý do báo UTF-8 hỏng chưa?  
**Không**, nếu byte stream chưa EOF. Đây có thể là prefix hợp lệ của code point ba byte. Decoder cần chờ byte tiếp theo.

**Câu 2.** Nếu đã biết **EOF thực sự** và chỉ còn `E2 98`, kết quả strict decoding?  
**Malformed input**, vì sequence chưa hoàn chỉnh.

**Câu 3.** Vì sao `new String(chunk1,UTF_8)+new String(chunk2,UTF_8)` có thể sai?  
**Vì mỗi decoder độc lập** không nhớ byte đầu của một code point nằm ở chunk trước.

**Câu 4.** `ByteBuffer.compact()` giúp gì trong một incremental decoder?  
**Giữ byte chưa được consume** khi đang chờ chunk tiếp theo và chuẩn bị vùng trống để append.

**Câu 5.** `CoderResult.OVERFLOW` là dữ liệu UTF-8 sai phải không?  
**Không.** Thường là output `CharBuffer` hết chỗ, cần drain rồi decode tiếp.

**Câu 6.** Chuỗi `"😀"` có `length() == 1` không?  
**Không**. `String.length() == 2` do cặp surrogate UTF-16, nhưng có 1 Unicode code point.

**Câu 7.** UTF-8 BOM gồm những byte nào?  
**EF BB BF**. UTF-8 không cần BOM để xác định endian.

**Câu 8.** Java UTF-8 decoder có luôn tự bỏ `EF BB BF` ở đầu không?  
**Không**. Nhiều API giải mã chuẩn đưa nó thành ký tự `U+FEFF`; cần policy rõ.

**Câu 9.** Có nên gọi `text.replace("\uFEFF","")` để xử lý BOM không?  
**Không theo mặc định**, vì thao tác đó xóa U+FEFF ở mọi vị trí. BOM là dấu ở **đầu stream/field**, không phải mọi ký tự U+FEFF.

**Câu 10.** Protocol gửi chuỗi UTF-8 có prefix byte length. Dùng `String.length()` được không?  
**Không**; phải encode rồi lấy số byte thực tế.

**Câu 11.** `InputStreamReader` giải mã UTF-8 đúng qua nhiều byte chunks có tự bảo đảm các lần `read(char[])` không chia đôi cặp surrogate không?  
**Không nên dựa vào điều đó**; tầng xử lý char units vẫn phải chịu trách nhiệm về code points/grapheme boundaries.

**Câu 12.** Vì sao `REPORT` hữu ích trong kiểm thử dữ liệu đầu vào?  
**Nó làm lỗi encoding lộ rõ**, thay vì âm thầm chèn replacement rồi khiến dữ liệu sai được xem là hợp lệ.

**Câu 13.** Hai chuỗi nhìn như `é` có nhất thiết có cùng bytes UTF-8 không?  
**Không**. Một chuỗi có thể là U+00E9, chuỗi kia là U+0065 U+0301.

**Câu 14.** Khi hỗ trợ UTF-16 và UTF-32 BOM, tại sao cần kiểm tra BOM dài nhất trước?  
**Để không hiểu nhầm prefix `FF FE` của UTF-32LE** là BOM hoàn chỉnh của UTF-16LE.

## 26. Checklist triển khai và kiểm thử

- [ ] Xác định chính xác charset từ giao thức hoặc metadata đáng tin cậy, ưu tiên UTF-8 khi spec cho phép.
- [ ] Ghi rõ encoding ở writer và reader; không phụ thuộc charset mặc định.
- [ ] Phân biệt byte offset, Java UTF-16 index, code point và grapheme.
- [ ] Không decode riêng từng byte chunk chưa chắc chứa một message hoàn chỉnh.
- [ ] Với text stream đơn giản, dùng `InputStreamReader`/`BufferedReader` cùng charset.
- [ ] Với binary protocol/ByteBuffer, dùng stateful `CharsetDecoder` và giữ byte dư bằng `compact()`.
- [ ] Đảm bảo `decode(..., true)` **chỉ khi EOF**, sau đó `flush` đúng protocol.
- [ ] Drain output buffer khi OVERFLOW, không mất byte/input state.
- [ ] Chọn `REPORT` cho dữ liệu yêu cầu chính xác; log và xử lý lỗi rõ.
- [ ] Đặt giới hạn bytes/ký tự/CPU/timeouts phù hợp nguồn tin cậy.
- [ ] Có policy BOM: cho phép, loại ở đầu, hoặc từ chối theo định dạng.
- [ ] Với UTF-16, xác định BE/LE; không đoán BOM khi format đã cố định charset.
- [ ] Không strip `U+FEFF` ở giữa nội dung một cách mù quáng.
- [ ] Khi framing network, tính length theo byte sau encoding.
- [ ] Kiểm thử với tiếng Việt, emoji, dấu tổ hợp, UTF-8 BOM, EOF giữa code point.
- [ ] Kiểm thử mọi kích thước chunk nhỏ: 1, 2, 3, 4, ... byte và random splits.
- [ ] So sánh byte round-trip khi yêu cầu bảo toàn chính xác.
- [ ] Đảm bảo normalization theo hợp đồng; không tự động biến NFD thành NFC khi không được phép.
- [ ] Định nghĩa hành vi trước malformed bytes và sai encoding.
- [ ] Không log raw data nhạy cảm trong thông điệp chẩn đoán.

## 27. Tóm tắt

- **Unicode là tập mã code point; UTF-8/UTF-16 là cách biểu diễn chúng thành các đơn vị lưu/truyền.**
- **Một lần đọc byte không tương đương một Unicode character.** UTF-8 từ 1–4 byte/code point, nên phải cho phép sequence nằm qua nhiều chunks.
- **Không dùng `new String` độc lập cho từng chunk tùy ý.** Dùng `InputStreamReader` hoặc `CharsetDecoder` giữ state.
- **`flip()` để chuyển sang đọc; `compact()` giữ byte chưa đủ; `clear()` có thể làm mất chúng.**
- **Báo EOF chỉ khi stream kết thúc**, xử lý decode final + flush và báo malformed khi input bị cắt.
- **BOM UTF-8 là EF BB BF**, tùy chọn; UTF-16 BOM cho biết endian. Policy BOM phải theo format, chỉ áp vào đầu stream/payload.
- **Byte count ≠ `String.length()` ≠ code point count**, đặc biệt khi có tiếng Việt/emoji/combining marks.
- **Kiểm thử ranh giới và error policy là bắt buộc** để không biến dữ liệu sai thành các ký tự thay thế mà không ai phát hiện.

### Tài liệu API Java SE 17

- [CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html)
- [CoderResult](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CoderResult.html)
- [CodingErrorAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CodingErrorAction.html)
- [InputStreamReader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html)
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html)
- [StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html)
- [String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Normalizer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-005">Tệp rỗng, tệp lớn, EOF sớm và bản ghi không hoàn chỉnh</a>

<details>
<summary>Xem chi tiết</summary>

# Java I/O: kiểm thử tệp rỗng, tệp lớn, EOF sớm và bản ghi không hoàn chỉnh

## 1. Vì sao phải kiểm thử các trường hợp biên?

Code đọc/ghi file có thể chạy đúng với một file nhỏ, đầy đủ dữ liệu, nhưng hỏng khi dữ liệu ít hơn mong đợi hoặc vượt giới hạn bộ nhớ. Những lỗi này thường xuất hiện khi tải lên tệp, xử lý log, đọc bản ghi nhị phân, truyền file, phục hồi sau crash hay đọc file được ứng dụng khác ghi đồng thời.

Những trường hợp tiêu biểu:

- File **0 byte** không có header, nhưng format yêu cầu header.
- Header cho biết payload dài **0 byte**, đây có thể là **một bản ghi hợp lệ**.
- Một lần `read(...)` trả ít byte hơn buffer yêu cầu, không phải EOF.
- EOF xuất hiện khi header mới có 1–3 byte, hoặc khi payload chưa đủ.
- File dài nhiều GiB nhưng code ép `long` sang `int` rồi cấp phát một mảng.
- Length prefix bị âm hoặc quá lớn; cấp phát ngay có thể gây `NegativeArraySizeException`/`OutOfMemoryError`.
- Reader mở file đang được writer append/truncate; `Files.size(...)` không còn mô tả snapshot ổn định.
- Writer crash giữa record, khiến file chứa header hợp lệ nhưng payload mới chỉ ghi một phần.
- Code nhầm `read()==0` với `read()==-1`, hoặc gọi `ByteBuffer.getInt()` trước khi đủ bốn byte.
- Bản ghi đủ byte nhưng bị hỏng dữ liệu; chỉ kiểm tra kích thước không đủ phát hiện.

Bài này xây dựng một contract đơn giản để giải thích, kèm chương trình Java 17+ tự tạo fixture và kiểm tra **đúng trường hợp nào phải thành công, trường hợp nào phải thất bại**.

## 2. Định dạng nhị phân ví dụ và nguyên tắc EOF

Giả sử file chỉ chứa **đúng một bản ghi**:

~~~text
+----------------------------+--------------------------+
| Header: 4 byte, big-endian | Payload: length byte     |
| int length >= 0            | có thể rỗng nếu length=0 |
+----------------------------+--------------------------+
   file offsets [0,4)             file offsets [4,4+N)
~~~

Ví dụ:
- `00 00 00 02 4F 4B`: payload dài 2 byte, nội dung ASCII `OK`.
- `00 00 00 00`: **một record hoàn chỉnh** chứa payload rỗng.
- File 0 byte: **không có record**, lỗi nếu định dạng buộc phải có một record.
- `00 00`: **header bị cắt**, vì mới có 2/4 byte.
- `00 00 00 05 41 42`: header công bố 5 byte payload, thực tế chỉ có 2; **record thiếu**.

Cần phân biệt **EOF hợp lệ ở ranh giới** và **EOF sớm trong record**:

~~~text
Multi-record:
  [record #1 complete][record #2 complete][EOF]
    -> Kết thúc hợp lệ nếu format cho phép nhiều record.

  [record #1 complete][2/4 byte header #2][EOF]
    -> TRUNCATED_HEADER.

  [record #1 complete][header #2 says 5][2 payload bytes][EOF]
    -> TRUNCATED_PAYLOAD.
~~~

Chính sách của chương trình demo là **single-record file**, nên sau payload còn bất kỳ byte nào cũng là lỗi `TRAILING_BYTES`. Parser multi-record sẽ khác: tiếp tục tìm header kế tiếp, và chỉ coi EOF là bình thường khi chưa nhận byte nào của header tiếp theo.

## 3. EOF, short read và read trả 0 không giống nhau

### InputStream

`InputStream.read()` trả:
- `0..255` khi đọc được một byte.
- `-1` khi hết stream.

`InputStream.read(byte[], off, len)` trả số byte đã đọc, có thể **nhỏ hơn len**. Khi `len == 0`, nó có thể trả `0` ngay. Không giả định một lệnh `read` luôn đọc đủ length.

`InputStream.readNBytes(int n)` trả mảng có thể **ngắn hơn n** nếu EOF đến trước. Nếu cần chính xác n byte, kiểm tra mảng/giá trị thực nhận. `DataInputStream.readFully(byte[])` sẽ cố đọc đủ hoặc ném `EOFException` khi không đủ.

~~~java
// Minh họa: in là DataInputStream, expected được kiểm tra.
if (expected < 0 || expected > MAX_RECORD_BYTES) {
    throw new IOException("Invalid length");
}
byte[] payload = new byte[expected];
in.readFully(payload); // Thiếu payload => EOFException.
~~~

Đoạn trên chưa độc lập vì `expected`, `MAX_RECORD_BYTES` và `in` phải được định nghĩa. Không dùng `readFully` với length prefix chưa kiểm tra vì array đã được cấp phát trước lời gọi.

### FileChannel và ByteBuffer

`FileChannel.read(ByteBuffer dst)` trả:
- Giá trị dương khi có thêm n byte và cập nhật `dst.position()`.
- `-1` khi EOF.
- `0` khi không đọc được byte trong lượt đó; với file thông thường đang đọc buffer còn chỗ, điều này không phải tín hiệu EOF.

~~~java
int n = channel.read(buffer);
if (n < 0) {
    // EOF.
} else if (n == 0) {
    // Chưa có tiến triển.
} else {
    // n byte mới được nạp, position tăng n.
}
~~~

**Không biến `0` thành `-1`**. Với `SocketChannel` ở non-blocking mode, 0 là tình huống thường gặp; cần chờ Selector/readiness thay vì spin. Với `FileChannel`, code kiểm thử có thể fail rõ ràng nếu lặp mà không tiến triển để tránh treo vô hạn.

### Read-exactly là một vòng lặp, không phải một lần read

~~~java
// Minh họa: FileChannel đang mở, dst đã cấp phát.
static int readUpTo(FileChannel channel, ByteBuffer dst)
        throws IOException {
    int total = 0;
    while (dst.hasRemaining()) {
        int n = channel.read(dst);
        if (n == -1) {
            break;
        }
        if (n == 0) {
            throw new IOException("no read progress");
        }
        total += n;
    }
    return total;
}
~~~

Hàm trả số byte thực nhận. Caller biết độ dài kỳ vọng và phân loại:
- Header cần 4, nhận 0 ở lần đầu ⇒ EMPTY_FILE hoặc normal EOF tùy contract.
- Header cần 4, nhận 1–3 rồi EOF ⇒ TRUNCATED_HEADER.
- Payload cần N, nhận k nhỏ hơn N rồi EOF ⇒ TRUNCATED_PAYLOAD.

## 4. ByteBuffer: position, limit và flip

Một lỗi phổ biến:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);
channel.read(header);
int length = header.getInt(); // Sai: chưa biết đủ byte, chưa flip.
~~~

Khi ghi vào ByteBuffer, `position()` dịch về phía cuối. Phải kiểm tra đủ dữ liệu rồi `flip()` để chuyển sang chế độ đọc:

~~~java
ByteBuffer header = ByteBuffer.allocate(4);
int actual = readUpTo(channel, header);
if (actual != 4) {
    throw new EOFException("incomplete header: " + actual + "/4");
}
header.flip();
int length = header.getInt();
~~~

Trong parser nhiều record, `compact()` giữ những byte còn lại chưa parse sau một lượt đọc, trong khi `clear()` sẽ làm vùng dữ liệu còn lại bị coi như không cần giữ nữa. Dùng sai `clear()` khi buffer đã chứa nửa header của record kế tiếp sẽ gây mất dữ liệu dù file không bị hỏng.

## 5. Validate length trước khi allocate

Header nhị phân có thể chứa số âm hoặc cực lớn. Đừng viết:

~~~java
int length = header.getInt();
byte[] bytes = new byte[length]; // Không giới hạn, không an toàn.
~~~

Thay bằng:

~~~java
int length = header.getInt();
if (length < 0 || length > MAX_RECORD_BYTES) {
    throw new IOException("invalid record length: " + length);
}
byte[] bytes = new byte[length];
~~~

`MAX_RECORD_BYTES` là giới hạn **theo giao thức và bộ nhớ ứng dụng**, không phải hằng số chuẩn của Java. Ví dụ demo đặt MAX=1024 để fixture dễ đọc; service thực cần định nghĩa theo workload và quota.

Các điều kiện quan trọng:
- `length == 0` có thể là payload rỗng hợp lệ, **khác file rỗng**.
- `length < 0` phải reject nếu format dùng signed nonnegative int.
- `length > MAX` phải reject **trước** cấp phát.
- Nếu cộng offset/length, tính bằng `long` để tránh tràn int.
- Nếu parser hỗ trợ unsigned 32-bit length, phải đặc tả riêng bằng `Integer.toUnsignedLong(length)` rồi kiểm giới hạn trước; **không tự suy ra signed và unsigned tương đương**.
- Không được báo thành công chỉ vì header hợp lệ; vẫn phải kiểm đủ payload và checksum nếu có.

## 6. Tệp rỗng và payload rỗng: bộ test tối thiểu

| Fixture | Kích thước file | Kết quả theo single-record contract |
|---|---:|---|
| File hoàn toàn rỗng | 0 | EMPTY_FILE |
| Header length 0 | 4 | COMPLETE, payload rỗng |
| Header length 0, thêm 1 byte | 5 | TRAILING_BYTES |
| Header chỉ có byte `00` | 1 | TRUNCATED_HEADER(1/4) |
| Header có 3 byte | 3 | TRUNCATED_HEADER(3/4) |
| Header length 1 + một byte | 5 | COMPLETE |
| Header length 1 + không có payload | 4 | TRUNCATED_PAYLOAD(0/1) |

Đây là test các ranh giới của **format**, không phải lỗi Files API. `Files.createTempFile` tạo file rỗng hợp lệ ở tầng filesystem; parser mới là nơi quyết định empty file có được chấp nhận hay không.

Không dùng `catch(EOFException e) { return new byte[0]; }` vì điều đó che giấu record thiếu và khiến payload rỗng hợp lệ không thể phân biệt với dữ liệu hỏng.

## 7. File lớn và nguy cơ tràn kiểu dữ liệu

Một file 3 GiB có kích thước lớn hơn `Integer.MAX_VALUE`. Trong Java:

- `Files.size(path)` trả `long`.
- `FileChannel.size()` trả `long`.
- `FileChannel.position(long)` và `read(dst, long offset)` xử lý offset kiểu long.
- `ByteBuffer.allocate(int capacity)` giới hạn bởi int và bộ nhớ.
- `Files.readAllBytes(path)` có giới hạn mảng/bộ nhớ, không phù hợp với file khổng lồ.

Sai:

~~~java
int size = (int) Files.size(path);
byte[] all = new byte[size];
~~~

Ép kiểu có thể cho số âm hoặc kích thước sai. Ngay cả file dưới 2 GiB, một mảng lớn vẫn có thể gây OOM.

Đúng hơn, đọc theo block:

~~~java
long total = 0L;
byte[] buffer = new byte[16 * 1024];

try (InputStream in = Files.newInputStream(path)) {
    int n;
    while ((n = in.read(buffer)) != -1) {
        if (n == 0) {
            throw new IOException("no stream progress");
        }
        total += n;
        // Xử lý/hash/chuyển từng chunk ở đây.
    }
}
~~~

Bộ nhớ đệm khoảng 16 KiB bất kể file có 5 MiB hay nhiều GiB, chưa kể bộ nhớ parser. Nếu file hoặc stream không đáng tin, đặt **quota tổng bytes** và deadline, không chỉ giới hạn kích thước từng chunk.

### Sparse files và test lớn hơn 2 GiB

Một số filesystem hỗ trợ sparse files: kích thước logic lớn mà không cần chiếm mọi block đĩa. Test với `RandomAccessFile.setLength(long)` hoặc ghi ở offset rất cao có thể kiểm logic long offset, nhưng:
- Không phải provider nào cũng hỗ trợ sparse theo cùng cách.
- File size logic lớn không chứng minh code streaming đúng với toàn bộ dữ liệu thực tế.
- Không được tạo fixture nhiều GiB bừa bãi trên CI hoặc volume sản xuất.
- Dùng test riêng cho long offset và test riêng cho dữ liệu streaming/throughput.

## 8. Chương trình Java 17+ hoàn chỉnh: tám trường hợp biên

Chương trình sau tạo fixture trong thư mục tạm, đọc **một record duy nhất** dạng `[int32 length][payload]` và in kết quả ổn định.

Các case gồm: file rỗng, payload rỗng, record hoàn chỉnh, header cắt, payload cắt, length quá lớn, dữ liệu thừa và file 5 MiB đọc streaming.

Lưu là **`IoBoundaryCasesDemo.java`**:

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Stream;

public class IoBoundaryCasesDemo {
    private static final int MAX_RECORD_BYTES = 1024;

    private static int readUpTo(FileChannel ch, ByteBuffer dst)
            throws IOException {
        int total = 0;
        while (dst.hasRemaining()) {
            int n = ch.read(dst);
            if (n < 0) {
                break; // EOF.
            }
            if (n == 0) {
                throw new IOException("no read progress");
            }
            total += n;
        }
        return total;
    }

    private static byte[] readOneRecord(Path file)
            throws IOException {
        try (FileChannel ch = FileChannel.open(
                file, StandardOpenOption.READ)) {

            ByteBuffer header = ByteBuffer.allocate(4);
            int headerCount = readUpTo(ch, header);

            if (headerCount == 0) {
                throw new EOFException("empty file");
            }
            if (headerCount < 4) {
                throw new EOFException(
                        "incomplete header: " + headerCount + "/4");
            }

            header.flip();
            int length = header.getInt();

            if (length < 0 || length > MAX_RECORD_BYTES) {
                throw new IOException(
                        "invalid record length: " + length);
            }

            byte[] payload = new byte[length];
            int payloadCount = readUpTo(
                    ch, ByteBuffer.wrap(payload));

            if (payloadCount < length) {
                throw new EOFException(
                        "incomplete payload: "
                        + payloadCount + "/" + length);
            }

            // Theo contract, file chỉ chứa một record.
            if (ch.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("unexpected trailing bytes");
            }

            return payload;
        }
    }

    private static byte[] framed(byte[] payload) {
        return ByteBuffer.allocate(4 + payload.length)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    private static void testCase(
            Path dir, String label, byte[] input)
            throws IOException {

        Path file = Files.createTempFile(dir, "case-", ".bin");
        Files.write(file, input);

        try {
            byte[] value = readOneRecord(file);
            System.out.println(label + ": ok="
                    + new String(value, StandardCharsets.US_ASCII));
        } catch (IOException ex) {
            System.out.println(label + ": error="
                    + ex.getMessage());
        }
    }

    private static long largeStreamingCase(Path dir)
            throws IOException {
        Path file = Files.createTempFile(dir, "large-", ".bin");
        byte[] writeBuffer = new byte[8192];
        Arrays.fill(writeBuffer, (byte) 'x');

        try (OutputStream out = Files.newOutputStream(file)) {
            for (int i = 0; i < 640; i++) {
                out.write(writeBuffer);
            }
        }

        byte[] readBuffer = new byte[16 * 1024];
        long total = 0L;
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(readBuffer)) != -1) {
                if (n == 0) {
                    throw new IOException("no stream progress");
                }
                total += n;
            }
        }
        return total;
    }

    public static void main(String[] args)
            throws IOException {
        Path dir = Files.createTempDirectory("io-boundary-");

        try {
            testCase(dir, "empty-file", new byte[0]);
            testCase(dir, "empty-payload",
                    framed(new byte[0]));
            testCase(dir, "complete", framed(
                    "OK".getBytes(StandardCharsets.US_ASCII)));

            testCase(dir, "short-header",
                    new byte[] {0, 0});

            testCase(dir, "short-payload",
                    ByteBuffer.allocate(6)
                            .putInt(5)
                            .put((byte) 'A')
                            .put((byte) 'B')
                            .array());

            testCase(dir, "too-large",
                    ByteBuffer.allocate(4)
                            .putInt(MAX_RECORD_BYTES + 1)
                            .array());

            testCase(dir, "trailing",
                    ByteBuffer.allocate(6)
                            .putInt(1)
                            .put((byte) 'Q')
                            .put((byte) '!')
                            .array());

            System.out.println("large-file: bytes="
                    + largeStreamingCase(dir));
        } finally {
            // Directory chỉ chứa fixture do demo vừa tạo.
            try (Stream<Path> entries = Files.list(dir)) {
                for (Path p : entries.toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(dir);
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac IoBoundaryCasesDemo.java
java IoBoundaryCasesDemo
~~~

**Stdout mong đợi khi thao tác với file tạm thành công:**

~~~text
empty-file: error=empty file
empty-payload: ok=
complete: ok=OK
short-header: error=incomplete header: 2/4
short-payload: error=incomplete payload: 2/5
too-large: error=invalid record length: 1025
trailing: error=unexpected trailing bytes
large-file: bytes=5242880
~~~

### Giải thích output và tính đúng đắn

1. `empty-file`: 0/4 byte header. Trong contract một record, không thể coi là bản ghi hợp lệ.
2. `empty-payload`: header đủ 4 byte, length=0, payload là mảng rỗng; output có `ok=` không kèm ký tự.
3. `complete`: 4 byte header và hai byte `OK`, đọc hết file sau payload.
4. `short-header`: chỉ 2 byte, parser báo chính xác 2/4 thay vì getInt khi thiếu.
5. `short-payload`: header công bố 5 byte, chỉ có hai byte A/B; parser báo 2/5.
6. `too-large`: length=1025 vượt MAX=1024; reject **trước allocate**.
7. `trailing`: length=1 chỉ nhận Q, byte `!` thừa làm file sai single-record contract.
8. `large-file`: 640 lần ghi khối 8192 byte = **5.242.880 byte**, đọc bằng buffer 16 KiB, không cấp phát mảng theo file size.

Chương trình cố tình dùng `FileChannel` bình thường, không dùng `SocketChannel` non-blocking. Ở socket không blocking, `read()==0` là trạng thái thường gặp và cần quay lại event loop; không ném IOException như helper của demo.

Dữ liệu demo chỉ dùng ASCII và fixed big-endian header để stdout không phụ thuộc locale/charset hệ điều hành. Thư mục và file tạm được dọn sau cả đường thành công lẫn exception thông thường; JVM crash/kill vẫn có thể để lại fixture, như mọi loại cleanup dựa trên finally.

## 9. Đọc một file chứa nhiều record

Nếu file có các record nối tiếp, ta không thể dùng cùng hàm `readOneRecord` bởi nó cấm trailing bytes. Thay vào đó:

~~~text
LOOP:
   đọc header 4 byte
      - 0 byte rồi EOF      -> END_OF_STREAM (bình thường)
      - 1..3 byte rồi EOF   -> TRUNCATED_HEADER (lỗi)
      - đủ 4 byte          -> kiểm length
   đọc payload length byte
      - thiếu rồi EOF       -> TRUNCATED_PAYLOAD (lỗi)
      - đủ                  -> COMPLETE(frame), xử lý record
   quay lại LOOP
~~~

Nên có các kiểu kết quả hoặc error codes rõ ràng:
`END_OF_STREAM_AT_BOUNDARY`, `TRUNCATED_HEADER`, `INVALID_LENGTH`, `TRUNCATED_PAYLOAD`, `CHECKSUM_MISMATCH` và `IO_FAILURE`.

Nếu đó là append-only log, EOF tại tail cần xét hoàn cảnh: writer có thể **đang tiếp tục ghi**, khác với một snapshot file đã commit nhưng bị thiếu. Recovery có thể cho phép bỏ một tail chưa commit **chỉ nếu giao thức log đã thiết kế như thế**, không tự ý truncate mọi file gặp EOF.

## 10. Partial write và file bị cắt giữa chừng

Nguyên nhân record thiếu thường đến từ phía ghi:

~~~java
// Mã minh họa: channel FileChannel mở WRITE, buffer đã chuẩn bị.
while (buffer.hasRemaining()) {
    int n = channel.write(buffer);
    if (n <= 0) {
        throw new IOException("no write progress");
    }
}
~~~

Một lần `write` không chắc ghi hết buffer. Nếu writer ghi header length=100 rồi chỉ ghi 60 byte payload trước khi crash, reader sẽ gặp EOF tại 60/100. Đừng biến lỗi đó thành một record “hợp lệ nhưng ngắn”.

Một thiết kế publish file hoàn chỉnh thường là:

1. Tạo file tạm cùng filesystem đích.
2. Ghi đủ dữ liệu, xử lý partial writes.
3. Kiểm tra độ dài, checksum và content format.
4. Chủ động `channel.force(...)` nếu cần durability theo hợp đồng storage tương ứng.
5. Đóng writer.
6. Công bố qua `Files.move(temp, target, ATOMIC_MOVE)` khi được hỗ trợ.
7. Reader vẫn phải kiểm record boundaries và checksum.

`ATOMIC_MOVE` **không tự đảm bảo durability sau crash**, cũng không phải transaction nhiều file. Trên filesystem mạng, cần xem xét thêm consistency và tính bền vững của server.

## 11. Checksum: đủ byte chưa chắc đúng nội dung

Một record có đủ header + payload nhưng bị sửa một byte vẫn có thể vượt qua mọi kiểm tra độ dài.

~~~text
Expected: length=4, payload=01 02 03 04
Actual:   length=4, payload=01 02 03 05
Cả hai đều có file size=8, nhưng nội dung khác.
~~~

Phương pháp thường dùng:
- **CRC32/CRC32C** để phát hiện nhiều lỗi ngẫu nhiên trong dữ liệu.
- **SHA-256** nếu cần hash mạnh để xác minh một nội dung cụ thể.
- **HMAC/chữ ký số** khi cần chống sửa có chủ ý và đối thủ có thể thay đổi cả file lẫn checksum.

Thứ tự trong parser nên là: kiểm magic/version nếu có → đọc đủ header → validate length → đọc đủ payload → validate checksum/schema → công nhận record. Đừng đưa dữ liệu vào business logic trước khi kiểm tra những invariant cần thiết.

## 12. Kiểm thử short read có chủ đích

FileChannel local có thể tình cờ làm đầy buffer trong một lần, khiến bug “chỉ đọc một lần” không bộc lộ. Dùng một wrapper trả tối đa 2 byte/lượt:

~~~java
// Mã minh họa: source là InputStream, thêm import cần thiết.
InputStream shortReads = new FilterInputStream(source) {
    @Override
    public int read(byte[] dst, int off, int len)
            throws IOException {
        return super.read(dst, off, Math.min(len, 2));
    }
};
~~~

Với payload chứa 4-byte header và 5-byte body, wrapper có thể chia thành lượt 2+2+2+2+1 byte; parser đúng vẫn phải dựng lại đầy đủ.

**Short read khác early EOF:** short read được nối tiếp bằng bytes ở lần sau; early EOF là nguồn trả `-1` trước khi lượng byte bắt buộc đủ. Cần negative tests cho cả hai hành vi.

Một wrapper trả 0 liên tục (khi caller yêu cầu length dương) có thể gây busy loop trong parser viết sai. Test cần timeout hữu hạn để phát hiện vòng lặp không tiến triển.

## 13. Kích thước file không phải snapshot

Ví dụ:

~~~java
long observed = Files.size(path);
try (InputStream in = Files.newInputStream(path)) {
    // File có thể bị truncate hoặc append sau phép size().
}
~~~

Không dùng `Files.size()==expected` như bằng chứng file vẫn giữ nguyên khi đã bắt đầu đọc. Một writer khác có thể sửa, truncate, replace path hoặc làm reader gặp EOF sớm. Tùy use case:

- File immutable được publish sau hoàn tất.
- File locking/coordination khi cần nhiều tiến trình truy cập.
- Filesystem snapshot hoặc transactional storage nếu yêu cầu snapshot thật.
- Với tailer, giữ parser state và chính sách chờ/timeout cho record đang ghi.

Những điều này quan trọng hơn khi file được chia sẻ qua network filesystem với cache/visibility riêng; việc `read` chính xác vẫn chỉ đảm bảo đọc đúng bytes **mà hệ thống cung cấp**, không tự xác nhận storage snapshot toàn hệ thống.

## 14. Bảng chẩn đoán exception

| Triệu chứng | Nguyên nhân khả dĩ | Hướng kiểm tra |
|---|---|---|
| `EOFException` lúc header | File 0–3 byte hoặc EOF đúng boundary tùy format | Header bytes đã nhận |
| `EOFException` lúc payload | Length hợp lệ nhưng body thiếu | Declared vs actual |
| `BufferUnderflowException` | Buffer thiếu dữ liệu hoặc chưa flip | position/limit/remaining |
| `NegativeArraySizeException` | Length âm dùng trực tiếp | Validate trước allocate |
| `OutOfMemoryError` | Đọc file quá lớn trong một mảng | Streaming, limits |
| `IOException` khi read()==0 | Không tiến triển, helper file-specific | Blocking mode/provider |
| `ClosedChannelException` | Channel đóng giữa lúc dùng | Resource ownership |
| `NonReadableChannelException` | Channel mở không có READ | OpenOption |
| `AccessDeniedException` | Quyền file/ACL | Filesystem permissions |
| `NoSuchFileException` | File đã bị xóa/thay path | Concurrent operations |
| `FileSystemException` | Lỗi storage/mount/file busy | Cause, platform diagnostics |
| Dữ liệu decode sai | Charset/order/framing không thống nhất | Big-endian/UTF-8 và parser |
| Đủ byte nhưng nội dung sai | Corruption/overwrite/race | Checksum, concurrency |
| Parser treo CPU cao | Vòng read()==0 không thoát | Readiness/backoff/timeout |

Không bắt `IOException` rồi trả payload rỗng như trường hợp thành công. Lỗi đọc phải được tách khỏi payload length 0 thật sự.

## 15. Log và đo đạc để tái hiện lỗi

Thông tin nên có trong diagnostic log (không ghi nội dung nhạy cảm):

- Request ID hoặc job ID.
- Tên operation: open, read-header, read-payload, verify, write, close.
- Record offset trong file (kiểu long), byte đã nhận và byte mong đợi.
- Length prefix, giới hạn tối đa, file size quan sát.
- Exception class và cause, trạng thái channel/EOF.
- Số lượng retry, deadline nếu đọc từ nguồn bất đồng bộ.
- Trạng thái checksum/version và cleanup đã thực hiện.

Ví dụ thông điệp:

~~~text
job=42 op=read-payload offset=4 declared=100
received=60 status=TRUNCATED_PAYLOAD
~~~

Cố gắng dùng **mã lỗi có cấu trúc** như `EMPTY_FILE`, `TRUNCATED_HEADER`, `TRUNCATED_PAYLOAD`, `INVALID_LENGTH`, `TRAILING_BYTES`, `CHECKSUM_MISMATCH` thay vì để code nghiệp vụ parse chuỗi ngoại lệ.

## 16. Ma trận kiểm thử đề xuất

| Test | Input | Kỳ vọng |
|---|---|---|
| File rỗng | 0 byte | EMPTY_FILE nếu cần 1 record |
| Empty payload | Header N=0, không body | COMPLETE |
| Header 1 byte | EOF | TRUNCATED_HEADER 1/4 |
| Header 2 byte | EOF | TRUNCATED_HEADER 2/4 |
| Header 3 byte | EOF | TRUNCATED_HEADER 3/4 |
| Payload thiếu 1 byte | N-1/N | TRUNCATED_PAYLOAD |
| Payload thiếu toàn bộ | 0/N với N>0 | TRUNCATED_PAYLOAD |
| Length âm | -1 | INVALID_LENGTH |
| Length đúng MAX | Đủ bytes | COMPLETE |
| Length MAX+1 | Header vượt max | INVALID_LENGTH, không allocate |
| Trailing garbage | Thừa sau record | TRAILING_BYTES |
| Partial read | Wrapper 1–2 byte/lượt | COMPLETE nếu đủ |
| Read 0 nhiều lần | Simulated custom channel | Không spin vô hạn |
| File 5 MiB | Stream chunks | Đếm đúng, heap nhỏ |
| Long offset >2 GiB | Sparse fixture | Không cast int |
| Checksum mismatch | Body đủ nhưng sửa byte | REJECT |
| Writer crash giữa payload | Record tail thiếu | Recovery policy rõ |
| Concurrent truncate | File thay khi reader mở | Không báo complete sai |
| Permission error | Không được mở file | Báo I/O error |
| Disk full khi write | Ghi được một phần | Không publish file |
| EOF đúng giữa nhiều records | Không có header mới | END_OF_STREAM |
| EOF giữa header record tiếp | 1–3 byte | TRUNCATED_HEADER |

Tạo fixture bằng byte arrays hoặc file tạm riêng biệt giúp kết quả tái lập và không phụ thuộc file ở ngoài repo. Nếu dùng JUnit 5, `@TempDir` có thể tự cung cấp workspace cho mỗi test; vẫn nên xác nhận cleanup và resource closing.

## 17. Property-based testing và kiểm tra bất biến

Các thuộc tính cần chứng minh cho codec length prefix:

~~~text
decode(encode(payload)) == payload
    với 0 <= payload.length <= MAX.

Cắt encoded stream trước khi đủ 4 byte:
    TRUNCATED_HEADER hoặc EMPTY_FILE.

Cắt encoded stream sau header nhưng trước đủ payload:
    TRUNCATED_PAYLOAD.

Thay declared length bằng giá trị âm hoặc >MAX:
    INVALID_LENGTH trước khi allocate.

Nối một byte bất kỳ vào record hoàn chỉnh:
    TRAILING_BYTES khi contract chỉ cho một record.
~~~

Đây là **pseudo-spec**, không phải Java syntax. Test giá trị biên 0, 1, MAX, MAX+1, Integer.MAX_VALUE, số âm và dữ liệu nhiều byte UTF-8 (byte length khác character count).

Nếu parser xử lý string UTF-8, length prefix thường nên đếm **byte** sau encode, không phải số ký tự Java `String.length()`; ký tự tiếng Việt có thể chiếm nhiều byte.

## 18. Hiệu năng và an toàn của chương trình đọc file lớn

1. Đọc theo khối cố định, không cấp phát toàn file.
2. Dùng `long` cho tổng bytes và offsets.
3. Áp quota tổng số bytes và số lượng records.
4. Không vô hạn lặp khi read trả 0.
5. Hạn chế số file open và số I/O pending.
6. Kiểm tra bộ nhớ của cả ứng dụng, không chỉ một ByteBuffer.
7. Với non-blocking socket, dùng Selector và giữ state giữa các event.
8. Không dùng `MappedByteBuffer` đơn lẻ để biểu diễn file vượt phạm vi capacity int; chia mapping thành vùng nếu cần.
9. Có chính sách timeout/retry khác nhau cho transient I/O errors và dữ liệu malformed.
10. Không dùng atomic rename hay `force()` để thay kiểm tra header/payload/checksum.

## 19. Những nhầm lẫn phổ biến

| Sai lầm | Cách hiểu đúng |
|---|---|
| File 0 byte giống payload dài 0 | File 0 byte không có header; payload 0 có header hợp lệ |
| Một lần read lấp đầy buffer | Có thể đọc một phần |
| Read 0 nghĩa EOF | EOF là -1 |
| Header hợp lệ nghĩa payload đủ | Writer có thể crash trước khi ghi xong body |
| File size trả int | Java trả long |
| Ép long xuống int luôn ổn | Có thể overflow |
| readNBytes luôn đủ | Có thể ngắn ở EOF |
| readFully trả ít bytes mà không lỗi | Nó ném EOFException khi không đủ |
| Buffer getInt đọc ngay sau read | Phải kiểm số byte và flip |
| Gặp EOF giữa record là kết thúc bình thường | Đó là truncated record |
| EOF đúng boundary của multi-record là lỗi | Có thể là normal end |
| Length trên file đáng tin | Validate negative/max trước allocate |
| File có đúng size nên checksum đúng | Size không kiểm tra nội dung |
| Đã ghi bằng write một lần là đủ | Phải kiểm và lặp nếu còn remaining |
| Files.size là snapshot cố định | File có thể đổi sau phép đo |
| Unit test file local đủ phát hiện mọi short read | Nên có wrapper ép partial reads |
| Một exception được bắt nghĩa parser thành công | Phải phản ánh trạng thái lỗi đúng |

## 20. Câu hỏi tự kiểm tra

1. File 0 byte, cần đúng một record có header 4 byte: kết quả? **EMPTY_FILE**.
2. File gồm bốn byte 0: kết quả? **Bản ghi payload rỗng hợp lệ**.
3. Header mới 3/4 byte rồi EOF: **TRUNCATED_HEADER**.
4. Header length 9 nhưng nhận 7 byte body rồi EOF: **TRUNCATED_PAYLOAD 7/9**.
5. `FileChannel.read` trả 2 khi remaining=4: **đọc tiếp**, không phải EOF.
6. `read()==0` có phải EOF không? **Không**, EOF = -1.
7. Length âm nên xử lý trước hay sau allocate? **Trước**.
8. File dài 3 GiB có thể ép size long sang int không? **Không**.
9. `DataInputStream.readFully` bị thiếu bytes làm gì? **Ném EOFException**.
10. `InputStream.readNBytes(100)` ở EOF sau 40 byte trả gì? **Mảng 40 byte**.
11. Header length hợp lệ có chứng minh record hoàn chỉnh không? **Không**.
12. File đúng kích thước có chắc nội dung hợp lệ không? **Không**, kiểm checksum/schema.
13. Có nên chỉ thử parser bằng file đầy đủ? **Không**, cần test partial/EOF.
14. Multi-record parser gặp EOF trước byte đầu header kế tiếp thì sao? **Có thể kết thúc hợp lệ**.
15. EOF giữa payload log mới ghi dở có thể xử lý thế nào? **Theo recovery protocol; không tự ý coi là complete/truncate**.
16. Vì sao dùng streaming với file lớn? **Giảm footprint bộ nhớ và tránh allocate theo kích thước file**.
17. Vì sao cần dọn fixture tạm cả khi test fail? **Tránh rò tài nguyên và ảnh hưởng các test sau**.

## 21. Tài liệu chính thức Java SE 17

- [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html): read/write, size, position và long offsets.
- [ByteBuffer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): position/limit/flip/getInt/compact và byte order.
- [InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): read, readNBytes và EOF.
- [DataInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/DataInputStream.html): readFully, readInt và EOFException.
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): size, newInputStream, readAllBytes, temp files.
- [EOFException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/EOFException.html): end-of-file bất ngờ trong dữ liệu nhị phân.
- [SocketChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking reads, zero reads và partial I/O.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-006">Mô phỏng partial read, partial write và lỗi giữa chừng</a>

<details>
<summary>Xem chi tiết</summary>

# Mô phỏng partial read, partial write và lỗi giữa chừng trong Java I/O

## 1. Vì sao phải kiểm thử I/O theo từng phần?

Trong Java NIO, `ReadableByteChannel.read(ByteBuffer)` và `WritableByteChannel.write(ByteBuffer)` trả về **số byte thật sự đã chuyển trong lời gọi hiện tại**, không hứa luôn xử lý hết `ByteBuffer.remaining()`. Với file nhỏ trên ổ cục bộ, một lần đọc/ghi có thể tình cờ đủ. Nhưng socket non-blocking, luồng bị giới hạn băng thông, bộ đệm gần đầy, I/O từ thiết bị hoặc một test double có thể khiến lệnh chỉ chuyển **một phần**, không chuyển byte nào, hoặc gặp `IOException` giữa chuỗi thao tác.

Nếu chỉ kiểm tra trường hợp “đọc hết một lần, ghi hết một lần”, code dễ chứa những lỗi:

- Bỏ quên phần `ByteBuffer` chưa ghi, gây **mất dữ liệu**.
- Coi `read() == 0` là EOF, nên dừng quá sớm.
- Coi `write() == 0` là thành công và loại bỏ bytes chưa chuyển.
- Quên gọi `flip()`/`clear()` hoặc `compact()`, khiến ghi sai vùng dữ liệu.
- Thử lại toàn bộ request/file sau lỗi và **ghi trùng** phần đã thực hiện.
- Coi `IOException` chỉ có thể xảy ra trước khi bất kỳ byte nào được chuyển.
- Dùng `Thread.sleep` trong test thay vì kiểm soát lịch lỗi một cách xác định.

Mục tiêu của bài này là **tạo tình huống lỗi một cách chủ động và tái lập được** bằng các channel giả lập, thay vì chờ mạng chậm hoặc làm hỏng file thật. Bạn sẽ biết cách kiểm thử vòng lặp đọc đủ/ghi đủ, phân biệt `0`/EOF/exception, quan sát trạng thái buffer và thiết kế retry có thể phục hồi.

## 2. Hợp đồng read và write cần thuộc lòng

Với `ReadableByteChannel.read(dst)`, gọi `dst.remaining()` trước khi đọc để biết số byte tối đa mà buffer nhận được.

| Kết quả | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `n > 0` | Đã đưa `n` bytes vào `dst`, tăng `position` tương ứng. | Cập nhật số byte, tiếp tục nếu đang đọc một frame cố định. |
| `n == 0` | Chưa đọc được byte trong lời gọi này; có thể do I/O chưa sẵn sàng hoặc buffer không còn chỗ. | **Không** tự coi là EOF; xem blocking mode, readiness và deadline. |
| `n == -1` | Đã tới end-of-stream (EOF). | Nếu cần đúng N bytes mà chưa đủ thì báo truncated frame. |
| `IOException` | Đọc thất bại. | Không coi là EOF hay “chắc chắn chưa đọc byte nào”; áp dụng chính sách lỗi/recovery. |

Với `WritableByteChannel.write(src)`:

| Kết quả | Ý nghĩa | Cách xử lý |
| --- | --- | --- |
| `n > 0` | Đã lấy `n` bytes từ `src`, tăng `position` tương ứng. | Tiếp tục ghi phần `src.hasRemaining()`. |
| `n == 0` | Chưa chuyển byte trong lần gọi này. | Giữ buffer, chờ khả năng ghi/áp dụng deadline; tránh busy-loop. |
| `IOException` | Lời gọi thất bại. | Không ACK thành công và không retry mù nếu có thể đã ghi một phần. |

`write` **không dùng `-1` làm tín hiệu EOF** như `read`. Một số channel có hành vi blocking và có thể đợi tới khi tiến triển, nhưng những channel như `SocketChannel` ở non-blocking mode có thể thường xuyên trả `0`. Giao diện `ReadableByteChannel` cho phép trả ít hơn số byte còn chỗ trong buffer; một lần read không đồng nghĩa một message TCP.

### ByteBuffer.position, limit và remaining

Giả sử `ByteBuffer.allocate(8)`: lúc mới tạo, `position=0`, `limit=8`, `remaining=8`.

```text
Sau khi read được 3 bytes:
  position=3, limit=8
  [A B C _ _ _ _ _]
   ^ dữ liệu nhận     ^ vùng còn chỗ

Sau flip():
  position=0, limit=3
  [A B C] -> vùng chuẩn bị truyền/đọc

Sau write được 2 bytes:
  position=2, limit=3
  [A B | C] -> còn byte C chưa gửi

Sau write hết byte C:
  position=3, limit=3; remaining=0
```

`flip()` chuyển buffer từ chế độ lấp dữ liệu sang chế độ tiêu thụ. `clear()` đưa position về 0 và limit về capacity nhưng **không xóa vật lý bytes**. `compact()` giữ các bytes chưa tiêu thụ rồi chuyển chúng về đầu để tiếp tục đọc thêm; hữu ích cho parser TCP nhận dữ liệu không trọn frame.

Nếu gọi `clear()` ngay sau một lần `write` ngắn, phần bytes chưa ghi sẽ bị coi như không còn trong buffer và có nguy cơ mất. Nếu luôn gọi `flip()` sau mỗi lệnh `read` mà chưa cân nhắc state, bạn có thể gửi lặp một đoạn hoặc phá trạng thái parser.

## 3. Thiết kế test double cho partial I/O

Một unit test tốt không cần socket Internet hoặc ghi lên ổ thật. Có thể triển khai hai interface Java NIO:

- `ReadableByteChannel` nhận dữ liệu từ byte array và mỗi lần chỉ đưa tối đa 2 bytes vào buffer.
- `WritableByteChannel` nhận dữ liệu vào `ByteArrayOutputStream` và mỗi lần chỉ lấy tối đa 3 bytes từ buffer.
- Ở lần gọi đầu tiên, mỗi channel trả `0` để kiểm tra code không nhầm “chưa sẵn sàng” với EOF hay thành công.
- Thống kê số lần gọi để xác nhận code đã đi qua nhánh partial I/O thực sự.

Với test double, các số 2/3 là **ràng buộc cố ý của test**, không phải dự đoán filesystem hoặc số byte do hệ điều hành quyết định. Lợi ích lớn nhất là kiểm thử hoàn toàn xác định: chạy một trăm lần vẫn đi qua cùng lịch sự kiện.

### Ví dụ Java 17 chạy được: ShortIoDemo

Tạo file `ShortIoDemo.java`:

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class ShortIoDemo {
    static final class ShortReader implements ReadableByteChannel {
        private final byte[] data = "ABCDEFG".getBytes(StandardCharsets.US_ASCII);
        int pos, calls;
        boolean firstZero, open = true;
        @Override public int read(ByteBuffer dst) throws IOException {
            if (!open) throw new ClosedChannelException();
            calls++;
            if (!firstZero) { firstZero = true; return 0; }
            if (pos == data.length) return -1;
            int n = Math.min(2, Math.min(dst.remaining(), data.length - pos));
            dst.put(data, pos, n);
            pos += n;
            return n;
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
    static final class ShortWriter implements WritableByteChannel {
        private final ByteArrayOutputStream saved = new ByteArrayOutputStream();
        int calls;
        boolean firstZero, open = true;
        @Override public int write(ByteBuffer src) throws IOException {
            if (!open) throw new ClosedChannelException();
            calls++;
            if (!firstZero) { firstZero = true; return 0; }
            int n = Math.min(3, src.remaining());
            byte[] chunk = new byte[n];
            src.get(chunk);
            saved.write(chunk);
            return n;
        }
        String content() { return saved.toString(StandardCharsets.US_ASCII); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
    public static void main(String[] args) throws Exception {
        try (ShortReader source = new ShortReader();
             ShortWriter target = new ShortWriter()) {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            int total = 0;
            while (true) {
                int n = source.read(buffer);
                if (n < 0) break;
                if (n == 0) continue; // Fixture chỉ trả 0 đúng một lần.
                buffer.flip();
                while (buffer.hasRemaining()) {
                    int written = target.write(buffer);
                    if (written == 0) continue; // Fixture chỉ trả 0 đúng một lần.
                    total += written;
                }
                buffer.clear();
            }
            System.out.println("content=" + target.content());
            System.out.println("copied=" + total);
            System.out.println("read-zero=" + source.firstZero);
            System.out.println("write-zero=" + target.firstZero);
            System.out.println("read-calls=" + source.calls);
            System.out.println("write-calls=" + target.calls);
        }
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac --release 17 ShortIoDemo.java
java ShortIoDemo
```

Kết quả đã kiểm thử:

```text
content=ABCDEFG
copied=7
read-zero=true
write-zero=true
read-calls=6
write-calls=5
```

**Diễn giải lịch I/O:**

1. `read` đầu trả `0`; vòng lặp chưa chuyển byte nào và chỉ tiếp tục vì fixture được thiết kế chắc chắn có tiến triển.
2. Bốn lệnh `read` tiếp theo trả `2, 2, 2, 1` byte. Mỗi lệnh sử dụng phần `dst.remaining()` và tăng `position` đúng số byte.
3. Lệnh `read` cuối trả `-1` để báo EOF. Tổng cộng **6** lần gọi.
4. `write` đầu tiên trả `0`. Bốn lần ghi có tiến triển sau đó tiêu thụ `2, 2, 2, 1` byte. Tổng **5** lần gọi write.
5. `flip()` chuyển 2 hoặc 1 byte vừa nhận sang chế độ đọc để writer tiêu thụ; `while (buffer.hasRemaining())` bảo đảm chưa mất byte nào trước `clear()`.
6. `copied=7` và `content=ABCDEFG` chứng minh toàn bộ bytes được đưa qua fixture đúng thứ tự.

**Cảnh báo quan trọng:** hai nhánh `if (n == 0) continue` ở đây chỉ có thể dùng an toàn vì fixture cố ý trả `0` đúng **một lần**. Không sao chép nguyên vòng này vào channel non-blocking production: một channel có thể trả `0` liên tục, gây CPU spin. Dùng `Selector`/readiness, hàng đợi/đợi có giới hạn và timeout đúng kiến trúc.

### Một lỗi test dễ bỏ qua

Nếu fixture luôn trả “đủ các bytes được hỏi” thì code sai sau vẫn vượt qua:

```java
// Anti-pattern: lỗi vì không xử lý short write.
ByteBuffer src = ByteBuffer.wrap(payload);
channel.write(src);               // Có thể chỉ ghi một phần.
System.out.println("finished");   // Báo thành công quá sớm.
```

Đây là đoạn minh họa có `payload`, `channel` giả định. Test dùng `ShortWriter` sẽ làm rõ ngay lỗi này: `src.hasRemaining()` vẫn true sau lời gọi đầu có tiến triển, hoặc thậm chí lần đầu trả `0` mà chưa có byte nào được ghi.

## 4. Mô phỏng lỗi giữa chừng: đã chuyển một phần rồi IOException

Mục tiêu tiếp theo là kiểm tra không chỉ short I/O, mà cả **lỗi đến sau một số lời gọi thành công**:

- Reader cung cấp đúng 2 bytes `HE`, rồi ném `IOException` khi ứng dụng còn cần 3 bytes.
- Writer nhận đúng 2 bytes `HE`, rồi ném `IOException` ở lần ghi kế tiếp; 3 bytes `LLO` vẫn còn trong buffer của ví dụ.
- Ứng dụng phải ghi nhận dữ liệu **chưa hoàn chỉnh**, không trả thành công.
- Không thể suy từ lỗi rằng bên nhận hoặc storage **chưa từng nhận byte nào**.

### Ví dụ Java 17: MidStreamFailureDemo

Tạo file `MidStreamFailureDemo.java`:

```java
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;

public class MidStreamFailureDemo {
    static final class FaultyReader implements ReadableByteChannel {
        boolean open = true;
        int calls;
        @Override public int read(ByteBuffer dst) throws IOException {
            if (!open) throw new ClosedChannelException();
            if (++calls == 1) {
                dst.put((byte) 'H').put((byte) 'E');
                return 2;
            }
            throw new IOException("injected read failure");
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
    static final class FaultyWriter implements WritableByteChannel {
        boolean open = true;
        int calls;
        final ByteArrayOutputStream saved = new ByteArrayOutputStream();
        @Override public int write(ByteBuffer src) throws IOException {
            if (!open) throw new ClosedChannelException();
            if (++calls == 1) {
                int n = Math.min(2, src.remaining());
                byte[] chunk = new byte[n];
                src.get(chunk);
                saved.write(chunk);
                return n;
            }
            throw new IOException("injected write failure");
        }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
    public static void main(String[] args) throws Exception {
        ByteBuffer received = ByteBuffer.allocate(5);
        try (FaultyReader source = new FaultyReader()) {
            try {
                while (received.hasRemaining()) {
                    int n = source.read(received);
                    if (n < 0) throw new EOFException("truncated frame");
                }
            } catch (IOException e) {
                System.out.println("read-error=" + e.getMessage());
            }
        }
        System.out.println("read-before-error=" + received.position());

        ByteBuffer pending = ByteBuffer.wrap(
                "HELLO".getBytes(StandardCharsets.US_ASCII));
        try (FaultyWriter target = new FaultyWriter()) {
            try {
                while (pending.hasRemaining()) {
                    int n = target.write(pending);
                    if (n == 0) throw new IOException("write stalled");
                }
            } catch (IOException e) {
                System.out.println("write-error=" + e.getMessage());
            }
            System.out.println("written-prefix=" +
                    target.saved.toString(StandardCharsets.US_ASCII));
            System.out.println("remaining=" + pending.remaining());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 MidStreamFailureDemo.java
java MidStreamFailureDemo
```

Kết quả đã kiểm thử:

```text
read-error=injected read failure
read-before-error=2
write-error=injected write failure
written-prefix=HE
remaining=3
```

Kết quả được kiểm soát hoàn toàn bởi fixture: lần đọc đầu tăng `received.position()` lên `2`; lần ghi đầu tăng `pending.position()` lên `2`. Sau đó lỗi xảy ra ở **lời gọi kế tiếp** trước khi fixture thay đổi buffer thêm lần nữa, nên bản ghi đã nhận đúng prefix `HE` và còn `3` byte chưa ghi.

**Không suy diễn ra mọi channel thật:** nếu một lời gọi I/O **tự nó** ném `IOException` giữa khi truy cập OS/thiết bị, mức tiến triển đã được lưu bền hoặc peer đã xử lý có thể **không xác định** ở tầng ứng dụng. Vị trí `ByteBuffer` của fixture là đo được vì ta tự viết nó; API không có một “số byte chắc chắn đã commit đến peer/database” sau mọi exception. Tránh báo retry an toàn chỉ dựa trên `position()` sau lỗi.

## 5. EOF giữa frame khác IOException giữa frame

Khi một protocol quy định frame dài **N bytes**, reader phải phân biệt:

- Đọc được đúng N bytes: hoàn thành frame.
- Nhận `-1` khi mới có ít hơn N bytes: **truncated frame**, dù EOF tự nó là kết thúc stream bình thường.
- Nhận `IOException`: lỗi đọc, không được tự diễn dịch là EOF.
- Nhận `0`: chưa tiến triển; không thể tự kết luận frame hỏng hoặc hoàn thành.

Một hàm đọc đủ trong **blocking context** (mã minh họa, không phải chương trình độc lập):

```java
static void readFully(ReadableByteChannel input, ByteBuffer dst)
        throws IOException {
    while (dst.hasRemaining()) {
        int n = input.read(dst);
        if (n < 0) {
            throw new EOFException("Unexpected EOF: missing "
                    + dst.remaining() + " bytes");
        }
        if (n == 0) {
            throw new IOException("No progress; use readiness/deadline policy");
        }
    }
}
```

Hàm cố tình **fail fast trên `0`** khi dùng trong test được kiểm soát, để tránh vòng lặp vô hạn. Với non-blocking `SocketChannel`, `0` là trạng thái hợp lệ; handler production nên giữ `dst` và đăng ký `OP_READ`, không coi nó là lỗi ngay. Với blocking channel thông thường, nếu buffer còn chỗ, read thường chờ có tiến triển hoặc EOF; vẫn cần xử lý trạng thái và timeout theo loại channel.

Khi một frame có **length-prefix**, phải giới hạn length trước khi cấp phát buffer để tránh lỗi bảo mật/tài nguyên:

```text
[4-byte length][payload length bytes][optional checksum]
```

Test lần lượt “header thiếu 1 byte”, “payload thiếu 1 byte”, “length âm/quá lớn”, “truyền nhiều frame chung một read”, “một frame trải qua nhiều read”, “đọc 0 nhiều lần”. Đây là kiểm thử parser/protocol, bổ sung cho test API Channel.

## 6. Ghi đủ không đồng nghĩa transaction nguyên tử

Vòng lặp ghi dữ liệu có tiến triển thường được diễn đạt:

```java
// Snippet minh họa dành cho channel có progress theo chính sách.
static void writeAll(WritableByteChannel output, ByteBuffer src)
        throws IOException {
    while (src.hasRemaining()) {
        int n = output.write(src);
        if (n == 0) {
            throw new IOException("No write progress");
        }
    }
}
```

Cách này hữu ích cho **fixture hoặc blocking path** được kỳ vọng có tiến triển. Với non-blocking socket, phải chờ `OP_WRITE` thay vì ném lỗi ngay; không bật `OP_WRITE` liên tục nếu không có bytes pending, vì socket thường writable khiến vòng xử lý thức liên tục.

Ngay cả khi `writeAll` hoàn tất:

- Với socket, điều đó **không chứng minh remote application đã parse/commit dữ liệu**; cần acknowledgement ở protocol.
- Với file, điều đó **không chứng minh bytes đã bền vững sau mất điện**; xem `FileChannel.force` và filesystem contract.
- Với record lớn, thao tác có thể gồm nhiều ghi nhỏ; nếu crash ở giữa, file có thể chứa prefix dở. Cần checksum/version/journal/atomic replace hoặc storage engine.
- Với network request không idempotent, lỗi kết nối sau partial write có thể tạo **trạng thái chưa rõ bên nhận xử lý tới đâu**. Retry mù có thể gây gửi trùng hoặc double charge.

## 7. Chiến lược retry an toàn sau lỗi

Một điểm dễ nhầm là tách ba cấp độ:

| Cấp độ | Có thể biết gì sau một lời gọi thành công? | Không thể kết luận gì? |
| --- | --- | --- |
| `ByteBuffer` | `position` tăng theo byte được channel báo đã chuyển. | Peer đã xử lý, disk đã commit. |
| Kết nối TCP | `write` chấp nhận một phần byte stream ở local stack. | Remote app đã nhận toàn bộ logical message. |
| Ứng dụng nghiệp vụ | Chỉ có response/ACK theo protocol của ứng dụng. | Nếu mất ACK, không chắc server chưa làm việc. |

Khi IOException xảy ra:

1. Ghi log **tác vụ, offset/frame ID, kích thước mong đợi, số bytes đã báo tiến triển từ các lời gọi thành công trước đó**, loại exception và trạng thái kênh.
2. Đánh dấu request/file ở trạng thái **incomplete hoặc uncertain**, đừng báo thành công.
3. Kiểm tra loại dữ liệu: file tạm mới có thể xóa rồi dựng lại; log append cần record framing/checksum; network request cần idempotency key hoặc hỏi transaction status.
4. Quy định retry budget, backoff và deadline; tránh retry vô hạn khi thiết bị/mạng đang hỏng.
5. Khi không chứng minh được offset đã commit, **không** tùy tiện chỉ gửi phần còn lại hoặc gửi từ đầu nếu giao thức không hỗ trợ resume/dedup.

Ví dụ một file import có thể ghi vào `incoming.tmp` rồi publish bằng `ATOMIC_MOVE` sau khi kiểm tra checksum; consumer không bao giờ đọc file tạm. Với log cần lưu mọi record, dùng length/checksum/commit marker và quét bỏ record cuối dở khi recovery. Đây là vấn đề durability/transaction, **không được giải quyết chỉ bằng vòng lặp write**.

## 8. Test matrix tối thiểu cần chạy

| Test case | Lịch mô phỏng | Kết quả phải kiểm tra |
| --- | --- | --- |
| Read đủ ngay | `read -> N` | Byte contents và count đúng. |
| Partial read | `read -> 2, 2, 1` | Tích lũy đủ N, không bỏ/mất thứ tự. |
| Read tạm 0 | `read -> 0, N` | Không coi `0` là EOF; không CPU-spin trong production. |
| EOF trước đủ N | `read -> 2, -1` | Báo truncated frame, không parse dữ liệu dở. |
| Read lỗi sau prefix | `read -> 2, IOException` | Báo lỗi, giữ context số bytes, không ACK. |
| Write đủ ngay | `write -> N` | `hasRemaining == false`, bytes đúng. |
| Partial write | `write -> 1, 2, ...` | Lặp cho tới khi hết buffer, không gọi `clear` sớm. |
| Write tạm 0 | `write -> 0, N` | Giữ pending bytes, chờ readiness/deadline. |
| Write lỗi giữa chừng | `write -> 2, IOException` | Prefix có thể đã được chuyển; xử lý uncertain state. |
| Lỗi ở lời gọi đầu | `read/write -> IOException` | Không suy diễn là EOF, không retry vô hạn. |
| Buffer rất nhỏ | Capacity 1, 2, 3 | Không phá trạng thái flip/clear, giữ đúng thứ tự. |
| Payload rỗng / boundary | 0, 1, N-1, N, N+1 | Không truy cập buffer quá limit. |
| Nhiều message | Frame bị tách hoặc gộp | Parser giữ state, không dựa vào một read = một message. |
| Client hủy / timeout | Thời hạn hết giữa I/O | Cleanup, close, không double-complete. |
| Retry sau lỗi | Thử lại cùng ID | Idempotency/khả năng khôi phục, không tạo side effect trùng. |

Nên dùng một test double **đặt sẵn lịch trả về** (`0, 2, 1, -1, exception`), chứ không phụ thuộc `Thread.sleep` hoặc traffic thật để đạt nhánh logic. Integration test sau đó nên chạy trên local file, loopback TCP, volume/OS mục tiêu, với network delay và restart simulation phù hợp.

### Cách viết assertions

Không chỉ kiểm tra “không ném exception”. Một bộ test tốt xác nhận:

- **Nội dung chính xác**: so sánh byte arrays (`Arrays.equals`), không chỉ số bytes.
- **Không mất/không nhân đôi bytes**: tổng số bytes ghi đúng bằng payload length khi hoàn tất.
- **Vòng lặp không treo**: số lần gọi hoặc deadline giới hạn; test phải thất bại rõ nếu channel cứ trả `0`.
- **Buffer state**: `position, limit, remaining` ở từng bước đúng ý nghĩa.
- **Nhánh lỗi**: ghi nhận exception, không thực hiện commit/ACK sau lỗi.
- **Tài nguyên**: cả hai channel được đóng trong `try-with-resources` khi thành công và khi ném lỗi.
- **Tái thực thi**: với retry, business state không bị nhân đôi và có thể phân biệt uncommitted/committed.

## 9. Quan sát và chẩn đoán khi chạy thực tế

Khi sự cố chỉ xảy ra dưới tải, log nên giúp tách **short I/O bình thường** khỏi **lỗi I/O**:

- Operation ID, channel type, local/remote endpoint hoặc path đã được bảo vệ thông tin nhạy cảm.
- Offset và total expected bytes của file/record/frame.
- Bytes returned bởi từng call; số lần trả `0` và số lần short read/write.
- `ByteBuffer.position/limit/remaining` **trước và sau lời gọi thành công**, chú ý rằng buffer có thể không an toàn để suy luận trạng thái commit sau exception tùy API.
- Thời gian chờ readiness, deadline, số lượng pending bytes, queue length và retry count.
- Exception class, cause và socket/file lifecycle; không nuốt stack trace khi cần chẩn đoán.

Tránh log toàn bộ payload chứa token, PII hoặc binary khổng lồ. Dùng sample hoặc metadata đủ để tái dựng vấn đề; khi cần xác minh toàn vẹn có thể dùng checksum ở mức record.

Một ví dụ ngắn về log định hướng:

```text
op=send-order-142 expected=1024 written=512 remaining=512 result=SHORT_WRITE
op=send-order-142 expected=1024 written=768 remaining=256 result=SHORT_WRITE
op=send-order-142 result=IO_ERROR error=IOException commit=UNKNOWN
```

Đây là **log giả định để minh họa**, không phải stdout của chương trình trên. Trường `commit=UNKNOWN` nhắc rằng không thể suy từ byte count local rằng nghiệp vụ bên kia chưa làm gì.

## 10. Lỗi thường gặp và sửa chữa

| Sai lầm | Vì sao sai | Hướng đúng |
| --- | --- | --- |
| Một `read` bằng cả message | TCP và channel có thể đọc ngắn | Parser tích lũy, length framing. |
| Một `write` gửi hết buffer | Channel có thể ghi ngắn hoặc 0 | Kiểm tra `hasRemaining` và ghi tiếp theo readiness. |
| Coi `read == 0` là EOF | EOF là `-1` | Xử lý 0 như chưa tiến triển. |
| Busy-loop `while(write==0)` | CPU 100% khi output buffer chưa sẵn sàng | `Selector`/backpressure/deadline. |
| Gọi `clear` khi còn bytes chưa write | Mất phần bytes chưa gửi | Chỉ clear sau khi tiêu thụ hết; dùng compact nếu cần. |
| Coi `IOException` như EOF | Lỗi và kết thúc bình thường khác semantics | Phân biệt exception, EOF, timeout. |
| Sau lỗi retry payload từ đầu | Có thể gây trùng side effect | Idempotency/resume/protocol ACK. |
| Sau lỗi giả định đã ghi đúng `position()` bytes bên remote | Local buffer không đo commit của peer | Theo dõi trạng thái ở protocol. |
| Test dựa vào sleep/network bất định | Flaky, khó tái hiện | Scripted fake channels/fault injection. |
| Chỉ test happy path | Không bắt được partial/truncation | Ma trận `0`/short/EOF/error/boundaries. |
| Giữ object buffer cho nhiều operation concurrent | Data race và trạng thái buffer sai | Một owner cho buffer tới completion. |
| Gọi `force` rồi coi file transaction nguyên tử | Durability khác atomicity | WAL/checksum/atomic publication/recovery. |

## 11. Checklist thực hành

1. Đã biết loại channel đang **blocking hay non-blocking** chưa?
2. Đã ghi đủ điều kiện dừng: `N bytes`, `EOF`, deadline hay protocol marker?
3. Đã phân biệt `0` với `-1` và `IOException`?
4. Có lưu phần `ByteBuffer.remaining()` sau short write không?
5. Sau `read` có `flip` đúng lúc và sau write có `clear/compact` đúng lúc không?
6. Có limit số bytes/frames để chống input bất hợp lệ và OOM không?
7. Vòng thử lại có bounded deadline/readiness, không busy-loop không?
8. Fixture có tạo được lịch partial + `0` + EOF + IOException deterministically không?
9. Test có assert **nội dung byte** và số lần tiến triển thay vì chỉ output console không?
10. Sau lỗi, ứng dụng có trạng thái uncertain/recovery/idempotency thay vì ACK sai không?
11. Đã log offset/bytes/exception an toàn, có đóng tài nguyên đúng vòng đời không?
12. Nếu dùng file thật hoặc network, đã có integration test trên provider/OS mục tiêu chưa?

## 12. Câu hỏi tự kiểm tra

**1. `read(dst)` trả `2` khi buffer còn chỗ cho 8 bytes có phải lỗi không?** Không; short read là hành vi hợp lệ.

**2. `read(dst)` trả `0` có nghĩa EOF?** Không. EOF của readable channel được báo bằng `-1`.

**3. `write(src)` trả `0` thì có được `src.clear()` không?** Không, vì bytes vẫn pending trong buffer.

**4. Đang đọc frame 5 bytes, mới được 2 bytes rồi `-1`: kết quả gì?** Truncated frame, thường báo `EOFException`.

**5. Sau `IOException` từ network write, có chắc server chưa xử lý request?** Không; cần acknowledgement/idempotency ở tầng ứng dụng.

**6. Vì sao test fake channel hữu ích hơn chỉ giới hạn băng thông mạng thật?** Vì fake channel kiểm soát thứ tự trả `0`, partial counts, EOF và exception, dễ lặp lại nhánh lỗi.

**7. Có nên dùng vòng `while (channel.write(buf) == 0)` liên tục trên non-blocking socket?** Không; nên chờ readiness và giới hạn thời gian để tránh CPU spin.

**8. Khi một lệnh `write` ném IOException, `ByteBuffer.position` có phải số byte chắc chắn đã tới disk/peer?** Không. Buffer state không phải application commit evidence.

## Tài liệu chính thức

- [Java SE 21 — ReadableByteChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/ReadableByteChannel.html): `read` có thể trả ít bytes hơn yêu cầu, 0 hoặc -1.
- [Java SE 21 — WritableByteChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/WritableByteChannel.html): `write` có thể ghi ngắn hoặc 0.
- [Java SE 21 — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): `flip`, `clear`, `compact`, position/limit.
- [Java SE 21 — SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html): non-blocking network read/write.
- [Java SE 21 — FileChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html): positional file I/O, IOException, `force`.
- [Java SE 21 — Selector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html): readiness-based non-blocking I/O.

**Tổng kết:** Đừng kiểm thử I/O bằng giả định mọi lần đọc/ghi đều đủ. Hãy chủ động mô phỏng **short read**, **short write**, `0`, EOF và lỗi giữa chừng; quan sát `ByteBuffer` đúng ranh giới và thiết kế recovery/idempotency khi đã chuyển một phần dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-007">Kiểm tra tài nguyên được đóng khi có lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tài nguyên được đóng khi có lỗi trong Java I/O

## 1. Vì sao một chương trình đúng kết quả vẫn có thể bị rò rỉ tài nguyên?

Chương trình Java I/O thường mở **file stream, reader/writer, channel, socket, ZIP archive, directory stream** hoặc các tài nguyên giữ file descriptor/handle của hệ điều hành. Nếu một exception xảy ra giữa chừng mà tài nguyên không được giải phóng, hệ quả có thể là:

- File descriptor/socket handle không được trả lại; hệ thống xuất hiện lỗi như “Too many open files”.
- File bị giữ mở, gây khó khăn cho đổi tên/xóa trên một số hệ điều hành.
- Buffer đầu ra chưa được flush/finalize; dữ liệu ZIP/GZIP thiếu trailer hoặc metadata.
- Kết nối mạng vẫn mở, giữ tài nguyên phía client và server.
- Directory stream hoặc stream đọc file tiếp tục giữ handle dù business logic đã ngừng dùng.
- Trong hệ thống xử lý nhiều request, lỗi hiếm có thể tích lũy thành leak nghiêm trọng.

**Kiểm tra tài nguyên được đóng khi có lỗi** nghĩa là viết test buộc chương trình đi qua các nhánh lỗi, sau đó quan sát **ai chịu trách nhiệm close, close được gọi mấy lần, theo thứ tự nào, và exception nào được giữ lại**.

Đây không chỉ là “thêm `finally`”. Java hiện đại ưu tiên **`try-with-resources`** cho các đối tượng `AutoCloseable`, nhưng ta vẫn cần kiểm thử hợp đồng sở hữu và xử lý những tình huống phức tạp.

Mục tiêu sau bài:

1. Nắm đúng semantics của `try-with-resources` với normal exit, exception, `return`, `break` và lỗi khi mở tài nguyên.
2. Kiểm tra tài nguyên được close kể cả khi `read`/`write` ném `IOException`.
3. Hiểu primary exception và **suppressed exceptions** phát sinh từ `close()`.
4. Xây test double/fault injection đo lệnh đóng và thứ tự đóng, không phụ thuộc lỗi I/O ngẫu nhiên.
5. Nhận diện giới hạn của test double: `close()` được gọi không tự chứng minh OS handle thực sự đã được giải phóng.
6. Áp dụng cho stream wrappers, `FileChannel`, `ZipFile`, `DirectoryStream` và `Files.lines()/walk()/list()`.

## 2. Phân biệt close, flush và giải phóng thật sự

Ba khái niệm thường bị trộn:

| Khái niệm | Hợp đồng chính | Cách quan sát/test |
|---|---|---|
| `flush()` | Đẩy dữ liệu được buffer sang tầng tiếp theo theo hợp đồng của stream/writer | Fake output ghi nhận `flush`, kiểm tra dữ liệu đã tới underlying stream |
| `close()` | Kết thúc vòng đời tài nguyên; tùy API có thể flush/finalize trước khi giải phóng | Spy/tracker đánh dấu close, integration test kiểm tra trạng thái tài nguyên |
| Độ bền dữ liệu (durability) | Byte đã được đảm bảo lưu ổn định theo hợp đồng ứng dụng | Dùng API tương ứng như `FileChannel.force(...)`, chính sách filesystem và kiểm thử riêng |

`OutputStream.close()`/`Writer.close()` thường hoàn tất xử lý buffer của lớp đó trước khi đóng stream bên dưới. Nhưng **đóng không đồng nghĩa tất cả dữ liệu chắc chắn đã durable trên thiết bị**, cũng không đồng nghĩa peer mạng đã xác nhận xử lý.

Đối với kiểm thử close, nên xác định chính xác bất biến:

> Nếu một phương thức **sở hữu** một tài nguyên đã mở thành công, thì trên mọi đường thoát của phương thức — kể cả khi phát sinh lỗi — tài nguyên đó phải được yêu cầu đóng đúng hợp đồng.

“Đã yêu cầu đóng” là mức kiểm thử unit với spy. “Handle không còn giữ mở” có thể cần integration test theo OS/API. “Lưu dữ liệu bền” lại là yêu cầu khác.

## 3. \`AutoCloseable\`, \`Closeable\` và chủ sở hữu tài nguyên

`AutoCloseable` khai báo:

~~~java
public interface AutoCloseable {
    void close() throws Exception;
}
~~~

`java.io.Closeable` mở rộng `AutoCloseable`, khai báo `close() throws IOException` và theo hợp đồng `Closeable`, gọi close trên đối tượng đã đóng không gây hiệu ứng mới. Nhưng **`AutoCloseable` thuần không bắt buộc close phải idempotent**; Javadoc khuyến khích implementer làm vậy nếu có thể.

Một số tài nguyên thường gặp:

| API | Tài nguyên | Ai nên đóng? |
|---|---|---|
| `FileInputStream`, `FileOutputStream` | File stream | Code tạo/mượn theo hợp đồng ownership |
| `BufferedInputStream`, `BufferedReader` | Wrapper và nguồn bên dưới | Thông thường đóng outer wrapper, nguồn bên dưới cũng được đóng |
| `InputStreamReader`, `OutputStreamWriter` | Bridge byte ↔ char và stream bên dưới | Thông thường chủ sở hữu của outer bridge |
| `FileChannel`, `SocketChannel`, `ServerSocketChannel` | File/socket channel | Thành phần đang sở hữu channel |
| `ZipFile`, `ZipInputStream` | ZIP container/stream | Thành phần mở ZIP |
| `DirectoryStream<Path>` | Directory handle | Thành phần gọi `Files.newDirectoryStream` |
| `Stream<String>` từ `Files.lines` | Open file stream | Code gọi `Files.lines` |
| `Stream<Path>` từ `Files.list`/`Files.walk` | Directory handles | Code tạo file-based stream |

**Ownership phải rõ ràng.** Nếu phương thức nhận một `InputStream` do caller truyền vào, có hai thiết kế hợp lệ nhưng không tương đương:

- **Callee owns:** phương thức nhận trách nhiệm đóng stream kể cả khi lỗi, phải nêu trong API contract.
- **Caller owns:** phương thức chỉ đọc/ghi và không đóng stream; caller sẽ đóng ở scope lớn hơn.

Không có quy tắc “hễ có InputStream là cứ close” trong mọi hoàn cảnh. Ví dụ nhiều API không nên tự đóng `System.in` hoặc stream dùng chung.

## 4. \`try-with-resources\`: nền tảng bảo vệ khi có exception

Cú pháp Java 17:

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    in.transferTo(out);
}
~~~

Đây là mã minh họa, cần `java.io.*`, `java.nio.file.*` và `Path source, target` đã xác định. Với một resource được khởi tạo thành công trong header, Java tự gọi `close()` khi thoát khỏi `try`, dù:

- Block chạy thành công tới cuối.
- Có `return` từ trong block.
- Có `break`/`continue` rời khỏi scope.
- Block ném `IOException`, `RuntimeException` hoặc `Error`.
- `catch`/`finally` đi sau cần xử lý kết quả.

### 4.1. Thứ tự tạo và đóng

Các resource trong header được **khởi tạo từ trái sang phải** và **đóng từ phải sang trái**.

~~~java
try (Tracked first = open("first");
     Tracked second = open("second")) {
    runBusinessLogic();
}
~~~

Đây là mã mô hình, `Tracked` và hai hàm factory tự định nghĩa. Logic:

~~~text
open first
open second
body
close second
close first
~~~

Nếu `runBusinessLogic()` thất bại, cả hai close vẫn được gọi theo thứ tự ngược. Nếu `close second` cũng ném lỗi, Java **vẫn cố close first**.

### 4.2. Nếu resource thứ hai không mở được?

Giả sử mở `first` thành công nhưng biểu thức khởi tạo `second` ném exception:

~~~text
open first   -> thành công
open second  -> ném IOException
close first  -> thực hiện tự động
body         -> KHÔNG chạy
~~~

Đây là một nhánh phải có trong test. Mã `try-with-resources` bảo đảm các resource **đã hoàn tất khởi tạo trước đó** được close. Nhưng nếu **constructor/factory của một resource tự nó cấp phát một native handle rồi ném trước khi trả về object**, header chưa nhận được resource đó; constructor/factory cần tự cleanup phần tài nguyên mở dở của nó.

### 4.3. Resource null

Biểu thức resource có thể cho ra `null`. Java không gọi `close()` trên resource `null`; tuy nhiên, dùng null để ngụ ý không có tài nguyên thường làm contract khó hiểu hơn và cần kiểm tra rõ khi API cho phép.

### 4.4. Biến đã tồn tại trong try-with-resources

Từ Java 9, biến local/field `final` hoặc **effectively final** có thể được đưa vào header:

~~~java
InputStream opened = Files.newInputStream(source);
try (opened) {
    consume(opened);
}
~~~

Đây là minh họa, cần `consume` được định nghĩa. Nó phù hợp khi phải khởi tạo trước một bước nhưng vẫn trao ownership của biến cho try-with-resources. Không sử dụng resource sau scope đã đóng.

## 5. Exception trong body và exception trong close: ai được ném?

Đây là điểm quan trọng nhất khi kiểm thử nhánh lỗi.

Giả sử:

1. `read()` ném `IOException("read-failed")`.
2. Khi cleanup, `close()` cũng ném `IOException("close-failed")`.

Với `try-with-resources`:

- Exception từ phần xử lý (`read-failed`) vẫn là **primary exception** được ném cho caller.
- Exception do close (`close-failed`) được gắn vào primary dưới dạng **suppressed exception**.
- Caller có thể xem chúng bằng `Throwable.getSuppressed()`.

~~~java
try (InputStream in = openFailingStream()) {
    consume(in); // có thể throw IOException("read-failed")
} catch (IOException ex) {
    System.err.println("Primary: " + ex);
    for (Throwable suppressed : ex.getSuppressed()) {
        System.err.println("During close: " + suppressed);
    }
}
~~~

Đây là **đoạn mã minh họa**, cần định nghĩa `openFailingStream()` và `consume(...)`. Không đánh đồng `Throwable.getCause()` với `getSuppressed()`:

| API | Thể hiện |
|---|---|
| `getCause()` | Nguyên nhân của một exception được bọc/chuỗi hóa bằng cause |
| `getSuppressed()` | Những exception bổ sung bị giữ lại khi xử lý/cleanup exception chính |
| `addSuppressed(...)` | Cho phép code tự đính exception cleanup, nhưng `try-with-resources` đã làm sẵn |

### Nếu block chạy bình thường mà close lỗi?

Trong trường hợp không có lỗi body, exception từ close là lỗi được ném ra. Nếu có **nhiều resource close đều lỗi**, exception từ resource đóng trước (resource ở bên phải header) là primary, và những close failures kế tiếp trở thành suppressed.

### Nếu cả open và close lỗi?

Nếu resource B không khởi tạo được nhưng A đã mở, exception từ khởi tạo B được giữ là primary; lỗi từ việc đóng A được đưa vào suppressed. Điều này bảo tồn thông tin về **lý do ban đầu làm quy trình thất bại**, thay vì để cleanup che mất nguyên nhân.

### Vì sao finally tự viết dễ sai?

Một cách dễ làm mất lỗi gốc:

~~~java
InputStream in = open();
try {
    doWork(in);         // ném IOException A
} finally {
    in.close();         // ném IOException B
}
~~~

Đây là phản ví dụ nếu chưa có suppressed handling: caller có thể chỉ nhận B, trong khi A đã bị che. `try-with-resources` có xử lý ưu tiên exception theo đặc tả ngôn ngữ; dùng nó thay vì viết lại logic này từ đầu.

## 6. Chiến lược kiểm thử: chủ động tiêm lỗi ở từng bước

Muốn bảo đảm close hoạt động khi lỗi, không nên chờ filesystem hoặc mạng tình cờ trục trặc. Hãy tạo **test double** hoặc fault-injection fixture mô phỏng lỗi ở từng điểm.

Một ma trận kiểm thử tối thiểu:

| Tình huống | Điều cần xác nhận |
|---|---|
| Xử lý thành công | Resource đã mở được đóng đúng một lượt sở hữu |
| `read` ném lỗi | Close được gọi; caller nhận lỗi read |
| `write` ném lỗi | Close được gọi; caller nhận lỗi write |
| `return` sớm | Close xảy ra trước khi return hoàn tất |
| `break`/`continue` rời scope | Close xảy ra khi thoát scope |
| Mở resource thứ hai lỗi | Resource thứ nhất vẫn được đóng |
| Hai resource cùng hoạt động | Đóng ngược thứ tự mở |
| `close` tự ném lỗi | Lỗi được truyền đúng; vẫn thử đóng các resource còn lại |
| Body + `close` cùng lỗi | Body là primary, close nằm trong suppressed |
| Wrapper gặp lỗi đọc | Đóng wrapper sẽ đóng nguồn theo ownership |
| Một resource bị close hai lần | Không phụ thuộc may mắn; xác định hợp đồng và kiểm tra số lượt |
| Hủy/timeout async I/O | Kiểm tra chính sách cancel và giải phóng khi mọi operation đã được giải quyết |

Nên phân biệt ba loại kiểm tra:

1. **Contract test tại unit level:** tài nguyên fake/spy xác nhận `close()` được gọi và ngoại lệ đúng.
2. **Integration test:** mở file/socket/ZIP thật, chạy fault scenario, kiểm tra hoạt động đóng qua API/OS.
3. **Diagnostic/soak test:** chạy nhiều lần lỗi để theo dõi file descriptors, memory, thread/socket count trong thời gian dài.

Không cần dùng một OS-specific lỗi “file đang mở không xóa được” làm **bằng chứng duy nhất**. Linux thường cho xóa tên một file ngay cả khi handle còn mở; Windows có quy tắc share/locking khác. Test portable nên dùng spy và xác nhận trạng thái theo API phù hợp.

## 7. Chương trình Java 17+ hoàn chỉnh kiểm tra bốn nhánh lỗi

Ví dụ dưới đây tự chứa tất cả fixture, không yêu cầu JUnit hoặc file thực trên đĩa. Nó mô phỏng:

- Lỗi trong body và lỗi `close` đồng thời.
- Hai resource được đóng **ngược** thứ tự mở.
- Thất bại khi mở resource thứ hai.
- `BufferedInputStream` đang đọc ném lỗi và vẫn đóng stream bên dưới.
- `return` sớm từ method nhưng resource vẫn được đóng.

**Tên tệp: `CloseOnFailureDemo.java`**

~~~java
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CloseOnFailureDemo {

    static final class Tracked implements AutoCloseable {
        final String name;
        final List<String> events;
        final boolean failClose;
        boolean closed;

        Tracked(String name, List<String> events, boolean failClose) {
            this.name = name;
            this.events = events;
            this.failClose = failClose;
            events.add("open:" + name);
        }

        @Override
        public void close() throws IOException {
            events.add("close:" + name);
            closed = true;

            if (failClose) {
                throw new IOException("close-" + name);
            }
        }
    }

    static Tracked failAcquire(List<String> events)
            throws IOException {
        events.add("acquire:failed");
        throw new IOException("acquire-failed");
    }

    static final class FailingInput extends InputStream {
        int closeCalls;

        @Override
        public int read() throws IOException {
            throw new IOException("read-failed");
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    static String earlyReturn(List<String> events)
            throws IOException {
        try (Tracked r = new Tracked(
                "early", events, false)) {
            return "done";
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> events = new ArrayList<>();

        try (Tracked first =
                     new Tracked("first", events, false);
             Tracked second =
                     new Tracked("second", events, true)) {

            events.add("body");
            throw new IOException("work-failed");

        } catch (IOException ex) {
            if (!"work-failed".equals(ex.getMessage())
                    || ex.getSuppressed().length != 1
                    || !"close-second".equals(
                            ex.getSuppressed()[0].getMessage())) {
                throw new AssertionError(
                        "Exception hierarchy incorrect");
            }

            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressed=" +
                    ex.getSuppressed()[0].getMessage());
        }

        System.out.println("close-order=" + events);

        List<String> acquisitionEvents = new ArrayList<>();
        try (Tracked held =
                     new Tracked("held", acquisitionEvents, false);
             Tracked unused = failAcquire(acquisitionEvents)) {

            throw new AssertionError("Not reached");

        } catch (IOException ex) {
            System.out.println(
                    "acquisition=" + ex.getMessage());
        }

        System.out.println(
                "acquisition-trace=" + acquisitionEvents);

        FailingInput raw = new FailingInput();

        try (BufferedInputStream wrapped =
                     new BufferedInputStream(raw)) {
            wrapped.read();

        } catch (IOException ex) {
            System.out.println(
                    "read-failure=" + ex.getMessage());
        }

        System.out.println(
                "wrapped-close-calls=" + raw.closeCalls);

        List<String> early = new ArrayList<>();
        System.out.println(
                "early-return=" + earlyReturn(early));
        System.out.println(
                "early-return-closed=" +
                        early.contains("close:early"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CloseOnFailureDemo.java
java CloseOnFailureDemo
~~~

**Stdout đã chạy kiểm tra với JDK:**

~~~text
primary=work-failed
suppressed=close-second
close-order=[open:first, open:second, body, close:second, close:first]
acquisition=acquire-failed
acquisition-trace=[open:held, acquire:failed, close:held]
read-failure=read-failed
wrapped-close-calls=1
early-return=done
early-return-closed=true
~~~

### Vì sao từng dòng quan trọng?

**`primary=work-failed`:** body ném IOException trước; đây là lỗi chính. `close()` lỗi không thay thế nó.

**`suppressed=close-second`:** resource `second` ném IOException khi close; Java vẫn giữ nguyên lỗi body và ghi nhận lỗi cleanup riêng.

**`close-order=...`:** resource được mở theo `first` rồi `second`, nhưng đóng theo `second` rồi `first`. Cả hai close được thử dù second ném lỗi.

**`acquisition=acquire-failed`:** `failAcquire` ném lỗi ở giai đoạn header; body không chạy, lỗi khởi tạo thứ hai được giữ.

**`acquisition-trace=...close:held`:** dù resource thứ hai không tạo được, `held` đã khởi tạo trước vẫn được đóng.

**`read-failure=read-failed`:** stream fake ném lỗi từ `read`. Đây là fault injection, không phải phải gây lỗi file thật.

**`wrapped-close-calls=1`:** `try-with-resources` đóng `BufferedInputStream`, và wrapper đóng `FailingInput` đúng một lần. Trong ví dụ, chỉ outer wrapper được khai báo là resource sở hữu để không phải close nguồn hai lần.

**`early-return-closed=true`:** dù method `earlyReturn` return một chuỗi, `Tracked.close` vẫn được gọi trước khi hoàn tất return.

### Giới hạn của test double

Trong demo, `Tracked.close()` đặt `closed=true` *trước khi* cố ý ném IOException. Điều này chỉ mô phỏng một hợp đồng do fixture định nghĩa. Với tài nguyên thực, `close()` ném lỗi có thể làm trạng thái hậu đóng phụ thuộc API/provider. Không tự kết luận **mọi native handle đã được giải phóng** chỉ vì spy nhận được `close()`.

Tương tự, kết quả `wrapped-close-calls=1` kiểm tra hành vi wrapper của ví dụ. Nếu production code vô tình giữ một tham chiếu khác tới cùng resource và gọi close tiếp, unit test ở riêng scope này không thể tự phát hiện mọi owner bên ngoài. Test cần dựng đúng cấu trúc sở hữu của ứng dụng.

## 8. Thiết kế test unit bằng spy và fault injection

### 8.1. Spy ghi lại số lần và thứ tự close

Một fake resource đơn giản:

~~~java
final class CloseSpy implements AutoCloseable {
    int closeCalls;
    boolean failOnClose;

    @Override
    public void close() throws IOException {
        closeCalls++;
        if (failOnClose) {
            throw new IOException("close failed");
        }
    }
}
~~~

Đây là một class minh họa với import `java.io.IOException`. Nếu class thật nhận resource từ caller, có thể inject `CloseSpy` thông qua một interface/factory thay vì dùng tài nguyên hệ điều hành thật. **Đừng chỉ assert `closeCalls >= 1` khi hợp đồng yêu cầu đúng một owner**; hãy assert đúng giá trị và kiểm tra không double-close vô ý.

### 8.2. Một hàm có hợp đồng ownership rõ ràng

~~~java
static void processOwned(InputStream in) throws IOException {
    try (in) {
        byte[] buffer = new byte[4096];
        while (in.read(buffer) != -1) {
            // Xử lý byte đã đọc.
        }
    }
}
~~~

Đây là một hàm minh họa cho **callee owns**. Test có thể truyền `InputStream` fake có `read(byte[],off,len)` cố ý ném lỗi sau n byte, rồi assert close vẫn được gọi.

Nếu giao diện yêu cầu **caller owns**, hàm cần khác:

~~~java
static void processBorrowed(InputStream in) throws IOException {
    byte[] buffer = new byte[4096];
    while (in.read(buffer) != -1) {
        // Chỉ dùng, KHÔNG tự đóng.
    }
}
~~~

Trong trường hợp borrowed, unit test nên xác nhận callee **không close**, còn caller phải đóng ở scope sở hữu ngoài. Không trộn hai hợp đồng.

### 8.3. Inject lỗi sau khi đã xử lý một phần

Để phát hiện leak xuất hiện khi tiến trình đã xử lý dữ liệu nhưng chưa hoàn thành, fake có thể:

- Cho phép `read()` thành công N lần rồi ném IOException ở lần N+1.
- Cho phép `write(...)` nhận một phần dữ liệu rồi ném lỗi.
- Gây lỗi parse sau khi stream được mở.
- Gây lỗi trong callback xử lý record/file entry.
- Gây lỗi khi finish ZIP/GZIP hoặc flush/close.

Test nên assert **bảo toàn exception gốc, số lần close, và cleanup của mọi resource đã được khởi tạo**, không chỉ kiểm tra method có ném IOException.

### 8.4. JUnit 5 — ví dụ assertThrows

Nếu project đã có JUnit Jupiter, một test có thể viết:

~~~java
@Test
void closesOwnedInputAfterReadFailure() {
    FailingInputStream fake = new FailingInputStream();

    IOException thrown = assertThrows(
            IOException.class,
            () -> service.processOwned(fake));

    assertEquals("read-failed", thrown.getMessage());
    assertEquals(1, fake.closeCalls());
}
~~~

Đây là **mã minh họa JUnit 5, không biên dịch độc lập**: `FailingInputStream`, `service` và phương thức của nó là fixture do project cung cấp; cần dependency/import Jupiter. Khai báo rõ test nhằm tránh nhầm nó với source Java SE 17 tự chạy. Không dùng assertions giả định nếu fake có cache đặc biệt làm thay đổi số lượt `read` thực tế.

## 9. Stream wrapper và đóng nguồn bên dưới

Các wrapper thường chuyển tiếp `close()` tới stream bên dưới. Ví dụ:

~~~java
InputStream raw = Files.newInputStream(path);
try (BufferedInputStream buffered = new BufferedInputStream(raw)) {
    consume(buffered);
}
~~~

Đây là mã minh họa với `Path path` và `consume(...)` được định nghĩa. Chủ sở hữu outer wrapper chịu trách nhiệm đóng; khi `BufferedInputStream.close()`, nó đóng underlying input.

Một cách nhiều người viết:

~~~java
try (InputStream raw = Files.newInputStream(path);
     BufferedInputStream buffered = new BufferedInputStream(raw)) {
    consume(buffered);
}
~~~

Điều này có thể dẫn đến **hai lần thử close raw**: đầu tiên do `buffered.close()`, sau đó TWR gọi `raw.close()` trong thứ tự ngược. Với `Closeable` implementations chuẩn, close thường idempotent, nhưng **không nên mặc định mọi wrapper/resource tùy biến chấp nhận double-close an toàn**. Trong code rõ ownership, thường chỉ cần outermost wrapper trong resource header, hoặc dùng một thiết kế chuyển ownership có kiểm soát.

### Reader/Writer và charset bridge

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    return reader.readLine();
}
~~~

Đây là mã minh họa bên trong method trả `String`, có `Path path`. `readLine` có thể trả null hay ném IOException; cả hai đường đi đều đóng reader.

`BufferedWriter` và `OutputStreamWriter` có thể tạo lỗi trong `flush/close` do bước encode và ghi byte. Vì vậy kiểm thử **write-time failure** và **close-time failure** đều quan trọng, nhất là với định dạng có trailer/finalization.

### Trường hợp wrapper không sở hữu underlying

Không phải mọi wrapper trong framework đều tự động được phép đóng stream bên dưới. Có API xây wrapper “close shield” để chặn close tới stream dùng chung. Nếu stream được **mượn** từ caller, wrapper hoặc factory phải thể hiện rõ **close có lan xuống nguồn hay không**. Test ownership ở lớp này để tránh vừa leak vừa vô tình đóng tài nguyên mà caller còn dùng.

## 10. Kiểm tra resource API khác nhau của Java NIO

### 10.1. FileChannel / SeekableByteChannel

`FileChannel` là `AutoCloseable`; khi read/write ném IOException, code phải đóng channel:

~~~java
try (FileChannel channel = FileChannel.open(
        path, StandardOpenOption.READ)) {
    ByteBuffer buffer = ByteBuffer.allocate(8192);
    while (channel.read(buffer) != -1) {
        buffer.clear();
    }
}
~~~

Đây là mã minh họa cho cách quản lý tài nguyên, không phải parser dữ liệu; khi dùng buffer có `remaining` bằng 0, `read` có thể trả 0 và logic trong ví dụ phải bảo đảm buffer được `clear()`. Trong production, cần xử lý no-progress đúng từng loại channel, không busy loop vô hạn.

Kiểm thử nên bảo đảm `channel.close()` được gọi nếu đoạn đọc/parse ném IOException. `Channel.isOpen()` cho phép kiểm tra trạng thái API sau close ở integration test. Nhưng thông tin “không còn handle trong OS” có thể cần quan sát độc lập khi vấn đề nằm ở native resources.

### 10.2. SocketChannel / ServerSocketChannel

Socket có thể lỗi ở `connect`, `accept`, `read`, `write` hoặc lúc close. Test cần xác định ai sở hữu accepted socket và listener:

- Đóng listener **không được giả định** là tự đóng mọi socket đã được accept.
- Khi accept thành công, mỗi accepted socket cần có cleanup path.
- Trên lỗi xử lý một client, không vô tình đóng toàn bộ listener nếu server còn tiếp tục phục vụ.
- Dùng loopback + port 0 cho integration test; đặt deadline để test không treo vô hạn.

Đo close với spy channel thường cần abstraction bởi các class channel có contract riêng; không nên tạo fake bằng cách override tùy tiện method final/native nếu test không phản ánh implementation thật.

### 10.3. ZipFile và ZipInputStream

ZIP là ví dụ quan trọng vì stream có thể lỗi **trong lúc giải nén** hoặc khi kiểm tra CRC:

~~~java
try (ZipFile zip = new ZipFile(zipPath.toFile())) {
    ZipEntry entry = zip.getEntry("data.txt");
    if (entry == null) {
        throw new IOException("Missing entry");
    }

    try (InputStream content = zip.getInputStream(entry)) {
        // Read / parse / validate content; exception có thể xảy ra.
    }
}
~~~

Đây là mã minh họa có `Path zipPath`. Khi exception xuất hiện, TWR đóng entry stream và ZipFile theo scope. Với `ZipInputStream`, đặt nó trong TWR và theo dõi việc đóng underlying stream nếu wrapper sở hữu nguồn đó. Với `ZipOutputStream` hoặc `GZIPOutputStream`, `close` có thể hoàn tất cấu trúc nén; đừng quên thử nhánh lỗi khi finalizing metadata.

### 10.4. DirectoryStream, Files.list, Files.walk và Files.lines

Không phải `Stream<T>` nào cũng giữ OS resource, nhưng **Stream do những Files API này trả về có thể giữ file/directory handle**. Cần đóng rõ:

~~~java
try (Stream<Path> paths = Files.list(directory)) {
    paths.forEach(System.out::println);
}
~~~

~~~java
try (Stream<Path> paths = Files.walk(directory)) {
    long count = paths.filter(Files::isRegularFile).count();
}
~~~

~~~java
try (Stream<String> lines = Files.lines(
        logFile, StandardCharsets.UTF_8)) {
    long count = lines.filter(line -> line.contains("ERROR"))
                      .count();
}
~~~

Đây là những đoạn minh họa, cần `java.nio.file.*`, `java.nio.charset.*`, `java.util.stream.Stream` và các biến `directory`/`logFile`. Chúng chưa phải các method độc lập và giá trị count chỉ là ví dụ sử dụng, không in output.

**Lưu ý:** `Stream.count()`/`findFirst()`/`forEach()` là terminal operation, nhưng **không thay cho `close()`** của file-backed stream. Nếu pipeline ném `UncheckedIOException` trong lúc iteration, TWR vẫn đóng stream.

### 10.5. Files.copy và API tự quản lý nguồn

Nếu API nhận `InputStream` từ caller, không mặc định nó sẽ đóng stream. Ví dụ `Files.copy(InputStream, Path, ...)` không thay thế scope sở hữu của input stream; caller cần đặt input vào TWR khi mình là owner.

Đừng suy luận quyền sở hữu chỉ vì phương thức đọc tới EOF; **EOF không đồng nghĩa đã close**.

## 11. Thử nghiệm lỗi ở giai đoạn khởi tạo: chỗ dễ leak

Một resource factory có thể gồm nhiều bước:

~~~java
Resource openResource(Path path) throws IOException {
    InputStream in = Files.newInputStream(path);
    // Bước khởi tạo khác có thể ném lỗi tại đây.
    return new Resource(in);
}
~~~

Đây là mã giả, `Resource` là type ứng dụng tự định nghĩa. Nếu bước giữa hai dòng thất bại trước khi object trả về, caller không nhận được `Resource` để close. Cần thiết kế factory chịu trách nhiệm cleanup của phần đã mở:

~~~java
Resource openResource(Path path) throws IOException {
    InputStream in = Files.newInputStream(path);
    boolean transferred = false;
    try {
        Resource resource = initializeOwnedResource(in);
        transferred = true; // Ownership chuyển sang resource trả về.
        return resource;
    } finally {
        if (!transferred) {
            in.close();
        }
    }
}
~~~

Đây là **mã minh họa**, không biên dịch độc lập vì `Resource` và `initializeOwnedResource(...)` do ứng dụng định nghĩa. Nó thể hiện ownership transfer, nhưng nếu cả khởi tạo và cleanup lỗi, block `finally` trên có thể **che mất lỗi khởi tạo**; production cần dùng nested try-with-resources hoặc thu thập suppressed exception để giữ nguyên nguyên nhân. Cách tốt hơn là giảm số bước manual transfer và đặt tài nguyên đã mở vào scope TWR càng sớm càng tốt.

Ví dụ kiểu cleanup rõ hơn bằng TWR:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    // Các bước chuẩn bị hoàn toàn tiêu thụ in trong scope này.
    return parseData(in);
}
~~~

Đoạn này minh họa khi `parseData` trả kết quả **không cần giữ stream**. Nếu `parseData` cần trả object sở hữu chính `in`, không thể đóng `in` trước return: phải thiết kế ownership transfer riêng.

### Kiểm thử khởi tạo mở dở

- Lỗi trước khi open: không có gì phải close.
- Open A thành công, open B thất bại: A phải close.
- Factory mở file rồi parse header lỗi: file phải close.
- Wrapper constructor lỗi sau khi đã nhận nguồn: factory/caller phải biết ai đang sở hữu nguồn và cleanup.
- Resource trả về thành công nhưng caller từ chối: caller phải close theo hợp đồng.

Không nên chỉ kiểm thử exception tại một vị trí; chính **điểm xảy ra lỗi** quyết định những tài nguyên nào đã tồn tại.

## 12. Test double không đủ để xác minh handle thật

Spy đo lời gọi Java rất hữu ích, nhưng có các giới hạn:

- `closeCalls==1` xác nhận method `close` được gọi trên fake, chưa chứng minh file descriptor được kernel trả lại.
- Spy không kiểm tra kernel buffer, delayed write, flush hoặc corruption.
- Một wrapper của thư viện khác có thể sở hữu thêm resource ngoài phạm vi fake.
- Lỗi đồng thời (thread khác đang đọc khi close) có thể tạo trạng thái và exception không xuất hiện ở unit test tuần tự.

### Integration test có giá trị gì?

Với file thực:

1. Dùng `Files.createTempFile` hoặc `Files.createTempDirectory` để có vùng test riêng.
2. Mở resource thật qua code production.
3. Tiêm lỗi parse/processing thay vì dựa vào lỗi thiết bị ngẫu nhiên.
4. Sau khi method kết thúc với exception, kiểm tra `isOpen()` (nếu API hỗ trợ), đọc/ghi lại file qua handle mới và cleanup tệp tạm.
5. Nếu cần, dùng OS-level tools để xem số descriptor trước/sau nhiều lần test.

**Không dùng khả năng xóa file như tiêu chí chung xuyên nền tảng**. Một số hệ điều hành cho unlink file đang mở, nên `Files.deleteIfExists` thành công **không chứng minh** handle đã close.

### Stress/soak test

Lỗi leak nhỏ khó nhìn thấy trong một lần chạy. Thử 1.000–10.000 lượt “open → lỗi đọc → cleanup” trong môi trường có quota phù hợp và quan sát:

- Số file handles/sockets.
- Số thread/task còn giữ resource.
- Thời gian và memory qua thời gian.
- Số request lỗi và số exception bị suppressed.
- Tình trạng directory stream, ZIP reader còn mở.

Kết luận dựa vào xu hướng ổn định và môi trường xác định, không dựa vào số cố định từ một OS/JDK khác. Mỗi test phải có cleanup riêng dù assertion thất bại.

## 13. Lỗi close trong hệ thống bất đồng bộ và nhiều thread

Với `AsynchronousFileChannel`, `AsynchronousSocketChannel` và các executor xử lý I/O, kết thúc một method chưa chắc kết thúc tất cả operation đang pending.

Cần xác định lifecycle:

~~~text
OPEN
  |
  +--> SUBMITTED OPERATIONS
  |          |
  |          +--> COMPLETED / FAILED / CANCELLED
  |
  +--> SHUTDOWN REQUEST
                |
         ngừng submit mới
                |
         chờ/giải quyết pending I/O theo contract
                |
             close channel
                |
             CLOSED
~~~

Một số API cho phép close channel trong khi operation đang pending; operation có thể kết thúc bất thường như `AsynchronousCloseException`. Unit test chỉ quan sát “close đã gọi ngay khi method throw” sẽ **không đủ** nếu trách nhiệm đóng thuộc một component chạy background; cần kiểm tra trạng thái shutdown sau khi các task/handler đã được giải quyết.

### Tránh flaky test bằng Thread.sleep

Không nên viết:

~~~java
startAsyncOperation();
Thread.sleep(200); // đoán operation đã xong
assertTrue(isClosed());
~~~

Đây là **phản ví dụ** không tự chạy. Sử dụng `CountDownLatch`, `CompletableFuture`, `Future.get(timeout, unit)` hoặc synchronization rõ ràng với **deadline** để chờ sự kiện close. Timeout là giới hạn cho test, không phải bằng chứng operation chắc chắn đã xong khi hết thời gian.

### Trường hợp thread bị interrupt

Java `try-with-resources` vẫn cleanup khi scope thoát vì exception do interrupt, nhưng bản thân API close có hợp đồng và ngoại lệ riêng. Với `AutoCloseable` tùy biến, Javadoc khuyến cáo không dùng `InterruptedException` làm lỗi close vì có thể gây hành vi khó dự đoán khi exception bị suppressed. Trong logic xử lý `InterruptedException`, cần quyết định có phục hồi interrupt flag và dừng theo chính sách ứng dụng hay không.

## 14. Thiết kế API dễ kiểm thử việc close

Một component I/O dễ test thường có các đặc điểm:

- **Constructor/factory inject được resource** hoặc resource factory, để mô phỏng lỗi đọc/ghi/close.
- **Hợp đồng owner rõ ràng**: ai tạo, ai dùng, ai đóng, ai chuyển ownership.
- **Không dùng global singleton cho stream mutable** nếu không thực sự cần chia sẻ.
- **Có giới hạn scope**: mở resource sát lúc dùng và close sớm khi không còn cần.
- **Không nuốt IOException**: lỗi body và lỗi cleanup được propagate/log theo chính sách.
- **Không phụ thuộc GC/finalizer/Cleaner làm cơ chế release chính**: chúng không cho thời điểm close xác định.
- **Không chỉ kiểm tra happy path**; thử lỗi khởi tạo, partial read/write, parser, close và return sớm.

Một phân tách điển hình:

~~~java
// Core logic làm việc trên stream do caller cho mượn:
static Result parse(InputStream borrowed) throws IOException {
    // Parse; không close borrowed.
    return decode(borrowed);
}

// Boundary nhận ownership của file stream:
static Result parseFile(Path path) throws IOException {
    try (InputStream owned = Files.newInputStream(path)) {
        return parse(owned);
    }
}
~~~

Đây là **mã minh họa**, `Result` và `decode` thuộc ứng dụng. Thiết kế này cho phép unit test `parse` bằng fake input và kiểm tra `parseFile` quản lý vòng đời tài nguyên mở từ filesystem. Không cần mock API static `Files.newInputStream` cho mọi test.

### Kiểm thử với các phương thức có trách nhiệm đóng rõ

Nếu một API có tham số `InputStream stream` và trách nhiệm **caller owns**, test nên khẳng định stream **vẫn open sau khi method kết thúc** (kể cả lỗi). Trái lại boundary `parseFile` phải khẳng định file stream được đóng khi bất kỳ lỗi nào xảy ra. Hai assertion khác nhau đều có thể đúng vì ownership khác nhau.

## 15. Vì sao kiểm thử close cần xét cả lỗi trong finally?

Khi bắt buộc phải dùng `finally` thủ công vì không có `AutoCloseable` phù hợp hoặc ownership phức tạp:

1. Giữ được exception gốc.
2. Không bỏ sót các resource cần cleanup nếu một close thất bại.
3. Không close tài nguyên mà method không sở hữu.
4. Không bỏ qua phần khởi tạo chưa hoàn tất.
5. Không giấu các suppressed exceptions trong logging/monitoring.
6. Tránh double-close resource có semantics không idempotent.

Với nhiều tài nguyên, tự viết chính xác tất cả trường hợp này khá dễ sai; **ưu tiên try-with-resources** bất cứ khi nào phù hợp. Nếu phải tự tổng hợp exception, có thể đính lỗi cleanup vào primary bằng `addSuppressed`, nhưng phải cẩn thận không tự suppress chính nó hoặc nhận null.

### Khi nào cần catch và khi nào không?

**Đơn giản và thường đủ:**

~~~java
try (InputStream in = Files.newInputStream(path)) {
    return parse(in);
}
~~~

Nếu caller hiểu IOException, không cần catch chỉ để log rồi throw lại. Nếu ứng dụng cần bổ sung ngữ cảnh, hãy preserve cause:

~~~java
try (InputStream in = Files.newInputStream(path)) {
    return parse(in);
} catch (IOException ex) {
    throw new IOException("Cannot parse: " + path, ex);
}
~~~

Hai đoạn là **mã minh họa trong method trả giá trị parser**, `parse` cần được định nghĩa. Với exception được bọc, lỗi suppressed vẫn nằm trên exception nguyên nhân `ex`; công cụ log nên duyệt cause và suppressed để không bỏ mất lỗi close.

**Không nên:**

~~~java
catch (IOException ex) {
    return null; // nuốt lỗi, caller tưởng đọc thành công
}
~~~

Đây là phản ví dụ. Nuốt IOException khiến tài nguyên có thể đã được đóng nhưng dữ liệu đầu ra/flow logic không còn đáng tin. Nếu sản phẩm có fallback hợp lệ, phải thể hiện bằng return type rõ nghĩa thay vì biến lỗi thành thành công mơ hồ.

## 16. Checklist cuối cùng cho test và code review

Trước khi coi một đường I/O đã có cleanup đúng, kiểm tra:

- [ ] Xác định **resource owner** cho từng stream/channel/wrapper.
- [ ] Tất cả resource mở thành công nằm trong scope `try-with-resources` hoặc cleanup tương đương.
- [ ] Khi lỗi ở bước mở thứ hai, resource đầu vẫn đóng.
- [ ] Khi lỗi `read`, `write`, parse hoặc callback, cleanup vẫn chạy.
- [ ] Khi `return`/`break` hoặc early exit, cleanup chạy đúng.
- [ ] Nhiều resource đóng **ngược thứ tự**, một close lỗi không cản close những resource khác.
- [ ] Exception trong body là primary và lỗi close nằm ở `getSuppressed()`.
- [ ] Wrapper đóng underlying theo đúng ownership; không double-close resource không idempotent.
- [ ] File-backed streams từ `Files.lines/list/walk` được đóng ngay cả khi terminal operation ném lỗi.
- [ ] Fake/spy xác nhận **đã gọi close**; integration test kiểm tra thực tế cần thiết.
- [ ] Không suy luận đã release OS handle chỉ từ khả năng xóa file.
- [ ] Async tasks có deadline và synchronization để kiểm tra completion/close, không dùng sleep cố định.
- [ ] Exception và suppressed được log/propagate đủ để chẩn đoán.
- [ ] Test fixtures tự giải phóng tài nguyên ngay cả khi assertion thất bại.

## 17. Các lỗi thường gặp

1. **“Đã tới EOF là stream tự đóng.”** Sai: `read() == -1` không thay `close()`.
2. **“GC sẽ tự thu hồi file handle kịp thời.”** Không có thời điểm xác định; phải close.
3. **“Chỉ cần close ở happy path.”** Sai: error path còn dễ leak hơn.
4. **“Mỗi tài nguyên khai báo TWR đóng theo thứ tự mở.”** Sai: đóng theo thứ tự **ngược**.
5. **“Nếu open resource thứ hai thất bại thì first không được đóng.”** Sai với first đã khởi tạo thành công trong header.
6. **“Nếu close thứ hai ném lỗi, Java bỏ close thứ nhất.”** Sai: các resource đã mở vẫn được thử đóng.
7. **“Lỗi close luôn đè lên lỗi read.”** Sai với TWR; lỗi close được suppressed nếu đã có primary.
8. **“getCause và getSuppressed là một.”** Sai: cause chain và lỗi bổ sung khác nhau.
9. **“Spy.closeCalls==1 bảo đảm không còn OS descriptor.”** Sai: cần test thực/instrumentation theo mục tiêu.
10. **“Gọi close hai lần mọi AutoCloseable đều an toàn.”** Sai: AutoCloseable không yêu cầu idempotent.
11. **“Đóng BufferedInputStream không ảnh hưởng underlying stream.”** Thông thường wrapper đóng nguồn, cần hiểu ownership.
12. **“Một terminal operation trên Files.lines tự close stream.”** Sai: file-backed stream cần close.
13. **“Constructor/factory ném lỗi trước khi return sẽ luôn được TWR tự cleanup bên trong.”** Sai: phần tài nguyên mở dở chưa trao cho TWR phải do factory tự xử lý.
14. **“File xóa được chứng minh stream đã đóng.”** Sai trên OS có unlink semantics cho file đang mở.
15. **“Thread.sleep(100) là đủ để test close async.”** Không xác định; dùng synchronization và deadline.
16. **“close() không thể ném IOException.”** Sai: đặc biệt output flush/finalization có thể lỗi.
17. **“catch rồi return null là cách xử lý lỗi I/O an toàn.”** Thường làm mất ngữ cảnh và tạo thành công giả.
18. **“Đóng listener cũng tự đóng mọi accepted channel.”** Không nên giả định, mỗi channel cần ownership riêng.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Trong TWR có hai resource A, B. Body ném `IOException("read")`, còn `B.close()` ném `IOException("close")`. Exception caller thấy là gì?

**Đáp án:** `"read"` là primary; `"close"` nằm trong `getSuppressed()`. Java vẫn thử đóng A.

**Câu 2.** Resource A mở thành công nhưng khởi tạo B ném IOException. A có close không?

**Đáp án:** Có, nếu A là resource đã khởi tạo thành công trong header TWR. Body không chạy và exception khởi tạo B là primary.

**Câu 3.** Nếu method `return` bên trong TWR, close chạy trước hay sau khi method đã hoàn tất return?

**Đáp án:** Close chạy khi rời scope trước khi method hoàn tất trả kết quả. Nếu close ném exception, nó có thể khiến phương thức hoàn tất bằng exception thay vì return bình thường.

**Câu 4.** Tại sao unit test dùng CloseSpy vẫn cần một số integration test?

**Đáp án:** Spy kiểm tra lời gọi close theo contract của fake; không kiểm tra đầy đủ native handles, output finalization hay các resource bên trong implementation thật.

**Câu 5.** Tại sao `Files.lines(path).count()` không đủ để đảm bảo file đã đóng?

**Đáp án:** Terminal operation xử lý các phần tử, không cam kết đóng file-backed stream. Dùng TWR cho stream trả về.

**Câu 6.** Một service dùng `BufferedInputStream` bọc `System.in`. Service có nên mặc định đóng wrapper khi xong không?

**Đáp án:** Không thể quyết định nếu chưa biết ownership. Đóng wrapper thường đóng nguồn bên dưới; với `System.in` dùng chung, phải thiết kế borrowed ownership hoặc close-shield rõ ràng.

**Câu 7.** Close handler của một object có thể ném IOException. Unit test nên kiểm tra gì ngoài `closeCalls`?

**Đáp án:** Kiểm tra primary/suppressed exception, thứ tự close của các resource khác và phương thức có propagate lỗi đúng contract hay không.

**Câu 8.** Nếu một callback I/O bất đồng bộ đang pending và code yêu cầu shutdown, khi nào có thể kết luận cleanup hoàn tất?

**Đáp án:** Sau khi chính sách cancel/completion và đóng channel/resource đã được xác nhận bằng signal/synchronization có deadline; không thể suy luận chỉ từ việc method request shutdown trả về.

### Tài liệu chính thức Java 17

- [JLS `14.20.3 — try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3)
- [AutoCloseable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html)
- [Closeable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html)
- [Throwable.getSuppressed](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed())
- [Files — lines, list, walk](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [BufferedInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedInputStream.html)
- [FileChannel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html)
- [ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-008">Kiểm tra symbolic link, quyền và hành vi theo nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra symbolic link, quyền truy cập và hành vi theo nền tảng trong Java NIO.2

## 1. Mục tiêu bài học

Một đoạn mã I/O chạy đúng trên Linux có thể gặp lỗi trên Windows, macOS, filesystem mạng, ổ USB hoặc filesystem ảo. Các nguyên nhân thường liên quan đến **symbolic link (liên kết tượng trưng)**, **quyền của tệp**, **file attribute view**, chính sách của provider và những thay đổi xảy ra giữa lúc kiểm tra với lúc sử dụng đường dẫn.

Ví dụ, đoạn mã sau không phải một phép bảo đảm an toàn:

```java
if (Files.exists(path) && Files.isReadable(path)) {
    // Không bảo đảm thao tác đọc sau đó sẽ thành công.
    String content = Files.readString(path);
}
```

Đây là snippet, chưa phải chương trình độc lập. Sau phép kiểm tra, file có thể bị xóa, thay quyền, đổi tên, thay bằng symbolic link hoặc khóa bởi cơ chế khác; `readString` vẫn có thể ném `IOException`.

Sau bài này, bạn có thể:

1. Kiểm tra một `Path` có phải symbolic link không, đọc link target và phân biệt **follow** với **`NOFOLLOW_LINKS`**.
2. Giải thích vì sao một link bị đứt có thể cho `Files.exists(path) == false` nhưng `Files.exists(path, NOFOLLOW_LINKS) == true`.
3. Xem basic attributes và phát hiện khả năng hỗ trợ **POSIX permissions**, **DOS attributes** hoặc **ACL** mà không giả định mọi platform đều có.
4. Phân biệt `Files.isReadable/isWritable/isExecutable` với phép kiểm tra quyền truy cập có thể tin cậy; hiểu các race kiểu **TOCTOU**.
5. Viết ví dụ Java 17+ và test có kết quả **phụ thuộc nền tảng được nhận diện rõ ràng**, thay vì dùng kết quả Linux làm chuẩn cho mọi hệ điều hành.
6. Xử lý unsupported operation, thiếu quyền, symbolic-link loop, đường dẫn tạm và cleanup an toàn.

Trong nhóm **Kiểm thử và chẩn đoán**, trọng tâm là biết **quan sát, kiểm chứng và đưa ra kết luận đúng phạm vi**, không phải cố vượt qua phân quyền hệ điều hành.

## 2. Symbolic link là gì?

Symbolic link (*symlink*) là một filesystem entry chứa tham chiếu tới một đường dẫn target. Nó **không phải** bản sao nội dung file đích và cũng không giống **hard link**.

```text
work/
  actual.txt       nội dung: "HELLO"
  shortcut.txt -->  actual.txt      (symbolic link tương đối)
```

Khi chương trình truy cập `shortcut.txt`, nhiều thao tác `Files` mặc định **đi theo symlink** tới `actual.txt`; một số thao tác được thiết kế để quan sát chính đối tượng link.

Cần phân biệt:

- **Link path**: đường dẫn nơi symbolic link được lưu, ví dụ `work/shortcut.txt`.
- **Link target**: giá trị target bên trong link, ví dụ `actual.txt` hoặc một đường dẫn tuyệt đối.
- **Resolved target**: đường dẫn nhận được khi hệ thống giải quyết target tương đối với **thư mục chứa link**, không phải mặc định theo thư mục làm việc của Java process.
- **Dangling/broken link**: link vẫn tồn tại nhưng target không tồn tại hoặc không thể được giải quyết.
- **Link cycle**: chuỗi link hoặc liên kết tới thư mục tạo vòng lặp khi theo dõi.

Ví dụ: `Files.readSymbolicLink(link)` trả target được lưu trong link và **không bắt buộc trả đường dẫn tuyệt đối**. Nếu target là relative, ngữ cảnh giải quyết chính là thư mục chứa link.

## 3. Những phương thức và tùy chọn quan trọng

| API | Công dụng | Lưu ý |
| --- | --- | --- |
| `Files.isSymbolicLink(path)` | Hỏi entry có phải symbolic link không | Nếu không thể xác định, có thể trả false |
| `Files.createSymbolicLink(link, target)` | Tạo symlink; operation tùy khả năng provider/OS | Có thể không hỗ trợ hoặc thiếu quyền |
| `Files.readSymbolicLink(link)` | Đọc target được lưu trong link | Target có thể là relative hoặc chưa tồn tại |
| `Files.exists(path)` | Kiểm tra path tồn tại theo quy tắc follow mặc định | Trả false khi không xác định được hoặc link target mất |
| `Files.exists(path, NOFOLLOW_LINKS)` | Kiểm tra chính entry link mà không theo target | Có thể true dù target mất |
| `Files.isRegularFile(path, NOFOLLOW_LINKS)` | Kiểm tra loại của chính entry | Symlink không được xem như regular file |
| `Files.readAttributes(path, BasicFileAttributes.class, options)` | Đọc loại, size, thời gian, file key | Có thể ném `IOException`; dùng để phân biệt lỗi |
| `Path.toRealPath(options)` | Trả đường dẫn thực theo contract provider | Không phải một biện pháp authorization độc lập |
| `Files.isSameFile(a, b)` | Kiểm tra cùng file khi có thể | Có thể truy cập filesystem và ném `IOException` |
| `Files.delete(link)` | Xóa directory entry chỉ định | Xóa symlink không tự xóa target |
| `Files.walkFileTree(...)` | Duyệt cây thư mục | Mặc định không đi theo symlink; `FOLLOW_LINKS` có tác động riêng |

Import thường dùng:

```java
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
```

Đây là tập import phục vụ các snippet trong bài. Khi cần bắt lỗi, dùng `IOException` và các subclass hoặc `UnsupportedOperationException` theo đặc điểm API.

## 4. `NOFOLLOW_LINKS` có nghĩa chính xác gì?

`LinkOption.NOFOLLOW_LINKS` điều khiển việc đi theo symbolic link cho **những API có tham số `LinkOption...` hoặc nhận option này theo hợp đồng**.

Giả sử:

```text
dir/
  item.txt
  link.txt -> item.txt
  broken.txt -> missing.txt
```

Trên hệ thống hỗ trợ symlink và còn nguyên trạng thái:

| Phép kiểm tra | `item.txt` | `link.txt` | `broken.txt` |
| --- | --- | --- | --- |
| `Files.exists(path)` | true | true | false |
| `Files.exists(path, NOFOLLOW_LINKS)` | true | true | true |
| `Files.isSymbolicLink(path)` | false | true | true |
| `Files.isRegularFile(path)` | true | true | false |
| `Files.isRegularFile(path, NOFOLLOW_LINKS)` | true | false | false |
| `Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS).isSymbolicLink()` | false | true | true |

Bảng nêu **tình huống file local bình thường, có quyền và provider phân loại đúng**, không hứa rằng `Files.exists` luôn trả true/false như trên khi có I/O lỗi hoặc điều kiện truy cập không đủ.

**Hai cảnh báo:**

1. Không phải phương thức nào của `Files` cũng nhận hoặc tuân theo `NOFOLLOW_LINKS` theo cùng cách; đọc Javadoc của phương thức cụ thể.
2. `NOFOLLOW_LINKS` thường kiểm soát việc đi theo **symbolic link cuối cùng** cho API được gọi; nó **không tự động bảo đảm mọi thành phần thư mục cha không có symlink hoặc không bị đổi trong lúc thao tác**.

Nếu một yêu cầu bảo mật là “không được vượt ra khỏi thư mục gốc kể cả khi có symlink trong path”, chỉ kiểm tra `normalize()` hoặc `exists(..., NOFOLLOW_LINKS)` là chưa đủ.

## 5. Ví dụ Java 17+ hoàn chỉnh: symlink hợp lệ và link bị đứt

Tạo **`SymbolicLinkInspectionDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class SymbolicLinkInspectionDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("nio-symlink-");
        Path actual = dir.resolve("actual.txt");
        Path link = dir.resolve("shortcut.txt");
        Path broken = dir.resolve("broken.txt");

        try {
            Files.writeString(actual, "HELLO");

            boolean linksAvailable;
            try {
                Files.createSymbolicLink(link, Path.of("actual.txt"));
                Files.createSymbolicLink(broken, Path.of("missing.txt"));
                linksAvailable = true;
            } catch (UnsupportedOperationException
                     | IOException | SecurityException ex) {
                linksAvailable = false;
            }

            System.out.println("symbolicLinksAvailable=" + linksAvailable);

            if (linksAvailable) {
                System.out.println("linkIsSymbolic="
                        + Files.isSymbolicLink(link));
                System.out.println("linkTargetIsRelative="
                        + !Files.readSymbolicLink(link).isAbsolute());
                System.out.println("regularWhenFollowed="
                        + Files.isRegularFile(link));
                System.out.println("regularWhenNotFollowed="
                        + Files.isRegularFile(
                                link, LinkOption.NOFOLLOW_LINKS));

                BasicFileAttributes attrs = Files.readAttributes(
                        link, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                System.out.println("attributesSaySymbolic="
                        + attrs.isSymbolicLink());

                System.out.println("brokenExistsFollow="
                        + Files.exists(broken));
                System.out.println("brokenExistsNoFollow="
                        + Files.exists(broken, LinkOption.NOFOLLOW_LINKS));
                System.out.println("sameAsActual="
                        + Files.isSameFile(link, actual));

                Files.delete(link); // xóa link, không xóa actual
                System.out.println("targetSurvivedDelete="
                        + Files.exists(actual));
            }
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(broken);
            Files.deleteIfExists(actual);
            Files.deleteIfExists(dir);
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 SymbolicLinkInspectionDemo.java
java SymbolicLinkInspectionDemo
```

**Stdout khi nền tảng hỗ trợ tạo symlink và các phép I/O thành công:**

```text
symbolicLinksAvailable=true
linkIsSymbolic=true
linkTargetIsRelative=true
regularWhenFollowed=true
regularWhenNotFollowed=false
attributesSaySymbolic=true
brokenExistsFollow=false
brokenExistsNoFollow=true
sameAsActual=true
targetSurvivedDelete=true
```

Nếu provider hoặc quyền thực thi **không cho tạo symbolic link**, dòng đầu có thể là:

```text
symbolicLinksAvailable=false
```

và chương trình bỏ qua phần kiểm tra symlink. Việc chỉ thấy `false` không chứng minh “Windows không hỗ trợ symlink”: nguyên nhân có thể là quyền, filesystem, policy, provider hoặc điều kiện I/O. Đừng tạo test bắt buộc symlink phải tạo được trên mọi CI runner.

**Giải thích:**

- `createSymbolicLink(link, Path.of("actual.txt"))` tạo target tương đối: đường dẫn được giải quyết từ **thư mục chứa `shortcut.txt`**.
- `readSymbolicLink(link).isAbsolute()` bằng false vì target được lưu là relative.
- Không kèm `NOFOLLOW_LINKS`, `isRegularFile(link)` kiểm tra target `actual.txt`; có `NOFOLLOW_LINKS`, nó kiểm tra chính entry link.
- `BasicFileAttributes` cung cấp phép thử loại file với lỗi I/O tường minh.
- `broken.txt` vẫn có entry là symlink nhưng target `missing.txt` không tồn tại, nên hai kiểu `exists` khác nhau.
- `Files.delete(link)` không xóa `actual.txt`.
- Cleanup thử xóa từng entry được tạo; ví dụ không thay đổi file nào ngoài thư mục tạm do chính nó tạo.

Phần catch khi tạo link chỉ xử lý lỗi khả năng nền tảng để **skip kiểm tra link**. Các phép I/O tiếp theo vẫn có thể thất bại; ví dụ để exception đó lộ ra như lỗi test, không che tất cả lỗi bằng `catch (Exception)`.

## 6. Không nhầm symlink với hard link và shortcut giao diện

**Hard link** là một directory entry khác tham chiếu cùng đối tượng file, được tạo bằng `Files.createLink(link, existing)` nếu provider/filesystem hỗ trợ. Nó thường không được `Files.isSymbolicLink()` nhận là symbolic link.

| Đặc điểm | Symbolic link | Hard link |
| --- | --- | --- |
| Đích có thể chưa tồn tại? | Có thể | Thường phải trỏ tới file đang tồn tại |
| `Files.isSymbolicLink` | true cho symlink hợp lệ được nhận diện | false |
| `Files.readSymbolicLink` | Đọc chuỗi target được lưu | Không áp dụng |
| Xóa một entry | Xóa link, không tự xóa target | Gỡ một directory entry; dữ liệu có thể còn qua hard link khác |
| Khả năng hỗ trợ | Có thể bị OS/policy/provider giới hạn | Có thể khác filesystem/provider |
| Tác động đến kiểm thử | Test follow/NOFOLLOW | Test identity và số lượng link theo OS khi phù hợp |

Tệp shortcut kiểu `.lnk` của Windows thường là **một file do shell xử lý**, không được mặc nhiên coi là filesystem symbolic link của `Files.isSymbolicLink`. Tương tự alias của GUI và junction/reparse point có ngữ nghĩa platform riêng; không suy ra mọi loại liên kết đều xuất hiện đồng nhất qua `java.nio.file.Files`.

## 7. Quyền truy cập trong Java: kiểm tra khác với mở thật

Các phép thử tiện dụng:

```java
boolean readable = Files.isReadable(path);
boolean writable = Files.isWritable(path);
boolean executable = Files.isExecutable(path);
```

Đây là snippet cần có `path`. Những phương thức này hữu ích để **hiển thị trạng thái quan sát** hoặc tối ưu đường đi, nhưng:

- Trả true không phải là cam kết lần `Files.newInputStream`/`newOutputStream` kế tiếp sẽ thành công.
- Trả false có thể do không có quyền **hoặc không thể xác định** quyền tại thời điểm đó.
- Quyền thực tế có thể liên quan owner, group, ACL, chính sách sandbox, share mode, network mounts hoặc process identity.
- File có thể bị đổi permission/xóa/thay link giữa phép thử và thao tác.
- Không suy từ `isWritable` ra chắc chắn còn quota/dung lượng đĩa hoặc storage hoạt động.

Cách xử lý đúng là **thực hiện thao tác I/O cần thiết và bắt exception phát sinh**, thay vì dùng `isReadable` như một “authorization check” của người dùng.

Ví dụ:

```java
try (var in = Files.newInputStream(path)) {
    // Xử lý nội dung và kiểm tra kết quả I/O.
} catch (java.nio.file.NoSuchFileException ex) {
    // File/đường dẫn không tồn tại ở thời điểm thao tác.
} catch (java.nio.file.AccessDeniedException ex) {
    // OS/provider từ chối truy cập.
} catch (java.io.IOException ex) {
    // Các lỗi I/O khác: link loop, device, file system...
}
```

Đây là snippet, không phải chương trình độc lập. Các ngoại lệ cụ thể không nhất thiết xuất hiện giống nhau trên mọi provider; nhiều lỗi được báo qua `FileSystemException` hoặc `IOException` chung. **Không được suy diễn `AccessDeniedException` thay thế authorization nghiệp vụ**; hệ điều hành và ứng dụng có các mô hình quyền riêng.

## 8. Basic attributes là điểm bắt đầu portable

`BasicFileAttributes` dùng cho những thuộc tính cơ bản:

```java
BasicFileAttributes info = Files.readAttributes(
        path, BasicFileAttributes.class,
        LinkOption.NOFOLLOW_LINKS);

System.out.println(info.isRegularFile());
System.out.println(info.isDirectory());
System.out.println(info.isSymbolicLink());
System.out.println(info.isOther());
System.out.println(info.size());
```

Snippet cần `path`, import và xử lý `IOException`.

`BasicFileAttributes` còn cung cấp `lastModifiedTime()`, `creationTime()`, `lastAccessTime()` và `fileKey()`. Nhưng không nên giả định:

- Mọi filesystem lưu `creationTime` theo nghĩa giống nhau.
- Timestamps có cùng độ chính xác hoặc được cập nhật cùng chính sách trên mọi OS.
- `fileKey()` luôn khác null, ổn định mãi mãi hoặc dùng được như ID public.
- `size()` của symbolic link khi `NOFOLLOW_LINKS` có nghĩa là số byte nội dung của target.
- Toàn bộ attributes được đọc atomically với những thay đổi đồng thời: Javadoc cho phép sự khác biệt do provider.

Nếu cần phân biệt “không phải symbolic link” với “không xác định được vì I/O lỗi”, dùng `readAttributes(..., NOFOLLOW_LINKS)` và xử lý exception, thay vì chỉ gọi `Files.isSymbolicLink` rồi coi false là chứng minh chắc chắn.

## 9. Các nhóm attribute view theo nền tảng

Java NIO.2 có nhiều giao diện attribute view, nhưng hỗ trợ cụ thể phụ thuộc filesystem/provider:

| View | Java API | Thông tin tiêu biểu |
| --- | --- | --- |
| Basic | `BasicFileAttributeView` | Loại entry, size, timestamps, file key |
| POSIX | `PosixFileAttributeView` | Owner, group, POSIX permission bits |
| DOS | `DosFileAttributeView` | Readonly, hidden, archive, system flags |
| Owner | `FileOwnerAttributeView` | Owner user principal |
| ACL | `AclFileAttributeView` | Danh sách ACE và owner |
| User-defined | `UserDefinedFileAttributeView` | Extended attributes nếu provider hỗ trợ |

Thư viện có thể hỏi ở hai cấp:

```java
Set<String> providerViews =
        path.getFileSystem().supportedFileAttributeViews();

FileStore store = Files.getFileStore(path);
boolean posixAvailable =
        store.supportsFileAttributeView(
                PosixFileAttributeView.class);
```

Snippet cần import `Set`, `FileStore`, `PosixFileAttributeView` và `Path`; `Files.getFileStore` có thể ném `IOException`.

- `supportedFileAttributeViews()` là khả năng mà **filesystem provider** hỗ trợ nói chung.
- `FileStore.supportsFileAttributeView(...)` là phép kiểm tra trên **file store** cụ thể.
- Quyền thực tế vẫn có thể khiến thao tác đọc/đổi attributes thất bại, dù view được hỗ trợ.
- Không dùng `System.getProperty("os.name")` làm nguồn quyết định duy nhất: cả trên một OS có thể có nhiều file store với khả năng khác nhau.

`Files.getFileAttributeView(path, PosixFileAttributeView.class)` có thể trả `null` khi view không hỗ trợ. Khi thật sự gọi `Files.getPosixFilePermissions` trên filesystem không hỗ trợ POSIX, thường nhận `UnsupportedOperationException`. Bài test phải có nhánh skip riêng.

## 10. Ví dụ Java 17+ hoàn chỉnh: kiểm tra khả năng file store

Tạo **`FileAttributeCapabilityDemo.java`**:

```java
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Set;

public class FileAttributeCapabilityDemo {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("nio-views-");
        Path file = dir.resolve("sample.txt");

        try {
            Files.writeString(file, "hello");

            Set<String> views =
                    file.getFileSystem().supportedFileAttributeViews();
            FileStore store = Files.getFileStore(file);

            boolean basic = Files.getFileAttributeView(
                    file, BasicFileAttributeView.class) != null;
            boolean posix = store.supportsFileAttributeView(
                    PosixFileAttributeView.class);
            boolean dos = store.supportsFileAttributeView(
                    DosFileAttributeView.class);
            boolean acl = store.supportsFileAttributeView(
                    AclFileAttributeView.class);

            System.out.println("basic=" + basic);
            System.out.println("providerSupportsBasic="
                    + views.contains("basic"));
            System.out.println("posixSupported=" + posix);
            System.out.println("dosSupported=" + dos);
            System.out.println("aclSupported=" + acl);

            // Kết quả thăm dò không thay thế open/read/write thực tế.
            System.out.println("content="
                    + Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FileAttributeCapabilityDemo.java
java FileAttributeCapabilityDemo
```

Hai dòng chắc chắn theo hợp đồng file system provider chuẩn khi các thao tác thành công:

```text
basic=true
providerSupportsBasic=true
```

Các dòng tiếp theo **phụ thuộc nền tảng và file store**:

```text
posixSupported=<true hoặc false>
dosSupported=<true hoặc false>
aclSupported=<true hoặc false>
content=hello
```

Các dấu `<true hoặc false>` là **mô tả khả năng**, không phải output literal cần mong đợi từng ký tự. Chương trình thật in `true` hoặc `false`. Không viết unit test buộc POSIX phải true hoặc DOS/ACL phải false: Java có thể chạy trên filesystem có khả năng khác hệ điều hành chủ.

**Giải thích:** `getFileStore(file)` lấy store của tệp thử nghiệm, không suy từ hệ điều hành. `supportsFileAttributeView` cho biết một khả năng, **không** xác nhận process luôn có quyền chỉnh thuộc tính đó. Ví dụ không thay đổi owner/ACL hoặc permission thật, nên có thể chạy trong workspace tạm mà không ảnh hưởng cấu hình tệp của người dùng.

## 11. POSIX permissions: hiểu các bit và giới hạn

Trên file store hỗ trợ POSIX, có thể đọc quyền bằng:

```java
Set<PosixFilePermission> perms =
        Files.getPosixFilePermissions(path);
String symbolic = PosixFilePermissions.toString(perms);
```

Snippet cần `path` và import `Set`, `PosixFilePermission`, `PosixFilePermissions`, xử lý `IOException`/`UnsupportedOperationException`.

Các ký hiệu `rwxr-x---` là quyền read/write/execute của **owner, group, others**. Nhưng hiệu lực có thể còn phụ thuộc user hiện hành, supplementary groups, ACL, mount flags, quyền thư mục cha và hệ điều hành.

Lưu ý:

- Trên filesystem không hỗ trợ POSIX view, gọi `getPosixFilePermissions` có thể thất bại với `UnsupportedOperationException`.
- `Files.setPosixFilePermissions(path, perms)` **thay đổi quyền file thực**; không dùng trên file người dùng trong ví dụ thử nghiệm.
- `Files.createTempFile`/`createTempDirectory` có thể tạo quyền ban đầu khác nhau theo provider và umask; không hardcode output octal dự kiến cho mọi máy.
- POSIX `OWNER_READ` không tương đương một công bố “mọi process đọc được”.
- Ở Windows, quyền NTFS thường dựa trên ACL; không thể suy ra cách phân quyền chỉ bằng sơ đồ rwx.
- Không dùng permission bits làm cơ chế kiểm tra quyền nghiệp vụ đối với user web. Cần authorization riêng ở application.

### Thăm dò quyền ở mức không gây thay đổi

Trong phần chẩn đoán, ghi các thông tin **không nhạy cảm** như `posixSupported=true`, `fileType=regular`, `testReadSucceeded=true` có thể hữu ích. Nếu cần ghi owner/group/path vào log, phải xét quyền truy cập và bảo vệ dữ liệu; owner hoặc path có thể chứa username thật.

Một phép thử `Files.isWritable(path)` trả true **không hứa** rằng `Files.write(path, bytes)` sẽ thành công sau đó: hệ thống có thể hết dung lượng, quota, file lock, thay trạng thái hoặc policy từ chối ghi vào thời điểm thực hiện.

## 12. DOS flags và ACL: không đồng nhất với POSIX

**DOS attributes** như `readonly`, `hidden`, `archive`, `system` có thể được truy cập qua `DosFileAttributeView` nếu provider hỗ trợ. Chúng không phải bản sao trực tiếp của các bit POSIX.

**ACL** qua `AclFileAttributeView` biểu diễn danh sách mục cấp quyền/từ chối quyền. Hệ thống có thể xét thứ tự và loại ACE, user/group principals và các quy tắc riêng; **không** nên tự thực hiện một parser đơn giản rồi nói “user này chắc chắn được đọc”.

Ví dụ phát hiện view:

```java
AclFileAttributeView aclView = Files.getFileAttributeView(
        path, AclFileAttributeView.class);

if (aclView == null) {
    // Không có ACL view cho path/provider này.
} else {
    // Chỉ khi nghiệp vụ cần và có quyền,
    // đọc ACL theo chính sách bảo mật.
}
```

Đây là snippet. `getAcl()` có thể trả dữ liệu nhạy cảm về user/group; không in toàn bộ ACL lên trang lỗi công khai. Với các test về thay đổi ACL/owner, sử dụng môi trường cô lập có chính sách cụ thể và làm sạch trạng thái sau test; không thay ACL của thư mục làm việc chung.

## 13. Thử thao tác thật và bắt lỗi đúng cách

Một phép thăm dò cần thiết nên ưu tiên **thao tác tối thiểu mà nghiệp vụ dự định làm**, trong phạm vi tệp/thư mục đã cấp quyền:

```java
try (var in = Files.newInputStream(path)) {
    byte[] first = in.readNBytes(32);
    // Chỉ xử lý first.length byte thực đọc.
} catch (java.nio.file.NoSuchFileException ex) {
    // Không tồn tại tại thời điểm mở.
} catch (java.nio.file.AccessDeniedException ex) {
    // Hệ thống không cho phép truy cập.
} catch (IOException ex) {
    // Các lỗi IO khác.
}
```

Đoạn snippet cần `IOException`, `Files`, `Path` và một `path` từ luồng nghiệp vụ đã phân quyền. Nếu cần không đi theo symbolic link cuối cùng, **mở bằng API có hợp đồng tùy chọn phù hợp**, chứ đừng chỉ gọi `isSymbolicLink` rồi mở bình thường.

Một lựa chọn đối với những provider hỗ trợ `NOFOLLOW_LINKS` khi mở channel là:

```java
try (var channel = Files.newByteChannel(
        path,
        java.util.Set.of(
                java.nio.file.StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS))) {
    // Đọc từ channel đã mở nếu thành công.
}
```

Snippet này giả định `path` và import `Files`, `LinkOption`, xử lý `IOException`/`UnsupportedOperationException`. `NOFOLLOW_LINKS` là `OpenOption` được hỗ trợ theo hợp đồng NIO phù hợp; khi target cuối là symbolic link, việc mở có thể thất bại thay vì theo link. Nó **không tự ngăn việc path cha chứa symlink** hoặc một đối thủ thay đổi đường dẫn trước khi mở.

Hãy nghĩ theo nguyên tắc **mở đối tượng cụ thể một lần và dùng handle đã mở**, hạn chế chuỗi “kiểm tra path → sử dụng lại path” tạo cơ hội cho race.

## 14. TOCTOU: lỗi giữa check và use

TOCTOU (*Time Of Check To Time Of Use*) xảy ra khi trạng thái được kiểm tra tại một thời điểm nhưng file thay đổi trước thao tác tiếp theo.

Ví dụ không an toàn:

```java
Path candidate = root.resolve(userSuppliedName).normalize();

if (candidate.startsWith(root)
        && !Files.isSymbolicLink(candidate)
        && Files.isReadable(candidate)) {
    // Một tiến trình khác có thể thay candidate sau các check.
    return Files.readAllBytes(candidate);
}
```

Snippet cố ý minh họa rủi ro; cần khai báo `root` và `userSuppliedName`, kiểm soát đầu vào và xử lý ngoại lệ. Ngoài race ở final symlink, kiểm tra `candidate.startsWith(root)` dựa trên **đường dẫn chuẩn hóa cú pháp**, không bảo đảm đường dẫn thực ở trong root khi thư mục cha chứa symlink.

Đối sách theo mức cần thiết:

1. **Không cho client truyền path tùy ý**; ánh xạ resource ID sang storage key do server quản lý.
2. Thiết lập thư mục lưu trữ có quyền OS phù hợp, không cho bên không tin cậy thay entry và symlink.
3. Dùng các tùy chọn mở `NOFOLLOW_LINKS` khi yêu cầu cấm final symlink; cần xem xét từng thành phần đường dẫn.
4. Trong provider hỗ trợ, cân nhắc `SecureDirectoryStream` để thao tác tương đối qua directory handle và giảm race khi phân giải directory entry.
5. Với tệp cần tạo mới, dùng `StandardOpenOption.CREATE_NEW` để tránh ghi đè entry tồn tại trong những tình huống thích hợp; kết hợp quyền thư mục và option liên quan.
6. Thiết kế xử lý `IOException` ngay ở thao tác thật; không coi check trước là giấy phép chắc chắn.
7. Khi yêu cầu an ninh cao, kiểm tra định danh/file identity và quyền ở OS/resource handle theo khả năng môi trường, thay vì dựa vào string path.

`Path.toRealPath()` giải quyết đường dẫn thực theo hợp đồng, nhưng kết quả đó **cũng có thể lỗi thời ngay sau lời gọi**. Không biến phép chuẩn hóa thành bảo đảm chống symlink swap.

## 15. `toRealPath()` và đường dẫn canonical: dùng đúng ý nghĩa

So sánh:

| API | Tác dụng chính |
| --- | --- |
| `path.toAbsolutePath()` | Tạo dạng absolute theo filesystem context |
| `path.normalize()` | Chuẩn hóa cú pháp `.`, `..`; không cần truy cập file thực |
| `path.toRealPath()` | Trả path thực của một entry tồn tại, theo dõi link mặc định, giải quyết path theo provider |
| `path.toRealPath(NOFOLLOW_LINKS)` | Trả path thực theo tùy chọn không theo symbolic links theo hợp đồng |
| `Files.readSymbolicLink(path)` | Trả target lưu trong symlink, có thể relative và chưa tồn tại |

`toRealPath()` có thể ném `NoSuchFileException`, `FileSystemLoopException` hoặc `IOException` nếu không giải quyết được. `normalize()` không xác minh tệp tồn tại. Tính đặc thù platform còn ảnh hưởng case sensitivity và aliasing.

Không in `toRealPath()` (hoặc path nội bộ gốc) trong HTTP error response chỉ để chẩn đoán; đó có thể là đường dẫn chứa tên người dùng, mount hoặc cấu trúc deployment. Hãy trả mã lỗi công khai ổn định, lưu diagnostic theo chính sách truy cập phù hợp.

## 16. Duyệt cây thư mục và vòng lặp symbolic link

`Files.walkFileTree(start, visitor)` mặc định **không đi theo symlink**. Nếu truyền `FileVisitOption.FOLLOW_LINKS`, traversal có thể đi vào directory liên kết và gặp vòng lặp.

```java
Files.walkFileTree(
        root,
        java.util.EnumSet.of(
                java.nio.file.FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        visitor);
```

Đây là snippet cần `root` và một `FileVisitor<Path> visitor` cùng import; không phải chương trình độc lập. Khi `FOLLOW_LINKS`, Java có cơ chế phát hiện cycle; `FileSystemLoopException` có thể được đưa đến `visitFileFailed`. Người viết visitor phải quyết định xử lý ra sao.

Khi **không có nhu cầu theo dõi link**, nên giữ mặc định không follow và xử lý symbolic-link entry riêng nếu cần. Đặc biệt khi thao tác xóa hoặc đóng gói cây thư mục, một traversal follow link ngoài root có thể chạm đến dữ liệu không thuộc phạm vi. Đừng chạy một chương trình “dọn thư mục” theo tên path chưa tin cậy với `FOLLOW_LINKS` mà không xác định rõ tác động.

### Không suy luận mọi directory listing là snapshot

`Files.walk` hoặc `walkFileTree` có thể quan sát trạng thái thay đổi trong lúc đang duyệt. Directory entry bị xóa, đổi quyền hoặc thay link có thể làm một vòng lặp fail giữa chừng. Chẩn đoán phải xử lý exception theo từng entry và không mặc định danh sách trả về là snapshot nhất quán tuyệt đối.

## 17. Tính đặc thù Windows, Linux, macOS và provider

| Chủ đề | Những điểm có thể thay đổi |
| --- | --- |
| Khả năng tạo symlink | Filesystem, quyền hệ điều hành, policy, developer mode/đặc quyền trên Windows |
| POSIX permission bits | Thường có trên nhiều filesystem kiểu Unix; không nên giả định có trên Windows/NTFS |
| ACL | Có thể có trên Windows/NTFS và một số filesystem khác; không đồng nhất với POSIX bits |
| DOS attributes | Thường gắn với FAT/NTFS hoặc provider liên quan; không có trên mọi store |
| Case sensitivity | NTFS, APFS, ext4... có thể cấu hình/diễn giải khác nhau |
| Separator/path syntax | Windows/Unix và filesystem provider có cách thể hiện khác |
| Symlink/junction/reparse point | Một số loại link riêng OS không nhất thiết được báo theo cùng API |
| Xóa file đang mở | Hành vi chia sẻ/khóa, đặc biệt giữa Windows và POSIX, khác nhau |
| Rename atomic | Phụ thuộc file store/provider và hỗ trợ `ATOMIC_MOVE` |
| Timestamp precision | Mốc thời gian có thể bị làm tròn/không được cập nhật giống nhau |
| Network filesystem | Caching, credentials, lỗi mạng, eventual observation, quyền từ xa |
| Virtual filesystem | ZIP filesystem, in-memory provider, read-only store có tập operation khác |

Không dùng `System.getProperty("os.name")` rồi hardcode toàn bộ hành vi. Kiểm tra **khả năng cụ thể** trên path/file store và **thử operation thật** khi có quyền. Một OS có thể gắn nhiều store với capability khác nhau, hoặc cùng store thay đổi policy giữa môi trường CI và production.

## 18. Viết bài test portable thay vì test phụ thuộc một máy

Một kiểm thử tốt:

- Chỉ tạo file/thư mục **bên trong temp directory** do test sở hữu.
- Với tính năng optional như symlink/POSIX view, có nhánh **skip/assumption** rõ ràng nếu không hỗ trợ hoặc không có quyền.
- Khác biệt giữa **không hỗ trợ** và **đã hỗ trợ nhưng thao tác bị lỗi bất ngờ**; không blanket catch mọi exception rồi báo PASS.
- Không yêu cầu exact POSIX permission string hoặc ACL trên mọi platform.
- Không thay quyền/chủ sở hữu file có sẵn của người dùng hoặc thư mục shared CI.
- Sau khi tạo link, kiểm tra bằng API cụ thể như `readAttributes(..., NOFOLLOW_LINKS)`.
- Kiểm tra broken link, link relative, quyền của target và cleanup.
- Không dựa vào giả định cùng thứ tự listing/timestamp precision cho mọi filesystem.
- Khi test security-critical code, bổ sung test có actor thay symlink đồng thời hoặc test ở môi trường kiểm soát; nêu rõ khả năng nondeterministic của race.

### Ví dụ Java 17+ hoàn chỉnh: test điều kiện symlink có hỗ trợ

Tạo **`SymlinkInvariantTest.java`**:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class SymlinkInvariantTest {
    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("symlink-test-");
        Path target = dir.resolve("data.txt");
        Path link = dir.resolve("alias.txt");

        try {
            Files.writeString(target, "OK");

            try {
                Files.createSymbolicLink(link, Path.of("data.txt"));
            } catch (UnsupportedOperationException
                     | IOException | SecurityException ex) {
                System.out.println("SKIP: symlink cannot be created");
                return;
            }

            BasicFileAttributes attrs = Files.readAttributes(
                    link, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);

            if (!attrs.isSymbolicLink()) {
                throw new AssertionError("Expected symbolic link");
            }
            if (!Files.isSameFile(link, target)) {
                throw new AssertionError("Link must resolve to target");
            }
            if (!Files.readString(link).equals("OK")) {
                throw new AssertionError("Following link read wrong data");
            }

            Files.delete(link);
            if (!Files.exists(target)) {
                throw new AssertionError("Target must survive link deletion");
            }

            System.out.println("PASS: symlink invariants");
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
        }
    }
}
```

```bash
javac -encoding UTF-8 SymlinkInvariantTest.java
java SymlinkInvariantTest
```

Kết quả nếu symlink được hỗ trợ và mọi phép kiểm tra đúng:

```text
PASS: symlink invariants
```

Nếu provider/policy không cho tạo link:

```text
SKIP: symlink cannot be created
```

**Điểm khác với test “luôn PASS”**: nếu việc tạo link thành công nhưng `readAttributes`, `isSameFile` hoặc `readString` thất bại ngoài dự kiến, lỗi không bị nuốt bởi catch tạo link. Nếu dữ liệu sai, `AssertionError` làm test fail. Chỉ giới hạn skip ở bước tạo link optional.

Trong hệ thống test lớn, nên phân loại lý do skip (unsupported/permission/other I/O) để vận hành dễ chẩn đoán; ví dụ tối giản này chỉ in một thông báo chung và không phụ thuộc JUnit.

## 19. Chẩn đoán lỗi quyền mà không lộ thông tin nội bộ

Khi một hành động I/O bị từ chối, có thể cần ghi log phục vụ nhân viên vận hành:

```text
event=FILE_ACCESS_FAILED
operation=OPEN_READ
reason=ACCESS_DENIED
requestId=<server-generated-id>
```

Không nên tự động trả cho client hoặc log công khai:

```text
AccessDeniedException:
 /var/run/secrets/app/production/token.key
```

`AccessDeniedException.getFile()`, `FileSystemException.getReason()` hoặc `exception.getMessage()` có thể chứa path và chi tiết provider. Nếu cần diagnostic đầy đủ, dùng kênh được bảo vệ, kiểm soát retention và nội dung theo chính sách; **public response** chỉ cần mã lỗi ổn định.

Test phần bảo mật thông báo công khai phải cố tình tạo path có sentinel nhạy cảm và xác minh rằng response/serializer không phản chiếu nó. Hãy phân biệt lỗi quyền truy cập file của process và **authorization nghiệp vụ của user**; hai tầng có thể cùng phát sinh mã 403/404/500 tùy chính sách API.

## 20. Những nhầm lẫn cần tránh

1. **“`Files.exists` false chắc chắn tệp không tồn tại.”** Sai: có thể không xác định được do lỗi truy cập.
2. **“Link bị đứt nghĩa là không có directory entry.”** Sai: `NOFOLLOW_LINKS` có thể chứng minh link vẫn tồn tại.
3. **“`Files.isSymbolicLink` false chứng minh file không phải symlink.”** Chưa chắc nếu không xác định được loại do I/O lỗi.
4. **“`NOFOLLOW_LINKS` chặn mọi symlink ở mọi thư mục cha.”** Không đủ: phải hiểu hợp đồng API và thiết kế directory/path security.
5. **“`readSymbolicLink` trả absolute path.”** Có thể trả relative target được lưu trong link.
6. **“Xóa symbolic link là xóa cả target.”** Sai với `Files.delete(link)` trong trường hợp bình thường.
7. **“Hard link chính là symlink khác tên.”** Sai; định danh và nội dung liên quan theo ngữ nghĩa khác.
8. **“`isReadable` true nghĩa là đọc chắc chắn thành công.”** Sai: TOCTOU và các lý do khác vẫn làm I/O fail.
9. **“`isWritable` false luôn là do ACL từ chối.”** Sai: có thể do không xác định được hoặc chính sách khác.
10. **“Mọi hệ điều hành hỗ trợ POSIX permissions.”** Sai; phải kiểm tra view của file store.
11. **“Windows không bao giờ tạo được symlink.”** Sai; phụ thuộc hệ điều hành, quyền và provider.
12. **“Mỗi file store trên Linux giống nhau.”** Sai; network/ZIP/FUSE/container stores có thể khác.
13. **“`normalize` và `toRealPath` là đủ chống path traversal.”** Sai nếu vẫn còn symlink race/authorization.
14. **“Có view ACL nghĩa là user web đã được cho phép.”** Sai; quyền OS khác policy ứng dụng.
15. **“`Files.walkFileTree` theo link theo mặc định.”** Sai; phải chủ động truyền `FOLLOW_LINKS`.
16. **“Symlink cycle sẽ luôn được xử lý im lặng.”** Sai; khi follow có thể xuất hiện `FileSystemLoopException`.
17. **“Timestamps và thứ tự duyệt luôn ổn định giữa Windows/macOS/Linux.”** Sai; dùng test dựa trên hợp đồng cụ thể.
18. **“Test không tạo symlink được là code xử lý symlink sai.”** Chưa chắc; feature có thể không hỗ trợ hoặc bị policy chặn.
19. **“Catch mọi IOException rồi báo SKIP.”** Sai: che lỗi thật sau khi đã xác định feature hỗ trợ.
20. **“In raw path để debug là vô hại.”** Có thể lộ username, server layout, private storage key.

## 21. Bài tập vận dụng

### Bài A — broken link

Tạo symlink `broken.txt -> missing.txt` trong thư mục tạm. Dự đoán `exists` có/không `NOFOLLOW_LINKS`, `isSymbolicLink` và `readSymbolicLink`. Vì sao các kết quả có thể khác nhau?

### Bài B — POSIX/ACL capability

Trên máy bạn, dùng `Files.getFileStore(path)`, `supportsFileAttributeView(PosixFileAttributeView.class)` và `AclFileAttributeView.class`. So sánh hai file store khác nhau nếu có. Vì sao không nên biến một kết quả thành quy tắc chung cho hệ điều hành?

### Bài C — race giữa kiểm tra và thao tác

Một tiến trình gọi `Files.isRegularFile(candidate, NOFOLLOW_LINKS)`, rồi tiến trình khác thay entry thành symbolic link trước `Files.readString(candidate)`. Giải thích rủi ro TOCTOU và đề xuất cách giảm thiểu bằng giới hạn thư mục, open options và handle.

### Bài D — directory traversal

Một file ZIP chứa entry `../../outside.txt`. Tại sao `root.resolve(name).normalize().startsWith(root)` vẫn chưa đủ nếu thư mục trong root có thể bị thay bởi symlink? Nếu cần thao tác trên cây thư mục do người dùng khác sửa đồng thời, cần cân nhắc capability nào của provider?

### Bài E — test portable trên CI

Hãy sửa `SymlinkInvariantTest` để log lý do skip vào kênh nội bộ nhưng không trả path ra output công khai. Trên môi trường không hỗ trợ symlink, tại sao phải skip test thuộc tính symlink thay vì fail cả build (nếu tính năng là optional)?

### Bài F — file đang mở và cleanup

Thử mở `FileChannel`, sau đó xóa/rename file trong cùng process ở các OS khác nhau. Ghi nhận hành vi thực tế, nhưng không viết test portable giả định Windows hoặc POSIX luôn cho kết quả giống nhau. Sau test phải đóng channel và xóa dữ liệu tạm an toàn.

## 22. Câu hỏi tự kiểm tra

1. `Files.isSymbolicLink(path)` khác `Files.isRegularFile(path, NOFOLLOW_LINKS)` thế nào?
2. Khi target của symlink bị xóa, vì sao `Files.exists(link)` và `Files.exists(link, NOFOLLOW_LINKS)` có thể khác?
3. `Files.readSymbolicLink(link)` trả Path relative so với đâu?
4. `NOFOLLOW_LINKS` có ý nghĩa và giới hạn gì trong việc chống symlink attack?
5. Khi nào nên dùng `Files.readAttributes(..., NOFOLLOW_LINKS)` thay vì chỉ dùng `Files.isSymbolicLink`?
6. `Files.isReadable(path)` trả true có thay thế việc bắt `IOException` không?
7. `FileStore.supportsFileAttributeView` cho biết điều gì, và điều gì nó không cam kết?
8. POSIX permissions và Windows ACL có thể được đồng nhất hoàn toàn không?
9. Tại sao việc check quyền rồi mở file có thể tạo TOCTOU?
10. `Files.delete(link)` có tự động xóa nội dung target của symlink không?
11. Vì sao `Path.normalize()` không phải phép kiểm tra security root chắc chắn?
12. Khi dùng `FOLLOW_LINKS` trong `walkFileTree`, cần xem xét dạng lỗi nào?
13. Nếu CI không thể tạo symlink, làm sao tổ chức test để vẫn nhận diện lỗi thật của chức năng khác?
14. Vì sao cần chạy kiểm thử trên nhiều loại file store, không chỉ nhiều tên hệ điều hành?
15. Có nên đưa path tuyệt đối hoặc owner name lên trang lỗi HTTP khi một `AccessDeniedException` xảy ra không?

## 23. Tài liệu tham khảo Java 17

- [`Files` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): create/read symbolic link, exists, readAttributes, access probes, walkFileTree.
- [`Path` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): normalize, toAbsolutePath, toRealPath và semantics provider.
- [`LinkOption` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): `NOFOLLOW_LINKS`.
- [`FileStore` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileStore.html): `supportsFileAttributeView`.
- [`java.nio.file.attribute` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/attribute/package-summary.html): Basic/POSIX/DOS/ACL/User-defined attribute views.
- [`SecureDirectoryStream` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): thao tác directory entry giúp giảm race nếu provider hỗ trợ.
- [`FileVisitOption` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileVisitOption.html): `FOLLOW_LINKS` khi duyệt cây.

## 24. Ghi nhớ

**Symbolic link là một entry riêng với target**, và `NOFOLLOW_LINKS` chỉ có ý nghĩa trong các API hỗ trợ. Với link bị đứt, kiểm tra tồn tại mặc định có thể false trong khi kiểm tra chính link với `NOFOLLOW_LINKS` là true.

**Quyền truy cập và thuộc tính file phụ thuộc provider/file store, OS, process identity và thời điểm**. `Files.isReadable/isWritable` không thay thế phép mở thật; POSIX/DOS/ACL views cần được phát hiện động. Đối với mã cần bảo mật, tránh TOCTOU bằng quản lý root/handle, quyền thư mục phù hợp, tùy chọn mở không follow và kiểm soát thành phần đường dẫn — không chỉ bằng `normalize()`.

Bài test chất lượng tốt **chỉ dùng tài nguyên tạm**, nhận diện tính năng optional, phân biệt SKIP với FAIL, không yêu cầu output về quyền/symlink giống nhau ở mọi nền tảng, và không làm lộ đường dẫn nội bộ trong thông báo công khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-009">Chẩn đoán sai working directory, charset và đường dẫn resource</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán sai working directory, charset và đường dẫn resource trong Java IO

## 1. Ba nguyên nhân gây lỗi dễ bị đánh đồng

Một chương trình Java đọc được dữ liệu trong IDE nhưng thất bại khi chạy từ terminal, JAR, container hoặc server thường gặp một trong ba nhóm vấn đề:

1. **Sai working directory (CWD)**: đường dẫn tệp tương đối được giải quyết tại thư mục làm việc của tiến trình, không phải nơi chứa file source `.java`, file `.class` hay JAR.
2. **Sai charset**: đã mở đúng file/resource nhưng biến các byte thành ký tự theo encoding khác, gây mất dấu tiếng Việt, ký tự lạ hoặc ngoại lệ giải mã.
3. **Sai đường dẫn resource**: nhầm file system path với resource trong classpath/module hoặc nhầm đường dẫn tính từ package của class với đường dẫn tính từ classpath root.

Chẩn đoán đúng phải tách **vị trí tìm tệp**, **cơ chế tra cứu** và **cách diễn giải bytes**. Sửa một nhóm vấn đề không tự giải quyết nhóm còn lại.

Bài hướng dẫn kiến thức Java 17+ trong module IO: nhận biết CWD và `user.dir`, dùng `Path`/`Files` với base directory tường minh, tra classpath resources trong IDE/JAR, chọn charset chính xác, tái hiện lỗi bằng chương trình độc lập và xây kiểm thử chịu được thay đổi môi trường.

## 2. Working directory không phải thư mục source hay thư mục JAR

Một ví dụ:

~~~java
Path path = Path.of("data/customers.csv");
String csv = Files.readString(path, StandardCharsets.UTF_8);
~~~

Đây là đoạn minh họa cần được đặt trong method với imports và xử lý `IOException`. Chuỗi `data/customers.csv` là **relative filesystem path**. Trên default filesystem, nó được diễn giải theo working directory mà runtime đang sử dụng. Java không tự động dò tìm:

- Thư mục hiện đang chứa file `.java`.
- Thư mục build có file `.class`.
- Thư mục cài đặt JAR.
- `src/main/resources` trong source tree.
- Những JAR/dependency nằm trên classpath.

Ví dụ cấu trúc:

~~~text
/work/app/
  data/customers.csv
  build/classes/example/Main.class
  app.jar
~~~

Nếu command chạy từ `/work/app`, path tương đối có thể chỉ tới `/work/app/data/customers.csv`. Nếu chạy cùng JAR khi CWD là `/srv/service`, Java có thể tìm `/srv/service/data/customers.csv`, nơi file không tồn tại.

Việc dùng `java -jar /work/app/app.jar` **không làm CWD tự chuyển sang thư mục chứa JAR**. Launcher, shell, IDE, service manager hoặc container xác định CWD.

### Cách kiểm tra CWD trong Java

~~~java
System.out.println(System.getProperty("user.dir"));
System.out.println(Path.of("").toAbsolutePath().normalize());
System.out.println(Path.of("data/customers.csv").toAbsolutePath().normalize());
~~~

Đoạn trên là minh họa, chưa phải chương trình độc lập. In các giá trị đó tại thời điểm gặp lỗi giúp xác nhận Java **đang tra file ở đâu**.

`System.getProperty("user.dir")` thường phản ánh working directory được runtime thiết lập lúc khởi chạy. Không dùng `System.setProperty("user.dir", "...")` như một giải pháp portable để đổi OS CWD giữa chương trình: thay đổi một Java property không có nghĩa mọi API/file system provider sẽ lập tức coi process đã đổi directory. Nếu muốn đổi nơi đọc dữ liệu, **truyền một base `Path` tường minh** hoặc khởi động tiến trình con với working directory mong muốn.

### Absolute path, normalize và toRealPath

| Phương thức | Chức năng | Lưu ý |
| --- | --- | --- |
| `Path.of("x/y")` | Tạo path theo provider mặc định | Thường relative nếu không có root |
| `path.isAbsolute()` | Kiểm tra path tuyệt đối theo provider | Cú pháp Unix và Windows khác nhau |
| `path.toAbsolutePath()` | Tạo absolute representation từ ngữ cảnh hiện tại | Không chứng minh file tồn tại |
| `path.normalize()` | Xử lý thành phần `.` và `..` theo cú pháp | Không tự resolve symbolic links |
| `path.toRealPath()` | Resolve sang real path và kiểm tra tồn tại theo options | Có thể ném `IOException`; symlink semantics tùy options |
| `base.resolve(relative)` | Ghép path theo rules của `Path` | Nếu `relative` là absolute, behavior cần được xác thực |

Nếu path đến từ user input, chỉ `normalize()` và `startsWith(base)` không đủ cho mọi yêu cầu bảo mật: symbolic links có thể dẫn ra ngoài base; còn race giữa lúc kiểm tra và mở file.

## 3. Chỉ định base directory để tránh phụ thuộc ngữ cảnh chạy

Nếu tài nguyên là **dữ liệu bên ngoài ứng dụng** như file khách hàng, logs, output directory hoặc config thay đổi theo deployment, hãy nhận location qua args/config/env và tạo path từ base đó.

~~~java
static Path resolveUnderBase(Path baseDirectory, String relativeName)
        throws IOException {
    Path base = baseDirectory.toAbsolutePath().normalize();
    Path resolved = base.resolve(relativeName).normalize();
    if (!resolved.startsWith(base)) {
        throw new IOException("Path outside configured base");
    }
    return resolved;
}
~~~

Đây là method minh họa; phải import `Path` và `IOException`. Với untrusted paths, ví dụ chỉ kiểm tra lexical traversal, không xử lý đầy đủ symlink và TOCTOU race. Cần chính sách quyền và filesystem isolation cụ thể.

Ưu tiên những lựa chọn có hợp đồng rõ:

- `--data-dir=/var/lib/example` được truyền khi khởi động.
- Environment variable/config chứa đường dẫn tuyệt đối đã được validate.
- `Files.createTempDirectory(...)` cho dữ liệu dùng trong test/job tạm.
- User home khi dữ liệu thật sự thuộc một tài khoản người dùng.
- Data volume được mount vào một vị trí có tài liệu hóa trong container.

Không liên tục thử `../data`, `../../data` rồi chọn cái chạy được ở IDE: cách đó trở nên mong manh khi cấu trúc build hoặc service CWD thay đổi.

## 4. Classpath resource là cơ chế khác filesystem

Tài nguyên đóng gói như template, ảnh tĩnh, schema hoặc file mặc định có thể nằm ở:

~~~text
src/main/java/example/App.java
src/main/resources/config/defaults.properties
src/main/resources/templates/message.txt
~~~

Build tool thường sao chép chúng vào classpath root hoặc JAR:

~~~text
classes/
  example/App.class
  config/defaults.properties
  templates/message.txt
~~~

**Không mặc định dùng path `src/main/resources/config/defaults.properties` khi chạy production.** Source tree không nhất thiết tồn tại trong container hay sau khi đóng gói JAR.

Đọc resource đúng:

~~~java
try (InputStream in = App.class
        .getResourceAsStream("/config/defaults.properties")) {
    if (in == null) throw new FileNotFoundException("Resource missing");
    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
~~~

Đây là mảnh mã cần import `InputStream`, `FileNotFoundException` và `StandardCharsets`, có `App` trong classpath và được đặt trong method. `getResourceAsStream` trả `null` nếu không tìm được resource theo cơ chế lookup; phải kiểm tra null trước khi đọc.

Resource thường nên được coi là **read-only asset**. Đừng cố ghi state vào `src/main/resources` hoặc vào JAR runtime. Muốn cho user chỉnh template mặc định, hãy copy resource vào thư mục dữ liệu do user/application sở hữu rồi sửa bản copy.

## 5. Quy tắc dấu slash và package khi tra resource

Giả sử class `com.example.io.Demo` và resource `config/defaults.properties` ở classpath root:

| Biểu thức | Vị trí được tìm |
| --- | --- |
| `Demo.class.getResourceAsStream("/config/defaults.properties")` | Từ resource root |
| `Demo.class.getResourceAsStream("defaults.properties")` | Tương đối package `com/example/io/` |
| `Demo.class.getResourceAsStream("config/defaults.properties")` | `com/example/io/config/defaults.properties` |
| `Demo.class.getClassLoader().getResourceAsStream("config/defaults.properties")` | Root của classloader |
| `Demo.class.getClassLoader().getResourceAsStream("/config/defaults.properties")` | Thường sai do leading slash |

`Class.getResourceAsStream` hỗ trợ cú pháp có leading slash để nói rõ root. `ClassLoader.getResourceAsStream` nhận resource name theo quy tắc classloader, thường không dùng slash đầu.

Không phải mọi class loader là cùng một instance: app server, plugin, module hoặc test runner có thể sử dụng classloader riêng. Bootstrap-loaded classes có thể có `getClassLoader()==null`. Nếu resource còn tồn tại nhưng lookup sai, hãy xác định class nào/classloader nào thực hiện truy cập.

### Khi nhiều JAR có resource cùng tên

Hai dependency cùng chứa `config/defaults.properties` có thể khiến một resource “đúng tên nhưng sai nội dung” được chọn. Nếu cần chẩn đoán:

~~~java
Enumeration<URL> found =
        Demo.class.getClassLoader().getResources("config/defaults.properties");
while (found.hasMoreElements()) {
    System.out.println(found.nextElement());
}
~~~

Đây là mảnh mã với imports `Enumeration`, `URL` và xử lý `IOException`; không phải chương trình hoàn chỉnh. Thứ tự classloader lookup có thể khác theo môi trường. Cân nhắc namespace resource path theo module/library, hoặc định nghĩa cơ chế override config rõ.

## 6. Resource bên trong JAR không phải một file path thông thường

Sau khi đóng gói, resource có thể có URL:

~~~text
jar:file:/opt/app/app.jar!/config/defaults.properties
~~~

Đừng coi nó là local file ở path `/opt/app/app.jar!/config/defaults.properties`. Code có thể chạy được trong IDE khi URL là `file:/.../classes/config/defaults.properties` nhưng thất bại khi URL là `jar:...`.

Ví dụ **không nên dùng như giải pháp tổng quát**:

~~~java
URL url = Demo.class.getResource("/config/defaults.properties");
Path path = Path.of(url.toURI());
~~~

Đây là mã minh họa thiếu imports và exception handling; trên JAR URI, chuyển sang `Path` có thể thất bại nếu filesystem provider tương ứng chưa được tạo hoặc không hỗ trợ. Để chỉ **đọc** resource, dùng `getResourceAsStream` và `try-with-resources`.

Nếu thư viện thật sự bắt buộc nhận `Path`, có thể copy resource ra temp file với giới hạn kích thước rồi quản lý cleanup, hoặc dùng ZIP FileSystem được mở/đóng có chủ đích trong trường hợp phù hợp. Không tự ép mọi URL thành file local.

Với named modules (JPMS), còn có phạm vi truy cập resource theo module và các quy tắc encapsulation. Nếu ứng dụng chạy module path thay vì classpath truyền thống, phải kiểm tra `Module.getResourceAsStream` và quyền `opens` nơi áp dụng.

## 7. Sai charset: đọc đúng byte nhưng tạo sai String

`Charset` là quy tắc mã hóa ký tự sang bytes và giải mã bytes thành ký tự. Nếu file được ghi bằng UTF-8 nhưng application đọc theo charset khác, dấu tiếng Việt có thể biến thành mojibake.

Ví dụ chữ `Café` được mã hóa UTF-8 thành:

~~~text
43 61 66 C3 A9
~~~

Trong đó `C3 A9` là hai byte biểu diễn `é`. Đọc cùng bytes theo ISO-8859-1 tạo hai ký tự khác với `é`.

~~~java
String text = "Caf\u00e9";
byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
String good = new String(utf8, StandardCharsets.UTF_8);
String wrong = new String(utf8, StandardCharsets.ISO_8859_1);
System.out.println(text.equals(good));  // true
System.out.println(text.equals(wrong)); // false
~~~

Đây là mảnh minh họa trong method có import `StandardCharsets`. Unicode escape trong source giúp một test cụ thể không phụ thuộc khả năng terminal/source editor hiển thị chữ `é`.

### Mặc định không thống nhất giữa mọi API

Một số API nhận charset rõ, một số overload sử dụng default charset của JVM:

- `Files.readString(path, StandardCharsets.UTF_8)`: explicit UTF-8.
- `Files.readString(path)` từ Java 11: overload không đối số charset **được đặc tả dùng UTF-8**.
- `Files.newBufferedReader(path)`: overload không charset dùng UTF-8.
- `new InputStreamReader(input)`: dùng default charset của JVM.
- `new FileReader(file)` không có charset: dùng default charset.
- `new OutputStreamWriter(output)` không có charset: dùng default charset.
- `text.getBytes()` không có charset: dùng default charset.
- `text.getBytes(StandardCharsets.UTF_8)`: không phụ thuộc default.

Với Java 17, default charset có thể tùy môi trường/locale/cấu hình hệ thống. JEP 400 đưa UTF-8 thành default từ JDK 18 theo cấu hình chuẩn, nhưng không biến mọi byte encoding/console behavior thành giống nhau.

**Không khắc phục bằng cách chỉ gọi `System.setProperty("file.encoding", "UTF-8")` giữa chương trình.** Việc đổi property runtime không được bảo đảm làm mọi decoder đã khởi tạo hoặc I/O API đổi encoding. Hãy chỉ định charset tại boundary dữ liệu.

## 8. File charset khác console charset

Luồng hiển thị trên terminal gồm ít nhất ba tầng:

~~~text
File bytes
    └── file decoder (UTF-8, ISO-8859-1, ...)
              └── Java String (Unicode text)
                          └── stdout/console encoder
                                    └── terminal decoder / font
                                               └── ký tự hiển thị
~~~

Nếu Java String đã đúng nhưng terminal hiện sai, sửa charset của file reader có thể làm dữ liệu thành sai. Ngược lại, nếu String đã sai ngay khi decode, thay font terminal không sửa được dữ liệu.

Cách kiểm tra:

1. So sánh String đã decode với expected literal bằng `equals`.
2. In hex bytes nguồn của một đoạn nhỏ, nếu dữ liệu được phép log.
3. In code points của String khi cần, không chỉ nhìn giao diện terminal.
4. Kiểm tra charset của writer phía trước; có thể file đã được encode sai từ nguồn.
5. Phân biệt default charset JVM, encoding do API quy định, encoding của console và hệ điều hành.
6. Với dữ liệu nhạy cảm, log kích thước/hash hoặc mẫu giả lập, không ghi toàn bộ nội dung.

Một lỗi giải mã có thể ném `MalformedInputException` hoặc hiển thị ký tự thay thế, tùy API/decoder policy. `NoSuchFileException` lại là vấn đề tra path; không thể chữa bằng đổi UTF-8.

### UTF-8 BOM và newline

Một số file UTF-8 bắt đầu bằng BOM `EF BB BF`, có thể xuất hiện trong Java String thành `U+FEFF` ở đầu nội dung. Không mặc định mọi parser đều loại bỏ BOM; ứng dụng nên có hợp đồng chấp nhận/bỏ BOM rõ ràng.

CRLF/LF khác nhau giữa môi trường hoặc công cụ tạo file. Nếu parser yêu cầu byte-for-byte reproduction, thao tác `BufferedReader.readLine()` sẽ bỏ newline, nên cần lựa chọn API lưu/đọc bytes phù hợp.

Nếu cần từ chối bytes UTF-8 sai thay vì tự thay bằng ký tự replacement, dùng `CharsetDecoder` với `CodingErrorAction.REPORT` và bắt `CharacterCodingException`.

## 9. Ví dụ Java 17+ độc lập: chạy từ CWD khác

Chương trình sau kiểm tra ba đường tìm dữ liệu và một tình huống sai charset:

1. File tương đối `input.txt` **không có trong CWD runtime**.
2. File input được truyền bằng **đường dẫn tuyệt đối** nên đọc được.
3. Resource `/sample/message.txt` nằm trong classpath root nên đọc được.
4. Dữ liệu `Café` được encode/decode UTF-8 đúng, nhưng decode ISO-8859-1 cho String khác.

Cấu trúc test:

~~~text
demo/
  ResourcePathsDemo.java
  classes/
    ResourcePathsDemo.class
    sample/message.txt           (RESOURCE_OK)
  input/input.txt                (FILE_OK)
  runtime/                       (thư mục chạy chương trình, không có input.txt)
~~~

Lưu thành **`ResourcePathsDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class ResourcePathsDemo {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Provide absolute path to input.txt");
        }
        Path relative = Path.of("input.txt");
        System.out.println("Relative input found: " + Files.exists(relative));

        Path explicit = Path.of(args[0]);
        System.out.println("Explicit file: " +
                Files.readString(explicit, StandardCharsets.UTF_8).trim());

        try (InputStream in = ResourcePathsDemo.class
                .getResourceAsStream("/sample/message.txt")) {
            if (in == null) throw new IOException("Classpath resource missing");
            String resourceText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Classpath resource: " + resourceText.trim());
        }

        String text = "Caf\u00e9";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        System.out.println("UTF-8 bytes: " +
                HexFormat.of().withUpperCase().formatHex(utf8));
        System.out.println("UTF-8 roundtrip: " +
                text.equals(new String(utf8, StandardCharsets.UTF_8)));
        System.out.println("Wrong decoding changes value: " +
                !text.equals(new String(utf8, StandardCharsets.ISO_8859_1)));
        Charset systemDefault = Charset.defaultCharset();
        System.out.println("Default charset inspected: " + (systemDefault != null));
    }
}
~~~

### Chuẩn bị và chạy từ terminal Linux/macOS

Từ thư mục `demo/`:

~~~bash
mkdir -p classes/sample input runtime
printf 'RESOURCE_OK\n' > classes/sample/message.txt
printf 'FILE_OK\n' > input/input.txt
javac -d classes ResourcePathsDemo.java

CLASSES="$(pwd)/classes"
INPUT="$(pwd)/input/input.txt"
cd runtime
java -cp "$CLASSES" ResourcePathsDemo "$INPUT"
~~~

### Chuẩn bị trên Windows PowerShell

Từ thư mục `demo/`:

~~~powershell
New-Item -ItemType Directory -Force classes/sample, input, runtime | Out-Null
Set-Content classes/sample/message.txt 'RESOURCE_OK' -Encoding ASCII
Set-Content input/input.txt 'FILE_OK' -Encoding ASCII
javac -d classes ResourcePathsDemo.java

$classes = (Resolve-Path classes).Path
$inputFile = (Resolve-Path input/input.txt).Path
Push-Location runtime
try {
    java -cp $classes ResourcePathsDemo $inputFile
} finally {
    Pop-Location
}
~~~

File mẫu chỉ có ASCII để script tạo dữ liệu không phụ thuộc cách PowerShell mặc định encode. Các file thực có tiếng Việt nên được lưu/đọc bằng UTF-8 theo contract; chương trình test ký tự `é` bằng Unicode escape của Java.

**Stdout đã kiểm chứng bằng biên dịch/chạy JDK 17 từ một runtime directory tách biệt:**

~~~text
Relative input found: false
Explicit file: FILE_OK
Classpath resource: RESOURCE_OK
UTF-8 bytes: 436166C3A9
UTF-8 roundtrip: true
Wrong decoding changes value: true
Default charset inspected: true
~~~

**Diễn giải kết quả:**

- `Path.of("input.txt")` được kiểm tra theo CWD là `runtime/`, nơi không có file, nên false trong test này.
- `args[0]` trỏ tới `input/input.txt` bằng absolute path và `Files.readString(..., UTF_8)` trả `FILE_OK`.
- `ResourcePathsDemo.class.getResourceAsStream("/sample/message.txt")` tìm tại root của classpath chứa `classes/`, không cần file tồn tại ở CWD.
- `String.getBytes(UTF_8)` của `Café` tạo bytes `43 61 66 C3 A9`; giải mã đúng UTF-8 khôi phục String, giải mã sai ISO-8859-1 tạo giá trị khác.
- Dòng kiểm tra default charset không phụ thuộc việc runtime default là Windows-1252 hay UTF-8. Code có thể quan sát tên bằng `Charset.defaultCharset().name()` khi chẩn đoán thật.
- Resource stream được đóng qua `try-with-resources`; data input đọc được vì location và charset đều tường minh.

**Lưu ý:** `Files.exists` có thể trả false trong những trường hợp không xác định được tồn tại do lỗi I/O/quyền, nên không dùng nó làm bằng chứng tuyệt đối rằng path chưa từng tồn tại. Trong test dựng riêng, `runtime/input.txt` cố tình không được tạo.

## 10. Kiểm thử sau khi package JAR

Nếu đã compile và copy `classes/sample/message.txt` như trên, có thể đóng JAR:

~~~bash
jar --create --file demo.jar --main-class ResourcePathsDemo -C classes .
jar --list --file demo.jar
~~~

Danh sách entries phải có `ResourcePathsDemo.class` và `sample/message.txt`. Đường dẫn resource trong archive không được vô tình thêm tiền tố `classes/` hoặc `src/main/resources/`.

Sau đó chạy JAR từ một working directory khác:

~~~bash
java -jar /absolute/path/to/demo.jar /absolute/path/to/input/input.txt
~~~

Hai đường dẫn trong ví dụ phải được thay bằng absolute paths thật. Nếu JAR resource đúng, `getResourceAsStream("/sample/message.txt")` vẫn đọc được dù CWD là thư mục khác.

Nếu code đã chạy được bằng `java -cp classes ...` nhưng không chạy khi `java -jar ...`, kiểm tra:

- File resource có được package vào JAR không?
- Tên entry có phân biệt hoa/thường khác so với code không?
- Có package/resource filtering làm đổi đường dẫn hoặc bytes không?
- Có phụ thuộc `src/test/resources` chỉ hiện trong test classpath không?
- Có dùng `new File(getResource(...).toURI())` khiến resource trong JAR không còn là file thông thường không?
- Classloader/module của bản chạy đã thay đổi không?

## 11. Maven/Gradle: resources có vòng đời riêng

Trong dự án Maven/Gradle tiêu chuẩn:

~~~text
src/main/java/...
src/main/resources/templates/email.txt
src/test/java/...
src/test/resources/fixtures/data.txt
~~~

`src/main/resources` được copy vào output dành cho runtime/JAR theo build configuration thông thường. `src/test/resources` thuộc test runtime; nó **không mặc định được package vào production JAR**.

Nếu một feature chỉ dùng test fixture và sau đó gọi classpath lookup trong production, có thể trả `null` vì resource không hiện diện.

Resource filtering có thể thay đổi contents (ví dụ biểu thức placeholder, charset và line endings). Với binary assets như ảnh, JKS/PKCS12, ZIP, chứng chỉ, không tùy tiện bật text filtering. Nếu cần content chính xác, kiểm tra checksums trước/sau package.

Trong Gradle/Maven IDE, working directory của Run Configuration thường là project root, nhưng **không nên lấy điều đó làm hợp đồng production**. Dùng path tuyệt đối được truyền vào hoặc resource lookup độc lập CWD.

## 12. Làm test không phụ thuộc một CWD cố định

Với dữ liệu file, dùng thư mục tạm và explicit UTF-8:

~~~java
Path temp = Files.createTempDirectory("io-test-");
Path file = temp.resolve("unicode.txt");
try {
    String expected = "Caf\u00e9";
    Files.writeString(file, expected, StandardCharsets.UTF_8);
    String actual = Files.readString(file, StandardCharsets.UTF_8);
    if (!expected.equals(actual)) throw new AssertionError("Wrong charset");
} finally {
    Files.deleteIfExists(file);
    Files.deleteIfExists(temp);
}
~~~

Đây là đoạn minh họa trong method có imports và throws/exception handling. Test thực tế có thể dùng JUnit `@TempDir` và kiểm tra cleanup tự động.

Với resource, đặt fixture trong test resources và đọc bằng `TestClass.class.getResourceAsStream("/fixtures/data.txt")`. Để bắt lỗi packaging, cần integration test chạy JAR thật, không chỉ chạy tests trên class directory.

### Tái hiện CWD khác trong một test harness

Java không cung cấp một API portable để đổi OS working directory của JVM đang chạy theo thread. Thay vì sửa `user.dir`, có thể khởi chạy **tiến trình Java con** qua `ProcessBuilder.directory(...)`:

~~~java
ProcessBuilder pb = new ProcessBuilder(
        "java", "-cp", absoluteClasspath, "ResourcePathsDemo", absoluteInput);
pb.directory(otherWorkingDir.toFile());
Process child = pb.start();
int exitCode = child.waitFor();
~~~

Đây là snippet minh họa chưa độc lập: `absoluteClasspath`, `absoluteInput`, `otherWorkingDir` cần được khai báo và xử lý `IOException`/`InterruptedException`. Với integration test thực tế, cần đọc stdout/stderr (tránh pipe bị tắc), timeout, và kiểm tra exit code/expected output. Có thể dùng Java executable từ toolchain thay vì giả định luôn có `java` trên PATH.

**Mục tiêu của test:** buộc các path sai dựa vào project root bị lộ rõ và xác nhận resource lookup/explicit input hoạt động khi CWD khác.

## 13. Lỗi thường gặp và cách phân loại

| Hiện tượng | Nhóm lỗi | Kiểm tra / xử lý |
| --- | --- | --- |
| `NoSuchFileException` | Path không tồn tại ở vị trí được mở | Log absolute target và CWD |
| `FileNotFoundException` | File stream không mở được path | Kiểm tra path, file type, quyền |
| `AccessDeniedException` | Thiếu quyền truy cập | Kiểm tra account, ACL, mount mode |
| `InvalidPathException` | Syntax path không hợp lệ | Kiểm tra input path theo OS/provider |
| `getResourceAsStream()==null` | Resource lookup thất bại | Kiểm tra leading slash, package, JAR/classloader |
| `FileSystemNotFoundException` khi đổi resource URI thành Path | Sai cơ chế filesystem provider | Đọc qua `InputStream` hoặc mở filesystem provider đúng |
| `MalformedInputException` | Bytes không hợp lệ với decoder | Kiểm tra original bytes và charset |
| `UnmappableCharacterException` | Encoding/decoding không biểu diễn được theo policy | Chọn charset và error strategy rõ |
| Có text lỗi dấu nhưng không exception | Mojibake hoặc decoder replacement | So sánh Unicode/code points và hex |
| Resource đúng tên nhưng nội dung sai | Trùng resource trong dependencies | Enumerate resource URLs và kiểm soát classloader |

Không bắt rộng `IOException` rồi báo tất cả trường hợp là “Không tìm thấy file”. Làm vậy che lỗi quyền, ổ đĩa, encoding, provider hoặc kết nối mạng.

## 14. Tệp lớn, resource stream và những giới hạn khác

`Files.readString` và `InputStream.readAllBytes()` đọc toàn bộ dữ liệu vào bộ nhớ; phù hợp với cấu hình hoặc resource nhỏ, nhưng không phải mặc định tốt cho log nhiều GB.

Với file văn bản lớn, đọc theo streaming:

~~~java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        handle(line);
    }
}
~~~

Đây là snippet minh họa; `handle` do ứng dụng cung cấp, imports/exception handling cần đầy đủ. `readLine()` không giữ newline terminators, nên nếu cần bảo toàn byte-exact file để xác thực checksum/chữ ký, hãy thao tác bytes thay vì khôi phục text.

Với binary file như ZIP, hình ảnh, key store hoặc compressed resource, tránh decode thành String rồi encode lại; điều đó có thể phá bytes. Dùng `InputStream` và đúng binary parser.

Khi xử lý cấu hình đầu vào không tin cậy, giới hạn kích thước, đường dẫn được phép, độ sâu cấu trúc và thời gian I/O. Resource path được normalize không đồng nghĩa an toàn trước symlink/race; phải chọn chiến lược bảo vệ theo threat model.

## 15. Checklist chẩn đoán theo nhóm lỗi

### A. Relative file path không hoạt động

1. In/log `Path.of("").toAbsolutePath()`, `user.dir` và `path.toAbsolutePath().normalize()`.
2. Kiểm tra file tồn tại ở vị trí thực, quyền và loại file.
3. So sánh CWD của IDE, terminal, service manager, Docker `WORKDIR` hoặc container process.
4. Truyền một base directory qua args/config thay vì giả định project root.
5. Thử chạy từ CWD khác bằng process con.

### B. Classpath resource trả null

1. Kiểm tra path của resource **trong build output/JAR**, không chỉ trong source tree.
2. Kiểm tra leading slash của `Class.getResourceAsStream` và không dùng slash đầu trong `ClassLoader.getResourceAsStream`.
3. Kiểm tra package-relative path, case sensitivity và classloader/module boundary.
4. Xác nhận main resources thay vì test-only resources.
5. Nếu có nhiều resources trùng tên, enumerate URL matches.
6. Với resource trong JAR, dùng `InputStream`, không tự biến nó thành filesystem `File`.

### C. Chữ Việt bị lỗi

1. Xác nhận đã mở đúng file/resource.
2. Xem bytes gốc, charset do producer dùng và charset decoder hiện tại.
3. Dùng explicit `StandardCharsets.UTF_8` khi hợp đồng là UTF-8.
4. Kiểm tra BOM, malformed sequences, decoder replacement policy.
5. Phân biệt Java String sai với console hiển thị sai.
6. Tạo regression test kiểm tra String equality/code points/expected bytes.

### D. IDE chạy tốt nhưng production sai

1. So sánh CWD, JVM version, default charset, locale, classpath, module path.
2. Kiểm tra JAR contents và tài nguyên đã đóng gói.
3. Kiểm tra environment variables, system properties, quyền service account.
4. Xác minh case-sensitive filenames trên OS đích.
5. Chạy integration test từ CWD khác và với build artifact thật.
6. Log đầy đủ các thông số đường dẫn/charset cần thiết nhưng không lộ bí mật.

## 16. Các anti-pattern đặc trưng

| Cách làm sai | Vì sao lỗi | Thay bằng |
| --- | --- | --- |
| `Path.of("src/main/resources/template.txt")` trong production | Source tree có thể không tồn tại | `getResourceAsStream("/template.txt")` |
| Giả định `java -jar` thay CWD | JAR location khác process CWD | Base dir tường minh |
| Gọi `getResourceAsStream("config/x.txt")` từ class ở package khác | Tên relative tính từ package | Dùng slash root nếu cần |
| Dùng `ClassLoader.getResourceAsStream("/config/x.txt")` | Sai quy tắc tên resource classloader | Bỏ leading slash |
| Ép `jar:` URL thành file địa phương | Resource JAR không phải file local độc lập | Đọc bằng stream |
| Lưu dữ liệu runtime vào JAR/classpath | Thường read-only/không writable | Data directory tách biệt |
| `InputStreamReader(in)` không charset với file UTF-8 | Default charset có thể khác | `InputStreamReader(in, UTF_8)` |
| `String.getBytes()` cho format cố định | Phụ thuộc default charset | `getBytes(UTF_8)` |
| Đổi `user.dir` để “sửa” CWD | Không portable cho OS/process | Truyền base Path hoặc ProcessBuilder.directory |
| Chỉ kiểm tra `Files.exists` rồi giả định chắc chắn | Race/permission errors | Mở/đọc thật và xử lý exception |
| Mọi `IOException` đều báo file missing | Che mất nguyên nhân cụ thể | Phân loại theo exception và operation |
| Đổi file decoder vì terminal in sai dấu | Console có thể sai, String đã đúng | So sánh String và terminal riêng |
| Đọc binary bytes như text | Có thể phá dữ liệu không phục hồi | Dùng byte streams |
| Dùng `readAllBytes` cho file rất lớn | Có thể tăng heap/OOME | Streaming và bounded memory |

## 17. Câu hỏi tự kiểm tra

1. `Path.of("data.txt")` tìm cạnh class file hay ở CWD? **Ở CWD của tiến trình**, không phải thư mục class.
2. `java -jar /opt/app/app.jar` có tự đổi CWD sang `/opt/app` không? **Không**.
3. Cách kiểm tra absolute path mà ứng dụng đang tìm? `path.toAbsolutePath().normalize()`.
4. Có nên dùng `System.setProperty("user.dir", ...)` để đổi OS working directory ở runtime không? **Không**, không portable.
5. `src/main/resources` có luôn tồn tại trên server sau build không? **Không**.
6. Với `Class.getResourceAsStream("/config/x.txt")`, leading slash nghĩa gì? **Tìm từ resource root**.
7. `Class.getResourceAsStream("x.txt")` không slash tìm ở đâu? **Relative với package của class**.
8. Với `ClassLoader.getResourceAsStream` có nên bắt đầu bằng slash không? **Thông thường không**.
9. Resource trong JAR có luôn chuyển thẳng thành `File` được không? **Không**; ưu tiên stream.
10. `Files.readString(path)` trong JDK 17 có dùng default charset không? **Không**, overload đó dùng UTF-8.
11. `new InputStreamReader(in)` không đối số charset thì sao? **Dùng default charset JVM**.
12. UTF-8 bytes của `Café` là gì? **43 61 66 C3 A9**.
13. Decode bytes UTF-8 bằng ISO-8859-1 có thể sai chữ dù không exception? **Có**.
14. Java String đúng nhưng terminal sai dấu có chứng minh file bị lỗi không? **Không**; có thể là console encoding/font.
15. BOM UTF-8 có bytes nào? **EF BB BF**.
16. `Files.exists(path)==false` chứng minh chắc file không tồn tại không? **Không tuyệt đối**, có race và lỗi quyền/I/O.
17. Làm sao phát hiện app phụ thuộc ngầm vào IDE working directory? **Khởi chạy từ một CWD khác trong integration test**.
18. File chỉ có trong `src/test/resources` có tự vào production JAR không? **Thông thường không**.
19. Nếu classpath có hai resources cùng tên, có nguy cơ đọc nhầm không? **Có**, cần namespace/kiểm soát classloader.
20. Dữ liệu runtime có nên ghi vào `src/main/resources`/JAR không? **Không**, hãy dùng data directory writable rõ ràng.

## Tài liệu API tham khảo

- [Java SE 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): relative/absolute path, normalize, toRealPath.
- [Java SE 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): readString, newBufferedReader, newInputStream và I/O errors.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): getResource/getResourceAsStream và package-relative lookup.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): tra cứu và enumerate resources.
- [Java SE 17 — Charset](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/Charset.html) và [StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): charset mặc định, UTF-8 và các charset chuẩn.
- [Java SE 17 — CharsetDecoder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetDecoder.html): malformed bytes và decoder policy.
- [Java SE 17 — ProcessBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html): chạy process con với working directory đã chỉ định.
- [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400): sự thay đổi từ JDK 18 và vấn đề tương thích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-010">Chẩn đoán dữ liệu chưa flush, file handle còn mở và buffer sai trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán dữ liệu chưa flush, file handle còn mở và buffer sai trạng thái trong Java IO/NIO.2

Một ứng dụng có thể gọi `write()` mà file vẫn rỗng; file không xóa hoặc đổi tên được vì tài nguyên còn mở; hoặc `FileChannel` ghi ra 0 byte dù `ByteBuffer` đã có dữ liệu. Ba triệu chứng này thường bị gộp chung thành “IO chưa ghi xong”, nhưng chúng đến từ **ba lớp trạng thái khác nhau**:

1. **Bộ đệm đầu ra chưa flush**: dữ liệu còn nằm trong `BufferedWriter`, `BufferedOutputStream` hoặc tầng mã hóa ký tự, chưa được chuyển hết xuống tầng IO bên dưới.
2. **Tài nguyên hệ điều hành chưa close**: `InputStream`, `OutputStream`, `Reader`, `Writer`, `FileChannel`, `DirectoryStream` hoặc `Stream` dựa trên tệp còn giữ tài nguyên; có thể tích lũy file descriptor/handle hoặc file lock.
3. **`Buffer` sai `position`/`limit`**: dữ liệu đã có trong vùng nhớ nhưng API NIO không còn byte “remaining” để đọc/ghi, hoặc dữ liệu cũ bị ghi lặp/lẫn với dữ liệu chưa hợp lệ.

Để sửa đúng, trước hết phải xác định dữ liệu đã đi qua những tầng nào, tài nguyên do ai sở hữu, và trạng thái của buffer tại thời điểm gọi API.

## 1. Chuỗi truyền dữ liệu: write, flush, close và độ bền trên đĩa

Một đường ghi phổ biến có các tầng:

```text
Dữ liệu ứng dụng
   └─ BufferedWriter (buffer ký tự)
        └─ OutputStreamWriter (mã hóa ký tự thành byte)
             └─ BufferedOutputStream (buffer byte, nếu có)
                  └─ FileOutputStream / FileChannel
                       └─ bộ đệm của hệ điều hành / filesystem
                            └─ thiết bị lưu trữ
```

Không phải chương trình nào cũng có đầy đủ mọi tầng. Ví dụ `Files.newBufferedWriter(path, UTF_8)` trả về một writer có buffering; `Files.writeString` là API ghi tiện ích, không để lại writer cho code gọi tự đóng.

| Hành động | Điều được bảo đảm trong phạm vi hợp đồng thông thường | Điều **không** được bảo đảm |
| --- | --- | --- |
| `writer.write(...)` | Đẩy dữ liệu vào trạng thái của writer; có thể vẫn chỉ nằm trong bộ đệm | File lập tức quan sát được toàn bộ dữ liệu |
| `writer.flush()` | Yêu cầu đẩy dữ liệu đang buffer xuống destination bên dưới, và flush các tầng writer/stream trong chuỗi theo hợp đồng | Byte đã được lưu bền vững trên thiết bị vật lý |
| `writer.close()` | Với writer chuẩn như `BufferedWriter`, flush trước khi close và giải phóng tài nguyên; có thể ném lỗi IO | Một giao dịch nghiệp vụ hoàn tất hay file được `fsync` tuyệt đối |
| `FileChannel.force(true)` | Yêu cầu buộc thay đổi nội dung và metadata qua channel xuống thiết bị theo bảo đảm cụ thể của provider | Biến mọi filesystem, NFS/remote mount, hardware cache thành giao dịch bền vững |
| `FileDescriptor.sync()` | Yêu cầu hệ điều hành đồng bộ dữ liệu liên quan tới descriptor | Thay thế cho việc flush bộ đệm ở tầng Java còn chưa chuyển byte xuống descriptor |

**Phân biệt ba câu hỏi:** “Đã `write`?” khác “Đã `flush` xuống hệ điều hành?” và khác “Đã `force/sync` để giảm nguy cơ mất dữ liệu do crash?”. Nếu writer còn giữ ký tự trong RAM, gọi `force` trên file channel bên dưới mà chưa flush writer **không thể đồng bộ các byte chưa đến channel**.

Với xử lý file thông thường, `try-with-resources` và việc kiểm tra exception từ close thường đủ để đảm bảo dữ liệu ở tầng Java được xả và tài nguyên được giải phóng. Với yêu cầu độ bền cao (nhật ký giao dịch, checkpoint), cần thêm hợp đồng đồng bộ cụ thể, file tạm, đổi tên đúng cách và kiểm tra hỗ trợ của filesystem.

## 2. Dấu hiệu của dữ liệu chưa được flush

Các tình huống quen thuộc:

- Gọi `write("hello")` vào `BufferedWriter`, sau đó một tiến trình khác đọc file và chưa thấy `"hello"`. Writer có thể vẫn giữ chuỗi đó trong buffer.
- Dùng `PrintWriter.print(...)` và chờ dữ liệu xuất hiện ngay. Cờ `autoFlush=true` của `PrintWriter` tự flush khi gọi `println`, `printf` hoặc `format`, **không** phải sau mọi `print`/`write`.
- Có nhiều tầng buffering nhưng chỉ xử lý một tầng thủ công không đúng chủ sở hữu. Trong thiết kế bình thường, flush ở **wrapper ngoài cùng** cho dữ liệu đi xuống các tầng bên dưới.
- Tiến trình kết thúc bất thường trước khi đóng writer. `try-with-resources` xử lý luồng điều khiển Java thông thường và exception, nhưng không chạy khi tiến trình bị kill ngay lập tức hoặc JVM/crash ở cấp hệ điều hành.
- Ghi dữ liệu sau khi đã log “saved” nhưng thực tế đoạn code ghi/flush/close có thể ném exception. Nên chỉ công bố thành công sau khi toàn bộ đường ghi trả về bình thường.

Mẫu đúng:

```java
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

Path file = Path.of("report.txt");
try (BufferedWriter writer =
         Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
    writer.write("hello");
    writer.newLine();
    writer.flush(); // Chỉ cần nếu phải thấy dữ liệu trước khi đóng.
} // close sẽ flush và đóng trong đường thực thi bình thường.
```

Đoạn trên là **mã minh họa trong một phương thức**; thiếu lớp và `main`. Nếu không có nhu cầu quan sát file trước khi đóng, thường không cần gọi `flush()` thủ công ngay trước `close()`. Nhưng nếu output là một giao thức tương tác như socket, phải flush tại các điểm cần gửi phản hồi, không đợi đóng kết nối.

**Không dùng `Thread.sleep(...)` làm giải pháp flush.** Chờ đợi không cam kết `BufferedWriter` tự xả bộ đệm. Cũng không nên dùng `flush` sau *mỗi byte* khi ghi file lớn, vì nhiều syscall nhỏ làm giảm hiệu năng.

### PrintWriter che giấu lỗi IO

Khác nhiều writer, `PrintWriter` không ném `IOException` trực tiếp từ các lệnh ghi thường dùng; `checkError()` cung cấp trạng thái lỗi và có thể flush nếu stream chưa đóng. Vì vậy việc gọi `println` mà không có exception **không đủ** để kết luận file đã ghi thành công. Với code cần báo lỗi rõ ràng, ưu tiên `BufferedWriter` hoặc API có exception minh bạch; nếu dùng `PrintWriter`, kiểm tra lỗi tại điểm kết thúc thích hợp.

## 3. File handle còn mở: ai có trách nhiệm close?

“File handle” trên Windows và “file descriptor” trên Unix-like là tài nguyên hệ điều hành. Một đối tượng Java giữ stream/channel mở có thể tiếp tục giữ tài nguyên này, trực tiếp hoặc qua wrapper. Những biểu hiện phổ biến:

- `Too many open files` / `EMFILE` khi mở nhiều tài nguyên mà không đóng, đặc biệt trong server xử lý liên tục.
- Trên Windows, `AccessDeniedException`, lỗi move/delete hoặc ứng dụng khác không đổi tên được file khi một process còn giữ handle có chế độ chia sẻ không phù hợp. Trên Unix, thường có thể unlink tên file đang mở; dữ liệu/tài nguyên vẫn tồn tại cho tới khi handle cuối đóng. Hành vi cụ thể phụ thuộc filesystem và quyền OS.
- File lock giữ lâu hơn cần thiết, worker khác không lấy được lock hoặc thao tác cần quyền độc quyền bị chặn.
- Sau một số request, bộ đếm descriptor tăng theo thời gian; một restart tạm “chữa” triệu chứng rồi lỗi lặp lại.

**Nguyên tắc sở hữu:** nơi tạo stream/channel thường chịu trách nhiệm đóng nó. Trả một stream ra khỏi hàm mà không định nghĩa caller phải đóng là nguồn gây leak. Nếu API trả một `Stream<String>` đọc file lazily, cần dùng try-with-resources tại nơi thực sự tiêu thụ.

Ví dụ đọc file và tự đóng nguồn:

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

Path path = Path.of("events.log");
try (Stream<String> lines =
         Files.lines(path, StandardCharsets.UTF_8)) {
    long errors = lines.filter(line -> line.contains("ERROR")).count();
    System.out.println(errors);
}
```

Đây là **đoạn trong phương thức `throws IOException` hoặc tự xử lý lỗi**, không phải chương trình Java độc lập. `Files.lines`, `Files.list`, `Files.walk` và `Files.find` có thể giữ nguồn filesystem mở; cần đóng `Stream` trả về. Lệnh terminal `cat` hay `grep` không kiểm soát vòng đời của Java stream.

Với channel hoặc khóa file, dùng đúng kiểu close:

```java
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

try (FileChannel channel = FileChannel.open(
         Path.of("state.bin"), StandardOpenOption.CREATE,
         StandardOpenOption.READ, StandardOpenOption.WRITE);
     FileLock lock = channel.lock()) {
    // Tác vụ cần khóa, thực hiện trong phạm vi try này.
} // Khóa được giải phóng, sau đó channel được đóng (đảo thứ tự tạo).
```

Đây là đoạn minh họa. `FileLock` còn phụ thuộc khả năng hỗ trợ/semantics của OS và filesystem. Việc đóng một wrapper có thể đóng luôn stream/channel bên dưới; đừng dùng tiếp tài nguyên ấy sau khi wrapper sở hữu nó đã close. Ngược lại, `ByteArrayOutputStream` là buffer trong RAM, không mở file descriptor; không nên đánh đồng mọi `close()` với thao tác giải phóng file.

### Cách tìm file descriptor bị leak

| Môi trường | Công cụ quan sát hữu ích | Lưu ý |
| --- | --- | --- |
| Linux | `lsof -p <PID>`; `ls -l /proc/<PID>/fd` | `lsof` có thể cần cài riêng/quyền phù hợp; đường dẫn `/proc` có thể bị hạn chế trong container |
| Windows | Resource Monitor → CPU → Associated Handles; Sysinternals Process Explorer/Handle | Tìm theo tên file/process, chú ý việc chia sẻ handle và quyền truy cập |
| JVM trên Unix | `com.sun.management.UnixOperatingSystemMXBean.getOpenFileDescriptorCount()` | API mở rộng phụ thuộc nền tảng; chỉ cho **tổng số**, không chỉ ra file nào leak |
| Trong ứng dụng | Metric số lượng descriptor/handle; log đường dẫn, vị trí tạo, sự kiện close; stress test lặp lại | Không log toàn bộ tên file nhạy cảm, thông tin user hay nội dung file vào hệ thống giám sát công khai |

Với leak, hãy quan sát **xu hướng dưới tải rồi sau khi workload hoàn tất**. Con số đang tăng trong lúc có nhiều kết nối hoạt động chưa chắc là leak; đáng nghi khi vẫn tăng sau mỗi vòng tải tương đương và không quay về baseline phù hợp.

Đừng suy từ một JVM thread dump rằng mọi file đang mở đã được phát hiện: thread dump cho biết stack/thread, không liệt kê đầy đủ descriptor. GC hay `System.gc()` cũng **không phải** cơ chế giải phóng file handle đáng tin cậy; hãy close rõ ràng.

## 4. ByteBuffer: capacity, position, limit và remaining

`ByteBuffer` là buffer có trạng thái. Bốn con số quan trọng:

- `capacity`: dung lượng cố định tính theo byte.
- `position`: chỉ số của phần tử kế tiếp sẽ đọc hoặc ghi theo thao tác tương đối.
- `limit`: ranh giới *exclusive* của phần còn được phép đọc/ghi.
- `remaining() = limit - position`: số byte các phương thức tương đối còn có thể xử lý.

Bất biến là `0 <= position <= limit <= capacity`.

Ví dụ `ByteBuffer.allocate(8)` sau khi ghi các byte `A B C` bằng `put`:

```text
Index:       0  1  2  3  4  5  6  7
Bytes:       A  B  C  ?  ?  ?  ?  ?
State:       position=3, limit=8, capacity=8
```

Dấu `?` tượng trưng phần không thuộc dữ liệu hợp lệ của ví dụ. Sau `flip()`:

```text
Index:       0  1  2  3  4  5  6  7
Bytes:       A  B  C  ?  ?  ?  ?  ?
State:       position=0, limit=3, capacity=8
Remaining:   A  B  C
```

Nếu gọi `FileChannel.write(buffer)` **trước `flip`**, channel sẽ đọc từ `position=3` đến `limit=8`, tức không phải 3 byte vừa đưa vào. Một trường hợp khác, nếu buffer đã `position==limit`, `write` có thể trả 0 vì `!hasRemaining()`, dù mảng bên trong vẫn giữ dữ liệu cũ.

### Bốn phép chuyển trạng thái cần nhớ

| API | Thay đổi trạng thái | Dùng lúc nào | Hiểu sai thường gặp |
| --- | --- | --- | --- |
| `flip()` | `limit = position; position = 0` | Kết thúc **ghi dữ liệu vào buffer**, chuẩn bị đọc từ buffer | Không gọi trước ghi channel; hoặc gọi hai lần khiến limit thành 0 |
| `clear()` | `position = 0; limit = capacity` | Chuẩn bị ghi đè buffer khi dữ liệu cũ đã được xử lý hết | **Không xóa byte vật lý**; làm mất dấu vùng dữ liệu còn chờ đọc |
| `rewind()` | `position = 0`, giữ `limit` | Đọc lại cùng vùng byte hợp lệ | Nhầm với `clear` và đọc cả byte không hợp lệ |
| `compact()` | Chuyển byte còn `remaining` về đầu; position đặt sau chúng; limit về capacity | Đã tiêu thụ **một phần**, giữ byte chưa xử lý để nhận thêm | Nhầm với clear làm mất dữ liệu chưa dùng |

`mark/reset` cũng là trạng thái; các thao tác `flip/clear/rewind/compact` thường hủy mark theo hợp đồng của `Buffer`. Khi debug, in `position`, `limit`, `capacity` và `remaining()` trước/sau mỗi lần channel read/write sẽ nhanh hơn việc chỉ in địa chỉ của buffer.

**`buffer.array()` không đồng nghĩa với “dữ liệu hợp lệ trong buffer”.** Với heap buffer, nó có thể bao gồm byte đã dùng, byte trống hoặc byte không nằm giữa `position`/`limit`; với direct/read-only buffer có thể không truy cập được backing array. Để đọc đúng phần còn lại, dùng `byte[] result = new byte[buffer.remaining()]; buffer.get(result);` hoặc dùng view `slice()` theo nhu cầu.

## 5. Ví dụ Java 17+ có stdout xác định

Ví dụ dùng một file text có `BufferedWriter` và một file nhị phân có `ByteBuffer/FileChannel`. Tệp được tạo trong thư mục tạm để không phụ thuộc tên file bên ngoài. Không có tác vụ cùng lúc thay đổi chúng.

Tạo `IoDiagnosticsDemo.java`:

```java
import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IoDiagnosticsDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("io-diagnostics-");
        Path text = dir.resolve("notes.txt");

        // 1) Xả bộ đệm, đọc lại trong khi writer vẫn mở.
        try (BufferedWriter writer =
                Files.newBufferedWriter(text, StandardCharsets.UTF_8)) {
            writer.write("hello");
            writer.flush();
            System.out.println("after flush="
                    + Files.readString(text, StandardCharsets.UTF_8));
        }
        System.out.println("after close="
                + Files.readString(text, StandardCharsets.UTF_8));

        // 2) Chuyển ByteBuffer từ chế độ put sang channel.write.
        Path binary = dir.resolve("data.bin");
        try (FileChannel out = FileChannel.open(binary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.put("ABC".getBytes(StandardCharsets.UTF_8));

            System.out.printf("before flip: pos=%d limit=%d%n",
                    buffer.position(), buffer.limit());
            buffer.flip();
            System.out.printf("after flip: pos=%d limit=%d%n",
                    buffer.position(), buffer.limit());

            while (buffer.hasRemaining()) {
                out.write(buffer);
            }
        }
        System.out.println("saved="
                + Files.readString(binary, StandardCharsets.UTF_8));

        // 3) Chiều ngược lại: channel.read rồi flip mới get.
        try (FileChannel in = FileChannel.open(binary,
                StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            int count = in.read(buffer);
            System.out.printf("after read: n=%d pos=%d limit=%d%n",
                    count, buffer.position(), buffer.limit());

            buffer.flip();
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            System.out.println("decoded="
                    + new String(result, StandardCharsets.UTF_8));
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 IoDiagnosticsDemo.java
java IoDiagnosticsDemo
```

Stdout trên JDK 17+ khi chạy với filesystem thông thường, không có tác vụ khác can thiệp:

```text
after flush=hello
after close=hello
before flip: pos=3 limit=8
after flip: pos=0 limit=3
saved=ABC
after read: n=3 pos=3 limit=8
decoded=ABC
```

Mỗi `try-with-resources` quản lý tài nguyên cụ thể, đồng thời làm lộ lỗi close thành exception nếu có. Lệnh `flush()` ở ví dụ cho phép kiểm tra `notes.txt` **trước khi writer đóng**; không phải yêu cầu phải flush hai lần ở mọi chương trình.

File `data.bin` có đúng ba byte `ABC` vì `flip` giới hạn vùng được write thành `[0,3)`; vòng `while (buffer.hasRemaining())` xử lý khả năng channel ghi ít hơn số byte remaining trong một lần gọi. Với nonblocking channel, nếu `write` trả 0 lặp lại, phải dùng `Selector` hoặc chiến lược chờ phù hợp, không spin vô hạn; ví dụ này dùng `FileChannel` mở để ghi file thông thường.

## 6. Quy trình debug khi file rỗng, thiếu byte hoặc dữ liệu xuất hiện muộn

Khi gặp một file “đã write nhưng chưa có dữ liệu”, nên kiểm tra theo thứ tự sau:

1. **Đúng file và đúng đường dẫn?** In/log đường dẫn đã chuẩn hóa và nơi chạy thực tế (working directory, file path, mount/container). Nhầm file tương đối là nguyên nhân rất thường gặp. Không đưa đường dẫn nhạy cảm vào log công khai.
2. **Mở file bằng option nào?** `TRUNCATE_EXISTING` xóa nội dung cũ; `APPEND` thêm vào cuối; `CREATE_NEW` từ chối ghi đè file tồn tại. Nếu thread khác mở file với truncate, có thể thấy file trống dù writer trước đã thành công.
3. **Có wrapper buffer không?** `BufferedWriter`, `BufferedOutputStream`, `OutputStreamWriter` có thể giữ dữ liệu; flush/close đúng lớp sở hữu. Dùng phép đọc lại chỉ *sau flush* để xác minh visibility, không kết luận từ kích thước đọc được khi buffer chưa xả.
4. **Exception có bị nuốt không?** Kiểm tra `IOException` ở write, flush, close; với `PrintWriter`, chú ý `checkError()`.
5. **ByteBuffer có còn remaining không?** Ghi log `position/limit/capacity`, `n=channel.write(buffer)` và cập nhật sau mỗi lần gọi. Phân biệt byte count trả về với số byte đã intended.
6. **Có gọi force/sync theo yêu cầu durability không?** Chỉ xét sau khi Java buffer đã flush; nhớ `force` và `flush` giải quyết hai tầng khác nhau.
7. **Ai còn ghi vào file?** Đồng bộ các worker, không cho phép đối tượng khác thay dữ liệu giữa kiểm tra và đọc lại. Tránh khẳng định “flush thất bại” khi nguyên nhân là race hoặc ứng dụng đang đọc file khác.

Thay vì ghi `System.out.println("saved")` ngay sau khi đưa dữ liệu vào buffer, hãy quan sát các mốc `write completed`, `flush completed`, `close completed`, `file published` theo nhu cầu. Không ghi log từng byte trong hot path production; dùng metric/các điểm trace khi tái hiện lỗi.

## 7. Quy trình debug khi file handle tăng liên tục

Một kịch bản rò rỉ:

```java
// Sai: mỗi lần gọi đều mở một Stream từ filesystem nhưng không close.
long countErrors(Path file) throws java.io.IOException {
    return java.nio.file.Files.lines(file)
            .filter(s -> s.contains("ERROR"))
            .count();
}
```

Vì `Files.lines` trả lazy Stream, việc `count()` kết thúc **không tự động gọi `close()` cho stream**. Sửa:

```java
long countErrors(Path file) throws java.io.IOException {
    try (java.util.stream.Stream<String> lines =
            java.nio.file.Files.lines(file)) {
        return lines.filter(s -> s.contains("ERROR")).count();
    }
}
```

Đây là hai **phương thức minh họa độc lập** đặt bên trong lớp, không biên dịch như một file chứa cả hai phương thức cùng chữ ký. Trong dự án thật, chỉ giữ phiên bản đã sửa.

Cách tái hiện/leak-test:

1. Đo số descriptor/handle trước khi chạy một vòng workload có đủ request tương đương.
2. Chạy lặp những nhánh **thành công, exception, cancellation, timeout**. Chính nhánh lỗi thường bỏ qua `close()` nếu code đóng thủ công cuối phương thức.
3. Sau khi workload dừng, so sánh baseline, liệt kê handle/path bằng công cụ OS rồi đối chiếu code tạo stream/channel/reader/writer.
4. Chuyển về `try-with-resources`; nếu nhiều resource, khai báo tài nguyên theo đúng thứ tự sở hữu. Đảm bảo các nhánh `return`, `throw` đều đóng đúng tài nguyên.
5. Chạy lại cùng workload; xác nhận số descriptor không tăng vô hạn và các thao tác file move/delete/lock cần thiết hoạt động bình thường.

Nếu tài nguyên được chuyển cho caller hoặc một async job, phải chuyển rõ ownership và bảo đảm close **sau khi** task hoàn thành. Đóng channel quá sớm trong khi worker còn IO có thể gây `ClosedChannelException` hoặc `AsynchronousCloseException`; đóng quá muộn lại giữ handle không cần thiết. Hủy `Future` không chứng minh IO đã dừng tức thời.

## 8. Các lỗi ByteBuffer thường gặp và phép thử

### Quên flip trước khi ghi ra channel

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
buffer.put(new byte[] {65, 66, 67});
// Sai: chưa flip; remaining là 5, không phải 3 byte "ABC".
channel.write(buffer);
```

Đoạn minh họa nằm trong phương thức có `WritableByteChannel channel`. Sửa bằng `buffer.flip()` trước khi chuyển sang đọc dữ liệu từ buffer. Khi dùng `ByteBuffer.wrap(byte[])`, vị trí ban đầu là 0 và limit bằng độ dài mảng, nên **không gọi flip vô điều kiện** ngay sau `wrap` nếu chưa có thao tác `put`; nếu không có thể biến limit thành 0.

### Quên flip sau channel.read

```java
ByteBuffer dst = ByteBuffer.allocate(1024);
int n = channel.read(dst);
if (n > 0) {
    dst.flip();
    // Xử lý chính xác dst.remaining() byte, không phải capacity.
}
```

Nếu quên `flip`, lời gọi `get` có thể đọc phần chưa được điền hoặc không đọc được vùng vừa nhận, tùy `position/limit`. Nếu đọc tới cuối stream, `read()` thường trả `-1`; với channel không blocking, `0` có thể có nghĩa tạm chưa có dữ liệu, **không** phải EOF.

### Dùng clear dù vẫn còn frame chưa xử lý

Trong parser protocol, một lần `read` có thể chỉ nhận nửa thông điệp. Sau khi `flip` và tiêu thụ các frame hoàn chỉnh, dùng `compact()` để dồn byte của frame chưa đủ về đầu buffer rồi đọc thêm:

```java
// Mã minh họa trong vòng xử lý protocol, không phải main độc lập.
buffer.flip();
consumeCompleteFrames(buffer); // Nâng position qua những byte đã xử lý.
buffer.compact();              // Giữ byte còn lại để lần đọc tiếp tục.
```

Nếu dùng `clear()` ở đây, dữ liệu còn lại bị xem như không tồn tại dù byte vật lý có thể vẫn nằm trong mảng. Nếu dùng `rewind()` sau khi đã xử lý nửa buffer, phần đầu đã xử lý có thể bị tiêu thụ lại.

### Shared buffer giữa nhiều luồng

`ByteBuffer` không thread-safe cho việc cùng thay đổi `position/limit` trên một instance. Nếu nhiều worker cùng thao tác relative `get/put` vào một buffer, có thể mất byte/đảo thứ tự dù `FileChannel` có hỗ trợ dùng giữa nhiều luồng. Dùng buffer riêng theo task, hoặc đồng bộ và chia slice rõ ràng; đừng chia sẻ một `ByteBuffer` mutable mà không quy định ownership.

### Partial write và trạng thái sau ghi

`FileChannel.write(buffer)` trả số byte thực sự ghi. Chỉ đóng/công bố file khi buffer không còn remaining hoặc có xử lý lỗi thích hợp. `write` không làm buffer tự `clear` hay tự `flip`; nó tăng `position` theo số byte đã chuyển. Với thao tác ghi file có nhiều buffer, cần xem cả số byte ghi thực tế và vị trí ghi tệp, tránh dùng `buffer.array().length` như số byte đã xuất.

## 9. Kiểm thử và chẩn đoán tự động

Nên xây test riêng cho ba lớp vấn đề để biết lỗi thuộc về đâu.

| Tình huống test | Bằng chứng cần có | Lỗi được phát hiện |
| --- | --- | --- |
| Ghi vài ký tự vào `BufferedWriter`, gọi `flush`, đọc ngay khi writer còn mở | Nội dung đọc lại đúng | Thiếu flush ở điểm cần visibility |
| Kết thúc `try-with-resources` rồi đọc lại | Dữ liệu đầy đủ; exception close được xử lý | Quên close/nuốt lỗi cuối đường ghi |
| Thao tác nhiều lần lên `Files.lines`, `Files.walk`, `FileChannel` | Descriptor không tăng mất kiểm soát sau workload | Leak tài nguyên |
| Chạy nhánh exception, cancellation, early return | Tài nguyên vẫn được đóng, không còn file lock vô ý | Chỉ đóng ở happy path |
| `ByteBuffer.put` → `flip` → ghi channel | Số byte ghi bằng số byte hợp lệ | Sai position/limit |
| Đọc file bằng buffer nhỏ hơn file | Lặp đọc cho tới EOF, không mất byte | Giả định một lần read lấy hết dữ liệu |
| Parser nhận nửa frame ở một lần đọc | `compact` giữ byte chưa tiêu thụ | Mất frame ở biên buffer |
| Output có yêu cầu bền vững khi crash | Trình tự flush → force/sync → commit được định nghĩa và đo kiểm theo filesystem | Nhầm visibility với durability |

**Không dùng test “file size vẫn bằng 0 ngay sau write chưa flush” làm bảo đảm tuyệt đối**: tùy kích thước dữ liệu, buffer và implementation, writer có thể tự flush khi đầy. Test nên chứng minh kết quả sau explicit flush/close, không đòi hỏi dữ liệu trước flush luôn vô hình.

Với unit test parser, có thể dùng `ByteBuffer.allocate(4)` để ép chia frame qua nhiều lần đọc. Với integration test resource, dùng tệp tạm và đo descriptor ở nơi có thể; tránh dựa duy nhất vào việc xóa file thành công trên Unix vì file còn mở có thể vẫn unlink được.

## 10. Phân biệt ba loại triệu chứng trong thực tế

| Triệu chứng | Khả năng cần kiểm tra đầu tiên | Hướng xử lý |
| --- | --- | --- |
| File rỗng trong khi writer còn mở, nhưng đúng sau close | Buffered output chưa flush | Flush tại ranh giới cần visibility |
| File đúng sau flush nhưng không sống sót qua sự cố điện/OS | Thiếu yêu cầu durability hoặc giới hạn filesystem/storage | Xem `FileChannel.force`/`FileDescriptor.sync` và protocol lưu bền vững |
| `Too many open files` | Stream/channel không close; worker ownership không rõ | Try-with-resources, kiểm tra nhánh lỗi, đo descriptor |
| Trên Windows không rename/delete được file | Handle/lock còn mở hoặc quyền chia sẻ/OS | Tìm process giữ handle; đảm bảo close/giải phóng lock |
| NIO `write` ghi 0 byte | `!buffer.hasRemaining()` hoặc nonblocking output chưa writable | In `position/limit`, kiểm tra channel và readiness |
| File chứa byte rác hoặc số byte sai | Thiếu flip, dùng cả `array()` hoặc không xử lý partial write | Giữ state buffer và lặp ghi đúng |
| Parser thiếu dữ liệu ở cuối block | Dùng clear thay vì compact, xử lý từng khối như frame độc lập | Giữ byte pending, kiểm thử split input |
| File đúng một lần nhưng sai dưới tải | Đọc/ghi đồng thời không điều phối, shared mutable buffer, close sai thời điểm | Tách buffer theo task, khóa/sắp thứ tự, quy định ownership |

## 11. Checklist áp dụng khi review code IO

1. Tìm tất cả nơi mở tài nguyên: `Files.newBufferedWriter`, `Files.newInputStream`, `FileChannel.open`, `Files.lines/list/walk/find`, `ZipFile`, `RandomAccessFile`. Xác định ai close trên mọi đường đi.
2. Với output có buffer, xác định **khi nào bên ngoài được phép nhìn thấy dữ liệu**. Nếu trước close, đặt flush có chủ đích; nếu cần durability, thiết kế bước force/sync sau khi dữ liệu đã xuống tầng file.
3. Không tuyên bố success trước khi thao tác ghi và đóng/commit hoàn thành; thu thập lỗi write/flush/close. Với `PrintWriter`, kiểm tra `checkError()` hoặc dùng API báo exception.
4. Với NIO, ghi rõ trạng thái buffer trước/sau `put/read/flip/write/clear/compact`. Dùng `remaining` cho byte hợp lệ, lặp khi channel có partial read/write.
5. Đặt thử nghiệm chạy lặp và nhánh lỗi để bắt handle leak; test trên nền tảng sẽ triển khai, đặc biệt nếu kết quả phụ thuộc Windows file sharing, filesystem từ xa hoặc lưu trữ đám mây.
6. Giữ resource scope tối thiểu: không giữ channel/stream xuyên qua lời gọi mạng hay task bất đồng bộ không cần thiết. Với worker bất đồng bộ, không đóng nguồn khi công việc còn cần dùng.

Điều cần nhớ: **`flush` xử lý byte đang đệm ở lớp Java; `close` kết thúc quyền sở hữu tài nguyên; `ByteBuffer.flip/clear/compact` xử lý trạng thái vị trí đọc/ghi; `force/sync` giải quyết yêu cầu đồng bộ xuống storage.** Chúng không thay thế nhau.

### Tài liệu tham khảo chính thức

- Java SE 17 `BufferedWriter`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html
- Java SE 17 `Writer` và `OutputStream` (flush/close): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Writer.html
- Java SE 17 `ByteBuffer` / `Buffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 `FileChannel` (write/force/close): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 17 `Files` (resource management): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="testing-and-diagnostics-011">Kiểm tra event, callback và thao tác đồng thời theo cách xác định</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra event, callback và thao tác đồng thời theo cách xác định trong Java I/O

## 1. Vấn đề: I/O đúng nhưng bài test thất thường

Trong các chương trình Java I/O, nhiều sự kiện không xảy ra đúng một thời điểm cố định: `WatchService` nhận thay đổi thư mục, `CompletionHandler` báo hoàn tất đọc/ghi, thread pool chạy các thao tác tệp, hoặc callback tiếp tục công việc sau khi kênh hoàn thành một phần.

Một bài kiểm thử kiểu này dễ trở thành **flaky test**: cùng mã, cùng input nhưng có lúc pass, có lúc fail do tốc độ máy, lịch chạy thread, cache, hệ điều hành hay cơ chế của file system. Chẳng hạn:

~~~java
startAsyncCopy();
Thread.sleep(100);
assertOutputExists(); // Sai: 100 ms không chứng minh copy đã xong.
~~~

Đây là **mã minh họa lỗi**, không phải chương trình độc lập. Máy phát triển có thể hoàn thành nhanh hơn 100 ms, trong khi máy CI đang tải nặng có thể cần nhiều thời gian. Tăng lên 2 giây chỉ làm test chậm hơn chứ không xây dựng được mối quan hệ đúng về mặt nhân quả.

**Kiểm thử theo cách xác định (deterministic testing)** nghĩa là kiểm tra **trạng thái và điều kiện hoàn thành được xác định rõ**, điều khiển những điểm đồng bộ cần thiết, không lấy khoảng thời gian chờ làm bằng chứng công việc đã hoàn tất. Phải phân biệt:

- **Thứ tự logic mà bài test có quyền kiểm soát:** tạo input trước, đăng ký callback, đợi một tín hiệu hoàn thành, sau đó kiểm tra kết quả.
- **Thứ tự thực thi mà scheduler/OS quyết định:** thread nào chạy trước, callback vào lúc nào, số event được gộp ra sao. Đây thường **không** phải thứ tự nên assert cứng.
- **Sự đúng đắn của trạng thái cuối:** dữ liệu đọc/ghi đúng byte, mỗi request báo một kết quả kết thúc, không có dữ liệu ngoài phạm vi.
- **Tính chất cạnh tranh:** bài test có kiểm soát để tạo ra interleaving quan trọng và kiểm tra ứng dụng vẫn đúng khi các thao tác thực sự có thể chạy đồng thời.

Sau bài này, bạn sẽ dùng được `CountDownLatch`, `Future`, `CompletableFuture`, `CompletionHandler`, timeout hữu hạn, root tạm riêng và chiến lược kiểm tra sự kiện `WatchService` một cách đáng tin hơn.

## 2. Thế nào là một assertion xác định?

Câu hỏi đúng khi kiểm tra I/O bất đồng bộ không phải “Sau 100 ms có kết quả chưa?” mà là “**Điều kiện nào chứng minh thao tác đã hoàn thành?**”.

| Tình huống | Assertion dễ flaky | Cách kiểm tra vững hơn |
| --- | --- | --- |
| Ghi file bất đồng bộ | Ngủ 100 ms rồi `Files.exists` | Đợi callback/`Future` báo hoàn tất, sau đó đọc kiểm chứng nội dung |
| Nhận một callback | `sleep` rồi so bộ đếm | `CompletableFuture.get(timeout)` hoặc `CountDownLatch.await(timeout)` |
| Hai worker cùng chạy | Đợi vài ms rồi tin là song song | Dùng latch/barrier xác nhận **cả hai đã sẵn sàng** trước khi cho tiếp tục |
| `WatchService` báo tạo file | Cứng nhắc assert đúng 1 `ENTRY_CREATE` trên mọi OS | Test parser bằng event giả lập; integration test kết hợp snapshot trạng thái và quan sát event không bắt buộc |
| Xử lý dữ liệu trên socket | Một lần `read` phải ra trọn message | Dùng framing, lặp đọc đến đủ byte và đợi completion |
| Kiểm tra thứ tự hoàn thành | Callback A phải luôn trước B | Nếu yêu cầu nghiệp vụ có thứ tự, dùng sequence/id và điều phối; nếu không, assert tập kết quả |
| Kiểm thử timeout | Đợi vô hạn để xem có treo không | Deadline hữu hạn, báo rõ thời điểm thất bại và cleanup |
| Kiểm tra exception | Lỗi ở worker được tự ném vào thread test | Thu lỗi qua `Future.get`, completion hoặc bộ thu lỗi của test |

**Tính xác định không đồng nghĩa mọi thread chạy theo cùng lịch mỗi lần.** Ta cần làm cho điều kiện *pass/fail* không phụ thuộc sự may rủi của lịch chạy, hoặc chủ động điều khiển những điểm đua cần kiểm tra.

## 3. Nguyên tắc thiết kế để tránh flaky test

1. **Một scenario, một phạm vi tài nguyên riêng:** tạo thư mục tạm cho từng bài test; không dùng file chung cố định.
2. **Thiết lập trạng thái đầu rõ ràng:** khởi tạo file/queue/buffer trước khi phát sự kiện hoặc khởi chạy tác vụ.
3. **Sự kiện kết thúc có tên:** ví dụ `CompletableFuture<Void> saved` đại diện “đã lưu xong”, không phải “đã gọi phương thức lưu”.
4. **Chờ có giới hạn:** `get(5, TimeUnit.SECONDS)` hoặc `await(5, TimeUnit.SECONDS)`; nếu hết hạn, test fail và dọn tài nguyên.
5. **Chỉ dùng assertion trên dữ liệu đã đồng bộ:** không đọc `ByteBuffer` lúc I/O còn pending, không đọc file trước khi writer xác nhận xong nếu giao thức yêu cầu.
6. **Kiểm tra số byte thực tế:** một callback `completed(n, ...)` chỉ có thể báo số byte của **một lần** đọc/ghi, không chắc đủ toàn bộ request.
7. **Tách unit test logic event khỏi khả năng phát event native:** logic do ta kiểm soát có thể kiểm thử xác định; delivery từ `WatchService`/OS có giới hạn nền tảng.
8. **Đảm bảo trạng thái kết thúc trên đường lỗi:** lỗi callback phải hoàn tất future theo kiểu thất bại, không chỉ ghi log rồi để caller đợi mãi.
9. **Đóng thread pool, channel, WatchService và root tạm:** tránh tài nguyên tồn đọng làm sai kết quả test sau.
10. **Quan sát đủ thông tin khi fail:** lưu request ID, offset, byte count, trạng thái barrier, cause và thời gian chờ; không chỉ viết “assert failed”.

Không phải mọi lỗi race xuất hiện chỉ sau một lần chạy. Phối hợp test có thứ tự điều khiển, test stress, instrumentation và kiểm tra invariants cho các lịch chạy khác nhau.

## 4. `CountDownLatch`: điều khiển “bắt đầu cùng pha” thay vì ngủ

`CountDownLatch` là bộ đếm giảm dần về 0; một hoặc nhiều thread có thể `await()` đến khi đủ số lần `countDown()`. Khi counter về 0, những thread đang chờ được cho tiếp tục.

Giả sử có hai worker:

~~~java
CountDownLatch ready = new CountDownLatch(2);
CountDownLatch go = new CountDownLatch(1);

// Mỗi worker:
ready.countDown();
if (!go.await(5, TimeUnit.SECONDS)) {
    throw new IllegalStateException("Start signal timed out");
}
// Bắt đầu phần I/O cần kiểm tra.

// Thread kiểm thử:
if (!ready.await(5, TimeUnit.SECONDS)) {
    throw new AssertionError("Workers not ready");
}
go.countDown();
~~~

Đây là **đoạn minh họa**, cần import `CountDownLatch` và `TimeUnit`; phải khởi chạy worker thật từ thread pool. `ready` cho biết hai worker đã tới điểm đồng bộ; `go` giải phóng chúng sau khi setup hoàn tất.

**Quan trọng:** một latch không ép CPU chạy hai worker đúng cùng một nanosecond. Nó tạo ra một ranh giới bắt đầu có chủ đích, giúp cả hai *có khả năng cạnh tranh* sau cùng một tín hiệu, và không phụ thuộc `Thread.sleep`.

Để tránh treo khi một worker không bao giờ tới barrier:

- Mọi `await` của test nên có timeout.
- Nếu `ready.await` thất bại, vẫn nên giải phóng `go` hoặc hủy worker để chúng không bị bỏ lại mãi.
- Dùng thread pool có **đủ worker**: nếu pool chỉ có một thread nhưng yêu cầu hai task cùng tới latch, có thể tự tạo deadlock/starvation.
- Thu tất cả kết quả qua `Future.get(timeout)`; exception bên worker không tự nổi lên ở luồng test.

`CountDownLatch` dùng một lần. Nếu cần các vòng đồng bộ lặp lại, cân nhắc `CyclicBarrier` hoặc `Phaser`; phải hiểu cách xử lý broken barrier và timeout.

## 5. `Future` và `CompletableFuture`: đợi kết quả, không đoán trạng thái

Một `Future<T>` cho phép lấy kết quả hoặc nguyên nhân lỗi:

~~~java
Future<String> work = executor.submit(() -> "done");
String result = work.get(5, TimeUnit.SECONDS);
if (!"done".equals(result)) {
    throw new AssertionError("Unexpected result");
}
~~~

Đây là **đoạn minh họa** dùng `ExecutorService executor` đã tạo; cần import `Future` và `TimeUnit`.

`get(timeout, unit)` tạo giới hạn thời gian cho thread test. `TimeoutException` báo rằng việc **chờ** đã hết thời gian, không nhất thiết worker đã dừng. `Future.cancel(true)` có thể yêu cầu hủy, nhưng thread bị interrupt không bắt buộc kết thúc ngay nếu tác vụ không hợp tác.

`CompletableFuture<T>` phù hợp khi cần kết nối callback vào một tín hiệu hoàn tất có thể đợi:

~~~java
CompletableFuture<Integer> finished = new CompletableFuture<>();

// Callback thành công:
finished.complete(actualBytes);

// Callback thất bại:
finished.completeExceptionally(error);

// Thread test:
int actualBytes = finished.get(5, TimeUnit.SECONDS);
~~~

Đây là **đoạn minh họa**, cần `CompletableFuture` và `TimeUnit`; biến `error` chỉ nguyên nhân lỗi. `CompletableFuture` có khả năng hoàn tất chỉ một lần: những lời gọi `complete` hoặc `completeExceptionally` đến sau không thay thế kết quả đã được công bố thành công.

**Lưu ý visibility:** việc nhận kết quả từ `Future.get`/`CompletableFuture.get` cung cấp điểm đồng bộ hữu ích để đọc trạng thái đã được tác vụ công bố. Nhưng nếu những thread khác vẫn còn sửa chung một buffer hoặc collection, không được suy ra rằng toàn bộ hệ thống đã yên; phải xác định ownership và phạm vi hoàn tất.

## 6. Kiểm tra `CompletionHandler` cho I/O bất đồng bộ

Java NIO.2 dùng các callback:

~~~java
channel.read(buffer, fileOffset, attachment,
        new CompletionHandler<Integer, RequestState>() {
            @Override
            public void completed(
                    Integer n, RequestState state) {
                // Một lần đọc hoàn tất: xử lý n và điều kiện đọc tiếp.
            }

            @Override
            public void failed(
                    Throwable error, RequestState state) {
                // Hoàn tất toàn bộ yêu cầu bằng lỗi.
            }
        });
~~~

Đây là **đoạn minh họa API**, không phải chương trình độc lập. `channel` là `AsynchronousFileChannel`, `buffer` là `ByteBuffer`, `fileOffset` là `long`; `RequestState` là kiểu context của ứng dụng.

### 6.1. Đừng đánh đồng một callback với toàn bộ request

Ví dụ mục tiêu là đọc đủ 5 byte từ file, nhưng callback đầu tiên trả 2 byte:

- `n = 2` nghĩa là đã đọc được 2 byte trong lần gọi đó.
- `ByteBuffer.position()` đã tăng 2, **không tự tăng lần thứ hai**.
- Nếu buffer còn chỗ, khởi phát lần đọc tiếp với offset tệp tăng 2.
- Chỉ hoàn tất future bao trùm cả request khi đã có đủ byte hoặc chính sách cho phép EOF sớm.

Khi `n == -1` trên thao tác đọc, đã gặp EOF. Nếu request yêu cầu đủ số byte còn thiếu, báo lỗi `EOFException`. Với `n == 0`, phân biệt buffer hết chỗ, provider đặc biệt hoặc điều kiện không tiến triển; ví dụ kiểm thử tệp ổn định có thể chủ động báo lỗi thay vì retry nóng.

### 6.2. Test cả đường thành công và thất bại

Với logic callback do bạn sở hữu, nên kiểm tra:

- Callback `completed` được xử lý khi có số byte dương.
- Callback `failed` làm request kết thúc theo nhánh lỗi và vẫn có cleanup.
- Khi hoàn thành một phần, code khởi phát thêm thao tác hoặc tích lũy byte đúng.
- Một callback kết thúc muộn hoặc một sự kiện duplicate **không** gây xử lý nghiệp vụ lặp.
- Callback lỗi không bị nuốt, và thread test đọc được nguyên nhân qua future.

**Không tự gọi callback thực của channel một cách thủ công rồi coi đó là kiểm chứng kernel async I/O.** Với unit test logic, bạn có thể tách riêng adapter `readAsync` hoặc inject một fake completion source rồi gọi handler một cách kiểm soát. Với integration test, dùng channel thật và đợi bằng future/latch, nhưng không assert thread name hoặc thời điểm callback tuyệt đối.

## 7. `WatchService`: event không phải trạng thái đích

`WatchService` thông báo những thay đổi do provider phát hiện trên các thư mục đã đăng ký. Các event thường dùng là `ENTRY_CREATE`, `ENTRY_MODIFY` và `ENTRY_DELETE`. `OVERFLOW` báo có thể đã bỏ lỡ sự kiện.

Một số hành vi **không nên assert giống nhau trên mọi hệ điều hành/file system**:

- Một lần ghi tạo đúng một event.
- Event modify phải đến sau create với một số lượng/timing cố định.
- Một thay đổi từ máy khác trên NFS/SMB phải luôn đến.
- Một file vừa tạo đã được ghi hoàn tất khi nhận create.
- Một event `ENTRY_MODIFY` luôn có nghĩa nội dung đã thay đổi có ý nghĩa nghiệp vụ.

Do đó, chia kiểm thử thành hai loại:

| Loại kiểm thử | Mục tiêu | Assertion thích hợp |
| --- | --- | --- |
| **Unit test parser/reconciler** | Kiểm tra code xử lý một tập event đã biết | Cấp event giả lập có thứ tự, duplicate, `OVERFLOW`, key invalid; assert trạng thái/kết quả |
| **Integration test trên file system thật** | Kiểm tra API/provider có thể đăng ký và phát hiện thay đổi trong môi trường mục tiêu | Dùng deadline hợp lý, ghi lại event có/không, luôn kiểm tra snapshot file; chỉ assert khả năng delivery nếu môi trường đã định nghĩa rõ là điều kiện bắt buộc |
| **Test độ tin cậy NFS/SMB** | Đánh giá thay đổi do máy khác thực hiện | Thiết kế kịch bản từ hai máy, đo tỷ lệ phát hiện và latency, có đối chiếu quét directory |
| **Test recovery khi OVERFLOW** | Kiểm tra chính sách full rescan khi mất event | Inject tín hiệu overflow vào thành phần ứng dụng; không cần cố gây quá tải native để có overflow thật |

Nếu bài học có một file `marker.txt` được tạo sau khi đăng ký watcher, test portable **có thể** xác nhận file thực sự tồn tại và đúng nội dung, còn việc nhận event chỉ là quan sát. Đó là một test xác định cho **snapshot correctness**, không phải chứng minh watcher đã giao sự kiện.

### 7.1. Một vòng lấy sự kiện có giới hạn

~~~java
WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
if (key != null) {
    for (WatchEvent<?> event : key.pollEvents()) {
        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
            requestFullRescan();
            continue;
        }
        // Đánh dấu đường dẫn dirty, sau đó kiểm tra trạng thái thật.
    }
    if (!key.reset()) {
        // Registration đã invalid; chuyển sang recovery.
    }
}
~~~

Đây là đoạn **minh họa** với `watchService` đang mở và `requestFullRescan()` do ứng dụng tự định nghĩa. `poll(timeout) == null` chỉ có nghĩa **chưa có key được báo** trong thời gian chờ; không kết luận directory không thay đổi. `event.context()` của sự kiện entry cần được xử lý như tên tương đối, xác thực trước khi truy cập path.

### 7.2. Giảm flaky bằng reconciliation

Khi event đến, xử lý nó như một hint “path có thể dirty”. Reconciler sẽ quét và đọc trạng thái file thực tế. Khi test, assert dữ liệu có đúng phiên bản sau khi reconciler báo hoàn tất; đừng assert số event.

Để test idempotency, gửi cùng một event `ENTRY_MODIFY` hai lần và kiểm tra nghiệp vụ chỉ tạo một kết quả cuối hoặc dedup bằng version/id. Để test overflow, đưa vào một tín hiệu `OVERFLOW` rồi assert một lượt full scan được lên lịch, không khẳng định OS sẽ tự sinh overflow đúng thời điểm.

## 8. Kiểm thử concurrency: invariant quan trọng hơn lịch chạy

Giả sử hai tác vụ ghi các file khác nhau trong thư mục riêng. Bài test có thể cần đảm bảo:

1. Hai tác vụ đều **đã tới điểm sẵn sàng** trước khi bắt đầu giai đoạn cần kiểm tra.
2. Cả hai được mở khóa bởi cùng một tín hiệu điều phối.
3. Mỗi tác vụ ghi đúng file của nó; không ghi nhầm sang file của tác vụ kia.
4. Mỗi kết quả nghiệp vụ chỉ được công bố sau khi tác vụ đó đã hoàn thành.
5. Kết quả được lấy qua `Future.get`, để lỗi worker được chuyển về thread kiểm thử.
6. Nếu có timeout hoặc failure, executor không bị bỏ chạy vô thời hạn.

**Đừng assert “task A phải hoàn thành trước B”** trừ khi chương trình thật có hợp đồng yêu cầu điều đó. Sau `go.countDown()`, việc A hoặc B được OS chạy trước là không xác định.

### Cách so sánh kết quả không phụ thuộc thứ tự

Nếu tác vụ có id, thu kết quả vào `Map<Id, Result>` rồi so theo id; không dựa vào thứ tự arrival. Nếu muốn kiểm tra “mỗi id xử lý đúng một lần”, dùng bảng trạng thái và điều kiện atomic/idempotent thay vì đọc một biến đếm `int` đang bị nhiều thread sửa mà không đồng bộ.

Khi kiểm thử data race thật sự, đôi khi cần ép interleaving hiếm bằng hook/barrier **đặt ở vị trí logic cụ thể**. Ví dụ, cả hai worker đọc version cũ trước khi cùng cố ghi, qua đó kiểm tra compare-and-set hoặc transaction. Chỉ tăng số thread/stress không đảm bảo kích hoạt đúng race.

## 9. Ví dụ Java 17+ độc lập: event, callback và hai thao tác được điều phối

Ví dụ sau chạy ba kiểm tra trong một chương trình không cần thư viện ngoài:

- **Event:** đăng ký `WatchService` cho một thư mục tạm, tạo file `marker.txt`, chờ tối đa một khoảng ngắn để quan sát event. Event có thể đến hoặc không; kết quả pass của phần này xác nhận **snapshot nội dung**.
- **Callback:** đọc đầy đủ 5 byte `ready` qua `AsynchronousFileChannel.read(..., CompletionHandler)` và nối việc hoàn tất vào `CompletableFuture`.
- **Concurrency:** đưa hai tác vụ vào pool có 2 thread, dùng `ready` và `start` latch để chắc chắn cả hai đã đến điểm xuất phát trước khi phát tín hiệu. Mỗi tác vụ tạo một thư mục tạm riêng và ghi một file có cùng tên tương đối.

**Tệp `DeterministicIoTestDemo.java`:**

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DeterministicIoTestDemo {
    private static final int WAIT_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("deterministic-io-");
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Path watched = Files.createDirectory(root.resolve("watched"));
            Path marker = watched.resolve("marker.txt");

            // Event được quan sát nhưng không quyết định test pass/fail.
            try (WatchService watch =
                         FileSystems.getDefault().newWatchService()) {
                watched.register(
                        watch,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                Files.writeString(
                        marker, "ready", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);

                WatchKey key = watch.poll(1, TimeUnit.SECONDS);
                boolean sawCreate = false;
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind()
                                == StandardWatchEventKinds.ENTRY_CREATE
                                && marker.getFileName()
                                        .equals(event.context())) {
                            sawCreate = true;
                        }
                    }
                    key.reset();
                }

                // Biến được dùng trong logic diagnostic, không dùng làm
                // tiêu chí pass: provider có thể không phát event.
                if (sawCreate) {
                    // Ở ứng dụng thật có thể cập nhật metrics.
                }
            }

            boolean snapshotOk = "ready".equals(
                    Files.readString(marker, StandardCharsets.UTF_8));
            if (!snapshotOk) {
                throw new AssertionError("File snapshot mismatch");
            }
            System.out.println("Snapshot verified: true");
            System.out.println("Watch event required for pass: false");

            int callbackBytes = readFullyWithHandler(marker);
            System.out.println("Callback bytes read: " + callbackBytes);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Future<String> alpha = pool.submit(
                    () -> runScenario(root, "alpha", "ONE", ready, start));
            Future<String> beta = pool.submit(
                    () -> runScenario(root, "beta", "TWO", ready, start));

            boolean bothReady = ready.await(
                    WAIT_SECONDS, TimeUnit.SECONDS);
            start.countDown(); // Giải phóng cả hai kể cả khi lỗi setup.

            if (!bothReady) {
                throw new IllegalStateException("Workers did not get ready");
            }

            // Đọc theo id cố định: không phụ thuộc task nào xong trước.
            System.out.println("alpha result: "
                    + alpha.get(WAIT_SECONDS, TimeUnit.SECONDS));
            System.out.println("beta result: "
                    + beta.get(WAIT_SECONDS, TimeUnit.SECONDS));
            System.out.println("All checks passed");
        } finally {
            pool.shutdownNow();
            // Đợi worker dừng trước khi xóa dữ liệu đang được dùng.
            boolean stopped =
                    pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            if (stopped) {
                deleteTree(root);
            } else {
                throw new IllegalStateException(
                        "Executor did not terminate; temp root retained");
            }
        }
    }

    private static int readFullyWithHandler(Path file)
            throws Exception {
        try (AsynchronousFileChannel channel =
                     AsynchronousFileChannel.open(
                             file, StandardOpenOption.READ)) {
            ByteBuffer data = ByteBuffer.allocate(5);
            CompletableFuture<Integer> finished =
                    new CompletableFuture<>();

            CompletionHandler<Integer, Long> handler =
                    new CompletionHandler<>() {
                private int total;

                @Override
                public void completed(Integer n, Long offset) {
                    if (n == -1) {
                        finished.completeExceptionally(
                                new EOFException("Incomplete payload"));
                        return;
                    }
                    if (n == 0) {
                        finished.completeExceptionally(
                                new IOException("Read made no progress"));
                        return;
                    }

                    total += n;
                    if (!data.hasRemaining()) {
                        finished.complete(total);
                        return;
                    }
                    long next = offset + n;
                    try {
                        channel.read(data, next, next, this);
                    } catch (RuntimeException error) {
                        finished.completeExceptionally(error);
                    }
                }

                @Override
                public void failed(Throwable error, Long offset) {
                    finished.completeExceptionally(error);
                }
            };

            channel.read(data, 0L, 0L, handler);
            int total = finished.get(WAIT_SECONDS, TimeUnit.SECONDS);
            data.flip();
            if (!"ready".equals(
                    StandardCharsets.UTF_8.decode(data).toString())) {
                throw new AssertionError("Callback payload mismatch");
            }
            return total;
        }
    }

    private static String runScenario(
            Path parent, String name, String payload,
            CountDownLatch ready, CountDownLatch start)
            throws Exception {
        Path ownRoot = Files.createTempDirectory(parent, name + "-");
        ready.countDown();

        if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start signal timed out");
        }

        Path file = ownRoot.resolve("same-name.txt");
        Files.writeString(
                file, payload, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);

        String actual = Files.readString(file, StandardCharsets.UTF_8);
        if (!payload.equals(actual)) {
            throw new AssertionError("Unexpected concurrent write");
        }
        return actual;
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(
                    Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(
                    Path dir, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 DeterministicIoTestDemo.java
java DeterministicIoTestDemo
~~~

**Stdout kỳ vọng khi provider hỗ trợ WatchService, file I/O hoạt động và các tác vụ hoàn tất trong deadline:**

~~~text
Snapshot verified: true
Watch event required for pass: false
Callback bytes read: 5
alpha result: ONE
beta result: TWO
All checks passed
~~~

### Giải thích những điểm quyết định tính đúng

1. `Files.createTempDirectory` cấp vùng riêng cho toàn bộ ví dụ. Hai task `alpha` và `beta` còn tạo hai thư mục con riêng, nên cùng ghi tên `same-name.txt` mà không xung đột.
2. Đăng ký watcher **trước** khi tạo `marker.txt`. `watch.poll(1, SECONDS)` là quan sát có deadline, không phải sự kiện bắt buộc để test snapshot pass. `key.pollEvents()` và `key.reset()` được gọi khi nhận key.
3. `sawCreate` chỉ là dữ liệu diagnostic, không xuất hiện trong stdout để giữ kết quả kỳ vọng ổn định. Trong test theo môi trường cụ thể, bạn có thể ghi nó vào log hoặc metric để thống kê tỷ lệ delivery.
4. Assertion về sự tồn tại/nội dung thật được thực hiện bằng `Files.readString`. Bài test không phụ thuộc vào số lần `ENTRY_MODIFY` hoặc lịch callback của OS.
5. `AsynchronousFileChannel` đọc theo offset tường minh. Một callback nhận `n` byte thì `ByteBuffer.position()` đã tiến `n`; nếu còn chỗ, code tiếp tục ở offset `offset + n`. Chỉ khi đủ 5 byte mới `finished.complete(total)`.
6. `CompletableFuture.get(5, SECONDS)` là điểm đồng bộ. Sau đó chương trình mới `flip` buffer và giải mã đúng chuỗi `ready`. Các lỗi I/O hoàn tất `finished` theo nhánh exception.
7. `pool` có **hai worker**, phù hợp hai task cùng đợi ở điểm `start`. `ready.await` chứng minh cả hai đã đến mốc đồng bộ trước khi luồng chính giải phóng `start`.
8. Nếu setup một worker không tới mốc đúng hạn, `start.countDown()` vẫn được gọi trước khi test báo lỗi. Điều này tránh để worker khác kẹt vô thời hạn ở latch.
9. `alpha.get` và `beta.get` có timeout và được in theo **thứ tự định danh cố định**, không phải thứ tự thread thực tế hoàn thành.
10. `finally` gọi `shutdownNow()` rồi `awaitTermination` để giảm nguy cơ xóa file khi worker còn truy cập. Nếu worker không dừng đúng hạn, ví dụ cố ý giữ root lại và báo lỗi thay vì xóa một cây đang được sử dụng.
11. `Files.walkFileTree` xóa file trước thư mục, không theo symbolic link theo mặc định. Không dùng lệnh xóa đệ quy trên đường dẫn từ bên ngoài.

**Giới hạn minh bạch:** đây là integration demo đơn giản, không phải một công cụ bảo đảm mọi race trong production đều được phát hiện. Timeout 5 giây là lựa chọn cho demo; máy CI tải nặng có thể vượt deadline và báo lỗi dù thuật toán không sai. Test suite thực tế cần deadline theo SLA môi trường, tài nguyên test phù hợp và quan sát rõ khi timeout. `finally` trong ví dụ có thể báo lỗi cleanup/termination thay thế lỗi trước đó; framework thực tế nên giữ lỗi gốc và gắn cleanup error làm suppressed exception.

## 10. Kiểm thử logic event/callback bằng fake để điều khiển thứ tự

Để unit test có thể **chủ động sinh event A, B, A trùng lặp, rồi OVERFLOW**, đừng chờ OS tạo đúng lịch đó. Tách logic ứng dụng khỏi adapter nhận event.

Ví dụ giao diện:

~~~java
interface EventSink {
    void onCreated(Path relativePath);
    void onModified(Path relativePath);
    void onOverflow();
}
~~~

Đây là **API minh họa**, với import `Path`. Production adapter `WatchService` chuyển `WatchEvent` thành lời gọi trên `EventSink`; unit test có thể tự gọi:

~~~java
sink.onCreated(Path.of("input.txt"));
sink.onModified(Path.of("input.txt"));
sink.onModified(Path.of("input.txt")); // Event lặp.
sink.onOverflow();
~~~

Đây là **đoạn minh họa**, giả định `sink` là đối tượng test đã khởi tạo; không độc lập. Assertions nên kiểm tra:

- `input.txt` chỉ được đưa vào danh sách “dirty” đúng một lần nếu ứng dụng có cơ chế dedup.
- `onOverflow()` đánh dấu cần full reconciliation; các event sau đó không làm mất yêu cầu rescan.
- Consumer thực thi parse/IO khi snapshot sẵn sàng; event giả không tự biến thành file vật lý.
- Khi nội dung hiện tại không đổi, xử lý lại event không sinh tác dụng phụ ngoài ý muốn (idempotency).

Tương tự với `CompletionHandler`, tách code xử lý kết quả sang thành phần có thể inject một completion source. Unit test có thể phát `completed(2)` rồi `completed(3)`, hoặc `failed(IOException)`, mà không cần ép file system thật luôn tạo partial read đúng hai lượt.

**Không dùng fake để thay thế hoàn toàn integration test.** Fake kiểm tra đúng logic của bạn; bài kiểm thử riêng phải xác nhận adapter hoạt động với thư viện/JDK/provider thật trong môi trường triển khai.

## 11. Race và thứ tự sự kiện: kiểm thử điều thực sự phải đúng

### 11.1. Race đọc/ghi chung một file

Hai thread cùng ghi vào một file hoặc một `ByteBuffer` mutable không được đồng bộ có thể tạo kết quả phụ thuộc lịch. Để kiểm thử một chiến lược đồng bộ, hãy định nghĩa invariant rõ:

- Mỗi record có một sequence ID duy nhất.
- Nếu writer công bố version mới, reader không thấy record bị cắt nửa.
- Không có duplicate commit với cùng request ID.
- Không đọc buffer trước khi thao tác I/O đã hoàn tất.
- Không có file tạm nào được công bố như bản hoàn chỉnh sau khi lỗi.

Đặt latch/barrier ngay quanh đoạn tranh chấp mà bạn muốn test; ví dụ hai worker cùng thấy phiên bản cũ rồi cùng thử compare-and-set. Sau khi barrier mở, chỉ một commit hợp lệ được chấp nhận. Không assert thread tên `pool-1-thread-1` phải thắng.

### 11.2. Lost update và visibility

Một biến `int count` được hai thread cùng thực hiện `count++` có thể bị **lost update** do thao tác không nguyên tử. Nếu test cần kiểm tra số lần callback, dùng `AtomicInteger` hoặc công cụ đồng bộ thích hợp thay vì đọc/ghi biến trần không an toàn.

Khi test chỉ chờ `CountDownLatch.await` thành công, các hành động trước `countDown` của worker có quan hệ happens-before với các hành động sau `await` thành công trong thread chờ theo hợp đồng concurrency. Tuy nhiên, các hành động **sau** khi worker gọi `countDown` chưa chắc đã hoàn tất khi waiter được mở khóa; hãy đặt tín hiệu sau khi trạng thái cần công bố thực sự xong.

### 11.3. Callback được gọi lại nhiều lần

Nếu một adapter hoặc tầng ứng dụng có khả năng phát tín hiệu duplicate, việc `CompletableFuture.complete` trả `false` khi future đã hoàn tất có thể là điểm bắt đầu để tránh công bố kết quả hai lần. Tuy vậy, **future hoàn tất một lần không tự bảo vệ side effect trước lời gọi `complete`**. Nếu callback đã ghi database hai lần rồi mới gọi `complete`, duplicate vẫn có thể xảy ra. Phải đặt idempotency/dedup trước side effect nghiệp vụ.

## 12. Trường hợp cần kiểm thử timeout, hủy và lỗi

Với mọi thao tác bất đồng bộ, test nên có đường lỗi độc lập:

| Tình huống | Cách kích hoạt kiểm soát | Điều phải kiểm chứng |
| --- | --- | --- |
| Callback thành công | Fake handler phát `completed(n)` hoặc I/O file thật | Có kết quả đúng và đủ byte |
| Callback thất bại | Fake handler gọi `failed(exception)` | Future của yêu cầu hoàn tất lỗi, không đợi mãi |
| Partial read | Fake trả lần đầu 2 byte, lần sau phần dư | Offset/buffer được cập nhật đúng, không xử lý nội dung thiếu |
| EOF sớm | Dùng file ngắn hơn kích thước yêu cầu | Báo lỗi thiếu dữ liệu, không coi thành công |
| Timeout chờ | Delay có điều khiển bằng latch/future chưa hoàn tất | `get(timeout)` ném timeout, có chính sách hủy/cleanup |
| Task failure | `Callable` chủ động ném exception | `Future.get` trả `ExecutionException` chứa cause |
| Concurrent start | Hai worker cùng đến `ready` rồi nhận `go` | Không xung đột file và invariants đúng |
| Watch event duplicate/overflow | Inject fake event source | Dedup và full rescan theo thiết kế |
| Watch native không phát event | Kịch bản trên provider hỗ trợ bất định | Snapshot vẫn được reconcile hoặc fallback polling |
| Worker không dừng | Fake task không hợp tác với interruption | Báo lỗi timeout, không báo “cleanup thành công” |

**Thời gian trong test** nên có hai vai trò tách biệt: (1) **deadline bảo vệ** để không treo vô hạn; (2) **thời gian nghiệp vụ** cần điều khiển bằng fake clock/ticker nếu logic phụ thuộc vào “sau 10 phút”. Không dùng `Thread.sleep(10 * 60 * 1000)` để test thời gian nghiệp vụ.

Với `ScheduledExecutorService`, kiểm thử logic timer có thể inject scheduler/clock giả để chủ động chạy task đến hạn. Nếu chỉ có integration test scheduler thật, kiểm tra với khoảng dư và deadline hợp lý; không đòi scheduler chạy đúng mili giây.

## 13. Tài nguyên và cleanup cũng là một phần của assertion

Kết quả dữ liệu đúng nhưng test để lại thread pool hoặc file handle có thể làm suite thất thường về sau. Do đó:

- Mỗi scenario tạo root tạm riêng; helper chỉ xóa root **do nó tạo và sở hữu**.
- Mở stream, channel, watch service bằng `try-with-resources` khi phù hợp.
- ExecutorService cần `shutdown`/`shutdownNow` và `awaitTermination`; trên Java 17 không nên dựa vào cú pháp `try (ExecutorService e = ...)`.
- Không xóa thư mục nếu tác vụ còn đang sử dụng nó. Thực hiện shutdown và xác minh đã kết thúc trước cleanup khi có race đáng kể.
- Nếu callback pending hoặc task timeout, cần quyết định hủy/đóng resource, đợi termination hoặc giữ artifact với cảnh báo rõ.
- Khi cleanup thất bại, giữ nguyên lỗi gốc bằng suppressed exception hoặc cơ chế của framework.
- Trên Windows, file/channel còn mở có thể khiến xóa file thất bại. Phải đóng chúng trước khi xóa.
- `WatchService` có thể ném `ClosedWatchServiceException` khi đóng trong lúc thread đang chờ; coi đây là nhánh vòng đời cần được kiểm soát, không đơn giản nuốt mọi exception.
- Khi root chứa symlink, `walkFileTree` mặc định không đi theo symlink; vẫn tránh cây bị tiến trình không tin cậy sửa đồng thời.

Test nên kết thúc với trạng thái rõ: mọi task hoàn thành/đã hủy, mọi future được quan sát, không còn handle bị bỏ quên, thư mục tạm đã dọn hoặc có cảnh báo vì lý do giữ lại.

## 14. Làm sao kiểm chứng tính ổn định?

Một lần pass chỉ chứng minh bài test pass **trong lần đó**. Để tăng độ tin cậy:

1. Chạy lặp lại với nhiều mức tải CPU/I/O và nhiều mức concurrency; thu tỷ lệ fail.
2. Kiểm tra cả Linux/Windows/macOS nếu ứng dụng cần portable; quan sát khác biệt `WatchService` trên file system cục bộ/NFS/SMB.
3. Giữ các assertion **không phụ thuộc độ trễ cụ thể**: có thể dùng thời hạn chờ đủ cho môi trường, không dùng “callback phải về trong 20 ms” trừ khi kiểm thử SLA.
4. Ghi trace có id, thread, offset, số byte thực tế và trạng thái barrier khi có lỗi.
5. Không dùng test lặp/stress như lý do bỏ các test kiểm soát interleaving. Stress chỉ tăng cơ hội thấy race, không đảm bảo khám phá mọi lịch chạy.
6. Xem xét test lỗi như overflow, duplicate, EOF sớm, failure callback và timeout bằng fake hoặc fault injection để không cần chờ lỗi hiếm tự xuất hiện.
7. Không cho test ảnh hưởng hệ thống bên ngoài không kiểm soát (thư mục công cộng, cổng cố định, database thật) nếu không có cô lập.

### Một ma trận QA ngắn cho dự án

| Kiểm thử | Có điều khiển? | Pass dựa trên |
| --- | --- | --- |
| Unit: parser xử lý `ENTRY_MODIFY` lặp | Có, fake event source | Chỉ một trạng thái cập nhật hợp lệ |
| Unit: callback đọc 2 + 3 byte | Có, fake completion | Buffer/offset đúng, hoàn tất đúng 5 byte |
| Integration: `AsynchronousFileChannel` | Một phần; lịch callback do provider | Future hoàn tất, byte thực tế/nội dung đúng |
| Integration: `WatchService` local | Giới hạn; delivery do provider | Snapshot đúng; event quan sát và ghi nhận |
| Integration: hai worker có start barrier | Có ở điểm xuất phát; lịch tiếp theo do OS | Hai kết quả đúng, không collision |
| Stress: nhiều worker đọc/ghi | Không cố định interleaving | Invariants và tỷ lệ lỗi qua nhiều lượt |
| End-to-end: xử lý file từ NFS/SMB | Chỉ điều khiển khâu sinh file, khó kiểm soát remote delivery | Dữ liệu cuối đúng, watcher + reconciliation, giám sát độ trễ |

## 15. Lỗi thường gặp và cách khắc phục

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| `Thread.sleep` rồi assert file tồn tại | Flaky trên máy nhanh/chậm | Chờ tín hiệu hoàn tất rồi kiểm tra |
| `Future.get()` không timeout | Test có thể treo vĩnh viễn | `get(timeout, unit)` và cleanup |
| `CountDownLatch.await()` không thời hạn | Kẹt nếu worker fail trước `countDown` | `await(timeout, unit)` |
| Pool 1 thread nhưng barrier cần 2 task | Starvation/deadlock | Pool đủ worker hoặc đổi thiết kế đồng bộ |
| Assertion số event WatchService chính xác | Khác nhau theo OS/provider | Assert trạng thái hoặc injection vào parser |
| Giả định event tới nghĩa file đã ghi xong | Có thể đọc file dở | Await producer completion hoặc protocol publish |
| Gọi callback rồi coi toàn bộ request hoàn tất | Sai khi partial I/O | Theo dõi tổng byte và offset |
| Quên `failed(...)` của CompletionHandler | Test đợi future mãi | `completeExceptionally` |
| Đọc buffer khi async read pending | Race/đọc dữ liệu thiếu | Chờ completion và kiểm soát ownership |
| Assert callback chạy trên thread tên cố định | Provider/executor khác nhau | Chỉ assert kết quả, có thể test executor riêng nếu contract yêu cầu |
| Chia sẻ một file path giữa các test parallel | Collision và false failure | Mỗi scenario có root riêng |
| Dùng biến đếm thường cho callback đa thread | Lost update/visibility lỗi | `AtomicInteger`/lock và synchronization |
| Assert thứ tự hoàn tất task không có contract | Kết quả thay đổi | So kết quả theo id hoặc tập hợp |
| Hủy Future và nghĩ side effect đã rollback | File có thể ghi một phần | Cleanup/staging và trạng thái hủy rõ |
| Cleanup trước khi worker dừng | Race, file deletion thất bại | Shutdown, await termination, sau đó xóa |
| Chỉ kiểm tra success path | Lỗi/EOF/timeout không được xử lý | Test cả failure và recovery bằng fake/injection |

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một thread gọi `Thread.sleep(500)` trước khi kiểm tra file được writer bất đồng bộ tạo. Đây có phải bằng chứng writer đã hoàn tất?

**Đáp án:** Không. Sleep chỉ làm thread chờ thời gian, không xác nhận callback, channel hoặc writer đã hoàn tất. Hãy đợi tín hiệu do writer công bố.

**Câu 2.** Vì sao cần hai latch `ready` và `start` khi chạy hai worker?

**Đáp án:** `ready` cho thread kiểm thử biết cả hai worker đã tới mốc setup; `start` cho phép giải phóng chúng sau khi mọi thứ sẵn sàng, giảm phụ thuộc scheduler trước thời điểm cạnh tranh.

**Câu 3.** `CompletableFuture.get(5, SECONDS)` timeout. Có được lập tức tái sử dụng `ByteBuffer` đang được cấp cho I/O đó?

**Đáp án:** Không. Timeout chỉ kết thúc việc đợi; thao tác I/O có thể còn chạy và đang dùng buffer. Cần đóng/hủy/đợi kết thúc theo hợp đồng.

**Câu 4.** `WatchService` phát hai `ENTRY_MODIFY` cho một file. Test có nên thất bại vì mong đúng một event?

**Đáp án:** Không, trừ khi bạn đang kiểm thử hợp đồng provider cụ thể đã xác minh điều đó. Logic ứng dụng nên chấp nhận duplicate và đối chiếu trạng thái thực.

**Câu 5.** Cần chắc chắn parser gọi full rescan khi overflow, nhưng không dễ gây overflow thật trên máy CI. Nên làm gì?

**Đáp án:** Tách parser/reconciler ra thành API nhận event và unit test bằng cách inject event `OVERFLOW`. Giữ một integration test riêng cho adapter WatchService.

**Câu 6.** Hai task chạy song song trả về đúng kết quả nhưng hoàn tất theo thứ tự khác nhau qua các lượt. Có phải test lỗi?

**Đáp án:** Không nếu nghiệp vụ không yêu cầu thứ tự. Assert kết quả theo request ID, không theo thời điểm completion.

**Câu 7.** Callback đọc 2 byte, lần tiếp 3 byte. Có nên dùng offset cũ cho lần tiếp không?

**Đáp án:** Không. Với `AsynchronousFileChannel`, phải đọc tại offset cũ + 2, đồng thời giữ `ByteBuffer.position()` đã được hệ thống cập nhật.

**Câu 8.** Một test đã pass nhưng executor không được shutdown và còn thread đang ghi file. Có thể coi test đã cô lập hoàn toàn?

**Đáp án:** Chưa. Tác vụ còn chạy có thể ảnh hưởng test sau hoặc gây leak. Chờ termination và kiểm soát cleanup là một phần của bài test đáng tin.

## 17. Ghi nhớ

- **Kiểm tra theo tín hiệu hoàn tất và invariants**, không theo thời gian ngủ ngẫu nhiên.
- **Event của WatchService là gợi ý thay đổi, không phải cam kết số lượng/thứ tự hay thời điểm file hoàn tất**; dùng fake để test logic và snapshot/reconciliation để test trạng thái.
- **Callback của NIO.2 cần xử lý partial I/O, failure và ownership buffer**; nối trạng thái yêu cầu với `CompletableFuture`/`CountDownLatch` để đợi có timeout.
- **Concurrency test cần barrier ở đúng điểm tranh chấp**, pool đủ thread và assertion không phụ thuộc scheduler.
- **Cleanup cũng phải xác định:** giữ phạm vi tài nguyên riêng, shutdown/await worker trước khi dọn, không để exception bất đồng bộ bị thất lạc.


</details>

- [Quay lại đầu trang](#back-to-top)
