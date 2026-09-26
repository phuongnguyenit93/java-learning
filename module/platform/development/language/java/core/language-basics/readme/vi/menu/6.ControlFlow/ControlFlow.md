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

### `if/else`: điều kiện boolean thật sự

Java không có implicit truthiness từ số hoặc reference:

```java
int count = 1;
// if (count) { } // không compile

if (count > 0) { }
```

Điều này làm condition explicit và giúp compiler kiểm tra type.

### `switch` statement và fall-through

Colon-style switch statement có thể fall-through nếu không `break`:

```java
switch (level) {
    case 1:
        enableBasic();
        break;
    case 2:
        enableAdvanced();
        break;
    default:
        disableAll();
}
```

Fall-through đôi khi có chủ ý, nhưng phải rất rõ. Arrow-style hiện đại tránh accidental fall-through và phù hợp khi mỗi case độc lập.

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

### Chọn loop theo intent

```java
for (int i = 0; i < items.size(); i++) {
    System.out.println(i + ": " + items.get(i));
} // cần index

for (String item : items) {
    System.out.println(item);
} // chỉ cần từng phần tử

while (iterator.hasNext()) {
    System.out.println(iterator.next());
} // lặp theo condition

do {
    attempt();
} while (retry); // body phải chạy ít nhất một lần
```

Enhanced-for làm intent rõ khi không cần index, nhưng biến loop không cho phép "reassign để thay phần tử" của array/collection:

```java
int[] values = {1, 2, 3};
for (int value : values) {
    value = 0; // chỉ đổi local variable value
}
```

Muốn sửa array element cần index hoặc API phù hợp.

### HOW - Loop thực sự chạy theo thứ tự nào?

Classic `for` có execution cycle:

```text
initialization       // một lần
      ↓
condition
 ├─ false → thoát loop
 └─ true
      ↓
body
      ↓
update
      ↓
quay lại condition
```

Vì vậy `continue` trong classic `for` chuyển control tới **update expression**, rồi mới kiểm tra condition lần tiếp theo:

```java
for (int i = 0; i < 3; i++) {
    if (i == 1) {
        continue; // vẫn chạy i++ trước lần check tiếp theo
    }
    System.out.println(i);
}
```

`while` kiểm tra condition trước body, nên body có thể chạy 0 lần. `do-while` chạy body trước rồi mới kiểm tra condition, nên body chạy ít nhất 1 lần.

Mental model này quan trọng hơn việc chỉ nhớ syntax, vì nó giải thích `continue`, termination và side effect trong condition/update.

## <a id="switch-expression">Switch Expression và yield</a>

Switch biểu thức trả value:

```java
String label = switch (status) {
    case NEW -> "new";
    case DONE -> "done";
    default -> "other";
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

Switch expression phải có khả năng tạo ra một value cho mọi path cần thiết. Điều này khiến decision table trở nên rõ và compiler có thể kiểm tra exhaustiveness trong các trường hợp phù hợp.

```java
int fee = switch (type) {
    case BASIC -> 10;
    case PREMIUM -> 0;
    default -> throw new IllegalArgumentException("unsupported");
};
```

`yield` chỉ dùng để trả value từ block của switch expression; nó không giống `return` khỏi method.

### Java 21: switch có thể match theo type pattern

Với Java 21, `switch` không còn chỉ là so selector với constant. Reference selector có thể được match bằng type pattern:

```java
static String describe(Object value) {
    return switch (value) {
        case Integer i -> "integer: " + i;
        case String s -> "string: " + s;
        case null -> "null";
        default -> "other";
    };
}
```

Mental model:

```text
switch selector
→ constant/enum/String-style matching khi phù hợp
→ type-pattern matching với reference value
→ binding pattern variable khi case match
→ có thể xử lý null bằng case null rõ ràng
```

`case Integer i` vừa kiểm tra runtime type vừa bind `i` với static type `Integer` trong case đó. Đây là cùng họ mental model với `instanceof Integer i`, nhưng được dùng trong decision table của `switch`.

Nếu selector reference có thể là `null`, hãy xem null là một phần contract. `case null` cho phép xử lý rõ ràng; nếu không có case phù hợp cho null thì switch trên null có thể thất bại thay vì tự chạy `default`.

Không cần biến mọi `if/else instanceof` thành pattern switch. Dùng nó khi nhiều nhánh cùng phân loại một selector và switch làm decision model rõ hơn.

## <a id="control-flow-pitfalls">Control Flow dễ đọc</a>

Các dấu hiệu control flow khó bảo trì:

- nested `if` quá sâu;
- boolean condition dài với side effect;
- switch fall-through không chủ ý;
- loop có quá nhiều `break/continue` ở nhiều tầng;
- cùng condition được lặp ở nhiều nơi.

Guard clause, method extraction hoặc data/polymorphism model tốt hơn đôi khi làm flow rõ hơn.

### Guard clause để giảm nesting

```java
if (user == null) {
    return;
}
if (!user.isActive()) {
    return;
}
process(user);
```

So với nhiều tầng `if`, guard clause thường đặt invalid/exit path lên trước và giữ happy path phẳng hơn.

### `return` là một control-flow exit

`return` không chỉ "trả value"; nó kết thúc execution của method hiện tại ngay tại điểm đó:

```java
if (!valid) {
    return;
}

process(); // chỉ chạy khi valid
```

`break` thoát loop/switch phù hợp, `continue` kết thúc iteration hiện tại, còn `return` thoát toàn bộ method. Phân biệt ba mức exit này giúp đọc nested control flow chính xác hơn.

### Loop termination phải nhìn thấy được

Với `while`/`do-while`, hãy xác định rõ state nào làm condition thay đổi. Infinite loop không phải luôn sai, nhưng nếu chủ ý thì thường cần mechanism dừng/interrupt rõ ở tầng phù hợp.

chương tiếp theo đóng gói hành vi thành method và xem compiler chọn method call như thế nào.
