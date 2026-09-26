# Toán tử

Toán tử không chỉ là ký hiệu viết tắt. Java áp dụng promotion, evaluation order và short-circuit các quy tắc trước/khi biểu thức được thực thi.

## <a id="numeric-promotion">Numeric Promotion</a>

Trong arithmetic biểu thức, `byte`, `short` và `char` thường được promote lên `int`:

```java
byte a = 1;
byte b = 2;
int c = a + b;
```

Vì vậy `byte c = a + b;` thường không compile nếu không có constant-biểu thức quy tắc hoặc cast phù hợp.

Mixed numeric biểu thức cũng được nâng type theo binary numeric promotion các quy tắc. Hãy suy luận type của **biểu thức**, không chỉ nhìn type của từng operand.

## <a id="short-circuit-operators">Short-circuit Boolean Operator</a>

`&&` và `||` có short-circuit:

```java
user != null && user.isActive()
```

Nếu `user != null` là false, vế phải không được evaluate.

Điều này không chỉ tối ưu hiệu năng; nó thường là một phần correctness để tránh dereference không hợp lệ hoặc side effect không mong muốn.

`&` và `|` với boolean evaluate cả hai vế, nên ngữ nghĩa khác.

## <a id="bitwise-shift">Bitwise và Shift</a>

Với integer type, Java hỗ trợ:

```text
& | ^ ~
<< >> >>>
```

`>>` giữ sign bit theo arithmetic shift; `>>>` zero-fill theo logical shift.

Shift distance cũng bị mask theo độ rộng type. Bitwise mã nên đi cùng unit test rõ ràng vì lỗi sign/width thường khó nhìn bằng mắt.

## <a id="precedence-side-effects">Precedence và Evaluation Order</a>

Precedence quyết định biểu thức được **group** như thế nào; evaluation order quyết định operand được evaluate theo thứ tự nào.

Java xác định left-to-right evaluation cho operand trong nhiều biểu thức ngữ cảnh, nhưng side effect bên trong biểu thức dài vẫn làm mã khó đọc:

```java
array[i++] = i + update();
```

Nếu phải nhớ precedence phức tạp để hiểu intent, dùng parentheses hoặc tách biểu thức thành statement nhỏ hơn.

chương tiếp theo đi từ chuyển đổi ngầm trong biểu thức sang chuyển đổi có chủ ý bằng casting.
