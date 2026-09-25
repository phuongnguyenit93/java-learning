# Biểu thức chính quy

## <a id="pattern-matcher">Mental model Pattern/Matcher</a>
`Pattern` là compiled regex; `Matcher` áp pattern vào input và giữ match state. Reusable pattern nên compile một lần khi phù hợp. `matches()` yêu cầu toàn region match; `find()` tìm matching subsequence tiếp theo.

## <a id="regex-groups">Group và capture</a>
Parentheses tạo capturing group trừ khi dùng non-capturing form. Group có thể reference theo number/name và expose substring từ match thành công. Numbering theo opening parenthesis nên thêm capture có thể đổi index; named group dễ maintain hơn.

## <a id="regex-quantifiers">Greedy/reluctant quantifier</a>
Greedy consume nhiều nhất rồi backtrack; reluctant consume ít nhất nhưng vẫn cho phần còn lại match; possessive không trả input đã consume. Khác biệt này ảnh hưởng cả result lẫn performance.

## <a id="regex-performance">Backtracking và performance pitfall</a>
Nested ambiguous quantifier có thể gây catastrophic backtracking với adversarial input. Regex mạnh cho lexical pattern nhưng không luôn phù hợp parser nested/recursive format. Bound input, ưu tiên pattern deterministic đơn giản và benchmark/redesign regex chạy trên untrusted large text.
