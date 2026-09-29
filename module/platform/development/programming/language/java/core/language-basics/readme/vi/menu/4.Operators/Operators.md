# Toán tử

Toán tử không chỉ là ký hiệu viết tắt. Java áp dụng promotion, evaluation order và short-circuit các quy tắc trước/khi biểu thức được thực thi.

Các nhóm thường gặp:

- arithmetic: `+`, `-`, `*`, `/`, `%` — tính toán số; `+` còn dùng để nối String;
- unary: `+`, `-`, `++`, `--`, `!`, `~` — tác động lên một operand;
- comparison: `<`, `<=`, `>`, `>=`, `==`, `!=` — tạo kết quả boolean;
- logical: `&&`, `||`, `!` — kết hợp điều kiện boolean;
- bitwise/shift: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>` — thao tác bit/integer;
- assignment: `=`, `+=`, `-=`, `*=`, ... — gán hoặc gán-kết-hợp;
- conditional: `condition ? a : b` — chọn một expression value.

## <a id="numeric-promotion">Numeric Promotion</a>

Trong arithmetic biểu thức, `byte`, `short` và `char` thường được promote lên `int`:

```java
byte a = 1;
byte b = 2;
int c = a + b;
```

Vì vậy `byte c = a + b;` thường không compile nếu không có constant-biểu thức quy tắc hoặc cast phù hợp.

Biểu thức trộn nhiều numeric type cũng được nâng kiểu theo quy tắc binary numeric promotion. Hãy suy luận type của **toàn bộ biểu thức**, không chỉ nhìn type của từng operand.

### Arithmetic và integer division

```java
int a = 5 / 2;       // 2
double b = 5 / 2;    // 2.0 vì division đã xảy ra bằng int
double c = 5 / 2.0;  // 2.5
```

Type của operand quyết định arithmetic semantics trước khi kết quả được gán.

`%` trả remainder theo integer/floating arithmetic tương ứng và thường dùng cho parity/cycle logic:

```java
boolean even = number % 2 == 0;
```

### Compound assignment có conversion ngầm đặc biệt

```java
byte b = 1;
// b = b + 1; // không compile: b + 1 là int
b += 1;        // compile: compound assignment có conversion về target type
```

`b += x` không hoàn toàn giống text expansion `b = b + x`; language rule có implicit cast/conversion tương ứng và evaluate left side một lần.

## <a id="short-circuit-operators">Short-circuit Boolean Operator</a>

`&&` và `||` có short-circuit:

```java
user != null && user.isActive()
```

Nếu `user != null` là false, vế phải không được evaluate.

Điều này không chỉ tối ưu hiệu năng; nó thường là một phần correctness để tránh dereference không hợp lệ hoặc side effect không mong muốn.

`&` và `|` với boolean evaluate cả hai vế, nên ngữ nghĩa khác.

### Guard theo thứ tự an toàn

```java
if (user != null && user.isActive()) {
    process(user);
}
```

Đảo hai vế sẽ làm guard mất tác dụng:

```java
// user.isActive() được evaluate trước
// nên user == null có thể gây NPE
if (user.isActive() && user != null) { }
```

Vì short-circuit ảnh hưởng việc **có chạy vế phải hay không**, đừng đặt side effect quan trọng vào vế phải nếu logic chương trình phụ thuộc việc side effect luôn xảy ra.

### Conditional operator `?:`

Conditional operator là một **expression** tạo ra value:

```java
String label = active ? "ACTIVE" : "INACTIVE";
```

Execution model:

```text
evaluate condition
├─ true  → chỉ evaluate nhánh thứ hai
└─ false → chỉ evaluate nhánh thứ ba
```

Giống short-circuit boolean operator, Java chỉ evaluate **một** trong hai result expression:

```java
String name = user != null ? user.getName() : "anonymous";
```

Type của toàn bộ conditional expression được compiler xác định từ hai nhánh theo các quy tắc type/conversion tương ứng. Với logic nhiều bước hoặc nhiều nested `?:`, `if/else` thường dễ đọc hơn.

## <a id="bitwise-shift">Bitwise và Shift</a>

Với integer type, Java hỗ trợ:

```text
& | ^ ~
<< >> >>>
```

`>>` giữ sign bit theo arithmetic shift; `>>>` zero-fill theo logical shift.

Shift distance cũng bị mask theo độ rộng type. Bitwise mã nên đi cùng unit test rõ ràng vì lỗi sign/width thường khó nhìn bằng mắt.

Ví dụ:

```java
int flags = 0b0101;
int mask  = 0b0001;
boolean enabled = (flags & mask) != 0;
```

Bitwise operator là tool mức thấp. Với business flags thông thường, enum/set thường diễn đạt intent rõ hơn; bitmask hợp lý khi protocol, compact representation hoặc low-level API yêu cầu.

## <a id="precedence-side-effects">Precedence và Evaluation Order</a>

Precedence quyết định biểu thức được **group** như thế nào; evaluation order quyết định operand được evaluate theo thứ tự nào.

Java xác định thứ tự evaluate operand từ trái sang phải trong nhiều ngữ cảnh biểu thức, nhưng side effect bên trong biểu thức dài vẫn làm mã khó đọc:

```java
array[i++] = i + update();
```

Nếu phải nhớ precedence phức tạp để hiểu intent, dùng parentheses hoặc tách biểu thức thành statement nhỏ hơn.

### Precedence không phải associativity

Precedence trả lời toán tử nào group chặt hơn. Associativity giải quyết cách group khi cùng precedence. Evaluation order lại trả lời operand nào được tính trước.

```java
int result = 2 + 3 * 4; // 14 vì * có precedence cao hơn +
int left = 20 / 5 / 2;  // (20 / 5) / 2 = 2 do left associativity
```

### `++`/`--`: prefix và postfix

```java
int i = 1;
int a = i++; // a = 1, i = 2
int b = ++i; // i = 3, b = 3
```

Các expression ghép nhiều increment/decrement có thể đúng theo specification nhưng khó review. Ưu tiên statement riêng khi giá trị trung gian có ý nghĩa.

### Equality trên primitive và reference

Với primitive, `==` so primitive value sau conversion phù hợp. Với reference, `==` so identity/reference relationship, không thay thế `equals` khi domain cần value equality.

chương tiếp theo đi từ chuyển đổi ngầm trong biểu thức sang chuyển đổi có chủ ý bằng casting.
