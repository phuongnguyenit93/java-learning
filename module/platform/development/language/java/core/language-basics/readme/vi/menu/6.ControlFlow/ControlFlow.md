# Luồng điều khiển

## <a id="branching-model">Mô hình nhánh if/switch</a>
`if` chọn path từ boolean condition. `switch` chọn giữa các alternative rời rạc và modern switch expression có thể tạo value. Nên chọn cấu trúc làm các business rule loại trừ nhau rõ ràng thay vì nested condition quá sâu.

## <a id="loop-control">for/while/do-while và break/continue</a>
`for` phù hợp khi initialization/update thuộc loop; enhanced `for` iterate `Iterable` hoặc array; `while` kiểm tra trước mỗi vòng; `do-while` chạy body ít nhất một lần. `break` thoát và `continue` bỏ qua phần còn lại của iteration. Labeled control flow có tồn tại nhưng nên dùng hiếm.

## <a id="switch-expression">Switch expression và yield</a>
Switch expression dùng `->` hoặc `yield` để trả value và tránh accidental fall-through. Exhaustiveness quan trọng khi compiler biết toàn bộ alternative, ví dụ enum.

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
};
```

## <a id="control-flow-pitfalls">Pitfall và readability của control flow</a>
Các lỗi phổ biến gồm classic-switch fall-through ngoài ý muốn, loop termination phụ thuộc hidden mutation, condition lặp lại và nested branch quá sâu. Guard clause và method nhỏ thường làm invariant rõ hơn.
