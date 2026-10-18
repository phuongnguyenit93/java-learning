# Giá trị, kiểu nguyên thủy và kiểu tham chiếu

Java thao tác trên **giá trị có kiểu**. Chặng này xây dựng mô hình nền tảng về hai nhóm giá trị lớn: giá trị nguyên thủy và giá trị tham chiếu, cách viết giá trị trực tiếp (literal) và ý nghĩa của một giá trị tham chiếu.

## <a id="primitive-vs-reference-model">Giá trị nguyên thủy và giá trị tham chiếu</a>

### KHÁI NIỆM

Biến kiểu nguyên thủy giữ trực tiếp một giá trị nguyên thủy:

```java
int age = 20;
boolean active = true;
```

Biến tham chiếu giữ một **giá trị tham chiếu** có thể nhận diện một đối tượng/mảng hoặc mang `null`:

```java
User user = new User();
int[] values = new int[3];
```

Điểm quan trọng: Java vẫn sao chép **giá trị** khi gán hoặc truyền đối số. Với biến tham chiếu, giá trị được sao chép chính là giá trị tham chiếu.

```java
User a = new User("A");
User b = a; // sao chép giá trị tham chiếu
```

`a` và `b` giờ cùng tham chiếu tới một đối tượng `User`.

### Vì sao phải phân biệt hai mô hình này?

Rất nhiều lỗi Java cơ bản xuất phát từ việc nghĩ rằng mọi biến đều “chứa đối tượng” theo cùng một cách. Thực tế, phép gán kiểu nguyên thủy sao chép giá trị nguyên thủy, còn phép gán kiểu tham chiếu sao chép giá trị tham chiếu. Vì vậy hai biến tham chiếu có thể là **hai biến khác nhau nhưng cùng nhận diện một đối tượng**.

```java
int x = 10;
int y = x;
y++;
// x vẫn là 10

User first = new User("A");
User second = first;
second.setName("B");
// first.getName() cũng là "B"
```

Mô hình tư duy nên giữ là:

```text
ô biến
    ↓ giữ
giá trị nguyên thủy

hoặc

ô biến
    ↓ giữ
giá trị tham chiếu ─────→ đối tượng
```

Không cần và không nên mô hình hóa tham chiếu như con trỏ C/C++ có thể cộng trừ địa chỉ. Java không cho phép phép toán số học trên tham chiếu.

## <a id="primitive-ranges-and-defaults">Kiểu nguyên thủy, phạm vi và giá trị viết trực tiếp</a>

Java có tám kiểu nguyên thủy:

```text
byte, short, int, long
float, double
char
boolean
```

Các kiểu nguyên thủy số nguyên có phạm vi cố định; số chấm động tuân theo IEEE 754; `char` là một code unit UTF-16 16-bit; `boolean` biểu diễn logic `true/false`.

Literal số cũng có kiểu ở thời điểm biên dịch. Hậu tố như `L`, `F`, tiền tố hệ cơ số hoặc biểu thức hằng có thể quyết định phép gán có hợp lệ hay không.

### Các kiểu nguyên thủy cần nhớ theo vai trò

| Nhóm | Kiểu | Ghi nhớ thực tế |
|---|---|---|
| số nguyên | `byte`, `short`, `int`, `long` | `int` là lựa chọn mặc định cho phép toán số nguyên; `long` dùng khi cần phạm vi lớn hơn |
| số chấm động | `float`, `double` | literal thập phân mặc định là `double`; `float` thường cần hậu tố `F` |
| ký tự | `char` | một code unit UTF-16, không đồng nghĩa “một ký tự Unicode hoàn chỉnh” trong mọi trường hợp |
| boolean | `boolean` | chỉ mang `true` hoặc `false`, không chuyển ngầm từ/sang số |

### Phạm vi và kích thước cần nắm

Với kiểu nguyên thủy số nguyên, Java định nghĩa kích thước/phạm vi ổn định giữa các nền tảng:

