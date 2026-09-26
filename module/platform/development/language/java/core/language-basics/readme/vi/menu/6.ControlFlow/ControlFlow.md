# Control Flow

Sau khi có value và biểu thức, chương trình cần quyết định **statement nào chạy, chạy bao nhiêu lần và khi nào dừng**. Đó là vai trò của control flow.

## <a id="branching-model">Nhánh if và switch</a>

`if/else` phù hợp khi điều kiện là boolean biểu thức linh hoạt:

```java
if (score >= 90) {
    grade = "A";
} else if (score >= 80) {
    grade = "B";
}
```

`switch` phù hợp khi một selector được đối chiếu với tập case rõ ràng. Với Java hiện đại, `switch` có thể là statement hoặc biểu thức.

Đừng chọn chỉ vì cú pháp ngắn hơn; hãy chọn cấu trúc làm decision model dễ đọc nhất.

## <a id="loop-control">Loop và break/continue</a>

Các loop chính:

```text
for
→ phù hợp khi có initialization/condition/update rõ

while
→ lặp khi condition còn đúng

do-while
→ body chạy ít nhất một lần
```

`break` thoát loop hiện tại; `continue` bỏ phần còn lại của iteration hiện tại và chuyển sang lần tiếp theo.

Labeled `break/continue` tồn tại nhưng thường chỉ nên dùng khi nó làm nested-loop intent rõ hơn; nếu flow quá khó theo dõi, refactor thành method nhỏ có thể tốt hơn.

## <a id="switch-expression">Switch Expression và yield</a>

Switch biểu thức trả value:

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
};
```

Với block case cần nhiều statement, `yield` trả value cho switch biểu thức:

```java
int result = switch (code) {
    case 1 -> 10;
    default -> {
        int computed = compute();
        yield computed;
    }
};
```

Arrow form tránh accidental fall-through của colon-style switch.

## <a id="control-flow-pitfalls">Control Flow dễ đọc</a>

Các dấu hiệu control flow khó bảo trì:

- nested `if` quá sâu;
- boolean condition dài với side effect;
- switch fall-through không chủ ý;
- loop có quá nhiều `break/continue` ở nhiều tầng;
- cùng condition được lặp ở nhiều nơi.

Guard clause, method extraction hoặc data/polymorphism model tốt hơn đôi khi làm flow rõ hơn.

chương tiếp theo đóng gói hành vi thành method và xem compiler chọn method call như thế nào.
