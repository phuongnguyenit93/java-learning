# Regular biểu thức

Regular biểu thức (**regex**) là một ngôn ngữ mô tả pattern trên text. Nó hữu ích cho validation, search, extraction và replacement khi bài toán thật sự mang tính pattern matching.

## <a id="pattern-matcher">Pattern và Matcher</a>

Java tách compiled pattern và trạng thái matching:

```java
Pattern pattern = Pattern.compile("(\\d+)-(\\w+)");
Matcher matcher = pattern.matcher(input);
```

`Pattern` mô tả regex đã compile; `Matcher` gắn pattern với một đầu vào cụ thể và giữ trạng thái tìm kiếm/match.

Compile lại cùng regex trong loop nóng có thể tốn chi phí không cần thiết; có thể reuse `Pattern` khi phù hợp.

### Pattern và Matcher có vai trò khác nhau

```text
regex source
    ↓ compile
Pattern
    ↓ matcher(input)
Matcher
    ↓ execute/search + giữ match state
```

`Pattern` immutable và có thể share; `Matcher` giữ mutable matching state nên thường tạo theo từng input/use.

### matches, lookingAt và find

Ba operation trả lời ba câu hỏi khác nhau:

```java
Pattern digits = Pattern.compile("\\d+");
String input = "123 abc 456";

digits.matcher(input).matches();   // toàn bộ input có match pattern?
digits.matcher(input).lookingAt(); // input có bắt đầu bằng match?

Matcher finder = digits.matcher(input);
while (finder.find()) {
    String match = finder.group();
}
```

```text
validation toàn chuỗi
→ matches

prefix pattern
→ lookingAt

search/extraction nhiều match
→ find
```

### Java escaping và regex escaping là hai layer

Muốn regex runtime là `\d+`:

```java
Pattern.compile("\\d+");
```

```text
Java source "\\d+"
        ↓ Java string parsing
runtime String "\d+"
        ↓ regex parsing
digit pattern
```

Đây là nguyên nhân rất nhiều regex Java trông có “gấp đôi backslash”.

Nếu text đến từ user/config và phải match như literal, `Pattern.quote(text)` giúp tránh interpret metacharacter ngoài ý muốn.

### Building blocks cơ bản

```text
[abc]     character class
[^abc]    negated class
^ / $     boundary anchors
a|b       alternation
(...)     group
\d \w   predefined classes
```

Không cần regex khi String API literal search đã đủ rõ.

### Pattern flags thay đổi matching semantics

Cùng một regex source có thể mang semantics khác khi compile với flags:

```java
Pattern multiline = Pattern.compile(
        "^error:",
        Pattern.MULTILINE
);

Pattern dotAll = Pattern.compile(
        "BEGIN.*END",
        Pattern.DOTALL
);
```

Một số flags quan trọng:

```text
CASE_INSENSITIVE
→ case-insensitive matching

MULTILINE
→ ^ và $ có thể hoạt động theo từng line

DOTALL
→ . cũng match line terminator

UNICODE_CASE
→ Unicode-aware case folding khi kết hợp case-insensitive behavior

UNICODE_CHARACTER_CLASS
→ predefined/POSIX character classes dùng Unicode semantics rộng hơn
```

Không nên bật flag “cho chắc”. Flag là một phần của regex contract và có thể thay đổi cả correctness lẫn performance.

Đặc biệt, regex Unicode semantics không nên bị nhầm với locale-sensitive language rules: regex flag và `Locale/Collator` giải quyết các abstraction khác nhau.

## <a id="regex-groups">Group và Capture</a>

Parentheses có thể tạo capturing group:

```regex
(\d+)-(\w+)
```

Sau khi match, `group(1)`, `group(2)` lấy phần text được capture tương ứng.

Nếu chỉ cần grouping mà không cần capture, non-capturing group `(?:...)` có thể thể hiện intent rõ hơn.

Named group cũng hữu ích khi regex phức tạp và tên mang ý nghĩa domain.

```java
Pattern p = Pattern.compile(
        "(?<user>[a-z]+)@(?<domain>[a-z.]+)"
);
Matcher m = p.matcher("ada@example.com");

if (m.matches()) {
    m.group("user");   // ada
    m.group("domain"); // example.com
}
```

### group(0) và group(n)

Sau successful match:

```text
group(0)
→ toàn bộ match

group(1..n)
→ capture group tương ứng
```

Gọi `group(...)` khi chưa có successful match state sẽ gây `IllegalStateException`.

### Replacement cũng có syntax riêng

Trong replacement của `replaceAll`/`Matcher.replaceAll`, `$` và `\` có ý nghĩa đặc biệt. Nếu replacement phải là literal runtime text, dùng `Matcher.quoteReplacement(...)` khi phù hợp.

## <a id="regex-quantifiers">Greedy và Reluctant Quantifier</a>

Quantifier như `*`, `+`, `{m,n}` mặc định thường greedy: cố lấy nhiều đầu vào nhất rồi backtrack nếu cần.

Reluctant variant như `*?`, `+?` bắt đầu với ít đầu vào hơn rồi mở rộng khi cần.

Java còn có **possessive quantifier** như `*+`, `++`, không backtrack phần đã consume. Nó có thể hữu ích để kiểm soát backtracking khi semantics phù hợp, nhưng không phải “phiên bản nhanh hơn” có thể thay greedy một cách máy móc.

Sự khác biệt này ảnh hưởng kết quả khi nhiều vị trí match có thể hợp lệ. Hãy đọc regex cùng đầu vào example thay vì chỉ nhìn cú pháp riêng lẻ.

Ví dụ:

```text
input: <a><b>

<.*>
→ greedy, có thể lấy <a><b>

<.*?>
→ reluctant, match đầu tiên có thể là <a>
```

## <a id="regex-performance">Backtracking và Hiệu năng</a>

Một số pattern có thể tạo lượng backtracking rất lớn trên đầu vào xấu, đặc biệt khi nested quantifier/ambiguous alternative kết hợp không cẩn thận.

Ví dụ dạng rủi ro:

```regex
(a+)+$
```

Input dài gần-match nhưng fail ở cuối có thể buộc engine thử rất nhiều partition trước khi kết luận.

Với regex nhận đầu vào không tin cậy:

- giữ pattern đơn giản;
- giới hạn đầu vào khi phù hợp;
- tránh cấu trúc dễ gây catastrophic backtracking;
- benchmark/test worst-case thay vì chỉ test đầu vào match đẹp.

Ngoài ra:

- reuse compiled `Pattern` khi cùng pattern chạy lặp lại nhiều;
- tránh dùng regex để parse grammar phức tạp khi parser/state machine rõ hơn;
- đặt size/time boundary ở layer ứng dụng nếu regex xử lý untrusted large input;
- review nested quantifier và overlapping alternation.

Regex rất mạnh nhưng không phải parser tốt cho mọi grammar phức tạp.

chương cuối của module nói về **cách viết String nhiều dòng trong source**, không phải một kiểu String mới.