| Kiểu | Kích thước | Phạm vi |
|---|---:|---|
| `byte` | 8-bit signed | `-128 .. 127` |
| `short` | 16-bit signed | `-32_768 .. 32_767` |
| `int` | 32-bit signed | `-2^31 .. 2^31 - 1` |
| `long` | 64-bit signed | `-2^63 .. 2^63 - 1` |
| `char` | 16-bit unsigned | `0 .. 65_535` UTF-16 code unit |

`float` là IEEE 754 binary32 và `double` là IEEE 754 binary64. Ở `language-basics`, điều quan trọng là nhận ra chúng là biểu diễn số chấm động có giới hạn độ chính xác; overflow/rounding/NaN/Infinity được đào sâu trong mô-đun `numbers`.

Các hằng chuẩn như `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Long.MAX_VALUE` thường rõ hơn việc ghi cứng các giá trị biên trong mã thật.

### Literal cũng có kiểu và cú pháp riêng

```java
int decimal = 10;
int hex = 0xFF;
int binary = 0b1010;
int octal = 012;          // 10 ở hệ 10

long big = 8_000_000_000L;

double ratio = 1.5;
float smallRatio = 1.5F;
double scientific = 1.2e3;

char letter = 'A';
char newline = '\n';
boolean active = true;
```

Một vài quy tắc thực tế:

- literal số nguyên mặc định thường là `int` nếu giá trị nằm trong phạm vi; dùng `L` khi cần `long`;
- literal số chấm động mặc định là `double`; dùng `F` cho `float`;
- `_` có thể làm số dễ đọc hơn nhưng chỉ được đặt ở những vị trí cú pháp hợp lệ;
- literal bắt đầu bằng `0` có thể là bát phân, nên `012` không phải số thập phân `12`;
- literal `char` dùng dấu nháy đơn, literal `String` dùng dấu nháy kép.

Ví dụ literal và kiểu ở thời điểm biên dịch:

```java
long population = 8_000_000_000L;
float ratio = 0.5F;
int hex = 0xFF;
int binary = 0b1010;
```

## <a id="reference-value-semantics">Giá trị tham chiếu hoạt động thế nào?</a>

Java không yêu cầu lập trình viên coi tham chiếu như một địa chỉ bộ nhớ vật lý có thể thao tác trực tiếp.

Mô hình tư duy đủ dùng:

```text
giá trị tham chiếu
→ có thể nhận diện đối tượng/mảng
→ có thể được sao chép
→ có thể so tính đồng nhất bằng ==
→ có thể được truy cập (dereference)
→ có thể là null
```

Một đối tượng có thể có nhiều alias. Mất một tham chiếu không đồng nghĩa đối tượng bị hủy nếu vẫn còn đường tham chiếu khác tới đối tượng đó.

### Alias, tính đồng nhất và trạng thái

```java
User a = new User("A");
User b = a;
User c = new User("A");
```

- `a` và `b` là hai biến chứa giá trị tham chiếu cùng nhận diện một đối tượng;
- `c` nhận diện đối tượng khác, dù trạng thái ban đầu có thể giống;
- `a == b` kiểm tra tính đồng nhất của tham chiếu và là `true`;
- `a == c` là `false` trong ví dụ này;
- so sánh bằng theo giá trị của đối tượng là chủ đề của `equals`, được đào sâu trong mô-đun Object Contract.

### Phép gán không sao chép đối tượng

```java
User original = new User("A");
User alias = original;
```

Dòng thứ hai **không tạo `User` mới** và không sao chép toàn bộ trường của đối tượng. Nó chỉ sao chép giá trị tham chiếu. Nếu cần đối tượng độc lập, API phải có cơ chế sao chép/constructor/factory phù hợp; đó là một quyết định thiết kế riêng.

chương tiếp theo hỏi: **một biến thuộc loại nào, tồn tại trong bao lâu, và tên của nó nhìn thấy ở đâu?**
