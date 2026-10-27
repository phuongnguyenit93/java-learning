<a id="back-to-top"></a>

# Biểu diễn dấu phẩy động

## Menu
- [float theo binary32 và double theo binary64](#floating-point-representation-001)
- [Bit dấu, phần mũ và phần trị](#floating-point-representation-002)
- [Precision nhị phân và số chữ số thập phân có thể tin cậy](#floating-point-representation-003)
- [Vì sao 0.1 thường không được biểu diễn chính xác](#floating-point-representation-004)
- [Phạm vi số nguyên được float hoặc double biểu diễn liên tiếp chính xác](#floating-point-representation-005)
- [Normal, subnormal và gradual underflow](#floating-point-representation-006)
- [MIN_VALUE, MIN_NORMAL và MAX_VALUE](#floating-point-representation-007)
- [Số 0 dương, số 0 âm, vô cực và NaN](#floating-point-representation-008)
- [ULP và khoảng cách giữa hai giá trị kề nhau](#floating-point-representation-009)
- [floatToIntBits, doubleToLongBits và các biến thể raw](#floating-point-representation-010)
- [Chuyển mẫu bit trở lại giá trị float hoặc double](#floating-point-representation-011)

## <a id="floating-point-representation-001">float theo binary32 và double theo binary64</a>

<details>
<summary>Xem chi tiết</summary>

# `float` theo binary32 và `double` theo binary64 trong Java

`float` và `double` đều là kiểu nguyên thủy để làm việc với số dấu phẩy động nhị phân trong Java. `float` dùng **IEEE 754 binary32 (32 bit)**, còn `double` dùng **IEEE 754 binary64 (64 bit)**. Điểm khác biệt cần nắm đầu tiên là **độ phân giải và miền độ lớn có thể biểu diễn**, chứ không phải một số lượng cố định chữ số sau dấu chấm thập phân.

Bài này tập trung vào **so sánh hai định dạng và ra quyết định chọn kiểu**. Các bài tiếp theo giải mã bit, phân tích precision, underflow, giá trị đặc biệt và API chuyển bit ở chiều sâu riêng.

## 1. Bảng so sánh binary32 và binary64

| Thuộc tính | `float` (binary32) | `double` (binary64) |
| --- | --- | --- |
| Tổng độ rộng | 32 bit | 64 bit |
| Bit dấu | 1 | 1 |
| Trường exponent lưu | 8 bit | 11 bit |
| Trường fraction lưu | 23 bit | 52 bit |
| Significand của số normal, kể cả bit 1 ẩn | 24 bit | 53 bit |
| Exponent bias | 127 | 1023 |
| Exponent thật ở miền normal | −126 đến +127 | −1022 đến +1023 |
| Chữ số thập phân có nghĩa thường dùng làm ước lượng | Khoảng 6–7 | Khoảng 15–16 |
| Số normal dương nhỏ nhất | `Float.MIN_NORMAL = 2^-126` | `Double.MIN_NORMAL = 2^-1022` |
| Số dương khác zero nhỏ nhất | `Float.MIN_VALUE = 2^-149` | `Double.MIN_VALUE = 2^-1074` |
| Số hữu hạn dương lớn nhất | `Float.MAX_VALUE ≈ 3.4 × 10^38` | `Double.MAX_VALUE ≈ 1.8 × 10^308` |

Trong số **normal**, một bit 1 dẫn đầu phần trị được ngầm hiểu. Vì thế trường fraction 23/52 bit đem lại **24/53 bit precision** tương ứng. Với số **subnormal** rất gần zero, bit dẫn này không có; độ chính xác tương đối giảm dần. `MIN_VALUE` của hai wrapper dấu phẩy động là **số dương khác 0 nhỏ nhất**, không phải số âm hữu hạn nhỏ nhất; số âm hữu hạn có trị tuyệt đối lớn nhất là `-MAX_VALUE`.

### Miền lớn không đồng nghĩa chính xác từng đơn vị

Phần **exponent** cho phép biến thiên rộng về độ lớn. Phần **significand** giới hạn chi tiết giữa các số ở cùng độ lớn. Chẳng hạn binary32 có thể chứa số hữu hạn khoảng `10^38`, nhưng ở quanh `2^24` đã không còn phân biệt được tất cả số nguyên kề nhau. Tương tự, binary64 không phân biệt liên tiếp mọi số nguyên sau `2^53`.

Nhờ nhiều bit exponent và fraction hơn, binary64 biểu diễn được các số có độ lớn rộng hơn và thường gần giá trị toán học hơn binary32. Tuy nhiên, không kiểu nào có thể lưu chính xác **mọi phân số thập phân**.

## 2. Quan sát cùng giá trị đầu vào ở hai định dạng

Trong Java, literal `0.1` mặc định là `double`; hậu tố `f` tạo literal `float`:

```java
float lowPrecision = 0.1f;
double highPrecision = 0.1;
double widened = lowPrecision;

System.out.println(lowPrecision);            // 0.1
System.out.println(highPrecision);           // 0.1
System.out.println(widened);                 // 0.10000000149011612
System.out.println(Float.toHexString(lowPrecision));
// 0x1.99999ap-4
System.out.println(Double.toHexString(highPrecision));
// 0x1.999999999999ap-4
```

`1/10` có khai triển nhị phân vô hạn, nên mỗi định dạng lưu số gần nhất theo độ chính xác của mình. Hai lời gọi `println` đầu cùng in `0.1` vì Java chọn chuỗi có thể đọc ngược về **giá trị đã lưu ở từng kiểu**. Khi `float` được mở rộng thành `double`, Java giữ đúng **giá trị binary32 đã làm tròn**; phép widening không tái tạo phân số 1/10 ban đầu.

Muốn xem mã hóa thay vì chuỗi số thập phân, dùng bit API:

```java
System.out.printf("0x%08x%n", Float.floatToRawIntBits(0.1f));
// 0x3dcccccd
System.out.printf("0x%016x%n", Double.doubleToRawLongBits(0.1));
// 0x3fb999999999999a
```

Đây là **hai mẫu bit IEEE 754**, không phải hai số nguyên được tạo bằng cast số học. Bài về sign/exponent/fraction sẽ chỉ cách giải mã từng trường; hai bài cuối chương sẽ phân biệt `raw`/canonical NaN và cách dựng lại số từ bit pattern.

## 3. Chọn định dạng theo yêu cầu dữ liệu

| Tình huống | Kiểu/cách biểu diễn thường phù hợp | Điều kiện quyết định |
| --- | --- | --- |
| Tính toán khoa học hoặc nghiệp vụ chấp nhận xấp xỉ nhị phân | `double` | Cần precision rộng hơn và đủ miền; vẫn đánh giá sai số số học |
| Buffer ảnh, dữ liệu đồ họa hoặc cảm biến bắt buộc 32 bit | `float` | Hợp đồng lưu/truyền binary32 và sai số ở các miền giá trị thực tế đều đạt |
| Mảng primitive rất lớn, nhạy với bộ nhớ và băng thông | `float[]` nếu đủ precision | Mỗi phần tử primitive 4 byte thay vì 8 byte; xét cả overhead cấu trúc |
| Số đếm, ID, offset cần chính xác từng đơn vị | `int`, `long` hoặc `BigInteger` | Không để làm tròn chấm động gộp hai giá trị nguyên khác nhau |
| Tiền, thuế, thập phân theo số chữ số cố định | `BigDecimal` từ chuỗi hoặc số nguyên đơn vị nhỏ nhất | Xác định scale, rounding và miền số chính xác ngay từ đầu |
| Giao thức nhị phân phải giữ mọi bit, kể cả payload NaN | `int`, `long`, `byte[]` ở lớp truyền tải | Chốt độ rộng, byte order và yêu cầu bit-exact trước khi tính toán |

**`double` là lựa chọn mặc định hợp lý cho nhiều phép tính số thực Java**, nhưng không tự động là lựa chọn tốt nhất khi API ngoài bắt buộc binary32 hay khi biểu diễn số thập phân phải chính xác. Chọn `float` vì dung lượng chỉ hợp lý sau khi kiểm tra sai số ở **giá trị lớn, nhỏ, gần 0 và sau nhiều bước tính**, không phải vì hai biến đều hiển thị giống nhau.

### Kiểm tra giới hạn khi đổi kiểu

Chuyển `double` sang `float` là narrowing; một giá trị còn hữu hạn ở binary64 có thể thành Infinity ở binary32:

```java
double large = 1e40;
float narrowed = (float) large;
System.out.println(narrowed);            // Infinity
System.out.println(Float.isFinite(narrowed)); // false
```

Tương tự, số rất nhỏ có thể biến thành `float` subnormal hoặc zero. Ngược lại, chuyển `float` sang `double` giữ đúng giá trị số đã lưu trong `float`, nhưng **không** khôi phục thông tin bị mất trong chuyển đổi trước đó.

Khi dữ liệu phải hữu hạn, hãy kiểm tra `Float.isFinite`/`Double.isFinite` trên **kết quả sau chuyển kiểu** và ràng buộc miền theo nghiệp vụ. Các toán tử chấm động có thể tạo NaN hoặc Infinity mà không ném `ArithmeticException`, khác phép chia số nguyên cho zero.

## 4. Literal, biểu thức và điểm làm tròn

```java
double d = 1.25;  // literal mặc định double
float f = 1.25f;  // hậu tố f yêu cầu float
float explicitlyNarrowed = (float) d;
// float invalid = d;  // không tự chuyển double sang float
```

`float` và `double` có cùng nhóm phép toán nhưng **mỗi phép toán có thể làm tròn** theo độ chính xác của kiểu thực hiện. Nếu biểu thức trộn `float` với `double`, quy tắc promotion có thể đưa toán hạng `float` sang `double` trước phép toán, nhưng vẫn mang giá trị `float` đã được làm tròn. Trong Java 17+, ngữ nghĩa floating-point luôn strict theo JEP 306; điều này không biến tính toán thành số học thực tuyệt đối chính xác.

Một ví dụ chọn sai kiểu lưu số nguyên:

```java
int original = 16_777_217; // 2^24 + 1
float rounded = original;
System.out.println((int) rounded); // 16777216
System.out.println(Math.ulp(16_777_216f)); // 2.0
```

Do phần trị binary32 có 24 bit chính xác, số nguyên này bị làm tròn. `double` có ngưỡng tương tự tại `2^53`, không có nghĩa mọi số nguyên lớn hơn ngưỡng đều không biểu diễn được. Bài về **phạm vi số nguyên liên tiếp** sẽ phân tích đầu mút và những bẫy khi so sánh `long` với `double`.

### Một ngưỡng sai số không dùng chung cho mọi số

`Math.ulp(1f)` khoảng `1.19E-7`, còn `Math.ulp(1d)` khoảng `2.22E-16`. Ở số lớn hơn, bước biểu diễn thường lớn hơn. Ngưỡng so sánh gần bằng phải xuất phát từ dữ liệu và bài toán (có thể kết hợp sai số tuyệt đối/tương đối), đồng thời xử lý NaN, Infinity và signed zero. ULP mô tả lưới biểu diễn; **không phải** một bảo đảm sai số của mọi chuỗi phép toán.

## 5. Một chương trình so sánh có thể tự kiểm chứng

Lưu dưới tên `FloatingFormatsComparisonDemo.java`, chạy bằng Java 17+:

```java
public class FloatingFormatsComparisonDemo {
    public static void main(String[] args) {
        float f = 0.1f;
        double d = 0.1;

        System.out.println("float bits="
                + Integer.toHexString(Float.floatToRawIntBits(f)));
        System.out.println("double bits="
                + Long.toHexString(Double.doubleToRawLongBits(d)));
        System.out.println("float widened=" + (double) f);
        System.out.println("float precision bits=" + 24);
        System.out.println("double precision bits=" + 53);
        System.out.println("float ulp at one=" + Math.ulp(1.0f));
        System.out.println("double ulp at one=" + Math.ulp(1.0d));
        System.out.println("integer lost=" + (int) (float) 16_777_217);
        System.out.println("narrowed large=" + (float) 1e40);
        System.out.println("narrowed tiny=" + (float) 1e-50);
    }
}
```

```bash
javac --release 17 FloatingFormatsComparisonDemo.java
java FloatingFormatsComparisonDemo
```

Output (Java 17+):

```text
float bits=3dcccccd
double bits=3fb999999999999a
float widened=0.10000000149011612
float precision bits=24
double precision bits=53
float ulp at one=1.1920929E-7
double ulp at one=2.220446049250313E-16
integer lost=16777216
narrowed large=Infinity
narrowed tiny=0.0
```

Bốn phép thử dùng giá trị thập phân, bit pattern, ULP và chuyển kiểu để trả lời bốn câu hỏi khác nhau: đang lưu **mã nào**, khoảng lưới dày tới đâu, phép thu hẹp mất gì và giá trị còn hữu hạn hay không. Không suy tất cả từ một lần `println`.

## 6. Câu hỏi tự kiểm tra

1. Vì sao `float` có 23 bit fraction lưu trữ nhưng 24 bit precision cho số normal? Sự khác biệt đó ảnh hưởng lựa chọn kiểu ra sao?
2. Tại sao `float f = 0.1f; double d = f;` không tương đương với `double d = 0.1;`?
3. Khi nào `float` là yêu cầu hợp đồng dữ liệu, và khi nào `double` cải thiện chất lượng phép tính?
4. Nếu cảm biến truyền `float` nhưng phép tính cần `double`, thông tin nào còn nguyên khi widening và thông tin nào không thể phục hồi?
5. Nếu một `double` phải dùng để lưu tiền chính xác theo cent, vấn đề là miền số, precision hay cơ số biểu diễn?
6. Làm sao xác nhận một bước narrowing từ `double` sang `float` vẫn thỏa yêu cầu về hữu hạn và sai số?

## 7. Liên hệ tới các bài chuyên sâu

- **Bit dấu, phần mũ và phần trị**: giải mã cụ thể cách 1/8/23 và 1/11/52 bit tạo giá trị.
- **Precision, 0.1, khoảng nguyên chính xác**: làm rõ sai số về độ phân giải, thập phân và chuyển số nguyên.
- **Normal/subnormal, ba hằng giới hạn, giá trị đặc biệt và ULP**: khảo sát các tình huống sát zero, tràn và khoảng cách giữa các số.
- **API đọc bit và dựng lại giá trị**: phục vụ debug, kiểm tra bit và trao đổi dữ liệu nhị phân đúng giao thức.

**Ghi nhớ:** sự khác nhau giữa binary32 và binary64 là một đánh đổi có thể kiểm chứng giữa **dung lượng, miền giá trị và độ phân giải**. Hãy chọn kiểu theo hợp đồng dữ liệu cùng sai số chấp nhận; chọn kiểu nguyên cho các đại lượng đếm cần đúng từng đơn vị và kiểu thập phân phù hợp cho nghiệp vụ cần decimal exact.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-002">Bit dấu, phần mũ và phần trị</a>

<details>
<summary>Xem chi tiết</summary>

# Bit dấu, phần mũ và phần trị trong biểu diễn dấu phẩy động Java

## 1. Mục tiêu: hiểu số `float`/`double` được lưu thế nào

`float` và `double` trong Java không lưu một số thập phân dưới dạng chuỗi chữ số như `"5.75"`. Chúng biểu diễn số theo dạng **dấu, phần mũ nhị phân và phần trị (significand)** với độ dài bit hữu hạn. Nắm được cấu trúc này giúp giải thích vì sao `0.1` không được biểu diễn chính xác, vì sao `-0.0` có thể khác `+0.0` ở mức bit, và vì sao một số phép tính cho `Infinity` hoặc `NaN`.

Bài này thuộc nhóm **Biểu diễn dấu phẩy động** của Java Core — Numbers. Sau khi học, bạn cần:

1. Chỉ được trường **sign**, **biased exponent** và **fraction field** trong một `float` 32 bit và `double` 64 bit.
2. Tính được phần mũ thực từ số mũ đã lưu bằng **bias 127** (binary32) hoặc **1023** (binary64).
3. Giải mã giá trị **normal** bằng bit `1` ngầm phía trước fraction; phân biệt với **subnormal** không có bit `1` ngầm.
4. Nhận diện đúng **±0.0**, **±Infinity**, **NaN** và ranh giới số hữu hạn.
5. Sử dụng `Float.floatToRawIntBits`, `Double.doubleToRawLongBits` và các bitmask để quan sát mẫu bit trong Java 17+.
6. Phân biệt **mẫu bit**, **giá trị toán học**, **cách in ra chuỗi** và **độ chính xác của phép tính**.

Kiến thức tiên quyết: nhị phân và lũy thừa 2, phép `&`, dịch phải `>>>`, literal hex `0x...`, kiểu `int`/`long` và `float`/`double`. Bài này dùng cấu trúc theo IEEE 754 binary32/binary64 và API Java SE 17.

## 2. Ba trường bit của một số dấu phẩy động

### 2.1. Tổng quan sơ đồ

**`float` (binary32)** có đúng 32 bit:

```text
bit index: 31 | 30................23 | 22..........................0
           S | E E E E E E E E    | F F F F F F F F F F F F F F F F F F F F F F F
width:     1 |        8           |                     23
```

**`double` (binary64)** có đúng 64 bit:

```text
bit index: 63 | 62................52 | 51...........................................0
           S  | E (11 bits)         | F (52 bits)
width:     1  |         11          |                    52
```

Trường `S` là **sign bit** (bit dấu); trường `E` là **stored/biased exponent** (mã số mũ); trường `F` là **fraction bits** hoặc **trailing significand field**. Thuật ngữ “mantissa” đôi khi được sử dụng trong tài liệu cũ, nhưng nên phân biệt rõ **fraction bits được lưu** với **significand thực** có thể bao gồm bit dẫn đầu ngầm định.

| Thuộc tính | `float` | `double` |
| --- | ---: | ---: |
| Độ dài toàn bộ | 32 bit | 64 bit |
| Bit dấu | 1 | 1 |
| Trường số mũ | 8 bit | 11 bit |
| Trường fraction | 23 bit | 52 bit |
| Bias | 127 | 1023 |
| Precision significand của số normal, kể cả bit đầu ngầm | 24 bit | 53 bit |
| Phần mũ không bias của số normal | −126 đến +127 | −1022 đến +1023 |
| Giá trị phần mũ lưu dành cho trường hợp đặc biệt | 0 và 255 | 0 và 2047 |

Dùng kiểu `int` để giữ toàn bộ bit của `float` và `long` để giữ toàn bộ bit của `double`. Nếu in những giá trị này theo **hệ 10 có dấu**, một mẫu bit có bit cao bật có thể trông thành số nguyên âm; muốn thấy rõ cấu trúc, hãy in hệ hex với độ rộng cố định.

### 2.2. Bit dấu có ý nghĩa gì?

Với các số hữu hạn khác 0 và vô cực:

- `S = 0` nghĩa là dấu dương;
- `S = 1` nghĩa là dấu âm.

Có thể hình dung hệ số dấu là `(-1)^S`. Ví dụ `+6.5` và `-6.5` có trường exponent/fraction giống nhau, nhưng bit dấu khác nhau.

**Lưu ý:** sign bit còn có trên `+0.0`, `-0.0` và các mẫu NaN. Không được coi dấu của NaN là “NaN âm có giá trị số âm” theo nghĩa so sánh số học thông thường; NaN không tham gia thứ tự số giống số hữu hạn.

## 3. Số normal: giải mã từ exponent và fraction

Khi trường số mũ lưu **không bằng 0 và cũng không toàn bit 1**, giá trị được gọi là **normal (normalized)**.

Công thức giá trị:

```text
normal = (-1)^S × (1 + F / 2^p) × 2^(E - bias)
```

Trong đó:

- `S`: bit dấu 0 hoặc 1.
- `E`: số nguyên không dấu thu được khi đọc toàn bộ trường exponent.
- `F`: số nguyên không dấu thu được khi đọc toàn bộ trường fraction.
- `p`: số bit fraction, bằng `23` với `float` và `52` với `double`.
- `bias`: `127` với `float`, `1023` với `double`.

**Ý nghĩa bit `1` ngầm:** với số normal, significand có dạng nhị phân `1.fraction_bits`. Bit `1` ở đầu không cần lưu trong trường fraction, giúp đạt 24 bit precision với 23 bit lưu ở `float`, hoặc 53 bit precision với 52 bit lưu ở `double`.

Ví dụ fraction nhị phân `0111000...` của `float` có nghĩa:

```text
1.0111₂ = 1 + 0/2 + 1/4 + 1/8 + 1/16 = 1.4375
```

Đừng đọc `E` là chính số mũ thật; phải **trừ bias**. Ví dụ `float` có `E = 129` thì số mũ thực là `129 - 127 = 2`. Với `double` có `E = 1025` thì số mũ thực là `1025 - 1023 = 2`.

### 3.1. Ví dụ giải mã `5.75f` từng bước

Đổi `5.75` sang nhị phân:

```text
5      = 101₂
0.75   = 0.11₂
5.75   = 101.11₂
       = 1.0111₂ × 2²
```

Trường sign bằng `0` vì số dương. Exponent thật `e = 2` nên exponent lưu của `float` là `2 + 127 = 129 = 10000001₂`. Fraction là các bit sau dấu chấm trong `1.0111₂`: `01110000000000000000000₂` (23 bit), bằng `0x380000`.

Ghép đủ 32 bit:

```text
sign       exponent       fraction
 0         10000001       01110000000000000000000
```

Chuỗi 32 bit bằng `0x40B80000`. Kiểm tra công thức:

```text
S = 0
E = 129
F / 2²³ = 0.4375
value = (+1) × (1 + 0.4375) × 2² = 5.75
```

Vì `0.75 = 3/4` có biểu diễn nhị phân hữu hạn, `5.75` được lưu chính xác ở đây.

### 3.2. Ví dụ giải mã `-6.5` ở kiểu `double`

`6.5 = 110.1₂ = 1.101₂ × 2²`.

- Sign `S = 1`.
- Unbiased exponent `e = 2`.
- Stored exponent `E = 2 + 1023 = 1025`.
- Fraction `.101₂ = 1/2 + 1/8 = 0.625`; 52 bit fraction tương đương số hex `0xA000000000000`.
- Toàn bộ mẫu bit `double`: `0xC01A000000000000`.

Kiểm tra: `(-1) × (1 + 0.625) × 4 = -6.5`.

## 4. Số exponent lưu không dùng tất cả các mã cho normal

Các trường số mũ `E = 0` và `E = toàn bit 1` được dành cho các ý nghĩa đặc biệt:

| Trường exponent | Trường fraction | Phân loại | Diễn giải |
| --- | --- | --- | --- |
| `0 < E < EmaxStored` | Bất kỳ | Normal | `1.fraction × 2^(E − bias)` với dấu |
| `E = 0` | `F = 0` | Zero | `+0.0` hay `-0.0` tùy sign |
| `E = 0` | `F != 0` | Subnormal | `0.fraction × 2^(1 − bias)` với dấu |
| `E = EmaxStored` | `F = 0` | Infinity | `+∞` hay `−∞` tùy sign |
| `E = EmaxStored` | `F != 0` | NaN | “Not a Number”, không phải một số có thứ tự |

Với `float`, `EmaxStored = 255`; với `double`, `EmaxStored = 2047`. Không nên nhầm **giá trị E lưu tối đa** với **unbiased normal exponent tối đa** (`127` hay `1023`).

Cũng không đúng khi nói “exponent 0 luôn là 2⁻¹²⁷” rồi thay vào công thức normal: khi `E=0`, cả số zero và subnormal dùng quy tắc riêng, được trình bày tiếp theo.

## 5. Subnormal: số quá nhỏ để biểu diễn ở dạng normal

Số **subnormal** (trước đây còn gọi denormal) dùng `E = 0` và `F != 0`. Nó không có bit `1` ngầm dẫn đầu; significand được hiểu là `0.fraction_bits`.

Công thức:

```text
subnormal = (-1)^S × (F / 2^p) × 2^(1 - bias)
```

Với `float`, `1 - bias = -126`; với `double` là `-1022`.

Tại sao cần subnormal? Giữa số normal dương nhỏ nhất và `+0.0` vẫn còn nhiều giá trị số dương có thể biểu diễn dưới dạng subnormal. Đây là cơ chế **gradual underflow** — độ chính xác giảm dần khi tiến gần zero, thay vì lập tức chuyển từ normal nhỏ nhất sang zero.

### 5.1. Các mốc `float`

- `Float.MIN_NORMAL` = `2⁻¹²⁶`, là **normal dương nhỏ nhất**: bit `0x00800000`.
- `Float.MIN_VALUE` = `2⁻¹⁴⁹`, là **subnormal dương nhỏ nhất**: bit `0x00000001`.
- Subnormal dương lớn nhất có bit `0x007FFFFF` và nằm ngay dưới `Float.MIN_NORMAL`.

**Tên `MIN_VALUE` dễ gây hiểu nhầm:** nó là giá trị **dương khác 0 nhỏ nhất**, không phải số âm nhỏ nhất; với số âm hữu hạn lớn về độ lớn, xem `-Float.MAX_VALUE`.

### 5.2. Các mốc `double`

- `Double.MIN_NORMAL` = `2⁻¹⁰²²`; bit `0x0010000000000000`.
- `Double.MIN_VALUE` = `2⁻¹⁰⁷⁴`; bit `0x0000000000000001`.
- Subnormal dương lớn nhất: `0x000FFFFFFFFFFFFF`.

**Điểm then chốt:** `MIN_NORMAL` và `MIN_VALUE` khác rất xa nhau về độ lớn; việc chỉ nhớ “double chính xác 15 chữ số” không đủ để hiểu underflow. Độ chính xác tương đối của subnormal kém hơn nhiều so với số normal, nhưng chúng vẫn biểu diễn đúng những mức rời rạc trong miền rất nhỏ.

### 5.3. Tính giá trị của subnormal nhỏ nhất

Với `float`, chọn `S=0`, `E=0`, `F=1`. Theo công thức:

```text
value = (1 / 2²³) × 2⁻¹²⁶
      = 2⁻¹⁴⁹
      = Float.MIN_VALUE
```

Không dùng `(1 + F / 2²³)` như với normal. Nếu có thêm bit `1` ngầm, giá trị sẽ không đúng tại `E = 0`.

## 6. Zero có bit dấu: `+0.0` và `-0.0`

Trong binary32:

```text
+0.0f = 0x00000000
-0.0f = 0x80000000
```

Trong binary64:

```text
+0.0  = 0x0000000000000000
-0.0  = 0x8000000000000000
```

Cả hai đều có `E = 0`, `F = 0`; chỉ khác `S`.

Trong phép so sánh số học:

```java
System.out.println(0.0 == -0.0); // true
```

Nhưng chúng vẫn là **hai mẫu bit khác nhau** và có thể tác động khác nhau tới một số phép toán:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

Vì vậy, cần xác định bạn đang so sánh **giá trị số**, **mẫu bit** hay **thứ tự toàn phần của wrapper**. `Double.compare(0.0, -0.0)` có kết quả dương (nó phân biệt dấu zero), trong khi `0.0 == -0.0` là `true`. Điều này không phải mâu thuẫn mà là hợp đồng so sánh khác nhau.

## 7. Infinity và NaN: khi exponent toàn bit 1

### 7.1. Vô cực

Với `float`, `E=255`, `F=0`:

```text
+Infinity  0x7F800000
-Infinity  0xFF800000
```

Với `double`, `E=2047`, `F=0`:

```text
+Infinity  0x7FF0000000000000
-Infinity  0xFFF0000000000000
```

`Float.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` là các hằng số API. Phép chia số thực như `1.0 / 0.0` cho vô cực, khác với `int` chia `0` ném `ArithmeticException`.

### 7.2. NaN

Với `E` toàn bit 1 **và** `F != 0`, mẫu bit biểu thị `NaN`.

- NaN có nhiều **bit pattern** khác nhau, không chỉ một chuỗi bit duy nhất.
- Sign bit có thể bằng 0 hoặc 1 nhưng không làm NaN trở thành một giá trị số âm/dương bình thường.
- `Double.NaN` và `Float.NaN` là hằng số có nghĩa NaN; không có primitive literal tên `NaN`.
- Theo phép so sánh floating-point `==`, `NaN == NaN` là `false`. Muốn kiểm tra, gọi `Float.isNaN` hoặc `Double.isNaN`.

`Float.floatToIntBits` và `Double.doubleToLongBits` **canonicalize NaN** khi xuất bit: các NaN được quy về một mẫu chuẩn (`0x7FC00000` cho `float` và `0x7FF8000000000000` cho `double`).

Nếu cần quan sát **raw bits** của NaN, dùng `Float.floatToRawIntBits` hoặc `Double.doubleToRawLongBits`. Tuy nhiên, một số NaN signaling có thể bị đổi thành quiet NaN khi truyền qua phần cứng hoặc phép toán; không nên hứa round trip bit tuyệt đối cho mọi payload NaN trên mọi nền tảng.

### 7.3. Phân loại chỉ từ E và F

Quy tắc phân loại có thể viết như sau:

```text
if E == 0:
    if F == 0: ZERO
    else:      SUBNORMAL
else if E == maximum exponent code:
    if F == 0: INFINITY
    else:      NaN
else:
    NORMAL
```

Đây là **pseudocode**, không phải Java độc lập. Ý nghĩa phụ thuộc đúng độ rộng trường exponent/fraction của `float` hay `double`.

## 8. Quan sát mẫu bit bằng Java thay vì đoán từ chuỗi

Các API chuẩn nên biết:

| Kiểu | Đọc bit | Dựng lại giá trị | Ghi chú |
| --- | --- | --- | --- |
| `float` | `Float.floatToRawIntBits(value)` | `Float.intBitsToFloat(bits)` | Trả/nhận `int` 32 bit |
| `float` | `Float.floatToIntBits(value)` | `Float.intBitsToFloat(bits)` | Chuẩn hóa NaN |
| `double` | `Double.doubleToRawLongBits(value)` | `Double.longBitsToDouble(bits)` | Trả/nhận `long` 64 bit |
| `double` | `Double.doubleToLongBits(value)` | `Double.longBitsToDouble(bits)` | Chuẩn hóa NaN |

**Không dùng cast primitive `(int) someFloat` để lấy bit!** Cast đó chuyển **giá trị số** sang số nguyên theo quy tắc narrowing, khác hoàn toàn việc tái diễn giải bit.

Ví dụ:

```java
float value = 5.75f;
int truncatedValue = (int) value;             // 5
int rawBits = Float.floatToRawIntBits(value); // 0x40B80000
```

Cùng một biến `float` nhưng hai giá trị `int` có nghĩa khác nhau: một số là kết quả chuyển giá trị, một số là **mã hóa bit**.

### 8.1. Ví dụ chạy được: tách sign/exponent/fraction của `float`

Lưu tệp **`FloatFields.java`**:

```java
public class FloatFields {
    public static void main(String[] args) {
        float value = 5.75f;
        int bits = Float.floatToRawIntBits(value);

        int sign = (bits >>> 31) & 1;
        int exponent = (bits >>> 23) & 0xFF;
        int fraction = bits & 0x7FFFFF;

        System.out.printf("%08X%n", bits);
        System.out.println(sign);
        System.out.println(exponent);
        System.out.printf("%06X%n", fraction);
        System.out.println(Float.intBitsToFloat(bits));
    }
}
```

Chạy với JDK 17+:

```bash
javac --release 17 FloatFields.java
java FloatFields
```

**Stdout chính xác:**

```text
40B80000
0
129
380000
5.75
```

Giải thích phép tách:

- `>>> 31` đưa bit dấu xuống cuối rồi `& 1` giữ một bit.
- `>>> 23` đưa 8 bit exponent về cuối rồi `& 0xFF` giữ đúng trường đó.
- `& 0x7FFFFF` giữ 23 bit fraction thấp.
- `printf("%08X%n", bits)` in 8 chữ số hex (32 bit); `%06X` in fraction tối thiểu 6 chữ số hex.
- `Float.intBitsToFloat(bits)` diễn giải lại đúng mẫu bit thành `5.75`.

Với `float`, dấu âm hay các mẫu NaN có thể khiến `bits` là số `int` âm khi đọc hệ 10. Đó là một **đặc điểm diễn giải số nguyên có dấu** của mẫu bit, không có nghĩa bản thân biến `float` bị ép về một giá trị nguyên âm có liên quan trực tiếp.

### 8.2. Ví dụ chạy được: tách trường của `double`

Lưu tệp **`DoubleFields.java`**:

```java
public class DoubleFields {
    public static void main(String[] args) {
        double value = -6.5;
        long bits = Double.doubleToRawLongBits(value);

        long sign = (bits >>> 63) & 1L;
        long exponent = (bits >>> 52) & 0x7FFL;
        long fraction = bits & 0x000F_FFFF_FFFF_FFFFL;

        System.out.printf("%016X%n", bits);
        System.out.println(sign);
        System.out.println(exponent);
        System.out.printf("%013X%n", fraction);
        System.out.println(Double.longBitsToDouble(bits));
    }
}
```

Chạy:

```bash
javac --release 17 DoubleFields.java
java DoubleFields
```

**Stdout chính xác:**

```text
C01A000000000000
1
1025
A000000000000
-6.5
```

Ở `double`, fraction có 52 bit = 13 chữ số hex; exponent 11 bit nên mask là `0x7FFL`. Tiền tố `L` của literal mask 64 bit thể hiện rõ phép toán `long`. Dù raw `bits` có dấu âm nếu đọc như `long`, dịch logic `>>>` và các mask vẫn trích đúng từng trường.

## 9. Ví dụ chạy được: các mẫu bit đặc biệt

Lưu thành **`SpecialFloatingBits.java`**:

```java
public class SpecialFloatingBits {
    public static void main(String[] args) {
        System.out.printf("%08X%n",
                Float.floatToRawIntBits(-0.0f));
        System.out.printf("%08X%n",
                Float.floatToRawIntBits(Float.MIN_VALUE));
        System.out.printf("%08X%n",
                Float.floatToRawIntBits(Float.MIN_NORMAL));
        System.out.printf("%08X%n",
                Float.floatToRawIntBits(Float.POSITIVE_INFINITY));

        System.out.printf("%016X%n",
                Double.doubleToRawLongBits(-0.0));
        System.out.println(0.0 == -0.0);
        System.out.println(Double.isNaN(0.0 / 0.0));
        System.out.println(Double.isInfinite(1.0 / 0.0));
        System.out.printf("%08X%n",
                Float.floatToIntBits(Float.intBitsToFloat(0x7FC01234)));
    }
}
```

Chạy:

```bash
javac --release 17 SpecialFloatingBits.java
java SpecialFloatingBits
```

**Stdout chính xác:**

```text
80000000
00000001
00800000
7F800000
8000000000000000
true
true
true
7FC00000
```

Bốn dòng `float` đầu là **−0.0**, **subnormal dương nhỏ nhất**, **normal dương nhỏ nhất**, **+Infinity**. Mẫu `double` tiếp theo là **−0.0**. Hai giá trị zero có so sánh `==` bằng nhau dù bit khác. Phép chia `0.0/0.0` tạo NaN; `1.0/0.0` tạo vô cực. Dòng cuối cho thấy `Float.floatToIntBits` quy nhiều bit pattern NaN về `0x7FC00000`.

Mã dùng `Float.intBitsToFloat(0x7FC01234)` để tạo một NaN theo bit; tránh diễn giải số nguyên `0x7FC01234` như một giá trị thập phân thực thông qua cast.

## 10. Precision: tại sao fraction field không đồng nghĩa đủ chữ số thập phân

Với số normal:

- `float`: **24 bit** precision cho significand (23 bit fraction lưu + 1 bit `1` ngầm).
- `double`: **53 bit** precision (52 bit fraction + 1 bit `1` ngầm).

Nghĩa là `float` phân biệt mọi số nguyên trong khoảng từ `-2²⁴` đến `+2²⁴` nếu chúng biểu diễn được chính xác; riêng `2²⁴ + 1` không có biểu diễn float khác `2²⁴` khi làm tròn theo round-to-nearest, ties-to-even. Tương tự, `double` biểu diễn chính xác mọi số nguyên có độ lớn đến `2⁵³`, nhưng không giữ tất cả các số nguyên lớn hơn mốc đó.

Ví dụ tự kiểm chứng:

```java
int bigFloatInt = 16_777_216; // 2^24
boolean floatEqual = (float) bigFloatInt
        == (float) (bigFloatInt + 1); // true

long bigDoubleLong = 9_007_199_254_740_992L; // 2^53
boolean doubleEqual = (double) bigDoubleLong
        == (double) (bigDoubleLong + 1); // true
```

Điều này không nói rằng `float` chỉ lưu được giá trị đến `2²⁴` hoặc `double` chỉ đến `2⁵³`: miền độ lớn của chúng còn rộng hơn nhiều nhờ exponent. Chỉ là **khoảng cách giữa các số biểu diễn được tăng lên khi exponent tăng**. Vì vậy:

- Trường exponent chủ yếu quyết định **thang độ lớn**.
- Trường significand/fraction quyết định **bước nhảy và độ chính xác tương đối** trong mỗi binade.
- Với số subnormal, precision tương đối giảm dần khi tiến tới 0.

### 10.1. Vì sao `0.1` bị làm tròn?

`0.1₁₀` có biểu diễn nhị phân vô hạn tuần hoàn. Vì số bit fraction hữu hạn, `float`/`double` lưu giá trị gần đúng sau khi làm tròn:

```java
double x = 0.1 + 0.2;
System.out.println(x);        // 0.30000000000000004
System.out.println(x == 0.3); // false
```

Đây là hệ quả cấu trúc sign/exponent/significand, không phải lỗi `println`. Nếu bài toán cần **thập phân chính xác theo nghiệp vụ** (ví dụ tiền tệ), cân nhắc `BigDecimal` khởi tạo từ chuỗi và quy tắc làm tròn rõ ràng; `BigDecimal` không dùng cấu trúc binary32/binary64 giống `float`/`double`.

### 10.2. Khoảng cách giữa số liên tiếp (ULP)

`Math.ulp(x)` trả về kích thước một **unit in the last place** của đối số ở định dạng floating point tương ứng. Ở gần `1.0f`, bước lên số float tiếp theo là `2⁻²³`; ở gần `1.0` kiểu `double`, bước lên thường là `2⁻⁵²`. Nhưng tại lân cận mốc lũy thừa 2, khoảng cách ở phía dưới có thể khác phía trên; không nên coi một epsilon cố định là đúng cho mọi thang giá trị.

`Math.nextUp(x)`/`Math.nextDown(x)` có thể dùng khi cần duyệt số floating-point biểu diễn kế cận. Chúng hữu ích cho kiểm thử ranh giới, nhưng không thay thế việc lựa chọn tiêu chí sai số phù hợp bài toán.

## 11. Từ hex floating-point literal quay lại ba trường bit

Java hỗ trợ literal có dạng `0x...p...`, trong đó `p` đánh dấu **số mũ cơ số 2**. Ví dụ:

```java
float a = 0x1.7p2f;    // 1.4375 × 2² = 5.75
double b = -0x1.ap2;   // -(1.625 × 2²) = -6.5
```

`0x1.7p2` có ý nghĩa số học trực tiếp gần với dạng normalized binary `1.0111₂ × 2²`. Có thể đối chiếu literal hex với `Float.toHexString` hoặc `Double.toHexString` khi cần đọc significand/exponent theo lũy thừa 2:

```java
System.out.println(Float.toHexString(5.75f)); // 0x1.7p2
System.out.println(Double.toHexString(-6.5)); // -0x1.ap2
```

**Quan trọng:** chuỗi do `toHexString` tạo ra là **biểu diễn giá trị**, không phải bản in trực tiếp từng trường raw bits. Với số subnormal, `toHexString` dùng cách thể hiện thích hợp cho miền dưới normal; muốn quan sát từng bit exponent/fraction, dùng API raw-bits và mask ở trên.

## 12. Phân biệt cấu trúc IEEE 754 với thứ tự byte trong bộ nhớ

Một `float` được định nghĩa có 32 bit giá trị; điều này không tự nói rằng bốn byte của nó xuất hiện theo thứ tự nào trong một file, gói tin hoặc `ByteBuffer`.

- **Bit layout** nói bit nào là sign/exponent/fraction khi xét mẫu 32/64 bit theo chỉ số.
- **Endianness** nói thứ tự các **byte** khi lưu cùng mẫu bit vào bộ nhớ/chuỗi byte. Big-endian và little-endian có thể cho thứ tự byte khác nhau, dù các trường dấu/phần mũ/phần trị của giá trị không thay đổi.
- `ByteBuffer` có chính sách byte order và có thể thiết lập qua `order(ByteOrder.BIG_ENDIAN)` hay `LITTLE_ENDIAN`.

Ví dụ mẫu `5.75f` là `0x40B80000`. Khi ghi 4 byte:

```text
big-endian:    40 B8 00 00
little-endian: 00 00 B8 40
```

Đây là ví dụ **thứ tự byte của cùng một mẫu bit**, không phải hai giá trị floating-point khác nhau. Khi trao đổi dữ liệu giữa các hệ thống, hợp đồng phải quy định cả định dạng số và byte order.

## 13. Những ngộ nhận và lỗi phổ biến

1. **“Fraction 23 bit nghĩa là float có 23 bit precision.”** Với normal, còn bit `1` ngầm, nên precision significand là **24 bit**.
2. **“Exponent lưu là exponent thật.”** Phải trừ **bias** cho normal; `E = 129` của float tương ứng `e = 2`.
3. **“Exponent 0 là một normal có số mũ -127.”** Sai. `E=0` được dành cho zero/subnormal và dùng công thức riêng với `1-bias`.
4. **“`Float.MIN_VALUE` là số âm nhỏ nhất.”** Sai. Đó là **số dương khác 0 nhỏ nhất**, một subnormal.
5. **“`~` hoặc cast `(int)` lấy đúng bit của số thực.”** Sai. Cast chuyển giá trị, không đọc bit encoding; dùng `Float.floatToRawIntBits`/`Double.doubleToRawLongBits`.
6. **“`-0.0` và `+0.0` giống hệt mọi thứ.”** Chúng bằng nhau khi so sánh `==`, nhưng có bit sign khác nhau và có thể dẫn tới dấu vô cực khác nhau khi chia.
7. **“Mỗi NaN luôn cùng một mẫu bit.”** Có nhiều NaN patterns; API `floatToIntBits`/`doubleToLongBits` sẽ canonicalize.
8. **“`0.1` được lưu chính xác do có nhiều chữ số thập phân hiển thị.”** Binary finite significand không thể biểu diễn chính xác 1/10; chuỗi hiển thị là lớp trình bày.
9. **“Exponent là số chữ số sau dấu chấm.”** Đây là lũy thừa **cơ số 2**, hoàn toàn khác scale thập phân của `BigDecimal`.
10. **“Bit layout quyết định byte order.”** Không. Cần quy định endianness nếu tuần tự hóa byte.
11. **“Số có bit sign bằng 1 luôn là số âm hữu hạn.”** Mẫu đó có thể là `-0.0`, `-Infinity` hoặc NaN.
12. **“Dạng normal và subnormal có cùng bit dẫn đầu ngầm.”** Với subnormal, significand là `0.fraction`, không phải `1.fraction`.

## 14. Bài luyện tập có đáp án

### Bài 1 — Vị trí từng trường

Hãy cho biết bit dấu, số bit exponent và số bit fraction của `float` và `double`.

**Đáp án:** `float` gồm `1 + 8 + 23 = 32` bit; `double` gồm `1 + 11 + 52 = 64` bit.

### Bài 2 — Bias

Với normal `float` có `E=130`, số mũ thật là bao nhiêu? Với normal `double` có `E=1025` thì sao?

**Đáp án:** `130−127=3` và `1025−1023=2`. Phải trừ bias tương ứng.

### Bài 3 — Giải mã `5.75f`

Biết mẫu bit là `0x40B80000`. Tách `S`, `E` và `F`, rồi tính lại giá trị.

**Đáp án:** `S=0`, `E=129`, `F=0x380000`. Fraction = `0.4375`; giá trị = `(1+0.4375)×2²=5.75`.

### Bài 4 — Phân biệt 0 và subnormal

Hai mẫu `0x00000000` và `0x00000001` (float) có cùng trường exponent. Chúng có giống nhau không?

**Đáp án:** không. `E=0,F=0` là `+0.0`; `E=0,F=1` là subnormal dương nhỏ nhất `2⁻¹⁴⁹`.

### Bài 5 — Nhận diện vô cực và NaN

Với `float`, `E=255`, `F=0` biểu thị gì? `E=255`, `F=1` biểu thị gì?

**Đáp án:** trường hợp thứ nhất là vô cực với dấu quyết định ±; trường hợp thứ hai là NaN vì fraction khác 0. Không gán NaN vào thứ tự số học thường.

### Bài 6 — Dấu zero

Vì sao `0.0 == -0.0` là true nhưng `Double.doubleToRawLongBits(0.0)` và `Double.doubleToRawLongBits(-0.0)` khác nhau?

**Đáp án:** quan hệ `==` là so sánh floating-point theo giá trị; raw bit bao gồm bit dấu. Hai zero có sign bit khác nhau.

### Bài 7 — Cast hay raw bits?

Cho `float f=5.75f`. `(int)f` và `Float.floatToRawIntBits(f)` cho gì?

**Đáp án:** cast cho `5`; raw-bits cho mẫu `0x40B80000` (nếu in như `int` thập phân, là một số nguyên lớn). Chúng có mục đích khác nhau.

### Bài 8 — Decimal vs binary precision

Vì sao `0.1 + 0.2 == 0.3` là false khi dùng `double`?

**Đáp án:** 1/10 và 1/5 có biểu diễn nhị phân tuần hoàn, được làm tròn về tập giá trị binary64; tổng làm tròn không nhất thiết khớp với mẫu binary64 của literal 0.3.

### Bài 9 — Đổi endian

Mẫu `float` `0x40B80000` tương ứng 4 byte nào ở big-endian và little-endian?

**Đáp án:** `40 B8 00 00` và `00 00 B8 40`. Giá trị float không thay đổi; chỉ thứ tự byte.

### Bài 10 — Số nguyên lớn và khoảng cách biểu diễn

Tại sao `(float)16_777_216` có thể bằng `(float)16_777_217` dù hai số `int` khác nhau?

**Đáp án:** binary32 có 24 bit precision significand, không có đủ bit để phân biệt tất cả số nguyên ở thang lớn hơn `2²⁴`; giá trị kế tiếp có thể được làm tròn về cùng mẫu.

## 15. Checklist và tài liệu chuẩn

- [ ] Tôi xác định đúng **bit sign/exponent/fraction** cho binary32 và binary64.
- [ ] Tôi không nhầm trường fraction lưu với toàn bộ significand normal.
- [ ] Tôi biết bias `127` và `1023`, và giới hạn unbiased exponent normal.
- [ ] Tôi viết được công thức giải mã **normal** và **subnormal**.
- [ ] Tôi phân biệt được zero, subnormal, normal, vô cực, NaN theo `E` và `F`.
- [ ] Tôi giải thích được `-0.0` khác `+0.0` ở raw bits, dù `==` là true.
- [ ] Tôi dùng được `floatToRawIntBits` và `doubleToRawLongBits`, không nhầm chúng với cast giá trị.
- [ ] Tôi hiểu NaN canonicalization và rủi ro round-trip signaling NaN.
- [ ] Tôi không suy luận độ chính xác từ số chữ số được `println` hiển thị.
- [ ] Tôi biết byte order là vấn đề riêng khi đọc/ghi các byte của mẫu bit.
- [ ] Tôi đã chạy ba chương trình và kiểm tra chính xác hex/giá trị đầu ra.

**Tài liệu chính thức cho Java 17:**

- [Java Language Specification, `4.2.3 Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3) — ngữ nghĩa floating point.
- [Java SE 17 Float API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html) — bit layout, `floatToIntBits`, `floatToRawIntBits`, `intBitsToFloat`.
- [Java SE 17 Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html) — `doubleToLongBits`, `doubleToRawLongBits`, `longBitsToDouble`.
- [Java SE 17 Math API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html) — `ulp`, `nextUp`, `nextDown`.
- [Java SE 17 ByteBuffer API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html) — byte order và thao tác với dữ liệu nhị phân.

Cách đọc hiệu quả một số `float`/`double` là: **đọc sign, giải mã exponent, chọn công thức normal/subnormal hoặc nhận diện trường hợp đặc biệt, rồi diễn giải fraction theo cơ số 2**. Khi đã hiểu các trường này, nhiều hiện tượng “lạ” của số thực trở thành hệ quả có thể giải thích và kiểm chứng được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-003">Precision nhị phân và số chữ số thập phân có thể tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# Precision nhị phân và số chữ số thập phân có thể tin cậy trong Java

Khi viết `double x = 0.1;`, nhiều người nghĩ máy tính đã lưu đúng **một phần mười**. Thực tế `float` và `double` của Java dùng biểu diễn dấu phẩy động **nhị phân**, nên đa số phân số thập phân như 0,1 không thể lưu chính xác. Giá trị được làm tròn tới một số nhị phân biểu diễn được gần nhất.

Một câu hỏi quan trọng hơn câu “`double` có bao nhiêu chữ số?” là: **số chữ số nào có thể tin cậy cho phép tính, và cần bao nhiêu chữ số thập phân để ghi ra rồi đọc lại đúng cùng giá trị bit?** Hai con số này khác nhau.

Bài học làm rõ *precision* (độ chính xác của phần trị), sai số làm tròn, khoảng cách giữa các số biểu diễn được (*ULP*), ý nghĩa của khoảng “6–9 chữ số” với `float`, “15–17 chữ số” với `double` và cách sử dụng các con số ấy khi lập trình Java 17+.

## 1. `float` và `double` lưu số bằng các bit như thế nào?

Java sử dụng biểu diễn dấu phẩy động nhị phân IEEE 754 cho hai kiểu:

| Đặc điểm | `float` | `double` |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit số mũ lưu trữ (*exponent field*) | 8 | 11 |
| Bit phần phân số lưu trữ (*fraction field*) | 23 | 52 |
| Độ chính xác phần trị (*significand precision*) cho số chuẩn hóa | **24 bit** | **53 bit** |
| Số chữ số thập phân có nghĩa thường có thể tin cậy | **khoảng 6–7** | **khoảng 15–16** |
| Số chữ số thập phân có nghĩa tối đa đủ để ghi mọi giá trị rồi đọc lại đúng bit | **9** | **17** |

Tại sao có 24 bit nhưng chỉ lưu 23 bit phân số? Với **số chuẩn hóa** (*normal number*), IEEE 754 có một bit 1 đầu tiên được **ngầm hiểu** trong phần trị nhị phân. Vì vậy `float` có độ chính xác 24 bit và `double` có 53 bit. Với **số dưới chuẩn** (*subnormal*) rất gần 0, bit 1 ngầm định không còn nữa; số bit có nghĩa thực tế có thể ít hơn.

Về ý tưởng, số chuẩn hóa có dạng:

```text
(-1)^sign × (1.fractionBits)_2 × 2^exponent
```

Số mũ cho phép biểu diễn số rất lớn hoặc rất nhỏ; **số bit phần trị** mới quyết định khoảng cách giữa hai giá trị liên tiếp và độ chi tiết có thể giữ lại. Đừng nhầm **range** (miền độ lớn) với **precision** (độ phân giải số).

Ví dụ `double` có thể có trị tuyệt đối đến khoảng `1.8 × 10^308` nhưng không thể phân biệt được mọi cặp số nguyên liên tiếp tại khoảng `10^16`.

## 2. Vì sao 0,1 thập phân không được lưu chính xác?

Hệ nhị phân biểu diễn chính xác các phân số có mẫu số là lũy thừa của 2 sau khi rút gọn:

- `0.5 = 1/2 = 0.1₂` được lưu chính xác.
- `0.25 = 1/4 = 0.01₂` được lưu chính xác.
- `0.125 = 1/8 = 0.001₂` được lưu chính xác.
- `0.1 = 1/10` có mẫu số chứa thừa số 5, nên khai triển nhị phân kéo dài vô hạn và không thể lưu đúng bằng hữu hạn bit.

Giá trị `double` gần `0.1` thực sự là:

```text
0.1000000000000000055511151231257827021181583404541015625
```

Đây là giá trị thập phân **chính xác của số nhị phân được lưu**, không phải kết quả Java mặc định in ra cho literal. Với `System.out.println(0.1)`, Java thường in `0.1` vì chuỗi ngắn đó đủ để đọc ngược lại ra cùng giá trị `double`.

```java
System.out.println(0.1);       // 0.1 (định dạng ngắn có thể round-trip)
System.out.println(0.1 + 0.2); // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false
```

Cả `0.1`, `0.2` và `0.3` đều đã được làm tròn về nhị phân, và phép cộng được làm tròn thêm. Vì thế kết quả cộng hai giá trị lưu gần `0.1` và `0.2` không nhất thiết bằng bit của giá trị lưu gần `0.3`.

Quan trọng: **sai số không có nghĩa JVM tính “sai chuẩn Java”**. Chính phép tính trên `double` tuân theo biểu diễn và quy tắc làm tròn được quy định; khác biệt xuất hiện khi đối chiếu với số học thập phân chính xác mà con người kỳ vọng.

## 3. Precision nhị phân: 24 bit và 53 bit phần trị

Độ chính xác phần trị `p` bit có nghĩa là, quanh một số chuẩn hóa, phần trị chứa khoảng `p` bit thông tin nhị phân. Đổi sang chữ số thập phân có nghĩa bằng phép ước lượng:

```text
float:  24 × log10(2) ≈ 7.2247 chữ số
double: 53 × log10(2) ≈ 15.9546 chữ số
```

Vì vậy thường nói **`float` có khoảng 7 chữ số có nghĩa**, **`double` có khoảng 15–16 chữ số có nghĩa**. Đây là hướng dẫn thực tế về độ chính xác tương đối của số chuẩn hóa, **không phải cam kết rằng mọi giá trị in ra có đúng 7 hoặc 16 chữ số đáng tin**.

Ví dụ:

```java
float f = 16_777_216f;
float changed = f + 1f;
// f và changed bằng nhau: tại 2^24, ULP phía trên là 2 nên +1 bị làm tròn trở lại
```

Ở độ lớn lớn hơn, đơn vị nhỏ không còn phân biệt được. Cần luôn xét **độ lớn của số**, **độ chênh cần đo** và **các phép toán đã diễn ra**, không chỉ đếm chữ số sau dấu phẩy.

## 4. Khác biệt giữa số chữ số đáng tin và số chữ số để round-trip

Hai hướng chuyển đổi dữ liệu là:

1. **Decimal → Binary → Decimal:** ta nhập một số thập phân có số chữ số có nghĩa nhất định, lưu bằng `float`/`double` rồi muốn diễn tả lại con số ban đầu với độ chính xác đã chọn.
2. **Binary → Decimal → Binary:** ta đã có một giá trị bit bất kỳ của `float`/`double`, ghi ra chuỗi thập phân, rồi muốn parse chuỗi trở lại **đúng giá trị bit ban đầu**.

Hai mục tiêu yêu cầu số chữ số khác nhau.

| Câu hỏi | `float` | `double` |
| --- | ---: | ---: |
| Số chữ số có nghĩa thập phân bảo thủ thường dùng khi cần giữ độ chính xác | **6** | **15** |
| Giới hạn chữ số dùng để **đảm bảo mọi giá trị hữu hạn** có thể round-trip dạng nhị phân → chuỗi đủ chính xác → nhị phân | **9** | **17** |
| Hướng dẫn gần đúng về độ chính xác có nghĩa trong bài tính | **6–7** | **15–16** |

**9 hoặc 17 không phải số chữ số “đều chính xác so với giá trị toán học ban đầu”.** Chúng là số chữ số thập phân có nghĩa đủ để **phân biệt các giá trị nhị phân** khi chuyển đổi văn bản với cách làm tròn phù hợp.

Ngược lại, **6 và 15** là những mốc bảo thủ để thảo luận việc duy trì các chữ số thập phân có nghĩa qua chuyển đổi; chúng không tuyên bố rằng chữ số thứ 7 hay 16 luôn sai. Từng giá trị như `0.5` có thể được biểu diễn chính xác, còn số như `0.1` không.

Trong Java, `Float.toString` và `Double.toString` chọn biểu diễn thập phân ngắn cần thiết theo quy tắc chuyển đổi của Java; khi cần lưu/đọc lại các giá trị, nên dùng các API ấy và `parseFloat`/`parseDouble` thay vì tự cắt cố định 6 hoặc 15 chữ số.

```java
double original = Math.PI;
String saved = Double.toString(original);
double restored = Double.parseDouble(saved);

boolean sameBits =
        Double.doubleToRawLongBits(original)
        == Double.doubleToRawLongBits(restored);
// true
```

Với chương trình thực tế, nếu có `NaN` mang payload đặc biệt, nhiều chi tiết bit không được bảo toàn qua văn bản; ví dụ ở trên dùng giá trị hữu hạn bình thường. `Double.toString` xuất `-0.0` riêng, nhưng đừng mở rộng nhận xét round-trip đơn giản này thành bảo đảm giữ mọi payload `NaN`.

## 5. ULP: khoảng cách giữa các giá trị biểu diễn được

**ULP** (*unit in the last place*) có thể hiểu thực dụng là độ lớn của một bước lưới số ở độ lớn đang xét. Với số chuẩn hóa thuộc vùng `[2^e, 2^(e+1))`, khoảng cách giữa các số cùng vùng xấp xỉ:

```text
ULP ≈ 2^(e - (p - 1))
p = 24 đối với float; p = 53 đối với double
```

Khi số lớn dần, **khoảng cách giữa các số biểu diễn được cũng tăng**. Vì vậy ta không thể nói `double` luôn chính xác đến “15 chữ số sau dấu chấm”: con số đáng quan tâm là **chữ số có nghĩa**, và độ phân giải tuyệt đối phụ thuộc vào độ lớn giá trị.

Java cung cấp `Math.ulp` và `Math.nextUp` để quan sát:

```java
System.out.println(Math.ulp(1.0f)); // 1.1920929E-7
System.out.println(Math.ulp(1.0));  // 2.220446049250313E-16
System.out.println(Math.nextUp(1.0)); // 1.0000000000000002
```

Tại chính `1.0`, `Math.ulp(1.0)` là khoảng từ 1.0 đến số `double` lớn hơn gần nhất. Về phía dưới 1.0, bước có thể nhỏ hơn do ranh giới giữa hai vùng số mũ.

### Số nguyên liên tiếp bắt đầu “dính” vào nhau ở đâu?

`float` có 24 bit phần trị, nên mọi số nguyên từ `-2^24` đến `2^24` đều biểu diễn chính xác; nhưng `2^24 + 1` không biểu diễn được dưới dạng `float`.

```java
int originalInt = 16_777_217; // 2^24 + 1
float roundedFloat = originalInt; // 16777216.0f
```

`double` có 53 bit phần trị, nên mọi số nguyên từ `-2^53` đến `2^53` đều biểu diễn chính xác; `2^53 + 1` không biểu diễn được dưới dạng `double`:

```java
long originalLong = 9_007_199_254_740_993L; // 2^53 + 1
double roundedDouble = originalLong; // 9.007199254740992E15
```

**Đây đều là widening hợp lệ của Java**, không cần cast và không ném exception, nhưng có mất một đơn vị giá trị. Nếu dữ liệu là ID, số lượng nguyên lớn, bộ đếm hoặc giá trị cần so sánh tuyệt đối, đừng tự ý chuyển `long` qua `double`.

## 6. Số thực có rất nhiều chữ số sau dấu phẩy không có nghĩa là tin cậy hết

Việc dùng một định dạng in rất dài, hoặc chuyển `double` thành biểu diễn thập phân chính xác của chính các bit đang lưu, có thể tạo ra hàng chục chữ số sau dấu chấm. Điều đó **không biến những chữ số ấy thành độ chính xác mới** so với con số người dùng đã định nhập.

```java
double x = 0.1;
System.out.println(Double.toString(x)); // 0.1
System.out.println(new java.math.BigDecimal(x));
// 0.1000000000000000055511151231257827021181583404541015625
```

`Double.toString(x)` hiển thị biểu diễn thập phân phù hợp để parse lại cùng giá trị binary; `new BigDecimal(x)` lấy **chính xác giá trị nhị phân đang có** rồi diễn tả thành thập phân, phơi bày sai khác.

Ngược lại:

```java
java.math.BigDecimal decimal =
        new java.math.BigDecimal("0.1");
java.math.BigDecimal fromValueOf =
        java.math.BigDecimal.valueOf(0.1);
```

Cả hai ở ví dụ trên biểu diễn giá trị thập phân `0.1` theo dạng decimal; `BigDecimal.valueOf(double)` dựa trên chuỗi mà `Double.toString` tạo ra, **không** làm lộ toàn bộ phân số nhị phân như constructor `new BigDecimal(double)`.

Nếu dữ liệu được nhập dưới dạng thập phân và phải giữ chính xác số thập phân đó, tạo `BigDecimal` từ **chuỗi** hoặc từ số nguyên cùng *scale* là cách rõ ràng hơn việc đi vòng qua `double`.

## 7. Sai số tích lũy và hiện tượng triệt tiêu chữ số có nghĩa

Sai số không chỉ xuất hiện khi tạo literal: mỗi phép cộng, trừ, nhân, chia số thực có thể làm tròn. Nhiều phép toán liên tiếp có thể tích lũy sai lệch:

```java
double sum = 0.0;
for (int i = 0; i < 10; i++) {
    sum += 0.1;
}
System.out.println(sum); // 0.9999999999999999
```

Tổng toán học là 1, nhưng từng lần cộng và lưu kết quả đều có thể làm tròn. **10 phép toán không bảo đảm sai số gấp đúng 10 lần**: sai số có thể bù trừ hoặc tăng tùy dữ liệu và thứ tự tính.

Một hiện tượng đáng chú ý là **cancellation** (mất chữ số có nghĩa khi trừ hai số gần nhau):

```java
double large = 1e16;
double tinyAddition = (large + 1.0) - large;
System.out.println(tinyAddition); // 0.0, không phải 1.0
```

Tại khoảng `1e16`, bước lưới của `double` lớn hơn 1, nên phép `large + 1.0` đã làm tròn thành `large`. Phép trừ phía sau không thể phục hồi phần đã mất.

Còn với một phép trừ hai số lớn gần bằng nhau, sai số đã có trong từng toán hạng cũng có thể lộ rõ ở kết quả nhỏ. Đây là lý do các bài toán số cần xem xét **độ ổn định số học (*numerical stability*)** ngoài việc chọn `float` hay `double`.

## 8. So sánh hai số `double`: khi nào dùng `==`?

`==` là phép so sánh chính xác theo ngữ nghĩa dấu phẩy động, không áp dụng “ngưỡng gần bằng” tự động:

```java
System.out.println(0.1 + 0.2 == 0.3); // false
```

Nếu hai phép tính độc lập phải tạo **đúng một giá trị bit**, `==` có thể phù hợp. Nhưng nếu mục tiêu là “hai kết quả số học đủ gần”, có thể dùng ngưỡng sai số được thiết kế theo miền dữ liệu.

Một hàm so sánh cho **hai giá trị hữu hạn**:

```java
static boolean approximatelyEqual(double a, double b,
                                  double absTol, double relTol) {
    double diff = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return diff <= Math.max(absTol, relTol * scale);
}
```

Method này là **đoạn minh họa bên trong class**, giả định `a`, `b` hữu hạn và `absTol`/`relTol` không âm; ứng dụng cần xử lý thêm `NaN`, vô cực, và tình huống trung gian bị tràn khi trừ hoặc tính ngưỡng. Không có một ngưỡng cố định tốt cho mọi ứng dụng.

Ví dụ ngưỡng tương đối thường hợp lý với số lớn, còn ngưỡng tuyệt đối có thể hữu ích gần 0. Với các bài toán tiền tệ, lựa chọn thường không phải “`double` + epsilon”, mà là **`BigDecimal`** hoặc số nguyên đơn vị nhỏ nhất kèm kiểm tra tràn.

### Các giá trị đặc biệt

- `Double.NaN == Double.NaN` cho `false`; `Double.isNaN` dùng kiểm tra NaN.
- `Double.POSITIVE_INFINITY`/`NEGATIVE_INFINITY` cần được xử lý theo miền dữ liệu; phép tính có vô cực không có cùng ý nghĩa với phép đo hữu hạn.
- `0.0 == -0.0` cho `true`, nhưng biểu diễn bit và một số phép toán có thể phân biệt dấu của zero.
- `Double.compare` cung cấp cách so sánh thứ tự theo quy tắc Java, khác với ngữ nghĩa so sánh số học thông thường ở NaN và zero có dấu.

Đối với khóa map, sort, serialization hoặc kiểm tra bit chính xác, cần chọn quy tắc so sánh phù hợp, không tự thay tất cả `==` bằng một công thức epsilon.

## 9. `float` so với `double`: chọn kiểu theo độ chính xác thực sự

`double` thường là lựa chọn mặc định cho tính toán số thực trong Java. Literal `0.1` có kiểu `double`, literal `0.1f` có kiểu `float`:

```java
double d = 0.1;
float f = 0.1f;
// float invalid = 0.1; // lỗi: double literal không tự narrowing sang float
```

`double` có độ chính xác phần trị cao hơn, nhưng vẫn **không đại diện cho số học thập phân chính xác**.

Với đồ họa, dữ liệu sensor, học máy hoặc tập hợp lớn mà nhu cầu precision cho phép, `float` có thể hợp lý về bộ nhớ và băng thông. Với mô phỏng/tính toán cần độ chính xác tương đối cao hơn, `double` thường phù hợp. Đối với giá tiền, lãi, thuế hoặc phép tính tài chính phải đúng quy tắc thập phân, dùng `BigDecimal` với `RoundingMode` phù hợp thay vì dựa trên độ chính xác của `double`.

**Đừng dùng “số chữ số in ra” làm thước đo duy nhất.** Cần xác định sai số tuyệt đối, sai số tương đối, giá trị lớn/nhỏ nhất, điều kiện tính toán, và cách người dùng sẽ sử dụng kết quả.

## 10. Chương trình Java 17+ hoàn chỉnh và output có thể kiểm chứng

Lưu nội dung sau thành **`BinaryPrecisionDemo.java`**:

```java
import java.math.BigDecimal;

public class BinaryPrecisionDemo {
    public static void main(String[] args) {
        System.out.println("floatBits=" + Float.SIZE);
        System.out.println("doubleBits=" + Double.SIZE);

        System.out.println("exactHalf=" + (0.5 + 0.25));
        System.out.println("decimalSum=" + (0.1 + 0.2));
        System.out.println("decimalEqual=" + (0.1 + 0.2 == 0.3));

        System.out.println("floatUlpOne=" + Math.ulp(1.0f));
        System.out.println("doubleUlpOne=" + Math.ulp(1.0));
        System.out.println("nextDouble=" + Math.nextUp(1.0));

        int bigInt = 16_777_217;
        float roundedFloat = bigInt;
        System.out.println("intViaFloat=" + (int) roundedFloat);

        long bigLong = 9_007_199_254_740_993L;
        double roundedDouble = bigLong;
        System.out.println("longViaDouble=" + (long) roundedDouble);
        System.out.println("longEqualsDouble=" + (bigLong == roundedDouble));

        double d = 0.1;
        System.out.println("shortDecimal=" + Double.toString(d));
        System.out.println("binaryAsDecimal=" + new BigDecimal(d));
        System.out.println("decimalFromString=" + new BigDecimal("0.1"));
        System.out.println("decimalValueOf=" + BigDecimal.valueOf(d));

        String encoded = Double.toString(Math.PI);
        double recovered = Double.parseDouble(encoded);
        System.out.println("roundTrip=" +
                (Double.doubleToRawLongBits(recovered) ==
                 Double.doubleToRawLongBits(Math.PI)));

        double tenTimes = 0.0;
        for (int i = 0; i < 10; i++) {
            tenTimes += 0.1;
        }
        System.out.println("tenTimes=" + tenTimes);
        System.out.println("lostUnit=" + ((1e16 + 1.0) - 1e16));

        System.out.println("nanSelfEqual=" + (Double.NaN == Double.NaN));
        System.out.println("signedZeroEqual=" + (0.0 == -0.0));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BinaryPrecisionDemo.java
java BinaryPrecisionDemo
```

Đầu ra:

```text
floatBits=32
doubleBits=64
exactHalf=0.75
decimalSum=0.30000000000000004
decimalEqual=false
floatUlpOne=1.1920929E-7
doubleUlpOne=2.220446049250313E-16
nextDouble=1.0000000000000002
intViaFloat=16777216
longViaDouble=9007199254740992
longEqualsDouble=true
shortDecimal=0.1
binaryAsDecimal=0.1000000000000000055511151231257827021181583404541015625
decimalFromString=0.1
decimalValueOf=0.1
roundTrip=true
tenTimes=0.9999999999999999
lostUnit=0.0
nanSelfEqual=false
signedZeroEqual=true
```

Điểm bất ngờ là `longEqualsDouble=true`: trong phép so sánh `long == double`, `long` được widening sang `double` và làm tròn, nên so sánh có thể cho `true` dù `long` ban đầu không được giữ nguyên khi chuyển đổi. Không nên dùng so sánh giữa `long` lớn và `double` để xác minh ID bằng nhau.

## 11. Các lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`double` có 15 chữ số sau dấu phẩy.” | Nó có **53 bit phần trị**, tương đương khoảng 15–16 **chữ số thập phân có nghĩa** cho số chuẩn hóa; không cố định theo số chữ số sau dấu phẩy. |
| “9 chữ số với `float`, 17 chữ số với `double` đều là chữ số toán học chính xác.” | 9/17 là giới hạn cho **round-trip nhị phân → chuỗi đủ chính xác → nhị phân**, không phải độ chính xác của phép đo ban đầu. |
| “Nếu Java in `0.1` thì đã lưu chính xác 0,1.” | `Double.toString` in biểu diễn ngắn đủ khôi phục cùng giá trị nhị phân, chứ không in toàn bộ số thập phân chính xác của bit lưu. |
| “`float` không thể giữ được số nguyên lớn vì tối đa chỉ ~3,4 × 10^38.” | `float` có miền **độ lớn** rộng, nhưng precision 24 bit nên nhiều số nguyên liên tiếp gần 2^24 không phân biệt được. |
| “Nâng `long` lên `double` là luôn chính xác vì 64 bit.” | `double` dùng 53 bit precision, nên `long` lớn có thể bị làm tròn. |
| “`==` luôn dùng tốt sau các phép cộng số thực.” | Có thể khác do làm tròn; chọn cách so sánh dựa trên yêu cầu của bài toán. |
| “Cộng mười lần 0,1 chắc chắn ra 1,0.” | Mỗi phép cộng có thể làm tròn, thứ tự tính ảnh hưởng đến sai số. |
| “Thêm chữ số khi in ra sẽ tăng độ chính xác lưu trữ.” | Định dạng chỉ thay đổi chuỗi hiển thị; không bổ sung bit thông tin đã mất. |
| “`BigDecimal.valueOf(0.1)` giống hoàn toàn `new BigDecimal(0.1)`.” | `valueOf` dùng biểu diễn chuỗi chuẩn của `double`; constructor `double` diễn tả chính xác giá trị nhị phân đã lưu. |
| “Có thể chọn epsilon 1e-9 cho mọi phép so sánh.” | Sai số hợp lý phụ thuộc thang đo, miền dữ liệu, sai số phép đo và thuật toán; cần chính sách cụ thể. |

### Checklist thực hành

1. **Chọn kiểu theo độ chính xác, không chỉ theo độ lớn.** `float` 24 bit, `double` 53 bit significand; cần thập phân chính xác thì cân nhắc `BigDecimal`.
2. **Phân biệt precision và round-trip:** khoảng 6–7/15–16 chữ số có nghĩa là hướng dẫn độ chính xác; 9/17 chữ số đủ để lưu chuỗi round-trip của mọi giá trị hữu hạn tương ứng.
3. **Đừng đếm chữ số sau dấu chấm:** sai số tuyệt đối và khoảng cách ULP thay đổi theo độ lớn.
4. **Kiểm tra phép cộng/trừ/multiplication nhiều bước:** sai số có thể tích lũy hoặc khuếch đại do mất chữ số có nghĩa; xem tính ổn định của thuật toán.
5. **Không mặc định dùng `==` cho kết quả số học gần đúng:** xác định miền dữ liệu, ngưỡng và cách xử lý NaN/vô cực phù hợp.
6. **Khi lưu và phục hồi bit số thực:** dùng `Float.toString`/`Double.toString` và parse tương ứng; khi cần giá trị decimal toán học chính xác, đừng đi vòng qua `double`.

**Ghi nhớ:** `float` và `double` lưu một lưới giá trị **nhị phân hữu hạn**. Quy tắc 6–9 hoặc 15–17 chữ số chỉ có nghĩa khi nói rõ mục tiêu: **độ chính xác của phép tính** hay **độ dài chuỗi cần để round-trip giá trị đang lưu**. Hiểu khác biệt này giúp tránh nhầm các chữ số hiển thị với độ tin cậy thực sự của dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-004">Vì sao 0.1 thường không được biểu diễn chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `0.1` thường không được biểu diễn chính xác trong Java?

## 1. Hiện tượng: số trông đơn giản nhưng phép tính cho kết quả lạ

Trong Java, những phép tính quen thuộc sau có thể gây ngạc nhiên:

```java
double a = 0.1;
double b = 0.2;
double sum = a + b;

System.out.println(a);          // 0.1
System.out.println(sum);        // 0.30000000000000004
System.out.println(sum == 0.3); // false
```

Đây **không phải Java tính toán ngẫu nhiên**. Các kiểu `float` và `double` lưu số thực bằng **biểu diễn dấu phẩy động nhị phân hữu hạn**, thường theo chuẩn IEEE 754. Một số phân số thập phân, trong đó có `0.1`, **không có biểu diễn nhị phân hữu hạn**, nên phải được làm tròn thành giá trị biểu diễn được gần nhất.

Điểm quan trọng:

- `0.1` được viết chính xác theo **hệ thập phân**, nhưng không nhất thiết được lưu chính xác trong `double`.
- Lỗi xấp xỉ thường xuất hiện **ngay khi literal được chuyển thành giá trị dấu phẩy động**, trước cả phép cộng.
- Sau đó mỗi phép toán dấu phẩy động có thể cần làm tròn lần nữa. Vì vậy `0.1 + 0.2` không nhất thiết trùng mẫu bit với literal `0.3`.
- `System.out.println(0.1)` in `0.1` **không chứng minh bit lưu bên trong đúng bằng một phần mười toán học**.

Học cách phân biệt **giá trị toán học**, **giá trị nhị phân được lưu** và **chuỗi thập phân khi hiển thị** là chìa khóa để hiểu vấn đề.

## 2. Phân số trong hệ nhị phân khác phân số trong hệ thập phân

Trong hệ thập phân, mỗi chữ số sau dấu chấm ứng với lũy thừa âm của 10:

```text
0.1 (hệ 10) = 1 × 10^-1 = 1/10
0.25        = 2 × 10^-1 + 5 × 10^-2
            = 25/100 = 1/4
```

Trong hệ nhị phân, các chữ số sau dấu chấm tương ứng với lũy thừa âm của 2:

```text
0.1₂   = 1 × 2^-1 = 1/2  = 0.5₁₀
0.01₂  = 1 × 2^-2 = 1/4  = 0.25₁₀
0.001₂ = 1 × 2^-3 = 1/8  = 0.125₁₀
0.11₂  = 1/2 + 1/4       = 0.75₁₀
```

Một phân số hữu tỉ ở dạng tối giản `p/q` (với `q > 0`) có biểu diễn **nhị phân hữu hạn** khi và chỉ khi mẫu `q` là lũy thừa của 2, ví dụ `1`, `2`, `4`, `8`, `16`, ...

| Giá trị thập phân | Phân số tối giản | Có biểu diễn nhị phân hữu hạn? | Ví dụ biểu diễn |
| --- | --- | --- | --- |
| `0.5` | `1/2` | Có | `0.1₂` |
| `0.25` | `1/4` | Có | `0.01₂` |
| `0.125` | `1/8` | Có | `0.001₂` |
| `0.375` | `3/8` | Có | `0.011₂` |
| `0.1` | `1/10` | Không | Vô hạn tuần hoàn |
| `0.2` | `1/5` | Không | Vô hạn tuần hoàn |
| `0.3` | `3/10` | Không | Vô hạn tuần hoàn |

Tại sao `0.1` đặc biệt? Vì `1/10 = 1/(2 × 5)`. Mẫu số sau tối giản vẫn chứa **thừa số 5**, không thể biến thành lũy thừa thuần túy của 2. Do đó, không tồn tại **một dãy hữu hạn chữ số nhị phân** biểu diễn đúng `1/10`.

Quy tắc này là kiến thức về hệ cơ số, **không riêng Java**. Tương tự, `1/3` viết thành `0.33333...` vô hạn trong hệ thập phân; `1/10` viết thành một dãy vô hạn trong hệ nhị phân.

## 3. Biểu diễn nhị phân của `0.1` lặp vô hạn ra sao?

Nhân liên tiếp phần lẻ với 2 để lấy từng bit sau dấu chấm:

| Bước | Phần lẻ × 2 | Bit phần nguyên | Phần lẻ còn lại |
| --- | --- | ---: | --- |
| 1 | 0.1 × 2 = 0.2 | 0 | 0.2 |
| 2 | 0.2 × 2 = 0.4 | 0 | 0.4 |
| 3 | 0.4 × 2 = 0.8 | 0 | 0.8 |
| 4 | 0.8 × 2 = 1.6 | 1 | 0.6 |
| 5 | 0.6 × 2 = 1.2 | 1 | 0.2 |
| 6 | 0.2 × 2 = 0.4 | 0 | 0.4 |
| 7 | 0.4 × 2 = 0.8 | 0 | 0.8 |
| 8 | 0.8 × 2 = 1.6 | 1 | 0.6 |

Từ phần dư `0.2`, các bước lại lặp như trước. Vì thế:

```text
0.1₁₀ = 0.000110011001100110011001100110011...₂
        0.00011(0011)(0011)(0011)...₂
```

Đây là dãy **vô hạn**; `float` hoặc `double` không thể cất giữ vô số bit. Máy phải chọn một số nhị phân hữu hạn **gần `0.1` nhất** trong tập giá trị kiểu đó biểu diễn được.

Không nên hiểu rằng Java “cắt bỏ tùy tiện sau vài bit”. Biểu diễn dấu phẩy động sử dụng quy tắc làm tròn xác định; theo chế độ làm tròn mặc định, giá trị có thể biểu diễn gần nhất được chọn với quy tắc xử lý trường hợp hòa (*round to nearest, ties to even*).

## 4. `float` và `double` lưu bit như thế nào?

Trong Java, `float` tương ứng định dạng nhị phân 32 bit và `double` tương ứng định dạng nhị phân 64 bit. Với số hữu hạn chuẩn hóa (*normal*), giá trị được biểu diễn từ dấu, phần trị (*significand*) và số mũ cơ số 2.

| Thành phần | `float` / binary32 | `double` / binary64 |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit trường số mũ | 8 | 11 |
| Bit phần phân số được lưu | 23 | 52 |
| Độ chính xác phần trị (kể cả bit `1` ẩn của số chuẩn hóa) | 24 bit | 53 bit |
| Chữ số thập phân có nghĩa gần đúng | 6–9 | 15–17 |

Lưu ý: con số **53 bit của `double`** không phải “53 chữ số thập phân”. Nó gồm 53 **chữ số nhị phân có nghĩa**. Số mũ quyết định độ lớn; số bit phần trị quyết định khả năng phân biệt các số gần nhau.

`double` có nhiều bit hơn `float`, nên xấp xỉ `0.1` **chính xác hơn**, nhưng **vẫn không bằng đúng `1/10`**.

### Giá trị `double` thực sự được lưu

Đối với literal `0.1` kiểu `double` trong Java:

```text
Dạng hexadecimal floating-point:
0x1.999999999999ap-4

Giá trị chính xác của mẫu bit đó:
3602879701896397 / 36028797018963968

Viết ở thập phân không làm tròn:
0.1000000000000000055511151231257827021181583404541015625
```

Phân số trên **lớn hơn `1/10` một chút**. Đây không phải “sai số xuất hiện vì `println`”; nó là giá trị số thật của mẫu bit đang lưu. Cách hiển thị mặc định thường ngắn hơn để dễ đọc.

Trong khi đó, literal `0.1f` kiểu `float` gần bằng:

```text
0.100000001490116119384765625
```

`float` có ít bit chính xác hơn, nên sai số biểu diễn có thể lớn hơn `double`.

## 5. Vì sao Java vẫn in `0.1` chứ không in cả dãy dài?

```java
double x = 0.1;
System.out.println(x);              // 0.1
System.out.println(Double.toHexString(x)); // 0x1.999999999999ap-4
```

`Double.toString` (được dùng khi `println` chuyển `double` thành chuỗi) chọn cách biểu diễn thập phân ngắn phù hợp với quy tắc định dạng của Java, có thể chuyển ngược về **cùng giá trị `double`**. Chuỗi `"0.1"` đủ để tái tạo mẫu bit của `double` đã làm tròn, nên không cần in toàn bộ chữ số của phân số nhị phân đó khi đổi chính xác sang cơ số 10.

Ba khái niệm phải tách riêng:

1. **Literal trong mã nguồn:** `0.1` là một ký hiệu thập phân hướng đến giá trị toán học một phần mười.
2. **Giá trị dấu phẩy động được lưu:** số nhị phân 64 bit gần nhất với một phần mười, nhưng không bằng đúng.
3. **Chuỗi khi in:** thường là chuỗi ngắn `"0.1"` để con người đọc và để chuyển ngược về đúng cùng mẫu bit.

Muốn nhìn thấy số thập phân đầy đủ tương ứng với giá trị được lưu, có thể dùng `new BigDecimal(x).toPlainString()`. Nhưng hành động này chỉ **phơi bày xấp xỉ nhị phân đã có**, không sửa nó thành số `0.1` toán học.

## 6. Vì sao `0.1 + 0.2 == 0.3` trả về `false`?

```java
System.out.println(0.1 + 0.2);      // 0.30000000000000004
System.out.println(0.3);            // 0.3
System.out.println(0.1 + 0.2 == 0.3); // false
```

Có **hai loại làm tròn** đáng chú ý:

1. Các literal `0.1`, `0.2`, `0.3` đều được chuyển thành các số nhị phân gần giá trị thập phân mong muốn.
2. Phép cộng hai giá trị `double` đã biểu diễn cũng tạo ra một kết quả được làm tròn thành mẫu bit `double` gần nhất.

Kết quả lưu của `0.1 + 0.2` là giá trị `double` **kế cận lớn hơn** giá trị `double` của literal `0.3`. Toán tử `==` so sánh **hai giá trị dấu phẩy động**, không kiểm tra xem hai biểu thức có “bằng nhau theo ý định thập phân” hay không.

Vì vậy không nên nói “Java hiểu rằng `0.1 + 0.2` là một số toán học khác `0.3`”. Phép cộng toán học vẫn chính xác bằng `0.3`; chính **cách xấp xỉ và làm tròn trong tập số biểu diễn được** khiến kết quả máy tính khác.

Một cách diễn đạt hữu ích:

```text
Giá trị lý tưởng: 0.1 + 0.2 = 0.3
Giá trị double:  fl(fl(0.1) + fl(0.2)) ≠ fl(0.3)
```

Trong đó `fl(x)` tượng trưng cho việc làm tròn số toán học thành giá trị `double` có thể biểu diễn.

## 7. Khi nào một số có thể được lưu chính xác?

**Không phải tất cả các số thập phân đều bị sai lệch.** Các phân số có mẫu tối giản là lũy thừa của 2 có thể có biểu diễn nhị phân hữu hạn, ví dụ:

```java
System.out.println(0.5 + 0.25 == 0.75); // true
System.out.println(0.125 + 0.25 == 0.375); // true
```

Các giá trị này đều có biểu diễn nhị phân ngắn và các phép tính ví dụ cũng cho kết quả biểu diễn chính xác. Tuy nhiên, **“biểu diễn nhị phân hữu hạn” chưa đủ đảm bảo mọi giá trị đều được lưu chính xác ở độ chính xác cố định**: nếu cần nhiều bit phần trị hơn 24 hoặc 53, số vẫn có thể làm tròn.

Ví dụ với số nguyên:

```java
long a = 9_007_199_254_740_992L; // 2^53.
long b = 9_007_199_254_740_993L; // 2^53 + 1.

System.out.println((double) a == (double) b); // true
```

Cả hai đều là **số nguyên** và dĩ nhiên có biểu diễn nhị phân hữu hạn, nhưng `double` không phân biệt được chúng tại độ lớn này: `2^53 + 1` làm tròn về `2^53`. Điều này cho thấy câu hỏi “số có viết hữu hạn trong hệ 2 không?” khác với câu hỏi “định dạng 64 bit có **đủ bit chính xác** cho số đó không?”.

**Quy tắc tổng quát:** để được biểu diễn chính xác trong một kiểu dấu phẩy động, số phải vừa có cấu trúc phù hợp với cơ số 2, vừa có phần trị và số mũ nằm trong giới hạn định dạng.

## 8. Tác hại thực tế: so sánh, tích lũy và ranh giới điều kiện

### 8.1. So sánh bằng tuyệt đối với kết quả phép tính

```java
double total = 0.1 + 0.2;

if (total == 0.3) {
    System.out.println("equal");
} else {
    System.out.println("different");
}
// Nhánh "different" được chạy.
```

Dùng `==` là hoàn toàn thích hợp trong những tình huống có **chủ ý so sánh mẫu giá trị** (chẳng hạn so sánh với chính cùng một giá trị đã lưu, hoặc đối chiếu những số nguyên nhỏ chuyển chính xác). Nhưng với các kết quả tính toán dấu phẩy động, `==` thường không thể thay cho khái niệm “gần nhau đủ để coi là bằng nhau” của bài toán.

Một kỹ thuật so sánh gần đúng:

```java
double a = 0.1 + 0.2;
double b = 0.3;
double tolerance = 1e-12;

boolean nearlyEqual = Math.abs(a - b) <= tolerance;
System.out.println(nearlyEqual); // true
```

**Không tồn tại một `tolerance` cố định phù hợp mọi trường hợp.** Ngưỡng phải đến từ đơn vị, miền giá trị và sai số được chấp nhận của bài toán. Với số rất lớn hoặc rất nhỏ, có thể cần ngưỡng tuyệt đối kết hợp ngưỡng tương đối; với `NaN`, vô cực và giá trị gần 0, phải xác định thêm cách xử lý riêng.

Nếu vấn đề yêu cầu so sánh **đúng từng chữ số thập phân** (ví dụ số tiền), việc chỉ tăng `tolerance` không đảm bảo đúng nghiệp vụ. Khi đó nên chọn cách biểu diễn thập phân chính xác từ đầu.

### 8.2. Cộng nhiều lần và sai số tích lũy

```java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total); // 0.9999999999999999
```

Vì mỗi phép cộng có thể làm tròn, lỗi nhỏ có thể tích lũy hoặc triệt tiêu tùy dữ liệu và thứ tự phép tính. Không nên suy luận rằng phép lặp cộng `0.1` mười lần **luôn** bằng chính xác `1.0` dưới kiểu `double`.

Với số lượng lớn giá trị thực trong khoa học hoặc thống kê, có thể cân nhắc thuật toán tổng ổn định hơn (ví dụ compensated summation/Kahan hoặc API tổng phù hợp), nhưng các thuật toán đó **giảm sai số**, không thay đổi sự thật rằng `0.1` không biểu diễn chính xác trong nhị phân hữu hạn.

### 8.3. Giới hạn và điều kiện vòng lặp

```java
double x = 0.0;
while (x < 1.0) {
    x += 0.1;
}
```

Ví dụ này chỉ minh họa bẫy thiết kế: lặp bằng cách cộng bước dấu phẩy động có thể khiến số vòng không như tính toán thập phân lý tưởng. Thường nên **lặp bằng biến đếm nguyên** và tính giá trị tương ứng:

```java
for (int i = 0; i <= 10; i++) {
    double x = i / 10.0;
    // Dùng x trong bài toán của bạn.
}
```

Cách lặp bằng bộ đếm nguyên giúp kiểm soát **số lần lặp**; nó không làm cho mọi `i / 10.0` trở thành thập phân biểu diễn chính xác.

## 9. Muốn tính chính xác theo hệ thập phân: dùng `BigDecimal` đúng cách

Đối với tiền, thuế, lãi suất với quy tắc làm tròn thập phân, hoặc dữ liệu cần đúng các chữ số thập phân, Java cung cấp `java.math.BigDecimal`. Nó biểu diễn giá trị bằng một **số nguyên không chia tỷ lệ (*unscaled value*) và một *scale* thập phân**, nên có thể biểu diễn **chính xác** `0.1`, `0.2` và `0.3` khi chúng được tạo từ đầu dưới dạng thập phân.

### Tạo từ chuỗi thập phân

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal expected = new BigDecimal("0.3");

BigDecimal sum = a.add(b);
System.out.println(sum);                   // 0.3
System.out.println(sum.equals(expected));  // true
```

Đây là phép cộng thập phân chính xác trên những giá trị đầu vào đã chọn. `BigDecimal` là đối tượng **bất biến**; phương thức `add` trả về đối tượng kết quả mới.

### Tránh bẫy `new BigDecimal(0.1)`

```java
BigDecimal fromLiteral = new BigDecimal(0.1);
BigDecimal fromString = new BigDecimal("0.1");

System.out.println(fromLiteral.equals(fromString)); // false
System.out.println(fromLiteral.toPlainString());
// 0.1000000000000000055511151231257827021181583404541015625
```

Constructor `new BigDecimal(double)` lưu **chính xác giá trị `double` đã được làm tròn**, không phải giá trị thập phân “ban đầu trong đầu người viết code”. Vì vậy nó phơi bày dãy số thập phân dài từ biểu diễn nhị phân.

Dùng `new BigDecimal("0.1")` khi đầu vào xác định theo chữ số thập phân. Nếu bắt buộc chuyển một `double` sang `BigDecimal` theo chuỗi thập phân ngắn thể hiện giá trị `double`, dùng `BigDecimal.valueOf(0.1)`:

```java
System.out.println(BigDecimal.valueOf(0.1)); // 0.1
```

Phân biệt: `BigDecimal.valueOf(double)` dựa trên cách chuyển `double` thành chuỗi thập phân. Nó **không thể khôi phục thông tin đầu vào hoặc sai số đã mất** nếu bạn thực hiện phép tính `double` trước khi chuyển:

```java
System.out.println(BigDecimal.valueOf(0.1 + 0.2)); // 0.30000000000000004
```

Do đó, nếu muốn tính toán thập phân chính xác, hãy tạo `BigDecimal` **trước khi tính**, thường từ chuỗi đã được kiểm tra hoặc từ số nguyên với scale rõ ràng.

### `BigDecimal` cũng có quy tắc riêng

- `BigDecimal("0.1").add(BigDecimal("0.2"))` theo cách viết minh họa không phải cú pháp Java hợp lệ: phải dùng `new BigDecimal("0.1")` cho từng toán hạng.
- Không dùng toán tử `+`, `-`, `*` trên các đối tượng `BigDecimal`; dùng `add`, `subtract`, `multiply`.
- Chia thập phân có thể tạo khai triển vô hạn: ví dụ `BigDecimal.ONE.divide(new BigDecimal("3"))` có thể ném `ArithmeticException` nếu không chỉ định ngữ cảnh làm tròn. Khi cần, sử dụng `MathContext` hoặc `divide(divisor, scale, RoundingMode)`.
- `equals` trên `BigDecimal` xét cả **giá trị và scale**: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false`; `compareTo` cho kết quả `0` nếu chỉ cần so sánh giá trị số.

**Lưu ý thiết kế:** `BigDecimal` phù hợp với thập phân chính xác nhưng có chi phí đối tượng/tính toán và cần quy tắc scale/rounding rõ ràng. Với ứng dụng nhúng, tài chính hoặc tiền tệ cố định hai chữ số, **số nguyên theo đơn vị nhỏ nhất** (ví dụ cents) cũng là lựa chọn tốt nếu đã quy định miền giá trị và tránh tràn số.

## 10. Đừng nhầm với những vấn đề khác của `float`/`double`

Số thực dấu phẩy động còn có `NaN`, `Infinity`, `-Infinity`, số dưới chuẩn (*subnormal*), và hai mẫu bit `+0.0`/`-0.0`. Các khái niệm này quan trọng nhưng **không phải nguyên nhân trực tiếp** của hiện tượng `0.1 + 0.2`.

Đối với lỗi biểu diễn `0.1`, hãy ghi nhớ sự khác nhau giữa:

- **Sai số biểu diễn:** literal `0.1` không có biểu diễn nhị phân hữu hạn, nên kiểu dấu phẩy động lưu giá trị gần nhất.
- **Sai số làm tròn phép toán:** khi cộng, nhân, chia, kết quả toán học cũng có thể không biểu diễn được và cần làm tròn.
- **Sai số chuyển kiểu:** khi chuyển `double → float` hoặc số nguyên lớn sang kiểu dấu phẩy động, có thể mất thêm độ chính xác.
- **Quy tắc in ra chuỗi:** `println` thường chọn chuỗi ngắn có thể chuyển ngược đúng, không phải in toàn bộ giá trị thập phân chính xác của các bit.

Việc đổi `float` sang `double` **sau khi giá trị đã được làm tròn** cũng không tự khôi phục giá trị gốc:

```java
float a = 0.1f;
double promoted = a;
System.out.println(promoted); // 0.10000000149011612
```

Chuyển đổi mở rộng giữ lại **giá trị `float` thực sự đã lưu**. Nó không thay thế bằng giá trị `double` gần `0.1` nhất.

## 11. Ví dụ Java 17+ đầy đủ, chạy được

Tạo tệp **`WhyPointOneIsApproximate.java`**:

```java
import java.math.BigDecimal;

public class WhyPointOneIsApproximate {
    public static void main(String[] args) {
        double oneTenth = 0.1;
        double sum = 0.1 + 0.2;

        System.out.println("0.1 printed = " + oneTenth);
        System.out.println("0.1 hex = " + Double.toHexString(oneTenth));
        System.out.println("0.1 exact decimal = "
                + new BigDecimal(oneTenth).toPlainString());
        System.out.println("0.1 + 0.2 = " + sum);
        System.out.println("sum == 0.3 = " + (sum == 0.3));
        System.out.println("near 0.3 = "
                + (Math.abs(sum - 0.3) < 1e-12));

        float asFloat = 0.1f;
        double expandedFloat = asFloat;
        System.out.println("float 0.1 widened = " + expandedFloat);

        double total = 0.0;
        for (int i = 0; i < 10; i++) {
            total += 0.1;
        }
        System.out.println("0.1 added ten times = " + total);

        BigDecimal decimalSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("BigDecimal sum = " + decimalSum);
        System.out.println("BigDecimal.valueOf(0.1) = "
                + BigDecimal.valueOf(0.1));
        System.out.println("BigDecimal.valueOf(double sum) = "
                + BigDecimal.valueOf(sum));

        System.out.println("exact binary double equals decimal BigDecimal = "
                + new BigDecimal(0.1).equals(new BigDecimal("0.1")));

        System.out.println("1.0 equals 1.00 with BigDecimal = "
                + new BigDecimal("1.0").equals(new BigDecimal("1.00")));
        System.out.println("1.0 compares numerically to 1.00 = "
                + new BigDecimal("1.0").compareTo(new BigDecimal("1.00")));
    }
}
```

Biên dịch và chạy:

```bash
javac WhyPointOneIsApproximate.java
java WhyPointOneIsApproximate
```

Kết quả:

```text
0.1 printed = 0.1
0.1 hex = 0x1.999999999999ap-4
0.1 exact decimal = 0.1000000000000000055511151231257827021181583404541015625
0.1 + 0.2 = 0.30000000000000004
sum == 0.3 = false
near 0.3 = true
float 0.1 widened = 0.10000000149011612
0.1 added ten times = 0.9999999999999999
BigDecimal sum = 0.3
BigDecimal.valueOf(0.1) = 0.1
BigDecimal.valueOf(double sum) = 0.30000000000000004
exact binary double equals decimal BigDecimal = false
1.0 equals 1.00 with BigDecimal = false
1.0 compares numerically to 1.00 = 0
```

Ví dụ trên kiểm tra ba tầng khác nhau: **giá trị lưu**, **hành vi phép toán** và **cách biểu diễn kết quả thành chuỗi**. Đặc biệt, `new BigDecimal(0.1)` và `new BigDecimal("0.1")` khác nhau vì chúng được tạo từ **hai giá trị đầu vào khác bản chất**.

## 12. Lỗi phổ biến và cách suy nghĩ đúng

| Quan niệm chưa đúng | Giải thích đúng |
| --- | --- |
| “Máy lưu số 0.1 chính xác, chỉ có phép cộng gây lỗi.” | Literal `0.1` đã được làm tròn khi chuyển thành `float`/`double`. |
| “`println(0.1)` ra 0.1 nên giá trị lưu phải đúng một phần mười.” | Chuỗi hiển thị ngắn không phải bản khai triển thập phân đầy đủ của các bit. |
| “`double` có 64 bit nên chứa chính xác mọi phân số thập phân.” | Số bit là hữu hạn và dùng cơ số 2; `1/10` có khai triển nhị phân vô hạn. |
| “Tăng từ `float` sang `double` sẽ xóa mọi sai số.” | `double` có xấp xỉ tốt hơn; dữ liệu `float` đã làm tròn không tự trở lại giá trị gốc. |
| “Số thập phân nào cũng không biểu diễn chính xác.” | `0.5`, `0.25`, `0.125` có biểu diễn nhị phân hữu hạn và thường được lưu chính xác khi đủ miền/độ chính xác. |
| “`0.1 + 0.2 == 0.3` luôn phải true vì toán học đúng.” | Biểu thức Java thực hiện phép tính trên các giá trị `double` xấp xỉ rồi so sánh. |
| “Dùng `new BigDecimal(0.1)` sẽ có số thập phân chính xác 0.1.” | Nó giữ chính xác giá trị `double` đã xấp xỉ; dùng chuỗi `"0.1"` cho dữ liệu thập phân. |
| “Cứ tăng epsilon thật lớn thì mọi phép so sánh sẽ đúng.” | Ngưỡng lớn có thể coi các giá trị thực sự khác nhau là bằng nhau; phải dựa trên độ chính xác nghiệp vụ. |
| “`BigDecimal` không cần nghĩ đến làm tròn.” | Các phép chia thập phân vô hạn vẫn cần lựa chọn `MathContext`/`RoundingMode`. |

**Ghi nhớ:** `0.1 = 1/10` không có biểu diễn nhị phân hữu hạn vì mẫu tối giản chứa thừa số 5. `float` và `double` chỉ lưu được **xấp xỉ gần nhất** trong số bit hữu hạn; phép tính tiếp theo có thể làm tròn lần nữa. Dùng `double` khi bài toán chấp nhận sai số số thực và quản lý so sánh phù hợp; dùng `BigDecimal` **tạo từ chuỗi hoặc dữ liệu thập phân được kiểm soát** khi phải giữ giá trị thập phân chính xác.

**Tài liệu tham khảo:** [Java Language Specification §4.2.3 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3); [JLS §15.4 — FP-strict Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.4); [Java SE 21 Double API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html); [Java SE 21 BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-005">Phạm vi số nguyên được float hoặc double biểu diễn liên tiếp chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi số nguyên được `float` và `double` biểu diễn liên tiếp chính xác trong Java

## 1. “Biểu diễn liên tiếp chính xác” nghĩa là gì?

Một kiểu dấu phẩy động **biểu diễn chính xác** số nguyên `n` khi có một giá trị thuộc kiểu đó bằng đúng `n`, không cần làm tròn sang số khác. Nhưng việc một số nguyên riêng lẻ biểu diễn được không có nghĩa **mọi số nguyên liền kề** cũng biểu diễn được.

Ta gọi `[-M, M]` là một **khoảng số nguyên liên tiếp biểu diễn chính xác** khi mọi số nguyên `n` thỏa `-M <= n <= M` đều có giá trị `float` hoặc `double` bằng đúng `n`. Với hai kiểu Java:

| Kiểu | Số bit chính xác của phần trị (precision) | Khoảng số nguyên liên tiếp chính xác, bao gồm hai đầu mút | Mốc dương dạng thập phân |
| --- | ---: | --- | ---: |
| `float` | 24 bit | `[-2^24, 2^24]` | `16_777_216` |
| `double` | 53 bit | `[-2^53, 2^53]` | `9_007_199_254_740_992` |

**Ngay sau** mốc dương, `2^24 + 1` không biểu diễn chính xác bằng `float` và `2^53 + 1` không biểu diễn chính xác bằng `double`. Tuy nhiên, một số nguyên xa hơn **vẫn có thể** biểu diễn chính xác, ví dụ `2^24 + 2` dưới dạng `float` và `2^53 + 2` dưới dạng `double`.

Từ “liên tiếp” rất quan trọng: không nên viết “`float` chỉ biểu diễn được số nguyên tối đa `2^24`” hoặc “`double` chỉ biểu diễn được số nguyên tối đa `2^53`”. Các câu đó sai: các kiểu dấu phẩy động còn biểu diễn được nhiều số nguyên có độ lớn lớn hơn, nhưng sẽ **bỏ qua một số số nguyên ở giữa**.

Một ví dụ sát biên:

```java
int a = 16_777_216;       // 2^24
int b = 16_777_217;       // 2^24 + 1
int c = 16_777_218;       // 2^24 + 2

float fa = a;             // chính xác
float fb = b;             // làm tròn thành 16_777_216f
float fc = c;             // chính xác
```

Vì vậy `fa == fb` là `true` dù `a != b`. Đây là lỗi tiềm ẩn khi dùng `float` làm khóa định danh, đếm số nguyên lớn hoặc so sánh hai kết quả vốn khác nhau đúng một đơn vị.

## 2. Vì sao mốc là `2^24` và `2^53`?

`float` và `double` của Java theo định dạng IEEE 754 binary32/binary64. Một số hữu hạn chuẩn hóa có thể được diễn giải về mặt toán học dưới dạng:

\[
(-1)^{sign} \times (1.fraction)_2 \times 2^{exponent}
\]

Trong đó phần trị hữu hiệu bao gồm bit `1` đầu ẩn (*implicit leading bit*) của số chuẩn hóa:

| Đặc tính | `float` (binary32) | `double` (binary64) |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Trường số mũ | 8 | 11 |
| Trường fraction lưu thực tế | 23 | 52 |
| **Độ chính xác phần trị hiệu dụng `p`** | **24** | **53** |

Trong khoảng các số dương từ `2^e` (bao gồm) đến `2^(e+1)` (không bao gồm), khoảng cách giữa hai số dấu phẩy động chuẩn hóa liền kề là:

\[
ULP = 2^{e-(p-1)}
\]

`ULP` (*unit in the last place*) chỉ khoảng cách ở mức độ chính xác đó. Với các số nguyên:

- Khi `ULP <= 1`, lưới biểu diễn đủ mịn để chứa **từng số nguyên**.
- Tại `2^p`, khoảng cách tới giá trị tiếp theo phía dương tăng thành `2`: số `2^p + 1` nằm giữa hai giá trị biểu diễn được.
- Sau những mốc cao hơn, khoảng cách có thể thành `4`, `8`, `16`, ...; các số có phần bit thấp không phù hợp với bước nhảy sẽ không còn biểu diễn được chính xác.

Với `float`, `p=24`: trong `[2^23, 2^24)` bước là `1`, còn tại và ngay phía trên `2^24` bước là `2`. Với `double`, `p=53`: trong `[2^52, 2^53)` bước là `1`, còn tại và phía trên `2^53` bước là `2`.

**Lưu ý tại đúng biên:** `2^p` vẫn được biểu diễn chính xác, và số nguyên ngay trước nó (`2^p - 1`) vẫn chính xác. Bước nhảy về bên trái và bên phải của một lũy thừa hai có thể khác nhau. Do đó miền liên tiếp chính xác có đầu mút **bao gồm** `±2^p`; tuy nhiên `2^p` không còn có hàng xóm lớn hơn đúng `1` ở dạng dấu phẩy động cùng kiểu.

## 3. Quan sát trực tiếp các khoảng trống sau mốc

### 3.1. `float`: biên `2^24`

| Số nguyên gốc | Giá trị `float` biểu diễn (dưới dạng số nguyên) | Chính xác? |
| --- | ---: | --- |
| `16_777_215 = 2^24 - 1` | `16_777_215` | Có |
| `16_777_216 = 2^24` | `16_777_216` | Có |
| `16_777_217 = 2^24 + 1` | `16_777_216` | Không |
| `16_777_218 = 2^24 + 2` | `16_777_218` | Có |
| `16_777_219 = 2^24 + 3` | `16_777_220` | Không |
| `16_777_220 = 2^24 + 4` | `16_777_220` | Có |

Ở khu vực ngay phía trên `2^24`, chỉ các số nguyên **chẵn** được biểu diễn chính xác. Giá trị lẻ nằm giữa hai số chẵn biểu diễn được sẽ được làm tròn khi chuyển sang `float`.

Quy tắc mặc định của chuyển đổi số nguyên sang dấu phẩy động Java là làm tròn tới giá trị gần nhất; khi đúng giữa hai giá trị, chọn kết quả có bit cuối phần trị là **chẵn** (*round to nearest, ties to even*). Bởi vậy:

- `16_777_217` nằm giữa `16_777_216` và `16_777_218`, nên về `16_777_216`.
- `16_777_219` nằm giữa `16_777_218` và `16_777_220`, nên về `16_777_220`.

Về phía âm có hiện tượng đối xứng: `-16_777_217` làm tròn thành `-16_777_216` dưới dạng `float`. Không nên áp dụng suy luận “số âm thì luôn làm tròn xuống”; quy tắc làm tròn ở đây dựa vào số gần nhất trong hệ nhị phân.

### 3.2. `double`: biên `2^53`

| Số nguyên gốc | Giá trị `double` biểu diễn (dưới dạng số nguyên) | Chính xác? |
| --- | ---: | --- |
| `9_007_199_254_740_991 = 2^53 - 1` | `9_007_199_254_740_991` | Có |
| `9_007_199_254_740_992 = 2^53` | `9_007_199_254_740_992` | Có |
| `9_007_199_254_740_993 = 2^53 + 1` | `9_007_199_254_740_992` | Không |
| `9_007_199_254_740_994 = 2^53 + 2` | `9_007_199_254_740_994` | Có |
| `9_007_199_254_740_995 = 2^53 + 3` | `9_007_199_254_740_996` | Không |

Ví dụ:

```java
long a = 9_007_199_254_740_992L;
long b = 9_007_199_254_740_993L;

double da = a; // 9_007_199_254_740_992
double db = b; // cũng là 9_007_199_254_740_992
```

Ở đây **hai `long` khác nhau có thể trở thành cùng một `double`**. Việc chuyển đổi Java vẫn hợp lệ và không ném lỗi, vì đây là một phép chuyển kiểu số nguyên → dấu phẩy động được ngôn ngữ cho phép.

Một số thư viện hoặc giao thức gọi `2^53 - 1` là *largest safe integer* vì yêu cầu phép cộng/trừ `1` và các số lân cận còn phân biệt được. Trong bài này, ta đang xét **tập các số nguyên biểu diễn đúng**, do đó `2^53` bản thân nó vẫn đúng và là đầu mút của khoảng liên tiếp; cách dùng từ “safe” có thể đặt điều kiện chặt hơn cho các phép tính.

## 4. Phạm vi liên tiếp khác với giá trị lớn nhất của kiểu

Không được nhầm ngưỡng `2^24`/`2^53` với `Float.MAX_VALUE`/`Double.MAX_VALUE`:

- `Float.MAX_VALUE` xấp xỉ `3.4028235 × 10^38`, rất lớn so với `16_777_216`.
- `Double.MAX_VALUE` xấp xỉ `1.7976931348623157 × 10^308`, rất lớn so với `9_007_199_254_740_992`.

Các giá trị `MAX_VALUE` này mô tả **cận trên số hữu hạn**, không mô tả miền số nguyên mà mọi giá trị liên tiếp đều có thể biểu diễn. Càng xa 0, khoảng cách giữa các giá trị dấu phẩy động thường càng lớn.

Ví dụ `Long.MIN_VALUE = -2^63` **biểu diễn chính xác** bằng `double` vì nó là một lũy thừa của 2, dù có độ lớn lớn hơn `2^53`. Trái lại, `Long.MAX_VALUE = 2^63 - 1` **không chính xác** khi chuyển sang `double` và sẽ được làm tròn thành `2^63`, một giá trị không nằm trong miền `long`.

Tương tự, `Integer.MAX_VALUE = 2^31 - 1` khi chuyển sang `float` được làm tròn thành `2^31`. Điều đáng chú ý là kết quả `float` hoàn toàn hữu hạn; việc nó hữu hạn không chứng tỏ giá trị nguyên ban đầu được bảo toàn.

## 5. Liên hệ với các kiểu số nguyên của Java

| Chuyển kiểu | Có đảm bảo chính xác cho **mọi** giá trị nguồn không? | Lý do |
| --- | --- | --- |
| `byte` → `float` | Có | Toàn bộ miền 8 bit nằm trong `±2^24` |
| `short` → `float` | Có | Toàn bộ miền 16 bit nằm trong `±2^24` |
| `char` → `float` | Có | Các giá trị `char` từ 0 đến 65535 nằm trong miền chính xác |
| `int` → `float` | **Không** | Nhiều `int` có độ lớn lớn hơn `2^24` |
| `int` → `double` | **Có** | Toàn bộ miền `int` nằm trong `±2^53` |
| `long` → `float` | **Không** | Phần trị `float` chỉ có 24 bit chính xác |
| `long` → `double` | **Không** | Nhiều `long` có độ lớn lớn hơn `2^53` |

Ngay cả chuyển kiểu **mở rộng** (*widening primitive conversion*) như `int` → `float` hoặc `long` → `double` vẫn có thể làm mất thông tin phần bit thấp. “Mở rộng” nói về quy tắc phân loại chuyển kiểu Java, không phải cam kết giữ mọi chữ số chính xác.

`float` → `double` thì giữ đúng **giá trị `float` đã có**, nhưng không thể phục hồi số nguyên gốc nếu nó đã bị làm tròn trước đó:

```java
int original = 16_777_217;
float rounded = original;       // 16_777_216
double widened = rounded;       // vẫn chính xác 16_777_216, không khôi phục 16_777_217
```

Một `double` biểu diễn mọi `int` chính xác, nhưng điều này không có nghĩa *mọi phép toán* giữa các giá trị `int` chuyển sang `double` đều cho kết quả đúng như số nguyên toán học. Kết quả của phép tính có thể vượt `2^53` hoặc có phần lẻ không biểu diễn chính xác.

## 6. Dùng `Math.ulp`, `Math.nextUp` và `Math.nextDown` để kiểm tra khoảng cách

Java có các hàm trong `Math` hữu ích để quan sát “lưới” giá trị:

- `Math.ulp(x)` trả về độ lớn của một ULP tại `x` (có overload `float` và `double`).
- `Math.nextUp(x)` trả về giá trị dấu phẩy động biểu diễn được liền sau theo hướng tăng.
- `Math.nextDown(x)` trả về giá trị biểu diễn được liền trước theo hướng giảm.

Ở mốc `float`:

```java
float edge = 16_777_216f;
float up = Math.nextUp(edge);     // 16_777_218f
float down = Math.nextDown(edge); // 16_777_215f
float step = Math.ulp(edge);      // 2.0f
```

Ở mốc `double`:

```java
double edge = 9_007_199_254_740_992d;
double up = Math.nextUp(edge);     // 9_007_199_254_740_994d
double down = Math.nextDown(edge); // 9_007_199_254_740_991d
double step = Math.ulp(edge);      // 2.0d
```

Một chi tiết quan trọng: **`Math.ulp(edge)` bằng 2 không có nghĩa giá trị phía dưới cách `edge` hai đơn vị**. Biên là nơi bước nhảy tăng: `nextDown(edge)` cách `1`, còn `nextUp(edge)` cách `2`.

`Math.ulp` chỉ đo khoảng cách cục bộ, không phải một phép kiểm tra chuyển đổi nguyên gốc có giữ đủ giá trị hay không. Muốn đánh giá độ chính xác của một giá trị nguồn cụ thể, còn phải so sánh với nguồn theo cách không tự làm tròn nguồn trong quá trình kiểm tra.

## 7. Ví dụ Java 17 đầy đủ, có thể chạy trực tiếp

Tạo tệp `ExactIntegerRangeDemo.java`:

```java
public class ExactIntegerRangeDemo {
    // Dùng long trung gian để kiểm tra float -> int mà không bão hòa ở Integer.MAX_VALUE.
    private static boolean exactAsFloat(int original) {
        float encoded = original;
        return (long) encoded == (long) original;
    }

    // Double của Long.MAX_VALUE làm tròn thành 2^63: phải chặn biên này.
    private static boolean exactAsDouble(long original) {
        double encoded = original;
        return encoded < 0x1.0p63 && (long) encoded == original;
    }

    public static void main(String[] args) {
        int floatEdge = 1 << 24;
        long doubleEdge = 1L << 53;

        System.out.println("float edge = " + floatEdge);
        System.out.println("float edge+1 stored = "
                + (long) (float) (floatEdge + 1));
        System.out.println("float edge+2 stored = "
                + (long) (float) (floatEdge + 2));
        System.out.println("float edge+1 exact = "
                + exactAsFloat(floatEdge + 1));
        System.out.println("float edge+2 exact = "
                + exactAsFloat(floatEdge + 2));
        System.out.println("float ulp at edge = " + Math.ulp((float) floatEdge));

        System.out.println("double edge = " + doubleEdge);
        System.out.println("double edge+1 stored = "
                + (long) (double) (doubleEdge + 1));
        System.out.println("double edge+2 stored = "
                + (long) (double) (doubleEdge + 2));
        System.out.println("double edge+1 exact = "
                + exactAsDouble(doubleEdge + 1));
        System.out.println("double edge+2 exact = "
                + exactAsDouble(doubleEdge + 2));
        System.out.println("double ulp at edge = "
                + Math.ulp((double) doubleEdge));

        System.out.println("Integer.MAX_VALUE exact as float = "
                + exactAsFloat(Integer.MAX_VALUE));
        System.out.println("Long.MAX_VALUE exact as double = "
                + exactAsDouble(Long.MAX_VALUE));
        System.out.println("Long.MIN_VALUE exact as double = "
                + exactAsDouble(Long.MIN_VALUE));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac ExactIntegerRangeDemo.java
java ExactIntegerRangeDemo
```

Đầu ra dự kiến:

```text
float edge = 16777216
float edge+1 stored = 16777216
float edge+2 stored = 16777218
float edge+1 exact = false
float edge+2 exact = true
float ulp at edge = 2.0
double edge = 9007199254740992
double edge+1 stored = 9007199254740992
double edge+2 stored = 9007199254740994
double edge+1 exact = false
double edge+2 exact = true
double ulp at edge = 2.0
Integer.MAX_VALUE exact as float = false
Long.MAX_VALUE exact as double = false
Long.MIN_VALUE exact as double = true
```

Hai hàm kiểm tra trên phục vụ **đúng kiểu nguồn được ghi trong chữ ký**: một `int` sang `float`, và một `long` sang `double`. Chúng không phải thư viện kiểm tra tùy ý mọi số thực hay số nguyên vô hạn. `0x1.0p63` là literal `double` biểu diễn đúng `2^63`; `Long.MAX_VALUE` nếu đã được đổi sang `double` thì có thể thành chính giá trị đó, nên cần phép kiểm tra cận trên trước khi cast lại về `long`.

Ở trường hợp `int` → `float`, ép ngược sang `long` làm bộ kiểm tra tránh sai sót do bão hòa của chuyển `float` → `int` tại `Integer.MAX_VALUE`. `float` nhận từ một `int` luôn là số hữu hạn có độ lớn nằm trong miền `long`, nên bước ép sang `long` này đủ phạm vi để so sánh với `int` gốc.

## 8. Các bẫy khi kiểm tra dữ liệu có mất độ chính xác hay không

### 8.1. So sánh hỗn hợp có thể tự chuyển nguồn thành `float`/`double`

```java
int n = 16_777_217;
float f = n;

System.out.println(n == f); // true, dù f chứa 16_777_216
```

Java áp dụng *binary numeric promotion*: khi so sánh `int` với `float`, `n` cũng được chuyển sang `float`, nên **cả hai bên đều đã bị làm tròn** và phép so sánh báo bằng nhau.

Tương tự:

```java
long n = 9_007_199_254_740_993L;
double d = n;

System.out.println(n == d); // true, dù d chứa 9_007_199_254_740_992
```

Do đó `original == (float) original` hoặc `original == (double) original` **không phải** phép thử đảm bảo không mất thông tin. Hãy giữ và so sánh nguồn ở kiểu nguyên thích hợp, đồng thời tránh bẫy bão hòa lúc ép ngược.

### 8.2. Ép ngược có thể bão hòa và gây kết quả “đúng giả”

Một kiểm tra tưởng hợp lý là `(int) (float) n == n`. Nhưng với `n = Integer.MAX_VALUE`, giá trị `float` đã làm tròn thành `2^31` (ngoài miền `int`), và cast về `int` bão hòa thành `Integer.MAX_VALUE`; so sánh có thể trả `true` dù giá trị trung gian sai.

Tương tự, `Long.MAX_VALUE` chuyển sang `double` thành `2^63`, rồi cast về `long` lại bão hòa thành `Long.MAX_VALUE`. Để kiểm tra chính xác, cần loại bỏ các giá trị trung gian ở hoặc ngoài biên kiểu đích trước khi cast, hoặc dùng kiểu nguyên trung gian lớn hơn nếu biết miền nguồn đủ nhỏ, như hàm ở mục 7.

### 8.3. Số thập phân chính xác là một vấn đề khác

`0.1` là số thực có biểu diễn thập phân hữu hạn nhưng không có biểu diễn nhị phân hữu hạn; vì vậy `0.1d` không chính xác tuyệt đối theo toán học. Mốc `2^53` **chỉ nói về số nguyên liên tiếp**, không nói rằng mọi số thực trong khoảng `[-2^53, 2^53]` đều biểu diễn đúng.

Với số tiền hoặc đầu vào decimal cần bảo toàn chữ số, cân nhắc dùng `BigDecimal` được tạo từ chuỗi:

```java
java.math.BigDecimal price = new java.math.BigDecimal("0.1");
```

Nếu cần một giá trị nguyên lớn với phạm vi vượt `long`, dùng `BigInteger`. Không thể khôi phục phần giá trị đã bị làm tròn nếu chỉ còn dữ liệu `float`/`double`.

### 8.4. Các phép tính ở sát ngưỡng có thể dừng tăng từng đơn vị

Ví dụ:

```java
float count = 16_777_216f;
count = count + 1f; // vẫn 16_777_216f
```

Vì `16_777_217` ở đúng giữa hai giá trị `float` đại diện được, kết quả làm tròn về `16_777_216f` (*ties to even*). Phép tính không gây exception. Tương tự, cộng `1d` vào `9_007_199_254_740_992d` vẫn trả lại giá trị ban đầu.

Không nên dùng `float` hoặc `double` làm bộ đếm cho những thao tác đòi hỏi số nguyên tăng chính xác từng 1 trên phạm vi lớn; chọn `int`, `long`, hoặc `BigInteger` theo phạm vi cần dùng và kiểm tra tràn số nguyên khi phù hợp.

## 9. Quy tắc lựa chọn kiểu cho từng bài toán

| Tình huống | Cách xử lý |
| --- | --- |
| Lưu một giá trị `int` bất kỳ để xử lý dưới dạng dấu phẩy động | `double` bảo toàn giá trị nguyên của mọi `int` |
| Lưu số đếm chắc chắn trong `[-16_777_216, 16_777_216]` bằng `float` | Mỗi số nguyên trong khoảng đều chính xác, nhưng cần xét độ chính xác của kết quả các phép tính sau đó |
| Chuyển `int` ngoài phạm vi trên sang `float` | Không mặc định chính xác; kiểm tra giá trị cụ thể hoặc giữ kiểu nguyên |
| Chuyển `long` sang `double` | Mọi giá trị trong `[-2^53, 2^53]` đều chính xác; ngoài vùng chỉ một phần chính xác |
| Lưu ID, mã giao dịch, khóa hoặc số đếm lớn | Dùng kiểu nguyên (`long`, `BigInteger`) thay vì `float`/`double` nếu cần phân biệt mọi giá trị |
| Làm việc với tiền / decimal yêu cầu chữ số thập phân chính xác | Dùng `BigDecimal` với nguồn từ chuỗi decimal và quy tắc làm tròn rõ ràng |
| Quan sát lưới biểu diễn quanh một giá trị | Dùng `Math.ulp`, `Math.nextUp`, `Math.nextDown`; chúng không tự xác thực nguồn có mất thông tin hay không |

Các giá trị vượt ngưỡng không phải tất cả đều sai: chẳng hạn `2^60` biểu diễn chính xác bằng `double` vì là lũy thừa của 2. Câu hỏi quan trọng là: **“Kiểu này có biểu diễn chính xác tất cả số nguyên trong khoảng tôi cần hay chỉ một số giá trị rời rạc?”**

## 10. Câu hỏi tự kiểm tra

1. `float` có biểu diễn chính xác `16_777_216` không? `16_777_217` thì sao?
2. Vì sao `16_777_218` vẫn chính xác bằng `float` dù nằm ngoài khoảng liên tiếp?
3. Một `double` có biểu diễn chính xác mọi giá trị `int` không? Có biểu diễn chính xác mọi `long` không?
4. `Long.MIN_VALUE` là một số nguyên rất lớn về độ lớn. Vì sao vẫn chính xác bằng `double`?
5. Vì sao `long n = 9_007_199_254_740_993L; double d = n; n == d` có thể là `true`?
6. Có thể dùng `(int)(float)n == n` để chắc chắn phép chuyển `int` → `float` chính xác không?
7. Tại `2^24f`, `Math.nextDown` và `Math.nextUp` cách giá trị đó bao nhiêu?

**Đáp án:**

1. `16_777_216` có; `16_777_217` không (làm tròn về `16_777_216`).
2. Bước nhảy phía trên mốc là `2`, nên các số chẵn gần mốc vẫn nằm trên lưới biểu diễn.
3. Có với mọi `int`; không với mọi `long`, vì `double` chỉ có 53 bit phần trị chính xác.
4. `-2^63` là một lũy thừa của 2 và được biểu diễn chính xác dù nằm ngoài khoảng liên tiếp.
5. Khi so sánh `long` với `double`, toán hạng `long` cũng bị chuyển sang `double`, làm hai bên trùng nhau sau khi mất thông tin.
6. Không; ép `float` đã làm tròn về lại `int` có thể bão hòa ở `Integer.MAX_VALUE`, tạo kết quả đúng giả.
7. `nextDown(2^24f) = 2^24 - 1`, cách `1`; `nextUp(2^24f) = 2^24 + 2`, cách `2`.

**Ghi nhớ:** `float` bảo toàn *mọi* số nguyên liên tiếp đến `±2^24`, `double` đến `±2^53` (đều bao gồm đầu mút). Ngoài miền này vẫn có nhiều số nguyên chính xác nhưng không còn **mọi** số nguyên liền kề. Độ lớn có thể biểu diễn, độ chính xác phần trị và độ chính xác của phép tính là ba thuộc tính cần phân biệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-006">Normal, subnormal và gradual underflow</a>

<details>
<summary>Xem chi tiết</summary>

# Normal, subnormal và gradual underflow trong Java

## 1. Mục tiêu của bài học

Trong Java, **float** và **double** biểu diễn số dấu phẩy động nhị phân với độ rộng cố định. Khi một số rất gần 0, cách biểu diễn của nó có thể thay đổi từ **normal** (số chuẩn hóa) sang **subnormal** (số dưới chuẩn), rồi cuối cùng thành **+0.0** hoặc **-0.0** nếu kết quả nhỏ tới mức không còn số khác 0 biểu diễn được.

**Gradual underflow** là cơ chế cho phép các kết quả nhỏ tiếp tục tồn tại dưới dạng subnormal thay vì lập tức bị thay bằng 0 tại ngưỡng nhỏ nhất của số normal. Đây là một phần quan trọng của ngữ nghĩa dấu phẩy động trong Java.

Sau bài này, bạn cần có khả năng:

- Nhận biết normal, subnormal, zero, infinity và NaN từ các trường bit của float/double.
- Giải thích **hidden leading bit** là 1 ở số normal và 0 ở số subnormal.
- Phân biệt **Float.MIN_NORMAL** với **Float.MIN_VALUE**, và tương tự ở double.
- Tính ngưỡng nhỏ nhất, khoảng cách giữa các số subnormal, cũng như thời điểm làm tròn thành 0.
- Hiểu gradual underflow giữ được gì, mất độ chính xác tương đối ở đâu và tại sao kết quả Java không tự động “flush to zero”.
- Đọc mẫu bit và tái hiện các tình huống bằng Java 17+.

## 2. Cấu trúc IEEE 754 của float và double

Java dùng hai định dạng nhị phân thông dụng:

| Thuộc tính | **float** (binary32) | **double** (binary64) |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit số mũ lưu trữ | 8 | 11 |
| Bit phân số lưu trữ | 23 | 52 |
| Độ chính xác của significand ở miền normal | 24 bit | 53 bit |
| Độ lệch số mũ (bias) | 127 | 1023 |
| Số mũ nhỏ nhất của normal | −126 | −1022 |
| Số mũ lớn nhất của normal | +127 | +1023 |

Một mẫu bit float có bố cục:

~~~text
float (32 bit)
+---+----------+-------------------------+
| s | exponent |         fraction        |
| 1 |  8 bit   |          23 bit         |
+---+----------+-------------------------+
~~~

Một mẫu bit double:

~~~text
double (64 bit)
+---+-------------+----------------------------------------------+
| s |  exponent   |                   fraction                   |
| 1 |   11 bit    |                     52 bit                   |
+---+-------------+----------------------------------------------+
~~~

Gọi **s** là bit dấu, **E** là số nguyên không dấu lưu trong trường exponent, và **F** là số nguyên không dấu lưu trong trường fraction. Dấu của giá trị do **(−1)^s** quyết định. **E** là trường bit được mã hóa và không phải luôn là số mũ thực tế.

Những trường hợp đặc biệt phụ thuộc vào mẫu của exponent:

| Trường exponent | Fraction | Loại giá trị |
| --- | --- | --- |
| Khác 0 và chưa phải toàn bit 1 | Bất kỳ | **Normal** |
| Toàn bit 0 | Khác 0 | **Subnormal** |
| Toàn bit 0 | Bằng 0 | **+0.0** hoặc **−0.0** |
| Toàn bit 1 | Bằng 0 | **+Infinity** hoặc **−Infinity** |
| Toàn bit 1 | Khác 0 | **NaN** |

Đừng nhầm **subnormal** với NaN, infinity hoặc zero. Subnormal là **số hữu hạn khác 0**, có thể tham gia cộng, trừ, nhân, chia thông thường. Dấu âm không thay đổi quy tắc phân loại.

## 3. Normal: số chuẩn hóa và bit 1 ẩn

Đối với float, khi **1 ≤ E ≤ 254**, số normal có giá trị:

\[
(-1)^s \times \left(1+\frac{F}{2^{23}}\right)\times 2^{E-127}
\]

Đối với double, khi **1 ≤ E ≤ 2046**:

\[
(-1)^s \times \left(1+\frac{F}{2^{52}}\right)\times 2^{E-1023}
\]

Phần **1 + F/2^n** có một bit **1** ở đầu trong dạng nhị phân của significand. Bit 1 này **không cần lưu trữ trong trường fraction**, vì biết chắc nó tồn tại đối với mọi số normal. Nó thường được gọi là **implicit leading 1** hoặc **hidden bit**.

Ví dụ số float **1.0f**:

~~~text
Bit dấu:      0
Exponent E:   01111111 (127)
Fraction F:   00000000000000000000000 (0)

Giá trị: (+1) × (1 + 0/2^23) × 2^(127−127) = 1
Mẫu 32 bit: 0x3F800000
~~~

Trong miền normal, các số không cách đều nhau trên toàn trục số. Với cùng số mũ thực tế, các số kế tiếp nhau có độ chênh cố định, nhưng khi số mũ thay đổi thì khoảng cách cũng thay đổi theo lũy thừa của 2. Nhờ vậy, số normal duy trì một mức **độ chính xác tương đối** gần như ổn định trong miền biểu diễn.

### Ranh giới nhỏ nhất của số normal

Số normal dương nhỏ nhất xuất hiện khi E = 1 và F = 0:

| Hằng Java | Công thức | Mẫu bit |
| --- | --- | --- |
| **Float.MIN_NORMAL** | **2^−126** ≈ **1.17549435 × 10^−38** | **0x00800000** |
| **Double.MIN_NORMAL** | **2^−1022** ≈ **2.2250738585072014 × 10^−308** | **0x0010000000000000** |

Nếu định dạng chỉ có số normal và zero, mọi kết quả dương nhỏ hơn ngưỡng này có nguy cơ phải nhảy mạnh xuống 0. **Subnormal** tạo ra các mức trung gian giữa zero và normal nhỏ nhất.

## 4. Subnormal: bỏ bit 1 ẩn và dùng số mũ cố định

Với **E = 0 và F ≠ 0**, bit đầu của significand là **0**, thay vì 1. Giá trị float lúc này là:

\[
(-1)^s \times \left(\frac{F}{2^{23}}\right)\times 2^{-126}
= (-1)^s \times F \times 2^{-149}
\]

Với double:

\[
(-1)^s \times \left(\frac{F}{2^{52}}\right)\times 2^{-1022}
= (-1)^s \times F \times 2^{-1074}
\]

Điều cần nhớ là **E = 0 không có nghĩa dùng số mũ −127 ở float hay −1023 ở double**. Trong công thức subnormal, số mũ nền vẫn là **−126** với float và **−1022** với double. Sự lựa chọn này làm cho bước khoảng cách nối tiếp từ subnormal lớn nhất đến normal nhỏ nhất **không bị gián đoạn**.

Minh họa significand với một số float:

~~~text
Normal nhỏ nhất:
1.00000000000000000000000 × 2^-126
^ bit 1 ẩn

Subnormal lớn nhất:
0.11111111111111111111111 × 2^-126
^ bit 0 ẩn

Subnormal nhỏ nhất:
0.00000000000000000000001 × 2^-126
~~~

Vì subnormal không có bit 1 ẩn, khi độ lớn giảm, số bit có nghĩa thực sự giảm theo. Điều này tạo ra phần “gradual”: ta vẫn biểu diễn được những số khác 0, nhưng độ chính xác **tương đối** suy giảm gần 0.

### Hằng MIN_VALUE dễ gây hiểu nhầm

| Hằng Java | Nghĩa chính xác | Giá trị xấp xỉ | Mẫu bit |
| --- | --- | --- | --- |
| **Float.MIN_VALUE** | Số **float dương khác 0 nhỏ nhất** | 1.40129846 × 10^−45 | **0x00000001** |
| **Double.MIN_VALUE** | Số **double dương khác 0 nhỏ nhất** | 4.9406564584124654 × 10^−324 | **0x0000000000000001** |

Cả hai **MIN_VALUE đều là subnormal**, không phải số âm nhất. Số âm hữu hạn có độ lớn lớn nhất tương ứng với **−Float.MAX_VALUE** hoặc **−Double.MAX_VALUE**.

Giá trị subnormal dương lớn nhất có F toàn bit 1:

| Kiểu | Công thức | Mẫu bit |
| --- | --- | --- |
| float | **2^−126 − 2^−149** | **0x007FFFFF** |
| double | **2^−1022 − 2^−1074** | **0x000FFFFFFFFFFFFF** |

Có thể lấy số subnormal lớn nhất bằng **Math.nextDown(Float.MIN_NORMAL)** hoặc **Math.nextDown(Double.MIN_NORMAL)** mà không cần tự tính.

## 5. Gradual underflow: từ normal xuống subnormal rồi mới tới zero

**Underflow** là tình huống phép tính dấu phẩy động tạo ra kết quả có độ lớn quá nhỏ so với miền normal. Java áp dụng **gradual underflow**: khi kết quả có thể biểu diễn hoặc làm tròn thành subnormal khác 0, số đó vẫn được giữ.

Hãy nhìn vào một phép chia cho 2 trong float:

~~~text
2^-125                     normal
2^-126                     normal nhỏ nhất = Float.MIN_NORMAL
2^-127                     subnormal, biểu diễn chính xác
2^-128                     subnormal, biểu diễn chính xác
...
2^-148                     subnormal, biểu diễn chính xác
2^-149                     subnormal nhỏ nhất = Float.MIN_VALUE
2^-150                     nằm giữa 0 và Float.MIN_VALUE; làm tròn thành +0.0
~~~

Từ **2^−126** đến **2^−149**, các phép chia đúng cho 2 được minh họa vẫn cho số khác 0 và biểu diễn chính xác. Khi tới **2^−150**, kết quả đúng toán học nằm đúng giữa 0 và **2^−149**; quy tắc làm tròn **nearest, ties to even** chọn **+0.0** vì bit cuối được chọn là bit 0.

Nếu hệ thống dùng cơ chế **flush-to-zero (FTZ)**, một kết quả nằm trong miền subnormal có thể bị thay ngay thành 0. Đây không phải ngữ nghĩa của toán tử dấu phẩy động Java 17+: Java yêu cầu hỗ trợ subnormal và gradual underflow.

### Hình dung “bậc thang” sát zero

~~~text
float dương, không theo tỉ lệ hình học:

0        1·u       2·u       ...   (2^23−1)·u      2^23·u
|---------|---------|-------------------|-----------------|
zero   minSub      subnormal           maxSub        minNormal
                                             
u = 2^-149 = Float.MIN_VALUE
minNormal = 2^23 · u = 2^-126
~~~

Các số subnormal dương float chính xác là **k × 2^−149** với k nguyên từ **1** đến **2^23 − 1**. Vì thế chúng **cách nhau một lượng tuyệt đối cố định u**. Bước từ subnormal lớn nhất đến normal nhỏ nhất cũng đúng bằng **u**, tạo liên tục về *khoảng cách biểu diễn* ở ranh giới. Điều này không có nghĩa các số thực nằm giữa hai giá trị float cũng biểu diễn được; mọi kết quả vẫn phải làm tròn về giá trị khả dụng.

Tương tự, các subnormal dương double có bước cố định **2^−1074**.

## 6. Mất dần độ chính xác tương đối

Trong miền normal, significand chứa **24 bit có nghĩa với float** và **53 bit với double** (gồm bit 1 ẩn). Khi sang subnormal, bit đầu bắt buộc là 0, phần có nghĩa hiệu dụng ít dần khi giá trị tiến về zero.

Hai ý tưởng về sai số cần phân biệt:

- **Độ phân giải tuyệt đối (absolute spacing)**: khoảng cách giữa hai giá trị biểu diễn được gần nhau. Với mọi float subnormal dương, đó là **2^−149**.
- **Độ phân giải tương đối (relative spacing)**: tỉ lệ của khoảng cách trên so với độ lớn số đang xét. Tỉ lệ này tăng mạnh khi số tiến về zero.

Ví dụ tại **Float.MIN_NORMAL = 2^−126**:

~~~text
Bước lưới             = 2^-149
Bước / giá trị        = 2^-149 / 2^-126 = 2^-23
                      ≈ 1.1920929 × 10^-7
~~~

Nhưng tại **Float.MIN_VALUE = 2^−149**, bước lưới lại **bằng chính giá trị đó**. Số rất nhỏ có thể có sai số tương đối lớn khi nhân, chia, cộng hoặc trừ. Đó là đánh đổi có chủ đích của gradual underflow: bảo toàn được nhiều giá trị rất nhỏ, nhưng không bảo toàn được độ chính xác tương đối như ở miền normal.

**Subnormal không mặc nhiên là kết quả sai.** Ví dụ phép tính **Float.MIN_NORMAL / 2** tạo ra một subnormal **chính xác**. Ngược lại, một phép tính dưới ngưỡng có thể phải làm tròn; tình trạng “kết quả là subnormal” và “kết quả bị làm tròn không chính xác” không đồng nghĩa nhau. Theo IEEE 754, khái niệm cờ *underflow* còn liên quan điều kiện tininess/inexact; Java không cung cấp một API thông thường để đọc cờ trạng thái phần cứng đó.

## 7. Làm tròn khi độ lớn nhỏ hơn subnormal nhỏ nhất

Java 17+ áp dụng ngữ nghĩa dấu phẩy động strict, phù hợp với quy tắc làm tròn của các toán tử tương ứng. Đối với cộng/trừ/nhân/chia dấu phẩy động thông thường, kết quả hữu hạn không biểu diễn chính xác sẽ được làm tròn về giá trị gần nhất; khi ở đúng trung điểm, chọn giá trị có bit thấp nhất chẵn (*ties to even*).

Xét float dương:

~~~text
0.0f                   = 0
Float.MIN_VALUE        = 2^-149
Float.MIN_VALUE / 2    = 2^-150
~~~

**2^−150** nằm đúng giữa 0 và **2^−149**, nên làm tròn thành **+0.0f**. Với số âm, **−2^−150** làm tròn thành **−0.0f**: bit dấu vẫn có ý nghĩa trong biểu diễn mặc dù hai số zero so sánh bằng nhau qua **==**.

Một trường hợp khác:

~~~text
Float.MIN_VALUE * 1.5 = 1.5 × 2^-149
~~~

Giá trị toán học nằm đúng giữa **1 × 2^−149** và **2 × 2^−149**; ties-to-even chọn **2 × 2^−149**, nên kết quả có trường fraction là **2**, tương ứng mẫu **0x00000002**.

**Không có ArithmeticException** khi kết quả phép toán float/double bị làm tròn thành 0 do underflow. Các phép toán này trả về giá trị floating-point thích hợp.

### Signed zero khác nhau khi quan sát bằng bit

~~~java
float positiveZero = Float.MIN_VALUE / 2.0f;
float negativeZero = -Float.MIN_VALUE / 2.0f;

System.out.println(positiveZero == negativeZero); // true
System.out.println(1.0f / positiveZero);            // Infinity
System.out.println(1.0f / negativeZero);            // -Infinity
~~~

Đoạn mã trên minh họa, không phải chương trình độc lập. Dùng **Float.floatToRawIntBits** để quan sát bit dấu chính xác; so sánh **==** không phân biệt +0.0 với −0.0.

## 8. Ví dụ Java 17+ kiểm tra các ranh giới bằng bit

Tạo tệp **SubnormalDemo.java** với toàn bộ mã sau:

~~~java
public class SubnormalDemo {
    static String bits(float value) {
        return "0x%08X".formatted(Float.floatToRawIntBits(value));
    }

    static String bits(double value) {
        return "0x%016X".formatted(Double.doubleToRawLongBits(value));
    }

    static String category(float value) {
        int raw = Float.floatToRawIntBits(value);
        int exponent = (raw >>> 23) & 0xFF;
        int fraction = raw & 0x7FFFFF;

        if (exponent == 0) {
            return fraction == 0 ? "zero" : "subnormal";
        }
        if (exponent == 0xFF) {
            return fraction == 0 ? "infinity" : "NaN";
        }
        return "normal";
    }

    public static void main(String[] args) {
        float minNormal = Float.MIN_NORMAL;
        float minSub = Float.MIN_VALUE;
        float maxSub = Math.nextDown(minNormal);

        System.out.println("float minNormal: " + bits(minNormal)
                + " / " + category(minNormal));
        System.out.println("float maxSub: " + bits(maxSub)
                + " / " + category(maxSub));
        System.out.println("float minSub: " + bits(minSub)
                + " / " + category(minSub));
        System.out.println("float zero: " + bits(0.0f)
                + " / " + category(0.0f));
        System.out.println("float -zero: " + bits(-0.0f)
                + " / " + category(-0.0f));

        System.out.println("float minNormal hex: "
                + Float.toHexString(minNormal));
        System.out.println("float minSub hex: "
                + Float.toHexString(minSub));

        System.out.println("float minNormal / 2: " + bits(minNormal / 2.0f));
        System.out.println("float maxSub + minSub: " + bits(maxSub + minSub));
        System.out.println("float minSub / 2: " + bits(minSub / 2.0f));
        System.out.println("float -minSub / 2: " + bits(-minSub / 2.0f));
        System.out.println("float minSub * 1.5: " + bits(minSub * 1.5f));

        double minNormalD = Double.MIN_NORMAL;
        double minSubD = Double.MIN_VALUE;

        System.out.println("double minNormal: " + bits(minNormalD));
        System.out.println("double maxSub: "
                + bits(Math.nextDown(minNormalD)));
        System.out.println("double minSub: " + bits(minSubD));
        System.out.println("double minNormal / 2: "
                + bits(minNormalD / 2.0));
        System.out.println("double minSub / 2: " + bits(minSubD / 2.0));
        System.out.println("double minNormal hex: "
                + Double.toHexString(minNormalD));
        System.out.println("double minSub hex: "
                + Double.toHexString(minSubD));
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac --release 17 SubnormalDemo.java
java SubnormalDemo
~~~

Kết quả stdout chính xác:

~~~text
float minNormal: 0x00800000 / normal
float maxSub: 0x007FFFFF / subnormal
float minSub: 0x00000001 / subnormal
float zero: 0x00000000 / zero
float -zero: 0x80000000 / zero
float minNormal hex: 0x1.0p-126
float minSub hex: 0x0.000002p-126
float minNormal / 2: 0x00400000
float maxSub + minSub: 0x00800000
float minSub / 2: 0x00000000
float -minSub / 2: 0x80000000
float minSub * 1.5: 0x00000002
double minNormal: 0x0010000000000000
double maxSub: 0x000FFFFFFFFFFFFF
double minSub: 0x0000000000000001
double minNormal / 2: 0x0008000000000000
double minSub / 2: 0x0000000000000000
double minNormal hex: 0x1.0p-1022
double minSub hex: 0x0.0000000000001p-1022
~~~

**Giải thích những dòng quan trọng:**

- **float minNormal 0x00800000**: bit exponent đầu tiên bằng 1, fraction bằng 0 → normal nhỏ nhất.
- **float maxSub 0x007FFFFF**: exponent bằng 0, fraction toàn bit 1 → subnormal lớn nhất.
- **float minSub 0x00000001**: exponent bằng 0, chỉ bit fraction thấp nhất bằng 1 → subnormal nhỏ nhất.
- **float minNormal / 2 0x00400000**: kết quả đã chuyển sang miền subnormal, nhưng không mất đi.
- **float maxSub + minSub 0x00800000**: từ subnormal lớn nhất tiến đúng một bước đến normal nhỏ nhất; chứng minh tính liên tục của khoảng cách biểu diễn ở ngưỡng.
- **float minSub / 2 0x00000000**: giá trị đúng toán học quá nhỏ, làm tròn thành zero dương.
- **float -minSub / 2 0x80000000**: sự mất độ lớn không xóa bit dấu.
- **float minSub * 1.5 0x00000002**: ví dụ một phép làm tròn đúng trung điểm về phía significand chẵn.
- **double minNormal / 2 0x0008000000000000**: gradual underflow cũng hiện diện ở kiểu double, với độ rộng trường fraction là 52 bit.

**Float.toHexString/Double.toHexString** in số hexa dấu phẩy động dạng **0x...p...**, trong đó **p** mô tả số mũ cơ số 2. Nó không giống chuỗi **0x00800000** biểu thị nguyên mẫu bit. Đó là lý do chương trình dùng cả **toHexString** và **floatToRawIntBits/doubleToRawLongBits**.

Hàm **category** dựa trên trường bit và phù hợp để hiểu cách phân loại số float. Java còn có **Float.isFinite**, **Float.isNaN**, **Float.isInfinite**, nhưng các hàm này không phân biệt riêng normal/subnormal; nếu cần xác định subnormal, hãy xét trường exponent và fraction như ví dụ.

## 9. Vì sao gradual underflow có ích trong tính toán?

Các thuật toán khoa học, xử lý tín hiệu và phép tính giá trị xác suất đôi khi tạo ra những lượng rất nhỏ. Gradual underflow có thể duy trì chúng để các phép tính tiếp theo còn khai thác được thay vì đột ngột biến mất.

Ví dụ, giả sử một bước tính cho ra **x = Float.MIN_NORMAL / 2**. Nếu kết quả ấy còn được giữ, **x × 2** phục hồi đúng **Float.MIN_NORMAL**:

~~~java
float x = Float.MIN_NORMAL / 2.0f;
float restored = x * 2.0f;
System.out.println(restored == Float.MIN_NORMAL); // true
~~~

Trong phép tính này, giá trị trung gian là subnormal khác 0. Nếu nó bị làm tròn ngay thành 0, phép nhân sau đó không thể khôi phục thông tin. Tuy nhiên không phải mọi thao tác với subnormal đều chính xác; gần ngưỡng nhỏ nhất, một bước làm tròn thành 0 là điều không tránh được.

Với những ứng dụng cần **độ chính xác tương đối ổn định** ở miền rất nhỏ, chỉ dựa vào gradual underflow chưa đủ. Có thể cần **đổi thang đo (rescaling)**, tính tổng theo thuật toán số ổn định, sử dụng **logarithm** để lưu giá trị xác suất rất nhỏ, hoặc chọn biểu diễn số khác theo bài toán. Mỗi hướng đều đòi hỏi đánh giá lại công thức; không mặc nhiên chuyển mọi phép tính sang BigDecimal nếu vấn đề gốc là sự ổn định của thuật toán nhị phân.

## 10. Những lưu ý riêng khi chạy trên Java

### Java 17+ yêu cầu ngữ nghĩa strict

Từ Java 17, [JEP 306 — Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306) khôi phục việc đánh giá strict cho các biểu thức dấu phẩy động. Các toán tử floating-point Java phải tuân theo ngữ nghĩa tương ứng, bao gồm subnormal và gradual underflow. Từ khóa **strictfp** không còn làm thay đổi cách đánh giá số học ở Java 17+, dù vẫn tồn tại để tương thích mã nguồn.

Đừng nhầm yêu cầu ngữ nghĩa Java với cách cấu hình xử lý subnormal trong các thư viện native/JNI, GPU hoặc môi trường tính toán khác. Các hệ thống ngoài Java có thể sử dụng thiết lập flush-to-zero riêng; kết quả ở ranh giới liên kết cần kiểm chứng theo môi trường cụ thể.

### Tối ưu hiệu năng không làm thay đổi quy tắc số học

Trên một số phần cứng hoặc đường xử lý nhất định, phép toán liên quan subnormal có thể có chi phí hiệu năng khác normal. Không nên mặc định mọi JVM hay CPU đều bị chậm. Khi cần tối ưu, đo trên JVM và đầu vào thực tế, đồng thời bảo toàn tính đúng đắn của thuật toán.

### Những hàm giúp khảo sát giá trị

| API | Công dụng |
| --- | --- |
| **Float.MIN_NORMAL**, **Double.MIN_NORMAL** | Ngưỡng normal dương nhỏ nhất |
| **Float.MIN_VALUE**, **Double.MIN_VALUE** | Subnormal dương nhỏ nhất, không phải số âm nhỏ nhất |
| **Math.nextDown(x)** | Giá trị dấu phẩy động biểu diễn được gần nhất ở phía nhỏ hơn |
| **Math.nextUp(x)** | Giá trị dấu phẩy động biểu diễn được gần nhất ở phía lớn hơn |
| **Math.ulp(x)** | Khoảng đơn vị tại mức độ lớn của x theo định nghĩa của API |
| **Float.toHexString(x)**, **Double.toHexString(x)** | Biểu diễn hexa dấu phẩy động với số mũ nhị phân |
| **Float.floatToRawIntBits(x)** | Xem 32 bit của float, bao gồm bit dấu của −0.0 |
| **Double.doubleToRawLongBits(x)** | Xem 64 bit của double |

**Math.ulp(Float.MIN_NORMAL)** và **Math.ulp(Float.MIN_VALUE)** đều bằng **Float.MIN_VALUE**. Mẫu này phản ánh bước lưới không đổi của miền subnormal tại phía gần ngưỡng normal.

### Float và double không có cùng ngưỡng

Một giá trị dưới **Float.MIN_NORMAL** chưa chắc là subnormal đối với **double**. Ví dụ giá trị toán học **2^−149** là subnormal nhỏ nhất của float, nhưng là số **normal** trong double, vì **−149** vẫn lớn hơn rất nhiều so với số mũ normal nhỏ nhất **−1022** của double. Khi đổi kiểu từ double sang float, một giá trị có thể bị làm tròn thành subnormal hoặc zero; hãy xét kiểu lưu trữ của kết quả, không chỉ giá trị ban đầu.

## 11. Những nhầm lẫn phổ biến

| Nhầm lẫn | Điều đúng cần ghi nhớ |
| --- | --- |
| **Float.MIN_VALUE** là float âm nhất | Nó là float **dương khác 0 nhỏ nhất**, thuộc miền subnormal |
| Subnormal nghĩa là zero | Subnormal có fraction khác 0, là số hữu hạn khác 0 |
| Exponent E=0 luôn có nghĩa số mũ thực bằng −bias | Subnormal dùng số mũ nền **−126** (float) hoặc **−1022** (double) và leading bit **0** |
| Số normal vừa dưới ngưỡng sẽ trở thành 0 ngay | Java giữ subnormal khi làm tròn có giá trị subnormal |
| Subnormal có đủ 24/53 bit có nghĩa như normal | Độ chính xác tương đối giảm dần khi tiến về zero |
| Giá trị subnormal luôn do phép tính sai số tạo ra | Nhiều kết quả subnormal, ví dụ chia đôi MIN_NORMAL, là chính xác |
| Underflow luôn ném exception | Phép toán float/double trả subnormal hoặc signed zero, không ném ArithmeticException do underflow |
| **0.0f == -0.0f** nghĩa là mẫu bit giống nhau | Hai zero so sánh bằng nhau nhưng khác bit dấu và có thể dẫn tới +/−Infinity khi làm mẫu số |
| Toán tử Java có thể tự flush mọi subnormal về 0 để tối ưu | Ngữ nghĩa floating-point Java yêu cầu gradual underflow; việc tối ưu phải giữ kết quả tương đương |
| In giá trị thập phân là đủ để hiểu biểu diễn | Dùng **toHexString** và **RawBits** để thấy ngưỡng, số mũ, bit fraction và signed zero |

## 12. Câu hỏi và bài tập tự kiểm tra

**Câu 1.** Vì sao **Float.MIN_NORMAL / 2.0f** là số khác 0?

**Đáp án:** Kết quả bằng **2^−127**, vẫn là bội nguyên của **2^−149**, nên biểu diễn được chính xác bằng subnormal có mẫu **0x00400000**.

**Câu 2.** Mẫu bit **0x00000001** của float thuộc loại nào? Giá trị là bao nhiêu?

**Đáp án:** Exponent bằng 0 và fraction bằng 1, nên là **subnormal** dương nhỏ nhất, giá trị **2^−149 = Float.MIN_VALUE**.

**Câu 3.** Vì sao **Float.MIN_VALUE / 2.0f** thành **+0.0f**, nhưng **Float.MIN_VALUE * 1.5f** thành **0x00000002**?

**Đáp án:** Cả hai kết quả toán học đều ở đúng trung điểm trên lưới biểu diễn. Ties-to-even chọn **0** trong trường hợp đầu và chọn **2 × 2^−149** trong trường hợp sau.

**Câu 4.** Số subnormal dương lớn nhất của float là gì? Số normal dương nhỏ nhất cách nó bao xa?

**Đáp án:** **0x007FFFFF = (2^23−1) × 2^−149**; khoảng cách đến **Float.MIN_NORMAL** đúng **Float.MIN_VALUE = 2^−149**.

**Câu 5.** Khi chuyển **float** nhỏ nhất dương sang **double**, nó còn là subnormal không?

**Đáp án:** Không. **2^−149** là số normal của double. Loại normal/subnormal phụ thuộc **định dạng đích**.

**Câu 6.** Nếu tính xác suất rất nhỏ, chỉ có gradual underflow thì đã tránh được mọi mất độ chính xác chưa?

**Đáp án:** Chưa. Nó kéo dài miền số hữu hạn khác 0, nhưng độ chính xác tương đối vẫn suy giảm ở subnormal và các kết quả đủ nhỏ vẫn bị làm tròn về zero. Có thể cần biến đổi công thức hoặc làm việc trong miền log tùy thuật toán.

## 13. Tài liệu đối chiếu

- [Java Language Specification SE 17 — §4.2.3 Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): miền biểu diễn normal/subnormal, độ rộng và độ chính xác.
- [Java Language Specification SE 17 — §15.4 Floating-Point Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.4): ngữ nghĩa phép tính, underflow và các giá trị đặc biệt.
- [Float API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html): các hằng **MIN_NORMAL**, **MIN_VALUE**, phương thức **toHexString** và thao tác bit.
- [Double API, Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): hằng số và hàm tương ứng với binary64.
- [OpenJDK JEP 306](https://openjdk.org/jeps/306): ngữ nghĩa floating-point luôn strict từ Java 17.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-007">MIN_VALUE, MIN_NORMAL và MAX_VALUE</a>

<details>
<summary>Xem chi tiết</summary>

# MIN_VALUE, MIN_NORMAL và MAX_VALUE của float/double trong Java

> **Java core – numbers · Biểu diễn dấu phẩy động**  
> **Mục tiêu:** Phân biệt ba hằng biên quan trọng của `Float`/`Double`, đọc đúng miền *normal* và *subnormal* của IEEE 754, hiểu underflow/overflow, nhận biết số 0 có dấu và chọn cách kiểm tra biên phù hợp trong Java 17+.

## 1. Câu trả lời quan trọng nhất

Đối với **`float` và `double`**, Java định nghĩa:

- **`MIN_VALUE`:** số dấu phẩy động **dương hữu hạn khác 0 nhỏ nhất** có thể biểu diễn, thuộc vùng **subnormal** (*dưới chuẩn hóa*).
- **`MIN_NORMAL`:** số dấu phẩy động **dương hữu hạn chuẩn hóa nhỏ nhất** (*smallest positive normal*).
- **`MAX_VALUE`:** số dấu phẩy động **dương hữu hạn lớn nhất** có thể biểu diễn.

**Cảnh báo:** `Double.MIN_VALUE` **không phải số âm nhỏ nhất**. Hằng này lớn hơn 0 nhưng cực kỳ gần 0. Nếu cần giá trị `double` hữu hạn âm có độ lớn lớn nhất, dùng `-Double.MAX_VALUE`. Quy tắc tương tự áp dụng cho `Float`.

Đây là đặc điểm khác hẳn hằng `Integer.MIN_VALUE` hay `Long.MIN_VALUE`: đối với kiểu **nguyên**, `MIN_VALUE` là số âm nhỏ nhất; đối với **dấu phẩy động**, nó là số **dương khác 0 nhỏ nhất**.

## 2. Bảng giá trị quan trọng

| Hằng | Giá trị Java in dạng thập phân | Dạng chính xác theo cơ số 2 |
| --- | --- | --- |
| `Float.MIN_VALUE` | `1.4E-45` | `2^-149` |
| `Float.MIN_NORMAL` | `1.17549435E-38` | `2^-126` |
| `Float.MAX_VALUE` | `3.4028235E38` | `(2 - 2^-23) × 2^127` |
| `Double.MIN_VALUE` | `4.9E-324` | `2^-1074` |
| `Double.MIN_NORMAL` | `2.2250738585072014E-308` | `2^-1022` |
| `Double.MAX_VALUE` | `1.7976931348623157E308` | `(2 - 2^-52) × 2^1023` |

Dãy số thập phân trong bảng là cách Java hiển thị giá trị dưới dạng chuỗi ngắn đủ nhận diện giá trị nhị phân; không nên hiểu `4.9E-324` là giá trị toán học chính xác bằng `4.9 × 10^-324`. Dạng lũy thừa của 2 mô tả trực tiếp giá trị trong hệ nhị phân.

Các hằng nằm trong `java.lang.Float` và `java.lang.Double`; không cần import riêng.

```java
float smallestPositiveFloat = Float.MIN_VALUE;
float smallestNormalFloat = Float.MIN_NORMAL;
float largestFiniteFloat = Float.MAX_VALUE;

double smallestPositiveDouble = Double.MIN_VALUE;
double smallestNormalDouble = Double.MIN_NORMAL;
double largestFiniteDouble = Double.MAX_VALUE;
```

Tất cả sáu hằng đều mang giá trị **dương, hữu hạn, khác 0**. Không hằng nào là `NaN` hoặc vô cực.

## 3. Bố cục IEEE 754 giải thích ba ranh giới

Java dùng dạng nhị phân IEEE 754 cho `float` (*binary32*) và `double` (*binary64*):

| Thuộc tính | `float` | `double` |
| --- | ---: | ---: |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit exponent lưu trữ | 8 | 11 |
| Bit fraction lưu trữ | 23 | 52 |
| Số bit chính xác hiệu dụng của số normal | 24 | 53 |
| Exponent không thiên lệch nhỏ nhất của normal | −126 | −1022 |
| Exponent không thiên lệch lớn nhất của normal | +127 | +1023 |

Lược đồ:

```text
float  (32 bit): [sign:1][exponent:8 ][fraction:23]
double (64 bit): [sign:1][exponent:11][fraction:52]
```

Với một số **normal hữu hạn** có exponent field không phải toàn 0/toàn 1, giá trị tuyệt đối có dạng:

\[
(1+\mathrm{fraction})\times 2^e
\]

Bit `1` đầu tiên của phần trị normal là **ẩn** (*implicit leading 1*), nhờ vậy số bit chính xác hiệu dụng lớn hơn số bit fraction lưu trong bộ nhớ 1 bit.

Với số **subnormal**, exponent field **toàn 0**, fraction field **khác 0**, và bit dẫn đầu chuyển thành **0**:

\[
\mathrm{fraction}\times 2^{e_{\min}}
\]

Ở đây `fraction` là giá trị từ các bit phần lẻ nhị phân; `e_min` bằng −126 cho `float` và −1022 cho `double`. Vì không còn bit `1` ẩn, độ chính xác tương đối của các số subnormal giảm dần khi tiến về 0.

Exponenent field **toàn 1** được dành cho các giá trị đặc biệt: `Infinity` và `NaN`. Số 0 có exponent field và fraction field đều bằng 0, với bit dấu có thể là 0 hoặc 1.

## 4. MIN_NORMAL: ranh giới bắt đầu của số normal

Số normal dương nhỏ nhất có phần trị hiệu dụng là **1.0** và exponent nhỏ nhất của normal:

\[
\mathrm{Float.MIN\_NORMAL}=1.0\times 2^{-126}
\]

\[
\mathrm{Double.MIN\_NORMAL}=1.0\times 2^{-1022}
\]

Trong Java:

```java
float f = 0x1.0p-126f;
double d = 0x1.0p-1022;

System.out.println(f == Float.MIN_NORMAL);   // true
System.out.println(d == Double.MIN_NORMAL); // true
```

Những hằng hex này đọc là: phần trị `0x1.0` bằng 1, chữ `p` chỉ số mũ của **2**, và hậu tố `f` buộc kiểu `float`. Các biểu thức hex rất tiện khi mô tả biên nhị phân chính xác.

**Điều cần tránh:** `MIN_NORMAL` không phải số dương khác 0 nhỏ nhất. Java còn biểu diễn được các giá trị nhỏ hơn nhờ miền subnormal.

## 5. MIN_VALUE: bước nhỏ nhất khác 0 ở sát số 0

Số subnormal dương nhỏ nhất có đúng **một bit fraction thấp nhất bằng 1**. Giá trị của nó là:

\[
\mathrm{Float.MIN\_VALUE}=2^{-149}
\]

\[
\mathrm{Double.MIN\_VALUE}=2^{-1074}
\]

Có thể viết chính xác:

```java
float minFloat = 0x1p-149f;       // Float.MIN_VALUE
double minDouble = 0x1p-1074;   // Double.MIN_VALUE

System.out.println(minFloat == Float.MIN_VALUE);   // true
System.out.println(minDouble == Double.MIN_VALUE); // true
```

Với `double`, fraction field có 52 bit, nên để từ exponent −1022 đi xuống bit fraction cuối cùng cần thêm 52 bậc: `−1022 − 52 = −1074`. Với `float`: `−126 − 23 = −149`.

Khoảng giữa **0** và **MIN_NORMAL** gồm nhiều số subnormal dương:

```text
0       MIN_VALUE      ...     subnormal lớn nhất    MIN_NORMAL
|-----------|--------------------------|------------------|
            ^                                      ^
  subnormal dương nhỏ nhất               normal dương nhỏ nhất
```

Khoảng cách giữa hai giá trị subnormal dương liên tiếp bằng `MIN_VALUE` của kiểu tương ứng. Đây là cơ chế *gradual underflow*, giúp hạn chế việc một phép toán rất nhỏ bị cắt ngay về 0.

Một kiểm tra:

```java
double smallestStep = Double.MIN_VALUE;
double largestSubnormal = Math.nextDown(Double.MIN_NORMAL);

System.out.println(largestSubnormal + smallestStep
        == Double.MIN_NORMAL); // true
```

Giá trị subnormal lớn nhất là số nằm ngay trước `MIN_NORMAL` trên trục số dương.

## 6. MAX_VALUE: số hữu hạn lớn nhất, chưa phải Infinity

`Float.MAX_VALUE` và `Double.MAX_VALUE` mang **exponent hữu hạn lớn nhất** và toàn bộ fraction bit bằng 1:

\[
\mathrm{Float.MAX\_VALUE}=(2-2^{-23})\times 2^{127}
\]

\[
\mathrm{Double.MAX\_VALUE}=(2-2^{-52})\times 2^{1023}
\]

Dạng literal hex chính xác:

```java
float largestFloat = 0x1.fffffep127f;
double largestDouble = 0x1.fffffffffffffp1023;

System.out.println(largestFloat == Float.MAX_VALUE); // true
System.out.println(largestDouble == Double.MAX_VALUE); // true
```

**MAX_VALUE không phải Infinity.** Nó hữu hạn. Nhưng một phép tính vượt miền có thể **overflow thành vô cực**:

```java
double big = Double.MAX_VALUE;
double overflow = big * 2.0;

System.out.println(Double.isFinite(big)); // true
System.out.println(overflow);             // Infinity
System.out.println(Double.isInfinite(overflow)); // true
```

Không được giả định mọi phép vượt gần biên đều lập tức ra `Infinity`: IEEE 754 làm tròn kết quả; ở một số giá trị thực toán học hơi lớn hơn `MAX_VALUE`, phép làm tròn vẫn có thể trả `MAX_VALUE`. Điều đúng cần nhớ là khi **kết quả đã làm tròn vượt số hữu hạn biểu diễn được**, nó trở thành vô cực theo các quy tắc dấu phẩy động.

Với kết quả âm vượt biên dưới hữu hạn, bạn có thể nhận `Double.NEGATIVE_INFINITY`. Các hằng `POSITIVE_INFINITY`/`NEGATIVE_INFINITY` và `NaN` thuộc hệ giá trị đặc biệt, khác `MAX_VALUE`.

## 7. Miền số trên trục số: âm, 0, subnormal, normal

Xét riêng các giá trị `double` hữu hạn:

```text
   -Double.MAX_VALUE                                  Double.MAX_VALUE
          |                                                   |
     âm normal ... âm subnormal  -0.0  +0.0  dương subnormal ... normal
          |                    |    |    |                   |
          +--------------------+----+----+-------------------+

Biên dương:
0 < Double.MIN_VALUE < Double.MIN_NORMAL < Double.MAX_VALUE
```

Hai số `+0.0` và `-0.0` có **bit dấu khác nhau** nhưng so sánh bằng nhau với toán tử `==`:

```java
System.out.println(0.0 == -0.0); // true
System.out.println(1.0 / 0.0);   // Infinity
System.out.println(1.0 / -0.0);  // -Infinity
```

Vậy `-0.0` là giá trị quan trọng ở ranh giới dưới 0, nhưng **không thể gọi** nó là “số âm hữu hạn nhỏ nhất” theo nghĩa miền giá trị; số âm hữu hạn có trị tuyệt đối lớn nhất là `-Double.MAX_VALUE`.

Một thứ tự giá trị **hữu hạn** quanh 0:

```text
... < -Double.MIN_NORMAL < -Double.MIN_VALUE < -0.0
                                                = +0.0
                                < Double.MIN_VALUE < Double.MIN_NORMAL < ...
```

Ký hiệu `-0.0 = +0.0` ở đây nói về so sánh số học thông thường (`==`), không khẳng định hai biểu diễn bit là giống nhau. Với `Double.compare` hoặc `Double.equals`, có những quy tắc khác về cách phân biệt hai số 0 có dấu.

## 8. Underflow: từ normal qua subnormal rồi về 0

*Underflow* xảy ra khi kết quả tính toán nằm rất sát 0 so với miền/độ chính xác của kiểu dấu phẩy động. Nó có thể tạo ra một số **subnormal**, hoặc về **0 có dấu** nếu kết quả quá nhỏ để làm tròn thành giá trị khác 0.

```java
double justSubnormal = Double.MIN_NORMAL / 2.0;
double underflowToZero = Double.MIN_VALUE / 2.0;

System.out.println(justSubnormal > 0.0);  // true
System.out.println(justSubnormal < Double.MIN_NORMAL); // true
System.out.println(underflowToZero);       // 0.0
```

Vì `MIN_VALUE` đã là bước subnormal nhỏ nhất khác 0, chia cho 2 tạo giá trị toán học đúng giữa 0 và `MIN_VALUE`; với chế độ làm tròn mặc định đến giá trị gần nhất, hòa thì chọn chữ số chẵn (*round to nearest, ties to even*), kết quả là `+0.0`.

Tương tự, `-Double.MIN_VALUE / 2.0` làm tròn về `-0.0`; **dấu có thể được giữ ngay cả khi độ lớn về 0**.

Vùng subnormal hữu ích vì phép trừ những số rất nhỏ còn có thể cho kết quả khác 0 thay vì lập tức đột ngột về 0. Tuy vậy số subnormal có ít bit có nghĩa hơn, nên **độ chính xác tương đối thấp hơn** so với số normal.

**Không dùng** `Double.MIN_VALUE` như “ngưỡng sai số nhỏ chuẩn” cho mọi phép so sánh gần bằng. Nó quá nhỏ so với sai số của các phép tính ở độ lớn thường gặp như gần 1 hoặc 1000.

## 9. ULP và nextUp/nextDown: cách khảo sát giá trị sát nhau

*ULP* (*unit in the last place*) mô tả khoảng cách giữa những giá trị biểu diễn được ở một vùng độ lớn nhất định. Trong Java:

- `Math.ulp(x)` cho biết đơn vị bước gần x theo quy tắc của API.
- `Math.nextUp(x)` trả số dấu phẩy động có thể biểu diễn ngay phía trên x.
- `Math.nextDown(x)` trả số có thể biểu diễn ngay phía dưới x.

Các ví dụ kiểm chứng biên:

```java
System.out.println(Math.nextUp(0.0) == Double.MIN_VALUE); // true
System.out.println(Math.nextDown(Double.MIN_NORMAL)
        + Double.MIN_VALUE == Double.MIN_NORMAL);         // true
System.out.println(Math.nextUp(Double.MAX_VALUE));       // Infinity
System.out.println(Math.ulp(0.0) == Double.MIN_VALUE);    // true
```

Với các số normal, khoảng cách giữa các số liền kề **không cố định trên toàn trục số**: càng tăng độ lớn, bước nhảy giữa các giá trị có thể biểu diễn thường tăng theo lũy thừa của 2.

Ví dụ:

```java
System.out.println(Math.ulp(1.0));    // 2.220446049250313E-16
System.out.println(Math.ulp(2.0));    // 4.440892098500626E-16
```

Vì vậy một điều kiện như `Math.abs(a - b) < Double.MIN_VALUE` gần như chỉ chấp nhận hiệu đúng bằng 0 ở nhiều vùng số; nếu muốn so sánh gần bằng, phải lựa chọn **độ sai số tuyệt đối/tương đối theo nghiệp vụ** (và cân nhắc vô cực, NaN, gần 0).

## 10. Số đặc biệt: NaN, Infinity, positive/negative zero

Ngoài số hữu hạn bình thường và subnormal, IEEE 754 còn có:

| Giá trị | Ví dụ tạo ra | Đặc điểm |
| --- | --- | --- |
| `Double.POSITIVE_INFINITY` | `1.0 / 0.0` | Không hữu hạn, lớn hơn mọi số hữu hạn |
| `Double.NEGATIVE_INFINITY` | `-1.0 / 0.0` | Không hữu hạn, nhỏ hơn mọi số hữu hạn |
| `Double.NaN` | `0.0 / 0.0` | “Not a Number”, so sánh số học đặc biệt |
| `+0.0` | `0.0` | Số 0 có dấu dương |
| `-0.0` | `-0.0` | Số 0 có dấu âm; `1.0 / -0.0` trả `-Infinity` |

```java
double x = Double.NaN;
System.out.println(x == x);             // false
System.out.println(Double.isNaN(x));     // true
System.out.println(Double.isFinite(x));  // false
```

Không kiểm tra NaN bằng `x == Double.NaN`. Dùng `Double.isNaN(x)`. Với vô cực dùng `Double.isInfinite(x)`, còn `Double.isFinite(x)` xác định số hữu hạn (bao gồm normal, subnormal và cả ±0.0).

Nếu API yêu cầu “số dương và hữu hạn”, điều kiện có thể là:

```java
static boolean positiveFinite(double x) {
    return Double.isFinite(x) && x > 0.0;
}
```

Điều kiện này chấp nhận số subnormal dương. Nếu nghiệp vụ muốn chỉ chấp nhận **normal dương**, phải nêu và kiểm tra thêm `x >= Double.MIN_NORMAL` (đồng thời giữ `isFinite`). Điều kiện `x >= Double.MIN_VALUE` **không** thay thế kiểm tra normal.

## 11. Inspect bit: ba hằng khác nhau ở exponent/fraction

Xem cấu trúc bit giúp kết nối giá trị thập phân với biểu diễn IEEE 754:

| Hằng | Hex của toàn bộ raw bits | Ý nghĩa |
| --- | --- | --- |
| `Float.MIN_VALUE` | `0x00000001` | exponent=0, fraction có bit thấp nhất |
| `Float.MIN_NORMAL` | `0x00800000` | exponent lưu bằng 1, fraction=0 |
| `Float.MAX_VALUE` | `0x7f7fffff` | exponent hữu hạn lớn nhất, fraction toàn 1 |
| `Double.MIN_VALUE` | `0x0000000000000001` | exponent=0, fraction có bit thấp nhất |
| `Double.MIN_NORMAL` | `0x0010000000000000` | exponent lưu bằng 1, fraction=0 |
| `Double.MAX_VALUE` | `0x7fefffffffffffff` | exponent hữu hạn lớn nhất, fraction toàn 1 |

Trong Java:

```java
int fBits = Float.floatToRawIntBits(Float.MIN_NORMAL);
long dBits = Double.doubleToRawLongBits(Double.MIN_NORMAL);
System.out.printf("float bits = %08x%n", fBits);  // 00800000
System.out.printf("double bits = %016x%n", dBits); // 0010000000000000
```

Lưu ý: **raw bits** là cách mã hóa đủ bit dấu, exponent và fraction, còn `Double.toHexString` biểu diễn **giá trị toán học** theo phần trị hex và số mũ nhị phân. Hai dạng có mục đích khác nhau; không nhầm chúng là một chuỗi.

Dạng `Double.toHexString(Double.MIN_VALUE)` là `"0x0.0000000000001p-1022"`; đây là cùng giá trị với literal ngắn `0x1p-1074`. Chữ `0` dẫn trước dấu chấm biểu thị subnormal khi API định dạng theo exponent thấp nhất của normal.

## 12. Ví dụ Java 17+ biên dịch và chạy độc lập

Tạo tệp **`FloatingPointLimitsDemo.java`**:

```java
public class FloatingPointLimitsDemo {
    public static void main(String[] args) {
        System.out.println("Float.MIN_VALUE = " + Float.MIN_VALUE);
        System.out.println("Float.MIN_NORMAL = " + Float.MIN_NORMAL);
        System.out.println("Float.MAX_VALUE = " + Float.MAX_VALUE);

        System.out.println("Double.MIN_VALUE = " + Double.MIN_VALUE);
        System.out.println("Double.MIN_NORMAL = " + Double.MIN_NORMAL);
        System.out.println("Double.MAX_VALUE = " + Double.MAX_VALUE);

        System.out.println("Double.MIN_VALUE > 0 = "
                + (Double.MIN_VALUE > 0.0));
        System.out.println("Double.MIN_VALUE < Double.MIN_NORMAL = "
                + (Double.MIN_VALUE < Double.MIN_NORMAL));
        System.out.println("-MAX finite = "
                + Double.isFinite(-Double.MAX_VALUE));

        double subnormal = Double.MIN_NORMAL / 2.0;
        double zero = Double.MIN_VALUE / 2.0;
        double infinity = Double.MAX_VALUE * 2.0;

        System.out.println("normal / 2 is subnormal = "
                + (subnormal > 0.0 && subnormal < Double.MIN_NORMAL));
        System.out.println("MIN_VALUE / 2 = " + zero);
        System.out.println("MAX_VALUE * 2 = " + infinity);
        System.out.println("nextUp(0) = " + Math.nextUp(0.0));
        System.out.println("nextUp(MAX_VALUE) = "
                + Math.nextUp(Double.MAX_VALUE));

        System.out.println("float raw MIN_NORMAL = "
                + String.format("%08x",
                        Float.floatToRawIntBits(Float.MIN_NORMAL)));
        System.out.println("double raw MIN_VALUE = "
                + String.format("%016x",
                        Double.doubleToRawLongBits(Double.MIN_VALUE)));
        System.out.println("double MIN_VALUE hex = "
                + Double.toHexString(Double.MIN_VALUE));

        System.out.println("+0 == -0 = " + (0.0 == -0.0));
        System.out.println("1 / -0 = " + (1.0 / -0.0));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingPointLimitsDemo.java
java FloatingPointLimitsDemo
```

**Stdout dự kiến trong Java 17+:**

```text
Float.MIN_VALUE = 1.4E-45
Float.MIN_NORMAL = 1.17549435E-38
Float.MAX_VALUE = 3.4028235E38
Double.MIN_VALUE = 4.9E-324
Double.MIN_NORMAL = 2.2250738585072014E-308
Double.MAX_VALUE = 1.7976931348623157E308
Double.MIN_VALUE > 0 = true
Double.MIN_VALUE < Double.MIN_NORMAL = true
-MAX finite = true
normal / 2 is subnormal = true
MIN_VALUE / 2 = 0.0
MAX_VALUE * 2 = Infinity
nextUp(0) = 4.9E-324
nextUp(MAX_VALUE) = Infinity
float raw MIN_NORMAL = 00800000
double raw MIN_VALUE = 0000000000000001
double MIN_VALUE hex = 0x0.0000000000001p-1022
+0 == -0 = true
1 / -0 = -Infinity
```

Điểm quan sát:

1. Cả `Float.MIN_VALUE` và `Double.MIN_VALUE` đều dương, rất nhỏ và **nhỏ hơn `MIN_NORMAL`**.
2. Chia `MIN_NORMAL` cho 2 vẫn còn một số dương hữu hạn, nằm trong miền subnormal.
3. Chia `MIN_VALUE` cho 2 tạo số 0 vì underflow/làm tròn.
4. Nhân `MAX_VALUE` với 2 thành vô cực vì overflow.
5. Các hex raw bits xác nhận khác biệt exponent/fraction; `toHexString` cho biểu diễn giá trị dấu phẩy động.

## 13. Nhầm lẫn phổ biến

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`Double.MIN_VALUE` là số âm nhỏ nhất” | Đây là số **dương khác 0 nhỏ nhất**; số âm hữu hạn có trị tuyệt đối lớn nhất là `-Double.MAX_VALUE` |
| “`MIN_NORMAL` và `MIN_VALUE` giống nhau” | `MIN_VALUE` là subnormal nhỏ nhất; `MIN_NORMAL` là normal nhỏ nhất |
| “Nhỏ hơn `MIN_NORMAL` là số 0” | Vẫn có nhiều số subnormal khác 0 |
| “Mọi giá trị nhỏ hơn `MIN_VALUE` đều bị từ chối khi chạy” | Phép toán có thể underflow và làm tròn thành 0; mặc định không ném ngoại lệ |
| “`MAX_VALUE` là Infinity” | `MAX_VALUE` còn hữu hạn; phép toán vượt biên có thể thành Infinity |
| “`MIN_VALUE` là epsilon để so sánh xấp xỉ” | Đây chỉ là độ lớn subnormal nhỏ nhất, quá nhỏ cho nhiều so sánh thực tế |
| “Toán tử `==` phân biệt `+0.0` và `-0.0`” | `+0.0 == -0.0` là true, dù raw bits và phép chia cho zero có thể khác |
| “`Double.toHexString` trả raw bits” | Nó trả phần trị hex và số mũ nhị phân của **giá trị**, khác bit field |
| “Subnormal giữ độ chính xác tương đối giống normal” | Số bit có nghĩa hiệu dụng giảm khi tiến sát 0 |
| “Dùng `Double.MIN_VALUE` để từ chối NaN” | NaN cần `Double.isNaN`; dùng `Double.isFinite` khi cần số hữu hạn |
| “`Float.MIN_VALUE` luôn nhỏ hơn `Double.MIN_VALUE` vì float ít bit hơn” | Ngược lại về **độ lớn**, `Float.MIN_VALUE` lớn hơn rất nhiều so với `Double.MIN_VALUE` |

## 14. Bài tập có lời giải

**Bài 1.** Hằng nào nhỏ hơn 0: `Double.MIN_VALUE` hay `-Double.MAX_VALUE`?  
**Lời giải:** chỉ `-Double.MAX_VALUE` nhỏ hơn 0. `Double.MIN_VALUE > 0`.

**Bài 2.** `Double.MIN_NORMAL / 2.0` có bằng 0 không?  
**Lời giải:** không; đây là một số dương **subnormal** biểu diễn được, nhỏ hơn `MIN_NORMAL`.

**Bài 3.** Viết dạng lũy thừa của 2 cho `Float.MIN_VALUE` và `Float.MIN_NORMAL`.  
**Lời giải:** lần lượt `2^-149` và `2^-126`. Chúng cách nhau rất nhiều bậc lũy thừa dù đều nhỏ.

**Bài 4.** Giá trị của `Math.nextUp(0.0)` là gì?  
**Lời giải:** `Double.MIN_VALUE`, số dương có thể biểu diễn ngay sau 0.

**Bài 5.** `Double.MAX_VALUE * 2.0` in gì?  
**Lời giải:** `Infinity` do overflow, không phải exception.

**Bài 6.** `Double.MIN_VALUE / 2.0` ra gì?  
**Lời giải:** `+0.0` vì kết quả quá nhỏ để biểu diễn dưới mặc định làm tròn về gần nhất; ngay tại nửa bước nhỏ nhất, quy tắc *ties to even* chọn 0.

**Bài 7.** Giá trị `double x = 1e-320;` là normal hay subnormal?  
**Lời giải:** là **subnormal dương**: vẫn lớn hơn `Double.MIN_VALUE` nhưng nhỏ hơn `Double.MIN_NORMAL ≈ 2.225e-308`.

**Bài 8.** `Float.MIN_VALUE` và `Double.MIN_VALUE`: hằng nào biểu diễn giá trị nhỏ hơn về độ lớn?  
**Lời giải:** `Double.MIN_VALUE` nhỏ hơn vì `2^-1074 < 2^-149`. Nhưng cả hai đều là những giá trị dương khác 0 nhỏ nhất **trong kiểu tương ứng**.

## 15. Checklist khi xử lý biên dấu phẩy động

1. **Chọn đúng khái niệm:** đang cần số dương khác 0 nhỏ nhất (`MIN_VALUE`), số normal nhỏ nhất (`MIN_NORMAL`) hay số hữu hạn lớn nhất (`MAX_VALUE`)?
2. **Không nhầm với số nguyên:** `Integer.MIN_VALUE` là âm, còn `Double.MIN_VALUE` là dương.
3. **Kiểm tra miền hữu hạn:** dùng `Double.isFinite(x)` hoặc `Float.isFinite(x)` nếu không muốn chấp nhận NaN/vô cực.
4. **Chú ý underflow:** phép tính có thể thành subnormal rồi ±0, không tự ném lỗi.
5. **Chú ý overflow:** phép toán có thể trả ±Infinity; kiểm tra kết quả nếu điều đó làm hỏng nghiệp vụ.
6. **Xác định nhu cầu chính xác:** subnormal có ít bit có nghĩa tương đối hơn; `Math.ulp`/`Math.nextUp` hữu ích để hiểu bước biểu diễn.
7. **Thiết kế so sánh:** không dùng `MIN_VALUE` làm epsilon chung; chọn dung sai từ bản chất dữ liệu.
8. **Nếu cần decimal exact:** `BigDecimal` từ chuỗi và quy tắc scale/rounding là một bài toán khác với miền IEEE 754 của `float`/`double`.

**Ghi nhớ:** Trong dấu phẩy động Java, `MIN_VALUE` là **số dương subnormal nhỏ nhất**, `MIN_NORMAL` là **số dương normal nhỏ nhất**, và `MAX_VALUE` là **số dương hữu hạn lớn nhất**. Ba hằng này mô tả ba biên khác nhau của biểu diễn IEEE 754; hiểu đúng chúng giúp kiểm soát underflow, overflow và độ chính xác gần số 0.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-008">Số 0 dương, số 0 âm, vô cực và NaN</a>

<details>
<summary>Xem chi tiết</summary>

# Số 0 dương, số 0 âm, vô cực và NaN trong Java

Trong Java, `float` và `double` không chỉ biểu diễn các số thực hữu hạn thông thường. Theo cơ chế dấu phẩy động nhị phân IEEE 754, chúng còn biểu diễn được **hai số 0 có dấu** (`+0.0`, `-0.0`), **vô cực dương/âm** (`+Infinity`, `-Infinity`) và **NaN** (*Not a Number*).

Các giá trị này không phải ngoại lệ Java và cũng không nhất thiết báo lỗi khi xuất hiện. Ví dụ:

```java
double positiveZero = 0.0;
double negativeZero = -0.0;
System.out.println(positiveZero == negativeZero); // true

System.out.println(1.0 / positiveZero); // Infinity
System.out.println(1.0 / negativeZero); // -Infinity
System.out.println(0.0 / 0.0);          // NaN
```

Ba phép chia dấu phẩy động trên **không ném `ArithmeticException`**. Nếu chương trình xử lý số đo, mô phỏng, thống kê, sắp xếp, lưu dữ liệu hoặc gửi JSON mà không xét tới giá trị đặc biệt, kết quả có thể sai âm thầm.

Bài học thuộc phần **Biểu diễn dấu phẩy động** của **Java core – numbers**, giúp giải thích biểu diễn và phép toán của các giá trị này, phân biệt `==` với `Double.compare`/`Double.equals`, và đưa ra cách kiểm tra dữ liệu thực tế.

## 1. Vì sao một kiểu số có nhiều “giá trị đặc biệt”?

`float` (32 bit) và `double` (64 bit) sử dụng định dạng dấu phẩy động nhị phân. Với giá trị hữu hạn, cách biểu diễn có thành phần dấu, số mũ và phần trị. Một số tổ hợp bit được dành riêng cho **zero**, **infinity** và **NaN**.

| Đặc điểm | `float` | `double` |
| --- | --- | --- |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit số mũ | 8 | 11 |
| Bit phần trị lưu trữ | 23 | 52 |
| Độ chính xác phần trị với bit ẩn | 24 bit | 53 bit |
| Hai dạng số 0 | `+0.0f`, `-0.0f` | `+0.0d`, `-0.0d` |
| Vô cực | `Float.POSITIVE_INFINITY`, `Float.NEGATIVE_INFINITY` | `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` |
| NaN | `Float.NaN` | `Double.NaN` |

Khi một số hữu hạn bị tràn về độ lớn của định dạng dấu phẩy động, kết quả theo phép toán thông thường có thể trở thành vô cực. Khi một phép toán không có kết quả số thực có nghĩa trong quy tắc IEEE 754, nó có thể tạo NaN. Khi kết quả quá nhỏ, nó có thể thành số rất nhỏ, **subnormal**, hoặc thành số 0 có dấu tùy độ lớn và cách làm tròn.

Không nên nhầm với số nguyên Java: `int`/`long` **không có** `NaN`, `Infinity` hoặc hai số 0 khác dấu. Tràn số nguyên và chia nguyên cho 0 tuân theo quy tắc khác.

## 2. Số 0 dương (+0.0) và số 0 âm (-0.0)

Trong toán học thông thường, 0 không có hai giá trị khác nhau. Ở IEEE 754, `+0.0` và `-0.0` có **hai mã bit riêng**:

```text
double +0.0 = 0x0000000000000000
double -0.0 = 0x8000000000000000

float  +0.0 = 0x00000000
float  -0.0 = 0x80000000
```

Khác biệt nằm ở bit dấu. Vì phần trị và số mũ đều biểu diễn zero, hai giá trị cùng được xem là bằng 0 trong phép so sánh số học `==`, nhưng **dấu của zero vẫn có thể ảnh hưởng phép toán tiếp theo**.

```java
double plusZero = 0.0;
double minusZero = -0.0;

System.out.println(plusZero == minusZero); // true
System.out.println(plusZero < minusZero);  // false
System.out.println(minusZero < plusZero);  // false
```

Với toán tử so sánh số học, `+0.0` và `-0.0` có cùng vị trí trên trục số. Nhưng Java vẫn giữ được dấu, cho phép nhận biết qua phép chia hoặc bit biểu diễn.

### 2.1. Hai phép chia cho zero cho kết quả khác dấu

```java
double plus = 1.0 / +0.0;  // Infinity
double minus = 1.0 / -0.0; // -Infinity
```

Dấu của kết quả tuân theo dấu toán hạng trong phép chia dấu phẩy động. Tương tự:

```java
double negativeOverPositive = -1.0 / +0.0; // -Infinity
double negativeOverNegative = -1.0 / -0.0; // Infinity
```

Do vậy một kiểm tra `value == 0.0` **không phân biệt được dấu** nhưng cả hai zero có thể dẫn tới hành vi khác ở phép toán sau.

### 2.2. Nhận biết zero âm

Một cách minh họa là đọc bit bằng `Double.doubleToRawLongBits`:

```java
double z = -0.0;
boolean negativeZero =
        z == 0.0 && Double.doubleToRawLongBits(z) < 0;
// true vì bit dấu ở bit cao nhất.
```

Nếu cần kiểm tra cả `float`, có thể dùng `Float.floatToRawIntBits`. So sánh `1.0 / z` với `Double.NEGATIVE_INFINITY` cũng phân biệt được zero âm, nhưng dùng bit trực tiếp thường thể hiện ý định rõ hơn khi thật sự phải kiểm tra dấu zero.

Không lấy `-0.0 == 0.0` làm bằng chứng rằng mã bit giống nhau. Đừng cho rằng chuyển qua một định dạng văn bản hoặc xử lý dữ liệu trung gian luôn bảo toàn dấu zero: cần kiểm chứng hợp đồng thư viện và giao thức.

### 2.3. Math.abs, signum, copySign, min/max

Một số API phân biệt hoặc giữ dấu zero:

```java
Math.abs(-0.0);            // +0.0
Math.signum(-0.0);         // -0.0
Math.copySign(1.0, -0.0);  // -1.0
Math.min(+0.0, -0.0);      // -0.0
Math.max(+0.0, -0.0);      // +0.0
```

`Math.min` và `Math.max` có quy tắc riêng với signed zero: `-0.0` được coi là nhỏ hơn `+0.0` khi chọn kết quả. Đây là lý do những hàm này **không hoàn toàn tương đương** với một biểu thức điều kiện tự viết chỉ dùng `<` hoặc `>` nếu cần bảo toàn dấu zero.

**Lưu ý:** giá trị `Math.signum(-0.0)` in ra là `-0.0`, không phải `-1.0`; `signum` trả lại zero có dấu đối với đầu vào zero.

## 3. Vô cực dương và vô cực âm

Java định nghĩa các hằng:

```java
double positive = Double.POSITIVE_INFINITY;
double negative = Double.NEGATIVE_INFINITY;
float positiveFloat = Float.POSITIVE_INFINITY;
float negativeFloat = Float.NEGATIVE_INFINITY;
```

Vô cực có thể xuất hiện từ việc chia một số hữu hạn khác 0 cho zero hoặc từ phép tính thực có kết quả quá lớn để biểu diễn:

```java
double a = 1.0 / 0.0;               // Infinity
double b = -1.0 / 0.0;              // -Infinity
double c = Double.MAX_VALUE * 2.0; // Infinity
```

Các giá trị vô cực không phải là “số lớn nhất có thể biểu diễn”. `Double.MAX_VALUE` là **số hữu hạn dương lớn nhất**, còn `Double.POSITIVE_INFINITY` là một giá trị đặc biệt lớn hơn mọi giá trị hữu hạn trong các phép so sánh thông thường.

```java
Double.MAX_VALUE < Double.POSITIVE_INFINITY; // true
Double.NEGATIVE_INFINITY < -Double.MAX_VALUE; // true
```

Một số phép toán với vô cực:

| Biểu thức | Kết quả | Ý nghĩa |
| --- | --- | --- |
| `1.0 / 0.0` | `Infinity` | Số dương hữu hạn chia cho zero dương |
| `-1.0 / 0.0` | `-Infinity` | Số âm hữu hạn chia cho zero dương |
| `Double.MAX_VALUE * 2.0` | `Infinity` | Tràn miền giá trị hữu hạn |
| `1.0 / Double.POSITIVE_INFINITY` | `+0.0` | Kết quả tiến về zero dương |
| `-1.0 / Double.POSITIVE_INFINITY` | `-0.0` | Kết quả tiến về zero âm |
| `Double.POSITIVE_INFINITY + 10.0` | `Infinity` | Vô cực cộng số hữu hạn |
| `Double.POSITIVE_INFINITY + Double.NEGATIVE_INFINITY` | `NaN` | Hai vô cực khác dấu cộng nhau |
| `Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY` | `NaN` | Dạng không xác định theo phép toán IEEE 754 |

Các biểu thức đó không tự tạo `ArithmeticException` chỉ vì xuất hiện `Infinity` hoặc `NaN`.

### 3.1. So sánh với vô cực

`Infinity` so sánh có thứ tự với các số hữu hạn:

```java
double x = Double.POSITIVE_INFINITY;

x > 1e300;                           // true
x == Double.POSITIVE_INFINITY;       // true
Double.NEGATIVE_INFINITY < -1e300;   // true
```

Tuy nhiên, đừng dùng vô cực như một giá trị mặc định “không có dữ liệu” trừ khi đặc tả API đã xác định rõ. Một phép tổng hợp, trung bình hoặc chuẩn hóa có thể vô tình truyền vô cực vào kết quả cuối.

## 4. NaN: giá trị biểu thị phép toán không có kết quả số thực xác định

`NaN` viết tắt của *Not a Number*. Nó là giá trị dấu phẩy động đặc biệt, có thể sinh ra bởi những phép toán như:

```java
double a = 0.0 / 0.0;           // NaN
double b = Math.sqrt(-1.0);     // NaN
double c = Double.POSITIVE_INFINITY
        - Double.POSITIVE_INFINITY; // NaN
double d = Double.NaN;
```

Không nên hiểu `NaN` chỉ là “lỗi cú pháp” hoặc một exception. Một khi NaN tham gia phép toán dấu phẩy động, kết quả của nhiều phép số học khác tiếp tục là NaN:

```java
double unknown = Double.NaN;
double result = unknown + 100.0; // NaN
```

Vì vậy, NaN có thể lan theo dòng xử lý dữ liệu. Nếu chương trình lưu nó như một phép đo hợp lệ, các phép thống kê hoặc mô hình dựa trên kết quả có thể trở nên khó kiểm soát.

### 4.1. Tại sao NaN != NaN?

Theo quy tắc so sánh số học của Java:

```java
double x = Double.NaN;

System.out.println(x == x); // false
System.out.println(x != x); // true
System.out.println(x < 1.0);  // false
System.out.println(x > 1.0);  // false
System.out.println(x <= 1.0); // false
System.out.println(x >= 1.0); // false
```

Mọi phép so sánh thứ tự với NaN bằng `<`, `>`, `<=`, `>=` đều trả `false`. `==` với NaN cũng trả `false`, kể cả khi so một biến với chính nó; `!=` trả `true`.

Bởi vậy, biểu thức `x != x` có thể phát hiện NaN về mặt kỹ thuật, nhưng **nên dùng `Double.isNaN(x)` hoặc `Float.isNaN(x)`** cho rõ ý định và tránh làm người đọc hiểu nhầm.

### 4.2. NaN làm hỏng kiểm tra khoảng

Giả sử hệ thống chấp nhận nhiệt độ trong khoảng từ -50 đến 150:

```java
static boolean badValidation(double temperature) {
    return !(temperature < -50.0 || temperature > 150.0);
}
```

Với `temperature = Double.NaN`, cả hai biểu thức `<` và `>` đều là `false`; phép phủ định khiến hàm **trả `true`** dù NaN không phải nhiệt độ hợp lệ.

Cách kiểm tra đúng khi chỉ chấp nhận giá trị hữu hạn:

```java
static boolean validTemperature(double value) {
    return Double.isFinite(value)
            && value >= -50.0
            && value <= 150.0;
}
```

Hàm kiểm tra này loại trừ cả `NaN`, `+Infinity` và `-Infinity`; sau đó mới kiểm tra khoảng. Nếu miền dữ liệu cho phép vô cực hoặc NaN có ý nghĩa riêng, phải thiết kế một nhánh xử lý tường minh, không vô tình coi chúng là số đo bình thường.

## 5. Các API kiểm tra NaN, vô cực và tính hữu hạn

Java có các phương thức kiểm tra tường minh:

| Phương thức | Trả `true` khi nào? |
| --- | --- |
| `Double.isNaN(value)` | Giá trị là NaN |
| `Double.isInfinite(value)` | Giá trị là vô cực dương hoặc âm |
| `Double.isFinite(value)` | Giá trị không phải NaN và không phải vô cực |
| `Float.isNaN(value)` | Giá trị `float` là NaN |
| `Float.isInfinite(value)` | Giá trị `float` là vô cực |
| `Float.isFinite(value)` | Giá trị `float` hữu hạn |

Ví dụ:

```java
Double.isFinite(0.0);                 // true
Double.isFinite(-0.0);                // true
Double.isFinite(Double.NaN);         // false
Double.isFinite(Double.POSITIVE_INFINITY); // false

Double.isInfinite(-1.0 / 0.0);       // true
Double.isNaN(0.0 / 0.0);             // true
```

**Signed zero vẫn là giá trị hữu hạn.** Nếu ứng dụng có yêu cầu riêng “không được có zero âm”, chỉ kiểm tra `isFinite` là chưa đủ; phải phát hiện bit dấu zero hoặc chuẩn hóa có chủ đích.

Trong lớp nhận dữ liệu/API, quy tắc đơn giản và hữu ích là **kiểm tra `isFinite` trước khi tính toán** khi ngữ nghĩa đầu vào là một số đo hữu hạn.

## 6. Toán tử ==, Double.compare và Double.equals không giống nhau

Đây là một trong những bẫy dễ gây lỗi nhất.

### 6.1. Toán tử == trên double

```java
+0.0 == -0.0;             // true
Double.NaN == Double.NaN; // false
```

`==` áp dụng so sánh số học của dấu phẩy động, không so bit và không định nghĩa thứ tự tổng quát cho NaN.

### 6.2. Double.compare cung cấp thứ tự dùng để sắp xếp

`Double.compare(a, b)` định nghĩa cách so sánh có thứ tự phù hợp cho các trường hợp đặc biệt. Trong đó:

- `Double.compare(-0.0, +0.0) < 0`: zero âm được xếp trước zero dương.
- `Double.compare(Double.NaN, Double.NaN) == 0`: hai giá trị NaN được xem là bằng nhau trong trật tự so sánh này.
- NaN được xem là **lớn hơn** `Double.POSITIVE_INFINITY` theo thứ tự của `Double.compare`, không có nghĩa NaN là một đại lượng số học lớn hơn vô cực.

```java
Double.compare(-0.0, +0.0); // Giá trị âm (cụ thể -1).
Double.compare(Double.NaN, Double.NaN); // 0
Double.compare(Double.NaN, Double.POSITIVE_INFINITY); // Dương.
```

`Float.compare` có cách xử lý tương ứng cho `float`. Các API sắp xếp/hộp wrapper có thể sử dụng cách so sánh này để có trật tự xác định cho dữ liệu chứa NaN và signed zero.

### 6.3. Double.equals so nội dung theo quy tắc wrapper

```java
Double.valueOf(-0.0).equals(Double.valueOf(+0.0)); // false
Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN)); // true
```

Điều này có thể gây ngạc nhiên:

| So sánh | Kết quả |
| --- | --- |
| `+0.0 == -0.0` | `true` |
| `Double.valueOf(+0.0).equals(Double.valueOf(-0.0))` | `false` |
| `Double.compare(-0.0, +0.0) < 0` | `true` |
| `Double.NaN == Double.NaN` | `false` |
| `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` | `true` |
| `Double.compare(Double.NaN, Double.NaN) == 0` | `true` |

Wrapper `Double.equals` dùng quy tắc dựa trên `Double.doubleToLongBits`: **phân biệt zero có dấu và chuẩn hóa các NaN cho so sánh bằng nhau**. Vì `equals` và `hashCode` của wrapper phải nhất quán, một `HashSet<Double>` có thể coi `+0.0` và `-0.0` là hai phần tử khác nhau, trong khi `==` giữa hai primitive zero trả true.

**Cách chọn:** sử dụng `==` khi thật sự muốn so sánh số học; `isNaN` để phát hiện NaN; `Double.compare` khi muốn thứ tự sắp xếp xác định; `equals` khi làm việc theo hợp đồng đối tượng của wrapper. Không coi các phép so sánh này là thay thế cho **so gần đúng theo sai số nghiệp vụ**.

## 7. Các phép toán hay tạo giá trị đặc biệt

| Biểu thức `double` | Kết quả | Bẫy thường gặp |
| --- | --- | --- |
| `1.0 / +0.0` | `Infinity` | Không có exception |
| `1.0 / -0.0` | `-Infinity` | Dấu zero ảnh hưởng dấu vô cực |
| `-1.0 / -0.0` | `Infinity` | Hai dấu âm triệt tiêu |
| `0.0 / 0.0` | `NaN` | Không phải 0 |
| `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` | `NaN` | Dạng không xác định |
| `0.0 * Double.POSITIVE_INFINITY` | `NaN` | Không phải 0 |
| `Double.MAX_VALUE * 2.0` | `Infinity` | Tràn dấu phẩy động |
| `-1.0 / Double.POSITIVE_INFINITY` | `-0.0` | Underflow về zero có dấu |
| `Math.sqrt(-1.0)` | `NaN` | Đầu vào ngoài miền thực |
| `Double.NaN + 3.0` | `NaN` | NaN lan truyền |

### 7.1. So sánh với chia nguyên cho zero

```java
int denominator = 0;
// int q = 1 / denominator; // Nếu chạy: ArithmeticException.

double floating = 1.0 / denominator; // Infinity, không exception.
```

Ở dòng `double floating`, vì có toán hạng `1.0` kiểu `double`, Java nâng `denominator` sang `double` rồi thực hiện phép chia dấu phẩy động. Vì thế **một thay đổi nhỏ về kiểu toán hạng có thể thay đổi cả cơ chế lỗi**.

### 7.2. Phần dư trên số thực

Java hỗ trợ toán tử `%` cho `float`/`double`, nhưng nó không trùng với toàn bộ quy tắc exception của `%` số nguyên:

```java
double r = 1.0 % 0.0; // NaN
```

Trong khi `int r = 1 % 0;` nếu được đánh giá sẽ ném `ArithmeticException`, phép phần dư dấu phẩy động với số chia zero trả NaN. Không áp dụng máy móc suy luận từ phép `%` nguyên sang phép `%` thực.

## 8. Dấu của zero khi làm tròn, in ra và chuyển kiểu

### 8.1. In ra và chuyển sang String

```java
System.out.println(+0.0); // 0.0
System.out.println(-0.0); // -0.0
System.out.println(Double.POSITIVE_INFINITY); // Infinity
System.out.println(Double.NaN); // NaN
```

Cách in này là biểu diễn dạng chuỗi thông thường của Java. Nó giúp quan sát, nhưng **không chứng minh giao thức trao đổi khác** (ví dụ JSON hay database) sẽ giữ nguyên chuỗi hoặc giá trị.

### 8.2. Chuyển từ double sang int/long

Khi thu hẹp `double`/`float` sang số nguyên Java, phần thập phân bị bỏ theo quy tắc chuyển kiểu và các giá trị đặc biệt có xử lý riêng:

- `(int) Double.NaN` là `0`.
- `(int) Double.POSITIVE_INFINITY` là `Integer.MAX_VALUE`.
- `(int) Double.NEGATIVE_INFINITY` là `Integer.MIN_VALUE`.
- `(int) -0.0` là `0`, không còn bit dấu zero.

```java
int fromNaN = (int) Double.NaN; // 0
int fromInfinity = (int) Double.POSITIVE_INFINITY; // 2147483647
int fromNegativeZero = (int) -0.0; // 0
```

Những phép cast này **không tự báo lỗi**. Nếu đầu vào của ứng dụng không được phép là NaN/vô cực, kiểm tra `Double.isFinite` trước khi cast; sau đó kiểm tra miền `int`/`long` và yêu cầu làm tròn riêng. Đặc biệt, phép cast đưa NaN thành 0 có thể che mất dữ liệu lỗi và khiến hệ thống xem một giá trị không hợp lệ như giá trị hợp lệ.

### 8.3. Negative zero có thể xuất hiện sau khi kết quả rất nhỏ

```java
double x = -Double.MIN_VALUE;
double y = x / 2.0;
System.out.println(y); // -0.0
```

`Double.MIN_VALUE` là **số double dương nhỏ nhất khác 0**, không phải số âm thấp nhất như `Integer.MIN_VALUE`. Lấy âm của nó rồi chia đôi khiến độ lớn nhỏ hơn mức subnormal nhỏ nhất có thể biểu diễn; kết quả tròn về zero âm.

Điều này thể hiện vì sao signed zero không chỉ do lập trình viên viết `-0.0` trực tiếp; nó có thể được tạo ra bởi phép toán số thực hợp lệ.

## 9. Truyền dữ liệu và chuẩn hóa: không âm thầm chấp nhận NaN hoặc Infinity

### 9.1. Dữ liệu nhập có thể chứa chuỗi đặc biệt

Một số hàm Java cho phép chuyển chuỗi đặc biệt thành giá trị `double`:

```java
Double.parseDouble("NaN");       // NaN
Double.parseDouble("Infinity");  // Infinity
Double.parseDouble("-Infinity"); // -Infinity
```

Nếu API đầu vào kỳ vọng “số hữu hạn”, chỉ gọi `Double.parseDouble` chưa đủ. Sau parse phải kiểm tra `Double.isFinite` và các ràng buộc miền.

Ví dụ:

```java
static double parseFinite(String text) {
    double value = Double.parseDouble(text);
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Finite number required");
    }
    return value;
}
```

Hàm này vẫn có thể nhận `-0.0`, vì nó là giá trị hữu hạn. Nếu ứng dụng cần cấm hoặc chuẩn hóa zero âm, xử lý như một **chính sách bổ sung**. Ngoài ra, `parseDouble` có thể ném `NumberFormatException` khi chuỗi không theo cú pháp số mà nó chấp nhận.

### 9.2. JSON tiêu chuẩn và API

Trong JSON chuẩn, ký hiệu trần `NaN`, `Infinity` và `-Infinity` **không phải các literal number hợp lệ**. Phản ứng của từng thư viện Java khi serialize giá trị đặc biệt có thể khác nhau: từ chối, thay bằng `null`, mã hóa thành chuỗi, hoặc sử dụng một định dạng mở rộng ngoài JSON chuẩn. **Không giả định** chúng tự truyền đi như number hợp lệ.

Nếu NaN/vô cực là trạng thái nghiệp vụ cần bảo toàn, nên thiết kế schema rõ ràng, ví dụ một trường `status` đi cùng giá trị tùy chọn, thay vì lén đưa các chuỗi đặc biệt vào một number field. Nếu chỉ chấp nhận số đo hữu hạn, validate trước khi serialize.

### 9.3. BigDecimal không hỗ trợ NaN và Infinity

`BigDecimal` biểu diễn số thập phân hữu hạn; nó không có giá trị NaN/vô cực tương ứng:

```java
// BigDecimal.valueOf(Double.NaN) hoặc
// BigDecimal.valueOf(Double.POSITIVE_INFINITY)
// ném NumberFormatException.
```

Không nên chuyển đổi dữ liệu `double` sang `BigDecimal` rồi kỳ vọng mọi giá trị đặc biệt được bảo toàn; kiểm tra hữu hạn trước. Nếu cần tiền hoặc số thập phân chính xác, ưu tiên tạo `BigDecimal` từ **chuỗi thập phân gốc** và áp dụng validation/rounding nghiệp vụ.

## 10. Khi nào cần giữ hoặc loại bỏ dấu zero?

Tùy ứng dụng:

- Với phép đo, mô phỏng và thuật toán IEEE 754, dấu zero có thể mang ý nghĩa của **hướng tiếp cận 0** hoặc dấu của kết quả rất nhỏ. Tự chuẩn hóa `-0.0` thành `+0.0` có thể thay đổi kết quả phép chia kế tiếp.
- Với giao diện người dùng hoặc báo cáo, `-0.0` có thể gây khó hiểu khi nó chỉ là kết quả làm tròn một giá trị âm rất gần 0. Có thể chọn hiển thị `0.0`, nhưng cần tách **cách hiển thị** khỏi dữ liệu gốc.
- Với khóa dữ liệu và so sánh đối tượng, `Double.equals` phân biệt `-0.0`/`+0.0`, nên một `Set<Double>` hoặc khóa hash có thể coi chúng khác nhau. Nếu nghiệp vụ muốn chúng cùng một khóa, hãy chuẩn hóa rõ ràng **trước khi tạo khóa**.
- Với phép thống kê, nên quyết định chính sách với NaN: loại mẫu, đánh dấu dữ liệu thiếu hoặc dừng xử lý. Không cho rằng tổng/trung bình tự bỏ qua NaN.

Ví dụ hàm chuẩn hóa zero khi và chỉ khi hợp đồng miền chấp nhận việc mất dấu:

```java
static double normalizeZero(double value) {
    return value == 0.0 ? 0.0 : value;
}
```

Hàm giữ NaN và vô cực nguyên trạng; nếu mục tiêu còn bao gồm loại bỏ chúng, phải kết hợp `Double.isFinite`. Nó cố ý biến `-0.0` thành `+0.0`. **Không áp dụng mặc định** cho một thuật toán số cần giữ hướng dấu.

## 11. Chương trình Java 17+ chạy độc lập

Lưu file **`FloatingSpecialValuesDemo.java`**. Chương trình dùng `println` thay vì định dạng phụ thuộc locale, và tách rõ zero có dấu, vô cực, NaN, so sánh wrapper, kiểm tra dữ liệu, cast và underflow thành zero âm.

```java
public class FloatingSpecialValuesDemo {
    static boolean validTemperature(double value) {
        return Double.isFinite(value)
                && value >= -50.0
                && value <= 150.0;
    }

    public static void main(String[] args) {
        double positiveZero = 0.0;
        double negativeZero = -0.0;

        System.out.println("zero-equal=" + (positiveZero == negativeZero));
        System.out.println("positive-zero-bits=" +
                Long.toHexString(Double.doubleToRawLongBits(positiveZero)));
        System.out.println("negative-zero-bits=" +
                Long.toHexString(Double.doubleToRawLongBits(negativeZero)));
        System.out.println("positive-reciprocal=" + (1.0 / positiveZero));
        System.out.println("negative-reciprocal=" + (1.0 / negativeZero));
        System.out.println("min-zero=" + Math.min(positiveZero, negativeZero));
        System.out.println("max-zero=" + Math.max(positiveZero, negativeZero));
        System.out.println("signum-negative-zero=" + Math.signum(negativeZero));

        double infinity = 1.0 / positiveZero;
        double nan = 0.0 / 0.0;
        System.out.println("infinity=" + infinity);
        System.out.println("opposite-infinities=" +
                (infinity + Double.NEGATIVE_INFINITY));
        System.out.println("zero-over-zero=" + nan);
        System.out.println("nan-equal-self=" + (nan == nan));
        System.out.println("nan-is-nan=" + Double.isNaN(nan));
        System.out.println("infinity-is-finite=" + Double.isFinite(infinity));
        System.out.println("negative-zero-is-finite=" +
                Double.isFinite(negativeZero));

        System.out.println("compare-zero=" +
                Double.compare(negativeZero, positiveZero));
        System.out.println("boxed-zero-equal=" +
                Double.valueOf(negativeZero).equals(Double.valueOf(positiveZero)));
        System.out.println("boxed-nan-equal=" +
                Double.valueOf(nan).equals(Double.valueOf(Double.NaN)));

        System.out.println("nan-temperature-valid=" + validTemperature(nan));
        System.out.println("normal-temperature-valid=" +
                validTemperature(25.0));
        System.out.println("cast-nan=" + (int) nan);
        System.out.println("cast-infinity=" + (int) infinity);

        double tinyNegative = -Double.MIN_VALUE;
        System.out.println("tiny-negative-half=" + (tinyNegative / 2.0));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac FloatingSpecialValuesDemo.java
java FloatingSpecialValuesDemo
```

Stdout dự kiến:

```text
zero-equal=true
positive-zero-bits=0
negative-zero-bits=8000000000000000
positive-reciprocal=Infinity
negative-reciprocal=-Infinity
min-zero=-0.0
max-zero=0.0
signum-negative-zero=-0.0
infinity=Infinity
opposite-infinities=NaN
zero-over-zero=NaN
nan-equal-self=false
nan-is-nan=true
infinity-is-finite=false
negative-zero-is-finite=true
compare-zero=-1
boxed-zero-equal=false
boxed-nan-equal=true
nan-temperature-valid=false
normal-temperature-valid=true
cast-nan=0
cast-infinity=2147483647
tiny-negative-half=-0.0
```

Đọc kết quả theo từng nhóm:

1. **Hai zero bằng nhau qua `==` nhưng khác bit.** Phép chia cho chúng cho hai vô cực khác dấu; `Math.min` và `Math.max` giữ quy tắc chọn zero có dấu.
2. **Vô cực và NaN không phải exception.** Cộng hai vô cực trái dấu ra NaN; NaN không bằng chính nó qua `==`.
3. **Đổi API so sánh đổi semantics.** `Double.compare(-0.0,+0.0)` âm; boxed `Double.equals` phân biệt hai zero nhưng xem hai NaN bằng nhau.
4. **Kiểm tra dữ liệu phải tường minh.** `Double.isFinite` loại bỏ NaN/vô cực, trong khi cast NaN về `int` trả 0 và có thể che giấu dữ liệu không hợp lệ.
5. **Số âm rất nhỏ có thể thành zero âm.** Dòng cuối không phải giá trị 0 âm được viết trực tiếp mà là kết quả underflow.

## 12. Kiểm thử và lỗi thường gặp

| Giả định sai | Vì sao nguy hiểm? | Cách kiểm tra/phòng tránh |
| --- | --- | --- |
| “Zero chỉ có một biểu diễn.” | `+0.0` và `-0.0` khác bit và cho phép chia khác dấu | Dùng bit, `Double.compare` hoặc chính sách chuẩn hóa rõ ràng khi cần |
| “`x == 0.0` nhận biết zero âm.” | Cả `+0.0` và `-0.0` đều bằng 0.0 | Dùng `Double.doubleToRawLongBits` nếu cần dấu zero |
| “Chia số thực cho 0 sẽ ném ArithmeticException.” | Có thể nhận vô cực hoặc NaN | Validate đầu vào và kết quả hữu hạn |
| “NaN có thể kiểm tra bằng `x == Double.NaN`.” | `NaN == NaN` luôn false | Dùng `Double.isNaN(x)` |
| “Nếu `x` không nhỏ hơn hoặc lớn hơn giới hạn thì x hợp lệ.” | So sánh thứ tự với NaN đều false | Kiểm tra `Double.isFinite` và điều kiện miền |
| “`Double.equals` giống `==`.” | `equals` phân biệt signed zero nhưng coi NaN bằng nhau | Chọn API theo nghĩa cần dùng |
| “Vô cực chỉ là số hữu hạn rất lớn.” | Vô cực là giá trị đặc biệt | `Double.isInfinite` và `Double.isFinite` |
| “`Double.MAX_VALUE` là infinity.” | Đây là số hữu hạn dương lớn nhất | Dùng `Double.POSITIVE_INFINITY` khi thực sự cần vô cực |
| “Cast NaN sang int phải ném lỗi.” | Cast cho kết quả 0 | Kiểm tra tính hữu hạn và miền trước cast |
| “JSON nào cũng lưu NaN/Infinity như number.” | JSON chuẩn không cho các literal đó | Thiết kế schema/serializer có xác thực |
| “`Double.MIN_VALUE` là số âm nhỏ nhất.” | Nó là số dương khác 0 nhỏ nhất | Phân biệt với `-Double.MAX_VALUE` hoặc `Double.NEGATIVE_INFINITY` |
| “Mọi zero có dấu có thể tự do gộp lại.” | Một số phép toán số học cần giữ dấu zero | Chuẩn hóa chỉ khi nghiệp vụ cho phép |

Các bộ test nên bao gồm `+0.0`, `-0.0`, `Double.MIN_VALUE`, `-Double.MIN_VALUE`, `Double.MAX_VALUE`, cả hai vô cực, NaN và các giá trị hữu hạn âm/dương. Khi thử API so sánh, hãy kiểm chứng cả `==`, `Double.compare` và `Double.equals` vì kết quả không giống nhau ở zero có dấu và NaN.

## 13. Bài tập vận dụng

**Bài 1 – Dự đoán kết quả.** Không chạy Java, tính `1.0 / +0.0`, `1.0 / -0.0`, `0.0 / 0.0`, `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY`. Phép nào ném exception, phép nào trả giá trị đặc biệt?

**Bài 2 – So sánh.** Cho `a = +0.0`, `b = -0.0`, `c = Double.NaN`. Lập bảng cho `a == b`, `Double.compare(a,b)`, `Double.valueOf(a).equals(Double.valueOf(b))`, `c == c`, `Double.isNaN(c)`. Giải thích mục đích của từng phép.

**Bài 3 – Validation.** Viết phương thức nhận chuỗi nhiệt độ và chỉ cho phép các giá trị hữu hạn trong `[-50,150]`. Thử `"25.5"`, `"NaN"`, `"Infinity"`, `"-Infinity"`, `"abc"`, `"-0.0"`. Chỉ rõ quy tắc ngoại lệ của parser và chính sách riêng với zero âm.

**Bài 4 – Bucket và key.** Một ứng dụng dùng `HashSet<Double>` để loại trùng kết quả đo. Dự đoán có bao nhiêu phần tử khi thêm `+0.0`, `-0.0`, `Double.NaN` hai lần. Vì sao `HashSet` không áp dụng trực tiếp `==` của primitive?

**Bài 5 – Dòng tính toán.** Phân tích đầu vào khiến một phép tính trả vô cực rồi dẫn tới NaN ở bước sau. Thiết kế cách quan sát và báo lỗi để người dùng không nhìn thấy NaN như một kết quả hợp lệ.

**Bài 6 – Underflow.** Tính `-Double.MIN_VALUE / 2.0`. Vì sao kết quả in ra là `-0.0` chứ không phải một số âm khác 0? Phép `1.0 / result` sẽ có giá trị gì?

**Bài 7 – JSON.** Đề xuất cấu trúc phản hồi API cho phép có giá trị đo hữu hạn hoặc trạng thái không tính được mà vẫn hợp lệ JSON chuẩn. Vì sao mã hóa trực tiếp number `NaN` không phải một hợp đồng an toàn?

## 14. Checklist dùng số thực an toàn

- Hiểu `float`/`double` có **hai zero có dấu, hai vô cực và NaN**, khác với kiểu nguyên.
- Nếu miền dữ liệu yêu cầu số hữu hạn, kiểm tra **`Float.isFinite`/`Double.isFinite`** trước phép tính quan trọng, trước khi cast và trước khi serialize.
- Dùng **`isNaN`** để phát hiện NaN; không dùng `== Double.NaN`.
- Phân biệt `==`, `Double.compare` và `Double.equals` đối với signed zero và NaN.
- Không mặc định mọi phép chia cho 0 đều ném exception; phụ thuộc vào **kiểu toán hạng**.
- Không lạm dụng cast `double → int/long` làm validation: NaN có thể thành 0, vô cực bị chặn về biên.
- Nếu cần bảo toàn dấu zero, không tự chuẩn hóa; nếu cần gộp hai zero trong khóa dữ liệu, hãy chuẩn hóa có chủ ý.
- Kiểm tra serializer/API vì **JSON chuẩn không cho NaN và Infinity dưới dạng number**.
- Với tiền và số thập phân cần chính xác, chọn kiểu và chính sách làm tròn theo nghiệp vụ, không coi NaN/vô cực như những giá trị `BigDecimal` thông thường.

**Kết luận:** `+0.0`, `-0.0`, vô cực và NaN là những **giá trị có hành vi được định nghĩa** trong dấu phẩy động Java, không phải tất cả đều là exception. Tính đúng đắn phụ thuộc vào việc hiểu quy tắc tính toán, lựa chọn phép so sánh phù hợp và xác thực rõ những giá trị được phép đi qua hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-009">ULP và khoảng cách giữa hai giá trị kề nhau</a>

<details>
<summary>Xem chi tiết</summary>

# ULP và khoảng cách giữa hai giá trị kề nhau trong Java

**ULP (Unit in the Last Place)** là đơn vị giúp mô tả **độ thưa của các số dấu phẩy động biểu diễn được** quanh một giá trị. Khác số nguyên, kiểu `float` và `double` không thể biểu diễn chính xác mọi số thực; chúng có một tập hữu hạn các mẫu bit. Khi trị tuyệt đối của số tăng, khoảng cách giữa các giá trị biểu diễn được gần nhau thường cũng tăng.

Ví dụ gây ngạc nhiên:

```java
double x = 1.0;
System.out.println(Math.nextUp(x));   // 1.0000000000000002
System.out.println(Math.ulp(x));      // 2.220446049250313E-16
```

Khi tiến từ `1.0` đến số `double` ngay phía trên, bước nhỏ nhất là khoảng `2.22 × 10^-16`. Nhưng ở quanh `10^16`, một đơn vị ULP đã là **2.0**: cộng thêm `1.0` có thể không tạo ra số `double` mới.

Điều quan trọng là phân biệt **khoảng cách của lưới biểu diễn**, **sai số sau khi tính toán**, và **độ chính xác thập phân mà con người mong muốn**. ULP mô tả lưới giá trị của kiểu dấu phẩy động, không tự động là một giới hạn sai số cho mọi thuật toán.

## 1. Vì sao số dấu phẩy động có các khoảng trống?

Java `float` là kiểu 32 bit và `double` là kiểu 64 bit theo chuẩn dấu phẩy động nhị phân IEEE 754, với các trường dấu, số mũ và phần trị (significand). Đối với các số hữu hạn **normal** (chuẩn hóa), giá trị gần như có cấu trúc:

```text
value = (-1)^sign × (1.fraction)_2 × 2^exponent
```

Các bit fraction là hữu hạn: `float` có **23 bit fraction được lưu** (24 bit significand hiệu dụng, tính cả bit 1 ẩn), còn `double` có **52 bit fraction được lưu** (53 bit significand hiệu dụng). Vì vậy, tại một khoảng số mũ cố định, chỉ có hữu hạn các mức fraction; giữa hai mức liên tiếp là một **khoảng trống**.

| Đặc tính | `float` | `double` |
| --- | --- | --- |
| Kích thước | 32 bit | 64 bit |
| Bit fraction lưu trữ | 23 | 52 |
| Số bit chính xác significand, gồm bit đầu ẩn ở normal | 24 | 53 |
| ULP tại `1.0` | `2^-23` ≈ `1.1920929E-7` | `2^-52` ≈ `2.220446049250313E-16` |
| Số nguyên liên tiếp biểu diễn chính xác đến ngưỡng | `2^24` | `2^53` |

Các con số này là **bit chính xác nhị phân**, không phải bảo đảm rằng một giá trị bất kỳ có chính xác 7 hoặc 16 chữ số thập phân. Chẳng hạn `0.1` không có biểu diễn hữu hạn trong hệ nhị phân và cần làm tròn khi chuyển thành `double`.

## 2. ULP là gì? Quan hệ với các số kề nhau

Ở một giá trị hữu hạn bình thường `x`, ULP là độ lớn của một bước biểu diễn tương ứng với vị trí bit cuối của phần trị tại cấp số mũ của `x`. Trong Java, `Math.ulp(x)` trả về giá trị ULP **không âm** cho `float` hoặc `double`, với quy tắc riêng ở zero, subnormal, vô cực và NaN.

Để quan sát các giá trị kề, Java cung cấp:

- `Math.nextUp(x)`: giá trị dấu phẩy động kế tiếp theo hướng **dương vô cực**.
- `Math.nextDown(x)`: giá trị kế tiếp theo hướng **âm vô cực**.
- `Math.nextAfter(start, direction)`: bước một giá trị biểu diễn được từ `start` theo hướng `direction`; dạng `double` trả `double`, dạng bắt đầu bằng `float` trả `float`.

Các phương thức này cho phép nhìn vào **những điểm trên lưới biểu diễn**, thay vì thử cộng một số `epsilon` tự chọn. Ví dụ, đặt `x = 1.0`:

```text
Math.nextDown(1.0) = 0.9999999999999999
                1.0
Math.nextUp(1.0)   = 1.0000000000000002
```

Tại đây khoảng cách phía **trên** là `2^-52`, nhưng khoảng cách phía **dưới** là `2^-53`. Đây không phải lỗi! `1.0` chính xác là một **lũy thừa của 2**, là ranh giới của hai khoảng có mật độ biểu diễn khác nhau. Bởi vậy không nên nói `Math.ulp(x)` luôn bằng **cả** `Math.nextUp(x)-x` và `x-Math.nextDown(x)`.

## 3. Công thức khoảng cách đối với số normal

Với `double` normal dương `x` trong khoảng lũy thừa hai:

```text
2^e <= x < 2^(e+1)
```

bước giữa các số normal liên tiếp **trong cùng khoảng số mũ** là:

```text
ULP_double(x) = 2^(e - 52)
ULP_float(x)  = 2^(e - 23)
```

Với `float` thay 52 bằng 23. Công thức mô tả khoảng cách theo significand; nó không nói rằng mọi số thực trong khoảng đều lưu được, cũng không nói kết quả phép toán luôn chỉ sai đúng một ULP.

Bảng ví dụ cho `double`:

| Vị trí | `Math.ulp(x)` | Ý nghĩa |
| --- | --- | --- |
| `x = 0.5` | `2^-53` | Mật độ dày hơn quanh `1.0` |
| `x = 1.0` | `2^-52` | Khoảng phía trên là `2^-52` |
| `x = 2.0` | `2^-51` | Bước tăng gấp đôi khi vượt ngưỡng 2 |
| `x = 1024.0` | `2^-42` | Mỗi bước lớn hơn bước quanh 1 |
| `x = 2^53` | `2` | Không còn đại diện được mọi số nguyên liên tiếp |

Sự thay đổi ULP theo số mũ tạo ra **độ chính xác tương đối gần như ổn định** đối với số normal, còn **độ chính xác tuyệt đối giảm khi độ lớn tăng**. Gần 1, một bước rất nhỏ; ở `2^53`, một bước là 2; ở các giá trị cực lớn, một bước có thể lớn hơn vô số số nguyên.

**Ngoại lệ ở ranh giới:** với `x = 2^e` ở miền normal (trừ một số ranh giới rất thấp), khoảng cách từ `x` xuống số hữu hạn kề trước **bằng một nửa** bước lên trên, vì nó nằm trong khoảng số mũ thấp hơn. `Math.ulp(x)` dùng khoảng tương ứng với `x`, tức khoảng phía trên ở ranh giới này.

## 4. Ví dụ chính xác quanh `1.0`

Ta có:

```text
nextUp(1.0) - 1.0   = 2^-52 = 2.220446049250313E-16
1.0 - nextDown(1.0) = 2^-53 = 1.1102230246251565E-16
Math.ulp(1.0)       = 2^-52
```

Ở `1.0`, `Math.ulp(x)` trùng bước **phía trên** nhưng không trùng bước **phía dưới**. Điều này quan trọng khi thiết kế thuật toán kiểm tra số gần nhau: nếu bạn mặc định “một ULP là một khoảng cách đều ở cả hai phía”, bạn sẽ sai tại các mốc `2^e`.

Để nhìn rõ bit fraction và mũ, dùng `Double.toHexString`:

```java
double ulp = Math.ulp(1.0);
System.out.println(Double.toHexString(ulp)); // 0x1.0p-52
```

Trong literal hex floating-point, `p-52` biểu thị số mũ **cơ số 2**. `0x1.0p-52` có giá trị `2^-52`; nó khác khái niệm literal số nguyên hex. Với `float`, `Float.toHexString(Math.ulp(1.0f))` trả `0x1.0p-23`.

## 5. Giới hạn số nguyên biểu diễn chính xác: `2^24` và `2^53`

Vì phần trị có số bit hữu hạn, trong `float` mọi số nguyên từ 0 đến `2^24` đều biểu diễn chính xác, nhưng **không phải mọi số nguyên lớn hơn** đều biểu diễn được. Với `double`, ngưỡng tương tự là `2^53`.

Ví dụ với `double`:

```java
double limit = 0x1.0p53;        // 2^53 = 9007199254740992
double tooSmallToChange = limit + 1.0;
System.out.println(tooSmallToChange == limit); // true
System.out.println(Math.ulp(limit));           // 2.0
```

Hai giá trị `limit` và `limit + 1.0` cho kết quả bằng nhau trong ví dụ này. Số toán học `2^53+1` nằm **chính giữa** hai số `double` biểu diễn được `2^53` và `2^53+2`; phép làm tròn mặc định kiểu **round to nearest, ties to even** đưa nó về `2^53`. Không phải “phép cộng bị bỏ qua theo cú pháp”; kết quả đúng về mặt toán học nằm **ngoài lưới biểu diễn**.

Tương tự, `Math.ulp(1e16)` là `2.0` và `1e16 + 1.0 == 1e16` có giá trị `true`. Với `float` tại `2^24`, `Math.ulp(0x1.0p24f)` bằng `2.0f`. Điều này giải thích tại sao không nên dùng `float` để lưu các định danh, bộ đếm nguyên lớn hoặc các giá trị cần chính xác tuyệt đối từng đơn vị.

## 6. Rounding: vì sao cộng nửa ULP có thể giữ nguyên giá trị?

Phép cộng trên dấu phẩy động được làm tròn tới một số có thể biểu diễn. Khi kết quả toán học nằm giữa hai giá trị kế nhau, chế độ mặc định thường là **gần nhất, trường hợp hòa thì chọn phần trị chẵn**. Do vậy một thay đổi nhỏ hơn khoảng cách gần số đang lưu có thể biến mất.

Ví dụ quanh `1.0`:

```java
double one = 1.0;
double step = Math.ulp(one);
boolean unchanged = (one + step / 2.0) == one; // true theo ties-to-even
boolean next = (one + step) == Math.nextUp(one); // true
```

Đây là đoạn mã minh họa trong phương thức. Ở `1.0`, `step/2.0 = 2^-53` nằm chính giữa `1.0` và `1.0 + 2^-52`; làm tròn chọn `1.0`. Nhưng **không thể suy thành quy luật chung** “cộng nửa ULP luôn không đổi”: kết quả hòa phụ thuộc bit cuối của significand và các điểm ranh giới.

Nếu cộng một thay đổi rất nhỏ vào tổng rất lớn nhiều lần, bạn có thể bị mất những bước nhỏ do làm tròn. Trong tính tổng dữ liệu nhiều độ lớn khác nhau, có thể cân nhắc thuật toán cộng bù (compensated summation), sắp xếp/nhóm dữ liệu hoặc dùng kiểu số thích hợp theo yêu cầu chính xác.

## 7. Subnormal: khoảng cách gần zero

Khi số quá nhỏ để biểu diễn dưới dạng **normal**, IEEE 754 có miền **subnormal (denormal)** với significand không có bit 1 ẩn. Các giá trị dương subnormal liên tiếp có bước **đều nhau**, giúp tiến dần về 0 thay vì lập tức mất toàn bộ các số nhỏ.

Với Java `double`:

- `Double.MIN_VALUE` là **số `double` dương nhỏ nhất khác 0**, khoảng `4.9E-324`; **không** phải số âm có độ lớn lớn nhất.
- `Double.MIN_NORMAL` là **số normal dương nhỏ nhất**, bằng `2^-1022`.
- Bước subnormal là `2^-1074`, cũng chính là `Double.MIN_VALUE`.
- `Math.ulp(0.0)` và `Math.ulp(Double.MIN_NORMAL)` đều bằng `Double.MIN_VALUE`.

Tương tự, `Float.MIN_VALUE` là số `float` dương subnormal nhỏ nhất (`2^-149`), và `Float.MIN_NORMAL` là số normal dương nhỏ nhất (`2^-126`). Bước nhỏ nhất ở miền subnormal của `float` là `2^-149`.

```java
double stepAtZero = Math.ulp(0.0);             // Double.MIN_VALUE
double firstPositive = Math.nextUp(0.0);        // Double.MIN_VALUE
double firstNegative = Math.nextDown(0.0);      // -Double.MIN_VALUE
```

Hai dạng zero `+0.0` và `-0.0` tồn tại trong IEEE 754; cả hai so sánh bằng nhau bằng `==`, nhưng có mẫu bit dấu khác nhau. `Math.nextUp(-0.0)` vẫn đi tới `Double.MIN_VALUE` dương. Trong miền subnormal, ULP **không tiếp tục giảm vô hạn**: dưới bước nhỏ nhất, một kết quả có thể làm tròn về zero (underflow).

## 8. Các trường hợp đặc biệt: `NaN`, vô cực và giá trị hữu hạn lớn nhất

Không phải mọi `double` đều là một số thực hữu hạn thông thường. Java có `NaN`, `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`. Quy tắc của `Math.ulp`:

| Đầu vào | Kết quả `Math.ulp` |
| --- | --- |
| `NaN` | `NaN` |
| `+Infinity` hoặc `-Infinity` | `+Infinity` |
| `+0.0` hoặc `-0.0` | `Double.MIN_VALUE` (hoặc `Float.MIN_VALUE`) |
| Giá trị hữu hạn khác 0 | ULP dương tương ứng với độ rộng kiểu |

Ở `Double.MAX_VALUE`, `Math.ulp(Double.MAX_VALUE)` vẫn là một số **hữu hạn rất lớn**, nhưng `Math.nextUp(Double.MAX_VALUE)` là **`+Infinity`**. Vì vậy không được tính “gap đến số hữu hạn kế trên” bằng phép trừ `nextUp(x)-x` trong mọi trường hợp: ở đỉnh hữu hạn, **không có số hữu hạn kế trên**.

Hãy tránh so sánh `Math.ulp(Double.POSITIVE_INFINITY)` với một khoảng cách số học hữu hạn hoặc lấy `Math.nextUp(Double.MAX_VALUE) - Double.MAX_VALUE` để suy ra một ULP thông thường. Nếu dùng `Math.nextUp`/`nextDown` trong thuật toán số, phải kiểm tra `Double.isFinite`, `Double.isNaN`, `Double.isInfinite` khi đầu vào có thể là những giá trị đặc biệt.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu tệp với tên **`UlpSpacingDemo.java`**:

```java
public class UlpSpacingDemo {
    public static void main(String[] args) {
        double one = 1.0;
        double above = Math.nextUp(one);
        double below = Math.nextDown(one);
        System.out.println("ulp(1.0)=" + Math.ulp(one));
        System.out.println("nextUp(1.0)=" + above);
        System.out.println("nextDown(1.0)=" + below);
        System.out.println("gapAbove=" + (above - one));
        System.out.println("gapBelow=" + (one - below));
        System.out.println("upperGapEqualsUlp=" + (above - one == Math.ulp(one)));
        System.out.println("lowerGapEqualsUlp=" + (one - below == Math.ulp(one)));
        System.out.println("ulpHex=" + Double.toHexString(Math.ulp(one)));
        System.out.println("floatUlpHex=" + Float.toHexString(Math.ulp(1.0f)));
        System.out.println("ulp(0.0)==MIN_VALUE=" + (Math.ulp(0.0) == Double.MIN_VALUE));
        System.out.println("ulp(minNormal)==MIN_VALUE=" + (Math.ulp(Double.MIN_NORMAL) == Double.MIN_VALUE));
        System.out.println("ulp(2.0)/ulp(1.0)=" + (Math.ulp(2.0) / Math.ulp(1.0)));
        System.out.println("ulp(2^53)=" + Math.ulp(0x1.0p53));
        System.out.println("2^53PlusOneLost=" + (0x1.0p53 + 1.0 == 0x1.0p53));
        System.out.println("ulp(1e16)=" + Math.ulp(1e16));
        System.out.println("1e16PlusOneLost=" + (1e16 + 1.0 == 1e16));
        System.out.println("floatULP(2^24)=" + Math.ulp(0x1.0p24f));
        System.out.println("nextUpMaxInfinity=" + Double.isInfinite(Math.nextUp(Double.MAX_VALUE)));
        System.out.println("ulpMaxFinite=" + Double.isFinite(Math.ulp(Double.MAX_VALUE)));
        System.out.println("ulpInfinityInfinite=" + Double.isInfinite(Math.ulp(Double.POSITIVE_INFINITY)));
        System.out.println("ulpNaNisNaN=" + Double.isNaN(Math.ulp(Double.NaN)));
        System.out.println("nextUpNegZeroMinPositive=" + (Math.nextUp(-0.0) == Double.MIN_VALUE));
        System.out.println("nextAfterOneDown=" + (Math.nextAfter(1.0, 0.0) == below));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 UlpSpacingDemo.java
java UlpSpacingDemo
```

Stdout chính xác:

```text
ulp(1.0)=2.220446049250313E-16
nextUp(1.0)=1.0000000000000002
nextDown(1.0)=0.9999999999999999
gapAbove=2.220446049250313E-16
gapBelow=1.1102230246251565E-16
upperGapEqualsUlp=true
lowerGapEqualsUlp=false
ulpHex=0x1.0p-52
floatUlpHex=0x1.0p-23
ulp(0.0)==MIN_VALUE=true
ulp(minNormal)==MIN_VALUE=true
ulp(2.0)/ulp(1.0)=2.0
ulp(2^53)=2.0
2^53PlusOneLost=true
ulp(1e16)=2.0
1e16PlusOneLost=true
floatULP(2^24)=2.0
nextUpMaxInfinity=true
ulpMaxFinite=true
ulpInfinityInfinite=true
ulpNaNisNaN=true
nextUpNegZeroMinPositive=true
nextAfterOneDown=true
```

Các dòng thể hiện bốn khía cạnh:

1. **Không đều ngay tại `1.0`**: `upperGapEqualsUlp=true`, `lowerGapEqualsUlp=false`; ULP tại `1.0` là `2^-52` nhưng bước xuống là `2^-53`.
2. **Độ lớn chi phối khoảng cách**: `ulp(2.0)/ulp(1.0)=2.0`; tại `2^53` và `1e16`, một bước tăng có độ lớn `2.0`.
3. **Zero và subnormal**: `Math.ulp(0.0) == Double.MIN_VALUE`, `Math.ulp(Double.MIN_NORMAL) == Double.MIN_VALUE`.
4. **Ngoại lệ biên**: `nextUp(Double.MAX_VALUE)` chuyển tới Infinity dù ULP tại max vẫn hữu hạn; `NaN` và Infinity có quy tắc ULP đặc biệt.

Tập ví dụ sử dụng `Double.toHexString` và `Float.toHexString` để hiển thị đúng mũ nhị phân, tránh việc các chữ số thập phân hiển thị ngắn làm ta tưởng rằng các khoảng cách nguyên tử đều biểu diễn được bằng một số chữ số giống nhau.

## 10. ULP có phải sai số tính toán không?

**Không đồng nhất.** ULP là mức phân giải của tập giá trị biểu diễn được gần một số, còn **sai số** là khoảng cách giữa kết quả xấp xỉ và kết quả toán học/giá trị chuẩn được chọn.

Ví dụ, `Math.ulp(1.0)` khoảng `2.22E-16`, nhưng điều đó **không có nghĩa** mọi phép tính trên `double` sẽ sai nhiều nhất `2.22E-16`. Sai số thực tế phụ thuộc độ lớn giá trị, cách làm tròn, số bước tính, điều kiện bài toán và độ ổn định của thuật toán. Một phép trừ hai số rất gần nhau có thể mất nhiều chữ số có nghĩa do **catastrophic cancellation**; lặp hàng triệu phép toán có thể tích lũy sai số.

Khi một phép toán cơ bản được làm tròn đúng tới giá trị `double` gần nhất trong phạm vi thích hợp, có thể phân tích sai số làm tròn bằng phần của một ULP **của kết quả lý tưởng**. Tuy nhiên, phát biểu như “mọi thuật toán sai không quá nửa ULP” là **sai**. Sai số của cả chuỗi phép tính có thể vượt nhiều ULP, nhất là khi dữ liệu nhạy hoặc gần hủy triệt tiêu.

Ngoài ra, **đơn vị ULP ở `a` và ở `b` có thể khác nhau** khi hai số thuộc hai cấp số mũ khác nhau. Khi báo cáo “chênh 2 ULP”, cần xác định quy ước đo tại điểm nào hoặc theo thứ tự mã bit nào, xử lý sign/zero/infinity ra sao.

## 11. Dùng `Math.ulp` để so sánh số dấu phẩy động: có giới hạn

Một số đoạn mã thử so sánh xấp xỉ:

```java
double a = 1.0;
double b = Math.nextUp(a);
boolean near = Math.abs(a - b) <= Math.ulp(a); // true trong trường hợp này
```

Đó là **một ví dụ có kiểm soát**, không phải hàm “so sánh double đúng cho mọi đầu vào”. Có nhiều vấn đề nếu lấy `Math.ulp(a)` làm ngưỡng cố định:

- Ở ranh giới `2^e`, bước phía trên và bước phía dưới khác nhau.
- So sánh hai số chênh độ lớn rất xa cần chính sách rõ về ngưỡng tương đối/tuyệt đối; ULP chỉ ở một đầu không mô tả đầy đủ.
- `a - b` có thể overflow sang Infinity hoặc underflow về zero trong trường hợp biên.
- `NaN`, Infinity và signed zero cần quy tắc nghiệp vụ riêng; `NaN == NaN` luôn `false`, còn hai zero so sánh `==` là `true`.
- Kết quả “gần nhau trong vài ULP” chỉ nói chúng ở gần trên **lưới biểu diễn**, không khẳng định chúng đúng so với thực tế.

Nếu đang kiểm tra một thuật toán số, có thể dùng **sai số tuyệt đối** `abs(a-b)`, **sai số tương đối** theo độ lớn hoặc khoảng cách ULP theo một định nghĩa rõ ràng, tùy phép đo và dữ liệu. Gần zero, ngưỡng tương đối thuần túy thường bất tiện; ở trị tuyệt đối rất lớn, ngưỡng tuyệt đối cố định có thể quá chặt.

Đối với **tiền tệ** hoặc số thập phân phải chính xác theo quy tắc kế toán, thường nên dùng `BigDecimal` được tạo từ chuỗi thập phân (`new BigDecimal("0.1")`) và chọn chế độ làm tròn rõ ràng; đừng coi ULP là cách thay thế độ chính xác thập phân.

## 12. Các nhầm lẫn phổ biến

| Nhầm lẫn | Thực tế | Cách kiểm tra |
| --- | --- | --- |
| “`double` chính xác khoảng 15–16 chữ số nên mọi bước đều nhỏ bằng nhau” | Bước tuyệt đối tăng khi cấp số mũ tăng | `Math.ulp(x)` ở nhiều độ lớn |
| `Math.ulp(x) == Math.nextUp(x)-x == x-Math.nextDown(x)` ở mọi nơi | Sai tại ranh giới lũy thừa 2 như `1.0` | In cả hai bước |
| `Math.ulp(0.0) == 0.0` | ULP tại zero bằng subnormal dương nhỏ nhất | `Math.ulp(0.0) == Double.MIN_VALUE` |
| `Double.MIN_VALUE` là số âm nhỏ nhất | Đây là số **dương** nhỏ nhất khác 0 | Phân biệt `Double.MIN_VALUE` và `-Double.MAX_VALUE` |
| `2^53 + 1` luôn khác `2^53` khi dùng `double` | Không biểu diễn được mọi số nguyên liền nhau sau ngưỡng | `0x1.0p53 + 1.0 == 0x1.0p53` |
| ULP là sai số tối đa của mọi chương trình | ULP mô tả khoảng cách lưới, không phải bảo đảm thuật toán | Đánh giá sai số theo phép tính cụ thể |
| `Math.ulp(Double.MAX_VALUE)` phải là Infinity | ULP tại MAX vẫn hữu hạn, nhưng `nextUp(MAX)` là Infinity | So hai lời gọi riêng biệt |
| So sánh `double` chỉ cần ngưỡng `1e-9` cho mọi thang số | Ngưỡng cố định không phù hợp mọi miền | Xác định sai số tuyệt đối/tương đối theo yêu cầu |
| `float` và `double` có cùng ULP tại `1.0` | Chúng có 24 và 53 bit significand hiệu dụng | So `Math.ulp(1f)` và `Math.ulp(1d)` |

## 13. Câu hỏi tự kiểm tra

1. **ULP mô tả điều gì?** Độ phân giải/khoảng cách biểu diễn liên quan tới vị trí bit cuối của significand gần một giá trị dấu phẩy động, không phải sai số mọi thuật toán.
2. **`Math.ulp(1.0)` bằng gì?** `2^-52` = khoảng `2.220446049250313E-16`. Với `float 1.0f`, ULP là `2^-23`.
3. **Vì sao `1.0-Math.nextDown(1.0)` chỉ bằng một nửa `Math.ulp(1.0)`?** Vì `1.0` là ranh giới giữa hai vùng số mũ liên tiếp; bước phía dưới nhỏ hơn phía trên.
4. **`Math.ulp(2.0)` so với `Math.ulp(1.0)` thế nào?** Gấp đôi; chúng lần lượt là `2^-51` và `2^-52`.
5. **Ở `2^53`, cộng `1.0` vào `double` có nhất thiết đổi số không?** Không; `2^53+1` nằm giữa hai giá trị biểu diễn được nên làm tròn về `2^53`.
6. **`Math.ulp(0.0)` có bằng 0 không?** Không; bằng `Double.MIN_VALUE`, tức bước subnormal dương nhỏ nhất.
7. **`Math.ulp(Double.MAX_VALUE)` là Infinity không?** Không, vẫn hữu hạn; riêng `Math.nextUp(Double.MAX_VALUE)` trả Infinity.
8. **Để tìm số representable ngay dưới `x`, dùng API nào?** `Math.nextDown(x)`; không tự lấy `x - Math.ulp(x)` và giả định luôn đúng.
9. **Có thể dùng `Math.ulp` trực tiếp thay `BigDecimal` cho phép tính tiền chính xác không?** Không; yêu cầu chính xác thập phân và quy tắc làm tròn tiền tệ là vấn đề khác.

**Kết luận:** ULP cho thấy vì sao số dấu phẩy động **có khoảng trống** và vì sao khoảng cách tuyệt đối **thay đổi theo độ lớn và số mũ**. Với Java, hãy dùng `Math.ulp` cùng `Math.nextUp`/`nextDown` để quan sát lưới biểu diễn, chú ý ranh giới lũy thừa 2, vùng subnormal và các giá trị đặc biệt. Khi tính toán thực tế, phân biệt rõ **độ phân giải biểu diễn** với **sai số thuật toán** và chọn chiến lược so sánh/làm tròn theo yêu cầu nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-010">floatToIntBits, doubleToLongBits và các biến thể raw</a>

<details>
<summary>Xem chi tiết</summary>

# `floatToIntBits`, `doubleToLongBits` và các biến thể `raw` trong Java

`Float.floatToIntBits`, `Float.floatToRawIntBits`, `Double.doubleToLongBits` và `Double.doubleToRawLongBits` cho phép **xem mẫu bit biểu diễn của một giá trị dấu phẩy động** trong Java. Đây là thao tác trên **cách mã hóa IEEE 754**, không phải phép chuyển giá trị số học sang số nguyên. Sự khác biệt giữa bản thường và bản `raw` đặc biệt quan trọng với **NaN và payload NaN**.

Ví dụ với `float`:

```java
float x = 1.0f;

int bits = Float.floatToIntBits(x);  // 0x3F800000 (1065353216)
int number = (int) x;               // 1: chuyển giá trị bằng cast
```

Cả hai biểu thức đều trả về `int`, nhưng **không cùng ý nghĩa**: `bits` giữ mẫu 32 bit của số `1.0f`; `number` là phần nguyên số học của giá trị `1.0f`. Để hiểu và sử dụng chính xác bốn API, cần phân biệt layout bit, NaN chuẩn hóa, `raw`, phép khôi phục mẫu bit và giới hạn của việc lưu/truyền dữ liệu.

## 1. Bốn API chính và hai API đi ngược

| API | Tham số | Kết quả | Xử lý NaN |
| --- | --- | --- | --- |
| `Float.floatToIntBits(float)` | `float`, 32 bit | `int` chứa mẫu bit 32 bit | **Chuẩn hóa mọi NaN** thành `0x7FC00000` |
| `Float.floatToRawIntBits(float)` | `float`, 32 bit | `int` chứa mẫu bit 32 bit | Trả **mẫu bit raw** của giá trị đang được biểu diễn |
| `Double.doubleToLongBits(double)` | `double`, 64 bit | `long` chứa mẫu bit 64 bit | **Chuẩn hóa mọi NaN** thành `0x7FF8000000000000L` |
| `Double.doubleToRawLongBits(double)` | `double`, 64 bit | `long` chứa mẫu bit 64 bit | Trả **mẫu bit raw** của giá trị đang được biểu diễn |

Hai API khôi phục tương ứng:

- `Float.intBitsToFloat(int bits)`: diễn giải **32 bit đã cho** thành một giá trị `float`.
- `Double.longBitsToDouble(long bits)`: diễn giải **64 bit đã cho** thành một giá trị `double`.

Chúng không phải phép chuyển số học từ `int` sang `float` hoặc `long` sang `double`:

```java
float arithmetic = (float) 0x3F800000;         // Chuyển số nguyên lớn thành float
float decoded = Float.intBitsToFloat(0x3F800000); // 1.0f

double arithmetic2 = (double) 0x3FF0000000000000L;
double decoded2 = Double.longBitsToDouble(0x3FF0000000000000L); // 1.0
```

Với `intBitsToFloat`/`longBitsToDouble`, **cùng chuỗi bit** được diễn giải theo định dạng dấu phẩy động; không thực hiện phép chia, nhân hay làm tròn số nguyên sang số thực như cast thông thường.

## 2. Layout IEEE 754 của `float` và `double`

Java sử dụng định dạng dấu phẩy động nhị phân theo IEEE 754 cho `float` và `double`. Giá trị biểu diễn gồm **bit dấu, trường số mũ và trường phần trị (fraction)**:

| Thành phần | `float` (binary32) | `double` (binary64) |
| --- | ---: | ---: |
| Tổng bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit exponent | 8 | 11 |
| Bit fraction | 23 | 52 |
| Bias của exponent | 127 | 1023 |

Minh họa vị trí bit, từ trái sang phải (bit có trọng số cao đến thấp):

```text
float  (32 bit): S EEEEEEEE FFFFFFFFFFFFFFFFFFFFFFF
                 1    8               23

double (64 bit): S EEEEEEEEEEE FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
                 1      11                         52
```

- **Dấu (`S`):** `0` dương, `1` âm.
- **Exponent:** với giá trị normal hữu hạn, exponent lưu số mũ có bias; với exponent bằng toàn bit `1`, mẫu bit chỉ vô cực hoặc NaN.
- **Fraction:** các bit phần trị. Với giá trị normal có bit dẫn ẩn, độ chính xác hiệu dụng là 24 bit cho `float` và 53 bit cho `double`. Với subnormal, quy tắc phần trị khác (không có bit dẫn 1 ẩn).

Ví dụ `float 1.0f` có dấu 0, exponent lưu 127 và fraction 0. Mẫu bit đầy đủ là `0x3F800000`. `double 1.0` có mẫu `0x3FF0000000000000L`.

Các mẫu tiêu biểu:

| Giá trị | Bit `float` | Bit `double` |
| --- | --- | --- |
| `+0.0` | `0x00000000` | `0x0000000000000000` |
| `-0.0` | `0x80000000` | `0x8000000000000000` |
| `+1.0` | `0x3F800000` | `0x3FF0000000000000` |
| `+Infinity` | `0x7F800000` | `0x7FF0000000000000` |
| `-Infinity` | `0xFF800000` | `0xFFF0000000000000` |
| NaN **chuẩn hóa bởi API thường** | `0x7FC00000` | `0x7FF8000000000000` |

Đây là các mã hexa của mẫu bit, **không phải** literal Java có kiểu `float`/`double`. Lưu `0x3F800000` vào `int` rồi cast thành `float` sẽ cho giá trị lớn khoảng một tỷ, không phải `1.0f`; muốn tái diễn giải phải dùng `Float.intBitsToFloat`.

## 3. `floatToIntBits` và `doubleToLongBits` hoạt động thế nào?

Hai phương thức `Float.floatToIntBits` và `Double.doubleToLongBits` chuyển giá trị sang một số nguyên **cùng độ rộng bit**:

```java
int floatBits = Float.floatToIntBits(1.0f);    // 0x3F800000
long doubleBits = Double.doubleToLongBits(1.0); // 0x3FF0000000000000L
```

Chúng bảo toàn các bit của giá trị **không phải NaN**, bao gồm:

- `+0.0` và `-0.0`: số học so sánh bằng nhau, nhưng bit dấu khác nhau nên kết quả bits khác nhau.
- `+Infinity` và `-Infinity`: exponent toàn 1, fraction toàn 0, dấu khác nhau.
- Số hữu hạn normal và subnormal: mã hóa của chúng được trả trong `int`/`long`.
- Số âm: bit cao nhất bằng 1; vì `int` và `long` có dấu, kết quả khi **in dạng thập phân có thể là số âm**. Điều này không làm thay đổi chuỗi bit.

**Riêng NaN**, API thường trả một mẫu chuẩn duy nhất: `Float.floatToIntBits(...)` trả `0x7FC00000` và `Double.doubleToLongBits(...)` trả `0x7FF8000000000000L` với mọi NaN. Do đó, nếu có nhiều NaN mang payload khác nhau, dùng API thường sẽ **không giữ sự khác biệt về payload**.

Lợi ích của chuẩn hóa là tạo biểu diễn **xác định** khi so sánh hoặc tạo hash dựa trên bit của giá trị NaN theo ngữ nghĩa wrapper. Điều này không đồng nghĩa với việc các phép so sánh primitive `==` giữa NaN trở thành đúng; Java vẫn có quy tắc `Double.NaN == Double.NaN` là `false`.

## 4. Biến thể `raw` khác gì với bản thường?

Các API `Float.floatToRawIntBits` và `Double.doubleToRawLongBits` trả lại mẫu bit của giá trị hiện tại **mà không chuẩn hóa mọi NaN về một mẫu canonical**. Với NaN chứa payload, điểm khác biệt có thể quan sát:

```java
float nanWithPayload = Float.intBitsToFloat(0x7FC01234);
int raw = Float.floatToRawIntBits(nanWithPayload);    // 0x7FC01234 trong ví dụ
int normal = Float.floatToIntBits(nanWithPayload);    // 0x7FC00000

double nan64 = Double.longBitsToDouble(0x7FF8000000001234L);
long raw64 = Double.doubleToRawLongBits(nan64);       // 0x7FF8000000001234L trong ví dụ
long normal64 = Double.doubleToLongBits(nan64);      // 0x7FF8000000000000L
```

Payload NaN nằm trong phần fraction khi exponent toàn 1 và fraction khác 0. Các phép tính dấu phẩy động có thể sinh hoặc truyền NaN; một số hệ thống sử dụng payload để ghi thông tin trạng thái. Tuy nhiên, **không nên dùng payload NaN như một kênh dữ liệu đáng tin cậy trên mọi đường đi**.

**Giới hạn quan trọng với NaN signaling:** Trên một số nền tảng, `Float.intBitsToFloat` hoặc `Double.longBitsToDouble` khi đưa mẫu *signaling NaN* vào giá trị số thực có thể khiến nó được **quiet hóa** (*quieting*), làm đổi một số bit. Vì vậy không được cam kết mọi mẫu NaN raw sẽ round-trip bit-perfect qua mọi phép toán, tham số, tối ưu hóa hoặc JVM. Trong ví dụ kiểm thử, các mẫu `0x7FC01234` và `0x7FF8000000001234L` là các **quiet NaN** có payload; API `raw` giữ mẫu đúng trong lần chạy được minh họa. Nếu yêu cầu bảo toàn **mọi mẫu bit NaN đầu vào** trong file/giao thức, lưu và vận chuyển chúng dưới dạng `int`/`long` hoặc mảng byte, và chỉ diễn giải thành `float`/`double` khi thực sự cần tính toán.

Với giá trị **không phải NaN**, kết quả của API `raw` và API thường là như nhau.

## 5. Vì sao `+0.0` và `-0.0` bằng nhau nhưng mã bit khác nhau?

IEEE 754 có **hai mẫu bit của số 0**:

```java
double plus = 0.0;
double minus = -0.0;

boolean numericEqual = (plus == minus);  // true
boolean bitEqual = Double.doubleToLongBits(plus)
                 == Double.doubleToLongBits(minus); // false
```

Với `double`, `+0.0` là `0x0000000000000000L`, `-0.0` là `0x8000000000000000L`. Với `float`, chúng là `0x00000000` và `0x80000000`. Bit dấu tạo sự khác biệt mặc dù phép so sánh giá trị `==` đánh giá bằng nhau.

Sự khác biệt đôi khi có nghĩa toán học: `1.0 / +0.0` sinh `+Infinity`, còn `1.0 / -0.0` sinh `-Infinity`. API bit hữu ích khi cần xác nhận dấu của zero trong phép tính hoặc khi giải mã dữ liệu nhị phân.

**Đừng dùng so sánh bit một cách máy móc để thay mọi so sánh số thực.** So sánh giá trị, so sánh theo `Float.compare`/`Double.compare`, và so sánh bit thô là ba yêu cầu khác nhau. Ví dụ, primitive `NaN == NaN` là false, trong khi wrapper `Double.equals` được thiết kế theo phép so sánh bit canonical; signed zero cũng được wrapper phân biệt. Hãy chọn quy tắc theo nghiệp vụ.

## 6. Khôi phục bit: `intBitsToFloat` và `longBitsToDouble`

Hai hàm “đảo” lấy **các bit nguyên** rồi tạo giá trị số thực:

```java
int bits32 = 0x3F800000;
float restored32 = Float.intBitsToFloat(bits32); // 1.0f

long bits64 = 0x3FF0000000000000L;
double restored64 = Double.longBitsToDouble(bits64); // 1.0
```

Với các giá trị `float`/`double` **không phải NaN**, có quan hệ round-trip bit quan trọng:

```java
int bits = Float.floatToIntBits(3.5f);
float again = Float.intBitsToFloat(bits); // 3.5f

long longBits = Double.doubleToLongBits(-0.0);
double again64 = Double.longBitsToDouble(longBits); // -0.0
```

Nếu bắt đầu từ một **mẫu bit NaN tùy ý**, đi qua API thường sẽ chuẩn hóa NaN, nên *bit round-trip* có thể khác. Ngay cả với `raw`, các caveat về signaling NaN và những phép toán trung gian vẫn phải được lưu ý.

Nên phân biệt **round-trip giá trị** và **round-trip chuỗi bit**:

- Với số hữu hạn, mục tiêu tái tạo đúng giá trị thường trùng với việc giữ mẫu bit.
- Với `+0.0` và `-0.0`, cùng so sánh bằng về số học nhưng bit khác; cần xác định mục tiêu có bảo toàn dấu zero không.
- Với NaN, kết quả “vẫn là NaN” chưa chứng minh được payload hoặc mọi bit ban đầu đều được giữ.

## 7. In bit đúng cách: hex và phép dịch không dấu

Do `Float.floatToIntBits` trả `int` có dấu, những mẫu có bit cao bằng 1 có thể in ra số nguyên âm. Ví dụ `Float.floatToIntBits(-0.0f)` là mẫu `0x80000000`, tương ứng với `Integer.MIN_VALUE` nếu in thập phân.

In hexa giúp xem đúng độ rộng:

```java
int bits32 = Float.floatToIntBits(-0.0f);
System.out.printf("0x%08X%n", bits32);   // 0x80000000

long bits64 = Double.doubleToLongBits(-0.0);
System.out.printf("0x%016X%n", bits64);  // 0x8000000000000000
```

Nếu muốn tách trường `float`:

```java
int bits = Float.floatToRawIntBits(1.0f);
int sign = (bits >>> 31) & 1;
int exponent = (bits >>> 23) & 0xFF;
int fraction = bits & 0x7FFFFF;
// sign = 0, exponent = 127, fraction = 0
```

Với `double`:

```java
long bits = Double.doubleToRawLongBits(1.0);
long sign = (bits >>> 63) & 1L;
long exponent = (bits >>> 52) & 0x7FFL;
long fraction = bits & 0xFFFFFFFFFFFFFL;
// sign = 0, exponent = 1023, fraction = 0
```

Dùng `>>>` (dịch phải **không dấu**, bổ sung bit 0 ở bên trái) khi phân tích trường bit, đặc biệt cho mẫu có bit dấu 1. Dùng đúng literal kiểu `long` (`1L`, `0x7FFL`) khi thao tác với 64 bit.

Đừng nhầm **bit của giá trị số thực** với chuỗi ký tự thập phân của nó. Số `0.1` không có biểu diễn nhị phân hữu hạn chuẩn xác; API trả về mẫu `double` thực tế, không phải mã chữ số “0”, “.”, “1”.

## 8. Chương trình hoàn chỉnh Java 17+ về canonical và raw

Tạo tệp `FloatingBitsDemo.java`:

```java
public class FloatingBitsDemo {
    static String fhex(int bits) {
        return String.format("0x%08X", bits);
    }

    static String dhex(long bits) {
        return String.format("0x%016X", bits);
    }

    public static void main(String[] args) {
        float f = 1.0f;
        System.out.println("float 1.0 bits: " + fhex(Float.floatToIntBits(f)));
        System.out.println("float -0.0 bits: " + fhex(Float.floatToIntBits(-0.0f)));
        System.out.println("float +0.0 bits: " + fhex(Float.floatToIntBits(0.0f)));
        System.out.println("int cast of 1.0f: " + (int) f);
        System.out.println("float NaN canonical: " + fhex(Float.floatToIntBits(Float.NaN)));

        float payloadFloat = Float.intBitsToFloat(0x7FC01234);
        System.out.println("float NaN raw: " + fhex(Float.floatToRawIntBits(payloadFloat)));
        System.out.println("float NaN normalized: " + fhex(Float.floatToIntBits(payloadFloat)));
        System.out.println("float restored 1.0: " + Float.intBitsToFloat(0x3F800000));
        System.out.println("float raw isNaN: " + Float.isNaN(payloadFloat));

        double d = 1.0;
        System.out.println("double 1.0 bits: " + dhex(Double.doubleToLongBits(d)));
        System.out.println("double -0.0 bits: " + dhex(Double.doubleToLongBits(-0.0)));
        System.out.println("double +0.0 bits: " + dhex(Double.doubleToLongBits(0.0)));
        System.out.println("double NaN canonical: " + dhex(Double.doubleToLongBits(Double.NaN)));

        double payloadDouble = Double.longBitsToDouble(0x7FF8000000001234L);
        System.out.println("double NaN raw: " + dhex(Double.doubleToRawLongBits(payloadDouble)));
        System.out.println("double NaN normalized: " + dhex(Double.doubleToLongBits(payloadDouble)));
        System.out.println("double restored 1.0: "
            + Double.longBitsToDouble(0x3FF0000000000000L));
        System.out.println("+0.0 == -0.0: " + (0.0 == -0.0));
        System.out.println("bits equal? "
            + (Double.doubleToLongBits(0.0) == Double.doubleToLongBits(-0.0)));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingBitsDemo.java
java FloatingBitsDemo
```

Stdout của chương trình (dùng ví dụ quiet NaN payload):

```text
float 1.0 bits: 0x3F800000
float -0.0 bits: 0x80000000
float +0.0 bits: 0x00000000
int cast of 1.0f: 1
float NaN canonical: 0x7FC00000
float NaN raw: 0x7FC01234
float NaN normalized: 0x7FC00000
float restored 1.0: 1.0
float raw isNaN: true
double 1.0 bits: 0x3FF0000000000000
double -0.0 bits: 0x8000000000000000
double +0.0 bits: 0x0000000000000000
double NaN canonical: 0x7FF8000000000000
double NaN raw: 0x7FF8000000001234
double NaN normalized: 0x7FF8000000000000
double restored 1.0: 1.0
+0.0 == -0.0: true
bits equal? false
```

**Giải thích:** Các dòng `1.0` đối chiếu mã IEEE 754 đã biết; `int cast` chứng minh bit conversion không phải numeric cast; các dòng `-0.0` so sánh bit dấu; các cặp `NaN raw` và `NaN normalized` cho thấy việc giữ hoặc chuẩn hóa payload; `intBitsToFloat`/`longBitsToDouble` khôi phục `1.0`. Dấu khác nhau của ±0 không làm phép `==` số học đổi từ true sang false, nhưng các mẫu `long` khác nhau.

## 9. Khi truyền bit ra file/network: byte order là một bước riêng

`floatToIntBits`/`doubleToLongBits` trả về **một giá trị `int`/`long` chứa đúng mẫu bit**, chưa biến nó thành mảng byte hoặc quyết định thứ tự byte của giao thức. Khi serialize, cần xác định:

1. Muốn **chuẩn hóa mọi NaN** hay cần giữ raw payload?
2. Định dạng truyền yêu cầu **big-endian** hay **little-endian**?
3. Bên nhận có giải mã bằng đúng độ rộng 32/64 bit và cùng quy tắc NaN không?
4. Dữ liệu có thể chứa `-0.0`, Infinity hay subnormal không, và những giá trị này có được phép theo hợp đồng dữ liệu không?

Ví dụ sử dụng `ByteBuffer` để tạo biểu diễn big-endian hoặc little-endian của **mẫu 32 bit**:

```java
int bits = Float.floatToIntBits(1.0f); // 0x3F800000

byte[] bigEndian = java.nio.ByteBuffer.allocate(4)
    .order(java.nio.ByteOrder.BIG_ENDIAN)
    .putInt(bits)
    .array(); // 3F 80 00 00

byte[] littleEndian = java.nio.ByteBuffer.allocate(4)
    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    .putInt(bits)
    .array(); // 00 00 80 3F
```

Đây là **mảnh mã minh họa** trong phương thức. Hãy chỉ định byte order theo giao thức thay vì mặc định suy diễn từ kiến trúc CPU. `ByteBuffer.putFloat`/`getFloat` và `putDouble`/`getDouble` cũng có API cho dữ liệu số thực; cần hiểu quy tắc biểu diễn/canonical NaN của chúng nếu có yêu cầu bảo toàn payload đặc biệt.

Nếu mục tiêu là bảo toàn **mọi byte thô** của thông điệp nhận được, tốt nhất tiếp tục giữ mảng byte hoặc các mẫu `int`/`long` gốc. Việc chuyển chúng thành giá trị số thực, thực hiện phép toán rồi chuyển lại có thể thay đổi NaN payload, hoặc thay đổi cả giá trị khi tính toán.

## 10. Dùng các API này để làm gì?

| Bài toán | Cách phù hợp | Lưu ý |
| --- | --- | --- |
| Debug dấu, exponent, fraction | `floatToRawIntBits`/`doubleToRawLongBits` + bit masks | Nhớ in hexa và dịch `>>>` |
| Kiểm tra `+0.0` vs `-0.0` | `floatToIntBits`/`doubleToLongBits` | Numeric `==` cho true |
| Tái tạo `float`/`double` từ mẫu 32/64 bit | `intBitsToFloat`/`longBitsToDouble` | Đây là diễn giải bit, không phải numeric cast |
| Hash/so sánh theo NaN canonical | `floatToIntBits`/`doubleToLongBits` | Tương thích với việc chuẩn hóa NaN |
| Quan sát NaN payload đang có | Biến thể `raw` | Không hứa mọi payload signaling tồn tại qua mọi phép toán |
| Lưu/truyền bit pattern chính xác | `int`/`long` hoặc `byte[]`, byte order rõ ràng | Tránh làm trung gian bằng phép tính số thực nếu phải bảo toàn bit |
| Chuyển giá trị số thực thành số nguyên | `(int) value`, `(long) value` hoặc chuyển có kiểm tra | Không dùng `floatToIntBits`/`doubleToLongBits` thay thế |

Phân biệt **biểu diễn số** với **giá trị toán học** cũng giúp xử lý các câu hỏi như “vì sao hai số cùng bằng 0 lại khác hash hoặc khác mã bit?” hoặc “vì sao NaN đọc ra vẫn là NaN nhưng payload thay đổi sau khi lưu?”.

## 11. Các sai lầm thường gặp

1. **“`Float.floatToIntBits(x)` tương đương `(int) x`.”** Sai: hàm trả mẫu 32 bit IEEE 754; cast chuyển giá trị toán học và có quy tắc cắt/biên riêng.
2. **“`Double.doubleToLongBits(x)` luôn giữ nguyên mọi NaN payload.”** Sai: hàm chuẩn hóa mọi NaN.
3. **“Bản `raw` cam kết bit-perfect round-trip mọi signaling NaN qua mọi JVM.”** Sai: quá trình chuyển mẫu signaling NaN thành giá trị `float`/`double` có thể quiet hóa NaN.
4. **“`+0.0` và `-0.0` có cùng bit vì `==` trả true.”** Sai: bit dấu khác nhau.
5. **“Hex `0x80000000` trả về `int` âm nên chắc chắn đã bị overflow.”** Sai: đó là mẫu bit 32 bit, và `int` diễn giải bit cao như bit dấu.
6. **“`Float.intBitsToFloat(0x3F800000)` giống cast `(float) 0x3F800000`.”** Sai: một bên đọc IEEE 754 thành `1.0f`, một bên chuyển số nguyên lớn sang số thực.
7. **“`raw` có thể khôi phục NaN payload sau khi `floatToIntBits` đã chuẩn hóa.”** Sai: bit payload bị mất tại bước chuẩn hóa.
8. **“Chuyển sang `int`/`long` tự quyết định byte order.”** Sai: thứ tự byte chỉ xuất hiện khi serialize thành byte.
9. **“Mẫu NaN bất kỳ đều có thể xử lý như một số thực hữu hạn.”** Sai: NaN không so sánh/hoạt động như giá trị hữu hạn bình thường.
10. **“Dùng bit equality thay cho so sánh số học ở mọi nơi.”** Sai: bit equality cố ý phân biệt ±0 và có cách xử lý NaN khác primitive `==`.

## 12. Tự kiểm tra

- `Float.floatToIntBits(1.0f)` trả gì? **Mẫu `0x3F800000`**, không phải `1`.
- `(int) 1.0f` trả gì? **`1`**, do đây là numeric conversion.
- `Float.floatToIntBits(-0.0f)` và `Float.floatToIntBits(0.0f)` có bằng nhau không? **Không**, vì bit dấu khác nhau.
- `Float.floatToIntBits(Float.intBitsToFloat(0x7FC01234))` có bắt buộc trả `0x7FC01234` không? **Không**. Vì đó là NaN, bản thường trả canonical `0x7FC00000`.
- API nào dùng để quan sát NaN payload còn trong giá trị hiện tại? **`Float.floatToRawIntBits` hoặc `Double.doubleToRawLongBits`**.
- `Double.longBitsToDouble(0x3FF0000000000000L)` cho bao nhiêu? **`1.0`**.
- Nếu phải truyền mẫu bit NaN **chính xác tuyệt đối** giữa hai hệ thống, nên làm gì? **Giữ nguyên `int`/`long` hoặc `byte[]` và quy định byte order**, tránh đưa qua các phép tính có thể chuẩn hóa/quiet hóa NaN.

**Ghi nhớ:** `floatToIntBits` và `doubleToLongBits` đọc mẫu IEEE 754 nhưng **canonical hóa NaN**; các biến thể `raw` quan sát mẫu bit NaN hiện có **không chủ động canonical hóa**. `intBitsToFloat` và `longBitsToDouble` giải mã ngược. Đây là **bit conversion**, khác hoàn toàn với `(int)`/`(long)` hay cast số học; muốn bảo toàn payload và binary protocol, phải kiểm soát nơi lưu bit, cách serialize và các giới hạn khi xử lý NaN.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-representation-011">Chuyển mẫu bit trở lại giá trị float hoặc double</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển mẫu bit trở lại giá trị `float` hoặc `double` trong Java

## 1. Hai việc khác nhau: đổi giá trị số và diễn giải lại bit

Một giá trị dấu phẩy động (*floating-point*) có thể được lưu dưới dạng mẫu bit theo IEEE 754. Để phục hồi **giá trị `float` hoặc `double` từ chính mẫu bit đó**, Java cung cấp hai phương thức:

- `Float.intBitsToFloat(int bits)`: xem 32 bit của một `int` như biểu diễn nhị phân của một `float`.
- `Double.longBitsToDouble(long bits)`: xem 64 bit của một `long` như biểu diễn nhị phân của một `double`.

Điểm quan trọng nhất: **diễn giải lại mẫu bit khác hoàn toàn với ép kiểu số học**.

```java
int bits = 0x3F80_0000; // 1065353216 nếu hiểu là int có dấu
float reconstructed = Float.intBitsToFloat(bits); // 1.0f
float converted = (float) bits; // giá trị số 1065353216 được đổi sang float
```

`Float.intBitsToFloat` lấy nguyên mẫu 32 bit `0x3F800000` và diễn giải theo IEEE 754, thu được **1.0**. Trong khi đó, `(float) bits` chuyển **giá trị số nguyên** 1_065_353_216 sang kiểu `float`. Hai phép toán có ý nghĩa khác nhau dù cùng xuất phát từ một biến `int`.

Đối với 64 bit, `Double.longBitsToDouble(bits)` khác `(double) bits` tương tự. Khi xử lý file nhị phân, giao thức mạng, log hex, kiểm thử hoặc phân tích bit, cần xác định dữ liệu đang là **bit pattern** hay **giá trị số nguyên cần chuyển đổi**.

## 2. Những API cần biết

| Mục đích | API Java | Kiểu đầu vào | Kiểu trả về |
|---|---|---|---|
| Bit pattern → `float` | `Float.intBitsToFloat(bits)` | `int`, 32 bit | `float` |
| Bit pattern → `double` | `Double.longBitsToDouble(bits)` | `long`, 64 bit | `double` |
| `float` → bit pattern nguyên bản | `Float.floatToRawIntBits(value)` | `float` | `int` |
| `double` → bit pattern nguyên bản | `Double.doubleToRawLongBits(value)` | `double` | `long` |
| `float` → bit pattern chuẩn hóa NaN | `Float.floatToIntBits(value)` | `float` | `int` |
| `double` → bit pattern chuẩn hóa NaN | `Double.doubleToLongBits(value)` | `double` | `long` |

Các phương thức `intBitsToFloat` và `longBitsToDouble` là `static`, có thể gọi trực tiếp từ tên lớp bao mà không tạo đối tượng `Float`/`Double`.

**Phân biệt hai họ phương thức đi từ giá trị sang bit:** phương thức có chữ `Raw` cố gắng trả về mẫu bit thực tế của giá trị, kể cả thông tin payload NaN khi biểu diễn cho phép. Phương thức không có `Raw` chuẩn hóa mọi NaN thành một mẫu NaN chuẩn:

- `Float.floatToIntBits(Float.NaN)` trả `0x7FC00000`.
- `Double.doubleToLongBits(Double.NaN)` trả `0x7FF8000000000000L`.

Vì NaN có nhiều biểu diễn bit, không nên mặc định một phép đi-về sẽ giữ **mọi bit NaN** trong mọi tình huống; phần này được giải thích kỹ hơn ở mục 7.

## 3. Cấu trúc biểu diễn IEEE 754 binary32 và binary64

Java `float` dùng **binary32**, còn `double` dùng **binary64** của IEEE 754:

| Thành phần | `float` (32 bit) | `double` (64 bit) |
|---|---:|---:|
| Bit dấu (*sign*) | 1 | 1 |
| Số bit số mũ (*exponent*) | 8 | 11 |
| Số bit phần phân số (*fraction/significand field*) | 23 | 52 |
| Độ lệch số mũ (*bias*) | 127 | 1023 |
| Tổng | 32 | 64 |

Cách bố trí từ bit cao xuống bit thấp:

```text
float (32 bit):
S | EEEEEEEE | FFFFFFFFFFFFFFFFFFFFFFF
1 |    8     |           23

double (64 bit):
S | EEEEEEEEEEE | FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF
1 |     11      |                         52
```

Ở đây:

- **S**: dấu, bằng 0 khi số không âm và bằng 1 khi số âm.
- **E**: trường số mũ được lưu có độ lệch (*biased exponent*).
- **F**: trường phân số, lưu các bit sau dấu chấm của phần trị; số chuẩn hóa có **bit 1 ẩn** đứng trước phần phân số.

Với số **chuẩn hóa** (*normal*) có trường số mũ nằm giữa 0 và giá trị toàn bit 1, giá trị toán học là:

\[
(-1)^S \times \left(1+\frac{F}{2^p}\right) \times 2^{E-\text{bias}}
\]

Trong đó `p = 23` cho `float`, `p = 52` cho `double`, còn `F` là số nguyên không dấu do các bit phần phân số biểu diễn.

Với trường số mũ bằng **0**, công thức đổi thành số **dưới chuẩn** (*subnormal*) khi `F != 0`:

\[
(-1)^S \times \frac{F}{2^p} \times 2^{1-\text{bias}}
\]

Nếu `E = 0` và `F = 0`, kết quả là **+0.0 hoặc -0.0**, tùy bit dấu. Nếu `E` gồm toàn bit 1, cách diễn giải đặc biệt được mô tả ở mục 6: **vô cực** hoặc **NaN**.

**Lưu ý:** không phải mọi mẫu 32/64 bit đều biểu diễn một số thực hữu hạn. Mọi mẫu đều có thể được diễn giải bằng API, nhưng kết quả có thể là số hữu hạn, ±0.0, ±Infinity hoặc NaN.

## 4. Phân tích một mẫu bit `float` cụ thể

Xét `0x40400000`, gồm 32 bit:

```text
Hex:     0x40400000
Binary:  01000000 01000000 00000000 00000000
Fields:  0 | 10000000 | 10000000000000000000000
         S |     E    |           F
```

Tính theo từng trường:

1. Bit dấu `S = 0`, nên kết quả dương.
2. Trường số mũ nhị phân `10000000` bằng 128, số mũ thực là \(128 - 127 = 1\).
3. Phần phân số có bit đầu là 1 và còn lại là 0, nên \(F/2^{23} = 1/2\).
4. Phần trị chuẩn hóa là \(1 + 1/2 = 1.5\).
5. Giá trị cuối: \(1.5 \times 2^1 = 3.0\).

Trong Java:

```java
float value = Float.intBitsToFloat(0x4040_0000);
System.out.println(value); // 3.0
```

Đổi bit dấu thành 1 mà giữ nguyên số mũ và phân số cho `0xC0400000`, được **-3.0**:

```java
float negative = Float.intBitsToFloat(0xC040_0000);
System.out.println(negative); // -3.0
```

Đối với `double`, mẫu `0x4008000000000000L` cũng biểu diễn 3.0, nhưng phân bố bit và độ lệch số mũ khác:

```java
double d = Double.longBitsToDouble(0x4008_0000_0000_0000L);
System.out.println(d); // 3.0
```

Có thể thấy **giá trị số 3.0 có bit pattern khác nhau** trong `float` và `double`. Không thể đưa trực tiếp mẫu 32 bit dành cho `float` vào `Double.longBitsToDouble` rồi kỳ vọng lấy đúng cùng giá trị.

## 5. Ví dụ Java 17+: phục hồi giá trị từ mẫu bit

Tạo tệp `BitsToFloatingDemo.java`:

```java
public class BitsToFloatingDemo {
    public static void main(String[] args) {
        int oneBits = 0x3F80_0000;
        int threeBits = 0x4040_0000;
        int minusThreeBits = 0xC040_0000;
        long doubleThreeBits = 0x4008_0000_0000_0000L;

        System.out.println("float 1 = "
                + Float.intBitsToFloat(oneBits));
        System.out.println("float 3 = "
                + Float.intBitsToFloat(threeBits));
        System.out.println("float -3 = "
                + Float.intBitsToFloat(minusThreeBits));
        System.out.println("double 3 = "
                + Double.longBitsToDouble(doubleThreeBits));

        float reconstructed = Float.intBitsToFloat(oneBits);
        float numericCast = (float) oneBits;
        System.out.println("same as numeric cast? "
                + (reconstructed == numericCast));

        int recovered = Float.floatToRawIntBits(reconstructed);
        System.out.printf("recovered float bits = %08X%n", recovered);

        double pi = Double.longBitsToDouble(
                0x4009_21FB_5444_2D18L);
        System.out.println("double pi = " + pi);
        System.out.printf("double pi bits = %016X%n",
                Double.doubleToRawLongBits(pi));
    }
}
```

Biên dịch và chạy:

```bash
javac BitsToFloatingDemo.java
java BitsToFloatingDemo
```

Kết quả:

```text
float 1 = 1.0
float 3 = 3.0
float -3 = -3.0
double 3 = 3.0
same as numeric cast? false
recovered float bits = 3F800000
double pi = 3.141592653589793
double pi bits = 400921FB54442D18
```

Giá trị π ở đây là **xấp xỉ binary64 gần nhất**, không phải π toán học vô hạn chữ số. Dòng in bit cho thấy mẫu binary64 đã được phục hồi từ một giá trị hữu hạn bình thường.

## 6. Các mẫu đặc biệt: ±0.0, số dưới chuẩn, ±Infinity và NaN

Dịch nghĩa trường số mũ và phần phân số:

| Trường số mũ E | Phân số F | Giá trị |
|---|---|---|
| 0 | 0 | +0.0 hoặc -0.0 theo bit dấu |
| 0 | khác 0 | Số dưới chuẩn (*subnormal*) |
| Từ 1 đến giá trị gần toàn-1 | Bất kỳ | Số chuẩn hóa hữu hạn |
| Toàn-1 | 0 | +Infinity hoặc -Infinity |
| Toàn-1 | khác 0 | NaN (*Not a Number*) |

### 6.1. Các mẫu `float` quen thuộc

| Mẫu 32 bit | Ý nghĩa |
|---|---|
| `0x00000000` | +0.0f |
| `0x80000000` | -0.0f |
| `0x00000001` | Số dưới chuẩn dương nhỏ nhất |
| `0x00800000` | Số chuẩn hóa dương nhỏ nhất |
| `0x3F800000` | 1.0f |
| `0x7F800000` | +Infinity |
| `0xFF800000` | -Infinity |
| `0x7FC00000` | NaN chuẩn |

### 6.2. Các mẫu `double` tương ứng

| Mẫu 64 bit | Ý nghĩa |
|---|---|
| `0x0000000000000000L` | +0.0 |
| `0x8000000000000000L` | -0.0 |
| `0x0000000000000001L` | Số dưới chuẩn dương nhỏ nhất |
| `0x0010000000000000L` | Số chuẩn hóa dương nhỏ nhất |
| `0x3FF0000000000000L` | 1.0 |
| `0x7FF0000000000000L` | +Infinity |
| `0xFFF0000000000000L` | -Infinity |
| `0x7FF8000000000000L` | NaN chuẩn |

Dạng hex có hậu tố `L` là literal `long`, kể cả khi bit cao nhất là 1 khiến giá trị `long` mang dấu âm. `Double.longBitsToDouble` **chỉ xét toàn bộ 64 bit**, không coi giá trị số `long` âm là yêu cầu trả `double` âm. Dấu `double` được xác định từ bit dấu của chính mẫu IEEE 754.

### 6.3. Hai số 0 có cùng so sánh số học nhưng khác mẫu bit

```java
float plusZero = Float.intBitsToFloat(0x0000_0000);
float minusZero = Float.intBitsToFloat(0x8000_0000);

System.out.println(plusZero == minusZero); // true
System.out.println(1.0f / plusZero);       // Infinity
System.out.println(1.0f / minusZero);      // -Infinity
```

Dù `+0.0f == -0.0f` là `true` theo toán tử số học, bit pattern của chúng **không giống nhau**. Điều này quan trọng khi serialize chính xác, ký số theo byte, debug số học hoặc nghiên cứu dấu của giá trị 0.

### 6.4. Phân biệt NaN qua phép kiểm tra

`NaN` là kết quả không phải số, ví dụ `0.0 / 0.0` trong số học dấu phẩy động. Java có các phương thức:

- `Float.isNaN(value)` hoặc `Double.isNaN(value)`.
- `Float.isInfinite(value)` hoặc `Double.isInfinite(value)`.
- `Float.isFinite(value)` hoặc `Double.isFinite(value)`.

Một mẫu có số mũ toàn-1 và phân số khác 0 là NaN, bất kể bit dấu là 0 hay 1. Các bit phân số có thể chứa *NaN payload*, nhưng không nên coi đó là dữ liệu số bình thường.

## 7. Round-trip và vấn đề NaN payload

Với giá trị hữu hạn, ±0.0 và ±Infinity, hai phép chuyển đổi thường cho phép kiểm tra bit nguyên bản chính xác:

```java
int bits = 0xBF80_0000; // mẫu float -1.0
float value = Float.intBitsToFloat(bits);
int back = Float.floatToRawIntBits(value);

System.out.println(back == bits); // true
```

Với NaN, phải phân biệt **giá trị được phân loại là NaN** với **mọi bit payload có được bảo toàn không**:

1. `Float.intBitsToFloat(bits)` và `Double.longBitsToDouble(bits)` diễn giải mẫu bit theo IEEE 754.
2. `Float.floatToIntBits(value)` và `Double.doubleToLongBits(value)` **chuẩn hóa NaN** về một mẫu NaN chuẩn; không giữ payload gốc.
3. `floatToRawIntBits` và `doubleToRawLongBits` dành cho việc đọc bit nguyên bản đang có. Tuy vậy, một số cấu trúc phần cứng hoặc thao tác có thể **quiet** một *signaling NaN* khi mẫu bit được chuyển thành giá trị floating-point. Không thể cam kết bit-perfect round-trip cho **mọi mẫu NaN báo hiệu** trên mọi môi trường thực thi.

Bởi vậy, nếu mục tiêu là **lưu/truyền payload bit đúng tuyệt đối**, ví dụ lưu file nhị phân với một payload NaN cụ thể, giải pháp chắc chắn nhất là **giữ mẫu gốc ở `int`/`long` hoặc mảng byte** trong suốt quá trình truyền; chỉ gọi `intBitsToFloat` hoặc `longBitsToDouble` khi thực sự cần tính toán trên giá trị số.

Nếu mục tiêu chỉ là kiểm tra dữ liệu có phải NaN hay không, dùng `isNaN`; không cần so trực tiếp bit với một mẫu NaN duy nhất. Phép so sánh `NaN == NaN` cho `false` theo quy tắc dấu phẩy động.

### Ví dụ runnable cho NaN và zero

Lưu file `SpecialFloatingBitsDemo.java`:

```java
public class SpecialFloatingBitsDemo {
    public static void main(String[] args) {
        float plusZero = Float.intBitsToFloat(0x0000_0000);
        float minusZero = Float.intBitsToFloat(0x8000_0000);
        float plusInfinity = Float.intBitsToFloat(0x7F80_0000);
        float minusInfinity = Float.intBitsToFloat(0xFF80_0000);
        float nan = Float.intBitsToFloat(0x7FC0_0000);

        System.out.println("zeros equal = " + (plusZero == minusZero));
        System.out.printf("plus zero bits = %08X%n",
                Float.floatToRawIntBits(plusZero));
        System.out.printf("minus zero bits = %08X%n",
                Float.floatToRawIntBits(minusZero));
        System.out.println("1 / +0 = " + (1.0f / plusZero));
        System.out.println("1 / -0 = " + (1.0f / minusZero));
        System.out.println("positive infinity = " + plusInfinity);
        System.out.println("negative infinity = " + minusInfinity);
        System.out.println("is NaN = " + Float.isNaN(nan));
        System.out.println("NaN == NaN = " + (nan == nan));
        System.out.printf("canonical NaN bits = %08X%n",
                Float.floatToIntBits(nan));

        double tiny = Double.longBitsToDouble(1L);
        System.out.println("double smallest positive = " + tiny);
    }
}
```

Biên dịch và chạy:

```bash
javac SpecialFloatingBitsDemo.java
java SpecialFloatingBitsDemo
```

Kết quả:

```text
zeros equal = true
plus zero bits = 00000000
minus zero bits = 80000000
1 / +0 = Infinity
1 / -0 = -Infinity
positive infinity = Infinity
negative infinity = -Infinity
is NaN = true
NaN == NaN = false
canonical NaN bits = 7FC00000
double smallest positive = 4.9E-324
```

Mẫu `0x7FC00000` được dùng ở ví dụ là NaN chuẩn, nên kết quả của `floatToIntBits` là xác định. Ví dụ **không khẳng định** payload bất kỳ của signaling NaN sẽ giữ nguyên khi đưa qua giá trị `float`.

## 8. Chuyển chuỗi hex/binary nhập vào thành mẫu bit

Một công cụ debug thường hiển thị mẫu IEEE 754 dưới dạng chuỗi, chẳng hạn `"40400000"` hoặc `"4008000000000000"`. Cần **parse chuỗi thành `int`/`long` mang bit tương ứng**, rồi mới gọi API diễn giải mẫu.

Ví dụ:

```java
int floatBits = Integer.parseUnsignedInt("40400000", 16);
float a = Float.intBitsToFloat(floatBits); // 3.0f

long doubleBits = Long.parseUnsignedLong("4008000000000000", 16);
double b = Double.longBitsToDouble(doubleBits); // 3.0
```

Tại sao dùng `parseUnsignedInt`/`parseUnsignedLong`?

- Các mẫu 32 bit hoặc 64 bit có thể bật bit cao nhất.
- `Integer.parseInt("FF800000", 16)` không thể parse như một giá trị `int` dương có dấu, nhưng `Integer.parseUnsignedInt("FF800000", 16)` có thể lưu đúng **mẫu 32 bit** trong `int` (giá trị `int` có thể âm).
- Tương tự, `Long.parseUnsignedLong("FFF0000000000000", 16)` giữ đúng **64 bit**, dù giá trị `long` khi xem là số có dấu là âm.

```java
int negativeInfinityBits =
        Integer.parseUnsignedInt("FF800000", 16);
float negativeInfinity =
        Float.intBitsToFloat(negativeInfinityBits); // -Infinity
```

API parse không tự bỏ mọi tiền tố một cách tùy ý: ví dụ `Integer.parseUnsignedInt("0x40400000", 16)` không phải cú pháp hợp lệ vì chứa `0x`. Nếu dữ liệu nhận có tiền tố, hãy kiểm tra đầu vào rồi bỏ tiền tố theo định dạng quy ước trước khi parse.

Nếu nguồn là chuỗi nhị phân 32 bit, dùng `Integer.parseUnsignedInt(text, 2)`; với 64 bit dùng `Long.parseUnsignedLong(text, 2)`. Luôn xác thực độ dài, cơ số và định dạng khi đọc dữ liệu từ nguồn không đáng tin, tránh âm thầm chấp nhận số ký tự hoặc độ rộng không đúng giao thức.

### Ví dụ runnable: parse mẫu 32/64 bit

Tạo file `ParseFloatingBitsDemo.java`:

```java
public class ParseFloatingBitsDemo {
    public static void main(String[] args) {
        int floatBits = Integer.parseUnsignedInt("40400000", 16);
        long doubleBits = Long.parseUnsignedLong(
                "4008000000000000", 16);

        System.out.println("float: "
                + Float.intBitsToFloat(floatBits));
        System.out.println("double: "
                + Double.longBitsToDouble(doubleBits));

        int negativeInfBits =
                Integer.parseUnsignedInt("FF800000", 16);
        System.out.println("negative infinity: "
                + Float.intBitsToFloat(negativeInfBits));

        System.out.printf("roundtrip float: %08X%n",
                Float.floatToRawIntBits(
                        Float.intBitsToFloat(floatBits)));
        System.out.printf("roundtrip double: %016X%n",
                Double.doubleToRawLongBits(
                        Double.longBitsToDouble(doubleBits)));
    }
}
```

Chạy:

```bash
javac ParseFloatingBitsDemo.java
java ParseFloatingBitsDemo
```

Kết quả:

```text
float: 3.0
double: 3.0
negative infinity: -Infinity
roundtrip float: 40400000
roundtrip double: 4008000000000000
```

## 9. Byte order: khi mẫu bit nằm trong file hoặc gói mạng

Các API `Float.intBitsToFloat` và `Double.longBitsToDouble` nhận **sẵn một `int`/`long`**, không tự đọc byte hay quyết định thứ tự byte. Khi mẫu bit được truyền dưới dạng 4 hoặc 8 byte, phải ghép đúng theo **endianness**:

- **Big-endian**: byte trọng số cao đứng trước. Mẫu `0x3F800000` có các byte `3F 80 00 00`.
- **Little-endian**: byte trọng số thấp đứng trước. Mẫu `0x3F800000` có các byte `00 00 80 3F`.

Java NIO `ByteBuffer` hỗ trợ `ByteOrder.BIG_ENDIAN` và `ByteOrder.LITTLE_ENDIAN`. Khi gọi `getFloat()`/`getDouble()`, thư viện đọc byte theo thứ tự đã cấu hình và diễn giải chúng thành dấu phẩy động.

Ví dụ chạy được, file `FloatingByteOrderDemo.java`:

```java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FloatingByteOrderDemo {
    public static void main(String[] args) {
        byte[] bigEndian = {
                0x3F, (byte) 0x80, 0x00, 0x00
        };
        byte[] littleEndian = {
                0x00, 0x00, (byte) 0x80, 0x3F
        };

        float a = ByteBuffer.wrap(bigEndian)
                .order(ByteOrder.BIG_ENDIAN)
                .getFloat();
        float b = ByteBuffer.wrap(littleEndian)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat();

        System.out.println("big endian: " + a);
        System.out.println("little endian: " + b);
        System.out.println("same value: " + (a == b));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingByteOrderDemo.java
java FloatingByteOrderDemo
```

Kết quả:

```text
big endian: 1.0
little endian: 1.0
same value: true
```

Nếu cấu hình sai thứ tự byte, cùng bốn byte có thể tạo giá trị rất khác, dù chương trình không báo lỗi. Với `double`, cần đủ 8 byte và chọn order tương ứng. Ngoài ra, phải kiểm tra số byte còn lại, cấu trúc gói tin và phiên bản định dạng, vì `getFloat()`/`getDouble()` không tự biết bạn đang đọc đúng vị trí dữ liệu hay chưa.

## 10. Có thể tự tách ba trường bit để kiểm tra

Khi cần phân tích vì sao một bit pattern tạo ra giá trị nào, có thể dùng phép dịch logic `>>>` và AND theo bit `&` để tách dấu, số mũ, phân số.

### Float 32 bit

```java
int bits = 0x4040_0000;

int sign = (bits >>> 31) & 1;
int exponent = (bits >>> 23) & 0xFF;
int fraction = bits & 0x7F_FFFF;

// sign=0, exponent=128, fraction=0x400000
```

### Double 64 bit

```java
long bits = 0x4008_0000_0000_0000L;

long sign = (bits >>> 63) & 1L;
long exponent = (bits >>> 52) & 0x7FFL;
long fraction = bits & 0x000F_FFFF_FFFF_FFFFL;

// sign=0, exponent=1024, fraction=0x8000000000000
```

Đây là **những đoạn mã minh họa** cần đặt trong phương thức hoặc chương trình Java để chạy. Chúng hữu ích khi debug biểu diễn, nhưng trong chương trình thông thường hãy ưu tiên `Float.intBitsToFloat`/`Double.longBitsToDouble` vì các API này xử lý đúng cả số dưới chuẩn và những giá trị đặc biệt.

**Bẫy phổ biến khi tự tính công thức:** số dưới chuẩn không có bit 1 ẩn, số mũ được diễn giải khác trường hợp chuẩn hóa, và tất cả mẫu có `E` toàn-1 phải xử lý Infinity/NaN riêng. Chỉ áp dụng công thức của số chuẩn hóa cho mọi mẫu sẽ cho kết quả sai.

## 11. Những nhầm lẫn và lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `(float) bits` không ra giá trị từ IEEE 754 | Cast là đổi giá trị số, không phải reinterpret bit | Dùng `Float.intBitsToFloat(bits)` |
| `(double) bits` không đọc đúng mẫu 64 bit | Cast `long → double` là chuyển số học | Dùng `Double.longBitsToDouble(bits)` |
| Mẫu có bit dấu 1 hiển thị như `int/long` âm | Primitive integer có dấu, nhưng bit pattern vẫn nguyên | Giữ nguyên bit, dùng API diễn giải IEEE 754 |
| Parse chuỗi `FF800000` bằng `parseInt` bị lỗi | Vượt miền `int` có dấu ở dạng dương | Dùng `parseUnsignedInt(..., 16)` |
| `parseUnsignedInt("0x...",16)` báo lỗi | API parse không nhận tiền tố literal `0x` | Bỏ tiền tố sau khi xác thực |
| `+0.0 == -0.0` nhưng bit khác nhau | IEEE 754 có hai số 0 có dấu | So mẫu qua `floatToRawIntBits` nếu cần đúng bit |
| NaN round-trip mất payload | API không Raw chuẩn hóa NaN; signaling NaN có thể bị quiet | Giữ mẫu nguyên `int/long` nếu đòi bit-perfect |
| Diễn giải byte ra giá trị sai | Sai endian, sai offset hoặc sai độ rộng | Kiểm tra giao thức, `ByteOrder`, chiều dài dữ liệu |
| Tưởng `float` 64 bit | `float` dùng 32 bit, `double` dùng 64 bit | Chọn đúng API và độ rộng |
| Tự tách bit cho Infinity như số thường | `E` toàn-1 có quy tắc đặc biệt | Xử lý Infinity/NaN trước công thức |
| Kết quả mất precision dù bit đúng | Binary32/binary64 chỉ biểu diễn hữu hạn độ chính xác | Giữ nguyên định dạng nguồn hoặc chọn cách biểu diễn phù hợp |

## 12. Kiểm tra giá trị sau khi phục hồi

Nếu mẫu bit đến từ dữ liệu bên ngoài, bạn có thể **phục hồi đúng bit** nhưng kết quả lại là NaN hoặc Infinity mà nghiệp vụ không cho phép. Hãy kiểm tra sau khi diễn giải:

```java
static float requireFiniteFloat(int bits) {
    float value = Float.intBitsToFloat(bits);
    if (!Float.isFinite(value)) {
        throw new IllegalArgumentException("non-finite float");
    }
    return value;
}
```

Đây là **phương thức minh họa**, đặt trong class Java để sử dụng. Tương tự dùng `Double.isFinite(Double.longBitsToDouble(bits))` đối với `double`.

`isFinite` chấp nhận cả số dưới chuẩn và ±0.0; nó chỉ loại NaN và ±Infinity. Nếu nghiệp vụ không cho phép các trường hợp khác (ví dụ số âm, số dưới chuẩn, quá lớn/nhỏ về mặt ứng dụng), cần kiểm tra thêm các ràng buộc cụ thể.

Khi yêu cầu là kiểm tra dữ liệu **thô** mà không mất NaN payload, nên kiểm tra cấu trúc bit trên `int/long` trước khi chuyển thành `float/double`. Chẳng hạn kiểm tra exponent toàn-1 và fraction khác 0 để nhận diện NaN ngay trên mẫu gốc; cách này tránh dựa vào payload sau một số thao tác floating-point.

## 13. Bài tập tự kiểm tra

**Câu 1.** `Float.intBitsToFloat(0x3F800000)` bằng bao nhiêu?  
**Đáp án:** 1.0f, vì đó là mẫu binary32 của 1.

**Câu 2.** `(float) 0x3F800000` có bằng `Float.intBitsToFloat(0x3F800000)` không?  
**Đáp án:** Không. Phép cast chuyển **số nguyên** 1_065_353_216 thành `float`; phương thức kia diễn giải lại **32 bit** thành 1.0f.

**Câu 3.** `Double.longBitsToDouble(0x4008000000000000L)` bằng bao nhiêu?  
**Đáp án:** 3.0.

**Câu 4.** `0x7F800000` và `0xFF800000` biểu diễn gì khi được chuyển bằng `Float.intBitsToFloat`?  
**Đáp án:** +Infinity và -Infinity.

**Câu 5.** `0x00000001` là mẫu `float` nào?  
**Đáp án:** Số dưới chuẩn dương nhỏ nhất, xấp xỉ 1.4E-45.

**Câu 6.** Hai số `+0.0f` và `-0.0f` có bằng nhau qua `==` không? Mẫu bit có giống nhau không?  
**Đáp án:** `==` là true, nhưng bit khác: lần lượt `0x00000000` và `0x80000000`.

**Câu 7.** Vì sao `Float.floatToIntBits` không đủ để lưu chính xác một NaN payload tùy ý?  
**Đáp án:** Vì phương thức đó chuẩn hóa mọi NaN về mẫu NaN chuẩn. Nếu phải giữ đúng payload, cần làm việc với dữ liệu raw và chú ý signaling NaN có thể bị quiet.

**Câu 8.** Chuỗi hex `"FF800000"` nên đọc thế nào để phục hồi `float` -Infinity?  
**Đáp án:** `int bits = Integer.parseUnsignedInt("FF800000", 16); float value = Float.intBitsToFloat(bits);`.

**Câu 9.** Nếu file chứa byte `00 00 80 3F` để biểu diễn 1.0f thì đó là thứ tự nào?  
**Đáp án:** Little-endian. Dùng `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)` khi đọc.

**Câu 10.** Tại sao không thể dùng công thức số chuẩn hóa cho bit pattern có exponent toàn 1?  
**Đáp án:** Exponent toàn-1 được dành cho Infinity (fraction bằng 0) và NaN (fraction khác 0), không phải số chuẩn hóa hữu hạn.

**Câu 11.** Muốn lấy bit pattern nguyên bản từ `double value`, dùng gì?  
**Đáp án:** `Double.doubleToRawLongBits(value)`. Nếu muốn mọi NaN chuẩn hóa thành một mẫu chung, dùng `Double.doubleToLongBits(value)`.

**Câu 12.** Nếu mục tiêu là kiểm tra bit pattern đã truyền qua mạng giữ nguyên 100% kể cả signaling NaN, có nên buộc nó đi qua `float` rồi mới so không?  
**Đáp án:** Không. Giữ và so trực tiếp các byte hoặc `int/long` gốc, vì trong một số môi trường signaling NaN có thể bị quiet khi đi qua giá trị dấu phẩy động.

## 14. Ghi nhớ

- **`Float.intBitsToFloat(int)`** diễn giải đúng 32 bit theo binary32; **`Double.longBitsToDouble(long)`** diễn giải đúng 64 bit theo binary64.
- **Cast `(float)`/`(double)` là đổi giá trị số**, khác với việc diễn giải lại mẫu bit.
- Mẫu IEEE 754 gồm **sign, exponent, fraction**; phải phân biệt giá trị chuẩn hóa, dưới chuẩn, ±0.0, ±Infinity và NaN.
- Với dữ liệu cần bảo toàn **mọi bit**, lưu giữ mẫu thô bằng `int/long` hoặc byte; chú ý chuẩn hóa hoặc quiet NaN.
- Khi đọc byte từ file/mạng, **endianness và độ rộng** phải đúng trước khi dùng API.
- Với đầu vào từ bên ngoài, phục hồi mẫu bit thành giá trị là một bước kỹ thuật; **kiểm tra finite và miền nghiệp vụ** là bước riêng.

Tài liệu chuẩn: [Float.intBitsToFloat — Java API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Float.html#intBitsToFloat(int)) và [Double.longBitsToDouble — Java API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#longBitsToDouble(long)).


</details>

- [Quay lại đầu trang](#back-to-top)
