<a id="back-to-top"></a>

# Dòng và token

## Menu
- [BufferedReader.readLine và dấu kết thúc dòng bị loại bỏ](#lines-and-tokens-001)
- [LF, CRLF, CR và quy ước xuống dòng](#lines-and-tokens-002)
- [BufferedWriter.newLine và System.lineSeparator](#lines-and-tokens-003)
- [BufferedReader.lines và quyền đóng Reader](#lines-and-tokens-004)
- [Scanner: nguồn dữ liệu, delimiter và token](#lines-and-tokens-005)
- [next, nextLine và phần xuống dòng còn lại](#lines-and-tokens-006)
- [Đọc số với Scanner theo locale và radix](#lines-and-tokens-007)
- [hasNext, InputMismatchException và token không hợp lệ](#lines-and-tokens-008)
- [Scanner.ioException và đóng nguồn dữ liệu](#lines-and-tokens-009)
- [StreamTokenizer và lựa chọn công cụ phân tích phù hợp](#lines-and-tokens-010)
- [Giới hạn dùng tách dòng hoặc split để đọc định dạng có escape phức tạp](#lines-and-tokens-011)

## <a id="lines-and-tokens-001">BufferedReader.readLine và dấu kết thúc dòng bị loại bỏ</a>

<details>
<summary>Xem chi tiết</summary>

# BufferedReader.readLine(): đọc từng dòng và dấu kết thúc dòng bị loại bỏ

`BufferedReader.readLine()` trong Java giúp đọc văn bản từng dòng mà không cần tự dò ký tự xuống dòng. Mỗi lần gọi trả về **nội dung của một dòng, đã bỏ dấu kết thúc dòng**, hoặc `null` nếu hết dữ liệu và không còn ký tự nào để tạo một dòng mới.

Điều này rất tiện khi phân tích từng dòng trong tệp cấu hình, log hoặc dữ liệu văn bản. Tuy nhiên, có một hệ quả quan trọng: **không thể biết dòng vừa đọc kết thúc bằng `\n`, `\r`, `\r\n` hay đơn giản kết thúc tại EOF chỉ từ `String` mà `readLine()` trả về**. Khi cần giữ nguyên dấu kết thúc dòng, phải chọn cách đọc khác.

## 1. BufferedReader là gì?

`java.io.BufferedReader` là một `Reader` có bộ đệm ký tự. Nó thường bọc quanh một `Reader` khác để đọc nhiều ký tự hiệu quả hơn, chẳng hạn `FileReader` hoặc `InputStreamReader`. Ngoài thao tác `read()` đọc ký tự, phương thức `readLine()` gom các ký tự thành một dòng.

Ví dụ một vòng lặp đọc:

~~~java
String line;
while ((line = reader.readLine()) != null) {
    // Xử lý đúng nội dung dòng, không có ký tự kết thúc dòng.
    System.out.println(line);
}
~~~

Đây là **mảnh mã minh họa**: biến `reader` phải là một `BufferedReader` đã mở, và chương trình phải xử lý `IOException`. Phép gán trong điều kiện là có chủ đích: mỗi vòng gọi `readLine()` **một lần** rồi so sánh giá trị trả về với `null`.

Luồng dữ liệu có hai lớp cần phân biệt:

~~~text
Tệp hoặc luồng byte --[giải mã bằng Charset]--> Reader chứa ký tự
                                            |
                                            v
                                        BufferedReader
                                            |
                                            v
                                    readLine(): String hoặc null
~~~

`BufferedReader` xử lý **ký tự đã được giải mã**, không trực tiếp quyết định encoding của file. Nếu nguồn là byte, chọn charset chính xác khi xây dựng `InputStreamReader`, hoặc dùng `Files.newBufferedReader(path, StandardCharsets.UTF_8)`. Điều này quan trọng với chữ tiếng Việt và dữ liệu trao đổi giữa nhiều máy.

## 2. Những dấu kết thúc dòng mà readLine() nhận diện

Theo hợp đồng của `BufferedReader.readLine()`, một dòng được coi là kết thúc tại:

| Ký hiệu trong chuỗi Java | Tên | Các ký tự kết thúc dòng |
|---|---|---|
| `"\n"` | LF (line feed) | `U+000A` |
| `"\r"` | CR (carriage return) | `U+000D` |
| `"\r\n"` | CRLF | Hai ký tự `U+000D U+000A` đi liền nhau |

Cả ba trường hợp đều được nhận diện khi đọc. Với CRLF, `\r\n` được tính là **một** dấu kết thúc dòng, không tạo thêm dòng rỗng chỉ vì có hai ký tự.

Ví dụ:

~~~text
Dữ liệu đầu vào dưới dạng escape: "Java\r\nSpring\nSQL\rEOF"
Kết quả gọi readLine() lần lượt:
1. "Java"
2. "Spring"
3. "SQL"
4. "EOF"
5. null
~~~

Dòng `"EOF"` được trả về dù không có dấu xuống dòng phía sau: **EOF cũng có thể kết thúc dòng cuối cùng có nội dung**. Chỉ lần gọi tiếp theo, khi không còn ký tự nào, mới trả về `null`.

Đừng nhầm ba dấu kết thúc dòng này với mọi ký tự Unicode hiển thị như xuống dòng. Ví dụ `U+2028` (LINE SEPARATOR) **không phải** dấu ngắt dòng được `readLine()` mô tả trong hợp đồng. `readLine()` cũng không yêu cầu dấu xuống dòng phải trùng `System.lineSeparator()` của hệ điều hành hiện tại. Một ứng dụng chạy trên Windows vẫn đọc đúng dữ liệu có LF và ngược lại.

## 3. Chính xác điều gì bị loại bỏ?

Giả sử nguồn chứa:

~~~text
"A\r\nB\nC\rD"
~~~

`BufferedReader.readLine()` trả theo thứ tự:

~~~text
"A"
"B"
"C"
"D"
null
~~~

Mỗi `String` trả về **không chứa** `\r` hoặc `\n` được dùng để kết thúc dòng. Phương thức không thêm dấu cách hoặc ký tự phân cách thay thế.

Không có thông tin nào đi kèm giá trị trả về cho biết dòng đó dùng LF, CR, CRLF hay kết thúc do EOF. Vì thế các nguồn khác nhau có thể tạo **cùng một danh sách các dòng**. Chẳng hạn cả `"A\nB"` và `"A\r\nB"` đều tạo danh sách `["A", "B"]`.

Lưu ý rằng `readLine()` loại bỏ **dấu kết thúc dòng**, chứ không gọi `trim()` hoặc `strip()`:

- Khoảng trắng đầu dòng và cuối dòng (dấu cách, tab) vẫn nằm trong `String`.
- Một dòng chứa ba dấu cách trả về chuỗi dài 3, **không phải** chuỗi rỗng.
- Một dòng trống thật sự trả về `""`, nghĩa là `String` có độ dài 0, **không phải** `null`.
- Những ký tự `\r`/`\n` đóng vai trò dấu ngắt dòng không còn nằm trong kết quả.

Nếu ứng dụng cần phân biệt dòng trắng do khoảng trắng với dòng hoàn toàn trống, hãy kiểm tra trực tiếp `line.isEmpty()` so với `line.isBlank()`. Hai điều kiện mang ý nghĩa khác nhau.

## 4. Ví dụ hoàn chỉnh: CRLF, CR, LF và dòng trống

Tạo file **`ReadLineDemo.java`** với mã sau (Java 17+):

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class ReadLineDemo {
    public static void main(String[] args) throws IOException {
        String content = "An\r\n\r\nBinh\nCuong\rDung\n";

        try (BufferedReader reader = new BufferedReader(
                new StringReader(content))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                index++;
                System.out.printf("%d: <%s> (len=%d)%n",
                        index, line, line.length());
            }
            System.out.println("count=" + index);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ReadLineDemo.java
java ReadLineDemo
~~~

Stdout chính xác:

~~~text
1: <An> (len=2)
2: <> (len=0)
3: <Binh> (len=4)
4: <Cuong> (len=5)
5: <Dung> (len=4)
count=5
~~~

Giải thích thứ tự:

1. `"An\r\n"` tạo dòng `"An"`; cả CR và LF của cặp CRLF bị bỏ.
2. `"\r\n"` ngay sau đó tạo một dòng **rỗng**; đó là dòng thứ hai `""`, có độ dài 0.
3. `"Binh\n"` tạo `"Binh"`, `"Cuong\r"` tạo `"Cuong"`.
4. `"Dung\n"` tạo `"Dung"`. Dù dữ liệu có LF cuối cùng, `readLine()` **không sinh thêm một dòng rỗng** chỉ vì chuỗi kết thúc bằng đúng một dấu kết thúc dòng.
5. Lần gọi sau cùng gặp EOF, trả `null`; vòng lặp dừng.

Ví dụ sử dụng `StringReader` để kết quả không phụ thuộc hệ điều hành hoặc encoding của file thực tế. Quy tắc loại dấu kết thúc dòng là quy tắc của `BufferedReader` và vẫn áp dụng khi nguồn là tệp.

## 5. Phân biệt dòng rỗng, hết file và newline ở cuối

Đây là phần thường gây lỗi nhất. Xem các mẫu dữ liệu sau:

| Nội dung nguồn (cú pháp escape) | Những giá trị `readLine()` trước EOF | Số dòng |
|---|---|---:|
| `""` | Không có `String` nào; trả `null` ngay | 0 |
| `"\n"` | `""` rồi `null` | 1 |
| `"\r\n"` | `""` rồi `null` | 1 |
| `"A"` | `"A"` rồi `null` | 1 |
| `"A\n"` | `"A"` rồi `null` | 1 |
| `"A\r\n"` | `"A"` rồi `null` | 1 |
| `"A\n\n"` | `"A"`, `""` rồi `null` | 2 |
| `"\nA"` | `""`, `"A"` rồi `null` | 2 |
| `"A\n\nB"` | `"A"`, `""`, `"B"` rồi `null` | 3 |

Hai khác biệt cần nhớ:

**Dòng trống**: một dấu kết thúc dòng xuất hiện khi chưa có ký tự nội dung kể từ dấu kết thúc dòng trước đó. Vì vậy đọc `"\n"` cho dòng rỗng `""`. Vòng lặp phải tiếp tục khi giá trị là `""`.

**EOF**: khi `readLine()` không còn bất kỳ ký tự nào để tạo một dòng mới, nó trả `null`. Dòng cuối có nội dung được trả về ngay cả khi chưa có dấu kết thúc dòng. Khi dấu kết thúc dòng đã là ký tự cuối, EOF sau đó **không phát sinh thêm dòng mới**.

Vì thế `"A"` và `"A\n"` cho cùng chuỗi trả về `"A"` rồi `null`. Nếu cần biết file gốc có newline cuối hay không, `readLine()` **không đủ thông tin**.

### Kiểm chứng bằng chương trình Java

Tạo **`ReadLineEdgeCases.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class ReadLineEdgeCases {
    public static void main(String[] args) throws IOException {
        inspect("empty", "");
        inspect("LF", "\n");
        inspect("A", "A");
        inspect("A+LF", "A\n");
        inspect("A+LF+LF", "A\n\n");
        inspect("A+CRLF", "A\r\n");
    }

    private static void inspect(String label, String source)
            throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new StringReader(source))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        String first = lines.isEmpty()
                ? "<none>" : "\"" + lines.get(0) + "\"";
        System.out.printf("%s -> count=%d, first=%s%n",
                label, lines.size(), first);
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReadLineEdgeCases.java
java ReadLineEdgeCases
~~~

Stdout:

~~~text
empty -> count=0, first=<none>
LF -> count=1, first=""
A -> count=1, first="A"
A+LF -> count=1, first="A"
A+LF+LF -> count=2, first="A"
A+CRLF -> count=1, first="A"
~~~

Chương trình cho thấy sự khác biệt giữa **`null` khi EOF**, **`""` khi có một dòng trống**, và việc **một dấu xuống dòng ở cuối không tự cộng thêm một dòng rỗng**.

## 6. Đọc file thực tế: luôn kiểm soát charset

Khi nguồn là tệp, có thể dùng `Files.newBufferedReader`. Mặc định `Files.newBufferedReader(path)` dùng UTF-8; để rõ hợp đồng dữ liệu, nên truyền `StandardCharsets.UTF_8` tường minh, nhất là khi xử lý file chứa tiếng Việt.

Ví dụ độc lập **`ReadTextFileLines.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadTextFileLines {
    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("read-lines-", ".txt");

        try {
            Files.writeString(path, "red\r\n\r\nblue\n",
                    StandardCharsets.UTF_8);

            try (BufferedReader reader = Files.newBufferedReader(
                    path, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    System.out.printf("%d: [%s]%n",
                            ++lineNumber, line);
                }
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReadTextFileLines.java
java ReadTextFileLines
~~~

Stdout:

~~~text
1: [red]
2: []
3: [blue]
~~~

Tệp tạm chỉ phục vụ minh họa; đoạn mã dùng `finally` để xóa sau khi đóng `BufferedReader`. Với một đường dẫn do người dùng cung cấp, hãy xử lý quyền truy cập và `IOException`; đừng dùng mã xóa file tạm để xóa một tệp thật.

Có thể mở bằng constructor truyền charset khác:

~~~java
// Mảnh mã minh họa: path là một Path đã được xác định.
try (BufferedReader reader =
         Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Sử dụng line.
    }
}
~~~

Đây là cách thuận tiện khi nguồn là file UTF-8. Nếu file được mã hóa bằng charset khác, phải biết và truyền đúng charset đó; `BufferedReader` **không tự phát hiện encoding**. Một số byte sai encoding có thể gây lỗi giải mã hoặc ký tự thay thế tùy reader/decoder và quy tắc của API.

### BufferedReader và dấu BOM

Nếu file UTF-8 có tiền tố BOM (`EF BB BF`), các API đọc ký tự UTF-8 thường có thể để lại ký tự `U+FEFF` ở đầu nội dung thay vì tự loại bỏ nó. `readLine()` **chỉ loại dấu kết thúc dòng** theo hợp đồng, nên đừng kỳ vọng nó loại BOM, khoảng trắng hoặc metadata khác. Nếu hệ thống hỗ trợ file có BOM, hãy quyết định xử lý BOM tại đầu văn bản theo định dạng dữ liệu.

## 7. Khi ghi lại: dấu kết thúc dòng có thể bị thay đổi

Vì đã mất ký tự phân cách gốc, code đọc bằng `readLine()` rồi ghi bằng một separator tự chọn sẽ **chuẩn hóa dấu kết thúc dòng**, không tái tạo byte gốc.

Ví dụ:

~~~java
// Mảnh mã minh họa: reader và writer đã được mở và cần đóng đúng cách.
String line;
while ((line = reader.readLine()) != null) {
    writer.write(line);
    writer.newLine();
}
~~~

`BufferedWriter.newLine()` ghi **line separator của nền tảng hiện tại** theo quy tắc của `System.lineSeparator()`. Nếu file gốc dùng CRLF, chương trình chạy trên một môi trường dùng LF có thể đổi CRLF thành LF. Hơn nữa, ví dụ này luôn ghi một dấu kết thúc dòng sau mỗi dòng, kể cả khi dòng cuối của nguồn ban đầu không có dấu kết thúc dòng.

Với `writer.write('\n')`, chương trình cố định LF nhưng cũng vẫn thay đổi đầu vào nếu nguyên bản dùng CRLF/CR. Với `System.lineSeparator()` hoặc `PrintWriter.println()`, ý nghĩa cũng là tạo một separator mới, không phải khôi phục separator đã bị xóa.

### Ví dụ nhận biết việc chuẩn hóa

Tạo tệp **`NormalizeLinesDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class NormalizeLinesDemo {
    public static void main(String[] args) throws IOException {
        String source = "one\r\ntwo\rthree\n";
        StringBuilder normalized = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new StringReader(source))) {
            boolean first = true;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!first) {
                    normalized.append('\n');
                }
                normalized.append(line);
                first = false;
            }
        }

        System.out.println("source chars=" + source.length());
        System.out.println("normalized chars=" + normalized.length());
        System.out.println("normalized="
                + normalized.toString().replace("\n", "|"));
    }
}
~~~

~~~bash
javac -encoding UTF-8 NormalizeLinesDemo.java
java NormalizeLinesDemo
~~~

Stdout:

~~~text
source chars=15
normalized chars=13
normalized=one|two|three
~~~

Nội dung nguồn có CRLF, CR và một LF ở cuối; kết quả chỉ nối các dòng bằng LF và không có newline cuối. `boolean first` được dùng thay vì `normalized.length() > 0` vì **dòng đầu có thể rỗng**: một dòng rỗng vẫn phải được tính là một dòng đã đọc. Ngay cả khi code chèn LF trước mỗi dòng sau dòng đầu, việc khôi phục byte nguồn chính xác vẫn là điều không thể từ các chuỗi `readLine()` đơn thuần.

Nếu mục đích chỉ là **phân tích nội dung từng dòng**, bỏ dấu ngắt thường là điều mong muốn. Nếu mục đích là **sao chép file nguyên vẹn**, đừng dùng đọc dòng rồi ghi lại; sao chép byte bằng `Files.copy` hoặc `InputStream`/`OutputStream`. Nếu cần chỉnh sửa mà bảo toàn chính xác CR, LF, CRLF và trạng thái newline cuối, phải đọc theo ký tự/byte và lưu separator cùng từng dòng (hoặc giữ toàn bộ nội dung trong bộ nhớ khi kích thước cho phép).

## 8. readLine(), lines() và các API khác

| Cách đọc | Kết quả | Mức độ giữ separator |
|---|---|---|
| `BufferedReader.readLine()` | Một `String` mỗi lần; `null` khi EOF | Bỏ CR, LF, CRLF |
| `BufferedReader.lines()` | `Stream<String>` của các dòng | Vẫn bỏ dấu kết thúc dòng |
| `Files.lines(path, charset)` | `Stream<String>` từ file | Bỏ dấu kết thúc dòng |
| `Files.readAllLines(path, charset)` | `List<String>` của toàn bộ dòng | Bỏ dấu kết thúc dòng |
| `Files.readString(path, charset)` | Toàn bộ văn bản dưới dạng `String` | Giữ các ký tự `\r` và `\n` có trong nội dung |
| Đọc bằng `Reader.read()` hoặc `read(char[])` | Các ký tự thô được reader giải mã | Có thể quan sát và lưu lại CR/LF |

`BufferedReader.lines()` có thể thuận tiện cho xử lý kiểu Stream, nhưng kết quả vẫn là các dòng **không mang terminator**. Khi đọc `lines()` mà có lỗi I/O trong quá trình tiêu thụ stream, ngoại lệ được biểu diễn dưới dạng `UncheckedIOException`. Không trộn thao tác đọc trực tiếp trên `BufferedReader` với việc tiêu thụ stream `lines()` đang hoạt động.

Ví dụ khi muốn duyệt từng dòng bằng Stream:

~~~java
// Mảnh mã minh họa: reader cần đang mở trong phạm vi try-with-resources.
reader.lines()
      .filter(line -> !line.isBlank())
      .forEach(System.out::println);
~~~

Giải pháp này chủ động bỏ dòng trống và dòng chỉ có whitespace vì điều kiện `!line.isBlank()`; đó là quyết định của ứng dụng, **không phải hành vi mặc định của `readLine()`**.

Lưu ý quản lý tài nguyên: với một stream lấy từ `reader.lines()`, đóng stream không thay thế cho việc quản lý đóng `reader` theo hợp đồng của `BufferedReader`; hãy sử dụng `try-with-resources` cho `BufferedReader`. Với `Files.lines(...)`, stream trả về chứa tài nguyên file và chính stream cần đóng.

## 9. Hiệu năng, độ dài dòng và các trường hợp cần cẩn thận

- **Có thể chặn để chờ**: trên một nguồn ký tự đang mở như socket, `readLine()` có thể chờ đến khi gặp dấu kết thúc dòng, EOF hoặc lỗi; không nên giả định mỗi lần gọi lập tức có một dòng.
- **Dòng rất dài**: `BufferedReader` có bộ đệm nhưng `readLine()` vẫn phải tạo `String` cho toàn bộ dòng. Dữ liệu không đáng tin cậy có thể có dòng dài bất thường gây tiêu thụ bộ nhớ lớn. Nếu cần giới hạn kích thước mỗi bản ghi, nên có logic đọc ký tự theo giới hạn trước khi gom toàn bộ dòng.
- **Không phải parser CSV tổng quát**: CSV có thể chứa dấu xuống dòng bên trong trường được đặt trong dấu nháy. `readLine()` chỉ phân tách dòng vật lý, không tự phân tách bản ghi logic của CSV.
- **Không loại whitespace**: `readLine()` không gọi `trim()`/`strip()`, không bỏ comment, không tách token; ứng dụng phải chủ động chọn bước xử lý sau đó.
- **Không dùng `reader.ready()` làm điều kiện hết file**: `ready()` cho biết liệu thao tác đọc tiếp theo có thể không bị block, không phải dấu EOF. Cách lặp đúng vẫn là kiểm tra `readLine() != null`.
- **Ngoại lệ I/O**: phương thức có thể ném `IOException`; dùng `try-with-resources`, phản ánh rõ lỗi đọc và tránh giả định rằng `null` đại diện cho lỗi.

Đối với file nhỏ và mục tiêu giữ nguyên các ký tự ngắt dòng, `Files.readString(path, UTF_8)` dễ hiểu hơn. Với file lớn và mục tiêu xử lý theo dòng, `BufferedReader.readLine()` giúp không cần tải toàn bộ file, miễn là từng dòng vẫn có kích thước hợp lý.

## 10. Các lỗi thường gặp và cách sửa

| Sai sót | Vì sao sai | Cách làm đúng |
|---|---|---|
| `while ((line = reader.readLine()) != "")` | So sánh tham chiếu `String` và không kiểm tra EOF; có thể gặp `null` | Dùng `while ((line = reader.readLine()) != null)` |
| `if (line.isEmpty()) break;` | Dòng rỗng `""` là dữ liệu hợp lệ, không phải EOF | Dừng khi `line == null` |
| Nghĩ file kết thúc bằng `\n` thì có thêm dòng rỗng | Một terminator cuối không tạo thêm một dòng mới ở EOF | Xem các ví dụ `"A\n"` và `"A\n\n"` |
| Ghép `readLine()` bằng `System.lineSeparator()` để tái tạo file y nguyên | Separator gốc đã bị loại, không còn biết LF/CRLF/CR hoặc newline cuối | Đọc/giữ separator thô nếu cần bảo toàn |
| Giả định `readLine()` tự hiểu UTF-8 | `BufferedReader` đọc ký tự đã giải mã bởi Reader bên dưới | Chọn charset ở `InputStreamReader` hoặc `Files.newBufferedReader` |
| Dùng `trim()` khi cần giữ whitespace | `trim()` xóa nội dung có ý nghĩa, khác với việc bỏ terminator | Dùng nguyên `line`, chỉ strip nếu nghiệp vụ yêu cầu |
| Dùng `reader.ready()` để xác định EOF | `ready()` không phải phép kiểm tra hết dữ liệu | Đọc đến khi trả `null` |
| Dùng `readLine()` cho tài liệu phải bảo toàn byte hoặc newline | Thao tác đọc dòng làm mất thông tin phân cách | Đọc ký tự/byte thô hoặc `Files.readString` với file vừa bộ nhớ |

## 11. Ghi nhớ

1. `BufferedReader.readLine()` nhận diện **LF (`\n`), CR (`\r`) và CRLF (`\r\n`)** và **loại bỏ dấu kết thúc dòng** khỏi `String` trả về.
2. **`null` nghĩa là EOF** khi không còn dòng nào; **`""` là một dòng rỗng hợp lệ**. Phải dừng trên `null`, không dừng trên `""`.
3. Dòng cuối **không có terminator** vẫn được trả về; dấu newline đơn lẻ ở cuối file **không tạo thêm một dòng trống**.
4. Từ `readLine()` **không thể khôi phục chính xác** loại separator hoặc biết có newline ở cuối nguồn. Đọc rồi ghi lại thường thay đổi dấu xuống dòng.
5. Chọn **charset chính xác**, đóng `BufferedReader` bằng `try-with-resources` và xử lý `IOException`.
6. Dùng đọc theo dòng cho xử lý nội dung; dùng cách đọc bảo toàn ký tự/byte nếu yêu cầu giữ nguyên định dạng.

### Tài liệu tham khảo chính thức

- [Java SE — BufferedReader.readLine()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#readLine())
- [Java SE — BufferedReader.lines()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html#lines())
- [Java SE — Files.newBufferedReader()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newBufferedReader(java.nio.file.Path,java.nio.charset.Charset))


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-002">LF, CRLF, CR và quy ước xuống dòng</a>

<details>
<summary>Xem chi tiết</summary>

# LF, CRLF, CR và quy ước xuống dòng trong Java

Trong file văn bản, **xuống dòng** là quy ước dùng một hoặc nhiều ký tự điều khiển để phân tách các dòng. Ba cách phổ biến nhất là **LF** (`\n`), **CRLF** (`\r\n`) và **CR** (`\r`). Cùng một nội dung hiển thị có thể được lưu bằng các dãy ký tự hoặc byte khác nhau; điều này ảnh hưởng đến đọc file, so sánh chuỗi, xử lý token, đếm dòng, truyền dữ liệu giữa hệ điều hành và tuân thủ các định dạng/giao thức yêu cầu một kiểu xuống dòng cụ thể.

Trong Java I/O, cần phân biệt hai thao tác:

- **Đọc văn bản thô:** giữ nguyên ký tự `\r` và `\n` đang hiện diện trong dữ liệu.
- **Đọc theo dòng:** nhận các chuỗi nội dung dòng; API có thể *nhận diện và loại bỏ* dấu kết thúc dòng.

Bài học làm rõ byte và ký tự tương ứng, quy ước lịch sử, hành vi của `BufferedReader`, `String.lines()`, `Files.readAllLines`, cách ghi xuống dòng có chủ đích và những lỗi xử lý thường gặp.

## 1. LF, CR, CRLF là gì?

| Tên | Escape Java | Unicode | Byte trong ASCII / UTF-8 | Dạng thập lục phân | Ý nghĩa quy ước |
|---|---|---|---|---|---|
| **LF** — Line Feed | `\n` | U+000A | Một byte `0A` | `0x0A` | Chuyển tới dòng kế tiếp |
| **CR** — Carriage Return | `\r` | U+000D | Một byte `0D` | `0x0D` | Trả vị trí về đầu dòng trong cơ chế máy đánh chữ |
| **CRLF** — Carriage Return + Line Feed | `\r\n` | U+000D rồi U+000A | Hai byte `0D 0A` | `0x0D 0x0A` | Cặp ký tự dùng làm một dấu kết thúc dòng |

Trong **UTF-8**, LF và CR vẫn là các byte đơn `0A` và `0D`. Trong UTF-16LE, cùng các code unit đó thường xuất hiện dưới dạng `0A 00` và `0D 00` ở mức byte; vì vậy không được quét byte `0A` để tìm dòng trong mọi loại mã hóa mà chưa hiểu charset. Bảng trên chỉ khẳng định giá trị byte đối với ASCII và UTF-8.

**Java xử lý CRLF gồm hai `char`**: `'\r'` và `'\n'`. Ở lớp đọc theo dòng, nhiều API coi hai `char` liền nhau là **một dấu ngắt dòng**, chứ không phải hai dòng ngắt độc lập. Điều này khác với việc chuỗi chứa hai code unit.

### Bối cảnh hệ điều hành

- **Unix/Linux** và macOS hiện đại thông thường dùng **LF** làm dấu kết thúc dòng trong file văn bản.
- **Windows** truyền thống dùng **CRLF** trong nhiều file văn bản và API/tiện ích.
- **Classic Mac OS trước macOS** từng dùng **CR**.
- Nhiều bộ soạn thảo hiện đại đọc/ghi được cả ba loại; định dạng file, giao thức mạng, ứng dụng và cấu hình mới quyết định chính xác kiểu được lưu.

Không nên giả định rằng *mọi* file được tạo trên Windows luôn có CRLF hay mọi file trên Linux chỉ có LF. Ví dụ file được tải từ Git, tạo bởi code Java ghi `"\n"`, hoặc do phần mềm bên ngoài xuất ra có thể giữ nguyên kiểu xuống dòng khác quy ước của hệ điều hành.

## 2. Dấu xuống dòng thuộc văn bản, không phải hình thức hiển thị

Hai chuỗi sau thường được hiển thị giống nhau trong trình xem văn bản:

```java
String lf = "first\nsecond";
String crlf = "first\r\nsecond";
```

Nhưng:

- `lf.length() == 12`, vì chỉ có một `char` `'\n'` ở giữa.
- `crlf.length() == 13`, vì có hai `char` `'\r'` và `'\n'` ở giữa.
- `lf.equals(crlf)` là **`false`**; mảng byte UTF-8, độ dài, checksum/hash và vị trí offset cũng khác.

Một đoạn văn bản có thể gồm các dòng `A`, `B`, `C` mà file dùng LF, CRLF, CR, hoặc **trộn cả ba**. Khi xem nội dung chỉ bằng mắt, không dễ phát hiện sự khác biệt.

Không nhầm lẫn ký tự điều khiển với hai ký tự chữ `\` và `n`. Trong mã nguồn Java, `"\n"` là **một** ký tự LF khi chạy; `"\\n"` là **hai** ký tự hiển thị `\` và `n`. Tương tự `"\r\n"` tạo hai ký tự điều khiển, còn `"\\r\\n"` chỉ là văn bản ký hiệu.

### Khái niệm line terminator, line separator và “dòng”

Đối với I/O, LF, CRLF và CR thường được gọi là **line terminators** — ký tự/cặp ký tự đánh dấu kết thúc một dòng. **Line content** là phần dữ liệu giữa các terminator. API đọc dòng như `BufferedReader.readLine()` trả lại **nội dung dòng** mà không kèm terminator.

Tùy API, `"A\n"` có thể biểu diễn **một dòng `A` có dấu kết thúc**, chứ không nhất thiết là hai dòng với dòng cuối rỗng. Phân biệt:

- **Dòng rỗng thực sự** nằm giữa hai dấu ngắt liên tiếp, hoặc là dòng trước dấu ngắt ở đầu dữ liệu.
- **Kết thúc file ngay sau dấu ngắt** thường **không sinh thêm** một dòng rỗng từ `readLine()` hoặc `String.lines()`.
- **File không có dấu ngắt cuối** vẫn có một dòng cuối nếu còn nội dung.
- **File trống** có thể được các API đếm là 0 dòng; đừng tự giả định 1 dòng.

Các tình huống này ảnh hưởng trực tiếp đến số dòng, logic parser và việc lưu lại đúng định dạng ban đầu.

## 3. Java có những cách đọc dòng nào?

### `BufferedReader.readLine()`

`BufferedReader.readLine()` chấp nhận dấu kết thúc dòng bằng **LF**, **CR** hoặc **CRLF**. Nó trả về `String` **không chứa** ký tự kết thúc dòng và trả `null` khi EOF. Dòng rỗng hợp lệ trả `""`.

Một lưu ý: ký tự CRLF liền nhau được nhìn như **một** terminator. Nếu mã nguồn Java đọc bằng `readLine()`, ứng dụng thường **không còn biết** liệu dòng cũ dùng LF, CRLF hay CR.

### `String.lines()` (Java 11+)

`"text".lines()` trả về `Stream<String>` gồm các dòng, nhận diện `\n`, `\r` hoặc `\r\n`. Các dấu ngắt bị bỏ khỏi kết quả. Stream này được xử lý theo nhu cầu và không bắt buộc chia toàn chuỗi thành một mảng ngay lập tức.

- Chuỗi `""` không có dòng: stream rỗng.
- Chuỗi `"A"` có một dòng `A`.
- Chuỗi `"A\n"` có một dòng `A`, **không tự phát sinh dòng cuối rỗng**.
- Chuỗi `"A\n\n"` có dòng `A` và một dòng rỗng.
- Chuỗi `"\n"` chứa một dòng rỗng.
- CRLF chỉ là **một** terminator dù gồm hai `char`.

`String.lines()` **không đồng nghĩa** với “tách mọi dấu ngắt dòng Unicode”: nó nhận diện đúng ba dạng được API quy định (LF, CR, CRLF), trong khi regex `\R` trong Java hỗ trợ tập line-break rộng hơn (ví dụ Unicode NEL, LS, PS).

### `Files.readAllLines` và `Files.lines`

Hai API thuộc `java.nio.file.Files` cũng nhận diện CRLF, LF và CR theo hợp đồng:

- `Files.readAllLines(path, charset)` đọc các dòng thành `List<String>`, loại dấu kết thúc dòng, **giữ toàn bộ kết quả trong bộ nhớ**.
- `Files.lines(path, charset)` tạo `Stream<String>` đọc file theo nhu cầu. Vì stream gắn với tài nguyên file, **đóng stream** trong `try-with-resources`; terminal operation như `count()` không thay thế việc đóng.
- `Files.readString(path, charset)` đọc **toàn bộ nội dung text**, bao gồm các ký tự CR/LF còn nguyên, nếu giải mã đúng; đừng dùng `readAllLines` để tạo bản sao cần giữ nguyên mọi dấu xuống dòng.

Đọc theo dòng có giá trị khi xử lý dữ liệu văn bản ở mức **dòng**; để giữ nguyên byte, dùng `Files.readAllBytes` hoặc stream byte, nhớ chọn đúng charset khi dựng lại văn bản.

## 4. Ví dụ 1 — ba quy ước trong cùng một chuỗi

Tạo tệp `LineEndingDemo.java` (Java 17+):

```java
import java.io.BufferedReader;
import java.io.StringReader;

public class LineEndingDemo {
    public static void main(String[] args) throws Exception {
        String mixed = "A\r\nB\nC\rD\r\n";

        System.out.println("raw length=" + mixed.length());
        System.out.println("String.lines count=" + mixed.lines().count());

        try (BufferedReader reader = new BufferedReader(new StringReader(mixed))) {
            String line;
            int index = 1;
            while ((line = reader.readLine()) != null) {
                System.out.println("line " + index++ + "=[" + line + "]");
            }
        }

        String normalized = mixed.replace("\r\n", "\n").replace('\r', '\n');
        System.out.println("normalized=" + normalized.replace("\n", "<LF>"));
        System.out.println("normalized length=" + normalized.length());
        System.out.println("keeps trailing LF=" + normalized.endsWith("\n"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 --release 17 LineEndingDemo.java
java LineEndingDemo
```

Kết quả đã kiểm tra:

```text
raw length=10
String.lines count=4
line 1=[A]
line 2=[B]
line 3=[C]
line 4=[D]
normalized=A<LF>B<LF>C<LF>D<LF>
normalized length=8
keeps trailing LF=true
```

**Cách hiểu:**

1. Biến `mixed` chứa lần lượt CRLF, LF, CR và CRLF. Bốn dấu kết thúc dòng đó tạo ra bốn dòng mang nội dung `A`, `B`, `C`, `D`.
2. `mixed.length()` bằng `10` UTF-16 code unit: bốn chữ cái, hai ký tự LF/CR đơn (ở giữa), cộng bốn ký tự nằm trong hai cặp CRLF.
3. `BufferedReader.readLine()` nhận cả ba dạng terminator và **loại dấu kết thúc**, do đó đầu ra là bốn chuỗi không chứa `\r` hoặc `\n`.
4. Lệnh `mixed.replace("\r\n", "\n").replace('\r', '\n')` chuyển **CRLF trước**, rồi CR đơn thành LF. Kết quả `A\nB\nC\nD\n` dài `8` code unit.
5. Dấu LF cuối vẫn còn trong `normalized`. Nếu ngay sau đó gọi `normalized.lines()` thì stream vẫn chỉ có bốn dòng, không phát sinh dòng rỗng cuối.

**Thứ tự thay thế rất quan trọng:** nếu thay `\r` bằng `\n` **trước**, chuỗi CRLF trở thành `\n\n` — hai dấu xuống dòng, vô tình thêm dòng rỗng.

Việc chuẩn hóa trên chỉ thay ký tự ngắt dòng **trong chính chuỗi văn bản**. Nó không thay đổi dữ liệu gốc nếu bạn chưa ghi chuỗi mới vào file, và không tự chọn charset hay kiểu xuống dòng mà một giao thức ngoài yêu cầu.

## 5. Ví dụ 2 — byte trong file và hành vi của `Files`

Tạo tệp `FileLineEndingDemo.java` (Java 17+):

```java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

public class FileLineEndingDemo {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("line-ending-demo-", ".txt");
        String text = "first\nsecond\r\nthird\r";
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
            byte[] bytes = Files.readAllBytes(file);
            System.out.println("hex=" + HexFormat.of().formatHex(bytes));
            System.out.println("raw same=" + text.equals(
                    Files.readString(file, StandardCharsets.UTF_8)));
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            System.out.println("lines=" + lines);
            System.out.println("line count=" + lines.size());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 FileLineEndingDemo.java
java FileLineEndingDemo
```

```text
hex=66697273740a7365636f6e640d0a74686972640d
raw same=true
lines=[first, second, third]
line count=3
```

Đầu ra hex cho thấy:

- Chữ `first` tiếp theo là `0A`, tức LF.
- Chữ `second` tiếp theo là `0D0A`, tức CRLF.
- Chữ `third` tiếp theo là `0D`, tức CR.
- Không có byte hay dòng nội dung nào được Java ngầm đổi khi `Files.writeString` nhận chuỗi có dấu ngắt được ghi rõ.

`Files.readString` giữ nguyên thứ tự dấu kết thúc dòng trong văn bản giải mã; do đó `raw same=true`. Trong khi đó `Files.readAllLines` **bỏ terminator**, chỉ trả ba dòng. Nếu nối danh sách dòng lại bằng dấu phân cách tự chọn, kết quả có thể hiển thị giống nhưng **không khôi phục nguyên vẹn file ban đầu**.

Ví dụ chỉ dùng dữ liệu ASCII nên UTF-8 biểu diễn từng ký tự thành một byte. Với tiếng Việt, các ký tự ngoài ASCII có thể chiếm nhiều byte; LF/CR vẫn là các giá trị U+000A/U+000D ở tầng Unicode, nhưng lúc kiểm tra byte cần xét charset cụ thể.

## 6. Ví dụ 3 — dòng rỗng, terminator cuối và token

Tạo tệp `EndOfLineCasesDemo.java` (Java 17+):

```java
import java.util.Arrays;
import java.util.Scanner;

public class EndOfLineCasesDemo {
    public static void main(String[] args) {
        String[] samples = {"", "A", "A\n", "A\n\n", "\n", "\r\n"};
        for (String sample : samples) {
            String escaped = sample.replace("\r", "\\r").replace("\n", "\\n");
            System.out.println("text=[" + escaped + "], lines="
                    + sample.lines().map(String::length).toList());
        }
        try (Scanner scanner = new Scanner("Ada 42\r\nLinh 7\n")) {
            System.out.println("name=" + scanner.next());
            System.out.println("age=" + scanner.nextInt());
            System.out.println("rest=[" + scanner.nextLine() + "]");
            System.out.println("next line=[" + scanner.nextLine() + "]");
        }
    }
}
```

```bash
javac -encoding UTF-8 --release 17 EndOfLineCasesDemo.java
java EndOfLineCasesDemo
```

```text
text=[], lines=[]
text=[A], lines=[1]
text=[A\n], lines=[1]
text=[A\n\n], lines=[1, 0]
text=[\n], lines=[0]
text=[\r\n], lines=[0]
name=Ada
age=42
rest=[]
next line=[Linh 7]
```

Danh sách `lines` cố ý chứa **độ dài các dòng** thay vì in trực tiếp từng phần tử: `List.of("")` khi in cũng hiển thị `[]`, dễ nhầm với danh sách rỗng. Ở kết quả trên, `lines=[0]` là **một dòng rỗng**; `lines=[]` là **không có dòng nào**.

`Scanner` được dùng để minh họa sự khác nhau giữa **token** và **dòng**:

- `next()` lấy token `Ada`; `nextInt()` lấy token số `42`. Scanner mặc định phân tách token bằng khoảng trắng theo quy tắc `Character.isWhitespace`, nên các dấu xuống dòng là ranh giới token.
- Sau `nextInt()`, dấu kết thúc dòng CRLF chưa được `nextLine()` tiêu thụ theo cách nhiều người kỳ vọng. `nextLine()` đầu tiên nhận **phần còn lại của dòng hiện tại**, tức chuỗi rỗng `""`.
- `nextLine()` kế tiếp mới lấy dòng `Linh 7`.

Trong bài toán nhập liệu tương tác, đây là lý do phổ biến khiến `nextLine()` sau `nextInt()` tưởng như “bị bỏ qua”. Cần chủ động tiêu thụ phần còn lại của dòng nếu muốn chuyển từ thao tác token sang thao tác đọc nguyên dòng. Đừng dùng `scanner.useDelimiter("\n")` một cách tùy tiện để xử lý cả CRLF: dấu CR có thể còn sót tùy mẫu delimiter và thao tác đọc.

## 7. Ghi xuống dòng: nên dùng cách nào?

### Khi muốn dùng quy ước của môi trường chạy

- `System.lineSeparator()` trả chuỗi dấu kết thúc dòng phụ thuộc môi trường hệ thống (thông thường `"\n"` trên Linux/macOS và `"\r\n"` trên Windows).
- `BufferedWriter.newLine()` ghi dấu xuống dòng dựa vào quy ước được hệ thống xác định.
- `PrintWriter.println(...)` và `System.out.println(...)` ghi kết thúc dòng theo hành vi formatter/writer và nền tảng.

Ví dụ bên trong một phương thức đã có `BufferedWriter writer`:

```java
writer.write("Dòng thứ nhất");
writer.newLine();
writer.write("Dòng thứ hai");
```

Đây là **mã minh họa**, cần có biến `writer` được khởi tạo và xử lý `IOException` khi cần. Nếu muốn output text dùng kiểu xuống dòng của hệ điều hành, `newLine()` là cách diễn đạt ý định rõ ràng.

### Khi phải tuân theo định dạng hoặc giao thức cụ thể

Ghi **literal terminator** do đặc tả yêu cầu:

```java
String dataLf = "A\nB\n";
String dataCrlf = "A\r\nB\r\n";
String dataCr = "A\rB\r";
```

- Với file chuẩn hóa LF trong dự án, ghi `"\n"` một cách rõ ràng.
- Với giao thức yêu cầu CRLF, phải ghi `"\r\n"`. Ví dụ các dòng header của HTTP/1.1 theo cú pháp giao thức dùng CRLF; không dùng `System.lineSeparator()` thay một cách tùy tiện.
- Khi tạo file cần bảo toàn nguyên trạng, **không tự gọi `newLine()` ở chỗ mỗi terminator vốn khác nhau** nếu chỉ có thông tin về nội dung từng dòng.

`System.lineSeparator()` không phải chức năng phân tích file đầu vào. Nó mô tả quy ước của môi trường chạy, **không suy ra** được file đang đọc dùng dấu kết thúc dòng nào.

### Dòng cuối có bắt buộc newline không?

Không có đáp án duy nhất cho mọi file. Một số công cụ theo truyền thống Unix thích hoặc yêu cầu **newline cuối file**; một số giao thức/định dạng quy định riêng; nhiều ứng dụng vẫn đọc được dòng cuối không có terminator. Ví dụ:

```text
"A\nB"   -> dòng B kết thúc tại EOF, không có terminator cuối
"A\nB\n" -> dòng B có LF cuối
```

Hai chuỗi đều có **hai dòng nội dung** theo `String.lines()` và `BufferedReader.readLine()`, nhưng **khác byte**. Kiểm tra yêu cầu đầu ra trước khi tự thêm hoặc xóa newline cuối.

## 8. Chuẩn hóa newline mà không làm mất nội dung cần giữ

Trong nhiều ứng dụng, chuẩn hóa LF giúp so sánh và xử lý nhất quán. Với một **chuỗi Java** chỉ dùng ba loại CRLF, CR, LF:

```java
String normalized = original.replace("\r\n", "\n").replace('\r', '\n');
```

Đây là **mã minh họa**; `original` phải là chuỗi được tạo ở chương trình của bạn. Cách này:

1. Chuyển cặp CRLF thành một LF.
2. Chuyển các CR đơn còn lại thành LF.
3. Giữ LF đã có; không tự cắt khoảng trắng, không tự xóa dòng rỗng, không tự thêm newline cuối.

Đây là phép biến đổi có mất thông tin về **loại dấu ngắt cũ**, dù giữ cấu trúc các dòng trong trường hợp ba loại terminator trên. Nếu cần **giữ chính xác byte**, hãy để nguyên dữ liệu ban đầu và không áp dụng chuẩn hóa.

### Các bẫy khi chuẩn hóa

**Sai thứ tự:**

```java
// SAI khi muốn CRLF tương đương một LF:
String wrong = original.replace('\r', '\n');
// CRLF -> LF LF, có thể vô tình thêm một dòng rỗng.
```

**Tự cắt khoảng trắng:**

```java
// Khác mục tiêu chuẩn hóa newline:
String changed = original.strip();
```

`strip()` còn loại cả whitespace đầu/cuối theo quy tắc Unicode, bao gồm dấu xuống dòng cuối; có thể phá hủy định dạng tài liệu hoặc dữ liệu trống có ý nghĩa. `trim()` cũng không phải phép chuẩn hóa kiểu xuống dòng.

**Phân chia rồi ghép lại:**

```java
String rebuilt = String.join("\n", original.lines().toList());
```

Phép này thường mất dấu kết thúc dòng cuối và không thể biết chỗ nào vốn là CR, CRLF hay LF. Có ích khi bạn **chủ động dựng lại file theo một định dạng mới**, nhưng không thể tuyên bố giữ nguyên dữ liệu.

**Regex `\R` có phạm vi rộng hơn:**

```java
String normalizedUnicodeBreaks = original.replaceAll("\\R", "\n");
```

Trong Java regex, `\R` nhận nhiều dạng line-break Unicode hơn ba kiểu CRLF/CR/LF. Chỉ dùng nếu yêu cầu rõ ràng là thống nhất cả những dấu ngắt Unicode như NEL, LS, PS; nếu không, nó có thể thay đổi các ký tự mà bạn không định coi là kết thúc dòng. `replaceAll` dùng regex và replacement, còn `replace` là thay chuỗi/ký tự theo nghĩa literal.

## 9. Vì sao `String.split("\n")` có thể sai với dữ liệu thực tế?

Một phản xạ thường gặp là đọc file văn bản rồi:

```java
String[] lines = text.split("\n");
```

Điều đó chỉ phù hợp nếu bạn **biết chắc** nguồn dùng LF và cách xử lý dòng cuối của `String.split` là điều mình muốn. Những khác biệt quan trọng:

| Đầu vào | `split("\n")` | `String.lines()` |
|---|---|---|
| `"A\nB"` | `["A", "B"]` | Hai dòng `A`, `B` |
| `"A\r\nB"` | Phần tử đầu là `"A\r"` | Hai dòng `A`, `B` không kèm CR |
| `"A\rB"` | Một phần tử gồm cả chuỗi | Hai dòng `A`, `B` |
| `"A\n"` | Mặc định bỏ chuỗi trống cuối | Một dòng `A` |
| `"\n"` | Mặc định có thể trả mảng không phần tử | Một dòng rỗng |
| `""` | `split` có thể trả một phần tử rỗng | Không có dòng |

`String.split(regex)` hoạt động theo **regular expression**, không theo khái niệm “dòng Java” nếu mẫu regex không đủ. Đặc biệt, `split(regex)` dùng giới hạn `0` theo mặc định nên **loại các chuỗi trống ở cuối**. Muốn giữ các mục rỗng cuối, truyền giới hạn âm, ví dụ `text.split("\\R", -1)`; nhưng lúc đó có thể tạo **một mục trống sau terminator cuối**, khác ngữ nghĩa của `String.lines()`.

Với nhu cầu duyệt dòng đơn thuần, ưu tiên `String.lines()` (chuỗi trong bộ nhớ), `BufferedReader.readLine()` hoặc `Files.lines(...)`. Với nhu cầu giữ nguyên dấu ngắt, dùng đọc văn bản thô rồi tự phân tích và lưu lại dấu ngắt tương ứng.

## 10. Dữ liệu trộn CRLF, LF và CR: nên xử lý thế nào?

Giả sử nhập file từ nhiều hệ thống có cả ba kiểu. Trước hết xác định **hợp đồng đầu vào**:

- Nếu file chỉ cần lấy **dữ liệu từng dòng**, `BufferedReader.readLine()` hoặc `Files.lines` sẽ phân tách ba kiểu này mà không cần bạn tự thay ký tự.
- Nếu sau đó phải ghi ra định dạng LF nhất quán, có thể chuẩn hóa trên chuỗi hoặc dùng `BufferedWriter` ghi từng dòng và literal `"\n"`. Quyết định có cần dấu kết thúc dòng cuối hay không.
- Nếu phải bảo toàn **chính xác** kiểu newline của từng dòng, không dùng các API đã bỏ terminator để lưu nội dung duy nhất; hãy phân tích văn bản thô và ghi lại cả terminator cho từng dòng.
- Nếu file được mở dưới dạng bytes để xử lý dữ liệu có cấu trúc, trước tiên phải biết **charset**. Không tìm dấu `0A`/`0D` bằng cách quét mù dữ liệu nhị phân hoặc UTF-16 mà không hiểu encoding.
- Nếu dữ liệu theo **giao thức**, dùng dấu kết thúc dòng giao thức yêu cầu, dù khác với `System.lineSeparator()` của máy chạy.

### Một parser cần giữ nguyên loại terminator

Về mặt dữ liệu, đừng chỉ lưu danh sách `List<String> lines` khi ứng dụng cần “round-trip” 100%:

```text
Nội dung gốc:      "A\r\nB\nC\rD"
Các đoạn logic:    ("A", "\r\n"), ("B", "\n"), ("C", "\r"), ("D", "")
```

Ở ví dụ này, mỗi cặp lưu **nội dung dòng** và **terminator gốc** (`""` cho dòng cuối không có dấu kết thúc). Ghép lần lượt nội dung và terminator của từng cặp sẽ tái lập nguyên chuỗi. Nếu cần bảo toàn **byte** gốc thay vì chỉ ký tự, còn phải giữ nguyên charset/BOM và byte stream ban đầu hoặc một cơ chế mã hóa chính xác tương đương.

Không tự loại bỏ BOM (Byte Order Mark), thay charset hoặc trim whitespace trong lúc “chỉ chuẩn hóa line ending”; những biến đổi đó nằm ngoài nhiệm vụ xử lý LF/CRLF/CR.

## 11. Dòng, token và cấu trúc tài liệu

Bài nằm trong nhóm **Dòng và token**, nên cần tách rõ:

- **Dòng (line):** một đoạn văn bản được phân ranh bởi line terminator theo quy tắc API.
- **Token:** một đơn vị do **delimiter** (khoảng trắng, dấu phẩy, dấu chấm phẩy, regex, cú pháp parser...) xác định. Một dòng có thể chứa nhiều token, và token cũng có thể trải qua dòng nếu parser/định dạng cho phép.
- `Scanner` mặc định dùng whitespace delimiter để lấy token qua `next()`, `nextInt()`, ...; `nextLine()` lại lấy phần còn lại của dòng hiện tại và tiêu thụ dấu kết thúc dòng.
- `BufferedReader.readLine()` không phân tách token; nó chỉ lấy một dòng. Bạn có thể parse token trong chuỗi dòng sau đó.

Ví dụ dữ liệu `"Ada 42\r\nLinh 7\n"` có hai dòng nhưng bốn token theo whitespace (Ada, 42, Linh, 7). Khi chuyển từ đọc token sang đọc dòng, cần chủ động xử lý delimiter còn lại như ví dụ 3.

Một lưu ý quan trọng với **CSV**: nếu CSV cho phép ký tự newline bên trong trường đặt trong dấu ngoặc kép, **một bản ghi logic không nhất thiết là một dòng vật lý**. Các parser CSV nên tuân theo cú pháp CSV chứ không dựa đơn thuần vào `readLine()` rồi `split(",")`. Tương tự, newline trong chuỗi literal của một định dạng cấu trúc có thể mang nghĩa riêng.

## 12. Những lỗi thường gặp

1. **Gọi CRLF là “một ký tự Java”.** Nó gồm hai `char` (`\r` rồi `\n`) nhưng nhiều API đọc dòng coi đó là một terminator.
2. **Dùng `split("\n")` cho CRLF.** Kết quả có thể còn `\r` ở cuối token/dòng, khiến `equals` và parse dữ liệu bị sai.
3. **Giả sử `readLine()` giữ newline.** Nó loại terminator; sau khi đọc theo dòng, bạn không thể suy ra chính xác terminator cũ từ nội dung trả về.
4. **Nhầm dòng rỗng với EOF.** Với `BufferedReader.readLine()`, `""` là dòng rỗng; `null` là hết input.
5. **Dùng `System.lineSeparator()` để tách file từ hệ khác.** Đây là quy ước nền tảng nơi chương trình đang chạy, không phải metadata của file đầu vào.
6. **Chuẩn hóa CR trước CRLF.** `\r\n` sẽ trở thành `\n\n`, làm tăng số dòng rỗng.
7. **Thay dấu xuống dòng bằng `strip()`/`trim()`.** Những hàm này còn loại bỏ whitespace đầu/cuối và có thể làm mất trailing newline.
8. **Tự động đổi hết file khi chỉ cần đọc.** Nếu parser đã nhận diện CRLF/LF/CR, đọc dòng không yêu cầu ghi file lại; ghi lại có thể tạo diff, thay đổi hash và phá tính nguyên bản.
9. **Giữ các dòng nhưng bỏ quy tắc EOF.** `"A"` và `"A\n"` có cùng dòng nội dung nhưng khác representation và có thể khác hợp đồng định dạng.
10. **Quét byte `0A` để tách dòng trong mọi charset.** UTF-16 và một số dạng mã hóa khác có cấu trúc byte khác; hãy giải mã đúng trước hoặc phân tích byte theo định dạng.
11. **Ngầm dùng `println` khi giao thức yêu cầu CRLF.** Kết quả sẽ phụ thuộc hệ điều hành thay vì quy tắc giao thức.
12. **Đánh đồng dòng vật lý với bản ghi logic.** CSV, nội dung nhiều dòng trong quoted string và các định dạng có cú pháp riêng cần parser phù hợp.
13. **Đoán newline chỉ theo hệ điều hành.** File có thể qua Git, mạng, editor và tool; hãy kiểm tra dữ liệu thực tế hoặc dùng contract được công bố.
14. **Thử chuẩn hóa mọi ký tự trông như “ngắt dòng”.** `\R` trong regex rộng hơn tập LF/CR/CRLF; chỉ dùng khi muốn xử lý toàn bộ nhóm line-break Unicode.

## 13. Checklist chọn giải pháp

| Nhu cầu | Cách làm phù hợp |
|---|---|
| Đọc từng dòng không quan tâm dấu kết thúc gốc | `BufferedReader.readLine()` hoặc `Files.lines(path, charset)` |
| Xử lý dòng trong một chuỗi đang có | `String.lines()` |
| Giữ nguyên CR, LF, CRLF trong văn bản | `Files.readString(path, charset)`, tránh tách/ghép lại nếu cần exact |
| Giữ nguyên **byte** file | `Files.readAllBytes(path)` hoặc I/O byte, không thực hiện chuyển mã hay viết lại |
| Chuẩn hóa ba kiểu sang LF trên chuỗi | `replace("\r\n","\n").replace('\r','\n')` |
| Ghi xuống dòng theo hệ điều hành | `BufferedWriter.newLine()`, `System.lineSeparator()` |
| Ghi dữ liệu theo chuẩn LF | Ghi literal `"\n"` |
| Ghi theo chuẩn CRLF | Ghi literal `"\r\n"` |
| Đếm các dòng text theo hành vi Java 11+ | `String.lines().count()`; chú ý quy ước dòng cuối |
| Xử lý token theo delimiter | `Scanner` hoặc parser chuyên dụng; tách riêng khái niệm line và token |

## 14. Tự kiểm tra kiến thức

**Câu 1.** `"x\r\ny"` gồm mấy `char` và mấy dòng theo `String.lines()`?  
**Đáp án:** 4 `char` (`x`, CR, LF, `y`) và 2 dòng.

**Câu 2.** `BufferedReader.readLine()` gặp `"\n"` ở đầu file thì trả gì?  
**Đáp án:** lần đọc đầu trả chuỗi rỗng `""`; lần tiếp theo nếu file hết sẽ trả `null`.

**Câu 3.** Vì sao `System.lineSeparator()` không dùng để đoán dấu xuống dòng của file?  
**Đáp án:** giá trị của nó dựa trên môi trường chương trình đang chạy, còn nội dung file có thể được tạo trên bất kỳ hệ thống hay quy ước nào.

**Câu 4.** Chuỗi `"A\r\nB"` đi qua `replace('\r','\n')` thì thế nào?  
**Đáp án:** thành `"A\n\nB"`, vô tình xuất hiện một dòng rỗng. Hãy thay `"\r\n"` trước rồi mới thay CR đơn.

**Câu 5.** `"A\n"` và `"A"` có `String.lines().count()` khác nhau không?  
**Đáp án:** không; cả hai có một dòng, nhưng độ dài và byte representation khác nhau.

**Câu 6.** Nếu dữ liệu cần Round-trip chính xác LF, CRLF, CR, có nên chỉ lưu kết quả `Files.readAllLines`?  
**Đáp án:** không. Cần giữ văn bản thô hoặc giữ nội dung dòng **kèm terminator tương ứng**, và giữ charset/byte gốc nếu phải tái lập bytes chính xác.

**Câu 7.** `Scanner.nextLine()` ngay sau `Scanner.nextInt()` có thể trả chuỗi rỗng dù còn dữ liệu?  
**Đáp án:** có, vì nó có thể tiêu thụ phần còn lại (rỗng) của dòng chứa token số, rồi lần gọi kế tiếp mới đọc dòng sau.

## Kết luận

**LF** là `\n` (U+000A), **CR** là `\r` (U+000D), **CRLF** là cặp `\r\n`. Sự khác biệt tồn tại trong nội dung chuỗi và byte file dù hiển thị có thể giống nhau. Trong Java, `BufferedReader.readLine()`, `String.lines()` và các API `Files` đọc theo dòng thường hiểu cả ba dạng và bỏ dấu kết thúc dòng. Khi cần **bảo toàn nguyên dạng**, đọc và giữ dữ liệu thô; khi cần **chuẩn hóa**, chọn quy ước đích có chủ ý và xử lý CRLF trước CR. Khi **ghi**, chọn dấu ngắt theo định dạng hay giao thức yêu cầu, không mặc định dùng quy ước của môi trường chạy.

### Tài liệu Java SE 17

- [`BufferedReader.readLine()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html)
- [`String.lines()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [`Files.readAllLines()`, `Files.lines()`, `Files.readString()`, `Files.writeString()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [`BufferedWriter.newLine()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedWriter.html)
- [`System.lineSeparator()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html)
- [`Scanner.nextLine()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html)
- [`Pattern` (`\R` trong regex Java)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-003">BufferedWriter.newLine và System.lineSeparator</a>

<details>
<summary>Xem chi tiết</summary>

# BufferedWriter.newLine và System.lineSeparator trong Java

Khi ghi văn bản nhiều dòng, ta phải phân biệt **nội dung ký tự**, **dấu kết thúc dòng (line separator)** và **bộ đệm ghi**. Hai API Java có liên quan trực tiếp là `BufferedWriter.newLine()` và `System.lineSeparator()`:

- `BufferedWriter.newLine()` **ghi một chuỗi ký tự kết thúc dòng** theo quy ước của nền tảng vào luồng ký tự đang mở.
- `System.lineSeparator()` **trả về một `String`** chứa chuỗi kết thúc dòng theo quy ước của nền tảng; chỉ gọi phương thức này **không ghi gì**.
- Cả hai phù hợp khi muốn tạo file văn bản **theo quy ước xuống dòng của môi trường chạy**. Nếu một định dạng/giao thức bắt buộc đúng `\n` hoặc `\r\n`, phải ghi chính xác chuỗi do chuẩn đó yêu cầu, không chọn tự động theo hệ điều hành.

Bài học nằm trong chủ đề **Dòng và token** của Java I/O: hiểu cách tách các dòng văn bản và không làm sai dữ liệu khi ghi, đọc lại hoặc trao đổi file giữa các hệ điều hành.

## 1. Line separator là gì?

Trong văn bản, một dấu xuống dòng thường được biểu diễn bằng một hoặc nhiều ký tự điều khiển:

| Biểu diễn Java | Tên | Thường gặp |
| --- | --- | --- |
| `"\n"` | LF, Line Feed, mã `U+000A` | Unix/Linux, macOS hiện đại |
| `"\r\n"` | CRLF, Carriage Return + Line Feed | Windows |
| `"\r"` | CR, Carriage Return, mã `U+000D` | Một số định dạng và hệ thống cũ |

Ví dụ hai chuỗi này hiển thị tương tự trên nhiều công cụ, nhưng **khác nhau ở số ký tự và byte**:

```java
String lf = "Một\nHai";      // "Một" + LF + "Hai"
String crlf = "Một\r\nHai";  // "Một" + CRLF + "Hai"
```

Đây là hai khai báo minh họa có thể đặt trong một phương thức Java. Số ký tự điều khiển và byte thực tế phụ thuộc vào cách mã hóa văn bản ra file; chẳng hạn khi mã hóa UTF-8, LF chiếm 1 byte `0A`, còn CRLF chiếm 2 byte `0D 0A`.

**Lưu ý:** “Dòng mới” trong văn bản không phải một đối tượng Java riêng. `BufferedWriter` chỉ ghi ký tự; `BufferedReader` có thể diễn giải các dấu kết thúc dòng khi đọc. Một dòng cuối không kết thúc bằng dấu xuống dòng vẫn là nội dung dòng hợp lệ trong nhiều định dạng văn bản.

## 2. `BufferedWriter.newLine()` làm gì?

`BufferedWriter` là một `Writer` có bộ đệm để giảm số lần ghi xuống luồng bên dưới. Khi gọi:

```java
writer.newLine();
```

`BufferedWriter` ghi chuỗi kết thúc dòng mà nó lấy theo quy ước nền tảng, được Java mô tả qua thuộc tính hệ thống `line.separator`. Điều này **không bảo đảm luôn là một ký tự `'\n'`**: trên Windows thông thường là `"\r\n"`, trên Unix/Linux thông thường là `"\n"`.

Đoạn minh họa:

```java
writer.write("Dòng thứ nhất");
writer.newLine();
writer.write("Dòng thứ hai");
```

Nếu môi trường dùng LF, kết quả về mặt ký tự là `"Dòng thứ nhất\nDòng thứ hai"`. Nếu dùng CRLF, nó là `"Dòng thứ nhất\r\nDòng thứ hai"`. `newLine()` **không tự động thêm khoảng trắng**, **không ghi nội dung của dòng kế tiếp** và **không gọi `flush()`** chỉ vì đã xuống dòng.

### Cú pháp và ngoại lệ

`newLine()` là phương thức thể hiện trong `java.io.BufferedWriter`, trả về `void` và có thể ném `IOException`:

```java
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

// Phương thức minh họa; tham số đích là Writer đã được mở.
static void writeTwoLines(Writer destination) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(destination)) {
        writer.write("Alpha");
        writer.newLine();
        writer.write("Beta");
    }
}
```

**Quyền sở hữu luồng:** đóng `BufferedWriter` sẽ đóng `Writer` bên dưới. Phương thức này chỉ nên dùng khi nó **được phép đóng** `destination`. Nếu tầng gọi vẫn cần dùng luồng đã truyền vào, cần thống nhất rõ chủ sở hữu và vòng đời.

### `newLine()` có tự tạo dòng trống không?

Về cơ bản, `newLine()` chỉ ghi **một dấu kết thúc dòng**. Nếu viết:

```java
writer.write("A");
writer.newLine();
writer.newLine();
writer.write("B");
```

sẽ xuất hiện một dòng trống giữa A và B. Ngược lại, nếu gọi `newLine()` sau dòng cuối cùng, file sẽ **kết thúc bằng dấu xuống dòng**. Có cần dấu xuống dòng ở cuối file hay không phụ thuộc quy ước của định dạng, công cụ hoặc dự án.

## 3. `System.lineSeparator()` trả về gì?

`System.lineSeparator()` là phương thức tĩnh của `java.lang.System`, trả về chuỗi dấu xuống dòng phụ thuộc nền tảng:

```java
String separator = System.lineSeparator();

String content = "Alpha" + separator + "Beta";
// Chỉ tạo String, chưa ghi ra file hoặc OutputStream.
```

Có thể kết hợp với `StringBuilder`, `String.join` hoặc một `Writer` bất kỳ:

```java
String content = String.join(
        System.lineSeparator(),
        "Dòng 1", "Dòng 2", "Dòng 3"
);
```

Hoặc:

```java
writer.write("Dòng 1");
writer.write(System.lineSeparator());
writer.write("Dòng 2");
```

Các đoạn minh họa này giả định `writer` đã tồn tại. `System.lineSeparator()` thích hợp khi cần **tạo một giá trị String chứa các dấu xuống dòng theo môi trường**, còn `BufferedWriter.newLine()` ngắn gọn hơn khi đang ghi trực tiếp bằng `BufferedWriter`.

Theo tài liệu Java, giá trị `System.lineSeparator()` dựa trên giá trị ban đầu của thuộc tính `line.separator` và giữ ổn định trong thời gian chạy. **Không sửa `System.setProperty("line.separator", ...)` ở giữa chương trình để cố đổi định dạng file**: đây không phải cách thiết kế định dạng đầu ra đáng tin cậy. Khi cần LF hoặc CRLF cố định, hãy dùng chuỗi tường minh.

## 4. So sánh và lựa chọn

| Tiêu chí | `BufferedWriter.newLine()` | `System.lineSeparator()` |
| --- | --- | --- |
| Loại API | Phương thức của một `BufferedWriter` | Phương thức tĩnh của `System` |
| Kết quả | **Ghi** dấu kết thúc dòng vào writer | **Trả về `String`**, chưa ghi |
| Giá trị trả về | `void` | `String` |
| Ngoại lệ thường gặp | `IOException` khi ghi | Không khai báo `IOException` |
| Khi dùng | Đang ghi dòng qua `BufferedWriter` | Đang ghép `String` hoặc ghi qua API khác |
| Tính phụ thuộc môi trường | Theo line separator của môi trường | Theo line separator của môi trường |
| Khi phải xuất LF hoặc CRLF cố định | Không phải lựa chọn phù hợp | Không phải lựa chọn phù hợp |

Nếu chương trình **ghi nhiều dòng trực tiếp vào file**, `BufferedWriter.newLine()` dễ đọc:

```java
writer.write("Tên: An");
writer.newLine();
writer.write("Tuổi: 20");
```

Nếu chương trình **xây dựng nội dung văn bản trước**, `System.lineSeparator()` tiện lợi:

```java
String report = "Tên: An"
        + System.lineSeparator()
        + "Tuổi: 20";
```

Khi cả hai dùng cùng quy ước nền tảng trong một chương trình Java thông thường, nội dung dấu xuống dòng tương ứng là như nhau. Khác biệt quan trọng là **một API thực hiện ghi**, còn API kia **cung cấp chuỗi ký tự**.

## 5. Chương trình Java 17+ hoàn chỉnh và chạy được

Ví dụ dưới đây ghi ba dòng vào `StringWriter` qua `BufferedWriter`. Sau khi writer được đóng, chương trình kiểm tra chuỗi nhận được có giống cách ghép bằng `System.lineSeparator()` không, xác nhận chính xác ba dòng bằng `BufferedReader.readLine()`, và kiểm tra có thêm dấu xuống dòng ở cuối hay không.

Lưu đúng tên file **`LineSeparatorDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class LineSeparatorDemo {

    public static void main(String[] args) throws IOException {
        StringWriter destination = new StringWriter();

        try (BufferedWriter writer = new BufferedWriter(destination)) {
            writer.write("Dòng một");
            writer.newLine();
            writer.write("Dòng hai");
            writer.newLine();
            writer.write("Dòng ba");
        }

        String actual = destination.toString();
        String expected = String.join(
                System.lineSeparator(),
                "Dòng một", "Dòng hai", "Dòng ba"
        );

        System.out.println("Matches platform separator: " +
                actual.equals(expected));
        System.out.println("Logical line count: " +
                countLines(actual));
        System.out.println("Ends with separator: " +
                actual.endsWith(System.lineSeparator()));
    }

    private static int countLines(String text) throws IOException {
        int count = 0;

        try (BufferedReader reader =
                     new BufferedReader(new StringReader(text))) {
            while (reader.readLine() != null) {
                count++;
            }
        }

        return count;
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 LineSeparatorDemo.java
java LineSeparatorDemo
```

**Stdout trên các môi trường thông thường dùng LF hoặc CRLF:**

```text
Matches platform separator: true
Logical line count: 3
Ends with separator: false
```

**Vì sao ra kết quả này?**

1. `writer.newLine()` được gọi **hai lần**, giữa các dòng, nên chuỗi có ba dòng logic.
2. `String.join(System.lineSeparator(), ...)` cũng chèn đúng hai separator tương ứng; so sánh trả `true`.
3. `BufferedReader.readLine()` trả lần lượt `"Dòng một"`, `"Dòng hai"`, `"Dòng ba"`, rồi `null` khi hết dữ liệu. Nó bỏ ký tự kết thúc dòng ra khỏi từng kết quả.
4. Dòng cuối `"Dòng ba"` không được theo sau bởi `newLine()` nên `endsWith(separator)` là `false`.

Ví dụ dùng `StringWriter` để quan sát nội dung mà không cần tạo file thật. `StringWriter` lưu ký tự trong bộ nhớ; đóng `BufferedWriter` sẽ gọi đóng writer dưới, nhưng với `StringWriter`, ta vẫn có thể lấy nội dung đã ghi qua `toString()`.

### Nhìn rõ LF và CRLF khi gỡ lỗi

Không nên dùng mắt để đoán separator vì console hoặc editor có thể hiển thị cả LF và CRLF gần như giống nhau. Với chuỗi văn bản đã có:

```java
String visible = actual
        .replace("\r", "\\r")
        .replace("\n", "\\n");
System.out.println(visible);
```

Đặt đoạn mã vào `main` của ví dụ sau khi tạo `actual`. Với LF, sẽ thấy:

```text
Dòng một\nDòng hai\nDòng ba
```

Với CRLF, sẽ thấy:

```text
Dòng một\r\nDòng hai\r\nDòng ba
```

Đây là **hiển thị minh họa phụ thuộc nền tảng**, không phải hai dòng stdout xuất hiện đồng thời.

## 6. Ghi file UTF-8 theo dòng

`BufferedWriter` xử lý **ký tự và bộ đệm**; charset như UTF-8 được xác định khi nối tới luồng byte của file. Với Java NIO, cách rõ ràng là:

```java
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public static void writeReport(Path path) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(
            path, StandardCharsets.UTF_8)) {
        writer.write("Tiêu đề");
        writer.newLine();
        writer.write("Nội dung tiếng Việt");
        writer.newLine();
    }
}
```

Đây là **phương thức minh họa**, phải đặt bên trong một class. `Files.newBufferedWriter(path, StandardCharsets.UTF_8)` tạo buffered writer mã hóa ký tự theo UTF-8, còn `writer.newLine()` chọn ký tự xuống dòng theo môi trường chạy. Hai quyết định **độc lập**:

- **Charset UTF-8** quyết định cách chuyển ký tự Unicode thành byte.
- **Line separator** quyết định chèn LF, CRLF hay chuỗi dấu xuống dòng được quy ước.

Với tùy chọn mặc định, `Files.newBufferedWriter` tạo file nếu chưa có hoặc cắt ngắn nội dung nếu file đã tồn tại. Cần xác định chính sách ghi đè nếu báo cáo cũ phải được lưu lại.

Trong ví dụ này, vì gọi `newLine()` sau dòng `"Nội dung tiếng Việt"`, file **có dấu kết thúc dòng ở cuối**. Điều đó có thể là quy ước mong muốn đối với file dạng dòng; nếu định dạng đầu ra yêu cầu không có dấu xuống dòng sau nội dung cuối, hãy bỏ lần gọi đó.

## 7. Buffer, `flush()` và `close()`

`BufferedWriter` giữ dữ liệu trong bộ đệm; không có bảo đảm rằng mỗi lệnh `write` hoặc `newLine` lập tức tạo thay đổi quan sát được ở file đích.

- `writer.write(...)` ghi ký tự vào writer; nội dung có thể còn nằm trong bộ đệm.
- `writer.newLine()` ghi chuỗi xuống dòng vào writer, **không tự động flush**.
- `writer.flush()` đẩy dữ liệu đang đệm qua các tầng writer bên dưới. Nó **không đồng nghĩa** với bảo đảm dữ liệu đã được ghi bền vững xuống thiết bị lưu trữ.
- `writer.close()` đóng writer, flush phần còn đệm trong quá trình đóng, và giải phóng tài nguyên. `try-with-resources` giúp gọi close kể cả khi có ngoại lệ.

Ví dụ khi cần quan sát dữ liệu trước khi đóng writer:

```java
writer.write("Trạng thái: đang chạy");
writer.newLine();
writer.flush();  // Cần khi bên nhận phải nhìn thấy dữ liệu ngay.
```

Không nên `flush()` sau **mọi** dòng nếu không cần, vì có thể làm giảm lợi ích của buffering. Với file thông thường, đóng đúng bằng `try-with-resources` ở cuối tác vụ thường là đủ.

## 8. Khi nào phải ghi `"\n"` hoặc `"\r\n"` tường minh?

Đây là khác biệt đặc biệt quan trọng khi chương trình Java xuất dữ liệu để hệ thống khác đọc. Không phải định dạng nào cũng sử dụng line separator của máy đang chạy.

**Khi định dạng hoặc protocol yêu cầu một kiểu kết thúc dòng cụ thể**, hãy ghi kiểu đó đúng theo chuẩn:

```java
writer.write("line 1");
writer.write("\n");    // LF cố định, bất kể hệ điều hành.
writer.write("line 2");
```

Hoặc khi giao thức yêu cầu CRLF:

```java
writer.write("Header: value");
writer.write("\r\n");  // CRLF cố định.
```

Các đoạn trên là minh họa cần `writer` đã mở. Ví dụ header HTTP/1.1 ở tầng giao thức có quy tắc CRLF riêng; trong ứng dụng thực tế nên để thư viện HTTP quản lý định dạng thông điệp thay vì tự ghép chuỗi protocol.

Các tình huống điển hình:

| Nhu cầu | Cách chọn dấu xuống dòng |
| --- | --- |
| File ghi chú, báo cáo trên máy người dùng, theo quy ước nền tảng | `writer.newLine()` hoặc `System.lineSeparator()` |
| File được chuẩn hóa LF để dùng cùng trong nhiều môi trường | `"\n"` tường minh hoặc chuẩn hóa output |
| Protocol yêu cầu CRLF | `"\r\n"` tường minh hoặc API protocol chuyên dụng |
| File đầu ra cần giữ nguyên kết thúc dòng của file nguồn | Phải đọc và lưu lại chính xác separator đã có, không đọc theo `readLine()` rồi tự chọn lại |
| Tạo `String` nhiều dòng cho ứng dụng theo nền tảng | `String.join(System.lineSeparator(), lines)` |
| Dữ liệu dạng nhị phân | Dùng luồng byte; `newLine()` không có ý nghĩa |

**Không dùng `System.lineSeparator()` như một cách dự đoán line ending của file bên ngoài.** Một file tạo trên Linux vẫn có thể chứa CRLF, và một file tạo trên Windows vẫn có thể chứa LF. Nguồn dữ liệu, chuẩn định dạng hoặc nội dung file mới quyết định ký tự có mặt trong file đó.

## 9. Đọc lại bằng `BufferedReader.readLine()` và giới hạn

`BufferedReader.readLine()` nhận diện các dòng kết thúc bởi LF, CR hoặc CRLF. Hàm trả chuỗi **không chứa ký tự kết thúc dòng**; `null` biểu thị EOF khi không còn dòng nào.

```java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý nội dung từng dòng, không kèm LF hay CRLF.
        System.out.println(line);
    }
}
```

Đoạn minh họa giả định `path` và các import `BufferedReader`, `Files`, `StandardCharsets` tồn tại. Một điểm cần nhớ là **đọc từng dòng làm mất thông tin separator ban đầu**:

```text
Nguồn 1: "A\nB"
Nguồn 2: "A\r\nB"
readLine() trên cả hai: "A", "B"
```

Vì vậy, nếu đọc bằng `readLine()` rồi ghi lại bằng `newLine()`, bạn có thể **chuẩn hóa hoặc thay đổi line ending**, dù chữ hiển thị không đổi. Đối với tác vụ cần giữ nguyên byte, ký tự xuống dòng hỗn hợp hoặc chữ ký/checksum của file, hãy đọc byte/ký tự và bảo toàn đúng dữ liệu gốc, không coi `readLine()` là phương pháp sao chép y nguyên.

Ngoài ra, dòng cuối **không có** dấu kết thúc dòng vẫn được `readLine()` trả về trước EOF, miễn có nội dung.

## 10. So với `PrintWriter.println()`, `System.out.println()` và `%n`

Một số API Java cũng cung cấp thao tác xuống dòng thuận tiện nhưng có hành vi riêng:

| API | Mục đích | Lưu ý |
| --- | --- | --- |
| `BufferedWriter.newLine()` | Ghi line separator vào `BufferedWriter` | Có `IOException`; không tự flush |
| `System.lineSeparator()` | Trả về `String` separator | Không thực hiện I/O |
| `PrintWriter.println(...)` | Ghi giá trị và kết thúc dòng | `PrintWriter` thường lưu cờ lỗi thay vì ném `IOException` trực tiếp; kiểm tra `checkError()` khi cần |
| `System.out.println(...)` | In giá trị/dòng ra chuẩn stdout | Thường theo quy ước môi trường |
| `String.format("A%nB")` | Định dạng chuỗi với dấu xuống dòng theo nền tảng | `%n` là format specifier, khác với `\n` cố định |

`PrintWriter` có các constructor/tùy chọn auto-flush; hành vi tự flush khi `println` phụ thuộc việc auto-flush được bật. **Không suy ra rằng `BufferedWriter.newLine()` có auto-flush như một số `PrintWriter`.**

Nếu cần xử lý `IOException` rõ ràng khi ghi file và muốn biết lúc nào flush, `BufferedWriter` là lựa chọn dễ kiểm soát.

## 11. Những lỗi thường gặp

| Lỗi | Hậu quả | Cách làm phù hợp |
| --- | --- | --- |
| Nghĩ `newLine()` luôn ghi `"\n"` | Kết quả có thể khác giữa Windows và Linux | Dùng `newLine()` khi muốn quy ước nền tảng; dùng literal khi chuẩn bắt buộc |
| Gọi `System.lineSeparator()` một mình rồi chờ file xuống dòng | Không có ký tự nào được ghi | `writer.write(System.lineSeparator())` hoặc `writer.newLine()` |
| Dùng `newLine()` để xuất định dạng yêu cầu CRLF cố định | File có thể sai trên môi trường LF | Ghi chính xác `"\r\n"` hoặc dùng thư viện giao thức |
| Gọi `newLine()` rồi tưởng dữ liệu đã flush | Người đọc khác có thể chưa thấy dòng | `flush()` ở điểm cần đồng bộ; đóng đúng |
| Đọc `readLine()` và ghi lại mà đòi giữ nguyên byte | Mất thông tin CR/LF/CRLF nguyên bản | Đọc/ghi bảo toàn chính xác ký tự hoặc byte |
| Coi UTF-8 quyết định LF/CRLF | Nhầm charset với line ending | Chọn charset và separator độc lập |
| Ghi `newLine()` sau mọi dòng mà không kiểm tra chuẩn | Có thể thêm newline cuối không mong muốn | Xác định chính sách dấu kết thúc dòng cuối |
| Dùng `"\n"` rồi khẳng định file luôn hiển thị đúng mọi ứng dụng | Một số hệ thống cũ/tool đặc thù kỳ vọng quy ước khác | Tuân thủ định dạng hoặc nhu cầu ứng dụng nhận |
| Thêm `flush()` sau từng lệnh không cần thiết | Giảm hiệu quả đệm và tăng I/O | Flush theo điểm đồng bộ có chủ đích |

## 12. Bài tập tự kiểm tra

1. **Trên Windows thông thường**, sau `writer.write("A"); writer.newLine(); writer.write("B");` thì chuỗi ký tự sẽ tương đương dạng nào? **`"A\r\nB"`**, nếu môi trường dùng CRLF.
2. **Trên Linux thông thường**, chuỗi tương đương là gì? **`"A\nB"`**, nếu môi trường dùng LF.
3. **Chỉ gọi `System.lineSeparator()` có ghi ra file không?** **Không**; phương thức trả về `String`.
4. **`newLine()` có tự gọi `flush()` không?** **Không**; gọi `flush()` tại thời điểm cần đẩy dữ liệu đệm, hoặc đóng writer đúng cách.
5. **Một chuẩn yêu cầu LF cố định trên mọi OS: dùng gì?** **Ghi `"\n"` rõ ràng** hoặc API xuất theo đúng chuẩn.
6. **Đọc file có CRLF bằng `readLine()` rồi ghi lại bằng `newLine()` trên Linux có giữ nguyên byte không?** **Không bảo đảm**; các ký tự kết thúc dòng có thể chuyển thành LF.
7. **UTF-8 có làm cho `newLine()` luôn dùng LF không?** **Không**; encoding byte và line ending là hai quyết định riêng.
8. **Muốn file text luôn có newline cuối cùng, đặt `newLine()` ở đâu?** **Sau cả dòng cuối**, theo quy định định dạng của file.

**Quy tắc thực hành:** dùng `BufferedWriter.newLine()` để **ghi xuống dòng theo nền tảng**, dùng `System.lineSeparator()` để **lấy chuỗi xuống dòng theo nền tảng**, và dùng `"\n"` hoặc `"\r\n"` **tường minh** khi đặc tả đầu ra yêu cầu chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-004">BufferedReader.lines và quyền đóng Reader</a>

<details>
<summary>Xem chi tiết</summary>

# BufferedReader.lines() và quyền đóng Reader trong Java

`BufferedReader.lines()` biến một `BufferedReader` thành `Stream<String>` để xử lý các dòng văn bản bằng Stream API. Cú pháp rất tiện cho lọc, biến đổi, đếm và tìm kiếm dòng:

~~~java
long count = reader.lines()
        .filter(line -> !line.isBlank())
        .count();
~~~

Tuy nhiên, cần phân biệt **quá trình tiêu thụ stream** và **quyền sở hữu tài nguyên I/O**. Stream được tạo bởi `BufferedReader.lines()` đọc dữ liệu từ cùng `BufferedReader`, nhưng **đóng stream không tự đóng reader**. Nếu reader đọc file hoặc socket, người sở hữu reader vẫn phải đóng reader khi hoàn tất. Điều này khác với `Files.lines(path, charset)`: stream do `Files.lines` trả về gắn với tài nguyên file và phải được đóng để giải phóng tài nguyên đó.

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích `BufferedReader.lines()` sinh `Stream<String>` **lazy** như thế nào và khi nào thao tác I/O thật sự diễn ra.
- Phân biệt dòng rỗng `""`, dòng có khoảng trắng và `null` của `readLine()`; hiểu ký tự kết thúc dòng không còn trong phần tử stream.
- Đặt `try-with-resources` quanh đúng đối tượng sở hữu tài nguyên, kể cả khi stream dừng sớm với `findFirst()` hoặc `limit()`.
- Chứng minh bằng chương trình chạy được rằng `stream.close()` không tự gọi `BufferedReader.close()`.
- Phân biệt `BufferedReader.lines()` với `Files.lines()` về quyền đóng tài nguyên.
- Xử lý `UncheckedIOException` phát sinh khi đọc lazy, tránh trả một stream gắn với reader đã đóng và tránh dùng reader đồng thời với terminal operation trên stream.
- Biết khi nào nên dùng `readLine()`, `lines()` hoặc đọc từng khối `char[]`.

## 2. Nguồn, stream và tài nguyên: ba vai trò cần tách bạch

Hãy xem mối quan hệ:

~~~text
File / socket / StringReader
          |
          v
     BufferedReader   <-- đối tượng quản lý Reader bên dưới
          |
          | .lines()
          v
     Stream<String>   <-- pipeline xử lý các dòng
          |
          | filter / map / limit / count / forEach / ...
          v
        Kết quả
~~~

- `BufferedReader` là một `Reader` có bộ đệm; bản thân nó triển khai `Closeable`/`AutoCloseable` và `close()` sẽ đóng reader bên dưới.
- `BufferedReader.lines()` trả về một `Stream<String>` **liên kết với reader ấy**; stream tự nó không mang quyền đóng reader theo hợp đồng này.
- `Stream` hỗ trợ `close()`, chủ yếu để chạy các **close handler** gắn với stream (nếu có). Không được suy luận rằng `close()` của mọi stream sẽ đóng nguồn dữ liệu mà pipeline đang đọc.
- Quyền đóng tài nguyên phải xuất phát từ cách **tài nguyên được mở và bàn giao**. Bên mở reader thông thường là bên chịu trách nhiệm đóng, trừ khi hợp đồng API tuyên bố rõ việc chuyển quyền sở hữu.

Một file có thể bị giữ open nếu chỉ đóng `Stream<String>` rồi quên đóng `BufferedReader`. Ngược lại, đóng `BufferedReader` quá sớm khiến stream còn chưa tiêu thụ không thể đọc đúng.

### Hợp đồng thiết yếu của `BufferedReader.lines()`

- Tạo stream gồm các dòng như kết quả của các lần gọi `readLine()`.
- Mỗi phần tử `String` **không chứa** ký tự kết thúc dòng.
- Dữ liệu được đọc **lazily**: chỉ khi thực hiện terminal operation thì pipeline mới yêu cầu các dòng.
- Nếu xảy ra `IOException` từ reader khi pipeline đang đọc, ngoại lệ được bọc trong `UncheckedIOException` để phù hợp Stream API.
- Không nên thực hiện các thao tác khác trên cùng reader trong lúc terminal operation của stream đang chạy; kết quả terminal operation khi đó không được bảo đảm.
- Sau một terminal operation, không được giả định reader còn ở vị trí cụ thể nào để tiếp tục đọc bằng API khác.
- Việc gọi `close()` trên stream trả về không tự động gọi `close()` trên reader.

Cần nhớ sự khác nhau giữa `lines()` và `readLine()`: `readLine()` trả về `null` khi EOF; trong Stream API, EOF khiến stream **hết phần tử** và không phát ra phần tử `null` ở cuối.

## 3. Lazy evaluation: tạo pipeline chưa đồng nghĩa đã đọc file

Ví dụ:

~~~java
Stream<String> pipeline = reader.lines()
        .map(String::trim)
        .filter(line -> !line.isEmpty());
~~~

Tại thời điểm vừa khai báo, các bước `map` và `filter` là **intermediate operations**; chúng chưa duyệt toàn bộ file và chưa trả danh sách dữ liệu. Một terminal operation như `forEach`, `count`, `toList`, `findFirst` hoặc `anyMatch` mới kích hoạt truy xuất và xử lý nguồn.

~~~java
List<String> values = pipeline.toList(); // terminal operation (Java 16+)
~~~

Một stream chỉ được tiêu thụ **một lần**. Sau `toList()` không thể tiếp tục `pipeline.count()`; cần tạo nguồn/pipeline mới nếu dữ liệu cho phép. Ngay cả khi terminal operation dừng sớm, ví dụ `findFirst()`, việc tái sử dụng cùng stream cũng không hợp lệ.

### `limit()` và `findFirst()` không tự đóng reader

~~~java
Optional<String> first = reader.lines()
        .filter(line -> line.startsWith("ERR"))
        .findFirst();
~~~

`findFirst()` có thể dừng mà chưa đi hết file; điều đó không làm tài nguyên reader tự được đóng. `limit(10)` cũng chỉ giới hạn **số phần tử stream** mà pipeline lấy, không giới hạn kích thước một dòng và không thực hiện đóng file. Reader còn mở cho tới khi chủ sở hữu đóng nó.

Một reader có thể đã đọc trước vào bộ đệm nhiều ký tự hơn lượng stream trả cho người gọi. Vì vậy, sau terminal operation **đừng dựa vào vị trí còn lại của reader** để đọc tiếp chính xác từ một offset suy đoán. Nếu phải kết hợp nhiều kiểu đọc theo một giao thức, nên quản lý bằng một cơ chế đọc nhất quán.

## 4. Mẫu sử dụng đúng: mở reader, tiêu thụ stream, rồi đóng reader

Với file văn bản UTF-8, đây là mẫu nên ưu tiên:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LineCounter {
    private LineCounter() {}

    public static long countNonBlank(Path path) throws IOException {
        try (BufferedReader reader =
                     Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .count();
        }
    }
}
~~~

Đây là **lớp tiện ích có thể biên dịch** nhưng không có `main`. Khi dùng, gọi `LineCounter.countNonBlank(path)` với một file tồn tại. `String.isBlank()` có từ Java 11; ví dụ dùng được với Java 17+.

- `Files.newBufferedReader` tạo `BufferedReader` gắn với file và sử dụng charset tường minh.
- `try-with-resources` giữ reader mở suốt quá trình `count()` chạy.
- Khi khối `try` kết thúc (kể cả do ngoại lệ), `reader.close()` được gọi.
- Không nhất thiết phải đóng riêng stream tạm thời bằng `try-with-resources` trong ví dụ này, vì stream từ `reader.lines()` không có quyền đóng reader; **reader mới là tài nguyên cần quản lý**.
- Nếu lỗi I/O phát sinh trong lúc `count()` duyệt, cần lưu ý ngoại lệ đó đi qua `UncheckedIOException`; `throws IOException` trong chữ ký trên chủ yếu bao gồm lỗi mở/đóng reader, không tự động biến ngoại lệ đọc lazy trở lại checked exception.

Nếu ứng dụng muốn API luôn báo lỗi đọc dưới dạng `IOException`, có thể chuyển lại từ `UncheckedIOException`:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CheckedLineCounter {
    private CheckedLineCounter() {}

    public static long countNonBlank(Path path) throws IOException {
        try (BufferedReader reader =
                     Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            try {
                return reader.lines()
                        .filter(line -> !line.isBlank())
                        .count();
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            }
        }
    }
}
~~~

Lớp thứ hai cũng không có `main`; nó chứng minh cách trả nguyên nhân I/O về hợp đồng checked exception. Không nên bắt mọi `RuntimeException` rồi coi tất cả là lỗi I/O: lỗi xử lý trong `map/filter` có thể là lỗi lập trình hoặc dữ liệu.

## 5. Ví dụ Java 17+ chạy được: đóng stream vẫn chưa đóng reader

Ví dụ tự theo dõi `close()` của nguồn để quan sát điều này mà không phụ thuộc việc hệ điều hành đang giữ file như thế nào.

Tạo file **`LinesOwnershipDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Stream;

public class LinesOwnershipDemo {
    static final class TrackedReader extends StringReader {
        private boolean closed;

        TrackedReader(String text) {
            super(text);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }

    public static void main(String[] args) throws IOException {
        TrackedReader source = new TrackedReader("alpha\n\nbeta\n");

        try (BufferedReader reader = new BufferedReader(source)) {
            try (Stream<String> lines = reader.lines()) {
                List<String> values = lines
                        .filter(line -> !line.isEmpty())
                        .toList();
                System.out.println("values=" + values);
            }

            System.out.println("afterStreamClose=" + source.isClosed());
        }

        System.out.println("afterReaderClose=" + source.isClosed());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LinesOwnershipDemo.java
java LinesOwnershipDemo
~~~

**Stdout chính xác:**

~~~text
values=[alpha, beta]
afterStreamClose=false
afterReaderClose=true
~~~

Diễn giải:

1. `TrackedReader` ghi nhận lúc phương thức `close()` của nó được gọi.
2. `BufferedReader` bọc `TrackedReader` và `lines()` tạo pipeline xử lý cả dòng `alpha`, dòng rỗng và `beta`.
3. `filter` bỏ dòng rỗng; `toList()` tiêu thụ stream. `Stream.toList()` có từ Java 16 và trả một list không hỗ trợ thao tác thêm/xóa phần tử.
4. Ra khỏi `try (Stream<String> lines = ...)` thì `lines.close()` chạy, nhưng nguồn bên dưới vẫn **chưa bị đóng**, nên in `false`.
5. Ra khỏi `try (BufferedReader reader = ...)` thì `reader.close()` đóng `TrackedReader`, nên in `true`.

Điều này không phụ thuộc vào việc nguồn là `StringReader` hay file. Với file/socket, hậu quả thực tế khi quên đóng reader còn có thể là giữ file descriptor/socket và bộ đệm liên quan.

**Có cần dùng cả hai `try`?** Nếu stream không có close handler riêng, `try` của stream không bắt buộc để giải phóng file trong ví dụ này; đóng reader là điều chính yếu. Tuy nhiên, có thể đóng cả hai nếu muốn thể hiện rõ vòng đời hoặc stream đã được gắn `onClose` handler. Đừng cho rằng đóng hai tầng là cùng một thao tác.

## 6. `BufferedReader.lines()` khác `Files.lines()` ở quyền đóng

Dù cùng trả về `Stream<String>`, chúng có **hợp đồng sở hữu tài nguyên khác nhau**.

| API | Bạn nhận được | Đối tượng phải đóng | Ví dụ cấu trúc đúng |
| --- | --- | --- | --- |
| `BufferedReader.lines()` | Stream từ reader **đang có** | `BufferedReader` do bên sở hữu tạo ra | `try (BufferedReader r = ...) { r.lines().count(); }` |
| `Files.lines(path, charset)` | Stream đọc file do API mở | **Stream trả về** | `try (Stream<String> s = Files.lines(path, UTF_8)) { s.count(); }` |
| `Stream.of(...)` | Stream dữ liệu trong bộ nhớ | Thường không có tài nguyên I/O để giải phóng | `Stream.of(...).count()` |

`Files.lines` mở một tài nguyên file phục vụ stream; gọi `close()` trên stream trả về sẽ giải phóng tài nguyên file của thao tác này. Với `BufferedReader.lines()`, việc đóng stream **không thay thế** `reader.close()`.

### Ví dụ hoàn chỉnh so sánh cả hai

Tạo file **`FileLinesDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FileLinesDemo {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("java-lines-", ".txt");

        try {
            Files.writeString(file, "red\r\n\r\nblue\n",
                    StandardCharsets.UTF_8);

            try (BufferedReader reader =
                         Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                long nonBlank = reader.lines()
                        .filter(line -> !line.isBlank())
                        .count();
                System.out.println("nonBlank=" + nonBlank);
            }

            try (Stream<String> lines =
                         Files.lines(file, StandardCharsets.UTF_8)) {
                long blank = lines
                        .filter(String::isBlank)
                        .count();
                System.out.println("blank=" + blank);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac FileLinesDemo.java
java FileLinesDemo
~~~

Stdout:

~~~text
nonBlank=2
blank=1
~~~

File tạm chứa `red`, một dòng trống và `blue`. Đoạn thứ nhất **đóng reader** bằng `try-with-resources`. Đoạn thứ hai **đóng stream do `Files.lines` trả về** bằng `try-with-resources`. Khối `finally` xóa file tạm sau khi cả hai tài nguyên đọc đã đóng, kể cả khi việc xử lý thất bại.

Lưu ý: việc thử nghiệm trên file tạm không chứng minh “đóng stream của `BufferedReader.lines` là đủ”; trường hợp đó đã được kiểm chứng bằng `TrackedReader` ở mục trước.

## 7. Lỗi quan trọng: trả stream từ reader đã đóng

Mẫu sau thường xuất hiện khi muốn một hàm “tiện lợi” trả về `Stream<String>`:

~~~java
// SAI: dòng chỉ được đọc khi caller bắt đầu terminal operation.
static java.util.stream.Stream<String> broken(java.nio.file.Path path)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 java.nio.file.Files.newBufferedReader(
                         path, java.nio.charset.StandardCharsets.UTF_8)) {
        return reader.lines();
    } // Reader đã đóng trước khi caller có cơ hội duyệt stream.
}
~~~

`lines()` là lazy. Khi phương thức `broken` trả về, khối `try` kết thúc nên reader đã đóng. Caller gọi `count()` hoặc `toList()` sẽ không thể tiêu thụ nguồn đúng; thường nhận `UncheckedIOException` do cố đọc từ stream đã đóng.

Có hai hướng thiết kế đúng, tùy ai sở hữu tài nguyên.

### Hướng A: xử lý toàn bộ khi reader vẫn còn mở

Nếu hàm cần trả về **kết quả**, thực hiện terminal operation trong `try`:

~~~java
static java.util.List<String> loadNonBlank(java.nio.file.Path path)
        throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 java.nio.file.Files.newBufferedReader(
                         path, java.nio.charset.StandardCharsets.UTF_8)) {
        return reader.lines()
                .filter(line -> !line.isBlank())
                .toList();
    }
}
~~~

Chỉ dùng cách này khi số dòng được giữ trong bộ nhớ là **hữu hạn và hợp lý**. `toList()` materialize mọi dòng; với file rất lớn, việc tạo list có thể tiêu tốn RAM đáng kể. Nếu bài toán chỉ cần số đếm, tổng hợp thống kê hay ghi kết quả sang đích khác, làm việc streaming trong khối `try` thích hợp hơn.

### Hướng B: trả stream có quyền sở hữu file rõ ràng

Nếu API chủ đích bàn giao một stream đọc file cho caller, dùng `Files.lines`:

~~~java
static java.util.stream.Stream<String> openLines(java.nio.file.Path path)
        throws java.io.IOException {
    return java.nio.file.Files.lines(
            path, java.nio.charset.StandardCharsets.UTF_8);
}
~~~

Caller bắt buộc sử dụng:

~~~java
try (java.util.stream.Stream<String> lines = openLines(path)) {
    lines.filter(line -> !line.isBlank())
         .forEach(System.out::println);
}
~~~

Đây là các **mảnh mã** (cần đặt phương thức trong lớp, có biến `path` và khai báo/giải quyết `IOException` ở caller). `Files.lines` tạo stream có cơ chế đóng tài nguyên file khi stream được close; tài liệu hàm nên nói rõ rằng caller **sở hữu và phải đóng** stream, kể cả khi terminal operation dừng sớm hoặc phát sinh ngoại lệ.

Không áp dụng máy móc mẹo “gắn `onClose(reader::close)`” vì `BufferedReader.close()` có thể ném checked `IOException` trong khi `onClose` nhận `Runnable`. Việc tự chuyển ngoại lệ và quy ước đóng cần cẩn thận; khi nguồn là file, `Files.lines` hoặc đóng reader ngay trong scope tiêu thụ thường rõ ràng hơn.

## 8. Cách xử lý ngoại lệ đọc lazy

`BufferedReader.readLine()` trực tiếp khai báo `throws IOException`. Nhưng terminal operation của `Stream<String>` không khai báo checked `IOException`. Vì vậy, khi thao tác đọc bên dưới thất bại trong lúc duyệt stream, Java bọc nguyên nhân trong `UncheckedIOException`.

Ví dụ độc lập tạo nguồn cố tình lỗi để thấy điều này. Tạo **`LinesIOExceptionDemo.java`**:

~~~java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Objects;

public class LinesIOExceptionDemo {
    static final class FailingReader extends Reader {
        @Override
        public int read(char[] buffer, int offset, int length)
                throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) {
                return 0;
            }
            throw new IOException("simulated read failure");
        }

        @Override
        public void close() {
            // Không có tài nguyên thực cần giải phóng trong demo.
        }
    }

    public static void main(String[] args) throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new FailingReader())) {
            try {
                reader.lines().count();
            } catch (UncheckedIOException exception) {
                System.out.println("cause="
                        + exception.getCause().getMessage());
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LinesIOExceptionDemo.java
java LinesIOExceptionDemo
~~~

Stdout:

~~~text
cause=simulated read failure
~~~

Lỗi xảy ra **trong `count()`**, tức lúc stream thực sự cần đọc dòng. Khi đóng reader, `close()` vẫn có thể ném `IOException` riêng; `try-with-resources` xử lý trường hợp phải đóng khi khối code đang có ngoại lệ và có thể ghi nhận ngoại lệ đóng dưới dạng *suppressed exception*. Việc biến đổi `UncheckedIOException` trở lại `IOException` ở ranh giới API là lựa chọn hợp lý nếu contract của ứng dụng cần checked exception nhất quán.

Với `Files.lines`, lỗi **mở file** có thể là `IOException` tại lời gọi `Files.lines(...)`, còn lỗi **đọc tiếp trong lúc duyệt** cũng có thể trở thành `UncheckedIOException`. Việc dùng `try-with-resources` vẫn quan trọng, bất kể terminal operation kết thúc bình thường hay lỗi.

## 9. Đọc dòng, dấu kết thúc dòng và dòng rỗng

`BufferedReader.readLine()` (và theo đó là `lines()`) phân biệt dòng theo `\n` (LF), `\r` (CR) hoặc cặp `\r\n` (CRLF). Chúng **loại bỏ** terminator khỏi chuỗi trả về.

| Nội dung ký tự nguồn | Các phần tử stream |
| --- | --- |
| `"A\nB"` | `"A"` và `"B"` |
| `"A\r\nB\n"` | `"A"` và `"B"` |
| `"\n"` | Một phần tử `""` |
| `"A\n\nB"` | `"A"`, `""`, `"B"` |
| Chuỗi rỗng `""` | Không có phần tử |

Dòng trống thật (`""`) vẫn tồn tại trong stream; `filter(String::isBlank)` giúp giữ các dòng chỉ toàn whitespace nếu mục tiêu là đếm chúng, còn `filter(line -> !line.isBlank())` loại bỏ chúng. `String.isBlank()` nhận cả những chuỗi chứa toàn ký tự whitespace theo định nghĩa Java; nó khác `isEmpty()`, vốn chỉ kiểm tra độ dài bằng 0.

`lines()` không thích hợp nếu cần bảo toàn **đúng các terminator**, ví dụ công cụ copy file phải giữ nguyên CRLF/LF từng vị trí. Khi ấy có thể dùng `BufferedReader.read(char[])` rồi `Writer.write(char[], off, count)` để không tự loại bỏ terminator, hoặc dùng byte streams/`Files.copy` khi cần giữ nguyên byte thực tế.

Ngoài ra, `Stream<String>` xử lý theo đơn vị **dòng**, không phải theo số byte hay Unicode code point. Charset thuộc bước chuyển đổi file byte sang `Reader`; luôn ghi rõ UTF-8 (hoặc charset giao thức yêu cầu), thay vì đoán encoding.

## 10. Chú ý tới vị trí reader và việc dùng chung giữa nhiều nơi

### Không đọc cạnh tranh trên cùng reader trong terminal operation

Tránh:

~~~java
// SAI: can thiệp trực tiếp cùng reader bên trong pipeline đang tiêu thụ.
reader.lines().forEach(line -> {
    try {
        System.out.println(reader.readLine());
    } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
    }
});
~~~

Ở đây pipeline và callback cùng tiêu thụ một reader. Kết quả không được bảo đảm: có thể mất dòng, bỏ sót dữ liệu hoặc rơi vào trạng thái khó dự đoán.

Đừng đồng thời giao một `BufferedReader` cho hai thread đọc từ `reader.lines()` và `reader.readLine()`. Dù một số thao tác có cơ chế đồng bộ nội bộ, hai bộ tiêu thụ vẫn tranh nhau **một vị trí dòng dữ liệu**. Thiết kế có một nơi sở hữu vòng đọc, rồi phân phát dữ liệu đã được giải mã cho các bên khác là cách an toàn và dễ kiểm soát hơn.

### Không dựa vào vị trí sau khi kết thúc pipeline

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    String first = reader.lines().findFirst().orElse(null);
    // Không dựa vào giả định readLine() tiếp theo là dòng thứ hai.
}
~~~

Mảnh code cần import `BufferedReader`, `Files`, `Path`, `StandardCharsets` và xử lý `IOException`. API `BufferedReader.lines()` không bảo đảm một vị trí cụ thể của reader sau terminal operation. Nếu cần thật sự đọc dòng đầu rồi tiếp tục tuần tự từ dòng hai, viết bằng `readLine()` trong cùng một vòng đọc hoặc parser kiểm soát vị trí, thay vì cắt giữa hai mô hình.

### Có nên dùng `parallel()`?

`reader.lines().parallel()` không biến một nguồn Reader đơn lẻ thành nhiều nguồn đọc độc lập. Chia tách/đọc dữ liệu phụ thuộc triển khai; tác vụ file I/O có chi phí tuần tự và callback có tác dụng phụ có thể không giữ thứ tự khi dùng `forEach`. Thường nên đọc tuần tự từ một reader; nếu cần xử lý CPU song song, cân nhắc thiết kế hàng đợi/batch hoặc phân đoạn dữ liệu có ranh giới rõ. Chỉ song song khi đã đo và kiểm soát thứ tự, trạng thái chia sẻ và vòng đời tài nguyên.

## 11. Stream close handler khác với đóng nguồn Reader

Một `Stream` cho phép đăng ký `onClose(Runnable)`:

~~~java
try (java.util.stream.Stream<String> stream =
             java.util.stream.Stream.of("A", "B")
                     .onClose(() -> System.out.println("stream closed"))) {
    System.out.println(stream.count());
}
~~~

Mảnh mã trên có thể đặt trong một phương thức Java; kết quả:

~~~text
2
stream closed
~~~

`onClose` chạy khi **`stream.close()` được gọi** (ở đây do `try-with-resources`), không phải tự động sau terminal operation `count()`. Vì vậy, thuật toán gọi `count()`/`findFirst()` rồi quên close vẫn có thể bỏ qua handler đóng nguồn nếu ứng dụng gắn vào stream.

Với `BufferedReader.lines()`, mặc định không có close handler tự động đóng reader. Nếu tự thiết kế wrapper trả về stream có handler đóng reader, cần quyết định rõ quyền sở hữu và cách chuyển checked `IOException` từ `Reader.close()` vào exception phù hợp với `Runnable`. Khi không thật sự cần chuyển quyền đóng, cách đơn giản nhất vẫn là giữ cả tiêu thụ stream lẫn đóng `BufferedReader` trong **cùng một scope**.

## 12. Dùng `Stream<String>` tiết kiệm bộ nhớ nhưng không có nghĩa vô hạn an toàn

Một pipeline như:

~~~java
try (BufferedReader reader = Files.newBufferedReader(
        path, StandardCharsets.UTF_8)) {
    long count = reader.lines().filter(s -> !s.isBlank()).count();
}
~~~

thường không cần giữ toàn bộ file trong RAM; dữ liệu được đọc lần lượt và tổng hợp thành số đếm. Tuy vậy:

- **Một dòng riêng lẻ** vẫn có thể rất dài. `readLine()` cần tạo chuỗi cho cả dòng, nên `lines()` không áp dụng giới hạn độ dài dòng.
- `toList()`, `collect(...)` hoặc `sorted()` có thể tích lũy nhiều hoặc toàn bộ dữ liệu. Streaming không đồng nghĩa lúc nào cũng có bộ nhớ hằng số.
- `limit(100)` chỉ giới hạn số **dòng**, không giới hạn mỗi dòng là bao nhiêu ký tự và không tự đóng reader.
- `findFirst()`/`anyMatch()` có thể dừng sớm, nhưng Reader vẫn là tài nguyên phải đóng.
- Với đầu vào không tin cậy hoặc socket, cần giới hạn kích thước bản ghi, timeout/chính sách hủy, xử lý encoding và framing rõ ràng. `lines()` có thể chặn chờ dấu xuống dòng hoặc EOF nếu peer tiếp tục giữ kết nối mở.

Nếu cần đặt **giới hạn byte/ký tự mỗi dòng ngay trong lúc đọc**, hãy dùng bộ phân tích đọc từng ký tự/khối có đếm giới hạn trước khi tích lũy, hoặc giao thức với trường độ dài rõ ràng. Kiểm tra `line.length()` sau khi `lines()` đã tạo chuỗi quá dài có thể quá muộn.

## 13. Các lỗi phổ biến và cách sửa

**Lỗi 1 — Chỉ đóng stream từ `BufferedReader.lines()`.**  
Đóng stream này không đóng reader nguồn. Cần đóng `BufferedReader` trong `try-with-resources` khi nó do hàm sở hữu.

**Lỗi 2 — Trả stream lazy khỏi `try (BufferedReader...)`.**  
Reader đã đóng ngay khi trả về, trong khi caller chưa bắt đầu đọc. Hãy thực hiện terminal operation trong scope còn mở, hoặc trả `Files.lines` với hợp đồng caller tự đóng stream.

**Lỗi 3 — Tưởng terminal operation tự gọi `close()`.**  
`count()`, `findFirst()`, `toList()` hoàn thành không bảo đảm `stream.close()` đã được gọi. `onClose` handler chỉ chạy khi stream được close; đóng reader bằng scope đúng.

**Lỗi 4 — Nhầm `UncheckedIOException` với `IOException` trực tiếp.**  
Lỗi đọc lazy qua `lines()` có thể ném `UncheckedIOException` tại terminal operation. Không chỉ xử lý checked exception ở lúc mở file mà bỏ qua đường lỗi này.

**Lỗi 5 — Xen `readLine()` vào pipeline hoặc tiếp tục dựa vào vị trí sau terminal operation.**  
Reader dùng chung một nguồn ký tự và một vị trí; kết quả khi xen thao tác không được bảo đảm. Chọn một cơ chế duyệt thống nhất.

**Lỗi 6 — Lẫn lộn `Files.lines()` và `BufferedReader.lines()` về quyền đóng.**  
`Files.lines` bàn giao một stream cần close để đóng file do chính API mở; `BufferedReader.lines()` tạo stream từ reader hiện hữu mà caller/người sở hữu vẫn phải đóng reader.

**Lỗi 7 — Giả định một phần tử stream giữ nguyên dấu xuống dòng.**  
`lines()` bỏ LF/CR/CRLF; không phù hợp cho tác vụ phải giữ nguyên ký tự phân cách hoặc byte gốc.

**Lỗi 8 — Sử dụng `toList()` với file không giới hạn hoặc xem `limit` là bảo vệ kích thước dòng.**  
`toList()` tích lũy kết quả; `limit` đếm phần tử, không kiểm tra độ dài mỗi phần tử. Đặt giới hạn thích hợp tại lúc đọc.

## 14. Checklist trước khi dùng trong dự án

- [ ] Biết **ai mở** `BufferedReader` và **ai đóng** nó?
- [ ] Terminal operation của `reader.lines()` được chạy khi reader chắc chắn còn mở?
- [ ] Nếu API trả `Files.lines()`, caller có `try-with-resources` để đóng chính stream?
- [ ] Không dùng `stream.close()` làm bằng chứng reader đã đóng?
- [ ] Có cân nhắc `UncheckedIOException` khi lỗi đọc xảy ra trong quá trình xử lý pipeline?
- [ ] Không vừa duyệt `lines()` vừa trực tiếp đọc trên cùng reader hoặc giả định cursor sau khi kết thúc stream?
- [ ] Nếu có `findFirst`/`limit`, reader vẫn được đóng dù pipeline tiêu thụ ít dòng?
- [ ] Charset được chỉ định rõ; newline và dòng trống được xử lý đúng quy định?
- [ ] Nếu có thể gặp dòng dài/dữ liệu từ mạng, đã đặt giới hạn/thời gian chờ theo giao thức?
- [ ] Có tránh giữ toàn bộ dữ liệu trong RAM khi không cần thiết?

### Câu hỏi tự kiểm tra

**1.** Gọi `reader.lines().filter(...).count()` có tự đóng `reader` không?  
**Đáp án:** Không. Hãy đặt `BufferedReader` do bạn sở hữu trong `try-with-resources`.

**2.** Nếu viết `try (Stream<String> stream = reader.lines()) { ... }`, sau `try` reader đã đóng chưa?  
**Đáp án:** Không phải bởi `stream.close()`. Reader chỉ được đóng nếu có thao tác riêng/hợp đồng khác (ví dụ scope của reader tự đóng).

**3.** `Files.lines(path, UTF_8)` cần đóng đối tượng nào?  
**Đáp án:** Đóng `Stream<String>` mà `Files.lines` trả về bằng `try-with-resources` để giải phóng tài nguyên file liên quan.

**4.** Vì sao không trả `reader.lines()` từ một hàm mà reader được tạo bên trong `try-with-resources`?  
**Đáp án:** Stream là lazy; reader bị đóng trước khi caller gọi terminal operation.

**5.** `reader.lines().findFirst()` đọc được dòng đầu và dừng. Có thể chắc chắn lần `reader.readLine()` kế tiếp trả dòng thứ hai không?  
**Đáp án:** Không. Không có bảo đảm về vị trí reader sau terminal operation trên stream.

**6.** Một lỗi I/O khi stream đang chạy `count()` được báo dưới dạng nào?  
**Đáp án:** Thường là `UncheckedIOException` chứa `IOException` gốc trong `getCause()`.

**Ghi nhớ:** `BufferedReader.lines()` là cách **duyệt dòng** bằng Stream API, còn `BufferedReader` là nơi **quản lý nguồn đọc**. Hãy tiêu thụ stream khi reader còn mở và đóng reader đúng owner. Với `Files.lines()`, chính stream được trả về là tài nguyên phải đóng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-005">Scanner: nguồn dữ liệu, delimiter và token</a>

<details>
<summary>Xem chi tiết</summary>

# Scanner trong Java: nguồn dữ liệu, delimiter và token

## 1. Scanner giải quyết vấn đề gì?

Khi nhận một đoạn văn bản như `"Lan 21 8.5"`, ứng dụng thường không chỉ muốn đọc từng ký tự: nó muốn lấy **tên**, **tuổi** và **điểm**. `java.util.Scanner` giúp chia đầu vào thành các **token** (đơn vị văn bản) theo quy tắc phân cách, rồi đọc token dưới dạng `String` hoặc chuyển thành các kiểu dữ liệu như `int`, `double` và `boolean`.

Ví dụ dữ liệu:

~~~text
Lan 21 8.5
~~~

Nếu dùng dấu trắng làm ký tự phân cách, luồng token nhận được là:

~~~text
Nguồn:    L a n [space] 2 1 [space] 8 . 5
                   │               │
                   ▼               ▼
Token 1: "Lan"  Token 2: "21"  Token 3: "8.5"
~~~

`Scanner` không chỉ nhận chuỗi trong bộ nhớ; nó còn có thể đọc nội dung từ tệp, `InputStream`, `Readable` và những nguồn được API hỗ trợ. Khi dùng Scanner trên I/O thực, việc tìm token **có thể chặn** trong lúc chờ đủ dữ liệu hoặc dấu phân cách.

Bài này tập trung vào ba câu hỏi có quan hệ trực tiếp:

1. **Nguồn dữ liệu** của `Scanner` là gì, được giải mã thành ký tự ra sao và ai phải đóng tài nguyên?
2. **Delimiter** (biểu thức phân cách) xác định ranh giới giữa những phần nào và được hiểu theo quy tắc regex nào?
3. **Token** được nhận diện, kiểm tra và tiêu thụ ra sao? Khi nào nên đọc token, khi nào nên đọc nguyên dòng?

Điểm cần ghi nhớ trước tiên: **Scanner là bộ phân tích văn bản theo token**. Nó không phải công cụ mặc định cho mọi định dạng tệp hay mọi giao thức dữ liệu.

## 2. Mô hình Scanner: nguồn → giải mã → phân cách → token → giá trị

~~~text
Nguồn
  ├─ String trong bộ nhớ
  ├─ Reader đã tạo sẵn
  ├─ InputStream (byte) + charset
  └─ tệp Path/File + charset
            │
            ▼
  Văn bản đã được đọc/giải mã
            │
            ▼
  Quy tắc delimiter (Pattern / regex)
            │
            ▼
  Chuỗi các token:  "Lan" | "21" | "8.5"
            │
            ├─ next()       → String
            ├─ nextInt()    → int, nếu hợp lệ
            ├─ nextDouble() → double, nếu hợp lệ theo Locale
            └─ next(...)    → String khớp mẫu được yêu cầu
~~~

Không nên trộn lẫn các tầng này:

- **Nguồn** cho biết dữ liệu nằm ở đâu và cách nó được cung cấp.
- **Charset** cho biết byte của nguồn được chuyển thành ký tự bằng quy tắc nào, khi nguồn là byte.
- **Delimiter** cho biết vị trí tách token trong chuỗi ký tự đã đọc.
- **Kiểu dữ liệu đích** cho biết token được diễn giải thành số, boolean hay văn bản.

Thay đổi delimiter **không đổi charset**; thay đổi `Locale` của Scanner để đọc số **không đổi charset**. Chẳng hạn UTF-8 quyết định cách đọc chữ “Đà Nẵng” từ byte; `Locale.GERMANY` quyết định `nextDouble()` hiểu dấu phẩy thập phân trong `"3,5"` thế nào.

## 3. Nguồn dữ liệu của Scanner

### 3.1. Chuỗi String: học và kiểm thử không cần I/O

`new Scanner(String source)` tạo Scanner đọc từ nội dung chuỗi có sẵn. Đây là lựa chọn tốt để thử delimiter và quy tắc token vì dữ liệu xác định và không phụ thuộc tệp.

**Ví dụ chạy độc lập**, lưu thành `ScannerStringDemo.java`:

~~~java
import java.util.Scanner;

public class ScannerStringDemo {
    public static void main(String[] args) {
        String source = "  Java\t17\nI/O  ";

        try (Scanner scanner = new Scanner(source)) {
            while (scanner.hasNext()) {
                System.out.println("[" + scanner.next() + "]");
            }
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ScannerStringDemo.java
java ScannerStringDemo
~~~

Kết quả:

~~~text
[Java]
[17]
[I/O]
~~~

Scanner mặc định dùng **khoảng trắng** làm delimiter, bao gồm dấu cách, tab và xuống dòng được nhận diện theo quy tắc khoảng trắng của Java. Nó bỏ qua các delimiter trước token nên không tạo token cho từng khoảng trắng liên tiếp trong ví dụ trên.

### 3.2. Từ InputStream: byte phải được giải mã

Một `InputStream` cung cấp byte, trong khi Scanner xử lý ký tự. Nên chỉ định charset rõ ràng:

~~~java
Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);
~~~

Đây là **đoạn mã minh họa**, với `inputStream` đã được mở; các import cần thiết là `java.util.Scanner` và `java.nio.charset.StandardCharsets`. API nhận `Charset` có trên Java 17. Lớp nguồn có thể là `FileInputStream` hoặc một nguồn byte khác. Cần xác định bên nào có quyền đóng `inputStream` trước khi đóng Scanner.

Nếu không chọn charset rõ ràng, việc giải mã có thể phụ thuộc cấu hình môi trường/API phiên bản Java. Với dữ liệu truyền giữa nhiều máy, hãy sử dụng charset được định dạng dữ liệu quy định, thường là UTF-8.

### 3.3. Từ Reader: nhận ký tự đã giải mã

`new Scanner(Reader)` phù hợp nếu phần khác đã tạo một `Reader` hoặc cần gắn Scanner vào chuỗi wrapper I/O:

~~~java
Scanner scanner = new Scanner(reader);
~~~

Đây là đoạn minh họa với `reader` đã có. Nếu `reader` được tạo bằng `Files.newBufferedReader(path, UTF_8)`, việc chuyển byte sang ký tự đã nằm ở Reader. Scanner sẽ dùng delimiter trên **ký tự đã nhận**, chứ không dùng regex trên byte UTF-8.

### 3.4. Từ Path/File: chọn charset và xử lý vòng đời

Có thể tạo Scanner từ `Path` cùng `Charset`:

~~~java
try (Scanner scanner = new Scanner(path, StandardCharsets.UTF_8)) {
    while (scanner.hasNext()) {
        String token = scanner.next();
        // Xử lý token.
    }
}
~~~

Đây là đoạn minh họa: `path` có kiểu `java.nio.file.Path`; phương thức bao ngoài phải khai báo hoặc xử lý ngoại lệ I/O phù hợp. Lớp `Scanner` mở tài nguyên tệp và `try-with-resources` đóng nó khi kết thúc.

Một cách khác cũng hợp lệ là:

~~~java
try (Scanner scanner = new Scanner(
        Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
    // Đọc dữ liệu.
}
~~~

Các đoạn trên cần import `java.util.Scanner`, `java.nio.file.Files`, `java.nio.file.Path` và `java.nio.charset.StandardCharsets`. Khi Scanner đóng, nếu nguồn triển khai `Closeable` thì Scanner sẽ đóng nguồn đó; vì vậy không được vô tình đóng một Reader hay InputStream mà bên khác còn sở hữu.

### 3.5. Từ System.in: luồng dùng chung của tiến trình

Đọc nhập liệu ở console có thể dùng:

~~~java
Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
~~~

Nếu chương trình dòng lệnh dùng `System.in` trong suốt vòng đời, có thể giữ Scanner tương ứng trong suốt phiên làm việc. **Đóng Scanner sẽ đóng `System.in`** vì nguồn là một `InputStream` có thể đóng; một thư viện hoặc một hàm ngắn không nên tự đóng tài nguyên chia sẻ này nếu phần khác còn cần nhập liệu.

Quy tắc sở hữu tổng quát: phần tạo nguồn và trao quyền sở hữu cho Scanner có thể để Scanner đóng; nếu chỉ mượn nguồn từ bên gọi, hãy công bố rõ quy ước đóng hoặc dùng API khác để tránh đóng ngoài ý muốn.

## 4. Delimiter là gì?

Delimiter là **mẫu biểu thức chính quy (regular expression, regex)** dùng để chỉ ra những đoạn văn bản ngăn cách token. Ban đầu Scanner dùng một mẫu nhận diện khoảng trắng.

Giả sử đầu vào:

~~~text
An, Bình; Chi
~~~

Với delimiter mặc định, khoảng trắng mới được coi là ranh giới, vì vậy có thể xuất hiện token `"An,"`, `"Bình;"` và `"Chi"`. Dấu phẩy, chấm phẩy vẫn nằm trong token.

Nếu muốn cả dấu phẩy và chấm phẩy phân cách tên, cần đổi delimiter, ví dụ `\\s*[,;]\\s*`:

- `[,;]`: khớp **một** dấu phẩy hoặc dấu chấm phẩy.
- `\\s*`: khớp **không hoặc nhiều** ký tự trắng ở hai bên dấu phân cách.
- Mẫu này tách các tên bằng một dấu phẩy hoặc dấu chấm phẩy, có thể kèm khoảng trắng.

Trong **chuỗi Java**, ký tự backslash của regex phải được escape. Chuỗi Java dùng cho regex `\s*` ở đây cần viết `"\\s*"` (hai dấu backslash trong mã Java trước `s`). Cẩn thận: từ Java 15, escape `\s` trong Java string literal mang nghĩa một dấu cách, vì vậy viết `"\s*"` không tương đương với regex whitespace `"\\s*"`.

### 4.1. Thay delimiter bằng useDelimiter

Lưu thành `ScannerDelimiterDemo.java`:

~~~java
import java.util.Scanner;

public class ScannerDelimiterDemo {
    public static void main(String[] args) {
        String source = "An, Bình; Chi";

        try (Scanner scanner = new Scanner(source)) {
            scanner.useDelimiter("\\s*[,;]\\s*");

            while (scanner.hasNext()) {
                System.out.println("<" + scanner.next() + ">");
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerDelimiterDemo.java
java ScannerDelimiterDemo
~~~

Kết quả:

~~~text
<An>
<Bình>
<Chi>
~~~

`useDelimiter(String pattern)` biên dịch chuỗi thành mẫu regex. Có thể dùng `useDelimiter(Pattern)` khi đã có một `java.util.regex.Pattern` biên dịch sẵn. `scanner.delimiter()` trả về mẫu delimiter đang được sử dụng.

Một delimiter không nhất thiết là **một ký tự đơn**: nó có thể là dấu phân cách dài (ví dụ `"::"`), nhiều dấu liên tiếp, hoặc một cấu trúc khớp regex. Tuy nhiên càng phức tạp thì càng phải tính đến các ký tự đặc biệt của regex và trường hợp khớp chuỗi rỗng.

### 4.2. Regex có ký tự đặc biệt

Một số dấu trong regex có ý nghĩa riêng:

| Muốn phân cách theo | Regex cần dùng | Chuỗi Java truyền cho useDelimiter |
| --- | --- | --- |
| Dấu phẩy | `,` | `","` |
| Dấu chấm phẩy | `;` | `";"` |
| Dấu chấm thật | `\\.` | `"\\."` |
| Dấu gạch đứng thật | `\\|` | `"\\|"` |
| Một hoặc nhiều dấu trắng | `\\s+` | `"\\s+"` |
| Dấu phẩy hoặc chấm phẩy, có dấu trắng quanh | `\\s*[,;]\\s*` | `"\\s*[,;]\\s*"` |

Dấu `.` chưa escape có nghĩa “một ký tự bất kỳ” trong đa số chế độ regex; dấu `|` chưa escape biểu thị phép lựa chọn. Vì vậy `useDelimiter(".")` hoặc `useDelimiter("|")` thường không mang nghĩa “ngắt ở dấu chấm/dấu gạch đứng như văn bản”.

Nếu delimiter là một chuỗi ký tự **literal** đến từ cấu hình, có thể dùng `Pattern.quote(delimiter)` để tránh vô tình coi nó như regex:

~~~java
String separator = "|";
scanner.useDelimiter(java.util.regex.Pattern.quote(separator));
~~~

Đây là đoạn mã minh họa khi `scanner` đã tồn tại. `Pattern.quote` giúp biểu diễn đúng **chuỗi delimiter cố định**; với một quy tắc regex được thiết kế chủ đích, truyền chính mẫu regex.

### 4.3. Delimiter rỗng hoặc có thể khớp độ dài 0

Regex như `""`, `"\\s*"` hoặc lookaround có thể khớp tại vị trí rỗng. Những mẫu như thế có thể tạo token khác dự kiến, thậm chí từng ký tự trở thành một token, và làm quá trình phân tích khó dự đoán.

Nếu muốn phân cách bằng **ít nhất một** khoảng trắng, dùng `"\\s+"` thay vì `"\\s*"`. Chỉ dùng regex có thể khớp rỗng khi đã hiểu quy tắc Scanner và có kiểm thử cho chuỗi đầu/cuối, delimiter lặp, Unicode và dữ liệu rỗng.

## 5. Token là gì và Scanner tiêu thụ token như thế nào?

Với một delimiter nhất định, token về cơ bản là **đoạn văn bản giữa các phần khớp delimiter** được Scanner phát hiện. `next()` lấy token kế tiếp và **dịch vị trí đọc**. `hasNext()` kiểm tra xem có token tiếp theo hay không nhưng **không tiêu thụ** token.

Ví dụ:

~~~java
try (Scanner scanner = new Scanner("red blue")) {
    System.out.println(scanner.hasNext());
    System.out.println(scanner.hasNext());
    System.out.println(scanner.next());
    System.out.println(scanner.next());
    System.out.println(scanner.hasNext());
}
~~~

Đây là ví dụ nhỏ chạy được bên trong một phương thức khai báo `throws Exception` hoặc phù hợp ngữ cảnh Java hỗ trợ `try-with-resources`. Khi cần chạy độc lập, đặt vào một lớp có `main` và `import java.util.Scanner`.

Kết quả:

~~~text
true
true
red
blue
false
~~~

Hai lần `hasNext()` đầu tiên đều thấy cùng token `red`; chỉ `next()` mới lấy token và tiến vị trí.

Không được gọi `next()` tùy tiện khi nguồn đã hết: nó ném `NoSuchElementException`. `hasNext()` có thể chặn khi Scanner đang chờ thêm đầu vào từ nguồn sống. Một vòng lặp phổ biến:

~~~java
while (scanner.hasNext()) {
    String token = scanner.next();
    // Xử lý token.
}
~~~

Đây là đoạn mã minh họa với `scanner` đã có. Mỗi vòng lặp tiêu thụ đúng một token. Nếu bỏ `scanner.next()` mà chỉ lặp `hasNext()`, chương trình có thể không tiến triển.

### 5.1. hasNext(Pattern) và next(Pattern)

`hasNext(pattern)` kiểm tra token kế tiếp có khớp mẫu được chỉ định không và không tiêu thụ. `next(pattern)` lấy token kế tiếp nếu khớp; nếu không khớp, ném `InputMismatchException`.

Mẫu trong `next(Pattern)` / `hasNext(Pattern)` được áp dụng cho **toàn bộ token** theo hợp đồng matching của Scanner, không đơn giản là tìm một chuỗi con tùy ý.

Ví dụ:

~~~java
scanner.hasNext("[A-Za-z]+"); // Token kế tiếp gồm các chữ cái ASCII?
scanner.hasNext("\\d+");      // Token kế tiếp gồm một hay nhiều chữ số?
~~~

Đây là đoạn minh họa. Với token `"id42"`, cả hai biểu thức trên đều không khớp toàn bộ token. Nếu cần tìm một đoạn văn bản nằm **bên trong** dòng, xem các phương thức tìm kiếm như `findInLine` hoặc dùng `Matcher` trên một `String` đã đọc; không ép `next(regex)` làm việc của công cụ tìm chuỗi con.

### 5.2. Token không phải dòng

Delimiter mặc định nhận diện xuống dòng như khoảng trắng nên hai dòng:

~~~text
Hoa 18
Nam 20
~~~

có thể thành bốn token `Hoa`, `18`, `Nam`, `20`. Scanner không tự giữ thông tin rằng hai token đầu thuộc dòng thứ nhất nếu ứng dụng chỉ dùng `next()`.

Khi ranh giới **dòng** mang ý nghĩa nghiệp vụ, cân nhắc `hasNextLine()` / `nextLine()` và sau đó phân tích từng dòng riêng, hoặc dùng `BufferedReader`. Đừng giả định cứ hai token là một dòng nếu nguồn có thể có cột trống, dấu nháy hoặc cấu trúc phức tạp.

## 6. Quan hệ giữa next() và nextLine(): lỗi rất thường gặp

`next()` đọc token theo delimiter đang thiết lập. `nextInt()`, `nextDouble()` cũng đọc một token và phân tích giá trị. `nextLine()` thì khác: nó lấy phần còn lại của **dòng hiện tại**, không bao gồm dấu kết thúc dòng, và đưa vị trí Scanner sang dòng kế tiếp.

Sau `nextInt()`, dấu xuống dòng cuối dòng có thể vẫn chưa được `nextLine()` tiêu thụ. Nếu gọi `nextLine()` ngay, bạn thường nhận được **chuỗi rỗng** khi phía sau số không còn ký tự nào trước dấu xuống dòng.

Ví dụ chạy độc lập: `ScannerLinePitfallDemo.java`

~~~java
import java.util.Scanner;

public class ScannerLinePitfallDemo {
    public static void main(String[] args) {
        String input = "42\nNguyen Van A\n";

        try (Scanner scanner = new Scanner(input)) {
            int age = scanner.nextInt();
            String restOfNumberLine = scanner.nextLine();
            String name = scanner.nextLine();

            System.out.println("Age: " + age);
            System.out.println("Remaining chars: " + restOfNumberLine.length());
            System.out.println("Name: " + name);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerLinePitfallDemo.java
java ScannerLinePitfallDemo
~~~

Kết quả:

~~~text
Age: 42
Remaining chars: 0
Name: Nguyen Van A
~~~

Với đầu vào mà sau số có ký tự khác trên **cùng dòng**, `nextLine()` sẽ lấy các ký tự còn lại chứ không nhất thiết trả chuỗi rỗng. Không nên gọi `nextLine()` “để xóa bộ đệm” một cách máy móc: lời gọi đó **tiêu thụ phần còn lại của dòng** và có thể bỏ dữ liệu hữu ích.

Hai chiến lược rõ ràng:

- **Bài toán token:** dùng đồng nhất `hasNext`/`next`/`nextInt` khi định dạng thực sự chia thành token.
- **Bài toán biểu mẫu theo dòng:** dùng `nextLine()` lấy từng dòng, rồi chuyển kiểu bằng `Integer.parseInt(line.strip())` hoặc tạo Scanner riêng cho mỗi dòng. Cách này làm ranh giới dòng rõ ràng.

Một trường hợp dễ nhầm khác: `scanner.useDelimiter(",")` đổi token separator thành dấu phẩy, nhưng `nextLine()` vẫn tìm ranh giới dòng theo hợp đồng của nó; nó **không biến thành “đọc đến dấu phẩy”**.

## 7. Đọc số: delimiter và Locale là hai quy tắc khác nhau

`nextInt()`, `nextLong()`, `nextDouble()` và các phương thức tương tự làm hai bước:

1. Xác định **token** bằng delimiter.
2. Diễn giải **nội dung token** theo ngữ pháp kiểu dữ liệu, radix/`Locale` thích hợp.

Dấu thập phân và dấu phân nhóm chữ số có thể phụ thuộc `Locale`. Vì thế, token `"3,5"` có thể đại diện cho `3.5` theo quy tắc một locale và không hợp lệ hoặc mang nghĩa khác ở locale khác. Đặt `Locale` rõ ràng với định dạng số cố định.

Ví dụ chạy độc lập, `ScannerLocaleDemo.java`:

~~~java
import java.util.Locale;
import java.util.Scanner;

public class ScannerLocaleDemo {
    public static void main(String[] args) {
        try (Scanner scanner =
                     new Scanner("17 3,5 yes").useLocale(Locale.GERMANY)) {
            int count = scanner.nextInt();
            double value = scanner.nextDouble();
            String flag = scanner.next();

            System.out.println("Count: " + count);
            System.out.println("Value: " + value);
            System.out.println("Flag: " + flag);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerLocaleDemo.java
java ScannerLocaleDemo
~~~

Kết quả:

~~~text
Count: 17
Value: 3.5
Flag: yes
~~~

`Scanner` đọc `3,5` theo `Locale.GERMANY` thành giá trị double `3.5`. Khi in bằng `System.out.println(double)`, Java dùng biểu diễn chuỗi số thông thường với dấu chấm; việc in số bằng `println` không tự kế thừa locale của Scanner.

### 7.1. Ví dụ có dấu phẩy phân cách và dấu phẩy thập phân

Giả sử định dạng có token phân cách bằng dấu phẩy, đồng thời một giá trị số cũng chứa dấu phẩy thập phân theo locale. Không thể dùng `useDelimiter(",")` rồi kỳ vọng Scanner tự biết dấu phẩy nào là dấu tách cột và dấu phẩy nào thuộc số.

**Delimiter giải quyết ranh giới token; Locale giải quyết ý nghĩa của token đã có**. Nếu hai quy tắc dùng cùng ký hiệu, cần một định dạng có quy tắc trích dẫn/escape rõ ràng (chẳng hạn định dạng CSV chuẩn) hoặc chọn ký hiệu phân cách khác. Scanner với một regex delimiter đơn giản không phải một bộ phân tích CSV đầy đủ.

### 7.2. hasNextInt() không tiêu thụ token lỗi

Khi `hasNextInt()` trả về `false`, token kế tiếp vẫn đang chờ. Nếu chỉ lặp kiểm tra mà không xử lý/tiêu thụ nó, chương trình không tiến triển.

Ví dụ `ScannerValidationDemo.java`:

~~~java
import java.util.Scanner;

public class ScannerValidationDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("12 wrong 34")) {
            while (scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    System.out.println("Number: " + scanner.nextInt());
                } else {
                    System.out.println("Skipped: " + scanner.next());
                }
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerValidationDemo.java
java ScannerValidationDemo
~~~

Kết quả:

~~~text
Number: 12
Skipped: wrong
Number: 34
~~~

Hàm `hasNextInt()` kiểm tra cú pháp và giới hạn của `int` theo ngữ pháp Scanner. Nếu token không phù hợp, `nextInt()` ném `InputMismatchException`; token gây lỗi **không tự được loại bỏ**. Trong ứng dụng thực, tùy yêu cầu bạn có thể dừng để báo lỗi có ngữ cảnh hoặc tiêu thụ token lỗi để phục hồi.

## 8. Dấu phân cách có thể làm thay đổi hoàn toàn token

Xét chuỗi:

~~~text
A|B C|D
~~~

Các thiết lập dẫn đến phân tích khác nhau:

| Delimiter | Token được phát hiện |
| --- | --- |
| Mặc định (khoảng trắng) | `A|B`, `C|D` |
| `"\\|"` | `A`, `B C`, `D` |
| `"[\\s|]+"` | `A`, `B`, `C`, `D` |

Vì dấu `|` là ký tự đặc biệt của regex, muốn phân cách theo dấu gạch đứng cần escape như `"\\|"` hoặc dùng lớp ký tự `"[|]"`. Ở mẫu `"[\\s|]+"`, dấu `|` bên trong lớp ký tự có nghĩa literal.

**Không được tự động bỏ khoảng trắng** khi khoảng trắng là một phần có ý nghĩa của token. Ví dụ `"Hà Nội|Việt Nam"` với delimiter dấu gạch đứng nên có hai token `"Hà Nội"` và `"Việt Nam"`; thêm `\\s+` vào delimiter có thể biến chúng thành bốn token ngoài ý muốn.

### 8.1. Token rỗng, delimiter liên tiếp và dấu nháy

Một mẫu regex tách đơn giản không đủ để biểu diễn mọi định dạng có trường rỗng hoặc trích dẫn. Với dữ liệu:

~~~text
a,,c
~~~

có thể cần hiểu có ba trường, trong đó trường thứ hai rỗng. Nhưng yêu cầu giữ chính xác trường rỗng **không nên phó mặc** cho logic token hóa thông thường nếu chưa kiểm tra hành vi delimiter và các ranh giới đầu/cuối. Với chuỗi trong bộ nhớ và dấu phân cách đơn giản, `String.split(",", -1)` là một phương án giữ cả trường rỗng cuối khi thật sự cần phân tích trường.

Với dữ liệu CSV như:

~~~text
"Nguyễn, An",21,"Hà Nội"
~~~

dấu phẩy nằm trong cặp dấu nháy của tên **không phải dấu phân cách cột**. `scanner.useDelimiter(",")` sẽ cắt sai. CSV còn có quy tắc escape dấu nháy, xuống dòng trong ô và nhiều trường hợp khác. Khi yêu cầu đúng theo CSV, hãy dùng parser chuyên dụng tuân thủ định dạng thay vì một regex đơn lẻ.

## 9. Phân tích token theo mẫu dữ liệu

`next(String pattern)` và `next(Pattern pattern)` chỉ lấy token kế tiếp nếu khớp mẫu; chúng có thể hữu ích khi dữ liệu có cấu trúc token rõ ràng.

Ví dụ `ScannerPatternDemo.java`:

~~~java
import java.util.Scanner;

public class ScannerPatternDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("abc 123 x9")) {
            while (scanner.hasNext()) {
                if (scanner.hasNext("[A-Za-z]+")) {
                    System.out.println("WORD: " + scanner.next());
                } else if (scanner.hasNext("\\d+")) {
                    System.out.println("NUMBER: " + scanner.next());
                } else {
                    System.out.println("OTHER: " + scanner.next());
                }
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerPatternDemo.java
java ScannerPatternDemo
~~~

Kết quả:

~~~text
WORD: abc
NUMBER: 123
OTHER: x9
~~~

Mẫu `"[A-Za-z]+"` chỉ nhận chữ ASCII; nếu dữ liệu có chữ tiếng Việt, cần mẫu Unicode thích hợp, chẳng hạn dùng nhóm ký tự `\\p{L}+` cho các ký tự Unicode thuộc nhóm Letter. Nếu cần nhận cả dấu kết hợp, định nghĩa “từ” phải xét thêm ký tự đánh dấu và yêu cầu nghiệp vụ.

Đừng dùng `hasNext("[A-Za-z]+")` để xác định mọi tên người hợp lệ; tên thật có khoảng trắng, dấu, dấu nối và nhiều dạng chữ viết. Mẫu chỉ là minh họa phân loại token.

## 10. Quy tắc đọc theo từng dòng rồi token hóa từng dòng

Dữ liệu có cấu trúc **mỗi dòng là một bản ghi** thường an toàn hơn khi ứng dụng giữ nguyên ranh giới dòng:

~~~text
Lan;21;Ha Noi
Minh;22;Da Nang
~~~

Có thể dùng Scanner ngoài để đọc dòng, Scanner trong để phân tích token của từng dòng:

~~~java
try (Scanner lines = new Scanner(text)) {
    while (lines.hasNextLine()) {
        String line = lines.nextLine();
        try (Scanner fields = new Scanner(line).useDelimiter(";")) {
            // Đọc các trường từ một dòng.
        }
    }
}
~~~

Đây là **đoạn minh họa**, biến `text` có kiểu `String`. Nó phù hợp với định dạng đơn giản không có dấu phân cách nằm trong trường và không có trường rỗng đặc biệt. Nó cũng cho phép chỉ rõ số dòng khi báo lỗi. Nếu dữ liệu cho phép trường rỗng, dấu phân cách được escape, trường đặt trong dấu nháy hoặc chứa xuống dòng, cần parser theo đúng định dạng đó.

### 10.1. Đừng trộn không kiểm soát nextLine với hasNext

Hai kiểu lặp khác nhau có hợp đồng khác nhau:

~~~java
while (scanner.hasNext()) {
    String token = scanner.next();
}
~~~

Lặp này kết thúc khi không còn **token** theo delimiter.

~~~java
while (scanner.hasNextLine()) {
    String line = scanner.nextLine();
}
~~~

Lặp này kết thúc khi không còn **dòng** để đọc. Với dòng rỗng hoặc nhiều dấu phân cách liên tiếp, số dòng không bằng số token. Nếu bài toán cần bảo toàn dòng trắng, chỉ lặp theo token sẽ bỏ qua thông tin đó.

## 11. Lỗi và ngoại lệ cần xử lý

Các phương thức của Scanner có những tình huống lỗi đáng chú ý:

| Tình huống | Hành vi điển hình | Cách xử lý |
| --- | --- | --- |
| `next()` khi không có token | `NoSuchElementException` | Kiểm tra `hasNext()` hoặc quản lý EOF |
| `nextInt()` gặp token không phải số nguyên hợp lệ hoặc vượt khoảng `int` | `InputMismatchException` | Kiểm tra `hasNextInt()` hoặc bắt lỗi và xử lý token |
| Gọi phương thức trên Scanner đã đóng | `IllegalStateException` | Dừng dùng sau `close()`; quản lý quyền sở hữu |
| Regex delimiter không hợp lệ | `PatternSyntaxException` | Kiểm thử regex hoặc dùng `Pattern.quote` cho literal |
| Mở tệp thất bại | Ngoại lệ I/O khi tạo nguồn/Scanner | Kiểm tra đường dẫn, quyền, charset và xử lý lỗi |
| Reader bên dưới ném `IOException` trong lúc Scanner đang đọc | Scanner coi như kết thúc nguồn ở tầng token hóa; lưu lỗi | Kiểm tra `scanner.ioException()` khi cần phân biệt lỗi I/O với EOF |

Một điểm quan trọng: Scanner được thiết kế thuận tiện cho phân tích văn bản, nên có thể **không ném trực tiếp `IOException` phát sinh trong lúc đọc từ `Readable`**. API `scanner.ioException()` cho biết ngoại lệ I/O cuối cùng mà nguồn ném, nếu có. Nếu chương trình phải phân biệt “hết dữ liệu bình thường” với “đọc lỗi giữa chừng”, hãy kiểm tra trường hợp đó và chuyển lỗi lên tầng thích hợp.

Ví dụ minh họa cuối quá trình quét:

~~~java
IOException problem = scanner.ioException();
if (problem != null) {
    throw problem;
}
~~~

Đây là **đoạn minh họa**, yêu cầu `scanner` đã có, `import java.io.IOException` và phương thức bao ngoài khai báo hoặc bắt `IOException`. Với nguồn `String`, sẽ không có lỗi đọc bên ngoài để quan sát; với tệp hoặc kết nối, đây là kiểm tra có giá trị.

Không nên bắt `InputMismatchException` rồi bỏ qua để gọi lại `nextInt()` trên cùng một token: token lỗi thường vẫn nằm ở vị trí hiện tại. Muốn thử lại cần tiêu thụ hoặc thay thế token không hợp lệ, hoặc sửa nguồn theo quy tắc chương trình.

## 12. Vòng đời tài nguyên và trách nhiệm đóng

`Scanner` triển khai `Closeable`, có thể dùng trong `try-with-resources`:

~~~java
try (Scanner scanner = new Scanner(path, StandardCharsets.UTF_8)) {
    // Quét token.
}
~~~

Khi kết thúc, Scanner tự được đóng, kể cả có ngoại lệ trong thân `try`. Nếu nguồn của Scanner triển khai `Closeable`, `Scanner.close()` sẽ đóng nguồn đó. Vì vậy, cách này là phù hợp khi Scanner **sở hữu** nguồn tệp đã mở.

Các tình huống quyền sở hữu:

- Tạo Scanner đọc từ `String`: không có handle tệp cần giải phóng, nhưng đóng Scanner vẫn có thể biểu đạt vòng đời sử dụng.
- Tạo Scanner từ `Path` hoặc mở `Reader` riêng cho một tác vụ: thông thường phần tạo phải bảo đảm tài nguyên được đóng.
- Nhận `Reader` hoặc `InputStream` của người gọi: cần thỏa thuận ai đóng, vì đóng Scanner có thể đóng nguồn đang được sử dụng chung.
- Scanner trên `System.in`: không nên đóng sớm ở một hàm nhỏ nếu các chức năng sau còn cần console input.

`Scanner` **không an toàn để nhiều thread dùng chung mà không đồng bộ bên ngoài**. Nó có trạng thái vị trí quét, delimiter, locale và buffer. Nếu nhiều phần cần phân tích dữ liệu khác nhau, nên tạo Scanner riêng cho mỗi nguồn độc lập hoặc xây dựng cơ chế điều phối có kiểm soát.

## 13. Hiệu năng: khi nào Scanner phù hợp?

Scanner tiện cho dữ liệu nhỏ hoặc vừa, công cụ dòng lệnh, bài thực hành, định dạng token đơn giản và chương trình cần chuyển kiểu trực tiếp. Nhưng nó cũng xử lý regex và nhiều quy tắc diễn giải dữ liệu, nên với lượng lớn số hoặc log, chi phí có thể cao hơn cách đọc tuyến tính chuyên biệt.

Một số lựa chọn:

| Bài toán | Gợi ý |
| --- | --- |
| Nhập dữ liệu từ console theo từ/số | Scanner với quy tắc token và locale rõ ràng |
| Tách một đoạn `String` nhỏ theo ký tự phân cách đơn giản | Scanner hoặc `String.split` tùy cần trường rỗng |
| Đọc tệp theo từng dòng, cần giữ cả dòng | `BufferedReader.readLine()` / `Files.newBufferedReader` |
| Đọc tệp văn bản rất lớn và xử lý bản ghi tuần tự | BufferedReader và parser phù hợp định dạng |
| Đọc tệp nhị phân | `InputStream` / NIO byte; không dùng Scanner |
| CSV có dấu nháy, dấu phẩy trong ô, dòng nhiều hàng | Parser CSV tuân thủ chuẩn của định dạng |
| JSON/XML | Parser JSON/XML; Scanner delimiter đơn giản không đủ |
| Token phức tạp cần Unicode/ngữ pháp riêng | Xây dựng bộ phân tích theo yêu cầu hoặc dùng thư viện chuyên dụng |

Một token cực dài mà không có delimiter phù hợp có thể khiến Scanner phải tích lũy nhiều ký tự để tìm ranh giới. Với dữ liệu đầu vào không tin cậy, cần định nghĩa giới hạn kích thước, số token, timeout/cách hủy việc đọc và quy tắc kiểm tra, tùy loại nguồn.

Không chọn delimiter regex quá phức tạp khi có cách mô tả định dạng đơn giản hơn. Một regex dễ gây backtracking nặng có thể làm việc tìm token chậm bất thường trên đầu vào dài.

## 14. Ví dụ tổng hợp: phân tích dòng dữ liệu dấu chấm phẩy với kiểm tra token

Ví dụ sau đọc một chuỗi dữ liệu gồm ba token: tên, tuổi và thành phố, phân cách bằng dấu chấm phẩy và khoảng trắng tùy chọn. Dữ liệu cố định cho phép xác minh kết quả rõ ràng.

Lưu vào **ScannerRecordDemo.java**:

~~~java
import java.util.Scanner;

public class ScannerRecordDemo {
    public static void main(String[] args) {
        String record = "Nguyễn An; 23; Hà Nội";

        try (Scanner scanner = new Scanner(record)) {
            scanner.useDelimiter("\\s*;\\s*");

            if (!scanner.hasNext()) {
                throw new IllegalArgumentException("Missing name");
            }
            String name = scanner.next();

            if (!scanner.hasNextInt()) {
                throw new IllegalArgumentException("Invalid age");
            }
            int age = scanner.nextInt();

            if (!scanner.hasNext()) {
                throw new IllegalArgumentException("Missing city");
            }
            String city = scanner.next();

            if (scanner.hasNext()) {
                throw new IllegalArgumentException("Unexpected extra token");
            }

            System.out.println("Name: " + name);
            System.out.println("Age: " + age);
            System.out.println("City: " + city);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ScannerRecordDemo.java
java ScannerRecordDemo
~~~

Kết quả:

~~~text
Name: Nguyễn An
Age: 23
City: Hà Nội
~~~

Giải thích:

1. Delimiter chỉ là dấu chấm phẩy kèm dấu trắng quanh nó. Vì **dấu cách giữa các từ trong tên/thành phố không phải delimiter**, `"Nguyễn An"` và `"Hà Nội"` là mỗi cụm một token.
2. `hasNext()` bảo vệ các lệnh `next()` khỏi nguồn thiếu token; `hasNextInt()` xác nhận token tuổi theo ngữ pháp số nguyên.
3. `nextInt()` tiêu thụ cùng một token mà `hasNextInt()` vừa kiểm tra, vì giữa hai lời gọi không có thao tác đọc khác.
4. Kiểm tra `hasNext()` sau cùng phát hiện dữ liệu dư. Với định dạng thực tế, cần bổ sung kiểm tra độ dài, khoảng tuổi, khoảng trắng đầu/cuối, trường rỗng và các trường hợp lỗi phù hợp.
5. Scanner nhận `String` nên không cần tệp; trong hệ thống thật, cùng cách phân tích token có thể áp dụng cho Reader nhưng phải quản lý charset, I/O và quyền sở hữu.

Ví dụ này **không phải parser CSV có dấu trích dẫn**, và `hasNextInt()` phụ thuộc cấu hình locale. Khi định dạng có quy tắc cố định, hãy đặt `Locale` và chính sách xác thực tương ứng.

## 15. Những lỗi phổ biến trong ứng dụng

| Cách làm | Điều có thể xảy ra | Cách sửa |
| --- | --- | --- |
| Dùng delimiter mặc định để đọc `"A,B,C"` | Cả chuỗi có thể là một token | `useDelimiter(",")` nếu định dạng thật sự phân cách bằng dấu phẩy |
| Dùng `useDelimiter(".")` để tách theo dấu chấm | `.` là regex đặc biệt, có thể khớp gần mọi ký tự | `useDelimiter("\\.")` hoặc `Pattern.quote(".")` |
| Gọi `nextLine()` ngay sau `nextInt()` và nghĩ luôn nhận tên | Có thể đọc phần còn lại rỗng của dòng số | Tách chiến lược token/dòng; đọc đúng dòng cần lấy |
| Trộn `next()` và `nextLine()` khi không hiểu vị trí Scanner | Dữ liệu bị bỏ qua hoặc đọc nhầm | Tài liệu hóa định dạng và kiểm thử bằng nhiều dấu xuống dòng |
| Dùng `hasNextInt()` trong vòng lặp nhưng không tiêu thụ token lỗi | Vòng lặp đứng yên | `next()` để bỏ token lỗi hoặc ném lỗi |
| Tưởng `hasNext()` luôn không chặn | Trên nguồn sống, Scanner có thể chờ dữ liệu | Thiết kế timeout/hủy đọc theo nguồn I/O |
| Dùng `useDelimiter(",")` cho CSV có dấu nháy | Cắt sai trường chứa dấu phẩy | Dùng parser CSV chuyên dụng |
| Dùng Scanner đọc nhị phân | Byte bị giải mã như ký tự văn bản | Dùng byte stream/NIO |
| Không chỉ định charset của nguồn byte | Chữ có dấu có thể hỏng | Chọn charset theo hợp đồng dữ liệu |
| Không chỉ định Locale cho số | `nextDouble()` có thể diễn giải sai | `useLocale(Locale.US)` hoặc locale quy định |
| Đóng Scanner trên `System.in` quá sớm | Phần khác không nhập liệu tiếp được | Quản lý vòng đời ở cấp ứng dụng |
| Bỏ qua `scanner.ioException()` khi nguồn đọc lỗi | Có thể nhầm lỗi I/O với EOF | Kiểm tra và báo lỗi đúng nơi |
| Chia token từ dữ liệu không tin cậy mà không giới hạn | Token/regex có thể tiêu tốn bộ nhớ và thời gian | Xác thực kích thước, token count, timeout |

## 16. Tự kiểm tra hiểu biết

**Câu 1.** Với nguồn `"A, B C"` và delimiter mặc định, có bao nhiêu token?

**Đáp án:** ba token theo khoảng trắng: `"A,"`, `"B"`, `"C"`. Dấu phẩy không tự động là delimiter.

**Câu 2.** Với nguồn `"A, B C"` và `useDelimiter("\\s*,\\s*")`, hai token là gì?

**Đáp án:** `"A"` và `"B C"`. Khoảng trắng bên trong `"B C"` được giữ vì chỉ có dấu phẩy kèm khoảng trắng mới phân cách.

**Câu 3.** `hasNext()` gọi hai lần liên tiếp có lấy mất hai token không?

**Đáp án:** không. Nó chỉ kiểm tra, không tiêu thụ token; `next()` mới dịch vị trí.

**Câu 4.** Khi `nextInt()` gặp token `"abc"`, sau khi bắt `InputMismatchException` có thể gọi ngay `nextInt()` lần nữa để đọc số kế không?

**Đáp án:** thông thường không. Token lỗi vẫn ở đó. Cần quyết định dừng báo lỗi hoặc tiêu thụ token lỗi qua `next()` trước khi tiếp tục.

**Câu 5.** `Locale.GERMANY` làm Scanner thay đổi dấu ngắt token từ khoảng trắng sang dấu phẩy phải không?

**Đáp án:** không. Locale ảnh hưởng việc diễn giải số theo quy tắc locale; delimiter được điều khiển riêng bằng `useDelimiter`.

**Câu 6.** Khi `Scanner scanner = new Scanner(System.in)` bị đóng, việc gì xảy ra?

**Đáp án:** `Scanner.close()` đóng nguồn triển khai `Closeable`, ở đây là `System.in`. Cần tránh đóng sớm nếu nhiều thành phần còn muốn dùng console input.

**Câu 7.** Vì sao không nên tự dùng dấu phẩy làm delimiter để đọc một tệp CSV tùy ý?

**Đáp án:** dấu phẩy có thể nằm trong trường trích dẫn, trường có thể rỗng hoặc chứa xuống dòng; cần parser hiểu quy tắc CSV.

### Bài tập thực hành

1. Sửa `ScannerDelimiterDemo` để nhận chuỗi `"Đà Nẵng|Hồ Chí Minh|Hà Nội"` và dùng delimiter dấu `|` literal. Kiểm tra ba địa danh có dấu cách vẫn nguyên vẹn.
2. Sửa `ScannerValidationDemo` để báo tổng các số nguyên hợp lệ và số token bị bỏ qua; kiểm tra nguồn `"2 bad 7 10"`.
3. Dùng `ScannerLinePitfallDemo` với chuỗi `"42 abc\nNguyen Van A\n"`. Quan sát phần còn lại của dòng đầu để hiểu rằng `nextLine()` không phải lời gọi chỉ “xóa ký tự xuống dòng”.
4. Tạo một tệp UTF-8 có dữ liệu `"Lan; 21; Hà Nội"`, dùng Scanner đọc qua `Path`/`Reader` và đối chiếu kết quả với nguồn String. Bổ sung xử lý thiếu tệp và xác định rõ bên đóng tài nguyên.

## 17. Ghi nhớ

- **Scanner biến đầu vào ký tự thành token** theo một delimiter regex rồi có thể diễn giải token thành kiểu dữ liệu cần đọc.
- **Nguồn là String, Reader, InputStream hoặc tệp**, và khi nguồn là byte cần chọn charset đúng. Quyền đóng Scanner có thể kéo theo đóng nguồn bên dưới.
- **Delimiter mặc định là khoảng trắng**; `useDelimiter` thay đổi quy tắc tách token, không thay đổi charset hay Locale.
- **`hasNext` kiểm tra; `next` tiêu thụ.** `hasNextInt`/`nextInt` xử lý token số, còn `nextLine` xử lý ranh giới dòng theo hợp đồng khác.
- **Regex không phải chuỗi literal**: escape `.`, `|` và những ký tự đặc biệt, hoặc dùng `Pattern.quote` cho dấu phân cách cố định.
- **Scanner phù hợp dữ liệu token tương đối đơn giản**. Với CSV có trích dẫn, JSON, XML, tệp nhị phân hoặc định dạng nghiêm ngặt, hãy dùng parser hoặc API tương ứng.

### Tài liệu tham khảo

- [Scanner — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html)
- [Pattern — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
- [Locale — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.html)
- [Files — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html)
- [InputStreamReader — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-006">next, nextLine và phần xuống dòng còn lại</a>

<details>
<summary>Xem chi tiết</summary>

# `Scanner.next()`, `nextLine()` và phần xuống dòng còn lại

Khi nhập dữ liệu trong Java, một lỗi thường gặp là: chương trình đọc số bằng `Scanner.nextInt()`, ngay sau đó hỏi tên bằng `nextLine()`, nhưng biến tên lại là chuỗi rỗng hoặc chương trình dường như “bỏ qua” câu hỏi. Tương tự, gọi `next()` trước `nextLine()` có thể khiến kết quả lần đọc sau chỉ là **phần còn lại của dòng**, thay vì một dòng hoàn toàn mới.

Để hiểu và xử lý chính xác, cần phân biệt hai cách đọc trong `java.util.Scanner`:

- **Đọc token:** `next()`, `nextInt()`, `nextDouble()`… làm việc với từng mục được ngăn bằng **delimiter** (dấu phân tách).
- **Đọc dòng:** `nextLine()` đọc **phần còn lại của dòng hiện tại**, đến hết dấu kết thúc dòng và chuyển vị trí đọc sang dòng kế tiếp.

Vấn đề không nằm ở việc `nextLine()` bị lỗi; đó là hệ quả của **vị trí quét hiện tại** sau một thao tác đọc token. Bài học này giải thích cơ chế, minh họa trường hợp có/không có dấu cách còn lại, và giới thiệu các cách viết code đúng tùy loại dữ liệu nhập.

## 1. Token và line khác nhau như thế nào?

### Token: một đơn vị tách bởi delimiter

`Scanner` sử dụng một mẫu delimiter để chia đầu vào thành token. Theo mặc định, delimiter nhận diện các ký tự khoảng trắng theo quy tắc của Java, bao gồm khoảng trắng thông thường và dấu xuống dòng.

Với chuỗi đầu vào:

~~~text
Java Core
27
Nguyễn Văn An
~~~

Các lần gọi `next()` liên tiếp nhận các token như:

~~~text
"Java" -> "Core" -> "27" -> "Nguyễn" -> "Văn" -> "An"
~~~

`next()` bỏ qua delimiter ở trước token kế tiếp, sau đó trả về token tương ứng. Nó **không tự trả lại cả dòng có khoảng trắng** và không có khái niệm “một tên đầy đủ” nếu tên có nhiều từ. Các phương thức như `nextInt()` cũng dùng cơ chế tìm token rồi chuyển đổi token theo kiểu dữ liệu cần đọc.

### Line: phần văn bản tới dấu kết thúc dòng

`nextLine()` hoạt động theo đường **dòng văn bản**. Nó trả về văn bản **còn lại của dòng tại vị trí đang quét**, không bao gồm dấu xuống dòng kết thúc dòng đó. Sau đó vị trí quét được đặt sang đầu dòng tiếp theo.

Với cùng chuỗi đầu vào và vị trí bắt đầu:

~~~java
String firstLine = scanner.nextLine(); // "Java Core"
String secondLine = scanner.nextLine(); // "27"
String thirdLine = scanner.nextLine(); // "Nguyễn Văn An"
~~~

Đây là các dòng minh họa với `scanner` là `Scanner` đã tạo từ đúng dữ liệu trên. Mỗi lần đọc lấy một **dòng**, bao gồm khoảng trắng nằm trong dòng. `nextLine()` có thể trả về `""` nếu vị trí hiện tại đang ngay trước một dấu kết thúc dòng.

**Mô hình dễ nhớ:** `next()` hỏi “token tiếp theo là gì?”, còn `nextLine()` hỏi “từ vị trí hiện tại đến hết dòng còn gì?”.

## 2. Vì sao `next()` và `nextLine()` cho kết quả bất ngờ?

Xét đầu vào một dòng `Java Core`, kết thúc bằng Enter. Để nhìn rõ khoảng trắng và ký tự kết thúc dòng, ta biểu diễn `↵` là Enter:

~~~text
Java Core↵
^^^^       next() lấy token "Java"
    ^^^^^  nextLine() kế tiếp lấy " Core" (bao gồm dấu cách đầu)
         ↵ dấu kết thúc dòng được nextLine() tiêu thụ
~~~

Nếu gọi `next()` trước, nó trả về token `Java`; vị trí quét nằm tại phần phân tách theo sau token. Khi gọi `nextLine()` ngay sau đó, phương thức **không tự bỏ phần còn lại để nhảy tới dòng mới**: nó trả về `" Core"`, có dấu cách ở đầu.

Nếu đầu vào là:

~~~text
Java↵
Core↵
~~~

thì sau `next()` lấy `"Java"`, phần còn lại trước Enter **không có ký tự nội dung**. Vì thế lần `nextLine()` kế tiếp trả về **`""`**, tiêu thụ Enter rồi đưa vị trí đến đầu dòng `Core`.

Hai trường hợp chỉ khác việc có chữ ở phần còn lại của dòng hay không. Do đó, không nên ghi nhớ máy móc rằng “sau `next()` thì `nextLine()` luôn rỗng”. Nó trả **phần còn lại của dòng hiện tại**, có thể rỗng, có khoảng trắng hoặc chứa cả văn bản.

## 3. Trường hợp quen thuộc: `nextInt()` rồi `nextLine()`

Giả sử chương trình yêu cầu tuổi và họ tên:

~~~text
27↵
Nguyễn Văn An↵
~~~

Cách viết dễ gặp:

~~~java
System.out.print("Tuổi: ");
int age = scanner.nextInt();

System.out.print("Họ tên: ");
String fullName = scanner.nextLine();
~~~

Đây là **đoạn minh họa** đặt bên trong một phương thức có `scanner` đang đọc từ đầu vào. Với mẫu dữ liệu trên, `nextInt()` nhận token số `27` nhưng **không đọc cả dòng như `nextLine()`**. Phần còn lại trước Enter trống, nên `nextLine()` ngay sau đó nhận chuỗi `""` và tiêu thụ dấu kết thúc dòng. Tên trên dòng kế tiếp **chưa được đọc**.

Minh họa vị trí quét:

~~~text
Trước:
27↵
Nguyễn Văn An↵

Sau nextInt():
27|↵              ("|" là vị trí đọc đang xét)
Nguyễn Văn An↵

Sau nextLine():
27↵
|Nguyễn Văn An↵  (nextLine đã trả "")
~~~

Lưu ý: đây là mô hình trực quan về **hành vi logic**; không nên suy diễn đây là mô tả byte hoặc chỉ số buffer nội bộ của `Scanner`.

### Nếu sau số có hai dấu cách thì sao?

Với:

~~~text
27··↵
Nguyễn Văn An↵
~~~

ở đây `·` biểu diễn một dấu cách. Sau `nextInt()`, gọi `nextLine()` trả về **hai dấu cách** (`"  "`) chứ không phải chuỗi rỗng. Vì vậy, kiểm tra `fullName.isEmpty()` không đủ để phát hiện mọi trường hợp “chưa đọc tên”.

Nếu trên **cùng một dòng** có nội dung sau số, ví dụ `27 Nguyễn Văn An↵`, `nextLine()` có thể trả về `" Nguyễn Văn An"`. Đó là dữ liệu có ý nghĩa và **không nên tự ý bỏ**.

## 4. Ví dụ Java 17 chạy được: cùng dữ liệu, ba hành vi

Chương trình sau đọc từ `String` để kết quả ổn định, không cần nhập tay khi kiểm tra. Nó tạo nhiều `Scanner` độc lập nhằm so sánh rõ:

1. `next()` + `nextLine()` trên một dòng nhiều từ.
2. `nextInt()` + `nextLine()` ngay tại ranh giới dòng.
3. Đọc **toàn bộ dòng** rồi mới chuyển số, tránh tình trạng sót newline.
4. Phần đuôi dòng chỉ gồm hai dấu cách sau token số.

Lưu tệp **`ScannerTokenLineDemo.java`**:

~~~java
import java.util.Scanner;

public class ScannerTokenLineDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("Java Core\n")) {
            String token = scanner.next();
            String restOfLine = scanner.nextLine();
            System.out.println("Token: [" + token + "]");
            System.out.println("Remaining line: [" + restOfLine + "]");
        }

        String input = "27\nNguyễn Văn An\n";
        try (Scanner scanner = new Scanner(input)) {
            int age = scanner.nextInt();
            String tail = scanner.nextLine();
            String name = scanner.nextLine();

            System.out.println("Age: " + age);
            System.out.println("After nextInt: [" + tail + "]");
            System.out.println("Next full line: [" + name + "]");
        }

        try (Scanner scanner = new Scanner(input)) {
            int age = Integer.parseInt(scanner.nextLine().trim());
            String name = scanner.nextLine();

            System.out.println("Line-based age: " + age);
            System.out.println("Line-based name: [" + name + "]");
        }

        try (Scanner scanner = new Scanner("27  \nAn\n")) {
            int age = scanner.nextInt();
            String tail = scanner.nextLine();
            System.out.println("Age with spaces: " + age);
            System.out.println("Tail with spaces: [" + tail + "]");
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 ScannerTokenLineDemo.java
java ScannerTokenLineDemo
~~~

Kết quả chuẩn (những khoảng trắng bên trong dấu ngoặc vuông là **cố ý**):

~~~text
Token: [Java]
Remaining line: [ Core]
Age: 27
After nextInt: []
Next full line: [Nguyễn Văn An]
Line-based age: 27
Line-based name: [Nguyễn Văn An]
Age with spaces: 27
Tail with spaces: [  ]
~~~

Giải thích:

- Dòng `Remaining line: [ Core]` có **một khoảng trắng** trước `Core` vì `next()` chỉ lấy token đầu tiên `Java`.
- `After nextInt: []` rỗng vì sau `27` là dấu xuống dòng, và `nextLine()` tiêu thụ nó.
- Sau khi `nextLine()` đầu tiên đã lấy phần đuôi rỗng, lần `nextLine()` thứ hai mới trả lại tên đầy đủ.
- Phương án đọc theo dòng `Integer.parseInt(scanner.nextLine().trim())` chuyển văn bản số thành `int` sau khi đã tiêu thụ hết dòng, nên lần `nextLine()` tiếp theo đọc được tên ngay.
- Dòng `Tail with spaces: [  ]` chứa **hai khoảng trắng**, chứng tỏ phương thức trả về phần còn lại của dòng chứ không luôn trả chuỗi rỗng.
- `Scanner` được tạo từ `String` nên không sử dụng `System.in`; đóng bằng `try-with-resources` không làm ảnh hưởng đến luồng nhập chuẩn của ứng dụng.

Khi hiển thị trên terminal không hỗ trợ Unicode hoặc cấu hình encoding không phù hợp, tên tiếng Việt trên màn hình có thể hiển thị khác; hành vi phân tách token/dòng trong `String` vẫn như minh họa.

## 5. Ba cách xử lý đúng và khi nào chọn

### Cách A — Ưu tiên đọc toàn dòng, rồi phân tích dữ liệu

Nếu mỗi câu hỏi/giá trị dùng **một dòng riêng**, cách rõ ràng nhất là thống nhất dùng `nextLine()`:

~~~java
int age = Integer.parseInt(scanner.nextLine().trim());
String fullName = scanner.nextLine();
~~~

Ưu điểm:

- Không trộn hai cơ chế token và line.
- Có thể đọc tên, địa chỉ, câu văn nhiều từ mà không cắt mất khoảng trắng ở giữa.
- Tương đối dễ kiểm tra từng dòng, hiển thị lỗi và yêu cầu người dùng nhập lại.

Nhược điểm:

- Phải tự xử lý lỗi chuyển đổi: `Integer.parseInt` có thể ném `NumberFormatException` khi dòng nhập rỗng, không phải số hoặc vượt phạm vi `int`.
- `trim()` chỉ nên áp dụng cho dòng tuổi khi muốn bỏ khoảng trắng đầu/cuối; với tên, password hoặc dữ liệu mà khoảng trắng có ý nghĩa, **không tự ý trim** nếu đặc tả không yêu cầu.
- Nếu dữ liệu có nhiều trường trên cùng một dòng, cần quy ước tách trường riêng hoặc parse dòng có cấu trúc.

Một cách xác thực an toàn là đọc dòng vào biến trước:

~~~java
String ageLine = scanner.nextLine();
try {
    int age = Integer.parseInt(ageLine.trim());
    // Xử lý tuổi hợp lệ tại đây.
} catch (NumberFormatException ex) {
    // Yêu cầu nhập lại hoặc báo lỗi theo thiết kế ứng dụng.
}
~~~

Đây là **đoạn phương thức minh họa**; muốn tránh lỗi khi hết dữ liệu cần kiểm tra `hasNextLine()` trước. Lưu ý comment trong khối `try`/`catch` biểu diễn nơi ứng dụng xử lý nghiệp vụ cụ thể, không tự thực hiện yêu cầu nhập lại.

### Cách B — Đọc token, rồi chủ động bỏ phần còn lại của dòng

Nếu thực sự muốn dùng `nextInt()` cho tuổi và **biết chắc** phần còn lại của dòng không chứa dữ liệu cần giữ:

~~~java
int age = scanner.nextInt();
scanner.nextLine();          // Tiêu thụ phần còn lại của dòng tuổi.
String fullName = scanner.nextLine();
~~~

Lệnh `scanner.nextLine()` ở giữa không có nghĩa “xóa newline” theo một lệnh chuyên biệt. Nó **đọc và bỏ toàn bộ phần còn lại của dòng hiện tại, kể cả phần nội dung nếu có**, rồi đưa vị trí sang dòng tiếp theo.

**Chỉ sử dụng khi định dạng đầu vào được xác định rõ:** mỗi dòng tuổi chỉ có token tuổi (và có thể có khoảng trắng cuối dòng), còn tên bắt đầu ở dòng kế tiếp. Nếu người dùng nhập `27 Nguyễn Văn An` trên cùng một dòng, lời gọi bổ sung đó sẽ **bỏ mất tên**.

Cũng không cần thêm `nextLine()` sau **mọi** lời gọi `nextInt()`: nếu đang cố ý đọc nhiều token trên cùng một dòng, việc bỏ phần còn lại sẽ làm mất dữ liệu. Quy tắc phải dựa trên **định dạng nhập** và vị trí của dữ liệu tiếp theo.

### Cách C — Đọc toàn dòng rồi dùng `Scanner` riêng để tách token

Nếu mỗi dòng chứa một record nhiều trường, ví dụ:

~~~text
27 Nguyễn Văn An
~~~

có thể lấy nguyên dòng từ luồng chính, sau đó xử lý cấu trúc dòng trong một `Scanner` khác:

~~~java
String record = scanner.nextLine();
try (Scanner lineScanner = new Scanner(record)) {
    int age = lineScanner.nextInt();
    String name = lineScanner.nextLine().stripLeading();
    // age = 27; name = "Nguyễn Văn An"
}
~~~

Đây là **đoạn minh họa**, trong đó `scanner` đọc dữ liệu đầu vào của ứng dụng. `stripLeading()` bỏ khoảng trắng đầu của phần tên sau token số, **không xóa các dấu cách bên trong tên**. Nếu record có cấu trúc cố định hơn (CSV, TSV, JSON…), hãy dùng parser phù hợp với định dạng đó thay vì tự tạo quy ước tách bằng khoảng trắng.

Chỉ nên đặt `lineScanner` trên **chuỗi dòng đã lấy**, tránh tạo nhiều `Scanner` độc lập cùng đọc `System.in` vì mỗi scanner có buffer riêng và có thể đọc trước dữ liệu làm scanner khác không nhận được điều mình mong đợi.

## 6. `hasNext()` và `hasNextLine()`: kiểm tra đúng thứ sắp đọc

Hai phương thức kiểm tra có mục đích khác nhau:

| Gọi kiểm tra | Câu hỏi trả lời | Thường đi với |
|---|---|---|
| `hasNext()` | Còn **token** tiếp theo không? | `next()` |
| `hasNextInt()` | Token tiếp theo có đọc được thành `int` không? | `nextInt()` |
| `hasNextLine()` | Còn **dòng** nào để đọc không? | `nextLine()` |

Ví dụ duyệt từng dòng an toàn:

~~~java
while (scanner.hasNextLine()) {
    String line = scanner.nextLine();
    System.out.println("Line: [" + line + "]");
}
~~~

Còn nếu chỉ lấy các token:

~~~java
while (scanner.hasNext()) {
    String token = scanner.next();
    System.out.println("Token: [" + token + "]");
}
~~~

Hai vòng lặp là các **đoạn minh họa** dùng `scanner` đã được tạo. Một dòng trắng có thể được `nextLine()` trả thành `""`, trong khi `next()` với delimiter mặc định **bỏ qua khoảng trắng** và không đưa ra token rỗng cho dòng trắng đó.

`hasNextInt()` hữu ích để tránh `InputMismatchException` khi token không thể chuyển sang số; tuy nhiên, nếu token sai, bạn phải quyết định **bỏ token đó, báo lỗi hoặc đọc lại**. Gọi `hasNextInt()` nhiều lần mà không di chuyển vị trí đọc sẽ không tự sửa đầu vào.

Các phương thức đọc hoặc kiểm tra trên luồng nhập tương tác có thể **chờ dữ liệu**. `hasNextLine()` không bảo đảm dữ liệu tiếp theo đã có sẵn ngay lập tức.

## 7. Dấu kết thúc dòng, khoảng trắng và delimiter tùy chỉnh

### Dấu kết thúc dòng

Dấu kết thúc dòng thường là LF (`\n`) hoặc CRLF (`\r\n`) tùy nguồn và môi trường. `Scanner.nextLine()` nhận diện các dạng kết thúc dòng theo hợp đồng Java, **trả nội dung dòng mà không kèm dấu kết thúc dòng**.

Nếu vị trí đọc đang trước dấu xuống dòng, `nextLine()` có thể nhận `""`. Một dòng trống thực sự trong file cũng có thể cho `""`. Cùng kết quả chuỗi rỗng **không đủ để suy ra nguyên nhân**; phải biết vị trí đọc và dữ liệu đầu vào.

Nếu dòng cuối **không có dấu kết thúc dòng** nhưng vẫn còn nội dung, `nextLine()` vẫn có thể trả về phần nội dung cuối. Đừng giả định dòng cuối cần bắt buộc có Enter.

### Khoảng trắng còn lại

Dấu cách hoặc tab trước Enter có thể trở thành nội dung trả về từ `nextLine()` sau khi một token được đọc. Để phát hiện “phần còn lại chỉ có khoảng trắng”:

~~~java
String remainder = scanner.nextLine();
boolean onlyWhitespace = remainder.isBlank();
~~~

`isBlank()` cũng trả `true` với chuỗi rỗng. Tuy nhiên, **phát hiện không đồng nghĩa với tự động xóa**: một số định dạng xem khoảng trắng là dữ liệu hợp lệ. Nếu cần bảo toàn dữ liệu, giữ `remainder` nguyên vẹn.

### Delimiter tùy chỉnh

`Scanner.useDelimiter(pattern)` thay đổi quy tắc tách token. Ví dụ:

~~~java
try (Scanner scanner = new Scanner("red,blue,green")) {
    scanner.useDelimiter(",");
    System.out.println(scanner.next()); // red
    System.out.println(scanner.next()); // blue
    System.out.println(scanner.next()); // green
}
~~~

Đây là đoạn Java có thể đặt vào phương thức `main` với import `java.util.Scanner`; nó chỉ minh họa việc thay delimiter. **`nextLine()` không trở thành “đọc đến dấu phẩy”**: nó vẫn làm việc theo dòng văn bản. Vì vậy, thay delimiter tùy chỉnh càng khiến việc pha trộn `next()` và `nextLine()` cần suy xét.

`useDelimiter` nhận **biểu thức chính quy**, không nhất thiết là chuỗi ký tự thuần túy. Với delimiter đặc biệt như dấu chấm (`.`) cần escape đúng regex; truyền thẳng `"."` sẽ mang ý nghĩa “bất kỳ ký tự nào” trong regex.

## 8. Khi nhập từ bàn phím thật: một scanner, vòng đời rõ ràng

Ví dụ tương tác với terminal, dùng **một `Scanner` duy nhất** cho `System.in` và đọc cả hai trường theo dòng:

~~~java
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ConsoleProfile {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.print("Tuổi: ");
        if (!scanner.hasNextLine()) return;
        String rawAge = scanner.nextLine();

        int age;
        try {
            age = Integer.parseInt(rawAge.trim());
        } catch (NumberFormatException ex) {
            System.out.println("Tuổi không hợp lệ.");
            return;
        }

        System.out.print("Họ tên: ");
        if (!scanner.hasNextLine()) return;
        String name = scanner.nextLine();

        System.out.println("Tuổi: " + age);
        System.out.println("Họ tên: " + name);
    }
}
~~~

Lưu thành **`ConsoleProfile.java`**, biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 ConsoleProfile.java
java ConsoleProfile
~~~

Một **phiên chạy mẫu** khi người dùng lần lượt nhập `27` và `Nguyễn Văn An` rồi Enter (hai dòng người dùng nhập là phần phản hồi bàn phím):

~~~text
Tuổi: 27
Họ tên: Nguyễn Văn An
Tuổi: 27
Họ tên: Nguyễn Văn An
~~~

Trong ví dụ, `new Scanner(System.in, StandardCharsets.UTF_8)` quy định cách đọc byte đầu vào thành ký tự. Chọn charset phù hợp với terminal thực tế. `Scanner.close()` cũng đóng nguồn đầu vào nếu nguồn triển khai `Closeable`; bởi `System.in` thường là tài nguyên chung thuộc vòng đời ứng dụng, ví dụ **không tự đóng `System.in`** khi kết thúc thao tác ngắn này.

Nếu ứng dụng tự tạo và sở hữu nguồn (tệp hoặc `StringReader` chẳng hạn), hãy dùng `try-with-resources` để đóng scanner đúng lúc. Với input được caller/framework sở hữu, cần tuân thủ hợp đồng ownership của tầng cấp tài nguyên.

## 9. Ngoại lệ và những nhầm lẫn cần tránh

| Nhầm lẫn | Vì sao sai? | Cách xử lý |
|---|---|---|
| “`next()` đọc một dòng.” | Nó đọc một **token** theo delimiter hiện tại. | Dùng `nextLine()` nếu cần cả dòng. |
| “`nextLine()` sau `nextInt()` luôn rỗng.” | Có thể còn khoảng trắng hoặc văn bản cùng dòng. | Hiểu nó trả về phần còn lại của dòng. |
| “Thêm một `nextLine()` ở mọi nơi là hết lỗi.” | Có thể bỏ nhầm dữ liệu hợp lệ trên cùng dòng. | Chỉ bỏ phần đuôi khi quy ước input cho phép. |
| “Gọi `nextLine()` sẽ lấy dòng tiếp theo cho dù đang đứng giữa dòng.” | Nó bắt đầu từ vị trí **hiện tại**, không tự nhảy trước một dòng. | Xác định vị trí sau thao tác token trước. |
| “Đọc `nextInt()` rồi trim tên là đủ.” | Nếu tên bị bỏ qua thì trim không khôi phục được. | Thiết kế lại cách đọc hoặc tiêu thụ ranh giới dòng có chủ ý. |
| “`hasNext()` và `hasNextLine()` như nhau.” | Một bên kiểm tra token; bên kia kiểm tra line, đặc biệt khác với dòng trống. | Dùng kiểm tra tương ứng với thao tác đọc. |
| “`nextInt()` đọc giá trị nào cũng được.” | Token không phải số hoặc vượt giới hạn có thể ném `InputMismatchException`. | Kiểm tra `hasNextInt()` hoặc đọc dòng rồi parse/validate. |
| “`nextLine()` luôn có dòng.” | Khi dữ liệu cạn, có thể ném `NoSuchElementException`. | Kiểm tra `hasNextLine()` hoặc xử lý hết input. |
| “Có thể tạo nhiều Scanner cùng đọc `System.in`.” | Buffer đọc trước khiến scanner này có thể lấy dữ liệu mà scanner kia cần. | Dùng một scanner với vòng đời rõ ràng. |
| “`scanner.close()` chỉ đóng wrapper.” | Nó đóng input source nếu source là `Closeable`. | Xác định ownership trước khi đóng scanner bọc `System.in`. |

### Trường hợp phân tích số có dấu phẩy thập phân

`Scanner.nextDouble()` xử lý định dạng số theo **locale**. Dấu chấm hoặc dấu phẩy có thể có ý nghĩa khác nhau giữa các locale. Đây là vấn đề **phân tích nội dung token**, tách biệt với vấn đề **ranh giới dòng**: ngay cả khi token số hợp lệ, `nextLine()` sau đó vẫn đọc phần còn lại của dòng.

Khi cần quy ước số ổn định, có thể chủ động `useLocale(...)` theo yêu cầu đầu vào; không nên coi việc đặt locale là cách sửa lỗi newline.

## 10. Tự kiểm tra

**Câu 1.** Input là `"An Bình\n"`. `next()` trả gì, `nextLine()` ngay sau đó trả gì?

> **Đáp án:** `next()` trả `"An"`; `nextLine()` trả `" Bình"`, gồm một khoảng trắng đầu. Dấu `\n` không nằm trong chuỗi trả về.

**Câu 2.** Input là `"42\nHà Nội\n"`. Sau `nextInt()` rồi `nextLine()`, kết quả đọc dòng là gì?

> **Đáp án:** `nextInt()` trả `42`; `nextLine()` trả `""` vì phần còn lại ngay sau token số đến cuối dòng là rỗng. Gọi `nextLine()` tiếp mới đọc `"Hà Nội"`.

**Câu 3.** Input là `"42  Hà Nội\n"` (hai dấu cách giữa số và tên). Có nên bỏ kết quả `nextLine()` đầu tiên sau `nextInt()` không?

> **Đáp án:** Không nếu cần giữ tên. `nextLine()` trả `"  Hà Nội"`; bỏ chuỗi này làm mất dữ liệu trên cùng một dòng.

**Câu 4.** Muốn nhập tuổi ở dòng 1 và họ tên có nhiều từ ở dòng 2, cách nào dễ hiểu và ít lỗi nhất?

> **Đáp án:** `Integer.parseInt(scanner.nextLine().trim())` cho tuổi (có xử lý lỗi), rồi `scanner.nextLine()` cho tên. Cả hai cùng theo **ranh giới dòng**.

**Câu 5.** Input gồm một dòng trống rồi một dòng `"hello"`. Vì sao vòng lặp `nextLine()` và `next()` cho kết quả khác?

> **Đáp án:** `nextLine()` nhận cả dòng trống, trả `""` trước `"hello"`; `next()` bỏ qua khoảng trắng mặc định nên token đầu tiên là `"hello"`.

**Câu 6.** Có nên đóng scanner trong một helper nhận `System.in` của ứng dụng không?

> **Đáp án:** Chỉ khi hợp đồng cho phép đóng nguồn đó. Vì đóng `Scanner` có thể đóng luôn `System.in`, helper chỉ mượn nguồn chung cần để chủ sở hữu quản lý vòng đời.

## Ghi nhớ

**`next()`/`nextInt()` đọc token; `nextLine()` đọc phần còn lại của dòng tại vị trí hiện tại và tiêu thụ dấu kết thúc dòng.** Phần còn lại ấy có thể rỗng, chỉ có khoảng trắng, hoặc chứa văn bản thực sự. Hãy chọn một chiến lược nhập nhất quán: nếu dữ liệu được tổ chức theo dòng, **đọc dòng trước rồi mới parse**; nếu trộn token và dòng, chỉ chủ động bỏ phần đuôi khi chắc chắn nó không mang dữ liệu cần giữ.

**Tài liệu tham khảo:** [Java SE 17 — `java.util.Scanner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-007">Đọc số với Scanner theo locale và radix</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc số với `Scanner` theo locale và radix

## 1. Vấn đề: cùng một token, nhiều cách hiểu số

`java.util.Scanner` có thể đọc văn bản từ `String`, tệp, `InputStream` hoặc nguồn ký tự, tách thành các **token** rồi chuyển token thành `int`, `long`, `double`, `BigDecimal`, `BigInteger` và những kiểu khác. Khi đọc số, hai cấu hình quan trọng là:

- **Locale**: quy định cách hiểu dấu thập phân, dấu phân nhóm hàng nghìn, dấu cộng/trừ theo quy tắc địa phương và một số cách biểu diễn số khác.
- **Radix**: **cơ số** dùng để hiểu chữ số của **số nguyên**, từ 2 đến 36.

Một chuỗi như `1,234.50` thường được hiểu là một nghìn hai trăm ba mươi tư phẩy năm dưới `Locale.US`. Dưới `Locale.GERMANY`, cách viết tương ứng là `1.234,50`. Mặt khác, token `FF` không phải số nguyên hệ thập phân, nhưng có giá trị `255` khi đọc trong hệ 16.

**Ý tưởng cốt lõi:** `Scanner` không “tự đoán” ý định của dữ liệu. Chương trình phải thiết lập locale, radix và quy tắc phân tách token khớp với **định dạng đầu vào**. Một file được tạo theo quy ước của hệ thống khác không nhất thiết phù hợp với locale mặc định của máy đang chạy.

## 2. Scanner tách token trước, rồi mới phân tích số

Mặc định, `Scanner` dùng dấu trắng (whitespace) làm delimiter: dấu cách, tab, xuống dòng theo mẫu delimiter của Scanner. Ví dụ đầu vào:

```text
12 34
56
```

Ba token lần lượt là `12`, `34`, `56`. Các lời gọi `nextInt()` nhận từng token và phân tích chúng thành số nguyên.

Các API liên quan:

| API | Ý nghĩa |
| --- | --- |
| `hasNextInt()` | Kiểm tra token kế tiếp có thể đọc thành `int` theo radix hiện tại không, **không tiêu thụ token** |
| `nextInt()` | Tiêu thụ token kế tiếp và phân tích thành `int` với radix hiện tại |
| `hasNextInt(radix)` | Kiểm tra `int` theo cơ số truyền vào |
| `nextInt(radix)` | Tiêu thụ và đọc `int` theo cơ số truyền vào |
| `hasNextDouble()` / `nextDouble()` | Kiểm tra/đọc số thực theo locale hiện tại |
| `hasNextBigDecimal()` / `nextBigDecimal()` | Đọc số thập phân chính xác dạng `BigDecimal` |
| `useLocale(locale)` | Đặt locale mà Scanner áp dụng cho việc nhận diện số |
| `useRadix(radix)` | Đặt **radix mặc định** cho các thao tác số nguyên có hỗ trợ radix |
| `useDelimiter(pattern)` | Thay quy tắc tách token; không đổi locale hay radix |

`hasNextInt()` và `hasNextDouble()` kiểm tra **token đầy đủ** theo quy tắc số. Chúng không phải công cụ tìm một đoạn số bất kỳ trong giữa văn bản. Nếu token sai định dạng, `nextInt()` hoặc `nextDouble()` không tự sửa token. Hãy xác định delimiter trước khi phân tích số.

## 3. Locale: dấu thập phân và phân nhóm hàng nghìn

Thường gặp hai nhóm quy ước:

| Định dạng nhập | Locale tương ứng | Giá trị toán học |
| --- | --- | ---: |
| `1,234.50` | `Locale.US` | 1234.5 |
| `1.234,50` | `Locale.GERMANY` | 1234.5 |
| `12.75` | `Locale.US` | 12.75 |
| `12,75` | `Locale.GERMANY` | 12.75 |

Trong ví dụ trên:

- `Locale.US`: dấu phẩy `,` phân nhóm hàng nghìn; dấu chấm `.` là dấu thập phân.
- `Locale.GERMANY`: dấu chấm `.` phân nhóm; dấu phẩy `,` là dấu thập phân.

Đây là hai trường hợp cụ thể, không phải mọi locale đều dùng đúng hai quy tắc trên. Một locale có thể dùng khoảng trắng đặc biệt để phân nhóm, ký hiệu số khác hoặc quy tắc địa phương riêng.

Ví dụ tạo Scanner với locale tường minh:

```java
try (Scanner scanner = new Scanner("1.234,50 42")
        .useLocale(Locale.GERMANY)) {
    double amount = scanner.nextDouble();
    int count = scanner.nextInt();
}
```

Đây là **đoạn mã minh họa**, cần import `java.util.Scanner` và `java.util.Locale`; các biến `amount` và `count` chỉ tồn tại trong khối. Trong trường hợp này, `amount` bằng `1234.5` và `count` bằng `42`.

Không nên đổi mọi dấu phẩy thành dấu chấm trước khi parse: `"1.234,50"` có cả dấu nhóm và dấu thập phân, thay thế tùy tiện có thể tạo chuỗi không hợp lệ hoặc đổi ý nghĩa dữ liệu. Hãy chọn locale đúng rồi dùng parser tương ứng.

### Locale mặc định và khả năng tái lập

Nếu không gọi `useLocale`, Scanner khởi tạo với locale mặc định dùng cho định dạng của JVM (thông thường dựa trên `Locale.getDefault(Locale.Category.FORMAT)` ở thời điểm tạo). Giá trị này có thể khác giữa môi trường phát triển, máy chủ và máy người dùng.

Ví dụ file do một dịch vụ quốc tế xuất cố định `1,234.50`: chương trình nên chọn `Locale.US` (hoặc định dạng máy được đặc tả) ngay cả khi máy chạy đặt locale tiếng Đức. **Không lấy locale của máy làm đặc tả file**.

Cần phân biệt **locale của Scanner khi đọc**, **locale khi in với `printf`/`String.format`**, và **charset khi chuyển byte ↔ ký tự**:

- `Scanner.useLocale(Locale.GERMANY)` chọn cách hiểu `1.234,50`.
- `System.out.printf(Locale.US, "%.2f", value)` chọn cách **in** `1234.50`.
- `StandardCharsets.UTF_8` điều khiển cách chuyển chữ thành byte; **không xác định dấu thập phân**.

## 4. Radix: cơ số từ 2 đến 36 cho số nguyên

Radix là cơ số dùng diễn giải vị trí chữ số. Với `nextInt(radix)`:

| Token | Radix | Giá trị thập phân |
| --- | ---: | ---: |
| `1011` | 2 | 11 |
| `17` | 8 | 15 |
| `10` | 10 | 10 |
| `FF` | 16 | 255 |
| `Z` | 36 | 35 |

Ví dụ `1011` ở cơ số 2 tương đương `1×2³ + 0×2² + 1×2¹ + 1×2⁰ = 11`. Radix hợp lệ của các API Scanner đọc số nguyên là từ **`Character.MIN_RADIX` (2)** đến **`Character.MAX_RADIX` (36)**. Radix không hợp lệ, ví dụ 1 hoặc 37, gây `IllegalArgumentException` theo hợp đồng API.

Hai cách chọn radix:

```java
int a = scanner.nextInt(16); // radix 16 chỉ cho lần đọc này
scanner.useRadix(2);         // đặt radix mặc định
int b = scanner.nextInt();   // đọc token tiếp theo ở radix 2
```

Đây là **đoạn mã minh họa**, `scanner` phải được khởi tạo và có token phù hợp. Mặc định ban đầu, Scanner dùng radix **10**. `useRadix(16)` ảnh hưởng các lời gọi không truyền radix của những phương thức số nguyên tương ứng, ví dụ `nextInt()`, `hasNextInt()`, `nextLong()`. Lời gọi `nextInt(10)` vẫn yêu cầu radix 10 cho lần đó.

**Radix không phải locale.** Cơ số giải thích chữ số của **số nguyên**; locale giải thích quy ước số theo địa phương. `nextDouble()` và `nextBigDecimal()` không có overload `nextDouble(radix)` hay `nextBigDecimal(radix)`; `useRadix(16)` không biến số thực thành “hexadecimal floating point parser”.

Khi nhận số nguyên dạng hex từ định dạng đã thỏa thuận, ghi nhận rõ là radix 16 và cung cấp các chữ số như `FF`, không giả định API `nextInt(16)` sẽ tự xử lý mọi cách viết có tiền tố `0x`, dấu `#` hoặc dấu phân tách do một ngôn ngữ lập trình cụ thể quy định.

## 5. Ví dụ Java 17+ đầy đủ: đọc theo locale và radix

Tạo file **`ScannerLocaleRadixDemo.java`**:

```java
import java.util.Locale;
import java.util.Scanner;

public class ScannerLocaleRadixDemo {
    public static void main(String[] args) {
        try (Scanner us = new Scanner("1,234.50 42")
                .useLocale(Locale.US)) {
            System.out.printf(Locale.ROOT, "US double=%.2f%n", us.nextDouble());
            System.out.println("US int=" + us.nextInt());
        }

        try (Scanner de = new Scanner("1.234,50 42")
                .useLocale(Locale.GERMANY)) {
            System.out.printf(Locale.ROOT, "DE double=%.2f%n", de.nextDouble());
            System.out.println("DE int=" + de.nextInt());
        }

        try (Scanner radix = new Scanner("FF 1011 17 10")
                .useLocale(Locale.US)) {
            System.out.println("hex FF=" + radix.nextInt(16));
            System.out.println("binary 1011=" + radix.nextInt(2));

            radix.useRadix(8);
            System.out.println("default octal 17=" + radix.nextInt());

            radix.useRadix(10);
            System.out.println("default decimal 10=" + radix.nextInt());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ScannerLocaleRadixDemo.java
java ScannerLocaleRadixDemo
```

Đầu ra:

```text
US double=1234.50
US int=42
DE double=1234.50
DE int=42
hex FF=255
binary 1011=11
default octal 17=15
default decimal 10=10
```

Tại sao kết quả đúng?

1. Hai Scanner đầu vào khác cách đặt dấu phân nhóm/thập phân, nhưng `useLocale` khiến cả hai đọc số toán học `1234.5`. `printf(Locale.ROOT, "%.2f")` cố định cách hiển thị dấu chấm trong đầu ra để kết quả dự đoán được trên nhiều máy.
2. `nextInt(16)` đọc `FF` thành `255`; `nextInt(2)` đọc `1011` thành `11`.
3. `useRadix(8)` đặt cơ số mặc định của scanner thứ ba; token `17` tương đương `1×8+7=15`.
4. `useRadix(10)` đặt lại cơ số; token `10` được đọc thành `10`.
5. Các Scanner đều dùng chuỗi trong bộ nhớ, không phụ thuộc tệp dữ liệu hoặc `System.in`; `try-with-resources` đóng đúng tài nguyên.

## 6. Kiểm tra token trước khi đọc và xử lý lỗi

Khi nội dung do người dùng nhập có thể sai, dùng `hasNextInt(radix)` hoặc `hasNextDouble()` trước lời gọi `next...`. Hai phương thức `hasNext...` **không tiêu thụ token**, nên cần tiến vị trí bằng `next...` khi thành công, hoặc `next()` để bỏ token sai và tránh vòng lặp vô hạn.

Tạo file **`ScannerValidationDemo.java`**:

```java
import java.util.Scanner;

public class ScannerValidationDemo {
    public static void main(String[] args) {
        try (Scanner input = new Scanner("FF G1 2147483648")) {
            if (input.hasNextInt(16)) {
                System.out.println("hex=" + input.nextInt(16));
            }

            if (!input.hasNextInt(16)) {
                System.out.println("invalid=" + input.next());
            }

            if (!input.hasNextInt()) {
                System.out.println("out of int=" + input.next());
            }
        }
    }
}
```

Biên dịch, chạy:

```bash
javac ScannerValidationDemo.java
java ScannerValidationDemo
```

Đầu ra:

```text
hex=255
invalid=G1
out of int=2147483648
```

- `FF` là token số nguyên hệ 16 hợp lệ.
- `G1` chứa `G`, không phải chữ số hợp lệ của hệ 16. `hasNextInt(16)` trả `false`; gọi `next()` để tiêu thụ token hỏng.
- `2147483648` vượt `Integer.MAX_VALUE` là `2147483647`. Dù chỉ có chữ số thập phân, nó **không phải `int` hợp lệ**. `hasNextInt()` trả `false`; nếu cần số này, cân nhắc `nextLong()` hoặc `nextBigInteger()` sau khi xác định quy tắc dữ liệu.

Khi gọi trực tiếp `nextInt()`, `nextDouble()` hoặc `nextBigDecimal()` mà token không khớp định dạng/số vượt phạm vi, `Scanner` có thể ném `InputMismatchException`. Nếu đầu vào đã hết mà vẫn gọi `next...`, có thể nhận `NoSuchElementException`; dùng scanner đã đóng gây `IllegalStateException`. Đừng trộn chúng thành một lỗi chung “EOF”; từng trường hợp cần thông báo hoặc chính sách xử lý riêng.

Mẫu vòng lặp xử lý token không tin cậy:

```java
while (scanner.hasNext()) {
    if (scanner.hasNextInt(16)) {
        int value = scanner.nextInt(16);
        // xử lý value
    } else {
        String invalidToken = scanner.next();
        // ghi nhận token sai / báo lỗi cho người dùng
    }
}
```

Đây là **đoạn mã minh họa**, `scanner` phải được khai báo và khởi tạo ở nơi gọi. `hasNext()` cho biết còn token theo delimiter, `hasNextInt(16)` kiểm tra parse không tiêu thụ token, `nextInt(16)`/`next()` làm tiến luồng đúng một token mỗi vòng.

## 7. Delimiter và dấu phẩy thập phân là hai chuyện khác nhau

Scanner mặc định phân tách token bằng dấu trắng. Token `1,5;2,75` **không tự động** trở thành hai số theo locale Đức; ký tự `;` chưa được cấu hình làm delimiter.

Nếu định dạng đầu vào dùng dấu chấm phẩy phân tách trường và dấu phẩy làm dấu thập phân, chỉ rõ **cả delimiter và locale**:

```java
import java.util.Locale;
import java.util.Scanner;

public class ScannerSemicolonDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("1,5;2,75")
                .useDelimiter(";")
                .useLocale(Locale.GERMANY)) {
            System.out.println(scanner.nextDouble());
            System.out.println(scanner.nextDouble());
        }
    }
}
```

Chạy:

```bash
javac ScannerSemicolonDemo.java
java ScannerSemicolonDemo
```

Đầu ra (Java `println(double)` in dạng số Java với dấu chấm):

```text
1.5
2.75
```

`useDelimiter(";")` quy định **ranh giới token**, còn `useLocale(Locale.GERMANY)` cho phép dấu phẩy trong mỗi token mang nghĩa dấu thập phân. Nếu delimiter lại là dấu phẩy, đầu vào `1,5;2,75` có thể bị cắt ở vị trí thuộc nội dung số, khiến parser thất bại hoặc hiểu sai.

Với tệp CSV, dấu phẩy có thể vừa là phần dữ liệu trong trường được quote vừa là delimiter. `Scanner.useDelimiter(",")` không phải CSV parser hoàn chỉnh: nó không hiểu escaping, trường chứa dấu phẩy trong dấu ngoặc kép hoặc xuống dòng bên trong trường. Với CSV thực sự nên dùng parser tuân chuẩn định dạng; với `Scanner`, chỉ dùng khi cú pháp đầu vào đủ đơn giản và đã được kiểm soát.

## 8. Khi cần số thập phân chính xác: `BigDecimal`

`nextDouble()` trả về `double` dạng IEEE 754, có thể không biểu diễn chính xác các giá trị thập phân như `0.1`. Điều đó phù hợp cho nhiều tính toán khoa học, nhưng không phải lựa chọn mặc định cho số tiền hay nghiệp vụ đòi hỏi chính xác thập phân.

`Scanner.nextBigDecimal()` đọc token số thực thỏa locale dưới dạng `java.math.BigDecimal`, hỗ trợ tính toán thập phân chính xác theo quy tắc của `BigDecimal`.

Tạo file **`ScannerBigDecimalDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Scanner;

public class ScannerBigDecimalDemo {
    public static void main(String[] args) {
        try (Scanner input = new Scanner("1.234,50;2,75")
                .useDelimiter(";")
                .useLocale(Locale.GERMANY)) {
            BigDecimal first = input.nextBigDecimal();
            BigDecimal second = input.nextBigDecimal();
            BigDecimal total = first.add(second);

            System.out.println("first=" + first.toPlainString());
            System.out.println("second=" + second.toPlainString());
            System.out.println("sum=" + total.toPlainString());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ScannerBigDecimalDemo.java
java ScannerBigDecimalDemo
```

Kết quả:

```text
first=1234.50
second=2.75
sum=1237.25
```

`Scanner` nhận diện dấu nhóm `.` và dấu thập phân `,` theo `Locale.GERMANY`. `BigDecimal.toPlainString()` hiển thị số ở dạng chữ số thập phân với dấu chấm, không tự tái dùng định dạng locale gốc. Nếu cần xuất ra theo quy ước địa phương, định dạng **đầu ra** bằng API định dạng thích hợp (ví dụ `NumberFormat`) và chỉ định locale rõ ràng.

Radix không áp dụng cho `nextBigDecimal()`; cơ số không quyết định dấu thập phân hoặc số chữ số sau phần thập phân.

## 9. Tương tác giữa `nextInt()` và `nextLine()`

`nextInt()` đọc **token số**, thường dừng trước ký tự xuống dòng; `nextLine()` lấy **phần còn lại của dòng hiện tại**, loại bỏ ký tự kết thúc dòng. Vì vậy sau một `nextInt()`, `nextLine()` có thể trả về chuỗi rỗng.

Ví dụ:

```java
import java.util.Scanner;

public class ScannerLineDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("42\nXin chào\n")) {
            int number = scanner.nextInt();
            String rest = scanner.nextLine();
            String nextLine = scanner.nextLine();

            System.out.println("number=" + number);
            System.out.println("rest empty=" + rest.isEmpty());
            System.out.println("next=" + nextLine);
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ScannerLineDemo.java
java ScannerLineDemo
```

Đầu ra:

```text
number=42
rest empty=true
next=Xin chào
```

Đây là hệ quả của việc trộn đọc **token** với đọc **dòng**, không phải lỗi locale hay radix. Nếu mỗi dòng đại diện cho một bản ghi, một cách khác là lấy cả dòng bằng `nextLine()` rồi dùng Scanner riêng cho chuỗi dòng đó với delimiter, locale và radix được chỉ định tường minh. Tránh bỏ `nextLine()` một cách máy móc nếu phần còn lại của dòng có dữ liệu cần giữ.

## 10. Đọc số từ tệp và vấn đề charset

Nếu nguồn là tệp byte, **charset** giải quyết chuyển byte thành văn bản, còn **locale/radix** giải quyết chuyển token văn bản thành số. Chúng độc lập:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Scanner;

public class ScanNumbersFile {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("numbers.txt");

        try (Scanner scanner = new Scanner(path, StandardCharsets.UTF_8)
                .useLocale(Locale.GERMANY)
                .useRadix(10)) {
            while (scanner.hasNext()) {
                if (scanner.hasNextDouble()) {
                    System.out.println(scanner.nextDouble());
                } else {
                    System.err.println("Token không phải số: " + scanner.next());
                }
            }
        }
    }
}
```

Chương trình này **đầy đủ về mã Java 17+**, nhưng yêu cầu có tệp `numbers.txt` tồn tại trong thư mục làm việc; do nội dung tệp có thể khác nhau, không công bố stdout cố định. `new Scanner(path, StandardCharsets.UTF_8)` chỉ rõ charset của file; `useLocale(Locale.GERMANY)` chỉ rõ cách hiểu dấu thập phân/nhóm; `useRadix(10)` chỉ có ý nghĩa cho những lần đọc số **nguyên** hỗ trợ radix và không đổi cách `nextDouble()` hoạt động.

Khi mở `Scanner` trên `System.in` và đóng Scanner, luồng chuẩn nhập bên dưới cũng bị đóng; điều này có thể gây lỗi cho những phần khác của ứng dụng cần đọc console tiếp. Hãy xác định rõ nơi sở hữu tài nguyên. Với Scanner tự tạo từ `String`, `try-with-resources` là cách dùng đơn giản và an toàn.

`Scanner` có khả năng phân tích token mạnh, nhưng việc dùng biểu thức chính quy và phân tích từng token khiến nó thường không phải lựa chọn nhanh nhất cho dữ liệu cực lớn. Với ứng dụng cần throughput cao, có thể đọc theo dòng hoặc byte rồi parse bằng API chuyên dụng và benchmark, trong khi vẫn giữ locale/định dạng nhất quán.

## 11. Locale, radix, delimiter và định dạng đầu ra: đối chiếu nhanh

| Yêu cầu | Cấu hình/API cần quan tâm | Không nên nhầm với |
| --- | --- | --- |
| Nhận `1.234,50` thành `1234.5` | `useLocale(Locale.GERMANY)`, `nextDouble()` | `useRadix(10)` |
| Nhận `FF` thành `255` | `nextInt(16)` hoặc `useRadix(16)` | `useLocale(Locale.US)` |
| Tách `1,5;2,75` thành hai token | `useDelimiter(";")` | Charset UTF-8 |
| Giữ chính xác số tiền | `nextBigDecimal()` với locale đúng | `nextDouble()` rồi cộng trực tiếp |
| Đọc bytes tệp UTF-8 | `new Scanner(path, StandardCharsets.UTF_8)` | Locale của số |
| In số theo quy ước cố định | `printf(Locale.ROOT, ...)` hoặc `String.format(locale, ...)` | Locale của Scanner |
| Phát hiện token không hợp lệ | `hasNextInt(radix)`, `hasNextDouble()` | `hasNext()` chỉ kiểm tra có token |

Một vài lỗi thường gặp:

- Để Scanner dùng locale hệ thống khiến dữ liệu cùng định dạng chạy đúng trên máy này nhưng sai trên máy khác.
- Tưởng `"1,5"` luôn là `1.5`: dưới locale không dùng dấu phẩy thập phân, token có thể bị từ chối hoặc được hiểu theo quy tắc khác.
- Đặt `useRadix(16)` rồi gọi `nextDouble()` và chờ kết quả “số thực hệ 16”.
- Gọi `hasNextInt()` liên tục mà không tiêu thụ token sai khi trả `false`: vòng lặp có thể đứng mãi ở một chỗ.
- Dùng `nextInt()` cho số vượt phạm vi `int`, rồi nghĩ rằng token chứa chữ cái; kiểm tra cả radix lẫn giới hạn giá trị.
- Đọc từng token rồi gọi `nextLine()` mà không xét phần còn lại của dòng.
- Dùng delimiter dấu phẩy cho văn bản trong đó dấu phẩy thuộc chính số thập phân.
- Sử dụng `Double.parseDouble(...)` để thay thế Scanner locale-aware: `Double.parseDouble` chấp nhận cú pháp số Java không dựa trên locale, nên `"1,5"` không trở thành `1.5` chỉ vì locale máy là tiếng Đức.

**Bài tự luyện:** Cho văn bản `"1.234,50;FF;1011"`. Hãy chọn delimiter `;`, dùng `Locale.GERMANY` để đọc token đầu bằng `nextBigDecimal()`, tiếp đó `nextInt(16)` và `nextInt(2)`. Các giá trị mong đợi lần lượt là **`1234.50`, `255`, `11`**. Giải thích vì sao đổi locale mà không đặt delimiter là chưa đủ.

**Ghi nhớ:** Khi dùng `Scanner` đọc số, cần định nghĩa **ba tầng khác nhau**: delimiter chia token, locale diễn giải dấu và cách viết số, radix diễn giải chữ số của số nguyên. Chọn cả ba theo hợp đồng dữ liệu, xác thực token trước khi tiêu thụ và chỉ định charset riêng nếu nguồn là byte.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-008">hasNext, InputMismatchException và token không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Scanner.hasNext(), InputMismatchException và xử lý token không hợp lệ

## 1. Bài toán: đầu vào có thể chứa token sai kiểu

**Scanner** trong gói **java.util** dùng để đọc văn bản, tách dữ liệu thành **token** theo một biểu thức phân cách (**delimiter**) rồi chuyển token sang kiểu Java như **int**, **double**, **boolean** hoặc lấy dưới dạng **String**. Delimiter mặc định là khoảng trắng theo quy tắc của **Character.isWhitespace**, thường bao gồm khoảng trắng, tab và dấu xuống dòng.

Ví dụ với đầu vào:

~~~text
12 abc 3.5 -7 2147483648 25
~~~

Có sáu token: **12**, **abc**, **3.5**, **-7**, **2147483648**, **25**. Nếu chương trình kỳ vọng tất cả là **int** thì **abc**, **3.5** và **2147483648** không đọc được bằng **nextInt()** (số cuối vượt giới hạn int 32-bit có dấu). Sai token không đồng nghĩa với việc luồng đã hết. Cần phân biệt:

- **Còn token không?** → **hasNext()**.
- **Token kế tiếp có thể đọc thành int không?** → **hasNextInt()**.
- **Đọc và chuyển token thành int** → **nextInt()**.
- **Token sai kiểu khi gọi nextInt()** → **InputMismatchException**.
- **Không còn token mà vẫn gọi nextInt()** → **NoSuchElementException**.

Điểm quan trọng nhất là **token gây InputMismatchException không tự bị Scanner bỏ qua**. Nếu cứ gọi lại **nextInt()** trên cùng token lỗi, chương trình có thể lặp lỗi vô hạn. Phải quyết định **bỏ token**, **bỏ cả dòng**, **thông báo người dùng nhập lại**, hoặc **dừng với thông báo lỗi** tùy bài toán.

## 2. Cơ chế token: delimiter, vị trí đọc và việc tiêu thụ dữ liệu

Scanner duy trì một vị trí đọc trên nguồn văn bản. Với delimiter mặc định, các dấu cách hoặc xuống dòng ngăn cách token; Scanner thường bỏ qua delimiter trước khi lấy token kế tiếp.

~~~text
Dữ liệu:       "  12   abc \n 25 "
Token:            12   abc    25
Sau nextInt():          ↑
                  token 12 đã được tiêu thụ;
                  token kế tiếp là abc.
~~~

| Phương thức | Trả về | Có tiêu thụ token hợp lệ? | Ý nghĩa |
| --- | --- | --- | --- |
| **hasNext()** | boolean | **Không** | Có một token kế tiếp bất kỳ hay không |
| **next()** | String | **Có** | Lấy token kế tiếp dưới dạng chuỗi |
| **hasNextInt()** | boolean | **Không** | Token kế tiếp chuyển được thành int theo radix/locale của Scanner không |
| **nextInt()** | int | **Có, nếu thành công** | Chuyển token kế tiếp thành số nguyên int |
| **hasNextDouble()** | boolean | **Không** | Token kế tiếp chuyển được thành double theo quy tắc tương ứng không |
| **nextDouble()** | double | **Có, nếu thành công** | Chuyển token kế tiếp thành double |

**hasNext() không phải là hasNextInt().** Khi token tiếp theo là **abc**, **hasNext() == true** nhưng **hasNextInt() == false**. Khi token là **3.5**, **hasNext()** vẫn là true, trong khi **hasNextInt()** là false dù **hasNextDouble()** có thể true với locale cho phép dấu chấm thập phân.

**hasNextInt() không lấy token**: hai lần gọi liên tiếp **hasNextInt()** sẽ kiểm tra cùng một token nếu chưa có thao tác khác tiêu thụ nó. Khi **nextInt()** chuyển đổi thành công, token mới được tiêu thụ và vị trí Scanner tiến lên.

Chú ý **hasNext() / hasNextInt()** có thể **chặn (block)** để chờ dữ liệu từ nguồn tương tác hoặc mạng. Một lần hasNext() trả true cũng không phải cam kết tuyệt đối lần next() tiếp theo sẽ không chặn, theo hợp đồng API. Không nên gọi chúng như thao tác bất đồng bộ, tức thời.

## 3. InputMismatchException là gì? Khi nào xảy ra?

**java.util.InputMismatchException** là một **unchecked exception** (kế thừa **NoSuchElementException**). Nó xảy ra khi token đang có **không khớp kiểu hoặc phạm vi giá trị** mà phương thức **nextXxx()** yêu cầu.

Ví dụ với **nextInt()**:

| Token kế tiếp | nextInt() (hệ thập phân) | Giải thích |
| --- | --- | --- |
| **42** | 42 | Hợp lệ |
| **-7** | -7 | Hợp lệ |
| **abc** | InputMismatchException | Không biểu diễn số nguyên |
| **3.14** | InputMismatchException | Không biểu diễn int |
| **2147483648** | InputMismatchException | Lớn hơn **Integer.MAX_VALUE = 2147483647** |
| **-2147483649** | InputMismatchException | Nhỏ hơn **Integer.MIN_VALUE = -2147483648** |
| Không còn token | NoSuchElementException | EOF/hết token, khác với sai kiểu |

Quy tắc nhận diện số cũng bị ảnh hưởng bởi **locale** và **radix**. Đừng mặc định mọi ký hiệu dấu thập phân/nhóm chữ số đều được diễn giải giống nhau trong mọi môi trường. Những ví dụ dưới đây dùng token ASCII đơn giản và đặt locale tường minh ở chỗ cần thiết.

### 3.1. Token gây lỗi vẫn còn nguyên để xử lý

Hãy hình dung:

~~~text
Scanner đang ở token "abc".
nextInt() → InputMismatchException.
Scanner vẫn ở token "abc".
nextInt() lần nữa → InputMismatchException lần nữa.
next() → "abc"; tiêu thụ token sai.
nextInt() tiếp theo → thử token đứng sau "abc".
~~~

Theo tài liệu Java, khi Scanner ném **InputMismatchException**, Scanner **không bỏ qua token** gây lỗi, để ứng dụng còn có thể đọc hoặc bỏ qua bằng phương thức khác. Đây là lý do phổ biến khiến vòng lặp **while (scanner.hasNext()) { scanner.nextInt(); }** bị dừng bởi exception ngay tại dữ liệu sai; nếu catch nhưng không tiến vị trí, vòng lặp có thể mắc kẹt.

**Không dùng hasNextInt() đơn độc làm điều kiện vòng lặp khi muốn tiếp tục sau token lỗi**:

~~~java
while (scanner.hasNextInt()) {
    System.out.println(scanner.nextInt());
}
~~~

Mảnh code trên **hợp lệ** nếu chỉ chấp nhận *dãy int liên tiếp đến token không phải int đầu tiên*. Nhưng khi dữ liệu là **1 bad 2**, vòng lặp chỉ đọc **1**, dừng tại **bad**, và không bao giờ tới **2**. Muốn **bỏ token lỗi rồi tiếp tục**, dùng vòng lặp **hasNext()** và nhánh xử lý token sai.

## 4. Chiến lược khuyến nghị: hasNext() + hasNextInt() + next()

Với một luồng token mà mục tiêu là **lọc số nguyên hợp lệ**, cách dễ đọc là:

1. Dùng **hasNext()** để biết còn token.
2. Dùng **hasNextInt()** để kiểm tra kiểu, **không làm tiến vị trí**.
3. Nếu hợp lệ, dùng **nextInt()** để tiêu thụ và sử dụng số.
4. Nếu không hợp lệ, dùng **next()** để **tiêu thụ đúng một token** và ghi nhận lỗi/bỏ qua.
5. Lặp đến khi hết token.

Ví dụ Java 17+ chạy độc lập. Lưu thành **ScannerTokenDemo.java**:

~~~java
import java.util.Locale;
import java.util.Scanner;

public class ScannerTokenDemo {
    public static void main(String[] args) {
        String data = "12 abc 3.5 -7 2147483648 25";

        try (Scanner scanner = new Scanner(data).useLocale(Locale.US)) {
            while (scanner.hasNext()) {
                if (scanner.hasNextInt()) {
                    int number = scanner.nextInt();
                    System.out.println("INT: " + number);
                } else {
                    String invalid = scanner.next();
                    System.out.println("INVALID: " + invalid);
                }
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ScannerTokenDemo.java
java ScannerTokenDemo
~~~

Kết quả chính xác:

~~~text
INT: 12
INVALID: abc
INVALID: 3.5
INT: -7
INVALID: 2147483648
INT: 25
~~~

**12**, **-7** và **25** đọc thành công. **abc** không là số, **3.5** là số thập phân chứ không phải int, **2147483648** vượt giới hạn int. Mỗi lần gặp token sai, **next()** tiêu thụ nó; vì thế vòng lặp **luôn có tiến triển** và vẫn đọc được **25** sau token lỗi.

**Locale.US** ở đây giúp làm rõ môi trường phân tích số. **nextInt()** trả về **int** thật; không cần dùng **Integer.parseInt(scanner.next())** một lần nữa sau khi đã kiểm tra thành công, trừ khi ứng dụng cố ý tự kiểm soát quy tắc kiểm tra theo String.

## 5. Chiến lược khác: try/catch InputMismatchException

Khi code cố tình gọi **nextInt()** trực tiếp, có thể dùng try/catch để xử lý sai kiểu. Điểm bắt buộc là nhánh **catch phải đảm bảo tiến vị trí** hoặc quyết định dừng, tránh gặp lại token lỗi.

Ví dụ Java 17+ chạy độc lập, lưu thành **ScannerMismatchDemo.java**:

~~~java
import java.util.InputMismatchException;
import java.util.Scanner;

public class ScannerMismatchDemo {
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner("8 oops 9")) {
            while (scanner.hasNext()) {
                try {
                    int number = scanner.nextInt();
                    System.out.println("number=" + number);
                } catch (InputMismatchException ex) {
                    // nextInt() thất bại không tiêu thụ token.
                    String bad = scanner.next();
                    System.out.println("skipped=" + bad);
                }
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac ScannerMismatchDemo.java
java ScannerMismatchDemo
~~~

Kết quả:

~~~text
number=8
skipped=oops
number=9
~~~

Nếu bỏ dòng **scanner.next()** trong **catch**, vòng lặp sẽ bắt gặp “oops” mãi; **scanner.hasNext()** vẫn là true và **scanner.nextInt()** tiếp tục ném cùng exception. Do vậy khi có thể kiểm tra trước bằng **hasNextInt()**, cách ở mục 4 thường rõ ràng hơn và tránh dùng exception cho luồng xử lý dữ liệu sai vốn được dự kiến trước.

**Không catch Exception quá rộng chỉ để bỏ qua mọi lỗi**. Khi chương trình đang yêu cầu một cấu trúc dữ liệu nghiêm ngặt, gặp token sai có thể phải báo lỗi và dừng, thay vì im lặng loại bỏ dữ liệu.

## 6. InputMismatchException khác NoSuchElementException và IllegalStateException

Các tình huống thường gặp:

| Trạng thái Scanner | Thao tác | Kết quả |
| --- | --- | --- |
| Token hiện tại là **abc** | **hasNext()** | true |
| Token hiện tại là **abc** | **hasNextInt()** | false |
| Token hiện tại là **abc** | **nextInt()** | **InputMismatchException**, token chưa bị tiêu thụ |
| Đã hết token | **hasNext() / hasNextInt()** | false |
| Đã hết token | **next() / nextInt()** | **NoSuchElementException** |
| Scanner đã **close()** | **hasNext() / nextInt()** | **IllegalStateException** |

**InputMismatchException kế thừa NoSuchElementException**, vì vậy nếu cần bắt hai loại riêng thì **bắt InputMismatchException trước**, rồi mới tới **NoSuchElementException**. Nếu bắt lớp cha trước, catch lớp con phía sau trở thành unreachable và Java từ chối biên dịch.

Ví dụ đúng về thứ tự catch:

~~~java
try {
    int value = scanner.nextInt();
    System.out.println(value);
} catch (java.util.InputMismatchException ex) {
    System.out.println("Sai định dạng int");
} catch (java.util.NoSuchElementException ex) {
    System.out.println("Không còn token");
}
~~~

Đây là **mảnh minh họa**, giả định **scanner** đã được tạo và còn mở. Trong ứng dụng xử lý nhiều token, nếu bắt InputMismatchException để tiếp tục thì vẫn phải **tiêu thụ token lỗi** bằng **next()** hoặc chọn cách kết thúc. Mảnh catch này chỉ minh họa phân biệt ngoại lệ và thứ tự catch.

## 7. Token và dòng khác nhau: hasNext() không phải hasNextLine()

Một lỗi phổ biến là nghĩ **Scanner** luôn xử lý nguyên dòng. Thực ra:

- **hasNext() / next()** làm việc theo **token**, mặc định cách nhau bởi khoảng trắng, kể cả dấu xuống dòng.
- **hasNextLine() / nextLine()** làm việc theo **dòng**; **nextLine()** lấy phần còn lại của dòng hiện tại, *không kèm ký tự kết thúc dòng*, rồi chuyển qua dòng sau.
- Một dòng trống có thể được **nextLine()** trả về là String rỗng. **hasNext()** có thể là false khi nguồn chỉ còn khoảng trắng hoặc dòng trống.
- **nextInt()** đọc token số nhưng thường **để lại dấu xuống dòng**, nên gọi **nextLine()** ngay sau đó có thể lấy String rỗng vì chỉ đọc phần còn lại của cùng dòng.

Ví dụ Java 17+ chạy độc lập, lưu thành **ScannerLineDemo.java**:

~~~java
import java.util.Scanner;

public class ScannerLineDemo {
    public static void main(String[] args) {
        String data = "42\nhello world\n\nZ";

        try (Scanner scanner = new Scanner(data)) {
            int number = scanner.nextInt();
            String rest = scanner.nextLine();
            String line1 = scanner.nextLine();
            String line2 = scanner.nextLine();
            String line3 = scanner.nextLine();

            System.out.println("number=" + number);
            System.out.println("rest=[" + rest + "]");
            System.out.println("line1=[" + line1 + "]");
            System.out.println("line2=[" + line2 + "]");
            System.out.println("line3=[" + line3 + "]");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ScannerLineDemo.java
java ScannerLineDemo
~~~

Kết quả:

~~~text
number=42
rest=[]
line1=[hello world]
line2=[]
line3=[Z]
~~~

Sau **nextInt()**, Scanner đang ở ngay sau token **42** nhưng vẫn trong dòng đầu; **nextLine()** đầu tiên đọc phần dư rỗng rồi tiêu thụ dấu xuống dòng. **nextLine()** tiếp theo mới lấy “hello world”. Dòng trống tiếp theo thành **line2=[]**.

Nếu cấu trúc đầu vào là **mỗi dòng một bản ghi**, có thể dùng **while (scanner.hasNextLine())**, lấy **line = scanner.nextLine()**, sau đó tự xác thực token trong chính dòng đó. Phân tích theo dòng cho phép báo lỗi đúng số dòng và tránh vô ý tiêu thụ sang bản ghi khác. Còn nếu cấu trúc dữ liệu chỉ là **chuỗi token bất kể dòng**, dùng hasNext()/next() sẽ tự nhiên hơn.

## 8. Xử lý token lỗi: bỏ một token hay bỏ cả dòng?

Giả sử đầu vào:

~~~text
10 bad 20
30 40
~~~

**Bỏ đúng một token:** khi “bad” sai, gọi **scanner.next()**; sau đó chương trình vẫn đọc được **20**, **30**, **40**. Hợp khi token độc lập và mọi số hợp lệ đều có giá trị.

**Bỏ phần còn lại của dòng:** khi một bản ghi phải có cấu trúc cố định, gặp “bad” có thể cần gọi **scanner.nextLine()** để bỏ phần dư của dòng hiện tại. Khi ấy **20** cũng bị bỏ cùng dòng đầu; dòng sau vẫn có **30 40**. Không nên dùng chiến lược này nếu muốn giữ số 20.

**Dừng với thông báo:** khi mỗi dòng bắt buộc thỏa hợp đồng dữ liệu (ví dụ có đúng 3 trường số), tốt hơn là báo lỗi rõ dòng và từ chối bản ghi thay vì giả vờ dữ liệu vẫn đầy đủ.

**Yêu cầu nhập lại:** với tương tác console, sau khi người dùng nhập token sai, cần loại token sai hoặc dòng hiện tại rồi mới đọc câu trả lời mới; nếu không, lần lặp sau sẽ kiểm tra lại chính token cũ.

Không có một chính sách phục hồi đúng cho mọi ứng dụng. Quy tắc kỹ thuật chung là: **không để nhánh xử lý lỗi lặp lại vô hạn trên token không được tiêu thụ**.

## 9. Delimiter, locale, radix và lý do một token bị coi là không hợp lệ

### 9.1. Delimiter mặc định và delimiter tùy chỉnh

Với delimiter mặc định, đầu vào **"10 20"** có hai token. Nhưng **"10,20"** là một token vì dấu phẩy không mặc định là dấu phân cách của Scanner. Nếu muốn phân cách theo dấu phẩy, có thể cấu hình biểu thức chính quy:

~~~java
Scanner scanner = new Scanner("10,abc,20")
        .useDelimiter("\\s*,\\s*");
~~~

Mảnh code này giả định **import java.util.Scanner**; khi dùng thực tế hãy đóng Scanner nếu thuộc sở hữu của mình. Delimiter trên khớp dấu phẩy với khoảng trắng tùy chọn ở hai bên, phù hợp với ví dụ đơn giản. Nó **không phải bộ phân tích CSV đầy đủ**: CSV có dấu ngoặc kép và dấu phẩy bên trong trường thì cần parser CSV đúng chuẩn.

Nếu delimiter không đúng với định dạng dữ liệu, **hasNextInt()** có thể trả false chỉ vì token bị ghép sai, dù từng phần riêng lẻ đều là số.

### 9.2. Locale ảnh hưởng cú pháp số

**Scanner.useLocale(Locale.US)** giúp chọn quy tắc dấu phân cách hàng nghìn, dấu thập phân và hình thức số phù hợp locale. Ví dụ **1.5** thường là số thập phân hợp lệ ở Locale.US; tại locale khác, dấu thập phân có thể là **dấu phẩy**. Khi nhập/đọc số thập phân, đặt locale rõ ràng để tránh việc code chạy khác giữa môi trường phát triển và production.

**hasNextInt()** không “cắt lấy phần nguyên” của số thập phân. Token **3.5** không phải **int** trong cấu hình cơ bản; nếu yêu cầu số thực, dùng **hasNextDouble() / nextDouble()** hoặc quy tắc riêng.

### 9.3. Radix ảnh hưởng cách đọc số nguyên

Mặc định số nguyên được hiểu ở hệ **10**, nhưng có thể dùng **scanner.useRadix(16)** hoặc **nextInt(16)** để đọc hệ 16. Ví dụ:

~~~java
try (Scanner scanner = new Scanner("FF")) {
    scanner.useRadix(16);
    System.out.println(scanner.hasNextInt());
    System.out.println(scanner.nextInt());
}
~~~

Mảnh code này cần import **java.util.Scanner**; kết quả lần lượt là **true** và **255**. Nếu không đặt hệ 16, token **FF** không phải số nguyên thập phân. Vì vậy **InputMismatchException** còn có thể biểu thị *sai quy tắc diễn giải hiện hành*, không chỉ biểu thị chữ cái trong token.

Với token số quá lớn so với **int**, có thể lựa chọn **nextLong()** hoặc **nextBigInteger()** nếu hợp đồng thực sự cho phép miền giá trị lớn hơn; không nên đổi loại tùy tiện khi chương trình chỉ chấp nhận int.

## 10. Kiểm tra dữ liệu chặt chẽ so với lọc token tùy ý

Mẫu **hasNext() + hasNextInt() + next()** phù hợp khi muốn **lọc token hợp lệ** từ một chuỗi dữ liệu có thể lẫn ký hiệu sai. Tuy nhiên, nếu ứng dụng đọc giao thức có cấu trúc, cần yêu cầu **đúng thứ tự, đúng số trường, đúng kiểu và không có trường thừa**.

Ví dụ một bản ghi phải gồm chính xác **hai số nguyên** như:

~~~text
12 34
~~~

Không đủ nếu chỉ lặp để tìm hai token int bất kỳ, bởi đầu vào **12 wrong 34** sẽ bị bỏ “wrong” rồi vẫn được chấp nhận. Một cách xử lý chặt hơn là đọc cả dòng, tách đúng các trường mong đợi, xác nhận **mọi token** và từ chối dòng có dữ liệu thừa/không hợp lệ.

Có thể dùng một Scanner thứ hai trên String của mỗi dòng:

~~~java
String line = scanner.nextLine();
try (Scanner row = new Scanner(line)) {
    if (!row.hasNextInt()) {
        throw new IllegalArgumentException("Thiếu hoặc sai số thứ nhất");
    }
    int first = row.nextInt();

    if (!row.hasNextInt()) {
        throw new IllegalArgumentException("Thiếu hoặc sai số thứ hai");
    }
    int second = row.nextInt();

    if (row.hasNext()) {
        throw new IllegalArgumentException("Dữ liệu thừa trong bản ghi");
    }
    System.out.println(first + second);
}
~~~

Đây là **mảnh code minh họa**, giả định **scanner** đang mở để đọc các dòng đầu vào và đã import **java.util.Scanner**. Nếu dùng mỗi dòng một bản ghi, hãy kiểm tra **scanner.hasNextLine()** trước khi gọi **nextLine()** và đưa try/catch quanh từng dòng theo chính sách xử lý lỗi. Mảnh này minh họa việc **không âm thầm bỏ token sai** trong bản ghi bắt buộc đúng định dạng.

## 11. Những trường hợp biên, lỗi nguồn và tài nguyên

### 11.1. EOF và Scanner đọc từ console

Một **Scanner(System.in)** thường lấy dữ liệu do người dùng nhập. Nếu không có token và nguồn console vẫn mở, **hasNext()** có thể **đợi người dùng nhập**; false thường chỉ xuất hiện khi nguồn đạt EOF. Tại console, kết thúc nhập phụ thuộc hệ điều hành/terminal, ví dụ tổ hợp phím báo EOF. Không nên hiểu “chưa gõ gì” là hết dữ liệu ngay.

Khi sở hữu Scanner đọc file/String, dùng **try-with-resources**. Nhưng **đóng Scanner(System.in)** sẽ đóng cả nguồn **System.in** do Scanner đang bao bọc; tránh đóng khi phần còn lại của ứng dụng còn cần dùng standard input.

### 11.2. IOException có thể bị Scanner coi là hết đầu vào

Theo hợp đồng Scanner, khi nguồn **Readable** bên dưới ném **IOException** trong quá trình đọc, Scanner **giả định đã đến cuối input**. Có thể lấy IOException gần nhất qua **scanner.ioException()**. Trong ứng dụng cần phân biệt EOF bình thường với lỗi đọc file/network, hãy kiểm tra ioException khi kết thúc thay vì khẳng định mọi **hasNext() == false** đều là kết thúc thành công.

Ví dụ kiểm tra sau vòng lặp:

~~~java
java.io.IOException readFailure = scanner.ioException();
if (readFailure != null) {
    throw new java.io.UncheckedIOException(readFailure);
}
~~~

Mảnh code này giả định **scanner** còn mở. Nó minh họa cách không bỏ qua lỗi I/O khi chính sách hệ thống cần báo lỗi, dù kiểu dữ liệu đầu vào thường được Scanner tiêu thụ như token.

### 11.3. Scanner và hiệu năng

Scanner tiện lợi vì kết hợp delimiter regex, locale và parse kiểu, nhưng có overhead lớn hơn đọc theo byte/char rồi tự parse trong một số workload cực lớn. Với dữ liệu khối lượng cao và quy tắc đơn giản, **BufferedReader** cùng với xử lý String hoặc parser chuyên dụng có thể phù hợp hơn sau khi đo. Không đổi cách đọc chỉ vì giả định Scanner luôn chậm: hãy đo trên dữ liệu thực.

**Scanner không an toàn khi dùng đồng thời từ nhiều thread nếu thiếu đồng bộ bên ngoài.** Nếu chia sẻ một Scanner giữa các tác vụ, mỗi tác vụ có thể tiêu thụ token của tác vụ khác khiến hasNext/next không còn là một thao tác logic nguyên tử.

## 12. Các lỗi thường gặp

1. **Dùng hasNext() rồi tin rằng nextInt() luôn hợp lệ.** hasNext chỉ kiểm tra token tồn tại. Cần **hasNextInt()** nếu mong int.
2. **Dùng while (hasNextInt()) khi cần bỏ qua token không hợp lệ.** Vòng lặp dừng ở token sai đầu tiên, bỏ lỡ token hợp lệ phía sau; dùng **while (hasNext())** và nhánh xử lý.
3. **Catch InputMismatchException rồi gọi lại nextInt() ngay.** Token sai vẫn ở vị trí cũ; cần **next()** để bỏ nó hoặc đổi chiến lược.
4. **Nhầm sai định dạng với EOF.** nextInt trên token sai ném InputMismatchException; khi hết token, nó ném NoSuchElementException.
5. **Bỏ qua số vượt miền int.** “2147483648” có chữ số đúng định dạng nhưng không fit int. Xác định miền giá trị và chọn kiểu phù hợp.
6. **Trộn nextInt() và nextLine() mà không xét phần còn lại của dòng.** nextLine ngay sau nextInt có thể trả String rỗng; thiết kế rõ cấu trúc dòng/token.
7. **Dùng nextLine() để bỏ lỗi nhưng lại muốn giữ các token còn lại trên cùng dòng.** nextLine bỏ qua phần dư của dòng hiện tại; nếu chỉ muốn bỏ một token, dùng next().
8. **Mặc định dấu phẩy luôn là delimiter.** Scanner mặc định tách theo whitespace; hãy khai báo delimiter hoặc dùng parser định dạng chuyên biệt.
9. **Bỏ qua locale/radix.** Các phương thức số phụ thuộc quy tắc diễn giải; đặt locale và radix phù hợp cho đầu vào ổn định.
10. **Âm thầm bỏ token sai trong giao thức nghiêm ngặt.** Với bản ghi cần tính toàn vẹn, phải báo lỗi hoặc từ chối thay vì nối những token hợp lệ rời rạc.
11. **Bỏ qua IOException nguồn.** Scanner có thể coi I/O lỗi là EOF; kiểm tra **ioException()** khi cần phân biệt.
12. **Đóng Scanner(System.in) khi phần khác còn cần đọc.** Đóng wrapper có thể đóng luôn nguồn bên dưới.

## 13. Tự kiểm tra và đáp án

**Câu 1:** Scanner đang ở token **"abc"**. Kết quả của **hasNext()**, **hasNextInt()**, **nextInt()** lần lượt là gì?  
**Đáp:** **true**, **false**, rồi **InputMismatchException**. Token “abc” vẫn còn sau exception.

**Câu 2:** Vòng lặp **while (scanner.hasNextInt())** trên đầu vào **"1 bad 2"** đọc được những số nào?  
**Đáp:** Chỉ **1**. Nó dừng ở “bad”; muốn tiếp tục, phải xử lý token lỗi bằng **next()** hoặc phương pháp khác.

**Câu 3:** Vì sao **2147483648** không đọc được bằng **nextInt()**?  
**Đáp:** Vượt **Integer.MAX_VALUE (2147483647)**. Có thể dùng kiểu số lớn hơn nếu hợp đồng cho phép, nhưng không được âm thầm bỏ sai lệch phạm vi.

**Câu 4:** Gọi **nextInt()** sau khi nguồn đã hết token gây lỗi gì?  
**Đáp:** **NoSuchElementException**, không phải InputMismatchException.

**Câu 5:** Nếu cần bỏ token sai **nhưng giữ token phía sau trên cùng dòng**, nên gọi **next()** hay **nextLine()**?  
**Đáp:** **next()** chỉ tiêu thụ token sai; **nextLine()** có thể bỏ cả phần còn lại của dòng.

**Câu 6:** Vì sao **nextLine()** ngay sau **nextInt()** thường trả về String rỗng?  
**Đáp:** nextInt chỉ tiêu thụ token số, để lại ký tự xuống dòng; nextLine đọc phần còn lại của dòng hiện tại, có thể không còn ký tự nào.

**Câu 7:** Token **FF** có thể được **hasNextInt()** chấp nhận không?  
**Đáp:** Có, nếu Scanner đang dùng hệ 16 (**useRadix(16)**). Trong hệ thập phân mặc định, nó không phải int hợp lệ.

## 14. Kết luận

**hasNext()** xác định còn token hay không; **hasNextInt()** xác định token kế tiếp có đọc thành **int** được hay không; **nextInt()** tiêu thụ và chuyển đổi token nếu thành công. Khi token không đúng kiểu hoặc vượt phạm vi, **nextInt() ném InputMismatchException và giữ nguyên token sai**. Khi không còn token, **nextInt() ném NoSuchElementException**.

Để **lọc và tiếp tục đọc sau token sai**, dùng **while (hasNext())**, trong vòng lặp chọn **hasNextInt() → nextInt()** hoặc **next() để bỏ token lỗi**. Với dữ liệu có cấu trúc, xác thực đầy đủ từng dòng/bản ghi và báo lỗi theo hợp đồng thay vì lọc bỏ dữ liệu tùy tiện.

### Tài liệu tham khảo chính thức

- [Oracle Java SE 21 — Scanner](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Scanner.html): delimiter, hasNext/next, hasNextInt/nextInt, token lỗi không bị tiêu thụ, ioException, locale, radix, blocking.
- [Oracle Java SE 21 — InputMismatchException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/InputMismatchException.html): lớp ngoại lệ và quan hệ kế thừa.
- [Oracle Java SE 21 — NoSuchElementException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NoSuchElementException.html): ngoại lệ khi không còn phần tử/token.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-009">Scanner.ioException và đóng nguồn dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Scanner.ioException và đóng nguồn dữ liệu trong Java

`java.util.Scanner` giúp đọc dữ liệu văn bản thành **token**, số, chuỗi hoặc dòng. Tuy nhiên, một chương trình đọc dữ liệu thực tế còn phải phân biệt **hết dữ liệu hợp lệ (EOF)** với **lỗi I/O**, và phải biết **ai chịu trách nhiệm đóng nguồn** sau khi đọc xong. Hai điểm này dễ bị bỏ sót vì `Scanner` chủ động xử lý một số `IOException` của nguồn dữ liệu thay cho người gọi.

Bài này tập trung vào `Scanner.ioException()`, `Scanner.close()`, vòng đời nguồn `Readable`/`Closeable` và mẫu mã xử lý lỗi phù hợp cho file, input stream, reader và bàn phím. Ví dụ sử dụng Java 17+.

## 1. Scanner nằm ở đâu trong luồng đọc?

Một pipeline thường có dạng:

```text
Tệp / socket / System.in / nguồn byte
               |
               v
       InputStream / Reader
               |
               | đọc dữ liệu, có thể phát sinh IOException
               v
             Scanner
        tách token, parse số,
        xử lý dấu phân cách
               |
               v
      hasNext(), next(), nextInt(),
      hasNextLine(), nextLine(), ...
```

`Scanner` có thể được tạo trực tiếp từ `Readable` (ví dụ `Reader`), `InputStream`, `File`, `Path`, `String` và các dạng nguồn khác. Cách dùng nào phù hợp tùy nguồn và nhu cầu mã hóa ký tự. Khi nguồn là byte, nên chọn **charset tường minh**, chẳng hạn `StandardCharsets.UTF_8`, để nội dung văn bản được giải mã nhất quán.

Một số tình huống lỗi khác nhau:

| Tình huống | Dấu hiệu thông thường |
| --- | --- |
| Dữ liệu hết tự nhiên | `hasNext()` / `hasNextLine()` trở thành `false` |
| Nguồn `Readable.read(...)` ném `IOException` | Scanner xem như đến cuối đầu vào; nguyên nhân có thể xem bằng `ioException()` |
| Token có mặt nhưng sai kiểu số | `nextInt()` có thể ném `InputMismatchException` |
| Gọi `next()` khi không còn token | `NoSuchElementException` |
| Gọi thao tác tìm/đọc khi Scanner đã đóng | `IllegalStateException` |
| Mở tệp thất bại tại constructor | Constructor có thể ném `IOException` / `FileNotFoundException` trước khi có Scanner để kiểm tra |
| Đóng nguồn `Closeable` thất bại | `Scanner.close()` xử lý lỗi và có thể lưu `IOException` để truy vấn qua `ioException()` |

Không nên gộp các tình huống này thành “Scanner trả false là thành công” hoặc “bất kỳ exception nào cũng là lỗi file”.

## 2. Chính xác Scanner.ioException() làm gì?

Chữ ký:

```java
public IOException ioException()
```

Đây là một phương thức **truy vấn**, không phải lệnh đọc dữ liệu:

- Trả về **`IOException` gần nhất** mà Scanner ghi nhận từ nguồn `Readable` bên dưới.
- Trả về `null` nếu chưa có ngoại lệ I/O thuộc loại đó được ghi nhận.
- Không tự ném lại ngoại lệ. Bạn phải kiểm tra giá trị trả về và quyết định báo lỗi, ghi log, thử lại hay dừng.
- Có thể gọi sau khi đọc xong và cả **sau `close()`** để xem lỗi đóng nguồn.
- Nó **không** đại diện cho mọi loại lỗi parse, sai định dạng dữ liệu hoặc lỗi ở constructor tạo nguồn.

Theo hợp đồng API Java 17, nếu phương thức `read()` trên `Readable` bên dưới ném `IOException`, Scanner **giả định đã đến cuối đầu vào**. Vì vậy, một vòng lặp `while (scanner.hasNext())` có thể dừng bình thường dù bên dưới vừa xảy ra lỗi I/O. Phải hỏi `ioException()` mới phân biệt được trong trường hợp này.

### Ví dụ tư duy: đọc được hai token rồi lỗi

```text
Nguồn thực tế: "10 20" + [IOException khi lấy dữ liệu tiếp]
                            |
                            v
Scanner có thể cung cấp "10", "20" trước, sau đó hasNext() == false.
                                  |
                                  +--- ioException() != null
                                       -> nguồn bị lỗi; không coi dữ liệu là đầy đủ.
```

Số token được trả trước khi lỗi phụ thuộc cơ chế đọc trước (buffering), quy tắc delimiter và thời điểm nguồn ném ngoại lệ; **không** dự đoán rằng sẽ nhận chính xác hai token trong mọi nguồn bằng một sơ đồ. Điều quan trọng là **không kết luận thành công chỉ từ việc vòng lặp token kết thúc**.

`ioException() == null` chỉ có nghĩa Scanner **chưa ghi nhận `IOException` từ nguồn**. Nó không chứng minh dữ liệu hợp lệ về nghiệp vụ, đủ số trường, đúng checksum, đúng charset, hay toàn bộ file mong đợi đã đến.

## 3. Vì sao IOException có thể không xuất hiện trực tiếp ở hasNext()/next()?

`Scanner` phục vụ thao tác phân tích văn bản và token. Nó quản lý đọc từ `Readable` và có lựa chọn thiết kế: **chuyển lỗi đọc thuộc `IOException` thành tín hiệu giống EOF ở tầng token**, đồng thời giữ lại nguyên nhân để truy vấn sau.

Ví dụ cách dùng **chưa đủ an toàn**:

```java
try (Scanner scanner = new Scanner(source)) {
    while (scanner.hasNext()) {
        process(scanner.next());
    }
    // Không kiểm tra scanner.ioException():
    // một read IOException có thể vừa làm vòng lặp kết thúc.
}
```

Đây là mảnh mã: `source` là một nguồn `Readable` hợp lệ và `process` là hàm nghiệp vụ. Nếu xử lý dữ liệu quan trọng, phải kiểm tra lỗi I/O **sau khi đọc**, và nếu cần nhận biết lỗi đóng nguồn thì kiểm tra **sau khi đóng**.

Để không nhầm:

- `hasNext() == false` có thể nghĩa không còn token; cần kiểm tra `ioException()` trước khi tuyên bố một luồng đã được đọc đầy đủ.
- `hasNextInt() == false` có thể nghĩa token kế tiếp không phải số nguyên theo locale/radix hiện tại, hoặc không còn token, hoặc lỗi đọc bên dưới. Cần kiểm tra ngữ cảnh; riêng `ioException()` chỉ trả lời về I/O.
- `nextInt()` có thể ném `InputMismatchException` nếu token sai kiểu. Ngoại lệ này **không** được ánh xạ tự động thành `ioException()`.
- `next()`/`nextLine()` có thể ném `NoSuchElementException` khi không còn nội dung phù hợp. Không nên chỉ bắt ngoại lệ đó rồi mặc định nguồn đã hết bình thường.

## 4. Ví dụ Java 17+ chạy được: đọc lỗi, đóng lỗi và vòng đời

Tệp `ScannerIoLifecycleDemo.java` dưới đây có hai nguồn mô phỏng, tạo lỗi có kiểm soát. Ví dụ không cần tệp trên đĩa nên kết quả dễ tái lập.

```java
import java.io.Closeable;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Scanner;

public class ScannerIoLifecycleDemo {
    private static final class FaultyReadSource
            implements Readable, Closeable {
        int closeCalls;

        @Override
        public int read(CharBuffer target) throws IOException {
            throw new IOException("read failed");
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static final class FaultyCloseSource
            implements Readable, Closeable {
        int closeCalls;

        @Override
        public int read(CharBuffer target) {
            return -1; // EOF tự nhiên
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) {
        // 1. Nguồn không lỗi; Scanner đọc đủ hai số.
        Scanner normal = new Scanner("7 11");
        int sum = 0;
        while (normal.hasNextInt()) {
            sum += normal.nextInt();
        }
        System.out.println("normal sum: " + sum);
        System.out.println("normal ioException: " + normal.ioException());
        normal.close();

        // 2. Nguồn ném IOException ngay lần đọc đầu tiên.
        FaultyReadSource reader = new FaultyReadSource();
        Scanner readScanner = new Scanner(reader);
        System.out.println("read hasNext: " + readScanner.hasNext());
        System.out.println("read ioException: "
                + readScanner.ioException().getMessage());
        readScanner.close();
        System.out.println("read close calls: " + reader.closeCalls);

        // 3. Nguồn hết tự nhiên nhưng đóng nguồn lại ném IOException.
        FaultyCloseSource closer = new FaultyCloseSource();
        Scanner closeScanner = new Scanner(closer);
        System.out.println("close error before close: "
                + closeScanner.ioException());
        closeScanner.close();
        System.out.println("close error after close: "
                + closeScanner.ioException().getMessage());
        System.out.println("close attempts: " + closer.closeCalls);

        try {
            closeScanner.hasNext();
        } catch (IllegalStateException e) {
            System.out.println("closed scanner: "
                    + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac ScannerIoLifecycleDemo.java
java ScannerIoLifecycleDemo
```

Stdout đã kiểm tra bằng JDK:

```text
normal sum: 18
normal ioException: null
read hasNext: false
read ioException: read failed
read close calls: 1
close error before close: null
close error after close: close failed
close attempts: 1
closed scanner: IllegalStateException
```

Những điểm cần rút ra:

1. Nguồn chuỗi thông thường không có `IOException`, nên `normal.ioException()` trả `null`.
2. `readScanner.hasNext()` trở thành `false` dù nguyên nhân là **`IOException("read failed")`**, không phải EOF thật. Lỗi được lưu trong `ioException()`.
3. Khi `readScanner.close()` được gọi, Scanner gọi `close()` của `FaultyReadSource` đúng một lần vì nó thực thi `Closeable`.
4. `FaultyCloseSource.read()` trả `-1` (EOF thật). Trước khi đóng, `ioException()` là `null`; sau khi đóng lỗi, `ioException()` mang `"close failed"`.
5. `Scanner.close()` không khai báo `throws IOException`; lỗi close của nguồn bị xử lý bên trong và có thể phải truy xuất bằng `ioException()`.
6. Sau `close()`, thao tác tìm token như `hasNext()` ném `IllegalStateException`. Lưu tham chiếu đến Scanner chỉ để kiểm tra lỗi sau close, không dùng nó để đọc tiếp.

Đây là **mô phỏng có chủ ý** chứ không phải ví dụ về lỗi hệ điều hành thực. Tình huống thực tế có thể là lỗi ổ đĩa, ngắt kết nối mạng, nguồn đọc được một phần rồi thất bại, hoặc lỗi xảy ra trong lúc đóng tài nguyên.

## 5. Đóng Scanner: đóng cả nguồn khi nguồn là Closeable

`Scanner` hiện thực `Closeable` và `AutoCloseable`; có thể dùng trong `try-with-resources`.

Hợp đồng `Scanner.close()`:

- Khi Scanner còn mở, nó **gọi `close()` trên nguồn đọc nếu nguồn đó hiện thực `java.io.Closeable`**.
- Nếu nguồn chỉ hiện thực `Readable` mà không phải `Closeable`, Scanner không có phương thức `close()` chung để đóng nguồn đó.
- Gọi `Scanner.close()` lần hai không đóng nguồn thêm lần nữa theo hợp đồng.
- Đọc/tìm kiếm token sau close sẽ ném `IllegalStateException`.
- IOException khi đóng nguồn có thể được ghi vào `ioException()`; không nên nghĩ rằng vì `scanner.close()` trả về bình thường thì chắc chắn mọi thao tác đóng I/O thành công.

Sơ đồ quyền sở hữu:

```text
try-with-resources đóng Scanner
             |
             v
         Scanner.close()
             |
             +-- nguồn implements Closeable?
                    |
                YES | NO
                    | 
                    v
              source.close()        (không có thao tác close nguồn chung)
                    |
             nguồn bọc tiếp có thể
             đóng các luồng bên dưới
```

Ví dụ:

```java
InputStream fileBytes = Files.newInputStream(path);
Scanner scanner = new Scanner(fileBytes, StandardCharsets.UTF_8);
scanner.close();
```

Đây là mảnh mã, với `path` là `Path` hợp lệ, cần import `java.io.*`, `java.nio.file.*`, `java.nio.charset.*`, `java.util.Scanner` và xử lý `IOException` khi mở tệp. Trong ví dụ, Scanner sử dụng một Reader nội bộ để giải mã từ InputStream; khi đóng Scanner, chuỗi wrapper thường đóng cả Reader và InputStream bên dưới. **Vì vậy, đóng Scanner có thể làm cho InputStream ban đầu không dùng được nữa.**

### Quy tắc sở hữu

- **Tự mở tệp trong phương thức hiện tại:** phương thức thường sở hữu tài nguyên và phải đóng khi xong. `try-with-resources` là lựa chọn tự nhiên.
- **Nhận `InputStream` / `Reader` từ đối tượng khác:** hỏi rõ hợp đồng sở hữu; tránh tự ý đóng Scanner khi thành phần gọi còn cần dùng nguồn.
- **Nguồn dùng chung như `System.in`:** thường không nên tự động đóng chỉ vì hoàn tất một lần hỏi người dùng, vì sẽ làm các phần sau của chương trình không còn stdin.
- **Nguồn chỉ là `String` trong bộ nhớ:** đóng Scanner vẫn là thực hành nhất quán về vòng đời, dù không liên quan đến handle tệp/mạng do chương trình giữ.

Đừng tạo nhiều Scanner trên cùng một `InputStream` hoặc `Reader` nếu không kiểm soát được đọc trước/buffering: một Scanner có thể tiêu thụ trước byte/char làm Scanner kia không nhận được dữ liệu như mong đợi.

## 6. Try-with-resources và kiểm tra ioException sau khi đóng

Mẫu cơ bản đọc file với charset tường minh:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

public class ReadFileLines {
    public static void main(String[] args) throws IOException {
        Path path = Path.of("input.txt");

        // Java 9+: có thể dùng một biến effectively final trong try(...).
        Scanner scanner = new Scanner(path, StandardCharsets.UTF_8);

        try (scanner) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                System.out.println(line);
            }
        }

        // Chạy sau close: phát hiện cả lỗi đọc và lỗi đóng nguồn.
        IOException failure = scanner.ioException();
        if (failure != null) {
            throw new IOException("Failed to read/close " + path, failure);
        }
    }
}
```

Chạy với JDK 17+:

```bash
javac ReadFileLines.java
java ReadFileLines
```

Khi chạy, tệp `input.txt` phải có sẵn tại **thư mục làm việc hiện tại**. Chương trình in từng dòng đọc được. Vì dữ liệu tùy file, không có một stdout cố định để khẳng định trong tài liệu. Nếu mở file thất bại, constructor có thể ném `IOException` **trước khi** khối `try` bắt đầu; khi đó chưa có Scanner để gọi `ioException()`.

Lý do cố ý khai báo `Scanner scanner` bên ngoài `try`: cần giữ **tham chiếu** để kiểm tra `ioException()` sau khi `try-with-resources` đã thực hiện `close()`. Mặc dù Scanner đã đóng, phương thức `ioException()` vẫn cho biết ngoại lệ I/O được lưu; không gọi `hasNextLine()` nữa.

**Giới hạn quan trọng của mẫu này:** nếu phần xử lý **bên trong** vòng lặp ném ngoại lệ độc lập (ví dụ dữ liệu nghiệp vụ sai), câu lệnh sau khối `try` sẽ không chạy. Ứng dụng cần cơ chế báo cáo lỗi riêng, hoặc kiểm tra `ioException()` trong `finally` nếu nhất thiết phải ghi nhận lỗi I/O ngay cả khi quá trình parse thất bại. Tránh cố tình ném lỗi mới trong `finally` làm che lỗi gốc nếu chưa thiết kế quy tắc ưu tiên ngoại lệ.

### Có phải cứ try-with-resources là tự phát hiện IOException khi đóng?

Không. Với `Scanner`, `close()` có kiểu trả về `void` và **không khai báo `throws IOException`**. Nó có thể giữ `IOException` từ nguồn trong `ioException()` thay vì ném ra ngoài. Do đó:

```java
try (Scanner scanner = new Scanner(source)) {
    consume(scanner);
}
// Việc khối try không ném IOException không chứng minh source.close() thành công.
```

Đây là mảnh mã minh họa: `source` là nguồn có thể đóng, `consume` là hàm xử lý. Nếu ứng dụng cần kiểm tra chắc chắn lỗi đóng, phải giữ tham chiếu để hỏi `ioException()` sau close, hoặc quản lý nguồn/Reader trực tiếp theo cách có hợp đồng truyền lỗi đóng phù hợp.

## 7. Phân biệt lỗi đọc, lỗi parse và EOF

| Biểu hiện | Khả năng | Cách xác minh |
| --- | --- | --- |
| `hasNext() == false` | EOF, không còn token phù hợp, hoặc IOException khi đọc | Kiểm tra `ioException()`; hiểu thêm cấu trúc input |
| `hasNextLine() == false` | Hết dòng hoặc lỗi đọc | Kiểm tra `ioException()` |
| `next()` ném `NoSuchElementException` | Không còn token | Kiểm tra trạng thái parser và `ioException()` |
| `nextInt()` ném `InputMismatchException` | Token không hợp lệ với kiểu/radix/locale | Xử lý dữ liệu sai kiểu; `ioException()` có thể vẫn `null` |
| `nextInt()` ném `NoSuchElementException` | Không còn token để lấy | Không coi ngoại lệ này là chính nó chứng minh IOException |
| `hasNextInt() == false` | Token hiện tại không phải số hoặc không có dữ liệu phù hợp | Có thể dùng `hasNext()` để phân biệt token sai kiểu với thiếu token |
| `ioException() != null` | Nguồn Scanner từng báo IOException khi đọc/đóng | Xử lý nguyên nhân I/O theo ngữ cảnh |

Ví dụ khi nhập số:

```java
if (scanner.hasNextInt()) {
    int number = scanner.nextInt();
    // Xử lý number.
} else if (scanner.hasNext()) {
    String invalidToken = scanner.next();
    // Có token, nhưng token này không parse được thành int.
} else {
    IOException failure = scanner.ioException();
    if (failure != null) {
        // Luồng đọc có IOException.
    } else {
        // Hiện không còn token; thường là EOF hợp lệ.
    }
}
```

Đây là mảnh mã: `scanner` đã được tạo trước, và chương trình dùng các import `java.util.Scanner` / `java.io.IOException`. Nếu nguồn có thể block, mỗi lần `hasNext...` có thể chờ dữ liệu. `Scanner` có buffer và mặc định delimiter là khoảng trắng; cần đặt `useDelimiter`/`useLocale`/`useRadix` theo định dạng thật nếu parser phụ thuộc chúng.

## 8. Xử lý System.in: khi nào không nên close?

Tình huống phổ biến:

```java
Scanner input = new Scanner(System.in, StandardCharsets.UTF_8);
System.out.print("Tên: ");
String name = input.nextLine();
System.out.println("Xin chào " + name);
// Không gọi input.close() nếu nhiều phần của ứng dụng vẫn dùng System.in.
```

Đây là mảnh mã, cần `java.util.Scanner` và `java.nio.charset.StandardCharsets`. Việc không đóng trong đoạn minh họa có mục đích: `System.in` là **nguồn dùng chung**, thường do vòng đời chương trình quản lý. Nếu gọi `input.close()`, chuỗi đóng có thể đóng `System.in`; lần nhập tiếp theo ở thành phần khác có thể không hoạt động.

Tuy nhiên, cũng không có quy tắc tuyệt đối rằng “Scanner trên System.in không bao giờ được đóng”. Ở chương trình CLI độc lập, khi ứng dụng thực sự kết thúc và sở hữu đầu vào, đóng tài nguyên có thể hợp lý. Quyết định dựa trên **ownership và vòng đời**, không dựa trên việc có dùng `try-with-resources` được hay không.

Một số lời khuyên thực dụng:

- Chỉ một thành phần làm chủ Scanner của `System.in` và chuyển dữ liệu/hàm nhập cho các thành phần khác.
- Không tạo rồi đóng Scanner riêng cho từng câu hỏi bàn phím khi còn câu hỏi sau.
- Nếu Scanner tồn tại xuyên suốt phiên CLI, hãy kiểm tra `ioException()` khi kết thúc hoặc khi nhập thất bại.
- Tránh bỏ qua các phương thức có thể **block**, như `hasNext()` hoặc `nextLine()`, khi thiết kế timeout và khả năng hủy ở tầng chương trình.

## 9. Tình huống: Scanner được tạo từ Reader có decoder nghiêm ngặt

Một `Scanner` đọc văn bản có thể nhận dữ liệu từ `InputStreamReader`. Nếu ứng dụng yêu cầu byte UTF-8 phải hoàn toàn hợp lệ, có thể truyền một decoder cấu hình `REPORT`:

```java
CharsetDecoder strict = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);

Reader reader = new InputStreamReader(inputStream, strict);
Scanner scanner = new Scanner(reader);
try (scanner) {
    while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        // Xử lý line.
    }
}
IOException readOrCloseFailure = scanner.ioException();
if (readOrCloseFailure != null) {
    throw readOrCloseFailure;
}
```

Đây là **mảnh mã minh họa**, cần các import `java.io.*`, `java.util.Scanner` và `java.nio.charset.*`; `inputStream` là nguồn byte hợp lệ và phương thức phải xử lý `IOException`.

Nếu `InputStreamReader` gặp byte UTF-8 sai định dạng với cấu hình `REPORT`, nó có thể ném `MalformedInputException` (một `IOException`). Scanner xem lỗi đọc đó như hết input và lưu lại để `ioException()` trả về. Vì thế **chỉ dựa vào `hasNextLine() == false` không đủ** để chứng minh văn bản UTF-8 hợp lệ.

Nếu chọn constructor Scanner với charset mà không truyền decoder cấu hình, đừng mặc định rằng chính sách lỗi ký tự giống cấu hình nghiêm ngặt trên; phải tra hợp đồng API tương ứng. Ngoài ra, `Scanner` không kiểm tra tính hợp lệ của CSV/JSON, số trường, chữ ký hay checksum: đó là trách nhiệm lớp parser/validator nghiệp vụ.

## 10. Nếu xảy ra cả lỗi đọc và lỗi đóng thì sao?

`ioException()` chỉ cung cấp **ngoại lệ I/O gần nhất** Scanner đã ghi nhận; nó không phải lịch sử đầy đủ các lần lỗi. Nếu một nguồn ném `IOException` khi đọc rồi tiếp tục ném `IOException` khác lúc đóng, thông tin lỗi được trả về sau cùng có thể là lỗi close. Không dùng `ioException()` như danh sách đầy đủ các lỗi; nếu cần chẩn đoán cả hai, lưu lại kết quả ngay sau vòng đọc và trước khi đóng.

Ví dụ khung xử lý (minh họa ý tưởng, không phải chương trình độc lập):

```java
Scanner scanner = new Scanner(source);
IOException readFailure;

try {
    while (scanner.hasNextLine()) {
        handle(scanner.nextLine());
    }
    readFailure = scanner.ioException(); // Trước close.
} finally {
    scanner.close();                     // Có thể cập nhật lỗi được lưu.
}

IOException lastFailure = scanner.ioException(); // Sau close.
```

Trong mảnh mã này, `source` là một `Readable` đã biết quyền sở hữu, `handle` là hàm nghiệp vụ, và cần import `java.util.Scanner`/`java.io.IOException`. Nếu `handle` ném exception, biến `readFailure` có thể chưa được gán và câu lệnh sau `finally` không chạy; vì vậy **không dùng nguyên khung này làm mã ứng dụng hoàn chỉnh**. Trong triển khai thật, khởi tạo `readFailure = null` và thiết kế `catch/finally` cùng quy tắc ưu tiên lỗi rõ ràng. Bản chất thông điệp: lỗi có thể đến **trước** hoặc **trong** khi close, còn `ioException()` chỉ nhớ **lần gần nhất**.

Nếu muốn ghi log và báo lỗi chi tiết cho một giao thức nghiêm ngặt, có thể bọc nguồn `Reader`/`InputStream` theo dõi exception trực tiếp, hoặc viết logic đọc ở tầng thấp hơn để phân biệt EOF và failure ngay tại nơi chúng phát sinh.

## 11. Khi nào dùng Scanner, khi nào chuyển sang API khác?

`Scanner` rất tiện cho tách token bằng regex, đọc số và dữ liệu nhập tương tác; nó **không phải lựa chọn duy nhất** khi cần luồng dữ liệu lớn hoặc quản lý lỗi I/O chính xác.

| Nhu cầu | Lựa chọn thường phù hợp |
| --- | --- |
| CLI nhỏ, đọc các số/token và câu trả lời từ người dùng | `Scanner`, với vòng đời `System.in` có chủ |
| Đọc vài dòng tệp và parse token | `Scanner` + try-with-resources + `ioException()` |
| Xử lý tệp văn bản rất lớn theo dòng | `BufferedReader` / `Files.newBufferedReader` hoặc `Files.lines` với đóng nguồn |
| Cần bắt `IOException` ngay tại lần đọc | `BufferedReader.readLine()` hoặc `Reader.read(...)` trực tiếp |
| Giao thức nhị phân có độ dài bản ghi | `InputStream`/`DataInputStream`/channel phù hợp |
| Muốn kiểm tra CSV, JSON hoặc định dạng đặc thù | Parser dành cho định dạng đó, quản lý stream theo hợp đồng của parser |

Ví dụ `BufferedReader`:

```java
try (BufferedReader reader =
        Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}
```

Đây là mảnh mã với `path`, `process` đã được định nghĩa và `IOException` được xử lý. Khác biệt: khi `readLine()` gặp lỗi I/O, nó **có thể ném `IOException` trực tiếp**, nhờ đó logic bắt lỗi tại chỗ tự nhiên hơn. Điều này không có nghĩa `BufferedReader` tự parse số và token như Scanner; hai API đáp ứng các nhiệm vụ khác nhau.

## 12. Những lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Chỉ kiểm tra `hasNext() == false` rồi xem file đọc xong | Lỗi I/O bị ngụy trang thành EOF | Kiểm tra `ioException()` |
| Nghĩ `ioException()` sẽ tự ném lỗi | Lỗi bị bỏ qua | Kiểm tra giá trị khác `null`, báo/throw khi cần |
| Cho rằng `InputMismatchException` là IOException | Phân loại sai lỗi dữ liệu | Bắt/kiểm tra lỗi parse riêng |
| Đóng Scanner trên `System.in` rồi còn nhập tiếp | Đóng nguồn dùng chung | Quản lý ownership và tái sử dụng một Scanner |
| Đóng Scanner rồi gọi `nextLine()` | `IllegalStateException` | Không đọc sau close |
| Cho rằng close không lỗi vì `Scanner.close()` không throw | Bỏ qua IOException khi nguồn đóng | Gọi `ioException()` sau close nếu cần biết |
| Chỉ kiểm tra `ioException()` trước close | Không phát hiện lỗi close | Cân nhắc kiểm tra lần nữa sau close |
| Mở nguồn/tệp lỗi nhưng cố gọi `scanner.ioException()` | Scanner chưa tồn tại | Bắt lỗi tại constructor mở nguồn |
| Tạo nhiều Scanner đọc chung một InputStream | Tranh chấp đọc trước, mất/đảo thứ tự token | Một chủ sở hữu hoặc thiết kế phân phối dữ liệu |
| Cho rằng `ioException() == null` nghĩa file hợp lệ | Bỏ qua thiếu trường, sai định dạng, charset | Xác thực dữ liệu độc lập với lỗi I/O |
| Kết thúc vòng lặp số bằng `hasNextInt() == false` mà không xét token sai kiểu | Token không phải số bị bỏ lại | Kiểm tra `hasNext()` và chính sách dữ liệu đầu vào |
| Dùng `Scanner` trong nhiều thread không đồng bộ | Trạng thái parser không an toàn | Mỗi consumer một nguồn, hoặc đồng bộ rõ ràng |

## 13. Tự kiểm tra kiến thức

1. **`hasNext()` trả `false` có đủ khẳng định EOF bình thường?** Không; Scanner có thể đã nhận `IOException` từ nguồn và coi như hết input.
2. **`ioException()` trả cái gì?** `IOException` gần nhất từ nguồn Scanner ghi nhận, hoặc `null` nếu chưa có.
3. **`ioException()` có ném ngoại lệ đã lưu không?** Không; nó chỉ trả về một đối tượng để ứng dụng xử lý.
4. **Sau khi Scanner.close(), còn gọi `ioException()` được không?** Có; đây là cách phát hiện lỗi close được lưu. Nhưng không được tiếp tục quét token.
5. **Đóng Scanner có luôn đóng nguồn không?** Scanner gọi close nếu nguồn bên dưới thực hiện `Closeable`; với nguồn được bọc, close có thể truyền tiếp xuống các stream.
6. **Vì sao try-with-resources chưa đủ nếu cần phát hiện lỗi đóng nguồn?** `Scanner.close()` có thể giữ IOException bên trong; cần kiểm tra `ioException()` sau đó.
7. **`nextInt()` ném `InputMismatchException` có nghĩa tệp lỗi I/O?** Không; thường nghĩa token hiện tại không parse được thành int.
8. **Mở tệp thất bại ở constructor thì kiểm tra `ioException()` bằng cách nào?** Không thể nếu Scanner chưa được tạo; bắt ngoại lệ trực tiếp của thao tác mở.
9. **Vì sao không tự ý đóng Scanner(System.in)?** Vì đóng scanner có thể đóng nguồn console dùng chung cho các phần khác.
10. **Khi nào ưu tiên BufferedReader?** Khi cần đọc dòng và nhận IOException trực tiếp hơn là phân tích token theo quy tắc Scanner.

## Tham khảo Java 17

- [`Scanner` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Scanner.html).
- [`Readable` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Readable.html).
- [`Closeable` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html).
- [`BufferedReader` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/BufferedReader.html).
- [`InputStreamReader` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStreamReader.html).

**Ghi nhớ:** `Scanner` có thể biến một `IOException` của nguồn thành kết thúc quét token, nên **`ioException()` là bước kiểm tra quan trọng trước khi kết luận đọc thành công**. `close()` đóng cả nguồn nếu nguồn là `Closeable`; hãy quản lý quyền sở hữu nguồn và kiểm tra lỗi close khi độ tin cậy dữ liệu đòi hỏi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-010">StreamTokenizer và lựa chọn công cụ phân tích phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# StreamTokenizer và lựa chọn công cụ phân tích phù hợp trong Java I/O

## 1. Mục tiêu và vị trí của `StreamTokenizer`

Khi đọc văn bản, chương trình thường cần đi xa hơn thao tác đọc từng ký tự hoặc từng dòng: phải nhận biết **từ**, **số**, **dấu phân cách** và đôi khi là **chuỗi nằm trong dấu nháy**. Công việc tách văn bản thành các phần tử có ý nghĩa này gọi là *tokenization* (phân tách token). Java cung cấp lớp lâu đời `java.io.StreamTokenizer` để đọc từng token từ luồng ký tự và cho phép tùy chỉnh một số quy tắc nhận diện.

Bài học thuộc chủ đề **Dòng và token** của Java Core I/O. Sau khi học, bạn cần:

- Biết khi nào dùng `StreamTokenizer`, hiểu vòng lặp `nextToken()`, các hằng `TT_WORD`, `TT_NUMBER`, `TT_EOL`, `TT_EOF` và các trường `ttype`, `sval`, `nval`.
- Hiểu cách định nghĩa ký tự cấu thành từ, khoảng trắng, dấu nháy, comment, số, ký tự thường và dấu xuống dòng.
- Viết chương trình Java 17+ xử lý token, phân biệt EOF và EOL, lấy thông tin dòng, quản lý `Reader` và xử lý lỗi.
- Biết giới hạn của tokenizer: **tách token chưa phải phân tích cú pháp (parsing)**, không tự hiểu CSV/JSON, không tự xác thực quy tắc ngữ nghĩa.
- Chọn đúng công cụ giữa `StreamTokenizer`, `Scanner`, `BufferedReader` + xử lý chuỗi/regex, parser CSV/JSON và lexer/parser chuyên dụng.

**Thông điệp chính:** `StreamTokenizer` phù hợp khi đầu vào là một ngôn ngữ văn bản **đơn giản, có quy tắc token rõ và cần đọc lần lượt từ `Reader`**. Với định dạng chuẩn hoặc ngữ pháp phức tạp, hãy chọn parser dành riêng cho định dạng đó.

## 2. Phân biệt ký tự, dòng, token và cấu trúc

Giả sử đầu vào:

```text
total = 12.5
name = "Ada Lovelace"
```

Có nhiều cấp độ xử lý:

1. **Đọc ký tự:** `Reader.read()` cung cấp các đơn vị `char` và thông báo EOF bằng `-1`.
2. **Đọc dòng:** `BufferedReader.readLine()` trả về chuỗi một dòng, không giữ ký tự kết thúc dòng; kết quả là `null` khi hết dòng.
3. **Tách token:** tokenizer nhận biết `total` là word, `=` là dấu riêng, `12.5` là number; ở dòng sau, `"Ada Lovelace"` được nhận diện như một quoted token theo cú pháp được cấu hình.
4. **Parsing:** bộ phân tích hiểu rằng cấu trúc phải là `name = value`, kiểm tra tên khóa hợp lệ, giá trị đúng kiểu, thứ tự hay dấu bắt buộc.
5. **Validation/ngữ nghĩa:** ứng dụng kiểm tra `total` không âm, `name` không rỗng hoặc giá trị nằm trong miền cho phép.

Tokenization **không tự đảm nhận** bước 4 và bước 5. Một dãy token hợp lệ ở mức lexical vẫn có thể là cấu hình hoặc biểu thức sai.

| Mục tiêu | Thao tác/công cụ |
| --- | --- |
| Chỉ cần giữ nguyên từng dòng văn bản | `BufferedReader.readLine()` |
| Đọc chuỗi/từ/số từ input đơn giản | `Scanner` hoặc `StreamTokenizer` |
| Điều khiển phân loại ký tự và các token riêng | `StreamTokenizer` |
| Tách log theo quy tắc rõ, ít phức tạp | Đọc từng dòng + phương thức chuỗi/`Pattern` |
| Đọc CSV theo RFC hoặc yêu cầu có quote, dấu phẩy trong trường, xuống dòng trong trường | Thư viện CSV chuyên dụng |
| Đọc JSON có nested object/array, escape, Unicode | Thư viện JSON có parser chuẩn |
| Ngôn ngữ riêng với grammar, precedence, vị trí token, thông báo lỗi chi tiết | Lexer/parser chuyên dụng hoặc generator như ANTLR |

## 3. Cơ chế của `java.io.StreamTokenizer`

Tạo tokenizer từ một `Reader`:

```java
java.io.StreamTokenizer tokenizer =
        new java.io.StreamTokenizer(reader);
```

`reader` là một `Reader` đã được mở. `nextToken()` đọc tiếp một token và trả về mã loại token (`int`); đồng thời cập nhật trạng thái tokenizer:

| Thành phần | Ý nghĩa | Cách lấy |
| --- | --- | --- |
| `StreamTokenizer.TT_WORD` | Token được phân loại là từ | Khi trả về loại này, lấy nội dung ở `tokenizer.sval` |
| `StreamTokenizer.TT_NUMBER` | Token được phân loại là số | Khi trả về loại này, lấy giá trị kiểu `double` ở `tokenizer.nval` |
| `StreamTokenizer.TT_EOL` | Ký hiệu kết thúc dòng (nếu bật `eolIsSignificant(true)`) | Không coi là nội dung chuỗi |
| `StreamTokenizer.TT_EOF` | Hết luồng đầu vào | Dừng vòng lặp, không đọc tiếp |
| Mã của một ký tự thông thường hoặc ký tự quote | Ví dụ `'='`, `'+'`, `'"'` | Với dấu thông thường: xử lý ký tự; với quoted token: `sval` chứa nội dung bên trong |
| `tokenizer.ttype` | Loại của token cuối cùng | Có thể dùng thay cho mã `int` do `nextToken()` trả về |
| `tokenizer.sval` | Nội dung của token dạng word hoặc quoted string | Chỉ diễn giải theo loại token tương ứng |
| `tokenizer.nval` | Giá trị số dưới dạng `double` | Chỉ diễn giải khi loại là `TT_NUMBER` |
| `tokenizer.lineno()` | Dòng hiện tại theo bộ đếm tokenizer | Dùng bổ sung ngữ cảnh lỗi, không phải cột/khoảng ký tự |

Có thể xử lý token bằng vòng lặp:

```java
int type;
while ((type = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
    if (type == StreamTokenizer.TT_WORD) {
        // Dùng tokenizer.sval
    } else if (type == StreamTokenizer.TT_NUMBER) {
        // Dùng tokenizer.nval
    } else {
        // Xử lý dấu, chuỗi quoted hoặc EOL tùy cấu hình
    }
}
```

Đoạn trên **chỉ minh họa cấu trúc**, chưa phải chương trình độc lập: biến `tokenizer` phải được tạo từ một `Reader` và quản lý vòng đời nguồn đúng cách.

### 3.1. EOF khác EOL

- **EOF** (`TT_EOF`): nguồn không còn token nào; phải kết thúc vòng lặp đọc token.
- **EOL** (`TT_EOL`): ranh giới dòng *khi được bật*; người dùng có thể tiếp tục đọc token ở dòng kế tiếp.
- Theo cấu hình mặc định, kết thúc dòng thường được bỏ qua như whitespace. Gọi `eolIsSignificant(true)` nếu grammar coi dòng là ranh giới bản ghi.
- `lineno()` hữu ích để đưa số dòng vào thông báo lỗi, nhưng tokenizer không tự lưu vị trí cột hoặc vị trí byte của từng token.

Nếu ứng dụng cần phân biệt **dòng trống**, **kết thúc record**, hoặc các trường theo từng dòng, EOL có thể quan trọng. Nếu ngữ pháp cho phép record trải trên nhiều dòng, việc dừng ở EOL có thể làm hỏng quá trình phân tích.

### 3.2. Số được lưu dạng `double`

Khi `TT_NUMBER` xuất hiện, `nval` là `double`. Với số nguyên lớn hơn miền chính xác của binary floating-point, số thập phân cần độ chính xác tài chính hoặc số có định dạng đặc biệt, không được xem đây là parser số “không mất thông tin”.

Ví dụ nguyên 64-bit lớn có thể bị làm tròn khi đi qua `double`; số tiền `0.1` không có biểu diễn nhị phân hữu hạn chính xác. Nếu dữ liệu cần giữ nguyên các chữ số, hãy đọc token **dạng chuỗi** theo grammar phù hợp rồi dùng `Long.parseLong`, `BigInteger` hoặc `BigDecimal` cùng validation; đừng chuyển qua `nval` trước.

## 4. Java 17+: ví dụ đầy đủ nhận dạng word, number, ký hiệu, quoted string và EOL

Ví dụ cố định đầu vào trong `StringReader` để kết quả kiểm thử ổn định. Khi áp dụng thực tế, có thể dùng `Files.newBufferedReader(path, StandardCharsets.UTF_8)` thay `StringReader`. `StringReader` là nguồn ở bộ nhớ, thích hợp cho dữ liệu mẫu ngắn.

Lưu mã vào **`StreamTokenizerDemo.java`**:

```java
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;

public class StreamTokenizerDemo {
    public static void main(String[] args) throws IOException {
        String input = "total = 12.5\nname = \"Ada Lovelace\"\n";

        try (Reader reader = new StringReader(input)) {
            StreamTokenizer tokenizer = new StreamTokenizer(reader);
            tokenizer.eolIsSignificant(true);

            while (true) {
                int type = tokenizer.nextToken();

                if (type == StreamTokenizer.TT_EOF) {
                    System.out.println("EOF");
                    break;
                }
                if (type == StreamTokenizer.TT_EOL) {
                    System.out.println("EOL");
                } else if (type == StreamTokenizer.TT_WORD) {
                    System.out.println("WORD " + tokenizer.sval);
                } else if (type == StreamTokenizer.TT_NUMBER) {
                    System.out.println("NUMBER " + tokenizer.nval);
                } else if (type == '"') {
                    System.out.println("QUOTED " + tokenizer.sval);
                } else {
                    System.out.println("CHAR " + (char) type);
                }
            }
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 StreamTokenizerDemo.java
java StreamTokenizerDemo
```

**stdout của ví dụ:**

```text
WORD total
CHAR =
NUMBER 12.5
EOL
WORD name
CHAR =
QUOTED Ada Lovelace
EOL
EOF
```

**Giải thích:**

- `total` và `name` là `TT_WORD`; nội dung nằm ở `sval`.
- `12.5` là `TT_NUMBER`; `nval` hiển thị `12.5`.
- Dấu `=` là ký tự riêng, nên nhánh cuối in `CHAR =`.
- Dấu `"` báo một quoted token; `sval` chứa `Ada Lovelace`, gồm cả khoảng trắng ở giữa nhưng không gồm dấu quote bao quanh.
- Do đã gọi `eolIsSignificant(true)` và input có ký tự `\n` ở cuối **cả hai dòng**, ta nhận hai token `EOL` trước `EOF`.
- `try-with-resources` đóng `Reader`. Bản thân `StreamTokenizer` không phải tài nguyên cần đóng riêng, nhưng reader nguồn thường cần được đóng, nhất là khi mở file/socket.

Đây là ví dụ về **phân loại token**, chưa hề xác minh rằng dấu `=` phải nằm giữa tên và giá trị. Muốn thực thi grammar, hãy thêm các bước “chờ tên khóa → đòi dấu bằng → đòi giá trị” và báo lỗi nếu gặp loại token không mong đợi.

## 5. Tùy chỉnh cú pháp: sử dụng đúng các phương thức cấu hình

Một ưu thế chính của `StreamTokenizer` là có thể định nghĩa ký tự nào được xem là word, whitespace, comment, quote và ký tự đơn.

| Phương thức | Tác dụng | Điều cần lưu ý |
| --- | --- | --- |
| `wordChars(low, high)` | Đặt dải ký tự thành thành phần của word | Dải cấu hình chỉ là một phần của grammar; không tự hiểu identifier Java đầy đủ |
| `whitespaceChars(low, high)` | Đặt dải ký tự làm whitespace | Dễ vô tình bỏ dấu phân cách nếu cấu hình quá rộng |
| `ordinaryChar(ch)` / `ordinaryChars(low, high)` | Phân loại ký tự thành token riêng | Có thể ghi đè vai trò word/whitespace/comment/quote trước đó |
| `quoteChar(ch)` | Đặt ký tự làm dấu mở/đóng chuỗi quoted | Không đồng nghĩa parser chuỗi JSON hay CSV chuẩn |
| `commentChar(ch)` | Đặt ký tự bắt đầu comment đến hết dòng | Chỉ có ý nghĩa nếu grammar định nghĩa đúng comment |
| `slashSlashComments(true)` | Nhận dạng comment dạng `//` | Cần cân nhắc khi văn bản có URL/đường dẫn chứa `//` |
| `slashStarComments(true)` | Nhận dạng comment dạng `/* ... */` | Không mặc nhiên xử lý mọi trường hợp ngữ pháp comment lồng nhau |
| `parseNumbers()` | Bật cách nhận diện số của tokenizer | Số trả về kiểu `double`; không thay parser số chính xác |
| `eolIsSignificant(true)` | Đưa EOL vào dãy token | Không đồng nghĩa với đọc dòng hoàn chỉnh |
| `lowerCaseMode(true)` | Chuyển token word thành chữ thường | Có thể làm mất phân biệt hoa/thường quan trọng |
| `resetSyntax()` | Đặt lại phân loại ký tự | Phải cấu hình lại word/whitespace/quote/number/comment theo nhu cầu |
| `pushBack()` | Đưa token vừa đọc trở lại để `nextToken()` trả lại một lần | Chỉ hỗ trợ nhìn trước rất hạn chế, không phải bộ đệm token tùy ý |

### 5.1. Đặt identifier có dấu gạch dưới và comment dấu `#`

Mặc định, không nên giả định identifier như `user_name` sẽ được nhận dạng theo đúng grammar mong muốn. Với file cấu hình đơn giản, ta có thể bổ sung ký tự `_` vào word và xem `#` là comment.

Lưu thành **`ConfiguredTokenizerDemo.java`**:

```java
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;

public class ConfiguredTokenizerDemo {
    public static void main(String[] args) throws IOException {
        String source =
                "user_name = \"Ada Lovelace\" # annotation\n"
                + "attempts = 3";

        try (Reader reader = new StringReader(source)) {
            StreamTokenizer tokenizer = new StreamTokenizer(reader);
            tokenizer.wordChars('_', '_');
            tokenizer.commentChar('#');

            int type;
            while ((type = tokenizer.nextToken())
                    != StreamTokenizer.TT_EOF) {
                if (type == StreamTokenizer.TT_WORD) {
                    System.out.println("WORD " + tokenizer.sval);
                } else if (type == StreamTokenizer.TT_NUMBER) {
                    System.out.println("NUMBER " + tokenizer.nval);
                } else if (type == '"') {
                    System.out.println("QUOTED " + tokenizer.sval);
                } else {
                    System.out.println("CHAR " + (char) type);
                }
            }
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConfiguredTokenizerDemo.java
java ConfiguredTokenizerDemo
```

**stdout:**

```text
WORD user_name
CHAR =
QUOTED Ada Lovelace
WORD attempts
CHAR =
NUMBER 3.0
```

**Giải thích:** `wordChars('_', '_')` giúp `user_name` là một token word. `commentChar('#')` bỏ phần sau `#` đến hết dòng. Vì không bật EOL như ví dụ trước, chương trình không in `EOL`. `attempts = 3` vẫn được phân loại thành word, dấu, number. Giá trị `3` thể hiện thành `3.0` vì `nval` có kiểu `double`.

Hai chương trình trên cho thấy cú pháp của `StreamTokenizer` đủ tiện lợi cho một tập token nhỏ. Tuy nhiên, mã mới **chỉ in token**; parser thực tế vẫn phải bắt lỗi trường thiếu, trùng khóa, giá trị sai kiểu, comment không được phép và thứ tự không hợp lệ.

### 5.2. Cẩn trọng với `resetSyntax()` và thay đổi ưu tiên ký tự

Gọi `resetSyntax()` làm mất các quy tắc có sẵn; nếu tiếp đó chỉ gọi `wordChars('a', 'z')` mà quên whitespace, số và quote, tokenizer sẽ không còn xử lý giống mặc định. Hãy lập một bảng grammar mong muốn trước khi viết các lệnh cấu hình và dùng test cho:

- Ký tự đầu/cuối của word.
- Dấu âm, dấu chấm trong số và độ chính xác.
- Dấu bằng/dấu phẩy/dấu chấm phẩy hoặc ngoặc.
- Dấu nháy đơn và nháy kép; chuỗi chưa đóng; ký tự escape.
- Whitespace, CRLF và LF.
- Comment trong và ngoài quoted text.
- Ký tự Unicode ngoài ASCII.

Không thể chỉ gọi một phương thức rồi cho rằng tokenizer trở thành lexer của Java, CSV hay JSON đầy đủ. Đặc biệt, việc nhận diện dấu trong một định dạng chuẩn có thể cần xử lý escape và trạng thái mà cách cấu hình ký tự đơn giản không mô tả được.

## 6. Khi nào `StreamTokenizer` phù hợp?

Dùng `StreamTokenizer` khi:

1. Đầu vào là luồng ký tự có thể đọc dần và ngữ pháp lexical nhỏ, ví dụ các lệnh nội bộ dạng `name = value`, danh sách tên và số, tập câu lệnh học tập.
2. Muốn tùy chỉnh word/number/quote/comment/EOL ở mức ký tự mà không tự viết scanner từ đầu.
3. Cần token tuần tự và grammar phía sau rất đơn giản; có thể viết state machine nhỏ để kiểm tra thứ tự token.
4. Chấp nhận giới hạn của `double` cho số hoặc có chiến lược riêng để parse những số cần độ chính xác.
5. Không cần vị trí token chính xác theo cột, vị trí byte, cây cú pháp phức tạp hay parser lỗi chuyên sâu.

Ví dụ bài toán phù hợp: một mini language dùng để luyện phân biệt token `name`, `=`, `number` và `;`, với tập từ khóa và cú pháp được định nghĩa rõ. Điều đó khác với việc nhận toàn bộ file SQL, JSON hoặc mã nguồn Java rồi mong `StreamTokenizer` xử lý grammar hoàn chỉnh.

## 7. So sánh với các công cụ phân tích khác

### 7.1. `Scanner`: đọc dữ liệu theo delimiter và pattern

`java.util.Scanner` thường thuận tiện cho nhập liệu dạng token phân cách bởi whitespace, phân tích số theo locale và xử lý regex delimiter:

```java
// Minh họa: scanner chỉ xử lý chuỗi ví dụ, không phải parser CSV.
try (java.util.Scanner scanner =
        new java.util.Scanner("red blue 42")) {
    while (scanner.hasNext()) {
        System.out.println(scanner.next());
    }
}
```

Với ví dụ trên, stdout là ba dòng `red`, `blue` và `42`. `Scanner` có `hasNextInt()`, `nextInt()`, `useDelimiter(...)` và cơ chế locale; khi parse số, cần lưu ý định dạng và `InputMismatchException`. Khi đọc từ file, phải đóng scanner (vì nó quản lý nguồn được bọc).

**Ưu:** API dễ đọc cho input nhỏ hoặc dạng whitespace/regex delimiter. **Nhược:** regex/locale/khối lượng lời gọi có thể gây chi phí đáng kể so với xử lý đơn giản theo khối; `Scanner` cũng không tự trở thành parser CSV/JSON hay lexer của ngôn ngữ phức tạp.

Chọn `StreamTokenizer` nếu quy tắc phân loại ký tự và dấu quote/comment phù hợp thiết kế của nó; chọn `Scanner` nếu input chủ yếu là các trường theo delimiter/pattern và muốn API chuyển kiểu tiện lợi.

### 7.2. `BufferedReader` + `String` / `Pattern`: kiểm soát theo dòng

Với log chỉ có “một record mỗi dòng”, thường dễ đọc bằng `readLine()` rồi áp dụng `indexOf`, `split` hoặc `Pattern` theo grammar cụ thể:

```java
// Minh họa một dòng đơn giản, không phải parser CSV.
String record = "ERROR|42|disk full";
String[] fields = record.split("\\|", -1);
System.out.println(fields[0]); // ERROR
System.out.println(fields[1]); // 42
```

Lưu ý `String.split` nhận **regular expression**, nên ký tự `|` phải escape dưới dạng `"\\|"` trong Java. Tham số `-1` giữ các trường rỗng ở cuối; không có tham số đó, các trường rỗng cuối có thể bị bỏ.

**Ưu:** tiện khi đơn vị nghiệp vụ là một dòng và thông báo lỗi cần số dòng. **Nhược:** dễ sai với quote/escape, multiline record hoặc dòng dài bất thường; `readLine()` đọc cả một dòng thành `String` nên không tự giới hạn độ dài dòng. Với log từ nguồn không tin cậy, cần quota dòng và kiểm tra input.

### 7.3. `StringTokenizer`: lớp cũ để chia chuỗi đơn giản

`java.util.StringTokenizer` tách **một `String` đã có sẵn**, chủ yếu dựa trên delimiter. Nó không đọc trực tiếp từ `Reader` theo token và không hiểu trọn vẹn quoted fields hay định dạng cấu trúc. Với code mới, `String.split`/`Pattern` hoặc parser đúng định dạng thường dễ kiểm soát hơn; không nên dùng `StringTokenizer` như giải pháp parser tổng quát.

### 7.4. CSV, JSON và định dạng chuẩn: parser chuyên dụng

Ví dụ CSV:

```text
id,name,description
1,"Ada, A.","first, second"
```

Nếu chỉ tách theo dấu phẩy, dữ liệu `"Ada, A."` bị chia sai. CSV còn có dấu nháy kép escaped, dòng mới bên trong trường, header, encoding và các biến thể dialect. Dùng thư viện CSV có hỗ trợ chuẩn/dialect cần thiết (ví dụ Apache Commons CSV hoặc OpenCSV, tùy dependency dự án). Khi parse dữ liệu quan trọng, định nghĩa rõ dialect và cách báo lỗi.

Ví dụ JSON:

```json
{"user":{"name":"Ada","roles":["reader","writer"]},"active":true}
```

JSON cần parser hiểu object, array, escape, kiểu số, boolean, null và cấu trúc lồng nhau. `StreamTokenizer` không tự tạo cây JSON hay xác thực grammar JSON. Dùng thư viện parser JSON phù hợp như Jackson hoặc Gson nếu chúng có sẵn trong dự án; kiểm tra giới hạn kích thước và độ sâu nếu input không đáng tin cậy.

### 7.5. Lexer/parser chuyên dụng

Nếu viết biểu thức có toán tử, ưu tiên, ngoặc lồng nhau, biến, keyword, string literal phức tạp hoặc ngôn ngữ riêng nhiều quy tắc, cần phân biệt rõ:

- **Lexer:** biến ký tự thành token mang loại, text, vị trí.
- **Parser:** kiểm tra grammar và tạo cấu trúc cú pháp như AST.
- **Semantic validation:** kiểm tra kiểu, tên biến, phép toán, miền giá trị.

`StreamTokenizer` chỉ hỗ trợ một phần lexical. Với nhu cầu mạnh hơn, cân nhắc lexer riêng rõ ràng, `Pattern` có ràng buộc tốt hoặc generator như ANTLR, tùy quy mô grammar và yêu cầu bảo trì.

## 8. Bảng quyết định nhanh

| Đầu vào thực tế | Lựa chọn bắt đầu | Vì sao |
| --- | --- | --- |
| Danh sách từ/số đơn giản phân cách whitespace | `Scanner` | Dễ đọc, có `nextInt`/`nextDouble` |
| Mini language với word, number, punctuation, quote/comment cơ bản | `StreamTokenizer` | Có cấu hình loại ký tự, trả token lần lượt từ `Reader` |
| Log “một record một dòng”, delimiter rõ, ít trường | `BufferedReader` + code dòng/`Pattern` | Kiểm soát record và thông báo lỗi dễ |
| File CSV có quote, delimiter trong trường hoặc multiline | Parser CSV chuyên dụng | Đúng cấu trúc và escape của CSV |
| JSON lồng nhau | Parser JSON chuyên dụng | Xử lý grammar, escape và kiểu dữ liệu |
| Dữ liệu cố định theo byte, giao thức nhị phân | `InputStream`/`DataInputStream`/`ByteBuffer` | Dữ liệu không phải văn bản để tách token |
| Ngôn ngữ có expression grammar, source positions, lỗi phục hồi | Lexer/parser chuyên dụng | `StreamTokenizer` không đủ cho grammar |
| Dữ liệu quá lớn nhưng chỉ cần đếm/phân lọc token | Đọc streaming với giới hạn tài nguyên | Tránh giữ toàn bộ tài liệu; đặt quota cho token/dòng |

**Không có công cụ “tốt nhất” tách khỏi yêu cầu.** Chọn theo định dạng thực, mức đúng cần đạt, kích thước input, báo lỗi, hiệu năng, bảo trì và thư viện đã có.

## 9. Tổ chức parser nhỏ dựa trên token

Giả sử grammar của một lệnh là:

```text
assignment := identifier "=" value
identifier := một word hợp lệ
value      := quoted-string | number | word
```

Một parser đúng phải lần lượt:

1. Gọi `nextToken()` và đòi loại `TT_WORD` cho tên khóa; nếu không, báo lỗi cùng `lineno()`.
2. Gọi `nextToken()` và đòi dấu `'='`. Nếu thiếu, không âm thầm nhận token tiếp theo làm giá trị.
3. Gọi `nextToken()` và chỉ chấp nhận những loại token hợp lệ cho value.
4. Kiểm tra bản ghi đã kết thúc: EOL, dấu `;` hoặc EOF, theo đúng grammar đã chọn.
5. Validate tên khóa, giá trị, số lớn/độ chính xác và trường trùng; trả lỗi có ngữ cảnh.
6. Nếu muốn lookahead một token, `pushBack()` có thể đưa token vừa đọc trở lại cho lần đọc kế; đừng dựa vào nó như bộ đệm bất kỳ độ dài.

Nếu grammar đặt ý nghĩa lên ranh giới dòng, bật `eolIsSignificant(true)`, nhưng kiểm tra kỹ trường hợp kết thúc nguồn ngay sau token cuối mà không có newline. Nếu grammar yêu cầu dấu `;`, phải cấu hình và kiểm tra dấu đó tường minh.

### 9.1. Vì sao không chỉ ghép chuỗi token?

Với đầu vào `x == 4`, tokenizer có thể trả lần lượt `x`, `=`, `=`, `4`. Nếu ứng dụng chỉ lấy những word/number và bỏ qua dấu, nó có thể vô tình chấp nhận biểu thức sai thành “x = 4”. Parser có nhiệm vụ kiểm tra thứ tự và số lượng token một cách chính xác.

Với đầu vào `total = 99999999999999999999`, việc lấy `nval` thành `double` rồi ép về `long` có thể mất dữ liệu hoặc làm thay đổi giá trị. Tách lexing và parsing rõ ràng: số cần chính xác nên được phân tích theo chuỗi chữ số gốc bởi bộ parser số phù hợp.

## 10. Quản lý nguồn, Unicode, bảo mật và tài nguyên

### 10.1. Charset và nguồn tệp

Với file văn bản, nên mở bằng charset xác định:

```java
// Minh họa API: path là một Path có sẵn.
try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(
        path, java.nio.charset.StandardCharsets.UTF_8)) {
    java.io.StreamTokenizer tokenizer =
            new java.io.StreamTokenizer(reader);
    // Đọc và xử lý token theo grammar của ứng dụng.
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập vì `path` chưa được khai báo. `try-with-resources` đóng reader ngay cả khi `nextToken()` ném `IOException`.

`StreamTokenizer` làm việc ở tầng `char` của Java, không ở tầng byte. Nếu dữ liệu được giải mã sai charset, token có thể sai ngay từ đầu. Với Unicode, không tự giả định cấu hình dải ký tự tương ứng hoàn toàn với grammar nhận diện mọi identifier Unicode hợp lệ; nếu có yêu cầu cụ thể về Unicode code point/normalization, hãy xác định và kiểm thử grammar riêng.

### 10.2. Giới hạn dữ liệu đầu vào không đáng tin cậy

Đọc streaming không có nghĩa an toàn vô điều kiện. Một token quoted cực dài, một dòng vô hạn, nhiều token liên tục hoặc nguồn block có thể làm ứng dụng tiêu tốn bộ nhớ, CPU, thời gian hoặc kết nối.

Khi phân tích input từ mạng/người dùng, cần chính sách:

- Giới hạn **tổng số byte** đọc (ở tầng byte) và/hoặc số ký tự nếu nghiệp vụ yêu cầu.
- Giới hạn **số token, kích thước token, số dòng, độ sâu cấu trúc** nếu grammar có cấu trúc lồng nhau.
- Đặt **timeout/cancellation** ở tầng nguồn nếu là socket/pipe có thể block.
- Kiểm soát lỗi định dạng: dấu quote không đóng, trường thiếu, number ngoài miền hợp lệ, ký tự ngoài grammar.
- Không đánh giá lệnh sinh ra từ token như code hoặc query nếu chưa validate và escape/parameterize theo bối cảnh.
- Với chuẩn có parser chuyên dụng, ưu tiên các giới hạn tài nguyên mà thư viện hỗ trợ thay vì tự giả định tokenizer an toàn.

`StreamTokenizer` không cung cấp sẵn quota token hay quota ký tự cho mọi trường hợp. Khi cần giới hạn chặt, đặt wrapper đầu vào có đo/giới hạn hoặc dùng parser hỗ trợ giới hạn được kiểm chứng.

### 10.3. Ngoại lệ và xử lý lỗi

`nextToken()` có thể ném `IOException` khi reader gặp vấn đề I/O. Điều đó khác lỗi grammar: ví dụ token không đúng chỗ là lỗi **phân tích cú pháp** mà code ứng dụng cần tự báo. Nên phân biệt ba nhóm:

- **I/O:** không đọc được file, lỗi mạng, hết quyền truy cập.
- **Lexical/syntax:** ký tự không hợp lệ, quote/grammar sai, thiếu dấu.
- **Semantic:** khóa không được phép, giá trị vượt giới hạn, trường trùng.

Không bỏ qua token bất ngờ chỉ để “đọc tiếp được”; điều đó làm sai dữ liệu và che khuất lỗi đầu vào.

## 11. Sai lầm phổ biến

- Nhầm `StreamTokenizer` là parser JSON/CSV hoặc một parser ngôn ngữ lập trình hoàn chỉnh.
- Nhầm `StreamTokenizer.TT_EOF` và `TT_EOL`; quên bật EOL nếu ngữ pháp cần phân biệt các dòng.
- Đọc `sval` khi token không phải word/quoted hoặc đọc `nval` khi token không phải number.
- Tin `nval` là số nguyên hoặc số thập phân chính xác cho mọi giá trị.
- Không kiểm tra dấu `=`, dấu ngoặc hoặc thứ tự token; chỉ thu thập word/number rồi bỏ dấu.
- Quên quản lý vòng đời `Reader`; tokenizer không tự đóng nguồn.
- Gọi `resetSyntax()` rồi quên khai báo lại whitespace/quote/number.
- Bật cú pháp comment mà không xét chuỗi chứa ký tự giống comment.
- Dùng `String.split(",")` để xử lý CSV có quoted comma.
- Dùng regex đơn giản để parse JSON lồng nhau.
- Coi API streaming là an toàn cho input vô hạn: token lớn hoặc thời gian đọc dài vẫn cần giới hạn.
- Không chỉ định charset khi đọc file và không kiểm thử Unicode/CRLF/LF.

## 12. Bài tập và tiêu chí tự đánh giá

1. **Phân loại token:** dự đoán stdout của `StreamTokenizerDemo` nếu bỏ `eolIsSignificant(true)`. Token `EOF` có biến mất không? (Không.)
2. **Quy tắc dấu:** thêm dấu `;` vào input, sửa parser để chấp nhận chính xác một dấu `=` và tùy chọn kết thúc bằng `;`. Kiểm thử `x == 4` bị từ chối.
3. **Tùy chỉnh word:** thêm tên `first_name` vào `ConfiguredTokenizerDemo`. Giải thích vì sao cần `wordChars('_', '_')`.
4. **Số chính xác:** thử một mã định danh 20 chữ số. Vì sao không lưu nó bằng `nval` rồi ép sang `long`? Thiết kế hướng xử lý token nguyên gốc dạng chuỗi.
5. **So sánh công cụ:** với `a,b,"c,d"`, giải thích vì sao `split(",")` sai và chọn parser CSV.
6. **Dữ liệu nhiều dòng:** tạo input hai lệnh, dòng trống, dấu xuống dòng CRLF và EOF không có newline cuối. Xác định cách parser phát hiện kết thúc record.
7. **Lỗi nguồn:** mô phỏng `IOException` từ reader và phân biệt thông điệp lỗi I/O với lỗi “thiếu dấu =”.
8. **Unicode:** kiểm thử tên và giá trị có dấu tiếng Việt, emoji; xác định grammar identifier mong muốn và charset khi đọc file.
9. **Tài nguyên:** nêu nơi đóng `Reader`, nơi đặt quota/timeout và vì sao `StreamTokenizer` không tự làm việc này.

**Kiểm tra kết quả cuối cùng:** bạn cần phân biệt được **tokenization, parsing, validation**, viết được vòng lặp `nextToken()` dừng chính xác ở EOF, hiểu cách lấy `sval`/`nval` đúng loại token, và chọn parser chuyên dụng khi định dạng có quy tắc vượt quá khả năng lexer đơn giản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lines-and-tokens-011">Giới hạn dùng tách dòng hoặc split để đọc định dạng có escape phức tạp</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của tách dòng và `split()` khi đọc định dạng có escape phức tạp

Khi đọc dữ liệu văn bản, hai thao tác thường gặp là **tách thành dòng** (`BufferedReader.readLine()`, `Files.lines()`) và **tách thành token** (`String.split(regex)`, `Scanner`). Cách này rất phù hợp với dữ liệu đơn giản: một dòng là một bản ghi, dấu phân cách không bao giờ xuất hiện trong giá trị, không có trích dẫn (quoting) hay escape.

Với các định dạng có escape phức tạp, hai giả định trên có thể sai:

- Một bản ghi logic có thể **trải qua nhiều dòng vật lý**, chẳng hạn trường CSV được bao trong dấu nháy chứa `\n`.
- Dấu phân cách có thể xuất hiện như **dữ liệu thật**, chẳng hạn dấu phẩy trong `"Hà Nội, Việt Nam"` hoặc `\|` trong định dạng phân cách bằng `|`.
- Dấu nháy và dấu gạch chéo ngược có thể có **quy tắc escape riêng**; ký tự phân cách chỉ có ý nghĩa sau khi xét trạng thái parser.
- Những định dạng lồng nhau như JSON cần hiểu dấu ngoặc, chuỗi và escape; chia theo `,`, `:` hoặc dấu xuống dòng không đủ để hiểu cấu trúc.

**Ý chính:** Tách chuỗi chỉ dựa trên dấu phân cách là thao tác từ vựng đơn giản. Khi dấu phân cách có thể bị escape, nằm trong chuỗi được trích dẫn hoặc trong cấu trúc lồng nhau, cần **parser hiểu quy tắc của định dạng**. Không tồn tại một công thức `split()` chung có thể đọc đúng mọi định dạng phức tạp.

## 1. Dòng vật lý, bản ghi logic và trường dữ liệu khác nhau

Giả sử tệp CSV chứa:

```csv
id,note
1,"Thành phố Hồ Chí Minh, Việt Nam"
2,"Dòng thứ nhất
Dòng thứ hai"
3,"Anh ấy nói ""Xin chào"""
```

Ta cần phân biệt:

| Khái niệm | Ý nghĩa | Trong ví dụ |
| --- | --- | --- |
| Dòng vật lý | Phần văn bản ngăn bởi dấu kết thúc dòng trong file | Bản ghi `id=2` trải qua **hai** dòng vật lý. |
| Bản ghi logic | Một đơn vị dữ liệu hoàn chỉnh theo cú pháp định dạng | CSV ở đây có một bản ghi header và ba bản ghi dữ liệu. |
| Trường (field) | Một giá trị nằm trong bản ghi | `Thành phố Hồ Chí Minh, Việt Nam` là một trường dù chứa dấu phẩy. |
| Dấu phân cách | Ký tự có ý nghĩa cấu trúc trong ngữ cảnh hợp lệ | Dấu phẩy **ngoài** cặp nháy phân cách trường; dấu phẩy **trong** cặp nháy là dữ liệu. |
| Escape | Cú pháp biểu diễn ký tự đặc biệt mà không làm thay đổi cấu trúc | CSV thường dùng `""` để biểu diễn một ký tự `"` trong trường có nháy. |

`BufferedReader.readLine()` trả một **dòng vật lý** và loại bỏ delimiter kết thúc dòng; nó **không biết** dấu xuống dòng có đang nằm trong trường CSV được trích dẫn hay không. Tương tự, `String.split(",")` không biết dấu phẩy đang nằm trong cặp nháy. Dùng cả hai liên tiếp vẫn không tự tạo ra parser CSV.

### Ví dụ 1 — minh họa lỗi bằng chương trình Java 17+

```java
// NaiveSplitDemo.java
public class NaiveSplitDemo {
    public static void main(String[] args) {
        String csv =
                "id,note\n"
                + "1,\"Hello, world\"\n"
                + "2,\"Dòng thứ nhất\nDòng thứ hai\"\n";

        String[] physicalLines = csv.split("\\R");
        String[] wrongFields = "1,\"Hello, world\"".split(",", -1);

        System.out.println("physicalLines=" + physicalLines.length);
        System.out.println("naiveFields=" + wrongFields.length);
        System.out.println("logicalRecordsExpected=3");
        System.out.println("fieldsPerRecordExpected=2");
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 NaiveSplitDemo.java
java NaiveSplitDemo
```

Kết quả:

```text
physicalLines=4
naiveFields=3
logicalRecordsExpected=3
fieldsPerRecordExpected=2
```

Dữ liệu thực có **ba bản ghi logic**, nhưng `split("\\R")` tạo bốn phần vì bản ghi thứ ba chứa newline trong dấu nháy. Dòng thứ hai có **hai trường**, nhưng `split(",")` tạo ba phần do dấu phẩy nằm trong `"Hello, world"`. `split()` vẫn hoạt động đúng theo quy tắc regex của nó; điều **không đúng** là giả định regex dấu phẩy/dấu xuống dòng biểu diễn toàn bộ cú pháp CSV.

## 2. Những giới hạn riêng của `String.split()`

`String.split(String regex)` dùng **biểu thức chính quy**, không phải chuỗi delimiter thuần túy. Kết quả dựa trên vị trí match của regex và tham số `limit`, chứ không suy diễn trích dẫn hoặc escape của định dạng đầu vào.

### 2.1. Dấu phân cách có ký tự đặc biệt trong regex

Các ký tự như `.`, `|`, `*`, `+`, `?`, `[`, `]`, `(`, `)` có ý nghĩa trong regex. Vì vậy:

```java
String[] parts = "a|b|c".split("|");  // Sai mục đích: | là toán tử alternation của regex
```

Đây là đoạn minh họa lỗi về **mục đích tách delimiter**, không phải cú pháp Java không biên dịch được. Để tách theo ký tự `|` theo nghĩa đen, dùng `"\\|"` hoặc `Pattern.quote("|")`.

### 2.2. Mặc định bỏ các trường rỗng ở cuối

`split(regex)` tương đương `split(regex, 0)`: bỏ các chuỗi rỗng **cuối kết quả**. Khi các cột cuối rỗng vẫn có ý nghĩa, phải chỉ định `limit` âm:

```java
// SplitSemanticsDemo.java
import java.util.Arrays;
import java.util.regex.Pattern;

public class SplitSemanticsDemo {
    public static void main(String[] args) {
        String line = "one|two||";

        System.out.println(Arrays.toString(line.split("\\|")));
        System.out.println(Arrays.toString(line.split("\\|", -1)));
        System.out.println(line.split(Pattern.quote("|"), -1).length);
    }
}
```

```bash
javac SplitSemanticsDemo.java
java SplitSemanticsDemo
```

Kết quả:

```text
[one, two]
[one, two, , ]
4
```

`Pattern.quote()` giúp escape delimiter cho **regex**, nhưng không giải quyết escape/trích dẫn **trong dữ liệu**. Ví dụ `one\|two|three` vẫn không thể tách đúng theo quy tắc “`\|` là dấu `|` thuộc nội dung” bằng cách chỉ dùng `split("\\|")`.

### 2.3. Regex có thể phù hợp với định dạng đơn giản, nhưng không tự hiểu grammar

Regex hữu ích khi quy tắc rõ ràng, không có nesting và delimiter chắc chắn không xuất hiện trong trường; ví dụ tách các dấu cách liên tiếp bằng `split("\\s+")`. Regex có thể biểu diễn nhiều điều kiện phức tạp, nhưng một regex tách đơn giản:

- Không tự duy trì trạng thái trường CSV đang ở trong hay ngoài dấu nháy qua nhiều dòng.
- Không tự kiểm tra số dấu escape, cách nhân đôi dấu nháy, record kết thúc ở đâu.
- Không tự phát hiện toàn bộ các trường hợp sai cú pháp như chuỗi quote chưa đóng hoặc escape không hợp lệ.
- Không phân tích đầy đủ cấu trúc JSON/XML/YAML vốn có quy tắc riêng về chuỗi, lồng nhau và biểu diễn ký tự.

Một regex được thiết kế rất kỹ có thể xử lý **một tập con** của một format; cần có quy tắc kiểm tra và test rõ ràng. Không coi một mẫu `split()` vài ký tự là CSV/JSON parser tổng quát.

## 3. Escape không chỉ là “có dấu `\` đứng trước”

Từ “escape” không có một nghĩa duy nhất cho mọi định dạng:

| Định dạng / quy ước | Ví dụ dữ liệu | Quy tắc cần hiểu |
| --- | --- | --- |
| CSV kiểu trích dẫn kép | `"He said ""Hi"""` | Hai dấu `""` ở bên trong trường trích dẫn biểu diễn một dấu `"` thật. |
| Chuỗi kiểu backslash | `a\|b|c` | Giả sử `\|` biểu diễn `|` thuộc nội dung; chỉ `|` không escape mới phân cách. |
| JSON | `{"message":"a,\"b\""}` | Trong văn bản JSON thực, `\"` nằm trong chuỗi; dấu phẩy trong chuỗi không chia thuộc tính. |
| JSON có escape Unicode | `"\u0041"` | Trình phân tích JSON phải xử lý escape theo grammar JSON; không dùng `split()` để tự giải mã. |
| URL-encoded form | `name=An%20Nguyen` | `%20` có nghĩa theo quy tắc percent-encoding; thứ tự split/decode rất quan trọng. |
| Log có dấu nháy hoặc metadata | `"GET /a,b HTTP/1.1" 200` | Tách theo dấu cách sẽ phá vỡ request nằm trong dấu nháy. |

**Ví dụ JSON trong bảng là cú pháp minh họa**, không phải Java string literal. Khi viết những nội dung này trực tiếp trong source Java, phải xét **hai lớp escape**: Java string literal và định dạng dữ liệu mà chuỗi Java chứa. Chẳng hạn Java `"\\n"` có hai ký tự backslash + `n` ở runtime, trong khi Java `"\n"` là ký tự xuống dòng thật. Sự nhầm lẫn hai lớp này dẫn tới các bài test parser sai ngay từ dữ liệu đầu vào.

### 3.1. Số lượng backslash liên tiếp có thể làm thay đổi ý nghĩa

Trong một grammar dùng backslash để escape, cần biết chính xác **backslash nào escape ký tự nào**. Ví dụ nếu quy ước cho phép `\|` là dấu `|` dữ liệu và `\\` là một backslash dữ liệu:

- Dữ liệu `a\|b|c` phải có hai token: `a|b` và `c`.
- Dữ liệu `a\\|b` có một backslash dữ liệu rồi một delimiter thực, nên thành `a\` và `b`.

Quy tắc đơn giản “hễ dấu `|` có backslash đứng ngay trước thì bỏ qua” sẽ sai khi có hai hoặc nhiều backslash liên tiếp. Thao tác `replace("\\|", "|")` **trước khi tách** còn có thể làm mất thông tin delimiter nào vốn đã bị escape. Cách an toàn là parser đọc tuần tự và giữ trạng thái escape, hoặc dùng thư viện hiểu chính xác grammar.

### Ví dụ 2 — parser nhỏ cho định dạng tự định nghĩa có `\|` và `\\`

Đây là **một định dạng minh họa do ta quy định**, không phải CSV: delimiter là `|`; chỉ hai escape hợp lệ là `\|` và `\\`; mọi escape khác phải báo lỗi. Nó cho thấy vì sao cần nhìn từng ký tự trong bối cảnh.

```java
// BackslashDelimitedDemo.java
import java.util.ArrayList;
import java.util.List;

public class BackslashDelimitedDemo {
    static List<String> parse(String input) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                if (c != '|' && c != '\\') {
                    throw new IllegalArgumentException(
                            "Unsupported escape: \\" + c);
                }
                field.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }

        if (escaped) {
            throw new IllegalArgumentException("Trailing backslash");
        }

        fields.add(field.toString());
        return fields;
    }

    public static void main(String[] args) {
        List<String> fields = parse("alpha\\|beta|gamma\\\\delta||");
        System.out.println("count=" + fields.size());
        System.out.println("first=" + fields.get(0));
        System.out.println("second=" + fields.get(1));
        System.out.println("emptyThird=" + fields.get(2).isEmpty());
        System.out.println("emptyFourth=" + fields.get(3).isEmpty());
    }
}
```

```bash
javac BackslashDelimitedDemo.java
java BackslashDelimitedDemo
```

Kết quả:

```text
count=4
first=alpha|beta
second=gamma\delta
emptyThird=true
emptyFourth=true
```

Bộ parser giữ trạng thái `escaped`; khi gặp backslash thì ký tự sau đó được xử lý như dữ liệu theo hai escape được cho phép. Nó cũng giữ nguyên trường rỗng ở cuối và báo lỗi nếu input kết thúc giữa một escape. Đây mới chỉ là **grammar một tầng rất nhỏ**: để hỗ trợ escape Unicode, dấu nháy, nesting, giới hạn kích thước, dòng và cột lỗi..., phải mở rộng parser có chủ đích, không âm thầm chấp nhận cú pháp ngoài hợp đồng.

## 4. CSV: dấu nháy quyết định dấu phẩy và newline có phải delimiter không

Với kiểu CSV dùng dấu `"` cho quoted field và `""` để escape quote:

1. Ngoài quoted field, dấu phẩy phân cách các trường; CR/LF hoặc CRLF kết thúc bản ghi.
2. Bên trong quoted field, dấu phẩy và newline là **dữ liệu của field**.
3. `""` bên trong quoted field biểu diễn một dấu nháy `"` trong giá trị.
4. Dấu nháy mở/đóng hợp lệ phụ thuộc vị trí; một dấu nháy sai chỗ là lỗi cú pháp.
5. Một bản ghi chỉ kết thúc ở newline khi parser **không ở trong quoted field**.

Quy tắc này giải thích vì sao `BufferedReader.readLine()` là công cụ đọc dòng vật lý nhưng **không thể tự quyết định ranh giới bản ghi CSV**. Parser phải giữ trạng thái khi đọc qua newline.

### 4.1. Mô hình trạng thái

Một parser CSV strict có thể sử dụng bốn trạng thái:

| Trạng thái | Ý nghĩa | Chuyển trạng thái điển hình |
| --- | --- | --- |
| `START` | Đang ở đầu một trường | `"` → `QUOTED`; ký tự thường → `PLAIN`; `,` → kết thúc trường rỗng. |
| `PLAIN` | Đang trong trường không có trích dẫn | `,` → kết thúc trường; newline → kết thúc bản ghi; `"` → lỗi. |
| `QUOTED` | Đang ở trong trường có dấu nháy | Delimiter/newline thành dữ liệu; `""` thêm một dấu nháy; dấu nháy đóng → `AFTER_QUOTE`. |
| `AFTER_QUOTE` | Vừa đóng trường trích dẫn | Chỉ chấp nhận dấu phẩy, newline hoặc EOF; ký tự khác → lỗi ở chế độ strict. |

Parser có thể đọc bằng `Reader` theo từng ký tự. `PushbackReader` giúp nhìn thêm một ký tự khi gặp dấu `"` mà không đánh mất ký tự kế tiếp.

### Ví dụ 3 — parser CSV minh họa có quoted newline và `""`

Ví dụ sau hỗ trợ **tập con CSV được xác định rõ**: delimiter `,`, quote `"`, quote escape bằng `""`, newline ngoài quote ngăn bản ghi, CRLF được nhận là một dấu kết thúc bản ghi, newline trong quote được giữ nguyên. Bộ parser **không** tự bỏ khoảng trắng quanh quote và không hỗ trợ những dialect CSV khác; mục tiêu là làm rõ state machine.

```java
// QuotedCsvDemo.java
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class QuotedCsvDemo {
    enum State { START, PLAIN, QUOTED, AFTER_QUOTE }

    static List<List<String>> parse(Reader source) throws IOException {
        PushbackReader input = new PushbackReader(source, 1);
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        State state = State.START;
        boolean recordStarted = false;
        int value;

        while ((value = input.read()) != -1) {
            char c = (char) value;

            if (state == State.QUOTED) {
                if (c == '"') {
                    int next = input.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        state = State.AFTER_QUOTE;
                        if (next != -1) {
                            input.unread(next);
                        }
                    }
                } else {
                    field.append(c);
                }
                continue;
            }

            if (state == State.AFTER_QUOTE
                    && c != ',' && c != '\r' && c != '\n') {
                throw new IOException("Unexpected char after closing quote");
            }

            if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                state = State.START;
                recordStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        input.unread(next);
                    }
                }

                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
                state = State.START;
                recordStarted = false;
            } else if (c == '"' && state == State.START) {
                state = State.QUOTED;
                recordStarted = true;
            } else if (c == '"') {
                throw new IOException("Quote inside unquoted field");
            } else {
                field.append(c);
                state = State.PLAIN;
                recordStarted = true;
            }
        }

        if (state == State.QUOTED) {
            throw new IOException("Unterminated quoted field");
        }

        if (recordStarted || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }

        return records;
    }

    public static void main(String[] args) throws Exception {
        String csv =
                "id,note\n"
                + "1,\"Hello, world\"\n"
                + "2,\"line one\nline two\"\n"
                + "3,\"He said \"\"Hi\"\"\"\n";

        try (Reader source = new StringReader(csv)) {
            List<List<String>> records = parse(source);
            System.out.println("records=" + records.size());

            for (List<String> row : records) {
                System.out.println(row.get(0) + " | "
                        + row.get(1).replace("\n", "\\n"));
            }
        }
    }
}
```

```bash
javac QuotedCsvDemo.java
java QuotedCsvDemo
```

Kết quả:

```text
records=4
id | note
1 | Hello, world
2 | line one\nline two
3 | He said "Hi"
```

Ở trường thứ ba, ký tự `\n` thực nằm **bên trong quoted field**, được giữ trong field và chỉ đổi thành hai ký tự hiển thị `\` + `n` ở lúc `println` để đầu ra dễ quan sát. Kết quả có bốn bản ghi (một header, ba data), dù dữ liệu thô chứa năm dòng vật lý.

Parser minh họa này không gọi `input.close()` trong `parse` vì `Reader` được **caller truyền vào**; caller giữ quyền đóng nguồn bằng `try-with-resources`. `PushbackReader` được dùng trong phạm vi hàm và không được giữ lại; khi caller đóng nguồn thì tài nguyên bên dưới được giải phóng. Đây là cách làm rõ ranh giới sở hữu tài nguyên khi chuyển từ xử lý từng dòng sang parser tuần tự.

**Giới hạn của ví dụ:** Nó giữ nguyên newline nằm trong quote (bao gồm `\r\n` nếu có), không có tùy chọn escape bằng backslash, không chấp nhận ký tự khoảng trắng sau quote đóng, không tự kiểm tra số trường cố định và chưa có chính sách giới hạn kích thước hay báo vị trí dòng/cột. Một parser CSV cho dữ liệu production còn phải quyết định các dialect và yêu cầu này. Nếu định dạng được định nghĩa là CSV thực tế với nguồn dữ liệu khác nhau, thư viện CSV chuyên dụng thường an toàn, dễ bảo trì hơn việc tự viết.

## 5. Không phải CSV: JSON, XML, log và dữ liệu lồng nhau

### JSON

Trong JSON, dấu phẩy ngăn phần tử mảng/thuộc tính object **chỉ khi nằm đúng mức cấu trúc**. Các dấu phẩy bên trong chuỗi hoặc đối tượng/mảng lồng nhau không có cùng ý nghĩa.

Ví dụ JSON:

```json
{
  "name": "Lan, An",
  "tags": ["java", "io"],
  "meta": {"message": "Có dấu phẩy, và escape \""}
}
```

Chuỗi chứa `\"` (một dấu quote được escape theo cú pháp JSON), các mảng/object có nesting, và các giá trị có thể chứa escape Unicode. `text.split(",")`, `text.split(":")` hoặc `replace("\\\"", "\"")` áp dụng mù quáng sẽ phá cấu trúc hoặc thay đổi giá trị. Dùng **JSON parser** (ví dụ Jackson hoặc một API JSON phù hợp với dự án) để parse thành cây hoặc đọc streaming theo token. Không dùng `String.split()` để mô phỏng JSON parser.

Đối với **JSON Lines/NDJSON**, hợp đồng thường yêu cầu *mỗi dòng vật lý chứa đúng một JSON value hoàn chỉnh*; khi đúng hợp đồng này, đọc từng dòng rồi truyền từng dòng cho **JSON parser** là hợp lý. Nhưng đó là ràng buộc của format NDJSON, không phải bằng chứng rằng có thể chia JSON tùy ý theo newline.

### XML/HTML

Dấu `>` có thể xuất hiện trong thuộc tính được trích dẫn; entity như `&amp;` và các cấu trúc CDATA có ngữ nghĩa riêng. `split("<")` / `split(">")` không hiểu DOM, namespace hoặc encoding. Với XML, dùng XML parser (`SAX`, `StAX` hoặc `DOM`) phù hợp mục đích; đặc biệt khi nhận dữ liệu không tin cậy, cấu hình parser để kiểm soát external entities và tài nguyên.

### Log có quote

Một log định dạng kiểu:

```text
127.0.0.1 "GET /search?q=a,b HTTP/1.1" 200
```

Nếu dùng `split(" ")`, request sẽ bị chia thành các token lẻ. Nếu format quy định quoted string và escape, phải parse theo đúng quote grammar, hoặc dùng log parser tương ứng. Không tự suy quy tắc CSV cho log chỉ vì chúng cùng có dấu nháy.

### URL/form encoding

Các ký tự `%2C`, `%26` hoặc `%3D` được mã hóa theo quy tắc percent-encoding, không phải cú pháp backslash/CSV. Thứ tự tách **cặp key/value** và giải mã có thể ảnh hưởng ý nghĩa khi dấu `&` hoặc `=` cũng nằm trong dữ liệu mã hóa. Hãy dùng parser của giao thức URL/form được chuẩn hóa, thay vì `split("&")` và tự decode tùy ý trên đầu vào bất kỳ.

## 6. Nên dùng công cụ nào trong Java?

| Dạng dữ liệu | Cách làm phù hợp | Khi nào `split()` là đủ? |
| --- | --- | --- |
| Mỗi dòng thực sự là một record và delimiter không thể nằm trong field | `BufferedReader.readLine()` rồi `split(Pattern.quote(delimiter), -1)` | Có, nếu quy tắc tách thật sự đơn giản. |
| File CSV có quoted fields, dấu phẩy hoặc newline bên trong field | CSV parser theo dialect; có thể dùng Apache Commons CSV hoặc parser CSV tương đương | Chỉ với một tập con CSV đã kiểm soát chặt, không có quoting/escape. |
| TSV có escape, quote, hoặc multiline | Parser theo đặc tả TSV thực tế; TSV không có một grammar escape duy nhất | Chỉ khi delimiter và newline được bảo đảm không nằm trong dữ liệu. |
| JSON / NDJSON | JSON parser (chẳng hạn Jackson). Với NDJSON, có thể đọc từng dòng rồi parse từng value. | Không dùng split để parse cấu trúc JSON. |
| XML | SAX/StAX/DOM với cấu hình phù hợp | Không dùng split để parse XML. |
| Protocol dạng record có length prefix | Đọc số byte/char theo framing, sau đó parse payload | Không dựa vào dấu xuống dòng làm boundary nếu protocol không quy định như vậy. |
| Định dạng tự tạo với `\` escape | Parser trạng thái nhỏ, có hợp đồng và test | Có thể chỉ với delimiter không escape; khi đã có escape cần trạng thái. |

`Scanner` cũng dựa vào delimiter regex trong các thao tác phân tách. `Scanner.useDelimiter(...)` giúp thay delimiter, nhưng **không biến `Scanner` thành CSV hoặc JSON parser**. `Files.lines()` cung cấp stream các **dòng vật lý** chứ không tự gom lại trường CSV multiline. Nếu cần parse streaming, hãy tổ chức parser đọc dần từ `Reader`/`InputStream` và phát ra bản ghi logic đúng ranh giới.

Không có thư viện CSV bắt buộc nào trong Java SE chuyên giải mọi dialect; nếu chọn dependency bên ngoài, phải thống nhất phiên bản/dialect, charset, dấu nháy, escape, header, khoảng trắng, cách xử lý dòng rỗng và lỗi dữ liệu theo yêu cầu dự án.

## 7. Những khác biệt cú pháp cần xác định trước khi parse

Đối với format có escape, parser phải có **hợp đồng rõ ràng**, thay vì tự phỏng đoán:

1. **Charset:** byte đầu vào là UTF-8 hay encoding khác? BOM có hợp lệ không? Decoder xử lý chuỗi byte lỗi ra sao?
2. **Record boundary:** newline vật lý có luôn kết thúc bản ghi không? Có `\n`, `\r\n`, hoặc `\r` không? Newline trong quoted field được giữ hay chuẩn hóa?
3. **Delimiter:** dấu phẩy, tab, `|`, dấu chấm phẩy hay chuỗi nhiều ký tự? Delimiter có nghĩa khi nào?
4. **Quote:** quote mở/đóng bằng ký tự nào? Có cho quote bên trong unquoted field không? Khoảng trắng trước/sau quote có hợp lệ không?
5. **Escape:** dùng `""`, `\"`, `\\`, `%xx`, hay cơ chế khác? Escape sai cú pháp được báo lỗi hay giữ nguyên?
6. **Trường rỗng:** `a,,` biểu diễn bao nhiêu trường? Record trống có một field rỗng, hay nên bỏ qua? Chuỗi `""` và field không quoted có cùng ý nghĩa không?
7. **Giới hạn:** tối đa bao nhiêu byte, số ký tự, độ sâu lồng nhau, trường và bản ghi? Parser phải chặn input khổng lồ khi đọc từ nguồn không tin cậy.
8. **Schema:** số field mỗi record có cố định không? Header có bắt buộc không? Có kiểu số/ngày tháng cần kiểm tra sau khi tách field không?
9. **Lỗi:** thiếu quote đóng, ký tự escape cuối file, ký tự dư sau quote đóng cần báo ở đâu? Có phục hồi một bản ghi lỗi và đọc tiếp hay phải dừng?
10. **Ownership:** caller hay parser sẽ đóng `Reader`/`InputStream`? Nên ghi rõ để tránh parser đóng luồng dùng chung ngoài dự kiến.

Đừng unescape toàn bộ chuỗi **trước** khi xác định cấu trúc: bước đó có thể biến delimiter vốn là dữ liệu thành delimiter thật. Trình tự đúng tùy định dạng, nhưng thường là **nhận diện cấu trúc theo trạng thái rồi giải mã nội dung field** khi grammar đã rõ.

## 8. Xử lý streaming, lỗi và ranh giới ký tự

Một parser dữ liệu lớn thường cần đọc theo từng phần thay vì `Files.readString()` toàn bộ file:

- **Giữ trạng thái qua các lần đọc:** một dấu nháy mở ở cuối chunk chỉ có thể đóng ở chunk sau; tương tự một escape `\` có thể nằm đúng mép buffer.
- **Không giả định một lần `Reader.read(buffer)` là một record:** `read` trả bao nhiêu `char` còn phụ thuộc bộ đệm; ranh giới chunk không quyết định ranh giới logic.
- **Đếm đơn vị đúng:** `Reader` trả các đơn vị UTF-16; payload protocol đôi khi quy định độ dài bằng byte, nên phải decode/parse đúng thứ tự.
- **Giữ vị trí lỗi:** số dòng, cột, record và field giúp phát hiện input hỏng. Với quoted multiline, số dòng vật lý không trùng số record.
- **Dùng giới hạn tài nguyên:** giới hạn độ dài quoted field, số phần tử/nesting và timeout cho nguồn mạng theo nhu cầu. Một field chưa đóng quote có thể khiến parser giữ dữ liệu rất lâu.
- **Quản lý nguồn:** đóng reader trong `try-with-resources` ở tầng sở hữu, xử lý `IOException` đúng chỗ. Nếu phương thức parser chỉ được truyền reader mượn, không tự đóng nguồn trừ khi hợp đồng nói vậy.

### Kiểm thử để tránh parser có vẻ chạy đúng nhưng vẫn sai

Với CSV, tối thiểu kiểm thử các chuỗi: `a,b`, `a,,b`, `a,b,`, `"a,b",c`, `"a\nb",c`, `"He said ""Hi""",x`, file rỗng, dấu `\r\n`, dấu nháy chưa đóng và ký tự dư sau nháy đóng. Với backslash escape, kiểm thử dấu delimiter đã escape, một và hai backslash, escape cuối input, escape không hợp lệ và hai delimiter liên tiếp. Với JSON, kiểm thử dấu phẩy/dấu hai chấm trong string, escaped quote, mảng/object lồng nhau và input JSON thiếu dấu đóng.

Không chỉ so khớp **số lượng token**; cần so khớp chính xác **giá trị sau giải escape**, phân biệt field rỗng với record rỗng, và bảo đảm dữ liệu hỏng tạo lỗi thay vì bị âm thầm diễn giải sai.

## 9. Các lỗi phổ biến và hậu quả

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Đọc CSV bằng `readLine()` rồi `split(",")` | Phá record multiline và field chứa dấu phẩy | Dùng CSV parser biết quote và record boundary. |
| Tưởng `split()` nhận delimiter literal | `|` hoặc `.` bị hiểu theo regex | Dùng `Pattern.quote(delimiter)` khi grammar đơn giản. |
| Không truyền `limit=-1` khi trường cuối có thể rỗng | Mất field rỗng ở cuối | Dùng `split(regex, -1)` cho dữ liệu phù hợp với split. |
| Unescape trước khi tách | Delimiter thuộc dữ liệu bị biến thành delimiter thật | Parse cấu trúc trước, decode field theo grammar. |
| Chỉ nhìn một ký tự trước delimiter | Sai với nhiều backslash liên tiếp | Theo dõi trạng thái escape và số lượng ký tự. |
| Tự dùng regex cho JSON/XML tổng quát | Sai với quote, nesting, entity và escape | Dùng parser chuyên dụng. |
| Xem mỗi chunk từ `Reader` là một dòng/record | Cắt đôi quote, escape hoặc Unicode | Duy trì trạng thái qua các lần đọc. |
| Im lặng chấp nhận dấu nháy không đóng | Dữ liệu hỏng được hiểu như dữ liệu hợp lệ | Báo lỗi cú pháp với vị trí nếu có thể. |
| Không giới hạn field khi đọc nguồn ngoài | Có thể tiêu thụ rất nhiều RAM/CPU | Đặt giới hạn dung lượng, độ sâu, số record và chính sách lỗi. |

## 10. Tự kiểm tra

**Câu 1.** Vì sao `readLine()` có thể sai khi lấy từng bản ghi CSV?

**Đáp án:** Một trường CSV quoted có thể chứa newline; một bản ghi logic có thể gồm nhiều dòng vật lý.

**Câu 2.** `"a,b",c` được `split(",")` thành bao nhiêu phần, còn CSV parser đúng thành bao nhiêu trường?

**Đáp án:** `split(",")` tạo ba phần do dấu phẩy nằm trong quote; CSV parser đúng tạo hai trường: `a,b` và `c`.

**Câu 3.** `"x|y||".split("\\|")` có giữ hai trường rỗng cuối không?

**Đáp án:** Không; dùng `split("\\|", -1)` khi hai trường rỗng cuối phải được giữ.

**Câu 4.** `Pattern.quote("|")` có làm `String.split()` hiểu được `a\|b|c` theo quy tắc escape backslash không?

**Đáp án:** Không. Nó chỉ xử lý cách biểu diễn dấu `|` **trong regex**, không thêm grammar escape cho nội dung đầu vào.

**Câu 5.** Vì sao gặp `\|` và `\\|` cần theo dõi trạng thái, không chỉ nhìn ký tự ngay trước `|`?

**Đáp án:** Trong quy ước ví dụ, `\|` biểu diễn delimiter thuộc dữ liệu; `\\|` biểu diễn một backslash thuộc dữ liệu rồi đến delimiter thật.

**Câu 6.** Muốn đọc JSON có object lồng nhau và escaped quote, dùng phương án nào?

**Đáp án:** Dùng JSON parser hiểu cấu trúc và escape. Nếu đầu vào là NDJSON hợp lệ, có thể lấy từng dòng vật lý rồi parse **từng JSON value**.

**Câu 7.** Nếu đang đọc bằng `Reader` mà quote mở nằm ở cuối buffer, parser có được kết thúc record không?

**Đáp án:** Không; phải giữ trạng thái quote sang lần đọc kế tiếp và chỉ kết thúc record khi grammar cho phép.

**Nguyên tắc cần nhớ:** `readLine()` chia theo newline vật lý, `split()` chia theo regex; **không phương thức nào tự hiểu ranh giới bản ghi, escape, quote hay nesting**. Dùng chúng cho dữ liệu có grammar đơn giản đã kiểm soát; dùng parser có trạng thái hoặc thư viện đúng định dạng khi cấu trúc và escape phức tạp.


</details>

- [Quay lại đầu trang](#back-to-top)
