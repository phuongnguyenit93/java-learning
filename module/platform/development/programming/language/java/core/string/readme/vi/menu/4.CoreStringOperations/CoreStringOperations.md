# Các thao tác String cốt lõi

Sau khi hiểu `String` là immutable và biết cách so sánh theo nội dung, bước tiếp theo là học cách **đọc, tìm kiếm, cắt, biến đổi và kết hợp text** bằng những API cốt lõi. Mục tiêu của chương này không phải ghi nhớ toàn bộ `java.lang.String`, mà là xây một mental model để chọn đúng nhóm thao tác cho câu hỏi đang giải quyết.

```text
Tôi muốn biết gì về text?
        ↓
inspect
        ↓
search
        ↓
extract
        ↓
transform
        ↓
split / join
        ↓
convert / format
```

Hầu hết thao tác tạo nội dung khác đều trả về **String mới**. String ban đầu vẫn không thay đổi.

### String là một CharSequence

`String` implement `CharSequence`. Đây là abstraction dùng cho một sequence các `char` có thể đọc theo index:

```text
CharSequence
    ↑
    ├── String
    ├── StringBuilder
    └── StringBuffer
```

Điểm quan trọng là **CharSequence không đồng nghĩa immutable**. `String` immutable, còn `StringBuilder` và `StringBuffer` là mutable character sequences.

Nhiều API nhận `CharSequence` để không ép caller phải có đúng một `String`:

```java
String text = "java-core";
StringBuilder token = new StringBuilder("java");

text.contains(token);       // true
text.contentEquals(token);  // false ở đây vì content khác "java-core"
```

Một pitfall cần nhớ: `CharSequence` không định nghĩa một equality contract chung giữa mọi implementation.

```java
String s = "java";
StringBuilder b = new StringBuilder("java");

s.equals(b);        // false
s.contentEquals(b); // true
```

Vì vậy:

```text
String-to-String content equality
→ equals

String-to-CharSequence content comparison
→ contentEquals khi contract phù hợp

CharSequence abstraction
→ không tự bảo đảm cross-implementation equals
```

## <a id="string-inspection">Kiểm tra nội dung String</a>

Nhóm inspection trả lời các câu hỏi như: chuỗi dài bao nhiêu, có rỗng không, có chỉ chứa whitespace không, hoặc code unit tại một vị trí là gì.

```java
String text = " Java ";

text.length();   // 6
text.isEmpty();  // false
text.isBlank();  // false
text.charAt(1);  // 'J'
```

Ba khái niệm dễ nhầm:

```text
""        → empty
"   "     → không empty nhưng blank
" Java "  → không empty, không blank
```

`length()` và `charAt()` làm việc theo **UTF-16 code unit**, không đảm bảo một `char` bằng một ký tự người dùng nhìn thấy. Chương Unicode sẽ đi sâu vào boundary này.

Valid index của `charAt(index)` là:

```text
0 <= index < length()
```

```java
"Java".charAt(4); // StringIndexOutOfBoundsException
```

Với supplementary character:

```java
String emoji = "😀";

emoji.length();  // 2
emoji.charAt(0); // high surrogate, chưa phải toàn bộ emoji
```

Đây là lý do không được mặc định “1 char = 1 ký tự người dùng nhìn thấy”.

## <a id="string-search">Tìm kiếm trong String</a>

Khi chỉ cần tìm literal text đơn giản, các API String trực tiếp thường rõ ràng hơn regex:

```java
String path = "/api/users/42";

path.startsWith("/api/");      // true
path.endsWith("42");           // true
path.contains("users");        // true
path.indexOf("users");         // 5
path.lastIndexOf('/');          // 10
```

`indexOf(...)` và `lastIndexOf(...)` trả `-1` khi không tìm thấy. Nếu đã lấy index để cắt chuỗi, phải kiểm tra boundary trước khi dùng index đó.

```java
int slash = path.lastIndexOf('/');
if (slash >= 0) {
    String id = path.substring(slash + 1);
}
```

Regex chỉ nên xuất hiện khi câu hỏi thực sự là **pattern matching**, không phải vì mọi search trên String đều cần regex.

`contains` không interpret regex:

```java
"file-123.txt".contains("\d+") // false
```

```text
literal search
→ contains / indexOf / startsWith / endsWith

pattern search
→ regex
```

## <a id="string-extraction">Trích xuất bằng index và substring</a>

`substring` tạo một String biểu diễn phần text trong một khoảng index:

```java
String value = "JAVA-21";

value.substring(0, 4); // "JAVA"
value.substring(5);    // "21"
```

Quy tắc khoảng là:

```text
beginIndex inclusive
endIndex   exclusive
```

Vì vậy `substring(0, 4)` lấy index `0,1,2,3`.

Index âm, vượt `length()`, hoặc `beginIndex > endIndex` là lỗi boundary và dẫn tới `IndexOutOfBoundsException`.

Cũng cần nhớ index của `String` là index theo UTF-16 code unit. Không được mặc định dùng `substring(i, i + 1)` để tách một user-visible character trong mọi Unicode text.

Half-open range giúp tính slice length đơn giản:

```text
substring(begin, end)

slice length = end - begin
```

Các boundary invalid:

```text
begin < 0
end > length()
begin > end
```

## <a id="string-transformation">Biến đổi text nhưng không mutate String</a>

Những API như `replace`, `trim`, `strip`, `toUpperCase`, `toLowerCase` mô tả **giá trị mới**:

