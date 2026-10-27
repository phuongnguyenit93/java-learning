<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng kích thước, miền giá trị và hằng của kiểu số](#numeric-reference-001)
- [Bảng widening, narrowing và numeric promotion](#numeric-reference-002)
- [Bảng chia, lấy dư và làm tròn cho số âm](#numeric-reference-003)
- [Bảng NaN, signed zero, equals và compare](#numeric-reference-004)
- [Bảng chọn primitive, wrapper, BigInteger hoặc BigDecimal](#numeric-reference-005)
- [Bảng precision, scale, MathContext và RoundingMode](#numeric-reference-006)
- [Bảng parse, format và chuyển đổi exact](#numeric-reference-007)
- [Tra cứu JLS và Javadoc các API số](#numeric-reference-008)

## <a id="numeric-reference-001">Bảng kích thước, miền giá trị và hằng của kiểu số</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng kích thước, miền giá trị và hằng của kiểu số trong Java

## 1. Phạm vi và cách dùng bảng tra cứu

Trong Java, kích thước và miền giá trị của **kiểu nguyên thủy** (*primitive type*) được đặc tả bởi ngôn ngữ, không thay đổi chỉ vì chạy trên Windows/Linux, JVM 32-bit/64-bit hay phần cứng khác. Khi cần tra cứu nhanh một giới hạn, nên dùng **hằng của lớp wrapper** như `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Double.MAX_VALUE` thay vì tự nhập lại con số dài.

Bài này tổng hợp:

- Các kiểu nguyên thủy biểu diễn giá trị số: `byte`, `short`, `int`, `long`, `float`, `double`; đồng thời giải thích `char` — kiểu tích phân không dấu 16-bit dùng cho **đơn vị mã UTF-16**.
- Số bit, số byte, giới hạn biểu diễn, giá trị nhỏ nhất/lớn nhất và hằng tiêu chuẩn từ các lớp wrapper.
- Khác biệt giữa số nguyên chính xác và dấu phẩy động gần đúng; các hằng `MIN_VALUE`, `MIN_NORMAL`, `MAX_EXPONENT`, `NaN`, `Infinity`.
- Vị trí của `BigInteger` và `BigDecimal` khi không có cận min/max cố định, cùng cách xem `precision()`, `scale()` và hằng thường dùng.
- Ví dụ Java 17+ có thể biên dịch và chạy để xác minh bảng bằng chính API thay vì ghi nhớ.

**Quy ước:** 1 byte trong các hằng `BYTES` ở đây là 8 bit. Bảng nói về **biểu diễn giá trị của kiểu**, không phải lượng RAM chiếm bởi một object wrapper, một mảng, một field trong object, hoặc một collection. Bộ nhớ thực tế còn có header, alignment, tham chiếu, cấu trúc dữ liệu và tối ưu JVM.

`boolean` không phải kiểu số; Java không quy định `boolean` có kích thước lưu trữ cố định theo cách áp dụng cho các kiểu số trong bảng dưới. Vì vậy không đưa `boolean` vào bảng số. `char` không phải loại dữ liệu số thường dùng để tính tiền/đếm nhưng là kiểu tích phân và có thể tham gia phép toán số thông qua numeric promotion; do đó được nêu riêng.

## 2. Bảng kiểu nguyên và char: kích thước, miền giá trị

| Kiểu Java | Bit | Byte | Có dấu? | Miền giá trị | Hằng lấy cận |
|---|---:|---:|---|---|---|
| `byte` | 8 | 1 | Có | `-128` đến `127` | `Byte.MIN_VALUE`, `Byte.MAX_VALUE` |
| `short` | 16 | 2 | Có | `-32768` đến `32767` | `Short.MIN_VALUE`, `Short.MAX_VALUE` |
| `int` | 32 | 4 | Có | `-2147483648` đến `2147483647` | `Integer.MIN_VALUE`, `Integer.MAX_VALUE` |
| `long` | 64 | 8 | Có | `-9223372036854775808` đến `9223372036854775807` | `Long.MIN_VALUE`, `Long.MAX_VALUE` |
| `char` | 16 | 2 | Không dấu | `0` đến `65535`, tức `U+0000`–`U+FFFF` theo giá trị đơn vị mã | `Character.MIN_VALUE`, `Character.MAX_VALUE` |

Đối với `byte`, `short`, `int`, `long`, số có dấu dùng **two's complement (bù hai)**. Nếu kiểu có \(n\) bit, miền là:

\[
-2^{n-1} \le x \le 2^{n-1}-1.
\]

Ví dụ `int` có 32 bit nên miền là \(-2^{31}\) đến \(2^{31}-1\). Số âm nhỏ nhất có trị tuyệt đối lớn hơn số dương lớn nhất đúng 1 đơn vị. Do đó `Math.abs(Integer.MIN_VALUE)` vẫn âm vì `2147483648` không thể biểu diễn bằng `int`; điều này cần chú ý khi viết kiểm tra trị tuyệt đối.

Riêng `char` là giá trị nguyên không dấu trong đoạn \(0\) đến \(2^{16}-1\). Java dùng `char` để biểu diễn **một UTF-16 code unit**, **không bảo đảm một `char` là toàn bộ một ký tự Unicode**. Những ký tự ngoài BMP dùng hai code unit (*surrogate pair*), vì vậy xử lý ký tự Unicode đầy đủ có thể cần `String.codePoints()`, `Character.codePointAt(...)` và kiểu `int` cho code point.

### Hằng kích thước tương ứng

| Kiểu | Hằng số bit | Giá trị | Hằng số byte | Giá trị |
|---|---|---:|---|---:|
| `byte` | `Byte.SIZE` | `8` | `Byte.BYTES` | `1` |
| `short` | `Short.SIZE` | `16` | `Short.BYTES` | `2` |
| `int` | `Integer.SIZE` | `32` | `Integer.BYTES` | `4` |
| `long` | `Long.SIZE` | `64` | `Long.BYTES` | `8` |
| `char` | `Character.SIZE` | `16` | `Character.BYTES` | `2` |

Các trường `SIZE`, `BYTES` là hằng `static final` của lớp wrapper tương ứng. Ví dụ dùng `Integer.SIZE` thay vì nhập cứng `32` trong code kiểm tra độ rộng bit giúp làm rõ ý nghĩa, dù giá trị Java đã cố định.

### Dạng cơ số 16 để nhận diện cận và bit dấu

| Kiểu | Cận dương lớn nhất (hex) | Cận âm nhỏ nhất (hex, mẫu bù hai) |
|---|---|---|
| `byte` | `0x7F` | `0x80` |
| `short` | `0x7FFF` | `0x8000` |
| `int` | `0x7FFFFFFF` | `0x80000000` |
| `long` | `0x7FFFFFFFFFFFFFFF` | `0x8000000000000000` |

Các giá trị ở cột hex âm là **mẫu bit** của cận âm, không phải phép viết số dương bằng literal hex có ý nghĩa tùy tiện. Ví dụ `Integer.MIN_VALUE` có mẫu bit 32-bit là `0x80000000`. `Character.MAX_VALUE` tương ứng `0xFFFF` và không có bit dấu.

## 3. Số dấu phẩy động: float và double không có “mọi giá trị trong khoảng”

Java cung cấp hai kiểu số dấu phẩy động nhị phân theo IEEE 754:

| Thuộc tính | `float` | `double` |
|---|---|---|
| Kích thước | 32 bit = 4 byte | 64 bit = 8 byte |
| Bit dấu | 1 | 1 |
| Bit exponent lưu trữ | 8 | 11 |
| Bit fraction lưu trữ | 23 | 52 |
| Precision nhị phân hữu hiệu cho số chuẩn hóa | 24 bit | 53 bit |
| Độ chính xác thập phân xấp xỉ | khoảng 6–9 chữ số có nghĩa cho round-trip | khoảng 15–17 chữ số có nghĩa cho round-trip |
| Cận hữu hạn dương lớn nhất | `Float.MAX_VALUE` ≈ `3.4028235E38` | `Double.MAX_VALUE` ≈ `1.7976931348623157E308` |
| Cận hữu hạn âm thấp nhất | `-Float.MAX_VALUE` | `-Double.MAX_VALUE` |
| Số dương nhỏ nhất khác 0 | `Float.MIN_VALUE` = `1.4E-45` khi in với JDK 17 (JDK 19+ có thể in `1.1754944E-38`) | `Double.MIN_VALUE` = `4.9E-324` khi in mặc định |
| Số dương **normal** nhỏ nhất | `Float.MIN_NORMAL` = `1.17549435E-38` khi in mặc định | `Double.MIN_NORMAL` = `2.2250738585072014E-308` |
| `MIN_EXPONENT` của số normal | `-126` | `-1022` |
| `MAX_EXPONENT` của số normal | `127` | `1023` |

Các chuỗi ở bảng là **biểu diễn thập phân ngắn mà Java in** cho những hằng dấu phẩy động, không phải khai triển thập phân chính xác đầy đủ của các số nhị phân đó. Chẳng hạn `Float.MIN_NORMAL` có giá trị toán học nhị phân chính xác là \(2^{-126}\), trong khi `Float.toString` ở JDK 17 in `1.17549435E-38`. Từ JDK 19, thay đổi thuật toán chuyển số thực thành chuỗi có thể cho dạng ngắn hơn `1.1754944E-38`; giá trị nhị phân và bit của hằng số không đổi.

**Rất quan trọng:** khác với `Integer.MIN_VALUE`, `Float.MIN_VALUE` và `Double.MIN_VALUE` **không phải số âm nhỏ nhất**. Chúng là **số dương nhỏ nhất lớn hơn 0 và khác 0**, thuộc loại *subnormal*. Nếu cần cận dưới hữu hạn của các giá trị `float`/`double`, dùng `-Float.MAX_VALUE` và `-Double.MAX_VALUE`.

Các giá trị có thể biểu diễn được bằng `float` hoặc `double` là một **tập rời rạc**, không phải tất cả số thực trong đoạn từ cận âm tới cận dương. Khoảng cách giữa hai số biểu diễn được thay đổi tùy theo độ lớn; nhiều phân số thập phân như `0.1` không được lưu chính xác dưới dạng nhị phân.

### Normal, subnormal và exponent

- **Normal:** có phần significand chuẩn hóa, nhận precision hữu hiệu 24 bit (`float`) hoặc 53 bit (`double`).
- **Subnormal:** những giá trị rất gần 0, nhỏ hơn `MIN_NORMAL` về trị tuyệt đối; dùng cách mã hóa cho phép tiến dần xuống 0 với precision tương đối thấp hơn.
- **`MIN_EXPONENT` và `MAX_EXPONENT`:** liên quan đến exponent của **số hữu hạn normal**, không phải chỉ số mũ nhỏ/lớn nhất trong bất kỳ cách hiển thị thập phân nào. Đừng dùng chúng như ngưỡng toàn diện cho các chuỗi `E` hoặc cho subnormal.
- **Zero:** có hai mẫu bit `+0.0` và `-0.0`, cùng so sánh bằng nhau theo toán tử primitive `==`.
- **Infinity và NaN:** những giá trị IEEE 754 đặc biệt, không thuộc đoạn số hữu hạn.

### Hằng dành cho float và double

| Hằng `float` | Hằng `double` | Ý nghĩa |
|---|---|---|
| `Float.SIZE` | `Double.SIZE` | Số bit: 32 và 64 |
| `Float.BYTES` | `Double.BYTES` | Số byte: 4 và 8 |
| `Float.MIN_VALUE` | `Double.MIN_VALUE` | Số dương subnormal nhỏ nhất khác 0 |
| `Float.MIN_NORMAL` | `Double.MIN_NORMAL` | Số dương normal nhỏ nhất |
| `Float.MAX_VALUE` | `Double.MAX_VALUE` | Số dương hữu hạn lớn nhất |
| `Float.MIN_EXPONENT` | `Double.MIN_EXPONENT` | Số mũ nhị phân thấp nhất của normal |
| `Float.MAX_EXPONENT` | `Double.MAX_EXPONENT` | Số mũ nhị phân cao nhất của normal |
| `Float.POSITIVE_INFINITY` | `Double.POSITIVE_INFINITY` | Dương vô cực |
| `Float.NEGATIVE_INFINITY` | `Double.NEGATIVE_INFINITY` | Âm vô cực |
| `Float.NaN` | `Double.NaN` | Not-a-Number |

Có thể kiểm tra giá trị đặc biệt bằng `Float.isNaN(...)`, `Double.isNaN(...)`, `Float.isInfinite(...)`, `Double.isInfinite(...)` và `isFinite(...)` tương ứng. `NaN == NaN` là `false` theo toán tử `==`; không kiểm tra NaN bằng phép so sánh bằng trực tiếp.

Ví dụ, `Double.POSITIVE_INFINITY` **khác** `Double.MAX_VALUE`: hằng thứ nhất không hữu hạn, còn hằng thứ hai là số hữu hạn dương lớn nhất.

### Miền số nguyên biểu diễn chính xác trong float/double

- Mọi số nguyên trong đoạn từ \(-2^{24}\) tới \(2^{24}\), tức `-16_777_216` tới `16_777_216`, đều được `float` biểu diễn chính xác. Số `16_777_217` không được biểu diễn chính xác bằng `float` và có thể làm tròn về `16_777_216`.
- Mọi số nguyên trong đoạn từ \(-2^{53}\) tới \(2^{53}\), tức `-9_007_199_254_740_992L` tới `9_007_199_254_740_992L`, đều được `double` biểu diễn chính xác. Số `9_007_199_254_740_993L` không được biểu diễn chính xác khi ép sang `double`.

Những ngưỡng này không có nghĩa mọi số nguyên lớn hơn ngưỡng đều không biểu diễn được: chẳng hạn một số chẵn đủ lớn vẫn có thể biểu diễn chính xác, nhưng **không còn bảo đảm mọi số nguyên liên tiếp**.

## 4. Chương trình Java 17+ kiểm tra các hằng ngay trên JVM

Để tránh tự đoán giá trị các hằng, có thể chạy chương trình tra cứu sau. Đây là một chương trình độc lập, tên file khớp với tên `public class`.

Lưu file **`NumericTypeReference.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumericTypeReference {
    public static void main(String[] args) {
        System.out.println("byte: " + Byte.SIZE + " bits, "
                + Byte.BYTES + " bytes; "
                + Byte.MIN_VALUE + ".." + Byte.MAX_VALUE);
        System.out.println("short: " + Short.SIZE + " bits, "
                + Short.BYTES + " bytes; "
                + Short.MIN_VALUE + ".." + Short.MAX_VALUE);
        System.out.println("int: " + Integer.SIZE + " bits, "
                + Integer.BYTES + " bytes; "
                + Integer.MIN_VALUE + ".." + Integer.MAX_VALUE);
        System.out.println("long: " + Long.SIZE + " bits, "
                + Long.BYTES + " bytes; "
                + Long.MIN_VALUE + ".." + Long.MAX_VALUE);
        System.out.println("char: " + Character.SIZE + " bits, "
                + Character.BYTES + " bytes; "
                + (int) Character.MIN_VALUE + ".."
                + (int) Character.MAX_VALUE);

        System.out.println("float: " + Float.SIZE + " bits, "
                + Float.BYTES + " bytes");
        System.out.println("float MIN_VALUE: " + Float.MIN_VALUE);
        System.out.println("float MIN_NORMAL: " + Float.MIN_NORMAL);
        System.out.println("float MAX_VALUE: " + Float.MAX_VALUE);
        System.out.println("float MIN_EXPONENT / MAX_EXPONENT: "
                + Float.MIN_EXPONENT + " / " + Float.MAX_EXPONENT);

        System.out.println("double: " + Double.SIZE + " bits, "
                + Double.BYTES + " bytes");
        System.out.println("double MIN_VALUE: " + Double.MIN_VALUE);
        System.out.println("double MIN_NORMAL: " + Double.MIN_NORMAL);
        System.out.println("double MAX_VALUE: " + Double.MAX_VALUE);
        System.out.println("double MIN_EXPONENT / MAX_EXPONENT: "
                + Double.MIN_EXPONENT + " / " + Double.MAX_EXPONENT);

        System.out.println("float +infinity? "
                + Float.isInfinite(Float.POSITIVE_INFINITY));
        System.out.println("double NaN? "
                + Double.isNaN(Double.NaN));

        System.out.println("big constants: "
                + BigInteger.ZERO + ", " + BigInteger.ONE + ", "
                + BigInteger.TWO + ", " + BigInteger.TEN);
        System.out.println("decimal constants: "
                + BigDecimal.ZERO + ", " + BigDecimal.ONE + ", "
                + BigDecimal.TWO + ", " + BigDecimal.TEN);

        BigDecimal decimal = new BigDecimal("123.4500");
        System.out.println("BigDecimal precision/scale: "
                + decimal.precision() + "/" + decimal.scale());
        System.out.println("BigInteger digits for 10^40: "
                + BigInteger.TEN.pow(40).toString().length());

        System.out.println("float exact-int boundary: "
                + ((float) 16_777_217 == (float) 16_777_216));
        System.out.println("double exact-long boundary: "
                + ((double) 9_007_199_254_740_993L
                        == (double) 9_007_199_254_740_992L));
    }
}
```

Chạy:

```bash
javac NumericTypeReference.java
java NumericTypeReference
```

Kết quả tham khảo với JDK 17 (một số chuỗi float/double có thể khác trên JDK 19+):

```text
byte: 8 bits, 1 bytes; -128..127
short: 16 bits, 2 bytes; -32768..32767
int: 32 bits, 4 bytes; -2147483648..2147483647
long: 64 bits, 8 bytes; -9223372036854775808..9223372036854775807
char: 16 bits, 2 bytes; 0..65535
float: 32 bits, 4 bytes
float MIN_VALUE: 1.4E-45
float MIN_NORMAL: 1.17549435E-38
float MAX_VALUE: 3.4028235E38
float MIN_EXPONENT / MAX_EXPONENT: -126 / 127
double: 64 bits, 8 bytes
double MIN_VALUE: 4.9E-324
double MIN_NORMAL: 2.2250738585072014E-308
double MAX_VALUE: 1.7976931348623157E308
double MIN_EXPONENT / MAX_EXPONENT: -1022 / 1023
float +infinity? true
double NaN? true
big constants: 0, 1, 2, 10
decimal constants: 0, 1, 2, 10
BigDecimal precision/scale: 7/4
BigInteger digits for 10^40: 41
float exact-int boundary: true
double exact-long boundary: true
```

**Cách đọc các dòng chính:**

1. `Byte.SIZE`/`Byte.BYTES` cho `8`/`1`; tương tự với các kiểu khác. Đây là đặc tả giá trị, không phụ thuộc việc JVM 64-bit.
2. Dòng `char` ép hai hằng sang `int` để in ra **mã số** 0 và 65535; nếu in trực tiếp `Character.MIN_VALUE`/`MAX_VALUE`, chúng được diễn giải như ký tự và không cho một bảng số dễ đọc.
3. `Float.MIN_VALUE` và `Double.MIN_VALUE` được in là **số dương nhỏ**; `MIN_NORMAL` cho ngưỡng từ normal xuống subnormal.
4. `BigInteger.TEN.pow(40)` là \(10^{40}\), viết dạng thập phân có **41 chữ số**. Chương trình chỉ dùng số mũ nhỏ để minh họa, không khuyến khích gọi lũy thừa rất lớn mà không kiểm soát tài nguyên.
5. Các dòng `float exact-int boundary` và `double exact-long boundary` đều là `true`: hai số nguyên gần nhau khác giá trị chính xác có thể **trở nên bằng nhau sau khi ép sang floating point**. Kết quả này không phải `float`/`double` có phép so sánh đúng hơn; nó thể hiện **mất khả năng phân biệt hai số**.

Đây là đầu ra cố định của các hằng và phép toán được dùng. Các phương thức `Float.toString`/`Double.toString` của Java tạo biểu diễn thập phân đủ để đọc lại giá trị dấu phẩy động theo hợp đồng API; chúng không liệt kê toàn bộ chữ số thập phân của giá trị nhị phân chính xác.

## 5. BigInteger và BigDecimal: không có kích thước cố định như primitive

Hai lớp `java.math.BigInteger` và `java.math.BigDecimal` là **lớp tham chiếu bất biến** (*immutable reference classes*), không phải primitive. Chúng không có `SIZE`/`BYTES` mô tả số bit lưu trữ cố định tương ứng một giá trị số duy nhất, và không định nghĩa cặp `MIN_VALUE`/`MAX_VALUE` theo kiểu `Integer`.

| Thuộc tính | `BigInteger` | `BigDecimal` |
|---|---|---|
| Loại giá trị | Số nguyên có độ lớn tùy ý | Số thập phân có precision tùy ý |
| Cấu trúc khái niệm | Dữ liệu nguyên nhiều bit | Unscaled integer và `scale` |
| Kích thước bit cố định? | Không | Không, unscaled value có độ lớn tùy ý |
| Cận min/max cố định của giá trị? | Không có hằng cận kiểu primitive | Không có hằng cận kiểu primitive |
| Hằng hay dùng | `ZERO`, `ONE`, `TWO`, `TEN` | `ZERO`, `ONE`, `TWO`, `TEN` |
| Thuộc tính kích thước | `bitLength()`, biểu diễn cơ số tùy chọn | `precision()`, `scale()`, `unscaledValue()` |
| Khởi tạo an toàn cho số viết bằng thập phân | `new BigInteger("12345678901234567890")` | `new BigDecimal("123.4500")` |

`BigInteger.TWO` và `BigDecimal.TWO` có trong Java 17+. Cả hai lớp cũng có hằng `ZERO`, `ONE` và `TEN`. Không nhầm các hằng này với giới hạn miền giá trị: chúng là những **giá trị thường dùng**, không phải cận max/min.

### BigInteger

`BigInteger` không bị giới hạn vào 32 hoặc 64 bit. Ví dụ:

```java
BigInteger large = new BigInteger("9223372036854775808");
// Lớn hơn Long.MAX_VALUE đúng 1.

BigInteger value = BigInteger.TEN.pow(40);
System.out.println(value.toString().length()); // 41
System.out.println(value.signum());            // 1
```

`bitLength()` giúp hiểu độ lớn bit; `signum()` cho `-1`, `0` hoặc `1` theo dấu. Tuy nhiên, “tùy ý” không có nghĩa vô hạn: còn giới hạn bộ nhớ, CPU, độ dài mảng và cài đặt của thư viện. Trong hệ thống tiếp nhận dữ liệu bên ngoài, cần giới hạn số chữ số đầu vào và phép toán có thể phát sinh kết quả cực lớn.

### BigDecimal

`BigDecimal` biểu diễn giá trị bằng công thức:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}.
\]

Ví dụ `new BigDecimal("123.4500")` có unscaled value bằng `1234500`, `precision() == 7`, `scale() == 4`. Số 0 cuối có thể ảnh hưởng tới **scale** và biểu diễn, dù không ảnh hưởng tới giá trị toán học.

- **Precision** là số chữ số thập phân của unscaled value (giá trị 0 có precision 1), không phải độ rộng bit cố định.
- **Scale** là số nguyên kiểu `int` và có thể âm, bằng 0 hoặc dương. `"1E+3"` có scale `-3`; `"1.00"` có scale `2`.
- `MathContext` có thể quy định precision kết quả và `RoundingMode`; `setScale(...)` quy định scale của kết quả. Đây là **quy tắc tính/làm tròn**, không phải hằng cận giá trị nguyên thủy.
- `BigDecimal` có thể tính thập phân chính xác khi dữ liệu được dựng từ chuỗi và phép toán cho phép; một số phép chia cần precision/rounding policy.

Nếu đầu vào là số thập phân chính xác từ người dùng hoặc tài liệu, ưu tiên `new BigDecimal("0.1")` thay vì `new BigDecimal(0.1)`. Constructor nhận `double` phản ánh giá trị nhị phân gần đúng của `0.1` được cung cấp. `BigDecimal.valueOf(0.1)` dùng biểu diễn thập phân của `Double.toString`, nhưng không khôi phục dữ liệu đã mất trong các phép `double` trước đó.

## 6. Hằng số, literal và chuyển đổi liên quan tới miền giá trị

### Literal nguyên: int là mặc định, long dùng L

Trong Java, literal số nguyên thập phân không có hậu tố thường có kiểu `int` nếu giá trị biểu diễn được; thêm `L` để thể hiện literal `long`. Ví dụ:

```java
int size = 1_000_000;
long population = 9_000_000_000L;
long max = Long.MAX_VALUE;
```

`9_000_000_000` không hậu tố không phải literal thập phân `int` hợp lệ dù biến đích là `long`. Việc gán sau đó không biến một literal ngoài miền `int` thành hợp lệ. Có thể sử dụng dấu gạch dưới giữa các chữ số để dễ đọc.

### Literal floating point: double mặc định, float dùng F/f

`3.14` là literal `double`; `3.14f` hoặc `3.14F` là literal `float`. Với `float`, việc dùng hậu tố `f` cần thiết khi muốn biểu diễn literal dấu phẩy động bằng kiểu `float`:

```java
float f = 0.1f;
double d = 0.1;
```

Ngay cả khi chuỗi thập phân trông giống nhau, `f` và `d` dùng lượng bit khác nhau, nên giá trị được lưu có thể khác nhau. Không giả định ép một giá trị `double` sang `float` rồi nâng lại sang `double` sẽ phục hồi precision đã mất.

### Chuyển kiểu hẹp và tràn số

`int` chứa mọi giá trị `byte` và `short`, nhưng phép chuyển `int → byte`/`short` có thể làm mất bit:

```java
int number = 130;
byte narrowed = (byte) number;
System.out.println(narrowed); // -126
```

Khi cần chắc chắn giá trị nằm trong miền, hãy so sánh với `Byte.MIN_VALUE` và `Byte.MAX_VALUE` **trước khi ép**. Với phép toán `int` hoặc `long` có thể tràn, dùng `Math.addExact`, `Math.subtractExact` hoặc `Math.multiplyExact` khi cần báo lỗi thay vì âm thầm wrap. Lưu ý `Math.abs(Integer.MIN_VALUE)` cũng gây bất ngờ vì trị tuyệt đối toán học của cận âm không thể chứa trong `int`.

### Không có primitive unsigned int/long riêng

Java không có primitive `unsigned int` hoặc `unsigned long` theo kiểu C/C++. Tuy nhiên, từ Java 8 có các phương thức hỗ trợ **diễn giải unsigned** của các bit `int`/`long` như `Integer.toUnsignedString(...)`, `Integer.compareUnsigned(...)`, `Long.divideUnsigned(...)`; `Byte.toUnsignedInt(...)` và `Short.toUnsignedInt(...)` giúp đọc 8/16 bit như số không dấu.

Điều đó **không làm mở rộng miền lưu trữ** của `int`/`long`: các biến vẫn có mẫu bit và cách hiển thị signed của kiểu tương ứng, còn một số phép toán/helper diễn giải mẫu bit theo quy ước unsigned.

## 7. Khi nào chọn kiểu nào?

| Tình huống | Lựa chọn thường hợp lý | Điểm cần kiểm tra |
|---|---|---|
| Bộ đếm, chỉ số mảng, số nguyên trong miền 32-bit | `int` | Tràn, miền âm/dương, thao tác đồng thời |
| Số nguyên vượt miền `int` nhưng trong 64-bit | `long` | Literal `L`, tràn và chuyển `long → double` |
| Giao thức tệp/đệm có trường 8 hoặc 16 bit | `byte`, `short` | Signed interpretation, masking, endian ở cấp byte |
| UTF-16 code unit | `char` | Không đồng nhất với Unicode code point |
| Mô phỏng/số thực cần tiết kiệm bộ nhớ và chấp nhận precision thấp | `float` | Sai số và nhu cầu độ chính xác |
| Phép tính số thực phổ biến với precision nhị phân cao hơn | `double` | Làm tròn, NaN, infinity, phép so sánh gần bằng |
| Số nguyên vượt miền `long` | `BigInteger` | CPU, RAM, số chữ số, phép toán sinh kết quả lớn |
| Tiền/số thập phân chính xác có quy tắc làm tròn rõ | `BigDecimal` | Scale, precision, rounding mode, nguồn tạo số |

`byte` hoặc `short` không tự làm mọi phép toán nhanh hơn `int`; các phép toán số học với `byte`/`short` thường được nâng lên `int`. Chọn kiểu theo hợp đồng dữ liệu và yêu cầu lưu trữ, không chỉ theo mong muốn “giảm số bit”.

Với tiền tệ, `BigDecimal` hoặc một số nguyên `long` đại diện đơn vị nhỏ nhất có thể phù hợp tùy nghiệp vụ. Chỉ dùng `double` nếu ứng dụng **chấp nhận rõ** sai số binary floating point và có quy tắc làm tròn/so sánh thích hợp.

## 8. Các nhầm lẫn thường gặp khi tra bảng

### Nhầm Float.MIN_VALUE là số âm thấp nhất

`Float.MIN_VALUE` là khoảng `1.4E-45`, **dương và rất gần 0**. Nếu muốn số hữu hạn âm có độ lớn lớn nhất, dùng `-Float.MAX_VALUE`. Tương tự với `Double.MIN_VALUE` và `-Double.MAX_VALUE`.

### Nhầm kích thước kiểu với kích thước object

`Integer.SIZE == 32` và `Integer.BYTES == 4` không chứng minh một đối tượng `new Integer(...)` hay `Integer` boxing chiếm đúng 4 byte RAM. Một object có header và chi phí quản lý; mảng, tham chiếu, alignment và JVM còn tạo overhead. Ngoài ra, việc tạo wrapper bằng constructor đã không được khuyến nghị; dùng autoboxing hoặc `Integer.valueOf` khi cần wrapper.

### Nhầm char là ký tự Unicode đầy đủ

`char` có 16 bit, nhưng một Unicode code point ngoài BMP cần hai UTF-16 code unit. Với chuỗi có emoji hoặc ký tự bổ sung, `String.length()` đếm code unit, không nhất thiết bằng số Unicode code point hoặc số ký tự người dùng cảm nhận.

### Nhầm Float.MIN_EXPONENT với exponent thập phân nhỏ nhất

`MIN_EXPONENT` và `MAX_EXPONENT` nêu phạm vi exponent **nhị phân của normal**, không phải số mũ `E` trong `toString()`. Mẫu subnormal còn có số dương nhỏ hơn `MIN_NORMAL`, và ký pháp scientific của số thập phân là chuyện trình bày khác.

### Nhầm miền hữu hạn với độ chính xác thập phân

`Double.MAX_VALUE` rất lớn nhưng không có nghĩa `double` biểu diễn chính xác mọi số từ 0 đến giới hạn ấy. Giá trị dấu phẩy động là tập rời rạc. Khi độ lớn tăng, khoảng cách giữa các giá trị liên tiếp có thể vượt 1; đó là lý do nhiều số nguyên lớn gần nhau ép sang `double` có cùng kết quả.

### Nhầm BigDecimal không giới hạn gì

`BigDecimal` có unscaled value độ lớn tùy ý, nhưng scale vẫn có kiểu `int`; dữ liệu và phép toán phải phù hợp RAM/CPU và quy tắc của API. `MathContext` quy định precision kết quả, không biến các phép tính với đầu vào khổng lồ thành an toàn tài nguyên.

### Nhầm nguyên tắc “chỉ cần cast kết quả”

Nếu hai toán hạng `int` thực hiện phép nhân bị tràn trước khi gán vào `long`, viết `long answer = (long)(a*b)` không khôi phục giá trị đúng. Cần đưa `long` **vào trước phép nhân**, chẳng hạn `(long)a * b`. Tương tự, `double ratio = 5 / 2` vẫn nhận `2.0`; dùng `5 / 2.0` nếu muốn `2.5`.

## 9. Checklist tra cứu nhanh và tự kiểm tra

Khi cần kiểm tra một giá trị số trong code:

1. Xác định đúng kiểu **biểu thức đang tính**, không chỉ kiểu biến đích.
2. Với `byte`/`short`/`int`/`long`, lấy hằng `MIN_VALUE`, `MAX_VALUE`, `SIZE`, `BYTES` từ wrapper tương ứng.
3. Với `float`/`double`, phân biệt **miền hữu hạn**, **precision nhị phân**, **subnormal**, `NaN` và infinity.
4. Với `char`, phân biệt UTF-16 code unit với Unicode code point và chỉ ép sang `int` khi muốn in mã số.
5. Với `BigInteger`/`BigDecimal`, xác định giới hạn nghiệp vụ và tài nguyên; dùng `bitLength()`, `precision()`, `scale()` theo từng loại thay vì hỏi `SIZE` cố định.
6. Trước khi thu hẹp kiểu hoặc nhân/chia số nguyên, xác định có mất thông tin hoặc tràn hay không.
7. Viết một test ranh giới: giá trị đúng `MAX_VALUE`, đúng `MIN_VALUE`, sát 0, số ngay ngoài miền (nếu có), và các trường hợp floating point gần ranh giới precision.

**Tự kiểm tra 1:** Vì sao `Float.MIN_VALUE` là số dương nhưng `Integer.MIN_VALUE` là số âm? Vì hai hằng có nghĩa khác: với `Integer` là cận âm của số nguyên có dấu, còn với `Float` là giá trị dương khác 0 nhỏ nhất biểu diễn được của IEEE 754.

**Tự kiểm tra 2:** `char` có thể chứa số `65535` không, và điều đó có nghĩa nó chứa mọi ký tự Unicode không? Có thể chứa giá trị code unit `65535`; **không**, code point Unicode có thể nằm ngoài BMP và phải biểu diễn bằng một cặp surrogate trong UTF-16.

**Tự kiểm tra 3:** `long` có biểu diễn được mọi số nguyên từ `0` đến `9_007_199_254_740_993` không? **Có**, vì tất cả đều nằm trong miền `long`. Nhưng `double` **không thể biểu diễn chính xác mọi số nguyên** trong khoảng đó: `2^53 + 1` đã là một ví dụ mất thông tin khi ép.

**Tự kiểm tra 4:** `BigInteger` có `MAX_VALUE` giống `Long.MAX_VALUE` không? **Không**. `BigInteger` không có một cận số học cố định ở 64 bit; giới hạn thực tế nằm ở tài nguyên và triển khai. Hằng `BigInteger.TEN` chỉ là số 10.

**Tự kiểm tra 5:** Một `BigDecimal("123.4500")` có `precision = 7`, `scale = 4` có chiếm “7 byte” không? **Không**. Precision là số chữ số thập phân có nghĩa của unscaled value, không phải kích thước lưu trữ byte.

**Ghi nhớ:** `byte/short/int/long` có miền số nguyên signed cố định; `char` là code unit 16-bit không dấu; `float/double` có miền hữu hạn, precision nhị phân hữu hạn và các giá trị đặc biệt; `BigInteger/BigDecimal` có độ lớn/precision linh hoạt nhưng không miễn giới hạn tài nguyên. Luôn tra hằng theo **kiểu và ý nghĩa cụ thể**, đặc biệt lưu ý `Float.MIN_VALUE`/`Double.MIN_VALUE` không giống cách hiểu `MIN_VALUE` của số nguyên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-002">Bảng widening, narrowing và numeric promotion</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng widening, narrowing và numeric promotion trong Java

## 1. Ba khái niệm cần phân biệt

Khi dùng các kiểu số nguyên thủy của Java, không chỉ có chuyện “số nhỏ gán cho số lớn”. Cần xác định **kiểu của từng toán hạng**, **kiểu phép toán được thực hiện**, và **kiểu của biến nhận kết quả**:

- **Widening primitive conversion**: chuyển sang một kiểu được phép mở rộng ngầm, ví dụ `int → long`. Không nhất thiết chính xác tuyệt đối khi kiểu đích là `float` hoặc `double`.
- **Narrowing primitive conversion**: chuyển sang kiểu có thể mất miền giá trị/chữ số, ví dụ `int → byte` hoặc `double → int`. Thường phải cast tường minh.
- **Numeric promotion**: tự nâng kiểu toán hạng trước khi tính, ví dụ `byte + byte` được tính bằng `int`.

Bài học là tài liệu tra cứu cho `byte`, `short`, `char`, `int`, `long`, `float`, `double` trong Java 17+, gồm bảng chuyển đổi, các ngoại lệ, phép tính số nguyên và số thực, ví dụ chương trình độc lập, lỗi thường gặp và bài tập. `boolean` **không chuyển thành số hoặc ngược lại** theo cơ chế này.

## 2. Bảng widening primitive conversion

Các phép gán sau hợp lệ mà **không cần cast**:

| Từ kiểu | Có thể widening sang |
| --- | --- |
| `byte` | `short`, `int`, `long`, `float`, `double` |
| `short` | `int`, `long`, `float`, `double` |
| `char` | `int`, `long`, `float`, `double` |
| `int` | `long`, `float`, `double` |
| `long` | `float`, `double` |
| `float` | `double` |
| `double` | Không có kiểu số nguyên thủy rộng hơn |
| `boolean` | Không tham gia chuyển đổi số |

Sơ đồ gợi nhớ:

~~~text
byte → short → int → long → float → double
               ↑
char ──────────┘
~~~

**Không có widening `byte → char` hay `short → char`.** `char` là kiểu **không dấu 16 bit**, lưu mã UTF-16 code unit từ 0 đến 65535; `short` là kiểu **có dấu 16 bit**, từ -32768 đến 32767. Cùng độ rộng bit không có nghĩa hai kiểu tự chuyển đổi ngầm qua lại.

### Widening có luôn giữ nguyên giá trị không?

**Không.** Các widening giữa những kiểu nguyên phù hợp (`byte → short/int/long`, `short → int/long`, `char → int/long`, `int → long`) đều giữ chính xác giá trị. `int → double` cũng giữ chính xác mọi int, vì double có 53 bit significand, nhiều hơn 32 bit của int. `float → double` giữ chính xác *giá trị nhị phân float đang lưu* nhưng không khôi phục số decimal đã bị làm tròn trước đó.

Ngược lại, `int → float`, `long → float` và `long → double` có thể mất các bit có nghĩa do kiểu đích không đủ độ chính xác. Trình biên dịch vẫn cho phép widening; nó không đảm bảo làm tròn sẽ không xảy ra.

### Ví dụ Java 17: widening mất precision

Tạo tệp `WideningDemo.java`:

~~~java
public class WideningDemo {
    public static void main(String[] args) {
        byte source = 100;
        short s = source;
        long exact = s;
        double d = exact;

        System.out.println("long = " + exact);
        System.out.println("double = " + d);

        int largeInt = 16_777_217;
        float f = largeInt;
        System.out.println("int = " + largeInt);
        System.out.println("float = " + f);
        System.out.println("float back = " + (int) f);

        long largeLong = 9_007_199_254_740_993L;
        double rounded = largeLong;
        System.out.println("long back = " + (long) rounded);
    }
}
~~~

~~~bash
javac WideningDemo.java
java WideningDemo
~~~

Kết quả:

~~~text
long = 100
double = 100.0
int = 16777217
float = 1.6777216E7
float back = 16777216
long back = 9007199254740992
~~~

`float` có 24 bit significand nên không thể biểu diễn chính xác mọi `int` trên mốc `2^24`. `double` không thể biểu diễn chính xác mọi `long` trên mốc `2^53`. Widening có thể an toàn về **kiểu** mà không an toàn về **precision**.

## 3. Bảng narrowing primitive conversion

Đường narrowing cần cast trong phần lớn tình huống:

| Từ | Các đích narrowing tiêu biểu | Hậu quả cần xét |
| --- | --- | --- |
| `short` | `byte`, `char` | Mất bit cao, có thể đổi dấu |
| `char` | `byte`, `short` | Mất bit hoặc đổi cách diễn giải dấu |
| `int` | `byte`, `short`, `char` | Giữ các bit thấp theo độ rộng đích |
| `long` | `byte`, `short`, `char`, `int` | Bỏ các bit cao |
| `double` | `float` | Có thể làm tròn, underflow thành zero, overflow thành infinity |
| `float/double` | `int`, `long` và các kiểu nguyên nhỏ hơn | Loại phần thập phân, có quy tắc riêng cho NaN/vượt miền |

Các kiểu nguyên có dấu sử dụng biểu diễn bù hai. Với narrowing từ `int` sang `byte`, Java giữ 8 bit thấp rồi diễn giải thành byte có dấu. Vì vậy `(byte) 130` bằng `-126`, không phải 127 và không ném exception. `(char) -1` giữ 16 bit thấp và thành code unit số `65535`.

### Cast từ float/double sang nguyên không phải rounding gần nhất

`(int) 3.99` là `3`; `(int) -3.99` là `-3`. Quy tắc loại phần thập phân **về 0**. Với `NaN`, chuyển sang `int`/`long` cho 0. Với giá trị vượt miền int/long, quá trình chuyển sang chính kiểu đó cho biên min/max theo hướng tương ứng; với `byte`/`short`/`char` còn có bước thu hẹp nguyên tiếp theo nên kết quả **không đơn giản là chặn tại biên kiểu nhỏ**.

### Ví dụ Java 17: narrowing và giá trị đặc biệt

Tạo tệp `NarrowingDemo.java`:

~~~java
public class NarrowingDemo {
    public static void main(String[] args) {
        int n = 130;
        byte b = (byte) n;
        char c = (char) -1;

        System.out.println("byte = " + b);
        System.out.println("char code = " + (int) c);
        System.out.println("3.99 -> int = " + (int) 3.99);
        System.out.println("-3.99 -> int = " + (int) -3.99);
        System.out.println("NaN -> int = " + (int) Double.NaN);
        System.out.println("infinity -> int = "
                + (int) Double.POSITIVE_INFINITY);
        System.out.println("1000 -> byte = " + (byte) 1000);
    }
}
~~~

~~~bash
javac NarrowingDemo.java
java NarrowingDemo
~~~

Kết quả:

~~~text
byte = -126
char code = 65535
3.99 -> int = 3
-3.99 -> int = -3
NaN -> int = 0
infinity -> int = 2147483647
1000 -> byte = -24
~~~

Khi cần làm tròn thực sự, sử dụng `Math.round` hoặc `BigDecimal.setScale` cùng rounding mode thích hợp; đừng thay bằng cast và kỳ vọng quy tắc giống nhau. Cũng không dùng cast để kiểm tra an toàn miền giá trị vì Java không tự ném lỗi khi các bit bị bỏ.

## 4. Unary numeric promotion

Các toán tử một ngôi số học `+x`, `-x` và toán tử bù bit `~x` (cho kiểu nguyên) áp dụng **unary numeric promotion**:

| Kiểu ban đầu | Kiểu sau promotion |
| --- | --- |
| `byte`, `short`, `char` | `int` |
| `int` | `int` |
| `long` | `long` |
| `float` | `float` |
| `double` | `double` |

Ví dụ `byte x = 5; int y = -x;` hợp lệ, vì `-x` có kiểu int. `byte y = -x;` không hợp lệ nếu `x` là biến byte runtime và không cast, dù giá trị `-5` có thể vừa byte.

`~charValue` cũng có kiểu int. Toán tử logic `!` áp dụng lên boolean, không phải numeric promotion. Tương tự, `boolean` không chuyển thành 0 hoặc 1 khi tính toán số học.

## 5. Binary numeric promotion: bảng chọn kiểu hai toán hạng

Trong các phép toán hai ngôi số học như `+`, `-`, `*`, `/`, `%`, Java chọn kiểu tính toán theo thứ tự:

1. Có `double` → dùng `double`.
2. Không có double, có `float` → dùng `float`.
3. Không có double/float, có `long` → dùng `long`.
4. Còn lại → dùng `int`.

| Biểu thức | Kiểu tính toán/kết quả |
| --- | --- |
| `byte + byte` | `int` |
| `byte + short` | `int` |
| `short + char` | `int` |
| `int + long` | `long` |
| `long + float` | `float` |
| `float + double` | `double` |
| `int / int` | `int` |
| `int / double` | `double` |
| `char * byte` | `int` |

Các toán tử so sánh số như `<` và `>=` cũng dùng numeric promotion để so, nhưng **kết quả của phép so sánh là boolean**. Đừng áp dụng bảng “kiểu kết quả số học” lên mọi toán tử một cách máy móc. Với phép nối chuỗi, toán tử `+` còn có ngữ nghĩa nối chuỗi khác phép cộng số.

### Ví dụ Java 17: chia nguyên và kiểu của biểu thức

Tạo tệp `NumericPromotionDemo.java`:

~~~java
public class NumericPromotionDemo {
    public static void main(String[] args) {
        byte a = 10;
        short b = 20;
        int sum = a + b;
        char letter = 'A';
        int nextCode = letter + 1;
        float mixed = 1L + 0.5f;

        System.out.println("byte + short = " + sum);
        System.out.println("char + int = " + nextCode);
        System.out.println("long + float = " + mixed);
        System.out.println("5 / 2 = " + (5 / 2));
        System.out.println("assigned double = " + (double) (5 / 2));
        System.out.println("5 / 2.0 = " + (5 / 2.0));
    }
}
~~~

~~~bash
javac NumericPromotionDemo.java
java NumericPromotionDemo
~~~

Kết quả:

~~~text
byte + short = 30
char + int = 66
long + float = 1.5
5 / 2 = 2
assigned double = 2.0
5 / 2.0 = 2.5
~~~

**Kiểu biến nhận kết quả không sửa kiểu của phép toán đã xảy ra.** `double d = 5 / 2;` thực hiện chia nguyên trước (cho 2), rồi widening từ int thành double (2.0). Muốn kết quả 2.5, chuyển **toán hạng trước phép chia**: `5 / 2.0` hoặc `(double) 5 / 2`.

## 6. Overflow trước khi widening sang biến đích

Một bẫy:

~~~java
// Đoạn minh họa: kết quả đã tràn int trước khi gán.
long wrong = 50_000 * 50_000;
~~~

Cả hai literal là `int`, nên `50_000 * 50_000` là phép nhân int, overflow trước khi được chuyển sang long. Để tránh, dùng literal long `50_000L * 50_000`, hoặc cast một toán hạng **trước khi nhân**.

### Ví dụ Java 17: overflow do kiểu toán hạng

Tạo tệp `OverflowDemo.java`:

~~~java
public class OverflowDemo {
    public static void main(String[] args) {
        long wrong = 50_000 * 50_000;
        long correct = 50_000L * 50_000;

        System.out.println("int first = " + wrong);
        System.out.println("long first = " + correct);

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("overflow detected");
        }
    }
}
~~~

~~~bash
javac OverflowDemo.java
java OverflowDemo
~~~

Kết quả:

~~~text
int first = -1794967296
long first = 2500000000
overflow detected
~~~

Phép `+`, `-`, `*` với `int`/`long` thường không ném lỗi overflow; kết quả bù hai quay vòng theo số bit. Khi không được phép tràn âm thầm, `Math.addExact`, `subtractExact`, `multiplyExact` giúp phát hiện lỗi đối với int/long.

## 7. Phép dịch bit dùng quy tắc riêng

Toán tử dịch bit `<<`, `>>`, `>>>` không dùng binary numeric promotion như phép cộng. **Từng toán hạng được unary numeric promotion** và kiểu kết quả theo toán hạng bên trái sau promotion (`int` hoặc `long`).

- `byte b = 1; b << 8` cho kết quả kiểu int = 256.
- `1L << 40` dịch long và cho 1099511627776.
- `1 << 32` bằng `1 << 0` vì chỉ 5 bit thấp của khoảng dịch được dùng cho int.
- `1L << 64` bằng `1L << 0` vì chỉ 6 bit thấp được dùng cho long.

### Ví dụ Java 17: shift và promotion

Tạo tệp `ShiftDemo.java`:

~~~java
public class ShiftDemo {
    public static void main(String[] args) {
        byte x = 1;
        int result = x << 8;
        long wide = 1L << 40;

        System.out.println("byte shift = " + result);
        System.out.println("long shift = " + wide);
        System.out.println("1 << 32 = " + (1 << 32));
        System.out.println("-1 >>> 1 = " + (-1 >>> 1));
    }
}
~~~

~~~bash
javac ShiftDemo.java
java ShiftDemo
~~~

Kết quả:

~~~text
byte shift = 256
long shift = 1099511627776
1 << 32 = 1
-1 >>> 1 = 2147483647
~~~

Các toán tử bitwise `&`, `|`, `^` giữa các số nguyên tuân quy tắc numeric promotion riêng phù hợp; khi hai toán hạng là boolean, chúng cũng có dạng toán tử boolean, **không có chuyển boolean sang int**.

## 8. Ngoại lệ: narrowing hằng int trong assignment

Trong **assignment conversion**, một *biểu thức hằng* có kiểu int có thể gán ngầm vào `byte`, `short` hoặc `char` nếu **giá trị hằng nằm trong miền** của kiểu đích:

~~~java
// Minh họa cú pháp hợp lệ, không phải chương trình độc lập.
byte max = 127;
short s = 1 + 2;
char latinA = 65;
final int LIMIT = 100;
byte fromConstant = LIMIT;
~~~

Nhưng:

~~~java
// Minh họa lỗi biên dịch, không được chạy nguyên khối này.
byte invalid = 128;      // Hằng vượt miền byte
int value = 127;
byte fromValue = value;   // value không phải constant expression
byte x = 10;
byte result = x + 1;     // x + 1 có kiểu int
~~~

Một `final int` khởi tạo bằng biểu thức hằng phù hợp có thể tham gia quy tắc này; biến `int` thông thường không được narrowing ngầm chỉ vì giá trị lúc chạy đang vừa miền byte.

### Ví dụ Java 17: hằng vừa miền và cast biến

Tạo tệp `ConstantAssignmentDemo.java`:

~~~java
public class ConstantAssignmentDemo {
    public static void main(String[] args) {
        byte max = 127;
        short computed = 1 + 2;
        char ascii = 65;
        final int LIMIT = 100;
        byte fromConstant = LIMIT;

        int runtime = 127;
        byte explicit = (byte) runtime;

        System.out.println("byte = " + max);
        System.out.println("short = " + computed);
        System.out.println("char code = " + (int) ascii);
        System.out.println("final constant = " + fromConstant);
        System.out.println("cast variable = " + explicit);
    }
}
~~~

~~~bash
javac ConstantAssignmentDemo.java
java ConstantAssignmentDemo
~~~

Kết quả:

~~~text
byte = 127
short = 3
char code = 65
final constant = 100
cast variable = 127
~~~

**Ngoại lệ gán hằng không áp dụng tự động cho method invocation.** Với phương thức `takeByte(byte x)`, gọi `takeByte(1)` không hợp lệ nếu đó là overload duy nhất; phải dùng `takeByte((byte) 1)` hoặc truyền biến byte.

## 9. Compound assignment và ++ có narrowing ngầm

`byte b = 120; b += 10;` hợp lệ, dù biểu thức `b + 10` có kiểu int. Compound assignment có ngữ nghĩa gần giống `b = (byte) (b + 10)`, khác ở chỗ toán hạng trái chỉ được đánh giá một lần. Điều này cho phép **narrowing ngầm** và có thể tạo overflow. `++`, `--` với các kiểu số nguyên nhỏ cũng có thể đưa giá trị quay vòng.

### Ví dụ Java 17: += không bảo đảm an toàn miền

Tạo tệp `CompoundAssignmentDemo.java`:

~~~java
public class CompoundAssignmentDemo {
    public static void main(String[] args) {
        byte b = 120;
        b += 10;

        short s = 32767;
        s++;

        int i = Integer.MAX_VALUE;
        i += 1L;

        System.out.println("byte += = " + b);
        System.out.println("short++ = " + s);
        System.out.println("int += long = " + i);
    }
}
~~~

~~~bash
javac CompoundAssignmentDemo.java
java CompoundAssignmentDemo
~~~

Kết quả:

~~~text
byte += = -126
short++ = -32768
int += long = -2147483648
~~~

Ngược lại, `b = b + 10` báo lỗi biên dịch nếu b là biến byte vì gán `int` runtime vào byte thiếu cast. Việc cú pháp `+=` biên dịch được không đồng nghĩa kết quả ở trong miền. Với dữ liệu cần an toàn, tính trên kiểu rộng và kiểm tra giới hạn trước khi cast.

## 10. Method invocation, overload, boxing và toán tử ba ngôi

**Method invocation conversion** cho phép widening primitive hợp lệ, nhưng **không áp dụng ngoại lệ narrowing hằng int trong assignment**. Phân giải overload còn phụ thuộc các quy tắc ưu tiên giữa phương thức có thể gọi, boxing/unboxing, varargs và mức độ cụ thể của chữ ký.

### Ví dụ Java 17: widening khi chọn overload

Tạo tệp `OverloadDemo.java`:

~~~java
public class OverloadDemo {
    static String choose(long x) {
        return "long";
    }
    static String choose(float x) {
        return "float";
    }
    static String choose(double x) {
        return "double";
    }

    public static void main(String[] args) {
        System.out.println("int -> " + choose(5));
        System.out.println("float -> " + choose(5.0f));
        System.out.println("double -> " + choose(5.0));
    }
}
~~~

~~~bash
javac OverloadDemo.java
java OverloadDemo
~~~

Kết quả:

~~~text
int -> long
float -> float
double -> double
~~~

`choose(5)` có thể widening int sang long, float, double; trong các overload này long là lựa chọn cụ thể hơn. `Integer`, `Long`, `Double` là **wrapper reference types**, không phải primitive; có quy tắc boxing/unboxing riêng. Ví dụ `Integer i = 10; long x = i;` hợp lệ do unboxing int rồi widening long, nhưng `Long l = Integer.valueOf(10);` không phải widening giữa hai wrapper. Unboxing một wrapper `null` có thể ném `NullPointerException`.

Toán tử ba ngôi `condition ? a : b` có quy tắc **numeric conditional expressions** riêng, với ngoại lệ liên quan byte/short/char và hằng int vừa miền. Vì vậy không thể áp dụng mù quáng bảng `a + b` cho tất cả toán tử ba ngôi. Hãy kiểm tra loại từng nhánh, các trường hợp hằng và phiên bản JLS khi cần suy luận kiểu kết quả chính xác.

## 11. BigDecimal không tự tham gia numeric promotion

`BigDecimal` là **lớp**, không thuộc hệ primitive promotion:

- `0.1 + 0.2` là phép tính double, không phải phép cộng decimal chính xác.
- `new BigDecimal("0.1").add(new BigDecimal("0.2"))` thực hiện phép cộng BigDecimal thập phân.
- Java không tự cho phép toán tử `BigDecimal + int`. Dùng `add` và tạo toán hạng BigDecimal tường minh.
- Java cũng không tự chuyển một phép int/long bị overflow sang BigInteger hoặc BigDecimal.
- `new BigDecimal(0.1)` giữ đúng giá trị double nhị phân gần đúng đã nhận, có thể khác số decimal 0.1 người dùng mong muốn; ưu tiên constructor chuỗi decimal chính xác khi bài toán cần điều đó.

Với tiền hoặc các giá trị decimal đòi độ chính xác, chọn kiểu số thích hợp **trước khi thực hiện phép toán**, không chờ mất dữ liệu rồi mới ép kiểu kết quả.

## 12. Bảng quyết định nhanh

| Code hoặc tình huống | Kết quả/quy tắc |
| --- | --- |
| `long l = someInt;` | Widening int→long, giữ nguyên giá trị |
| `float f = someInt;` | Widening hợp lệ, có thể mất precision |
| `byte b = 127;` | Hợp lệ do ngoại lệ hằng int trong assignment |
| `byte b = 128;` | Lỗi compile: ngoài miền |
| `int i = someLong;` | Lỗi compile nếu không cast |
| `byte a = 1, b = 2; int c = a+b;` | Phép cộng dùng int |
| `double d = 5/2;` | 2.0, vì chia int trước |
| `double d = 5/2.0;` | 2.5, vì có toán hạng double |
| `long r = 50000*50000;` | Tràn int trước khi widening sang long |
| `long r = 50000L*50000;` | Nhân long, kết quả 2500000000 |
| `byte b = 120; b += 10;` | Kết quả -126 do narrowing ngầm |
| `int i = (int)3.99;` | 3, bỏ phần lẻ hướng về 0 |
| `char c = (char)-1;` | Mã char = 65535 |
| `int s = 1 << 32;` | 1 do chỉ dùng 5 bit thấp khoảng dịch |
| `boolean b = (boolean)1;` | Không hợp lệ, boolean không tham gia chuyển đổi số |

Bảng áp dụng trực tiếp cho kiểu primitive. Những trường hợp boxing, wrapper, generic, overload, toán tử ba ngôi và hằng đặc biệt phải xét thêm quy tắc tương ứng.

## 13. Sai lầm thường gặp

1. **“Widening luôn chính xác.”** Không đúng với int→float hoặc long→double cho số lớn.
2. **“Biến long nhận kết quả sẽ ép phép toán sang long.”** Không: int có thể tràn trước khi gán.
3. **“byte + byte là byte.”** Không: binary promotion thành int.
4. **“char tương đương short.”** Không: char không dấu, short có dấu.
5. **“Cast double sang int làm tròn gần nhất.”** Không: cắt phần thập phân về 0.
6. **“Cast narrowing tự kiểm tra vượt miền.”** Không: số nguyên có thể bị cắt các bit cao.
7. **“`+=` an toàn hơn `= a+b`.”** Không: có narrowing ngầm.
8. **“Hằng số int vừa miền được narrowing trong mọi ngữ cảnh.”** Ngoại lệ assignment không áp dụng với method invocation thông thường.
9. **“Dịch bit dùng binary promotion như cộng.”** Không: kiểu kết quả dựa vào toán hạng trái đã unary promotion.
10. **“Boolean có thể cast sang int.”** Java không hỗ trợ.
11. **“Wrapper có thể chuyển ngầm như primitive.”** Boxing/unboxing và widening reference có quy tắc riêng.
12. **“BigDecimal được Java tự chọn khi có số decimal.”** Không: phải dùng API BigDecimal tường minh.

## 14. Bài tập và đáp án

**Câu 1.** `int i = 16_777_217; float f = i;` có cho cùng giá trị không?

**Đáp án:** Không. Float có thể biểu diễn 16.777.216 mà không giữ chính xác 16.777.217.

**Câu 2.** Vì sao `byte x=10, y=20; byte sum=x+y;` không biên dịch?

**Đáp án:** `x+y` có kiểu int; dùng `int sum=x+y` hoặc cast sau khi xác minh miền.

**Câu 3.** `double result=5/2;` bằng bao nhiêu và sửa thế nào?

**Đáp án:** 2.0; dùng `5/2.0` hoặc `(double)5/2` để được 2.5.

**Câu 4.** `(byte)130`, `(char)-1` và `(int)-3.99` cho kết quả gì?

**Đáp án:** -126, char code 65535 và -3.

**Câu 5.** `byte b=120; b+=10;` và `short s=32767; s++;` cho giá trị gì?

**Đáp án:** b là -126, s là -32768; compound assignment/increment có thể narrowing.

**Câu 6.** `long p=50_000*50_000;` có bằng `2_500_000_000L` không?

**Đáp án:** Không; phép nhân int tràn trước. Dùng `50_000L*50_000`.

**Câu 7.** Tại sao `byte b=127;` hợp lệ nhưng `int n=127; byte b=n;` không?

**Đáp án:** Biểu thức hằng int vừa miền có ngoại lệ gán, còn biến int thông thường cần cast.

**Câu 8.** `1 << 32` cho số nào?

**Đáp án:** 1, vì dịch int chỉ xem 5 bit thấp của khoảng dịch, nên 32 tương đương 0.

## 15. Ghi nhớ

- **Widening** được phép ngầm nhưng có thể mất precision khi từ số nguyên lớn sang floating-point.
- **Narrowing** thường cần cast; số nguyên bỏ bit cao, dấu phẩy động sang nguyên bỏ phần lẻ hướng về 0 và có quy tắc biên.
- **Unary promotion** nâng byte/short/char lên int; **binary promotion** ưu tiên double, float, long rồi int trước phép tính.
- **Kiểu toán hạng quyết định phép toán trước khi gán**, nên double nhận phép chia nguyên hoặc long nhận phép nhân int đã tràn vẫn giữ lỗi trung gian.
- **Constant narrowing trong assignment**, compound assignment, toán tử dịch, overload, toán tử ba ngôi và boxing/unboxing là các ngoại lệ cần tra cứu riêng.
- Với số học decimal chính xác hoặc số nguyên vượt long, dùng BigDecimal/BigInteger chủ động qua API, không mong primitive promotion tự giải quyết.

Tài liệu chuẩn: Java Language Specification cho Java SE 17 — `5.1.2 Widening Primitive Conversion`, `5.1.3 Narrowing Primitive Conversion`, `5.2 Assignment Contexts`, `5.6 Numeric Contexts`, `15.15 Unary Operators`, `15.17 Multiplicative Operators`, `15.18 Additive Operators`, `15.19 Shift Operators`, `15.25 Conditional Operator` và `15.26 Assignment Operators`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-003">Bảng chia, lấy dư và làm tròn cho số âm</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng chia, lấy dư và làm tròn cho số âm trong Java

## 1. Mục tiêu tra cứu: cùng một số âm, nhiều quy tắc khác nhau

Với số dương, nhiều phép chia và làm tròn trông giống nhau. Nhưng khi xuất hiện số âm, ba khái niệm sau có thể dẫn đến các kết quả rất khác:

1. **Chia lấy thương nguyên theo hướng về 0** — phép `/` trên hai kiểu nguyên của Java.
2. **Chia lấy thương nguyên theo hướng về âm vô cực** — `Math.floorDiv`.
3. **Làm tròn một số thực/thập phân** — `Math.floor`, `Math.ceil`, `Math.round`, `Math.rint` hoặc `BigDecimal.setScale(..., RoundingMode)`.

Ví dụ:

~~~java
System.out.println(-7 / 3);                 // -2
System.out.println(-7 % 3);                 // -1
System.out.println(Math.floorDiv(-7, 3));   // -3
System.out.println(Math.floorMod(-7, 3));   // 2
~~~

Nếu cần chia thời gian thành bucket, tính chỉ số trang, quay vòng index, đối chiếu số dư nợ, hoặc dựng công thức làm tròn tài chính, nhầm các quy tắc này có thể gây lỗi biên nghiêm trọng. Bài tra cứu này tập trung vào **số âm**, nhưng đặt kết quả dương bên cạnh để tránh rút ra quy tắc sai.

**Bản đồ nhanh:**

| Muốn thực hiện | API / toán tử | Hướng chia hoặc làm tròn |
| --- | --- | --- |
| Chia `int` / `long`, bỏ phần lẻ | `a / b` | Thương nguyên về **0** |
| Lấy dư tương ứng thương về 0 | `a % b` | Dư cùng dấu **số bị chia** nếu khác 0 |
| Chia nguyên theo floor | `Math.floorDiv(a, b)` | Thương về **−∞** |
| Phần dư theo floor | `Math.floorMod(a, b)` | Dư cùng dấu **số chia** nếu khác 0 |
| Làm tròn xuống theo trục số | `Math.floor(x)` | Về **−∞**, trả `double` |
| Làm tròn lên theo trục số | `Math.ceil(x)` | Về **+∞**, trả `double` |
| Làm tròn gần nhất, điểm giữa về +∞ | `Math.round(x)` | Trả `int` nếu `float`, `long` nếu `double` |
| Làm tròn gần nhất, điểm giữa về số chẵn | `Math.rint(x)` | Trả `double` |
| Làm tròn decimal với chính sách rõ ràng | `BigDecimal.setScale(scale, mode)` | Do `RoundingMode` quyết định |

Không có một phương pháp gọi chung là “làm tròn xuống” đủ chính xác khi giá trị có thể âm. Hãy luôn hỏi “**xuống theo trục số**, hay **giảm độ lớn về 0**?”.

## 2. Chia số nguyên: `/` luôn cắt phần lẻ về 0

Khi cả hai toán hạng đều là các kiểu nguyên (`int`, `long`; `byte`/`short`/`char` sau numeric promotion), phép `/` cho thương **nguyên**. Java cắt phần thập phân về **0**, không dùng floor.

| Số bị chia `a` | Số chia `b` | Thương thực | `a / b` (Java) |
| ---: | ---: | ---: | ---: |
| `7` | `3` | `2.333…` | `2` |
| `-7` | `3` | `-2.333…` | `-2` |
| `7` | `-3` | `-2.333…` | `-2` |
| `-7` | `-3` | `2.333…` | `2` |
| `-1` | `3` | `-0.333…` | `0` |
| `-8` | `3` | `-2.666…` | `-2` |

Ở đây `-7 / 3 == -2`, **không phải −3**. Phần lẻ bị bỏ, dù điều đó khiến kết quả lớn hơn thương thực theo trục số.

Thứ tự toán hạng rất quan trọng: `-7 / 3` và `7 / -3` cùng ra `-2`, nhưng lấy dư đi kèm không giống nhau vì số bị chia khác dấu.

### `/` trên floating point không phải chia nguyên

~~~java
System.out.println(-7 / 3);     // -2, int
System.out.println(-7.0 / 3);   // -2.3333333333333335, double
System.out.println(-7 / 3.0);   // -2.3333333333333335, double
~~~

`-7.0 / 3` không bị cắt về 0: toán hạng `double` khiến phép chia dùng floating point. Kết quả thập phân hiển thị là xấp xỉ binary64, không phải một decimal vô hạn chính xác.

Nếu muốn làm tròn thương thành một số nguyên theo quy tắc khác, **đừng** chia nguyên rồi mới cast sang `double` hoặc gọi `Math.floor`. Phần lẻ đã mất và không thể khôi phục:

~~~java
double tooLate = Math.floor(-7 / 3);    // -2.0, vì -7 / 3 đã ra -2
double floorReal = Math.floor(-7.0 / 3); // -3.0
~~~

## 3. Toán tử `%`: số dư có dấu theo số bị chia

Với phép chia **nguyên**, Java dùng một thương cắt về 0; số dư từ `a % b` thỏa mãn:

\[
a = (a / b) \times b + (a \% b)
\]

Giả sử `b != 0` và không rơi vào góc tràn `MIN_VALUE / -1`. Khi số dư khác 0, dấu của nó cùng dấu `a` (số bị chia).

| `a` | `b` | `a / b` | `a % b` | Kiểm tra |
| ---: | ---: | ---: | ---: | --- |
| `7` | `3` | `2` | `1` | `2 × 3 + 1 = 7` |
| `-7` | `3` | `-2` | `-1` | `-2 × 3 − 1 = −7` |
| `7` | `-3` | `-2` | `1` | `-2 × -3 + 1 = 7` |
| `-7` | `-3` | `2` | `-1` | `2 × -3 − 1 = −7` |
| `-8` | `3` | `-2` | `-2` | `-2 × 3 − 2 = −8` |
| `-1` | `3` | `0` | `-1` | `0 × 3 − 1 = −1` |

**Cảnh báo:** `%` trong Java thường được gọi là “modulo”, nhưng về mặt dấu nó là **remainder theo phép chia cắt về 0**. Với `-7 % 3`, bạn nhận `-1`, không nhận `2` như modulo Euclid dương.

Không dùng trực tiếp `negativeIndex % listSize` để lập chỉ số danh sách nếu `negativeIndex` có thể âm: kết quả âm gây `IndexOutOfBoundsException` khi truy cập phần tử.

## 4. `Math.floorDiv` và `Math.floorMod`: chia theo âm vô cực

`Math.floorDiv(a,b)` chọn **thương nguyên không lớn hơn thương thực** (floor). `Math.floorMod(a,b)` trả số dư tương ứng sao cho:

\[
a = \operatorname{floorDiv}(a,b) \times b
    + \operatorname{floorMod}(a,b)
\]

Với `b > 0`, `floorMod(a,b)` nằm trong `[0,b)`. Với `b < 0`, số dư không bằng 0 có dấu **âm**, trùng dấu số chia. Vì vậy không nên tuyên bố `floorMod` “luôn không âm”: điều này chỉ đúng khi **số chia dương**.

| `a` | `b` | `a / b` | `a % b` | `floorDiv(a,b)` | `floorMod(a,b)` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| `7` | `3` | `2` | `1` | `2` | `1` |
| `-7` | `3` | `-2` | `-1` | `-3` | `2` |
| `7` | `-3` | `-2` | `1` | `-3` | `-2` |
| `-7` | `-3` | `2` | `-1` | `2` | `-1` |
| `8` | `3` | `2` | `2` | `2` | `2` |
| `-8` | `3` | `-2` | `-2` | `-3` | `1` |
| `1` | `3` | `0` | `1` | `0` | `1` |
| `-1` | `3` | `0` | `-1` | `-1` | `2` |

Ví dụ ứng dụng: lập index vòng tròn độ dài 5 từ một vị trí có thể âm:

~~~java
int size = 5;
int negativeOffset = -1;

System.out.println(negativeOffset % size);             // -1 (không dùng làm index)
System.out.println(Math.floorMod(negativeOffset, size)); // 4 (index hợp lệ)
~~~

Nếu `size <= 0` thì phải kiểm tra dữ liệu trước: size = 0 gây chia cho 0; size âm không phải kích thước hợp lệ của list và không cho miền index mong muốn.

### Không đánh đồng `floorDiv` với cast của chia thực

Ví dụ `(int) Math.floor((double) a / b)` có thể mất chính xác khi `long` lớn do chuyển sang `double`. `Math.floorDiv(long,long)` làm việc trực tiếp trên số nguyên, không cần binary64 trung gian và thường là lựa chọn đúng cho chia nguyên theo floor.

## 5. Các tình huống biên: số 0, MIN_VALUE và chia cho 0

- Chia nguyên hoặc lấy dư nguyên với số chia bằng 0 ném `ArithmeticException`.
- `Math.floorDiv` và `Math.floorMod` với số chia 0 cũng ném `ArithmeticException`.
- `0 / -3 == 0` và `0 % -3 == 0`, vì số bị chia bằng 0.
- `Integer.MIN_VALUE / -1` không nằm trong miền `int` vì thương toán học bằng 2147483648; Java cho kết quả wrap về `Integer.MIN_VALUE`. `Math.floorDiv(Integer.MIN_VALUE, -1)` cũng trả `Integer.MIN_VALUE` tại trường hợp đặc biệt này.
- Với `Long.MIN_VALUE / -1L`, vấn đề tương tự xảy ra trong miền `long`.

~~~java
System.out.println(Integer.MIN_VALUE / -1);              // -2147483648
System.out.println(Math.floorDiv(Integer.MIN_VALUE, -1)); // -2147483648
System.out.println(Integer.MIN_VALUE % -1);              // 0
~~~

Nếu chương trình cần phát hiện overflow chính xác tại chia cho -1, phải kiểm tra riêng `MIN_VALUE` và số chia `-1` (hoặc chọn miền số lớn hơn), không trông chờ `/` hay `Math.floorDiv` tự ném lỗi overflow.

**Lưu ý về công thức:** biểu thức chứng minh đồng nhất `(a / b) * b + (a % b)` cũng có thể chịu wrap do các phép nhân/cộng `int` trong những trường hợp biên. Khi lập lập luận toán học về chia và số dư, cần xét giới hạn biểu diễn của phép tính thực tế.

## 6. Floating point: `%` không giống `Math.IEEEremainder`

Toán tử `%` hỗ trợ cả `float` và `double`. Về cơ bản, nó trả số dư theo thương bị cắt về 0; phần dư có thể là số âm và khác với phần dư IEEE 754 kiểu “nearest integral quotient”.

Ví dụ:

~~~java
System.out.println(-7.0 % 4.0);                  // -3.0
System.out.println(Math.IEEEremainder(-7.0, 4.0)); // 1.0
~~~

`Math.IEEEremainder(x,y)` dùng thương nguyên **gần nhất** với `x/y`; trường hợp thương nằm đúng giữa hai số nguyên thì chọn **thương chẵn**. Nó **không** phải phép toán modulo dương, cũng không phải bản thay thế trực tiếp cho `%`.

| Biểu thức | Kết quả | Vì sao |
| --- | ---: | --- |
| `-7.0 % 4.0` | `-3.0` | Thương -1 khi cắt về 0; số dư -3 |
| `Math.IEEEremainder(-7.0,4.0)` | `1.0` | Thương gần nhất -2; dư +1 |
| `Math.IEEEremainder(6.0,4.0)` | `-2.0` | Thương 1.5, tie chọn thương chẵn 2 |
| `Math.IEEEremainder(-6.0,4.0)` | `2.0` | Thương -1.5, tie chọn thương chẵn -2 |

Phép chia floating point cho 0, các biểu thức với `NaN`, `±Infinity` và **âm 0.0** có những ngữ nghĩa IEEE đặc biệt. Chẳng hạn `-7.0 / 0.0` tạo vô cực âm và `-7.0 % 0.0` là `NaN`; ngược lại, phép chia nguyên cho 0 ném exception. Không tự viết logic xử lý chung cho hai kiểu số mà bỏ qua khác biệt này.

## 7. `Math.floor`, `Math.ceil`, `Math.round`, `Math.rint` với số âm

Cần tách **hướng trên trục số** khỏi “tăng/giảm độ lớn tuyệt đối”:

- `Math.floor(x)`: trả số nguyên gần nhất **không lớn hơn x**, ở dạng `double`.
- `Math.ceil(x)`: trả số nguyên gần nhất **không nhỏ hơn x**, ở dạng `double`.
- `Math.round(float)`: làm tròn thành `int` gần nhất, điểm giữa theo hướng **+∞**.
- `Math.round(double)`: làm tròn thành `long` gần nhất, điểm giữa theo hướng **+∞**.
- `Math.rint(double)`: trả `double` mang giá trị nguyên gần nhất; điểm giữa chọn giá trị **chẵn**.

Bảng với những số âm quan trọng:

| `x` | `Math.floor(x)` | `Math.ceil(x)` | `Math.round(x)` với `x` là `double` | `Math.rint(x)` |
| ---: | ---: | ---: | ---: | ---: |
| `-2.1` | `-3.0` | `-2.0` | `-2` | `-2.0` |
| `-2.4` | `-3.0` | `-2.0` | `-2` | `-2.0` |
| `-2.5` | `-3.0` | `-2.0` | `-2` | `-2.0` |
| `-2.6` | `-3.0` | `-2.0` | `-3` | `-3.0` |
| `-3.5` | `-4.0` | `-3.0` | `-3` | `-4.0` |
| `2.5` | `2.0` | `3.0` | `3` | `2.0` |

Đặc biệt:

~~~java
System.out.println(Math.round(-2.5)); // -2
System.out.println(Math.round(-3.5)); // -3
System.out.println(Math.rint(-2.5));  // -2.0
System.out.println(Math.rint(-3.5));  // -4.0
~~~

`Math.round(-2.5)` **không** làm tròn ra xa 0. Nếu muốn “điểm giữa thì xa 0”, hãy xem `BigDecimal` cùng `RoundingMode.HALF_UP` hoặc xây dựng quy tắc rõ ràng cho miền số cần dùng.

`Math.round` còn có các quy tắc riêng cho `NaN`, vô cực và giá trị ngoài miền `int`/`long` (có thể bão hòa ở biên). Đây không phải phép toán kiểm tra overflow tổng quát; tránh đưa kết quả `Math.round` thành bằng chứng rằng mọi số có thể được biểu diễn chính xác bằng kiểu nguyên đích.

## 8. `BigDecimal`: làm tròn thập phân chính xác với `RoundingMode`

Với giá trị decimal cần làm tròn theo chính sách nghiệp vụ, `BigDecimal` hữu ích vì biểu diễn chuỗi thập phân **chính xác**, không bắt buộc chuyển qua `double`.

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal negative = new BigDecimal("-2.5");

System.out.println(negative.setScale(0, RoundingMode.HALF_UP));   // -3
System.out.println(negative.setScale(0, RoundingMode.HALF_DOWN)); // -2
System.out.println(negative.setScale(0, RoundingMode.HALF_EVEN)); // -2
~~~

### Bảng đủ tám chế độ với số âm

Các ví dụ dùng `new BigDecimal(value).setScale(0, mode)`, tức làm tròn về **hàng đơn vị**. `-2.4` không nằm giữa hai số nguyên; `-2.5` và `-3.5` nằm đúng giữa.

| `RoundingMode` | `-2.4` | `-2.5` | `-3.5` | `+2.5` | Quy tắc |
| --- | ---: | ---: | ---: | ---: | --- |
| `UP` | `-3` | `-3` | `-4` | `3` | Xa số 0 nếu cần loại chữ số |
| `DOWN` | `-2` | `-2` | `-3` | `2` | Về số 0 |
| `CEILING` | `-2` | `-2` | `-3` | `3` | Về dương vô cực |
| `FLOOR` | `-3` | `-3` | `-4` | `2` | Về âm vô cực |
| `HALF_UP` | `-2` | `-3` | `-4` | `3` | Gần nhất; điểm giữa xa 0 |
| `HALF_DOWN` | `-2` | `-2` | `-3` | `2` | Gần nhất; điểm giữa về 0 |
| `HALF_EVEN` | `-2` | `-2` | `-4` | `2` | Gần nhất; điểm giữa chọn chữ số được giữ là chẵn |
| `UNNECESSARY` | lỗi | lỗi | lỗi | lỗi | Cấm làm tròn mất giá trị |

“lỗi” ở `UNNECESSARY` nghĩa là `ArithmeticException` vì bốn đầu vào đều cần loại phần thập phân khác 0. `UNNECESSARY` vẫn hợp lệ nếu việc đổi scale không mất giá trị:

~~~java
System.out.println(new BigDecimal("-2.00")
        .setScale(0, RoundingMode.UNNECESSARY)); // -2
~~~

**Phân biệt bốn hướng:** với `-2.4`, `UP=-3` và `FLOOR=-3`; `DOWN=-2` và `CEILING=-2`. Nhưng sự trùng hợp này không còn đúng nếu đổi sang số dương: `UP(2.4)=3`, `FLOOR(2.4)=2`.

### Half-even không có nghĩa “mọi số âm đều về 0”

`HALF_EVEN(-2.5)=-2` nhưng `HALF_EVEN(-3.5)=-4`. Chỉ khi **hai ứng viên có khoảng cách bằng nhau** mới xét chữ số cuối của ứng viên nào là chẵn. Ở giá trị `-2.6`, mọi chế độ `HALF_*` chọn `-3` vì đó là ứng viên gần hơn.

## 9. `BigDecimal`: thương nguyên và remainder của số âm

`BigDecimal` **không hỗ trợ** toán tử `/` hoặc `%` trực tiếp giữa hai object. Thay vào đó:

- `a.divideToIntegralValue(b)`: phần **nguyên** của thương, theo hướng về 0.
- `a.remainder(b)`: phần còn lại sau phép chia dùng thương nguyên theo hướng về 0.
- `a.divideAndRemainder(b)`: trả đồng thời thương nguyên và dư.
- `a.divide(b, scale, mode)`: thương thập phân với scale và cách làm tròn đã chỉ định.

Ví dụ:

~~~java
BigDecimal a = new BigDecimal("-7");
BigDecimal b = new BigDecimal("3");

System.out.println(a.divideToIntegralValue(b).toPlainString()); // -2
System.out.println(a.remainder(b).toPlainString());              // -1
System.out.println(a.divide(b, 2, RoundingMode.HALF_UP));        // -2.33
~~~

Đặc biệt, `BigDecimal.remainder` không tự cung cấp modulo dương. Với `-7` và `3`, dư là `-1` giống dấu số bị chia. Nếu muốn một modulo trong khoảng không âm cho modulus dương `m`, cần quy định rõ và xây hàm riêng, ví dụ dùng `BigInteger.mod` cho số nguyên tùy ý.

`BigInteger` có cả `remainder` và `mod`, nhưng hai phương thức có ý nghĩa khác nhau:

~~~java
import java.math.BigInteger;

BigInteger x = BigInteger.valueOf(-7);
BigInteger m = BigInteger.valueOf(3);

System.out.println(x.remainder(m)); // -1
System.out.println(x.mod(m));       // 2
~~~

`BigInteger.mod` yêu cầu modulus **dương**, và ném `ArithmeticException` nếu modulus không dương. Khi hệ thống định nghĩa một vòng quay thời gian/index dương, `Math.floorMod` (kiểu nguyên) hoặc `BigInteger.mod` thường phù hợp hơn remainder có dấu.

## 10. Chương trình Java 17+ tra bảng và kiểm chứng

Tạo tệp `NegativeDivisionRoundingTable.java`. Chương trình in bảng chia nguyên/dư, các chế độ làm tròn thập phân và đối chiếu những phép Math quan trọng.

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NegativeDivisionRoundingTable {

    static String rounded(String raw, RoundingMode mode) {
        try {
            return new BigDecimal(raw).setScale(0, mode).toPlainString();
        } catch (ArithmeticException ex) {
            return "ERR";
        }
    }

    public static void main(String[] args) {
        System.out.println("a b / % floorDiv floorMod");
        int[][] values = {
                { 7, 3 }, { -7, 3 }, { 7, -3 }, { -7, -3 },
                { 8, 3 }, { -8, 3 }, { 1, 3 }, { -1, 3 }
        };
        for (int[] pair : values) {
            int a = pair[0];
            int b = pair[1];
            System.out.printf("%d %d %d %d %d %d%n",
                    a, b, a / b, a % b,
                    Math.floorDiv(a, b), Math.floorMod(a, b));
        }

        System.out.println("mode -2.4 -2.5 -3.5");
        for (RoundingMode mode : RoundingMode.values()) {
            System.out.printf("%s %s %s %s%n",
                    mode,
                    rounded("-2.4", mode),
                    rounded("-2.5", mode),
                    rounded("-3.5", mode));
        }

        System.out.println("round(-2.5)=" + Math.round(-2.5));
        System.out.println("round(-3.5)=" + Math.round(-3.5));
        System.out.println("rint(-3.5)=" + Math.rint(-3.5));
        System.out.println("floor(-2.4)=" + Math.floor(-2.4));
        System.out.println("ceil(-2.4)=" + Math.ceil(-2.4));

        BigDecimal a = new BigDecimal("-7");
        BigDecimal b = new BigDecimal("3");
        System.out.println("BigDecimal quotient="
                + a.divideToIntegralValue(b).toPlainString());
        System.out.println("BigDecimal remainder="
                + a.remainder(b).toPlainString());
        System.out.println("BigDecimal rounded quotient="
                + a.divide(b, 2, RoundingMode.HALF_UP).toPlainString());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NegativeDivisionRoundingTable.java
java NegativeDivisionRoundingTable
~~~

Stdout:

~~~text
a b / % floorDiv floorMod
7 3 2 1 2 1
-7 3 -2 -1 -3 2
7 -3 -2 1 -3 -2
-7 -3 2 -1 2 -1
8 3 2 2 2 2
-8 3 -2 -2 -3 1
1 3 0 1 0 1
-1 3 0 -1 -1 2
mode -2.4 -2.5 -3.5
UP -3 -3 -4
DOWN -2 -2 -3
CEILING -2 -2 -3
FLOOR -3 -3 -4
HALF_UP -2 -3 -4
HALF_DOWN -2 -2 -3
HALF_EVEN -2 -2 -4
UNNECESSARY ERR ERR ERR
round(-2.5)=-2
round(-3.5)=-3
rint(-3.5)=-4.0
floor(-2.4)=-3.0
ceil(-2.4)=-2.0
BigDecimal quotient=-2
BigDecimal remainder=-1
BigDecimal rounded quotient=-2.33
~~~

Dòng `UNNECESSARY ERR ERR ERR` biểu diễn **những trường hợp bị từ chối làm tròn**. Trong chương trình thực tế, đừng bỏ qua hoàn toàn ngoại lệ: nên trả lỗi validation theo hợp đồng, hoặc để ngoại lệ đi lên nơi có trách nhiệm xử lý. `rounded` bắt ngoại lệ chỉ để bài học có bảng đầu ra thống nhất.

## 11. Hướng dẫn chọn phép toán theo tình huống

| Bài toán | Chọn | Vì sao |
| --- | --- | --- |
| Cắt phần lẻ của thương nguyên | `/` | Hướng về 0 |
| Dư đi kèm phép chia nguyên thông thường | `%` | `a = (a/b)*b + a%b` |
| Chia số nguyên thành bucket có thể âm | `Math.floorDiv` | Bucket theo floor, nhất quán với trục số |
| Quay vòng index với kích thước dương | `Math.floorMod` | Dư không âm khi modulus dương |
| Làm tròn xuống về số nguyên theo trục số | `Math.floor` | Với số âm đi xa 0 nếu có phần lẻ |
| Làm tròn lên về số nguyên theo trục số | `Math.ceil` | Với số âm tiến về 0 nếu có phần lẻ |
| Gần nhất, tie thiên về +∞ theo `Math.round` | `Math.round` | Khác HALF_UP ở số âm tie |
| Gần nhất, tie chọn chẵn ở floating point | `Math.rint` | Kết quả kiểu `double` |
| Làm tròn số tiền/decimal đến số chữ số xác định | `BigDecimal.setScale` | Chọn chính sách `RoundingMode` rõ ràng |
| Thương nguyên và dư BigDecimal | `divideToIntegralValue`, `remainder` | Ngữ nghĩa cắt về 0 |
| Modulo dương với số nguyên rất lớn | `BigInteger.mod` | Yêu cầu modulus dương |

**Ví dụ lập nhóm thời gian:** nếu mỗi nhóm ứng với 10 giây theo mốc 0 và một sự kiện có offset -1 giây, `-1 / 10 == 0` sẽ đưa sự kiện vào nhóm 0 theo truncation, trong khi `Math.floorDiv(-1,10) == -1` đưa vào nhóm âm đúng theo khoảng `[-10,0)`. Khi định nghĩa các khoảng liên tiếp theo floor, `floorDiv` tránh lỗi quanh mốc 0.

**Ví dụ số dư kế toán:** `-7 % 3 == -1` là số dư mang dấu của số bị chia; nếu quy tắc nghiệp vụ cần “số bù còn thiếu để đạt bội số” phải định nghĩa một đại lượng khác, không chỉ đổi dấu tùy tiện hoặc `Math.abs(remainder)`.

## 12. Sai lầm thường gặp

1. **Gọi `/` là floor division.** Với `-7/3`, Java ra `-2`, không phải `-3`.
2. **Cho rằng `%` luôn không âm.** `-7%3` ra `-1`.
3. **Cho rằng `floorMod` luôn không âm.** Điều này chỉ bảo đảm với **modulus dương**; `floorMod(7,-3)=-2`.
4. **Dùng cast sang `int` để làm floor.** `(int)-2.7 == -2` vì cast cắt về 0; `Math.floor(-2.7)=-3.0`.
5. **Dùng `Math.round` như `RoundingMode.HALF_UP`.** `Math.round(-2.5)=-2` nhưng `BigDecimal("-2.5").setScale(0, HALF_UP)=-3`.
6. **Đánh đồng `BigDecimal.remainder` và modulo không âm.** `BigDecimal(-7).remainder(3)` vẫn là `-1`.
7. **Quên kiểu của kết quả.** `Math.floor` và `Math.ceil` trả `double`, `Math.round(float)` trả `int`, `Math.round(double)` trả `long`, còn `BigDecimal.setScale` trả `BigDecimal`.
8. **Chia nguyên rồi muốn khôi phục phần lẻ bằng cast.** `(double)(-7/3)` vẫn là `-2.0`.
9. **Bỏ qua trường hợp chia cho 0 và MIN_VALUE/-1.** Các trường hợp này cần kiểm thử riêng; trong đó MIN_VALUE/-1 có thể wrap thay vì exception.
10. **Dùng `double` để làm tròn số tiền chính xác.** Một số decimal như 0.1 không được biểu diễn chính xác trong binary64; hãy dùng `new BigDecimal("...")` và chính sách `RoundingMode` theo yêu cầu nghiệp vụ.

## 13. Bài tập tra bảng

Không chạy code trước; điền kiểu và kết quả theo nguyên tắc rồi dùng chương trình nhỏ để kiểm tra:

| STT | Biểu thức | Kết quả dự đoán |
| ---: | --- | --- |
| 1 | `-11 / 4` | ? |
| 2 | `-11 % 4` | ? |
| 3 | `Math.floorDiv(-11, 4)` | ? |
| 4 | `Math.floorMod(-11, 4)` | ? |
| 5 | `11 % -4` | ? |
| 6 | `Math.floorMod(11, -4)` | ? |
| 7 | `(int) -2.9` | ? |
| 8 | `Math.floor(-2.9)` | ? |
| 9 | `Math.ceil(-2.9)` | ? |
| 10 | `Math.round(-2.5)` | ? |
| 11 | `Math.rint(-2.5)` | ? |
| 12 | `new BigDecimal("-2.5").setScale(0, HALF_UP)` | ? |
| 13 | `new BigDecimal("-2.5").setScale(0, HALF_EVEN)` | ? |
| 14 | `new BigInteger("-11").mod(new BigInteger("4"))` | ? |

Đáp án (ở các câu BigDecimal/BigInteger, cần import kiểu và enum):

| STT | Kết quả | Giải thích |
| ---: | --- | --- |
| 1 | `-2` (`int`) | Thương nguyên cắt về 0 |
| 2 | `-3` (`int`) | `-11 = (-2)×4 - 3` |
| 3 | `-3` (`int`) | Floor của -2.75 |
| 4 | `1` (`int`) | `-11 = (-3)×4 + 1` |
| 5 | `3` (`int`) | Dư `%` cùng dấu số bị chia 11 |
| 6 | `-1` (`int`) | Với modulus âm, `floorMod` trả dư âm |
| 7 | `-2` (`int`) | Cast floating point về int cắt về 0 |
| 8 | `-3.0` (`double`) | Floor theo âm vô cực |
| 9 | `-2.0` (`double`) | Ceiling theo dương vô cực |
| 10 | `-2` (`long`) | `Math.round(double)` tie về +∞ |
| 11 | `-2.0` (`double`) | Tie chọn số chẵn |
| 12 | `-3` (`BigDecimal`) | HALF_UP tie xa số 0 |
| 13 | `-2` (`BigDecimal`) | HALF_EVEN chọn 2 chẵn |
| 14 | `1` (`BigInteger`) | Modulo dương trong `[0,4)` |

**Tóm tắt dễ nhớ:** `/` và `%` trên số nguyên là **truncation toward zero**; `floorDiv`/`floorMod` là **floor-based division**; `Math.floor`, `Math.ceil`, `Math.round`, `Math.rint` có các chính sách khác nhau với số âm; `BigDecimal` cho phép chỉ định rõ mode và scale. Khi viết code xử lý số âm, chọn chính sách theo định nghĩa toán học/kinh doanh, không chọn chỉ vì tên method nghe có vẻ đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-004">Bảng NaN, signed zero, equals và compare</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu: NaN, signed zero, equals và compare trong Java

## 1. Mục tiêu: phân biệt bốn khái niệm “bằng nhau”

Trong Java, các kiểu dấu phẩy động `float` và `double` tuân theo ngữ nghĩa IEEE 754 được Java quy định. Một số giá trị đặc biệt có cách so sánh khác trực giác số học thông thường:

- **NaN (Not a Number)**: giá trị đặc biệt biểu thị kết quả không xác định theo phép toán floating-point, ví dụ `0.0 / 0.0`.
- **Signed zero**: có cả `+0.0` và `-0.0`. Hai giá trị bằng nhau qua toán tử số học `==`, nhưng không giống nhau ở mọi thao tác, ví dụ `1.0 / +0.0` và `1.0 / -0.0`.
- **`==`, `!=`, `<`, `>`, `<=`, `>=` trên primitive**: áp dụng quy tắc **so sánh số học**, trong đó NaN không được xem là bằng bất kỳ số nào, kể cả chính nó.
- **`Double.equals` / `Float.equals` và `Double.compare` / `Float.compare`**: sử dụng **quy tắc bình đẳng và thứ tự dành cho wrapper**, được thiết kế nhất quán để dùng làm khóa trong collection và sắp xếp.

Vì vậy, cùng nhìn vào hai giá trị `double` mà lựa chọn API khác nhau có thể cho kết quả khác nhau. Đây không phải lỗi của Java; đó là những **hợp đồng so sánh khác nhau**.

Bài này là bảng tra cứu cho Java 17+, kèm ví dụ chạy độc lập, các trường hợp NaN với payload khác nhau, signed zero, hash/sorted collections và cách chọn phép so sánh theo bài toán.

## 2. Bảng nhanh: toán tử primitive và wrapper

Giả sử:

~~~java
double nan = Double.NaN;
double positiveZero = 0.0;
double negativeZero = -0.0;
~~~

Đây là các khai báo **trong thân một phương thức**; chương trình hoàn chỉnh nằm ở phần 8.

| Phép kiểm tra | Kết quả Java | Lý do |
|---|---|---|
| `Double.NaN == Double.NaN` | `false` | NaN không bằng bất kỳ giá trị nào bằng primitive `==` |
| `Double.NaN != Double.NaN` | `true` | Phép `!=` có kết quả true nếu có NaN |
| `Double.NaN < 1.0` | `false` | Quan hệ thứ tự primitive với NaN là unordered |
| `Double.NaN > 1.0` | `false` | Tương tự |
| `Double.NaN <= 1.0` | `false` | Tương tự |
| `Double.NaN >= 1.0` | `false` | Tương tự |
| `Double.NaN == Double.POSITIVE_INFINITY` | `false` | Infinity là giá trị riêng, không phải NaN |
| `0.0 == -0.0` | `true` | Hai zero bằng nhau theo so sánh số học |
| `0.0 < -0.0` | `false` | Không có chênh lệch giá trị số học |
| `0.0 > -0.0` | `false` | Tương tự |
| `Double.valueOf(nan).equals(Double.valueOf(nan))` | `true` | Wrapper xem các NaN là bằng nhau |
| `Double.valueOf(-0.0).equals(Double.valueOf(0.0))` | `false` | Wrapper phân biệt bit dấu của zero |
| `Double.compare(nan, nan)` | `0` | Hai NaN cùng vị trí trong thứ tự wrapper |
| `Double.compare(nan, Double.POSITIVE_INFINITY)` | Số dương (`1` ở JDK 17) | NaN xếp **sau** mọi giá trị số, kể cả +Infinity |
| `Double.compare(-0.0, +0.0)` | Số âm (`-1` ở JDK 17) | `-0.0` xếp trước `+0.0` |
| `Double.compare(+0.0, -0.0)` | Số dương (`1` ở JDK 17) | `+0.0` xếp sau `-0.0` |

**Mẹo nhớ:** nếu cần so sánh giá trị dấu phẩy động theo toán tử số học, `==` và `<` có quy tắc về NaN/zero riêng. Nếu cần một **thứ tự toàn phần để sắp xếp**, `Double.compare` và `Float.compare` là API phù hợp.

Các ví dụ trong bảng dùng `Double.NaN` kiểu primitive `double`, không phải đối tượng `Double`. Sự khác nhau giữa primitive và wrapper trở nên rõ trong những đoạn tiếp theo.

## 3. NaN và toán tử primitive

NaN có thể hình thành từ phép toán dấu phẩy động không xác định:

~~~java
double undefined = 0.0 / 0.0;
double infinity = 1.0 / 0.0;

System.out.println(undefined); // NaN
System.out.println(infinity);  // Infinity
~~~

Không nên suy luận NaN như một số lớn hoặc nhỏ. Khi ít nhất một toán hạng là NaN:

- `==` trả `false`, kể cả khi biểu thức so sánh NaN với chính NaN.
- `!=` trả `true`.
- Các phép `<`, `<=`, `>`, `>=` đều trả `false`.
- Do đó hai điều kiện `!(a < b)` và `a >= b` **không tương đương** khi có NaN: với `a = NaN`, `!(a < b)` là true trong khi `a >= b` false.

Ví dụ:

~~~java
double a = Double.NaN;
double b = 10.0;

System.out.println(a == a);  // false
System.out.println(a != a);  // true
System.out.println(a < b);   // false
System.out.println(a >= b);  // false
System.out.println(!(a < b)); // true
~~~

**Không phát hiện NaN bằng `x == Double.NaN`.** Điều kiện đó luôn false cho mọi giá trị x, kể cả NaN. Dùng:

~~~java
boolean invalid = Double.isNaN(a);
~~~

Với `float`, dùng `Float.isNaN(value)`. Nếu cần loại bỏ mọi giá trị không hữu hạn (cả NaN lẫn ±Infinity), dùng `Double.isFinite(value)` hoặc `Float.isFinite(value)` (có trong Java 17).

### Infinity không phải NaN

`Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` là các giá trị có thứ tự số học:

~~~java
System.out.println(Double.NEGATIVE_INFINITY < -1e300); // true
System.out.println(Double.POSITIVE_INFINITY > 1e300);  // true
System.out.println(Double.isNaN(Double.POSITIVE_INFINITY)); // false
System.out.println(Double.isInfinite(Double.POSITIVE_INFINITY)); // true
~~~

Với kiểu `double`, phép chia `1.0 / 0.0` trả +Infinity và `-1.0 / 0.0` trả -Infinity theo quy tắc floating-point. Nhưng phép chia **số nguyên** `1 / 0` hoặc `int x / intZero` ném `ArithmeticException`; đừng gom hai trường hợp làm một.

Nếu dữ liệu nhập phải là số hữu hạn, việc kiểm tra `!Double.isNaN(x)` **chưa đủ**: Infinity không phải NaN. Quy tắc hữu ích:

~~~java
if (!Double.isFinite(x)) {
    throw new IllegalArgumentException("Value must be finite");
}
~~~

Đây là đoạn trong một method có biến `x` kiểu double.

## 4. Signed zero: vì sao 0.0 bằng -0.0 nhưng không hoàn toàn giống nhau?

IEEE 754 có hai biểu diễn zero:

- `+0.0`: bit dấu bằng 0.
- `-0.0`: bit dấu bằng 1.

Toán tử `==` trên primitive coi chúng **bằng nhau**, vì cùng giá trị zero theo phép so sánh số học:

~~~java
System.out.println(0.0 == -0.0); // true
System.out.println(-0.0 < +0.0);  // false
~~~

Nhưng trong một số phép toán, dấu zero ảnh hưởng kết quả:

~~~java
System.out.println(1.0 / +0.0); // Infinity
System.out.println(1.0 / -0.0); // -Infinity

System.out.println(Math.min(-0.0, +0.0)); // -0.0
System.out.println(Math.max(-0.0, +0.0)); // 0.0
~~~

`Math.min` và `Math.max` cho `double` có quy tắc dấu zero: `-0.0` được xem là nhỏ hơn `+0.0` khi hai đối số đều là zero khác dấu. Nếu một toán hạng là NaN, kết quả của hai hàm này là NaN theo API Java. Vì vậy đừng sử dụng min/max như một cách để **loại NaN** khỏi dữ liệu.

Biểu diễn chuỗi cũng có thể giữ dấu:

~~~java
System.out.println(Double.toString(-0.0)); // -0.0
System.out.println(Double.toString(+0.0)); // 0.0
~~~

Nếu bài toán chỉ cần biết hai giá trị số học có bằng nhau không, so sánh `+0.0 == -0.0` là true. Nếu bài toán có ý nghĩa phân biệt dấu zero (ví dụ kết quả tính 1/x, serialization theo bit, kiểm tra chính xác đầu ra), không được mặc định hai biểu diễn hoàn toàn thay thế cho nhau.

### Bit pattern của hai zero

~~~java
long positiveBits = Double.doubleToRawLongBits(+0.0);
long negativeBits = Double.doubleToRawLongBits(-0.0);
System.out.println(positiveBits == negativeBits); // false
~~~

Trên dạng IEEE 754 binary64 của Java, bit sign khác nhau. `Double.doubleToLongBits` cũng giữ sự khác nhau này cho ±0.0.

**Chú ý:** chữ “signed zero” không có nghĩa rằng `-0.0 < 0.0` trong mọi API. Toán tử primitive `<` trả false, nhưng `Double.compare(-0.0, +0.0) < 0` là true theo thứ tự wrapper được định nghĩa riêng.

## 5. Double.equals và Double.compare: khác toán tử == ra sao?

### 5.1. Double.equals

`Double.equals(Object)` so sánh hai **đối tượng Double** theo bit biểu diễn, với NaN được canonicalize. Kết quả khác so sánh primitive ở hai chỗ quan trọng:

| Cặp giá trị | Primitive `a == b` | `Double.valueOf(a).equals(Double.valueOf(b))` |
|---|---|---|
| `1.0` và `1.0` | true | true |
| `1.0` và `2.0` | false | false |
| `+0.0` và `-0.0` | **true** | **false** |
| `NaN` và `NaN` | **false** | **true** |
| `NaN` và `+Infinity` | false | false |
| `+Infinity` và `+Infinity` | true | true |
| `-Infinity` và `-Infinity` | true | true |

Định nghĩa này giúp `Double` dùng được với `equals`/`hashCode` trong `HashMap`, `HashSet` và các collection thông thường. Hai giá trị Double mà `equals` xem là bằng nhau có `hashCode` bằng nhau.

**Không dùng `==` để so sánh hai đối tượng `Double` nếu bạn cần equality giá trị.** Khi cả hai toán hạng là reference `Double`, toán tử `==` so sánh **tham chiếu**. Khi một bên bị unbox sang primitive, hành vi lại chuyển sang so sánh số học và có thể phát sinh `NullPointerException`.

~~~java
Double left = Double.valueOf(1.5);
Double right = Double.valueOf(1.5);

System.out.println(left.equals(right)); // true
// left == right: không dùng để suy ra value-equality.
~~~

Nếu hai reference `Double` có thể null, dùng `Objects.equals(left, right)` khi muốn quy tắc equality wrapper có xử lý null; nếu null không hợp lệ, kiểm tra đầu vào rồi dùng `left.equals(right)`. `Objects.equals` **không chuyển sang primitive `==`**, do đó vẫn phân biệt signed zero và coi NaN bằng NaN.

### 5.2. Double.compare và compareTo

`Double.compare(double a, double b)` và `Double.compareTo(Double other)` xây dựng **thứ tự toàn phần** để sắp xếp, cho phép đặt NaN vào một vị trí xác định:

~~~text
-Infinity < số âm hữu hạn < -0.0 < +0.0
          < số dương hữu hạn < +Infinity < NaN
~~~

Đó là thứ tự quan trọng nhất cần nhớ. Mọi NaN được đặt bằng nhau **theo compare**, dù có payload bit khác nhau. Dù có kết quả là số âm/0/số dương, không nên viết điều kiện dựa trên `compare(...) == -1` hay `== 1`; hợp đồng của các API compare nói chung chỉ yêu cầu dấu, dùng `< 0`, `== 0`, `> 0`.

Ví dụ:

~~~java
System.out.println(Double.compare(-0.0, +0.0)); // -1
System.out.println(Double.compare(+0.0, -0.0)); // 1
System.out.println(Double.compare(Double.NaN, Double.NaN)); // 0
System.out.println(Double.compare(
        Double.NaN, Double.POSITIVE_INFINITY)); // 1
~~~

**Quy tắc bao trùm:** với hai giá trị primitive double a, b (kể cả NaN, ±0.0), biểu thức `Double.compare(a,b) == 0` nhất quán với `Double.valueOf(a).equals(Double.valueOf(b))`. Nhưng chúng **không nhất quán với primitive `a == b`** tại NaN và signed zero.

### 5.3. Float có quy tắc tương ứng

`Float.equals`, `Float.compare` và `Float.compareTo` tuân theo cách xử lý NaN và signed zero tương tự, nhưng trên bit-width **binary32**, khác độ chính xác và miền giá trị `double` (binary64).

~~~java
System.out.println(Float.NaN == Float.NaN); // false
System.out.println(Float.compare(Float.NaN, Float.NaN)); // 0
System.out.println(Float.compare(-0.0f, +0.0f)); // -1
System.out.println(Float.valueOf(-0.0f).equals(+0.0f)); // false
~~~

Nếu đối tượng wrapper khác lớp, `Double.valueOf(1.0).equals(Float.valueOf(1.0f))` là **false**: `Double.equals` chỉ nhận đối tượng kiểu Double, không tự quy đổi Float sang Double để so sánh.

## 6. Bảng lựa chọn API theo mục đích

| Mục đích | Cách nên chọn | Ý nghĩa cần nắm |
|---|---|---|
| Kiểm tra hai primitive bằng nhau theo phép toán dấu phẩy động | `a == b` | NaN khác mọi giá trị, ±0.0 bằng nhau |
| Kiểm tra hai primitive khác nhau | `a != b` | True khi một bên NaN, kể cả hai bên NaN |
| Loại bỏ NaN | `Double.isNaN(a)` | Không dùng `a == Double.NaN` |
| Từ chối cả NaN và Infinity | `Double.isFinite(a)` | Hữu hạn mới được chấp nhận |
| Kiểm tra wrapper value-equality | `Double.valueOf(a).equals(Double.valueOf(b))` | NaN bằng NaN, ±0.0 khác nhau |
| Kiểm tra wrapper với khả năng null | `Objects.equals(left, right)` | Áp dụng equals của wrapper, null-safe |
| Sắp xếp một cột double | `Double.compare(a,b)` | Thứ tự tổng thể, NaN sau +Infinity |
| So sánh theo hợp đồng SortedSet/SortedMap dùng Double | `Double.compare` hoặc thứ tự tự nhiên | Trùng khóa khi compare == 0 |
| Phân biệt raw bit/payload của NaN | `Double.doubleToRawLongBits(x)` | Không dùng equals/compare để giữ payload |
| Kiểm tra giá trị gần bằng theo dung sai | Một predicate riêng với sai số rõ ràng | Không biến thành equals/hashCode/order |
| Sắp xếp theo “NaN trước” hoặc “NaN là missing” | Comparator/policy riêng | Phải giữ thứ tự bắc cầu và nhất quán |
| Tính toán tiền/độ chính xác thập phân | BigDecimal hoặc đơn vị nguyên | Không kỳ vọng primitive double luôn chính xác |

Đối với dữ liệu đầu vào bên ngoài, việc định nghĩa **chính sách NaN/Infinity** trước khi so sánh quan trọng hơn chọn đúng một toán tử. Ví dụ khi giá trị NaN nghĩa là “không đo được”, nghiệp vụ có thể muốn từ chối, đưa cuối danh sách hoặc xử lý thiếu dữ liệu; những chính sách này là khác nhau.

## 7. NaN payload, bit chuẩn hóa và hashCode

NaN không chỉ có một raw bit pattern duy nhất. Trong biểu diễn IEEE 754 có nhiều cách biểu diễn NaN khác nhau; Java cho phép quan sát raw bits từ một giá trị `double` bằng:

~~~java
long raw = Double.doubleToRawLongBits(value);
~~~

Một phương thức khác:

~~~java
long normalized = Double.doubleToLongBits(value);
~~~

`Double.doubleToLongBits` **canonicalize các NaN**: mọi NaN cho cùng mẫu bit NaN chuẩn khi gọi phương thức này. `Double.equals` sử dụng ngữ nghĩa tương thích với kết quả `doubleToLongBits`, nhờ vậy **hai NaN khác raw payload vẫn equal**. `Double.hashCode` cũng thống nhất với equality này.

Ví dụ hai payload khác nhau:

~~~java
double a = Double.longBitsToDouble(0x7ff8000000000001L);
double b = Double.longBitsToDouble(0x7ff8000000000002L);

System.out.println(Double.isNaN(a) && Double.isNaN(b)); // true
System.out.println(Double.doubleToRawLongBits(a)
        != Double.doubleToRawLongBits(b)); // true
System.out.println(Double.valueOf(a).equals(b)); // true
System.out.println(Double.compare(a, b));        // 0
~~~

Các raw bit pattern trên được tạo và đọc trong cùng chương trình Java 17 theo API. Tuy vậy, **đừng dùng payload NaN như kênh lưu metadata lâu dài** mà không có hợp đồng serialization và platform rõ ràng: các phép toán hay đường truyền/định dạng có thể canonicalize hoặc không bảo toàn raw payload. Nếu cần lưu **chính xác bit**, làm việc trực tiếp với raw bit integer theo format cụ thể, không dựa vào phép toán trên double.

`Double.doubleToRawLongBits` và `Double.doubleToLongBits` đều giữ được khác biệt raw signed-zero:

~~~java
System.out.println(
    Double.doubleToLongBits(-0.0)
        == Double.doubleToLongBits(+0.0)); // false
~~~

Với `Float` có cặp API tương ứng `Float.floatToRawIntBits` và `Float.floatToIntBits`. Khi số là NaN, `Float.floatToIntBits` canonicalize tương tự.

## 8. Chương trình Java 17+ hoàn chỉnh và output chính xác

Lưu file **`FloatingCompareTableDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Set;

public class FloatingCompareTableDemo {
    public static void main(String[] args) {
        double nan = Double.NaN;
        double posZero = 0.0d;
        double negZero = -0.0d;

        System.out.println("primitive NaN == NaN: " + (nan == nan));
        System.out.println("primitive NaN != NaN: " + (nan != nan));
        System.out.println("primitive NaN < 1.0: " + (nan < 1.0));
        System.out.println("primitive NaN >= 1.0: " + (nan >= 1.0));
        System.out.println("primitive -0.0 == +0.0: "
                + (negZero == posZero));

        System.out.println("Double.equals(NaN, NaN): " +
                Double.valueOf(nan).equals(Double.valueOf(nan)));
        System.out.println("Double.compare(NaN, NaN): " +
                Double.compare(nan, nan));
        System.out.println("Double.compare(NaN, +Infinity): " +
                Double.compare(nan, Double.POSITIVE_INFINITY));
        System.out.println("Double.equals(-0.0, +0.0): " +
                Double.valueOf(negZero).equals(Double.valueOf(posZero)));
        System.out.println("Double.compare(-0.0, +0.0): " +
                Double.compare(negZero, posZero));
        System.out.println("Double.compare(+0.0, -0.0): " +
                Double.compare(posZero, negZero));

        System.out.println("1.0 / +0.0: " + (1.0 / posZero));
        System.out.println("1.0 / -0.0: " + (1.0 / negZero));
        System.out.println("Math.min(-0.0, +0.0): " +
                Math.min(negZero, posZero));
        System.out.println("Math.max(-0.0, +0.0): " +
                Math.max(negZero, posZero));

        System.out.println("isNaN(NaN): " + Double.isNaN(nan));
        System.out.println("isFinite(+Infinity): " +
                Double.isFinite(Double.POSITIVE_INFINITY));

        double nanPayloadA =
                Double.longBitsToDouble(0x7ff8000000000001L);
        double nanPayloadB =
                Double.longBitsToDouble(0x7ff8000000000002L);

        System.out.println("NaN raw bit patterns differ: " +
                (Double.doubleToRawLongBits(nanPayloadA)
                 != Double.doubleToRawLongBits(nanPayloadB)));
        System.out.println("NaN wrapper equality: " +
                Double.valueOf(nanPayloadA).equals(nanPayloadB));
        System.out.println("NaN wrapper hash codes equal: " +
                (Double.hashCode(nanPayloadA)
                 == Double.hashCode(nanPayloadB)));

        Set<Double> hashSet = new HashSet<>(Arrays.asList(
                -0.0, +0.0, Double.NaN, nanPayloadA, nanPayloadB));
        Set<Double> treeSet = new TreeSet<>(Arrays.asList(
                -0.0, +0.0, Double.NaN, nanPayloadA, nanPayloadB));

        System.out.println("HashSet size: " + hashSet.size());
        System.out.println("TreeSet size: " + treeSet.size());
        System.out.println("Sorted values: " + treeSet);

        System.out.println("Float NaN == NaN: "
                + (Float.NaN == Float.NaN));
        System.out.println("Float.compare(NaN, NaN): "
                + Float.compare(Float.NaN, Float.NaN));
        System.out.println("Float.compare(-0.0f, +0.0f): "
                + Float.compare(-0.0f, +0.0f));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FloatingCompareTableDemo.java
java FloatingCompareTableDemo
~~~

**Kết quả stdout đã kiểm chứng trên Java 17:**

~~~text
primitive NaN == NaN: false
primitive NaN != NaN: true
primitive NaN < 1.0: false
primitive NaN >= 1.0: false
primitive -0.0 == +0.0: true
Double.equals(NaN, NaN): true
Double.compare(NaN, NaN): 0
Double.compare(NaN, +Infinity): 1
Double.equals(-0.0, +0.0): false
Double.compare(-0.0, +0.0): -1
Double.compare(+0.0, -0.0): 1
1.0 / +0.0: Infinity
1.0 / -0.0: -Infinity
Math.min(-0.0, +0.0): -0.0
Math.max(-0.0, +0.0): 0.0
isNaN(NaN): true
isFinite(+Infinity): false
NaN raw bit patterns differ: true
NaN wrapper equality: true
NaN wrapper hash codes equal: true
HashSet size: 3
TreeSet size: 3
Sorted values: [-0.0, 0.0, NaN]
Float NaN == NaN: false
Float.compare(NaN, NaN): 0
Float.compare(-0.0f, +0.0f): -1
~~~

Chương trình không dùng số ngẫu nhiên, nên các kết quả không phụ thuộc lịch chạy. Các mã bit payload NaN được tạo tường minh, còn `TreeSet` in theo thứ tự tự nhiên của `Double`; `HashSet` chỉ in **size**, không dựa vào thứ tự duyệt không được bảo đảm.

## 9. Collection: HashSet, TreeSet, HashMap, TreeMap

Hai loại collection sử dụng hai hợp đồng khác nhau:

- `HashSet<Double>` / `HashMap<Double,...>` dùng `Double.equals` và `Double.hashCode` để xác định khóa trùng.
- `TreeSet<Double>` / `TreeMap<Double,...>` dùng `Double.compareTo` hoặc `Comparator` được cung cấp; với thứ tự tự nhiên, comparator trả 0 thì được xem là cùng vị trí khóa.

May mắn là `Double.compareTo` **nhất quán với `Double.equals`**, kể cả NaN và signed zero, nên với giá trị Double thuần túy, hai loại cấu trúc cùng phân biệt hai zero và gộp tất cả NaN thành một khóa theo khái niệm equality tương ứng.

| Dữ liệu thêm vào | HashSet kích thước | TreeSet kích thước tự nhiên | Giải thích |
|---|---:|---:|---|
| `+0.0`, `-0.0` | 2 | 2 | Hai zero không equal theo wrapper và compare khác 0 |
| `NaN`, `NaN` | 1 | 1 | NaN wrapper equal và compare = 0 |
| Hai NaN raw payload khác nhau | 1 | 1 | Payload bị canonicalize trong equality |
| `-0.0`, `+0.0`, ba NaN | 3 | 3 | Có ba nhóm khóa theo wrapper |
| `1.0`, `1.0` | 1 | 1 | Trùng nhau hoàn toàn |

**Không áp dụng quy tắc primitive `==` để đoán hành vi `HashSet<Double>`.** Nếu `NaN == NaN` false mà collection vẫn gộp NaN, đó là do collection dùng wrapper `equals` chứ không dùng primitive `==`.

Ví dụ tra cứu Map:

~~~java
Map<Double, String> map = new HashMap<>();
map.put(Double.NaN, "missing");
map.put(-0.0, "negative");
map.put(+0.0, "positive");

System.out.println(map.size());             // 3
System.out.println(map.get(Double.NaN));    // missing
System.out.println(map.get(-0.0));          // negative
System.out.println(map.get(+0.0));          // positive
~~~

Đây là code **trong method**, cần import `java.util.Map` và `java.util.HashMap`. Nếu dùng `TreeMap` với thứ tự tự nhiên của `Double`, cấu trúc vẫn phân biệt hai zero và coi các NaN là cùng khóa.

**Cẩn thận comparator tùy biến**: nếu thay comparator bằng một quy tắc mà `compare(a,b) == 0` trong khi `!a.equals(b)`, ý nghĩa trùng khóa của TreeSet/TreeMap khác HashSet/HashMap. Điều này có thể là lựa chọn có chủ đích, nhưng phải được tài liệu hóa và kiểm thử.

## 10. Sắp xếp dữ liệu có NaN: ba chính sách hay gặp

### Chính sách A — Giữ thứ tự tự nhiên Java

Dùng `Double.compare`, `Comparator.comparingDouble`, `Arrays.sort(double[])` hoặc `DoubleStream.sorted()` tùy cấu trúc dữ liệu. NaN đứng cuối, signed zero được phân biệt thứ tự:

~~~java
double[] values = {
    Double.NaN, 0.0, -0.0,
    Double.POSITIVE_INFINITY, -3.0, Double.NEGATIVE_INFINITY
};
java.util.Arrays.sort(values);
System.out.println(java.util.Arrays.toString(values));
// [-Infinity, -3.0, -0.0, 0.0, Infinity, NaN]
~~~

Đoạn trên nằm trong phương thức. Chính sách này tiện để sort, nhưng **không đồng nghĩa NaN là “số lớn nhất” theo toán học**; nó chỉ có vị trí quy ước trong thứ tự wrapper.

### Chính sách B — Từ chối NaN và vô cực

Nếu cột chứa dữ liệu kinh doanh bắt buộc hữu hạn, kiểm tra trước:

~~~java
static double requireFinite(double x) {
    if (!Double.isFinite(x)) {
        throw new IllegalArgumentException("Expected finite double");
    }
    return x;
}
~~~

Đây là method trong class. Khi đã từ chối NaN/Infinity, logic xử lý bớt nhiều trường hợp bất ngờ. Nhưng signed zero vẫn có thể tồn tại nếu API cho phép; quyết định có normalize ±0.0 hay không theo yêu cầu dữ liệu.

### Chính sách C — NaN là dữ liệu thiếu

Nếu NaN đại diện “không có kết quả đo”, có thể tách thành **trạng thái thiếu dữ liệu** bằng một kiểu mô hình rõ ràng, thay vì dựa vào một giá trị double có tính chất so sánh đặc biệt. Khi sắp xếp, ứng dụng phải định nghĩa “thiếu” đứng trước/sau và có được tính vào thống kê hay không.

Nếu chọn custom comparator, nó phải tuân theo hợp đồng `Comparator` (phản đối xứng về dấu, bắc cầu, nhất quán). Đừng đặt một so sánh “gần bằng theo epsilon” làm comparator rồi dùng `TreeSet`: quan hệ gần bằng có thể không bắc cầu, khiến sorted collection hoạt động không đáng tin.

## 11. So sánh “gần bằng”: khác equality và khác NaN handling

Với số thực, hai công thức toán học tương đương có thể cho kết quả hơi khác do sai số làm tròn. Nhiều bài toán cần kiểm tra dung sai:

~~~java
static boolean approximatelyEqual(double a, double b, double epsilon) {
    if (!Double.isFinite(a) || !Double.isFinite(b)
            || !Double.isFinite(epsilon) || epsilon < 0.0) {
        return false;
    }
    return Math.abs(a - b) <= epsilon;
}
~~~

Đây là **predicate minh họa** với chính sách từ chối NaN/Infinity. Nó cũng có giới hạn: phép trừ `a - b` có thể overflow thành Infinity khi a/b ở biên rất lớn, kể cả khi riêng a và b hữu hạn; điều này cần được thiết kế và kiểm thử nếu dùng cho phạm vi floating cực đoan. Dung sai tuyệt đối chỉ phù hợp khi đơn vị và quy mô dữ liệu hợp lý; đôi khi cần dung sai tương đối hoặc kết hợp hai loại.

Điểm quan trọng:

- `Double.compare(a,b) == 0` **không phải kiểm tra gần bằng**.
- `a == b` trên primitive **không phải kiểm tra gần bằng**.
- Với NaN, predicate trên trả false chủ động, thay vì trông vào một quy tắc suy diễn.
- Với ±0.0, `Math.abs(a-b) <= epsilon` trả true khi epsilon ≥ 0; đó là quyết định **nghiệp vụ của predicate**, khác wrapper equality.
- Không override `equals`/`hashCode` chỉ vì một phép tính cần sai số dung sai.

Ví dụ không bắc cầu: A = 0.00, B = 0.09, C = 0.18 và epsilon = 0.10. A gần B, B gần C, nhưng A không gần C. Vì vậy quan hệ này **không thể** thay cho equality dùng làm khóa HashMap hoặc thứ tự comparator một cách trực tiếp.

## 12. Bẫy về ép kiểu, boxing, null và BigDecimal

### 12.1. primitive và wrapper không luôn so sánh cùng cách

~~~java
Double obj = Double.valueOf(Double.NaN);
double raw = Double.NaN;

System.out.println(obj.equals(Double.valueOf(raw))); // true
System.out.println(obj.doubleValue() == raw);         // false
~~~

`obj.equals` sử dụng wrapper equality; `obj.doubleValue() == raw` dùng primitive `==`. Nếu `obj` null, `obj.doubleValue()` ném `NullPointerException`.

Trong generic collections như `List<Double>`, các phần tử thường được boxing thành Double; các thao tác `contains` và `remove(Object)` áp dụng `equals`, không phải primitive `==`. Do đó `list.contains(Double.NaN)` có thể true ngay cả khi `Double.NaN == Double.NaN` false.

### 12.2. Float và Double không equal chỉ vì số nhìn giống nhau

~~~java
Object a = Double.valueOf(1.0);
Object b = Float.valueOf(1.0f);
System.out.println(a.equals(b)); // false
~~~

Đó là hai lớp wrapper khác nhau. Nếu cần chuyển đổi kiểu để so sánh số học, phải xác định phép chuyển kiểu và vấn đề precision. Với số lớn, chuyển long sang double có thể mất chính xác; không tự ép mọi kiểu sang double rồi kết luận hai giá trị giống nhau.

### 12.3. BigDecimal có hợp đồng hoàn toàn khác

`BigDecimal` không có NaN hay ±Infinity như các primitive floating. Ngoài ra, `BigDecimal.equals` kiểm tra cả **giá trị và scale**, trong khi `BigDecimal.compareTo` so sánh theo giá trị số:

~~~java
java.math.BigDecimal x = new java.math.BigDecimal("2.0");
java.math.BigDecimal y = new java.math.BigDecimal("2.00");
System.out.println(x.equals(y));      // false
System.out.println(x.compareTo(y));   // 0
~~~

Đây chỉ là **đối chiếu để không nhầm hợp đồng**, không phải quy tắc NaN/signed zero của Double. `BigDecimal.valueOf(Double.NaN)` không tạo một giá trị BigDecimal NaN hợp lệ: nó ném `NumberFormatException`. Nếu chuyển dữ liệu floating sang BigDecimal, cần xử lý NaN/Infinity trước và cân nhắc signed zero vì biểu diễn thập phân không giữ cùng ngữ nghĩa dấu zero như double.

## 13. Checklist khi review lỗi so sánh floating-point

1. **Biến thực sự là primitive hay wrapper?** `==` giữa hai reference wrapper kiểm tra identity; `==` trên primitive hoặc có unboxing kiểm tra giá trị floating.
2. **Dữ liệu có thể chứa NaN?** Nếu có, so sánh primitive bất đẳng thức có thể đều false, còn `!=` true. Sử dụng `isNaN` khi cần phát hiện.
3. **Có thể chứa Infinity?** `isNaN` không chặn Infinity; nếu cần giá trị hữu hạn, `isFinite`.
4. **Signed zero có ý nghĩa không?** Nếu cần giữ khác biệt dấu, dùng wrapper equality, `Double.compare` hoặc bit API; nếu chỉ cần zero số học, primitive `==` xem chúng như nhau.
5. **Đang sort hay dùng SortedSet?** Chọn `Double.compare` hoặc comparator hợp đồng rõ; không đưa `(a,b) -> a < b ? -1 : (a > b ? 1 : 0)` vào TreeSet khi có NaN vì nó coi NaN bằng tất cả giá trị và có thể vi phạm tính nhất quán.
6. **Đang dùng HashMap/HashSet?** Phải suy luận từ `Double.equals/hashCode`, không phải primitive `==`.
7. **Cần nhận biết payload của NaN?** Dùng `doubleToRawLongBits` và giao thức lưu bit rõ ràng.
8. **Cần kiểm tra gần bằng?** Viết predicate cục bộ có yêu cầu dung sai; không thay đổi global equals và không dùng làm sorted comparator tùy tiện.
9. **Đang chuyển kiểu?** Có thể mất precision hoặc làm mất trạng thái không hữu hạn; không coi cast là bước trung tính.
10. **Định nghĩa nghiệp vụ khi thiếu dữ liệu?** “NaN = missing” là một chính sách, không được tự suy từ thứ tự Double.compare.

### Các câu hỏi kiểm thử tối thiểu

- `Double.NaN == Double.NaN` có false không?
- `Double.isNaN(Double.NaN)` có true không?
- `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` có true không?
- `Double.compare(Double.NaN, Double.POSITIVE_INFINITY) > 0` có true không?
- `+0.0 == -0.0` có true không?
- `Double.compare(-0.0,+0.0) < 0` có true không?
- `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` có false không?
- `1.0/-0.0` có -Infinity không?
- Một HashSet<Double> nhận 2 zero khác dấu và 3 NaN có size = 3 không?
- Một TreeSet<Double> với dữ liệu đó có đúng `[-0.0, 0.0, NaN]` theo thứ tự tự nhiên không?

## 14. Bài tập và đáp án

**Bài 1.** Dự đoán kết quả của các biểu thức sau, nêu rõ primitive hay wrapper:

~~~java
double a = Double.NaN;
System.out.println(a == a);
System.out.println(Double.compare(a, a) == 0);
System.out.println(Double.valueOf(a).equals(a));
~~~

**Bài 2.** Vì sao `0.0 == -0.0` là true nhưng `Double.valueOf(0.0).equals(-0.0)` là false? Nêu một phép toán làm lộ sự khác biệt.

**Bài 3.** Dự đoán tập sau khi sắp xếp bằng `TreeSet<Double>` với `-0.0`, `+0.0`, `Double.NaN`, `Double.POSITIVE_INFINITY`. Vì sao NaN ở cuối?

**Bài 4.** Bạn nhận số đo sensor có thể trả NaN và Infinity. Đề xuất quy tắc kiểm tra trước khi tính trung bình và trước khi lưu vào database.

**Bài 5.** Hai NaN có raw payload khác nhau. `Double.equals` và `Double.compare` trả gì? Cần API nào để giữ được sự khác nhau của raw bit?

**Bài 6.** Một comparator viết:

~~~java
Comparator<Double> bad = (x, y) -> x < y ? -1 : x > y ? 1 : 0;
~~~

Tại sao không nên dùng `bad` với giá trị có NaN trong `TreeSet`? Đề xuất thay thế.

**Đáp án:**

1. `false`, `true`, `true`. Trong biểu thức thứ ba, `Double.equals(Object)` nhận tham số `a` được boxing sang Double, nên NaN bằng NaN.
2. Primitive equality coi hai zero bằng số học; wrapper phân biệt bit dấu. `1.0 / -0.0` cho -Infinity, khác +Infinity của `1.0 / +0.0`.
3. `[-0.0, 0.0, Infinity, NaN]` theo thứ tự Double.compare; NaN được đặt sau mọi giá trị số.
4. Nếu nghiệp vụ chỉ chấp nhận số hữu hạn, dùng `Double.isFinite(x)` để từ chối cả NaN và ±Infinity; xác định riêng quy tắc thiếu dữ liệu và đảm bảo dữ liệu được kiểm tra trước khi lưu/tính.
5. `Double.equals` true và `Double.compare` trả 0; dùng `Double.doubleToRawLongBits` để phân biệt raw payload khi cần.
6. Comparator đó trả 0 giữa NaN và nhiều giá trị khác nhau, không tạo thứ tự phù hợp. Dùng `Double::compare` hoặc comparator tùy biến có xử lý NaN rõ và giữ các tính chất thứ tự.

## 15. Tài liệu Java 17

- Java SE 17 — `Double` (`equals`, `compare`, `compareTo`, `isNaN`, `doubleToLongBits`, `doubleToRawLongBits`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html
- Java SE 17 — `Float`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html
- Java SE 17 — `Math.min`/`max`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html
- Java SE 17 — `Comparator`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html
- Java Language Specification 17 — `15.20.1 Numerical Comparison Operators` và `15.21.1 Numerical Equality Operators`: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html
- Java SE 17 — `TreeSet` và `HashSet`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html

**Ghi nhớ:** với primitive, **NaN không bằng NaN** và **+0.0 bằng -0.0**. Với `Double.equals` và `Double.compare`, hai quy tắc ấy **đảo lại ở hai điểm đặc biệt**: mọi NaN được xem là bằng nhau và signed zero được phân biệt. Hãy chọn API theo đúng nhu cầu: số học, hash key, sắp xếp hay bảo toàn raw bits.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-005">Bảng chọn primitive, wrapper, BigInteger hoặc BigDecimal</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng chọn primitive, wrapper, BigInteger hoặc BigDecimal trong Java

## 1. Mục tiêu: chọn kiểu số theo yêu cầu, không theo thói quen

Java có nhiều cách biểu diễn dữ liệu số: kiểu nguyên thủy (**primitive**), lớp bao (**wrapper**), `BigInteger` và `BigDecimal`. Không có kiểu nào “tốt nhất cho mọi trường hợp”. Việc chọn kiểu cần dựa trên **miền giá trị**, **độ chính xác**, **đơn vị**, **null/có dữ liệu hay không**, **API sử dụng** và **chi phí thực thi**.

Bài này là bảng tra cứu để chọn loại số phù hợp khi khai báo biến, kiểu trường, tham số, kết quả tính toán, khóa collection và dữ liệu trao đổi. Các tiêu chí cốt lõi:

1. **Số nguyên hay thập phân?** Số lượng, thứ tự, chỉ mục thường là số nguyên; giá, tỷ lệ, phép đo có thể cần phần lẻ.
2. **Cần chính xác tuyệt đối hay gần đúng?** Không dùng `double` thay `BigDecimal` khi một chữ số tiền tệ sai cũng không chấp nhận.
3. **Có giới hạn trên/dưới không?** `int`, `long` có giới hạn cố định, `BigInteger` mở rộng theo tài nguyên.
4. **Có cần biểu diễn trạng thái thiếu dữ liệu?** Primitive không chứa `null`; wrapper có thể chứa `null` nhưng phải tránh unboxing không kiểm tra.
5. **Làm việc với generics/collection/framework?** `List<int>` không hợp lệ trong Java 17; `List<Integer>` hợp lệ.
6. **Kiểu lưu và kiểu tính trung gian có thể khác nhau.** Kết quả cuối có thể vừa `int` nhưng tổng hoặc tích trung gian cần `long` hay `BigInteger`.

## 2. Bảng chọn nhanh — trường hợp điển hình

| Nhu cầu | Lựa chọn nên cân nhắc | Vì sao | Tránh nhầm lẫn |
| --- | --- | --- | --- |
| Biến đếm, chỉ mục, số lượng nhỏ trong phạm vi 32 bit | `int` | Primitive gọn, phép toán đơn giản, không null | Tổng/tích của nhiều giá trị `int` vẫn có thể tràn |
| Tổng lớn, timestamp hoặc số nguyên trong phạm vi 64 bit | `long` | Miền rộng hơn `int` | `long` vẫn có giới hạn, phép cộng/nhân vẫn có thể tràn |
| Tập giá trị chỉ có byte hoặc short do giao thức/schema | `byte`, `short` | Đúng miền/định dạng bên ngoài | Số học `byte`/`short` thường được nâng thành `int` |
| Biểu thị ký tự UTF-16 code unit | `char` | Là primitive 16-bit cho code unit | Không dùng `char` thay kiểu số chỉ vì không dấu |
| Số đo, đồ họa, thống kê chấp nhận xấp xỉ | `double` | Floating point phổ biến, tốc độ tốt | 0.1 không lưu chính xác, có NaN/Infinity/-0.0 |
| Dữ liệu hoặc API thực sự yêu cầu float 32-bit | `float` | Bớt dung lượng, đúng giao thức | Chính xác thấp hơn `double`; tránh dùng mặc định |
| Số nguyên cần `null` hoặc tham gia `List<T>`, `Map<K,V>` | `Integer`, `Long` và wrapper phù hợp | Kiểu object hỗ trợ generics, nullable | Boxing/unboxing, `==`, `NullPointerException` |
| Số nguyên chính xác vượt `long` | `BigInteger` | Không giới hạn 64 bit cố định | Chi phí object, dùng `add`/`multiply` thay toán tử |
| Số thập phân chính xác, tiền và rounding có quy tắc | `BigDecimal` | Hệ thập phân có scale, phương thức rounding rõ ràng | Chia cần xử lý thương vô hạn; `equals` xét scale |
| Có thể vắng một số nguyên/real nhưng không muốn `null` | `OptionalInt`, `OptionalLong`, `OptionalDouble` (đặc biệt ở API kết quả) | Thể hiện rõ có/không có giá trị | Không phải kiểu số dùng để tính như primitive |
| Tiền đã chốt ở đơn vị nhỏ nhất, phạm vi vừa 64 bit | `long` cents (theo domain) | Exact integer với đơn vị cố định | Thuế/phần trăm có thể cần `BigDecimal` trung gian |
| ID gồm chữ số, có thể có số 0 đầu, không dùng toán học | `String` | Giữ định danh và số 0 đầu | Không chọn `int`/`long` chỉ vì nhìn giống số |

Đây là **gợi ý dựa trên hợp đồng domain**, không phải mệnh lệnh dùng một kiểu duy nhất. Ví dụ `Long` là hợp lý cho cột nullable nếu schema cho phép thiếu, trong khi `long` tốt hơn cho bộ đếm bắt buộc có dữ liệu.

## 3. Ma trận năng lực của các lựa chọn chính

| Tiêu chí | Primitive nguyên `int/long` | Primitive thực `float/double` | Wrapper `Integer/Long/Double/...` | `BigInteger` | `BigDecimal` |
| --- | --- | --- | --- | --- | --- |
| Giá trị `null` | Không | Không | Có | Có ở biến tham chiếu, nhưng null không phải số | Có ở biến tham chiếu, nhưng null không phải số |
| Miền | Cố định theo bit | Cố định theo IEEE 754 | Như primitive tương ứng | Độ lớn linh hoạt theo tài nguyên | Độ lớn thập phân linh hoạt theo tài nguyên |
| Số nguyên chính xác | Có trong miền | Chỉ một số miền và độ lớn; không phải mọi `long` | Theo primitive bọc | Có | Có thể biểu diễn số nguyên chính xác |
| Số thập phân chính xác | Không biểu diễn phần lẻ | Nói chung không chính xác decimal | Wrapper của `float/double` cũng xấp xỉ | Chỉ nguyên | Có với giá trị biểu diễn hữu hạn thập phân |
| Phép toán bằng `+ - * /` | Có | Có | Có nhờ unboxing (với nguy cơ null) | Không; dùng phương thức | Không; dùng phương thức |
| Dùng trực tiếp trong `List<T>` | Không | Không | Có | Có | Có |
| Tạo nhiều đối tượng khi tính | Không đối với giá trị primitive thuần | Không đối với giá trị primitive thuần | Có thể phát sinh boxing | Phép tính tạo đối tượng kết quả mới | Phép tính tạo đối tượng kết quả mới |
| Kiểm soát rounding decimal | Không phù hợp | Quy tắc floating point, không phải decimal business | Tương ứng primitive | Không có phần lẻ | Dùng `scale`, `MathContext`, `RoundingMode` |
| Nguy cơ thường gặp | Overflow, chia nguyên | Sai số, NaN/Infinity | Null unboxing, so `==` | Chuyển thu hẹp sai | Dùng constructor từ `double`, scale/rounding, `equals` |

Tính bất biến: `BigInteger`, `BigDecimal` và các wrapper số chuẩn là immutable. Ví dụ `total.add(value)` không sửa `total`; phải gán lại kết quả. Kiểu primitive không phải object nên không có trạng thái tham chiếu để chia sẻ theo cách wrapper.

## 4. Cây quyết định dạng văn bản

```text
Đầu vào có thực sự là giá trị số để tính toán?
  ├─ Không: mã, ID, số tài khoản, mã bưu chính... → cân nhắc String
  └─ Có
      ├─ Cần phần thập phân?
      │   ├─ Không, chỉ nguyên
      │   │   ├─ Vừa int và không cần null/generics? → int
      │   │   ├─ Vừa long và không cần null/generics? → long
      │   │   ├─ Vượt long nhưng phải exact? → BigInteger
      │   │   └─ Cần null/generics? → wrapper phù hợp miền (Integer/Long...)
      │   └─ Có
      │       ├─ Phải chính xác thập phân/rounding nghiệp vụ? → BigDecimal
      │       └─ Chấp nhận xấp xỉ IEEE 754? → double (hoặc float khi có lý do)
      └─ Trước khi lưu/trả API:
          kiểm tra miền, đơn vị, null, rounding và chuyển đổi exact
```

Lưu ý một giá trị có thể đồng thời có nhiều ràng buộc. Ví dụ “số tiền phải chính xác nhưng dùng `List`” không có nghĩa chọn `Double`: có thể chọn `List<BigDecimal>`. “Giá trị nguyên tùy chọn” có thể biểu diễn bằng `OptionalInt` ở ranh giới trả kết quả hoặc bằng `Integer` nullable theo yêu cầu framework.

## 5. Primitive: int, long, float và double

### 5.1. Số nguyên có dấu: byte, short, int, long

| Kiểu | Bit | Min | Max |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32.768 | 32.767 |
| `int` | 32 | -2.147.483.648 | 2.147.483.647 |
| `long` | 64 | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 |

Trong nhiều biểu thức số học, `byte`, `short` và `char` được nâng thành `int`; việc một trường lưu ở kiểu nhỏ không có nghĩa mọi phép tính sẽ giữ nguyên kiểu đó.

`int` là lựa chọn mặc định tốt cho bộ đếm/chỉ số có giới hạn rõ ràng. `long` phù hợp khi miền vượt `int`. Nhưng **lưu ý phép tính có thể tràn trước khi gán sang kiểu rộng**:

```java
int quantity = 50_000;
int price = 50_000;

long wrong = quantity * price;         // -1794967296
long right = (long) quantity * price;  // 2500000000
```

Cả hai dòng đều biên dịch; chỉ dòng thứ hai nhân ở kiểu `long`. Muốn kiểm tra overflow thay vì wraparound, dùng `Math.addExact`, `Math.multiplyExact`, `Math.subtractExact` và các overload phù hợp.

### 5.2. Số thực nhị phân: float, double

`double` là số dấu phẩy động 64-bit, với khoảng 53 bit significand; `float` là 32-bit, khoảng 24 bit significand. Chúng có thể biểu diễn số rất lớn và rất nhỏ, nhưng **không thể biểu diễn chính xác mọi số thập phân**.

```java
double result = 0.1 + 0.2;
// Thường in 0.30000000000000004, không phải đúng decimal 0.3
```

Kiểu này có `NaN`, `+Infinity`, `-Infinity`, `+0.0` và `-0.0`, cùng vùng subnormal gần 0. `Double.isFinite(value)` dùng để kiểm tra một giá trị có hữu hạn hay không, nhưng không đánh giá được độ chính xác nghiệp vụ.

**Đừng chọn `double` làm “số nguyên cực lớn”**: mọi số nguyên tới 2^53 đều biểu diễn chính xác, nhưng những số nguyên lớn hơn không còn được đảm bảo. `long` giữ các số nguyên trong miền của nó đúng tuyệt đối, dù miền độ lớn nhỏ hơn một phần dải của `double`.

`float` không tự nhiên chính xác hơn vì tên ngắn. Chỉ dùng `float` nếu yêu cầu giao thức, bộ nhớ, dữ liệu GPU hay API định rõ 32-bit hoặc sau khi đo đạc cân nhắc độ chính xác.

## 6. Wrapper: Integer, Long, Double và tự động boxing

Mỗi primitive số có wrapper tương ứng:

| Primitive | Wrapper |
| --- | --- |
| `byte` | `Byte` |
| `short` | `Short` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |
| `char` | `Character` |

`Boolean` cũng là wrapper của `boolean`, nhưng không phải kiểu số; không đưa vào nhóm lựa chọn cho dữ liệu số.

Wrapper là object và cho phép:

- Biểu diễn `null` khi domain hoặc framework cho phép thiếu dữ liệu.
- Sử dụng với generics như `List<Integer>`, `Map<Long, String>`.
- Dùng các phương thức tiện ích: `Integer.parseInt`, `Integer.valueOf`, `Long.compare`, `Double.isFinite`, `Integer.toString`.
- Thỏa yêu cầu API nhận object thay vì primitive.

Java hỗ trợ **autoboxing** (chuyển primitive thành wrapper) và **unboxing** (chuyển wrapper về primitive):

```java
int primitive = 42;
Integer boxed = primitive; // autoboxing
int unboxed = boxed;       // unboxing
```

Nhưng `Integer nullable = null; int x = nullable;` gây `NullPointerException` khi unboxing. Cũng đừng dùng toán tử `==` để so giá trị hai wrapper tham chiếu: `==` trên hai đối tượng wrapper có thể là so **định danh object**; kết quả với một số giá trị có thể bị ảnh hưởng bởi cache boxing. Dùng `Objects.equals(a,b)` khi hai bên nullable, hoặc unbox/`Integer.compare` sau khi đã kiểm tra null.

`Integer.parseInt("17")` trả primitive `int`; `Integer.valueOf("17")` trả wrapper `Integer`. Trong generic collection, Java 17 yêu cầu kiểu object: `List<int>` không hợp lệ, `List<Integer>` hợp lệ.

### Chọn wrapper không có nghĩa “luôn muốn null”

Nếu dữ liệu chắc chắn tồn tại và không bị ràng buộc bởi generics hoặc API, primitive thường đơn giản và tránh null. Wrapper chỉ nên dùng khi có lý do: contract nullable, generic, reflection/ORM hoặc một thư viện cần object.

Không đồng nhất `Integer` với `OptionalInt`: `Integer` có thể null, còn `OptionalInt` là object chứa trạng thái có/không của một `int` và chủ yếu có ích ở ranh giới kết quả. Tránh dùng `Optional` làm trường dữ liệu tùy tiện nếu framework hoặc serialization của hệ thống không hỗ trợ hợp đồng đó.

## 7. BigInteger: số nguyên exact vượt long

`java.math.BigInteger` biểu diễn số nguyên không bị giới hạn ở 32 hoặc 64 bit. Độ lớn thực tế bị giới hạn bởi bộ nhớ, thời gian tính toán và các giới hạn triển khai.

Ví dụ:

```java
BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE); // 9223372036854775808
```

Khác primitive, cần dùng phương thức:

| Phép toán | BigInteger |
| --- | --- |
| Cộng | `a.add(b)` |
| Trừ | `a.subtract(b)` |
| Nhân | `a.multiply(b)` |
| Chia nguyên | `a.divide(b)` |
| Chia lấy dư | `a.remainder(b)` hoặc `a.divideAndRemainder(b)` |
| So sánh | `a.compareTo(b)` |
| Lũy thừa nguyên không âm | `a.pow(exponent)` |
| Chuyển về long có kiểm tra | `a.longValueExact()` |
| Chuyển về int có kiểm tra | `a.intValueExact()` |

`BigInteger` chỉ dành cho số nguyên: chia `7 / 2` cho thương nguyên `3`; không có phần lẻ `3.5`. Muốn tính phân số hữu tỉ chính xác, có thể lưu tử số/mẫu số riêng hoặc chọn `BigDecimal` cùng chính sách precision/rounding khi cần một kết quả decimal.

**Không dùng `longValue()`/`intValue()` nếu cấm mất dữ liệu**, vì chúng có thể thu hẹp và bỏ bit. Dùng `longValueExact()` hoặc `intValueExact()` để nhận `ArithmeticException` nếu không vừa miền.

## 8. BigDecimal: số thập phân chính xác và rounding

`java.math.BigDecimal` biểu diễn giá trị:

```text
unscaledValue × 10^(-scale)
```

Ví dụ `new BigDecimal("12.34")` có unscaled value 1234 và scale 2. Dùng `BigDecimal` khi phép tính phải giữ decimal chính xác hoặc khi domain có chính sách làm tròn rõ ràng, chẳng hạn tiền, tỷ lệ thuế, hạn mức và số liệu kế toán.

### Tạo BigDecimal đúng

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = BigDecimal.valueOf(1234L, 2); // 12.34
BigDecimal c = BigDecimal.valueOf(0.1);
```

Đoạn này dùng trong phương thức có import `java.math.BigDecimal`.

`new BigDecimal("0.1")` là decimal đúng 0.1. `BigDecimal.valueOf(0.1)` dùng biểu diễn chuỗi chuẩn của `double` 0.1; thường phù hợp hơn `new BigDecimal(0.1)` nếu đầu vào đã là `double`. Nhưng nếu các phép tính trước đó làm mất độ chính xác của nguồn, `valueOf` không phục hồi được thông tin đã mất.

**Tránh `new BigDecimal(0.1)` khi ý định là số decimal 0.1 chính xác**: constructor nhận `double` sẽ biểu diễn chính xác giá trị nhị phân gần đúng của `double`, vốn không bằng decimal 0.1.

### Phép tính, chia và làm tròn

```java
BigDecimal price = new BigDecimal("19.99");
BigDecimal rate = new BigDecimal("0.075");
BigDecimal tax = price.multiply(rate)
        .setScale(2, RoundingMode.HALF_UP); // 1.50
```

Ví dụ này giả định domain yêu cầu hai chữ số thập phân và làm tròn `HALF_UP`; không mặc định mọi đơn vị tiền tệ cần scale 2 hay mọi nghiệp vụ dùng cùng RoundingMode.

| Nhu cầu | API |
| --- | --- |
| Cộng | `a.add(b)` |
| Trừ | `a.subtract(b)` |
| Nhân | `a.multiply(b)` |
| Chia exact nếu thương kết thúc | `a.divide(b)` |
| Chia có làm tròn | `a.divide(b, scale, roundingMode)` |
| Đổi scale có kiểm soát | `a.setScale(scale, roundingMode)` |
| So giá trị toán học | `a.compareTo(b)` |
| Chuyển về int/long exact | `a.intValueExact()`, `a.longValueExact()` |
| Kiểm tra decimal vừa miền precision | Dùng `precision()`, `scale()` và validation của domain/schema |

`BigDecimal.ONE.divide(new BigDecimal("3"))` ném `ArithmeticException` vì `1/3` có khai triển decimal vô hạn. Dùng overload với scale/RoundingMode hoặc MathContext phù hợp khi phép chia được phép làm tròn.

`BigDecimal` là immutable. Lời gọi `balance.add(amount)` **không** thay đổi `balance`; phải viết `balance = balance.add(amount)`.

### equals khác compareTo

```java
BigDecimal first = new BigDecimal("2.0");
BigDecimal second = new BigDecimal("2.00");

System.out.println(first.compareTo(second) == 0); // true
System.out.println(first.equals(second));          // false
```

`compareTo` so theo giá trị số; `equals` xét cả giá trị và scale. Điều này ảnh hưởng `HashSet`/`HashMap` khi dùng `BigDecimal` làm khóa: 2.0 và 2.00 là hai key khác nhau nếu giữ nguyên biểu diễn. Nếu nghiệp vụ chỉ quan tâm giá trị số, cần một chính sách chuẩn hóa khóa nhất quán (ví dụ `stripTrailingZeros()`) trước cả lúc thêm và lúc tra cứu.

## 9. Chương trình Java 17+ minh họa lựa chọn theo tình huống

Lưu `NumberChoiceDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.OptionalInt;

public class NumberChoiceDemo {
    public static void main(String[] args) {
        int quantity = 50_000;
        int priceUnits = 50_000;
        long amount = Math.multiplyExact((long) quantity, priceUnits);
        System.out.println("long product: " + amount);

        BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        System.out.println("beyond long: " + beyondLong);

        BigDecimal exact = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        System.out.println("decimal sum: " + exact);

        BigDecimal base = new BigDecimal("19.99");
        BigDecimal tax = base.multiply(new BigDecimal("0.075"))
                .setScale(2, RoundingMode.HALF_UP);
        System.out.println("tax rounded: " + tax);
        System.out.println("with tax: " + base.add(tax));

        List<Integer> readings = List.of(3, 4);
        System.out.println("boxed list total: "
                + readings.stream().mapToInt(Integer::intValue).sum());
        OptionalInt missing = OptionalInt.empty();
        System.out.println("optional int empty: " + missing.isEmpty());
    }
}
```

Biên dịch và chạy:

```bash
javac NumberChoiceDemo.java
java NumberChoiceDemo
```

Stdout đã kiểm chứng bằng Java:

```text
long product: 2500000000
beyond long: 9223372036854775808
decimal sum: 0.3
tax rounded: 1.50
with tax: 21.49
boxed list total: 7
optional int empty: true
```

Một tình huống sử dụng nhiều kiểu là bình thường: `int` cho quantity, `long` cho tích cần miền 64 bit, `BigInteger` cho giá trị nguyên vượt 64 bit, `BigDecimal` cho decimal exact, và `Integer` trong generic collection. Không nên ép tất cả thành cùng một kiểu chỉ để trông nhất quán về mặt cú pháp.

## 10. Chuyển đổi qua ranh giới kiểu: dùng exact khi không cho mất dữ liệu

Dữ liệu có thể được tính bằng kiểu rộng hơn rồi cần lưu thành kiểu nhỏ; việc **chuyển đổi** phải là một bước có kiểm tra riêng.

| Chuyển từ | Có nguy cơ | Gợi ý |
| --- | --- | --- |
| `long` sang `int` | Mất bit/đổi dấu nếu ngoài miền | `Math.toIntExact(value)` |
| `BigInteger` sang `long` | Giá trị lớn hơn 64-bit | `longValueExact()` |
| `BigInteger` sang `int` | Giá trị ngoài 32-bit | `intValueExact()` |
| `BigDecimal` sang `long` | Phần lẻ hoặc vượt miền | `longValueExact()` |
| `BigDecimal` sang `int` | Phần lẻ hoặc vượt miền | `intValueExact()` |
| `BigDecimal` sang cents `long` | Còn phần nhỏ hơn đơn vị cents hoặc vượt miền | `movePointRight(2).longValueExact()` khi domain có scale 2 |
| `double` sang `int`/`long` | Làm tròn về 0, bão hòa ở biên, NaN; mất thông tin | Validation rõ miền/độ chính xác hoặc chuyển đổi theo domain |
| `BigDecimal` sang `double` | Mất độ chính xác, có thể ra vô cực | Chỉ khi bài toán chấp nhận xấp xỉ |

Đối với `BigDecimal`, chuyển exact sang `int`/`long` **không bắt buộc scale 0**: `new BigDecimal("2.00").longValueExact()` trả 2 vì không có phần lẻ có giá trị. Nhưng `new BigDecimal("2.01").longValueExact()` ném lỗi.

Không tự thay thế `longValueExact()` bằng `longValue()` nếu việc âm thầm bỏ phần thập phân hoặc bit vượt miền bị cấm.

### Ví dụ chuyển đổi có kiểm tra

Lưu `NumberBoundaryDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class NumberBoundaryDemo {
    public static void main(String[] args) {
        int x = 50_000;
        int y = 50_000;
        System.out.println("int overflow: " + (x * y));
        System.out.println("widen first: " + ((long) x * y));

        try {
            System.out.println(Math.toIntExact((long) x * y));
        } catch (ArithmeticException e) {
            System.out.println("narrowing rejected");
        }

        BigInteger huge = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        try {
            System.out.println(huge.longValueExact());
        } catch (ArithmeticException e) {
            System.out.println("big integer narrowing rejected");
        }

        BigDecimal amount = BigDecimal.valueOf(1234L, 2);
        System.out.println("minor units to decimal: " + amount);
        System.out.println("back to minor units: "
                + amount.movePointRight(2).longValueExact());

        try {
            new BigDecimal("12.345").movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            System.out.println("fractional minor unit rejected");
        }

        BigDecimal d1 = new BigDecimal("2.0");
        BigDecimal d2 = new BigDecimal("2.00");
        System.out.println("numeric equal: " + (d1.compareTo(d2) == 0));
        System.out.println("object equal: " + d1.equals(d2));
        System.out.println("1/3 rounded: "
                + BigDecimal.ONE.divide(
                        new BigDecimal("3"), 2, RoundingMode.HALF_UP));
    }
}
```

Biên dịch và chạy:

```bash
javac NumberBoundaryDemo.java
java NumberBoundaryDemo
```

Stdout đã kiểm chứng:

```text
int overflow: -1794967296
widen first: 2500000000
narrowing rejected
big integer narrowing rejected
minor units to decimal: 12.34
back to minor units: 1234
fractional minor unit rejected
numeric equal: true
object equal: false
1/3 rounded: 0.33
```

Ví dụ sử dụng **2 chữ số đơn vị nhỏ nhất** cho phép chuyển cents. Nếu domain không theo quy ước này (hoặc tỷ lệ cần nhiều chữ số trung gian), phải thay factor/scale và chính sách làm tròn phù hợp; không được áp dụng vô điều kiện cho mọi đơn vị tiền.

## 11. Wrapper và collection: null, boxing và hiệu năng

Lưu `BoxingChoiceDemo.java` để thấy rõ wrapper và primitive được chuyển đổi:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BoxingChoiceDemo {
    public static void main(String[] args) {
        int primitive = 42;
        Integer wrapper = primitive;
        int unboxed = wrapper;
        System.out.println("unboxed: " + unboxed);
        System.out.println("wrapper value equality: "
                + Integer.valueOf(42).equals(wrapper));

        List<Integer> values = new ArrayList<>();
        values.add(3);
        values.add(4);
        System.out.println("collection sum: "
                + values.stream().mapToInt(Integer::intValue).sum());

        Integer nullable = null;
        System.out.println("nullable absent: " + Objects.isNull(nullable));
        try {
            int dangerous = nullable;
            System.out.println(dangerous);
        } catch (NullPointerException e) {
            System.out.println("null unboxing rejected");
        }

        System.out.println("parsed int: " + Integer.parseInt("17"));
    }
}
```

Chạy:

```bash
javac BoxingChoiceDemo.java
java BoxingChoiceDemo
```

Stdout:

```text
unboxed: 42
wrapper value equality: true
collection sum: 7
nullable absent: true
null unboxing rejected
parsed int: 17
```

Với các phép tổng hợp trên nhiều wrapper, có thể chuyển sang primitive stream bằng `mapToInt(Integer::intValue)`, `mapToLong(Long::longValue)` hoặc `mapToDouble(Double::doubleValue)`. **Chú ý kiểu luồng đầu vào**: `IntStream` đã là primitive, nên không dùng `mapToLong(Integer::longValue)` trên `IntStream`; thay vào đó dùng `asLongStream()` hoặc `mapToLong(x -> (long) x)`.

Primitive stream giúp hạn chế boxing trong những phép tính nhất định nhưng không xóa mọi chi phí object của collection gốc. Với dữ liệu khối lượng rất lớn, đo hiệu năng và profile thay vì chọn wrapper hay primitive theo cảm tính.

## 12. Chọn kiểu tính trung gian khác với kiểu lưu

Một số giá trị lưu được bằng `int` nhưng phép tính trung gian cần `long`; tương tự tổng hoặc tích của nhiều `long` có thể cần `BigInteger`. Khi tính decimal exact, có thể dùng `BigDecimal` rồi lưu bằng `long` số đơn vị nhỏ nhất **sau khi** đã làm tròn và kiểm tra.

Ví dụ xử lý đơn hàng:

```text
quantity: int hoặc long theo giới hạn từng dòng
unitPriceCents: long nếu đơn vị nhỏ nhất xác định
subtotalCents: long với multiplyExact, hoặc BigInteger nếu miền quá lớn
tax: BigDecimal với tỷ lệ/rounding từ domain
finalCents: long sau khi chuyển exact và kiểm tra miền
DB: BIGINT cents hoặc DECIMAL(p,s) theo schema
```

Đây là bản thiết kế tham khảo, không phải công thức bắt buộc cho hệ thống tiền tệ. Tùy phạm vi dữ liệu, có thể dùng `BigDecimal` làm cả kiểu tính lẫn kiểu lưu; hoặc dùng `BigInteger` ở bước tổng hợp khối lượng lớn. Phần quan trọng là **có contract rõ ràng tại ranh giới chuyển**.

Không chờ đến cuối mới phát hiện tràn: nếu một phép nhân `int` đã overflow, chuyển kết quả sai sang `BigInteger` không khôi phục được giá trị gốc. Hãy chọn kiểu đúng *trước* phép tính, hoặc dùng checked arithmetic.

## 13. Java API và database/JSON

Kiểu Java không tự quyết định miền và độ chính xác khi qua ranh giới hệ thống:

- **Database:** `INTEGER`, `BIGINT`, `DECIMAL(p,s)`, `FLOAT`/`DOUBLE` có miền và quy tắc chuyển đổi khác nhau tùy hệ quản trị. Kiểm tra schema, nullability, độ lớn, precision và scale, không trông chờ driver tự sửa dữ liệu.
- **JSON:** JSON number không phải kiểu `long` hay `BigDecimal` của Java. Một client JavaScript dùng `Number` có thể không giữ chính xác số nguyên lớn hơn `2^53 - 1`; cân nhắc chuỗi decimal hoặc giao thức hỗ trợ kiểu số lớn nếu tính nguyên vẹn là quan trọng.
- **NaN/Infinity:** `double` có giá trị không hữu hạn, nhưng JSON number chuẩn không biểu diễn NaN/Infinity. Cần chính sách từ chối hoặc encoding đặc biệt rõ ràng.
- **Nullable:** phân biệt `null` (không có giá trị), `0` (giá trị số không), và trường không xuất hiện. Tùy framework, wrapper có thể phù hợp cho nullable; primitive phù hợp cho trường bắt buộc với validation.
- **ID:** mã khách hàng, số tài khoản và chuỗi có số 0 đầu không mặc nhiên là số cần toán học; lưu dưới `String` khi tính chất định danh quan trọng.

Đặc biệt, nếu dữ liệu vào/ra phải chính xác decimal, đừng chuyển trung gian qua `double` trong serialization hoặc tính toán chỉ vì `double` dễ dùng.

## 14. Lỗi thường gặp khi chọn kiểu

| Sai lầm | Hậu quả | Quy tắc sửa |
| --- | --- | --- |
| Dùng `int` cho tổng có thể vượt 2^31-1 | Tràn âm/dương | `long`/`BigInteger` và kiểm tra miền |
| `long total = intA * intB` | Tràn `int` trước khi gán | Ép `(long) intA * intB` trước phép nhân |
| Dùng `double` cho cents exact | Sai số nhị phân | `long` đơn vị nhỏ nhất hoặc `BigDecimal` |
| Chọn `BigInteger` cho 7/2 rồi cần 3.5 | Chia nguyên không giữ phần lẻ | `BigDecimal`/phân số và rounding rõ |
| `new BigDecimal(0.1)` | Tạo decimal đúng với **xấp xỉ nhị phân**, không phải decimal 0.1 | `new BigDecimal("0.1")` |
| `BigDecimal.divide` khi 1/3 mà không rounding | `ArithmeticException` | Khai báo scale/MathContext/RoundingMode |
| `BigDecimal.equals` để so 2.0 và 2.00 theo giá trị số | False do scale khác | `compareTo == 0` |
| So hai wrapper bằng `==` | So identity object, phụ thuộc boxing/cache | `equals` hoặc `Objects.equals` |
| Unbox một wrapper null | `NullPointerException` | Kiểm tra null hoặc thiết kế API không nullable |
| Dùng `List<int>` | Không hợp lệ với Java 17 generics | `List<Integer>` hoặc primitive array/stream |
| `BigInteger.longValue()` khi dữ liệu vượt long | Mất bit | `longValueExact()` |
| `BigDecimal.longValue()` khi còn phần lẻ | Bỏ phần lẻ, có thể sai nghiệp vụ | `longValueExact()` khi cần exact |
| Dùng `float` để “tăng độ chính xác” | Thực tế ít precision hơn double | `double` hoặc `BigDecimal` theo nhu cầu |
| Dùng số nguyên cho mã có leading zero | Mất 0 đầu | `String` |
| Dùng `Integer` vì “object luôn tốt hơn” | Null/boxing/chi phí không cần thiết | Primitive khi bắt buộc có giá trị và không cần generics |
| Dùng `BigDecimal` ở mọi phép đo xấp xỉ | Chi phí phức tạp không mang lại độ chính xác nguồn | `double` khi sai số được chấp nhận |

## 15. Checklist lựa chọn trước khi quyết định

Dùng các câu hỏi sau để đi từ yêu cầu đến kiểu:

1. **Giá trị có phải số cần tính?** Nếu chỉ là định danh, cân nhắc `String`.
2. **Chỉ nguyên hay có phần lẻ?** Nguyên: `int`/`long`/`BigInteger`; thập phân: `double` hoặc `BigDecimal`.
3. **Miền lớn nhất của cả kết quả trung gian và cuối?** Tính cả nhân, cộng dồn, lũy thừa và dữ liệu tương lai.
4. **Có chấp nhận sai số IEEE 754?** Nếu không, đừng chọn `float`/`double` để giữ decimal chính xác.
5. **Có cần null?** Primitive không null; wrapper null nhưng phải kiểm tra trước unboxing; có thể dùng `OptionalInt/Long/Double` ở API kết quả.
6. **Có generics/collection không?** Chọn wrapper hoặc `BigInteger/BigDecimal` cho generic Java 17.
7. **Có yêu cầu rounding cụ thể?** Với `BigDecimal`, xác định scale và `RoundingMode` tại ranh giới đúng.
8. **Có conversion về kiểu nhỏ hơn không?** Dùng `Math.toIntExact`, `intValueExact/longValueExact` hoặc kiểm tra đầy đủ.
9. **Schema và client có hiểu giá trị không?** Kiểm tra JDBC, JSON/JavaScript, database precision/scale và nullability.
10. **Đã có test biên chưa?** Thử MIN/MAX, overflow, 0, số âm, 0.1, 1/3, null và biểu diễn decimal khác scale.

## 16. Câu hỏi tự kiểm tra

1. **Lưu số lượng nhỏ có giá trị bắt buộc và không cần generics?** Thường `int`.
2. **Tổng hai `int` có thể vượt `Integer.MAX_VALUE`?** Chuyển sang `long` trước phép cộng hoặc dùng checked arithmetic.
3. **Dùng `List<int>` trong Java 17 được không?** Không; dùng `List<Integer>`.
4. **Nếu `Integer` là null rồi unbox sang `int`?** Ném `NullPointerException`.
5. **Cần một số nguyên exact lớn hơn `Long.MAX_VALUE`?** `BigInteger`.
6. **Cần giữ 0.1 và 0.2 chính xác decimal?** `BigDecimal` từ chuỗi hoặc nguồn decimal chính xác.
7. **`BigInteger.divide` có cho phần lẻ không?** Không; nó là phép chia nguyên.
8. **Vì sao `BigDecimal("2.0").equals(BigDecimal("2.00"))` false?** Scale khác; `compareTo` mới phù hợp khi chỉ so giá trị toán học.
9. **Chuyển `BigDecimal("12.345")` thành cents `long` theo scale 2 mà cấm mất dữ liệu?** `movePointRight(2).longValueExact()` ném lỗi; cần xử lý rounding theo nghiệp vụ nếu được phép.
10. **`double` có chứa chính xác mọi số nguyên `long` không?** Không, vì precision hữu hạn.
11. **Khi nào chọn wrapper thay primitive?** Khi cần nullable, generics hoặc API yêu cầu object; không tự động chọn wrapper cho mọi trường.
12. **Tại sao `BigDecimal` không luôn tốt hơn `double`?** `double` phù hợp nhiều phép tính xấp xỉ và có chi phí thấp; `BigDecimal` có overhead và cần quản lý scale/rounding.

## 17. Kết luận

Có thể ghi nhớ một bảng chọn tối giản:

- **`int`/`long`:** số nguyên có miền xác định, giá trị bắt buộc; chọn theo phạm vi cả phép tính.
- **`Integer`/`Long` và wrapper:** cùng ý nghĩa số với primitive tương ứng nhưng cần object, nullable hoặc generics.
- **`double` (và `float` khi có lý do):** số thực nhị phân xấp xỉ, thích hợp nhiều bài toán khoa học/thống kê nhưng không bảo toàn mọi decimal.
- **`BigInteger`:** số nguyên exact vượt kiểu cố định.
- **`BigDecimal`:** thập phân exact và rounding nghiệp vụ, đặc biệt tiền hoặc dữ liệu decimal cần kiểm soát.

Luôn xác định **miền, độ chính xác, nullability, đơn vị và ranh giới dữ liệu** trước khi chọn. Trong một luồng xử lý thực tế, nhiều kiểu có thể cùng xuất hiện — đó là thiết kế đúng nếu mỗi kiểu có vai trò rõ ràng và chuyển đổi giữa chúng được kiểm tra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-006">Bảng precision, scale, MathContext và RoundingMode</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu BigDecimal: precision, scale, MathContext và RoundingMode

## 1. Bản đồ khái niệm

Khi xử lý số decimal trong Java, bốn khái niệm thường bị nhầm với nhau:

| Khái niệm | Câu hỏi cần trả lời | Được biểu diễn bởi |
| --- | --- | --- |
| **Giá trị số học** | Đây là số bao nhiêu? | Một `BigDecimal` với `unscaledValue` và `scale` |
| **Precision của BigDecimal** | Hệ số nguyên không có số 0 đầu có bao nhiêu chữ số? | `value.precision()` |
| **Scale của BigDecimal** | Dấu thập phân nằm ở đâu so với hệ số nguyên? | `value.scale()`, có thể âm |
| **MathContext** | Phép tính/rounding được phép giữ bao nhiêu **chữ số có nghĩa**? | `new MathContext(precision, roundingMode)` |
| **RoundingMode** | Nếu phải bỏ chữ số thì chọn phía nào? | `RoundingMode.HALF_EVEN`, `HALF_UP`, v.v. |

**Quy tắc quan trọng nhất:** `setScale(2, mode)` yêu cầu hai chữ số **sau dấu thập phân**; `round(new MathContext(2, mode))` yêu cầu tối đa hai chữ số **có nghĩa**. Hai thao tác giải quyết hai vấn đề khác nhau và thường cho hai kết quả khác nhau.

```java
BigDecimal x = new BigDecimal("1234.567");

System.out.println(x.setScale(2, RoundingMode.HALF_UP)); // 1234.57
System.out.println(x.round(new MathContext(3, RoundingMode.HALF_UP))); // 1.23E+3
```

Kết quả thứ nhất giữ hai vị trí thập phân; kết quả thứ hai giữ ba chữ số có nghĩa, tương đương trị số `1230`. Các đoạn code ngắn trong bài giả định đã import `java.math.BigDecimal`, `java.math.MathContext` và `java.math.RoundingMode`; phần 11 cung cấp chương trình Java 17+ độc lập.

## 2. Công thức nền: unscaled value, scale và precision

Theo mô hình của `BigDecimal`:

```text
numeric value = unscaledValue × 10^(-scale)
```

`unscaledValue` là `BigInteger` có dấu; `scale` là số nguyên có thể dương, bằng 0 hoặc âm.

Ví dụ `new BigDecimal("123.4500")` có:

- `unscaledValue() = 1234500`
- `scale() = 4`
- `precision() = 7`
- Trị số bằng `1234500 × 10^-4 = 123.4500`

**Precision không phải “số chữ số trước dấu chấm” hay “số chữ số sau dấu chấm”.** Precision của `BigDecimal` là số chữ số thập phân trong phần hệ số nguyên không dấu, tính từ chữ số khác 0 đầu tiên; với giá trị 0, precision là 1.

| Cách tạo | `toString()` | `unscaledValue()` | `scale()` | `precision()` |
| --- | --- | ---: | ---: | ---: |
| `new BigDecimal("123.4500")` | `123.4500` | 1234500 | 4 | 7 |
| `new BigDecimal("12.34")` | `12.34` | 1234 | 2 | 4 |
| `new BigDecimal("0.00120")` | `0.00120` | 120 | 5 | 3 |
| `new BigDecimal("1000")` | `1000` | 1000 | 0 | 4 |
| `new BigDecimal("1E+3")` | `1E+3` | 1 | -3 | 1 |
| `new BigDecimal("0.000")` | `0.000` | 0 | 3 | 1 |

Bảng cho thấy hai biểu diễn `1000` và `1E+3` có **cùng trị số** nhưng scale/precision khác nhau; `BigDecimal` không chỉ giữ một số thực trừu tượng mà còn mang trạng thái biểu diễn decimal.

### 2.1. Scale âm không phải lỗi

`new BigDecimal("1E+3")` có scale `-3`, tức `1 × 10^3 = 1000`. Khi làm tròn về hàng chục/hàng trăm, `setScale` cũng có thể tạo scale âm:

```java
BigDecimal x = new BigDecimal("1234.56");
BigDecimal hundreds = x.setScale(-2, RoundingMode.HALF_UP);

System.out.println(hundreds);                // 1.2E+3
System.out.println(hundreds.toPlainString()); // 1200
System.out.println(hundreds.scale());         // -2
```

**Scale = -2** nghĩa là vị trí làm tròn ở **hàng trăm**, không phải “giữ -2 chữ số”. Giá trị đã làm tròn là 1200.

### 2.2. Adjusted exponent và biểu diễn khoa học

Một thuộc tính có ích khi đọc tài liệu là *adjusted exponent*:

```text
adjusted exponent = precision - scale - 1
```

Nó cho biết bậc của chữ số có nghĩa đầu tiên đối với các giá trị khác 0. Ví dụ `123.45` có precision 5, scale 2 nên adjusted exponent 2; viết dạng khoa học là khoảng `1.2345 × 10^2`.

`toString()` có thể chọn dạng khoa học; `toPlainString()` viết đủ chữ số không dùng `E`. **Không đánh đồng chuỗi hiển thị với scale**: parse lại chuỗi plain của một `BigDecimal` có scale âm có thể tạo đối tượng cùng trị số nhưng scale 0.

## 3. Bảng đối chiếu các thao tác scale và precision

| API | Ý nghĩa | Có thể phải làm tròn? | Thay đổi đối tượng nguồn? |
| --- | --- | --- | --- |
| `precision()` | Đếm chữ số của hệ số nguyên | Không | Không |
| `scale()` | Trả scale đang lưu | Không | Không |
| `unscaledValue()` | Trả hệ số nguyên `BigInteger` | Không | Không |
| `setScale(newScale)` | Yêu cầu scale cụ thể, **không cho mất chữ số có giá trị** | Nếu cần làm tròn sẽ ném `ArithmeticException` | Không |
| `setScale(newScale, mode)` | Yêu cầu scale cụ thể với mode | Có nếu giảm scale và phải bỏ chữ số khác 0 | Không |
| `round(mathContext)` | Làm tròn theo precision có nghĩa của context | Có nếu `precision > 0` và vượt mức cho phép | Không |
| `plus(mathContext)` | Trả giá trị theo context | Có thể | Không |
| `stripTrailingZeros()` | Bỏ số 0 cuối có thể bỏ trong biểu diễn, giữ trị số | Không đổi trị số | Không |
| `movePointLeft(n)` | Dịch dấu thập phân sang trái `n` vị trí | Không mất precision số học | Không |
| `movePointRight(n)` | Dịch dấu thập phân sang phải `n` vị trí | Không mất precision số học | Không |
| `toPlainString()` | Xuất decimal text không số mũ | Không | Không |

`BigDecimal` là **immutable**. Mỗi phép tính trả kết quả; lời gọi `x.setScale(2, mode);` mà không lưu kết quả **không thay** giá trị `x`. Không cần khẳng định JVM luôn cấp phát một object vật lý mới; điều quan trọng là **ngữ nghĩa bất biến**.

### 3.1. Thay đổi scale có thể chỉ thêm hoặc bỏ các số 0

```java
BigDecimal a = new BigDecimal("12.3");
BigDecimal moreZeros = a.setScale(4);
System.out.println(moreZeros); // 12.3000

BigDecimal b = new BigDecimal("12.3400");
BigDecimal fewerZeros = b.setScale(2);
System.out.println(fewerZeros); // 12.34
```

Hai lời gọi một tham số đều hợp lệ vì **không phải bỏ chữ số khác 0**. Nhưng `new BigDecimal("12.345").setScale(2)` ném `ArithmeticException` do mất chữ số `5`. Muốn cho phép làm tròn, truyền `RoundingMode` rõ ràng.

## 4. MathContext: precision tính toán và rounding mode

`MathContext` chứa hai trường chính:

- `precision`: **số chữ số có nghĩa** được giữ trong phép tính có context; precision `0` nghĩa là **không giới hạn**.
- `roundingMode`: cách chọn kết quả khi phải làm tròn. Nó không có nghĩa “áp dụng rounding dù không mất chữ số”.

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

BigDecimal original = new BigDecimal("1234.567");
BigDecimal rounded = original.round(mc);

System.out.println(rounded);                // 1.23E+3
System.out.println(rounded.toPlainString()); // 1230
```

Khi giới hạn ba chữ số có nghĩa, phép rounding giữ `1, 2, 3` rồi đổi bậc số; vì vậy scale kết quả có thể trở thành âm. `MathContext` **không buộc fixed-scale** và không thay thế `setScale` dùng ở ranh giới hiển thị hoặc lưu trữ.

### 4.1. Những MathContext định nghĩa sẵn

| Hằng số | Precision | Rounding mode | Cách hiểu |
| --- | ---: | --- | --- |
| `MathContext.UNLIMITED` | 0 | `HALF_UP` (ít ý nghĩa vì không ép làm tròn) | Số học thập phân chính xác nếu operation có biểu diễn hữu hạn |
| `MathContext.DECIMAL32` | 7 | `HALF_EVEN` | Giới hạn 7 chữ số có nghĩa |
| `MathContext.DECIMAL64` | 16 | `HALF_EVEN` | Giới hạn 16 chữ số có nghĩa |
| `MathContext.DECIMAL128` | 34 | `HALF_EVEN` | Giới hạn 34 chữ số có nghĩa |

Ba context `DECIMAL32`/`64`/`128` đặt precision và mode như trên; **đừng mặc định rằng chúng tự áp đặt mọi giới hạn exponent, xử lý NaN/Infinity và tất cả quy tắc encoding của định dạng phần cứng IEEE 754 tương ứng**. `BigDecimal` có mô hình kiểu dữ liệu riêng.

`MathContext.UNLIMITED` hoặc `new MathContext(0, mode)` **không phải phép màu** giúp `1 / 3` trở thành thập phân hữu hạn. Khi chia chính xác mà thương là chuỗi decimal vô hạn, `divide` vẫn có thể ném `ArithmeticException`.

### 4.2. Ví dụ context không tương đương setScale

```java
BigDecimal n = new BigDecimal("1234.567");
BigDecimal fixedDecimals = n.setScale(2, RoundingMode.HALF_UP);
BigDecimal significantDigits = n.round(
        new MathContext(3, RoundingMode.HALF_UP));

System.out.println(fixedDecimals);   // 1234.57
System.out.println(significantDigits); // 1.23E+3
```

Tham số 2 của `setScale` là **scale**, còn tham số 3 của `MathContext` là **precision**. Hai số này không cùng đơn vị.

## 5. RoundingMode: bảng đủ tám giá trị

`java.math.RoundingMode` là enum gồm tám chế độ. Bảng dưới làm tròn `+2.5` và `-2.5` tới **scale 0**; cả hai số nằm đúng điểm giữa hai số nguyên kề nhau.

| RoundingMode | Quy tắc | `+2.5 → scale 0` | `-2.5 → scale 0` |
| --- | --- | ---: | ---: |
| `UP` | **Ra xa 0** nếu có phần bị bỏ khác 0 | 3 | -3 |
| `DOWN` | **Về 0**, bỏ phần dư | 2 | -2 |
| `CEILING` | Về **dương vô cực** | 3 | -2 |
| `FLOOR` | Về **âm vô cực** | 2 | -3 |
| `HALF_UP` | Gần nhất, nếu hòa thì **xa 0** | 3 | -3 |
| `HALF_DOWN` | Gần nhất, nếu hòa thì **về 0** | 2 | -2 |
| `HALF_EVEN` | Gần nhất, nếu hòa thì chọn kết quả có chữ số cuối **chẵn** | 2 | -2 |
| `UNNECESSARY` | Yêu cầu kết quả **chính xác, không mất chữ số** | Exception | Exception |

### 5.1. Phân biệt UP/DOWN và CEILING/FLOOR

Tên `UP` trong `RoundingMode` **không đồng nghĩa tăng giá trị số học**: với `-2.5`, `UP` cho `-3` (xa 0), còn `CEILING` cho `-2` (hướng về +∞).

Với `+2.5`: `UP == CEILING` và `DOWN == FLOOR`.

Với `-2.5`: `UP == FLOOR` và `DOWN == CEILING`.

Đó là lý do mọi hàm rounding nhận giá trị có dấu cần test cả dương lẫn âm.

### 5.2. Ba chế độ HALF chỉ khác khi đúng midpoint

Với `2.4` về scale 0, `HALF_UP`, `HALF_DOWN` và `HALF_EVEN` đều cho 2.

Với `2.6`, cả ba đều cho 3.

Với `2.5`:

- `HALF_UP` → 3 (xa 0).
- `HALF_DOWN` → 2 (về 0).
- `HALF_EVEN` → 2 (chọn số chẵn).

Với `3.5`, `HALF_EVEN` → 4 (không phải lúc nào cũng xuống). Tính “even” xét chữ số cuối của **kết quả** ở vị trí đang làm tròn.

### 5.3. UNNECESSARY là công cụ validation

`RoundingMode.UNNECESSARY` không đồng nghĩa “làm tròn xuống” hoặc “không làm gì”. Nó nói: nếu operation yêu cầu làm tròn mất chữ số khác 0 thì **ném `ArithmeticException`**.

```java
System.out.println(new BigDecimal("1.2300")
        .setScale(2, RoundingMode.UNNECESSARY)); // 1.23

// Ném ArithmeticException vì phải bỏ chữ số 4:
new BigDecimal("1.234").setScale(2, RoundingMode.UNNECESSARY);
```

Sử dụng khi định dạng dữ liệu **phải đúng** với scale quy định; chẳng hạn trường đơn giá chỉ cho tối đa hai chữ số sau dấu phẩy mà chương trình không được tự làm tròn.

## 6. Bảng lựa chọn API theo yêu cầu

| Yêu cầu thực tế | API gợi ý | Điều phải chú ý |
| --- | --- | --- |
| Đọc số chữ số có nghĩa của value | `value.precision()` | Zero có precision 1. |
| Đọc số vị trí scale | `value.scale()` | Scale có thể âm. |
| Hiển thị đúng 2 chữ số thập phân | `value.setScale(2, mode).toPlainString()` | Mode do nghiệp vụ quy định. |
| Chỉ chấp nhận tối đa 2 số lẻ, không làm tròn | `value.setScale(2, UNNECESSARY)` | Thất bại nếu chữ số lẻ vượt mức và khác 0. |
| Làm tròn thành 3 chữ số có nghĩa | `value.round(new MathContext(3, mode))` | Không cố định số chữ số sau dấu chấm. |
| Tính decimal exact khi có thể | `add`, `subtract`, `multiply` không context | Vẫn cần quan tâm giới hạn bộ nhớ/scale và chia không hữu hạn. |
| Cộng/nhân với precision giới hạn | `add(other, mc)`, `multiply(other, mc)` | Kết quả từng bước có thể bị làm tròn. |
| Chia kết quả đúng 2 chữ số sau dấu chấm | `divide(divisor, 2, mode)` | Chọn rounding mode. |
| Chia kết quả tối đa p chữ số có nghĩa | `divide(divisor, new MathContext(p, mode))` | Scale kết quả không cố định. |
| Chia hoàn toàn chính xác | `divide(divisor)` | Nếu thương decimal vô hạn, ném exception. |
| Bỏ số 0 cuối không có ý nghĩa trị số | `stripTrailingZeros()` | Có thể làm scale trở thành âm. |
| Xuất decimal text không số mũ | `toPlainString()` | Không tự chọn scale hay rounding. |
| So sánh trị số thập phân | `a.compareTo(b) == 0` | `equals` còn xét scale. |

Lựa chọn đúng phụ thuộc vào **đơn vị nghĩa của yêu cầu**: “2 chữ số sau dấu chấm” là scale, “2 chữ số có nghĩa” là precision, “làm tròn nửa lên” là mode, “chỉ làm tròn ở bước kết toán” là **thời điểm áp dụng**.

## 7. Bảng tác động của phép toán lên precision và scale

Khi dùng `BigDecimal` không truyền `MathContext`, các phép toán cơ bản thường cho **kết quả thập phân chính xác**. Scale “ưu tiên” của các phép toán giúp người đọc dự đoán cách biểu diễn:

| Phép toán | Giá trị toán học | Scale ưu tiên của kết quả chính xác |
| --- | --- | --- |
| `a.add(b)` | `a + b` | `max(a.scale(), b.scale())` |
| `a.subtract(b)` | `a - b` | `max(a.scale(), b.scale())` |
| `a.multiply(b)` | `a × b` | `a.scale() + b.scale()` |
| `a.divide(b)` | `a / b` | `a.scale() - b.scale()` là scale ưu tiên, nhưng scale kết quả thực có thể tăng để biểu diễn thương hữu hạn chính xác |
| `a.setScale(s, mode)` | Trị số được làm tròn nếu cần | Chính xác `s` |
| `a.round(mc)` | Trị số giới hạn precision | Scale do kết quả rounding quyết định |

Ví dụ:

```java
BigDecimal a = new BigDecimal("1.20");  // scale 2
BigDecimal b = new BigDecimal("3.456"); // scale 3

System.out.println(a.add(b));      // 4.656 (scale 3)
System.out.println(a.multiply(b)); // 4.14720 (scale 5)
```

`multiply` giữ scale cộng từ các toán hạng và có thể in số 0 cuối. Kết quả trị số `4.14720` bằng `4.1472`, nhưng hai đối tượng có thể có scale khác nhau.

### Chia phải phân biệt “chính xác” và “bị làm tròn”

```java
System.out.println(new BigDecimal("1")
        .divide(new BigDecimal("8"))); // 0.125

System.out.println(new BigDecimal("1")
        .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP)); // 0.33

// Ném ArithmeticException vì 1/3 không thể là thập phân hữu hạn chính xác:
new BigDecimal("1").divide(new BigDecimal("3"));
```

Không phải cứ `BigDecimal` là tự động chia ra kết quả vô hạn chữ số. Quy tắc rounding là một phần API và cần được chỉ rõ ở phép chia.

### Khi dùng MathContext tại mỗi bước

`a.add(b, mc)`, `a.multiply(b, mc)` và `a.divide(b, mc)` có thể trả giá trị **đã được làm tròn ngay ở bước đó**. Nếu làm tròn sau từng bước rồi cộng tiếp, tổng cuối có thể khác việc tính ở precision đủ cao rồi làm tròn một lần cuối.

Ví dụ `RoundingMode.HALF_UP` với scale 2:

```text
round2(0.006) + round2(0.006) = 0.01 + 0.01 = 0.02
round2(0.006 + 0.006) = round2(0.012) = 0.01
```

Đây không phải bug của `BigDecimal`: hai quy trình có **thời điểm làm tròn khác nhau**. Nếu nghiệp vụ yêu cầu làm tròn mỗi dòng hóa đơn, cách đầu có thể đúng; nếu chỉ làm tròn tổng cuối, cách sau phù hợp. Cần xác định trước khi dùng context trong mọi phép toán.

## 8. Bảng dấu âm, điểm giữa và các ranh giới

Những test chỉ có số dương dễ bỏ qua lỗi rounding.

| Giá trị gốc | Scale đích | Mode | Kết quả | Lý do |
| --- | ---: | --- | --- | --- |
| `2.5` | 0 | `HALF_UP` | `3` | Midpoint, xa 0 |
| `-2.5` | 0 | `HALF_UP` | `-3` | Midpoint, xa 0 |
| `2.5` | 0 | `HALF_DOWN` | `2` | Midpoint, về 0 |
| `-2.5` | 0 | `HALF_DOWN` | `-2` | Midpoint, về 0 |
| `2.5` | 0 | `HALF_EVEN` | `2` | Midpoint, kết quả chẵn |
| `3.5` | 0 | `HALF_EVEN` | `4` | Midpoint, kết quả chẵn |
| `-2.5` | 0 | `CEILING` | `-2` | Hướng +∞ |
| `-2.5` | 0 | `FLOOR` | `-3` | Hướng -∞ |
| `1.2300` | 2 | `UNNECESSARY` | `1.23` | Bỏ zero, không mất trị số |
| `1.234` | 2 | `UNNECESSARY` | Lỗi | Bỏ chữ số khác 0 |
| `1234.56` | -2 | `HALF_UP` | `1200` | Làm tròn về hàng trăm |

**Phân biệt với `Math.round`:** `Math.round(-2.5)` trả `-2` vì quy tắc điểm giữa của `Math.round` ưu tiên phía dương vô cực, trong khi `new BigDecimal("-2.5").setScale(0, HALF_UP)` trả `-3`. Không đổi qua lại hai API chỉ vì cùng được gọi là “rounding”.

## 9. Precision/scale sau stripTrailingZeros và khi so sánh

`stripTrailingZeros()` bỏ những số 0 cuối không làm đổi trị số. Nhưng representation/scale có thể thay đổi đáng kể:

```java
BigDecimal before = new BigDecimal("1000.00");
BigDecimal after = before.stripTrailingZeros();

System.out.println(before.scale());         // 2
System.out.println(after);                  // 1E+3
System.out.println(after.scale());           // -3
System.out.println(after.toPlainString());  // 1000
```

Với `new BigDecimal("0.000").stripTrailingZeros()`, kết quả là zero chuẩn hóa có scale 0, không còn scale 3.

**Chú ý `equals` khác `compareTo`:**

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.equals(b));             // false
System.out.println(a.compareTo(b) == 0);     // true
```

`equals` phân biệt scale vì hai biểu diễn khác nhau. `compareTo` so sánh **giá trị số học**. Điều này ảnh hưởng đến khóa `HashMap`/`HashSet` khi dùng `BigDecimal` khác scale: các collection phụ thuộc `equals` không xử lý chúng như cùng object theo trị số.

Nếu hợp đồng định danh là “cùng giá trị decimal”, nên chọn quy tắc chuẩn hóa đầu vào hoặc cấu trúc key phù hợp. Nhưng đừng `stripTrailingZeros` bừa bãi với dữ liệu mà số chữ số lẻ thể hiện ý nghĩa nghiệp vụ (ví dụ các đơn vị tính hoặc format chuẩn).

## 10. Những lựa chọn thiết kế thực tế

### 10.1. Tiền và hóa đơn

Với các giá trị decimal cần tính theo quy định, thường chọn:

1. Đọc decimal chính xác từ chuỗi hoặc kiểu decimal của database, không đi qua `double` không cần thiết.
2. Giữ precision trung gian theo phép toán và hợp đồng.
3. Chỉ áp dụng `setScale(targetScale, roundingMode)` tại **ranh giới làm tròn được chỉ định**.
4. Xuất chuỗi bằng `toPlainString` hoặc formatter theo locale; format không tự thay thế chính sách rounding.

`BigDecimal.valueOf(0.1)` thường tạo biểu diễn `0.1` mong đợi từ literal `double` này, còn `new BigDecimal(0.1)` giữ chính xác giá trị nhị phân xấp xỉ của `double`. Tốt nhất là parse thẳng decimal từ chuỗi khi dữ liệu gốc có sẵn dưới dạng văn bản decimal.

Không mặc định mọi đơn vị tiền tệ đều dùng scale 2 hoặc cùng rounding mode. Luật, hợp đồng, loại tiền và vị trí áp dụng rounding mới quyết định.

### 10.2. Đo lường và số liệu khoa học

Một số đo như `1.230 m` có thể mang thông tin về độ phân giải thiết bị. `precision()` là thuộc tính **biểu diễn BigDecimal**, không phải tự động là sai số đo hoặc số chữ số đáng tin của cảm biến. Có thể cần mô hình đơn vị và sai số đo riêng.

Nếu dữ liệu bản chất xấp xỉ và thuật toán cần hiệu năng, `double` có thể hợp lý. Nếu yêu cầu thập phân chính xác và có quy tắc rounding, `BigDecimal` phù hợp hơn. Không chọn kiểu số chỉ theo thói quen.

### 10.3. Lưu trong database hoặc API

Khi database quy định `DECIMAL(p,s)`, `p` và `s` thường là **precision tổng** và **scale**, nhưng cách cưỡng chế, làm tròn, tràn và giới hạn phụ thuộc hệ quản trị. Phải kiểm tra hợp đồng schema thật; không tự suy rằng `new MathContext(p)` thay thế kiểm tra toàn bộ ràng buộc `DECIMAL(p,s)`.

Ví dụ số có precision ≤9 **và** scale 2 có thể cần kiểm tra số chữ số ở phần nguyên, scale đích và hành vi rounding theo quy định. Một context precision 9 không tự bảo đảm scale chính xác 2.

### 10.4. Lỗi do chia hai lần hoặc round hai lần

`round(finalScale, round(intermediateScale, value))` có thể khác `round(finalScale, value)` với các chế độ gần nhất. Ví dụ `1.245`:

```text
setScale(2, HALF_UP) => 1.25
rồi setScale(1, HALF_UP) => 1.3

setScale(1, HALF_UP) trực tiếp => 1.2
```

Một bước rounding sớm có thể làm mất thông tin vị trí tương đối so với ngưỡng midpoint cuối. Khi cần kết quả có thể kiểm toán, lưu đầy đủ giá trị và chỉ áp dụng rounding tại điểm nghiệp vụ được phép.

## 11. Chương trình Java 17+ hoàn chỉnh (đã kiểm tra kết quả)

Lưu thành file **`PrecisionScaleReferenceDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class PrecisionScaleReferenceDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("123.4500");
        BigDecimal z = new BigDecimal("0.000");
        BigDecimal e = new BigDecimal("1E+3");
        BigDecimal stripped = new BigDecimal("1000.00")
                .stripTrailingZeros();

        System.out.println("a precision=" + a.precision()
                + ", scale=" + a.scale());
        System.out.println("z precision=" + z.precision()
                + ", scale=" + z.scale());
        System.out.println("e precision=" + e.precision()
                + ", scale=" + e.scale());
        System.out.println("stripped=" + stripped
                + ", precision=" + stripped.precision()
                + ", scale=" + stripped.scale());

        BigDecimal x = new BigDecimal("1234.567");
        System.out.println("setScale="
                + x.setScale(2, RoundingMode.HALF_UP));
        BigDecimal rounded = x.round(
                new MathContext(3, RoundingMode.HALF_UP));
        System.out.println("mathContext=" + rounded);
        System.out.println("contextPlain=" + rounded.toPlainString());

        System.out.println("exactAdd="
                + new BigDecimal("1.20")
                        .add(new BigDecimal("3.456")));
        System.out.println("exactMultiply="
                + new BigDecimal("1.20")
                        .multiply(new BigDecimal("3.456")));
        System.out.println("divideRounded="
                + new BigDecimal("1")
                        .divide(new BigDecimal("3"), 2,
                                RoundingMode.HALF_UP));

        System.out.println("halfUpPositive="
                + new BigDecimal("2.5")
                        .setScale(0, RoundingMode.HALF_UP));
        System.out.println("halfUpNegative="
                + new BigDecimal("-2.5")
                        .setScale(0, RoundingMode.HALF_UP));
        System.out.println("halfEvenPositive="
                + new BigDecimal("2.5")
                        .setScale(0, RoundingMode.HALF_EVEN));
        System.out.println("halfEvenNegative="
                + new BigDecimal("-2.5")
                        .setScale(0, RoundingMode.HALF_EVEN));
        System.out.println("ceilingNegative="
                + new BigDecimal("-2.5")
                        .setScale(0, RoundingMode.CEILING));
        System.out.println("floorNegative="
                + new BigDecimal("-2.5")
                        .setScale(0, RoundingMode.FLOOR));

        BigDecimal hundreds = new BigDecimal("1234.56")
                .setScale(-2, RoundingMode.HALF_UP);
        System.out.println("negativeScale=" + hundreds);
        System.out.println("negativeScalePlain="
                + hundreds.toPlainString());

        System.out.println("unlimitedPrecision="
                + MathContext.UNLIMITED.getPrecision());
        System.out.println("decimal64Precision="
                + MathContext.DECIMAL64.getPrecision());
        System.out.println("trailingZeroScale="
                + z.stripTrailingZeros().scale());
    }
}
```

Chạy trong thư mục chứa file với JDK 17 trở lên:

```bash
javac PrecisionScaleReferenceDemo.java
java PrecisionScaleReferenceDemo
```

**Đầu ra đã đối chiếu khi chạy chương trình:**

```text
a precision=7, scale=4
z precision=1, scale=3
e precision=1, scale=-3
stripped=1E+3, precision=1, scale=-3
setScale=1234.57
mathContext=1.23E+3
contextPlain=1230
exactAdd=4.656
exactMultiply=4.14720
divideRounded=0.33
halfUpPositive=3
halfUpNegative=-3
halfEvenPositive=2
halfEvenNegative=-2
ceilingNegative=-2
floorNegative=-3
negativeScale=1.2E+3
negativeScalePlain=1200
unlimitedPrecision=0
decimal64Precision=16
trailingZeroScale=0
```

Điều cần đối chiếu trong kết quả:

- `a` có precision 7 dù phần thập phân hiển thị có bốn vị trí; `z` có precision 1 dù có ba chữ số 0 sau dấu chấm.
- `setScale` và `MathContext` cho hai kết quả khác vì ràng buộc khác nhau.
- Scale âm tạo chuỗi khoa học khi gọi `toString()`, nhưng `toPlainString()` viết đầy đủ số 0.
- `HALF_UP` đi ra xa 0 khi gặp midpoint; `HALF_EVEN` chọn kết quả chẵn.
- `MathContext.UNLIMITED.getPrecision() == 0` là ký hiệu **không giới hạn precision**, không phải “0 chữ số được giữ”.

## 12. Mẫu code ngắn để kiểm tra nghiệp vụ

### Mẫu A: yêu cầu đúng hai số lẻ, không cho làm tròn

```java
static BigDecimal requireTwoDecimals(BigDecimal value) {
    return value.setScale(2, RoundingMode.UNNECESSARY);
}
```

Nếu value là `1.2300`, kết quả `1.23`; nếu là `1.234`, ném `ArithmeticException`. Phương thức này chỉ minh họa chính sách xác thực, cần đặt trong lớp Java.

### Mẫu B: chỉ làm tròn khi kết toán

```java
static BigDecimal sumThenRound(
        java.util.List<BigDecimal> values) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal value : values) {
        total = total.add(value);
    }
    return total.setScale(2, RoundingMode.HALF_UP);
}
```

Đây là chính sách mẫu; không phù hợp nếu quy tắc bắt buộc làm tròn **từng khoản trước khi cộng**. Cần tài liệu hóa lý do chọn scale 2 và mode thay vì rải hằng số này khắp codebase.

### Mẫu C: so sánh theo trị số

```java
static boolean numericallyEqual(
        BigDecimal left, BigDecimal right) {
    return left.compareTo(right) == 0;
}
```

`new BigDecimal("1.0")` và `new BigDecimal("1.00")` so sánh theo trị số là bằng nhau, nhưng `equals` cho false vì scale khác. Nếu cần dùng làm khóa hash, nên chuẩn hóa trước theo hợp đồng, không áp dụng ngẫu nhiên chiến lược so sánh khác nhau giữa các lớp.

### Mẫu D: chia với scale tường minh

```java
static BigDecimal divideToTwoDecimals(
        BigDecimal numerator, BigDecimal denominator) {
    return numerator.divide(denominator, 2,
            RoundingMode.HALF_EVEN);
}
```

Phải kiểm tra mẫu số khác 0 và chọn mode theo nghiệp vụ. Đây cũng chỉ là phương thức minh họa.

## 13. Chẩn đoán nhanh các triệu chứng thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Hướng xử lý |
| --- | --- | --- |
| `setScale(2)` ném `ArithmeticException` | Cần loại chữ số khác 0 nhưng không truyền mode | Truyền mode **nếu được phép làm tròn**, hoặc dùng `UNNECESSARY` để chủ ý từ chối |
| `divide()` ném lỗi với `1/3` | Thương decimal không hữu hạn | Chọn scale + mode hoặc MathContext theo yêu cầu |
| Kết quả `1.23E+3` thay vì `1230` | Scale âm và cách `toString()` biểu diễn | Dùng `toPlainString()` nếu cần decimal text không số mũ |
| `precision()` ra 1 cho `0.000` | Precision của zero được định nghĩa là 1 | Đừng đồng nhất precision với số chữ số lẻ |
| `setScale(2)` và `round(mc)` khác số | Fixed-scale khác significant digits | Rà lại yêu cầu thật: vị trí decimal hay chữ số có nghĩa? |
| `equals` false dù nhìn số giống nhau | Scale khác nhau | Dùng `compareTo() == 0` nếu chỉ cần bằng trị số |
| Sau `stripTrailingZeros()` xuất hiện `1E+3` | Các zero cuối bị bỏ, scale chuyển âm | Xem yêu cầu giữ scale/trailing zero và chuỗi output |
| Tổng khi song song/làm tròn từng bước bị lệch | Rounding trung gian đổi kết quả | Đặt ranh giới rounding rõ, không làm tròn tùy ý |
| `MathContext.UNLIMITED` vẫn chia `1/3` lỗi | Unlimited precision không tạo decimal vô hạn | Xác định chính sách approximate/round hoặc biểu diễn phân số |
| `new BigDecimal(0.1)` có nhiều chữ số lạ | `double` đầu vào là xấp xỉ nhị phân | Dùng chuỗi decimal hoặc API chuyển phù hợp |

## 14. Câu hỏi tra cứu nhanh

**Câu 1.** `new BigDecimal("0.00120")` có precision và scale bao nhiêu?

**Đáp án:** Precision 3 (các chữ số có nghĩa 1,2,0), scale 5.

**Câu 2.** `new BigDecimal("1E+3")` có scale bao nhiêu?

**Đáp án:** Scale `-3`, precision 1. Trị số là 1000.

**Câu 3.** Muốn `"12.30"` từ `new BigDecimal("12.3")` nên dùng gì?

**Đáp án:** `setScale(2).toPlainString()`, vì thêm zero không làm mất chữ số có giá trị.

**Câu 4.** `setScale(2, HALF_UP)` có giống `round(new MathContext(2, HALF_UP))` không?

**Đáp án:** Không. Một bên giữ hai chữ số thập phân, bên kia giữ hai chữ số có nghĩa.

**Câu 5.** Chế độ nào làm `-2.5` thành `-3` ở scale 0?

**Đáp án:** `UP`, `FLOOR` và `HALF_UP`; `HALF_EVEN` chọn `-2` vì chẵn.

**Câu 6.** `RoundingMode.UNNECESSARY` dùng khi nào?

**Đáp án:** Khi operation phải chính xác tại scale/precision yêu cầu; nếu bắt buộc bỏ chữ số khác 0, API ném `ArithmeticException`.

**Câu 7.** `MathContext.DECIMAL64` có precision/mode nào?

**Đáp án:** Precision 16 và `HALF_EVEN`.

**Câu 8.** `stripTrailingZeros()` có thể tạo scale âm không?

**Đáp án:** Có; `new BigDecimal("1000.00").stripTrailingZeros()` thành biểu diễn `1E+3`, scale `-3`.

**Câu 9.** `BigDecimal.ONE.divide(new BigDecimal("3"))` có trả decimal exact không?

**Đáp án:** Không. Nó ném `ArithmeticException` vì `1/3` không có biểu diễn decimal hữu hạn.

**Câu 10.** Khi nào phải làm tròn tổng thay vì từng phần tử?

**Đáp án:** Khi hợp đồng nghiệp vụ chỉ định ranh giới rounding tại tổng cuối. Không thể suy chính sách từ kiểu `BigDecimal` hay `MathContext`; hai lựa chọn có thể tạo kết quả khác nhau.

## 15. Tài liệu chính thức

- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `precision`, `scale`, `setScale`, `round`, `stripTrailingZeros` và phép toán.
- [Java SE 17 — MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): precision có nghĩa, rounding mode và các context hằng.
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): bảng hành vi đầy đủ các chế độ rounding.

**Tổng kết để tra cứu:** `precision()` đo **chữ số có nghĩa của biểu diễn**, `scale()` xác định **vị trí dấu thập phân**, `MathContext` giới hạn **precision khi tính/round**, `RoundingMode` quyết định **làm tròn về phía nào**, và `setScale` áp dụng một **scale kết quả cụ thể**. Luôn xác định cả **thời điểm làm tròn** và **ý nghĩa nghiệp vụ** trước khi kết hợp các API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-007">Bảng parse, format và chuyển đổi exact</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu parse, format và chuyển đổi exact trong Java Core Numbers

## 1. Ba thao tác khác nhau, không thể dùng thay thế tùy tiện

Khi làm việc với số trong Java, cần phân biệt:

1. **Parse (đọc/giải mã):** từ `String` hoặc nguồn văn bản thành giá trị số. Ví dụ `Integer.parseInt("42")`.
2. **Format (biểu diễn):** từ giá trị số thành `String` theo cơ số, số chữ số, dấu phân cách hoặc `Locale`. Ví dụ `number.toPlainString()` hay `DecimalFormat.format(...)`.
3. **Convert (chuyển kiểu):** từ một kiểu số sang kiểu số khác. Một số phép chuyển bảo toàn giá trị; một số **cắt phần lẻ, overflow, làm tròn hoặc mất precision**. Ví dụ `BigDecimal.intValueExact()` khác hẳn `BigDecimal.intValue()`.

Từ khóa **exact** ở đây có ý nghĩa cụ thể: phép chuyển hoặc phép toán phải **không mất giá trị toán học**, hoặc sẽ **ném ngoại lệ** khi không thể bảo toàn theo điều kiện của API. Exact không đồng nghĩa "giữ nguyên chuỗi hiển thị", "giữ nguyên scale" trong mọi trường hợp, hay "mọi phép tính số thực đều chính xác".

Các câu hỏi tra cứu nhanh:

- Chuỗi là thập phân hay cơ số 2/8/16? Có dấu `+`/`-`, tiền tố `0x`, dấu phân cách hàng nghìn?
- Kiểu đích là `int`/`long`, `BigInteger`, `double` hay `BigDecimal`?
- Đầu vào có khả năng `null`, rỗng, ký hiệu `NaN`/`Infinity` hoặc vượt miền số?
- Kết quả cần **giá trị chính xác**, **thập phân với scale cố định**, hay **format theo vùng miền**?
- Nếu không thể chuyển exact, cần ném lỗi, chọn rounding được chỉ định hay dùng kiểu lớn hơn?

## 2. Bảng parse String → kiểu số

| Kiểu cần nhận | Cách parse | Ví dụ | Lưu ý quan trọng |
|---|---|---|---|
| `byte` | `Byte.parseByte(s)` | `"127" → 127` | Chỉ trong miền [-128, 127] |
| `short` | `Short.parseShort(s)` | `"32000" → 32000` | Chỉ trong miền [-32768, 32767] |
| `int` | `Integer.parseInt(s)` | `"42" → 42` | Mặc định thập phân có dấu; sai cú pháp/vượt miền → `NumberFormatException` |
| `long` | `Long.parseLong(s)` | `"9000000000" → 9000000000L` | Cần literal/hàm có kiểu `long` tương ứng |
| `float` | `Float.parseFloat(s)` | `"1.25" → 1.25f` | IEEE 754; có thể làm tròn, hỗ trợ `NaN`/Infinity |
| `double` | `Double.parseDouble(s)` | `"1.25e2" → 125.0` | IEEE 754; chuỗi hữu hạn về cú pháp vẫn có thể vượt phạm vi và thành Infinity |
| `BigInteger` | `new BigInteger(s)` | `"12345678901234567890"` | Số nguyên độ lớn tùy ý theo tài nguyên máy; không nhận phần thập phân |
| `BigDecimal` | `new BigDecimal(s)` | `"123.4500"` | Bảo toàn giá trị thập phân được viết và scale được biểu đạt |
| Wrapper đối tượng | `Integer.valueOf(s)`, `Long.valueOf(s)`... | `Integer.valueOf("42")` | Trả wrapper; không mặc nhiên an toàn với `null` |

Đối với `Integer.parseInt`/`Long.parseLong`, chuỗi có dấu cộng hoặc trừ được hỗ trợ khi cú pháp hợp lệ:

~~~java
int positive = Integer.parseInt("+42"); // 42
int negative = Integer.parseInt("-42"); // -42
~~~

Nhưng dấu phân cách không tự động được chấp nhận:

~~~java
// Integer.parseInt("1,234"); // NumberFormatException
// Integer.parseInt("42.0");  // NumberFormatException
// Integer.parseInt(" 42 ");  // NumberFormatException
~~~

Muốn nhận chuỗi có khoảng trắng, có thể chuẩn hóa theo contract:

~~~java
String raw = " 42 ";
int number = Integer.parseInt(raw.trim()); // 42
~~~

Lưu ý: `trim()` thay đổi chuỗi đầu vào và không đồng nghĩa với xử lý mọi loại khoảng trắng Unicode. Hợp đồng nhập liệu phải quy định có được bỏ khoảng trắng không; đừng chuẩn hóa thầm nếu dữ liệu đòi dạng chặt chẽ.

**Quan trọng:** `Double.parseDouble("NaN")` có thể trả NaN mà **không ném ngoại lệ**. `Double.parseDouble("Infinity")` trả vô cực dương; `Double.parseDouble("1e400")` có thể trở thành vô cực do vượt phạm vi hữu hạn của `double`. Khi ứng dụng chỉ cho phép số hữu hạn, parse xong phải kiểm tra `Double.isFinite(result)`.

## 3. Parse theo cơ số (radix): 2, 8, 10, 16

Có hai nhóm API dễ nhầm.

### 3.1. parseInt(s, radix), parseLong(s, radix)

~~~java
int binary = Integer.parseInt("101010", 2); // 42
int octal = Integer.parseInt("52", 8);      // 42
int hex = Integer.parseInt("2A", 16);       // 42
long l = Long.parseLong("FF", 16);          // 255L
~~~

Tham số `radix` nằm trong khoảng `Character.MIN_RADIX` đến `Character.MAX_RADIX` (2 đến 36). Các chữ cái A–Z/a–z đại diện các chữ số từ 10 đến 35 khi phù hợp. Lưu ý `parseInt("0x2A", 16)` **không** nhận tiền tố `0x` như một chữ số hex hợp lệ; muốn parse chuỗi với tiền tố, có thể dùng `Integer.decode(...)` khi phù hợp.

~~~java
int hex = Integer.decode("0x2A"); // 42
int oct = Integer.decode("052");  // 42 theo cơ số 8
~~~

### 3.2. decode xử lý tiền tố khác với parseInt(radix)

| Chuỗi | `Integer.parseInt(s)` | `Integer.decode(s)` | Giải thích |
|---|---|---|---|
| `"42"` | 42 | 42 | Decimal thông thường |
| `"0x2A"` | Lỗi | 42 | `decode` nhận `0x`/`0X` hoặc `#` |
| `"#2A"` | Lỗi | 42 | Tiền tố hex của `decode` |
| `"052"` | 52 | 42 | `decode` xem số bắt đầu bằng 0 là octal |
| `"08"` | 8 | Lỗi | `8` không phải chữ số octal |
| `"-0x2A"` | Lỗi | -42 | `decode` hỗ trợ dấu trước tiền tố |

`Long.decode` tương tự nhưng cho kiểu `Long`. Hãy dùng `decode` chỉ khi định dạng nguồn thực sự chấp nhận quy tắc tiền tố và octal cũ. Nếu mục tiêu là số nguyên thập phân `"052"` nghĩa là 52, `Integer.parseInt(s, 10)` rõ ràng hơn.

### 3.3. BigInteger theo cơ số

~~~java
BigInteger a = new BigInteger("FF", 16);  // 255
BigInteger b = new BigInteger("1010", 2); // 10

System.out.println(a.toString(16)); // ff
System.out.println(b.toString(2));  // 1010
~~~

`BigInteger(String, radix)` giải mã chữ số theo cơ số được chỉ định; `toString(radix)` xuất cơ số tương ứng. Constructor không tự nhận tiền tố `0x` theo cách của `decode`. `BigInteger` cho số nguyên có độ lớn vượt `long` mà không cần giả làm số nguyên có dấu 64 bit.

## 4. Số nguyên không dấu: parseUnsignedInt/parseUnsignedLong

Java `int` và `long` vẫn là các kiểu nguyên thủy **có dấu**. Các API "unsigned" diễn giải mẫu bit theo miền số không dấu, không tạo một kiểu primitive mới:

~~~java
int bits = Integer.parseUnsignedInt("4294967295");
System.out.println(bits);                          // -1 (nếu in như int có dấu)
System.out.println(Integer.toUnsignedString(bits)); // 4294967295
System.out.println(Integer.toUnsignedLong(bits));   // 4294967295
~~~

| API | Ý nghĩa |
|---|---|
| `Integer.parseUnsignedInt(s)` | Parse số nguyên không dấu 32 bit thập phân vào mẫu bit của `int` |
| `Integer.parseUnsignedInt(s, radix)` | Parse số nguyên không dấu theo cơ số |
| `Integer.toUnsignedString(int)` | In mẫu bit `int` theo giá trị 0..2^32−1 |
| `Integer.toUnsignedLong(int)` | Nâng giá trị `int` không dấu thành `long` dương, bảo toàn số 0..2^32−1 |
| `Long.parseUnsignedLong(s)` | Parse số không dấu 64 bit vào mẫu bit `long` |
| `Long.toUnsignedString(long)` | In mẫu bit `long` theo giá trị không dấu 0..2^64−1 |

Ví dụ với `Long.parseUnsignedLong("18446744073709551615")`, mẫu bit kết quả có dạng `-1L` khi hiểu là `long` có dấu; `Long.toUnsignedString(...)` sẽ in lại `18446744073709551615`. Muốn thao tác số nguyên vượt miền signed `long` theo **giá trị số học** mà không chỉ thao tác bit, `BigInteger` thường rõ ràng hơn.

Đừng dùng `parseUnsignedInt` rồi tưởng một `int` sẽ biểu diễn số 4.294.967.295 như một `int` dương: điều đó không thể vì `int` chỉ có 32 bit và miền signed tối đa là 2.147.483.647.

## 5. Bảng chuyển đổi số → String (không định dạng locale)

| Mục tiêu | API | Ví dụ/kết quả | Lưu ý |
|---|---|---|---|
| Primitive bất kỳ → chuỗi mặc định | `String.valueOf(value)` | `String.valueOf(42) → "42"` | Có overload cho primitive; không thêm dấu nhóm |
| `int` → decimal | `Integer.toString(42)` | `"42"` | Không thay đổi giá trị |
| `long` → decimal | `Long.toString(42L)` | `"42"` | Không có grouping |
| `int` → hex | `Integer.toHexString(255)` | `"ff"` | Với số âm, thể hiện mẫu bit hai bù; cần hiểu semantics |
| `int` → binary | `Integer.toBinaryString(10)` | `"1010"` | Thể hiện bit theo quy tắc API |
| `int` → octal | `Integer.toOctalString(8)` | `"10"` | Không tự thêm tiền tố `0` |
| `BigInteger` → decimal | `bigInteger.toString()` | Chuỗi số nguyên thập phân | Không giới hạn `int/long` |
| `BigInteger` → radix | `bigInteger.toString(16)` | `255 → "ff"` | Chữ cái thường |
| `BigDecimal` → canonical | `value.toString()` | `1E+4 → "1E+4"` | Parse lại với `BigDecimal(String)` giữ cả giá trị và scale |
| `BigDecimal` → plain | `value.toPlainString()` | `1E+4 → "10000"` | Không có `E`; parse lại có thể mất scale âm |
| `BigDecimal` → engineering | `value.toEngineeringString()` | `1E+4 → "10E+3"` | Nếu dùng số mũ, số mũ bội của 3; parse lại có thể đổi scale |
| `double` → canonical | `Double.toString(value)` | `125.0 → "125.0"` | Có thể dùng dạng `E`, `NaN`, `Infinity` |
| `float` → canonical | `Float.toString(value)` | `1.5f → "1.5"` | Biểu diễn ngắn cho giá trị nhị phân theo quy tắc Java |

Ví dụ phân biệt ba phương thức của `BigDecimal`:

~~~java
BigDecimal x = new BigDecimal("1E+4");

System.out.println(x.toString());            // 1E+4
System.out.println(x.toEngineeringString()); // 10E+3
System.out.println(x.toPlainString());       // 10000
~~~

Cả ba chuỗi thể hiện cùng giá trị `10000`, nhưng **không có bảo đảm cả ba khi parse lại sẽ giữ cùng scale**:

~~~java
BigDecimal original = new BigDecimal("1E+4"); // scale -4
BigDecimal fromPlain = new BigDecimal(original.toPlainString()); // scale 0

System.out.println(original.compareTo(fromPlain) == 0); // true
System.out.println(original.equals(fromPlain));          // false
~~~

`compareTo` so giá trị; `equals` so cả giá trị lẫn scale. Nếu cần serialize rồi parse lại để bảo toàn biểu diễn `BigDecimal`, `toString()` phù hợp hơn `toPlainString()`/`toEngineeringString()`.

## 6. Format theo locale, số chữ số lẻ và dấu nhóm

**Parse/format mặc định của `Integer.parseInt`, `BigDecimal(String)` và `toString()` không dùng dấu phân cách locale**, thường dùng dấu `.` làm dấu thập phân trong chuỗi Java. Nhưng giao diện người dùng có thể cần dấu nhóm và dấu thập phân của từng vùng.

### 6.1. String.format với Locale rõ ràng

~~~java
import java.util.Locale;

String text = String.format(Locale.US, "%,.2f", 12345.67);
// "12,345.67"
~~~

Với format string, `%,.2f` có nghĩa hiển thị số thực với dấu nhóm theo locale và hai chữ số sau dấu thập phân. `%.2f` không phải cơ chế chính để xác định **quy tắc làm tròn tài chính**; khi cần rounding mode nghiệp vụ, hãy chọn và kiểm soát phép làm tròn rõ ràng trước khi xuất.

Một số conversion thường gặp:

| Ký hiệu trong format | Ví dụ | Ý nghĩa |
|---|---|---|
| `%d` | `String.format(Locale.US, "%d", 42)` | Số nguyên decimal |
| `%x` | `String.format(Locale.US, "%x", 255)` | Hex lowercase |
| `%o` | `String.format(Locale.US, "%o", 8)` | Octal |
| `%.2f` | Format số thực 2 chữ số lẻ | Dạng fixed-point, chịu quy tắc của Formatter |
| `%e` | Format số thực dạng số mũ | Dạng scientific theo Formatter |
| `%,.2f` | `12,345.67` | Grouping và 2 chữ số lẻ theo locale |
| `%s` | `String.format("%s", value)` | Chuỗi biểu diễn đối tượng, không phải format số chuyên biệt |

Lưu ý `%d` không áp dụng cho `Double`; loại đối số và conversion phải tương thích. `String.format` có overload nhận `Locale`; không truyền `Locale` có thể khiến kết quả phụ thuộc locale mặc định của JVM.

### 6.2. DecimalFormat / NumberFormat

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

DecimalFormat format = (DecimalFormat)
        NumberFormat.getNumberInstance(Locale.US);

format.setGroupingUsed(true);
format.setMinimumFractionDigits(2);
format.setMaximumFractionDigits(2);
format.setRoundingMode(RoundingMode.HALF_UP);

String output = format.format(new BigDecimal("12345.675"));
// "12,345.68"
~~~

Ở đây `RoundingMode.HALF_UP` được chỉ định rõ. `setMinimumFractionDigits(2)` đệm đủ hai vị trí lẻ khi cần; `setMaximumFractionDigits(2)` hạn chế số vị trí lẻ ở đầu ra. **Việc format không thay đổi đối tượng `BigDecimal` gốc.**

`NumberFormat` và `DecimalFormat` là đối tượng **mutable**, nhìn chung không an toàn để dùng đồng thời từ nhiều thread mà không có phối hợp thích hợp. Trong request nhiều thread, dùng instance cho từng ngữ cảnh hoặc thiết kế tương ứng, không vô tư chia sẻ một formatter rồi sửa thông số.

Khi cần format tiền, có thể dùng `NumberFormat.getCurrencyInstance(locale)`, nhưng ký hiệu tiền tệ, nhóm và số chữ số lẻ là **quy tắc hiển thị**, không đủ để xác định hoàn chỉnh việc tính tiền, đơn vị đồng tiền, làm tròn giao dịch hay chuyển đổi tỷ giá.

## 7. Parse số có định dạng locale: khác parse số kỹ thuật

Chuỗi `"1,234.50"` dùng dấu phẩy ngăn nghìn theo `Locale.US`:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

DecimalFormat parser = (DecimalFormat)
        NumberFormat.getNumberInstance(Locale.US);
parser.setParseBigDecimal(true);

Number value = parser.parse("1,234.50"); // Có thể ném ParseException.
BigDecimal decimal = (BigDecimal) value; // 1234.50 trong ví dụ hữu hạn này.
~~~

**Không dùng** `new BigDecimal("1,234.50")`: constructor của `BigDecimal` không parse dấu grouping locale. Và `Integer.parseInt("1,234")` cũng không chấp nhận grouping.

### 7.1. Parse locale có thể chấp nhận tiền tố chuỗi — phải kiểm tra hết đầu vào

`NumberFormat.parse(String)` có thể xử lý linh hoạt hơn parser số kỹ thuật và không nhất thiết từ chối toàn bộ chuỗi có phần dư không hợp lệ. Với dữ liệu cần kiểm soát chặt, dùng `ParsePosition`:

~~~java
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

String input = "1,234.50";
NumberFormat parser = NumberFormat.getNumberInstance(Locale.US);
ParsePosition position = new ParsePosition(0);

Number parsed = parser.parse(input, position);
if (parsed == null || position.getIndex() != input.length()) {
    throw new IllegalArgumentException("Invalid numeric input");
}
~~~

Kiểm tra `getIndex()` đảm bảo **tất cả ký tự đã được tiêu thụ**. Nhưng điều này **chưa đủ** nếu hợp đồng còn yêu cầu định dạng nhóm hoàn toàn chính xác: parser locale có thể cho phép các dạng linh hoạt. Khi đó cần thêm validation cú pháp theo schema trước khi parse, đồng thời chọn rõ cơ chế dấu decimal/grouping.

Nếu dùng `setParseBigDecimal(true)`, không cast mù kết quả ở mọi đầu vào đặc biệt: một số đầu vào như NaN/Infinity có thể được biểu diễn theo quy tắc riêng của parser và không trả `BigDecimal`. Hãy kiểm tra kiểu kết quả và trạng thái hợp lệ nếu dữ liệu không được đảm bảo hữu hạn.

### 7.2. Format không phải lúc nào có phép nghịch đảo hoàn hảo

Ví dụ `12345.675` format thành `"12,345.68"` sẽ **mất ba phần nghìn** do làm tròn. Parse chuỗi đó trở lại chỉ thu được giá trị đã làm tròn, không thể khôi phục `12345.675`.

Quy tắc:

- **Dùng chuỗi chuẩn để lưu/truyền dữ liệu số chính xác** khi cần round trip, không dùng chuỗi đẹp của giao diện.
- **Dùng formatter cho trình bày**, có thể làm tròn/đệm 0 hoặc chèn dấu nhóm.
- **Lưu thêm đơn vị, scale và chính sách rounding** nếu chúng là phần thiết yếu của hợp đồng nghiệp vụ.

## 8. Bảng chuyển đổi từ primitive/wrapper: widening, narrowing và exact

| Chuyển đổi | Ví dụ | Có bảo toàn mọi giá trị nguồn không? | Ghi chú |
|---|---|---|---|
| `int → long` | `long l = i;` | **Có** | Widening integer |
| `int → double` | `double d = i;` | **Có** | Mọi `int` được biểu diễn chính xác bởi `double` |
| `long → double` | `double d = l;` | **Không** | Số nguyên lớn hơn ngưỡng chính xác `2^53` có thể bị làm tròn |
| `long → int` | `int i = (int) l;` | **Không** | Mất các bit cao nếu không nằm trong miền int |
| `double → int` | `int i = (int) d;` | **Không** | Cắt phần lẻ về 0 và có quy tắc bão hòa/NaN riêng |
| `double → long` | `long l = (long) d;` | **Không** | Có thể cắt/làm mất precision; phải xác thực trước |
| `float → double` | `double d = f;` | **Có đối với giá trị float đã được lưu** | Không biến số thập phân gốc bị làm tròn thành chính xác |
| `double → float` | `float f = (float) d;` | **Không** | Có thể overflow thành vô cực hoặc mất precision |
| `Integer → int` | `int i = wrapper;` | Khi wrapper khác null | Null gây `NullPointerException` |
| `BigInteger → long` | `bi.longValue()` | Không | Có thể mất bit cao |
| `BigDecimal → int` | `bd.intValue()` | Không | Bỏ phần lẻ và có thể mất thông tin do vượt miền |
| `BigDecimal → double` | `bd.doubleValue()` | Không | Có thể làm tròn, underflow/overflow thành 0/Infinity |

Các phép cast và phương thức `intValue()` không có chữ `Exact` là **chuyển đổi cho phép mất thông tin** trong những tình huống nhất định. Không thể chỉ nhìn rằng mã biên dịch thành công để kết luận giá trị được giữ nguyên.

Ví dụ:

~~~java
int truncated = new BigDecimal("12.9").intValue(); // 12

// Có thể khác dữ liệu gốc rất nhiều khi ngoài miền int:
int wrapped = new BigInteger("4294967296").intValue(); // 0 (2^32 bit thấp bằng 0)
~~~

`(int) Double.NaN` cho 0 theo quy tắc narrowing của primitive; `(int) Double.POSITIVE_INFINITY` cho `Integer.MAX_VALUE`. Đây là các quy tắc chuyển đổi ngôn ngữ, **không phải** kiểm tra "giá trị hợp lệ". Nếu NaN/Infinity không được chấp nhận, phải từ chối **trước** khi cast.

## 9. Bảng API chuyển đổi exact

Đây là bảng tham chiếu quan trọng nhất khi quy tắc yêu cầu "không mất giá trị":

| Nguồn → đích | API chính | Thành công khi nào? | Nếu không exact |
|---|---|---|---|
| `long → int` | `Math.toIntExact(long)` | Trong miền signed int | `ArithmeticException` |
| `BigInteger → int` | `bigInteger.intValueExact()` | Trong miền signed int | `ArithmeticException` |
| `BigInteger → long` | `bigInteger.longValueExact()` | Trong miền signed long | `ArithmeticException` |
| `BigInteger → short` | `bigInteger.shortValueExact()` | Trong miền short | `ArithmeticException` |
| `BigInteger → byte` | `bigInteger.byteValueExact()` | Trong miền byte | `ArithmeticException` |
| `BigDecimal → int` | `bd.intValueExact()` | Không có phần lẻ khác 0 và trong miền int | `ArithmeticException` |
| `BigDecimal → long` | `bd.longValueExact()` | Không có phần lẻ khác 0 và trong miền long | `ArithmeticException` |
| `BigDecimal → short/byte` | `bd.shortValueExact()` / `bd.byteValueExact()` | Là số nguyên đúng miền đích | `ArithmeticException` |
| `BigDecimal → BigInteger` | `bd.toBigIntegerExact()` | Giá trị toán học là số nguyên | `ArithmeticException` |
| `BigInteger → BigDecimal` | `new BigDecimal(bi)` | Luôn giữ giá trị nguyên tương ứng | Không mất precision ở bước này |
| `long → BigInteger` | `BigInteger.valueOf(l)` | Toàn bộ miền signed long | Không mất giá trị |
| `long → BigDecimal` | `BigDecimal.valueOf(l)` | Toàn bộ miền signed long | Không mất giá trị |
| Unscaled long + scale → BigDecimal | `BigDecimal.valueOf(unscaled, scale)` | Giá trị chính xác `unscaled × 10^-scale` | Không làm tròn |
| `BigDecimal → BigDecimal scale nhỏ hơn` | `bd.setScale(n, UNNECESSARY)` | Không bỏ chữ số ảnh hưởng giá trị | `ArithmeticException` |

Ví dụ:

~~~java
BigDecimal one = new BigDecimal("12.000");
int exact = one.intValueExact(); // 12 vì phần lẻ đều là 0.

// Hai trường hợp này phải bị từ chối nếu cần chuyển exact:
// new BigDecimal("12.9").intValueExact();        // ArithmeticException
// new BigInteger("2147483648").intValueExact();  // ArithmeticException

long value = 2147483648L;
// Math.toIntExact(value); // ArithmeticException
~~~

**Tính exact đo theo giá trị**, không nhất thiết theo chuỗi. `new BigDecimal("12.000").intValueExact()` cho 12 dù scale ban đầu là 3. Chuyển sang `int` không giữ scale, vì `int` vốn không có scale.

Ví dụ `toBigIntegerExact` với số âm:

~~~java
BigDecimal value = new BigDecimal("-8.000");
BigInteger exact = value.toBigIntegerExact(); // -8

// new BigDecimal("-8.001").toBigIntegerExact();
// ArithmeticException: có phần lẻ khác 0.
~~~

### Có API exact cho BigDecimal → double không?

Không có `BigDecimal.doubleValueExact()` trong API chuẩn. `doubleValue()` có thể mất precision, overflow thành vô cực hoặc underflow. Nếu nghiệp vụ **bắt buộc** biểu diễn chính xác trong `double`, cần quy định phép kiểm tra riêng phù hợp miền và quy tắc ứng dụng; không coi việc cast rồi parse/format trở lại là bằng chứng đủ cho mọi trường hợp.

Ví dụ `BigDecimal("0.1")` là số thập phân chính xác nhưng không thể biểu diễn đúng bằng giá trị nhị phân `double`. Chuyển sang `double` thường chỉ gần đúng.

## 10. Exact arithmetic: phép cộng/nhân không tràn và phép chia

Chuyển đổi exact không thay thế việc kiểm tra **phép tính** exact. Các phép toán primitive signed `int`/`long` có thể overflow và wrap nếu dùng toán tử thường:

~~~java
int max = Integer.MAX_VALUE;
int wrapped = max + 1; // -2147483648

// Math.addExact(max, 1); // ArithmeticException
~~~

Các phương thức toán học kiểm tra overflow gồm:

| Phép | API | Lỗi khi |
|---|---|---|
| Cộng `int/long` | `Math.addExact(a, b)` | Tổng ngoài miền kiểu đích |
| Trừ `int/long` | `Math.subtractExact(a, b)` | Hiệu ngoài miền |
| Nhân `int/long` | `Math.multiplyExact(a, b)` | Tích ngoài miền |
| Tăng/giảm `int/long` | `Math.incrementExact(x)` / `decrementExact(x)` | Vượt giới hạn |
| Đổi dấu `int/long` | `Math.negateExact(x)` | Ví dụ đổi dấu MIN_VALUE |
| Chuyển `long → int` | `Math.toIntExact(x)` | Ngoài miền int |

`BigInteger` thường là giải pháp khi kết quả số nguyên cần vượt giới hạn `long`. Tuy nhiên, số lớn vẫn tiêu thụ bộ nhớ và thời gian; API exact không làm tài nguyên trở nên vô hạn.

### BigDecimal.divide và tính exact

~~~java
BigDecimal exact = BigDecimal.ONE.divide(new BigDecimal("8"));
// 0.125: khai triển thập phân hữu hạn.

// BigDecimal.ONE.divide(new BigDecimal("3"));
// ArithmeticException: biểu diễn thập phân vô hạn nếu không chọn rounding.
~~~

Muốn kết quả xấp xỉ theo quy tắc minh bạch:

~~~java
BigDecimal quotient = BigDecimal.ONE.divide(
        new BigDecimal("3"),
        4,
        RoundingMode.HALF_UP); // 0.3333, scale = 4
~~~

Hoặc dùng precision theo chữ số có nghĩa:

~~~java
BigDecimal q = BigDecimal.ONE.divide(
        new BigDecimal("3"),
        new MathContext(4, RoundingMode.HALF_EVEN)); // 0.3333
~~~

**Khác nhau:** `setScale(4)` / `divide(..., 4, mode)` nói đến **4 chữ số sau dấu thập phân**, còn `MathContext(4)` nói đến **4 chữ số có nghĩa**. Đây là hai kiểu yêu cầu độc lập.

## 11. BigDecimal(String), valueOf(double) và new BigDecimal(double)

Ba cách tạo `BigDecimal` từ ý nghĩa thập phân dễ gây nhầm:

| Cách tạo | Ví dụ | Hành vi |
|---|---|---|
| `new BigDecimal("0.1")` | 0,1 theo hệ thập phân | Lưu chính xác giá trị số thập phân được ghi |
| `BigDecimal.valueOf(0.1)` | Giá trị `double` 0.1 được chuyển qua `Double.toString` | Tạo kết quả biểu diễn ngắn `0.1`, thường phù hợp khi nguồn đã là double |
| `new BigDecimal(0.1)` | Constructor nhận `double` | Biểu diễn **chính xác giá trị double nhị phân đã được lưu**, thường có nhiều chữ số thập phân |

Minh họa:

~~~java
System.out.println(new BigDecimal("0.1"));
// 0.1

System.out.println(BigDecimal.valueOf(0.1));
// 0.1

System.out.println(new BigDecimal(0.1));
// 0.1000000000000000055511151231257827021181583404541015625
~~~

Hai chuỗi `0.1` đầu tiên không chứng minh đầu vào `double 0.1` có thể biểu diễn chính xác 0,1 thập phân. `BigDecimal.valueOf(double)` đi theo chuỗi biểu diễn được `Double.toString` chọn để round-trip giá trị double; `new BigDecimal(double)` phơi bày giá trị nhị phân đã được lưu dưới dạng thập phân chính xác.

Đối với số tiền hoặc giá trị nghiệp vụ thuần thập phân, tốt nhất nhận dữ liệu trực tiếp dưới dạng chuỗi được xác thực hoặc nguồn thập phân có độ chính xác thích hợp rồi tạo `BigDecimal(String)`. Không chuyển `BigDecimal` sang `double` rồi quay lại chỉ để "chuẩn hóa", vì có thể mất precision.

### Giá trị NaN và Infinity

`BigDecimal` không có NaN/Infinity:

~~~java
// BigDecimal.valueOf(Double.NaN); // NumberFormatException
// BigDecimal.valueOf(Double.POSITIVE_INFINITY); // NumberFormatException
~~~

Với nguồn `double` chỉ cho phép số hữu hạn:

~~~java
static BigDecimal requireFiniteDecimal(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("non-finite number");
    }
    return BigDecimal.valueOf(value);
}
~~~

