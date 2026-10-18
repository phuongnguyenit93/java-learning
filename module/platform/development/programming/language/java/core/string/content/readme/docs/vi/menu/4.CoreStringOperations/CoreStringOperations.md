# Các thao tác String cốt lõi

Sau khi hiểu `String` là bất biến và biết cách so sánh theo nội dung, bước tiếp theo là học cách **đọc, tìm kiếm, cắt, biến đổi và kết hợp văn bản** bằng những API cốt lõi. Mục tiêu của chương này không phải ghi nhớ toàn bộ `java.lang.String`, mà là xây một mô hình tư duy để chọn đúng nhóm thao tác cho câu hỏi đang giải quyết.

```text
Tôi muốn làm gì với văn bản?
        ↓
kiểm tra
        ↓
tìm kiếm
        ↓
trích xuất
        ↓
biến đổi
        ↓
tách / ghép
        ↓
chuyển đổi / định dạng
```

Hầu hết thao tác tạo nội dung khác đều trả về **String mới**. String ban đầu vẫn không thay đổi.

## <a id="string-char-sequence">String và CharSequence</a>

`String` triển khai `CharSequence`. Đây là tầng trừu tượng cho một chuỗi `char` có thể đọc theo chỉ số:

```text
CharSequence
    ↑
    ├── String
    ├── StringBuilder
    └── StringBuffer
```

Điểm quan trọng là **CharSequence không đồng nghĩa bất biến**. `String` bất biến, còn `StringBuilder` và `StringBuffer` là các chuỗi ký tự có thể thay đổi.

Nhiều API nhận `CharSequence` để không ép bên gọi phải có đúng một `String`:

```java
String text = "java-core";
StringBuilder token = new StringBuilder("java");

text.contains(token);       // true
text.contentEquals(token);  // false vì nội dung khác "java-core"
```

Một điểm dễ nhầm cần nhớ: `CharSequence` không định nghĩa một quy ước `equals` chung giữa mọi kiểu triển khai.

```java
String s = "java";
StringBuilder b = new StringBuilder("java");

s.equals(b);        // false
s.contentEquals(b); // true
```

Khi hợp đồng cần so nội dung với một `CharSequence`, không cần chuyển mọi chuỗi ký tự thành `String` chỉ để so sánh; `contentEquals(...)` thể hiện trực tiếp ý định đó.

Vì vậy:

```text
so sánh nội dung String với String
→ equals

so sánh nội dung String với CharSequence
→ contentEquals khi quy ước phù hợp

tầng trừu tượng CharSequence
→ không tự bảo đảm equals giữa các kiểu triển khai khác nhau
```

## <a id="string-inspection">Kiểm tra nội dung String</a>

Nhóm kiểm tra trả lời các câu hỏi như: chuỗi dài bao nhiêu, có rỗng không, có chỉ chứa khoảng trắng không, hoặc đơn vị mã tại một vị trí là gì.

```java
String text = " Java ";

text.length();   // 6
text.isEmpty();  // false
text.isBlank();  // false
text.charAt(1);  // 'J'
```

Ba khái niệm dễ nhầm:

```text
""        → rỗng
"   "     → không rỗng nhưng chỉ chứa khoảng trắng
" Java "  → không rỗng, không chỉ chứa khoảng trắng
```

`isBlank()` dùng khái niệm khoảng trắng của `Character.isWhitespace(...)`: chuỗi rỗng hoặc chỉ gồm các code point được API này xem là whitespace sẽ được coi là blank.

`length()` và `charAt()` làm việc theo **đơn vị mã UTF-16 (code unit)**, không đảm bảo một `char` bằng một ký tự người dùng nhìn thấy. Chương Unicode sẽ đi sâu vào ranh giới này.

Chỉ số hợp lệ của `charAt(index)` là:

```text
0 <= index < length()
```

```java
"Java".charAt(4); // StringIndexOutOfBoundsException
```

Với ký tự nằm ngoài BMP:

```java
String emoji = "😀";

emoji.length();  // 2
emoji.charAt(0); // high surrogate, chưa phải toàn bộ emoji
```

Đây là lý do không được mặc định “1 char = 1 ký tự người dùng nhìn thấy”.

## <a id="string-search">Tìm kiếm trong String</a>

Khi chỉ cần tìm một đoạn văn bản cố định, các API String trực tiếp thường rõ ràng hơn Regex:

```java
String path = "/api/users/42";

path.startsWith("/api/");      // true
path.endsWith("42");           // true
path.contains("users");        // true
path.indexOf("users");         // 5
path.lastIndexOf('/');          // 10
```