```java
String raw = "  java-core  ";

String cleaned = raw.strip();
String renamed = cleaned.replace("core", "string");

System.out.println(raw);     // "  java-core  "
System.out.println(renamed); // "java-string"
```

`trim()` và `strip()` không hoàn toàn là hai tên cho cùng một rule: `strip()` dùng khái niệm Unicode whitespace rộng hơn, còn `trim()` dựa trên rule lịch sử hẹp hơn của Java.

Ngoài ra:

```java
text.stripLeading();
text.stripTrailing();
```

### replace khác replaceAll

```java
"a.b".replace(".", "-");    // "a-b"
"a.b".replaceAll(".", "-"); // "---"
```

`replace` nhận literal character/sequence. `replaceAll` nhận regex, nên `.` có nghĩa “match một character bất kỳ”.

### Một số API text hiện đại

Không cần học thuộc toàn bộ `String` API, nhưng một số operation hiện đại đáng biết vì chúng mô tả intent rất rõ:

```java
String lines = """
        alpha
        beta
        """;

lines.lines().forEach(System.out::println);

"ab".repeat(3); // "ababab"
```

`lines()` tạo stream để duyệt các line theo line-terminator contract của chính API, thay vì buộc caller tự scan/cắt text khi chỉ cần xử lý từng line. `repeat(n)` lặp text `n` lần và phù hợp cho formatting/test data đơn giản.

Các API như `indent` và `stripIndent` liên quan mạnh tới multiline text nên được nối tiếp ở chapter Text Blocks.

Case conversion cần phân biệt mục đích:

```text
human-language text
→ có thể phụ thuộc Locale

technical token / protocol identifier
→ cần rule ổn định theo contract
```

Locale/collation đầy đủ thuộc module `localization`; chương này chỉ thiết lập boundary để không coi lower/upper-case conversion là phép chuẩn hóa universal.

## <a id="string-split-join">Tách và ghép nhiều phần text</a>

`split` biến một String thành nhiều phần, còn `String.join` ghép nhiều phần bằng delimiter:

```java
String csv = "red,green,blue";
String[] colors = csv.split(",");

String path = String.join("/", "api", "users", "42");
// "api/users/42"
```

Điểm quan trọng: tham số của `String.split(...)` là **regular expression**, không phải luôn là literal delimiter.

```java
"a.b.c".split("\\.");
```

Ở đây cần escape cả Java String literal lẫn regex metacharacter. Chương Regex sẽ giải thích hai layer này kỹ hơn.

Nếu delimiter chỉ là một literal cố định nhưng có ý nghĩa regex, hãy chủ động quote hoặc dùng API phù hợp thay vì đoán escape.

### split(regex, limit) thay đổi cách giữ phần tử

`split(regex)` tương đương behavior của `split(regex, 0)`: regex được áp dụng nhiều lần và trailing empty strings bị loại:

```java
"a,b,".split(",").length // 2
"a,b,".split(",", 0).length // 2
```

Khi `limit < 0`, regex được áp dụng nhiều lần mà không có giới hạn dương và trailing empty strings được giữ lại:

```java
"a,b,".split(",", -1).length // 3
```

Khi `limit > 0`, result có tối đa `limit` phần tử, nên regex chỉ được áp dụng nhiều nhất `limit - 1` lần. Phần tử cuối giữ phần input còn lại:

```java
Arrays.toString("a,b,c,d".split(",", 2));
// [a, b,c,d]

Arrays.toString("a,b,c,d".split(",", 3));
// [a, b, c,d]
```

Mental model:

```text
limit > 0
→ tối đa limit phần tử
→ phần tử cuối giữ phần còn lại

limit == 0
→ split không giới hạn dương
→ bỏ trailing empty strings

limit < 0
→ split không giới hạn dương
→ giữ trailing empty strings
```

`limit` là data-parsing contract, không chỉ là performance option. Nếu empty trailing field hay phần remainder có semantic meaning, chọn overload có chủ ý.

Điều này cũng cho thấy CSV thật có thể cần parser riêng vì quoted field/escape rule phức tạp hơn một delimiter đơn giản.

## <a id="string-conversion-formatting">Chuyển giá trị thành String và định dạng</a>

`String.valueOf(...)` là một entry point phổ biến để biểu diễn primitive/object dưới dạng String:

```java
String count = String.valueOf(42);
String active = String.valueOf(true);
```

Null behavior là một khác biệt đáng nhớ:

```java
String.valueOf((Object) null); // "null"
```

trong khi:

```java
Object value = null;
// value.toString(); // NullPointerException
```

Khi cần ghép output theo format rõ ràng, Java cũng cung cấp formatting:

```java
String message = "User %s has %d points".formatted("Ada", 42);
```

Formatting không thay đổi bản chất immutable của String; nó tạo ra String result.

Nếu format phụ thuộc ngôn ngữ, số, ngày tháng hoặc quy tắc trình bày theo vùng, cần chuyển sang boundary của module `localization` thay vì giả định một format phù hợp cho mọi locale.

Mental model cần giữ lại sau chương này:

```text
String API cơ bản
→ đọc trạng thái text
→ tìm vị trí/nội dung
→ trích xuất bằng boundary rõ ràng
→ tạo giá trị biến đổi mới
→ tách/ghép nhiều phần
→ chuyển/format thành output text

không có bước nào mutate String object cũ
```

Chương tiếp theo tập trung vào một thao tác đặc biệt: **concatenation**, nơi tính immutable ảnh hưởng trực tiếp tới cách String được xây qua nhiều bước.
