# Biểu thức chính quy (Regex)

## <a id="regex-purpose">Regex là gì và khi nào nên dùng?</a>

Biểu thức chính quy (**Regex**) là một ngôn ngữ mô tả **mẫu văn bản**. Nó tồn tại để giải quyết những câu hỏi không còn là tìm một chuỗi cố định, ví dụ: “đầu vào có đúng cấu trúc mong muốn không?”, “hãy tìm mọi đoạn khớp một quy luật”, hoặc “hãy trích xuất các phần có hình dạng xác định”.

Regex hữu ích cho kiểm tra hợp lệ, tìm kiếm, trích xuất và thay thế khi bài toán thật sự mang tính **so khớp mẫu**. Nếu chỉ cần tìm một chuỗi cố định, kiểm tra tiền tố/hậu tố hoặc thay thế literal, API `String` trực tiếp thường đơn giản và dễ đọc hơn.

```text
tìm "users" đúng nguyên văn
→ contains / indexOf

tìm mọi chuỗi chữ số theo một quy luật
→ Regex

ngữ pháp dữ liệu phức tạp, có cấu trúc nhiều tầng
→ cân nhắc bộ phân tích cú pháp (parser) thay vì cố nhồi mọi thứ vào Regex
```

## <a id="pattern-matcher">Pattern và Matcher</a>

Java tách mẫu đã biên dịch và trạng thái so khớp:

```java
Pattern pattern = Pattern.compile("(\\d+)-(\\w+)");
Matcher matcher = pattern.matcher(input);
```

`Pattern` mô tả Regex đã được biên dịch; `Matcher` gắn mẫu đó với một đầu vào cụ thể và giữ trạng thái tìm kiếm/so khớp.

Biên dịch lại cùng Regex trong một vòng lặp nóng có thể tốn chi phí không cần thiết; có thể tái sử dụng `Pattern` khi phù hợp.

### Pattern và Matcher có vai trò khác nhau

```text
Regex trong mã nguồn
    ↓ biên dịch
Pattern
    ↓ matcher(input)
Matcher
    ↓ thực thi/tìm kiếm + giữ trạng thái so khớp
```

`Pattern` bất biến và có thể dùng chung; `Matcher` giữ trạng thái so khớp có thể thay đổi nên thường tạo theo từng đầu vào/lần sử dụng.

### matches, lookingAt và find

Ba thao tác trả lời ba câu hỏi khác nhau:

```java
Pattern digits = Pattern.compile("\\d+");
String input = "123 abc 456";

digits.matcher(input).matches();   // toàn bộ đầu vào có khớp mẫu?
digits.matcher(input).lookingAt(); // đầu vào có bắt đầu bằng phần khớp?

Matcher finder = digits.matcher(input);
while (finder.find()) {
    String match = finder.group();
}
```

```text
kiểm tra toàn chuỗi
→ matches

mẫu ở đầu chuỗi
→ lookingAt

tìm kiếm/trích xuất nhiều phần khớp
→ find
```

## <a id="regex-syntax-boundary">Cú pháp Regex và ranh giới sử dụng</a>

Trước khi đi sâu vào nhóm và lượng từ, cần phân biệt rõ ba tầng: khi nào Regex thực sự cần thiết, cú pháp Regex được biểu diễn thế nào trong String literal của Java, và các cờ biên dịch thay đổi quy tắc so khớp ra sao.

### Ký tự thoát của Java và Regex là hai tầng khác nhau

Muốn Regex khi chạy là `\d+`:

```java
Pattern.compile("\\d+");
```

```text
mã nguồn Java "\\d+"
        ↓ Java phân tích String literal
String khi chạy "\d+"
        ↓ Regex phân tích mẫu
mẫu chữ số
```

Đây là nguyên nhân rất nhiều Regex trong Java trông có “gấp đôi dấu gạch chéo ngược”.

Nếu văn bản đến từ người dùng/cấu hình và phải được so khớp như literal, `Pattern.quote(text)` giúp tránh diễn giải ký tự đặc biệt ngoài ý muốn.

### Các thành phần cơ bản

```text
[abc]     lớp ký tự
[^abc]    lớp ký tự phủ định
^ / $     mốc biên
a|b       lựa chọn
(...)     nhóm
\d \w   lớp dựng sẵn
```

Không cần Regex khi API String cho tìm kiếm literal đã đủ rõ; đây là ranh giới lựa chọn đã thiết lập ở đầu chương.

### Cờ của Pattern thay đổi ngữ nghĩa so khớp

Cùng một Regex có thể mang ngữ nghĩa khác khi biên dịch với các cờ khác nhau:

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

Một số cờ quan trọng:

