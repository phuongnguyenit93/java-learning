# Biểu thức và toán tử

Toán tử không chỉ là ký hiệu viết tắt. Java áp dụng các quy tắc về **thăng hạng kiểu**, **thứ tự đánh giá** và **đánh giá ngắn mạch** trước hoặc trong khi biểu thức được thực thi.

Các nhóm thường gặp:

- số học (arithmetic): `+`, `-`, `*`, `/`, `%` — tính toán số; `+` còn dùng để nối `String`;
- một ngôi (unary): `+`, `-`, `++`, `--`, `!`, `~` — tác động lên một toán hạng;
- so sánh: `<`, `<=`, `>`, `>=`, `==`, `!=` — tạo kết quả boolean;
- logic: `&&`, `||`, `!` — kết hợp điều kiện boolean;
- bit và dịch bit: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>` — thao tác trên bit và số nguyên;
- gán: `=`, `+=`, `-=`, `*=`, ... — gán hoặc gán kết hợp;
- điều kiện: `condition ? a : b` — chọn một giá trị biểu thức.

## <a id="numeric-promotion">Thăng hạng kiểu số</a>

Trong biểu thức số học, `byte`, `short` và `char` thường được thăng hạng lên `int`:

```java
byte a = 1;
byte b = 2;
int c = a + b;
```

Vì vậy `byte c = a + b;` thường không biên dịch được nếu không rơi vào quy tắc đặc biệt dành cho **biểu thức hằng** hoặc không có ép kiểu phù hợp.

Biểu thức trộn nhiều kiểu số cũng được nâng kiểu theo quy tắc **binary numeric promotion**. Hãy suy luận kiểu của **toàn bộ biểu thức**, không chỉ nhìn kiểu của từng toán hạng.

### Phép toán số học và chia số nguyên

```java
int a = 5 / 2;       // 2
double b = 5 / 2;    // 2.0 vì phép chia đã xảy ra bằng int
double c = 5 / 2.0;  // 2.5
```

Kiểu của toán hạng quyết định ngữ nghĩa phép toán trước khi kết quả được gán.

`%` trả phần dư theo phép toán số nguyên hoặc số thực tương ứng và thường dùng cho kiểm tra chẵn/lẻ hay logic theo chu kỳ:

```java
boolean even = number % 2 == 0;
```

### Phép gán kết hợp có chuyển đổi ngầm đặc biệt

```java
byte b = 1;
// b = b + 1; // không biên dịch: b + 1 là int
b += 1;        // hợp lệ: phép gán kết hợp có chuyển đổi về kiểu đích
```

`b += x` không hoàn toàn tương đương với việc viết lại thành `b = b + x`; quy tắc ngôn ngữ cho phép chuyển đổi ngầm tương ứng và chỉ đánh giá vế trái một lần.

## <a id="short-circuit-operators">Toán tử logic đánh giá ngắn mạch</a>

`&&` và `||` sử dụng đánh giá ngắn mạch (short-circuit):

```java
user != null && user.isActive()
```

Nếu `user != null` là `false`, vế phải không được đánh giá.

Điều này không chỉ tối ưu hiệu năng; nó còn giúp bảo đảm tính đúng đắn, chẳng hạn tránh truy cập qua tham chiếu `null` hoặc tránh tác dụng phụ không mong muốn.

`&` và `|` với boolean đánh giá cả hai vế, nên ngữ nghĩa khác.

### Điều kiện bảo vệ theo thứ tự an toàn

```java
if (user != null && user.isActive()) {
    process(user);
}
```

Đảo hai vế sẽ làm điều kiện bảo vệ mất tác dụng:

```java
// user.isActive() được đánh giá trước
// nên user == null có thể gây NPE
if (user.isActive() && user != null) { }
```

Vì đánh giá ngắn mạch quyết định **vế phải có được chạy hay không**, đừng đặt tác dụng phụ quan trọng vào vế phải nếu logic chương trình yêu cầu tác dụng phụ đó luôn xảy ra.

### Toán tử điều kiện `?:`

Toán tử điều kiện là một **biểu thức** tạo ra giá trị:

```java
String label = active ? "ACTIVE" : "INACTIVE";
```

Mô hình thực thi:

```text
đánh giá điều kiện
├─ true  → chỉ đánh giá nhánh thứ hai
└─ false → chỉ đánh giá nhánh thứ ba
```

Giống toán tử boolean đánh giá ngắn mạch, Java chỉ đánh giá **một** trong hai biểu thức kết quả:

```java
String name = user != null ? user.getName() : "anonymous";
```

Kiểu của toàn bộ biểu thức điều kiện được trình biên dịch xác định từ hai nhánh theo các quy tắc kiểu/chuyển đổi tương ứng. Với logic nhiều bước hoặc nhiều `?:` lồng nhau, `if/else` thường dễ đọc hơn.

## <a id="bitwise-shift">Toán tử bit và dịch bit</a>

Với kiểu số nguyên, Java hỗ trợ:

```text
& | ^ ~
<< >> >>>
```

`>>` giữ bit dấu theo dịch số học; `>>>` chèn bit `0` theo dịch logic.

Khoảng dịch cũng được giới hạn theo độ rộng của kiểu. Mã thao tác bit nên đi cùng kiểm thử rõ ràng vì lỗi về dấu hoặc độ rộng thường khó nhận ra bằng mắt.

Ví dụ:

```java
int flags = 0b0101;
int mask  = 0b0001;
boolean enabled = (flags & mask) != 0;
```

Toán tử bit là công cụ mức thấp. Với các cờ nghiệp vụ thông thường, `enum`/`Set` thường diễn đạt ý định rõ hơn; bitmask phù hợp khi giao thức, biểu diễn nhỏ gọn hoặc API mức thấp yêu cầu.

## <a id="precedence-side-effects">Độ ưu tiên, thứ tự đánh giá và tác dụng phụ</a>

Độ ưu tiên quyết định biểu thức được **nhóm** như thế nào; thứ tự đánh giá quyết định toán hạng nào được tính trước.

Java xác định thứ tự đánh giá toán hạng từ trái sang phải trong nhiều ngữ cảnh biểu thức, nhưng tác dụng phụ bên trong biểu thức dài vẫn làm mã khó đọc:

```java
array[i++] = i + update();
```

Nếu phải nhớ bảng độ ưu tiên phức tạp mới hiểu được ý định, hãy dùng dấu ngoặc hoặc tách biểu thức thành các câu lệnh nhỏ hơn.

### Độ ưu tiên không phải tính kết hợp

Độ ưu tiên trả lời toán tử nào nhóm chặt hơn. Tính kết hợp giải quyết cách nhóm khi các toán tử có cùng độ ưu tiên. Thứ tự đánh giá lại trả lời toán hạng nào được tính trước.

```java
int result = 2 + 3 * 4; // 14 vì * có độ ưu tiên cao hơn +
int left = 20 / 5 / 2;  // (20 / 5) / 2 = 2 do kết hợp từ trái sang phải
```

### `++`/`--`: prefix và postfix

```java
int i = 1;
int a = i++; // a = 1, i = 2
int b = ++i; // i = 3, b = 3
```

Các biểu thức ghép nhiều phép tăng/giảm có thể đúng theo đặc tả nhưng khó kiểm tra. Ưu tiên câu lệnh riêng khi giá trị trung gian có ý nghĩa.

### So sánh bằng trên giá trị nguyên thủy và tham chiếu

Với giá trị nguyên thủy, `==` so sánh giá trị sau chuyển đổi phù hợp. Với tham chiếu, `==` so sánh tính đồng nhất của tham chiếu, không thay thế `equals` khi bài toán cần so sánh giá trị theo ngữ nghĩa.

Chương tiếp theo đi từ chuyển đổi ngầm trong biểu thức sang chuyển đổi có chủ ý bằng ép kiểu (casting).