## 12. Chuyển đổi exact không đồng nghĩa làm tròn

Khi một số có phần lẻ, có ít nhất ba chính sách:

1. **Từ chối mất thông tin:** dùng `intValueExact()` / `toBigIntegerExact()`.
2. **Cắt phần lẻ:** dùng `intValue()` / `toBigInteger()` có chủ đích. Đây không phải phép làm tròn gần nhất.
3. **Làm tròn rồi chuyển:** chọn `setScale(0, roundingMode)`, sau đó `intValueExact()` để phát hiện overflow.

~~~java
BigDecimal source = new BigDecimal("12.6");

// 1. Exact: bị từ chối.
// source.intValueExact(); // ArithmeticException

// 2. Truncate về 0:
int truncated = source.intValue(); // 12

// 3. Làm tròn theo chính sách rồi kiểm tra miền int:
int rounded = source.setScale(0, RoundingMode.HALF_UP)
        .intValueExact(); // 13
~~~

Mô hình ở lựa chọn 3 minh bạch: **làm tròn là quyết định nghiệp vụ**, còn `intValueExact` kiểm tra bước chuyển sau rounding còn nằm trong miền int hay không.

Với số âm:

~~~java
BigDecimal minus = new BigDecimal("-12.6");

int towardZero = minus.intValue(); // -12
int floor = minus.setScale(0, RoundingMode.FLOOR)
        .intValueExact(); // -13