```text
CASE_INSENSITIVE
→ so khớp không phân biệt hoa/thường

MULTILINE
→ ^ và $ có thể hoạt động theo từng dòng

DOTALL
→ . cũng khớp ký tự kết thúc dòng

UNICODE_CASE
→ áp dụng quy tắc chữ hoa/thường theo Unicode khi kết hợp với so khớp không phân biệt hoa/thường

UNICODE_CHARACTER_CLASS
→ các lớp ký tự dựng sẵn/POSIX dùng ngữ nghĩa Unicode rộng hơn
```

Không nên bật cờ “cho chắc”. Cờ là một phần của hợp đồng Regex và có thể thay đổi cả tính đúng đắn lẫn hiệu năng.

Đặc biệt, ngữ nghĩa Unicode của Regex không nên bị nhầm với quy tắc ngôn ngữ phụ thuộc vùng miền: cờ Regex và `Locale/Collator` giải quyết các tầng trừu tượng khác nhau.

## <a id="regex-groups">Nhóm và phần được bắt giữ</a>

Dấu ngoặc có thể tạo nhóm bắt giữ:

```regex
(\d+)-(\w+)
```

Sau khi so khớp thành công, `group(1)`, `group(2)` lấy phần văn bản được bắt giữ tương ứng.

Nếu chỉ cần nhóm mà không cần bắt giữ, nhóm không bắt giữ `(?:...)` có thể thể hiện mục đích rõ hơn.

Nhóm có tên cũng hữu ích khi Regex phức tạp và tên mang ý nghĩa nghiệp vụ.

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

Sau khi so khớp thành công:

```text
group(0)
→ toàn bộ phần khớp

group(1..n)
→ nhóm bắt giữ tương ứng
```

Gọi `group(...)` khi chưa có trạng thái so khớp thành công sẽ gây `IllegalStateException`.

### Chuỗi thay thế cũng có cú pháp riêng

Trong chuỗi thay thế của `replaceAll`/`Matcher.replaceAll`, `$` và `\` có ý nghĩa đặc biệt. Nếu phần thay thế phải được hiểu là văn bản literal khi chạy, dùng `Matcher.quoteReplacement(...)` khi phù hợp.

## <a id="regex-quantifiers">Lượng từ tham lam và không tham lam</a>

Lượng từ như `*`, `+`, `{m,n}` mặc định thường tham lam: cố lấy nhiều đầu vào nhất rồi quay lui nếu cần.

Biến thể không tham lam như `*?`, `+?` bắt đầu với ít đầu vào hơn rồi mở rộng khi cần.

Java còn có **lượng từ chiếm hữu (possessive quantifier)** như `*+`, `++`, không quay lui phần đã tiêu thụ. Nó có thể hữu ích để kiểm soát backtracking khi ngữ nghĩa phù hợp, nhưng không phải “phiên bản nhanh hơn” có thể thay thế lượng từ tham lam một cách máy móc.

Sự khác biệt này ảnh hưởng kết quả khi nhiều vị trí so khớp có thể hợp lệ. Hãy đọc Regex cùng đầu vào ví dụ thay vì chỉ nhìn cú pháp riêng lẻ.

Ví dụ:

```text
đầu vào: <a><b>

<.*>
→ tham lam, có thể lấy <a><b>

<.*?>
→ không tham lam, phần khớp đầu tiên có thể là <a>
```

## <a id="regex-performance">Quay lui (backtracking) và hiệu năng</a>

Một số mẫu có thể tạo lượng quay lui (backtracking) rất lớn trên đầu vào xấu, đặc biệt khi lượng từ lồng nhau hoặc các nhánh lựa chọn mơ hồ kết hợp không cẩn thận.

Ví dụ dạng rủi ro:

```regex
(a+)+$
```

Đầu vào dài gần khớp nhưng thất bại ở cuối có thể buộc bộ máy Regex thử rất nhiều cách phân chia trước khi kết luận.

Với regex nhận đầu vào không tin cậy:

- giữ mẫu đơn giản;
- giới hạn đầu vào khi phù hợp;
- tránh cấu trúc dễ gây quay lui bùng nổ (catastrophic backtracking);
- đo đạc/kiểm thử trường hợp xấu nhất thay vì chỉ kiểm thử đầu vào khớp đẹp.

Ngoài ra:

- tái sử dụng `Pattern` đã biên dịch khi cùng một mẫu chạy lặp lại nhiều;
- tránh dùng Regex để phân tích ngữ pháp phức tạp khi bộ phân tích cú pháp hoặc máy trạng thái rõ ràng hơn;
- đặt giới hạn kích thước/thời gian ở tầng ứng dụng nếu Regex xử lý đầu vào lớn không tin cậy;
- rà soát lượng từ lồng nhau và các nhánh lựa chọn chồng lấn.

Regex rất mạnh nhưng không phải bộ phân tích cú pháp phù hợp cho mọi ngữ pháp phức tạp.

Chương cuối của mô-đun nói về **cách viết String nhiều dòng trong mã nguồn** và tổng hợp toàn bộ mô hình xử lý String, không phải một kiểu String mới.
