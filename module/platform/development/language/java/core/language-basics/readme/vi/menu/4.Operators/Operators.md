# Toán tử trong Java

## <a id="numeric-promotion">Numeric promotion</a>
Arithmetic không phải lúc nào cũng chạy bằng type nhìn thấy của operand. Unary/binary numeric promotion có thể nâng integer nhỏ lên `int`, kết hợp type rộng hơn và ảnh hưởng cả result type lẫn overflow behavior.

```java
byte a = 1, b = 2;
int c = a + b; // byte + byte tạo int
```

## <a id="short-circuit-operators">Short-circuit boolean operator</a>
`&&` và `||` chỉ evaluate operand phải khi cần. `&` và `|` trên boolean evaluate cả hai phía. Điều này ảnh hưởng side effect, expensive work và null-safe guard. Short-circuit là control-flow semantics, không chỉ là optimization.

## <a id="bitwise-shift">Bitwise và shift operator</a>
Bitwise operator trên integer thao tác bit pattern. `<<` shift trái, `>>` shift phải giữ sign, còn `>>>` shift phải zero-fill. Shift distance được mask theo width của operand sau promotion.

## <a id="precedence-side-effects">Precedence, evaluation order và side effect</a>
Precedence quyết định grouping, đồng thời Java quy định evaluation order. Parentheses nên thể hiện intent. Expression trộn increment, call, assignment và side effect có thể hợp lệ nhưng thường khó đọc và khó bảo trì.
