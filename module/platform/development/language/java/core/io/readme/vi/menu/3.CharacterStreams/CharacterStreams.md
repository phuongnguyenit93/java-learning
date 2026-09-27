# Character Stream

Byte stream giữ nguyên payload nhị phân. Nhưng khi bài toán nói về “đọc text”, ứng dụng thường muốn nhận ký tự thay vì tự giải mã từng cụm byte. Character stream giải quyết đúng tầng đó.

## <a id="reader-writer">Reader và Writer</a>

`Reader` là **mô hình chung (abstraction)** để **đọc dữ liệu ký tự**. `Writer` là mô hình chung để **ghi dữ liệu ký tự**.

```text
text source ──> Reader ──> char data ──> application
application ──> char data ──> Writer ──> text sink
```

API của chúng giống mental model stream nhưng đơn vị logic là ký tự Java:

- `Reader.read()` trả một giá trị ký tự dưới dạng `int`, hoặc `-1` ở EOF. Dùng `int` giúp API vừa biểu diễn được giá trị ký tự vừa dành riêng `-1` để báo hết dữ liệu.
- `Reader.read(char[])` trả số `char` thực sự đọc được.
- `Writer.write(...)` ghi `char`, `char[]` hoặc `String`.

Giống byte stream, `Reader.read(char[])` **không hứa lấp đầy mảng**. Nếu code cần đúng N ký tự thì phải lặp theo số lượng thực tế đã đọc, thay vì giả định một lần `read` là đủ.

`PrintWriter` là writer tiện cho `print/println/printf`. Giống `PrintStream`, các method ghi của nó không ném `IOException` ra caller; khi cần biết có lỗi I/O hay không, code kiểm tra `checkError()`. Vì semantics này khác `BufferedWriter`/`OutputStreamWriter`, không nên đổi sang `PrintWriter` chỉ vì cú pháp ngắn hơn nếu caller cần xử lý exception trực tiếp.

Ví dụ chỉ làm việc trong memory:

```java
try (java.io.Reader reader =
         new java.io.StringReader("Java I/O")) {
    char[] buffer = new char[4];
    int count = reader.read(buffer);
    System.out.println(new String(buffer, 0, count)); // Java
}
```

`Reader`/`Writer` giúp code xử lý text ở đúng mức abstraction. Tuy nhiên file và network cuối cùng vẫn truyền bytes. Vì thế cần một cầu nối giữa hai thế giới.

## <a id="charset-bridge">Cầu nối charset</a>

**Charset** là bộ quy tắc quy định chuỗi ký tự được biểu diễn thành chuỗi byte như thế nào và ngược lại. **Encoding** là chiều characters → bytes; **decoding** là chiều bytes → characters. Charset cần tồn tại vì cùng một ký tự có thể có cách biểu diễn byte khác nhau giữa các encoding, và một byte đơn lẻ không tự nói nó là ký tự nào.

`InputStreamReader` dùng một `Charset` để **decode bytes → characters**. `OutputStreamWriter` làm chiều ngược lại: **encode characters → bytes**.

```text
file bytes
    ↓ InputStream
InputStreamReader + UTF-8 decoder
    ↓
characters

characters
    ↓
OutputStreamWriter + UTF-8 encoder
    ↓ OutputStream
file bytes
```

Ví dụ round-trip chuỗi UTF-8 qua file tạm:

`Path`, `Files.createTempFile(...)`, `toFile()` và `deleteIfExists(...)` ở đây chỉ là scaffolding để có một file tạm an toàn cho ví dụ. Chapter filesystem phía sau mới là nơi học các API đó; phần cần tập trung ở đây là `Reader`/`Writer` và charset bridge.

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("chars-", ".txt");
String expected = "Xin chào Java ☕";

try (java.io.Writer writer =
         new java.io.OutputStreamWriter(
             new java.io.FileOutputStream(temp.toFile()),
             java.nio.charset.StandardCharsets.UTF_8)) {
    writer.write(expected);
}

