<a id="back-to-top"></a>

# Luồng byte

## Menu
- [InputStream và OutputStream](#byte-streams-001)
- [read trả về int từ 0 đến 255 và dấu hiệu EOF -1](#byte-streams-002)
- [Đọc theo byte&#91;&#93;, offset và length](#byte-streams-003)
- [Một lần read không bảo đảm lấp đầy buffer](#byte-streams-004)
- [Ghi đúng số byte thực tế đã đọc](#byte-streams-005)
- [readNBytes và khác biệt với readAllBytes](#byte-streams-006)
- [skip, skipNBytes và giới hạn khi bỏ qua dữ liệu](#byte-streams-007)
- [available không phải kích thước toàn bộ dữ liệu](#byte-streams-008)
- [transferTo và quyền sở hữu hai stream](#byte-streams-009)
- [FileInputStream, FileOutputStream và chế độ append](#byte-streams-010)
- [nullInputStream, nullOutputStream và tình huống sử dụng](#byte-streams-011)

## <a id="byte-streams-001">InputStream và OutputStream</a>

<details>
<summary>Xem chi tiết</summary>

# InputStream và OutputStream trong Java: đọc và ghi luồng byte

## 1. Bức tranh tổng thể

Trong Java I/O, **luồng byte (byte stream)** là cách đọc một chuỗi byte từ **nguồn** hoặc ghi byte đến **đích** mà không buộc chương trình biết nguồn/đích được hiện thực như thế nào. Hai lớp trừu tượng cốt lõi trong gói `java.io` là:

- **`InputStream`**: đọc byte từ nguồn vào chương trình.
- **`OutputStream`**: ghi byte từ chương trình ra đích.

```text
Nguồn (tệp / bộ nhớ / kết nối ...)
              │
              ▼
         InputStream
              │ đọc byte
              ▼
          Ứng dụng
              │ ghi byte
              ▼
         OutputStream
              │
              ▼
Đích (tệp / bộ nhớ / kết nối ...)
```

Đây là **hai lớp trừu tượng**, không phải hai lớp dành riêng cho tệp. Cùng một vòng lặp sao chép byte có thể làm việc với `FileInputStream`, `ByteArrayInputStream`, hoặc một luồng được cung cấp từ API khác. Những đối tượng ấy khác nguồn dữ liệu, nhưng có chung hợp đồng `InputStream`. Tương tự với đầu ra.

**Khi nào chọn byte stream?** Khi cần giữ nguyên byte: sao chép tệp, tải dữ liệu nhị phân, xử lý ảnh, nén, định dạng nhị phân, hoặc giao thức truyền byte. Nếu mục tiêu là xử lý **ký tự/văn bản** theo encoding, ưu tiên `Reader`/`Writer` hoặc API văn bản với `Charset` xác định; byte stream tự nó không giải mã UTF-8, UTF-16 hay encoding nào.

### Một số thuật ngữ cần phân biệt

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Nguồn (source) | Nơi cung cấp byte cho `InputStream` |
| Đích (sink) | Nơi tiếp nhận byte từ `OutputStream` |
| EOF (end of stream) | Luồng đầu vào đã hết dữ liệu; `read()` trả `-1` |
| Buffer | Vùng nhớ tạm dùng để đọc/ghi theo lô, giảm số lần gọi I/O |
| Wrapper/decorator | Luồng bọc luồng khác để bổ sung hành vi như buffering hoặc đọc kiểu dữ liệu |

Một luồng có thể **chặn luồng thực thi (blocking)** khi chưa có dữ liệu đọc hoặc khi việc ghi phải chờ thiết bị/đích. Không nên coi mỗi lời gọi `read` hoặc `write` là một “gói tin” hay một bản ghi ứng dụng.

## 2. InputStream: hợp đồng đọc byte

`InputStream` là lớp trừu tượng. Phương thức nền tảng `read()` trả một `int` để biểu diễn **một byte không dấu từ 0 đến 255**, hoặc `-1` nếu đã hết luồng. Kiểu trả về là `int` vì byte `0xFF` cũng là dữ liệu hợp lệ (giá trị 255) và phải phân biệt được với `-1` báo EOF.

```java
int value = in.read();
if (value == -1) {
    // Đã đến cuối luồng.
} else {
    // Một byte hợp lệ: 0..255.
}
```

Trong đoạn này, `in` là một `InputStream` đã được mở; đây là **đoạn minh họa**, chưa phải chương trình chạy độc lập.

Các thao tác đọc thường gặp:

| Phương thức | Hành vi chính |
| --- | --- |
| `int read()` | Đọc tối đa một byte; trả `0..255` hoặc `-1` |
| `int read(byte[] b)` | Đọc vào mảng; trả số byte đọc được hoặc `-1` |
| `int read(byte[] b, int off, int len)` | Đọc vào `b[off .. off+len)`; trả số byte thực nhận hoặc `-1` |
| `byte[] readNBytes(int len)` | Cố đọc tối đa `len` byte; mảng trả về có thể ngắn hơn khi gặp EOF |
| `int readNBytes(byte[] b, int off, int len)` | Cố đọc đủ `len` byte, dừng khi EOF hoặc lỗi; trả số byte đã đọc |
| `byte[] readAllBytes()` | Đọc tất cả byte còn lại vào RAM; chỉ nên dùng khi kích thước được kiểm soát |
| `long transferTo(OutputStream out)` | Chuyển các byte còn lại sang luồng đích; trả số byte đã chuyển |
| `long skip(long n)` | Yêu cầu bỏ qua tối đa `n` byte; có thể bỏ qua ít hơn |
| `void skipNBytes(long n)` | Bỏ qua đúng `n` byte hoặc ném ngoại lệ khi không thể |
| `int available()` | Ước lượng lượng byte có thể đọc ngay mà không chặn; **không phải tổng kích thước luồng** |
| `boolean markSupported()`, `mark(int)`, `reset()` | Hỗ trợ đánh dấu/quay lại **nếu** implementation cho phép |
| `void close()` | Đóng luồng và giải phóng tài nguyên liên quan |

Hầu hết hoạt động I/O có thể ném `IOException`. Với `read(b, off, len)`, số byte thực nhận **không nhất thiết bằng `len`**; chẳng hạn yêu cầu 8192 byte có thể chỉ nhận 300 byte. Chỉ vùng `b[off .. off+soByteDoc)` có dữ liệu mới của lần đọc. Với `len == 0`, kết quả là `0`.

### Vòng lặp đọc đúng

```java
byte[] buffer = new byte[8192];
int count;
while ((count = in.read(buffer)) != -1) {
    // Chỉ xử lý buffer[0] đến buffer[count - 1].
}
```

Đây là **mẫu mã nằm trong ngữ cảnh** đã có `InputStream in`. Điều kiện dựa vào kết quả `read`, không dựa vào `available()` và cũng không yêu cầu phải đọc đầy mảng. Với bộ đệm có độ dài dương, `InputStream` chuẩn thường đợi để nhận ít nhất một byte, EOF hoặc lỗi.

## 3. OutputStream: hợp đồng ghi byte

`OutputStream` cũng là lớp trừu tượng. Các phương thức thông dụng:

| Phương thức | Hành vi chính |
| --- | --- |
| `void write(int b)` | Ghi **8 bit thấp** của `b` làm một byte |
| `void write(byte[] b)` | Ghi toàn bộ byte trong mảng |
| `void write(byte[] b, int off, int len)` | Ghi đúng `len` byte bắt đầu ở `off` |
| `void flush()` | Yêu cầu đẩy dữ liệu được giữ trong các bộ đệm của luồng xuống tầng đích |
| `void close()` | Đóng luồng; implementation có thể flush trước khi giải phóng tài nguyên |

Khác `InputStream.read(...)`, `OutputStream.write(...)` trả về `void`; khi lời gọi hoàn tất bình thường, nó được kỳ vọng đã xử lý số byte yêu cầu theo hợp đồng của luồng. Nếu không ghi được, thông thường sẽ có `IOException`. Không có giá trị trả về “đã ghi một phần” để người gọi tiếp tục ở offset khác.

Ví dụ `out.write(0x1FF)` ghi một byte `0xFF` vì chỉ lấy tám bit thấp. Ví dụ `out.write(buffer, 0, count)` chỉ ghi số byte thật sự đọc ở lượt hiện tại.

**`flush()` khác `close()`:** `flush` hữu ích khi muốn dữ liệu đã ghi qua `BufferedOutputStream` được chuyển đến luồng bên dưới trong khi vẫn tiếp tục sử dụng luồng. `close` kết thúc vòng đời và giải phóng tài nguyên. `flush` **không bảo đảm dữ liệu đã được đồng bộ xuống thiết bị lưu trữ bền vững**; điều đó còn phụ thuộc hệ điều hành và cơ chế lưu trữ.

## 4. Ví dụ 1: nhận biết byte 255 và sao chép trong bộ nhớ

Chương trình độc lập dưới đây dùng `ByteArrayInputStream` làm nguồn và `ByteArrayOutputStream` làm đích. Nó minh họa đồng thời ba ý: `read()` trả `int`, EOF là `-1`, và luồng byte giữ nguyên dữ liệu nhị phân.

**Tệp `ByteBasics.java`:**

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.StringJoiner;

public class ByteBasics {
    public static void main(String[] args) throws IOException {
        byte[] payload = {0, 65, (byte) 0xFF};
        StringJoiner numbers = new StringJoiner(", ");

        try (InputStream in = new ByteArrayInputStream(payload);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            int value;
            while ((value = in.read()) != -1) {
                numbers.add(Integer.toString(value));
                out.write(value);
            }

            System.out.println("read(): " + numbers);
            System.out.println(
                    "same bytes: " + Arrays.equals(payload, out.toByteArray()));
        }
    }
}
```

Chạy với Java 17 trở lên:

```bash
javac ByteBasics.java
java ByteBasics
```

**Stdout chính xác:**

```text
read(): 0, 65, 255
same bytes: true
```

Tại sao `(byte) 0xFF` in ra 255 khi đọc? Trong mảng Java, `byte` có dấu nên `(byte) 0xFF` có giá trị số `-1`. Nhưng `InputStream.read()` trả dữ liệu byte đó dưới dạng `int` không dấu `255`. Nếu kiểm tra `byte value == -1` để phát hiện EOF, ta có thể nhầm dữ liệu hợp lệ `0xFF` với kết thúc luồng. **Luôn giữ kết quả `read()` ở kiểu `int` cho đến khi đã kiểm tra `-1`.**

`ByteArrayOutputStream.toByteArray()` tạo bản sao byte đã ghi. Với dữ liệu nằm hoàn toàn trong bộ nhớ, ví dụ này không cần đụng tới hệ thống tệp; `try-with-resources` vẫn thể hiện thói quen quản lý luồng thống nhất.

## 5. Ví dụ 2: sao chép tệp nhị phân bằng buffer

Khi làm việc với tệp, ta có thể mở luồng qua `Files.newInputStream(Path)` và `Files.newOutputStream(Path)` thuộc `java.nio.file`. Cả hai trả về đúng kiểu trừu tượng `InputStream`/`OutputStream` nên thuật toán sao chép độc lập với cách mở tệp. Đệm 8192 byte giúp tránh việc gọi I/O ở tầng ngoài từng byte một.

**Tệp `CopyBytes.java`:**

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyBytes {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java CopyBytes <source> <target>");
            return;
        }

        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path target = Path.of(args[1]).toAbsolutePath().normalize();

        if (source.equals(target)
                || (Files.exists(target) && Files.isSameFile(source, target))) {
            throw new IllegalArgumentException(
                    "Source and target must be different files");
        }

        try (InputStream in =
                     new BufferedInputStream(Files.newInputStream(source));
             OutputStream out =
                     new BufferedOutputStream(Files.newOutputStream(target))) {

            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }

        System.out.println("Copied bytes: " + Files.size(target));
    }
}
```

Biên dịch và chạy:

```bash
javac CopyBytes.java
java CopyBytes input.bin output.bin
```

Tệp `input.bin` cần tồn tại; `output.bin` sẽ được tạo nếu chưa có, hoặc bị **ghi đè nội dung** nếu đã có. Dòng stdout là `Copied bytes: N` với `N` bằng kích thước đầu ra tính theo byte; `N` tùy thuộc tệp đầu vào nên không gán một kết quả cố định. Các tên `input.bin`/`output.bin` có thể thay bằng đường dẫn thật.

Phân tích theo từng thao tác:

1. **Mở nguồn và đích:** `Files.newInputStream(source)` đọc từ tệp; `Files.newOutputStream(target)` mặc định tạo tệp khi thiếu và ghi đè (truncate) khi tệp tồn tại.
2. **Bọc buffer:** `BufferedInputStream`/`BufferedOutputStream` bổ sung đệm nội bộ. Mảng `buffer` trong chương trình vẫn có ý nghĩa rõ ràng: gom nhiều byte cho mỗi lượt xử lý.
3. **Đọc rồi ghi:** `count` là số byte hợp lệ của lần đọc. `out.write(buffer, 0, count)` không phát tán byte cũ còn lại trong mảng.
4. **Tự động đóng:** `try-with-resources` đóng `out` rồi `in` theo thứ tự ngược lúc khai báo, kể cả khi phát sinh ngoại lệ. Khi đóng `BufferedOutputStream`, dữ liệu đệm còn lại được chuyển xuống luồng dưới.
5. **Kiểm tra kết quả:** chương trình in kích thước đầu ra. Muốn xác nhận byte giống hệt, có thể so sánh byte hoặc checksum; kích thước bằng nhau **chưa chứng minh** nội dung bằng nhau.

Kiểm tra `Files.isSameFile` nếu đích tồn tại nhằm tránh mở đích trỏ vào chính nguồn và làm mất dữ liệu. Với đường dẫn bí danh phức tạp, kiểm tra trước không phải giao dịch nguyên tử: nếu luồng cần chạy trong môi trường có bên khác thay đổi đường dẫn đồng thời, hãy thiết kế thêm cơ chế bảo vệ phù hợp. Đoạn ví dụ cũng **không tự tạo thư mục cha** của đích.

### Giải pháp gọn hơn với transferTo

Với bài toán chuyển toàn bộ byte còn lại từ nguồn sang đích, `InputStream.transferTo(OutputStream)` (Java 9+) thay vòng lặp thủ công:

```java
// Đoạn nằm trong ngữ cảnh đã có Path source và Path target hợp lệ,
// đã kiểm tra không cùng tệp.
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(target)) {
    long transferred = in.transferTo(out);
    System.out.println("Transferred bytes: " + transferred);
}
```

Ở đoạn này, `Files`, `Path`, `InputStream`, `OutputStream` là các kiểu trong `java.nio.file` và `java.io` cần import. `transferTo` đọc cho đến EOF và trả số byte đã chuyển; không hứa chắc sẽ sử dụng cơ chế zero-copy của hệ điều hành. Nếu cần xử lý, giới hạn, đo đếm hoặc biến đổi từng khối byte, vòng lặp `read(buffer)` vẫn rõ ràng và linh hoạt.

## 6. Các implementation thường gặp và quan hệ giữa chúng

| Nhóm | Đầu vào | Đầu ra | Công dụng |
| --- | --- | --- | --- |
| Tệp | `FileInputStream` hoặc `Files.newInputStream` | `FileOutputStream` hoặc `Files.newOutputStream` | Đọc/ghi byte của tệp |
| Bộ nhớ | `ByteArrayInputStream` | `ByteArrayOutputStream` | Test, tích lũy dữ liệu nhỏ/vừa |
| Có đệm | `BufferedInputStream` | `BufferedOutputStream` | Giảm lời gọi I/O ở luồng bên dưới |
| Giá trị nguyên thủy | `DataInputStream` | `DataOutputStream` | Đọc/ghi giá trị như `int`, `long`, `double` theo định dạng nhị phân đã quy định |
| Luồng có lớp bọc khác | `GZIPInputStream` (trong `java.util.zip`) | `GZIPOutputStream` | Giải nén/nén các byte đi qua luồng |

Một implementation có thể **bọc** implementation khác: `new BufferedInputStream(Files.newInputStream(path))`. Luồng ngoài nhận hành vi bổ sung, nhưng vẫn tuân thủ hợp đồng `InputStream`. Đối với kiểu dữ liệu có cấu trúc, `DataInputStream` và `DataOutputStream` cần được dùng theo **cùng thứ tự và định dạng** ở hai phía. Nếu viết `writeInt(42)` thì phía đọc cần `readInt()`, chứ không tự tách byte bằng encoding văn bản.

**Không phải mọi stream đều seek được.** `InputStream` là cách đọc tuần tự. Nếu cần truy cập vị trí tùy ý của tệp, cân nhắc `SeekableByteChannel` hoặc `RandomAccessFile`. `mark/reset` chỉ là tùy chọn: luôn kiểm tra `markSupported()`; `BufferedInputStream` hỗ trợ, còn `FileInputStream` không hỗ trợ đánh dấu/quay lại theo cách đó.

## 7. Quản lý tài nguyên và lỗi I/O

Luồng gắn với tệp hoặc kết nối thường giữ tài nguyên hệ điều hành. Hãy sử dụng **`try-with-resources`** khi tạo và sở hữu luồng:

```java
try (InputStream in = Files.newInputStream(source)) {
    // Đọc dữ liệu ở đây.
} // close() tự động được gọi.
```

Đoạn này giả định có `Path source` và các import cần thiết. Không gọi `close()` sớm khi còn cần truy cập; thao tác với stream đã đóng có thể ném `IOException` (tùy implementation). Ngoại lệ phát sinh ở phần xử lý vẫn được ném ra; nếu `close()` cũng lỗi, Java gắn lỗi đóng thành **suppressed exception** để không che mất lỗi chính. Có thể xem qua `Throwable.getSuppressed()` khi gỡ lỗi.

Phân biệt **quyền sở hữu**: code tự mở luồng thường tự đóng; code nhận luồng từ bên gọi nên thống nhất hợp đồng ai đóng. Tránh tùy tiện đóng `System.in` hoặc `System.out` vì đây là tài nguyên dùng chung của quá trình chạy.

`IOException` có thể xuất hiện khi tệp không tồn tại, không đủ quyền, thiết bị bị lỗi, hoặc luồng bên ngoài bị gián đoạn. Một phép sao chép thất bại giữa chừng có thể để lại **tệp đích một phần**; `try-with-resources` giúp đóng tài nguyên chứ không đảm bảo rollback. Nếu ứng dụng cần thay thế tệp một cách an toàn, có thể ghi vào tệp tạm cùng filesystem, xác minh rồi thay thế/di chuyển theo khả năng và điều kiện hệ thống; đó là vấn đề riêng ngoài hợp đồng `InputStream`/`OutputStream`.

## 8. Đọc text qua byte stream: phải chỉ rõ charset

Byte stream không biết ký tự. Khi cần chuyển giữa chuỗi và byte, **chọn `Charset` tường minh**:

```java
// Đoạn minh họa, cần import java.nio.charset.StandardCharsets.
byte[] bytes = "Xin chào".getBytes(StandardCharsets.UTF_8);
String text = new String(bytes, StandardCharsets.UTF_8);
```

Việc cắt tùy ý một mảng byte UTF-8 rồi giải mã từng phần có thể chia đôi chuỗi mã hóa một ký tự và gây lỗi/nhân vật thay thế. Nếu xử lý văn bản có cấu trúc, hãy dùng `InputStreamReader`/`OutputStreamWriter` với charset rõ ràng, hoặc `Files.newBufferedReader`/`Files.newBufferedWriter`. Với byte thuần, giữ byte đến cuối quá trình sao chép thì không có vấn đề encoding.

Chẳng hạn, **sao chép ảnh PNG qua `InputStream`/`OutputStream` không được chuyển ảnh thành `String`**. Chuyển byte nhị phân qua String với charset không phù hợp có thể làm thay đổi byte và hỏng tệp.

## 9. Các góc cạnh API dễ hiểu sai

**`available()` không đo kích thước nguồn.** Nó là ước lượng số byte có thể đọc ngay mà không chặn, có thể trả 0 dù sẽ có thêm dữ liệu sau đó. Không dùng `new byte[in.available()]` làm chiến lược đọc toàn bộ nội dung, đặc biệt với luồng mạng.

**`skip(n)` không chắc bỏ qua đủ n byte.** Nó trả số thực sự đã bỏ qua. Nếu cần đảm bảo bỏ qua đúng `n`, Java hiện đại có `skipNBytes(n)`, phương thức có thể ném `EOFException` nếu luồng kết thúc sớm.

**`readAllBytes()` đổi tính tiện lợi lấy bộ nhớ.** Với đầu vào rất lớn hoặc không giới hạn, dữ liệu có thể vượt RAM. Dùng vòng lặp theo khối khi kích thước chưa biết hoặc khi cần streaming.

**Một lần `read(buffer)` không tương đương một thông điệp.** Luồng byte không bảo toàn ranh giới bản ghi. Nếu giao thức có bản ghi, ứng dụng phải định nghĩa cách phân ranh: độ dài cố định, trường length-prefix, dấu phân cách có quy tắc escape, hoặc cách mã hóa khác.

**`flush()` không thay thế `close()`.** Flush yêu cầu chuyển dữ liệu đang đệm, còn close giải phóng tài nguyên. Với các lớp bọc lồng nhau, đóng lớp ngoài thường đóng cả lớp bên trong; không cần khai báo cả outer stream và underlying stream thành hai tài nguyên riêng để đóng hai lần.

**`write(int)` không viết bốn byte của một `int`.** Nó chỉ ghi 8 bit thấp. Nếu muốn biểu diễn một `int` thành 4 byte theo định dạng xác định, dùng `DataOutputStream.writeInt` hoặc tự đặc tả mã hóa; đầu đọc phải dùng cùng định dạng.

**`byte` của Java là có dấu, byte trên luồng không có kiểu dấu.** Khi cần đổi `byte b` sang số nguyên 0..255, dùng `b & 0xFF`. Trái lại, `InputStream.read()` đã trả `int` 0..255 nên không cần thao tác đó.

## 10. Sai lầm thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `byte b = (byte) in.read(); if (b == -1) ...` | Giá trị dữ liệu `0xFF` bị nhầm với EOF | Dùng `int value = in.read()`, kiểm tra `value == -1` |
| `out.write(buffer)` ngay sau `read(buffer)` | Lượt cuối có thể đọc ít hơn kích thước buffer, ghi cả byte cũ | `out.write(buffer, 0, count)` |
| Cho rằng `read(buffer)` luôn đọc đủ mảng | `read` có thể trả số byte dương nhỏ hơn yêu cầu | Dựa vào số byte trả về; tích lũy nếu cần một khối đủ dài |
| Lặp `while (in.available() > 0)` để đọc đến hết | `available` không là EOF/total-size signal | Lặp đến khi `read` trả `-1` |
| Dùng `readAllBytes()` cho file không giới hạn | Có nguy cơ hết bộ nhớ | Đọc/ghi theo khối |
| Quên đóng file stream | Có thể rò rỉ file descriptor hoặc thiếu flush | `try-with-resources` |
| Giải mã byte nhị phân thành String rồi ghi lại | Charset có thể làm thay đổi dữ liệu | Giữ nguyên byte, chỉ decode khi thực sự là text |
| Mở đầu ra trên cùng tệp nguồn | Đầu ra mặc định truncate, mất dữ liệu nguồn | Kiểm tra hai đường dẫn và thiết kế quy trình thay thế an toàn |
| Coi `flush()` là xác nhận ghi bền vững xuống đĩa | Flush không mang cam kết fsync | Dùng cơ chế đồng bộ lưu trữ riêng khi yêu cầu độ bền |
| Chỉ so `Files.size` để xác nhận bản sao | Tệp khác nội dung vẫn có thể cùng kích thước | So byte, hash phù hợp hoặc `Files.mismatch` khi cần xác minh |

## 11. Chọn API trong các tình huống thực tế

- **Sao chép luồng byte không giới hạn:** `read(buffer)` + `write(buffer, 0, count)`, hoặc `transferTo` nếu chỉ cần sao chép nguyên trạng.
- **Tệp nhỏ, đã biết giới hạn:** `Files.readAllBytes`/ `InputStream.readAllBytes` có thể thuận tiện nhưng sử dụng RAM tỉ lệ kích thước nội dung.
- **Đọc/ghi text:** `Reader`/`Writer` hoặc `Files` API kèm `StandardCharsets.UTF_8` khi phù hợp.
- **Cần đọc/ghi số nguyên/giá trị nguyên thủy theo định dạng nhị phân:** `DataInputStream` và `DataOutputStream`, đồng bộ thứ tự đọc/ghi.
- **I/O tuần tự với nhiều thao tác nhỏ:** thêm `BufferedInputStream`/`BufferedOutputStream` khi chưa có buffering phù hợp ở tầng dưới.
- **Truy cập ngẫu nhiên vị trí file:** chọn API random-access/channels thay vì cố gắng dùng byte stream tuần tự.

Thường nên khai báo biến theo **kiểu trừu tượng** `InputStream`/`OutputStream` và tạo một implementation cụ thể ở biên ứng dụng. Cách này giúp thay tệp bằng dữ liệu in-memory trong test mà không thay logic xử lý.

## 12. Tự thực hành và câu hỏi kiểm tra

1. Chạy `ByteBasics`, thay `payload` bằng `{(byte) 0x80, (byte) 0xFF, 0}`. Dự đoán `read()` trả các số nào (đáp án: `128, 255, 0`).
2. Dùng `CopyBytes` với một tệp nhị phân nhỏ và một tệp lớn. Kiểm tra nội dung bằng `Files.mismatch(Path, Path)` (Java 12+): kết quả `-1` nghĩa là không tìm thấy byte khác nhau; giá trị không âm là vị trí byte khác nhau đầu tiên.
3. Giải thích tại sao trong vòng lặp sao chép cần `out.write(buffer, 0, count)` và điều gì xảy ra nếu lượt đọc cuối chỉ trả 7 byte trong buffer 8192 byte.
4. Viết một phiên bản dùng `transferTo`, sau đó sửa thành phiên bản chỉ sao chép tối đa 1024 byte đầu. Với bài toán có giới hạn như vậy, cần tự kiểm soát số byte yêu cầu đọc ở từng vòng.
5. Tự trả lời: `read()` trả `-1` có giống với byte `0xFF` không? `flush()` có đồng nghĩa đóng luồng không? `available()` có biểu thị file còn bao nhiêu byte không?

**Ghi nhớ:** `InputStream` cung cấp byte và tín hiệu EOF; `OutputStream` nhận byte; vòng lặp phải dùng **đúng số byte vừa đọc**; tài nguyên phải có vòng đời rõ ràng. Nắm chắc bốn điều này sẽ giúp áp dụng cùng tư duy cho tệp, bộ nhớ và nhiều loại nguồn/đích byte khác trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-002">read trả về int từ 0 đến 255 và dấu hiệu EOF -1</a>

<details>
<summary>Xem chi tiết</summary>

# `InputStream.read()`: giá trị byte 0–255 và dấu hiệu EOF `-1`

## 1. Giá trị trả về của `read()`

Trong Java I/O, `InputStream` đọc dữ liệu theo **byte**. Phương thức cơ bản được khai báo:

```java
public abstract int read() throws IOException;
```

Một lần gọi `read()` có hai loại kết quả:

- **`0` đến `255`:** đọc được một byte. Số nguyên thể hiện **giá trị không dấu** của 8 bit trong byte đó.
- **`-1`:** **end of stream (EOF)**, không còn byte tiếp theo được luồng cung cấp theo hợp đồng của phương thức.

Ví dụ, đọc chuỗi byte hex `00 7F 80 FF`:

| Lần gọi | Byte thực tế | `read()` trả về |
| --- | --- | ---: |
| 1 | `0x00` | `0` |
| 2 | `0x7F` | `127` |
| 3 | `0x80` | `128` |
| 4 | `0xFF` | `255` |
| 5 | Hết byte | `-1` |

**Không nhầm lẫn:** `0` là byte dữ liệu hợp lệ; `255` cũng là byte hợp lệ. Chỉ `-1` mới báo EOF. Dấu hiệu `-1` **không phải** byte `0xFF` được lưu trong luồng và không được xử lý như dữ liệu đầu vào.

## 2. Vì sao đọc byte nhưng nhận `int`?

`byte` trong Java là số nguyên có dấu 8 bit, có miền **`-128..127`**. Nhưng mọi byte đọc từ luồng có thể mang một trong 256 mẫu bit, thông thường biểu diễn không dấu từ **`0..255`**.

Nếu kết quả trả về có kiểu `byte`, byte chứa `11111111` (`0xFF`) sẽ có giá trị `-1`, trùng dấu hiệu EOF. Bởi vậy `InputStream.read()` dùng `int`: 256 giá trị dành cho dữ liệu và một giá trị riêng cho kết thúc.

| Mẫu bit | `byte` có dấu trong Java | `int` do `read()` trả về |
| --- | ---: | ---: |
| `0x00` | `0` | `0` |
| `0x7F` | `127` | `127` |
| `0x80` | `-128` | `128` |
| `0xFF` | `-1` | `255` |

Cần nhớ:

- **Giữ kết quả `read()` ở biến `int`** cho đến sau khi kiểm tra EOF.
- **Không ép sang `byte` rồi so sánh với `-1`**, vì dữ liệu `0xFF` cũng biến thành `(byte) -1`.
- Khi đã có `byte b` và muốn diễn giải thành số không dấu, dùng `Byte.toUnsignedInt(b)` hoặc `b & 0xFF`. Với `int` do `read()` trả về trong khoảng `0..255` thì không cần chuyển đổi.

## 3. Chương trình Java 17+ chạy độc lập

Tạo tệp **`ReadByteDemo.java`** với nội dung:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ReadByteDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};

        try (InputStream input = new ByteArrayInputStream(data)) {
            int value;
            while ((value = input.read()) != -1) {
                System.out.printf("%d -> 0x%02X%n", value, value);
            }

            System.out.println("Sau EOF: " + input.read());
        }
    }
}
```

Biên dịch, chạy:

```bash
javac ReadByteDemo.java
java ReadByteDemo
```

Đầu ra **chính xác**:

```text
0 -> 0x00
127 -> 0x7F
128 -> 0x80
255 -> 0xFF
Sau EOF: -1
```

Giải thích:

1. Mảng lưu bốn mẫu bit `00`, `7F`, `80` và `FF`. Cần `(byte)` với `0x80`, `0xFF` vì hai hằng int ấy vượt miền `byte` có dấu.
2. `ByteArrayInputStream` lần lượt cung cấp bốn byte. Mỗi `input.read()` trả về số `int` không dấu `0..255`, nên `0x80` trở thành `128` và `0xFF` trở thành `255`.
3. `(value = input.read()) != -1` vừa gán byte mới vào `value` vừa dừng **chỉ khi EOF**; không làm mất byte `0x00`. Một vòng chỉ thực hiện một lần đọc.
4. `%02X` in số hex hai chữ số. Đây là mẫu hiển thị; dữ liệu thực đọc vẫn là byte nhị phân.
5. Sau bốn byte, `ByteArrayInputStream` trả `-1`; với chính lớp này, gọi tiếp sau EOF vẫn nhận `-1`. Không coi dòng cuối là byte thứ năm.
6. `try-with-resources` đóng luồng kể cả khi xuất hiện ngoại lệ.

Với `FileInputStream`, điều kiện lặp `int` và `-1` vẫn đúng; thời điểm EOF tùy theo độ dài tệp. Mảng ví dụ trên giúp kết quả được xác định mà không cần chuẩn bị tệp ngoài.

## 4. Điều kiện vòng lặp đúng và các lỗi điển hình

Mẫu đọc từng byte:

```java
int value;
while ((value = input.read()) != -1) {
    // value là dữ liệu 0..255, xử lý tại đây
}
```

Đây là **đoạn mã minh họa**, giả sử `input` là một `InputStream` đã mở và nơi chứa đoạn mã xử lý hoặc khai báo `IOException`. Điều kiện `>= 0` cũng đúng vì hợp đồng `read()` chỉ cho kết quả `-1` hoặc `0..255`; viết `!= -1` nhấn mạnh ý nghĩa EOF.

Những cách viết sai:

```java
// SAI: dừng khi gặp byte hợp lệ 0x00.
while ((value = input.read()) > 0) {
    // ...
}

// SAI: 0xFF và EOF cùng biến thành byte -1 sau phép ép.
byte b = (byte) input.read();
if (b == -1) {
    // Không phân biệt được 0xFF với EOF.
}

// SAI: sử dụng giá trị trước khi kiểm tra.
int n = input.read();
// Không được coi n là một byte nếu n == -1.
```

Nếu cần lưu một byte vào `byte`, hãy đọc vào `int`, kiểm tra `n != -1`, rồi mới ép kiểu trong nhánh thành công. Khi `n == 255`, `(byte)n` là `-1` theo biểu diễn có dấu của Java, nhưng **byte dữ liệu vẫn tồn tại**; EOF đã được kiểm tra trước đó.

## 5. EOF không phải “chưa có dữ liệu” hoặc lỗi

`InputStream.read()` có thể **chặn (blocking)**, tùy loại luồng. Nếu byte tiếp theo chưa sẵn sàng và chưa có EOF, lệnh có thể chờ có dữ liệu, chờ kết thúc hoặc ném `IOException`. Phân biệt:

- **`0..255`:** đọc được một byte, kể cả byte có giá trị `0`.
- **`-1`:** luồng báo đã đến cuối đầu vào theo hợp đồng `read()`.
- **Đang chờ:** chưa có kết quả trả về; không thể tự suy luận là EOF.
- **`IOException`:** có lỗi I/O; không nên giả lập thành `-1` rồi im lặng bỏ lỗi.

`input.available() == 0` **không chứng minh EOF**: `available()` chỉ ước lượng số byte có thể đọc mà không chặn. Một socket chẳng hạn có thể tạm thời chưa nhận byte nào nhưng vẫn tiếp tục được gửi dữ liệu. EOF của luồng socket thường liên quan đến việc bên kia đóng chiều gửi; một lần đọc không nhận được dữ liệu ngay lập tức không tạo thành dấu hiệu kết thúc.

Nếu chương trình cần giới hạn thời gian chờ hoặc biết ranh giới từng thông điệp, dùng cơ chế timeout hoặc quy ước định dạng/giao thức phù hợp. Bản thân `read()` và dấu `-1` chỉ mô tả luồng byte và kết thúc luồng.

## 6. Phân biệt với `read(byte[])`

Giá trị trả về **có ý nghĩa khác nhau** giữa hai kiểu đọc:

| Lời gọi | Kết quả trả về |
| --- | --- |
| `input.read()` | **Giá trị byte** `0..255` hoặc `-1` ở EOF |
| `input.read(buffer)` | **Số byte thực đọc** vào buffer hoặc `-1` ở EOF |
| `input.read(buffer, offset, length)` | **Số byte thực đọc** trong vùng yêu cầu; `0` nếu `length == 0`; `-1` nếu EOF trước khi đọc byte nào với yêu cầu có độ dài dương |

Khi đọc mảng, phải duyệt `buffer[0]` tới `buffer[count - 1]`. Các ô khác có thể chứa dữ liệu từ lần đọc trước. Một lần `read(buffer)` không bảo đảm lấp đầy cả mảng với mọi loại luồng.

Ví dụ Java 17+ độc lập, tạo **`ReadBufferDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ReadBufferDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = {(byte) 0xFF, 0x00, 0x2A};

        try (InputStream input = new ByteArrayInputStream(data)) {
            byte[] buffer = new byte[2];
            int count;

            while ((count = input.read(buffer)) != -1) {
                System.out.print("Đọc " + count + " byte:");
                for (int i = 0; i < count; i++) {
                    System.out.print(" " + Byte.toUnsignedInt(buffer[i]));
                }
                System.out.println();
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ReadBufferDemo.java
java ReadBufferDemo
```

Kết quả:

```text
Đọc 2 byte: 255 0
Đọc 1 byte: 42
```

Lần đầu `count == 2`; buffer có dữ liệu mới `255`, `0`. Lần sau `count == 1`; chỉ `buffer[0]` chứa byte mới `42`, còn `buffer[1]` giữ giá trị cũ `0`. Sau đó EOF trả `-1`. Cách chia `2` byte rồi `1` byte được bảo đảm với `ByteArrayInputStream` cụ thể trong ví dụ; các luồng khác có thể trả về ít byte hơn kích thước buffer trong một lần đọc.

Khi `length == 0`, `read(buffer, offset, length)` trả `0` mà không đọc dữ liệu. Với yêu cầu đọc có độ dài dương, hợp đồng thông thường của `InputStream` trả số byte dương hoặc `-1` tại EOF. Vì vậy **không được áp dụng quy tắc kiểm tra EOF của `read()` cho mọi giá trị mang tên “read” mà không xét chữ ký phương thức**.

Các phương thức tiện ích `readNBytes` và `readAllBytes` có hợp đồng riêng về lượng dữ liệu đọc và cấp phát bộ nhớ; chúng không làm thay đổi ý nghĩa `0..255` và `-1` của `read()` không tham số.

## 7. Tự kiểm tra

Trước khi hoàn tất một vòng lặp đọc byte, xác nhận:

1. Biến nhận `read()` có kiểu `int`?
2. Có kiểm tra `-1` **trước** khi ép thành `byte` hoặc dùng như dữ liệu?
3. Byte `0x00` và `0xFF` đều được xử lý?
4. Với buffer, chỉ duyệt đúng số byte thực đọc?
5. Có phân biệt EOF với trạng thái đang chờ và với `IOException`?
6. Luồng được đóng khi không dùng nữa, thường bằng `try-with-resources`?

**Bài tập nhỏ:** Luồng chứa đúng hai byte `00 FF`. Ba lời gọi `read()` liên tiếp phải trả **`0`, `255`, `-1`**. Nếu dừng tại `0` hoặc coi `255` là EOF, hãy kiểm tra lại điều kiện lặp và phép ép kiểu.

**Ghi nhớ:** `InputStream.read()` trả về `int` vì cần chứa đầy đủ **256 giá trị byte không dấu (`0..255`)** cùng **dấu EOF riêng (`-1`)**. Luôn kiểm tra `-1` rồi mới xử lý byte.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-003">Đọc theo byte&#91;&#93;, offset và length</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc luồng byte bằng `byte[]`, `offset` và `length` trong Java

Khi làm việc với `InputStream`, ta thường đọc nhiều byte vào một mảng thay vì gọi `read()` từng byte. Hai tham số `offset` và `length` cho phép chỉ định **vùng được phép ghi trong mảng đích**. Hiểu đúng chúng giúp xử lý dữ liệu nhị phân, sao chép tệp, đọc dữ liệu mạng và ghép các phần dữ liệu mà không làm hỏng byte đã có.

## 1. Ba cách đọc cơ bản

```java
int oneByte = in.read();
int count1 = in.read(buffer);
int count2 = in.read(buffer, offset, length);
```

- `read()`: đọc tối đa **một byte**, trả về số nguyên từ `0` đến `255`, hoặc `-1` khi hết luồng. Không ép trực tiếp về `byte` trước khi kiểm tra `-1`.
- `read(byte[] buffer)`: yêu cầu đọc tối đa `buffer.length` byte vào mảng, bắt đầu ở chỉ số `0`; về mặt hợp đồng tương đương vùng `read(buffer, 0, buffer.length)`.
- `read(byte[] buffer, int offset, int length)`: yêu cầu đọc tối đa `length` byte vào `buffer` từ chỉ số `offset`.

Trong ba trường hợp, dữ liệu được lấy từ **vị trí đọc hiện tại của luồng**. `offset` không phải vị trí trong tệp hoặc luồng; nó là chỉ số bắt đầu trong **mảng đích**.

## 2. Ý nghĩa chính xác của `offset` và `length`

Với chữ ký:

```java
int n = in.read(buffer, offset, length);
```

| Thành phần | Ý nghĩa |
|---|---|
| `buffer` | Mảng `byte[]` nhận dữ liệu |
| `offset` (thường viết tắt `off`) | Chỉ số phần tử **đầu tiên được phép ghi** trong mảng |
| `length` (thường viết tắt `len`) | **Số byte tối đa** muốn đọc trong lần gọi này, không phải chỉ số kết thúc |
| `n` | **Số byte thực sự đọc được**; dùng chính giá trị này để xử lý dữ liệu |

Nếu lời gọi trả về `n > 0`, chỉ vùng `buffer[offset]` đến `buffer[offset + n - 1]` chứa dữ liệu mới từ lần đọc đó. Vùng ngoài khoảng nêu trên giữ nguyên; phần còn lại của vùng dự kiến nhưng chưa được đọc **không tự được xóa**.

Điều kiện chỉ số hợp lệ:

```text
buffer != null
offset >= 0
length >= 0
offset <= buffer.length
length <= buffer.length - offset
```

Ví dụ, mảng 8 byte và `read(buffer, 2, 3)` cho phép ghi tại chỉ số `2, 3, 4`, tức khoảng nửa mở `[2, 5)`. Nó **không** ghi đến chỉ số `3` rồi dừng, cũng không bỏ qua hai byte ở đầu luồng.

- `buffer == null` dẫn đến `NullPointerException`.
- Chỉ số hoặc kích thước vùng không hợp lệ dẫn đến `IndexOutOfBoundsException`.
- `length == 0` trả về `0` mà không đọc byte; đừng dùng mảng dài 0 trong vòng lặp chờ `-1`.
- Trong lần đọc có `length > 0`, kết quả thường là một số dương **không vượt quá** `length`, hoặc `-1` khi không còn byte vì đã đến cuối luồng. Một số nguồn có thể chặn (block) để chờ dữ liệu hoặc báo lỗi `IOException`.

## 3. Ví dụ chạy được: giữ nguyên vùng ngoài `offset`

Tạo tệp **`ByteSliceReadDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ByteSliceReadDemo {
    public static void main(String[] args) throws IOException {
        byte[] storage = "........".getBytes(StandardCharsets.US_ASCII);

        try (InputStream in = new ByteArrayInputStream(
                "ABCDE".getBytes(StandardCharsets.US_ASCII))) {
            int first = in.read(storage, 2, 3);
            int second = in.read(storage, 5, 2);
            int eof = in.read(storage, 0, 2);

            System.out.printf("first=%d, second=%d, eof=%d%n",
                    first, second, eof);
            System.out.println(new String(storage, StandardCharsets.US_ASCII));
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac ByteSliceReadDemo.java
java ByteSliceReadDemo
```

Kết quả:

```text
first=3, second=2, eof=-1
..ABCDE.
```

Giải thích lần lượt:

1. `read(storage, 2, 3)` lấy `A B C` vào chỉ số `2, 3, 4`; trả `3`.
2. `read(storage, 5, 2)` tiếp tục **từ vị trí luồng sau C**, lấy `D E` vào chỉ số `5, 6`; trả `2`.
3. Lần đọc tiếp theo hết luồng, trả `-1` và không có byte mới để xử lý.
4. Chỉ số `0, 1, 7` vẫn là dấu chấm ban đầu. Với một `ByteArrayInputStream` như ví dụ, lượng dữ liệu trong bộ nhớ đã biết nên kết quả trên xác định; **không suy rộng** rằng mọi luồng đều trả đủ số byte yêu cầu sau mỗi lần đọc.

## 4. Đọc một phần là điều bình thường

`read(buffer, off, len)` yêu cầu **tối đa** `len` byte, **không bảo đảm** lấy đủ `len` byte trong một lần. Ví dụ, yêu cầu 1024 byte có thể chỉ trả `n = 37` khi tại thời điểm đó luồng chỉ cung cấp được một phần. Đọc tệp, socket hoặc luồng đã bọc qua bộ đệm đều phải tuân theo giá trị trả về thực tế.

Với `n > 0`, dùng đúng khoảng `[off, off + n)`:

```java
int n = in.read(buffer, 4, 8);
if (n > 0) {
    out.write(buffer, 4, n); // Viết đúng n byte vừa đọc
}
```

Đoạn trên là **mảnh mã minh họa**, yêu cầu `buffer.length >= 12` cùng các biến `in`, `out` đã được tạo. `length = 8` là tối đa tám byte, còn `n` mới là số byte đã nhận.

Không xử lý toàn bộ `length` hay toàn bộ `buffer.length` nếu `n` nhỏ hơn: phần dư chứa dữ liệu cũ hoặc dữ liệu khởi tạo, sẽ gây sai dữ liệu đầu ra.

## 5. Mẫu hoàn chỉnh: đọc theo từng khối rồi sao chép

Tạo tệp **`ByteStreamCopyDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ByteStreamCopyDemo {
    public static void main(String[] args) throws IOException {
        byte[] source = {0, 1, 2, (byte) 255, 4};

        try (InputStream in = new ByteArrayInputStream(source);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[3]; // Kích thước > 0
            int n;

            while ((n = in.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, n);
                System.out.println("chunk=" + n);
            }

            System.out.println(Arrays.toString(out.toByteArray()));
        }
    }
}
```

```bash
javac ByteStreamCopyDemo.java
java ByteStreamCopyDemo
```

Kết quả:

```text
chunk=3
chunk=2
[0, 1, 2, -1, 4]
```

Khối cuối chỉ có 2 byte, nên phải dùng `out.write(buffer, 0, n)`. Nếu thay bằng `out.write(buffer)`, toàn bộ ba vị trí sẽ được xuất, bao gồm một byte còn sót lại từ khối trước.

Byte `(byte) 255` được hiển thị là `-1` trong `Arrays.toString(byte[])` vì kiểu `byte` của Java có dấu (giá trị từ `-128` đến `127`). Nội dung nhị phân vẫn giữ mẫu bit `0xFF`. Khi cần xem dạng số không dấu, dùng `buffer[i] & 0xFF`. Giá trị `-1` báo EOF chỉ là **giá trị trả về kiểu `int` của phương thức đọc**, không phải bằng chứng byte `0xFF` là EOF.

Đối với tệp thực, có thể thay nguồn bằng `Files.newInputStream(path)` và đích bằng `Files.newOutputStream(path)` sau khi xác định đường dẫn thích hợp; giữ nguyên cách lặp và nguyên tắc ghi đúng `n` byte. `try-with-resources` bảo đảm đóng luồng cả khi gặp lỗi.

## 6. Muốn điền gần đủ một vùng mảng thì làm sao?

Có hai nhu cầu khác nhau:

**A. Đọc tối đa `length` byte, được phép dừng sớm:** dùng `read(buffer, offset, length)`, kiểm tra `n` và xử lý phần đã nhận.

**B. Cố gắng điền tối đa `length` byte, chấp nhận cần nhiều lần đọc:** từ Java 9, dùng `readNBytes(buffer, offset, length)`. Phương thức này có thể đọc lặp cho đến khi đủ, gặp EOF hoặc lỗi; giá trị trả về là **tổng số byte đã đọc**, từ `0` đến `length`. Đặc biệt, khi EOF trước khi đọc được byte nào, `readNBytes` trả `0` (khác `read` trả `-1`).

Ví dụ chạy được (JDK 17+) trong tệp **`ReadNBytesDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class ReadNBytesDemo {
    public static void main(String[] args) throws IOException {
        byte[] target = {9, 9, 9, 9, 9};

        try (InputStream in = new ByteArrayInputStream(
                new byte[] {10, 20, 30})) {
            int n = in.readNBytes(target, 1, 4);
            System.out.println("n=" + n);
            System.out.println(Arrays.toString(target));
        }
    }
}
```

```bash
javac ReadNBytesDemo.java
java ReadNBytesDemo
```

```text
n=3
[9, 10, 20, 30, 9]
```

Mặc dù yêu cầu tối đa 4 byte, nguồn chỉ có 3 byte. Phần tử cuối của mảng vẫn giữ giá trị 9. Nếu giao thức **bắt buộc đúng số byte** (ví dụ một header dài cố định), dùng `DataInputStream.readFully(buffer, offset, length)`, phương thức chỉ thành công khi đọc đủ và ném `EOFException` nếu EOF đến sớm. Không đánh đồng `readNBytes` với `readFully`.

Khi cần đọc đến hết tệp có kích thước biết trước và phù hợp bộ nhớ, `readAllBytes()` cũng có thể hữu ích; tránh mặc định đọc toàn bộ luồng rất lớn hoặc không giới hạn vào RAM.

## 7. Những lỗi dễ gặp

| Lỗi | Tại sao sai | Cách sửa |
|---|---|---|
| Gọi `read(buffer, 4, 8)` với mảng dài 8 | `length` là số lượng, nên vùng `[4, 12)` vượt mảng | Dùng `read(buffer, 4, 4)` nếu muốn phần tử 4–7 |
| Nghĩ `read(buffer, off, len)` luôn trả `len` | Luồng có thể trả số byte ít hơn | Luôn lưu và kiểm tra `n` |
| Xử lý cả `buffer` sau khi chỉ đọc được `n` byte | Dữ liệu cũ bị trộn vào dữ liệu mới | Xử lý `buffer[off..off+n)` |
| Dùng `while (read(...) > 0)` để đọc mọi luồng | Với lời gọi `length == 0` có thể trả 0 liên tục; còn EOF cần xử lý rõ | Đảm bảo buffer có độ dài dương; kiểm tra `n == -1` |
| Hiểu `offset` là vị trí trong tệp | `offset` chỉ vị trí trong mảng nhận | Vị trí luồng do các thao tác đọc/skip/seek tương ứng quyết định |
| Dùng `available()` làm tổng kích thước luồng | `available()` chỉ là ước lượng byte đọc được mà không chặn | Đọc theo vòng lặp tới EOF hoặc theo giao thức định độ dài |
| Giải mã từng khối byte UTF-8 độc lập | Một ký tự Unicode có thể bị tách giữa hai lần đọc | Với văn bản, dùng `InputStreamReader` hoặc bộ giải mã lưu trạng thái |

Ngoài ra, `IOException` có thể phát sinh trong bất kỳ lần đọc nào. Khi một giao thức yêu cầu lấy đúng N byte, phải chủ động xác định cách xử lý trường hợp EOF sớm, dữ liệu thiếu và lỗi I/O; chỉ kiểm tra điều kiện `n != -1` không tự bảo đảm gói dữ liệu đầy đủ.

## 8. Ghi nhớ khi áp dụng

- `offset` là **vị trí bắt đầu ghi vào mảng đích**; `length` là **số byte tối đa yêu cầu**, với `offset + length <= buffer.length`.
- `read` trả **số byte thực tế**, có thể ít hơn yêu cầu; `-1` nghĩa là **hết luồng**, còn `length == 0` trả `0`.
- Chỉ đọc/xuất các phần tử vừa nhận: từ `offset` đến trước `offset + n`.
- Luôn dùng bộ đệm có kích thước dương và vòng lặp kiểm tra số byte thực tế khi cần đọc cả luồng.
- Chọn `readNBytes` nếu muốn cố gắng điền vùng nhận; chọn `DataInputStream.readFully` nếu bắt buộc đủ dữ liệu hoặc phát hiện EOF sớm là lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-004">Một lần read không bảo đảm lấp đầy buffer</a>

<details>
<summary>Xem chi tiết</summary>

# Một lần `read` không bảo đảm lấp đầy buffer trong Java

Trong **luồng byte** (Java I/O), kích thước mảng đệm là *sức chứa tối đa của một lần đọc*, không phải số byte chắc chắn nhận được. Khi gọi `InputStream.read(byte[])` hoặc `InputStream.read(byte[], off, len)`, hãy luôn dùng **giá trị trả về** để biết có bao nhiêu byte hợp lệ. Hiểu sai điểm này dễ dẫn đến mất dữ liệu, chèn byte cũ hoặc byte `0` vào kết quả, giải mã văn bản hỏng và lỗi khi đọc giao thức mạng.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích vì sao `read` trả về ít byte hơn số byte yêu cầu dù luồng chưa kết thúc.
- Phân biệt `n > 0`, `n == -1` và trường hợp `len == 0` theo hợp đồng của `InputStream`.
- Xử lý đúng `buffer[0..n)` hoặc `buffer[off..off+n)`; không xem phần còn lại của buffer là dữ liệu mới.
- Viết vòng lặp đọc đến EOF, đọc đúng N byte, hoặc đọc theo khung thông điệp.
- Chọn giữa `read`, `readNBytes`, `DataInputStream.readFully` và `readAllBytes` theo nhu cầu.

## 2. Hợp đồng của `InputStream.read`

Ba biến thể thường gặp:

~~~java
int one = input.read();                     // Một byte 0..255, hoặc -1 nếu EOF
int count = input.read(buffer);             // Tối đa buffer.length byte
int count2 = input.read(buffer, off, len);  // Tối đa len byte từ vị trí off
~~~

Với `read(byte[], off, len)` của một triển khai `InputStream` tuân thủ hợp đồng Java:

| Kết quả | Ý nghĩa | Xử lý |
| --- | --- | --- |
| `1..len` nếu `len > 0` | Số byte đã đọc; **có thể nhỏ hơn** `len` | Chỉ dùng `buffer[off]` đến `buffer[off + count - 1]` |
| `-1` | Đã đến **end of stream (EOF)** trước khi đọc byte nào | Kết thúc vòng lặp hoặc báo thiếu dữ liệu theo giao thức |
| `0` nếu `len == 0` | Yêu cầu đọc 0 byte | Không có dữ liệu mới |
| Ngoại lệ `IOException` | Lỗi trong quá trình I/O | Xử lý/đẩy ngoại lệ; không coi là EOF |

Khi `len > 0`, hợp đồng của `InputStream.read(byte[], off, len)` quy định hoặc đọc được ít nhất một byte, hoặc trả về `-1` khi EOF; cách diễn đạt này **không áp dụng nguyên xi cho mọi API đọc dữ liệu**. Chẳng hạn, `ReadableByteChannel.read(ByteBuffer)` của kênh ở chế độ non-blocking có thể trả về `0` dù vẫn còn dữ liệu sẽ đến sau.

Một lệnh gọi `read(buffer)` có thể chặn (block) để đợi **một phần dữ liệu**, nhưng điều đó **không có nghĩa** nó sẽ chờ đến khi đầy `buffer`. Tùy nguồn và triển khai, lệnh gọi có thể trả về ngay khi đọc được một số byte, kể cả khi tổng dữ liệu của luồng còn rất nhiều.

**Quan trọng:** buffer dài 4096 byte, `read` trả về `37` thì chỉ **37 byte** mới đọc là hợp lệ. 4059 vị trí còn lại không thuộc lần đọc ấy, dù chúng đang chứa giá trị `0` hoặc byte từ vòng trước.

## 3. Vì sao đọc thiếu buffer là bình thường?

Giả sử chương trình có `byte[] buffer = new byte[1024]` nhưng nguồn chỉ cung cấp 3 byte tại thời điểm hoàn thành thao tác đọc. Hàm có quyền trả về `3`; những lần gọi tiếp theo có thể tiếp tục lấy byte còn lại.

Một số tình huống:

- **Socket TCP:** byte có thể đến qua nhiều đợt; TCP truyền một *dòng byte*, không giữ ranh giới các lần `write` của bên gửi. Một lần `read` không tương ứng chắc chắn với một lần gửi hay một thông điệp.
- **Luồng qua pipe hoặc filter:** phần dữ liệu hiện có hoặc cách lớp bọc triển khai `read` có thể khiến lần đọc chỉ trả về một đoạn.
- **File và buffered stream:** kết quả có thể bằng toàn bộ kích thước yêu cầu trong nhiều lần chạy, nhưng đó là hành vi quan sát được, **không phải điều kiện nên dựa vào**.
- **Điểm cuối thực sự:** nếu dữ liệu còn 7 byte mà buffer dài 100 byte, lần đọc có thể trả về 7; lần tiếp theo trả `-1`.

Cũng vì vậy, không thể suy ra “`count < buffer.length` nghĩa là EOF”. **Chỉ `-1`** báo EOF cho phương thức đọc mảng của `InputStream` với độ dài dương; còn trong trường hợp giao thức cố định N byte, EOF đến sớm là lỗi thiếu dữ liệu.

## 4. Ví dụ chạy được: một nguồn luôn trả tối đa 2 byte mỗi lần

`ByteArrayInputStream` tự nó thường đọc đủ phần dữ liệu đang có. Để thấy **short read** một cách lặp lại và không phụ thuộc thời điểm mạng, ví dụ sau đặt một `FilterInputStream` chỉ cho phép tối đa 2 byte ở mỗi lần đọc mảng.

Tạo file **`PartialReadDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PartialReadDemo {
    static final class TwoAtATimeInputStream extends FilterInputStream {
        TwoAtATimeInputStream(InputStream source) {
            super(source);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            return super.read(b, off, Math.min(len, 2));
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] source = "ABCDEFG".getBytes(StandardCharsets.US_ASCII);

        try (InputStream input =
                     new TwoAtATimeInputStream(new ByteArrayInputStream(source))) {
            byte[] buffer = new byte[5];
            int count;

            while ((count = input.read(buffer)) != -1) {
                String chunk = new String(
                        buffer, 0, count, StandardCharsets.US_ASCII);
                System.out.printf("count=%d; data=%s%n", count, chunk);
            }
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac PartialReadDemo.java
java PartialReadDemo
~~~

Kết quả chính xác:

~~~text
count=2; data=AB
count=2; data=CD
count=2; data=EF
count=1; data=G
~~~

Mảng có **5 ô**, nhưng các lần đọc lần lượt nhận `2, 2, 2, 1` byte. Luồng chỉ thực sự kết thúc khi vòng lặp gọi `read` thêm một lần và nhận `-1`. Đoạn mã dùng `new String(buffer, 0, count, US_ASCII)` vì nội dung kiểm thử là ASCII và chỉ giải mã những byte thực sự vừa nhận.

Trong stream bọc, `Math.min(len, 2)` chỉ thay đổi **số byte tối đa được yêu cầu**, còn `Objects.checkFromIndexSize` vẫn kiểm tra ranh giới của yêu cầu gốc. Đây là mô hình minh họa có chủ đích, không phản ánh số byte tối đa cố định của socket/file trong thực tế.

## 5. Lỗi phổ biến: bỏ qua số byte `read` đã trả về

**Sai:**

~~~java
byte[] buffer = new byte[1024];
int count = input.read(buffer);
if (count != -1) {
    output.write(buffer); // SAI: ghi cả 1024 byte
}
~~~

Nếu chỉ nhận 10 byte, `output.write(buffer)` vẫn ghi đủ 1024 byte. Đoạn dư có thể là byte `0` hoặc giá trị cũ từ lần trước. Đặc biệt nguy hiểm khi dùng lại cùng một buffer qua nhiều vòng.

**Đúng:**

~~~java
byte[] buffer = new byte[1024];
int count = input.read(buffer);
if (count != -1) {
    output.write(buffer, 0, count); // Chỉ ghi byte mới đọc
}
~~~

Các ví dụ trên là **mảnh mã** dùng hai biến `input` (`InputStream`) và `output` (`OutputStream`) đã được khai báo; chúng không phải chương trình độc lập. Khi dùng phương thức `read(buffer, off, len)`, phải xử lý đúng đoạn `buffer[off..off+count)`, không mặc định dữ liệu bắt đầu tại chỉ số 0.

Lỗi liên quan cũng thường xuất hiện khi viết:

~~~java
int count = input.read(buffer);
if (count < buffer.length) {
    // SAI: short read không chứng minh đã đến EOF
}
~~~

Hay:

~~~java
String text = new String(buffer, StandardCharsets.UTF_8);
// SAI: có thể giải mã byte chưa được ghi hoặc byte cũ
~~~

Dù có truyền `count`, không nên tự động chuyển **từng chunk bất kỳ** thành chuỗi UTF-8: một ký tự nhiều byte có thể bị tách qua hai lần `read`. Với dữ liệu văn bản, dùng `InputStreamReader`/`BufferedReader` cùng định nghĩa ranh giới bản ghi phù hợp, hoặc ghép đủ byte rồi giải mã. Một “chunk I/O” không nhất thiết là một ký tự, dòng hay thông điệp.

## 6. Mẫu chuẩn: đọc đến EOF khi chưa biết tổng độ dài

Khi định dạng quy định EOF chính là điểm kết thúc nội dung (ví dụ đọc toàn bộ file), lặp cho đến `-1` và chỉ xử lý `count` byte. Đoạn sau sao chép luồng theo từng khối, không cần chứa toàn bộ dữ liệu trong RAM:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamCopy {
    private StreamCopy() {}

    public static long copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;

        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
            total += count;
        }
        return total;
    }
}
~~~

Đây là lớp có thể biên dịch nhưng **không có `main`**; gọi `StreamCopy.copy(input, output)` từ nơi sở hữu hai luồng. Người gọi quyết định thời điểm đóng tài nguyên, thường bằng `try-with-resources`. Phương thức `InputStream.transferTo(OutputStream)` (Java 9+) cung cấp cách sao chép đến EOF tương tự khi không cần tự xử lý từng chunk.

**Đừng dùng vòng lặp chờ “đủ một buffer”** nếu mục tiêu là đọc đến EOF. Kích thước 8192 chỉ là lựa chọn hiệu năng; không quyết định độ dài nội dung hoặc ý nghĩa thông điệp.

Nếu cần chứa kết quả trong bộ nhớ để xử lý, có thể ghi các đoạn vào `ByteArrayOutputStream` rồi gọi `toByteArray()`. Cách này phù hợp khi dữ liệu có kích thước được kiểm soát; với nội dung lớn hoặc không tin cậy, ưu tiên xử lý streaming và đặt giới hạn.

## 7. Mẫu chuẩn: cần **đúng N byte**, kể cả sau nhiều lần `read`

Một giao thức có thể quy định phần đầu hoặc payload dài đúng N byte. Khi ấy, một `read(buffer)` là không đủ. Lặp đến khi tổng byte tích lũy bằng N hoặc báo lỗi nếu EOF đến trước.

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class FixedLengthReader {
    private FixedLengthReader() {}

    public static byte[] readExactly(InputStream input, int size)
            throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }

        byte[] result = new byte[size];
        int filled = 0;

        while (filled < size) {
            int count = input.read(result, filled, size - filled);
            if (count == -1) {
                throw new EOFException(
                        "Expected " + size + " bytes but got " + filled);
            }
            filled += count;
        }
        return result;
    }
}
~~~

Đây là lớp tiện ích có thể biên dịch, không có `main`. Trong mỗi lần lặp, vùng còn thiếu bắt đầu tại `filled` và dài `size - filled`. Với `InputStream` tuân thủ hợp đồng, khi độ dài yêu cầu dương, `count` là số dương hoặc `-1` nên `filled` tăng dần. Trường hợp `size == 0` sẽ trả về mảng rỗng mà không đọc.

**Giới hạn quan trọng:** nếu `size` do phía bên ngoài cung cấp, phải kiểm tra giới hạn kích thước trước khi cấp phát; `new byte[size]` có thể tốn nhiều RAM. Nếu ứng dụng có timeout/huỷ thao tác, hãy cấu hình phù hợp với nguồn dữ liệu (ví dụ socket timeout), vì một lần đọc có thể chặn trong lúc chờ dữ liệu.

### API có sẵn cho nhu cầu đọc đủ byte

| API | Dùng khi nào | Khi không đủ byte |
| --- | --- | --- |
| `InputStream.read(byte[], off, len)` | Chủ động xử lý từng chunk, streaming | Có thể trả về ít byte hơn `len` trong một lần |
| `InputStream.readNBytes(int len)` (Java 9+) | Muốn tối đa `len` byte dưới dạng mảng mới | Trả mảng ngắn hơn nếu EOF trước `len`; cần tự kiểm tra `length` |
| `InputStream.readNBytes(byte[], off, len)` (Java 9+) | Muốn tích lũy vào buffer có sẵn | Trả số byte thực đọc, có thể nhỏ hơn `len` khi EOF |
| `DataInputStream.readFully(byte[])` hoặc `readFully(byte[],off,len)` | **Bắt buộc** đủ N byte, nếu thiếu phải lỗi | Ném `EOFException` nếu EOF trước khi đủ |
| `InputStream.readAllBytes()` (Java 9+) | Đọc hết đến EOF và dữ liệu đủ nhỏ | Không có “N cần đủ”; có thể tốn lượng RAM lớn |
| `InputStream.transferTo(output)` (Java 9+) | Chuyển dữ liệu đến EOF sang output | Trả số byte chuyển; không tự biết ranh giới bản tin |

`readNBytes`/`readFully` có thể thực hiện **nhiều lần đọc nội bộ**, và vẫn có thể chặn hoặc ném `IOException`. Các phương thức này **không biến TCP thành giao thức có ranh giới thông điệp**; chúng chỉ đáp ứng một số chiến lược lấy byte.

### Ví dụ độc lập: `readNBytes` khác `readFully`

Tạo **`ExactLengthDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ExactLengthDemo {
    public static void main(String[] args) throws IOException {
        byte[] payload = "abc".getBytes(StandardCharsets.US_ASCII);

        try (InputStream input = new ByteArrayInputStream(payload)) {
            byte[] result = input.readNBytes(5);
            System.out.printf("readNBytes=%d; data=%s%n",
                    result.length,
                    new String(result, StandardCharsets.US_ASCII));
        }

        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            input.readFully(new byte[5]);
            System.out.println("readFully=ok");
        } catch (EOFException exception) {
            System.out.println("readFully=EOFException");
        }
    }
}
~~~

Chạy:

~~~bash
javac ExactLengthDemo.java
java ExactLengthDemo
~~~

Kết quả:

~~~text
readNBytes=3; data=abc
readFully=EOFException
~~~

Nguồn chỉ có ba byte `abc`. `readNBytes(5)` trả về ba byte thực có trước EOF; `readFully(new byte[5])` cần đúng năm byte, nên báo thiếu. Với `readFully`, nếu EOF xảy ra giữa chừng thì buffer đích có thể đã được ghi một phần; không sử dụng như payload hoàn chỉnh sau khi bắt ngoại lệ.

## 8. Phân biệt **buffer**, **EOF** và **ranh giới bản tin**

Hãy đặt ba câu hỏi khác nhau:

1. **Buffer chứa được bao nhiêu byte?** Đây là giới hạn lưu trữ cho một thao tác (`buffer.length` hoặc `len`).
2. **Lần `read` vừa trả về bao nhiêu byte?** Đây là số byte hợp lệ trong lần đọc (`count`).
3. **Ứng dụng biết một thông điệp kết thúc lúc nào?** Câu trả lời phải đến từ định dạng/giao thức: số byte cố định, trường độ dài, ký tự phân tách, hoặc EOF nếu quy ước cho phép.

Ví dụ: bên gửi TCP thực hiện hai lần ghi `"HELLO"` và `"WORLD"`. Bên nhận **không có bảo đảm** sẽ nhận đúng hai lần đọc, mỗi lần năm byte. Nó có thể nhận `"HE"`, `"LLOW"`, `"ORLD"`; hoặc `"HELLOWORLD"` trong một lần đọc. Vì vậy ứng dụng cần framing, ví dụ tiền tố độ dài và phần payload, chứ không dùng “một lần đọc = một message”.

Với thông điệp có **header 4 byte chỉ độ dài payload**:

- Đọc **đủ 4 byte header** trước, dùng đúng thứ tự byte (endianness) mà giao thức quy định.
- Giải mã độ dài, kiểm tra không âm, không vượt mức tối đa cho phép.
- Đọc **đúng số byte payload** đã khai báo; EOF đến sớm là thông điệp bị cắt.
- Nếu còn thông điệp tiếp theo trên cùng connection, tiếp tục đọc header; không giả định phần buffer còn trống hoặc một lần `read` kế tiếp bắt đầu đúng message mới.

Nếu định dạng dùng dấu xuống dòng, có thể sử dụng `BufferedReader.readLine()` khi dữ liệu thực sự là văn bản với charset xác định và lưu ý giới hạn kích thước dòng. Nếu là byte nhị phân, không tùy tiện diễn giải byte thành ký tự để tìm delimiter.

## 9. `available()` không giải quyết vấn đề

Cách làm sau **không đáng tin cậy** để xác định kích thước dữ liệu:

~~~java
int expected = input.available();
byte[] buffer = new byte[expected];
input.read(buffer);
~~~

`InputStream.available()` chỉ đưa ra **ước tính** số byte có thể đọc (hoặc bỏ qua) mà không bị chặn tại thời điểm gọi; nó **không phải tổng độ dài nội dung**, không dự báo các byte mạng sẽ đến sau, cũng không bảo đảm một lệnh `read` sẽ lấp đầy mảng. Với nhiều stream, giá trị có thể là `0` ngay cả khi EOF chưa đến. Không dựa vào `available() == 0` để kết luận hết dữ liệu.

## 10. Lưu ý khi dùng NIO channel hoặc văn bản

**NIO channels:** `ReadableByteChannel.read(ByteBuffer)` cũng trả số byte thực đọc hoặc `-1` tại EOF. Với channel ở chế độ non-blocking, `0` là kết quả bình thường; ứng dụng phải đợi sự kiện sẵn sàng (ví dụ `Selector`) hay quản lý tiến độ phù hợp. Không dùng vòng lặp bận chạy liên tục khi nhận `0`. Chú ý `ByteBuffer.position()`, `limit()`, `remaining()` và `flip()` trước khi chuyển sang đọc dữ liệu trong buffer.

**Văn bản UTF-8:** một ký tự có thể cần nhiều byte. Các mảng byte được chia tùy ý theo `read` không bảo toàn ranh giới ký tự. Với dữ liệu text hãy ghép đủ byte cho một đơn vị hoàn chỉnh rồi giải mã, hoặc sử dụng `InputStreamReader` để bộ giải mã duy trì trạng thái qua nhiều lần đọc. `Reader.read(char[])` cũng có thể trả về ít ký tự hơn độ dài mảng; việc dùng số lượng trả về vẫn cần thiết.

**Tài nguyên và lỗi:** đóng luồng khi sở hữu nó (thường dùng `try-with-resources`); không nuốt `IOException` rồi coi như EOF; tránh cấp phát theo độ dài do nguồn không tin cậy cung cấp; đặt giới hạn và timeout hợp lý cho luồng có thể chờ vô hạn.

## 11. Checklist khi review mã đọc byte

- [ ] Có lưu và sử dụng **`count`** từ mỗi lệnh `read`?
- [ ] Với `count > 0`, chỉ xử lý đúng đoạn `[off, off + count)`?
- [ ] Chỉ dừng khi nhận `-1` (nếu đọc đến EOF), hoặc khi đã đạt đúng số byte theo framing?
- [ ] Có tránh suy luận `count < len` nghĩa là EOF hay kết thúc bản tin?
- [ ] Nếu cần **đúng N byte**, đã lặp để tích lũy đủ hoặc dùng `readFully`, đồng thời xử lý EOF sớm?
- [ ] Có tránh dùng `available()` làm tổng kích thước, và tránh giải mã từng khúc UTF-8 tùy ý?
- [ ] Với socket/giao thức, có giới hạn kích thước payload và xác định rõ khung thông điệp?
- [ ] Với NIO non-blocking, có xử lý `read == 0` bằng cơ chế chờ phù hợp?

### Câu hỏi tự kiểm tra

**1.** Buffer dài 100, `read(buffer)` trả `12`. Bao nhiêu byte vừa đọc hợp lệ?  
**Đáp án:** 12 byte đầu. Không dùng 88 byte còn lại như dữ liệu mới.

**2.** `read(buffer)` trả `8` khi buffer dài 64. Có khẳng định EOF được không?  
**Đáp án:** Không. Cần đọc tiếp; với `InputStream`, `-1` mới báo EOF.

**3.** Header giao thức yêu cầu chính xác 8 byte; `read(buffer)` trả `3`. Phải làm gì?  
**Đáp án:** Tiếp tục đọc 5 byte còn thiếu vào đúng offset; nếu EOF đến trước khi đủ 8 byte thì báo lỗi, không giải mã header chưa hoàn chỉnh.

**4.** Khi nào dùng `readAllBytes`?  
**Đáp án:** Khi muốn đọc hết stream và kích thước được kiểm soát, phù hợp bộ nhớ. Với nguồn lớn/không biết trước, dùng streaming.

**Ghi nhớ:** `read` trả về **số byte thực nhận**, buffer thể hiện **sức chứa**, còn ranh giới dữ liệu nghiệp vụ do **giao thức** xác định. Ba đại lượng này độc lập với nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-005">Ghi đúng số byte thực tế đã đọc</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi đúng số byte thực tế đã đọc trong Java byte stream

Khi sao chép dữ liệu từ `InputStream` sang `OutputStream`, **chỉ ghi số byte mà lần gọi `read` vừa trả về**. Một buffer có thể chứa nhiều vị trí chưa được ghi mới, nên việc ghi toàn bộ buffer sau mỗi lần đọc có thể làm dữ liệu đầu ra bị thừa hoặc sai.

## 1. Kết quả cần nắm

- `read(buffer)` trả về **số byte thực sự đọc được trong lần gọi hiện tại**, không phải kích thước của buffer.
- Với buffer có độ dài lớn hơn 0, giá trị trả về thường là số dương từ `1` đến `buffer.length`; `-1` báo đã hết dữ liệu (EOF). Không được hiểu một lần đọc ngắn là EOF.
- Dùng `output.write(buffer, 0, bytesRead)` để ghi chính xác đoạn buffer hợp lệ, bắt đầu tại vị trí `0` và dài `bytesRead` byte.
- Tiếp tục đọc đến khi `read` trả `-1`. Không dùng `available()`, số byte mong đợi hoặc kích thước buffer làm tín hiệu kết thúc.

Đây là quy tắc áp dụng cho tệp nhị phân, ảnh, ZIP, dữ liệu mạng và cả văn bản khi xử lý ở mức byte.

## 2. `read` đã thay đổi phần nào của buffer?

Ví dụ một buffer dài 8 byte và nguồn chỉ còn 2 byte cuối:

```text
buffer.length = 8

Lần đọc trước:  [A B C D E F G H]     -> bytesRead = 8
Lần đọc cuối:   [I J C D E F G H]     -> bytesRead = 2
                 ^ ^ |
          chỉ 2 byte mới; C..H là dữ liệu cũ trong buffer
```

Hai byte mới hợp lệ là `I` và `J`. Sáu vị trí còn lại **không được phép ghi ra**, dù chúng vẫn chứa ký tự từ lần đọc trước. Sau khi `read` trả về `2`, lệnh `write(buffer)` sẽ ghi cả 8 byte, biến 10 byte đầu vào thành 16 byte đầu ra trong tình huống này.

Cần phân biệt ba đại lượng:

| Đại lượng | Ý nghĩa |
| --- | --- |
| `buffer.length` | Dung lượng bộ nhớ đệm được cấp phát |
| `bytesRead` | Số byte hợp lệ của **lần đọc hiện tại** |
| `totalBytes` | Tổng các giá trị `bytesRead` dương đã được ghi trong cả quá trình |

`read(buffer)` có thể trả ít hơn `buffer.length` dù nguồn **chưa** kết thúc. Điều này có thể xảy ra với luồng mạng, thiết bị, luồng bọc hoặc nguồn tự giới hạn số byte của mỗi lượt đọc.

## 3. Lỗi điển hình và cách sửa

**Sai: ghi toàn bộ buffer sau mỗi lượt đọc.**

```java
byte[] buffer = new byte[8192];
int bytesRead;

while ((bytesRead = input.read(buffer)) != -1) {
    output.write(buffer);  // Sai khi bytesRead < buffer.length
}
```

`OutputStream.write(byte[])` ghi toàn bộ mảng; phương thức này không biết bao nhiêu byte vừa được `read` đưa vào mảng. Dữ liệu cũ có thể bị lặp ở cuối, làm sai tệp đích, checksum hoặc nội dung giao thức.

**Đúng: ghi đúng đoạn vừa đọc.**

```java
byte[] buffer = new byte[8192];
int bytesRead;

while ((bytesRead = input.read(buffer)) != -1) {
    output.write(buffer, 0, bytesRead);
}
```

Đây là **đoạn mã minh họa nằm trong phương thức đã có `input` và `output`**, chưa phải một chương trình độc lập. Ý nghĩa các tham số của `write(buffer, 0, bytesRead)` là:

- `buffer`: mảng chứa các byte vừa nhận.
- `0`: vị trí bắt đầu của dữ liệu hợp lệ.
- `bytesRead`: số byte cần ghi, lấy trực tiếp từ kết quả `read`.

Nếu đọc bằng `input.read(buffer, offset, length)`, vùng vừa đọc hợp lệ sẽ bắt đầu từ `offset`; khi đó lệnh tương ứng là `output.write(buffer, offset, bytesRead)`. Không được mặc định offset luôn bằng 0 trong mọi chương trình.

## 4. Chương trình Java 17+ chạy được: chủ động tạo các lần đọc ngắn

`ByteArrayInputStream` trực tiếp thường có thể trả về đủ số byte yêu cầu. Để kiểm chứng rằng một lần `read` **không bắt buộc** lấp đầy buffer, ví dụ này bọc luồng nguồn và cố ý giới hạn mỗi lần đọc ở tối đa 3 byte, trong khi buffer có độ dài 8.

Lưu toàn bộ mã sau vào **`ReadActualBytesDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ReadActualBytesDemo {

    public static void main(String[] args) throws IOException {
        byte[] original = "ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8);

        try (InputStream input = new MaxChunkInputStream(
                 new ByteArrayInputStream(original), 3);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            long copied = copy(input, output);
            byte[] result = output.toByteArray();

            System.out.println("Total bytes: " + copied);
            System.out.println("Result: " +
                    new String(result, StandardCharsets.UTF_8));
            System.out.println("Same bytes: " +
                    Arrays.equals(original, result));
        }
    }

    static long copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[8];
        long total = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            total += bytesRead;
            System.out.println("Read: " + bytesRead);
        }

        output.flush();
        return total;
    }

    static final class MaxChunkInputStream extends FilterInputStream {
        private final int maxChunk;

        MaxChunkInputStream(InputStream source, int maxChunk) {
            super(source);
            if (maxChunk <= 0) {
                throw new IllegalArgumentException("maxChunk must be > 0");
            }
            this.maxChunk = maxChunk;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, Math.min(len, maxChunk));
        }
    }
}
```

Biên dịch và chạy (bắt buộc tên tệp trùng tên `public class`):

```bash
javac -encoding UTF-8 ReadActualBytesDemo.java
java ReadActualBytesDemo
```

**Stdout chính xác:**

```text
Read: 3
Read: 3
Read: 3
Read: 1
Total bytes: 10
Result: ABCDEFGHIJ
Same bytes: true
```

Diễn giải từng vòng lặp:

1. Buffer có 8 vị trí, nhưng wrapper chỉ cho đọc `3` byte đầu (A–C); chỉ ghi 3 byte.
2. Hai lần kế tiếp lần lượt đọc D–F và G–I, mỗi lần ghi đúng 3 byte.
3. Lần cuối còn J nên `read` trả `1`; chỉ ghi byte J. Lần kế tiếp trả `-1`, vòng lặp kết thúc.
4. Tổng byte ghi là `3 + 3 + 3 + 1 = 10`, bằng chiều dài nguồn. `Arrays.equals` chứng minh từng byte của đầu ra đúng với đầu vào.

Wrapper giới hạn dữ liệu chỉ để thử nghiệm tình huống đọc ngắn một cách có thể lặp lại. Trong thực tế, số byte của mỗi lượt đọc phụ thuộc vào `InputStream` cụ thể; tính đúng đắn của vòng lặp không phụ thuộc vào việc buffer được lấp đầy.

## 5. Áp dụng để sao chép tệp

Khi làm việc với tệp, hãy mở và đóng cả hai luồng bằng `try-with-resources`. Ví dụ phương thức sau có thể đặt trong một class cùng các `import` tương ứng:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

static long copyFile(Path source, Path destination) throws IOException {
    try (InputStream input = Files.newInputStream(source);
         OutputStream output = Files.newOutputStream(destination)) {
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalBytes += bytesRead;
        }

        return totalBytes;
    }
}
```

Đoạn trên là **phương thức minh họa**, chưa phải file Java độc lập: đưa các `import` lên đầu file class, rồi đặt `copyFile` bên trong class. `Files.newOutputStream(destination)` với tùy chọn mặc định sẽ tạo mới hoặc cắt ngắn tệp đích có sẵn; hãy chọn đường dẫn đích khác đường dẫn nguồn và quyết định rõ chính sách ghi đè trước khi chạy.

Việc đóng `OutputStream` bằng `try-with-resources` sẽ giải phóng tài nguyên và đảm bảo luồng được đóng ngay cả khi có lỗi. Nếu sao chép dữ liệu mạng hay dữ liệu nhị phân, không chuyển từng khối byte thành `String`: một ký tự UTF-8 có thể nằm trên ranh giới giữa hai lần `read`, và dữ liệu nhị phân tùy ý không phải văn bản.

## 6. Những giới hạn và phân biệt quan trọng

### EOF không đồng nghĩa với đọc thiếu buffer

Điều kiện kết thúc là `bytesRead == -1`. Giá trị `1`, `2` hoặc bất kỳ số dương nhỏ hơn kích thước buffer **vẫn là dữ liệu hợp lệ**. Không dừng vòng lặp sau một lần đọc ngắn, nếu không dữ liệu về sau sẽ bị mất.

Với `InputStream` thông thường, khi đọc bằng buffer có kích thước lớn hơn 0, phương thức blocking `read(byte[])` chờ có byte, EOF hoặc lỗi; `0` là kết quả hợp lệ khi yêu cầu độ dài `0` qua `read(b, off, 0)`. Nếu phải tích hợp một adapter khác có thể trả `0` liên tục, cần hiểu contract của adapter và tránh vòng lặp quay CPU vô hạn; ví dụ ở trên dùng đúng contract của `InputStream`.

### Một lần `write` và số byte ghi

`OutputStream.write(buffer, off, len)` được yêu cầu ghi đúng `len` byte hoặc báo lỗi bằng ngoại lệ; nó trả về `void` nên không có “số byte đã ghi” để cộng thêm. Trong vòng lặp trên, `totalBytes` được tăng bằng `bytesRead` sau khi lệnh ghi thành công. Không nhầm API này với một số API channel có thể trả số byte đã ghi, ví dụ `WritableByteChannel.write`.

### Khi cần *chính xác N byte*, thay vì đọc đến EOF

Nếu định dạng giao thức yêu cầu đọc đủ `N` byte (chẳng hạn header có độ dài cố định), một lần gọi `read(buffer)` không bảo đảm đủ `N`. Có thể dùng `DataInputStream.readFully(buffer)` để đọc đủ hoặc ném `EOFException` nếu nguồn kết thúc sớm; hoặc dùng `InputStream.readNBytes(N)`, kiểm tra độ dài mảng trả về và xử lý nếu thiếu. Đây là bài toán khác với sao chép toàn bộ luồng đến EOF.

### Có thể dùng API sao chép sẵn

Từ Java 9 có `input.transferTo(output)` để truyền toàn bộ byte còn lại từ một `InputStream` sang `OutputStream` và trả về tổng số byte đã truyền. API này hữu ích khi chỉ cần sao chép, nhưng việc tự viết vòng lặp giúp hiểu rõ quy tắc dùng kết quả `read` để xác định vùng dữ liệu được ghi.

## 7. Các lỗi thường gặp cần tránh

| Cách làm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| `output.write(buffer)` ở mọi vòng | Có thể ghi cả byte cũ hoặc byte chưa thuộc dữ liệu mới | `output.write(buffer, 0, bytesRead)` |
| Cho rằng `read` luôn trả `buffer.length` | Sai với nhiều loại luồng; mất hoặc hỏng dữ liệu | Dùng giá trị `bytesRead` thực tế |
| Thoát khi `bytesRead < buffer.length` | Cắt mất dữ liệu khi gặp short read | Chỉ kết thúc khi `bytesRead == -1` |
| Dùng `while (input.available() > 0)` | `available()` chỉ ước lượng byte có thể đọc không chặn, không phải EOF | Lặp theo kết quả `read` |
| Gọi `write(buffer, 0, buffer.length)` | Tương đương ghi cả buffer dù vừa đọc ít hơn | Truyền `bytesRead` vào tham số thứ ba |
| Coi buffer là chuỗi ký tự | Có thể cắt đôi ký tự hoặc làm sai dữ liệu nhị phân | Giữ nguyên byte trong thao tác copy |
| Quên đóng luồng file | Rò rỉ tài nguyên, dữ liệu đệm có thể chưa hoàn tất | Dùng `try-with-resources` |

## 8. Tự kiểm tra

1. Buffer có 8 byte, `read` lần lượt trả `8`, `2`, rồi `-1`. Tổng số byte cần ghi là bao nhiêu? **10 byte**, chia thành hai lệnh `write(buffer, 0, 8)` và `write(buffer, 0, 2)`.
2. Một lần `read` trả `3` trên buffer 8192 byte: có phải hết luồng? **Không**; tiếp tục gọi `read`.
3. Đầu ra có dung lượng lớn hơn đầu vào sau khi sao chép một file ảnh: cần kiểm tra gì trước? **Kiểm tra có chỗ nào gọi `write(buffer)` hoặc truyền nhầm `buffer.length` thay vì `bytesRead` hay không.**
4. Muốn sao chép nhị phân và đảm bảo đúng từng byte: nên kiểm tra bằng gì? **So sánh trực tiếp byte (ví dụ `Arrays.equals` với dữ liệu nhỏ) hoặc so sánh checksum/hash và kích thước với tệp lớn.**

**Quy tắc thực hành:** `read` quyết định *bao nhiêu byte vừa hợp lệ*; `write(buffer, offset, bytesRead)` chỉ được ghi đúng số byte đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-006">readNBytes và khác biệt với readAllBytes</a>

<details>
<summary>Xem chi tiết</summary>

# readNBytes và khác biệt với readAllBytes trong Java I/O

**InputStream** đọc luồng dữ liệu dạng byte. Hai phương thức **readNBytes(...)** và **readAllBytes()** đều trả về các byte được lấy từ *vị trí hiện tại* của luồng, nhưng giải quyết hai nhu cầu khác nhau: **đọc tối đa N byte** và **đọc toàn bộ phần còn lại đến EOF**. Chọn sai API có thể làm mất ranh giới bản tin, đọc thiếu dữ liệu, chờ vô hạn hoặc dùng quá nhiều bộ nhớ.

## 1. Những phương thức cần phân biệt

| API thuộc InputStream | Có từ Java | Trả về | Dừng khi nào |
| --- | --- | --- | --- |
| **byte[] readNBytes(int len)** | 11 | Mảng mới chứa 0 đến **len** byte | Đã đọc đủ **len**, gặp EOF hoặc lỗi I/O |
| **int readNBytes(byte[] b, int off, int len)** | 9 | Số byte thực tế ghi vào mảng có sẵn | Đã đọc đủ **len**, gặp EOF hoặc lỗi I/O |
| **byte[] readAllBytes()** | 9 | Mảng mới chứa tất cả byte còn lại | Gặp EOF hoặc lỗi I/O |
| **int read(byte[] b)** | 1.0 | Số byte đọc được hoặc **-1** khi EOF | Có thể trả về ngay khi đọc được một phần byte |

**EOF** (end of stream) nghĩa là cuối luồng; **len** là số byte yêu cầu, không phải số ký tự. Cả ba phương thức đầu tiên **không đóng InputStream**. Chúng tiêu thụ dữ liệu theo đúng thứ tự: byte đã đọc sẽ không xuất hiện lần nữa, trừ khi tạo luồng mới hoặc thiết lập và sử dụng reset trên luồng có hỗ trợ.

Một lần **read(buffer)** có thể trả về ít hơn kích thước buffer mặc dù dữ liệu vẫn còn. **readNBytes(...)** cố tiếp tục đọc cho đủ số byte được chỉ định; nó có thể trả về ít hơn nếu gặp EOF. Nếu chương trình cần *chính xác* N byte, phải tự kiểm tra kết quả hoặc dùng **DataInputStream.readFully(...)**.

## 2. Cơ chế của readNBytes(int len)

~~~java
byte[] firstFive = input.readNBytes(5);
~~~

Với **len = 5**, Java cố đọc 5 byte từ vị trí hiện tại. Nếu đủ 5 byte trước EOF, mảng trả về dài 5 và dữ liệu còn lại vẫn nằm trong luồng. Nếu chỉ còn 3 byte đến EOF, phương thức trả về mảng dài 3; **không tự ném EOFException vì thiếu 2 byte**. Nếu đang ở EOF, kết quả là mảng rỗng. Nếu **len = 0**, cũng trả về mảng rỗng nhưng không đọc. **len < 0** gây **IllegalArgumentException**.

Phương thức có thể chặn luồng thực thi trong lúc chờ cho đến khi đọc đủ **len**, gặp EOF hoặc gặp lỗi I/O. Một luồng mạng mới gửi 3 byte nhưng vẫn giữ kết nối mở có thể khiến **readNBytes(5)** tiếp tục chờ 2 byte nữa. Do đó, **len** chỉ giới hạn *số byte*, hoàn toàn không giới hạn *thời gian chờ*.

Mảng byte được trả về vừa bằng số byte thực nhận, không tự đệm 0 cho đủ kích thước. Tuy nhiên, nó vẫn cần bộ nhớ tỷ lệ với lượng dữ liệu đọc và có thể gặp **OutOfMemoryError** nếu lượng được yêu cầu quá lớn. Lỗi nguồn đọc có thể ném **IOException**.

## 3. Cơ chế của readAllBytes()

~~~java
byte[] remaining = input.readAllBytes();
~~~

Phương thức **readAllBytes()** đọc cho đến EOF, rồi trả về một mảng chứa **mọi byte còn lại**. Nếu đã đọc 4 byte của luồng 10 byte, sau đó gọi **readAllBytes()**, mảng chỉ có 6 byte. Phương thức không quay về đầu luồng.

Khác với **readNBytes(int len)**, **readAllBytes()** không có tham số để đặt giới hạn byte. Với file lớn, nguồn không tin cậy hoặc luồng vô hạn, việc dồn toàn bộ dữ liệu vào RAM có thể gây áp lực bộ nhớ hoặc **OutOfMemoryError**. Với socket giữ mở, lời gọi có thể tiếp tục chờ cho tới khi nhận EOF dù một phần dữ liệu đã đến.

**Không dùng available() để đo kích thước toàn bộ luồng**: nó chỉ biểu thị ước lượng byte có thể đọc mà không chặn lúc đó, không phải tổng byte còn lại. Cả hai API đều khác với kiểm tra dữ liệu “đang sẵn có”.

## 4. So sánh theo tình huống thực tế

| Câu hỏi | **readNBytes(int len)** | **readAllBytes()** |
| --- | --- | --- |
| Có giới hạn lượng dữ liệu theo tham số? | Có: tối đa **len** byte | Không |
| Có đọc tiếp cho đến EOF dù đã đủ N byte? | Không | Có |
| Nếu EOF đến sớm? | Trả mảng có thể ngắn hơn **len** | Trả toàn bộ byte đã lấy được |
| Luồng giữ mở khi chưa đủ dữ liệu? | Có thể chặn để chờ đủ N | Có thể chặn để chờ EOF |
| Phù hợp đọc header/prefix N byte? | Có | Không, vì sẽ lấy cả phần còn lại |
| Phù hợp đọc một file nhỏ, hữu hạn? | Nếu chỉ cần phần đầu hoặc N byte | Có, nếu chấp nhận chi phí RAM |
| Phù hợp dữ liệu lớn không rõ độ dài? | Dùng từng khối có giới hạn | Nên tránh |
| Có đóng luồng sau lời gọi? | Không | Không |

Nếu cần đọc một khung dữ liệu có độ dài được khai báo trong header, **readNBytes(expectedLength)** hữu ích vì có thể dừng ở ranh giới đó. Nếu cần chính xác độ dài, **bắt buộc kiểm tra bytes.length**. Nếu mục tiêu là lấy toàn bộ một tài nguyên nhỏ, có EOF rõ ràng, **readAllBytes()** tiện hơn.

## 5. Ví dụ Java 17+ chạy được: lượng byte đọc và vị trí luồng

Lưu thành **ReadNBytesDemo.java**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ReadNBytesDemo {
    public static void main(String[] args) throws IOException {
        byte[] source = "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII);

        // Đọc 4 byte, sau đó đọc phần còn lại.
        try (InputStream in = new ByteArrayInputStream(source)) {
            byte[] prefix = in.readNBytes(4);
            byte[] remaining = in.readAllBytes();
            System.out.println("prefix=" +
                    new String(prefix, StandardCharsets.US_ASCII));
            System.out.println("remaining=" +
                    new String(remaining, StandardCharsets.US_ASCII));
        }

        // Yêu cầu nhiều byte hơn số byte đang có đến EOF.
        try (InputStream in = new ByteArrayInputStream(
                "XYZ".getBytes(StandardCharsets.US_ASCII))) {
            byte[] five = in.readNBytes(5);
            byte[] afterEnd = in.readNBytes(2);
            System.out.println("requested=5, actual=" + five.length);
            System.out.println("bytes=" +
                    new String(five, StandardCharsets.US_ASCII));
            System.out.println("afterEOF=" + afterEnd.length);
        }

        // Biến thể ghi vào buffer có sẵn, offset = 1.
        try (InputStream in = new ByteArrayInputStream(
                "123456".getBytes(StandardCharsets.US_ASCII))) {
            byte[] buffer = new byte[4];
            int count = in.readNBytes(buffer, 1, 3);
            System.out.println("buffer count=" + count + ", segment=" +
                    new String(buffer, 1, count, StandardCharsets.US_ASCII));
            System.out.println("tail=" +
                    new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ReadNBytesDemo.java
java ReadNBytesDemo
~~~

Kết quả:

~~~text
prefix=ABCD
remaining=EFGHIJ
requested=5, actual=3
bytes=XYZ
afterEOF=0
buffer count=3, segment=123
tail=456
~~~

Luồng đầu chứa “ABCDEFGHIJ”; **readNBytes(4)** lấy “ABCD”, rồi **readAllBytes()** chỉ lấy “EFGHIJ”. Luồng “XYZ” chỉ có 3 byte, nên **readNBytes(5)** trả về mảng dài 3, và lần sau ở EOF trả về mảng dài 0. Với buffer dài 4, cách gọi **readNBytes(buffer, 1, 3)** ghi “123” vào các phần tử từ chỉ số 1 đến 3; 3 byte “456” vẫn còn.

## 6. readNBytes(byte[] b, int off, int len): điểm dễ nhầm với read

Biến thể này nhận mảng đích có sẵn và trả về **int count** (số byte vừa ghi), tránh phải nhận một mảng mới cho mỗi lần đọc. Các vị trí chứa dữ liệu mới là **b[off]** đến **b[off + count - 1]**. Những phần tử khác có thể vẫn chứa dữ liệu cũ; không xử lý toàn bộ **len** phần tử khi **count < len**.

Điều kiện biên và ngoại lệ:

- Mảng **b** bằng null: **NullPointerException**.
- **off < 0**, **len < 0** hoặc **len > b.length - off**: **IndexOutOfBoundsException**.
- **len = 0**: trả về 0, không đọc gì.
- EOF trước khi có byte nào: trả về **0**, kể cả khi **len > 0**.
- EOF sau khi nhận một phần: trả về số byte thực đọc, có thể nhỏ hơn **len**.
- Lỗi I/O: có thể ném **IOException** sau khi đã sửa một phần mảng đích.

Điểm khác quan trọng: **read(...)** báo EOF bằng **-1** khi yêu cầu có độ dài dương; **readNBytes(buffer, off, len)** trả về **0** ở EOF. Vì thế, một vòng lặp dùng **while (n != -1)** vốn dành cho **read(...)** không thể bê nguyên sang biến thể **readNBytes**: dễ lặp vô hạn ở EOF.

Nếu cần đọc theo khối kích thước cố định trong một luồng hữu hạn, có thể gọi **readNBytes(buffer, 0, buffer.length)** và xử lý đúng đoạn từ chỉ số 0 đến **count - 1**. Với **count == 0** và kích thước yêu cầu dương, luồng đã tới EOF trong điều kiện hoạt động thông thường.

## 7. Đọc đúng N byte cho giao thức: kiểm tra EOF

Một bản tin có thể ghi độ dài payload trong header. Nếu header nói có 5 byte nhưng người gửi chỉ truyền 3 byte rồi đóng luồng, **readNBytes(5)** trả về 3 byte. Coi đó là bản tin hợp lệ là lỗi ứng dụng. Cần so độ dài và ném **EOFException** khi dữ liệu bị cắt.

Ví dụ phương thức đọc payload (lưu thành **PayloadReader.java**; lớp biên dịch độc lập, nhưng chưa có **main** để chạy riêng):

~~~java
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class PayloadReader {
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private PayloadReader() {
    }

    public static byte[] readPayload(InputStream in, int expectedBytes)
            throws IOException {
        // Chặn độ dài âm/quá lớn, đặc biệt khi lấy từ input bên ngoài.
        if (expectedBytes < 0 || expectedBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid payload length");
        }

        byte[] payload = in.readNBytes(expectedBytes);
        if (payload.length != expectedBytes) {
            throw new EOFException(
                    "Truncated payload: expected " + expectedBytes +
                    " bytes, got " + payload.length);
        }
        return payload;
    }
}
~~~

**MAX_PAYLOAD_BYTES** đặt ngưỡng để không cấp phát tùy tiện khi độ dài do bên ngoài cung cấp. Với nguồn mạng, cần thêm **timeout** theo giao thức/kết nối; đặt ngưỡng byte không ngăn một kết nối đang mở nhưng ngừng gửi dữ liệu làm thao tác đọc chờ lâu.

Một lựa chọn cho yêu cầu **phải đọc chính xác N byte** là **DataInputStream.readFully(byte[])**: nó ném **EOFException** nếu không đọc đủ trước EOF. **readNBytes(...)** chủ ý trả về dữ liệu ngắn hơn khi EOF, vì vậy hợp đồng sử dụng khác nhau.

## 8. Bộ nhớ, dữ liệu lớn và mã hóa ký tự

**InputStream** xử lý *byte*, không phải *ký tự*. UTF-8 có thể mã hóa một ký tự bằng nhiều byte; lấy đúng 5 byte chưa chắc lấy đúng 5 ký tự. Không nên giải mã từng fragment tùy ý khi ranh giới có thể cắt ngang một ký tự UTF-8. Đối với dữ liệu văn bản, dùng charset tường minh như **StandardCharsets.UTF_8**, hoặc đọc qua **InputStreamReader** khi thích hợp.

**readAllBytes()** thích hợp cho dữ liệu **nhỏ, hữu hạn và có giới hạn thực tế**. File vài GB hay luồng vô hạn nên xử lý tăng dần theo buffer, tránh dồn toàn bộ vào mảng. Ví dụ sao chép file bằng luồng (Java 17+):

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyFileDemo {
    public static void main(String[] args) throws IOException {
        Path source = Path.of("input.bin");
        Path target = Path.of("output.bin");

        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(target)) {
            long copied = in.transferTo(out);
            System.out.println("Copied bytes: " + copied);
        }
    }
}
~~~

Chương trình cần có file **input.bin** trước khi chạy; số byte in ra tùy kích thước file thực tế. **transferTo(OutputStream)** đọc đến EOF và ghi sang đích mà không yêu cầu mã ứng dụng gom toàn bộ nội dung vào một **byte[]** lớn. Đối với luồng mạng hoặc nguồn chậm, thời gian chờ và lỗi đọc/ghi vẫn phải được xử lý.

## 9. Những lỗi thường gặp

1. **Ngộ nhận readNBytes(N) luôn trả về N byte.** Độ dài có thể ngắn hơn khi EOF; hãy kiểm tra độ dài nếu yêu cầu đủ khung dữ liệu.
2. **Ngộ nhận readAllBytes() đọc lại từ đầu.** Nó đọc từ vị trí hiện tại; các byte đã tiêu thụ trước đó không tự quay lại.
3. **Dùng readAllBytes() cho nguồn không giới hạn.** Dữ liệu quá lớn có thể chiếm RAM đáng kể hoặc gây OOME; đặt ngưỡng và đọc theo khối.
4. **Nhầm giữa read và readNBytes(buffer, ...).** EOF của **read(...)** được báo bằng -1; EOF của biến thể **readNBytes(buffer, ...)** được báo bằng 0.
5. **Dùng available() như kích thước file/luồng.** Đây không phải tổng byte còn lại và không phải tín hiệu EOF đáng tin.
6. **Cho rằng readNBytes() có timeout.** Số byte tối đa không phải thời gian tối đa; luồng mạng vẫn có thể chặn.
7. **Giải mã UTF-8 theo mảnh byte cắt tùy ý.** Mảnh byte có thể thiếu một phần ký tự; cần quản lý ranh giới hoặc dùng bộ giải mã phù hợp.
8. **Quên đóng tài nguyên.** Hai phương thức không tự gọi close; dùng **try-with-resources** khi sở hữu luồng.

## 10. Tự kiểm tra kiến thức

- Đọc **readNBytes(4)** trên một luồng 10 byte, rồi **readAllBytes()**: số byte lần lượt là **4** và **6**.
- Đọc **readNBytes(7)** trên luồng còn 2 byte rồi EOF: kết quả dài **2**, không tự ném EOFException.
- Đọc **readAllBytes()** từ socket vẫn mở: có thể chặn chờ **EOF**, dù một số byte đã được gửi.
- Đọc frame bắt buộc đủ 8 byte: dùng **readNBytes(8)** và kiểm tra **length == 8** (hoặc **DataInputStream.readFully**), kèm giới hạn độ dài và timeout khi cần.
- Đọc 3 byte vào **buffer[1..3]** bằng **readNBytes(buffer, 1, 3)**: chỉ xử lý **count** byte thực đọc; EOF của API này trả 0.

**Kết luận:** **readNBytes** giới hạn lượng byte theo **N**, còn **readAllBytes** lấy **tất cả byte còn lại tới EOF**. Chúng khác nhau về ranh giới đọc, thời điểm dừng, bộ nhớ cần dùng và tình huống áp dụng.

### Tham khảo

- [Oracle Java SE 11 — InputStream](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/InputStream.html): hợp đồng **readNBytes**, **readAllBytes** và mốc **Since**.
- [Oracle Java SE 11 — DataInputStream](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/DataInputStream.html): **readFully** và **EOFException**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-007">skip, skipNBytes và giới hạn khi bỏ qua dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# `skip`, `skipNBytes` và giới hạn khi bỏ qua dữ liệu trong luồng byte Java

Trong Java I/O, đôi khi chương trình cần bỏ qua một đoạn dữ liệu để đọc phần phía sau: bỏ qua header cố định, bỏ qua một trường chưa cần xử lý hoặc tiến tới payload. Hai phương thức thường gặp trên `java.io.InputStream` là `skip(long n)` và `skipNBytes(long n)`. Chúng đều *tiêu thụ/bỏ qua byte từ vị trí hiện tại* nhưng khác nhau về cam kết hoàn thành, kết quả trả về và cách báo lỗi.

Bài này thuộc nhóm **luồng byte** (`InputStream`). Mọi con số `n` bên dưới đều tính bằng **byte**, không phải ký tự, số dòng hay số phần tử Java.

## 1. Hiểu đúng việc “bỏ qua”

Hãy hình dung luồng có các byte `A B C D E F G` và con trỏ đọc đang ở `A`:

```text
Trước:  [A] B  C  D  E  F  G
Bỏ 3 byte:
Sau:     A  B  C [D] E  F  G
```

Ba byte `A`, `B`, `C` đã được tiêu thụ; lần `read()` kế tiếp trả về byte `D`. Dữ liệu đã bỏ qua không được tự động trả lại. Cả hai phương thức không đóng luồng, không trả về nội dung đã bỏ qua và không tạo một vị trí đọc ngẫu nhiên có thể nhảy lùi như `FileChannel.position(...)`.

“Bỏ qua” không đồng nghĩa với “seek nhanh”. Cài đặt `InputStream.skip` mặc định có thể **đọc rồi loại bỏ** byte bằng bộ đệm tạm; một số lớp con dùng cơ chế hiệu quả hơn. Với luồng mạng, luồng giải nén hoặc luồng bọc, việc bỏ qua vẫn có thể phải đọc/xử lý dữ liệu bên dưới và có thể chặn (block).

## 2. `skip(long n)`: yêu cầu bỏ qua, nhận về số byte thực tế

Chữ ký API:

```java
public long skip(long n) throws IOException
```

- `n` là số byte mong muốn bỏ qua.
- Giá trị trả về `long` cho biết **số byte thực tế đã bỏ qua**. Nó có thể nhỏ hơn `n`, thậm chí bằng `0`; không được mặc định rằng kết quả luôn bằng `n`.
- Nguyên nhân bỏ qua thiếu có thể là hết dữ liệu (EOF), đặc tính của lớp con, hoặc điều kiện I/O khác. `skip(n) == 0` **không đủ** để kết luận chắc chắn đã EOF.
- Với `n <= 0`, triển khai mặc định của `InputStream` bỏ qua 0 byte và trả về `0`; lớp con có thể quy định khác cho số âm. Mã ứng dụng nên từ chối `n < 0` nếu nó biểu thị độ dài không hợp lệ.
- Khi xảy ra lỗi I/O, phương thức có thể ném `IOException`. Không giả định vị trí luồng giữ nguyên sau lỗi.

**Quy tắc:** Nếu chỉ cần bỏ qua *tối đa* `n` byte, có thể chấp nhận số lượng ít hơn; luôn dùng giá trị trả về để tính số byte đã thực sự tiêu thụ.

Ví dụ trên `ByteArrayInputStream` chứa ba byte, `skip(5)` chỉ bỏ qua được ba byte và trả về `3`. Lần đọc tiếp theo trả về `-1` vì đã hết luồng. Đây là hành vi của ví dụ cụ thể; không dùng kết quả đó làm cam kết cho mọi loại `InputStream`.

## 3. `skipNBytes(long n)`: yêu cầu bỏ qua đủ số byte

Chữ ký API (có từ **Java 12**, sử dụng được trong Java 17+):

```java
public void skipNBytes(long n) throws IOException
```

- Nếu hoàn thành bình thường, phương thức đã bỏ qua đúng `n` byte theo hợp đồng API; không có giá trị trả về.
- Nếu gặp EOF trước khi bỏ qua đủ `n` byte, nó ném `EOFException` (lớp con của `IOException`).
- Nó cũng có thể ném `IOException` nếu I/O lỗi, kết quả `skip` bên dưới bất thường hoặc không thể di chuyển tới vị trí cần thiết.
- Trong triển khai mặc định, `n == 0` hoặc `n < 0` không bỏ qua byte; các lớp con có thể xử lý giá trị âm khác. Nên xác thực `n >= 0` tại ranh giới nghiệp vụ để không im lặng chấp nhận độ dài sai.
- Phương thức có thể chặn cho tới khi bỏ qua đủ byte, gặp EOF hoặc có lỗi; nó không đặt giới hạn thời gian.

Cách hoạt động đáng nhớ của **triển khai mặc định**: gọi `skip(remaining)` lặp lại. Nếu `skip` trả `0`, nó thử `read()` một byte để có tiến triển hoặc phát hiện EOF. Nếu `skip` trả số âm hoặc lớn hơn số byte còn phải bỏ qua, nó ném `IOException`. Lớp con có thể ghi đè để tối ưu.

**Quy tắc:** Dùng `skipNBytes(n)` khi cấu trúc dữ liệu đòi hỏi phải bỏ qua **đúng** `n` byte trước khi đọc phần tiếp theo. Nếu luồng ngắn, lỗi được biểu thị rõ bằng ngoại lệ.

## 4. So sánh nhanh

| Tiêu chí | `skip(n)` | `skipNBytes(n)` |
| --- | --- | --- |
| Hợp đồng | Bỏ qua tối đa theo khả năng triển khai; có thể thiếu | Bỏ qua đủ `n` byte hoặc báo lỗi |
| Giá trị trả về | `long`: số byte đã bỏ qua | `void` |
| EOF trước khi đủ | Có thể trả số nhỏ hơn yêu cầu | Ném `EOFException` |
| Trả `0` khi còn muốn bỏ qua | Có thể xảy ra; bên gọi cần quyết định tiếp tục thế nào | Mặc định thử `read()` để tiến thêm hoặc xác định EOF |
| Dùng khi | Bỏ qua tùy khả năng, cần biết số thực tế | Header/trường bắt buộc đủ độ dài |
| Phiên bản Java | Có từ các phiên bản Java đầu tiên | Có từ Java 12 |

Hai phương thức cùng tiêu thụ luồng tại **vị trí hiện tại**. Không dùng `skipNBytes` trên Java 8/11 vì API này chưa tồn tại ở những phiên bản đó.

## 5. Ví dụ Java 17+ có thể chạy trực tiếp

Lưu thành `SkipExample.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SkipExample {
    public static void main(String[] args) throws IOException {
        byte[] sevenBytes = "ABCDEFG".getBytes(StandardCharsets.US_ASCII);

        // skip trả về số byte thực tế đã bỏ qua.
        try (InputStream in = new ByteArrayInputStream(sevenBytes)) {
            long skipped = in.skip(3);
            System.out.println("skip(3): " + skipped
                    + ", next: " + (char) in.read());
        }

        // skipNBytes đảm bảo đủ độ dài nếu trả về bình thường.
        try (InputStream in = new ByteArrayInputStream(sevenBytes)) {
            in.skipNBytes(3);
            System.out.println("skipNBytes(3) next: " + (char) in.read());
        }

        // Nguồn chỉ có 3 byte, nhưng yêu cầu bỏ qua 5 byte.
        try (InputStream in = new ByteArrayInputStream(new byte[] {10, 20, 30})) {
            System.out.println("skip(5) on 3 bytes: " + in.skip(5));
            System.out.println("next at EOF: " + in.read());
        }

        try (InputStream in = new ByteArrayInputStream(new byte[] {10, 20, 30})) {
            try {
                in.skipNBytes(5);
                System.out.println("unexpected success");
            } catch (EOFException e) {
                System.out.println("skipNBytes(5): EOFException");
            }
        }

        // Lớp bọc này chủ động chỉ cho phép mỗi lần skip tối đa 1 byte.
        try (InputStream in = new OneByteSkippingInputStream(
                new ByteArrayInputStream(sevenBytes))) {
            System.out.println("single skip(3): " + in.skip(3));
            System.out.println("next after one skip: " + (char) in.read());
        }

        try (InputStream in = new OneByteSkippingInputStream(
                new ByteArrayInputStream(sevenBytes))) {
            in.skipNBytes(3);
            System.out.println("next after exact skip: " + (char) in.read());
        }
    }

    private static final class OneByteSkippingInputStream
            extends FilterInputStream {
        OneByteSkippingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public long skip(long n) throws IOException {
            return super.skip(Math.min(n, 1L));
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac SkipExample.java
java SkipExample
```

Stdout chính xác:

```text
skip(3): 3, next: D
skipNBytes(3) next: D
skip(5) on 3 bytes: 3
next at EOF: -1
skipNBytes(5): EOFException
single skip(3): 1
next after one skip: B
next after exact skip: D
```

Đoạn cuối cố ý dùng lớp bọc chỉ bỏ qua tối đa 1 byte mỗi lần gọi: `skip(3)` đơn lẻ chỉ tiến một byte nên lần đọc kế là `B`; `skipNBytes(3)` tiếp tục gọi `skip` cho tới khi đã bỏ qua đủ ba byte nên lần đọc kế là `D`. Đây là lý do không được viết `in.skip(3);` rồi mặc định rằng luồng chắc chắn đã tiến ba byte.

## 6. Khi cần số lượng thực tế nhưng muốn thử bỏ qua tới giới hạn

Nếu yêu cầu nghiệp vụ là “bỏ qua nhiều nhất `n` byte, đến EOF thì dừng và **trả tổng byte thực tế**”, một lệnh `skip(n)` riêng lẻ chưa đủ đáng tin do có thể bỏ qua thiếu. Có thể tự viết hàm lặp với biện pháp chống vòng lặp vô hạn khi `skip` trả về `0`.

Phương thức sau có thể đặt trong một lớp Java đang dùng `java.io.InputStream` / `java.io.IOException`:

```java
static long skipUpTo(InputStream in, long requested) throws IOException {
    if (requested < 0) {
        throw new IllegalArgumentException("requested must be >= 0");
    }

    long remaining = requested;

    while (remaining > 0) {
        long skipped = in.skip(remaining);

        if (skipped < 0 || skipped > remaining) {
            throw new IOException("Invalid skip result: " + skipped);
        }

        if (skipped > 0) {
            remaining -= skipped;
            continue;
        }

        // skip(remaining) == 0: thử tiêu thụ 1 byte để tiến lên.
        if (in.read() == -1) {
            break;  // EOF: đã bỏ qua được tối đa bao nhiêu thì trả về bấy nhiêu.
        }
        remaining--;
    }

    return requested - remaining;
}
```

Với nguồn `"ABCDE"`, gọi `skipUpTo(in, 3)` trả `3` và lần `read()` tiếp theo trả byte `D`. Gọi `skipUpTo(in, 8)` trên một luồng mới cùng dữ liệu trả `5` và luồng ở EOF. Mỗi vòng lặp hoặc giảm `remaining`, hoặc thoát, hoặc ném ngoại lệ; nhờ vậy không bị lặp vô hạn khi lớp con trả `0`.

Hàm này phù hợp khi thiếu dữ liệu là kết quả chấp nhận được. Nếu **thiếu byte là lỗi**, dùng `skipNBytes` để thể hiện trực tiếp ý định; đừng viết lại một vòng lặp không cần thiết. Với đầu vào từ mạng hoặc pipe, cả `skipNBytes` lẫn hàm tự viết đều có thể chờ dữ liệu; phải quản lý timeout/cancellation bằng cơ chế phù hợp với nguồn I/O.

## 7. Giới hạn và những hiểu nhầm quan trọng

### 7.1. Số byte khác số ký tự

`InputStream` thao tác trên byte thô. Ví dụ chuỗi `"é"` được mã hóa UTF-8 thường dài **2 byte**; `skip(1)` giữa dữ liệu UTF-8 có thể làm lần giải mã kế tiếp gặp phần giữa của một ký tự và sinh ký tự thay thế hoặc lỗi giải mã. Không dùng `skip` để nhảy “5 ký tự” trong tệp văn bản đa byte. Khi cần xử lý văn bản, hãy dùng `Reader` với charset xác định và đếm theo đơn vị ký tự tương ứng, hoặc thiết kế định dạng có ranh giới byte rõ ràng.

### 7.2. `skip` không chứng minh rằng đã đạt vị trí cần thiết

Đây là lỗi sai:

```java
in.skip(headerLength);             // Bỏ qua thiếu mà không biết!
int firstPayloadByte = in.read();   // Có thể vẫn đang ở giữa header.
```

Nếu header có độ dài bắt buộc, sau khi kiểm tra `headerLength >= 0`, dùng:

```java
in.skipNBytes(headerLength);
int firstPayloadByte = in.read();   // Chỉ chạy khi header đã được bỏ qua đủ.
```

Cả hai đoạn là **mảnh mã minh họa**: `in` là một `InputStream` hợp lệ, `headerLength` là số byte đã xác định và phương thức gọi phải xử lý `IOException`. Nếu file/protocol không có đủ header, đoạn thứ hai ném `EOFException` trước khi xử lý payload.

### 7.3. `available()` không phải “số byte còn lại trong file”

`available()` chỉ là **ước lượng byte có thể đọc hoặc bỏ qua mà không bị block**, không phải kích thước dữ liệu còn lại của mọi loại luồng. Nó có thể trả `0` trong khi dữ liệu sẽ đến sau đó (đặc biệt với luồng mạng). Vì thế không dùng `skip(in.available())` để “đi đến EOF”, cũng không dựa vào `available() == 0` để kết luận đã hết dữ liệu.

### 7.4. Ngoại lệ không tự động hoàn tác những byte đã tiêu thụ

Khi `skipNBytes(100)` phát sinh `EOFException` ở byte thứ 80, những byte đã tiêu thụ **không tự quay lại**. Sau một `IOException` vị trí/ trạng thái luồng có thể không còn đáng tin; xử lý lỗi ở mức bản ghi/giao thức, thường là ngừng parse và đóng luồng. Nếu cần đọc lại, `mark/reset` chỉ dùng khi `markSupported()` là `true` và còn thỏa điều kiện mark; không tự giả định mọi luồng hỗ trợ.

### 7.5. Kết quả và chi phí phụ thuộc lớp `InputStream`

- `ByteArrayInputStream` thao tác trên mảng byte đã nằm trong bộ nhớ; ví dụ ở trên có hành vi rõ ràng, tái lập được.
- `BufferedInputStream` có thể tận dụng dữ liệu đã đệm; việc bọc đệm không biến luồng thành thiết bị truy cập ngẫu nhiên.
- `FileInputStream` và `InputStream` của socket, ZIP hoặc luồng giải nén có cơ chế khác nhau. Không khẳng định `skip` luôn O(1), luôn không block hoặc luôn dừng chính xác tại ranh giới file.
- `skipNBytes` có thể nhiều lần gọi `skip`, và ở triển khai mặc định có thể đọc từng byte khi `skip` trả `0`. Đảm bảo về **số byte** khác với đảm bảo về **hiệu năng**.
- Không dùng `skip` để vượt qua kiểm tra tính hợp lệ của một trường dữ liệu bắt buộc kiểm tra nội dung, checksum hoặc ranh giới bản ghi. Bỏ qua byte chỉ di chuyển trong luồng; nó không xác thực dữ liệu.

### 7.6. Không lẫn `skipNBytes` với `readNBytes` hay seek

- `readNBytes(...)` **lấy dữ liệu vào bộ đệm/mảng**; `skipNBytes(...)` **loại bỏ** dữ liệu khi tiến lên.
- `skip(n)` trả số byte **đã bỏ qua**, trong khi `read()` trả dữ liệu hoặc `-1` ở EOF.
- Nếu cần truy cập vị trí bất kỳ trong tệp và biết offset tuyệt đối, cân nhắc `FileChannel.position(long)` / `SeekableByteChannel` thay vì đọc/bỏ qua tuần tự. Với ZIP hay luồng giải nén, vị trí trong byte đã giải nén không đồng nghĩa offset trong file nén.

## 8. Tình huống áp dụng: header dài cố định trước payload

Giả sử một bản tin nhị phân có 4 byte magic, 2 byte version, tiếp đến payload. Nếu bước này chỉ được phép bỏ qua đúng 6 byte header (vì header đã được xử lý/kiểm tra ở chỗ khác):

```java
static int readFirstPayloadByte(InputStream in) throws IOException {
    in.skipNBytes(6);
    return in.read();   // -1: không có payload sau header.
}
```

Với luồng có ít hơn 6 byte, `skipNBytes(6)` ném `EOFException`, giúp phân biệt **header bị cắt cụt** với **header đầy đủ nhưng payload rỗng** (`read() == -1`). Trong ứng dụng thật, nếu magic/version cần kiểm tra, phải **đọc và xác thực các byte header** thay vì bỏ qua chúng. Nếu độ dài header lấy từ đầu vào không tin cậy, xác thực không âm và giới hạn tối đa trước khi bỏ qua, để tránh chờ lâu hoặc đọc qua lượng dữ liệu không hợp lý.

## 9. Lỗi phổ biến và cách tránh

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Gọi `skip(n)` và bỏ qua giá trị trả về | Parser đọc lệch trường khi skip thiếu | Dùng `skipNBytes` hoặc cộng dồn số byte thực tế |
| Lặp `while (remaining > 0) remaining -= in.skip(remaining);` | Có thể lặp vô hạn khi `skip` trả `0` | Khi trả `0`, thử `read()` một byte hoặc dừng theo hợp đồng |
| Dùng `available()` làm tổng kích thước còn lại | Cắt thiếu dữ liệu trên stream/ socket | Dựa vào độ dài giao thức, EOF hoặc số byte đọc thực tế |
| Nhầm số ký tự Unicode với byte UTF-8 | Cắt giữa byte của một ký tự | Chọn đúng đơn vị và charset; xử lý text bằng API phù hợp |
| Coi `skipNBytes` là thao tác atomic/rollback | Sau EOF/IOException tiếp tục parse sai trạng thái | Xử lý lỗi, kết thúc bản ghi và đóng luồng khi cần |
| Dùng Java 8/11 nhưng gọi `skipNBytes` | Lỗi biên dịch do API chưa có | Yêu cầu Java 12+ hoặc triển khai vòng lặp có kiểm tra |
| Bỏ qua header cần xác thực | Bỏ lọt dữ liệu sai định dạng | Đọc và validate header trước khi xử lý payload |

## 10. Tự kiểm tra kiến thức

1. Một luồng thực tế chỉ có 4 byte, gọi `skip(10)` có luôn trả `4`? **Không.** `skip` có thể trả số ít hơn vì nhiều lý do; chỉ kết quả trả về của lần gọi mới cho biết số đã bỏ qua. Với `ByteArrayInputStream` bốn byte thì trả `4`.
2. `skip(6)` trả `2`; có thể đọc payload ngay không? **Không**, nếu cần bỏ qua đủ 6 byte; phải xử lý 4 byte còn lại hoặc dùng `skipNBytes(6)` ngay từ đầu.
3. `skipNBytes(5)` chỉ còn 3 byte đến EOF thì sao? Nó ném `EOFException`; ba byte có thể đã bị tiêu thụ.
4. `skip(4)` trả `0` có chắc EOF? **Không**. Không được chỉ dựa vào số `0` để kết luận.
5. Có nên dùng `skip(1)` để bỏ một ký tự UTF-8? **Không** khi chưa biết kích thước byte và ranh giới ký tự.
6. Khi nào dùng `skipNBytes`? Khi bản ghi/giao thức đòi hỏi **tiến đúng số byte xác định**, và thiếu byte phải được xem là lỗi.

## Tham khảo API

- [Java 17 — `InputStream.skip(long)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#skip(long)).
- [Java 17 — `InputStream.skipNBytes(long)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#skipNBytes(long)).

**Ghi nhớ:** `skip` cho biết *đã bỏ qua được bao nhiêu*; `skipNBytes` yêu cầu *bỏ qua đúng bao nhiêu hoặc báo lỗi*. Cả hai đều thao tác trên byte của luồng hiện tại, nên luôn kiểm tra độ dài, xử lý EOF/IOException và chọn cách truy cập dữ liệu phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-008">available không phải kích thước toàn bộ dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# `InputStream.available()` không phải kích thước toàn bộ dữ liệu

## Mục tiêu bài học

Khi làm việc với **luồng byte** trong Java, cần phân biệt lượng dữ liệu *có thể đọc ngay lúc này* với tổng dung lượng của nguồn dữ liệu. Sau bài này, bạn có thể:

- Giải thích đúng ý nghĩa của `InputStream.available()` và lý do kết quả của nó không phải độ dài toàn bộ stream.
- Nhận ra vì sao `available() == 0` **không đồng nghĩa** đã hết dữ liệu (EOF), còn `available() > 0` **không cho biết** toàn bộ dữ liệu dài bao nhiêu.
- Đọc dữ liệu bằng `read()`, `read(byte[])` hoặc những API đọc đến EOF phù hợp; chọn cách xác định kích thước tệp khi thực sự có tệp.
- Tránh cấp phát bộ đệm, điều kiện lặp và logic giao thức dựa vào `available()`.

## 1. `available()` thực sự trả về điều gì?

`java.io.InputStream` khai báo:

```java
public int available() throws IOException
```

Giá trị trả về là **ước lượng số byte có thể đọc hoặc bỏ qua từ stream mà không bị chặn (blocking)** ở thời điểm gọi. Nó có thể bằng `0`, kể cả khi stream vẫn còn dữ liệu và lần gọi `read()` tiếp theo có thể lấy được byte sau khi chờ, hoặc ngay lập tức.

Cần giữ ba khái niệm riêng biệt:

| Khái niệm | Cách hiểu đúng |
| --- | --- |
| `available()` | Ước lượng byte có thể lấy **không phải chờ** ở thời điểm hiện tại; phụ thuộc loại stream. |
| Tổng kích thước nguồn | Số byte toàn bộ tệp, thông điệp hoặc dữ liệu sẽ được cung cấp; nhiều stream không có tổng này từ trước. |
| EOF | Trạng thái hết dữ liệu, được thể hiện khi `read()` trả `-1` (hoặc API tương ứng cho biết đã hết luồng). |

Đặc biệt, triển khai mặc định của `InputStream.available()` trả về **`0`**. Các lớp con có thể ghi đè phương thức này khi chúng biết thêm về dữ liệu đang sẵn có. Vì vậy, không thể suy ra dung lượng toàn bộ stream từ một con số `available()` duy nhất.

## 2. Tại sao kết quả khác nhau giữa các luồng?

- **`ByteArrayInputStream`:** dữ liệu đã có đầy đủ trong mảng byte; `available()` trả về số byte chưa đọc **còn lại trong mảng**. Với riêng lớp này, con số ấy phản ánh phần còn lại của nguồn hiện có.
- **`FileInputStream`:** với tệp thông thường, giá trị thường gần với số byte còn có thể đọc từ vị trí hiện tại, nhưng API vẫn mô tả nó theo hợp đồng `available()`. Đừng biến đặc điểm của `FileInputStream` thành quy tắc chung cho mọi `InputStream`.
- **Luồng từ socket, pipe hoặc nguồn nhận dữ liệu dần:** có thể chưa có byte nào trong bộ đệm *ngay bây giờ*, dù phía gửi sẽ gửi thêm sau đó. `available() == 0` không chứng minh kết nối đã kết thúc hoặc thông điệp đã hoàn chỉnh.
- **Lớp con chỉ triển khai `read()`:** nếu không ghi đè `available()`, giá trị luôn là `0` theo triển khai mặc định, dù dữ liệu vẫn đọc được.

Lưu ý: con số có thể thay đổi sau một lần đọc hoặc khi dữ liệu mới đến. Nó cũng không thay thế một định dạng giao thức quy định trước độ dài thông điệp.

## 3. Thí nghiệm Java 17+: có dữ liệu nhưng `available() == 0`

Lưu đoạn sau vào tệp **`AvailableDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AvailableDemo {
    public static void main(String[] args) throws IOException {
        byte[] data = "ABCDE".getBytes(StandardCharsets.US_ASCII);

        try (InputStream arrayStream = new ByteArrayInputStream(data)) {
            System.out.println("array before = " + arrayStream.available());
            System.out.println("array first  = " + (char) arrayStream.read());
            System.out.println("array after  = " + arrayStream.available());
        }

        try (InputStream customStream = new InputStream() {
            private int index;

            @Override
            public int read() {
                return index < data.length ? (data[index++] & 0xff) : -1;
            }
        }) {
            System.out.println("custom before = " + customStream.available());
            System.out.println("custom first  = " + (char) customStream.read());
            System.out.println("custom after  = " + customStream.available());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac AvailableDemo.java
java AvailableDemo
```

Kết quả chuẩn:

```text
array before = 5
array first  = A
array after  = 4
custom before = 0
custom first  = A
custom after  = 0
```

**Giải thích:**

1. `ByteArrayInputStream` biết mảng chứa năm byte; sau khi đọc `A`, còn bốn byte chưa đọc nên `available()` từ `5` thành `4`.
2. `customStream` chỉ ghi đè `read()` và kế thừa `available()` mặc định. Do đó `available()` luôn là `0`, ngay cả trước khi `read()` trả byte `A` thành công.
3. Cả hai luồng đều được khởi tạo từ dữ liệu `ABCDE`, nhưng phép đo `available()` khác nhau. Điều đó chứng minh phương thức này **không đại diện cho tổng kích thước dữ liệu**.

Ví dụ cố tình dùng một `InputStream` tùy chỉnh nhỏ để quan sát rõ hợp đồng API; đây không phải cách cần tạo mọi luồng trong ứng dụng thực tế.

## 4. Hai cách dùng sai dễ làm mất dữ liệu

### Sai lầm A: cấp phát theo `available()` rồi đọc một lần

```java
// SAI: không bảo đảm giữ được mọi byte của luồng.
byte[] all = new byte[in.available()];
int count = in.read(all);
```

Đoạn này có ít nhất hai lỗi độc lập:

1. `available()` có thể là `0` hoặc nhỏ hơn dữ liệu sẽ đến, nên mảng `all` có thể thiếu chỗ hoặc rỗng dù stream chưa kết thúc.
2. Ngay cả khi mảng đủ lớn, **một lần `read(byte[])` không được bảo đảm điền đầy mảng**; giá trị trả về là số byte thực sự đọc được hoặc `-1` khi EOF. Không được mặc định `count == all.length`.

### Sai lầm B: dùng `available() > 0` để quyết định còn dữ liệu

```java
// SAI: có thể dừng sớm khi chưa nhận hết dữ liệu.
while (in.available() > 0) {
    int oneByte = in.read();
    // Xử lý byte...
}
```

Với một luồng mạng, dữ liệu có thể đến theo từng đợt. Khi bộ đệm tạm thời rỗng, `available()` có thể về `0`; vòng lặp kết thúc dù đầu bên kia vẫn đang truyền. Với lớp `InputStream` không ghi đè `available()`, vòng lặp thậm chí không chạy lần nào.

**Quy tắc:** không dùng `available()` làm điều kiện kết thúc dữ liệu, không dùng nó làm độ dài thông điệp và không dùng nó làm căn cứ cấp phát một bộ đệm chứa toàn bộ stream.

## 5. Cách đúng: đọc cho tới EOF

Đối với một luồng thông thường mà bạn muốn đọc hết, đọc trong vòng lặp và kiểm tra giá trị trả về của `read(byte[])`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ReadUntilEofDemo {
    public static void main(String[] args) throws IOException {
        byte[] source = "Xin chao IO!".getBytes(StandardCharsets.UTF_8);

        try (InputStream in = new ByteArrayInputStream(source);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4];
            int count;

            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }

            byte[] result = out.toByteArray();
            System.out.println(new String(result, StandardCharsets.UTF_8));
            System.out.println("bytes = " + result.length);
        }
    }
}
```

Lưu thành **`ReadUntilEofDemo.java`**, rồi chạy:

```bash
javac ReadUntilEofDemo.java
java ReadUntilEofDemo
```

Kết quả:

```text
Xin chao IO!
bytes = 12
```

Lý do cách này đúng:

- Bộ đệm `buffer` có kích thước cố định `4` byte, độc lập với `available()`. Đây là dung lượng *mỗi lượt đọc*, không phải kích thước toàn bộ nguồn.
- `read(buffer)` trả về số byte thực tế của lượt đọc. `out.write(buffer, 0, count)` chỉ ghi đúng `count` byte, tránh ghi phần cũ còn sót lại trong mảng.
- Khi API báo `-1`, vòng lặp mới xem là kết thúc stream. Nếu nguồn có thể chặn, `read` có thể cần chờ dữ liệu tiếp theo; đó là hành vi đọc được xác định bởi stream.

`ByteArrayOutputStream` ở ví dụ này giữ tất cả byte trong RAM để minh họa. Với dữ liệu lớn, nên xử lý từng khối hoặc ghi trực tiếp vào `OutputStream` đích thay vì tích lũy toàn bộ vào bộ nhớ.

## 6. Chọn API theo mục tiêu thực tế

| Bạn muốn làm gì? | Cách phù hợp | Lưu ý |
| --- | --- | --- |
| Đọc hết stream chưa biết độ dài | `read(byte[])` trong vòng lặp đến `-1` | Phù hợp khi cần xử lý/ghi từng khối; không cần gọi `available()`. |
| Lấy tất cả byte của luồng **nhỏ, hữu hạn** | `in.readAllBytes()` (Java 9+) | Đọc đến EOF; tiêu tốn bộ nhớ theo lượng dữ liệu và có thể chờ lâu nếu nguồn chưa đóng. |
| Chuyển dữ liệu sang một đầu ra | `in.transferTo(out)` (Java 9+) | Trả về số byte đã chuyển dưới dạng `long`; không cần đo trước độ dài luồng. |
| Cần đọc tối đa `n` byte từ stream | `in.readNBytes(n)` hoặc vòng lặp có giới hạn | Số byte thực nhận có thể ít hơn `n` nếu gặp EOF. |
| Giao thức yêu cầu **đủ chính xác N byte** | Đọc lặp tới khi đủ N; có thể dùng `DataInputStream.readFully(byte[])` | `readFully` ném `EOFException` nếu hết luồng trước khi đọc đủ. |
| Cần kích thước của **tệp trên filesystem** | `Files.size(path)` | Trả về `long` cho tệp tại thời điểm truy vấn; không dùng cho socket hay mọi `InputStream`. |

Ví dụ lấy kích thước một tệp:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSizeDemo {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("data.bin");
        long size = Files.size(path);
        System.out.println("File size (bytes): " + size);
    }
}
```

Đây là chương trình Java hợp lệ; khi chạy cần có tệp `data.bin` ở thư mục làm việc. Giá trị in ra **phụ thuộc tệp thực tế**, nên không có stdout cố định. `Files.size(path)` cho biết kích thước của tệp được chỉ định, không phải số byte hiện có trong bộ đệm một `InputStream` đang mở. Nếu tệp thay đổi trong lúc đọc, kích thước đo trước cũng không thay thế việc kiểm tra kết quả từng lần `read`.

## 7. EOF, blocking và ranh giới thông điệp

Hãy hình dung luồng mạng nhận chuỗi byte theo hai đợt:

```text
Thời điểm t1: [A B] đã đến; phần C D E chưa đến
Thời điểm t2: [C D E] đến sau
Kết thúc:    phía gửi đóng luồng hoặc giao thức xác nhận hết thông điệp
```

Tại một thời điểm giữa hai đợt, `available()` có thể là `0` dù sau đó `read()` vẫn trả byte `C`. Tương tự, có lúc `available()` dương nhưng số đó không chứng minh rằng đã nhận đủ thông điệp.

Phân biệt hai câu hỏi khác nhau:

- **“Luồng đã tới EOF chưa?”** Dựa vào `read() == -1` khi đọc tới cuối stream, không dựa vào `available() == 0`.
- **“Thông điệp đã nhận đủ chưa?”** Phụ thuộc giao thức: ví dụ độ dài được truyền trước, ký tự phân tách, khung dữ liệu, hoặc kết thúc stream. Một kết nối mạng có thể mang nhiều thông điệp trước khi EOF; đừng chờ EOF cho từng thông điệp nếu giao thức dùng cơ chế đóng khung khác.

Trong thực tế, khi `read()` có thể chờ vô thời hạn, hãy dùng giới hạn kích thước, timeout hoặc cơ chế đóng khung tương ứng với loại nguồn. `available()` không thay thế những biện pháp đó.

## 8. Những điểm dễ nhầm khi xử lý byte

1. **Số byte khác số ký tự.** Với UTF-8, một ký tự có thể chiếm nhiều byte; `available()` dù đúng số byte đang có cũng không cho biết số ký tự văn bản.
2. **`read()` trả kiểu `int`.** Một byte hợp lệ được trả về ở khoảng `0..255`; `-1` là dấu EOF. Khi tự triển khai `InputStream.read()`, chuyển `byte` sang int bằng `& 0xff` như ví dụ để tránh nhầm byte có bit dấu thành giá trị âm.
3. **Đọc một lần không đồng nghĩa đọc đủ.** Kiểm tra `count` từ `read(buffer)` và chỉ xử lý đúng vùng `0..count-1`.
4. **Dung lượng bộ đệm là quyết định hiệu năng, không phải độ dài dữ liệu.** Có thể dùng bộ đệm vài KiB và đọc rất nhiều lượt mà không biết tổng kích thước.
5. **Không giữ vô hạn dữ liệu không tin cậy trong RAM.** `readAllBytes()` và `ByteArrayOutputStream` hữu ích với dữ liệu hữu hạn, có giới hạn; với upload hoặc kết nối dài, xử lý luồng theo khối và áp dụng giới hạn riêng.
6. **Chỉ đóng stream do mình sở hữu.** `try-with-resources` giải phóng tài nguyên trong các chương trình minh họa; trong mã thư viện, cần tuân theo hợp đồng về bên sở hữu stream.

## 9. Tự kiểm tra

**Câu 1.** Một `InputStream` trả `available() == 0`. Có thể khẳng định `read()` ngay sau đó trả `-1` không?

**Trả lời:** Không. Stream có thể còn dữ liệu nhưng không có byte sẵn ngay tại thời điểm đo; hoặc lớp con dùng `available()` mặc định luôn trả `0`. EOF phải được xác định qua thao tác đọc.

**Câu 2.** `ByteArrayInputStream` tạo từ mảng 10 byte, đã đọc thành công 3 byte. Trong điều kiện không có thao tác `mark/reset/skip` khác, `available()` của chính lớp này cho kết quả nào?

**Trả lời:** `7`, vì lớp này biết chính xác lượng byte chưa đọc trong mảng. Đó là đặc điểm của `ByteArrayInputStream`, không phải cam kết chung của mọi stream.

**Câu 3.** Muốn sao chép dữ liệu từ một `InputStream` không biết trước độ dài sang một `OutputStream`, có cần tính `available()` không?

**Trả lời:** Không. Có thể dùng `in.transferTo(out)` hoặc vòng lặp `read(buffer)` và `out.write(buffer, 0, count)` cho đến EOF, kèm giới hạn và xử lý timeout khi phù hợp.

**Ghi nhớ:** `available()` trả về *ước lượng số byte có thể lấy ngay mà không blocking*, **không phải tổng kích thước dữ liệu và không phải phép thử EOF**. Đọc bằng kết quả `read()`; lấy độ dài từ nguồn dữ liệu hoặc giao thức nếu thật sự cần biết trước.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-009">transferTo và quyền sở hữu hai stream</a>

<details>
<summary>Xem chi tiết</summary>

# `InputStream.transferTo` và quyền sở hữu hai stream

Khi sao chép byte từ một nguồn sang một đích, Java cung cấp `InputStream.transferTo(OutputStream)` để đọc toàn bộ **phần dữ liệu còn lại** của luồng đầu vào và ghi lần lượt sang luồng đầu ra. Phương thức này giúp code sao chép ngắn gọn, nhưng có một quy tắc dễ bỏ sót: **`transferTo` không đóng cả hai stream**. Việc đóng tài nguyên thuộc trách nhiệm của bên sở hữu chúng.

Bài học tập trung vào hai câu hỏi: **`transferTo` thực sự làm gì với dữ liệu?** và **ai phải đóng input/output sau lời gọi đó?**

## 1. API và cơ chế của `transferTo`

Kể từ **Java 9**, lớp `java.io.InputStream` có phương thức:

~~~java
public long transferTo(OutputStream out) throws IOException
~~~

Gọi bằng:

~~~java
long count = input.transferTo(output);
~~~

Ý nghĩa:

- **`input`** là `InputStream` đang ở vị trí đọc hiện tại. Nó là nguồn của thao tác.
- **`output`** là `OutputStream` nhận các byte. Nó là đích của thao tác.
- Phương thức đọc lần lượt các byte còn lại từ `input` và ghi chúng vào `output` **theo cùng thứ tự**.
- Khi trả về thành công, `input` đã ở **cuối luồng** (end of stream); một lần đọc tiếp thường sẽ nhận dấu hiệu hết luồng `-1` nếu lớp luồng tuân theo ngữ nghĩa EOF thông thường.
- Kết quả trả về kiểu `long` là **số byte đã chuyển**, không phải chuỗi, không phải mảng byte và không phải số ký tự.
- `transferTo` **không gọi `close()` trên `input` hoặc `output`** theo hợp đồng API. Sau khi chuyển xong, chúng vẫn thuộc bên chịu trách nhiệm quản lý.
- Nếu `output` bằng `null`, phương thức ném `NullPointerException`. Nếu thao tác đọc/ghi gặp lỗi, nó có thể ném `IOException` **sau khi đã chuyển một phần dữ liệu**.

Sơ đồ dòng dữ liệu:

~~~text
                      InputStream.transferTo(OutputStream)
Tệp / socket / memory -------- [đọc byte -> ghi byte] --------> Tệp / socket / memory
      input (nguồn)                                             output (đích)
      đã tới EOF nếu thành công                                   không tự đóng
       không tự đóng                                             không tự flush bảo đảm
~~~

Điểm quan trọng là **`transferTo` thực hiện sao chép byte**, không tự chuyển mã ký tự, không xử lý văn bản và không cần bạn tự tạo `byte[]` để viết vòng lặp thông thường. Nội dung truyền có thể là văn bản UTF-8, ảnh, ZIP hay dữ liệu nhị phân bất kỳ.

Ví dụ, với luồng đang chứa `A B C D E` nhưng đã đọc trước `A B`, gọi `transferTo` sẽ chuyển **`C D E`**, vì nó hoạt động từ **vị trí hiện tại**; không quay lại đầu luồng. Với nguồn đã hết dữ liệu, kết quả là `0`.

### So với vòng lặp `read`/`write`

Công việc về mặt ý nghĩa gần tương đương:

~~~java
byte[] buffer = new byte[8192];
long total = 0;
int n;
while ((n = input.read(buffer)) != -1) {
    output.write(buffer, 0, n);
    total += n;
}
~~~

Trong đoạn này, phải ghi đúng `n` byte vừa đọc vì `read(buffer)` không bắt buộc lấp đầy mảng. `transferTo` che giấu việc lặp và quản lý bộ đệm trong triển khai. Đừng kết luận từ đó rằng mọi `InputStream` cụ thể đều có cùng chiến lược tối ưu; lớp con có thể thay đổi triển khai.

Với API Java I/O, **đọc đến EOF không tương đương với đóng stream**: một luồng đọc có thể đã cạn nhưng vẫn chưa được `close` và vẫn có thể giữ tài nguyên như file descriptor hoặc kết nối.

## 2. Quyền sở hữu stream là gì?

**Quyền sở hữu (ownership)** là trách nhiệm quyết định khi nào một tài nguyên được đóng hoặc giải phóng. Trong code Java, bản thân kiểu `InputStream` hay `OutputStream` **không cho biết chắc** ai sở hữu: điều này đến từ cách tạo stream và **hợp đồng giữa các phương thức**.

Có ba vai trò thường gặp:

1. **Bên tạo và sở hữu stream:** ví dụ phương thức tự gọi `Files.newInputStream(path)` và `Files.newOutputStream(path)` để thực hiện công việc. Thông thường nó chịu trách nhiệm đóng cả hai stream.
2. **Bên mượn stream (borrower):** ví dụ helper nhận tham số `InputStream input, OutputStream output` do caller truyền vào. Mặc định helper chỉ sử dụng chúng; caller tiếp tục quản lý vòng đời. Nếu API muốn nhận quyền sở hữu, phải nói rõ trong hợp đồng.
3. **Bên quản lý vòng đời ở cấp cao hơn:** ví dụ framework quản lý request/response, kết nối mạng, hoặc `System.in`/`System.out` do môi trường chạy cung cấp. Code dùng một lần không nên tự ý đóng tài nguyên chung.

Quy tắc thiết kế thực dụng: **nơi sở hữu tài nguyên đóng tài nguyên; nơi chỉ mượn không đóng**. `transferTo` không thay đổi chủ sở hữu chỉ vì nó chuyển dữ liệu.

| Trường hợp | Ai sở hữu? | Sau `transferTo` phải làm gì? |
|---|---|---|
| Caller mở cả input và output rồi gọi helper | Caller | Helper trả về số byte; caller đóng cả hai |
| Helper tự mở cả hai stream trong cùng phương thức | Helper | Dùng `try-with-resources` cho cả input và output |
| Helper mở input từ tệp nhưng nhận output từ caller | Input thuộc helper; output thuộc caller | Helper chỉ đóng input; giữ output mở |
| Helper chuyển byte ra `System.out` | Thường thuộc ứng dụng/môi trường chạy | Không đóng `System.out` trong helper |
| Framework cấp stream cho callback | Theo hợp đồng framework | Làm đúng quy định ownership của API đang dùng |

**Không có quy tắc “ai gọi `transferTo` thì phải đóng cả hai”.** Ngược lại, cũng không có quy tắc “stream nào được truyền vào hàm cũng không bao giờ được đóng”: nếu API nói rõ ownership được chuyển giao, bên nhận có thể có trách nhiệm đóng.

## 3. Ví dụ hoàn chỉnh: `transferTo` không đóng input hoặc output

Ví dụ Java 17+ dưới đây theo dõi xem `close()` đã được gọi chưa. Ta dùng `ByteArrayInputStream` và `ByteArrayOutputStream` để chương trình có thể chạy độc lập, không cần tệp ngoài. Hai lớp `TrackedInput` và `TrackedOutput` là wrapper đánh dấu mỗi lần phương thức `close()` được gọi.

Lưu thành **`TransferToOwnershipDemo.java`**:

~~~java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TransferToOwnershipDemo {
    static final class TrackedInput extends FilterInputStream {
        boolean closed;

        TrackedInput(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    static final class TrackedOutput extends FilterOutputStream {
        boolean closed;

        TrackedOutput(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    static long copyBorrowed(InputStream source, OutputStream destination)
            throws IOException {
        return source.transferTo(destination);
    }

    public static void main(String[] args) throws IOException {
        byte[] original = "Java I/O".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream storage = new ByteArrayOutputStream();
        TrackedInput input = new TrackedInput(new ByteArrayInputStream(original));
        TrackedOutput output = new TrackedOutput(storage);

        try (input; output) {
            long bytesCopied = copyBorrowed(input, output);
            System.out.println("Transferred: " + bytesCopied + " byte");
            System.out.println("After transferTo: inputClosed=" + input.closed
                    + ", outputClosed=" + output.closed);

            output.write('!');
            output.flush();
            System.out.println("After extra write: "
                    + storage.toString(StandardCharsets.UTF_8));
        }

        System.out.println("After try: inputClosed=" + input.closed
                + ", outputClosed=" + output.closed);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 TransferToOwnershipDemo.java
java TransferToOwnershipDemo
~~~

Kết quả chuẩn:

~~~text
Transferred: 8 byte
After transferTo: inputClosed=false, outputClosed=false
After extra write: Java I/O!
After try: inputClosed=true, outputClosed=true
~~~

**Giải thích theo đúng thứ tự:**

1. `"Java I/O"` có 8 byte trong UTF-8 (toàn ký tự ASCII), được lưu ở `input`.
2. `copyBorrowed` gọi `source.transferTo(destination)`; helper **mượn** hai stream, chỉ trả về số byte, không đóng chúng.
3. Ngay sau `transferTo`, hai cờ `closed` vẫn là `false`. Đây là bằng chứng trong ví dụ rằng **chưa gọi `close()`**.
4. `output.write('!')` vẫn ghi thêm được byte sau khi chuyển; kết quả trong `ByteArrayOutputStream` thành `"Java I/O!"`. Input đã tới cuối luồng, nhưng output không bị đóng do `transferTo`.
5. Kết thúc `try (input; output)`, **try-with-resources** gọi `close` theo thứ tự **ngược với khai báo**: output rồi input. Hai cờ sau khối trở thành `true`.

*Lưu ý:* `ByteArrayInputStream`/`ByteArrayOutputStream` không đại diện cho việc giải phóng file descriptor thực; wrapper ở đây chỉ giúp quan sát **lời gọi `close`**. Với tệp, socket hoặc tài nguyên OS, thiếu `close` có thể gây rò rỉ tài nguyên. `try (input; output)` dùng cú pháp tài nguyên đã khai báo bên ngoài được hỗ trợ từ Java 9; các biến ở đây phải là final hoặc *effectively final*.

## 4. Khi phương thức tự mở cả hai stream

Khi mở tài nguyên từ đường dẫn tệp ngay trong phương thức, phương thức đó thường **sở hữu cả hai**. Hãy dùng `try-with-resources` để đóng cả hai ngay cả khi `transferTo` ném `IOException`:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileCopyService {
    public static long copyFile(Path source, Path destination)
            throws IOException {
        try (InputStream input = Files.newInputStream(source);
             OutputStream output = Files.newOutputStream(destination)) {
            return input.transferTo(output);
        }
    }
}
~~~

Đây là một **lớp tiện ích có phương thức `copyFile`**, chưa có `main`; nó được gọi từ code ứng dụng khác. Khi tạo thành công cả hai stream, `try-with-resources` quản lý chúng. Nếu mở output thất bại, input đã mở vẫn được đóng. Nếu `transferTo` thất bại, các tài nguyên đã mở cũng được đóng; ngoại lệ đóng thêm có thể được gắn dưới dạng *suppressed exception*.

**Cẩn thận khi ghi file thật:** `Files.newOutputStream(destination)` với tùy chọn mặc định có thể tạo tệp mới hoặc **cắt ngắn nội dung tệp đã tồn tại**. Nếu không muốn ghi đè, chọn tùy chọn mở phù hợp, chẳng hạn `StandardOpenOption.CREATE_NEW` theo hợp đồng tác vụ. Quyền sở hữu stream là vấn đề khác với chính sách thay thế dữ liệu đích.

Không cần gọi `input.close()`/`output.close()` lần nữa bên trong khối `try`. Hạn chế đóng thủ công ở nhiều nhánh vì dễ bỏ sót tài nguyên khi có ngoại lệ.

## 5. Khi helper chỉ nhận stream do caller cung cấp

Trong code thư viện, một hàm sao chép thường không biết liệu input/output còn được dùng tiếp hay không. Hợp đồng mượn stream nên rõ ràng:

~~~java
// Hàm này chỉ dùng hai stream do caller sở hữu.
// Hàm không close và không tự quyết định flush cho output.
static long transfer(InputStream input, OutputStream output)
        throws IOException {
    return input.transferTo(output);
}
~~~

Đây là **đoạn phương thức minh họa** cần đặt vào một lớp có import `java.io.InputStream`, `java.io.OutputStream` và `java.io.IOException`. Caller có thể mở chúng trong `try-with-resources` và gọi helper:

~~~java
try (InputStream input = Files.newInputStream(source);
     OutputStream output = Files.newOutputStream(destination)) {
    long count = transfer(input, output);
    System.out.println("Copied: " + count);
}
~~~

Các biến `source`, `destination` là `Path` đã được khai báo; đoạn này minh họa cách sử dụng, không phải một chương trình độc lập.

Một trường hợp **quyền sở hữu hỗn hợp**:

~~~java
// Helper tự mở input: helper phải đóng input.
// destination do caller đưa vào: helper không đóng destination.
static long sendFile(Path path, OutputStream destination)
        throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
        return input.transferTo(destination);
    }
}
~~~

Nếu gọi `sendFile(path, System.out)`, helper đóng **input tệp** nhưng vẫn để **`System.out` mở** để ứng dụng còn in được các thông báo khác.

Ngược lại, viết như sau là không phù hợp với hợp đồng mượn stream:

~~~java
// SAI nếu caller vẫn sở hữu output:
try (OutputStream borrowed = output) {
    input.transferTo(borrowed);
}
// borrowed.close() đã được gọi, có thể đóng output của caller.
~~~

`try-with-resources` là cơ chế đóng đúng khi code **sở hữu** tài nguyên; đặt stream được mượn vào `try` có thể vô tình chiếm luôn quyền đóng. Cú pháp hợp lệ không có nghĩa hợp đồng ownership là đúng.

## 6. `flush()`, `close()` và `transferTo()` khác nhau thế nào?

| Thao tác | Ý nghĩa chính | Có tự đóng input/output không? |
|---|---|---|
| `input.transferTo(output)` | Chuyển các byte còn lại sang output; trả số byte | **Không** |
| `output.flush()` | Yêu cầu đẩy byte đang được buffer ra đích bên dưới | Không đóng |
| `output.close()` | Kết thúc tài nguyên output, thường flush theo lớp triển khai | **Đóng output**; có thể kéo theo stream nằm dưới |
| `input.close()` | Kết thúc tài nguyên input | **Đóng input**; có thể kéo theo stream nằm dưới |
| `try-with-resources` | Gọi `close()` tự động khi ra khỏi khối, cả khi có lỗi | Đóng **các tài nguyên đã khai báo** |

**`transferTo` không đưa ra bảo đảm rằng output đã được `flush`.** Nếu output có buffer như `BufferedOutputStream`, phần byte còn trong buffer có thể cần `flush()` trước khi bên nhận thấy đủ dữ liệu. Việc `flush` khi nào phải theo giao thức và hợp đồng của người sở hữu output: helper mượn output không nên tùy tiện áp đặt đóng hoặc flush nếu chưa được yêu cầu.

**`flush` không đồng nghĩa dữ liệu đã được ghi bền vững trên thiết bị lưu trữ.** Theo hợp đồng chung của `OutputStream`, flush đến lớp bên dưới có thể chỉ tới hệ điều hành; muốn độ bền dữ liệu trên đĩa có thể cần API và quy trình riêng. Đóng output cũng không chứng minh một bên nhận mạng đã xử lý toàn bộ thông điệp.

**Wrapper có thể kéo theo đóng luồng bên dưới:** `BufferedOutputStream`, `FilterOutputStream` và một số stream nén/giải nén thường bao một stream khác. Gọi `close` trên wrapper có thể đóng luôn stream gốc, trong khi một số định dạng cần `finish()` hoặc quy trình riêng để hoàn tất nội dung. Nếu wrapper bọc `System.out` hoặc stream do framework cấp, phải thiết kế ownership cả ở tầng wrapper để tránh đóng mất tài nguyên chung.

## 7. Ngoại lệ, chuyển một phần và khả năng bị chặn

`transferTo` thuận tiện, nhưng không biến I/O thành thao tác nguyên tử:

- Nếu đọc input hoặc ghi output lỗi, `IOException` có thể xuất hiện **sau khi đã ghi một phần byte**. Không được giả định file đích còn rỗng hay source vẫn đứng ở vị trí cũ.
- Nếu dữ liệu đầu vào liên tục hoặc đầu ra chậm, phương thức có thể **chặn** trong lúc chờ đọc/ghi, thậm chí kéo dài không xác định. Với socket và giao thức mạng cần chính sách timeout, hủy hoặc giới hạn dữ liệu từ lớp sở hữu.
- Nếu muốn giới hạn chỉ chuyển một phần dữ liệu, `transferTo` trực tiếp không nhận tham số “tối đa N byte”. Có thể dùng luồng giới hạn thích hợp hoặc vòng đọc/ghi có kiểm soát.
- Khi xử lý lỗi, bên sở hữu stream nên đóng các tài nguyên và quyết định có xóa file đích ghi dở, thử lại hay báo lỗi. Helper chỉ mượn stream nên ném lỗi lên caller trừ khi hợp đồng có quy định xử lý khác.
- Gọi `transferTo` **lần thứ hai** trên input đã ở EOF sẽ không tự gửi lại dữ liệu; muốn chạy lại cần tạo nguồn mới hoặc một luồng hỗ trợ thay đổi vị trí thích hợp.

Luồng đang bị dùng **đồng thời** bởi nhiều thread có thể tạo kết quả không mong muốn, nhất là khi các thread cùng đọc một input hoặc cùng ghi một output. `transferTo` không tự thiết lập giao thức đồng bộ để bảo vệ các lời gọi khác.

## 8. Phân biệt với các API có tên tương tự

- **`InputStream.transferTo(OutputStream)`**: truyền **byte** từ input đang đọc sang output, trả **số byte**; có từ Java 9.
- **`Reader.transferTo(Writer)`**: truyền **ký tự** từ `Reader` sang `Writer`, trả **số ký tự**; có từ Java 10. Bài này nói về **luồng byte**.
- **`Files.copy(Path, Path, ...)`**: API sao chép theo đường dẫn với các lựa chọn xử lý file; hợp đồng tạo/đóng tài nguyên do `Files` quản lý khác với việc tự chuyền hai stream vào `InputStream.transferTo`.
- **`FileChannel.transferTo(...)`**: API kênh (channel), chữ ký/điều kiện truyền khác; không nên suy diễn semantics hay ownership từ phương thức này sang `InputStream.transferTo`.

Khi review code, cần nhận diện **đúng kiểu đối tượng và chữ ký phương thức** trước khi áp dụng quy tắc.

## 9. Các lỗi thường gặp và cách sửa

| Sai lầm | Vấn đề | Cách sửa |
|---|---|---|
| “`transferTo` đóng input, output hộ tôi.” | API không đóng stream; có thể rò rỉ tài nguyên. | Bên sở hữu dùng `try-with-resources` hoặc quản lý vòng đời rõ ràng. |
| “Helper nhận output thì cứ đưa vào `try`.” | Có thể đóng tài nguyên caller còn cần. | Helper mượn output thì chỉ chuyển dữ liệu; caller chịu trách nhiệm đóng. |
| “Sau khi copy, input tự quay về đầu.” | `transferTo` đọc từ vị trí hiện tại tới EOF. | Nếu cần lần đọc mới, mở stream mới hoặc thiết kế hỗ trợ định vị. |
| “Output đã nhận byte thì chắc chắn file/network hoàn tất.” | Có thể còn buffer và giới hạn bảo đảm của tầng lưu trữ/giao thức. | Xác định khi nào cần `flush`, đóng hoặc xác nhận giao thức. |
| “`transferTo` luôn chạy xong và không sinh bản sao dở.” | I/O có thể lỗi giữa chừng hoặc chờ vô hạn. | Có chiến lược timeout/huỷ, cleanup, xử lý dữ liệu ghi một phần. |
| “Dữ liệu UTF-8 nên `transferTo` sẽ giải mã và mã hóa lại.” | `transferTo` chỉ chuyển byte. | Chỉ dùng Reader/Writer với charset khi thực sự cần xử lý ký tự. |
| “`try-with-resources` chỉ dùng khi thành công.” | Đây là cơ chế dọn tài nguyên cả khi lỗi. | Đặt các stream **do mình sở hữu** vào phần khai báo tài nguyên. |

## 10. Tự kiểm tra

**Câu 1.** `input.transferTo(output)` trả về `120L`. Con số 120 là gì? Nếu thao tác thành công, input ở đâu?

> **Đáp án:** 120 là số **byte được chuyển**. Input ở cuối phần dữ liệu có thể đọc; `transferTo` không tự đóng input hay output.

**Câu 2.** Hàm `saveReport(OutputStream out)` nhận output từ caller, tự tạo một `InputStream` đọc báo cáo. Ai đóng gì?

> **Đáp án:** Hàm tự đóng input do mình tạo. Caller đóng (hoặc quản lý) output mà mình sở hữu theo hợp đồng. `transferTo` không thay đổi trách nhiệm đó.

**Câu 3.** Nếu output bọc một `BufferedOutputStream` và helper gọi `transferTo` nhưng caller chưa thấy đủ nội dung, nên xem xét điều gì?

> **Đáp án:** Kiểm tra chính sách buffer và `flush`/`close`, cũng như thời điểm bên nhận đọc dữ liệu. `transferTo` không bảo đảm flush; việc flush hay close cần phù hợp ownership.

**Câu 4.** Nếu đầu vào đã được đọc hết trước `transferTo`, kết quả là gì?

> **Đáp án:** `0` byte được chuyển, vì không còn byte nào để đọc. Output vẫn không bị đóng bởi `transferTo`.

### Ghi nhớ

**`input.transferTo(output)` = chuyển byte còn lại + trả số byte; không đóng hai stream.** Viết code an toàn bằng cách xác định rõ **ai tạo, ai mượn và ai đóng** mỗi tài nguyên, dùng `try-with-resources` tại tầng sở hữu, và xử lý lỗi I/O có thể xảy ra sau khi đã ghi một phần dữ liệu.

**Tài liệu đối chiếu:** [Java SE 17 `InputStream.transferTo`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#transferTo(java.io.OutputStream)) và [Java SE 17 `OutputStream.flush`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html#flush()).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-010">FileInputStream, FileOutputStream và chế độ append</a>

<details>
<summary>Xem chi tiết</summary>

# FileInputStream, FileOutputStream và chế độ append trong Java

`FileInputStream` và `FileOutputStream` là hai luồng byte dùng để làm việc trực tiếp với tệp trong gói `java.io`. Một bên đọc byte có sẵn trên đĩa, bên còn lại ghi byte vào tệp. Khi ghi, chọn **ghi từ đầu tệp** hay **ghi nối vào cuối tệp (append)** là quyết định rất quan trọng: mở `FileOutputStream` không bật append trên tệp đã tồn tại có thể làm mất toàn bộ dữ liệu cũ ngay lúc mở luồng.

Bài này tập trung vào cách mở, đọc, ghi, ghi nối, đóng và xử lý lỗi của hai lớp; các ví dụ áp dụng cho Java 17+.

## 1. Luồng byte làm việc với tệp như thế nào?

- **`FileInputStream`** kế thừa `InputStream`: mở tệp để lấy các byte theo thứ tự, với vị trí đọc tiến lên qua các lần gọi `read(...)`. Đọc đến hết tệp trả `-1`.
- **`FileOutputStream`** kế thừa `OutputStream`: mở tệp để ghi các byte bằng `write(...)`; không cung cấp thao tác đọc. Chế độ mở quyết định dữ liệu cũ được giữ để nối thêm hay bị thay thế.
- Hai lớp xử lý **byte thô**, không tự giải mã UTF-8, không tự hiểu dòng, số nguyên, ảnh hay JSON. Diễn giải dữ liệu là trách nhiệm của tầng phía trên.
- Cả hai là luồng I/O gắn với tài nguyên hệ điều hành. Mở có thể thất bại và thao tác có thể ném `IOException`; phải đóng tài nguyên khi dùng xong.

| Nhu cầu | Lựa chọn | Hành vi chính |
|---|---|---|
| Đọc tệp | `new FileInputStream(file)` | Mở để đọc; không tạo tệp còn thiếu |
| Tạo hoặc thay thế nội dung tệp | `new FileOutputStream(file)` | Nếu tệp hợp lệ chưa có thì tạo; nếu có thì **xóa nội dung cũ (truncate)** |
| Nối byte vào cuối tệp | `new FileOutputStream(file, true)` | Nếu tệp chưa có thì tạo; nếu đã có thì giữ dữ liệu cũ và ghi tiếp |
| Ghi từ đầu với tham số tường minh | `new FileOutputStream(file, false)` | Cùng ý nghĩa với không truyền cờ append |

Ở đây `file` có thể là một đối tượng `File`; các constructor tương tự cũng nhận đường dẫn `String`. Nên tạo đường dẫn một cách tường minh bằng `Path` và chuyển sang `File` khi sử dụng các API cổ điển:

~~~java
Path path = Path.of("data.bin");
File file = path.toFile();

// Các dòng sau là minh họa, không mở cùng lúc cả ba luồng ghi:
FileInputStream input = new FileInputStream(file);
FileOutputStream overwrite = new FileOutputStream(file);
FileOutputStream append = new FileOutputStream(file, true);
~~~

Đoạn mã trên chỉ minh họa các constructor, **không** phải chương trình chạy độc lập. Trong code thật hãy chọn đúng một chế độ theo mục đích, quản lý đóng luồng và tránh mở luồng ghi khi chưa định ghi dữ liệu. Chỉ riêng việc tạo `new FileOutputStream(file)` đã có thể truncate tệp.

## 2. Phân biệt overwrite và append

Giả sử tệp đang chứa ba byte theo thứ tự:

~~~text
A B C
~~~

Sau khi mở `new FileOutputStream(file)` và ghi hai byte `X Y`, nội dung cuối cùng là:

~~~text
X Y
~~~

Ngược lại, mở `new FileOutputStream(file, true)` rồi ghi `X Y` sẽ cho:

~~~text
A B C X Y
~~~

Lưu ý quan trọng:

1. `append = true` nghĩa là **ghi byte mới ở cuối tệp**. Nó không tự thêm ký tự xuống dòng, dấu cách, dấu phân cách hay header.
2. `append = false` là mặc định. Trên tệp thông thường đã tồn tại, constructor mở đầu ra ở chế độ này sẽ truncate tệp; việc đóng ngay sau đó cũng không khôi phục dữ liệu cũ.
3. Cả hai chế độ có thể tạo tệp mới nếu tệp không tồn tại và đường dẫn cùng quyền truy cập hợp lệ. Chúng **không tự tạo thư mục cha**.
4. Mở thất bại, chẳng hạn vì tệp không tồn tại khi đọc, đường dẫn là thư mục không phù hợp hoặc thiếu quyền, sẽ phát sinh lỗi (thường là `FileNotFoundException` khi tạo luồng), không phải trả về một luồng rỗng.
5. Append không mặc nhiên bảo đảm nhiều luồng hoặc nhiều tiến trình cùng ghi thành từng **bản ghi nguyên vẹn** theo ý nghĩa nghiệp vụ. Nếu cần đồng bộ truy cập hoặc khóa tệp, phải thiết kế riêng; cũng cần xác định rõ quy tắc phân cách dữ liệu.

Để thêm một dòng văn bản vào tệp, chính ứng dụng phải tự quyết định có ghi `"\n"` hay không. Append chỉ là hành vi đặt vị trí ghi, không phải API quản lý dòng.

## 3. Ví dụ Java 17+: ghi mới, nối thêm rồi ghi thay thế

Tạo tệp **`FileAppendDemo.java`** với mã hoàn chỉnh:

~~~java
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class FileAppendDemo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("append-demo-", ".bin");

        try {
            // Không append: bắt đầu nội dung tệp bằng 3 byte.
            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                out.write(new byte[] {10, 20, 30});
            }

            // Append: giữ 3 byte cũ và nối 2 byte tiếp theo.
            try (FileOutputStream out = new FileOutputStream(path.toFile(), true)) {
                out.write(new byte[] {40, 50});
            }
            System.out.println("Sau append: " + Arrays.toString(readAll(path)));

            // Không append: tệp trước đó bị truncate.
            try (FileOutputStream out = new FileOutputStream(path.toFile(), false)) {
                out.write(7);
            }
            System.out.println("Sau overwrite: " + Arrays.toString(readAll(path)));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    private static byte[] readAll(Path path) throws IOException {
        try (FileInputStream in = new FileInputStream(path.toFile());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[3];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac FileAppendDemo.java
java FileAppendDemo
~~~

Kết quả chuẩn:

~~~text
Sau append: [10, 20, 30, 40, 50]
Sau overwrite: [7]
~~~

Điều đáng quan sát:

- Chương trình tạo một tệp tạm mới bằng `Files.createTempFile(...)` để tránh ghi đè nhầm tài liệu sẵn có.
- Lần mở đầu tiên, `FileOutputStream(path.toFile())` ghi ba byte. Lần mở với `true` nối thêm hai byte; đọc lại cho thấy đầy đủ năm byte.
- Lần mở cuối cùng với `false` truncate dữ liệu cũ rồi ghi một byte có giá trị `7`; vì vậy toàn bộ tệp chỉ còn `[7]`.
- `readAll(...)` lặp đến EOF; mỗi lần chỉ chuyển `n` byte thực sự đọc được sang `ByteArrayOutputStream`. Nó không coi kích thước bộ đệm 3 là số byte luôn đọc được.
- Cả ba luồng ghi, luồng đọc và luồng bộ nhớ đều đóng bằng `try-with-resources`. Khối `finally` xóa tệp tạm sau khi các luồng đã đóng.

Trong ví dụ này, phương thức `readAll` gom dữ liệu vào RAM để tiện in ra màn hình; với tệp rất lớn nên xử lý hoặc sao chép từng khối thay vì giữ toàn bộ nội dung trong bộ nhớ.

## 4. Đọc đúng cách với FileInputStream

Các phương thức thông dụng kế thừa từ `InputStream`:

| Lời gọi | Điều nhận được |
|---|---|
| `int value = in.read()` | Một byte ở dạng `int` trong khoảng `0..255`, hoặc `-1` khi EOF |
| `int n = in.read(buffer)` | Đọc tối đa `buffer.length` byte, bắt đầu tại chỉ số 0 |
| `int n = in.read(buffer, offset, length)` | Đọc tối đa `length` byte vào mảng tại chỉ số `offset` |
| `byte[] data = in.readAllBytes()` | Đọc toàn bộ các byte còn lại vào mảng, phù hợp khi dữ liệu đủ nhỏ |
| `long n = in.transferTo(out)` | Truyền byte từ đầu vào sang một `OutputStream` cho tới EOF |

`read(buffer, off, len)` trả về **số byte thực đọc**, có thể nhỏ hơn `len`, hoặc `-1` nếu đã EOF (với `len > 0`). Nếu `len == 0`, lời gọi trả `0`. `off` chỉ là chỉ số trong **mảng đích**, không phải vị trí trên đĩa. Vùng hợp lệ phải thỏa `0 <= off <= buffer.length` và `0 <= len <= buffer.length - off`.

Vòng lặp đọc/sao chép thường viết:

~~~java
byte[] buffer = new byte[8192];
int n;
while ((n = input.read(buffer)) != -1) {
    output.write(buffer, 0, n);
}
~~~

Đây là **mảnh mã minh họa** yêu cầu hai biến `input`/`output` là các luồng đang mở. Không dùng `output.write(buffer)` thay cho `output.write(buffer, 0, n)`: lần đọc cuối có thể nhận ít hơn bộ đệm, khiến phần dư cũ bị ghi sai.

Nếu xử lý mỗi byte riêng lẻ, luôn so sánh kết quả `int` với `-1` **trước khi chuyển sang `byte`**. Byte `0xFF` hợp lệ có dạng `-1` khi hiển thị bằng kiểu `byte` có dấu, nhưng `in.read()` sẽ trả số nguyên `255` khi đọc nó thành công; chỉ `-1` kiểu `int` mới báo EOF.

Lệnh `available()` không cho biết chính xác tổng số byte còn trong tệp/luồng theo hợp đồng chung của `InputStream`; không dùng nó làm điều kiện đọc hết. Luôn dựa vào `read()` trả `-1`, độ dài đã biết của giao thức hoặc API đọc đủ chuyên dụng.

## 5. Ghi byte bằng FileOutputStream

Những phương thức kế thừa từ `OutputStream` thường dùng:

- `out.write(int b)` ghi **8 bit thấp** của số nguyên: `out.write(65)` ghi đúng một byte `0x41` (chữ A nếu diễn giải theo ASCII).
- `out.write(byte[] bytes)` ghi toàn bộ mảng.
- `out.write(byte[] bytes, int offset, int length)` ghi chính xác `length` byte bắt đầu tại chỉ số `offset` trong mảng nguồn.
- `out.close()` đóng luồng và giải phóng tài nguyên liên quan; ưu tiên `try-with-resources` để đóng cả khi có ngoại lệ.

Khác với `read(...)`, `write(byte[], off, len)` trả về `void`; nếu hoàn thành bình thường, lời gọi đã thực hiện yêu cầu ghi `len` byte vào luồng theo hợp đồng của API. Nếu có sự cố ghi, phương thức có thể ném `IOException`. Không dùng giá trị trả về của `read(...)` làm `length` trước khi đã kiểm tra nó là số dương.

Ví dụ với một mảng byte đã có:

~~~java
byte[] payload = {11, 22, 33, 44, 55};
out.write(payload, 1, 3); // Ghi ba byte 22, 33, 44
~~~

Đây là mảnh mã minh họa; `out` cần là một `FileOutputStream` đã mở hợp lệ. Tham số thứ ba là **số lượng byte**, không phải chỉ số cuối.

Khi cần nâng hiệu suất ghi nhiều khối nhỏ, có thể bọc bằng `BufferedOutputStream`. Trong trường hợp đó, `flush()` đẩy dữ liệu đang nằm trong bộ đệm của Java xuống luồng bên dưới; nó không đồng nghĩa với việc dữ liệu đã được lưu bền vững trên thiết bị lưu trữ. `close()` của luồng bọc sẽ đóng luôn luồng bên dưới, nên chỉ quản lý đóng lớp bao ngoài khi đã trao quyền sở hữu cho wrapper.

## 6. Ví dụ Java 17+: append văn bản có mã hóa rõ ràng

`FileOutputStream` là luồng byte. Khi cần lưu văn bản, phải tự chuyển `String` thành byte bằng charset rõ ràng; với dữ liệu UTF-8, dùng `StandardCharsets.UTF_8`. Ví dụ này cố ý ghi ký tự xuống dòng ở cuối từng bản ghi, bởi cờ append tự nó **không** thêm dấu xuống dòng.

Tệp **`AppendUtf8Demo.java`**:

~~~java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppendUtf8Demo {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("append-lines-", ".txt");

        try {
            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                out.write("Dong 1\n".getBytes(StandardCharsets.UTF_8));
            }

            try (FileOutputStream out = new FileOutputStream(path.toFile(), true)) {
                out.write("Dong 2\n".getBytes(StandardCharsets.UTF_8));
            }

            try (FileInputStream in = new FileInputStream(path.toFile())) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                System.out.print(text);
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

~~~bash
javac AppendUtf8Demo.java
java AppendUtf8Demo
~~~

Kết quả:

~~~text
Dong 1
Dong 2
~~~

Ví dụ giữ ASCII trong nội dung để stdout giống nhau ở các terminal phổ biến; thao tác chuyển mã vẫn thực sự sử dụng UTF-8. Với tệp văn bản lớn hoặc cần xử lý từng dòng, nên chọn `BufferedReader`/`BufferedWriter`, `InputStreamReader`/`OutputStreamWriter` hoặc `Files.newBufferedReader`/`Files.newBufferedWriter`. Không nên chuyển từng mảng byte đọc được sang `String` một cách độc lập nếu một ký tự UTF-8 có thể bị tách giữa hai lần đọc.

## 7. Quản lý tài nguyên và lỗi tệp

Ưu tiên `try-with-resources` cho cả đọc và ghi:

~~~java
try (FileInputStream in = new FileInputStream(sourceFile);
     FileOutputStream out = new FileOutputStream(targetFile)) {
    in.transferTo(out);
}
~~~

Đây là mảnh mã minh họa: `sourceFile` và `targetFile` là những biến `File` hợp lệ đã được xác định. Cách viết tự đóng hai luồng khi khối kết thúc, kể cả khi đọc/ghi ném lỗi; không cần gọi `close()` thủ công trong `finally`. Lưu ý mở đích bằng constructor không append **sẽ truncate đích**, và sao chép tệp lên chính nó theo cách này có thể phá dữ liệu; nên xác minh nguồn và đích là hai tệp phù hợp trước khi mở.

Các tình huống cần chủ động xử lý:

| Tình huống | Hệ quả và hướng xử lý |
|---|---|
| Đọc tệp không tồn tại | `FileInputStream` có thể ném `FileNotFoundException`; kiểm tra đường dẫn hoặc xử lý lỗi |
| Ghi vào thư mục cha chưa tồn tại | Constructor không tự tạo thư mục; tạo thư mục trước nếu ứng dụng cho phép |
| Thiếu quyền đọc/ghi hoặc tệp bị khóa | Có thể ném ngoại lệ I/O; hiển thị lỗi đúng ngữ cảnh, không giả vờ thành công |
| Mở output không append trên tệp có dữ liệu | Truncate ngay khi mở; cân nhắc append, lưu bản sao hoặc cơ chế ghi an toàn |
| Đọc bằng mảng và bỏ qua `n` | Có thể xuất thêm dữ liệu cũ cuối bộ đệm; dùng đúng số byte thực nhận |
| Append nhiều bản ghi cùng lúc từ nhiều tiến trình | Bản ghi có thể bị xen kẽ tùy hệ thống và cách ghi; cần đồng bộ/khóa hoặc kiến trúc ghi tập trung khi tính toàn vẹn quan trọng |
| Lưu file lớn bằng `readAllBytes()` | Có thể tiêu tốn RAM lớn; đọc theo các khối kích thước cố định |
| Ghi văn bản mà không xác định charset | Dễ lệch mã hóa giữa máy; dùng UTF-8 hoặc API đọc/ghi văn bản phù hợp |

Một số phương thức viết có thể đưa dữ liệu qua bộ đệm của hệ điều hành. Đóng luồng sau khi ghi bảo đảm đóng tài nguyên theo hợp đồng API nhưng **không tự tạo bảo đảm bền vững trên ổ đĩa khi mất điện**. Nếu ứng dụng cần mức bảo đảm này, cần thiết kế cơ chế đồng bộ dữ liệu phù hợp như `FileDescriptor.sync()` và cân nhắc tính nguyên tử khi thay thế tệp; vấn đề này tách khỏi ý nghĩa của append.

## 8. FileInputStream/FileOutputStream so với Files API

Các lớp cổ điển rất hữu ích để hiểu luồng byte và tương thích mã cũ. Trong Java hiện đại, `java.nio.file.Files` cung cấp thêm các cách mở với ý định rõ ràng:

~~~java
Path path = Path.of("data.bin");

// Các biểu thức này chỉ minh họa cách chọn API.
InputStream in = Files.newInputStream(path);
OutputStream fresh = Files.newOutputStream(path);
OutputStream append = Files.newOutputStream(
        path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
~~~

Đây là **mảnh mã minh họa**, cần import `java.io.InputStream`, `java.io.OutputStream`, `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.file.StandardOpenOption`. Mỗi luồng phải được quản lý đóng riêng; không nên cùng lúc mở `fresh` và `append` vào cùng tệp trong một ví dụ sử dụng thực tế.

- Mặc định, `Files.newOutputStream(path)` có hiệu ứng `CREATE`, `TRUNCATE_EXISTING` và `WRITE`: tạo mới hoặc truncate tệp cũ rồi ghi.
- Để nối dữ liệu, chỉ rõ `StandardOpenOption.APPEND`; thêm `CREATE` khi muốn tạo nếu chưa tồn tại, như ví dụ.
- Nếu không cho phép ghi đè tệp đã có, xem xét `CREATE_NEW` thay vì mặc định tạo/truncate.
- `Files.copy(...)` và `Files.write(...)` có thể phù hợp cho thao tác tệp ngắn, nhưng vẫn cần hiểu open options và rủi ro ghi đè.

Đừng nhầm `APPEND` với `CREATE` hoặc `TRUNCATE_EXISTING`. Chúng mô tả những quyết định khác nhau: vị trí ghi, việc có tạo tệp hay không, và việc có xóa nội dung cũ hay không. Tránh kết hợp những open option có ý nghĩa xung đột.

## 9. Cách chọn chế độ trong tình huống thực tế

**Tạo kết quả nhị phân mới:** viết với `FileOutputStream` sau khi đã xác minh đường dẫn đích. Nếu ứng dụng không được ghi đè, dùng API có lựa chọn `CREATE_NEW`.

**Thêm bản ghi nhật ký:** dùng chế độ append, viết dấu phân cách và charset phù hợp. Với hệ thống ghi đồng thời hoặc yêu cầu log đáng tin cậy, cân nhắc logger chuyên dụng và cơ chế đồng bộ.

**Đọc và sao chép tệp:** dùng `FileInputStream` đọc theo khối, `FileOutputStream` ghi đúng số byte nhận được. Không cần giải mã dữ liệu nếu chỉ sao chép byte.

**Đọc một file văn bản:** có thể đọc byte qua `FileInputStream` rồi giải mã, nhưng `Files.newBufferedReader` hoặc `InputStreamReader` thường diễn đạt mục đích đúng hơn, đặc biệt khi nội dung lớn.

**Cập nhật một byte ở giữa tệp:** hai lớp stream này không cung cấp truy cập ngẫu nhiên theo vị trí. Xem `RandomAccessFile` hoặc `FileChannel` nếu thật sự cần đọc/ghi theo offset của tệp; offset trong `read(byte[], off, len)` chỉ là offset **trong mảng**.

## 10. Checklist kiến thức

1. `FileInputStream` chỉ đọc, trả `-1` ở EOF; `FileOutputStream` chỉ ghi.
2. `new FileOutputStream(file)` hoặc `(..., false)` **truncate** tệp đã có; `(..., true)` **nối vào cuối**.
3. Append không tự tạo newline, không tạo thư mục cha, không đảm bảo tính nguyên tử của bản ghi nghiệp vụ khi nhiều tác nhân cùng ghi.
4. Khi đọc vào `byte[]`, chỉ xử lý `n` byte mà `read(...)` thực sự trả về.
5. Đóng luồng với `try-with-resources` và xử lý `IOException`; mở luồng đầu ra cũng là thao tác có tác dụng phụ.
6. Byte thô khác văn bản: muốn ghi văn bản thì xác định charset, chẳng hạn UTF-8.
7. Với ứng dụng thật, lựa chọn rõ ràng giữa tạo mới, thay thế và append trước khi mở đích; dùng `Files` và `StandardOpenOption` khi cần diễn đạt các chính sách này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="byte-streams-011">nullInputStream, nullOutputStream và tình huống sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# `InputStream.nullInputStream()`, `OutputStream.nullOutputStream()` và tình huống sử dụng

## Mục tiêu

Trong Java I/O, đôi khi chương trình cần một nguồn byte **chủ động không có dữ liệu** hoặc một đích ghi **chủ động bỏ qua mọi byte nhận được**. Java cung cấp sẵn hai factory method:

- `InputStream.nullInputStream()`: tạo một `InputStream` rỗng, ở trạng thái EOF ngay từ đầu.
- `OutputStream.nullOutputStream()`: tạo một `OutputStream` nhận thao tác ghi nhưng **loại bỏ** các byte, không lưu hay chuyển chúng đến tệp, bộ nhớ hoặc mạng.

Cả hai API xuất hiện từ **Java 11** và dùng trực tiếp được trong Java 17+. Bài học phân biệt chúng với tham chiếu Java `null`, giải thích hành vi khi mở/đóng luồng, cung cấp ví dụ chạy được và chỉ ra lúc nên hoặc không nên sử dụng.

## 1. “Null stream” không có nghĩa là một biến bằng `null`

Hai cách khai báo dưới đây hoàn toàn khác nhau:

```java
InputStream missing = null;                    // Không có đối tượng nào.
InputStream empty = InputStream.nullInputStream(); // Có đối tượng InputStream hợp lệ.
```

Gọi `missing.read()` sẽ gây `NullPointerException`. Với `empty`, phương thức `read()` có hành vi bình thường của một nguồn đã đến cuối dữ liệu: trả về `-1` (trong khi luồng còn mở).

Tương tự:

```java
OutputStream missing = null;
OutputStream discard = OutputStream.nullOutputStream();
```

`missing.write(65)` gây `NullPointerException`; `discard.write(65)` được chấp nhận khi luồng còn mở, nhưng byte `65` không được lưu ở đâu cả.

Tên “null” ở đây diễn tả **nguồn rỗng** hoặc **đích bỏ dữ liệu**, không phải tham chiếu rỗng. Hai factory method trả về những đối tượng thật triển khai đúng hợp đồng của `InputStream` và `OutputStream`.

## 2. `InputStream.nullInputStream()`: luồng byte rỗng, EOF tức thì

Chữ ký API:

```java
public static InputStream nullInputStream()
```

Khi **chưa đóng**, luồng này không chứa byte nào:

| Thao tác | Kết quả có ý nghĩa |
| --- | --- |
| `read()` | Trả về `-1` (EOF). |
| `read(byte[])` hoặc `read(byte[], off, len)` | Với yêu cầu đọc có độ dài dương: EOF, thường biểu diễn bằng `-1`; yêu cầu đọc 0 byte trả `0`. |
| `available()` | Trả `0`. |
| `readAllBytes()` | Trả về mảng byte rỗng. |
| `readNBytes(n)` | Không có byte để đọc; kết quả là mảng rỗng khi `n >= 0`. |
| `skip(n)` | Không bỏ qua byte nào: trả `0` với `n > 0`. |
| `transferTo(out)` | Không chuyển byte nào: trả `0`, miễn tham số đích hợp lệ. |
| `markSupported()` | Trả `false`; `mark()` không có tác dụng, `reset()` ném `IOException`. |

**EOF không phải lỗi.** Khi chưa đóng, kết quả `read() == -1` nghĩa là nguồn đã hết dữ liệu theo hợp đồng đọc stream. Nó không có nghĩa là “đang chờ mạng” hay “chưa nhận được dữ liệu”. Một `InputStream` khác có thể trả `available() == 0` mà vẫn chưa EOF; riêng `nullInputStream()` cố ý diễn tả dữ liệu đã kết thúc.

### Ví dụ đầy đủ: nguồn rỗng

Tạo tệp **`NullInputDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;

public class NullInputDemo {
    public static void main(String[] args) throws IOException {
        try (InputStream in = InputStream.nullInputStream()) {
            System.out.println("available = " + in.available());
            System.out.println("read = " + in.read());
            System.out.println("bytes = " + in.readAllBytes().length);
            System.out.println("skip = " + in.skip(10));
            System.out.println("mark supported = " + in.markSupported());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac NullInputDemo.java
java NullInputDemo
```

Kết quả chính xác:

```text
available = 0
read = -1
bytes = 0
skip = 0
mark supported = false
```

Ở chương trình này, `available() == 0` đi cùng EOF vì **loại nguồn cụ thể** là `nullInputStream()`. Không suy rộng điều đó sang socket, pipe hay mọi kiểu `InputStream`.

## 3. `OutputStream.nullOutputStream()`: nhận ghi nhưng không giữ byte

Chữ ký API:

```java
public static OutputStream nullOutputStream()
```

Khi **chưa đóng**, các thao tác `write(int)`, `write(byte[])` và `write(byte[], off, len)` không tạo đầu ra. Byte đã gửi vào bị bỏ đi. `flush()` không có công việc cần làm, vì không có dữ liệu đệm để đẩy ra tệp hay thiết bị.

### Ví dụ đầy đủ: ghi thành công nhưng không tạo kết quả lưu trữ

Tạo tệp **`NullOutputDemo.java`**:

```java
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class NullOutputDemo {
    public static void main(String[] args) throws IOException {
        try (OutputStream out = OutputStream.nullOutputStream()) {
            byte[] message = "Hello, Java".getBytes(StandardCharsets.UTF_8);
            out.write(message);
            out.write('!');
            out.flush();
            System.out.println("write operations completed");
        }
        System.out.println("no payload was stored");
    }
}
```

Biên dịch và chạy:

```bash
javac NullOutputDemo.java
java NullOutputDemo
```

Kết quả chính xác:

```text
write operations completed
no payload was stored
```

`System.out.println(...)` vẫn hiển thị ở console vì đoạn mã ghi trực tiếp lên `System.out`. Chỉ các byte được gửi tới đối tượng `out` từ `nullOutputStream()` mới bị bỏ đi.

**Lưu ý về hiệu năng:** việc ghi vào đích này không thực hiện I/O lưu trữ thực tế. Tuy nhiên, mọi công việc *trước khi ghi* (tạo dữ liệu, mã hóa, nén, tính toán, đọc nguồn) vẫn có thể tốn thời gian và bộ nhớ. Vì thế, “không có đầu ra” không có nghĩa toàn bộ pipeline miễn phí.

## 4. Đóng luồng rồi thì sao?

Hai factory method đều trả về stream **đang mở**. `close()` đặt chúng vào trạng thái đóng; gọi `close()` thêm lần nữa không có tác dụng.

- **`InputStream.nullInputStream()`:** sau khi đóng, các phương thức I/O như `read()`, `available()`, `readAllBytes()`, `skip()` và `transferTo()` ném `IOException`. Kết quả `-1` chỉ mô tả luồng rỗng **khi còn mở**. `reset()` cũng ném `IOException`; `markSupported()` vẫn không hỗ trợ đánh dấu.
- **`OutputStream.nullOutputStream()`:** sau khi đóng, các thao tác `write(...)` ném `IOException`. Theo đặc tả của factory này, `flush()` vẫn là thao tác không làm gì, kể cả khi không có dữ liệu cần đẩy ra; không nên dùng `flush()` để xác nhận stream còn mở.
- **`try-with-resources`:** cách thường dùng để bảo đảm gọi `close()` đúng lúc, nhất là khi cùng một hàm có thể nhận một stream thật thay cho null stream.

### Kiểm chứng ngoại lệ sau khi đóng

Tạo tệp **`ClosedNullStreamsDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ClosedNullStreamsDemo {
    public static void main(String[] args) throws IOException {
        InputStream in = InputStream.nullInputStream();
        in.close();
        in.close(); // Đóng lại vẫn hợp lệ.

        try {
            in.read();
        } catch (IOException e) {
            System.out.println("closed input: IOException");
        }

        OutputStream out = OutputStream.nullOutputStream();
        out.close();
        out.close(); // Đóng lại vẫn hợp lệ.

        try {
            out.write(42);
        } catch (IOException e) {
            System.out.println("closed output: IOException");
        }
    }
}
```

Chạy:

```bash
javac ClosedNullStreamsDemo.java
java ClosedNullStreamsDemo
```

Kết quả:

```text
closed input: IOException
closed output: IOException
```

Phân biệt **“rỗng”** với **“đã đóng”**: một luồng rỗng đang mở vẫn xử lý thao tác đọc hợp lệ bằng EOF; luồng đã đóng không còn cho phép đọc/ghi như trên.

## 5. Những tình huống sử dụng thực tế

### Tình huống A: tham số `InputStream` tùy chọn, muốn biểu diễn “không có dữ liệu”

Một API nhận `InputStream` không nhất thiết cần dữ liệu trong mọi trường hợp. Khi nghiệp vụ **thật sự cho phép đầu vào rỗng**, có thể thay `null` bằng stream rỗng để thuật toán xử lý thống nhất:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class OptionalInputDemo {
    static String readText(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        try (InputStream empty = InputStream.nullInputStream()) {
            String text = readText(empty);
            System.out.println("text length = " + text.length());
        }
    }
}
```

Chạy `javac OptionalInputDemo.java` rồi `java OptionalInputDemo`:

```text
text length = 0
```

Cách này phù hợp khi “không có nội dung” là một giá trị nghiệp vụ hợp lệ, chẳng hạn body tùy chọn trong luồng xử lý dữ liệu. **Không tự thay dữ liệu bắt buộc bị thiếu bằng `nullInputStream()`**, vì sẽ che giấu lỗi và khiến ứng dụng nhầm là đã đọc một đầu vào rỗng hợp lệ.

### Tình huống B: chạy pipeline đầy đủ nhưng chủ động bỏ đầu ra

Khi cần kiểm tra việc *đọc, giải mã, nén hoặc chuyển dữ liệu* mà không muốn tạo tệp kết quả, có thể nối pipeline vào `OutputStream.nullOutputStream()`.

Tạo tệp **`DiscardTransferDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class DiscardTransferDemo {
    public static void main(String[] args) throws IOException {
        byte[] payload = "Java I/O".getBytes(StandardCharsets.UTF_8);

        try (InputStream source = new ByteArrayInputStream(payload);
             OutputStream sink = OutputStream.nullOutputStream()) {
            long consumed = source.transferTo(sink);
            System.out.println("bytes consumed = " + consumed);
            System.out.println("next read = " + source.read());
        }
    }
}
```

Chạy:

```bash
javac DiscardTransferDemo.java
java DiscardTransferDemo
```

Kết quả:

```text
bytes consumed = 8
next read = -1
```

**Diễn giải:** `InputStream.transferTo(out)` đọc từ nguồn cho đến EOF, ghi các byte đã đọc vào `out` và trả về số byte được chuyển. Ở đây tám byte đã đi qua pipeline nên nguồn bị tiêu thụ; sink bỏ chúng đi. `consumed == 8` **không chứng minh có tám byte được lưu trên đĩa**.

Dùng cách này để kiểm thử logic đọc dữ liệu hoặc đo một phần chi phí xử lý là hợp lý, nhưng phép đo không phản ánh chi phí thực tế của việc ghi tệp hay truyền mạng.

### Tình huống C: bộ kiểm thử cần nguồn rỗng hoặc sink bỏ qua kết quả

- Đưa `InputStream.nullInputStream()` vào hàm parse để thử trường hợp file rỗng và xem hàm trả kết quả rỗng hay báo định dạng thiếu dữ liệu.
- Đưa `OutputStream.nullOutputStream()` vào hàm xuất báo cáo khi bài kiểm thử chỉ kiểm tra luồng xử lý, số bản ghi, số byte đã chuyển hoặc hành vi exception.
- Khi cần **khẳng định nội dung byte đã ghi chính xác**, phải dùng `ByteArrayOutputStream` hoặc đích có thể đọc lại. Sink bỏ dữ liệu không hỗ trợ assert trên payload.

Ví dụ thiết kế hàm cho phép đưa đầu ra từ bên ngoài:

```java
static void exportBytes(byte[] payload, java.io.OutputStream out)
        throws java.io.IOException {
    out.write(payload);
}
```

Đây là **đoạn mã minh họa phương thức**, không phải chương trình chạy độc lập. Người gọi có thể truyền `OutputStream.nullOutputStream()` khi có chủ ý bỏ đầu ra, hoặc `ByteArrayOutputStream` khi cần giữ lại dữ liệu để kiểm tra.

### Tình huống D: cung cấp triển khai mặc định rõ nghĩa cho một nhánh tùy chọn

Giả sử thành phần có `boolean shouldPersist`: chỉ khi `shouldPersist` đúng mới cần ghi ra file. Có thể chọn đầu ra theo điều kiện:

```java
// Đoạn mã minh họa, giả định biến target kiểu Path và shouldPersist đã có.
try (OutputStream out = shouldPersist
        ? java.nio.file.Files.newOutputStream(target)
        : OutputStream.nullOutputStream()) {
    produceReport(out);
}
```

`produceReport(OutputStream)` là phương thức của ứng dụng và chưa được định nghĩa trong snippet. Khi nhánh lưu tệp được chọn, cần xử lý đúng đường dẫn, quyền ghi và lỗi I/O. Nếu đầu ra là yêu cầu bắt buộc, chọn `nullOutputStream()` khi lỗi mở tệp **không phải** cách xử lý đúng: lỗi cần được báo cho người gọi.

## 6. So sánh với các lựa chọn thường gặp

| Lựa chọn | Có byte để đọc/ghi? | Có giữ được dữ liệu? | Phù hợp khi |
| --- | --- | --- | --- |
| `InputStream.nullInputStream()` | Đọc luôn EOF | Không có dữ liệu nguồn | Muốn biểu diễn đầu vào rỗng hợp lệ. |
| `new ByteArrayInputStream(new byte[0])` | Đọc luôn EOF | Đã có một mảng byte rỗng | Cần nguồn mảng, có thể mở rộng bằng dữ liệu byte thật. |
| `OutputStream.nullOutputStream()` | Chấp nhận ghi khi còn mở rồi bỏ byte | Không | Muốn tiêu thụ kết quả nhưng không lưu. |
| `ByteArrayOutputStream` | Chấp nhận ghi | Có, trong RAM | Kiểm tra, chuyển đổi hoặc thu thập payload để đọc lại. |
| `Files.newOutputStream(path)` | Chấp nhận ghi ra một tệp | Có, trên filesystem khi thành công | Cần lưu kết quả vào tệp. |

Các kiểu có thể cùng dùng một interface nhưng sở hữu ngữ nghĩa khác nhau. Vì thế, chọn chúng theo **mục đích giữ hay bỏ dữ liệu**, không chỉ vì chúng đều biên dịch được.

## 7. Cạm bẫy và giới hạn

1. **Đừng dùng luồng rỗng để hợp thức hóa thiếu dữ liệu.** Nếu hợp đồng yêu cầu file thật hoặc payload bắt buộc, hãy từ chối input thiếu thay vì trả kết quả rỗng âm thầm.
2. **Đừng hiểu tên `nullOutputStream` là “không bao giờ lỗi”.** Việc ghi vào stream đã đóng sẽ ném `IOException`; các bước *trước khi ghi* vẫn có thể ném ngoại lệ từ chính chúng.
3. **Đừng kỳ vọng sink lưu hay phát lại nội dung.** Không thể khôi phục các byte đã ghi vào `nullOutputStream()`; để kiểm tra nội dung phải sử dụng sink có lưu trữ.
4. **Đừng nhầm số byte đã xử lý với byte đã lưu.** Số byte từ `transferTo` đo lượng dữ liệu được chuyển qua lời gọi, kể cả khi đích bỏ dữ liệu.
5. **Đừng nhầm `available() == 0` của null input với quy tắc EOF chung.** Với network stream, `available() == 0` có thể chỉ là tạm thời không có byte sẵn. Quy tắc EOF chung vẫn là kiểm tra kết quả của thao tác `read()`.
6. **Đừng dùng null sink để đánh giá thông lượng tệp/mạng.** Loại bỏ công đoạn lưu/truyền sẽ làm phép đo khác tình huống thực tế; ghi rõ mình đo phần nào của pipeline.
7. **Phân biệt quyền sở hữu stream.** Hàm nhận `InputStream` từ bên ngoài thường không nên tự ý `close()` nếu hợp đồng cho người gọi sở hữu; với stream tạo nội bộ, `try-with-resources` là lựa chọn mặc định hợp lý.
8. **Quan tâm kích thước và giới hạn xử lý.** Bỏ kết quả không giới hạn lượng dữ liệu nguồn phải đọc; với nguồn lớn hoặc do người dùng gửi, vẫn cần giới hạn tài nguyên/timeout đúng loại nguồn.

## 8. Câu hỏi tự kiểm tra

**1. `InputStream.nullInputStream()` có tương đương khai báo `InputStream in = null` không?**

Không. Nó là một đối tượng stream hợp lệ. Khi chưa đóng, `in.read()` trả `-1` thay vì gây `NullPointerException`.

**2. Ghi 1 MiB vào `OutputStream.nullOutputStream()` thành công có đồng nghĩa tạo ra tệp 1 MiB không?**

Không. Dữ liệu đã bị loại bỏ. Không có tệp, không có buffer lưu nội dung được tạo bởi factory này.

**3. Sau khi `close()` một null input stream, `read()` trả `-1` hay ném ngoại lệ?**

Ném `IOException`. Giá trị `-1` là EOF của null input **còn mở**.

**4. Vì sao `InputStream.nullInputStream()` không thay thế được việc kiểm tra dữ liệu đầu vào bắt buộc?**

Vì stream rỗng luôn hợp lệ về mặt I/O và có thể khiến logic xử lý nhầm thiếu dữ liệu thành “file rỗng” theo nghiệp vụ. Nếu dữ liệu phải tồn tại thì nên báo lỗi rõ ràng.

**5. Khi nào nên dùng `ByteArrayOutputStream` thay `OutputStream.nullOutputStream()`?**

Khi cần đọc lại, so sánh hoặc khẳng định nội dung đã ghi. `nullOutputStream()` chỉ phù hợp khi chủ động bỏ nội dung.

## 9. Tài liệu tra cứu

- Java 17 API: [`InputStream.nullInputStream()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html#nullInputStream()).
- Java 17 API: [`OutputStream.nullOutputStream()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html#nullOutputStream()).

**Ghi nhớ:** `nullInputStream()` là *đầu vào hợp lệ nhưng rỗng*; `nullOutputStream()` là *đầu ra hợp lệ nhưng cố ý bỏ byte*. Hai API giúp biểu diễn ý định rõ ràng ở các nhánh tùy chọn và trong kiểm thử. Chúng không thay thế dữ liệu bắt buộc, cơ chế ghi thật hoặc xử lý lỗi I/O.


</details>

- [Quay lại đầu trang](#back-to-top)