~~~

Cắt phần lẻ về 0 khác `FLOOR` khi số âm. Không dùng từ "làm tròn xuống" mơ hồ nếu cần phân biệt hai hành vi.

## 13. Phân biệt precision và scale khi tra cứu

`BigDecimal` biểu diễn giá trị theo:

~~~text
value = unscaledValue × 10^(-scale)
~~~

- `scale()`: độ chia tỷ lệ thập phân, có thể dương, 0 hoặc âm.
- `precision()`: số chữ số của unscaled value, không tính dấu.
- `setScale(n, mode)`: đổi scale về `n`, làm tròn nếu cần.
- `round(new MathContext(p, mode))`: giới hạn tối đa `p` chữ số có nghĩa, không ấn định số chữ số sau dấu chấm.

Ví dụ:

~~~java
BigDecimal amount = new BigDecimal("123.4500");
System.out.println(amount.precision()); // 7
System.out.println(amount.scale());     // 4
~~~

| Yêu cầu | API phù hợp |
|---|---|
| Hiển thị đúng hai chữ số thập phân theo nghiệp vụ | Làm tròn/chuẩn hóa bằng `setScale(2, mode)` trước khi format |
| Giữ ba chữ số có nghĩa | `round(new MathContext(3, mode))` |
| Chỉ bỏ các số 0 cuối không ảnh hưởng giá trị | `stripTrailingZeros()`, lưu ý có thể đổi scale |
| Giảm scale nhưng tuyệt đối không được đổi giá trị | `setScale(newScale, RoundingMode.UNNECESSARY)` |
| Chia lấy đúng hai chữ số sau dấu chấm | `divide(divisor, 2, mode)` |
| Chia theo số chữ số có nghĩa | `divide(divisor, new MathContext(p, mode))` |

