<a id="back-to-top"></a>

# Thay thế bằng regex

## Menu
- [replaceFirst và replaceAll](#regex-replacement-001)
- [Nhóm bắt và tham chiếu nhóm trong chuỗi thay thế](#regex-replacement-002)
- [Ý nghĩa đặc biệt của dấu đô la và gạch chéo ngược](#regex-replacement-003)
- [Matcher.quoteReplacement cho nội dung thay thế nguyên văn](#regex-replacement-004)
- [Phân biệt escape của mẫu tìm với escape của phần thay thế](#regex-replacement-005)
- [Matcher.replaceAll với hàm tính chuỗi thay thế](#regex-replacement-006)
- [Thay thế nhiều lần và giới hạn của việc dùng regex thay bộ phân tích](#regex-replacement-007)
- [Kiểm thử dữ liệu chứa ký tự có ý nghĩa đặc biệt](#regex-replacement-008)

## <a id="regex-replacement-001">replaceFirst và replaceAll</a>

<details>
<summary>Xem chi tiết</summary>

# replaceFirst và replaceAll: thay thế bằng regex trong Java

Trong Java, lớp **String** có hai phương thức thay thế nội dung theo **biểu thức chính quy (regular expression, regex)**: **replaceFirst** và **replaceAll**. Cả hai tìm đoạn văn bản khớp mẫu rồi tạo một String kết quả; khác biệt cốt lõi là thay **lần khớp đầu tiên** hay **mọi lần khớp**.

## 1. Cú pháp và cách hoạt động

~~~java
String replaceFirst(String regex, String replacement)
String replaceAll(String regex, String replacement)
~~~

| Phương thức | Ý nghĩa | Ví dụ kết quả |
| --- | --- | --- |
| **replaceFirst** | Thay lần khớp đầu tiên từ trái sang phải | "a1 b2".replaceFirst("\\d+", "#") → "a# b2" |
| **replaceAll** | Thay mọi lần khớp không chồng lấn | "a1 b2".replaceAll("\\d+", "#") → "a# b#" |

- **regex** là mẫu tìm kiếm theo cú pháp regex của **java.util.regex.Pattern**.
- **replacement** là chuỗi thay thế theo cú pháp của **java.util.regex.Matcher**. Trong đó, **$** và dấu gạch chéo ngược có ý nghĩa riêng; không phải lúc nào cũng được xem là ký tự thường.
- Hai phương thức **không sửa String gốc**, vì String bất biến. Cần dùng giá trị trả về nếu muốn giữ thay đổi.
- Nếu regex không khớp đoạn nào, kết quả có cùng nội dung với chuỗi đầu vào.
- Bộ máy xét các đoạn khớp từ trái sang phải; kết quả mới chèn vào không bị tìm kiếm lại trong cùng lượt.
- Không cần regex khớp toàn bộ chuỗi: một đoạn con khớp cũng đủ để được thay thế.

Có thể hình dung cách triển khai về mặt ngữ nghĩa:

~~~java
Pattern.compile(regex).matcher(input).replaceFirst(replacement);
Pattern.compile(regex).matcher(input).replaceAll(replacement);
~~~

Đoạn này minh họa cơ chế; để chạy cần khai báo các biến và import **java.util.regex.Pattern**. Với nhu cầu xử lý nhiều chuỗi theo cùng mẫu, có thể tự giữ đối tượng Pattern để tái sử dụng (xem phần 8).

## 2. Chương trình Java 17+ chạy được

Lưu tệp **ReplaceRegexDemo.java**:

~~~java
public class ReplaceRegexDemo {
    public static void main(String[] args) {
        String original = "Mã A12, B345 và C6";

        System.out.println(original.replaceFirst("\\d+", "#"));
        System.out.println(original.replaceAll("\\d+", "#"));
        System.out.println("a.b.c".replaceAll("\\.", "-"));
        System.out.println("Java JAVA java".replaceAll("(?i)java", "JVM"));
        System.out.println(
                "2026-10-23".replaceFirst(
                        "(\\d{4})-(\\d{2})-(\\d{2})", "$3/$2/$1"));
        System.out.println("ababa".replaceAll("aba", "X"));
        System.out.println(
                "  Xin   chào   Java  ".replaceAll("\\s+", " ").trim());
    }
}
~~~

Chạy bằng:

~~~bash
javac ReplaceRegexDemo.java
java ReplaceRegexDemo
~~~

Stdout:

~~~text
Mã A#, B345 và C6
Mã A#, B# và C#
a-b-c
JVM JVM JVM
23/10/2026
Xba
Xin chào Java
~~~

Giải thích lần lượt: **\\d+** khớp một cụm chữ số; **\\.** khớp dấu chấm thật; **(?i)** bật so khớp không phân biệt hoa/thường; **$3/$2/$1** sắp xếp lại dữ liệu của ba nhóm bắt giữ; **"ababa"** chỉ có một lần khớp "aba" không chồng lấn khi quét từ trái sang phải; **\\s+** gộp cụm khoảng trắng, còn **trim()** bỏ khoảng trắng hai đầu.

## 3. Hai lớp escaping: Java String và regex

Chuỗi regex đi qua **hai bộ phân tích**: trình biên dịch Java đọc String literal trước, sau đó Pattern diễn giải chuỗi nhận được thành regex. Vì thế, nhiều dấu gạch chéo ngược phải viết đôi trong mã Java.

| Muốn tìm | Mẫu regex | String literal trong Java |
| --- | --- | --- |
| Một hay nhiều chữ số | \d+ | "\\d+" |
| Một hay nhiều khoảng trắng | \s+ | "\\s+" |
| Dấu chấm thật | \. | "\\." |
| Dấu gạch chéo ngược thật | \\ | "\\\\" |
| Chữ hoa từ A đến Z | [A-Z] | "[A-Z]" |
| Chuỗi bắt đầu bằng ID- | ^ID- | "^ID-" |

Một số cú pháp regex thường dùng:

- **.**: bất kỳ ký tự nào trừ một số ký tự kết thúc dòng theo chế độ mặc định.
- **[0-9]**, **[A-Z]**, **[^,]**: lớp ký tự.
- **+**: ít nhất một lần; **\***: không hoặc nhiều lần; **?**: có hoặc không; **{n}**: đúng n lần.
- **^** và **$**: neo đầu và cuối chuỗi (có thể thay đổi theo chế độ MULTILINE).
- **(...)**: nhóm bắt giữ; **(?:...)**: nhóm không bắt giữ.
- **(?i)**: cờ bỏ qua khác biệt chữ hoa/thường; **(?m)**: cho ^/$ hoạt động theo từng dòng.
- **\\s+**: gom các ký tự khoảng trắng; **\\d{4}**: đúng bốn chữ số.

Ví dụ chỉ xóa khoảng trắng **ở đầu**:

~~~java
String input = "   Java   regex   ";
String first = input.replaceFirst("^\\s+", ""); // "Java   regex   "
String all   = input.replaceAll("\\s+", " ");   // " Java regex "
~~~

Hai dòng khác mục tiêu: dùng **^** để chỉ chọn phần đầu chuỗi, hoặc chuẩn hóa mọi cụm khoảng trắng. Nếu muốn cả chuỗi khớp đúng định dạng, dùng neo đầu/cuối thích hợp thay vì mặc định rằng replaceAll kiểm tra toàn bộ chuỗi.

## 4. Nhóm bắt: tái sử dụng phần đã tìm thấy

Regex **(...)** đặt nhóm bắt theo thứ tự ngoặc mở. Replacement dùng **$1**, **$2**... để lấy từng nhóm, **$0** để lấy toàn bộ lần khớp và **${name}** để lấy nhóm đặt tên bởi **(?<name>...)**. Ví dụ đổi ngày bằng **$3/$2/$1** ở mục 2: nhóm thứ ba chứa ngày, nhóm thứ hai chứa tháng và nhóm thứ nhất chứa năm.

Khi chỉ cần gom nhánh regex mà không muốn thay đổi số nhóm, dùng **(?:...)**. Nhóm tùy chọn đã khai báo nhưng không tham gia một match được thay bằng chuỗi rỗng; tham chiếu tới nhóm không tồn tại lại là lỗi. Bài kế tiếp đào sâu nhóm lồng nhau, nhóm có tên và quy tắc tham chiếu.

## 5. Tìm và chèn văn bản nguyên văn

Với văn bản cần tìm do người dùng nhập như **a.b**, **Pattern.quote(needle)** khiến dấu chấm được hiểu theo nghĩa đen thay vì wildcard. Với văn bản muốn chèn như **$5\backup**, **Matcher.quoteReplacement(value)** giữ nguyên dấu đô la và dấu gạch chéo ngược, tránh nhầm **$5** thành tham chiếu nhóm.

Hai API quote bảo vệ **hai tham số độc lập**; không dùng chúng hoán đổi cho nhau. Nếu cả phần tìm và phần thay đều là chuỗi literal, **String.replace(target, replacement)** đơn giản hơn. Các bài về ký tự đặc biệt và quoteReplacement tiếp tục làm rõ từng trường hợp và cách kiểm thử.

## 6. So sánh với String.replace

**String.replace** hoạt động theo nghĩa literal, không phân tích regex:

~~~java
String input = "a.b 1.2";

System.out.println(input.replace(".", "-"));      // a-b 1-2
System.out.println(input.replaceAll("\\.", "-")); // a-b 1-2
System.out.println(input.replaceAll(".", "-"));    // -------
~~~

- **replace(CharSequence, CharSequence)** thay tất cả lần xuất hiện của văn bản cố định.
- **replace(char, char)** thay tất cả lần xuất hiện của ký tự cố định.
- **replaceAll(regex, replacement)** thay mọi đoạn khớp biểu thức chính quy.
- **replaceFirst(regex, replacement)** chỉ thay đoạn khớp đầu tiên.

Với nhu cầu thay literal thông thường, dùng **replace** để tránh viết regex và tránh quy tắc đặc biệt của replacement. Ví dụ, **"a$b".replace("$", "$$")** trả về **"a$$b"** mà không cần quote replacement.

## 7. Hành vi ở các trường hợp biên

**Không có đoạn khớp:** "abc".replaceAll("\\d+", "#") cho nội dung "abc"; đây là hành vi bình thường.

**Khớp độ dài bằng 0:** regex có neo hoặc lookahead có thể khớp ở vị trí giữa các ký tự mà không tiêu thụ ký tự:

~~~java
System.out.println("abc".replaceFirst("^", ">>"));  // >>abc
System.out.println("abc".replaceAll("(?=b)", "|")); // a|bc
~~~

**Các lần khớp không chồng lấn:** "ababa".replaceAll("aba", "X") là **"Xba"**, không phải kết quả của việc thay tất cả các vị trí bắt đầu có thể chồng lên nhau.

**Nhiều dòng:** dấu chấm **.** mặc định không khớp một số ký tự kết thúc dòng. Nếu muốn dấu chấm bao gồm cả xuống dòng, cân nhắc chế độ **DOTALL**, ví dụ tiền tố **(?s)**. Cờ **(?m)** phục vụ cách hiểu ^ và $ theo dòng; hai cờ không thay thế nhau.

**Lỗi regex/replacement:** regex không hợp lệ như **[** gây **PatternSyntaxException**; tham chiếu nhóm không hợp lệ hay cú pháp **$**/**\\** sai trong replacement có thể gây **IllegalArgumentException** hoặc **IndexOutOfBoundsException**. Tham số null có thể dẫn đến **NullPointerException**. Cần validate đầu vào và thông báo lỗi rõ ràng cho người sử dụng.

**String bất biến:**

~~~java
String original = "a1 b2";
String updated = original.replaceAll("\\d", "#");

System.out.println(original); // a1 b2
System.out.println(updated);  // a# b#
~~~

Gọi replaceAll mà bỏ qua giá trị trả về thì biến **original** không hề thay đổi.

## 8. Tái sử dụng Pattern và thay thế động

Nếu xử lý nhiều chuỗi với cùng regex, biên dịch Pattern một lần:

~~~java
import java.util.List;
import java.util.regex.Pattern;

public class ReusePatternDemo {
    public static void main(String[] args) {
        Pattern digits = Pattern.compile("\\d+");

        for (String value : List.of("A12", "B345", "C6")) {
            System.out.println(digits.matcher(value).replaceAll("#"));
        }
    }
}
~~~

Lưu thành **ReusePatternDemo.java**, chạy:

~~~bash
javac ReusePatternDemo.java
java ReusePatternDemo
~~~

Stdout:

~~~text
A#
B#
C#
~~~

**Pattern** là đối tượng bất biến và có thể dùng chung giữa các luồng. **Matcher** lưu trạng thái của một lượt tìm kiếm và không nên chia sẻ để thao tác đồng thời giữa các luồng.

Từ Java 9, **Matcher.replaceAll(Function<MatchResult, String>)** cho phép tính replacement khác nhau cho từng match. Chuỗi hàm trả về vẫn được diễn giải theo cú pháp replacement; nếu muốn chèn nguyên văn nội dung tính được có **$** hoặc dấu gạch chéo ngược, dùng **Matcher.quoteReplacement**. Bài chuyên sâu về callback phân tích cách đọc nhóm, quản lý trạng thái và xử lý lỗi.

## 9. Lỗi hay gặp, cách phòng tránh

| Lỗi | Nguyên nhân | Cách giải quyết |
| --- | --- | --- |
| replaceAll(".", "") làm mất gần hết văn bản | Dấu chấm là wildcard | Dùng "\\." hoặc Pattern.quote(".") |
| Viết "\d" khiến Java báo lỗi escape | Java String cần escape dấu gạch chéo ngược | Dùng "\\d" trong mã Java |
| Replacement chứa "$" dẫn đến lỗi nhóm | $ được phân tích theo tham chiếu nhóm | Dùng Matcher.quoteReplacement |
| Chỉ thay được lần đầu trong khi muốn thay mọi lần | Đang dùng replaceFirst | Dùng replaceAll |
| Muốn tìm văn bản cố định nhưng regex khớp quá rộng | Dữ liệu literal bị hiểu là regex | Dùng replace hoặc Pattern.quote |
| Kết quả không thay đổi trên biến gốc | String bất biến | Gán biến bằng giá trị trả về |
| Xử lý lặp nhiều chuỗi gây tốn thời gian | Regex được biên dịch lại nhiều lần | Tạo một Pattern và dùng matcher riêng cho từng chuỗi |

Các regex có lượng từ lồng nhau hoặc cấu trúc backtracking phức tạp có thể xử lý rất chậm trên đầu vào đặc biệt dài. Với dữ liệu ngoài, nên giới hạn kích thước hợp lý, kiểm soát regex được chấp nhận và ưu tiên tìm kiếm literal khi bài toán chỉ cần khớp ký tự chính xác.

## 10. Tự kiểm tra và cách lựa chọn

Cho chuỗi **"x9 y88 z777"**, hãy dự đoán:

~~~java
String s = "x9 y88 z777";
System.out.println(s.replaceFirst("\\d+", "?"));
System.out.println(s.replaceAll("\\d+", "?"));
~~~

Đáp án stdout:

~~~text
x? y88 z777
x? y? z?
~~~

Khi chỉ muốn thay **một** đoạn khớp đầu tiên, chọn **replaceFirst**. Khi muốn thay **tất cả** đoạn khớp, chọn **replaceAll**. Khi không cần biểu thức chính quy, dùng **replace**. Nếu phải thay nhiều chuỗi theo cùng mẫu, có thể dùng **Pattern** tái sử dụng; nếu dữ liệu phải được hiểu theo nghĩa literal, dùng đúng **Pattern.quote** và **Matcher.quoteReplacement** cho từng phía.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-002">Nhóm bắt và tham chiếu nhóm trong chuỗi thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# Nhóm bắt và tham chiếu nhóm trong chuỗi thay thế — Java Regex

Khi xử lý văn bản, ta có thể cần **đảo vị trí, giữ lại hoặc kết hợp những phần của đoạn khớp**. Java Regex cho phép dùng *nhóm bắt* (capturing group) trong biểu thức tìm kiếm và *tham chiếu nhóm* (group reference) trong chuỗi thay thế để tái sử dụng chính dữ liệu đã khớp.

Ví dụ đổi ngày `2026-10-23` thành `23/10/2026`:

```java
String result = "2026-10-23".replaceAll(
        "(\\d{4})-(\\d{2})-(\\d{2})",
        "$3/$2/$1");
System.out.println(result); // 23/10/2026
```

Ba nhóm lần lượt bắt năm, tháng, ngày. Chuỗi thay thế dùng `$3` (ngày), `$2` (tháng), `$1` (năm). Đây là cách **sắp xếp lại dữ liệu từ match** thay vì cắt chuỗi thủ công.

## 1. Nhóm bắt được tạo và đánh số như thế nào?

Cặp ngoặc tròn `(...)` trong regex tạo **nhóm bắt**. Khi tìm thấy một đoạn khớp, Java ghi lại chuỗi con mà mỗi nhóm đã khớp.

Xét regex:

```regex
(\d{4})-(\d{2})-(\d{2})
```

Với đầu vào `2026-10-23`:

| Số nhóm | Giá trị | Ý nghĩa |
| --- | --- | --- |
| 0 | `2026-10-23` | **Toàn bộ** đoạn khớp |
| 1 | `2026` | Nhóm đầu: năm |
| 2 | `10` | Nhóm thứ hai: tháng |
| 3 | `23` | Nhóm thứ ba: ngày |

Quy tắc đánh số: đếm **theo thứ tự dấu ngoặc mở `(` của các nhóm bắt, từ trái qua phải**, bắt đầu từ 1. Nhóm 0 luôn là toàn bộ match và không đòi hỏi cặp ngoặc riêng.

Với nhóm lồng nhau `((ab)(cd))` trên `abcd`, nhóm 1 là `abcd`, nhóm 2 là `ab`, nhóm 3 là `cd`, do vị trí ba dấu ngoặc mở.

Khi cần gom các phần của regex nhưng **không muốn tạo nhóm bắt**, dùng `(?:...)` (non-capturing group):

```regex
(?:Mr\.|Ms\.)\s+([A-Za-z]+)
```

`(?:Mr\.|Ms\.)` gom hai lựa chọn tiền tố mà không chiếm số nhóm; `([A-Za-z]+)` là **nhóm 1** để lấy tên. Không cần tham chiếu đến một nhóm thì nên dùng `(?:...)` nhằm tránh thay đổi thứ tự các nhóm quan trọng.

## 2. Tham chiếu nhóm trong chuỗi thay thế

`String.replaceAll(regex, replacement)` và `String.replaceFirst(regex, replacement)` sử dụng cú pháp replacement của `Matcher`:

| Cú pháp trong replacement | Ý nghĩa |
| --- | --- |
| `$1`, `$2`, ... | Chèn văn bản đã khớp bởi nhóm bắt số 1, 2, ... |
| `$0` | Chèn **toàn bộ** match (nhóm 0) |
| `${name}` | Chèn văn bản khớp bởi **nhóm có tên** |
| `\$` ở runtime | Chèn ký tự `$` literal |
| `\\` ở runtime | Chèn dấu `\` literal |

Trong mã nguồn Java, `"$1"` không cần escape dấu `$`. Khi muốn viết dấu `$` literal trực tiếp trong replacement Java, giá trị runtime `\$` được biểu diễn bằng `"\\$"`; khi muốn chèn ký tự `\` literal, cần biểu diễn runtime `\\`, tức Java string `"\\\\"`. Với dữ liệu runtime không cố định, sử dụng `Matcher.quoteReplacement` sẽ đơn giản và an toàn hơn.

`${name}` chỉ dành cho **tên nhóm**, không viết `${1}` để thay cho `$1`.

Ví dụ dùng nhóm 0 và nhóm 1:

```java
String s = "id=42";
System.out.println(s.replaceAll("(\\d+)", "<$0|$1>"));
// id=<42|42>
```

Cả nhóm 0 và nhóm 1 đều là `42` trong ví dụ này vì regex chứa một nhóm bao trọn match. Nếu regex có phần nằm ngoài ngoặc, nhóm 0 và nhóm 1 có thể khác.

### Phân biệt tham chiếu khi tìm kiếm và khi thay thế

**Trong regex tìm kiếm**, `\1` nghĩa là yêu cầu phần văn bản tiếp theo bằng chính nội dung nhóm 1 đã khớp (backreference); trong chuỗi Java cần viết `"\\1"`.

**Trong replacement**, `$1` nghĩa là chèn nội dung nhóm 1 vào đầu ra.

```java
System.out.println(
        "ha ha".replaceAll("(\\w+)\\s+\\1", "$1"));
// ha
```

Regex `(\\w+)\\s+\\1` nhận ra một từ đứng cạnh chính nó sau khoảng trắng; `"$1"` giữ lại một từ.

## 3. Nhóm đặt tên: `(?<name>...)` và `${name}`

Với regex có nhiều nhóm, `$1` và `$2` dễ gây nhầm. Java cho phép đặt tên bằng `(?<name>regex)`, sau đó tham chiếu bằng `${name}` trong replacement.

Ví dụ đổi `"Họ, Tên"` thành `"Tên Họ"`:

```java
String names = "Nguyen, An; Tran, Binh";
String reordered = names.replaceAll(
        "(?<last>[A-Za-z]+),\\s*(?<first>[A-Za-z]+)",
        "${first} ${last}");

System.out.println(reordered);
// An Nguyen; Binh Tran
```

Giải thích: `(?<last>[A-Za-z]+)` bắt họ, `,\\s*` khớp dấu phẩy và các khoảng trắng ngay sau nó, `(?<first>[A-Za-z]+)` bắt tên; `${first} ${last}` đưa tên lên trước họ.

Nhóm đặt tên **vẫn được đánh số** như nhóm thường; nó không làm mất khả năng truy cập theo số. Tên nhóm bắt đầu bằng chữ, theo sau là chữ hoặc số, và mỗi tên phải duy nhất trong regex.

Regex `[A-Za-z]+` ở đây dùng cho dữ liệu minh họa chữ Latin không dấu. Họ tên thực tế có thể gồm dấu tiếng Việt, dấu nối, khoảng trắng và quy tắc phức tạp hơn; không nên dùng regex minh họa này để phân tích tên người hoàn chỉnh.

## 4. Ví dụ Java chạy được độc lập

Tạo file `RegexGroupReplacementDemo.java`:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexGroupReplacementDemo {
    public static void main(String[] args) {
        String date = "2026-10-23";
        System.out.println("date     = " + date.replaceAll(
                "(\\d{4})-(\\d{2})-(\\d{2})",
                "$3/$2/$1"));

        String names = "Nguyen, An; Tran, Binh";
        System.out.println("names    = " + names.replaceAll(
                "(?<last>[A-Za-z]+),\\s*(?<first>[A-Za-z]+)",
                "${first} ${last}"));

        // (?:...) gom regex mà không tạo nhóm bắt.
        System.out.println("noncap   = " + "a1 b2".replaceAll(
                "(?:[a-z])([0-9])",
                "[$1]"));

        // $0 là toàn bộ match; $1 là nhóm đầu.
        System.out.println("full     = " + "id=42".replaceAll(
                "(\\d+)",
                "<$0|$1>"));

        // Nhóm tùy chọn không khớp thì thay bằng chuỗi rỗng.
        System.out.println("optional = " + "ab1 a2".replaceAll(
                "(a)(b)?(\\d)",
                "[$1|$2|$3]"));

        // Dấu $ và \ trong replacement là ký tự literal.
        String literal = "$5\\path";
        System.out.println("literal  = " + "amount=42".replaceAll(
                "\\d+",
                Matcher.quoteReplacement(literal)));

        // Thay theo giá trị tính riêng cho từng match.
        Matcher matcher = Pattern.compile("(\\d+)").matcher("a12 b3");
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            int doubled = Integer.parseInt(matcher.group(1)) * 2;
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement("[" + doubled + "]"));
        }
        matcher.appendTail(out);
        System.out.println("computed = " + out);
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac RegexGroupReplacementDemo.java
java RegexGroupReplacementDemo
```

Kết quả stdout chính xác:

```text
date     = 23/10/2026
names    = An Nguyen; Binh Tran
noncap   = [1] [2]
full     = id=<42|42>
optional = [a|b|1] [a||2]
literal  = amount=$5\path
computed = a[24] b[6]
```

`String` trong Java là immutable; `replaceAll` trả kết quả mới, không sửa chuỗi gốc. Nếu không có đoạn nào khớp, nội dung trả về bằng đầu vào. Mỗi match của `replaceAll` sẽ sử dụng **những nhóm được bắt trong chính match ấy**.

## 5. Nhóm tùy chọn, nhóm lặp và nhiều match

### Nhóm có thể không khớp

Với regex `(a)(b)?(\\d)` trên `"ab1 a2"`:

- Match `ab1`: nhóm 1 là `a`, nhóm 2 là `b`, nhóm 3 là `1`.
- Match `a2`: nhóm 1 là `a`, **nhóm 2 không tham gia match**, nhóm 3 là `2`.

Do nhóm 2 tồn tại trong pattern, tham chiếu `$2` là hợp lệ. Nhưng khi nhóm này không tham gia vào một match, Java chèn **chuỗi rỗng**:

```java
System.out.println(
        "ab1 a2".replaceAll("(a)(b)?(\\d)", "[$1|$2|$3]"));
// [a|b|1] [a||2]
```

**Nhóm có tồn tại nhưng không khớp** khác với **nhóm không được khai báo**. Tham chiếu đến nhóm không tồn tại sẽ gây lỗi.

### Nhóm đặt dưới lượng từ

Ví dụ regex `([a-z])+` khớp `abc` nhưng nhóm 1 chứa **lần bắt cuối**, là `c`; nhóm không lưu danh sách tất cả lần lặp. Nếu muốn nhóm 1 chứa cả `abc`, đưa lượng từ vào bên trong: `([a-z]+)`.

### Thay nhiều match

`replaceAll` duyệt các match **không chồng lấp**, theo thứ tự từ trái sang phải, và áp dụng chuỗi replacement cho mỗi match:

```java
System.out.println(
        "A=12; B=345".replaceAll("(\\d+)", "[$1]"));
// A=[12]; B=[345]
```

`$1` lần đầu là `12`, lần sau là `345`. `replaceFirst` chỉ thay match đầu tiên.

## 6. Giữ regex và replacement đúng vai trò

Trong **regex tìm kiếm**, tham chiếu ngược yêu cầu phần văn bản sau khớp lại với một nhóm đã bắt; trong **replacement**, **$1**, **$2** và **${name}** chèn dữ liệu từ nhóm vào kết quả. **String.replace** thì làm việc với chuỗi literal, nên không mở rộng tham chiếu nhóm.

Khi cần chèn nội dung ngoài hệ thống như giá tiền hoặc đường dẫn có **$** và dấu gạch chéo ngược, dùng **Matcher.quoteReplacement**. Nhưng nếu cố ý dùng **$1** để tái sử dụng nhóm bắt, không quote toàn bộ template. Những bài kế tiếp trình bày riêng cú pháp escape và kỹ thuật chèn nguyên văn.

## 7. Truy cập nhóm để tính giá trị riêng cho từng match

**String.replaceAll** phù hợp khi mọi match dùng cùng một template nhóm bắt. Khi cần tính kết quả khác nhau, **Matcher.find()** kết hợp **group(1)**, **appendReplacement** và **appendTail** cho phép lấy nhóm, tính chuỗi mới và giữ phần đuôi chưa khớp; chương trình ở mục 4 đã minh họa quy trình này.

Từ Java 9, **Matcher.replaceAll(Function<MatchResult, String>)** rút gọn việc thay riêng từng match. Trong Java 17, callback **MatchResult** cho phép đọc nhóm bằng chỉ số, còn **Matcher** cũng hỗ trợ nhóm theo tên. Cả giá trị truyền vào appendReplacement lẫn chuỗi callback trả về vẫn tuân theo cú pháp replacement, nên quote khi muốn chèn nguyên văn.

## 8. Những lỗi thường gặp

1. **Đặt `$1` vào regex tìm kiếm.** Tham chiếu nhóm trong *regex* là `\1`; `$1` dành cho *replacement*.
2. **Nhầm nhóm không bắt với nhóm bắt.** `(?:...)` không tạo nhóm và không có số để tham chiếu. Với regex chỉ gồm nhóm không bắt, replacement `$1` là không hợp lệ.
3. **Đánh số theo dấu ngoặc đóng.** Phải đánh số từ trái sang phải theo **ngoặc mở** của nhóm bắt, kể cả với nhóm lồng nhau.
4. **Quên nhóm có thể không tham gia match.** Nhóm có tồn tại nhưng không khớp được thay bằng chuỗi rỗng; nhóm hoàn toàn không tồn tại gây lỗi.
5. **Đưa nguyên chuỗi người dùng làm replacement.** Ký tự `$` và `\` có thể mang nghĩa đặc biệt. Nếu cần literal, dùng `Matcher.quoteReplacement`.
6. **Dùng `$10` rồi hiểu nhầm.** Cú pháp numeric được đọc đủ dài theo số nhóm thực sự tồn tại. Với mười nhóm, `$10` tham chiếu nhóm 10; với một nhóm, `$10` có thể là nhóm 1 rồi ký tự `0`. Nhóm đặt tên giúp hạn chế mơ hồ.
7. **Mong nhóm lặp chứa toàn bộ các lần bắt.** `([a-z])+` thường chỉ để lại lần bắt cuối; `([a-z]+)` giữ cả dãy ký tự trong cùng một match.
8. **Dùng `String.replace` rồi mong `$1` hoạt động.** `String.replace(CharSequence, CharSequence)` thực hiện thay literal; không diễn giải regex hay tham chiếu nhóm.
9. **Dùng regex minh họa làm parser dữ liệu phức tạp.** Có thể cần xử lý Unicode, quoted values, dấu escape hay định dạng nhiều tầng; cần chọn parser phù hợp thay vì dựa vào vài nhóm bắt đơn giản.

Regex không hợp lệ có thể gây `PatternSyntaxException`; replacement sai cú pháp hoặc tham chiếu tới nhóm không tồn tại có thể gây `IllegalArgumentException` hoặc `IndexOutOfBoundsException` tùy tình huống. Phải xác định rõ dữ liệu bên ngoài được phép mang cú pháp regex/replacement hay chỉ là văn bản literal.

## 9. Bài tự luyện

**Bài 1.** Đổi `user:100` thành `100 (user)` bằng hai nhóm:

```java
"user:100".replaceAll("([a-z]+):(\\d+)", "$2 ($1)")
// "100 (user)"
```

**Bài 2.** Vì sao `"a1 b2".replaceAll("(?:[a-z])(\\d)", "$1")` tạo `"1 2"`?

Vì `(?:[a-z])` khớp chữ nhưng **không tạo nhóm bắt**, nên nhóm 1 chính là `(\\d)`. Chữ bị bỏ khi toàn bộ match được thay bằng mỗi chữ số.

**Bài 3.** Muốn chèn đúng hai ký tự `$1` theo nghĩa literal khi thay chữ số:

```java
"abc42".replaceAll("\\d+", Matcher.quoteReplacement("$1"))
// "abc$1"
```

**Bài 4.** Với regex `(?<area>\\d{3})-(?<number>\\d{4})`, replacement nào chỉ giữ số phía sau dấu gạch ngang?

`${number}` (cũng có thể dùng `$2`, nhưng tên nhóm giúp rõ nghĩa).

**Bài 5.** Tại sao `"abc".replaceAll("([a-z])+", "$1")` là `"c"`? Nhóm 1 được lặp và chỉ giữ lần bắt cuối. Muốn giữ cả chuỗi, dùng `([a-z]+)`.

**Ghi nhớ:** `(...)` tạo nhóm bắt; `(?:...)` chỉ nhóm cú pháp; `$1` và `${name}` tái sử dụng nội dung nhóm trong replacement; `Matcher.quoteReplacement` bảo vệ văn bản thay thế literal; `Matcher` phù hợp khi thay thế đòi hỏi tính toán riêng cho từng match.

**Tham khảo chính thức:** [`String.replaceAll` (Java SE 21)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#replaceAll(java.lang.String,java.lang.String)), [`Matcher`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Matcher.html), [`Pattern`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-003">Ý nghĩa đặc biệt của dấu đô la và gạch chéo ngược</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa đặc biệt của dấu đô la (`$`) và gạch chéo ngược (`\`) khi thay thế bằng regex trong Java

## 1. Vì sao cần hiểu cú pháp chuỗi thay thế?

Hai phương thức sau có ý nghĩa khác nhau:

~~~java
input.replace("x", replacement);       // Tìm/thay thế chuỗi literal.
input.replaceAll("x", replacement);    // Tìm bằng regex, thay bằng cú pháp replacement.
~~~

Trong `String.replaceAll(regex, replacement)`, tham số `regex` là biểu thức chính quy. Tham số `replacement` không được chèn nguyên xi: `Matcher` **diễn giải** nó theo một cú pháp riêng. Hai ký tự quan trọng nhất là:

- **`$`** mở đầu tham chiếu đến **nhóm bắt** (capturing group), ví dụ `$1`, `$2`, `$0` và `${name}`.
- **`\`** là ký tự **escape trong replacement**: ký tự ngay sau nó được đưa vào kết quả theo nghĩa đen.

Do đó, văn bản cần chèn như `$100` hay `C:\data` có thể bị hiểu sai hoặc gây lỗi nếu truyền trực tiếp làm replacement. Cách khắc phục là escape đúng hoặc dùng `Matcher.quoteReplacement(...)`.

## 2. Phân biệt ba lớp escape

Trong một câu lệnh `replaceAll`, ta phải phân biệt **Java string literal**, **regex pattern** và **replacement**.

| Lớp | Viết trong Java hoặc ở runtime | Ý nghĩa |
| --- | --- | --- |
| Java string literal | `"\\d+"` | Tạo chuỗi runtime `\d+` |
| Regex pattern | `\d+` | Khớp một hoặc nhiều chữ số |
| Java string literal | `"$1"` | Tạo chuỗi runtime `$1` |
| Replacement | `$1` | Chèn giá trị nhóm bắt số 1 |
| Java string literal | `"\\$"` | Tạo chuỗi runtime `\$` |
| Replacement | `\$` | Chèn một ký tự `$` |
| Java string literal | `"\\\\"` | Tạo chuỗi runtime gồm **hai** dấu `\` |
| Replacement | `\\` | Chèn một ký tự `\` |

- **Lớp Java:** dấu `\` dùng để tạo các escape trong mã nguồn như `\n`, `\t`, `\\`. Một dấu gạch chéo ngược runtime phải được biểu diễn bằng `\\` trong Java string literal thông thường.
- **Lớp regex:** dấu `\` mang ý nghĩa trong mẫu tìm kiếm, ví dụ `\d` (chữ số), `\b` (word boundary), `\.` (dấu chấm thật). Đây là luật của **tham số thứ nhất**.
- **Lớp replacement:** dấu `\` escape ký tự tiếp theo, còn `$` mở đầu tham chiếu nhóm. Đây là luật của **tham số thứ hai**.

Ví dụ:

~~~java
String result = "a1b2".replaceAll("\\d", "\\$");
System.out.println(result);
~~~

Đầu tiên, Java biến literal `"\\d"` thành regex `\d` và `"\\$"` thành replacement runtime `\$`. Regex khớp mỗi chữ số, còn replacement parser chuyển `\$` thành dấu `$`. Kết quả: `a$b$`.

## 3. Dấu đô la `$` và tham chiếu nhóm bắt

### 3.1. `$1`, `$2` và `$0`

Trong regex, mỗi cặp ngoặc tròn bắt `(...)` tạo một nhóm. Các nhóm được đánh số từ `1` theo thứ tự dấu ngoặc mở; `(?:...)` là nhóm **không bắt** nên không tăng số. `$0` là toàn bộ phần khớp (tương tự `Matcher.group(0)`).

~~~java
String value = "Mã AB-123";
String result = value.replaceAll(
        "([A-Z]{2})-(\\d+)",
        "$2/$1"
);
System.out.println(result);
~~~

Kết quả:

~~~text
Mã 123/AB
~~~

Ở đây nhóm `1` là `AB`, nhóm `2` là `123`. Chuỗi thay thế `$2/$1` đảo vị trí hai nhóm; phần văn bản không khớp vẫn giữ nguyên.

~~~java
System.out.println("a1b2".replaceAll("\\d", "[$0]"));
~~~

Kết quả: `a[1]b[2]`. Vì mẫu `\d` không khai báo nhóm bắt phụ, dùng `$1` trong ví dụ này là sai, còn `$0` vẫn hợp lệ.

Nếu một nhóm tùy chọn có khai báo nhưng **không tham gia** một lần khớp, tham chiếu đến nhóm đó tạo chuỗi rỗng. Nếu **chỉ số nhóm không tồn tại**, phép thay thế sẽ báo lỗi lúc replacement được xử lý.

### 3.2. Nhóm bắt có tên và `${name}`

Java hỗ trợ khai báo nhóm bằng `(?<name>...)` trong regex và lấy giá trị bằng `${name}` trong replacement:

~~~java
String result = "cat:42".replaceAll(
        "(?<animal>[a-z]+):(?<count>\\d+)",
        "${count}/${animal}"
);
System.out.println(result);
~~~

Kết quả: `42/cat`.

`${animal}` là cú pháp **replacement**, không phải biến Java hay interpolation. Tên nhóm không khai báo sẽ gây lỗi.

### 3.3. `$` đứng một mình không phải dấu đô la literal

Những cách dùng sau là replacement không hợp lệ khi regex có phần khớp:

~~~java
"a1".replaceAll("\\d", "$");          // Thiếu chỉ số hoặc tên nhóm.
"a1".replaceAll("\\d", "${missing}"); // Không có nhóm tên missing.
~~~

Nếu muốn chèn chính ký tự `$`, hãy escape ở **replacement** hoặc dùng `Matcher.quoteReplacement`.

## 4. Gạch chéo ngược `\` trong replacement

### 4.1. Chèn một ký tự `$` theo nghĩa đen

Replacement runtime phải chứa `\$`, tức là dấu `\` rồi tới `$`. Để tạo chuỗi đó trong **Java source**, dùng `"\\$"`:

~~~java
System.out.println("a1b2".replaceAll("\\d", "\\$"));
~~~

Kết quả: `a$b$`.

Viết `"$"` sẽ bị hiểu là tham chiếu nhóm không hoàn chỉnh. Viết trực tiếp `"\$"` trong **Java source** cũng không hợp lệ vì Java không có escape `\$`. Cần hai dấu `\` trong string literal.

### 4.2. Chèn một ký tự gạch chéo ngược thật

Replacement runtime cần hai dấu `\` liền nhau, tức `\\`, để giải mã thành **một** dấu `\` trong output. Trong Java source phải viết tổng cộng **bốn dấu** `"\\\\"`:

~~~java
System.out.println("a1b2".replaceAll("\\d", "\\\\"));
~~~

Output gồm các ký tự `a`, `\`, `b`, `\`:

~~~text
a\b\
~~~

`System.out.println` in dấu gạch chéo ngược thật; nó không in thêm dấu để trình bày escape như cách một số debugger thể hiện Java string.

### 4.3. Khi `\` gây lỗi hoặc làm mất dữ liệu

Một replacement runtime kết thúc bằng **một** ký tự `\` chưa escape ký tự nào phía sau nên không hợp lệ khi được dùng để thay thế. Ký hiệu regex `\d` không còn có nghĩa “chữ số” trong replacement; tại đây, `\d` khiến parser chèn ký tự `d`. Tương tự, nếu bạn truyền đường dẫn Windows làm replacement mà không quote, dấu `\` trong đường dẫn có thể bị bỏ khi được hiểu là escape.

Vì thế, nếu chuỗi thay thế là **dữ liệu thông thường**, nên dùng `Matcher.quoteReplacement(...)` thay vì cố đoán cách thêm dấu gạch chéo ngược theo vị trí.

## 5. Chương trình Java 17+ đầy đủ và kết quả chạy

Lưu mã thành `RegexReplacementDemo.java`:

~~~java
import java.util.regex.Matcher;

public final class RegexReplacementDemo {
    public static void main(String[] args) {
        String code = "Mã AB-123";
        System.out.println(
                code.replaceAll("([A-Z]{2})-(\\d+)", "$2/$1"));

        String value = "a1b2";
        System.out.println(value.replaceAll("\\d", "[$0]"));
        System.out.println(value.replaceAll("\\d", "\\$"));
        System.out.println(value.replaceAll("\\d", "\\\\"));

        // Văn bản cần chèn có cả '$' và dấu '\'.
        String literal = "$5\\tmp";
        System.out.println(value.replaceAll(
                "\\d", Matcher.quoteReplacement(literal)));

        System.out.println("x$y".replace("$", "USD"));
        System.out.println("x$y".replaceAll("\\$", "USD"));
        System.out.println("x$y".replaceAll("$", "USD"));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 RegexReplacementDemo.java
java RegexReplacementDemo
~~~

`stdout`:

~~~text
Mã 123/AB
a[1]b[2]
a$b$
a\b\
a$5\tmpb$5\tmp
xUSDy
xUSDy
x$yUSD
~~~

Giải thích:

1. `$2/$1` lắp ghép nhóm bắt số `2` và `1` theo thứ tự mới.
2. `$0` là toàn bộ phần khớp.
3. `"\\$"` chèn dấu `$`; `"\\\\"` chèn dấu `\`.
4. `Matcher.quoteReplacement(literal)` giữ nguyên `$5\tmp`. Dòng output có hai ký tự `\` và `t`, **không phải tab**.
5. `String.replace("$", "USD")` thay **ký tự đô la literal**, vì `replace` không hiểu regex.
6. `replaceAll("\\$", "USD")` cũng thay dấu đô la literal, nhưng bằng **regex `\$`**.
7. `replaceAll("$", "USD")` có `$` trong **pattern**. Ở vị trí này `$` là anchor cuối input, nên nó **thêm** `USD` vào sau `x$y`.

## 6. Dữ liệu thay thế động nên được chèn nguyên văn

Với chuỗi do người dùng hoặc dịch vụ khác gửi đến, dùng **Matcher.quoteReplacement(value)** để bảo toàn **$** và dấu gạch chéo ngược. Phương thức này chỉ xử lý **replacement**; nếu chuỗi tìm kiếm cũng là dữ liệu literal, dùng **Pattern.quote(needle)** ở phía regex, hoặc **String.replace** khi không cần regex.

Đừng quote cả template **$2/$1** nếu chủ đích là lấy giá trị nhóm. Bài tiếp theo về quoteReplacement sẽ đi sâu vào dữ liệu động, appendReplacement và những tình huống chỉ quote một phía.

## 7. Lỗi phổ biến cần phân biệt

### Lỗi 1: nhầm `$` trong pattern và replacement

~~~java
System.out.println("a$b".replaceAll("$", "X"));    // a$bX
System.out.println("a$b".replaceAll("\\$", "X"));  // aXb
~~~

`$` trong **pattern** đánh dấu cuối input (hoặc cuối dòng tùy chế độ regex); `$` trong **replacement** mở đầu tham chiếu nhóm.

### Lỗi 2: nghĩ `$1` luôn là văn bản literal

~~~java
String text = "x12y";
System.out.println(text.replaceAll("(\\d+)", "[$1]")); // x[12]y
System.out.println(text.replaceAll(
        "(\\d+)", Matcher.quoteReplacement("[$1]")));  // x[$1]y
~~~

Dòng đầu chèn **nhóm bắt**; dòng sau chèn nguyên xi ký tự `[`, `$`, `1`, `]`.

### Lỗi 3: đếm nhầm số dấu `\`

~~~java
String replacement = "\\\\";
System.out.println(replacement.length());                // 2
System.out.println("x".replaceAll("x", replacement).length()); // 1
~~~

Bốn dấu trong **Java source**, hai dấu trong **runtime replacement**, một dấu trong **kết quả**.

### Lỗi 4: dùng regex khi chỉ muốn thay chuỗi

~~~java
String path = "C:\\temp\\data.txt";
System.out.println(path.replace("\\", "/"));
// C:/temp/data.txt
~~~

`String.replace(CharSequence, CharSequence)` thay mọi lần xuất hiện theo nghĩa đen, không diễn giải `$` và `\` theo ngữ pháp replacement của regex. Đây thường là lựa chọn đơn giản hơn khi không cần pattern hay nhóm bắt.

### Lỗi 5: quote sai tham số

~~~java
String text = "a.b";
System.out.println(text.replaceAll(".", "X")); // XXX

String correct = text.replaceAll(
        java.util.regex.Pattern.quote("."),
        java.util.regex.Matcher.quoteReplacement("$")
);
System.out.println(correct); // a$b
~~~

`Pattern.quote(".")` khiến pattern khớp dấu chấm thật thay vì regex “bất kỳ ký tự nào”. `Matcher.quoteReplacement("$")` đảm bảo ký tự đô la được chèn literal.

## 8. Lưu ý nâng cao

- Tham chiếu nhóm số có thể được đọc theo số nhóm **hợp lệ** của pattern: `$10` có thể là nhóm số 10 nếu tồn tại, hoặc nhóm 1 rồi đến ký tự `0` nếu chỉ nhóm 1 hợp lệ. Khi cần ghép chữ số vào sau giá trị nhóm, named group `${name}` làm ý định rõ hơn.
- Các API `Matcher.replaceAll`, `Matcher.replaceFirst`, `Matcher.appendReplacement` cũng dùng ngữ pháp replacement có ý nghĩa đối với `$` và `\`. `String.replaceFirst` chỉ thay lần khớp đầu tiên nhưng có quy tắc cú pháp replacement tương tự.
- Chuỗi replacement sai cú pháp có thể chỉ phát sinh lỗi khi pattern **có phần khớp**. Trường hợp không tìm thấy match không chứng minh replacement hợp lệ.
- Khi sử dụng overload nhận callback để sinh replacement từ từng `MatchResult`, đọc hợp đồng API về cách chuỗi trả về được diễn giải; không giả định rằng nội dung từ callback mặc định là literal.
- Không cần regex để “thay tất cả” chuỗi con cố định: `String.replace` cũng thay **mọi lần khớp literal**.

## 9. Tự kiểm tra

Hãy giải thích vì sao **dấu đô la trong pattern** có thể đánh dấu cuối chuỗi, nhưng **dấu đô la đứng một mình trong replacement** là cú pháp sai khi có match. Sau đó dự đoán kết quả nếu regex tìm chữ số và replacement được viết để xuất đúng một ký tự đô la.

Để debug, kiểm tra ba bước theo thứ tự: Java tạo chuỗi runtime nào, Pattern tìm những vị trí nào, và Matcher diễn giải replacement ra sao. Các ví dụ và kết quả tại mục 5 cho phép đối chiếu từng bước.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-004">Matcher.quoteReplacement cho nội dung thay thế nguyên văn</a>

<details>
<summary>Xem chi tiết</summary>

# Matcher.quoteReplacement: giữ nguyên văn nội dung thay thế trong Java

## Mục tiêu bài học

Khi dùng biểu thức chính quy để thay thế nội dung trong `String`, Java diễn giải **mẫu cần tìm** (*regex*) và **chuỗi dùng để thay thế** (*replacement*) theo hai bộ quy tắc khác nhau. Bài này giúp bạn:

- Giải thích tại sao dấu `$` và dấu gạch chéo ngược `\` trong chuỗi thay thế có thể bị Java hiểu là cú pháp đặc biệt.
- Dùng `Matcher.quoteReplacement(String s)` để đưa nội dung thay thế vào `String.replaceAll`, `String.replaceFirst` hoặc `Matcher.appendReplacement` **đúng nguyên văn**.
- Phân biệt `Matcher.quoteReplacement` với `Pattern.quote`, với `String.replace` và với cú pháp tham chiếu nhóm như `$1` hoặc `${name}`.
- Viết ví dụ Java 17+ có thể biên dịch, đồng thời nhận diện lỗi và giới hạn áp dụng.

## 1. Vấn đề: replacement không phải văn bản thuần túy

Xét lời gọi:

~~~java
String result = input.replaceAll(regex, replacement);
~~~

Đối số `regex` là mẫu để **tìm** các đoạn phù hợp trong `input`. Sau khi tìm được từng đoạn khớp, Java còn diễn giải đối số `replacement`:

| Ký hiệu trong replacement | Ý nghĩa khi dùng trực tiếp |
| --- | --- |
| `$0` | Toàn bộ đoạn vừa khớp |
| `$1`, `$2`, ... | Nội dung nhóm bắt (*capturing group*) tương ứng |
| `${name}` | Nội dung nhóm bắt có tên `name`, nếu mẫu khai báo nhóm đó |
| `\` | Escape ký tự kế tiếp trong cú pháp replacement, ví dụ làm `$` trở thành ký tự thông thường |

Do vậy, nếu muốn thay một đoạn bằng đúng văn bản `$1\docs`, truyền thẳng nội dung đó vào `replaceAll` sẽ không có nghĩa là “chép nguyên văn”: `$1` bị xem là tham chiếu nhóm bắt, còn `\` có thể làm mất dấu gạch chéo hoặc gây lỗi tùy vị trí. Khi nhóm `1` không tồn tại, `$1` có thể khiến thao tác thay thế ném ngoại lệ.

**`Matcher.quoteReplacement(s)` giải quyết đúng vấn đề này.** Phương thức tĩnh thuộc `java.util.regex.Matcher` trả về một chuỗi *replacement* tương ứng với **nội dung nguyên văn của `s`**. Khi dùng chuỗi trả về làm đối số thay thế của API regex, mọi `$` và `\` xuất phát từ `s` đều được coi là ký tự bình thường. Nó không đổi nội dung cần chèn và không điều khiển regex tìm kiếm.

Chữ ký API:

~~~java
public static String quoteReplacement(String s)
~~~

Đây là phương thức `static`, nên gọi `Matcher.quoteReplacement(s)` mà không cần tạo một đối tượng `Matcher`. Giá trị `null` không phải chuỗi thay thế hợp lệ; truyền `null` sẽ gây `NullPointerException`.

## 2. Ví dụ Java 17+: thay thế bằng nội dung có dấu $ và \

Lưu toàn bộ chương trình sau vào tệp `QuoteReplacementDemo.java`:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuoteReplacementDemo {
    public static void main(String[] args) {
        String source = "A1 B2";
        String literalReplacement = "$5\\bin";

        // Thay mọi đoạn phù hợp bằng đúng nội dung "$5\bin".
        String all = source.replaceAll(
                "[A-Z]\\d",
                Matcher.quoteReplacement(literalReplacement));
        System.out.println(all);

        // Thay duy nhất đoạn khớp đầu tiên.
        String first = source.replaceFirst(
                "[A-Z]\\d",
                Matcher.quoteReplacement(literalReplacement));
        System.out.println(first);

        // Khi muốn dùng nhóm bắt, KHÔNG quote cả template.
        System.out.println(
                source.replaceAll("([A-Z])(\\d)", "$2-$1"));

        // Khi muốn chính các ký tự "$2-$1" xuất hiện, HÃY quote.
        System.out.println(
                source.replaceAll("([A-Z])(\\d)",
                        Matcher.quoteReplacement("$2-$1")));

        // Bảo vệ cả chuỗi tìm kiếm nguyên văn và chuỗi thay thế nguyên văn.
        String text = "a.b|a.b";
        String needle = "a.b";
        String replacement = "$1\\dst";
        System.out.println(text.replaceAll(
                Pattern.quote(needle),
                Matcher.quoteReplacement(replacement)));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 QuoteReplacementDemo.java
java QuoteReplacementDemo
~~~

Kết quả chuẩn:

~~~text
$5\bin $5\bin
$5\bin B2
1-A 2-B
$2-$1 $2-$1
$1\dst|$1\dst
~~~

Diễn giải:

1. `"[A-Z]\\d"` khớp lần lượt `A1` và `B2`; `quoteReplacement` làm cho `$5\bin` được chèn y hệt, dù không có nhóm số 5.
2. `replaceFirst` có cách diễn giải replacement giống `replaceAll` nhưng chỉ thay lần khớp đầu tiên, nên `B2` vẫn còn.
3. Trong `"([A-Z])(\\d)"`, nhóm `1` là chữ cái và nhóm `2` là chữ số. Replacement `"$2-$1"` cố ý lấy nhóm và đổi `A1` thành `1-A`.
4. Sau khi bọc `"$2-$1"` bằng `quoteReplacement`, chuỗi này được chèn đúng nguyên văn; nhóm bắt không còn được thay vào vị trí `$1` hoặc `$2`.
5. `Pattern.quote("a.b")` làm dấu `.` trong **mẫu tìm kiếm** mất ý nghĩa đặc biệt; `Matcher.quoteReplacement("$1\\dst")` làm `$` và `\` trong **replacement** mất ý nghĩa đặc biệt.

**Hai tầng escape cần nhớ:** Mã Java `"$5\\bin"` tạo ra giá trị thực `$5\bin` vì hai ký tự `\\` trong source tạo một dấu `\` trong chuỗi runtime. `Matcher.quoteReplacement` sau đó xây dựng biểu diễn phù hợp cho bộ xử lý replacement, giúp dấu `\` runtime được giữ lại ở kết quả.

## 3. Phân biệt pattern, replacement và cách gọi API

Đừng nhầm vị trí của dữ liệu:

| Nhu cầu | Cách làm | Lý do |
| --- | --- | --- |
| Mẫu tìm là regex, nội dung thay là chuỗi thuần | `input.replaceAll(regex, Matcher.quoteReplacement(literal))` | Regex vẫn hoạt động; replacement được bảo toàn |
| Mẫu tìm là đoạn văn bản thuần, nội dung thay là chuỗi thuần | `input.replace(literalTarget, literalReplacement)` | Không cần regex hoặc quote |
| Bắt buộc dùng API regex nhưng cần tìm chuỗi nguyên văn | `input.replaceAll(Pattern.quote(target), Matcher.quoteReplacement(replacement))` | Quote **cả hai đối số**, mỗi cái theo ngữ pháp của nó |
| Cần chèn giá trị các nhóm bắt | `input.replaceAll(regex, "$2-$1")` | Cho phép bộ máy replacement mở rộng nhóm bắt |
| Cần dùng quy trình thay thế từng match | `matcher.appendReplacement(builder, Matcher.quoteReplacement(value))` rồi `matcher.appendTail(builder)` | Mỗi match có thể có replacement riêng, bảo toàn giá trị tạo ra |

**`Pattern.quote` và `Matcher.quoteReplacement` không hoán đổi được:**

- `Pattern.quote(literalTarget)` tạo biểu thức chính quy khớp nguyên văn `literalTarget`. Nó giải quyết dấu regex đặc biệt, chẳng hạn `.`, `*`, `[`, `(`.
- `Matcher.quoteReplacement(literalReplacement)` tạo cú pháp replacement đại diện cho nội dung nguyên văn `literalReplacement`. Nó giải quyết `$` và `\` trong **văn bản thay thế**.
- `String.replace(target, replacement)` là thay thế chuỗi con nguyên văn, không dùng regex cho cả `target` lẫn `replacement`; đó thường là lựa chọn ngắn nhất khi không cần regex.

Nếu **chỉ có** nội dung replacement cần bảo toàn, không bọc `regex` trong `Pattern.quote`: làm vậy sẽ biến các ký hiệu regex mong muốn thành văn bản literal và thay đổi logic tìm kiếm.

## 4. So sánh mở rộng nhóm bắt với chèn đúng nguyên văn

Giả sử một mẫu có hai nhóm:

~~~java
String source = "id=42";
String regex = "([a-z]+)=(\\d+)";
~~~

Ba cách thay thế dưới đây có ý nghĩa khác nhau:

| Lệnh | Kết quả | Giải thích |
| --- | --- | --- |
| `source.replaceAll(regex, "$2:$1")` | `42:id` | `$2` và `$1` được mở rộng từ nhóm bắt |
| `source.replaceAll(regex, Matcher.quoteReplacement("$2:$1"))` | `$2:$1` | Nội dung có `$` được chèn đúng nguyên văn |
| `source.replaceAll(regex, Matcher.quoteReplacement("USD $5\\tmp"))` | `USD $5\tmp` | Giữ cả dấu `$` lẫn dấu `\` |

Vì thế **không bọc cả template nhóm bắt trong `quoteReplacement`** nếu chủ đích là dùng `$1`, `$2` hoặc `${name}` để tạo kết quả từ dữ liệu tìm được. Ngược lại, nếu một giá trị replacement đến từ ô nhập liệu, cấu hình hoặc nội dung sinh động mà người dùng muốn giữ đúng ký tự, `quoteReplacement` là cách bảo vệ hợp lý.

Có thể trộn dữ liệu nhóm bắt và văn bản literal bằng cách **tạo giá trị kết quả cuối cùng cho từng match**, rồi `quoteReplacement` giá trị cuối cùng đó trước khi đưa vào `appendReplacement`; ví dụ ở phần tiếp theo. Cách này còn tránh các mơ hồ như nối `"$1" + "2"` thành `"$12"`: bộ xử lý có thể diễn giải `$12` là nhóm 12 nếu nhóm đó tồn tại.

## 5. Thay thế động theo từng match với Matcher.appendReplacement

`String.replaceAll` nhận một replacement chung cho mọi match. Khi replacement cần dựa trên chính đoạn vừa khớp hoặc logic tùy biến, sử dụng `Matcher.find()` với `appendReplacement` và `appendTail`.

Ví dụ: với mỗi mã gồm hai chữ in hoa và hai chữ số, bao quanh nó bằng `[` và `]`, đồng thời thêm hậu tố literal có dấu `$` và `\`.

Lưu vào `DynamicReplacementDemo.java`:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicReplacementDemo {
    public static void main(String[] args) {
        Pattern pattern = Pattern.compile("[A-Z]{2}[0-9]{2}");
        Matcher matcher = pattern.matcher("AB12 and CD34");
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String text = "[" + matcher.group() + " $5\\log]";
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(text));
        }
        matcher.appendTail(result);

        System.out.println(result);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 DynamicReplacementDemo.java
java DynamicReplacementDemo
~~~

Kết quả:

~~~text
[AB12 $5\log] and [CD34 $5\log]
~~~

Trình tự hoạt động:

1. `find()` tìm `AB12`, sau đó `CD34`.
2. `group()` trả về phần vừa khớp và giúp chương trình xây dựng replacement riêng cho mỗi lần.
3. `quoteReplacement(text)` bảo đảm các ký tự `$` và `\` trong giá trị cuối cùng là dữ liệu literal.
4. `appendReplacement` nối phần văn bản chưa được xử lý và replacement cho match hiện tại vào `StringBuilder`.
5. `appendTail` nối nốt văn bản còn lại sau match cuối cùng. Nếu quên bước này, phần đuôi có thể bị mất; nếu không có match nào, bước này vẫn giữ lại toàn bộ văn bản gốc.

`appendReplacement(StringBuilder, String)` có mặt từ Java 9 nên dùng được trong Java 17+. Với mẫu dùng lặp lại nhiều lần, `Pattern.compile` một lần rồi tạo `Matcher` cho từng input giúp tránh biên dịch regex lặp lại không cần thiết. Đừng chia sẻ cùng một `Matcher` giữa nhiều luồng mà không đồng bộ vì đối tượng này có trạng thái thay đổi theo mỗi lần tìm khớp.

## 6. Những lỗi phổ biến

### 6.1. Chèn replacement từ người dùng mà không quote

Ví dụ sai khi `userReplacement` là `"$10"` hoặc `"C:\\new"`:

~~~java
// Sai nếu muốn thay bằng đúng chuỗi mà người dùng nhập:
String result = input.replaceAll(regex, userReplacement);
~~~

`$10` có thể được diễn giải là tham chiếu nhóm (quy tắc số nhóm phụ thuộc các nhóm hiện có); `\` có thể bị tiêu thụ để escape ký tự tiếp theo. Nếu dấu `$` đứng ở vị trí không hợp lệ hoặc tham chiếu nhóm không tồn tại, chương trình có thể ném `IllegalArgumentException` hay `IndexOutOfBoundsException` tùy trường hợp.

Cách đúng để chèn nguyên văn:

~~~java
String result = input.replaceAll(
        regex, Matcher.quoteReplacement(userReplacement));
~~~

### 6.2. Nhầm escape của Java với escape của replacement

Hai tầng có vai trò khác nhau:

- **Tầng chuỗi Java:** `"\\":` biểu diễn **một** dấu `\` trong giá trị runtime. Trong ví dụ code, `"$1\\docs"` là giá trị có ký tự `$`, `1`, `\`, `d`, `o`, `c`, `s`.
- **Tầng replacement của regex:** Bộ xử lý `replaceAll` gán ý nghĩa cho `$` và `\` trong giá trị runtime. `quoteReplacement` giúp chúng trở thành literal mà không cần tự viết các dấu escape thủ công.

Lưu ý: khi đọc regex từ biến hoặc tệp, escape của **source Java** đã được xử lý (hoặc không liên quan); không nên thêm ngẫu nhiên dấu `\` trước khi gọi `quoteReplacement`.

### 6.3. Dùng quoteReplacement để sửa một regex sai

`Matcher.quoteReplacement` chỉ bảo vệ chuỗi dùng để **thay thế**. Nó không sửa regex thiếu dấu `]`, không biến `.*` thành mẫu tìm literal, và không xử lý `PatternSyntaxException` phát sinh ở đối số tìm kiếm. Nếu muốn tìm chính xác văn bản đầu vào, dùng `Pattern.quote` cho đối số regex hoặc `String.replace` nếu không cần regex.

### 6.4. Quote quá nhiều khi chủ đích sử dụng nhóm bắt

~~~java
// Muốn đảo hai nhóm: đúng
String ok = "A1".replaceAll("([A-Z])(\\d)", "$2$1");

// Muốn "$2$1" xuất hiện nguyên văn: đúng
String literal = "A1".replaceAll(
        "([A-Z])(\\d)", Matcher.quoteReplacement("$2$1"));
~~~

Giá trị `ok` là `"1A"`, còn `literal` là `"$2$1"`. Khác biệt đến từ **ý định sử dụng replacement**, không phải do cú pháp nào “tốt hơn” trong mọi tình huống.

### 6.5. Quên xử lý null, đầu vào lớn hoặc độ phức tạp regex

- `Matcher.quoteReplacement(null)` gây `NullPointerException`; kiểm tra null hoặc xác định chính sách dữ liệu thiếu trước khi dùng.
- Nếu regex không khớp đoạn nào, `replaceAll` trả lại toàn bộ nội dung gốc; `quoteReplacement` không ép phải có match.
- `quoteReplacement` chỉ giải quyết ký tự đặc biệt trong replacement. Nó không bảo vệ khỏi regex quá tốn thời gian (chẳng hạn một số biểu thức lặp lồng nhau gây *catastrophic backtracking*), không xác thực định dạng và không tự giới hạn kích thước đầu vào.

## 7. Bài tập thực hành

Dự đoán kết quả:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplacementQuiz {
    public static void main(String[] args) {
        System.out.println("aa".replaceAll("a", Matcher.quoteReplacement("$0")));
        System.out.println("a1".replaceAll("([a-z])(\\d)", "$2$1"));
        System.out.println("a1".replaceAll("([a-z])(\\d)",
                Matcher.quoteReplacement("$2$1")));
        System.out.println("x.y".replaceAll(
                Pattern.quote("."), Matcher.quoteReplacement("$")));
        System.out.println("aaa".replaceFirst("a",
                Matcher.quoteReplacement("\\dir")));
    }
}
~~~

Lưu `ReplacementQuiz.java`, chạy `javac -encoding UTF-8 ReplacementQuiz.java` rồi `java ReplacementQuiz`. Đáp án:

~~~text
$0$0
1a
$2$1
x$y
\diraa
~~~

Lý do: hai `a` được thay bằng chuỗi literal `$0`; `$2$1` không quote thì đảo nhóm; quote thì giữ nguyên ký tự; `Pattern.quote(".")` tìm đúng dấu chấm; và `replaceFirst` thay chỉ chữ `a` đầu tiên bằng `\dir`.

## Ghi nhớ và checklist áp dụng

- Hỏi rõ: **giá trị replacement cần giữ nguyên văn hay cần mở rộng các nhóm bắt?** Chỉ dùng `Matcher.quoteReplacement` cho phần cần giữ nguyên văn.
- Với regex để tìm và nội dung tự do để thay: `replaceAll(regex, Matcher.quoteReplacement(text))`.
- Với cả chuỗi tìm và chuỗi thay là literal: ưu tiên `String.replace(target, replacement)`; nếu cần matcher thì phối hợp `Pattern.quote(target)` và `Matcher.quoteReplacement(replacement)`.
- Với nội dung thay khác nhau theo mỗi match: tính giá trị cần chèn dựa trên `group()` rồi đưa qua `quoteReplacement` trước `appendReplacement`, cuối cùng gọi `appendTail`.
- Kiểm tra các tình huống có `$`, `\`, không có match, nhiều match, chuỗi rỗng, nhóm bắt và giá trị `null`.

## Tài liệu tham khảo

- [Java SE 17 — Matcher.quoteReplacement(String)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html#quoteReplacement(java.lang.String))
- [Java SE 17 — Matcher.appendReplacement và appendTail](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html)
- [Java SE 17 — Pattern.quote(String)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html#quote(java.lang.String))
- [Java SE 17 — String.replaceAll, replaceFirst, replace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-005">Phân biệt escape của mẫu tìm với escape của phần thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt escape của mẫu tìm và escape của phần thay thế trong Java

## 1. Tại sao có hai kiểu escape khác nhau?

Khi thay thế bằng regex trong Java, ví dụ `text.replaceAll(regex, replacement)`, hai tham số đi qua **hai bộ quy tắc riêng**:

1. **Mẫu tìm (regex/pattern)** được giải thích bởi `java.util.regex.Pattern`. Các ký tự như `.`, `*`, `+`, `?`, `[`, `(`, `|`, `$` và dấu `\` có thể mang ý nghĩa đặc biệt đối với việc **tìm vị trí khớp**.
2. **Chuỗi thay thế (replacement)** được giải thích bởi `java.util.regex.Matcher`. Ở đây, `$1`, `$2` hoặc `${name}` có nghĩa là **chèn nội dung nhóm bắt**, còn dấu `\` dùng để escape ký tự tiếp theo trong *replacement*. Các ký tự regex như `.` hay `*` trong replacement thông thường chỉ là văn bản.
3. **Nếu viết trong mã nguồn Java**, cả regex lẫn replacement còn phải đi qua cú pháp *Java string literal*: viết `\\` trong mã nguồn để tạo **một** dấu `\` ở chuỗi lúc chạy. Đây là lớp escape thứ ba, dễ bị nhầm với hai lớp ở trên.

Hình dung luồng xử lý:

```text
Mã nguồn Java
     |
     |  Java string literal (\\ trong source -> \ lúc chạy)
     v
regex -------------------> Pattern: quy tắc tìm kiếm
replacement -------------> Matcher: quy tắc chèn/đổi nhóm
                                        |
                                        v
                                   Chuỗi kết quả
```

Ví dụ, `"\\."` dùng ở **regex** có nghĩa là tìm **dấu chấm thật**; `"\\$"` dùng ở **replacement** có nghĩa là ghi **ký tự đô la thật**. Hai cú pháp giống hình dạng (dấu `\` trong chuỗi Java) nhưng thuộc **hai bộ giải thích khác nhau**.

> Quy tắc cần nhớ: **`Pattern.quote(...)` bảo vệ mẫu tìm**; **`Matcher.quoteReplacement(...)` bảo vệ nội dung thay thế**. Dùng đúng hàm tại đúng đầu vào.

## 2. Escape của mẫu tìm: regex diễn giải các ký tự đặc biệt

### 2.1. Mẫu tìm là regex, không mặc định là văn bản thuần

Xét hai câu lệnh:

```java
System.out.println("a.b".replaceAll(".", "-"));    // ---
System.out.println("a.b".replaceAll("\\.", "-")); // a-b
```

- Regex `"."` khớp **bất kỳ một ký tự** thông thường (trừ một số ký tự xuống dòng tùy chế độ), nên cả `a`, `.` và `b` đều được thay.
- Regex ở runtime `\.` khớp **dấu chấm thật**; trong Java source phải viết `"\\."`, nên chỉ một dấu chấm được thay.

Bảng sau đọc từ **mã nguồn Java** sang ý nghĩa tìm kiếm:

| Muốn tìm | Java source cho regex | Regex ở runtime | Ý nghĩa |
| --- | --- | --- | --- |
| Dấu chấm `.` | `"\\."` | `\.` | Dấu chấm thông thường, không phải wildcard |
| Một hoặc nhiều chữ số | `"\\d+"` | `\d+` | Regex cho dãy chữ số |
| Ký tự `$` | `"\\$"` | `\$` | Dấu đô la thật, không phải mốc cuối |
| Một dấu gạch chéo ngược `\` | `"\\\\"` | `\\` | Regex escape dấu `\` |
| Từ "a" theo sau bởi chữ số | `"a\\d"` | `a\d` | Regex mô tả cấu trúc, không phải chuỗi cố định |

Viết `"\d+"` hoặc `"\."` trực tiếp trong Java source sẽ gây lỗi biên dịch *illegal escape character*: `\d` và `\.` không phải escape hợp lệ của Java string literal. Điều này xảy ra **trước khi** regex engine nhận đầu vào.

### 2.2. Khi mẫu tìm đến từ dữ liệu người dùng

Nếu mục tiêu là tìm **đúng một chuỗi ký tự** do người dùng nhập, dùng `Pattern.quote(...)`:

```java
String find = "$5.00";
String source = "Giá là $5.00";
String result = source.replaceAll(Pattern.quote(find), "10 USD");
System.out.println(result); // Giá là 10 USD
```

Nếu dùng trực tiếp `"$5.00"` làm regex, ký tự `$` sẽ được hiểu là mốc kết thúc input, còn dấu `.` là ký tự bất kỳ; kết quả không phù hợp mục tiêu tìm văn bản `$5.00`. `Pattern.quote(find)` khiến toàn bộ nội dung `find` được xử lý như **văn bản literal**, kể cả khi chứa `\`, `$`, dấu ngoặc hoặc dấu `.`.

Cách khác khi làm việc trực tiếp với `Pattern`:

```java
Pattern literal = Pattern.compile("$5.00", Pattern.LITERAL);
System.out.println(literal.matcher("Giá là $5.00").find()); // true
```

`Pattern.LITERAL` thay đổi **cách diễn giải pattern**, không thay đổi cú pháp replacement của `Matcher`. Nếu thật sự muốn tìm cấu trúc bằng regex (ví dụ `"\\d+"`), **không** quote toàn bộ regex, vì khi đó nó sẽ tìm ký tự `\`, `d`, `+` theo nghĩa đen.

## 3. Escape của phần thay thế: cú pháp riêng của Matcher

Với `String.replaceAll(regex, replacement)`, `String.replaceFirst(regex, replacement)` và `Matcher.replaceAll(replacement)`, replacement không được hiểu như regex; thay vào đó nó có cú pháp riêng:

- `$1`, `$2`, ... tham chiếu tới nhóm bắt tương ứng. `$0` là **toàn bộ lần khớp**.
- `${name}` tham chiếu đến **nhóm bắt có tên**, được định nghĩa bằng `(?<name>...)` trong pattern.
- `\` trong replacement escape ký tự tiếp theo; cần escape chính dấu `$` hoặc dấu `\` khi muốn chèn chúng theo nghĩa đen.
- Một ký tự `$` không có tham chiếu nhóm hợp lệ có thể làm replacement lỗi. Tham chiếu tới nhóm không tồn tại cũng gây lỗi.

### 3.1. Ví dụ thay thế bằng nhóm bắt

```java
Pattern p = Pattern.compile("(\\w+)-(\\d+)");
System.out.println(p.matcher("item-42").replaceAll("$2:$1"));
// 42:item
```

Regex có hai nhóm: `(\\w+)` bắt `item`; `(\\d+)` bắt `42`. Replacement `"$2:$1"` chèn nhóm hai, dấu hai chấm, rồi nhóm một. Ký tự `:` trong replacement là dấu hai chấm thông thường.

Với nhóm có tên:

```java
String result = "item-42".replaceAll(
        "(?<name>[A-Za-z]+)-(?<qty>\\d+)",
        "${qty}x${name}");
System.out.println(result); // 42xitem
```

Lưu ý: `${qty}` là cú pháp **replacement**; `(?<qty>...)` là cú pháp **regex pattern**. Không được hoán đổi hai dạng này.

### 3.2. Muốn xuất đúng ký tự đô la `$` hoặc backslash `\`

Ví dụ thay dấu chấm bằng dấu đô la thật:

```java
System.out.println("a.b".replaceAll("\\.", "\\$")); // a$b
```

Ở đây:
- Java source `"\\."` tạo regex `\.`, tìm dấu chấm.
- Java source `"\\$"` tạo replacement gồm **backslash + dollar** (`\$` ở runtime). Matcher bỏ dấu escape và chèn `$` thật vào kết quả.

Tương tự, muốn thay dấu chấm bằng **một dấu backslash thật**:

```java
System.out.println("a.b".replaceAll("\\.", "\\\\")); // a\b
```

Java source `"\\\\"` tạo **hai dấu backslash** lúc chạy; Matcher xử lý chúng để xuất **một dấu backslash**. Nếu replacement ở runtime chỉ có một dấu `\` đứng cuối, nó không escape được ký tự tiếp theo và sẽ gây lỗi.

| Muốn đưa vào kết quả | Replacement Java source | Điều Matcher nhận tại runtime | Ý nghĩa |
| --- | --- | --- | --- |
| Giá trị nhóm 1 | `"$1"` | `$1` | Thay bằng nội dung group 1 |
| Dấu `$` thật | `"\\$"` | `\$` | Escape `$` trong replacement |
| Dấu `\` thật | `"\\\\"` | `\\` | Escape `\` trong replacement |
| Văn bản `$1` thật | `Matcher.quoteReplacement("$1")` | `\$1` | Bảo vệ cả `$` để không đọc thành group |
| Văn bản `$HOME\path` | `Matcher.quoteReplacement("$HOME\\path")` | Các dấu `$` và `\` được escape | Chèn nguyên văn đúng dữ liệu |

Dùng `Matcher.quoteReplacement(replacement)` là lựa chọn ít lỗi hơn khi chuỗi thay thế là văn bản tự do, có thể chứa nhiều dấu `$` và `\`:

```java
String literal = "$1\\";
System.out.println("a.b".replaceAll(
        "\\.", Matcher.quoteReplacement(literal)));
// a$1\b
```

Nếu truyền `"$1\\"` trực tiếp dưới dạng replacement mà chưa quote, Matcher sẽ cố diễn giải `$1` là tham chiếu nhóm, hoặc diễn giải `\` là escape. Việc quote giúp coi toàn bộ chuỗi thay thế là văn bản.

**Khác biệt quan trọng:** Khi muốn `$1` thực sự chèn **nhóm 1**, đừng gọi `Matcher.quoteReplacement("$1")`, vì lúc đó chuỗi `$1` chỉ được chèn theo nghĩa đen.

## 4. Ví dụ Java 17+ hoàn chỉnh

Tạo file **`RegexEscapingDemo.java`**:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexEscapingDemo {
    public static void main(String[] args) {
        String source = "file.txt | price $5.00 | item-42";

        // 1. Pattern: tìm dấu chấm thật.
        System.out.println(source.replaceAll("\\.", "#"));

        // 2. Tìm một chuỗi literal và thay bằng một chuỗi literal.
        //    Cả dấu $ và backslash ở replacement đều được giữ nguyên.
        System.out.println(source.replaceAll(
                Pattern.quote("$5.00"),
                Matcher.quoteReplacement("$10\\new")));

        // 3. Replacement dùng nhóm bắt (không quoteReplacement).
        Pattern item = Pattern.compile("(\\w+)-(\\d+)");
        System.out.println(item.matcher(source).replaceAll("$2:$1"));

        // 4. Named capturing groups.
        System.out.println("item-42".replaceAll(
                "(?<name>[A-Za-z]+)-(?<qty>\\d+)",
                "${qty}x${name}"));

        // 5. Xuất $ và \ theo nghĩa đen.
        System.out.println("a.b".replaceAll("\\.", "\\$"));
        System.out.println("a.b".replaceAll("\\.", "\\\\"));

        // 6. Replacement chứa '$1' mà KHÔNG có ý nghĩa group 1.
        System.out.println("a.b".replaceAll(
                "\\.", Matcher.quoteReplacement("$1\\")));

        // 7. String.replace là phép thay thế literal ở cả hai phía.
        System.out.println("a.b".replace(".", "$1\\"));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 RegexEscapingDemo.java
java RegexEscapingDemo
```

Kết quả stdout:

```text
file#txt | price $5#00 | item-42
file.txt | price $10\new | item-42
file.txt | price $5.00 | 42:item
42xitem
a$b
a\b
a$1\b
a$1\b
```

Kết quả trên dùng dấu `\` **thật**; ví dụ `$10\new` không chứa xuống dòng, vì mã Java `"$10\\new"` tạo dấu backslash thật trước chữ `n`. Các biểu thức tương đương đã được kiểm tra qua JShell với JDK có hỗ trợ các API Java 17 này.

## 5. Kết hợp hai phía khi dữ liệu được nhập nguyên văn

Nếu hệ thống cung cấp **find** và **replacement** như hai chuỗi do người dùng nhập, trước hết xác định có cần tính năng regex hay không. Với tác vụ thay chuỗi literal thông thường, **String.replace(find, replacement)** giữ nguyên ký tự đặc biệt ở cả hai phía và là API dễ hiểu nhất.

Nếu vẫn phải dùng bộ máy regex, dùng **Pattern.quote(find)** cho tham số tìm và **Matcher.quoteReplacement(replacement)** cho tham số thay. Chỉ quote phía nào thực sự là dữ liệu literal; quote cả một biểu thức regex đang dùng chủ động sẽ thay đổi điều kiện tìm kiếm. Ví dụ, tìm đúng ký tự **$5.00** và thay bằng **$10\x** đòi hỏi bảo vệ cả hai phía để tránh đọc **$** thành anchor hoặc tham chiếu nhóm.

## 6. Khi vừa dùng nhóm bắt, vừa muốn thêm ký tự đặc biệt

Không thể quote **toàn bộ** replacement nếu trong đó có `$1` cần mở rộng thành nhóm bắt. Hãy xây dựng replacement có chủ đích.

Ví dụ muốn đổi `name=42` thành `[name: $42]`:

```java
String output = "name=42".replaceAll(
        "(\\w+)=(\\d+)",
        "[$1: \\$$2]");
System.out.println(output); // [name: $42]
```

Đọc replacement Java source `"[$1: \\$$2]"` từ trái sang phải:

- `$1`: chèn nhóm 1 `name`.
- `: `: văn bản thông thường.
- `\\$` trong Java source: tạo `\$` ở runtime, để Matcher xuất dấu `$` thật.
- `$2`: chèn nhóm 2 `42`.
- `]`: văn bản thông thường.

Với logic replacement động phức tạp, `Matcher.appendReplacement` kết hợp `appendTail` cho phép tạo giá trị từ nội dung từng lần khớp. Giá trị được tạo ra **vẫn phải quote** nếu muốn chèn nguyên văn:

```java
Matcher matcher = Pattern.compile("(\\w+)=([0-9]+)")
        .matcher("a=1;b=2");
StringBuffer out = new StringBuffer();

while (matcher.find()) {
    String literalPart = "[" + matcher.group(1)
            + " -> $" + matcher.group(2) + "]";
    matcher.appendReplacement(
            out, Matcher.quoteReplacement(literalPart));
}
matcher.appendTail(out);

System.out.println(out); // [a -> $1];[b -> $2]
```

Cơ chế: `appendReplacement` chèn phần chưa khớp trước đó, sau đó chèn replacement cho lần khớp; `appendTail` chép nốt phần cuối còn lại. Vì `literalPart` chứa ký tự `$` với mục đích literal, dùng `quoteReplacement` là bắt buộc để tránh bị thay bằng group. `StringBuffer` là kiểu đối số của overload `appendReplacement(StringBuffer, String)` (Java cũng có overload với `StringBuilder` trong phiên bản hiện đại).

## 7. Các lỗi thường gặp và cách sửa

| Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `replaceAll(".", "-")` thay mọi ký tự | `.` là wildcard regex | Dùng `"\\."` hoặc `Pattern.quote(".")` |
| `replaceAll("$5", "...")` không tìm được dấu `$` thật | `$` trong regex là mốc cuối input | Dùng `Pattern.quote("$5")` |
| `replaceAll("\\.", "$1")` lỗi khi không có nhóm 1 | `$1` trong replacement tham chiếu tới group 1 | Nếu muốn literal `$1`, dùng `Matcher.quoteReplacement("$1")` |
| Replacement chứa dấu `$` do người dùng nhập gây lỗi tham chiếu nhóm | Chưa escape cú pháp replacement | Dùng `Matcher.quoteReplacement(userReplacement)` |
| Dấu `\` biến mất hoặc replacement lỗi | Backslash được Matcher dùng để escape ký tự tiếp theo | Dùng `Matcher.quoteReplacement(...)`; hoặc đặt đủ backslash trong source |
| Dùng `Pattern.quote(...)` cho replacement | Quote nhầm cú pháp regex thay vì replacement | Dùng `Matcher.quoteReplacement(...)` |
| Dùng `Matcher.quoteReplacement(...)` cho mẫu tìm | Quote nhầm phía và có thể tạo regex sai nghĩa | Dùng `Pattern.quote(...)` |
| Muốn chèn group `$1` nhưng toàn bộ replacement bị quote | `quoteReplacement` biến `$` thành literal | Giữ cú pháp `$1` trực tiếp và escape riêng các ký tự literal |
| `"\d+"` hoặc `"\."` không biên dịch | Java string literal không nhận escape `\d` hay `\.` | Viết `"\\d+"` hoặc `"\\."` |

Một số ngoại lệ thường gặp:

- `PatternSyntaxException`: cú pháp regex không hợp lệ, chẳng hạn dấu `[` chưa đóng.
- `IllegalArgumentException`: một số dạng replacement có tham chiếu nhóm sai cú pháp (chẳng hạn `$` đứng một mình).
- `IndexOutOfBoundsException`: tham chiếu đến nhóm đánh số không tồn tại, ví dụ `$1` khi pattern không khai báo nhóm bắt nào.

Đừng dựa vào việc bắt rồi bỏ qua ngoại lệ để xử lý dữ liệu đầu vào; hãy phân biệt ngay từ đầu trường hợp người dùng cung cấp **văn bản literal** hay **regex/replacement template**.

## 8. Bài tập áp dụng

**Bài 1.** Tìm dấu `$` thật trong `"Total: $12"` rồi đổi thành `"USD "`.

```java
System.out.println("Total: $12".replaceAll("\\$", "USD "));
// Total: USD 12
```

Đây là escape của **mẫu tìm**. Dấu `$` ở replacement `"USD "` không có mặt nên không cần quote.

**Bài 2.** Thay dấu chấm trong `"a.b"` bằng đúng văn bản `"$2\path"`.

```java
String replacement = "$2\\path";
System.out.println("a.b".replaceAll(
        "\\.", Matcher.quoteReplacement(replacement)));
// a$2\pathb
```

Ở đây `$2` là văn bản literal, **không** tham chiếu nhóm 2. Dấu `\` cũng phải giữ nguyên.

**Bài 3.** Đổi `"code-7"` thành `"7/code"` thông qua nhóm bắt:

```java
System.out.println("code-7".replaceAll(
        "([A-Za-z]+)-(\\d+)", "$2/$1"));
// 7/code
```

Trường hợp này `$2` và `$1` **phải** mang ý nghĩa nhóm bắt, nên không quote replacement.

**Bài 4.** Vì sao đoạn này không nhất thiết cho đúng kết quả mong muốn?

```java
String find = "a.b";
String replacement = "$1";
String output = "a.b".replaceAll(find, replacement);
```

Đáp án: `find` được xử lý như regex, trong đó dấu chấm khớp bất kỳ ký tự; `replacement` chứa `$1` nhưng pattern không định nghĩa group 1 nên gây lỗi. Nếu mục tiêu là thay một chuỗi literal bằng chính `$1`, viết:

```java
String output = "a.b".replaceAll(
        Pattern.quote(find),
        Matcher.quoteReplacement(replacement));
System.out.println(output); // $1
```

Hoặc chỉ cần `"a.b".replace("a.b", "$1")` nếu không có yêu cầu dùng regex.

## 9. Checklist trước khi thay thế

1. **Mẫu tìm là regex thực sự hay văn bản literal?** Regex thực sự thì viết/kiểm tra escape theo `Pattern`; văn bản literal thì dùng `Pattern.quote` hoặc `String.replace`.
2. **Replacement là mẫu có group `$1`/`${name}` hay văn bản literal?** Mẫu group thì giữ đúng cú pháp tham chiếu; văn bản literal thì dùng `Matcher.quoteReplacement`.
3. **Các dấu `\` trong Java source đã được viết đúng chưa?** Nên kiểm tra riêng chuỗi **sau khi Java phân tích string literal** và cách regex/Matcher phân tích chuỗi đó.
4. **Có group phù hợp với replacement không?** `$1` đòi hỏi group 1; `${name}` đòi hỏi group có tên `name`.
5. **Có dữ liệu động chứa `$`, `\`, `.`, `[`...?** Quote đúng từng phía; nếu không cần regex, chọn `String.replace` để giảm khả năng nhầm lẫn.

**Kết luận:** `Pattern.quote` và `Matcher.quoteReplacement` giải quyết **hai vấn đề độc lập**. Escape regex quyết định **khớp cái gì**; escape replacement quyết định **chèn cái gì**. Trong Java source còn có lớp escape thứ ba cho dấu backslash. Xác định rõ ba lớp này giúp xử lý chính xác ký tự đặc biệt và nhóm bắt khi gọi `replaceAll`, `replaceFirst` hoặc `Matcher`.

**Tham khảo chính thức:** [Java SE 17 — Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html), [Java SE 17 — Matcher.appendReplacement / quoteReplacement / replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html), [Java SE 17 — String.replaceAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#replaceAll(java.lang.String,java.lang.String)).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-006">Matcher.replaceAll với hàm tính chuỗi thay thế</a>

<details>
<summary>Xem chi tiết</summary>

# Matcher.replaceAll với hàm tính chuỗi thay thế trong Java

Khi dùng regex, nhiều trường hợp ta muốn mỗi đoạn khớp nhận **một chuỗi thay thế khác nhau**, được tính dựa trên chính đoạn đó. Ví dụ: nhân đôi từng số trong câu, chuẩn hóa từng mã định danh, che bớt dữ liệu theo độ dài, hoặc tra cứu giá trị để điền placeholder. Từ **Java 9**, lớp **java.util.regex.Matcher** có phương thức **replaceAll(Function<MatchResult, String>)** để giải quyết những bài toán này.

Điểm cốt lõi: với **mỗi đoạn khớp**, Matcher gọi hàm do ta cung cấp, đưa vào thông tin match hiện tại và lấy **chuỗi thay thế do hàm trả về**. Những đoạn không khớp vẫn được giữ nguyên. Bài này hướng dẫn API, cơ chế, ví dụ Java 17 chạy độc lập và các lỗi cần tránh.

## 1. Khác nhau giữa thay thế cố định và thay thế bằng hàm

| Cách dùng | Ý nghĩa | Khi phù hợp |
| --- | --- | --- |
| matcher.replaceAll(String replacement) | Áp dụng một khuôn chuỗi thay thế cho tất cả các lần khớp; có thể tham chiếu nhóm bắt. | Mọi match dùng cùng khuôn, ví dụ nối lại group 2 trước group 1. |
| matcher.replaceAll(Function<MatchResult, String> replacer) | Tính riêng chuỗi thay thế cho mỗi match bằng hàm/lambda. | Có chuyển đổi số, tra cứu, điều kiện hay biến đổi chuỗi phức tạp. |
| input.replaceAll(regex, replacement) | Phương thức của String nhận regex và **chuỗi thay thế**. | Cách viết nhanh khi không cần lambda. |

Chữ ký API, viết gọn:

~~~java
String replaceAll(Function<MatchResult, String> replacer)
~~~

Function là giao diện hàm trong java.util.function; MatchResult thuộc java.util.regex. Lambda thường nhận một tham số như match và trả về String. **String.replaceAll không có overload nhận lambda**: nếu muốn callback, phải tạo Pattern/Matcher rồi gọi overload của Matcher.

Với cùng một biểu thức dùng nhiều lần, nên biên dịch Pattern một lần, sau đó tạo Matcher cho đầu vào đang xử lý. Pattern bất biến và an toàn để chia sẻ; Matcher chứa trạng thái thay đổi, không nên dùng chung giữa các luồng.

## 2. Ví dụ hoàn chỉnh: nhân đôi các số vừa khớp

Tạo tệp **NumberReplacementDemo.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberReplacementDemo {
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    public static void main(String[] args) {
        String input = "Điểm A=7, B=12, C=3.";
        Matcher matcher = NUMBER.matcher(input);

        String output = matcher.replaceAll(match -> {
            int number = Integer.parseInt(match.group());
            return Integer.toString(number * 2);
        });

        System.out.println(input);
        System.out.println(output);
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 NumberReplacementDemo.java
java NumberReplacementDemo
~~~

Kết quả tiêu chuẩn:

~~~text
Điểm A=7, B=12, C=3.
Điểm A=14, B=24, C=6.
~~~

**Diễn giải từng bước:** biểu thức \d+ tìm ra ba đoạn lần lượt là 7, 12, 3. Matcher gọi lambda ba lần; mỗi lần match.group() là dãy chữ số đang được xử lý. Lambda chuyển chuỗi thành số nguyên, nhân đôi và trả về chuỗi mới. Java lắp những chuỗi thay thế vào vị trí tương ứng, giữ nguyên toàn bộ dấu cách, chữ cái và dấu câu ngoài các match. Biến input không đổi vì String trong Java bất biến.

Trong ví dụ, Integer.parseInt phù hợp vì đầu vào nhỏ và có kiểm soát. Với dữ liệu tùy ý, số quá lớn có thể gây NumberFormatException, phép nhân int có thể tràn; cần chọn BigInteger/long hoặc áp dụng giới hạn nghiệp vụ. Mẫu \d+ cũng không có nghĩa là bộ phân tích đầy đủ số âm, số thập phân và Unicode theo mọi quy ước địa phương.

## 3. Đọc group để tính replacement theo cấu trúc

Regex có thể chia kết quả khớp thành nhiều **nhóm bắt**. Đối tượng MatchResult cung cấp:

- group() hoặc group(0): toàn bộ đoạn khớp.
- group(1), group(2), ...: nội dung các nhóm bắt theo chỉ số.
- start() và end(): biên của toàn bộ match; end() là vị trí ngay sau ký tự cuối.
- start(n), end(n): biên nhóm bắt n. Một nhóm tùy chọn không tham gia có thể có group(n) bằng null, start/end bằng -1.

Ví dụ: chuẩn hóa mã dạng hai chữ cái, dấu gạch ngang, một đến ba chữ số. Chữ cái chuyển thành in hoa; số được thêm 0 bên trái để có ba chữ số.

Tạo tệp **CodeNormalizeDemo.java**:

~~~java
import java.util.Locale;
import java.util.regex.Pattern;

public class CodeNormalizeDemo {
    public static void main(String[] args) {
        Pattern code = Pattern.compile("(?i)\\b([a-z]{2})-(\\d{1,3})\\b");
        String input = "Đơn ab-7 và Cd-42; ghi chú giữ nguyên.";

        String output = code.matcher(input).replaceAll(match -> {
            String prefix = match.group(1).toUpperCase(Locale.ROOT);
            int number = Integer.parseInt(match.group(2));
            return String.format(Locale.ROOT, "%s-%03d", prefix, number);
        });

        System.out.println(output);
    }
}
~~~

~~~bash
javac -encoding UTF-8 CodeNormalizeDemo.java
java CodeNormalizeDemo
~~~

~~~text
Đơn AB-007 và CD-042; ghi chú giữ nguyên.
~~~

Ở đây (?i) bật phân biệt hoa/thường không nghiêm ngặt cho các chữ ASCII trong mẫu; group(1) là tiền tố, group(2) là chữ số. Regex chỉ nhận các đoạn đúng cấu trúc để lambda có thể biến đổi hai phần riêng biệt.

**Điểm cần chú ý với Java 17:** tham số lambda được suy luận là giao diện **MatchResult**, không phải Matcher. Trong Java 17, nên truy cập nhóm theo **chỉ số** bằng group(1), group(2); lời gọi group("tenNhom") không có trong giao diện MatchResult của Java 17. Không ép tham số này thành Matcher để đổi trạng thái.

## 4. Replacement do lambda trả về vẫn có cú pháp đặc biệt

Đây là điểm thường gây lỗi nhất. Mặc dù replacement được tính bằng hàm, chuỗi trả về **vẫn được Matcher diễn giải theo cú pháp chuỗi thay thế**, thay vì luôn được chèn như văn bản thuần.

Trong cú pháp replacement:
- $1, $2, ... có thể tham chiếu các nhóm bắt bằng số.
- Cú pháp dấu đô la kèm tên nhóm trong ngoặc nhọn có thể tham chiếu nhóm được đặt tên.
- Dấu gạch chéo ngược \ có thể dùng để thoát ký tự kế tiếp.

Vì thế nếu một hàm trả về nguyên văn **Giá $5** hoặc đường dẫn **C:\data\test**, ký tự $ và \ có thể bị hiểu thành cú pháp replacement. Đầu ra có thể sai hoặc gây ngoại lệ. Khi muốn trả về **văn bản nguyên trạng**, hãy dùng:

~~~java
String literal = "Giá $5";
String safe = Matcher.quoteReplacement(literal);
~~~

Hàm **Matcher.quoteReplacement(String)** tạo chuỗi có thể dùng làm replacement mà vẫn giữ nguyên nội dung literal sau khi thay. Nếu lambda đã ghép chuỗi đích xong, cách an toàn là:

~~~java
return Matcher.quoteReplacement(computedValue);
~~~

Phân biệt với **Pattern.quote(String)**: Pattern.quote dùng để biến dữ liệu người dùng thành một **mẫu tìm kiếm** theo nghĩa literal; Matcher.quoteReplacement dùng để biến dữ liệu thành **chuỗi thay thế** theo nghĩa literal. Chúng phục vụ hai phía khác nhau của phép tìm và thay thế.

Khi **muốn** replacement thực thi tham chiếu nhóm như $1, $2, có thể chủ động trả về cú pháp ấy mà **không quote**. Nếu đã dùng match.group(1) để tạo kết quả hoàn chỉnh trong lambda, thông thường nên quote chuỗi kết quả để tránh $ hoặc \ phát sinh từ dữ liệu.

## 5. Ví dụ hoàn chỉnh: thay placeholder bằng Map, giữ nguyên ký tự đặc biệt

Yêu cầu: chuỗi mẫu chứa các placeholder dạng {{key}}. Tìm giá trị tương ứng từ Map, giữ nguyên placeholder nếu chưa có dữ liệu. Đặc biệt, giá trị có thể chứa $ và \ mà kết quả vẫn phải giữ đúng các ký tự đó.

Tạo tệp **TemplateReplacementDemo.java**:

~~~java
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateReplacementDemo {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}");

    public static void main(String[] args) {
        String input = "Người: {{person}}; thư mục: {{path}}; thiếu: {{unknown}}";
        Map<String, String> values = Map.of(
                "person", "Lan$1",
                "path", "C:\\data\\test"
        );

        String output = PLACEHOLDER.matcher(input).replaceAll(match -> {
            String key = match.group(1);
            String value = values.getOrDefault(key, match.group());
            return Matcher.quoteReplacement(value);
        });

        System.out.println(output);
    }
}
~~~

~~~bash
javac -encoding UTF-8 TemplateReplacementDemo.java
java TemplateReplacementDemo
~~~

~~~text
Người: Lan$1; thư mục: C:\data\test; thiếu: {{unknown}}
~~~

**Giải thích:** regex chỉ nhận các placeholder theo cấu trúc {{tên}}. Nhóm 1 là tên khóa như person, path, unknown. Lambda lấy giá trị từ Map; nếu không có, dùng match.group() để giữ nguyên placeholder. Sau đó quoteReplacement bảo đảm dấu $ trong Lan$1 và các dấu \ trong đường dẫn được giữ nguyên. Nếu bỏ quoteReplacement, giá trị Lan$1 có thể bị hiểu thành tham chiếu nhóm $1, còn dấu \ có thể bị tiêu thụ khi xử lý replacement.

Đây là ví dụ thay thế văn bản, **không phải hệ thống template hoàn chỉnh**: nó không giải quyết escaping HTML, URL, SQL, JavaScript hay ngữ cảnh đầu ra khác. Khi đưa dữ liệu bên ngoài vào những ngữ cảnh đó, vẫn cần escaping phù hợp hoặc truy vấn tham số hóa.

## 6. Trình tự xử lý và trạng thái Matcher

Về mặt hành vi, replaceAll(Function) bắt đầu lại quá trình tìm kiếm, lần lượt tìm các đoạn khớp, gọi replacer cho mỗi đoạn và ghép kết quả thành một chuỗi đầu ra:

1. **Không có match:** hàm không được gọi, chuỗi đầu vào được giữ nguyên về nội dung.
2. **Có nhiều match:** hàm được gọi theo các kết quả tìm được, thông thường từ trái sang phải; các match do find() tìm là những đoạn không chồng lấn.
3. **Phần văn bản không khớp:** luôn được đưa vào kết quả nguyên vẹn.
4. **Replacement không được tìm tiếp trong cùng lượt:** nếu đoạn thay thế mới cũng khớp regex, nó không được xử lý thêm một lần nữa trong lời gọi hiện tại.
5. **Matcher chứa trạng thái duyệt:** replaceAll bắt đầu lượt thay thế từ đầu vùng đầu vào của Matcher; không trông chờ vị trí tìm kiếm trước đó được giữ nguyên. Nếu cần duyệt lại, tạo Matcher mới hoặc reset() đúng cách.
6. **Không sửa Matcher bên trong replacer:** gọi find(), reset(), region() hoặc phương thức thay đổi trạng thái trên chính Matcher đang thực hiện replaceAll có thể gây ConcurrentModificationException. Callback nên đọc tham số MatchResult và tạo chuỗi trả về.

Không lưu đối tượng MatchResult được callback cấp cho để dùng sau khi Matcher tiếp tục làm việc; nếu cần ghi nhận dữ liệu, sao chép group/start/end ngay trong callback. Pattern bất biến có thể dùng lại từ nhiều luồng, nhưng mỗi luồng/yêu cầu nên sử dụng Matcher của riêng mình.

## 7. So với find() + appendReplacement() + appendTail()

Trước Java 9, người lập trình thường dùng vòng lặp find() để tính replacement riêng từng match. Hai phương thức appendReplacement() và appendTail() hỗ trợ ghép kết quả: appendReplacement thêm phần chưa xử lý cùng replacement hiện tại; appendTail thêm phần còn lại của chuỗi gốc.

Đoạn minh họa sau giả định **pattern**, **input** và một phương thức tự viết **transform(String)** đã tồn tại (không phải chương trình biên dịch độc lập):

~~~java
Matcher matcher = pattern.matcher(input);
StringBuffer output = new StringBuffer();

while (matcher.find()) {
    String replacement = transform(matcher.group());
    matcher.appendReplacement(
            output,
            Matcher.quoteReplacement(replacement)
    );
}
matcher.appendTail(output);
String result = output.toString();
~~~

Với Java 9+, khi thao tác chỉ là tạo replacement từ từng match, có thể viết ngắn hơn:

~~~java
String result = pattern.matcher(input).replaceAll(
        match -> Matcher.quoteReplacement(transform(match.group()))
);
~~~

Hai cách đều cần quan tâm đến cú pháp replacement, và hai cách đều nên được thử với dữ liệu chứa $ và \. Vòng lặp thủ công vẫn hữu ích khi phải quản lý thêm trạng thái hoặc ghép đầu ra theo quy tắc riêng; callback tiện hơn cho phép biến đổi độc lập của từng match.

## 8. Điều kiện trong lambda: chỉ đổi một số match

Có những trường hợp regex nhận đúng tất cả đoạn cần xét, nhưng không phải đoạn nào cũng cần thay. Callback có thể quyết định giữ nguyên bằng cách trả lại chính group().

Đoạn mã sau giả định **input** đã được khai báo; mục đích là đưa ngoặc vuông quanh các số một chữ số và giữ nguyên các số khác:

~~~java
Pattern numbers = Pattern.compile("\\b\\d+\\b");
String result = numbers.matcher(input).replaceAll(match -> {
    int value = Integer.parseInt(match.group());
    String replacement = value < 10
            ? "[" + value + "]"
            : match.group();
    return Matcher.quoteReplacement(replacement);
});
~~~

Ngay cả ở nhánh giữ nguyên, lambda vẫn được gọi cho match đó. Nếu muốn tối ưu, thiết kế regex sao cho chỉ tìm đúng những đoạn thực sự cần thay khi khả thi. Cần xử lý trường hợp số quá lớn hoặc có định dạng khác trước khi parseInt.

Callback nên tập trung vào **tính toán chuỗi**. Tránh ghi database, gọi HTTP, thay đổi biến dùng chung hoặc tương tác bên ngoài trong một phép replaceAll; những side effect này dễ gây kết quả khó đoán nếu xử lý lỗi hoặc chạy lại. Nếu tính toán tốn kém cho mỗi match, hiệu suất thực tế phụ thuộc cả số match, độ phức tạp regex và công việc của hàm.

## 9. Lỗi thường gặp và cách phòng tránh

| Triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| Không biên dịch lambda cho replaceAll | Dùng Java 8 hoặc gọi String.replaceAll thay vì Matcher.replaceAll. | Sử dụng Java 9+, gọi Pattern.compile(...).matcher(input).replaceAll(lambda). |
| Bị lỗi khi giá trị chứa dấu $ | Dữ liệu bị hiểu là tham chiếu nhóm trong replacement. | Dùng Matcher.quoteReplacement cho chuỗi literal. |
| Dấu \ mất hoặc làm thay đổi ký tự phía sau | Replacement diễn giải \ như ký tự thoát. | Quote replacement literal; kiểm tra lại cách escape trong chuỗi Java. |
| match.group("name") không biên dịch trên Java 17 | MatchResult trong Java 17 dùng chỉ số nhóm. | Dùng group(1), group(2) theo vị trí. |
| NullPointerException khi xử lý group | Nhóm tùy chọn không tham gia, hoặc giá trị tra cứu là null. | Kiểm tra null, trả về chuỗi mặc định khác null. |
| NumberFormatException | Dữ liệu số quá dài hoặc không hợp lệ cho kiểu đang parse. | Giới hạn định dạng/giá trị; dùng long/BigInteger nếu cần. |
| ConcurrentModificationException | Lambda làm thay đổi trạng thái chính Matcher đang thay thế. | Chỉ dùng đối số MatchResult, không gọi find/reset trên Matcher đó. |
| Hàm không chạy | Không có match, hoặc regex không nhận dạng đoạn mong đợi. | Kiểm tra biểu thức, cờ biên dịch và đầu vào. |
| Thay quá nhiều phần nội dung | Regex quá rộng, ví dụ .* không có ràng buộc. | Thu hẹp mẫu, thêm các ranh giới phù hợp và test trường hợp phản ví dụ. |
| Quá chậm với dữ liệu dài | Regex gây backtracking hoặc hàm chạy nặng cho từng match. | Đo đạc, viết regex rõ ràng và tối ưu công việc của callback. |

## 10. Chọn giải pháp trong thực tế

Chọn **replaceAll(Function)** khi chuỗi thay thế cần được tính riêng theo nội dung match: chuyển đổi số, chuẩn hóa nhóm bắt, tra Map hay áp dụng điều kiện. Chọn **replaceAll(String)** khi chỉ cần khuôn cố định hoặc tham chiếu nhóm trong replacement. Chọn vòng lặp **find + appendReplacement + appendTail** khi cần kiểm soát việc ghép kết quả hoặc duy trì tương thích với Java cũ.

Khi kiểm thử, hãy bao phủ ít nhất: input không có match; nhiều match liền nhau; nhóm bắt tùy chọn; replacement chứa $ hoặc \; số vượt giới hạn; regex có thể khớp chuỗi rỗng; và callback báo lỗi. Một lỗi trong callback có thể làm toàn bộ lời gọi replaceAll thất bại, vì vậy hãy kiểm tra dữ liệu và quyết định chiến lược xử lý lỗi trước khi sử dụng trong ứng dụng.

Ghi nhớ: **Pattern xác định đoạn nào được chọn; Function quyết định từng chuỗi thay thế; Matcher diễn giải replacement và ghép kết quả.** Hiểu rõ ba bước này giúp sử dụng replaceAll với hàm chính xác, dễ kiểm thử và an toàn hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-007">Thay thế nhiều lần và giới hạn của việc dùng regex thay bộ phân tích</a>

<details>
<summary>Xem chi tiết</summary>

# Thay thế nhiều lần và giới hạn của việc dùng regex thay bộ phân tích

## 1. Hai kiểu thay thế “nhiều lần”

Trong Java, `String.replaceAll(String regex, String replacement)` tìm các đoạn văn bản **khớp regex và không chồng lấn** trong chuỗi đầu vào, rồi thay tất cả các đoạn đó trong **một lượt**. Nó không tự tiếp tục tìm trong chính phần văn bản vừa được thay.

Điều này khác với việc chương trình **gọi thay thế lặp nhiều lượt**, ví dụ lặp cho đến khi kết quả ổn định, hoặc lần lượt áp dụng nhiều quy tắc thay thế.

```java
String once = "aaaa".replaceAll("aa", "a"); // "aa", không phải "a"

String twice = once.replaceAll("aa", "a");  // "a"
```

Trong lượt đầu, hai phần `"aa"` của `"aaaa"` được thay bằng hai chữ `a`, tạo `"aa"`. Lượt thứ hai mới tạo `"a"`. Một lần gọi `replaceAll` thay **tất cả match của lượt hiện tại**, không có nghĩa là “lặp đến khi không còn match”.

`String` là kiểu bất biến: mỗi phương thức trả về một chuỗi mới hoặc chuỗi hiện tại khi không đổi; muốn dùng kết quả tiếp thì phải gán lại biến.

## 2. Chọn quy tắc phù hợp trước khi lặp

**String.replace** thay văn bản cố định theo nghĩa literal; **String.replaceFirst** và **String.replaceAll** tìm theo regex, lần lượt thay match đầu tiên hoặc mọi match không chồng lấn của một lượt. Nếu dùng cùng regex trên nhiều đầu vào, biên dịch một **Pattern** rồi tạo **Matcher** riêng cho mỗi đầu vào.

Một replacement template cố định có thể tham chiếu nhóm bằng **$1** hoặc **${name}**; giá trị nguyên văn chứa dấu đô la hoặc gạch chéo ngược cần **Matcher.quoteReplacement**. Nếu replacement phụ thuộc từng match, Java 9+ có **Matcher.replaceAll(Function<MatchResult,String>)**, còn **appendReplacement/appendTail** hỗ trợ khi muốn kiểm soát quá trình ghép văn bản. Việc chọn API đúng không thay thế yêu cầu phân tích khả năng hội tụ và giới hạn của dữ liệu cấu trúc ở các mục sau.

## 3. Ví dụ Java 17+ biên dịch và chạy được

Tạo file `RepeatedRegexReplacementDemo.java`:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepeatedRegexReplacementDemo {
    // Chỉ dùng khi quy tắc có cơ sở để hội tụ.
    private static String replaceUntilStable(
            String input, Pattern pattern, String replacement, int maxPasses) {
        if (maxPasses < 1) {
            throw new IllegalArgumentException("maxPasses must be positive");
        }

        String current = input;
        for (int pass = 0; pass < maxPasses; pass++) {
            String next = pattern.matcher(current).replaceAll(replacement);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        throw new IllegalStateException("No convergence within limit");
    }

    public static void main(String[] args) {
        // Một lượt gộp các nhóm khoảng trắng liên tiếp.
        String text = "red  green\tblue";
        System.out.println("Normalize: "
                + text.replaceAll("\\s+", " ").trim());

        // Một lượt khác với lặp nhiều lượt đến khi ổn định.
        System.out.println("Single pass: " + "aaaa".replaceAll("aa", "a"));
        System.out.println("Until stable: "
                + replaceUntilStable("aaaa", Pattern.compile("aa"), "a", 10));

        // replace nguyên văn khác replaceAll nhận regex.
        System.out.println("Literal dot: " + "a.b".replace(".", "|"));
        System.out.println("Regex dot: " + "a.b".replaceAll(".", "|"));

        // match đầu tiên khác tất cả match.
        System.out.println("First: " + "foo foo".replaceFirst("foo", "bar"));
        System.out.println("All: " + "foo foo".replaceAll("foo", "bar"));

        // Replacement nguyên văn có ký tự đặc biệt của cú pháp replacement.
        String price = "$5\\item";
        String safe = Pattern.compile("\\{\\{price\\}\\}")
                .matcher("cost={{price}}")
                .replaceAll(Matcher.quoteReplacement(price));
        System.out.println("Safe literal: " + safe);

        // Tính riêng replacement cho mỗi match (Java 9+).
        String calculated = Pattern.compile("(\\w+)=(\\d+)")
                .matcher("a=3;b=42")
                .replaceAll(match ->
                        match.group(1).toUpperCase()
                                + "=" + (Integer.parseInt(match.group(2)) + 1));
        System.out.println("Calculated: " + calculated);

        // Regex không hiểu quy tắc dấu nháy kép của CSV.
        String csv = "a,\"b,c\",d";
        System.out.println("Naive CSV: " + csv.replaceAll(",", "|"));

        // Mẫu zero-width có thể dẫn tới chèn ký tự.
        System.out.println("Zero width: " + "aa".replaceAll("(?=a)", "X"));
    }
}
```

Biên dịch và chạy:

```bash
javac RepeatedRegexReplacementDemo.java
java RepeatedRegexReplacementDemo
```

Đầu ra:

```text
Normalize: red green blue
Single pass: aa
Until stable: a
Literal dot: a|b
Regex dot: |||
First: bar foo
All: bar bar
Safe literal: cost=$5\item
Calculated: A=4;B=43
Naive CSV: a|"b|c"|d
Zero width: XaXa
```

Ở phần `Until stable`, dữ liệu đi qua `"aaaa" → "aa" → "a"`: cần **hai lượt có thay đổi** và một lượt kiểm tra thấy không còn thay đổi. Phần `Calculated` tính kết quả riêng cho từng match: `a=3` thành `A=4`, còn `b=42` thành `B=43`. Phần `Naive CSV` cho thấy regex thay cả dấu phẩy nằm **bên trong trường được nháy kép**, vốn không phải dấu phân cách giữa các trường theo quy tắc CSV.

## 4. Thay thế theo nhiều quy tắc: thứ tự quan trọng

Có thể ghép nhiều quy tắc để xử lý văn bản đơn giản:

```java
String input = "  Hello,\tJava!!  ";
String result = input
        .replaceAll("\\s+", " ")  // Gom các nhóm khoảng trắng.
        .replaceAll("!+", "!")    // Gom nhiều dấu ! liên tiếp.
        .trim();                  // Bỏ khoảng trắng hai đầu.

System.out.println(result);      // Hello, Java!
```

Đây là **đoạn minh họa trong phương thức hoặc khối lệnh**, không phải một lớp Java độc lập. Mỗi lời gọi dùng kết quả của lời gọi trước, nên đổi thứ tự có thể đổi kết quả. Ví dụ, nếu quy tắc thứ nhất đổi `"ab"` thành `"b"`, và quy tắc thứ hai đổi `"b"` thành `"c"`, chuỗi `"ab"` có thể đi tới `"c"` khi dùng đúng thứ tự; nếu đảo thứ tự sẽ còn `"b"`.

Khi dùng một regex nhiều lần, biên dịch `Pattern` một lần:

```java
Pattern whitespace = Pattern.compile("\\s+");
String first = whitespace.matcher("a   b").replaceAll(" ");
String second = whitespace.matcher("c\t d").replaceAll(" ");
```

`Pattern` được thiết kế để tái sử dụng. `Matcher` giữ trạng thái tìm kiếm, nên tạo `Matcher` mới cho từng chuỗi hoặc quản lý trạng thái bằng API phù hợp; đừng chia sẻ một `Matcher` đồng thời giữa nhiều luồng.

Nếu cần thay thế theo nhóm bắt, có thể dùng cú pháp replacement:

```java
String result = "2026-10-23".replaceAll(
        "(\\d{4})-(\\d{2})-(\\d{2})", "$3/$2/$1");
// result = "23/10/2026"
```

Nhóm bắt giúp đổi thứ tự dữ liệu có cấu trúc **đơn giản, đã kiểm soát**. Chuỗi replacement lấy từ bên ngoài cần `Matcher.quoteReplacement` nếu muốn chèn nguyên văn. Ngay cả hàm `Matcher.replaceAll(match -> ...)` cũng trả về một **replacement string** có cú pháp `$`/`\\`; bọc kết quả bằng `Matcher.quoteReplacement` nếu nó có thể chứa các ký tự đặc biệt và phải được chèn nguyên văn.

## 5. Lặp đến khi ổn định: không phải lúc nào cũng kết thúc

Quy tắc `"aa" → "a"` có số chữ `a` giảm qua mỗi lượt, vì vậy có cơ sở hội tụ. Nhưng không phải phép thay thế nào cũng có tính chất này:

- `"a" → "aa"`: mỗi lượt làm chuỗi dài thêm, không ổn định nếu đầu vào vẫn chứa `a`.
- Quy tắc luân phiên `"a" → "b"` và `"b" → "a"`: có thể quay lại trạng thái cũ, tạo chu trình.
- Có quy tắc tạo ra thêm nhiều mẫu mới, khiến kích thước đầu ra tăng nhanh và chi phí xử lý tăng theo.

Vòng lặp `replaceUntilStable` trong ví dụ dùng giới hạn `maxPasses` và **ném lỗi** khi chưa hội tụ, giúp tránh vòng lặp vô hạn do chỉ chờ hai chuỗi liên tiếp bằng nhau. Tuy nhiên, giới hạn số lượt **không tự giới hạn kích thước chuỗi**: trong thực tế có thể cần thêm giới hạn kích thước đầu ra, thời gian thực thi và kiểm tra chu trình dài hơn một trạng thái.

Khi thiết kế một pipeline lặp, nên xác định:

1. **Điều kiện dừng:** không đổi so với lượt trước, không còn token cần thay, hoặc đã đạt số lượt quy định.
2. **Lý do hội tụ:** ví dụ có đại lượng không âm giảm nghiêm ngặt qua mỗi lượt. Nếu chưa chứng minh được, phải xử lý trường hợp không hội tụ.
3. **Giới hạn tài nguyên:** số lượt, kích thước đầu vào/đầu ra và bộ nhớ cần cấp phát.
4. **Quy tắc khi thất bại:** báo lỗi rõ ràng, không âm thầm trả một kết quả chưa hoàn tất nếu caller chờ kết quả ổn định.

Một lần `replaceAll` không tìm tất cả **match chồng lấn**. Chẳng hạn, `"aaa"` có hai vị trí bắt đầu cho mẫu `"aa"` nếu xét chồng lấn, nhưng `replaceAll("aa", "X")` chỉ lấy match đầu tiên và cho `"Xa"`. Nếu bài toán thật sự cần xử lý match chồng lấn, hãy thiết kế lookahead một cách có chủ đích hoặc viết bộ quét chỉ số ký tự; lặp `replaceAll` một cách tùy tiện không phải lời giải tổng quát.

Mẫu có **độ rộng 0** (zero-width), như `(?=a)`, có thể chèn thay vì xóa ký tự: `"aa".replaceAll("(?=a)", "X")` tạo `"XaXa"`. Engine quản lý việc tiến qua các match zero-width trong một lượt, nhưng đầu ra vẫn cần được kiểm thử kỹ ở đầu/cuối chuỗi, chuỗi rỗng và khi có Unicode.

## 6. Giới hạn của việc dùng regex thay bộ phân tích

Regex mô tả **mẫu văn bản** và phù hợp với các phép biến đổi cục bộ, như chuẩn hóa khoảng trắng, che một chuỗi ID có định dạng đơn giản, đổi một tiền tố có ranh giới rõ ràng hoặc thay một token cố định. Một **bộ phân tích cú pháp** (parser) hiểu vai trò các thành phần trong ngữ pháp, chẳng hạn “ký tự này nằm trong chuỗi được nháy kép”, “đây là thuộc tính của thẻ nào” hoặc “dấu ngoặc này thuộc cấp lồng nào”.

| Dữ liệu | Vì sao regex thay thế đơn giản dễ sai? | Cách tiếp cận phù hợp |
| --- | --- | --- |
| CSV | Dấu phẩy bên trong trường có dấu nháy kép không ngăn cách cột; còn có escape dấu nháy và xuống dòng trong trường. | Dùng parser CSV để nhận từng trường rồi ghi dữ liệu theo quy tắc CSV. |
| JSON | Dấu phẩy, dấu hai chấm, ngoặc và dấu nháy có vai trò khác nhau theo trạng thái; object/array lồng nhau, chuỗi có escape. | Parse JSON thành cây/đối tượng, sửa đúng node, rồi serialize. |
| HTML/XML | Thẻ, thuộc tính, entity, bình luận, text node và cấu trúc cây; `>` có thể xuất hiện trong giá trị thuộc tính được quote. | Dùng parser HTML/XML để thao tác node hoặc attribute. |
| SQL và mã nguồn | Dấu câu trong string literal, comment và cấu trúc lồng nhau không có cùng ý nghĩa với token cú pháp. | Dùng lexer/parser/AST hoặc API chuyên biệt. |
| Ngoặc lồng nhau tùy độ sâu | Mẫu cần nhớ các cấp cấu trúc; regex Java thông thường không phải parser tổng quát cho ngữ pháp lồng vô hạn. | Dùng parser hoặc bộ quét có stack/trạng thái rõ ràng. |

Ví dụ, đoạn CSV `a,"b,c",d` có **3 trường**. Thay tất cả dấu phẩy bằng `|` theo regex tạo `a|"b|c"|d`, đặt một dấu `|` vào giữa trường `"b,c"`; sau đó không thể mặc nhiên coi mỗi `|` là dấu phân cách mới của ba trường. Một parser CSV phân biệt dấu phẩy **ở ngoài** và **ở trong** cặp nháy kép, kể cả nháy được escape.

Tương tự, một biểu thức như `<[^>]*>` không tạo ra parser HTML hoàn chỉnh: dấu `>` có thể nằm trong thuộc tính được nháy, và ngữ cảnh thẻ/comment/text có thể khác nhau. Thêm các regex để vá từng trường hợp dễ làm giải pháp khó hiểu và dễ phá dữ liệu hợp lệ.

**Tiêu chí quyết định:** Nếu cách thay thế phụ thuộc vào ngữ cảnh quote, escape, cấu trúc cây, phạm vi lồng nhau hoặc ngữ pháp tài liệu, dùng mô hình **parse → sửa cấu trúc → serialize**. Nếu mẫu khớp chỉ phụ thuộc vào một đoạn văn bản cục bộ đã biết, regex có thể phù hợp.

Không phải mọi thay đổi trong JSON, HTML hay CSV đều tuyệt đối cấm dùng regex: chẳng hạn có thể thay một đoạn text độc lập đã được lấy ra bởi parser. Điều cần tránh là dùng regex để **đoán ranh giới cấu trúc** của cả tài liệu phức tạp.

## 7. Hiệu năng và các lỗi thường gặp

**Backtracking quá mức.** Một số regex có lượng từ lồng hoặc nhánh mơ hồ như `(a+)+$` có thể rất chậm với chuỗi dài gồm nhiều `a` rồi một ký tự không khớp. Java `Pattern` dùng cơ chế có backtracking; với đầu vào hoặc pattern không đáng tin, cần giới hạn kích thước dữ liệu, tránh các mẫu mơ hồ và kiểm tra trường hợp xấu. Không ghép chuỗi do người dùng nhập trực tiếp thành regex nếu yêu cầu chỉ là khớp nguyên văn.

**Quét lặp lại và cấp phát nhiều chuỗi.** Mỗi lượt regex có thể quét lại một chuỗi lớn và tạo kết quả mới. Nếu thuật toán cần lặp rất nhiều lượt, hãy cân nhắc một bộ quét trạng thái qua dữ liệu một lần hoặc một parser phù hợp.

**Nhầm `replaceAll` với `replace`.** `.`, `|`, `*`, `[` và `\\` có cú pháp riêng trong regex. Thay văn bản cố định thì `String.replace` dễ đọc và ít gây nhầm hơn. Nếu vẫn dùng regex với dấu phân cách nguyên văn, hãy áp `Pattern.quote` cho pattern.

**Nhầm ký tự `$` và `\\` trong replacement.** Nếu muốn chèn nguyên văn `"$5\item"`, hãy dùng `Matcher.quoteReplacement`. Nếu định dùng nhóm bắt, cú pháp `$1` hoặc `${name}` cần nhóm bắt tồn tại, nếu không có thể phát sinh lỗi.

**Quy tắc tưởng như vô hại nhưng làm hỏng cú pháp.** Thay dấu phẩy toàn cục trong CSV, thay từ trong string literal của mã nguồn, hoặc đổi key JSON bằng regex quét nguyên tài liệu có thể làm thay sai vị trí. Với cấu trúc dữ liệu, dùng parser.

**Thiếu kiểm thử biên.** Kiểm thử đầu/cuối chuỗi, không có match, nhiều match liên tiếp, chuỗi rỗng, zero-width, `$`, gạch chéo ngược, Unicode, xuống dòng và dữ liệu lớn. Các cờ regex `DOTALL`, `MULTILINE`, `CASE_INSENSITIVE` có thể thay đổi ý nghĩa của mẫu; cần dùng có chủ đích và có ca kiểm thử phù hợp.

## 8. Tự kiểm tra

**Câu 1.** Vì sao `"aaaa".replaceAll("aa", "a")` trả `"aa"`, còn gọi thêm một lần trả `"a"`? Hãy giải thích theo khái niệm match không chồng lấn và không tự quét lại đầu ra.

**Câu 2.** Nếu người dùng gửi văn bản thay thế `"$5\item"`, vì sao đưa trực tiếp vào `replaceAll` có thể làm lỗi hoặc đổi dữ liệu? Hãy chỉ ra API dùng để quote phần replacement và API dùng để quote phần pattern.

**Câu 3.** Muốn thay dấu phân cách giữa các trường CSV `a,"b,c",d` nhưng giữ dấu phẩy trong `"b,c"`, vì sao một `replaceAll(",", "|")` không đủ? Mô tả các bước với parser CSV.

**Câu 4.** Một quy tắc thay `"a"` thành `"aa"` có hội tụ khi lặp đến lúc không đổi hay không? Nếu không, vòng lặp phải kiểm soát những giới hạn nào?

**Ghi nhớ:** `replaceAll` thay mọi match không chồng lấn **trong một lượt**. Khi lặp nhiều lượt cần kiểm soát điều kiện dừng và tài nguyên; khi việc thay phụ thuộc vào cấu trúc/ngữ pháp của dữ liệu, dùng parser để thay đúng thành phần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regex-replacement-008">Kiểm thử dữ liệu chứa ký tự có ý nghĩa đặc biệt</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử dữ liệu chứa ký tự có ý nghĩa đặc biệt khi thay thế bằng regex trong Java

## 1. Vì sao cần kiểm thử riêng?

`String.replaceAll(String regex, String replacement)` và `String.replaceFirst(String regex, String replacement)` sử dụng **hai ngôn ngữ cú pháp khác nhau**:

- **Regex tìm kiếm**: dấu chấm, dấu sao, dấu cộng, dấu hỏi, ngoặc, dấu gạch đứng, dấu mũ, dấu dollar và backslash đều có thể mang ý nghĩa đặc biệt.
- **Chuỗi replacement**: `$1` hoặc nhóm bắt có tên có thể được thay bằng nội dung nhóm đã khớp; dấu backslash có vai trò escape ký tự tiếp theo.

Bên ngoài hai cú pháp đó còn có **cú pháp Java string literal**: dấu backslash phải được escape một lần nữa trong mã Java. Vì vậy, dữ liệu chứa `.`, `[`, `|`, `$` và `\` dễ làm một phép thay thế trả kết quả sai hoặc ném ngoại lệ.

Ví dụ, `"a.b".replaceAll(".", "_")` trả về `"___"` chứ không phải `"a_b"`. Regex `.` là wildcard khớp các ký tự `a`, `.` và `b`. Ở phía replacement, truyền thẳng `"$1"` khi không có nhóm bắt thứ nhất có thể ném `IndexOutOfBoundsException`; một dấu `"$"` đứng riêng trong replacement có thể ném `IllegalArgumentException`.

**Mục tiêu kiểm thử:** chứng minh rằng dữ liệu tìm kiếm được xem đúng là regex hay văn bản nguyên văn theo yêu cầu, replacement giữ đúng từng ký tự, các trường hợp biên hoạt động rõ ràng, và lỗi cú pháp được phát hiện.

## 2. Phân biệt các API

| Nhu cầu | Cách dùng | Vì sao |
| --- | --- | --- |
| Thay thế hai chuỗi **nguyên văn** | `text.replace(target, replacement)` | `String.replace(CharSequence, CharSequence)` không diễn giải hai đối số theo regex hoặc tham chiếu nhóm. |
| Thay nguyên văn nhưng cần gọi `replaceAll` | `text.replaceAll(Pattern.quote(target), Matcher.quoteReplacement(replacement))` | Quote riêng cho cú pháp regex tìm kiếm và cú pháp replacement. |
| Chủ động thay theo regex và dùng nhóm bắt | `text.replaceAll("(\\d+)", "[$1]")` | Giữ regex và `$1` đúng mục đích. |
| Chỉ thay lần khớp đầu | `replaceFirst(regex, replacement)` | Vẫn phải áp dụng đúng hai quy tắc escape như `replaceAll`. |

**`Pattern.quote(String s)`** biến chuỗi tìm kiếm thành biểu thức khớp **nguyên văn**. Nó hoạt động với metacharacter như `.`, `*`, `[`, `\` và cả các chuỗi chứa tổ hợp `\E`. Nó không xử lý replacement.

**`Matcher.quoteReplacement(String s)`** biến chuỗi thay thế thành **văn bản nguyên văn** khi truyền cho `replaceAll`/`replaceFirst`. Nó giữ ký tự `$` và `\`, không biến `$1` thành group reference. Nó không xử lý regex.

Hai chức năng **không thay thế được cho nhau**. Nếu cả hai đối số là dữ liệu văn bản thuần túy, `String.replace` thường là API đơn giản nhất.

Ví dụ ngắn (các lệnh đặt bên trong phương thức Java):

~~~java
String text = "a.b";
System.out.println(text.replaceAll(".", "_"));          // ___
System.out.println(text.replaceAll("\\.", "_"));        // a_b
System.out.println(text.replace(".", "$1\\backup"));    // a$1\backup
~~~

Lệnh đầu dùng wildcard; lệnh thứ hai escape đúng regex dấu chấm; lệnh thứ ba thay văn bản nguyên văn, nên `$1` và backslash không bị diễn giải theo cú pháp replacement.

## 3. Hàm thay thế nguyên văn an toàn

Nếu API của hệ thống yêu cầu dùng `replaceAll`, có thể bọc quy tắc escape:

~~~java
static String replaceLiteral(String text, String target, String replacement) {
    if (target.isEmpty()) {
        throw new IllegalArgumentException("target must not be empty");
    }
    return text.replaceAll(
            java.util.regex.Pattern.quote(target),
            java.util.regex.Matcher.quoteReplacement(replacement));
}
~~~

Đây là **mảnh phương thức minh họa**, được tích hợp đầy đủ trong chương trình phía dưới.

Hàm chủ động **từ chối target rỗng**. Vì regex rỗng có thể khớp các vị trí độ rộng 0, việc thay toàn bộ có thể chèn replacement vào nhiều ranh giới; điều này thường không phải yêu cầu “tìm và thay một từ khóa”. Nếu ứng dụng muốn cho phép target rỗng, hãy quy định hành vi cụ thể và viết thêm test cho trường hợp đó.

Không nên chỉ escape thủ công một hoặc hai ký tự: danh sách metacharacter regex rộng và cách parse replacement lại khác hẳn. Với dữ liệu thuần, hãy dùng API quote của thư viện chuẩn.

## 4. Bộ kiểm thử Java 17+ chạy độc lập

Ví dụ sau kiểm tra cả ký tự đặc biệt ở **target**, ở **replacement**, hành vi khi không khớp, Unicode, newline và những ngoại lệ cần xuất hiện. Không cần thư viện bên ngoài hay JUnit. Mỗi trường hợp so sánh chính xác chuỗi đầu ra bằng `equals`.

Tạo tệp **RegexSpecialCharacterTest.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RegexSpecialCharacterTest {
    private static int passed = 0;

    static String replaceLiteral(String text, String target, String replacement) {
        if (target.isEmpty()) {
            throw new IllegalArgumentException("target must not be empty");
        }
        return text.replaceAll(
                Pattern.quote(target),
                Matcher.quoteReplacement(replacement));
    }

    private static void check(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    name + ": expected <" + expected + "> but got <" + actual + ">");
        }
        passed++;
    }

    private static void expectThrows(
            String name, Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            if (type.isInstance(ex)) {
                passed++;
                return;
            }
            throw new AssertionError(name + ": unexpected exception " + ex, ex);
        }
        throw new AssertionError(name + ": expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        // Special characters in regex search patterns.
        check("regex dot wildcard", "___", "a.b".replaceAll(".", "_"));
        check("literal dot", "a_b", replaceLiteral("a.b", ".", "_"));
        check("literal star", "aXb", replaceLiteral("a*b", "*", "X"));
        check("literal pipe", "a/b/c", replaceLiteral("a|b|c", "|", "/"));
        check("literal brackets", "X X",
                replaceLiteral("[tag] [tag]", "[tag]", "X"));
        check("literal backslash search", "C:/temp/v1",
                replaceLiteral("C:\\temp\\v1", "\\", "/"));
        check("literal dollar search", "price USD",
                replaceLiteral("price $5", "$5", "USD"));

        // Literal dollar and backslash in replacement text.
        check("replacement dollar one", "x$1y",
                replaceLiteral("x.y", ".", "$1"));
        check("replacement backslash", "x\\tmpy",
                replaceLiteral("x.y", ".", "\\tmp"));
        check("replacement both", "$1\\new",
                replaceLiteral("$5", "$5", "$1\\new"));

        // Use a capturing group deliberately.
        check("intentional capture", "x=[42]",
                "x=42".replaceAll("(\\d+)", "[$1]"));
        check("replaceFirst quotes replacement", "a$1a.a",
                "a.a.a".replaceFirst(
                        Pattern.quote("."), Matcher.quoteReplacement("$1")));

        // Boundaries: no match, adjacent matches, Unicode, and newlines.
        check("no match", "abc", replaceLiteral("abc", "XYZ", "$\\"));
        check("adjacent delimiters", "$\\$\\$\\$\\",
                replaceLiteral("....", ".", "$\\"));
        check("Unicode and emoji", "[X]\uD83D\uDE42[X]",
                replaceLiteral(
                        "\u0111\u00E1\uD83D\uDE42\u0111\u00E1",
                        "\u0111\u00E1", "[X]"));
        check("line break as data", "a\\nb",
                replaceLiteral("a\nb", "\n", "\\n"));
        check("empty input", "", replaceLiteral("", ".", "_"));

        // Invalid input must fail in a controlled, testable way.
        expectThrows("empty target", IllegalArgumentException.class,
                () -> replaceLiteral("abc", "", "x"));
        expectThrows("invalid regex [", PatternSyntaxException.class,
                () -> "abc".replaceAll("[", "x"));
        expectThrows("bare dollar replacement", IllegalArgumentException.class,
                () -> "a.b".replaceAll("\\.", "$"));

        System.out.println("PASS: " + passed + " checks");
    }
}
~~~

Biên dịch, rồi chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 RegexSpecialCharacterTest.java
java RegexSpecialCharacterTest
~~~

Stdout khi toàn bộ kiểm thử thành công:

~~~text
PASS: 20 checks
~~~

Nếu kỳ vọng khác kết quả thực tế, chương trình ném `AssertionError` và sẽ **không in thông báo PASS**. Cách này đáng tin cậy hơn việc chỉ nhìn `System.out.println` để đoán các token rỗng hay dấu backslash. `expectThrows` còn kiểm tra đúng loại ngoại lệ thay vì coi mọi ngoại lệ đều là thành công. Không dùng Java `assert` ở đây vì câu lệnh `assert` có thể bị bỏ qua nếu JVM chạy thiếu cờ `-ea`.

Các trường hợp bao quát nhiều hơn phép thay một dấu chấm: mã còn thử dấu `|`, dấu ngoặc vuông, backslash trong từ khóa tìm kiếm, `$5` trong từ khóa, `$1` và `\` trong replacement, thay một lần bằng `replaceFirst`, nhóm bắt thật `$1`, nhiều dấu liên tiếp, dữ liệu Unicode/emoji, xuống dòng thật, chuỗi đầu vào rỗng và các đầu vào sai cú pháp.

## 5. Ma trận kiểm thử với dữ liệu thực

| Nhóm | Tình huống cần thử | Kỳ vọng quan trọng |
| --- | --- | --- |
| Regex metacharacters | Các ký tự `.`, `*`, `+`, `?`, `|`, `[`, `]`, `(`, `)`, `^`, `$` | Khi mục tiêu là tìm nguyên văn, ký tự không biến thành wildcard, quantifier, character class, nhóm hoặc anchor. |
| Backslash trong target | Tìm `\` trong một đường dẫn | Regex khớp đúng một dấu backslash và không lỗi cú pháp. |
| Dollar trong target | Tìm chuỗi `$5` | Dollar là dữ liệu nguyên văn, không thành anchor cuối chuỗi. |
| Dollar trong replacement | Thay thành `$1`, `$5` hoặc chỉ `$` | Dollar không bị xử lý thành nhóm bắt nếu ứng dụng không yêu cầu. |
| Backslash trong replacement | Thay thành chuỗi chứa `\tmp` hoặc `\` | Backslash vẫn tồn tại chính xác trong kết quả. |
| Nhiều khớp liên tiếp | Thay từng dấu chấm trong `....` | Mỗi vị trí khớp được xử lý đúng một lần; không thiếu hoặc nhân đôi. |
| Không có khớp | Input `abc`, target `XYZ` | Trả về nguyên chuỗi ban đầu. |
| Unicode và emoji | Ký tự tiếng Việt có dấu và emoji | Không mất dấu hay làm hỏng cặp surrogate. |
| Xuống dòng | Thay newline thật bằng hai ký tự `\` và `n` | Phân biệt newline thật với văn bản biểu diễn newline. |
| Biên | Input rỗng, target rỗng, dữ liệu lặp | Có kết quả rõ ràng hoặc ngoại lệ được quy định. |
| Cú pháp hỏng | Regex `[` hoặc replacement `$` chưa quote | Test xác nhận đúng loại lỗi, không nuốt exception. |

Khi có yêu cầu mới, mở rộng ma trận: chỉ thay lần đầu, thay không phân biệt chữ hoa/thường, nhóm bắt có tên, ký tự xuống dòng Windows CRLF, chuỗi rất dài hoặc target thuộc nhiều bảng mã. Các lựa chọn đó cần được kiểm thử theo hợp đồng thực tế của ứng dụng.

## 6. Ba lớp escape thường bị nhầm

### Lớp 1: Java string literal

Để có một dấu backslash thật ở runtime, mã Java viết `"\\"`. Newline thật viết `"\n"`. Để có hai ký tự **backslash rồi chữ n**, mã Java viết `"\\n"`.

### Lớp 2: Regex tìm kiếm

Regex dấu chấm nguyên văn cần `\.`, tức Java string literal `"\\."`. Regex khớp một backslash thật cần hai backslash trong regex, tức bốn backslash trong Java string literal `"\\\\"`. Với target từ người dùng, `Pattern.quote` giúp khỏi phải tự tính các lớp escape đó.

### Lớp 3: Cú pháp replacement

`$1` là tham chiếu tới nhóm bắt số 1 nếu chủ động dùng cú pháp này. Dấu `\` trong replacement có thể được parser dùng để escape ký tự tiếp theo. Bởi vậy, replacement từ dữ liệu như `"$1\\new"` cần `Matcher.quoteReplacement` nếu phải giữ nguyên mọi ký tự. **Quote regex không đồng thời quote replacement.**

Khi debug, so sánh toàn chuỗi bằng `equals`. Nếu cần phân tích sâu, có thể in `length` và từng code point; nhìn văn bản log đôi khi không phân biệt được một dấu backslash bị nuốt hoặc một newline thực.

## 7. Các lỗi phổ biến

1. **Chỉ quote target:** `Pattern.quote` xử lý regex nhưng replacement chứa `$` hoặc `\` vẫn có thể bị diễn giải hay gây lỗi.
2. **Chỉ quote replacement:** regex chứa `.` hoặc `|` vẫn có thể khớp vị trí khác dự kiến.
3. **Quote biểu thức khi thực sự muốn regex:** `Pattern.quote("(\\d+)")` tìm đúng các ký tự của biểu thức, chứ không tìm chữ số. Cần thống nhất target là **regex có chủ ý** hay **văn bản nguyên văn**.
4. **Dùng `replaceAll` cho thay thế hoàn toàn nguyên văn:** cân nhắc `String.replace`, vừa rõ ý định vừa tránh lỗi cú pháp.
5. **Catch mọi exception rồi đánh dấu test PASS:** `PatternSyntaxException` báo regex sai; replacement sai cũng có thể gây `IllegalArgumentException` hoặc `IndexOutOfBoundsException`. Phải kiểm tra đúng ngoại lệ mong đợi.
6. **Chỉ thử dữ liệu ASCII đơn giản:** chưa bao phủ newline, Unicode, backslash hoặc dấu dollar; cần kiểm thử với dữ liệu thực tế.
7. **Nhận regex tùy ý từ người dùng mà không giới hạn:** nếu tính năng cho phép regex tự do, cần thông báo lỗi cú pháp và kiểm soát tài nguyên do một số regex có thể chạy rất lâu. Nếu chỉ cần tìm văn bản thuần, quote target.

## 8. Quy trình áp dụng

Khi xây tính năng tìm và thay thế trong một trình soạn thảo hay công cụ xử lý chuỗi:

1. Xác định target là **regex** hay **dữ liệu nguyên văn**, và replacement là **template nhóm bắt** hay **dữ liệu nguyên văn**.
2. Chọn API thích hợp: `String.replace` cho nguyên văn; `Pattern.quote` kết hợp `Matcher.quoteReplacement` nếu cần `replaceAll` với dữ liệu nguyên văn; regex thực khi cần nhóm bắt.
3. Xây bộ test bao gồm metacharacters ở cả hai phía, nhiều lần khớp, không khớp, lỗi cú pháp, chuỗi rỗng, Unicode và newline.
4. Kiểm tra **nội dung chính xác** và **ngoại lệ chính xác**, thay vì chỉ xác nhận chương trình không bị crash.

**Ghi nhớ:** ký tự đặc biệt chỉ có ý nghĩa trong **ngữ cảnh cú pháp cụ thể**. `.` quan trọng với regex tìm kiếm, `$1` quan trọng với replacement, còn `\` phải đi qua lớp escape của Java. Kiểm thử tốt phải chứng minh cả ba lớp được xử lý đúng, đồng thời giữ nguyên dữ liệu người dùng muốn thay thế.


</details>

- [Quay lại đầu trang](#back-to-top)
