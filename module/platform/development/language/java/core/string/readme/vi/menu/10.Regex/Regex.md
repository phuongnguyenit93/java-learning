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

## <a id="regex-groups">Group và Capture</a>

Parentheses có thể tạo capturing group:

```regex
(\d+)-(\w+)
```

Sau khi match, `group(1)`, `group(2)` lấy phần text được capture tương ứng.

Nếu chỉ cần grouping mà không cần capture, non-capturing group `(?:...)` có thể thể hiện intent rõ hơn.

Named group cũng hữu ích khi regex phức tạp và tên mang ý nghĩa domain.

## <a id="regex-quantifiers">Greedy và Reluctant Quantifier</a>

Quantifier như `*`, `+`, `{m,n}` mặc định thường greedy: cố lấy nhiều đầu vào nhất rồi backtrack nếu cần.

Reluctant variant như `*?`, `+?` bắt đầu với ít đầu vào hơn rồi mở rộng khi cần.

Sự khác biệt này ảnh hưởng kết quả khi nhiều vị trí match có thể hợp lệ. Hãy đọc regex cùng đầu vào example thay vì chỉ nhìn cú pháp riêng lẻ.

## <a id="regex-performance">Backtracking và Hiệu năng</a>

Một số pattern có thể tạo lượng backtracking rất lớn trên đầu vào xấu, đặc biệt khi nested quantifier/ambiguous alternative kết hợp không cẩn thận.

Với regex nhận đầu vào không tin cậy:

- giữ pattern đơn giản;
- giới hạn đầu vào khi phù hợp;
- tránh cấu trúc dễ gây catastrophic backtracking;
- benchmark/test worst-case thay vì chỉ test đầu vào match đẹp.

Regex rất mạnh nhưng không phải parser tốt cho mọi grammar phức tạp.

chương cuối của module nói về **cách viết String nhiều dòng trong source**, không phải một kiểu String mới.
