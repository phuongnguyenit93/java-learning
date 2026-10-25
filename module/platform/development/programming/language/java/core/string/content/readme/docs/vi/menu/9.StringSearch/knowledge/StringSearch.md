<a id="back-to-top"></a>

# Tìm kiếm

## Menu
- [contains để kiểm tra chuỗi con](#string-search-001)
- [startsWith, startsWith có vị trí và endsWith](#string-search-002)
- [indexOf với ký tự hoặc code point](#string-search-003)
- [indexOf với chuỗi con và fromIndex](#string-search-004)
- [lastIndexOf và hướng tìm ngược](#string-search-005)
- [Giá trị -1 và lỗi dùng kết quả tìm kiếm làm chỉ số](#string-search-006)
- [Chuỗi tìm rỗng và các trường hợp ở biên](#string-search-007)
- [indexOf có beginIndex, endIndex từ Java 21](#string-search-008)
- [Phân biệt tìm chuỗi nguyên văn với tìm theo biểu thức chính quy](#string-search-009)

## <a id="string-search-001">contains để kiểm tra chuỗi con</a>

<details>
<summary>Xem chi tiết</summary>

# `String.contains()` để kiểm tra chuỗi con trong Java

Trong Java, **`String.contains(CharSequence s)`** trả về `true` nếu chuỗi đang xét **có chứa một dãy ký tự liên tiếp đúng bằng** đối số `s`, ngược lại trả về `false`. Đây là công cụ đơn giản để kiểm tra sự xuất hiện của một **chuỗi con** (*substring*) mà không cần biết vị trí bắt đầu.

Ví dụ:

```java
String text = "Java Core - String";

System.out.println(text.contains("Core")); // true
System.out.println(text.contains("core")); // false
```

Từ khóa quan trọng là **liên tiếp** và **đúng nguyên văn**: `contains()` phân biệt chữ hoa/chữ thường, không bỏ khoảng trắng, không tự chuẩn hóa Unicode, không tách từ và **không dùng biểu thức chính quy (regex)**.

## 1. Cú pháp, tham số và giá trị trả về

Khai báo trong lớp `java.lang.String`:

```java
public boolean contains(CharSequence s)
```

- **Đối tượng nhận:** một `String` cần tìm bên trong, chẳng hạn `text`.
- **Tham số `s`:** một `CharSequence` làm chuỗi con cần tìm. Thông thường truyền một `String` như `"Java"`; đối số có thể là một triển khai `CharSequence` khác như `StringBuilder`.
- **Kết quả:** `boolean`, bằng `true` nếu tìm được dãy ký tự liên tiếp, bằng `false` nếu không.
- **Vị trí:** `contains()` **không trả về vị trí**. Nếu muốn vị trí xuất hiện đầu tiên, dùng `indexOf()`.
- **`null`:** gọi phương thức trên `null` hoặc truyền tham số `null` đều gây `NullPointerException` (chi tiết trong phần xử lý null).

Ví dụ so sánh:

| Đầu vào | Lời gọi | Kết quả | Giải thích |
| --- | --- | --- | --- |
| `"Java Core"` | `contains("Java")` | `true` | Tìm thấy ở đầu |
| `"Java Core"` | `contains("Core")` | `true` | Tìm thấy ở cuối |
| `"Java Core"` | `contains("va Co")` | `true` | Tồn tại dãy liên tiếp xuyên qua dấu cách |
| `"Java Core"` | `contains("java")` | `false` | `J` và `j` khác nhau |
| `"Java Core"` | `contains("JavaCore")` | `false` | Thiếu dấu cách so với dữ liệu gốc |
| `"Java Core"` | `contains("Python")` | `false` | Không xuất hiện |

**`contains()` không yêu cầu khớp toàn bộ chuỗi**. `"Java Core".contains("Java")` là `true` dù đối tượng có thêm `" Core"`, còn `"Java Core".equals("Java")` là `false`.

## 2. Ví dụ Java 17+ hoàn chỉnh

Lưu mã dưới tên `ContainsBasics.java`:

```java
public class ContainsBasics {
    public static void main(String[] args) {
        String text = "Java Core - String";

        System.out.println(text.contains("Core"));
        System.out.println(text.contains("core"));
        System.out.println(text.contains("Java Core"));
        System.out.println(text.contains(""));

        System.out.println("".contains(""));
        System.out.println("".contains("Java"));

        System.out.println(
                text.contains(new StringBuilder("String")));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ContainsBasics.java
java ContainsBasics
```

Kết quả:

```text
true
false
true
true
true
false
true
```

Giải thích:

1. Chuỗi `"Core"` xuất hiện liên tiếp nên trả `true`.
2. `"core"` khác `"Core"` do phân biệt chữ hoa/thường nên `false`.
3. `"Java Core"` xuất hiện ở đầu chuỗi, khớp đúng dấu cách.
4. **Chuỗi rỗng `""` được coi là chuỗi con của mọi `String`**, kể cả chuỗi rỗng; vì thế cả `text.contains("")` và `"".contains("")` đều trả `true`.
5. Chuỗi rỗng không chứa dãy ký tự `"Java"` nên `"".contains("Java")` trả `false`.
6. `StringBuilder` triển khai `CharSequence`, vì vậy `contains(new StringBuilder("String"))` hợp lệ và tìm được dãy ký tự.

Về mặt ngữ nghĩa, Java kiểm tra sự hiện diện của **một chuỗi con chính xác**. Không cần gọi `toString()` trước khi truyền một `StringBuilder`, nhưng nếu nội dung của đối tượng thay đổi trong khi một luồng khác đang sử dụng nó, cần đồng bộ phù hợp; các `CharSequence` có thể thay đổi không có bảo đảm thread-safe mặc định.

## 3. Phân biệt `contains`, `indexOf`, `startsWith`, `endsWith`, `equals` và `matches`

Các API đều xử lý chuỗi, nhưng hỏi các câu khác nhau:

| API | Mục đích | Ví dụ |
| --- | --- | --- |
| `text.contains(part)` | Có chứa đoạn nguyên văn ở **bất kỳ đâu** không? | `"file.txt".contains("le.")` → `true` |
| `text.indexOf(part)` | Đoạn nguyên văn xuất hiện **đầu tiên ở vị trí nào**? | `"banana".indexOf("ana")` → `1` |
| `text.lastIndexOf(part)` | Đoạn nguyên văn xuất hiện **cuối cùng ở vị trí nào**? | `"banana".lastIndexOf("ana")` → `3` |
| `text.startsWith(part)` | Chuỗi có **bắt đầu** bằng đoạn này không? | `"file.txt".startsWith("file")` → `true` |
| `text.endsWith(part)` | Chuỗi có **kết thúc** bằng đoạn này không? | `"file.txt".endsWith(".txt")` → `true` |
| `text.equals(other)` | Hai chuỗi có **toàn bộ nội dung** bằng nhau không? | `"java".equals("java")` → `true` |
| `text.matches(regex)` | **Toàn bộ** chuỗi có khớp biểu thức chính quy không? | `"abc123".matches(".*\\d+.*")` → `true` |

Đặc biệt, `String.contains()` không diễn giải dấu `.`, `*`, `+`, `?` hay `\d` theo ngôn ngữ regex. Khi tìm dấu chấm bằng `contains(".")`, nó tìm **ký tự dấu chấm thật**; khi tìm `contains("\\d+")`, nó tìm **ba ký tự** dấu gạch chéo ngược, `d` và `+` trong văn bản, chứ không có nghĩa “một hoặc nhiều chữ số”.

Ví dụ thực thi độc lập `ContainsCompare.java`:

```java
public class ContainsCompare {
    public static void main(String[] args) {
        String text = "Error: file missing";

        System.out.println(text.contains("Error:"));
        System.out.println(text.startsWith("Error:"));
        System.out.println(text.endsWith("Error:"));
        System.out.println(text.indexOf("file"));

        System.out.println("a.b".contains("."));
        System.out.println("abc123".contains("\\d+"));
        System.out.println("abc123".matches(".*\\d+.*"));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 ContainsCompare.java
java ContainsCompare
```

Kết quả:

```text
true
true
false
7
true
false
true
```

Ở đây `"file"` bắt đầu ở vị trí UTF-16 **7**. Khi chỉ cần một giá trị đúng/sai, `contains()` diễn đạt ý định rõ; khi cần cắt đoạn hoặc báo vị trí, `indexOf()` cung cấp chỉ số. `indexOf(part) >= 0` thường có cùng ý nghĩa về sự tồn tại của đoạn ký tự với `contains(part)` khi tham số là một `String` không null.

### `contains()` không kiểm tra ranh giới từ

`"supervisor".contains("admin")` là `false` vì không có chuỗi `"admin"`; trong khi `"superadministrator".contains("admin")` và `"superadmin".contains("admin")` đều là `true`. Phép tìm không biết đâu là **từ**, **quyền hạn** hay **mã sản phẩm**. Nếu hệ thống cần kiểm tra một vai trò bằng `"admin"`, hãy kiểm tra đúng token/giá trị đã phân tách và chuẩn hóa theo hợp đồng; không dùng `contains("admin")` để quyết định cấp quyền.

## 4. Chuỗi rỗng và giá trị `null`

Hai trường hợp cần phân biệt:

- **`""` là chuỗi rỗng hợp lệ.** Mọi chuỗi đều chứa chuỗi rỗng theo định nghĩa API.
- **`null` nghĩa là không có tham chiếu tới đối tượng `String`.** Không thể gọi phương thức instance trên `null`.

```java
String text = "hello";
System.out.println(text.contains("")); // true

String missing = null;
// missing.contains("h"); // NullPointerException
// text.contains(null);   // NullPointerException
```

Đoạn trên là mã minh họa trong thân phương thức (hai dòng có thể ném ngoại lệ đã được comment). Khi nhận dữ liệu có thể vắng mặt, chọn chính sách nghiệp vụ:

```java
public static boolean containsSafely(
        String text, String query) {
    return text != null
            && query != null
            && text.contains(query);
}
```

Đây là **phương thức minh họa để đặt trong class**. Nó trả `false` khi `text` hoặc `query` là `null`; nhưng với `query.equals("")`, nếu `text` không null, nó vẫn trả `true`. Chính sách này thích hợp khi null có nghĩa “không kiểm tra được”; ứng dụng khác có thể muốn báo lỗi đầu vào.

**Điểm dễ bỏ sót:** Nếu giao diện tìm kiếm không muốn từ khóa trống khớp mọi bản ghi, kiểm tra rõ `query.isEmpty()` hoặc `query.isBlank()` trước khi gọi `contains()`. Hai phương thức này khác nhau: `isBlank()` còn nhận các chuỗi chỉ gồm khoảng trắng theo khái niệm whitespace của Java, không có nghĩa tất cả ký tự trống Unicode đều nhất thiết được nhận diện như nhau.

## 5. Phân biệt chữ hoa/thường và tìm kiếm không phân biệt hoa/thường

`contains()` là phép so khớp **phân biệt hoa/thường**:

```java
System.out.println("Introduction to Java".contains("java"));
// false
```

Nếu bài toán tìm kiếm cơ bản muốn bỏ qua khác biệt hoa/thường, có thể đưa **cả hai chuỗi** về chữ thường bằng cùng một `Locale` rồi tìm:

Lưu `ContainsIgnoreCaseDemo.java`:

```java
import java.util.Locale;

public class ContainsIgnoreCaseDemo {
    public static void main(String[] args) {
        String text = "INTRO JAVA";
        String query = "java";

        boolean found = text.toLowerCase(Locale.ROOT)
                .contains(query.toLowerCase(Locale.ROOT));

        System.out.println(found);
        System.out.println(text.contains(query));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ContainsIgnoreCaseDemo.java
java ContainsIgnoreCaseDemo
```

Kết quả:

```text
true
false
```

`Locale.ROOT` là lựa chọn nhất quán cho nhiều tình huống so khớp **không phụ thuộc ngôn ngữ người dùng**. Tuy nhiên, biến đổi chữ hoa/thường có những quy tắc Unicode phức tạp, có thể thay đổi số code point và phụ thuộc ngôn ngữ; cách hạ chữ rồi `contains()` **không tương đương đầy đủ với Unicode case folding, tìm kiếm theo collation, phân tách từ hoặc tìm kiếm không dấu**. Với văn bản đa ngôn ngữ và yêu cầu so khớp ngôn ngữ cụ thể, cần chọn thuật toán/thư viện phù hợp và kiểm thử các trường hợp như `I/i/İ/ı` trong tiếng Thổ Nhĩ Kỳ.

Không tự động dùng cùng phép hạ chữ trong các vị trí yêu cầu **so khớp chính xác theo giao thức**, chẳng hạn khóa định danh phân biệt chữ hoa, chữ ký số hoặc token bảo mật.

## 6. Unicode: cùng cách nhìn nhưng chuỗi con vẫn có thể không khớp

`String.contains()` tìm dãy **UTF-16 code unit** khớp liên tiếp. Nó **không** thực hiện Unicode normalization, không xét grapheme cluster hay chữ có dấu tương đương về mặt hiển thị.

Ví dụ “é” có thể được biểu diễn theo hai cách:

- `"\u00E9"`: một code point dựng sẵn U+00E9.
- `"e\u0301"`: chữ `e` (U+0065) theo sau bởi dấu sắc kết hợp U+0301.

Hai cách thường trông giống nhau, nhưng chuỗi UTF-16 khác nhau. Muốn so khớp theo dạng chuẩn tương đương, có thể chủ động chuẩn hóa **cả nguồn và từ khóa** về cùng Unicode normalization form.

Ví dụ độc lập `ContainsUnicodeDemo.java`:

```java
import java.text.Normalizer;

public class ContainsUnicodeDemo {
    public static void main(String[] args) {
        String composed = "Caf\u00E9";
        String decomposed = "Cafe\u0301";

        System.out.println(composed.contains(decomposed));

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        System.out.println(composed.contains(normalized));

        String emojiText = "A\uD83D\uDE00B"; // A😀B
        System.out.println(
                emojiText.contains("\uD83D\uDE00"));
        System.out.println(
                emojiText.contains("\uD83D"));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 ContainsUnicodeDemo.java
java ContainsUnicodeDemo
```

Kết quả:

```text
false
true
true
true
```

Hai dòng cuối cho thấy một sự khác biệt quan trọng: chuỗi `"A😀B"` chứa cả hai code unit tạo thành emoji, nhưng cũng **chứa riêng high surrogate `\uD83D`** ở mức code unit, nên phép tìm trả `true`. Điều đó không có nghĩa high surrogate là một ký tự Unicode hoàn chỉnh. Nếu cần tìm “ký tự người dùng cảm nhận”, hãy kết hợp xử lý grapheme cluster (ví dụ `\X` trong `java.util.regex.Pattern` trên Java 9+) và các quy tắc nghiệp vụ tương ứng; `contains()` không bảo đảm ranh giới cụm ký tự.

**Không nhầm normalize với bỏ dấu:** `Normalizer.Form.NFC`/`NFD` đưa biểu diễn về dạng chuẩn, không tự biến `"é"` thành `"e"`. Chức năng tìm kiếm không dấu còn cần một chính sách riêng, đặc biệt đối với tiếng Việt.

## 7. Các tình huống áp dụng thực tế

### 7.1. Lọc danh sách theo một đoạn văn bản

Giả sử danh sách tiêu đề đã có trong bộ nhớ và người dùng nhập một từ khóa. Trong ví dụ này, tìm kiếm phân biệt hoa/thường theo `contains()`:

```java
import java.util.List;

public class FilterBySubstringDemo {
    public static void main(String[] args) {
        List<String> titles = List.of(
                "Java String",
                "Java Collection",
                "Spring Boot",
                "Modern Java");

        String query = "Java";

        List<String> selected = titles.stream()
                .filter(title -> title.contains(query))
                .toList();

        System.out.println(selected);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 FilterBySubstringDemo.java
java FilterBySubstringDemo
```

Kết quả:

```text
[Java String, Java Collection, Modern Java]
```

`contains()` hợp lý với danh sách nhỏ trong bộ nhớ và khi nghiệp vụ cần một phép tìm nguyên văn. Với dữ liệu lớn, hệ thống tìm kiếm toàn văn, phân tích từ, xếp hạng kết quả hoặc truy vấn cơ sở dữ liệu, cần chọn cơ chế phù hợp (index, full-text search, collation…). Tránh lặp lại việc chuyển hoa/thường hoặc chuẩn hóa cùng nội dung hàng nghìn lần nếu có thể tính trước một lần trong pipeline.

### 7.2. Kiểm tra một đoạn thông báo đã xuất hiện

```java
String message = "WARN: connection timeout";
boolean hasTimeout = message.contains("timeout"); // true
```

Cách này thuận tiện để phát hiện đoạn văn bản trong tình huống đơn giản. Nếu cần phân tích log đúng định dạng, đếm tần suất hoặc kiểm tra mã sự kiện, nên parse trường riêng hoặc dùng định dạng log có cấu trúc; lời nhắn người dùng có thể chứa một cụm từ tương tự mà không tương ứng với cùng sự kiện.

### 7.3. Quyết định dùng công cụ nào

- Chỉ cần **có/không** một đoạn nguyên văn → `contains()`.
- Cần **vị trí bắt đầu** của đoạn → `indexOf()`, trả `-1` nếu không tìm thấy.
- Cần **lấy phần trước/sau** một dấu phân cách → `indexOf()` rồi `substring()`, nhớ kiểm tra `-1`.
- Cần **khớp tiền tố/hậu tố** → `startsWith()`/`endsWith()`.
- Cần **quy tắc regex** → `Pattern`/`Matcher` hoặc `matches()` theo yêu cầu khớp toàn bộ; không đưa regex vào `contains()`.
- Cần **khớp từ, bỏ dấu, không phân biệt locale hoặc phân đoạn Unicode** → làm rõ đặc tả tìm kiếm rồi chọn công cụ thích hợp; `contains()` không tự cung cấp các đặc tính đó.

## 8. Lỗi thường gặp

1. **Đọc `contains()` như “tìm gần đúng”:** thực ra là tìm đoạn liên tiếp chính xác, không hỗ trợ lỗi chính tả, khoảng trắng tùy ý hoặc khoảng cách chỉnh sửa.
2. **Quên phân biệt chữ hoa/thường:** `"Java".contains("java")` là `false`.
3. **Truyền regex với kỳ vọng mẫu sẽ được diễn giải:** `contains("\\d+")` tìm chuỗi nguyên văn `\d+` chứ không tìm chữ số.
4. **Dùng `contains("")` để kiểm tra có dữ liệu:** luôn `true` với chuỗi nhận không null. Muốn kiểm tra chuỗi trống dùng `isEmpty()`; muốn kiểm tra khoảng trắng có thể dùng `isBlank()`.
5. **Không xử lý null:** cả đối tượng nhận null và query null đều dẫn tới `NullPointerException`.
6. **Dùng `contains()` thay kiểm tra token:** tìm `"admin"` trong chuỗi không đồng nghĩa xác minh quyền `admin`; tìm `"cat"` trong `"catalog"` vẫn `true`.
7. **Lấy kết quả boolean làm vị trí:** `contains()` chỉ trả `boolean`; `indexOf()` mới trả index.
8. **Không chuẩn hóa Unicode khi yêu cầu tương đương chuẩn:** hai biểu diễn chữ có dấu có thể nhìn giống nhau nhưng trả `false`.
9. **Coi chuỗi con của UTF-16 là ký tự Unicode đầy đủ:** có thể tìm trúng nửa surrogate pair; `contains()` không xét ranh giới code point/grapheme.
10. **Gọi `toLowerCase()` theo default locale không kiểm soát:** kết quả có thể đổi theo môi trường; chọn locale và chính sách so khớp theo nghiệp vụ.
11. **Quá lạm dụng kiểm tra văn bản tự do cho logic nghiệp vụ:** với mã trạng thái, quyền, định danh và định dạng có cấu trúc, parse hoặc so sánh trường chính xác thường đáng tin hơn.

## 9. Câu hỏi tự kiểm tra

Với `String s = "Java Core"`, hãy dự đoán:

| Biểu thức | Kết quả | Vì sao |
| --- | --- | --- |
| `s.contains("Java")` | `true` | Tiền tố cũng là chuỗi con |
| `s.contains("a C")` | `true` | Đoạn liên tiếp trải qua dấu cách |
| `s.contains("Core")` | `true` | Hậu tố cũng là chuỗi con |
| `s.contains("core")` | `false` | Sai khác chữ hoa/thường |
| `s.contains("JavaCore")` | `false` | Thiếu dấu cách |
| `s.contains("")` | `true` | Chuỗi rỗng khớp tại mọi chuỗi |
| `s.contains("Java Core!")` | `false` | Đối số dài và khác nội dung |
| `s.indexOf("Core")` | `5` | `indexOf` trả vị trí, không phải boolean |

Tự đặt thêm câu hỏi: nếu `s` là `null` thì `s.contains("Java")` sẽ thế nào? Câu trả lời là **`NullPointerException`**, vì không có đối tượng để gọi phương thức.

**Ghi nhớ:** `contains(CharSequence)` là phép kiểm tra **có hay không một dãy ký tự liên tiếp chính xác** trong `String`. Nó trả `boolean`, **phân biệt hoa/thường**, **không dùng regex**, **không tự chuẩn hóa Unicode**, và **`contains("")` luôn đúng với chuỗi nhận không null**. Khi cần vị trí, quy tắc tìm theo từ/ngôn ngữ hoặc so khớp gần đúng, hãy dùng API/thuật toán chuyên biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-002">startsWith, startsWith có vị trí và endsWith</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm kiếm theo tiền tố và hậu tố với `startsWith`, `startsWith(prefix, offset)` và `endsWith` trong Java

Khi cần xác định một `String` có bắt đầu hoặc kết thúc bằng một chuỗi ký tự cho trước hay không, Java cung cấp ba cách dùng trực tiếp: `startsWith(String prefix)`, `startsWith(String prefix, int toffset)` và `endsWith(String suffix)`. Cả ba phương thức trả về kiểu `boolean` và **so sánh nội dung ký tự theo vị trí xác định**, không thay đổi chuỗi nguồn.

Điểm dễ nhầm nhất nằm ở overload có `toffset`: nó kiểm tra tiền tố **bắt đầu chính xác tại chỉ số `toffset` trong chuỗi nguồn**, chứ không đi tìm tiền tố ở một vị trí tùy ý từ `toffset` trở về sau.

## 1. Ý nghĩa của ba phương thức

| Phương thức | Kiểm tra điều gì? | Ví dụ |
| --- | --- | --- |
| `text.startsWith(prefix)` | `prefix` có xuất hiện ngay tại **đầu** `text` (chỉ số 0) không? | `"banana".startsWith("ban")` → `true` |
| `text.startsWith(prefix, toffset)` | `prefix` có khớp **ngay tại vị trí `toffset`** trong `text` không? | `"banana".startsWith("ana", 1)` → `true` |
| `text.endsWith(suffix)` | `suffix` có xuất hiện ngay tại **cuối** `text` không? | `"banana".endsWith("ana")` → `true` |

Chữ ký công khai trong lớp `java.lang.String`:

```java
public boolean startsWith(String prefix)
public boolean startsWith(String prefix, int toffset)
public boolean endsWith(String suffix)
```

Đây là chữ ký phương thức để tham khảo, không phải một chương trình chạy độc lập.

Cả ba đều kiểm tra **một chuỗi con liên tiếp**. Chuỗi có thể dài hơn tiền tố/hậu tố. Ví dụ, `"Spring Boot"` bắt đầu bằng `"Spring"` dù sau đó còn ký tự; `"file.txt"` kết thúc bằng `".txt"` dù trước hậu tố còn tên tệp.

## 2. `startsWith(String prefix)`: kiểm tra ngay tại đầu chuỗi

Ví dụ cơ bản:

```java
String text = "Java Core";

System.out.println(text.startsWith("Java"));   // true
System.out.println(text.startsWith("Ja"));     // true
System.out.println(text.startsWith("Core"));   // false
System.out.println(text.startsWith("Java Core")); // true
System.out.println(text.startsWith("Java Core!")); // false
```

Các lệnh trên là đoạn minh họa đặt trong một phương thức Java.

Một tiền tố có thể bằng toàn bộ nội dung chuỗi: `text.startsWith(text)` trả về `true`. Ngược lại, tiền tố dài hơn chuỗi nguồn không thể khớp. Phương thức không bỏ qua khoảng trắng, không tự chuyển hoa/thường, cũng không tự cắt bỏ ký tự xuống dòng.

Tệp chạy được `StartsWithBasics.java`:

```java
public class StartsWithBasics {
    public static void main(String[] args) {
        String text = "banana";

        System.out.println("ban: " + text.startsWith("ban"));
        System.out.println("ana at start: " + text.startsWith("ana"));
        System.out.println("whole: " + text.startsWith("banana"));
        System.out.println("longer: " + text.startsWith("bananas"));
        System.out.println("uppercase: " + text.startsWith("Ban"));
        System.out.println("suffix ana: " + text.endsWith("ana"));
        System.out.println("suffix ban: " + text.endsWith("ban"));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 StartsWithBasics.java
java StartsWithBasics
```

Kết quả:

```text
ban: true
ana at start: false
whole: true
longer: false
uppercase: false
suffix ana: true
suffix ban: false
```

Ở đây `"ana"` thực sự có trong `"banana"`, nhưng `startsWith("ana")` vẫn là `false` vì `"ana"` không nằm tại **đầu chuỗi**. Nếu mục tiêu là kiểm tra có xuất hiện **ở bất kỳ đâu**, dùng `contains(...)` hoặc `indexOf(...)` cho mục đích tìm kiếm khác.

## 3. `startsWith(String prefix, int toffset)`: kiểm tra ở một biên cụ thể

Overload hai tham số có cú pháp:

```java
boolean matched = text.startsWith(prefix, toffset);
```

`toffset` là **chỉ số UTF-16 code unit trong `text`**, đánh dấu vị trí ký tự đầu tiên phải được so khớp với `prefix`. Nếu tiền tố có độ dài `m`, phương thức cần khớp đúng `m` code unit liên tiếp bắt đầu từ chỉ số `toffset`.

Ví dụ, trong `"banana"`:

```text
Chuỗi:       b a n a n a
Chỉ số:      0 1 2 3 4 5
             | | | | | |
"ana" tại 1:  a n a
"ana" tại 3:      a n a
```

Vì thế:

- `"banana".startsWith("ana", 0)` → `false`; đầu chuỗi là `"ban"`.
- `"banana".startsWith("ana", 1)` → `true`; từ vị trí 1 là `"ana..."`.
- `"banana".startsWith("ana", 2)` → `false`; từ vị trí 2 là `"nan..."`.
- `"banana".startsWith("ana", 3)` → `true`; ba ký tự cuối là `"ana"`.

Tệp `StartsWithOffsetDemo.java`:

```java
public class StartsWithOffsetDemo {
    public static void main(String[] args) {
        String text = "banana";

        for (int offset = 0; offset <= text.length(); offset++) {
            System.out.printf("offset %d: %b%n",
                    offset, text.startsWith("ana", offset));
        }

        System.out.println("offset -1: " + text.startsWith("ana", -1));
        System.out.println("offset 7: " + text.startsWith("ana", 7));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 StartsWithOffsetDemo.java
java StartsWithOffsetDemo
```

Đầu ra:

```text
offset 0: false
offset 1: true
offset 2: false
offset 3: true
offset 4: false
offset 5: false
offset 6: false
offset -1: false
offset 7: false
```

Nếu `toffset` âm, vượt quá độ dài chuỗi, hoặc không còn đủ ký tự để chứa toàn bộ `prefix`, kết quả là `false` (với `prefix` không phải `null`). Đây là **kết quả so khớp không thành công**, chứ không phải lời gọi luôn ném `IndexOutOfBoundsException` khi `toffset` nằm ngoài phạm vi. Riêng tiền tố rỗng có quy tắc biên hợp lệ được giải thích ở phần sau.

### Điều rất dễ nhầm: offset không phải vị trí bắt đầu tìm kiếm

Ví dụ:

```java
String text = "abaXaba";

System.out.println(text.startsWith("aba", 1)); // false
System.out.println(text.indexOf("aba", 1));     // 4
```

`startsWith("aba", 1)` chỉ kiểm tra ba ký tự ở **chính vị trí 1** (`"baX"`), nên trả `false`. `indexOf("aba", 1)` tìm **từ chỉ số 1 trở đi** và trả về vị trí 4 khi tìm thấy. Không thể thay thế hai phương thức này cho nhau.

Nếu cần so khớp một nhãn đứng sau phần đầu cố định, `startsWith(prefix, offset)` rất phù hợp:

```java
String line = "2026-10-22 ERROR: connection lost";

System.out.println(line.startsWith("ERROR:", 11)); // true
System.out.println(line.startsWith("ERROR:", 10)); // false
```

Với dữ liệu trên, ký tự `E` đầu tiên của `ERROR:` nằm tại chỉ số 11. Tuy nhiên, khi dữ liệu phần đầu có độ dài biến đổi, đừng tự đoán một offset cố định: hãy tìm mốc phù hợp bằng `indexOf` hoặc phân tích cấu trúc dữ liệu trước.

## 4. `endsWith(String suffix)`: kiểm tra phần kết thúc

`endsWith(suffix)` trả về `true` nếu các code unit cuối của chuỗi nguồn bằng toàn bộ `suffix`. Phương thức không nhận tham số offset vì vị trí được xác định từ **cuối chuỗi nguồn**.

```java
String file = "report.csv";

System.out.println(file.endsWith(".csv"));  // true
System.out.println(file.endsWith("csv"));   // true
System.out.println(file.endsWith(".txt"));  // false
System.out.println(file.endsWith("report.csv")); // true
System.out.println(file.endsWith("REPORT.CSV")); // false
```

Hậu tố có thể bằng toàn bộ chuỗi. Nếu hậu tố dài hơn chuỗi, kết quả là `false`. Việc kiểm tra không tự nhận biết khái niệm đuôi tệp, URL hay loại MIME; nó chỉ so sánh ký tự.

Về quan hệ ngữ nghĩa, với một `suffix` không `null`, có thể hiểu phép kiểm tra:

```java
text.endsWith(suffix)
```

tương đương về kết quả với:

```java
text.startsWith(suffix, text.length() - suffix.length())
```

Nếu hậu tố dài hơn chuỗi, hiệu `text.length() - suffix.length()` âm và việc kiểm tra bắt đầu ở offset âm thất bại. Đây là cách nhìn để hiểu vị trí kiểm tra, **không phải yêu cầu bạn phải viết lại `endsWith`** trong code ứng dụng.

## 5. Prefix và suffix rỗng: những trường hợp biên

Chuỗi rỗng `""` là tiền tố và hậu tố hợp lệ của **mọi `String`**.

```java
String text = "Java";
String empty = "";

System.out.println(text.startsWith(empty)); // true
System.out.println(text.endsWith(empty));   // true
System.out.println(empty.startsWith(empty)); // true
System.out.println(empty.endsWith(empty));  // true
```

Với overload có offset, tiền tố rỗng khớp tại **mọi vị trí biên hợp lệ từ 0 đến `text.length()`**, bao gồm biên cuối:

| Lời gọi với `text = "Java"` (độ dài 4) | Kết quả | Giải thích |
| --- | --- | --- |
| `text.startsWith("", 0)` | `true` | Tiền tố rỗng tại biên đầu |
| `text.startsWith("", 2)` | `true` | Tiền tố rỗng tại biên giữa |
| `text.startsWith("", 4)` | `true` | Tiền tố rỗng tại biên cuối |
| `text.startsWith("", -1)` | `false` | Offset âm không hợp lệ |
| `text.startsWith("", 5)` | `false` | Offset lớn hơn độ dài |
| `text.startsWith("J", 4)` | `false` | Không còn code unit cho tiền tố không rỗng |

Điều này tương tự khoảng rỗng `[k, k)` ở một biên hợp lệ: vì không cần so khớp ký tự nào nên kết quả là `true`. Đừng tự động đồng nhất `startsWith("") == true` với việc chuỗi có chứa dữ liệu; ngay cả chuỗi nguồn rỗng cũng trả về `true`.

Tệp `EmptyPrefixSuffix.java` giúp kiểm tra đầy đủ:

```java
public class EmptyPrefixSuffix {
    public static void main(String[] args) {
        String text = "Java";

        System.out.println("empty prefix: " + text.startsWith(""));
        System.out.println("empty suffix: " + text.endsWith(""));
        System.out.println("at length: "
                + text.startsWith("", text.length()));
        System.out.println("after length: "
                + text.startsWith("", text.length() + 1));
        System.out.println("nonempty at length: "
                + text.startsWith("a", text.length()));
        System.out.println("empty source: "
                + "".startsWith(""));
    }
}
```

Biên dịch:

```bash
javac -encoding UTF-8 EmptyPrefixSuffix.java
java EmptyPrefixSuffix
```

Đầu ra:

```text
empty prefix: true
empty suffix: true
at length: true
after length: false
nonempty at length: false
empty source: true
```

Nếu muốn một prefix/suffix **bắt buộc phải có nội dung**, hãy xác thực điều kiện `!prefix.isEmpty()` hoặc `!suffix.isEmpty()` trước khi kiểm tra. Hành vi mặc định của API là cố ý và nhất quán.

## 6. So sánh có phân biệt hoa/thường và không tự chuẩn hóa Unicode

Ba phương thức đều so sánh **chính xác dãy UTF-16 code unit**. Chúng không tự:

- bỏ qua phân biệt chữ hoa/chữ thường;
- xóa khoảng trắng đầu/cuối;
- xử lý biến thể Unicode tương đương theo chuẩn hóa;
- thực thi biểu thức chính quy (regex), wildcard `*` hoặc `?`;
- dịch nghĩa văn bản theo ngôn ngữ tự nhiên.

Ví dụ:

```java
String name = "Readme.md";

System.out.println(name.startsWith("read")); // false
System.out.println(name.endsWith(".MD"));    // false
System.out.println(name.endsWith(".md"));    // true
```

Nếu nghiệp vụ **đối với dữ liệu ASCII** muốn so sánh không phân biệt hoa/thường, có thể chuyển đổi các phía theo cùng quy tắc, chẳng hạn `toLowerCase(Locale.ROOT)`. Tuy nhiên, xử lý Unicode đầy đủ có thể phức tạp hơn vì ánh xạ chữ hoa/thường đôi khi thay đổi số code point; việc chuẩn hóa hai chuỗi cần được thiết kế và kiểm thử theo dữ liệu thực tế.

Khi chỉ cần so khớp một vùng cố định mà không phân biệt hoa/thường, `regionMatches(true, ...)` là API khác đáng cân nhắc:

```java
String text = "Java Core";
String prefix = "java";
boolean result = text.regionMatches(
        true, 0, prefix, 0, prefix.length()
);
System.out.println(result); // true
```

Ở đây `true` bật chế độ ignore case của `regionMatches`; đây **không phải** overload của `startsWith`. Đoạn minh họa cần đặt trong một phương thức và biến `text` đã được khai báo như trên.

Với Unicode, hai chuỗi có thể hiển thị giống nhau nhưng khác dãy code point. Ví dụ `"\u00E9"` (é tiền tổ hợp) và `"e\u0301"` (e cộng dấu kết hợp) không tự động bằng nhau trong `startsWith` hoặc `endsWith` nếu chưa chuẩn hóa. Cần phân biệt vấn đề này với quy tắc so sánh tiền tố/hậu tố.

## 7. Vị trí trong `startsWith(prefix, toffset)` là UTF-16, không phải code point

Trong Java, `String.length()`, `charAt()` và tham số `toffset` dùng **chỉ số UTF-16 code unit**. Một Unicode code point ngoài BMP (ví dụ emoji 😀, `U+1F600`) chiếm hai code unit (surrogate pair). Vì vậy, vị trí code point và vị trí UTF-16 có thể không trùng nhau.

Tệp `UnicodeOffsetPrefix.java`:

```java
public class UnicodeOffsetPrefix {
    public static void main(String[] args) {
        String smile = new String(Character.toChars(0x1F600));
        String text = "A" + smile + "B";

        System.out.println("length=" + text.length());
        System.out.println("smile at 1=" + text.startsWith(smile, 1));
        System.out.println("smile at 2=" + text.startsWith(smile, 2));
        System.out.println("B at 2=" + text.startsWith("B", 2));
        System.out.println("B at 3=" + text.startsWith("B", 3));
        System.out.println("ends with B=" + text.endsWith("B"));

        int indexAfterTwoCodePoints = text.offsetByCodePoints(0, 2);
        System.out.println("B at code point boundary="
                + text.startsWith("B", indexAfterTwoCodePoints));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 UnicodeOffsetPrefix.java
java UnicodeOffsetPrefix
```

Kết quả:

```text
length=4
smile at 1=true
smile at 2=false
B at 2=false
B at 3=true
ends with B=true
B at code point boundary=true
```

`text` chứa ba code point (`A`, emoji, `B`) nhưng có bốn UTF-16 code unit. `B` nằm ở chỉ số UTF-16 **3**, không phải **2**. Nếu bắt đầu từ **vị trí code point**, hãy chuyển thành chỉ số UTF-16 với `offsetByCodePoints(...)`.

Các phương thức prefix/suffix chỉ thực hiện so khớp code unit; chúng không kiểm chứng ranh giới grapheme cluster và cũng không từ chối một chuỗi chứa surrogate đứng lẻ. Với dữ liệu văn bản quốc tế, phải định nghĩa đúng đơn vị chỉ số trước khi gọi overload có offset.

## 8. Ví dụ ứng dụng: kiểm tra nhãn đầu dòng và phần mở rộng tệp

Bài toán: đọc một số chuỗi đầu vào và phân loại theo nhãn `INFO:`, `ERROR:`, hoặc kiểm tra tên tệp có hậu tố `.csv`. Các so sánh này phù hợp vì nhãn nằm **ở đầu** và đuôi tệp nằm **ở cuối**.

Tệp `PrefixSuffixUseCase.java`:

```java
public class PrefixSuffixUseCase {
    static String logLevel(String line) {
        if (line.startsWith("ERROR:")) {
            return "error";
        }
        if (line.startsWith("INFO:")) {
            return "info";
        }
        return "other";
    }

    static boolean isCsv(String fileName) {
        return fileName.endsWith(".csv");
    }

    public static void main(String[] args) {
        System.out.println(logLevel("ERROR: disk full"));
        System.out.println(logLevel("INFO: started"));
        System.out.println(logLevel(" WARN: pending"));

        System.out.println(isCsv("report.csv"));
        System.out.println(isCsv("report.csv.bak"));
        System.out.println(isCsv("REPORT.CSV"));

        String message = "2026-10-22 ERROR: disk full";
        System.out.println(message.startsWith("ERROR:", 11));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 PrefixSuffixUseCase.java
java PrefixSuffixUseCase
```

Kết quả:

```text
error
info
other
true
false
false
true
```

Các tình huống cần hiểu:

- `" WARN: pending"` có dấu cách ở đầu nên không khớp bất kỳ nhãn nào bắt đầu tại chỉ số 0; ứng dụng muốn bỏ qua khoảng trắng cần xử lý riêng.
- `"report.csv.bak"` chứa `".csv"` ở giữa, nhưng không **kết thúc** bằng `".csv"`; `endsWith(".csv")` trả `false`.
- `"REPORT.CSV"` không kết thúc bằng `".csv"` theo so sánh phân biệt hoa/thường.
- Trong `message`, chuỗi `ERROR:` bắt đầu ở chỉ số 11, nên overload `startsWith("ERROR:", 11)` kiểm tra thành công.

Kiểm tra `endsWith(".csv")` chỉ nhận diện hậu tố ký tự; nó **không xác minh nội dung tệp thực sự là CSV**, không bảo đảm an toàn đường dẫn và không thay thế xác thực định dạng dữ liệu.

## 9. So sánh với `contains`, `indexOf`, `matches` và `regionMatches`

Khi chọn API, hãy bắt đầu bằng ý nghĩa câu hỏi:

| Câu hỏi của ứng dụng | API phù hợp |
| --- | --- |
| Có bắt đầu bằng tiền tố tại chỉ số 0 không? | `startsWith(prefix)` |
| Có đúng tiền tố tại chỉ số UTF-16 xác định không? | `startsWith(prefix, toffset)` |
| Có kết thúc bằng hậu tố không? | `endsWith(suffix)` |
| Một chuỗi ký tự xuất hiện **ở bất kỳ đâu** không? | `contains(fragment)` |
| Chuỗi xuất hiện **đầu tiên ở đâu**, tùy chọn bắt đầu tìm từ vị trí nào? | `indexOf(fragment)` hoặc `indexOf(fragment, fromIndex)` |
| Toàn bộ chuỗi có khớp một **regex** không? | `matches(regex)` |
| Hai vùng ký tự có bằng nhau, có tùy chọn bỏ qua hoa/thường không? | `regionMatches(...)` |

Ví dụ:

```java
String text = "banana";

System.out.println(text.startsWith("ana"));    // false
System.out.println(text.startsWith("ana", 1)); // true
System.out.println(text.contains("ana"));      // true
System.out.println(text.indexOf("ana"));       // 1
System.out.println(text.endsWith("ana"));      // true
```

Không nên dùng `matches(".*prefix.*")` chỉ để hỏi một chuỗi có bắt đầu bằng nội dung cố định hay không. `startsWith` rõ mục đích hơn và không biến ký tự trong tiền tố thành cú pháp regex.

## 10. Giá trị `null` và an toàn khi sử dụng

Các phương thức này không xem `null` là tiền tố hoặc hậu tố rỗng. Nếu truyền tham số `null` cho `startsWith` hoặc `endsWith`, lời gọi sẽ ném `NullPointerException`. Nếu chính biến chuỗi nguồn là `null`, gọi phương thức trên nó cũng gây `NullPointerException`.

Ví dụ xử lý tham chiếu có thể là `null`:

```java
static boolean hasErrorPrefix(String line) {
    return line != null && line.startsWith("ERROR:");
}

static boolean hasCsvSuffix(String fileName) {
    return fileName != null && fileName.endsWith(".csv");
}
```

Hai phương thức trên là ví dụ minh họa cần đặt bên trong một lớp Java. Toán tử `&&` thực hiện đánh giá ngắn mạch, nên chỉ gọi API chuỗi khi tham chiếu nguồn không `null`.

Nếu `prefix` hoặc `suffix` đến từ dữ liệu đầu vào và có thể là `null`, hãy xử lý chúng theo quy định nghiệp vụ (từ chối, yêu cầu không rỗng hoặc dùng một giá trị mặc định có chủ đích), thay vì ngầm coi `null` bằng `""`.

## 11. Lỗi phổ biến và cách tránh

**1. Tưởng `startsWith(prefix, offset)` tìm vị trí đầu tiên sau offset.** Phương thức chỉ kiểm tra **đúng tại offset**; cần tìm từ offset trở đi thì dùng `indexOf(prefix, offset)`.

**2. Nghĩ offset nằm ngoài chuỗi luôn ném ngoại lệ.** Với tiền tố không `null`, `startsWith(prefix, offset)` trả `false` nếu offset âm hoặc vượt phạm vi có thể so khớp, kể cả trường hợp không đủ ký tự còn lại.

**3. Bỏ quên tiền tố/hậu tố rỗng.** `startsWith("")` và `endsWith("")` luôn `true`; `startsWith("", offset)` chỉ `true` khi offset nằm trong khoảng từ 0 đến `length()`. Kiểm tra thêm `isEmpty()` nếu yêu cầu nghiệp vụ cần chuỗi con không rỗng.

**4. Tưởng các phương thức bỏ qua hoa/thường, khoảng trắng hoặc normalization.** Cả ba so khớp nội dung thực của `String`; mọi tiền xử lý cần được thiết kế riêng.

**5. Nhầm offset là số Unicode code point.** Java dùng chỉ số UTF-16; với emoji và ký tự bổ sung, chỉ số có thể khác vị trí code point.

**6. Dùng `endsWith(".txt")` như bằng chứng chắc chắn về kiểu nội dung.** Hậu tố chỉ phản ánh tên chuỗi, không xác thực file hay định dạng dữ liệu.

**7. Truyền `null` và mong kết quả `false`.** Cần tự kiểm tra đầu vào nếu có thể `null`.

**8. Dùng `contains` để thay thế kiểm tra chính xác đầu/cuối.** `contains` có thể đúng dù phần cần tìm nằm giữa chuỗi; điều kiện phân loại sẽ sai nếu nghiệp vụ yêu cầu tiền tố hoặc hậu tố.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Với `String s = "banana";`, ba lệnh sau trả về gì?

```java
s.startsWith("ana")
s.startsWith("ana", 1)
s.startsWith("ana", 2)
```

**Đáp án:** lần lượt là `false`, `true`, `false`. `"ana"` không ở đầu chuỗi, nhưng xuất hiện đúng tại chỉ số 1 và không xuất hiện tại chỉ số 2.

**Câu 2.** Vì sao `"abc".startsWith("", 3)` là `true` nhưng `"abc".startsWith("", 4)` là `false`?

**Đáp án:** tiền tố rỗng khớp tại biên hợp lệ từ 0 đến `length()` (bằng 3), còn chỉ số 4 vượt biên cuối.

**Câu 3.** `"archive.tar.gz".endsWith(".tar")` cho kết quả gì?

**Đáp án:** `false`, bởi toàn bộ chuỗi kết thúc bằng `".gz"`. `".tar"` chỉ xuất hiện trước hậu tố cuối cùng.

**Câu 4.** Nếu cần tìm `"ERROR:"` ở bất cứ vị trí nào từ offset 5 trở về sau, nên dùng API nào?

**Đáp án:** `indexOf("ERROR:", 5)`, rồi kiểm tra kết quả có khác `-1` không. `startsWith("ERROR:", 5)` chỉ kiểm tra chính xác tại vị trí 5.

**Câu 5.** `startsWith` và `endsWith` có hiểu `.` hoặc `*` là ký tự đại diện regex không?

**Đáp án:** Không. Các tham số là dãy ký tự thông thường; muốn dùng biểu thức chính quy, phải chọn API phù hợp với regex và xử lý cú pháp riêng.

**Ghi nhớ:** `startsWith(prefix)` kiểm tra **đầu chuỗi**, `startsWith(prefix, toffset)` kiểm tra **đúng tại offset** theo UTF-16, và `endsWith(suffix)` kiểm tra **cuối chuỗi**. Cả ba phân biệt hoa/thường, xử lý tiền tố/hậu tố rỗng theo quy tắc biên, và không tự tìm kiếm tùy ý bên trong chuỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-003">indexOf với ký tự hoặc code point</a>

<details>
<summary>Xem chi tiết</summary>

# indexOf với ký tự hoặc Unicode code point trong Java String

Trong Java, **String.indexOf(int ch)** tìm lần xuất hiện đầu tiên của một ký tự hoặc một Unicode code point trong chuỗi; **String.indexOf(int ch, int fromIndex)** tìm từ một vị trí UTF-16 được chỉ định. Cả hai phương thức trả về **vị trí bắt đầu trong dãy đơn vị mã UTF-16** hoặc **-1 nếu không tìm thấy**.

Điểm dễ nhầm là đối số có kiểu **int**, không chỉ là char. Vì vậy, chúng có thể tìm những code point Unicode nằm ngoài BMP (Basic Multilingual Plane) và chiếm **hai đơn vị mã UTF-16**, chẳng hạn emoji 😀 (U+1F600). Tuy nhiên, vị trí trả về vẫn là **chỉ số UTF-16**, không phải số thứ tự code point hoặc số ký tự người dùng nhìn thấy.

## 1. Cú pháp và ý nghĩa

Java cung cấp hai overload liên quan trực tiếp:

~~~java
int indexOf(int ch)
int indexOf(int ch, int fromIndex)
~~~

Đây là **chữ ký API để học**, không phải phương thức cần tự định nghĩa. Hai overload đều trả về int:

- **indexOf(ch)**: tìm giá trị ch từ đầu chuỗi; nếu xuất hiện nhiều lần, trả vị trí bắt đầu của lần đầu tiên.
- **indexOf(ch, fromIndex)**: chỉ xem xét các lần xuất hiện có vị trí bắt đầu từ **fromIndex trở đi**.
- **Tìm thấy**: trả về chỉ số UTF-16 của lần khớp.
- **Không tìm thấy**: trả về **-1**; đây là giá trị báo không có kết quả, không phải một chỉ số hợp lệ.
- **Không sửa chuỗi**: String bất biến, indexOf() chỉ đọc nội dung.

Với ch nằm trong BMP, hành vi về cơ bản là tìm đơn vị mã char có giá trị bằng ch. Với ch là code point bổ sung từ **U+10000 đến U+10FFFF**, phương thức tìm **cặp high surrogate + low surrogate** tương ứng và trả chỉ số của high surrogate. Với giá trị ch âm hoặc lớn hơn **U+10FFFF**, không có code point hợp lệ để tìm nên kết quả là -1.

## 2. Tìm một ký tự BMP bằng char

Trong Java, literal như **'a'**, **'J'**, **'?'**, **'7'** có kiểu char. Khi truyền cho indexOf(int), Java tự nâng giá trị char sang int. Bạn không cần ép kiểu.

~~~java
String text = "Java Java";

System.out.println(text.indexOf('J'));     // 0
System.out.println(text.indexOf('a'));     // 1
System.out.println(text.indexOf('v'));     // 2
System.out.println(text.indexOf('z'));     // -1
System.out.println(text.indexOf('J', 1));  // 5
~~~

Giải thích: chữ 'J' xuất hiện tại vị trí 0 và 5; indexOf('J') lấy vị trí 0, còn indexOf('J', 1) bỏ qua vị trí 0 và tìm vị trí 5. Nếu không có giá trị cần tìm, trả về -1.

**Không có sự khác biệt về kết quả giữa giá trị char 'J' và int 74** khi tìm trong chuỗi này:

~~~java
String text = "Java";
System.out.println(text.indexOf('J')); // 0
System.out.println(text.indexOf(74));  // 0
~~~

74 chính là code point U+004A của chữ J. Tuy nhiên, nên dùng literal char khi muốn thể hiện một ký tự BMP dễ nhận biết và dùng mã code point dạng int khi xử lý Unicode tổng quát.

### Phân biệt chữ hoa và chữ thường

indexOf() tìm **đúng giá trị ký tự/code point**; nó không tự bỏ qua hoa/thường:

~~~java
String text = "Java";
System.out.println(text.indexOf('J')); // 0
System.out.println(text.indexOf('j')); // -1
~~~

Nếu nghiệp vụ cần tìm không phân biệt hoa/thường, phải xác định một quy tắc xử lý thích hợp. Việc tự chuyển toàn bộ chuỗi sang lowercase/uppercase có thể chịu ảnh hưởng của locale hoặc biến đổi số lượng ký tự; không nên mặc định phép chuyển đổi đó bảo toàn chỉ số UTF-16 trong mọi trường hợp.

## 3. Vì sao kết quả là UTF-16 index, không phải thứ tự code point?

Java String sử dụng các **đơn vị mã UTF-16**. Một code point trong BMP thường chiếm một đơn vị mã; code point bổ sung ngoài BMP chiếm **hai** đơn vị mã: high surrogate rồi low surrogate.

Xét chuỗi:

~~~java
String text = "A😀B😀";
~~~

Nội dung gồm bốn Unicode code point theo thứ tự: A, 😀, B, 😀.

| UTF-16 index | Đơn vị mã | Ý nghĩa |
|---:|---|---|
| 0 | U+0041 | A |
| 1 | U+D83D | High surrogate của 😀 thứ nhất |
| 2 | U+DE00 | Low surrogate của 😀 thứ nhất |
| 3 | U+0042 | B |
| 4 | U+D83D | High surrogate của 😀 thứ hai |
| 5 | U+DE00 | Low surrogate của 😀 thứ hai |

Vì vậy:

- **text.length() = 6**: sáu đơn vị mã UTF-16.
- **text.codePointCount(0, text.length()) = 4**: bốn Unicode code point.
- **text.indexOf('B') = 3**: B là code point thứ ba nếu đếm từ 1, nhưng bắt đầu tại UTF-16 index 3.
- **text.indexOf(0x1F600) = 1**: emoji đầu tiên bắt đầu ở high surrogate index 1.
- **text.indexOf(0x1F600, 2) = 4**: vị trí bắt đầu 1 nhỏ hơn fromIndex 2, nên bỏ qua emoji đầu và tìm emoji thứ hai tại index 4.

Số thứ tự code point, vị trí UTF-16 và cụm ký tự hiển thị (grapheme cluster) là các khái niệm khác nhau. indexOf() trả về **UTF-16 offset**.

## 4. Tìm code point ngoài BMP bằng int

Một emoji như 😀 tương ứng **U+1F600** và có giá trị thập phân 128512. Giá trị đó lớn hơn giới hạn của char, nên cần biểu diễn bằng **int**.

~~~java
String text = "A😀B😀";
int smile = 0x1F600;

System.out.println(text.indexOf(smile));      // 1
System.out.println(text.indexOf(smile, 2));   // 4
System.out.println(text.indexOf(smile, 4));   // 4
System.out.println(text.indexOf(smile, 5));   // -1
~~~

Ở lần tìm cuối, fromIndex=5 trỏ vào low surrogate của emoji thứ hai. **Vị trí bắt đầu** của cả hai cặp emoji là 1 và 4, đều nhỏ hơn 5; do đó không tìm được cặp bắt đầu tại hoặc sau index 5.

Một literal char chỉ lưu một đơn vị mã 16 bit, nên **không thể viết** một emoji bổ sung trực tiếp như char '😀'. Hãy dùng int 0x1F600, Character.toCodePoint(high, low) khi đã có cặp surrogate, hoặc một API trả về code point dạng int.

Ví dụ tạo giá trị tìm kiếm từ chính chuỗi:

~~~java
String text = "A😀B";
int emojiCodePoint = text.codePointAt(1);
System.out.printf("U+%X%n", emojiCodePoint); // U+1F600
System.out.println(text.indexOf(emojiCodePoint)); // 1
~~~

**Điểm quan trọng:** codePointAt(1) trả về U+1F600 nhờ ghép cặp surrogate đúng; indexOf(emojiCodePoint) tìm cả cặp này. Cần phân biệt với text.charAt(1), vốn chỉ trả về high surrogate.

## 5. Chỉ số fromIndex: bao gồm vị trí bắt đầu

Với **indexOf(ch, fromIndex)**, Java tìm lần xuất hiện đầu tiên có chỉ số bắt đầu **lớn hơn hoặc bằng fromIndex**. Chính vị trí fromIndex vẫn nằm trong phạm vi tìm kiếm.

~~~java
String text = "ABABA";

System.out.println(text.indexOf('A'));      // 0
System.out.println(text.indexOf('A', 0));   // 0
System.out.println(text.indexOf('A', 1));   // 2
System.out.println(text.indexOf('A', 2));   // 2
System.out.println(text.indexOf('A', 3));   // 4
System.out.println(text.indexOf('A', 5));   // -1
~~~

Những lưu ý về ranh giới:

- **fromIndex âm**: việc tìm kiếm bắt đầu tương đương vị trí 0.
- **fromIndex bằng 0**: tương đương tìm từ đầu chuỗi.
- **fromIndex >= length()**: với overload tìm ch, không còn vị trí bắt đầu hợp lệ, nên trả về -1.
- **Chuỗi rỗng**: không có code unit để khớp, nên indexOf(int ch) luôn trả về -1.
- **fromIndex là UTF-16 offset**, không phải số thứ tự Unicode code point.

Tìm code point bổ sung đòi hỏi hai đơn vị mã liền nhau. Nếu fromIndex rơi vào giữa một cặp (tại low surrogate), cặp có high surrogate phía trước fromIndex sẽ **không** được coi là một lần khớp mới bắt đầu từ fromIndex.

## 6. Một code point hợp lệ khác với một surrogate code unit

Đối số int của indexOf() có hai cách được xử lý tùy miền giá trị:

| Đối số ch | Cách Java tìm |
|---|---|
| 0 đến U+FFFF | So khớp một đơn vị mã UTF-16 có giá trị bằng ch |
| U+10000 đến U+10FFFF | So khớp cặp surrogate UTF-16 hợp lệ biểu diễn ch |
| ch âm hoặc ch > U+10FFFF | Không có kết quả: -1 |

Trong dải **U+D800 đến U+DFFF**, các giá trị là surrogate code unit, **không phải Unicode scalar value độc lập**. Tuy nhiên, vì nằm trong miền 0 đến U+FFFF, indexOf(int) vẫn có thể tìm được riêng high/low surrogate trong chuỗi.

~~~java
String text = "A😀B";

System.out.println(text.indexOf(0xD83D));   // 1: high surrogate
System.out.println(text.indexOf(0xDE00));   // 2: low surrogate
System.out.println(text.indexOf(0x1F600));  // 1: cặp đầy đủ
System.out.println(text.indexOf(0x110000)); // -1: ngoài miền Unicode
~~~

Đây là khác biệt hữu ích khi gỡ lỗi UTF-16:

- Tìm **0xD83D** chỉ xác định đơn vị mã high surrogate, chưa chứng minh đó là emoji 😀 hoàn chỉnh.
- Tìm **0x1F600** yêu cầu **cả cặp** high surrogate U+D83D và low surrogate U+DE00 liền nhau.
- Với String có surrogate đơn lẻ hoặc cặp không hợp lệ, việc tìm một đơn vị mã surrogate vẫn có thể có kết quả; tìm code point bổ sung tương ứng sẽ không khớp cặp không hợp lệ.

Để kiểm tra giá trị bổ sung là code point hợp lệ, có thể dùng Character.isValidCodePoint(int); nếu nghiệp vụ yêu cầu **Unicode scalar value**, còn phải loại trừ dải surrogate U+D800–U+DFFF.

## 7. Chương trình đầy đủ Java 17+

Lưu tệp **StringIndexOfCodePointDemo.java** bằng UTF-8:

~~~java
public class StringIndexOfCodePointDemo {
    public static void main(String[] args) {
        String text = "A😀B😀";
        int emoji = 0x1F600;

        System.out.println("length=" + text.length());
        System.out.println("codePoints="
                + text.codePointCount(0, text.length()));

        System.out.println("A=" + text.indexOf('A'));
        System.out.println("B=" + text.indexOf('B'));
        System.out.println("missing=" + text.indexOf('X'));

        System.out.println("emoji-first=" + text.indexOf(emoji));
        System.out.println("emoji-from-2=" + text.indexOf(emoji, 2));
        System.out.println("emoji-from-4=" + text.indexOf(emoji, 4));
        System.out.println("emoji-from-5=" + text.indexOf(emoji, 5));

        System.out.println("high=" + text.indexOf(0xD83D));
        System.out.println("low=" + text.indexOf(0xDE00));
        System.out.println("invalid=" + text.indexOf(0x110000));

        int found = text.indexOf(emoji);
        if (found >= 0) {
            System.out.printf("found=U+%X%n", text.codePointAt(found));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StringIndexOfCodePointDemo.java
java StringIndexOfCodePointDemo
~~~

Đầu ra chính xác:

~~~text
length=6
codePoints=4
A=0
B=3
missing=-1
emoji-first=1
emoji-from-2=4
emoji-from-4=4
emoji-from-5=-1
high=1
low=2
invalid=-1
found=U+1F600
~~~

**Giải thích kết quả:**

1. length=6 vì có hai emoji, mỗi emoji chiếm hai đơn vị mã UTF-16.
2. codePoints=4 vì nội dung gồm A, emoji, B, emoji.
3. 'B' nằm tại UTF-16 index 3, dù là code point thứ ba tính từ đầu.
4. Chỉ số của hai emoji là 1 và 4, tức vị trí **đầu cặp surrogate**.
5. fromIndex=2 bỏ qua cặp bắt đầu ở 1; fromIndex=5 bỏ qua cả cặp bắt đầu ở 4.
6. 0xD83D và 0xDE00 được xử lý như giá trị đơn vị mã riêng; 0x110000 nằm ngoài miền Unicode.
7. Chỉ sau khi bảo đảm found >= 0, chương trình mới gọi codePointAt(found) để đọc code point khớp, tránh sử dụng -1 làm chỉ số.

Chương trình không dựa vào cách console vẽ emoji để xác minh kết quả: code point được in dưới dạng U+1F600.

## 8. Tìm tất cả lần xuất hiện của một code point

Khi cần mọi vị trí, có thể gọi indexOf(ch, fromIndex) trong vòng lặp và cập nhật **fromIndex** sau mỗi lần tìm thấy. Với code point bổ sung, nên nhảy qua toàn bộ cặp UTF-16 vừa khớp để tránh lặp lại và để thể hiện rõ điểm bắt đầu tìm tiếp.

Ví dụ độc lập, lưu **AllCodePointMatches.java**:

~~~java
public class AllCodePointMatches {
    public static void main(String[] args) {
        String text = "😀x😀y😀";
        int target = 0x1F600;
        int fromIndex = 0;

        while (true) {
            int found = text.indexOf(target, fromIndex);
            if (found == -1) {
                break;
            }
            System.out.println("match at UTF-16 index " + found);
            fromIndex = found + Character.charCount(target);
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 AllCodePointMatches.java
java AllCodePointMatches
~~~

Kết quả:

~~~text
match at UTF-16 index 0
match at UTF-16 index 3
match at UTF-16 index 6
~~~

Vì mỗi emoji chiếm hai đơn vị mã, chuỗi "😀x😀y😀" có các vị trí bắt đầu là 0, 3, 6. Hàm Character.charCount(0x1F600) trả 2; sau mỗi lần tìm, fromIndex được chuyển qua emoji đã khớp.

Nếu mục tiêu là **mọi lần xuất hiện có thể chồng lấn** của một mẫu chuỗi, chính sách di chuyển con trỏ có thể khác. Ví dụ trên chỉ xử lý từng code point đơn lẻ và lấy những lần xuất hiện tiếp theo.

### Khi chỉ cần duyệt từng code point, không phải tìm một giá trị

String.codePoints() cung cấp IntStream các code point; thích hợp để xử lý **toàn bộ** code point mà không cần xác định vị trí UTF-16. Nếu cần biết chính xác vị trí bắt đầu khớp trong String, indexOf(int) hoặc vòng lặp duyệt chỉ số UTF-16 sẽ phù hợp hơn.

## 9. Phân biệt indexOf(int) với indexOf(String)

String còn có overload **indexOf(String str)** và **indexOf(String str, int fromIndex)** để tìm **cả một chuỗi con**, khác với tìm một code point bằng int.

Ví dụ:

~~~java
String text = "abcabc";

System.out.println(text.indexOf('a'));     // 0: một ký tự
System.out.println(text.indexOf("ab"));    // 0: chuỗi con
System.out.println(text.indexOf("bc", 3)); // 4: chuỗi con từ index 3
~~~

Với emoji ngoài BMP, có thể tạo String biểu diễn code point bằng:

~~~java
int cp = 0x1F600;
String emoji = new String(Character.toChars(cp));
String text = "A😀B";

System.out.println(text.indexOf(cp));    // 1
System.out.println(text.indexOf(emoji)); // 1
~~~

Kết quả giống nhau ở ví dụ, nhưng **kiểu tham số quyết định overload**: int tìm một code point (hoặc một đơn vị mã BMP), String tìm một dãy đơn vị mã. Đừng vô tình tìm chuỗi ký tự chữ số "128512" khi mục tiêu là emoji có code point 128512.

**Lưu ý sự khác nhau về chuỗi rỗng:** tìm "" bằng overload indexOf(String) có quy tắc khớp chuỗi rỗng tại một vị trí hợp lệ; tìm int không có khái niệm “code point rỗng”. Các quy tắc trên về fromIndex >= length() áp dụng cho **indexOf(int ch, int fromIndex)**; không tự áp dụng nguyên xi sang overload indexOf(String, int).

## 10. Sai lầm thường gặp và cách tránh

1. **Dùng '😀' làm char literal:** một char chỉ chứa một đơn vị mã UTF-16; emoji U+1F600 cần hai đơn vị mã. Dùng int 0x1F600.
2. **Hiểu kết quả là số thứ tự code point:** giá trị trả về của indexOf() là UTF-16 index. Với "A😀B", 'B' ở vị trí 3.
3. **Nhầm fromIndex với thứ tự code point:** fromIndex=2 bắt đầu tìm từ UTF-16 index 2, không có nghĩa bỏ qua hai Unicode code point.
4. **Xem -1 là vị trí bình thường:** trước khi dùng kết quả với charAt(), codePointAt(), substring(), cần kiểm tra result >= 0.
5. **Cho rằng indexOf(0xD83D) có nghĩa tìm trọn emoji 😀:** đó chỉ là tìm high surrogate; muốn trọn code point dùng 0x1F600.
6. **Mong indexOf() không phân biệt hoa/thường, tự bỏ dấu hoặc chuẩn hóa Unicode:** nó tìm giá trị theo dãy đơn vị mã; các quy tắc so khớp mở rộng phải được thiết kế riêng.
7. **Thấy String.indexOf('x') và String.indexOf("x") mà nghĩ mọi quy tắc đều giống nhau:** tham số char được nâng thành int, còn "x" là String; các overload khác nhau, đặc biệt ở quy tắc tìm chuỗi rỗng.
8. **Tính index theo số ký tự hiển thị:** emoji có thể ghép nhiều code point và dấu kết hợp; UTF-16 index không trực tiếp tương ứng vị trí hiển thị.
9. **Cắt chuỗi tại chỉ số tưởng là biên code point:** nếu nhận kết quả indexOf(int) là đầu cặp surrogate, muốn lấy trọn code point bổ sung cần kết thúc tại start + Character.charCount(ch), không mặc định start + 1.
10. **Dùng kết quả tìm chuỗi Unicode làm chỉ số cho hệ khác:** các thư viện hoặc giao thức có thể đo offset theo UTF-8 bytes, code points hoặc grapheme clusters; cần quy đổi minh bạch trước khi trao đổi chỉ số.

## 11. Bài tập tự kiểm tra

Với:

~~~java
String s = "x😀y😀";
~~~

Hãy tự dự đoán:

1. s.length() và s.codePointCount(0, s.length()) bằng bao nhiêu?
2. s.indexOf('y') bằng bao nhiêu?
3. s.indexOf(0x1F600) bằng bao nhiêu?
4. s.indexOf(0x1F600, 2) bằng bao nhiêu?
5. s.indexOf(0x1F600, 5) bằng bao nhiêu?
6. s.indexOf(0xD83D) có nhất thiết là kiểm tra trọn một emoji 😀 hay không?

**Đáp án và lý do:**

1. **6 và 4**: x, emoji, y, emoji có 4 code point nhưng 6 đơn vị mã UTF-16.
2. **3**: x ở 0; emoji đầu chiếm 1–2; y bắt đầu ở 3.
3. **1**: emoji đầu có high surrogate tại 1.
4. **4**: bỏ qua cặp bắt đầu ở 1, tìm cặp thứ hai tại 4.
5. **-1**: fromIndex 5 nằm sau điểm bắt đầu cuối cùng 4.
6. **Không**: 0xD83D chỉ yêu cầu khớp một đơn vị mã high surrogate, không xác nhận cả cặp U+D83D U+DE00.

**Kết luận:** dùng **indexOf(int ch)** để tìm ký tự BMP hoặc Unicode code point; dùng **indexOf(int ch, int fromIndex)** khi cần tìm tiếp từ một offset. Khi tìm code point bổ sung, Java so khớp **cặp surrogate**, nhưng **mọi chỉ số trả về và fromIndex vẫn đo bằng đơn vị mã UTF-16**. Luôn kiểm tra **-1** và cẩn thận với ranh giới cặp surrogate.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-004">indexOf với chuỗi con và fromIndex</a>

<details>
<summary>Xem chi tiết</summary>

# `String.indexOf(String)` và `indexOf(String, fromIndex)` — tìm chuỗi con từ vị trí chỉ định

## 1. Mục tiêu và tình huống sử dụng

`String.indexOf` tìm vị trí xuất hiện của một **chuỗi con nguyên văn** trong một `String`. Đặc biệt, overload nhận `fromIndex` cho phép chọn **vị trí bắt đầu tìm**. Bạn có thể dùng nó để xác định dấu phân cách, trích xuất một trường dữ liệu, tìm lần xuất hiện tiếp theo và liệt kê mọi lần khớp, kể cả những lần khớp **chồng lấp**.

Sau bài này, bạn cần nắm:

- `indexOf(String str)` và `indexOf(String str, int fromIndex)` khác nhau thế nào.
- `fromIndex` là **chỉ số bắt đầu tìm kiếm, bao gồm vị trí đó**; nó không phải số lượng ký tự cần bỏ qua hay chỉ số bắt đầu của chuỗi con `str`.
- Kết quả là chỉ số bắt đầu của lần khớp đầu tiên, tính theo **đơn vị mã UTF-16**; không tìm thấy thì trả về `-1`.
- Cách xử lý `fromIndex` âm, bằng/vượt `length()`, chuỗi con rỗng, chuỗi đích rỗng và `null`.
- Cách dùng `indexOf` trong vòng lặp tiến vị trí, xử lý trường hợp chồng lấp và kết hợp an toàn với `substring`.
- Những giới hạn của phép tìm kiếm nguyên văn khi nội dung có chữ hoa/chữ thường, Unicode hoặc yêu cầu khớp theo từ.

## 2. Hai overload thường dùng

Trong `java.lang.String`, Java cung cấp:

~~~java
int indexOf(String str)
int indexOf(String str, int fromIndex)
~~~

Cách đọc:

~~~java
String text = "Java JavaScript Java";

int first = text.indexOf("Java");     // 0
int second = text.indexOf("Java", 1); // 5
int third = text.indexOf("Java", 6);  // 16
~~~

`indexOf("Java")` tìm từ đầu chuỗi; có thể xem như `indexOf("Java", 0)` về **kết quả**. `indexOf("Java", 1)` loại vị trí bắt đầu `0` ra khỏi các ứng viên nên nhận vị trí `5`. `indexOf("Java", 6)` bỏ qua cả lần bắt đầu ở `5`, nên nhận vị trí `16`. Lần khớp tại index `5` có thể kéo dài qua vị trí `6`; điều kiện vẫn là **điểm bắt đầu lần khớp** phải lớn hơn hoặc bằng `fromIndex`.

Hình dung vị trí:

~~~text
Văn bản:  J a v a   J a v a S c r i p t   J a v a
Chỉ số:  0 1 2 3 4 5 6 7 8 9 ...      15 16 17 18 19

"Java" bắt đầu tại: 0, 5, 16
fromIndex = 0  -> trả 0
fromIndex = 1  -> trả 5
fromIndex = 5  -> trả 5 (bao gồm)
fromIndex = 6  -> trả 16
~~~

Trong cả hai overload, kết quả là một `int`:

- Nếu tìm thấy: `0 <= result < text.length()` với chuỗi con **không rỗng**.
- Nếu không tìm thấy: `result == -1`.
- Với **chuỗi con rỗng**, kết quả còn có thể bằng `text.length()`, vì một chuỗi rỗng khớp tại ranh giới cuối. Đây là ngoại lệ quan trọng với bất đẳng thức dành cho kết quả không rỗng.

## 3. Quy tắc chính xác của `fromIndex`

Giả sử `text` có chiều dài `n` tính bằng UTF-16 code unit, `needle` là chuỗi con cần tìm.

Với `needle` **không rỗng**, `indexOf(needle, fromIndex)` tìm lần xuất hiện sớm nhất có **vị trí bắt đầu lớn hơn hoặc bằng `max(fromIndex, 0)`**. Nếu không tồn tại thì trả `-1`.

| Tình huống | Ví dụ | Kết quả |
| --- | --- | --- |
| Tìm lần xuất hiện đầu | `"banana".indexOf("ana")` | `1` |
| Bắt đầu ngay vị trí một lần khớp | `"banana".indexOf("ana", 1)` | `1` |
| Bỏ qua vị trí khớp ban đầu | `"banana".indexOf("ana", 2)` | `3` |
| `fromIndex` âm | `"banana".indexOf("ana", -100)` | `1`, tương đương tìm từ `0` |
| `fromIndex` bằng `length()`, needle không rỗng | `"banana".indexOf("ana", 6)` | `-1` |
| `fromIndex` lớn hơn `length()`, needle không rỗng | `"banana".indexOf("ana", 999)` | `-1` |
| Chuỗi con dài hơn phần còn lại | `"banana".indexOf("ana", 4)` | `-1` |
| Không có chuỗi cần tìm | `"banana".indexOf("xyz", 0)` | `-1` |

Chú ý `fromIndex` **không phải cận cuối tìm kiếm**. Hàm vẫn có thể so khớp các ký tự sau `fromIndex`; nó chỉ giới hạn vị trí **bắt đầu** của ứng viên. Ví dụ `"banana".indexOf("ana", 2)` trả `3` vì chuỗi `"ana"` bắt đầu tại `3`.

### Chuỗi con rỗng: một quy tắc riêng

Với `needle.equals("")`, kết quả là:

~~~text
indexOf("", fromIndex) = min(max(fromIndex, 0), text.length())
~~~

Áp dụng cho `"Java"` (`length() == 4`):

| Lệnh | Kết quả |
| --- | ---: |
| `"Java".indexOf("")` | `0` |
| `"Java".indexOf("", -10)` | `0` |
| `"Java".indexOf("", 2)` | `2` |
| `"Java".indexOf("", 4)` | `4` |
| `"Java".indexOf("", 100)` | `4` |

**Vì sao không phải `-1` khi `fromIndex` quá lớn?** Một chuỗi con rỗng khớp tại mọi ranh giới hợp lệ, kể cả cuối chuỗi, và Java chặn kết quả về khoảng `[0, length()]`. Quy tắc này đặc biệt quan trọng trong vòng lặp tìm nhiều lần xuất hiện.

### Chuỗi nguồn rỗng

~~~java
String empty = "";

System.out.println(empty.indexOf("x"));    // -1
System.out.println(empty.indexOf(""));     // 0
System.out.println(empty.indexOf("", 50)); // 0
~~~

Chuỗi rỗng không chứa chuỗi con **không rỗng**, nhưng bản thân chuỗi rỗng khớp tại ranh giới duy nhất `0`.

## 4. `indexOf` làm những việc gì, và không làm những việc gì?

`indexOf(String)` tìm một **dãy ký tự liền nhau chính xác**, không phải biểu thức chính quy (regex), không tự hiểu khái niệm “từ hoàn chỉnh”, không tự chuẩn hóa Unicode và không tự bỏ qua khác biệt chữ hoa/chữ thường.

Ví dụ:

~~~java
String text = "Java JavaScript";
System.out.println(text.indexOf("Java"));   // 0
System.out.println(text.indexOf("Java", 1)); // 5
System.out.println(text.indexOf("java"));   // -1
~~~

Lần khớp ở `5` nằm **bên trong `JavaScript`**. Nếu mục tiêu là tìm từ `Java` đứng độc lập, cần kiểm tra ranh giới từ hoặc dùng một bộ phân tích có quy tắc rõ ràng. Nếu mục tiêu là tìm regex như `Ja.a`, cần `Pattern`/`Matcher`; `indexOf("Ja.a")` chỉ tìm đúng dấu chấm `.` trong văn bản.

**Phân biệt overload:** `text.indexOf('a', fromIndex)` dùng overload tìm `char/int`; `text.indexOf("a", fromIndex)` dùng overload **`String`**. Hai trường hợp đơn giản có thể cho cùng vị trí, nhưng overload tìm `int ch` còn có quy định về Unicode code point supplementary. Bài này tập trung vào tìm **chuỗi con `String`**.

## 5. Ví dụ Java 17+ đầy đủ, đã kiểm tra bằng biên dịch và chạy

Tạo tệp **`IndexOfSubstringDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class IndexOfSubstringDemo {
    public static void main(String[] args) {
        String text = "Java JavaScript Java";
        System.out.println("length=" + text.length());
        System.out.println("first=" + text.indexOf("Java"));
        System.out.println("from1=" + text.indexOf("Java", 1));
        System.out.println("from6=" + text.indexOf("Java", 6));
        System.out.println("missing=" + text.indexOf("Python", 0));
        System.out.println("negativeFrom=" + text.indexOf("Java", -100));
        System.out.println("tooLargeFrom=" + text.indexOf("Java", 999));
        System.out.println("emptyNegative=" + text.indexOf("", -3));
        System.out.println("emptyAt3=" + text.indexOf("", 3));
        System.out.println("emptyTooLarge=" + text.indexOf("", 999));
        System.out.println("nonemptyAtEnd=" + text.indexOf("Java", text.length()));
        System.out.println("emptyAtEnd=" + text.indexOf("", text.length()));

        String overlap = "bananana";
        String term = "ana";
        List<Integer> positions = new ArrayList<>();
        for (int from = 0, found;
             (found = overlap.indexOf(term, from)) != -1;
             from = found + 1) {
            positions.add(found);
        }
        System.out.println("overlapping=" + positions);

        positions.clear();
        for (int from = 0, found;
             (found = overlap.indexOf(term, from)) != -1;
             from = found + term.length()) {
            positions.add(found);
        }
        System.out.println("nonOverlapping=" + positions);

        String email = "user@example.com";
        int separator = email.indexOf("@");
        String domain = separator >= 0 ? email.substring(separator + 1) : "";
        System.out.println("domain=" + domain);

        String emoji = Character.toString(0x1F600);
        String mixed = "A" + emoji + "B" + emoji;
        System.out.println("mixedLength=" + mixed.length());
        System.out.println("emojiFirst=" + mixed.indexOf(emoji));
        System.out.println("emojiNext=" + mixed.indexOf(emoji, 2));
        System.out.println("lowSurrogate=" + mixed.indexOf(String.valueOf(mixed.charAt(2))));
        System.out.println("caseSensitive=" + "Java".indexOf("java"));
    }
}
~~~

Biên dịch/chạy (đã kiểm tra ở chế độ tương thích Java 17):

~~~bash
javac --release 17 -encoding UTF-8 IndexOfSubstringDemo.java
java IndexOfSubstringDemo
~~~

`stdout` chính xác:

~~~text
length=20
first=0
from1=5
from6=16
missing=-1
negativeFrom=0
tooLargeFrom=-1
emptyNegative=0
emptyAt3=3
emptyTooLarge=20
nonemptyAtEnd=-1
emptyAtEnd=20
overlapping=[1, 3, 5]
nonOverlapping=[1, 5]
domain=example.com
mixedLength=6
emojiFirst=1
emojiNext=4
lowSurrogate=2
caseSensitive=-1
~~~

**Giải thích kết quả:**

1. `"Java JavaScript Java"` có `length() == 20`. `"Java"` xuất hiện tại UTF-16 index `0`, `5`, `16`; các giá trị `fromIndex` lần lượt chọn vị trí tương ứng.
2. Với `fromIndex = -100`, tìm như từ `0`. Với `999`, chuỗi con `"Java"` không thể có vị trí bắt đầu hợp lệ nên trả `-1`.
3. Chuỗi con rỗng khớp ở biên `0`, `3` hoặc `20` sau khi chặn `fromIndex` theo `length()`; needle không rỗng tại `fromIndex = 20` trả `-1`.
4. Trong `"bananana"`, `"ana"` bắt đầu tại `1`, `3`, `5`. Tăng `from = found + 1` cho phép khớp chồng lấp; tăng `found + term.length()` bỏ qua các ký tự đã khớp.
5. Vị trí `'@'` trong email được kiểm tra trước khi gọi `substring(separator + 1)`, tránh xử lý dữ liệu theo kết quả `-1` như thể là một vị trí tìm thấy.
6. `mixed` có sáu code unit UTF-16 vì mỗi emoji `U+1F600` dùng hai code unit. Hai emoji bắt đầu tại index `1` và `4`; vẫn có thể tìm riêng low surrogate tại index `2` nếu cố ý tạo một chuỗi con chỉ gồm code unit đó.
7. `"Java".indexOf("java")` trả `-1`: phép tìm này phân biệt hoa/thường.

## 6. Tìm lần xuất hiện tiếp theo bằng `fromIndex`

Bài toán thường gặp: “Tôi đã tìm thấy một kết quả; làm sao tiếp tục?”

Với chuỗi con `needle` **không rỗng**, ta chọn:

- **Cho phép chồng lấp:** `nextFrom = found + 1` (tăng một **UTF-16 code unit**).
- **Không cho phép chồng lấp:** `nextFrom = found + needle.length()` (đi qua toàn bộ dãy vừa khớp).

Ví dụ `"bananana"` và `"ana"`:

~~~text
Chuỗi:   b a n a n a n a
Index:   0 1 2 3 4 5 6 7
"ana":     1..3
"ana":         3..5
"ana":             5..7
Cho chồng lấp:        [1, 3, 5]
Không chồng lấp:      [1, 5]
~~~

Mẫu tổng quát khi muốn tìm **mọi vị trí bắt đầu, kể cả chồng lấp**:

~~~java
static List<Integer> allPositions(String text, String needle) {
    if (needle.isEmpty()) {
        throw new IllegalArgumentException("needle phải không rỗng");
    }

    List<Integer> positions = new ArrayList<>();
    for (int from = 0, found;
         (found = text.indexOf(needle, from)) != -1;
         from = found + 1) {
        positions.add(found);
    }
    return positions;
}
~~~

Đoạn trên là **phương thức minh họa**, cần import `java.util.List` và `java.util.ArrayList` khi đặt vào một class Java hoàn chỉnh (như ví dụ `IndexOfSubstringDemo`).

Điều kiện `needle.isEmpty()` không thể bỏ qua tùy tiện. Nếu tìm `""` trong vòng lặp chỉ kết thúc khi thấy `-1`, sẽ có nguy cơ **lặp vô hạn**: khi `from` vượt `length()`, `indexOf("", from)` vẫn trả `length()`, không bao giờ thành `-1`. Nếu nghiệp vụ cần vị trí của tất cả các khoảng rỗng, định nghĩa riêng `0, 1, ..., text.length()`, tức `length() + 1` ranh giới UTF-16, thay vì dùng mẫu tìm needle không rỗng.

Đối với khớp không chồng lấp, thay đúng một dòng `from = found + 1` bằng `from = found + needle.length()`; vẫn phải giữ điều kiện needle không rỗng.

### Cẩn thận với giá trị `-1`

Không bao giờ coi `-1` là một vị trí hợp lệ của `substring` hay `charAt`:

~~~java
String record = "name=value";
int equalsAt = record.indexOf("=");

if (equalsAt >= 0) {
    String name = record.substring(0, equalsAt);
    String value = record.substring(equalsAt + 1);
    System.out.println(name + " => " + value);
}
~~~

Nếu không thấy dấu phân cách và vẫn thực hiện `substring(0, equalsAt)`, bạn sẽ truyền `-1` làm chỉ số và nhận lỗi vượt biên. Trong một số trường hợp như `substring(indexOf("@") + 1)`, kết quả `-1 + 1 == 0` **không ném lỗi** nhưng lại âm thầm lấy toàn bộ chuỗi; đây cũng là lỗi logic cần tránh.

## 7. Ý nghĩa của kết quả index khi có Unicode

Java `String` dùng UTF-16. Các API `length()`, `substring(...)` và `indexOf(String, fromIndex)` làm việc theo **chỉ số code unit UTF-16**.

Ví dụ:

~~~text
Giá trị hiển thị:     A       😀       B       😀
Code point:         U+0041  U+1F600  U+0042  U+1F600

Chỉ số UTF-16:         0    1    2      3    4    5
Code unit:            0041 D83D DE00   0042 D83D DE00
                             \__/                \__/
                           một CP              một CP
String.length() == 6
~~~

Vì vậy:

~~~java
String emoji = Character.toString(0x1F600);
String s = "A" + emoji + "B" + emoji;

System.out.println(s.indexOf(emoji));     // 1
System.out.println(s.indexOf(emoji, 2));  // 4
~~~

Kết quả `1` và `4` là **vị trí UTF-16**, không phải số thứ tự code point. `fromIndex = 2` nằm giữa surrogate pair đầu tiên, vì thế nó không tìm lại emoji bắt đầu tại `1`; lần emoji kế tiếp bắt đầu tại `4`.

Quan trọng hơn, `indexOf(String)` so khớp các **đơn vị UTF-16 liền nhau** nên một needle cố ý chứa **một surrogate code unit đơn lẻ** vẫn có thể được tìm thấy bên trong cặp surrogate:

~~~java
String lowHalf = String.valueOf(s.charAt(2));
System.out.println(s.indexOf(lowHalf)); // 2
~~~

Nếu muốn thao tác với Unicode code point hoàn chỉnh, tự bảo đảm needle/ranh giới không cắt giữa cặp surrogate. Nếu cần **vị trí tính bằng code point**, có thể chuyển chỉ số UTF-16 `matchIndex` sang số code point trước vị trí khớp:

~~~java
int matchIndex = s.indexOf(emoji);
if (matchIndex >= 0) {
    int codePointsBefore = s.codePointCount(0, matchIndex);
    System.out.println(codePointsBefore); // 1
}
~~~

Điều này giả định `matchIndex` là ranh giới code point hợp lệ. Đối với khớp surrogate lẻ, không thể coi kết quả là một ranh giới ký tự Unicode hoàn chỉnh.

Ngoài ra, chuỗi Unicode có thể có nhiều cách mã hóa nhìn giống nhau (ví dụ ký tự `é` đơn nhất và `e` cộng dấu kết hợp). `indexOf` **không chuẩn hóa NFC/NFD** trước khi tìm. Nếu nghiệp vụ cần so khớp tương đương chuẩn hóa, có thể dùng `java.text.Normalizer` để chuẩn hóa dữ liệu **và needle** theo chính sách thống nhất trước khi so sánh; khi chuyển về chuỗi đã chuẩn hóa, nhớ rằng index trả về thuộc **chuỗi sau chuẩn hóa**, có thể không ánh xạ trực tiếp sang chuỗi ban đầu.

## 8. Phân biệt với `contains`, `startsWith` và `lastIndexOf`

| Mục đích | API phù hợp | Kết quả |
| --- | --- | --- |
| Chỉ cần biết có chứa chuỗi con không | `text.contains(needle)` | `boolean` |
| Cần vị trí lần đầu | `text.indexOf(needle)` | `int`, hoặc `-1` |
| Cần tìm lần đầu từ một vị trí | `text.indexOf(needle, fromIndex)` | `int`, hoặc `-1` |
| Cần kiểm tra tiền tố tại vị trí cụ thể | `text.startsWith(prefix, offset)` | `boolean` |
| Cần tìm từ phải sang trái | `text.lastIndexOf(needle)` hoặc `lastIndexOf(needle, fromIndex)` | `int`, hoặc `-1` |
| Cần biểu thức chính quy hoặc nhóm capture | `java.util.regex.Pattern` và `Matcher` | Match có quy tắc regex |

`contains` thuận tiện khi không dùng index, nhưng không trả vị trí. `startsWith(prefix, offset)` kiểm tra **chính xác tại offset**, còn `indexOf(prefix, offset)` có thể tìm thấy ở **bất kỳ vị trí nào từ offset trở về sau**. `lastIndexOf` có chiều tìm và quy tắc `fromIndex` riêng, không thể lấy nguyên các ví dụ của `indexOf` để suy ra tất cả hành vi của nó.

## 9. Tìm kiếm phân biệt hoa/thường và theo “từ”

`indexOf` có phân biệt chữ hoa và chữ thường:

~~~java
String headline = "Java Core";
System.out.println(headline.indexOf("java")); // -1
~~~

Nếu nghiệp vụ cần không phân biệt hoa/thường, không nên mặc định rằng gọi `toLowerCase()` là giải pháp chính xác cho mọi ngôn ngữ và mọi tình huống. Chuyển đổi hoa/thường có thể phụ thuộc locale, làm thay đổi độ dài UTF-16 và thay đổi ý nghĩa chỉ số; Unicode case folding và grapheme matching có quy tắc riêng.

Với văn bản giới hạn trong ASCII và mục tiêu chỉ là kiểm tra có chứa hay không, có thể chuẩn hóa đầu vào theo chính sách cố định (chẳng hạn `Locale.ROOT`). Nếu bạn cần **chỉ số trong chuỗi gốc**, kiểm tra cách ánh xạ index sau biến đổi; không mặc định chỉ số của chuỗi đã lowercase trùng tuyệt đối với vị trí trong chuỗi gốc. Với tìm kiếm ngôn ngữ tự nhiên, cân nhắc `Collator` hoặc công cụ tìm kiếm phù hợp.

`indexOf("Java")` cũng có thể tìm thấy `"Java"` trong `"JavaScript"`. Nếu yêu cầu “nguyên từ”, cần quy tắc ranh giới (khoảng trắng, dấu câu, ký tự Unicode, v.v.) hoặc regex cụ thể, không chỉ một lời gọi `indexOf`.

## 10. Hiệu năng và sử dụng đúng

- `indexOf` thích hợp cho **tìm kiếm chuỗi con nguyên văn**, nhất là khi đầu vào và quy tắc tìm đơn giản.
- Kết quả được đo bằng **UTF-16 offset**. Nếu dùng tiếp trong `substring`, bạn có thể dùng trực tiếp index khi ranh giới đúng và đã kiểm tra `-1`.
- Không thể suy ra một **độ phức tạp thực thi cố định** từ chữ ký API; cách tìm và tối ưu của JDK có thể thay đổi. Với chuỗi rất lớn hoặc tìm nhiều mẫu phức tạp, hãy đo trên dữ liệu thật.
- Nếu quét cùng một nội dung nhiều lần, tránh vô ý tạo ra nhiều bản sao chuỗi lớn và cân nhắc dữ liệu/thuật toán có cấu trúc phù hợp với bài toán.
- `fromIndex` không tự sửa nội dung chuỗi. `String` immutable; mỗi lời gọi chỉ trả vị trí tìm thấy theo chuỗi hiện tại.
- Không giả định kết quả sẽ là chỉ số theo ký tự người dùng hiển thị (grapheme cluster). Muốn cắt theo code point hay grapheme, cần chuyển đổi ranh giới phù hợp.

## 11. Những lỗi phổ biến

1. **Nhầm `fromIndex` là vị trí bắt đầu trong needle.** Thực tế nó là vị trí tìm kiếm trên **chuỗi nguồn**.
2. **Bỏ qua tính bao gồm của `fromIndex`.** `indexOf("ana", 1)` trên `"banana"` trả ngay `1`; để tìm lần kế tiếp, dùng `found + 1` hoặc `found + needle.length()` tùy chính sách chồng lấp.
3. **Không xử lý `-1`.** Có thể dẫn đến exception trong `substring` hoặc lỗi âm thầm nếu cộng `1` vào `-1`.
4. **Dùng vòng lặp cho phép needle rỗng.** Vì `indexOf("", hugeIndex) == length()`, vòng lặp chờ `-1` có thể không bao giờ kết thúc.
5. **Cho rằng `fromIndex` quá lớn luôn trả `-1`.** Ngoại lệ là tìm **chuỗi con rỗng**: trả `length()`.
6. **Tăng fromIndex thêm `needle.length()` khi cần bắt các kết quả chồng lấp.** Sẽ bỏ sót `3` trong ví dụ `"bananana"`.
7. **Cho rằng `indexOf` là regex hoặc không phân biệt hoa/thường.** Hàm tìm **nguyên văn, phân biệt hoa/thường** và không xét ranh giới từ.
8. **Quên rằng kết quả là UTF-16 index.** Có thể cắt đôi surrogate pair nếu dùng index tùy tiện cho văn bản Unicode.
9. **Không phân biệt `null` và `""`.** `text.indexOf((String) null)` ném `NullPointerException`; còn `text.indexOf("")` hợp lệ và trả `0`.
10. **Dùng index sau chuẩn hóa Unicode/hoa-thường trên chuỗi gốc mà không kiểm tra.** Các phép biến đổi có thể làm thay đổi số code unit; chỉ số phải thuộc đúng chuỗi được tìm.

## 12. Bài tập tự kiểm tra

**Câu 1.** `"ababab".indexOf("ab", 2)` trả bao nhiêu?

**Đáp án:** `2`, vì `fromIndex` bao gồm vị trí `2`.

**Câu 2.** `"banana".indexOf("ana", 2)` trả bao nhiêu? Vì sao?

**Đáp án:** `3`. Lần khớp ở `1` bắt đầu trước `fromIndex`, còn lần khớp tại `3` được phép.

**Câu 3.** `"Java".indexOf("", 999)` và `"Java".indexOf("a", 999)` khác nhau thế nào?

**Đáp án:** trả lần lượt `4` và `-1`; needle rỗng được chặn về ranh giới cuối chuỗi.

**Câu 4.** Với `"bananana"` và needle `"ana"`, cách tăng từ lần khớp `found` nào bắt đủ `[1, 3, 5]`?

**Đáp án:** `from = found + 1`. Nếu tăng `found + needle.length()` sẽ thu được `[1, 5]`.

**Câu 5.** Nếu `indexOf("@")` trả `-1`, có nên gọi `substring(index + 1)` ngay không?

**Đáp án:** không. `-1 + 1` trở thành `0` và có thể trả toàn chuỗi như một kết quả sai nghiệp vụ. Cần kiểm tra `index >= 0` trước.

**Câu 6.** Vì sao vị trí emoji thứ hai trong `"A😀B😀"` là UTF-16 index `4`?

**Đáp án:** chữ `A` dùng một code unit; emoji đầu dùng hai code unit ở index `1` và `2`; chữ `B` ở index `3`, nên emoji thứ hai bắt đầu tại index `4`.

**Câu 7.** `text.indexOf(needle, fromIndex)` có thể trả index **nhỏ hơn** `fromIndex` khi `fromIndex` không âm không?

**Đáp án:** không đối với needle không rỗng; nó chỉ chấp nhận các lần khớp có vị trí bắt đầu `>= fromIndex`. Riêng needle rỗng và `fromIndex > length()`, kết quả bị chặn về `length()`, nhỏ hơn `fromIndex`.

## 13. Tài liệu tham khảo

- [Oracle Java SE 17 — `String.indexOf(String)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#indexOf(java.lang.String)): tìm lần đầu của chuỗi con nguyên văn.
- [Oracle Java SE 17 — `String.indexOf(String, int)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#indexOf(java.lang.String,int)): ngữ nghĩa `fromIndex`, kết quả `-1` và needle rỗng.
- [Oracle Java SE 17 — `String.substring`, `codePointCount`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): các API dùng/đổi UTF-16 index.
- [Oracle Java SE 17 — `Character`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): code point, surrogate pair, UTF-16.

**Ghi nhớ:** `indexOf(needle, fromIndex)` trả **UTF-16 index bắt đầu** của lần xuất hiện đầu tiên có vị trí `>= fromIndex` hoặc `-1`. `fromIndex` âm được hiểu là bắt đầu từ `0`; chuỗi con rỗng có quy tắc riêng theo `length()`. Khi tìm nhiều lần, tăng điểm bắt đầu theo chính sách **có/không chồng lấp**, và luôn kiểm tra `-1` trước khi dùng index để trích xuất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-005">lastIndexOf và hướng tìm ngược</a>

<details>
<summary>Xem chi tiết</summary>

# `lastIndexOf` và hướng tìm ngược trong Java

`String.lastIndexOf(...)` dùng để tìm **lần xuất hiện cuối cùng** của một ký tự hoặc chuỗi con, tính theo chỉ số trong `String`. Nó hữu ích khi cần dấu phân cách cuối cùng, phần mở rộng tệp, vị trí dấu gạch chéo cuối trong đường dẫn, hoặc khi muốn tìm ngược từ một vị trí xác định.

Khác biệt quan trọng với `indexOf` là **hướng tìm và vị trí khớp được chọn**. Cả hai trả về một chỉ số bắt đầu tính từ **0 ở đầu chuỗi**, không phải khoảng cách đếm từ cuối. Nếu không tìm thấy thì trả về `-1`.

> Quy tắc trọng tâm: `lastIndexOf` chọn vị trí bắt đầu của **lần khớp nằm xa về phía bên phải nhất** trong phạm vi tìm kiếm; `fromIndex`, nếu có, giới hạn phạm vi tìm từ phải sang trái.

## 1. Bốn overload của `String.lastIndexOf`

`String` trong Java cung cấp bốn overload chính:

| Phương thức | Tìm gì? | Bắt đầu ở đâu? |
| --- | --- | --- |
| `lastIndexOf(int ch)` | Một Unicode code point được biểu diễn bằng `int` | Từ cuối chuỗi về đầu |
| `lastIndexOf(int ch, int fromIndex)` | Code point `ch` | Từ vị trí `fromIndex` (bao gồm vị trí đó) trở về đầu |
| `lastIndexOf(String str)` | Toàn bộ chuỗi con `str` | Lần xuất hiện cuối trong chuỗi nguồn |
| `lastIndexOf(String str, int fromIndex)` | Toàn bộ chuỗi con `str` | Lần khớp có **chỉ số bắt đầu** không vượt quá `fromIndex` |

Mỗi overload trả về `int`: **chỉ số đầu tiên của đoạn khớp** trên thang vị trí UTF-16 của chuỗi nguồn, hoặc `-1` khi không khớp.

Các overload `int` nhận Unicode code point. Với ký tự thông thường trong Basic Multilingual Plane (BMP), bạn có thể truyền một literal kiểu `char` vì Java tự nâng `char` thành `int`. Với code point ngoài BMP (ví dụ một số emoji), `int` cho phép tìm cả cặp surrogate như một code point; điều này được giải thích ở phần Unicode.

Các overload nhận `String str` đòi hỏi đối số `str` không phải `null`. Đối số `null` không phải «không tìm thấy»: nó gây `NullPointerException`. Nếu bạn cần xử lý trường hợp thiếu chuỗi tìm kiếm, hãy kiểm tra và quyết định chính sách ở phía gọi.

## 2. Tìm lần xuất hiện cuối cùng nhưng trả chỉ số tính từ đầu

Xét `"abracadabra"`:

```text
Giá trị: a b r a c a d a b r a
Chỉ số: 0 1 2 3 4 5 6 7 8 9 10
```

Ký tự `'a'` xuất hiện ở các vị trí `0`, `3`, `5`, `7` và `10`. Do đó:

- `indexOf('a')` trả về `0`: lần xuất hiện đầu tiên.
- `lastIndexOf('a')` trả về `10`: lần xuất hiện cuối cùng.
- `lastIndexOf("bra")` trả về `8`: đoạn `"bra"` cuối cùng bắt đầu tại chỉ số 8.
- `lastIndexOf("missing")` trả về `-1`: không có đoạn khớp.

Tên `lastIndexOf` mô tả cách **chọn kết quả**; chỉ số vẫn là chỉ số của Java tính từ đầu. Không được lấy `length() - result` để xem đó là vị trí trả về của phương thức. Nếu bài toán thật sự cần khoảng cách đến cuối, bạn mới tự tính từ chỉ số đã tìm được theo đúng ý nghĩa bài toán.

### Ví dụ 1: phân biệt `indexOf` và `lastIndexOf`

Tạo tệp `LastIndexOfBasicsDemo.java`:

```java
public class LastIndexOfBasicsDemo {
    public static void main(String[] args) {
        String text = "abracadabra";

        System.out.println(text.indexOf('a'));
        System.out.println(text.lastIndexOf('a'));
        System.out.println(text.indexOf("bra"));
        System.out.println(text.lastIndexOf("bra"));
        System.out.println(text.lastIndexOf("BRA"));
        System.out.println(text.lastIndexOf('z'));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac LastIndexOfBasicsDemo.java
java LastIndexOfBasicsDemo
```

Kết quả:

```text
0
10
1
8
-1
-1
```

`"BRA"` không khớp với `"bra"` vì các phép tìm này **phân biệt chữ hoa và chữ thường**. `lastIndexOf` không tự bỏ dấu, chuẩn hóa Unicode, tách từ hay áp dụng quy tắc ngôn ngữ tự nhiên.

## 3. `fromIndex`: tìm ngược từ vị trí được chỉ định

Hai overload có `fromIndex` cho phép giới hạn phần nguồn được xét:

```java
lastIndexOf(int ch, int fromIndex)
lastIndexOf(String str, int fromIndex)
```

Tìm theo hướng **giảm chỉ số**, từ khu vực gần `fromIndex` sang bên trái. Với một ký tự thuộc BMP, vị trí `fromIndex` được kiểm tra **bao gồm cả chính nó** nếu hợp lệ.

Ví dụ:

```text
String text = "abracadabra";

Index:   0 1 2 3 4 5 6 7 8 9 10
Text:    a b r a c a d a b r a
                 ^
           fromIndex = 6
```

Lệnh `text.lastIndexOf('a', 6)` không xét `'a'` ở 7 hay 10; nó đi ngược từ chỉ số 6 và gặp `'a'` ở 5, nên trả `5`. Tương tự, `text.lastIndexOf('a', 3)` trả `3` vì vị trí bắt đầu **được tính vào phạm vi tìm kiếm**.

Một điểm quan trọng hơn với *chuỗi con*: `fromIndex` giới hạn **vị trí bắt đầu của lần khớp**, không nhất thiết giới hạn tất cả ký tự của lần khớp ở bên trái `fromIndex`.

Ví dụ `"abcd".lastIndexOf("bcd", 1)` trả về `1` dù phần khớp có các ký tự ở chỉ số `2` và `3`. Đoạn khớp bắt đầu ở chỉ số 1 nên vẫn thỏa điều kiện.

### Ví dụ 2: vị trí bắt đầu được tính vào kết quả

Tạo `LastIndexOfFromIndexDemo.java`:

```java
public class LastIndexOfFromIndexDemo {
    public static void main(String[] args) {
        String text = "abracadabra";

        System.out.println(text.lastIndexOf('a', 6));
        System.out.println(text.lastIndexOf('a', 3));
        System.out.println(text.lastIndexOf('a', -1));
        System.out.println(text.lastIndexOf('a', 99));

        System.out.println(text.lastIndexOf("bra", 8));
        System.out.println(text.lastIndexOf("bra", 7));

        System.out.println("abcd".lastIndexOf("bcd", 1));
        System.out.println("abcd".lastIndexOf("bcd", 0));
    }
}
```

```bash
javac LastIndexOfFromIndexDemo.java
java LastIndexOfFromIndexDemo
```

Kết quả:

```text
5
3
-1
10
8
1
1
-1
```

Giải thích:

1. Khi `fromIndex` là 6, vị trí `'a'` gần nhất tính về bên trái là 5.
2. Khi `fromIndex` là 3, ký tự `'a'` ngay tại chỉ số 3 được tính.
3. Khi `fromIndex < 0`, không có vị trí bắt đầu hợp lệ nên kết quả là `-1`.
4. Khi `fromIndex` vượt quá chỉ số cuối, tìm ký tự bắt đầu hiệu quả từ vị trí cuối hợp lệ; kết quả là 10.
5. Với `"bra"`, vị trí bắt đầu 8 được tính trong `lastIndexOf("bra", 8)`; khi giới hạn `fromIndex = 7` thì tìm lần khớp trước đó ở 1.
6. `"bcd"` bắt đầu tại 1 nên `lastIndexOf("bcd", 1)` hợp lệ, dù đoạn khớp kéo dài qua chỉ số 3.

Nếu bạn định nghĩa «chỉ tìm trong **tiền tố** kết thúc tại một chỉ số», cần kiểm soát ranh giới đoạn khớp thay vì giả định `fromIndex` tự cắt dữ liệu đầu vào.

## 4. Một chuỗi con có thể khớp nhiều lần, kể cả chồng lấp

`lastIndexOf(String)` tìm toàn bộ dãy ký tự liên tiếp. Các lần xuất hiện chồng lấp vẫn là ứng viên hợp lệ.

Ví dụ `"bananana"` có đoạn `"ana"` bắt đầu ở các chỉ số `1`, `3` và `5`:

```text
b a n a n a n a
0 1 2 3 4 5 6 7
  └─ana─┘
      └─ana─┘
          └─ana─┘
```

Do đó:

- `lastIndexOf("ana")` → `5`.
- `lastIndexOf("ana", 4)` → `3`.
- `lastIndexOf("ana", 2)` → `1`.
- `lastIndexOf("ana", 0)` → `-1`.

### Ví dụ 3: tìm ngược nhiều lần

Tạo `ReverseSearchDemo.java`:

```java
public class ReverseSearchDemo {
    public static void main(String[] args) {
        String text = "bananana";

        System.out.println(text.indexOf("ana"));
        System.out.println(text.lastIndexOf("ana"));
        System.out.println(text.lastIndexOf("ana", 4));
        System.out.println(text.lastIndexOf("ana", 2));
        System.out.println(text.lastIndexOf("ana", 0));

        int at = text.lastIndexOf("ana");
        while (at >= 0) {
            System.out.println("match = " + at);
            at = text.lastIndexOf("ana", at - 1);
        }
    }
}
```

```bash
javac ReverseSearchDemo.java
java ReverseSearchDemo
```

Kết quả:

```text
1
5
3
1
-1
match = 5
match = 3
match = 1
```

Trong vòng lặp, `at - 1` bảo đảm lần tìm tiếp theo xét các **vị trí bắt đầu nhỏ hơn** vị trí vừa tìm thấy. Nếu gọi lại với đúng `at`, kết quả có thể là chính vị trí hiện tại và tạo vòng lặp vô hạn. Mẫu trên liệt kê được cả những lần khớp **chồng lấp**. Nếu muốn bỏ khớp chồng lấp, chính sách cập nhật chỉ số cần được thiết kế theo mục tiêu riêng.

## 5. Biên đặc biệt: chuỗi trống, vị trí âm và vị trí vượt độ dài

Các cạnh biên dễ bị bỏ qua nhất nằm ở overload tìm **chuỗi con rỗng**.

Với một chuỗi `s` có độ dài `n`:

- `s.lastIndexOf("")` trả về `n`, vì chuỗi rỗng có thể khớp ở ranh giới ngay sau ký tự cuối.
- `s.lastIndexOf("", fromIndex)` trả về `min(fromIndex, n)` nếu `fromIndex >= 0`; nếu `fromIndex < 0`, trả về `-1`.
- Với chuỗi tìm kiếm **không rỗng**, `fromIndex < 0` trả về `-1`; `fromIndex` quá lớn sẽ được giới hạn vào các vị trí bắt đầu có thể khớp.
- `s.lastIndexOf("longer-than-source")` trả về `-1` nếu toàn bộ chuỗi con không thể nằm trong `s`.
- `s.lastIndexOf((String) null)` gây `NullPointerException`, khác với kết quả `-1` khi không tìm thấy.

### Ví dụ 4: kiểm thử ranh giới một cách có chủ đích

Tạo `LastIndexOfEdgeCasesDemo.java`:

```java
public class LastIndexOfEdgeCasesDemo {
    public static void main(String[] args) {
        String text = "abc";

        System.out.println(text.lastIndexOf(""));
        System.out.println(text.lastIndexOf("", 1));
        System.out.println(text.lastIndexOf("", 100));
        System.out.println(text.lastIndexOf("", -1));

        System.out.println(text.lastIndexOf("abcd"));
        System.out.println(text.lastIndexOf('b', 100));
        System.out.println(text.lastIndexOf('b', -1));

        String empty = "";
        System.out.println(empty.lastIndexOf(""));
        System.out.println(empty.lastIndexOf('a'));
    }
}
```

```bash
javac LastIndexOfEdgeCasesDemo.java
java LastIndexOfEdgeCasesDemo
```

Kết quả:

```text
3
1
3
-1
-1
1
-1
0
-1
```

**Không đồng nhất chuỗi rỗng với «không tìm thấy».** Nếu một tham số tìm kiếm rỗng là không hợp lệ trong nghiệp vụ, hãy kiểm tra `needle.isEmpty()` trước khi gọi, thay vì dựa vào `lastIndexOf` để trả `-1`.

## 6. Chỉ số của `lastIndexOf` theo UTF-16, không phải vị trí ký tự hiển thị

`String` lưu các ký tự với ngữ nghĩa chỉ số **UTF-16 code unit**. Một ký tự Unicode ngoài BMP (ví dụ U+1F600) được biểu diễn bằng **hai `char`** — một cặp surrogate. Vì thế chỉ số trả về của `lastIndexOf` cũng là **vị trí code unit**, không phải vị trí đếm theo code point hoặc grapheme cluster.

Có hai cách tìm đáng phân biệt:

- `lastIndexOf(int ch)` có thể nhận một Unicode code point hợp lệ ngoài BMP, ví dụ `0x1F600`, và tìm cặp surrogate tương ứng. Kết quả là chỉ số UTF-16 của **high surrogate**, tức vị trí bắt đầu của cặp.
- `lastIndexOf(String str)` tìm chính xác dãy code unit có trong chuỗi con `str`. Có thể dùng một chuỗi chứa đủ cặp surrogate.
- `lastIndexOf(char)` không phải overload độc lập; truyền một `char` gọi overload `int` sau khi nâng kiểu. Một `char` đơn lẻ không thể biểu diễn toàn bộ code point ngoài BMP.

### Ví dụ 5: tìm emoji bằng Unicode code point

Tạo `LastIndexOfUnicodeDemo.java`:

```java
public class LastIndexOfUnicodeDemo {
    public static void main(String[] args) {
        String text = "A\uD83D\uDE00B\uD83D\uDE00";
        int emoji = 0x1F600;

        System.out.println(text.length());
        System.out.println(text.lastIndexOf(emoji));
        System.out.println(text.lastIndexOf(emoji, 3));
        System.out.println(text.lastIndexOf("\uD83D\uDE00"));
        System.out.println(text.lastIndexOf('\uDE00'));
        System.out.println(text.codePointCount(0, text.length()));
    }
}
```

```bash
javac LastIndexOfUnicodeDemo.java
java LastIndexOfUnicodeDemo
```

Kết quả:

```text
6
4
1
4
5
4
```

Chuỗi hiển thị là `A😀B😀`: bốn code point nhưng sáu UTF-16 code unit. Emoji đầu có high surrogate ở vị trí 1; emoji cuối ở vị trí 4. `lastIndexOf(emoji, 3)` tìm lùi từ khu vực chỉ số 3 và gặp emoji đầu ở 1. Tìm riêng low surrogate `'\uDE00'` cho kết quả 5, nhưng đó chỉ là **nửa sau của cặp surrogate cuối**, không đại diện cho cả emoji.

Lưu ý bổ sung: ngay cả khi đếm theo code point, một ký tự mà người dùng nhìn thấy có thể gồm nhiều code point (ví dụ ký tự kết hợp dấu hoặc emoji ghép). Nếu cần tìm kiếm theo **grapheme cluster** hay quy tắc ngôn ngữ tự nhiên, `lastIndexOf` không tự cung cấp chức năng đó.

## 7. Tình huống thực tế: lấy phần sau dấu phân cách cuối

Khi biết dữ liệu có cấu trúc đơn giản với dấu phân cách cố định, `lastIndexOf` kết hợp `substring` là giải pháp trực tiếp.

Giả sử tên tệp là `report.final.csv` và cần lấy phần mở rộng cuối:

```java
String filename = "report.final.csv";
int dot = filename.lastIndexOf('.');
String extension = dot >= 0 ? filename.substring(dot + 1) : "";
```

Kết quả `extension` là `"csv"`. `dot + 1` đúng vì `substring(beginIndex)` bắt đầu **sau** dấu chấm. Nếu dấu chấm không xuất hiện, `lastIndexOf` trả `-1`. Trong ví dụ trên, ta chủ động chọn giá trị rỗng cho trường hợp không có dấu chấm.

### Ví dụ 6: xử lý nhiều tên tệp

Tạo `FileExtensionDemo.java`:

```java
public class FileExtensionDemo {
    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    public static void main(String[] args) {
        System.out.println(extension("report.final.csv"));
        System.out.println(extension("README"));
        System.out.println("[" + extension("notes.") + "]");
        System.out.println(extension(".env"));
    }
}
```

```bash
javac FileExtensionDemo.java
java FileExtensionDemo
```

Kết quả:

```text
csv

[]
env
```

Trong đầu ra có **một dòng trống** sau `csv` vì `README` không có dấu chấm. `"notes."` có dấu chấm cuối nên `substring(dot + 1)` là chuỗi rỗng được in dưới dạng `[]`. Với `".env"`, hàm đơn giản coi `"env"` là phần mở rộng; nhiều quy ước về tệp ẩn lại coi `.env` là **tên tệp không có phần mở rộng**.

**Giới hạn của ví dụ:** đây là quy tắc xử lý *chuỗi tên tệp*, không phải parser đường dẫn đầy đủ. Một dấu chấm trong tên thư mục, đường dẫn URL, file ẩn hoặc quy ước nhiều phần mở rộng có thể cần chính sách khác. Trong ứng dụng cần xử lý đường dẫn đa nền tảng, cân nhắc `java.nio.file.Path` để tách tên tệp khỏi đường dẫn trước khi áp dụng logic phần mở rộng.

## 8. Tình huống thực tế: lấy đoạn sau dấu phân cách nhiều ký tự

Khi dấu phân cách có nhiều ký tự, cần cộng **độ dài của toàn bộ dấu phân cách**, không chỉ cộng 1.

```java
String message = "region::team::member";
String separator = "::";
int at = message.lastIndexOf(separator);
String last = at < 0 ? message : message.substring(at + separator.length());
```

Kết quả `last` là `"member"`. Nếu bạn cộng `at + 1`, kết quả sẽ chứa dư một dấu `':'` ở đầu.

### Ví dụ 7: tìm phần cuối và kiểm soát lỗi

Tạo `LastSegmentDemo.java`:

```java
public class LastSegmentDemo {
    static String lastSegment(String text, String separator) {
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator must not be empty");
        }

        int at = text.lastIndexOf(separator);
        return at < 0 ? text : text.substring(at + separator.length());
    }

    public static void main(String[] args) {
        System.out.println(lastSegment("a::b::c", "::"));
        System.out.println(lastSegment("single", "::"));
        System.out.println("[" + lastSegment("a::", "::") + "]");
    }
}
```

```bash
javac LastSegmentDemo.java
java LastSegmentDemo
```

Kết quả:

```text
c
single
[]
```

Hàm lựa chọn trả toàn bộ đầu vào khi không có dấu phân cách, và trả chuỗi rỗng nếu dấu phân cách nằm cuối. Đây là **chính sách nghiệp vụ** được viết rõ trong code, không phải tính năng tự động của `lastIndexOf`. Hàm cũng chặn dấu phân cách rỗng vì `lastIndexOf("")` có ngữ nghĩa riêng, thường không phù hợp để tách phân đoạn.

## 9. So sánh `indexOf` và `lastIndexOf` khi lựa chọn cách tìm

| Nhu cầu | Lựa chọn phù hợp |
| --- | --- |
| Tìm vị trí **đầu tiên** của ký tự hoặc chuỗi con | `indexOf(...)` |
| Tìm vị trí **cuối cùng** của ký tự hoặc chuỗi con | `lastIndexOf(...)` |
| Tìm lần xuất hiện đầu ở vị trí ≥ `fromIndex` | `indexOf(..., fromIndex)` |
| Tìm lần xuất hiện cuối có vị trí bắt đầu ≤ `fromIndex` | `lastIndexOf(..., fromIndex)` |
| Kiểm tra có chứa một chuỗi con cụ thể hay không | `contains(...)` hoặc `indexOf(...) >= 0` |
| Kiểm tra tiền tố/hậu tố cụ thể | `startsWith(...)` / `endsWith(...)` |
| Tìm theo biểu thức chính quy, nhóm bắt, tập ký tự | `Pattern`/`Matcher` và regex thích hợp |

`lastIndexOf` tìm **literal** ký tự hoặc chuỗi con. Ví dụ `lastIndexOf(".")` tìm dấu chấm thực, không diễn giải `"."` là «mọi ký tự» như trong regex. Khi chỉ cần dấu phân cách cuối, không cần regex.

`lastIndexOf` có thể được JVM tối ưu ở mức implementation, nhưng API không cam kết một độ phức tạp hay kiểu bộ nhớ duy nhất cho mọi JVM. Không nên đưa ra kết luận hiệu năng tuyệt đối nếu chưa đo trên dữ liệu và runtime thực tế.

## 10. Những lỗi dễ mắc

**Lỗi 1 — tưởng kết quả đếm từ cuối.** `lastIndexOf` trả **chỉ số từ đầu chuỗi**. Với `"abcabc"`, `lastIndexOf("abc") == 3`, không phải 0.

**Lỗi 2 — dùng `fromIndex` như chỉ số loại trừ.** Khác với `endIndex` trong `substring`, `fromIndex` trong `lastIndexOf` được tính **bao gồm**. Muốn bỏ vị trí khớp hiện tại, thường cần gọi lại với `at - 1`.

**Lỗi 3 — nghĩ chuỗi con phải nằm hoàn toàn bên trái `fromIndex`.** `fromIndex` hạn chế **vị trí bắt đầu**, không nhất thiết hạn chế vị trí kết thúc đoạn khớp. `"abcd".lastIndexOf("bcd", 1)` trả 1.

**Lỗi 4 — không kiểm tra `-1`.** `substring(text.lastIndexOf("::") + 2)` có thể xử lý sai khi dấu phân cách vắng mặt vì `-1 + 2 == 1`. Hãy kiểm tra vị trí khớp trước khi cắt chuỗi.

**Lỗi 5 — cộng 1 cho dấu phân cách nhiều ký tự.** Với `"::"`, phải cộng `separator.length()` khi lấy phần sau đoạn khớp.

**Lỗi 6 — bỏ qua chuỗi rỗng.** `lastIndexOf("")` trả độ dài chuỗi, không trả `-1`. Hãy tự định nghĩa có cho phép tìm chuỗi rỗng hay không.

**Lỗi 7 — giả định tìm kiếm không phân biệt hoa thường.** `"Java".lastIndexOf("java") == -1`. Nếu muốn xử lý không phân biệt hoa thường, cần quy tắc so sánh riêng, đặc biệt khi có Unicode và locale.

**Lỗi 8 — coi `char` là mọi ký tự Unicode.** Một code point ngoài BMP chiếm hai UTF-16 code unit. Dùng overload `int` với code point đúng, hoặc dùng chuỗi con hoàn chỉnh, và hiểu chỉ số trả về.

**Lỗi 9 — dùng `lastIndexOf` như parser ngữ nghĩa.** Tìm dấu `'.'` cuối chỉ là thao tác trên chuỗi; nó không tự biết phần mở rộng tệp, định dạng URL, escape sequence hay quy tắc đường dẫn.

**Lỗi 10 — quên rằng chuỗi nguồn bất biến.** `lastIndexOf` chỉ đọc `String` và trả vị trí, không sửa nội dung hay dịch con trỏ tìm kiếm bên trong chuỗi.

## 11. Tự kiểm tra

### Câu 1: tìm ngược có chỉ số bắt đầu

Cho:

```java
String text = "one-two-two";
System.out.println(text.lastIndexOf("two"));
System.out.println(text.lastIndexOf("two", 5));
System.out.println(text.lastIndexOf('-', 6));
```

Đáp án:

```text
8
4
3
```

`"two"` xuất hiện tại 4 và 8; tìm từ vị trí 5 chỉ có thể chọn khớp bắt đầu ở 4. Dấu `'-'` ở chỉ số 3 và 7; tìm ngược từ 6 gặp chỉ số 3.

### Câu 2: chuỗi tìm kiếm rỗng

Cho:

```java
String text = "xyz";
System.out.println(text.lastIndexOf(""));
System.out.println(text.lastIndexOf("", 1));
System.out.println(text.lastIndexOf("", -1));
```

Đáp án:

```text
3
1
-1
```

Vị trí ranh giới ngay sau ký tự cuối cũng là một vị trí khớp hợp lệ của chuỗi rỗng.

### Câu 3: lấy hậu tố sau dấu hai chấm cuối

Với `String text = "a:b:c";`, viết phép cắt để lấy `"c"`.

Lời giải:

```java
int separator = text.lastIndexOf(':');
String tail = separator < 0 ? text : text.substring(separator + 1);
```

Biến `tail` có giá trị `"c"`. Đây là một đoạn mã minh họa đặt trong phương thức có biến `text` đã khai báo.

### Câu 4: các lần khớp chồng lấp

Với `"aaaa"`, đoạn `"aa"` bắt đầu ở các chỉ số `0`, `1`, `2`. Vì vậy `lastIndexOf("aa")` trả `2`, còn `lastIndexOf("aa", 1)` trả `1`. Tính chồng lấp không khiến một vị trí khớp hợp lệ bị loại bỏ.

## Ghi nhớ

**`lastIndexOf` tìm từ phải sang trái nhưng trả về chỉ số bắt đầu tính từ trái sang phải.** Overload với `fromIndex` tìm các lần khớp có vị trí bắt đầu không lớn hơn ranh giới đó; ranh giới được tính **bao gồm**. Kết quả `-1` nghĩa là không tìm thấy, còn tìm chuỗi rỗng là một trường hợp đặc biệt có kết quả hợp lệ. Khi dùng chỉ số để cắt đoạn văn bản, hãy kiểm tra `-1`, cộng đúng độ dài dấu phân cách, và nhớ rằng các chỉ số của `String` dùng UTF-16 code unit.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-006">Giá trị -1 và lỗi dùng kết quả tìm kiếm làm chỉ số</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị -1 và lỗi dùng kết quả tìm kiếm làm chỉ số trong Java String

Khi tìm một ký tự hoặc một đoạn văn bản bằng `String.indexOf(...)` hay `String.lastIndexOf(...)`, kết quả là **vị trí bắt đầu khớp theo chỉ số UTF-16**, hoặc **`-1` nếu không tìm thấy**. Giá trị `-1` là một *giá trị báo hiệu* (*sentinel*), không phải vị trí của ký tự trong chuỗi.

Lỗi thường gặp là đưa ngay kết quả tìm kiếm vào `substring(...)`, `charAt(...)` hoặc phép tính chỉ số mà chưa kiểm tra. Có trường hợp Java ném ngoại lệ rõ ràng; có trường hợp phép tính biến `-1` thành `0` và trả về nội dung **sai nhưng hoàn toàn không báo lỗi**. Để xử lý đúng, hãy hiểu hợp đồng của hàm tìm kiếm trước, kiểm tra trạng thái “không tìm thấy”, rồi mới tính ranh giới cần trích xuất.

## 1. `indexOf` và `lastIndexOf` trả giá trị gì?

Những overload quen thuộc của `String` gồm:

```java
text.indexOf('a');             // tìm ký tự/code point
text.indexOf("abc");           // tìm chuỗi con
text.indexOf("abc", fromIndex); // tìm từ vị trí chỉ định
text.lastIndexOf('a');         // vị trí khớp cuối cùng
text.lastIndexOf("abc");       // vị trí bắt đầu khớp cuối cùng
```

Đây là **đoạn minh họa các lời gọi**, giả sử biến `text` có kiểu `String` và không null. `indexOf` tìm từ trái sang phải; `lastIndexOf` tìm lần xuất hiện cuối cùng (hoặc ngược về phía đầu đối với overload có `fromIndex`). Cả hai trả về **`int`**, với hai nhóm kết quả:

- **Tìm thấy:** trả một offset UTF-16 **không âm**. Giá trị `0` là kết quả hợp lệ, nghĩa là khớp ngay từ đầu chuỗi. Nếu tìm đoạn dài hơn 0, vị trí khớp không thể vượt quá `text.length() - needle.length()`.
- **Không tìm thấy:** trả **`-1`**, không có vị trí tương ứng trong chuỗi.

Ví dụ với `"bananas"` có độ dài `7`:

| Lời gọi | Kết quả | Giải thích |
|---|---:|---|
| `"bananas".indexOf('a')` | `1` | Chữ `a` đầu tiên ở vị trí 1 |
| `"bananas".lastIndexOf('a')` | `5` | Chữ `a` cuối cùng ở vị trí 5 |
| `"bananas".indexOf("na")` | `2` | Khớp `"na"` đầu tiên bắt đầu ở 2 |
| `"bananas".lastIndexOf("na")` | `4` | Khớp cuối cùng bắt đầu ở 4 |
| `"bananas".indexOf("xy")` | `-1` | Không chứa `"xy"` |
| `"bananas".indexOf("ba")` | `0` | Khớp ở đầu chuỗi; **không phải** thất bại |
| `"bananas".indexOf("")` | `0` | Chuỗi tìm kiếm rỗng khớp tại ranh giới đầu |
| `"bananas".lastIndexOf("")` | `7` | Chuỗi tìm kiếm rỗng khớp tại ranh giới cuối |

**Phân biệt `0` và `-1`:** Điều kiện `if (index > 0)` sẽ bỏ sót khớp ở đầu; điều kiện `if (index == 0)` không có nghĩa là không tìm thấy. Để kiểm tra có khớp, dùng `index >= 0` hoặc `index != -1`; để kiểm tra thất bại, dùng `index == -1` hoặc `index < 0` khi giá trị xuất phát từ các API tìm kiếm này.

### Ví dụ chạy được: đọc đúng vị trí và sentinel

Lưu thành **`SearchResultDemo.java`**:

```java
public class SearchResultDemo {
    public static void main(String[] args) {
        String text = "bananas";

        System.out.println("first a: " + text.indexOf('a'));
        System.out.println("last a: " + text.lastIndexOf('a'));
        System.out.println("first na: " + text.indexOf("na"));
        System.out.println("last na: " + text.lastIndexOf("na"));
        System.out.println("missing xy: " + text.indexOf("xy"));
        System.out.println("at beginning: " + text.indexOf("ba"));
        System.out.println("empty indexOf: " + text.indexOf(""));
        System.out.println("empty lastIndexOf: " + text.lastIndexOf(""));
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac SearchResultDemo.java
java SearchResultDemo
```

**Stdout:**

```text
first a: 1
last a: 5
first na: 2
last na: 4
missing xy: -1
at beginning: 0
empty indexOf: 0
empty lastIndexOf: 7
```

Chuỗi rỗng là **trường hợp đặc biệt quan trọng**. Tìm `""` không có nghĩa là tìm thất bại: `indexOf("")` cho `0`, `lastIndexOf("")` cho `length()`. Vì vậy, khi thiết kế chức năng “tìm lần xuất hiện của một từ khóa”, hãy quyết định rõ liệu từ khóa rỗng có được chấp nhận hay phải bị từ chối ngay từ đầu.

## 2. Vì sao `-1` nguy hiểm khi dùng như một chỉ số?

Nhiều API truy cập chuỗi chỉ nhận chỉ số không âm:

| API | Miền chỉ số hợp lệ | Nếu đưa trực tiếp `-1` |
|---|---|---|
| `charAt(index)` | `0 <= index < length()` | Ném `IndexOutOfBoundsException` |
| `substring(begin)` | `0 <= begin <= length()` | Ném `IndexOutOfBoundsException` |
| `substring(begin, end)` | `0 <= begin <= end <= length()` | Ném `IndexOutOfBoundsException` nếu begin/end là `-1` |
| `subSequence(begin, end)` | Tương tự `substring(begin, end)` | Phạm vi sai; ném ngoại lệ |
| `codePointAt(index)` | `0 <= index < length()` | Ném `IndexOutOfBoundsException` |
| `substring(position + 1)` | Tùy kết quả phép cộng | **Có thể hợp lệ nhưng sai ý nghĩa** nếu `position == -1` |

Điểm cuối cùng đặc biệt dễ bỏ qua:

```java
String input = "name=value";
int colon = input.indexOf(':'); // -1

// Sai: từ 0 tới -1, gây ngoại lệ.
String beforeColon = input.substring(0, colon);

// Cũng sai về ý nghĩa: -1 + 1 = 0.
// Không có ngoại lệ; kết quả lại là toàn chuỗi "name=value".
String afterColon = input.substring(colon + 1);
```

Đây là **mã minh họa hai lỗi**, không phải chương trình để chạy nguyên khối. Một lỗi biểu hiện ngay bằng ngoại lệ; lỗi còn lại nguy hiểm vì giá trị trông hợp lệ và dễ lọt qua kiểm thử hời hợt.

**Nguyên tắc:** kiểm tra **kết quả tìm kiếm gốc trước khi cộng/trừ**. Đừng dùng `index + 1`, `index - 1`, `substring(index)` hoặc `charAt(index)` trước khi bảo đảm `index >= 0` và ranh giới phát sinh vẫn hợp lệ.

## 3. Ví dụ đối chiếu: ngoại lệ và lỗi trả sai âm thầm

**`SearchIndexPitfallDemo.java`**

```java
public class SearchIndexPitfallDemo {
    public static void main(String[] args) {
        String input = "name=value";
        int colon = input.indexOf(':');

        System.out.println("colon: " + colon);

        try {
            System.out.println(input.substring(0, colon));
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("substring(0, -1): invalid");
        }

        try {
            System.out.println(input.charAt(colon));
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("charAt(-1): invalid");
        }

        String misleading = input.substring(colon + 1);
        System.out.println("substring(-1 + 1): " + misleading);
    }
}
```

```bash
javac SearchIndexPitfallDemo.java
java SearchIndexPitfallDemo
```

**Stdout:**

```text
colon: -1
substring(0, -1): invalid
charAt(-1): invalid
substring(-1 + 1): name=value
```

Ví dụ chủ động bắt ngoại lệ để minh họa lỗi. Trong mã nghiệp vụ, không nên dùng bắt ngoại lệ làm bước kiểm tra thông thường cho trường hợp dấu phân cách có thể không tồn tại: `indexOf()` đã trả `-1` để báo rõ tình huống đó.

Một bẫy liên quan: nếu viết `text.substring(found, found + keyword.length())` khi `found == -1`, biểu thức có thể gây ngoại lệ; nếu `keyword.length() == 1` thì `found + keyword.length() == 0` và vẫn sai vì ranh giới bắt đầu `-1`. Không có phép cộng nào khiến `found == -1` trở thành bằng chứng đã tìm thấy.

## 4. Quy trình chuẩn: kiểm tra trước, tính chỉ số sau

Để lấy phần trước/sau một dấu phân cách, tách ba bước:

1. **Tìm** dấu phân cách bằng `indexOf()` hoặc `lastIndexOf()`.
2. **Kiểm tra** kết quả có phải `-1` không.
3. **Chỉ khi tìm thấy**, sử dụng vị trí để gọi `substring()`.

Ví dụ phân tích chuỗi `key=value`:

**`SafeDelimiterDemo.java`**

```java
public class SafeDelimiterDemo {
    private static void parse(String line) {
        int separator = line.indexOf('=');

        if (separator < 0) {
            System.out.println("Missing separator: " + line);
            return;
        }

        String key = line.substring(0, separator);
        String value = line.substring(separator + 1);

        System.out.println("key=<" + key + ">, value=<" + value + ">");
    }

    public static void main(String[] args) {
        parse("name=Java");
        parse("=value");
        parse("key=");
        parse("missing");
        parse("a=b=c");
    }
}
```

```bash
javac SafeDelimiterDemo.java
java SafeDelimiterDemo
```

**Stdout:**

```text
key=<name>, value=<Java>
key=<>, value=<value>
key=<key>, value=<>
Missing separator: missing
key=<a>, value=<b=c>
```

Cần hiểu các trường hợp biên:

- Dấu phân cách ở **đầu** có `separator == 0`. Đây là **đã tìm thấy**, nên chuỗi trước dấu là `""`.
- Dấu phân cách ở **cuối** có `separator == length() - 1`, vì thế `substring(separator + 1)` tương đương `substring(length())`, là phép trích xuất **hợp lệ** trả về `""`.
- Không có dấu phân cách thì `separator == -1`. Ví dụ xử lý bằng thông báo riêng; không tiếp tục tính ranh giới.
- Nếu có nhiều dấu `=`, `indexOf('=')` lấy dấu **đầu tiên**, phần bên phải có thể vẫn chứa dấu `=`. Nếu yêu cầu lấy dấu cuối cùng, chuyển sang `lastIndexOf('=')` và giữ nguyên bước kiểm tra `-1`.
- Bài toán có thể yêu cầu **key không rỗng**, **value không rỗng**, hoặc chỉ chấp nhận đúng một dấu `=`. Đó là các kiểm tra **nghiệp vụ bổ sung**; sự tồn tại của dấu phân cách không tự bảo đảm các điều kiện ấy.

### Trường hợp không tìm thấy cần một hợp đồng rõ ràng

Thay vì luôn in thông báo, một hàm thư viện có thể:

- Trả về `Optional<String>` hoặc một kiểu kết quả nêu rõ “có/không có”.
- Ném `IllegalArgumentException` nếu dấu phân cách bắt buộc phải có và thiếu là dữ liệu sai.
- Trả `null` nếu API quy định như vậy, nhưng người gọi phải biết và kiểm tra `null`.

Không nên trả `""` vô điều kiện để thay cho “không có dấu”: dấu ở cuối chuỗi cũng cho **giá trị rỗng hợp lệ**. Nếu hai trạng thái có ý nghĩa khác nhau, cần biểu diễn chúng khác nhau.

## 5. Từ vị trí khớp đến phạm vi chuỗi con

Với `needle` là từ khóa **không rỗng** và `position = text.indexOf(needle)`:

```text
Nếu position == -1:
    Không có vùng khớp; không được tự tạo chỉ số con.

Nếu position >= 0:
    begin = position
    end   = position + needle.length()
    [begin, end) nằm trong text.
```

`indexOf(String)` trả vị trí bắt đầu của toàn bộ chuỗi con khớp, vì vậy khi tìm thấy, Java bảo đảm đủ code unit để có đoạn `needle`. Độ dài ở đây là `needle.length()` theo UTF-16, cùng đơn vị với chỉ số mà `indexOf` trả về.

Ví dụ:

**`MatchRangeDemo.java`**

```java
public class MatchRangeDemo {
    private static void showMatch(String text, String needle) {
        if (needle.isEmpty()) {
            System.out.println("Empty keyword is not supported");
            return;
        }

        int position = text.indexOf(needle);
        if (position < 0) {
            System.out.println("Not found: " + needle);
            return;
        }

        int end = position + needle.length();
        String found = text.substring(position, end);
        System.out.println("match [" + position + "," + end + "): " + found);
    }

    public static void main(String[] args) {
        showMatch("Java String", "String");
        showMatch("Java String", "Java");
        showMatch("Java String", "Kotlin");
        showMatch("Java String", "");
    }
}
```

```bash
javac MatchRangeDemo.java
java MatchRangeDemo
```

**Stdout:**

```text
match [5,11): String
match [0,4): Java
Not found: Kotlin
Empty keyword is not supported
```

Trường hợp `needle.isEmpty()` được **chủ động từ chối theo hợp đồng ví dụ**, vì nhu cầu là khớp một từ khóa có nội dung. Đây không phải hạn chế của `indexOf("")`; Java cho phép tìm chuỗi rỗng.

Khi biến `position` đã được kiểm tra, việc trích xuất cả vùng khớp hữu ích cho tô sáng, thay thế có kiểm soát, ghi nhận offset hoặc phân tích cú pháp. Nhưng nếu muốn so nội dung, `text.contains(needle)` hoặc `text.indexOf(needle) >= 0` có thể đủ, không cần `substring`.

## 6. Lấy phần mở rộng tên tệp bằng `lastIndexOf`

Một ứng dụng hay tìm dấu chấm cuối cùng rồi lấy phần sau:

```java
int dot = filename.lastIndexOf('.');
String extension = filename.substring(dot + 1);
```

Nếu không có dấu chấm, `dot == -1`, làm `dot + 1 == 0` và biến `extension` nhận **toàn bộ tên tệp** thay vì một phần mở rộng. Không có ngoại lệ, nhưng nghĩa dữ liệu sai.

Ngay cả khi có dấu, cần phân biệt:

- `"report.txt"`: dấu ở giữa, phần mở rộng `"txt"`.
- `"report"`: không có dấu, không có phần mở rộng.
- `".env"`: dấu ở đầu; nhiều hệ thống xem đây là tên file ẩn **không có phần mở rộng**.
- `"report."`: dấu ở cuối; phần sau rỗng, chính sách ứng dụng có thể coi là không có phần mở rộng.
- `"archive.tar.gz"`: dùng dấu cuối nên phần mở rộng là `"gz"`, không phải `"tar.gz"`.

Ví dụ dưới đây quy định **chỉ trả phần mở rộng nếu dấu chấm nằm giữa tên tệp**.

**`FileExtensionDemo.java`**

```java
import java.util.Optional;

public class FileExtensionDemo {
    private static Optional<String> extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(name.substring(dot + 1));
    }

    private static void show(String name) {
        System.out.println(name + " -> "
                + extensionOf(name).orElse("(none)"));
    }

    public static void main(String[] args) {
        show("report.txt");
        show("report");
        show(".env");
        show("report.");
        show("archive.tar.gz");
    }
}
```

```bash
javac FileExtensionDemo.java
java FileExtensionDemo
```

**Stdout:**

```text
report.txt -> txt
report -> (none)
.env -> (none)
report. -> (none)
archive.tar.gz -> gz
```

Đây chỉ là quy tắc phân tích **chuỗi tên tệp đơn giản**, không phải trình phân tích đường dẫn đầy đủ hoặc quy tắc thống nhất của mọi hệ điều hành. Với đường dẫn có thư mục, dấu chấm trong tên thư mục hay nhiều loại file đặc biệt, cần xử lý tên tệp riêng và thống nhất chính sách ứng dụng.

## 7. Các bẫy với `fromIndex`, vị trí đầu và từ khóa rỗng

Overload có `fromIndex` dùng để điều khiển nơi bắt đầu tìm, nhưng không biến `-1` thành một chỉ số hợp lệ để trích chuỗi.

Ví dụ trên `"bananas"`:

| Biểu thức | Kết quả | Ý nghĩa |
|---|---:|---|
| `indexOf("na", 0)` | `2` | Khớp đầu tiên từ vị trí 0 |
| `indexOf("na", 3)` | `4` | Bỏ qua khớp bắt đầu ở 2 |
| `indexOf("na", 5)` | `-1` | Không còn khớp bắt đầu tại/từ 5 |
| `indexOf("na", -10)` | `2` | Với `indexOf`, `fromIndex` âm được xử lý như 0 |
| `indexOf("na", 99)` | `-1` | Vị trí bắt đầu tìm đã ở sau mọi khớp |
| `lastIndexOf("na", 3)` | `2` | Khớp cuối cùng có vị trí bắt đầu không vượt 3 |
| `lastIndexOf("na", -1)` | `-1` | Không có vị trí bắt đầu không âm <= -1 |
| `lastIndexOf("na", 99)` | `4` | Vẫn lấy khớp cuối cùng hiện có |

**Chú ý sự khác nhau:** `indexOf(..., -10)` có thể tìm ra kết quả vì cách nó xử lý offset bắt đầu âm, còn `lastIndexOf(..., -1)` trả `-1`. Đây là hợp đồng của **phương thức tìm kiếm**, không phải giấy phép truyền chỉ số âm vào `substring()` hay `charAt()`.

Ngoài ra:

- `text.indexOf("")` bằng `0` và `text.lastIndexOf("")` bằng `text.length()` đối với dạng **không truyền fromIndex**.
- Với `indexOf("", fromIndex)`, Java giới hạn ranh giới kết quả vào đoạn `[0, length()]`; tìm chuỗi rỗng thường vẫn có kết quả kể cả khi offset bắt đầu nằm ngoài độ dài.
- Với `lastIndexOf("", fromIndex)`, ranh giới lớn hơn độ dài được đưa về `length()`; nếu `fromIndex < 0` thì trả `-1`.
- Nếu thuật toán lặp đang tìm tất cả các khớp, một `needle` rỗng cần xử lý đặc biệt. Nếu không, việc tăng chỉ số qua `length()` nhưng tiếp tục gọi `indexOf("", fromIndex)` có thể dẫn đến **vòng lặp vô hạn** khi kết quả cứ là `length()`.

Nếu người gọi chỉ cần **biết có hay không có**, dùng `text.contains(needle)` là cách trực tiếp, rõ ý định, nhưng cần biết `contains("")` là `true`. Khi cần offset để trích xuất, `indexOf`/`lastIndexOf` vẫn cần thiết.

## 8. Tìm tất cả vị trí: kiểm tra `-1` và tiến chỉ số đúng

Để tìm nhiều lần xuất hiện, thường dùng vòng lặp gọi lại `indexOf(needle, fromIndex)`. Luôn:

1. Quy định từ khóa không rỗng hoặc có nhánh xử lý riêng cho chuỗi rỗng.
2. Kiểm tra `position < 0` **trước khi** sử dụng hoặc cộng vào vị trí khớp.
3. Tăng vị trí tìm tiếp để thuật toán có tiến triển.
4. Chọn có cho phép các khớp **chồng lấn** hay không.

Ví dụ với `"aaaa"` và `"aa"`: vị trí khớp chồng lấn là `0, 1, 2`. Nếu tăng `fromIndex` theo `needle.length() == 2` sau mỗi khớp, thuật toán chỉ lấy `0, 2` (không chồng lấn).

**`FindAllMatchesDemo.java`**

```java
public class FindAllMatchesDemo {
    private static void findAllOverlapping(String text, String needle) {
        if (needle.isEmpty()) {
            throw new IllegalArgumentException("needle must not be empty");
        }

        int from = 0;
        while (true) {
            int index = text.indexOf(needle, from);
            if (index == -1) {
                return;
            }

            System.out.println("match at: " + index);
            from = index + 1;
        }
    }

    public static void main(String[] args) {
        findAllOverlapping("aaaa", "aa");
    }
}
```

```bash
javac FindAllMatchesDemo.java
java FindAllMatchesDemo
```

**Stdout:**

```text
match at: 0
match at: 1
match at: 2
```

Ở đây tăng `from = index + 1` để cho phép chồng lấn. Vì `needle` không rỗng, mỗi vị trí khớp nằm trước cuối chuỗi; `from` tăng và vòng lặp dừng khi không còn khớp. Nếu ứng dụng yêu cầu khớp không chồng lấn, đặt `from = index + needle.length()` sau mỗi kết quả và giữ nguyên kiểm tra `-1`.

## 9. Chỉ số tìm kiếm là offset UTF-16, không phải số “ký tự nhìn thấy”

Các overload tìm kiếm của `String` trả **offset UTF-16**, thống nhất với `substring`. Điều này quan trọng với emoji, ký tự ngoài BMP, dấu kết hợp và một số tổ hợp ký tự.

Ví dụ `"A😊B😊"` có `length() == 6`: `A` chiếm một code unit, mỗi emoji 😊 chiếm hai code unit và `B` chiếm một. Hai emoji lần lượt bắt đầu tại offset `1` và `4`, dù người đọc thấy chúng ở vị trí ký tự thứ hai và thứ tư nếu đếm theo phần tử hiển thị.

**`UnicodeSearchIndexDemo.java`**

```java
public class UnicodeSearchIndexDemo {
    public static void main(String[] args) {
        String smile = "\uD83D\uDE0A";
        String text = "A" + smile + "B" + smile;

        int first = text.indexOf(smile);
        int last = text.lastIndexOf(smile);
        int missing = text.indexOf("X");

        System.out.println("units: " + text.length());
        System.out.println("points: "
                + text.codePointCount(0, text.length()));
        System.out.println("first smile: " + first);
        System.out.println("last smile: " + last);
        System.out.println("missing X: " + missing);
        System.out.println("complete smile: "
                + text.substring(first, first + smile.length()).equals(smile));

        String broken = text.substring(first, first + 1);
        System.out.println("broken high surrogate: "
                + Character.isHighSurrogate(broken.charAt(0)));
    }
}
```

```bash
javac UnicodeSearchIndexDemo.java
java UnicodeSearchIndexDemo
```

**Stdout:**

```text
units: 6
points: 4
first smile: 1
last smile: 4
missing X: -1
complete smile: true
broken high surrogate: true
```

**Giải thích:** `indexOf(smile)` trả offset bắt đầu của toàn bộ cặp surrogate; `smile.length() == 2` nên phạm vi `[first, first + 2)` lấy đầy đủ emoji. Nếu chỉ cắt `[first, first + 1)`, kết quả là một high surrogate đứng lẻ dù ranh giới vẫn đúng về mặt số học.

Nếu chỉ số dùng để đánh dấu vị trí trong giao diện hay đếm code point, hãy chuyển đổi đơn vị một cách tường minh. Dù trích đúng các code point, một tổ hợp chữ và dấu hoặc emoji ghép có thể gồm nhiều code point; khi yêu cầu giữ nguyên “ký tự người dùng cảm nhận”, cần phân đoạn theo grapheme cluster.

## 10. Phân biệt kết quả tìm kiếm, chỉ số không hợp lệ và đầu vào null

Ba loại tình huống này cần xử lý khác nhau:

- **Không tìm thấy:** `indexOf`/`lastIndexOf` trả `-1`. Đây là **kết quả bình thường** của một phép tìm kiếm, không phải ngoại lệ.
- **Dùng chỉ số không hợp lệ để truy cập/trích xuất:** `charAt(-1)`, `substring(0, -1)` hoặc `substring(length() + 1)` ném một loại `IndexOutOfBoundsException`.
- **Không có đối tượng `String` để gọi phương thức:** nếu biến `text == null` mà gọi `text.indexOf("a")`, chương trình ném `NullPointerException`. Không thể coi null là “không tìm thấy” trừ khi API bao ngoài chủ động thiết kế như vậy.

Nếu chỉ cần kiểm tra chuỗi có chứa đoạn văn bản, `contains()` là một lựa chọn diễn đạt rõ ràng:

```java
String text = "Java String";
boolean found = text.contains("String"); // true
boolean absent = text.contains("Kotlin"); // false
```

Đây là ví dụ minh họa, không phải một lớp độc lập. `contains()` trả boolean, không có sentinel `-1`; tuy nhiên nó cũng không cho vị trí để cắt chuỗi. Nếu `text` là null, `text.contains(...)` vẫn ném `NullPointerException`.

Ngoài ra, **đừng kiểm tra kết quả bằng `if (index)`** như trong vài ngôn ngữ khác: Java không tự chuyển `int` thành `boolean` nên câu đó không biên dịch. Viết rõ `if (index >= 0)` hoặc `if (index == -1)`.

## 11. Sai lầm điển hình và cách phòng tránh

| Sai lầm | Hậu quả | Cách xử lý |
|---|---|---|
| `substring(0, text.indexOf(':'))` | Nếu không có `:`, `end == -1` và ném ngoại lệ | Lưu kết quả vào biến, kiểm tra `>= 0` trước |
| `substring(text.indexOf(':') + 1)` | Nếu không thấy `:`, trả toàn chuỗi từ offset 0 | Kiểm tra sentinel **trước khi cộng 1** |
| `if (index > 0)` để kiểm tra tìm thấy | Bỏ sót khớp ở vị trí `0` | Dùng `index >= 0` |
| `charAt(indexOf(...))` | Có thể nhận chỉ số `-1` và ném ngoại lệ | Chỉ truy cập sau khi chắc chắn có khớp |
| Lấy phần mở rộng bằng `substring(lastIndexOf('.') + 1)` | Không có dấu chấm thì trả toàn bộ tên | Kiểm tra `dot` và ràng buộc vị trí hợp lệ |
| Lặp `indexOf("", fromIndex)` không có nhánh riêng | Có thể không tiến triển và lặp mãi ở cuối | Xử lý từ khóa rỗng tường minh |
| Coi `indexOf(...) == 0` là thất bại | Bỏ qua từ khóa ở đầu | `0` là vị trí hợp lệ |
| Nghĩ mọi kết quả `-1` đều là ngoại lệ | Nhầm hợp đồng tìm kiếm với hợp đồng trích xuất | Tìm kiếm trả sentinel; dùng sai chỉ số mới gây lỗi |
| Dùng index tìm kiếm như số thứ tự code point | Sai vị trí khi chuỗi chứa ký tự ngoài BMP | Ghi rõ offset UTF-16 và chuyển đổi khi cần |
| Xem chuỗi rỗng sau dấu phân cách là “không tìm thấy” | Gộp hai trạng thái nghiệp vụ khác nhau | Phân biệt sentinel `-1` và `substring(length()) == ""` |

## 12. Tự kiểm tra

Với `text = "a:b:c"`, dự đoán kết quả sau:

```java
String text = "a:b:c";

int first = text.indexOf(':');
int last = text.lastIndexOf(':');
int absent = text.indexOf('/');

System.out.println(first);
System.out.println(last);
System.out.println(absent);
System.out.println(text.substring(first + 1));
System.out.println(text.substring(last + 1));
System.out.println(text.substring(absent + 1));
```

**Stdout:**

```text
1
3
-1
b:c
c
a:b:c
```

Ba dòng cuối minh họa điều quan trọng: cả `first + 1` và `last + 1` đều hợp lệ **vì có dấu `:`**, trong khi `absent + 1 == 0` tạo ra chuỗi toàn phần mà không ném ngoại lệ. Nếu kết quả có thể không tìm thấy, không được viết dòng cuối theo mẫu đó trong mã thật.

**Bài luyện tập ngắn:** viết một hàm lấy phần trước dấu `@` của địa chỉ nhập vào. Thử các trường hợp `"a@b"`, `"@b"`, `"a@"`, `"abc"` và `""`. Xác định riêng trường hợp thiếu dấu, trường hợp phần trước rỗng và trường hợp phần sau rỗng; đừng để phép cộng hoặc `substring` tự quyết định chính sách nghiệp vụ.

**Quy tắc cần ghi nhớ:** khi một API trả vị trí tìm kiếm, hãy đọc `-1` là **“không có vị trí”**, `0` là **“có khớp tại đầu”**, còn mọi giá trị dương hợp lệ là **offset UTF-16 của khớp**. Kiểm tra sentinel **trước**, sau đó mới tính `begin`, `end` hoặc gọi `substring`. Việc này ngăn được cả ngoại lệ hiển nhiên lẫn lỗi âm thầm khi `-1 + 1` trở thành `0`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-007">Chuỗi tìm rỗng và các trường hợp ở biên</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi tìm rỗng và các trường hợp ở biên khi tìm kiếm `String` trong Java

Khi tìm một chuỗi con bằng `String.indexOf()`, `lastIndexOf()`, `contains()`, `startsWith()` hay `endsWith()`, rất dễ bỏ qua trường hợp **chuỗi cần tìm là `""` (chuỗi rỗng)**. Tuy nhiên, Java coi đây là **đầu vào hợp lệ**, và kết quả có quy tắc cụ thể. Các chỉ số ở biên như `0`, `length()`, chỉ số âm hoặc lớn hơn độ dài cũng có thể tạo kết quả khác với suy đoán ban đầu.

Bài học này tập trung vào ba câu hỏi:

1. Vì sao tìm kiếm `""` vẫn có kết quả dù nó không chứa ký tự nào?
2. Các API tìm kiếm xử lý **chuỗi rỗng, chuỗi nguồn rỗng và chỉ số `fromIndex` ngoài phạm vi** như thế nào?
3. Làm sao viết mã an toàn khi chuỗi cần tìm đến từ người dùng hoặc khi lặp qua nhiều lần xuất hiện?

Các ví dụ áp dụng cho **Java 17+** và dựa trên hợp đồng của `java.lang.String`.

## 1. Phân biệt chuỗi nguồn, chuỗi cần tìm và vị trí tìm kiếm

Trong biểu thức:

```java
int position = text.indexOf(keyword);
```

- `text` là **chuỗi nguồn** (*haystack*).
- `keyword` là **chuỗi cần tìm** (*needle*).
- `position` là chỉ số bắt đầu của lần xuất hiện đầu tiên hoặc `-1` nếu không tìm thấy.

Chỉ số `String` trong Java bắt đầu từ `0`, dùng **đơn vị mã UTF-16**, và vị trí kết thúc chuỗi (ngay sau đơn vị mã cuối) là `text.length()`.

Ví dụ chuỗi `"Java"` có độ dài `4`:

```text
       J   a   v   a
Index  0   1   2   3
Vị trí có thể chèn chuỗi rỗng:
       0   1   2   3   4
```

Một cách hiểu hữu ích: **chuỗi rỗng phù hợp tại một vị trí ranh giới, không cần chiếm ký tự nào**. Vì thế, không nên tự động diễn giải kết quả của tìm kiếm `""` là “không tìm thấy”.

Hãy phân biệt rõ ba giá trị:

- `""`: một đối tượng `String` hợp lệ có `length() == 0`.
- `" "`: chuỗi chứa một dấu cách, có `length() == 1`. Tìm dấu cách khác hoàn toàn tìm chuỗi rỗng.
- `null`: không tham chiếu tới đối tượng `String`; không phải chuỗi có độ dài 0.

## 2. Kết quả cơ bản khi chuỗi cần tìm là `""`

Xét:

```java
String text = "Java";
System.out.println(text.indexOf(""));
System.out.println(text.lastIndexOf(""));
System.out.println(text.contains(""));
System.out.println(text.startsWith(""));
System.out.println(text.endsWith(""));
```

Các dòng sẽ in:

```text
0
4
true
true
true
```

Giải thích theo từng API:

| Lời gọi với `text = "Java"` | Kết quả | Ý nghĩa |
| --- | --- | --- |
| `text.indexOf("")` | `0` | Vị trí khớp rỗng đầu tiên là ngay trước chữ `J`. |
| `text.lastIndexOf("")` | `4` | Vị trí khớp rỗng cuối cùng là ngay sau chữ `a` cuối. |
| `text.contains("")` | `true` | Chuỗi rỗng được coi là chuỗi con của mọi `String`. |
| `text.startsWith("")` | `true` | Mọi chuỗi đều có tiền tố rỗng. |
| `text.endsWith("")` | `true` | Mọi chuỗi đều có hậu tố rỗng. |

Đây là **hành vi theo hợp đồng API**, không phải lỗi hoặc sự trùng hợp ngẫu nhiên từ một phiên bản JVM. Đặc biệt, `indexOf("")` trả `0` và `lastIndexOf("")` trả `length()`, không trả `-1`.

Ngược lại, khi tìm một **chuỗi không rỗng** mà nguồn không chứa nó, kết quả `indexOf()` hoặc `lastIndexOf()` là `-1`:

```java
"Java".indexOf("Python")      // -1
"Java".lastIndexOf("Python")  // -1
"Java".contains("Python")     // false
```

Các dòng vừa rồi là biểu thức minh họa trong một phương thức, không phải tệp Java độc lập.

## 3. Nếu chính chuỗi nguồn cũng rỗng thì sao?

Cho `String empty = "";`. Câu trả lời vẫn tuân theo cùng quy tắc:

```java
String empty = "";

System.out.println(empty.length());        // 0
System.out.println(empty.indexOf(""));     // 0
System.out.println(empty.lastIndexOf("")); // 0
System.out.println(empty.contains(""));    // true
System.out.println(empty.startsWith(""));  // true
System.out.println(empty.endsWith(""));    // true
System.out.println(empty.indexOf("a"));    // -1
System.out.println(empty.contains("a"));   // false
```

Chuỗi nguồn rỗng có **một vị trí biên duy nhất**, tức vị trí `0`, nên tìm `""` thành công tại đó. Nhưng nó **không chứa bất kỳ chuỗi con không rỗng nào**.

Các trường hợp nên nhớ:

| Chuỗi nguồn | Chuỗi tìm | `indexOf` | `lastIndexOf` | `contains` |
| --- | --- | ---: | ---: | --- |
| `""` | `""` | 0 | 0 | `true` |
| `""` | `"a"` | -1 | -1 | `false` |
| `"abc"` | `""` | 0 | 3 | `true` |
| `"abc"` | `"bc"` | 1 | 1 | `true` |
| `"abc"` | `"x"` | -1 | -1 | `false` |

Không dùng `text.contains("")` làm điều kiện xác nhận `text` **không rỗng**. Cả `"abc".contains("")` lẫn `"".contains("")` đều là `true`. Để kiểm tra nguồn rỗng, dùng `text.isEmpty()` sau khi bảo đảm tham chiếu khác `null`.

## 4. `indexOf("", fromIndex)`: vị trí tìm rỗng theo chiều tiến

Phương thức `indexOf(String str, int fromIndex)` tìm lần xuất hiện đầu tiên của `str` bắt đầu từ chỉ số `fromIndex`. Với `str = ""`, công thức kết quả rất cụ thể:

```text
indexOf("", fromIndex)
    = min(max(fromIndex, 0), text.length())
```

Nói cách khác, Java **giới hạn chỉ số về khoảng từ 0 đến `length()`** khi chuỗi cần tìm rỗng.

Ví dụ `text = "Java"` có `length() == 4`:

| `fromIndex` | `text.indexOf("", fromIndex)` | Giải thích |
| ---: | ---: | --- |
| `-3` | `0` | Chỉ số âm được quy về vị trí đầu. |
| `0` | `0` | Khớp rỗng ngay đầu chuỗi. |
| `2` | `2` | Khớp rỗng tại ranh giới trước đơn vị mã thứ ba. |
| `4` | `4` | Cho phép vị trí ngay sau ký tự cuối. |
| `9` | `4` | Vượt quá độ dài, kết quả được giới hạn tại `length()`. |

**Điểm dễ sai:** nếu chuỗi cần tìm **không rỗng**, truyền `fromIndex` lớn hơn hoặc bằng `length()` thường sẽ không còn đủ vị trí để khớp và trả `-1`. Với chuỗi cần tìm rỗng, ngay cả `fromIndex` rất lớn vẫn trả `length()`:

```java
String text = "Java";
System.out.println(text.indexOf("a", 99)); // -1
System.out.println(text.indexOf("", 99));  // 4
```

Không giả định rằng “`fromIndex` vượt biên thì mọi phép tìm kiếm đều trả `-1`”.

## 5. `lastIndexOf("", fromIndex)`: tìm theo chiều ngược

`lastIndexOf(String str, int fromIndex)` tìm lần xuất hiện cuối cùng có vị trí bắt đầu **không lớn hơn `fromIndex`**. Với `str = ""`, quy tắc khác `indexOf` ở phía chỉ số âm:

```text
Nếu fromIndex < 0:
    lastIndexOf("", fromIndex) = -1
Ngược lại:
    lastIndexOf("", fromIndex) = min(fromIndex, text.length())
```

Cho `text = "Java"`:

| `fromIndex` | `text.lastIndexOf("", fromIndex)` | Giải thích |
| ---: | ---: | --- |
| `-3` | `-1` | Không có vị trí bắt đầu hợp lệ ở bên trái chỉ số âm. |
| `0` | `0` | Khớp rỗng đầu tiên cũng là vị trí cuối cho phép. |
| `2` | `2` | Khớp rỗng gần nhất từ phía trái tại vị trí 2. |
| `4` | `4` | Khớp rỗng tại điểm kết thúc chuỗi. |
| `9` | `4` | Chỉ số lớn hơn độ dài được giới hạn ở cuối chuỗi. |

Điểm đối chiếu quan trọng:

```java
String text = "Java";
System.out.println(text.indexOf("", -1));     // 0
System.out.println(text.lastIndexOf("", -1)); // -1
```

Cả hai API đều hiểu `fromIndex` theo hướng tìm kiếm của mình. Với chỉ số âm, tìm xuôi vẫn bắt đầu từ đầu chuỗi; tìm ngược thì không còn điểm bắt đầu hợp lệ, trả `-1`.

**Không nhầm `lastIndexOf("")` với `lastIndexOf("", 0)`**:

```java
"Java".lastIndexOf("")     // 4
"Java".lastIndexOf("", 0)  // 0
```

Bản không truyền `fromIndex` xét vị trí khớp rỗng cuối cùng của toàn bộ chuỗi, còn bản truyền `0` chỉ xét đến vị trí `0`.

## 6. Tiền tố/hậu tố rỗng và kiểm tra chỉ số `startsWith("", offset)`

`startsWith(String prefix)` và `endsWith(String suffix)` trả `true` khi đối số rỗng, kể cả khi chuỗi nguồn cũng rỗng:

```java
"Java".startsWith("") // true
"Java".endsWith("")   // true
"".startsWith("")     // true
"".endsWith("")       // true
```

Với phương thức có `offset`, điều kiện chính xác hơn. `text.startsWith("", offset)` trả `true` **khi và chỉ khi** `0 <= offset <= text.length()`:

| `text = "Java"`, `length() = 4` | Kết quả |
| --- | --- |
| `text.startsWith("", -1)` | `false` |
| `text.startsWith("", 0)` | `true` |
| `text.startsWith("", 2)` | `true` |
| `text.startsWith("", 4)` | `true` |
| `text.startsWith("", 5)` | `false` |

So sánh hai lời gọi khi `offset` lớn hơn độ dài:

```java
String text = "Java";
System.out.println(text.indexOf("", 5));     // 4
System.out.println(text.startsWith("", 5));   // false
```

`indexOf` chuẩn hóa chỉ số đối với chuỗi cần tìm rỗng; `startsWith` với chỉ số `offset` không hợp lệ trả `false`. Vì vậy, **không suy diễn quy tắc chỉ số từ API này sang API khác**.

## 7. Chương trình Java 17+: bảng kết quả biên có thể chạy lại

Tạo tệp `EmptySearchBoundaryDemo.java`:

```java
public class EmptySearchBoundaryDemo {
    public static void main(String[] args) {
        String text = "Java";
        int[] positions = {-3, 0, 2, 4, 9};

        System.out.println("length = " + text.length());
        System.out.println("indexOfEmpty = " + text.indexOf(""));
        System.out.println("lastIndexOfEmpty = " + text.lastIndexOf(""));
        System.out.println("containsEmpty = " + text.contains(""));
        System.out.println("startsWithEmpty = " + text.startsWith(""));
        System.out.println("endsWithEmpty = " + text.endsWith(""));

        for (int from : positions) {
            System.out.println(
                "from=" + from
                + " indexOf=" + text.indexOf("", from)
                + " lastIndexOf=" + text.lastIndexOf("", from)
                + " startsWith=" + text.startsWith("", from)
            );
        }

        String empty = "";
        System.out.println("empty.indexOfEmpty = " + empty.indexOf(""));
        System.out.println(
            "empty.lastIndexOfEmpty = " + empty.lastIndexOf(""));
        System.out.println("empty.containsEmpty = " + empty.contains(""));
        System.out.println("empty.indexOfA = " + empty.indexOf("a"));
    }
}
```

Biên dịch và chạy:

```bash
javac EmptySearchBoundaryDemo.java
java EmptySearchBoundaryDemo
```

Kết quả:

```text
length = 4
indexOfEmpty = 0
lastIndexOfEmpty = 4
containsEmpty = true
startsWithEmpty = true
endsWithEmpty = true
from=-3 indexOf=0 lastIndexOf=-1 startsWith=false
from=0 indexOf=0 lastIndexOf=0 startsWith=true
from=2 indexOf=2 lastIndexOf=2 startsWith=true
from=4 indexOf=4 lastIndexOf=4 startsWith=true
from=9 indexOf=4 lastIndexOf=4 startsWith=false
empty.indexOfEmpty = 0
empty.lastIndexOfEmpty = 0
empty.containsEmpty = true
empty.indexOfA = -1
```

Ba khác biệt nên tự giải thích lại:

1. `indexOf("", -3) == 0` nhưng `lastIndexOf("", -3) == -1`.
2. `indexOf("", 9) == 4` nhưng `startsWith("", 9) == false`.
3. Tìm `""` trong nguồn `""` vẫn thành công tại `0`; tìm `"a"` trong nguồn rỗng không thành công.

## 8. Chỉ số biên khi chuỗi tìm **không rỗng**

Không nên chỉ ghi nhớ trường hợp chuỗi rỗng rồi áp dụng sai cho mọi chuỗi tìm. Với `text = "ababa"`, độ dài `5`:

```java
String text = "ababa";

System.out.println(text.indexOf("aba"));       // 0
System.out.println(text.indexOf("aba", 1));    // 2
System.out.println(text.indexOf("aba", 3));    // -1

System.out.println(text.lastIndexOf("aba"));   // 2
System.out.println(text.lastIndexOf("aba", 1)); // 0

System.out.println(text.indexOf("ba", -10));    // 1
System.out.println(text.indexOf("ba", 5));      // -1
System.out.println(text.lastIndexOf("ba", 99)); // 3
System.out.println(text.lastIndexOf("ba", -1)); // -1
```

Đây là fragment Java đặt trong một phương thức. Những kết quả này cho thấy:

- `indexOf("aba", 1)` tìm từ chỉ số `1` về cuối, nên bỏ lần khớp ở `0` và gặp lần khớp ở `2`.
- `lastIndexOf("aba", 1)` chỉ chấp nhận điểm bắt đầu không lớn hơn `1`, nên tìm ra lần ở `0`.
- Với chuỗi tìm không rỗng, `indexOf` từ `length()` không còn vị trí khớp.
- Với `lastIndexOf`, chỉ số lớn hơn độ dài có thể vẫn tìm thấy chuỗi ở phía bên trái, vì đây là phép tìm **ngược**.
- Hai lần xuất hiện có thể **chồng lấn**, như `"aba"` ở chỉ số `0` và `2`.

Mô hình tổng quát: `indexOf` tìm lần đầu có vị trí bắt đầu **từ `fromIndex` trở về sau** (chỉ số âm được coi như `0`); `lastIndexOf` tìm lần cuối có vị trí bắt đầu **không vượt quá `fromIndex`**. Kết quả `-1` nghĩa là không có lần xuất hiện thỏa điều kiện.

## 9. Cẩn thận với `null`: không đồng nghĩa `""`

Chuỗi tìm rỗng là **giá trị hợp lệ**; `null` thì **không phải một chuỗi**.

```java
String text = "Java";
String keyword = null;

// text.indexOf(keyword);  // Ném NullPointerException.
// text.contains(keyword); // Ném NullPointerException.
// text.startsWith(keyword); // Ném NullPointerException.
```

Ba dòng gọi API trên được chú thích để ví dụ không ném ngoại lệ khi biên dịch/chạy. Tương tự, nếu chính biến `text` là `null` thì `text.indexOf(...)` cũng ném `NullPointerException` vì không có đối tượng nhận lời gọi.

Nếu keyword đến từ UI, API hoặc cấu hình, hãy **định nghĩa rõ chính sách**. Ví dụ, tùy chức năng, bạn có thể:

- Từ chối `null` và `""` như từ khóa không hợp lệ.
- Coi `""` là điều kiện tìm kiếm không lọc (trả mọi bản ghi), nhưng kiểm tra tường minh thay vì vô tình dựa vào `contains("")`.
- Cho phép `""` để mô hình hóa vị trí rỗng trong một API chuyên xử lý văn bản.

Các lựa chọn này khác nhau về nghiệp vụ; Java không tự biết người dùng muốn cách nào.

## 10. Lỗi nguy hiểm: vòng lặp tìm mọi lần xuất hiện bị kẹt khi keyword rỗng

Khi duyệt các lần xuất hiện của một chuỗi con, lập trình viên thường tăng vị trí tìm tiếp dựa theo `keyword.length()`:

```java
// Mã minh họa cách làm SAI nếu keyword là "":
int from = 0;
int hit;
while ((hit = text.indexOf(keyword, from)) >= 0) {
    System.out.println(hit);
    from = hit + keyword.length();
}
```

Nếu `keyword.equals("")`, thì `keyword.length() == 0`. Ở lần đầu, `hit` có thể là `0`, và `from` vẫn bằng `0`. Lần sau lại tìm được đúng vị trí đó: **vòng lặp không tiến lên**.

Một cách phòng tránh là yêu cầu keyword phải không rỗng trước khi tìm. Nếu chức năng cần liệt kê tất cả **điểm ranh giới rỗng**, cần viết một nhánh riêng có số bước hữu hạn (từ `0` đến `text.length()`); không dùng mã lặp dành cho chuỗi con không rỗng mà không sửa quy tắc tăng chỉ số.

### Ví dụ Java 17+ có kiểm tra đầu vào và hỗ trợ các lần khớp chồng lấn

Tạo tệp `SafeSubstringSearch.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SafeSubstringSearch {
    static List<Integer> findAll(String text, String keyword) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(keyword, "keyword");

        if (keyword.isEmpty()) {
            throw new IllegalArgumentException("keyword must not be empty");
        }

        List<Integer> positions = new ArrayList<>();
        int from = 0;

        while (from < text.length()) {
            int found = text.indexOf(keyword, from);
            if (found == -1) {
                break;
            }
            positions.add(found);
            from = found + 1; // Cho phép lần khớp tiếp theo chồng lấn.
        }

        return positions;
    }

    public static void main(String[] args) {
        System.out.println(findAll("ababa", "aba"));
        System.out.println(findAll("aaaa", "aa"));
        System.out.println(findAll("", "a"));
        System.out.println(findAll("Java", "x"));

        try {
            findAll("Java", "");
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac SafeSubstringSearch.java
java SafeSubstringSearch
```

Kết quả:

```text
[0, 2]
[0, 1, 2]
[]
[]
keyword must not be empty
```

Trong `findAll()`:

- Kiểm tra `null` trước khi gọi phương thức trên `text` hoặc `keyword`.
- Chặn `keyword.isEmpty()` để không có trường hợp `indexOf("")` trả lại vị trí cũ liên tục.
- Tăng `from = found + 1` giúp tìm **cả những lần xuất hiện chồng lấn**. Nếu bài toán chỉ cần lần khớp **không chồng lấn** với keyword không rỗng, có thể tăng `from = found + keyword.length()`.
- Vòng lặp hữu hạn vì `from` tăng và bị ràng buộc bởi độ dài chuỗi. Khi không còn kết quả, `indexOf()` trả `-1` và vòng lặp dừng.

Với chương trình cần hiển thị vị trí khớp rỗng như một tính năng hợp lệ, hãy mô tả rõ: trên chuỗi có `n` UTF-16 code units sẽ có **`n + 1` ranh giới** (từ `0` đến `n`). Đây là quy ước về **vị trí chỉ số UTF-16**, không phải số ranh giới grapheme mà người dùng nhìn thấy.

## 11. Unicode: vị trí tìm kiếm tính theo UTF-16, không theo số emoji hiển thị

Các API `indexOf()` và `lastIndexOf()` của `String` trả về **chỉ số UTF-16 code units**. Ví dụ:

```java
String text = "A\uD83D\uDE00B"; // A😀B, emoji là một surrogate pair.

System.out.println(text.length());        // 4
System.out.println(text.indexOf("B"));    // 3
System.out.println(text.indexOf(""));     // 0
System.out.println(text.lastIndexOf("")); // 4
System.out.println(text.indexOf("", 2));  // 2
```

Về mặt chuỗi ký tự hiển thị, văn bản `A😀B` thường có ba ký tự được cảm nhận. Nhưng về chỉ số UTF-16, nó có bốn code units: `A` ở 0; hai đơn vị mã của emoji ở 1 và 2; `B` ở 3.

Vì chuỗi tìm rỗng không chiếm code unit nào, vị trí `2` vẫn là một **ranh giới UTF-16 hợp lệ cho phép tìm kiếm**, dù ranh giới đó nằm giữa hai nửa của surrogate pair. Đây là lý do không nên diễn giải vị trí tìm `""` trực tiếp thành “vị trí ký tự hiển thị” trong giao diện.

Nếu ứng dụng muốn di chuyển theo Unicode code points, dùng những API như `codePointAt()`, `codePointCount()`, `offsetByCodePoints()` khi phù hợp. Nếu muốn thao tác theo ký tự người dùng cảm nhận (grapheme), cần quy tắc phân đoạn grapheme phù hợp. **`String.length()` và chỉ số `indexOf()` không tự giải quyết vấn đề đó.**

So sánh tìm kiếm `String` theo các API trên cũng **phân biệt chữ hoa/chữ thường** và **không tự chuẩn hóa Unicode**. Ví dụ `"Java".indexOf("java") == -1`; còn hai cách biểu diễn Unicode nhìn giống nhau có thể khác dãy đơn vị mã. Nếu nghiệp vụ yêu cầu chuẩn hóa, cần làm một cách nhất quán trước khi tìm.

## 12. Một số trường hợp ở biên bổ sung

| Tình huống | Kết quả / Điều cần lưu ý |
| --- | --- |
| `text.indexOf("")` | Luôn `0` với mọi đối tượng `String text` khác `null`. |
| `text.lastIndexOf("")` | Luôn bằng `text.length()`. |
| `text.contains("")` | Luôn `true` đối với đối tượng `String` khác `null`. |
| `text.indexOf("", Integer.MAX_VALUE)` | Bằng `text.length()`. |
| `text.indexOf("", Integer.MIN_VALUE)` | Bằng `0`. |
| `text.lastIndexOf("", Integer.MAX_VALUE)` | Bằng `text.length()`. |
| `text.lastIndexOf("", Integer.MIN_VALUE)` | `-1`. |
| `text.startsWith("", text.length())` | `true`. |
| `text.startsWith("", text.length() + 1)` | `false`, nếu phép cộng không tràn số nguyên (với chuỗi Java thực tế thông thường). |
| `text.indexOf("x")` mà không có `x` | `-1`. |
| `text.indexOf("") == -1` | Luôn `false` khi `text` khác `null`. |
| `text.contains(" ")` | Tìm **dấu cách thật**, không đồng nghĩa `contains("")`. |

Cần nhớ rằng `indexOf(int ch)` tìm một **ký tự/code point** theo tham số `int`, không nhận một chuỗi tìm rỗng; các quy tắc tìm `""` ở bài này dành cho overload nhận **`String`** (hoặc `CharSequence` trong trường hợp `contains()`).

## 13. Những ngộ nhận dễ gây lỗi

1. **“Không có ký tự nào thì không thể tìm thấy.”** Sai: chuỗi rỗng là chuỗi con hợp lệ; `indexOf("")` trả `0`.
2. **“`lastIndexOf("")` luôn trả `0`.”** Sai: trên chuỗi `"Java"`, nó trả `4`.
3. **“`contains("")` nghĩa là nguồn phải có ít nhất một ký tự.”** Sai: `"".contains("")` vẫn là `true`.
4. **“Chỉ số lớn hơn độ dài thì `indexOf` luôn trả `-1`.”** Sai khi chuỗi tìm rỗng: `text.indexOf("", 999)` trả `text.length()`.
5. **“`indexOf` và `lastIndexOf` xử lý chỉ số âm giống nhau.”** Sai: `indexOf("", -1)` trả `0`; `lastIndexOf("", -1)` trả `-1`.
6. **“`startsWith("", offset)` luôn đúng ở mọi offset.”** Sai: chỉ đúng khi `offset` thuộc đoạn `0..length()`.
7. **“Chuỗi rỗng giống `null`.”** Sai: `""` là đối tượng hợp lệ còn `null` không phải đối tượng `String`.
8. **“Vòng lặp tìm tất cả khớp có thể tăng `keyword.length()` bất kể giá trị.”** Sai: khi keyword rỗng, độ tăng bằng `0` có thể tạo vòng lặp vô hạn.
9. **“Mọi chỉ số tìm kiếm là số ký tự hiển thị.”** Sai: `String` dùng vị trí UTF-16 code units, kể cả ở giữa surrogate pair.

## 14. Tự kiểm tra

**Câu 1.** Với `String s = "abc";`, ba kết quả sau là gì?

```java
System.out.println(s.indexOf(""));
System.out.println(s.lastIndexOf(""));
System.out.println(s.contains(""));
```

Đáp án:

```text
0
3
true
```

**Câu 2.** Vì sao kết quả hai lời gọi sau không giống nhau?

```java
String s = "abc";
System.out.println(s.indexOf("", -5));
System.out.println(s.lastIndexOf("", -5));
```

Kết quả lần lượt là `0` và `-1` vì tìm xuôi giới hạn đầu vào âm về `0`, còn tìm ngược với `fromIndex < 0` không có điểm bắt đầu hợp lệ.

**Câu 3.** Nếu `String s = "";`, kết quả `s.startsWith("")`, `s.endsWith("")` và `s.indexOf("x")` là gì?

Lần lượt: `true`, `true` và `-1`. Nguồn rỗng vẫn có tiền tố/hậu tố rỗng nhưng không chứa chuỗi `"x"`.

**Câu 4.** Một ô tìm kiếm cho phép người dùng để trống, và chức năng yêu cầu “không nhập gì thì hiển thị tất cả bản ghi”. Có nên chỉ viết `value.contains(keyword)`?

Không nên chỉ dựa vào hành vi ngầm của `contains("")`. Hãy kiểm tra rõ `keyword == null`, `keyword.isEmpty()` (hoặc `isBlank()` nếu nghiệp vụ coi whitespace là bỏ trống), rồi chọn nhánh “không lọc”. Làm vậy sẽ rõ chính sách dữ liệu và tránh trường hợp mã sau này đổi API nhưng không còn giữ cùng hành vi.

**Câu 5.** Một vòng lặp tiến từ `from = found + keyword.length()` bị kẹt khi keyword rỗng vì sao?

Vì `keyword.length() == 0` và `indexOf("", from)` trả lại `from` nếu nó nằm trong phạm vi. Cần chặn keyword rỗng hoặc triển khai một nhánh xử lý ranh giới riêng.

## Tóm tắt kiến thức

- `""` là chuỗi tìm hợp lệ: `indexOf("") == 0`, `lastIndexOf("") == length()`, `contains("") == true`, `startsWith("") == true` và `endsWith("") == true`.
- `indexOf("", fromIndex)` giới hạn `fromIndex` trong đoạn `0..length()`. `lastIndexOf("", fromIndex)` trả `-1` với chỉ số âm, còn chỉ số không âm được giới hạn tối đa tại `length()`.
- `startsWith("", offset)` chỉ trả `true` khi `offset` nằm trong phạm vi `0..length()`.
- Với chuỗi tìm không rỗng, `-1` nghĩa là không có khớp thỏa điều kiện; kết quả tìm kiếm tính theo **UTF-16 code-unit indexes**.
- Phân biệt rõ `""`, whitespace và `null`. Khi viết vòng lặp tìm nhiều lần khớp, **xử lý keyword rỗng tường minh** để tránh không tiến lên.
- Trường hợp ở biên không phải ngoại lệ cần đoán: mỗi phương thức có hợp đồng riêng; đọc và thử đúng API được dùng.

## Tài liệu tham khảo

- [Java SE 21 API — `java.lang.String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html): hợp đồng chính thức của `indexOf(String)`, `indexOf(String, int)`, `lastIndexOf(String)`, `lastIndexOf(String, int)`, `contains(CharSequence)`, `startsWith(String, int)` và `endsWith(String)`.
- [Java SE 21 API — `java.lang.Character`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Character.html): UTF-16, surrogate pairs và Unicode code points.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-008">indexOf có beginIndex, endIndex từ Java 21</a>

<details>
<summary>Xem chi tiết</summary>

# `String.indexOf` với `beginIndex` và `endIndex` từ Java 21

Trong Java 21, `java.lang.String` bổ sung hai overload `indexOf` cho phép **giới hạn tìm kiếm trong một khoảng của chuỗi gốc**. Nhờ đó, có thể tìm ký tự/code point hoặc chuỗi con trong đoạn `[beginIndex, endIndex)` mà không phải tự tạo `substring` để khoanh vùng. Kết quả vẫn là **vị trí trong chuỗi gốc**, không phải vị trí tương đối trong đoạn được tìm.

Hai overload mới:

```java
// Có từ Java 21:
int indexOf(int ch, int beginIndex, int endIndex);
int indexOf(String str, int beginIndex, int endIndex);
```

Đây là các **chữ ký phương thức để tra cứu**, không phải khai báo có thể đặt độc lập trong một lớp tùy ý. Để gọi được chúng cần **biên dịch và chạy bằng JDK 21 trở lên**. Hai dạng cũ `indexOf(int ch, int fromIndex)` và `indexOf(String str, int fromIndex)` chỉ chọn điểm bắt đầu; không có tham số giới hạn điểm kết thúc.

## 1. Hiểu đúng khoảng `[beginIndex, endIndex)`

Cú pháp khoảng **nửa mở** được dùng xuyên suốt các API tìm kiếm này:

- `beginIndex`: vị trí bắt đầu, **bao gồm**.
- `endIndex`: vị trí kết thúc, **không bao gồm**.
- Khoảng hợp lệ thỏa `0 <= beginIndex <= endIndex <= s.length()`.
- Chỉ số dùng đơn vị **UTF-16 code unit** (giống `String.length()` và `substring()`), không phải số Unicode code point hay số ký tự người dùng nhìn thấy.
- Trả về vị trí đầu tiên khớp trong phạm vi hoặc `-1` nếu không thấy; kết quả khớp là chỉ số **tuyệt đối của `s`**.

Ví dụ chuỗi `"cat|dog|cat|owl"` có độ dài 15:

```text
Nội dung:       c a t | d o g | c a t | o w l
UTF-16 index:   0 1 2 3 4 5 6 7 8 9 10 11 12 13 14
                                                  end = 15

Khoảng [4, 11): d o g | c a t       (vị trí 4..10)
Khoảng [4, 10): d o g | c a         (thiếu chữ t ở index 10)
```

Vì vậy:

```java
String s = "cat|dog|cat|owl";

System.out.println(s.indexOf("cat", 4, 11)); // 8
System.out.println(s.indexOf("cat", 4, 10)); // -1
System.out.println(s.indexOf('|', 4, 11));   // 7
System.out.println(s.indexOf('|', 8, 11));   // -1
```

Ở dòng đầu, `"cat"` tại vị trí 8–10 nằm **trọn vẹn** trong khoảng `[4, 11)` nên kết quả là `8`. Ở dòng thứ hai, khoảng dừng trước index 10, không chứa trọn `"cat"` nên kết quả `-1`. Ký tự phân cách tại vị trí 11 cũng không thuộc khoảng có `endIndex = 11`.

## 2. Tìm chuỗi con: `indexOf(String str, beginIndex, endIndex)`

Đây là overload dùng khi muốn tìm **một chuỗi con hoàn chỉnh** trong một cửa sổ của chuỗi.

```java
String s = "abc---abc---xyz";

System.out.println(s.indexOf("abc", 0, 3));   // 0
System.out.println(s.indexOf("abc", 3, 9));   // 6
System.out.println(s.indexOf("abc", 3, 8));   // -1
System.out.println(s.indexOf("xyz", 9, 15));  // 12
```

Phép gọi thứ ba trả về `-1` vì cửa sổ `[3,8)` chỉ chứa các vị trí `3,4,5,6,7`; chuỗi `"abc"` bắt đầu ở 6 nhưng ký tự cuối nằm ở index 8, ngoài phạm vi.

### Quy tắc so khớp chính xác

Với `s.indexOf(str, beginIndex, endIndex)`, nếu `str` **không rỗng**, Java tìm chỉ số nhỏ nhất `k` thỏa:

- `k >= beginIndex`;
- `k + str.length() <= endIndex`;
- `s.startsWith(str, k)` trả về `true`.

Nếu không có `k` hợp lệ thì trả về `-1`. Do vậy, **toàn bộ chuỗi con** phải nằm trong cửa sổ.

Theo Java 21 API, kết quả tương đương về mặt giá trị với:

```java
String window = s.substring(beginIndex, endIndex);
int local = window.indexOf(str);
int result = local < 0 ? -1 : beginIndex + local;
```

Đoạn trên là **mã minh họa trong thân phương thức** khi các biến đã tồn tại; nó cố ý tạo `window` để làm rõ việc quy đổi chỉ số. Overload ba tham số cho phép viết trực tiếp:

```java
int result = s.indexOf(str, beginIndex, endIndex);
```

Theo tài liệu API, overload này không cần tạo đối tượng `substring` trung gian chỉ để tìm kiếm. Đó là lợi thế về sự rõ ràng và tránh việc tạo chuỗi tạm không cần thiết; không suy diễn rằng mọi tình huống chắc chắn nhanh hơn hoặc không phát sinh bất kỳ cấp phát nội bộ nào.

### Kết quả là vị trí trong chuỗi ban đầu

```java
String s = "0123Java789";
System.out.println(s.indexOf("Java", 4, 8)); // 4
```

`Java` là nội dung của cửa sổ `[4,8)`, nhưng kết quả là `4`, **không phải 0**. Đây là điểm rất dễ nhầm khi chuyển từ cách `s.substring(begin,end).indexOf(...)`.

## 3. Tìm ký tự / Unicode code point: `indexOf(int ch, beginIndex, endIndex)`

Overload có tham số đầu là `int ch` dùng để tìm một giá trị ký tự/code point. Java nhận cả biểu thức `char` thông thường vì `char` tự chuyển được thành `int`:

```java
String s = "alpha|beta|gamma";

System.out.println(s.indexOf('|', 0, 10)); // 5
System.out.println(s.indexOf('|', 6, 10)); // -1
System.out.println(s.indexOf('a', 6, 10)); // 9
```

Ở ví dụ này, ký tự `'a'` đầu tiên trong đoạn `[6,10)` nằm tại index 9.

Với giá trị `ch` trong dải BMP (`0x0000` đến `0xFFFF`), Java so sánh với từng code unit qua `charAt(k)`. Với giá trị ngoài BMP, Java tìm code point qua `codePointAt(k)`. Trong cả hai trường hợp, **chỉ số trả về là vị trí UTF-16 bắt đầu của lần xuất hiện**, không phải thứ tự code point.

Ví dụ emoji 😀 (`U+1F600`) chiếm hai code unit UTF-16:

```java
String emoji = new String(Character.toChars(0x1F600));
String s = "A" + emoji + "B"; // UTF-16 length = 4

System.out.println(s.length());                       // 4
System.out.println(s.indexOf(0x1F600, 0, s.length())); // 1
System.out.println(s.indexOf(emoji, 0, s.length()));    // 1
```

Hai lần tìm trên cùng chuỗi cho kết quả `1`, vị trí high surrogate của emoji. **Lưu ý về ranh giới:** overload `int ch` xác định code point tại chỉ số bắt đầu nằm trong khoảng; overload `String str` yêu cầu toàn bộ chuỗi con nằm trong khoảng. Khi một `endIndex` cắt ngang surrogate pair, không nên coi hai overload có cùng quy tắc “độ dài mẫu” — muốn giới hạn **trọn một ký tự Unicode**, trước hết phải đặt cửa sổ theo ranh giới code point hợp lệ (có thể tính bằng `offsetByCodePoints`).

## 4. So sánh overload cũ và mới

| Lệnh | Cách tìm | Java |
| --- | --- | --- |
| `s.indexOf("cat")` | Tìm trong toàn chuỗi | Trước Java 21 |
| `s.indexOf("cat", fromIndex)` | Tìm từ một vị trí về cuối, không có `endIndex` | Trước Java 21 |
| `s.indexOf("cat", beginIndex, endIndex)` | Tìm chuỗi con **nằm trọn trong cửa sổ** | Từ Java 21 |
| `s.indexOf('c')` | Tìm ký tự đầu tiên trong toàn chuỗi | Trước Java 21 |
| `s.indexOf('c', fromIndex)` | Tìm ký tự từ `fromIndex` trở đi | Trước Java 21 |
| `s.indexOf('c', beginIndex, endIndex)` | Chỉ xét vị trí bắt đầu trong cửa sổ | Từ Java 21 |

Các dạng một tham số hoặc hai tham số dùng `fromIndex` có thể xử lý chỉ số ngoài phạm vi khá “thoáng”: chỉ số âm được xử lý như tìm từ đầu, và chỉ số vượt quá độ dài thường cho kết quả `-1` đối với mẫu không rỗng. Trong khi đó, **overload ba tham số kiểm tra nghiêm ngặt**:

```java
String s = "abc";

System.out.println(s.indexOf("a", -3)); // 0
System.out.println(s.indexOf("a", 99)); // -1

// s.indexOf("a", -3, 3); // Ném StringIndexOutOfBoundsException
// s.indexOf("a",  0, 4); // Ném StringIndexOutOfBoundsException
// s.indexOf("a",  2, 1); // Ném StringIndexOutOfBoundsException
```

Do đó overload mới giúp phân biệt rõ **“không thấy mẫu trong cửa sổ hợp lệ”** (`-1`) với **“người gọi truyền ranh giới không hợp lệ”** (exception).

## 5. Ví dụ hoàn chỉnh Java 21+ có thể biên dịch và chạy

Lưu toàn bộ chương trình sau vào `BoundedIndexOfDemo.java`:

```java
public class BoundedIndexOfDemo {
    public static void main(String[] args) {
        String s = "cat|dog|cat|owl";

        System.out.println("length = " + s.length());
        System.out.println("cat in [4,11) = " + s.indexOf("cat", 4, 11));
        System.out.println("cat in [4,10) = " + s.indexOf("cat", 4, 10));
        System.out.println("dog in [4,7) = " + s.indexOf("dog", 4, 7));
        System.out.println("pipe in [4,11) = " + s.indexOf('|', 4, 11));
        System.out.println("pipe in [8,11) = " + s.indexOf('|', 8, 11));
        System.out.println("empty in [6,6) = " + s.indexOf("", 6, 6));
        System.out.println("empty in [15,15) = " + s.indexOf("", 15, 15));
        System.out.println("missing = " + s.indexOf("fox", 0, 15));

        try {
            s.indexOf("cat", -1, 5);
            System.out.println("unexpected success");
        } catch (StringIndexOutOfBoundsException expected) {
            System.out.println("invalid range rejected");
        }

        String emoji = new String(Character.toChars(0x1F600));
        String unicode = "A" + emoji + "B";
        System.out.println("UTF-16 length = " + unicode.length());
        System.out.println("emoji code point index = "
                + unicode.indexOf(0x1F600, 0, unicode.length()));
        System.out.println("emoji string index = "
                + unicode.indexOf(emoji, 0, unicode.length()));
    }
}
```

Biên dịch/chạy với **JDK 21 hoặc mới hơn**:

```bash
javac BoundedIndexOfDemo.java
java BoundedIndexOfDemo
```

Kết quả chuẩn:

```text
length = 15
cat in [4,11) = 8
cat in [4,10) = -1
dog in [4,7) = 4
pipe in [4,11) = 7
pipe in [8,11) = -1
empty in [6,6) = 6
empty in [15,15) = 15
missing = -1
invalid range rejected
UTF-16 length = 4
emoji code point index = 1
emoji string index = 1
```

Hãy đặc biệt quan sát việc `indexOf("", 6, 6)` trả về `6`. Mẫu rỗng có thể khớp tại chính vị trí bắt đầu của một cửa sổ rỗng. Ngược lại, tìm một mẫu không rỗng hoặc ký tự bình thường trong cửa sổ rỗng sẽ cho `-1`.

## 6. Quy tắc biên, lỗi và các ca dễ nhầm

### 6.1. Khoảng rỗng và mẫu rỗng

Với `String str` là **chuỗi rỗng**, có một kết quả hợp lệ tại `beginIndex`, kể cả khi `beginIndex == endIndex`, hoặc khi cả hai bằng `s.length()`:

```java
String s = "abc";
System.out.println(s.indexOf("", 0, 0)); // 0
System.out.println(s.indexOf("", 2, 2)); // 2
System.out.println(s.indexOf("", 3, 3)); // 3
System.out.println(s.indexOf("a", 2, 2)); // -1
System.out.println(s.indexOf('a', 2, 2)); // -1
```

Không có “vị trí ký tự” nằm trong `[2,2)`, nhưng mẫu rỗng có thể khớp ở một ranh giới hợp lệ. Đừng vô tình dùng `indexOf("", ...)` khi ý định là tìm một dấu phân cách không rỗng.

### 6.2. Ranh giới không hợp lệ

Cả hai overload Java 21 ba tham số đều ném `StringIndexOutOfBoundsException` nếu:

- `beginIndex < 0`;
- `endIndex > s.length()`;
- `beginIndex > endIndex`.

Khi xét xử lý đầu vào, cũng phải đảm bảo bản thân biến `s` không phải `null`. Với overload nhận `String str`, `str == null` gây `NullPointerException`; một tham số `int ch` không có trạng thái `null`. Chọn rõ chính sách với tham số `null` thay vì biến lỗi truy cập thành kết quả “không tìm thấy”.

Ví dụ minh họa cách kiểm tra trước trong một phương thức ứng dụng:

```java
static int boundedSearch(String text, String needle, int begin, int end) {
    java.util.Objects.requireNonNull(text, "text");
    java.util.Objects.requireNonNull(needle, "needle");

    if (begin < 0 || begin > end || end > text.length()) {
        throw new IllegalArgumentException("Invalid search window");
    }
    return text.indexOf(needle, begin, end); // Cần Java 21+
}
```

Đây là **phương thức minh họa để đặt bên trong một lớp**, không phải chương trình độc lập. Ta chủ động chuyển lỗi giới hạn thành `IllegalArgumentException` theo hợp đồng riêng của API; nếu muốn giữ nguyên exception chuẩn từ `String.indexOf`, có thể bỏ phần kiểm tra này.

### 6.3. So khớp phân biệt hoa thường, không tự chuẩn hóa Unicode

Các overload mới chỉ **thu hẹp cửa sổ tìm kiếm**. Chúng không tự bỏ dấu cách, không chuyển `"A"` thành `"a"`, không chuẩn hóa Unicode, cũng không so khớp theo ngôn ngữ tự nhiên:

```java
String s = "Java java";
System.out.println(s.indexOf("java", 0, s.length())); // 5
System.out.println(s.indexOf("JAVA", 0, s.length())); // -1
```

Nếu ứng dụng cần tìm không phân biệt hoa thường hay xử lý Unicode phức tạp, đó là một yêu cầu khác, phải xác định quy tắc chuẩn hóa/collation phù hợp. Không nên mặc nhiên xử lý bằng `toLowerCase()` không chọn locale rồi coi là đúng cho mọi ngôn ngữ.

## 7. Ứng dụng thực tế: tìm trong một trường hoặc một đoạn thông điệp

Xét một thông điệp có các trường ngăn bởi dấu `;`:

```text
id=7;role=user;token=abc
```

Bài toán: kiểm tra trường `role=user` có nằm **bên trong đoạn ở giữa dấu chấm phẩy thứ nhất và thứ hai** hay không, và tránh việc một kết quả nằm trong trường khác bị nhận nhầm.

Chương trình độc lập `FieldWindowDemo.java`:

```java
public class FieldWindowDemo {
    public static void main(String[] args) {
        String input = "id=7;role=user;token=abc";

        int firstSeparator = input.indexOf(';'); // 4
        int secondSeparator = input.indexOf(';', firstSeparator + 1); // 14

        int begin = firstSeparator + 1; // 5
        int end = secondSeparator;       // 14

        int role = input.indexOf("role=user", begin, end);
        int token = input.indexOf("token", begin, end);
        System.out.println("role offset = " + role);
        System.out.println("token offset = " + token);
    }
}
```

Chạy:

```bash
javac FieldWindowDemo.java
java FieldWindowDemo
```

Kết quả:

```text
role offset = 5
token offset = -1
```

`role` nằm trong `[5,14)` và được trả về theo chỉ số gốc `5`. Từ khóa `token` nằm **ngoài cửa sổ**, nên trả về `-1` dù xuất hiện trong toàn thông điệp.

Trong ví dụ này, ta đã biết chắc có hai dấu `;`. Nếu đầu vào thực tế có thể thiếu dấu phân cách, **phải kiểm tra kết quả `-1` trước khi dùng để xây dựng khoảng tìm**, và xác định hành vi khi dữ liệu lỗi. Không nên lấy các con số ở ví dụ làm chỉ số cứng cho mọi bản ghi.

Đây chỉ là ví dụ về tìm chuỗi theo đoạn; khi parse định dạng phức tạp, đặc biệt có escape, dấu phân cách lồng nhau hoặc biểu diễn có cấu trúc, cần dùng parser đúng hợp đồng định dạng thay vì tìm chuỗi đơn giản.

## 8. Dùng overload mới khi nào?

**Nên cân nhắc** khi đã có một vùng `[beginIndex, endIndex)` xác định và chỉ muốn tìm trong vùng đó: nội dung giữa hai dấu phân cách, một section của tài liệu, một token trong phần tiêu đề, hoặc kiểm tra mẫu xuất hiện hoàn chỉnh trong một đoạn.

Việc viết:

```java
int found = text.indexOf(needle, beginIndex, endIndex);
```

làm rõ mục đích hơn cách tạo chuỗi tạm để tìm trong một cửa sổ rồi cộng offset. Nó cũng ngăn một mẫu bắt đầu trong cửa sổ nhưng **kéo dài ra ngoài `endIndex`** đối với overload `String str`.

**Không nhất thiết phải dùng** nếu bạn cần tìm từ một điểm đến tận cuối chuỗi: `indexOf(str, fromIndex)` vẫn đơn giản và tương thích JDK cũ hơn. Nếu dự án còn phải biên dịch/chạy trên **Java 17**, các overload ba tham số **chưa tồn tại**, nên không dùng trực tiếp. Giải pháp tương thích Java 17 có thể là `substring` theo phạm vi đã xác minh hoặc một thuật toán tự tìm trong khoảng; nếu viết giải pháp thay thế, nhớ điều chỉnh chỉ số cục bộ trở lại chỉ số gốc.

### Lưu ý với chỉ số UTF-16 và code point

Một emoji nằm ngoài BMP chiếm hai code unit; vì vậy `endIndex` và `beginIndex` không phải thứ tự code point. Nếu nhận vị trí theo code point từ API khác, hãy đổi sang UTF-16 index bằng `offsetByCodePoints` trước khi gọi `indexOf`. Cũng cần nhớ một Unicode grapheme cluster có thể gồm nhiều code point; các overload `indexOf` không tự phân đoạn văn bản theo cụm hiển thị.

## 9. Những lỗi thường gặp

1. **Cho rằng chỉ số trả về tính từ `beginIndex`.** Sai: `indexOf` trả về UTF-16 index trên chuỗi gốc, không cần cộng thêm `beginIndex`.
2. **Hiểu `endIndex` là bao gồm.** Sai: biên phải bị loại trừ. Mẫu chuỗi con phải khớp trọn trong vùng.
3. **Truyền chỉ số code point vào tham số UTF-16 index.** Sai với các chuỗi có surrogate pair; cần chuyển hệ chỉ số.
4. **Đánh đồng overload tìm `int ch` và tìm `String str` trong mọi tình huống Unicode.** Với code point ngoài BMP, cần hiểu phép kiểm tra vị trí bắt đầu của `int ch` và độ dài toàn mẫu của `String str`.
5. **Bỏ qua ngoại lệ khi khoảng không hợp lệ.** Overload ba tham số không âm thầm chấp nhận chỉ số âm, `endIndex` vượt `length()` hoặc `beginIndex > endIndex`.
6. **Kỳ vọng mẫu rỗng trả về `-1`.** `""` khớp tại `beginIndex`, kể cả cửa sổ rỗng hợp lệ.
7. **Dùng trên JDK 17 rồi tưởng là lỗi logic của `indexOf`.** Đây là API được thêm trong Java 21; cần đúng JDK và cấu hình build/runtime.
8. **Quên kiểm tra `-1` của phép tìm dấu phân cách trước khi tính cửa sổ.** Có thể tạo ra khoảng sai hoặc ném exception không mong muốn.
9. **Cho rằng phương thức tự tìm không phân biệt chữ hoa/thường.** Nó tìm chính xác theo nội dung; yêu cầu so khớp ngôn ngữ cần giải pháp riêng.

## 10. Tự kiểm tra

Với `String s = "red|blue|red";`, hãy tính kết quả:

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `s.indexOf("red", 0, 8)` | `0` | Tìm thấy lần đầu trong vùng |
| `s.indexOf("red", 4, 12)` | `9` | Chỉ lần xuất hiện thứ hai nằm trọn trong vùng |
| `s.indexOf("red", 4, 11)` | `-1` | Mẫu thứ hai cần ký tự tại index 11, nhưng biên phải bị loại trừ |
| `s.indexOf('|', 4, 8)` | `8`? | **Bẫy kiểm tra:** index 8 bị loại trừ, đáp án đúng là `-1` |
| `s.indexOf("", 8, 8)` | `8` | Mẫu rỗng khớp tại ranh giới hợp lệ |

Trong bảng, dòng có dấu hỏi là câu hỏi tự kiểm tra; **kết quả thực sự là `-1`**. Đừng dùng đáp án giả `8` như một đầu ra chương trình.

**Ghi nhớ:** Java 21 thêm `indexOf` ba tham số để tìm kiếm trong `[beginIndex, endIndex)`. Với `String str`, mẫu phải nằm trọn trong khoảng; với `int ch`, chỉ số xuất hiện được xác định theo giá trị ký tự/code point. Kết quả là **UTF-16 index của chuỗi gốc**, và phạm vi không hợp lệ sẽ bị từ chối bằng exception.

**Tài liệu chính thức:** [Java SE 21 — `String` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-search-009">Phân biệt tìm chuỗi nguyên văn với tìm theo biểu thức chính quy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tìm chuỗi nguyên văn với tìm theo biểu thức chính quy trong Java

## 1. Hai kiểu tìm kiếm có ý nghĩa khác nhau

Một thao tác tìm văn bản có thể nhận **từ khóa nguyên văn (literal)** hoặc **biểu thức chính quy (regex)**. Hai khái niệm này khác nhau ngay cả khi tình cờ cho ra cùng kết quả.

- **Tìm nguyên văn:** các ký tự của từ khóa chính là dữ liệu cần tìm. Từ khóa **a.b** chỉ khớp đúng ba ký tự **a**, dấu chấm, **b**.
- **Tìm regex:** từ khóa là một *mẫu* có ký hiệu điều khiển. Regex **a.b** khớp **a.b**, **aXb**, **a1b**, vì dấu chấm **.** biểu diễn một ký tự bất kỳ (trừ một số line terminator theo chế độ mặc định).

Trong Java, chọn **String.contains()** hoặc **String.indexOf()** khi người dùng muốn tìm một đoạn văn bản chính xác; chọn **java.util.regex.Pattern** và **Matcher** khi cần một quy tắc như “mã bắt đầu bằng item- rồi tới nhiều chữ số”.

Không nên nghĩ rằng String chứa ký tự **.**, **\**, **+**, **?** hoặc ngoặc vuông thì tự động biến thành regex. Chúng chỉ mang ý nghĩa regex khi được đưa vào một API **chủ động phân tích biểu thức chính quy**.

Ví dụ nhanh, đặt trong một phương thức:

~~~java
String text = "a.b aXb";
System.out.println(text.contains("a.b")); // true: literal "a.b"
System.out.println(text.indexOf("a.b"));  // 0
System.out.println(text.contains("a.b aXb")); // true
System.out.println(java.util.regex.Pattern
        .compile("a.b").matcher("aXb").find()); // true: "." là wildcard
~~~

## 2. Những API tìm nguyên văn của String

| API | Kết quả | Cách tìm |
| --- | --- | --- |
| **text.contains(query)** | boolean | Có đoạn **query** xuất hiện nguyên văn trong text không? |
| **text.indexOf(query)** | int | Vị trí đầu tiên của đoạn query hoặc **-1** nếu không có |
| **text.indexOf(query, fromIndex)** | int | Tìm nguyên văn, bắt đầu từ một offset UTF-16 |
| **text.lastIndexOf(query)** | int | Tìm lần xuất hiện cuối theo chiều ngược |
| **text.startsWith(prefix)** | boolean | Khớp tiền tố nguyên văn |
| **text.endsWith(suffix)** | boolean | Khớp hậu tố nguyên văn |
| **text.equals(query)** | boolean | So toàn bộ nội dung, không phải tìm chuỗi con |

Ví dụ:

~~~java
String path = "report.v1.txt";
System.out.println(path.contains(".v1."));   // true
System.out.println(path.indexOf(".v1."));    // 6
System.out.println(path.startsWith("rep"));  // true
System.out.println(path.endsWith(".txt"));   // true
System.out.println(path.contains("\\d+"));   // false
~~~

Ở câu cuối, **"\\d+"** là một String Java có nội dung gồm dấu gạch chéo ngược, chữ **d** và dấu **+**. **contains()** không giải mã nó như regex “một hoặc nhiều chữ số”.

Các phương thức tìm String nêu trên **phân biệt hoa thường**, không tự chuẩn hóa Unicode và không nhận chính sách so khớp bỏ dấu tiếng Việt.

### Trường hợp rỗng, null và vị trí

- **text.contains("")** trả về **true** với một String text khác null.
- **text.indexOf("")** trả về **0**; **text.lastIndexOf("")** trả về **text.length()**.
- Nếu **text == null**, việc gọi phương thức trên text gây **NullPointerException**. Nếu query của **contains** hoặc **indexOf(String)** là null, phương thức cũng không chấp nhận và ném ngoại lệ.
- **indexOf()**, **lastIndexOf()** và đa số phép cắt String dùng **offset UTF-16**, không phải số ký tự người dùng nhìn thấy. Emoji ngoài BMP có thể chiếm hai đơn vị UTF-16.

Nếu query rỗng không có ý nghĩa trong chức năng tìm kiếm, hãy kiểm tra đầu vào trước khi gọi API; đừng mặc nhiên hiểu **contains("") == true** là kết quả người dùng mong muốn.

### Backslash trong literal Java

~~~java
String location = "C:\\temp\\file.txt";
System.out.println(location.contains("\\"));  // true
System.out.println(location.contains(".txt")); // true
~~~

Dấu **\\** trong mã nguồn được viết kép vì cú pháp **Java string literal**. Đây là cơ chế escape của ngôn ngữ Java, độc lập với cú pháp regex. Nếu query đã được nhập từ bàn phím dưới dạng biến String thì dùng **contains(query)** trực tiếp, không tự chèn thêm các dấu gạch chéo ngược.

## 3. Regex trong Java: Pattern và Matcher

Để dùng biểu thức chính quy, import:

~~~java
import java.util.regex.Pattern;
import java.util.regex.Matcher;
~~~

**Pattern.compile(regex)** biên dịch một regex thành **Pattern**. **pattern.matcher(text)** tạo **Matcher** có trạng thái duyệt một văn bản. Sau đó:

- **find()** tìm **đoạn con** khớp regex ở vị trí tiếp theo.
- **matches()** yêu cầu **toàn bộ vùng được xét** khớp regex.
- **lookingAt()** yêu cầu regex khớp ngay **đầu vùng**, không nhất thiết hết vùng.
- **group()** lấy văn bản vừa khớp; **start()** và **end()** trả về offset UTF-16 bắt đầu và ngay sau đoạn khớp.

Ví dụ tìm các mã theo mẫu:

~~~java
Pattern pattern = Pattern.compile("item-\\d+");
Matcher matcher = pattern.matcher("item-42, item-7");

while (matcher.find()) {
    System.out.println(matcher.group());
}
~~~

Stdout:

~~~text
item-42
item-7
~~~

**\\d** trong regex đại diện cho chữ số theo quy tắc của Java Pattern; **+** nghĩa là ít nhất một lần. Trong **mã nguồn Java**, regex này phải viết **"item-\\d+"** để giá trị String thực tế đưa vào engine chứa dấu gạch chéo ngược.

### find(), matches() và String.matches()

~~~java
Pattern digits = Pattern.compile("\\d+");

System.out.println(digits.matcher("abc123xyz").find());      // true
System.out.println(digits.matcher("abc123xyz").matches());   // false
System.out.println(digits.matcher("123abc").lookingAt());    // true
System.out.println("123".matches("\\d+"));                   // true
System.out.println("abc123xyz".matches("\\d+"));             // false
~~~

Các câu lệnh có import **Pattern** ở trên là minh họa trong một phương thức.

**String.matches(regex)** cũng kiểm tra **toàn chuỗi**. Vì vậy **"abc123".matches("\\d+")** là false, dù phần **123** hoàn toàn khớp regex. Nếu muốn **tìm xem có tồn tại một đoạn khớp** trong văn bản, dùng **Pattern.compile(regex).matcher(text).find()**.

Không nên máy móc bọc regex bằng **".*"** ở hai đầu để giả lập find(): quy tắc sẽ khó đọc hơn, dễ bị ảnh hưởng bởi cách **.** xử lý newline và có thể phát sinh chi phí xử lý không cần thiết.

## 4. Chạy ví dụ Java 17+: so sánh literal với regex

Lưu thành **LiteralVsRegexSearchDemo.java** theo UTF-8:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiteralVsRegexSearchDemo {
    public static void main(String[] args) {
        String text = "file.txt | fileXtxt | item-42 | ITEM-7";

        System.out.println(text.contains("file.txt"));
        System.out.println(text.indexOf("file.txt"));
        System.out.println(text.indexOf("fileXtxt"));

        Pattern exactDot = Pattern.compile("file\\.txt");
        Pattern wildcardDot = Pattern.compile("file.txt");

        System.out.println(exactDot.matcher(text).find());
        System.out.println(wildcardDot.matcher("fileXtxt").matches());
        System.out.println(exactDot.matcher("fileXtxt").matches());
        System.out.println(text.matches("file\\.txt"));

        Matcher items = Pattern.compile("(?i)item-\\d+")
                .matcher(text);

        while (items.find()) {
            System.out.printf("%s@%d-%d%n",
                    items.group(), items.start(), items.end());
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 LiteralVsRegexSearchDemo.java
java LiteralVsRegexSearchDemo
~~~

Stdout:

~~~text
true
0
11
true
true
false
false
item-42@22-29
ITEM-7@32-38
~~~

Giải thích:

1. **contains("file.txt")** tìm chuỗi nguyên văn có dấu chấm, trả true.
2. **indexOf("file.txt") == 0**, còn **fileXtxt** bắt đầu tại offset 11.
3. Regex **file\\.txt** trong Java yêu cầu **dấu chấm thật**; vì vậy tìm được file.txt nhưng không khớp fileXtxt.
4. Regex **file.txt** cho phép một ký tự bất kỳ tại dấu chấm và vì vậy khớp cả fileXtxt.
5. **text.matches("file\\.txt")** trả false vì text dài hơn file.txt; **matches** yêu cầu khớp toàn bộ.
6. Flag inline **(?i)** bật so khớp không phân biệt hoa thường, nên tìm được item-42 và ITEM-7.
7. **start()/end()** trả về offset UTF-16, trong đó **end()** không được tính vào đoạn khớp. Có thể dùng **text.substring(start, end)** để lấy lại đoạn đã khớp.

## 5. Các ký hiệu regex dễ bị hiểu thành văn bản nguyên vẹn

| Cú pháp regex | Ý nghĩa theo mẫu | Khi muốn tìm literal |
| --- | --- | --- |
| **.** | Một ký tự bất kỳ, ngoại trừ một số line terminator theo chế độ | Escape thành **\\.** |
| **\\d** | Chữ số | Quote ký tự backslash và d nếu chúng thực sự là dữ liệu |
| **[abc]** | Một ký tự trong tập a, b, c | Quote cả chuỗi **[abc]** |
| **a+** | Một hoặc nhiều ký tự a | Escape dấu cộng nếu cần tìm **a+** |
| **a*** | Không hoặc nhiều ký tự a | Escape dấu sao nếu cần tìm **a*** |
| **a?** | Không hoặc một ký tự a | Escape dấu hỏi nếu cần tìm **a?** |
| **^abc** | Mẫu tại đầu theo chế độ | Quote dấu mũ nếu là dữ liệu |
| **abc$** | Mẫu tại cuối theo chế độ | Quote dấu đô-la nếu là dữ liệu |
| **(abc)** | Nhóm regex | Quote dấu ngoặc nếu cần tìm văn bản có ngoặc |
| **a\|b** | Khớp a hoặc b | Quote dấu pipe khi nó là văn bản |
| **\\bcat\\b** | Từ cat với ranh giới từ | Tìm bằng regex khi nghiệp vụ thực sự cần ranh giới |

Regex có các cấu trúc nhóm, character class, quantifier, anchor, lookaround. Chúng rất hữu ích khi đầu vào là một **mẫu có chủ đích**, nhưng sẽ gây kết quả sai nếu lấy nguyên từ khóa người dùng rồi coi như mẫu.

### Tìm từ nguyên vẹn khác tìm một phần của từ

~~~java
String text = "cat scatter cat";
Pattern word = Pattern.compile("\\bcat\\b");
Matcher matcher = word.matcher(text);
while (matcher.find()) {
    System.out.println(matcher.group());
}
~~~

Đoạn minh họa trên in:

~~~text
cat
cat
~~~

**String.contains("cat")** vẫn trả true vì chứa đoạn cat; **indexOf** còn có thể tìm chuỗi con cat bên trong scatter. Regex **\\bcat\\b** giới hạn bằng ranh giới từ và không nhận chữ cat bên trong scatter. Quy tắc word boundary của regex không thay thế hoàn toàn tách từ ngôn ngữ tự nhiên, nhất là văn bản đa ngữ.

## 6. Hiểu đúng hai tầng escape: Java literal và regex

Giả sử muốn tìm dấu chấm thật:

1. Văn bản cần khớp: **.**
2. Cú pháp regex: **\\.**
3. Java string literal truyền vào Pattern: **"\\."**

**Java xử lý escape của string literal trước; regex engine đọc kết quả sau đó.**

| Ý định | Regex thực tế | Viết trong Java String literal |
| --- | --- | --- |
| Dấu chấm | **\\.** | **"\\."** |
| Dấu cộng | **\\+** | **"\\+"** |
| Dấu hỏi | **\\?** | **"\\?"** |
| Một backslash | **\\\\** | **"\\\\\\\\"** |
| Một hoặc nhiều chữ số | **\\d+** | **"\\d+"** |
| Ba ký tự a, ., b | **a\\.b** | **"a\\.b"** |

Với biến **query** đã là String chứa dữ liệu người dùng, không áp dụng quy tắc escape Java string literal thêm lần nữa. Ví dụ **query = "a.b"** được nhận từ một form đã là văn bản thực tế. Để tìm nguyên văn, gọi **text.contains(query)**; để tìm bằng Matcher mà vẫn giữ literal, dùng **Pattern.quote(query)**.

## 7. Tìm nguyên văn bằng Pattern: quote và LITERAL

Đôi khi ứng dụng muốn duyệt từng lần xuất hiện, lấy **Matcher.start()/end()** hoặc kết hợp một phần literal vào mẫu regex lớn. Lúc đó có hai công cụ quan trọng.

### 7.1. Pattern.quote(query)

**Pattern.quote(String)** biến query thành một đoạn regex có nghĩa **khớp nguyên văn toàn bộ nội dung query**, kể cả dấu cộng, dấu hỏi, dấu ngoặc, backslash hoặc chuỗi **\\E**.

~~~java
String query = "a.b";
Pattern literalPattern = Pattern.compile(Pattern.quote(query));
System.out.println(literalPattern.matcher("aXb a.b").find()); // true
~~~

Đoạn trên nằm trong một phương thức có import **Pattern**. Kết quả true vì văn bản có **a.b** ở cuối; regex này không nhận aXb làm đoạn literal.

Đừng tự ghép **"\\Q" + query + "\\E"** cho đầu vào tùy ý: bản thân query có thể chứa ký hiệu **\\E**. **Pattern.quote** đã xử lý trường hợp đặc biệt đó.

Khi chỉ **một phần** mẫu là dữ liệu người dùng, quote phần đó:

~~~java
String query = "a.b";
Pattern token = Pattern.compile(
        "start:" + Pattern.quote(query) + ":end");

System.out.println(token.matcher("start:a.b:end").matches()); // true
System.out.println(token.matcher("start:aXb:end").matches()); // false
~~~

Phần **start:** và **:end** ở ví dụ này cố định; query là văn bản nguyên vẹn được chèn vào giữa. Nếu các phần cố định có ký hiệu đặc biệt của regex thì chính chúng cũng phải được thiết kế hoặc quote thích hợp.

### 7.2. Pattern.LITERAL

**Pattern.LITERAL** khiến **toàn bộ mẫu đầu vào** được hiểu là văn bản nguyên vẹn, không phân tích các regex metacharacter:

~~~java
String query = "a.b";
Pattern literal = Pattern.compile(query, Pattern.LITERAL);

System.out.println(literal.matcher("aXb").find()); // false
System.out.println(literal.matcher("a.b").find()); // true
~~~

**Pattern.LITERAL** phù hợp khi cả mẫu là literal. **Pattern.quote** linh hoạt hơn khi phải trộn regex cố định với một đoạn dữ liệu literal. Các flag về cách khớp hoa/thường như **CASE_INSENSITIVE** và **UNICODE_CASE** vẫn có thể có tác dụng với mẫu **LITERAL**.

### Ví dụ Java 17+ đầy đủ

Lưu thành **SafeLiteralSearchDemo.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafeLiteralSearchDemo {
    static void printMatches(String label, Pattern p, String text) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            System.out.printf("%s=%d:%s%n",
                    label, m.start(), m.group());
        }
    }

    public static void main(String[] args) {
        String text = "a.b aXb [a.b]";
        String query = "a.b";

        System.out.println("contains=" + text.contains(query));
        System.out.println("index=" + text.indexOf(query));

        printMatches("regex", Pattern.compile(query), text);
        printMatches("quoted", Pattern.compile(Pattern.quote(query)), text);
        printMatches("literal",
                Pattern.compile(query, Pattern.LITERAL), text);
    }
}
~~~

~~~bash
javac -encoding UTF-8 SafeLiteralSearchDemo.java
java SafeLiteralSearchDemo
~~~

Stdout:

~~~text
contains=true
index=0
regex=0:a.b
regex=4:aXb
regex=9:a.b
quoted=0:a.b
quoted=9:a.b
literal=0:a.b
literal=9:a.b
~~~

Đầu vào có **3 đoạn** khớp regex **a.b**, vì wildcard cũng nhận aXb, trong khi **quote** và **LITERAL** chỉ khớp **2 đoạn** a.b nguyên văn. Vị trí 0, 4 và 9 là offset UTF-16.

## 8. Phân biệt với replace, replaceAll và split

Nhiều nhầm lẫn xuất phát từ việc một số API của String dùng literal, nhưng các API khác lại nhận regex.

| API | Nghĩa của đối số tìm kiếm |
| --- | --- |
| **contains(CharSequence)** | Literal |
| **indexOf(String)** | Literal |
| **replace(CharSequence, CharSequence)** | Literal cho đoạn cần thay và đoạn thay thế |
| **replaceAll(String regex, String replacement)** | Regex |
| **replaceFirst(String regex, String replacement)** | Regex |
| **split(String regex)** | Regex để xác định dấu phân cách |
| **matches(String regex)** | Regex yêu cầu khớp toàn chuỗi |

Ví dụ:

~~~java
String file = "one.two.three";
System.out.println(file.replace(".", "-"));     // one-two-three
System.out.println(file.replaceAll("\\.", "-")); // one-two-three
System.out.println(file.replaceAll(".", "-"));   // -------------
~~~

Regex **.** trong **replaceAll** khớp từng ký tự ở ví dụ không có line terminator. Với **split**, muốn tách theo dấu chấm thì viết **split("\\.")** hoặc **split(Pattern.quote("."))** thay vì **split(".")**.

**split(regex)** có giới hạn ngầm **limit = 0**, làm bỏ các phần tử rỗng ở cuối. Nếu muốn giữ các phần rỗng cuối chuỗi, dùng **split(regex, -1)**.

### Replacement là cú pháp riêng

Đối số **replacement** của **Matcher.replaceAll** và **String.replaceAll** có cách hiểu đặc biệt với ký hiệu **$** tham chiếu capture group và backslash escape. Khi replacement là dữ liệu người dùng, dùng **Matcher.quoteReplacement(replacement)**, khác với **Pattern.quote(query)** dùng cho mẫu tìm:

~~~java
String text = "cost=42";
String replacement = "$5";
String safe = Pattern.compile("\\d+")
        .matcher(text)
        .replaceAll(Matcher.quoteReplacement(replacement));
System.out.println(safe); // cost=$5
~~~

Đoạn minh họa nằm trong phương thức có import **Pattern** và **Matcher**. Nếu không quote replacement, ký hiệu **$5** có thể bị diễn giải thành tham chiếu nhóm không có thật và gây lỗi. Với **String.replace(old, new)**, đoạn thay thế là văn bản nguyên vẹn.

## 9. Hoa/thường và Unicode là những quy tắc độc lập

**String.contains** và **indexOf** tìm nguyên văn có phân biệt chữ hoa/thường. Regex có thể dùng:

~~~java
Pattern insensitive = Pattern.compile(
        "java", Pattern.CASE_INSENSITIVE);
System.out.println(insensitive.matcher("JAVA").find()); // true
~~~

Nếu cần case folding Unicode theo Java Pattern, dùng thêm **Pattern.UNICODE_CASE** cùng **CASE_INSENSITIVE**. Chọn rõ chính sách nghiệp vụ; việc tìm không phân biệt hoa/thường không tự đồng nghĩa với tìm bỏ dấu tiếng Việt.

String và regex cũng không tự bảo đảm hai cách mã hóa Unicode có hình thức hiển thị giống nhau sẽ khớp:

~~~java
String composed = "\u00E1";     // á dựng sẵn
String decomposed = "a\u0301"; // a + dấu sắc kết hợp

System.out.println(composed.contains(decomposed)); // false
~~~

Nếu cần canonical equivalence cho tiếng Việt, chuẩn hóa dữ liệu và truy vấn theo cùng một quy tắc, ví dụ **java.text.Normalizer.Form.NFC**, trước khi tìm. Regex có **Pattern.CANON_EQ** nhưng cách sử dụng và chi phí cần đánh giá; chuẩn hóa nhất quán thường dễ kiểm soát hơn cho tìm kiếm thông thường.

Một regex có thể khớp một hoặc nhiều code point; “một ký tự hiển thị” vẫn có thể là grapheme cluster gồm nhiều code point. Nếu thật sự xử lý grapheme, Java Pattern hỗ trợ **\\X**; đó là một bài toán khác với tìm chuỗi nguyên văn.

## 10. Hiệu năng, an toàn và đầu vào người dùng

**Tìm nguyên văn đơn giản:** ưu tiên **contains** hoặc **indexOf**. Ý định được thể hiện trực tiếp và không phải biên dịch regex.

**Tìm theo mẫu thực sự:** biên dịch **Pattern** và dùng **Matcher**. Nếu lặp lại nhiều lần với cùng regex, có thể tái sử dụng **Pattern**. Một **Pattern** là đối tượng bất biến và có thể dùng chung giữa các luồng; **Matcher** chứa trạng thái tìm kiếm biến đổi, nên mỗi tác vụ hoặc luồng cần Matcher riêng.

**Đầu vào là từ khóa người dùng:** không gọi **Pattern.compile(userInput)** trừ khi chức năng thực sự cho phép người dùng viết regex. Ký tự như **[**, **(** có thể gây **PatternSyntaxException**; lượng từ hoặc điều kiện khác có thể thay đổi hoàn toàn kết quả.

~~~java
String query = userInput; // giá trị đến từ form hoặc API
boolean found = text.contains(query);

// Nếu cần vị trí các lần khớp do Matcher cung cấp:
Pattern safe = Pattern.compile(Pattern.quote(query));
boolean foundByMatcher = safe.matcher(text).find();
~~~

Đoạn trên giả định **text**, **userInput** đã được khai báo, với import Pattern. Với từ khóa null hoặc rỗng, áp dụng hợp đồng đầu vào trước khi tìm.

**Cho phép người dùng nhập regex một cách có chủ đích:** bắt **PatternSyntaxException** để báo lỗi cú pháp có ích:

~~~java
try {
    Pattern userPattern = Pattern.compile("[unclosed");
} catch (java.util.regex.PatternSyntaxException ex) {
    System.out.println("Invalid regex");
}
~~~

Ngoài lỗi cú pháp, regex trên dữ liệu không đáng tin cậy có thể gây **quá tải do backtracking** trong những mẫu phức tạp. Nếu hỗ trợ chế độ tìm regex, nên giới hạn đầu vào, kiểm soát độ phức tạp và kiểm thử những mẫu tốn tài nguyên. **Pattern.quote(query)** giúp tránh diễn giải các ký hiệu regex khi ý định là literal.

## 11. Chọn API theo tình huống

| Yêu cầu | Cách phù hợp | Lý do |
| --- | --- | --- |
| Có chứa đúng **item.42** không? | **text.contains("item.42")** | Dấu chấm là dữ liệu |
| Vị trí đầu tiên của một URL/ID cố định? | **text.indexOf(query)** | Trả về offset UTF-16 |
| Tìm mã **item-** kèm nhiều chữ số | **Pattern.compile("item-\\d+").matcher(text).find()** | Mẫu thay đổi phần chữ số |
| Toàn bộ chuỗi chỉ có ba chữ cái và bốn chữ số | **text.matches("[A-Za-z]{3}\\d{4}")** | Xác thực toàn chuỗi |
| Người dùng nhập dấu ngoặc, dấu cộng, dấu chấm và cần offsets | **Pattern.compile(Pattern.quote(query))** | Tìm literal bằng Matcher |
| Toàn bộ regex phải được hiểu là văn bản | **Pattern.compile(query, Pattern.LITERAL)** | Không diễn giải ký hiệu regex |
| Tách dữ liệu bằng dấu chấm thật | **text.split("\\.")** | split nhận regex |
| Thay dấu chấm bằng dấu gạch ngang | **text.replace(".", "-")** | replace tìm nguyên văn |

Trước khi triển khai một ô tìm kiếm, xác định rõ: literal hay regex, khớp toàn bộ hay một đoạn, có phân biệt chữ hoa/thường không, xử lý Unicode normalization thế nào, có cần tất cả vị trí khớp không và đầu vào rỗng được hiểu ra sao.

## 12. Những lỗi phổ biến

1. **Dùng text.matches("abc") để hỏi có chứa abc không.** Vì matches đòi khớp toàn bộ, dùng **contains** hoặc **Matcher.find**.
2. **Tưởng regex a.b chỉ khớp đúng a.b.** Escape dấu chấm (**"a\\.b"**), hoặc dùng **Pattern.quote**.
3. **Truyền query nguyên văn cho Pattern.compile(query).** Query có thể chứa metacharacter, sai kết quả hoặc gây lỗi cú pháp. Dùng literal search khi thích hợp.
4. **Quên hai tầng escape Java và regex.** Regex **\\.** được viết **"\\."** trong mã Java.
5. **Dùng split(".") để tách dấu chấm.** Regex **.** là wildcard; dùng **split("\\.")**.
6. **Dùng replaceAll(".", "-") để đổi duy nhất dấu chấm.** Nó thay tất cả ký tự khớp wildcard; dùng **replace(".", "-")** hoặc regex escape.
7. **Nhầm quoteReplacement với Pattern.quote.** Một cái xử lý văn bản thay thế, cái kia xử lý từ khóa tìm kiếm.
8. **Tưởng Matcher.start()/end() là số Unicode code point.** Chúng là offset UTF-16; đặc biệt lưu ý emoji ngoài BMP.
9. **Bỏ qua normalization.** Hai chuỗi tiếng Việt cùng hiển thị có thể khác mã hóa và không được tìm thấy nếu không có chính sách chuẩn hóa.
10. **Dùng lại Matcher giữa các luồng.** Matcher mutable, nên tạo riêng từ Pattern đã biên dịch.
11. **Coi mọi regex đều an toàn vì compile thành công.** Regex có thể tiêu thụ nhiều CPU trên một số đầu vào; cần kiểm soát chế độ tìm mẫu do người dùng nhập.

## 13. Tự kiểm tra kiến thức

1. **"a.b"** trong **contains** và **Pattern.compile** khác nhau thế nào? — contains tìm dấu chấm thật; regex a.b cho phép một ký tự bất kỳ ở vị trí giữa.
2. Tại sao **"abc123".matches("\\d+")** là false nhưng **Pattern.compile("\\d+").matcher("abc123").find()** là true? — Khớp toàn bộ khác khớp một đoạn con.
3. Tìm chính xác ký tự dấu chấm bằng String làm thế nào? — **contains(".")** hoặc **indexOf(".")**.
4. Khi query do người dùng nhập có **[**, **?**, **+**, làm sao tìm nguyên văn bằng Matcher? — **Pattern.compile(Pattern.quote(query))**.
5. **Pattern.LITERAL** khác **Pattern.quote** ở đâu? — LITERAL áp dụng cho toàn bộ mẫu; quote cho phép quote một thành phần hoặc cả từ khóa khi xây regex.
6. **Matcher.quoteReplacement** dùng để làm gì? — Giữ nguyên văn bản thay thế, không để **$** và backslash bị diễn giải là cú pháp replacement.
7. **Matcher.start()** trả về loại vị trí nào? — Offset UTF-16.
8. Tìm tiếng Việt muốn coi chữ dạng NFC và NFD tương đương thì làm gì? — Xác định chính sách chuẩn hóa và chuẩn hóa nhất quán dữ liệu với query trước khi tìm.

## 14. Ghi nhớ

- **Tìm nguyên văn:** **String.contains**, **indexOf**, **startsWith**, **endsWith**. Ký tự đặc biệt của regex chỉ là ký tự dữ liệu.
- **Tìm theo mẫu:** **Pattern.compile(regex)** và **Matcher.find()**; kiểm tra full match bằng **Matcher.matches()** hoặc **String.matches(regex)**.
- **Dấu chấm regex . là wildcard**; regex khớp dấu chấm thật cần escape, viết **"\\."** trong Java.
- **Từ khóa người dùng cần tìm nguyên văn:** dùng **Pattern.quote(query)** hoặc **Pattern.LITERAL** khi vẫn cần Regex API.
- **contains/indexOf/replace** và **matches/replaceAll/split** có ngữ nghĩa khác nhau. Chọn đúng API trước khi xử lý escape.
- **Hoa thường, Unicode normalization và grapheme** là các yêu cầu riêng, không tự được giải quyết chỉ nhờ chọn literal hoặc regex.

Tham khảo Java SE 17: **java.lang.String** (contains, indexOf, matches, replace, replaceAll, split), **java.util.regex.Pattern** (compile, quote, LITERAL, flags) và **java.util.regex.Matcher** (find, matches, lookingAt, group, start, end, quoteReplacement).

</details>

- [Quay lại đầu trang](#back-to-top)
