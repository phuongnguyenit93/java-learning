<a id="back-to-top"></a>

# Số học nguyên

## Menu
- [Cộng, trừ, nhân và kiểu của kết quả](#integer-arithmetic-001)
- [Chia số nguyên và việc cắt phần thập phân về phía 0](#integer-arithmetic-002)
- [Toán tử % và dấu của số dư](#integer-arithmetic-003)
- [Chia hoặc lấy dư cho 0 với số nguyên](#integer-arithmetic-004)
- [Phép toán đơn ngôi, tiền tố và hậu tố ++, --](#integer-arithmetic-005)
- [Độ ưu tiên, tính kết hợp và thứ tự đánh giá toán hạng](#integer-arithmetic-006)
- [Dấu ngoặc và tác động tới giá trị trung gian](#integer-arithmetic-007)
- [floorDiv, floorMod và khác biệt với /, %](#integer-arithmetic-008)
- [Trường hợp MIN_VALUE chia cho -1](#integer-arithmetic-009)
- [Nối chuỗi bằng + khác phép cộng như thế nào](#integer-arithmetic-010)

## <a id="integer-arithmetic-001">Cộng, trừ, nhân và kiểu của kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Cộng, trừ, nhân và kiểu của kết quả trong số học nguyên Java

## 1. Câu hỏi quan trọng: phép tính chạy bằng kiểu nào?

Với biểu thức số nguyên trong Java, **kiểu của kết quả được xác định từ toán hạng và quy tắc thăng cấp số học** (*numeric promotion*). Kiểu của biến nằm **bên trái phép gán** không tự làm phép cộng, trừ hoặc nhân ở vế phải chạy trên kiểu rộng hơn.

Ví dụ:

```java
int a = 1_500_000_000;
int b = 2;

int wrapped = a * b;        // phép nhân int, tràn số
long stillWrapped = a * b;  // phép nhân vẫn là int, rồi mới đổi sang long
long correct = (long) a * b; // phép nhân long, kết quả chính xác
```

Ở đây, giá trị toán học của \(1\,500\,000\,000 \times 2\) là \(3\,000\,000\,000\), lớn hơn `Integer.MAX_VALUE`. Hai biểu thức `a * b` đầu tiên có **kiểu `int`**, đều cho kết quả -1_294_967_296 do tràn; biến `long` chỉ chứa bản mở rộng có dấu của kết quả đã tràn. Biểu thức `(long) a * b` được thực hiện bằng `long` và cho 3_000_000_000.

Bài học tập trung vào ba toán tử nhị phân:

- `+`: cộng.
- `-`: trừ.
- `*`: nhân.

Chúng áp dụng cho các kiểu số nguyên `byte`, `short`, `char`, `int` và `long`. Với toán hạng nguyên thủy, phải hiểu **thăng cấp**, **miền giá trị**, **tràn số**, **chuyển kiểu**, và **thứ tự đánh giá** để dự đoán đúng kết quả.

## 2. Quy tắc thăng cấp kiểu của phép cộng, trừ và nhân

### 2.1. Thăng cấp số học nhị phân (*binary numeric promotion*)

Đối với hai toán hạng có kiểu tích phân nguyên thủy, Java áp dụng các quy tắc trọng tâm:

1. `byte`, `short` và `char` được **thăng cấp thành `int`** trước khi thực hiện phép cộng, trừ hoặc nhân.
2. Nếu một trong hai toán hạng là `long`, toán hạng còn lại được chuyển thành `long` và **kết quả có kiểu `long`**.
3. Nếu không có `long`, kết quả có **kiểu `int`**.

Bảng minh họa:

| Kiểu toán hạng trái | Kiểu toán hạng phải | Kiểu của `a + b`, `a - b`, `a * b` |
|---|---|---|
| `byte` | `byte` | `int` |
| `short` | `short` | `int` |
| `byte` | `short` | `int` |
| `char` | `char` | `int` |
| `char` | `int` | `int` |
| `int` | `int` | `int` |
| `int` | `long` | `long` |
| `long` | `long` | `long` |

Quy tắc không phụ thuộc **giá trị thực tế đang nhỏ hay lớn**. Ví dụ `byte x = 1; byte y = 2;` thì `x + y` có kiểu `int` dù số 3 hoàn toàn nằm trong miền `byte`.

Trong phạm vi bài này chỉ xét toán hạng tích phân. Nếu một toán hạng là `float` hoặc `double`, quy tắc *binary numeric promotion* rộng hơn sẽ cho kết quả số thực; không dùng bảng trên để suy luận về biểu thức trộn số nguyên và số thực.

### 2.2. Kiểu của biểu thức khác kiểu của biến nhận kết quả

```java
byte first = 10;
byte second = 20;

// byte sum = first + second; // LỖI: kết quả phép cộng có kiểu int
int sum = first + second;     // hợp lệ, sum = 30
byte narrowed = (byte) (first + second); // hợp lệ khi ép kiểu chủ động

long x = 1L + 2;  // 1L là long => phép cộng cho long
long y = 1 + 2;   // phép cộng cho int (3), sau đó mới nới rộng thành long
```

Dòng `long y = 1 + 2;` chạy đúng vì 3 không tràn, nhưng không nên kết luận phép cộng đã được tính bằng `long`.

### 2.3. Dấu cộng/trừ một ngôi cũng có thăng cấp

Các toán tử dấu `+x`, `-x` chỉ có **một toán hạng**. Khi `x` là `byte`, `short` hoặc `char`, kết quả dấu một ngôi có kiểu `int`; `long` vẫn là `long`.

```java
byte b = 5;
int negative = -b;   // -b có kiểu int, giá trị -5
char c = 'A';
int code = +c;       // +c có kiểu int, giá trị 65
long n = 10L;
long flipped = -n;   // kiểu long, giá trị -10
```

Không đánh đồng `-x` một ngôi với `a - b` hai ngôi; cả hai có thể tràn nếu kết quả toán học vượt miền biểu diễn tương ứng.

## 3. Cách tính cộng, trừ và nhân

### 3.1. Cộng (`+`)

Phép `a + b` tính tổng. Ví dụ `7 + 5` là 12. Khi cả hai toán hạng là `int`, phép tính được thực hiện trong miền số nguyên 32 bit. Nếu có `long`, phép tính được thực hiện trong miền 64 bit.

Dấu `+` còn dùng để **nối chuỗi**: `"total=" + 5` tạo chuỗi `"total=5"`. Đây không phải phép cộng số nguyên; luôn xét kiểu toán hạng trước khi suy luận về `+`. Ví dụ, `"sum=" + 2 + 3` cho `"sum=23"` vì phép toán đánh giá từ trái sang phải; `"sum=" + (2 + 3)` mới cho `"sum=5"`.

### 3.2. Trừ (`-`)

Phép `a - b` tính hiệu; `7 - 5` là 2. Khi trừ số âm, giá trị toán học có thể tăng; ví dụ `7 - (-5)` bằng 12. Đừng chỉ kiểm tra trường hợp cộng số dương khi đánh giá nguy cơ tràn: `Integer.MIN_VALUE - 1` cũng tràn.

### 3.3. Nhân (`*`)

Phép `a * b` tính tích; `7 * 5` là 35. Phép nhân dễ vượt miền ngay cả khi **mỗi toán hạng đều hợp lệ**. Ví dụ, mỗi toán hạng 1_500_000_000 đều là `int` hợp lệ nhưng tích với 2 không còn vừa `int`.

Số 0 là phần tử hấp thụ của phép nhân nguyên: `x * 0` luôn bằng 0. Số 1 là phần tử đơn vị: `x * 1` có cùng giá trị x. Tuy nhiên, kết quả biểu thức vẫn theo quy tắc thăng cấp kiểu; `byte x` nhân `1` cho biểu thức kiểu `int`.

## 4. Độ ưu tiên, kết hợp và thứ tự đánh giá

Phép nhân `*` có **độ ưu tiên cao hơn** phép cộng/trừ `+`, `-`; cộng và trừ cùng mức và kết hợp từ trái sang phải:

```java
int a = 2 + 3 * 4;   // 14
int b = (2 + 3) * 4; // 20
int c = 10 - 3 - 2;  // (10 - 3) - 2 = 5
int d = 10 - (3 - 2); // 9
```

Java đánh giá các toán hạng của toán tử nhị phân theo **thứ tự từ trái sang phải**; với `a() + b()`, `a()` được đánh giá trước `b()`. Đây là thứ tự đánh giá toán hạng, không làm thay đổi độ ưu tiên toán tử.

Ngoài ra, **các phép toán nguyên có thể tràn**, nên biến đổi đại số chỉ an toàn nếu vẫn giữ ý nghĩa nghiệp vụ. Ví dụ, để tránh tràn, `(long) a + b` khác `(long) (a + b)` khi tổng `int` vượt miền. Cả hai có cùng dạng gần như đại số, nhưng thời điểm nâng kiểu khác nhau.

## 5. Ví dụ Java chạy được: giá trị và kiểu kết quả

Tạo tệp `IntegerArithmeticDemo.java`:

```java
public class IntegerArithmeticDemo {
    private static String typeOf(int value) {
        return "int";
    }

    private static String typeOf(long value) {
        return "long";
    }

    public static void main(String[] args) {
        byte smallA = 120;
        byte smallB = 10;
        short shortA = 30_000;
        short shortB = 1_000;
        char letter = 'A';
        int big = 1_500_000_000;
        int multiplier = 2;

        System.out.println("byte + byte = " + (smallA + smallB));
        System.out.println("type byte + byte = " + typeOf(smallA + smallB));
        System.out.println("short * short = " + (shortA * shortB));
        System.out.println("char + int = " + (letter + 1));
        System.out.println("type char + int = " + typeOf(letter + 1));
        System.out.println("type int + long = " + typeOf(1 + 2L));
        System.out.println("int * int = " + (big * multiplier));
        System.out.println("assigned to long = " + (long) (big * multiplier));
        System.out.println("long * int = " + ((long) big * multiplier));
        System.out.println("max + 1 = " + (Integer.MAX_VALUE + 1));
        System.out.println("min - 1 = " + (Integer.MIN_VALUE - 1));
        System.out.println("2 + 3 * 4 = " + (2 + 3 * 4));
        System.out.println("(2 + 3) * 4 = " + ((2 + 3) * 4));
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac IntegerArithmeticDemo.java
java IntegerArithmeticDemo
```

Kết quả chính xác:

```text
byte + byte = 130
type byte + byte = int
short * short = 30000000
char + int = 66
type char + int = int
type int + long = long
int * int = -1294967296
assigned to long = -1294967296
long * int = 3000000000
max + 1 = -2147483648
min - 1 = 2147483647
2 + 3 * 4 = 14
(2 + 3) * 4 = 20
```

Hai phương thức `typeOf` overload chỉ để **chứng minh kiểu tĩnh mà compiler chọn**: nếu đối số là `int`, gọi `typeOf(int)`; nếu là `long`, gọi `typeOf(long)`. Các số 130 và 30_000_000 chứng minh kết quả của `byte + byte` và `short * short` được tính trên `int`, thay vì tự giới hạn vào miền `byte`/`short`.

## 6. Overflow và underflow: giá trị quay vòng ở độ rộng cố định

### 6.1. Miền biểu diễn liên quan

| Kiểu kết quả của phép toán | Miền số có dấu |
|---|---|
| `int` (32 bit) | -2_147_483_648 tới 2_147_483_647 |
| `long` (64 bit) | -9_223_372_036_854_775_808 tới 9_223_372_036_854_775_807 |

Với `+`, `-`, `*` nguyên, Java tính kết quả theo **độ rộng của kiểu phép toán**. Nếu kết quả toán học không nằm trong miền của kiểu có dấu, phần bit cao vượt độ rộng bị loại bỏ và mẫu bit còn lại được diễn giải theo bù hai (*two's complement*). Có thể hình dung kết quả được lấy modulo \(2^{32}\) đối với `int` hoặc \(2^{64}\) đối với `long`, rồi diễn giải có dấu.

Ví dụ:

- `Integer.MAX_VALUE + 1` → `Integer.MIN_VALUE`.
- `Integer.MIN_VALUE - 1` → `Integer.MAX_VALUE`.
- `1_500_000_000 * 2` → -1_294_967_296 (phép nhân `int`).
- `Long.MAX_VALUE + 1L` → `Long.MIN_VALUE`.

Cả **overflow** (vượt giới hạn lớn nhất) và **underflow ở phía âm** (nhỏ hơn giới hạn dưới) đều có thể làm sai giá trị mong đợi trong số học nguyên. Những phép `+`, `-`, `*` thông thường **không tự ném ngoại lệ** khi vượt miền.

### 6.2. Nới rộng kiểu trước hay sau phép tính

Để một phép tính chạy bằng `long`, ít nhất một toán hạng phải là `long` **trước khi toán tử thực thi**:

```java
int a = 1_500_000_000;
int b = 2;

long wrong = a * b;             // -1294967296 (int đã tràn)
long alsoWrong = (long) (a * b); // -1294967296
long right = (long) a * b;      // 3000000000
long alsoRight = a * 2L;        // 3000000000
```

Việc gán sang `long` sau phép tính không khôi phục các bit đã mất. Ngay cả `long` cũng có giới hạn: nếu tích lớn hơn `Long.MAX_VALUE` hoặc nhỏ hơn `Long.MIN_VALUE`, phép nhân `long` vẫn có thể tràn.

### 6.3. Số nhỏ nhất đổi dấu vẫn tràn

Miền số có dấu **không đối xứng**: `Integer.MIN_VALUE` là -2_147_483_648, nhưng `Integer.MAX_VALUE` chỉ 2_147_483_647. Do đó:

```java
int min = Integer.MIN_VALUE;
System.out.println(-min);              // -2147483648
System.out.println(min * -1);           // -2147483648
System.out.println(Math.abs(min));      // -2147483648
```

Các trường hợp này đều không tạo ra số dương như trực giác toán học vì +2_147_483_648 không có trong miền `int`. `Long.MIN_VALUE` cũng có tính chất tương tự đối với `long`.

## 7. Kiểm tra tràn số bằng các phương thức `Math.*Exact`

Nếu tràn số là **lỗi nghiệp vụ**, Java cung cấp phép toán kiểm tra:

| Phương thức | Công dụng |
|---|---|
| `Math.addExact(a, b)` | Cộng, ném `ArithmeticException` nếu tràn |
| `Math.subtractExact(a, b)` | Trừ, ném ngoại lệ nếu tràn |
| `Math.multiplyExact(a, b)` | Nhân, ném ngoại lệ nếu tràn |
| `Math.negateExact(value)` | Đổi dấu, ném ngoại lệ với giá trị nhỏ nhất |
| `Math.toIntExact(longValue)` | Thu hẹp `long` sang `int`, ném ngoại lệ nếu ngoài miền |

`addExact`, `subtractExact` và `multiplyExact` có các overload cho số nguyên với độ rộng phù hợp; kiểu kết quả của overload nhận hai `int` là `int`, hai `long` là `long`. `Math.multiplyExact` cũng có overload cho `long` và `int` trong Java hiện đại.

Tạo tệp `CheckedArithmeticDemo.java`:

```java
public class CheckedArithmeticDemo {
    public static void main(String[] args) {
        System.out.println("safe sum = " + Math.addExact(7, 5));
        System.out.println("safe diff = " + Math.subtractExact(7, 5));
        System.out.println("safe product = " + Math.multiplyExact(7, 5));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("addExact: overflow");
        }

        try {
            Math.subtractExact(Integer.MIN_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("subtractExact: overflow");
        }

        try {
            Math.multiplyExact(1_500_000_000, 2);
        } catch (ArithmeticException e) {
            System.out.println("multiplyExact: overflow");
        }

        long exactLong = (long) 1_500_000_000 * 2;
        System.out.println("long product = " + exactLong);

        try {
            Math.toIntExact(exactLong);
        } catch (ArithmeticException e) {
            System.out.println("toIntExact: outside int range");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CheckedArithmeticDemo.java
java CheckedArithmeticDemo
```

Kết quả:

```text
safe sum = 12
safe diff = 2
safe product = 35
addExact: overflow
subtractExact: overflow
multiplyExact: overflow
long product = 3000000000
toIntExact: outside int range
```

**Không bắt `ArithmeticException` rồi tiếp tục sử dụng một kết quả số đã sai.** Hãy xác định cách xử lý lỗi phù hợp: thông báo dữ liệu vượt giới hạn, từ chối phép tính, hoặc chuyển sang một cách biểu diễn số rộng hơn.

Nếu nghiệp vụ yêu cầu số nguyên vượt cả miền `long`, dùng `java.math.BigInteger`:

```java
import java.math.BigInteger;

BigInteger a = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger bigger = a.add(BigInteger.ONE);  // 9223372036854775808
BigInteger squared = bigger.multiply(bigger);
```

Đoạn mã `BigInteger` chỉ minh họa **các biểu thức đặt trong một phương thức**, không phải một tệp Java độc lập. `BigInteger` dùng các phương thức như `add`, `subtract`, `multiply` và không hỗ trợ toán tử `+`/`-`/`*` cho hai đối tượng.

## 8. Phép gán, ép kiểu và toán tử gán kết hợp

### 8.1. Gán thông thường yêu cầu kiểu tương thích

```java
byte x = 10;
byte y = 20;

// byte total = x + y; // LỖI BIÊN DỊCH: x + y là int
int total = x + y;     // hợp lệ
byte narrowed = (byte) (x + y); // ép kiểu tường minh
```

Nếu tổng `int` không nằm trong miền `byte`, phép ép sẽ chỉ giữ 8 bit thấp và có thể làm **thay đổi giá trị**:

```java
byte x = 120;
byte y = 10;
byte result = (byte) (x + y); // 130 -> -126
```

### 8.2. `+=`, `-=` và `*=` có ép kiểu ngầm

Với toán tử gán kết hợp, Java thực hiện một phép toán trên các toán hạng đã thăng cấp rồi **chuyển kết quả về kiểu của biến vế trái**, tương tự:

```java
// Với biến kiểu T:
x += y;          // gần tương đương: x = (T) (x + y)
x -= y;          // gần tương đương: x = (T) (x - y)
x *= y;          // gần tương đương: x = (T) (x * y)
```

Chữ `T` chỉ là ký hiệu kiểu trong **mã giả minh họa**. Hai cách viết không hoàn toàn tương đương khi `x` là biểu thức có tác dụng phụ: toán tử gán kết hợp **chỉ đánh giá vế trái một lần**. Điểm cần nhớ ở đây là phép chuyển kiểu ngầm có thể làm mất bit.

Ví dụ:

```java
byte b = 120;
b += 10;  // tương đương về giá trị với (byte) (120 + 10)
System.out.println(b); // -126

short s = 30_000;
s *= 2; // phép nhân thành int rồi thu hẹp về short
System.out.println(s); // -5536
```

Ở ví dụ `short`, số 60_000 vượt miền `short` (-32_768..32_767), và việc giữ 16 bit thấp cho -5_536. **`+=` và `*=` không tự bảo vệ chống tràn hoặc thu hẹp kiểu.**

### 8.3. Biểu thức hằng số khi biên dịch là ngoại lệ cần biết

Java cho phép một số **biểu thức hằng** kiểu `int` gán trực tiếp vào `byte`, `short` hoặc `char` nếu giá trị biểu diễn được:

```java
byte a = 100 + 20;       // hợp lệ: biểu thức hằng int bằng 120
short b = 10_000 * 3;    // hợp lệ: hằng int bằng 30000
char c = 'A' + 1;        // hợp lệ: hằng int bằng 66, nằm trong miền char

// byte bad = 100 + 30;  // lỗi biên dịch: 130 vượt miền byte

int variable = 20;
// byte another = 100 + variable; // lỗi: không còn là hằng biểu diễn được
```

Dòng `char c = 'A' + 1;` biên dịch được vì kết quả là **hằng số `int` 66** được phép chuyển hẹp ở bước gán. Nó **không** chứng minh toán tử cộng trên `char` trả `char`: kiểu của biểu thức cộng vẫn là `int`. Một `char current` không phải hằng số biên dịch thông thường muốn tăng bằng phép `+` rồi gán lại `char` sẽ cần ép kiểu hoặc `+=`.

## 9. Kết hợp với literal, wrapper và autoboxing

### 9.1. Literal quyết định kiểu trung gian

```java
long a = 2_000_000_000 + 1_000_000_000;  // phép cộng int bị tràn
long b = 2_000_000_000L + 1_000_000_000; // phép cộng long = 3000000000
```

Dấu `L` biến literal đầu thành `long`, do đó toán hạng còn lại được nâng lên `long` và kết quả phép cộng có kiểu `long`. Nếu muốn ép rõ ràng, viết `(long) first + second` thay vì `(long) (first + second)`.

### 9.2. Wrapper được unbox trước khi tính

Khi dùng `Integer`, `Long` hoặc các wrapper khác trong biểu thức số học, Java có thể **unbox** thành giá trị nguyên thủy rồi áp dụng thăng cấp số học:

```java
Integer a = 10;
Long b = 20L;
long sum = a + b; // unbox int/long, thăng cấp lên long => 30
```

Nếu một wrapper là `null`, unboxing sẽ ném `NullPointerException` **trước khi phép toán hoàn tất**. Vì thế cần kiểm tra null khi dữ liệu wrapper có thể không có giá trị.

Không dùng `Integer`/`Long` để kỳ vọng tự động tránh tràn: sau unboxing, các phép toán vẫn chịu miền `int`/`long` tương ứng.

## 10. Cách áp dụng an toàn trong chương trình

Có ba tình huống chính:

**Tình huống A — chắc chắn phép toán vừa `int`:** dùng `int` bình thường, nhưng dựa trên ràng buộc miền đầu vào có thể kiểm tra được. Ví dụ hai số đếm nhỏ trong khoảng 0..1000 sẽ có tổng nhiều nhất 2000.

**Tình huống B — `int` có thể tràn nhưng `long` đủ rộng:** chủ động nâng toán hạng **trước khi tính**, chẳng hạn `long total = (long) quantity * unitPrice;` khi cả hai biến nguồn là `int`. Kiểm tra thêm ràng buộc nghiệp vụ nếu `long` cũng có khả năng vượt miền.

**Tình huống C — tràn số phải được phát hiện:** dùng `Math.addExact`, `subtractExact`, `multiplyExact` hoặc thiết kế dữ liệu với `BigInteger` khi miền không cố định. Không coi kết quả phép tính tràn số là dữ liệu hợp lệ nếu nghiệp vụ không có quy tắc wraparound.

Đối với yêu cầu đếm byte, thời gian, tổng tiền, tích số lượng và giá đơn vị, nên xác định **giá trị lớn nhất hợp lệ** trước khi chọn kiểu. Nếu cần số tiền thập phân, độ chính xác của tiền tệ là vấn đề riêng: thường cân nhắc `BigDecimal` hoặc lưu đơn vị tiền nhỏ nhất dưới dạng số nguyên theo quy ước rõ ràng.

## 11. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| `byte result = a + b;` báo lỗi | `byte + byte` cho kiểu `int` | Dùng `int` hoặc ép kiểu có kiểm tra |
| `long total = a * b;` vẫn sai | Hai `int` nhân trước rồi mới gán sang `long` | Viết `(long) a * b` |
| `Integer.MAX_VALUE + 1` thành số âm | Tràn `int` 32 bit | Dùng `long` trước phép cộng hoặc `addExact` |
| `Integer.MIN_VALUE - 1` thành số dương | Vượt giới hạn âm `int` | Dùng kiểu rộng hơn hoặc `subtractExact` |
| `-Integer.MIN_VALUE` vẫn âm | Giá trị dương đối xứng ngoài miền `int` | Dùng `negateExact` hoặc chuyển sang `long` trước |
| `b += 10` làm `byte` thành âm | Gán kết hợp thu hẹp kết quả ngầm | Tính trên `int`, kiểm tra miền trước khi ép |
| `Math.multiplyExact(a, b)` ném lỗi | Tích ngoài miền kiểu của overload | Chọn `long`/`BigInteger` hoặc xử lý overflow |
| `"sum=" + 2 + 3` in `sum=23` | Toán tử `+` đang nối chuỗi từ trái sang phải | Viết `"sum=" + (2 + 3)` |
| Wrapper số học gây `NullPointerException` | Unboxing giá trị `null` | Kiểm tra wrapper trước phép toán |

## 12. Tự kiểm tra

**Câu 1.** `byte a = 1; byte b = 2;` thì `a * b` có kiểu gì?  
**Đáp án:** `int`, dù tích bằng 2 và vừa trong `byte`.

**Câu 2.** `short a = 100; short b = 20;` thì `a - b` có kiểu gì?  
**Đáp án:** `int`, giá trị 80.

**Câu 3.** `long x = Integer.MAX_VALUE + 1;` có giá trị bao nhiêu?  
**Đáp án:** -2_147_483_648, vì phép cộng hai `int` tràn trước khi gán sang `long`.

**Câu 4.** Viết biểu thức nhân hai biến `int` `price` và `count` nhưng tính bằng `long`.  
**Đáp án:** `(long) price * count` hoặc `price * (long) count`.

**Câu 5.** `byte b = 120; b += 10;` cho kết quả gì?  
**Đáp án:** -126 vì biểu thức cộng bằng `int` cho 130, sau đó bị thu hẹp về `byte`.

**Câu 6.** Khi nào `Math.multiplyExact` phù hợp hơn `*`?  
**Đáp án:** Khi tích vượt miền số nguyên phải được coi là lỗi và cần phát hiện bằng `ArithmeticException`.

**Câu 7.** Vì sao `char c = 'A' + 1;` hợp lệ nhưng một biểu thức `charVariable + 1` có kiểu `int`?  
**Đáp án:** Cả hai phép cộng đều cho kiểu `int`. Ở trường hợp đầu, đó là hằng số `int` 66 nằm trong miền `char` nên compiler cho phép phép gán thu hẹp đặc biệt.

**Câu 8.** `10 - 3 - 2` và `10 - (3 - 2)` khác nhau thế nào?  
**Đáp án:** Lần lượt là 5 và 9, vì `-` nhị phân kết hợp trái; dấu ngoặc thay đổi thứ tự phép toán.

**Ghi nhớ:** Với số nguyên Java, **`byte`/`short`/`char` được thăng cấp thành `int` khi cộng, trừ, nhân; có `long` thì kết quả `long`**. Kết quả được tính ở kiểu của **biểu thức**, sau đó mới được gán. Muốn tránh tràn, nâng kiểu **trước phép tính** hoặc dùng `Math.*Exact` để phát hiện và xử lý kết quả ngoài miền.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-002">Chia số nguyên và việc cắt phần thập phân về phía 0</a>

<details>
<summary>Xem chi tiết</summary>

# Chia số nguyên trong Java: Vì sao phần thập phân bị cắt về phía 0?

Khi **cả hai toán hạng của `/` đều là số nguyên**, Java thực hiện **phép chia số nguyên**. Kết quả là một số nguyên: phần phân số của thương toán học bị **loại bỏ theo hướng về 0** (*truncation toward zero*). Vì vậy:

- `7 / 3` → `2`, vì thương toán học là khoảng `2.333...`.
- `-7 / 3` → `-2`, vì thương toán học là khoảng `-2.333...`.
- `1 / 2` → `0`, không phải `0.5`.

Đây là quy tắc ngữ nghĩa của phép toán, **không phải lỗi thiếu độ chính xác hay lỗi làm tròn ngẫu nhiên**. Để tính thương có phần thập phân, phải làm cho **ít nhất một toán hạng** có kiểu số thực trước lúc thực hiện phép chia.

## 1. Phép `/` làm gì với các kiểu số nguyên?

Các kiểu tích phân nguyên thủy của Java gồm `byte`, `short`, `char`, `int` và `long`. Với biểu thức `a / b` mà cả hai đều thuộc nhóm số nguyên:

1. Java xác định kiểu tính toán bằng **binary numeric promotion**. `byte` và `short` được nâng lên ít nhất `int`. Nếu có một toán hạng `long` thì kết quả phép chia nguyên theo `long`.
2. Máy thực hiện phép chia số nguyên trên hai giá trị nguyên.
3. Thương toán học được **cắt phần phân số về 0**. Giá trị trả về có kiểu nguyên (`int` hoặc `long`); không có phần thập phân để giữ lại.

| Biểu thức | Thương toán học | Giá trị Java | Giải thích |
| --- | ---: | ---: | --- |
| `7 / 3` | `2.333...` | `2` | Bỏ phần sau dấu phẩy |
| `8 / 4` | `2` | `2` | Chia hết |
| `1 / 2` | `0.5` | `0` | Giá trị dương nhỏ hơn 1 bị cắt thành 0 |
| `-1 / 2` | `-0.5` | `0` | Phần phân số bị cắt về 0 |
| `-7 / 3` | `-2.333...` | `-2` | Hướng về 0, không xuống `-3` |
| `7 / -3` | `-2.333...` | `-2` | Thương âm bị cắt về 0 |
| `-7 / -3` | `2.333...` | `2` | Hai dấu âm cho thương dương |
| `10 / 2L` | `5` | `5L` | Có toán hạng `long` nên kiểu kết quả là `long` |

**Dấu của thương:** Nếu thương khác 0, hai toán hạng cùng dấu cho thương dương; trái dấu cho thương âm. Nếu độ lớn thương toán học nhỏ hơn 1, kết quả là `0`, Java không có hai giá trị `+0` và `-0` phân biệt đối với `int` hay `long`.

**“Cắt về 0” khác “làm tròn đến số gần nhất”:** `8 / 3` là `2` dù gần `3` hơn về số học; `-8 / 3` là `-2` dù thương toán học gần `-3` hơn. Phép `/` trên hai số nguyên **không thực hiện rounding-to-nearest**.

## 2. Vì sao `-7 / 3` là `-2` thay vì `-3`?

Có ba cách lấy một số nguyên từ `-2.333...`:

- **Truncation toward zero:** bỏ phần phân số theo hướng 0 → `-2`.
- **Floor (làm tròn xuống):** lấy số nguyên lớn nhất không vượt quá giá trị → `-3`.
- **Ceiling (làm tròn lên):** lấy số nguyên nhỏ nhất không nhỏ hơn giá trị → `-2`.

Với số dương `2.333...`, truncation và floor đều cho `2`, nên khác biệt thường chỉ lộ rõ khi kết quả âm.

Java dùng truncation đối với phép `/` số nguyên. Nếu cần phép chia theo **floor**, dùng `Math.floorDiv(a, b)`; ví dụ:

```java
int a = -7, b = 3;
int truncated = a / b;            // -2
int floored = Math.floorDiv(a, b); // -3
```

`Math.floorDiv` có các overload cho `int` và `long`. Hãy chọn quy tắc dựa trên nghiệp vụ: chia một khoản nợ, tính thứ tự các bucket có chỉ số âm và quy đổi khoảng thời gian âm có thể cần floor; tính thương nguyên thông thường có thể cần truncation. Không thay thế `/` bằng `Math.floorDiv` theo thói quen khi chưa xác định quy tắc làm tròn mong muốn.

## 3. Phép lấy dư `%` đi cùng với phép chia `/`

Trong phép chia nguyên, `%` lấy **số dư**, không phải phép toán “phần trăm”. Với số bị chia `a` và số chia `b != 0`, thương `q = a / b` và số dư `r = a % b` tuân theo quan hệ:

\[
a = q \times b + r
\]

cho các phép chia số nguyên thông thường. Java chọn `q` bằng cách cắt về 0, từ đó số dư `r` có các tính chất:

- `|r| < |b|` về mặt toán học khi `b != 0`.
- Nếu `r != 0`, **dấu của `r` giống dấu `a`** (số bị chia), không phụ thuộc vào dấu `b`.
- Nếu chia hết thì `r == 0`.

| `a` | `b` | `a / b` | `a % b` | Kiểm tra |
| ---: | ---: | ---: | ---: | --- |
| 7 | 3 | 2 | 1 | `7 = 2 * 3 + 1` |
| -7 | 3 | -2 | -1 | `-7 = (-2) * 3 - 1` |
| 7 | -3 | -2 | 1 | `7 = (-2) * (-3) + 1` |
| -7 | -3 | 2 | -1 | `-7 = 2 * (-3) - 1` |

Không nhầm `%` với modulo toán học luôn trả số không âm. Chẳng hạn `-7 % 3 == -1` trong Java, trong khi `Math.floorMod(-7, 3) == 2`. Với `b` dương, `Math.floorMod(a, b)` phù hợp khi cần chỉ số vòng lặp hoặc bucket nằm trong `0..b-1` kể cả lúc `a` âm.

**Ngoại lệ cần hiểu:** `Integer.MIN_VALUE / -1` và `Long.MIN_VALUE / -1L` không có thương toán học biểu diễn được trong chính kiểu đích. Java cho kết quả tràn số, xem mục 7; không được suy diễn mọi quy tắc miền của thương toán học sang trường hợp này mà không xét giới hạn kiểu.

## 4. Muốn có kết quả thập phân thì phải thay kiểu trước phép chia

Viết `double result = 1 / 2;` vẫn cho **`0.0`**, vì Java tính biểu thức `1 / 2` theo `int` trước khi chuyển kết quả sang `double`. Cần dùng literal dấu phẩy động hoặc ép toán hạng:

```java
double wrong = 1 / 2;          // 0.0: đã chia nguyên trước
double correctA = 1.0 / 2;     // 0.5: 1.0 là double
double correctB = (double) 1 / 2; // 0.5: đổi trước phép chia
double correctC = 1 / 2.0;     // 0.5: 2.0 là double
double tooLate = (double) (1 / 2); // 0.0: cast sau phép chia
```

Khi một toán hạng là `double`, phép chia số thực có thể tạo phần thập phân; nhưng `double` có độ chính xác nhị phân hữu hạn nên **không biểu diễn chính xác mọi phân số thập phân**. Câu “muốn kết quả có phần thập phân” khác với “muốn kết quả số thập phân chính xác tuyệt đối”. Với tiền tệ hoặc quy tắc làm tròn thập phân, cân nhắc `BigDecimal` và chính sách `RoundingMode` cụ thể.

Cẩn trọng khi ép `long` cực lớn sang `double`: giá trị nguyên có thể bị làm tròn vì phần trị `double` chỉ giữ 53 bit độ chính xác hiệu dụng. Nếu bài toán đòi thương nguyên và số dư chính xác của số cực lớn, không dùng chuyển sang `double` để giải quyết.

## 5. Kiểu kết quả, biểu thức và thứ tự phép tính

### `byte`, `short` vẫn chia theo `int`

```java
byte numerator = 7, denominator = 3;
int q = numerator / denominator;  // Đúng, q = 2
// byte invalid = numerator / denominator; // Lỗi biên dịch: kết quả là int
```

Các toán hạng `byte`/`short` được nâng thành `int`. Còn `long / int` cho kết quả kiểu `long`, nhưng **vẫn cắt về 0**:

```java
long q = 10_000_000_000L / 3;  // 3_333_333_333L
```

`long` chỉ mở rộng **miền giá trị**, không tự biến phép chia nguyên thành phép chia có phần thập phân.

### Các toán tử `*`, `/`, `%` cùng mức ưu tiên

Chúng kết hợp từ trái sang phải. Vì phép chia nguyên đã mất phần phân số nên thay đổi vị trí ngoặc có thể thay đổi kết quả:

```java
int a = 20 / 3 * 3;   // (20 / 3) * 3 = 6 * 3 = 18
int b = 20 * 3 / 3;   // (20 * 3) / 3 = 60 / 3 = 20
```

Ví dụ `b` hợp lệ vì `20 * 3` không vượt `int`. Với toán hạng lớn, **nhân trước chia** có thể overflow, nên không thể tự ý đổi công thức để “giữ độ chính xác” nếu chưa kiểm tra miền trung gian.

Trong ví dụ trung bình:

```java
int sum = 7, count = 2;
int averageTruncated = sum / count;      // 3
double averageDecimal = (double) sum / count; // 3.5
```

Xác định đầu ra mong muốn là thương nguyên hay giá trị thập phân trước khi chọn biểu thức.

## 6. Ví dụ chạy độc lập trên Java 17+

Lưu chương trình sau vào `IntegerDivisionDemo.java`:

```java
public class IntegerDivisionDemo {
    public static void main(String[] args) {
        System.out.println("7 / 3 = " + (7 / 3) + ", 7 % 3 = " + (7 % 3));
        System.out.println("-7 / 3 = " + (-7 / 3) + ", -7 % 3 = " + (-7 % 3));
        System.out.println("7 / -3 = " + (7 / -3) + ", 7 % -3 = " + (7 % -3));
        System.out.println("-7 / -3 = " + (-7 / -3) + ", -7 % -3 = " + (-7 % -3));

        System.out.println("1 / 2 = " + (1 / 2));
        System.out.println("double division = " + ((double) 1 / 2));
        System.out.println("cast after division = " + ((double) (1 / 2)));

        byte a = 7, b = 3;
        int quotient = a / b;
        System.out.println("byte division result = " + quotient);

        System.out.println("floorDiv(-7, 3) = " + Math.floorDiv(-7, 3));
        System.out.println("floorMod(-7, 3) = " + Math.floorMod(-7, 3));
        System.out.println("MIN / -1 = " + (Integer.MIN_VALUE / -1));
        System.out.println("MIN % -1 = " + (Integer.MIN_VALUE % -1));

        int zero = 0;
        try {
            System.out.println(7 / zero);
        } catch (ArithmeticException e) {
            System.out.println("integer division by zero: ArithmeticException");
        }
        System.out.println("long division = " + (10_000_000_000L / 3));
    }
}
```

Dùng JDK 17 trở lên để biên dịch và chạy:

```bash
javac IntegerDivisionDemo.java
java IntegerDivisionDemo
```

Stdout:

```text
7 / 3 = 2, 7 % 3 = 1
-7 / 3 = -2, -7 % 3 = -1
7 / -3 = -2, 7 % -3 = 1
-7 / -3 = 2, -7 % -3 = -1
1 / 2 = 0
double division = 0.5
cast after division = 0.0
byte division result = 2
floorDiv(-7, 3) = -3
floorMod(-7, 3) = 2
MIN / -1 = -2147483648
MIN % -1 = 0
integer division by zero: ArithmeticException
long division = 3333333333
```

**Điểm quan sát chính:** `/` và `%` nhất quán với dấu của toán hạng; cast **trước** phép chia khác cast **sau** phép chia; `Math.floorDiv`/`Math.floorMod` cho kết quả khác khi chia số âm; trường hợp `MIN_VALUE / -1` bị overflow; mẫu số bằng 0 ném ngoại lệ.

## 7. Hai trường hợp biên cần kiểm soát

### Chia nguyên cho 0

`int` và `long` không có giá trị infinity/NaN như các kiểu dấu phẩy động. Do đó `a / 0` hoặc `a % 0` ở thời điểm thực hiện phép toán gây **`ArithmeticException`**. Ví dụ:

```java
int divisor = 0;
int value = 5;
try {
    int result = value / divisor;
} catch (ArithmeticException ex) {
    System.out.println("Cannot divide integers by zero");
}
```

Khi nhận mẫu số từ đầu vào, **kiểm tra `divisor != 0` trước phép chia** rồi thông báo dữ liệu không hợp lệ hoặc xử lý theo quy định ứng dụng. Một giá trị mẫu số sai không nên bị thay ngầm thành 1 nếu nghiệp vụ không cho phép.

### `MIN_VALUE / -1`: thương không chứa được trong kiểu

`int` có miền `-2_147_483_648` đến `2_147_483_647`. Trị tuyệt đối toán học của `Integer.MIN_VALUE` là `2_147_483_648`, **vượt miền int**. Khi chia cho -1:

```java
int value = Integer.MIN_VALUE;
int q = value / -1; // -2147483648, do overflow số nguyên
int r = value % -1; // 0
```

Tương tự `Long.MIN_VALUE / -1L` trả về `Long.MIN_VALUE`. Đây là trường hợp đặc biệt **không ném ngoại lệ tự động**. Quy tắc cắt về 0 vẫn là ý tưởng xác định thương, nhưng **miền kiểu nguyên có giới hạn** khiến kết quả được biểu diễn theo quy tắc overflow.

Nếu không muốn chấp nhận tràn:

```java
int dividend = Integer.MIN_VALUE;
int divisor = -1;
if (divisor == -1 && dividend == Integer.MIN_VALUE) {
    throw new ArithmeticException("integer quotient overflows int");
}
int quotient = dividend / divisor;
```

Đoạn trên minh họa một nhánh guard, **không phải chương trình độc lập**. Khi dùng trong phương thức thật, kiểm tra mẫu số bằng 0 và giữ cách xử lý ngoại lệ thống nhất.

Một cách khác khi phép chia `int` có thể cần kết quả lớn hơn miền `int`: **chuyển số bị chia lên `long` trước phép chia**, ví dụ `(long) Integer.MIN_VALUE / -1 == 2_147_483_648L`. Với trường hợp `long` lớn hơn miền, cần `BigInteger` khi thật sự đòi kết quả nguyên chính xác.

## 8. Dùng `/` trong ứng dụng mà không làm sai nghiệp vụ

**Ví dụ 1: chia nhóm cố định.** Có 11 người, mỗi nhóm chứa tối đa 4 người; `11 / 4 == 2` là **hai nhóm đầy đủ**, còn `11 % 4 == 3` người dư. Nếu hỏi tổng nhóm cần chuẩn bị thì cần **3 nhóm**.

Với `total >= 0` và `size > 0`, cách tính tránh overflow trung gian của biểu thức `(total + size - 1) / size` là:

```java
int total = 11;
int size = 4;
int groups = total / size + (total % size == 0 ? 0 : 1); // 3
```

Công thức này chỉ áp dụng theo giả thiết tổng và kích thước không âm/dương như đã nêu; nếu dữ liệu âm, phải xác định lại ý nghĩa làm tròn và có thể dùng `Math.floorDiv` hoặc quy tắc nghiệp vụ khác.

**Ví dụ 2: tỷ lệ và phần trăm.** Nếu `completed = 1` và `total = 4`, biểu thức `completed / total * 100` cho **0**, vì `1 / 4` được cắt về 0 trước khi nhân. Nếu chỉ cần phần trăm nguyên và dữ liệu nằm trong miền phù hợp, có thể tính `(long) completed * 100 / total` để tránh tràn `int` trung gian rồi chủ động xử lý việc cắt phần lẻ. Nếu cần tỷ lệ thập phân, dùng `100.0 * completed / total` hoặc loại số thập phân có quy tắc làm tròn đáp ứng nghiệp vụ.

**Ví dụ 3: phân loại theo chỉ số âm.** Khi muốn gom các thời điểm hoặc tọa độ âm theo ô có độ rộng dương, `a / width` có thể đưa `-1` vào ô 0 vì cắt về 0. `Math.floorDiv(-1, width)` đưa nó vào ô -1 khi `width > 1`, phù hợp với cách chia trục số thành các khoảng theo floor.

Điều chung của ba ví dụ: trước khi chọn biểu thức, phải xác định yêu cầu là **thương nguyên cắt về 0**, **làm tròn xuống**, **làm tròn lên**, **thương số thực**, hay **phân nhóm có số dư**.

## 9. Sai lầm thường gặp

1. **“Biến nhận là `double` thì `1 / 2` sẽ ra `0.5`.”** Sai: Java tính `1 / 2` thành `int` bằng 0 rồi mới gán thành `0.0`.
2. **“`(double) (a / b)` khôi phục phần phân số.”** Sai: phép chia nguyên đã mất phần lẻ; cần `(double) a / b`.
3. **“Phép chia nguyên luôn làm tròn xuống.”** Sai với thương âm không nguyên: `-7 / 3 == -2`, còn `Math.floorDiv(-7,3) == -3`.
4. **“Số dư luôn không âm.”** Sai: `-7 % 3 == -1`. Nếu cần số dư không âm với mẫu số dương, cân nhắc `Math.floorMod`.
5. **“Dùng `long` sẽ giữ phần thập phân.”** Sai: `10L / 4` vẫn bằng `2L`; `long` chỉ mở rộng miền.
6. **“`byte / byte` trả về `byte`.”** Sai: các toán hạng được nâng lên `int`, kiểu biểu thức là `int`.
7. **“Đảo thứ tự nhân và chia không thay đổi kết quả.”** Sai khi phép chia nguyên cắt phần lẻ; và nhân trước có thể overflow.
8. **“Chia số nguyên cho 0 trả infinity.”** Sai: nó ném `ArithmeticException`, khác phép chia dấu phẩy động.
9. **“Chia `Integer.MIN_VALUE` cho -1 luôn báo lỗi tràn.”** Sai: kết quả là `Integer.MIN_VALUE` do overflow có quy tắc.
10. **“`a / b` tự cho số nguyên gần thương thật nhất.”** Sai: Java chọn hướng về 0. Nếu cần làm tròn theo miền nghiệp vụ, phải thể hiện quy tắc đó rõ ràng.

## 10. Tự kiểm tra

1. **`9 / 4` và `-9 / 4` cho bao nhiêu?** → `2` và `-2`, vì cắt phần phân số về 0.
2. **`9 % 4` và `-9 % 4` cho bao nhiêu?** → `1` và `-1`, số dư khác 0 theo dấu số bị chia.
3. **`double ratio = 3 / 2;` lưu giá trị nào?** → `1.0`. Muốn `1.5`, viết `double ratio = 3.0 / 2;`.
4. **Tại sao `Math.floorDiv(-9,4) == -3`?** → Floor lấy số nguyên lớn nhất không vượt quá `-2.25`, tức -3.
5. **`byte a = 8, b = 3;` thì biểu thức `a / b` thuộc kiểu nào?** → `int`.
6. **Nếu có 13 vật phẩm, mỗi thùng chứa 5, cần bao nhiêu thùng?** → `13 / 5 = 2` thùng đầy và còn `13 % 5 = 3`, nên cần **3** thùng nếu tất cả phải được chứa.

**Ghi nhớ:** `/` giữa hai toán hạng số nguyên cho **thương nguyên cắt về 0**. Hãy phân biệt quy tắc này với *floor* và *rounding to nearest*; nếu cần giá trị có phần thập phân, chuyển ít nhất một toán hạng sang loại số thích hợp **trước phép chia**. Dùng `%` để xử lý số dư, và kiểm tra mẫu số, miền kết quả cùng trường hợp `MIN_VALUE / -1` khi cần tính toán an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-003">Toán tử % và dấu của số dư</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `%` và dấu của số dư trong Java

Trong Java, `%` là **toán tử lấy phần dư** (*remainder operator*). Với phép toán số nguyên, nó đi cùng phép chia `/` **làm tròn thương về 0** (*truncation toward zero*). Vì vậy, phần dư có thể **âm** khi số bị chia âm. Chẳng hạn:

```java
System.out.println(7 % 3);     //  1
System.out.println(-7 % 3);    // -1
System.out.println(7 % -3);    //  1
System.out.println(-7 % -3);   // -1
```

Các dòng này là mảnh mã minh họa đặt trong `main`. Điểm cần nhớ: với số nguyên Java, **phần dư khác 0 có cùng dấu với số bị chia (toán hạng bên trái)**, không nhất thiết cùng dấu với số chia bên phải. Nếu muốn kết quả **không âm** theo một chu kỳ có kích thước dương, thường nên dùng `Math.floorMod(x, modulus)`.

Bài học thuộc phần **Số học nguyên** của module **Java core – numbers**. Nội dung đi từ cơ chế `/` và `%` đến lỗi với số âm, cách dùng `Math.floorMod`, tình huống thực tế và kiểm thử biên.

## 1. Phần dư được xác định như thế nào?

Cho hai số nguyên `a` và `b` với `b != 0`. Trong phép toán số nguyên Java:

- `q = a / b`: thương nguyên được tính bằng phép chia **bỏ phần lẻ theo hướng 0**.
- `r = a % b`: phần dư đi kèm phép chia đó.
- Hệ thức toán học tương ứng là `a = q × b + r`. Trong code Java có độ rộng nguyên cố định, việc tự tính `q * b + r` còn phải xét tràn số ở biểu thức trung gian.

Ví dụ với hai số dương:

```text
7 / 3 = 2
7 % 3 = 1

7 = 2 × 3 + 1
```

Với số bị chia âm:

```text
-7 / 3 = -2      (cắt phần lẻ của -2,333... về 0)
-7 % 3 = -1

-7 = (-2) × 3 + (-1)
```

Nếu lấy thương bằng phép **làm tròn xuống** (*floor*), kết quả sẽ khác với phép chia nguyên Java khi thương toán học âm và không nguyên:

```text
floor(-7 / 3) = -3
-7 = (-3) × 3 + 2
```

Đó là lý do toán tử `%` và `Math.floorMod` có thể cho **hai kết quả khác nhau**.

### Quy tắc dấu chính xác

Với số nguyên và số chia khác 0:

1. Phần dư bằng 0 hoặc cùng dấu với **số bị chia**.
2. Giá trị tuyệt đối của phần dư **nhỏ hơn giá trị tuyệt đối của số chia** khi xét về mặt toán học.
3. Việc đổi dấu số chia **không làm đổi phần dư** nếu số bị chia giữ nguyên và cùng miền có thể biểu diễn.

Quy tắc thứ hai không yêu cầu gọi `Math.abs` trong code: `Math.abs(Integer.MIN_VALUE)` vẫn âm vì giá trị dương tương ứng không biểu diễn được trong `int`. Đây là lý do nên diễn đạt điều kiện về **độ lớn toán học**, không dùng công thức kiểm thử tùy tiện `Math.abs(r) < Math.abs(b)` cho mọi đầu vào.

## 2. Bảng bốn tổ hợp dấu bắt buộc phải hiểu

| Số bị chia `a` | Số chia `b` | `a / b` | `a % b` | Kiểm tra `a = q × b + r` |
| ---: | ---: | ---: | ---: | --- |
| `7` | `3` | `2` | `1` | `7 = 2 × 3 + 1` |
| `-7` | `3` | `-2` | `-1` | `-7 = -2 × 3 - 1` |
| `7` | `-3` | `-2` | `1` | `7 = (-2) × (-3) + 1` |
| `-7` | `-3` | `2` | `-1` | `-7 = 2 × (-3) - 1` |

Số chia âm **không kéo dấu phần dư về âm**. Hãy nhìn toán hạng bên trái: `7 % -3 == 1`; `-7 % -3 == -1`.

Với phép chia hết, phần dư bằng 0 bất kể dấu:

```java
12 % 3     // 0
-12 % 3    // 0
12 % -3    // 0
-12 % -3   // 0
```

Vì vậy, `n % d == 0` vẫn là phép kiểm tra chia hết hợp lệ cho số nguyên âm và số chia âm, miễn là `d != 0`.

## 3. Tại sao toán tử % không luôn trả kết quả dương?

Nhiều tài liệu toán học định nghĩa phép **modulo theo môđun dương** sao cho kết quả nằm trong `0, 1, ..., m-1` với `m > 0`. Quy ước ấy rất tiện cho toán học rời rạc và chỉ số vòng. Nhưng Java định nghĩa `%` cho số nguyên theo **phép chia cắt về 0**, do vậy:

```java
int remainder = -1 % 5;  // -1; KHÔNG phải 4
```

Trong toán học theo môđun 5, `-1` và `4` thuộc cùng lớp đồng dư. Tuy nhiên “đồng dư” là quan hệ toán học, còn `%` trong Java trả **một đại diện cụ thể** theo quy tắc phần dư. Muốn đại diện không âm khi môđun dương, hãy chọn `Math.floorMod`.

Hai khái niệm không nên trộn lẫn:

| Nhu cầu | Biểu thức Java | Kết quả với `-7` và `3` |
| --- | --- | --- |
| Phần dư của chia nguyên cắt về 0 | `-7 % 3` | `-1` |
| Phần dư theo phép chia làm tròn xuống | `Math.floorMod(-7, 3)` | `2` |
| Thương của chia nguyên cắt về 0 | `-7 / 3` | `-2` |
| Thương của chia nguyên làm tròn xuống | `Math.floorDiv(-7, 3)` | `-3` |

Không có phép nào “sai”; chúng trả lời **hai quy ước khác nhau**. Sai sót xảy ra khi thuật toán cần kết quả trong miền không âm nhưng người viết dùng `%` trên một số có thể âm.

## 4. Math.floorMod: lựa chọn cho chỉ số vòng và môđun dương

Java cung cấp `Math.floorMod(int x, int y)` và `Math.floorMod(long x, long y)`, cùng một số overload khác. Phương thức này kết hợp với `Math.floorDiv` theo phép chia **làm tròn xuống**, không cắt về 0:

```java
Math.floorDiv(-7, 3); // -3
Math.floorMod(-7, 3); //  2

Math.floorDiv(7, 3);  //  2
Math.floorMod(7, 3);  //  1
```

Với **môđun dương** `m > 0`, `Math.floorMod(x, m)` luôn trả một số nguyên trong phạm vi **từ 0 đến `m - 1`**, kể cả khi `x` âm.

| `x` | `m` | `x % m` | `Math.floorMod(x, m)` |
| ---: | ---: | ---: | ---: |
| `8` | `5` | `3` | `3` |
| `-1` | `5` | `-1` | `4` |
| `-8` | `5` | `-3` | `2` |
| `-10` | `5` | `0` | `0` |
| `-11` | `5` | `-1` | `4` |

**Lưu ý với số chia âm:** `Math.floorMod` **không cam kết trả số không âm** nếu `m` âm. Phần dư khác 0 theo floor division có cùng dấu với **số chia**. Ví dụ:

```java
7 % -3;                //  1
Math.floorMod(7, -3);  // -2
Math.floorDiv(7, -3);  // -3
```

Vì vậy, khi sử dụng `floorMod` để tạo chỉ số `0..n-1`, hãy **bảo đảm `n > 0`**. Với danh sách hoặc mảng rỗng, `n = 0` là lỗi thiết kế chỉ số, không phải trường hợp `floorMod` có thể xử lý.

### Tránh tự viết công thức chuẩn hóa thiếu an toàn

Một mẹo thường gặp là:

```java
int normalized = (x % m + m) % m;
```

Nếu `m > 0` và các phép toán diễn ra trong kiểu nguyên có độ rộng cố định, **biểu thức `x % m + m` có thể tràn** khi `x % m` đã dương. Ví dụ, với `x = 2_147_483_646` và `m = 2_147_483_647`, phép cộng trung gian `2_147_483_646 + 2_147_483_647` tràn `int`, làm kết quả có thể sai.

Dùng:

```java
int normalized = Math.floorMod(x, m);
```

Khi `m > 0`, cách này trực tiếp thể hiện quy tắc cần dùng và tránh phép cộng dễ tràn ở công thức tự chế.

## 5. Tình huống thực tế: chỉ số của bộ đệm vòng

Giả sử một bộ đệm vòng có `capacity = 5`, và thuật toán có bước lùi một vị trí từ index 0. Kết quả mong muốn là index 4.

Đoạn sau **sai** khi `currentIndex == 0`:

```java
int capacity = 5;
int currentIndex = 0;
int previousIndex = (currentIndex - 1) % capacity; // -1
// buffer[previousIndex] sẽ gây ArrayIndexOutOfBoundsException.
```

Sử dụng `Math.floorMod`:

```java
int capacity = 5;
int currentIndex = 0;
int previousIndex = Math.floorMod(currentIndex - 1, capacity); // 4
```

Quy tắc này hữu ích cho lịch tuần hoàn, vị trí quay vòng và số bước âm. Tuy nhiên có hai điều phải xét:

- **Kích thước phải dương:** `capacity > 0`. Không thể lấy phần dư với 0.
- **Biểu thức đầu vào cũng có thể tràn:** nếu `currentIndex` hoặc `offset` có thể rất lớn, phép `currentIndex + offset` có thể tràn **trước khi** gọi `floorMod`. Có thể chuyển phép cộng sang `long` trong phạm vi đảm bảo, hoặc thiết kế cách cộng môđun không làm tràn.

Ví dụ với `int` index, offset `int` và kích thước dương đã kiểm tra:

```java
static int wrappedIndex(int current, int offset, int capacity) {
    if (capacity <= 0) {
        throw new IllegalArgumentException("capacity must be positive");
    }
    long position = (long) current + offset;
    return (int) Math.floorMod(position, (long) capacity);
}
```

Đây là phương thức minh họa cần đặt trong một class. Vì tổng của hai `int` luôn nằm trong miền `long`, phép cộng ở đây không tràn. Phần dư nằm trong `0..capacity-1` nên việc cast kết quả về `int` an toàn. Nếu API định nghĩa `current` phải nằm trong `0..capacity-1`, vẫn nên xác thực riêng điều kiện đó.

## 6. Tình huống thực tế: tính chẵn lẻ và kiểm tra chia hết

Một phép kiểm tra phổ biến là:

```java
boolean isEven = value % 2 == 0;
boolean isOdd = value % 2 != 0;
```

Cả hai đúng đối với `int` âm và dương. **Đừng viết `value % 2 == 1` như cách kiểm tra số lẻ tổng quát:** `-3 % 2` bằng `-1`, nên `-3 % 2 == 1` là `false` dù `-3` là số lẻ.

Ví dụ:

| `value` | `value % 2` | `value % 2 != 0` |
| ---: | ---: | --- |
| `4` | `0` | `false` |
| `5` | `1` | `true` |
| `-4` | `0` | `false` |
| `-5` | `-1` | `true` |

Nếu hệ thống chỉ nhận số nguyên không âm, kiểm tra đầu vào trước là một lựa chọn hợp lý. Nhưng **phép kiểm tra số lẻ đúng cho mọi số nguyên** là `value % 2 != 0`, không cần loại trừ số âm chỉ để bảo vệ phép kiểm tra.

### Tách chữ số cuối của số nguyên âm

`-123 % 10` bằng `-3`, nên phép `value % 10` không tự cho một chữ số trong `0..9` nếu `value` có thể âm. Khi cần phần dư dương theo cơ số 10 để lập thuật toán số học môđun, `Math.floorMod(value, 10)` cho `7` đối với `-123`; **đó không phải chữ số 3 của trị tuyệt đối**.

Nếu mục tiêu là chữ số đơn vị của **độ lớn tuyệt đối**, phải nêu ý nghĩa đó và xử lý `Integer.MIN_VALUE` cẩn thận; đừng áp dụng `Math.abs(value)` vô điều kiện. Có thể nâng lên `long` trước rồi lấy độ lớn:

```java
int value = Integer.MIN_VALUE;
long magnitude = Math.abs((long) value);
long lastDigit = magnitude % 10; // 8
```

Cách tính đúng phải dựa trên câu hỏi “chữ số của trị tuyệt đối” hay “đại diện môđun không âm”.

## 7. Chia cho 0 và các giá trị biên

Với số nguyên, `a / 0` và `a % 0` đều gây `ArithmeticException` khi được đánh giá. Đừng dùng `%` như một phép kiểm tra an toàn nếu số chia lấy từ input chưa được xác thực.

```java
static boolean divisibleBy(int value, int divisor) {
    if (divisor == 0) {
        throw new IllegalArgumentException("divisor must not be zero");
    }
    return value % divisor == 0;
}
```

Phương thức trên xử lý số chia âm, số bị chia âm và cả trường hợp `Integer.MIN_VALUE % -1 == 0`.

### Trường hợp đặc biệt: Integer.MIN_VALUE / -1

Đây là ca biên quan trọng vì `Integer.MIN_VALUE = -2_147_483_648` và giá trị dương `2_147_483_648` không biểu diễn được bằng `int`.

```java
int x = Integer.MIN_VALUE;
int quotient = x / -1;  // Integer.MIN_VALUE, thương toán học bị tràn int
int remainder = x % -1; // 0
```

Quy tắc Java cho phép thương trong phép chia nguyên thường tràn ở trường hợp đặc biệt này, nhưng **phần dư vẫn bằng 0**. Đừng suy ra rằng mọi phép chia số nguyên sẽ ném ngoại lệ khi tràn. Nếu cần thương toán học chính xác, hãy mở rộng toán hạng trước khi chia:

```java
long quotient = (long) Integer.MIN_VALUE / -1; // 2147483648L
```

Khi tự kiểm tra hệ thức `a == (a / b) * b + a % b` trong code kiểu `int`, cần lưu ý tràn của phép nhân/thương. Hệ thức là công cụ lý giải về số học, nhưng biểu thức kiểm thử cũng phải được thiết kế phù hợp với phạm vi biểu diễn.

## 8. Kiểu của biểu thức % và quy tắc chuyển kiểu

Với các toán hạng nguyên thủy, toán tử `%` áp dụng **binary numeric promotion**:

- `byte`, `short` và `char` thường được nâng lên `int` trước khi tính.
- Nếu một toán hạng có kiểu `long`, biểu thức số nguyên thường được nâng lên `long`.
- Kết quả có kiểu sau promotion, không tự co về kiểu ban đầu của biến.

Ví dụ:

```java
byte small = -7;
int remainder = small % 3; // -1, kiểu biểu thức là int
// byte invalid = small % 3; // Không biên dịch: biểu thức int sang byte.
long large = -7L;
long longRemainder = large % 3; // -1L
```

Toán tử `%` cũng được Java định nghĩa với `float`/`double`, nhưng phần dư dấu phẩy động còn có các trường hợp `NaN`, vô cực và số 0 có dấu. **Bài học này tập trung vào phần dư số nguyên**; không lấy kết luận về lỗi chia cho 0 của integer áp lên `double` hoặc xem `%` floating point là cùng quy tắc xử lý ngoại lệ.

## 9. BigInteger: remainder và mod mang hai quy ước khác nhau

Khi số nguyên vượt phạm vi `long`, `java.math.BigInteger` cung cấp hai phương thức cần phân biệt:

- `remainder(divisor)`: phần dư theo cách chia cắt về 0, giống dấu của toán tử `%` với số nguyên âm.
- `mod(modulus)`: chọn đại diện môđun **không âm**, nhưng **yêu cầu modulus dương**; giá trị môđun 0 hoặc âm gây `ArithmeticException`.

```java
BigInteger negative = new BigInteger("-7");
BigInteger three = BigInteger.valueOf(3);

BigInteger remainder = negative.remainder(three); // -1
BigInteger modulo = negative.mod(three);          //  2
```

Đây là mảnh mã cần `import java.math.BigInteger;`. `BigInteger` bất biến và không dùng toán tử `%` trực tiếp giữa hai đối tượng `BigInteger`; hãy dùng phương thức. Với `BigInteger`, cũng kiểm tra rõ 0 ở tham số chia và giới hạn tài nguyên cho dữ liệu đầu vào lớn.

Sự phân biệt `BigInteger.remainder` so với `BigInteger.mod` cho thấy vì sao việc ghi rõ “phần dư” hay “modulo không âm” trong tài liệu API là quan trọng, đặc biệt khi chuyển thuật toán từ `int` sang kiểu số nguyên lớn.

## 10. Chương trình Java 17+ chạy độc lập

Lưu đoạn sau thành **`RemainderSignDemo.java`**. Chương trình in kết quả của bốn tổ hợp dấu, so sánh `%` với `Math.floorMod`, kiểm tra chẵn lẻ âm, minh họa chỉ số vòng, ca biên `Integer.MIN_VALUE` và `BigInteger`.

```java
import java.math.BigInteger;

public class RemainderSignDemo {
    public static void main(String[] args) {
        int[][] pairs = {
            {7, 3},
            {-7, 3},
            {7, -3},
            {-7, -3}
        };

        for (int[] pair : pairs) {
            int a = pair[0];
            int b = pair[1];
            System.out.printf(
                    "%d / %d = %d, %% = %d, floorDiv = %d, floorMod = %d%n",
                    a, b, a / b, a % b,
                    Math.floorDiv(a, b), Math.floorMod(a, b));
        }

        System.out.println("wrapped-index=" + Math.floorMod(-1, 5));
        System.out.println("odd-negative=" + (-5 % 2 != 0));
        System.out.println("negative-remainder=" + (-123 % 10));
        System.out.println("divisible-negative=" + (-12 % 3 == 0));
        System.out.println(
                "min-divisor-remainder=" + (Integer.MIN_VALUE % -1));

        BigInteger minusSeven = new BigInteger("-7");
        BigInteger three = BigInteger.valueOf(3);
        System.out.println(
                "big-remainder=" + minusSeven.remainder(three));
        System.out.println("big-mod=" + minusSeven.mod(three));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac RemainderSignDemo.java
java RemainderSignDemo
```

Stdout chính xác:

```text
7 / 3 = 2, % = 1, floorDiv = 2, floorMod = 1
-7 / 3 = -2, % = -1, floorDiv = -3, floorMod = 2
7 / -3 = -2, % = 1, floorDiv = -3, floorMod = -2
-7 / -3 = 2, % = -1, floorDiv = 2, floorMod = -1
wrapped-index=4
odd-negative=true
negative-remainder=-3
divisible-negative=true
min-divisor-remainder=0
big-remainder=-1
big-mod=2
```

Ở dòng thứ hai, `-7 / 3` cắt thương về 0 nên `-2` và phần dư `-1`; `floorDiv(-7,3)` là `-3` nên `floorMod` là `2`. Ở dòng thứ ba, số chia âm khiến `floorMod(7,-3)` là `-2`, chứng minh không thể giả định nó luôn không âm. Hai dòng cuối cho thấy `BigInteger.remainder` và `BigInteger.mod` đi theo hai quy ước khác nhau.

## 11. Sai lầm thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Nghĩ `a % b` luôn không âm | Dấu của phần dư số nguyên khác 0 theo dấu `a` | Dùng `Math.floorMod(a, b)` khi yêu cầu `b > 0` và miền `0..b-1` |
| Kiểm tra lẻ bằng `n % 2 == 1` | Số lẻ âm cho phần dư `-1` | Dùng `n % 2 != 0` |
| Dùng `(index - 1) % length` rồi truy cập mảng | Với index 0 tạo chỉ số `-1` | Kiểm tra `length > 0` rồi dùng `Math.floorMod` |
| Cộng `(x % m + m) % m` mặc định an toàn | Có thể tràn tại phép cộng | Dùng `Math.floorMod(x, m)` |
| Tưởng đổi dấu số chia sẽ đổi dấu phần dư | Với `%`, dấu phụ thuộc số bị chia | Lập bảng bốn tổ hợp dấu để kiểm tra |
| Gọi `Math.floorMod(x, negativeM)` rồi giả định không âm | Khi số chia âm, kết quả khác 0 cũng âm | Xác thực modulus dương trước khi dùng làm chỉ số |
| Bỏ qua số chia 0 | `%` nguyên với số chia 0 ném `ArithmeticException` | Validate divisor hoặc size trước phép toán |
| Dùng `Math.abs(x)` không kiểm tra biên | `Math.abs(Integer.MIN_VALUE)` vẫn âm | Nâng lên `long` trước khi lấy trị tuyệt đối khi cần |
| Mong `BigInteger.remainder` luôn dương | Phương thức trả phần dư theo phép chia cắt về 0 | Dùng `BigInteger.mod` với modulus dương |
| Thử nghiệm chỉ với số dương | Không phát hiện lỗi dấu của phần dư | Thêm số bị chia âm, số chia âm, số 0 và giá trị biên |

### Lưu ý về biến đổi công thức

Đối với `m > 0`, có thể chứng minh về mặt toán học rằng `x` và `Math.floorMod(x,m)` đồng dư môđun `m`. Tuy nhiên, khi tối ưu các biểu thức chứa `+`, `-` hoặc `*` trên `int`/`long`, vẫn phải xét tràn số **trước** bước lấy modulo. Không phải mọi đẳng thức số học nguyên vô hạn đều giữ nguyên ý nghĩa khi đánh giá với kiểu nguyên có độ rộng cố định.

Ví dụ, một thuật toán tính `(a + b) % m` trong `int` có thể sai nếu `a + b` đã tràn. `Math.floorMod(a + b, m)` cũng **không sửa được** giá trị trung gian bị tràn; cần nâng kiểu hoặc dùng thuật toán cộng môđun an toàn theo phạm vi đầu vào.

## 12. Câu hỏi và bài tập tự luyện

1. Không chạy Java, tính `11 % 4`, `-11 % 4`, `11 % -4`, `-11 % -4`. Với mỗi trường hợp hãy tính `/` và kiểm tra hệ thức chia lấy thương và dư.
2. Vì sao `-17 % 5` bằng `-2` nhưng `Math.floorMod(-17, 5)` bằng `3`? Hãy giải thích bằng sự khác nhau giữa “cắt về 0” và “làm tròn xuống”.
3. Một mảng độ dài 7 dùng chỉ số từ 0 đến 6. Viết hàm lấy phần tử trước và sau khi đang ở index 0 hoặc 6. Kiểm tra hành vi với offset âm và dương, đồng thời xử lý mảng rỗng.
4. Viết hàm `isOdd(int value)` đúng cho cả `Integer.MIN_VALUE`, số 0 và số âm. Tại sao `value % 2 == 1` chưa đạt?
5. So sánh kết quả `Math.floorMod(7, -3)` và `7 % -3`. Vì sao một thuật toán cần phần dư không âm nên quy định số chia dương?
6. Thiết kế kiểm thử cho parser nhận `divisor` từ JSON hoặc CLI, bảo đảm không bao giờ thực hiện `%` với 0. Bạn sẽ xử lý lỗi đầu vào dưới dạng ngoại lệ hay thông báo xác thực?
7. Với `BigInteger(-7)`, so sánh `remainder(3)`, `mod(3)` và thao tác `mod(-3)`. Phương thức nào yêu cầu modulus dương và vì sao?
8. Kiểm thử công thức `(x % m + m) % m` khi `x = 2_147_483_646`, `m = 2_147_483_647`. Tìm chỗ tràn và thay bằng một phép gọi `Math.floorMod`.

## 13. Ghi nhớ

- **`%` số nguyên Java là phần dư của chia cắt về 0**, vì thế nếu khác 0 nó có cùng dấu với **số bị chia**.
- **`-7 % 3 == -1`**, còn **`Math.floorMod(-7, 3) == 2`**. Chọn theo quy ước mà thuật toán cần.
- Dùng **`Math.floorMod(x, m)` với `m > 0`** khi muốn chỉ số vòng hoặc đại diện môđun trong `0..m-1`; kiểm tra `m` không phải 0.
- Để kiểm tra số lẻ cho mọi số nguyên, dùng **`n % 2 != 0`**; để kiểm tra chia hết, dùng **`n % d == 0`** sau khi bảo đảm `d != 0`.
- Phép modulo **không tự ngăn tràn** ở biểu thức đầu vào, và `Math.floorMod` với số chia âm không bảo đảm trả giá trị không âm.
- Với số nguyên tùy ý, `BigInteger.remainder` và `BigInteger.mod` thể hiện hai quy tắc khác nhau.

Hiểu đúng **dấu của phần dư** giúp tránh lỗi âm thầm trong tính chỉ số, xử lý chu kỳ và các thuật toán số nguyên. Hãy chọn phép toán theo yêu cầu của bài toán trước khi viết biểu thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-004">Chia hoặc lấy dư cho 0 với số nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Chia hoặc lấy dư cho 0 với số nguyên trong Java

## 1. Quy tắc cốt lõi

Với các kiểu số nguyên trong Java, **mẫu số (toán hạng bên phải) bằng `0` trong phép chia `/` hoặc phép lấy dư `%` luôn gây `ArithmeticException` khi phép toán được thực thi**. Java không đưa ra một thương hoặc số dư hợp lệ cho những biểu thức này.

```java
int a = 12;
int zero = 0;

// Hai phép tính sau đều ném ArithmeticException khi chạy:
// int quotient = a / zero;
// int remainder = a % zero;
```

Những điểm cần nhớ ngay:

- `12 / 0` **không** trả về số vô cực với số nguyên; phép tính thất bại.
- `12 % 0` **không** trả về `0` hay `12`; phép tính cũng thất bại.
- `0 / 0` và `0 % 0` **đều** gây lỗi. Tử số bằng `0` không cứu được phép toán.
- Kiểm tra **toán hạng bên phải** (`divisor == 0`), không chỉ kiểm tra tử số.
- `ArithmeticException` là **unchecked exception** (lớp con của `RuntimeException`). Không bắt buộc khai báo `throws` hoặc `catch`, nhưng chương trình phải xác định cách xử lý đầu vào có thể bằng `0`.

Đây là quy tắc đối với **integer arithmetic** của Java, bao gồm phép chia/lấy dư thực hiện theo kiểu `int` hoặc `long` sau khi áp dụng quy tắc nâng kiểu số (*numeric promotion*). Không nên suy ra kết quả cho số nguyên từ hành vi của `double`/`float`.

## 2. Tại sao phép chia và phép lấy dư cho 0 không xác định?

Với số nguyên `a` và `b != 0`, Java định nghĩa thương nguyên `q = a / b` bằng phép chia làm tròn về **0**. Số dư `r = a % b` đi cùng thương và thỏa mãn hệ thức (theo số học nguyên Java):

```text
a = b * q + r
q = a / b
r = a % b
```

Ví dụ:

```java
System.out.println(17 / 5);    // 3
System.out.println(17 % 5);    // 2
System.out.println(-17 / 5);   // -3
System.out.println(-17 % 5);   // -2
```

Ở đây `17 = 5 * 3 + 2`, còn `-17 = 5 * (-3) + (-2)`. Với `%` trên số nguyên, số dư khác `0` có cùng dấu với **tử số** (số bị chia), không nhất thiết dương.

Nếu `b = 0`, không thể xác định thương nguyên duy nhất theo ý nghĩa phép chia. Công thức `a = b * q + r` không còn cung cấp một phép chia có nghĩa thông thường; Java **chủ động từ chối cả `/` và `%` khi mẫu số bằng 0**. Vì vậy, việc tính `a % 0` không phải một cách “an toàn hơn” để xử lý `a / 0`.

Không cần ghi nhớ một thông báo lỗi văn bản cố định: ý nghĩa đáng tin cậy là **loại ngoại lệ `java.lang.ArithmeticException`**; chi tiết nội dung message do triển khai đưa ra.

## 3. Khi nào lỗi xảy ra và ảnh hưởng đến luồng chương trình ra sao?

Ngoại lệ xảy ra **khi JVM thực thi phép chia/lấy dư nguyên với mẫu số thực tế bằng 0**. Ví dụ, tham số hoặc phép tính trung gian có thể trở thành 0:

```java
static int averagePerGroup(int total, int groups) {
    return total / groups; // groups == 0 -> ArithmeticException.
}
```

Nếu ngoại lệ không được bắt ở nơi phù hợp, luồng thực thi bình thường bị ngắt và ngoại lệ được truyền lên trên chuỗi gọi. Khi không có nơi xử lý, luồng thực thi kết thúc với lỗi; ứng dụng không nhận được một giá trị “thương mặc định”.

Phép tính **không được thực thi** thì không thể phát sinh ngoại lệ do chính phép tính đó. Ví dụ:

```java
int divisor = 0;
if (divisor != 0) {
    int q = 10 / divisor; // Không được chạy trong trường hợp hiện tại.
}
```

Một điều kiện ngắn mạch giúp bảo vệ phép toán nếu đặt đúng thứ tự:

```java
int divisor = 0;
boolean valid = divisor != 0 && 100 / divisor > 5;
// valid == false; vế 100 / divisor bị bỏ qua.
```

Ở đây `&&` chỉ đánh giá vế phải khi vế trái `true`. Nếu thay bằng `&` với hai toán hạng boolean, **cả hai vế** được đánh giá và phép chia có thể ném ngoại lệ. Cũng cần tránh đảo thứ tự thành `100 / divisor > 5 && divisor != 0`: phép chia đã chạy trước khi kiểm tra mẫu số.

Các biểu thức điều kiện chỉ an toàn nếu **mẫu số được kiểm tra là đúng mẫu số dùng trong phép tính** và không thể thay đổi bất ngờ giữa lúc kiểm tra và sử dụng. Với giá trị số cục bộ, hãy chụp giá trị cần dùng vào cùng một biến nếu phép tính ban đầu đọc từ một nguồn có thể thay đổi.

## 4. Những kiểu số nguyên nào chịu quy tắc này?

Các phép toán số nguyên với `byte`, `short`, `char` và `int` thường được *numeric promotion* lên `int`; nếu có toán hạng `long` (không có số thực tham gia), phép tính diễn ra ở `long`. Cả phép chia/lấy dư kiểu `int` và `long` đều ném `ArithmeticException` nếu mẫu số bằng `0`.

```java
byte a = 12;
byte b = 0;
long big = 12L;
long longZero = 0L;

// int x = a / b;       // ArithmeticException khi thực thi (chia bằng int).
// int y = a % b;       // ArithmeticException khi thực thi (lấy dư bằng int).
// long z = big / longZero; // ArithmeticException (chia bằng long).
// long r = big % longZero; // ArithmeticException (lấy dư bằng long).
```

Toán tử ghép gán cũng không được miễn trừ:

```java
int count = 10;
int zero = 0;

// count /= zero; // ArithmeticException; count không nhận được thương hợp lệ.
// count %= zero; // ArithmeticException; không nhận được số dư hợp lệ.
```

Ngoài các toán tử, những API thực hiện phép chia/lấy dư nguyên như `Math.floorDiv(a, b)`, `Math.floorMod(a, b)`, `Integer.divideUnsigned(a, b)` và `Integer.remainderUnsigned(a, b)` cũng không chấp nhận mẫu số bằng 0. Với `BigInteger`, `divide(BigInteger.ZERO)` và `remainder(BigInteger.ZERO)` cũng ném `ArithmeticException`. **Độ rộng lớn hơn hoặc khả năng biểu diễn số nguyên rất lớn không làm cho phép chia cho 0 có nghĩa.**

Lưu ý nhỏ nhưng hữu ích: các hàm `Math.floorDiv`/`Math.floorMod` có quy tắc làm tròn và dấu khác chia nguyên thông thường đối với một số toán hạng âm, nhưng điều kiện mẫu số khác 0 **vẫn bắt buộc**.

## 5. Phân biệt phép chia cho 0 kiểu số nguyên và kiểu dấu phẩy động

Cùng ký hiệu `/` hoặc `%`, hành vi có thể khác khi biểu thức được tính bằng `double` hoặc `float`. Đối với phép toán dấu phẩy động Java theo chuẩn IEEE 754:

| Biểu thức | Kiểu phép toán | Kết quả |
| --- | --- | --- |
| `10 / 0` | Số nguyên (`int`) | `ArithmeticException` khi chạy |
| `10L / 0L` | Số nguyên (`long`) | `ArithmeticException` khi chạy |
| `10 % 0` | Lấy dư nguyên | `ArithmeticException` khi chạy |
| `0 / 0` | Số nguyên | `ArithmeticException` khi chạy |
| `10.0 / 0` | Dấu phẩy động (`double`) | `Infinity` |
| `-10.0 / 0.0` | Dấu phẩy động | `-Infinity` |
| `0.0 / 0.0` | Dấu phẩy động | `NaN` |
| `10.0 % 0` | Lấy dư dấu phẩy động | `NaN` |

Trong `10.0 / 0`, toán hạng `10.0` có kiểu `double`, nên `0` được nâng lên `double` và phép chia thuộc ngữ nghĩa số thực. Ngược lại, `10 / 0` chỉ có toán hạng nguyên nên không tự chuyển sang phép chia số thực.

```java
System.out.println(Double.isInfinite(10.0 / 0)); // true
System.out.println(Double.isNaN(0.0 / 0.0));     // true
System.out.println(Double.isNaN(10.0 % 0));      // true
```

**Không sử dụng phép chuyển sang `double` chỉ để tránh ngoại lệ** nếu bài toán yêu cầu số nguyên chính xác. `Infinity` hoặc `NaN` có thể tiếp tục lan truyền vào tính toán sau và làm kết quả sai về ý nghĩa nghiệp vụ. Nếu quy tắc là “số nhóm phải lớn hơn 0”, hãy kiểm tra quy tắc đó thay vì chuyển kiểu.

## 6. Cách xử lý đúng trong ứng dụng

### 6.1. Kiểm tra mẫu số trước khi tính

Nếu `0` là dữ liệu đầu vào **không hợp lệ**, hãy từ chối sớm bằng thông báo rõ ràng:

```java
static int dividePositiveGroups(int total, int groups) {
    if (groups <= 0) {
        throw new IllegalArgumentException("groups must be positive");
    }
    return total / groups;
}
```

Ở đây kiểm tra `<= 0` vì **quy tắc nghiệp vụ** yêu cầu số nhóm dương; riêng để phòng lỗi chia cho 0 về mặt ngôn ngữ chỉ cần `groups == 0`. Số âm là mẫu số hợp lệ trong phép chia nguyên Java, nhưng có thể không hợp lệ trong bài toán đếm nhóm.

Nếu cần tính cả thương và số dư, chỉ tính sau khi xác nhận mẫu số:

```java
static String describeDivision(int total, int groups) {
    if (groups == 0) {
        return "Không thể chia khi số nhóm bằng 0";
    }
    int quotient = total / groups;
    int remainder = total % groups;
    return "Thương=" + quotient + ", dư=" + remainder;
}
```

Giá trị trả về ở trường hợp `groups == 0` là **quyết định thiết kế của ứng dụng**, không phải kết quả số học mặc định của Java. Các hệ thống khác có thể chọn báo lỗi, yêu cầu nhập lại, hoặc dùng kiểu trả về thể hiện phép tính không có kết quả.

### 6.2. Bắt ngoại lệ tại ranh giới xử lý phù hợp

Khi mẫu số có thể đến từ dữ liệu bên ngoài hoặc một thành phần khác, có thể bắt `ArithmeticException` tại điểm ứng dụng biết cách thông báo và phục hồi:

```java
int total = 10;
int groups = 0;
try {
    int perGroup = total / groups;
    System.out.println(perGroup);
} catch (ArithmeticException ex) {
    System.out.println("Không thể chia cho 0");
}
```

Nếu có thể kiểm tra đầu vào sớm, giải pháp kiểm tra trước thường dễ đọc hơn. **Không nên bắt `ArithmeticException` quá rộng rồi bỏ qua** vì còn có những trường hợp số học khác, khiến lỗi lập trình bị che khuất. Khi không thể khôi phục hợp lệ, để ngoại lệ lan lên tầng xử lý lỗi là lựa chọn phù hợp hơn việc tự gán một giá trị giả như `0`.

### 6.3. Tránh mất an toàn do mẫu số được tính động

Mẫu số có thể phát sinh từ phép tính trung gian:

```java
int users = 8;
int activeUsers = 8;
int inactiveUsers = users - activeUsers; // 0

if (inactiveUsers != 0) {
    int average = 100 / inactiveUsers;
    System.out.println(average);
}
```

Khi nguồn dữ liệu có thể thay đổi đồng thời, lấy mẫu số một lần, kiểm tra, rồi dùng **chính giá trị đó**:

```java
int currentDivisor = getCurrentDivisor();
if (currentDivisor != 0) {
    int result = 100 / currentDivisor;
    System.out.println(result);
}
```

`getCurrentDivisor()` trong đoạn ngắn thứ hai là **hàm minh họa do ứng dụng định nghĩa**, không phải API chuẩn của Java. Nếu gọi nguồn dữ liệu thêm lần nữa trong phép chia, giá trị có thể đã đổi thành 0. Với chương trình song song, còn cần thiết kế đồng bộ khi những dữ liệu liên quan phải nhất quán theo một thao tác.

## 7. Ví dụ Java 17+ chạy độc lập và kết quả chính xác

Tạo tệp **`IntegerDivisionByZeroDemo.java`**:

```java
public class IntegerDivisionByZeroDemo {
    public static void main(String[] args) {
        int numerator = 10;
        int validDivisor = 3;
        int zero = 0;

        System.out.println("10 / 3 = " + (numerator / validDivisor));
        System.out.println("10 % 3 = " + (numerator % validDivisor));

        try {
            int quotient = numerator / zero;
            System.out.println(quotient);
        } catch (ArithmeticException ex) {
            System.out.println("10 / 0 -> ArithmeticException");
        }

        try {
            int remainder = numerator % zero;
            System.out.println(remainder);
        } catch (ArithmeticException ex) {
            System.out.println("10 % 0 -> ArithmeticException");
        }

        try {
            int zeroOverZero = zero / zero;
            System.out.println(zeroOverZero);
        } catch (ArithmeticException ex) {
            System.out.println("0 / 0 -> ArithmeticException");
        }

        boolean canDivide = zero != 0;
        System.out.println("valid divisor? " + canDivide);
        System.out.println("floating / zero infinite = "
                + Double.isInfinite(10.0 / zero));
        System.out.println("floating % zero NaN = "
                + Double.isNaN(10.0 % zero));
    }
}
```

Biên dịch và chạy:

```bash
javac IntegerDivisionByZeroDemo.java
java IntegerDivisionByZeroDemo
```

Kết quả:

```text
10 / 3 = 3
10 % 3 = 1
10 / 0 -> ArithmeticException
10 % 0 -> ArithmeticException
0 / 0 -> ArithmeticException
valid divisor? false
floating / zero infinite = true
floating % zero NaN = true
```

Vì đoạn chương trình **bắt riêng từng ngoại lệ**, các ví dụ sau vẫn tiếp tục thực thi. Đây chỉ là ví dụ giải thích sự khác nhau giữa phép toán nguyên và phép toán dấu phẩy động. Trong code nghiệp vụ, kiểm tra miền dữ liệu và báo lỗi theo yêu cầu thực tế là lựa chọn thường rõ ràng hơn.

## 8. Những hiểu lầm phổ biến

1. **“Chỉ chia cho 0 mới lỗi; lấy dư cho 0 thì trả lại tử số.”** Sai: với số nguyên, cả `/ 0` lẫn `% 0` đều ném `ArithmeticException`.
2. **“Nếu tử số bằng 0, `0 / 0` sẽ bằng 0.”** Sai: mẫu số vẫn bằng 0, nên phép chia không hợp lệ.
3. **“Java tự trả về `Infinity` khi có lỗi chia cho 0.”** Chỉ có hành vi `Infinity`/`NaN` liên quan tới phép toán dấu phẩy động; phép chia nguyên ném ngoại lệ.
4. **“Đổi biến nhận kết quả thành `double` là đủ.”** Sai: `double x = 10 / zero;` vẫn đánh giá `10 / zero` theo kiểu `int` trước khi gán, nên ngoại lệ vẫn xảy ra. Phải có toán hạng dấu phẩy động ngay **trong biểu thức** mới dùng ngữ nghĩa số thực.
5. **“Đã kiểm tra `divisor != 0` ở vế sau của `&&` thì an toàn.”** Sai nếu phép chia đứng trước; thứ tự đánh giá quyết định phép chia có chạy hay không.
6. **“`Math.floorMod(a, 0)` hoặc `BigInteger.divide(BigInteger.ZERO)` tự xử lý được.”** Sai: các API chia/lấy dư nguyên cũng từ chối mẫu số bằng 0.
7. **“Catch mọi `ArithmeticException` rồi trả về `0` là xử lý xong.”** Không có kết quả thương/số dư mặc định bằng 0. Trả về một giá trị thay thế phải là quy tắc được xác định rõ của hệ thống.

**Ghi nhớ:** Với phép tính nguyên trong Java, hãy xác nhận **mẫu số khác 0 trước khi thực hiện cả `/` và `%`**. Nếu mẫu số 0 là dữ liệu không hợp lệ, xử lý nó như một lỗi đầu vào hoặc tình huống không có kết quả. Số thực có `Infinity`/`NaN` theo quy tắc khác, nhưng không thay đổi tính không hợp lệ của phép chia nguyên cho 0.

**Tài liệu đặc tả:** [JLS §15.17.2 — Division Operator /](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17.2), [JLS §15.17.3 — Remainder Operator %](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17.3), [Java SE 21 — ArithmeticException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ArithmeticException.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-005">Phép toán đơn ngôi, tiền tố và hậu tố ++, --</a>

<details>
<summary>Xem chi tiết</summary>

# Phép toán đơn ngôi và tiền tố, hậu tố `++`, `--` trong Java

**Toán tử đơn ngôi** (*unary operator*) tác động lên một toán hạng, chẳng hạn `-x` hoặc `~mask`. Hai toán tử **tăng một đơn vị `++`** và **giảm một đơn vị `--`** có hai cách viết: đặt **trước** biến (`++i`, `--i`) hoặc **sau** biến (`i++`, `i--`). Điểm quan trọng nhất là cả hai dạng đều thay đổi biến, nhưng **giá trị mà biểu thức trả về khác nhau**.

Trong phạm vi số học nguyên, cần hiểu đồng thời kiểu của kết quả, phạm vi giá trị, tràn số, thứ tự đánh giá và điều kiện để toán hạng có thể thay đổi. Bài này dùng Java 17+ và phân biệt các ví dụ chạy độc lập với những dòng chỉ dùng để minh họa lỗi biên dịch.

## 1. Các toán tử đơn ngôi liên quan

| Cú pháp | Tên | Ý nghĩa | Kiểu toán hạng |
| --- | --- | --- | --- |
| `+x` | Dấu cộng đơn ngôi | Lấy giá trị số sau *unary numeric promotion*; không đổi dấu | Kiểu số nguyên hoặc số thực |
| `-x` | Dấu trừ đơn ngôi | Đổi dấu giá trị số sau *unary numeric promotion* | Kiểu số nguyên hoặc số thực |
| `~x` | Bù bit (*bitwise complement*) | Đảo từng bit của giá trị đã nâng kiểu | Chỉ kiểu số nguyên |
| `!flag` | Phủ định logic | `true` thành `false` và ngược lại | `boolean` hoặc `Boolean` (unboxing) |
| `++x` / `x++` | Tăng tiền tố / hậu tố | Thay đổi biến bằng cách tăng 1; khác ở giá trị trả về | Biến có kiểu số nguyên/số thực hoặc wrapper số tương ứng |
| `--x` / `x--` | Giảm tiền tố / hậu tố | Thay đổi biến bằng cách giảm 1; khác ở giá trị trả về | Biến có kiểu số nguyên/số thực hoặc wrapper số tương ứng |

`+` và `-` cũng tồn tại ở **dạng nhị nguyên**: `a + b` cộng hai số, `a - b` trừ hai số. Java xác định dạng đang dùng dựa trên cấu trúc biểu thức:

```java
int a = 5;
int positive = +a; // 5: cộng đơn ngôi
int negative = -a; // -5: trừ đơn ngôi
int sum = a + 2;   // 7: cộng nhị nguyên
int difference = a - 2; // 3: trừ nhị nguyên
```

`+` đơn ngôi **không phải toán tử nối chuỗi**: `+"abc"` không hợp lệ. Toán tử logic `!` chỉ áp dụng cho boolean; không có quy tắc kiểu C/C++ coi `0` tương đương `false`:

```java
boolean enabled = true;
boolean disabled = !enabled; // false
// boolean invalid = !0;      // lỗi: int không phải boolean
// int invalidBits = ~true;    // lỗi: ~ không nhận boolean
```

## 2. Nâng kiểu số học đơn ngôi (*unary numeric promotion*)

Với `+x`, `-x` và `~x`, Java thực hiện *unary numeric promotion* trước khi tạo kết quả. Đây là khác biệt dễ bỏ sót khi toán hạng là `byte`, `short` hoặc `char`.

| Kiểu toán hạng | Kiểu kết quả của `+x`, `-x` | Kiểu kết quả của `~x` |
| --- | --- | --- |
| `byte`, `short`, `char` | `int` | `int` |
| `int` | `int` | `int` |
| `long` | `long` | `long` |
| `float` | `float` | Không hợp lệ |
| `double` | `double` | Không hợp lệ |

Wrapper số như `Byte` có thể được *unbox* rồi nâng kiểu; nếu giá trị wrapper là `null` thì việc unboxing có thể gây `NullPointerException`.

Ví dụ:

```java
byte b = 5;
int x = +b;  // +b có kiểu int
int y = -b;  // -b có kiểu int
int z = ~b;  // ~b có kiểu int, giá trị -6

// byte wrong = -b; // lỗi: kết quả int không tự gán về byte
byte converted = (byte) -b; // -5, ép kiểu có chủ đích
```

Điểm cần phân biệt: **`b++` vẫn cập nhật biến `b` theo kiểu `byte`**, dù `+b` và `-b` tạo kết quả kiểu `int`. Quy tắc đặc biệt của `++`/`--` sẽ được giải thích bên dưới.

### Bù bit `~` và biểu diễn bù hai

Với `int`, `~` đảo toàn bộ 32 bit; với `long`, đảo toàn bộ 64 bit. Ta có đồng nhất thức toán học theo biểu diễn bù hai:

```text
~x == -x - 1
```

Ví dụ:

```java
int mask = 0b1010; // 10
int oppositeBits = ~mask; // -11
```

`~10` không ra `0b0101` (5), vì `int` có **32 bit**, gồm cả các bit 0 bên trái bốn bit thấp. Đảo tất cả 32 bit tạo ra giá trị âm `-11`. Muốn **chỉ đảo bốn bit thấp**, dùng mặt nạ:

```java
int lowFourFlipped = (~0b1010) & 0b1111; // 0b0101 = 5
```

Đừng nhầm `~` với `!`: `~` làm việc trên bit số nguyên, còn `!` làm việc trên giá trị logic.

## 3. `++` và `--`: tác dụng phụ và giá trị của biểu thức

Mỗi toán tử tăng/giảm vừa **đọc giá trị cũ**, vừa **ghi giá trị mới** vào biến. Về giá trị dùng trong một biểu thức lớn hơn:

- **Tiền tố `++i`:** tăng biến `i` trước; giá trị biểu thức là **giá trị mới**.
- **Hậu tố `i++`:** tăng biến `i`; giá trị biểu thức là **giá trị cũ**.
- **Tiền tố `--i`:** giảm biến trước; giá trị biểu thức là **giá trị mới**.
- **Hậu tố `i--`:** giảm biến; giá trị biểu thức là **giá trị cũ**.

Giả sử ban đầu `i = 5`. Mỗi hàng trong bảng sau là **một lần chạy độc lập** bắt đầu từ 5:

| Biểu thức | Giá trị biểu thức | Giá trị `i` sau biểu thức |
| --- | ---: | ---: |
| `++i` | 6 | 6 |
| `i++` | 5 | 6 |
| `--i` | 4 | 4 |
| `i--` | 5 | 4 |

Ví dụ hậu tố:

```java
int i = 5;
int old = i++; // old = 5; i = 6
```

Ví dụ tiền tố:

```java
int i = 5;
int current = ++i; // current = 6; i = 6
```

Khi **dùng như một câu lệnh độc lập**, `i++` và `++i` đều làm `i` tăng một. Khác biệt quan trọng khi kết quả biểu thức được dùng, ví dụ `int a = i++;` hoặc `System.out.println(++i);`.

Tương tự `i--` và `--i` đều giảm một khi đứng một mình, nhưng cách trả về giá trị cũ/mới vẫn khác nhau.

### Đọc output từ đầu đến cuối

Tệp Java hoàn chỉnh sẽ có ở phần 9. Với hai câu lệnh này:

```java
int n = 3;
System.out.println(n++); // in 3; sau đó n thành 4
System.out.println(++n); // n thành 5; in 5
```

Output theo đúng thứ tự là `3` rồi `5`. Không thể hiểu hậu tố theo nghĩa “tăng muộn ở một thời điểm tùy ý”; tác dụng ghi vào biến xảy ra trong quá trình đánh giá biểu thức đó. Chỉ **giá trị trả về của biểu thức hậu tố** là giá trị trước khi tăng/giảm.

## 4. Kiểu của `++`/`--` và vì sao `byte` vẫn tăng được

Với `++`/`--`, toán hạng phải là một **biến** có kiểu số. Java lấy giá trị của biến, thực hiện phép tăng/giảm theo quy tắc số học, rồi **chuyển kết quả trở lại kiểu của biến** trước khi lưu. Giá trị của biểu thức tăng/giảm cũng có kiểu của biến đó.

Do vậy:

```java
byte count = 127;
count++;  // hợp lệ: byte từ 127 tràn sang -128
byte result = count++; // hợp lệ: kết quả hậu tố cũng có kiểu byte
```

Khác với:

```java
byte count = 5;
// byte invalid = count + 1; // lỗi: count + 1 có kiểu int
// byte invalid2 = +count;   // lỗi: +count có kiểu int
count++;                   // hợp lệ: phép ++ có quy tắc chuyển kiểu đặc biệt
```

Câu `count++` **không tương đương về kiểu** với việc viết một phép gán `count = count + 1;`, vì biểu thức `count + 1` là `int`. `count += 1;` cũng hợp lệ trong nhiều trường hợp nhưng là toán tử gán kết hợp có quy tắc riêng và cần hiểu hệ quả ép kiểu.

Điểm chú ý đối với wrapper:

```java
Integer boxed = 3;
boxed++;  // unbox Integer -> int, tăng, box lại thành Integer
// Integer missing = null;
// missing++; // biên dịch được nhưng ném NullPointerException lúc chạy
```

Wrapper `Integer` là tham chiếu tới object; đây là phép **gán lại biến tham chiếu tới một giá trị mới được boxing**, không có chuyện thay đổi nội dung object `Integer` bất biến.

`char` cũng là kiểu nguyên 16 bit không dấu và có thể tăng:

```java
char letter = 'A';
letter++;                // 'B'
int code = letter;       // 66
```

Không dùng `++` trên `boolean`, `String` hoặc `BigInteger`. Với `BigInteger`, nên gọi `add(BigInteger.ONE)` để tạo giá trị mới.

## 5. Biến nào là toán hạng hợp lệ của `++` và `--`?

Toán tử tăng/giảm phải tác động lên **biến có thể gán được** (*assignable variable*) có kiểu số. Ví dụ:

```java
int local = 1;
local++;                 // biến cục bộ
int[] items = {4};
items[0]++;              // phần tử mảng
Counter counter = new Counter();
counter.value++;         // field có thể gán
```

`Counter` ở trên là một class minh họa, ví dụ:

```java
class Counter {
    int value;
}
```

Các trường hợp dưới đây sai nếu bỏ dấu chú thích:

```java
// 5++;                 // literal không phải biến
// (2 + 3)++;           // giá trị phép cộng không phải biến
// getNumber()++;       // giá trị trả về của method không phải biến
final int limit = 10;
// limit++;             // final không thể gán lại
```

Một biến cục bộ phải được **gán chắc chắn trước khi tăng/giảm**, vì `++` và `--` luôn cần đọc giá trị cũ:

```java
void example() {
    int number;
    // number++; // lỗi biên dịch: number chưa được khởi tạo
    number = 0;
    number++;    // hợp lệ
}
```

Đoạn `example()` chỉ là một method minh họa đặt trong class; không có method `main` nên không độc lập để chạy.

## 6. Thứ tự đánh giá trong biểu thức có nhiều lần tăng

Java đánh giá các toán hạng theo thứ tự xác định từ **trái sang phải**. Vì vậy Java cho kết quả xác định đối với biểu thức như sau:

```java
int i = 1;
int sum = i++ + ++i;
```

Diễn tiến:

1. Đánh giá toán hạng bên trái `i++`: lấy giá trị biểu thức là `1` rồi cập nhật `i = 2`.
2. Đánh giá toán hạng bên phải `++i`: cập nhật `i = 3` rồi lấy giá trị biểu thức là `3`.
3. Cộng `1 + 3` thành `4`; sau câu lệnh, `i = 3`.

Do đó `sum == 4` và `i == 3`. Đây là hành vi Java xác định, nhưng kiểu mã trên **khó đọc và dễ bảo trì sai**. Trong code ứng dụng, ưu tiên tách ra:

```java
int i = 1;
int left = i;
i++;
i++;
int right = i;
int sum = left + right; // 4; i = 3
```

Chỉ đọc `i++` và `++i` theo ý nghĩa giá trị cũ/mới; không dựa vào mẹo ghi nhớ mơ hồ “trước thì trước, sau thì sau” mà quên theo dõi biến được cập nhật lúc nào.

### Bẫy: `i = i++`

```java
int i = 1;
i = i++;
```

Phép `i++` tạo giá trị biểu thức **`1`**, đồng thời cập nhật biến lên 2. Sau đó phép gán bên ngoài ghi **`1`** trở lại `i`. Kết quả cuối cùng vẫn là **`i == 1`**.

Đây là ví dụ điển hình cho thấy hậu tố có hai kết quả cần phân biệt: *side effect* lên biến và *giá trị biểu thức được sử dụng*. Nếu chỉ muốn tăng, viết riêng `i++;`. Đừng kết hợp gán lại cùng một biến.

### Thứ tự đánh giá với phần tử mảng

```java
int[] values = {3};
int index = 0;
int previous = values[index++]++;
```

Java đánh giá mảng và chỉ số, nên phần tử được chọn là `values[0]`; đồng thời `index` tăng lên 1. Toán tử hậu tố trên phần tử trả giá trị cũ `3` rồi ghi `4` vào `values[0]`. Kết quả: `previous == 3`, `values[0] == 4` và `index == 1`.

Khi chỉ cần tăng một phần tử, cách `values[index]++;` thường dễ hiểu hơn; tăng cả `index` và phần tử trong cùng biểu thức có thể gây nhầm lẫn.

## 7. Tràn số và các giá trị biên

`++`, `--` không tự ném exception khi số nguyên primitive vượt phạm vi. Java số nguyên có độ rộng cố định; các phép tính có thể **tràn** (*overflow*).

```java
int max = Integer.MAX_VALUE; // 2147483647
max++;                      // -2147483648

int min = Integer.MIN_VALUE; // -2147483648
min--;                      // 2147483647

byte b = Byte.MAX_VALUE;     // 127
b++;                        // -128
```

Cũng cần chú ý toán tử trừ đơn ngôi tại biên:

```java
int min = Integer.MIN_VALUE;
int negated = -min; // vẫn -2147483648 do int không biểu diễn được +2147483648
long exact = -(long) min; // 2147483648: mở rộng trước rồi đổi dấu
```

Nếu bài toán yêu cầu phát hiện tràn thay vì chấp nhận quay vòng, sử dụng `Math.addExact(x, 1)`, `Math.subtractExact(x, 1)` hoặc `Math.negateExact(x)` khi phù hợp cho kiểu `int`/`long`. Ví dụ `Math.incrementExact(Integer.MAX_VALUE)` ném `ArithmeticException` thay vì trả số âm. Những method này **không được gọi ngầm** bởi `++`/`--`.

Với số thực `float`/`double`, `++`/`--` vẫn hợp lệ; nhưng do độ chính xác hữu hạn, tăng 1 có thể **không làm giá trị thay đổi** ở độ lớn nhất định. Tránh dùng số thực nhị phân cho tiền tệ; đây là vấn đề độ chính xác số học, không phải riêng toán tử `++`.

## 8. Lỗi thường gặp và cách sửa

| Nhầm lẫn / lỗi | Giải thích đúng |
| --- | --- |
| “`i++` và `++i` luôn trả về cùng giá trị” | Chúng cùng tăng biến, nhưng hậu tố trả giá trị cũ; tiền tố trả giá trị mới |
| “`System.out.println(i++)` sẽ in giá trị mới” | `println` nhận giá trị cũ của biểu thức hậu tố |
| “`-b` có kiểu `byte` nếu `b` là byte” | Unary numeric promotion khiến kết quả có kiểu `int` |
| “`~0b1010` bằng `0b0101`” | `~` đảo đủ 32 bit của `int` nên ra `-11`; muốn chỉ 4 bit, cần mặt nạ |
| “`byte b = 127; b++;` là lỗi biên dịch” | Hợp lệ; khi tăng sẽ tràn về `-128` |
| “`i = i++;` giúp tăng i” | Phép gán cuối cùng ghi đè thay đổi, khiến `i` giữ giá trị cũ |
| “Có thể viết `5++` hoặc `getNumber()++`” | Toán hạng phải là biến gán được, không phải literal hay giá trị method |
| “`final int n = 1; n++;` hợp lệ” | Không thể gán lại biến `final` |
| “`Integer n = null; n++;` tự khởi tạo n thành 1” | Unboxing `null` gây `NullPointerException` |
| “`i++` luôn an toàn khi nhiều thread cùng chạy” | Đọc, cộng và ghi là các bước; `++` không bảo đảm cập nhật nguyên tử giữa nhiều thread |

### Ghi chú về nhiều luồng

`counter++` trên biến dùng chung **không phải thao tác nguyên tử**. Hai thread có thể đọc cùng giá trị và ghi đè kết quả của nhau, dẫn đến mất lần tăng. Nếu cần bộ đếm dùng chung, một lựa chọn là `AtomicInteger`:

```java
java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger(0);
int old = counter.getAndIncrement(); // trả giá trị cũ
int current = counter.incrementAndGet(); // trả giá trị mới
```

Đây là ví dụ nhỏ đối chiếu ngữ nghĩa hậu tố/tiền tố; việc thiết kế đồng bộ giữa thread là chủ đề riêng.

## 9. Chương trình chạy độc lập trên Java 17+

Tạo tệp **`UnaryOperatorsDemo.java`**:

```java
public class UnaryOperatorsDemo {
    public static void main(String[] args) {
        int i = 5;
        System.out.println("postIncValue=" + i++);
        System.out.println("afterPostInc=" + i);
        System.out.println("preIncValue=" + ++i);
        System.out.println("afterPreInc=" + i);
        System.out.println("postDecValue=" + i--);
        System.out.println("afterPostDec=" + i);
        System.out.println("preDecValue=" + --i);
        System.out.println("afterPreDec=" + i);

        byte b = 127;
        b++;
        System.out.println("byteWrap=" + b);

        int mask = 0b1010;
        System.out.println("unaryPlus=" + (+mask));
        System.out.println("unaryMinus=" + (-mask));
        System.out.println("bitwiseNot=" + (~mask));
        System.out.println("lowFourNot=" + ((~mask) & 0b1111));
        System.out.println("logicalNot=" + (!true));

        int j = 1;
        int sum = j++ + ++j;
        System.out.println("combinedSum=" + sum);
        System.out.println("combinedJ=" + j);

        int assigned = 1;
        assigned = assigned++;
        System.out.println("assignedPost=" + assigned);

        int[] values = {3};
        int index = 0;
        int previous = values[index++]++;
        System.out.println("arrayOld=" + previous);
        System.out.println("arrayCurrent=" + values[0]);
        System.out.println("arrayIndex=" + index);

        int min = Integer.MIN_VALUE;
        System.out.println("negMinInt=" + (-min));
        System.out.println("negMinLong=" + (-(long) min));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac UnaryOperatorsDemo.java
java UnaryOperatorsDemo
```

Output chính xác:

```text
postIncValue=5
afterPostInc=6
preIncValue=7
afterPreInc=7
postDecValue=7
afterPostDec=6
preDecValue=5
afterPreDec=5
byteWrap=-128
unaryPlus=10
unaryMinus=-10
bitwiseNot=-11
lowFourNot=5
logicalNot=false
combinedSum=4
combinedJ=3
assignedPost=1
arrayOld=3
arrayCurrent=4
arrayIndex=1
negMinInt=-2147483648
negMinLong=2147483648
```

Đọc từng dòng theo hai câu hỏi: **biến đã thay đổi thành gì?** và **giá trị biểu thức được truyền cho `println` là gì?** Ví dụ `postIncValue=5` đi kèm `afterPostInc=6` là bằng chứng rõ ràng rằng hậu tố trả giá trị cũ nhưng đã cập nhật biến.

## 10. Quy trình giải bài toán có `++`/`--`

Khi phân tích một biểu thức, làm theo thứ tự:

1. **Kiểm tra toán hạng:** `++`/`--` cần biến số gán được, đã được gán trước khi đọc và không phải `final` không thể cập nhật.
2. **Xác định vị trí toán tử:** tiền tố trả giá trị sau thay đổi, hậu tố trả giá trị trước thay đổi.
3. **Theo dõi tác dụng phụ:** cập nhật giá trị biến đúng một bước tăng/giảm (có thể tràn hoặc chịu quy tắc số thực).
4. **Theo dõi thứ tự tính biểu thức:** Java đánh giá các toán hạng từ trái sang phải; nếu một biến xuất hiện nhiều lần, ghi lại cả giá trị trả về và giá trị biến tại mỗi bước.
5. **Kiểm tra kiểu:** `+`, `-`, `~` có nâng kiểu số học; `++`, `--` lưu kết quả trở lại kiểu biến và có thể thu hẹp/tràn.
6. **Ưu tiên viết rõ:** trong mã sản phẩm, tránh trộn nhiều lần tăng/giảm hoặc vừa gán vừa hậu tố trên cùng biến. Chia thành các câu lệnh độc lập khi logic không cần biểu thức phức tạp.

**Điểm cần nhớ:** `++i` trả giá trị **mới**, `i++` trả giá trị **cũ**, nhưng cả hai đều tăng biến. Dấu `+`/`-` đơn ngôi và `~` lại tuân theo quy tắc nâng kiểu số học; hiểu rõ kiểu, thứ tự đánh giá và giá trị biên giúp tránh lỗi khó phát hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-006">Độ ưu tiên, tính kết hợp và thứ tự đánh giá toán hạng</a>

<details>
<summary>Xem chi tiết</summary>

# Độ ưu tiên, tính kết hợp và thứ tự đánh giá toán hạng trong Java

> **Java core – numbers · Số học nguyên**  
> **Mục tiêu:** Phân biệt đúng *precedence*, *associativity* và *evaluation order*; đọc được các biểu thức số học nguyên, bitwise và điều kiện; dự đoán kết quả khi toán hạng có tác dụng phụ; biết khi nào phải đặt ngoặc để chương trình rõ nghĩa và an toàn.

## 1. Ba quy tắc trả lời ba câu hỏi khác nhau

Khi đọc biểu thức Java như:

```java
int result = 2 + 3 * 4;
```

Ta cần phân biệt **ba khái niệm**:

| Khái niệm | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| **Độ ưu tiên toán tử** (*operator precedence*) | Toán tử nào **gắn với toán hạng nào**, biểu thức được nhóm như thế nào? | `2 + 3 * 4` được nhóm `2 + (3 * 4)`, kết quả 14 |
| **Tính kết hợp** (*associativity*) | Các toán tử **cùng mức ưu tiên** được nhóm theo bên trái hay bên phải? | `20 - 6 - 2` nhóm `(20 - 6) - 2`, kết quả 12 |
| **Thứ tự đánh giá** (*evaluation order*) | Các **toán hạng/biểu thức con được thực thi** theo trình tự nào, đặc biệt nếu gọi hàm hoặc sửa biến? | Trong `a() + b() * c()`, Java đánh giá `a()` trước `b()` rồi `c()`, mặc dù phép nhân có độ ưu tiên cao hơn |

**Điểm mấu chốt:** “`*` ưu tiên hơn `+`” **không có nghĩa** hàm tạo toán hạng cho phép nhân chắc chắn chạy trước hàm tạo toán hạng cho phép cộng. *Precedence* quyết định **cấu trúc phép tính**; *evaluation order* quyết định **trình tự thực hiện biểu thức con**.

Ví dụ sau đánh giá `left()` trước rồi mới đến `middle()` và `right()`, nhưng vẫn nhân hai kết quả cuối trước khi cộng:

```java
int answer = left() + middle() * right();
// Nhóm tương đương: left() + (middle() * right())
// Thứ tự gọi hàm: left(), middle(), right()
```

Mặc dù một số compiler có thể tối ưu việc tính toán, hành vi quan sát được của chương trình Java phải tuân thủ ngữ nghĩa thứ tự đánh giá được quy định.

## 2. Bảng độ ưu tiên toán tử thường gặp

Đọc **từ trên xuống dưới**: mức trên có độ ưu tiên cao hơn mức dưới. Đây là bảng thực hành tập trung vào số học và các toán tử thường xuất hiện cùng biểu thức số.

| Mức (cao → thấp) | Toán tử | Ví dụ và cách nhóm |
| --- | --- | --- |
| Hậu tố (*postfix*) | `expr++`, `expr--` | `i++` trả giá trị cũ rồi tăng biến |
| Đơn ngôi (*unary*) | `++expr`, `--expr`, `+expr`, `-expr`, `~expr`, `!expr` | `-a * b` nhóm `(-a) * b` |
| Nhân/chia/chia dư | `*`, `/`, `%` | `a + b * c` nhóm `a + (b * c)` |
| Cộng/trừ | `+`, `-` | `a + b << 2` nhóm `(a + b) << 2` |
| Dịch bit | `<<`, `>>`, `>>>` | `1 << 2 + 1` nhóm `1 << (2 + 1)` |
| So sánh thứ tự | `<`, `<=`, `>`, `>=`, `instanceof` | `a + b < c` nhóm `(a + b) < c` |
| So sánh bằng | `==`, `!=` | `a < b == flag` nhóm `(a < b) == flag` |
| AND bitwise/boolean | `&` | `(a & b) == c` phải đặt ngoặc nếu muốn so sánh kết quả AND |
| XOR bitwise/boolean | `^` | `a ^ b & c` nhóm `a ^ (b & c)` |
| OR bitwise/boolean | `|` | `a | b ^ c` nhóm `a | (b ^ c)` |
| AND logic ngắn mạch | `&&` | `checkA() && checkB()` |
| OR logic ngắn mạch | `||` | `checkA() || checkB()` |
| Điều kiện | `? :` | `a ? x : b ? y : z` là `a ? x : (b ? y : z)` |
| Gán và gán kết hợp | `=`, `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `^=`, `|=`, `<<=`, `>>=`, `>>>=` | `a = b = c` nhóm `a = (b = c)` |

**Lưu ý về bảng:** ngoặc tròn `(...)` chủ động chỉ định cách nhóm; truy cập thành viên, gọi phương thức, indexing, cast, tạo đối tượng và các cấu trúc đặc biệt của ngữ pháp cũng có quy tắc riêng. Bảng trên là phần quy tắc quan trọng khi học biểu thức, **không phải toàn bộ ngữ pháp Java**.

Trong code production, dấu ngoặc thường đáng dùng ngay cả khi không bắt buộc, nhất là lúc kết hợp shift, bitwise, so sánh và điều kiện.

### Ví dụ nhanh để đọc đúng bảng

```java
int a = 2 + 3 * 4;         // 14 = 2 + 12
int b = (2 + 3) * 4;       // 20
int c = 1 << 2 + 1;        // 8 = 1 << (2 + 1)
int d = (1 << 2) + 1;      // 5
int e = 17 % 5 * 2;        // 4 = (17 % 5) * 2
int f = 2 | 4 & 3;         // 2 = 2 | (4 & 3)
boolean g = (5 & 3) == 1; // true
```

Biểu thức `5 & 3 == 1` **không tương đương** `(5 & 3) == 1`. Vì `==` có độ ưu tiên cao hơn `&`, Java cố nhóm `5 & (3 == 1)`. Vế phải lúc đó là `boolean`, còn vế trái là `int`, gây **lỗi biên dịch**. Khi kiểm tra bit bằng mask, viết rõ `(value & mask) == expected` hoặc `(value & mask) != 0`.

## 3. Tính kết hợp: nhóm từ trái hoặc từ phải

**Associativity** chỉ có ý nghĩa khi biểu thức gồm các toán tử có cách nhóm có thể gây nhập nhằng tại cùng một mức ưu tiên.

### 3.1. Nhiều toán tử nhị phân kết hợp trái

Các toán tử số học như `+`, `-`, `*`, `/` và `%` ở cùng mức tương ứng được nhóm **từ trái sang phải**.

```text
20 - 6 - 2
= (20 - 6) - 2
= 12

20 - (6 - 2)
= 20 - 4
= 16
```

```java
int leftGrouped = 20 - 6 - 2;      // 12
int rightGrouped = 20 - (6 - 2);   // 16

int quotient = 100 / 5 / 2;       // 10 = (100 / 5) / 2
int other = 100 / (5 / 2);        // 50: 5 / 2 = 2, rồi 100 / 2
```

Vì phép chia nguyên bỏ phần thập phân theo hướng về 0, thay ngoặc có thể thay đổi đáng kể kết quả. Ví dụ `7 / 2 * 2 = 6`, dù biểu thức toán học trên số thực `(7/2)×2 = 7`. Trong Java, `/` và `*` **cùng mức ưu tiên**; cách nhóm bên trái và kiểu số của toán hạng mới quyết định kết quả.

Tương tự, `1 << 2 << 1` nhóm `(1 << 2) << 1`, kết quả 8. Các toán tử shift ở cùng mức có cách nhóm trái.

### 3.2. Phép gán kết hợp phải

```java
int a, b, c;
a = b = c = 7;
```

Cách nhóm là:

```text
a = (b = (c = 7))
```

Cả ba biến đều nhận 7. Biểu thức gán trả về giá trị được gán, vì vậy kết quả của `c = 7` được dùng làm giá trị để gán cho `b`, rồi `a`.

**Không suy ra** từ tính kết hợp phải rằng “mọi toán hạng đều được thực thi từ phải sang trái”. Phần **đánh giá vế trái của phép gán** và các biểu thức con vẫn có quy tắc đánh giá riêng. Cách nhóm của biểu thức và thứ tự thực thi có thể khác nhau.

### 3.3. Toán tử ba ngôi kết hợp phải

```java
int value = false ? 10 : true ? 20 : 30;
// Nhóm: false ? 10 : (true ? 20 : 30)
// Kết quả: 20
```

Để dễ bảo trì, đặt ngoặc rõ ràng trong biểu thức điều kiện lồng nhau, hoặc dùng `if/else` nếu nội dung phức tạp. Chỉ **một** trong hai nhánh tương ứng với kết quả điều kiện được đánh giá.

## 4. Java đánh giá toán hạng theo thứ tự từ trái sang phải

Với phần lớn biểu thức toán tử nhị phân thông thường, Java **đánh giá toán hạng bên trái trước**, rồi toán hạng bên phải; sau đó mới thực hiện phép toán với các giá trị thu được.

Xét:

```java
int result = first() + second() * third();
```

Cây cấu trúc phép tính:

```text
         (+)
        /   \
   first()   (*)
            /   \
       second() third()
```

Trình tự thực thi khi chạy:

```text
1. Gọi first(), giữ giá trị trả về
2. Gọi second(), giữ giá trị trả về
3. Gọi third(), giữ giá trị trả về
4. Nhân kết quả second() với third()
5. Cộng kết quả first() vào tích
```

Đây là ví dụ điển hình chứng minh **độ ưu tiên phép toán** không đồng nghĩa **ưu tiên chạy hàm**.

Lệnh gọi phương thức cũng đánh giá các đối số theo thứ tự từ trái sang phải. Ví dụ `method(first(), second())` gọi `first()` trước `second()`; nếu đối số thứ nhất ném ngoại lệ, đối số thứ hai không được đánh giá.

Java còn bảo đảm sự quan sát nhất quán các tác dụng phụ theo thứ tự quy định, kể cả khi compiler hoặc máy ảo tối ưu các biểu thức không gây thay đổi hành vi quan sát được.

## 5. Increment/decrement: tiền tố và hậu tố

`++x` và `x++` đều tăng biến `x` thêm 1, nhưng **giá trị của biểu thức** khác nhau.

| Cách viết | Giá trị biểu thức | Giá trị biến sau thao tác |
| --- | --- | --- |
| `++x` | Giá trị **mới** của `x` | Đã tăng 1 |
| `x++` | Giá trị **cũ** của `x` | Đã tăng 1 |
| `--x` | Giá trị **mới** sau giảm | Đã giảm 1 |
| `x--` | Giá trị **cũ** trước giảm | Đã giảm 1 |

```java
int i = 1;
int a = i++; // a = 1, i = 2
int b = ++i; // b = 3, i = 3
```

Một biểu thức có nhiều tác dụng phụ vẫn có kết quả **được Java xác định**, nhưng dễ gây lỗi khi đọc:

```java
int i = 1;
int result = i++ + i++ * 10;
// Toán hạng trái của +: i++ trả 1, i thành 2.
// Toán hạng trái của *: i++ trả 2, i thành 3.
// Phép nhân: 2 * 10 = 20.
// Phép cộng: 1 + 20 = 21.
// Sau cùng: result = 21, i = 3.
```

Nếu nhầm rằng “nhân chạy trước nên `i++` của phép nhân phải chạy trước”, bạn sẽ dự đoán sai. Đây chính là sự khác nhau giữa **gắn toán tử với toán hạng** và **thực thi toán hạng**.

Dù Java xác định rõ kết quả, trong code nghiệp vụ nên tách tác dụng phụ để tránh người đọc phải mô phỏng trạng thái biến:

```java
int i = 1;
int first = i++;
int second = i++;
int result = first + second * 10; // 21
```

## 6. Toán tử logic ngắn mạch khác bitwise/boolean không ngắn mạch

Với toán hạng kiểu `boolean`:

- `a && b`: luôn đánh giá `a`; nếu `a` là `false` thì **bỏ qua** `b`.
- `a || b`: luôn đánh giá `a`; nếu `a` là `true` thì **bỏ qua** `b`.
- `a & b`, `a | b`, `a ^ b` dùng được với `boolean` nhưng **đánh giá cả hai vế** (trừ khi việc đánh giá vế trước bị dừng bởi ngoại lệ); không có tính ngắn mạch như `&&`, `||`.
- Với số nguyên, `&`, `|`, `^` là các toán tử bitwise; `&&`/`||` **không dùng cho số nguyên**.

```java
int divisor = 0;
boolean safe = divisor != 0 && 100 / divisor > 10; // false, không chia 0
```

Trong biểu thức trên, nếu thay `&&` bằng `&`, vế phải vẫn chạy và dẫn đến `ArithmeticException: / by zero`. Vì vậy thứ tự đánh giá và việc **có đánh giá hay không** là một phần của tính đúng đắn, không chỉ là chuyện hiệu năng.

```java
boolean accepted = true || expensiveCheck(); // expensiveCheck() không được gọi
boolean both = true & expensiveCheck();       // expensiveCheck() được gọi
```

Toán tử ba ngôi `condition ? whenTrue : whenFalse` cũng chỉ đánh giá một nhánh sau khi đã đánh giá điều kiện. Trong một biểu thức lồng nhau, đặt ngoặc hoặc dùng cấu trúc điều khiển khi nhánh có tác dụng phụ.

## 7. Ngoặc tròn thay đổi cách nhóm, không tự đảo thứ tự chạy

Dùng ngoặc để nêu rõ ý định:

```java
int defaultValue = 2 + 3 * 4;   // 14
int overridden = (2 + 3) * 4;   // 20
```

Nhưng:

```java
int result = first() + (second() * third());
```

vẫn gọi `first()` trước, rồi `second()`, rồi `third()`. Ngoặc ở đây làm rõ **cách nhóm đã có sẵn**; không ra lệnh “chạy `second()` trước `first()`”.

Nếu bạn thật sự muốn điều chỉnh thứ tự tác dụng phụ, hãy viết biểu thức thành nhiều câu lệnh theo thứ tự mong muốn:

```java
int secondValue = second();
int thirdValue = third();
int firstValue = first();
int result = firstValue + secondValue * thirdValue;
```

Không nên thay đổi cách nhóm tùy tiện vì với phép trừ/chia nguyên, tràn số hay phép cộng số thực, việc đổi ngoặc có thể đổi kết quả.

## 8. Riêng với số học nguyên: kiểu toán hạng và phép chia quan trọng không kém độ ưu tiên

### 8.1. Phép nhân, chia, chia dư cùng mức

```java
int a = 7 / 2 * 2;       // (7 / 2) * 2 = 3 * 2 = 6
int b = 7 * 2 / 2;       // (7 * 2) / 2 = 14 / 2 = 7
int c = 20 % 6 * 3;      // (20 % 6) * 3 = 2 * 3 = 6
int d = 20 % (6 * 3);    // 20 % 18 = 2
```

Cả `/`, `*` và `%` có cùng mức ưu tiên; vì thế thứ tự kết hợp trái giải thích sự khác nhau. Phép chia nguyên Java làm tròn thương về **0**: `-7 / 2 == -3`, `7 / -2 == -3`. Phần dư `a % b` thỏa `a == (a / b) * b + (a % b)` trong các trường hợp nguyên thông thường (kể cả tình huống tràn đặc biệt của `MIN_VALUE / -1` theo cách Java xác định), và phần dư có dấu cùng dấu với số bị chia khi khác 0.

### 8.2. Phép toán thực hiện theo kiểu sau numeric promotion

```java
int x = 1_500_000_000;
long wrong = x * 2;           // -1294967296L: phép nhân int tràn trước
long correct = (long) x * 2; // 3000000000L: chuyển x sang long trước
```

Đây là hiệu ứng của **kiểu toán hạng** và **thời điểm cast**, không phải độ ưu tiên đơn thuần. Trong `(long) x * 2`, `(long) x` trở thành `long` trước phép nhân, khiến phép nhân được tính bằng `long`. Gán kết quả phép nhân `int` sang biến `long` sau khi đã tràn không thể lấy lại bit đã mất.

Tương tự, `byte`, `short` và `char` thường được nâng thành `int` khi tham gia phép toán số học nhị phân:

```java
byte a = 10, b = 20;
int sum = a + b;      // int, giá trị 30
// byte bad = a + b;  // lỗi biên dịch: expression int không thể gán ngầm vào byte
```

**Ngoại lệ liên quan hằng:** compiler cho phép một số biểu thức hằng `int` gán cho `byte`/`short`/`char` nếu nằm trong miền phù hợp, ví dụ `byte p = 10 + 20;`. Điều này không biến `byte + byte` thành phép toán có kết quả `byte`.

### 8.3. Dấu âm đơn ngôi và giá trị biên

`-` đơn ngôi có độ ưu tiên cao hơn `*` và `+` nhị phân:

```java
int a = -2 * 3;      // (-2) * 3 = -6
int b = 2 * -3;      // 2 * (-3) = -6
int c = -(2 + 3);    // -5
```

Với `Integer.MIN_VALUE`, phủ định ở kiểu `int` tạo kết quả tràn:

```java
int min = Integer.MIN_VALUE;
int same = -min;                 // vẫn là Integer.MIN_VALUE
long positive = -(long) min;     // 2147483648L
```

Việc mở rộng kiểu phải diễn ra **trước phép đổi dấu** nếu muốn có kết quả toán học dương đúng. Dùng `Math.negateExact` nếu cần phát hiện tràn thay vì chấp nhận vòng bit.

## 9. Dịch bit, mask và phép so sánh

Trong nhóm số học nguyên, việc nhầm ưu tiên giữa cộng, dịch bit và so sánh rất phổ biến:

```java
int a = 1 << 3 + 1;       // 16 vì 1 << (3 + 1)
int b = (1 << 3) + 1;     // 9

int flags = 0b1010;
boolean hasBit = (flags & 0b0010) != 0; // true
```

Để kiểm tra bit bằng mask, nên luôn dùng ngoặc:

```java
if ((flags & mask) != 0) {
    // Có ít nhất một bit trong mask được bật.
}
```

`flags & mask != 0` không được nhóm như phép kiểm tra mong muốn: phép `!=` gắn chặt hơn `&` và gây lỗi kiểu `int & boolean`.

Nhớ rằng toán tử dịch bit thực hiện trên `int` hoặc `long` sau promotion. `>>` dịch phải giữ dấu, `>>>` dịch phải điền 0. Khi số lượng bit dịch là biểu thức số học, cả **ưu tiên dấu cộng** lẫn **loại của toán hạng trái** đều ảnh hưởng kết quả.

## 10. Thứ tự đánh giá trong phép gán và compound assignment

Phép gán kết hợp phải, nhưng vế trái có thể là biến, trường hoặc phần tử mảng có biểu thức truy cập cần đánh giá. Đừng gộp “đặt nhóm từ phải” với “chạy từ phải”.

Ví dụ:

```java
int[] numbers = {10, 20};
int index = 0;
numbers[index++] = 99;
// Phần tử numbers[0] thành 99, index thành 1.
```

Với **compound assignment** như `x += expression`, Java lưu giá trị hiện có của vế trái để thực hiện phép toán kết hợp trước khi ghi kết quả; đối với vế trái có chỉ mục/receiver phức tạp, chúng chỉ được đánh giá một lần theo quy tắc của phép gán kết hợp.

```java
int x = 1;
x += (x = 5);
System.out.println(x); // 6
```

Diễn giải: vế trái của `+=` đọc và giữ giá trị cũ `1`; vế phải `(x = 5)` đặt `x` thành `5` và có giá trị `5`; phép `+=` sử dụng giá trị đã giữ `1 + 5` rồi gán cuối cùng `6` cho `x`.

Phép gán kết hợp cũng chứa chuyển kiểu ngầm về kiểu của biến bên trái. Ví dụ:

```java
byte count = 127;
count += 1;          // count thành -128 do ép hẹp, không ném lỗi
// count = count + 1; // lỗi biên dịch vì count + 1 có kiểu int
```

Về khái niệm, `E1 op= E2` gần với `E1 = (T)((E1) op (E2))` trong đó `T` là kiểu của `E1`, **nhưng `E1` chỉ được đánh giá một lần**. Vì vậy không được triển khai thay bằng phép gán thường một cách máy móc nếu `E1` có tác dụng phụ hoặc là vị trí truy cập mảng/phương thức.

## 11. Khi xảy ra ngoại lệ, thứ tự đánh giá quyết định tác dụng phụ nào đã xảy ra

Phép chia nguyên cho 0 ném `ArithmeticException`. Với biểu thức:

```java
int result = numerator() / denominator();
```

Java đánh giá `numerator()` trước rồi `denominator()`. Sau đó phép chia được thực hiện; nếu mẫu số bằng 0 thì ném ngoại lệ. Nếu `numerator()` đã in log hoặc cập nhật biến, tác dụng phụ đó đã xảy ra.

Trong `left() + right()`, nếu `left()` ném ngoại lệ thì `right()` không được gọi. Cùng nguyên lý áp dụng cho các đối số hàm đánh giá từ trái sang phải. Còn trong `&&`/`||`, một vế có thể không được đánh giá vì ngắn mạch ngay cả khi không có ngoại lệ.

Khi logic chỉ có ý nghĩa nếu các bước thành công, phân chia thành các câu lệnh riêng thường làm rõ hơn những nơi có thể phát sinh ngoại lệ và những thay đổi trạng thái đã thực hiện.

## 12. Chương trình Java 17+ tự chạy và kết quả

Tạo file **`OperatorEvaluationDemo.java`**:

```java
public class OperatorEvaluationDemo {
    private static int number(String name, int value) {
        System.out.print(name + " ");
        return value;
    }

    private static boolean condition(String name, boolean value) {
        System.out.print(name + " ");
        return value;
    }

    public static void main(String[] args) {
        System.out.print("precedence: ");
        int p = number("A", 2) + number("B", 3) * number("C", 4);
        System.out.println("=> " + p);

        System.out.print("associativity: ");
        int s = number("X", 20) - number("Y", 6) - number("Z", 2);
        System.out.println("=> " + s);

        int shift = 1 << 2 + 1;
        int mask = 5 & 3;
        System.out.println("shift = " + shift);
        System.out.println("mask = " + mask);

        int i = 1;
        int value = i++ + i++ * 10;
        System.out.println("postfix value = " + value + ", i = " + i);

        int x = 1;
        x += (x = 5);
        System.out.println("compound x = " + x);

        System.out.print("short-circuit AND: ");
        boolean and = condition("left", false)
                && condition("right", true);
        System.out.println("=> " + and);

        System.out.print("short-circuit OR: ");
        boolean or = condition("left", true)
                || condition("right", false);
        System.out.println("=> " + or);

        System.out.print("conditional: ");
        int selected = condition("test", false)
                ? number("then", 10)
                : number("else", 20);
        System.out.println("=> " + selected);

        System.out.print("division: ");
        try {
            int quotient = number("numerator", 10)
                    / number("denominator", 0);
            System.out.println("=> " + quotient);
        } catch (ArithmeticException e) {
            System.out.println("=> / by zero");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac OperatorEvaluationDemo.java
java OperatorEvaluationDemo
```

**Kết quả chuẩn:**

```text
precedence: A B C => 14
associativity: X Y Z => 12
shift = 8
mask = 1
postfix value = 21, i = 3
compound x = 6
short-circuit AND: left => false
short-circuit OR: left => true
conditional: test else => 20
division: numerator denominator => / by zero
```

Những điểm cần quan sát:

- Dù phép nhân có mức ưu tiên cao hơn phép cộng, `A` vẫn được đánh giá trước `B` và `C`. Kết quả tính toán là `2 + (3 * 4) = 14`.
- Phép trừ kết hợp trái: `(20 − 6) − 2 = 12`.
- `1 << 2 + 1` lấy số bit dịch là `3`; phép `&` trên `5` và `3` cho `1`.
- Hai lần `i++` lần lượt đọc `1` và `2`; kết quả `21` và `i` cuối cùng bằng `3`.
- `+=` giữ giá trị cũ của vế trái nên `x` cuối cùng bằng `6`.
- `right` không in ra với `&&` khi vế trái sai, và với `||` khi vế trái đúng.
- Toán tử `?:` chỉ gọi `else` khi điều kiện sai.
- Phép chia đã gọi cả `numerator` lẫn `denominator` trước khi phát hiện số chia bằng 0.

## 13. Lỗi hay gặp trong thực tế

| Nhầm lẫn | Vì sao sai và cách khắc phục |
| --- | --- |
| “`*` ưu tiên cao hơn nên hàm ở vế nhân được gọi trước” | Java đánh giá toán hạng bên trái trước; ưu tiên chỉ xác định nhóm phép toán |
| “Kết hợp trái nghĩa là mọi hàm/phép toán đều chạy ngay lập tức từ trái sang phải” | Kết hợp quyết định cây biểu thức; kết quả phép toán chỉ tính khi đủ toán hạng, trong khi việc gọi hàm có thứ tự riêng |
| “`a = b = c` chạy mọi thứ từ phải sang trái” | Đây là **cách nhóm phép gán**; việc đánh giá các biểu thức làm vế trái có quy tắc riêng |
| “`100 / 5 / 2` = `100 / (5 / 2)`” | Cách nhóm khác nhau; phép chia nguyên còn làm tròn về 0 |
| “`1 << 2 + 1` dịch 2 bit rồi cộng 1” | `+` ưu tiên hơn `<<`; thực tế dịch 3 bit |
| “`flags & mask != 0` kiểm tra mask” | `!=` ưu tiên hơn `&`; cần `(flags & mask) != 0` |
| “`&&` và `&` giống nhau với boolean” | `&&` có ngắn mạch, `&` đánh giá cả hai vế |
| “`i++ + i++` là không có thứ tự trong Java” | Java quy định thứ tự trái sang phải; vẫn nên tránh vì khó bảo trì |
| “`long x = intA * intB` tránh tràn `int`” | Tràn có thể đã xảy ra trong phép nhân trước khi gán; cast trước phép nhân |
| “Ngoặc cho phép gọi hàm bên phải trước” | Ngoặc đổi cấu trúc phép tính, không tự đảo thứ tự đánh giá các hàm |
| “`x += y` luôn tương đương `x = x + y`” | Có chuyển kiểu ngầm và bảo đảm vị trí bên trái chỉ được đánh giá một lần |
| “Thay đổi ngoặc luôn cho cùng kết quả theo toán học” | Chia nguyên, tràn số, số thực nhị phân và tác dụng phụ đều có thể làm kết quả khác |

## 14. Bài tập tự kiểm tra

Hãy tính kết quả theo Java, không dựa riêng vào trực giác về toán học.

**Bài 1.** Kết quả của `int x = 2 + 4 * 3 - 5;` là gì?

**Bài 2.** `int x = 32 / 4 / 2;` khác thế nào với `int y = 32 / (4 / 2);`?

**Bài 3.** `int x = 1 << 1 + 2;` cho số nào?

**Bài 4.** Với `int flags = 0b1100; int mask = 0b0100;`, viết điều kiện boolean để kiểm tra bit của `mask` có bật trong `flags` không.

**Bài 5.** Sau đoạn này, `result` và `i` có giá trị bao nhiêu?

```java
int i = 1;
int result = ++i + i++ * 3;
```

**Bài 6.** Vì sao hai biểu thức sau có thể cho kết quả khác nhau?

```java
int a = 7 / 2 * 2;
int b = 7 * 2 / 2;
```

**Bài 7.** `boolean ok = false && (10 / 0 > 1);` có gây ngoại lệ lúc chạy không? So với việc dùng `&` thì sao?

**Bài 8.** `int x = 1; x += (x = 4);` sẽ để `x` bằng mấy? Vì sao?

### Đáp án giải thích

1. `2 + 12 - 5 = 9`, do `*` ưu tiên hơn `+`/`-`.
2. `x = (32/4)/2 = 4`; `y = 32/(4/2) = 16`.
3. `1 << (1 + 2) = 1 << 3 = 8`.
4. `boolean enabled = (flags & mask) != 0;`, kết quả `true`.
5. `++i` trả `2` và để `i=2`; `i++` tiếp theo trả `2` rồi để `i=3`; `result = 2 + 2*3 = 8`.
6. Trong `7 / 2 * 2`, phép chia nguyên cho `3` trước rồi nhân được `6`; trong `7 * 2 / 2`, nhân trước cho `14` rồi chia cho `2` được `7`.
7. `false && ...` ngắn mạch nên không đánh giá phép chia; không ném ngoại lệ. Với `false & ...`, Java đánh giá vế phải và phép chia cho 0 ném `ArithmeticException`. *Trong bài tập, số chia bằng literal 0 nằm trong biểu thức runtime; biểu thức được biên dịch và lỗi xảy ra nếu thực sự đánh giá phép chia.*
8. `x += (x = 4)` giữ `x` ban đầu là `1` cho vế trái; vế phải gán `4`; kết quả phép cộng là `1+4=5`, nên `x` cuối cùng bằng `5`.

## 15. Checklist khi đọc hoặc viết biểu thức phức tạp

1. **Xác định kiểu** của từng toán hạng: `int`, `long`, `boolean` hay kiểu tham chiếu? Phép toán có promote, cast hẹp hoặc tràn không?
2. **Nhóm biểu thức** theo ngoặc và độ ưu tiên; khi cùng mức, xác định tính kết hợp.
3. **Đánh dấu thứ tự đánh giá toán hạng**, nhất là các lời gọi hàm, `++`/`--`, phép gán và truy cập mảng.
4. **Kiểm tra ngắn mạch** ở `&&`, `||` và nhánh của `?:`; vế nào có thể bị bỏ qua?
5. **Kiểm tra ngoại lệ** và tác dụng phụ: vế nào đã chạy trước khi một phép chia 0, truy cập sai hoặc hàm ném lỗi?
6. **Thêm ngoặc và tách câu lệnh** nếu người đọc phải nhớ nhiều quy tắc cùng lúc; ưu tiên tính rõ ràng trong code tính tiền, dữ liệu bit và điều kiện xác thực.
7. **Không áp dụng biến đổi đại số tùy tiện**: tính toán trên integer có giới hạn miền và phép chia nguyên; trên floating-point có làm tròn và các giá trị đặc biệt.

**Ghi nhớ:** **Ưu tiên** chọn phép toán gắn với toán hạng; **kết hợp** quyết định cách nhóm các toán tử cùng cấp; **thứ tự đánh giá** quyết định biểu thức con nào thực sự chạy trước. Phân biệt chính xác cả ba giúp bạn dự đoán đúng giá trị, tác dụng phụ và ngoại lệ của biểu thức Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-007">Dấu ngoặc và tác động tới giá trị trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu ngoặc và tác động tới giá trị trung gian trong số học nguyên Java

## 1. Mục tiêu và tình huống đặt vấn đề

Cùng những toán hạng, chương trình có thể trả kết quả rất khác khi thay vị trí dấu ngoặc. Với số học nguyên trong Java, điều đó xảy ra vì **dấu ngoặc quyết định cách nhóm phép toán**, còn **mỗi phép toán trung gian có kiểu dữ liệu, quy tắc chia nguyên và giới hạn biểu diễn riêng**. Một kết quả trung gian đã bị cắt phần dư hoặc tràn miền giá trị sẽ không được “khôi phục” chỉ vì kết quả cuối cùng được gán vào biến rộng hơn.

Sau bài này, bạn có thể:

- Đọc được cách Java nhóm biểu thức bằng **độ ưu tiên toán tử**, **tính kết hợp (associativity)** và dấu ngoặc `( ... )`.
- Phân biệt thứ tự **nhóm phép toán** với thứ tự **đánh giá toán hạng** từ trái sang phải.
- Dự đoán chính xác kết quả của các phép `+`, `-`, `*`, `/` và `%` khi thay dấu ngoặc.
- Nhận diện nơi **phép chia nguyên cắt phần thập phân**, nơi **`int`/`long` có thể tràn**, nơi **numeric promotion** hay **cast** thay đổi kiểu tính trung gian.
- Chọn cách đặt ngoặc, chuyển kiểu và kiểm tra overflow phù hợp với yêu cầu tính toán; tự chạy ví dụ Java 17+ và giải thích stdout.

**Phạm vi:** số học nguyên Java Core với `byte`, `short`, `int`, `long`; số thực xuất hiện khi cần đối chiếu tác động của ép kiểu. Các ví dụ dùng Java SE 17 trở lên và không cần thư viện ngoài JDK.

## 2. Ba quy tắc phải tách riêng

### 2.1. Độ ưu tiên (precedence) — toán tử nào được nhóm chặt hơn?

Với các toán tử số học thường gặp, thứ tự ưu tiên từ cao xuống thấp là:

| Mức | Cú pháp | Ý nghĩa chính |
| --- | --- | --- |
| 1 | `( ... )` | Chỉ rõ biểu thức con; ưu tiên việc tính biểu thức bên trong theo cấu trúc đã nhóm |
| 2 | `+x`, `-x`, `++x`, `x++` | Toán tử một ngôi/tăng giảm; prefix, postfix có chi tiết ưu tiên riêng |
| 3 | `*`, `/`, `%` | Nhân, chia, lấy dư |
| 4 | `+`, `-` | Cộng, trừ |

Bảng giản lược chỉ phục vụ **số học**; biểu thức Java còn có shift, so sánh, logic, điều kiện, gán và các cấu trúc khác với thứ tự ưu tiên riêng. Toán tử một ngôi không đồng nghĩa mọi dạng prefix/postfix đều có cùng mức trong bảng đầy đủ.

Ví dụ:

```java
int result = 10 + 2 * 3;      // 16: 10 + (2 * 3)
int changed = (10 + 2) * 3;   // 36: (10 + 2) * 3
```

Dấu ngoặc không phải “phép toán mới” làm biến đổi dữ liệu. Nó yêu cầu tính **biểu thức con** theo cách nhóm khác, từ đó có thể thay đổi giá trị/kiểu của kết quả trung gian.

### 2.2. Tính kết hợp (associativity) — nhóm thế nào khi cùng mức ưu tiên?

Các phép số học nhị phân `+`, `-`, `*`, `/` và `%` được nhóm **từ trái sang phải** khi không có ngoặc điều chỉnh.

```java
int x = 20 / 3 / 2;      // (20 / 3) / 2 = 6 / 2 = 3
int y = 20 / (3 / 2);    // 20 / 1 = 20
```

Hai biểu thức có cùng toán hạng theo cùng thứ tự nhưng **cấu trúc phép chia khác nhau**. Phép chia không có tính kết hợp đại số, nên dấu ngoặc đặc biệt quan trọng.

Ví dụ khác:

```java
int a = 15 - 6 - 2;      // (15 - 6) - 2 = 7
int b = 15 - (6 - 2);    // 15 - 4 = 11
```

Không nên hiểu “kết hợp trái” là Java được tùy ý đảo thứ tự phép toán trong mọi trường hợp. Đây là quy tắc **phân tích cấu trúc biểu thức**.

### 2.3. Thứ tự đánh giá (evaluation order) — toán hạng nào được thực thi trước?

Java bảo đảm các toán hạng của toán tử nhị phân được **đánh giá từ trái sang phải**, ngay cả khi toán tử bên phải có độ ưu tiên cao hơn. Ví dụ `left() + middle() * right()` được nhóm thành `left() + (middle() * right())`; khi chạy, `left()` vẫn được gọi trước `middle()` và `right()`.

Phân biệt rõ:

- **Grouping/precedence:** biểu thức con nào cung cấp kết quả cho phép toán nào.
- **Evaluation order:** thao tác, lời gọi phương thức hoặc side effect nào xảy ra trước.
- **Integer intermediate:** kết quả tại từng phép số học mang kiểu và quy tắc của chính phép đó.

Vì vậy câu “Java luôn thực thi phép nhân trước phép cộng” là cách nói thiếu chính xác nếu các toán hạng có lời gọi phương thức/side effect. Java **nhóm** phép nhân chặt hơn; đánh giá toán hạng vẫn phải tôn trọng trái sang phải.

## 3. Dấu ngoặc làm đổi kết quả phép cộng, trừ, nhân, chia

| Biểu thức | Tính từng bước | Kết quả `int` |
| --- | --- | ---: |
| `18 / 3 + 3` | `(18 / 3) + 3 = 6 + 3` | 9 |
| `18 / (3 + 3)` | `18 / 6` | 3 |
| `20 / 3 / 2` | `(20 / 3) / 2 = 6 / 2` | 3 |
| `20 / (3 / 2)` | `20 / 1` | 20 |
| `10 + 2 * 3` | `10 + (2 * 3)` | 16 |
| `(10 + 2) * 3` | `12 * 3` | 36 |
| `15 - 6 - 2` | `(15 - 6) - 2` | 7 |
| `15 - (6 - 2)` | `15 - 4` | 11 |

Dấu ngoặc hữu ích khi bạn muốn biểu thức phản ánh công thức nghiệp vụ, chẳng hạn:

- Số nhóm cần xử lý từ `items` và `groupSize`.
- Tổng trọng số chia cho tổng mẫu.
- Thứ tự áp dụng khấu trừ hay điều chỉnh số lượng.

Tuy nhiên, công thức viết giống toán học **chưa đủ** để bảo đảm kết quả: kiểu phép tính và khả năng tràn phải được phân tích độc lập.

## 4. Điều quan trọng nhất: phép chia nguyên cắt phần dư ngay tại bước trung gian

Trong Java, nếu **cả hai toán hạng của `/` là kiểu nguyên**, phép chia nguyên tạo kết quả nguyên và **bỏ phần thập phân theo hướng về 0**. Đặt ngoặc có thể làm phép chia xảy ra sớm hơn hoặc muộn hơn.

```java
int items = 7;
int groups = 2;
int quotient = items / groups;       // 3
int beforeMultiply = (items / groups) * groups; // 6
int afterMultiply = (items * groups) / groups;  // 7
```

Vì `7 / 2` đã trở thành `3`, `(7 / 2) * 2` trở thành `6`, không thể tự lấy lại phần dư `1`. Biểu thức `(7 * 2) / 2` nhận `7` trong ví dụ nhỏ này vì phép nhân xảy ra trước phép chia và không tràn. **Không** suy ra cách “nhân trước chia” luôn an toàn: tích trung gian có thể vượt miền `int`.

### 4.1. Chia cho số âm và toán tử phần dư

`-7 / 2` bằng `-3`, **không phải `-4`**; `-7 % 2` bằng `-1`. Trong Java, số dư có dấu theo số bị chia (dividend) đối với `%` số nguyên, và khi phép tính hợp lệ, `a == (a / b) * b + (a % b)` theo ngữ nghĩa số nguyên.

Ví dụ:

```java
int a = -7;
int b = 2;
System.out.println(a / b);   // -3
System.out.println(a % b);   // -1
```

Tránh áp dụng vô thức hàm floor của toán học cho phép `int / int`. Nếu bài toán muốn chia làm tròn xuống với số âm, xem `Math.floorDiv` và `Math.floorMod`; cần đối chiếu quy ước dấu và các giá trị biên. Chúng **không** đơn thuần là thêm dấu ngoặc cho phép chia nguyên.

### 4.2. Tránh phép tính trung gian khác ý định

```java
int a = 20;
int b = 3;
int c = 2;

int leftGrouped = (a / b) / c; // 3
int rightGrouped = a / (b / c); // 20
```

Trong `a / (b / c)`, hãy tính `b / c` trước: `3 / 2 = 1`. Nếu `b / c` bằng `0`, phép chia ngoài sẽ gây `ArithmeticException`, dù phép chia trái sang phải có thể hoàn toàn hợp lệ.

Nếu mẫu số phụ thuộc dữ liệu, kiểm tra nó **sau khi đã tính đúng biểu thức con**, không chỉ kiểm tra biến ban đầu khác `0`.

## 5. Dấu ngoặc không tự nâng `int` lên `long`

Đây là lỗi thường gặp nhất khi làm phép cộng hoặc nhân trên dữ liệu số nguyên lớn.

Giả sử:

```java
int a = 2_000_000_000;
int b = 1_000_000_000;

long wrong = (long) (a + b);
long correct = (long) a + b;
```

Cách diễn giải:

1. Trong `(a + b)`, cả hai toán hạng là `int`, nên phép cộng diễn ra **trong `int`**. Tổng toán học là `3_000_000_000` vượt `Integer.MAX_VALUE`; kết quả `int` bị tràn thành `-1_294_967_296`.
2. Cast `(long) (a + b)` chỉ mở rộng **giá trị đã tràn**; `wrong` vẫn là `-1_294_967_296L`.
3. Trong `(long) a + b`, toán hạng `a` đã được chuyển sang `long` **trước khi cộng**. Binary numeric promotion chuyển `b` sang `long` và phép cộng trả `3_000_000_000L`.

**Nguyên tắc:** để phép tính trung gian dùng kiểu rộng, hãy chuyển kiểu **trước phép có thể tràn**. Đặt ngoặc quanh toàn phép tính rồi cast sau đó là quá muộn.

Ví dụ tương đương cho phép nhân:

```java
int quantity = 50_000;
int unit = 50_000;
long wrong = quantity * unit;          // int multiplication overflows
long correct = (long) quantity * unit; // long multiplication
```

`wrong` nhận giá trị `-1_794_967_296` vì phép `int * int` tràn rồi mới widening sang `long`; `correct` là `2_500_000_000L`.

Nhưng `long` cũng có giới hạn: khi các toán hạng có thể vượt 64 bit, cân nhắc `BigInteger` hoặc kiểm tra tràn. Không có phép cast kỳ diệu nào khiến phép tính primitive có độ rộng vô hạn.

## 6. Bẫy: phép nhân tràn dù thương cuối cùng đáng lẽ vừa trong `int`

Công thức toán học `n(n + 1) / 2` tính tổng từ 1 đến n. Với `n = 50_000`:

- Tổng toán học bằng `1_250_025_000`, **vừa trong `int`**.
- Tích trung gian `50_000 × 50_001 = 2_500_050_000` lại **vượt `int`**.
- Tính `n * (n + 1) / 2` bằng toàn `int` làm tràn trước khi chia, cho kết quả sai.

```java
int n = 50_000;
int wrong = n * (n + 1) / 2;
long correct = (long) n * (n + 1) / 2;
```

Kết quả:

```text
wrong   = -897458648
correct = 1250025000
```

Dấu ngoặc `(n + 1)` giúp ghi công thức rõ ràng nhưng **không** tự bảo vệ phép nhân tiếp theo. Cast `(long) n` đặt trước phép nhân mới bảo vệ được bước trung gian. Với bài toán cần giữ kết quả cuối là `int` và phải báo lỗi nếu ngoài miền, bạn có thể kiểm tra bằng `Math.toIntExact(correct)` sau khi tính bằng `long`.

Đây là lý do khi review biểu thức, phải xét **miền của từng node trong cây phép toán**, thay vì chỉ xét biến đích hay giá trị cuối kỳ vọng.

## 7. Numeric promotion và phạm vi của cast

Java có quy tắc **binary numeric promotion** cho các toán tử số học nhị phân thường gặp:

- `byte`, `short` và `char` được nâng lên tối thiểu `int` khi tham gia phép toán số học nhị phân.
- Nếu có toán hạng `long` và không có kiểu chấm động thì phép tính nhận kiểu `long`; nếu chỉ có `int` trở xuống thì nhận `int`.
- Khi có toán hạng `float`/`double`, phép tính chuyển sang floating point theo quy tắc promotion tương ứng; đó là chủ đề đối chiếu, không phải giải pháp mặc định cho mọi lỗi overflow số nguyên.

### 7.1. Cùng dấu ngoặc, khác kiểu phép tính

```java
byte left = 100;
byte right = 20;
// byte sum = left + right; // lỗi biên dịch: byte + byte => int
int sum = left + right;
```

Dù `120` vừa trong `byte`, **kiểu của biểu thức `left + right` là `int`**. Nếu thật sự cần chuyển hẹp, `byte sum = (byte) (left + right);` có thể biên dịch nhưng **có thể mất dữ liệu** với đầu vào khác; không coi cast là kiểm tra an toàn.

### 7.2. Cast bên ngoài và cast bên trong khác nhau

Với `a = 7`, `b = 2`:

| Biểu thức | Phép tính trung gian | Kết quả |
| --- | --- | --- |
| `(double) (a / b)` | `a / b` là `int` bằng 3, sau đó đổi sang `double` | `3.0` |
| `(double) a / b` | Đổi `a` sang `double` trước, rồi chia | `3.5` |
| `a / (double) b` | Đổi `b` sang `double` trước, rồi chia | `3.5` |
| `(long) (a * b)` | Nhân trong `int` trước, sau đó widening | Kết quả có thể đã tràn |
| `(long) a * b` | Nhân trong `long` | Tránh tràn `int`, nhưng vẫn phải xét giới hạn `long` |

Không đổi một biểu thức từ số học nguyên sang `double` chỉ để “né” tràn khi nghiệp vụ cần **độ chính xác số nguyên tuyệt đối**. `double` không biểu diễn chính xác mọi số nguyên `long` (vùng nguyên chính xác liên tiếp chỉ đến 2⁵³). Với phép tính số nguyên lớn, dùng `long`/`BigInteger` theo yêu cầu.

## 8. Phân biệt tính kết hợp đại số và tính kết hợp trong Java

Đây là điểm dễ bị dạy sai. Phép **cộng và nhân số nguyên thuần `int` hoặc `long`** của Java, nếu cùng kiểu và không có side effect, vẫn có tính kết hợp theo kết quả modulo 2³²/2⁶⁴ kể cả khi phép tính tràn:

```java
int x = Integer.MAX_VALUE;
int y = 1;
int z = -1;

int first = (x + y) + z;
int second = x + (y + z);
// first == second == Integer.MAX_VALUE
```

Ở cách nhóm đầu, `x + y` tràn thành `Integer.MIN_VALUE` trước rồi cộng `-1`; ở cách nhóm sau, `y + z` thành `0`. Giá trị **trung gian khác nhau**, nhưng kết quả cuối `int` bằng nhau. Không nên kết luận rằng cứ đổi dấu ngoặc cộng `int` thuần là đổi kết quả cuối.

Tính kết hợp trên **không cho phép** tùy ý tái nhóm khi:

- Biểu thức có **chia/lấy dư**, vì chúng không có tính kết hợp tương tự.
- Có **kiểu trung gian khác nhau**, cast, lời gọi chuyển kiểu hoặc các phương thức có hợp đồng riêng.
- Có **side effect** như `++`, phép gán, I/O hoặc gọi phương thức quan sát được.
- Dùng API **kiểm tra tràn** (chẳng hạn `Math.addExact`) thay cho `+` thông thường: điểm phát hiện lỗi trung gian có thể khác.
- Chuyển sang **số thực**: cộng và nhân dấu phẩy động không có tính kết hợp tổng quát do rounding, overflow và NaN.

### 8.1. Ví dụ với phép cộng được kiểm tra tràn

`Math.addExact(a,b)` ném `ArithmeticException` nếu phép cộng vượt miền kiểu trả về:

```java
int x = Integer.MAX_VALUE;
int y = 1;
int z = -1;

// Math.addExact(Math.addExact(x, y), z); // throws: inner add overflows
int safeGrouping = Math.addExact(x, Math.addExact(y, z)); // MAX_VALUE
```

Hai biểu thức số học có cùng tổng toán học; tuy nhiên **hợp đồng phát hiện tràn ở mỗi bước** khác nhau. Không dùng phép cộng `+` tràn để chứng minh phép cộng checked an toàn.

Khi cần tránh báo overflow trung gian nếu chỉ quan tâm tổng cuối, hãy quyết định rõ yêu cầu nghiệp vụ: có thể cộng trong `long`/`BigInteger` rồi kiểm tra miền đích sau; hoặc yêu cầu mọi phép cộng từng bước phải an toàn bằng `addExact`. Đó là hai hợp đồng khác nhau.

## 9. Số học trung gian và thứ tự đánh giá có side effect

Dấu ngoặc thay đổi cách nhóm, nhưng Java vẫn đánh giá các toán hạng theo quy tắc của ngôn ngữ. Ví dụ runnable dưới đây cho thấy lời gọi `left()` xuất hiện trước `right()` dù phép nhân ở vế phải có độ ưu tiên cao hơn phép cộng.

Lưu thành **`EvaluationOrder.java`**:

```java
public class EvaluationOrder {
    static int left() {
        System.out.println("left");
        return 10;
    }

    static int right() {
        System.out.println("right");
        return 3;
    }

    public static void main(String[] args) {
        int result = left() + right() * 2;
        System.out.println(result);
    }
}
```

Chạy:

```bash
javac --release 17 EvaluationOrder.java
java EvaluationOrder
```

Stdout:

```text
left
right
16
```

`right() * 2` được nhóm trước khi cộng, nhưng `left()` vẫn được đánh giá đầu tiên. Java không được tự ý đảo hành vi quan sát được chỉ vì phép nhân có ưu tiên cao.

### 9.1. Tránh nhét `++` hoặc gán vào cùng biểu thức phức tạp

```java
int value = 4;
int result = value + value++;
```

Java có quy tắc xác định cho code trên: toán hạng trái đọc `4` trước, toán hạng phải `value++` trả giá trị cũ `4` và tăng biến lên `5`; `result` là `8`. Tuy vậy đây là mã **khó đọc, dễ nhầm** khi review, nhất là nếu còn lồng dấu ngoặc, phương thức và nhiều side effect.

Thay bằng các bước tường minh:

```java
int value = 4;
int original = value;
value++;
int result = original + original;
```

Ví dụ thay thế thể hiện ý định “cộng hai lần giá trị trước khi tăng”. Dấu ngoặc không thay thế việc tách side effect khỏi phép tính. Còn với các toán tử điều kiện `&&`, `||` và `?:`, Java có quy tắc đánh giá có điều kiện; không được gom chúng vào cách hiểu “luôn đánh giá mọi toán hạng”.

## 10. Ví dụ hoàn chỉnh: dấu ngoặc và mất giá trị trung gian

Lưu nội dung sau thành **`IntegerParentheses.java`**:

```java
public class IntegerParentheses {
    public static void main(String[] args) {
        System.out.println(18 / 3 + 3);
        System.out.println(18 / (3 + 3));

        System.out.println((20 / 3) / 2);
        System.out.println(20 / (3 / 2));

        int seven = 7;
        System.out.println((seven / 2) * 2);
        System.out.println((seven * 2) / 2);

        System.out.println((double) (seven / 2));
        System.out.println((double) seven / 2);

        System.out.println(-7 / 2);
        System.out.println(-7 % 2);
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac --release 17 IntegerParentheses.java
java IntegerParentheses
```

**Stdout chính xác:**

```text
9
3
3
20
6
7
3.0
3.5
-3
-1
```

Điểm cần quan sát: dấu ngoặc `(3 + 3)` thay đổi mẫu của phép chia; `20 / (3 / 2)` tính mẫu trong `int` trước; `(seven / 2) * 2` mất phần dư; cast sau chia không thể lấy lại `0.5`; với số âm, phép chia nguyên cắt về 0.

## 11. Ví dụ hoàn chỉnh: tràn trước khi gán sang kiểu lớn

Lưu thành **`IntermediateOverflow.java`**:

```java
public class IntermediateOverflow {
    public static void main(String[] args) {
        int a = 2_000_000_000;
        int b = 1_000_000_000;

        long lateCast = (long) (a + b);
        long earlyCast = (long) a + b;
        System.out.println(lateCast);
        System.out.println(earlyCast);

        int n = 50_000;
        int wrongSum = n * (n + 1) / 2;
        long correctSum = (long) n * (n + 1) / 2;
        System.out.println(wrongSum);
        System.out.println(correctSum);

        try {
            Math.addExact(a, b);
            System.out.println("no overflow");
        } catch (ArithmeticException ex) {
            System.out.println("overflow detected");
        }
    }
}
```

Chạy:

```bash
javac --release 17 IntermediateOverflow.java
java IntermediateOverflow
```

**Stdout chính xác:**

```text
-1294967296
3000000000
-897458648
1250025000
overflow detected
```

Từng dòng giải thích được bằng kiểu phép tính trung gian:

1. `a + b` tính `int` trước rồi mới cast, nhận giá trị đã tràn.
2. `(long) a + b` sử dụng phép cộng `long` từ đầu.
3. `n * (n + 1)` tính `int` và tràn trước khi chia hai.
4. `(long) n * (n + 1)` tính `long` từ phép nhân nên ra tổng toán học đúng.
5. `Math.addExact(a,b)` phát hiện phép cộng `int` vượt giới hạn và ném ngoại lệ đã được catch.

Trong mã sản xuất, khi tổng phải là `int`, có thể thực hiện phép cộng `long` rồi dùng `Math.toIntExact` để kiểm tra trước khi chuyển về `int`. Tránh dùng cast hẹp `(int)` như thể nó là phép kiểm tra.

## 12. Trường hợp biên: số nguyên nhỏ nhất, chia cho 0, cast và toán tử âm

### 12.1. Chia nguyên cho 0

`int` hoặc `long` chia cho 0 (hoặc lấy dư với 0) ném `ArithmeticException` tại lúc chạy nếu phép chia thật sự được thực hiện.

```java
int denominator = 0;
// int invalid = 10 / denominator; // runtime ArithmeticException
```

Khi mẫu là **biểu thức có ngoặc**, phải xét **giá trị mẫu sau khi tính**:

```java
int a = 10;
int b = 2;
int c = 3;
// int invalid = a / (b / c); // b / c = 0, rồi 10 / 0
```

Đừng kiểm tra riêng `b != 0` và `c != 0` rồi cho rằng `b / c != 0`.

### 12.2. Giá trị nhỏ nhất chia cho `-1`

`Integer.MIN_VALUE` là `-2_147_483_648`. Giá trị đối của nó, `2_147_483_648`, nằm ngoài miền `int`. Theo đặc tả Java, `Integer.MIN_VALUE / -1` trả về lại `Integer.MIN_VALUE` (trường hợp tràn đặc biệt), không ném ngoại lệ.

```java
int min = Integer.MIN_VALUE;
System.out.println(min / -1);         // -2147483648
System.out.println((long) min / -1);   // 2147483648
```

Nếu yêu cầu phép chia chính xác `int` phải báo overflow, kiểm tra riêng ranh giới này trước khi chia; `Math.negateExact(Integer.MIN_VALUE)` cũng ném `ArithmeticException` khi cần phủ định có kiểm tra tràn. Dấu ngoặc không tự làm biểu thức rộng hơn.

### 12.3. Cast hẹp và phép toán ghép

`int result = (int) ((long) Integer.MAX_VALUE + 1);` có thể nhận `Integer.MIN_VALUE` vì phép cast `long` xuống `int` làm mất bit cao; phép cast không đồng nghĩa phép tính an toàn.

`short` và `byte` được nâng lên `int` trong phép cộng thông thường. Phép `s += 1` có quy tắc compound assignment bao gồm chuyển kiểu ngầm, khác `s = s + 1` nếu `s` là `short`. Đây là một nguồn **mất dữ liệu trung gian** dễ bị che khuất; đừng thay dấu ngoặc để “chữa” một phép chuyển kiểu không được kiểm soát.

## 13. Khi nào đổi dấu ngoặc làm đổi kết quả, khi nào không?

| Tình huống | Đổi dấu ngoặc có thể đổi gì? | Cách xử lý |
| --- | --- | --- |
| `(a / b) / c` ↔ `a / (b / c)` | Thương, lỗi chia cho 0 | Viết công thức mong muốn và kiểm tra mẫu trung gian |
| `(a - b) - c` ↔ `a - (b - c)` | Giá trị kết quả | Kiểm tra dấu và ý nghĩa của từng phép trừ |
| `(a + b) * c` ↔ `a + (b * c)` | Công thức khác nhau, có thể tràn ở vị trí khác | Chọn nhóm theo nghiệp vụ và chọn kiểu trước từng phép |
| `(a + b) + c` ↔ `a + (b + c)`, tất cả thuần `int` | Trung gian có thể khác; kết quả `int` cuối giống nhau theo modulo 2³² | Không khẳng định ngoặc luôn đổi kết quả |
| `(long) (a + b)` ↔ `(long) a + b` | **Kiểu của phép cộng** và khả năng tràn | Cast trước phép cộng nếu cần `long` |
| `(double) (a / b)` ↔ `(double) a / b` | `int` division ↔ `double` division | Chuyển kiểu trước phép chia nếu yêu cầu phần thập phân |
| `Math.addExact(Math.addExact(a,b),c)` ↔ nhóm checked còn lại | Vị trí phát hiện overflow | Thống nhất hợp đồng kiểm tra từng bước |
| Biểu thức có `++`/phương thức | Side effect, thứ tự quan sát được | Tách biến trung gian và side effect cho dễ đọc |

Một biểu thức “tương đương đại số” trong toán học không nhất thiết tương đương về **giá trị, ngoại lệ hoặc side effect** trong Java. Ngược lại, không nên phóng đại: một số phép cộng/nhân cùng kiểu nguyên thuần vẫn có tính kết hợp ngay cả khi overflow modulo, như đã giải thích.

## 14. Kỹ thuật viết biểu thức dễ đọc và an toàn

**Bước 1: viết công thức theo nghiệp vụ.** Ví dụ: “tính tổng 1..n” tương ứng `n * (n + 1) / 2`; “chia tổng hai lượng” là `total / (left + right)` nếu đó thực sự là mẫu.

**Bước 2: vẽ sơ đồ kết quả trung gian trên giấy.** Đặt kiểu và miền lên từng phép:

```text
             /
           /   \
          *     2 (int)
        /   \
 n (int)    +
           /   \
        n(int)  1(int)

n = 50_000
n + 1   -> int = 50_001
n * ... -> int overflow
... / 2 -> int không còn là tổng toán học
```

**Bước 3: chuyển kiểu trước phép có nguy cơ vượt miền.** Ví dụ `(long) n * (n + 1) / 2`. Nếu `long` vẫn có nguy cơ tràn, `Math.multiplyExact` hay `BigInteger` có thể phù hợp hơn, tùy hợp đồng.

**Bước 4: xác định quy tắc chia nguyên.** Nếu cần số nhóm làm tròn lên, không tự động dùng `(items + size - 1) / size` với mọi dữ liệu vì phép cộng `items + size - 1` có thể tràn `int`, và dữ liệu âm có ngữ nghĩa khác. Chọn công thức được chứng minh cho miền input; có thể dùng thương/dư hoặc kiểu rộng phù hợp.

**Bước 5: tách biểu thức dài và thêm tên trung gian.** Ví dụ:

```java
int n = 50_000;
long next = (long) n + 1;
long product = (long) n * next;
long result = product / 2;
```

Cách tách này giúp debug, ghi log và gắn kiểm tra ở từng bước. Với phép tính thuần, dấu ngoặc hữu ích cho khả năng đọc; với quy tắc nghiệp vụ phức tạp, **biến trung gian có tên** thường rõ hơn việc lồng nhiều cấp ngoặc.

**Bước 6: kiểm thử boundary.** Tối thiểu gồm `0`, `1`, số âm nếu được phép, số chia bằng 0, `Integer.MIN_VALUE`/`MAX_VALUE`, số khiến trung gian tràn, và trường hợp chia có dư.

## 15. Bài tập tự luyện có đáp án

### Bài 1 — Dự đoán kết quả

Với `int a = 18, b = 3, c = 3;`, kết quả của `a / b + c` và `a / (b + c)` là gì?

**Đáp án:** `9` và `3`. Biểu thức đầu được nhóm `(a/b)+c`; biểu thức sau lấy `b+c` làm mẫu.

### Bài 2 — Đổi ngoặc ở phép chia nguyên

Với `a = 20, b = 3, c = 2`, so sánh `(a/b)/c` và `a/(b/c)`.

**Đáp án:** `3` và `20`; `20/3` thành `6`, rồi `6/2` thành `3`, trong khi `3/2` thành `1`, rồi `20/1` thành `20`.

### Bài 3 — Cast đặt muộn

Tại sao `long total = (long) (Integer.MAX_VALUE + 1);` không cho `2147483648`?

**Đáp án:** phép cộng `int` đã tràn thành `-2147483648` trước khi cast. Sửa thành `(long) Integer.MAX_VALUE + 1` hoặc dùng `Math.addExact` nếu cần báo lỗi kiểu `int`.

### Bài 4 — Kết quả cuối vừa `int` nhưng vẫn sai

Cho `int n = 50_000;`. Giải thích lỗi của `n*(n+1)/2` và sửa.

**Đáp án:** tích `2_500_050_000` tràn `int` trước khi chia. `(long)n*(n+1)/2` cho `1_250_025_000`. Nếu kết quả phải là `int`, kiểm tra bằng `Math.toIntExact`.

### Bài 5 — Phân biệt promotion và ngoặc

`byte x=100, y=20; byte z=(x+y);` có hợp lệ không?

**Đáp án:** không; `x+y` có kiểu `int`. Dấu ngoặc không đổi kiểu. Dùng `int z=x+y;` hoặc cast hẹp có kiểm soát khi yêu cầu dữ liệu là `byte`.

### Bài 6 — Trường hợp phép cộng `int` thuần

Với `x=Integer.MAX_VALUE, y=1, z=-1`, có phải `(x+y)+z` khác `x+(y+z)` không?

**Đáp án:** không; hai kết quả cuối đều `Integer.MAX_VALUE`, dù trung gian khác nhau. Điều này phù hợp tính kết hợp của phép cộng `int` modulo; không áp dụng trực tiếp cho checked addition, chia, số thực hay side effect.

### Bài 7 — Chia cho 0 ẩn trong ngoặc

`int x=10, y=2, z=3;`, vì sao `x/(y/z)` gây lỗi lúc chạy dù `y` và `z` đều khác 0?

**Đáp án:** `y/z` chia nguyên bằng `0`; phép chia ngoài trở thành `10/0` và ném `ArithmeticException`.

### Bài 8 — Thứ tự gọi phương thức

Trong `left() + right() * 2`, phương thức nào chạy trước? Phép nhân được nhóm với những toán hạng nào?

**Đáp án:** `left()` được đánh giá trước `right()`; cấu trúc biểu thức là `left() + (right() * 2)`. Phân biệt thứ tự đánh giá với cách nhóm phép toán.

## 16. Checklist và tài liệu chính thức

- [ ] Tôi đọc đúng precedence, associativity và grouping khi thấy `( ... )`.
- [ ] Tôi phân biệt thứ tự **đánh giá toán hạng** với thứ tự **thực hiện phép tính đã nhóm**.
- [ ] Tôi dự đoán đúng kết quả chia nguyên, dấu của phần dư, và phát hiện mẫu trung gian bằng 0.
- [ ] Tôi không nghĩ `long result = intA * intB` sẽ tự bảo vệ khỏi `int` overflow.
- [ ] Tôi đặt cast trước phép toán cần kiểu rộng, kiểm tra kiểu của từng kết quả trung gian.
- [ ] Tôi biết trường hợp `(a+b)+c` và `a+(b+c)` với cùng kiểu số nguyên thuần không tự nhiên khác kết quả cuối; ngược lại chia và phép checked có khác biệt.
- [ ] Tôi xử lý được số âm, `Integer.MIN_VALUE / -1` và giá trị biên.
- [ ] Tôi tách side effect ra khỏi biểu thức số học phức tạp.
- [ ] Tôi đã chạy ít nhất hai chương trình đầy đủ và thử thêm trường hợp biên.

Đọc thêm theo Java SE 17:

- [JLS §15.7 — Evaluation Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7): thứ tự đánh giá và bảo toàn ngoặc/độ ưu tiên.
- [JLS §15.8.5 — Parenthesized Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.8.5): biểu thức trong ngoặc và kiểu của biểu thức.
- [JLS §15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17): nhân/chia/lấy dư.
- [JLS §15.18 — Additive Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18): cộng/trừ.
- [JLS §5.6 — Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6): promotion và kiểu phép tính.
- [Java SE 17 Math API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `addExact`, `multiplyExact`, `toIntExact`, `floorDiv`.

**Thông điệp thực hành:** dấu ngoặc chọn **cấu trúc phép tính**, nhưng độ chính xác/overflow được quyết định tại **mỗi phép trung gian**. Hãy ghi đúng công thức, kiểm tra kiểu và miền của từng bước, rồi kiểm chứng bằng dữ liệu biên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-008">floorDiv, floorMod và khác biệt với /, %</a>

<details>
<summary>Xem chi tiết</summary>

# `Math.floorDiv`, `Math.floorMod` và khác biệt với `/`, `%` trong Java

Với số nguyên dương, hai cách tính thương và phần dư thường cho cùng kết quả:

```java
System.out.println(7 / 3);                 // 2
System.out.println(7 % 3);                 // 1
System.out.println(Math.floorDiv(7, 3));   // 2
System.out.println(Math.floorMod(7, 3));   // 1
```

Nhưng khi có **số âm**, kết quả khác nhau. Java định nghĩa `/` trên số nguyên là phép **chia lấy thương làm tròn về 0** (*truncating division*), còn `Math.floorDiv` làm tròn thương **xuống âm vô cùng** (*floor division*). Tương ứng, `%` là phép **remainder** theo phép chia làm tròn về 0, còn `Math.floorMod` lấy phần dư theo phép chia floor.

Ví dụ quan trọng nhất:

```java
System.out.println(-7 / 3);                 // -2
System.out.println(-7 % 3);                 // -1
System.out.println(Math.floorDiv(-7, 3));   // -3
System.out.println(Math.floorMod(-7, 3));   // 2
```

Nếu bài toán xử lý chỉ số vòng, nhóm dữ liệu theo khoảng thời gian, tọa độ ô lưới hoặc số âm, chọn nhầm `%` và `floorMod` có thể gây sai logic dù chương trình biên dịch thành công.

## 1. Phân biệt “chia lấy thương” và “chia floor”

Cho hai số nguyên `x` và `y`, với `y != 0`:

- `x / y` lấy thương nguyên bằng cách **bỏ phần thập phân theo hướng về 0**.
- `Math.floorDiv(x, y)` lấy thương bằng cách **làm tròn xuống số nguyên nhỏ hơn hoặc bằng thương toán học**.

Ví dụ, thương toán học của `-7 / 3` là khoảng `-2.333…`. Làm tròn **về 0** được `-2`; làm tròn **xuống âm vô cùng** được `-3`.

| `x` | `y` | Thương toán học | `x / y` | `Math.floorDiv(x, y)` |
| ---: | ---: | ---: | ---: | ---: |
| 7 | 3 | 2,333… | 2 | 2 |
| −7 | 3 | −2,333… | −2 | −3 |
| 7 | −3 | −2,333… | −2 | −3 |
| −7 | −3 | 2,333… | 2 | 2 |
| −6 | 3 | −2 | −2 | −2 |
| −1 | 3 | −0,333… | 0 | −1 |

**Khi phép chia hết**, hai cách cho cùng thương. **Khi còn dư và hai toán hạng trái dấu**, `floorDiv` nhỏ hơn `/` đúng một đơn vị. Khi hai toán hạng cùng dấu, thương không âm nên làm tròn về 0 trùng với làm tròn xuống. Điều này áp dụng cho số nguyên trong miền biểu diễn thông thường, ngoại trừ trường hợp tràn `MIN_VALUE / -1` được nêu riêng ở phần 6.

Java thực hiện phép chia `int` và `long` hoàn toàn bằng số nguyên. Không cần, và thường không nên, chuyển sang `double` rồi gọi `Math.floor`: với `long` lớn hơn `2^53`, `double` có thể mất độ chính xác, khiến thương và ranh giới bucket sai.

## 2. Phân biệt `%` và `Math.floorMod`

Phần dư được xác định cùng với **cách làm tròn thương**.

Với phép chia thường của Java:

```text
q = x / y
r = x % y
x = q * y + r
```

Với phép chia floor:

```text
q = Math.floorDiv(x, y)
r = Math.floorMod(x, y)
x = q * y + r
```

Các đẳng thức trên mô tả quan hệ số học giữa thương và phần dư. Khi tự tính `q * y` bằng `int`/`long`, cần chú ý phép nhân có thể tràn trong trường hợp biên; đừng dùng phép tính lại này như một cách thay thế không kiểm tra cho `floorMod`. Trường hợp `MIN_VALUE / -1` còn có ngoại lệ tràn thương riêng.

### Bảng kết quả đầy đủ khi có dấu âm

| `x` | `y` | `x / y` | `x % y` | `floorDiv(x,y)` | `floorMod(x,y)` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 3 | 2 | 1 | 2 | 1 |
| −7 | 3 | −2 | −1 | −3 | 2 |
| 7 | −3 | −2 | 1 | −3 | −2 |
| −7 | −3 | 2 | −1 | 2 | −1 |
| −6 | 3 | −2 | 0 | −2 | 0 |
| −1 | 3 | 0 | −1 | −1 | 2 |

Nhận xét hai quy tắc về dấu:

1. `x % y` có **cùng dấu với số bị chia `x`**, hoặc bằng 0. Vì thế `-7 % 3` bằng `-1`, không phải `2`.
2. `Math.floorMod(x, y)` có **cùng dấu với số chia `y`**, hoặc bằng 0. Vì thế `Math.floorMod(-7, 3)` bằng `2`, nhưng `Math.floorMod(7, -3)` bằng `-2`.

Đặc biệt, khi **`y > 0`**, giá trị `Math.floorMod(x, y)` luôn nằm trong khoảng **`0 <= r < y`**, ngay cả khi `x` âm. Đây là đặc tính thường cần cho index vòng hoặc phân nhóm theo modulo không âm.

Khi **`y < 0`**, giá trị `floorMod` thỏa **`y < r <= 0`**. Không nên gọi `floorMod` là “luôn không âm” nếu chưa bảo đảm **số chia dương**.

### Kiểm tra công thức với số âm

Với `x = -7`, `y = 3`:

- Phép chia thường: `q = -2` và `r = -1`, nên `-7 = (-2) * 3 + (-1)`.
- Phép chia floor: `q = -3` và `r = 2`, nên `-7 = (-3) * 3 + 2`.

Với `x = 7`, `y = -3`:

- Phép chia thường: `q = -2` và `r = 1`, nên `7 = (-2) * (-3) + 1`.
- Phép chia floor: `q = -3` và `r = -2`, nên `7 = (-3) * (-3) + (-2)`.

Cả hai mô hình nhất quán; điểm khác nhau là **quy tắc chọn thương** và do đó là **quy tắc chọn phần dư**.

## 3. Cơ chế tạo ra khác biệt

Với `x`, `y` là các số nguyên khác trường hợp chia cho 0:

- Nếu phép chia **hết**, cả hai mô hình có cùng thương, phần dư bằng 0.
- Nếu phép chia **còn dư và hai toán hạng cùng dấu**, thương toán học dương nên cắt về 0 cũng là làm tròn xuống; hai mô hình trùng nhau.
- Nếu phép chia **còn dư và hai toán hạng trái dấu**, thương toán học âm, có phần lẻ. `/` làm tròn lên về 0, còn `floorDiv` lấy giá trị nhỏ hơn một đơn vị; `floorMod` điều chỉnh phần dư tương ứng theo số chia.

Đặt `q0 = x / y` và `r0 = x % y`. Khi `r0 != 0` và `x`, `y` trái dấu, có thể mô tả về mặt toán học:

```text
qFloor = q0 - 1
rFloor = r0 + y
```

Nếu không thỏa điều kiện đó, `qFloor = q0` và `rFloor = r0`. Đây là lời giải thích cơ chế, không phải lời khuyên tự cài đặt phép chia floor trong mọi bối cảnh: Java có sẵn `Math.floorDiv`/`Math.floorMod`, rõ ràng hơn và xử lý những góc cạnh của số nguyên.

**Lưu ý về phép chia:** với số nguyên, `-1 / 3` là `0` chứ không phải số `-0.333…`. Java không giữ số lẻ trong kết quả `int`. Nếu viết `-1.0 / 3`, phép toán trở thành chấm động và có kết quả gần `-0.333…`; `Math.floorDiv` chỉ dành cho kiểu nguyên, không dùng với `double`.

## 4. API `Math.floorDiv` và `Math.floorMod`

Các overload thông dụng của `java.lang.Math` gồm:

```java
int q1 = Math.floorDiv(-7, 3);
long q2 = Math.floorDiv(-7L, 3L);
long q3 = Math.floorDiv(-7L, 3);

int r1 = Math.floorMod(-7, 3);
long r2 = Math.floorMod(-7L, 3L);
int r3 = Math.floorMod(-7L, 3);
```

Kết quả của `floorDiv(long, int)` là `long`, còn kết quả của `floorMod(long, int)` là `int` do độ lớn phần dư luôn nhỏ hơn độ lớn số chia. Các overload `int,int` cho `int` và `long,long` cho `long`.

**Không có overload tương đương cho `float` và `double`.** Nếu muốn làm việc với số thực, cần quy định bài toán làm tròn/phần dư chấm động riêng (ví dụ `Math.floor` hoặc `Math.IEEEremainder` có quy tắc khác). Không được áp dụng máy móc các luật số nguyên cho số thực có sai số biểu diễn.

Các phương thức `floorDiv` và `floorMod` có từ Java 8, nên dùng được trên Java 17+. Chúng không sửa biến đầu vào: trả về kết quả mới, giống các toán tử tính toán.

### Số chia bằng 0

Cả `/`, `%`, `Math.floorDiv` và `Math.floorMod` trên các kiểu nguyên đều ném `ArithmeticException` nếu **số chia bằng 0**:

```java
int size = 0;
// int invalid = 10 % size;            // Ném ArithmeticException khi chạy.
// int alsoInvalid = Math.floorMod(10, size); // Cũng ném ArithmeticException.
```

Không gọi modulo với kích thước collection rỗng hoặc số bucket bằng 0. Nếu giá trị `y` đến từ bên ngoài, kiểm tra `y != 0` trước, và với bài toán yêu cầu remainder không âm, kiểm tra mạnh hơn là `y > 0`.

## 5. Ứng dụng 1: chỉ số vòng và modulo không âm

Giả sử ta muốn đi lùi một bước trong vòng tròn 5 phần tử. Index hợp lệ là 0, 1, 2, 3, 4. Đi lùi từ vị trí 0 phải trở về 4.

```java
int capacity = 5;
int current = 0;

int wrong = (current - 1) % capacity;            // -1: không phải index hợp lệ.
int correct = Math.floorMod(current - 1, capacity); // 4.
```

Lý do: `%` giữ dấu của số bị chia `-1`. Còn `Math.floorMod(-1, 5)` chuẩn hóa về tập `[0, 4]`.

Với bộ đếm có thể âm hoặc vị trí logic cần quy về chỉ số vòng, điều kiện cần là **số chia dương**:

```java
static int wrapIndex(long logicalIndex, int capacity) {
    if (capacity <= 0) {
        throw new IllegalArgumentException("capacity must be positive");
    }
    return Math.floorMod(logicalIndex, capacity);
}
```

Đây là đoạn phương thức minh họa, không phải một chương trình độc lập. Overload `Math.floorMod(long, int)` trả `int`, nên hữu ích khi chỉ số logic dùng `long` nhưng mảng/buffer có `int capacity`.

Với `capacity = 5`, `wrapIndex(-1, 5)` trả 4; `wrapIndex(5, 5)` trả 0; `wrapIndex(-6, 5)` trả 4. Chỉ số vòng là một **ánh xạ theo modulo**. Nó không tự ngăn race condition hay kiểm soát chuyện ghi đè trong ring buffer đa luồng.

**Sai lầm khác:** dùng `Math.abs(index % capacity)` để tránh số âm. Với `index = -1`, `capacity = 5`, cách này cho 1, trong khi index vòng đúng là 4. Hơn nữa `Math.abs(Integer.MIN_VALUE)` vẫn âm do tràn. Hãy dùng `floorMod` khi cần dư không âm.

## 6. Ứng dụng 2: phân nhóm theo khoảng, bucket và thời gian âm

Giả sử một dòng thời gian được đo bằng số nguyên giây so với mốc 0. Mỗi bucket dài 60 giây và cần sắp xếp các thời điểm **trước** mốc 0.

Với thời điểm `t = -1`:

```java
long t = -1L;
long bucketWrong = t / 60;                  // 0: làm tròn về 0.
long bucketCorrect = Math.floorDiv(t, 60);  // -1: bucket [-60, -1].
int secondsIntoBucket = Math.floorMod(t, 60); // 59.
```

Theo mô hình bucket floor:

```text
t = bucketIndex * 60 + offset
-1 = (-1) * 60 + 59
```

Tức thời điểm −1 nằm trong bucket bắt đầu tại −60, offset 59 giây. Nếu dùng `t / 60`, thời điểm −1 bị xếp vào bucket 0, không đúng với các khoảng cố định dạng `[k*60, (k+1)*60)`.

Một số ví dụ với bucket dài 60:

| `t` (giây) | `t / 60` | `floorDiv(t,60)` | `t % 60` | `floorMod(t,60)` |
| ---: | ---: | ---: | ---: | ---: |
| 61 | 1 | 1 | 1 | 1 |
| 60 | 1 | 1 | 0 | 0 |
| 0 | 0 | 0 | 0 | 0 |
| −1 | 0 | −1 | −1 | 59 |
| −60 | −1 | −1 | 0 | 0 |
| −61 | −1 | −2 | −1 | 59 |

Quy tắc này phù hợp với việc chia tọa độ âm thành ô lưới, gom số nguyên vào dải độ rộng cố định, hay chia timestamp tương đối thành bucket (khi miền dữ liệu và hệ quy chiếu đã được xác định). Với dữ liệu thời gian thực, còn phải chú ý đơn vị milliseconds/nanoseconds, múi giờ, lịch và ranh giới thời gian; `floorDiv` chỉ giải quyết **phép chia số nguyên**, không giải quyết toàn bộ nghiệp vụ thời gian.

### Tọa độ ô lưới có thể âm

Giả sử chiều rộng mỗi ô là 10 đơn vị. Tọa độ `x = -1` phải nằm trong ô từ −10 đến −1:

```java
int cellWidth = 10;
int coordinate = -1;
int cellNumber = Math.floorDiv(coordinate, cellWidth); // -1
int offset = Math.floorMod(coordinate, cellWidth);     // 9
```

Nếu dùng `coordinate / cellWidth`, ô sẽ được xác định là 0, tạo sai lệch tại các tọa độ âm. Đây là một nguyên nhân phổ biến gây lỗi khi chia không gian có gốc tọa độ 0.

## 7. Góc cạnh quan trọng: tràn số nguyên nhỏ nhất chia cho −1

Java có một trường hợp đặc biệt: `Integer.MIN_VALUE` bằng `-2147483648`, trong khi số dương `2147483648` **không vừa kiểu `int`**. Vì vậy:

```java
int min = Integer.MIN_VALUE;
System.out.println(min / -1);                 // -2147483648
System.out.println(Math.floorDiv(min, -1));   // -2147483648
System.out.println(min % -1);                 // 0
System.out.println(Math.floorMod(min, -1));   // 0
```

Về toán học, thương đúng phải là `2147483648`. Java trả `Integer.MIN_VALUE` vì phép chia số nguyên xảy ra tình huống **overflow**; không ném ngoại lệ cho trường hợp này. `Math.floorDiv` cũng không tự phát hiện overflow đó. Tương tự, `Long.MIN_VALUE / -1L` và `Math.floorDiv(Long.MIN_VALUE, -1L)` trả `Long.MIN_VALUE`.

Điều này **không phủ nhận quy tắc floor** trong miền thông thường. Nó nhắc rằng ngay cả `floorDiv` vẫn trả một giá trị kiểu `int`/`long` hữu hạn, nên phép toán có thể mất kết quả toán học khi vượt miền biểu diễn.

Nếu nghiệp vụ phải tính thương chính xác trong trường hợp này, có thể mở rộng **trước phép chia**:

```java
long exactIntQuotient = (long) Integer.MIN_VALUE / -1;
// exactIntQuotient = 2147483648L.
```

Nếu toán hạng `long` có khả năng gặp `Long.MIN_VALUE / -1L` và cần kết quả nguyên chính xác lớn hơn `long`, cân nhắc `BigInteger` (với kiểm tra chia 0) hoặc kiểm tra riêng tình huống tràn.

Các cặp quan trọng khác cần nhớ:

- Với `x % y` và `floorMod(x, y)`, phép chia **hết** cho `y` luôn có dư 0 (khi `y != 0`).
- Với số chia dương, `floorMod(Integer.MIN_VALUE, 3)` vẫn là một giá trị trong `0..2`; không cần lấy trị tuyệt đối của số âm lớn.
- Nếu `y = Integer.MIN_VALUE`, số chia là âm; kết quả `floorMod` có thể âm. Đừng gọi `Math.abs(y)` để tự “biến thành modulus dương”, vì `Math.abs(Integer.MIN_VALUE)` bị tràn.

## 8. Tránh nhầm với “modulo” và phép toán của các API khác

Trong toán học, “modulo dương” thường được dùng để lấy đại diện của một lớp đồng dư trong khoảng `0..m-1` với `m > 0`. Trong Java, `%` là **remainder** theo phép chia cắt về 0; nó có thể âm. `Math.floorMod(x, m)` với `m > 0` phù hợp hơn với đại diện modulo không âm.

Một số tài liệu gọi `a % b` là “modulo” một cách thông tục. Khi đọc, hãy xem ví dụ với số âm và hợp đồng API trước khi suy ra phép toán nào được dùng.

### Liên hệ với `BigInteger`

`BigInteger.divide`/`remainder` áp dụng quy tắc lấy thương cắt về 0 và phần dư phù hợp. `BigInteger.mod(m)` trả kết quả không âm nhưng yêu cầu `m` **dương**; nếu modulus không dương, nó ném `ArithmeticException`. Nó không hoàn toàn thay thế `Math.floorMod` cho trường hợp số chia âm.

```java
// Đoạn minh họa trong nơi đã import java.math.BigInteger:
BigInteger x = BigInteger.valueOf(-7);
System.out.println(x.remainder(BigInteger.valueOf(3))); // -1
System.out.println(x.mod(BigInteger.valueOf(3)));       // 2
```

Nếu cần miền số nguyên lớn hơn `long`, chọn `BigInteger`; đừng chuyển một số `long` rất lớn qua `double` để thử làm tròn xuống.

## 9. Ví dụ Java 17+ chạy độc lập

Tạo tệp `FloorArithmeticDemo.java`:

```java
public class FloorArithmeticDemo {
    public static void main(String[] args) {
        int[][] pairs = {
                {7, 3}, {-7, 3}, {7, -3},
                {-7, -3}, {-6, 3}, {-1, 3}
        };

        for (int[] pair : pairs) {
            int x = pair[0];
            int y = pair[1];
            System.out.println(
                    "x=" + x + ", y=" + y
                            + " | /=" + (x / y)
                            + ", %=" + (x % y)
                            + ", floorDiv=" + Math.floorDiv(x, y)
                            + ", floorMod=" + Math.floorMod(x, y));
        }

        System.out.println("Circular index (-1, 5): " + Math.floorMod(-1, 5));
        System.out.println(
                "Bucket for -1 sec: q=" + Math.floorDiv(-1, 60)
                        + ", offset=" + Math.floorMod(-1, 60));

        int min = Integer.MIN_VALUE;
        System.out.println("MIN / -1: " + (min / -1));
        System.out.println("MIN floorDiv -1: " + Math.floorDiv(min, -1));
        System.out.println("MIN floorMod -1: " + Math.floorMod(min, -1));

        int modFromLong = Math.floorMod(-7L, 3);
        System.out.println("Long dividend, int divisor: " + modFromLong);

        try {
            Math.floorMod(10, 0);
        } catch (ArithmeticException ex) {
            System.out.println("Division by zero: ArithmeticException");
        }
    }
}
```

Biên dịch, chạy từ thư mục chứa tệp:

```bash
javac FloorArithmeticDemo.java
java FloorArithmeticDemo
```

Stdout:

```text
x=7, y=3 | /=2, %=1, floorDiv=2, floorMod=1
x=-7, y=3 | /=-2, %=-1, floorDiv=-3, floorMod=2
x=7, y=-3 | /=-2, %=1, floorDiv=-3, floorMod=-2
x=-7, y=-3 | /=2, %=-1, floorDiv=2, floorMod=-1
x=-6, y=3 | /=-2, %=0, floorDiv=-2, floorMod=0
x=-1, y=3 | /=0, %=-1, floorDiv=-1, floorMod=2
Circular index (-1, 5): 4
Bucket for -1 sec: q=-1, offset=59
MIN / -1: -2147483648
MIN floorDiv -1: -2147483648
MIN floorMod -1: 0
Long dividend, int divisor: 2
Division by zero: ArithmeticException
```

Chương trình thể hiện bốn kết hợp dấu của hai toán hạng, số chia hết, số bị chia gần 0, cách đưa chỉ số âm vào vòng và phân bucket âm. Nó cũng kiểm chứng overflow ở `Integer.MIN_VALUE / -1` và ngoại lệ khi số chia bằng 0.

## 10. Quy trình chọn toán tử cho bài toán thực tế

| Nhu cầu | Ưu tiên | Lý do |
| --- | --- | --- |
| Chia số nguyên và muốn thương **cắt về 0** | `/` | Đúng ngữ nghĩa phép chia nguyên mặc định Java |
| Phần dư theo phép chia cắt về 0; dấu theo số bị chia | `%` | Dùng khi hợp đồng yêu cầu remainder Java |
| Chia vào bucket/ô lưới số nguyên, có thể chứa giá trị âm | `Math.floorDiv` | Biên nhóm nhất quán với làm tròn xuống |
| Đưa index âm vào khoảng `0..size−1` với `size>0` | `Math.floorMod(index, size)` | Dư không âm khi modulus dương |
| Lấy offset không âm trong bucket độ rộng dương | `Math.floorMod(value, width)` | Offset thuộc `[0,width)` |
| Phép toán có thể vượt miền `int`/`long` | Kiểm tra miền, mở rộng kiểu trước, `BigInteger` nếu cần | `floorDiv` cũng chịu giới hạn miền kiểu trả về |
| Có số chia bằng 0 hoặc size không dương | Kiểm tra đầu vào và đưa ra lỗi rõ ràng | Tránh `ArithmeticException` bất ngờ và kết quả modulo không hợp đồng |

### Những lỗi thường gặp

1. **Tưởng `%` luôn cho số không âm.** `-7 % 3` là `-1`. Dùng `floorMod` với modulus dương khi cần `0..m-1`.
2. **Tưởng `floorMod` luôn không âm với mọi số chia.** `Math.floorMod(7, -3)` bằng `-2`. Kiểm tra `m > 0` nếu cần index.
3. **Tính `Math.abs(x % m)` để sửa chỉ số âm.** Giá trị thường sai hướng và `Math.abs(MIN_VALUE)` còn có thể tràn.
4. **Dùng `/` để phân bucket tọa độ/thời gian âm.** `-1 / 60` bằng 0, trong khi `floorDiv(-1,60)` bằng −1.
5. **Tự tính floor bằng `Math.floor((double)x / y)` cho mọi `long`.** Chuyển sang `double` có thể mất độ chính xác, đặc biệt với số rất lớn.
6. **Quên trường hợp `MIN_VALUE / -1`.** `floorDiv` không tự phát hiện tràn số nguyên này; nếu cần thương toán học chính xác, mở rộng kiểu hoặc dùng `BigInteger`.
7. **Dùng modulo với size bằng 0.** Cả `%` lẫn `floorMod` sẽ ném `ArithmeticException`. Đặt điều kiện size dương trước.

### Tự kiểm tra

1. Tính `-8 / 3`, `-8 % 3`, `Math.floorDiv(-8,3)` và `Math.floorMod(-8,3)`. Vì sao hai thương và hai phần dư khác nhau?
2. Với `Math.floorMod(8,-3)`, kết quả có không âm không? Đặc tính nào của số chia quyết định dấu phần dư?
3. Một ring buffer có 7 phần tử; vị trí logic là −2. `-2 % 7` và `Math.floorMod(-2,7)` khác nhau thế nào?
4. Timestamp có giá trị −61 giây, bucket width 60. Vì sao bucket phải là −2 với phương pháp floor?
5. `Math.floorDiv(Integer.MIN_VALUE,-1)` có trả thương toán học chính xác không? Làm sao xử lý khi bài toán cần kết quả 2147483648?
6. Hai toán tử `/`, `%` và hai phương thức floor có ném ngoại lệ chia cho 0 trên kiểu nguyên không?

**Ghi nhớ:** `/` và `%` là phép chia/phần dư số nguyên **làm tròn thương về 0**. `Math.floorDiv` và `Math.floorMod` dùng quy tắc **làm tròn thương xuống**. Với modulus dương, `floorMod` giúp đưa mọi số nguyên, kể cả số âm, vào khoảng phần dư không âm; với divisor âm, phần dư floor có thể âm. Chọn cách chia theo ngữ nghĩa bài toán và kiểm tra chia 0, miền kiểu số nguyên, cùng những trường hợp tràn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-009">Trường hợp MIN_VALUE chia cho -1</a>

<details>
<summary>Xem chi tiết</summary>

# Trường hợp `MIN_VALUE` chia cho `-1` trong Java

## 1. Kết quả gây bất ngờ

Trong toán học, nếu `a` là số nguyên âm thì `a / -1` là số đối dương của `a`. Nhưng trong Java, **`Integer.MIN_VALUE / -1` vẫn bằng `Integer.MIN_VALUE`**, và **`Long.MIN_VALUE / -1L` vẫn bằng `Long.MIN_VALUE`**.

Đó là một trường hợp đặc biệt của **phép chia số nguyên có dấu**, được Java quy định rõ, không phải hành vi ngẫu nhiên của máy hoặc lỗi do JVM.

~~~java
int intResult = Integer.MIN_VALUE / -1;   // -2147483648
long longResult = Long.MIN_VALUE / -1L;  // -9223372036854775808
~~~

Đoạn trên là mã minh họa; phía dưới có các chương trình đầy đủ để biên dịch và chạy.

**Vì sao?** Giá trị số nguyên nhỏ nhất có độ lớn tuyệt đối lớn hơn số nguyên dương lớn nhất cùng kiểu một đơn vị:

| Kiểu | `MIN_VALUE` | `MAX_VALUE` | Giá trị toán học `MIN_VALUE / -1` |
|---|---:|---:|---:|
| `int` (32 bit) | −2.147.483.648 | 2.147.483.647 | **2.147.483.648** — vượt miền `int` |
| `long` (64 bit) | −9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 | **9.223.372.036.854.775.808** — vượt miền `long` |

Kết quả dương chính xác không thể chứa trong kiểu số nguyên ban đầu. Phép chia thông thường `/` của Java **không báo overflow**: với cặp toán hạng duy nhất gây tràn thương có dấu là `MIN_VALUE` và `-1`, kết quả được quy định bằng chính `MIN_VALUE`.

Điểm phân biệt cốt lõi:

- **Chia cho `0`:** phép chia nguyên phát sinh `ArithmeticException`.
- **Chia `MIN_VALUE` cho `-1`:** **không** phát sinh `ArithmeticException`; kết quả là `MIN_VALUE`.
- **Chia các giá trị nguyên còn lại cho `-1`:** kết quả đúng bằng số đối của giá trị bị chia, nằm trong miền kiểu đích.

## 2. Cơ chế: miền không đối xứng và phép chia nguyên

### 2.1. Tại sao miền số âm rộng hơn phía dương?

Java sử dụng biểu diễn số nguyên có dấu theo dạng **bù hai (*two's complement*)** với độ rộng cố định. Với `int` 32 bit, giá trị nhỏ nhất là `-2^31` và lớn nhất là `2^31 - 1`. Với `long` 64 bit, giá trị nhỏ nhất là `-2^63` và lớn nhất là `2^63 - 1`.

Vì vậy, kiểu `int` có thể lưu `-2147483648` nhưng **không thể** lưu `+2147483648`; kiểu `long` có thể lưu `-9223372036854775808` nhưng **không thể** lưu `+9223372036854775808`. Khi chia giá trị nhỏ nhất cho `-1`, thương toán học thuộc phía dương vượt quá `MAX_VALUE` đúng một đơn vị.

Ở mức bit, giá trị `Integer.MIN_VALUE` có mẫu `0x80000000`, còn `Long.MIN_VALUE` có mẫu `0x8000000000000000L`. Các mẫu này không đổi khi thực hiện phép lấy số đối trong độ rộng tương ứng, do số đối toán học không thể biểu diễn. Java quy định riêng cho phép chia tương ứng để bảo đảm kết quả nhất quán.

### 2.2. Quy tắc chia nguyên thông thường

Với toán hạng thuộc kiểu nguyên, toán tử `/`:

1. Tính thương nguyên và **loại phần lẻ về phía 0** (*truncate toward zero*), không tự làm tròn xuống theo nghĩa toán học.
2. Nếu số chia bằng `0`, ném `ArithmeticException`.
3. Nếu số bị chia là `Integer.MIN_VALUE` và số chia là `-1` trong phép toán kiểu `int`, trả `Integer.MIN_VALUE`. Trường hợp `long` tương ứng trả `Long.MIN_VALUE`.
4. Các phép chia nguyên khác trả kết quả nằm trong miền của kiểu kết quả và không ném lỗi chỉ vì kết quả có dấu âm.

Ví dụ:

| Phép tính kiểu `int` | Kết quả `/` | Giải thích |
|---|---:|---|
| `7 / 3` | `2` | Bỏ phần dư |
| `-7 / 3` | `-2` | Làm tròn về 0 |
| `7 / -3` | `-2` | Làm tròn về 0 |
| `-7 / -3` | `2` | Làm tròn về 0 |
| `Integer.MIN_VALUE / 1` | `Integer.MIN_VALUE` | Vẫn nằm trong miền |
| `Integer.MIN_VALUE / -1` | `Integer.MIN_VALUE` | **Tràn thương đặc biệt, không ném lỗi** |

**Lưu ý về kiểu:** Khi chia hai biến `byte`, `short` hoặc `char`, Java nâng toán hạng lên `int` theo *binary numeric promotion*. Các giá trị kiểu nhỏ được mở rộng rồi thực hiện chia trong `int`, nên **trường hợp tràn `MIN_VALUE / -1` chỉ đáng xét đối với phép chia mang kiểu tính toán `int` hoặc `long`**. Một biến `short` bằng `Short.MIN_VALUE` chia cho `-1` có thương `32768` hợp lệ trong `int`; gán kết quả ngược về `short` sẽ cần kiểm tra/ép kiểu riêng.

### 2.3. Không cần dấu âm đứng trong literal

`-1` trong biểu thức Java là toán tử âm một ngôi áp dụng lên literal `1`. Còn `Integer.MIN_VALUE` là hằng số `int` bằng `-2147483648`. Kết quả phép chia được xác định dựa trên **giá trị và kiểu toán hạng**, bất kể giá trị `-1` được tạo từ literal, biến, hoặc phương thức:

~~~java
int min = Integer.MIN_VALUE;
int divisor = -1;
int quotient = min / divisor; // Integer.MIN_VALUE
~~~

Đây là một đoạn minh họa; tình huống không thay đổi khi thay `divisor` bằng tham số truyền từ ngoài vào. Chính vì kết quả không ném exception, một phép chia như vậy có thể đi qua các nhánh xử lý thông thường mà ứng dụng không nhận ra overflow.

## 3. Chương trình Java 17+ minh họa trực tiếp

Tạo tệp `MinValueDivisionDemo.java`:

~~~java
public class MinValueDivisionDemo {
    public static void main(String[] args) {
        int minInt = Integer.MIN_VALUE;
        long minLong = Long.MIN_VALUE;

        System.out.println("intMin=" + minInt);
        System.out.println("intDiv=" + (minInt / -1));
        System.out.println("longMin=" + minLong);
        System.out.println("longDiv=" + (minLong / -1L));
        System.out.println("intRem=" + (minInt % -1));
        System.out.println("longRem=" + (minLong % -1L));
        System.out.println("normal=" + (-7 / 3));
        System.out.println("exactPositive=" + ((long) minInt / -1L));
    }
}
~~~

Dùng JDK 17 trở lên, chạy trong thư mục chứa tệp:

~~~bash
javac MinValueDivisionDemo.java
java MinValueDivisionDemo
~~~

Đầu ra:

~~~text
intMin=-2147483648
intDiv=-2147483648
longMin=-9223372036854775808
longDiv=-9223372036854775808
intRem=0
longRem=0
normal=-2
exactPositive=2147483648
~~~

Các dòng `intDiv` và `longDiv` thể hiện chính xác trường hợp ngoại lệ về **giá trị kết quả**. Hai dòng `intRem` và `longRem` cho biết phần dư là 0; một phần dư bằng 0 **không chứng minh thương không bị tràn**. Dòng `exactPositive` cho thấy mở rộng `int` sang `long` **trước khi chia** sẽ nhận thương chính xác 2.147.483.648.

## 4. Phần dư `%` và quan hệ với phép chia

Trong Java, phép chia lấy dư nguyên `a % b` có cùng trường hợp chia cho 0 gây `ArithmeticException`, nhưng:

~~~java
Integer.MIN_VALUE % -1  // 0
Long.MIN_VALUE % -1L    // 0L
~~~

Đó là kết quả **được quy định**, không phải dấu hiệu cho thấy phép chia an toàn. Với các phép chia không phải chia cho 0, số dư thỏa quan hệ:

`a == (a / b) * b + (a % b)`

Khi `a = Integer.MIN_VALUE` và `b = -1`, biểu thức này vẫn đúng theo **số học `int` có tràn**: thương mang giá trị `Integer.MIN_VALUE`; nhân thương với `-1` cũng tràn trở lại `Integer.MIN_VALUE`; cộng phần dư 0 không đổi. Không thể dùng quan hệ này mà tính bằng cùng kiểu `int` để phát hiện overflow.

Ngoài ra, dấu của số dư `%` trong Java đi theo số bị chia (*dividend*) hoặc bằng 0. Ví dụ `-7 % 3` bằng `-1`, đi cùng `-7 / 3` bằng `-2`. Đây là phần của phép chia làm tròn về 0, khác một số định nghĩa phép modulo trong toán học.

### Phân biệt `Math.floorDiv` và `/`

`Math.floorDiv(a, b)` làm tròn thương **về âm vô cùng** (*floor*), nên `Math.floorDiv(-7, 3)` bằng `-3` trong khi `-7 / 3` bằng `-2`. Nhưng `Math.floorDiv` **không loại bỏ trường hợp tràn**:

~~~java
Math.floorDiv(Integer.MIN_VALUE, -1) // Integer.MIN_VALUE
Math.floorMod(Integer.MIN_VALUE, -1) // 0
~~~

Vì vậy, dùng `Math.floorDiv` để thay đổi cách làm tròn là hợp lý khi bài toán cần quy tắc đó, **nhưng không được coi nó là phép chia có kiểm tra overflow**.

## 5. Vì sao đổi kiểu *sau* khi chia không giúp được?

Sự khác biệt giữa hai biểu thức sau là **thời điểm mở rộng kiểu**:

~~~java
int min = Integer.MIN_VALUE;

long tooLate = (long) (min / -1);
long safeForIntMin = ((long) min) / -1;
~~~

- `tooLate`: `min / -1` chạy trong kiểu `int` và trả `-2147483648`; sau đó cast sang `long` chỉ giữ lại kết quả đã tràn.
- `safeForIntMin`: `min` được chuyển thành `long` **trước phép chia**; `2147483648` nằm trong miền `long`, nên phép chia trả thương dương chính xác.

### Trường hợp `Long.MIN_VALUE`

Không tồn tại kiểu nguyên thủy Java nào rộng hơn `long`. Vì vậy, ép kiểu trong họ integer primitive không thể biểu diễn chính xác `Long.MIN_VALUE / -1L`. Khi thật sự cần thương toán học dương 9.223.372.036.854.775.808, dùng `BigInteger`:

~~~java
BigInteger exact = BigInteger.valueOf(Long.MIN_VALUE)
        .divide(BigInteger.valueOf(-1));
~~~

Đây là **đoạn minh họa**, giả sử đã import `java.math.BigInteger`. Trong `BigInteger`, kết quả không giới hạn ở 64 bit cố định (nhưng vẫn bị giới hạn bởi bộ nhớ và tài nguyên). Nếu hệ thống cuối cùng bắt buộc trả về `long`, phải thông báo lỗi hoặc có chính sách riêng; không thể nhét thương đó vào `long` mà giữ chính xác.

## 6. Phát hiện overflow thay vì nhận kết quả bị cuốn vòng

Khi API có yêu cầu phép chia **phải chính xác trong kiểu `int`**, hãy kiểm tra hai điều kiện trước khi dùng `/`:

1. Số chia `divisor == 0`: không có phép chia nguyên hợp lệ.
2. `dividend == Integer.MIN_VALUE && divisor == -1`: thương toán học không nằm trong miền `int`.

Với `long`, thay `Integer.MIN_VALUE` bằng `Long.MIN_VALUE` và dùng toán hạng `long`. Kiểm tra này bao phủ trường hợp thương chia nguyên vượt miền, vì chia một số nguyên cho trị tuyệt đối của số chia từ 1 trở lên không làm tăng trị tuyệt đối của số bị chia, ngoại trừ phép đổi dấu đối với `MIN_VALUE / -1`.

### Ví dụ độc lập: phép chia có kiểm tra

Tạo tệp `CheckedDivisionDemo.java`:

~~~java
public class CheckedDivisionDemo {
    public static int divideInt(int dividend, int divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            throw new ArithmeticException("int quotient overflow");
        }
        return dividend / divisor;
    }

    public static long divideLong(long dividend, long divisor) {
        if (divisor == 0L) {
            throw new ArithmeticException("division by zero");
        }
        if (dividend == Long.MIN_VALUE && divisor == -1L) {
            throw new ArithmeticException("long quotient overflow");
        }
        return dividend / divisor;
    }

    public static void main(String[] args) {
        System.out.println("12 / -3=" + divideInt(12, -3));
        System.out.println("-7 / 3=" + divideInt(-7, 3));

        try {
            divideInt(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException ex) {
            System.out.println("intOverflow=" + ex.getMessage());
        }

        try {
            divideLong(Long.MIN_VALUE, -1L);
        } catch (ArithmeticException ex) {
            System.out.println("longOverflow=" + ex.getMessage());
        }

        try {
            divideInt(10, 0);
        } catch (ArithmeticException ex) {
            System.out.println("zeroDivisor=" + ex.getMessage());
        }
    }
}
~~~

~~~bash
javac CheckedDivisionDemo.java
java CheckedDivisionDemo
~~~

Đầu ra:

~~~text
12 / -3=-4
-7 / 3=-2
intOverflow=int quotient overflow
longOverflow=long quotient overflow
zeroDivisor=division by zero
~~~

Phép kiểm tra tập trung ở **ranh giới thao tác số học**, giúp người gọi phân biệt phép chia cho 0 với overflow. Các thông điệp lỗi được đặt tường minh trong chương trình để đầu ra ổn định; không nên dựa vào chuỗi `ArithmeticException` mặc định của một JVM cụ thể để làm hợp đồng dữ liệu.

**Đừng dùng `Math.abs(divisor)` như cách kiểm tra duy nhất**: `Math.abs(Integer.MIN_VALUE)` cũng nhận kết quả âm `Integer.MIN_VALUE`, vì trị tuyệt đối dương vượt miền `int`. Tương tự, `Math.negateExact(Integer.MIN_VALUE)` có thể phát hiện việc phủ định vượt miền, nhưng gọi phủ định để thay cho phép chia với mọi loại số chia là không cần thiết; hãy kiểm tra chính xác điều kiện phép chia.

## 7. Khi cần phép chia chính xác bằng `BigInteger`

Nếu yêu cầu giữ lại thương toán học của phép chia `MIN_VALUE / -1`, chọn một kiểu đủ rộng **trước phép chia**:

- Với `int`: sử dụng `long` là đủ cho thương dương 2.147.483.648.
- Với `long`: sử dụng `BigInteger` để có thương dương 9.223.372.036.854.775.808.

Tạo tệp `ExactMinDivisionDemo.java`:

~~~java
import java.math.BigInteger;

public class ExactMinDivisionDemo {
    public static void main(String[] args) {
        int minInt = Integer.MIN_VALUE;
        long wideQuotient = ((long) minInt) / -1L;

        BigInteger minLong = BigInteger.valueOf(Long.MIN_VALUE);
        BigInteger exactQuotient =
                minLong.divide(BigInteger.valueOf(-1));

        System.out.println("wideIntQuotient=" + wideQuotient);
        System.out.println("exactLongQuotient=" + exactQuotient);

        try {
            System.out.println("asLong=" + exactQuotient.longValueExact());
        } catch (ArithmeticException ex) {
            System.out.println("asLong=out of range");
        }

        System.out.println("bigRemainder="
                + minLong.remainder(BigInteger.valueOf(-1)));
    }
}
~~~

~~~bash
javac ExactMinDivisionDemo.java
java ExactMinDivisionDemo
~~~

Đầu ra:

~~~text
wideIntQuotient=2147483648
exactLongQuotient=9223372036854775808
asLong=out of range
bigRemainder=0
~~~

`BigInteger.longValueExact()` ném `ArithmeticException` khi giá trị không còn vừa `long`. So với `longValue()` có thể cắt mất bit cao, phiên bản `Exact` phù hợp khi **mất thông tin không được phép**. Phép chia `BigInteger.divide` với các toán hạng nguyên sử dụng thương làm tròn về 0, tương ứng với cách làm tròn chia nguyên của Java trong những trường hợp thương có thể biểu diễn.

## 8. Hệ quả với logic nghiệp vụ và an toàn dữ liệu

Dù hiếm gặp, `MIN_VALUE / -1` đáng chú ý khi đầu vào là số nguyên bất kỳ do người dùng hoặc dịch vụ khác gửi tới. Nếu một phép chia xuất hiện trong tính toán giới hạn, quota, phân chia khối lượng, bộ đếm hoặc kiểm tra ngưỡng, **giá trị đáng lẽ dương nhưng lại âm** có thể làm sai nhánh điều kiện.

Ví dụ giả định:

~~~java
int quotient = amount / divisor;
if (quotient >= 0) {
    // Xử lý cho trường hợp không âm
}
~~~

Đây là **đoạn minh họa**. Nếu `amount == Integer.MIN_VALUE` và `divisor == -1`, nhánh trên sẽ không chạy, mặc dù thương toán học là số dương. Một kiểm tra `divisor != 0` là cần thiết nhưng **chưa đủ** để bảo đảm thương biểu diễn chính xác.

Khi thiết kế xử lý:

- Nếu API cho phép kết quả `int`/`long` cố định: kiểm tra và thông báo overflow trước khi chia.
- Nếu phép toán phải chính xác với mọi đầu vào trong miền `int`: nâng lên `long` trước khi chia (và quyết định kiểu kết quả trả ra).
- Nếu phải chính xác với mọi đầu vào trong miền `long`: sử dụng `BigInteger` cho phép tính/kết quả phù hợp.
- Nếu quy tắc làm tròn về âm vô cùng quan trọng: `Math.floorDiv` giải quyết **cách làm tròn**, nhưng vẫn cần kiểm tra riêng trường hợp tràn.

Khi viết test, kiểm tra **biên**, không chỉ các số đẹp: `MIN_VALUE / 1`, `MIN_VALUE / -1`, `MIN_VALUE % -1`, `MAX_VALUE / -1`, `0 / -1`, số chia `0` và một phép chia âm có phần dư như `-7 / 3`.

## 9. Những nhầm lẫn thường gặp

| Phát biểu | Đánh giá | Giải thích |
|---|---|---|
| “Chia `MIN_VALUE` cho `-1` sẽ ném `ArithmeticException`.” | Sai | Phép chia nguyên `/` trả `MIN_VALUE` theo đặc tả. |
| “`MIN_VALUE / -1` cho `MAX_VALUE`.” | Sai | Kết quả là **`MIN_VALUE`**, không phải giá trị dương lớn nhất. |
| “Phần dư bằng 0 có nghĩa phép chia không overflow.” | Sai | `MIN_VALUE % -1` bằng 0 dù thương tràn. |
| “Ép kết quả phép chia từ `int` sang `long` sẽ sửa overflow.” | Sai | Cần mở rộng **trước khi tính**. |
| “`Math.floorDiv` tự phát hiện tràn thương.” | Sai | Nó thay quy tắc làm tròn, không kiểm tra biên `MIN_VALUE / -1`. |
| “`BigInteger` bị tràn như `long` khi chia.” | Sai | Không bị giới hạn bởi độ rộng 64 bit; bị giới hạn bởi tài nguyên thực tế. |
| “Bảo vệ phép chia cho 0 là đủ để tính toán an toàn.” | Sai | Còn phải kiểm tra thương vượt miền nếu bài toán yêu cầu tính chính xác. |

## 10. Câu hỏi tự kiểm tra

1. Vì sao phép chia `Integer.MIN_VALUE / -1` có thương toán học không thể biểu diễn bằng `int`?
2. Java thực sự trả giá trị gì khi thực hiện phép chia đó? Có ném exception không?
3. `Long.MIN_VALUE / -1L` có cùng vấn đề không? Cần kiểu nào để biểu diễn thương dương chính xác?
4. Kết quả `Integer.MIN_VALUE % -1` bằng bao nhiêu? Có thể xem phép `%` là cơ chế phát hiện overflow không?
5. Hai biểu thức `(long) (Integer.MIN_VALUE / -1)` và `((long) Integer.MIN_VALUE) / -1` khác gì nhau?
6. `Math.floorDiv(-7, 3)` khác `-7 / 3` như thế nào? `Math.floorDiv(Integer.MIN_VALUE, -1)` có tự tránh overflow không?
7. Viết điều kiện tối thiểu để phép chia `int x / int y` trả thương chính xác hoặc phát sinh lỗi, không bao giờ âm thầm cuốn vòng ở biên.

**Đáp án gợi ý:** (1) Miền `int` không đối xứng, thiếu số dương `2147483648`. (2) Trả `Integer.MIN_VALUE`, không ném exception. (3) Có; dùng `BigInteger` nếu cần thương chính xác. (4) `0`; không. (5) Biểu thức thứ nhất tràn trước khi cast, biểu thức thứ hai được nâng lên `long` trước phép chia. (6) `-3` so với `-2`; `floorDiv` vẫn trả `MIN_VALUE` ở biên. (7) Kiểm tra `y == 0` và `x == Integer.MIN_VALUE && y == -1` trước khi dùng `x / y`.

**Ghi nhớ:** Trong phép chia số nguyên có dấu của Java, **`MIN_VALUE / -1` là trường hợp thương toán học vượt miền nhưng toán tử `/` vẫn trả chính `MIN_VALUE`**. Nếu kết quả phải chính xác, hãy **kiểm tra overflow hoặc mở rộng kiểu trước phép tính**; chia cho 0 là lỗi riêng biệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="integer-arithmetic-010">Nối chuỗi bằng + khác phép cộng như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Nối chuỗi bằng `+` khác phép cộng như thế nào trong Java?

## 1. Cùng một dấu `+`, hai hành vi khác nhau

Trong Java, dấu `+` là toán tử có **hai cách sử dụng chính** trong biểu thức:

- **Cộng số học** (*numeric addition*) khi hai toán hạng là các giá trị số có thể áp dụng quy tắc thăng cấp số học: `2 + 3` tạo ra số nguyên **5**.
- **Nối chuỗi** (*string concatenation*) khi ít nhất một toán hạng có kiểu `String`: `"2" + 3` tạo ra chuỗi **"23"**.

Những ví dụ đầu tiên:

| Biểu thức Java | Kiểu kết quả | Giá trị kết quả | Vì sao? |
|---|---|---|---|
| `2 + 3` | `int` | `5` | Cộng hai số nguyên |
| `2L + 3` | `long` | `5L` | Có `long` nên kết quả là `long` |
| `"2" + 3` | `String` | `"23"` | Nối văn bản "2" với giá trị 3 |
| `2 + "3"` | `String` | `"23"` | Nối số 2 đã được chuyển thành văn bản với "3" |
| `"2" + "3"` | `String` | `"23"` | Nối hai chuỗi |
| `'2' + 3` | `int` | `53` | Ký tự '2' có mã UTF-16 50, cộng với 3 |
| `"2" + '3'` | `String` | `"23"` | Một toán hạng là `String` |

**Giá trị `"23"` khác giá trị số `23`.** Chuỗi là dữ liệu văn bản và có thể gồm ký tự, khoảng trắng, đơn vị đo, mã định danh; phép cộng số học tạo kết quả số theo kiểu và miền giá trị của toán hạng.

Không phải hễ xuất hiện dấu `+` là Java cộng số, và cũng không phải hễ xuất hiện ký tự chữ số là một đối tượng `String`. Hãy luôn xác định **kiểu của từng toán hạng tại thời điểm toán tử `+` được đánh giá**.

## 2. Quy tắc chọn cộng số hay nối chuỗi

### 2.1. Hai toán hạng số: thăng cấp rồi cộng

Với các toán hạng nguyên thủy dạng số, phép `+` dùng *binary numeric promotion*:

1. `byte`, `short` và `char` được nâng thành `int`.
2. Nếu có toán hạng `long`, phép cộng tích phân sử dụng `long`.
3. Nếu không có `long`, phép cộng của các kiểu tích phân nguyên thủy cho kết quả `int`.

Ví dụ:

```java
byte a = 10;
byte b = 20;
int sum = a + b;     // 30, biểu thức a + b có kiểu int
long wide = a + 20L; // 30L, biểu thức có kiểu long
```

Không thể gán trực tiếp `byte c = a + b;` vì phép cộng hai biến `byte` trả `int`; muốn chuyển về `byte` phải ép kiểu có chủ đích, kèm kiểm tra miền khi cần.

### 2.2. Một toán hạng là `String`: chuyển thành văn bản rồi nối

Nếu tại một phép `+` nhị phân **một toán hạng có kiểu `String`**, Java dùng phép nối chuỗi. Toán hạng còn lại được biểu diễn bằng văn bản:

```java
String name = "Lan";
int points = 12;
String message = name + " có " + points + " điểm";
// "Lan có 12 điểm"
```

Ở phép nối cuối, số nguyên `points` được chuyển thành biểu diễn văn bản `"12"`. Đó là chuyển đổi **số sang chuỗi**, không phải cộng thêm 12 vào một con số khác.

Các kiểu nguyên thủy như `int`, `long`, `boolean`, `char` đều có thể xuất hiện trong phép nối với `String`. Giá trị tham chiếu cũng có thể nối: `null` được biểu diễn thành văn bản `"null"`; đối tượng khác có cách biểu diễn chuỗi tương ứng, thường liên quan đến `toString()`.

**Lưu ý phạm vi:** phép `+` được phép nối `String`, nhưng Java **không cho phép** dùng `-` hoặc `*` với chuỗi. Ví dụ `"8" - "3"` và `"8" * 3` đều là lỗi biên dịch. Muốn tính số từ chuỗi, phải **parse** sang kiểu số trước.

## 3. Thứ tự đánh giá và tính kết hợp trái gây kết quả bất ngờ

Toán tử `+` nhị phân có **tính kết hợp trái** (*left-associative*): khi không có dấu ngoặc, một dãy phép cộng/nối cùng mức được nhóm từ trái sang phải.

Do đó:

```java
String a = "Kết quả: " + 2 + 3;   // "Kết quả: 23"
String b = "Kết quả: " + (2 + 3); // "Kết quả: 5"
String c = 2 + 3 + " là tổng";     // "5 là tổng"
String d = 2 + "3" + 4;           // "234"
```

Phân tích từng bước:

- `"Kết quả: " + 2 + 3` được nhóm thành `("Kết quả: " + 2) + 3`. Bước đầu đã là nối chuỗi, cho `"Kết quả: 2"`; bước sau tiếp tục nối `"3"`.
- `"Kết quả: " + (2 + 3)`: ngoặc buộc `2 + 3` được tính bằng số nguyên trước, cho 5; sau đó mới nối thành `"Kết quả: 5"`.
- `2 + 3 + " là tổng"` được nhóm thành `(2 + 3) + " là tổng"`. Bước đầu cộng hai số thành 5, bước sau chuyển 5 thành chuỗi rồi nối.
- `2 + "3" + 4`: bước `2 + "3"` đã cho chuỗi `"23"`, nên toán tử tiếp theo nối thêm ký tự `4`; không có phép cộng số `23 + 4`.

**Điểm then chốt:** quyết định phép `+` là cộng hay nối được xét **ở mỗi nút của biểu thức**, không phải một quyết định chung cho toàn dòng. Khi một phần biểu thức đã cho `String`, những phép `+` tiếp theo với phần đó sẽ là nối chuỗi; dấu ngoặc có thể tạo một phép cộng số **độc lập** rồi mới nối.

### Độ ưu tiên với toán tử khác

Phép nhân `*` có độ ưu tiên cao hơn `+`:

```java
String x = "Tổng: " + 2 * 3;       // "Tổng: 6"
String y = "Tổng: " + (2 + 3) * 4; // "Tổng: 20"
```

Dấu ngoặc giúp thể hiện ý định, đặc biệt trong câu log, báo cáo, định dạng thông báo và chuỗi hiển thị kết quả tính toán. Không cần thêm ngoặc ở khắp nơi, nhưng nên dùng khi một người đọc có thể nhầm cộng số với nối chuỗi.

## 4. Chương trình Java 17+ để kiểm chứng

Tạo tệp `PlusOperatorDemo.java`:

```java
public class PlusOperatorDemo {
    public static void main(String[] args) {
        System.out.println("2 + 3 = " + (2 + 3));
        System.out.println("plain addition: " + (2 + 3));
        System.out.println("concat first: " + 2 + 3);
        System.out.println(2 + 3 + " is five");
        System.out.println("mixed: " + 2 + "3" + 4);
        System.out.println("multiply: " + 2 * 3);
        System.out.println("char arithmetic: " + ('A' + 1));
        System.out.println("char concat: " + 'A' + 1);

        String count = "count=";
        count += 4;
        count += 5;
        System.out.println(count);
    }
}
```

Biên dịch và chạy:

```bash
javac PlusOperatorDemo.java
java PlusOperatorDemo
```

Kết quả chính xác:

```text
2 + 3 = 5
plain addition: 5
concat first: 23
5 is five
mixed: 234
multiply: 6
char arithmetic: 66
char concat: A1
count=45
```

Các dòng `char` cho thấy một khác biệt quan trọng. `'A'` là literal **kiểu `char`**, có giá trị code unit số 65. Trong `'A' + 1`, Java nâng `char` lên `int`, cho **66**. Trong `"char concat: " + 'A'`, một toán hạng đã là `String`, nên ký tự **A** được nối vào chuỗi; phép `+ 1` tiếp theo lại nối văn bản **1**.

### `char` không giống `String`

```java
int numeric = '1' + 2;     // 49 + 2 = 51
String text = "1" + 2;      // "12"
String letters = 'A' + "" + 'B'; // "AB"
int codes = 'A' + 'B';      // 65 + 66 = 131
```

Dấu nháy đơn `'A'` biểu diễn `char`; nháy kép `"A"` biểu diễn `String`. Sự khác biệt **kiểu** này quyết định hành vi của `+`.

Một Unicode code point ngoài BMP có thể cần hai `char` (cặp surrogate), vì `char` là **UTF-16 code unit**, không phải mọi ký tự người dùng nhìn thấy. Khi nối một `String` với `char`, Java nối code unit đó theo biểu diễn chuỗi.

## 5. Nối chuỗi không chữa được overflow xảy ra trước đó

Phép cộng số nguyên có độ rộng hữu hạn. Với `int` 32 bit, `Integer.MAX_VALUE` là 2_147_483_647. Nếu cộng 1 **bằng `int`** thì tràn thành -2_147_483_648.

Xét:

```java
int max = Integer.MAX_VALUE;

String a = "sum=" + (max + 1);       // "sum=-2147483648"
String b = "sum=" + max + 1;         // "sum=21474836471"
String c = "sum=" + ((long) max + 1); // "sum=2147483648"
```

- Với `a`, dấu ngoặc buộc cộng hai số `int` trước → **tràn số**, rồi giá trị âm mới được đổi thành văn bản.
- Với `b`, `"sum=" + max` đã tạo chuỗi, nên `+ 1` chỉ nối văn bản `1`, thành một chuỗi số nhìn giống nhưng **không phải tổng số học**.
- Với `c`, `(long) max + 1` được tính bằng `long` nên cho kết quả số đúng, rồi mới nối chuỗi.

Viết `long result = max + 1;` cũng không khắc phục tràn: phép cộng hoàn tất ở `int` trước khi gán. Nếu tràn số phải bị từ chối, dùng `Math.addExact(max, 1)` để Java ném `ArithmeticException`, thay vì tính toán rồi định dạng một số sai.

**Nguyên tắc:** giải quyết **đúng kiểu và đúng phép tính số học trước**, sau đó mới nối phần văn bản trình bày. Đừng dùng giao diện hiển thị chuỗi như một cách kiểm chứng kết quả số.

## 6. Nối chuỗi với số thực, boolean, null và đối tượng

### 6.1. Số thực và boolean

```java
String temperature = "Temp: " + 23.5; // "Temp: 23.5"
String enabled = "Enabled: " + true;  // "Enabled: true"
```

`true` là `boolean`, không được cộng số học với `int`; nhưng `"Enabled: " + true` hợp lệ vì đó là **nối chuỗi**.

Số thực cũng được chuyển sang văn bản trong phép nối. Nếu giao diện cần quy tắc hiển thị rõ ràng (ví dụ hai chữ số thập phân, dấu phân cách theo locale), dùng API định dạng phù hợp thay vì dựa vào cách biểu diễn chuỗi mặc định của `double`.

### 6.2. `null` trong nối chuỗi

Khi một **tham chiếu** là `null` và tham gia phép nối chuỗi, văn bản được thêm là **"null"**:

```java
String name = null;
Object item = null;
System.out.println("name=" + name); // name=null
System.out.println("item=" + item); // item=null
```

Đây không phải một đối tượng `String` thực sự chứa `"null"` ở biến `name`: biến `name` vẫn là tham chiếu `null`. Văn bản `"null"` chỉ được tạo để biểu diễn nó trong chuỗi kết quả.

Với wrapper số như `Integer`, sự phân biệt càng quan trọng:

```java
Integer quantity = null;
System.out.println("quantity=" + quantity); // quantity=null

// int n = quantity + 1; // khi chạy sẽ ném NullPointerException
```

Trường hợp thứ nhất là nối chuỗi với **tham chiếu**; trường hợp thứ hai là cộng số, nên Java phải **unbox** `Integer` thành `int` trước, và việc unbox `null` ném `NullPointerException`.

### 6.3. Đối tượng và `toString()`

Một đối tượng thông thường được chuyển thành biểu diễn văn bản khi nối với `String`. Cách biểu diễn thường do `toString()` quyết định.

```java
record Item(String code, int quantity) {}

Item item = new Item("BK", 2);
String description = "item=" + item;
```

Đây là **đoạn mã minh họa** trong một phạm vi có thể khai báo `record` và sử dụng biến; muốn chạy độc lập cần đặt trong cấu trúc Java hoàn chỉnh. Với record Java, biểu diễn mặc định có dạng `Item[code=BK, quantity=2]` (nếu không ghi đè `toString()`).

**Đừng giả định tất cả đối tượng tự tạo đều có thông tin hữu ích khi nối chuỗi.** Nếu lớp không tự định nghĩa `toString()`, kết quả mặc định thường là tên lớp kèm dấu `@` và mã băm ở dạng hex, không phải các thuộc tính nghiệp vụ. Nếu cần hiển thị mảng, dùng `Arrays.toString(array)` hoặc `Arrays.deepToString(array)` cho mảng lồng phù hợp thay vì chỉ nối mảng với chuỗi.

## 7. Ví dụ xử lý `null` và hiển thị đối tượng có thể chạy

Tạo tệp `StringConversionDemo.java`:

```java
import java.util.Arrays;

public class StringConversionDemo {
    record Product(String code, int count) {}

    public static void main(String[] args) {
        Integer quantity = null;
        String name = null;
        int[] numbers = {1, 2, 3};

        System.out.println("quantity=" + quantity);
        System.out.println("name=" + name);
        System.out.println("array=" + Arrays.toString(numbers));
        System.out.println("product=" + new Product("BK", 2));

        try {
            int next = quantity + 1; // phải unbox null
            System.out.println(next);
        } catch (NullPointerException e) {
            System.out.println("numeric null: NullPointerException");
        }

        System.out.println("boolean=" + true);
        System.out.println("decimal=" + 23.5);
    }
}
```

Chạy:

```bash
javac StringConversionDemo.java
java StringConversionDemo
```

Kết quả:

```text
quantity=null
name=null
array=[1, 2, 3]
product=Product[code=BK, count=2]
numeric null: NullPointerException
boolean=true
decimal=23.5
```

Đoạn mã minh họa vì sao hai phép `+` có thể xử lý cùng một biến `quantity` theo hai cách: `"quantity=" + quantity` là nối chuỗi; `quantity + 1` là phép toán số học cần unboxing.

## 8. `+=`: cộng gán hay nối thêm?

Toán tử `+=` cũng phụ thuộc kiểu của biến bên trái:

```java
int number = 5;
number += 3;        // cộng số: number = 8

String label = "item";
label += 3;         // nối chuỗi: label = "item3"
label += 4;         // nối tiếp: label = "item34"
```

Với biến `String`, `s += x` có hiệu ứng nối giá trị `x` vào chuỗi cũ của `s`; kết quả vẫn là `String`. Với biến số, `+=` dùng phép tính số học và quy tắc gán kết hợp của Java, có thể bao gồm **ép kiểu ngầm** về kiểu biến vế trái.

```java
byte small = 120;
small += 10; // cho -126 do phép cộng int rồi thu hẹp về byte

String text = "120";
text += 10;  // "12010", không có overflow số học
```

Với `String`, toán tử `+=` có thể tạo kết quả mới vì **`String` là bất biến** (*immutable*): nội dung chuỗi đã tồn tại không bị sửa trực tiếp. Việc gán lại khiến biến `text` tham chiếu đến kết quả nối.

**Giới hạn chiều ngược lại:** `int x = 10; x += "20";` là lỗi biên dịch vì không thể gán kết quả chuỗi về biến `int`. Java không tự parse một chuỗi chữ số chỉ vì biến nhận là số.

## 9. Thứ tự đánh giá và tác dụng phụ trong phép `+`

Java đánh giá các toán hạng của toán tử nhị phân **từ trái sang phải**. Nếu toán hạng có hành động thay đổi trạng thái (ví dụ `i++`, gọi phương thức cập nhật biến), thứ tự đó ảnh hưởng đến chuỗi kết quả.

Ví dụ:

```java
int i = 1;
System.out.println("before=" + i++ + ", after=" + i);
```

Kết quả:

```text
before=1, after=2
```

Phép `i++` trả giá trị cũ 1 cho phép nối hiện tại, sau đó tăng `i` thành 2. Toán hạng `", after=" + i` được đánh giá sau nên quan sát được giá trị mới. Thứ tự đánh giá được ngôn ngữ Java xác định; nhưng để code dễ hiểu, tránh đưa nhiều tác dụng phụ vào một biểu thức nối chuỗi dài.

Một lưu ý khác: biểu thức `"result=" + calculate()` **vẫn gọi** `calculate()` trước khi có giá trị chuỗi, ngay cả nếu giá trị sau đó chỉ dùng để hiển thị. Không nên gọi hàm có side effect trong phép nối chỉ để định dạng.

## 10. Chuyển chuỗi chữ số thành số để tính toán thật

Nối chuỗi **không tự parse** chữ số:

```java
String left = "20";
String right = "3";

String joined = left + right;  // "203"
int total = Integer.parseInt(left) + Integer.parseInt(right); // 23
```

Nếu dữ liệu người dùng là số nhưng được nhập dạng text, phải xác thực đầu vào và chuyển bằng API thích hợp, chẳng hạn `Integer.parseInt` hoặc `Long.parseLong`. Khi chuỗi không phải định dạng số hợp lệ hoặc vượt miền kiểu đích, các phương thức parse ném `NumberFormatException`.

Ví dụ:

```java
String price = "120";
String quantity = "3";
int amount = Integer.parseInt(price) * Integer.parseInt(quantity);
// amount = 360
String display = "amount=" + amount; // "amount=360"
```

Trình tự dễ kiểm soát là **parse → tính toán → kiểm tra tràn/miền → định dạng kết quả**. Nếu phép tính có nguy cơ tràn, chọn kiểu rộng hoặc `Math.addExact`/`Math.multiplyExact` phù hợp trước khi chuyển kết quả sang chuỗi.

**Dấu `+` một ngôi:** `+5` và `+number` chỉ là dấu dương số học, không phải nối chuỗi; không thể dùng `+"hello"`. Chủ đề nối chuỗi ở đây nói về **toán tử `+` nhị phân** có hai toán hạng.

## 11. Hiệu năng, `StringBuilder` và cách viết rõ ràng

Vì `String` bất biến, việc nối trong **vòng lặp lớn** có thể tạo ra nhiều chuỗi trung gian và tốn bộ nhớ/cấp phát:

```java
String result = "";
for (int n = 1; n <= 3; n++) {
    result += n;
}
// result = "123"
```

Khi cần tích lũy nhiều đoạn văn bản trong vòng lặp, có thể dùng `StringBuilder`:

```java
StringBuilder builder = new StringBuilder();
for (int n = 1; n <= 3; n++) {
    builder.append(n);
}
String result = builder.toString(); // "123"
```

Một biểu thức nối thông thường như `"name=" + name + ", age=" + age` vẫn là cú pháp tự nhiên, dễ đọc; các phiên bản Java hiện đại có cơ chế tối ưu việc nối chuỗi ở lúc biên dịch/chạy. Không cần biến mọi phép `+` với `String` thành `StringBuilder`. Chỉ cân nhắc builder khi thực sự **lặp lại nhiều lần**, tích lũy văn bản lớn hoặc khi hiệu năng có bằng chứng là vấn đề.

Khi định dạng thông báo nhiều trường, cũng có thể dùng `String.format`, `System.out.printf` hoặc các công cụ định dạng phù hợp. Các API này hữu ích cho cấu trúc hiển thị, độ rộng trường, số chữ số thập phân và locale; bản chất phép cộng số học vẫn phải được xác định trước.

## 12. Lỗi phổ biến và cách khắc phục

| Triệu chứng | Nguyên nhân | Cách viết rõ ràng hơn |
|---|---|---|
| `"Sum=" + 2 + 3` in `Sum=23` | Nối chuỗi xuất hiện trước phép cộng số | `"Sum=" + (2 + 3)` |
| `2 + "3" + 4` thành `234` | Sau bước đầu kết quả là `String` | Parse/ngoặc phép tính theo mục đích |
| `'1' + 2` cho 51 | `char` thăng cấp thành `int`, '1' có mã 49 | Dùng `"1" + 2` để nối, hoặc chuyển chữ số sang số |
| `"x=" + (max + 1)` in số âm | Phép cộng `int` tràn trước khi nối | Nâng lên `long` hoặc dùng `Math.addExact` |
| `Integer value = null; value + 1` gây NPE | Số học cần unbox tham chiếu null | Kiểm tra null trước khi tính |
| `"obj=" + obj` in thông tin khó đọc | Chuỗi mặc định của đối tượng không mang dữ liệu mong muốn | Viết `toString()` hữu ích hoặc chọn trường rõ ràng |
| `"array=" + array` không in phần tử | Mảng không tự hiển thị nội dung qua phép nối | `Arrays.toString(array)` |
| `int x = 10; x += "20";` lỗi | Kết quả chuỗi không thể gán cho biến số | Parse chuỗi hoặc dùng biến `String` |
| Nối chuỗi lặp nhiều lần gây overhead | `String` bất biến; tích lũy nhiều chuỗi | Dùng `StringBuilder` cho vòng lặp lớn |

## 13. Bài tập tự kiểm tra

**Câu 1.** `System.out.println("Total: " + 10 + 20);` in gì?  
**Đáp án:** `Total: 1020`, vì toán tử đầu đã bắt đầu nối chuỗi.

**Câu 2.** Muốn in `Total: 30` từ hai số 10 và 20, viết gì?  
**Đáp án:** `System.out.println("Total: " + (10 + 20));`.

**Câu 3.** `System.out.println(10 + 20 + " items");` in gì?  
**Đáp án:** `30 items`, vì `10 + 20` là phép cộng số trước khi gặp `String`.

**Câu 4.** `'3' + 1` có bằng `"3" + 1` không?  
**Đáp án:** Không. `'3'` là `char` có mã 51, nên kết quả số là `int` 52; `"3"` là `String`, nên kết quả là `"31"`.

**Câu 5.** `String s = null; String t = "value=" + s;` cho `t` bằng gì?  
**Đáp án:** `"value=null"`. Biến `s` vẫn là tham chiếu `null`.

**Câu 6.** `Integer n = null; System.out.println("n=" + n);` có gây lỗi như `n + 1` không?  
**Đáp án:** Không. Phép đầu nối văn bản `"null"`; phép sau cần unbox `n` và ném `NullPointerException`.

**Câu 7.** Vì sao `"sum=" + (Integer.MAX_VALUE + 1)` in số âm?  
**Đáp án:** Vì tổng hai `int` tràn **trước** khi được chuyển thành chuỗi. Nâng toán hạng lên `long` trước phép cộng nếu cần miền lớn hơn.

**Câu 8.** Khi nào dùng `StringBuilder`?  
**Đáp án:** Khi tích lũy nhiều đoạn chuỗi qua vòng lặp hoặc cần giảm lượng chuỗi trung gian; với một câu ngắn, `+` thường rõ ràng và phù hợp.

**Câu 9.** `String s = "x"; s += 2; s += 3;` cho kết quả gì?  
**Đáp án:** `"x23"`, vì cả hai lần `+=` đều nối văn bản vào biến `String`.

**Câu 10.** `"Result: " + 2 * 3` cho gì, và tại sao?  
**Đáp án:** `"Result: 6"`. Toán tử nhân `*` có độ ưu tiên cao hơn `+`, nên tính số 6 trước khi nối chuỗi.

**Ghi nhớ:** Với `+` nhị phân, **hai toán hạng số → cộng số học; có `String` → nối văn bản**. Java nhóm các phép `+` cùng mức từ trái sang phải. Muốn hiển thị **tổng số**, đặt phép tính trong ngoặc (hoặc tính trước vào biến) rồi mới nối thành chuỗi; kết quả chuỗi không tự sửa lỗi tràn số đã xảy ra.


</details>

- [Quay lại đầu trang](#back-to-top)