`indexOf(...)` và `lastIndexOf(...)` trả `-1` khi không tìm thấy. Nếu đã lấy chỉ số để cắt chuỗi, phải kiểm tra phạm vi trước khi dùng chỉ số đó.

```java
int slash = path.lastIndexOf('/');
if (slash >= 0) {
    String id = path.substring(slash + 1);
}
```

Regex chỉ nên xuất hiện khi câu hỏi thực sự là **so khớp mẫu**, không phải vì mọi thao tác tìm kiếm trên String đều cần Regex.

`contains` không diễn giải Regex:

```java
System.out.println("file-123.txt".contains("\\d+")); // false
```

```text
tìm kiếm đoạn văn bản cố định
→ contains / indexOf / startsWith / endsWith

tìm kiếm theo mẫu
→ regex
```

## <a id="string-extraction">Trích xuất bằng chỉ số và substring</a>

`substring` tạo một String biểu diễn phần văn bản trong một khoảng chỉ số:

```java
String value = "JAVA-21";

value.substring(0, 4); // "JAVA"
value.substring(5);    // "21"
```

Quy tắc khoảng là:

```text
beginIndex được tính
endIndex   không được tính
```

Vì vậy `substring(0, 4)` lấy các chỉ số `0,1,2,3`.

Chỉ số âm, vượt `length()`, hoặc `beginIndex > endIndex` là lỗi phạm vi và dẫn tới `IndexOutOfBoundsException`.

Cũng cần nhớ chỉ số của `String` được tính theo đơn vị mã UTF-16. Không được mặc định dùng `substring(i, i + 1)` để tách một ký tự mà người dùng nhìn thấy trong mọi văn bản Unicode.

Khoảng nửa kín nửa hở giúp tính độ dài đoạn cắt đơn giản:

```text
substring(begin, end)

độ dài đoạn cắt = end - begin
```

Các phạm vi không hợp lệ:

```text
begin < 0
end > length()
begin > end
```

## <a id="string-transformation">Biến đổi văn bản</a>

Những API như `replace`, `trim`, `strip`, `toUpperCase`, `toLowerCase` mô tả **giá trị mới**:

```java
String raw = "  java-core  ";

String cleaned = raw.strip();
String renamed = cleaned.replace("core", "string");

System.out.println(raw);     // "  java-core  "
System.out.println(renamed); // "java-string"
```

`trim()` và `strip()` không hoàn toàn là hai tên cho cùng một quy tắc. `strip()` loại ký tự đầu/cuối dựa trên `Character.isWhitespace(...)`, còn `trim()` dùng quy tắc lịch sử: loại các ký tự đầu/cuối có mã không lớn hơn `U+0020`. Vì vậy `strip()` thường phù hợp hơn khi xử lý khoảng trắng theo API Unicode của Java, nhưng cũng không nên đồng nhất máy móc `Character.isWhitespace` với mọi thuộc tính whitespace trong chuẩn Unicode.

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

`replace` nhận ký tự/chuỗi cố định. `replaceAll` nhận Regex, nên `.` mang ngữ nghĩa của dấu chấm trong Regex: mặc định nó khớp một ký tự nhưng không khớp ký tự kết thúc dòng; cờ `DOTALL` có thể thay đổi quy tắc đó. Ví dụ `"a.b"` không chứa ký tự kết thúc dòng nên cả ba ký tự đều bị thay thế.

### Một số API xử lý văn bản hiện đại

Không cần học thuộc toàn bộ API của `String`, nhưng một số thao tác hiện đại đáng biết vì chúng mô tả mục đích rất rõ:

```java
String lines = "alpha\nbeta\n";

lines.lines().forEach(System.out::println);

"ab".repeat(3); // "ababab"
```

`lines()` tạo một luồng (`Stream<String>`) để duyệt từng dòng theo quy ước ký tự kết thúc dòng của chính API, thay vì buộc bên gọi tự quét/cắt văn bản. `repeat(n)` lặp văn bản `n` lần và phù hợp cho định dạng hoặc dữ liệu kiểm thử đơn giản.

Các API như `indent` và `stripIndent` liên quan mạnh tới văn bản nhiều dòng nên được nối tiếp ở chương Text Block.

Chuyển đổi chữ hoa/thường cần phân biệt mục đích:

```text
văn bản ngôn ngữ tự nhiên
→ có thể phụ thuộc Locale

token kỹ thuật / mã định danh giao thức
→ cần quy tắc ổn định theo hợp đồng
```

Quy tắc vùng miền và so sánh thứ tự văn bản (collation) đầy đủ thuộc mô-đun Bản địa hóa (Localization); chương này chỉ thiết lập ranh giới để không coi chuyển đổi chữ hoa/thường là phép chuẩn hóa dùng được cho mọi trường hợp.

