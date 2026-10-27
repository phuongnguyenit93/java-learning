<a id="back-to-top"></a>

# Math và StrictMath

## Menu
- [abs, min, max, signum và clamp theo phiên bản JDK](#math-and-strict-math-001)
- [pow, sqrt, cbrt và lựa chọn phép tính phù hợp](#math-and-strict-math-002)
- [exp, log, log10, expm1 và log1p](#math-and-strict-math-003)
- [sin, cos, tan và chuyển độ sang radian](#math-and-strict-math-004)
- [asin, acos, atan và atan2](#math-and-strict-math-005)
- [hypot và việc giảm nguy cơ overflow trung gian](#math-and-strict-math-006)
- [ceil, floor, rint và round](#math-and-strict-math-007)
- [nextUp, nextDown, nextAfter và ulp](#math-and-strict-math-008)
- [copySign, scalb và getExponent](#math-and-strict-math-009)
- [Phân biệt yêu cầu tái lập của Math và StrictMath](#math-and-strict-math-010)
- [Miền xác định, NaN, vô cực và độ chính xác được Javadoc quy định](#math-and-strict-math-011)

## <a id="math-and-strict-math-001">abs, min, max, signum và clamp theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# abs, min, max, signum và clamp theo phiên bản JDK

> **Java core – numbers · Math và StrictMath**  
> **Mục tiêu:** Dùng đúng các hàm trị tuyệt đối, chọn cực tiểu/cực đại, xác định dấu và giới hạn giá trị; xử lý tràn số nguyên, NaN, vô cực, số 0 có dấu; phân biệt API chạy được trên Java 17 với `Math.clamp`/`StrictMath.clamp` được bổ sung từ JDK 21.

## 1. Tổng quan: 5 thao tác nhìn đơn giản nhưng có nhiều bẫy

Khi xử lý số, những câu hỏi thường gặp là:

- **`abs(x)`:** độ lớn không âm của x là bao nhiêu?
- **`min(a,b)`:** giá trị nào nhỏ hơn?
- **`max(a,b)`:** giá trị nào lớn hơn?
- **`signum(x)`:** x âm, bằng 0 hay dương?
- **`clamp(x,low,high)`:** đưa x về **đoạn đóng** từ low đến high; nếu x thấp hơn low lấy low, nếu cao hơn high lấy high, nếu nằm trong đoạn thì giữ x.

Ví dụ trực quan trên số nguyên:

```text
             -∞               0         10              +∞
--------------|---------------|----------|----------------|----
                              [0--------10]
clamp(-5,0,10) -> 0
clamp( 7,0,10) -> 7
clamp(20,0,10) -> 10
```

Nhưng Java không chỉ có một hệ số: kiểu nguyên có miền hữu hạn; kiểu floating-point IEEE 754 có NaN, ±Infinity và cả +0.0/-0.0. Một số API cũng **không có trên các JDK cũ**. Vì vậy cần đọc cả **kiểu tham số** lẫn **phiên bản JDK**.

### Bảng định hướng API

| Thao tác | Java 17 | JDK 21+ | Lưu ý |
| --- | --- | --- | --- |
| `Math.abs` / `StrictMath.abs` | Có | Có | `int/long MIN_VALUE` vẫn âm khi lấy abs thường |
| `Math.absExact` / `StrictMath.absExact` | Có (từ 15) | Có | Ném `ArithmeticException` nếu trị tuyệt đối nguyên tràn |
| `Math.min` / `StrictMath.min` | Có | Có | Có overload int, long, float, double |
| `Math.max` / `StrictMath.max` | Có | Có | Truyền NaN cho overload float/double trả NaN |
| `Math.signum` / `StrictMath.signum` | Có | Có | Chỉ có overload float/double |
| `Integer.signum` / `Long.signum` | Có | Có | Trả int −1, 0 hoặc 1 |
| `Math.clamp` / `StrictMath.clamp` | **Không** | **Có, từ 21** | Kiểm tra min/max hợp lệ, nhiều overload |

**Điểm đặc biệt:** trên JDK 21, phương thức `clamp` cho số nguyên nhận `(long value,long min,long max)` hoặc `(long value,int min,int max)`; **không có riêng overload `clamp(int,int,int)` trong bảng API**. Lời gọi với ba biểu thức `int` vẫn có thể dùng overload `long,int,int` bằng cách mở rộng đối số đầu tiên sang `long`, và kiểu trả về khi đó là `int`.

## 2. Math và StrictMath: nên chọn cái nào?

Cả `java.lang.Math` và `java.lang.StrictMath` đều cung cấp những phép toán số học cơ bản. Hai lớp đều có phương thức **static** nên gọi trực tiếp:

```java
int magnitude = Math.abs(-12);
double minimum = StrictMath.min(3.0, 5.0);
```

Không tạo đối tượng `new Math()` hay `new StrictMath()`. Hai lớp này không được thiết kế để khởi tạo.

Với các phép đơn giản trong bài như `abs`, `min`, `max`, `signum`, quy tắc kết quả đặc biệt được mô tả rõ trong Javadoc và hai lớp có ngữ nghĩa tương ứng. `StrictMath` nổi bật ở những phép toán toán học nâng cao có yêu cầu đặc biệt về tính tái lập của thuật toán; **không cần chuyển mọi `Math.abs` thành `StrictMath.abs` để sửa lỗi tràn nguyên hay NaN**.

Từ Java 17, ngữ nghĩa biểu thức dấu phẩy động của Java được thống nhất theo chế độ *always-strict*; điều này **không làm biến mất** NaN, số 0 có dấu hoặc các giới hạn biểu diễn. `Math` và `StrictMath` vẫn là hai API được tài liệu hóa riêng, nhưng trong chủ đề này phải tập trung vào **contract từng phương thức** thay vì coi tên `StrictMath` như một cách đảm bảo phép toán nguyên tự kiểm tra tràn.

## 3. Math.abs và StrictMath.abs: trị tuyệt đối

`abs` trả độ lớn của một số theo quy tắc của kiểu đầu vào:

```java
System.out.println(Math.abs(-7));        // 7
System.out.println(Math.abs(-7L));       // 7
System.out.println(Math.abs(-3.25));     // 3.25
System.out.println(Math.abs(-3.25f));    // 3.25
```

Các overload chuẩn:

| Hàm | Tham số | Kiểu trả về |
| --- | --- | --- |
| `Math.abs(int)` | int | int |
| `Math.abs(long)` | long | long |
| `Math.abs(float)` | float | float |
| `Math.abs(double)` | double | double |

`StrictMath.abs` cũng có bốn overload tương ứng.

Với `float`/`double`, cần nhớ ba trường hợp:

```java
System.out.println(Math.abs(-0.0));                    // 0.0
System.out.println(Math.abs(Double.NEGATIVE_INFINITY)); // Infinity
System.out.println(Double.isNaN(Math.abs(Double.NaN)));  // true
```

`Math.abs(-0.0)` trả **+0.0**, không giữ dấu âm của zero. Nếu phải giữ riêng bit dấu (ví dụ logic nhị phân đặc biệt), `abs` không phải thao tác thích hợp để lưu dấu.

## 4. Bẫy quan trọng: abs của MIN_VALUE có thể vẫn âm

Với số nguyên có dấu theo bù hai, miền âm có thêm **một** giá trị không có đối ứng dương cùng độ lớn:

```text
int:
MIN_VALUE = -2147483648
MAX_VALUE =  2147483647

long:
MIN_VALUE = -9223372036854775808
MAX_VALUE =  9223372036854775807
```

Vì thế trị tuyệt đối toán học của `Integer.MIN_VALUE` là 2.147.483.648, **vượt miền `int`**.

```java
int minimum = Integer.MIN_VALUE;
System.out.println(Math.abs(minimum)); // -2147483648
```

Đây là kết quả của **overflow theo quy tắc số nguyên Java**: `Math.abs(int)` không tự ném ngoại lệ. Tương tự:

```java
System.out.println(Math.abs(Long.MIN_VALUE));
// -9223372036854775808
```

Hậu quả rất thực tế:

```java
int hash = Integer.MIN_VALUE;
int bucket = Math.abs(hash) % 10;
System.out.println(bucket); // -8
```

Nếu sau đó dùng `bucket` làm chỉ số mảng, bạn có thể gặp lỗi. Không dùng công thức `Math.abs(x) % size` như cách **luôn bảo đảm chỉ số không âm**.

### Cách xử lý tùy mục tiêu

**Cần nhận biết overflow và báo lỗi:** từ Java 15 có `Math.absExact(int)`/`Math.absExact(long)` và các phương thức tương ứng của `StrictMath`:

```java
try {
    Math.absExact(Integer.MIN_VALUE);
} catch (ArithmeticException ex) {
    System.out.println("abs overflow");
}
```

**Cần trị tuyệt đối của mọi giá trị int theo một kiểu đủ rộng:**

```java
long exactMagnitude = Math.abs((long) Integer.MIN_VALUE);
System.out.println(exactMagnitude); // 2147483648
```

Cast sang `long` phải xảy ra **trước** khi gọi `abs`. Viết `(long) Math.abs(Integer.MIN_VALUE)` là quá muộn: phép `abs(int)` đã overflow.

**Với `Long.MIN_VALUE`**, ngay cả `long` cũng không đủ chứa độ lớn dương. Khi cần một số nguyên toán học chính xác, dùng `BigInteger`:

```java
import java.math.BigInteger;

BigInteger magnitude =
        BigInteger.valueOf(Long.MIN_VALUE).abs();
System.out.println(magnitude); // 9223372036854775808
```

**Nếu mục tiêu là chỉ số 0..size−1**, cân nhắc `Math.floorMod(hash,size)` khi `size > 0`, thay vì ép trị tuyệt đối:

```java
System.out.println(Math.floorMod(Integer.MIN_VALUE, 10)); // 2
```

Công thức chọn phụ thuộc yêu cầu nghiệp vụ: `floorMod` mang nghĩa modulo không âm với môđun dương, **khác** việc lấy trị tuyệt đối rồi chia lấy dư.

## 5. min và max cho số nguyên

Các phương thức `Math.min(a,b)` và `Math.max(a,b)` chọn phần tử nhỏ/lớn theo thứ tự số.

```java
System.out.println(Math.min(7, 12));   // 7
System.out.println(Math.max(7, 12));   // 12
System.out.println(Math.min(-5L, 3L)); // -5
```

Có overload int, long, float và double. Nếu có lẫn các kiểu, **numeric promotion** và khả năng chọn overload là vấn đề cần để ý:

```java
int small = 5;
long large = 9L;
long result = Math.max(small, large); // chọn overload long
```

Nếu bắt buộc kết quả giữ nguyên kiểu `int`, cần bảo đảm các toán hạng và miền giá trị phù hợp. Đừng ép thu hẹp một giá trị `long` về `int` trước khi chọn min/max nếu có thể mất bit.

### min/max không tự kiểm tra tràn phép tính đầu vào

```java
int a = Integer.MAX_VALUE;
int b = 2;
int chosen = Math.max(a + b, 0); // a+b tràn thành số âm
System.out.println(chosen);      // 0
```

`Math.max` không thể biết biểu thức `a+b` từng có ý định biểu diễn số lớn hơn `Integer.MAX_VALUE`. Phải kiểm tra phép cộng từ trước, chẳng hạn bằng `Math.addExact(a,b)` hoặc chuyển sang `long` trước phép cộng nếu miền đủ rộng.

## 6. min/max với float/double: NaN và số 0 có dấu

Khác số nguyên, kiểu IEEE 754 có `NaN` và hai cách mã hóa số 0.

### 6.1. Khi một đối số là NaN

Đối với overload `float`/`double`:

```java
double result = Math.min(Double.NaN, 42.0);
System.out.println(Double.isNaN(result)); // true
```

Tương tự:

```java
System.out.println(Double.isNaN(
        Math.max(42.0, Double.NaN))); // true
```

**Math.min/max truyền thống ở đây là NaN-propagating.** Không giả định rằng `Math.max(x,0.0)` “lọc bỏ NaN” hoặc trả giá trị 0 khi `x` là NaN.

Nếu nghiệp vụ quy định bỏ qua mẫu đo NaN, hãy định nghĩa logic xử lý missing/invalid data trước, thay vì trông chờ `min/max` tự loại giá trị NaN. Quy tắc bỏ NaN khác với quy tắc làm phép toán đại số thông thường.

### 6.2. Khi một đối số là +0.0, đối số kia -0.0

Hai số 0 so sánh bằng nhau dưới `==`:

```java
System.out.println(+0.0 == -0.0); // true
```

Nhưng `Math.min` và `Math.max` phân biệt dấu của zero:

```java
System.out.println(Math.min(+0.0, -0.0)); // -0.0
System.out.println(Math.max(+0.0, -0.0)); // 0.0
```

Trong thứ tự của các phương thức này, **−0.0 được coi là nhỏ hơn +0.0**. Điều này khác khi dùng toán tử `<`/`>` với zero vì `-0.0 < +0.0` là false.

Các trường hợp tương tự áp dụng cho overload `float`:

```java
float small = Math.min(+0.0f, -0.0f); // -0.0f
float big = Math.max(+0.0f, -0.0f);   // +0.0f
```

Khi debug dạng bit, kiểm tra:

```java
long bits = Double.doubleToRawLongBits(
        Math.min(+0.0, -0.0));
System.out.printf("%016x%n", bits);
// 8000000000000000
```

Số `0x8000000000000000` là mẫu IEEE 754 của `-0.0`. Dùng raw bits khi cần kiểm chứng chính xác dấu zero hoặc mã hóa nhị phân.

## 7. signum là dấu của số, không phải trị tuyệt đối

`Math.signum(float)` và `Math.signum(double)` trả:

- `-1.0` hoặc `-1.0f` nếu đối số là một số âm **khác 0**;
- `+1.0` hoặc `+1.0f` nếu đối số là một số dương **khác 0**;
- giữ nguyên `+0.0` hoặc `-0.0` nếu đầu vào là signed zero;
- trả `NaN` nếu đối số là NaN.

```java
System.out.println(Math.signum(-12.5)); // -1.0
System.out.println(Math.signum( 12.5)); // 1.0
System.out.println(Math.signum(  0.0)); // 0.0
System.out.println(Math.signum( -0.0)); // -0.0
System.out.println(Double.isNaN(
        Math.signum(Double.NaN)));      // true
```

Với `±Infinity`, dấu vẫn xác định: `signum(+Infinity)=+1.0`, `signum(-Infinity)=-1.0`.

**Không có overload `Math.signum(int)` hay `Math.signum(long)`.** Với số nguyên dùng:

```java
System.out.println(Integer.signum(-12)); // -1
System.out.println(Integer.signum(0));   // 0
System.out.println(Long.signum(100L));   // 1
```

Đừng mặc định kiểu trả về của `Math.signum(-12)` là `int`: đối số `int` có thể được chuyển sang `float` và chọn overload `float` (theo quy tắc overload của Java); kết quả **không** phải kiểu `int`. Khi cần xử lý số nguyên tường minh và đúng kiểu, chọn `Integer.signum`/`Long.signum`.

### Tại sao signum(-0.0) không phải 0.0 dương?

Theo IEEE 754, `-0.0` và `+0.0` có dấu khác nhau dù `==` trả true. `Math.signum` trả **chính zero đầu vào** khi đối số bằng zero, nên:

```java
double negativeZero = -0.0;
double result = Math.signum(negativeZero);
System.out.println(result); // -0.0
```

Nếu bạn cần chỉ một phân loại 3 trạng thái `-1,0,+1` trong ứng dụng và **không muốn phân biệt dấu zero**, có thể kiểm tra `x == 0.0` trước để trả số nguyên 0 theo ý nghĩa nghiệp vụ. Nhưng phải xử lý NaN riêng vì phép so sánh `NaN == 0.0` và `NaN < 0.0` đều là false.

## 8. Clamp nghĩa là gì?

*Clamp* (giới hạn/bão hòa) đưa một giá trị vào đoạn đóng `[min,max]`:

\[
\operatorname{clamp}(x,\ell,h)=
\begin{cases}
\ell,&x<\ell\\
h,&x>h\\
x,&\ell\le x\le h.
\end{cases}
\]

Ví dụ ứng dụng:

- Khống chế mức âm lượng từ 0 đến 100.
- Giới hạn chỉ số tiến độ từ 0.0 đến 1.0.
- Đưa điểm số ngoài miền về biên hợp lệ **khi nghiệp vụ chấp nhận bão hòa**.
- Chuyển một `long` thành `int` bằng cách **bão hòa** theo biên `int`, thay vì cắt bit.

**Clamp không giống** phép chia lấy dư, thu hẹp bit hoặc chuẩn hóa (normalization). `clamp(150,0,100)` là 100; `150 % 101` là 49, còn ép `byte` có thể cho số khác nữa.

Đặc biệt, clamp cũng **không phải validate** theo nghĩa từ chối mọi giá trị ngoài miền; nó **chủ động thay** giá trị bằng cận. Nếu dữ liệu ngoài miền phải gây lỗi, kiểm tra biên và ném ngoại lệ thay vì âm thầm clamp.

## 9. Phiên bản JDK: Math.clamp/StrictMath.clamp được thêm từ 21

Một bẫy tương thích rất phổ biến:

```java
// Chỉ có từ JDK 21:
// int volume = Math.clamp(150L, 0, 100);
```

Mã này **không biên dịch khi dùng `javac --release 17`** hoặc biên dịch trên JDK 17 vì ở đó không có phương thức `clamp` của `Math`.

JDK 21 bổ sung bốn overload trong cả `Math` và `StrictMath`:

| Chữ ký overload | Kiểu kết quả | Ý nghĩa |
| --- | --- | --- |
| `clamp(long value, int min, int max)` | `int` | Clamp một `long` vào miền `int` được chỉ rõ |
| `clamp(long value, long min, long max)` | `long` | Clamp số nguyên 64 bit |
| `clamp(float value, float min, float max)` | `float` | Clamp floating-point 32 bit |
| `clamp(double value, double min, double max)` | `double` | Clamp floating-point 64 bit |

Các overload này có cùng ý nghĩa cơ bản ở `Math` và `StrictMath`. Tài liệu Java 21 ghi rõ `Since: 21` cho chúng.

Một lời gọi với ba `int` được giải quyết bằng overload nhận `long,int,int`:

```java
// Java 21+:
int level = Math.clamp(150, 0, 100);
System.out.println(level); // 100
```

Tuy nhiên, nên viết `150L` nếu đang minh họa tính năng **thu hẹp long một cách bão hòa thành int**, để ý định về overload rõ ràng.

### Dùng khi code chạy nhiều phiên bản JDK

Nếu dự án target Java 17 hoặc vẫn phát hành ứng dụng cho Java 17:

- **Không gọi trực tiếp** `Math.clamp` trong code chung được biên dịch bằng `--release 17`.
- Viết helper clamp cho kiểu cần thiết, với kiểm tra `min <= max`.
- Khi nâng tối thiểu lên JDK 21+, cân nhắc chuyển helper về `Math.clamp` và kiểm thử lại các trường hợp NaN/±0.0.

Không đánh giá API chỉ bằng JDK đang chạy trên máy lập trình: một dự án có thể dùng JDK mới để build nhưng dùng `--release 17` cho mục tiêu tương thích.

## 10. Clamp số nguyên: từ long về int bằng bão hòa

Overload:

```java
// Java 21+:
int result = Math.clamp(hugeLong, lowInt, highInt);
```

nhận giá trị đầu vào `long` và hai cận `int`, kết quả luôn nằm trong đoạn được xác định bởi hai cận, nên trả được kiểu `int` mà **không cắt mất các bit cao như narrowing cast**.

Ví dụ:

```java
// Java 21+:
long count = 5_000_000_000L;
int limited = Math.clamp(count, 0, 100);

System.out.println(limited); // 100
```

So sánh với cast:

```java
long count = 5_000_000_000L;
int truncated = (int) count; // làm hẹp theo bit, không clamp
```

Hai thao tác thể hiện **hai hợp đồng hoàn toàn khác**:

- `(int) longValue`: giữ 32 bit thấp và có thể thay đổi dấu/giá trị.
- `Math.clamp(longValue, min, max)`: trả cận gần nhất nếu vượt, giữ giá trị khi nằm trong đoạn.
- `Math.toIntExact(longValue)`: yêu cầu giá trị nằm trong miền `int`, nếu không sẽ **ném `ArithmeticException`**.

Nếu yêu cầu là “giữ nguyên chính xác hoặc báo lỗi”, `Math.toIntExact` phù hợp hơn clamp. Nếu yêu cầu là “luôn cho ra một chỉ số hoặc mức trong giới hạn”, clamp đúng hơn.

### Cận không hợp lệ phải bị từ chối

```java
// Java 21+:
// Math.clamp(5L, 10, 0); // IllegalArgumentException vì 10 > 0
```

Hàm clamp chuẩn **không tự tráo min và max**. Nếu ứng dụng cho phép hai cận bị đảo và muốn đảo chúng, đó là bước **chuẩn hóa dữ liệu nghiệp vụ** riêng; cần định nghĩa rõ trước khi gọi API.

## 11. Clamp float/double: NaN và signed zero không được bỏ qua

Với JDK 21+:

```java
double result = Math.clamp(1.4, 0.0, 1.0);
System.out.println(result); // 1.0
```

Tuy nhiên floating-point cần kiểm tra ràng buộc đặc biệt:

**1. Nếu chính `value` là NaN**, kết quả vẫn là NaN:

```java
// Java 21+:
double result = Math.clamp(Double.NaN, 0.0, 1.0);
System.out.println(Double.isNaN(result)); // true
```

**2. Nếu một trong hai cận là NaN**, hàm ném `IllegalArgumentException`:

```java
// Java 21+:
// Math.clamp(0.5, Double.NaN, 1.0); // IllegalArgumentException
```

**3. Nếu `min > max`**, hàm ném `IllegalArgumentException`. Đây là phần kiểm tra hợp đồng không nên bỏ qua khi tự viết helper cho Java 17.

**4. Zero có dấu được phân biệt theo thứ tự của clamp.**

```java
// Java 21+:
double z = Math.clamp(-0.0, +0.0, 1.0);
System.out.println(z); // 0.0 (zero dương)
```

Khi cận dưới là `+0.0` và giá trị là `-0.0`, việc clamp nâng lên `+0.0`. Đây là điểm khác với việc tự viết:

```java
double naive = -0.0;
if (naive < +0.0) naive = +0.0; // điều kiện false!
```

Vì `-0.0 < +0.0` trả false, cách so sánh thuần `<`/`>` có thể **không tái tạo đúng** kết quả `Math.clamp`.

**5. Một khoảng với `min=+0.0` và `max=-0.0` được xem là không hợp lệ**, dù `+0.0 == -0.0` là true theo phép so sánh số học:

```java
// Java 21+:
// Math.clamp(0.0, +0.0, -0.0); // IllegalArgumentException
```

Với cận hợp lệ `min=-0.0`, `max=+0.0`, đoạn này cho phép cả hai dạng zero theo quy tắc signed-zero của floating-point API.

### Infinity cũng là đối số có quy tắc rõ ràng

Cận có thể là ±Infinity nếu thứ tự hợp lệ; ví dụ `[-Infinity,+Infinity]` là đoạn đầy đủ theo thứ tự của số floating-point không phải NaN.

Nếu input là `+Infinity` và cận trên hữu hạn, clamp trả cận trên. Nhưng nếu application yêu cầu đầu vào **phải hữu hạn**, clamp không thay thế việc `Double.isFinite(x)` trước đó (đặc biệt nếu cận cho phép Infinity).

## 12. Java 17: viết helper clamp khi API chưa có

Một helper cho số nguyên trên Java 17:

```java
static int clampInt17(int value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min > max");
    }
    return Math.max(min, Math.min(value, max));
}
```

Đây là **phương thức minh họa**, cần nằm trong class. Nó thực hiện bão hòa như định nghĩa clamp cho kiểu `int`, có kiểm tra cận hợp lệ.

Nếu cần thu hẹp `long` thành `int` có bão hòa trên Java 17, dùng helper phù hợp:

```java
static int clampLongToInt17(long value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min > max");
    }
    if (value < min) return min;
    if (value > max) return max;
    return (int) value; // an toàn: đã chứng minh nằm giữa cận int
}
```

Đây là phiên bản dễ đọc của ý tưởng overload `Math.clamp(long,int,int)` trên Java 21+. Các phép so sánh mở rộng cận `int` lên `long`; cast cuối **chỉ xảy ra khi giá trị đã nằm trong miền cận int**.

### Có nên tự viết float/double bằng Math.min/max?

Có thể viết:

```java
double candidate = Math.max(min, Math.min(value, max));
```

trong ngữ cảnh đã bảo đảm cận hợp lệ, nhưng **không coi đây là drop-in replacement đầy đủ cho Math.clamp trên mọi trường hợp** nếu chưa thêm quy tắc kiểm tra NaN cận, cận đảo và signed zero. Với JDK 21+, ưu tiên API chuẩn khi cần chính xác ngữ nghĩa; với Java 17, xác định chính sách xử lý số thực đặc biệt một cách rõ ràng rồi kiểm thử.

### Tránh bẫy clamp rồi đổi kiểu

```java
// Cần thu hẹp long an toàn trên Java 17:
long total = 5_000_000_000L;
int limited = clampLongToInt17(total, 0, 100);
// limited == 100
```

Đừng làm:

```java
// Sai thứ tự:
// int limited = clampInt17((int) total, 0, 100);
```

Vì cast `long → int` có thể mất bit **trước** khi clamp, làm giá trị đi theo một nhánh sai.

## 13. Đọc overload cho đúng: literal, promotion và kiểu trả về

Java chọn một overload dựa trên **kiểu compile-time của đối số**, không chỉ giá trị hiện tại.

```java
int i = 5;
long l = 5L;

int small = Math.abs(i);  // overload int -> int
long big = Math.abs(l);   // overload long -> long
```

Trong `min/max`, khi trộn `int` và `long`, overload `long` có thể được chọn:

```java
int a = 100;
long b = 200L;
long larger = Math.max(a, b); // 200L
```

Với `signum`, chỉ overload `float` và `double` có trong `Math`/`StrictMath`. Muốn nhận lại số nguyên −1/0/1, dùng `Integer.signum` hoặc `Long.signum`.

Với `clamp` trên JDK 21+:

```java
// long, int, int => kết quả int:
int first = Math.clamp(15L, 0, 10);  // 10

// long, long, long => kết quả long:
long second = Math.clamp(15L, 0L, 10L); // 10

// double, double, double => kết quả double:
double third = Math.clamp(1.5, 0.0, 1.0); // 1.0
```

Nhấn mạnh: **kiểu literal** `0`, `0L` và `0.0` có thể ảnh hưởng phương thức nào được gọi và kiểu của biểu thức kết quả.

Tránh tạo code “linh hoạt” bằng nhiều cast tùy tiện như `Math.clamp((int) someLong,... )`: đoạn cast có thể đã mất dữ liệu trước khi lời gọi bắt đầu.

## 14. Chọn hàm đúng theo mục tiêu nghiệp vụ

| Mục tiêu | Phương án |
| --- | --- |
| Trị tuyệt đối float/double, chấp nhận +0.0 và NaN | `Math.abs(x)` |
| Trị tuyệt đối int/long nhưng không cho phép tràn | `Math.absExact(x)` (Java 15+) |
| Trị tuyệt đối của mọi int theo miền long | `Math.abs((long) x)` |
| Trị tuyệt đối của mọi long kể cả MIN_VALUE theo giá trị toán học | `BigInteger.valueOf(x).abs()` |
| Chọn nhỏ nhất/lớn nhất giữa hai số nguyên | `Math.min/max` |
| Chọn nhỏ/lớn floating-point theo quy tắc Java, NaN lan truyền | `Math.min/max` |
| Phân loại dấu float/double, giữ ±0.0 | `Math.signum` |
| Phân loại dấu int/long thành −1/0/1 | `Integer.signum`/`Long.signum` |
| Bão hòa trong đoạn với JDK 21+ | `Math.clamp`/`StrictMath.clamp` |
| Bão hòa trên Java 17 | Helper có kiểm tra cận |
| Từ chối số ngoài miền thay vì sửa giá trị | So sánh cận rồi ném lỗi; dùng `Math.toIntExact` khi chuyển long → int |
| Lấy chỉ số modulo không âm | `Math.floorMod(x,size)` với size dương |

Ba chiến lược **không tương đương** cần phân biệt:

1. **Clamp/saturation**: kéo về cận nếu vượt.
2. **Checked conversion**: từ chối dữ liệu vượt miền.
3. **Modulo/wraparound**: lấy phần dư hoặc giữ bit thấp.

Một giá trị ngoài miền có thể có cách xử lý hợp lý khác nhau tùy yêu cầu. Hãy ghi rõ hợp đồng dữ liệu thay vì chọn theo tên API “nghe an toàn”.

## 15. Chương trình Java 17+ chạy độc lập

Tạo file **`MathBoundsJava17Demo.java`**:

```java
public class MathBoundsJava17Demo {
    private static int clampInt17(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return Math.max(min, Math.min(value, max));
    }

    private static int clampLongToInt17(long value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        if (value < min) return min;
        if (value > max) return max;
        return (int) value;
    }

    public static void main(String[] args) {
        System.out.println("abs(-7) = " + Math.abs(-7));
        System.out.println("abs(INT_MIN) = "
                + Math.abs(Integer.MIN_VALUE));

        try {
            Math.absExact(Integer.MIN_VALUE);
        } catch (ArithmeticException ex) {
            System.out.println("absExact(INT_MIN) = rejected");
        }

        System.out.println("wide abs(INT_MIN) = "
                + Math.abs((long) Integer.MIN_VALUE));
        System.out.println("min(7,12) = " + Math.min(7, 12));
        System.out.println("max(7,12) = " + Math.max(7, 12));

        System.out.println("min(+0,-0) = "
                + Math.min(+0.0, -0.0));
        System.out.println("max(+0,-0) = "
                + Math.max(+0.0, -0.0));
        System.out.println("min(NaN,1) is NaN = "
                + Double.isNaN(Math.min(Double.NaN, 1.0)));

        System.out.println("signum(-3.5) = "
                + Math.signum(-3.5));
        System.out.println("signum(-0.0) = "
                + Math.signum(-0.0));
        System.out.println("int signum(-42) = "
                + Integer.signum(-42));
        System.out.println("strict abs(-7) = "
                + StrictMath.abs(-7));

        System.out.println("clamp17(-5) = "
                + clampInt17(-5, 0, 10));
        System.out.println("clamp17(7) = "
                + clampInt17(7, 0, 10));
        System.out.println("clamp17(20) = "
                + clampInt17(20, 0, 10));
        System.out.println("long-to-int clamp17 = "
                + clampLongToInt17(5_000_000_000L, 0, 100));

        try {
            clampInt17(5, 10, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid bounds = rejected");
        }

        System.out.println("floorMod(INT_MIN,10) = "
                + Math.floorMod(Integer.MIN_VALUE, 10));
    }
}
```

Biên dịch theo API Java 17 ngay cả khi máy cài một JDK mới:

```bash
javac --release 17 MathBoundsJava17Demo.java
java MathBoundsJava17Demo
```

**Stdout dự kiến:**

```text
abs(-7) = 7
abs(INT_MIN) = -2147483648
absExact(INT_MIN) = rejected
wide abs(INT_MIN) = 2147483648
min(7,12) = 7
max(7,12) = 12
min(+0,-0) = -0.0
max(+0,-0) = 0.0
min(NaN,1) is NaN = true
signum(-3.5) = -1.0
signum(-0.0) = -0.0
int signum(-42) = -1
strict abs(-7) = 7
clamp17(-5) = 0
clamp17(7) = 7
clamp17(20) = 10
long-to-int clamp17 = 100
invalid bounds = rejected
floorMod(INT_MIN,10) = 2
```

Giải thích:

- `Math.abs(Integer.MIN_VALUE)` không đủ miền dương và trả cùng số âm; `absExact` phát hiện trường hợp ấy.
- Cast `int → long` trước khi `abs` giữ đủ miền để nhận `2147483648`.
- `min/max` của floating-point xử lý signed zero và NaN khác phép so sánh số học thông thường.
- `signum(-0.0)` giữ dấu zero, `Integer.signum` trả primitive int.
- Hai helper cho Java 17 làm clamp nguyên có kiểm tra cận; helper `long→int` tránh cast thu hẹp quá sớm.
- `floorMod` cho phần dư không âm khi môđun dương.

## 16. Ví dụ riêng cho JDK 21+ với Math.clamp

Chương trình này **không tương thích Java 17**. Tạo file **`MathClampJava21Demo.java`** và biên dịch với `--release 21` hoặc dùng JDK 21+:

```java
public class MathClampJava21Demo {
    public static void main(String[] args) {
        System.out.println("int from long = "
                + Math.clamp(5_000_000_000L, 0, 100));
        System.out.println("long = "
                + Math.clamp(15L, 0L, 10L));
        System.out.println("double = "
                + Math.clamp(1.5, 0.0, 1.0));
        System.out.println("strict = "
                + StrictMath.clamp(-3L, 0L, 10L));

        double zero = Math.clamp(-0.0, +0.0, 1.0);
        System.out.println("negative zero clamped = " + zero);

        double nan = Math.clamp(Double.NaN, 0.0, 1.0);
        System.out.println("NaN value = "
                + Double.isNaN(nan));

        try {
            Math.clamp(5L, 10, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("reversed bounds = rejected");
        }

        try {
            Math.clamp(0.5, Double.NaN, 1.0);
        } catch (IllegalArgumentException ex) {
            System.out.println("NaN bound = rejected");
        }

        try {
            Math.clamp(0.0, +0.0, -0.0);
        } catch (IllegalArgumentException ex) {
            System.out.println("signed zero bounds = rejected");
        }
    }
}
```

Lệnh:

```bash
javac --release 21 MathClampJava21Demo.java
java MathClampJava21Demo
```

**Stdout dự kiến (JDK 21+):**

```text
int from long = 100
long = 10
double = 1.0
strict = 0
negative zero clamped = 0.0
NaN value = true
reversed bounds = rejected
NaN bound = rejected
signed zero bounds = rejected
```

Đây là hai bài demo **riêng biệt theo baseline JDK**, không trộn lẫn. Khi một dự án còn bắt buộc Java 17, hãy dùng chương trình thứ nhất hoặc helper tự viết; không gọi API clamp 21 rồi mong runtime 17 tự hiểu.

## 17. Lỗi phổ biến và cách sửa

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`Math.abs` luôn không âm” | `abs(Integer.MIN_VALUE)` hoặc `abs(Long.MIN_VALUE)` vẫn âm do overflow |
| “Ép kết quả abs int sang long sẽ sửa lỗi” | Phải ép **đầu vào** sang long trước khi `abs` |
| “absExact có trên mọi JDK cũ” | Có từ Java 15; nếu target thấp hơn phải tự kiểm tra |
| “`Math.signum(int)` là phương thức chuẩn” | Dùng `Integer.signum(int)`; Math.signum chỉ có float/double |
| “`Math.min` với NaN trả số còn lại” | `Math.min/max` cho float/double có NaN thì trả NaN |
| “+0.0 và -0.0 không thể phân biệt bởi min/max” | `min(+0,-0)=-0.0`, `max(+0,-0)=+0.0` |
| “`Math.clamp` có trong Java 17” | API được bổ sung ở Java 21 |
| “`Math.clamp` chỉ là viết tắt của một lệnh cast” | Clamp bão hòa theo biên, cast thu hẹp cắt bit |
| “JDK 21 có overload clamp(int,int,int) riêng” | Overload nguyên là `(long,int,int)` và `(long,long,long)`; int có thể widen |
| “Clamp số thực có thể dùng comparator < > mà không xét zero” | So sánh < > không phân biệt -0.0 và +0.0 |
| “Clamp tự lọc NaN” | NaN ở **value** được giữ; NaN ở **bound** gây `IllegalArgumentException` |
| “min lớn hơn max thì Java sẽ tự đảo” | `Math.clamp` ném `IllegalArgumentException` |
| “Math.max(x,0) ngăn x là NaN” | Không; NaN lan truyền khi dùng overload float/double |
| “StrictMath sẽ sửa overflow hoặc kiểu không hợp lệ” | StrictMath cũng tuân hợp đồng của phép toán được gọi |
| “Clamp luôn là validation dữ liệu” | Nó thay giá trị ngoài miền bằng biên; nếu muốn từ chối cần validate riêng |

## 18. Bài tập có đáp án

**Bài 1.** `Math.abs(Integer.MIN_VALUE)` trả gì? Tại sao?  
**Lời giải:** `-2147483648` vì `+2147483648` vượt miền `int`. Dùng `Math.absExact` để phát hiện hoặc `Math.abs((long) x)` để nhận độ lớn đúng của mọi int.

**Bài 2.** `Math.min(+0.0,-0.0)` và `Math.max(+0.0,-0.0)` có cùng kết quả không?  
**Lời giải:** Không. Lần lượt là `-0.0` và `+0.0`, dù `+0.0 == -0.0` là true.

**Bài 3.** `Math.signum(-0.0)` và `Integer.signum(0)` trả gì?  
**Lời giải:** `-0.0` kiểu double và `0` kiểu int. Hai phương thức không hoàn toàn cùng kiểu/hợp đồng signed zero.

**Bài 4.** `Math.min(Double.NaN,5.0)` trả 5 hay NaN?  
**Lời giải:** NaN. Dùng `Double.isNaN` để kiểm tra; không giả định NaN bị bỏ qua.

**Bài 5.** Một chương trình Java 17 viết `Math.clamp(150L,0,100)` có biên dịch với `--release 17` không?  
**Lời giải:** Không. `Math.clamp` có từ JDK 21. Trên Java 17, dùng helper clamp kiểm tra cận.

**Bài 6.** Trên JDK 21+, `Math.clamp(5_000_000_000L,0,100)` trả kiểu gì và giá trị nào?  
**Lời giải:** Kiểu `int`, giá trị **100** qua overload `(long,int,int)`.

**Bài 7.** `Math.clamp(-0.0,+0.0,1.0)` trả zero nào trên JDK 21+?  
**Lời giải:** `+0.0` vì clamp xét thứ tự signed zero và không để −0.0 thấp hơn cận dưới +0.0.

**Bài 8.** Trên JDK 21+, `Math.clamp(0.0,+0.0,-0.0)` có hợp lệ không?  
**Lời giải:** Không. Cặp cận +0.0 và −0.0 theo thứ tự này bị xem là đảo; ném `IllegalArgumentException`.

**Bài 9.** Với `long x = Long.MIN_VALUE` và cần độ lớn dương chính xác, có đủ dùng `Math.abs(x)` không?  
**Lời giải:** Không. Cần `BigInteger.valueOf(x).abs()` hoặc một biểu diễn không bị giới hạn miền `long`.

**Bài 10.** Một hàm cần **từ chối** số lượng ngoài 0..100. Dùng clamp có phù hợp không?  
**Lời giải:** Không nếu hợp đồng yêu cầu từ chối. Clamp sẽ thay mọi giá trị vượt miền bằng 0 hoặc 100; phải kiểm tra đầu vào và báo lỗi.

**Bài 11.** Khi `Integer.MIN_VALUE` được đưa vào công thức chỉ số `Math.abs(hash)%10`, điều gì xảy ra?  
**Lời giải:** `Math.abs(hash)` còn là `Integer.MIN_VALUE`; toán tử `%` có thể cho kết quả âm `-8`. Dùng `Math.floorMod(hash,10)` nếu mục tiêu là modulo không âm.

**Bài 12.** Vì sao một helper `Math.max(min,Math.min(value,max))` không hoàn toàn thay thế `Math.clamp` nếu thiếu kiểm tra cận?  
**Lời giải:** Helper đó không tự ném lỗi khi min>max hoặc bound NaN theo hợp đồng của `Math.clamp`. Với floating-point còn phải chú ý ±0.0 và special values.

## 19. Checklist khi đọc code Math/StrictMath

1. Xác định **JDK tối thiểu** mà code phải chạy: Java 17 không có `clamp`, Java 21+ có.
2. Xác định **kiểu overload**: int, long, float, double; đặc biệt clamp nguyên có overload `long,int,int`.
3. Với `abs(int/long)`, kiểm tra giá trị `MIN_VALUE` và quyết định **cho overflow, ném lỗi hay đổi kiểu**.
4. Với `min/max(float/double)`, kiểm tra hành vi **NaN** và **signed zero** nếu chúng có thể xuất hiện.
5. Với `signum`, phân biệt `Math.signum(float/double)` với `Integer/Long.signum`.
6. Với `clamp`, kiểm tra ràng buộc **min <= max**, cộng thêm quy tắc NaN/signed zero cho floating-point.
7. Không clamp sau khi đã **cast hẹp bị mất dữ liệu** hoặc sau phép tính tràn.
8. Phân biệt **saturation, exact conversion và modulo**; chọn theo hợp đồng nghiệp vụ.
9. Nếu dùng helper Java 17, kiểm thử biên, loại NaN và mô tả rõ cách xử lý bounds không hợp lệ.
10. Kiểm thử ví dụ trên **đúng phiên bản Java mục tiêu** bằng `javac --release N` khi khả thi.
11. Với API cần giữ giá trị nguyên vượt long, cân nhắc `BigInteger` thay vì cố ép về phạm vi.
12. Trong code review, ưu tiên **ý định rõ ràng** hơn những biểu thức min/max lồng nhau rất khó đọc.

### Tài liệu tham khảo theo phiên bản

- [Math – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html)
- [StrictMath – Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html)
- [Math – Java SE 21 (bao gồm clamp)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [StrictMath – Java SE 21 (bao gồm clamp)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StrictMath.html)

**Ghi nhớ:** `abs`, `min`, `max` và `signum` có mặt trên Java 17, nhưng phải tôn trọng các biên số nguyên và quy tắc IEEE 754. `Math.clamp`/`StrictMath.clamp` chỉ xuất hiện từ **JDK 21**, có kiểm tra bounds và đặc tả rõ cho NaN/±0.0. Khi chọn API, hãy kiểm tra **kiểu dữ liệu**, **mục tiêu xử lý vượt miền**, và **phiên bản JDK** thay vì đoán từ tên hàm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-002">pow, sqrt, cbrt và lựa chọn phép tính phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Math.pow, Math.sqrt, Math.cbrt và cách chọn phép tính phù hợp trong Java

Trong Java Core, ba phương thức **`Math.pow`**, **`Math.sqrt`** và **`Math.cbrt`** có vẻ gần nhau vì đều liên quan đến lũy thừa hoặc khai căn. Tuy nhiên, chúng **không hoàn toàn thay thế cho nhau**. Sự khác nhau nằm ở **ngữ nghĩa toán học**, **miền giá trị hợp lệ**, **hành vi với số âm và zero có dấu**, **giới hạn `double`**, **độ chính xác** và khả năng chọn một thuật toán chuyên dụng tốt hơn.

Ví dụ:

```java
double square = Math.pow(3.0, 2.0); // 9.0
double root2 = Math.sqrt(9.0);      // 3.0
double root3 = Math.cbrt(-8.0);     // -2.0

double surprise = Math.pow(-8.0, 1.0 / 3.0);
// surprise là NaN, không phải -2.0.
```

Đoạn mã trên là **mảnh ví dụ** có thể đặt trong một phương thức. Với căn bậc ba âm, `Math.cbrt` biết rõ đó là **căn thực bậc ba**; `Math.pow` chỉ nhìn thấy cơ số âm và một số mũ `double` không nguyên.

Bài học thuộc nhóm **Math và StrictMath**: hiểu cách API được thiết kế và chọn đúng công cụ trước khi tối ưu hiệu năng hoặc sửa những sai số dấu phẩy động.

## 1. Tổng quan API và giá trị trả về

| Phép tính | Phương thức | Kiểu nhận/trả | Ý nghĩa |
| --- | --- | --- | --- |
| Lũy thừa bất kỳ | `Math.pow(double a, double b)` | Hai `double` → `double` | Tính `a^b` theo quy tắc dấu phẩy động |
| Căn bậc hai | `Math.sqrt(double a)` | `double` → `double` | Căn bậc hai thực không âm của `a` |
| Căn bậc ba | `Math.cbrt(double a)` | `double` → `double` | Căn bậc ba thực, kể cả `a` âm |
| Khoảng cách hai chiều | `Math.hypot(double x, double y)` | Hai `double` → `double` | Tính `sqrt(x*x+y*y)` theo cách tránh overflow/underflow trung gian |
| Các hàm tương ứng | `StrictMath.pow/sqrt/cbrt` | Cũng là `double` | Có hợp đồng về khả năng tái lập kết quả theo quy định `StrictMath` |

Tất cả đều là **phương thức static** của lớp `java.lang.Math` hoặc `java.lang.StrictMath`; không cần import riêng nếu gọi bằng tên lớp. Với ba hàm đầu, **không có overload trả `int` hoặc `long`** cho kết quả lũy thừa hoặc căn. Ví dụ:

```java
double answer = Math.sqrt(25);   // 5.0, dù đối số viết bằng literal int.
double power = Math.pow(2, 10);  // 1024.0, không phải kiểu long.
```

Hai lời gọi này nhận literal `int` và tự **widen** thành `double` trước khi gọi hàm. Đừng dựa vào cách viết `Math.pow(2, 10)` để suy ra kết quả là số nguyên chính xác tùy ý: kiểu trả về luôn là `double`.

## 2. `Math.pow(base, exponent)`: phép lũy thừa tổng quát

`Math.pow(a,b)` biểu diễn phép `a` mũ `b`, trong đó cả cơ số và số mũ là `double`. Với các trường hợp thông dụng:

```java
double a = Math.pow(2.0, 10.0);  // 1024.0
double b = Math.pow(10.0, -2.0); // 0.01 (xấp xỉ nhị phân)
double c = Math.pow(9.0, 0.5);   // 3.0 với ví dụ này
double d = Math.pow(-2.0, 3.0);  // -8.0
double e = Math.pow(-2.0, 4.0);  // 16.0
```

Các dòng ở trên là mảnh mã dùng trong phương thức. Điểm đáng chú ý là số mũ được **truyền và xử lý như `double`**, chứ không phải “số nguyên tổng quát”: `Math.pow` không hứa rằng mọi phép lũy thừa số nguyên sẽ tạo ra một số nguyên **chính xác**.

Với số mũ nguyên và cơ số nguyên nhỏ, kết quả có thể được biểu diễn chính xác; với số rất lớn, số mũ không nguyên hoặc kết quả gần biên của `double`, phép tính sẽ chịu quy tắc làm tròn và giới hạn của dấu phẩy động.

**Không dùng dấu `^` làm toán tử lũy thừa trong Java.** `2 ^ 3` là phép **XOR theo bit** trên hai số nguyên và cho kết quả 1, không phải 8. Muốn mũ 3, dùng `Math.pow(2,3)`, nhân liên tiếp khi phù hợp hoặc một hàm lũy thừa số nguyên được thiết kế riêng.

## 3. `Math.sqrt(x)`: căn bậc hai và miền xác định

`Math.sqrt(x)` tìm **căn bậc hai không âm** của `x` khi `x` hữu hạn và không âm.

```java
double a = Math.sqrt(9.0);    // 3.0
double b = Math.sqrt(2.0);    // Số double gần căn 2.
double c = Math.sqrt(0.0);    // +0.0
double d = Math.sqrt(-4.0);   // NaN.
```

Với số âm khác zero, **không tồn tại căn bậc hai thực**, do đó `Math.sqrt(-4.0)` trả **`Double.NaN`**. Java không tự chuyển sang miền **số phức** và cũng không tự ném `ArithmeticException`. Nếu ứng dụng cần căn bậc hai phức, phải dùng thư viện số phức và kiểu dữ liệu có thể chứa phần thực/ảo.

`Math.sqrt` có hợp đồng **correctly rounded**: kết quả là số `double` gần nhất với căn toán học chính xác theo quy tắc làm tròn được quy định. Đây là một điểm mạnh của hàm chuyên dụng; không nên giả định `Math.pow(x,0.5)` luôn có toàn bộ cùng hợp đồng độ chính xác và mọi trường hợp biên.

Chú ý zero có dấu:

```java
double negZero = -0.0;
double root = Math.sqrt(negZero);    // -0.0
boolean sameBits = Double.doubleToRawLongBits(root)
        == Double.doubleToRawLongBits(negZero); // true
```

Theo IEEE 754, `-0.0` và `+0.0` có mẫu bit khác nhau mặc dù `-0.0 == +0.0` là `true`. `Math.sqrt(-0.0)` **giữ nguyên âm zero**, không trả NaN như khi đầu vào là `-4.0`.

## 4. `Math.cbrt(x)`: căn bậc ba thực, kể cả số âm

`Math.cbrt(x)` tính **căn bậc ba thực** của `x`. Khác căn bậc hai, một số âm hữu hạn vẫn có căn bậc ba thực:

```java
double a = Math.cbrt(27.0);  // 3.0
double b = Math.cbrt(-8.0);  // -2.0
double c = Math.cbrt(0.0);   // +0.0
double d = Math.cbrt(-0.0);  // -0.0
```

Ví dụ `(-2)^3 = -8`, nên căn bậc ba thực của `-8` là `-2`. `Math.cbrt` cũng **giữ dấu của zero** và có hành vi với Infinity/NaN theo quy tắc API. Nó có hợp đồng độ chính xác **trong vòng 1 ULP** với yêu cầu liên quan về tính đơn điệu gần đúng, khác với phát biểu `Math.sqrt` được làm tròn đúng.

Chính vì có miền âm, hàm chuyên biệt `cbrt` thường là lựa chọn đúng hơn `pow(x, 1.0/3.0)` để tìm căn bậc ba thực, kể cả khi `x` dương: nó thể hiện chính xác **ý định toán học** và được thiết kế cho phép toán ấy.

## 5. Vì sao `Math.pow(-8.0, 1.0 / 3.0)` là NaN?

Trong toán học thực, căn bậc ba của -8 là -2. Nhưng ở Java:

```java
double exponent = 1.0 / 3.0;
double viaPow = Math.pow(-8.0, exponent); // NaN.
double viaCbrt = Math.cbrt(-8.0);        // -2.0.
```

Nguyên nhân gồm hai ý:

1. `1.0 / 3.0` là một số `double` **xấp xỉ 1/3**, không phải phân số chính xác với tử số/mẫu số tách biệt.
2. Với **cơ số âm hữu hạn** và **số mũ hữu hạn không nguyên**, `Math.pow` theo định nghĩa `double` của Java trả **NaN**. Nó không suy luận rằng một số mũ gần `1/3` có nghĩa “lấy căn bậc ba thực rồi giữ dấu”.

Nếu chỉ thay cách viết thành `Math.pow(-8.0, 0.3333333333333333)`, kết quả vẫn là NaN vì số mũ đó không phải số nguyên. Đây là sai **lựa chọn API**, không phải sai số nhỏ do in thập phân.

Không nên tự sửa bằng `Math.abs(x)` rồi luôn gắn lại dấu âm cho **mọi** số mũ thực: quy tắc đúng còn phụ thuộc bản chất toán học của số mũ (ví dụ phân số có tử/mẫu chẵn/lẻ), miền giá trị và khả năng biểu diễn. Nếu muốn căn bậc ba thực thì `Math.cbrt` chính là API phù hợp.

## 6. Tính bình phương, lập phương và lựa chọn `pow` hay nhân trực tiếp

Với các mũ cố định nhỏ, phép nhân tường minh dễ đọc và có thể tránh chi phí gọi một phép lũy thừa tổng quát:

```java
double x = 2.5;
double square = x * x;
double cube = x * x * x;
double generic = Math.pow(x, 7.0);
```

Tuy nhiên, **đừng mặc định phép nhân trực tiếp và `Math.pow(x,2.0)` cho mẫu bit giống nhau ở mọi input**. Chúng có thể có các quy tắc đặc biệt hoặc sai khác làm tròn/thuật toán; trong các bài toán yêu cầu kết quả bit-for-bit hay một tiêu chuẩn số học cụ thể, hãy kiểm thử trên miền dữ liệu thực tế.

Về hiệu năng, không nên suy luận “`x*x` luôn nhanh hơn” hay “`Math.pow` luôn được CPU tối ưu” mà không đo. JVM có thể tối ưu mã khác nhau theo môi trường; khi hiệu năng quan trọng, benchmark với JMH và dữ liệu đại diện.

**Quan trọng hơn:** với **số nguyên**, dùng `int * int` vẫn có thể tràn. Chuyển qua `Math.pow` không phải giải pháp chính xác; kết quả `double` chỉ hữu hạn độ chính xác.

## 7. Chọn `pow` cho mũ số nguyên có thể làm mất độ chính xác

`double` có 53 bit significand hiệu dụng, nên **không biểu diễn chính xác mọi số nguyên lớn hơn `2^53`**. Ví dụ:

```java
double n = Math.pow(2.0, 53.0);     // 9007199254740992.0
boolean lost = (n + 1.0 == n);     // true.
```

Số toán học `2^53 + 1` không có biểu diễn `double` chính xác. Khi `n + 1.0`, kết quả làm tròn về chính `n`. Do đó **không nên** dùng `(long) Math.pow(base, exponent)` như giải pháp luôn chính xác cho lũy thừa số nguyên:

- Nếu phép lũy thừa tạo ra số nguyên không lưu chính xác trong `double`, ép về `long` **không thể khôi phục bit đã mất**.
- Nếu kết quả `double` vượt miền `long`, phép chuyển từ số thực sang số nguyên theo quy tắc Java không giống phép tính nguyên chính xác; với Infinity và giá trị rất lớn, kết quả chuyển kiểu có thể bị bão hòa.
- Nếu số mũ âm, giá trị lũy thừa thường không phải số nguyên, nên ép kiểu dễ **cắt phần thập phân**.
- Nếu cơ số âm với số mũ thực không nguyên, phép `pow` có thể trả NaN.

Dùng `Math.pow` khi dữ liệu và đầu ra có ngữ nghĩa **dấu phẩy động**. Khi cần số nguyên tuyệt đối chính xác, hãy chọn phép toán nguyên với kiểm tra tràn hoặc `BigInteger.pow`.

## 8. Lũy thừa số nguyên chính xác: `Math.multiplyExact` và `BigInteger.pow`

Một phương pháp cho `long` là dùng **exponentiation by squaring** (bình phương liên tiếp), kiểm tra tràn ở mỗi phép nhân:

```java
static long powExact(long base, int exponent) {
    if (exponent < 0) {
        throw new IllegalArgumentException("negative exponent");
    }
    long result = 1L;
    long factor = base;
    int remaining = exponent;
    while (remaining > 0) {
        if ((remaining & 1) != 0) {
            result = Math.multiplyExact(result, factor);
        }
        remaining >>>= 1;
        if (remaining > 0) {
            factor = Math.multiplyExact(factor, factor);
        }
    }
    return result;
}
```

Đây là **phương thức nằm trong lớp Java**, cũng xuất hiện trong chương trình hoàn chỉnh bên dưới. Thuật toán chạy trong `O(log exponent)` phép bình phương/nhân, không phải nhân một cách tuyến tính theo số mũ. `Math.multiplyExact` ném `ArithmeticException` khi tích nằm ngoài miền `long`.

```java
long valid = powExact(3L, 10); // 59049.
// powExact(2L, 63) ném ArithmeticException vì 2^63 > Long.MAX_VALUE.
```

Lưu ý phương thức chủ động **không hỗ trợ số mũ âm** vì kết quả có thể là phân số. Việc thêm `if (remaining > 0)` trước phép bình phương tiếp theo tránh một phép nhân không còn cần thiết khi thuật toán đã xử lý bit cao nhất; điều này quan trọng với API kiểm tra tràn vì phép trung gian vô ích cũng có thể gây exception.

Nếu đầu ra cần vượt quá `long`, dùng:

```java
java.math.BigInteger a = java.math.BigInteger.valueOf(2);
java.math.BigInteger result = a.pow(100); // Số nguyên 2^100 chính xác.
```

`BigInteger.pow(int exponent)` hỗ trợ số mũ **không âm**, trả giá trị nguyên chính xác trong giới hạn tài nguyên; nó không dùng `double` trung gian. Số mũ âm không phải kết quả `BigInteger` nói chung.

## 9. `sqrt` cho việc kiểm tra số chính phương

Với một `int` hoặc `long`, gọi `Math.sqrt(n)` trả `double`. Nếu mục đích là **hiển thị căn bậc hai xấp xỉ**, cách này rất hợp lý.

Nếu mục đích là kiểm tra **`n` có phải số chính phương chính xác không**, cần cẩn thận:

```java
long n = 81L;
long root = (long) Math.sqrt(n); // 9 với ví dụ này.
boolean square = (root * root == n); // true với n=81.
```

Với `long` rất lớn, vấn đề phức tạp hơn. Một `long` dương có thể không biểu diễn chính xác bằng `double`, phép ép căn sang `long` có thể cần hiệu chỉnh, và `root*root` có thể **tràn** nếu chưa kiểm tra giới hạn. Nếu thực sự cần kiểm tra chính phương cho dữ liệu ở mọi biên `long`, hãy dùng thuật toán căn nguyên có kiểm soát hoặc phép chia an toàn để so sánh.

Với Java có API `Math.sqrt`, **không mặc định** `(long)Math.sqrt(n)` là kết quả căn nguyên chính xác trên mọi `long`. Nếu nghiệp vụ là số nguyên và không chấp nhận sai một đơn vị, hãy định nghĩa rõ quy trình kiểm tra và test các biên gần `Long.MAX_VALUE`.

## 10. `Math.sqrt` và `Math.pow(x,0.5)` khác nhau ở ý định

Về ý nghĩa toán học, với `x >= 0`, `x^0.5` là căn bậc hai. Tuy nhiên, trong Java:

```java
double a = Math.sqrt(9.0);     // 3.0.
double b = Math.pow(9.0, 0.5); // 3.0 trong ví dụ này.
```

Hai cách không nhất thiết có cùng **bảo đảm sai số hay quy tắc các giá trị đặc biệt** cho mọi tổ hợp. `sqrt` được thiết kế cho một phép toán cụ thể và có bảo đảm correctly rounded, nên là lựa chọn mặc định khi bạn thật sự muốn **căn bậc hai**.

Với `Math.pow(x,0.5)`, bạn đang đi qua một hàm lũy thừa tổng quát. Mỗi hàm có tập quy tắc riêng đối với `-0.0`, NaN, Infinity, số mũ đặc biệt, và độ chính xác; cần dựa vào **contract của hàm được gọi**, không dùng biến đổi đại số vô điều kiện để đoán mọi output.

Đối với `x < 0`, cả căn bậc hai thực và `pow` với số mũ `0.5` đều không cho một số thực hữu hạn; Java trả NaN theo trường hợp tương ứng, chứ không tự tạo số phức.

## 11. Trường hợp biên: số âm, zero, Infinity, NaN

| Đầu vào/phép tính | Kết quả minh họa | Giải thích |
| --- | --- | --- |
| `Math.sqrt(-4.0)` | `NaN` | Không có căn bậc hai thực của -4 |
| `Math.sqrt(-0.0)` | `-0.0` | Giữ dấu zero |
| `Math.cbrt(-8.0)` | `-2.0` | Căn bậc ba thực tồn tại |
| `Math.cbrt(-0.0)` | `-0.0` | Giữ dấu zero |
| `Math.pow(-2.0, 3.0)` | `-8.0` | Mũ nguyên lẻ |
| `Math.pow(-2.0, 4.0)` | `16.0` | Mũ nguyên chẵn |
| `Math.pow(-8.0, 1.0/3.0)` | `NaN` | Cơ số âm, mũ hữu hạn không nguyên |
| `Math.pow(Double.NaN, 0.0)` | `1.0` | Quy tắc đặc biệt của `pow`: số mũ 0 |
| `Math.sqrt(Double.POSITIVE_INFINITY)` | `+Infinity` | Căn vô cực dương |
| `Math.cbrt(Double.POSITIVE_INFINITY)` | `+Infinity` | Căn bậc ba vô cực dương |
| `Math.pow(1e200, 2)` | `+Infinity` | Kết quả toán học vượt dải hữu hạn `double` |
| `Math.pow(1e-200, 2)` | `+0.0` | Kết quả quá nhỏ, underflow về zero |

**Lưu ý:** `NaN` không bằng chính nó dưới phép `==`. Để nhận biết NaN, dùng `Double.isNaN(value)` hoặc `Double.isFinite(value)` khi bạn muốn bảo đảm kết quả hữu hạn. Muốn phân biệt `+0.0` và `-0.0` chính xác từng bit, dùng `Double.doubleToRawLongBits` thay vì `==`.

Trong một số nghiệp vụ, đầu vào âm cho `sqrt` cần được **từ chối tường minh** trước khi gọi hàm, thay vì âm thầm tạo NaN rồi truyền qua các bước sau:

```java
static double positiveRoot(double x) {
    if (!Double.isFinite(x) || x < 0.0) {
        throw new IllegalArgumentException("root input must be finite and >= 0");
    }
    return Math.sqrt(x);
}
```

Đây là **phương thức minh họa**, không phải API mặc định của JDK. Việc `x < 0.0` vẫn chấp nhận `-0.0` vì so sánh đó là false; nếu nghiệp vụ cần cấm âm zero, hãy kiểm tra bit dấu riêng. Định nghĩa domain theo yêu cầu ứng dụng, không suy rằng Java sẽ ném exception thay bạn.

## 12. Overflow, underflow và vì sao `Math.hypot` có thể thích hợp hơn `sqrt(x*x+y*y)`

Một công thức toán học phổ biến để tính độ dài vector hai chiều là:

```text
length = sqrt(x^2 + y^2)
```

Nhưng trong `double`:

```java
double x = 1.0e200;
double y = 1.0e200;

double naive = Math.sqrt(x * x + y * y);
double robust = Math.hypot(x, y);
```

Ở công thức `naive`, phép nhân `x*x` đã vượt phạm vi hữu hạn `double` (vì `1e400` quá lớn), nên kết quả trung gian là Infinity. `sqrt(Infinity)` vẫn Infinity, mặc dù độ dài vector toán học khoảng `1.414e200` và **vẫn nằm trong dải double**.

`Math.hypot(x,y)` được thiết kế để giảm nguy cơ **overflow/underflow trung gian** trong bài toán tính độ dài. Trường hợp này nó trả kết quả hữu hạn. Tương tự, với dữ liệu rất nhỏ, phép tính bình phương trực tiếp có thể underflow về 0, trong khi `hypot` có cách tính ổn định hơn.

Do đó, **lựa chọn đúng không phải lúc nào cũng là pow hoặc sqrt**: đôi khi một API chuyên dụng như `Math.hypot` là phù hợp hơn cả chuỗi `Math.sqrt(Math.pow(x,2)+Math.pow(y,2))`.

## 13. Đừng tạo phép nhân số nguyên lớn chỉ vì căn trả double

Đối với `int`, phép nhân có thể tràn **trước** khi kết quả được truyền sang `Math.sqrt`:

```java
int x = 50_000;
int y = x * x;  // -1794967296: overflow int.
double wrong = Math.sqrt(y); // NaN vì y âm.
```

Trong ví dụ, `50_000^2 = 2_500_000_000` vượt `Integer.MAX_VALUE`. Các bit thấp còn lại được Java diễn giải thành một số `int` âm. Khi đưa số âm đó vào `Math.sqrt`, kết quả NaN; nguyên nhân **đầu tiên** là phép nhân nguyên bị tràn, không phải `sqrt` “sai”.

Nếu cần tính bình phương trước dưới dạng `long`:

```java
int x = 50_000;
long exactSquare = (long) x * x;  // 2_500_000_000L.
double root = Math.sqrt(exactSquare); // 50000.0 trong ví dụ.
```

Dấu `(long)` phải có **trước phép nhân**, không phải `(long)(x*x)`; cast sau khi tràn không thể khôi phục kết quả. Nếu phạm vi có thể vượt `long`, dùng `BigInteger` hoặc kiểm tra phạm vi tường minh.

## 14. `Math` hay `StrictMath`?

Cả `Math` và `StrictMath` đều có các phép tính `pow`, `sqrt`, `cbrt` và nhiều phép toán khác. Chúng cung cấp API gần như tương đương về mục đích, nhưng **hợp đồng cài đặt và khả năng tái lập bit** có những điểm khác biệt:

- **`Math`** có thể được triển khai tối ưu theo nền tảng, miễn đáp ứng yêu cầu về độ chính xác và ngữ nghĩa của API.
- **`StrictMath`** hướng tới kết quả tái lập theo cách tính được chuẩn hóa cho nhiều hàm, với các quy định nghiêm ngặt hơn về thuật toán tham chiếu. Một số hàm như `sqrt` có bảo đảm làm tròn chính xác.
- Hai lớp **có thể trả cùng kết quả cho nhiều input**, như `Math.sqrt(9.0)` và `StrictMath.sqrt(9.0)`, nhưng không nên phát biểu mọi lời gọi luôn giống nhau tới từng bit nếu đặc tả không đòi hỏi.

```java
double a = StrictMath.pow(2.0, 10.0); // 1024.0.
double b = StrictMath.sqrt(9.0);      // 3.0.
double c = StrictMath.cbrt(27.0);     // 3.0.
```

Trong ứng dụng thường ngày, `Math` là lựa chọn trực tiếp. Nếu một hệ thống yêu cầu **tính tái lập numerical results qua nhiều môi trường**, cần xem kỹ yêu cầu so sánh bit, JVM và hợp đồng `StrictMath` của từng phép toán; dùng đúng kiểu, thứ tự tính và bộ test tham chiếu. Chỉ thay tên lớp không làm mọi phép tính khác trong ứng dụng tự trở nên deterministic.

Với Java 17, các biểu thức dấu phẩy động có các ràng buộc nhất quán từ nền tảng ngôn ngữ, nhưng điều đó không loại bỏ các sai số làm tròn, những trường hợp đặc biệt hoặc sự khác nhau giữa các thuật toán tính gần đúng.

## 15. Kiểm tra `double` trước và sau khi tính

Một API tính khoảng cách, căn hoặc lũy thừa cho người dùng có thể cần chính sách đầu vào như:

- Cho phép âm hay không? `cbrt` nhận số âm hữu hạn; `sqrt` không có căn thực cho số âm khác zero.
- Cho phép NaN/Infinity hay không? `Double.isFinite` kiểm tra số hữu hạn.
- Có chấp nhận kết quả Infinity nếu phép toán overflow hay không? Nếu không, kiểm tra **đầu ra** sau khi gọi.
- Có chấp nhận underflow thành zero hay không? Với số rất nhỏ, kết quả khác 0 về toán học vẫn có thể thành 0 trong `double`.
- Kết quả có cần chính xác từng đơn vị nguyên hoặc decimal không? Nếu có, tránh lạm dụng `double`/`Math.pow`.
- Có yêu cầu dấu zero hay không? `-0.0` và `+0.0` so sánh `==` nhưng khác bit.
- Có đòi hỏi số thực dương do **nghiệp vụ** không, ngay cả khi toán học cho giá trị âm hợp lệ?

Một ví dụ bảo vệ kết quả:

```java
static double finiteSquareRoot(double value) {
    if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException("invalid square root input");
    }
    double result = Math.sqrt(value);
    if (!Double.isFinite(result)) {
        throw new ArithmeticException("non-finite result");
    }
    return result;
}
```

Đây là phương thức dùng trong lớp ứng dụng, không phải phương thức có sẵn trong `Math`. Hãy chọn loại ngoại lệ và thông báo theo contract API thực tế; không nhất thiết mọi hệ thống phải cấm Infinity hay NaN.

## 16. Chương trình Java 17+ hoàn chỉnh

Lưu mã sau với tên **`PowerRootDemo.java`**:

```java
public class PowerRootDemo {
    static long powExact(long base, int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("negative exponent");
        }
        long result = 1L;
        long factor = base;
        int remaining = exponent;
        while (remaining > 0) {
            if ((remaining & 1) != 0) {
                result = Math.multiplyExact(result, factor);
            }
            remaining >>>= 1;
            if (remaining > 0) {
                factor = Math.multiplyExact(factor, factor);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("pow2_10=" + Math.pow(2.0, 10.0));
        System.out.println("pow9_half=" + Math.pow(9.0, 0.5));
        System.out.println("sqrt9=" + Math.sqrt(9.0));
        System.out.println("cbrt27=" + Math.cbrt(27.0));
        System.out.println("cbrtNegative=" + Math.cbrt(-8.0));
        System.out.println("powNegativeOdd=" + Math.pow(-2.0, 3.0));
        System.out.println("powNegativeEven=" + Math.pow(-2.0, 4.0));
        System.out.println("powNegativeFractionNaN="
                + Double.isNaN(Math.pow(-8.0, 1.0 / 3.0)));
        System.out.println("sqrtNegativeNaN="
                + Double.isNaN(Math.sqrt(-4.0)));
        System.out.println("sqrtMinusZeroBits="
                + (Double.doubleToRawLongBits(Math.sqrt(-0.0))
                == Double.doubleToRawLongBits(-0.0)));
        System.out.println("cbrtMinusZeroBits="
                + (Double.doubleToRawLongBits(Math.cbrt(-0.0))
                == Double.doubleToRawLongBits(-0.0)));
        System.out.println("powNaNExponentZero="
                + Math.pow(Double.NaN, 0.0));
        System.out.println("powOverflowInfinite="
                + Double.isInfinite(Math.pow(1.0e200, 2.0)));
        System.out.println("powUnderflowZero="
                + (Math.pow(1.0e-200, 2.0) == 0.0));
        System.out.println("sqrtInfinity="
                + Math.sqrt(Double.POSITIVE_INFINITY));
        System.out.println("cbrtInfinity="
                + Math.cbrt(Double.POSITIVE_INFINITY));
        System.out.println("sqrtMinPositive="
                + (Math.sqrt(Double.MIN_VALUE) > 0.0));
        System.out.println("cbrtMinPositive="
                + (Math.cbrt(Double.MIN_VALUE) > 0.0));

        double large = 1.0e200;
        System.out.println("naiveHypotInfinite="
                + Double.isInfinite(Math.sqrt(large * large + large * large)));
        System.out.println("hypotFinite="
                + Double.isFinite(Math.hypot(large, large)));

        int x = 50_000;
        System.out.println("intMultiplyOverflow=" + (x * x));
        System.out.println("powExact3_10=" + powExact(3L, 10));
        try {
            System.out.println("powExact2_63=" + powExact(2L, 63));
        } catch (ArithmeticException ex) {
            System.out.println("powExact2_63=ArithmeticException");
        }
        System.out.println("pow2_53PlusOneLost="
                + (Math.pow(2.0, 53.0) + 1.0 == Math.pow(2.0, 53.0)));
        System.out.println("strictSqrt9=" + StrictMath.sqrt(9.0));
        System.out.println("strictCbrt27=" + StrictMath.cbrt(27.0));
    }
}
```

Biên dịch/chạy tại thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 PowerRootDemo.java
java PowerRootDemo
```

Stdout chính xác:

```text
pow2_10=1024.0
pow9_half=3.0
sqrt9=3.0
cbrt27=3.0
cbrtNegative=-2.0
powNegativeOdd=-8.0
powNegativeEven=16.0
powNegativeFractionNaN=true
sqrtNegativeNaN=true
sqrtMinusZeroBits=true
cbrtMinusZeroBits=true
powNaNExponentZero=1.0
powOverflowInfinite=true
powUnderflowZero=true
sqrtInfinity=Infinity
cbrtInfinity=Infinity
sqrtMinPositive=true
cbrtMinPositive=true
naiveHypotInfinite=true
hypotFinite=true
intMultiplyOverflow=-1794967296
powExact3_10=59049
powExact2_63=ArithmeticException
pow2_53PlusOneLost=true
strictSqrt9=3.0
strictCbrt27=3.0
```

Một số điểm nổi bật từ chương trình:

1. `pow2_10=1024.0` chứng minh kết quả của `Math.pow` là **`double`**, dù phép toán tương ứng là lũy thừa số nguyên.
2. `sqrt9`, `cbrt27` minh họa hai căn chuyên dụng; `cbrtNegative=-2.0` nhưng `powNegativeFractionNaN=true` phân biệt ngữ nghĩa căn thực âm.
3. `sqrtNegativeNaN=true` và hai dòng `...MinusZeroBits=true` cho thấy **âm khác zero** không giống **âm zero**.
4. `powNaNExponentZero=1.0` nhắc rằng `pow` có các quy tắc đặc biệt; không thể suy hết bằng quy tắc toán học thực đơn giản.
5. `powOverflowInfinite` và `powUnderflowZero` minh họa giới hạn biểu diễn `double`.
6. `naiveHypotInfinite=true` nhưng `hypotFinite=true` giải thích vì sao nên dùng `Math.hypot` để tính độ dài vector lớn.
7. `intMultiplyOverflow=-1794967296` cho thấy phép nhân `int` có thể tràn; `powExact3_10=59049` và `powExact2_63=ArithmeticException` minh họa phép lũy thừa nguyên **có kiểm tra phạm vi**.
8. `pow2_53PlusOneLost=true` chứng tỏ miền `double` không biểu diễn chính xác mọi số nguyên lớn.
9. Hai dòng `strictSqrt9`, `strictCbrt27` minh họa cú pháp `StrictMath`.

Bằng việc dùng các phương thức `Double.isNaN`/`Double.isInfinite`, chương trình tránh giả định mọi kết quả đều là số hữu hạn thông thường.

## 17. Bảng quyết định: nên dùng phương thức nào?

| Yêu cầu thực tế | Chọn | Lý do |
| --- | --- | --- |
| Tính `a^b` với `double` và mũ bất kỳ trong miền hợp lệ | `Math.pow(a,b)` | Cú pháp tổng quát cho dấu phẩy động |
| Tính căn bậc hai của `x >= 0` | `Math.sqrt(x)` | Hàm chuyên dụng với bảo đảm correctly rounded |
| Tính căn bậc ba thực, kể cả `x < 0` | `Math.cbrt(x)` | Hỗ trợ cơ số âm theo ngữ nghĩa căn bậc ba |
| Tính `x*x` hoặc `x*x*x` trên `double` | Nhân tường minh khi phù hợp | Rõ mũ nhỏ; kiểm chứng sai số/hiệu năng nếu quan trọng |
| Tính độ dài `sqrt(x*x+y*y)` với `double` có độ lớn cực đoan | `Math.hypot(x,y)` | Tránh overflow/underflow trung gian không cần thiết |
| Tính `3^10` dưới dạng `long` và báo lỗi nếu vượt `long` | Nhân có `Math.multiplyExact` | Không chuyển qua `double` làm mất đơn vị |
| Tính `2^100` thành số nguyên chính xác | `BigInteger.pow(100)` | Số nguyên lớn tùy ý, không giới hạn ở 64 bit |
| Cần tái lập `pow`/`cbrt` theo contract `StrictMath` | `StrictMath` và QA tương ứng | Lựa chọn ngữ nghĩa kết quả tái lập |
| Cần căn số âm trong miền số phức | Thư viện số phức | `Math.sqrt` chỉ trả `double`, không biểu diễn số phức |
| Cần kiểm tra số nguyên có phải chính phương chính xác | Thuật toán nguyên có kiểm tra biên | `double` có thể làm tròn dữ liệu `long` lớn |

Một lựa chọn đúng không chỉ cho ra “con số gần mong muốn”, mà còn phải phù hợp **miền đầu vào, kiểu kết quả, chính sách sai số và trường hợp lỗi**.

## 18. Lỗi phổ biến khi dùng `pow`, `sqrt`, `cbrt`

| Lỗi/nhầm lẫn | Hậu quả | Cách đúng |
| --- | --- | --- |
| Viết `2 ^ 3` để lấy 2 mũ 3 | Java dùng XOR theo bit → 1 | Dùng `Math.pow(2,3)` hoặc nhân phù hợp |
| Tính căn bậc ba âm bằng `Math.pow(x,1.0/3.0)` | Có thể trả NaN | Dùng `Math.cbrt(x)` |
| Dùng `Math.sqrt(-4)` rồi mong exception | Hàm trả NaN, không ném `ArithmeticException` | Kiểm tra domain hoặc `Double.isNaN` |
| Giả định `sqrt(-0.0)` là NaN | Kết quả giữ `-0.0` | Kiểm tra signed zero khi có yêu cầu |
| Dùng `(long)Math.pow(large, n)` để có số nguyên chính xác | Sai số biểu diễn, overflow/saturation khi chuyển | Dùng `BigInteger.pow` hoặc `multiplyExact` |
| Viết `Math.sqrt(x*x+y*y)` với số rất lớn | Bình phương trung gian thành Infinity | Dùng `Math.hypot` |
| Cast kết quả sau khi `int * int` tràn | Không cứu được kết quả đã sai | Widen trước khi nhân hoặc `Math.multiplyExact` |
| Nghĩ `Math.pow` luôn giữ nguyên từng bit so với nhân trực tiếp | Đường tính/rounding có thể khác | Chọn phép toán có ngữ nghĩa rõ và QA biên |
| Nghĩ `Math.sqrt` luôn trả số nguyên khi input chính phương lớn | Kết quả là `double` | Dùng thuật toán số nguyên khi cần exact |
| Dùng `== Double.NaN` để phát hiện lỗi | NaN không bằng bất kỳ số nào, kể cả chính nó | `Double.isNaN` hoặc `Double.isFinite` |
| Nghĩ mọi `Math` và `StrictMath` đều bit-exact như nhau trên mọi input | Contract về tái lập khác nhau theo hàm | Dựa vào API contract và test mục tiêu |
| Tối ưu trước bằng các mẹo biến đổi đại số | Có thể đổi sai số, zero, Infinity, NaN | Kiểm thử cả miền đặc biệt và yêu cầu chính xác |

## 19. Câu hỏi tự kiểm tra

1. **`Math.pow(2,10)` trả về kiểu nào?** `double`, giá trị `1024.0`. Java nâng các đối số `int` thành `double`.
2. **Có thể dùng `^` để lũy thừa trong Java không?** Không. `^` là XOR.
3. **`Math.sqrt(9)` và `Math.pow(9,0.5)` đều cho 3.0 trong ví dụ, vậy chúng tương đương trong mọi trường hợp không?** Không nên giả định đồng nhất toàn bộ contract về rounding và trường hợp biên.
4. **`Math.sqrt(-4.0)` cho gì?** `NaN`, không phải exception.
5. **Vì sao `Math.cbrt(-8.0)` hợp lệ?** Vì căn bậc ba thực của số âm tồn tại; kết quả là `-2.0`.
6. **Vì sao `Math.pow(-8.0,1.0/3.0)` không trả `-2.0`?** Số mũ `double` hữu hạn nhưng không nguyên; với cơ số âm, `Math.pow` trả NaN theo đặc tả.
7. **`Math.sqrt(-0.0)` có mất bit dấu không?** Không; kết quả vẫn là `-0.0`.
8. **Tính `sqrt(x*x+y*y)` với `x=y=1e200` nên dùng gì?** `Math.hypot(x,y)`, vì bình phương riêng lẻ sẽ overflow.
9. **Dùng `Math.pow(2.0,53)+1.0` có giữ được đơn vị `+1` không?** Không; ở `2^53`, ULP đã là 2.
10. **Nếu muốn lũy thừa số nguyên không chấp nhận tràn, nên dùng gì?** `Math.multiplyExact` trong thuật toán nhân nguyên hoặc `BigInteger.pow`.
11. **`Math.sqrt` và `Math.cbrt` đều xử lý NaN như số thường không?** Không; phải kiểm tra giá trị đặc biệt theo contract dấu phẩy động.
12. **Nên chọn `Math` hay `StrictMath` khi cần tái lập bit?** Xem hợp đồng của từng hàm và kiểm chứng theo môi trường; `StrictMath` hướng tới kết quả tái lập chặt chẽ hơn cho nhiều hàm.

**Ghi nhớ:** `Math.pow` là **lũy thừa `double` tổng quát**, `Math.sqrt` là **căn bậc hai chuyên dụng** và `Math.cbrt` là **căn bậc ba thực kể cả với số âm**. Chọn chúng theo **bản chất bài toán**, không chỉ theo biến đổi đại số. Khi tính độ dài vector dùng `Math.hypot` để hạn chế tràn trung gian; khi tính lũy thừa **số nguyên chính xác**, dùng phép nhân có kiểm tra hoặc `BigInteger.pow`, không đặt niềm tin sai vào `double`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-003">exp, log, log10, expm1 và log1p</a>

<details>
<summary>Xem chi tiết</summary>

# `exp`, `log`, `log10`, `expm1` và `log1p` trong Java

Java cung cấp năm hàm số mũ/logarit thường gặp trong `java.lang.Math` và `java.lang.StrictMath`. Chúng có quan hệ toán học gần nhau, nhưng **không thể dùng thay thế máy móc** trong tính toán dấu phẩy động:

- `Math.exp(x)` tính \(e^x\).
- `Math.log(x)` tính \(\ln(x)\), logarit tự nhiên cơ số \(e\).
- `Math.log10(x)` tính \(\log_{10}(x)\), logarit cơ số 10.
- `Math.expm1(x)` tính \(e^x-1\) **trực tiếp với độ chính xác tốt hơn** khi \(x\) rất gần 0.
- `Math.log1p(x)` tính \(\ln(1+x)\) **trực tiếp với độ chính xác tốt hơn** khi \(x\) rất gần 0.

Cả năm đều nhận một tham số `double` và trả `double`. Nếu truyền `int` hoặc `float`, Java có thể widening sang `double` trước lời gọi. Các hàm không trả giá trị số nguyên chính xác tùy ý; chúng làm việc trong giới hạn IEEE 754 của `double`.

**Mục tiêu học tập:** chọn đúng hàm, hiểu miền xác định và các trường hợp `NaN`/`Infinity`/`-0.0`, tránh mất độ chính xác khi tự viết `exp(x)-1` hoặc `log(1+x)`, và biết lúc nào cần `StrictMath` thay cho `Math`.

## 1. Bảng tổng quan và quan hệ toán học

| Java API | Biểu thức toán học | Điều kiện để kết quả thực hữu hạn thông thường | Trường hợp dễ nhớ |
| --- | --- | --- | --- |
| `Math.exp(x)` | \(e^x\) | \(x\) hữu hạn và không vượt/underflow miền biểu diễn đầu ra | `exp(0.0) = 1.0` |
| `Math.log(x)` | \(\ln(x)\) | \(x > 0\), hữu hạn | `log(1.0) = 0.0` |
| `Math.log10(x)` | \(\log_{10}(x)\) | \(x > 0\), hữu hạn | `log10(1000.0) = 3.0` |
| `Math.expm1(x)` | \(e^x - 1\) | \(x\) hữu hạn, kết quả trong phạm vi | `expm1(0.0) = 0.0` |
| `Math.log1p(x)` | \(\ln(1+x)\) | \(x > -1\), hữu hạn | `log1p(0.0) = 0.0` |

Các điều kiện hữu hạn nêu ở bảng là tình huống **thường có kết quả hữu hạn**; `exp` và `expm1` vẫn có thể tràn hoặc underflow ngay cả khi tham số `double` hữu hạn. `log`, `log10` và `log1p` cũng định nghĩa cách xử lý đầu vào ở **ranh giới miền**, không đơn giản ném exception như phép tính số nguyên.

Cơ số \(e\) xấp xỉ `2.718281828459045`, có hằng `Math.E`:

```java
System.out.println(Math.E);                  // 2.718281828459045
System.out.println(Math.exp(0.0));           // 1.0
System.out.println(Math.log(1.0));           // 0.0
System.out.println(Math.log10(1000.0));      // 3.0
```

Dùng các ví dụ số học quen thuộc để nắm ý nghĩa, nhưng **đừng suy ra mọi phép ghép hàm sẽ cho chính xác tuyệt đối** vì phần lớn phép tính đều làm tròn theo `double`.

## 2. `Math.exp(x)`: hàm mũ cơ số `e`

`Math.exp(x)` tính \(e^x\); kết quả toán học luôn **dương** đối với mọi \(x\) thực hữu hạn. Các tính chất:

- `Math.exp(0.0)` và `Math.exp(-0.0)` đều bằng `1.0`.
- `Math.exp(1.0)` xấp xỉ \(e\).
- `Math.exp(Math.log(a))` xấp xỉ `a` khi `a > 0` và không gặp giới hạn làm tròn/miền.
- `Math.exp(x + y)` tương ứng \(e^x e^y\) về mặt toán học, nhưng hai cách tính trên `double` **có thể khác do sai số hoặc overflow**.

```java
double rate = 0.05;
double years = 10.0;
double growthFactor = Math.exp(rate * years); // e^0.5, hệ số tăng trưởng
```

Đây là đoạn minh họa trong một method, không phải chương trình Java độc lập. Mô hình \(e^{rt}\) thường dùng cho tăng trưởng liên tục; ứng dụng phải xác định rõ đơn vị thời gian và ý nghĩa của `rate`.

### Overflow và underflow của `exp`

`double` không chứa được mọi giá trị của \(e^x\). Ví dụ:

```java
System.out.println(Math.exp(800.0));  // Infinity
System.out.println(Math.exp(-800.0)); // 0.0
```

`Math.exp(800.0)` vượt độ lớn hữu hạn của `double` nên cho dương vô cực. `Math.exp(-800.0)` là giá trị toán học dương nhưng quá nhỏ để thể hiện, nên làm tròn thành dương zero.

Vì thế **`Math.exp(x) == 0.0` không nhất thiết nói \(e^x=0\) về toán học**. Với phép tính xác suất cực nhỏ, log-likelihood hoặc số liệu có nhiều bậc độ lớn, đôi khi nên giữ dữ liệu ở **miền logarit** thay vì chuyển sang số mũ quá sớm.

## 3. `Math.log(x)`: logarit tự nhiên

`Math.log(x)` tính \(\ln(x)\), hàm ngược của \(e^x\) trên miền số thực dương:

```java
double x = 3.0;
double logX = Math.log(x);
double approximatelyOriginal = Math.exp(logX);
```

Do làm tròn, `approximatelyOriginal` không được bảo đảm có bit giống hoàn toàn `x`. Không nên viết test khẳng định `Math.exp(Math.log(x)) == x` cho mọi `double`.

Một số quy tắc:

- `Math.log(1.0)` trả `0.0`.
- Nếu `0 < x < 1`, \(\ln(x)\) âm.
- Nếu `x > 1`, \(\ln(x)\) dương.
- `Math.log(+0.0)` và `Math.log(-0.0)` đều trả `-Infinity`.
- `Math.log(x)` với `x < 0` trả `NaN`, vì không có logarit thực.
- `Math.log(Double.POSITIVE_INFINITY)` trả `Infinity`.

```java
System.out.println(Math.log(1.0));           // 0.0
System.out.println(Math.log(0.0));           // -Infinity
System.out.println(Math.log(-1.0));          // NaN
System.out.println(Math.log(Double.POSITIVE_INFINITY)); // Infinity
```

**Không ném `ArithmeticException` cho các đầu vào miền sai thông thường.** Đây là hàm dấu phẩy động; ứng dụng cần kiểm tra kết quả hoặc đầu vào nếu không cho phép NaN/vô cực.

Logarit còn giúp biến tích thành tổng về mặt toán học: \(\ln(ab)=\ln(a)+\ln(b)\) với \(a,b>0\). Điều này hữu ích khi tích của nhiều giá trị dương quá lớn hoặc quá nhỏ, nhưng cần cân nhắc sai số khi lấy log và khi quy đổi lại.

## 4. `Math.log10(x)`: logarit cơ số 10

`Math.log10(x)` trả \(\log_{10}(x)\). Nó có cùng miền số thực dương với `log`, nhưng **cơ số khác nhau**:

```java
System.out.println(Math.log10(1.0));    // 0.0
System.out.println(Math.log10(10.0));   // 1.0
System.out.println(Math.log10(1000.0)); // 3.0
```

Dùng `log10` khi vấn đề liên quan số bậc độ lớn thập phân, thang đo cơ số 10 hoặc tỷ lệ theo lũy thừa của 10. Dùng `log` khi công thức cần logarit tự nhiên, ví dụ liên tục, giải tích và nhiều mô hình xác suất.

Quan hệ toán học:

\[
\log_{10}(x)=\frac{\ln(x)}{\ln(10)}.
\]

Đừng nhất thiết triển khai `Math.log(x) / Math.log(10.0)` thay cho `Math.log10(x)`: Java đã có API trực tiếp, và các cách tính dấu phẩy động khác nhau có thể khác ở chữ số cuối.

**Đổi cơ số tổng quát:** \(\log_b(x)=\ln(x)/\ln(b)\), với `x > 0`, `b > 0` và `b != 1`:

```java
double base = 2.0;
double value = 8.0;
double logBase2 = Math.log(value) / Math.log(base);
```

Đây là công thức tính logarit cơ số 2 từ hai lời gọi `log`; kết quả là xấp xỉ. Khi `base` cực gần 1, mẫu số \(\ln(base)\) nhỏ nên bài toán có thể nhạy với sai số; cần đánh giá độ ổn định thay vì chỉ thay bằng một phép chia.

## 5. Vì sao `Math.exp(x) - 1.0` có thể mất precision?

Với \(x\) gần 0, \(e^x\) gần 1. Nếu gọi `Math.exp(x)`, kết quả bị làm tròn sang lưới `double` quanh 1. Sau đó trừ 1 có thể làm **mất toàn bộ tín hiệu rất nhỏ**.

Chọn:

```java
double x = 1e-16;
double naive = Math.exp(x) - 1.0;
double stable = Math.expm1(x);

System.out.println(naive);  // 0.0
System.out.println(stable); // 1.0E-16
```

Về toán học, \(e^{10^{-16}}-1\) xấp xỉ \(10^{-16}\), nhưng `Math.exp(1e-16)` được làm tròn thành `1.0` ở độ chính xác `double`, khiến `1.0-1.0` bằng 0. Đây là **cancellation** (mất thông tin khi trừ hai số gần nhau).

`Math.expm1(x)` tính \(e^x-1\) bằng thuật toán đặc thù, **không đơn thuần thực hiện phép Java `Math.exp(x) - 1` theo cùng các bước**. Nhờ đó hàm giữ được độ chính xác cao khi x rất nhỏ.

### Chọn `expm1` theo ý nghĩa công thức

Nếu công thức cần **hệ số tăng trưởng** \(e^r\), dùng `exp(r)`. Nếu cần **mức tăng so với 1** \(e^r-1\), dùng `expm1(r)`:

```java
double continuousRate = 1e-12;
double relativeIncrease = Math.expm1(continuousRate);
```

Với lãi suất rất nhỏ hoặc biến đổi phần trăm, `relativeIncrease` là đại lượng cần thiết. Dùng `exp` rồi trừ 1 có thể mất chữ số có nghĩa.

Ở đầu vào rất âm, `Math.expm1(-1000.0)` trả `-1.0` vì \(e^{-1000}-1\) làm tròn thành -1. Trái lại `Math.exp(-1000.0)` làm tròn thành zero. Đây không phải mâu thuẫn, vì **hai hàm trả hai đại lượng khác nhau**.

## 6. Vì sao `Math.log(1.0 + x)` có thể mất precision?

Cơ chế tương tự xảy ra khi \(x\) rất gần 0. Phép `1.0 + x` có thể được làm tròn thành đúng `1.0` **trước khi** `Math.log` được gọi:

```java
double x = 1e-16;
double naive = Math.log(1.0 + x);
double stable = Math.log1p(x);

System.out.println(naive);  // 0.0
System.out.println(stable); // 1.0E-16
```

Về toán học, \(\ln(1+x) \approx x\) khi \(x\) nhỏ. `Math.log(1.0 + x)` không biết rằng giá trị đầu vào `1.0` là kết quả làm tròn của tổng `1.0 + 1e-16`; thông tin đã mất **trước khi gọi log**.

`Math.log1p(x)` tính \(\ln(1+x)\) bằng thuật toán giữ precision cho x nhỏ. Nó thích hợp trong:

- Công thức lợi suất logarit \(\ln(1+r)\) với tỷ lệ biến động `r` rất nhỏ.
- Xử lý xác suất và các biến đổi được biểu diễn theo phần thay đổi quanh 1.
- Thuật toán cần logarit của tổng `1+x` mà `x` là một đầu vào độc lập nhỏ.

Ví dụ tính *log-return*:

```java
double smallReturn = 1e-12;
double logReturn = Math.log1p(smallReturn);
```

Không nên dùng `log1p` chỉ vì muốn lấy logarit thông thường: `Math.log1p(100.0)` là \(\ln(101)\), **khác** `Math.log(100.0)` là \(\ln(100)\).

## 7. Miền xác định: `log(x)` khác `log1p(x)`

Hai hàm logarit có miền số thực khác nhau:

| Hàm | Đầu vào cho giá trị thực thông thường | Điểm ranh giới | Phía không hợp lệ |
| --- | --- | --- | --- |
| `Math.log(x)` | `x > 0.0` | `x == ±0.0` → `-Infinity` | `x < 0.0` → `NaN` |
| `Math.log10(x)` | `x > 0.0` | `x == ±0.0` → `-Infinity` | `x < 0.0` → `NaN` |
| `Math.log1p(x)` | `x > -1.0` | `x == -1.0` → `-Infinity` | `x < -1.0` → `NaN` |

`Math.log1p(-0.5)` là giá trị hữu hạn âm vì `1-0.5 = 0.5 > 0`, còn `Math.log(-0.5)` trả NaN.

```java
System.out.println(Math.log1p(-1.0)); // -Infinity
System.out.println(Math.log1p(-2.0)); // NaN
System.out.println(Math.log(-1.0));   // NaN
```

Dấu zero là một trường hợp đặc biệt:

```java
System.out.println(Math.log1p(-0.0)); // -0.0
System.out.println(Math.expm1(-0.0)); // -0.0
System.out.println(Math.log(-0.0));   // -Infinity
```

`log1p(-0.0)` và `expm1(-0.0)` **giữ âm zero**. Trong khi đó `log(-0.0)` trả âm vô cực, vì logarit tại giá trị zero ở ranh giới miền. Không được suy luận rằng hai hàm có thể dùng thay nhau chỉ vì x gần 0.

Nếu kết quả phải hữu hạn, hãy kiểm tra miền đầu vào **và** kiểm tra đầu ra; các quy tắc đặc biệt có thể cho `Infinity`/`NaN` mà không phát sinh exception.

## 8. Các trường hợp `Infinity`, `NaN` và overflow/underflow

Với `double`, năm hàm có hành vi số thực đặc biệt. Bảng sau nêu các kết quả đáng nhớ:

| Đầu vào | `exp(x)` | `log(x)` | `log10(x)` | `expm1(x)` | `log1p(x)` |
| --- | --- | --- | --- | --- | --- |
| `+0.0` | `1.0` | `-Infinity` | `-Infinity` | `+0.0` | `+0.0` |
| `-0.0` | `1.0` | `-Infinity` | `-Infinity` | `-0.0` | `-0.0` |
| `+Infinity` | `+Infinity` | `+Infinity` | `+Infinity` | `+Infinity` | `+Infinity` |
| `-Infinity` | `+0.0` | `NaN` | `NaN` | `-1.0` | `NaN` |
| `NaN` | `NaN` | `NaN` | `NaN` | `NaN` | `NaN` |
| `-1.0` | Dương hữu hạn | `NaN` | `NaN` | Âm hữu hạn | `-Infinity` |

Lưu ý: `Math.exp(-Infinity)` là **dương zero**, không phải âm zero. `Math.expm1(-Infinity)` là -1.0. `Math.log1p(-Infinity)` là NaN vì \(1+(-\infty)\) không thuộc miền logarit thực dương.

### Không có exception tự động cho log âm và exp overflow

`Math.log(-2.0)` không ném `IllegalArgumentException`; nó trả `NaN`. `Math.exp(800.0)` không ném `ArithmeticException`; nó trả `Infinity`. Đó là giá trị dấu phẩy động đặc biệt theo quy tắc API.

Để phân loại:

```java
double result = Math.exp(800.0);
System.out.println(Double.isInfinite(result)); // true
System.out.println(Double.isFinite(result));   // false
```

`Double.isFinite` chỉ nói kết quả có hữu hạn hay không. Nó không bảo đảm **không mất precision**, và `0.0` do underflow vẫn được coi là hữu hạn. Nếu cần ngăn underflow hoặc kiểm soát độ chính xác tương đối, phải phân tích miền đầu vào và thuật toán cụ thể.

## 9. Độ chính xác: `Math` và `StrictMath`

Cả `Math` và `StrictMath` cung cấp `exp`, `log`, `log10`, `expm1`, `log1p` với ý nghĩa và các trường hợp đặc biệt tương ứng.

- **`Math`:** API chuẩn cho tính toán thông thường; những hàm này có các yêu cầu về độ chính xác như sai số tối đa khoảng **1 ulp** và tính chất *semi-monotonic* theo đặc tả từng phương thức. JVM có thể sử dụng triển khai tối ưu.
- **`StrictMath`:** thiết kế nhằm đem lại kết quả có tính tái lập giữa các nền tảng cho các hàm được chỉ định, dựa trên quy tắc/thuật toán được chuẩn hóa. Nó không có nghĩa luôn gần kết quả toán học hơn `Math` ở mọi đầu vào.
- Từ Java 17, biểu thức dấu phẩy động Java đã có ngữ nghĩa luôn nghiêm ngặt theo thay đổi ngôn ngữ; điều này **không làm `Math` và `StrictMath` trở thành cùng một API hoặc bắt buộc trả cùng mọi bit**.

Ví dụ:

```java
double fastOrDefault = Math.exp(1.0);
double reproducible = StrictMath.exp(1.0);

String first = String.format(java.util.Locale.ROOT,
        "%.6f", fastOrDefault);
String second = String.format(java.util.Locale.ROOT,
        "%.6f", reproducible);
// Cả hai có thể hiển thị "2.718282" khi format 6 số sau dấu chấm.
```

**Không nên khẳng định `Math.exp(x) == StrictMath.exp(x)` luôn true**, cũng không tuyên bố `StrictMath` luôn nhanh/chậm hơn trên mọi JVM; đánh giá độ chính xác, tính tái lập và hiệu năng theo hợp đồng và môi trường.

`StrictMath.expm1`/`StrictMath.log1p` vẫn hữu ích cho mục đích **ổn định phép tính gần 0**. Yếu tố chọn `expm1` thay `exp-1`, hoặc `log1p` thay `log(1+x)`, là công thức số học cần tính, không chỉ tên class.

## 10. Những công thức thường áp dụng và giới hạn

### Tính tăng trưởng nhỏ

Nếu cần phần tăng \(e^{rt}-1\):

```java
double rateTimesTime = 1e-12;
double relativeGrowth = Math.expm1(rateTimesTime);
```

`relativeGrowth` giữ được tín hiệu nhỏ tốt hơn `Math.exp(rateTimesTime)-1.0`. Nếu chỉ cần hệ số nhân \(e^{rt}\), dùng `Math.exp(rateTimesTime)`.

### Lợi suất logarit

Nếu có phần thay đổi tương đối `r`, với `r > -1`:

```java
double r = 1e-12;
double logReturn = Math.log1p(r);
```

Nếu `r == -1`, kết quả là `-Infinity`. Nếu `r < -1`, kết quả là `NaN`. Hợp đồng nghiệp vụ có thể yêu cầu từ chối dữ liệu không phù hợp trước khi gọi hàm.

### Ghép hai hàm là phép đảo gần đúng, không bảo đảm chính xác từng bit

Về toán học, với điều kiện phù hợp:

\[
\ln(e^x)=x,\qquad e^{\ln(x)}=x \quad(x>0).
\]

Tuy nhiên với số học dấu phẩy động:

- `Math.log(Math.exp(800.0))` trả `Infinity` vì `exp(800)` đã overflow.
- `Math.log(Math.exp(-800.0))` trả `-Infinity` vì `exp(-800)` đã underflow về 0.
- Với `x` nhỏ, `Math.log(Math.exp(x))` có thể trả 0 thay vì x; `Math.log1p(Math.expm1(x))` có thể giữ x tốt hơn gần 0.
- Với x dương bình thường, `Math.exp(Math.log(x))` vẫn là xấp xỉ; không dùng so sánh `==` làm bảo đảm toán học tuyệt đối.

### Tính trên miền log tránh tràn tích số

Nếu nhiều hệ số dương rất nhỏ, có thể cộng `Math.log(p)` thay vì nhân tất cả trực tiếp:

```java
double p = 1e-200;
double q = 1e-200;
double directProduct = p * q;      // underflow: 0.0
double logProduct = Math.log(p) + Math.log(q); // hữu hạn
```

Đây là chiến lược lưu **log của tích**, không tự động khôi phục được tích ban đầu dưới dạng `double` nếu nó vẫn nhỏ hơn miền biểu diễn. Với mô hình xác suất và các thuật toán thống kê, đây thường là lợi ích thực tế.

## 11. Chương trình Java 17+ chạy độc lập

Tạo file **`ExpLogDemo.java`**:

```java
import java.util.Locale;

public class ExpLogDemo {
    public static void main(String[] args) {
        System.out.println("expZero=" + Math.exp(0.0));
        System.out.println("expNegativeZero=" + Math.exp(-0.0));
        System.out.println("logOne=" + Math.log(1.0));
        System.out.println("logTenBase10=" + Math.log10(10.0));
        System.out.println("logThousandBase10=" + Math.log10(1000.0));

        double x = 1e-16;
        System.out.println("naiveExpMinusOne=" + (Math.exp(x) - 1.0));
        System.out.println("stableExpMinusOne=" + Math.expm1(x));
        System.out.println("naiveLogOnePlus=" + Math.log(1.0 + x));
        System.out.println("stableLogOnePlus=" + Math.log1p(x));
        System.out.println("composedNaive=" + Math.log(Math.exp(x)));
        System.out.println("composedStable=" +
                Math.log1p(Math.expm1(x)));

        System.out.println("expOverflow=" + Math.exp(800.0));
        System.out.println("expUnderflow=" + Math.exp(-800.0));
        System.out.println("expm1NegativeInfinity=" +
                Math.expm1(Double.NEGATIVE_INFINITY));
        System.out.println("expPositiveInfinity=" +
                Math.exp(Double.POSITIVE_INFINITY));
        System.out.println("expNegativeInfinity=" +
                Math.exp(Double.NEGATIVE_INFINITY));

        System.out.println("logZero=" + Math.log(0.0));
        System.out.println("logNegativeZero=" + Math.log(-0.0));
        System.out.println("logNegative=" + Math.log(-1.0));
        System.out.println("log10Zero=" + Math.log10(0.0));
        System.out.println("log10Negative=" + Math.log10(-10.0));
        System.out.println("logPositiveInfinity=" +
                Math.log(Double.POSITIVE_INFINITY));

        System.out.println("log1pMinusOne=" + Math.log1p(-1.0));
        System.out.println("log1pBelowMinusOne=" + Math.log1p(-2.0));
        System.out.println("expm1NegativeZero=" + Math.expm1(-0.0));
        System.out.println("log1pNegativeZero=" + Math.log1p(-0.0));
        System.out.println("nanInput=" + Math.log1p(Double.NaN));

        double overflow = Math.exp(800.0);
        System.out.println("overflowIsInfinite=" +
                Double.isInfinite(overflow));
        System.out.println("overflowIsFinite=" +
                Double.isFinite(overflow));

        System.out.println("mathExpSixDecimals=" +
                String.format(Locale.ROOT, "%.6f", Math.exp(1.0)));
        System.out.println("strictExpSixDecimals=" +
                String.format(Locale.ROOT, "%.6f", StrictMath.exp(1.0)));
        System.out.println("logOneHalfSixDecimals=" +
                String.format(Locale.ROOT, "%.6f", Math.log1p(-0.5)));
        System.out.println("logBaseTwoEightSixDecimals=" +
                String.format(Locale.ROOT, "%.6f",
                        Math.log(8.0) / Math.log(2.0)));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac ExpLogDemo.java
java ExpLogDemo
```

**Kết quả mong đợi** trên JDK 17 khi sử dụng đúng ví dụ này:

```text
expZero=1.0
expNegativeZero=1.0
logOne=0.0
logTenBase10=1.0
logThousandBase10=3.0
naiveExpMinusOne=0.0
stableExpMinusOne=1.0E-16
naiveLogOnePlus=0.0
stableLogOnePlus=1.0E-16
composedNaive=0.0
composedStable=1.0E-16
expOverflow=Infinity
expUnderflow=0.0
expm1NegativeInfinity=-1.0
expPositiveInfinity=Infinity
expNegativeInfinity=0.0
logZero=-Infinity
logNegativeZero=-Infinity
logNegative=NaN
log10Zero=-Infinity
log10Negative=NaN
logPositiveInfinity=Infinity
log1pMinusOne=-Infinity
log1pBelowMinusOne=NaN
expm1NegativeZero=-0.0
log1pNegativeZero=-0.0
nanInput=NaN
overflowIsInfinite=true
overflowIsFinite=false
mathExpSixDecimals=2.718282
strictExpSixDecimals=2.718282
logOneHalfSixDecimals=-0.693147
logBaseTwoEightSixDecimals=3.000000
```

Bốn dòng `naive...` và `stable...` chứng minh sự khác biệt thiết yếu: những bước `exp(x)-1` và `log(1+x)` có thể đánh mất tín hiệu gần 0; `expm1` và `log1p` giữ precision tốt hơn. Các dòng `Infinity`/`NaN` chứng minh lý do phải xét miền đầu vào và đầu ra. Hai dòng `Math`/`StrictMath` dùng **định dạng sáu chữ số**, không khẳng định mọi chữ số nhị phân của hai kết quả đều giống nhau.

## 12. Sai lầm thường gặp

| Nhận định | Cách hiểu đúng |
| --- | --- |
| “`Math.log` trong Java mặc định cơ số 10.” | Sai: `Math.log` là logarit tự nhiên; `Math.log10` mới là cơ số 10. |
| “`Math.exp(x) - 1` và `Math.expm1(x)` luôn có cùng độ chính xác.” | Sai: `expm1` tốt hơn gần x = 0 do tránh cancellation. |
| “`Math.log(1+x)` giống `Math.log1p(x)` trong máy tính ở mọi x.” | Sai: bước `1+x` có thể làm tròn thành 1. |
| “`Math.log1p(x)` có nghĩa log của x cộng thêm 1 sau phép log.” | Sai: nó tính \(\ln(1+x)\), không phải \(\ln(x)+1\). |
| “`Math.expm1(x)` tính \(e^{x-1}\).” | Sai: nó tính \(e^x-1\). |
| “`Math.log(-1)` phải ném exception.” | Với số thực `double`, kết quả là `NaN`, không phải exception tự động. |
| “`Math.log1p(-1)` cho NaN.” | Sai: đúng ranh giới -1 trả `-Infinity`; nhỏ hơn -1 mới NaN. |
| “`Math.exp(800)` sẽ luôn cho một số `double` hữu hạn.” | Sai: overflow thành `Infinity`. |
| “`Math.exp(-800)` bằng 0 trong toán học.” | Sai: đó là underflow về zero trong biểu diễn `double`. |
| “`Math.log1p(-0.0)` sẽ đổi dấu thành dương zero.” | Sai: API giữ `-0.0`. |
| “`StrictMath` luôn cho nhiều chữ số chính xác hơn `Math`.” | Không có bảo đảm chung như vậy; mục tiêu quan trọng của `StrictMath` là tái lập kết quả. |
| “`exp(log(x)) == x` luôn true.” | Là hệ thức toán học trong miền phù hợp, nhưng triển khai dấu phẩy động có làm tròn và trường hợp đặc biệt. |

## 13. Checklist chọn hàm

1. **Bạn cần hàm mũ hay logarit?** `exp(x)` tính \(e^x\); `log(x)` tính \(\ln x\); `log10(x)` tính \(\log_{10}x\).
2. **Bài toán cần trừ 1 sau hàm mũ?** Nếu công thức là \(e^x-1\), ưu tiên `expm1(x)`, đặc biệt khi `x` gần 0.
3. **Bài toán cần log của 1 cộng x?** Nếu công thức là \(\ln(1+x)\), ưu tiên `log1p(x)`, đặc biệt khi `x` gần 0.
4. **Miền xác định có hợp lệ không?** `log`/`log10` cần x dương; `log1p` cần x lớn hơn -1 để có kết quả hữu hạn thông thường.
5. **Có nguy cơ overflow hoặc underflow không?** `exp` có thể thành vô cực/zero. Xem xét miền log và xử lý `Double.isFinite` phù hợp.
6. **Có quan tâm `NaN`, Infinity và signed zero không?** Đừng dựa vào exception số nguyên; dùng kiểm tra miền, `Double.isNaN`/`isInfinite`/`isFinite`.
7. **Bạn cần tính tái lập xuyên JVM không?** Cân nhắc `StrictMath` theo yêu cầu; không mặc định nó luôn nhanh hơn hoặc chính xác hơn.
8. **Bạn cần kết quả bit giống hệt sau ghép hàm không?** Nếu không có bảo đảm, dùng sai số tuyệt đối/tương đối phù hợp thay vì `==` cho các kết quả số thực xấp xỉ.

**Ghi nhớ:** `exp` và `log` là hai hàm cơ bản theo cơ số \(e\), `log10` theo cơ số 10; hai hàm `expm1` và `log1p` dành cho những biểu thức **rất gần 0 dễ mất chữ số có nghĩa**. Chọn đúng hàm thường quan trọng hơn việc tìm cách làm tròn kết quả sau mỗi bước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-004">sin, cos, tan và chuyển độ sang radian</a>

<details>
<summary>Xem chi tiết</summary>

# sin, cos, tan và chuyển độ sang radian trong Java

## 1. Vấn đề quan trọng nhất: Java nhận radian

Các hàm lượng giác **Math.sin(double)**, **Math.cos(double)** và **Math.tan(double)** trong Java luôn hiểu đối số là **radian**, không phải độ. Nếu dữ liệu nhập từ giao diện hoặc bài toán hình học dùng độ, hãy chuyển đơn vị **trước khi gọi hàm**.

~~~java
double angleDegrees = 30.0;
double angleRadians = Math.toRadians(angleDegrees);
double value = Math.sin(angleRadians); // xấp xỉ 0.5
~~~

Ngược lại, **Math.sin(30.0)** tính sin của **30 radian**: đó không phải sin(30°) mà học sinh thường gặp ở trường.

Bài học này làm rõ đơn vị góc, công thức lượng giác, cách dùng Math và StrictMath, sai số dấu phẩy động, xử lý góc gần 90°, chương trình chạy bằng Java 17+ và những lỗi lập trình phổ biến.

## 2. Độ, radian và công thức chuyển đổi

- **Độ**: một vòng tròn đầy đủ là **360°**.
- **Radian**: độ lớn góc bằng tỷ số **độ dài cung / bán kính**. Một vòng đầy đủ là **2π rad**.
- **180° = π rad**; **90° = π/2 rad**.

Công thức:

\[
\text{radian}=\text{độ}\times\frac{\pi}{180}
\]

\[
\text{độ}=\text{radian}\times\frac{180}{\pi}
\]

Bảng quy đổi:

| Độ | Radian (toán học) | Gần đúng |
| ---: | --- | ---: |
| 0° | 0 | 0 |
| 30° | π/6 | 0.523599 |
| 45° | π/4 | 0.785398 |
| 60° | π/3 | 1.047198 |
| 90° | π/2 | 1.570796 |
| 180° | π | 3.141593 |
| 270° | 3π/2 | 4.712389 |
| 360° | 2π | 6.283185 |

Hằng **Math.PI** chứa một giá trị **double xấp xỉ π**, vì π có vô hạn chữ số không tuần hoàn. Do đó, phép chuyển đơn vị trong Java có thể phải làm tròn.

### Math.toRadians và Math.toDegrees

~~~java
double radians = Math.toRadians(180.0); // xấp xỉ π
double degrees = Math.toDegrees(Math.PI); // xấp xỉ 180
~~~

**Math.toRadians(double)** đổi độ → radian; **Math.toDegrees(double)** đổi radian → độ. Cả hai đều nhận và trả **double**, không áp đặt rằng độ phải thuộc khoảng 0–360.

~~~java
double negative = Math.toRadians(-45.0); // xấp xỉ -π/4
double repeated = Math.toRadians(720.0); // xấp xỉ 4π
~~~

Giá trị **−45°** có thể biểu diễn chiều quay ngược so với +45°. **720°** là hai vòng đầy đủ, vẫn có thể truyền cho sin/cos/tan. Tuy nhiên, do sai số biểu diễn, các kết quả theo chu kỳ có thể không bằng nhau **từng bit**.

Cũng không được giả định phép đổi sang radian rồi đổi ngược sang độ luôn trả đúng lại double ban đầu theo toán tử **==**. Khi cần xác minh, hãy so sánh theo ngưỡng sai số phù hợp với dữ liệu và ứng dụng.

## 3. Sin, cos, tan trên đường tròn đơn vị

Theo hệ tọa độ toán học chuẩn, góc θ đo từ trục x dương, chiều dương ngược chiều kim đồng hồ. Điểm trên đường tròn đơn vị có:

\[
x=\cos(\theta),\qquad y=\sin(\theta)
\]

**Cos** cho thành phần ngang; **sin** cho thành phần dọc. Khi **cos(θ) khác 0** về mặt toán học:

\[
\tan(\theta)=\frac{\sin(\theta)}{\cos(\theta)}
\]

Các tính chất:

- Với đối số thực hữu hạn, sin và cos nằm trong **[−1, 1]**.
- Tan có thể lớn vô hạn về độ lớn khi tiến đến tiệm cận; không có một biên tuyệt đối cố định.
- **sin(−θ)=−sin(θ)**, **tan(−θ)=−tan(θ)**: các hàm lẻ.
- **cos(−θ)=cos(θ)**: hàm chẵn.
- Sin và cos có chu kỳ **2π**; tan có chu kỳ **π** trong toán học.

Các đồng nhất thức này đúng chính xác trong toán học; số dấu phẩy động Java có thể chỉ xấp xỉ do làm tròn.

### Các góc đặc biệt

| Góc độ | sin | cos | tan |
| ---: | --- | --- | --- |
| 0° | 0 | 1 | 0 |
| 30° | 1/2 | √3/2 | √3/3 |
| 45° | √2/2 | √2/2 | 1 |
| 60° | √3/2 | 1/2 | √3 |
| 90° | 1 | 0 | **Không xác định** |
| 180° | 0 | −1 | 0 |
| 270° | −1 | 0 | **Không xác định** |
| 360° | 0 | 1 | 0 |

Ở **90°** và **270°**, tan **không xác định theo toán học**. Không viết rằng tan tại hai góc này bằng Infinity: Java không có biểu diễn π/2 chính xác trong double, và tan có thể trả một giá trị hữu hạn rất lớn khi tính gần tiệm cận.

## 4. Math.sin: tính thành phần y và độ cao

~~~java
double sine = Math.sin(Math.toRadians(30.0)); // xấp xỉ 0.5
~~~

Sin dương ở phần tư I và II, âm ở phần tư III và IV. Ở góc 0° và 180°, sin bằng 0 theo toán học; tuy nhiên **Math.sin(Math.PI)** không bắt buộc bằng 0.0 chính xác, vì Math.PI chỉ xấp xỉ π.

Ví dụ vector có độ lớn 10 tạo góc 30° với trục x:

~~~java
double length = 10.0;
double radians = Math.toRadians(30.0);
double y = length * Math.sin(radians); // xấp xỉ 5.0
~~~

Đầu vào của sin phải là radian; hệ tọa độ thực tế có thể có chiều trục y khác quy ước toán học, vì vậy phải kiểm tra trước khi áp dụng vào giao diện hoặc đồ họa.

## 5. Math.cos: tính thành phần x

~~~java
double cosine = Math.cos(Math.toRadians(60.0)); // xấp xỉ 0.5
~~~

Cos dương ở phần tư I và IV, âm ở phần tư II và III. Ở góc 90° và 270°, cos bằng 0 trong toán học. Nhưng:

~~~java
double rightAngle = Math.toRadians(90.0);
boolean exactlyZero = Math.cos(rightAngle) == 0.0; // false trên JDK thông dụng
~~~

Giá trị Math.cos(rightAngle) thường gần **6.123 × 10^−17**; không phải lỗi hình học. Nó là sai số rất nhỏ do các phép chuyển đổi và tính toán trên double.

Ví dụ thành phần x của vector dài 10 ở góc 60°:

~~~java
double x = 10.0 * Math.cos(Math.toRadians(60.0));
// x xấp xỉ 5.0
~~~

## 6. Math.tan: tính độ dốc và tránh điểm cực

~~~java
double tangent = Math.tan(Math.toRadians(45.0)); // xấp xỉ 1.0
~~~

Trong toán học, tan=sin/cos khi cos khác 0. Tan thường được dùng để suy ra tỷ lệ hai cạnh hoặc độ dốc tại góc không thẳng đứng.

**Tại sao tan(90°) không nhất thiết thành Infinity trong Java?**

~~~java
double nearHalfPi = Math.toRadians(90.0);
double c = Math.cos(nearHalfPi);
double t = Math.tan(nearHalfPi);
System.out.println(c == 0.0);        // false
System.out.println(Double.isFinite(t)); // true
~~~

Math.toRadians(90.0) là một double **gần π/2**. Vì cos không bằng chính xác 0, Math.tan trả một giá trị hữu hạn rất lớn ở góc xấp xỉ này. Dù máy trả số, **độ dốc tại góc thẳng đứng không xác định** trong mô hình toán học.

Đối với một ứng dụng không chấp nhận góc quá gần tiệm cận của tan, có thể định nghĩa chính sách kiểm tra:

~~~java
double radians = Math.toRadians(inputDegrees);
if (Math.abs(Math.cos(radians)) < 1.0e-12) {
    throw new IllegalArgumentException("Angle too close to a tan pole");
}
double slope = Math.tan(radians);
~~~

Đoạn trên nằm bên trong method có biến **inputDegrees**. **1e−12 không phải ngưỡng đúng cho mọi bài toán**: đây chỉ là ví dụ yêu cầu miền đầu vào; phải chọn tiêu chí theo độ nhạy và sai số cho phép. Kiểm tra cosine khác 0 bằng toán tử **!=** vẫn có thể chấp nhận các độ dốc lớn phi thực tế.

**Math.tan(x)** có thể không trùng từng bit với **Math.sin(x)/Math.cos(x)**, vì thuật toán và bước làm tròn khác nhau. Khi cần tan, gọi trực tiếp Math.tan; nếu cần vector, hãy tính sin/cos theo từng trục.

## 7. Trigonometry trong ứng dụng: vector và tọa độ

Một vector dài **r** và góc **θ** từ trục x dương theo chiều ngược kim đồng hồ có:

\[
x=r\cos(\theta),\qquad y=r\sin(\theta)
\]

Với **r=10, θ=30°**:

~~~java
double magnitude = 10.0;
double angleDegrees = 30.0;
double angleRadians = Math.toRadians(angleDegrees);
double x = magnitude * Math.cos(angleRadians); // xấp xỉ 8.660254
double y = magnitude * Math.sin(angleRadians); // xấp xỉ 5.0
~~~

Công thức xuất hiện trong đồ họa, robot, mô phỏng, vật lý, lực, vận tốc và hình học. Tuy nhiên màn hình máy tính thường có **y tăng xuống dưới**, khác hệ trục toán học. Một chương trình đồ họa có thể phải đổi dấu thành phần y hoặc đổi quy tắc đo góc. Không áp dụng máy móc khi chưa xác định hệ tọa độ.

Nguyên tắc quan trọng khi thiết kế API: đặt tên **angleDegrees** hoặc **angleRadians**; nếu tương tác với nhiều thư viện, ghi rõ quy tắc trục, chiều dương và đơn vị góc tại điểm giao tiếp.


## 8. Math và StrictMath khác gì nhau?

Java có **java.lang.Math** và **java.lang.StrictMath**, đều cung cấp **sin**, **cos**, **tan**, **toRadians**, **toDegrees**.

- **Math** có thể được JVM tối ưu bằng các cách thực thi khác nhau, miễn đáp ứng các cam kết của đặc tả API. Với một số phép toán, bit kết quả có thể khác đôi chút giữa nền tảng.
- **StrictMath** được thiết kế cho hành vi tái lập của các hàm toán học cốt lõi theo thuật toán được chỉ định, phù hợp khi cần tính nhất quán kết quả giữa các triển khai Java.
- **Cả hai đều nhận góc radian** cho sin/cos/tan.
- StrictMath **không làm cho π trở thành số biểu diễn chính xác** và không bảo đảm tất cả đồng nhất thức lượng giác đúng từng bit.

Ví dụ:

~~~java
double rad = Math.toRadians(30.0);
double fast = Math.sin(rad);
double reproducible = StrictMath.sin(rad);
// Cả hai xấp xỉ 0.5; không nên giả định luôn cùng mẫu bit.
~~~

Tài liệu Java 17 cho **Math.sin/cos/tan** nêu yêu cầu độ chính xác trong phạm vi **1 ulp** của kết quả đúng toán học và tính **semi-monotonic**. Điều này không có nghĩa Math.cos(π/2) phải bằng chính xác 0, bởi π/2 đưa vào đã bị xấp xỉ.

Trong đa số ứng dụng Java Core, **Math** phù hợp. Chọn **StrictMath** khi yêu cầu sản phẩm có ràng buộc cụ thể về tái lập kết quả bit của các hàm được đặc tả.

## 9. Sai số floating-point và so sánh gần đúng

Double có độ chính xác hữu hạn. Những biểu thức có kết quả toán học đẹp như 0, 0.5 hoặc 1 không luôn tạo đúng những bit tương ứng do chuyển đơn vị, phép tính xấp xỉ hoặc cách giảm đối số.

Ví dụ:

~~~java
boolean equal = Math.sin(Math.toRadians(180.0)) == 0.0;
// false trên JDK thông dụng, dù sin(180°)=0 trong toán học
~~~

Khi kiểm thử trên miền giá trị có giới hạn, một hàm so sánh **sai số tuyệt đối** hữu ích:

~~~java
static boolean nearlyEqual(double a, double b, double tolerance) {
    return Double.isFinite(a) && Double.isFinite(b)
            && tolerance >= 0.0
            && Math.abs(a - b) <= tolerance;
}
~~~

Đây là phương thức đặt trong class. Ví dụ:

~~~java
boolean valid = nearlyEqual(
        Math.sin(Math.toRadians(30.0)), 0.5, 1.0e-12);
~~~

**1e−12 chỉ là lựa chọn minh họa** đối với phép tính lượng giác đơn giản trên miền gần [−1, 1]. Không tự sử dụng ngưỡng này cho mọi hệ thống. Với đại lượng có đơn vị, cần xác định giới hạn nghiệp vụ; với dữ liệu độ lớn khác nhau, có thể dùng sai số tương đối hoặc ULP tùy hợp đồng.

Việc làm tròn mọi kết quả nhỏ thành 0 có thể che dấu thông tin số học quan trọng. Nếu ứng dụng muốn đánh dấu các góc đặc biệt như 90° hoặc 180°, phải định nghĩa quy tắc xử lý góc và ngưỡng xấp xỉ ở mức nghiệp vụ, không gắn cứng vào Math.

## 10. Chương trình Java 17+ biên dịch và chạy độc lập

Lưu chương trình thành **TrigAngleDemo.java**:

~~~java
import java.util.Locale;

public class TrigAngleDemo {
    static boolean nearlyEqual(double a, double b, double tolerance) {
        return Double.isFinite(a) && Double.isFinite(b)
                && tolerance >= 0.0
                && Math.abs(a - b) <= tolerance;
    }

    static void show(double degrees) {
        double radians = Math.toRadians(degrees);
        System.out.printf(Locale.US,
                "%5.0f deg -> rad=%8.5f, sin=% .6f, cos=% .6f, tan=% .6f%n",
                degrees, radians, Math.sin(radians),
                Math.cos(radians), Math.tan(radians));
    }

    public static void main(String[] args) {
        show(0.0);
        show(30.0);
        show(45.0);
        show(60.0);
        show(-45.0);

        double rightAngle = Math.toRadians(90.0);
        double halfTurn = Math.toRadians(180.0);

        System.out.println("cos(90) == 0: "
                + (Math.cos(rightAngle) == 0.0));
        System.out.printf(Locale.US, "cos(90) approx: %.3e%n",
                Math.cos(rightAngle));
        System.out.println("tan(90) is finite: "
                + Double.isFinite(Math.tan(rightAngle)));
        System.out.println("sin(180) == 0: "
                + (Math.sin(halfTurn) == 0.0));

        System.out.println("sin(30) near 0.5: "
                + nearlyEqual(Math.sin(Math.toRadians(30.0)),
                        0.5, 1.0e-12));
        System.out.println("StrictMath.sin(30) near 0.5: "
                + nearlyEqual(
                        StrictMath.sin(StrictMath.toRadians(30.0)),
                        0.5, 1.0e-12));

        System.out.printf(Locale.US, "roundtrip: %.10f deg%n",
                Math.toDegrees(Math.toRadians(123.0)));
        System.out.println("sin(infinity) is NaN: "
                + Double.isNaN(Math.sin(Double.POSITIVE_INFINITY)));
        System.out.println("sin(-0.0) keeps negative zero: "
                + (Double.doubleToRawLongBits(Math.sin(-0.0))
                        == Double.doubleToRawLongBits(-0.0)));
    }
}
~~~

Lệnh dùng với JDK 17+:

~~~bash
javac --release 17 TrigAngleDemo.java
java TrigAngleDemo
~~~

Stdout trên JDK đã kiểm tra (giữ nguyên các khoảng trắng đầu dòng do printf):

~~~text
    0 deg -> rad= 0.00000, sin= 0.000000, cos= 1.000000, tan= 0.000000
   30 deg -> rad= 0.52360, sin= 0.500000, cos= 0.866025, tan= 0.577350
   45 deg -> rad= 0.78540, sin= 0.707107, cos= 0.707107, tan= 1.000000
   60 deg -> rad= 1.04720, sin= 0.866025, cos= 0.500000, tan= 1.732051
  -45 deg -> rad=-0.78540, sin=-0.707107, cos= 0.707107, tan=-1.000000
cos(90) == 0: false
cos(90) approx: 6.123e-17
tan(90) is finite: true
sin(180) == 0: false
sin(30) near 0.5: true
StrictMath.sin(30) near 0.5: true
roundtrip: 123.0000000000 deg
sin(infinity) is NaN: true
sin(-0.0) keeps negative zero: true
~~~

### Giải thích dòng output

- **0°, 30°, 45°, 60°, −45°** được đưa qua Math.toRadians trước khi tính. Số có 6 chữ số thập phân chỉ là chuỗi đã format, không phải độ chính xác đầy đủ của kết quả double.
- **sin(30°) ≈ 0.5**, **cos(60°) ≈ 0.5**, tan(45°) xấp xỉ 1 theo công thức toán học.
- **cos(90) == 0** và **sin(180) == 0** false vì đầu vào radian và kết quả đều xấp xỉ ở kiểu double.
- **tan(90) is finite** true không có nghĩa tan tại góc thẳng đứng xác định theo toán học.
- **roundtrip: 123.0000000000 deg** được làm tròn đến 10 chữ số hiển thị; không phải lời bảo đảm rằng mọi góc double đều round-trip chính xác.
- **sin(infinity) is NaN** và bit dấu của **sin(−0.0)** minh họa các trường hợp dấu phẩy động đặc biệt.

## 11. NaN, infinity và zero có dấu

Các hàm sin/cos/tan của Java có các hành vi đặc biệt:

| Đối số double | sin | cos | tan |
| --- | --- | --- | --- |
| NaN | NaN | NaN | NaN |
| +Infinity hoặc −Infinity | NaN | NaN | NaN |
| +0.0 | +0.0 | 1.0 | +0.0 |
| −0.0 | −0.0 | 1.0 | −0.0 |

Hai giá trị **+0.0 và −0.0** bằng nhau theo toán tử == nhưng khác bit dấu. Nếu cần kiểm tra dấu âm zero:

~~~java
boolean negativeZeroRetained =
        Double.doubleToRawLongBits(Math.sin(-0.0))
                == Double.doubleToRawLongBits(-0.0); // true
~~~

Nếu dữ liệu biểu thị một **góc vật lý thực**, thường nên kiểm tra đầu vào hữu hạn:

~~~java
if (!Double.isFinite(angleDegrees)) {
    throw new IllegalArgumentException("Angle must be finite");
}
double radians = Math.toRadians(angleDegrees);
~~~

Đoạn minh họa nằm trong method có biến angleDegrees đã khai báo. Không so một giá trị với Double.NaN qua ==, vì NaN không bằng chính nó. Dùng **Double.isNaN** để kiểm tra.

## 12. Góc âm, nhiều vòng và góc rất lớn

Sin và cos có chu kỳ 2π, tan có chu kỳ π trong toán học. Vì vậy góc 30° và 390° cho cùng giá trị sin/cos lý tưởng. Nhưng trong số dấu phẩy động, phép nhân và phép giảm theo chu kỳ có thể làm tròn.

Nếu cần một góc chuẩn hóa theo độ trong **[0,360)** cho dữ liệu hữu hạn thông thường:

~~~java
double normalized = ((angleDegrees % 360.0) + 360.0) % 360.0;
~~~

Công thức minh họa này không phải phương pháp giảm đối số chính xác tuyệt đối cho **mọi double cực lớn**. Khi góc tích lũy qua quá nhiều vòng, phần lẻ của góc có thể bị mất do độ phân giải của double. Thiết kế tốt là giữ góc trong miền hữu hạn vừa phải ngay từ đầu nếu quy trình cho phép.

Cũng cần phân biệt chu kỳ với miền: một góc âm có thể chứa thông tin chiều quay, nên không tự gọi **Math.abs** trước khi tính sin.

## 13. Lấy góc trở lại bằng Math.atan2

Khi có tọa độ **(x,y)** và muốn suy ra góc, dùng **Math.atan2(y, x)** để lấy góc dưới dạng radian rồi **Math.toDegrees** nếu muốn độ:

~~~java
double x = Math.sqrt(3.0);
double y = 1.0;
double radians = Math.atan2(y, x);
double degrees = Math.toDegrees(radians); // xấp xỉ 30.0
~~~

**Thứ tự đối số atan2 là (y, x)**, rất dễ nhầm với tọa độ thường ghi (x,y). Khác với atan(y/x), atan2 phân biệt phần tư và có quy tắc cho trục tọa độ, zero có dấu. Hàm atan2 hữu ích khi đi theo chiều ngược từ vector sang góc.

Đây là kiến thức bổ sung liên quan: mục đích chính của bài vẫn là chuyển **độ sang radian trước khi dùng sin/cos/tan**.

## 14. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Gọi Math.sin(30) khi 30 là độ | Tính sin của 30 radian | Math.sin(Math.toRadians(30)) |
| Quên chia cho 180 khi dùng Math.PI | Sai đơn vị góc | Dùng Math.toRadians |
| Cho rằng Math.PI biểu diễn chính xác π | So sánh == tại π, π/2 thất bại | So sánh gần đúng có chính sách |
| Mong tan tại 90° luôn bằng Infinity | Java có thể cho số hữu hạn rất lớn | Xử lý tiệm cận/miền góc |
| Dùng tan để biểu diễn đường thẳng đứng | Độ dốc toán học không xác định | Xử lý riêng vector/đường đứng |
| So Math.cos(Math.PI/2) == 0 | Có thể false do làm tròn | Dùng kiểm tra phù hợp |
| Dùng epsilon cố định cho mọi góc | Có thể quá lớn hoặc quá nhỏ | Chọn ngưỡng theo yêu cầu |
| Tự lấy Math.abs của góc âm | Mất hướng quay, sai sin/tan | Giữ dấu theo hợp đồng |
| Nghĩ StrictMath nhận độ | Cũng nhận radian | Chuyển đơn vị trước |
| Dùng sin/cos rồi chia thay tan và kỳ vọng đúng bit | Thuật toán/làm tròn khác nhau | Gọi Math.tan nếu cần tan |
| Không kiểm tra NaN/infinity | Tạo NaN lan truyền | Kiểm tra Double.isFinite nếu cần |
| Dùng y dương xuống như y dương lên | Vector quay không đúng theo hình học | Xác định hệ tọa độ |
| Cộng số vòng rất lớn rồi giảm modulo tùy tiện | Độ phân giải double có thể mất pha | Giữ góc ở miền vừa phải |

## 15. Bài tập và đáp án

**Bài 1.** Muốn tính sin 60° trong Java, viết biểu thức nào?

*Đáp án:* **Math.sin(Math.toRadians(60.0))**, gần **0.8660254**.

**Bài 2.** 45° bằng bao nhiêu radian? Tan(45°) bằng bao nhiêu trong toán học?

*Đáp án:* **π/4 rad** và **1**. Giá trị Java là xấp xỉ.

**Bài 3.** Math.cos(Math.toRadians(90.0)) có luôn là 0.0 không?

*Đáp án:* Không. Math.PI và quá trình tính double có sai số, nên thường ra khoảng **6.123e−17**.

**Bài 4.** Vì sao tan(90°) không nên dùng để tính độ dốc đường thẳng đứng?

*Đáp án:* Tan tại 90° không xác định trong toán học; Math.tan được gọi với góc double gần π/2 có thể cho giá trị hữu hạn rất lớn và cực nhạy.

**Bài 5.** Cho vector độ dài 10 ở góc 30°. Thành phần x và y xấp xỉ bao nhiêu?

*Đáp án:* **x≈8.660254**, **y≈5** nếu lấy trục x dương làm góc 0° và chiều dương ngược kim đồng hồ.

**Bài 6.** Đầu vào đã là radian thì có nên gọi Math.toRadians thêm lần nữa không?

*Đáp án:* Không. Truyền trực tiếp góc radian cho Math.sin/cos/tan.

**Bài 7.** Math.sin(Double.POSITIVE_INFINITY) trả gì?

*Đáp án:* **NaN**, kiểm tra bằng **Double.isNaN**.

**Bài 8.** Khi nào chọn StrictMath.sin?

*Đáp án:* Khi yêu cầu tính tái lập theo đặc tả StrictMath giữa các triển khai; cả Math lẫn StrictMath vẫn nhận radian.

**Bài 9.** Vì sao chuyển độ sang radian rồi trở về độ có thể không chính xác bằng ==?

*Đáp án:* Số π và các kết quả double đều hữu hạn độ chính xác, nên phép chuyển đổi có thể phải làm tròn.

## 16. Tài liệu gốc

- [Math Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): sin, cos, tan, PI, toRadians, toDegrees, độ chính xác của các hàm.
- [StrictMath Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html): tính tái lập của các hàm toán học.
- [Double Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): NaN, Infinity, isFinite và raw bits.
- [Java Language Specification 17 — Floating-Point Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): biểu diễn double, zero có dấu, NaN và infinity.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-005">asin, acos, atan và atan2</a>

<details>
<summary>Xem chi tiết</summary>

# asin, acos, atan và atan2 trong Java — Math và StrictMath

## 1. Bạn sẽ học gì?

Khi biết **sin, cos, tan** của một góc, làm sao tìm lại góc ấy? Khi biết tọa độ `(x, y)` của một điểm, làm sao tìm góc phương vị mà **không nhầm góc phần tư**?

Java cung cấp bốn hàm lượng giác ngược quan trọng:

- `Math.asin(x)`: **arc sine**, tìm một góc có sin bằng `x`.
- `Math.acos(x)`: **arc cosine**, tìm một góc có cos bằng `x`.
- `Math.atan(x)`: **arc tangent**, tìm một góc có tan bằng `x`.
- `Math.atan2(y, x)`: tìm góc từ trục `+x` đến vector `(x, y)`, có xét **dấu của cả hai tọa độ** để nhận biết góc phần tư.

Cả bốn đều có phiên bản tương ứng trên `StrictMath`. Bài này thuộc **Java Core — Numbers / Math và StrictMath**, sử dụng Java 17 trở lên.

Hoàn thành bài học, bạn cần:

1. Nêu đúng **đầu vào hợp lệ, khoảng giá trị đầu ra và đơn vị radian** của từng hàm.
2. Hiểu tại sao phép toán ngược không khôi phục mọi góc ban đầu: các hàm trả **giá trị chính (principal value)**.
3. Phân biệt `atan(y / x)` với `atan2(y, x)`, nhớ **tham số y đứng trước x**.
4. Biết cách đổi radian ↔ degree bằng `Math.toRadians` và `Math.toDegrees`.
5. Xử lý `NaN`, ±Infinity và **signed zero** theo hợp đồng API; hiểu góc ở đường biên ±π.
6. So sánh giới hạn chính xác của `Math` và yêu cầu tái lập kết quả của `StrictMath`.
7. Viết mã hình học, kiểm tra miền đầu vào và xây dựng test cho bốn góc phần tư.

Kiến thức chuẩn bị: kiểu `double`, số thực IEEE 754, tọa độ Descartes, sin/cos/tan và ý nghĩa của `Math.PI`.

## 2. Lượng giác ngược không có nghĩa đảo hàm trên toàn trục thực

Trên một vòng tròn, nhiều góc khác nhau có cùng giá trị sin hoặc cos. Chẳng hạn:

```text
sin(π / 6) = 1 / 2
sin(5π / 6) = 1 / 2
sin(π / 6 + 2π) = 1 / 2
```

Không có cách chọn **duy nhất** góc gốc chỉ từ giá trị `sin`. Vì vậy, mỗi hàm lượng giác ngược chọn một **khoảng giá trị chính**.

| Hàm Java | Ý nghĩa | Miền đầu vào thông thường | Khoảng kết quả chính (radian) |
| --- | --- | --- | --- |
| `asin(v)` | Góc có sin = `v` | `−1 ≤ v ≤ 1` | `[−π/2, +π/2]` |
| `acos(v)` | Góc có cos = `v` | `−1 ≤ v ≤ 1` | `[0, π]` |
| `atan(v)` | Góc có tan = `v` | Mọi số thực hữu hạn; ±∞ cũng được xử lý | `[−π/2, +π/2]`, tiệm cận hai đầu |
| `atan2(y,x)` | Góc vector `(x,y)` tính từ trục `+x` | Hai tọa độ thực, có quy tắc IEEE 754 cho giá trị đặc biệt | `[−π, +π]` |

**Đơn vị trả về luôn là radian**, không phải độ, và **kiểu trả về là primitive `double`**. Java cung cấp `Math.asin(double)`, `Math.acos(double)`, `Math.atan(double)` và `Math.atan2(double,double)`. Truyền `float` hoặc `int` vào các method này thường được widening sang `double`; không có bộ overload riêng `asin(float)` trả `float` trong `Math` của Java 17.

Lưu ý các đầu mút của `atan` với đối số hữu hạn: nó không đạt chính xác ±π/2 theo toán học cho một giá trị hữu hạn, nhưng trong biểu diễn floating point kết quả có thể được làm tròn tới mẫu `double` gần π/2. Khi đối số ±Infinity, Java trả kết quả gần nhất với ±π/2 theo hợp đồng API.

## 3. Đổi độ và radian trước khi tính

Một vòng tròn có `360° = 2π` radian. Công thức:

```text
radian = degree × π / 180
degree = radian × 180 / π
```

Java có hai helper chuẩn:

```java
double radians = Math.toRadians(30.0);
double degrees = Math.toDegrees(Math.PI / 6);
```

Chúng trả `double`, và phép chuyển có thể có **sai số làm tròn**; không khẳng định mọi độ lớn đều đổi qua lại tuyệt đối chính xác ở mức bit.

Ví dụ:

```java
System.out.println(Math.toDegrees(Math.asin(0.5))); // 30.000000000000004 có thể xuất hiện
```

Trong phần chương trình bên dưới, sẽ dùng `printf(Locale.US, "%.1f%n", ...)` để **chủ động hiển thị một chữ số thập phân**, tránh hiểu nhầm cách in số gần 30 là kết quả không đúng.

**Bẫy phổ biến:** `Math.asin(0.5)` là khoảng `0.5235987756` **radian**, không phải `30.0` degree. `Math.asin(30)` không phải phép tính “asin của 30°”; đây là đối số **ngoài miền** của hàm asin.

## 4. Math.asin: arc sine, chọn góc trong [−π/2, π/2]

Ký hiệu toán học `arcsin(v)` hoặc `sin⁻¹(v)` mô tả **một góc có sin bằng v**. Đây là hàm lượng giác ngược, **không phải** giá trị `1/sin(v)`.

Với `v` trong `[−1,1]`, `Math.asin(v)` cho góc chính trong `[−π/2,π/2]`:

| v | Góc chính radian | Góc theo degree |
| ---: | --- | ---: |
| −1 | −π/2 | −90° |
| −0.5 | −π/6 | −30° |
| 0 | 0 | 0° |
| 0.5 | π/6 | 30° |
| 1 | π/2 | 90° |

Ví dụ:

```java
double angle = Math.asin(0.5);
System.out.printf(java.util.Locale.US, "%.1f%n",
        Math.toDegrees(angle)); // 30.0
```

**Điểm quan trọng:** `Math.asin(Math.sin(theta))` không luôn bằng `theta`. Nếu `theta` ở góc phần tư II, `sin(theta)` có thể trùng với một góc góc phần tư I, và `asin` sẽ trả kết quả trong khoảng `[−90°,90°]`. Ví dụ `sin(150°)≈0.5`; `asin(0.5)` trả `30°` chứ không phải `150°`.

### 4.1. Đầu vào ngoài miền và NaN

Nếu `|v| > 1` hoặc `v` là NaN, `Math.asin(v)` trả **NaN**, không ném `IllegalArgumentException` chỉ vì miền số học không hợp lệ.

```java
System.out.println(Double.isNaN(Math.asin(1.01))); // true
System.out.println(Double.isNaN(Math.asin(Double.NaN))); // true
```

`Math.asin(Double.POSITIVE_INFINITY)` và `Math.asin(Double.NEGATIVE_INFINITY)` cũng trả NaN vì độ lớn vượt 1.

Nếu input lẽ ra là tỉ số nằm `[−1,1]` nhưng phép tính do rounding ra `1.0000000000000002`, có thể bạn cần chính sách xử lý sai số. **Không tự động ép mọi input về [−1,1]**: clamp có thể che giấu dữ liệu sai như `5.0`; nếu nghiệp vụ cho phép clamp trong dung sai đã định, phải kiểm tra cách giá trị ra khỏi miền trước khi clamp.

### 4.2. Signed zero

`Math.asin(+0.0)` trả `+0.0`; `Math.asin(-0.0)` trả `-0.0`. Hai số so sánh bằng nhau theo toán tử primitive `==`, nhưng có bit dấu khác nhau. Nếu cần kiểm thử dấu zero, dùng `Double.doubleToRawLongBits` thay vì chỉ `==`.

## 5. Math.acos: arc cosine, chọn góc trong [0, π]

`Math.acos(v)` trả góc chính trong `[0,π]` với `cos(angle) = v`:

| v | Góc chính radian | Degree |
| ---: | --- | ---: |
| 1 | 0 | 0° |
| 0.5 | π/3 | 60° |
| 0 | π/2 | 90° |
| −0.5 | 2π/3 | 120° |
| −1 | π | 180° |

Ví dụ:

```java
double angle = Math.acos(0.5);
System.out.printf(java.util.Locale.US, "%.1f%n",
        Math.toDegrees(angle)); // 60.0
```

**Miền đầu vào là [−1,1]**, giống `asin`; nếu ngoài miền hoặc NaN, kết quả NaN. `Math.acos(1.0)` trả **+0.0** theo hợp đồng API. Với `Math.acos(-1.0)`, kết quả là giá trị `double` gần nhất với π.

Tính chất toán học hữu ích:

```text
acos(v) + asin(v) ≈ π/2  (v ∈ [−1,1])
```

Ký hiệu `≈` là có thể lệch một lượng nhỏ do làm tròn floating point. Không nên kiểm tra tổng bằng chính xác `Math.PI/2` ở mọi input.

### 5.1. Vì sao acos không thay thế atan2 để tìm hướng?

`acos(x/r)` chỉ trả góc trong `[0,π]`, không cho biết tọa độ `y` nằm **phía trên hay phía dưới** trục x. Nếu chỉ biết cos, góc `+60°` và `−60°` có cùng cosine. Từ một vector `(x,y)`, cách trực tiếp để biết dấu và góc phần tư là `atan2(y,x)`.

## 6. Math.atan: arc tangent của một tỉ số

`Math.atan(v)` là góc chính có tan bằng `v`; khoảng giá trị gần `(−π/2,π/2)` cho đối số hữu hạn. Điểm khác `asin`/`acos`: `atan` **không giới hạn input vào [−1,1]**.

Một số mốc:

| v | Kết quả radian | Degree |
| --- | --- | --- |
| −∞ | gần −π/2 | −90° |
| −1 | −π/4 | −45° |
| −0.0 | −0.0 | −0.0° |
| 0.0 | 0.0 | 0.0° |
| 1 | π/4 | 45° |
| +∞ | gần +π/2 | +90° |

Ví dụ:

```java
System.out.println(Math.atan(1.0)); 
// 0.7853981633974483 trên JDK được kiểm thử

System.out.println(Math.atan(Double.POSITIVE_INFINITY));
// 1.5707963267948966 trên JDK được kiểm thử
```

`Math.atan(Double.NaN)` trả NaN. `Math.atan(-0.0)` bảo toàn zero âm.

### 6.1. Khi đảo tangens, mất thông tin góc phần tư

Vì `tan(theta + π) = tan(theta)`, `atan` không thể khôi phục thông tin theta thuộc phần tư nào. Hơn nữa, khi dùng tỉ số `y/x`, bạn mất dấu riêng của `x`/`y`, đặc biệt nếu `x=0`.

Ví dụ hai vector `(1,1)` và `(−1,−1)` cùng có tỉ số `y/x = 1` nên:

```java
Math.atan(1.0 / 1.0);      // gần +45°
Math.atan(-1.0 / -1.0);    // gần +45° (không phải -135°)
```

Nhưng góc hướng từ trục +x của vector `(−1,−1)` là `−135°` theo quy ước của `atan2`. Đó là lý do không dùng một mình `atan(y/x)` cho góc vector tổng quát.

## 7. Math.atan2(y, x): biết góc phần tư từ cả hai tọa độ

Phương thức:

```java
double theta = Math.atan2(y, x);
```

Nhận **tọa độ y đầu tiên, tọa độ x thứ hai**. Đây là một quy ước cố định của Java (cũng xuất hiện trong nhiều thư viện toán học), dễ bị viết ngược do ta quen ghi điểm `(x,y)`.

Với vector `(x,y)` khác gốc tọa độ, góc chính thường nằm trong `[−π,+π]`:

```text
                          y+
                           ^
                 QII       |       QI
                  +135°    |    +45°
                           |
          x− --------------+--------------> x+
                           |
                  −135°    |    −45°
                 QIII      |       QIV
                           v
                          y−
```

Ví dụ bốn góc phần tư, in degree tới một chữ số:

| Tọa độ (x,y) | Cách gọi đúng | Góc |
| --- | --- | ---: |
| (1,1) | `atan2(1,1)` | +45° |
| (−1,1) | `atan2(1,−1)` | +135° |
| (−1,−1) | `atan2(−1,−1)` | −135° |
| (1,−1) | `atan2(−1,1)` | −45° |

`Math.atan2` xử lý góc phần tư dù `x` âm. Nếu chỉ dùng `Math.atan(y/x)`, góc thường bị đưa về khoảng ±90° và thiếu thông tin để phân biệt QI với QIII, QII với QIV.

### 7.1. Không thay atan2 bằng atan(y/x) khi x = 0

`x = 0` khiến biểu thức `y/x` có thể tạo ±Infinity hoặc NaN theo IEEE 754 (hoặc ném `ArithmeticException` nếu thao tác chia là integer). Với `atan2`, hai tọa độ được đưa vào trực tiếp nên có quy tắc cụ thể:

```java
System.out.println(Math.atan2(1.0, 0.0));
// 1.5707963267948966 ~ +90°

System.out.println(Math.atan2(-1.0, 0.0));
// -1.5707963267948966 ~ -90°
```

Khi `x` và `y` quá lớn hoặc quá nhỏ, phép chia `y/x` riêng lẻ còn có thể overflow/underflow và làm mất thông tin về tỷ lệ; `atan2` là API chuẩn để xử lý cặp tọa độ, không bắt buộc tạo tỉ số trung gian.

### 7.2. Cách diễn giải góc trên hình học

Trong hệ tọa độ toán học thông thường, trục `+x` hướng phải và `+y` hướng lên; góc dương quay **ngược kim đồng hồ**. Nhưng trên nhiều hệ tọa độ màn hình, `y` tăng khi đi **xuống dưới**. Lúc đó một vector pixel `(dx,dy)` truyền vào `atan2(dy,dx)` có chiều góc dương hướng xuống, dễ bị người dùng cảm nhận như quay thuận chiều kim đồng hồ.

Hãy **xác định hệ trục trước khi diễn giải dấu góc**. Nếu muốn chuyển tọa độ màn hình có y hướng xuống về tọa độ toán học y hướng lên, một cách trong trường hợp trục không bị biến đổi khác là `Math.atan2(-dy, dx)`.

### 7.3. Giới hạn principal angle và chuẩn hóa góc

`atan2` trả góc chính `[−π,+π]`, không tự trả giá trị trong `[0,2π)`. Nếu cần góc từ 0° đến nhỏ hơn 360°, quy tắc thông thường là cộng `2π` cho giá trị âm:

```java
double angle = Math.atan2(y, x);
if (angle < 0.0) {
    angle += 2.0 * Math.PI;
}
```

Đây là **mảnh code** cho góc hữu hạn thông thường. Nếu `angle` bằng `-0.0`, điều kiện `angle < 0.0` là false; nếu nghiệp vụ cần chuẩn hóa bit dấu của zero, hãy bổ sung quy tắc `angle == 0.0 ? 0.0 : angle`. Nếu `angle` là `NaN`, phép so sánh `angle < 0.0` false và NaN vẫn giữ nguyên, cần kiểm tra đầu vào riêng.

Do phép làm tròn ở ranh giới ±π/2π, có thể cần chuẩn hóa thêm ở điểm gần `2π` nếu yêu cầu đầu ra **chắc chắn nhỏ hơn 2π**. Không dùng vòng lặp trừ 2π thiếu điều kiện với một số NaN/Infinity; nên định nghĩa rõ miền dữ liệu và điều kiện chuẩn hóa.

## 8. Bảng quyết định: hàm nào phù hợp?

| Nhu cầu | Cách tiếp cận |
| --- | --- |
| Biết `sin(theta)` và cần một góc chính | `asin(s)`, xác thực `s ∈ [−1,1]` |
| Biết `cos(theta)` và cần góc từ 0 đến π | `acos(c)`, xác thực `c ∈ [−1,1]` |
| Biết riêng giá trị `tan(theta)` và chỉ cần góc chính | `atan(t)` |
| Biết điểm/vector `(x,y)` và cần hướng/góc phần tư | **`atan2(y,x)`** |
| Cần kết quả ở degree | `Math.toDegrees(angleRadians)` |
| Cần xử lý input đang ở degree cho sin/cos/tan | `Math.toRadians(degrees)` trước |
| Cần tính toán có yêu cầu bit-reproducible về thư viện lượng giác | Xem `StrictMath` và hợp đồng JDK cụ thể |

Phân biệt **lấy góc từ tọa độ** với **lấy độ dài vector**. Nếu cần bán kính `r = √(x²+y²)`, API thích hợp là `Math.hypot(x,y)`; góc lấy bằng `atan2(y,x)`. `atan2` tự nó chỉ trả góc, không trả độ lớn vector.

## 9. Chương trình Java đầy đủ A — bốn hàm cơ bản

Tạo tệp **`InverseTrigBasics.java`**:

```java
import java.util.Locale;

public class InverseTrigBasics {
    public static void main(String[] args) {
        System.out.printf(Locale.US, "asin(0.5) = %.1f deg%n",
                Math.toDegrees(Math.asin(0.5)));
        System.out.printf(Locale.US, "acos(0.5) = %.1f deg%n",
                Math.toDegrees(Math.acos(0.5)));
        System.out.printf(Locale.US, "atan(1)   = %.1f deg%n",
                Math.toDegrees(Math.atan(1.0)));
        System.out.printf(Locale.US, "atan2(1,1) = %.1f deg%n",
                Math.toDegrees(Math.atan2(1.0, 1.0)));

        System.out.println(Double.isNaN(Math.asin(1.01)));
        System.out.println(Double.isNaN(Math.acos(-1.01)));
    }
}
```

Chạy với JDK 17+:

```bash
javac --release 17 InverseTrigBasics.java
java InverseTrigBasics
```

**Stdout** (formatter US với một chữ số phần lẻ):

```text
asin(0.5) = 30.0 deg
acos(0.5) = 60.0 deg
atan(1)   = 45.0 deg
atan2(1,1) = 45.0 deg
true
true
```

Giải thích: đối số `0.5` hợp lệ với `asin` và `acos`; `atan(1)` cho π/4; `atan2(1,1)` là vector góc 45°. Các đối số `1.01` và `−1.01` vượt miền của `asin`/`acos` nên trả NaN, được kiểm tra bằng `Double.isNaN`.

Không khẳng định giá trị nội bộ luôn là **30.0 radian** chỉ vì formatter in `30.0 deg`: kết quả đã được **đổi radian sang degree** và trình bày với một chữ số phần lẻ.

## 10. Chương trình Java đầy đủ B — bốn góc phần tư

Tạo **`Atan2Quadrants.java`**:

```java
import java.util.Locale;

public class Atan2Quadrants {
    private static void show(double x, double y) {
        double angleDegrees = Math.toDegrees(Math.atan2(y, x));
        System.out.printf(Locale.US,
                "(x=%4.1f, y=%4.1f) -> %6.1f deg%n",
                x, y, angleDegrees);
    }

    public static void main(String[] args) {
        show( 1.0,  1.0);
        show(-1.0,  1.0);
        show(-1.0, -1.0);
        show( 1.0, -1.0);
    }
}
```

Chạy:

```bash
javac --release 17 Atan2Quadrants.java
java Atan2Quadrants
```

**Stdout:**

```text
(x= 1.0, y= 1.0) ->   45.0 deg
(x=-1.0, y= 1.0) ->  135.0 deg
(x=-1.0, y=-1.0) -> -135.0 deg
(x= 1.0, y=-1.0) ->  -45.0 deg
```

Chú ý chữ ký `show(x,y)` dùng thứ tự tọa độ quen thuộc, nhưng bên trong gọi `Math.atan2(y,x)`. Đây là cách đặt tên rõ ràng để tránh truyền sai thứ tự.

### 10.1. Vì sao công thức atan(y/x) trả sai QIII?

Với `x=-1`, `y=-1`:

```java
double byRatio = Math.toDegrees(Math.atan(y / x));
// khoảng +45°, đánh mất góc phần tư

double byVector = Math.toDegrees(Math.atan2(y, x));
// khoảng -135°, đúng hướng của vector
```

Đây là **mảnh code** cần khai báo `x`, `y` trước khi biên dịch. Trong ứng dụng thực tế, ưu tiên `atan2` khi đã có tọa độ, không thử sửa bằng cách cộng π tự phát trong từng nhánh if khi API chuẩn xử lý được.

## 11. Signed zero: hai mẫu zero có thể dẫn tới hai góc khác nhau

IEEE 754 có `+0.0` và `-0.0`. Chúng bằng nhau theo primitive `==`, nhưng có bit dấu khác nhau. `asin` và `atan` giữ dấu zero của đối số; `atan2` dùng dấu zero để chọn phía đường biên góc.

Bảng quy tắc `atan2(y,x)` tại trục tọa độ:

| y | x | `Math.atan2(y,x)` |
| --- | --- | --- |
| `+0.0` | `+1.0` | `+0.0` |
| `-0.0` | `+1.0` | `-0.0` |
| `+0.0` | `-1.0` | `+π` gần nhất |
| `-0.0` | `-1.0` | `-π` gần nhất |
| `+1.0` | `+0.0` | `+π/2` gần nhất |
| `-1.0` | `+0.0` | `-π/2` gần nhất |

**Rất quan trọng:** `Math.atan2(+0.0, -1.0)` và `Math.atan2(-0.0, -1.0)` trả hai góc khác dấu, xấp xỉ +π và −π. Chúng là hai phía của **branch cut** ở trục x âm.

### 11.1. atan2(0,0) không mặc nhiên là NaN

Trong toán học, hướng của vector zero tại gốc tọa độ không được xác định theo góc hình học. Nhưng Java `Math.atan2` vẫn có **quy tắc floating-point xác định** cho ±0 ở hai tọa độ:

```java
System.out.println(Math.atan2(+0.0, +0.0)); // 0.0
System.out.println(Math.atan2(-0.0, +0.0)); // -0.0
System.out.println(Math.atan2(+0.0, -0.0)); // gần +pi
System.out.println(Math.atan2(-0.0, -0.0)); // gần -pi
```

**Không đồng nhất “API trả một giá trị” với “vector có hướng hình học hợp lệ”.** Nếu ứng dụng không chấp nhận vector zero, hãy kiểm tra từ đầu:

```java
if (x == 0.0 && y == 0.0) {
    throw new IllegalArgumentException("Zero vector has no direction");
}
double angle = Math.atan2(y, x);
```

Đây là **mảnh code minh họa**; đặt trong method có khai báo `x`, `y`. Điều kiện `== 0.0` nhận cả zero dương và zero âm. Nếu đầu vào có thể chứa NaN hoặc Infinity, cần định nghĩa thêm chính sách hữu hạn.

## 12. Vô cực và NaN: hành vi của từng phương thức

`Math`/`StrictMath` xử lý các giá trị đặc biệt theo hợp đồng Java, không cần được bao ngoài bằng `try/catch` cho `ArithmeticException` trong các trường hợp domain số thực thông thường.

| Trường hợp | Kết quả |
| --- | --- |
| `Math.asin(1.1)` | NaN (vượt miền) |
| `Math.acos(-1.1)` | NaN (vượt miền) |
| `Math.asin(Double.NaN)` | NaN |
| `Math.acos(Double.POSITIVE_INFINITY)` | NaN |
| `Math.atan(Double.NaN)` | NaN |
| `Math.atan(Double.POSITIVE_INFINITY)` | gần +π/2 |
| `Math.atan(Double.NEGATIVE_INFINITY)` | gần −π/2 |
| `Math.atan2(Double.NaN, 1.0)` | NaN |
| `Math.atan2(1.0, Double.NaN)` | NaN |
| `Math.atan2(1.0, +0.0)` | gần +π/2 |
| `Math.atan2(-1.0, +0.0)` | gần −π/2 |

Với cả hai tọa độ vô cực, `atan2` có thêm quy tắc đặc biệt, không thể suy đơn giản từ tỷ số vô cực/vô cực (vốn là NaN):

| y | x | Góc |
| --- | --- | --- |
| +∞ | +∞ | +π/4 |
| +∞ | −∞ | +3π/4 |
| −∞ | +∞ | −π/4 |
| −∞ | −∞ | −3π/4 |

Điều này là một ví dụ khác cho thấy **`atan2(y,x)` không phải lời gọi `atan(y/x)` đơn thuần**.

### 12.1. Vì sao không so NaN bằng ==?

`NaN == NaN` trả false theo primitive floating-point equality, kể cả khi cùng tham chiếu tới giá trị `Double.NaN` sau unbox. Để phát hiện giá trị không hợp lệ:

```java
double result = Math.acos(1.1);

if (Double.isNaN(result)) {
    System.out.println("outside domain");
}
```

Muốn ràng buộc cả vô cực, dùng `Double.isFinite(result)` trong Java 17. Nếu nhận đầu vào từ người dùng, thông báo nguyên nhân phù hợp: `acos(1.1)` do vượt miền, còn `atan2(Double.NaN,1)` do tọa độ không phải một số hợp lệ.

### 12.2. Dấu zero có thể bị mất ở những bước khác

Nếu trước `atan2` bạn tính tỉ số `y/x`, việc chia hoặc các phép toán khác có thể làm tròn, overflow, underflow hoặc thay đổi dấu zero. Do đó, truyền trực tiếp các tọa độ cho `atan2` giúp bảo toàn nhiều thông tin cần thiết về dấu/góc hơn so với tự tính `atan(y/x)`.

## 13. Chương trình Java đầy đủ C — những trường hợp đặc biệt

Lưu **`InverseTrigEdges.java`**:

```java
public class InverseTrigEdges {
    public static void main(String[] args) {
        System.out.println(Double.isNaN(Math.asin(1.01)));
        System.out.println(Double.isNaN(Math.acos(-1.01)));

        System.out.println(Math.asin(-0.0));
        System.out.println(Math.atan(-0.0));
        System.out.println(Math.acos(1.0));

        System.out.println(Math.atan2(+0.0, -1.0));
        System.out.println(Math.atan2(-0.0, -1.0));
        System.out.println(Math.atan2(+0.0, +1.0));
        System.out.println(Math.atan2(-0.0, +1.0));

        System.out.println(Math.atan2(1.0, +0.0));
        System.out.println(Math.atan2(-1.0, +0.0));

        System.out.println(Math.atan2(+0.0, +0.0));
        System.out.println(Double.isNaN(
                Math.atan2(Double.NaN, 1.0)));
    }
}
```

Chạy:

```bash
javac --release 17 InverseTrigEdges.java
java InverseTrigEdges
```

**Stdout trên JDK được kiểm thử:**

```text
true
true
-0.0
-0.0
0.0
3.141592653589793
-3.141592653589793
0.0
-0.0
1.5707963267948966
-1.5707963267948966
0.0
true
```

Các mốc đặc biệt như ±0, ±π, ±π/2, NaN được nêu trong hợp đồng API. Khi dùng `Math` trên các JDK/nền tảng khác nhau, **không nên lấy mọi chữ số thập phân của các góc không đặc biệt làm cam kết bit-for-bit**. `StrictMath` được định nghĩa chặt hơn cho một số hàm để tăng khả năng tái lập kết quả giữa các implementation.

## 14. Math so với StrictMath: trùng chức năng nhưng khác mức cam kết tái lập

Java cung cấp cả:

```java
double a = Math.asin(0.5);
double b = StrictMath.asin(0.5);

double c = Math.acos(0.5);
double d = StrictMath.acos(0.5);

double e = Math.atan(1.0);
double f = StrictMath.atan(1.0);

double g = Math.atan2(1.0, -1.0);
double h = StrictMath.atan2(1.0, -1.0);
```

Cả hai lớp dùng **radian**, cùng ý nghĩa toán học và cùng kiểu trả về `double`. Điểm khác không phải `Math` “sai”, mà là **chính sách đảm bảo kết quả theo thuật toán**.

### 14.1. Math: độ chính xác đủ theo đặc tả, implementation được linh hoạt

Java SE 17 `Math.asin`/`acos`/`atan` quy định sai số trong **1 ulp** của kết quả toán học chính xác, với yêu cầu **semi-monotonic**. `Math.atan2` cho phép sai số trong **2 ulp** và có yêu cầu semi-monotonic theo hợp đồng API.

`ulp` là “unit in the last place”, nói khái quát là khoảng cách giữa các giá trị floating-point biểu diễn được quanh một số. Sai số trong 1–2 ulp là một ràng buộc có ý nghĩa với binary64, **không phải** “sai số cố định nhỏ hơn 0.000001 radian cho mọi giá trị” hay “luôn làm tròn chính xác nhất”.

Các implementation của `Math` được phép sử dụng thuật toán hoặc chỉ thị tối ưu phù hợp với nền tảng, miễn là thỏa hợp đồng chính xác và special cases.

### 14.2. StrictMath: nhắm đến kết quả dễ tái lập hơn

Theo tài liệu Java SE 17, một số hàm của `StrictMath` (bao gồm các hàm lượng giác đang học) được ràng buộc theo thuật toán **fdlibm 5.3** (Freely Distributable Math Library) được mô tả trong API, để hỗ trợ kết quả **bit-for-bit phù hợp giữa các implementation Java tuân chuẩn**.

Điều đó không có nghĩa:

- Kết quả `StrictMath` biểu diễn số thực toán học bằng **độ chính xác vô hạn**.
- Mọi hàm của `StrictMath` đều có cùng cơ chế fdlibm hoặc cùng chính sách làm tròn trong mọi trường hợp.
- `Math.asin(x)` **phải luôn khác** `StrictMath.asin(x)`: trên nhiều JDK chúng có thể cho chính cùng bit.
- Dùng `StrictMath` làm phép so sánh `==` của những kết quả tính toán có tính ổn định tuyệt đối cho mọi thuật toán cộng/trừ/nhân/chia khác: những phép toán trung gian vẫn có rounding.

**Hướng chọn:** với ứng dụng thông thường cần hàm toán học có độ chính xác bảo đảm bởi JDK, `Math` là lựa chọn trực tiếp. Khi hệ thống yêu cầu kết quả lượng giác dễ đối chiếu **bit-for-bit giữa các implementation** cho cùng đầu vào (kiểm thử hồi quy, mô phỏng xác định có quy định), xem xét `StrictMath` và tài liệu phiên bản đích.

### 14.3. strictfp không thay thế StrictMath

Trong Java 17, ngữ nghĩa floating-point expression luôn được đánh giá nghiêm ngặt theo JLS, và modifier `strictfp` đã trở nên không cần thiết cho mục đích ép strict evaluation của phép toán primitive. Nhưng điều đó **không biến** `Math` thành các hàm fdlibm của `StrictMath`. Câu hỏi về **thuật toán thư viện tính asin/acos/atan/atan2** khác câu hỏi về **precision của các phép toán primitive**.

## 15. Chương trình Java đầy đủ D — cùng API ở Math và StrictMath

Tạo tệp **`MathVersusStrictMath.java`**:

```java
import java.util.Locale;

public class MathVersusStrictMath {
    static void show(String name, double math, double strict) {
        System.out.printf(Locale.US,
                "%s -> Math: %.6f, StrictMath: %.6f%n",
                name, math, strict);
    }

    public static void main(String[] args) {
        show("asin", Math.asin(0.5), StrictMath.asin(0.5));
        show("acos", Math.acos(0.5), StrictMath.acos(0.5));
        show("atan", Math.atan(1.0), StrictMath.atan(1.0));
        show("atan2", Math.atan2(1.0, -1.0),
                StrictMath.atan2(1.0, -1.0));
    }
}
```

Chạy:

```bash
javac --release 17 MathVersusStrictMath.java
java MathVersusStrictMath
```

**Stdout (làm tròn hiển thị tới 6 chữ số lẻ):**

```text
asin -> Math: 0.523599, StrictMath: 0.523599
acos -> Math: 1.047198, StrictMath: 1.047198
atan -> Math: 0.785398, StrictMath: 0.785398
atan2 -> Math: 2.356194, StrictMath: 2.356194
```

**Cách hiểu:** ví dụ cho cùng chữ số thập phân sau khi format **không chứng minh** hai kết quả giống hệt nhau ở mọi bit hoặc mọi đầu vào. Muốn đánh giá tính tái lập thật sự, cần so sánh raw bits `Double.doubleToRawLongBits` trên tập input đại diện và nhiều JDK/kiến trúc được hỗ trợ. Đừng xây dựng một bài kiểm thử bắt buộc `Math` và `StrictMath` luôn bit-equal khi hợp đồng không đòi điều ấy.

## 16. Ứng dụng: hướng của chuyển động, tọa độ và vector

Giả sử một vật đi từ `(x1,y1)` tới `(x2,y2)` trên hệ trục Descartes. Vector chuyển động:

```text
dx = x2 - x1
dy = y2 - y1
theta = atan2(dy, dx)
```

Ví dụ một chuyển động từ `(1,2)` tới `(−2,5)`:

```java
double x1 = 1.0;
double y1 = 2.0;
double x2 = -2.0;
double y2 = 5.0;

double dx = x2 - x1; // -3
double dy = y2 - y1; // +3

double heading = Math.atan2(dy, dx); // QII
System.out.printf(java.util.Locale.US,
        "%.1f degrees%n", Math.toDegrees(heading));
// 135.0 degrees
```

Đoạn này **minh họa một phương thức** (hoặc khối bên trong `main`), không phải một file Java đầy đủ riêng. Góc +135° đúng vì dx âm và dy dương.

### 16.1. Vector length ≠ angle

`Math.hypot(dx,dy)` cho độ lớn vector. Nó tránh một số nguy cơ overflow/underflow trung gian so với tự tính `Math.sqrt(dx*dx + dy*dy)`.

```java
double length = Math.hypot(dx, dy);
double angle = Math.atan2(dy, dx);
```

Hai kết quả lần lượt là **độ dài** và **góc**. Nếu vector bằng zero, độ dài bằng 0 nhưng hướng thường không được định nghĩa trong bài toán hình học, dù `atan2` của Java vẫn trả giá trị theo signed-zero inputs.

### 16.2. Chuẩn hóa nếu domain muốn [0,360)

`Math.toDegrees(Math.atan2(dy,dx))` trả gần `[−180,180]`. Nếu muốn hướng từ 0 đến nhỏ hơn 360 độ:

```java
double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
if (angleDeg < 0.0) {
    angleDeg += 360.0;
}
```

Điều này phù hợp với input hữu hạn trong tình huống thông thường. Với signed zero và các điểm ranh giới −180/+180, quy ước chính xác cần được mô tả và kiểm thử. Nếu hệ tọa độ màn hình dùng y tăng xuống dưới, chiều góc cũng phải được điều chỉnh trước khi hiển thị.

## 17. Những lỗi thường gặp khi dùng inverse trig

| Sai lầm | Điều gì xảy ra | Cách đúng |
| --- | --- | --- |
| Cho rằng `asin(0.5)` trả `30` | Kết quả là **radian** | Dùng `toDegrees` khi cần degree |
| Gọi `asin(30)` với ý nghĩa 30° | 30 ngoài miền [−1,1] → NaN | Phân biệt giá trị sin với góc |
| Dùng `acos(1.0001)` rồi mong 0 | Đối số vượt miền → NaN | Xác thực input; clamp chỉ theo chính sách sai số |
| Dùng `atan(y/x)` tìm góc phần tư | Mất dấu riêng x,y | Dùng `atan2(y,x)` |
| Gọi `atan2(x,y)` | Đổi nhầm tham số | Hàm **atan2(y,x)** |
| Dùng `atan2(0,0)` như hướng vector hợp lệ | API trả số theo signed zero nhưng hình học không có hướng duy nhất | Kiểm tra vector zero nếu cần |
| Bỏ qua zero âm | Có thể đổi ±π ở branch cut | Bảo toàn signed zero hoặc chuẩn hóa có chủ đích |
| So NaN bằng `== Double.NaN` | Luôn false | `Double.isNaN` |
| Gán `Math.atan(1.0)` trực tiếp cho `float` | Type trả `double`, cần cast và có thể mất precision | Dùng `double` nếu không có yêu cầu đặc biệt |
| Coi `asin(sin(t))==t` luôn đúng | Sin có tính tuần hoàn, asin chỉ trả principal value | Xem miền và điều kiện duy nhất |
| Gán “Math lúc nào cũng nhanh hơn StrictMath” | Hiệu năng phụ thuộc JDK/hardware | Benchmark trên môi trường thật |
| Coi `StrictMath` luôn chính xác tuyệt đối | Vẫn là `double` có rounding | Hiểu fdlibm và precision |
| So kết quả góc bằng `==` với số thập phân hiển thị | Sai số biểu diễn/rounding | Chọn tolerance theo đơn vị và độ chính xác bài toán |

Một bẫy tinh tế là **dùng sin và cos được tính từ góc rồi đảo lại**: `Math.atan2(Math.sin(theta), Math.cos(theta))` trả **phiên bản chuẩn hóa của theta về góc chính**, không bảo đảm đúng theta gốc nếu theta ngoài `[−π,π]`. Các sai số floating point cũng có thể xuất hiện gần ranh giới và tại bội của π.

## 18. Thiết kế kiểm thử đúng cho toán học dấu phẩy động

**Không kiểm tra mọi giá trị bằng một epsilon cố định không có ngữ cảnh.** Nếu ứng dụng chấp nhận sai số về góc là 0,01 độ, hãy đổi các giá trị cần kiểm tra sang degree hoặc đổi dung sai sang radian, đồng thời xác định cách so sánh tại đường biên ±180°.

Các bài kiểm thử nên gồm:

1. Giá trị chuẩn: `asin(0.5)` ≈ 30°, `acos(0.5)` ≈ 60°, `atan(1)` ≈ 45°.
2. Hai biên miền: `asin(−1)`, `asin(1)`, `acos(−1)`, `acos(1)`.
3. Ngoài miền: `asin(1.01)`, `acos(−1.01)` → NaN.
4. Bốn góc phần tư: `atan2(±1, ±1)`.
5. Trục tọa độ: `atan2(±0.0, ±1.0)` và `atan2(±1.0, ±0.0)`.
6. Gốc tọa độ: xác định chính sách xử lý zero vector.
7. Giá trị đặc biệt: ±Infinity, NaN.
8. Tọa độ rất lớn/rất nhỏ: không tự chia `y/x` hoặc bình phương mất kiểm soát.
9. Ranh giới ±π: quan sát branch cut và quy ước chuẩn hóa góc.
10. Đối chiếu `Math` và `StrictMath` theo yêu cầu tái lập thực sự, không dựa trên chuỗi đã làm tròn.

### 18.1. Khoảng cách góc phải tính theo vòng tròn khi cần

Hai góc `179.9°` và `−179.9°` cách nhau **0.2° theo hướng ngắn nhất**, không phải gần 359.8° như phép trừ số thập phân thông thường. Khi cần so **hướng trên vòng tròn**, hãy định nghĩa phép tính chênh lệch góc có chuẩn hóa mod 360°/2π.

Một công thức hữu ích cho độ lệch góc principal trong radian là:

```java
static double angleDistance(double a, double b) {
    return Math.abs(Math.atan2(
            Math.sin(a - b), Math.cos(a - b)));
}
```

Đây là **mảnh phương thức** cần đặt trong một class; nó hữu ích cho góc hữu hạn bình thường nhưng phép trừ `a-b` với số cực lớn/Infinity có thể tràn hoặc tạo NaN, và kết quả vẫn chịu rounding. Nếu hệ thống xử lý góc rất lớn hoặc đặc biệt, chuẩn hóa góc cẩn thận trước và xác thực `Double.isFinite`.

## 19. Bài tập tự luyện có đáp án

### Bài 1 — Đọc đúng ý nghĩa hàm

`Math.asin(0.5)` trả khoảng bao nhiêu radian và bao nhiêu độ?

**Đáp án:** gần `0.5235987756` radian, tương đương `30°`. Dùng `Math.toDegrees` nếu muốn kết quả độ.

### Bài 2 — Góc của acos

`Math.acos(0.5)` và `Math.acos(-1.0)` tương ứng bao nhiêu độ?

**Đáp án:** `60°` và `180°`. `acos` trả góc chính trong `[0°,180°]`.

### Bài 3 — Góc của atan

`Math.atan(1.0)` tương ứng bao nhiêu độ?

**Đáp án:** `45°`, tức gần π/4 radian.

### Bài 4 — Giá trị ngoài miền

`Math.asin(2.0)` và `Math.acos(-2.0)` ném exception hay cho gì?

**Đáp án:** trả `NaN`; kiểm tra bằng `Double.isNaN`. Không có góc thực mà sin/cos bằng ±2.

### Bài 5 — Bốn góc phần tư

Với tọa độ `(x,y) = (−1,1)` và `(−1,−1)`, gọi `atan2` thế nào, kết quả góc gì?

**Đáp án:** `Math.atan2(1,-1)` ≈ +135°; `Math.atan2(-1,-1)` ≈ −135°. `y` là tham số đầu tiên.

### Bài 6 — Vì sao không dùng atan(y/x)?

Với vector `(−1,−1)`, `atan(y/x)` có thể trả khoảng `+45°`, còn `atan2(y,x)` trả `−135°`. Tại sao?

**Đáp án:** phép chia mất dấu riêng của hai tọa độ: `(−1)/(−1) = +1`, giống tỉ số của vector `(1,1)`. `atan2` vẫn thấy x âm và y âm nên chọn QIII.

### Bài 7 — Signed zero trên trục x âm

`atan2(+0.0,-1.0)` và `atan2(-0.0,-1.0)` là góc gì?

**Đáp án:** gần +π và −π. Dấu zero quyết định phía branch cut.

### Bài 8 — Zero vector

`Math.atan2(0.0,0.0)` có cho NaN không? Có nên xem đó là hướng hình học hợp lệ không?

**Đáp án:** theo Java, cho `+0.0` với hai zero dương. Nhưng vector (0,0) không có hướng xác định, nên ứng dụng cần kiểm tra riêng nếu yêu cầu vector khác 0.

### Bài 9 — Infinity

`Math.atan(Double.POSITIVE_INFINITY)` và `Math.atan(Double.NEGATIVE_INFINITY)` tiến đến góc nào?

**Đáp án:** gần +π/2 và −π/2; theo contract Java, trả giá trị double gần nhất với hai mốc đó.

### Bài 10 — Hai tọa độ infinity

`Math.atan2(Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)` gần góc bao nhiêu?

**Đáp án:** +3π/4 = 135°. `atan(y/x)` không thay thế được vì `Infinity / -Infinity` là NaN.

### Bài 11 — Math và StrictMath

Có được khẳng định `Math.atan2(y,x) == StrictMath.atan2(y,x)` với **mọi** giá trị không? Vì sao?

**Đáp án:** không nên khẳng định như vậy. `Math` cho phép implementation linh hoạt trong giới hạn chính xác; `StrictMath` có ràng buộc thuật toán fdlibm để hỗ trợ tái lập bit cho một số method.

### Bài 12 — Radian/degree

Viết một biểu thức Java để lấy góc độ của vector `(x,y)`.

**Đáp án:** `Math.toDegrees(Math.atan2(y,x))`. Nếu muốn góc trong [0,360), thêm chuẩn hóa theo yêu cầu và kiểm tra signed zero/ràng buộc biên.

### Bài 13 — Góc principal

Vì sao `asin(sin(150°))` không cho 150°?

**Đáp án:** `sin(150°)=sin(30°)`, trong khi `asin` chỉ trả góc trong [−90°,90°], nên kết quả chính là gần 30°; phải đổi 150° sang radian trước khi gọi sin.

### Bài 14 — Tọa độ màn hình

Trên màn hình, y tăng hướng xuống. Khi rê chuột sang phải và xuống, `atan2(dy,dx)` trả góc dương hay âm? Muốn góc dương theo chiều ngược kim đồng hồ toán học phải làm gì?

**Đáp án:** nếu dx>0 và dy>0 thì `atan2(dy,dx)` trả góc dương trong góc phần tư I của hệ tọa độ số truyền vào; trên màn hình, nó trông như hướng xuống-phải. Muốn quy ước y hướng lên, dùng `atan2(-dy,dx)` nếu không có biến đổi tọa độ khác.

### Bài 15 — Kiểm tra độ chính xác

Nếu ứng dụng cần so góc ở gần +180° và −180°, vì sao `Math.abs(a-b)` có thể đánh giá sai “gần nhau”?

**Đáp án:** khoảng góc quanh vòng tròn có tính chu kỳ. Hai hướng ±179.9° chỉ chênh 0.2° theo cung ngắn; cần chuẩn hóa chênh lệch góc modulo 360° hoặc 2π theo contract của ứng dụng.

## 20. Checklist và tài liệu tham khảo

- [ ] Tôi nhớ `asin` và `acos` nhận đối số trong [−1,1], còn `atan` nhận số thực ngoài khoảng đó.
- [ ] Tôi biết khoảng kết quả chính của từng hàm và đơn vị **radian**.
- [ ] Tôi hiểu `asin(sin(theta))`, `acos(cos(theta))`, `atan(tan(theta))` không luôn khôi phục theta gốc.
- [ ] Tôi luôn viết `atan2(y,x)` khi có tọa độ, **không đảo tham số**.
- [ ] Tôi không dùng `atan(y/x)` thay cho `atan2` khi cần phân biệt góc phần tư và xử lý x=0.
- [ ] Tôi kiểm tra input của `asin`/`acos` thay vì âm thầm che giấu dữ liệu sai.
- [ ] Tôi biết `asin`/`atan` bảo toàn signed zero; `atan2` có quy tắc cho ±0 ở các trục.
- [ ] Tôi biết `atan2(0,0)` có output theo IEEE 754 nhưng vector zero có thể bị coi là không hợp lệ theo nghiệp vụ.
- [ ] Tôi kiểm tra `Double.isNaN`/`Double.isFinite` với input và output phù hợp.
- [ ] Tôi phân biệt yêu cầu chính xác 1 ulp/2 ulp của `Math` với ràng buộc fdlibm của `StrictMath`.
- [ ] Tôi tự viết được ví dụ vector dx/dy và đổi kết quả sang độ.
- [ ] Tôi kiểm thử bốn phần tư, trục tọa độ, ±π, signed zero, NaN và infinity.
- [ ] Tôi hiểu bài toán góc trên **vòng tròn** khác so sánh hai số thực trên một trục thẳng.
- [ ] Tôi đã chạy các chương trình Java 17+ và đối chiếu stdout.

**Tài liệu chính thức Java SE 17:**

- [Math API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `asin`, `acos`, `atan`, `atan2`, `toDegrees`, `toRadians`, đặc tả về ulp và special cases.
- [StrictMath API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html): các hàm tương ứng, quy tắc fdlibm và tính tái lập.
- [Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `NaN`, ±Infinity, signed zero, `doubleToRawLongBits`.
- [JLS 17 `4.2.3 Floating-Point Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): số thực Java.
- [JLS 17 `15.4 Floating-Point Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.4): precision, rounding và ngữ nghĩa biểu thức trong Java 17.

**Kết luận:** `asin`, `acos`, `atan` tìm **góc chính** từ giá trị lượng giác, còn `atan2(y,x)` tìm **hướng vector dựa trên cả hai tọa độ**. Trong code hình học Java, hãy ưu tiên `atan2` khi có x/y, nhớ radian, xem xét signed zero/NaN và chọn `Math` hay `StrictMath` theo đúng yêu cầu về độ chính xác và tái lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-006">hypot và việc giảm nguy cơ overflow trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# `Math.hypot` và việc giảm nguy cơ overflow trung gian trong Java

## 1. Bài toán: tính căn của tổng hai bình phương

Trong hình học phẳng, độ dài cạnh huyền của tam giác vuông với hai cạnh góc vuông `x`, `y` là:

\[
h=\sqrt{x^2+y^2}.
\]

Công thức này cũng là chuẩn Euclid hai chiều của vectơ `(x, y)` và xuất hiện trong phép tính khoảng cách giữa hai điểm, độ lớn vận tốc, tín hiệu phức, thống kê và các thuật toán số.

Một cách viết trực tiếp bằng Java:

```java
double x = 3.0;
double y = 4.0;
double h = Math.sqrt(x * x + y * y);

System.out.println(h); // 5.0
```

Ở ví dụ nhỏ, công thức hoạt động tốt. Nhưng ở thang giá trị rất lớn hoặc rất nhỏ, các phép **bình phương trung gian** có thể vượt ra ngoài miền biểu diễn hoặc làm tròn về 0, ngay cả khi **kết quả cuối cùng vẫn biểu diễn được** bằng `double`.

Java cung cấp API phù hợp hơn:

```java
double h = Math.hypot(x, y);
```

`Math.hypot(double x, double y)` tính độ lớn tương ứng với \(\sqrt{x^2+y^2}\) bằng thuật toán nhằm tránh overflow/underflow **không cần thiết ở bước trung gian**. `StrictMath.hypot(double,double)` cũng có chức năng cùng ý nghĩa.

**Ghi nhớ ngay:** khi thực sự muốn tính `sqrt(x*x + y*y)` trên số thực hữu hạn, hãy ưu tiên `Math.hypot(x, y)` thay vì tự tạo hai bình phương. Tuy nhiên, `hypot` không thể làm cho một kết quả thực sự quá lớn trở thành số hữu hạn, và không sửa dữ liệu đã mất chính xác từ trước khi gọi.

## 2. Hai lỗi số học khác nhau: overflow và underflow trung gian

### 2.1. Overflow trung gian khi bình phương

`double` chỉ biểu diễn được số hữu hạn đến khoảng:

\[
\texttt{Double.MAX_VALUE}\approx 1.7976931348623157\times10^{308}.
\]

Với `x = y = 1e200`, mỗi bình phương toán học là `1e400`, lớn hơn miền hữu hạn của `double`. Vì vậy:

```java
double x = 1e200;
double y = 1e200;

double naive = Math.sqrt(x * x + y * y);
double stable = Math.hypot(x, y);

System.out.println(naive);  // Infinity
System.out.println(stable); // 1.414213562373095E200
```

Kết quả toán học thực sự là:

\[
\sqrt{(10^{200})^2+(10^{200})^2}
=\sqrt{2}\cdot 10^{200}
\approx 1.414213562373095\times10^{200}.
\]

Giá trị cuối vẫn nhỏ hơn `Double.MAX_VALUE`. `Infinity` ở cách trực tiếp là **overflow giả do cách sắp xếp phép tính**, không phải vì kết quả toán học không chứa được trong `double`.

Trong biểu thức trực tiếp, `x * x` đã thành `Infinity` trước khi `Math.sqrt` có cơ hội lấy căn. Một khi bước trung gian đã thành vô cực, căn không thể phục hồi giá trị hữu hạn vốn có.

### 2.2. Underflow trung gian khi bình phương

`Double.MIN_VALUE` là số `double` **dương khác 0 nhỏ nhất**, khoảng `4.94E-324`. Giá trị `Double.MIN_NORMAL` là ngưỡng số chuẩn hóa dương nhỏ nhất; phía dưới vẫn có số dưới chuẩn (*subnormal*).

Với `x = y = 1e-200`, bình phương toán học là `1e-400`, quá nhỏ để `double` giữ khác 0. Do đó:

```java
double x = 1e-200;
double y = 1e-200;

double naive = Math.sqrt(x * x + y * y);
double stable = Math.hypot(x, y);

System.out.println(naive);  // 0.0
System.out.println(stable); // 1.414213562373095E-200
```

Kết quả cuối khoảng `1.414E-200`, **lớn hơn rất nhiều** so với `Double.MIN_NORMAL` và hoàn toàn có thể biểu diễn như số hữu hạn chuẩn hóa. Nhưng công thức trực tiếp đánh mất cả hai bình phương trước khi lấy căn.

Điểm chung của hai lỗi: thực hiện phép toán trung gian ở **thang độ lớn quá cao hoặc quá thấp** so với kết quả cuối. Chọn lại cấu trúc thuật toán có thể tránh phải biểu diễn các số trung gian nguy hiểm ấy.

## 3. `Math.hypot` thực hiện điều gì và bảo đảm gì?

Chữ *hypot* viết tắt từ *hypotenuse* (cạnh huyền). Chữ ký phương thức trong Java 17:

```java
public static double hypot(double x, double y)
```

Ý nghĩa toán học:

\[
\operatorname{hypot}(x,y)=\sqrt{x^2+y^2}.
\]

Đối với đối số hữu hạn, kết quả là độ lớn không âm của vectơ `(x,y)`, được tính theo quy tắc số học dấu phẩy động. Thư viện tiêu chuẩn dùng thuật toán số để **giảm nguy cơ tràn hoặc mất dữ liệu trung gian** so với bình phương trực tiếp.

Một vài tính chất đáng nhớ:

- Thứ tự hai đối số không thay đổi giá trị toán học: `hypot(x,y) = hypot(y,x)`.
- Dấu của đối số không thay đổi độ dài: `hypot(-x,y) = hypot(x,y)` về mặt toán học.
- Nếu `x = 0` và `y` hữu hạn, giá trị toán học là `abs(y)`.
- Kết quả hữu hạn không âm; khi kết quả lý tưởng quá lớn để biểu diễn, kết quả có thể là `Infinity`.
- Theo tài liệu API `Math.hypot`, kết quả số hữu hạn có sai số trong khoảng giới hạn **1 ULP** của kết quả chính xác và đáp ứng các yêu cầu chất lượng số được nêu trong đặc tả Java.

`hypot` **không** có nghĩa là tính toán với độ chính xác vô hạn hay hoàn toàn chính xác cho mọi cặp đầu vào. Đó là một phép tính dấu phẩy động có làm tròn, nhưng giảm được một số nguy cơ lớn do biểu thức ngây thơ gây ra.

### Vì sao giá trị trả về là `double`?

Trong Java 17, chữ ký `Math.hypot` nhận hai đối số `double` và trả `double`; không có overload `hypot(float,float)` trả `float`.

```java
float fx = 3.0f;
float fy = 4.0f;

double accurateRange = Math.hypot(fx, fy); // float được nâng sang double
float narrowed = (float) Math.hypot(fx, fy); // có ép kiểu xuống float
```

Nếu ép kết quả về `float`, phép chuyển đổi cuối có thể làm tròn hoặc overflow dù `Math.hypot` trả `double` hữu hạn. Sử dụng kiểu kết quả đúng với miền giá trị và yêu cầu độ chính xác.

## 4. Ý tưởng kỹ thuật: đổi thang trước khi bình phương

Để hiểu cơ chế mà không cần biết chi tiết triển khai của JDK, ta xem xét một biến đổi đại số.

Với `x`, `y` hữu hạn, đặt:

\[
a=\max(|x|,|y|),\quad b=\min(|x|,|y|).
\]

Nếu `a > 0`, thì:

\[
\sqrt{x^2+y^2}
=a\sqrt{1+\left(\frac{b}{a}\right)^2}.
\]

Bởi vì `0 <= b/a <= 1`, việc **bình phương tỷ số** không tạo ra số lớn bất thường. Phép nhân với `a` được dời về cuối, khi đã có độ lớn gần kết quả cần tìm.

Ví dụ với `x = y = 1e200`:

\[
a=10^{200},\quad b/a=1,\quad
h=10^{200}\sqrt{2}.
\]

Thay vì tính `1e400` rồi lấy căn, thuật toán chỉ cần bình phương số `1`. Với `x = y = 1e-200` cũng vậy: tỷ số bằng `1`, không phải bình phương trực tiếp thành `1e-400`.

### Phác thảo thuật toán để học nguyên lý

Phương thức sau chỉ minh họa phép đổi thang, **không phải bản tái triển khai thay thế hoàn chỉnh của `Math.hypot`**:

```java
static double scaledExample(double x, double y) {
    if (Double.isInfinite(x) || Double.isInfinite(y)) {
        return Double.POSITIVE_INFINITY;
    }
    if (Double.isNaN(x) || Double.isNaN(y)) {
        return Double.NaN;
    }

    double a = Math.max(Math.abs(x), Math.abs(y));
    double b = Math.min(Math.abs(x), Math.abs(y));

    if (a == 0.0) {
        return 0.0; // Tránh 0/0 khi cả hai đối số là 0.
    }

    double ratio = b / a; // 0 <= ratio <= 1.
    return a * Math.sqrt(1.0 + ratio * ratio);
}
```

Đoạn trên giải thích tại sao **đưa tỷ lệ về khoảng [0,1]** làm giảm nguy cơ overflow của phép bình phương. Tuy nhiên, một thư viện số chất lượng cao còn phải xử lý chi tiết làm tròn, chênh lệch số mũ rất lớn, số dưới chuẩn, tràn khi nhân cuối, tính chính xác 1 ULP và các trường hợp biên. Bản phác thảo này **không cam kết** cùng chất lượng số với `Math.hypot` trên toàn miền.

`Math.hypot` là lựa chọn mặc định tốt hơn việc tự viết hàm tương đương, trừ khi có yêu cầu chuyên sâu và có kiểm thử số học đầy đủ.

### Tại sao tỷ số có thể làm tròn về 0 mà vẫn hữu ích?

Nếu `a` rất lớn còn `b` vô cùng nhỏ so với `a`, tỷ số `b/a` có thể bị làm tròn thành `0`. Khi đó:

\[
\sqrt{a^2+b^2}\approx a.
\]

Đóng góp của `b` thực sự có thể quá nhỏ so với độ phân giải của kết quả `double` ở cỡ `a`, nên kết quả làm tròn vẫn là `a`. Tuy nhiên, điều này không có nghĩa `b` bằng 0 về mặt toán học; yêu cầu độ chính xác cao hơn phải dùng mô hình số phù hợp.

## 5. `hypot` tránh lỗi trung gian nhưng không tránh tràn của kết quả thật

Một điểm dễ nhầm: `Math.hypot` **không bảo đảm kết quả luôn hữu hạn**.

```java
double x = Double.MAX_VALUE;
double y = Double.MAX_VALUE;

System.out.println(Math.hypot(x, y)); // Infinity
```

Ở đây độ dài toán học bằng \(\sqrt2 \cdot \texttt{Double.MAX_VALUE}\), vượt giá trị hữu hạn tối đa. Overflow ở **kết quả cuối** là một giới hạn không thể giải quyết bằng việc đổi thứ tự phép tính trong cùng kiểu `double`.

Ngược lại:

```java
System.out.println(Math.hypot(1e308, 1e308));
// 1.4142135623730951E308
```

Độ dài toán học vẫn dưới `Double.MAX_VALUE` nên `Math.hypot` có thể trả một số hữu hạn, dù cách `Math.sqrt(x*x + y*y)` đã overflow ở bình phương.

Khi thiết kế chương trình, cần phân biệt:

- **Overflow trung gian có thể tránh:** bình phương hoặc cộng số trung gian quá lớn nhưng kết quả cần tìm vẫn nằm trong miền kiểu.
- **Overflow kết quả không thể tránh bằng `hypot`:** độ dài thực sự lớn hơn miền `double`.
- **Sai số làm tròn vẫn còn:** giá trị toán học nằm trong miền hữu hạn nhưng không có biểu diễn nhị phân chính xác.

Nếu ứng dụng bắt buộc kết quả hữu hạn, vẫn nên xác thực:

```java
double length = Math.hypot(x, y);
if (!Double.isFinite(length)) {
    throw new ArithmeticException("Length is not finite");
}
```

Tùy hợp đồng, có thể cần phân biệt đầu vào vô cực/NaN với overflow do độ lớn kết quả; chỉ nhìn kết quả cuối không đủ cho mọi yêu cầu chẩn đoán.

## 6. `Infinity`, `NaN` và số 0 có dấu

Các phương thức dấu phẩy động của Java có hành vi xác định cho giá trị đặc biệt.

### Vô cực và NaN

```java
System.out.println(Math.hypot(Double.POSITIVE_INFINITY, Double.NaN));
// Infinity

System.out.println(Math.hypot(Double.NaN, 4.0));
// NaN
```

Quy tắc quan trọng của `hypot`: nếu **một đối số là vô cực**, kết quả là vô cực dương, **kể cả khi đối số kia là NaN**. Nếu không có đối số vô cực mà một đối số là `NaN`, kết quả là `NaN`.

Không suy diễn quy tắc này từ việc thay trực tiếp giá trị vào `x*x + y*y`, vì phép cộng với NaN trong biểu thức thông thường có thể tạo `NaN` trong tình huống `hypot` trả vô cực.

### Dấu âm và signed zero

Độ dài Euclid là độ lớn, nên kết quả không âm đối với đối số số thực. Nếu hai đối số đều là số 0, kể cả `-0.0`, kết quả là `+0.0`:

```java
double length = Math.hypot(-0.0, -0.0);

System.out.println(length); // 0.0
System.out.println(
        Long.toHexString(Double.doubleToRawLongBits(length)));
// 0
```

`0.0` và `-0.0` bằng nhau theo toán tử primitive `==`, nhưng có bit dấu khác nhau. `hypot` biểu diễn **độ dài**, do đó dấu của tọa độ âm hoặc của signed zero không được giữ trong kết quả.

### Đầu vào lớn/nhỏ nhưng hữu hạn

`Math.hypot(1e200, 1e200)` và `Math.hypot(1e-200, 1e-200)` đều hữu hạn. Nhưng nếu đầu vào đã là `Infinity`, hoặc được tạo từ một phép tính khác đã overflow, `hypot` không thể khôi phục dữ liệu vốn hữu hạn trước phép toán đó.

Tương tự, nếu một giá trị có độ lớn khác 0 đã underflow thành `0.0` **trước** khi truyền vào `hypot`, thì `hypot` chỉ nhìn thấy `0.0`; nó không khôi phục được giá trị gốc.

## 7. `Math.hypot` và `StrictMath.hypot` khác nhau như thế nào?

Java có hai lớp tiện ích tính toán:

- `java.lang.Math`: tập hợp hàm toán học phổ biến. JVM có thể sử dụng các phép tối ưu hoặc mã nền tảng khi vẫn đáp ứng hợp đồng API.
- `java.lang.StrictMath`: tập hợp hàm toán học được thiết kế với định hướng hành vi số học nhất quán, có các quy định chặt chẽ hơn về khả năng tái lập cho nhiều phép tính. Không nên suy luận rằng **mọi** cặp phương thức trong `Math` và `StrictMath` bắt buộc cho bit kết quả giống nhau trên mọi nền tảng và mọi phiên bản.

Cả hai có phương thức:

```java
double a = Math.hypot(3.0, 4.0);
double b = StrictMath.hypot(3.0, 4.0);
```

Đối với bài toán tính độ dài thông thường, nên ưu tiên `Math.hypot` vì dễ đọc, là API trực tiếp và đáp ứng yêu cầu chất lượng số. Nếu hệ thống có hợp đồng kiểm thử tái lập chi tiết từng bit giữa các môi trường, cần đánh giá **đúng phương thức, JDK, phiên bản và nền tảng** trước khi chọn, thay vì mặc định tên `StrictMath` là lời hứa mọi phép toán toàn hệ thống đều chính xác tuyệt đối.

Đừng dùng `StrictMath.hypot` như biện pháp tự động loại bỏ mọi sai số dấu phẩy động: cả hai đều nhận và trả `double`, nên cùng chịu giới hạn miền biểu diễn.

## 8. Tình huống ứng dụng: khoảng cách giữa hai điểm

Cho hai điểm `P(x1, y1)` và `Q(x2, y2)`:

\[
d(P,Q)=\sqrt{(x_2-x_1)^2+(y_2-y_1)^2}.
\]

Thay vì:

```java
double dx = x2 - x1;
double dy = y2 - y1;
double distance = Math.sqrt(dx * dx + dy * dy);
```

hãy dùng:

```java
double dx = x2 - x1;
double dy = y2 - y1;
double distance = Math.hypot(dx, dy);
```

Hai đoạn trên là **mã minh họa trong ngữ cảnh đã khai báo các tọa độ**. `Math.hypot` loại bỏ nguy cơ overflow/underflow không cần thiết của các bình phương `dx*dx`, `dy*dy`.

Tuy nhiên, cần phân tích luôn phép **trừ tọa độ**:

1. Nếu `x1`, `x2` là `double` rất lớn và gần nhau, các chữ số thấp có thể đã bị làm tròn trong bước lưu dữ liệu. `hypot` không phục hồi chênh lệch đã mất.
2. Nếu tọa độ gốc là số nguyên lớn và được chuyển sang `double` trước khi trừ, các số nguyên liên tiếp có thể trở thành cùng giá trị `double`.
3. Nếu dùng kiểu `int` và viết `int dx = x2 - x1`, bản thân phép trừ có thể overflow theo quy tắc số nguyên Java trước khi truyền vào `hypot`.

Với tọa độ `int`, một cách tránh tràn ở phép trừ là **chuyển sang `double` trước khi trừ**:

```java
int x1 = Integer.MIN_VALUE;
int x2 = Integer.MAX_VALUE;

double dx = (double) x2 - (double) x1;
double distance = Math.hypot(dx, 0.0);

System.out.println(distance); // 4.294967295E9
```

Mọi giá trị `int` đều biểu diễn chính xác bằng `double`, nên phép chuyển trước phép trừ không mất bit phần trị của tọa độ `int`. Ngược lại, ép kiểu **sau** phép trừ nguyên không cứu được overflow đã xảy ra.

Nếu tọa độ là `long` rất lớn và cần giữ phân biệt từng đơn vị, chỉ chuyển từng `long` sang `double` có thể làm tròn. Phải chọn cách tính chênh lệch chính xác và kiểu kết quả phù hợp với miền giá trị của ứng dụng.

### Khoảng cách 3 chiều

Với vectơ `(x,y,z)`, có thể lồng hai phép gọi:

```java
double length3D = Math.hypot(Math.hypot(x, y), z);
```

Đây là cách tránh viết ngay `sqrt(x*x + y*y + z*z)` với ba bình phương trung gian. Kết quả vẫn là phép tính `double` có làm tròn; cần xét overflow cuối cùng và sai số tích lũy. Nếu ứng dụng có yêu cầu numerical analysis đặc biệt, có thể cần thuật toán tính chuẩn nhiều chiều chuyên dụng.

## 9. Ví dụ Java 17 đầy đủ và kết quả

Lưu chương trình sau dưới tên `HypotDemo.java`:

```java
public class HypotDemo {
    public static void main(String[] args) {
        double x = 3.0;
        double y = 4.0;

        System.out.println("3-4 naive: "
                + Math.sqrt(x * x + y * y));
        System.out.println("3-4 hypot: " + Math.hypot(x, y));

        double big = 1e200;
        System.out.println("big naive: "
                + Math.sqrt(big * big + big * big));
        System.out.println("big hypot: " + Math.hypot(big, big));

        double tiny = 1e-200;
        System.out.println("tiny naive: "
                + Math.sqrt(tiny * tiny + tiny * tiny));
        System.out.println("tiny hypot: " + Math.hypot(tiny, tiny));

        System.out.println("near max hypot: "
                + Math.hypot(1e308, 1e308));
        System.out.println("result overflow: "
                + Math.hypot(Double.MAX_VALUE, Double.MAX_VALUE));

        System.out.println("infinite with NaN: "
                + Math.hypot(Double.POSITIVE_INFINITY, Double.NaN));
        System.out.println("NaN with finite: "
                + Math.hypot(Double.NaN, 4.0));

        double zeroLength = Math.hypot(-0.0, -0.0);
        System.out.println("signed zero hypot: " + zeroLength);
        System.out.println("signed zero bits: "
                + Long.toHexString(
                        Double.doubleToRawLongBits(zeroLength)));

        int x1 = Integer.MIN_VALUE;
        int x2 = Integer.MAX_VALUE;
        double dx = (double) x2 - (double) x1;
        System.out.println("int coordinate distance: "
                + Math.hypot(dx, 0.0));

        System.out.println("strict hypot: "
                + StrictMath.hypot(big, big));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac HypotDemo.java
java HypotDemo
```

Kết quả điển hình trên JDK 17 tuân thủ hợp đồng Java (những chữ số cuối của phép tính dấu phẩy động phụ thuộc các bảo đảm cụ thể của phương thức và môi trường):

```text
3-4 naive: 5.0
3-4 hypot: 5.0
big naive: Infinity
big hypot: 1.414213562373095E200
tiny naive: 0.0
tiny hypot: 1.414213562373095E-200
near max hypot: 1.4142135623730951E308
result overflow: Infinity
infinite with NaN: Infinity
NaN with finite: NaN
signed zero hypot: 0.0
signed zero bits: 0
int coordinate distance: 4.294967295E9
strict hypot: 1.414213562373095E200
```

Ví dụ kiểm chứng hai tình huống quan trọng:

- Với `big`, phép nhân trung gian overflow thành `Infinity`, trong khi `Math.hypot` vẫn hữu hạn.
- Với `tiny`, phép nhân trung gian làm tròn thành `0.0`, trong khi `Math.hypot` giữ kết quả hữu hạn khác 0.

Trường hợp `Double.MAX_VALUE` cho thấy `Math.hypot` **không** thể tránh tràn ở kết quả cuối nếu độ dài thật vượt cận.

## 10. Vì sao không tự thay bằng biểu thức “đã rút gọn”?

### `Math.sqrt(x*x + y*y)` nhanh và ngắn nhưng có giới hạn

Công thức trực tiếp thường được dạy trong toán học và có thể đủ khi độ lớn đầu vào đã được giới hạn chặt. Tuy nhiên, khi số liệu có thể trải qua miền rất lớn/nhỏ, nó làm phát sinh các phép nhân nguy hiểm mà `hypot` được thiết kế để giảm.

### Dùng `Math.abs(x)+Math.abs(y)` làm độ dài có đúng không?

Không. Đây là chuẩn Manhattan (`L1`) trong hai chiều, khác chuẩn Euclid (`L2`):

\[
|x|+|y| \neq \sqrt{x^2+y^2}
\]

trừ một số trường hợp đặc biệt. Với `(3,4)`, tổng tuyệt đối là `7` còn độ dài Euclid là `5`. Không thay đổi phép đo chỉ để tránh overflow mà không xác định lại bài toán.

### Dùng `x / max`, `y / max` rồi nhân lại có luôn an toàn?

Kỹ thuật scaling là cơ sở tốt nhưng một phép cài đặt đơn giản có thể gặp khó ở số cực nhỏ, mất độ chính xác của tỷ số hoặc sai số làm tròn ở nhân cuối. Cần xử lý `max == 0`, `NaN`, vô cực, dấu, tỷ lệ rất lệch và kết quả gần giới hạn biểu diễn. Dùng `Math.hypot` thay cho mã tự phát triển trừ khi thật sự cần thuật toán đặc thù.

### Đổi sang `BigDecimal` sau khi có `Infinity` có giúp không?

Không. Nếu `x*x` đã thành `Infinity`, mọi chữ số về giá trị hữu hạn trước đó đã mất trong phép nhân. `BigDecimal` cũng không nhận trực tiếp vô cực làm giá trị decimal.

Nếu thực sự cần miền lớn hơn `double` hoặc tính toán decimal chính xác, phải giữ **đầu vào nguyên bản** dưới kiểu phù hợp và chọn thuật toán căn số cao độ chính xác riêng, chứ không chuyển một kết quả đã overflow sang `BigDecimal` và mong phục hồi.

## 11. Độ chính xác, ULP và kiểm thử

### `hypot` có thể làm tròn nhưng thường đáng tin hơn biểu thức ngây thơ

Dù tránh được nhiều lỗi trung gian, `Math.hypot` vẫn trả một giá trị `double`, tức độ dài thường phải được làm tròn. Cần phân biệt hai yêu cầu:

- **Đúng miền giá trị:** phép tính không overflow/underflow sai do trung gian khi kết quả có thể biểu diễn được.
- **Đúng độ chính xác:** kết quả nằm gần giá trị toán học chuẩn trong giới hạn sai số quy định.

Tài liệu `Math.hypot` nêu yêu cầu chất lượng về **ULP** (unit in last place). ULP là bước đơn vị của phần trị ở vùng số cụ thể, không phải một ngưỡng sai số tuyệt đối cố định trong toàn miền `double`.

```java
double h = Math.hypot(3.0, 4.0);
double ulpAtResult = Math.ulp(h);
```

Một ULP ở cỡ `5` khác hoàn toàn một ULP ở cỡ `1e200`. Không dùng số `1e-9` chung cho mọi phép so sánh nếu bài toán trải qua nhiều thang số.

### Test những miền nào?

| Tình huống | Ví dụ | Mục tiêu |
| --- | --- | --- |
| Tam giác quen thuộc | `(3,4)` | Kết quả cơ bản `5` |
| Hai thành phần lớn | `(1e200,1e200)` | Không overflow trung gian |
| Hai thành phần nhỏ | `(1e-200,1e-200)` | Không underflow trung gian thành 0 |
| Gần cận hữu hạn | `(1e308,1e308)` | Trả độ dài hữu hạn nếu có thể |
| Kết quả thật vượt cận | `(MAX_VALUE,MAX_VALUE)` | Nhận biết `Infinity` hợp lệ |
| Một thành phần 0 | `(3,0)`, `(0,-4)` | Độ dài bằng trị tuyệt đối thành phần còn lại |
| Hai signed zero | `(-0.0,-0.0)` | Kết quả `+0.0` |
| Giá trị đặc biệt | `(NaN,4)`, `(Infinity,NaN)` | Đúng quy tắc API |
| Một lớn, một rất nhỏ | `(1e200,1)` | Không giả định số nhỏ luôn ảnh hưởng bit kết quả |
| Tọa độ nguyên gần biên | `Integer.MIN_VALUE`, `MAX_VALUE` | Tránh overflow khi tính hiệu nguyên |

Với bài toán có độ chính xác cao, kiểm thử có thể dùng kết quả tham chiếu từ thuật toán số chính xác hơn, so sánh theo ULP hoặc theo sai số tương đối/tuyệt đối phù hợp. Không nên so sánh tất cả kết quả dấu phẩy động bằng `==` nếu phép tính có làm tròn; nhưng những trường hợp được bảo đảm chính xác như `(3,4)` cũng có thể kiểm tra kết quả cụ thể.

## 12. Những lỗi thường gặp

1. **Viết `Math.sqrt(x*x+y*y)` cho dữ liệu không giới hạn thang đo.** Bình phương có thể overflow/underflow dù kết quả cuối còn biểu diễn được.
2. **Cho rằng `Math.hypot` luôn trả số hữu hạn.** Nếu độ dài thật vượt `Double.MAX_VALUE`, kết quả có thể `Infinity`.
3. **Chỉ thử số nhỏ nên không nhìn thấy lỗi.** Các case `(3,4)` không phơi lộ overflow trung gian như `(1e200,1e200)`.
4. **Cho rằng mọi underflow cuối đều do lỗi `hypot`.** Nếu độ dài toán học thật quá nhỏ so với độ phân giải, kết quả làm tròn về 0 có thể là đúng quy tắc của `double`.
5. **Dùng `Double.MIN_VALUE` như số âm nhỏ nhất.** Nó là số dương khác 0 nhỏ nhất, không phải cận âm.
6. **Bỏ qua `NaN`/vô cực trong dữ liệu nguồn.** `hypot(Infinity,NaN)` cho vô cực, không nhất thiết giống cách lan truyền NaN của biểu thức trực tiếp.
7. **Trừ `int` rồi mới chuyển sang `double`.** Overflow có thể xảy ra ở hiệu `int` trước khi `hypot` được gọi.
8. **Chuyển `long` rất lớn sang `double` rồi kỳ vọng giữ từng đơn vị.** Giá trị nguyên có thể đã mất bit phần thấp.
9. **Tưởng `StrictMath.hypot` cho độ chính xác vô hạn.** API vẫn làm việc với `double`.
10. **Coi công thức scaling vài dòng là chắc chắn tương đương thư viện.** Phải xử lý sai số, subnormal, overflow cuối và mọi góc biên.
11. **Thay Euclid bằng `abs(x)+abs(y)` không xét ý nghĩa.** Đây là chuẩn khác, làm thay đổi bài toán.
12. **Không kiểm tra kết quả trong ứng dụng đòi hỏi finite.** `Math.hypot` có thể trả `Infinity` hoặc `NaN`, cần chính sách xử lý.
13. **Kỳ vọng `hypot` khôi phục thông tin đầu vào đã mất.** Các phép toán trước đó có thể đã làm tròn hoặc overflow; `hypot` chỉ nhận các `double` hiện tại.
14. **Nhầm độ chính xác của phép tính với độ chính xác của dữ liệu nguồn.** Một thuật toán ổn định không thể sửa số đo hoặc biểu diễn ban đầu đã thiếu độ phân giải.

## 13. Bài tập và đáp án

**Bài 1.** Tại sao `Math.sqrt(1e200*1e200 + 1e200*1e200)` có thể trả `Infinity`, trong khi kết quả toán học cỡ `1.4e200`?

**Đáp án:** Hai phép bình phương trung gian tạo `1e400`, vượt miền hữu hạn của `double`; toán tử nhân cho `Infinity` trước khi có thể lấy căn. `Math.hypot(1e200,1e200)` tránh các bình phương trung gian quá lớn nên giữ kết quả hữu hạn.

**Bài 2.** Tại sao `Math.sqrt(1e-200*1e-200 + 1e-200*1e-200)` cho `0.0`?

**Đáp án:** Các bình phương cỡ `1e-400` nhỏ hơn số `double` dương khác 0 nhỏ nhất, bị làm tròn về `0.0`; cộng và lấy căn không khôi phục được chúng. `Math.hypot` đổi thang và cho khoảng `1.414E-200`.

**Bài 3.** `Math.hypot(Double.MAX_VALUE,Double.MAX_VALUE)` có phải lỗi của API nếu trả `Infinity`?

**Đáp án:** Không. Bản thân độ dài thực sự lớn hơn giá trị hữu hạn tối đa của `double`. `hypot` giảm **overflow trung gian không cần thiết**, không bảo đảm tránh overflow khi kết quả đúng vượt miền.

**Bài 4.** Với `x = 3`, `y = -4`, kết quả toán học của `Math.hypot(x,y)` là bao nhiêu? Dấu âm có được giữ không?

**Đáp án:** `5.0`. Độ dài luôn không âm; dấu của từng thành phần không trở thành dấu âm của kết quả.

**Bài 5.** Nếu một đối số là `Infinity` và đối số kia là `NaN`, `Math.hypot` trả gì?

**Đáp án:** Vô cực dương. Quy tắc đặc biệt của `hypot` ưu tiên vô cực nếu bất kỳ đối số nào là vô cực.

**Bài 6.** Muốn tính khoảng cách giữa hai điểm với hoành độ kiểu `int`, tại sao nên dùng `(double)x2 - x1` trước khi gọi `hypot`?

**Đáp án:** Nếu trừ hai giá trị `int` gần hai biên đối dấu, phép trừ `int` có thể overflow. Mọi giá trị `int` đều biểu diễn chính xác dưới dạng `double`, nên chuyển trước phép trừ tránh được overflow nguyên trong trường hợp này.

**Bài 7.** `Math.hypot(1e308,1e308)` có thể hữu hạn không? Vì sao?

**Đáp án:** Có. Giá trị đúng khoảng `1.4142135623730951E308`, nhỏ hơn `Double.MAX_VALUE` khoảng `1.7976931348623157E308`; cách tính ổn định tránh được overflow bình phương.

**Bài 8.** Nếu một giá trị `float` được ép từ kết quả `Math.hypot`, có chắc không overflow không?

**Đáp án:** Không. `Math.hypot` trả `double`; khi ép về `float`, số đó có thể vượt `Float.MAX_VALUE` và thành vô cực, hoặc bị làm tròn/mất độ chính xác.

**Bài 9.** Nếu thuật toán 3D viết `Math.hypot(Math.hypot(x,y),z)`, có loại bỏ hoàn toàn mọi sai số không?

**Đáp án:** Không. Cách này tránh nhiều nguy cơ từ các phép bình phương trung gian nhưng vẫn làm tròn ở mỗi kết quả `double`. Với yêu cầu độ chính xác đặc biệt, cần phân tích sai số và kiểm tra trên miền dữ liệu thực tế.

**Ghi nhớ:** `Math.hypot(x,y)` là cách trực tiếp và đáng tin để tính \(\sqrt{x^2+y^2}\) trong Java 17+, nhất là khi thành phần có độ lớn rất lớn hoặc rất nhỏ. Hiểu **overflow trung gian** khác **overflow kết quả thực**, và kiểm tra cả chất lượng đầu vào lẫn giá trị trả về khi ứng dụng cần bảo đảm miền hữu hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-007">ceil, floor, rint và round</a>

<details>
<summary>Xem chi tiết</summary>

# ceil, floor, rint và round trong Java: bốn cách làm tròn dễ nhầm

## 1. Mục tiêu

Java cung cấp nhiều phương thức liên quan đến làm tròn trong hai lớp `java.lang.Math` và `java.lang.StrictMath`. Bốn tên thường gặp nhất là `ceil`, `floor`, `rint` và `round`. Chúng **không tương đương**, dù đôi khi cùng trả một kết quả.

Sau bài học, bạn sẽ có thể:

- Giải thích hướng làm tròn của từng phương thức bằng định nghĩa toán học.
- Phân biệt quy tắc xử lý số nằm **đúng giữa hai số nguyên**: `rint` dùng *ties to even*, còn `round` ưu tiên số nguyên **về phía dương** khi có hai lựa chọn cách đều.
- Dự đoán kết quả với số âm, `-0.0`, giá trị rất lớn, `NaN` và `Infinity`.
- Chọn phương thức đúng theo **kiểu trả về**: `ceil`/`floor`/`rint` trả `double`; `round(float)` trả `int` và `round(double)` trả `long`.
- Biết khi nào cần `BigDecimal` và `RoundingMode` thay vì áp dụng `Math.round` cho các giá trị tài chính.
- Tránh mất thông tin khi ép kiểu, chuyển đổi số và làm tròn nhiều bước.

Các ví dụ chính chạy trên **Java 17+**, không dùng preview. Phần lớn quy tắc cũng áp dụng cho `StrictMath` tương ứng.

## 2. Trước tiên: làm tròn tới số nguyên là gì?

Giả sử có một số thực `x` và ta muốn nhận một giá trị nguyên gần hoặc liên quan tới `x`. Có nhiều quy tắc hợp lý nhưng **phải xác định quy tắc trước khi tính**.

Ví dụ số `2.7`:

- Số nguyên lớn hơn hoặc bằng nhỏ nhất là 3.
- Số nguyên nhỏ hơn hoặc bằng lớn nhất là 2.
- Số nguyên gần nhất là 3.

Ví dụ số `-2.7`:

- Số nguyên lớn hơn hoặc bằng nhỏ nhất là **−2**.
- Số nguyên nhỏ hơn hoặc bằng lớn nhất là **−3**.
- Số nguyên gần nhất là **−3**.

Đây là lý do các cách nói chung như “làm tròn lên” hoặc “làm tròn xuống” rất dễ gây hiểu nhầm với **số âm**. Trong Java, hãy dùng các định nghĩa hình thức:

| Phương thức | Quy tắc chính | Kết quả toán học với 2.7 | Với −2.7 |
| --- | --- | --- | --- |
| `Math.ceil(x)` | Số nguyên nhỏ nhất **≥ x** | 3 | −2 |
| `Math.floor(x)` | Số nguyên lớn nhất **≤ x** | 2 | −3 |
| `Math.rint(x)` | Số nguyên gần nhất; hòa thì chọn **chẵn** | 3 | −3 |
| `Math.round(x)` | Số nguyên gần nhất; hòa thì chọn phía **+∞** | 3 | −3 |

Bảng trên nói về **giá trị toán học**. Về kiểu Java, ba phương thức đầu trả giá trị nguyên **ở dạng `double`**, ví dụ `3.0`; `Math.round(double)` trả số nguyên primitive `long`, ví dụ `3L`.

## 3. Math.ceil(double): hướng về dương vô cực

`Math.ceil(double a)` trả về giá trị `double` bằng số nguyên toán học **nhỏ nhất không bé hơn** đối số.

\[
\operatorname{ceil}(x)=\min\{n\in\mathbb Z:n\ge x\}
\]

~~~java
System.out.println(Math.ceil(2.1));  // 3.0
System.out.println(Math.ceil(2.9));  // 3.0
System.out.println(Math.ceil(-2.1)); // -2.0
System.out.println(Math.ceil(-2.9)); // -2.0
System.out.println(Math.ceil(3.0));  // 3.0
~~~

Các đoạn ngắn như trên được đặt bên trong một phương thức; chương trình độc lập có trong phần 10.

**Điểm cần nhớ:** đối với số âm, `ceil` có thể làm **giảm độ lớn tuyệt đối**, vì hướng làm tròn là hướng tới `+∞`, không phải “ra xa số 0”.

Ví dụ `-2.9` nằm giữa `-3` và `-2`; số nguyên nhỏ nhất không bé hơn `-2.9` là `-2`.

### Zero có dấu

Java `double` phân biệt `+0.0` và `-0.0`. Theo hợp đồng `Math.ceil`, đối số âm lớn hơn `-1.0` nhưng nhỏ hơn `0.0` cho kết quả **`-0.0`**:

~~~java
System.out.println(Math.ceil(-0.3)); // -0.0
System.out.println(Math.ceil(+0.3)); // 1.0
~~~

Dù `-0.0 == 0.0` là true, dấu của zero có thể quan sát khi in hoặc lấy nghịch đảo (`1.0 / -0.0` là `-Infinity`). Nếu ứng dụng không quan tâm tới dấu zero, có thể chuẩn hóa sau khi làm tròn một cách có chủ đích.

## 4. Math.floor(double): hướng về âm vô cực

`Math.floor(double a)` trả về số nguyên toán học **lớn nhất không vượt quá** đối số, nhưng kiểu kết quả vẫn là `double`.

\[
\operatorname{floor}(x)=\max\{n\in\mathbb Z:n\le x\}
\]

~~~java
System.out.println(Math.floor(2.1));  // 2.0
System.out.println(Math.floor(2.9));  // 2.0
System.out.println(Math.floor(-2.1)); // -3.0
System.out.println(Math.floor(-2.9)); // -3.0
System.out.println(Math.floor(-0.0)); // -0.0
~~~

Với số âm, `floor` thường làm **tăng độ lớn tuyệt đối**, vì nó đi về `-∞`. Ví dụ `floor(-2.1) == -3.0`, không phải `-2.0`.

### floor khác ép kiểu nguyên

Ép `double` sang `int` hoặc `long` (trong miền thông thường) loại bỏ phần thập phân theo hướng **về 0**, không phải về âm vô cực:

~~~java
double x = -2.9;
System.out.println((int) x);       // -2
System.out.println(Math.floor(x)); // -3.0
~~~

Nếu bạn cần chia một trục tọa độ âm thành các ô dạng `floor(x / cellSize)`, sự khác biệt này đặc biệt quan trọng. Dùng ép kiểu nguyên khi thực sự cần bỏ phần thập phân theo hướng 0; dùng `floor` khi muốn lấy biên dưới toán học.

**Chú ý:** ép kiểu số thực thành `int`/`long` còn có các quy tắc riêng cho `NaN` và giá trị vượt miền. Không thay thế `floor` bằng phép cast nếu muốn giữ đúng quy tắc toán học và xử lý giá trị đặc biệt.

## 5. Math.rint(double): số nguyên gần nhất, hòa thì chọn chẵn

`Math.rint(double a)` trả `double` là giá trị nguyên gần nhất với đối số. Nếu có **hai giá trị nguyên cách đều**, nó chọn giá trị **chẵn** (*round half to even*, còn gọi là *ties to even* hoặc *banker's rounding*).

~~~java
System.out.println(Math.rint(2.3));  // 2.0
System.out.println(Math.rint(2.7));  // 3.0
System.out.println(Math.rint(2.5));  // 2.0
System.out.println(Math.rint(3.5));  // 4.0
System.out.println(Math.rint(-2.5)); // -2.0
System.out.println(Math.rint(-3.5)); // -4.0
~~~

Vì 2 và 4 là số chẵn, các điểm giữa `2.5` và `3.5` được xử lý khác nhau. Với số âm, `-2` và `-4` vẫn là các số chẵn theo khái niệm số nguyên.

### Tại sao chọn số chẵn khi hòa?

Chọn chẵn làm giảm xu hướng sai lệch một phía khi rất nhiều trường hợp **đúng giữa** hai số nguyên được làm tròn. Nhưng điều đó **không đảm bảo** tổng một tập dữ liệu bất kỳ sẽ không có sai số hoặc không có bias: độ phân bố dữ liệu và cách thực hiện phép tính vẫn quan trọng.

Ví dụ, với các giá trị đúng nửa nguyên liên tiếp:

| Đầu vào | Hai ứng viên gần nhất | Kết quả `rint` |
| --- | --- | --- |
| 1.5 | 1 hoặc 2 | 2.0 |
| 2.5 | 2 hoặc 3 | 2.0 |
| 3.5 | 3 hoặc 4 | 4.0 |
| 4.5 | 4 hoặc 5 | 4.0 |
| −1.5 | −2 hoặc −1 | −2.0 |
| −2.5 | −3 hoặc −2 | −2.0 |
| −3.5 | −4 hoặc −3 | −4.0 |

### rint trả double, không phải int hoặc long

~~~java
double nearest = Math.rint(2.5); // 2.0
// int n = Math.rint(2.5);       // lỗi biên dịch: double không tự thành int
~~~

Nếu cần `int`, hãy kiểm tra tính hữu hạn và miền trước khi chuyển:

~~~java
double nearest = Math.rint(input);
if (!Double.isFinite(nearest)
        || nearest < Integer.MIN_VALUE
        || nearest > Integer.MAX_VALUE) {
    throw new IllegalArgumentException("Rounded value outside int range");
}
int count = (int) nearest;
~~~

Đây là đoạn minh họa bên trong một phương thức có biến `input`. Kiểm tra trên đảm bảo kết quả hữu hạn và trong miền `int`; yêu cầu miền nghiệp vụ (ví dụ `count >= 0`) vẫn phải kiểm tra riêng.

### Điểm đặc biệt của −0.5

`Math.rint(-0.5)` trả **`-0.0`** theo quy tắc hòa chọn số nguyên chẵn (0) và giữ dấu zero. Không nên chuyển ngay kết quả sang `int` rồi cho rằng chưa có khác biệt về dấu:

~~~java
System.out.println(Math.rint(-0.5)); // -0.0
~~~

## 6. Math.round(float) và Math.round(double): gần nhất, hòa hướng dương

Khác ba phương thức trước, `Math.round` có **hai overload**:

~~~java
int roundedFloat = Math.round(2.5f);
long roundedDouble = Math.round(2.5);
~~~

Chữ ký:

| Overload | Tham số | Kiểu trả về |
| --- | --- | --- |
| `Math.round(float a)` | `float` | `int` |
| `Math.round(double a)` | `double` | `long` |

Quy tắc là chọn số nguyên gần nhất; **nếu hai số nguyên cách đều**, chọn số nguyên hướng về **dương vô cực**.

~~~java
System.out.println(Math.round(2.5));   // 3 (long)
System.out.println(Math.round(3.5));   // 4 (long)
System.out.println(Math.round(-2.5));  // -2 (long)
System.out.println(Math.round(-3.5));  // -3 (long)
System.out.println(Math.round(-0.5));  // 0 (long)
~~~

Đối với `-2.5`, hai số nguyên cách đều là `-3` và `-2`. Số ở **hướng dương hơn** là `-2`, do đó `Math.round(-2.5) == -2L`.

**Đừng mô tả `Math.round` đơn giản là “làm tròn half away from zero”.** Với số âm đúng nửa, `Math.round` chọn hướng `+∞` nên `-2.5` thành `-2` chứ không phải `-3`.

### round khác rint tại tie

~~~java
System.out.println(Math.rint(2.5));  // 2.0 (double)
System.out.println(Math.round(2.5)); // 3 (long)

System.out.println(Math.rint(-0.5));  // -0.0 (double)
System.out.println(Math.round(-0.5)); // 0 (long)
~~~

Khi chọn phương thức, luôn xem cả **quy tắc hòa** lẫn **kiểu dữ liệu đầu ra**.

## 7. Bảng so sánh các trường hợp dễ nhầm

Các ô trong ba cột `ceil`/`floor`/`rint` dùng biểu diễn `double`; cột `round` dùng `long`.

| `x` | `ceil(x)` | `floor(x)` | `rint(x)` | `round(x)` |
| ---: | ---: | ---: | ---: | ---: |
| 2.1 | 3.0 | 2.0 | 2.0 | 2 |
| 2.5 | 3.0 | 2.0 | 2.0 | 3 |
| 3.5 | 4.0 | 3.0 | 4.0 | 4 |
| −2.1 | −2.0 | −3.0 | −2.0 | −2 |
| −2.5 | −2.0 | −3.0 | −2.0 | −2 |
| −3.5 | −3.0 | −4.0 | −4.0 | −3 |
| −0.5 | −0.0 | −1.0 | −0.0 | 0 |
| 0.0 | 0.0 | 0.0 | 0.0 | 0 |
| −0.0 | −0.0 | −0.0 | −0.0 | 0 |

Kết luận từ bảng:

1. `ceil`/`floor` luôn theo **hướng trên/dưới toán học**, không phải hướng xa/gần zero.
2. `rint` và `round` thường cùng kết quả nếu không đúng điểm giữa.
3. Khi đúng điểm giữa, `rint` chọn số chẵn; `round` chọn phía dương.
4. `round` luôn trả một giá trị primitive **nguyên** nên không bảo toàn được `-0.0`.
5. Nếu đầu vào không phải một số hữu hạn hoặc đã quá lớn, phải xét thêm các quy tắc ở phần 8.

## 8. NaN, Infinity, signed zero và giá trị vượt miền

### 8.1. ceil, floor và rint giữ các giá trị đặc biệt

Đối với `Math.ceil`, `Math.floor` và `Math.rint`:

- Nếu tham số là `NaN`, kết quả là `NaN`.
- Nếu tham số là `+Infinity` hoặc `-Infinity`, kết quả là chính infinity đó.
- Nếu tham số là `+0.0` hoặc `-0.0`, kết quả giữ zero với dấu tương ứng.
- Nếu đã là số nguyên toán học dưới dạng `double`, không có phần thập phân để làm tròn; giá trị giữ nguyên.

~~~java
System.out.println(Math.ceil(Double.NaN));              // NaN
System.out.println(Math.floor(Double.POSITIVE_INFINITY)); // Infinity
System.out.println(Math.rint(Double.NEGATIVE_INFINITY));  // -Infinity
System.out.println(Math.floor(-0.0));                   // -0.0
~~~

Những phương thức này có thể trả `double` với giá trị rất lớn lớn hơn giới hạn `long` mà không cần ép về số nguyên, do bản thân kiểu trả về vẫn là `double`.

### 8.2. round chuyển sang kiểu số nguyên có miền hữu hạn

`Math.round(double)` trả `long`; `Math.round(float)` trả `int`. Chúng có các quy tắc được định nghĩa rõ khi đầu vào không thể làm tròn vào miền số nguyên đích:

- `NaN` → `0` (kiểu `int` hoặc `long` tùy overload).
- `+Infinity` hoặc giá trị quá lớn về phía dương → `Integer.MAX_VALUE` / `Long.MAX_VALUE`.
- `-Infinity` hoặc giá trị quá lớn về phía âm → `Integer.MIN_VALUE` / `Long.MIN_VALUE`.
- `-0.0` → `0` (không giữ được dấu zero).

~~~java
System.out.println(Math.round(Double.NaN));             // 0
System.out.println(Math.round(Double.POSITIVE_INFINITY)); // 9223372036854775807
System.out.println(Math.round(Double.NEGATIVE_INFINITY)); // -9223372036854775808
System.out.println(Math.round(Float.POSITIVE_INFINITY)); // 2147483647
System.out.println(Math.round(Float.NEGATIVE_INFINITY)); // -2147483648
~~~

**Rủi ro:** `Math.round` **không báo lỗi** nếu gặp `NaN` hoặc số vượt miền; nó có thể trả về 0 hoặc cực trị của kiểu nguyên. Nếu 0, `Integer.MAX_VALUE` hoặc `Long.MAX_VALUE` là các giá trị hợp lệ trong nghiệp vụ, bạn có thể vô tình chấp nhận số sai mà không phát hiện.

Với dữ liệu người dùng/cấu hình, nên kiểm tra `Double.isFinite` hoặc `Float.isFinite`, giới hạn miền và kiểm tra quy tắc làm tròn trước khi lưu kết quả.

### 8.3. Mất độ chính xác trước khi làm tròn

`double` có khoảng 53 bit precision có nghĩa. Những số nguyên lớn hơn `2^53` không phải lúc nào cũng được biểu diễn chính xác, nên `Math.round` **không thể khôi phục** chữ số đã mất khi chuỗi decimal hoặc phép toán bị làm tròn sang binary64 trước đó.

~~~java
double input = Double.parseDouble("9007199254740993");
System.out.println(input);          // 9.007199254740992E15
System.out.println(Math.round(input)); // 9007199254740992
~~~

Nếu dữ liệu cần là một số nguyên chính xác, ưu tiên `Long.parseLong` hoặc `BigInteger` từ **chuỗi gốc**, không parse sang `double` rồi round.

## 9. ceil/floor/rint và ép kiểu: mục đích hoàn toàn khác

Ép kiểu Java từ `double` sang `int`/`long` thực hiện chuyển đổi về miền số nguyên; với các giá trị hữu hạn có phần thập phân trong miền thông thường, phần thập phân bị cắt **về 0**.

~~~java
double positive = 2.8;
double negative = -2.8;

System.out.println((int) positive);  // 2
System.out.println((int) negative);  // -2
System.out.println(Math.floor(negative)); // -3.0
System.out.println(Math.ceil(negative));  // -2.0
~~~

Không nên dùng `(int) Math.ceil(x)` khi không biết miền x; kết quả `ceil` vẫn là `double` và bước cast có thể xử lý cực trị/NaN bằng quy tắc conversion riêng, làm mất thông tin. Nếu muốn kết quả `int` từ một phép round, hãy validate tính hữu hạn, miền và ngữ nghĩa trước.

### Chọn floor cho chỉ số ô trên trục âm

Giả sử bạn chia mặt phẳng thành các ô chiều rộng 10, và ô chứa `x` được đánh số bằng `floor(x / 10)`:

~~~java
double coordinate = -1.0;
int cell = (int) Math.floor(coordinate / 10.0);
System.out.println(cell); // -1
~~~

Nếu thay bằng `(int) (coordinate / 10.0)`, bạn nhận 0, sai ý nghĩa vùng ở phía âm. Ví dụ này có miền nhỏ được kiểm soát; với tọa độ ngoài miền int, cần kiểm tra trước khi cast.

## 10. Chương trình Java 17+ đầy đủ, có kết quả chính xác

Tạo tệp `RoundingMethodsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class RoundingMethodsDemo {
    private static void compare(double x) {
        System.out.println(
                "x=" + x +
                " ceil=" + Math.ceil(x) +
                " floor=" + Math.floor(x) +
                " rint=" + Math.rint(x) +
                " round=" + Math.round(x));
    }

    public static void main(String[] args) {
        compare(2.5);
        compare(3.5);
        compare(-2.5);
        compare(-3.5);
        compare(-0.5);

        System.out.println("round(float)=" + Math.round(2.5f));
        System.out.println("round(double)=" + Math.round(2.5));
        System.out.println("rint(2.5)=" + Math.rint(2.5));

        System.out.println("round(NaN)=" + Math.round(Double.NaN));
        System.out.println("round(+Infinity)=" +
                Math.round(Double.POSITIVE_INFINITY));
        System.out.println("round(-Infinity)=" +
                Math.round(Double.NEGATIVE_INFINITY));

        System.out.println("ceil(-0.3)=" + Math.ceil(-0.3));
        System.out.println("strict rint=" + StrictMath.rint(2.5));

        BigDecimal tie = new BigDecimal("-2.5");
        System.out.println("HALF_UP=" +
                tie.setScale(0, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN=" +
                tie.setScale(0, RoundingMode.HALF_EVEN));
    }
}
~~~

Biên dịch trên Java 17:

~~~bash
javac --release 17 RoundingMethodsDemo.java
java RoundingMethodsDemo
~~~

Stdout:

~~~text
x=2.5 ceil=3.0 floor=2.0 rint=2.0 round=3
x=3.5 ceil=4.0 floor=3.0 rint=4.0 round=4
x=-2.5 ceil=-2.0 floor=-3.0 rint=-2.0 round=-2
x=-3.5 ceil=-3.0 floor=-4.0 rint=-4.0 round=-3
x=-0.5 ceil=-0.0 floor=-1.0 rint=-0.0 round=0
round(float)=3
round(double)=3
rint(2.5)=2.0
round(NaN)=0
round(+Infinity)=9223372036854775807
round(-Infinity)=-9223372036854775808
ceil(-0.3)=-0.0
strict rint=2.0
HALF_UP=-3
HALF_EVEN=-2
~~~

Giải thích:

1. Với `2.5`, `rint` trả 2.0 do chọn chẵn; `round` trả 3 vì hướng dương ở điểm hòa.
2. Với `-3.5`, `rint` trả −4.0 (chẵn), trong khi `round` trả −3 (phía dương).
3. Với `-0.5`, `ceil` và `rint` giữ dấu zero âm; `round` trả số nguyên 0.
4. `Math.round(double)` có kiểu `long` và không biểu diễn `NaN`/`Infinity`; kết quả được quy định là 0 hoặc giới hạn tương ứng.
5. `StrictMath.rint(2.5)` có cùng quy tắc ties-to-even.
6. `RoundingMode.HALF_UP` làm tròn điểm giữa **ra xa 0**, nên −2.5 thành −3; `RoundingMode.HALF_EVEN` chọn chẵn nên −2.5 thành −2.

Đây là chương trình độc lập dùng `Math`, `StrictMath` và `BigDecimal` trên Java 17. Nó không dựa trên locale hoặc cấu hình hệ điều hành cho cách in số trong các biểu thức trên.

## 11. StrictMath so với Math đối với bốn phương thức này

Hai lớp đều nằm trong `java.lang` nên không cần thêm import:

~~~java
double a = Math.ceil(-1.2);
double b = StrictMath.ceil(-1.2);

double c = Math.rint(2.5);
double d = StrictMath.rint(2.5);

long e = Math.round(-2.5);
long f = StrictMath.round(-2.5);
~~~

`StrictMath` được thiết kế với yêu cầu kết quả tính toán có thể tái lập chặt chẽ trên nhiều nền tảng, đặc biệt quan trọng với nhiều hàm toán học phức tạp. Tuy nhiên, đối với **ceil, floor, rint và round**, các quy tắc kết quả đã được API xác định rõ; không có “chính sách làm tròn bí mật” khác nhau giữa `Math` và `StrictMath` trong các ví dụ này.

| Tên | Math | StrictMath | Khác biệt cần chú ý |
| --- | --- | --- | --- |
| `ceil(double)` | Có | Có | Cùng định nghĩa về +∞ |
| `floor(double)` | Có | Có | Cùng định nghĩa về −∞ |
| `rint(double)` | Có | Có | Cùng ties-to-even |
| `round(float)` | Có | Có | Cùng kiểu `int` và ties-to-positive-infinity |
| `round(double)` | Có | Có | Cùng kiểu `long` và ties-to-positive-infinity |

Không nên đoán rằng `StrictMath` “luôn chậm hơn” hoặc “luôn chính xác hơn” trong mọi môi trường. Hiệu năng phụ thuộc JDK/JVM/phần cứng, và các hàm làm tròn này đã có ngữ nghĩa được định nghĩa cụ thể. Chọn API theo yêu cầu hệ thống và đo bằng benchmark đại diện nếu tốc độ là vấn đề.

## 12. Làm tròn số thập phân: tránh lạm dụng Math.round

Một cách viết thường thấy để làm tròn hai chữ số thập phân:

~~~java
double x = 1.005;
double result = Math.round(x * 100.0) / 100.0;
~~~

Cách làm này **không đảm bảo** kết quả decimal như người dùng kỳ vọng:

- Nhiều số thập phân không được biểu diễn chính xác bằng `double`.
- Phép nhân `x * 100.0` cũng có thể làm tròn, nên một giá trị tưởng như ở điểm hòa không thực sự bằng đúng điểm hòa trong binary64.
- Với độ lớn rất lớn, nhân hệ số có thể overflow.
- Với số âm đúng điểm hòa, quy tắc `Math.round` là ties to +∞, không phải làm tròn nửa ra xa 0 như nhiều chính sách tài chính.
- Kết quả cuối vẫn là `double`, nên định dạng hiển thị hai chữ số không có nghĩa dữ liệu được lưu với precision decimal cố định.

Nếu yêu cầu **decimal chính xác**, hãy parse trực tiếp từ chuỗi decimal thành `BigDecimal` rồi chọn `RoundingMode`.

~~~java
BigDecimal price = new BigDecimal("1.005");
BigDecimal halfUp = price.setScale(2, RoundingMode.HALF_UP);
BigDecimal halfEven = price.setScale(2, RoundingMode.HALF_EVEN);

System.out.println(halfUp);   // 1.01
System.out.println(halfEven); // 1.00
~~~

Lưu ý `new BigDecimal("1.005")` giữ đúng giá trị decimal `1.005`. Ngược lại, `new BigDecimal(1.005)` tạo `BigDecimal` từ giá trị binary64 xấp xỉ đã có, có thể gây kết quả làm tròn khác. Nếu dữ liệu nguồn là chuỗi người dùng nhập, hãy dùng chuỗi gốc.

## 13. Các chế độ BigDecimal so với Math

Các chế độ `RoundingMode` liên quan:

| Chế độ | Hành vi ở giá trị không nguyên | Ví dụ 2.5 | Ví dụ −2.5 |
| --- | --- | --- | --- |
| `CEILING` | Hướng `+∞` | 3 | −2 |
| `FLOOR` | Hướng `-∞` | 2 | −3 |
| `DOWN` | Hướng **về 0** | 2 | −2 |
| `UP` | Hướng **xa 0** | 3 | −3 |
| `HALF_UP` | Gần nhất; hòa thì **xa 0** | 3 | −3 |
| `HALF_DOWN` | Gần nhất; hòa thì **về 0** | 2 | −2 |
| `HALF_EVEN` | Gần nhất; hòa thì chọn giá trị **chẵn** | 2 | −2 |
| `UNNECESSARY` | Từ chối nếu cần làm tròn | Lỗi | Lỗi |

Đây là bảng khi làm tròn tới **scale 0** bằng `BigDecimal.setScale(0, mode)`.

### Điều tương ứng và khác biệt

- `Math.ceil(x)` có hướng tương đương `RoundingMode.CEILING` (nhưng kiểu dữ liệu và biểu diễn khác).
- `Math.floor(x)` có hướng tương đương `RoundingMode.FLOOR`.
- `Math.rint(x)` gần với quy tắc `HALF_EVEN` ở mức giá trị nguyên.
- `Math.round(x)` **không có một tên `RoundingMode` độc lập tương đương cho cả số dương lẫn số âm**: khi hòa, nó luôn ưu tiên **+∞**, trong khi `HALF_UP` ưu tiên **xa 0**.

Hãy chọn chính sách làm tròn theo yêu cầu nghiệp vụ như quy định thuế, hóa đơn, thống kê hay xử lý tín hiệu, không chọn chỉ vì tên phương thức dễ nhớ.

## 14. Không phải mọi đầu vào double đều có “phần thập phân” có ý nghĩa

Ở các độ lớn rất lớn, khoảng cách giữa các giá trị `double` biểu diễn được có thể là **2, 4, 8 hoặc nhiều hơn**, nên mọi giá trị gần đó đã là số nguyên toán học theo biểu diễn binary64. `Math.ceil`, `Math.floor` hay `Math.rint` sẽ không tự thêm độ chính xác.

Ví dụ:

~~~java
double large = 1e20;
System.out.println(Math.rint(large) == large); // true
~~~

Tuy `large` đã là giá trị nguyên toán học trong binary64, nó **vượt miền `long`**. Do vậy:

~~~java
System.out.println(Math.round(1e20)); // 9223372036854775807
~~~

Kết quả trên là **`Long.MAX_VALUE`**, không phải `100000000000000000000`, vì `Math.round(double)` chỉ trả `long`.

Điểm mấu chốt: “đã làm tròn thành số nguyên” và “có thể lưu trong kiểu số nguyên đích” là **hai câu hỏi khác nhau**.

## 15. Rounding trong tính toán và rounding khi hiển thị

Nếu mục tiêu chỉ là **hiển thị** 2 chữ số thập phân, định dạng bằng `NumberFormat`/`DecimalFormat` hoặc `String.format` theo locale và rounding mode đã chọn có thể phù hợp hơn thay đổi giá trị tính toán.

Ví dụ với `BigDecimal`:

~~~java
BigDecimal amount = new BigDecimal("12.345");
String display = amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
System.out.println(display); // 12.34
~~~

Nếu xử lý tiền, dùng `BigDecimal` hoặc lưu số nguyên đơn vị nhỏ nhất là một lựa chọn thiết kế phổ biến. **Không làm tròn ở mỗi bước tính trung gian** trừ khi yêu cầu nghiệp vụ bắt buộc; làm tròn lặp nhiều lần có thể tích lũy sai số.

Nếu chỉ hiển thị, giữ giá trị tính toán nội bộ với độ chính xác phù hợp và định dạng ở điểm xuất dữ liệu. Nếu API quy định “số thập phân hai chữ số” thực sự là thuộc tính dữ liệu, phải kiểm soát `scale`/kiểu biểu diễn ở ranh giới nhập và lưu.

## 16. Những lỗi thường gặp

| Nhầm lẫn | Vấn đề | Cách xử lý |
| --- | --- | --- |
| “ceil luôn tăng trị tuyệt đối” | Với số âm, ceil hướng tới +∞, có thể làm gần 0 hơn | Hiểu định nghĩa `ceil(x) >= x` |
| “floor là cắt phần thập phân” | Với số âm, `floor(-2.8)=-3` nhưng cast cho −2 | Chọn `floor` hoặc cast đúng mục đích |
| “rint và round giống nhau” | Khác tie rule và kiểu trả về | `rint` ties-even; `round` ties +∞ |
| “round là half-away-from-zero” | `round(-2.5)=-2` | Dùng `BigDecimal.HALF_UP` khi cần xa 0 |
| “round trả double” | `round(float)` trả int, `round(double)` trả long | Kiểm tra đúng overload |
| “rint trả int” | `rint` trả double | Chuyển có kiểm tra miền |
| “round(NaN) ném lỗi” | Trả 0, có thể che dữ liệu sai | Kiểm tra `isFinite` trước |
| “round(Infinity) ném lỗi” | Trả cực trị của int/long | Kiểm tra miền trước |
| “ceil/floor/rint(NaN) thành 0” | Trả NaN | Kiểm tra giá trị đặc biệt |
| “−0.0 giống hệt +0.0” | `==` true nhưng bit dấu khác | Chọn có bảo toàn dấu zero không |
| “Math.round giúp parse số nguyên lớn” | `double` có thể mất chữ số trước | Parse `long`/`BigInteger` từ chuỗi |
| “nhân 100 rồi round luôn chính xác decimal” | Sai số binary64/overflow | Dùng `BigDecimal(String)` và `setScale` |
| “HALF_UP giống Math.round cho số âm” | HALF_UP đi xa 0, Math.round ưu tiên +∞ | Kiểm tra chính sách ở điểm hòa |
| “Math và StrictMath có quy tắc tie khác nhau” | Hai API cùng ngữ nghĩa làm tròn đã công bố | Đọc hợp đồng của từng phương thức |

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Kết quả của `Math.ceil(-4.2)` và `Math.floor(-4.2)`?

**Đáp án:** `-4.0` và `-5.0`. Ceil đi tới +∞; floor đi tới −∞.

**Câu 2.** Vì sao `Math.rint(2.5)` là `2.0`, trong khi `Math.round(2.5)` là `3`?

**Đáp án:** `rint` chọn số **chẵn** ở điểm hòa; `round` chọn số nguyên về phía **+∞** ở điểm hòa. Ngoài ra `rint` trả double, `round(double)` trả long.

**Câu 3.** `Math.round(-2.5)` có bằng `-3` như `HALF_UP` không?

**Đáp án:** Không. `Math.round(-2.5)` bằng `-2`, còn `new BigDecimal("-2.5").setScale(0, HALF_UP)` bằng `-3`.

**Câu 4.** Vì sao `Math.round(Double.NaN)` nguy hiểm nếu dùng để xử lý dữ liệu đầu vào?

**Đáp án:** Nó trả `0L` thay vì báo lỗi, có thể che giấu một giá trị không hợp lệ. Kiểm tra `Double.isFinite` trước khi làm tròn.

**Câu 5.** Vì sao `Math.round(1e20)` không trả nguyên giá trị toán học \(10^{20}\)?

**Đáp án:** `Math.round(double)` trả `long`, nhưng \(10^{20}\) vượt `Long.MAX_VALUE`, nên kết quả bị giới hạn về `Long.MAX_VALUE`.

**Câu 6.** Dữ liệu decimal `"1.005"` cần làm tròn hai chữ số thập phân với `HALF_UP`. Nên dùng gì?

**Đáp án:** `new BigDecimal("1.005").setScale(2, RoundingMode.HALF_UP)` cho `1.01`; không dựa vào `Math.round(x*100)/100` nếu cần precision decimal.

**Câu 7.** Kết quả `Math.rint(-0.5)` và `Math.round(-0.5)`?

**Đáp án:** `-0.0` (double) và `0` (long). `rint` có thể giữ dấu zero; `round` trả kiểu nguyên không có zero âm.

## 18. Tóm tắt và tài liệu tham khảo

Hãy nhớ bốn câu ngắn:

1. **`ceil`:** tới số nguyên **bên trên** theo trục số (`+∞`).
2. **`floor`:** tới số nguyên **bên dưới** theo trục số (`-∞`).
3. **`rint`:** số nguyên gần nhất, **hòa chọn chẵn**, trả `double`.
4. **`round`:** số nguyên gần nhất, **hòa chọn phía dương**, trả `int` hoặc `long`.

Đối với dữ liệu nghiêm ngặt, kiểm tra `NaN`/`Infinity`, miền của kiểu đích và yêu cầu chính xác **trước** khi làm tròn. Với số decimal cần quy tắc làm tròn rõ và chính xác, ưu tiên `BigDecimal` cùng `RoundingMode`. Không nhầm làm tròn **giá trị tính toán** với định dạng **chuỗi hiển thị**.

Nguồn chính thức:

- [Java SE 17 — Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): định nghĩa `ceil`, `floor`, `rint`, `round` và xử lý số đặc biệt.
- [Java SE 17 — StrictMath](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html): các phương thức tương ứng và tính tái lập.
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): `HALF_UP`, `HALF_EVEN`, `CEILING`, `FLOOR` và các chế độ khác.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `setScale` và độ chính xác decimal.
- [JLS Java SE 17 — Floating-Point Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): độ chính xác, NaN, infinity và signed zero.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-008">nextUp, nextDown, nextAfter và ulp</a>

<details>
<summary>Xem chi tiết</summary>

# `nextUp`, `nextDown`, `nextAfter` và `ulp` trong Java

Khi làm việc với `float` và `double`, giữa hai số thực gần nhau không phải lúc nào cũng tồn tại một giá trị dấu phẩy động có thể biểu diễn. Java cung cấp bốn phương thức trong `Math` và `StrictMath` để tìm **giá trị biểu diễn liền kề** và **khoảng cách ở độ chính xác máy**:

- `nextUp(x)`: giá trị biểu diễn kế tiếp theo hướng dương vô cực.
- `nextDown(x)`: giá trị biểu diễn kế tiếp theo hướng âm vô cực.
- `nextAfter(start, direction)`: giá trị biểu diễn kế tiếp tính từ `start` theo hướng của `direction`.
- `ulp(x)`: kích thước một *unit in the last place* (ULP) tại `x`.

Chúng phục vụ kiểm thử biên, nghiên cứu sai số và thuật toán số; **không** phải các hàm “cộng một chút bất kỳ” hoặc làm tròn số thập phân để hiển thị.

## 1. Vì sao cần số biểu diễn liền kề?

Java sử dụng định dạng nhị phân IEEE 754 cho hai kiểu dấu phẩy động phổ biến:

| Kiểu | Độ dài | Số bit có nghĩa hữu hiệu | Ví dụ về ULP tại `1.0` |
|---|---:|---:|---|
| `float` | 32 bit | 24 bit | `2^-23` ≈ `1.1920929e-7` |
| `double` | 64 bit | 53 bit | `2^-52` ≈ `2.220446049250313e-16` |

Một tập hữu hạn các mẫu bit không thể biểu diễn mọi số thực. Ví dụ: `1.0` là giá trị `double` chính xác, nhưng số `double` lớn hơn nó ngay lập tức là:

```text
1.0
↓ giá trị double kế tiếp
0x1.0000000000001p0  =  1.0000000000000002 (dạng thập phân được làm ngắn khi in)
```

Ở đây `0x...p...` là ký pháp số dấu phẩy động nhị phân dạng thập lục phân của Java: phần sau `p` là lũy thừa của **2**, không phải 10. `Double.toHexString(...)` cho phép quan sát các bước biểu diễn chính xác hơn việc in dạng thập phân thông thường.

Khoảng cách giữa hai số liền kề **không cố định** trên toàn miền: quanh số có độ lớn lớn hơn, các bước thường lớn hơn; quanh số nhỏ, các bước nhỏ hơn, xuống tới **subnormal**. Bởi vậy, giả định rằng luôn có thể dùng `x + 1e-15` để tới số tiếp theo là sai: phép cộng có thể không đổi `x`, nhảy qua nhiều giá trị, hoặc tạo kết quả không phù hợp với kiểu dữ liệu.

> Trong bài này, “liền kề” nghĩa là **không có số `float`/`double` có thể biểu diễn ở giữa** theo thứ tự số học. Nó không có nghĩa là hai số nguyên hoặc hai số thập phân liên tiếp.

## 2. API và nguyên tắc chọn hướng

Các overload chính của `java.lang.Math`:

```java
public static double nextUp(double d);
public static float  nextUp(float f);

public static double nextDown(double d);
public static float  nextDown(float f);

public static double nextAfter(double start, double direction);
public static float  nextAfter(float start, double direction);

public static double ulp(double d);
public static float  ulp(float f);
```

`StrictMath` cũng cung cấp các phương thức tương ứng với cùng ý nghĩa. Điểm dễ bỏ qua là overload `nextAfter(float, double)` nhận **`direction` kiểu `double`** nhưng **trả `float`**: giá trị *start* quyết định lưới số biểu diễn được bước qua.

Với giá trị bắt đầu hữu hạn và không ở trường hợp biên:

| Lời gọi | Cách hiểu |
|---|---|
| `Math.nextUp(x)` | Một bước sang phải trên trục số |
| `Math.nextDown(x)` | Một bước sang trái trên trục số |
| `Math.nextAfter(x, y)`, với `y > x` | Một bước từ `x` về phía `y`, tương tự `nextUp(x)` |
| `Math.nextAfter(x, y)`, với `y < x` | Một bước từ `x` về phía `y`, tương tự `nextDown(x)` |
| `Math.nextAfter(x, y)`, với `x == y` | Trả chính `direction` (chi tiết quan trọng khi hai số 0 khác dấu) |
| `Math.ulp(x)` | Độ lớn ULP tại `x`, luôn không âm với đầu vào hữu hạn |

`nextAfter(start, direction)` **không nhảy thẳng tới `direction`**; nó di chuyển đúng một bước trên lưới giá trị thuộc kiểu của `start`, trừ khi hai giá trị đã bằng nhau theo phép so sánh số học (hoặc gặp `NaN`/biên đặc biệt).

### Phân biệt `nextAfter` và `nextUp`/`nextDown`

```java
double x = 1.0;

Math.nextAfter(x, 2.0); // Cùng kết quả với Math.nextUp(x)
Math.nextAfter(x, 0.0); // Cùng kết quả với Math.nextDown(x)
Math.nextAfter(x, x);   // 1.0: đã tới hướng đích
```

Đây là **đoạn minh họa**, không phải lớp Java độc lập. Khi chỉ cần hướng cố định, `nextUp`/`nextDown` thể hiện mục đích trực tiếp hơn; khi hướng phụ thuộc đích, `nextAfter` cho phép biểu đạt thuật toán tổng quát.

## 3. Chương trình Java 17+ chạy và kiểm chứng

Tạo tệp `FloatingNeighborsDemo.java` với nội dung:

```java
public class FloatingNeighborsDemo {
    public static void main(String[] args) {
        double x = 1.0;
        System.out.println("nextUp(1.0): " + Double.toHexString(Math.nextUp(x)));
        System.out.println("nextDown(1.0): " + Double.toHexString(Math.nextDown(x)));
        System.out.println("nextAfter(1.0, 2.0): " + Double.toHexString(Math.nextAfter(x, 2.0)));
        System.out.println("nextAfter(1.0, 0.0): " + Double.toHexString(Math.nextAfter(x, 0.0)));
        System.out.println("ulp(1.0): " + Double.toHexString(Math.ulp(x)));
        System.out.println("gap above: " + Double.toHexString(Math.nextUp(x) - x));
        System.out.println("gap below: " + Double.toHexString(x - Math.nextDown(x)));
        System.out.println("nextUp(-0.0): " + Double.toHexString(Math.nextUp(-0.0)));
        System.out.println("nextDown(+0.0): " + Double.toHexString(Math.nextDown(+0.0)));
        System.out.println("nextAfter(+0.0, -0.0): " + Double.toHexString(Math.nextAfter(+0.0, -0.0)));
        System.out.println("ulp(0.0): " + Double.toHexString(Math.ulp(0.0)));
        System.out.println("nextUp(MAX_VALUE): " + Math.nextUp(Double.MAX_VALUE));
        System.out.println("nextDown(+INFINITY) == MAX_VALUE: "
                + (Math.nextDown(Double.POSITIVE_INFINITY) == Double.MAX_VALUE));
        float f = 1.0f;
        System.out.println("nextAfter(1.0f, 2.0): " + Float.toHexString(Math.nextAfter(f, 2.0)));
        System.out.println("ulp(1.0f): " + Float.toHexString(Math.ulp(f)));
        System.out.println("NaN: " + Double.isNaN(Math.nextUp(Double.NaN)));
        System.out.println("StrictMath agrees: " + (StrictMath.nextUp(x) == Math.nextUp(x)));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 FloatingNeighborsDemo.java
java FloatingNeighborsDemo
```

Stdout đã được đối chiếu bằng cách biên dịch và chạy chương trình:

```text
nextUp(1.0): 0x1.0000000000001p0
nextDown(1.0): 0x1.fffffffffffffp-1
nextAfter(1.0, 2.0): 0x1.0000000000001p0
nextAfter(1.0, 0.0): 0x1.fffffffffffffp-1
ulp(1.0): 0x1.0p-52
gap above: 0x1.0p-52
gap below: 0x1.0p-53
nextUp(-0.0): 0x0.0000000000001p-1022
nextDown(+0.0): -0x0.0000000000001p-1022
nextAfter(+0.0, -0.0): -0x0.0p0
ulp(0.0): 0x0.0000000000001p-1022
nextUp(MAX_VALUE): Infinity
nextDown(+INFINITY) == MAX_VALUE: true
nextAfter(1.0f, 2.0): 0x1.000002p0
ulp(1.0f): 0x1.0p-23
NaN: true
StrictMath agrees: true
```

**Điểm quan trọng từ kết quả:**

1. Kết quả `nextUp(1.0)` là số `double` liền trên `1.0`; `nextDown(1.0)` là số liền dưới. `nextAfter` đưa ra cùng hai số nếu hướng đích lần lượt ở trên hoặc dưới.
2. `ulp(1.0)` bằng bước **phía trên** tại `1.0`, nhưng bước **phía dưới** chỉ bằng một nửa (vì `1.0` là lũy thừa chính xác của 2, nằm ở ranh giới hai vùng bước).
3. `nextUp(-0.0)` và `nextDown(+0.0)` đi tới hai giá trị subnormal nhỏ nhất khác 0, lần lượt dương và âm.
4. `nextAfter(+0.0, -0.0)` trả `-0.0` dù phép so sánh `+0.0 == -0.0` là `true`; cách ghi bằng hex giúp nhìn thấy dấu.
5. Ở biên trên hữu hạn, bước kế tiếp của `Double.MAX_VALUE` theo chiều tăng là `+Infinity`.
6. Overload `float` đi trên lưới 32 bit nên bước quanh `1.0f` lớn hơn bước của `double`.

## 4. Hiểu chính xác `ulp(x)`

ULP là đơn vị có trọng số của chữ số cuối trong biểu diễn dấu phẩy động tại độ lớn đang xét. Trong miền **normal**, bước thường có dạng:

- `double`, có độ chính xác 53 bit: khoảng `2^(e - 52)`;
- `float`, có độ chính xác 24 bit: khoảng `2^(e - 23)`,

với `e` là số mũ nhị phân của khoảng giá trị đang xét. Ở vùng số **subnormal**, độ giãn cách cố định ở mức nhỏ nhất của định dạng.

Ví dụ:

```java
Math.ulp(1.0);     // double: 2^-52
Math.ulp(2.0);     // double: 2^-51
Math.ulp(1.0f);    // float: 2^-23
Math.ulp(2.0f);    // float: 2^-22
Math.ulp(0.0);     // Double.MIN_VALUE (số double dương nhỏ nhất khác 0)
Math.ulp(0.0f);    // Float.MIN_VALUE  (số float dương nhỏ nhất khác 0)
```

Đây là các biểu thức minh họa; để kiểm tra đầu ra có thể in bằng `Double.toHexString` hoặc `Float.toHexString`.

### `ulp(x)` không luôn bằng `nextUp(x) - x`

Một ngộ nhận dễ gây lỗi kiểm thử là áp dụng đẳng thức `Math.ulp(x) == Math.nextUp(x) - x` với **mọi** `x`. Ở ranh giới các lũy thừa của 2 hoặc khi đổi dấu, khoảng cách liền kề hai bên có thể khác nhau.

Ví dụ với `x = 1.0`:

| Đại lượng | Kết quả chính xác |
|---|---|
| `Math.ulp(1.0)` | `2^-52` |
| `Math.nextUp(1.0) - 1.0` | `2^-52` |
| `1.0 - Math.nextDown(1.0)` | `2^-53` |

Với `x = -1.0`, tình huống đảo chiều: đi về phía `+∞` tiến gần 0 hơn và có bước `2^-53`, còn đi về phía `-∞` có bước `2^-52`; trong khi `Math.ulp(-1.0)` vẫn bằng `2^-52`. Do đó nếu thuật toán cần **khoảng cách ở một hướng cụ thể**, dùng hai hàm láng giềng để đo khoảng cách ở hướng đó, kết hợp xử lý các giá trị đặc biệt.

Không lấy `ulp(x)` làm “sai số tuyệt đối bảo đảm” của mọi phép tính. Sai số phụ thuộc phép toán, thuật toán, độ lớn các toán hạng, tình trạng mất chữ số có nghĩa và số bước tính. Một ULP có thể hữu ích cho việc đo sai khác biểu diễn, nhưng quy tắc chấp nhận sai số phải đến từ yêu cầu của bài toán.

### Vì sao không có một epsilon cố định?

`Math.ulp(0.0)` bằng `Double.MIN_VALUE`, cực nhỏ; trong khi `Math.ulp(1.0)` lớn hơn nhiều, còn `Math.ulp(1e20)` lại lớn hơn nữa. Ngay cả `Math.ulp(1.0)` cũng không có ý nghĩa là mọi phép tính đều có sai số tối đa đúng từng ấy.

Trong kiểm thử số học, việc so sánh theo sai số tuyệt đối, sai số tương đối hoặc số ULP phải cân nhắc miền dữ liệu và các trường hợp 0, vô cực, `NaN`, tràn/thiếu độ lớn. Đừng áp dụng một `epsilon` cố định chỉ vì nó có tên quen thuộc.

## 5. Những biên đặc biệt phải kiểm thử

`float` và `double` có hai số 0 (`+0.0`, `-0.0`), hai vô cực, `NaN` và các giá trị subnormal. Vì vậy không thể suy luận mọi trường hợp chỉ bằng bảng số thực thông thường.

| Đầu vào | Kết quả hoặc hành vi |
|---|---|
| `Math.nextUp(+0.0)` hoặc `Math.nextUp(-0.0)` | `Double.MIN_VALUE` |
| `Math.nextDown(+0.0)` hoặc `Math.nextDown(-0.0)` | `-Double.MIN_VALUE` |
| `Math.nextAfter(+0.0, -0.0)` | `-0.0` (trả đúng `direction`) |
| `Math.nextAfter(-0.0, +0.0)` | `+0.0` (trả đúng `direction`) |
| `Math.nextUp(Double.MAX_VALUE)` | `Double.POSITIVE_INFINITY` |
| `Math.nextDown(-Double.MAX_VALUE)` | `Double.NEGATIVE_INFINITY` |
| `Math.nextDown(Double.POSITIVE_INFINITY)` | `Double.MAX_VALUE` |
| `Math.nextUp(Double.NEGATIVE_INFINITY)` | `-Double.MAX_VALUE` |
| `Math.nextUp(Double.POSITIVE_INFINITY)` | `Double.POSITIVE_INFINITY` |
| `Math.nextDown(Double.NEGATIVE_INFINITY)` | `Double.NEGATIVE_INFINITY` |
| `Math.nextAfter(Double.NaN, 1.0)` | `NaN` |
| `Math.nextAfter(1.0, Double.NaN)` | `NaN` |
| `Math.ulp(Double.NaN)` | `NaN` |
| `Math.ulp(Double.POSITIVE_INFINITY)` | `Double.POSITIVE_INFINITY` |

Với overload `float`, thay các hằng tương ứng bằng `Float.MIN_VALUE`/`Float.MAX_VALUE` và dùng kết quả kiểu `float`. Đừng nhầm tên `Double.MIN_VALUE`: đây là **số dương nhỏ nhất khác 0** có thể biểu diễn bằng `double`, **không phải số âm nhỏ nhất**. Số `double` hữu hạn âm có độ lớn lớn nhất là `-Double.MAX_VALUE`.

### Số 0 có dấu và `nextAfter`

`+0.0 == -0.0` là `true` trong phép so sánh số học của Java, nhưng dạng bit và một vài hành vi số học của chúng khác nhau. Khi `start` và `direction` bằng nhau, đặc tả `nextAfter` trả chính tham số `direction`, vì vậy:

```java
double result = Math.nextAfter(+0.0, -0.0);
boolean negativeZero =
        Double.doubleToRawLongBits(result) == Double.doubleToRawLongBits(-0.0);
// negativeZero == true
```

Đoạn này là mã minh họa đặt trong phương thức. Nếu dùng `== 0.0` để kiểm tra kết quả, bạn **không thể phân biệt** hai dấu 0. Muốn quan sát bit, dùng `Double.doubleToRawLongBits`, `Float.floatToRawIntBits` hoặc biểu diễn hex thích hợp.

### Subnormal và giới hạn nhỏ nhất

Với `double`:

- `Double.MIN_NORMAL` là số **normal dương nhỏ nhất** (xấp xỉ `2.225e-308`).
- `Double.MIN_VALUE` là số **subnormal dương nhỏ nhất khác 0** (xấp xỉ `4.9e-324`).
- `Math.nextUp(0.0) == Double.MIN_VALUE`.
- `Math.nextDown(Double.MIN_VALUE)` trả `+0.0` vì giá trị có thể biểu diễn gần hơn theo hướng giảm là số 0.
- `Math.ulp(0.0)` và `Math.ulp(Double.MIN_VALUE)` bằng `Double.MIN_VALUE`.

Đây là lý do các giá trị rất nhỏ không có cùng số chữ số có nghĩa hữu hiệu như các giá trị normal. Khi đi qua ranh giới normal/subnormal, phải kiểm thử vì độ chính xác tương đối thay đổi.

## 6. `Math` và `StrictMath` khác nhau thế nào trong bài này?

Các phương thức `nextUp`, `nextDown`, `nextAfter` và `ulp` đều có trên `Math` và `StrictMath` với cùng quy tắc xử lý giá trị lân cận. Bạn có thể viết:

```java
double a = Math.nextAfter(1.0, 2.0);
double b = StrictMath.nextAfter(1.0, 2.0);
// a và b cùng là giá trị double liền trên 1.0
```

Đây là mã minh họa dùng trong lớp/phương thức. `StrictMath` thường được nhắc tới khi quan tâm tính tái lập kết quả của các phép toán số học; tuy nhiên với **bốn phép bước trên lưới biểu diễn** đang học, quy tắc biên và kiểu trả về là trọng tâm. Đừng suy rộng từ nhóm hàm này sang mọi hàm lượng giác hoặc hàm siêu việt mà chưa đọc hợp đồng API cụ thể.

## 7. Ứng dụng thực tế

### 7.1. Kiểm thử sát ranh giới của điều kiện

Giả sử quy tắc yêu cầu một giá trị `double` phải **nhỏ hơn hoặc bằng** `limit`:

```java
static boolean withinLimit(double value, double limit) {
    return value <= limit;
}
```

Đây là phương thức minh họa. Với `limit = 1.0`, một bộ kiểm thử biên có thể dùng ba giá trị:

```java
double limit = 1.0;
boolean below = withinLimit(Math.nextDown(limit), limit); // true
boolean exact = withinLimit(limit, limit);                // true
boolean above = withinLimit(Math.nextUp(limit), limit);  // false
```

Từng giá trị kiểm thử là một điểm có thể biểu diễn được; không cần đoán “một số rất nhỏ” để cộng/trừ. Khi `limit` là vô cực, `NaN` hoặc có thể phát sinh overflow, cần định nghĩa thêm kỳ vọng và test riêng.

### 7.2. Bước về một mốc đích

Nếu cần biết **bước đầu tiên** từ một số `double` về phía mốc:

```java
double current = 1.0;
double target = 2.0;
double step = Math.nextAfter(current, target);
// step == Math.nextUp(current)
```

Một bước của `nextAfter` thường rất nhỏ; đừng dùng nó để duyệt tùy ý từ `1.0` đến `2.0`. Giữa hai giá trị này có số lượng cực lớn các số `double`, và thuật toán duyệt từng số có thể quá chậm. Nếu thực sự cần lặp, phải có giới hạn số bước, điều kiện kết thúc và xử lý `NaN`, vô cực, trường hợp không tiến triển.

### 7.3. Đo mức chênh trên lưới số

`ulp(...)` có thể giúp diễn giải mức độ sai khác gần một tham chiếu, ví dụ quan sát sự khác biệt giữa `1.0` với số `double` ngay phía trên:

```java
double baseline = 1.0;
double observed = Math.nextUp(baseline);

double delta = observed - baseline;
double oneUlpAtBaseline = Math.ulp(baseline);
// delta == oneUlpAtBaseline, đúng cho baseline = 1.0
```

Ở ví dụ cụ thể này, sai khác đúng một ULP tại `baseline`; với giá trị ở biên lũy thừa hai hoặc số âm, không mặc định rằng sai khác hai láng giềng theo **mọi hướng** đều bằng `ulp(baseline)`. Muốn đo khoảng cách ULP chính xác cho số ở miền rộng, thường phải so sánh biểu diễn bit đã chuẩn hóa theo thứ tự và xử lý `NaN`, signed zero cũng như vô cực.

## 8. Các lỗi thường gặp

**Nhầm 1 — `Math.nextUp(x)` là `x + Math.ulp(x)` trong mọi trường hợp.** Công thức cộng có thể hữu ích cho một số điểm, nhưng thất bại ở các biên như vô cực, `NaN`, và không mô tả nhất quán khoảng cách theo hướng trái/phải. Dùng trực tiếp `nextUp` khi mục tiêu là tìm láng giềng phía trên.

**Nhầm 2 — `nextAfter(start, direction)` trả luôn `direction`.** Khi hai số khác nhau, nó chỉ tiến **một giá trị biểu diễn**; tham số `direction` quyết định hướng di chuyển.

**Nhầm 3 — `Math.nextAfter(1.0f, 2.0)` hoạt động theo bước của `double`.** Overload có `start` kiểu `float` trả về `float`, vì vậy kết quả sát `1.0f` theo bước `float`. Phải kiểm tra kiểu toán hạng đầu tiên và chữ `f` ở literal.

**Nhầm 4 — `Float.MIN_VALUE` là số âm nhỏ nhất.** Nó là số dương khác 0 nhỏ nhất; phía âm liền dưới 0 là `-Float.MIN_VALUE`. Số hữu hạn âm có độ lớn lớn nhất là `-Float.MAX_VALUE`.

**Nhầm 5 — Bỏ qua `+0.0` và `-0.0`.** Phép so sánh `==` coi chúng bằng nhau nhưng `nextAfter(+0.0, -0.0)` trả giá trị đích `-0.0`. Cần kiểm thử bằng raw bits nếu dấu 0 là điều kiện quan trọng.

**Nhầm 6 — Xem `ulp(x)` như ngưỡng chính xác cho mọi phép toán.** `ulp` là thuộc tính của miền số biểu diễn, không phải bảo đảm chất lượng của một thuật toán. Với so sánh gần đúng, cần quy tắc tolerance theo bài toán.

**Nhầm 7 — Tạo hướng bằng phép tính đã bị làm tròn về `start`.** Ví dụ biểu thức `1.0 + 1e-20` được tính bằng `double` thành `1.0`, nên `Math.nextAfter(1.0, 1.0 + 1e-20)` trả `1.0` thay vì bước lên. Nếu mục tiêu đơn giản là số biểu diễn liền trên `1.0`, gọi `Math.nextUp(1.0)`.

**Nhầm 8 — Dùng bước biểu diễn để thay thế làm tròn theo quy tắc tài chính.** `ulp` liên quan độ chính xác nhị phân, không phải số chữ số sau dấu thập phân. Khi cần quy tắc thập phân, cân nhắc `BigDecimal`/`RoundingMode` theo đặc tả nghiệp vụ.

## 9. Câu hỏi tự kiểm tra

Hãy tự đoán trước khi chạy trong JShell hoặc một lớp Java:

1. `Math.nextUp(-0.0)` là gì?
2. `Math.nextDown(Double.POSITIVE_INFINITY)` là gì?
3. `Math.nextAfter(1.0, 1.0)` có di chuyển không?
4. Tại sao `Math.nextUp(1.0) - 1.0` khác `1.0 - Math.nextDown(1.0)`?
5. Tại sao `Math.nextAfter(1.0f, 2.0)` có bước lớn hơn `Math.nextAfter(1.0, 2.0)`?
6. `Math.ulp(Double.NaN)` có phải một số thực hữu hạn dùng làm tolerance không?

**Đáp án:** (1) `Double.MIN_VALUE`; (2) `Double.MAX_VALUE`; (3) không, trả `1.0`; (4) `1.0` nằm đúng ranh giới nhị phân, khoảng cách hai bên là `2^-52` và `2^-53`; (5) `float` có 24 bit chính xác hữu hiệu, `double` có 53 bit; (6) không, kết quả là `NaN`.

## Ghi nhớ

- `nextUp`/`nextDown`: láng giềng theo hướng cố định trên trục số; `nextAfter`: láng giềng về phía mục tiêu.
- Lưới của **kiểu `float` hoặc `double`** quyết định bước, và overload `float` của `nextAfter` nhận hướng kiểu `double`.
- `ulp(x)` cho biết đơn vị bước tại độ lớn `x`; không tự đồng nhất với khoảng cách theo **mọi hướng**.
- Phải kiểm thử signed zero, subnormal, giá trị hữu hạn lớn nhất, vô cực và `NaN`.
- Để nhìn rõ từng giá trị, dùng `Double.toHexString(...)`, `Float.toHexString(...)` và các hàm raw-bit khi cần phân biệt dấu 0.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-009">copySign, scalb và getExponent</a>

<details>
<summary>Xem chi tiết</summary>

# `copySign`, `scalb` và `getExponent` trong Java: dấu, lũy thừa của 2 và số mũ IEEE 754

`java.lang.Math` và `java.lang.StrictMath` có ba nhóm phương thức thường xuất hiện khi cần làm việc **gần với cách biểu diễn nhị phân của `float`/`double`**:

- **`copySign(magnitude, sign)`**: lấy độ lớn của đối số thứ nhất nhưng gắn **dấu của đối số thứ hai**, bao gồm dấu của zero âm.
- **`scalb(value, scaleFactor)`**: nhân giá trị với **\(2^{scaleFactor}\)** theo quy tắc làm tròn dấu phẩy động, rất hữu ích khi điều chỉnh thang đo nhị phân.
- **`getExponent(value)`**: trả về **số mũ không có bias của trường exponent** trong biểu diễn dấu phẩy động, đồng thời quy định các giá trị sentinel riêng cho zero, subnormal, NaN và infinity.

```java
double signed = Math.copySign(3.5, -0.0); // -3.5
double scaled = Math.scalb(1.5, 3);       // 12.0 = 1.5 × 2^3
int exponent = Math.getExponent(8.0);     // 3
```

Ba hàm này **không thay thế lẫn nhau**: `copySign` thay đổi bit dấu mà giữ độ lớn; `scalb` thay đổi thang độ lớn và có thể overflow/underflow; `getExponent` quan sát thông tin số mũ **chứ không tính một logarit tổng quát**. Bài này tập trung vào semantics của Java 17+, những giá trị IEEE 754 đặc biệt, ví dụ chạy được và cách dùng an toàn.

## 1. Nhắc lại mô hình biểu diễn `float` và `double`

Hai kiểu dấu phẩy động cơ bản của Java dùng định dạng nhị phân IEEE 754. Một số **normal** hữu hạn khác zero được biểu diễn bằng bit dấu, số mũ có bias và fraction; về ý tưởng có dạng:

\[
(-1)^{sign}\times (1.fraction)_2 \times 2^{exponent}
\]

| Thành phần | `float` / binary32 | `double` / binary64 |
| --- | ---: | ---: |
| Tổng bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit exponent | 8 | 11 |
| Bit fraction | 23 | 52 |
| Exponent của **normal** nhỏ nhất | `Float.MIN_EXPONENT = -126` | `Double.MIN_EXPONENT = -1022` |
| Exponent của **normal** lớn nhất | `Float.MAX_EXPONENT = 127` | `Double.MAX_EXPONENT = 1023` |
| Số hữu hạn **normal** dương nhỏ nhất | `Float.MIN_NORMAL` | `Double.MIN_NORMAL` |
| Số dương nhỏ nhất, kể cả **subnormal** | `Float.MIN_VALUE` | `Double.MIN_VALUE` |

Số **subnormal** có trường exponent bằng 0 và không dùng bit 1 ẩn ở đầu phần trị như số normal. Nhờ subnormal, Java biểu diễn được những số dương nhỏ hơn `MIN_NORMAL`, với độ chính xác giảm dần.

Ngoài số hữu hạn còn có `+0.0`, `-0.0`, `+Infinity`, `-Infinity` và **NaN**. Những giá trị này khiến các hàm thao tác dấu, scale hoặc exponent cần quy tắc đặc biệt.

**Nhầm lẫn cần tránh:** `Double.MIN_VALUE` là **số dương subnormal nhỏ nhất**, **không phải số `double` âm nhất**. Tương tự, `Math.getExponent(Double.MIN_VALUE)` không cho exponent toán học `-1074` của riêng giá trị subnormal đó; nó trả sentinel theo quy tắc API.

## 2. `Math.copySign`: lấy độ lớn từ một số và dấu từ số khác

Chữ ký overload:

```java
static double Math.copySign(double magnitude, double sign)
static float Math.copySign(float magnitude, float sign)
```

Có phương thức tương ứng trong `StrictMath`. Hai đối số phải phù hợp overload kiểu `double` hoặc `float`; hàm không tạo kiểu wrapper mới và không đọc chuỗi văn bản.

Ví dụ:

```java
Math.copySign(3.5, -2.0);        // -3.5
Math.copySign(-3.5, 2.0);        // 3.5
Math.copySign(0.0, -1.0);        // -0.0
Math.copySign(-0.0, +1.0);       // +0.0
Math.copySign(Double.POSITIVE_INFINITY, -1.0);
// -Infinity
```

Đối số đầu cung cấp **magnitude**. Nếu nó đã âm, `copySign` có thể chuyển thành dương khi đối số thứ hai mang dấu dương. Đây không phải phép cộng hai giá trị, cũng không phải lấy tích hay kiểm tra `sign > 0`.

### Dấu của `-0.0`: vì sao `sign < 0` không đủ?

Trong so sánh dấu phẩy động primitive:

```java
-0.0 == +0.0      // true
-0.0 < 0.0        // false
```

Nhưng chúng có **bit dấu khác nhau**. `Math.copySign(3.5, -0.0)` trả **`-3.5`**, vì đọc **dấu của zero âm**, không kiểm tra `-0.0 < 0`. Tương tự:

```java
double negativeZero = Math.copySign(0.0, -1.0);
boolean sameValue = negativeZero == 0.0; // true
long rawBits = Double.doubleToRawLongBits(negativeZero);
// 0x8000000000000000L
```

Nếu nghiệp vụ cần phát hiện zero âm, có thể kiểm tra bit dấu bằng `Double.doubleToRawLongBits` hoặc quan sát `1.0 / negativeZero` là `-Infinity`. Không dùng phép so sánh `value < 0` làm cách nhận diện tổng quát bit dấu.

### Dấu của vô cực

`copySign` giữ độ lớn vô cực và thay dấu:

```java
double result = Math.copySign(
    Double.POSITIVE_INFINITY, -1.0
);
// -Infinity
```

Nếu `magnitude` bằng `0.0`, hàm vẫn trả zero có dấu tương ứng; nếu `magnitude` là vô cực, hàm trả vô cực cùng dấu với `sign`.

### Trường hợp `NaN` và khác biệt với `StrictMath`

**Quan trọng về đặc tả Java 17:** Với đối số **`sign` là NaN**, `StrictMath.copySign` **luôn coi NaN đó có dấu dương**. Trong khi đó, `Math.copySign` **không bắt buộc** xử lý mọi NaN sign như dương; các triển khai được phép dùng bit dấu của một số mẫu NaN để tối ưu.

Vì vậy không nên viết logic nghiệp vụ dựa trên dấu của `NaN` rồi mong `Math.copySign` và `StrictMath.copySign` cho kết quả giống nhau trên mọi JVM.

Nếu `magnitude` là NaN, kết quả tiếp tục là NaN; **không dùng phép toán này như giao thức bảo toàn mọi bit payload NaN**. Nếu thực sự cần giữ nguyên mọi mẫu bit vào/ra (kể cả signaling NaN), làm việc với `int`/`long` chứa mẫu bit hay byte thô, vì quá trình đưa signaling NaN qua giá trị dấu phẩy động có thể quiet hóa.

**Khi nào dùng `copySign`?** Ví dụ dựng đáp số theo độ lớn đã biết nhưng cần duy trì dấu của phần tử gốc, giữ signed zero trong xử lý dấu phẩy động, hoặc thực hiện biến đổi biểu diễn có chủ đích. Nó không dùng để “chuẩn hóa giá trị” mà bỏ qua NaN.

## 3. `Math.scalb`: nhân chính xác theo một lũy thừa 2 khi có thể biểu diễn

Chữ ký:

```java
static double Math.scalb(double value, int scaleFactor)
static float Math.scalb(float value, int scaleFactor)
```

Hàm tính ý nghĩa toán học **\(value\times 2^{scaleFactor}\)** và trả kết quả dấu phẩy động với quy tắc làm tròn được định nghĩa. Ví dụ:

```java
Math.scalb(1.5, 3);   // 12.0
Math.scalb(3.0, -1);  // 1.5
Math.scalb(1.0, 10);  // 1024.0
Math.scalb(0.5f, 2);  // 2.0f
```

Khác với phép nhân một số `double` bất kỳ, nhân với lũy thừa 2 về cơ bản **dịch thang của exponent trong hệ nhị phân**. Với đầu vào normal phù hợp và kết quả vẫn ở miền normal có thể biểu diễn, phép scale có thể chính xác. Đây là lý do `scalb` hữu ích trong thuật toán số hoặc chuyển scale nhị phân.

Tuy nhiên, **không được tuyên bố mọi `scalb` đều không làm tròn**:

- Khi kết quả **subnormal**, có thể mất các bit chính xác ở phần trị.
- Khi kết quả quá lớn, có thể **overflow thành `±Infinity`**.
- Khi kết quả quá nhỏ, có thể **underflow thành `±0.0`**, vẫn giữ dấu.
- NaN vẫn là NaN; ±Infinity vẫn là vô cực cùng dấu; zero vẫn là zero cùng dấu.

`scalb` thực hiện scale theo hàm số học dấu phẩy động; **không đổi bit bằng cách tự ý cộng thẳng vào exponent raw**, vốn dễ sai với subnormal, NaN, Infinity và tràn số.

### Vì sao không luôn viết `x * Math.pow(2, n)`?

Công thức toán học giống nhau, nhưng:

```java
double a = Math.scalb(x, n);
// double b = x * Math.pow(2.0, n);
```

Dòng thứ hai tạo **kết quả trung gian `Math.pow(2.0, n)`** có thể overflow hoặc underflow trước khi nhân với `x`, và có thể làm tròn ở những điểm khác. `Math.scalb` nhằm thực hiện thao tác scale theo lũy thừa 2 với đặc tả kết quả riêng, tránh việc xây dựng hệ số trung gian không cần thiết.

Ví dụ, nếu `x` là một số rất nhỏ nhưng `2^n` lớn vượt miền `double`, `Math.pow(2, n)` có thể thành Infinity, còn tích lý tưởng vẫn hữu hạn. `Math.scalb(x, n)` là công cụ phù hợp hơn khi đây thực sự là phép scale cơ số 2.

Không nhầm `scalb` với `Math.pow(value, exponent)`: `scalb(value, n)` nhân `value` với `2^n`, **không** tính `value^n`.

## 4. Độ lớn cực trị: overflow, underflow và subnormal

Trong Java, một `double` có exponent normal tối đa 1023. Tăng scale quá mức có thể tạo vô cực:

```java
Math.scalb(Double.MAX_VALUE, 1);
// Infinity
```

Ở đầu nhỏ, `Double.MIN_VALUE` là subnormal dương nhỏ nhất:

```java
Math.scalb(Double.MIN_VALUE, -1); // 0.0 (underflow)
```

Khi đặt dấu âm cho giá trị nhỏ rồi scale xuống tiếp, dấu có thể vẫn tồn tại trong zero âm:

```java
double x = Math.scalb(-Double.MIN_VALUE, -1);
// -0.0; raw bits 0x8000000000000000
```

**Subnormal không phải lúc nào cũng bằng zero.** Ví dụ:

```java
double half = Math.scalb(Double.MIN_NORMAL, -1);
// Số subnormal dương, mẫu bit 0x0008000000000000L
```

Nó nhỏ hơn `Double.MIN_NORMAL` nhưng khác 0. Điều này giúp giải thích tại sao API `getExponent` ở phần sau trả **cùng một sentinel** cho nhiều giá trị subnormal rất khác nhau, chứ không phải exponent toán học riêng của từng giá trị.

**Không dùng `scalb` để bỏ qua kiểm tra miền:** Nếu đầu vào có thể là NaN/Infinity hoặc scale quá lớn, hãy đặt chính sách xác thực. Nếu tính toán yêu cầu kết quả hữu hạn, kiểm tra `Double.isFinite(result)` (hoặc `Float.isFinite`) sau phép scale và đánh giá khả năng underflow về zero khi đó là lỗi nghiệp vụ.

### Dấu của zero khi scale

`Math.scalb(-0.0, 1000)` vẫn cho **`-0.0`**. Không có gì để tăng lên thành số khác zero: scale chỉ nhân giá trị zero với một hệ số lũy thừa 2 dương về ý nghĩa, không đổi dấu bit zero.

Hàm không sửa đối số gốc, không có side effect trên biến `double` và không cần cấp phát wrapper để trả primitive.

## 5. `Math.getExponent`: xem trường exponent không bias

Chữ ký:

```java
static int Math.getExponent(double value)
static int Math.getExponent(float value)
```

Giá trị trả về là **`int`**, biểu diễn exponent không bias liên quan đến trường exponent IEEE 754. Đối với số **normal hữu hạn khác zero**, kết quả có thể hiểu như số mũ `e` sao cho độ lớn nằm trong khoảng:

\[
2^e \le |value| < 2^{e+1}
\]

Ví dụ:

```java
Math.getExponent(8.0);   // 3, vì 8 = 1 × 2^3
Math.getExponent(0.75);  // -1, vì 0.75 = 1.5 × 2^-1
Math.getExponent(-16.0); // 4; dấu không làm đổi exponent
```

**Nhưng với zero, subnormal, NaN hoặc vô cực, `getExponent` trả sentinel được API định nghĩa chứ không thực hiện `floor(log2(abs(value)))` theo nghĩa toán học.**

### Bảng kết quả `double`

| Đối số | `Math.getExponent(double)` | Giải thích |
| --- | ---: | --- |
| `8.0` | `3` | Normal, exponent 3 |
| `0.75` | `-1` | Normal, 0.75 = 1.5 × 2^-1 |
| `Double.MIN_NORMAL` | `-1022` | Normal nhỏ nhất |
| `Double.MAX_VALUE` | `1023` | Normal lớn nhất |
| `+0.0` hoặc `-0.0` | `-1023` | `Double.MIN_EXPONENT - 1` |
| `Double.MIN_VALUE` (subnormal) | `-1023` | Cùng sentinel với mọi subnormal |
| `Double.NaN` | `1024` | `Double.MAX_EXPONENT + 1` |
| `±Infinity` | `1024` | Cùng sentinel với NaN |

Với `float`, hai giá trị sentinel tương ứng là:

- Zero và subnormal: **`Float.MIN_EXPONENT - 1 = -127`**.
- NaN và ±Infinity: **`Float.MAX_EXPONENT + 1 = 128`**.

Ví dụ:

```java
Math.getExponent(0.0f);                      // -127
Math.getExponent(Float.MIN_VALUE);           // -127
Math.getExponent(Float.MIN_NORMAL);          // -126
Math.getExponent(Float.POSITIVE_INFINITY);   // 128
```

`Float.MIN_VALUE` có giá trị toán học `2^-149`, nhưng `Math.getExponent(Float.MIN_VALUE)` vẫn trả `-127`. `Double.MIN_VALUE` có giá trị toán học `2^-1074`, nhưng `Math.getExponent(Double.MIN_VALUE)` vẫn trả `-1023`. **Không đọc kết quả sentinel như log2 của subnormal.**

## 6. Vì sao `getExponent` không thay được `log2`?

Trong miền normal, ta có thể dùng kết quả exponent để biết **bậc độ lớn theo cơ số 2**. Nhưng:

- `getExponent(8.0)` là 3, không trả fraction/mantissa.
- `getExponent(12.0)` cũng là 3 vì `12 = 1.5 × 2^3`.
- `getExponent(0.0)` là `-1023`; không phải logarit của zero.
- `getExponent(Double.MIN_VALUE)` là `-1023`; trong khi exponent lũy thừa 2 của nó theo giá trị toán học là `-1074`.
- `getExponent(Double.NaN)` và `getExponent(Double.POSITIVE_INFINITY)` đều 1024; số sentinel không cho biết đối số là NaN hay vô cực.

Nếu cần logarithm thực của một giá trị dương hữu hạn, có thể cân nhắc `Math.log(value) / Math.log(2.0)` (có sai số dấu phẩy động), nhưng **không lấy nó làm phép bit-exact thay thế `getExponent`**. Với giải mã exponent raw và logic xử lý subnormal chuyên sâu, cần hiểu format và xây thuật toán riêng.

### Làm sao phân biệt trường hợp đặc biệt?

```java
double x = Double.MIN_VALUE;
int e = Math.getExponent(x);

if (Double.isNaN(x)) {
    // NaN: không dùng e như exponent của số hữu hạn
} else if (Double.isInfinite(x)) {
    // Infinity
} else if (x == 0.0) {
    // +0.0 hoặc -0.0
} else if (e == Double.MIN_EXPONENT - 1) {
    // x là subnormal khác zero
} else {
    // x là số normal hữu hạn khác zero
}
```

Đây là **đoạn mã minh họa**, cần đặt trong một phương thức/class khi sử dụng. Thứ tự kiểm tra có chủ đích: `e == Double.MIN_EXPONENT - 1` tự nó **không phân biệt** zero và subnormal; `e == Double.MAX_EXPONENT + 1` tự nó **không phân biệt** NaN và vô cực.

Cách kiểm tra trên phù hợp khi thuật toán cần tách trường hợp normal/subnormal. Nhiều ứng dụng thông thường chỉ cần `Double.isFinite`, kiểm tra zero và xử lý ngoại lệ input; không cần thao tác exponent bit-level.

## 7. Phối hợp ba hàm mà không làm sai ý nghĩa

Ba API rất hữu ích khi có thuật toán xử lý **dấu tách biệt**, **scale riêng** và **exponent riêng**.

Ví dụ một pipeline ý tưởng:

```java
double input = -12.0;
int e = Math.getExponent(input);     // 3
double base = Math.scalb(input, -e); // -1.5
double positiveBase = Math.copySign(base, +1.0); // 1.5
```

Đối với số normal hữu hạn khác zero, `base` thường có độ lớn từ `1` đến dưới `2`, vì ta chia giá trị cho `2^e`. `copySign` sau đó chuyển sang độ lớn dương mà vẫn dùng semantics của dấu bit.

**Caveat:** Đừng áp dụng pipeline “chuẩn hóa” trên vào mọi giá trị:

- `input == 0.0`: `getExponent` trả sentinel, không phải exponent hữu ích để chuẩn hóa số 0.
- `input` subnormal: sentinel của `getExponent` không phải exponent thực tương ứng từng giá trị; `scalb(input, -e)` chưa chắc có phần trị chuẩn hóa như mong muốn.
- `input` NaN/Infinity: phải xử lý riêng, nếu không kết quả chuẩn hóa không đại diện số hữu hạn.
- Scale có thể overflow/underflow hoặc giảm độ chính xác với giá trị cực trị.

Nếu thuật toán yêu cầu chuẩn hóa số thực chính xác hơn, hãy xây dựng quy tắc miền và bộ kiểm thử riêng thay vì chỉ ghép ba hàm theo cảm giác.

## 8. `Math` và `StrictMath`: khác biệt thực tế

Cả hai class đều có overload `copySign`, `scalb` và `getExponent` cho `float`/`double` tương ứng. Chúng là **static methods**, không tạo đối tượng `Math` hoặc `StrictMath`.

| Vấn đề | `Math` | `StrictMath` |
| --- | --- | --- |
| `copySign` với dấu hữu hạn (kể cả zero) | Lấy dấu bit từ đối số `sign` | Cùng ngữ nghĩa |
| `copySign` khi **`sign` là NaN** | Được phép không luôn coi NaN là dấu dương | **Bắt buộc coi NaN sign là dương** |
| `scalb` | Nhân với lũy thừa 2 theo đặc tả scale/rounding | Cùng đặc tả số học cho hàm tương ứng |
| `getExponent` | Trả exponent không bias và sentinel | Cùng bảng sentinel |
| Kiểu giá trị trả | `copySign/scalb`: `float` hoặc `double`; `getExponent`: `int` | Tương ứng |

Không suy luận từ tên `StrictMath` rằng mọi hàm trong class này luôn cho giá trị khác `Math`. Trong các trường hợp hữu hạn thông thường, kết quả thường giống nhau. Điểm khác cụ thể, quan trọng của nhóm API bài này là **xử lý đối số dấu NaN của `copySign`** theo cam kết trong tài liệu Java.

Khi cần kết quả dự đoán được ở trường hợp NaN sign theo API, dùng `StrictMath.copySign` và xác định quy tắc xử lý NaN ngay từ thiết kế. Khi các tham số đều hữu hạn phù hợp, `Math` thường là API trực tiếp thuận tiện.

## 9. Chương trình hoàn chỉnh Java 17+ để tự kiểm tra

Tạo tệp `FloatingScaleDemo.java`:

```java
public class FloatingScaleDemo {
    static String hex(double x) {
        return String.format("%016x", Double.doubleToRawLongBits(x));
    }

    public static void main(String[] args) {
        System.out.println("copySign(3.5, -0.0): "
            + Math.copySign(3.5, -0.0));
        System.out.println("copySign(-3.5, +0.0): "
            + Math.copySign(-3.5, +0.0));
        System.out.println("copySign(infinity, -1): "
            + Math.copySign(Double.POSITIVE_INFINITY, -1.0));
        System.out.println("copySign(+0.0, -1.0) raw: "
            + hex(Math.copySign(0.0, -1.0)));
        System.out.println("float copySign: "
            + Math.copySign(2.5f, -0.0f));
        System.out.println("strict copySign: "
            + StrictMath.copySign(3.5, -1.0));

        System.out.println("scalb(1.5,3): " + Math.scalb(1.5, 3));
        System.out.println("scalb(3,-1): " + Math.scalb(3.0, -1));
        System.out.println("scalb zero raw: "
            + hex(Math.scalb(-0.0, 1000)));
        System.out.println("scalb underflow: "
            + Math.scalb(Double.MIN_VALUE, -1));
        System.out.println("scalb negative underflow raw: "
            + hex(Math.scalb(-Double.MIN_VALUE, -1)));
        System.out.println("scalb overflow: "
            + Math.scalb(Double.MAX_VALUE, 1));
        System.out.println("scalb min-normal/2 raw: "
            + hex(Math.scalb(Double.MIN_NORMAL, -1)));
        System.out.println("strict scalb: "
            + StrictMath.scalb(1.5, 3));

        System.out.println("exp(8.0): " + Math.getExponent(8.0));
        System.out.println("exp(0.75): " + Math.getExponent(0.75));
        System.out.println("exp(0.0): " + Math.getExponent(0.0));
        System.out.println("exp(min subnormal): "
            + Math.getExponent(Double.MIN_VALUE));
        System.out.println("exp(min normal): "
            + Math.getExponent(Double.MIN_NORMAL));
        System.out.println("exp(max): "
            + Math.getExponent(Double.MAX_VALUE));
        System.out.println("exp(NaN): "
            + Math.getExponent(Double.NaN));
        System.out.println("exp(infinity): "
            + Math.getExponent(Double.POSITIVE_INFINITY));

        System.out.println("float exp(zero): "
            + Math.getExponent(0.0f));
        System.out.println("float exp(min normal): "
            + Math.getExponent(Float.MIN_NORMAL));
        System.out.println("float exp(infinity): "
            + Math.getExponent(Float.POSITIVE_INFINITY));
        System.out.println("strict getExponent: "
            + StrictMath.getExponent(8.0));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingScaleDemo.java
java FloatingScaleDemo
```

Stdout từ chương trình (đã đối chiếu với JDK):

```text
copySign(3.5, -0.0): -3.5
copySign(-3.5, +0.0): 3.5
copySign(infinity, -1): -Infinity
copySign(+0.0, -1.0) raw: 8000000000000000
float copySign: -2.5
strict copySign: -3.5
scalb(1.5,3): 12.0
scalb(3,-1): 1.5
scalb zero raw: 8000000000000000
scalb underflow: 0.0
scalb negative underflow raw: 8000000000000000
scalb overflow: Infinity
scalb min-normal/2 raw: 0008000000000000
strict scalb: 12.0
exp(8.0): 3
exp(0.75): -1
exp(0.0): -1023
exp(min subnormal): -1023
exp(min normal): -1022
exp(max): 1023
exp(NaN): 1024
exp(infinity): 1024
float exp(zero): -127
float exp(min normal): -126
float exp(infinity): 128
strict getExponent: 3
```

**Hướng dẫn đọc output:**

- Sáu dòng `copySign` cho thấy dấu của zero âm có ý nghĩa, kể cả khi giá trị so sánh bằng zero dương. `StrictMath` với đối số bình thường trả cùng kết quả.
- Tám dòng `scalb` chứng minh tăng/giảm lũy thừa của 2, dấu zero, overflow thành infinity và underflow thành zero/subnormal.
- Các dòng `getExponent` phân biệt normal với các sentinel: `double` zero/subnormal là `-1023`, NaN/infinity là `1024`; `float` tương ứng `-127` và `128`.
- Hàm `hex` dùng **mẫu bit raw** để phân biệt zero âm/dương; số hiển thị dạng hexa đã pad đủ 16 ký tự cho `double`.

## 10. Khi nào nên dùng ba API này?

| Nhu cầu | API nên chọn | Lưu ý |
| --- | --- | --- |
| Gắn dấu từ một giá trị tham chiếu | `Math.copySign` | Nhận cả dấu của `-0.0` |
| Yêu cầu NaN sign luôn được coi dương | `StrictMath.copySign` | Khác cam kết `Math.copySign` với NaN sign |
| Nhân/chia theo lũy thừa 2 | `Math.scalb` | Theo dõi overflow/underflow |
| Điều chỉnh scale cho số `float` | `Math.scalb(float, int)` | Kết quả vẫn là `float` |
| Quan sát bậc exponent của số normal | `Math.getExponent` | Không phải logarit tổng quát |
| Phân loại zero/subnormal/special | `getExponent` kết hợp `isNaN`/`isInfinite`/kiểm tra zero | Sentinel một mình chưa đủ |
| Xem mẫu bit thật | `Double.doubleToRawLongBits`/`Float.floatToRawIntBits` | Không nhầm API quan sát bit với numeric scaling |
| Kiểm tra giá trị hữu hạn | `Double.isFinite`/`Float.isFinite` | Phải dùng rõ ràng nếu hợp đồng yêu cầu |

Ví dụ xử lý dữ liệu khoa học, biểu diễn số trong thuật toán chuẩn hóa, giải mã tín hiệu hoặc thống kê có miền độ lớn trải rộng thường cần các hàm trên. Nhưng đừng dùng chúng chỉ vì tưởng rằng hàm cấp thấp luôn “chính xác hơn”: **lựa chọn bắt nguồn từ thao tác mong muốn và giới hạn biểu diễn**.

## 11. Các nhầm lẫn thường gặp

1. **“`copySign(x, y)` tính `x * y`.”** Sai: nó lấy độ lớn của `x` và dấu của `y`.
2. **“`copySign` với `-0.0` giống với dấu dương vì `-0.0 == 0.0`.”** Sai: bit dấu được giữ và được áp dụng.
3. **“`Math.copySign` và `StrictMath.copySign` có cam kết hoàn toàn giống nhau với NaN sign.”** Sai: `StrictMath` coi NaN sign là dương; `Math` được phép khác.
4. **“`scalb(x, n)` là `Math.pow(x, n)`.”** Sai: `scalb` tính `x × 2^n`.
5. **“`scalb` luôn chính xác đến mọi bit, kể cả subnormal.”** Sai: underflow/rounding có thể mất độ chính xác hoặc tạo zero.
6. **“Tính `x * Math.pow(2,n)` luôn an toàn tương đương `scalb`.”** Sai: biểu thức có kết quả trung gian có thể overflow/underflow trước khi nhân.
7. **“`getExponent(x)` luôn trả `floor(log2(abs(x)))`.”** Sai với zero, subnormal, NaN và Infinity.
8. **“`Math.getExponent(Double.MIN_VALUE) == -1074`.”** Sai: API trả `-1023` cho mọi subnormal `double`.
9. **“`Math.getExponent(0.0)` và `Math.getExponent(Double.MIN_VALUE)` khác nhau.”** Sai: đều `-1023`.
10. **“`getExponent` trả `Double.MIN_EXPONENT` khi zero.”** Sai: trả `Double.MIN_EXPONENT - 1`.
11. **“`getExponent` trả NaN nếu đầu vào là NaN.”** Sai: return type là `int` và trả sentinel `MAX_EXPONENT + 1`.
12. **“`getExponent` phân biệt NaN với vô cực chỉ từ kết quả.”** Sai: chúng có cùng sentinel.
13. **“`Double.MIN_VALUE` là giá trị âm nhỏ nhất của `double`.”** Sai: đó là số dương subnormal nhỏ nhất.
14. **“`scalb(-0.0, k)` trở thành `+0.0` vì nhân với lũy thừa 2 dương.”** Sai: dấu zero được giữ.
15. **“`copySign` có thể thay mọi thao tác chuyển dấu của NaN mà vẫn bảo toàn payload.”** Sai: không coi đây là giao thức bit-perfect cho mọi dạng NaN.

## 12. Tự kiểm tra

**1. `Math.copySign(5.0, -0.0)` trả gì?** → `-5.0`, vì `-0.0` mang dấu âm.

**2. `Math.copySign(-5.0, +0.0)` trả gì?** → `5.0`, vì độ lớn 5 mang dấu dương mới.

**3. `Math.scalb(1.25, 3)` trả gì?** → `10.0`, do `1.25 × 2^3`.

**4. `Math.scalb(3.0, -1)` trả gì?** → `1.5`.

**5. `Math.scalb(Double.MAX_VALUE, 1)` trả gì?** → `Infinity` vì overflow.

**6. `Math.scalb(-Double.MIN_VALUE, -1)` có dấu gì?** → `-0.0`; bit dấu âm được bảo toàn.

**7. `Math.getExponent(0.75)` là bao nhiêu?** → `-1`.

**8. `Math.getExponent(Double.MIN_NORMAL)` và `Math.getExponent(Double.MIN_VALUE)` có bằng nhau không?** → Không: lần lượt `-1022` và `-1023`.

**9. `Math.getExponent(Double.NaN)` so với `Math.getExponent(Double.POSITIVE_INFINITY)`?** → Đều `1024`, nên phải dùng API phân loại riêng nếu cần phân biệt.

**10. `Math.getExponent(Float.MIN_VALUE)` có bằng `-149` không?** → Không: trả `-127` sentinel cho subnormal.

**11. `Math.copySign` và `StrictMath.copySign` có gì khác nếu sign là NaN?** → `StrictMath` luôn coi NaN sign như dấu dương; `Math` được phép xử lý dấu một số NaN khác nhau theo triển khai.

**Ghi nhớ:** `copySign` làm việc với **dấu bit**; `scalb` thay đổi **thang lũy thừa 2** với quy tắc làm tròn/overflow/underflow; `getExponent` đọc **số mũ không bias** của biểu diễn và trả sentinel cho **zero, subnormal, NaN, infinity**. Khi dùng trong thuật toán, hãy phân loại giá trị đặc biệt, giữ đúng kiểu `float`/`double` và không nhầm kết quả bit-level với logarit hoặc số học chính xác vô hạn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-010">Phân biệt yêu cầu tái lập của Math và StrictMath</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt yêu cầu tái lập của Math và StrictMath trong Java

Java có hai lớp tiện ích toán học trong `java.lang`: `Math` và `StrictMath`. Cả hai cung cấp nhiều hàm tương ứng như `sin`, `cos`, `log`, `exp`, `pow`, `sqrt` và `hypot`. Nhưng khi yêu cầu **tái lập kết quả giữa các nền tảng** (*reproducibility*), không thể kết luận rằng hai lớp có hợp đồng giống hệt nhau.

**Điểm khác biệt chính:** Với một số hàm số thực, `Math` cho phép những triển khai khác nhau cho ra các giá trị `double` *khác nhau ở bit thấp* dù vẫn đạt giới hạn sai số và tính chất được đặc tả. Đối với **nhóm hàm `StrictMath` được ràng buộc với thuật toán `fdlibm`**, Java quy định cách tính tham chiếu để giúp tạo **cùng kết quả bit-for-bit cho cùng đầu vào** trên các triển khai Java phù hợp.

Đây là chủ đề thuộc **Math và StrictMath** của Java Core. Bài học tách bạch “cùng công thức”, “đủ gần về số học”, “cùng bit”, “cùng kết quả toàn chương trình”, giải thích bối cảnh Java 17+ và hướng dẫn cách chọn API phù hợp.

## 1. Ba mức độ “tái lập” cần phân biệt

| Mức yêu cầu | Ý nghĩa | Cách đánh giá |
| --- | --- | --- |
| Tính đúng gần đúng (*numerical accuracy*) | Giá trị tính được ở trong giới hạn sai số cho phép của hàm | So sánh với chuẩn toán học và giới hạn `ulp`/dung sai |
| Tái lập bit một hàm (*bitwise function reproducibility*) | Cùng **đối số có cùng bit** thì kết quả trả về có cùng biểu diễn bit theo đặc tả | So sánh `Double.doubleToRawLongBits` hoặc `doubleToLongBits` theo mục tiêu |
| Tái lập toàn chương trình | Cùng dữ liệu và hợp đồng thực thi thì toàn bộ phép tính/chuỗi đầu ra nhất quán | Phải kiểm soát thuật toán, thứ tự phép toán, dữ liệu, luồng, định dạng và phiên bản/phụ thuộc |

Một phương thức **chính xác trong 1 ulp** vẫn có thể có **hai đáp án bit khác nhau** nếu đặc tả cho phép hai giá trị kề nhau quanh kết quả toán học. Vì vậy “chính xác cao” không tự suy ra “tái lập từng bit”.

Ngược lại, kết quả lặp lại giống hệt bit giữa các lần chạy không tự chứng minh nó đúng về mặt nghiệp vụ. Một thuật toán có thể nhất quán mà vẫn dùng công thức không ổn định, sai kiểu dữ liệu hoặc không phản ánh yêu cầu thực tế.

## 2. `Math`: ưu tiên tính đúng theo đặc tả, cho phép tối ưu triển khai

Theo tài liệu API, một số hàm tương ứng trong `Math` **không bắt buộc mọi triển khai cho kết quả bit-for-bit như nhau**. Điều này cho phép JVM tận dụng thư viện nền tảng hoặc lệnh CPU hiệu quả hơn, miễn kết quả vẫn tuân thủ hợp đồng của từng hàm.

Ví dụ `Math.sin(double)` yêu cầu kết quả có sai số trong **1 ulp** và thỏa tính *semi-monotonic*. Các hàm khác có thể có giới hạn 1 ulp, 2 ulp, hoặc đòi **làm tròn đúng** tùy phương thức; không được áp một con số cho mọi thành viên của `Math`.

Khái niệm `ulp` (*unit in the last place*) nói về khoảng cách giữa các giá trị dấu phẩy động có thể biểu diễn gần một giá trị được xét. Chẳng hạn:

~~~java
System.out.println(Math.ulp(1.0));
// 2.220446049250313E-16
~~~

Khoảng cách này phụ thuộc độ lớn của số; không tương đương một epsilon thập phân cố định cho toàn bộ miền `double`.

**Cần hiểu đúng “được phép khác”:**

- Không có nghĩa `Math` luôn khác `StrictMath`.
- Không có nghĩa một JVM luôn chọn hàm xấp xỉ “kém chính xác” hơn.
- Không có nghĩa `Math.sin(x)` được trả giá trị bất kỳ; vẫn có yêu cầu độ chính xác và tính chất của hàm.
- Không bảo đảm sử dụng `Math` luôn nhanh hơn `StrictMath`: hiệu năng thực phụ thuộc JDK/JIT/CPU, hàm được gọi và workload.

Nhiều triển khai thực tế có thể gọi trực tiếp `StrictMath` cho một số hàm hoặc tạo kết quả giống nhau tại bộ dữ liệu kiểm thử. **Một thử nghiệm cho ra kết quả bằng nhau trên máy hiện tại không nâng cấp hợp đồng API thành bảo đảm xuyên nền tảng.**

## 3. `StrictMath`: nhóm hàm được xác định bằng `fdlibm`

`StrictMath` được thiết kế để hỗ trợ khả năng di động/tái lập. Java quy định các hàm toán học nhất định sử dụng ngữ nghĩa tương ứng với **`fdlibm` (Freely Distributable Math Library) phiên bản 5.3**, hiểu như thuật toán được chuyển sang Java và thực thi theo quy tắc số học Java.

Theo API Java SE 21, nhóm hàm ràng buộc thuật toán `fdlibm` gồm:

- Lượng giác và nghịch đảo: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`.
- Mũ/logarit: `exp`, `log`, `log10`, `expm1`, `log1p`, `pow`.
- Hyperbolic: `sinh`, `cosh`, `tanh`.
- Các hàm khác trong nhóm: `cbrt`, `hypot`.

Với **cùng bit của đầu vào** và một triển khai tuân thủ đặc tả áp dụng, các hàm này có quy tắc tính toán tham chiếu, nhờ đó thích hợp hơn khi bạn cần kết quả **bit-for-bit ổn định giữa các nền tảng Java**.

**Không nên nói toàn bộ mọi phương thức của `StrictMath` đều sử dụng `fdlibm`**. Những thành viên như `abs`, `min`, `max`, `nextUp`, `copySign`, `sqrt` hoặc các phương thức số nguyên có hợp đồng riêng; một số có tính đúng chính xác/correctly rounded hoặc quy tắc bit rõ ràng mà không cần thuật toán `fdlibm`. Phải đọc tài liệu từng hàm khi yêu cầu tái lập chặt chẽ.

Cũng không nên nói `StrictMath` trả “giá trị toán học chính xác tuyệt đối” cho `sin` hay `log`. Kết quả vẫn là giá trị `double` xấp xỉ. Điều nó bổ sung là **một quy tắc tính toán được chỉ định**, không biến binary64 thành số thực có độ chính xác vô hạn.

## 4. So sánh trực tiếp hai lớp

| Câu hỏi | `Math` | `StrictMath` |
| --- | --- | --- |
| Có các hàm lượng giác/mũ/log tương ứng? | Có | Có |
| Hàm trả số thực được biểu diễn bằng `double`? | Có | Có |
| Phải tuân thủ quy tắc NaN, vô cực, zero có dấu theo từng hàm? | Có | Có |
| Mọi hàm tương ứng bắt buộc cùng bit trên mọi JVM? | **Không**; một số hàm cho phép tự do triển khai trong giới hạn đặc tả | **Nhóm hàm `fdlibm` có thuật toán tham chiếu** để tái lập |
| Có thể cho cùng kết quả bit ở JVM đang dùng? | Có thể | Có thể |
| Luôn chạy nhanh hơn lớp kia? | Không có bảo đảm | Không có bảo đảm |
| Chỉ cần chọn lớp là đủ bảo đảm tái lập *toàn bộ chương trình*? | Không | Không |

Ví dụ:

~~~java
double x = 0.7;
double a = Math.sin(x);
double b = StrictMath.sin(x);
~~~

Trên một số JVM, `a` và `b` có thể bằng nhau ở bit; trên triển khai khác, `Math.sin` có thể trả đáp án trong giới hạn `ulp` của nó nhưng khác bit thấp so với `StrictMath.sin`. **Không nên viết kiểm thử portable buộc `a == b` cho mọi x và mọi JVM** chỉ dựa trên tên hai hàm.

Nếu cần kiểm thử giao thức bitwise dựa trên `sin`, dùng `StrictMath.sin` và cố định các đầu vào (kể cả bit của `double`), không suy luận từ một tập benchmark có kết quả trùng hợp.

## 5. Java 17+ và JEP 306: `strictfp` không đồng nghĩa `StrictMath`

Từ **Java 17**, **JEP 306 — Restore Always-Strict Floating-Point Semantics** đưa ngôn ngữ và JVM về ngữ nghĩa dấu phẩy động luôn nghiêm ngặt (*always-strict*). Các phép toán primitive như cộng, trừ, nhân, chia trên `float`/`double` đều sử dụng ngữ nghĩa dấu phẩy động nghiêm ngặt; từ khóa `strictfp` không còn thay đổi cách đánh giá các biểu thức này trên Java 17+.

Điều này **không xóa khác biệt về hợp đồng thư viện**:

- `strictfp` (keyword) liên quan đến ngữ nghĩa thực thi biểu thức dấu phẩy động của mã Java; trên Java 17+ ngữ nghĩa ấy luôn strict.
- `StrictMath` là một **lớp API** với những hàm có yêu cầu thuật toán `fdlibm` đặc thù.
- `Math` vẫn có một số hàm được phép tối ưu theo nền tảng trong giới hạn sai số cho phép.

Nói cách khác, mã chạy trên Java 17+ **không tự biến lời gọi `Math.sin` thành lời gọi `StrictMath.sin`** chỉ vì phép toán dấu phẩy động đã always-strict. Đừng dùng từ khóa `strictfp` như giải pháp thay thế cho việc chọn đúng API hoặc thiết kế pipeline tái lập.

## 6. “Cùng đầu vào” là cùng *bit*, không chỉ chuỗi trông giống nhau

Kết quả phụ thuộc chính xác vào các bit của đối số. `0.7` trong source Java được làm tròn thành một giá trị binary64. Khi kiểm thử tái lập, nếu một hệ thống nhập chuỗi theo cách khác, ép từ `float` lên `double` hoặc làm tròn trước khi gọi hàm, **bit đầu vào** có thể khác nhau.

~~~java
double fromDouble = 0.7;
double fromFloat = (double) 0.7f;
boolean equalBits = Double.doubleToLongBits(fromDouble)
        == Double.doubleToLongBits(fromFloat); // false
~~~

Mặc dù khi hiển thị với `%.1f` cả hai có thể đều trông là `0.7`, hai giá trị biểu diễn bit khác nhau. `StrictMath.sin` không có trách nhiệm khiến **hai đối số khác bit** tạo cùng kết quả.

Khi cần lưu dữ liệu để tái lập một phép tính, hãy xác định:

1. Đầu vào được lưu như `double` binary64, `float` binary32 hay decimal.
2. Format trao đổi có đảm bảo round-trip hay không, ví dụ `Double.toString` + `Double.parseDouble`.
3. Có chuyển kiểu, thay đổi đơn vị, chuẩn hóa hay làm tròn trước phép tính không.
4. Có trạng thái NaN/vô cực/số 0 có dấu và chính sách xử lý bit đặc biệt không.

`Double.doubleToRawLongBits` giữ raw bit của giá trị để chẩn đoán. `Double.doubleToLongBits` chuẩn hóa các NaN thành một biểu diễn canonical khi cần so sánh số thực theo quy ước Java; lựa chọn API kiểm bit phụ thuộc mục tiêu. Với zero có dấu, cả hai có thể phân biệt `+0.0` và `-0.0`.

## 7. Ví dụ Java 17+ hoàn chỉnh: kết quả cố định và kiểm tra bit

Tạo tệp `ReproducibleMathDemo.java`:

~~~java
public class ReproducibleMathDemo {
    static String bits(double x) {
        return "0x" + Long.toHexString(Double.doubleToRawLongBits(x));
    }

    public static void main(String[] args) {
        double angle = 0.7;
        double a = StrictMath.sin(angle);
        double b = StrictMath.sin(angle);

        System.out.println("strict sin hex = "
                + Double.toHexString(a));
        System.out.println("strict sin bits = " + bits(a));
        System.out.println("repeat strict identical = "
                + (Double.doubleToLongBits(a)
                == Double.doubleToLongBits(b)));
        System.out.println("strict sin(-0) bits = "
                + bits(StrictMath.sin(-0.0)));
        System.out.println("strict log(1) = "
                + StrictMath.log(1.0));
        System.out.println("strict log(-1) is NaN = "
                + Double.isNaN(StrictMath.log(-1.0)));
        System.out.println("strict sqrt(4) = "
                + StrictMath.sqrt(4.0));
        System.out.println("math sqrt(4) = "
                + Math.sqrt(4.0));
        System.out.println("ulp(1) = " + Math.ulp(1.0));
        System.out.println("nextUp(1) = " + StrictMath.nextUp(1.0));

        double x = 1.0e16, y = -1.0e16, z = 1.0;
        System.out.println("left grouped = " + ((x + y) + z));
        System.out.println("right grouped = " + (x + (y + z)));

        System.out.println("strict abs(minInt) = "
                + StrictMath.abs(Integer.MIN_VALUE));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("addExact overflow caught");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ReproducibleMathDemo.java
java ReproducibleMathDemo
~~~

Stdout đã xác minh với Java:

~~~text
strict sin hex = 0x1.49d6e694619b8p-1
strict sin bits = 0x3fe49d6e694619b8
repeat strict identical = true
strict sin(-0) bits = 0x8000000000000000
strict log(1) = 0.0
strict log(-1) is NaN = true
strict sqrt(4) = 2.0
math sqrt(4) = 2.0
ulp(1) = 2.220446049250313E-16
nextUp(1) = 1.0000000000000002
left grouped = 1.0
right grouped = 0.0
strict abs(minInt) = -2147483648
addExact overflow caught
~~~

Đọc kết quả theo đúng mục tiêu:

- Hai dòng `strict sin` cho phép quan sát cả biểu diễn hexadecimal của `double` và **bit thô** của kết quả `StrictMath.sin(0.7)` theo ngữ nghĩa `fdlibm`.
- `repeat strict identical = true` xác nhận hai lời gọi cùng đầu vào trong chương trình cho cùng bit; **chỉ test một máy không chứng minh mọi triển khai đều như vậy**, bảo đảm portable đến từ đặc tả `StrictMath`.
- `sin(-0.0)` giữ dấu âm của 0, thể hiện ở bit dấu của binary64.
- `log(-1.0)` trả NaN và `sqrt(4.0)` trả 2.0 theo hợp đồng hàm; hai kết quả dễ quan sát không có nghĩa mọi đầu vào `Math` và `StrictMath` sẽ cho cùng bit.
- Hai dòng `left grouped` và `right grouped` minh họa **thứ tự phép cộng khác nhau có thể cho kết quả khác nhau**, dù cùng dùng phép toán Java 17+ strict.
- `abs(Integer.MIN_VALUE)` vẫn ra `Integer.MIN_VALUE` vì miền `int` không chứa giá trị dương tương ứng. `addExact` ném khi cộng tràn miền, không liên quan “tái lập” của hàm lượng giác.

**Lưu ý:** các hàm được bảo đảm tái lập là những hàm mà hợp đồng API ràng buộc như `fdlibm`. Không lấy vài dòng output đơn giản của các hàm khác làm bằng chứng rằng toàn bộ API đều được quy định bằng cùng thuật toán.

## 8. Thứ tự phép tính vẫn có thể phá tái lập toàn chương trình

Ngay cả khi **từng lời gọi `StrictMath.sin`** cho cùng bit với cùng đầu vào, kết quả chương trình còn phụ thuộc cách các phép tính kết hợp.

Ví dụ:

~~~java
double x = 1.0e16;
double y = -1.0e16;
double z = 1.0;

double left = (x + y) + z;  // 1.0
double right = x + (y + z); // 0.0
~~~

Phép cộng dấu phẩy động **không có tính kết hợp** theo cách số thực toán học lý tưởng: thay ngoặc đã đổi chỗ làm tròn. Java 17 always-strict quy định cách tính biểu thức, nhưng **không khiến hai biểu thức có thứ tự khác nhau tương đương**.

Đối với pipeline song song, kết quả `reduce`/`sum` có thể chịu ảnh hưởng nếu thứ tự cộng thay đổi do chia partition hoặc thứ tự duyệt. Với thuật toán cần tái lập bitwise, cần kiểm soát thứ tự giảm, cách phân nhóm và chiến lược tích lũy, hoặc sử dụng một thuật toán tổng ổn định phù hợp.

Tương tự, thay `a * b + c` bằng `Math.fma(a,b,c)` thay đổi quy tắc làm tròn: `fma` thực hiện nhân rồi cộng với một lần làm tròn ở kết quả cuối theo đặc tả, có thể khác việc làm hai phép riêng. Đừng coi việc refactor sang một công thức “tương đương toán học” là bảo đảm **cùng bit**.

**Yêu cầu tái lập toàn bộ** còn bao gồm nguồn random, seed, dữ liệu sắp xếp, xử lý null/NaN, chuyển đổi kiểu, locale khi xuất chuỗi, phụ thuộc phiên bản và khả năng thay đổi thuật toán.

## 9. NaN, ±Infinity, ±0 và phép kiểm bit

Các giá trị dấu phẩy động đặc biệt làm nổi bật sự khác nhau giữa so sánh toán học và so sánh bit:

~~~java
double negativeZero = -0.0;

boolean numericallyEqual = (negativeZero == 0.0); // true
boolean sameBits = Double.doubleToRawLongBits(negativeZero)
        == Double.doubleToRawLongBits(+0.0);      // false

boolean nanEquality = (Double.NaN == Double.NaN); // false
boolean nanDetected = Double.isNaN(Double.NaN);  // true
~~~

Khi đối số âm 0, một số hàm giữ dấu theo hợp đồng, ví dụ `StrictMath.sin(-0.0)` trả âm 0. Với phép xử lý NaN, raw payload có thể khác, và `Double.doubleToLongBits` chuẩn hóa NaN; cần quyết định “bitwise identity” có bao gồm payload NaN hay không.

Kết quả `StrictMath` cũng có thể là NaN hoặc vô cực theo miền của hàm; strict reproducibility **không bảo đảm mọi phép tính cho số hữu hạn**. Dữ liệu không hữu hạn cần được validation theo ngữ nghĩa ứng dụng. Có thể dùng `Double.isFinite` khi ràng buộc cần số hữu hạn.

Nếu kết quả được serialize sang JSON hoặc hệ thống khác không hỗ trợ NaN/vô cực ở dạng số thông thường, phải chọn quy ước truyền tải riêng. Đây là vấn đề hợp đồng dữ liệu, không được `StrictMath` tự giải quyết.

## 10. Khi nào nên chọn `Math`, khi nào chọn `StrictMath`?

**Ưu tiên `Math`** khi cần hàm toán học đúng theo yêu cầu API, chú trọng hiệu năng/thông lượng nhưng **không đòi bit-for-bit reproducibility** với nhóm hàm lượng giác/mũ/log giữa các nền tảng. Tính toán UI, đồ họa, nhiều bài toán mô phỏng hay thống kê chấp nhận sai số được xác định thường phù hợp, sau khi đánh giá yêu cầu riêng.

**Cân nhắc `StrictMath`** khi bài toán cần so khớp bitwise đối với những hàm được ràng buộc theo `fdlibm`: tái lập kết quả trong kiểm thử liên nền tảng, trao đổi kết quả số học giữa các JVM tuân thủ cùng đặc tả, hoặc một thành phần thuật toán mà bit kết quả đóng vai trò dữ liệu đầu ra.

Tuy vậy, không tự đồng nhất `StrictMath` với:

- **“Luôn chậm hơn.”** Tốc độ phụ thuộc hàm và triển khai; hãy benchmark workload thực tế.
- **“Chính xác tuyệt đối hơn `Math`.”** Nó đảm bảo thuật toán tham chiếu cho một tập hàm, không biến `double` thành phép toán vô hạn precision.
- **“Tất cả method StrictMath đều fdlibm.”** Chỉ có tập hàm được liệt kê/định nghĩa trong tài liệu là ràng buộc tương ứng.
- **“Cùng source code ở hai máy chắc chắn cho toàn bộ ứng dụng cùng bit.”** Dữ liệu, thứ tự phép tính, thuật toán song song và các thành phần khác vẫn có thể khác.

Một lựa chọn hợp lý là dùng `Math` cho nhu cầu thông thường, xác định **điểm nào thực sự có yêu cầu tái lập chặt chẽ**, và chọn `StrictMath` cho các lời gọi phù hợp tại những điểm đó. Việc chọn phải dựa trên hợp đồng thực tế của hàm, không trên tên “Strict” một cách tuyệt đối.

## 11. Kiểm thử và chẩn đoán khi cần bit-for-bit

### Kiểm thử hai giá trị số thực

~~~java
static boolean sameCanonicalBits(double a, double b) {
    return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
}
~~~

Đây là **phương thức minh họa**, cần đặt trong class; nó dùng biểu diễn **canonical** với NaN. Nếu yêu cầu phân biệt payload, sử dụng `Double.doubleToRawLongBits`. Nếu chỉ kiểm tra giá trị **gần đúng** theo sai số, bitwise comparison là không phù hợp: hai số cách nhau 1 ulp sẽ bị coi là khác nhau.

### Kiểm thử “đúng gần đủ”

Nếu kết quả không đòi tái lập tuyệt đối mà chỉ cần sai số trong một mức cho phép, hãy xác định tolerance dựa trên miền giá trị, độ lớn và tính chất của hàm. Đối với những hàm `Math` có quy định sai số tính bằng `ulp`, thiết kế kiểm tra phù hợp với hợp đồng từng hàm, không áp một epsilon cố định cho mọi hàm và mọi giá trị.

### Lưu dấu vết kiểm thử tái lập

Nên lưu rõ phiên bản Java/API áp dụng, kiến trúc, bit hoặc hexadecimal của đầu vào/đầu ra, thuật toán và seed nếu có ngẫu nhiên. Nếu kiểm chứng xuyên các môi trường, chạy cùng bộ vector đầu vào và so sánh theo yêu cầu bài toán:

1. **Bitwise:** so sánh hex/bit, xử lý NaN và zero có dấu có chủ đích.
2. **Numerical tolerance:** so sánh theo sai số với NaN/vô cực được xử lý riêng.
3. **Nghiệp vụ:** kiểm tra kết quả được phép/không được phép (ví dụ ngưỡng, đơn vị, mô hình dữ liệu).

Đừng trộn ba kiểu assertion vào một bài test mà không ghi rõ tiêu chí thành công.

## 12. Các sai lầm hay gặp

1. **Nghĩ Java 17 always-strict khiến mọi `Math.sin`, `Math.log` bắt buộc cùng bit trên mọi nền tảng.** Sai: ngữ nghĩa biểu thức primitive và hợp đồng các hàm thư viện là hai tầng khác nhau.
2. **Nghĩ `strictfp` và `StrictMath` là cùng cơ chế.** Sai: một cái là modifier ngôn ngữ, một cái là class API.
3. **Nghĩ tất cả phương thức `StrictMath` được hiện thực trực tiếp bằng `fdlibm`.** Sai: tài liệu xác định danh sách cụ thể.
4. **Coi kết quả `Math.sin(x) == StrictMath.sin(x)` trên một JVM là bằng chứng portable.** Sai: kết quả thực tế trùng không thay thế bảo đảm trong tài liệu.
5. **Cho rằng `StrictMath` luôn chính xác hơn `Math`.** Mục tiêu quan trọng là tái lập thuật toán cho tập hàm tương ứng, không phải lời hứa “vô hạn độ chính xác”.
6. **Áp giới hạn 1 ulp cho mọi hàm của `Math`.** Mỗi method có yêu cầu riêng, có thể 0.5, 1, 2 ulp hoặc chính xác theo bit.
7. **Chỉ kiểm soát một hàm mà quên thứ tự cộng/reduction.** Nhóm phép tính khác nhau có thể dẫn tới kết quả bit khác nhau.
8. **Bỏ qua bit đầu vào, signed zero và NaN.** Hai chuỗi in giống nhau không nhất thiết tạo cùng binary64; hai zero bằng nhau theo `==` nhưng khác bit.
9. **Sửa biểu thức sang `Math.fma`, đổi cách tính hoặc thay cấu trúc pipeline mà không kiểm tra tính tái lập.** Số lần và vị trí làm tròn có thể đổi.
10. **Không đo hiệu năng trước khi khẳng định lớp nào nhanh.** JIT, CPU, thuật toán và phân bố đầu vào quyết định hiệu năng thực.
11. **Dùng `Double.toString`/format ít chữ số để chứng minh bitwise equality.** Một bản in có thể che sai lệch; dùng bit hoặc hex khi cần.
12. **Tái lập bitwise mà quên dữ liệu nghiệp vụ phải hợp lý.** Kết quả ổn định nhưng sai công thức/đơn vị vẫn là lỗi.

## 13. Câu hỏi tự kiểm tra

**1.** Vì sao `Math.sin(x)` có thể khác `StrictMath.sin(x)` ở bit thấp?

**Đáp án:** `Math.sin` có hợp đồng sai số/tính chất cho phép lựa chọn triển khai trong giới hạn; `StrictMath.sin` dùng ngữ nghĩa thuật toán `fdlibm` được chỉ định nhằm tái lập.

**2.** Java 17 always-strict có đồng nghĩa mọi method trong `Math` phải trả cùng bit trên mọi CPU?

**Đáp án:** Không. JEP 306 điều chỉnh ngữ nghĩa biểu thức dấu phẩy động của ngôn ngữ/JVM; không xóa các khoảng tự do triển khai được mô tả trong API `Math`.

**3.** `StrictMath.sin` có chính xác toán học tuyệt đối vì tên “Strict” không?

**Đáp án:** Không. Kết quả vẫn là `double` xấp xỉ; tính nổi bật là tuân theo thuật toán tham chiếu nhằm tái lập giữa nền tảng.

**4.** Vì sao `(1e16 + -1e16) + 1` khác `1e16 + (-1e16 + 1)`?

**Đáp án:** Phép cộng binary64 có thể làm tròn ở từng bước và không có tính kết hợp; hai cách nhóm phép toán cho `1.0` và `0.0`.

**5.** Khi nào dùng `Double.doubleToRawLongBits` thay cho so sánh `==`?

**Đáp án:** Khi tiêu chí là mẫu bit, kể cả phân biệt `-0.0` với `+0.0` và các NaN payload nếu còn được giữ. `==` thể hiện equality số học, không phải đồng nhất bit.

**6.** Một chương trình dùng `StrictMath.log` và `StrictMath.sin` đã đủ để có kết quả toàn bộ tái lập chưa?

**Đáp án:** Chưa. Vẫn cần kiểm soát bit đầu vào, thứ tự tính, các phép chuyển kiểu, dữ liệu đầu ra, ngẫu nhiên, song song và các bộ phận khác của pipeline.

## Tài liệu tham khảo

- [Java SE 21 — StrictMath](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StrictMath.html): danh sách phương thức ràng buộc `fdlibm` và quy tắc đặc biệt từng phương thức.
- [Java SE 21 — Math](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html): độ chính xác tính theo `ulp`, semi-monotonic và quyền lựa chọn triển khai.
- [JEP 306 — Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306): thay đổi ngữ nghĩa strict của Java 17+.
- [Java SE 21 — Double](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html): `toHexString`, `doubleToRawLongBits`, `doubleToLongBits` và xử lý NaN/zero.
- [Java Language Specification `15.4 và `4.2.3](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.4): biểu thức dấu phẩy động và quy tắc đánh giá.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="math-and-strict-math-011">Miền xác định, NaN, vô cực và độ chính xác được Javadoc quy định</a>

<details>
<summary>Xem chi tiết</summary>

# Miền xác định, NaN, vô cực và độ chính xác được Javadoc quy định: `Math` và `StrictMath`

Khi gọi `Math.sqrt`, `Math.log`, `Math.pow`, `Math.hypot` hay `StrictMath.exp`, không đủ để biết công thức toán học. Một chương trình Java còn phải biết **đầu vào nào hợp lệ, điều gì xảy ra với các giá trị đặc biệt IEEE 754, giới hạn sai số của kết quả, và tính tái lập mà API thực sự cam kết**.

Một ví dụ ngắn:

```java
System.out.println(Math.sqrt(-1.0)); // NaN
System.out.println(Math.log(0.0));   // -Infinity
System.out.println(Math.pow(Double.NaN, 0.0)); // 1.0
```

Cả ba kết quả trên đều có ý nghĩa theo **hợp đồng của từng phương thức**, không thể suy luận bằng một quy tắc chung “đầu vào bất thường thì luôn ném exception” hoặc “có NaN ở đâu thì kết quả chắc chắn NaN”.

Bài học dùng **Java 17+**, tập trung vào cách **đọc và áp dụng Javadoc**. Khi triển khai với phiên bản Java khác, kiểm tra đúng tài liệu của phiên bản đang chạy. Nguồn chuẩn để tra cứu:

- [Javadoc Java 17 — java.lang.Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html)
- [Javadoc Java 17 — java.lang.StrictMath](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html)

## 1. “Miền xác định” trong toán học và trong `double`

Toán học thực cho hàm \(\sqrt{x}\) miền \(x\geq 0\), hàm \(\ln(x)\) miền \(x>0\), và \(\arcsin(x)\) miền \(-1\leq x\leq1\). Nhưng giá trị `double` có thêm:

- `Double.NaN`: kết quả không phải số theo ngữ nghĩa IEEE 754.
- `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`: vô cực có dấu.
- `+0.0` và `-0.0`: zero có bit dấu khác nhau, dù `+0.0 == -0.0` là `true`.
- Giá trị **subnormal** rất gần 0; overflow hoặc underflow có thể làm kết quả thành vô cực hoặc zero.

**Miền thực thông thường** chỉ là bước đầu để đọc tài liệu. Javadoc còn mô tả **special cases**: khi nhận NaN, vô cực, zero có dấu, số ngoài miền, hoặc các kết hợp đặc biệt giữa hai/ba tham số, phương thức cụ thể phải trả gì.

Ví dụ, với `sqrt`:

```java
double a = Math.sqrt(4.0); // 2.0
double b = Math.sqrt(-1.0); // NaN
double c = Math.sqrt(-0.0); // -0.0
```

`sqrt(-0.0)` **không** được coi như căn bậc hai của một số âm không hợp lệ: Javadoc ghi rõ phải trả **zero với cùng dấu**. Ngược lại `sqrt(-1.0)` trả NaN.

Không đồng nhất “NaN” với “exception”. Trong tính toán dấu phẩy động, nhiều phương thức thông báo trạng thái qua chính **giá trị trả về**.

## 2. Vì sao không thể dùng một bảng quy tắc NaN chung cho mọi hàm?

Một số hàm lan truyền NaN theo cách thông thường, nhưng **special cases có thể ưu tiên hơn**:

| Biểu thức Java | Kết quả | Hợp đồng cần nhận biết |
| --- | --- | --- |
| `Math.sqrt(-1.0)` | `NaN` | Căn bậc hai của số âm thực không có kết quả thực |
| `Math.sqrt(-0.0)` | `-0.0` | Giữ dấu zero |
| `Math.log(+0.0)` | `-Infinity` | Zero là ranh giới của logarit |
| `Math.log(-0.0)` | `-Infinity` | Cả hai zero cho âm vô cực |
| `Math.log(-1.0)` | `NaN` | Đầu vào âm ngoài miền |
| `Math.exp(Double.NEGATIVE_INFINITY)` | `+0.0` | Giới hạn của \(e^x\) khi \(x\to-\infty\) |
| `Math.pow(Double.NaN, 0.0)` | `1.0` | Mũ bằng zero được quy định ưu tiên |
| `Math.pow(-2.0, 0.5)` | `NaN` | Cơ số âm với số mũ hữu hạn không nguyên |
| `Math.hypot(Double.POSITIVE_INFINITY, Double.NaN)` | `Infinity` | Đối số vô cực có ưu tiên theo Javadoc |
| `Math.fma(Double.POSITIVE_INFINITY, 0.0, 1.0)` | `NaN` | Tích vô cực nhân zero là trường hợp đặc biệt |
| `Math.min(-0.0, +0.0)` | `-0.0` | Âm zero nhỏ hơn dương zero theo contract của `min` |
| `Math.max(-0.0, +0.0)` | `+0.0` | Dương zero lớn hơn âm zero theo contract của `max` |

Điểm tinh tế: **`Math.pow(Double.NaN, 0.0)` trả 1.0**, dù đối số đầu là NaN. **`Math.hypot(Infinity, NaN)` trả vô cực**, dù có NaN. Việc khẳng định “bất kỳ tham số NaN nào cũng bắt buộc cho NaN” sẽ sai.

Cũng không nên tưởng `Math.min`/`Math.max` sử dụng y hệt toán tử `<`/`>` trong mọi trường hợp. Chúng có quy tắc tường minh cho signed zero và NaN.

## 3. Bảng miền xác định và các trường hợp biên tiêu biểu

| Phương thức | Miền số thực hữu hạn thông thường | Đầu vào ở biên/ngoài miền |
| --- | --- | --- |
| `sqrt(x)` | \(x\geq0\) | `x<0` → NaN; `-0.0` → -0.0; `+Infinity` → +Infinity |
| `cbrt(x)` | Mọi x thực | Số âm cho căn bậc ba âm; `-0.0` → -0.0; ±Infinity giữ dấu |
| `log(x)` | \(x>0\) | ±0.0 → -Infinity; x âm → NaN; +Infinity → +Infinity |
| `log10(x)` | \(x>0\) | ±0.0 → -Infinity; x âm → NaN |
| `log1p(x)` | \(x>-1\) | `-1.0` → -Infinity; x nhỏ hơn -1 → NaN; ±0.0 giữ dấu |
| `exp(x)` | Mọi x thực | -Infinity → +0.0; +Infinity → +Infinity; có thể overflow/underflow |
| `expm1(x)` | Mọi x thực | -Infinity → -1.0; ±0.0 giữ dấu; có thể overflow |
| `asin(x)`, `acos(x)` | \(-1\leq x\leq1\) | `abs(x)>1` → NaN |
| `sin(x)`, `cos(x)`, `tan(x)` | Mọi x thực | NaN hoặc ±Infinity → NaN; `sin`/`tan` giữ dấu zero |
| `pow(a,b)` | Phụ thuộc cả a và b | `a<0`, b hữu hạn không nguyên → NaN; các quy tắc zero, NaN, vô cực chi tiết |
| `hypot(x,y)` | Mọi x,y thực hữu hạn | Có Infinity → +Infinity kể cả tham số khác NaN |
| `fma(a,b,c)` | Phép \(a\times b+c\) dấu phẩy động | Infinity × zero → NaN; có NaN → NaN |
| `min(a,b)` / `max(a,b)` cho `double` | Mọi giá trị hữu hạn | Nếu một tham số NaN → NaN; phân biệt -0.0 và +0.0 |

Bảng là bản đồ để tra cứu, **không thay thế danh sách special cases đầy đủ của từng Javadoc**. Đặc biệt `pow` có nhiều trường hợp tổ hợp số mũ 0, ±Infinity, cơ số ±0.0 và số mũ nguyên chẵn/lẻ; khi tính một tình huống biên phải đọc đúng mục `pow`.

**Hàm không ném exception** trong một ví dụ không chứng minh toàn bộ API không thể ném exception trong mọi bối cảnh. Chẳng hạn thao tác primitive nguyên như `Math.addExact` ném `ArithmeticException` khi tràn, nhưng `Math.sqrt(-1.0)` lại trả NaN. Mỗi phương thức có hợp đồng riêng.

## 4. `NaN`, ±Infinity và các phép kiểm tra đúng

Dùng API phân loại giá trị dấu phẩy động:

```java
double x = Math.log(-1.0);

System.out.println(Double.isNaN(x));      // true
System.out.println(Double.isInfinite(x)); // false
System.out.println(Double.isFinite(x));   // false
```

`Double.isFinite(x)` chỉ trả `true` cho các giá trị hữu hạn, bao gồm `+0.0`, `-0.0` và cả số subnormal. `Float` có `Float.isNaN`, `Float.isInfinite` và `Float.isFinite` tương ứng.

**Không kiểm tra NaN bằng `x == Double.NaN`.** Bởi theo so sánh dấu phẩy động, NaN không bằng chính nó:

```java
double x = Double.NaN;
System.out.println(x == Double.NaN); // false
System.out.println(Double.isNaN(x));  // true
```

Khi chương trình yêu cầu **kết quả hữu hạn**, có thể xác thực sau phép tính:

```java
static double requireFinite(double result) {
    if (!Double.isFinite(result)) {
        throw new ArithmeticException("Result is not finite");
    }
    return result;
}
```

Đây là method minh họa đặt trong class. Exception trong helper **do người lập trình chủ động ném**; không phải `Math` tự ném exception tương tự khi gặp giá trị bất thường. Kiểm tra `isFinite` vẫn **không phát hiện mất chữ số do làm tròn**, cũng không ngăn kết quả underflow về zero.

## 5. `+0.0` và `-0.0`: bằng nhau nhưng không thể thay thế tùy ý

Các kiểu `float` và `double` có bit dấu riêng cho zero. Toán tử số học `==` coi hai zero bằng nhau:

```java
System.out.println(+0.0 == -0.0); // true
```

Nhưng Javadoc quy định nhiều hàm phải xét dấu:

- `Math.sqrt(-0.0)` → `-0.0`.
- `Math.cbrt(-0.0)` → `-0.0`.
- `Math.sin(-0.0)` và `Math.tan(-0.0)` → `-0.0`.
- `Math.expm1(-0.0)` và `Math.log1p(-0.0)` → `-0.0`.
- `Math.log(-0.0)` → `-Infinity`.
- `Math.exp(-0.0)` → `1.0`.
- `Math.min(+0.0,-0.0)` → `-0.0`; `Math.max(+0.0,-0.0)` → `+0.0`.

Để phân biệt bit:

```java
long a = Double.doubleToRawLongBits(+0.0); // 0x0000000000000000
long b = Double.doubleToRawLongBits(-0.0); // 0x8000000000000000
boolean sameBits = a == b; // false
```

`Double.compare(+0.0, -0.0)` cũng phân biệt signed zero, khác với `==`. Khi dữ liệu chạy qua thuật toán có phép chia hoặc giới hạn một phía, giữ/chuẩn hóa dấu zero có thể ảnh hưởng kết quả. Ứng dụng bình thường không cần phân biệt dấu zero vẫn nên **chuẩn hóa có chủ đích**, không vì thấy `==` là true mà nghĩ bit và các phép toán sau đều giống nhau.

## 6. ULP là gì? “Sai số trong 1 ulp” có nghĩa gì?

`ulp` là viết tắt của *unit in the last place*. Với một vùng giá trị của `double`, khoảng cách giữa các số thực biểu diễn được **thay đổi theo độ lớn của số**. Gần `1.0`, khoảng cách giữa `1.0` và số `double` lớn hơn gần nhất là:

```java
System.out.println(Math.ulp(1.0));       // 2.220446049250313E-16
System.out.println(Math.nextUp(1.0));    // 1.0000000000000002
```

Javadoc `Math` dùng ULP để định nghĩa **giới hạn sai số so với kết quả toán học chính xác**, chứ không phải số chữ số decimal cố định. Với cách nói “sai số tối đa 1 ulp”, giới hạn được đo trên **lưới số thực nhị phân ở độ lớn của kết quả**, không phải “sai số không quá 1 đơn vị số thực” hoặc “chính xác một chữ số cuối”.

Một cách hình dung:

```text
... 1.0000000000000000
... 1.0000000000000002   (số double tiếp theo)
... 1.0000000000000004
       ^ khoảng cách gần 1.0 khoảng 2.22 × 10^-16
```

Gần số rất lớn, ULP lớn hơn; gần zero, khoảng cách của số subnormal cực nhỏ. Độ chính xác tương đối và sai số tuyệt đối phải được phân tích trong miền dữ liệu thực tế.

### `Math.ulp` không đo sai số thực tế của một lời gọi

`Math.ulp(result)` trả quy mô một bước biểu diễn quanh `result`; nó **không tự biết giá trị toán học chính xác** để tính `abs(result - exact)`.

Do đó:

```java
double value = Math.exp(1.0);
double spacing = Math.ulp(value);
```

`spacing` là một kích thước ULP quanh số đã nhận, **không phải báo cáo** rằng `Math.exp(1.0)` thực sự sai đúng `spacing`.

Muốn đánh giá sai số của một hàm bằng kiểm thử chuẩn, cần kết quả tham chiếu có precision cao hơn hoặc phương pháp đối chiếu đáng tin; không thể suy ra error chỉ từ một lần gọi `Math.ulp`.

## 7. “Correctly rounded”, giới hạn 0,5/1/2 ulp và semi-monotonic

Javadoc phân biệt các cấp bảo đảm:

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| **Correctly rounded** | Chọn kết quả dấu phẩy động đúng theo quy tắc làm tròn được chỉ định từ kết quả toán học chính xác |
| **Trong 0,5 ulp** | Mức sai số rất chặt; phương thức đúng như vậy được xem là trả số biểu diễn gần kết quả thực nhất (với quy tắc tie phù hợp) |
| **Trong 1 ulp** | Sai lệch giới hạn theo khoảng cách lưới số; **không** nhất thiết luôn là kết quả làm tròn gần nhất trong mọi trường hợp |
| **Trong 2 ulp** | Giới hạn lỏng hơn 1 ulp, áp dụng cho một số hàm được Javadoc chỉ rõ |
| **Semi-monotonic** | Trên vùng hàm toán học tăng (hoặc giảm), xấp xỉ dấu phẩy động không được đảo hướng tăng (hoặc giảm), theo yêu cầu từng hàm |

`Math.sqrt` được Javadoc Java 17 mô tả là **correctly rounded positive square root**: đây là một cam kết cụ thể. Trong khi đó, các hàm như `Math.sin`, `Math.log` và `Math.exp` có quy định **within 1 ulp** và **semi-monotonic**, không có lời hứa mọi kết quả được làm tròn đúng đến bit gần nhất.

Cần nhớ:

1. **Đọc con số ULP trên đúng mục phương thức**, đừng áp dụng “1 ulp cho toàn bộ `Math`”.
2. **Semi-monotonic không có nghĩa hàm luôn tăng trên toàn bộ trục số.** `sin(x)` tự nhiên có đoạn tăng, đoạn giảm; ràng buộc nói về việc xấp xỉ giữ chiều trên các đoạn mà hàm toán học có tính đơn điệu.
3. **Trong 1 ulp không đồng nghĩa cùng bit trên mọi JVM.** Hai implementation có thể cho kết quả khác nhau nhưng đều nằm trong giới hạn Javadoc.
4. **Bảo đảm về độ chính xác không thay cho bảo đảm miền giá trị.** Một hàm có đặc tả ULP vẫn có special cases NaN/Infinity/signed zero.

### Ví dụ các mức theo từng phương thức

Theo Javadoc Java 17:

| `Math` method | Cam kết liên quan |
| --- | --- |
| `sqrt(double)` | Correctly rounded |
| `sin(double)`, `cos(double)`, `tan(double)` | Trong **1 ulp**, semi-monotonic |
| `exp(double)`, `log(double)`, `log10(double)` | Trong **1 ulp**, semi-monotonic |
| `expm1(double)`, `log1p(double)` | Trong **1 ulp**, semi-monotonic |
| `pow(double,double)` | Trong **1 ulp**, semi-monotonic |
| `hypot(double,double)` | Trong **1 ulp**; semi-monotonic theo một biến khi biến kia cố định |
| `atan2(double,double)` | Trong **2 ulp**, semi-monotonic |
| `sinh(double)`, `cosh(double)`, `tanh(double)` | Trong **2,5 ulp** theo Javadoc |
| `fma(double,double,double)` | Tính tích rồi cộng như có precision/range vô hạn, **làm tròn một lần theo nearest-even** |

Không suy luận sai rằng hàm có “2,5 ulp” chắc chắn luôn sai nhiều hơn một hàm “1 ulp”. Đó là **giới hạn tối đa được API cho phép**, không phải độ lớn sai số thực tế trong mọi lần gọi.

### Tại sao `fma` khác `a * b + c`?

```java
double combined = Math.fma(a, b, c);
double separate = a * b + c;
```

`Math.fma` làm tròn **một lần** sau khi tính chính xác toán học \(a\times b+c\), trong khi biểu thức thông thường có bước làm tròn tích rồi làm tròn tổng. Hai kết quả có thể khác vài bit ở trường hợp nhạy cảm. Điều này là **contract**, không phải do CPU nào “tình cờ tính tốt hơn”.

`Math.fma(Double.POSITIVE_INFINITY, 0.0, 1.0)` trả NaN theo special case cho vô cực nhân zero, không ném exception.

## 8. `Math` khác `StrictMath` ở đâu?

**Cả hai đều** cung cấp nhiều phương thức toán học dấu phẩy động và các giá trị đặc biệt theo hợp đồng Java. Nhưng tính **tái lập kết quả bit-for-bit** không giống nhau:

- `Math` cho phép implementation khác nhau của **một số hàm** trên nền tảng/JVM khác nhau, miễn **đáp ứng accuracy và các ràng buộc Javadoc**. Điều này mở đường cho intrinsic, hướng dẫn CPU hoặc thư viện native nhanh hơn.
- `StrictMath` yêu cầu một số hàm toán học có hành vi tương thích các thuật toán được công bố trong **fdlibm 5.3**; mục tiêu chính là **tính tái lập** giữa các nền tảng, không phải lời hứa “luôn chính xác hơn `Math`”.
- Từ Java 17 (JEP 306), biểu thức dấu phẩy động Java đã trở lại ngữ nghĩa *always-strict*. Điều đó **không xóa khác biệt giữa hợp đồng `Math` và `StrictMath`**.

Javadoc Java 17 `StrictMath` nêu cụ thể các hàm phải theo fdlibm, bao gồm `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `exp`, `log`, `log10`, `cbrt`, `atan2`, `pow`, `sinh`, `cosh`, `tanh`, `hypot`, `expm1` và `log1p`.

**Không khái quát rằng mọi method trong `StrictMath` đều chạy fdlibm**; các phương thức khác có đặc tả riêng. Tên `StrictMath` cũng **không bảo đảm mọi phép gọi chạy chậm hơn hoặc chính xác hơn `Math`**.

Ví dụ một yêu cầu thực tiễn:

```java
double fastOrPlatform = Math.exp(1.0);
double specReproducible = StrictMath.exp(1.0);
```

Hai giá trị có thể giống nhau trong môi trường hiện tại, nhưng **không được dùng một test `Math.exp(x) == StrictMath.exp(x)` cho mọi x và mọi JVM như một đảm bảo**. Nếu quy trình cần tái lập bit trong hàm thuộc nhóm fdlibm, cân nhắc `StrictMath`, đồng thời cố định phiên bản Java, dữ liệu đầu vào và môi trường khi cần.

## 9. Phương thức số nguyên: độ chính xác và overflow có hợp đồng khác

Chủ đề `Math`/`StrictMath` không chỉ có `double`. Một số API số nguyên như `Math.addExact`, `Math.multiplyExact` và `Math.toIntExact` **ném `ArithmeticException` khi tràn**.

Ngược lại, phép `Math.abs(int)` có trường hợp đặc biệt:

```java
int minimum = Integer.MIN_VALUE;
System.out.println(Math.abs(minimum)); // -2147483648
```

Bởi miền `int` là `[-2^31, 2^31-1]`, giá trị tuyệt đối toán học của `Integer.MIN_VALUE` không biểu diễn được dưới dạng `int`. `Math.abs(minimum)` theo hợp đồng cũ trả lại chính số âm đó. **Đừng diễn giải tên `abs` là bảo đảm kết quả không âm với mọi `int`**.

Nếu cần phát hiện điều này:

```java
try {
    int exact = Math.absExact(Integer.MIN_VALUE);
} catch (ArithmeticException ex) {
    System.out.println("abs overflow detected");
}
```

`Math.absExact` (có trong Java 17) ném exception khi giá trị tuyệt đối không thể chứa vừa kiểu nguyên. Đây là **cơ chế lỗi khác** so với các giá trị NaN/Infinity của nhiều hàm `double`.

Tóm lại, phải xem **kiểu tham số, overload và hợp đồng cụ thể** trước khi suy luận miền và cách báo lỗi.

## 10. Xây dựng xác thực đầu vào/đầu ra theo hợp đồng ứng dụng

Javadoc cho biết **Java sẽ trả gì**, không tự quyết định giá trị đó có được ứng dụng cho phép hay không.

Ví dụ ứng dụng yêu cầu căn bậc hai của số hữu hạn không âm, kết quả cũng phải hữu hạn:

```java
static double checkedSqrt(double x) {
    if (!Double.isFinite(x) || x < 0.0) {
        throw new IllegalArgumentException("Expected finite nonnegative x");
    }
    double answer = Math.sqrt(x);
    if (!Double.isFinite(answer)) {
        throw new ArithmeticException("Non-finite square root");
    }
    return answer;
}
```

Đây là method minh họa đặt trong class. Vì `-0.0 < 0.0` là false, helper **chấp nhận -0.0**, và `Math.sqrt(-0.0)` sẽ trả **-0.0**. Nếu contract cần loại trừ hoặc chuẩn hóa âm zero, phải thêm quy tắc cụ thể; điều kiện `x < 0.0` chưa đủ.

Với logarit tự nhiên:

```java
static double checkedLog(double x) {
    if (!Double.isFinite(x) || x <= 0.0) {
        throw new IllegalArgumentException("Expected finite positive x");
    }
    double answer = Math.log(x);
    if (!Double.isFinite(answer)) {
        throw new ArithmeticException("Non-finite logarithm");
    }
    return answer;
}
```

Ở đây `x <= 0.0` sẽ **từ chối cả +0.0 và -0.0**. Cặp kiểm tra `Double.isFinite` và miền dương giúp loại NaN/Infinity một cách rõ ràng, thay vì chờ chúng lan truyền qua công thức.

### Khi cần chấp nhận giá trị IEEE 754

Các hệ thống nghiên cứu hoặc mô phỏng có thể chủ ý giữ `NaN`/Infinity như trạng thái tính toán. Khi đó không nên dùng helper cấm mọi giá trị không hữu hạn; thay vào đó cần một cách xử lý có tài liệu:

```java
double result = Math.log(input);
if (Double.isNaN(result)) {
    // Đường xử lý NaN đã được thiết kế
} else if (Double.isInfinite(result)) {
    // Đường xử lý vô cực
} else {
    // Kết quả hữu hạn
}
```

Đây là đoạn minh họa bên trong method có biến `input`. Không âm thầm chuyển NaN thành zero để “làm chương trình chạy tiếp”: zero cũng là một giá trị toán học hợp lệ, nên cách đó che mất lỗi dữ liệu.

## 11. Chương trình Java 17+ độc lập và đầu ra đáng kiểm chứng

Lưu thành **`MathJavadocContractDemo.java`**:

```java
import java.util.Locale;

public class MathJavadocContractDemo {
    static double checkedLog(double x) {
        if (!Double.isFinite(x) || x <= 0.0) {
            throw new IllegalArgumentException("Expected finite positive x");
        }
        return Math.log(x);
    }

    public static void main(String[] args) {
        System.out.println("sqrtNegative=" + Math.sqrt(-1.0));
        System.out.println("sqrtNegativeZero=" + Math.sqrt(-0.0));
        System.out.println("cbrtNegativeZero=" + Math.cbrt(-0.0));
        System.out.println("logZero=" + Math.log(0.0));
        System.out.println("logNegativeZero=" + Math.log(-0.0));
        System.out.println("logNegative=" + Math.log(-1.0));
        System.out.println("log1pMinusOne=" + Math.log1p(-1.0));
        System.out.println("expm1MinusZero=" + Math.expm1(-0.0));

        System.out.println("powNanZero=" + Math.pow(Double.NaN, 0.0));
        System.out.println("powNegativeFraction=" + Math.pow(-2.0, 0.5));
        System.out.println("hypotInfAndNan=" +
                Math.hypot(Double.POSITIVE_INFINITY, Double.NaN));
        System.out.println("fmaInfinityTimesZero=" +
                Math.fma(Double.POSITIVE_INFINITY, 0.0, 1.0));

        System.out.println("minSignedZero=" + Math.min(+0.0, -0.0));
        System.out.println("maxSignedZero=" + Math.max(+0.0, -0.0));
        System.out.println("zeroNumericEqual=" + (+0.0 == -0.0));
        System.out.println("zeroSameBits=" +
                (Double.doubleToRawLongBits(+0.0) ==
                 Double.doubleToRawLongBits(-0.0)));

        System.out.println("ulpOne=" + Math.ulp(1.0));
        System.out.println("nextUpOne=" + Math.nextUp(1.0));

        double outOfDomain = Math.log(-1.0);
        System.out.println("nanDetected=" + Double.isNaN(outOfDomain));
        System.out.println("nanFinite=" + Double.isFinite(outOfDomain));
        System.out.println("nanEqualsSelf=" +
                (Double.NaN == Double.NaN));

        System.out.println("expOverflow=" + Math.exp(800.0));
        System.out.println("expUnderflow=" + Math.exp(-800.0));
        System.out.println("overflowIsInfinite=" +
                Double.isInfinite(Math.exp(800.0)));

        System.out.println("mathExpSixDigits=" +
                String.format(Locale.ROOT, "%.6f", Math.exp(1.0)));
        System.out.println("strictExpSixDigits=" +
                String.format(Locale.ROOT, "%.6f", StrictMath.exp(1.0)));

        System.out.println("absMinimum=" + Math.abs(Integer.MIN_VALUE));
        try {
            Math.absExact(Integer.MIN_VALUE);
            System.out.println("absExact=notDetected");
        } catch (ArithmeticException ex) {
            System.out.println("absExact=detected");
        }

        try {
            checkedLog(0.0);
            System.out.println("checkedLogZero=notDetected");
        } catch (IllegalArgumentException ex) {
            System.out.println("checkedLogZero=detected");
        }
        System.out.println("checkedLogOne=" + checkedLog(1.0));
    }
}
```

Biên dịch và chạy:

```bash
javac MathJavadocContractDemo.java
java MathJavadocContractDemo
```

**Đầu ra với các giá trị được chọn**:

```text
sqrtNegative=NaN
sqrtNegativeZero=-0.0
cbrtNegativeZero=-0.0
logZero=-Infinity
logNegativeZero=-Infinity
logNegative=NaN
log1pMinusOne=-Infinity
expm1MinusZero=-0.0
powNanZero=1.0
powNegativeFraction=NaN
hypotInfAndNan=Infinity
fmaInfinityTimesZero=NaN
minSignedZero=-0.0
maxSignedZero=0.0
zeroNumericEqual=true
zeroSameBits=false
ulpOne=2.220446049250313E-16
nextUpOne=1.0000000000000002
nanDetected=true
nanFinite=false
nanEqualsSelf=false
expOverflow=Infinity
expUnderflow=0.0
overflowIsInfinite=true
mathExpSixDigits=2.718282
strictExpSixDigits=2.718282
absMinimum=-2147483648
absExact=detected
checkedLogZero=detected
checkedLogOne=0.0
```

Các test tập trung vào **special cases mà Javadoc xác định rõ**. Hai dòng định dạng `Math.exp(1)` và `StrictMath.exp(1)` chỉ kiểm tra dạng hiển thị sáu chữ số decimal, **không tuyên bố mọi bit hai giá trị đều phải khớp**. Dùng số nhỏ, zero và giá trị đặc biệt giúp xây dựng test ổn định hơn so với kiểm tra chính xác toàn bộ chữ số của một phép tính siêu việt trên mọi JVM.

## 12. Thiết kế test đúng theo Javadoc thay vì hard-code kết quả máy hiện tại

Một bộ kiểm thử nên tách ba loại:

**(A) Special cases với kết quả xác định:** kiểm tra `Double.isNaN(Math.sqrt(-1.0))`, `Math.pow(Double.NaN, 0.0) == 1.0`, hoặc bit âm zero sau `Math.sqrt(-0.0)`. Với NaN, không dùng `== Double.NaN`.

**(B) Miền đầu vào và giá trị đầu ra:** kiểm tra ứng dụng có từ chối log của số không dương, hoặc phát hiện overflow thành vô cực không. Các quy tắc này thuộc **business contract**, bổ sung cho Javadoc.

**(C) Sai số của kết quả hữu hạn:** nếu phương thức chỉ hứa “within 1 ulp” thay vì cùng bit, đừng lưu một chuỗi thập phân quá dài từ máy hiện tại làm expected duy nhất cho mọi hệ thống. So sánh với tham chiếu chính xác đủ cao và dùng mức sai số phù hợp đặc tả, đồng thời xét subnormal, vô cực, NaN, signed zero. Một công thức kiểu `abs(actual - expected) <= Math.ulp(actual)` chỉ là **minh họa thô**, chưa chắc đo đúng ulp của kết quả thực, không xử lý các giá trị đặc biệt và **không thay thế quy trình kiểm chứng chuẩn**.

Ví dụ một test về đặc điểm hướng tăng ở miền log dương có thể kiểm tra nhiều điểm mẫu. Nhưng vài điểm mẫu **không chứng minh hoàn toàn** semi-monotonic cho mọi `double`. Muốn kiểm thử sâu phải biết chính xác hợp đồng Javadoc, bộ tham chiếu, dải đầu vào và công cụ đánh giá số học.

Nếu hệ thống đòi kết quả bit-for-bit để tái lập, cân nhắc dùng `StrictMath` với các hàm có ràng buộc fdlibm và viết rõ chính sách phiên bản/đầu vào, thay vì vô tình dựa vào một implementation cụ thể của `Math`.

## 13. Hiểu lầm phổ biến và checklist

| Hiểu lầm | Cách hiểu đúng |
| --- | --- |
| “Hàm `Math` nhận số ngoài miền sẽ ném `ArithmeticException`.” | Nhiều hàm số thực trả `NaN` hoặc Infinity, theo từng special case. |
| “Bất kỳ đầu vào NaN nào cũng bắt buộc cho NaN.” | Sai với trường hợp như `Math.pow(NaN,0.0)`, và `hypot(Infinity,NaN)`. |
| “`sqrt(-0.0)` phải cho NaN vì số âm.” | Sai: -0.0 được giữ dấu theo hợp đồng căn bậc hai. |
| “`Math.min(+0.0,-0.0)` có thể tùy ý chọn một zero.” | Javadoc quy định chọn -0.0; `max` chọn +0.0. |
| “1 ulp là sai số không quá 1.0.” | Sai: ulp là bước của lưới số thực nhị phân tại độ lớn kết quả. |
| “Trong 1 ulp nghĩa là kết quả đã correctly rounded.” | Không nhất thiết; có thể chọn một giá trị kề thay vì gần nhất. |
| “`Math.sqrt`, `Math.exp` và `Math.atan2` cùng một cam kết sai số.” | Không: phải đọc từng mục Javadoc. |
| “Semi-monotonic nghĩa là hàm `sin` tăng mãi.” | Sai: đó là ràng buộc bảo toàn hướng trên vùng mà hàm toán học đơn điệu. |
| “`StrictMath` luôn nhiều chữ số chính xác hơn `Math`.” | Không có bảo đảm như vậy; trọng tâm là tái lập theo thuật toán được chỉ định. |
| “Java 17 always-strict khiến `Math` và `StrictMath` giống nhau.” | Sai: ngữ nghĩa biểu thức không xóa sự khác nhau ở đặc tả các method thư viện. |
| “`Math.abs(int)` luôn không âm.” | Sai với `Integer.MIN_VALUE`; có thể dùng `absExact` để phát hiện tràn. |
| “`Double.isFinite` đủ chứng minh kết quả không sai số.” | Sai: nó chỉ loại NaN/Infinity, không kiểm tra làm tròn hay underflow. |

Checklist thực hành:

1. **Mở đúng Javadoc của phiên bản Java đang dùng**, tìm đúng overload theo kiểu tham số.
2. **Đọc công thức và miền thực** nhưng không dừng ở đó.
3. **Đọc trọn mục “Special Cases”**: NaN, Infinity, signed zero, tham số ở biên, thứ tự ưu tiên giữa các trường hợp.
4. **Đọc dòng cam kết sai số** của từng method: correctly rounded, 1 ulp, 2 ulp, 2,5 ulp và semi-monotonic nếu có.
5. **Phân biệt `Math` và `StrictMath` theo yêu cầu tái lập**, không theo suy đoán tên class.
6. **Xác định business contract** có cho phép kết quả NaN/vô cực, zero có dấu và underflow không.
7. **Kiểm thử trường hợp biên riêng khỏi kiểm thử độ chính xác**; không dùng một so sánh `==` duy nhất cho mọi số thực.
8. **Khi cần độ chính xác hoặc tái lập nghiêm ngặt**, dùng dữ liệu tham chiếu và tiêu chí sai số phù hợp, không lấy kết quả máy hiện tại làm chân lý cho mọi nền tảng.

**Kết luận:** Hợp đồng `Math`/`StrictMath` không chỉ là công thức. Nó bao gồm **miền xác định, special cases, độ chính xác đo theo ULP, tính đơn điệu xấp xỉ và mức tái lập**. Lập trình Java đúng đòi hỏi đọc **Javadoc của từng phương thức**, xác thực giá trị theo mục đích ứng dụng và kiểm thử đúng điều API thực sự cam kết.

</details>

- [Quay lại đầu trang](#back-to-top)