StringBuilder actual = new StringBuilder();
try (java.io.Reader reader =
         new java.io.InputStreamReader(
             new java.io.FileInputStream(temp.toFile()),
             java.nio.charset.StandardCharsets.UTF_8)) {
    char[] buffer = new char[8];
    int count;
    while ((count = reader.read(buffer)) != -1) {
        actual.append(buffer, 0, count);
    }
}

System.out.println(expected.contentEquals(actual)); // true
java.nio.file.Files.deleteIfExists(temp);
```

Charset phải giống với format dữ liệu. Nếu file được encode bằng UTF-8 nhưng đọc bằng charset khác, byte vẫn được đọc thành công nhưng text có thể sai hoặc decoder có thể gặp input không hợp lệ.

Không phải lỗi text nào cũng nên được xử lý giống nhau. Khi cần policy rõ ràng cho input hỏng, `CharsetDecoder`/`CharsetEncoder` cho phép chọn `CodingErrorAction.REPORT`, `REPLACE` hoặc `IGNORE` cho malformed/unmappable data. Ví dụ nếu protocol yêu cầu UTF-8 hợp lệ tuyệt đối, có thể cấu hình decoder để `REPORT`:

```java
java.nio.charset.CharsetDecoder decoder =
    java.nio.charset.StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);

java.io.Reader strictReader =
    new java.io.InputStreamReader(inputStream, decoder);
```

Đây là boundary về tính hợp lệ của dữ liệu, không phải phần Unicode sâu: điều cần quyết định ở tầng I/O là input lỗi phải được **report**, **replace** hay **ignore**.

Khi encoding là một phần của format/protocol, hãy truyền `Charset` rõ ràng. Từ Java 18, charset mặc định của Java là UTF-8 trừ khi implementation được cấu hình theo cơ chế cho phép thay đổi default charset; tuy vậy, code phụ thuộc vào một format cụ thể vẫn nên thể hiện quyết định encoding ngay tại boundary để người đọc không phải đoán.

Character stream xử lý `char`/UTF-16 code unit. Các khái niệm sâu hơn như surrogate pair, code point và grapheme **chưa cần hiểu để theo tiếp chương này**; chúng thuộc kiến thức String/Unicode. Điều quan trọng ở đây là charset bridge chịu trách nhiệm chuyển đúng giữa bytes và representation ký tự của Java.

## <a id="character-buffering">Buffering cho text</a>

Đọc từng `char` một qua boundary bên dưới thường tạo nhiều lời gọi nhỏ. Khi xử lý text, Java thường ghép character stream với:

- `BufferedReader` để đọc theo buffer và có `readLine()`;
- `BufferedWriter` để gom nhiều lần ghi ký tự trước khi chuyển xuống writer bên dưới.

Ví dụ:

```java
java.nio.file.Path temp = java.nio.file.Files.createTempFile("lines-", ".txt");

try (java.io.BufferedWriter writer =
         new java.io.BufferedWriter(
             new java.io.OutputStreamWriter(
                 new java.io.FileOutputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    writer.write("dòng 1");
    writer.newLine();
    writer.write("dòng 2");
}

try (java.io.BufferedReader reader =
         new java.io.BufferedReader(
             new java.io.InputStreamReader(
                 new java.io.FileInputStream(temp.toFile()),
                 java.nio.charset.StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
}

java.nio.file.Files.deleteIfExists(temp);
```

`readLine()` bỏ ký tự kết thúc dòng khỏi giá trị trả về và trả `null` khi EOF. Vì line separator có thể khác nhau giữa môi trường, `BufferedWriter.newLine()` giúp ghi separator phù hợp với hệ thống khi đó là điều ứng dụng muốn.

Buffering không thay đổi encoding; charset bridge vẫn chịu trách nhiệm encode/decode. Chapter tiếp theo sẽ tách riêng buffering để giải thích vì sao nó cải thiện throughput, khi nào phải `flush()` và vì sao “buffer càng lớn càng tốt” không phải quy tắc đúng.
