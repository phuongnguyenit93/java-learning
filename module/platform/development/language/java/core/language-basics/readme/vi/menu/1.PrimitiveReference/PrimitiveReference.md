# Primitive và Reference

Trước khi học `if`, method, array hay OOP, cần trả lời một câu rất cơ bản: **một biến Java thực sự đang giữ loại giá trị nào?**

Java có hai nhóm lớn trong mô hình tư duy này:

```text
giá trị primitive
→ bản thân giá trị số, logic hoặc ký tự primitive

giá trị tham chiếu (reference)
→ dùng để tham chiếu tới object/array, hoặc mang null
```

Sự khác biệt này nối xuyên suốt Java Core: phép gán, boxing, casting, lời gọi method, pass-by-value, array, `null`, equality và runtime type.

Lộ trình của module:

```text
Biến Java giữ loại giá trị nào?
Primitive vs Reference
        ↓
Tên biến tồn tại và nhìn thấy ở đâu?
Biến và Scope
        ↓
Primitive tham gia object/generic API thế nào?
Wrapper & Boxing
        ↓
Giá trị được kết hợp và chuyển kiểu thế nào?
Operators → Casting
        ↓
Chương trình chọn nhánh chạy ra sao?
Control Flow
        ↓
Hành vi được đặt tên và gọi thế nào?
Methods → Varargs → Pass-by-Value
        ↓
Dãy có kích thước cố định được biểu diễn ra sao?
Arrays
        ↓
Tên type được tổ chức thế nào?
Packages & Imports
        ↓
Tham chiếu không trỏ tới object nào nghĩa là gì?
null
        ↓
Compile-time type và runtime type nối với nhau thế nào?
Type System Mental Model
```

## <a id="primitive-vs-reference-model">Primitive và Reference</a>

### KHÁI NIỆM

Biến primitive giữ trực tiếp một giá trị primitive:

```java
int age = 20;
boolean active = true;
```

Biến reference giữ một **giá trị tham chiếu** có thể nhận diện một object/array hoặc mang `null`:

```java
User user = new User();
int[] values = new int[3];
```

Điểm quan trọng: Java vẫn sao chép **giá trị** khi gán hoặc truyền đối số. Với biến reference, giá trị được sao chép chính là giá trị tham chiếu.

```java
List<String> a = new ArrayList<>();
List<String> b = a; // sao chép giá trị tham chiếu
```

`a` và `b` giờ cùng tham chiếu một list object.

### WHY - Vì sao phải phân biệt hai mô hình này?

Rất nhiều lỗi Java cơ bản xuất phát từ việc nghĩ rằng mọi biến đều "chứa object" theo cùng một cách. Thực tế, primitive assignment sao chép primitive value, còn reference assignment sao chép reference value. Vì vậy hai biến reference có thể là **hai variable khác nhau nhưng cùng nhận diện một object**.

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

Mental model nên giữ là:

```text
variable slot
    ↓ giữ
primitive value

hoặc

variable slot
    ↓ giữ
reference value ─────→ object
```

Không cần và không nên mô hình hóa reference như con trỏ C/C++ có thể cộng trừ địa chỉ. Java không cho arithmetic trên reference.

## <a id="primitive-ranges-and-defaults">Phạm vi và giá trị mặc định</a>

Java có tám primitive type:

```text
byte, short, int, long
float, double
char
boolean
```

Primitive số nguyên có phạm vi cố định; floating-point tuân theo IEEE 754; `char` là một UTF-16 code unit 16-bit; `boolean` biểu diễn logic `true/false`.

Field nhận giá trị mặc định khi trạng thái của object hoặc class được khởi tạo, ví dụ số là `0`, boolean là `false`, reference là `null`. Biến cục bộ **không tự có giá trị mặc định để đọc ngay**; compiler yêu cầu definite assignment trước khi sử dụng.

Numeric literal cũng có kiểu ở compile time. Suffix như `L`, `F`, tiền tố radix hoặc constant expression có thể quyết định phép gán có hợp lệ hay không.

### Các primitive cần nhớ theo vai trò

