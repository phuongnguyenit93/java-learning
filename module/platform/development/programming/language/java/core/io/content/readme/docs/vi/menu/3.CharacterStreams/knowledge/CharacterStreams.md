<a id="back-to-top"></a>

# Luồng ký tự

## Menu
- [Reader và Writer xử lý đơn vị char](#character-streams-001)
- [Đọc và ghi char&#91;&#93;, String và từng phần dữ liệu](#character-streams-002)
- [EOF của Reader và số ký tự thực tế đã đọc](#character-streams-003)
- [InputStreamReader nối luồng byte với ký tự](#character-streams-004)
- [OutputStreamWriter nối ký tự với luồng byte](#character-streams-005)
- [FileReader, FileWriter và lựa chọn charset](#character-streams-006)
- [Reader.transferTo và giới hạn xử lý dữ liệu lớn](#character-streams-007)
- [Readable, Appendable và khả năng ghép API](#character-streams-008)
- [Vì sao không dùng luồng ký tự để sao chép dữ liệu nhị phân](#character-streams-009)

## <a id="character-streams-001">Reader và Writer xử lý đơn vị char</a>

<details>
<summary>Xem chi tiết</summary>

# Reader và Writer xử lý đơn vị `char` trong Java

Trong Java, `java.io.Reader` và `java.io.Writer` là hai lớp trừu tượng nền tảng cho **I/O ký tự**. `Reader` cung cấp thao tác **đọc** đơn vị `char` từ nguồn, còn `Writer` cung cấp thao tác **ghi** các đơn vị `char` đến đích. Đây là mô hình phù hợp khi ứng dụng làm việc với **văn bản**: nội dung file, chuỗi ký tự, log, dữ liệu trao đổi ở dạng text, v.v.

Điều quan trọng cần hiểu ngay từ đầu: một Java `char` là **một đơn vị mã UTF-16 16 bit**, không phải luôn luôn là một “ký tự người dùng nhìn thấy”. Vì thế API đọc/ghi `char` khác với API đọc/ghi **byte**, và cũng khác với việc duyệt **Unicode code point**.

## 1. Mô hình Reader/Writer và ranh giới dữ liệu

`Reader` và `Writer` thuộc gói `java.io`:

- **`Reader`**: nhận dữ liệu dạng ký tự từ một nguồn; ví dụ `StringReader` đọc chuỗi có sẵn, `InputStreamReader` giải mã byte từ `InputStream`, `BufferedReader` bọc một `Reader` để tăng hiệu quả và hỗ trợ đọc dòng.
- **`Writer`**: gửi dữ liệu dạng ký tự đến một đích; ví dụ `StringWriter` tích lũy ký tự trong bộ nhớ, `OutputStreamWriter` mã hóa ký tự thành byte để ghi vào `OutputStream`, `BufferedWriter` bổ sung bộ đệm và `newLine()`.
- Một `Reader` hoặc `Writer` là **kênh thao tác đọc/ghi**, không phải danh sách hay collection chứa sẵn toàn bộ văn bản; hành vi đọc, ghi, buffering và đóng phụ thuộc lớp triển khai và nguồn/đích.
- `Reader` không cung cấp `write()`, còn `Writer` không cung cấp `read()`. Muốn đọc một nguồn và ghi sang đích, dùng đồng thời hai đối tượng.

Sơ đồ luồng chuyển đổi khi dữ liệu lưu dưới dạng byte:

```text
Nguồn byte (file/socket/byte[])
         |
         v
InputStream --[InputStreamReader + Charset: giải mã]--> char/Reader
                                                           |
                                                           v
                                               Chương trình xử lý văn bản
                                                           |
                                                           v
OutputStream <--[OutputStreamWriter + Charset: mã hóa]-- char/Writer
         |
         v
Đích byte (file/socket/byte[])
```

`InputStreamReader` và `OutputStreamWriter` là **cầu nối** giữa byte stream và character stream; còn `StringReader`/`StringWriter` làm việc với ký tự đã ở bộ nhớ, không cần giải mã file.

## 2. `char` chính xác là đơn vị gì?

Kiểu nguyên thủy `char` trong Java có 16 bit và lưu **một UTF-16 code unit** (giá trị từ `0x0000` đến `0xFFFF`). Unicode code point là định danh của một ký tự Unicode trong khoảng `U+0000` đến `U+10FFFF`, loại trừ vùng surrogate khi xét code point hợp lệ.

- Phần lớn ký tự thông dụng trong **Basic Multilingual Plane (BMP)** dùng **một** `char`: `'A'`, `'đ'`, `'中'`.
- Ký tự thuộc vùng ngoài BMP, chẳng hạn emoji `😊` (U+1F60A), dùng **hai** `char` liên tiếp trong UTF-16, gọi là **surrogate pair**: `\uD83D` và `\uDE0A`.
- Một ký tự hiển thị có thể còn gồm **nhiều code point**, ví dụ chữ kèm dấu kết hợp hoặc emoji có biến thể. Vì vậy **đếm `char`**, **đếm code point** và **đếm grapheme cluster** là ba phép đếm khác nhau.
- `String.length()` trả về số UTF-16 code unit; `String.codePointCount(0, s.length())` đếm code point trong phạm vi đó; cả hai không bảo đảm tương đương số ký tự người dùng nhận thức.

Điều này tác động trực tiếp đến I/O: `Reader.read()` đọc **một UTF-16 code unit** trong mỗi lần trả về thành công, chứ không cam kết trả về nguyên vẹn một Unicode code point. `Writer.write(int)` ghi **16 bit thấp** của đối số thành một đơn vị `char`; nó không phải API “ghi một Unicode code point” tổng quát. Nếu cần biến code point ngoài BMP thành văn bản, hãy chuyển bằng `Character.toChars(codePoint)` hoặc xử lý chuỗi chứa surrogate pair đầy đủ.

### Ví dụ 1 — quan sát từng đơn vị `char` bằng `StringReader`

Tạo tệp `ReaderWriterUnitsDemo.java`:

```java
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

public class ReaderWriterUnitsDemo {
    public static void main(String[] args) throws Exception {
        String text = "A😊B";
        try (Reader reader = new StringReader(text);
             StringWriter writer = new StringWriter()) {
            int codeUnit;
            while ((codeUnit = reader.read()) != -1) {
                System.out.printf("unit=U+%04X%n", codeUnit);
                writer.write(codeUnit);
            }
            String copy = writer.toString();
            System.out.println("char units=" + copy.length());
            System.out.println("code points=" + copy.codePointCount(0, copy.length()));
            System.out.println("same=" + text.equals(copy));
        }
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 --release 17 ReaderWriterUnitsDemo.java
java ReaderWriterUnitsDemo
```

Kết quả chính xác:

```text
unit=U+0041
unit=U+D83D
unit=U+DE0A
unit=U+0042
char units=4
code points=3
same=true
```

`Reader.read()` trả về `int` để có đủ chỗ biểu diễn mọi giá trị `char` từ 0 đến 65535 **và** giá trị đặc biệt `-1` cho cuối luồng (EOF). Không nên chuyển ngay kết quả `read()` sang `char` rồi mới kiểm tra EOF, vì sẽ mất dấu hiệu `-1`.

Ví dụ có bốn code unit: `A`, surrogate cao, surrogate thấp, `B`; nhưng chỉ có ba code point. `StringWriter` ghi lại đúng từng unit nên khôi phục được chuỗi ban đầu. Nếu chuyển đổi từng code unit độc lập thành byte hoặc thành chuỗi rồi xử lý tùy tiện, surrogate pair có thể bị tách và làm sai văn bản.

## 3. Các phương thức cốt lõi và ý nghĩa giá trị trả về

| API | Ý nghĩa | Điểm cần nhớ |
|---|---|---|
| `int Reader.read()` | Đọc một UTF-16 code unit | Trả `0..65535` hoặc `-1` khi EOF; có thể ném `IOException` |
| `int Reader.read(char[] buffer)` | Đọc tối đa `buffer.length` code unit vào mảng | Trả **số phần tử thực sự đọc** hoặc `-1`; đừng giả sử mảng được lấp đầy |
| `int Reader.read(char[] b, int off, int len)` | Đọc tối đa `len` unit vào mảng từ vị trí `off` | Phải tôn trọng `off` và số lượng trả về; `len = 0` có thể trả `0` |
| `long Reader.skip(long n)` | Bỏ qua số `char` | Đơn vị là code unit; kết quả là số lượng thực sự bỏ qua |
| `boolean Reader.ready()` | Kiểm tra khả năng đọc không bị chặn ngay | **Không** phải phép kiểm tra EOF; `false` không có nghĩa đã hết dữ liệu |
| `void Writer.write(int c)` | Ghi một `char` | Dùng 16 bit thấp của `c` |
| `void Writer.write(char[] b, int off, int len)` | Ghi đúng đoạn mảng ký tự | Phù hợp khi sao chép từng khối đọc từ `Reader` |
| `void Writer.write(String s)` | Ghi chuỗi ký tự | Không tự chèn dấu xuống dòng |
| `void Writer.flush()` | Đẩy dữ liệu đang đệm xuống đích bên dưới | Không đồng nghĩa dữ liệu đã được đồng bộ bền vững xuống đĩa |
| `void close()` | Giải phóng tài nguyên | Gọi bằng `try-with-resources` khi sở hữu nguồn/đích |

Các thao tác trên có thể phát sinh `IOException` tùy triển khai. Khi đọc bằng `read(char[])`, một lần gọi **có thể lấy ít ký tự hơn số lượng yêu cầu**; đây không phải EOF. Kiểm tra `n == -1` để kết thúc và chỉ xử lý `buffer[0..n)`.

Một mẫu sao chép ký tự đúng là:

```java
char[] buffer = new char[4096];
int n;
while ((n = reader.read(buffer, 0, buffer.length)) != -1) {
    writer.write(buffer, 0, n);
}
writer.flush();
```

Đây là **đoạn minh họa nằm bên trong một hàm**; hai biến `reader` và `writer` phải được tạo ở nơi gọi, và caller phải xử lý `IOException`. Đặc biệt, dùng `writer.write(buffer)` thay cho `writer.write(buffer, 0, n)` có thể ghi cả ký tự cũ còn sót lại trong mảng ở lượt đọc cuối.

Sao chép các code unit nguyên vẹn bằng phương thức trên vẫn giữ đúng surrogate pair, kể cả khi hai phần của cặp nằm ở hai lượt đọc khác nhau. Ngược lại, các phép **biến đổi** theo từng khối (ví dụ cắt chuỗi, viết hoa theo `char`, mã hóa riêng mỗi khối) cần quan tâm khả năng tách surrogate và xử lý theo code point hoặc từng đoạn văn bản đầy đủ.

Từ Java 10, `Reader.transferTo(Writer)` có thể sao chép trực tiếp toàn bộ `char` còn lại sang writer, trả số đơn vị đã truyền; phương thức này **không tự đóng** reader hoặc writer.

## 4. Đọc và ghi văn bản thực tế qua `BufferedReader`/`BufferedWriter`

`BufferedReader` và `BufferedWriter` bọc nguồn/đích ký tự bằng bộ đệm, giảm chi phí của nhiều thao tác nhỏ:

- `BufferedReader.readLine()` lấy nội dung một dòng, **không kèm** ký tự kết thúc dòng; trả `null` khi không còn dòng nào. Dòng rỗng hợp lệ trả chuỗi rỗng `""`, không phải `null`.
- `BufferedWriter.newLine()` ghi dấu kết thúc dòng theo nền tảng (như LF hoặc CRLF); muốn định dạng chính xác bất kể hệ điều hành thì chủ động ghi dấu kết thúc dòng được giao thức/file format quy định.
- Bộ đệm không biến I/O thành một nguồn dữ liệu nằm hoàn toàn trong RAM. Nó chỉ giúp giảm số lần đọc/ghi xuống lớp bên dưới, tùy kích thước và cách truy cập.
- `Files.newBufferedReader(path, charset)` / `Files.newBufferedWriter(path, charset)` là cách thuận tiện để mở file văn bản với charset rõ ràng.

### Ví dụ 2 — đọc ghi file UTF-8, giữ tiếng Việt và emoji

Tạo tệp `TextFileDemo.java`:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextFileDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("reader-writer-", ".txt");
        String first = "Tiếng Việt: Nguyễn";
        String second = "Emoji: 😊";
        try {
            try (BufferedWriter writer =
                     Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(first);
                writer.newLine();
                writer.write(second);
            }
            try (BufferedReader reader =
                     Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String a = reader.readLine();
                String b = reader.readLine();
                String end = reader.readLine();
                System.out.println("line 1 matches=" + first.equals(a));
                System.out.println("line 2 matches=" + second.equals(b));
                System.out.println("EOF=" + (end == null));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 TextFileDemo.java
java TextFileDemo
```

```text
line 1 matches=true
line 2 matches=true
EOF=true
```

**Diễn giải luồng xử lý:**

1. `Files.createTempFile` tạo đường dẫn file thực tế. Chương trình không phụ thuộc vào file có sẵn.
2. `newBufferedWriter` ghi các code unit từ `String`, đồng thời lớp mã hóa chuyển chúng thành **byte UTF-8**. `newLine()` ghi dấu xuống dòng phù hợp nền tảng.
3. Khối `try-with-resources` đóng writer trước khi mở reader, bảo đảm dữ liệu đã đệm được gửi xuống dưới và file sẵn sàng cho lần đọc tiếp theo.
4. `newBufferedReader` đọc byte UTF-8 và giải mã thành code unit UTF-16. `readLine` trả các đoạn text **đã bỏ dấu kết thúc dòng**, vì vậy cả hai phép so sánh đều đúng.
5. Lần `readLine()` thứ ba trả `null`: EOF. Khối `finally` xóa file tạm sau khi reader đã đóng.

Lưu ý: `try-with-resources` cũng xử lý đóng khi có ngoại lệ. `finally` được dùng ở ví dụ để dọn file tạm; một lỗi xóa file vẫn có thể khiến chương trình ném `IOException`. Kết quả in ra là boolean nên ổn định ngay cả khi console của máy không hiển thị tiếng Việt hoặc emoji đúng.

## 5. Vì sao ký tự phải chuyển qua charset khi đi ra/vào byte?

**Java `char` không phải byte UTF-8.** Một code point có thể chiếm số byte khác nhau trong UTF-8; Java `String` giữ các UTF-16 code unit. Việc ghi text ra file, socket, hoặc một `OutputStream` cần một bước **mã hóa** (`encode`). Khi đọc byte thành text, cần **giải mã** (`decode`).

| Tầng dữ liệu | Đại diện | Đơn vị |
|---|---|---|
| Byte I/O | `InputStream`, `OutputStream`, `byte[]` | 8-bit byte |
| Character I/O | `Reader`, `Writer`, `char[]` | UTF-16 code unit |
| Unicode code point | `int`, `String.codePoints()` | Giá trị code point (có thể tương ứng một hoặc hai code unit) |
| Ký tự nhìn thấy | Grapheme cluster, giao diện người dùng | Có thể gồm nhiều code point |

Cầu nối phổ biến:

- **`InputStreamReader(input, StandardCharsets.UTF_8)`** giải mã byte từ input stream thành `char`.
- **`OutputStreamWriter(output, StandardCharsets.UTF_8)`** mã hóa `char` rồi ghi byte vào output stream.
- `Files.newBufferedReader` và `Files.newBufferedWriter` kết hợp mở file, chuyển charset và buffering theo cách tiện dụng.

Không nên phụ thuộc vào **charset mặc định của hệ thống** khi yêu cầu đọc/ghi văn bản chính xác giữa các máy. Hãy chọn charset theo định dạng dữ liệu hoặc giao thức, ví dụ `StandardCharsets.UTF_8`, và dùng thống nhất ở cả hai phía. Sai charset có thể làm biến dạng văn bản; xử lý chuỗi byte không hợp lệ còn tùy chính sách của bộ giải mã (thay thế ký tự lỗi hay báo lỗi).

### Ví dụ 3 — byte ⇄ char với `OutputStreamWriter` và `InputStreamReader`

Tạo tệp `EncodingBridgeDemo.java`:

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class EncodingBridgeDemo {
    public static void main(String[] args) throws Exception {
        String text = "Chữ 😊";
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(sink, StandardCharsets.UTF_8)) {
            writer.write(text);
        }
        byte[] bytes = sink.toByteArray();
        StringWriter result = new StringWriter();
        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            char[] buffer = new char[2];
            int n;
            while ((n = reader.read(buffer, 0, buffer.length)) != -1) {
                result.write(buffer, 0, n);
            }
        }
        String copy = result.toString();
        System.out.println("UTF-8 bytes=" + bytes.length);
        System.out.println("UTF-16 units=" + copy.length());
        System.out.println("same=" + text.equals(copy));
    }
}
```

```bash
javac -encoding UTF-8 --release 17 EncodingBridgeDemo.java
java EncodingBridgeDemo
```

```text
UTF-8 bytes=10
UTF-16 units=6
same=true
```

Đoạn `"Chữ 😊"` có sáu UTF-16 code unit: `C`, `h`, `ữ`, dấu cách, hai unit của emoji. Nhưng sau khi mã hóa UTF-8, dãy dữ liệu cần **10 byte**. `OutputStreamWriter` nhận văn bản và tạo byte; `InputStreamReader` đọc lại byte và tái lập văn bản.

Bộ đệm ký tự trong ví dụ chỉ dài hai phần tử nhằm thể hiện việc **đọc từng khối**. Ngay cả khi một surrogate pair nằm qua ranh giới hai khối, thao tác `StringWriter.write(buffer, 0, n)` sao chép lại đúng chuỗi các code unit. Không giả định `n == 2`; lần đọc có thể trả ít hơn kích thước buffer.

`OutputStreamWriter` có thể giữ byte/code unit đang đệm, nên cần **flush hoặc close** trước khi lấy toàn bộ kết quả ở đích. Ví dụ đóng writer bằng `try-with-resources`; `ByteArrayOutputStream` trong bộ nhớ vẫn cho phép truy xuất `toByteArray()` sau đó.

## 6. Cách quản lý tài nguyên và buffering

`Reader` và `Writer` là các lớp triển khai `Closeable`, dùng được trong `try-with-resources`. Khi mở file, socket hay kết nối khác, hãy xác định **đối tượng nào sở hữu tài nguyên** và đóng nó đúng thời điểm:

```java
try (BufferedReader reader =
         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý từng dòng tại đây.
    }
}
```

Đây là **mẫu minh họa**, cần định nghĩa biến `path` và xử lý/khai báo `IOException` tại nơi gọi. Khi `BufferedReader` được đóng, nó cũng đóng `Reader` nằm bên dưới. Tương tự, đóng `BufferedWriter` làm đóng writer bên dưới; không đóng nguồn byte trước khi lớp bao ngoài hoàn tất việc đọc/ghi.

**`flush()` khác `close()`:**

- `flush()` đẩy dữ liệu đang được `Writer` giữ trong bộ đệm xuống các tầng bên dưới và vẫn cho phép ghi tiếp (nếu đích hỗ trợ).
- `close()` hoàn tất và giải phóng tài nguyên; với `BufferedWriter`/`OutputStreamWriter`, việc đóng đảm bảo flush phần dữ liệu đang đệm theo hợp đồng của lớp, nhưng có thể ném `IOException`.
- `flush()`/`close()` **không thay thế** yêu cầu đồng bộ bền vững xuống lưu trữ vật lý (ví dụ `FileChannel.force`) khi ứng dụng cần bảo đảm durability đặc biệt.

**Bộ đệm phù hợp cho thao tác nhỏ:** gọi `write` từng `char` không phải lúc nào cũng hiệu quả trên file hoặc mạng. Bọc writer bằng `BufferedWriter` hoặc dùng `Files.newBufferedWriter`; tương tự `BufferedReader` giúp hạn chế đọc lẻ từ nguồn. Không tự mặc định một buffer lớn hơn luôn tốt: quy mô, giao thức, dung lượng và thời gian phản hồi mới quyết định.

**Các lớp trong bộ nhớ:** `StringReader` và `StringWriter` vẫn tuân theo mô hình `Reader`/`Writer` nhưng không nắm file descriptor. Việc đóng chúng có sắc thái riêng theo triển khai; vẫn có thể dùng `try-with-resources` khi muốn thể hiện ranh giới sở hữu rõ ràng.

## 7. Các lỗi phổ biến và cách khắc phục

1. **Nghĩ `char` luôn là một Unicode code point.** Emoji ngoài BMP chiếm hai `char`. Muốn thao tác theo code point, dùng `String.codePoints()`, `Character.codePointAt(...)` và `Character.toChars(...)` thay vì xử lý từng `char` độc lập.
2. **Ép kết quả `read()` sang `char` trước khi kiểm tra `-1`.** Hãy nhận bằng `int`, so sánh với `-1`, rồi mới sử dụng giá trị đọc được.
3. **Giả sử `read(buffer)` luôn điền đầy mảng.** Lấy đúng số đơn vị `n` mà API trả về; khi ghi lại, gọi `write(buffer, 0, n)`.
4. **Dùng `ready()` để quyết định đã hết file.** `ready() == false` không tương đương EOF. Đọc tới lúc `read()` trả `-1` hoặc `readLine()` trả `null`.
5. **Nhầm dòng trống với EOF.** `readLine()` trả `""` cho dòng rỗng hợp lệ; `null` mới là hết dữ liệu.
6. **Dùng sai charset hoặc bỏ qua charset.** File UTF-8 đọc bằng charset khác có thể trở thành chữ lỗi; định nghĩa mã hóa rõ ràng ngay tại biên byte↔char.
7. **Dùng `Reader`/`Writer` để sao chép dữ liệu nhị phân.** Chuyển mã có thể biến đổi byte; ảnh, ZIP và nội dung nhị phân nên đi qua `InputStream`/`OutputStream` hoặc API byte tương ứng.
8. **Xử lý riêng từng nửa surrogate.** Một lần `read()` hoặc một khối buffer không bảo đảm ranh giới code point. Nếu sửa nội dung Unicode, phải chú ý kết hợp surrogate, dấu kết hợp và trường hợp có nhiều code point tạo thành một ký tự hiển thị.
9. **Bỏ `close()` hoặc quên `flush()` khi cần.** Với tài nguyên I/O, dùng `try-with-resources` và để lớp bao ngoài kiểm soát vòng đời của lớp bên dưới.
10. **Giả định `write(String)` hoặc `readLine()` giữ nguyên dấu xuống dòng.** `write(String)` không tự thêm xuống dòng; `readLine()` loại bỏ terminator. Khi bảo toàn định dạng newline quan trọng, đọc/ghi ký tự thô theo khối hoặc xử lý rõ chính sách dấu xuống dòng.
11. **Giả định các writer đều báo lỗi theo cùng cách.** `Writer` thường ném `IOException`; riêng `PrintWriter` có các phương thức in không ném `IOException` trong thao tác in thông thường và cung cấp `checkError()`. Hiểu hợp đồng của lớp thực tế.
12. **Xem `Reader`/`Writer` là đồng nghĩa `java.util.stream.Stream`.** Reader/Writer là API I/O theo `char`; `java.util.stream.Stream<T>` là pipeline xử lý phần tử. `BufferedReader.lines()` có thể tạo `Stream<String>` để xử lý dòng, nhưng vẫn cần quản lý reader đã mở.

## 8. Khi nào chọn lớp nào?

| Nhu cầu | Lựa chọn thường dùng | Lý do |
|---|---|---|
| Đọc file văn bản, xử lý từng dòng | `Files.newBufferedReader(path, UTF_8)` | `BufferedReader.readLine()` tiện lợi, có buffer |
| Ghi file văn bản, cần xuống dòng | `Files.newBufferedWriter(path, UTF_8)` | `write` và `newLine`, có buffer |
| Chuyển byte từ socket/file thành text | `InputStreamReader` + charset, có thể bọc `BufferedReader` | Giải mã theo đúng charset |
| Chuyển text thành byte cho socket/file | `OutputStreamWriter` + charset, có thể bọc `BufferedWriter` | Mã hóa theo đúng charset |
| Thử nghiệm hoặc tạo nội dung text trong bộ nhớ | `StringReader` / `StringWriter` | Không cần nguồn/đích hệ điều hành |
| Sao chép văn bản theo `char` | `Reader.transferTo(writer)` hoặc vòng lặp `read(char[])`/`write(char[],0,n)` | Sao chép dữ liệu ký tự đúng số lượng |
| Sao chép file nhị phân nguyên vẹn | `InputStream`/`OutputStream`, `Files.copy` | Giữ byte, không chuyển mã |

## 9. Kiểm tra hiểu bài

**Tình huống A.** Một `Reader` trả lần lượt `0xD83D` và `0xDE0A`. Có phải nguồn chứa hai emoji? **Không**. Đó là một surrogate pair biểu diễn một code point U+1F60A (😊).

**Tình huống B.** `reader.read(buffer)` trả `3` trong khi mảng dài `1024`. Có nên ghi toàn bộ `1024` phần tử? **Không**. Chỉ xử lý/ghi `buffer[0..3)`; phần còn lại không thuộc lần đọc này.

**Tình huống C.** `BufferedReader.readLine()` trả `""`. Đã đến EOF? **Chưa chắc; thông thường đó là dòng rỗng**. EOF được báo bằng `null`.

**Tình huống D.** File chứa tiếng Việt được ghi qua `OutputStreamWriter` nhưng đọc ra bị lỗi dấu. Kiểm tra gì trước? **Charset khi mã hóa và giải mã**, sau đó kiểm tra byte đầu vào và chính sách xử lý dữ liệu không hợp lệ.

**Tình huống E.** Cần lưu ảnh PNG mà không làm thay đổi một byte nào. Dùng `Writer`? **Không**. Chọn API I/O byte; `Writer` dành cho văn bản và có thể thực hiện chuyển mã.

## Kết luận

**`Reader` và `Writer` thao tác trên UTF-16 `char`, không trực tiếp trên byte hoặc Unicode code point.** `Reader.read()` trả một code unit dưới dạng `int` hoặc `-1` khi EOF; `Writer.write(...)` nhận code unit hay chuỗi code unit. Khi đi qua file, mạng hoặc nguồn byte, phải có một charset để **giải mã** hoặc **mã hóa**. Với văn bản thực tế, `BufferedReader`/`BufferedWriter` và `try-with-resources` giúp đọc/ghi hiệu quả, rõ quyền sở hữu tài nguyên và tránh bỏ sót dữ liệu đệm.

### Tài liệu tra cứu Java SE 17

- [`java.io.Reader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Reader.html) và [`java.io.Writer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Writer.html)
- [`InputStreamReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html) và [`OutputStreamWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html)
- [`BufferedReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html) và [`BufferedWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html)
- [`java.lang.Character`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html), [`java.lang.String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Files.newBufferedReader`, `Files.newBufferedWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-002">Đọc và ghi char&#91;&#93;, String và từng phần dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc và ghi char[], String và từng phần dữ liệu trong luồng ký tự Java

## 1. Mục tiêu và bức tranh chung

Trong Java I/O, **luồng ký tự** (`Reader` và `Writer`) cho phép ứng dụng đọc, ghi nội dung văn bản theo các đơn vị `char` của Java. Khi dữ liệu có thể dài, đến chậm hoặc được chia thành nhiều phần, ta thường dùng **mảng `char[]` làm bộ đệm** thay vì đọc toàn bộ văn bản thành một `String`.

Bài này giải quyết các câu hỏi thực tế:

- `Reader.read(char[])` đọc bao nhiêu ký tự trong một lần? Tại sao không được giả định mảng luôn đầy?
- `char[]`, `String` và `StringBuilder` có vai trò khác nhau ra sao khi lưu, xử lý và ghi dữ liệu?
- `off` và `len` trong `read`/`write` có nghĩa gì? Làm thế nào tránh lẫn vị trí bắt đầu với vị trí kết thúc?
- Làm thế nào sao chép từng phần văn bản mà không thêm ký tự cũ, mất dữ liệu hay phá vỡ nội dung?
- Khi nào cần quan tâm đến Unicode, charset, bộ đệm và việc đóng luồng?

Một đường đi điển hình:

~~~text
Nguồn văn bản (tệp hoặc chuỗi)
          │
          ▼
      Reader
          │ read(char[], off, len)
          ▼
   char[] dùng lại nhiều lần
          │ write(char[], 0, count)
          ▼
      Writer
          │
          ▼
Đích văn bản (tệp hoặc vùng nhớ)
~~~

`Reader` và `Writer` thao tác trên **ký tự Java**; nếu đầu vào hoặc đầu ra thực sự là tệp hay kết nối mạng, byte sẽ được giải mã/mã hóa qua một charset tại ranh giới I/O. `char[]` chỉ là vùng nhớ trung gian. Việc đọc theo từng phần giúp sử dụng bộ nhớ có giới hạn, độc lập với độ dài tài liệu.

## 2. Ba cách biểu diễn văn bản: char[], String và luồng ký tự

| Thành phần | Vai trò | Điểm cần nhớ |
| --- | --- | --- |
| `char` | Một đơn vị mã UTF-16 16 bit | Không luôn biểu diễn trọn một ký tự Unicode theo nghĩa code point |
| `char[]` | Mảng ký tự có thể sửa, có độ dài cố định | Có thể tái sử dụng làm bộ đệm; các vị trí cũ không tự biến mất |
| `String` | Chuỗi văn bản bất biến | Thích hợp cho đoạn văn bản đã thu được hoặc tham số cần truyền |
| `StringBuilder` | Bộ tích lũy ký tự trong bộ nhớ, có thể thay đổi | Phù hợp khi cần nối nhiều đoạn thành một chuỗi cuối cùng |
| `Reader` | Giao diện lớp trừu tượng để nhận ký tự theo thứ tự | `read` có thể trả về ít hơn lượng yêu cầu; `-1` nghĩa là hết dữ liệu |
| `Writer` | Giao diện lớp trừu tượng để đưa ký tự ra đích | `write` không trả về số ký tự đã ghi; có thể ném `IOException` |

Ví dụ, biểu thức `new char[1024]` tạo mảng chứa 1024 đơn vị `char`. Mỗi lượt đọc có thể chỉ ghi đè phần đầu của mảng. Nếu một lần chỉ đọc được 7 đơn vị, **chỉ 7 phần tử đầu là dữ liệu mới của lượt đó** khi dùng `read(buffer)`.

`String` không thể thay đổi tại chỗ. Nếu cần biến phần dữ liệu mới đọc thành chuỗi, dùng `new String(buffer, 0, count)`; chuỗi này chứa **đúng `count` phần tử**. Tránh `new String(buffer)` khi chỉ một phần mảng hợp lệ, vì chuỗi khi đó có thể chứa ký tự tồn dư hoặc giá trị `\u0000` chưa được ghi.

Khi cần xử lý dữ liệu lớn, không nhất thiết tạo `String` ở mỗi lần đọc: có thể chuyển thẳng `char[]` từ `Reader` sang `Writer`, hoặc xử lý phần dữ liệu hợp lệ rồi ghi. Việc liên tục nối `result += chunk` có thể tạo nhiều đối tượng `String` trung gian.

## 3. Hợp đồng đọc: Reader.read() và Reader.read(char[])

### 3.1. Đọc một đơn vị char

`Reader.read()` trả về một `int`:

- Giá trị không âm biểu diễn một đơn vị ký tự đã đọc (0–65535).
- `-1` biểu diễn **kết thúc luồng** (EOF).
- Lỗi I/O thường được báo bằng `IOException`.

Dùng `int` thay vì `char` để giữ được dấu hiệu `-1`:

~~~java
int codeUnit;
while ((codeUnit = reader.read()) != -1) {
    char ch = (char) codeUnit;
    // Xử lý ch.
}
~~~

Đây là đoạn minh họa: `reader` là một `Reader` đã mở và phải được đóng đúng quyền sở hữu. Cách đọc từng đơn vị dễ hiểu nhưng thường không tối ưu cho luồng lớn so với đọc vào mảng.

### 3.2. Đọc vào mảng

`read(char[] cbuf)` có thể đọc **tối đa** `cbuf.length` ký tự; giá trị trả về là số phần tử thực sự được lấp bằng dữ liệu mới, hoặc `-1` nếu luồng đã kết thúc. Đối với độ dài yêu cầu dương, hợp đồng của `Reader` sẽ chờ cho đến khi có dữ liệu, EOF hoặc lỗi, nhưng **không hứa đọc đủ toàn bộ độ dài yêu cầu**.

~~~java
char[] buffer = new char[4096];
int count;
while ((count = reader.read(buffer)) != -1) {
    writer.write(buffer, 0, count);
}
~~~

Với đoạn minh họa này, `reader` và `writer` là các đối tượng đã được tạo bên ngoài. Dòng `writer.write(buffer, 0, count)` là yếu tố then chốt: **ghi đúng phần dữ liệu vừa đọc**.

Các giá trị `count` có thể thay đổi:

~~~text
Bộ đệm dài 4; nguồn "ABCDEFGHIJ"

Lượt 1: count=4, dữ liệu mới ABCD
Lượt 2: count=4, dữ liệu mới EFGH
Lượt 3: count=2, dữ liệu mới IJ; hai ô còn lại vẫn có thể giữ GH
Lượt 4: count=-1, EOF
~~~

Nếu lượt 3 gọi `writer.write(buffer)`, hai phần tử cũ có thể bị ghi thêm thành `IJGH`. Nếu gọi `new String(buffer)` rồi nối vào kết quả, lỗi tương tự xuất hiện.

### 3.3. Trường hợp mảng rỗng

`read(new char[0])` yêu cầu đọc 0 phần tử và có thể trả về `0` ngay, kể cả chưa đến EOF. Vì thế, **không dùng bộ đệm độ dài 0** trong vòng lặp đọc thông thường. Với bộ đệm dương, vẫn nên viết thuật toán dựa trên số ký tự thực sự trả về thay vì suy đoán từ kích thước buffer hoặc dung lượng tệp.

Đọc luồng ký tự có thể chặn thread khi dữ liệu chưa sẵn sàng. Không dùng `reader.ready() == false` để kết luận đã EOF: `ready()` chỉ cho biết lượt đọc kế tiếp có thể tiến hành mà không chặn theo hợp đồng của lớp, không có nghĩa đã đọc hết dữ liệu.

## 4. Vùng đọc: read(char[] buffer, int off, int len)

Dạng ba tham số cho phép ghi vào **một đoạn** của mảng đích:

~~~java
int count = reader.read(buffer, off, len);
~~~

- `buffer` là mảng nhận dữ liệu.
- `off` là chỉ số **bắt đầu** trong mảng (tính từ 0).
- `len` là **số ký tự tối đa muốn đọc**, không phải chỉ số kết thúc.
- Giá trị trả về `count` là số ký tự thực tế, hoặc `-1` ở EOF khi yêu cầu có độ dài dương.

Điều kiện hợp lệ: `off >= 0`, `len >= 0` và `off + len <= buffer.length` về mặt toán học. Để tránh tràn số khi tự kiểm tra, có thể đánh giá bằng `off <= buffer.length` rồi `len <= buffer.length - off`.

Ví dụ độc lập minh họa vị trí điền dữ liệu:

~~~java
import java.io.StringReader;
import java.util.Arrays;

public class ReadSliceDemo {
    public static void main(String[] args) throws Exception {
        char[] destination = {'_', '_', '_', '_', '_'};

        try (StringReader reader = new StringReader("ABC")) {
            int count = reader.read(destination, 1, 3);
            System.out.println("count=" + count);
            System.out.println(Arrays.toString(destination));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ReadSliceDemo.java
java ReadSliceDemo
~~~

Kết quả:

~~~text
count=3
[_, A, B, C, _]
~~~

Có hai điều phải chú ý: dữ liệu chỉ được đặt từ chỉ số 1 đến 3; hai phần tử ngoài đoạn đọc không bị thay đổi. Nếu `count` bằng 2, đoạn mới hợp lệ chỉ là `destination[1]` và `destination[2]`. Khi ghi tiếp, dùng `writer.write(destination, 1, count)`, **không** dùng `writer.write(destination, 1, 3)` theo độ dài đã yêu cầu.

Đọc với `off` và `len` hữu ích khi mảng chứa nhiều vùng, khi ghép thêm vào phần đang có, hoặc khi giao thức xử lý chỉ cho phép ghi vào một đoạn nhất định. Trong ví dụ sao chép toàn bộ luồng, `read(buffer)` ngắn gọn hơn.

## 5. Ghi char[], String và từng đoạn

### 5.1. Writer.write(char[])

`writer.write(charArray)` ghi **toàn bộ mảng**, từ chỉ số 0 đến `charArray.length - 1`. Phương thức này phù hợp khi mọi phần tử đều có ý nghĩa.

`writer.write(charArray, off, len)` chỉ ghi `len` phần tử bắt đầu tại `off`. Đây là cách đúng khi mảng chỉ chứa `count` ký tự hợp lệ từ một lần đọc.

~~~java
char[] chars = {'J', 'a', 'v', 'a', '!', '?'};

writer.write(chars, 0, 5); // Ghi "Java!"
writer.write(chars, 2, 3); // Ghi "va!"
~~~

Đoạn này mang tính minh họa với `writer` đã được cung cấp. `write` trả về `void`; nếu thao tác không thành công, nó có thể báo lỗi thay vì trả số ký tự đã ghi. Không giả định `write` tự thêm dấu kết thúc dòng; nếu cần xuống dòng, phải ghi `"\n"`, `System.lineSeparator()` hoặc sử dụng phương thức dòng phù hợp với mục đích.

### 5.2. Writer.write(String)

`writer.write(text)` ghi toàn bộ `String`. `writer.write(text, off, len)` ghi đúng đoạn bắt đầu tại chỉ số `off` với độ dài `len`, cũng tính theo **đơn vị UTF-16**.

Ví dụ:

~~~java
writer.write("abcdef", 2, 3); // "cde", không phải "c"
~~~

Bởi tham số cuối là **độ dài**, không phải chỉ số kết thúc. So sánh `String.substring(beginIndex, endIndex)`: tham số cuối của `substring` là chỉ số loại trừ, nên `"abcdef".substring(2, 3)` chỉ tạo `"c"`. Nhầm hai hợp đồng này có thể khiến mất hoặc ghi thừa ký tự.

### 5.3. Append khác write ở cặp chỉ số

`writer.append("abcdef", 2, 5)` sử dụng `start` và `end` (end loại trừ), do hợp đồng `Appendable`; kết quả là `"cde"`. Ngược lại, `writer.write("abcdef", 2, 5)` yêu cầu **5** đơn vị tính từ chỉ số 2, vượt quá chuỗi dài 6 và không hợp lệ.

| Lời gọi | Cách hiểu tham số | Phần được lấy |
| --- | --- | --- |
| `write("abcdef", 2, 3)` | off=2, len=3 | `cde` |
| `append("abcdef", 2, 3)` | start=2, end=3 | `c` |
| `"abcdef".substring(2, 3)` | begin=2, end=3 | `c` |
| `write(new char[]{'a','b','c'}, 1, 2)` | off=1, len=2 | `bc` |

**Không dùng chung một biến tên `end` cho tham số `len` nếu chưa chuyển đổi**; chọn tên `offset`, `length` hoặc `start`, `endExclusive` để làm rõ ý nghĩa.

### 5.4. Ví dụ runnable về cách ghi đoạn

~~~java
import java.io.StringWriter;

public class WriteSliceDemo {
    public static void main(String[] args) throws Exception {
        try (StringWriter writer = new StringWriter()) {
            writer.write("abcdef", 1, 3);           // bcd
            writer.write(new char[]{'1','2','3','4'}, 1, 2); // 23
            writer.append("XYZ", 1, 3);             // YZ
            System.out.println(writer.toString());
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 WriteSliceDemo.java
java WriteSliceDemo
~~~

Kết quả:

~~~text
bcd23YZ
~~~

`StringWriter` ghi vào bộ đệm ký tự trong RAM, nên ví dụ chạy mà không cần mở tệp. `StringWriter.close()` không có tác động làm mất nội dung tích lũy; với `FileWriter` hoặc một `Writer` gắn với tệp/socket, việc đóng tài nguyên cần thiết hơn nhiều.

## 6. Sao chép từng phần và tái sử dụng buffer

Kỹ thuật nền tảng khi xử lý văn bản lớn là một vòng lặp:

1. Tạo một mảng ký tự **có độ dài dương**, cố định.
2. Đọc vào mảng; nhận `count`.
3. Nếu `count == -1` thì dừng vì đã EOF.
4. Xử lý hoặc ghi **đúng `count` ký tự**; không đụng vào phần còn lại.
5. Lặp lại cho đến hết dữ liệu, rồi đóng tài nguyên theo trách nhiệm.

Một ví dụ đầy đủ có thể quan sát số phần tử mỗi lần đọc:

~~~java
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class CharChunkDemo {
    public static void main(String[] args) throws Exception {
        try (Reader reader = new StringReader("abcdefg");
             Writer writer = new StringWriter()) {

            char[] buffer = new char[3];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                System.out.println("read=" + count
                        + " segment=" + new String(buffer, 0, count));
                writer.write(buffer, 0, count);
            }

            System.out.println("result=" + writer);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CharChunkDemo.java
java CharChunkDemo
~~~

Kết quả chính xác:

~~~text
read=3 segment=abc
read=3 segment=def
read=1 segment=g
result=abcdefg
~~~

Trong lượt cuối, mảng vật lý có thể đang chứa `g` cùng ký tự còn lại từ lượt trước, nhưng `new String(buffer, 0, 1)` và `write(buffer, 0, 1)` chỉ dùng `g`. Vì `StringReader` đọc từ bộ nhớ đã có sẵn, đây là ví dụ có phân đoạn dễ dự đoán; với luồng tệp hoặc mạng thực, số ký tự mỗi lần đọc có thể khác giữa các lượt và môi trường.

Không cần xóa mảng bằng `Arrays.fill(buffer, '\0')` sau mỗi lần đọc để bảo đảm đúng dữ liệu, miễn là thuật toán luôn tuân thủ `count` và không sử dụng vùng cũ. Tùy loại dữ liệu nhạy cảm, việc xóa sau sử dụng có thể là yêu cầu riêng, nhưng không thay thế quy tắc đọc/ghi theo độ dài hợp lệ.

### 6.1. Nếu cần tích lũy thành String

Khi thật sự phải thu toàn bộ nội dung để xử lý trong bộ nhớ:

~~~java
StringBuilder result = new StringBuilder();
char[] buffer = new char[1024];
int count;

while ((count = reader.read(buffer)) != -1) {
    result.append(buffer, 0, count);
}

String text = result.toString();
~~~

Đây là đoạn minh họa dùng `Reader reader` đã có sẵn. `StringBuilder.append(char[], off, len)` cũng dùng `off` và `len`. Khả năng tiếp nhận toàn bộ dữ liệu phụ thuộc bộ nhớ heap. Với tệp lớn, cách chuyển thẳng từ Reader sang Writer hoặc xử lý trực tiếp từng khối thường phù hợp hơn.

Để nhận chuỗi từ một nguồn nhỏ đã biết, `StringWriter` cũng là một lựa chọn lưu trong bộ nhớ. Một số API tiện ích như `Reader.transferTo(Writer)` (Java 10+) có thể tự chuyển toàn bộ dữ liệu và trả về số `char` đã chuyển; nó **không tự đóng** Reader và Writer, nên vẫn cần quản lý vòng đời bên ngoài.

## 7. Ví dụ Java 17: đọc và ghi tệp văn bản theo từng khối char[]

Ví dụ sau tạo thư mục tạm, ghi văn bản UTF-8 làm dữ liệu nguồn, sau đó sao chép bằng `BufferedReader` và `BufferedWriter` trong từng lần đọc. Đích được đọc lại để xác minh nội dung; mọi luồng tự mở đều được đóng qua `try-with-resources`.

Lưu vào tệp **CopyTextChunksDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyTextChunksDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("char-chunks-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");
        String text = "Chào Java!\nDòng 2";

        try {
            Files.writeString(source, text, StandardCharsets.UTF_8);

            long copiedUnits = 0;
            try (BufferedReader reader =
                         Files.newBufferedReader(source, StandardCharsets.UTF_8);
                 BufferedWriter writer =
                         Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {

                char[] buffer = new char[4];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, count);
                    copiedUnits += count;
                }
            }

            String copied = Files.readString(target, StandardCharsets.UTF_8);
            System.out.println("UTF-16 units: " + copiedUnits);
            System.out.println("Equal content: " + text.equals(copied));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CopyTextChunksDemo.java
java CopyTextChunksDemo
~~~

Kết quả:

~~~text
UTF-16 units: 17
Equal content: true
~~~

Lý do kết quả là `17`: `"Chào Java!"` có 10 đơn vị UTF-16, dấu xuống dòng `\n` có 1, `"Dòng 2"` có 6. Giá trị `copiedUnits` ghi lại **số đơn vị `char`**, không phải số byte UTF-8 trên đĩa. Ký tự có dấu trong văn bản có thể chiếm nhiều byte UTF-8 dù ở đây mỗi ký tự dùng một đơn vị UTF-16.

Giải thích cách chương trình giữ dữ liệu đúng:

- `newBufferedReader` giải mã các byte UTF-8 từ tệp thành luồng ký tự; `newBufferedWriter` mã hóa ký tự trở lại UTF-8.
- `char[4]` là bộ đệm nhỏ có chủ đích để minh họa quá trình chia khối. Trong chương trình thực, có thể chọn bộ đệm lớn hơn và đo hiệu năng bằng dữ liệu thực.
- `writer.write(buffer, 0, count)` ghi đúng đoạn vừa đọc, kể cả lần cuối chưa đầy mảng.
- Sau khi ra khỏi `try-with-resources`, writer đã được đóng và phần bộ đệm của nó đã được chuyển xuống luồng bên dưới; lúc đó mới mở tệp đích để đọc kiểm tra.
- `finally` dọn các tệp/thư mục thử nghiệm. Với ứng dụng có yêu cầu chẩn đoán lỗi cao, nên xử lý riêng ngoại lệ dọn dẹp để không che ngoại lệ xảy ra trước đó.

Vì đây là sao chép **văn bản**, việc giải mã rồi mã hóa lại là có chủ đích. Nếu cần bảo toàn nguyên vẹn từng byte của tệp bất kỳ, nên dùng byte stream hoặc `Files.copy`; đừng tùy tiện dùng `Reader`/`Writer` cho dữ liệu nhị phân.

## 8. Dữ liệu đến theo nhiều phần: điều gì được bảo đảm?

Giả sử giao thức gửi chuỗi `"HELLO"` nhưng nguồn chỉ sẵn có `"HE"` tại thời điểm đọc. `read(buffer)` có thể trả 2; lần sau trả 3. **Một lượt đọc không nhất thiết tương ứng một thông điệp, một dòng hoặc một đoạn người gửi đã ghi.**

Vì vậy, các cấu trúc như “đọc một lần cho đủ 100 ký tự” hoặc “một lần đọc là một bản ghi” là không an toàn nếu không có quy tắc xác định ranh giới. Thường có ba chiến lược:

1. **Đọc cho đến EOF** khi toàn bộ nguồn đại diện cho một nội dung. Dùng vòng lặp `read` như các ví dụ trên.
2. **Đọc đủ một độ dài được chỉ định** khi dữ liệu có phần đầu cung cấp độ dài hoặc hợp đồng yêu cầu chính xác N đơn vị. Lặp và cộng số đã đọc, xử lý EOF sớm như lỗi thiếu dữ liệu theo hợp đồng.
3. **Đọc theo dấu phân cách** khi dữ liệu được tổ chức thành dòng hoặc bản ghi có delimiter. `BufferedReader.readLine()` là API cho dòng văn bản, nhưng nó bỏ dấu kết thúc dòng khỏi kết quả.

Ví dụ đoạn mã minh họa cho yêu cầu đọc đúng `required` đơn vị `char`:

~~~java
char[] data = new char[required];
int offset = 0;

while (offset < required) {
    int count = reader.read(data, offset, required - offset);
    if (count == -1) {
        throw new java.io.EOFException("Văn bản kết thúc trước độ dài yêu cầu");
    }
    offset += count;
}
~~~

Giả định `required` là số không âm đã được kiểm tra và `reader` hợp lệ. Đoạn mã chưa phải một chương trình Java độc lập. Một nguồn đặc biệt có thể trả `0` lặp lại dù yêu cầu có độ dài dương; nếu làm việc với triển khai `Reader` không chuẩn hoặc luồng có giao ước riêng, cần thiết kế cơ chế tiến triển/tránh vòng lặp vô hạn. Với những Reader tuân thủ hợp đồng chuẩn, thao tác đọc độ dài dương thông thường sẽ chờ có dữ liệu, EOF hoặc lỗi.

**Không chọn `available()` để quyết định đọc đủ bao nhiêu ký tự**: đây là phương thức của `InputStream` (byte stream), không phải công cụ xác định độ dài dữ liệu cuối cùng. Tương tự, `ready()` của Reader không phải dấu hiệu EOF.

### 8.1. Không lẫn “một lần ghi” với “một lần nhận”

Phía ghi có thể gọi `writer.write("HELLO")` một lần nhưng phía đọc vẫn nhận dữ liệu làm nhiều phần. Điều này đặc biệt đúng với các giao thức truyền dữ liệu liên tục và lớp đệm trung gian. Nếu ranh giới thông điệp quan trọng, giao thức phải mã hóa ranh giới bằng độ dài, dấu phân cách có quy tắc escaping, hoặc cấu trúc bản ghi thích hợp.

## 9. Unicode: một char không luôn là một ký tự người dùng nhìn thấy

Java `char` là **một đơn vị mã UTF-16**. Hầu hết ký tự thông dụng như `A` hay `à` được biểu diễn bằng một đơn vị `char`; nhiều ký tự ngoài Basic Multilingual Plane, ví dụ một số emoji, cần **cặp surrogate** gồm hai đơn vị `char`.

Ví dụ:

~~~java
public class UnicodeUnitsDemo {
    public static void main(String[] args) {
        String text = "A😀B";
        System.out.println("UTF-16 units: " + text.length());
        System.out.println("Code points: " + text.codePointCount(0, text.length()));
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnicodeUnitsDemo.java
java UnicodeUnitsDemo
~~~

Kết quả:

~~~text
UTF-16 units: 4
Code points: 3
~~~

Chuỗi gồm ba code point (`A`, emoji, `B`) nhưng bốn đơn vị `char`. **Một lần `read(char[])` có thể chia dữ liệu ở ranh giới giữa hai đơn vị surrogate**; khi sao chép không biến đổi toàn bộ dữ liệu theo thứ tự, việc chuyển tuần tự qua Reader/Writer thích hợp sẽ giữ nguyên chuỗi đơn vị mã, nhưng nếu xử lý độc lập từng khối bằng cách đảo, cắt, lọc hoặc giải mã lại, cần giữ trạng thái giữa các khối để không tách cặp surrogate thành phần rời rạc.

Đừng hiểu `count` của `read` hoặc `Writer.write` là số “ký tự nhìn thấy” hay số code point. Muốn đếm hoặc thao tác theo code point trên `String`, có thể dùng `codePoints()` hoặc `Character.codePointAt` theo đúng ngữ cảnh. Ngay cả code point cũng không luôn bằng một ký tự hiển thị hoàn chỉnh: dấu kết hợp có thể đi với chữ cái trước đó.

### 9.1. Charset nằm ở ranh giới đọc/ghi byte

`StringReader` và `StringWriter` chỉ xử lý ký tự trong RAM; chúng không chọn UTF-8 hay UTF-16 cho tệp. Với tệp, nên nêu charset rõ ràng bằng những API như:

~~~java
try (BufferedReader reader =
             Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    // Đọc các đơn vị char đã được giải mã từ byte UTF-8.
}
~~~

Với dữ liệu đã có sẵn dưới dạng byte, `InputStreamReader(input, UTF_8)` làm cầu nối byte → ký tự; `OutputStreamWriter(output, UTF_8)` làm cầu nối ký tự → byte. Khi charset nguồn và đích khác nhau, văn bản có thể được chuyển mã một cách có chủ đích. Nếu charset đọc sai, `char[]` có thể chứa văn bản đã lỗi trước cả khi logic ghi bắt đầu.

### 9.2. Nếu phải xử lý đoạn một cách độc lập

Một thuật toán như “mỗi khối 4 char đem đảo ngược rồi ghi ngay” có thể phá cặp surrogate hoặc dấu kết hợp khi nằm sát ranh giới. Giải pháp tùy mục đích: xử lý theo code point, giữ lại phần chưa hoàn chỉnh cho khối kế, hoặc dùng bộ xử lý văn bản theo quy tắc Unicode phù hợp. **Ranh giới buffer là chi tiết truyền dữ liệu, không tự nhiên là ranh giới ngữ nghĩa của văn bản**.

## 10. Đọc dòng, giữ dấu xuống dòng và xử lý khối

`BufferedReader.readLine()` trả về một `String` cho một dòng và **loại bỏ dấu xuống dòng** (`\n`, `\r`, hoặc `\r\n`) ở cuối. Nó tiện khi xử lý các dòng log, CSV đơn giản hoặc dữ liệu mà kết thúc dòng không cần giữ nguyên.

Nhưng trong một công cụ **sao chép văn bản chính xác**, nếu đọc từng dòng rồi ghi `writer.write(line)`, mọi dấu xuống dòng bị mất. Nếu ghi thêm `writer.newLine()`, dấu xuống dòng cũng có thể bị chuẩn hóa theo nền tảng, khác với nguồn ban đầu. Khi cần giữ đúng nội dung ký tự, sao chép bằng `read(char[])` và `write(char[], off, count)` như trên.

Ví dụ đầu vào:

~~~text
alpha\r\nbeta\n
~~~

Nếu chuỗi ký hiệu trên biểu diễn dữ liệu chứa `\r\n` sau `alpha` và `\n` sau `beta`, cách đọc/ghi từng khối char sẽ giữ nguyên chuỗi ký tự xuống dòng. Đọc bằng `readLine()` rồi gọi `newLine()` có thể thay đổi biểu diễn. Nếu mục tiêu là **chuẩn hóa** xuống dòng, đó lại là xử lý có chủ đích, nên ghi rõ trong hợp đồng chương trình.

## 11. Buffering, flush và close khi ghi dữ liệu từng phần

`BufferedReader` giảm số lần lấy dữ liệu từ nguồn bên dưới nhờ bộ đệm ký tự; `BufferedWriter` gom các lần ghi nhỏ trước khi đẩy tiếp xuống tầng bên dưới. Khi đã có `BufferedReader` hoặc `BufferedWriter`, dùng thêm một mảng `char[]` trong ứng dụng vẫn hợp lệ: mảng này có thể giúp thao tác theo khối và kiểm soát phần dữ liệu hợp lệ.

**`writer.write(...)` thành công không đồng nghĩa với dữ liệu đã nằm bền vững trên ổ đĩa.** Nếu writer có bộ đệm, dữ liệu có thể đang chờ trong bộ nhớ.

- `flush()` đẩy nội dung đang giữ trong bộ đệm writer xuống tầng tiếp theo theo hợp đồng của lớp; không bảo đảm dữ liệu đã được đồng bộ bền vững xuống thiết bị vật lý.
- `close()` kết thúc quyền sử dụng tài nguyên; với `BufferedWriter`, đóng cũng chuyển phần dữ liệu còn đệm xuống writer bên dưới rồi đóng nó.
- `try-with-resources` giúp gọi `close()` trên cả đường thành công lẫn ngoại lệ; nếu thân `try` và `close()` đều lỗi, lỗi đóng có thể được gắn dưới dạng suppressed exception.
- Với `StringWriter`, dữ liệu ở RAM và `close()` không ảnh hưởng đến nội dung, nhưng kiểu sở hữu vẫn nên được thể hiện nhất quán.

Nếu một hàm **nhận** `Writer` của bên gọi, thường không nên tự đóng nó trừ khi hợp đồng giao quyền sở hữu rõ ràng. Đóng wrapper như `BufferedWriter` thường đóng cả writer bên dưới; điều này đặc biệt quan trọng khi bên gọi tiếp tục dùng tài nguyên sau khi hàm hoàn thành.

## 12. Các lỗi phổ biến khi xử lý từng phần

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `writer.write(buffer)` sau mỗi `read(buffer)` | Lượt cuối có thể đọc ít hơn độ dài mảng; ghi thừa phần cũ | `writer.write(buffer, 0, count)` |
| `new String(buffer)` ở mọi lượt | Lấy cả phần mảng chưa chứa dữ liệu mới | `new String(buffer, 0, count)` |
| Dùng `len` làm chỉ số kết thúc cho `write(String, off, len)` | API yêu cầu độ dài, không phải end index | Tính `len = endExclusive - start` |
| Tưởng `read(buffer)` chắc chắn điền đầy mảng | Reader có thể trả về số dương nhỏ hơn yêu cầu | Lặp cho đến EOF hoặc đủ độ dài giao thức |
| Chỉ cần `ready() == false` là kết thúc | “Chưa sẵn sàng” không đồng nghĩa EOF | Dựa vào `read() == -1` |
| Nối nhiều khối qua `result += chunk` | Tạo nhiều String trung gian, gây tốn bộ nhớ | Dùng `StringBuilder` hoặc ghi trực tiếp |
| Ghi theo `readLine()` rồi quên xuống dòng | `readLine` loại bỏ dấu kết thúc dòng | Dùng char[] để bảo toàn hoặc ghi dòng có chủ đích |
| Xử lý mỗi `char` như một code point hoàn chỉnh | Emoji và ký tự ngoài BMP có cặp surrogate | Áp dụng thao tác Unicode có lưu trạng thái |
| Dùng charset mặc định khi chạy ở nhiều hệ | Văn bản có dấu có thể giải mã/mã hóa khác nhau | Chỉ định `StandardCharsets.UTF_8` hoặc charset của định dạng |
| Không đóng `BufferedReader`/`BufferedWriter` | Có thể rò rỉ tài nguyên hoặc mất dữ liệu đang đệm | Dùng `try-with-resources` theo quyền sở hữu |
| Giữ toàn bộ văn bản rất lớn trong một `String` | Tốn RAM và có thể vượt giới hạn mảng/heap | Xử lý trực tiếp từng phần |

### Checklist khi tự viết một vòng lặp đọc/ghi

Hãy tự trả lời trước khi chạy:

1. Mảng có kích thước lớn hơn 0 không?
2. Có kiểm tra kết quả `-1` để kết thúc hay không?
3. Có chỉ dùng đoạn `[offset, offset + count)` vừa đọc hay không?
4. Nếu cần đọc đúng N đơn vị, có xử lý EOF sớm không?
5. Nếu cần giữ nguyên văn bản, có tránh thao tác phá vỡ dấu xuống dòng và Unicode không?
6. Nếu nguồn là byte/tệp, charset có được xác định không?
7. Reader/Writer do ai mở và ai có trách nhiệm đóng?
8. Có phân biệt số đơn vị UTF-16, số code point và số byte khi báo cáo kết quả không?

## 13. Bài tập tự kiểm tra

**Bài tập 1 — Buffer chưa đầy.** Cho `char[] buffer = new char[5]`. Lượt đọc cuối trả về `2`. Nếu ghi cả mảng, điều gì xảy ra? **Đáp án:** chỉ hai phần tử đầu là dữ liệu mới, ba phần tử cuối có thể chứa dữ liệu cũ hoặc ký tự mặc định. Chỉ ghi `write(buffer, 0, 2)`.

**Bài tập 2 — off/len.** Với `String text = "ABCDEFGHIJ"`, lời gọi `writer.write(text, 3, 4)` ghi gì? **Đáp án:** `"DEFG"`; `off=3` là vị trí `D`, `len=4` là độ dài đoạn. Nếu dùng `append(text, 3, 4)` thì chỉ ghi `"D"`.

**Bài tập 3 — EOF bất ngờ.** Giao thức yêu cầu đọc đúng 10 ký tự nhưng Reader kết thúc sau 7. Có nên coi 7 là đủ? **Đáp án:** không, nếu hợp đồng đòi chính xác 10; cần báo lỗi thiếu dữ liệu. Nếu mục tiêu chỉ là “đọc hết những gì có”, 7 ký tự trước EOF có thể là kết quả hợp lệ.

**Bài tập 4 — Unicode.** `"A😀B".length()` bằng bao nhiêu, và tại sao? **Đáp án:** 4 đơn vị UTF-16; emoji chiếm hai đơn vị. Nếu muốn số code point thì dùng `codePointCount(0, text.length())`, kết quả 3.

**Bài tập 5 — Giữ nguyên xuống dòng.** Vì sao `readLine` → `write(line)` không phải thuật toán sao chép nguyên vẹn? **Đáp án:** `readLine` bỏ các ký tự kết thúc dòng, nên luồng ghi không nhận được những ký tự đó; việc tự thêm `newLine` có thể thay đổi kiểu xuống dòng.

**Thực hành mở rộng:** sửa `CharChunkDemo` để buffer dài 2 và nguồn là một chuỗi có dấu/emoji; quan sát từng lượt đọc mà không giả định ranh giới khối là ranh giới code point. Tiếp tục sửa `CopyTextChunksDemo` để xử lý văn bản có cả `\r\n` và `\n`, rồi so sánh `text.equals(copied)`. Cuối cùng thử thay vòng lặp sao chép bằng `reader.transferTo(writer)`, vẫn dùng `try-with-resources` cho hai tài nguyên.

## Tóm tắt kiến thức cốt lõi

- `read(char[])` nhận tối đa kích thước mảng và trả về **số đơn vị ký tự thực sự đọc**, hoặc `-1` khi EOF. Không có cam kết mỗi lần đọc đầy mảng.
- `read(char[], off, len)`, `write(char[], off, len)` và `write(String, off, len)` dùng **offset và độ dài**; `append(CharSequence, start, end)` và `substring(begin, end)` dùng **chỉ số kết thúc loại trừ**.
- Tái sử dụng `char[]` với `write(buffer, 0, count)` là cách căn bản để xử lý văn bản dài theo từng phần mà không vô tình thêm ký tự cũ.
- `String` thích hợp cho dữ liệu đã tích lũy; `StringBuilder` để nối khối trong RAM; khi có thể, truyền trực tiếp từ Reader sang Writer để tránh giữ toàn bộ nội dung.
- `char`/`count` đo **đơn vị UTF-16**; các ký tự Unicode có thể trải qua nhiều `char`, và ranh giới buffer không phải ranh giới văn bản.
- Charset, dấu xuống dòng, bộ đệm, `flush` và `close` quyết định tính đúng đắn khi dữ liệu đi giữa bộ nhớ và hệ thống tệp.

### Tài liệu API tham khảo

- [Reader — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Reader.html)
- [Writer — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Writer.html)
- [BufferedReader — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)
- [BufferedWriter — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html)
- [Files — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [StringBuilder — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-003">EOF của Reader và số ký tự thực tế đã đọc</a>

<details>
<summary>Xem chi tiết</summary>

# EOF của `Reader` và số ký tự thực tế đã đọc trong Java

Khi đọc văn bản bằng `java.io.Reader`, hai quy tắc quyết định tính đúng đắn:

1. **EOF (end of file/end of stream)** được báo bằng `-1` ở các phương thức `read(...)`, tức luồng ký tự đã kết thúc.
2. **Số ký tự đọc được có thể nhỏ hơn kích thước vùng đích.** Giá trị `int n` do `read(...)` trả về mới là số đơn vị `char` đã thực sự nhận, nên chỉ xử lý đúng `n` phần tử mới trong buffer.

**Đọc ngắn (short read) chưa phải EOF.** Ngay cả khi mảng đủ lớn, một lần đọc có thể nhận ít dữ liệu hơn yêu cầu, vì vậy cần lặp cho đến `-1` khi muốn đọc hết. Mặt khác, `char` của Java là một **đơn vị mã UTF-16**; đếm `char` không luôn giống đếm Unicode code point, ký tự người dùng nhìn thấy hay byte.

## 1. `Reader` là gì và mỗi phương thức trả về gì?

`Reader` là API đọc luồng **ký tự**; `InputStream` đọc luồng **byte**. Những triển khai phổ biến:

- `StringReader`: đọc dữ liệu đã nằm trong `String`.
- `InputStreamReader`: giải mã byte từ `InputStream` bằng một `Charset` như UTF-8.
- `BufferedReader`: thêm bộ đệm và hỗ trợ `readLine()`.
- `Files.newBufferedReader(path, StandardCharsets.UTF_8)`: mở tệp văn bản với charset chỉ định rõ.

| Phương thức | Giá trị thành công | Hết luồng | Ý nghĩa |
| --- | --- | --- | --- |
| `int read()` | Một đơn vị UTF-16 trong khoảng `0..65535` | `-1` | Không trả trực tiếp `char`, để dành giá trị âm biểu thị EOF. |
| `int read(char[] dst)` | Số đơn vị `char` mới ghi, tối đa `dst.length` | `-1` khi yêu cầu đọc dương và hết dữ liệu | Có thể nhỏ hơn kích thước mảng. |
| `int read(char[] dst, int off, int len)` | Số `char` ghi vào `dst` từ chỉ số `off` | `-1` khi yêu cầu dương và EOF | `off` là vị trí trong mảng, **không phải offset trong file**. |
| `int read(CharBuffer target)` | Số `char` mới thêm vào `target` | `-1` khi yêu cầu dương và EOF | Giới hạn bởi `target.remaining()`. |
| `String readLine()` của `BufferedReader` | Nội dung dòng, có thể là `""` | `null` | Dấu kết thúc dòng bị bỏ khỏi kết quả. |

`Reader.read(...)` và `BufferedReader.readLine()` **không dùng chung tín hiệu EOF**: nhóm đầu trả `int` với dấu `-1`; `readLine()` trả `null`.

## 2. EOF của `Reader.read()` phải kiểm tra khi kết quả còn là `int`

`Reader.read()` trả `-1` khi đã hết dữ liệu, còn các giá trị không âm từ `0` đến `65535` đều có thể biểu diễn một đơn vị `char`. Ép về `char` quá sớm sẽ làm mất khả năng phân biệt EOF.

Sai (đoạn minh họa, giả định `reader` đã tồn tại):

```java
char ch = (char) reader.read();
if (ch == -1) {
    // Không phát hiện EOF được vì đã ép kiểu.
}
```

Khi ép `-1` sang `char`, kết quả trở thành `0xFFFF`. Đúng là giữ `int` cho tới khi đã kiểm tra:

```java
int value;
while ((value = reader.read()) != -1) {
    char ch = (char) value;
    // Lúc này ch chắc chắn là dữ liệu đã đọc.
}
```

Chương trình Java 17+ độc lập sau cho thấy giá trị `0xFFFF` khác EOF:

```java
// ReaderEofDemo.java
import java.io.Reader;
import java.io.StringReader;

public class ReaderEofDemo {
    public static void main(String[] args) throws Exception {
        try (Reader reader =
                     new StringReader("A" + (char) 0xFFFF)) {
            System.out.println(reader.read());
            System.out.println(reader.read());
            System.out.println(reader.read());
        }
    }
}
```

```bash
javac ReaderEofDemo.java
java ReaderEofDemo
```

Đầu ra:

```text
65
65535
-1
```

`65` biểu diễn `A`; `65535` là giá trị `char` `0xFFFF`; `-1` mới là EOF. Giá trị `0` cũng có thể là nội dung ký tự NUL, **không có nghĩa là EOF** khi gọi `read()` không tham số.

### EOF khác “chưa nhận được dữ liệu”

Một `Reader` có thể đọc từ file, bộ nhớ, socket hoặc nguồn tương tác. Với nguồn như socket, lời gọi `read` ở chế độ blocking có thể **đợi** dữ liệu đến hoặc đầu truyền kết thúc. Chưa có byte đến ngay lúc đó không có nghĩa là EOF. Nếu reader có bộ đệm, dữ liệu còn đệm vẫn được trả trước khi EOF xuất hiện.

`Reader.ready()` **không phải phép kiểm tra EOF**: `false` không đủ để suy ra hết dữ liệu, còn việc `true` nói về điều kiện sẵn sàng đọc theo hợp đồng của implementation. Đóng reader bằng `close()` cũng khác EOF; đọc sau khi đóng thường ném `IOException`, không phải cách chuẩn để nhận `-1`.

## 3. `read(char[])`: `n` là số ký tự vừa được ghi, không phải dung lượng buffer

Khi gọi `int n = reader.read(buffer)`:

- `n > 0`: có `n` đơn vị UTF-16 mới được chép vào `buffer[0]` đến `buffer[n - 1]`.
- `n == -1`: đã tới EOF nếu lời gọi yêu cầu đọc dương.
- `n == 0`: có thể xảy ra với yêu cầu đọc có độ dài 0 (xem phần tiếp theo); không được dùng nó thay cho EOF.

Ví dụ một mảng dài 4 đọc chuỗi dài 5:

```java
// ReaderChunkDemo.java
import java.io.Reader;
import java.io.StringReader;

public class ReaderChunkDemo {
    public static void main(String[] args) throws Exception {
        try (Reader reader = new StringReader("ABCDE")) {
            char[] buffer = new char[4];
            int n;

            while ((n = reader.read(buffer)) != -1) {
                System.out.println("n=" + n
                        + ", chunk=" + new String(buffer, 0, n));
            }
            System.out.println("next=" + reader.read(buffer));
        }
    }
}
```

```bash
javac ReaderChunkDemo.java
java ReaderChunkDemo
```

Đầu ra:

```text
n=4, chunk=ABCD
n=1, chunk=E
next=-1
```

Lần thứ hai chỉ ký tự `E` là mới. Phần còn lại trong mảng vẫn có thể chứa `BCD` của lần đọc trước. Vì vậy `new String(buffer)` hoặc `System.out.print(buffer)` sẽ vô tình in **dữ liệu cũ**. Dùng `new String(buffer, 0, n)` hoặc `writer.write(buffer, 0, n)` để chỉ xử lý đúng phần mới nhận.

**Không kết luận EOF từ short read:** một lần đọc 2/4096 ký tự chỉ cho biết có hai ký tự được trả ở lần đó. Lần sau vẫn có thể nhận thêm. Khi đọc hết luồng, điều kiện kết thúc là `-1`, không phải `n < buffer.length`.

Ví dụ sao chép ký tự theo khối, giả định `reader` và `writer` đều đã mở:

```java
char[] buffer = new char[8192];
int n;
long total = 0;

while ((n = reader.read(buffer, 0, buffer.length)) != -1) {
    writer.write(buffer, 0, n);
    total += n;
}
```

Đây là **đoạn minh họa**, không phải một class độc lập. `writer.write` nhận số lượng `n` vừa đọc, còn `total` cộng số đơn vị `char` thực tế. Nếu cộng `buffer.length`, tổng sẽ sai ở các lần đọc ngắn.

### `off`, `len` và trường hợp độ dài bằng 0

`read(dst, off, len)` yêu cầu tối đa `len` `char` được ghi vào mảng `dst`, bắt đầu từ chỉ số `off`. Với `off=3`, `len=4`, `n=2`, chỉ phần tử `dst[3]`, `dst[4]` vừa được cập nhật. Không được hiểu `off` là vị trí trong file.

Một yêu cầu với `len == 0` hợp lệ (nếu `off` hợp lệ) sẽ trả `0` mà không tiêu thụ ký tự. Có thể nhận `0` **ngay cả khi reader đang ở EOF**. Ví dụ xác định của `StringReader`:

```java
// ReaderZeroLengthDemo.java
import java.io.Reader;
import java.io.StringReader;

public class ReaderZeroLengthDemo {
    public static void main(String[] args) throws Exception {
        try (Reader reader = new StringReader("K")) {
            char[] buffer = new char[3];
            System.out.println("empty=" + reader.read(buffer, 0, 0));
            System.out.println("first=" + reader.read(buffer, 0, 3));
            System.out.println("eof=" + reader.read(buffer, 0, 3));
            System.out.println("emptyAfterEof=" + reader.read(buffer, 0, 0));
        }
    }
}
```

```bash
javac ReaderZeroLengthDemo.java
java ReaderZeroLengthDemo
```

Đầu ra:

```text
empty=0
first=1
eof=-1
emptyAfterEof=0
```

Tương tự, `read(CharBuffer)` với buffer không còn `remaining()` không yêu cầu thêm ký tự. Nếu `off`, `len` âm hoặc khoảng ghi vượt ra ngoài mảng, tham số không hợp lệ và có thể ném `IndexOutOfBoundsException`.

## 4. Đọc đúng N ký tự phải lặp đến khi đủ hoặc gặp EOF sớm

Một lần `read(dst, off, len)` **không bảo đảm** lấy đủ `len` ký tự; điều này đặc biệt quan trọng khi giao thức hoặc cấu trúc file quy định số ký tự cần nhận. Muốn lấy đúng N đơn vị UTF-16, hãy cộng dồn `n`, và báo lỗi nếu `-1` xuất hiện trước khi đủ.

Ví dụ Java 17+ độc lập:

```java
// ReaderExactDemo.java
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class ReaderExactDemo {
    static String readExactly(Reader reader, int expected)
            throws IOException {
        if (expected < 0) {
            throw new IllegalArgumentException("expected must be >= 0");
        }

        char[] result = new char[expected];
        int filled = 0;

        while (filled < expected) {
            int n = reader.read(result, filled, expected - filled);

            if (n == -1) {
                throw new EOFException(
                        "expected=" + expected + ", actual=" + filled);
            }
            if (n == 0) {
                throw new IOException("Reader made no progress");
            }
            filled += n;
        }

        return new String(result);
    }

    public static void main(String[] args) throws Exception {
        try (Reader reader = new StringReader("ABC")) {
            System.out.println(readExactly(reader, 2));
            try {
                System.out.println(readExactly(reader, 2));
            } catch (EOFException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }
}
```

```bash
javac ReaderExactDemo.java
java ReaderExactDemo
```

Đầu ra:

```text
AB
expected=2, actual=1
```

Lượt đầu lấy đủ `AB`. Lượt sau lấy được `C` rồi EOF, nên lỗi ghi rõ mới có 1/2 ký tự. Nhánh `n == 0` tránh busy loop nếu một `Reader` tùy biến trả 0 khi yêu cầu đọc dương mà không tiến triển; với các triển khai tuân hợp đồng thông thường, đọc với `len > 0` sẽ trả số dương hoặc `-1`. Trường hợp `expected == 0` trả `""` mà không đọc; không được coi đây là dấu hiệu EOF.

Nếu chỉ muốn xử lý **toàn bộ luồng**, không cần `readExactly`: dùng vòng lặp đọc đến `-1` và xử lý mỗi khối mới nhận.

## 5. Số `char` từ `Reader` khác số byte và Unicode code point

Mỗi Java `char` là **một code unit UTF-16** 16-bit. Unicode code point bên ngoài BMP (ví dụ emoji) dùng **hai** đơn vị mã (surrogate pair). Phân biệt:

- **Byte:** phụ thuộc cách mã hóa ký tự thành byte, ví dụ UTF-8.
- **`char` / code unit UTF-16:** đơn vị mà `Reader.read` trả và `String.length()` đếm.
- **Unicode code point:** có thể cần một hoặc hai code unit UTF-16.
- **Grapheme cluster:** ký tự mà người dùng cảm nhận; có thể gồm nhiều code point, chẳng hạn chữ có dấu kết hợp.

Chuỗi `"A😀B"` là một ví dụ: ba code point, bốn `char`, sáu byte UTF-8. Ví dụ độc lập dùng `InputStreamReader` để giải mã UTF-8 và cộng tổng từ giá trị `n` thực tế:

```java
// ReaderCharCountDemo.java
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ReaderCharCountDemo {
    public static void main(String[] args) throws Exception {
        String original = "A😀B";
        byte[] utf8 = original.getBytes(StandardCharsets.UTF_8);
        StringBuilder decoded = new StringBuilder();
        int actualChars = 0;

        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(utf8),
                StandardCharsets.UTF_8)) {
            char[] buffer = new char[2];
            int n;

            while ((n = reader.read(buffer)) != -1) {
                decoded.append(buffer, 0, n);
                actualChars += n;
            }
        }

        System.out.println("utf8Bytes=" + utf8.length);
        System.out.println("readChars=" + actualChars);
        System.out.println("codePoints="
                + decoded.codePointCount(0, decoded.length()));
        System.out.println("roundTrip=" + original.contentEquals(decoded));
    }
}
```

```bash
javac -encoding UTF-8 ReaderCharCountDemo.java
java ReaderCharCountDemo
```

Đầu ra:

```text
utf8Bytes=6
readChars=4
codePoints=3
roundTrip=true
```

Đoạn mã không giả định cách chia chunk cố định. Khi xử lý văn bản theo chunk, cần chú ý **surrogate pair có thể nằm ở biên giữa hai chunk**. Nếu phải xử lý Unicode code point hoặc encode lại từng chunk riêng lẻ, cần giữ trạng thái để không tách sai cặp. Ví dụ trên ghép hoàn chỉnh vào `StringBuilder` trước khi đếm code point.

`InputStreamReader` làm việc qua **bộ giải mã byte → char**. Một byte UTF-8 không nhất thiết tạo ra một `char`; reader có thể đọc trước nhiều byte vào bộ đệm. Vì vậy không suy số byte từ `read` của `Reader`, và không suy EOF của reader chỉ từ số byte đã lấy ở tầng dưới.

## 6. `BufferedReader.readLine()`: `null` mới là EOF, `""` vẫn là một dòng

`BufferedReader.readLine()` trả về nội dung của một dòng, bỏ delimiter `\n`, `\r` hoặc `\r\n`. Dòng rỗng được biểu diễn bằng `""` (chuỗi có độ dài 0). Chỉ khi không còn dòng nào để đọc, phương thức mới trả `null`.

```java
// ReaderLinesDemo.java
import java.io.BufferedReader;
import java.io.StringReader;

public class ReaderLinesDemo {
    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new StringReader("alpha\n\nomega"))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                System.out.println(++lineNumber + ": <" + line + ">");
            }
            System.out.println("eof=" + (reader.readLine() == null));
        }
    }
}
```

```bash
javac ReaderLinesDemo.java
java ReaderLinesDemo
```

Đầu ra:

```text
1: <alpha>
2: <>
3: <omega>
eof=true
```

Dòng thứ hai là một **dòng rỗng hợp lệ**; không được bỏ qua vì tưởng EOF. Trường hợp `"alpha\n"` trả `"alpha"` rồi `null`: dấu xuống dòng kết thúc dòng đang có, không tự tạo thêm một dòng rỗng thứ hai. Trường hợp `"\n"` trả `""` rồi `null`.

Nếu cần giữ nguyên dấu xuống dòng trong dữ liệu, `readLine()` không phù hợp vì nó loại bỏ các delimiter; hãy đọc từng khối `char` hoặc lưu dấu ngắt theo định dạng riêng.

## 7. Ví dụ với tệp UTF-8 và tổng số ký tự thực tế

Chương trình độc lập dưới đây tạo hai tệp tạm, sao chép văn bản qua `Reader`/`Writer`, đếm đúng `n` của từng lần đọc, đóng tài nguyên rồi so sánh dữ liệu:

```java
// ReaderFileDemo.java
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReaderFileDemo {
    public static void main(String[] args) throws Exception {
        Path source = Files.createTempFile("reader-src-", ".txt");
        Path target = Files.createTempFile("reader-dst-", ".txt");

        try {
            Files.writeString(source, "Xin chào\nJava IO\n",
                    StandardCharsets.UTF_8);

            long copiedChars = 0;
            try (Reader reader = Files.newBufferedReader(
                         source, StandardCharsets.UTF_8);
                 Writer writer = Files.newBufferedWriter(
                         target, StandardCharsets.UTF_8)) {
                char[] buffer = new char[5];
                int n;

                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                    copiedChars += n;
                }
            }

            System.out.println("copiedChars=" + copiedChars);
            System.out.println("sameText=" + Files.readString(
                    source, StandardCharsets.UTF_8).equals(
                    Files.readString(target, StandardCharsets.UTF_8)));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }
}
```

```bash
javac -encoding UTF-8 ReaderFileDemo.java
java ReaderFileDemo
```

Đầu ra:

```text
copiedChars=17
sameText=true
```

`"Xin chào\n"` gồm 9 code unit UTF-16, `"Java IO\n"` gồm 8; tổng là **17 `char`**. Mảng đệm chỉ có 5 vị trí, nhưng kết quả cuối cùng không phụ thuộc cách `Reader` chia các lượt đọc. `try-with-resources` bảo đảm writer được đóng trước khi đọc lại tệp đích, nên dữ liệu đã được ghi xong.

Đối với tệp lớn, đọc từng khối như thế này giúp tránh nạp cả file thành một `String` trong RAM. Từ Java 10 còn có `Reader.transferTo(Writer)`, phương thức sao chép đến EOF và trả về tổng số `char` đã chuyển, nhưng không tự đóng reader/writer.

## 8. Các nhầm lẫn và lỗi phổ biến

| Lỗi | Vì sao sai | Cách khắc phục |
| --- | --- | --- |
| Ép `reader.read()` sang `char` rồi mới so với `-1` | Sentinel EOF bị mất khi ép kiểu | Kiểm tra kết quả `int` trước. |
| Gọi `read(buffer)` rồi xử lý toàn bộ `buffer` | Phần chưa ghi có thể còn dữ liệu cũ | Chỉ xử lý `buffer[0..n)` khi `n > 0`. |
| Coi `n < buffer.length` là EOF | Short read chưa chắc là hết nguồn | Lặp đến `-1`. |
| Cộng `buffer.length` vào số ký tự đã đọc | Đếm cả vùng không có dữ liệu mới | Cộng chính xác `n`. |
| Chỉ gọi `read` một lần để lấy N ký tự | Có thể lấy thiếu | Lặp cộng dồn; xử lý EOF sớm. |
| Dùng `len == 0` để dò EOF | Trả `0` dù nguồn có thể đang ở EOF | Đọc với yêu cầu dương để nhận `-1`. |
| Coi `readLine().isEmpty()` là kết thúc | Dòng rỗng khác `null`, EOF gây NPE | Kiểm tra `line != null`. |
| Dùng `ready()` để dò EOF | Sự sẵn sàng không tương đương hết nguồn | Đọc và xét giá trị trả về. |
| Coi số `char` là số byte hoặc code point | Unicode/charset có thể dùng nhiều đơn vị | Xác định rõ loại đơn vị cần đếm. |
| Không đóng `Reader` | Rò rỉ file descriptor/nguồn lực | Dùng `try-with-resources`. |

### Tự kiểm tra

1. `char[] b = new char[100]`; `reader.read(b)` trả `7`. **Chỉ bảy ký tự đầu của mảng là dữ liệu mới**, không phải cả 100.
2. Lần đọc vào mảng dài 100 trả `7`. Lần sau có chắc `-1`? **Không.** Đọc ngắn chưa chứng minh EOF.
3. `readLine()` trả `""`. Đã tới EOF? **Không thể suy ra như vậy:** đây là dòng rỗng; EOF được biểu diễn bằng `null`.
4. `read(array, 5, 2)` trả `1`. Ký tự mới nằm ở **`array[5]`**, không phải vị trí 5 trong tệp.
5. `read(array, 0, 0)` có thể trả `0` ngay ở EOF? **Có**, vì không yêu cầu lấy thêm ký tự.
6. `"A😀B"` gồm bao nhiêu byte UTF-8, `char` và code point? **6 byte, 4 `char`, 3 code point**.

**Quy tắc cốt lõi:** Với `Reader.read(...)`, **`-1` là EOF; giá trị không âm là số đơn vị `char` thực tế đã đọc** (khi yêu cầu chiều dài 0 thì trả 0). Với `BufferedReader.readLine()`, **`null` là EOF; chuỗi rỗng `""` vẫn là một dòng hợp lệ**. Luôn sử dụng giá trị trả về của chính lần đọc đang thực hiện để quyết định phần dữ liệu nào được xử lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-004">InputStreamReader nối luồng byte với ký tự</a>

<details>
<summary>Xem chi tiết</summary>

# InputStreamReader: nối luồng byte với ký tự trong Java

`InputStreamReader` là cầu nối từ **`InputStream` (byte)** sang **`Reader` (ký tự)**. Nó dùng bộ giải mã của một `Charset` để diễn giải các byte theo một bảng mã xác định, rồi trả về các đơn vị `char` của Java (UTF-16). Nhờ đó, chương trình có thể đọc văn bản từ file, socket hoặc nguồn byte khác mà không tự giải mã từng byte.

Đây là chủ đề thuộc **luồng ký tự**: cần hiểu luồng byte ở tầng dưới, ý nghĩa của encoding và cách đọc đúng văn bản bằng API `Reader`.

## 1. Vì sao cần chuyển từ byte sang ký tự?

Máy tính truyền/lưu trữ file dưới dạng byte; chữ viết là ký tự Unicode. **Một byte không nhất thiết tương ứng với một ký tự**:

- Trong UTF-8, ký tự ASCII thường chiếm 1 byte, ký tự như `é` chiếm 2 byte, ký tự `☕` chiếm 3 byte, nhiều emoji chiếm 4 byte.
- Trong Java, một `char` là **một đơn vị mã UTF-16** (16 bit), không bảo đảm biểu diễn trọn một Unicode code point. Ví dụ emoji `🙂` cần **hai** `char` (surrogate pair).
- Cùng một chuỗi byte có thể thành văn bản khác nhau nếu giải mã bằng charset khác; byte không hợp lệ theo charset đã chọn còn có thể làm mất dữ liệu hoặc gây lỗi.

`InputStream` chỉ cung cấp byte, chẳng hạn `read()` trả về một byte dưới dạng số `0..255` hoặc `-1` khi EOF. Nếu muốn đọc văn bản, ta cần **giải mã nhiều byte thành ký tự khi cần**. Đó là trách nhiệm của `InputStreamReader`.

```text
File / mạng / vùng nhớ
        |
        | dãy byte (ví dụ UTF-8: 43 61 66 C3 A9)
        v
     InputStream
        |
        | giải mã theo Charset
        v
  InputStreamReader
        |
        | Java char (UTF-16): C  a  f  é
        v
       Reader  ---> xử lý văn bản
```

Chuỗi byte ở sơ đồ là chữ `Café` trong UTF-8. Cặp byte `C3 A9` cùng mã hóa chữ `é`; nếu tự ép từng byte thành `char`, chương trình không thể giải mã đúng.

## 2. Cách tạo `InputStreamReader`

Định dạng cơ bản:

```java
InputStream input = /* nguồn byte đã mở */;
Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa**, yêu cầu biến `input` và các import thích hợp. Hãy chọn charset theo **định dạng thực tế của dữ liệu**, ví dụ:

```java
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// Ví dụ khi đã có InputStream input:
InputStreamReader reader =
        new InputStreamReader(input, StandardCharsets.UTF_8);
```

Thư viện `java.nio.charset.StandardCharsets` cung cấp những charset thông dụng như `UTF_8`, `UTF_16`, `ISO_8859_1`, `US_ASCII`; không cần viết chuỗi tên charset tùy ý cho các trường hợp này.

Nên **truyền charset tường minh**. Hàm tạo `new InputStreamReader(input)` dùng *default charset* của môi trường; Java 18+ mặc định là UTF-8 theo JEP 400 trong hầu hết trường hợp thông thường, nhưng mã học tập hoặc hệ thống chạy trên nhiều phiên bản/môi trường vẫn không nên ngầm giả định encoding. Charset đúng phải do chuẩn định dạng, protocol hoặc cấu hình nguồn dữ liệu quy định.

Nếu nguồn là UTF-8 mà bạn dùng ISO-8859-1, các byte `C3 A9` thường được diễn giải thành `Ã©` thay vì `é` (*mojibake*). **Đổi charset chỉ hợp lý khi bạn biết charset gốc**, không phải một cách sửa mọi lỗi hiển thị.

## 3. `InputStreamReader` giải mã như thế nào?

`InputStreamReader` kế thừa `Reader`; các thao tác đọc trả về ký tự đã giải mã:

| API | Giá trị trả về | Ý nghĩa |
| --- | --- | --- |
| `reader.read()` | `int` từ `0` đến `65535`, hoặc `-1` | Một đơn vị `char` (UTF-16) hoặc EOF |
| `reader.read(char[])` | `int` là số `char` đã đọc, hoặc `-1` | Số đơn vị `char` hợp lệ trong mảng |
| `reader.read(char[], off, len)` | `int` là số `char` đã đọc, hoặc `-1` | Ghi ký tự bắt đầu từ vị trí `off` |
| `reader.close()` | không có giá trị | Đóng reader và đóng cả luồng nguồn bên dưới |

Với `read(char[])`, số `n` trả về là **số phần tử `char` hợp lệ**, không phải số byte đã tiêu thụ; một `char[]` có thể còn các phần tử cũ từ lần đọc trước. Do đó phải xử lý đúng `n` ký tự:

```java
char[] buffer = new char[1024];
int n;

while ((n = reader.read(buffer)) != -1) {
    text.append(buffer, 0, n);  // text là một StringBuilder đã khai báo
}
```

Sai lầm thường gặp là `text.append(buffer)`, vì như vậy mỗi vòng sẽ thêm **toàn bộ buffer**, kể cả phần dư chưa được thay đổi. Vòng lặp ví dụ giả định buffer có chiều dài dương và `reader` tuân theo hợp đồng `Reader`; không suy luận EOF chỉ vì một lần đọc ngắn.

Một lần `reader.read(buffer)` không bắt buộc lấp đầy mảng. Bên dưới, bộ giải mã có thể đọc trước nhiều byte, giữ lại byte của một chuỗi đa byte chưa đầy đủ và ghép với byte của lần đọc tiếp theo. **Ranh giới của các lần đọc byte không phải ranh giới ký tự.**

Chẳng hạn `é` là `C3 A9` trong UTF-8. Nếu nguồn trả `C3` ở một lần đọc và `A9` ở lần đọc sau, bộ giải mã cần giữ trạng thái để tạo được ký tự `é`. Tự gọi `new String(bytes, UTF_8)` trên từng mảnh byte độc lập có nguy cơ giải mã sai khi một chuỗi UTF-8 bị cắt đôi.

## 4. Chương trình Java 17+ chạy độc lập

Ví dụ này ép nguồn `InputStream` chỉ cung cấp tối đa **1 byte mỗi lần đọc vào mảng**. Trong khi đó, `InputStreamReader` vẫn phải tái tạo đúng các ký tự có encoding UTF-8 dài hơn một byte. Việc dùng `char[2]` cũng minh họa cách chỉ thêm đúng số `char` mà `read` trả về.

Lưu nội dung dưới đây thành file **`InputStreamReaderDemo.java`**:

```java
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class InputStreamReaderDemo {

    public static void main(String[] args) throws IOException {
        String expected = "Café ☕";
        byte[] utf8 = expected.getBytes(StandardCharsets.UTF_8);
        StringBuilder actual = new StringBuilder();

        try (InputStream input = new OneByteAtATimeStream(
                    new ByteArrayInputStream(utf8));
             Reader reader = new InputStreamReader(
                    input, StandardCharsets.UTF_8)) {

            char[] chars = new char[2];
            int charsRead;

            while ((charsRead = reader.read(chars)) != -1) {
                actual.append(chars, 0, charsRead);
            }
        }

        System.out.println("UTF-8 bytes: " + utf8.length);
        System.out.println("Java char units: " + actual.length());
        System.out.println("Text: " + actual);
        System.out.println("Matches: " + expected.contentEquals(actual));
    }

    private static final class OneByteAtATimeStream
            extends FilterInputStream {

        private OneByteAtATimeStream(InputStream source) {
            super(source);
        }

        @Override
        public int read(byte[] b, int off, int len)
                throws IOException {
            return super.read(b, off, Math.min(len, 1));
        }
    }
}
```

Trong terminal có JDK 17+:

```bash
javac -encoding UTF-8 InputStreamReaderDemo.java
java InputStreamReaderDemo
```

**Kết quả chuẩn (stdout):**

```text
UTF-8 bytes: 9
Java char units: 6
Text: Café ☕
Matches: true
```

Giải thích:

1. Chuỗi `Café ☕` có **9 byte UTF-8**: ba chữ ASCII `C`, `a`, `f` = 3 byte; `é` = 2 byte; dấu cách = 1 byte; `☕` = 3 byte.
2. Chuỗi giải mã gồm **6 đơn vị `char` UTF-16**: `C`, `a`, `f`, `é`, dấu cách và `☕`. Trường hợp này không chứa ký tự ngoài Basic Multilingual Plane, nên mỗi ký tự vừa nêu chiếm một `char`.
3. `OneByteAtATimeStream` mô phỏng đầu vào có các lần đọc byte ngắn, buộc bộ giải mã nối nhiều lần đọc để tạo `é` và `☕`.
4. `actual.append(chars, 0, charsRead)` chỉ lấy phần mới có dữ liệu. `contentEquals` xác nhận chuỗi đọc giống chuỗi ban đầu.

Lớp `OneByteAtATimeStream` chỉ phục vụ bài thử: các `InputStream` thực tế không bảo đảm số byte mỗi lần đọc như vậy. Phần quan trọng để dùng trong ứng dụng là `InputStreamReader(input, StandardCharsets.UTF_8)` và vòng lặp `Reader.read(char[])`.

## 5. Đọc văn bản từ tệp

Với đường dẫn `Path` và encoding đã biết, có thể mở luồng byte và bọc bằng `InputStreamReader`:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public static String readUtf8File(Path path) throws IOException {
    StringBuilder text = new StringBuilder();

    try (InputStream input = Files.newInputStream(path);
         Reader reader = new InputStreamReader(
                 input, StandardCharsets.UTF_8)) {

        char[] chars = new char[4096];
        int n;

        while ((n = reader.read(chars)) != -1) {
            text.append(chars, 0, n);
        }
    }

    return text.toString();
}
```

Đây là **phương thức minh họa** cần đặt bên trong một class; đưa `import` lên đầu file. `try-with-resources` đảm bảo đóng reader và stream kể cả khi phát sinh `IOException`. Việc đóng reader vốn đã đóng stream bên dưới; khai báo cả hai tài nguyên như ví dụ giúp thể hiện rõ chủ sở hữu, nhưng cũng có thể chỉ quản lý trực tiếp reader:

```java
try (Reader reader = new InputStreamReader(
        Files.newInputStream(path), StandardCharsets.UTF_8)) {
    // Đọc ký tự bằng reader
}
```

Nếu chỉ cần đọc văn bản từ file, `Files.newBufferedReader(path, StandardCharsets.UTF_8)` thường ngắn gọn hơn. Nó trả về `BufferedReader`, phù hợp khi muốn dùng `readLine()`. Dù dùng API nào, vẫn phải xác định đúng charset của dữ liệu.

**Lưu ý kích thước:** Gom toàn bộ tệp vào một `StringBuilder` sẽ dùng bộ nhớ tỷ lệ với kích thước văn bản; với file lớn, nên xử lý từng khối hoặc từng dòng thay vì giữ toàn bộ nội dung trong RAM.

## 6. Khi cần `BufferedReader`

`InputStreamReader` chuyển byte thành ký tự; `BufferedReader` thêm lớp đệm ký tự và các tiện ích như `readLine()`:

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

static void printLines(InputStream input) throws IOException {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String line;

        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }
}
```

Đây là phương thức minh họa cần đặt trong một class, với `input` là stream mà phương thức **được phép đóng**. `readLine()` trả `null` khi EOF và bỏ ký tự kết thúc dòng (CR, LF hoặc CRLF) khỏi chuỗi kết quả. Nếu phải bảo toàn nguyên văn dấu xuống dòng, dùng vòng lặp `read(char[])` hoặc logic xử lý ký tự phù hợp.

Luồng lớp bọc:

```text
InputStream (byte)
   -> InputStreamReader (decode bytes thành char)
      -> BufferedReader (đệm ký tự, readLine)
         -> ứng dụng
```

Không bọc `InputStreamReader` khi dữ liệu thực chất là ảnh, ZIP hay định dạng nhị phân tùy ý; hãy tiếp tục thao tác bằng `InputStream`. Dùng `Reader` chỉ khi byte thực sự chứa văn bản được mã hóa bằng một charset xác định.

## 7. Charset, byte lỗi và Unicode

### Charset sai gây văn bản sai

`InputStreamReader` không tự động đoán đúng encoding. Cần xác định charset qua chuẩn tệp, header giao thức (`Content-Type`/`charset` khi đáng tin), tài liệu nguồn hoặc cấu hình thống nhất của hệ thống. Một file có BOM không tự động khiến mọi cách tạo `InputStreamReader` đều tự nhận diện charset và loại bỏ BOM; cách xử lý BOM phụ thuộc encoding/bộ giải mã và ứng dụng. Đừng dựa vào cơ chế suy đoán.

### Byte không hợp lệ và chính sách giải mã

Khi dùng constructor nhận `Charset`, Java thường cấu hình decoder để **thay chuỗi byte lỗi/không ánh xạ được** bằng ký tự thay thế (hay thấy là `�` với UTF-8). Do đó nhận được một chuỗi `String` không chứng minh rằng dữ liệu nguồn hoàn toàn hợp lệ.

Nếu cần **từ chối dữ liệu UTF-8 lỗi** thay vì âm thầm thay thế, truyền `CharsetDecoder` với chế độ `REPORT`:

```java
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

// Giả sử đã có InputStream input:
InputStreamReader reader = new InputStreamReader(
        input,
        StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
);
```

Đoạn mã minh họa này chưa bao gồm thao tác đọc và đóng tài nguyên. Khi đọc gặp byte không hợp lệ, decoder báo lỗi qua `IOException` cụ thể, chẳng hạn `MalformedInputException`. Chính sách `REPLACE` có thể phù hợp khi ưu tiên tiếp tục hiển thị, còn `REPORT` phù hợp khi tính toàn vẹn dữ liệu quan trọng.

### Một `char` không nhất thiết bằng một ký tự người dùng thấy

`InputStreamReader` trả về **các đơn vị UTF-16**. Ví dụ chuỗi `"A🙂"` có 5 byte UTF-8, nhưng `String.length()` bằng 3 vì `A` chiếm một `char` và `🙂` chiếm một cặp surrogate (hai `char`). Số Unicode code point là 2, còn số *grapheme cluster* hiển thị trong trường hợp này cũng là 2. Những khái niệm này khác nhau với các dấu kết hợp hoặc emoji ghép phức tạp.

`Reader.read(char[])` có thể kết thúc một lượt đọc ở giữa cặp surrogate. Việc ghép tất cả các đoạn `char` theo đúng thứ tự vẫn giữ nguyên chuỗi; tuy nhiên, đừng coi **mỗi đoạn `char[]`** hoặc **mỗi `char`** là một ký tự Unicode hoàn chỉnh. Nếu cần xử lý từng code point, hãy dùng các API Unicode của `String`/`Character` hoặc thuật toán giữ trạng thái qua ranh giới buffer.

## 8. Vòng đời, buffering và ranh giới dữ liệu

- **Đóng luồng đúng chủ sở hữu:** `InputStreamReader.close()` đóng `InputStream` được bọc. Nếu đang đọc từ `System.in` và muốn tiếp tục sử dụng console, cân nhắc không đóng wrapper tại thời điểm đó.
- **Decoder có thể đọc trước:** một lần `Reader.read()` có thể khiến tầng dưới lấy nhiều byte để duy trì bộ đệm và giải mã. Sau khi bọc stream bằng reader, không tùy tiện xen kẽ `input.read()` trực tiếp với `reader.read()` trên cùng stream; việc đọc trước sẽ làm khó dự đoán vị trí dữ liệu.
- **Lỗi I/O:** `read` và `close` có thể ném `IOException`. Dùng `try-with-resources` và để tầng gọi quyết định thông báo/khôi phục nếu có lỗi.
- **Không cố dự đoán số byte từ số char:** byte UTF-8, đơn vị `char` UTF-16 và Unicode code point là ba thước đo khác nhau.
- **Luồng theo chiều ngược:** `OutputStreamWriter` chuyển các `char` từ `Writer` thành byte theo `Charset` để ghi ra `OutputStream`. Dùng cùng charset ở hai đầu khi giao thức yêu cầu, ví dụ UTF-8.

## 9. Các lỗi phổ biến

| Lỗi | Vì sao sai | Cách sửa |
| --- | --- | --- |
| `(char) input.read()` cho mọi văn bản | Đọc từng byte, làm hỏng ký tự mã hóa bằng nhiều byte | Dùng `InputStreamReader` với charset đúng |
| `new InputStreamReader(input)` mà không quan tâm môi trường | Ngầm phụ thuộc default charset | Ghi rõ `StandardCharsets.UTF_8` hoặc charset thực tế |
| `new String(bytesChunk, UTF_8)` cho từng khối độc lập | Có thể cắt đôi mã UTF-8 giữa hai khối | Giải mã trạng thái liên tục bằng `InputStreamReader` |
| `builder.append(buffer)` sau `read(char[])` | Thêm cả ký tự cũ ở cuối mảng | `builder.append(buffer, 0, charsRead)` |
| Coi `charsRead` là số byte | Một ký tự có thể cần nhiều byte và ngược lại | Phân biệt số byte, số `char`, số code point |
| Dừng sau lần đọc thiếu `buffer.length` | Luồng còn dữ liệu ở các lần sau | Chỉ EOF khi `read` trả `-1` |
| Bọc dữ liệu ảnh/ZIP trong reader | Giải mã làm thay đổi byte gốc | Dùng `InputStream`/`OutputStream` cho nhị phân |
| Đóng reader rồi lại dùng input bên dưới | `close()` đóng cả stream nguồn | Quy định rõ vòng đời và chủ sở hữu |
| Giả định đầu vào hỏng luôn ném lỗi | Decoder mặc định có thể thay thế chuỗi lỗi | Cấu hình `CharsetDecoder` với `REPORT` |

## 10. Tự kiểm tra

1. Byte UTF-8 của `é` là `C3 A9`. Nếu nguồn trả hai byte này ở hai lượt đọc khác nhau, nên tự đổi từng byte thành `char` không? **Không.** `InputStreamReader` giữ trạng thái giải mã để tạo ký tự đúng.
2. `reader.read(buffer)` trả `3` trong khi `buffer.length == 1024`. Phải thêm bao nhiêu đơn vị `char` vào kết quả? **Chỉ 3**, bằng `append(buffer, 0, 3)`.
3. `Reader.read()` trả `-1` có nghĩa là gì? **Hết luồng ký tự**, không có thêm ký tự để đọc.
4. Một chuỗi Java có `String.length() == 3` có luôn là 3 Unicode code point không? **Không**; ký tự ngoài BMP có thể cần hai đơn vị `char`.
5. Đọc file UTF-8 theo dòng nên kết hợp những lớp nào? **`InputStream` → `InputStreamReader`(UTF-8) → `BufferedReader`**, hoặc dùng `Files.newBufferedReader`.
6. Gặp UTF-8 sai và yêu cầu báo lỗi ngay: cần gì? **`CharsetDecoder` cấu hình `CodingErrorAction.REPORT`**, không chỉ dựa vào constructor mặc định.

**Ghi nhớ:** `InputStreamReader` chịu trách nhiệm **giải mã liên tục byte → UTF-16 char theo charset đã chọn**; vòng lặp `Reader.read(char[])` xử lý đúng **số char vừa đọc**, còn `BufferedReader` bổ sung đệm và đọc theo dòng khi cần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-005">OutputStreamWriter nối ký tự với luồng byte</a>

<details>
<summary>Xem chi tiết</summary>

# OutputStreamWriter: cầu nối từ ký tự sang luồng byte trong Java

`OutputStreamWriter` trong `java.io` là **cầu nối giữa API ghi ký tự (`Writer`) và API ghi byte (`OutputStream`)**. Khi chương trình viết chữ bằng `write(...)`, đối tượng này sử dụng **charset** để mã hóa ký tự thành một chuỗi byte, rồi chuyển các byte đó xuống luồng đích. Đây là thành phần quan trọng khi ghi văn bản tiếng Việt, Unicode, dữ liệu dạng dòng hoặc giao thức văn bản qua tệp, bộ nhớ hay socket.

Mục tiêu: hiểu chính xác hướng chuyển đổi, vai trò của charset, cách ghép `OutputStreamWriter` với `OutputStream`/`BufferedWriter`, ý nghĩa của `flush()`/`close()`, và các lỗi khiến văn bản ghi ra không đúng.

## 1. Cầu nối hoạt động như thế nào?

Sơ đồ:

```text
String / char[] / ký tự Java
       |
       | Writer.write(...)
       v
+-------------------------+
| OutputStreamWriter      |
| CharsetEncoder          |  UTF-8, UTF-16, US-ASCII, ...
| ký tự UTF-16 -> byte    |
+-------------------------+
       |
       | các byte được mã hóa
       v
OutputStream (FileOutputStream / ByteArrayOutputStream / Socket OutputStream...)
       |
       v
Tệp, mảng byte, mạng hoặc một đích byte khác
```

Java lưu nội dung của `String` theo đơn vị **UTF-16 code unit** (`char` là đơn vị 16 bit); ký tự ngoài Basic Multilingual Plane, như emoji `😀`, dùng một **cặp surrogate** (hai `char`). `OutputStreamWriter` nhận dãy ký tự này, xử lý bằng bộ mã hóa đã chọn và phát sinh byte theo charset. Vì thế, **số `char` không nhất thiết bằng số ký tự Unicode, và cũng không bằng số byte**.

Ví dụ:

| Nội dung | Số đơn vị `char` UTF-16 | Số byte UTF-8 |
| --- | ---: | ---: |
| `A` | 1 | 1 |
| `é` | 1 | 2 |
| `中` | 1 | 3 |
| `😀` | 2 | 4 |
| Ký tự xuống dòng `\n` | 1 | 1 |

Nếu ghi `"Aé中😀\n"` theo UTF-8, chuỗi này có **6 đơn vị `char`, 5 Unicode code point và 11 byte UTF-8**. Không nên lấy `String.length()` để dự đoán số byte được lưu hoặc gửi qua mạng.

`OutputStreamWriter` chỉ **mã hóa ký tự thành byte**. Chiều ngược lại, đọc byte thành ký tự, là công việc của `InputStreamReader`.

## 2. Cấu tạo một pipeline ghi ký tự

Ví dụ cơ bản:

```java
OutputStream destination = new FileOutputStream("message.txt");
Writer writer = new OutputStreamWriter(destination, StandardCharsets.UTF_8);
writer.write("Xin chào, Java!");
writer.close();
```

Đây là **mảnh minh họa**; cần import `java.io.*`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Khi chạy, `FileOutputStream` nhận byte; `OutputStreamWriter` chịu trách nhiệm chuyển ký tự sang UTF-8. Sau `close()`, luồng ghi đã kết thúc.

Trong code thật, hãy dùng `try-with-resources` để đóng tài nguyên kể cả khi xảy ra ngoại lệ:

```java
try (Writer writer = new OutputStreamWriter(
        new FileOutputStream("message.txt"), StandardCharsets.UTF_8)) {
    writer.write("Xin chào, Java!");
}
```

`try-with-resources` tự gọi `close()` trên `writer`. Khi đóng, `OutputStreamWriter` hoàn tất luồng mã hóa, flush phần dữ liệu còn lại và **đóng `OutputStream` bên dưới**. Do đó không cần đóng riêng `FileOutputStream` sau khi đã giao quyền sở hữu cho writer. Nếu bạn nhận `OutputStream` do nơi khác quản lý, phải thống nhất ai có quyền đóng trước khi tạo writer.

## 3. Chọn charset tường minh

Các constructor đáng biết:

```java
new OutputStreamWriter(outputStream)
new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
new OutputStreamWriter(outputStream, "UTF-8")
new OutputStreamWriter(outputStream, charsetEncoder)
```

- `OutputStreamWriter(OutputStream)` dùng **default charset** của môi trường chạy. Không nên dựa vào nó cho định dạng file, giao tiếp giữa hệ thống hoặc kết quả phải ổn định trên máy khác. Java 17 có thể có default charset phụ thuộc nền tảng/cấu hình; các phiên bản và cấu hình khác cũng ảnh hưởng đến hành vi mặc định.
- `OutputStreamWriter(OutputStream, Charset)` chọn một charset cụ thể. Thường ưu tiên `StandardCharsets.UTF_8` để tránh lỗi đánh máy tên charset và bảo đảm thống nhất.
- `OutputStreamWriter(OutputStream, String)` chấp nhận tên charset; tên không được hỗ trợ có thể gây `UnsupportedEncodingException` khi khởi tạo.
- `OutputStreamWriter(OutputStream, CharsetEncoder)` cho phép kiểm soát chính sách xử lý ký tự không mã hóa được hoặc UTF-16 không hợp lệ.

Charset **không phải loại dữ liệu `String`**, và cũng không phải thuộc tính tự động đi kèm một `OutputStream` bất kỳ. Người ghi và người đọc phải thống nhất định dạng byte. Ghi với UTF-8 nhưng phía đọc giải mã theo charset khác có thể tạo ra chữ bị lỗi (*mojibake*) hoặc ký tự thay thế.

Đọc lại bằng đúng UTF-8:

```java
byte[] bytes = "Xin chào".getBytes(StandardCharsets.UTF_8);
String restored = new String(bytes, StandardCharsets.UTF_8);
```

Đoạn trên mô tả cặp mã hóa/giải mã khi có sẵn toàn bộ dữ liệu trong bộ nhớ; khi cần ghi **theo luồng**, `OutputStreamWriter` cung cấp thao tác `write(...)` mà không yêu cầu tạo toàn bộ mảng byte trước. Ngược lại, có thể dùng `InputStreamReader` khi dữ liệu được đọc dưới dạng luồng.

## 4. Các thao tác quan trọng trên OutputStreamWriter

| Thao tác | Ý nghĩa |
| --- | --- |
| `write(int c)` | Ghi một **đơn vị ký tự**; tham số `int` không có nghĩa là một Unicode code point bất kỳ |
| `write(char[] chars, int offset, int length)` | Ghi một đoạn mảng `char`, độ dài theo số `char` |
| `write(String text, int offset, int length)` | Ghi một đoạn `String` tính theo chỉ số UTF-16 |
| `write(String text)` | Phương thức tiện ích thừa hưởng từ `Writer` để ghi cả chuỗi |
| `append(...)` | Ghi thêm chuỗi/ký tự thông qua giao diện `Appendable` |
| `flush()` | Đẩy byte đã được mã hóa còn trong bộ đệm và yêu cầu luồng dưới flush |
| `close()` | Hoàn tất và đóng writer, đồng thời đóng luồng đích |
| `getEncoding()` | Trả tên mã hóa (có thể là tên lịch sử); có thể trả `null` sau khi đã đóng |

Các biến thể có `offset`, `length` kiểm tra phạm vi của mảng hoặc chuỗi và ném lỗi khi phạm vi sai. `Writer.write(int)` ghi một `char`, nên **không** truyền nguyên Unicode code point `0x1F600` rồi kỳ vọng nó tự được chuyển thành cặp surrogate. Muốn ghi emoji, dùng `write("😀")` hoặc:

```java
writer.write(Character.toChars(0x1F600));
```

Đoạn này là mảnh mã, với `writer` là một `Writer` đang mở và phương thức gọi xử lý `IOException`. `Character.toChars(...)` trả về cặp `char` cần thiết cho code point ngoài BMP.

Cũng tránh vô tình cắt đôi cặp surrogate khi chia `String` theo `offset`/`length`. Khi dữ liệu được ghép từ nhiều thao tác ghi, trình mã hóa có thể phải giữ trạng thái ký tự dở dang; đừng dựa vào việc một lần `write` luôn tương ứng một số byte đã sẵn sàng bên dưới.

## 5. Ví dụ Java 17+ hoàn chỉnh: từ Unicode sang byte UTF-8

Tạo tệp `OutputStreamWriterDemo.java`:

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class OutputStreamWriterDemo {
    public static void main(String[] args) throws IOException {
        String text = "Aé中😀\n";
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(destination, StandardCharsets.UTF_8)) {
            writer.write(text);
            writer.flush();
            System.out.println("Bytes after flush: " + destination.size());
        }

        byte[] result = destination.toByteArray();
        System.out.println("UTF-16 code units: " + text.length());
        System.out.println("Unicode code points: "
                + text.codePointCount(0, text.length()));
        System.out.println("UTF-8 bytes: " + result.length);
        System.out.println("Hex: "
                + HexFormat.ofDelimiter(" ").withUpperCase().formatHex(result));
        System.out.println("Round trip: "
                + text.equals(new String(result, StandardCharsets.UTF_8)));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac OutputStreamWriterDemo.java
java OutputStreamWriterDemo
```

Stdout chính xác:

```text
Bytes after flush: 11
UTF-16 code units: 6
Unicode code points: 5
UTF-8 bytes: 11
Hex: 41 C3 A9 E4 B8 AD F0 9F 98 80 0A
Round trip: true
```

Giải thích:

1. `ByteArrayOutputStream` là đích byte nằm trong bộ nhớ; nó giúp quan sát byte mà không phụ thuộc hệ thống tệp.
2. `writer.write(text)` cung cấp các đơn vị UTF-16 cho bộ mã hóa UTF-8. `writer.flush()` đẩy byte đã mã hóa xuống đích nên `destination.size()` cho kết quả **11** ngay tại thời điểm kiểm tra.
3. `HexFormat` (có trong Java 17) in từng byte ở hệ thập lục phân: `41` cho `A`, `C3 A9` cho `é`, `E4 B8 AD` cho `中`, `F0 9F 98 80` cho emoji, `0A` cho `\n`.
4. `new String(result, UTF_8)` giải mã đúng charset, phục hồi chính xác chuỗi gốc; biểu thức so sánh là `true`.
5. `close()` do `try-with-resources` gọi cũng đóng writer và đích byte. `ByteArrayOutputStream` cho phép gọi `toByteArray()` sau khi đã đóng, khác với nhiều đích I/O thực tế.

Ví dụ minh họa **encoding**, không phụ thuộc console có hiển thị đúng emoji hay không: kết quả được kiểm bằng hex và boolean, ổn định khi chạy trên JDK 17+.

## 6. OutputStreamWriter, BufferedWriter và OutputStream: phân chia trách nhiệm

Khi thực tế ghi nhiều đoạn nhỏ, thường dùng ba lớp:

```text
Ứng dụng gọi write("...")
        |
        v
BufferedWriter            đệm ký tự; hỗ trợ newLine()
        |
        v
OutputStreamWriter        mã hóa ký tự theo UTF-8 thành byte
        |
        v
FileOutputStream          ghi byte xuống tệp
```

`OutputStreamWriter` có bộ đệm **byte đã mã hóa**, nhưng không tạo một bộ đệm ký tự lớn để gộp các lần `write` nhỏ. Tài liệu Java khuyến nghị bọc nó trong `BufferedWriter` khi cần hiệu năng tốt hơn: việc gộp ký tự trước khi gọi bộ mã hóa có thể giảm số lần chuyển đổi.

Ví dụ ghi tệp văn bản:

```java
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WriteTextFile {
    public static void main(String[] args) throws Exception {
        Path path = Path.of("message.txt");

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        Files.newOutputStream(path),
                        StandardCharsets.UTF_8))) {
            writer.write("Dòng đầu");
            writer.newLine();
            writer.write("Dòng thứ hai");
        }
    }
}
```

Dùng:

```bash
javac WriteTextFile.java
java WriteTextFile
```

Kết quả là một tệp `message.txt` trong thư mục đang chạy, chứa hai dòng UTF-8. `Files.newOutputStream(path)` với tùy chọn mặc định tạo tệp nếu chưa có và **xóa nội dung cũ** nếu tệp đã tồn tại. `BufferedWriter.newLine()` ghi **dấu xuống dòng theo nền tảng** (ví dụ `\r\n` trên một số hệ thống, `\n` trên hệ thống khác). Nếu định dạng/protocol yêu cầu chính xác LF, dùng `writer.write("\n")` thay cho `newLine()`.

Khi đóng `BufferedWriter`, chuỗi đóng đi theo lớp bọc: flush ký tự, hoàn tất mã hóa, rồi đóng `OutputStream`. Đối với file cấu hình, JSON Lines, văn bản trao đổi qua HTTP hoặc log có yêu cầu charset rõ ràng, phải quy định charset và quy tắc xuống dòng ở nơi thiết kế định dạng.

### Ghi thêm vào cuối tệp thay vì ghi đè

Nếu thật sự cần append, mở đích byte với `StandardOpenOption.APPEND`:

```java
Path path = Path.of("message.txt");
try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(
                Files.newOutputStream(path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND),
                StandardCharsets.UTF_8))) {
    writer.write("Một dòng được nối tiếp\n");
}
```

Đây là **mảnh mã**, cần import `java.io.*`, `java.nio.file.*`, `java.nio.charset.StandardCharsets` và xử lý `IOException`. Khi append, không tự động thêm ký tự xuống dòng vào giữa nội dung cũ và mới; bạn phải kiểm soát ranh giới bản ghi. Nếu nhiều tiến trình/luồng cùng ghi một file, chỉ dùng chế độ append không đủ để đảm bảo mỗi bản ghi nhiều thao tác sẽ nguyên vẹn, không xen kẽ.

## 7. Flush, close và thời điểm byte xuất hiện

### `write(...)` không có nghĩa là “byte đã nằm bền vững trên đĩa”

`OutputStreamWriter` có thể tích lũy byte mã hóa trong bộ đệm nội bộ. Nếu còn `BufferedWriter` bên ngoài, ký tự cũng có thể nằm trong bộ đệm ký tự. Vì thế, việc `write(...)` trả về không đảm bảo byte đã được đẩy hết tới đích.

- `writer.flush()` yêu cầu đẩy các bộ đệm có liên quan xuống `OutputStream` và gọi flush trên luồng bên dưới. Nó giúp bên nhận thấy byte sớm hơn khi cấu trúc luồng/transport hỗ trợ.
- `writer.close()` hoàn tất chuỗi mã hóa, flush phần còn lại rồi đóng các lớp bên dưới. Dùng khi kết thúc tài nguyên.
- `flush()` **không** đồng nghĩa dữ liệu đã được đồng bộ bền vững xuống thiết bị lưu trữ (*fsync*) hoặc đã được hệ thống đầu xa xác nhận. Tính bền vững/ACK cần cơ chế riêng của file/network/protocol.
- `flush()` không đóng luồng. Có thể tiếp tục ghi sau `flush()`, nhưng sau `close()` thì không.

Nếu có `BufferedWriter` bọc ngoài, gọi `flush()` trên **writer ngoài cùng** để bảo đảm cả buffer ký tự và pipeline phía dưới được flush. Tránh chỉ gọi flush trực tiếp trên `OutputStream` nằm bên trong khi `BufferedWriter` vẫn còn ký tự chưa chuyển xuống.

Đối với mã hóa có trạng thái hoặc dữ liệu kết thúc bằng một nửa cặp surrogate, thao tác **kết thúc luồng** qua `close()` có thể có bước xử lý cuối khác với `flush()` giữa chừng. Luôn đóng writer khi đã ghi xong; đừng coi `flush()` là thay thế hoàn toàn cho `close()`.

### Quyền sở hữu tài nguyên

Một wrapper là một phần của chuỗi đóng tài nguyên. Ví dụ:

```java
try (Writer writer =
        new OutputStreamWriter(System.out, StandardCharsets.UTF_8)) {
    writer.write("Hello\n");
}
```

**Không nên** dùng kiểu trên nếu chương trình vẫn cần in ra `System.out` sau khối `try`: đóng writer có thể đóng cả chuẩn xuất mà phần còn lại của ứng dụng vẫn dùng. Với `System.out` hoặc socket stream do thành phần khác sở hữu, chỉ đóng khi chính code hiện tại sở hữu vòng đời của luồng. Nếu không, phối hợp hợp đồng sở hữu hoặc chỉ `flush()` tại chỗ phù hợp.

## 8. Ký tự không mã hóa được và dữ liệu Unicode lỗi

Không phải charset nào cũng biểu diễn được toàn bộ Unicode. `US_ASCII` chỉ biểu diễn các ký tự trong phạm vi ASCII. Chẳng hạn, chuỗi `"café"` có ký tự `é` không thể mã hóa nguyên dạng bằng `US_ASCII`.

Khi tạo `OutputStreamWriter` theo charset thông thường, mặc định lớp này **thay thế** các chuỗi ký tự không thể mã hóa hoặc surrogate không hợp lệ bằng chuỗi byte thay thế của charset. Điều này có thể làm mất thông tin nhưng không nhất thiết phát sinh exception.

Nếu định dạng dữ liệu yêu cầu **không mất ký tự**, dùng một `CharsetEncoder` được cấu hình hành động `REPORT`:

```java
CharsetEncoder encoder = StandardCharsets.US_ASCII.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try (Writer writer = new OutputStreamWriter(
        new ByteArrayOutputStream(), encoder)) {
    writer.write("café");
} catch (CharacterCodingException e) {
    // Ký tự không thể biểu diễn bằng US-ASCII;
    // xử lý lỗi ở tầng nghiệp vụ thay vì chấp nhận mất dữ liệu.
}
```

Đây là **mảnh mã minh họa**; cần import `java.io.*`, `java.nio.charset.*`. `CharacterCodingException` là một loại `IOException`. Lỗi có thể xuất hiện trong khi ghi, flush hoặc đóng, tùy bộ đệm và trạng thái mã hóa. Nếu thay `REPORT` bằng `REPLACE` hoặc `IGNORE`, ứng dụng đang chấp nhận thay thế/bỏ mất ký tự — đó phải là quyết định có chủ ý.

Trong phần lớn tình huống văn bản tiếng Việt và Unicode hiện đại, **UTF-8** là lựa chọn thực tế, tương thích và biểu diễn đầy đủ Unicode hợp lệ. Tuy nhiên, ngay cả với UTF-8, dữ liệu `String` chứa surrogate lẻ vẫn là UTF-16 không hợp lệ; quy tắc thay thế hoặc báo lỗi vẫn cần được hiểu.

## 9. Tình huống thực tế: ghi văn bản vào socket

Giả sử một giao thức văn bản dùng **UTF-8**, mỗi thông điệp kết thúc bằng byte LF (`0A`):

```java
OutputStream socketBytes = socket.getOutputStream();
Writer writer = new BufferedWriter(
        new OutputStreamWriter(socketBytes, StandardCharsets.UTF_8));

writer.write("HELLO Xin chào\n");
writer.flush();
```

Đây là mảnh mã: `socket` đã được kết nối; xử lý `IOException` và quản lý đóng socket tại tầng sở hữu. Ứng dụng phải thống nhất với đầu nhận rằng thông điệp dùng UTF-8 và được phân tách bằng LF. `flush()` giúp đẩy dữ liệu đang đệm để bên nhận có thể xử lý, nhưng **không xác nhận** rằng đầu nhận đã nhận hay xử lý xong.

Nếu gửi **giao thức nhị phân** trộn header độ dài, checksum và payload văn bản, phải định nghĩa thứ tự byte và ranh giới rõ ràng. Ví dụ cần khai báo độ dài payload bằng **số byte UTF-8** (`payload.getBytes(UTF_8).length`), không dùng `payload.length()`. Nếu mã hóa ra bytes trong bộ đệm trước, số byte này là chính xác; nếu stream dần, thiết kế cách truyền độ dài hoặc cơ chế framing trước khi gửi.

Không ghi đồng thời qua `OutputStreamWriter` và trực tiếp lên cùng `OutputStream` khi còn ký tự/byte đệm chưa xả: thứ tự thực tế có thể khác thứ tự lệnh ở hai lớp bọc. Đặt một chủ sở hữu pipeline hoặc có quy tắc đồng bộ/flush/đóng rõ ràng. Đối với HTTP, JSON hoặc các giao thức có serializer riêng, ưu tiên API serializer/thư viện phù hợp nếu chúng cung cấp quy tắc escaping, charset và framing đầy đủ.

## 10. Phân biệt với các API gần nhau

| API | Đơn vị xử lý | Khi nào phù hợp |
| --- | --- | --- |
| `OutputStream` / `FileOutputStream` | Byte | Ghi byte nhị phân hoặc byte đã mã hóa sẵn |
| `OutputStreamWriter` | Nhận ký tự, mã hóa ra byte | Ghi văn bản với charset xác định lên bất kỳ `OutputStream` |
| `BufferedWriter` | Ký tự, có bộ đệm ký tự | Nhiều lần ghi nhỏ, ghi theo dòng, bọc ngoài `Writer` |
| `InputStreamReader` | Nhận byte, giải mã ra ký tự | Đọc văn bản từ `InputStream` theo charset |
| `String.getBytes(charset)` | Mã hóa cả chuỗi thành `byte[]` | Khi toàn bộ chuỗi đã có sẵn trong bộ nhớ, cần mảng byte |
| `Files.newBufferedWriter(path, charset)` | `Writer` thuận tiện cho file | Ghi văn bản vào tệp khi không cần tự quản lý các lớp stream |

`FileWriter` là một lớp con của `OutputStreamWriter` thuận tiện cho file; Java 17 cũng có constructor nhận `Charset`. `OutputStreamWriter` đáng học vì nó cho thấy rõ mô hình chuyển đổi ký tự ↔ byte và áp dụng cho các đích không chỉ là file.

Ví dụ hiện đại cho tệp có thể ngắn gọn:

```java
try (BufferedWriter writer = Files.newBufferedWriter(
        Path.of("message.txt"), StandardCharsets.UTF_8)) {
    writer.write("Xin chào");
}
```

Đoạn minh họa cần import `java.nio.file.*`, `java.nio.charset.StandardCharsets`, `java.io.BufferedWriter` và xử lý `IOException`. Cơ chế trừu tượng hóa vẫn như trên: thao tác với ký tự, cuối cùng ghi byte bằng charset.

## 11. Lỗi thường gặp và cách phòng tránh

| Lỗi | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Không chỉ định charset | Kết quả thay đổi khi môi trường mặc định khác | Truyền `StandardCharsets.UTF_8` hoặc charset do protocol quy định |
| Ghi UTF-8 nhưng giải mã theo charset khác | Văn bản lỗi dấu, ký tự thay thế | Ghi/đọc bằng cùng charset và thỏa thuận định dạng |
| Cho rằng `String.length()` là số byte | Sai độ dài bản tin, cắt dữ liệu Unicode | Mã hóa ra bytes rồi đếm hoặc tính kích thước bằng encoder đúng |
| Gọi `write(...)` rồi đọc file/đích ngay mà chưa flush/close | Có thể chưa thấy đủ byte | `flush()` khi cần, `close()` khi hoàn tất |
| Đóng writer trên `System.out`/socket do nơi khác sở hữu | Thành phần khác không thể tiếp tục ghi | Thiết kế rõ ownership, chỉ đóng khi sở hữu đích |
| Dùng `write(int)` cho emoji code point | Cắt còn một đơn vị `char` | Dùng `write(String)` hoặc `Character.toChars(...)` |
| Đọc và ghi văn bản trên cùng stream nhưng bỏ qua charset | Dữ liệu truyền bị hỏng hoặc mất chữ | Ghép `InputStreamReader`/`OutputStreamWriter` với charset nhất quán |
| Cho rằng `flush()` đồng nghĩa file đã bền vững/đầu nhận đã ACK | Kết luận sai về trạng thái ghi | Dùng `FileDescriptor.sync`/`FileChannel.force` hoặc ACK giao thức khi thật sự cần |
| Dùng `newLine()` trong định dạng yêu cầu LF tuyệt đối | Byte cuối dòng khác giữa hệ điều hành | Dùng `write("\n")` khi protocol quy định LF |
| Mong mọi ký tự không mã hóa được sẽ ném exception | Có thể bị thay bằng byte khác và mất dữ liệu | Dùng `CharsetEncoder` với `CodingErrorAction.REPORT` |

## 12. Câu hỏi tự kiểm tra

1. **OutputStreamWriter đi theo hướng nào?** Từ ký tự Java (`Writer`) sang byte (`OutputStream`), sử dụng charset được chọn.
2. **Vì sao UTF-8 đầu ra không thể lấy kích thước bằng `String.length()`?** Vì mỗi Unicode code point có thể chiếm 1–4 byte trong UTF-8, trong khi Java `char` là đơn vị UTF-16.
3. **Vì sao nên bọc `BufferedWriter` ngoài `OutputStreamWriter`?** Để gom các lần ghi ký tự nhỏ trước khi mã hóa, giảm nhiều lần gọi chuyển đổi.
4. **`flush()` có đóng writer hay bảo đảm ACK mạng không?** Không. `flush()` đẩy các bộ đệm liên quan nhưng không đóng và không xác nhận đầu xa.
5. **`close()` trên OutputStreamWriter ảnh hưởng gì đến OutputStream?** Writer hoàn tất và đóng luồng byte bên dưới, nên phải rõ quyền sở hữu.
6. **Điều gì xảy ra khi charset không biểu diễn được ký tự?** Mặc định writer có thể thay thế; nếu cần báo lỗi, dùng `CharsetEncoder` với `REPORT`.
7. **Đoạn code ghi `"Aé中😀\n"` theo UTF-8 sinh bao nhiêu byte?** 11 byte; `String.length()` của chuỗi là 6.
8. **Nếu bản tin dùng LF (`0A`), nên dùng `newLine()` không?** Không khi yêu cầu byte chính xác; dùng `write("\n")`.

## Nguồn tra cứu API chính thức

- [Java 17 — OutputStreamWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStreamWriter.html).
- [Java 17 — BufferedWriter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html).
- [Java 17 — CharsetEncoder và CodingErrorAction](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/CharsetEncoder.html).
- [Java 17 — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html).
- [Java 17 — HexFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HexFormat.html).

**Điểm cốt lõi:** `OutputStreamWriter` không đơn thuần “ghi String ra file”. Nó là tầng mã hóa có trạng thái, nằm giữa API ký tự và API byte. Việc chọn charset, sử dụng buffer, quản lý flush/close và quyền sở hữu luồng quyết định văn bản có đúng, đủ và được chuyển đến nơi cần ghi hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-006">FileReader, FileWriter và lựa chọn charset</a>

<details>
<summary>Xem chi tiết</summary>

# FileReader, FileWriter và lựa chọn charset trong Java

## 1. Vì sao đọc/ghi tệp văn bản cần quan tâm đến charset?

Tệp trên đĩa lưu **byte**, nhưng chương trình Java thường làm việc với **ký tự** (`char`, `String`). Giữa hai thế giới này luôn có bước chuyển đổi:

```text
Đọc tệp:
Tệp (byte) ──giải mã bằng Charset──> Reader ──ký tự──> Chương trình

Ghi tệp:
Chương trình ──ký tự──> Writer ──mã hóa bằng Charset──> Tệp (byte)
```

Trong nhóm **luồng ký tự** (`java.io`), `FileReader` đọc ký tự từ tệp và `FileWriter` ghi ký tự ra tệp. Chúng giúp sử dụng API `Reader`/`Writer` thay vì tự ghép từng byte. Tuy nhiên, **chỉ gọi `new FileReader("data.txt")` hoặc `new FileWriter("data.txt")` không nói rõ tệp đang dùng bộ mã ký tự nào**. Chọn sai charset có thể tạo văn bản lỗi dấu như `CafÃ©` hoặc gây mất thông tin khi ghi.

Mục tiêu của bài: biết `FileReader`/`FileWriter` làm gì, dùng đúng API Java 17+, chọn charset rõ ràng và xử lý các tình huống thực tế như ghi đè, nối thêm, xuống dòng, lỗi mã hóa và tài nguyên tệp.

### Các khái niệm nền

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| Character / ký tự | Đơn vị văn bản ở tầng ứng dụng; API `Reader`/`Writer` trao đổi các đơn vị `char` UTF-16 của Java |
| Byte | Đơn vị 8 bit được lưu trên tệp; khác với `char` |
| Charset | Quy tắc ánh xạ giữa ký tự và byte (ví dụ UTF-8, UTF-16, ISO-8859-1) |
| Encoding | Chiều mã hóa ký tự thành byte khi ghi |
| Decoding | Chiều giải mã byte thành ký tự khi đọc |
| Default charset | Charset mặc định của Java runtime, có thể khác nhau theo phiên bản/cấu hình |
| Mojibake | Chữ bị biến dạng do đọc byte với charset khác charset đã dùng khi ghi |

Charset là **quy ước của dữ liệu**; tên file `.txt`, `.csv`, `.json` tự nó không xác định charset.

## 2. `FileReader`: đọc tệp thành ký tự

`FileReader` kế thừa `InputStreamReader` → `Reader`. Nó mở tệp, đọc byte từ nguồn và **giải mã** chúng thành các đơn vị `char` Java. Một số cách khởi tạo trên Java 17:

```java
Reader reader1 = new FileReader("notes.txt"); // Charset mặc định của JVM.
Reader reader2 = new FileReader("notes.txt", StandardCharsets.UTF_8);
Reader reader3 = new FileReader(Path.of("notes.txt").toFile(),
                                StandardCharsets.UTF_8);
```

Đây là **đoạn minh họa**, cần import `java.io.FileReader`, `java.io.Reader`, `java.nio.charset.StandardCharsets` và `java.nio.file.Path`; hãy đóng các reader sau khi dùng. Các constructor nhận `Charset` được thêm từ **Java 11**. Dùng constructor có charset để ghi rõ hợp đồng của tệp.

Một số phương thức kế thừa từ `Reader`:

| Phương thức | Ý nghĩa |
| --- | --- |
| `int read()` | Đọc một đơn vị `char` dạng `int`, hoặc `-1` khi hết luồng |
| `int read(char[] buffer)` | Đọc một số đơn vị ký tự vào mảng, trả số đã đọc hoặc `-1` |
| `int read(char[] buffer, int off, int len)` | Đọc vào đoạn `buffer[off .. off+len)` |
| `long skip(long n)` | Bỏ qua tối đa `n` đơn vị ký tự, có thể bỏ qua ít hơn |
| `void close()` | Đóng reader và tài nguyên liên quan |

Lưu ý:

- Kết quả `read()` phải lưu vào `int` để phân biệt dữ liệu với **EOF = -1**.
- `read(char[])` không cam kết điền đầy mảng mỗi lần. Chỉ xử lý `buffer[0..count)` khi `count > 0`.
- Một `char` là **một code unit UTF-16**, không nhất thiết là một Unicode code point. Ví dụ emoji nằm ngoài BMP thường gồm **hai `char`** (surrogate pair). Do đó số đơn vị `char` đọc không nhất thiết bằng số “ký tự người dùng nhìn thấy”.
- `FileReader` đọc **văn bản**; nếu phải giữ nguyên byte của ảnh, ZIP hoặc file nhị phân thì dùng `InputStream`.

### Đọc đến EOF bằng mảng ký tự

```java
// Giả định biến Reader reader đã được mở trong try-with-resources.
char[] buffer = new char[4096];
int count;
while ((count = reader.read(buffer)) != -1) {
    // Chỉ xử lý các phần tử 0 đến count - 1.
}
```

Đây là **mẫu mã trong ngữ cảnh**, chưa phải một chương trình chạy độc lập. Điều kiện dừng luôn dựa trên giá trị trả về của `read`, không dựa vào kích thước tệp bằng byte.

## 3. `FileWriter`: ghi ký tự thành byte

`FileWriter` kế thừa `OutputStreamWriter` → `Writer`. Nó nhận `String`/`char`, **mã hóa theo charset** rồi ghi byte vào tệp.

```java
Writer writer1 = new FileWriter("notes.txt"); // Charset mặc định.
Writer writer2 = new FileWriter("notes.txt", StandardCharsets.UTF_8);
Writer writer3 = new FileWriter("notes.txt", StandardCharsets.UTF_8, true);
```

Đây là **đoạn minh họa**, cần import `java.io.FileWriter`, `java.io.Writer`, `java.nio.charset.StandardCharsets` và quản lý đóng luồng. Tham số boolean `true` ở constructor thứ ba là **append**: nối thêm vào cuối tệp. Các constructor kết hợp `Charset` với `append` có từ Java 11.

Các phương thức quan trọng:

| Phương thức | Hành vi |
| --- | --- |
| `write(int c)` | Ghi một đơn vị `char` (16 bit thấp của `int`), rồi mã hóa thành byte theo charset |
| `write(char[] data)` | Ghi toàn bộ mảng ký tự |
| `write(char[] data, int off, int len)` | Ghi đoạn mảng ký tự |
| `write(String text)` | Ghi chuỗi ký tự |
| `write(String text, int off, int len)` | Ghi một đoạn chuỗi |
| `append(CharSequence csq)` | Thêm ký tự vào luồng |
| `flush()` | Đẩy dữ liệu đệm của writer xuống tầng bên dưới; writer vẫn dùng được |
| `close()` | Kết thúc writer, xử lý dữ liệu đệm còn lại và giải phóng tài nguyên |

Mặc định `new FileWriter(path, UTF_8)` **tạo tệp nếu chưa tồn tại**, và **xóa nội dung cũ để ghi mới** khi tệp đã tồn tại. Điều này quan trọng với file cấu hình, log và dữ liệu người dùng. `new FileWriter(path, UTF_8, true)` dùng để nối thêm; nó **không tự thêm ký tự xuống dòng** giữa nội dung cũ và mới.

Cũng như luồng byte, `flush()` không phải bảo đảm dữ liệu đã bền vững trên đĩa (`fsync`). Với tài nguyên do mình mở, ưu tiên `try-with-resources` để `close()` tự chạy khi kết thúc khối, kể cả nếu có ngoại lệ.

## 4. Charset nào cần chọn và vì sao UTF-8 thường là mặc định tốt?

**UTF-8** mã hóa ASCII thông dụng bằng một byte, đồng thời biểu diễn được toàn bộ Unicode bằng chuỗi byte có độ dài thay đổi. Nó phù hợp cho văn bản đa ngôn ngữ, bao gồm tiếng Việt, và có tính liên thông tốt với công cụ, API, HTML, JSON.

Một số charset thường gặp:

| Charset | Đặc điểm | Khi nào nên dùng |
| --- | --- | --- |
| `StandardCharsets.UTF_8` | Unicode, 1–4 byte cho mỗi code point | Lựa chọn ưu tiên cho file text mới và trao đổi dữ liệu hiện đại |
| `StandardCharsets.UTF_16` | Unicode, byte-order mark có thể được dùng để biểu thị thứ tự byte | Khi định dạng/tệp quy định UTF-16 |
| `StandardCharsets.UTF_16LE` / `UTF_16BE` | Dạng UTF-16 có thứ tự byte xác định | Khi hệ thống đối tác yêu cầu chính xác LE/BE |
| `StandardCharsets.ISO_8859_1` | Một byte/đơn vị ký tự trong tập Latin-1, không biểu diễn đầy đủ tiếng Việt | Chỉ khi dữ liệu thực sự là Latin-1 |
| `StandardCharsets.US_ASCII` | Chỉ các ký tự ASCII | Dữ liệu có giao kèo ASCII |
| Charset cũ như `windows-1252` | Mã hóa legacy giới hạn ký tự | Khi phải tương thích với tệp/hệ thống cũ theo đặc tả |

**Đừng chọn charset theo chữ có vẻ đọc được**. UTF-8 cũng có thể chứa hoàn toàn ASCII; nhìn dòng `Hello` không chứng minh định dạng là UTF-8. Hãy xác nhận qua tài liệu định dạng, cấu hình xuất dữ liệu, BOM nếu có, hoặc quy ước rõ ràng giữa bên ghi và bên đọc.

### Vì sao không dựa vào `Charset.defaultCharset()`?

Các phiên bản Java có khác biệt quan trọng:

- **Java 17 trở về trước:** default charset của JVM thường phụ thuộc hệ điều hành, locale hoặc cấu hình runtime. Hai máy có thể mặc định khác nhau.
- **Java 18 trở lên:** theo **JEP 400**, mặc định trong phần lớn API Java SE là **UTF-8**; có chế độ tương thích `-Dfile.encoding=COMPAT`. Một số kênh console/thiết bị còn có quy tắc encoding riêng.
- `Files.newBufferedReader(path)` và `Files.newBufferedWriter(path)` được đặc tả dùng UTF-8 khi không truyền charset, kể cả trên Java 17; vì vậy đừng đồng nhất mặc định của hai API này với `new FileReader(path)` không tham số charset.

Nếu file do ứng dụng kiểm soát, hãy chọn **`StandardCharsets.UTF_8` tường minh** ở cả đầu đọc và đầu ghi. Nếu định dạng bên ngoài bắt buộc charset khác, **phải tuân thủ charset đó** thay vì tự chuyển sang UTF-8 mà không thống nhất.

## 5. Ví dụ chạy được: vòng ghi và đọc UTF-8

Chương trình sau tạo file `hello-utf8.txt`, ghi tiếng Việt bằng `FileWriter` với UTF-8, đọc lại bằng `FileReader` cùng charset và so sánh nội dung. Nó dùng vòng `read(char[])` để thể hiện hợp đồng đọc từng phần.

**Lưu thành `FileTextRoundTrip.java` (mã nguồn UTF-8):**

```java
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTextRoundTrip {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("hello-utf8.txt");
        String original = "Tiếng Việt: Hà Nội, Đà Nẵng và Cần Thơ.";

        try (Writer writer =
                     new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
            writer.write(original);
        }

        StringBuilder restored = new StringBuilder();
        try (Reader reader =
                     new FileReader(path.toFile(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                restored.append(buffer, 0, count);
            }
        }

        System.out.println("Equal text: " + original.contentEquals(restored));
        System.out.println("File exists: " + Files.exists(path));
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac -encoding UTF-8 FileTextRoundTrip.java
java FileTextRoundTrip
```

**Stdout chính xác khi đọc/ghi thành công:**

```text
Equal text: true
File exists: true
```

Hai `try-with-resources` tạo ranh giới rõ: writer phải được đóng hoàn toàn **trước khi** reader mở để đọc tệp. Việc tạo `FileReader` sau khi `FileWriter` kết thúc cũng tránh phải đoán thời điểm dữ liệu đệm đã xuống tệp.

`StringBuilder.append(char[], offset, length)` dùng đúng `count`; nếu không, ở lượt đọc cuối sẽ lẫn ký tự cũ trong mảng đệm. Định dạng file là UTF-8 vì **cả hai constructor dùng cùng `StandardCharsets.UTF_8`**. In ra boolean ASCII giúp kết quả kiểm thử không phụ thuộc font hoặc charset của terminal.

## 6. Minh họa hỏng dấu khi chọn sai charset

Giả sử một tệp chứa từ `Café` được lưu UTF-8. Chữ `é` có byte UTF-8 là `C3 A9`. Nếu cùng hai byte này bị diễn giải dưới Latin-1 thì cho ra hai ký tự `Ã` và `©`, tạo chuỗi `CafÃ©`. Đây là **mojibake**, không phải do kiểu `String` tự làm hỏng Unicode.

**Tệp `CharsetMismatch.java`:**

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class CharsetMismatch {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("cafe-utf8.txt");
        String expected = "Café";

        Files.writeString(path, expected, StandardCharsets.UTF_8);

        String correct = Files.readString(path, StandardCharsets.UTF_8);
        String wrong = Files.readString(path, StandardCharsets.ISO_8859_1);

        System.out.println("UTF-8 matches: " + expected.equals(correct));
        System.out.println("Latin-1 matches: " + expected.equals(wrong));
        System.out.println("File hex: "
                + HexFormat.of().formatHex(Files.readAllBytes(path)));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CharsetMismatch.java
java CharsetMismatch
```

**Stdout chính xác:**

```text
UTF-8 matches: true
Latin-1 matches: false
File hex: 436166c3a9
```

`HexFormat` có từ Java 17. Các byte `43 61 66` là `Caf` ở ASCII; `C3 A9` là ký tự `é` ở UTF-8. Kết quả boolean chứng minh rằng **byte không thay đổi nhưng chuỗi sau giải mã có thể khác nhau**. Nếu một tệp vốn là Latin-1 mà ta đọc nhầm thành UTF-8, decoder có thể gặp chuỗi byte không hợp lệ và xử lý theo quy tắc lỗi của API.

Đổi cách giải mã **không phải phép sửa chữa thần kỳ**: nếu dữ liệu đã bị giải mã sai, chỉnh sửa, rồi ghi đè lại, thông tin gốc có thể đã mất. Cần biết tệp gốc được tạo bằng encoding gì.

## 7. Ghi đè, append và ranh giới dòng

Hai cách mở writer có ý nghĩa khác nhau:

```java
// Ghi mới: nội dung cũ của file sẽ bị xóa.
new FileWriter("events.txt", StandardCharsets.UTF_8);

// Nối thêm byte được tạo từ ký tự ghi:
new FileWriter("events.txt", StandardCharsets.UTF_8, true);
```

Đây là **đoạn minh họa**; khi dùng thật hãy mở trong `try-with-resources` và đóng writer. Cũng cần nhớ rằng append chỉ di chuyển điểm ghi tới cuối tệp; không tự thêm dấu cách hoặc ký tự xuống dòng.

**Tệp `AppendText.java`:**

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class AppendText {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("events.txt");

        // Chạy nhiều lần vẫn bắt đầu bằng nội dung xác định.
        try (FileWriter writer =
                     new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
            writer.write("first");
            writer.write(System.lineSeparator());
        }

        try (FileWriter writer =
                     new FileWriter(path.toFile(), StandardCharsets.UTF_8, true)) {
            writer.write("second");
            writer.write(System.lineSeparator());
        }

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader(path.toFile(), StandardCharsets.UTF_8))) {
            System.out.println("Line 1: " + reader.readLine());
            System.out.println("Line 2: " + reader.readLine());
            System.out.println("EOF: " + (reader.readLine() == null));
        }
    }
}
```

Chạy:

```bash
javac AppendText.java
java AppendText
```

**Stdout chính xác:**

```text
Line 1: first
Line 2: second
EOF: true
```

`BufferedReader.readLine()` đọc đến ký tự kết thúc dòng hoặc EOF và **loại bỏ ký tự kết thúc dòng khỏi chuỗi trả về**. Khi không còn dữ liệu, nó trả `null`; `null` khác với một dòng rỗng (`""`). `System.lineSeparator()` tạo xuống dòng theo nền tảng, thường `"\n"` trên Linux/macOS hoặc `"\r\n"` trên Windows. Nếu định dạng file yêu cầu LF cố định (ví dụ một giao thức hay file được chuẩn hóa), hãy ghi chính xác `"\n"` theo quy ước đó thay vì lệ thuộc hệ điều hành.

Tệp cũ có thể **không có dòng kết thúc**; nối `second` lúc này sẽ dính vào chữ cuối cùng của dòng trước. Bài toán ghi log cần thận trọng hơn: nhiều tiến trình nối cùng file, khóa tệp, đồng bộ và rollover không được bảo đảm chỉ bằng `append=true`.

## 8. FileReader/FileWriter và BufferedReader/BufferedWriter

`FileReader` và `FileWriter` mở tệp và chuyển đổi charset. Chúng đã có cơ chế đệm nhất định khi chuyển đổi giữa byte và ký tự, nhưng không cung cấp mọi tiện ích đọc/ghi theo dòng của các lớp buffered wrapper.

```java
// Đoạn minh họa; imports java.io.* và StandardCharsets.
try (BufferedReader reader = new BufferedReader(
        new FileReader("input.txt", StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý từng dòng, không giữ toàn bộ file trong RAM.
    }
}
```

`BufferedReader` có `readLine()`; `BufferedWriter` có `newLine()`. Wrapper hỗ trợ các thao tác kích thước nhỏ hiệu quả hơn tùy workload. Khi dùng `try-with-resources`, chỉ khai báo và đóng wrapper ngoài nếu wrapper sở hữu luồng trong; đóng wrapper sẽ đóng reader/writer bên dưới.

Có thể tạo writer theo dòng:

```java
// Đoạn minh họa; imports java.io.* và StandardCharsets.
try (BufferedWriter writer = new BufferedWriter(
        new FileWriter("output.txt", StandardCharsets.UTF_8))) {
    writer.write("Dòng thứ nhất");
    writer.newLine();
    writer.write("Dòng thứ hai");
}
```

`newLine()` dùng dấu kết thúc dòng theo nền tảng. Nếu file có quy định newline LF/CRLF riêng, hãy ghi đúng ký tự đó.

### Dùng API Files hiện đại khi thuận tiện

Java 17 còn có API `java.nio.file.Files` trả về `BufferedReader`/`BufferedWriter`:

```java
Path path = Path.of("notes.txt");

// UTF-8 được ghi rõ, giữ hợp đồng nội dung rõ ràng:
try (BufferedWriter writer =
         Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
    writer.write("Xin chào!");
}

try (BufferedReader reader =
         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    System.out.println(reader.readLine());
}
```

Đoạn này yêu cầu import `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.charset.StandardCharsets`, `java.io.BufferedReader`, `java.io.BufferedWriter` và xử lý `IOException`. Nếu chạy thành công, stdout là `Xin chào!` trên terminal hỗ trợ Unicode. Cả `Files.newBufferedReader(path)` lẫn `Files.newBufferedWriter(path)` mặc định dùng UTF-8, song truyền tường minh vẫn giúp người đọc code nhận ra quy ước.

Với tệp text nhỏ, `Files.readString(path, StandardCharsets.UTF_8)` và `Files.writeString(path, text, StandardCharsets.UTF_8)` ngắn gọn. Tuy nhiên, đọc cả tệp thành một chuỗi cần bộ nhớ tỷ lệ kích thước tệp; dùng `Reader`/`BufferedReader` để xử lý tuần tự dữ liệu lớn. `Files.writeString` mặc định tạo hoặc truncate tệp; muốn append thì dùng `StandardOpenOption.APPEND` cùng các tùy chọn tạo file phù hợp.

### Bảng chọn cách tiếp cận

| Nhu cầu | API hợp lý |
| --- | --- |
| Hiểu cơ chế reader/writer gắn tệp | `new FileReader(file, charset)`, `new FileWriter(file, charset)` |
| Đọc file text theo từng dòng | `Files.newBufferedReader(path, charset)` hoặc `BufferedReader(FileReader)` |
| Ghi file text từng dòng | `Files.newBufferedWriter(path, charset)` hoặc `BufferedWriter(FileWriter)` |
| Tệp nhỏ, đọc/ghi nguyên chuỗi | `Files.readString` / `Files.writeString` |
| Chỉ sao chép tệp nguyên trạng, không cần hiểu nội dung | API byte như `Files.copy` / `InputStream` |
| Chuyển mã giữa các định dạng text | Reader giải mã theo charset nguồn rồi Writer mã hóa theo charset đích |

## 9. Chuyển mã charset và xử lý tệp legacy

Giả sử đã xác minh tệp cũ thực sự lưu Latin-1, muốn chuyển sang UTF-8. Hướng đi đúng là:

```text
legacy.txt (Latin-1 bytes)
    └─ FileReader(..., ISO_8859_1)
            └─ các char Unicode trong Java
                    └─ FileWriter(..., UTF_8)
                            └─ utf8.txt (UTF-8 bytes)
```

**Tệp `ConvertLatin1ToUtf8.java`:**

```java
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class ConvertLatin1ToUtf8 {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println(
                    "Usage: java ConvertLatin1ToUtf8 <latin1-source> <utf8-target>");
            return;
        }

        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);

        if (source.toAbsolutePath().normalize()
                    .equals(target.toAbsolutePath().normalize())
                || (java.nio.file.Files.exists(target)
                    && java.nio.file.Files.isSameFile(source, target))) {
            throw new IllegalArgumentException("Use distinct files");
        }

        try (Reader reader =
                     new FileReader(source.toFile(), StandardCharsets.ISO_8859_1);
             Writer writer =
                     new FileWriter(target.toFile(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
        }

        System.out.println("Conversion complete");
    }
}
```

Chạy sau khi có tệp nguồn Latin-1:

```bash
javac ConvertLatin1ToUtf8.java
java ConvertLatin1ToUtf8 legacy.txt utf8.txt
```

**Stdout khi hoàn tất thành công:**

```text
Conversion complete
```

Bài toán này khác **sao chép nhị phân**: file đầu ra có thể chứa byte khác và kích thước khác dù nội dung ký tự tương đương. Không thể suy ra charset nguồn chắc chắn từ tên file; ví dụ đang giả định charset nguồn đã được xác minh. Không dùng hai đường dẫn cùng tệp vì mở writer có thể xóa nguồn. Kiểm tra đường dẫn trước khi mở chỉ giảm rủi ro trong tình huống thông thường, không phải cơ chế chống thay đổi đồng thời nguyên tử.

**Hạn chế của ISO-8859-1:** nó không bao phủ tất cả chữ tiếng Việt. Nếu dữ liệu gốc đến từ encoding kiểu `windows-1258` hay một charset khác, phải chọn đúng charset thực tế, ví dụ `Charset.forName("windows-1258")` **nếu charset đó được runtime hỗ trợ**. Không thay bừa bằng Latin-1 khi thấy file lỗi dấu.

## 10. Encoding lỗi, ký tự không biểu diễn được và BOM

### 10.1 Charset không phải lúc nào cũng nhận được mọi dữ liệu

Với charset hạn chế như US-ASCII hoặc Latin-1, một số ký tự Unicode **không thể mã hóa trực tiếp**. Ngược lại, byte stream có thể chứa chuỗi byte **không hợp lệ** đối với UTF-8.

Phản ứng khi gặp dữ liệu không hợp lệ/phần ký tự không biểu diễn được phụ thuộc API và cách cấu hình decoder/encoder. Một số đường tạo reader/writer thuận tiện có thể dùng **replacement** (ký tự thay thế khi giải mã hoặc byte thay thế khi mã hóa), khiến chương trình tiếp tục chạy nhưng mất thông tin. Vì vậy “không có exception” **không chứng minh dữ liệu được giữ nguyên**.

Nếu bắt buộc phát hiện dữ liệu không hợp lệ, có thể cấu hình **`CharsetDecoder`** và chọn hành động `CodingErrorAction.REPORT`:

```java
// Minh họa đọc UTF-8 nghiêm ngặt từ tệp đã có.
CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

try (Reader reader = new InputStreamReader(
        Files.newInputStream(Path.of("input.txt")), decoder)) {
    char[] buf = new char[4096];
    while (reader.read(buf) != -1) {
        // Xử lý nội dung đã giải mã.
    }
}
```

Đây là **đoạn minh họa**; cần `java.nio.charset.CharsetDecoder`, `CodingErrorAction`, `StandardCharsets`, `java.io.InputStreamReader`, `java.io.Reader`, `java.nio.file.Files`, `Path`. Khi gặp byte UTF-8 malformed, decoder ở chế độ `REPORT` có thể ném `MalformedInputException` (một `IOException`). Cần quyết định rõ chính sách ứng dụng: báo lỗi, thay ký tự hoặc bỏ qua, không âm thầm sửa nội dung quan trọng.

Tương tự có thể cấu hình `CharsetEncoder` với `REPORT` khi cần phát hiện ký tự không mã hóa được.

### 10.2 Byte Order Mark (BOM)

BOM là một mẫu byte ở đầu một số tệp Unicode để nhận biết encoding/thứ tự byte. Với UTF-8, BOM thường là `EF BB BF` và **không bắt buộc**. `FileWriter` với UTF-8 **không tự chèn BOM**; nếu dữ liệu nguồn có BOM, cần hiểu cách công cụ/decoder cụ thể xử lý, tránh xuất hiện ký tự `U+FEFF` không mong muốn ở đầu nội dung.

Với UTF-16, thứ tự byte là quan trọng. Dùng `UTF_16`, `UTF_16LE`, `UTF_16BE` theo **đúng định dạng được quy định**; đừng phỏng đoán dựa vào hệ điều hành.

### 10.3 Ký tự Unicode và việc cắt buffer

Vì `Reader` trả đơn vị `char` UTF-16, một code point ngoài BMP có thể chia thành **hai code unit**. Vòng đọc/gửi `char[]` không vì thế có nghĩa là mỗi lần đọc trả nguyên một “ký tự hiển thị”. Nếu ứng dụng cần xử lý Unicode code point độc lập, có thể dùng `String.codePoints()` sau khi tập hợp một đoạn chuỗi hợp lệ và chú ý cặp surrogate bị chia qua ranh giới buffer.

Khi **sao chép ký tự trung thực** từ Reader sang Writer có cùng quy ước, việc ghi từng mảng theo thứ tự vẫn phù hợp; quan trọng là không loại bỏ hoặc sửa cặp surrogate giữa các lần đọc.

## 11. Đọc theo dòng và định dạng xuống dòng

Khi cần các dòng logic, `BufferedReader.readLine()` tiện hơn tự tìm `'\n'`. Nó nhận biết LF, CR hoặc CRLF và trả nội dung **không chứa dấu kết thúc dòng**. Tệp có dòng rỗng sẽ cho `""` còn EOF là `null`.

Nếu đọc bằng `readLine()` rồi viết lại với `writer.newLine()`, file đầu ra có thể đổi dấu xuống dòng từ LF sang CRLF hoặc ngược lại tùy môi trường. Nếu mục tiêu là **giữ nguyên nội dung tệp đến từng byte**, không giải mã rồi ghi lại theo dòng mà nên sao chép byte.

Trong các định dạng có yêu cầu xuống dòng cố định, ví dụ trao đổi dữ liệu giữa Linux và Windows, thống nhất `"\n"` hay `"\r\n"` theo đặc tả. Nếu file text được tạo bởi nhiều thành phần khác nhau, hãy xác định cả **charset** và **newline convention**.

## 12. Vòng đời tài nguyên và các lỗi cần xử lý

Dùng `try-with-resources` để đóng `FileReader`/`FileWriter` ngay cả khi phát sinh ngoại lệ:

```java
try (FileReader reader =
         new FileReader("input.txt", StandardCharsets.UTF_8)) {
    // Xử lý dữ liệu.
}
```

Đây là **đoạn minh họa** có các import tương ứng. Nếu `read()`/ `write()` xảy ra lỗi, thường nhận `IOException` hoặc ngoại lệ con; thao tác mở reader có thể thất bại nếu file không tồn tại/không có quyền đọc, writer có thể thất bại nếu không thể tạo/ghi file. Kết quả ghi dở có thể để lại **file đầu ra chỉ chứa một phần dữ liệu**; đóng tài nguyên không biến cả thao tác thành giao dịch nguyên tử.

Một số nguyên tắc:

1. **Rõ người sở hữu luồng**: nếu phương thức nhận một Reader do bên gọi sở hữu, cần quy định bên nào chịu trách nhiệm đóng. Không tùy tiện đóng luồng được dùng chung.
2. **Tránh ghi đè ngoài ý muốn**: với file quan trọng, kiểm tra tình huống tồn tại tệp, dùng lựa chọn tạo file phù hợp hoặc một quy trình ghi tạm rồi thay thế khi thành công.
3. **Đọc từng phần khi file lớn**: `Files.readString`/ `readAllLines` tích lũy dữ liệu trong RAM, trong khi `BufferedReader.readLine` xử lý từng dòng.
4. **Phân biệt lỗi kỹ thuật và lỗi dữ liệu**: file mở được nhưng sai charset không nhất thiết phát sinh `IOException` ngay.
5. **Đừng trông cậy `flush()` để bảo đảm tính bền vững**: ứng dụng cần bảo đảm ghi xuống thiết bị phải dùng cơ chế đồng bộ file riêng.
6. **Nếu mở nhiều resource trong cùng try**: Java đóng theo thứ tự **ngược** thứ tự khai báo và giữ các lỗi đóng bổ sung trong `suppressed exceptions`.

## 13. Các lỗi thường gặp

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| `new FileReader("a.txt")` trên Java 17 mà quên default charset | Có thể đọc khác nhau giữa các máy | `new FileReader("a.txt", UTF_8)` hoặc đúng charset của dữ liệu |
| Ghi UTF-8 nhưng đọc Latin-1/charset mặc định khác | Sai dấu, mojibake | Thống nhất charset hai phía |
| Ghi tiếng Việt với US-ASCII | Ký tự không biểu diễn được có thể bị thay | Chọn UTF-8 hoặc encoding hỗ trợ dữ liệu |
| Dùng `byte[]` rồi ép thẳng sang `char` để đọc văn bản | Bỏ qua quy tắc mã hóa đa byte | Dùng `Reader`/`CharsetDecoder` |
| Ghi `new FileWriter(path, UTF_8)` lên tệp đang có dữ liệu | Tệp cũ bị truncate | Chủ động chọn ghi mới, append hoặc fail-if-exists |
| `append=true` nhưng không chèn newline | Nội dung bị dính dòng | Thêm newline theo định dạng |
| Cho rằng mỗi `read(char[])` lấp đầy buffer | Mất dữ liệu hoặc lấy ký tự cũ | Dựa đúng `count` |
| So `char` trực tiếp với EOF rồi bỏ qua kiểu trả về | Dễ nhầm `int -1` với dữ liệu ký tự | Giữ kết quả `read()` ở `int` |
| Quên `close()` writer | Có thể giữ tài nguyên, chưa đẩy hết dữ liệu đệm | `try-with-resources` |
| Đọc toàn bộ file rất lớn vào `String` | Tốn nhiều bộ nhớ | Streaming theo khối hoặc dòng |
| Dùng `readLine()` rồi `newLine()` khi cần giữ byte gốc | Newline/encoding có thể thay đổi | Sao chép byte nguyên trạng |
| Giả định mọi file UTF-8 đều có BOM | Có thể bỏ sai byte đầu hoặc để lại U+FEFF | Theo đặc tả file/đầu vào thực tế |

## 14. Quy tắc chọn API trong thực tế

- **Chỉ muốn lưu/đọc văn bản mới theo UTF-8 và thao tác đơn giản:** `Files.writeString`/`Files.readString` cho file nhỏ; `Files.newBufferedReader`/`newBufferedWriter` cho dữ liệu dài.
- **Đang dùng `FileReader`/`FileWriter`:** truyền `StandardCharsets.UTF_8` hoặc charset được đặc tả thay vì dùng mặc định không rõ.
- **Muốn thao tác theo dòng:** dùng `BufferedReader`/`BufferedWriter`.
- **Muốn sửa hay chuyển charset của tài liệu legacy:** biết chính xác charset nguồn, giải mã đúng rồi mã hóa theo charset đích; test với các ký tự khó như chữ có dấu và emoji.
- **Muốn giữ byte 100%:** ưu tiên API byte, không tự giải mã rồi mã hóa lại.
- **Cần xử lý dữ liệu có thể sai encoding:** xác định chính sách lỗi và cân nhắc decoder/encoder với `CodingErrorAction.REPORT`.

Không có một charset “đúng cho mọi tệp”: **UTF-8 là lựa chọn tốt cho tệp mới**, nhưng với file hiện hữu thì charset đúng là charset mà nguồn dữ liệu thực sự sử dụng.

## 15. Bài tập tự kiểm tra

1. Chạy `FileTextRoundTrip` hai lần và xác nhận `hello-utf8.txt` bị ghi lại từ đầu. Đổi `original` thành chuỗi có emoji và dấu tiếng Việt, kiểm tra `Equal text: true`.
2. Chạy `CharsetMismatch`, lý giải từng nhóm byte trong `436166c3a9` và vì sao đọc Latin-1 không bằng bản gốc.
3. Trong `AppendText`, bỏ `System.lineSeparator()` ở lần ghi đầu. Dự đoán `readLine()` sẽ trả gì và kiểm chứng.
4. Viết hàm đọc file UTF-8 theo dòng, đếm số dòng rỗng. Phân biệt dòng `""` với `null` ở EOF.
5. Xây dựng tệp UTF-8 sai (ví dụ byte `0xC3` đứng một mình cuối file), thử decoder mặc định và decoder `REPORT`, ghi lại sự khác nhau về kết quả hoặc exception.
6. Tự trả lời: vì sao Java 17 không nên phụ thuộc vào `new FileReader(path)` khi dữ liệu đa ngôn ngữ? Khi nào cần `ISO_8859_1`? Khi sao chép file ảnh có nên dùng `FileReader`/`FileWriter` không?
7. Cho tệp `legacy.txt` được xác nhận là Latin-1, thử chương trình chuyển mã và kiểm tra byte đầu/đuôi của tệp đích; giải thích vì sao tệp kết quả UTF-8 có thể lớn hơn.

## Tài liệu tham khảo

- Java SE 17 API: [`FileReader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileReader.html), [`FileWriter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileWriter.html).
- Java SE 17 API: [`Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`StandardCharsets`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html).
- OpenJDK: [JEP 400 — UTF-8 by Default](https://openjdk.org/jeps/400).

**Ghi nhớ:** `FileReader` đọc byte từ tệp rồi giải mã thành ký tự; `FileWriter` mã hóa ký tự thành byte rồi ghi. Chọn **đúng charset của dữ liệu**, ưu tiên **UTF-8 rõ ràng** với file mới, quản lý resource bằng `try-with-resources`, và kiểm tra cẩn thận chế độ **ghi đè/append**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-007">Reader.transferTo và giới hạn xử lý dữ liệu lớn</a>

<details>
<summary>Xem chi tiết</summary>

# `Reader.transferTo` và giới hạn xử lý dữ liệu lớn

## 1. Mục tiêu và phạm vi

Trong nhóm **luồng ký tự (character streams)** của Java I/O, `Reader.transferTo(Writer)` cho phép sao chép toàn bộ dữ liệu ký tự từ nguồn `Reader` sang đích `Writer` mà không phải viết vòng lặp thủ công cho tình huống đọc đến hết dữ liệu. API này có từ **Java 10** và dùng được trên Java 17+.

Sau bài này, bạn cần hiểu chính xác API sao chép gì, trả về đơn vị gì, khi nào nó xử lý dữ liệu lớn với bộ nhớ nhỏ, vì sao vẫn có thể gây hết bộ nhớ/đầy ổ đĩa, và cách đặt giới hạn khi nguồn dữ liệu không đáng tin cậy.

> `transferTo` **không có tham số giới hạn**, không tự đóng hoặc flush hai luồng, không đặt thời gian chờ và không bảo đảm dữ liệu đã ghi được rollback khi xảy ra lỗi.

Với dữ liệu nhị phân (ảnh, ZIP, tệp nén, giao thức byte), dùng `InputStream`/`OutputStream` hoặc API tệp phù hợp. `Reader`/`Writer` dành cho nội dung **ký tự** và thường cần xử lý charset ở ranh giới tệp/luồng byte.

## 2. Cách `Reader.transferTo` hoạt động

Hình thức gọi:

```java
long transferred = reader.transferTo(writer);
```

Phương thức đọc ký tự từ `reader` theo các khối, lần lượt viết các ký tự đã đọc sang `writer` và dừng khi reader báo **EOF**. Không có phép biến đổi nghiệp vụ như tách dòng, lọc ký tự hay phân tích nội dung. Trong triển khai thông thường, việc sao chép theo khối giúp không phải giữ toàn bộ nội dung trong một mảng lớn, nhưng bộ nhớ thực tế phụ thuộc cả reader, writer và ứng dụng bao quanh.

| Thuộc tính | Ý nghĩa |
| --- | --- |
| Khai báo API | `public long transferTo(Writer out) throws IOException` trong `java.io.Reader` |
| Dữ liệu | Các đơn vị ký tự `char` (UTF-16 code units) |
| Điểm dừng | Reader báo EOF; nếu nguồn tiếp tục có dữ liệu, hàm tiếp tục đọc |
| Giá trị trả về | `long`: số đơn vị `char` truyền được, **không phải số byte** |
| Thứ tự | Các ký tự đến writer theo thứ tự reader cung cấp |
| Ngoại lệ | Lỗi đọc/ghi ném `IOException`; dữ liệu đích có thể đã ghi một phần |
| Đóng tài nguyên | Người gọi phải quản lý `close()` bằng `try-with-resources` khi phù hợp |
| Flush | Không dựa vào `transferTo` để tự flush writer |
| Giới hạn dung lượng | Không có giới hạn ký tự, byte hoặc tệp được tích hợp |
| Thời gian | Có thể block khi đọc/ghi; không tự đặt timeout/cancellation |

Phân biệt ba ý niệm thường bị nhầm:

- **`Reader.transferTo(Writer)`** sao chép ký tự và trả số UTF-16 code units.
- **`InputStream.transferTo(OutputStream)`** sao chép byte, phù hợp dữ liệu nhị phân và trả số byte.
- **`java.util.stream.Stream<T>`** là API xử lý phần tử theo mô hình stream, không phải luồng byte/ký tự trong `java.io`.

### 2.1. Số `char` không bằng số byte

Java `char` là một đơn vị mã UTF-16. Một Unicode code point ngoài BMP (ví dụ emoji) được biểu diễn bằng **hai** `char`. Trong UTF-8, một ký tự có thể chiếm nhiều byte.

| Văn bản | Unicode code points | UTF-16 code units | UTF-8 bytes |
| --- | ---: | ---: | ---: |
| `ABC` | 3 | 3 | 3 |
| `A😀B` | 3 | 4 | 6 |
| `A😀B\n` (có LF) | 4 | 5 | 7 |

Do đó `transferTo` trả `5` khi truyền `A😀B\n` qua reader/writer, nhưng tệp UTF-8 chứa **7 byte**. Không lấy `transferred` làm giá trị dung lượng file hoặc quota mạng. `String.length()` cũng đo đơn vị UTF-16, không đo số code points hay byte.

### 2.2. Charset nằm ở hai đầu luồng

Khi nguồn là tệp, `Files.newBufferedReader(path, StandardCharsets.UTF_8)` giải mã byte UTF-8 thành ký tự; khi ghi tệp, `Files.newBufferedWriter(path, StandardCharsets.UTF_8)` mã hóa ký tự trở lại UTF-8. Bản thân `Reader.transferTo` chỉ chuyển các ký tự.

```text
File source UTF-8 bytes
      |
Files.newBufferedReader(..., UTF_8)
      |
Reader -- char[] theo từng khối --> Writer
                                     |
                              Files.newBufferedWriter(..., UTF_8)
                                     |
                              File target UTF-8 bytes
```

Khi cần giữ nguyên **byte**, chẳng hạn sao chép file ZIP hoặc kiểm tra checksum nhị phân, không giải mã và mã hóa lại bằng character streams. Nếu đầu vào chứa byte không hợp lệ theo charset, reader/decoder có thể ném lỗi hoặc áp dụng chính sách thay thế tùy cách cấu hình; không tự giả định mọi byte tùy ý đều là văn bản.

## 3. Ví dụ chạy được: chuyển toàn bộ tệp văn bản

Chương trình Java 17 dưới đây dùng thư mục tạm, tạo tệp đầu vào nhỏ, chuyển ký tự UTF-8 sang tệp đích, rồi kiểm tra nội dung. Hai reader/writer được đóng bằng `try-with-resources`. Đoạn so sánh bằng `Files.readString` chỉ dành cho **tệp thử nhỏ**, không phải cách xác minh tệp hàng GB.

Lưu thành **`ReaderTransferToDemo.java`** (UTF-8):

```java
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReaderTransferToDemo {
    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("reader-transfer-");
        Path source = directory.resolve("source.txt");
        Path target = directory.resolve("target.txt");

        try {
            Files.writeString(source, "A😀B\n", StandardCharsets.UTF_8);

            long transferred;
            try (Reader in = Files.newBufferedReader(
                        source, StandardCharsets.UTF_8);
                 Writer out = Files.newBufferedWriter(
                        target, StandardCharsets.UTF_8)) {
                transferred = in.transferTo(out);
            }

            String before = Files.readString(source, StandardCharsets.UTF_8);
            String after = Files.readString(target, StandardCharsets.UTF_8);
            System.out.println("UTF-16 units: " + transferred);
            System.out.println("Contents match: " + before.equals(after));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch, chạy với JDK 17+:

```bash
javac -encoding UTF-8 ReaderTransferToDemo.java
java ReaderTransferToDemo
```

`stdout` của lần chạy thành công:

```text
UTF-16 units: 5
Contents match: true
```

Giải thích: `A` và `B` chiếm một `char` mỗi ký tự, `😀` chiếm hai `char`, `\n` chiếm một `char`, tổng cộng 5. `Reader.transferTo` không đóng luồng; hai đối tượng được đóng khi thoát khối `try-with-resources`, kể cả khi có ngoại lệ. Sau khi đóng writer, chương trình đọc tệp đích để kiểm tra. `finally` cố dọn hai tệp và thư mục tạm; lỗi dọn tài nguyên cũng có thể phát sinh `IOException`, nên ứng dụng thực tế phải chọn chính sách xử lý lỗi rõ ràng.

**Giới hạn ví dụ:** Nội dung rất nhỏ; không có kiểm soát kích thước tối đa, timeout, ghi nguyên tử hay xác minh checksum streaming. Không lấy `Files.readString` trên tệp lớn chỉ để so sánh nội dung.

## 4. Khi nào xử lý dữ liệu lớn mà không dùng nhiều heap?

Nếu đầu vào là `Files.newBufferedReader` và đầu ra là `Files.newBufferedWriter`, reader và writer có bộ đệm hữu hạn, dữ liệu được chuyển dần, nên ứng dụng **không bắt buộc** phải nạp toàn bộ tệp vào RAM. Phân biệt:

- **Bộ đệm truyền**: mảng ký tự của quá trình đọc/ghi thường có kích thước giới hạn; việc sao chép không nhất thiết tạo một `String` bằng kích thước tệp.
- **Bộ nhớ của đích**: `StringWriter`, `StringBuilder` hoặc `List<String>` tích lũy dữ liệu trong heap; chúng vẫn có thể lớn tỷ lệ với toàn bộ input.
- **Bộ nhớ nguồn**: nếu ứng dụng đã gọi `Files.readString` trước đó, nội dung lớn vốn đã được giữ trong bộ nhớ dù về sau gọi `transferTo`.
- **Chi phí ngoài heap**: đích tệp có thể tăng liên tục và làm hết dung lượng đĩa; hệ điều hành, bộ mã hóa và các lớp bao có thêm chi phí I/O.

Ví dụ phản diện sau minh họa writer gom tất cả nội dung:

```java
// Đoạn minh họa: biến path phải là Path hợp lệ.
// Không phù hợp nếu nguồn có thể dài hàng GB.
try (Reader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    java.io.StringWriter writer = new java.io.StringWriter();
    reader.transferTo(writer);
    String entireContent = writer.toString();
}
```

`StringWriter` giữ tất cả ký tự trong bộ nhớ; `toString()` tiếp tục tạo đối tượng `String` kết quả. Không có gì trong `transferTo` bảo đảm toàn pipeline dùng ít RAM khi writer tích lũy nội dung. Đoạn trên không phải chương trình độc lập và đòi hỏi `path` cùng các import thích hợp.

Với đầu vào lớn, đầu ra nên có khả năng ghi dần (tệp, socket hoặc bộ tiếp nhận có backpressure), có cách đo/giới hạn dung lượng, không gom hết dữ liệu vào một collection. Nếu phải xử lý từng bản ghi, ưu tiên vòng đọc theo dòng/khối, tránh tạo bản sao đầy đủ.

## 5. Những giới hạn mà `Reader.transferTo` không cung cấp

### 5.1. Dung lượng và đơn vị đo

Không có đối số `maxChars` hoặc `maxBytes`. `long transferred` chỉ có ý nghĩa **sau** khi công việc thành công; nó không ngăn writer ghi quá dung lượng cho phép. Nếu đầu ra là tệp, số byte viết thực còn phụ thuộc charset. Hạn mức byte nghiêm ngặt phải được áp dụng ở tầng byte hoặc đầu ra mã hóa được đo đếm, không suy từ UTF-16 units.

### 5.2. Thời gian, EOF và nguồn mở lâu

`Reader.transferTo` đọc đến EOF. Một reader kết nối với socket/pipe có thể chờ khi chưa có dữ liệu mới; nếu nguồn không kết thúc, lệnh sao chép có thể kéo dài vô hạn. Đặt **read timeout, deadline, cancellation hoặc giới hạn giao thức** tại lớp nguồn/thành phần bao quanh khi hỗ trợ. Không coi việc dùng `BufferedReader` là cơ chế timeout.

### 5.3. Ghi dở khi lỗi

Nếu `Reader.read` hoặc `Writer.write` ném `IOException`, writer có thể đã nhận một số ký tự. Không có rollback tự động. Với đầu ra có ý nghĩa nghiệp vụ, cân nhắc ghi vào tệp tạm, xác minh hoàn chỉnh rồi mới công bố/thay thế. `Files.move` cùng `ATOMIC_MOVE` tùy khả năng của filesystem; không giả định mọi nền tảng đều hỗ trợ.

### 5.4. Không xử lý từng dòng hoặc dừng ở điều kiện

`transferTo` chuyển tất cả ký tự. Nếu phải kiểm tra độ dài từng dòng, xử lý CSV, cắt tại marker, lọc record hoặc đếm dòng, cần vòng đọc thích hợp với quy tắc nghiệp vụ. `BufferedReader.readLine()` bỏ ký tự kết thúc dòng; muốn giữ nguyên `\n` và `\r\n` thì không tái tạo văn bản bằng `readLine()` một cách mù quáng.

## 6. Sao chép có giới hạn: vòng lặp thay cho `transferTo`

Để chặn nguồn quá dài theo **số UTF-16 code units**, dùng vòng lặp với mảng `char[]` có kích thước giới hạn. Đoạn dưới sẽ báo lỗi khi nguồn vượt mức cho phép, thay vì im lặng coi bản bị cắt cụt là hoàn chỉnh.

Lưu thành **`BoundedReaderCopyDemo.java`**:

```java
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class BoundedReaderCopyDemo {
    static long copyWithLimit(
            Reader reader, Writer writer, long maxUnits) throws IOException {
        if (maxUnits < 0) {
            throw new IllegalArgumentException("maxUnits must be nonnegative");
        }

        char[] buffer = new char[4096];
        long copied = 0;

        while (true) {
            long remaining = maxUnits - copied;

            if (remaining == 0) {
                if (reader.read() != -1) {
                    throw new IOException(
                            "Input exceeds " + maxUnits + " UTF-16 units");
                }
                return copied;
            }

            int requested = (int) Math.min(buffer.length, remaining);
            int read = reader.read(buffer, 0, requested);

            if (read == -1) {
                return copied;
            }
            if (read == 0) {
                throw new IOException("Reader made no progress");
            }

            writer.write(buffer, 0, read);
            copied += read;
        }
    }

    public static void main(String[] args) throws IOException {
        try (Reader reader = new StringReader("abcdef");
             StringWriter writer = new StringWriter()) {
            try {
                copyWithLimit(reader, writer, 4);
                System.out.println("Copy complete");
            } catch (IOException error) {
                System.out.println("Limit exceeded: " + error.getMessage());
            }
            System.out.println("Partial output: " + writer);
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 BoundedReaderCopyDemo.java
java BoundedReaderCopyDemo
```

`stdout`:

```text
Limit exceeded: Input exceeds 4 UTF-16 units
Partial output: abcd
```

**Cơ chế:** `copyWithLimit` đọc tối đa phần hạn mức còn lại. Sau khi đã ghi 4 đơn vị `abcd`, nó đọc thử thêm một đơn vị để xác định nguồn có tiếp tục hay không. Nguồn còn `e`, vì vậy ném `IOException`. Writer đang chứa `abcd`: **báo lỗi không có nghĩa đầu ra đã được hoàn tác**.

Các giới hạn còn lại của ví dụ:

1. **Hạn mức ký tự khác hạn mức byte**: đây là UTF-16 units; UTF-8 có độ dài byte khác. Nếu quota áp dụng cho dữ liệu lưu trữ, phải đếm byte thực.
2. **Có thể cắt giữa surrogate pair**: một giới hạn `char` rơi giữa hai đơn vị của emoji có thể làm nội dung đầu ra mất hợp lệ khi mã hóa. Nếu yêu cầu giới hạn theo code point, hãy thiết kế bộ đọc có xử lý surrogate pair; nếu yêu cầu không cắt grapheme cluster, cần quy tắc phân đoạn Unicode cao hơn.
3. **Vẫn có thể block**: bước đọc thêm khi chạm hạn mức cần xác định có EOF thật; với nguồn truyền liên tục hoặc không kết thúc, phải kết hợp timeout/cancellation ở tầng nguồn.
4. **Không tự xóa bản ghi dở**: nếu phát hiện vượt hạn mức sau khi đã ghi, đích cần được bỏ hoặc ghi vào file tạm thay vì được công bố.
5. **Ví dụ dùng input nhỏ để xác minh stdout**: `StringReader`/`StringWriter` phù hợp minh họa, không phải mô hình xử lý tệp lớn trong thực tế.

Trường hợp nghiệp vụ chỉ muốn **cắt lấy tối đa N đơn vị** và không cần kiểm tra còn dữ liệu hay không, có thể bỏ bước đọc thử ngoài hạn mức, nhưng phải ghi rõ đầu ra là bản cắt cụt theo chủ đích.

## 7. Chọn API theo yêu cầu

| Bài toán | Gợi ý API | Điểm cần kiểm soát |
| --- | --- | --- |
| Chuyển toàn bộ văn bản từ reader sang writer | `Reader.transferTo` | EOF, charset ở đầu/cuối, quản lý tài nguyên |
| Đọc tệp UTF-8 lớn và ghi tệp UTF-8 | `Files.newBufferedReader` → `transferTo` → `Files.newBufferedWriter` | Đường dẫn, dung lượng tệp đích, ngoại lệ |
| Giới hạn tổng số UTF-16 units | Vòng lặp `read(char[])`/`write` có bộ đếm | Surrogate pairs; tác động của bản ghi dở |
| Giới hạn theo byte | Byte streams và bộ đếm byte | Dữ liệu mã hóa thực; không đếm bằng `char` |
| Sao chép nguyên tệp | `Files.copy` | Xử lý tệp đã tồn tại, quyền truy cập, metadata |
| Sao chép nhị phân | `InputStream.transferTo(OutputStream)` | Byte, resource cleanup, quota |
| Phân tích từng dòng | `BufferedReader.readLine()`, hoặc `lines()` | Line ending, dòng quá dài, đóng stream |
| Đọc theo vị trí tệp | `FileChannel` với `ByteBuffer` | Position/limit, đọc/ghi từng phần |
| Truyền dữ liệu có thể mở vô hạn | API nguồn có timeout/cancellation kết hợp quota | Không đợi `transferTo` tự dừng |

Lựa chọn chính dựa trên **định dạng dữ liệu** (byte hay ký tự), **khả năng kết thúc**, **giới hạn tài nguyên** và **yêu cầu nhất quán** của đầu ra.

## 8. Lỗi phổ biến và phương pháp kiểm thử

**Lỗi dễ gặp:**

- Đo số byte bằng `Reader.transferTo`; với UTF-8 và emoji, giá trị sẽ sai.
- Cho rằng sao chép theo khối luôn tiết kiệm RAM, nhưng đích là `StringWriter` hoặc các khối được thêm vào `List`.
- Gọi `transferTo` với dữ liệu không đáng tin mà không có quota; input vô hạn có thể làm đầy tệp.
- Mong `transferTo` tự đóng writer hoặc tự flush/bảo đảm ghi bền vững; nó không làm vậy.
- Bắt `IOException` rồi tiếp tục công bố file kết quả chưa đầy đủ.
- Kiểm tra `Files.size(path)` trước khi đọc rồi cho rằng dữ liệu/đường dẫn không thể thay đổi trong lúc chạy.
- Dùng character streams để sao chép byte nhị phân.
- Dùng `readLine()` để sao chép chính xác văn bản mà quên mất ký tự kết thúc dòng đã bị bỏ.
- Không xử lý giới hạn thời gian khi Reader đang chờ đầu vào.
- Thực thi giới hạn theo `char` rồi nhầm đó là giới hạn Unicode code point hoặc grapheme cluster.

**Bài tập tự kiểm tra:**

1. Chuyển chuỗi rỗng và giải thích vì sao `transferred == 0`.
2. Chuyển `A😀B\n` bằng UTF-8; giải thích vì sao kết quả 5 units nhưng 7 bytes.
3. Dùng vòng lặp có ngưỡng 4: input `abcd` thành công; input `abcde` báo lỗi; giải thích writer có thể chứa gì sau lỗi.
4. Thử sử dụng `StringWriter` với input ngày càng lớn (trong giới hạn môi trường), so sánh với writer ra tệp.
5. Giả lập `IOException` từ writer và xác minh lỗi không bị nuốt, tài nguyên được đóng.
6. Thiết kế cách ghi tệp đích tạm, chỉ thay thế tệp thật khi toàn bộ quá trình đọc/ghi/kiểm tra thành công.
7. Với một reader đọc từ nguồn có thể không phát EOF, giải thích nơi phải đặt timeout, deadline hoặc cancellation.

## 9. Ghi nhớ để áp dụng đúng

`Reader.transferTo` là lựa chọn gọn cho việc **chuyển hết luồng ký tự đến EOF**. Nó thường hỗ trợ sao chép theo từng khối, nhưng không thay cho chính sách tài nguyên của ứng dụng. Khi xử lý dữ liệu lớn, cần xem toàn bộ pipeline: loại reader và writer, charset, giới hạn char/byte, timeout, cách xử lý output dở và điều kiện công bố kết quả. Nếu cần giới hạn hoặc biến đổi nội dung, triển khai vòng lặp có chủ đích thay vì chỉ dựa vào `transferTo`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-008">Readable, Appendable và khả năng ghép API</a>

<details>
<summary>Xem chi tiết</summary>

# Readable, Appendable và khả năng ghép API trong Java

Trong Java I/O, nhiều API xử lý **ký tự** có thể ghép với nhau mà không cần phụ thuộc vào một lớp nguồn hoặc đích cụ thể. Hai giao diện quan trọng cho cách thiết kế này là `java.lang.Readable` (nguồn cung cấp ký tự) và `java.lang.Appendable` (đích nhận thêm ký tự). Chúng là những **hợp đồng tối thiểu**: một bên biết đọc vào `CharBuffer`, bên kia biết nối nội dung ký tự.

Điểm đáng học không chỉ là thuộc tên phương thức, mà là cách viết thành phần dùng chung cho `StringReader`, `BufferedReader`, `CharBuffer`, `StringBuilder`, `Writer`, v.v. mà không phải viết lại thuật toán theo từng loại.

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt `Readable`, `Reader`, `Appendable`, `Writer` và `CharSequence` theo đúng vai trò.
- Giải thích hợp đồng `read(CharBuffer)`, `append(...)`, giá trị trả về và khả năng ném `IOException`.
- Ghép một nguồn ký tự với một đích ký tự nhờ khai báo tham số theo interface.
- Sử dụng `CharBuffer` đúng các bước **ghi → flip → tiêu thụ → clear**.
- Biết khi nào cần thêm `Closeable`, `Flushable`, charset, giới hạn dữ liệu hoặc cơ chế chờ I/O.
- Nhận diện các lỗi thường gặp: đọc một lần rồi tưởng đủ, quên `flip()`, quên EOF, lạm dụng `toString()`, hoặc đóng tài nguyên không thuộc sở hữu.

## 2. Hai interface tối thiểu

### `Readable`: một nguồn ký tự

Hợp đồng của `java.lang.Readable` có một phương thức chính:

~~~java
int read(java.nio.CharBuffer cb) throws java.io.IOException;
~~~

Phương thức đọc ký tự vào `cb` từ **vị trí hiện tại** của `CharBuffer`; vị trí sẽ tiến lên theo số ký tự đã thêm. Giá trị trả về:

- Số ký tự thực sự đã đưa vào buffer (có thể **ít hơn** `cb.remaining()` ban đầu).
- `-1` nếu đã đến **end of stream (EOF)**.
- `0` trong một số trường hợp không đọc ký tự nào, ví dụ buffer không còn chỗ hoặc nguồn hiện chưa tạo ra tiến độ. Cách phản ứng với `0` phụ thuộc nguồn; không nên quay vòng CPU vô hạn.

`Readable` không định nghĩa `close()`, `mark()`, `reset()`, `ready()` hay cách đổi từ byte sang ký tự. Những khả năng đó, nếu có, nằm ở các kiểu hoặc tầng API khác.

Ví dụ triển khai: `java.io.Reader` là lớp trừu tượng triển khai `Readable`; `StringReader` và `BufferedReader` là các loại `Reader` cụ thể. `java.nio.CharBuffer` cũng triển khai `Readable`, nên một vùng ký tự có thể được dùng làm nguồn đọc cho API cần interface này.

### `Appendable`: một đích nhận thêm ký tự

`java.lang.Appendable` cung cấp ba overload:

~~~java
Appendable append(CharSequence csq) throws java.io.IOException;
Appendable append(CharSequence csq, int start, int end)
        throws java.io.IOException;
Appendable append(char c) throws java.io.IOException;
~~~

Các thao tác bổ sung ký tự vào đích và trả về `Appendable` để có thể nối lời gọi:

~~~java
sink.append("Hello").append(' ').append("Java");
~~~

`CharSequence` là mô tả một chuỗi **ký tự**, có thể được thể hiện bằng `String`, `StringBuilder`, `CharBuffer` hoặc kiểu triển khai khác. Trong `append(csq, start, end)`, `start` **bao gồm**, `end` **loại trừ**, và chỉ số tính theo đơn vị **UTF-16 char**, không phải số Unicode code point.

Theo hợp đồng `Appendable`, truyền `null` làm `CharSequence` sẽ dẫn đến việc thêm chuỗi bốn ký tự `"null"`. Điều đó không thay thế cho kiểm tra dữ liệu đầu vào khi `null` phải mang ý nghĩa khác.

Ví dụ triển khai: `StringBuilder`, `StringBuffer`, `java.io.Writer` và `java.nio.CharBuffer`. `Appendable` không hứa dữ liệu đã xuống đĩa, không có `flush()` hoặc `close()` trong chính interface và không bảo đảm đồng bộ đa luồng.

### Bảng đối chiếu

| Kiểu | Vai trò | Năng lực ngoài hợp đồng tối thiểu |
| --- | --- | --- |
| `Readable` | Đọc ký tự vào `CharBuffer` | Chỉ bảo đảm `read(CharBuffer)` |
| `Reader` | Nguồn ký tự thuộc Java I/O | `read(char[])`, `close()` và nhiều tiện ích khác; là một `Readable` |
| `Appendable` | Nhận ký tự qua `append` | Không có `flush`/`close` bắt buộc |
| `Writer` | Đích ký tự thuộc Java I/O | `write(...)`, `flush()`, `close()`; là một `Appendable` |
| `CharSequence` | Biểu diễn dãy ký tự để đọc theo chỉ số | `length()`, `charAt()`, `subSequence()`; không đồng nghĩa một nguồn I/O |
| `CharBuffer` | Buffer ký tự NIO có `position/limit` | Có thể đóng vai trò `Readable`, `Appendable` và `CharSequence` |

**Không đánh đồng** `Readable` với `Reader`: một đối tượng có thể là `Readable` mà không phải `Reader`; tương tự, `Appendable` không nhất thiết là `Writer`. Nếu hàm chỉ đọc/append, khai báo tham số tối thiểu thường làm hàm dùng lại được rộng hơn.

## 3. Ghép API: đầu vào và đầu ra có thể thay thế

Ta có thể viết một thuật toán chỉ biết:

~~~text
Readable (nguồn ký tự)
        |
        | read(CharBuffer)
        v
CharBuffer [ghi vào các ô trống]
        |
        | flip() => chỉ lộ các ký tự vừa đọc
        v
Appendable (đích nhận thêm ký tự)
~~~

Ví dụ cùng một logic có thể đọc từ `StringReader` vào `StringBuilder` khi kiểm thử, rồi đọc từ `BufferedReader` vào `BufferedWriter` khi xử lý file. Thuật toán trung gian không cần biết nguồn ở RAM hay trên đĩa.

Tuy nhiên, đây là **ghép hợp đồng ký tự**. Với byte thuần, dùng `InputStream`/`OutputStream` hoặc byte channel. Nếu dữ liệu đi qua file/socket ở dạng byte mà cần xử lý văn bản, phải chọn charset để chuyển đổi tại biên thông qua `InputStreamReader` và `OutputStreamWriter`.

## 4. Ví dụ chạy được: sao chép ký tự từ `Readable` sang `Appendable`

Tạo file **`ReadableAppendableDemo.java`**:

~~~java
import java.io.IOException;
import java.io.StringReader;
import java.nio.CharBuffer;

public class ReadableAppendableDemo {
    public static long copy(Readable source, Appendable destination)
            throws IOException {
        CharBuffer buffer = CharBuffer.allocate(4);
        long total = 0;

        while (true) {
            int count = source.read(buffer);

            if (count == -1) {
                break;
            }
            if (count == 0) {
                // Chính sách của helper minh họa này: yêu cầu nguồn có tiến độ.
                // Nguồn non-blocking cần cơ chế chờ khả dụng riêng.
                throw new IOException("Source made no progress");
            }

            buffer.flip();
            destination.append(buffer);
            total += count;
            buffer.clear();
        }

        return total;
    }

    public static void main(String[] args) throws IOException {
        StringBuilder result = new StringBuilder();

        try (StringReader source = new StringReader("Hello, Java!")) {
            long copied = copy(source, result);
            System.out.println("characters=" + copied);
        }

        System.out.println("text=" + result);
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac ReadableAppendableDemo.java
java ReadableAppendableDemo
~~~

**Stdout chính xác:**

~~~text
characters=12
text=Hello, Java!
~~~

**Vì sao chạy đúng:**

1. `StringReader` là một `Reader`, do đó dùng được làm `Readable`. `StringBuilder` triển khai `Appendable`.
2. `CharBuffer.allocate(4)` có sức chứa 4 ký tự; chuỗi 12 ký tự được xử lý nhiều lượt. Mỗi `read` trả về **số ký tự thực nhận**, không phải lệnh yêu cầu đọc đúng bốn.
3. `read` ghi từ `buffer.position()` và tăng `position`; `flip()` chuyển sang trạng thái có thể đọc, đặt `limit` bằng vị trí đã ghi và đưa `position` về 0.
4. `destination.append(buffer)` nhận `CharBuffer` dưới dạng `CharSequence`; tại thời điểm này chuỗi nhìn thấy chỉ là những ký tự vừa đọc.
5. `clear()` đặt lại `position=0` và `limit=capacity` để vòng sau ghi tiếp. **`clear()` không xóa vật lý các ký tự cũ**; chúng chỉ không còn thuộc phần dữ liệu hợp lệ của lượt kế tiếp.
6. Khi `read` trả `-1`, helper kết thúc và trả tổng số **đơn vị `char`** đã chép. Con số này không nhất thiết bằng số byte UTF-8 hoặc số Unicode code point.

Đoạn này chủ động ném `IOException` khi `read` trả `0` để tránh một vòng lặp không tiến triển. **Đây là chính sách giới hạn của helper minh họa**, phù hợp nguồn blocking có tiến độ như `StringReader`. Một `Readable` tổng quát có thể trả `0`; với nguồn non-blocking phải tích hợp cơ chế báo sẵn sàng, huỷ/timeout hoặc quy tắc thử lại phù hợp, không mặc định đó là EOF và cũng không spin vô hạn.

### Nếu quên `flip()` thì sao?

~~~java
CharBuffer buffer = CharBuffer.allocate(4);
int count = source.read(buffer);

// Sai: buffer vẫn ở trạng thái chuẩn bị ghi; position đã tăng,
// remaining() có thể bằng 0 hoặc chứa vùng chưa được ghi.
destination.append(buffer);
~~~

Nếu mảng được lấp đầy, `position == limit` khiến phần `CharSequence` đang nhìn thấy rỗng. Nếu chỉ đọc một phần, đoạn còn “remaining” có thể chính là các ô **chưa được ghi**. Cần `flip()` trước khi chuyển dữ liệu đã đọc sang đích. Không tự chuyển `CharBuffer` thành `String` trước khi biết rõ `position` và `limit`.

Một thiết kế khác có thể dùng mảng `char[]` với `Reader.read(char[])` và `Writer.write(char[], 0, count)`, nhưng lúc đó hàm yêu cầu **`Reader/Writer`** cụ thể, kém tổng quát hơn helper `Readable/Appendable` ở trên.

## 5. Ví dụ ghép thư viện chuẩn: `Scanner` và `Formatter`

`java.util.Scanner` nhận `Readable` làm nguồn phân tích token; `java.util.Formatter` có thể nhận `Appendable` làm đích định dạng. Điều này cho phép ta kết nối parser và formatter với các nguồn/đích khác nhau mà không bắt mỗi nơi phải là file.

Tạo file **`ScannerFormatterDemo.java`**:

~~~java
import java.io.StringReader;
import java.util.Formatter;
import java.util.Locale;
import java.util.Scanner;

public class ScannerFormatterDemo {
    public static void main(String[] args) {
        StringBuilder result = new StringBuilder();

        try (Scanner scanner = new Scanner(new StringReader("8 13"));
             Formatter formatter = new Formatter(result, Locale.ROOT)) {
            int a = scanner.nextInt();
            int b = scanner.nextInt();

            formatter.format("a=%d, b=%d, sum=%d", a, b, a + b);
        }

        System.out.println(result);
    }
}
~~~

Chạy:

~~~bash
javac ScannerFormatterDemo.java
java ScannerFormatterDemo
~~~

Stdout:

~~~text
a=8, b=13, sum=21
~~~

`Scanner` tiêu thụ nguồn triển khai `Readable`; `Formatter` ghi qua đích triển khai `Appendable`. Ở ví dụ trên đích là `StringBuilder` nên không có `IOException` do I/O đĩa. Nếu dùng `Writer` làm đích cho `Formatter`, lưu ý `Formatter` có thể ghi nhận lỗi I/O và cho truy vấn qua `formatter.ioException()`; việc gọi `format` không thay thế hoàn toàn kiểm tra lỗi đầu ra.

**Vòng đời tài nguyên:** `Scanner.close()` đóng nguồn nếu nguồn đó triển khai `Closeable`; `Formatter.close()` đóng đích nếu đích triển khai `Closeable`. Trong ví dụ, `StringBuilder` không cần đóng. Khi được **truyền** nguồn/đích từ bên ngoài vào một utility, không tự ý đóng chúng nếu hợp đồng sở hữu tài nguyên không giao quyền cho utility.

## 6. Thiết kế hàm có thể ghép từ các thành phần nhỏ

Giả sử nhiệm vụ là viết tiêu đề và tên vào nhiều loại đích. Nên khai báo kiểu tham số theo năng lực thực sự cần dùng:

~~~java
import java.io.IOException;

public final class GreetingWriter {
    private GreetingWriter() {}

    public static void writeGreeting(
            Appendable destination, CharSequence name) throws IOException {
        destination.append("Xin chào, ")
                   .append(name)
                   .append('!');
    }
}
~~~

Lớp này có thể biên dịch nhưng **không có `main`**; được gọi từ chương trình khác. Nó dùng được với `StringBuilder`, `StringBuffer` hoặc `Writer`. Kiểu trả về của các lệnh `append` được phép **covariant**: lớp triển khai như `StringBuilder` có thể trả về `StringBuilder` thay vì `Appendable` mà vẫn tuân thủ interface.

Tương tự, khai báo `Readable` cho API chỉ cần nhận luồng ký tự:

~~~java
static int firstCharacter(Readable source) throws IOException {
    java.nio.CharBuffer one = java.nio.CharBuffer.allocate(1);

    while (true) {
        int read = source.read(one);
        if (read == -1) {
            return -1; // Hết dữ liệu, không có ký tự đầu tiên.
        }
        if (read > 0) {
            one.flip();
            return one.get();
        }
        throw new IOException("Source made no progress");
    }
}
~~~

Mảnh mã trên cần nằm trong một lớp Java và có `import java.io.*` phù hợp. Đây cũng là chính sách **fail khi không có tiến độ**, không phải lời khuyên rằng mọi nguồn `Readable` đều phải ném lỗi khi tạm thời trả `0`.

**Nguyên tắc lựa chọn:** khai báo `Appendable` khi chỉ cần nối ký tự; dùng `Writer` khi cần `write(char[])`, `flush()` hay `close()` trong hợp đồng thao tác; dùng `Readable` khi chỉ cần `read(CharBuffer)`; dùng `Reader` khi cần `read(char[])`, `read()`, `close()` hoặc phương thức riêng của `Reader`. Đừng ép người gọi biến mọi nguồn thành `String` bằng `toString()`: một file lớn hoặc luồng vô hạn không thể xử lý hiệu quả theo cách đó.

## 7. `append`, `CharSequence` và các giới hạn dễ nhầm

### `append(csq, start, end)` không phải `substring` tự động

~~~java
StringBuilder builder = new StringBuilder();
builder.append("012345", 1, 4);
System.out.println(builder); // 123
~~~

Chỉ số bắt đầu **1** bao gồm, kết thúc **4** loại trừ. Nếu `start < 0`, `end > csq.length()` hoặc `start > end`, thao tác có thể báo `IndexOutOfBoundsException` theo hợp đồng. Chỉ số là đơn vị `char` (UTF-16): không tùy tiện cắt đôi surrogate pair nếu muốn giữ nguyên một Unicode code point.

### `append` chỉ hứa nhận thêm ký tự

- Giao diện không buộc thao tác append phải **nguyên tử**. Nếu đích I/O lỗi giữa chừng, dữ liệu có thể đã được ghi một phần.
- Giao diện không hứa thread safety. `StringBuilder` không đồng bộ; `StringBuffer` đồng bộ các thao tác riêng lẻ, nhưng điều đó không làm nhiều lệnh nối liên tiếp thành một giao dịch nguyên tử.
- Giao diện không hứa có `flush()`; nếu đích là `BufferedWriter`, bạn cần quyết định lúc nào flush/close dựa vào **chủ sở hữu đích**, không dựa vào type `Appendable`.
- Giao diện không hứa một `append` tương đương một bản tin hay một dòng. Với network/file protocol cần tự quy định khung/định dạng.

### `CharSequence` có thể thay đổi trong lúc dùng

`String` bất biến, nhưng `StringBuilder` hoặc `CharBuffer` có trạng thái/giới hạn thay đổi được. Tránh sửa nguồn khi một thao tác append khác đang đọc nội dung từ nó; hợp đồng `Appendable` không hứa tự chụp snapshot của mọi `CharSequence` ở mọi thời điểm.

## 8. Ghép với byte stream đúng charset

`Readable` và `Appendable` hoạt động trên ký tự Java (`char`/UTF-16). Để đọc/ghi dữ liệu file hoặc mạng ở dạng byte, cần tầng chuyển đổi rõ ràng:

~~~java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class TextFileBridge {
    public static void main(String[] args) throws IOException {
        Path source = Path.of("source.txt");
        Path target = Path.of("target.txt");

        try (BufferedReader reader =
                     Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer =
                     Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            ReadableAppendableDemo.copy(reader, writer);
        }
    }
}
~~~

Ví dụ này cần **`ReadableAppendableDemo.java`** từ mục 4 cùng thư mục để biên dịch; đồng thời cần `source.txt` tồn tại trước khi chạy. Biên dịch cả hai lớp:

~~~bash
javac ReadableAppendableDemo.java TextFileBridge.java
java TextFileBridge
~~~

Kết quả là **`target.txt`** nhận văn bản từ `source.txt` theo UTF-8; chương trình không in stdout. `Files.newBufferedReader/newBufferedWriter` chọn đúng charset tường minh; `try-with-resources` đóng cả nguồn và đích để flush đích khi thành công hoặc khi có ngoại lệ. Trong ứng dụng thực tế, nên xác định rõ hành vi ghi đè/tạo file mục tiêu và yêu cầu an toàn trước khi chạy.

**Giới hạn Unicode:** `Readable.read(CharBuffer)` đếm **UTF-16 code units**, không phải số byte UTF-8 hoặc số ký tự mà người dùng nhìn thấy. Một ký tự ngoài BMP có thể chiếm hai `char` (surrogate pair). Chuyển nối nguyên chuỗi `char` đúng thứ tự vào `Writer` tương thích thì bộ mã hóa có thể xử lý các cặp, nhưng tự mã hóa riêng từng chunk không đầy đủ dễ cắt đôi ký tự và làm hỏng dữ liệu. Khi cần đếm code point, dùng API code point riêng sau khi hiểu ranh giới văn bản.

## 9. Khi nào nên dùng interface nào?

| Yêu cầu | Loại đầu vào/đầu ra đề xuất | Lý do |
| --- | --- | --- |
| Chỉ cần tiêu thụ ký tự vào buffer | `Readable` | API nhận được nhiều nguồn hơn |
| Cần đọc ký tự theo mảng hoặc xử lý từng dòng | `Reader` / `BufferedReader` | Có các thao tác đọc tiện dụng |
| Chỉ cần thêm ký tự | `Appendable` | Có thể dùng `StringBuilder` hoặc `Writer` |
| Cần ghi mảng ký tự, flush hoặc đóng | `Writer` | Diễn đạt đầy đủ năng lực I/O |
| Cần định dạng dữ liệu ra nhiều loại đích | `Formatter(Appendable)` | Dựa vào hợp đồng append |
| Cần phân tích token từ nguồn khác nhau | `Scanner(Readable)` | Dựa vào hợp đồng đọc ký tự |
| Sao chép file nhị phân, ảnh, ZIP | `InputStream` / `OutputStream` hoặc byte channel | Không chuyển đổi qua `char` |
| Cần xử lý nội dung lớn | Streaming bằng buffer | Không giữ toàn bộ nội dung trong RAM |

Đừng chọn `Readable` chỉ vì tên nghe tổng quát hơn. Nếu bài toán cần biết EOF theo giao thức, cần truy cập ngẫu nhiên, xử lý số lượng byte chính xác, cần backpressure hoặc cần quản lý đóng/mở, những năng lực đó phải được thiết kế rõ thay vì giả định chúng có sẵn trong hai interface nhỏ.

## 10. Lỗi phổ biến và cách sửa

**Lỗi 1 — Một lần `read` phải đầy buffer.**  
Sai: bỏ qua số ký tự trả về; dùng cả `buffer.capacity()` làm dữ liệu hợp lệ.  
Đúng: dùng vị trí/limit sau `read`, `flip()` trước khi đưa buffer sang đích, sau đó `clear()`.

**Lỗi 2 — Xem `0` là EOF hoặc luôn quay vòng khi nhận 0.**  
Sai: `if (count == 0) break;` với mọi `Readable`, hoặc `while (count == 0) read(...)` không có cơ chế chờ.  
Đúng: `-1` là EOF; `0` cần xử lý theo đặc tính nguồn, nhất là non-blocking. Với utility chỉ hỗ trợ nguồn có tiến độ, có thể chủ động báo lỗi rõ ràng như ví dụ.

**Lỗi 3 — Dùng `append(buffer)` trước `flip()`.**  
Sai: buffer còn ở trạng thái viết, phần `CharSequence` lộ ra không phải dữ liệu vừa đọc.  
Đúng: chuyển buffer về trạng thái đọc rồi mới append.

**Lỗi 4 — Giả định `Appendable` cũng có `flush()` và `close()`.**  
Sai: ép kiểu thẳng sang `Writer` hoặc đóng tài nguyên do người gọi truyền vào.  
Đúng: chỉ dùng các phương thức của hợp đồng đã yêu cầu; thêm `Writer` hoặc khả năng riêng khi bài toán thật sự cần, và xác định ai sở hữu việc đóng.

**Lỗi 5 — Đổi byte stream thành text không xác định charset.**  
Sai: decode bằng charset mặc định ngoài ý muốn, hoặc xử lý mỗi đoạn byte như một string độc lập.  
Đúng: xác định charset, dùng `Reader`/`Writer` bọc quanh nguồn/đích byte và duy trì trạng thái decoder/encoder.

**Lỗi 6 — Cho rằng mọi `CharSequence` đều là `String` bất biến.**  
Sai: giả định dữ liệu không thay đổi khi `StringBuilder` hoặc `CharBuffer` vẫn được sửa.  
Đúng: chọn quy tắc sở hữu/mutability rõ ràng; chụp `toString()` khi thực sự cần snapshot và dữ liệu đủ nhỏ.

## 11. Câu hỏi tự kiểm tra

**1.** Một hàm chỉ cần nối văn bản vào đích bất kỳ; chọn tham số `Writer` hay `Appendable`?  
**Trả lời:** `Appendable` đủ nếu chỉ gọi `append`, giúp hàm nhận cả `StringBuilder`, `Writer` và kiểu tương thích khác.

**2.** Sau `read(CharBuffer)`, vì sao không append buffer ngay?  
**Trả lời:** Vị trí đang ở trạng thái ghi; phải `flip()` để `position=0` và `limit` chỉ đến cuối vùng vừa đọc.

**3.** `Readable` có bắt buộc `close()` không?  
**Trả lời:** Không. Một số lớp cụ thể như `Reader` hỗ trợ `close()`, nhưng bản thân `Readable` không yêu cầu.

**4.** `append(CharSequence, 2, 5)` dùng ranh giới nào?  
**Trả lời:** Lấy chỉ số 2, 3, 4; `end=5` là exclusive, tính theo UTF-16 `char`.

**5.** `Scanner` và `Formatter` liên quan gì đến hai giao diện này?  
**Trả lời:** `Scanner` có thể nhận một `Readable` làm nguồn; `Formatter` có thể ghi vào một `Appendable`. Chúng minh họa cách các API thư viện ghép với nhau theo hợp đồng tối thiểu.

**Ghi nhớ:** `Readable` định nghĩa **cách nhận ký tự từ nguồn**, `Appendable` định nghĩa **cách thêm ký tự vào đích**. `CharBuffer` nối hai thế giới qua `read → flip → append → clear`. Sức mạnh của chúng là khả năng thay thế nguồn/đích, với điều kiện bạn vẫn quản lý rõ số ký tự thực đọc, trạng thái buffer, encoding và vòng đời tài nguyên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="character-streams-009">Vì sao không dùng luồng ký tự để sao chép dữ liệu nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không dùng luồng ký tự để sao chép dữ liệu nhị phân?

## 1. Vấn đề cốt lõi: sao chép dữ liệu phải giữ nguyên từng byte

Khi sao chép một file **PNG, JPEG, PDF, ZIP, JAR, MP3, video, file nén, file cơ sở dữ liệu hoặc nội dung nhị phân từ mạng**, yêu cầu quan trọng nhất là **dãy byte đầu ra phải giống dãy byte đầu vào**, theo đúng thứ tự và đúng số lượng.

Ví dụ, nếu byte nguồn là:

~~~text
41 FF 00 0D 0A 42
~~~

Thì kết quả sao chép hợp lệ phải vẫn là **41 FF 00 0D 0A 42**. Không được thêm, bỏ hoặc đổi bất kỳ byte nào, kể cả byte **00**, byte **FF**, hay cặp byte **0D 0A**.

Một file nhị phân không nhất thiết biểu diễn văn bản theo bảng mã nào. Dữ liệu của nó có thể gồm mọi giá trị từ **00** đến **FF**, trong đó nhiều tổ hợp **không phải chuỗi ký tự hợp lệ** của UTF-8 hoặc charset khác.

Trong Java, **InputStream/OutputStream** xử lý các byte gần với biểu diễn gốc, trong khi **Reader/Writer** xử lý ký tự. Dùng Reader/Writer để sao chép file nhị phân sẽ thêm bước diễn giải dữ liệu thành văn bản. Bước này **không có cơ sở** khi nội dung vốn không phải văn bản và có thể làm mất tính toàn vẹn dữ liệu.

> Quy tắc: **sao chép byte → dùng luồng byte**. **Đọc, xử lý hoặc xuất văn bản theo charset → dùng luồng ký tự**.

## 2. Khác nhau về đơn vị dữ liệu và công việc thực hiện

| Tiêu chí | Luồng byte | Luồng ký tự |
| --- | --- | --- |
| API Java tiêu biểu | **InputStream**, **OutputStream** | **Reader**, **Writer** |
| Đơn vị xử lý | **byte** (8 bit) | Đơn vị ký tự Java **char** (UTF-16 code unit, 16 bit) |
| Diễn giải charset? | Không cần | Có, khi chuyển giữa byte và ký tự |
| Ví dụ | **FileInputStream**, **FileOutputStream**, **BufferedInputStream**, **BufferedOutputStream** | **FileReader**, **FileWriter**, **BufferedReader**, **BufferedWriter** |
| Phù hợp | Ảnh, ZIP/JAR, PDF, âm thanh, dữ liệu nhị phân, mọi file cần sao chép chính xác | Nội dung văn bản cần đọc theo ký tự hoặc dòng |
| Tiêu chí chính | Bảo toàn dãy byte gốc | Bảo toàn ý nghĩa ký tự theo một mã hóa đã chọn |

**char** trong Java biểu diễn một đơn vị mã UTF-16, không phải lúc nào cũng trùng với một ký tự Unicode hoàn chỉnh. Một số ký tự Unicode ngoài Basic Multilingual Plane cần **hai char** (surrogate pair). Sự khác nhau này càng cho thấy **một char không tương ứng cố định với một byte**.

Một ứng dụng dùng **BufferedInputStream** vẫn đang làm việc theo byte, vì buffering chỉ là tích lũy byte nhằm giảm chi phí I/O; bản thân nó **không biến byte thành ký tự**. Ngược lại, **BufferedReader** là luồng ký tự, không phù hợp với sao chép nhị phân dù có chữ “Buffered” giống nhau.

## 3. Tại sao Reader và Writer có thể thay đổi byte?

### 3.1. Giải mã: byte → ký tự

Khi dữ liệu từ một **InputStream** đi qua **InputStreamReader**, các byte được **giải mã (decode)** theo charset:

~~~text
File nhị phân
  │
  ▼
byte[] gốc
  │
  ▼
InputStreamReader (decode theo charset)
  │
  ▼
ký tự Java (char)
~~~

Ví dụ, trong UTF-8:

- Byte **41** có thể biểu diễn ký tự **A**.
- Dãy **C3 A9** biểu diễn ký tự **é**.
- Byte **FF** đứng một mình **không hợp lệ** trong UTF-8.

Với cấu hình thông dụng dùng **InputStreamReader(..., StandardCharsets.UTF_8)**, chuỗi byte sai mã hóa có thể bị thay bằng ký tự thay thế **U+FFFD (�)**. Khi thông tin byte gốc đã bị thay thế, **không thể suy lại chắc chắn byte FF ban đầu** từ ký tự thay thế đó.

Có thể cấu hình **CharsetDecoder** để **REPORT** và ném lỗi thay vì thay thế ký tự không hợp lệ, nhưng điều đó vẫn không làm Reader trở thành công cụ thích hợp cho sao chép nhị phân: nhiều dãy byte nhị phân hợp lệ vẫn **không phải văn bản UTF-8 hợp lệ**.

### 3.2. Mã hóa: ký tự → byte

Khi ký tự đi qua **OutputStreamWriter**, chúng được **mã hóa (encode)** thành byte theo charset của đầu ra:

~~~text
Reader                   Writer
byte[] nguồn              ký tự đã giải mã              byte[] đích
     └── decode ──────────►     char[]      ── encode ───►     │
     (charset đầu vào)                   (charset đầu ra)
~~~

Nếu Reader gặp byte **FF** và đổi thành U+FFFD, Writer mã hóa U+FFFD trong UTF-8 thành **EF BF BD**. **Một byte FF** đã biến thành **ba byte EF BF BD**, làm lệch dữ liệu ngay cả khi dùng UTF-8 ở **cả đầu vào và đầu ra**.

Nếu mã hóa đầu vào và đầu ra khác nhau, một văn bản có thể được chuyển đổi charset theo ý định. Nhưng với dữ liệu nhị phân, đó là hành động làm biến đổi byte, **không phải sao chép**.

### 3.3. Các thao tác theo dòng có thể làm mất byte kết thúc dòng

**BufferedReader.readLine()** đọc một dòng văn bản và **không giữ dấu kết thúc dòng** trong String trả về. Nó coi **LF (\n)**, **CR (\r)** và **CRLF (\r\n)** là dấu hết dòng.

Nếu sau đó gọi **BufferedWriter.newLine()** hoặc **PrintWriter.println()**, chương trình ghi lại dấu xuống dòng theo quy tắc mới, thường phụ thuộc nền tảng. Dãy **0D 0A** có thể chuyển thành **0A**, hoặc các ranh giới dòng khác bị thay đổi. Điều này cũng xảy ra trên nội dung ASCII thuần, không cần có byte UTF-8 sai.

Ngoài ra, **readLine()** không tự giữ thông tin ranh giới cụ thể của từng dòng. Khi file chứa cả CRLF và LF, chương trình không còn biết mỗi dòng trước đó dùng kiểu xuống dòng nào chỉ từ String nhận được.

**Quan trọng:** bản thân Reader/Writer không tự động đổi mọi byte CRLF khi chỉ dùng **read(char[])** và **write(char[])**. Sự biến đổi xuống dòng xảy ra khi ứng dụng dùng các API mức dòng hoặc tự viết lại dấu xuống dòng; cần phân biệt lỗi do *decode/encode* với lỗi do *xử lý nội dung dạng text*.

## 4. Ví dụ Java 17+: thấy tận mắt byte bị biến đổi

Đoạn code sau tạo dữ liệu byte có giá trị **FF** không hợp lệ trong UTF-8, rồi đối chiếu hai cách sao chép:

1. **Sai cho nhị phân:** decode bằng **InputStreamReader**, sau đó encode bằng **OutputStreamWriter**.
2. **Đúng:** sao chép trực tiếp từ **InputStream** sang **OutputStream** bằng **transferTo**.
3. Thử thêm **readLine()** để thấy CRLF/CR có thể mất khi ghi lại từng dòng.

Lưu thành **BinaryCopyDemo.java**:

~~~java
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class BinaryCopyDemo {
    public static void main(String[] args) throws IOException {
        byte[] original = {
                0x41, (byte) 0xFF, 0x00, 0x0D, 0x0A, 0x42
        };

        // SAI đối với dữ liệu nhị phân: chuyển byte -> char -> byte.
        ByteArrayOutputStream textTarget = new ByteArrayOutputStream();
        try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(original),
                    StandardCharsets.UTF_8);
             Writer writer = new OutputStreamWriter(
                    textTarget, StandardCharsets.UTF_8)) {
            char[] chars = new char[16];
            int count;
            while ((count = reader.read(chars)) != -1) {
                writer.write(chars, 0, count);
            }
        }
        byte[] viaCharacters = textTarget.toByteArray();

        // ĐÚNG: byte -> byte, không qua bảng mã.
        ByteArrayOutputStream byteTarget = new ByteArrayOutputStream();
        try (InputStream input = new ByteArrayInputStream(original)) {
            input.transferTo(byteTarget);
        }
        byte[] viaBytes = byteTarget.toByteArray();

        System.out.println("original=" +
                HexFormat.of().formatHex(original));
        System.out.println("characters=" +
                HexFormat.of().formatHex(viaCharacters));
        System.out.println("bytes=" +
                HexFormat.of().formatHex(viaBytes));
        System.out.println("characters equal=" +
                Arrays.equals(original, viaCharacters));
        System.out.println("bytes equal=" +
                Arrays.equals(original, viaBytes));

        // CRLF và CR sẽ bị mất khi readLine() bỏ ký tự kết thúc.
        byte[] newlines =
                "A\r\nB\rC\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream lineTarget = new ByteArrayOutputStream();
        try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new ByteArrayInputStream(newlines),
                            StandardCharsets.UTF_8));
             Writer writer = new OutputStreamWriter(
                    lineTarget, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write('\n'); // Cố ý dùng LF để minh họa biến đổi.
            }
        }
        System.out.println("lines original=" +
                HexFormat.of().formatHex(newlines));
        System.out.println("lines rewritten=" +
                HexFormat.of().formatHex(lineTarget.toByteArray()));
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac BinaryCopyDemo.java
java BinaryCopyDemo
~~~

Stdout dự kiến, cố định vì charset và dấu kết thúc dòng đã chỉ rõ trong code:

~~~text
original=41ff000d0a42
characters=41efbfbd000d0a42
bytes=41ff000d0a42
characters equal=false
bytes equal=true
lines original=410d0a420d430a
lines rewritten=410a420a430a
~~~

Giải thích từng phần:

- **41** tương ứng **A** và được giữ nguyên.
- **FF** là byte UTF-8 không hợp lệ, bị thay bằng ký tự **�**; UTF-8 mã hóa ký tự ấy thành **EF BF BD**.
- **00**, **0D**, **0A** và **42** không tự mất đi chỉ vì decode/encode bằng UTF-8 trong ví dụ đầu. Phần này cho thấy **không phải mọi byte đều nhất thiết bị thay đổi**; chỉ một byte bị đổi đã đủ làm file không còn giống bản gốc.
- **transferTo** chuyển nguyên dãy byte, vì vậy **Arrays.equals** trả về **true**.
- Chuỗi **A\r\nB\rC\n** được đọc thành ba dòng “A”, “B”, “C”. Mỗi dòng được viết lại với **LF (\n)**, nên dữ liệu gốc **0D 0A** và **0D** bị thay thành **0A**.

Ví dụ dùng **ByteArrayInputStream/ByteArrayOutputStream** để tái hiện kết quả mà không cần tạo file thực tế. Nguyên tắc tương tự áp dụng cho **FileInputStream/FileOutputStream** khi làm việc với file trên ổ đĩa.

## 5. Cách sao chép dữ liệu nhị phân đúng trong Java

### 5.1. Cách ngắn nhất để sao chép file: Files.copy

Với hai đường dẫn trên ổ đĩa, API **java.nio.file.Files.copy** là lựa chọn trực tiếp:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyBinaryFile {
    public static void main(String[] args) throws IOException {
        Path source = Path.of("source.bin");
        Path destination = Path.of("copy.bin");

        // Chỉ sao chép nếu đích chưa tồn tại.
        Files.copy(source, destination);

        // -1 nghĩa là nội dung hai file giống nhau từng byte.
        System.out.println("identical=" +
                (Files.mismatch(source, destination) == -1L));
    }
}
~~~

Lưu thành **CopyBinaryFile.java**, đặt một file nguồn tên **source.bin** cùng thư mục chạy, sau đó:

~~~bash
javac CopyBinaryFile.java
java CopyBinaryFile
~~~

Khi sao chép thành công và không có tiến trình khác làm thay đổi file trong lúc kiểm tra, kết quả:

~~~text
identical=true
~~~

**Files.copy(Path, Path)** mặc định sẽ báo lỗi nếu file đích đã tồn tại; ví dụ không chủ động ghi đè. Nếu nghiệp vụ muốn thay file đích, có thể dùng tùy chọn **StandardCopyOption.REPLACE_EXISTING** nhưng phải cân nhắc việc mất dữ liệu đích cũ. Sao chép nội dung byte giống nhau không có nghĩa là mọi metadata file (chẳng hạn owner, timestamp, quyền) đều được giữ hoàn toàn; đó là một yêu cầu khác.

### 5.2. Sao chép giữa hai luồng với InputStream.transferTo

Khi nguồn/đích không nhất thiết là file, chẳng hạn stream từ kết nối, blob hoặc tài nguyên:

~~~java
try (InputStream in = Files.newInputStream(source);
     OutputStream out = Files.newOutputStream(destination)) {
    long copiedBytes = in.transferTo(out);
}
~~~

Đây là **mảnh code minh họa**, giả định đã có biến **source**, **destination** kiểu **Path** và import các lớp cần thiết. **transferTo(OutputStream)** đọc đến EOF và ghi các byte sang OutputStream, không cần chuyển qua Reader/Writer và cũng không yêu cầu mã ứng dụng giữ toàn bộ dữ liệu trong một mảng lớn. Giá trị trả về là tổng số byte đã chuyển. Cả InputStream và OutputStream vẫn cần được đóng khi đã dùng xong; ví dụ dùng **try-with-resources**.

### 5.3. Sao chép theo buffer khi cần tự kiểm soát

Mẫu sao chép từng khối thường dùng **InputStream.read(byte[])** rồi **OutputStream.write(byte[], off, len)**:

~~~java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class BufferedBinaryCopy {
    public static void main(String[] args) throws IOException {
        Path source = Path.of("source.bin");
        Path destination = Path.of("copy.bin");

        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }
    }
}
~~~

Lưu thành **BufferedBinaryCopy.java** rồi biên dịch với **javac BufferedBinaryCopy.java** và chạy **java BufferedBinaryCopy** khi có file **source.bin** và đích **copy.bin** chưa tồn tại. **count** là số byte đọc thật trong mỗi lần, có thể ít hơn **buffer.length**. Nếu viết nhầm **out.write(buffer)** thay vì **out.write(buffer, 0, count)**, lần đọc cuối ngắn hơn buffer có thể ghi thêm byte thừa từ vùng chưa được cập nhật.

Mẫu buffer phù hợp khi muốn cập nhật tiến độ, tính hash khi đang đọc, giới hạn tổng byte, hoặc xử lý từng khối trước khi viết. Đối với nguồn dữ liệu không đáng tin cậy, ngoài tính toàn vẹn còn cần giới hạn dung lượng, timeout và cách xử lý khi sao chép gặp lỗi giữa chừng.

## 6. Những trường hợp Reader/Writer thực sự phù hợp

Reader/Writer **không sai**: chúng được thiết kế để xử lý **văn bản**. Nếu nhiệm vụ là **đọc một file UTF-8, tìm/thay chuỗi, chuẩn hóa dòng hoặc chuyển từ một charset sang charset khác**, luồng ký tự là công cụ đúng.

Ví dụ nghiệp vụ muốn đổi toàn bộ dấu xuống dòng sang LF hoặc bỏ một số dòng khỏi file cấu hình: đọc từng dòng bằng **BufferedReader**, xử lý **String**, rồi ghi lại bằng **BufferedWriter** là hợp lý. Đầu ra lúc này được chủ động tạo thành một **văn bản mới**; không có yêu cầu giữ byte gốc.

Nếu muốn **sao chép nguyên văn byte của một file text**, chẳng hạn để lưu bản backup có chữ ký số hoặc hash đã biết, vẫn nên dùng **luồng byte**. File mang đuôi **.txt** cũng có thể có BOM, charset cũ, CRLF hoặc các byte đặc biệt cần bảo toàn. **Phần mở rộng .txt không tự cho phép ứng dụng thay đổi byte**.

| Yêu cầu | Nên chọn | Lý do |
| --- | --- | --- |
| Clone **photo.png**, **document.pdf**, **archive.zip**, **app.jar** | **Files.copy** hoặc InputStream/OutputStream | Giữ nguyên nội dung byte |
| Lưu nguyên payload HTTP nhận được để so hash | **InputStream/OutputStream** | Tránh decode/encode, giữ nội dung gốc |
| Đọc file cấu hình UTF-8 để phân tích từng dòng | **BufferedReader** với charset cụ thể | Đối tượng cần xử lý là text |
| Chuyển một tài liệu từ UTF-8 sang UTF-16 | **Reader + Writer** với charset đầu vào/đầu ra tương ứng | Chủ ý thay biểu diễn byte của văn bản |
| Sao lưu file text **giữ nguyên từng byte** | **Files.copy** | Hash và byte gốc cần giữ |
| Xuất dữ liệu CSV từ các String trong chương trình | **Writer** với charset rõ ràng | Sinh văn bản mới có cấu trúc |
| Đọc nhị phân theo trường (số nguyên, byte array) | **DataInputStream** hoặc ByteBuffer theo format | Diễn giải nhị phân theo giao thức đã định, không phải charset |

## 7. Có ngoại lệ nào mà sao chép qua ký tự vẫn giữ nguyên byte?

Có những trường hợp có thể **tình cờ hoặc có điều kiện** cho ra byte giống nhau, chẳng hạn các byte đầu vào đều hợp lệ trong cùng charset, không có thao tác sửa chuỗi và được mã hóa lại theo cách tương thích. Có charset ánh xạ đơn trị cho mọi giá trị byte, nên một lượt chuyển đổi tương ứng có thể khôi phục byte ban đầu.

Tuy nhiên, **đó không phải hợp đồng chung của Reader/Writer** và không có lý do dựa vào điều đó để sao chép file nhị phân. Với UTF-8, byte **FF** trong ví dụ trên đã cho thấy một phản ví dụ. Còn với **readLine/println**, thậm chí dữ liệu ký tự hoàn toàn hợp lệ cũng có thể biến đổi dấu xuống dòng.

Điểm cần phân biệt:

- **Đúng với một bộ dữ liệu thử nghiệm** không có nghĩa đúng với mọi file.
- **Charset giống nhau ở hai đầu** không đảm bảo không mất thông tin khi dữ liệu đầu vào có chuỗi byte sai charset.
- **Chọn ISO-8859-1 hoặc một charset khác** để ép byte thành ký tự vẫn tạo thêm thao tác không cần thiết; chỉ có thể chấp nhận nếu chương trình thực sự làm **chuyển đổi text** và có hợp đồng dữ liệu rõ ràng.
- **Giữ được chữ hiển thị** không đồng nghĩa với giữ được byte; một văn bản có thể có nội dung người dùng nhìn thấy giống nhau nhưng dùng encoding, BOM hoặc dấu xuống dòng khác.

## 8. Kiểm tra tính toàn vẹn sau khi sao chép

Với dữ liệu cần bảo đảm tính chính xác, kiểm tra kết quả theo **byte**, không dựa vào việc mở file và thấy hiển thị bình thường.

**Files.mismatch(source, copy)** (Java 12+) trả về **-1L** nếu không tìm thấy vị trí byte khác nhau; nếu khác, kết quả chỉ ra vị trí byte đầu tiên khác (hoặc điểm mà một file kết thúc trong khi file kia còn byte). Nó hữu ích khi có hai file nội bộ và muốn so byte chính xác.

Hash như **SHA-256** là cách thực tế để đối chiếu nội dung giữa hai hệ thống hoặc thời điểm khác nhau; hash giống nhau cho bằng chứng rất mạnh rằng nội dung tương đương, nhưng về mặt toán học vẫn là phép kiểm tra xác suất va chạm cực nhỏ, không phải thao tác so từng byte tuyệt đối. Chọn SHA-256 hoặc thuật toán hash mạnh được chấp nhận trong hệ thống thay cho chỉ so kích thước file.

Chỉ so sánh **Files.size(...)** là chưa đủ: hai file có thể cùng kích thước nhưng khác nội dung. Đồng thời, bản sao sẽ **không nhất thiết hoàn chỉnh** nếu đọc hoặc ghi gặp IOException giữa chừng; ứng dụng cần chính sách xử lý file đích dở dang (xóa hoặc ghi vào file tạm rồi thay thế nếu phù hợp).

## 9. Lỗi phổ biến và cách tránh

1. **Sao chép ảnh/PDF bằng FileReader và FileWriter.** Chuyển đổi charset có thể thay byte. Hãy dùng **Files.copy**, **InputStream/OutputStream**, hoặc API NIO byte-based.
2. **Cho rằng đặt cùng UTF-8 ở cả Reader và Writer thì an toàn.** Dữ liệu nhị phân có byte không hợp lệ với UTF-8; decoder có thể thay thế hoặc báo lỗi, làm quá trình khác sao chép.
3. **Dùng BufferedReader.readLine() để lấy nhị phân thành các dòng.** Đây là API dành cho text, bỏ mất dấu kết thúc dòng, không phải khung chia dữ liệu nhị phân.
4. **Dùng println() khi mong giữ nguyên byte.** println tạo định dạng văn bản và có thể thêm dấu xuống dòng theo nền tảng; ghi byte bằng **OutputStream.write**.
5. **Dùng String làm container của binary.** Tạo String từ byte rồi lấy byte ngược trở lại sẽ đi qua charset; dùng **byte[]** hoặc **ByteBuffer**.
6. **Đọc buffer rồi ghi cả buffer bất kể số byte thực nhận.** Phải dùng **out.write(buffer, 0, count)** với **count** từ **in.read(buffer)**.
7. **Cho rằng mọi byte 0 đều kết thúc dữ liệu.** Trong Java, **0x00** là dữ liệu nhị phân hợp lệ; **InputStream.read(...)** báo EOF bằng **-1**, không phải 0.
8. **Không kiểm tra file sao chép.** Nếu tính chính xác quan trọng, dùng **Files.mismatch** hoặc hash để xác nhận.
9. **Nghĩ buffering biến nhị phân thành văn bản.** **BufferedInputStream** an toàn cho byte; **BufferedReader** có chức năng khác hẳn.
10. **Không xử lý file đích đã tồn tại hoặc lỗi sao chép giữa chừng.** Chọn rõ chính sách overwrite, đóng tài nguyên, và xác nhận file đích đầy đủ khi nghiệp vụ cần.

## 10. Câu hỏi tự kiểm tra

**Câu 1:** Một file ZIP gồm các byte tùy ý có thể được copy an toàn bằng **BufferedReader + BufferedWriter** nếu dùng cùng UTF-8 không?  
**Đáp:** Không có đảm bảo. Dữ liệu ZIP không có nghĩa là chuỗi UTF-8 hợp lệ; decode/encode có thể thay các byte sai charset. Dùng luồng byte.

**Câu 2:** Tại sao byte FF thành EF BF BD trong ví dụ?  
**Đáp:** **FF** không hợp lệ khi đứng riêng trong UTF-8, decoder thông dụng thay bằng ký tự **U+FFFD**, rồi UTF-8 encoder tạo **EF BF BD**.

**Câu 3:** **BufferedReader.readLine()** có giữ dãy **0D 0A** không?  
**Đáp:** Không. Nó trả về nội dung dòng không chứa dấu kết thúc. Nếu ghi lại bằng **newLine()**, dấu xuống dòng được tạo mới.

**Câu 4:** File text có cần dùng Reader để backup không?  
**Đáp:** Không. Nếu cần sao chép từng byte y hệt, dùng **Files.copy** dù file là text. Nếu cần chỉnh sửa nội dung ký tự, Reader/Writer phù hợp.

**Câu 5:** Vì sao phải dùng **out.write(buffer, 0, count)**?  
**Đáp:** **read(buffer)** có thể đọc được ít hơn dung lượng buffer, nhất là lần cuối. **count** chỉ đúng lượng byte vừa nhận; ghi cả buffer có thể chèn byte dư.

## 11. Tổng kết

Luồng ký tự **Reader/Writer** phục vụ văn bản: nó **giải mã byte thành ký tự**, cho phép xử lý nội dung ở mức ký tự, sau đó **mã hóa lại thành byte**. Vì dữ liệu nhị phân **không có nghĩa vụ tuân theo charset**, việc giải mã có thể thay thế byte không hợp lệ; xử lý dòng còn có thể thay dấu kết thúc. Chỉ cần thay **một byte** là bản sao đã sai.

Muốn sao chép dữ liệu nhị phân chính xác, dùng **Files.copy(...)**, **InputStream.transferTo(OutputStream)** hoặc vòng lặp **InputStream.read(buffer) / OutputStream.write(buffer, 0, count)**. Nếu cần chắc chắn, kiểm tra lại bằng **Files.mismatch(...)** hoặc hash phù hợp.

### Tham khảo chính thức

- [Oracle Java SE 21 — InputStreamReader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStreamReader.html): chuyển đổi byte thành ký tự và charset.
- [Oracle Java SE 21 — OutputStreamWriter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/OutputStreamWriter.html): chuyển đổi ký tự thành byte.
- [Oracle Java SE 21 — BufferedReader.readLine](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedReader.html#readLine()): xử lý dấu kết thúc dòng.
- [Oracle Java SE 21 — InputStream.transferTo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/InputStream.html#transferTo(java.io.OutputStream)): sao chép theo byte.
- [Oracle Java SE 21 — Files.copy và Files.mismatch](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): sao chép và so sánh file theo byte.


</details>

- [Quay lại đầu trang](#back-to-top)