## <a id="string-split-join">Tách và ghép nhiều phần văn bản</a>

`split` biến một String thành nhiều phần, còn `String.join` ghép nhiều phần bằng dấu phân cách:

```java
String csv = "red,green,blue";
String[] colors = csv.split(",");

String path = String.join("/", "api", "users", "42");
// "api/users/42"
```

Điểm quan trọng: tham số của `String.split(...)` là **biểu thức chính quy**, không phải luôn là một dấu phân cách cố định.

```java
"a.b.c".split("\\.");
```

Ở đây cần xử lý ký tự thoát (escape) ở cả tầng Java String literal lẫn tầng ký tự đặc biệt của Regex. Chương Regex sẽ giải thích hai tầng này kỹ hơn.

Nếu dấu phân cách chỉ là một chuỗi cố định nhưng lại có ý nghĩa đặc biệt trong Regex, hãy chủ động dùng `Pattern.quote(...)` hoặc API phù hợp thay vì đoán cách viết ký tự thoát.

### split(regex, limit) thay đổi cách giữ phần tử

`split(regex)` có hành vi tương đương `split(regex, 0)`: Regex được áp dụng nhiều lần và các chuỗi rỗng ở cuối bị loại:

```java
System.out.println("a,b,".split(",").length);    // 2
System.out.println("a,b,".split(",", 0).length); // 2
```

Khi `limit < 0`, Regex được áp dụng nhiều lần mà không có giới hạn dương và các chuỗi rỗng ở cuối được giữ lại:

```java
System.out.println("a,b,".split(",", -1).length); // 3
```

Khi `limit > 0`, kết quả có tối đa `limit` phần tử, nên Regex chỉ được áp dụng nhiều nhất `limit - 1` lần. Phần tử cuối giữ phần đầu vào còn lại:

```java
Arrays.toString("a,b,c,d".split(",", 2));
// [a, b,c,d]

Arrays.toString("a,b,c,d".split(",", 3));
// [a, b, c,d]
```

Mô hình tư duy:

```text
limit > 0
→ tối đa limit phần tử
→ phần tử cuối giữ phần còn lại

limit == 0
→ split không giới hạn dương
→ bỏ các chuỗi rỗng ở cuối

limit < 0
→ split không giới hạn dương
→ giữ các chuỗi rỗng ở cuối
```

`limit` là một phần của hợp đồng phân tích dữ liệu, không chỉ là tùy chọn hiệu năng. Nếu trường rỗng ở cuối hay phần dữ liệu còn lại có ý nghĩa, hãy chọn overload có chủ ý.

Điều này cũng cho thấy CSV thật có thể cần bộ phân tích riêng vì trường có dấu nháy và quy tắc ký tự thoát phức tạp hơn một dấu phân cách đơn giản.

## <a id="string-conversion-formatting">Chuyển giá trị thành String và định dạng</a>

`String.valueOf(...)` là một điểm vào phổ biến để biểu diễn giá trị nguyên thủy hoặc đối tượng dưới dạng String:

```java
String count = String.valueOf(42);
String active = String.valueOf(true);
```

Hành vi với null là một khác biệt đáng nhớ:

```java
String.valueOf((Object) null); // "null"
```

trong khi:

```java
Object value = null;
// value.toString(); // NullPointerException
```

Khi cần tạo đầu ra theo định dạng rõ ràng, Java cũng cung cấp API định dạng:

```java
String message = "User %s has %d points".formatted("Ada", 42);
```

Định dạng không thay đổi bản chất bất biến của String; nó tạo ra một kết quả String.

Nếu định dạng phụ thuộc ngôn ngữ, số, ngày tháng hoặc quy tắc trình bày theo vùng, cần chuyển sang phạm vi của mô-đun Bản địa hóa (Localization) thay vì giả định một định dạng phù hợp cho mọi locale.

Mô hình tư duy cần giữ lại sau chương này:

```text
String API cơ bản
→ đọc trạng thái văn bản
→ tìm vị trí/nội dung
→ trích xuất bằng phạm vi rõ ràng
→ tạo giá trị biến đổi mới
→ tách/ghép nhiều phần
→ chuyển đổi/định dạng thành văn bản đầu ra

không có bước nào sửa đối tượng String cũ tại chỗ
```

Chương tiếp theo tập trung vào một thao tác đặc biệt: **nối chuỗi**, nơi tính bất biến ảnh hưởng trực tiếp tới cách String được xây dựng qua nhiều bước.