| Nhóm | Type | Ghi nhớ thực tế |
|---|---|---|
| integer | `byte`, `short`, `int`, `long` | `int` là lựa chọn mặc định cho integer arithmetic; `long` dùng khi cần phạm vi lớn hơn |
| floating point | `float`, `double` | literal thập phân mặc định là `double`; `float` thường cần suffix `F` |
| character | `char` | một UTF-16 code unit, không đồng nghĩa "một Unicode character hoàn chỉnh" trong mọi trường hợp |
| boolean | `boolean` | chỉ mang `true` hoặc `false`, không chuyển ngầm từ/to số |

### Range và kích thước cần nắm

Với integer primitive, Java định nghĩa kích thước/range ổn định giữa các platform:

| Type | Kích thước | Range |
|---|---:|---|
| `byte` | 8-bit signed | `-128 .. 127` |
| `short` | 16-bit signed | `-32_768 .. 32_767` |
| `int` | 32-bit signed | `-2^31 .. 2^31 - 1` |
| `long` | 64-bit signed | `-2^63 .. 2^63 - 1` |
| `char` | 16-bit unsigned | `0 .. 65_535` UTF-16 code unit |

`float` là IEEE 754 binary32 và `double` là IEEE 754 binary64. Ở `language-basics`, điều quan trọng là nhận ra chúng là floating-point representation có giới hạn precision; overflow/rounding/NaN/Infinity được đào sâu trong module `numbers`.

Các constant chuẩn như `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Long.MAX_VALUE` thường rõ hơn việc hard-code boundary number trong code thật.

### Literal cũng có type và cú pháp riêng

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

Một vài rule thực tế:

- integer literal mặc định thường là `int` nếu value fit; dùng `L` khi cần `long`;
- floating-point literal mặc định là `double`; dùng `F` cho `float`;
- `_` có thể làm số dễ đọc hơn nhưng chỉ được đặt ở những vị trí cú pháp hợp lệ;
- literal bắt đầu bằng `0` có thể là octal, nên `012` không phải decimal `12`;
- `char` literal dùng single quote, `String` literal dùng double quote.

Ví dụ literal và compile-time type:

```java
long population = 8_000_000_000L;
float ratio = 0.5F;
int hex = 0xFF;
int binary = 0b1010;
```

### Field default khác local variable

```java
class Sample {
    int count;       // 0
    boolean active;  // false
    User user;       // null

    void run() {
        int local;
        // System.out.println(local); // compile error: chưa definite-assigned
    }
}
```

Default value là một phần của object/class initialization. Nó **không** phải lý do để bỏ qua việc khởi tạo state có ý nghĩa domain.

## <a id="reference-value-semantics">Giá trị tham chiếu</a>

Java không yêu cầu lập trình viên coi reference như một địa chỉ bộ nhớ vật lý có thể thao tác trực tiếp.

Mô hình tư duy đủ dùng:

```text
giá trị tham chiếu
→ có thể nhận diện object/array
→ có thể được sao chép
→ có thể so identity bằng ==
→ có thể được dereference
→ có thể là null
```

Một object có thể có nhiều alias. Mất một reference không đồng nghĩa object bị hủy nếu vẫn còn đường tham chiếu khác tới object đó.

### Alias, identity và state

```java
User a = new User("A");
User b = a;
User c = new User("A");
```

- `a` và `b` là hai variable chứa reference value cùng nhận diện một object;
- `c` nhận diện object khác, dù state ban đầu có thể giống;
- `a == b` kiểm tra reference identity và là `true`;
- `a == c` là `false` trong ví dụ này;
- value equality của object là chủ đề của `equals`, được đào sâu trong module Object Contract.

### Assignment không clone object

```java
User original = new User("A");
User alias = original;
```

Dòng thứ hai **không tạo `User` mới** và không copy toàn bộ field của object. Nó chỉ copy reference value. Nếu cần object độc lập, API phải có cơ chế copy/constructor/factory phù hợp; đó là một quyết định thiết kế riêng.

chương tiếp theo hỏi: **một biến thuộc loại nào, tồn tại trong bao lâu, và tên của nó nhìn thấy ở đâu?**