`stripTrailingZeros()` giữ nguyên giá trị nhưng có thể làm `equals()` thay đổi do scale khác đi. Ví dụ `new BigDecimal("1000.00").stripTrailingZeros()` có thể trở thành dạng `1E+3`. Đừng áp dụng nếu contract cần giữ đúng scale ban đầu.

### HALF_UP và HALF_EVEN

~~~java
BigDecimal x = new BigDecimal("1.245");

System.out.println(x.setScale(2, RoundingMode.HALF_UP));
// 1.25

System.out.println(x.setScale(2, RoundingMode.HALF_EVEN));
// 1.24
~~~

`HALF_UP` chọn xa 0 khi đúng giữa; `HALF_EVEN` chọn chữ số cuối được giữ là chẵn khi đúng giữa. Chính sách nào đúng phụ thuộc yêu cầu tính toán, không phụ thuộc việc một cách cho chuỗi "đẹp hơn".

## 14. Ví dụ đầy đủ Java 17+: tra cứu parse, format và exact

Tạo tệp `NumericConversionReference.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class NumericConversionReference {
    static void expectedFailure(String name, Runnable operation) {
        try {
            operation.run();
            System.out.println(name + ": unexpectedly succeeded");
        } catch (ArithmeticException | NumberFormatException ex) {
            System.out.println(name + ": " + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws ParseException {
        System.out.println("parse int radix: " +
                Integer.parseInt("7F", 16));
        System.out.println("decode: " + Integer.decode("0x2A"));
        System.out.println("unsigned: " + Integer.toUnsignedString(
                Integer.parseUnsignedInt("4294967295")));
        System.out.println("parse long: " +
                Long.parseLong("9000000000"));
        System.out.println("parse double NaN: " + Double.isNaN(
                Double.parseDouble("NaN")));
        System.out.println("big integer: " +
                new BigInteger("FF", 16));

        BigDecimal decimal = new BigDecimal("123.4500");
        System.out.println("decimal: " + decimal.toPlainString()
                + ", precision=" + decimal.precision()
                + ", scale=" + decimal.scale());

        BigDecimal scientific = new BigDecimal("1E+4");
        System.out.println("strings: " + scientific
                + " | " + scientific.toEngineeringString()
                + " | " + scientific.toPlainString());

        System.out.println("exact int: " +
                new BigDecimal("12.000").intValueExact());
        System.out.println("truncating int: " +
                new BigDecimal("12.9").intValue());

        expectedFailure("fraction", () ->
                new BigDecimal("12.9").intValueExact());
        expectedFailure("out-of-range", () ->
                Math.toIntExact(2147483648L));
        expectedFailure("big-integer range", () ->
                new BigInteger("2147483648").intValueExact());

        System.out.println("HALF_UP: " +
                new BigDecimal("1.245").setScale(2, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN: " +
                new BigDecimal("1.245").setScale(2, RoundingMode.HALF_EVEN));

        DecimalFormat format = (DecimalFormat)
                NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);

        System.out.println("formatted: " +
                format.format(new BigDecimal("12345.675")));

        format.setParseBigDecimal(true);
        BigDecimal parsed = (BigDecimal) format.parse("1,234.50");
        System.out.println("localized parse: " + parsed.toPlainString());

        System.out.println("valueOf double: " +
                BigDecimal.valueOf(0.1));
        System.out.println("binary constructor: " +
                new BigDecimal(0.1));
    }
}
~~~

Chạy:

~~~bash
javac NumericConversionReference.java
java NumericConversionReference
~~~

Stdout:

~~~text
parse int radix: 127
decode: 42
unsigned: 4294967295
parse long: 9000000000
parse double NaN: true
big integer: 255
decimal: 123.4500, precision=7, scale=4
strings: 1E+4 | 10E+3 | 10000
exact int: 12
truncating int: 12
fraction: ArithmeticException
out-of-range: ArithmeticException
big-integer range: ArithmeticException
HALF_UP: 1.25
HALF_EVEN: 1.24
formatted: 12,345.68
localized parse: 1234.50
valueOf double: 0.1
binary constructor: 0.1000000000000000055511151231257827021181583404541015625
~~~

Kết quả được gắn với các quy tắc sau:

- `Integer.parseInt("7F",16)` là 127; `decode("0x2A")` nhận tiền tố hex và trả 42.
- `Integer.parseUnsignedInt("4294967295")` giữ bit pattern; `toUnsignedString` hiển thị lại theo giá trị unsigned.
- `new BigDecimal("123.4500")` giữ precision 7, scale 4.
- `1E+4` ở dạng engineering thành `10E+3`, trong khi plain là `10000`.
- `intValueExact` nhận 12.000 vì không có phần lẻ khác 0, từ chối 12.9 và số ngoài miền.
- `HALF_UP` và `HALF_EVEN` khác nhau ở điểm giữa 1.245.
- `DecimalFormat` dùng `Locale.US` và `HALF_UP` rõ ràng; parse ngược chuỗi đã được định dạng cho giá trị sau làm tròn.
- `new BigDecimal(0.1)` phơi bày giá trị nhị phân của `double`, khác với hai cách tạo chuỗi thập phân ngắn ở đầu.

`expectedFailure` chỉ dùng trong demo để biến ngoại lệ dự kiến thành dòng output kiểm tra được. Trong mã nghiệp vụ, không nên bắt `ArithmeticException`/`NumberFormatException` rồi mặc định tiếp tục không xử lý; hãy trả lỗi phù hợp hoặc xử lý input theo contract.

## 15. Bảng lựa chọn nhanh theo nhu cầu

| Nhu cầu thực tế | Chọn | Tránh |
|---|---|---|
| Đọc số nguyên decimal nhỏ trong miền int | `Integer.parseInt(s, 10)` | `Double.parseDouble` rồi cast |
| Đọc số nguyên vượt long | `new BigInteger(s)` | Parse long rồi cố sửa overflow |
| Đọc số tiền/decimal chính xác | `new BigDecimal(validatedString)` | `new BigDecimal(double)` từ input thập phân |
| Đọc số hex có tiền tố `0x` | `Integer.decode(s)` nếu chính sách octal phù hợp | `parseInt(s,16)` trực tiếp trên `0x...` |
| Đọc số hex không tiền tố | `Integer.parseInt(s,16)` hoặc `new BigInteger(s,16)` | Dùng `decode` rồi bỏ qua cú pháp nguồn |
| Đọc unsigned 32-bit | `Integer.parseUnsignedInt` và xuất `toUnsignedString` | Coi `int` kết quả là số dương signed |
| Chuyển long sang int phải exact | `Math.toIntExact` | `(int) longValue` nếu có thể overflow |
| Chuyển BigDecimal sang int phải exact | `intValueExact()` | `intValue()` hoặc cast qua double |
| Chuyển BigDecimal sang BigInteger phải nguyên vẹn | `toBigIntegerExact()` | `toBigInteger()` nếu không muốn cắt phần lẻ |
| Làm tròn decimal đến 2 vị trí | `setScale(2, mode)` | `intValue()`; `MathContext(2)` nếu ý nghĩa là 2 chữ số lẻ |
| In số không có `E` | `toPlainString()` | Giả định nó bảo toàn scale âm sau parse |
| In số để BigDecimal round trip scale | `toString()` | `toEngineeringString()` làm format lưu trữ bắt buộc |
| Hiển thị dấu nhóm và dấu thập phân | `DecimalFormat`/`NumberFormat` theo `Locale` | Ghép dấu bằng thao tác chuỗi tùy tiện |
| Parse số có dấu nhóm locale | `NumberFormat.parse` + kiểm tra hết chuỗi | `BigDecimal(String)` trực tiếp với `1,234.50` |
| Parse double nhưng chỉ chấp nhận hữu hạn | `Double.parseDouble` rồi `Double.isFinite` | Chỉ dựa vào ngoại lệ parse |
| Parse null/blank | Xác định chính sách missing hoặc invalid trước | Unbox/cast null thành primitive |
| Tính tổng số nguyên có thể overflow | `Math.addExact`/`BigInteger` hoặc kiểu lớn thích hợp | Cộng int rồi mới cast kết quả |
| Xuất JSON trao đổi giữa hệ thống | Số hữu hạn/decimal theo schema, xử lý nonfinite riêng | Đưa `NaN`/`Infinity` trực tiếp như số JSON chuẩn |

## 16. Các bẫy thường gặp

### 16.1. Parse số có vẻ đúng nhưng sai định dạng

~~~java
// "01" với parseInt decimal là 1.
// "01" với Integer.decode vẫn là octal, nhưng giá trị cũng là 1.
// "010" với parseInt decimal là 10.
// "010" với Integer.decode là 8.
~~~

Nếu dữ liệu chứa số có số 0 ở đầu, đừng thay `parseInt` bằng `decode` trừ khi hợp đồng thực sự cho phép octal.

### 16.2. Convert nhanh nhưng làm mất phần lẻ

`BigDecimal.intValue()` không ném lỗi chỉ vì có `.9`. Nếu quy tắc yêu cầu toàn bộ giá trị chính xác, hãy dùng `intValueExact()`. Nếu muốn làm tròn, hãy làm tròn theo quy tắc rồi chuyển exact.

### 16.3. Dùng DecimalFormat để chuẩn hóa giá trị bên trong

Format là công đoạn **trình bày**; có thể làm tròn và phụ thuộc locale. Không nên chuyển số thành chuỗi có dấu nhóm rồi parse lại trong lõi tính toán chỉ để "đẹp hơn". Giữ `BigDecimal` làm kiểu dữ liệu số trong thuật toán, định dạng ở biên xuất dữ liệu.

### 16.4. Nhầm exact của giá trị với exact của biểu diễn

`BigDecimal("1.20").compareTo(BigDecimal("1.2")) == 0`, nhưng `equals` là false vì scale khác nhau. `intValueExact()` chỉ đảm bảo số nguyên không mất giá trị; nó không giữ lại "hai chữ số lẻ" ban đầu. Nếu scale là một phần của hợp đồng, phải lưu/kiểm tra scale riêng.

### 16.5. Không phân biệt lỗi parse với lỗi miền

`Integer.parseInt("abc")` ném `NumberFormatException` do không đúng cú pháp. `Integer.parseInt("2147483648")` cũng ném `NumberFormatException` vì vượt miền int. Nếu API cần báo lỗi riêng, có thể kiểm tra cú pháp, sau đó parse vào `BigInteger` hoặc kiểm tra miền một cách có hệ thống; không suy ra mọi `NumberFormatException` đều là lỗi ký tự.

### 16.6. Chia cho 0 hoặc chia thập phân vô hạn mà quên chính sách

`BigDecimal.ONE.divide(BigDecimal.ZERO)` lỗi; `BigDecimal.ONE.divide(new BigDecimal("3"))` cũng có thể lỗi vì không có khai triển thập phân hữu hạn. Nhưng nguyên nhân khác nhau. Nếu kết quả được phép làm tròn, phải chỉ rõ precision/scale và rounding mode.

### 16.7. Dùng format mặc định cho hợp đồng trao đổi

Locale mặc định hoặc thiết lập phân cách có thể đổi giữa máy. Hãy truyền `Locale` cụ thể khi hiển thị, và dùng định dạng bất biến theo giao thức khi serialize. Không coi dấu nhóm giao diện là một phần bắt buộc của biểu diễn số chuẩn Java.

### 16.8. Parser locale có thể bỏ qua phần dư

Dùng `ParsePosition` và so `getIndex() == input.length()`. Đối với yêu cầu format nghiêm ngặt, kết hợp thêm bước validation, vì parser có thể khoan dung với grouping.

### 16.9. Boxing và null

`Integer.valueOf(s)` trả wrapper, `Integer.parseInt(s)` trả primitive. Một `Integer` có thể null, và gán tự động vào `int` sẽ gây `NullPointerException`. Parse/convert nên xác định rõ nullable input trước khi thực hiện phép số học.

## 17. Bài tập tra cứu và đáp án

**Câu 1.** Parse chuỗi `"7F"` thành 127 theo cơ số 16.

**Đáp án:**

~~~java
int value = Integer.parseInt("7F", 16); // 127
~~~

**Câu 2.** Chuyển `new BigDecimal("20.000")` thành `int` mà từ chối mọi trường hợp mất giá trị.

**Đáp án:**

~~~java
int exact = new BigDecimal("20.000").intValueExact(); // 20
~~~

Scale có thể khác 0 nhưng phần lẻ phải hoàn toàn là 0.

**Câu 3.** Vì sao `new BigDecimal("20.3").intValue()` không phù hợp nếu đầu vào phải là số nguyên chính xác?

**Đáp án:** Vì nó cắt phần lẻ và cho 20. Dùng `intValueExact()` để ném `ArithmeticException`.

**Câu 4.** `Math.toIntExact(2147483648L)` làm gì?

**Đáp án:** Ném `ArithmeticException` vì 2.147.483.648 nằm ngoài miền signed `int`.

**Câu 5.** Cần đọc `"1,234.50"` theo quy tắc `Locale.US`. Dùng `new BigDecimal(String)` trực tiếp được không?

**Đáp án:** Không; chuỗi có grouping. Dùng parser locale như `NumberFormat`/`DecimalFormat` theo `Locale.US`, bật parse BigDecimal khi phù hợp và kiểm tra hết chuỗi theo yêu cầu chặt chẽ.

**Câu 6.** So `new BigDecimal("0.1")` với `new BigDecimal(0.1)`.

**Đáp án:** Cách nhận `String` biểu diễn chính xác 0.1 thập phân. Constructor nhận `double` lưu chính xác giá trị nhị phân double, thường được hiển thị bằng nhiều chữ số thập phân.

**Câu 7.** Muốn in `new BigDecimal("1E+4")` ở dạng thường không `E` và vẫn bảo toàn scale -4 khi parse lại: một chuỗi `toPlainString()` có đủ không?

**Đáp án:** Không; `toPlainString()` cho `"10000"`, parse lại có scale 0. Nếu cần bảo toàn scale, `toString()` là định dạng canonical phù hợp.

**Câu 8.** Sau `Integer.parseUnsignedInt("4294967295")`, vì sao `System.out.println(value)` in -1?

**Đáp án:** Giá trị 32 bit toàn bit 1 được lưu trong kiểu signed `int`. In theo signed cho -1; dùng `Integer.toUnsignedString(value)` để hiển thị số không dấu.

**Câu 9.** Parse `"NaN"` vào double có báo lỗi không? Làm sao từ chối?

**Đáp án:** `Double.parseDouble("NaN")` trả NaN. Kiểm tra `Double.isFinite(value)` và từ chối nếu false.

**Câu 10.** Cần làm tròn `1.245` về hai chữ số thập phân theo HALF_EVEN rồi chuyển exact nếu kết quả là số nguyên; thực hiện mấy bước?

**Đáp án:** Thứ nhất `setScale(2, RoundingMode.HALF_EVEN)` cho `1.24`. Thứ hai `intValueExact()` sẽ ném `ArithmeticException` vì kết quả vẫn có phần lẻ khác 0. Làm tròn đến hai chữ số lẻ **không** khiến một giá trị đương nhiên là số nguyên.

---

**Ghi nhớ:** Hãy chọn API theo câu hỏi: **parse** từ chuỗi với đúng radix/locale, **format** số thành chuỗi theo yêu cầu hiển thị, và **convert exact** khi không được mất giá trị. Nếu cần kiểm soát rounding, thực hiện bằng `BigDecimal` với scale/precision và `RoundingMode` được chỉ định; sau đó dùng API `...Exact()` để phát hiện không thể chuyển sang kiểu đích. Đừng thay lỗi bằng một giá trị đã bị cắt, wrap hoặc định dạng lại mà không có hợp đồng nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-reference-008">Tra cứu JLS và Javadoc các API số</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu JLS và Javadoc các API số trong Java: tìm đúng quy tắc, đúng phiên bản, đúng phương thức

## 1. Vì sao cần đọc tài liệu gốc?

Khi gặp một kết quả khó hiểu như `Integer.MAX_VALUE + 1` trở thành số âm, `-7 / 3` ra `-2`, `2.0` khác `2.00` trong `BigDecimal.equals()`, hoặc `Double.MIN_VALUE` lại **dương**, cách đáng tin nhất là lần theo **tài liệu đặc tả chính thức** thay vì suy đoán hoặc chỉ nhìn kết quả trên một máy.

Java có hai nguồn tài liệu quan trọng nhưng **khác mục đích**:

1. **JLS – Java Language Specification:** định nghĩa ngữ nghĩa của **ngôn ngữ Java**: kiểu primitive, toán tử `+`/`-`/`*`/`/`/`%`, promotion, casting, boxing/unboxing, thứ tự đánh giá, so sánh, literal và quy tắc khi chạy biểu thức.
2. **Javadoc – tài liệu API Java SE:** định nghĩa **lớp, hằng, constructor và phương thức thư viện**: `Math.addExact`, `Integer.parseInt`, `Double.isNaN`, `BigDecimal.divide`, `BigInteger.mod`, `RoundingMode.HALF_EVEN`, v.v.

Một truy vấn thường cần cả hai. Ví dụ, để giải thích vì sao `Integer.MAX_VALUE + 1` không tự ném lỗi, hãy tra **JLS** về toán học số nguyên; để biết thay bằng `Math.addExact` thì ngoại lệ gì được ném, hãy tra **Javadoc của `Math`**.

Bài học này là **hướng dẫn tra cứu và đối chiếu**, không phải bản liệt kê tất cả API số. Sau khi học, bạn có thể tự xác định “vấn đề này thuộc JLS hay Javadoc?”, chọn đúng bản Java, tìm đúng đề mục/phương thức, đọc hợp đồng và tạo chương trình Java 17+ để tái hiện kết quả.

## 2. Bản đồ nguồn tài liệu chính thức

| Cần giải đáp | Ưu tiên nguồn | Ví dụ câu hỏi |
|---|---|---|
| Ngữ nghĩa của toán tử `+`, `/`, `%`, `==` | JLS, thường Chương 15 | Vì sao `-7 % 3` là `-1`? |
| Miền primitive và quy tắc floating-point | JLS Chương 4 | `int` có mấy bit? `double` có signed zero? |
| Chuyển kiểu, boxing, unboxing, numeric promotion | JLS Chương 5 | `int + long` ra kiểu gì? `Integer` null bị unbox khi nào? |
| Kiểu và hành vi của `Math.floorDiv`, `addExact` | Javadoc `java.lang.Math` | Phương thức trả gì, ngoại lệ nào? |
| Hằng `MIN_VALUE`, `MAX_VALUE` và parse | Javadoc `Integer`, `Long`, `Double` | `Double.MIN_VALUE` thực ra là gì? |
| Số thập phân chính xác và so sánh giá trị | Javadoc `java.math.BigDecimal` | `equals` khác `compareTo` ở đâu? |
| Số nguyên tùy độ lớn, chia lấy dư | Javadoc `java.math.BigInteger` | `mod` có chấp nhận modulus âm không? |
| Rounding và precision | Javadoc `RoundingMode`, `MathContext` | `HALF_EVEN` xử lý đúng điểm giữa ra sao? |
| Random cho mô phỏng hay token | Javadoc `Random`, `SecureRandom` | Thuật toán có phù hợp bảo mật không? |
| Bytecode/JVM runtime, biểu diễn ở mức máy ảo | JVM Specification (JVMS), khi thật sự cần | Lệnh bytecode, verifier, execution engine |

**JLS không phải Javadoc**, và Javadoc không phải quy tắc của một toán tử tự thân. `BigDecimal.add` là **method call** được mô tả trong Javadoc; `int + int` là **biểu thức** được quy định bởi JLS. Đừng viện dẫn `BigDecimal` để giải thích mặc định phép cộng primitive hay ngược lại.

### Điểm vào cho Java 17 (phù hợp ví dụ trong bài)

- [JLS Java SE 17 – trang chủ](https://docs.oracle.com/javase/specs/jls/se17/html/index.html).
- [JLS Chương 4 – Types, Values, and Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html).
- [JLS Chương 5 – Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html).
- [JLS Chương 15 – Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html).
- [Java SE 17 API documentation – Module `java.base`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/module-summary.html).
- [Javadoc `java.lang.Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html).
- [Javadoc `java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

Đường dẫn tài liệu **có phiên bản**: `se17` trong JLS và `/javase/17/docs/api/` trong Javadoc. Khi dự án chạy Java 17, hãy bắt đầu với Java 17. Nếu dự án chạy bản khác, chuyển sang **JLS/Javadoc cùng phiên bản**, vì các API mới có thể chưa tồn tại hoặc hành vi/hợp đồng có ghi chú thay đổi.

## 3. Quy trình tra cứu chuẩn: từ biểu thức đến dẫn chứng

Đề xuất quy trình 7 bước có thể áp dụng trong review và debug:

1. **Viết lại câu hỏi thật cụ thể.** Ví dụ: “Ở Java 17, với biến `int a = -7;`, tại sao `a / 3` trả `-2` mà không phải `-3`?”
2. **Phân loại biểu thức.** Toán tử ngôn ngữ hay lời gọi method? Primitive, wrapper hay `BigDecimal`? Có thể có unboxing/promotion không?
3. **Khóa phiên bản JDK.** Lấy `java -version`, `javac -version`; nếu build có `--release 17` hoặc cấu hình toolchain, dùng đúng mức đó để so API.
4. **Mở JLS hoặc Javadoc đúng đề mục.** Với toán tử chia, tới JLS §15.17.2; với `Math.floorDiv`, tìm method trong Javadoc `Math`.
5. **Đọc đầy đủ các điều kiện cạnh và chú thích.** Quan tâm dấu âm, zero, overflow, rounding, loại tham số, `null`, ngoại lệ, và giới hạn của provider/implementation nếu có.
6. **Tạo tái hiện tối thiểu.** Một lớp Java nhỏ, input cố định, in kiểu/giá trị hoặc bắt ngoại lệ có tên; tránh dependency bên ngoài.
7. **Ghi kết luận kèm nguồn và phiên bản.** Trình bày tách bạch: “JLS định nghĩa ...; phương thức API theo Javadoc ...; chạy Java 17 cho kết quả ...”.

Mẫu ghi chú thực tế:

```text
Câu hỏi    : Vì sao -7 / 3 = -2?
Kiểu       : int / int -> int
Nguồn      : JLS SE 17 §15.17.2 (Integer Division Operator /)
Kết luận   : Phép chia nguyên làm tròn thương về 0
Kiểm chứng : javac --release 17, java; in (-7 / 3) được -2
API khác   : Math.floorDiv(-7,3) = -3, tra Javadoc Math.floorDiv
```

Đây là **mẫu ghi chú tra cứu**, không phải lệnh shell. Điểm quan trọng là dẫn **đề mục**, không chỉ đưa link tới một trang chủ quá rộng.

## 4. Đọc JLS số ở đâu?

### 4.1. Chương 4: primitive và miền giá trị

[JLS §4.2 – Primitive Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2) giải thích các kiểu primitive. Những mốc hữu ích:

| Mục JLS 17 | Nội dung nên tra |
|---|---|
| [§4.2.1](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.1) | Kiểu số nguyên và miền `byte`, `short`, `int`, `long`, `char` |
| [§4.2.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.2) | Các phép toán số nguyên, tràn số nguyên |
| [§4.2.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3) | Kiểu và giá trị floating-point `float`, `double` |
| [§4.2.4](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.4) | Các phép toán floating-point và quy tắc số học |

**Ví dụ câu hỏi:** “`Double.MIN_VALUE` có phải số âm bé nhất?” Trước tiên tra Javadoc `Double.MIN_VALUE` để hiểu hằng **cụ thể**; dùng JLS §4.2.3–4.2.4 để hiểu miền floating-point, subnormal, zero có dấu và `Infinity`. Chú ý `Double.MIN_VALUE` là **số dương khác 0 nhỏ nhất** biểu diễn được, không phải số hữu hạn âm nhỏ nhất.

### 4.2. Chương 5: kiểu của biểu thức và chuyển đổi

[JLS Chương 5](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html) đặc biệt hữu ích khi thấy kết quả bất ngờ sau casting hoặc boxing:

| Mục | Câu hỏi điển hình |
|---|---|
| [§5.1.2 Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.2) | `int` chuyển sang `long` có chính xác không? `long` sang `double` có thể mất chữ số không? |
| [§5.1.3 Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.3) | Ép `long` sang `int` bị mất phần nào? |
| [§5.1.7 Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7) | Khi `int` chuyển thành `Integer`, có tạo object mới không? |
| [§5.1.8 Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8) | `Integer n = null; n + 1` sẽ làm gì? |
| [§5.6 Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6) | Khi `int + double`, operands được promote thế nào? |

**Điểm tinh tế:** chuyển sang kiểu rộng hơn không nhất thiết giữ **mọi bit chính xác số học**. `long` → `double` là *widening primitive conversion* nhưng có thể mất chính xác với số nguyên lớn; nếu đang debug một giá trị `long` gần `2^53`, hãy tra JLS §5.1.2 thay vì cho rằng “widening luôn không mất dữ liệu”.

### 4.3. Chương 15: toán tử và ngữ nghĩa biểu thức

Các mục thường dùng:

- [§15.17 – Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17): `*`, `/`, `%`; đặc biệt [§15.17.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.2) cho phép chia và [§15.17.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.3) cho phép dư.
- [§15.18 – Additive Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18): phép cộng/trừ (và `+` nối chuỗi); [§15.18.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18.2) cho cộng/trừ số.
- [§15.21 – Equality Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21): so sánh bằng, gồm numeric equality và reference equality.
- [§15.7 – Evaluation Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7): thứ tự đánh giá các phần của biểu thức.
- [§15.26 – Assignment Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.26): phép gán và compound assignment như `+=`.

Ví dụ dễ nhầm: `byte b = 1; b += 1;` hợp lệ nhưng `b = b + 1;` không biên dịch nếu thiếu ép kiểu, bởi numeric promotion và quy tắc compound assignment không hoàn toàn giống gán đơn. Tra **JLS §5.6** và **§15.26.2** thay vì chỉ đọc Javadoc của `Byte`.

## 5. Đọc Javadoc các API số như thế nào?

Một trang Javadoc điển hình có các phần: **Class/Interface/Enum**, mô tả ở đầu lớp, field/constructor/method summary, rồi **method detail**. Để xác nhận hành vi, đừng chỉ đọc summary một dòng; hãy cuộn tới đúng **overload** và kiểm tra:

- **Signature:** `static`/instance, kiểu tham số, kiểu trả về, overload nhận `int`, `long`, `double`, `BigDecimal` hay `MathContext`.
- **Parameters/Returns:** ý nghĩa `bound`, giá trị âm, giới hạn mở/đóng, thứ tự/scale của kết quả.
- **Throws:** ngoại lệ có thể phát sinh như `ArithmeticException`, `NumberFormatException`, `NullPointerException`.
- **Since:** API xuất hiện từ bản Java nào; rất quan trọng nếu code phải chạy Java 17.
- **API Note / Implementation Requirements / Implementation Note:** đọc khi có, nhưng phân biệt hợp đồng của API với mô tả chi tiết triển khai.
- **See Also:** các API liên quan và lựa chọn thay thế.
- **Mô tả của lớp:** đôi khi quy tắc quan trọng (như `BigDecimal.equals` khác `compareTo`) được nêu ở đầu lớp thay vì lặp lại trong mọi method.

Đừng suy ra hành vi mọi overload chỉ từ tên phương thức. Chẳng hạn `BigDecimal.divide(BigDecimal)`, `divide(BigDecimal, int, RoundingMode)` và `divide(BigDecimal, MathContext)` có **hợp đồng độ chính xác/rounding khác nhau**.

### Bảng điều hướng nhanh

| Lớp / enum | Link Javadoc Java 17 | Nên tra |
|---|---|---|
| `java.lang.Integer` | [Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html) | `MIN_VALUE`, `MAX_VALUE`, `parseInt`, `valueOf`, `compare`, `compareUnsigned` |
| `java.lang.Long` | [Long](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html) | `parseLong`, `valueOf`, `compareUnsigned`, biên 64 bit |
| `java.lang.Double` | [Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html) | `MIN_VALUE`, `MIN_NORMAL`, `isNaN`, `isFinite`, `compare`, `equals` |
| `java.lang.Float` | [Float](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html) | Hằng, parse, so sánh floating-point |
| `java.lang.Math` | [Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html) | `abs`, `floor`, `ceil`, `round`, `floorDiv`, `floorMod`, `addExact`, `nextUp`, `ulp` |
| `java.lang.StrictMath` | [StrictMath](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StrictMath.html) | Hợp đồng độ chính xác/tính tái lập của hàm số học |
| `java.math.BigInteger` | [BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html) | `add`, `divide`, `remainder`, `mod`, `intValueExact` |
| `java.math.BigDecimal` | [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) | `scale`, `precision`, `equals`, `compareTo`, `divide`, `setScale` |
| `java.math.MathContext` | [MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html) | Precision kết quả và rounding mode |
| `java.math.RoundingMode` | [RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html) | `HALF_EVEN`, `HALF_UP`, `CEILING`, `FLOOR`, `UNNECESSARY` |
| `java.util.Random` | [Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html) | Seed, `nextInt(bound)`, mô phỏng ngẫu nhiên |
| `java.security.SecureRandom` | [SecureRandom](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/SecureRandom.html) | Sinh dữ liệu khó đoán cho mục tiêu bảo mật |

**Tìm nhanh:** mở class Javadoc và dùng chức năng tìm kiếm trên trang (`Ctrl+F`) theo `methodName` hoặc `fieldName`, rồi chọn đúng overload trong phần **Method Details**; hoặc dùng mục Search của trang Java SE API. Khi phương thức có nhiều overload, ghi đầy đủ kiểu tham số trong ghi chú (ví dụ `divide(BigDecimal, int, RoundingMode)`).

## 6. Tình huống 1: vì sao chia số nguyên âm khác `Math.floorDiv`?

**Câu hỏi:** `-7 / 3` và `Math.floorDiv(-7, 3)` có giống nhau không?

Tra cứu theo hai bước:

1. **JLS §15.17.2** quy định phép chia nguyên `/` làm tròn thương về **0** (*truncation toward zero*). Bởi vậy `-7 / 3 = -2`.
2. **Javadoc `Math.floorDiv`** định nghĩa chia theo hướng **âm vô cực**; bởi vậy `Math.floorDiv(-7, 3) = -3`.

Phép dư `% ` primitive có dấu gắn với phép chia về 0: `-7 % 3 = -1`. `Math.floorMod(-7,3)` tương ứng với `floorDiv`, cho `2`. Nguồn: JLS §15.17.3 và Javadoc `Math.floorMod`.

**Tệp `NumericJlsDemo.java` – Java 17+:**

```java
public class NumericJlsDemo {
    public static void main(String[] args) {
        System.out.println("intDivision=" + (-7 / 3));
        System.out.println("intRemainder=" + (-7 % 3));
        System.out.println("floorDivision=" + Math.floorDiv(-7, 3));
        System.out.println("floorMod=" + Math.floorMod(-7, 3));

        int max = Integer.MAX_VALUE;
        System.out.println("overflow=" + (max + 1));

        try {
            System.out.println(Math.addExact(max, 1));
        } catch (ArithmeticException ex) {
            System.out.println("addExact=ArithmeticException");
        }

        System.out.println("widenedDivision=" + (7.0 / 2));
        System.out.println("integerDivision=" + (7 / 2));
    }
}
```

Lệnh:

```bash
javac --release 17 NumericJlsDemo.java
java NumericJlsDemo
```

Stdout:

```text
intDivision=-2
intRemainder=-1
floorDivision=-3
floorMod=2
overflow=-2147483648
addExact=ArithmeticException
widenedDivision=3.5
integerDivision=3
```

Ở ví dụ này, `7.0 / 2` thực hiện phép chia floating-point sau numeric promotion, còn `7 / 2` là phép chia nguyên. Biểu thức `max + 1` là cộng `int` và bị overflow, không ném ngoại lệ. `Math.addExact(max,1)` **là phương thức API khác**, có hợp đồng ném `ArithmeticException` khi tràn. Đây là lý do hai nguồn JLS/Javadoc phải được đọc phối hợp.

## 7. Tình huống 2: `BigDecimal.equals` khác `compareTo` ở đâu?

**Câu hỏi:** Vì sao `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` trả `false`, nhưng `compareTo` lại trả `0`?

Đây là **hành vi thư viện**, không phải trực tiếp quy tắc `==` cho primitive. Tra Javadoc lớp `BigDecimal` và hai method:

- `equals(Object)`: yêu cầu giá trị **và biểu diễn/scale** tương ứng cùng nhau.
- `compareTo(BigDecimal)`: so thứ tự theo giá trị số học, có thể trả 0 cho hai số khác scale.
- `scale()`, `precision()`: giải thích chi tiết biểu diễn.
- `setScale(int,RoundingMode)`: điều chỉnh scale, có thể cần rounding.
- `divide`: từng overload đặt chính sách tính chính xác/rounding khác nhau.

**Tệp `NumericBigDecimalDocsDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumericBigDecimalDocsDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("equals=" + a.equals(b));
        System.out.println("compareTo=" + a.compareTo(b));
        System.out.println("scaleA=" + a.scale());
        System.out.println("scaleB=" + b.scale());
        System.out.println("precisionB=" + b.precision());

        BigDecimal half = new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_EVEN);
        System.out.println("halfEven=" + half);

        try {
            System.out.println(BigDecimal.ONE.divide(new BigDecimal("3")));
        } catch (ArithmeticException ex) {
            System.out.println("exactDivision=ArithmeticException");
        }

        BigDecimal approx = BigDecimal.ONE.divide(
                new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        System.out.println("roundedDivision=" + approx);
    }
}
```

```bash
javac --release 17 NumericBigDecimalDocsDemo.java
java NumericBigDecimalDocsDemo
```

```text
equals=false
compareTo=0
scaleA=1
scaleB=2
precisionB=3
halfEven=2.34
exactDivision=ArithmeticException
roundedDivision=0.33
```

**Cách đọc kỹ:** `BigDecimal.ONE.divide(new BigDecimal("3"))` không thể tạo một thương thập phân hữu hạn chính xác, nên ném `ArithmeticException`; overload với scale `2` và `HALF_UP` trả `0.33`. Nếu nhầm các overload, bạn có thể áp dụng sai cách làm tròn hoặc kết luận sai rằng `BigDecimal.divide` “lúc nào cũng lỗi khi chia 3”.

Một ngộ nhận phổ biến khác: `new BigDecimal(0.1)` và `new BigDecimal("0.1")` **không đồng nhất** về giá trị thập phân, do tham số `double` đã mang sai số biểu diễn nhị phân. Điều này được giải thích trong mô tả constructor của Javadoc `BigDecimal`; hãy đọc **constructor details**, không chỉ xem tên lớp.

## 8. Tình huống 3: widening không luôn có nghĩa giữ toàn bộ chính xác

**Câu hỏi:** `long` có thể chuyển thành `double` mà không mất chữ số không?

Tra **JLS §5.1.2**: `long` → `double` là một phép widening primitive conversion được phép, nhưng **có thể mất precision** vì `double` có số bit hữu hiệu hữu hạn.

Một ví dụ được chọn sát ngưỡng `2^53`:

**Tệp `NumericConversionDocsDemo.java`:**

```java
public class NumericConversionDocsDemo {
    public static void main(String[] args) {
        long original = 9_007_199_254_740_993L;
        double converted = original;

        System.out.println("original=" + original);
        System.out.println("convertedBack=" + (long) converted);
        System.out.println("sameAfterRoundTrip="
                + (original == (long) converted));

        Integer maybe = null;
        try {
            System.out.println(maybe + 1);
        } catch (NullPointerException ex) {
            System.out.println("unboxing=NullPointerException");
        }
    }
}
```

```bash
javac --release 17 NumericConversionDocsDemo.java
java NumericConversionDocsDemo
```

```text
original=9007199254740993
convertedBack=9007199254740992
sameAfterRoundTrip=false
unboxing=NullPointerException
```

Tra **JLS §5.1.7–§5.1.8** để hiểu boxing/unboxing. `maybe + 1` đòi unbox `Integer` thành `int`; vì `maybe == null` nên ném `NullPointerException`. Javadoc `Integer` giải thích kiểu wrapper và phương thức; quy tắc chuyển đổi trong **biểu thức `+`** thuộc JLS.

Ví dụ cũng nhắc rằng một phép chuyển đổi hợp lệ theo hệ thống kiểu có thể **không đáp ứng yêu cầu dữ liệu** nếu nghiệp vụ đòi giữ chính xác mọi chữ số.

## 9. Tình huống 4: `Double.MIN_VALUE`, `NaN` và signed zero

**Câu hỏi:** Vì sao `Double.MIN_VALUE` dương và `Double.NaN == Double.NaN` false?

Tra kết hợp:

1. Javadoc `Double.MIN_VALUE` định nghĩa nó là **giá trị `double` dương khác 0 nhỏ nhất**; `Double.MIN_NORMAL` là giá trị normal dương nhỏ nhất.
2. JLS §4.2.3–§4.2.4 mô tả các giá trị `float`/`double`, gồm signed zero, infinities và NaN.
3. JLS §15.21.1 quy định numeric equality; so sánh `NaN == NaN` là false, còn `0.0 == -0.0` là true.
4. Javadoc `Double.equals` và `Double.compare` mô tả quy tắc riêng khi **hai giá trị là wrapper**: NaN và signed zero có xử lý khác với primitive `==`.

**Tệp `NumericFloatingDocsDemo.java`:**

```java
public class NumericFloatingDocsDemo {
    public static void main(String[] args) {
        System.out.println("minPositive=" + Double.MIN_VALUE);
        System.out.println("nextUpZero=" + Math.nextUp(0.0));
        System.out.println("nextUpOne=" + Math.nextUp(1.0));
        System.out.println("nanPrimitive="
                + (Double.NaN == Double.NaN));
        System.out.println("nanWrapper="
                + Double.valueOf(Double.NaN)
                        .equals(Double.valueOf(Double.NaN)));
        System.out.println("zeroPrimitive=" + (0.0 == -0.0));
        System.out.println("zeroWrapper="
                + Double.valueOf(0.0)
                        .equals(Double.valueOf(-0.0)));
    }
}
```

```bash
javac --release 17 NumericFloatingDocsDemo.java
java NumericFloatingDocsDemo
```

```text
minPositive=4.9E-324
nextUpZero=4.9E-324
nextUpOne=1.0000000000000002
nanPrimitive=false
nanWrapper=true
zeroPrimitive=true
zeroWrapper=false
```

Nếu cần kiểm tra một giá trị có phải NaN hoặc hữu hạn, hãy đọc hợp đồng `Double.isNaN` và `Double.isFinite`. Không dùng `x == Double.NaN` để phát hiện NaN. Khi cần xét “giá trị kề theo biểu diễn”, `Math.nextUp`, `nextDown`, `ulp` được định nghĩa trong Javadoc `Math`, không phải tự lấy `x + 0.0000001` cho mọi độ lớn.

## 10. Chọn đúng phiên bản và tránh thông tin “đúng nhưng không dùng được”

Một bài đăng có thể giới thiệu phương thức hợp lệ trong Java 21 nhưng dự án đang dùng Java 17. Khi ấy mã có thể không biên dịch, dù ý tưởng đúng.

**Quy trình phiên bản:**

- Kiểm tra runtime: `java -version`.
- Kiểm tra compiler: `javac -version`.
- Kiểm tra cấu hình project (Maven `maven.compiler.release`, Gradle toolchain hoặc `--release`) khi có.
- Tra Javadoc đúng **Java SE release** theo target, không mặc định trang “latest”.
- Xem tag **Since** ở class và method; phương thức được thêm vào bản mới có thể không tồn tại trong bản cũ.
- Dùng `javac --release 17 Demo.java` để kiểm tra **ngôn ngữ và public API** theo mục tiêu Java 17 khi biên dịch bằng JDK hỗ trợ cờ này. Sau đó vẫn phải chạy thử trên runtime mục tiêu.

Ví dụ, **không tự mặc định** một tên phương thức đọc được từ Javadoc Java 21 cũng có trong Java 17. Nếu Java 17 chưa có API tương ứng, cần phương án tương thích Java 17 hoặc yêu cầu nâng bản Java có căn cứ.

### Javadoc khác với implementation detail

Javadoc của một phương thức có thể nêu “returns...” và “throws...”: đây là phần hợp đồng API. Một câu “implementation may use ...” hoặc một thuộc tính dựa trên provider/hệ điều hành có thể chỉ là chi tiết triển khai. Cần giữ ranh giới:

- **Contract:** client được phép dựa vào.
- **Implementation note:** thông tin hữu ích để tối ưu/chẩn đoán, không nhất thiết bảo đảm mọi nền tảng.
- **JLS:** quy tắc ngôn ngữ được tiêu chuẩn hóa cho các compiler/Java implementation phù hợp.

Ví dụ `Math` và `StrictMath` có các mô tả về độ chính xác và khả năng tái lập của hàm toán học. Đọc lời mở đầu của class và từng method trước khi tuyên bố “`Math.sin` luôn trả bit-pattern giống nhau trên mọi platform”.

## 11. Mẹo tìm kiếm và trích dẫn nguồn

Khi một vấn đề chưa rõ nằm ở đâu, nên tìm các từ khóa tiếng Anh đúng thuật ngữ tài liệu:

| Vấn đề | Từ khóa để tìm | Trang ưu tiên |
|---|---|---|
| Overflow `int` | `integer overflow JLS 4.2.2` | JLS §4.2.2 hoặc §15.18.2 |
| Chia số âm | `integer division rounds toward zero JLS 15.17.2` | JLS §15.17.2 |
| Ép kiểu số | `widening narrowing primitive conversion JLS 5.1` | JLS §5.1.2/§5.1.3 |
| Boxing cache / unboxing null | `JLS 5.1.7 5.1.8` | JLS §5.1.7–§5.1.8 |
| Wrapper `==` | `reference equality JLS 15.21` | JLS §15.21 |
| `Math.abs` của MIN_VALUE | `Math abs Integer.MIN_VALUE Java 17` | Javadoc `Math.abs(int)` |
| `BigDecimal.equals` | `BigDecimal equals compareTo scale Javadoc` | Javadoc `BigDecimal` |
| `BigDecimal.divide` | `BigDecimal divide ArithmeticException non terminating` | Javadoc đúng overload `divide` |
| Chính sách làm tròn | `RoundingMode HALF_EVEN table` | Javadoc `RoundingMode` |
| `Double.MIN_VALUE` | `Double MIN_VALUE MIN_NORMAL Java 17` | Javadoc `Double` |

Nếu dùng công cụ tìm kiếm ngoài tài liệu, giới hạn kết quả vào miền chính thức như `docs.oracle.com`, rồi đối chiếu phiên bản. Trang thảo luận, blog và diễn đàn hữu ích để có ví dụ, nhưng đối với **hợp đồng ngôn ngữ/API**, nên kết luận dựa trên JLS/Javadoc gốc. Với một vấn đề tương tác của nhiều API, ghi đủ hai hoặc nhiều nguồn thay vì ép tất cả vào một link.

**Dẫn chứng tốt:** “Java SE 17 JLS §15.17.2 (integer division) quy định chia nguyên theo hướng 0; Javadoc `Math.floorDiv(int,int)` định nghĩa floor division.”  
**Dẫn chứng chưa đủ:** “Đọc trên mạng thấy Java chia âm hơi lạ.”

## 12. Những lỗi thường gặp khi tra cứu

1. **Tra nhầm Javadoc cho toán tử:** `int + int`, `/`, `%`, `==` thuộc JLS; class `Integer` không trực tiếp thay thế ngữ nghĩa mọi toán tử.
2. **Tra JLS để hỏi overload thư viện:** `BigDecimal.divide` và `Math.floorDiv` có hợp đồng method được mô tả trong Javadoc; JLS chỉ định nghĩa lời gọi method và các quy tắc ngôn ngữ liên quan.
3. **Bỏ qua kiểu toán hạng:** `7 / 2` và `7.0 / 2` khác vì numeric promotion; wrapper còn có unboxing.
4. **Bỏ qua dấu âm/zero:** `-7 / 3`, `-7 % 3`, `Math.floorDiv`, `Double.NaN`, `+0.0`/`-0.0` thường là test phân biệt đúng sai.
5. **Chỉ xem method summary:** bỏ lỡ overload, `Throws`, `Since` hoặc câu cảnh báo trong method detail.
6. **Nhầm `MIN_VALUE`:** `Integer.MIN_VALUE` là biên âm, `Double.MIN_VALUE` là số **dương** khác 0 nhỏ nhất.
7. **Dùng Javadoc phiên bản mới hơn:** mã không thể biên dịch trên target cũ dù trang “latest” mô tả API hợp lệ.
8. **Suy luận từ một lần chạy:** một kết quả có thể phụ thuộc implementation/provider; chạy chương trình chỉ là kiểm chứng, không thay thế mô tả đặc tả.
9. **Đọc chữ “exact” mà bỏ kiểu đích:** `Math.addExact`, `BigDecimal.divide`, `BigInteger.intValueExact` đều có ý nghĩa chính xác và ngoại lệ tùy method; đọc đúng signature.
10. **Bỏ qua precision/scale:** cùng giá trị toán học có thể khác `BigDecimal.equals`; nhìn `toString` mà không đọc `scale()` gây hiểu nhầm.
11. **Quên rằng JLS/Javadoc là tài liệu tiếng Anh chuẩn:** khi dịch các từ `truncation`, `floor`, `ceiling`, `round toward zero`, `narrowing`, giữ kèm thuật ngữ gốc để giảm sai lệch.
12. **Coi FAQ/preview/ảnh chụp tài liệu là nguồn cuối cùng:** ưu tiên trang đặc tả có đường dẫn phiên bản và section/method chính xác.

## 13. Bài tập tra cứu có đáp án

1. **Câu hỏi:** `-7 / 3` có phải floor division? Tra ở đâu?  
   **Đáp án:** Không, `/` primitive `int` lấy thương hướng 0, cho `-2`; xem **JLS §15.17.2**. Muốn floor division `-3`, xem method `Math.floorDiv` trong **Javadoc `Math`**.

2. **Câu hỏi:** `Math.addExact(Integer.MAX_VALUE,1)` có khác `Integer.MAX_VALUE+1` không?  
   **Đáp án:** Có. Toán tử primitive overflow theo JLS; `Math.addExact` ném `ArithmeticException` theo Javadoc method. Kết quả `+` là `Integer.MIN_VALUE`.

3. **Câu hỏi:** `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false` do JLS hay Javadoc?  
   **Đáp án:** Chủ yếu là hợp đồng `BigDecimal.equals` trong **Javadoc `BigDecimal`**; so sánh giá trị số học bằng `compareTo() == 0`.

4. **Câu hỏi:** Vì sao `Double.MIN_VALUE > 0`?  
   **Đáp án:** Hằng `MIN_VALUE` được **Javadoc `Double`** định nghĩa là số dương khác 0 nhỏ nhất. JLS §4.2.3 mô tả mô hình giá trị `double`.

5. **Câu hỏi:** Nếu `Integer x = null;` thì `x + 1` làm gì?  
   **Đáp án:** Ném `NullPointerException` khi unboxing, tra **JLS §5.1.8** và §5.6. `x == null` chỉ là reference equality nên không unbox.

6. **Câu hỏi:** `Math.nextUp(1.0)` tìm ở đâu?  
   **Đáp án:** Method detail của **Javadoc `Math`**. Nó trả số `double` biểu diễn được kề trên 1.0, được in là `1.0000000000000002`.

7. **Câu hỏi:** `BigDecimal.ONE.divide(new BigDecimal("3"))` có làm tròn tự động không?  
   **Đáp án:** Overload chia chính xác sẽ ném `ArithmeticException` khi thương thập phân không kết thúc; xem **Javadoc `BigDecimal.divide(BigDecimal)`**. Chọn overload với scale và rounding mode khi nghiệp vụ cho phép làm tròn.

8. **Câu hỏi:** Vì sao cùng lời gọi method trên JDK mới biên dịch nhưng Java 17 không?  
   **Đáp án:** Kiểm tra phần **Since** và phiên bản Javadoc, dùng `javac --release 17` để đối chiếu API công khai theo target; có thể method chưa được hỗ trợ trên Java 17.

9. **Câu hỏi:** Nếu muốn tìm quy tắc `byte b = 1; b += 1;` khác `b = b + 1;`, đọc đâu?  
   **Đáp án:** **JLS §5.6** về numeric promotion và **§15.26.2** về compound assignment. Không phải chỉ Javadoc `Byte`.

10. **Câu hỏi:** Khi viết một báo cáo lỗi số học, cần dẫn chứng thế nào?  
    **Đáp án:** Nêu phiên bản Java, kiểu toán hạng, biểu thức cụ thể, method overload (nếu có), expected/actual, đoạn mã tái hiện, và link JLS section hoặc Javadoc method phù hợp.

## 14. Checklist tra cứu nhanh trước khi kết luận

- [ ] Ghi đúng bản Java target và lệnh biên dịch/run.
- [ ] Xác định đây là **toán tử ngôn ngữ**, **chuyển kiểu**, hay **method thư viện**.
- [ ] Tra JLS §4, §5, §15 nếu liên quan primitive, toán tử, promotion, casting, boxing/unboxing.
- [ ] Tra **đúng lớp, đúng overload, đúng Java release** trong Javadoc nếu liên quan API.
- [ ] Đọc `Returns`, `Throws`, `Since`, cảnh báo trong class/method detail.
- [ ] Kiểm tra biên: MIN/MAX, số âm, số 0, NaN/Infinity, scale, rounding.
- [ ] Viết chương trình nhỏ có đầu ra xác định và đối chiếu với đặc tả.
- [ ] Phân biệt điều **được bảo đảm** với chi tiết triển khai/provider.
- [ ] Lưu link tới section hoặc method cụ thể trong ghi chú kiến thức, test hoặc review.

## 15. Tổng kết

Đối với Java Core – numbers, hãy nhớ một nguyên tắc: **JLS giải thích Java thực hiện biểu thức như thế nào; Javadoc giải thích thư viện số công khai cam kết điều gì.** Khi cần kiểm chứng một hành vi:

1. Xác định **kiểu và phiên bản Java**.
2. Tìm **đúng section JLS** nếu là toán tử/chuyển đổi, hoặc **đúng method Javadoc** nếu là API.
3. Đọc **các trường hợp biên, ngoại lệ, rounding và overload**.
4. Dùng ví dụ Java 17+ tái hiện, nhưng kết luận theo **hợp đồng tài liệu**.
5. Ghi nguồn chính thức cùng section/method để người khác kiểm chứng lại.

Nguồn gốc: [JLS SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html), [Java SE 17 Javadoc – `java.base`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/module-summary.html), [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html), [Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).


</details>

- [Quay lại đầu trang](#back-to-top)
