<a id="back-to-top"></a>

# Dòng và Stream

## Menu
- [lines và các kiểu kết thúc dòng CR, LF, CRLF](#string-lines-streams-001)
- [Chuỗi rỗng và ký tự xuống dòng ở cuối](#string-lines-streams-002)
- [Khác biệt giữa lines và split với limit](#string-lines-streams-003)
- [indent thay đổi lề và chuẩn hóa kết thúc dòng](#string-lines-streams-004)
- [chars trả các đơn vị char mở rộng thành int](#string-lines-streams-005)
- [codePoints kết hợp surrogate pair thành code point](#string-lines-streams-006)
- [Lọc, đếm và biến đổi dữ liệu bằng IntStream](#string-lines-streams-007)
- [Xây lại String bằng bộ đệm với appendCodePoint](#string-lines-streams-008)

## <a id="string-lines-streams-001">lines và các kiểu kết thúc dòng CR, LF, CRLF</a>

<details>
<summary>Xem chi tiết</summary>

# String.lines() và các kiểu kết thúc dòng CR, LF, CRLF



## 1. Mục tiêu bài học

Một chuỗi Java có thể chứa nhiều dòng văn bản: nội dung tệp cấu hình, thông điệp HTTP đã tải về, kết quả lệnh hoặc dữ liệu sao chép từ hệ điều hành khác. “Xuống dòng” trong dữ liệu này không nhất thiết là một ký tự duy nhất. Ba dạng thường gặp là **LF**, **CR** và **CRLF**. Khi xử lý nhiều dòng, ta cần biết chuỗi chứa ký tự gì, phương thức nào xem chúng là ranh giới dòng và liệu dòng trống có bị giữ lại không.

Từ **Java 11**, `String.lines()` cho phép duyệt các dòng trong một `String` thông qua `Stream<String>`. Bài này tập trung vào:
- Hiểu chính xác `\n` (LF), `\r` (CR), `\r\n` (CRLF).
- Biết `String.lines()` trả về những chuỗi nào và loại bỏ ký tự kết thúc dòng ra sao.
- Dự đoán các trường hợp **dòng trống giữa văn bản**, **chuỗi rỗng** và **ký tự kết thúc dòng ở cuối**.
- Xử lý dòng bằng Stream, so sánh với `split("\\R")`, và chọn cách xử lý phù hợp cho dữ liệu đa nền tảng.

## 2. CR, LF và CRLF là gì?

| Tên | Tên đầy đủ | Escape trong chuỗi Java | Mã Unicode | Ký tự |
| --- | --- | --- | --- | --- |
| LF | Line Feed | `\n` | `U+000A` | Một ký tự điều khiển xuống dòng |
| CR | Carriage Return | `\r` | `U+000D` | Một ký tự điều khiển đưa đầu in về đầu dòng theo lịch sử |
| CRLF | Carriage Return + Line Feed | `\r\n` | `U+000D U+000A` | **Hai ký tự liên tiếp**, thường được xem như **một lần** kết thúc dòng |

Trong thực tế, văn bản kiểu Unix/Linux thường sử dụng LF, văn bản Windows thường sử dụng CRLF, còn CR đơn từng được dùng phổ biến trên hệ Macintosh cũ. Đây là quy ước hay gặp, không phải quy tắc bất biến: tệp có thể được tạo, đồng bộ hoặc truyền qua hệ thống khác và chứa bất kỳ dạng nào.

Chú ý `"\r\n"` có độ dài **2** trong `String` Java, nhưng nhiều API xử lý văn bản coi cặp này là **một ranh giới dòng**. Không nên hiểu CR và LF trong cặp CRLF như hai dòng độc lập.

```java
String lf = "A\nB";
String cr = "A\rB";
String crlf = "A\r\nB";

System.out.println(lf.length());   // 3
System.out.println(cr.length());   // 3
System.out.println(crlf.length()); // 4
```

Ba chuỗi đều thể hiện hai dòng theo `String.lines()`, nhưng số ký tự bên trong khác nhau. Escape `\n` và `\r` được viết trong **mã nguồn Java**; giá trị runtime chứa ký tự điều khiển thực, không chứa hai ký tự `\` và `n`/`r`.

## 3. String.lines() hoạt động như thế nào?

Chữ ký API:

```java
public Stream<String> lines()
```

Đây là **phương thức instance** của `java.lang.String`, có từ Java 11. Nó trả về một `Stream<String>` chứa các dòng của chuỗi nguồn theo đúng thứ tự xuất hiện. Mỗi phần tử là nội dung dòng, **không bao gồm ký tự kết thúc dòng**.

Trong định nghĩa của `String.lines()`, một *line terminator* là **một trong ba dạng**: LF (`\n`), CR (`\r`) hoặc CRLF (`\r\n`). Cặp CRLF được tiêu thụ cùng nhau như một dấu kết thúc dòng, dù gồm hai ký tự.

Về trực giác, một dòng là:
1. Không hoặc nhiều ký tự nằm trước một line terminator, **hoặc**
2. Không hoặc nhiều ký tự ở cuối chuỗi, sau line terminator cuối cùng nếu còn ký tự.

Tuy nhiên, **ký tự kết thúc dòng ở cuối chuỗi không tự sinh thêm một dòng trống cuối cùng**. Đây là điểm dễ nhầm khi so sánh với `String.split(..., -1)`.

```java
import java.util.List;

String text = "đầu\ncuối";
List<String> lines = text.lines().toList();

System.out.println(lines);       // [đầu, cuối]
System.out.println(lines.size()); // 2
```

`Stream.toList()` có từ Java 16, nên ví dụ này phù hợp với Java 17+. Trên Java 11, có thể thay `.toList()` bằng `.collect(Collectors.toList())`.

## 4. Ví dụ thực hành: trộn CR, LF và CRLF

Tạo tệp `LinesDemo.java` (lưu UTF-8) với mã đầy đủ sau:

```java
import java.util.List;

public class LinesDemo {
    private static void show(String label, String input) {
        List<String> lines = input.lines().toList();
        System.out.println(label + " = " + lines.size() + " dòng");

        for (int i = 0; i < lines.size(); i++) {
            System.out.println("  " + i + ": [" + lines.get(i) + "]");
        }
    }

    public static void main(String[] args) {
        show("LF", "A\nB");
        show("CR", "A\rB");
        show("CRLF", "A\r\nB");
        show("mixed", "một\nhai\r\nba\rbốn");
        show("blank middle", "A\n\nB");
        show("trailing LF", "A\n");
        show("empty", "");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 LinesDemo.java
java LinesDemo
```

Stdout:

```text
LF = 2 dòng
  0: [A]
  1: [B]
CR = 2 dòng
  0: [A]
  1: [B]
CRLF = 2 dòng
  0: [A]
  1: [B]
mixed = 4 dòng
  0: [một]
  1: [hai]
  2: [ba]
  3: [bốn]
blank middle = 3 dòng
  0: [A]
  1: []
  2: [B]
trailing LF = 1 dòng
  0: [A]
empty = 0 dòng
```

Chương trình in `[ ]` xung quanh mỗi dòng để dễ thấy nội dung rỗng; trong kết quả, `[]` chính là một chuỗi dòng có độ dài 0. Thứ tự dòng được giữ nguyên khi đi qua Stream.

Quan sát sự khác biệt:
- `"A\nB"`, `"A\rB"` và `"A\r\nB"` đều tạo hai dòng `"A"` và `"B"`.
- `"A\n\nB"` chứa **hai dấu kết thúc dòng liên tiếp**; khoảng giữa chúng là một dòng rỗng hợp lệ.
- `"A\n"` kết thúc ngay sau một dấu LF; `lines()` chỉ trả `"A"`.
- `""` không có ký tự nào và không có dòng nào; Stream rỗng.

### Ranh giới với các bài chuyên sâu

Bảng dòng trống và ký tự xuống dòng cuối được kiểm chứng chi tiết ở bài **Chuỗi rỗng và ký tự xuống dòng ở cuối**. Nếu cần phân biệt `split(regex, 0)`, `split(regex, -1)` và `lines().limit(n)`, xem bài **Khác biệt giữa lines và split với limit**. Ở bài hiện tại, điểm trọng tâm là cách LF, CR và CRLF được nhận diện như ranh giới dòng và tác động của việc chuẩn hóa chúng.

## 5. Xử lý các dòng bằng Stream

Vì `lines()` trả `Stream<String>`, có thể kết hợp trực tiếp `map`, `filter`, `count`, `forEach`, `limit`, `collect`... để thực hiện các thao tác theo dòng.

### Đếm số dòng

```java
String text = "alpha\nbeta\r\ngamma";
long count = text.lines().count(); // 3
```

Giá trị ở đây là **số phần tử dòng** do `lines()` sinh ra, không phải số lần xuất hiện của ký tự `\n`. Trong chuỗi trên, CRLF là một lần kết thúc dòng; tổng có ba dòng.

### Lọc các dòng trống hoặc chỉ chứa khoảng trắng

```java
import java.util.List;

String text = "  Java  \n\n  \r\nStream\n";
List<String> nonBlank = text.lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .toList();

System.out.println(nonBlank); // [Java, Stream]
```

Trong ví dụ này:
- `strip()` loại khoảng trắng Unicode theo quy tắc `Character.isWhitespace` ở hai đầu chuỗi (Java 11+).
- `filter(...)` bỏ những dòng trở thành rỗng sau khi strip.
- `toList()` gom kết quả thành danh sách (Java 16+).

Nếu cần **giữ lại** các dòng trống để bảo toàn bố cục gốc, đừng áp dụng `filter`. Nếu chỉ cần bỏ dòng trống theo định nghĩa `String.isBlank()` mà vẫn giữ khoảng trắng ở những dòng khác, có thể dùng `.filter(line -> !line.isBlank())`.

### Thêm số thứ tự dòng

```java
import java.util.List;
import java.util.stream.IntStream;

List<String> lines = "first\nsecond\nthird".lines().toList();
List<String> numbered = IntStream.range(0, lines.size())
        .mapToObj(i -> (i + 1) + ". " + lines.get(i))
        .toList();

System.out.println(numbered);
// [1. first, 2. second, 3. third]
```

Đây là đoạn mã minh họa đặt trong một phương thức hoặc `main`; ví dụ hoàn chỉnh chạy độc lập đã có tại mục 4.

### Nối lại các dòng

```java
import java.util.stream.Collectors;

String original = "A\r\nB\nC";
String normalized = original.lines()
        .collect(Collectors.joining("\n"));

System.out.println(normalized.equals("A\nB\nC")); // true
```

Sau khi tách, các dấu CR, LF hoặc CRLF **không còn nằm trong phần tử dòng**. Để nối lại, bạn phải chủ động chọn dấu phân cách mới. Kết quả này không giữ nguyên chính xác các dấu kết thúc dòng gốc. Nếu văn bản ban đầu kết thúc bằng newline, việc nối các dòng thường sẽ **không** tái tạo newline cuối cùng. Nếu cần bảo toàn từng byte của văn bản nguồn, đừng dùng `lines()` rồi tự join mà không ghi lại thông tin dấu kết thúc dòng.

## 6. Tránh nhầm với System.lineSeparator()

`System.lineSeparator()` trả về chuỗi kết thúc dòng **mặc định của nền tảng đang chạy**. Thường trên Windows là `"\r\n"`, còn trên Linux/macOS hiện đại là `"\n"`. Giá trị này phục vụ **việc tạo đầu ra theo nền tảng**, không quyết định loại terminator mà `String.lines()` có thể đọc.

```java
String output = String.join(
        System.lineSeparator(), "first", "second");
```

Đoạn trên tạo văn bản theo dấu xuống dòng hệ thống. Nhưng nếu một `String` đã chứa CRLF hoặc CR từ nguồn khác, `lines()` vẫn nhận diện chúng, bất kể máy đang chạy thuộc hệ điều hành nào.

Tương tự, `%n` trong `String.format(...)`/`Formatter` in ký tự xuống dòng theo nền tảng, và `println(...)` cũng phát sinh dấu xuống dòng của môi trường. Các cách **tạo xuống dòng** này khác với quy tắc **đọc/tách xuống dòng** của `String.lines()`.

## 7. Lines, stream và tài nguyên

`String.lines()` hoạt động trên một **chuỗi đã có trong bộ nhớ**. Nó không tự đọc tệp, không mở kết nối và không đóng luồng hệ điều hành. Stream trả về hỗ trợ thao tác theo chuỗi bước (pipeline), có thứ tự theo văn bản nguồn và việc tìm ranh giới dòng diễn ra khi Stream được sử dụng.

Ví dụ chỉ lấy hai dòng đầu:

```java
import java.util.List;

String text = "one\ntwo\nthree\nfour";
List<String> firstTwo = text.lines().limit(2).toList();

System.out.println(firstTwo); // [one, two]
```

Stream được sử dụng bởi một terminal operation như `toList()` hoặc `count()`; **không được tái sử dụng cùng một đối tượng Stream** sau khi terminal operation đã chạy:

```java
import java.util.stream.Stream;

String text = "A\nB";
Stream<String> stream = text.lines();

long count = stream.count(); // 2
// stream.toList(); // Sai: Stream đã được sử dụng
```

Muốn xử lý lần hai, hãy gọi `text.lines()` lần nữa. `String.lines()` tạo Stream từ một `String`; bản thân `String` là immutable nên không thay đổi vì hoạt động đọc dòng.

**Đối với tệp rất lớn:** đọc toàn bộ tệp vào một `String` có thể tốn bộ nhớ. Khi cần xử lý tăng dần, chọn `Files.lines(path, charset)` hoặc `BufferedReader.lines()` rồi quản lý vòng đời tệp/reader bằng `try-with-resources`. `Files.lines` cần đóng Stream vì nó nắm tài nguyên I/O; `String.lines` không có tài nguyên tệp cần đóng. Những API đọc dòng từ tệp và `String.lines` có cùng mục tiêu khai thác dòng văn bản, nhưng chi phí và vòng đời tài nguyên khác nhau.

## 8. Quy tắc thực hành khi chuẩn hóa xuống dòng

Khi nhận văn bản từ nhiều nguồn, hãy xác định **yêu cầu đầu ra** trước khi chuyển đổi:
1. **Chỉ cần tính toán theo dòng** (đếm, lọc, tìm kiếm): `String.lines()` thường phù hợp.
2. **Cần chuẩn hóa nội dung để hiển thị hoặc so sánh:** dùng `lines()` rồi join với `"\n"` hoặc quy tắc đầu ra đã chọn, chấp nhận việc thay đổi dấu xuống dòng gốc và khả năng mất newline cuối.
3. **Cần giữ nguyên định dạng gốc, kể cả CRLF/CR/LF hỗn hợp hoặc newline cuối:** lưu nguyên chuỗi; nếu phải phân tích, dùng thuật toán giữ kèm delimiter hoặc cơ chế đọc bảo toàn dấu kết thúc dòng.
4. **Cần xử lý nhiều loại Unicode linebreak:** cân nhắc regex `\R` và kiểm tra yêu cầu về các phần tử rỗng ở cuối.
5. **Cần phân tích theo chuẩn cụ thể** (CSV nhiều dòng trong trường có quote, HTTP headers, log có nhiều dòng): không mặc định mỗi dấu xuống dòng là ranh giới record; hãy dùng parser tuân thủ chuẩn.

Một ví dụ điển hình: nếu cần kiểm tra xem cấu hình có bao nhiêu **dòng không rỗng**, có thể viết:

```java
String config = "# sample\r\nhost=localhost\n\r\nport=8080\n";
long nonBlankLines = config.lines()
        .filter(line -> !line.isBlank())
        .count();

System.out.println(nonBlankLines); // 3
```

Đây là bài toán **đếm dòng hiển thị không rỗng**, không phải parse ngữ nghĩa của tệp cấu hình. Nếu cần đọc key/value, bình luận hay escape đúng chuẩn định dạng, phải dùng parser tương ứng.

## 9. Bài tự luyện có đáp án

**Bài 1.** Dự đoán số phần tử của ba Stream:

```java
"A\r\nB".lines().count();
"A\n\nB".lines().count();
"A\n".lines().count();
```

Đáp án lần lượt: **2**, **3**, **1**. CRLF là một dấu kết thúc dòng; hai LF liên tiếp tạo một dòng rỗng ở giữa; LF cuối không thêm dòng mới.

**Bài 2.** `"\r\n\n".lines()` có bao nhiêu dòng và nội dung của từng dòng là gì?

Đáp án: **2 dòng**, cả hai đều là `""`. CRLF tạo dòng rỗng thứ nhất; LF tiếp theo tạo dòng rỗng thứ hai.

**Bài 3.** Với `String text = "x\r\ny\n";`, hãy lấy danh sách dòng không chứa terminator.

```java
import java.util.List;

String text = "x\r\ny\n";
List<String> values = text.lines().toList();
// ["x", "y"]
```

**Bài 4.** Muốn giữ lại cả phần tử trống **sau LF cuối** để phân biệt `"A"` và `"A\n"`, nên dùng cách nào?

Một cách đơn giản khi dữ liệu phù hợp với các linebreak regex nhận diện:

```java
String[] values = "A\n".split("\\R", -1);
// ["A", ""]
```

Với yêu cầu bảo toàn chính xác CR/LF/CRLF gốc để tái xuất, `split("\\R", -1)` vẫn **không giữ loại delimiter**: phải dùng giải pháp lưu cả delimiter.

**Bài 5.** Cần xác định một chuỗi có bao nhiêu dòng không rỗng sau khi loại khoảng trắng đầu/cuối.

```java
String text = "A\n   \r\n B \n";
long count = text.lines()
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .count();
// 2
```

**Bài 6.** So sánh trên chuỗi `"A\u2028B"`: `lines()` và `split("\\R")` có cùng số phần tử không?

Đáp án: **không**. `lines()` tạo một dòng; `split("\\R")` tạo hai phần tử, vì regex `\R` nhận diện Unicode LINE SEPARATOR (`U+2028`).

## 10. Ghi nhớ

- **LF = `\n`**, **CR = `\r`**, **CRLF = `\r\n`**; CRLF gồm hai ký tự nhưng được coi là một line terminator.
- **`String.lines()` từ Java 11** tạo `Stream<String>` theo thứ tự dòng, tự bỏ LF/CR/CRLF khỏi từng phần tử.
- **Chuỗi rỗng → 0 dòng; một terminator đơn → 1 dòng rỗng; terminator ở cuối sau nội dung → không phát sinh thêm dòng cuối**.
- **Dòng trống ở đầu/giữa vẫn được giữ**, trừ khi bạn chủ động lọc bằng Stream.
- **`split("\\R")` nhận nhiều Unicode linebreak hơn; `split("\\R", -1)` giữ phần tử trống sau terminator cuối**, nhưng không bảo toàn chính loại dấu kết thúc dòng.
- **`System.lineSeparator()` là lựa chọn dấu xuống dòng khi tạo văn bản** theo nền tảng, không giới hạn những terminator mà `String.lines()` nhận diện.
- Nếu phải tái tạo văn bản **chính xác**, bao gồm newline cuối và CR/LF/CRLF hỗn hợp, hãy giữ nguyên nguồn hoặc lưu cả delimiter.

Tham khảo chính thức: [Java SE 17 — String.lines()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#lines()), [Java SE 17 — Pattern (\\R)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html), [Java SE 17 — Files.lines()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#lines(java.nio.file.Path)).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-002">Chuỗi rỗng và ký tự xuống dòng ở cuối</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi rỗng và ký tự xuống dòng ở cuối khi dùng String.lines() trong Java



## 1. Vì sao kết quả số dòng dễ gây nhầm lẫn?

Trong Java, `String.lines()` (có từ Java 11) tạo một `Stream<String>` chứa các **dòng văn bản** của chuỗi. Mỗi phần tử là nội dung một dòng **không bao gồm ký tự kết thúc dòng**. Tuy nhiên, **chuỗi rỗng**, **dòng rỗng**, và **chuỗi có ký tự xuống dòng ở cuối** không phải ba trường hợp giống nhau.

Xét ba đầu vào:

```text
""          chuỗi có 0 ký tự
"Java\n"    chữ Java, rồi một ký tự xuống dòng LF
"Java\n\n"  chữ Java, rồi hai ký tự LF liên tiếp
```

Trong đoạn trên, `\n` là cách biểu diễn ký tự xuống dòng trong Java string literal, không phải hai ký tự dấu gạch chéo ngược và chữ `n`.

Kết quả khác nhau:

- `"".lines()` tạo **0 phần tử**: không có dòng nào.
- `"Java\n".lines()` tạo **1 phần tử** `"Java"`: dấu kết thúc dòng cuối cùng không tự tạo thêm dòng rỗng.
- `"Java\n\n".lines()` tạo **2 phần tử**: `"Java"` và `""`; dấu LF thứ hai kết thúc một dòng rỗng sau `Java`.

Điều quan trọng nhất của bài: **`String.lines()` không tự thêm một chuỗi rỗng chỉ vì đầu vào kết thúc bằng dấu xuống dòng.** Nhưng khi có **nhiều dấu kết thúc dòng liên tiếp**, chúng vẫn biểu thị những dòng rỗng có thật.

## 2. Bảng trường hợp biên

Để nhìn rõ, mỗi giá trị dòng bên dưới được đặt trong dấu nháy đôi. Ký hiệu `[]` nghĩa là **không có phần tử**, còn `[""]` nghĩa là **có một phần tử là chuỗi rỗng**.

| Chuỗi đầu vào (Java literal) | Các phần tử từ `lines().toList()` | Số dòng |
| --- | --- | ---: |
| `""` | `[]` | 0 |
| `"Java"` | `["Java"]` | 1 |
| `"Java\n"` | `["Java"]` | 1 |
| `"Java\n\n"` | `["Java", ""]` | 2 |
| `"\n"` | `[""]` | 1 |
| `"\n\n"` | `["", ""]` | 2 |
| `"\r\n"` | `[""]` | 1 |
| `"one\n\ntwo"` | `["one", "", "two"]` | 3 |
| `"one\r\ntwo\r\n"` | `["one", "two"]` | 2 |
| `"first\nsecond"` | `["first", "second"]` | 2 |

Giải thích hai trường hợp đặc biệt:

- `"\n"` là một **dòng rỗng có dấu kết thúc**: stream chứa một phần tử `""`. Còn `""` không chứa dòng nào, nên stream rỗng.
- `"Java\n\n"` gồm dòng `Java` kết thúc bởi dấu LF thứ nhất, sau đó một **dòng rỗng** kết thúc bởi dấu LF thứ hai. Dấu LF thứ hai cũng là ký tự kết thúc cuối cùng nên không phát sinh dòng rỗng thứ ba.

**Số ký tự xuống dòng không luôn bằng số dòng.** Với `"Java\n"` có một LF và một dòng; với `"Java"` không có LF nhưng vẫn có một dòng. Với chuỗi rỗng thì có 0 dòng.

## 3. Chương trình Java 17+ hoàn chỉnh

Lưu đoạn mã sau thành tệp `StringLinesEdgeCases.java`:

```java
import java.util.Arrays;
import java.util.List;

public class StringLinesEdgeCases {
    private static void show(String label, String input) {
        List<String> visible = input.lines()
                .map(line -> "\"" + line + "\"")
                .toList();

        System.out.println(label + " => count=" +
                visible.size() + ", lines=" + visible);
    }

    public static void main(String[] args) {
        show("empty", "");
        show("no terminator", "Java");
        show("one trailing LF", "Java\n");
        show("two trailing LF", "Java\n\n");
        show("only LF", "\n");
        show("two LF", "\n\n");
        show("only CRLF", "\r\n");
        show("mixed blanks", "one\n\ntwo");
        show("CRLF final", "one\r\ntwo\r\n");

        String[] byDefault = "Java\n".split("\\R");
        String[] keepTrailing = "Java\n".split("\\R", -1);
        System.out.println("split default count: " + byDefault.length);
        System.out.println("split keep count: " + keepTrailing.length);
        System.out.println("split keep last empty: " +
                keepTrailing[1].isEmpty());
        System.out.println("empty split count: " +
                "".split("\\R", -1).length);

        String original = "one\r\ntwo\r\n";
        String reconstructed = String.join("\n",
                original.lines().toList());
        System.out.println("reconstructed equals original: " +
                reconstructed.equals(original));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac StringLinesEdgeCases.java
java StringLinesEdgeCases
```

Kết quả chuẩn:

```text
empty => count=0, lines=[]
no terminator => count=1, lines=["Java"]
one trailing LF => count=1, lines=["Java"]
two trailing LF => count=2, lines=["Java", ""]
only LF => count=1, lines=[""]
two LF => count=2, lines=["", ""]
only CRLF => count=1, lines=[""]
mixed blanks => count=3, lines=["one", "", "two"]
CRLF final => count=2, lines=["one", "two"]
split default count: 1
split keep count: 2
split keep last empty: true
empty split count: 1
reconstructed equals original: false
```

Hàm `show` bọc nội dung mỗi dòng trong dấu nháy đôi để ta phân biệt được `[]` với `[""]`. Đây chỉ là cách hiển thị dễ quan sát khi học; bản thân `String.lines()` trả về dòng gốc, không tự thêm dấu nháy.

`toList()` là phương thức `Stream.toList()` có trong Java 16+, vì vậy chương trình dùng Java 17 chạy được. Trước Java 16, có thể thu thập thành danh sách bằng `collect(Collectors.toList())`. Phần quan trọng của bài, `String.lines()`, đã có từ Java 11.

### Từ dòng sang trường tách

Ở đây chỉ cần biết `lines()` không tạo thêm dòng sau newline cuối, trong khi `split("\\R", -1)` có thể giữ trường rỗng phía sau dấu phân cách. `split("\\R")` mặc định bỏ các trường rỗng cuối; với chính chuỗi `""`, `split` vẫn trả một phần tử rỗng, còn `lines()` trả stream rỗng. Cách `limit` dương và tập ngắt dòng Unicode thuộc bài **Khác biệt giữa lines và split với limit**.

## 4. Phân biệt chuỗi rỗng, dòng rỗng và dòng chỉ có khoảng trắng

Ba khái niệm thường được kiểm tra bằng những API khác nhau:

- **Chuỗi đầu vào rỗng:** `input.isEmpty()` trả về `true` khi độ dài chuỗi bằng 0.
- **Dòng rỗng:** với từng `line` từ `input.lines()`, `line.isEmpty()` trả về `true` nếu dòng có 0 ký tự.
- **Dòng trắng:** `line.isBlank()` trả về `true` nếu dòng chỉ gồm khoảng trắng theo định nghĩa của Java, hoặc rỗng.

Ví dụ:

```java
String text = "first\n \n\nlast\n";

long allLines = text.lines().count();
long emptyLines = text.lines()
        .filter(String::isEmpty)
        .count();
long blankLines = text.lines()
        .filter(String::isBlank)
        .count();

System.out.println(allLines);   // 4
System.out.println(emptyLines); // 1
System.out.println(blankLines); // 2
```

Giải thích: các dòng lần lượt là `"first"`, `" "` (một dấu cách), `""` và `"last"`. Dòng `" "` là **blank** nhưng không **empty**. Dấu `\n` cuối cùng không tạo ra dòng rỗng thứ năm.

**Lưu ý:** mỗi lần gọi `text.lines()` ở ví dụ tạo một stream mới. Một `Stream` đã dùng terminal operation như `count()` sẽ bị tiêu thụ; không gọi tiếp `count()` trên cùng đối tượng stream.

## 5. Muốn giữ chính xác ký tự xuống dòng cuối phải làm gì?

`String.lines()` trả lại **nội dung dòng** và loại bỏ dấu kết thúc của từng dòng. Sau khi tách, ta không còn biết từ danh sách dòng rằng đầu vào ban đầu dùng `\n`, `\r`, hay `\r\n`, cũng không luôn biết có ký tự xuống dòng ở cuối hay không.

Ví dụ:

```java
String original = "one\r\ntwo\r\n";
String reconstructed = String.join("\n",
        original.lines().toList());

System.out.println(reconstructed.equals(original)); // false
```

`reconstructed` là `"one\ntwo"`; nó không còn CRLF và không còn terminator sau `two`. Vì vậy, **không dùng `String.join("\n", text.lines().toList())` để khẳng định khôi phục nguyên trạng văn bản**.

Nếu chỉ muốn biết chuỗi gốc có kết thúc bằng một trong ba loại terminator được `String.lines()` nhận biết, kiểm tra trực tiếp chuỗi gốc **trước khi duyệt dòng**:

```java
boolean endsWithLineTerminator =
        text.endsWith("\n") || text.endsWith("\r");
```

Biểu thức đó cũng trả về `true` cho CRLF, vì `"\r\n"` kết thúc bằng `"\n"`. Nếu muốn phân biệt chính xác ba kiểu để ghi lại:

```java
String terminal;
if (text.endsWith("\r\n")) {
    terminal = "\r\n";
} else if (text.endsWith("\n")) {
    terminal = "\n";
} else if (text.endsWith("\r")) {
    terminal = "\r";
} else {
    terminal = "";
}
```

Đây chỉ là phép kiểm tra **terminator cuối**, không lưu được toàn bộ dấu kết thúc khác nhau bên trong chuỗi. Nếu yêu cầu giữ nguyên byte, giữ nguyên định dạng xuống dòng hỗn hợp hoặc tái tạo văn bản không sai một ký tự, hãy giữ nguyên nguồn ban đầu hoặc dùng bộ phân tích có bảo toàn delimiter; không chỉ dựa vào `lines()`.

### 9.1. Trường hợp nhập liệu hoặc xử lý bản ghi

Với tài liệu văn bản thông thường, newline cuối thường chỉ khép lại dòng cuối. Với một giao thức hay định dạng mà **phần sau delimiter cuối** được coi là một bản ghi/trường rỗng, nghiệp vụ có thể cần giữ phần tử rỗng này; khi đó cách chia dùng `split("\\R", -1)` (sau khi xác nhận phạm vi terminator phù hợp) sẽ gần với mô hình “các trường được ngăn bằng delimiter” hơn.

Ví dụ, đầu vào `"A\nB\n"` có:
- Hai dòng theo `String.lines()`: `A` và `B`.
- Ba phần tử theo `split("\\R", -1)`: `A`, `B` và `""`.

Hai kết quả đều có lý do riêng. Hãy quyết định trước dấu kết thúc cuối đại diện cho **kết thúc dòng** hay **dấu phân cách tạo trường rỗng** trong định dạng dữ liệu cụ thể.

## 6. Bài tập tự kiểm tra

Trước khi chạy mã, đoán kết quả các biểu thức:

| Biểu thức | Kết quả |
| --- | ---: |
| `"".lines().count()` | `0` |
| `"\r".lines().count()` | `1` |
| `"\r\n".lines().count()` | `1` |
| `"x\r\n".lines().count()` | `1` |
| `"x\r\n\r\n".lines().count()` | `2` |
| `"x\n\n\ny".lines().count()` | `4` |
| `" x \n".lines().findFirst().orElse("?")` | `" x "` |
| `"x\n".split("\\R", -1).length` | `2` |

Thực hành thêm: viết một hàm nhận chuỗi đầu vào, báo số dòng tất cả, số dòng rỗng, số dòng chỉ có khoảng trắng và một boolean cho biết đầu vào có kết thúc bằng LF hoặc CR hay không. Hãy thử cả `""`, `"\n"`, `"a\n"`, `"a\n\n"`, `"a\r\nb\r\n"`.

## 7. Ghi nhớ

- **`String.lines()` với chuỗi rỗng cho 0 dòng**, còn chuỗi gồm duy nhất một LF cho 1 dòng rỗng.
- Một **terminator duy nhất ở cuối** không sinh thêm phần tử rỗng; **hai terminator liên tiếp** vẫn giữ dòng rỗng nằm giữa chúng.
- `String.lines()` nhận `\n`, `\r`, `\r\n`; regex `\R` nhận tập linebreak rộng hơn.
- **`split("\\R", -1)` giữ phần tử rỗng cuối**, khác với cách chia dòng của `String.lines()`; với chuỗi rỗng, `split` cho một phần tử rỗng.
- `isEmpty()` và `isBlank()` phục vụ hai cách hiểu khác nhau về dòng rỗng và dòng trắng.
- `String.lines()` loại các dấu kết thúc dòng; nếu cần biết hoặc bảo toàn newline cuối và kiểu CR/LF/CRLF, phải giữ thông tin từ đầu vào gốc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-003">Khác biệt giữa lines và split với limit</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt giữa String.lines() và String.split() với tham số limit trong Java



## 1. Mục tiêu bài học

Java cung cấp hai cách phổ biến để xử lý một chuỗi có nhiều dòng:

- `String.lines()` (Java 11+) trả về `Stream<String>`, duyệt các dòng phân cách bằng **LF (`\n`), CR (`\r`) hoặc CRLF (`\r\n`)**. Đây là thao tác theo dòng; không có tham số `limit` kiểu `split`.
- `String.split(regex)` và `String.split(regex, limit)` trả về **mảng `String[]`**, tách theo một **biểu thức chính quy**. Tham số `limit` điều khiển **số lần áp dụng regex** và **việc giữ hay bỏ các phần rỗng ở cuối**.

Sự khác biệt quan trọng nhất là **dữ liệu rỗng**, **cách giới hạn phần tử**, **bộ ký tự xuống dòng được nhận diện**, và **cách tiêu thụ kết quả**. Không nên coi `lines()` là cách viết khác hoàn toàn tương đương với `split("\\R")`.

Ví dụ nhanh:

```java
String text = "A\n\nB\n";

System.out.println(text.lines().toList());            // [A, , B]
System.out.println(Arrays.toString(text.split("\\R")));    // [A, , B]
System.out.println(Arrays.toString(text.split("\\R", -1))); // [A, , B, ]
```

Dù hai dòng đầu trông giống nhau ở ví dụ này, chúng **khác về kiểu trả về và chế độ làm việc**. Dòng cuối có thêm một phần tử `""` biểu thị ô trống sau ký tự xuống dòng cuối cùng. Khi in trực tiếp `List` hay mảng, phần tử rỗng rất khó quan sát; bài này sẽ dùng cách hiển thị riêng `<...>`.

**Phạm vi bài này:** `String.lines()` xem LF, CR và CRLF là kết thúc dòng và không phát sinh dòng rỗng chỉ từ newline cuối. Phần giải thích chi tiết các terminator nằm ở bài **lines và các kiểu kết thúc dòng CR, LF, CRLF**, còn bảng ranh giới chuỗi rỗng/newline cuối nằm ở bài **Chuỗi rỗng và ký tự xuống dòng ở cuối**. Bài hiện tại tập trung vào chế độ `limit`, kiểu trả về và số phần tử.

**Khác biệt ký tự phân tách:** regex `\\R` nhận Unicode linebreak rộng hơn LF/CR/CRLF, ví dụ U+2028; `split("\\n")` chỉ tách LF nên có thể để lại CR ở chuỗi trước một CRLF. Chọn regex theo định dạng đầu vào.

## 2. String.split(regex, limit): ba chế độ limit

Cú pháp:

```java
String[] parts = text.split(regex);         // Tương đương text.split(regex, 0)
String[] all = text.split(regex, -1);       // Giữ phần rỗng cuối
String[] atMostTwo = text.split(regex, 2); // Tối đa hai phần tử
```

`regex` là **regular expression** (không phải chuỗi phân cách literal). Để tách theo mọi kiểu ngắt dòng Unicode mà Java regex hỗ trợ, dùng `"\\R"` trong **mã nguồn Java**. Hai dấu `\\` trong Java source tạo một dấu `\` ở chuỗi runtime; `\R` là cú pháp regex biểu thị một chuỗi xuống dòng Unicode. Viết `"\R"` trực tiếp trong source sẽ không biên dịch vì `\R` không phải Java string escape.

| Giá trị `limit` | Cách tách | Phần rỗng ở cuối |
| --- | --- | --- |
| `limit > 0` | Pattern được áp dụng **tối đa `limit - 1` lần**; phần tử cuối chứa toàn bộ phần input còn lại | Được giữ nếu có trong các phần kết quả |
| `limit = 0` | Pattern được áp dụng nhiều lần nhất có thể | **Bỏ tất cả** phần tử `""` ở cuối |
| `limit < 0`, ví dụ `-1` | Pattern được áp dụng nhiều lần nhất có thể | **Giữ tất cả** phần tử `""` ở cuối |

`text.split(regex)` tương đương `text.split(regex, 0)`. Dấu `0` ở đây **không** có nghĩa là trả về không phần tử; nó là chế độ tách tối đa nhưng loại các chuỗi rỗng cuối.

Ví dụ cụ thể với `"A\n\nB\n"`:

- `split("\\R", 0)` → `["A", "", "B"]`. Có dòng trống ở giữa, nhưng bỏ trường trống cuối.
- `split("\\R", -1)` → `["A", "", "B", ""]`. Giữ cả trường rỗng sau dấu xuống dòng cuối.
- `split("\\R", 2)` → `["A", "\nB\n"]`. Chỉ tách tại dấu xuống dòng **đầu tiên**; phần thứ hai còn nguyên một dấu xuống dòng trước `B` và dấu xuống dòng cuối.
- `split("\\R", 1)` → mảng một phần tử duy nhất chứa toàn bộ `"A\n\nB\n"`. Không có lần tách nào được áp dụng vì `limit - 1 = 0`.

Điều này cho thấy `limit` dương của `split` **không tương đương** với số dòng cần lấy từ `lines().limit(n)`.

## 3. Ví dụ chạy được với Java 17+

Tạo file **`LinesVsSplitDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LinesVsSplitDemo {
    // Bao mỗi phần tử trong <> và hiện ký tự xuống dòng dưới dạng \n, \r
    // để phân biệt phần tử rỗng với mảng/danh sách không có phần tử.
    static String show(List<String> parts) {
        return parts.stream()
                .map(s -> "<" + s
                        .replace("\r", "\\r")
                        .replace("\n", "\\n") + ">")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static String show(String[] parts) {
        return show(Arrays.asList(parts));
    }

    public static void main(String[] args) {
        String input = "A\n\nB\n";

        System.out.println("lines       = " + show(input.lines().toList()));
        System.out.println("split(0)    = " + show(input.split("\\R")));
        System.out.println("split(-1)   = " + show(input.split("\\R", -1)));
        System.out.println("split(2)    = " + show(input.split("\\R", 2)));
        System.out.println("limit(2)    = " + show(input.lines().limit(2).toList()));

        System.out.println("mix         = "
                + show("A\r\nB\rC\nD".lines().toList()));

        System.out.println("empty lines = " + "".lines().count());
        System.out.println("empty split = " + "".split("\\R").length);

        String unicode = "A\u2028B"; // Unicode LINE SEPARATOR (U+2028)
        System.out.println("unicode lines = " + unicode.lines().count());
        System.out.println("unicode split = " + unicode.split("\\R").length);
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac --release 17 LinesVsSplitDemo.java
java LinesVsSplitDemo
```

**Stdout** (đã đối chiếu hành vi các biểu thức qua JShell/JDK):

```text
lines       = [<A>, <>, <B>]
split(0)    = [<A>, <>, <B>]
split(-1)   = [<A>, <>, <B>, <>]
split(2)    = [<A>, <\nB\n>]
limit(2)    = [<A>, <>]
mix         = [<A>, <B>, <C>, <D>]
empty lines = 0
empty split = 1
unicode lines = 1
unicode split = 2
```

Các kết quả quan trọng:

1. `lines()` giữ **dòng rỗng nội bộ** của `"A\n\nB\n"`, nhưng không tạo thêm dòng rỗng sau ký tự `\n` cuối.
2. `split("\\R", -1)` giữ **cột trống cuối**, thích hợp khi phải phân biệt một bản ghi kết thúc bằng delimiter với bản ghi không có delimiter cuối.
3. `split("\\R", 2)` giữ lại phần `"\nB\n"` trong token cuối; `lines().limit(2)` chỉ lấy **hai dòng đầu tiên** là `"A"` và `""`.
4. Input rỗng là trường hợp khác biệt rõ: `lines()` cho `0` dòng, còn `split("\\R")` cho mảng có **một** phần tử `""`. Vì vậy in `Arrays.toString` trực tiếp có thể khiến cả hai trông như `[]` dù số phần tử khác nhau.
5. Ký tự U+2028 được `\\R` nhận diện là line break, còn `lines()` **không** nhận diện nó như một trong ba line terminator LF/CR/CRLF.

## 4. Những khác biệt dễ nhầm khi dùng limit

### 5.1. lines().limit(n) đếm dòng, split(regex, n) đếm phần tối đa

Cho input `"red\ngreen\nblue"`:

```java
String colors = "red\ngreen\nblue";

List<String> topTwo = colors.lines().limit(2).toList();
// ["red", "green"]

String[] twoParts = colors.split("\\R", 2);
// ["red", "green\nblue"]
```

`lines().limit(2)` bỏ qua dòng `blue` vì terminal operation không yêu cầu đến đó. Ngược lại, `split("\\R", 2)` không bỏ dữ liệu phía sau dấu phân tách đầu tiên; toàn bộ phần dư `green\nblue` nằm trong phần tử cuối.

**Khi nào dùng cách nào?**

- Chỉ cần hai dòng đầu để hiển thị, preview hoặc kiểm tra header: `lines().limit(2)`.
- Muốn tách **phần đầu** và **phần thân còn lại**, ví dụ dòng đầu là header và phần còn lại là nội dung: `split("\\R", 2)`. Cần xử lý input rỗng và trường hợp không có delimiter phù hợp theo nghiệp vụ.

### 5.2. Không nhầm split(..., 0) với Stream.limit(0)

`split("\\R", 0)` yêu cầu tách với chế độ loại trường rỗng ở cuối; kết quả vẫn có thể chứa nhiều phần tử. `lines().limit(0)` là Stream không có phần tử nào. Hai số `0` ở hai API có ý nghĩa hoàn toàn khác:

```java
String data = "a\nb";
System.out.println(data.split("\\R", 0).length); // 2
System.out.println(data.lines().limit(0).count()); // 0
```

### 5.3. filter và limit thay đổi thứ tự logic

```java
String input = "\nfirst\n\nsecond\nthird";

List<String> a = input.lines()
        .filter(s -> !s.isBlank())
        .limit(2)
        .toList();
// ["first", "second"]

List<String> b = input.lines()
        .limit(2)
        .filter(s -> !s.isBlank())
        .toList();
// ["first"]
```

Trong `a`, chương trình tìm hai dòng **không trống** đầu tiên; trong `b`, chỉ xét hai dòng gốc đầu tiên rồi lọc. Quy tắc tương tự áp dụng cho pipeline Stream nói chung.

## 5. Dòng rỗng: bảng các trường hợp biên

Bảng dùng ký hiệu `<A>` cho chuỗi `"A"` và `<>` cho chuỗi rỗng. Dấu ngoặc vuông `[...]` biểu diễn danh sách các phần tử; `[]` nghĩa là không có phần tử nào. Tất cả chuỗi input sau đây đều sử dụng ký tự `\n` thật.

| Input trong Java | `lines()` | `split("\\R", 0)` | `split("\\R", -1)` |
| --- | --- | --- | --- |
| `""` | `[]` | `[<>]` | `[<>]` |
| `"A"` | `[<A>]` | `[<A>]` | `[<A>]` |
| `"A\n"` | `[<A>]` | `[<A>]` | `[<A>, <>]` |
| `"A\n\n"` | `[<A>, <>]` | `[<A>]` | `[<A>, <>, <>]` |
| `"\nA"` | `[<>, <A>]` | `[<>, <A>]` | `[<>, <A>]` |
| `"\n"` | `[<>]` | `[]` | `[<>, <>]` |
| `"A\nB"` | `[<A>, <B>]` | `[<A>, <B>]` | `[<A>, <B>]` |

Không thể thay thế `lines()` bằng `split("\\R", 0)` và mong giữ nguyên 100% cấu trúc dòng trong mọi trường hợp. Hai API đặc biệt khác nhau với chuỗi rỗng và khi input kết thúc bằng nhiều delimiter. Nếu ứng dụng cần lưu chính xác một danh sách trường/record, phải định nghĩa rõ liệu dấu xuống dòng cuối có đại diện cho một trường rỗng mới hay chỉ là **dấu kết thúc dòng**.

Đối với `split`, mặc định các phần rỗng ở **giữa** vẫn được giữ; việc loại bỏ chỉ áp dụng với các phần rỗng ở **cuối** khi limit bằng 0.

## 6. Lựa chọn API theo yêu cầu thực tế

| Nhu cầu | Cách nên cân nhắc | Lý do |
| --- | --- | --- |
| Đọc các dòng trong một chuỗi, lọc và lấy vài dòng đầu | `text.lines().filter(...).limit(n)` | Stream lười, `limit` cho phép dừng duyệt khi đã đủ |
| Lấy toàn bộ các dòng LF/CR/CRLF thành `List` | `text.lines().toList()` | Dễ nối tiếp `map`/`filter`, xử lý theo dòng |
| Cần `String[]` của các phần phân cách bằng Unicode newline, bỏ rỗng cuối | `text.split("\\R")` | Regex phân tách, `limit=0` mặc định |
| Cần giữ trường rỗng ở cuối của dữ liệu delimiter | `text.split("\\R", -1)` | Bảo toàn vị trí phần tử trống sau delimiter cuối |
| Cần chia một lần để lấy header và phần còn lại | `text.split("\\R", 2)` | Phần tử cuối giữ nguyên dữ liệu chưa tách |
| Cần giữ mọi ký tự, không muốn hiểu regex | Chọn API literal phù hợp hoặc xử lý chỉ số trực tiếp | `split` nhận regex; phải escape delimiter đặc biệt |

**Lưu ý về hiệu năng:** `String.lines()` thường phù hợp hơn khi cần pipeline Stream và có thể dừng sớm. `split` trả mảng nên tạo các phần kết quả trước khi caller xử lý. Với input nhỏ hoặc khi cần đầy đủ mọi phần, hãy chọn theo semantics và kiểm chứng bằng benchmark nếu hiệu năng thật sự quan trọng. Tránh khẳng định Stream luôn nhanh hơn.

**Lưu ý về phạm vi:** Cả hai API đang thao tác trên một `String` **đã có trong bộ nhớ**; `String.lines()` không tự đọc dữ liệu từ file hay mạng. Khi xử lý file rất lớn, `Files.lines(path)` (trả Stream đọc từ file) là API khác, với yêu cầu quản lý tài nguyên riêng.

## 7. Bài tập tự kiểm tra

**Bài 1: Dự đoán số phần tử.** Với `String input = "\n\n";`, xét:

```java
System.out.println(input.lines().count());
System.out.println(input.split("\\R", 0).length);
System.out.println(input.split("\\R", -1).length);
```

Đáp án tương ứng là **2, 0, 3**. Hai dấu xuống dòng kết thúc hai dòng rỗng; `split` với limit 0 bỏ tất cả chuỗi rỗng cuối, còn limit âm giữ ba phần rỗng trước, giữa và sau hai delimiter.

**Bài 2: Chỉ cần hai dòng hợp lệ đầu tiên.** Với `"\nalpha\n\nbeta\ngamma"`, viết:

```java
List<String> result = "\nalpha\n\nbeta\ngamma"
        .lines()
        .filter(s -> !s.isBlank())
        .limit(2)
        .toList();
System.out.println(result); // [alpha, beta]
```

Nếu chuyển `limit(2)` lên trước `filter`, kết quả chỉ còn `[alpha]`, vì chỉ xét hai dòng nguồn đầu.

**Bài 3: Giữ nguyên phần nội dung sau dòng tiêu đề.** Với `"HEADER\nbody line 1\nbody line 2"`, dùng:

```java
String[] parts = "HEADER\nbody line 1\nbody line 2"
        .split("\\R", 2);
System.out.println(parts[0]); // HEADER
System.out.println(parts[1]); // in hai dòng body, còn dấu \n giữa chúng
```

`split("\\R", 2)` phù hợp vì phần tử thứ hai giữ nguyên `"body line 1\nbody line 2"`. Nếu dùng `lines().limit(2)` sẽ mất dòng body thứ hai.

**Bài 4: Input rỗng.** Giải thích vì sao `"".lines().count()` bằng 0 nhưng `"".split("\\R").length` bằng 1: `lines` định nghĩa **dòng**, còn `split` khi không có delimiter khớp sẽ trả lại **toàn bộ input** làm một token, kể cả khi token đó là `""`.

## 8. Ghi nhớ nhanh

`String.lines()` là công cụ xử lý **dòng LF/CR/CRLF bằng Stream lười**, thích hợp cho pipeline và lấy một số dòng đầu. `String.split(regex, limit)` là công cụ **tách theo regex thành mảng**; `limit=0` bỏ trường rỗng cuối, `limit<0` giữ trường rỗng cuối, `limit>0` giới hạn số lần tách và giữ phần dư ở token cuối. `Stream.limit(n)` giới hạn **số dòng được phát ra**; nó không mang ý nghĩa `split(..., n)`. Hãy lựa chọn theo **dữ liệu rỗng cần giữ**, **loại newline thực tế**, **kiểu trả về** và **cách dùng phần dữ liệu còn lại**.

**Tài liệu chính thức:** [Java SE 17 — String.lines()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#lines()), [Java SE 17 — String.split(String, int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#split(java.lang.String,int)), [Java SE 17 — Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html), [Java SE 17 — Stream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-004">indent thay đổi lề và chuẩn hóa kết thúc dòng</a>

<details>
<summary>Xem chi tiết</summary>

# String.indent(int): thay đổi lề và chuẩn hóa kết thúc dòng trong Java

## 1. Mục tiêu bài học

Khi ghép văn bản nhiều dòng để hiển thị log, sinh nội dung hoặc trình bày dữ liệu, ta thường muốn tăng hoặc giảm số ký tự trắng ở **đầu từng dòng**. Java cung cấp **String.indent(int n)** (từ **Java 12**) để thực hiện thao tác này, đồng thời **chuẩn hóa ký tự kết thúc dòng về LF**.

Sau bài học, bạn có thể:

- Giải thích tác dụng của **n > 0**, **n < 0** và **n = 0**.
- Dự đoán chính xác kết quả với dấu cách, tab, dòng trống, CR, LF, CRLF và chuỗi không có newline.
- Phân biệt **indent()** với **lines()**, **stripLeading()**, **stripIndent()** và cách ghép thủ công.
- Tránh các lỗi thường gặp khi xử lý văn bản từ Windows, Unix, text block và dữ liệu có định dạng cố định.

Điểm quan trọng: **indent() không chỉ thay đổi khoảng trắng ở đầu dòng**. Một lời gọi **indent(0)** cũng có thể thay đổi chuỗi vì ký tự kết thúc dòng được chuẩn hóa và dòng cuối có thể được thêm LF.

## 2. Chữ ký phương thức và cơ chế tổng quát

~~~java
public String indent(int n)
~~~

Đây là phương thức của **java.lang.String**. String bất biến: phương thức trả về một String kết quả, không sửa đối tượng nguồn.

Về mặt khái niệm, Java thực hiện:

1. Tách chuỗi đầu vào thành các dòng theo quy tắc tương tự **String.lines()**.
2. Điều chỉnh các ký tự trắng **ở đầu mỗi dòng** theo tham số **n**.
3. Ghép các dòng đã điều chỉnh, **mỗi dòng theo sau bằng LF** (ký tự **\n**, U+000A).

Quy tắc theo tham số:

| Giá trị n | Tác động lên từng dòng | Lưu ý |
| --- | --- | --- |
| **n > 0** | Thêm đúng **n dấu cách ASCII U+0020** ở đầu dòng | Không phải thêm n tab; áp dụng cả dòng rỗng |
| **n = 0** | Không đổi nội dung của từng dòng | Vẫn chuẩn hóa kết thúc dòng và có thể thêm LF cuối |
| **n < 0** | Bỏ tối đa **\|n\| ký tự whitespace** ở đầu mỗi dòng | Nếu có ít hơn \|n\| ký tự trắng thì bỏ hết; phần nội dung còn lại được giữ |
| **Chuỗi rỗng** | Trả về chuỗi rỗng | Không tự tạo một dòng hoặc newline |

Phương thức xử lý **từng dòng độc lập**. Nó không tìm “lề chung” rồi chỉ bỏ đúng mức lề tối thiểu của toàn khối. Đó là điểm khác biệt quan trọng với **stripIndent()**.

## 3. Thế nào là một dòng và ký tự kết thúc dòng?

**String.lines()** nhận diện ba kiểu dấu kết thúc dòng sau:

| Ký tự | Ký hiệu | Thường gặp |
| --- | --- | --- |
| Line Feed | **\n** (LF, U+000A) | Linux/Unix và nhiều API |
| Carriage Return | **\r** (CR, U+000D) | Dữ liệu cũ hoặc một số nguồn |
| CR rồi LF | **\r\n** (CRLF) | Văn bản Windows |

**indent()** chuẩn hóa tất cả những kiểu kết thúc dòng đó thành **\n**.

Một dòng là phần ký tự **không bao gồm dấu kết thúc dòng** của nó. Nếu chuỗi đã kết thúc bằng một dấu xuống dòng, **lines() không tạo thêm một dòng rỗng ảo sau dấu cuối cùng**.

Ví dụ:

~~~text
Input khái niệm:  A\r\nB\rC\n
Các dòng:         "A", "B", "C"
Sau indent(0):    A\nB\nC\n
~~~

Hai hệ quả dễ bất ngờ:

- Với input **"A"** (không có ký tự xuống dòng), **"A".indent(0)** trả **"A\n"**.
- Với input **"A\n"**, **"A\n".indent(0)** vẫn là **"A\n"**, không biến thành **"A\n\n"**.

Chuỗi **""** không có dòng nào nên **"".indent(0)** và **"".indent(2)** đều là **""**.

**Phạm vi chuẩn hóa của indent() là ba loại line terminator nói trên.** Không nên suy diễn rằng nó nhận diện và đổi mọi ký tự Unicode có thể trông giống xuống dòng, ví dụ Unicode line separator U+2028. Đừng nhầm với quy tắc rộng hơn của regex **\R**.

## 4. Ví dụ Java 17+ chạy độc lập, hiển thị rõ khoảng trắng

Khi dùng **System.out.println()**, khoảng trắng ở đầu dòng thường khó nhìn và **println()** còn thêm newline của chính nó. Ví dụ sau chuyển dấu cách thành dấu **_**, tab thành **<TAB>**, LF thành chữ **\n** và CR thành chữ **\r** trước khi in. Dấu gạch dưới chỉ là **ký hiệu hiển thị**, không phải ký tự mà **indent()** chèn vào chuỗi thật.

Tạo tệp **IndentDemo.java**:

~~~java
public class IndentDemo {
    private static String visible(String text) {
        return text.replace(" ", "_")
                .replace("\t", "<TAB>")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static void show(String label, String text) {
        System.out.println(label + ": [" + visible(text) + "]");
    }

    public static void main(String[] args) {
        String mixed = "A\r\n  B\rC\n";

        show("origin", mixed);
        show("indent(+2)", mixed.indent(2));
        show("indent(-2)", mixed.indent(-2));
        show("indent(0)", mixed.indent(0));

        show("blank line", "X\n\nY".indent(2));
        show("tab", "  X\n\tY\nZ".indent(-1));
        show("empty", "".indent(2));
        show("one line", "X".indent(0));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac IndentDemo.java
java IndentDemo
~~~

Kết quả:

~~~text
origin: [A\r\n__B\rC\n]
indent(+2): [__A\n____B\n__C\n]
indent(-2): [A\nB\nC\n]
indent(0): [A\n__B\nC\n]
blank line: [__X\n__\n__Y\n]
tab: [_X\nY\nZ\n]
empty: []
one line: [X\n]
~~~

Giải thích cụ thể:

1. Input **mixed** gồm ba dòng. Dòng **B** vốn đã có hai dấu cách đầu dòng.
2. **indent(2)** thêm hai dấu cách vào **mọi dòng**, nên B có tổng cộng bốn dấu cách. Đồng thời CRLF và CR đều trở thành LF.
3. **indent(-2)** bỏ hai dấu cách của B, còn A/C không có dấu cách đầu nên giữ nguyên phần chữ.
4. **indent(0)** giữ đúng hai dấu cách trước B nhưng vẫn đổi ba loại kết thúc dòng về LF.
5. Với dòng trống nằm giữa X và Y, **indent(2)** vẫn thêm hai dấu cách vào dòng trống, khiến dòng đó không còn là chuỗi có độ dài 0 nếu xét nội dung ký tự.
6. **indent(-1)** bỏ một ký tự trắng đầu dòng: từ hai dấu cách của X còn một, còn tab đứng đầu Y bị bỏ hoàn toàn.
7. Chuỗi rỗng vẫn rỗng; chuỗi **"X"** có một dòng nên được thêm LF ở cuối sau **indent(0)**.

## 5. n dương: thêm dấu cách vào đầu mỗi dòng

Khi **n > 0**, Java tạo đúng **n ký tự space U+0020** và đặt ở đầu mỗi dòng.

~~~java
String data = "first\nsecond";
String result = data.indent(4);

System.out.println(result.equals("    first\n    second\n")); // true
~~~

Không quan trọng dòng đã có lề hay chưa; **indent(4)** không căn chỉnh các dòng về một cột tuyệt đối mà **tăng thêm 4 dấu cách cho mỗi dòng**.

Với chuỗi:

~~~text
first
  second
~~~

sau **indent(2)**, mức lề tương đối vẫn chênh nhau hai dấu cách:

~~~text
  first
    second
~~~

Cần phân biệt:

- **indent(2)** tăng mức lề của **từng dòng** lên hai dấu cách.
- **String.repeat(2)** lặp lại **toàn bộ chuỗi** hai lần; không liên quan đến quy tắc tăng lề.
- Gọi **indent(2)** hai lần thường thêm tổng cộng bốn dấu cách vào mỗi dòng không rỗng về mặt tách dòng; phương thức không tự nhận biết “đã thụt lề trước đó”.

**Dòng trống:** nếu đầu vào có dòng trống thật sự giữa hai terminator, **indent(n > 0)** cũng chèn dấu cách vào dòng trống ấy:

~~~java
String input = "A\n\nB";
String result = input.indent(2);
System.out.println(result.equals("  A\n  \n  B\n")); // true
~~~

Nếu một hệ thống cần dòng trống có độ dài đúng 0 hoặc cấm trailing whitespace, cần cân nhắc bước xử lý khác. **indent() không tự động loại khoảng trắng ở dòng rỗng**.

## 6. n âm: bỏ tối đa |n| ký tự trắng ở đầu từng dòng

Khi **n < 0**, Java loại bỏ từ 0 đến **|n|** ký tự whitespace ở **đầu mỗi dòng**, tùy lượng ký tự có thật.

~~~java
String text = "    A\n B\nC";
String result = text.indent(-3);

System.out.println(result.equals(" A\nB\nC\n")); // true
~~~

- Dòng A có bốn dấu cách: bỏ ba, còn một.
- Dòng B có một dấu cách: bỏ một, còn không.
- Dòng C không có dấu cách: không bỏ gì.

Nếu đầu dòng có ít hơn **|n|** ký tự trắng, phương thức **không xóa lấn sang ký tự không phải whitespace**. Ví dụ **" A".indent(-5)** vẫn có phần chữ **A**, rồi LF.

### 6.1. Tab tính là một ký tự, không phải một độ rộng hiển thị

Java dùng khái niệm whitespace theo **Character.isWhitespace** cho việc bỏ lề âm. Ký tự tab **\t** được tính là **một ký tự trắng**, không tự mở thành 4 hay 8 cột:

~~~java
String source = "\t  A";
System.out.println(source.indent(-1).equals("  A\n")); // true
System.out.println(source.indent(-2).equals(" A\n"));  // true
~~~

Độ rộng hiển thị thực tế của tab còn tùy tab stop trong terminal/editor. Vì vậy, **indent(-2)** nghĩa là bỏ tối đa **hai ký tự whitespace**, không bảo đảm lùi trái hai **cột thị giác**.

Java không chỉ xem dấu cách ASCII là whitespace. Nhưng cũng không phải **mọi ký tự Unicode trông giống dấu cách** đều thỏa **Character.isWhitespace**. Nếu nhập liệu chứa Unicode đặc biệt, hãy kiểm tra loại ký tự và quy tắc mong muốn.

### 6.2. indent(-n) khác với bỏ một mức lề chung

Xét ba dòng:

~~~text
    A
  B
      C
~~~

Dùng **indent(-2)** sẽ bỏ hai ký tự trắng ở đầu mỗi dòng, cho:

~~~text
  A
B
    C
~~~

Không có bước nào phân tích toàn khối để suy ra “lề chuẩn”. Nếu cần loại **common/incidental indentation**, hãy xem **stripIndent()** thay vì giả định **indent(-n)** hoạt động như nó.

## 7. n bằng 0: tại sao kết quả vẫn thay đổi?

**indent(0)** không thay đổi ký tự của từng dòng đã được tách, nhưng **nó vẫn xây dựng lại chuỗi với LF kết thúc mỗi dòng**.

~~~java
String input = "red\r\ngreen\rblue";
String output = input.indent(0);

System.out.println(output.equals("red\ngreen\nblue\n")); // true
System.out.println(input.equals(output));               // false
~~~

Input có CRLF, CR và không có terminator cuối. Output gồm ba dòng đều kết thúc bằng LF.

Điều này hữu ích khi bạn muốn chuyển văn bản nhiều dòng từ các nền tảng khác nhau về **dạng LF thống nhất** mà giữ nguyên các ký tự đầu mỗi dòng.

Tuy nhiên, có hai giới hạn cần nhớ:

- **Thêm LF ở cuối:** nếu input không rỗng và dòng cuối chưa có terminator, **indent(0)** thêm LF. Vì vậy đây không phải bộ chuẩn hóa chỉ thay CRLF/CR thành LF rồi giữ nguyên hoàn toàn sự có/mất newline cuối.
- **Dòng trống có trước:** các dòng trống thật sự vẫn được giữ theo quy tắc **lines()**, với các terminator đầu ra là LF. Không có dòng rỗng ảo mới được sinh ra *sau* terminator cuối của input.

Ví dụ về khác biệt dấu kết thúc:

| Input là Java String literal | Kết quả sau **indent(0)** |
| --- | --- |
| **""** | **""** |
| **"A"** | **"A\n"** |
| **"A\n"** | **"A\n"** |
| **"A\r"** | **"A\n"** |
| **"A\r\n"** | **"A\n"** |
| **"A\n\n"** | **"A\n\n"** |
| **"\n"** | **"\n"** |

Bảng dùng **ký hiệu Java String literal**: chẳng hạn **"A\n"** chứa một ký tự A và một ký tự LF, không chứa hai ký tự dấu gạch chéo ngược và n.

## 8. Dòng rỗng, whitespace cuối dòng và các trường hợp biên

### 8.1. indent() chỉ sửa phần đầu mỗi dòng

**indent() không cắt whitespace ở cuối nội dung dòng**:

~~~java
String input = "A  \n B  ";
String out = input.indent(-1);

System.out.println(out.equals("A  \nB  \n")); // true
~~~

Hai dấu cách sau A và hai dấu cách sau B vẫn tồn tại. Muốn cắt khoảng trắng cuối dòng, cần xử lý riêng theo nghiệp vụ.

### 8.2. Chuỗi có nhiều terminator liên tiếp

~~~java
String input = "A\r\n\r\nB";
System.out.println(input.indent(0).equals("A\n\nB\n")); // true
~~~

CRLF là một terminator; CRLF tiếp theo biểu diễn một **dòng rỗng thực sự** giữa A và B. Sau chuẩn hóa, vẫn có một dòng rỗng ở giữa.

### 8.3. Chuỗi chỉ chứa dấu cách

~~~java
String spaces = "   ";
System.out.println(spaces.indent(-2).equals(" \n"));  // true
System.out.println(spaces.indent(-5).equals("\n"));   // true
System.out.println(spaces.indent(2).equals("     \n")); // true
~~~

Đây là **một dòng có nội dung chỉ gồm khoảng trắng**, không phải chuỗi rỗng. Kết quả vẫn có LF cuối dòng. Nếu nghiệp vụ muốn xóa bỏ toàn bộ các dòng trắng, cần quyết định quy tắc rõ ràng; **indent() không mặc định lọc dòng**.

### 8.4. Giá trị n cực lớn

Tránh gọi **indent(n)** với giá trị dương khổng lồ lấy trực tiếp từ người dùng: phép thêm ký tự diễn ra **theo mỗi dòng**, có thể làm bộ nhớ cần thiết tăng mạnh hoặc vượt giới hạn String/heap. Số lượng ký tự được thêm xấp xỉ **n × số dòng**.

Với **n âm**, hành vi là bỏ tối đa số ký tự trắng hiện có, không thể xóa ký tự nội dung. Java xử lý được cả trường hợp **Integer.MIN_VALUE** mà không cần bạn tự lấy **Math.abs(n)**; tự tính **Math.abs(Integer.MIN_VALUE)** trong mã phụ trợ có thể tràn số nguyên nên cần tránh.

## 9. So sánh với các phương thức String liên quan

| API | Mục đích | Điểm khác so với **indent(int)** |
| --- | --- | --- |
| **lines()** (Java 11+) | Trả một Stream các dòng, bỏ terminator | Không tự thêm/bớt lề hay trả String đã ghép |
| **stripLeading()** (Java 11+) | Bỏ whitespace ở **đầu toàn bộ String** | Không lặp qua mọi dòng |
| **stripTrailing()** (Java 11+) | Bỏ whitespace ở **cuối toàn bộ String** | Có thể ảnh hưởng newline cuối, không phải đổi lề từng dòng |
| **strip()** (Java 11+) | Bỏ whitespace hai đầu toàn bộ String | Không giữ nguyên cấu trúc nhiều dòng theo cùng cách |
| **stripIndent()** (Java 15+) | Loại common/incidental indentation, xử lý dòng trắng và trailing whitespace | Tính lề tối thiểu từ các dòng phù hợp; không đơn giản là bỏ n ký tự từng dòng |
| **repeat(int)** (Java 11+) | Lặp toàn bộ String n lần | Không thụt lề theo dòng |
| **indent(int)** (Java 12+) | Thêm hoặc bỏ tối đa số ký tự trắng đầu **mỗi dòng**, chuẩn hóa LF | Có tham số số lượng và luôn tái tạo terminator dạng LF |

### 9.1. indent(-2) khác stripLeading()

~~~java
String input = "  A\n  B";

System.out.println(input.indent(-2).equals("A\nB\n"));   // true
System.out.println(input.stripLeading().equals("A\n  B")); // true
~~~

**stripLeading()** chỉ loại dấu cách ở trước A, vì nó xử lý phần đầu của **toàn bộ chuỗi**; dấu cách trước B nằm sau newline nên vẫn tồn tại.

### 9.2. indent(-n) khác stripIndent()

**stripIndent()** (Java 15+) nhằm xử lý common/incidental indentation, đặc biệt phù hợp với text block. Nó xác định mức lề tối thiểu theo quy tắc riêng, loại mức lề ấy ở các dòng thích hợp và loại trailing whitespace theo định nghĩa của phương thức.

Ví dụ:

~~~java
String input = "    A\n  B\n      C";
String fixed = input.indent(-2);

System.out.println(fixed.equals("  A\nB\n    C\n")); // true
~~~

Trong ví dụ này, nếu mục tiêu là dịch toàn khối để dòng ít thụt nhất chạm lề trái, **stripIndent()** diễn đạt mục tiêu đó tự nhiên hơn. Đừng hoán đổi hai phương thức trong code nếu chưa kiểm tra dòng trống, dòng cuối, trailing whitespace và thay đổi line terminator, vì chúng có quy tắc khác nhau.

### 9.3. Text block và indent()

Text block (Java 15+) loại incidental indentation theo quy tắc riêng tại thời điểm tạo giá trị String. Sau đó bạn vẫn có thể gọi **indent()** lên giá trị ấy:

~~~java
String message = """
        hello
        world
        """;

System.out.println(message.indent(2).equals("  hello\n  world\n")); // true
~~~

Trong ví dụ này, text block gốc đã được xử lý lề chung, rồi **indent(2)** tăng thêm đúng hai dấu cách mỗi dòng.

Đừng đếm lề dựa chỉ vào vị trí dấu ngoặc text block trong source code: giá trị String thực tế sau khi Java áp dụng text block mới là đầu vào của **indent()**.

## 10. Ứng dụng thực tế

### 10.1. Thụt lề văn bản nhiều dòng trong báo cáo

~~~java
String detail = "name: Alice\nrole: developer";
String report = "User:\n" + detail.indent(2);

System.out.print(report);
~~~

Kết quả:

~~~text
User:
  name: Alice
  role: developer
~~~

Chọn **print()** để không thêm một newline ngoài ý muốn: **indent(2)** đã đặt LF ở cuối mỗi dòng của **detail**.

### 10.2. Chuẩn hóa kết thúc dòng từ nguồn khác nhau

~~~java
String imported = "first\r\nsecond\rthird";
String normalized = imported.indent(0);

System.out.println(normalized.equals("first\nsecond\nthird\n")); // true
~~~

Dạng LF thống nhất hữu ích khi so sánh nội dung text theo từng dòng hoặc đưa vào pipeline chỉ chấp nhận LF. Tuy nhiên, nếu checksum/file format yêu cầu giữ nguyên byte hay phân biệt rõ có/không có LF cuối, **indent(0)** là phép biến đổi có ý nghĩa, cần lựa chọn có chủ đích.

### 10.3. Giảm lề trong đoạn đã có lề cố định

~~~java
String source = "    GET /users\n    Accept: application/json";
String compact = source.indent(-4);

System.out.println(
        compact.equals("GET /users\nAccept: application/json\n")); // true
~~~

Nếu mỗi dòng có ít nhất bốn whitespace đầu dòng, **indent(-4)** loại đúng tối đa bốn ký tự. Nếu có dòng không đủ lề, nó chỉ bỏ phần có thể, không ném lỗi vì thiếu khoảng trắng.

## 11. Lỗi thường gặp và cách kiểm tra

1. **Nghĩ indent(0) sẽ giữ nguyên String.** Nó chuẩn hóa CR/CRLF thành LF và thêm LF cuối cho chuỗi không rỗng chưa có terminator. Kiểm tra bằng **equals()**, **length()** hoặc biểu diễn escape.
2. **Cho rằng n âm sẽ xóa đúng n cột hiển thị.** Tab được tính là một ký tự whitespace, không mở rộng theo tab stop; độ rộng thị giác có thể khác.
3. **Cho rằng indent(-n) tính common indentation của toàn khối.** Nó xóa tối đa |n| ký tự trên từng dòng độc lập. Khi muốn loại incidental indentation, cân nhắc **stripIndent()**.
4. **Bỏ qua dòng trống khi n dương.** Dòng trống cũng nhận n dấu cách; có thể trở thành dòng chỉ có whitespace.
5. **Dùng println() rồi tưởng indent() sinh hai newline.** Nếu String kết quả đã kết thúc bằng LF, **println()** có thể thêm một terminator nữa; dùng **print()** hoặc kiểm soát dấu xuống dòng khi ghép.
6. **Nghĩ indent() xóa dấu cách cuối dòng.** Nó chỉ chỉnh phần đầu; khoảng trắng ở cuối phần chữ vẫn còn.
7. **Cho rằng indent() xử lý mọi kiểu Unicode line separator.** Quy tắc tách dòng của **String.lines()** tập trung vào CR, LF, CRLF. Với định dạng dữ liệu khác cần phương án phân tích tương ứng.
8. **Đem indent(0) vào pipeline nhạy cảm byte mà không cân nhắc.** CRLF sang LF và thêm LF cuối là thay đổi nội dung; có thể làm khác checksum hoặc kết quả so sánh byte.
9. **Đọc text block như đúng số dấu cách trong source.** Text block đã có xử lý incidental indentation; nên xác minh giá trị String thực tế.
10. **Dùng n dương không giới hạn với input dài.** Có nguy cơ tăng kích thước dữ liệu và chi phí bộ nhớ rất lớn; ràng buộc độ dài và tham số theo bài toán.

## 12. Tự kiểm tra nhanh

Dự đoán kết quả rồi chạy các lệnh sau trong Java 17+:

~~~java
System.out.println("A".indent(0).equals("A\n"));
System.out.println("A\r\nB".indent(0).equals("A\nB\n"));
System.out.println("  A\n B".indent(-2).equals("A\nB\n"));
System.out.println("A\n\nB".indent(1).equals(" A\n \n B\n"));
System.out.println("\tX".indent(-1).equals("X\n"));
System.out.println("".indent(3).equals(""));
~~~

**Cả sáu dòng đều in true.** Nếu một kết quả gây bất ngờ, hãy xác định:

- Chuỗi gồm bao nhiêu dòng theo **lines()**?
- Mỗi dòng có bao nhiêu ký tự whitespace ở đầu?
- Phần đó bị **cộng n space** hay **trừ tối đa |n| whitespace**?
- Sau cùng, từng dòng có được nối bằng LF hay không?

## 13. Ghi nhớ

**String.indent(int n)** là thao tác **theo dòng**:

- **n dương:** thêm n dấu cách đầu **mọi dòng**, kể cả dòng trống.
- **n âm:** xóa tối đa |n| ký tự whitespace đầu từng dòng; tab được tính là một ký tự.
- **n bằng 0:** không đổi nội dung từng dòng, nhưng vẫn chuẩn hóa **CR/CRLF/LF → LF**.
- **Input rỗng:** output rỗng; **input không rỗng:** kết quả có LF sau dòng cuối.
- **Whitespace cuối dòng:** không bị xóa bởi **indent()**.

Khi lựa chọn API, hãy phân biệt rõ ba nhu cầu: **đổi lề theo số lượng xác định** (**indent**), **loại lề chung của khối** (**stripIndent**) và **chỉ xử lý đầu/cuối toàn bộ String** (**stripLeading/stripTrailing/strip**).

### Tài liệu tham khảo chính thức

- Java SE 17, **String.indent(int)**, **String.lines()** và **String.stripIndent()**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html
- Java SE 17, **Character.isWhitespace(int)**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-005">chars trả các đơn vị char mở rộng thành int</a>

<details>
<summary>Xem chi tiết</summary>

# String.chars(): các đơn vị char UTF-16 được mở rộng thành int

## 1. String.chars() là gì?

Trong Java, `String.chars()` tạo ra một `IntStream` lần lượt chứa **giá trị của từng đơn vị mã UTF-16 (`char`)** trong chuỗi. Mỗi giá trị `char` được **mở rộng thành `int` bằng zero-extension**: các bit phía trên được điền bằng 0, nên giá trị số giữ nguyên và nằm trong khoảng 0–65.535.

Chữ ký có thể nhìn từ `CharSequence` (Java 9+):

~~~java
IntStream chars()
~~~

Ví dụ đơn giản:

~~~java
String s = "ABC";
int[] units = s.chars().toArray();
// [65, 66, 67]
~~~

Kết quả là `int[]` bởi vì `chars()` trả về `IntStream`, dù từng giá trị đại diện cho một đơn vị `char`. Với chuỗi `"ABC"`, mã UTF-16 của `'A'`, `'B'` và `'C'` lần lượt là 65, 66, 67 nên ta thấy ba số nguyên đó.

**Điểm phải nhớ:** `chars()` không hứa trả về từng ký tự Unicode hoàn chỉnh (*Unicode code point*) và không tự giải mã cặp surrogate. Nó lần lượt lấy đúng từng đơn vị UTF-16 có trong chuỗi, chuyển giá trị của đơn vị đó từ `char` (16 bit không dấu) sang `int` (32 bit có dấu nhưng giá trị vẫn không âm).

## 2. Vì sao một char và một ký tự Unicode có thể khác nhau?

Java biểu diễn `String` bằng một dãy đơn vị mã UTF-16. Một `char` có độ rộng 16 bit, nhưng tập mã Unicode có các code point lớn hơn phạm vi 16 bit.

- Các code point thuộc **BMP** (Basic Multilingual Plane), ngoại trừ vùng dành riêng cho surrogate, được biểu diễn bằng một đơn vị UTF-16. Ví dụ chữ `A` (`U+0041`) và chữ `á` (`U+00E1`).
- Các code point **ngoài BMP**, từ `U+10000` đến `U+10FFFF`, cần **hai đơn vị UTF-16**: một *high surrogate* thuộc `U+D800..U+DBFF` và một *low surrogate* thuộc `U+DC00..U+DFFF`.
- Một đơn vị surrogate riêng lẻ **không phải** Unicode scalar value hoàn chỉnh. Trong `String` vẫn có thể tồn tại surrogate lẻ; `chars()` sẽ phát ra nguyên giá trị đơn vị đó.

Ví dụ chuỗi hiển thị `A😀B`, trong đó emoji `😀` có code point `U+1F600`:

| Vị trí `char` (chỉ số UTF-16) | Giá trị hex của đơn vị UTF-16 | Giá trị thập phân `int` | Ý nghĩa |
|---|---|---|---|
| 0 | `0041` | 65 | `A` |
| 1 | `D83D` | 55.357 | High surrogate của `😀` |
| 2 | `DE00` | 56.832 | Low surrogate của `😀` |
| 3 | `0042` | 66 | `B` |

Do đó:

~~~java
String s = "A\uD83D\uDE00B"; // A😀B
System.out.println(s.length());        // 4 đơn vị UTF-16
System.out.println(s.chars().count()); // 4 phần tử int
System.out.println(s.codePoints().count()); // 3 code point
~~~

Số phần tử của `chars()` bằng số đơn vị UTF-16 trong `String`, tức bằng `s.length()`. Đừng suy ra “4 ký tự người đọc nhìn thấy” từ kết quả đó: cách đếm đơn vị UTF-16, Unicode code point và ký tự hiển thị trên giao diện có thể cho các kết quả khác nhau.

## 3. Cơ chế mở rộng char thành int

`char` là kiểu nguyên thủy 16 bit **không dấu**. Khi mở rộng `char` thành `int`, Java giữ 16 bit thấp và đặt các bit còn lại bằng 0 (*zero-extension*); đây không phải chuyển đổi có dấu (*sign-extension*).

~~~java
char ch = '\u00E1';           // á, mã U+00E1
int asInt = ch;               // 225
int viaStream = "á".chars().findFirst().orElseThrow();
// viaStream == 225
~~~

Với `'A'`, phép chuyển đổi có thể hình dung như:

~~~text
char: 0000 0000 0100 0001   (0x0041, 65)
int : 0000 0000 0000 0000 0000 0000 0100 0001   (0x00000041, 65)
~~~

Với một đơn vị surrogate có bit cao bật, `int` sau khi mở rộng **vẫn là số dương**, ví dụ `0xD83D` thành `55357`; không trở thành một số nguyên âm.

Vì đây là mở rộng giá trị của một đơn vị `char`, **nó không biến hai `char` của emoji thành một code point**. Muốn kết hợp đúng một cặp surrogate hợp lệ thành code point, cần cơ chế đọc code point như `codePoints()` hoặc API `Character`.

## 4. So sánh chars(), codePoints(), charAt() và length()

| API | Kết quả | Đơn vị xử lý | Trường hợp phù hợp |
|---|---|---|---|
| `s.chars()` | `IntStream` | Từng đơn vị UTF-16, mở rộng thành `int` | Kiểm tra/xử lý trực tiếp các code unit; thao tác luồng với text ASCII/BMP theo yêu cầu đã rõ |
| `s.codePoints()` | `IntStream` | Mỗi cặp surrogate hợp lệ được kết hợp thành một code point | Xử lý Unicode code point, đặc biệt khi có chữ hoặc emoji ngoài BMP |
| `s.charAt(i)` | `char` | Một đơn vị UTF-16 ở chỉ số `i` | Truy cập trực tiếp code unit theo chỉ số |
| `s.length()` | `int` | Số đơn vị UTF-16 | Kiểm tra độ dài lưu trữ logic của chuỗi theo `char` |
| `s.codePointCount(0, s.length())` | `int` | Số code point trong đoạn UTF-16 chỉ định | Đếm code point, tính cặp surrogate hợp lệ là một |

Đối với `"A\uD83D\uDE00B"`:

~~~text
UTF-16 code units:  0041 | D83D | DE00 | 0042   → 4 phần tử
Unicode code points: U+0041 | U+1F600 | U+0042  → 3 phần tử
~~~

`chars()` và `codePoints()` cùng trả `IntStream` nhưng giá trị của các phần tử có **ngữ nghĩa khác nhau**. Kiểu `int` tự nó không nói rõ đang chứa code unit hay code point; tên biến và API nguồn giúp tránh nhầm lẫn.

Lưu ý đặc biệt: `codePoints()` chỉ kết hợp **cặp high/low surrogate hợp lệ**. Nếu chuỗi chứa một surrogate không ghép cặp, phương thức có thể phát ra giá trị surrogate đó dưới dạng một `int` riêng; nó không tự làm sạch dữ liệu lỗi hay thay bằng ký tự thay thế `U+FFFD`.

## 5. Ví dụ Java 17+ chạy hoàn chỉnh

Tạo tệp **`StringCharsDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class StringCharsDemo {
    public static void main(String[] args) {
        // A + 😀 + B. Emoji U+1F600 được mã hóa bằng hai char UTF-16.
        String text = "A\uD83D\uDE00B";

        System.out.println("length = " + text.length());
        System.out.println("chars count = " + text.chars().count());
        System.out.println("chars decimal = "
                + Arrays.toString(text.chars().toArray()));

        String unitsHex = text.chars()
                .mapToObj(unit -> String.format(Locale.ROOT, "%04X", unit))
                .collect(Collectors.joining(" "));
        System.out.println("chars hex = " + unitsHex);

        String pointsHex = text.codePoints()
                .mapToObj(point -> String.format(Locale.ROOT, "U+%04X", point))
                .collect(Collectors.joining(" "));
        System.out.println("code points hex = " + pointsHex);

        System.out.println("code point count = "
                + text.codePoints().count());

        System.out.println("high surrogate = "
                + Character.isHighSurrogate(text.charAt(1)));
        System.out.println("low surrogate = "
                + Character.isLowSurrogate(text.charAt(2)));

        long countA = "banana".chars()
                .filter(unit -> unit == 'a')
                .count();
        System.out.println("count a = " + countA);

        String reconstructed = text.codePoints()
                .collect(
                        StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append
                )
                .toString();
        System.out.println("round trip = " + text.equals(reconstructed));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StringCharsDemo.java
java StringCharsDemo
~~~

Kết quả:

~~~text
length = 4
chars count = 4
chars decimal = [65, 55357, 56832, 66]
chars hex = 0041 D83D DE00 0042
code points hex = U+0041 U+1F600 U+0042
code point count = 3
high surrogate = true
low surrogate = true
count a = 3
round trip = true
~~~

Giải thích:

1. `length()` và `chars().count()` đều là **4** vì emoji chiếm hai code unit.
2. `chars().toArray()` tạo **`int[]`**: `[65, 55357, 56832, 66]`. Hai số giữa biểu diễn hai nửa UTF-16 của emoji.
3. `mapToObj(...)` chuyển từng số thành chuỗi hex; `Collectors.joining(" ")` ghép các phần thành một dòng dễ đọc.
4. `codePoints()` kết hợp hai surrogate hợp lệ thành `U+1F600`, nên chỉ có **3** code point.
5. `Character.isHighSurrogate` và `Character.isLowSurrogate` xác minh hai đơn vị ở chỉ số 1 và 2.
6. Bộ lọc `unit == 'a'` có kết quả 3 với `banana`, vì chữ `a` thuộc BMP và được biểu diễn bằng đúng một đơn vị UTF-16.
7. `StringBuilder.appendCodePoint` bổ sung code point trở lại chuỗi UTF-16 hợp lệ; vì dữ liệu đầu vào có cặp surrogate hợp lệ, phép tạo lại cho chuỗi đúng bằng ban đầu.

Trong code, `StringBuilder::appendCodePoint` được dùng với `IntStream.collect` để cộng dồn code point, còn `StringBuilder::append` dùng để kết hợp các bộ đệm khi cần.

## 6. Những thao tác trực tiếp với IntStream

### 6.1. Duyệt các đơn vị char

~~~java
String s = "ABC";
s.chars().forEachOrdered(unit -> System.out.println(unit));
// 65
// 66
// 67
~~~

Vì nguồn có thứ tự, `forEachOrdered` bảo toàn thứ tự duyệt, kể cả khi chuyển stream sang chế độ song song. `forEach` trên stream song song không bảo đảm thứ tự thực hiện giữa các phần tử.

### 6.2. Lọc và đếm

~~~java
long digits = "a1b23".chars()
        .filter(unit -> unit >= '0' && unit <= '9')
        .count();
// digits == 3
~~~

Mẫu này chủ động kiểm tra **chữ số ASCII** `'0'` đến `'9'`. Nếu cần hỗ trợ chữ số thuộc nhiều hệ chữ Unicode, hãy cân nhắc `codePoints().filter(Character::isDigit)` theo quy tắc nghiệp vụ; không đồng nhất “ASCII digit” với “mọi ký tự được Unicode xem là chữ số”.

### 6.3. Tạo danh sách giá trị nguyên hoặc tổng hợp

~~~java
int[] values = "ABC".chars().toArray();
// [65, 66, 67]

int total = "ABC".chars().sum();
// 198
~~~

`sum()` chỉ cộng giá trị số của các đơn vị UTF-16; nó **không tạo một số đo ý nghĩa văn bản**. Các phép tính số học trên code unit nên được sử dụng khi bài toán thực sự yêu cầu những giá trị đó, ví dụ kiểm tra mã ký tự trong phạm vi ASCII.

### 6.4. Chuyển giá trị code unit thành chuỗi để hiển thị

~~~java
String hex = "Az".chars()
        .mapToObj(unit -> String.format(Locale.ROOT, "%04X", unit))
        .collect(Collectors.joining(", "));
// "0041, 007A"
~~~

`mapToObj` chuyển `IntStream` thành `Stream<String>`; sau đó `Collectors.joining` ghép các chuỗi. Cách này phù hợp để hiển thị **giá trị code unit**. Nếu muốn hiện từng code point Unicode thành ký tự tương ứng, hãy dùng `codePoints()` và chuyển code point thành chuỗi bằng `Character.toChars(cp)`.

Các stream là **một lần sử dụng**: sau khi đã gọi terminal operation như `count()`, `toArray()` hoặc `forEachOrdered()`, không dùng lại cùng đối tượng stream. Nếu muốn chạy phép tính khác, gọi `s.chars()` lần nữa.

## 7. Vì sao không nên ép mọi int từ chars() thành “một ký tự Unicode”?

Đối với ASCII/BMP thông thường, đoạn sau có vẻ hoạt động:

~~~java
String value = "ABC";
value.chars().forEachOrdered(unit -> {
    char c = (char) unit;
    System.out.print(c);
});
// ABC
~~~

Nhưng với dữ liệu chứa emoji ngoài BMP, mỗi lần ép `(char) unit` vẫn chỉ tạo **một nửa surrogate**. Việc xử lý, kiểm tra, xóa hoặc phát từng nửa như một ký tự độc lập có thể làm hỏng cặp surrogate:

~~~java
String emoji = "\uD83D\uDE00"; // 😀
int[] units = emoji.chars().toArray();
// [55357, 56832]

// units[0] là high surrogate; nó không phải emoji hoàn chỉnh.
boolean high = Character.isHighSurrogate((char) units[0]); // true
~~~

Nếu cần đi qua từng Unicode code point và chuyển trở lại chuỗi, hãy dùng:

~~~java
String source = "A\uD83D\uDE00B";
String joined = source.codePoints()
        .mapToObj(cp -> new String(Character.toChars(cp)))
        .collect(Collectors.joining());
// joined.equals(source) == true, với nguồn có code point hợp lệ
~~~

`Character.toChars(cp)` chuyển một code point hợp lệ thành một hoặc hai `char` UTF-16 đúng quy tắc. Với surrogate lẻ do `codePoints()` phát ra, `Character.toChars(cp)` vẫn có thể trả về chính một đơn vị surrogate vì giá trị này nằm trong phạm vi code point mà API chấp nhận; nó **không** xác thực hay sửa Unicode lỗi. Nếu đầu ra phải gồm các Unicode scalar value hợp lệ, hãy phát hiện rồi từ chối hoặc thay thế surrogate lẻ theo hợp đồng ứng dụng.

Nếu ứng dụng cần cắt chuỗi theo vị trí code point thay vì vị trí UTF-16, đừng tự tăng chỉ số `char` đúng 1 sau mọi phần tử. Có thể sử dụng `offsetByCodePoints(...)` để chuyển chỉ số code point thành chỉ số UTF-16:

~~~java
String s = "A\uD83D\uDE00B";
int from = s.offsetByCodePoints(0, 1); // chỉ số UTF-16: 1
int to = s.offsetByCodePoints(0, 2);   // chỉ số UTF-16: 3
String secondCodePoint = s.substring(from, to); // 😀
~~~

Trong ví dụ trên, `s.substring(1, 2)` chỉ lấy high surrogate và tạo chuỗi có surrogate lẻ; muốn lấy trọn emoji phải lấy đoạn UTF-16 `[1,3)`.

## 8. Đếm code unit, code point và ký tự người dùng thấy

Ba cách đếm thường bị lẫn:

| Khái niệm | Đếm gì? | Công cụ liên quan |
|---|---|---|
| Đơn vị UTF-16 (code unit) | Từng `char` 16 bit | `length()`, `chars().count()` |
| Unicode code point | Mỗi mã điểm; cặp surrogate hợp lệ tính là một | `codePoints().count()`, `codePointCount(...)` |
| Grapheme cluster (*ký tự cảm nhận*) | Nhóm code point hiển thị như một đơn vị người dùng nhận thấy | Bộ phân đoạn văn bản Unicode phù hợp, ví dụ API phân tách theo grapheme cluster |

Một chữ có dấu có thể được mã hóa bằng **một code point dựng sẵn** hoặc **chữ nền cộng dấu kết hợp**; hai cách có thể nhìn gần giống nhau nhưng đếm số code point khác nhau. Emoji có modifier, dấu nối zero-width joiner hoặc cờ quốc gia cũng có thể gồm nhiều code point nhưng hiển thị như một hình.

Vì vậy:

- `chars().count()` **không** phải luôn là số ký tự Unicode.
- `codePoints().count()` cũng **không** chắc bằng số ký tự người dùng nhìn thấy.
- Khi xây dựng giao diện (giới hạn số ký tự người dùng nhập, cắt tên hiển thị, chọn vị trí con trỏ), phải định nghĩa rõ đơn vị cần đếm và chọn bộ phân đoạn tương ứng.

Đây là hệ quả trực tiếp của cách Java dùng UTF-16 trong `String`, chứ không phải lỗi riêng của `chars()`.

## 9. Một số lưu ý về API và hiệu năng

**`IntStream` là primitive stream.** Từ `chars()` nhận được `IntStream`, không phải `Stream<Character>`. Nếu cần `int[]`, gọi `toArray()`; nếu cần stream của đối tượng, dùng `mapToObj` hoặc `boxed()` khi thực sự cần. Việc boxing thành `Integer` có thể tạo thêm chi phí so với duy trì kiểu nguyên thủy.

**`chars()` không thay đổi `String`.** `String` là bất biến; `filter`, `map`, `count` chỉ tạo pipeline hoặc tính kết quả. Các phép trung gian như `filter()` có tính lười (*lazy*), thường chỉ được thực thi khi gọi terminal operation như `count()`.

**Tránh tạo stream nếu mục tiêu là đọc một đơn vị ở chỉ số cố định.** `s.charAt(i)` rõ ràng hơn cho phép đọc một code unit. Với các tác vụ duyệt đơn giản hoặc cần phối hợp chỉ số UTF-16, vòng lặp `for (int i = 0; i < s.length(); i++)` có thể dễ kiểm soát hơn; lựa chọn nào nhanh hơn phải đo trong ngữ cảnh thực tế.

**Không giả định mỗi int chứa một “ký tự đầy đủ”.** Đây là vấn đề đúng/sai của Unicode, không chỉ hiệu năng. Việc dùng `codePoints()` từ đầu giúp tránh vô tình tách đôi ký tự ngoài BMP.

**Không dùng stream đã kết thúc.** Ví dụ:

~~~java
var stream = "ABC".chars();
long size = stream.count();  // 3
// stream.sum(); // Sai: stream đã được tiêu thụ
int sum = "ABC".chars().sum(); // Tạo stream mới, kết quả 198
~~~

**Lưu ý chuỗi rỗng và null.** `"".chars()` là stream rỗng, nên `count()` trả 0 và `toArray()` trả mảng rỗng. Gọi `chars()` trên tham chiếu `String` bằng `null` sẽ gây `NullPointerException`; nếu đầu vào có thể null, hãy quyết định cách xử lý trước khi tạo stream.

## 10. Sai lầm phổ biến và cách sửa

1. **Đọc `chars()` là “stream các code point”.** Không đúng: đầu ra là các code unit UTF-16 đã nâng kiểu lên `int`. Với ký tự ngoài BMP, số phần tử sẽ lớn hơn số code point. **Sửa:** dùng `codePoints()` nếu yêu cầu là Unicode code point.
2. **Nghĩ `toArray()` sẽ tạo `char[]`.** `IntStream.toArray()` trả `int[]`. Nếu cần mảng `char[]` chứa các code unit, dùng `s.toCharArray()`.
3. **Ép `(char)` cho một int rồi giả định đó là một ký tự người dùng nhìn thấy.** Một surrogate có thể chỉ là nửa của code point. **Sửa:** xử lý theo cặp hoặc dùng `codePoints()` và `Character.toChars()`.
4. **Sử dụng `length()` để giới hạn “số ký tự nhìn thấy”.** `length()` đếm các code unit UTF-16. **Sửa:** xác định yêu cầu là code unit, code point hay grapheme cluster trước.
5. **Cắt chuỗi theo chỉ số UTF-16 ngay giữa cặp surrogate.** Điều này tạo chuỗi chứa surrogate lẻ. **Sửa:** xác định ranh giới code point với `offsetByCodePoints()` hoặc API phù hợp.
6. **Tái sử dụng `IntStream` sau terminal operation.** Stream chỉ được tiêu thụ một lần. **Sửa:** gọi lại `s.chars()` hoặc lưu kết quả cần tái sử dụng.
7. **Dùng một phép kiểm tra ASCII để xử lý mọi hệ chữ.** Ví dụ so sánh `'0' <= unit && unit <= '9'` chỉ nhận chữ số ASCII. **Sửa:** dùng thuật toán theo code point và quy tắc Unicode khi thực sự muốn hỗ trợ nhiều hệ chữ.
8. **Cho rằng `codePoints()` tự sửa surrogate lẻ hoặc tính luôn grapheme cluster.** Cả hai đều sai. **Sửa:** kiểm tra dữ liệu Unicode không hợp lệ và chọn bộ phân đoạn grapheme khi yêu cầu liên quan ký tự hiển thị.

## 11. Tự kiểm tra nhanh

**Câu 1.** `"Java".chars().count()` trả bao nhiêu? — **4**, vì mỗi chữ Latin ở đây chiếm một đơn vị UTF-16.

**Câu 2.** Với `String s = "\uD83D\uDE00";`, `s.chars().count()` và `s.codePoints().count()` trả bao nhiêu? — Lần lượt **2** và **1**: emoji dùng một cặp surrogate nhưng là một code point.

**Câu 3.** `"A".chars().toArray()` cho kiểu dữ liệu nào? — **`int[]`**, chứa giá trị `65`, bởi nguồn là `IntStream`.

**Câu 4.** Nếu cần in mã điểm Unicode `U+1F600` của emoji, nên bắt đầu từ API nào? — **`codePoints()`**; `chars()` phát ra riêng `D83D` và `DE00`.

**Câu 5.** `String.chars()` có tự đổi chuỗi gốc hay trả từng `Character` object không? — **Không** ở cả hai ý: chuỗi là bất biến và luồng chứa giá trị `int` nguyên thủy.

## 12. Ghi nhớ

`String.chars()` là công cụ duyệt **đơn vị `char` UTF-16**, sau khi **zero-extend** từng giá trị thành `int` trong `IntStream`. Phương thức phù hợp khi bài toán muốn thao tác ở mức code unit, hoặc trên dữ liệu ASCII/BMP với giả định được làm rõ. Khi bài toán cần xử lý **code point Unicode**, nhất là văn bản có emoji hay ký tự ngoài BMP, hãy chọn `String.codePoints()`. Khi bài toán cần “một ký tự mà người dùng nhìn thấy”, cả hai cách đếm trên có thể chưa đủ và cần xử lý grapheme cluster.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-006">codePoints kết hợp surrogate pair thành code point</a>

<details>
<summary>Xem chi tiết</summary>

# `String.codePoints()` kết hợp surrogate pair thành code point trong Java

## 1. Vấn đề: một ký tự Unicode có thể chiếm hai `char`

Trong Java, kiểu nguyên thủy `char` có **16 bit** và một `String` được lưu theo các **UTF-16 code unit**. Với ký tự thuộc Basic Multilingual Plane (BMP), ví dụ `A` (U+0041), một UTF-16 code unit thường đủ biểu diễn ký tự. Nhưng các code point ngoài BMP, như emoji `😃` (U+1F603), cần **hai UTF-16 code unit** liên tiếp, gọi là **surrogate pair**.

Ví dụ:

~~~java
String text = "A😃B";
System.out.println(text.length());             // 4 code unit UTF-16
System.out.println(text.codePoints().count()); // 3 code point
~~~

Ta **nhìn thấy ba ký hiệu** `A`, `😃`, `B`, nhưng `length()` trả về `4` vì `😃` chiếm **hai `char`**. Đây là lý do không thể mặc định `charAt(i)` trả về trọn vẹn một code point Unicode.

**Điểm chính:** `String.codePoints()` tạo `IntStream` gồm các Unicode code point được đọc từ chuỗi. Nếu nó gặp một **high surrogate** ngay trước một **low surrogate**, nó kết hợp hai code unit ấy thành **một giá trị `int`** trong stream. Các code unit không tạo thành cặp hợp lệ được đưa ra riêng lẻ.

## 2. Ba khái niệm dễ nhầm

| Khái niệm | Trong Java | Ví dụ với `A😃B` |
| --- | --- | --- |
| UTF-16 **code unit** | `char` 16 bit, được `length()`, `charAt()`, `chars()` quan sát | 4 đơn vị: U+0041, U+D83D, U+DE03, U+0042 |
| Unicode **code point** | Số nguyên biểu thị một mã Unicode, thường xử lý bằng `int` | 3 mã: U+0041, U+1F603, U+0042 |
| **Grapheme cluster** (cụm ký tự người dùng nhìn thấy) | Khái niệm phân đoạn văn bản Unicode, không tương đương `char` hay code point | Có thể chứa nhiều code point dù hiển thị như một ký tự |

Không phải mọi code point đều là một ký tự hiển thị độc lập. Ví dụ `e` + dấu sắc kết hợp `\u0301` là **hai code point** có thể cùng tạo nên một chữ `é` trên màn hình. Emoji có biến thể màu da, variation selector hoặc chuỗi Zero Width Joiner (ZWJ) cũng có thể gồm nhiều code point nhưng hiển thị thành một biểu tượng.

`codePoints()` giúp đọc **code point**, **không** tự ghép các code point thành grapheme cluster và không tự chuẩn hóa Unicode.

## 3. Surrogate pair là gì?

Trong UTF-16, các code point từ U+10000 đến U+10FFFF được mã hóa bằng **hai `char`**:

- **High surrogate**: giá trị trong khoảng U+D800–U+DBFF.
- **Low surrogate**: giá trị trong khoảng U+DC00–U+DFFF.

Hai giá trị phải xuất hiện **theo thứ tự high rồi low và nằm cạnh nhau** mới tạo nên surrogate pair hợp lệ. Công thức ghép:

~~~text
codePoint = 0x10000
          + ((high - 0xD800) << 10)
          +  (low  - 0xDC00)
~~~

Với `😃` (U+1F603), UTF-16 chứa:

~~~text
High surrogate: U+D83D
Low surrogate : U+DE03
Kết hợp       : U+1F603
~~~

Java cung cấp sẵn các hàm `Character.isHighSurrogate(char)`, `Character.isLowSurrogate(char)`, `Character.isSurrogatePair(char, char)`, `Character.toCodePoint(high, low)` và `Character.toChars(int)`. Nên dùng API chuẩn thay vì tự viết công thức ghép trong code ứng dụng.

Ví dụ minh họa riêng (các lệnh đặt trong phương thức `main`):

~~~java
String emoji = "😃";
char high = emoji.charAt(0);
char low = emoji.charAt(1);

System.out.println(Character.isHighSurrogate(high));   // true
System.out.println(Character.isLowSurrogate(low));     // true
System.out.println(Character.isSurrogatePair(high, low)); // true
System.out.println(Character.toCodePoint(high, low) == 0x1F603); // true
~~~

**Lưu ý:** một code point trong BMP (ví dụ chữ `A`) được biểu diễn bởi một code unit; riêng vùng U+D800–U+DFFF được dành cho surrogate nên không phải các Unicode scalar value hợp lệ khi đứng lẻ.

## 4. So sánh `chars()` và `codePoints()`

Cả hai trả về `IntStream`, nhưng **ý nghĩa mỗi `int`** khác nhau.

### `String.chars()`

`chars()` xuất ra từng UTF-16 code unit dưới dạng `int` bằng cách mở rộng giá trị `char` thành số nguyên không dấu. Nó **không ghép** surrogate pair.

~~~java
"A😃B".chars()
       .mapToObj(cp -> String.format("U+%04X", cp))
       .forEach(System.out::println);
~~~

Kết quả:

~~~text
U+0041
U+D83D
U+DE03
U+0042
~~~

### `String.codePoints()`

`codePoints()` xét các phần tử `char` liên tiếp. Nếu gặp high surrogate và low surrogate liền nhau, nó phát ra **một `int` code point bổ sung**; còn lại phát ra giá trị code unit riêng lẻ.

~~~java
"A😃B".codePoints()
       .mapToObj(cp -> String.format("U+%04X", cp))
       .forEach(System.out::println);
~~~

Kết quả:

~~~text
U+0041
U+1F603
U+0042
~~~

| Phương thức | Số phần tử với `A😃B` | Đơn vị được duyệt |
| --- | ---: | --- |
| `text.length()` | 4 | Độ dài theo UTF-16 code unit |
| `text.chars().count()` | 4 | Mỗi `int` là một code unit |
| `text.codePoints().count()` | 3 | Mỗi `int` là một code point được giải mã |
| `text.codePointCount(0, text.length())` | 3 | Đếm code point trong một vùng chỉ số UTF-16 |

`String.codePoints()` có từ Java 9, còn nội dung ở đây dùng Java 17+ để thống nhất ví dụ. `IntStream` xử lý code point theo thứ tự của chuỗi khi duyệt tuần tự.

## 5. Ví dụ Java 17+ đầy đủ, chạy được

Lưu tệp thành `CodePointsDemo.java` (mã nguồn UTF-8):

~~~java
import java.util.stream.Collectors;

public class CodePointsDemo {
    private static String hex(int value) {
        return String.format("U+%04X", value);
    }

    public static void main(String[] args) {
        String text = "A😃B";

        System.out.println("length=" + text.length());
        System.out.println("chars=" + text.chars().count());
        System.out.println("codePoints=" + text.codePoints().count());

        System.out.println("units=" +
                text.chars()
                    .mapToObj(CodePointsDemo::hex)
                    .collect(Collectors.joining(", ")));

        System.out.println("points=" +
                text.codePoints()
                    .mapToObj(CodePointsDemo::hex)
                    .collect(Collectors.joining(", ")));

        System.out.println("charAt1=" + hex(text.charAt(1)));
        System.out.println("pointAt1=" + hex(text.codePointAt(1)));
        System.out.println("before3=" + hex(text.codePointBefore(3)));
        System.out.println("offsetAfter2=" +
                text.offsetByCodePoints(0, 2));

        System.out.println("supplementary=" +
                text.codePoints()
                    .filter(Character::isSupplementaryCodePoint)
                    .count());

        String unpaired = new String(new char[] {
                '\uD83D', 'X', '\uDE03'
        });
        System.out.println("unpaired=" +
                unpaired.codePoints()
                        .mapToObj(CodePointsDemo::hex)
                        .collect(Collectors.joining(", ")));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 CodePointsDemo.java
java CodePointsDemo
~~~

Kết quả chuẩn:

~~~text
length=4
chars=4
codePoints=3
units=U+0041, U+D83D, U+DE03, U+0042
points=U+0041, U+1F603, U+0042
charAt1=U+D83D
pointAt1=U+1F603
before3=U+1F603
offsetAfter2=3
supplementary=1
unpaired=U+D83D, U+0058, U+DE03
~~~

Diễn giải:

1. `charAt(1)` trả về **high surrogate** U+D83D, chỉ là **một nửa** của emoji.
2. `codePointAt(1)` nhìn thêm low surrogate ở vị trí 2, trả về code point U+1F603.
3. `codePointBefore(3)` đọc về phía trước chỉ số 3, kết hợp hai code unit ở vị trí 1 và 2 thành U+1F603.
4. `offsetByCodePoints(0, 2)` đi qua **hai code point** kể từ chỉ số UTF-16 0 nên trả về chỉ số UTF-16 `3`, tức vị trí chữ `B`.
5. `filter(Character::isSupplementaryCodePoint)` đếm được một code point ngoài BMP.
6. Ví dụ `unpaired` cố ý đặt high surrogate, chữ `X`, low surrogate **không kề nhau**. `codePoints()` không ghép chúng thành một code point; nó xuất lần lượt U+D83D, U+0058, U+DE03.

Các dòng hiển thị code point theo hệ thập lục phân để phân biệt rõ mã Unicode với ký tự xuất hiện trên terminal.

## 6. Cách duyệt và xử lý code point đúng

### 6.1. Duyệt bằng stream

Nếu chỉ quan tâm các code point thực tế, dùng `codePoints()`:

~~~java
String s = "A😃B";
s.codePoints().forEachOrdered(cp -> {
    System.out.println(String.format("U+%04X", cp));
});
~~~

Kết quả:

~~~text
U+0041
U+1F603
U+0042
~~~

Phương thức `codePoints()` là **nguồn dữ liệu** cho pipeline `IntStream`. Có thể dùng `filter`, `map`, `distinct`, `count` hoặc `toArray` như với các primitive stream khác. Stream không tự biến thành danh sách `String` một ký tự.

Ví dụ lọc các code point ngoài BMP:

~~~java
long count = "A😃B".codePoints()
        .filter(Character::isSupplementaryCodePoint)
        .count();
System.out.println(count); // 1
~~~

Lưu ý các phương thức `Character.isLetter(int)`, `Character.isDigit(int)`, `Character.getType(int)` nhận `int codePoint`, phù hợp khi kiểm tra đặc tính Unicode của từng code point.

### 6.2. Khôi phục `String` từ các code point

Không chuyển code point bổ sung thành một `char` duy nhất bằng ép kiểu, vì sẽ làm mất thông tin:

~~~java
int smile = 0x1F603;
// char broken = (char) smile; // Ép kiểu làm mất các bit cao: không phải 😃.
String restored = new String(Character.toChars(smile));
System.out.println(restored.codePointAt(0) == smile); // true
~~~

`Character.toChars(int)` chuyển code point ngoài BMP thành cặp surrogate. Khi ghép nhiều code point vào chuỗi, dùng `StringBuilder.appendCodePoint(int)`:

~~~java
int[] points = "A😃B".codePoints().toArray();
StringBuilder builder = new StringBuilder();

for (int point : points) {
    builder.appendCodePoint(point);
}

System.out.println(builder.toString().equals("A😃B")); // true
~~~

Phân biệt `append((char) point)` và `appendCodePoint(point)`: cách thứ nhất chỉ thêm **một code unit**, còn cách thứ hai thêm đúng một hoặc hai UTF-16 code unit tùy code point.

### 6.3. Chỉ số UTF-16 và chỉ số code point không giống nhau

Các API `charAt`, `substring`, `indexOf`, `codePointAt` đều nhận/chia sẻ **chỉ số UTF-16** của `String`, không tự hiểu đó là vị trí code point thứ mấy.

~~~java
String s = "A😃B";

// Bắt đầu từ UTF-16 index 1, bỏ qua đúng một code point (emoji).
int afterEmoji = s.offsetByCodePoints(1, 1);
System.out.println(afterEmoji);        // 3
System.out.println(s.charAt(afterEmoji)); // B
~~~

Nếu cần lấy phần tử Unicode thứ `k` (đếm từ 0), dùng `offsetByCodePoints(0, k)` để tìm vị trí UTF-16, rồi `codePointAt(index)` để lấy code point. Bảo đảm `k` nằm trong phạm vi trước khi truy cập; các phương thức này có thể ném `IndexOutOfBoundsException` khi chỉ số không hợp lệ.

Ví dụ lấy **đúng một code point** dưới dạng `String`:

~~~java
String text = "A😃B";
int start = text.offsetByCodePoints(0, 1);
int end = text.offsetByCodePoints(start, 1);
String emoji = text.substring(start, end);

System.out.println(emoji.codePoints().count()); // 1
~~~

`substring(1, 2)` sẽ chỉ lấy high surrogate, còn `substring(1, 3)` lấy đủ hai code unit cho emoji trong ví dụ này.

## 7. Surrogate lẻ: giới hạn quan trọng của `codePoints()`

Một `String` Java có thể chứa dữ liệu UTF-16 **không ghép cặp hợp lệ**, chẳng hạn một high surrogate không có low surrogate ngay sau hoặc một low surrogate đứng lẻ.

~~~java
String invalid = new String(new char[]{'\uD83D', 'X'});
System.out.println(invalid.length());            // 2
System.out.println(invalid.codePoints().count()); // 2
~~~

Java **không âm thầm xóa**, ghép sai vị trí hoặc tự sửa các surrogate đứng lẻ khi chạy `codePoints()`. Theo hợp đồng của `String.codePoints()`, một surrogate **không ghép cặp** được xuất ra như một giá trị `int` bằng cách mở rộng chính code unit của nó (ví dụ U+D83D).

Điều cần phân biệt:

- Một surrogate lẻ có thể trở thành một phần tử trong `IntStream` do `codePoints()` trả về.
- Giá trị từ U+D800 đến U+DFFF **không phải Unicode scalar value hợp lệ** khi đứng riêng, dù Java vẫn cho phép `String` chứa chúng.
- Vì thế không nên xem việc dùng `codePoints()` là bước **xác thực UTF-16** hoặc sửa dữ liệu Unicode không hợp lệ.
- Nếu đầu vào có thể lỗi, cần quyết định rõ chính sách: phát hiện/từ chối, thay thế bằng ký tự thay thế U+FFFD hoặc áp dụng chính sách giải mã của thư viện phù hợp. Việc chuyển sang byte UTF-8 còn có thể áp dụng chính sách thay thế ký tự không hợp lệ riêng.

Có thể kiểm tra surrogate lẻ khi duyệt `codePoints()`:

~~~java
String invalid = new String(new char[]{'\uD83D'});
boolean hasUnpairedSurrogate = invalid.codePoints()
        .anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF);
System.out.println(hasUnpairedSurrogate); // true
~~~

Khi chỉ gặp cặp high–low hợp lệ, `codePoints()` trả về mã ngoài BMP của chúng, không xuất riêng hai giá trị surrogate.

## 8. Code point khác với một ký tự người dùng nhìn thấy

`codePoints()` giải quyết vấn đề **surrogate pair của UTF-16**, nhưng không giải quyết mọi vấn đề về việc đếm “ký tự”.

Ví dụ:

~~~java
String composed = "é";       // U+00E9
String decomposed = "e\u0301"; // U+0065 rồi U+0301

System.out.println(composed.codePoints().count());   // 1
System.out.println(decomposed.codePoints().count()); // 2
~~~

Hai chuỗi có thể trông giống nhau, nhưng biểu diễn Unicode khác nhau. Nếu ứng dụng cần chuẩn hóa tương đương, có thể cân nhắc `java.text.Normalizer` (ví dụ NFC) trước khi so sánh theo quy tắc nghiệp vụ; `codePoints()` **không tự chuẩn hóa**.

Một emoji gồm nhiều thành phần hoặc một lá cờ tạo bởi các regional indicator cũng có thể có **nhiều code point** nhưng hiển thị như một biểu tượng. Với tác vụ di chuyển con trỏ, xóa một ký tự người dùng nhìn thấy, hoặc giới hạn độ dài theo **grapheme cluster**, cần công cụ phân đoạn Unicode phù hợp, ví dụ thư viện hỗ trợ Unicode grapheme boundaries. Đừng lấy `codePoints().count()` làm số grapheme cluster.

## 9. Lỗi phổ biến và cách tránh

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `length()` là số code point | `length()` đếm UTF-16 code unit | `codePointCount(0, length())` hoặc `codePoints().count()` |
| `charAt(i)` luôn lấy trọn ký tự Unicode | Có thể trả về nửa surrogate pair | `codePointAt(i)` khi `i` là UTF-16 index hợp lệ |
| `chars()` và `codePoints()` giống nhau | `chars()` tách từng code unit | `codePoints()` ghép surrogate pair hợp lệ |
| Ép `(char) cp` để dựng lại emoji | `char` không đủ chứa code point ngoài BMP | `Character.toChars(cp)` hoặc `appendCodePoint(cp)` |
| Dùng code point index với `substring` | `substring` nhận UTF-16 index | Quy đổi bằng `offsetByCodePoints` |
| `codePoints()` sửa mọi surrogate lỗi | Nó vẫn phát ra surrogate đứng lẻ | Xác thực hoặc xử lý lỗi riêng |
| Mỗi code point là một grapheme | Ký tự kết hợp và emoji phức hợp có nhiều code point | Phân đoạn grapheme phù hợp nhu cầu |

### Checklist tự kiểm tra

- Với `A😃B`: `length() == 4` và `codePoints().count() == 3`.
- `chars()` xuất U+D83D, U+DE03 riêng; `codePoints()` xuất U+1F603.
- `codePointAt(1)` trả về U+1F603; `charAt(1)` chỉ trả về U+D83D.
- `offsetByCodePoints(0, 2)` trả về UTF-16 index `3`.
- High và low surrogate bị ngăn bởi ký tự khác **không ghép**; stream vẫn mang surrogate đứng lẻ.
- `codePoints()` đếm code point, **không tự đếm grapheme cluster** hoặc chuẩn hóa Unicode.

**Kết luận:** `String.codePoints()` cho phép xử lý chuỗi Java theo từng code point Unicode thay vì từng `char` UTF-16. Nó ghép **high surrogate + low surrogate liền kề** thành một `int`, giúp duyệt emoji và ký tự ngoài BMP chính xác hơn. Tuy nhiên, chỉ số của `String` vẫn theo UTF-16, surrogate lỗi vẫn cần xử lý riêng, và việc nhận diện “một ký tự nhìn thấy” đòi hỏi mức phân tích grapheme cluster.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-007">Lọc, đếm và biến đổi dữ liệu bằng IntStream</a>

<details>
<summary>Xem chi tiết</summary>

# Lọc, đếm và biến đổi dữ liệu bằng IntStream trong Java String

## Mục tiêu bài học

Một `String` có thể tạo ra một `IntStream` để xử lý từng đơn vị ký tự dưới dạng số nguyên, từ đó lọc những phần tử cần giữ, đếm số phần tử thỏa điều kiện, tính tổng hoặc biến đổi dữ liệu. Sau bài này, bạn có thể:

- Dùng `String.chars()` và `String.codePoints()` đúng mục đích, hiểu sự khác nhau giữa **UTF-16 code unit** và **Unicode code point**.
- Tạo pipeline với `filter`, `map`, `mapToObj` và các phép kết thúc như `count`, `sum`, `collect`.
- Chuyển kết quả xử lý các code point về `String` bằng `StringBuilder.appendCodePoint`.
- Nhận diện bẫy của Unicode, phép đếm, luồng dùng một lần và các phép biến đổi chữ hoa/chữ thường.

## 1. IntStream là gì, và vì sao String trả về IntStream?

`java.util.stream.IntStream` là một *primitive stream*: nó biểu diễn một chuỗi giá trị `int`, hỗ trợ các thao tác xử lý theo pipeline mà không phải bọc từng phần tử thành `Integer`. Đây là stream xử lý dữ liệu **trong bộ nhớ**, không phải `InputStream` dùng để đọc byte từ tệp hoặc mạng.

`String` cung cấp hai phương thức quan trọng:

~~~java
IntStream utf16Units = text.chars();
IntStream unicodePoints = text.codePoints();
~~~

- `chars()` tạo luồng các **UTF-16 code unit** của chuỗi; mỗi giá trị được mở rộng từ `char` thành `int`. Một Unicode supplementary character (ví dụ emoji nhiều ký tự trong UTF-16) sẽ trở thành hai phần tử surrogate riêng.
- `codePoints()` tạo luồng các **Unicode code point**; hai surrogate hợp lệ liên tiếp được kết hợp thành một code point. Những code unit thông thường vẫn là một phần tử. Đây thường là lựa chọn phù hợp để lọc hoặc kiểm tra ký tự theo các phương thức `Character.isLetter(int)`, `Character.isDigit(int)`.
- Cả hai phương thức đều **không** tự động tách “chữ nhìn thấy trên màn hình” (*grapheme cluster*). Một ký tự hiển thị có thể gồm nhiều code point, chẳng hạn chữ cái cộng dấu kết hợp hoặc emoji nối bằng ký tự Zero Width Joiner.

Nếu chỉ làm việc với mã ASCII, kết quả của `chars()` và `codePoints()` thường trùng nhau; với nội dung đa ngôn ngữ, nên quyết định dựa trên đơn vị bạn cần xử lý.

### Ví dụ kiểm chứng UTF-16 và code point

Lưu vào `UnicodeStreamDemo.java`:

~~~java
import java.util.stream.Collectors;

public class UnicodeStreamDemo {
    public static void main(String[] args) {
        String text = "A😀B";

        System.out.println(text.length());
        System.out.println(text.chars().count());
        System.out.println(text.codePoints().count());

        String values = text.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(", "));
        System.out.println(values);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 UnicodeStreamDemo.java
java UnicodeStreamDemo
~~~

Kết quả:

~~~text
4
4
3
U+0041, U+1F600, U+0042
~~~

`"A😀B"` có ba Unicode code point nhưng bốn UTF-16 code unit, do `😀` được lưu bằng một cặp surrogate. `String.length()` và `chars().count()` đều phản ánh số code unit trong ví dụ; `codePoints().count()` phản ánh số code point. **Đừng gọi cả ba kết quả là “số ký tự người dùng nhìn thấy”.**

## 2. Pipeline gồm những bước nào?

Một pipeline thường có ba thành phần:

~~~text
String.codePoints()      // nguồn: IntStream của code point
  .filter(...)            // thao tác trung gian: giữ các giá trị thỏa điều kiện
  .map(...)               // thao tác trung gian: biến đổi int -> int
  .count()                // thao tác kết thúc: trả về long
~~~

Các phép trung gian như `filter`, `map`, `distinct`, `sorted`, `limit` thường tạo một luồng mới mô tả việc sẽ xử lý, nhưng chưa chạy toàn bộ dữ liệu ngay. Thao tác kết thúc như `count`, `sum`, `forEach` hoặc `collect` sẽ kích hoạt pipeline (ngoại trừ những tối ưu hóa hợp lệ của thư viện Stream). Mỗi stream chỉ được tiêu thụ một lần.

| Phép toán | Đặc điểm | Ví dụ trong bài |
| --- | --- | --- |
| `filter(IntPredicate)` | Giữ giá trị `int` khi predicate trả về `true` | Lọc chữ số với `Character::isDigit` |
| `map(IntUnaryOperator)` | Đổi mỗi `int` thành một `int` khác | Đổi code point thành chữ in hoa |
| `mapToObj(IntFunction<R>)` | Đổi từ `int` thành object | Biến code point thành chuỗi `"U+...."` |
| `distinct()` | Loại giá trị `int` trùng nhau | Lấy danh sách code point duy nhất |
| `sorted()` | Sắp xếp giá trị số nguyên | Sắp theo mã code point |
| `count()` | Trả về số phần tử kiểu `long` | Đếm chữ số |
| `sum()` | Tính tổng kiểu `int` | Cộng các chữ số ASCII sau khi đổi mã |
| `findFirst()` | Tìm phần tử đầu tiên, trả về `OptionalInt` | Tìm code point số đầu tiên |
| `collect(...)` | Gom kết quả bằng container mutable | Xây dựng `StringBuilder` |

`filter` không sửa `String` gốc: `String` là bất biến (*immutable*). Nếu cần kết quả thành chuỗi mới, bạn phải thu gom các phần tử còn lại vào cấu trúc hoặc đối tượng khác.

## 3. Lọc, đếm và tính tổng chữ số

Giả sử đầu vào là `"a1B2c3😀"`. Ta muốn đếm code point dạng chữ số, đếm dạng chữ cái và tính tổng **giá trị các chữ số ASCII**.

Lưu vào `FilterCountDemo.java`:

~~~java
public class FilterCountDemo {
    public static void main(String[] args) {
        String text = "a1B2c3😀";

        long digitCount = text.codePoints()
                .filter(Character::isDigit)
                .count();

        long letterCount = text.codePoints()
                .filter(Character::isLetter)
                .count();

        int asciiDigitSum = text.codePoints()
                .filter(cp -> cp >= '0' && cp <= '9')
                .map(cp -> cp - '0')
                .sum();

        System.out.println("Digits: " + digitCount);
        System.out.println("Letters: " + letterCount);
        System.out.println("ASCII digit sum: " + asciiDigitSum);
    }
}
~~~

Chạy bằng `javac -encoding UTF-8 FilterCountDemo.java` và `java FilterCountDemo`:

~~~text
Digits: 3
Letters: 3
ASCII digit sum: 6
~~~

Từng bước:

1. `codePoints()` tạo luồng gồm `a`, `1`, `B`, `2`, `c`, `3`, `😀` (đại diện dưới dạng `int`).
2. `filter(Character::isDigit)` chỉ giữ ba chữ số và `count()` cho `3L` (kiểu `long`).
3. `filter(Character::isLetter)` giữ `a`, `B`, `c` nên kết quả cũng là `3L`.
4. Bộ lọc `cp >= '0' && cp <= '9'` chủ động giới hạn ở **ASCII 0–9**. Sau đó `map(cp -> cp - '0')` đổi mã của `'1'`, `'2'`, `'3'` thành giá trị số 1, 2, 3, rồi `sum()` cho `6`.

**Cẩn thận:** `Character.isDigit(int)` có thể nhận diện chữ số của nhiều hệ chữ viết ngoài ASCII. Vì vậy, không được tùy tiện dùng `cp - '0'` cho tất cả code point mà `isDigit` chấp nhận. Nếu cần giá trị số của chữ số Unicode, dùng `Character.digit(cp, 10)` và xử lý kết quả `-1` khi không có giá trị ở hệ cơ số yêu cầu.

Ví dụ `codePoints().filter(Character::isDigit).count()` là phép **đếm số code point thuộc loại chữ số**, không tự động cho biết giá trị số, số chữ trong một chuỗi và cũng không xác thực rằng toàn bộ input là số. Muốn kiểm tra toàn chuỗi, có thể dùng `String.matches` với mẫu phù hợp hoặc kiểm tra tất cả code point và quy tắc độ dài riêng.

## 4. Biến đổi và giữ lại ký tự để tạo chuỗi mới

`map` trong `IntStream` trả về một `int` cho mỗi phần tử đầu vào. Khi làm việc với Unicode code point, phải tạo chuỗi đầu ra bằng thao tác hiểu **code point**, thay vì ép `int` thành `char`.

Ví dụ muốn lấy các code point thuộc chữ cái rồi đổi thành chữ in hoa. Lưu `TransformStreamDemo.java`:

~~~java
public class TransformStreamDemo {
    public static void main(String[] args) {
        String text = "a1B2c3😀";

        String result = text.codePoints()
                .filter(Character::isLetter)
                .map(Character::toUpperCase)
                .collect(
                        StringBuilder::new,
                        (builder, cp) -> builder.appendCodePoint(cp),
                        StringBuilder::append)
                .toString();

        System.out.println(result);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 TransformStreamDemo.java
java TransformStreamDemo
~~~

Kết quả:

~~~text
ABC
~~~

Ý nghĩa các bước:

- `filter(Character::isLetter)` loại chữ số và emoji khỏi ví dụ, giữ `a`, `B`, `c`.
- `map(Character::toUpperCase)` biến mỗi code point thành code point in hoa tương ứng nếu có.
- `collect(supplier, accumulator, combiner)` tạo một `StringBuilder`, nối từng code point bằng `appendCodePoint` và cung cấp thao tác gộp các builder (nếu pipeline được xử lý theo nhánh song song); `toString()` lấy chuỗi kết quả.

Tại sao không dùng `(char) cp`? Vì phép ép kiểu đó chỉ giữ 16 bit thấp và có thể phá hỏng ký tự ngoài Basic Multilingual Plane. `StringBuilder.appendCodePoint(int)` bổ sung đúng một hoặc hai UTF-16 code unit cho code point hợp lệ.

**Giới hạn của phép đổi hoa bằng code point:** `Character.toUpperCase(int)` chỉ trả về **một** code point trên mỗi đầu vào và không xử lý đầy đủ những quy tắc đổi hoa phụ thuộc ngôn ngữ hoặc cần mở rộng thành nhiều ký tự. Nếu bài toán là chuyển **cả chuỗi** thành chữ in hoa theo ngôn ngữ, hãy cân nhắc `text.toUpperCase(locale)` và chính sách chuẩn hóa Unicode, thay vì cho rằng pipeline trên luôn cho cùng kết quả.

## 5. Dùng mapToObj khi muốn kết quả không còn là int

`mapToObj` hữu ích nếu mỗi code point cần được biểu diễn thành một object, chẳng hạn chuỗi nhãn hoặc đối tượng dữ liệu. Ví dụ:

~~~java
import java.util.stream.Collectors;

public class CodePointListDemo {
    public static void main(String[] args) {
        String text = "A😀B";

        String names = text.codePoints()
                .mapToObj(cp -> String.format("U+%04X", cp))
                .collect(Collectors.joining(", "));

        System.out.println(names);
    }
}
~~~

Lưu thành `CodePointListDemo.java`, biên dịch và chạy bằng `javac -encoding UTF-8 CodePointListDemo.java` rồi `java CodePointListDemo`. Kết quả:

~~~text
U+0041, U+1F600, U+0042
~~~

Ở đây `mapToObj` chuyển từng `int` thành `String`; sau đó `Collectors.joining(", ")` ghép các phần thành một chuỗi. Đây là hướng phù hợp khi cần hiển thị metadata cho từng code point. Nếu chỉ cần tạo lại chuỗi Unicode từ các code point đã được lọc/biến đổi, cách `collect(StringBuilder::new, ...appendCodePoint..., StringBuilder::append)` ở phần trước tránh tạo nhiều chuỗi trung gian nhỏ.

## 6. Khi nào nên dùng chars(), codePoints() hoặc IntStream.range()?

Ba cách đều có ích nhưng trả lời những câu hỏi khác nhau:

| Nguồn | Đơn vị duyệt | Phù hợp nhất |
| --- | --- | --- |
| `text.chars()` | UTF-16 code unit (`char` dạng `int`) | Xử lý mã ASCII, thao tác rõ ràng dựa trên `char` hoặc code unit |
| `text.codePoints()` | Unicode code point (`int`) | Lọc/đếm chữ cái, chữ số, ký tự Unicode và biến đổi theo `Character` |
| `IntStream.range(0, text.length())` | Chỉ số `char` 0 đến `length() - 1` | Khi cần **vị trí UTF-16** (offset) của ký tự theo `charAt` |

Ví dụ tìm chỉ số UTF-16 của chữ cái trong chuỗi ASCII:

~~~java
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IndexStreamDemo {
    public static void main(String[] args) {
        String text = "a1B2";

        String positions = IntStream.range(0, text.length())
                .filter(i -> Character.isLetter(text.charAt(i)))
                .mapToObj(i -> i + ":" + text.charAt(i))
                .collect(Collectors.joining(", "));

        System.out.println(positions);
    }
}
~~~

Lưu `IndexStreamDemo.java`, chạy `javac -encoding UTF-8 IndexStreamDemo.java` và `java IndexStreamDemo`. Kết quả:

~~~text
0:a, 2:B
~~~

Mẫu này phù hợp với `"a1B2"` vì toàn bộ dữ liệu ở ASCII. Với Unicode supplementary character, `charAt(i)` chỉ trả về một surrogate của cặp và chỉ số `i` là **offset code unit**, không phải chỉ số code point. Khi cần offset theo code point, hãy xem `String.offsetByCodePoints`, `String.codePointAt` hoặc cơ chế duyệt có quản lý offset. Không đánh đồng chỉ số code unit với vị trí “ký tự thứ N” trong văn bản hiển thị.

### Đếm code point không phải đếm grapheme cluster

Ví dụ ký tự `é` có thể được biểu diễn bằng một code point `U+00E9` hoặc hai code point `U+0065` và `U+0301` (chữ `e` kèm dấu kết hợp). `codePoints().count()` có thể cho số đếm khác nhau với hai chuỗi trông giống nhau. Với emoji có thành phần kết hợp, một hình hiển thị cũng có thể có nhiều code point.

Nếu yêu cầu của sản phẩm là đếm “ký tự người dùng nhìn thấy”, cắt chuỗi theo ranh giới grapheme hoặc áp giới hạn ký tự trên ô nhập, chỉ dùng `IntStream` theo code point là chưa đủ. Cần bộ phân đoạn Unicode phù hợp và cân nhắc chuẩn hóa văn bản cho bài toán cụ thể.

## 7. Pipeline được chạy một lần, và phép đếm không có tác dụng phụ mong muốn

Một lỗi thường gặp là lưu `IntStream` rồi cố dùng lại:

~~~java
// Minh họa lỗi: không phải chương trình hoàn chỉnh.
var stream = "A1B2".codePoints();
long count = stream.filter(Character::isDigit).count();
// stream.count(); // Sai: stream đã được tiêu thụ, sẽ ném IllegalStateException.
~~~

Tạo **stream mới** cho mỗi phép kết thúc:

~~~java
String text = "A1B2";
long digitCount = text.codePoints()
        .filter(Character::isDigit)
        .count();
long letterCount = text.codePoints()
        .filter(Character::isLetter)
        .count();
~~~

Không nhét side effect vào `filter`/`map` chỉ để đếm số lần gọi. Thư viện Stream có thể tối ưu hóa pipeline; ví dụ, `count()` trên nguồn có kích thước đã biết có thể trả về kết quả mà không cần chạy các phép trung gian không ảnh hưởng số lượng. Hãy dùng kết quả trả về từ thao tác kết thúc để xác định đầu ra, thay vì giả định mọi phép trung gian đều chạy đúng một lần cho mỗi phần tử.

`forEach` cũng không đảm bảo thứ tự quan sát trong stream song song; nếu thật sự cần thứ tự gặp phần tử (*encounter order*), có `forEachOrdered`. Với xử lý ký tự chuỗi thông thường, hãy ưu tiên pipeline tuần tự, ngắn và dễ hiểu trước khi cân nhắc parallelization.

## 8. Kỹ thuật bổ sung: tìm, loại trùng và giới hạn

Một số nhu cầu thực tế ngoài lọc/đếm/biến đổi:

~~~java
import java.util.OptionalInt;

public class MoreIntStreamDemo {
    public static void main(String[] args) {
        String text = "a1b2a3";

        OptionalInt firstDigit = text.codePoints()
                .filter(Character::isDigit)
                .findFirst();
        System.out.println(firstDigit.isPresent()
                ? Character.toString(firstDigit.getAsInt())
                : "Không có");

        long uniqueLetters = text.codePoints()
                .filter(Character::isLetter)
                .distinct()
                .count();
        System.out.println(uniqueLetters);
    }
}
~~~

Lưu `MoreIntStreamDemo.java` rồi chạy `javac -encoding UTF-8 MoreIntStreamDemo.java` và `java MoreIntStreamDemo`. Kết quả:

~~~text
1
2
~~~

`findFirst()` trả về `OptionalInt`, vì có thể không có phần tử phù hợp; nên dùng `isPresent`/`orElse`/`ifPresent` tùy ngữ cảnh thay vì gọi `getAsInt()` vô điều kiện. `distinct()` loại code point trùng nhau; trong ví dụ có hai chữ cái khác nhau là `a` và `b`. Phép này so sánh **giá trị code point**, không tự gom các cách viết Unicode tương đương hoặc so sánh không phân biệt hoa/thường.

`limit(n)` có thể giới hạn số phần tử đi tiếp trong stream, nhưng giới hạn `n` **code point** không nhất thiết là giới hạn `n` ký tự hiển thị trên UI. Với `sorted()`, thứ tự là thứ tự số nguyên của code point, không phải thứ tự bảng chữ cái theo `Locale`.

## 9. Lỗi thường gặp

**1. Chọn chars() cho văn bản đa ngôn ngữ rồi xem mỗi phần tử là một ký tự Unicode.** Surrogate pair bị tách làm hai, nên phép lọc theo `Character.isLetter(int)` hoặc khôi phục bằng cast `char` có thể sai. Dùng `codePoints()` khi xử lý code point.

**2. Đếm count() rồi nghĩ đó là số ký tự trên màn hình.** `chars().count()` là số UTF-16 code unit; `codePoints().count()` là số Unicode code point; không cách nào mặc nhiên đếm đúng grapheme cluster.

**3. Nhầm đếm với tổng.** `filter(...).count()` đếm số phần tử phù hợp; `filter(...).map(...).sum()` cộng các giá trị sau biến đổi. `count()` trả `long`; `sum()` của `IntStream` trả `int` và có khả năng tràn số khi giá trị quá lớn.

**4. Biến đổi mã code point bằng cast char.** `(char) cp` có thể làm mất phần mã cao. Dùng `appendCodePoint` hoặc `Character.toChars(cp)` khi xây chuỗi.

**5. Áp dụng cp - '0' cho mọi chữ số Unicode.** Phép trừ đơn giản chỉ đúng theo giả định mã chữ số ASCII liên tiếp. Nếu cần số Unicode, hãy dùng `Character.digit(cp, 10)` và xử lý ký tự không có giá trị số.

**6. Đòi dùng lại một stream đã kết thúc.** Sau `count()`, `sum()`, `collect()` hoặc `forEach()`, hãy gọi lại `text.codePoints()` để tạo pipeline mới.

**7. Đồng nhất toUpperCase(code point) với toUpperCase(Locale).** Chuyển từng code point có thể không xử lý biến thể theo ngôn ngữ hay phép đổi hoa một thành nhiều. Chọn API dựa trên yêu cầu chuyển đổi **ký tự** hay **văn bản**.

**8. Lạm dụng stream cho logic quá đơn giản.** Với kiểm tra một vị trí hoặc thao tác cố định, vòng lặp hay `String` API chuyên biệt có thể rõ ràng hơn. Stream hữu ích khi cần biểu đạt chuỗi phép lọc, biến đổi, kết hợp hoặc thống kê.

**9. Giả định lọc chữ cái sẽ giữ dấu kết hợp đi cùng chữ.** Một dấu combining mark có thể không thỏa `Character.isLetter` và bị xóa, làm thay đổi văn bản. Trước khi lọc ký tự Unicode, hãy định nghĩa chính sách với dấu kết hợp, chuẩn hóa và grapheme.

## 10. Bài tập tự kiểm tra

Cho chuỗi `"A7b0C3"`, dự đoán các biểu thức sau:

~~~java
String value = "A7b0C3";

long digits = value.codePoints()
        .filter(Character::isDigit)
        .count();

int sum = value.codePoints()
        .filter(cp -> cp >= '0' && cp <= '9')
        .map(cp -> cp - '0')
        .sum();

long differentLetters = value.codePoints()
        .filter(Character::isLetter)
        .map(Character::toLowerCase)
        .distinct()
        .count();
~~~

**Đáp án:** `digits = 3` vì có `7`, `0`, `3`; `sum = 10`; `differentLetters = 3` vì ba chữ cái `a`, `b`, `c` khác nhau sau khi đổi về chữ thường.

Bài mở rộng: sửa phép lọc để chỉ nhận chữ cái ASCII `A-Z` hoặc `a-z`; tiếp theo thử chuỗi có `😀` và một chữ có dấu kết hợp, rồi so sánh `length()`, `chars().count()`, `codePoints().count()`. Quan sát những khác biệt để lựa chọn đơn vị xử lý phù hợp.

## Checklist áp dụng vào dự án

1. Xác định đầu vào là ASCII, UTF-16 code unit, Unicode code point hay grapheme cluster.
2. Chọn đúng nguồn `chars()`, `codePoints()` hoặc `IntStream.range(...)` tùy đơn vị.
3. Viết rõ predicate của `filter`, tránh đồng nhất chữ số ASCII với toàn bộ chữ số Unicode.
4. Với `map` trên code point, giữ miền giá trị hợp lệ; khi xây chuỗi dùng `appendCodePoint`.
5. Chọn thao tác kết thúc thích hợp: `count` cho số phần tử, `sum` cho tổng, `findFirst` cho tìm kiếm, `collect` cho ghép chuỗi.
6. Mỗi phép kết thúc cần một stream mới; kiểm tra chuỗi rỗng, ký tự bổ sung, dấu kết hợp và input không phù hợp giả định.

## Tài liệu chính thức

- [Java SE 17 — String.chars() và String.codePoints()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)
- [Java SE 17 — IntStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html)
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html)
- [Java SE 17 — StringBuilder.appendCodePoint(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html#appendCodePoint(int))
- [Java SE 17 — Stream package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-lines-streams-008">Xây lại String bằng bộ đệm với appendCodePoint</a>

<details>
<summary>Xem chi tiết</summary>

# Xây lại String bằng bộ đệm với appendCodePoint trong Java

## 1. Mục tiêu

Khi xử lý văn bản từ `String.codePoints()` hoặc một `IntStream` các mã Unicode, ta thường cần tạo lại một `String`. Cách làm trực tiếp là dùng **`StringBuilder` làm bộ đệm**, thêm lần lượt mỗi mã Unicode với **`appendCodePoint(int)`**, rồi gọi `toString()`.

Mẫu cơ bản:

~~~java
String source = "A\uD83D\uDE80B"; // "A🚀B"
StringBuilder builder = new StringBuilder(source.length());
source.codePoints().forEach(builder::appendCodePoint);
String rebuilt = builder.toString();
~~~

Đây là **đoạn minh họa đặt trong một phương thức**, không phải một tệp Java độc lập. `rebuilt.equals(source)` là `true`.

Cách này đặc biệt hữu ích khi luồng xử lý cần **lọc, ánh xạ hoặc biến đổi từng Unicode code point** trước khi lắp thành chuỗi mới. Với việc sao chép nguyên xi một chuỗi mà không xử lý gì, không cần tạo bộ đệm; ta đã có chuỗi gốc.

## 2. Phân biệt `char`, code point và ký tự hiển thị

Java `String` lưu văn bản theo **UTF-16**. Ba đơn vị đo sau khác nhau:

| Khái niệm | Ví dụ/đặc điểm |
| --- | --- |
| **UTF-16 code unit** | Một giá trị `char`, 16 bit. `String.length()` trả số code unit, không luôn bằng số ký tự người dùng nhìn thấy. |
| **Unicode code point** | Giá trị số của một vị trí mã Unicode. Có thể cần **một** hoặc **hai** UTF-16 code unit để biểu diễn. `String.codePoints()` trả về `IntStream` của các giá trị kiểu `int`. |
| **Grapheme cluster** (ký tự hiển thị) | Một đơn vị người dùng nhìn như một ký tự, có thể gồm **nhiều code point**, ví dụ chữ `e` kết hợp dấu sắc `\u0301` hay emoji nối bằng ZWJ. |

Ví dụ:

- `"A"`: một code point U+0041, một UTF-16 code unit.
- `"★"`: U+2605, một code point, một code unit.
- `"🚀"`: U+1F680, một code point nhưng **hai** code unit (surrogate pair).
- `"e\u0301"`: thường hiển thị thành một chữ có dấu, nhưng chứa **hai code point** (`e` và U+0301), cũng là hai code unit.

Do đó:

~~~java
String text = "A\uD83D\uDE80B"; // A🚀B
System.out.println(text.length());                       // 4
System.out.println(text.codePointCount(0, text.length())); // 3
~~~

`String.chars()` trả một `IntStream` nhưng mỗi phần tử chỉ là **một UTF-16 code unit mở rộng sang int**, nên surrogate pair tạo **hai phần tử**. Ngược lại, `String.codePoints()` ghép cặp surrogate hợp lệ thành **một phần tử kiểu int**. Vì vậy, khi thao tác theo code point, hãy chọn `codePoints()`.

Một `char` đơn lẻ không chứa trọn emoji như U+1F680. Nếu dùng `(char) cp` để ép kiểu một code point bổ sung, sẽ mất phần thông tin vượt quá 16 bit.

## 3. `StringBuilder.appendCodePoint(int)` làm gì?

`StringBuilder.appendCodePoint(int codePoint)` thêm biểu diễn UTF-16 của code point vào cuối builder:

1. Với giá trị trong **Basic Multilingual Plane (BMP)** như U+0041 hoặc U+2605, phương thức thêm **một** UTF-16 code unit.
2. Với code point bổ sung như U+1F680, phương thức tạo **một surrogate pair**, tức **hai** code unit.
3. Với giá trị ngoài phạm vi mã code point (ví dụ `-1` hoặc `0x110000`), phương thức ném `IllegalArgumentException`.

Phương thức trả về chính `StringBuilder` nên có thể nối chuỗi lệnh:

~~~java
StringBuilder b = new StringBuilder();
b.appendCodePoint(0x41)
 .appendCodePoint(0x1F680)
 .appendCodePoint(0x42);
String result = b.toString(); // A🚀B
~~~

Với `StringBuilder`, `length()` đo **số UTF-16 code unit đã thêm**. Khi thêm emoji U+1F680, độ dài tăng **2**, không phải 1.

**Lưu ý tinh tế:** `Character.isValidCodePoint` và `appendCodePoint` kiểm tra phạm vi code point từ U+0000 đến U+10FFFF, bao gồm cả dải giá trị surrogate U+D800–U+DFFF. Giá trị thuộc dải này khi thêm đơn lẻ tạo **unpaired surrogate** trong chuỗi UTF-16, không phải Unicode scalar value hợp lệ để trao đổi qua mọi bộ mã hóa. Với dữ liệu văn bản ngoài hệ thống, nên kiểm soát cả các surrogate lẻ, tùy hợp đồng xử lý.

## 4. Ví dụ Java 17+ hoàn chỉnh và kiểm thử

Lưu chương trình sau vào tệp **AppendCodePointDemo.java**. Bài kiểm thử chạy không cần thư viện ngoài; mỗi trường hợp sai sẽ ném `AssertionError`.

~~~java
public class AppendCodePointDemo {
    private static final int ROCKET = 0x1F680; // U+1F680 🚀

    static String rebuild(String input) {
        StringBuilder result = new StringBuilder(input.length());
        input.codePoints().forEach(result::appendCodePoint);
        return result.toString();
    }

    static String removeRocket(String input) {
        StringBuilder result = new StringBuilder(input.length());
        input.codePoints()
                .filter(cp -> cp != ROCKET)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    static String rocketToStar(String input) {
        StringBuilder result = new StringBuilder(input.length());
        input.codePoints()
                .map(cp -> cp == ROCKET ? 0x2605 : cp) // U+2605 ★
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    static String rebuildByIndex(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length();) {
            int cp = input.codePointAt(i);
            result.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return result.toString();
    }

    private static void check(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError("Failed: " + name);
        }
    }

    public static void main(String[] args) {
        String text = "A\uD83D\uDE80B"; // A🚀B: 4 UTF-16 units, 3 code points

        check("UTF-16 length", text.length() == 4);
        check("code point count",
                text.codePointCount(0, text.length()) == 3);
        check("round trip stream", text.equals(rebuild(text)));
        check("round trip indexes", text.equals(rebuildByIndex(text)));
        check("remove rocket", "AB".equals(removeRocket(text)));
        check("replace rocket", "A\u2605B".equals(rocketToStar(text)));
        check("empty input", "".equals(rebuild("")));
        check("combining mark", "e\u0301".equals(rebuild("e\u0301")));
        String isolatedHighSurrogate = String.valueOf((char) 0xD83D);
        check("isolated surrogate retained",
                isolatedHighSurrogate.equals(rebuild(isolatedHighSurrogate)));

        StringBuilder supplementary = new StringBuilder();
        supplementary.appendCodePoint(ROCKET);
        check("supplementary uses two UTF-16 units",
                supplementary.length() == 2);

        boolean invalidRejected = false;
        try {
            new StringBuilder().appendCodePoint(0x110000);
        } catch (IllegalArgumentException expected) {
            invalidRejected = true;
        }
        check("invalid code point rejected", invalidRejected);

        System.out.println("utf16Length=" + text.length());
        System.out.println("codePointCount="
                + text.codePointCount(0, text.length()));
        System.out.println("roundTrip=" + text.equals(rebuild(text)));
        System.out.println("removedRocket=" + removeRocket(text));
        System.out.println("replacedRocket=" + rocketToStar(text));
        System.out.println("emojiUtf16Length=" + supplementary.length());
        System.out.println("invalidRejected=" + invalidRejected);
        System.out.println("PASS: 11 checks");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 AppendCodePointDemo.java
java AppendCodePointDemo
~~~

Đầu ra dự kiến khi toàn bộ 11 kiểm tra thành công:

~~~text
utf16Length=4
codePointCount=3
roundTrip=true
removedRocket=AB
replacedRocket=A★B
emojiUtf16Length=2
invalidRejected=true
PASS: 11 checks
~~~

Trong ví dụ:

- `rebuild` duyệt `codePoints()` và nối từng code point theo đúng thứ tự, nên khôi phục nguyên văn chuỗi đầu vào.
- `removeRocket` loại U+1F680 bằng `filter`; cả hai UTF-16 code unit tạo ra emoji bị loại **cùng nhau**, thay vì lọc từng nửa surrogate pair.
- `rocketToStar` dùng `map` biến một code point bổ sung thành code point BMP U+2605, nên độ dài UTF-16 của kết quả thay đổi dù số code point vẫn bằng nhau.
- `rebuildByIndex` cho cách tương đương khi cần nắm **vị trí trong chuỗi gốc**: `codePointAt(i)` đọc code point, `Character.charCount(cp)` giúp bước nhảy đúng 1 hoặc 2 code unit.
- Bài kiểm thử còn gồm chuỗi rỗng, dấu kết hợp, surrogate đứng lẻ, và mã nằm ngoài U+10FFFF. Các giá trị surrogate đơn lẻ được `codePoints()` trả như giá trị code unit, rồi `appendCodePoint` có thể ghi lại, nhờ vậy việc sao chép không vô tình làm đổi chuỗi gốc.

Dùng phép kiểm tra `equals` xác nhận kết quả thực tế giúp phát hiện các khác biệt không dễ thấy bằng mắt, đặc biệt khi dữ liệu chứa emoji hoặc dấu kết hợp. Không dựa chỉ vào lệnh Java `assert`, vì `assert` có thể bị tắt nếu JVM không bật `-ea`.

## 5. Cơ chế phối hợp với `IntStream`

`String.codePoints()` cung cấp luồng `IntStream` tuần tự. Mỗi giá trị `int` biểu diễn một code point (hoặc một surrogate lẻ). `forEach(result::appendCodePoint)` là cách viết ngắn gọn của:

~~~java
StringBuilder result = new StringBuilder();
text.codePoints().forEach(cp -> result.appendCodePoint(cp));
String output = result.toString();
~~~

Nếu cần điều kiện hoặc ánh xạ, đặt trước phép append:

~~~java
StringBuilder result = new StringBuilder();
text.codePoints()
    .filter(cp -> cp != 0x1F680)          // Bỏ 🚀
    .map(cp -> cp == 'a' ? 'A' : cp)        // Đổi a thành A
    .forEach(result::appendCodePoint);
String output = result.toString();
~~~

Hai đoạn là **mảnh code minh họa trong phương thức**, với `text` là một biến `String` đã có. Trong `map`, mỗi đầu ra cần là giá trị code point hợp lệ cho `appendCodePoint`. Nếu một đầu vào phải đổi thành **nhiều code point**, hãy dùng vòng lặp/`forEach` với nhiều lệnh `appendCodePoint` hoặc dùng cấu trúc xử lý phù hợp; `IntStream.map` thông thường là phép biến đổi **một sang một**.

Lưu ý quan trọng về thứ tự: mẫu `forEach` với một `StringBuilder` dùng **luồng tuần tự**. Nếu gọi `parallel()` rồi ghi cùng một builder trong `forEach`, các thao tác có thể đua nhau, mất thứ tự, hỏng kết quả do `StringBuilder` không thread-safe. Khi cần xử lý song song, phải thiết kế phương án gom kết quả an toàn và đúng thứ tự; đừng đơn thuần thêm `parallel()` vào ví dụ.

## 6. Độ phức tạp, bộ nhớ và cách chọn vòng lặp

Với đầu vào gồm `n` UTF-16 code unit và xử lý tuyến tính theo từng code point, duyệt rồi append có thời gian tổng thể **O(n)**. `StringBuilder` giảm chi phí tạo các chuỗi trung gian so với nối `String` lặp lại bằng `result += ...` trong vòng lặp lớn.

`new StringBuilder(input.length())` cấp phát dung lượng ban đầu theo **số UTF-16 code unit**, không theo số code point. Nếu chỉ sao chép hoặc lọc bỏ code point, dung lượng đó thường đủ cho kết quả. Nếu một code point có thể được thay bằng nhiều ký tự hoặc chuỗi dài hơn, builder vẫn tự mở rộng, nhưng có thể có thêm chi phí cấp phát.

`StringBuilder` là mutable và thích hợp khi dựng kết quả **trong một luồng xử lý**. Sau `toString()`, kết quả là một đối tượng `String` bất biến độc lập với các thao tác thay đổi builder về sau.

Khi cần vừa xử lý vừa biết chỉ số UTF-16 trong đầu vào, vòng lặp sau có thể thuận tiện hơn stream:

~~~java
StringBuilder out = new StringBuilder(input.length());
for (int index = 0; index < input.length();) {
    int cp = input.codePointAt(index);
    out.appendCodePoint(cp);
    index += Character.charCount(cp);
}
String rebuilt = out.toString();
~~~

Đây là mảnh code bên trong phương thức có biến `input`. **Không tăng `index++` vô điều kiện** nếu đang xử lý theo code point: với một supplementary code point, phải tiến **hai code unit**. Phương thức `Character.charCount(cp)` trả về 2 với code point bổ sung, trả về 1 với BMP hoặc surrogate đứng lẻ.

## 7. Những điểm dễ sai trong thực tế

1. **Dùng `chars()` khi muốn xử lý trọn code point:** `chars()` nhìn từng code unit, có thể làm tách đôi surrogate pair trong các phép lọc và biến đổi. Hãy dùng `codePoints()` nếu yêu cầu ở mức code point.
2. **Ép kiểu `(char) cp` rồi append:** làm mất thông tin với các giá trị trên U+FFFF. Dùng `appendCodePoint(cp)`.
3. **Nhầm code point với “một chữ người dùng thấy”:** emoji nối bằng ZWJ hoặc ký tự kết hợp dấu có thể gồm nhiều code point. `codePoints()` không tự nhóm grapheme cluster; khi cần xử lý theo cụm ký tự, cân nhắc `BreakIterator` hoặc thư viện phân đoạn Unicode phù hợp.
4. **Duyệt song song và ghi chung builder:** `StringBuilder` không an toàn khi bị nhiều luồng ghi đồng thời. Sử dụng pipeline tuần tự hoặc kiến trúc thu gom được bảo đảm thứ tự.
5. **Quên `toString()`:** builder là bộ đệm mutable; API yêu cầu `String` cần kết quả cuối `builder.toString()`.
6. **Tự giả định mọi code point chỉ thêm một `char`:** supplementary code point cần surrogate pair. Kích thước builder và chỉ số trong `String` đều tính theo UTF-16.
7. **Không kiểm tra code point sinh từ phép biến đổi:** mã âm hoặc vượt `0x10FFFF` làm `appendCodePoint` ném `IllegalArgumentException`. Nếu cần xuất bản văn bản Unicode chuẩn, chú ý cả surrogate lẻ trong vùng U+D800–U+DFFF.
8. **Dùng builder để tái tạo nguyên xi chuỗi gốc mà không có biến đổi:** không cần thiết nếu ứng dụng đã có `String` và có thể trả trực tiếp. Builder hữu ích khi cần thực sự dựng một kết quả mới.

## 8. Bài tập tự kiểm tra

**Bài 1.** Với `"x🚀y"`, `length()` và `codePoints().count()` bằng bao nhiêu?

Đáp án: `length() == 4` vì `x` và `y` mỗi ký tự một code unit, rocket hai code unit; `codePoints().count() == 3` vì có ba code point.

**Bài 2.** Vì sao `input.codePoints().filter(cp -> cp != 0x1F680).forEach(builder::appendCodePoint)` xóa emoji 🚀 một cách trọn vẹn?

Đáp án: luồng chứa **một phần tử int U+1F680** cho cả surrogate pair. `filter` loại chính phần tử này; không có nửa surrogate nào được append.

**Bài 3.** Nếu thay U+1F680 bằng U+2605 thông qua `map`, số code point thay đổi không? Số UTF-16 code unit có thể thay đổi không?

Đáp án: một mã đổi thành một mã nên số code point không đổi, nhưng U+1F680 dùng hai code unit còn U+2605 dùng một, nên `String.length()` giảm một.

**Bài 4.** Làm sao giữ nguyên thứ tự và tránh lỗi khi dựng chuỗi bằng builder từ stream?

Đáp án: dùng `codePoints()` **tuần tự** và append tuần tự; không ghi song song vào cùng `StringBuilder`.

## 9. Khi nào nên dùng mẫu này?

Dùng `codePoints() → filter/map → StringBuilder.appendCodePoint() → toString()` khi cần **chủ động dựng lại String theo code point**, ví dụ bỏ emoji cụ thể, chuẩn hóa một số ký tự, thay ký hiệu Unicode, hoặc lọc những mã không cho phép. Nếu cần xử lý theo UTF-16 code unit, grapheme cluster hoặc quy tắc ngôn ngữ tự nhiên, cần chọn mức trừu tượng tương ứng.

**Quy tắc nhớ:** `String.length()` đếm UTF-16 code unit, `String.codePoints()` duyệt code point, còn `StringBuilder.appendCodePoint()` biến mỗi giá trị `int` trở lại biểu diễn UTF-16 đúng độ rộng. Giữ thứ tự, kiểm tra mã đầu vào và xây dựng kết quả tuần tự để tái tạo `String` an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)
