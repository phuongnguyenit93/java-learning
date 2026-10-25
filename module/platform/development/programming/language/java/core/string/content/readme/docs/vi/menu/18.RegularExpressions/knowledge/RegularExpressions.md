<a id="back-to-top"></a>

# Biểu thức chính quy

## Menu
- [String.matches kiểm tra toàn bộ chuỗi](#regular-expressions-001)
- [Pattern và Matcher cho nhiều lần xử lý](#regular-expressions-002)
- [Matcher.matches, find và lookingAt](#regular-expressions-003)
- [Lớp ký tự, lượng từ, neo và nhóm bắt](#regular-expressions-004)
- [Hai lớp escape: literal Java và cú pháp regex](#regular-expressions-005)
- [Pattern.quote cho dữ liệu được tìm nguyên văn](#regular-expressions-006)
- [Cờ CASE_INSENSITIVE, UNICODE_CASE và UNICODE_CHARACTER_CLASS](#regular-expressions-007)
- [Ký tự Unicode, ranh giới từ và cụm ký tự trong regex](#regular-expressions-008)
- [Lựa chọn mẫu tránh khớp quá rộng hoặc quay lui quá nhiều](#regular-expressions-009)

## <a id="regular-expressions-001">String.matches kiểm tra toàn bộ chuỗi</a>

<details>
<summary>Xem chi tiết</summary>

# String.matches(): kiểm tra toàn bộ chuỗi bằng biểu thức chính quy

## Mục tiêu

Sau bài này, bạn có thể giải thích vì sao `String.matches(regex)` chỉ trả về `true` khi **toàn bộ** giá trị `String` khớp với regex; viết regex trong chuỗi ký tự Java đúng cách; phân biệt kiểm tra toàn chuỗi với tìm đoạn khớp; và chọn `String.matches` hay `Pattern`/`Matcher` cho từng tình huống.

## 1. Nguyên lý: khớp toàn bộ, không tìm chuỗi con

Phương thức có chữ ký:

~~~java
public boolean matches(String regex)
~~~

`regex` là biểu thức chính quy (regular expression); kết quả là `boolean`. `text.matches(regex)` có cùng kết quả với `Pattern.matches(regex, text)` của `java.util.regex.Pattern`.

Điều kiện thành công là **regex phải bao phủ toàn bộ chuỗi đầu vào**, từ ký tự đầu đến ký tự cuối. Ví dụ:

| Chuỗi | Regex Java | Kết quả | Lý do |
| --- | --- | --- | --- |
| `"abc123"` | `"[a-z]+\\d+"` | `true` | Chữ thường theo sau bởi một hoặc nhiều chữ số, không thừa ký tự |
| `"abc123!"` | `"[a-z]+\\d+"` | `false` | Dấu `!` không thuộc mẫu |
| `"12345"` | `"\\d+"` | `true` | Cả chuỗi là chữ số |
| `"ID-42"` | `"\\d+"` | `false` | Có tiền tố `ID-` |
| `"abcXYZdef"` | `"XYZ"` | `false` | `XYZ` chỉ xuất hiện ở giữa |
| `"abcXYZdef"` | `".*XYZ.*"` | `true` | Phần trước và sau `XYZ` được regex cho phép |

Có thể hình dung `matches` kiểm tra **cả chuỗi có đúng dạng này không?**, còn `Matcher.find()` kiểm tra **có chỗ nào trong chuỗi khớp không?**. Không cần thêm `^` và `$` chỉ để buộc `String.matches` khớp toàn bộ: chính API đã yêu cầu điều đó. Tuy nhiên `^`/`$` là các anchor có ngữ nghĩa riêng trong regex (đặc biệt khi dùng cờ nhiều dòng), nên không nên xem việc thêm anchor là phép biến đổi luôn tương đương trong mọi trường hợp.

## 2. Ví dụ Java 17+ chạy độc lập

Lưu vào `StringMatchesDemo.java`:

~~~java
public class StringMatchesDemo {
    public static void main(String[] args) {
        System.out.println("abc123".matches("[a-z]+\\d+"));
        System.out.println("abc123!".matches("[a-z]+\\d+"));
        System.out.println("123".matches("\\d+"));
        System.out.println("ID-42".matches("ID-\\d{2}"));
        System.out.println("42".matches("\\d"));
        System.out.println("42".matches("\\d+"));

        System.out.println("".matches("a*"));
        System.out.println("".matches("a+"));

        System.out.println("abcXYZdef".matches("XYZ"));
        System.out.println("abcXYZdef".matches(".*XYZ.*"));

        System.out.println("a\nb".matches("a.b"));
        System.out.println("a\nb".matches("(?s)a.b"));
    }
}
~~~

Chạy bằng terminal tại thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 StringMatchesDemo.java
java StringMatchesDemo
~~~

Kết quả chuẩn (theo đúng thứ tự các lệnh `println`):

~~~text
true
false
true
true
false
true
true
false
false
true
false
true
~~~

Giải thích các trường hợp dễ nhầm:

- `"42".matches("\\d")` trả về `false` vì `\d` khớp **một** chữ số, trong khi chuỗi có hai; `\d+` cho phép một hoặc nhiều chữ số.
- `"".matches("a*")` là `true` vì dấu `*` cho phép **0 lần** xuất hiện; `a+` yêu cầu ít nhất một `a`.
- `"abcXYZdef".matches("XYZ")` là `false` vì phần `abc` và `def` chưa được mẫu bao phủ.
- Theo mặc định dấu chấm `.` không khớp ký tự xuống dòng trong ví dụ; `(?s)` bật chế độ DOTALL, nên `"a\nb"` khớp `(?s)a.b`.

## 3. Hai tầng cú pháp: Java String và regex

Regex được truyền dưới dạng **chuỗi Java**, nên dấu gạch chéo ngược `\` phải qua hai tầng diễn giải: Java xử lý chuỗi trước, sau đó bộ máy regex mới đọc mẫu. Chẳng hạn để regex nhìn thấy `\d+`, mã nguồn Java phải viết `"\\d+"`.

| Muốn kiểm tra | Mẫu regex có ý nghĩa | Viết trong mã Java |
| --- | --- | --- |
| Một hoặc nhiều chữ số | `\d+` | `"\\d+"` |
| Chính xác ba chữ số | `\d{3}` | `"\\d{3}"` |
| Một dấu chấm thật | `\.` | `"\\."` |
| Một khoảng trắng regex | `\s` | `"\\s"` |
| Mã gồm 2 chữ in hoa, dấu gạch ngang và 3 số | `[A-Z]{2}-\d{3}` | `"[A-Z]{2}-\\d{3}"` |

Một số thành phần thường dùng:

- `[abc]`: một ký tự thuộc tập `a`, `b`, `c`; `[A-Z]`: một chữ cái Latin in hoa trong khoảng chỉ định.
- `\d`, `\w`, `\s`: lớp ký tự dựng sẵn; ngữ nghĩa Unicode có thể chịu tác động của cờ regex.
- `?`: 0 hoặc 1 lần; `*`: 0 hoặc nhiều lần; `+`: 1 hoặc nhiều lần; `{n}`: đúng n lần; `{m,n}`: từ m đến n lần.
- `(...)`: nhóm; `|`: lựa chọn; `.`: ký tự bất kỳ trừ một số dấu kết thúc dòng nếu chưa bật DOTALL.
- `[0-9]` kiểm tra chữ số ASCII rõ ràng; `\d` mặc định dùng lớp chữ số regex của Java và có thể mở rộng khi bật `UNICODE_CHARACTER_CLASS`/`(?U)`.

Ví dụ, `"v1x2".matches("v1.2")` là `true` vì dấu `.` đại diện cho một ký tự bất kỳ (ở đây `x`). Muốn so sánh với dấu chấm thật, dùng `"v1\\.2"`. Nếu chuỗi cần so khớp đến từ người dùng và phải được hiểu theo nghĩa **nguyên văn**, dùng `Pattern.quote(text)` thay vì đưa thẳng vào regex.

## 4. So sánh matches, find, lookingAt và contains

`String.matches` và `Matcher.matches()` đều yêu cầu khớp toàn bộ chuỗi (hoặc toàn bộ *region* được cấu hình trong `Matcher`). `Matcher.lookingAt()` kiểm tra tiền tố, còn `Matcher.find()` quét tìm một đoạn khớp. `String.contains` chỉ tìm đoạn ký tự **nguyên văn**, không hiểu cú pháp regex.

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatchVsFindDemo {
    public static void main(String[] args) {
        String text = "Order 42";
        Pattern digits = Pattern.compile("\\d+");

        System.out.println(digits.matcher(text).matches());
        System.out.println(digits.matcher(text).lookingAt());

        Matcher matcher = digits.matcher(text);
        System.out.println(matcher.find());
        System.out.println(matcher.group());

        System.out.println(text.contains("42"));
        System.out.println(text.matches(".*\\d+"));
    }
}
~~~

Lưu là `MatchVsFindDemo.java`, biên dịch bằng `javac -encoding UTF-8 MatchVsFindDemo.java` và chạy `java MatchVsFindDemo`. Kết quả:

~~~text
false
false
true
42
true
true
~~~

`matches()` thất bại vì `"Order "` còn dư; `lookingAt()` thất bại vì đầu chuỗi không phải chữ số; `find()` tìm được `42` và `group()` trả về đoạn đã khớp. Ví dụ tạo `Matcher` riêng cho từng kiểu kiểm tra để không phụ thuộc trạng thái của những lần match trước.

| Nhu cầu | API phù hợp |
| --- | --- |
| Kiểm tra toàn bộ giá trị có đúng định dạng regex | `value.matches(regex)` hoặc `pattern.matcher(value).matches()` |
| Tìm một đoạn phù hợp regex trong văn bản | `pattern.matcher(value).find()` |
| Kiểm tra phần đầu chuỗi theo regex | `pattern.matcher(value).lookingAt()` |
| Tìm sự hiện diện của đoạn ký tự cố định | `value.contains(literal)` |
| So sánh toàn bộ chuỗi với một giá trị cố định | `value.equals(literal)` |

## 5. Áp dụng: xác thực định dạng dữ liệu

Giả sử một mã có dạng **hai chữ cái ASCII in hoa, một dấu `-`, chính xác ba chữ số ASCII**. Regex phù hợp là `[A-Z]{2}-[0-9]{3}`. Dùng `matches` để ngăn phần dư ở đầu/cuối vô tình được chấp nhận.

~~~java
import java.util.regex.Pattern;

public class CodeValidationDemo {
    private static final Pattern CODE = Pattern.compile("[A-Z]{2}-[0-9]{3}");

    public static boolean isValidCode(String value) {
        return value != null && CODE.matcher(value).matches();
    }

    public static void main(String[] args) {
        String[] candidates = {"AB-123", "AB-1234", "ab-123", " AB-123", "XY-000"};
        for (String candidate : candidates) {
            System.out.println(candidate + " -> " + isValidCode(candidate));
        }
        System.out.println("null -> " + isValidCode(null));
    }
}
~~~

Lưu là `CodeValidationDemo.java` rồi chạy:

~~~bash
javac -encoding UTF-8 CodeValidationDemo.java
java CodeValidationDemo
~~~

Kết quả:

~~~text
AB-123 -> true
AB-1234 -> false
ab-123 -> false
 AB-123 -> false
XY-000 -> true
null -> false
~~~

Ở ví dụ này `Pattern.compile(...)` được gọi một lần rồi dùng lại: hợp lý khi kiểm tra cùng một regex nhiều lần. `String.matches(regex)` tiện cho kiểm tra ngắn, ít lần; mỗi lời gọi thực hiện thao tác tương đương `Pattern.matches(regex, value)`, nên tránh biên dịch lại biểu thức không cần thiết trong vòng lặp lớn.

Đây là **kiểm tra hình thức**, chưa khẳng định một mã có tồn tại trong hệ thống. Tương tự, một regex cho email hoặc URL hiếm khi thay thế được mọi quy tắc nghiệp vụ hay bước xác thực thực tế.

## 6. Lỗi thường gặp và cách xử lý

**1. Nhầm khớp toàn chuỗi với tìm chuỗi con.** Nếu muốn tìm số trong `"Order 42"`, dùng `find()`. Nếu phải xác thực định dạng gồm cả tiền tố và số, viết mẫu phủ cả hai, ví dụ `"Order \\d+"`.

**2. Quên escape dấu gạch chéo trong Java.** Mã `"\\d+"` tạo regex `\d+`. Viết `"\d+"` trực tiếp trong chuỗi Java là lỗi biên dịch (*illegal escape character*). Muốn dấu chấm thật phải viết `"\\."`, không phải `"."`.

**3. Tưởng `.` khớp mọi thứ kể cả newline.** Mặc định dấu chấm không khớp dấu kết thúc dòng. Dùng `(?s)` khi chủ đích muốn chế độ DOTALL; hoặc chọn tập ký tự chặt chẽ hơn nếu đang xác thực dữ liệu.

**4. Bỏ qua ký tự trắng ở đầu/cuối.** `" AB-123 "` không khớp `[A-Z]{2}-[0-9]{3}`. Chỉ gọi `strip()` trước `matches` nếu quy tắc nhập liệu cho phép bỏ khoảng trắng; nếu khoảng trắng là dữ liệu không hợp lệ, phải giữ nguyên để từ chối.

**5. Không xét `null` hoặc regex sai.** Gọi `matches` trên biến `String` đang `null` gây `NullPointerException`. Regex sai cú pháp như `"[abc"` gây `PatternSyntaxException`. Nếu regex lấy từ bên ngoài, cần xử lý lỗi cú pháp và giới hạn độ phức tạp trước khi chạy.

**6. Chấp nhận input theo regex quá rộng.** Mẫu `".*"` khớp cả chuỗi rỗng; `".+"` không khớp chuỗi rỗng, nhưng vẫn quá rộng cho mã định danh. Viết lớp ký tự và độ dài thật sự cần (ví dụ `[A-Z]{2}-[0-9]{3}`).

**7. Xem regex như bộ xác thực mọi ngôn ngữ/ký tự.** `[A-Z]` chỉ nói tới các chữ từ A đến Z; `(?i)` có thể bật so khớp không phân biệt hoa thường theo quy tắc regex, nhưng yêu cầu Unicode/ngôn ngữ khác có thể cần cách xây dựng lớp ký tự và cờ phù hợp. Không giả định một mẫu ASCII bao phủ tiếng Việt hay toàn bộ Unicode.

**8. Đưa regex không tin cậy vào xử lý dữ liệu dài.** Một số biểu thức có nhánh lặp lồng nhau gây thời gian chạy rất lớn (*catastrophic backtracking*) khi gặp chuỗi gần khớp. Ưu tiên regex đơn giản, có giới hạn; cân nhắc giới hạn đầu vào, hiệu năng và giải pháp parser chuyên biệt nếu cần.

## 7. Tự kiểm tra

Không cần chạy code, hãy dự đoán:

1. `"A12".matches("[A-Z]\\d{2}")`
2. `"A123".matches("[A-Z]\\d{2}")`
3. `"".matches("\\d*")`
4. `"x42y".matches("\\d+")`
5. `"x42y".matches(".*\\d+.*")`

**Đáp án:** (1) `true`; (2) `false` vì thừa một chữ số; (3) `true` vì `*` cho phép 0 lần; (4) `false` vì có `x` và `y`; (5) `true` vì `.*` ở hai phía bao phủ ký tự không phải chữ số.

Khi tự thiết kế mẫu để xác thực, luôn thử ít nhất bốn nhóm giá trị: **hợp lệ**, **thừa tiền tố/hậu tố**, **thiếu hoặc sai ký tự**, **rỗng/null**. Đây là cách nhanh để bắt lỗi nhầm lẫn giữa kiểm tra toàn bộ và tìm một phần của chuỗi.

## Tham khảo chính thức

- [Java SE 17 — String.matches(String regex)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#matches(java.lang.String))
- [Java SE 17 — Pattern](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html)
- [Java SE 17 — Matcher: matches(), lookingAt(), find()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Matcher.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-002">Pattern và Matcher cho nhiều lần xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Pattern và Matcher cho nhiều lần xử lý trong Java

Trong Java, lớp **java.util.regex.Pattern** biểu diễn một biểu thức chính quy đã được biên dịch; lớp **java.util.regex.Matcher** dùng Pattern đó để kiểm tra, tìm kiếm, trích xuất hoặc thay thế nội dung trên một chuỗi đầu vào. Khi cần xử lý nhiều chuỗi hoặc tìm nhiều kết quả trong một chuỗi, hãy **biên dịch Pattern một lần và tạo Matcher phù hợp cho từng lượt xử lý**. Đây là cách sử dụng tự nhiên, dễ kiểm soát trạng thái và tránh biên dịch lại cùng một regex không cần thiết.

Bài này tập trung vào xử lý lặp: một regex dùng với nhiều đầu vào, một đầu vào chứa nhiều kết quả, tái sử dụng Matcher theo tuần tự, kiểm soát trạng thái và những lỗi thường gặp.

## 1. Phân biệt vai trò của Pattern và Matcher

| Thành phần | Chức năng | Trạng thái và khả năng chia sẻ |
| --- | --- | --- |
| Pattern | Lưu regex đã biên dịch và các cờ (flags). Tạo Matcher bằng matcher(input). | Bất biến (immutable), an toàn để nhiều luồng cùng dùng. |
| Matcher | Thực thi regex trên CharSequence; giữ đầu vào, vùng tìm kiếm, vị trí khớp vừa tìm, nhóm bắt, vị trí cho lần tìm tiếp theo. | Có trạng thái thay đổi (mutable), **không an toàn để dùng chung đồng thời giữa các luồng**. |
| PatternSyntaxException | Báo regex không hợp lệ tại thời điểm biên dịch. | Cần xử lý khi regex được nhập hoặc cấu hình động. |

Ví dụ minh họa ý tưởng (đoạn mã, chưa phải chương trình chạy độc lập):

~~~java
Pattern pattern = Pattern.compile("\\d+");   // Biên dịch một lần
Matcher first = pattern.matcher("Mã 17");    // Matcher cho đầu vào thứ nhất
Matcher second = pattern.matcher("Mã 42");   // Matcher khác cho đầu vào thứ hai

System.out.println(first.find());            // true
System.out.println(second.find());           // true
~~~

Hai Matcher độc lập có thể tiến qua đầu vào của mình mà không làm thay đổi trạng thái của nhau. Nếu cùng một regex được dùng thường xuyên, có thể lưu Pattern dưới dạng hằng số static final, trong một service hoặc trong một đối tượng được tái sử dụng.

### Vì sao không biên dịch trong mỗi vòng lặp?

Cách sau biên dịch lại regex mỗi lần duyệt phần tử:

~~~java
for (String line : lines) {
    Matcher matcher = Pattern.compile("\\bID-\\d{3}\\b").matcher(line);
    // ...
}
~~~

Nên chuyển phần biên dịch ra ngoài:

~~~java
Pattern idPattern = Pattern.compile("\\bID-\\d{3}\\b");
for (String line : lines) {
    Matcher matcher = idPattern.matcher(line);
    // ...
}
~~~

Tương tự, String.matches(regex) và Pattern.matches(regex, input) phù hợp khi kiểm tra nhanh một lần, nhưng chúng biên dịch regex cho mỗi lần gọi. Trong xử lý lặp với cùng regex, Pattern đã biên dịch cùng matcher(input).matches() tránh việc đó. Lợi ích thực tế phụ thuộc độ phức tạp regex và tần suất gọi; tối ưu quan trọng nhất vẫn là tính đúng của regex.

## 2. Một Pattern xử lý nhiều dòng, mỗi dòng có nhiều kết quả

Yêu cầu: tìm tất cả mã đúng định dạng ID- theo sau bởi **đúng ba chữ số**, xuất mã, phần số và vị trí. Từ ranh giới \b giúp không nhận mã dính sát ký tự thuộc từ; ký hiệu (?<number>...) đặt tên cho nhóm bắt số.

Tệp **RegexReuseDemo.java** (Java 17+):

~~~java
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexReuseDemo {
    private static final Pattern ID_PATTERN =
            Pattern.compile("\\bID-(?<number>\\d{3})\\b");

    public static void main(String[] args) {
        List<String> lines = List.of(
                "Đơn ID-123, ID-045; mã ID-7 không hợp lệ.",
                "ID-999 kết thúc; xid-111 không khớp."
        );

        for (String line : lines) {
            Matcher matcher = ID_PATTERN.matcher(line);
            while (matcher.find()) {
                System.out.printf(
                        "%s | mã=%s | vị trí=[%d,%d)%n",
                        matcher.group(),
                        matcher.group("number"),
                        matcher.start(),
                        matcher.end()
                );
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 RegexReuseDemo.java
java RegexReuseDemo
~~~

Kết quả:

~~~text
ID-123 | mã=123 | vị trí=[4,10)
ID-045 | mã=045 | vị trí=[12,18)
ID-999 | mã=999 | vị trí=[0,6)
~~~

Giải thích:

1. **ID_PATTERN** chỉ biên dịch một lần khi lớp được khởi tạo. Mỗi dòng nhận một Matcher mới bằng matcher(line).
2. **find()** tìm kết quả tiếp theo. Gọi liên tiếp trong vòng while duyệt các kết quả không chồng lấn từ trái sang phải; khi hết kết quả, find() trả về false.
3. **group()** (hay group(0)) là toàn bộ đoạn khớp; **group("number")** là nội dung của nhóm được đặt tên.
4. **start()** là vị trí bắt đầu, **end()** là vị trí *ngay sau* ký tự cuối. Vì vậy khoảng vị trí là [start, end). Chỉ số tính theo đơn vị char/UTF-16 của Java, không nhất thiết tương đương số ký tự người đọc thấy nếu có ký tự Unicode bổ sung.
5. ID-7 không đủ ba chữ số. xid-111 không khớp vì chữ ID viết hoa trong regex và không nằm tại ranh giới từ thích hợp.

Dấu ngoặc nhọn \d{3} quy định số lượng, còn \b định nghĩa ranh giới từ; nếu quy tắc nghiệp vụ yêu cầu một mã đứng độc lập theo cách khác (chẳng hạn dấu gạch dưới, ký tự Unicode hoặc quy tắc mã riêng), cần thiết kế ranh giới đúng yêu cầu. Regex trên minh họa một định dạng cụ thể, không phải bộ kiểm tra mọi loại mã định danh.

## 3. Tái sử dụng chính đối tượng Matcher bằng reset()

Trong một luồng xử lý tuần tự, khi muốn giảm số Matcher được tạo ra, có thể giữ một Matcher và gọi **reset(newInput)** trước mỗi đầu vào. Matcher sẽ quên kết quả tìm kiếm trước đó và bắt đầu lại trên chuỗi mới.

Tệp **MatcherResetDemo.java**:

~~~java
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherResetDemo {
    public static void main(String[] args) {
        Pattern numberPattern = Pattern.compile("\\b\\d+\\b");
        Matcher matcher = numberPattern.matcher("");

        for (String line : List.of("A: 17, B: 22", "C: 8")) {
            matcher.reset(line);
            while (matcher.find()) {
                System.out.printf("%s -> %s%n", line, matcher.group());
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 MatcherResetDemo.java
java MatcherResetDemo
~~~

Kết quả:

~~~text
A: 17, B: 22 -> 17
A: 17, B: 22 -> 22
C: 8 -> 8
~~~

Có hai kiểu reset thường gặp:

- **reset()**: dùng lại đầu vào hiện tại, bắt đầu tìm lại từ đầu.
- **reset(CharSequence input)**: đổi sang đầu vào mới, đặt lại trạng thái tìm kiếm và vùng tìm kiếm mặc định về toàn bộ đầu vào mới.

Sau reset, nhóm bắt và vị trí khớp trước đó không còn hợp lệ. Chỉ gọi group(), start(), end() sau khi matches(), lookingAt() hoặc find() đã trả về true. Nếu chưa có kết quả khớp hợp lệ, các phương thức này ném IllegalStateException.

**Lựa chọn mặc định:** dùng Pattern chung + Matcher mới cho từng đầu vào vì mã rõ ràng, ít lỗi trạng thái. Chỉ tái sử dụng chính Matcher khi xử lý tuần tự, kiểm soát vòng đời của nó và có lý do đo đạc được. Không chia sẻ một Matcher tái sử dụng cho các tác vụ chạy song song.

## 4. Chọn phương thức khi tái sử dụng Pattern

Một `Pattern` đã biên dịch có thể dùng cho cả ba cách kiểm tra; **phương thức gọi trên Matcher** quyết định câu hỏi của từng lượt xử lý:

| Phương thức | Với regex `\d+` và input `"123abc456"` | Dùng khi |
| --- | --- | --- |
| `matches()` | `false`: còn phần `abc456` ngoài mẫu | Xác thực toàn bộ vùng dữ liệu |
| `lookingAt()` | `true`: tiền tố `"123"` khớp | Nhận diện token ở đầu vùng |
| `find()` | `true`: tìm `"123"`, lần kế tiếp tìm `"456"` | Duyệt nhiều đoạn khớp liên tiếp |

Khi kiểm tra các chế độ **độc lập**, hãy gọi `digits.matcher(input)` để tạo Matcher riêng cho mỗi phép thử. Khi cần thu mọi kết quả của `find()`, giữ **một Matcher xuyên suốt vòng lặp** để vị trí tìm kiếm tiến đúng; chỉ `reset()` nếu cố ý bắt đầu lại. Phân biệt phạm vi của ba phương thức là nền tảng, còn trọng tâm của bài này là quản lý vòng đời `Pattern` và trạng thái `Matcher` trong xử lý nhiều lần.

## 5. Làm việc với các nhóm và vị trí khi lặp find()

- group(0) hoặc group(): toàn bộ đoạn khớp.
- group(1), group(2), ...: các nhóm bắt theo thứ tự dấu ngoặc tròn mở. group("name") truy cập nhóm đặt tên.
- start(), end() là vị trí của toàn bộ đoạn khớp; start(1), end(1) là vị trí nhóm số 1; tương tự có phiên bản nhận tên nhóm.
- Một nhóm bắt là tùy chọn có thể không tham gia vào lần khớp: group tương ứng trả về null, còn start/end của nhóm trả về -1. Điều này khác với nhóm tham gia nhưng khớp chuỗi rỗng.
- Dùng (?:...) khi chỉ cần gom biểu thức mà **không muốn tạo nhóm bắt**; điều này giúp chỉ số nhóm ổn định và dễ đọc hơn.

Ví dụ ý tưởng: regex (ID)-(\\d{3}) có group(1) là "ID" và group(2) là ba chữ số; regex (?:ID)-(\\d{3}) chỉ có một nhóm bắt số. Với dữ liệu trích xuất theo nhiều nhóm, kiểm tra sự hiện diện của nhóm tùy chọn trước khi chuyển đổi kiểu hoặc thao tác chuỗi.

Cẩn thận với biểu thức có thể khớp **chuỗi rỗng** (chẳng hạn .* hoặc các lượng từ tùy chọn). find() xử lý việc tiến tới vị trí tiếp theo cho các lần khớp độ dài bằng không, nhưng đoạn mã tự cập nhật chỉ số quét bằng end() có thể mắc vòng lặp nếu không xử lý trường hợp start() == end(). Đừng mặc định mọi lần find() đều tiêu thụ ít nhất một ký tự.

## 6. Các cờ biên dịch và cách thoát ký tự

Có thể khai báo cờ ở thời điểm biên dịch:

~~~java
Pattern keyword = Pattern.compile(
        "\\bjava\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
);
~~~

- **CASE_INSENSITIVE**: khớp không phân biệt hoa/thường; kết hợp UNICODE_CASE khi cần đối chiếu chữ cái Unicode theo quy tắc case-insensitive của Java.
- **MULTILINE**: thay đổi cách ^ và $ nhận diện đầu/cuối dòng trong một chuỗi nhiều dòng; không biến matches() thành thao tác tìm từng dòng.
- **DOTALL**: dấu chấm . có thể khớp cả ký tự xuống dòng. Không liên quan trực tiếp đến việc tái sử dụng Matcher.

Trong **chuỗi Java**, một dấu gạch chéo ngược của cú pháp regex thường phải viết thành hai dấu: regex \d được truyền vào Pattern từ chuỗi Java "\\d"; regex \b từ "\\b". Nếu viết "\b" đơn lẻ bên trong chuỗi Java, đó là ký tự backspace của Java, không phải biểu thức ranh giới từ của regex.

Nếu đầu vào của người dùng phải được tìm **theo nghĩa văn bản nguyên trạng**, dùng Pattern.quote(text) thay vì ghép trực tiếp vào regex; nếu nội dung thay thế có ký tự $ hoặc \, dùng Matcher.quoteReplacement(replacement) để giữ nguyên nghĩa. Đoạn mã minh họa sau giả định đã có ba biến kiểu String:

~~~java
Pattern literal = Pattern.compile(Pattern.quote(searchText));
Matcher matcher = literal.matcher(sourceText);
String result = matcher.replaceAll(Matcher.quoteReplacement(replacementText));
~~~

Nếu không quote, ký tự đặc biệt trong searchText có thể trở thành toán tử regex; ký tự $ hoặc dấu \ trong replacementText có thể được hiểu là tham chiếu nhóm hoặc cú pháp thoát. Khi cần thay thế có chủ đích theo nhóm, hãy dùng đúng cú pháp tham chiếu nhóm của Matcher thay vì quoteReplacement.

## 7. Xử lý nhiều lần trong chương trình và ứng dụng đa luồng

Với API/service nhận nhiều request hoặc worker chạy song song, Pattern có thể dùng chung an toàn:

~~~java
private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]+");

static boolean containsToken(String input) {
    return TOKEN.matcher(input).find();
}
~~~

Mỗi lần gọi containsToken tạo Matcher cục bộ. Vì Matcher giữ vị trí duyệt và tham chiếu đến chuỗi đang xử lý, không lưu một Matcher duy nhất vào biến static hoặc chia sẻ nó giữa các request. Đồng bộ hóa có thể ngăn truy cập đồng thời nhưng thường tạo ra thiết kế khó hiểu và không cần thiết; Pattern bất biến + Matcher cục bộ là đủ cho đa số trường hợp.

Khi regex được người dùng cung cấp, nên xác thực và kiểm soát cấu hình: Pattern.compile có thể ném PatternSyntaxException; một số biểu thức bị backtracking mạnh có thể tốn nhiều CPU trên dữ liệu dài hoặc cố ý gây khó. Việc **cache Pattern** chỉ tránh biên dịch lặp, **không loại bỏ** rủi ro regex xử lý chậm. Với regex động, cần cân nhắc số mẫu cache, giới hạn kích thước dữ liệu, thử nghiệm tình huống xấu và thiết kế biểu thức tránh các nhánh lặp mơ hồ.

## 8. Những lỗi dễ gặp và cách sửa

| Tình huống | Nguyên nhân | Cách làm phù hợp |
| --- | --- | --- |
| Chỉ thu được kết quả đầu tiên | Dùng một lần if (matcher.find()) rồi dừng. | Dùng while (matcher.find()) khi cần toàn bộ kết quả. |
| Không tìm lại được từ đầu | Matcher đã tiến đến cuối sau nhiều lần find(). | Gọi reset() trước một lượt quét mới hoặc tạo matcher(input) khác. |
| Kết quả dòng mới sai / không có | Tái sử dụng Matcher mà quên reset(newInput). | Reset đúng đầu vào trước mỗi lượt. |
| group()/start()/end() ném IllegalStateException | Chưa có lần khớp thành công đang hợp lệ, hoặc đã reset. | Chỉ truy cập sau kết quả true và trước khi làm mất trạng thái đó. |
| Xác thực toàn chuỗi nhưng vẫn chấp nhận đoạn con | Dùng find() thay vì matches(). | Dùng matches() cho toàn bộ đầu vào. |
| Chậm khi lặp hàng nghìn dòng | Gọi Pattern.compile hoặc String.matches lặp lại với cùng regex; hoặc regex gây backtracking nặng. | Biên dịch Pattern một lần, đo hiệu năng và điều chỉnh biểu thức nếu cần. |
| Lỗi khi chạy song song | Nhiều luồng dùng chung một Matcher mutable. | Chia sẻ Pattern, tạo Matcher riêng theo mỗi tác vụ. |
| Tìm chuỗi ký tự literal sai | Nội dung nhập chứa ., +, [, (, ... và bị hiểu là regex. | Dùng Pattern.quote() cho mẫu literal. |
| Thay thế có $ hoặc \ bị lỗi | Chuỗi thay thế bị hiểu thành cú pháp nhóm/escape. | Dùng Matcher.quoteReplacement() cho phần thay thế literal. |

## 9. Cách chọn thiết kế cho từng nhu cầu

- **Cùng regex, nhiều chuỗi:** biên dịch Pattern một lần; với mỗi chuỗi, gọi pattern.matcher(input).
- **Một chuỗi, nhiều đoạn khớp:** một Matcher với while (matcher.find()); đọc group/start/end ngay trong mỗi lần lặp.
- **Một Matcher quét lại hoặc đổi đầu vào theo thứ tự:** gọi reset() hoặc reset(newInput) trước lần quét tiếp theo.
- **Chỉ xác thực một chuỗi:** gọi matcher(input).matches(); đừng nhầm với find().
- **Cùng Pattern trên nhiều luồng:** chia sẻ Pattern; Matcher thuộc về từng luồng/từng tác vụ.
- **Regex hoặc nội dung thay thế từ bên ngoài:** kiểm tra lỗi cú pháp, quote nội dung literal đúng cách và đánh giá rủi ro hiệu năng.

Điểm cần nắm vững là **Pattern giữ quy tắc; Matcher giữ trạng thái của một lượt xử lý**. Quản lý hai vòng đời này đúng giúp mã regex vừa rõ ràng vừa ổn định khi phải xử lý nhiều chuỗi và nhiều kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-003">Matcher.matches, find và lookingAt</a>

<details>
<summary>Xem chi tiết</summary>

# Matcher.matches(), find() và lookingAt() trong Java

## 1. Mục tiêu và bối cảnh

Khi xử lý **biểu thức chính quy (regular expression, regex)**, Java tách hai vai trò:

- **Pattern**: biểu thức đã được biên dịch; có thể tái sử dụng.
- **Matcher**: đối tượng giữ chuỗi đầu vào, vùng tìm kiếm, vị trí và kết quả khớp hiện tại. Tạo bằng **pattern.matcher(input)**.

Ba phương thức **Matcher.matches()**, **Matcher.lookingAt()** và **Matcher.find()** đều trả về **boolean**, nhưng câu hỏi chúng trả lời khác nhau:

- **matches()**: *Toàn bộ vùng đang xét có khớp regex không?*
- **lookingAt()**: *Regex có khớp ngay từ đầu vùng đang xét không?*
- **find()**: *Có đoạn con nào khớp regex kể từ vị trí tìm kiếm hiện tại không?*

“Vùng đang xét” (region) mặc định là toàn bộ chuỗi đầu vào. Matcher có thể giới hạn vùng bằng **region(start, end)**; vì thế mô tả chính xác là “toàn bộ vùng” thay vì luôn luôn “toàn bộ String”.

## 2. So sánh nhanh

| Phương thức | Bắt buộc bắt đầu tại đầu region? | Bắt buộc kết thúc tại cuối region? | Tìm nhiều lần liên tiếp? | Mục đích điển hình |
| --- | --- | --- | --- | --- |
| **matches()** | Có | Có | Không; mỗi lần kiểm tra khớp toàn vùng | Kiểm tra một giá trị có hợp lệ theo mẫu |
| **lookingAt()** | Có | Không | Không; kiểm tra tiền tố của region | Nhận diện tiền tố, token ở đầu |
| **find()** | Không | Không | Có; gọi liên tiếp để duyệt các kết quả | Trích xuất các phần khớp trong văn bản |

Ví dụ với regex **cat**:

| Chuỗi đầu vào | matches() | lookingAt() | find() |
| --- | --- | --- | --- |
| cat | true | true | true |
| cat nap | false | true | true |
| a cat | false | false | true |
| dog | false | false | false |

**Lưu ý:** cả ba chỉ biết regex, không biết ý nghĩa nghiệp vụ. Regex **cat** có thể tìm thấy trong **scatter** nếu không ràng buộc ranh giới từ; muốn khớp cả từ thì dùng **\bcat\b** (viết trong Java là **"\\bcat\\b"**).

## 3. Ví dụ Java 17+ chạy độc lập

Tạo tệp **MatcherOperationsDemo.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherOperationsDemo {
    public static void main(String[] args) {
        String input = "ID=42; ID=7";
        Pattern pattern = Pattern.compile("ID=(\\d+)");

        Matcher whole = pattern.matcher(input);
        System.out.println("matches: " + whole.matches());

        Matcher prefix = pattern.matcher(input);
        System.out.println("lookingAt: " + prefix.lookingAt());
        System.out.println("prefix id: " + prefix.group(1));

        Matcher scan = pattern.matcher(input);
        while (scan.find()) {
            System.out.printf(
                    "find: %s, id=%s, range=[%d,%d)%n",
                    scan.group(), scan.group(1), scan.start(), scan.end());
        }

        System.out.println("find after loop: " + scan.find());
        System.out.println("find after reset: " + scan.reset().find());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MatcherOperationsDemo.java
java MatcherOperationsDemo
~~~

Kết quả chuẩn:

~~~text
matches: false
lookingAt: true
prefix id: 42
find: ID=42, id=42, range=[0,5)
find: ID=7, id=7, range=[7,11)
find after loop: false
find after reset: true
~~~

Giải thích:

1. **matches() = false** vì **ID=42** chỉ khớp đoạn đầu; phần **; ID=7** còn lại khiến toàn bộ chuỗi không khớp.
2. **lookingAt() = true** vì chuỗi mở đầu bằng **ID=42**; phương thức không yêu cầu phần sau cũng khớp.
3. **find()** tìm lần thứ nhất ở đoạn **[0, 5)** và lần thứ hai ở **[7, 11)**. Chỉ số **end()** là vị trí *ngay sau* ký tự cuối cùng của phần khớp.
4. Hết các đoạn khớp, **find()** trả **false**. **reset()** đưa Matcher về trạng thái ban đầu nên tìm lại được từ đầu.

Ví dụ dùng **ba Matcher độc lập** để mỗi phép thử không bị ảnh hưởng bởi vị trí hoặc kết quả của phép thử trước. Có thể tái sử dụng **Pattern**, nhưng một Matcher có trạng thái thay đổi sau mỗi thao tác.

## 4. matches(): kiểm tra khớp toàn bộ region

~~~java
Pattern digits = Pattern.compile("\\d+");

System.out.println(digits.matcher("12345").matches());  // true
System.out.println(digits.matcher("a12345").matches()); // false
System.out.println(digits.matcher("12345a").matches()); // false
System.out.println(digits.matcher("").matches());       // false
~~~

**matches()** cố gắng làm cho regex bao phủ toàn bộ region. Vì vậy:

- Với region là toàn chuỗi, regex **\\d+** chấp nhận chuỗi chỉ gồm ít nhất một chữ số ASCII thông thường theo cách dùng regex này.
- Regex **\\d+** không cần thêm **^** và **$** để làm cho **matches()** kiểm tra toàn region.
- Nếu chỉ muốn tìm một dãy số ở đâu đó bên trong văn bản, dùng **find()** thay vì nới regex thành **.*\\d+.*** một cách máy móc.
- Đừng nhầm **Matcher.matches()** với việc “kiểm tra có một đoạn con khớp”. Nó không hoạt động như phép tìm kiếm một phần.

Hai cách viết tiện ích cũng mang ý nghĩa khớp toàn chuỗi:

~~~java
boolean a = "123".matches("\\d+");
boolean b = Pattern.matches("\\d+", "123");

System.out.println(a); // true
System.out.println(b); // true
~~~

**String.matches(regex)** và **Pattern.matches(regex, input)** phù hợp cho phép kiểm tra đơn lẻ. Khi chạy cùng regex nhiều lần, biên dịch **Pattern** một lần rồi tạo Matcher mới cho từng input để tránh biên dịch lặp không cần thiết.

## 5. lookingAt(): nhận diện một tiền tố

**lookingAt()** bắt buộc regex bắt đầu tại đầu region, nhưng không yêu cầu đi đến cuối region:

~~~java
Pattern command = Pattern.compile("GET|POST");

System.out.println(command.matcher("GET /users").lookingAt());  // true
System.out.println(command.matcher("POST /users").lookingAt()); // true
System.out.println(command.matcher("X GET /users").lookingAt());// false
~~~

Giá trị trả về cho biết **một tiền tố** khớp; nó không kiểm tra token có kết thúc đúng ranh giới hay không. Chẳng hạn regex **GET|POST** cũng khớp tiền tố trong **GETAWAY**. Nếu cần phân biệt lệnh **GET** đứng độc lập, phải bổ sung ranh giới phù hợp, ví dụ **(?:GET|POST)\b** cho token dạng từ hoặc thiết kế mẫu bao gồm dấu phân cách.

Khi thành công, **group()**, **start()**, **end()** cho biết tiền tố được khớp, để chương trình có thể tiếp tục xử lý phần văn bản phía sau.

**lookingAt()** luôn thử từ đầu region, kể cả khi Matcher từng có phép tìm kiếm khác. Tuy nhiên, để so sánh ba phương thức công bằng và dễ hiểu, hãy dùng Matcher riêng hoặc gọi **reset()** giữa các kịch bản độc lập.

## 6. find(): quét và trích xuất nhiều đoạn khớp

**find()** tìm một đoạn con khớp từ vị trí tìm kiếm hiện tại; khi thành công, lần gọi **find()** tiếp theo tiếp tục sau phần vừa khớp. Trường hợp phổ biến:

~~~java
Pattern number = Pattern.compile("\\d+");
Matcher matcher = number.matcher("A12 B345 C6");

while (matcher.find()) {
    System.out.println(matcher.group());
}
~~~

Output:

~~~text
12
345
6
~~~

**find()** thường dùng cho log, văn bản tự do, trích ID, tìm token hoặc các lần xuất hiện của một mẫu. Mỗi lần **find()** thành công, thông tin kết quả mới thay thế kết quả khớp trước đó.

### 6.1. find(int start) và reset()

Biến thể **find(int start)** bắt đầu tìm từ một chỉ số được chỉ định trong **chuỗi đầu vào** và **reset Matcher trước khi tìm**:

~~~java
Matcher m = Pattern.compile("aba").matcher("ababa");

System.out.println(m.find());    // true: [0,3)
System.out.println(m.start());   // 0
System.out.println(m.find());    // false (không quét chồng lên đoạn đã khớp)

System.out.println(m.find(2));   // true: [2,5)
System.out.println(m.start());   // 2
~~~

Phần khớp **aba** thứ hai bắt đầu ở chỉ số 2, nhưng chồng lên phần đầu. **find()** tuần tự không trả về các kết quả chồng lấn theo mặc định. **find(2)** là một cách chủ động bắt đầu một phép tìm kiếm mới từ chỉ số 2; đây không phải hành vi tự động tìm mọi vị trí chồng lấn.

**find(int start)** gọi logic reset nên không nên giả định nó giữ nguyên region tùy chỉnh hay trạng thái tìm kiếm trước đó. Nếu cần region cụ thể, thiết lập lại region sau khi reset hoặc thiết kế Matcher mới.

### 6.2. Mẫu khớp chuỗi rỗng

Một số regex có thể khớp **độ dài 0**, ví dụ **a*** hoặc ranh giới từ **\b**. Khi ấy **start() == end()**. Matcher có cơ chế tiến vị trí tìm kiếm để vòng **while (find())** không lặp mãi tại một vị trí rỗng, nhưng kết quả có thể gồm các lần khớp rỗng mà ứng dụng không muốn.

Nếu cần trích nội dung thực sự có ký tự, hãy xác định regex có cho phép độ dài 0 hay không; ví dụ **\\d+** cần ít nhất một chữ số, còn **\\d*** cho phép cả chuỗi rỗng.

## 7. Kết quả khớp, capturing group và trạng thái Matcher

Sau **một phép khớp thành công** của bất kỳ phương thức nào:

- **group()** hoặc **group(0)**: toàn bộ đoạn khớp.
- **group(1)**, **group(2)**, ...: giá trị của các *capturing group* đánh số từ 1 theo dấu ngoặc bắt nhóm trong regex.
- **start()**, **end()**: chỉ số đầu và chỉ số sau cuối của toàn bộ đoạn khớp; có biến thể **start(group)**, **end(group)**.
- Các chỉ số dùng offset theo **đơn vị UTF-16** của Java String, không nhất thiết trùng số ký tự người đọc nhìn thấy khi có emoji hoặc ký tự bổ sung Unicode.

Ví dụ nhóm bắt:

~~~java
Pattern p = Pattern.compile("([A-Z]+)-(\\d+)");
Matcher m = p.matcher("x AB-12 y");

if (m.find()) {
    System.out.println(m.group());  // AB-12
    System.out.println(m.group(1)); // AB
    System.out.println(m.group(2)); // 12
    System.out.println(m.start());  // 2
    System.out.println(m.end());    // 7
}
~~~

Nếu nhóm là **tùy chọn và không tham gia phép khớp**, **group(n)** trả **null**, còn **start(n)** và **end(n)** trả **-1**. Kết quả khác với nhóm có tham gia nhưng khớp chuỗi rỗng, khi đó **group(n)** là **""**.

**Rất quan trọng:** không được gọi **group()**, **start()**, **end()** để lấy kết quả khi chưa có phép khớp thành công hoặc ngay sau một phép khớp thất bại; Java sẽ ném **IllegalStateException**. Hãy kiểm tra boolean trước:

~~~java
Matcher m = Pattern.compile("\\d+").matcher("abc");

if (m.find()) {
    System.out.println(m.group());
} else {
    System.out.println("Không tìm thấy số");
}
~~~

Một nhóm vượt quá số nhóm bắt hợp lệ cũng gây lỗi chỉ số nhóm. Ngoài ra, dùng **(?:...)** khi chỉ cần nhóm cú pháp mà không cần lấy dữ liệu.

**Matcher không an toàn để chia sẻ đồng thời giữa nhiều thread** vì mang trạng thái mutable. **Pattern** có thể chia sẻ giữa các thread; mỗi luồng hoặc mỗi tác vụ nên tạo Matcher riêng.

## 8. region(), anchor và flag: những điểm dễ hiểu sai

### 8.1. region(start, end) thay đổi phạm vi kiểm tra

~~~java
Matcher m = Pattern.compile("\\d+")
        .matcher("xx123yy")
        .region(2, 5);

System.out.println(m.matches()); // true
System.out.println(m.start());   // 2
System.out.println(m.end());     // 5
~~~

Ở đây input gốc là **xx123yy**, nhưng region **[2,5)** chỉ chứa **123**, nên **matches()** đúng. **start()** và **end()** vẫn là offset trong input gốc.

Region cũng chi phối điểm bắt đầu của **lookingAt()** và phạm vi tìm của **find()**. Mặc định Matcher dùng **anchoring bounds = true**, cho phép **^**, **$** nhận biên region là ranh giới; **transparent bounds = false** khiến một số phép nhìn trước/nhìn sau và biên từ không nhìn xuyên qua region. Khi regex dựa vào lookaround/anchor tại biên region, có thể cần hiểu **useAnchoringBounds(...)** và **useTransparentBounds(...)**.

### 8.2. ^, $, MULTILINE và DOTALL không biến ba phương thức thành nhau

- **^** và **$** là anchor trong **regex**. **matches()** tự đòi hỏi phủ toàn region dù không ghi anchor.
- **Pattern.MULTILINE** thay đổi ý nghĩa anchor **^** và **$** để chúng còn có thể gắn với ranh giới dòng; nó **không** biến **matches()** thành tìm một dòng tùy ý trong toàn input.
- **Pattern.DOTALL** cho phép dấu **.** khớp cả ký tự xuống dòng; nếu regex **a.b** dùng với **matches()** trên **"a\nb"**, kết quả mặc định là **false** nhưng có thể là **true** khi bật DOTALL.

Ví dụ tách rõ regex, mode và phương thức:

~~~java
String lines = "one\ntwo";
Pattern p = Pattern.compile("^two$", Pattern.MULTILINE);

System.out.println(p.matcher(lines).find());    // true
System.out.println(p.matcher(lines).matches()); // false
~~~

**find()** tìm được dòng thứ hai nhờ MULTILINE cho **^two$**. **matches()** vẫn đòi hỏi toàn region từ **one** đến **two** phải khớp.

### 8.3. Dấu gạch chéo ngược trong regex Java

Java String literal và cú pháp regex đều dùng dấu **\**. Để regex engine nhận **\d+**, mã Java phải ghi **"\\d+"**. Tương tự, regex ranh giới từ **\b** phải viết trong Java là **"\\b"**; viết **"\b"** sẽ tạo ký tự backspace ở tầng Java String và làm regex có nghĩa khác.

## 9. Chọn phương thức phù hợp theo bài toán

| Bài toán | Nên dùng | Lý do |
| --- | --- | --- |
| Kiểm tra mã chỉ được gồm chữ số | **matches()** với regex **\\d+** | Không cho phép ký tự dư ở đầu/cuối |
| Kiểm tra đầu câu lệnh có token hợp lệ | **lookingAt()** với regex và ranh giới token | Chỉ yêu cầu khớp từ đầu |
| Lấy mọi số trong nội dung log | Vòng lặp **while (find())** | Quét từng đoạn con khớp |
| Xem văn bản có chứa ít nhất một email theo mẫu | **find()** | Chỉ cần một đoạn con phù hợp |
| Kiểm tra toàn bộ giá trị nhập theo một định dạng | **matches()** | Mọi ký tự trong region phải được regex bao phủ |

Đối với kiểm tra dữ liệu nghiệp vụ, regex mới chỉ xác minh **cú pháp**. Ví dụ ngày tháng khớp dạng **\\d{4}-\\d{2}-\\d{2}** vẫn có thể là ngày không tồn tại; cần thêm kiểm tra giá trị bằng API phù hợp.

## 10. Sai lầm thường gặp và cách tự kiểm tra

1. **Dùng matches() rồi ngạc nhiên khi false.** Xem có ký tự thừa hoặc xuống dòng không; nếu muốn tìm đoạn con hãy thử **find()**, nếu muốn tiền tố hãy thử **lookingAt()**.
2. **Dùng lookingAt() để xác thực trọn input.** Nó có thể trả true cho chuỗi có phần đuôi không hợp lệ. Dùng **matches()** nếu cần hợp lệ toàn bộ.
3. **Chỉ gọi find() một lần dù cần tất cả kết quả.** Dùng vòng **while (matcher.find())**; mỗi lần đọc **group()** ngay trong lần lặp đó.
4. **Đọc group() sau khi find() trả false.** Không còn kết quả khớp hợp lệ cho lần kiểm tra vừa thất bại; chỉ đọc thông tin match trong nhánh boolean true.
5. **Tái sử dụng Matcher mà quên vị trí hiện tại.** Gọi **reset()** hoặc tạo Matcher mới trước một kịch bản độc lập; một **Pattern** có thể tái sử dụng.
6. **Muốn tìm các kết quả chồng lấn nhưng dùng find() liên tục.** Kết quả tuần tự mặc định không chồng lấn; cần thiết kế tìm kiếm từ vị trí thích hợp hoặc dùng lookahead tùy bài toán.
7. **Nhầm \d với chuỗi Java "\\d" hoặc quên escape.** Kiểm tra từng tầng Java string và regex; nếu tạo regex từ input người dùng, cân nhắc **Pattern.quote(...)** khi muốn hiểu input là văn bản thường.
8. **Nghĩ ^ và $ luôn tương đương matches().** Anchor chịu ảnh hưởng bởi MULTILINE, region và các ranh giới; **matches()** có điều kiện phủ toàn region riêng.

## 11. Ghi nhớ qua ba câu hỏi

Với một **Pattern** đã biên dịch và một **Matcher** trên input/region:

- **“Cả vùng có hợp lệ theo mẫu không?” → matches().**
- **“Vùng có bắt đầu bằng mẫu này không?” → lookingAt().**
- **“Ở đâu trong vùng có một đoạn khớp, và có bao nhiêu đoạn?” → find().**

Khi phép khớp thành công, khai thác **group/start/end** ngay lúc đó; khi cần kiểm tra độc lập, bắt đầu bằng Matcher mới hoặc **reset()**. Sự khác nhau cốt lõi là **phạm vi phải bao phủ và vị trí bắt đầu**, không phải ba kiểu cú pháp regex khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-004">Lớp ký tự, lượng từ, neo và nhóm bắt</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp ký tự, lượng từ, neo và nhóm bắt trong Java Regex

Biểu thức chính quy (regular expression, regex) mô tả mẫu ký tự để **kiểm tra**, **tìm kiếm**, **trích xuất** hoặc **thay thế** chuỗi. Bốn thành phần quan trọng là: **lớp ký tự** quyết định một vị trí được phép chứa ký tự gì; **lượng từ** quyết định một đơn vị xuất hiện bao nhiêu lần; **neo/ranh giới** giới hạn vị trí khớp; và **nhóm bắt** gom mẫu, lưu đoạn đã khớp để truy xuất hoặc tham chiếu lại. Trong Java, các mẫu này được thực thi qua `java.util.regex.Pattern` và `Matcher`.

## 1. Đọc regex đúng trong Java

Có hai lớp cú pháp cần phân biệt:

- **Regex**: `\d{2}` nghĩa là hai chữ số; `\b` nghĩa là ranh giới từ.
- **Java string literal**: dấu `\` phải được viết thành `\\`. Vì vậy regex `\d{2}` được truyền bằng chuỗi Java `"\\d{2}"`, regex `\b` bằng `"\\b"`. Nếu viết `"\b"`, Java tạo ký tự backspace thay cho ranh giới từ.

| Mẫu regex thực tế | Chuỗi Java tương ứng | Ý nghĩa |
| --- | --- | --- |
| `\d+` | `"\\d+"` | Một hoặc nhiều chữ số |
| `\bcat\b` | `"\\bcat\\b"` | Từ `cat` độc lập |
| `^[A-Z]{2}\d{3}$` | `"^[A-Z]{2}\\d{3}$"` | Toàn chuỗi là hai chữ hoa rồi ba chữ số |
| `\.` | `"\\."` | Dấu chấm thật, không phải ký hiệu `.` của regex |

`Pattern.compile(regex)` biên dịch mẫu; `pattern.matcher(input)` tạo `Matcher` cho một đầu vào. Ba cách kiểm tra thường gặp:

- `matcher.matches()` yêu cầu **toàn bộ đầu vào** khớp mẫu.
- `matcher.find()` tìm **đoạn con kế tiếp** khớp mẫu; gọi lặp để duyệt các đoạn khớp.
- `matcher.lookingAt()` yêu cầu khớp **từ đầu vùng tìm kiếm**, nhưng không bắt buộc tới hết.

Ví dụ: với regex `\d+` và đầu vào `"abc123xyz"`, `find()` thành công và lấy `123`, còn `matches()` và `lookingAt()` đều trả về `false`. Chỉ gọi `group()`, `start()` hoặc `end()` sau khi một thao tác khớp thành công, nếu không có thể gặp `IllegalStateException`.

## 2. Lớp ký tự (character classes)

Lớp ký tự thường khớp **một ký tự** trong tập cho phép; lượng từ phía sau mới làm nó lặp nhiều lần.

| Cú pháp regex | Ý nghĩa | Ví dụ khớp |
| --- | --- | --- |
| `[abc]` | Một trong `a`, `b`, `c` | `b` |
| `[^abc]` | Một ký tự không thuộc tập `a`, `b`, `c` | `x` |
| `[a-z]` | Một ký tự trong khoảng từ `a` đến `z` | `m` |
| `[A-Za-z0-9_]` | Một chữ Latin, chữ số ASCII hoặc `_` | `T`, `7`, `_` |
| `[a-f&&[^b-d]]` | Giao của `a`–`f` với phần bù `b`–`d` | `a`, `e`, `f` |
| `\d` / `\D` | Chữ số / không phải chữ số | `5` / `Z` |
| `\s` / `\S` | Ký tự trắng / không phải ký tự trắng | dấu cách / `A` |
| `\w` / `\W` | Ký tự thuộc nhóm *word* / không thuộc *word* | `a` / `!` |
| `\p{L}` | Một ký tự Unicode thuộc nhóm chữ cái | `Đ`, `ê`, `A` |

Phủ định trong lớp ký tự là dấu `^` **ngay sau `[`**: `[^0-9]` nhận một ký tự không phải chữ số ASCII. Dấu `^` đứng ngoài lớp ký tự là neo đầu dòng/đầu vùng (phần 4), không phải phủ định. Dấu gạch nối `-` có thể tạo khoảng: `[a-z]`; nếu muốn dấu `-` thật, có thể đặt đầu/cuối như `[-+]` hoặc escape phù hợp. Các ký tự `]`, `\`, `^`, `-` có ý nghĩa tùy vị trí trong lớp, nên cần kiểm tra cách đặt/escape thay vì giả định chúng luôn là ký tự thường.

**Chú ý về Unicode:** mặc định trong Java, `\d`, `\w` và nhiều lớp viết tắt theo cách dùng ASCII; đừng mặc nhiên cho rằng `\w+` bao trùm mọi tên tiếng Việt. Dùng `\p{L}` khi cần các chữ cái Unicode, hoặc bật `Pattern.UNICODE_CHARACTER_CLASS` (cờ nhúng `(?U)`) để các lớp viết tắt và POSIX class được diễn giải theo Unicode. Quy tắc dữ liệu còn quan trọng hơn regex: tên người có thể gồm dấu cách, dấu nháy, gạch nối hoặc dấu kết hợp; một lớp chữ cái đơn thuần chưa đủ làm bộ kiểm tra tên hoàn chỉnh.

**Dấu chấm `.` không phải lớp `[.]`:** `.` khớp gần như mọi ký tự nhưng mặc định **không khớp ký tự kết thúc dòng**; cờ `Pattern.DOTALL` (`(?s)`) cho phép `.` khớp cả các ký tự đó. `\.` hoặc `[.]` mới khớp dấu chấm thật. Lớp `[\s\S]` có thể khớp cả ký tự trắng lẫn không trắng, nhưng thường khó đọc hơn một mẫu rõ ràng có `DOTALL`.

## 3. Lượng từ (quantifiers)

Lượng từ tác động lên **đơn vị ngay trước nó**: một ký tự, một lớp ký tự hoặc một nhóm. Chẳng hạn `ab+` là `a` rồi một hoặc nhiều `b`, còn `(ab)+` lặp cả cặp `ab`.

| Lượng từ | Số lần xuất hiện | Ví dụ |
| --- | --- | --- |
| `X?` | 0 hoặc 1 | `colou?r` nhận `color`, `colour` |
| `X*` | 0 trở lên | `a*` nhận cả chuỗi rỗng |
| `X+` | 1 trở lên | `a+` nhận `a`, `aaa` |
| `X{n}` | Chính xác `n` | `\d{4}` nhận bốn chữ số |
| `X{n,}` | Ít nhất `n` | `[A-Z]{2,}` |
| `X{n,m}` | Từ `n` đến `m` (gồm hai đầu) | `\d{2,4}` |

Một lượng từ có ba chiến lược:

1. **Tham lam (greedy)**, ví dụ `.*`, `a+`, `a{2,4}`: thử lấy nhiều nhất có thể, **có thể quay lui** để phần còn lại khớp.
2. **Tiết chế (reluctant/lazy)**, ví dụ `.*?`, `a+?`, `a{2,4}?`: thử lấy ít nhất trước, rồi mở rộng nếu cần.
3. **Sở hữu (possessive)**, ví dụ `.*+`, `a++`, `a{2,4}+`: lấy nhiều nhất **và không trả lại ký tự** qua backtracking.

Đối với đầu vào `<a><b>`, mẫu `<.*>` nhận cả `<a><b>` do tham lam; mẫu `<.*?>` cho `find()` trả về `<a>` rồi `<b>`. Tuy nhiên cách tìm thẻ XML/HTML đúng nghĩa cần parser tương ứng: regex minh họa này không xử lý thẻ lồng nhau, dấu nháy và cấu trúc ngôn ngữ đánh dấu.

~~~java
// QuantifierDemo.java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuantifierDemo {
    public static void main(String[] args) {
        String input = "<a><b>";
        Matcher greedy = Pattern.compile("<.*>").matcher(input);
        if (greedy.find()) {
            System.out.println("Greedy: " + greedy.group());
        }

        Matcher reluctant = Pattern.compile("<.*?>").matcher(input);
        while (reluctant.find()) {
            System.out.println("Reluctant: " + reluctant.group());
        }

        System.out.println("a+a on aaa: " + Pattern.matches("a+a", "aaa"));
        System.out.println("a++a on aaa: " + Pattern.matches("a++a", "aaa"));
    }
}
~~~

Chạy bằng `javac --release 17 QuantifierDemo.java` rồi `java QuantifierDemo`:

~~~text
Greedy: <a><b>
Reluctant: <a>
Reluctant: <b>
a+a on aaa: true
a++a on aaa: false
~~~

Ở hai dòng cuối, `a+a` có thể trả lại một `a` để phần `a` cuối khớp. `a++a` đã chiếm hết `aaa`, không backtrack nên thất bại. `*?` không có nghĩa là “không bao giờ lấy thêm”: nó vẫn mở rộng khi phần mẫu phía sau yêu cầu.

## 4. Neo và ranh giới (anchors, boundaries)

Neo kiểm tra **vị trí**, thường không tiêu thụ ký tự. Vì vậy `^\d+$` mô tả một dãy chữ số chiếm cả chuỗi trong cách kiểm tra thông thường; chỉ `\d+` có thể tìm một đoạn chữ số nằm giữa văn bản.

| Ký hiệu regex | Vị trí được khớp |
| --- | --- |
| `^` | Đầu đầu vào/vùng; với `MULTILINE`, còn là đầu từng dòng |
| `$` | Cuối đầu vào/vùng, có quy tắc đặc biệt trước ký tự kết thúc dòng cuối; với `MULTILINE`, còn là cuối từng dòng |
| `\A` | Đầu tuyệt đối của đầu vào theo vùng áp dụng |
| `\z` | Cuối tuyệt đối của đầu vào theo vùng áp dụng |
| `\Z` | Cuối đầu vào, có thể ngay trước ký tự kết thúc dòng cuối |
| `\b` | Ranh giới từ: vị trí chuyển giữa ký tự *word* và không-*word* |
| `\B` | Vị trí không phải ranh giới từ |
| `\G` | Cuối lần khớp trước (hữu ích khi muốn các lần `find()` liền kề) |

`Pattern.MULTILINE` (cờ `(?m)`) thay đổi cách `^` và `$` hoạt động quanh các dòng. Ví dụ `Pattern.compile("^foo$", Pattern.MULTILINE).matcher("foo\nbar").find()` là `true` vì khớp dòng `foo`; nhưng `matches()` trên toàn chuỗi đó vẫn là `false`. `DOTALL` thay đổi hành vi của **`.`**, không thay vai trò của `^` và `$`.

`\bcat\b` tìm từ `cat` độc lập trong `"a cat!"`, trong khi mẫu `cat` có thể khớp cả trong `"concatenate"`. Ranh giới từ **không giống dấu cách**: dấu câu, số, dấu gạch dưới, dấu kết hợp và Unicode có thể làm kết quả khác trực giác ngôn ngữ tự nhiên. Nếu quy tắc nghiệp vụ là “từ” trong một ngôn ngữ cụ thể, phải kiểm thử chuỗi thực tế.

Khi kiểm tra **toàn chuỗi**, ưu tiên `matcher.matches()` vì ý định rõ; khi muốn đọc **từng dòng** bằng `find()`, dùng `MULTILINE` có chủ đích; khi cần neo kết thúc tuyệt đối trong mẫu có thể được dùng bằng `find()`, cân nhắc `\A` và `\z` thay vì giả định `$` luôn có cùng nghĩa.

## 5. Nhóm bắt (capturing groups) và nhóm không bắt

Dấu ngoặc tròn làm hai việc: **gom các nhánh/đơn vị để lượng từ tác động** và, theo mặc định, **ghi nhớ đoạn đã khớp**.

| Cú pháp | Công dụng |
| --- | --- |
| `(ab)+` | Nhóm bắt `ab`, lặp ít nhất một lần |
| `(?:ab)+` | Nhóm **không bắt**: gom `ab` để lặp nhưng không tạo chỉ số nhóm |
| `(cat|dog)` | Nhóm bắt một trong hai nhánh |
| `(?<day>\d{2})` | Nhóm bắt có tên `day` |
| `\1` | Tham chiếu lại **nội dung** đã khớp ở nhóm bắt số 1 |
| `\k<day>` | Tham chiếu lại nội dung nhóm tên `day` |

Các nhóm bắt được đánh số **1, 2, ...** theo thứ tự dấu `(` mở của nhóm bắt khi duyệt mẫu từ trái sang phải; nhóm `0` là **toàn bộ lần khớp**. `(?:...)` không tăng số nhóm. Sau khi `find()` hoặc `matches()` thành công, `group(1)`, `group("day")`, `start(1)`, `end(1)` truy xuất nội dung và vị trí; `end()` là chỉ số **exclusive**. Vị trí Java dùng chỉ số UTF-16, nên với ký tự bổ sung Unicode (ngoài BMP) không đồng nhất với chỉ số ký tự hiển thị. `groupCount()` cho biết số nhóm bắt, không tính nhóm `0`.

Nếu một nhánh hoặc một nhóm tùy chọn không tham gia lần khớp, `group(n)` của nhóm đó là `null` (và `start(n)`, `end(n)` là `-1`). Phân biệt “không tham gia” với nhóm có khớp **chuỗi rỗng**. Nếu nhóm nằm dưới lượng từ và khớp nhiều lần, `group(n)` không phải danh sách tất cả lần bắt; thông thường nó giữ giá trị bắt sau cùng. Muốn lấy mọi lần xuất hiện, hãy thiết kế mẫu và lặp `find()` phù hợp.

Tên nhóm trong Java có dạng tên chữ Latin bắt đầu bằng chữ, theo sau là chữ/số; ví dụ `day`, `year2`. Không dùng lại cùng một tên trong một mẫu. Nhóm không bắt phù hợp khi chỉ cần gom, ví dụ `(?:https?://)?`, giúp chỉ số nhóm bắt ổn định hơn.

### Ví dụ chạy được: kiểm tra, bắt nhóm có tên và vị trí

~~~java
// RegexDemo.java
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexDemo {
    public static void main(String[] args) {
        Pattern username = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{2,11}$");
        for (String value : List.of("an", "anh_2026", "3abc", "abc_DEF012")) {
            System.out.println(value + " -> " + username.matcher(value).matches());
        }

        Pattern date = Pattern.compile("(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4})");
        Matcher matcher = date.matcher("Due: 03/11/2026; 25/12/2026.");
        while (matcher.find()) {
            System.out.println(matcher.group(0) + " -> "
                    + matcher.group("day") + "/" + matcher.group("month")
                    + "/" + matcher.group("year")
                    + " [" + matcher.start() + ", " + matcher.end() + ")");
        }
    }
}
~~~

Chạy `javac --release 17 RegexDemo.java` và `java RegexDemo`:

~~~text
an -> false
anh_2026 -> true
3abc -> false
abc_DEF012 -> true
03/11/2026 -> 03/11/2026 [5, 15)
25/12/2026 -> 25/12/2026 [17, 27)
~~~

Mẫu tên đăng nhập dùng **lớp ký tự** và **lượng từ `{2,11}`**: ký tự đầu là chữ Latin, 2–11 ký tự còn lại là chữ/số/`_`, nghĩa là tổng cộng **3–12 ký tự**. `matches()` kiểm tra toàn bộ. Mẫu ngày dùng ba nhóm có tên; `find()` tìm từng đoạn con. Đây là ví dụ **trích xuất theo hình dạng `dd/MM/yyyy`**, không xác nhận ngày hợp lệ: `99/99/2026` vẫn khớp regex; hãy dùng `java.time` để kiểm tra giá trị ngày thực tế.

### Tham chiếu ngược: tìm từ bị lặp

Regex `\b(?<word>[A-Za-z]+)\s+\k<word>\b` tìm hai từ giống nhau ngăn bởi khoảng trắng. Từ thứ hai phải có **đúng nội dung nhóm `word` vừa bắt**, không đơn thuần là một từ bất kỳ. Cần cờ `CASE_INSENSITIVE` nếu muốn `Go go` khớp:

~~~java
// BackreferenceDemo.java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackreferenceDemo {
    public static void main(String[] args) {
        Pattern repeatedWord = Pattern.compile(
                "\\b(?<word>[A-Za-z]+)\\s+\\k<word>\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = repeatedWord.matcher("Go go, step ahead, Java java.");
        while (matcher.find()) {
            System.out.println(matcher.group());
        }
    }
}
~~~

Chạy `javac --release 17 BackreferenceDemo.java` và `java BackreferenceDemo`:

~~~text
Go go
Java java
~~~

**Phân biệt:** tham chiếu ngược `\1` hoặc `\k<word>` yêu cầu lặp lại **chính giá trị đã khớp**; còn `([A-Za-z]+)\s+([A-Za-z]+)` chỉ đòi hai từ bất kỳ. Trong Java string literal, viết tham chiếu số 1 là `"\\1"`, tham chiếu tên là `"\\k<word>"`.

## 6. Ghép các thành phần để giải bài toán

Quy trình xây mẫu dễ kiểm tra:

1. Viết rõ **định dạng dữ liệu** và các chuỗi hợp lệ/không hợp lệ; quyết định cần so toàn chuỗi hay tìm đoạn con.
2. Chọn **lớp ký tự** cho từng vị trí (`[A-Z]`, `\d`, `\p{L}`, ...), xét cả Unicode.
3. Thêm **lượng từ** diễn tả độ dài (`{4}`, `{2,8}`, `+`), đặt ngoặc cho đơn vị cần lặp (`(?:ab){3}`).
4. Thêm **neo/ranh giới** khi thực sự cần, rồi chọn `matches()` hay `find()` tương ứng.
5. Chỉ tạo **nhóm bắt** cho dữ liệu cần lấy/so sánh; dùng `(?:...)` cho phần gom còn lại.
6. Thử cả trường hợp rỗng, dài quá, ký tự lạ, xuống dòng, Unicode và chuỗi gây nhiều cách backtracking.

Ví dụ biểu diễn một mã có hai chữ Latin viết hoa, dấu `-` rồi bốn chữ số là `^[A-Z]{2}-\d{4}$` (Java string: `"^[A-Z]{2}-\\d{4}$"`). Mẫu cho phép `AB-1234`, bác bỏ `A-1234`, `AB_1234` và `AB-12345` khi dùng `matches()`. Nếu bắt phần mã để xử lý riêng: `^([A-Z]{2})-(\d{4})$`; lấy mã chữ qua `group(1)`, mã số qua `group(2)` sau khi khớp thành công.

## 7. Các lỗi thường gặp và cách tránh

- **Quên hai mức escape**: Java `"\\d"` mới truyền regex `\d`; Java `"\b"` là backspace. Nếu regex viết ở tệp cấu hình hay được đọc từ đầu vào, quy tắc escape của **Java source** không còn áp dụng nguyên xi.
- **Nhầm `find()` với `matches()`**: `find()` có thể chấp nhận một phần input. Với kiểm tra định dạng toàn chuỗi, dùng `matches()` để tránh lọt tiền tố/hậu tố không hợp lệ.
- **Nhầm `.` với dấu chấm thật**, `^` trong/ngoài `[]`, hoặc `ab+` với `(ab)+`; cần xác định đơn vị regex mà toán tử tác động.
- **Cho rằng lazy luôn nhận đúng cấu trúc**: `<.*?>` minh họa sự khác biệt với greedy, không thay parser HTML/XML/JSON. Regex không tự kiểm tra được cấu trúc lồng nhau phức tạp.
- **Dùng quá nhiều nhóm bắt**: thay nhóm chỉ để gom bằng `(?:...)` để `group(1)`, `group(2)` không bị đổi nghĩa khi sửa mẫu.
- **Không xử lý nhóm tùy chọn**: `group(n)` có thể là `null`; không gọi `group()` khi `find()`/`matches()` thất bại.
- **Đồng nhất Unicode với ASCII**: `[A-Z]`, `\w`, `\d` có các phạm vi khác nhau; thử dữ liệu có dấu, ký tự bổ sung, dấu kết hợp và bật cờ Unicode khi phù hợp.
- **Backtracking tốn kém**: các lượng từ lồng nhau mơ hồ như `(a+)+$` có thể rất chậm trên chuỗi dài gần khớp. Hạn chế đầu vào, dùng lớp ký tự cụ thể, cấu trúc không mơ hồ, hoặc lượng từ sở hữu khi bảo toàn đúng ý nghĩa. `Pattern` biên dịch tái sử dụng được và an toàn khi chia sẻ; mỗi `Matcher` giữ trạng thái riêng, không dùng chung đồng thời giữa các thread.
- **Nhúng văn bản người dùng vào regex**: nếu cần tìm chuỗi **nguyên văn**, dùng `Pattern.quote(userText)` thay vì ghép trực tiếp các ký tự đặc biệt vào regex. Khi thay thế và muốn đưa chuỗi thay thế vào nguyên văn, xét `Matcher.quoteReplacement(replacementText)` vì `$` và `\` có nghĩa đặc biệt trong replacement.

## 8. Tự kiểm tra hiểu bài

1. Vì sao `Pattern.matches("a+", "baaa")` là `false` nhưng `Pattern.compile("a+").matcher("baaa").find()` là `true`?
2. Với chuỗi `"abab"`, `(ab){2}` và `ab{2}` có cùng kết quả khi dùng `matches()` không? Lượng từ đang áp dụng lên đơn vị nào?
3. Trên `<a><b>`, `.*` và `.*?` khiến `<...>` nhận đoạn khác nhau thế nào, và vì sao `.*+` có thể làm mẫu thất bại?
4. Viết mẫu bắt riêng `year`, `month`, `day` trong chuỗi `"2026-10-23"` rồi kiểm tra vì sao regex định dạng vẫn chưa thay thế kiểm tra ngày lịch.
5. Khi cần kiểm tra một chuỗi kết thúc **thực sự** tại vị trí cuối đầu vào, tại sao `\z` rõ ràng hơn `$`?

**Ghi nhớ:** lớp ký tự = *ký tự gì*; lượng từ = *bao nhiêu lần*; neo/ranh giới = *ở vị trí nào*; nhóm bắt = *phần nào cần gom/lấy lại*. Chọn `matches()` cho kiểm tra toàn chuỗi, `find()` cho tìm/trích xuất, và dùng ví dụ kiểm thử với dữ liệu thật để xác minh từng lớp quy tắc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-005">Hai lớp escape: literal Java và cú pháp regex</a>

<details>
<summary>Xem chi tiết</summary>

# Hai lớp escape: literal Java và cú pháp regex

## 1. Vì sao phải hiểu hai lớp escape?

Trong Java, biểu thức chính quy thường được truyền cho `java.util.regex.Pattern`, `String.matches`, `String.split` hoặc `String.replaceAll` dưới dạng một `String`. Khi viết chuỗi này **ngay trong mã nguồn Java**, có hai bộ phân tích cú pháp xử lý lần lượt:

1. **Java string literal:** trình biên dịch xử lý cú pháp bên trong dấu `"..."`, bao gồm escape như `\\` (một dấu backslash), `\n` (xuống dòng), `\t` (tab), `\"` (dấu nháy).
2. **Regex:** regex engine của `Pattern` nhận chuỗi *đã được Java xử lý* rồi diễn giải `\d` (chữ số), `\s` (whitespace), `\.` (dấu chấm literal), `\b` (ranh giới từ), v.v.

Do đó, **backslash dùng trong regex thường phải viết gấp đôi trong Java string literal**.

```text
Java source           Giá trị String runtime        Regex engine hiểu
"\\d+"          ->     \d+                      ->   một hay nhiều chữ số
"\\."           ->     \.                       ->   dấu chấm literal
"\\\\"         ->     \\                       ->   một dấu \ literal
```

Hãy giải mã lớp Java trước, rồi mới giải mã lớp regex. Hai lớp cùng dùng dấu `\` nhưng có **ngữ pháp và mục đích khác nhau**.

## 2. Lớp thứ nhất: Java string literal

| Literal trong Java source | Giá trị chuỗi thực tế | Lưu ý |
| --- | --- | --- |
| `"abc"` | `abc` | Không có escape |
| `"\\."` | `\.` | Hai backslash trong source tạo một backslash |
| `"\\d+"` | `\d+` | Ba ký tự runtime: `\`, `d`, `+` |
| `"\\\\"` | `\\` (hai dấu backslash) | Bốn backslash trong source tạo hai backslash |
| `"\n"` | Một ký tự xuống dòng | Không phải hai ký tự `\` và `n` |
| `"\t"` | Một ký tự tab | Không phải hai ký tự `\` và `t` |
| `"\""` | Một dấu nháy kép `"` | Escape dành cho dấu nháy |

Java không công nhận các escape source `\d` và `\.`. Vì vậy `"\d+"` hay `"\."` gây lỗi biên dịch (*illegal escape character*) trước khi regex engine có cơ hội xử lý.

### Chú ý đặc biệt với `\s` trong Java 17+

Từ Java 15, `\s` **là escape hợp lệ trong Java string literal**, nghĩa là một dấu cách thường U+0020. Điều này dễ gây lỗi âm thầm:

- `"\\s+"` tạo regex `\s+`, khớp một hay nhiều whitespace theo quy tắc regex (như space, tab).
- `"\s+"` tạo regex ` +`, khớp một hay nhiều **dấu cách thường**, không khớp tab.

Trên Java 17+, cả hai dòng đều biên dịch, nhưng hành vi khác nhau. Không thể dùng kết quả biên dịch thành công để kết luận đã escape regex chính xác.

## 3. Lớp thứ hai: Regex engine của `Pattern`

Một số ánh xạ thông dụng:

| Ý định | Regex thực tế cần truyền vào `Pattern` | Viết trong Java string literal |
| --- | --- | --- |
| Một hay nhiều chữ số | `\d+` | `"\\d+"` |
| Một hay nhiều whitespace | `\s+` | `"\\s+"` |
| Dấu chấm literal | `\.` | `"\\."` |
| Dấu gạch đứng literal `|` | `\|` | `"\\|"` |
| Một dấu backslash literal `\` | `\\` | `"\\\\"` |
| Ranh giới từ | `\b` | `"\\b"` |
| Ký tự thuộc Unicode Letter | `\p{L}` | `"\\p{L}"` |

Dấu chấm `.` không được escape trong regex là **ký tự đại diện** (thông thường khớp một ký tự trừ ký tự kết thúc dòng, trừ khi bật `DOTALL`). Regex `\.` có nghĩa dấu chấm thật; Java literal cho nó là `"\\."`. Còn muốn regex nhận *một dấu backslash thật*, phải tạo regex `\\`, vì vậy Java source cần `"\\\\"`.

Đối với ranh giới từ, regex `\b` khớp **vị trí**, không tiêu thụ ký tự. Java literal `"\\b"` đưa hai ký tự `\` và `b` đến regex engine. Ngược lại `"\b"` là escape **backspace U+0008 của Java**, nên nghĩa khác hoàn toàn.

## 4. Ví dụ Java 17+ có thể chạy độc lập

Lưu đúng tên tệp `EscapeLayersDemo.java`:

```java
import java.util.Arrays;
import java.util.regex.Pattern;

public class EscapeLayersDemo {
    public static void main(String[] args) {
        System.out.println("dot literal: " + "a.b".matches("a\\.b"));
        System.out.println("dot strict: " + "axb".matches("a\\.b"));
        System.out.println("dot wildcard: " + "axb".matches("a.b"));

        System.out.println("digit: " + "123".matches("\\d+"));
        System.out.println("digit overescape: " + "123".matches("\\\\d+"));

        System.out.println("backslash: " + "\\".matches("\\\\"));
        System.out.println("tab whitespace: " + "a\tb".matches("a\\sb"));
        System.out.println("tab mistaken Java escape: "
                + "a\tb".matches("a\s+b"));

        System.out.println("quoted dot: "
                + "a.b".matches("a" + Pattern.quote(".") + "b"));
        System.out.println("literal replacement: "
                + "a.b".replace(".", "-"));
        System.out.println("regex replacement: "
                + "a.b".replaceAll("\\.", "-"));
        System.out.println("split: "
                + Arrays.toString("one.two".split("\\.")));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 EscapeLayersDemo.java
java EscapeLayersDemo
```

Stdout chính xác:

```text
dot literal: true
dot strict: false
dot wildcard: true
digit: true
digit overescape: false
backslash: true
tab whitespace: true
tab mistaken Java escape: false
quoted dot: true
literal replacement: a-b
regex replacement: a-b
split: [one, two]
```

Các điểm cần đọc kỹ từ ví dụ:

- `"a\\.b"` qua lớp Java trở thành regex `a\.b`, chỉ khớp `a.b`. `"a.b"` qua regex giữ dấu `.` đại diện nên còn khớp `axb`.
- `"\\d+"` trở thành regex `\d+`, khớp `123`. Nhưng `"\\\\d+"` trở thành regex `\\d+`: regex hiểu `\\` là **backslash literal**, còn `d+` là một hoặc nhiều chữ `d`, vì vậy không khớp `123`. Đây là lỗi **escape thừa**.
- `"\\"` tạo dữ liệu đầu vào chỉ gồm một dấu backslash. `"\\\\"` tạo regex khớp đúng dấu đó.
- `"a\tb"` chứa tab. Regex `a\sb` khớp tab, nhưng Java source `"a\s+b"` tạo regex `a +b` nên không khớp tab.
- `Pattern.quote(".")` tạo mẫu tìm **dấu chấm literal**. `replace` không dùng regex, còn `replaceAll` dùng regex ở đối số đầu tiên.
- `split("\\.")` dùng regex tìm dấu chấm literal. `split(".")` sẽ hiểu `.` là ký tự đại diện, nên không có ý nghĩa tách theo dấu chấm thật.

`String.matches(regex)` yêu cầu **toàn bộ chuỗi** khớp. Nếu cần tìm một đoạn bất kỳ, dùng `Pattern.compile(regex).matcher(input).find()`. Ví dụ `"abc123".matches("\\d+")` cho `false`, nhưng `Pattern.compile("\\d+").matcher("abc123").find()` cho `true`.

## 5. Phân tích escape đúng cách: từ mã nguồn đến hành vi

Xét hai chuỗi sau trong thân một phương thức Java:

```java
String digits = "\\d+";
String extraSlash = "\\\\d+";

System.out.println(digits);      // \d+
System.out.println(extraSlash);  // \\d+
```

- `digits` có ba ký tự runtime: `\`, `d`, `+`. Regex hiểu là *một hay nhiều chữ số*.
- `extraSlash` có bốn ký tự runtime: `\`, `\`, `d`, `+`. Regex hiểu là *một backslash literal theo sau một hay nhiều chữ d*.

Các thông báo lỗi cũng xảy ra ở **những giai đoạn khác nhau**:

```java
// Không biên dịch được: \d không phải escape Java hợp lệ.
// String invalidJava = "\d+";

// Dòng sau biên dịch Java được nhưng ném PatternSyntaxException
// khi Pattern cố biên dịch regex gồm một backslash đơn lẻ.
// Pattern.compile("\\");
```

Nếu `String regex` đến từ input người dùng hoặc database *lúc chương trình chạy*, Java không tự áp lại các quy tắc string literal cho ký tự của giá trị đó. Khi `Pattern.compile(regex)` nhận chuỗi runtime có nội dung `\d+`, chỉ còn lớp xử lý regex. File cấu hình như JSON/YAML có thể có cú pháp escape riêng; cần xét định dạng nguồn thực tế, không mặc nhiên áp cú pháp Java source lên mọi chuỗi dữ liệu.

### Xuống dòng và chuỗi `\n`

Cần phân biệt **ký tự xuống dòng thực** với **hai ký tự backslash và chữ n**:

- Trong Java, `"\n"` là ký tự xuống dòng.
- `"\\n"` là hai ký tự `\` và `n`. Khi đưa nội dung `\n` này qua regex engine, `Pattern` diễn giải nó thành mẫu khớp ký tự xuống dòng.
- Nếu muốn khớp đúng hai ký tự `\` và `n` trong dữ liệu, cách an toàn là `Pattern.quote("\\n")`, vì nó tạo regex theo nghĩa literal của toàn bộ chuỗi.

Việc “nhìn thấy `\n`” trong một chuỗi được in ra chưa đủ để biết đó là xuống dòng hay chữ literal; phải xác định đang nhìn mã nguồn, giá trị runtime hay regex đã được phân tích.

## 6. Khi nào nên dùng `Pattern.quote`?

Khi đoạn cần tìm là **dữ liệu literal**, đặc biệt dấu phân cách lấy từ input/configuration, `Pattern.quote(value)` tạo regex khớp nguyên văn nội dung chuỗi. Không cần tự escape dấu chấm, dấu `|`, ngoặc hoặc dấu backslash.

```java
import java.util.Arrays;
import java.util.regex.Pattern;

String delimiter = ".";
String[] fields = "api.example.com".split(Pattern.quote(delimiter));
// fields = ["api", "example", "com"]

String[] keepEmptyEnd = "a.b.".split(Pattern.quote(delimiter), -1);
// keepEmptyEnd = ["a", "b", ""]
```

Đây là **đoạn mã minh họa đặt trong một phương thức**, không phải class chạy độc lập. Mẫu `Pattern.quote` có thể quote cả một chuỗi nhiều ký tự, và cũng xử lý được dấu backslash.

Ngược lại, **không quote một regex mà bạn muốn nó hoạt động như regex**. Ví dụ `Pattern.quote("\\d+")` sẽ tìm đúng ba ký tự `\`, `d`, `+` trong input, không còn mang nghĩa một hay nhiều chữ số. Để khớp chữ số, dùng `Pattern.compile("\\d+")`.

Các ký tự có ý nghĩa đặc biệt trong regex gồm `.`, `^`, `$`, `*`, `+`, `?`, `(`, `)`, `[`, `]`, `{`, `}`, `|` và `\`. Khi chúng là *dữ liệu*, chọn `Pattern.quote` hoặc escape regex theo đúng ngữ cảnh; khi chúng là *toán tử regex*, giữ ngữ nghĩa mong muốn.

## 7. Phân biệt các API và “lớp escape” thứ ba

| API | Ý nghĩa của tham số liên quan |
| --- | --- |
| `Pattern.compile(regex)` | Biên dịch regex |
| `String.matches(regex)` | Regex, yêu cầu toàn chuỗi khớp |
| `String.split(regex)` | Regex làm dấu phân cách |
| `String.replaceAll(regex, replacement)` | Tham số đầu là regex; replacement có cú pháp riêng |
| `String.replace(target, replacement)` | Thay chuỗi literal; không diễn giải regex |

Đặc biệt, với `replaceAll`, **replacement** có thêm quy tắc riêng: `$1` có thể tham chiếu nhóm bắt và `\` là ký tự escape trong replacement. Nếu replacement đến từ dữ liệu literal có thể chứa `$` hoặc `\`, dùng `Matcher.quoteReplacement(replacement)`. Hai hàm không hoán đổi vai trò:

- `Pattern.quote(literal)` xử lý **mẫu tìm kiếm** theo nghĩa literal.
- `Matcher.quoteReplacement(literal)` xử lý **chuỗi thay thế** theo nghĩa literal.

Đây là lý do không thể áp dụng mẹo “chỉ cần nhân đôi mọi backslash” một cách máy móc: trước hết phải biết chuỗi đang được phân tích bởi ngữ pháp Java source, ngữ pháp regex hay ngữ pháp replacement.

## 8. Lỗi phổ biến và cách kiểm tra

1. **`"\d+"` hoặc `"\."` không biên dịch:** Java không có những escape đó. Cần `"\\d+"` hoặc `"\\."`.
2. **`"\s+"` biên dịch được nhưng bỏ sót tab:** Java đã biến `\s` thành space. Để dùng regex whitespace, cần `"\\s+"`.
3. **`"."` khớp quá rộng:** regex hiểu `.` là ký tự đại diện. Nếu muốn dấu chấm, dùng `"\\."`, `"[.]"` hoặc `Pattern.quote(".")`.
4. **`"\\"` được đưa trực tiếp vào regex để tìm dấu backslash:** Java tạo một dấu `\` đơn, khiến regex không hợp lệ. Dùng `"\\\\"` hoặc `Pattern.quote("\\")`.
5. **`"\\\\d+"` bị escape thừa:** regex không còn là chữ số. Xác định giá trị `String` trung gian trước khi sửa.
6. **`"\b"` bị dùng cho word boundary:** Java tạo ký tự backspace. Dùng `"\\b"`.
7. **`Pattern.quote("\\d+")` mất tác dụng regex:** chỉ quote khi mẫu thực sự là dữ liệu literal.
8. **Thay thế chuỗi chứa `$` hay `\` bằng `replaceAll`:** với replacement nguyên văn, dùng `Matcher.quoteReplacement`.
9. **Nhầm `matches` và `find`:** `matches` khớp toàn bộ input, `find` tìm đoạn phù hợp.

### Quy trình tự kiểm tra

**Bước 1.** Viết rõ bạn muốn khớp một ký tự literal hay cú pháp regex (ví dụ dấu chấm thật hay ký tự bất kỳ).

**Bước 2.** Tính chính xác giá trị `String` sau bước Java literal: có bao nhiêu ký tự `\`, tab hoặc xuống dòng thực?

**Bước 3.** Phân tích giá trị đó theo `Pattern`; nếu dùng `replaceAll`, phân tích replacement theo ngữ pháp replacement riêng.

**Bước 4.** Kiểm thử cả dữ liệu phải khớp và dữ liệu gần giống nhưng **không được khớp**, chẳng hạn `a.b` so với `axb`. Kiểm tra đúng API `matches`, `find`, `split` hoặc `replaceAll` theo mục đích.

**Ghi nhớ:** với regex viết trong Java source, phải đi qua **hai lớp**: Java giải mã literal thành `String`; regex engine giải mã `String` thành quy tắc khớp. `Pattern.quote` hữu ích khi mục tiêu là tìm chuỗi literal, còn `"\\d+"`, `"\\."`, `"\\s+"` minh họa cách escape khi muốn dùng cú pháp regex thật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-006">Pattern.quote cho dữ liệu được tìm nguyên văn</a>

<details>
<summary>Xem chi tiết</summary>

# Pattern.quote: tìm dữ liệu nguyên văn khi làm việc với regex trong Java

## 1. Vấn đề: dữ liệu cần tìm có thể bị hiểu thành biểu thức chính quy

Trong Java, các API như `Pattern.compile(regex)`, `String.matches(regex)`, `String.split(regex)` và `String.replaceAll(regex, replacement)` nhận **biểu thức chính quy** (regular expression). Nhưng chuỗi do người dùng nhập thường chỉ là **văn bản cần tìm nguyên văn**.

Ví dụ người dùng nhập `a.b` vì họ muốn tìm đúng ba ký tự: `a`, dấu chấm `.`, rồi `b`. Nếu viết:

```java
Pattern pattern = Pattern.compile("a.b");
System.out.println(pattern.matcher("a.b").matches()); // true
System.out.println(pattern.matcher("axb").matches()); // true
```

Cả hai lần kiểm tra đều trả về `true`. Lý do: trong regex, `.` mang nghĩa “một ký tự bất kỳ” (ngoại trừ một số ký tự kết thúc dòng theo chế độ mặc định). Nếu dữ liệu là `file[1].txt`, dấu ngoặc vuông và dấu chấm còn khiến biểu thức được hiểu khác hẳn chuỗi gốc.

Các ký tự như `.`, `*`, `+`, `?`, `^`, `$`, `(`, `)`, `[`, `]`, `{`, `}`, `|` và `\` có vai trò đặc biệt trong cú pháp regex tùy ngữ cảnh. Một dữ liệu đầu vào như `[` còn có thể làm `Pattern.compile` ném `PatternSyntaxException` vì regex không hợp lệ.

**Giải pháp:** khi cần tìm hoặc khớp **nguyên văn** một chuỗi bên trong API regex, dùng `Pattern.quote(text)`:

```java
import java.util.regex.Pattern;

String keyword = "a.b";
Pattern literalPattern = Pattern.compile(Pattern.quote(keyword));

System.out.println(literalPattern.matcher("a.b").matches()); // true
System.out.println(literalPattern.matcher("axb").matches()); // false
```

Ở đây `Pattern.quote(keyword)` khiến toàn bộ `keyword` được hiểu là dữ liệu literal; dấu chấm không còn là toán tử regex.

## 2. Pattern.quote hoạt động như thế nào?

Khai báo API:

```text
java.util.regex.Pattern.quote(String s) → String
```

Đây là cách ghi chữ ký để giải thích: trong mã Java thực tế, gọi `Pattern.quote(s)`; không khai báo lại phương thức đó.

`Pattern.quote(s)` trả về một **chuỗi biểu thức chính quy** có thể khớp đúng các ký tự trong `s` theo nghĩa nguyên văn. Phương thức **không thay đổi `s`**, không tìm dữ liệu và không tự biên dịch `Pattern`. Bạn đưa chuỗi kết quả vào `Pattern.compile(...)` hoặc một API có nhận regex.

Với dữ liệu thông thường, chuỗi regex được tạo có dạng `\Q...\E`: trong cú pháp regex Java, `\Q` bắt đầu vùng được coi là literal, `\E` kết thúc vùng đó.

```java
String literal = "a.b";
String safeRegex = Pattern.quote(literal);
System.out.println(safeRegex); // \Qa.b\E
```

Không cần tự ghép `"\Q"` và `"\E"` vào dữ liệu. Nếu chuỗi gốc chứa chính dãy ký tự `\E`, `Pattern.quote` vẫn xử lý đúng để giữ nguyên ý nghĩa literal. Ví dụ:

```java
String input = "\\E"; // Hai ký tự thực tế: dấu \ và chữ E
boolean matches = Pattern.compile(Pattern.quote(input))
        .matcher(input)
        .matches();
System.out.println(matches); // true
```

Lưu ý hai tầng cú pháp:

- **Java string literal:** `"\\E"` biểu diễn chuỗi runtime `\E`; dấu `\` phải được escape khi viết mã Java.
- **Java regex:** `Pattern.quote` xử lý tiếp chuỗi runtime đó để regex engine không diễn giải nó như một lệnh điều khiển regex.

**Quy tắc cốt lõi:** `Pattern.quote` là công cụ **escape chuỗi tìm kiếm (pattern)**. Nó không phải công cụ escape chuỗi **thay thế (replacement)**.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp `PatternQuoteDemo.java` với toàn bộ mã dưới đây:

```java
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternQuoteDemo {
    public static void main(String[] args) {
        String text = "a.b | axb | a.b";
        String keyword = "a.b";

        // Không quote: "." được hiểu là ký tự bất kỳ.
        System.out.println("raw matches axb: " +
                Pattern.compile(keyword).matcher("axb").matches());

        // Quote: chỉ khớp đúng chuỗi literal "a.b".
        Pattern literal = Pattern.compile(Pattern.quote(keyword));
        System.out.println("literal matches axb: " +
                literal.matcher("axb").matches());

        Matcher matcher = literal.matcher(text);
        int count = 0;
        while (matcher.find()) {
            System.out.println("found: " + matcher.group());
            count++;
        }
        System.out.println("count: " + count);

        // Dấu "|" là ký tự đặc biệt của regex; giữ trường rỗng cuối.
        String[] fields = "red|green||".split(Pattern.quote("|"), -1);
        System.out.println("fields: " + Arrays.toString(fields));
        System.out.println("fields count: " + fields.length);

        // Pattern.quote bảo vệ phần cần tìm;
        // Matcher.quoteReplacement bảo vệ phần thay thế.
        String replacement = "$5\\path";
        String changed = Pattern.compile(Pattern.quote("a.b"))
                .matcher("a.b and a.b")
                .replaceAll(Matcher.quoteReplacement(replacement));
        System.out.println("replaced: " + changed);

        // Trường hợp chuỗi gốc chứa chính dấu kết thúc vùng quote.
        String special = "\\E";
        System.out.println("embedded E: " +
                Pattern.compile(Pattern.quote(special))
                        .matcher(special).matches());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac PatternQuoteDemo.java
java PatternQuoteDemo
```

Kết quả:

```text
raw matches axb: true
literal matches axb: false
found: a.b
found: a.b
count: 2
fields: [red, green, , ]
fields count: 4
replaced: $5\path and $5\path
embedded E: true
```

Khi đọc kết quả:

1. Không quote, regex `a.b` nhận cả `axb`; có quote, `a.b` chỉ khớp đúng `a.b`.
2. `matcher.find()` quét lần lượt các vùng khớp bên trong chuỗi dài. Trong `"a.b | axb | a.b"` có đúng **hai** lần xuất hiện literal `a.b`.
3. `split(Pattern.quote("|"), -1)` tách theo đúng dấu `|`; `-1` là tham số riêng của `split` để giữ hai trường rỗng ở cuối.
4. Chuỗi thay thế chứa cả `$` và `\` vẫn được chèn nguyên văn nhờ `Matcher.quoteReplacement`.
5. Trường hợp `\E` cũng khớp nguyên văn, chứng tỏ không cần tự xử lý thủ công dấu kết thúc vùng quote.

## 4. Chọn phương thức tìm kiếm đúng mục đích

Sau khi tạo `Pattern` từ literal, việc nhận kết quả phụ thuộc cách kiểm tra:

| Mục đích | API | Hành vi |
| --- | --- | --- |
| Tìm xem chuỗi có chứa dữ liệu nguyên văn hay không | `text.contains(keyword)` | Tìm literal, không cần regex. |
| Tìm một hay nhiều vị trí xuất hiện literal bằng regex | `Pattern.compile(Pattern.quote(keyword)).matcher(text).find()` | `find()` tìm một vùng khớp bên trong chuỗi. |
| Yêu cầu **toàn bộ** chuỗi bằng literal | `Pattern.compile(Pattern.quote(keyword)).matcher(text).matches()` | `matches()` chỉ đúng khi khớp toàn bộ đầu vào. |
| Xác định phần đầu của chuỗi bằng regex | `matcher.lookingAt()` | Kiểm tra một vùng khớp bắt đầu tại đầu input. |
| Tách theo dấu phân cách literal | `text.split(Pattern.quote(separator), -1)` | Dấu phân cách được hiểu nguyên văn; `-1` giữ trường rỗng cuối. |
| Thay toàn bộ chuỗi con literal | `text.replace(oldText, newText)` | Cả hai đối số đều là literal; không cần quote. |

Ví dụ phân biệt `find()` với `matches()`:

```java
Pattern p = Pattern.compile(Pattern.quote("api.v1"));
Matcher m = p.matcher("prefix-api.v1-suffix");

System.out.println(m.find()); // true
System.out.println(p.matcher("prefix-api.v1-suffix").matches()); // false
System.out.println(p.matcher("api.v1").matches()); // true
```

Đoạn mã trên là một ví dụ đặt trong một phương thức có `import java.util.regex.Pattern;` và `import java.util.regex.Matcher;`.

Đặc biệt, **không bắt buộc phải dùng regex** để kiểm tra chuỗi con nguyên văn. `String.contains` hoặc `String.indexOf` thường rõ ràng hơn cho việc tìm đơn giản. Dùng `Pattern.quote` khi thật sự đang làm việc với regex, ví dụ cần `Matcher` để lấy vị trí, cần ghép regex với dữ liệu literal, hoặc API yêu cầu đối số regex.

## 5. Ghép phần literal vào regex có chủ đích

Không phải mọi bài toán đều muốn biến **toàn bộ** biểu thức thành literal. Đôi khi ta muốn giữ một số thành phần regex (anchor, khoảng trắng linh hoạt, nhóm bắt) nhưng tên hoặc nhãn do người dùng cung cấp phải được tìm đúng nguyên văn.

Giả sử một dòng cấu hình có dạng `"status[0]: ACTIVE"`, trong đó `status[0]` là nhãn đầu vào:

```java
String label = "status[0]";
String line = "status[0]: ACTIVE";

Pattern pattern = Pattern.compile(
        "^" + Pattern.quote(label) + "\\s*:\\s*(.+)$");

Matcher matcher = pattern.matcher(line);
if (matcher.matches()) {
    System.out.println(matcher.group(1)); // ACTIVE
}
```

Ý nghĩa của từng đoạn:

- `^` và `$`: các anchor do người lập trình chủ động đặt ở đầu/cuối regex.
- `Pattern.quote(label)`: nhãn `status[0]` được khớp nguyên văn; `[0]` không biến thành character class.
- `\\s*:\\s*` trong mã Java: regex cho phép 0 hoặc nhiều ký tự khoảng trắng ở hai bên dấu hai chấm.
- `(.+)`: nhóm bắt nội dung ở cuối; `group(1)` lấy giá trị `ACTIVE`.

Nếu quote **toàn bộ** biểu thức `"^" + label + "\\s*:\\s*(.+)$"` thì các dấu `^`, `$`, `\s*` và `(.+)` cũng biến thành ký tự literal và mất chức năng regex. Chỉ quote **đoạn dữ liệu cần khớp nguyên văn**, giữ nguyên các thành phần regex có chủ đích.

## 6. Pattern.quote và Matcher.quoteReplacement là hai việc khác nhau

Với các API như `Matcher.replaceAll(replacement)`, chuỗi thay thế có cú pháp riêng:

- `$1`, `$2`… có thể tham chiếu đến nhóm bắt.
- `\` được dùng làm ký tự escape trong replacement.

Nếu replacement do người dùng nhập là `"$5\path"`, đưa trực tiếp vào `Matcher.replaceAll` có thể gây lỗi khi `$5` bị diễn giải như một tham chiếu nhóm không tồn tại, hoặc khiến dấu `\` không được giữ nguyên như mong muốn.

Để thay một literal bằng một literal thông qua regex:

```java
String original = "key[0] and key[0]";
String fromUser = "key[0]";
String replacement = "$5\\path";

String result = Pattern.compile(Pattern.quote(fromUser))
        .matcher(original)
        .replaceAll(Matcher.quoteReplacement(replacement));

System.out.println(result);
// $5\path and $5\path
```

Sơ đồ trách nhiệm:

```text
Dữ liệu cần tìm      --Pattern.quote--------> regex pattern literal
Dữ liệu cần thay thế --Matcher.quoteReplacement--> replacement literal
                                      |
                     Matcher.replaceAll(replacement)
```

Nếu chỉ cần thay chuỗi con literal, dùng API ngắn hơn và tự nhiên hơn:

```java
String result = original.replace(fromUser, replacement);
```

`String.replace(CharSequence, CharSequence)` thay chuỗi literal; còn `String.replaceAll(String, String)` nhận regex và replacement có ngữ nghĩa đặc biệt. Đừng nhầm tên `replace` với `replaceAll`.

## 7. Pattern.LITERAL có thay được Pattern.quote không?

Java cung cấp cờ `Pattern.LITERAL`:

```java
String keyword = "file[1].txt";

Pattern byQuote = Pattern.compile(Pattern.quote(keyword));
Pattern byFlag = Pattern.compile(keyword, Pattern.LITERAL);

System.out.println(byQuote.matcher(keyword).matches()); // true
System.out.println(byFlag.matcher(keyword).matches());  // true
```

Cả hai cách đều khiến nội dung `keyword` được hiểu là literal trong ví dụ trên. Khác biệt trong cách dùng:

- `Pattern.quote(keyword)` trả về **một chuỗi regex**; phù hợp khi cần chèn dữ liệu literal vào một regex lớn hơn, như `"^" + Pattern.quote(keyword) + "$"`.
- `Pattern.compile(keyword, Pattern.LITERAL)` áp dụng cách hiểu literal cho **toàn bộ** chuỗi pattern được biên dịch. Nếu đưa cả `"^"` hoặc `".*"` vào chuỗi với cờ này, các ký tự đó cũng sẽ được tìm như ký tự thường.

Khi chỉ cần biên dịch một pattern hoàn toàn literal, cả hai là lựa chọn hợp lệ. Khi cần pha trộn literal và cú pháp regex, `Pattern.quote` cho từng phần dữ liệu thuận tiện hơn.

## 8. Lỗi phổ biến và trường hợp biên

### 8.1. Tự escape từng ký tự đặc biệt

Ví dụ thủ công thay mọi dấu chấm bằng `\\.` có thể giải quyết riêng dấu chấm, nhưng chưa giải quyết `[`, `|`, `\` và nhiều ký tự khác. Regex còn có quy tắc escape khác nhau tùy vị trí; càng nhiều lần biến đổi thủ công càng dễ dẫn đến escape thiếu hoặc thừa.

Hãy dùng `Pattern.quote` cho **nguyên một giá trị literal**. Nếu đầu vào có thể chứa `\E`, hàm này cũng xử lý được; không tự ghép `\Q`/`\E` rồi hy vọng đầu vào luôn “đơn giản”.

### 8.2. Nhầm giá trị literal với regex hợp lệ do người dùng cố ý nhập

Nếu chức năng sản phẩm là **“Tìm theo regex”**, việc áp dụng `Pattern.quote` lên toàn bộ mẫu đầu vào sẽ khiến các toán tử regex không còn hoạt động. Ví dụ người dùng cố ý nhập `a.*b` để khớp nhiều ký tự thì sau quote chương trình chỉ tìm văn bản `a.*b`.

Ngược lại, với chức năng **“Tìm nguyên văn”**, không quote dữ liệu rồi đưa trực tiếp cho regex engine sẽ tạo hành vi sai hoặc lỗi `PatternSyntaxException`. Phải xác định đúng chế độ tìm kiếm dựa trên yêu cầu.

### 8.3. Nhầm quote với kiểm soát phân biệt chữ hoa/thường

`Pattern.quote` chỉ xử lý cú pháp regex, không tự chuyển chữ hoa/thường. Mặc định, tìm `"Java"` không đồng nghĩa tìm `"java"`.

Nếu cần tìm không phân biệt chữ hoa/thường bằng regex, có thể dùng cờ thích hợp:

```java
Pattern pattern = Pattern.compile(
        Pattern.quote("Java"),
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

System.out.println(pattern.matcher("java").matches()); // true
```

Các cờ này ảnh hưởng quy tắc khớp, còn `Pattern.quote` vẫn chịu trách nhiệm xem đầu vào là literal. Với yêu cầu Unicode phức tạp, cần kiểm tra rõ chính sách so sánh văn bản của ứng dụng.

### 8.4. Đầu vào `null` hoặc rỗng

`Pattern.quote(null)` không tạo ra regex hợp lệ để tìm, mà ném `NullPointerException`. Với chuỗi rỗng `""`, việc quote là hợp lệ, nhưng mẫu rỗng có thể khớp ở các vị trí độ dài 0; ví dụ `find()` có thể trả về `true` ngay cả khi người dùng chưa gõ từ khóa.

Nếu chức năng yêu cầu từ khóa có ít nhất một ký tự, hãy kiểm tra trước:

```java
if (keyword == null || keyword.isEmpty()) {
    throw new IllegalArgumentException("Từ khóa phải có nội dung");
}
Pattern pattern = Pattern.compile(Pattern.quote(keyword));
```

Không tùy tiện gọi `trim()` hoặc `strip()` nếu khoảng trắng đầu/cuối là một phần dữ liệu người dùng thực sự muốn tìm.

### 8.5. Quote pattern không điều khiển cách split giữ phần tử rỗng

`Pattern.quote("|")` quyết định **dấu `|` là dấu phân cách literal**; nó không quyết định mảng kết quả có giữ trường rỗng cuối hay không.

```java
String s = "a|b||";
System.out.println(Arrays.toString(s.split(Pattern.quote("|"))));
// [a, b]

System.out.println(Arrays.toString(s.split(Pattern.quote("|"), -1)));
// [a, b, , ]
```

Ở đây `-1` là lựa chọn của `String.split` để giữ mọi phần tử rỗng cuối. Ví dụ này cần đặt trong phương thức có `import java.util.Arrays;` và `import java.util.regex.Pattern;`.

### 8.6. Biên dịch Pattern nhiều lần không cần thiết

`Pattern` là đối tượng đã biên dịch regex và có thể được dùng lại cho nhiều lần so khớp. Nếu cùng một từ khóa được tìm nhiều lần, có thể biên dịch một lần rồi gọi `pattern.matcher(text)` cho từng chuỗi:

```java
Pattern pattern = Pattern.compile(Pattern.quote(keyword));

for (String line : lines) {
    if (pattern.matcher(line).find()) {
        System.out.println(line);
    }
}
```

Đây là đoạn mã minh họa giả sử `keyword` là `String` và `lines` là tập hợp dòng văn bản đã có. `Pattern` có thể tái sử dụng; không chia sẻ cùng một đối tượng `Matcher` đang thay đổi trạng thái giữa các luồng mà không đồng bộ.

## 9. Tự kiểm tra

Hãy xác định câu lệnh nào trả về `true`:

| Biểu thức | Kết quả | Giải thích |
| --- | --- | --- |
| `Pattern.compile("a.b").matcher("axb").matches()` | `true` | Dấu chấm là toán tử regex. |
| `Pattern.compile(Pattern.quote("a.b")).matcher("axb").matches()` | `false` | Dấu chấm phải là ký tự thật. |
| `Pattern.compile(Pattern.quote("a.b")).matcher("x-a.b-y").find()` | `true` | `find()` tìm trong một phần của chuỗi. |
| `Pattern.compile(Pattern.quote("a.b")).matcher("x-a.b-y").matches()` | `false` | `matches()` yêu cầu toàn bộ chuỗi khớp. |
| `Pattern.compile(Pattern.quote("[0]")).matcher("[0]").matches()` | `true` | Dấu ngoặc vuông được hiểu literal. |
| `Pattern.compile(Pattern.quote("a|b")).matcher("a").matches()` | `false` | `|` không còn là toán tử lựa chọn. |

Một bài tập thực hành: xây dựng chức năng nhận một từ khóa bất kỳ từ người dùng, tìm mọi vị trí xuất hiện của từ khóa đó trong một câu rồi in `matcher.start()` và `matcher.end()`. Thử lần lượt với `a.b`, `[0]`, `C:\temp`, `.*` và `\E`. Nếu đây là chế độ tìm nguyên văn, cả năm chuỗi phải được xử lý như dữ liệu, không phải mã lệnh regex.

## 10. Ghi nhớ

- **`Pattern.quote(input)` biến dữ liệu thành regex literal**; dùng khi API đang yêu cầu regex nhưng người dùng muốn tìm nguyên văn.
- **`find()` tìm vùng khớp**, **`matches()` khớp toàn bộ đầu vào**; quote không thay đổi khác biệt này.
- Khi cần kết hợp dữ liệu nguyên văn với cú pháp regex, chỉ quote **phần dữ liệu**, ví dụ `"^" + Pattern.quote(label) + "$"`.
- Với chuỗi **thay thế**, dùng `Matcher.quoteReplacement(replacement)` nếu gọi `Matcher.replaceAll`; `Pattern.quote` không giải quyết vấn đề replacement.
- Với tìm hoặc thay thế literal đơn giản, ưu tiên `String.contains`, `String.indexOf` hoặc `String.replace` vì chúng diễn đạt trực tiếp ý định.
- `Pattern.LITERAL` là lựa chọn hợp lệ khi muốn coi **toàn bộ pattern** là văn bản nguyên văn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-007">Cờ CASE_INSENSITIVE, UNICODE_CASE và UNICODE_CHARACTER_CLASS</a>

<details>
<summary>Xem chi tiết</summary>

# CASE_INSENSITIVE, UNICODE_CASE và UNICODE_CHARACTER_CLASS trong Java Regex

## 1. Vì sao cần phân biệt ba cờ?

Trong Java, `java.util.regex.Pattern` biên dịch một biểu thức chính quy (regular expression), còn `Matcher` kiểm tra biểu thức đó trên chuỗi. Tham số `flags` của `Pattern.compile(regex, flags)` thay đổi cách engine hiểu phép so khớp. Ba cờ dễ bị nhầm lẫn:

- `Pattern.CASE_INSENSITIVE`: bật **so khớp không phân biệt hoa/thường**; mặc định phần xử lý hoa/thường này theo US-ASCII.
- `Pattern.UNICODE_CASE`: khi đã bật so khớp không phân biệt hoa/thường, dùng **quy tắc Unicode case folding** thay vì chỉ US-ASCII. Bản thân cờ này **không bật** chế độ bỏ qua hoa/thường.
- `Pattern.UNICODE_CHARACTER_CLASS`: cho các lớp ký tự định nghĩa sẵn và các lớp POSIX như `\d`, `\w`, `\s`, `\p{Alpha}`, `\p{Digit}` hoạt động theo định nghĩa Unicode. Cờ này **ngầm bật UNICODE_CASE**, nhưng **không ngầm bật CASE_INSENSITIVE**.

Vì thế, “không phân biệt hoa/thường” và “nhận diện lớp ký tự Unicode” là hai vấn đề độc lập. Một regex có thể hỗ trợ chữ số Unicode mà vẫn phân biệt `é` với `É`; hoặc có thể so khớp `é` với `É` nhưng `\d` vẫn chỉ nhận các chữ số ASCII.

| Cờ Java | Ký hiệu ngay trong regex | Điều thay đổi trực tiếp | Điều không tự làm |
| --- | --- | --- | --- |
| `CASE_INSENSITIVE` | `(?i)` | Bỏ qua khác biệt hoa/thường khi so khớp, mặc định dựa trên US-ASCII | Không tự mở rộng `\d`, `\w`, `\s` thành Unicode |
| `UNICODE_CASE` | `(?u)` | Dùng Unicode case folding **khi chế độ không phân biệt hoa/thường đã bật** | Không tự bật `CASE_INSENSITIVE`; không đổi các lớp ký tự thành Unicode |
| `UNICODE_CHARACTER_CLASS` | `(?U)` (chữ `U` viết hoa) | Áp dụng quy tắc Unicode cho các lớp ký tự định nghĩa sẵn/POSIX; đồng thời ngầm bật `UNICODE_CASE` | Không tự bật `CASE_INSENSITIVE`; không xóa dấu hay chuẩn hóa Unicode |

## 2. CASE_INSENSITIVE: không phân biệt hoa/thường kiểu mặc định

`Pattern.CASE_INSENSITIVE` làm cho `"java"` khớp `"JAVA"`. Nó cũng tác động đến ký tự chữ cái trong literal, tập ký tự và các nhóm regex thích hợp. Tuy nhiên, theo hợp đồng của `Pattern`, khi dùng riêng cờ này, so khớp không phân biệt hoa/thường mặc định giả định **US-ASCII**. Ví dụ với `é` và `É` sẽ thể hiện giới hạn:

```java
import java.util.regex.Pattern;

boolean ascii = Pattern.compile("java", Pattern.CASE_INSENSITIVE)
                       .matcher("JAVA").matches();

boolean accent = Pattern.compile("\u00E9", Pattern.CASE_INSENSITIVE)
                        .matcher("\u00C9").matches();

System.out.println(ascii);  // true
System.out.println(accent); // false
```

Hai đoạn trên là các câu lệnh minh họa (có thể chạy trong `jshell`), không phải một lớp Java độc lập. Phương thức `matches()` yêu cầu **toàn bộ đầu vào** khớp regex; `find()` tìm một đoạn con phù hợp. Khác biệt giữa hai phương thức này không liên quan đến cờ.

Sử dụng `(?i)` khi muốn khai báo cờ ngay trong regex, chẳng hạn `Pattern.compile("(?i)java")`. Trong mã Java, hai cách sau tương đương về ý nghĩa với regex này:

```java
Pattern.compile("java", Pattern.CASE_INSENSITIVE);
Pattern.compile("(?i)java");
```

## 3. UNICODE_CASE: làm Unicode case folding hoạt động cùng CASE_INSENSITIVE

Cờ `Pattern.UNICODE_CASE` điều chỉnh **cách** đối chiếu hoa/thường khi `CASE_INSENSITIVE` đang có hiệu lực. Nó không tự biến biểu thức thành case-insensitive. Với hai ký tự `é` (U+00E9) và `É` (U+00C9):

| Bộ cờ | `"é"` có khớp `"É"` không? | Vì sao? |
| --- | --- | --- |
| Không cờ | Không | So khớp có phân biệt hoa/thường |
| Chỉ `CASE_INSENSITIVE` | Không | Bỏ qua hoa/thường theo US-ASCII mặc định |
| Chỉ `UNICODE_CASE` | Không | Chưa bật bỏ qua hoa/thường |
| `CASE_INSENSITIVE \| UNICODE_CASE` | Có | Bật bỏ qua hoa/thường và dùng Unicode case folding |

Cách kết hợp đúng là dùng phép OR bitwise `|`:

```java
Pattern p = Pattern.compile(
    "\u00E9",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
);
boolean found = p.matcher("\u00C9").matches(); // true
```

Hoặc đặt cả hai cờ trong biểu thức: `(?iu)é`. Chữ `i` bật không phân biệt hoa/thường; chữ `u` chọn cách xử lý Unicode. Dùng `(?u)` một mình không đạt yêu cầu này.

**Ranh giới cần nhớ:** Unicode case folding không đồng nghĩa với xóa dấu, chuyển tự hay chuẩn hóa văn bản. Ví dụ `é` và `e` là hai chữ khác nhau; chuỗi một ký tự `é` (dạng dựng sẵn) và chuỗi `e` + dấu sắc kết hợp (dạng phân rã) cũng không được tự động xem là tương đương chỉ vì bật `UNICODE_CASE`. Khi cần cùng một chuẩn biểu diễn, cân nhắc `java.text.Normalizer` với NFC/NFD theo yêu cầu; việc chuẩn hóa phải được thiết kế riêng.

Case folding trong regex cũng không thay thế phép biến đổi chữ hoa/thường phụ thuộc ngôn ngữ như `String.toLowerCase(Locale)`. Với các luật xử lý tên riêng, thứ tự từ điển hoặc ngôn ngữ cụ thể, cần kiểm thử dữ liệu thực tế thay vì giả định một phép so khớp có thể đáp ứng mọi quy tắc bản địa hóa.

## 4. UNICODE_CHARACTER_CLASS: mở rộng ý nghĩa của lớp ký tự

Không bật `UNICODE_CHARACTER_CLASS`, một số lớp ký tự định nghĩa sẵn thường được hiểu theo phạm vi US-ASCII:

| Regex | Mặc định (US-ASCII) | Khi bật `UNICODE_CHARACTER_CLASS` |
| --- | --- | --- |
| `\d` | Chữ số `0` đến `9` | Chữ số thập phân Unicode, tương ứng `\p{IsDigit}` |
| `\w` | `[a-zA-Z_0-9]` | Gồm chữ cái Unicode, dấu kết hợp, chữ số, dấu nối thuộc lớp connector và join control theo định nghĩa Java |
| `\s` | Các khoảng trắng ASCII tiêu chuẩn như space, tab, xuống dòng | Ký tự khoảng trắng theo Unicode `White_Space` |
| `\p{Alpha}` | Chữ cái ASCII | Ký tự có thuộc tính Unicode `Alphabetic` |
| `\p{Digit}` | Chữ số ASCII | Chữ số thập phân Unicode |
| `\p{ASCII}` | Vẫn chỉ ASCII | Vẫn chỉ ASCII; cờ không biến mọi lớp thành “mọi ký tự” |

Ví dụ `"١٢٣"` gồm ba chữ số Arabic-Indic: `\d+` mặc định không khớp toàn bộ, nhưng sẽ khớp khi bật `UNICODE_CHARACTER_CLASS`. Tương tự, `"café"` không khớp toàn bộ `\w+` mặc định, nhưng khớp khi bật cờ này.

```java
Pattern asciiDigits = Pattern.compile("\\d+");
Pattern unicodeDigits = Pattern.compile(
    "\\d+", Pattern.UNICODE_CHARACTER_CLASS
);

String digits = "\u0661\u0662\u0663"; // ١٢٣
System.out.println(asciiDigits.matcher(digits).matches());   // false
System.out.println(unicodeDigits.matcher(digits).matches()); // true
```

**Điểm phân biệt quan trọng:** `UNICODE_CASE` **không** làm `\d` hay `\w` mở rộng phạm vi; muốn đổi các lớp đó thì dùng `UNICODE_CHARACTER_CLASS`. Ngược lại, `UNICODE_CHARACTER_CLASS` có ngầm bật `UNICODE_CASE` nhưng vẫn cần `CASE_INSENSITIVE` nếu muốn bỏ qua hoa/thường:

- `Pattern.compile("é", Pattern.UNICODE_CHARACTER_CLASS)` vẫn không khớp `"É"` với `matches()`.
- `Pattern.compile("é", Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE)` sẽ khớp `"É"`.
- `Pattern.compile("(?U)\\w+")` nhận dạng `\w` theo Unicode, còn `Pattern.compile("(?iU)é")` vừa dùng Unicode character classes vừa bỏ qua hoa/thường.

Các lớp như `\p{L}` (ký tự thuộc nhóm Letter của Unicode) và `\p{Nd}` (Decimal_Number) **đã là các thuộc tính Unicode được gọi tường minh**, kể cả khi chưa bật `UNICODE_CHARACTER_CLASS`. Vì vậy `\p{L}+` là một lựa chọn rõ nghĩa khi chỉ cần chữ cái Unicode, không nhất thiết phải bật `(?U)` cho toàn regex.

Lưu ý rằng khái niệm “word” trong `\w` khi bật Unicode **rộng hơn chữ cái đơn thuần**. Nó có thể nhận các dấu kết hợp và một số ký tự điều khiển nối chữ. Nếu kiểm tra tên người, mã tài khoản hay định danh, đừng mặc nhiên coi `(?U)\w+` là bộ quy tắc nghiệp vụ hoàn chỉnh.

## 5. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu chương trình UTF-8 vào `RegexUnicodeFlagsDemo.java`. Các chuỗi `\u00E9`, `\u00C9` và `\u0661`–`\u0663` là cách viết Unicode escape trong mã nguồn Java để ví dụ tái hiện được mà không phụ thuộc bàn phím:

```java
import java.util.regex.Pattern;

public class RegexUnicodeFlagsDemo {
    private static void check(String name, String regex, String input, int flags) {
        boolean ok = Pattern.compile(regex, flags).matcher(input).matches();
        System.out.println(name + " = " + ok);
    }

    public static void main(String[] args) {
        check("ASCII letters", "java", "JAVA", Pattern.CASE_INSENSITIVE);
        check("Accent, i only", "\u00E9", "\u00C9", Pattern.CASE_INSENSITIVE);
        check("Accent, u only", "\u00E9", "\u00C9", Pattern.UNICODE_CASE);
        check("Accent, iu", "\u00E9", "\u00C9",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        check("Arabic digits, default", "\\d+", "\u0661\u0662\u0663", 0);
        check("Arabic digits, U", "\\d+", "\u0661\u0662\u0663",
                Pattern.UNICODE_CHARACTER_CLASS);

        check("Unicode word, default", "\\w+", "caf\u00E9", 0);
        check("Unicode word, U", "\\w+", "caf\u00E9",
                Pattern.UNICODE_CHARACTER_CLASS);

        check("Accent, U only", "\u00E9", "\u00C9",
                Pattern.UNICODE_CHARACTER_CLASS);
        check("Accent, iU", "\u00E9", "\u00C9",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

        check("Explicit letter class", "\\p{L}+", "caf\u00E9", 0);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 RegexUnicodeFlagsDemo.java
java RegexUnicodeFlagsDemo
```

Kết quả chuẩn:

```text
ASCII letters = true
Accent, i only = false
Accent, u only = false
Accent, iu = true
Arabic digits, default = false
Arabic digits, U = true
Unicode word, default = false
Unicode word, U = true
Accent, U only = false
Accent, iU = true
Explicit letter class = true
```

Đọc kết quả theo hai nhóm: bốn dòng đầu cho thấy **bật `i` và `u` cùng nhau** mới so khớp chữ hoa/thường Unicode; các dòng chữ số/word cho thấy **`U`** thay đổi lớp ký tự. Dòng `Accent, U only` xác nhận `U` không tự bật `i`; dòng `Accent, iU` cho thấy `U` đã ngầm có `u`. Dòng cuối cho thấy có thể gọi lớp thuộc tính Unicode tường minh mà không bật `U`.

## 6. Chọn cách cấu hình cờ và phạm vi áp dụng

Có ba cách phổ biến:

**Dùng cờ khi biên dịch `Pattern`:** phù hợp khi tạo pattern tái sử dụng nhiều lần và muốn cấu hình tách khỏi regex.

```java
Pattern search = Pattern.compile(
    "cafe",
    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
);
boolean result = search.matcher("CAFE").matches(); // true
```

**Dùng inline flag ở đầu regex:** phù hợp với API chỉ nhận biểu thức chuỗi, ví dụ `String.matches(String regex)`:

```java
boolean a = "\u00C9".matches("(?iu)\u00E9");  // true
boolean b = "caf\u00E9".matches("(?U)\\w+"); // true
```

**Giới hạn cờ cho một nhóm:** Java hỗ trợ cú pháp `(?flags:...)`. Ví dụ `(?i:[a-z]+)` chỉ bật chế độ không phân biệt hoa/thường bên trong nhóm; `(?iu:é)` bật cả `i` và `u` cho nhóm đó. Khi cần một phần quy tắc giữ phân biệt hoa/thường, scoped flags giúp diễn đạt phạm vi chính xác. Có thể tắt cờ inline phù hợp bằng dạng `(?-i:...)`.

Chữ trong embedded flag phân biệt hoa/thường: `u` viết thường chỉ chế độ Unicode-aware case folding; `U` viết hoa bật Unicode character classes. Đừng đọc `(?u)` và `(?U)` là cùng một tùy chọn.

**Quy tắc về dấu gạch chéo trong Java:** chuỗi Java `"\\d+"` biểu diễn regex `\d+` (vì Java cần escape ký tự `\`). Do đó `"(?U)\\d+"` biểu diễn regex `(?U)\d+`. Viết `"\d+"` trực tiếp trong Java string là sai cú pháp escape của Java. Nếu regex lấy từ cấu hình hoặc chuỗi đã được tạo sẵn, số lần escape phụ thuộc lớp biểu diễn chứ không thay đổi ý nghĩa của `\d`.

## 7. Những lựa chọn thực tế và lỗi dễ gặp

| Yêu cầu | Lựa chọn tiêu biểu | Lý do |
| --- | --- | --- |
| Mã chỉ gồm chữ Latin ASCII và số ASCII, không phân biệt hoa/thường | `Pattern.compile("[a-z0-9_-]+", Pattern.CASE_INSENSITIVE)` | Giữ tập ký tự đầu vào hẹp, rõ ràng |
| So khớp chữ có dấu mà không phân biệt hoa/thường | `CASE_INSENSITIVE \| UNICODE_CASE` hoặc `(?iu)` | Cần cả việc bật case-insensitive lẫn Unicode folding |
| Nhận chữ số thập phân từ các hệ chữ viết Unicode bằng `\d` | `(?U)\d+` | Mở rộng lớp `\d` theo Unicode |
| Cho phép chữ cái Unicode nhưng kiểm soát rõ tập ký tự | `\p{L}+` hoặc một tập `\p{L}`, `\p{M}`, `\p{Nd}` được thiết kế riêng | Không phụ thuộc việc `\w` có chấp nhận thêm dấu nối và ký tự đặc biệt |
| Chỉ nhận ký tự số từ `0` đến `9` | `[0-9]+` | Ràng buộc ASCII rõ ràng ngay cả khi pattern có `(?U)` |

Các lỗi cần tránh:

1. **Nghĩ `UNICODE_CASE` tự bỏ qua hoa/thường.** Cần kết hợp `CASE_INSENSITIVE`.
2. **Nghĩ `CASE_INSENSITIVE` tự khiến `\w` nhận tất cả chữ Unicode.** Cờ thay đổi so khớp hoa/thường, không phải lớp ký tự.
3. **Nghĩ `(?U)` tự làm mọi so khớp case-insensitive.** `U` ngầm có `u` nhưng không có `i`.
4. **Dùng `\d` mà quên yêu cầu nghiệp vụ.** `(?U)\d` nhận chữ số Unicode; `[0-9]` mới nói rõ “chỉ số ASCII”. Điều này quan trọng khi chuỗi phải tương thích với giao thức, định dạng hay hệ thống chỉ nhận ASCII.
5. **Dùng `(?U)\w+` như bộ lọc tên/username tuyệt đối.** Hãy định nghĩa rõ tập ký tự, độ dài, chuẩn hóa và các quy tắc riêng cho dữ liệu đầu vào.
6. **Nhầm hoa/thường Unicode với bỏ dấu hoặc so sánh ngôn ngữ.** `"é"` và `"e"` vẫn khác nhau; normalization và collation là những chủ đề độc lập.
7. **Quên phân biệt `matches()` và `find()`, hoặc quên Java string escaping.** Một pattern đúng vẫn có thể cho kết quả khác kỳ vọng nếu kiểm tra toàn chuỗi thay vì đoạn con hoặc escape sai.

Hiệu năng: tài liệu JDK lưu ý `CASE_INSENSITIVE` có thể thêm ít chi phí, còn `UNICODE_CASE` và `UNICODE_CHARACTER_CLASS` có thể tăng chi phí xử lý. Chọn cờ theo yêu cầu thực tế, tái sử dụng `Pattern` đã biên dịch khi dùng lặp lại, và đo đạc bằng dữ liệu thực nếu hiệu năng quan trọng. Kết quả cụ thể còn phụ thuộc phiên bản JDK và phiên bản dữ liệu Unicode mà JDK hỗ trợ.

## 8. Tự kiểm tra

**Câu 1.** Vì sao `Pattern.compile("\\d+")` không khớp toàn bộ chuỗi `"١٢٣"` nhưng `Pattern.compile("(?U)\\d+")` lại khớp?

**Giải thích:** `\d` mặc định là chữ số ASCII; `(?U)` chuyển lớp này sang phạm vi chữ số thập phân Unicode. Hai mẫu đều dùng `matches()` để kiểm tra toàn chuỗi.

**Câu 2.** Nếu đã bật `UNICODE_CHARACTER_CLASS`, liệu `"é"` có tự khớp `"É"` hay không?

**Giải thích:** Không. Cờ `U` ngầm bật `UNICODE_CASE` để định nghĩa quy tắc folding nhưng không bật `CASE_INSENSITIVE`. Muốn bỏ qua hoa/thường, thêm `CASE_INSENSITIVE` hoặc dùng `(?iU)`.

**Câu 3.** Khi chỉ muốn nhận số ASCII, nên viết `\\d+` với `(?U)` hay `[0-9]+`?

**Giải thích:** `[0-9]+` nêu rõ tập ký tự ASCII cần chấp nhận, ổn định về phạm vi khi pattern được dùng chung với các cờ Unicode khác.

**Tài liệu tham khảo:** [Java SE 17 — `java.util.regex.Pattern`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html), đặc biệt phần “Predefined Character classes”, “Unicode Support” và mô tả ba hằng `CASE_INSENSITIVE`, `UNICODE_CASE`, `UNICODE_CHARACTER_CLASS`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-008">Ký tự Unicode, ranh giới từ và cụm ký tự trong regex</a>

<details>
<summary>Xem chi tiết</summary>

# Ký tự Unicode, ranh giới từ và cụm ký tự trong Java regex

## 1. Vì sao regex với Unicode khác regex chỉ xử lý ASCII?

Một `Java String` lưu chuỗi dưới dạng các **đơn vị mã UTF-16** (`char`). Trong Unicode, cần phân biệt ba khái niệm:

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Code unit** | Một đơn vị UTF-16, là thứ `String.length()` đếm. | Emoji `😀` (U+1F600) cần **2** `char` (surrogate pair). |
| **Code point** | Một giá trị ký tự Unicode, có thể cần 1 hoặc 2 code unit UTF-16. | `e` + dấu sắc kết hợp U+0301 là **2** code point. |
| **Extended grapheme cluster** | Một cụm code point thường được người đọc cảm nhận là một đơn vị ký tự. | `e` + U+0301 thường là **1** cụm; emoji `👩‍💻` là một cụm ghép bằng ZWJ. |

Một ký tự nhìn thấy trên màn hình **không nhất thiết tương ứng với một `char` hay một code point**. Ngay cả một grapheme cluster cũng không bảo đảm chiếm đúng một ô hiển thị, vì độ rộng và cách kết xuất phụ thuộc font/giao diện.

Trong `java.util.regex.Pattern`, regex hiểu các thuộc tính Unicode qua `\p{...}` và `\P{...}`; nhận diện ranh giới từ bằng `\b`, `\B`; và hỗ trợ **extended grapheme cluster** bằng `\X` cùng ranh giới `\b{g}` (Java 9+). Đây là những khái niệm khác nhau và phục vụ mục đích khác nhau.

## 2. Viết regex trong chuỗi Java: hai lớp escape

Regex là một ngôn ngữ nằm **bên trong** chuỗi Java. Khi viết trực tiếp bằng Java string literal, dấu gạch chéo ngược trong regex phải được escape thêm lần nữa:

| Ý định | Regex | String literal trong Java |
| --- | --- | --- |
| Một chữ cái Unicode | `\p{L}` | `"\\p{L}"` |
| Ranh giới từ | `\b` | `"\\b"` |
| Một cụm grapheme | `\X` | `"\\X"` |
| Ranh giới grapheme | `\b{g}` | `"\\b{g}"` |
| Code point U+1F600 | `\x{1F600}` | `"\\x{1F600}"` |

**Cẩn thận:** `"\b"` trong mã Java biểu diễn **ký tự backspace**, không phải regex word boundary. Tương tự, `"\p{L}"` không phải string literal Java hợp lệ vì `\p` không phải escape của Java.

`Pattern.compile(regex).matcher(text).find()` tìm một đoạn khớp; `matcher.matches()` yêu cầu **toàn bộ vùng tìm kiếm** khớp. Với biểu thức dùng thường xuyên, nên biên dịch `Pattern` một lần để tái sử dụng.

## 3. Chọn ký tự theo thuộc tính Unicode

### 3.1. General category và nhãn Unicode

`\p{...}` khớp ký tự mang thuộc tính; `\P{...}` khớp ký tự **không** có thuộc tính tương ứng.

| Biểu thức | Ý nghĩa | Tình huống dùng |
| --- | --- | --- |
| `\p{L}` | Nhóm **Letter**: chữ cái thuộc nhiều ngôn ngữ. | Tìm chữ cái, thay vì giới hạn `[A-Za-z]`. |
| `\p{Lu}`, `\p{Ll}` | Chữ hoa / chữ thường theo Unicode category. | Kiểm tra loại chữ. |
| `\p{M}` | **Mark**, gồm các dấu kết hợp (`Mn`, `Mc`, `Me`). | Giữ dấu khi xử lý chuỗi ở dạng decomposed. |
| `\p{N}`, `\p{Nd}` | Số nói chung / chữ số thập phân. | Phân biệt `Ⅳ` thuộc nhóm số với chữ số thập phân. |
| `\p{Sc}` | Ký hiệu tiền tệ. | Phát hiện các ký hiệu như `€` hay `$`. |
| `\p{IsAlphabetic}` | Thuộc tính nhị phân Alphabetic. | Định nghĩa rộng hơn một số kiểu ký tự loại `L`. |
| `\P{L}` | Một code point không thuộc nhóm chữ cái. | Tìm ký tự nằm ngoài nhóm Letter. |

Ví dụ:

```java
Pattern letters = Pattern.compile("\\p{L}+");
Pattern lettersAndMarks = Pattern.compile("\\p{L}[\\p{L}\\p{M}]*");

System.out.println(letters.matcher("Việt").matches());       // true
System.out.println(letters.matcher("e\u0301").matches());     // false
System.out.println(lettersAndMarks.matcher("e\u0301").matches()); // true
```

`"Việt"` ở ví dụ đầu chứa chữ có dấu **dạng precomposed**. Còn `"e\u0301"` chứa chữ `e` và một **combining acute accent**. Nếu chỉ dùng `\p{L}+`, dấu kết hợp không thuộc nhóm Letter nên `matches()` trả `false`. Mẫu `\p{L}[\p{L}\p{M}]*` yêu cầu mở đầu bằng chữ và cho phép dấu sau đó; đây là ví dụ kỹ thuật chứ chưa phải định nghĩa đầy đủ của một từ trong ngôn ngữ tự nhiên.

### 3.2. Script khác block

- **Script** (`\p{IsLatin}`, `\p{sc=Greek}`, `\p{script=Hiragana}`) chỉ hệ chữ mà ký tự được gán.
- **Block** (`\p{InGreek}`, `\p{blk=Greek}`) là một **khoảng mã** Unicode, có thể chứa ký tự khác script hoặc ký tự chưa được gán.
- **Category** (`\p{L}`, `\p{Lu}`) phân loại theo tính chất ký tự, độc lập với script.

Ví dụ `\p{sc=Greek}+` diễn đạt **script Greek**; `\p{InGreek}+` diễn đạt các code point nằm trong **block Greek**. Hai tập này không thể thay thế cho nhau trong mọi tình huống. Khi lọc văn bản tiếng Việt, `\p{IsLatin}` cũng không tự động bảo đảm bao gồm mọi dấu kết hợp đi kèm, vì mark có thể mang script `Inherited`.

Java regex hỗ trợ phép giao/trừ tập trong character class, ví dụ `[\p{L}&&[^\p{Lu}]]` là ký tự loại Letter nhưng không thuộc category Uppercase Letter.

## 4. `\w`, `\d`, `\s` và chế độ Unicode

Mặc định theo Java 17, các lớp dựng sẵn chủ yếu dựa trên ASCII: `\w` tương đương `[a-zA-Z_0-9]`, `\d` là `[0-9]`, `\s` gồm các khoảng trắng ASCII thông dụng. Vì vậy `\w+` **không** tự động nhận toàn bộ chữ tiếng Việt.

Bật cờ `Pattern.UNICODE_CHARACTER_CLASS` (inline là `(?U)`) khi muốn các lớp dựng sẵn và các lớp POSIX dùng quy tắc Unicode:

```java
Pattern asciiWord = Pattern.compile("\\w+");
Pattern unicodeWord = Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS);
Pattern inlineUnicodeWord = Pattern.compile("(?U)\\w+");

System.out.println(asciiWord.matcher("đèn").matches());        // false
System.out.println(unicodeWord.matcher("đèn").matches());      // true
System.out.println(inlineUnicodeWord.matcher("đèn").matches()); // true
```

Trong Unicode mode, `\w` bao gồm chữ cái alphabetic, chữ số thập phân, combining marks, connector punctuation và một số ký tự điều khiển nối (`Join_Control`). Do đó `\w` **không đồng nghĩa** với “chữ cái”, “identifier Java hợp lệ” hay “từ có nghĩa”. Tùy yêu cầu:

- Chỉ chữ cái theo category: `\p{L}+`.
- Chữ kèm dấu kết hợp: `\p{L}[\p{L}\p{M}]*`.
- Token kiểu word, có thể chứa số, gạch dưới và mark: `(?U)\w+`.
- Kiểm tra identifier Java thực sự: cân nhắc `Character.isJavaIdentifierStart`/`isJavaIdentifierPart` trên **code point**, hoặc parser tương ứng; regex `\w+` chưa đủ.

Bật `UNICODE_CHARACTER_CLASS` cũng hàm ý `UNICODE_CASE`. Ngược lại, `Pattern.UNICODE_CASE` (inline `(?u)`) chỉ điều chỉnh cách so khớp **không phân biệt hoa/thường** khi kết hợp `CASE_INSENSITIVE`; `(?u)` tự nó không biến `\w` thành lớp Unicode. Phân biệt `u` chữ thường và `U` chữ hoa.

## 5. Ranh giới từ `\b` và `\B`

`\b` là **zero-width assertion**: kiểm tra một *vị trí* ở ranh giới từ, không tiêu thụ ký tự. `\B` kiểm tra vị trí **không** phải ranh giới từ.

```java
String text = "café_42, đèn!";
Matcher words = Pattern.compile("(?U)\\b\\w+\\b").matcher(text);
while (words.find()) {
    System.out.println(words.group());
}
```

Kết quả:

```text
café_42
đèn
```

Giải thích: `(?U)` chọn ngữ nghĩa Unicode của `\w`; `\b` ở hai đầu kiểm tra ranh giới, còn `\w+` **tiêu thụ** các ký tự của token. Gạch dưới và chữ số có thể nằm trong cùng một token, nên `café_42` là một kết quả. Muốn chỉ lấy chữ và dấu, cần mô tả tập ký tự riêng, không chỉ bao quanh `\w+` bằng `\b`.

**Không nhầm `\b` với trình phân tích từ cho ngôn ngữ tự nhiên**: dấu gạch nối, dấu nháy, hashtag, emoji, tiếng Thái không có khoảng trắng tách từ, hoặc tiếng Việt nhiều âm tiết đều cần quy tắc phân đoạn phù hợp. `\b` không quyết định một “từ” theo từ điển hay ngữ pháp. Sử dụng `(?U)` nhất quán giúp tránh các giả định ASCII không mong muốn khi ghép `\b` với `\w`; đừng dựa vào việc từng phiên bản JDK xử lý chi tiết ranh giới giống hệt một tokenizer ngôn ngữ.

## 6. Cụm grapheme với `\X` và `\b{g}`

Một **extended grapheme cluster** có thể gồm một hoặc nhiều code point: chữ + dấu kết hợp, chuỗi emoji ghép bằng zero-width joiner (ZWJ), hoặc cặp regional indicators tạo lá cờ.

| Regex | Vai trò | Có tiêu thụ ký tự? |
| --- | --- | --- |
| `\X` | Khớp **một cụm grapheme mở rộng**. | Có |
| `\b{g}` | Kiểm tra **ranh giới giữa các cụm grapheme**. | Không |
| `\b` | Kiểm tra ranh giới **word**. | Không |
| `.` | Khớp một đơn vị regex; **không** phải cách đáng tin để đếm cụm grapheme. | Có |

Ví dụ chuỗi `"e\u0301👩‍💻"` gồm **7 UTF-16 code unit**, **5 code point**, nhưng chỉ **2 grapheme cluster**: cụm `e + U+0301` và cụm emoji `woman technologist`. Với regex `\X`, hai lần `find()` trả hai cụm. `\b{g}` xác định vị trí UTF-16 ở các offset `0`, `2`, `7`. Trái lại, `\b` diễn đạt ranh giới từ và không dùng thay `\b{g}`.

Có thể dùng `\X` khi muốn đi qua chuỗi theo các đơn vị ký tự mà người dùng thường nhận biết, chẳng hạn kiểm tra giới hạn *số cụm grapheme* hoặc cắt chuỗi ở ranh giới cluster. Tuy vậy, phân cụm Unicode không thay thế xử lý hiển thị/phông chữ, cũng không bảo đảm một cluster là “một chữ” của ngôn ngữ học.

### 6.1. Vị trí `Matcher.start()`/`end()` vẫn là UTF-16

Ngay cả khi regex `\X` ghép cả emoji thành một cụm, các chỉ số `Matcher.start()`, `Matcher.end()`, `String.substring(...)` vẫn tính theo **UTF-16 code unit**, với `end()` là giới hạn loại trừ (*exclusive*). Đừng tăng một offset bằng 1 rồi cho rằng đã đi qua một code point hay một grapheme.

Muốn đếm code point: `s.codePointCount(0, s.length())`. Muốn đi qua code point: `s.codePoints()` hoặc `s.offsetByCodePoints(...)`. Muốn đi qua **grapheme**: dùng `Pattern.compile("\\X").matcher(s)` với `find()`. Không cắt chuỗi ở giữa surrogate pair hoặc cụm dấu/emoji khi mục tiêu là giữ nguyên ký tự người dùng thấy.

## 7. Ví dụ Java 17+ độc lập, chạy được

Lưu **nguyên chương trình** sau thành `UnicodeRegexDemo.java`. Mã dùng escape Unicode trong những chuỗi đặc biệt để thể hiện rõ cấu trúc ký tự và chạy nhất quán ngay cả khi terminal không thuận tiện nhập emoji.

```java
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnicodeRegexDemo {
    public static void main(String[] args) {
        String decomposed = "e\u0301";
        String womanTechnologist = "\uD83D\uDC69\u200D\uD83D\uDCBB";
        String sample = decomposed + womanTechnologist;

        System.out.println("UTF-16 units: " + sample.length());
        System.out.println("Code points: "
                + sample.codePointCount(0, sample.length()));

        Matcher clusters = Pattern.compile("\\X").matcher(sample);
        while (clusters.find()) {
            System.out.printf("Grapheme: [%d,%d)%n",
                    clusters.start(), clusters.end());
        }

        Matcher boundaries = Pattern.compile("\\b{g}").matcher(sample);
        System.out.print("Grapheme boundaries:");
        while (boundaries.find()) {
            System.out.print(" " + boundaries.start());
        }
        System.out.println();

        System.out.println("ASCII word đ: "
                + Pattern.compile("\\w+").matcher("\u0111").matches());
        System.out.println("Unicode word đ: "
                + Pattern.compile("(?U)\\w+").matcher("\u0111").matches());
        System.out.println("NFD all letters: "
                + Pattern.compile("\\p{L}+").matcher(decomposed).matches());
        System.out.println("NFD letters and marks: "
                + Pattern.compile("\\p{L}[\\p{L}\\p{M}]*")
                        .matcher(decomposed).matches());

        Matcher words = Pattern.compile("(?U)\\b\\w+\\b")
                .matcher("caf\u00e9_42, \u0111\u00e8n!");
        while (words.find()) {
            System.out.println("Word: " + words.group());
        }

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);
        System.out.println("NFC equals é: "
                + normalized.equals("\u00e9"));
    }
}
```

Biên dịch/chạy:

```bash
javac UnicodeRegexDemo.java
java UnicodeRegexDemo
```

Kết quả chuẩn (UTF-8 output):

```text
UTF-16 units: 7
Code points: 5
Grapheme: [0,2)
Grapheme: [2,7)
Grapheme boundaries: 0 2 7
ASCII word đ: false
Unicode word đ: true
NFD all letters: false
NFD letters and marks: true
Word: café_42
Word: đèn
NFC equals é: true
```

Ba phép đếm **7 / 5 / 2** lần lượt nói về code unit, code point và grapheme cluster. Các offset `[0,2)`, `[2,7)` thể hiện hai cụm, không phải hai `char`. Kết quả token tiếng Việt chứng minh chế độ Unicode cho `\w` trong regex này; hai phép kiểm tra NFD chứng minh lý do cần xét combining marks.

## 8. Chuẩn hóa Unicode, so khớp hoa/thường và giới hạn

### NFC và NFD: hai cách biểu diễn cùng hình thức chữ

`"é"` có thể là U+00E9 (precomposed) hoặc U+0065 + U+0301 (decomposed). Hai chuỗi đó có thể nhìn giống nhau nhưng không bằng nhau qua `String.equals()` và không nhất thiết khớp cùng một pattern literal. Khi cần so khớp một cách nhất quán, **chuẩn hóa cả dữ liệu lẫn tiêu chí tìm kiếm**:

```java
import java.text.Normalizer;

String a = "\u00E9";
String b = "e\u0301";
String left = Normalizer.normalize(a, Normalizer.Form.NFC);
String right = Normalizer.normalize(b, Normalizer.Form.NFC);
System.out.println(left.equals(right)); // true
```

`Normalizer.Form.NFC` thường phù hợp khi muốn chuẩn hóa về dạng kết hợp. `NFD` tách canonical decomposition. Java còn có `Pattern.CANON_EQ` để đối sánh tương đương chuẩn tắc, nhưng cờ này có thể gây chi phí đáng kể; nên kiểm soát chuẩn hóa đầu vào trước nếu mục tiêu là so khớp văn bản theo cùng một dạng lưu trữ.

### Unicode case folding

`Pattern.CASE_INSENSITIVE` riêng lẻ mặc định ưu tiên ASCII; thêm `Pattern.UNICODE_CASE` (hoặc `(?iu)`) để đối sánh hoa/thường theo Unicode. Đây là **case-insensitive matching**, không phải bước chuẩn hóa NFC/NFD và cũng không phải bảo đảm mọi phép chuyển chữ hoa/thường đặc thù từng locale. Các thao tác chuẩn hóa/case folding có thể làm thay đổi chuỗi và vị trí, nên cần chọn thứ tự xử lý nhất quán trước khi ghi nhận offset.

### Những sai lầm hay gặp

1. Dùng `String.length()` để giới hạn “số ký tự người dùng nhìn thấy”; thực tế nó đếm UTF-16 code unit. Nếu sản phẩm cần giới hạn grapheme, hãy đếm `\X`.
2. Dùng `\w+` mặc định rồi kết luận chữ có dấu “không hợp lệ”; thay bằng `(?U)\w+` hoặc biểu thức `\p{...}` đúng chính sách.
3. Dùng `\p{L}+` cho chữ decomposed mà quên `\p{M}`; dẫn tới mất/loại dấu kết hợp.
4. Dùng `\b` như bộ tách từ tiếng Việt/Thái hay như ranh giới emoji; mục tiêu đó cần logic ngôn ngữ hoặc `\b{g}`/`\X` tương ứng.
5. Lấy chỉ số `Matcher.start()` làm vị trí code point hoặc grapheme; thực tế là UTF-16.
6. Cho rằng NFC, cờ `(?U)` hay `(?iu)` loại được **ký tự giả mạo (confusables)** hoặc mọi zero-width character; kiểm tra nhận dạng/định danh cần chính sách riêng.
7. Ghép thẳng dữ liệu người dùng vào regex; nếu tìm nguyên văn, dùng `Pattern.quote(userInput)` để tránh metacharacter hoạt động ngoài ý muốn.

## 9. Chọn công cụ theo mục tiêu

| Mục tiêu | Cách làm gợi ý |
| --- | --- |
| Chỉ kiểm tra một code point thuộc loại chữ/số/dấu | `\p{L}`, `\p{Nd}`, `\p{M}` hoặc API `Character`. |
| Tìm token gồm chữ, số, dấu kết hợp và gạch dưới | `(?U)\b\w+\b` rồi bổ sung quy tắc nghiệp vụ. |
| Duyệt từng code point | `String.codePoints()`; giữ nguyên cặp surrogate. |
| Duyệt từng cụm ký tự mở rộng | `Matcher` với `\X`. |
| Xác định vị trí giữa cụm grapheme | Assertion `\b{g}`. |
| So khớp chuỗi có thể khác dạng tổ hợp dấu | Chuẩn hóa `Normalizer` về cùng NFC/NFD trước. |
| Chia từ theo quy tắc ngôn ngữ cụ thể | `BreakIterator.getWordInstance(locale)` để bắt đầu, hoặc thư viện NLP/ICU4J khi cần phân đoạn chính xác theo ngôn ngữ. |

**Ghi nhớ:** **thuộc tính Unicode** trả lời “ký tự thuộc nhóm nào?”, **word boundary** trả lời “vị trí này có phải ranh giới token theo quy tắc regex?”, còn **grapheme cluster** trả lời “những code point nào đi cùng nhau thành một cụm ký tự?”. Việc chọn đúng tầng giúp regex xử lý chính xác tiếng Việt có dấu, emoji và các hệ chữ khác.

### Tài liệu tham khảo

- [Java SE 17 — `java.util.regex.Pattern`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html): Unicode Support, character classes, boundary matchers, `UNICODE_CHARACTER_CLASS`, `UNICODE_CASE`.
- [Java SE 17 — `java.text.Normalizer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/Normalizer.html): NFC/NFD và so khớp canonical equivalence.
- [Java SE 17 — `java.lang.String`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): UTF-16, `codePointCount` và offset theo code point.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="regular-expressions-009">Lựa chọn mẫu tránh khớp quá rộng hoặc quay lui quá nhiều</a>

<details>
<summary>Xem chi tiết</summary>

# Lựa chọn mẫu regex tránh khớp quá rộng hoặc quay lui quá nhiều trong Java

## 1. Mục tiêu

Biểu thức chính quy (regex) giúp kiểm tra, tìm kiếm và trích xuất văn bản, nhưng một mẫu “trông có vẻ chạy đúng” vẫn có thể gặp hai loại vấn đề khác nhau:

1. **Khớp quá rộng (overmatching):** regex chấp nhận hoặc nuốt những ký tự/đoạn văn bản nằm ngoài ý định. Đây trước hết là vấn đề **tính đúng đắn**.
2. **Quay lui quá nhiều (excessive backtracking):** regex engine thử quá nhiều cách ghép nhóm, lặp hoặc nhánh khi tìm kết quả; trường hợp xấu có thể tiêu tốn rất nhiều CPU. Đây là vấn đề **hiệu năng và an toàn**, đặc biệt khi input do người dùng kiểm soát.

Hai vấn đề có thể cùng xuất hiện nhưng không đồng nghĩa: một regex ngắn, ít quay lui vẫn có thể khớp sai phạm vi; một regex chỉ chấp nhận đúng tập chuỗi mong muốn vẫn có thể rất chậm trên đầu vào gần đúng nhưng thất bại.

Trong Java, **java.util.regex.Pattern** biên dịch regex và **Matcher** thực hiện phép khớp. Pattern dùng kiểu engine NFA có thứ tự thử nhánh và khả năng backtracking. Vì vậy, thiết kế regex cần xét **ngôn ngữ được chấp nhận, phạm vi tìm kiếm và đường đi khi thất bại**, không chỉ ví dụ thành công.

## 2. Bắt đầu từ điều kiện dữ liệu thật sự

Trước khi viết regex, trả lời bốn câu hỏi:

- **Muốn xác thực toàn bộ hay chỉ tìm một phần?** Dùng **matcher.matches()** để khớp toàn bộ region; **find()** để tìm đoạn con; **lookingAt()** để kiểm tra tiền tố. Một regex tốt vẫn cho kết quả sai nếu chọn nhầm phương thức.
- **Tập ký tự hợp lệ là gì?** Khi biết cấu trúc đầu vào, ưu tiên **[0-9]**, **[A-Za-z]**, **[^,]** hoặc lớp ký tự chính xác thay vì **.**.
- **Độ dài hợp lệ là bao nhiêu?** Dùng lượng từ có biên, ví dụ **{1,8}**, và kiểm tra giới hạn kích thước input tại ranh giới ứng dụng.
- **Dấu phân cách và ranh giới token ở đâu?** Chốt rõ đầu/cuối, dấu phẩy, dấu **>**, dấu xuống dòng hoặc ranh giới từ thay vì trông chờ **.* ** tự dừng ở vị trí hợp lý.

Ví dụ một mã tài khoản chỉ gồm 3–16 ký tự, ký tự đầu phải là chữ in hoa ASCII, các ký tự sau có thể là chữ in hoa, chữ số hoặc dấu gạch dưới:

~~~java
Pattern accountCode = Pattern.compile("[A-Z][A-Z0-9_]{2,15}");
boolean valid = accountCode.matcher("AB_7").matches();
~~~

Dùng **matches()** bảo đảm toàn bộ chuỗi phải thỏa mẫu. Dùng **find()** với cùng regex trên **"xAB_7!"** có thể tìm được một đoạn con hợp lệ, nhưng điều đó **không** làm cả mã đầu vào hợp lệ.

**Một nguyên tắc quan trọng:** regex nên mô tả ngữ pháp tối thiểu cần thiết, thay vì mô tả “mọi thứ có thể xuất hiện” rồi hy vọng kết quả đúng.

## 3. Dấu chấm và lượng từ tham lam: nguồn gốc overmatching

Trong Java regex:

- **.** khớp một ký tự bất kỳ, ngoại trừ ký tự kết thúc dòng theo chế độ mặc định.
- **.*** khớp từ 0 ký tự trở lên và là **greedy**: ban đầu cố tiêu thụ nhiều ký tự nhất có thể; nếu phần sau không khớp, engine có thể quay lui để trả lại ký tự.
- **.+** khớp ít nhất một ký tự, cũng greedy.
- **Pattern.DOTALL** cho phép **.** khớp cả ký tự xuống dòng; nó có thể làm phạm vi khớp rộng thêm.

Ví dụ, muốn tìm từng đoạn có dạng thẻ trong chuỗi:

~~~text
<b>Hi</b><i>ok</i>
~~~

Mẫu quá rộng:

~~~regex
<.*>
~~~

Khi gọi **find()**, mẫu này có thể lấy từ dấu **<** đầu tiên đến dấu **>** cuối cùng:

~~~text
<b>Hi</b><i>ok</i>
~~~

Nếu chỉ muốn đoạn bắt đầu bằng **<** và kết thúc ở dấu **>** đầu tiên, hai cách thường gặp là:

~~~regex
<.*?>
<[^>]*>
~~~

**.*?** là lượng từ **reluctant/lazy**, thử ngắn trước; nó sẽ mở rộng nếu cần để phần sau khớp. **[^>]* ** mô tả chính xác hơn “không chứa dấu >” và không cần thử vượt qua dấu phân cách **>**. Với ngữ pháp token đơn giản, mẫu có lớp ký tự phủ định thường dễ suy luận hơn.

Các mẫu trên chỉ thích hợp để minh họa **token dấu ngoặc nhọn đơn giản**. Chúng **không** phải trình phân tích HTML/XML hoàn chỉnh: thuộc tính có dấu **>** trong chuỗi trích dẫn, phần comment hoặc cú pháp lồng nhau đòi hỏi parser chuyên dụng.

### 3.1. Vì sao đổi sang lazy chưa giải quyết được mọi vấn đề?

Lượng từ **greedy** như `a+` thử lấy nhiều nhất trước rồi có thể quay lui; **lazy** như `a+?` thử lấy ít nhất trước nhưng vẫn có thể mở rộng và quay lui. Cả hai đều cần được kiểm tra với dữ liệu gần khớp nhưng thất bại: thay `.*` bằng `.*?` không tự loại bỏ sự mơ hồ trong ngữ pháp hoặc bảo đảm hiệu năng.

Lượng từ **possessive** như `a++` không trả lại ký tự; nó có thể giảm một số đường quay lui nhưng cũng đổi tập chuỗi được chấp nhận. Phần 7 đi sâu vào cách chứng minh khi nào possessive hoặc atomic group bảo toàn ngữ nghĩa. Với token có dấu phân cách rõ, thử một lớp ký tự cụ thể như `[^>]*` trước khi tối ưu chiến lược lượng từ.

## 4. Ví dụ Java 17+ chạy được

Tạo tệp **RegexSelectionDemo.java**:

~~~java
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexSelectionDemo {
    public static void main(String[] args) {
        String input = "<b>Hi</b><i>ok</i>";

        Matcher broad = Pattern.compile("<.*>").matcher(input);
        if (broad.find()) {
            System.out.println("broad: " + broad.group());
        }

        Matcher precise = Pattern.compile("<[^>]*>").matcher(input);
        while (precise.find()) {
            System.out.println("token: " + precise.group());
        }

        Pattern accountCode = Pattern.compile("[A-Z][A-Z0-9_]{2,15}");
        System.out.println("valid: " + accountCode.matcher("AB_7").matches());
        System.out.println("invalid: " + accountCode.matcher("AB_7!").matches());

        System.out.println("greedy: "
                + Pattern.compile("a+a").matcher("aaa").matches());
        System.out.println("possessive: "
                + Pattern.compile("a++a").matcher("aaa").matches());

        String nearMiss = "aaaaaaaaab";
        System.out.println("linear rewrite: "
                + Pattern.compile("a+$").matcher(nearMiss).matches());
    }
}
~~~

Chạy:

~~~bash
javac RegexSelectionDemo.java
java RegexSelectionDemo
~~~

Output chính xác:

~~~text
broad: <b>Hi</b><i>ok</i>
token: <b>
token: </b>
token: <i>
token: </i>
valid: true
invalid: false
greedy: true
possessive: false
linear rewrite: false
~~~

Ý nghĩa:

1. **<.*>** lấy toàn bộ từ dấu mở đầu tiên đến dấu đóng cuối cùng, quá rộng nếu muốn từng token.
2. **<[^>]*>** dừng tại dấu **>** gần nhất theo đúng tập ký tự cho phép.
3. **accountCode.matches()** chỉ chấp nhận toàn bộ mã hợp lệ, không chấp nhận ký tự **!** ở cuối.
4. Lượng từ possessive đổi kết quả vì không nhường ký tự.
5. Mẫu đơn giản **a+$** không chấp nhận chuỗi có **b** cuối; đây là phiên bản gọn của tình huống xử lý ký tự **a** liên tiếp được đề cập dưới đây.

## 5. Catastrophic backtracking là gì?

**Backtracking** là cơ chế thông thường: khi một nhánh khớp chưa đưa tới kết quả cuối cùng, engine có thể thử phân chia hoặc nhánh khác. Điều này giúp regex hỗ trợ các cấu trúc phong phú, nhưng có thể trở nên đắt đỏ nếu **một đoạn input có quá nhiều cách được các phần regex khác nhau cùng tiêu thụ**.

Mẫu cảnh báo kinh điển:

~~~regex
(a+)+$
~~~

Với input gồm nhiều ký tự **a** rồi thêm **b** ở cuối:

~~~text
aaaaaaaaaaaaaaaaab
~~~

Về logic, mẫu cần “một hoặc nhiều nhóm, mỗi nhóm chứa một hoặc nhiều a”, sau đó đến cuối. Trên một chuỗi toàn **a**, nhiều cách chia nhóm cùng cho kết quả khớp. Khi ký tự **b** làm toàn biểu thức thất bại, engine có thể phải thử một lượng rất lớn những cách phân chia đó.

Ở các họ input và triển khai cụ thể, mức tăng thời gian có thể rất lớn, thậm chí theo hàm mũ. **Không nên dựa vào một con số độ phức tạp cứng cho mọi regex và mọi phiên bản JVM**, vì engine có thể tối ưu một số cấu trúc; vấn đề cần nhận diện là **số đường quay lui tăng rất nhanh khi độ dài input tăng**.

Ví dụ khác đáng nghi:

~~~regex
(a|aa)+$
([A-Za-z]+)*$
(.*)+$
~~~

Ở mẫu đầu, **a** và **aa** chồng lấn; ở mẫu thứ hai và thứ ba, các lớp lặp lồng nhau có nhiều cách chia chuỗi. Một input rất dài chỉ khác ở ký tự cuối thường là phép thử phơi bày vấn đề tốt hơn input khớp hoàn toàn.

**Đây là nền tảng của ReDoS (Regular Expression Denial of Service):** kẻ tấn công cung cấp input gây quá nhiều công việc cho regex engine, khiến thread xử lý yêu cầu bị chiếm CPU lâu bất thường. Kích thước input không cần cực lớn nếu cấu trúc mẫu đủ mơ hồ.

## 6. Sửa từ ngữ pháp: loại bỏ đường phân tích dư thừa

Với mục tiêu “chuỗi chỉ gồm ít nhất một a”, mẫu:

~~~regex
(a+)+$
~~~

không cần hai lớp lặp. Có thể viết:

~~~regex
a+
~~~

và dùng **matches()** để kiểm tra toàn chuỗi. Một mẫu ngắn hơn, không có vòng lặp lồng nhau, thường dễ kiểm chứng hơn về cả tính đúng đắn lẫn hiệu năng.

Tương tự, nếu chỉ muốn ít nhất một chữ cái ASCII, thay vì:

~~~regex
([A-Za-z]+)*$
~~~

hãy viết:

~~~regex
[A-Za-z]+
~~~

và gọi **matches()**. Hai mẫu ở trên còn **không hoàn toàn tương đương**: mẫu có **\*** bên ngoài có thể chấp nhận chuỗi rỗng, còn **+** thì không. Vì vậy, cần xác định rõ **chuỗi rỗng có hợp lệ không** trước khi thay thế.

Đối với alternation chồng lấn:

~~~regex
(a|aa)+
~~~

nếu mục tiêu chỉ là một dãy **a**, thay bằng **a+**. Nếu hai nhánh có ý nghĩa thật sự khác nhau, cần diễn đạt dấu phân cách/ràng buộc sao cho phân tách được rõ ràng, thay vì cắt nhánh một cách tùy tiện.

**Quy tắc sửa:** trước tiên xác định ngôn ngữ hợp lệ; sau đó tạo một biểu thức càng ít cách phân tích cùng một input càng tốt. Tránh sửa hiệu năng bằng cách vô tình đổi tập chuỗi được chấp nhận.

## 7. Khi nào dùng possessive quantifier và atomic group?

Java hỗ trợ lượng từ **possessive**:

~~~regex
X*+
X++
X?+
X{m,n}+
~~~

và **atomic/independent group**:

~~~regex
(?>X)
~~~

Cả hai giới hạn đường quay lui: một khi đoạn được tiêu thụ trong phần possessive hoặc atomic đã được chốt, engine không thử các phương án nội bộ khác để cứu phép khớp sau đó.

Ví dụ:

~~~java
Pattern greedy = Pattern.compile("a+a");
Pattern possessive = Pattern.compile("a++a");

System.out.println(greedy.matcher("aaa").matches());     // true
System.out.println(possessive.matcher("aaa").matches()); // false
~~~

Đây là chứng cứ rằng phép thay đổi **có thể ảnh hưởng kết quả**. Chỉ dùng possessive khi chắc chắn phần sau không cần các ký tự được phần trước tiêu thụ.

Một trường hợp tương đối tự nhiên: chuỗi gồm **một hoặc nhiều chữ số rồi đến dấu chấm phẩy**. Nếu chữ số là **[0-9]** và dấu tiếp theo bắt buộc là **;**, hai phần không tranh giành ký tự:

~~~regex
[0-9]++[;]
~~~

So với **[0-9]+[;]**, việc không hoàn lại chữ số là an toàn cho ngữ pháp này vì dấu **;** không thuộc lớp chữ số. Nhưng điều đó không có nghĩa possessive luôn nhanh hơn có thể đo được; chỉ là đã loại đi một loại quay lui không cần thiết.

Atomic group có thể được dùng để cô lập một nhánh hoặc một cụm lặp khi bạn chứng minh được engine không cần xem xét lại lựa chọn bên trong nó. Ví dụ **(?>a+)a** cũng thất bại trên **aaa**, giống **a++a**.

Với bài toán đơn giản, **sửa cấu trúc mẫu và dùng lớp ký tự cụ thể là ưu tiên đầu tiên**. Possessive/atomic chỉ là công cụ khi cần khống chế backtracking có chủ đích; chúng không cứu được tất cả regex mơ hồ và có thể loại bỏ kết quả hợp lệ nếu dùng sai.

## 8. Giới hạn phạm vi tìm kiếm để tránh quét lan rộng

### 8.1. Chọn đúng phương thức

**matches()** phù hợp khi xác thực cả vùng đầu vào:

~~~java
Pattern p = Pattern.compile("[A-Z0-9_]{3,16}");
boolean ok = p.matcher("AB_12").matches();
~~~

**find()** phù hợp khi cần trích các đoạn con, ví dụ tìm tất cả số:

~~~java
Matcher m = Pattern.compile("[0-9]+").matcher("x12 y345");
while (m.find()) {
    System.out.println(m.group());
}
~~~

Nếu xác thực dữ liệu mà vô tình dùng **find()**, mã độc hại có thể mang theo một đoạn con hợp lệ nhưng cả chuỗi vẫn sai. Nếu chỉ cần tìm một đoạn con, ép **matches()** bằng cách bao quanh **.*...*** thường làm mẫu rộng và khó đoán hơn.

### 8.2. Dùng anchor khi cần rõ ràng về vị trí

- **\A**: đầu toàn bộ input của Matcher.
- **\z**: cuối tuyệt đối của input.
- **^** và **$**: chịu ảnh hưởng của **MULTILINE** và cách xử lý ranh giới dòng.
- **\b**: ranh giới từ, phụ thuộc quy tắc ký tự từ; không thay thế cho việc xác định rõ dấu phân cách theo nghiệp vụ.

Ví dụ khi tìm một dòng log bắt đầu bằng **ID=** và tối đa tám chữ số:

~~~java
Pattern line = Pattern.compile("^ID=([0-9]{1,8})$", Pattern.MULTILINE);
Matcher m = line.matcher("noise\nID=42\nend");
System.out.println(m.find());    // true
System.out.println(m.group(1)); // 42
~~~

Nếu cần kiểm tra cả input là **ID=42** và không có dữ liệu thừa, dùng **matches()** với **ID=[0-9]{1,8}** hoặc anchor phù hợp. Với mọi lựa chọn, đừng quên giới hạn độ dài ngoài regex.

### 8.3. Dùng lớp ký tự phủ định đến dấu phân cách

Nếu yêu cầu một trường không chứa dấu phẩy:

~~~regex
[^,]{1,40}
~~~

mô tả trực tiếp quy tắc tốt hơn:

~~~regex
.{1,40}
~~~

vì dấu chấm vẫn cho phép dấu phẩy. Nếu trường được xác thực toàn bộ bằng **matches()**, cả hai mẫu đều kiểm tra độ dài, nhưng chỉ mẫu thứ nhất cấm dấu phẩy.

Khi dùng **[^,]**, nhớ rằng lớp phủ định này vẫn có thể chứa xuống dòng. Nếu dữ liệu bắt buộc nằm trên một dòng, loại trừ thêm **\r**, **\n** bằng lớp ký tự tương ứng (trong Java String literal phải escape dấu gạch chéo ngược đúng cách).

### 8.4. Tách parsing có cấu trúc khỏi regex

Regex rất hữu ích để nhận dạng đoạn tuyến tính đơn giản, nhưng không nên cố xử lý hoàn chỉnh HTML, XML, JSON hoặc cú pháp lồng sâu bằng một mẫu **<.*?>** hay cấu trúc tương tự. Trong trường hợp đó, parser chuyên dụng sẽ kiểm soát cấu trúc tốt hơn và thường cho thông báo lỗi rõ hơn.

Với bài toán tìm một chuỗi cố định, API **String.contains()**, **indexOf()**, **startsWith()**, **endsWith()** có thể đơn giản và dễ dự đoán hơn regex. Với dữ liệu giới hạn rõ bằng một delimiter, cân nhắc parser hoặc thao tác chuỗi thay vì tạo regex có nhiều nhánh chồng lấn.

## 9. Đầu vào không tin cậy và giới hạn tài nguyên

Ngay cả regex đã được xem xét vẫn cần giới hạn input khi chạy trong dịch vụ:

1. **Đặt giới hạn chiều dài input trước khi match.** Giới hạn phải xuất phát từ nghiệp vụ: chẳng hạn mã tài khoản không nên dài hàng nghìn ký tự. **String.length()** đo đơn vị UTF-16; nếu chính sách yêu cầu số Unicode code point hoặc byte, phải đo theo đúng đơn vị đó.
2. **Không cho phép người dùng cung cấp regex tùy ý** nếu chỉ cần tìm chuỗi văn bản. Nếu input người dùng là **literal**, dùng **Pattern.quote(userText)**, hoặc thao tác chuỗi không qua regex. Regex do người dùng tự thiết kế cần kiểm soát riêng về độ phức tạp.
3. **Tạo bộ test input gần khớp nhưng thất bại ở cuối**, các đoạn lặp dài, dấu phân cách bị thiếu, dữ liệu nhiều dòng và dữ liệu dài tới giới hạn hợp lệ. Test đúng ở input ngắn chưa chứng minh được an toàn.
4. **Quan sát thời gian, CPU, tỉ lệ timeout của request** và hạn chế số tác vụ xử lý đồng thời nếu regex nằm trên đường xử lý truy cập từ bên ngoài.
5. **Đừng nhầm giới hạn thời gian request với khả năng cắt ngang regex ngay lập tức.** Java **Matcher** không có tham số timeout riêng cho từng phép match; **Thread.interrupt()** hoặc **Future.cancel(true)** không phải bảo đảm chung rằng một phép regex đang chạy sẽ dừng đúng thời điểm. Nếu xử lý regex tùy ý không tin cậy, cần ranh giới cô lập và cơ chế kiểm soát tài nguyên thực sự, ngoài việc tối ưu mẫu.

**Biên độ an toàn phải tính bằng dữ liệu xấu nhất dự kiến**, chứ không chỉ dựa trên tốc độ trên vài chuỗi thử nghiệm ngắn. Độ dài input có giới hạn là một lớp bảo vệ quan trọng, nhưng không biến mọi regex thành an toàn.

### 9.1. Pattern.quote dùng cho literal

~~~java
String requested = "a.b"; // Người dùng muốn tìm chính xác dấu chấm
Pattern literal = Pattern.compile(Pattern.quote(requested));

System.out.println(literal.matcher("x a.b y").find()); // true
System.out.println(literal.matcher("x acb y").find()); // false
~~~

Nếu không quote, dấu **.** trong regex mang nghĩa wildcard và có thể khiến **acb** được nhận nhầm. **Pattern.quote** giúp biểu diễn đúng literal; không phải giải pháp cho một regex phức tạp do lập trình viên tự viết.

### 9.2. Biên dịch lại Pattern không giải quyết backtracking

**Pattern** là đối tượng immutable, an toàn để nhiều luồng dùng chung; **Matcher** giữ trạng thái và không an toàn khi chia sẻ đồng thời. Biên dịch **Pattern** một lần rồi tạo Matcher mới cho từng input giúp tránh chi phí biên dịch lại, nhưng **không làm mất rủi ro backtracking của biểu thức**.

~~~java
private static final Pattern CODE =
        Pattern.compile("[A-Z][A-Z0-9_]{2,15}");

// Trong một phương thức xử lý từng yêu cầu:
boolean accepted = CODE.matcher(candidate).matches();
~~~

Đoạn trên chỉ minh họa cách tái sử dụng Pattern; **candidate** cần là String đầu vào thực tế và lớp xử lý vẫn phải đặt giới hạn theo nghiệp vụ.

## 10. Đánh giá và kiểm thử hiệu năng có kiểm soát

Đo thời gian là công cụ hỗ trợ, không nên dùng một con số đơn lẻ để khẳng định một regex không có ReDoS.

Một quy trình kiểm tra hợp lý:

- Soạn nhóm **positive cases** (phải khớp) và **negative cases** (không được khớp) để bảo vệ ngữ nghĩa.
- Xây các **near-miss cases** dài dần, nhất là chuỗi lặp nhiều lần rồi có ký tự phá hỏng tại cuối. Kiểm tra trên input nhỏ trước để tránh vô tình treo tiến trình thử nghiệm.
- Theo dõi xu hướng thời gian khi tăng độ dài; nếu thời gian tăng đột biến trong khi input chỉ tăng nhẹ, xem lại các lượng từ lồng nhau, alternation chồng lấn hoặc lookaround phức tạp.
- Chạy benchmark trong môi trường kiểm soát với giới hạn tài nguyên, warm-up JVM và nhiều lần đo; **System.nanoTime()** chỉ là phép đo thời gian, không có khả năng cắt phép match đang kẹt.
- So sánh đồng thời **tập kết quả** của mẫu cũ và mẫu mới: tối ưu thành công khi vừa đúng nghiệp vụ vừa có đặc tính hiệu năng tốt hơn trên nhóm input đáng quan tâm.

Không đưa mẫu khả nghi vào endpoint công khai để đo trực tiếp và không sử dụng input dài nguy hiểm trong ví dụ học cơ bản. Một regex gây treo một thread trên dữ liệu thử không chứng minh được JVM “lỗi”; nó cho thấy cần thiết kế lại mẫu và giới hạn tài nguyên.

## 11. Các lỗi phổ biến

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Dùng **.*** để “bắt mọi thứ” | Nuốt qua nhiều dấu phân cách hoặc phần không mong muốn | Dùng lớp ký tự cụ thể như **[^,]* **, giới hạn độ dài |
| Dùng **find()** để xác thực toàn chuỗi | Chấp nhận input có đoạn con hợp lệ nhưng toàn bộ không hợp lệ | Dùng **matches()** |
| Cho rằng **.*?** luôn nhanh | Vẫn có thể mở rộng và backtrack | Thu hẹp lớp ký tự và loại bỏ nhánh mơ hồ |
| Lặp chồng **(a+)+** hoặc alternation chồng **(a|aa)+** | Nhiều đường phân tích khi near-miss | Viết lại thành **a+** khi cùng ngữ nghĩa |
| Chuyển toàn bộ **+** sang **++** | Loại bỏ các kết quả vốn hợp lệ | Chỉ dùng possessive khi chứng minh không cần trả ký tự |
| Quên dấu phân cách/độ dài | Regex khớp rộng, dữ liệu vượt kỳ vọng | Ràng buộc biên và kiểm tra chiều dài input |
| Ghép thẳng input người dùng vào regex | Metacharacter làm đổi ý nghĩa mẫu | Dùng **Pattern.quote** nếu input là literal |
| Dựa vào timeout request để chặn regex đang chạy | Có thể vẫn chiếm tài nguyên sau khi request hết hạn | Thiết kế regex an toàn và có giới hạn tài nguyên phù hợp |
| Chỉ test các chuỗi khớp | Không phát hiện tình huống quay lui khi thất bại | Test input near-miss và chiều dài tăng dần |

## 12. Checklist chọn mẫu trước khi đưa vào chương trình

- [ ] Mình đã định nghĩa rõ đầu vào hợp lệ, bao gồm chuỗi rỗng và ký tự Unicode nếu liên quan?
- [ ] Mình dùng **matches()**, **lookingAt()** hay **find()** phù hợp phạm vi yêu cầu?
- [ ] Có thể thay **.*** hoặc **.+** bằng lớp ký tự cụ thể, dấu phân cách và độ dài có biên?
- [ ] Các nhánh alternation có chồng lấn không? Có lượng từ lồng nhau cùng ăn một loại ký tự không?
- [ ] Nếu dùng possessive hoặc atomic group, đã kiểm tra không làm mất trường hợp khớp hợp lệ?
- [ ] Đã test input gần đúng nhưng thất bại tại cuối và input sát giới hạn kích thước?
- [ ] Đã đặt giới hạn đầu vào và xem xét cách bảo vệ CPU/thread khi input không tin cậy?
- [ ] Nếu dữ liệu có cấu trúc lồng nhau, parser chuyên dụng có phù hợp hơn?

**Ghi nhớ:** chọn mẫu **cụ thể về ký tự, giới hạn, dấu phân cách và điểm bắt đầu/kết thúc**. Hạn chế nhiều cách phân tích cùng một chuỗi; khi cần hiệu năng tốt, **đơn giản hóa ngữ pháp trước, dùng possessive/atomic đúng chỗ sau, rồi kiểm chứng trên dữ liệu đúng và dữ liệu xấu**.

### Tài liệu tham khảo

- Java SE 21 API, **java.util.regex.Pattern**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html
- OWASP, **Regular expression Denial of Service (ReDoS)**: https://owasp.org/www-community/attacks/Regular_expression_Denial_of_Service_-_ReDoS


</details>

- [Quay lại đầu trang](#back-to-top)
