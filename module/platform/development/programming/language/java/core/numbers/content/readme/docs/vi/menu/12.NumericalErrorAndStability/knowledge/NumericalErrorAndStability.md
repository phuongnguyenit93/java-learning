<a id="back-to-top"></a>

# Sai số và ổn định

## Menu
- [Sai số biểu diễn khác sai số thuật toán](#numerical-error-and-stability-001)
- [Sai số tuyệt đối, tương đối và tích lũy qua nhiều bước](#numerical-error-and-stability-002)
- [Giới hạn của hằng epsilon cố định cho mọi bài toán](#numerical-error-and-stability-003)
- [Sắp xếp thứ tự phép tính để giảm mất thông tin](#numerical-error-and-stability-004)
- [Tổng bù và tư tưởng Kahan summation](#numerical-error-and-stability-005)
- [Điều kiện bài toán và trường hợp sai số đầu vào bị khuếch đại](#numerical-error-and-stability-006)
- [Không làm tròn sau mọi phép tính nếu hợp đồng không yêu cầu](#numerical-error-and-stability-007)
- [Định dạng ít chữ số không sửa sai số của giá trị đang lưu](#numerical-error-and-stability-008)
- [Chọn double hay BigDecimal theo mô hình dữ liệu](#numerical-error-and-stability-009)
- [Kiểm chứng thuật toán bằng dữ liệu biên và kết quả tham chiếu](#numerical-error-and-stability-010)

## <a id="numerical-error-and-stability-001">Sai số biểu diễn khác sai số thuật toán</a>

<details>
<summary>Xem chi tiết</summary>

# Sai số biểu diễn khác sai số thuật toán trong Java

> **Java core – numbers · Sai số và ổn định**  
> **Mục tiêu:** Phân biệt sai số do lưu trữ số trong `float`/`double` với sai số do cách xây dựng phép tính hoặc thuật toán; giải thích tại sao dùng `double` chưa chắc cho kết quả đúng, và biết cách đo, giảm, kiểm thử sai số một cách có cơ sở.

## 1. Cùng một kết quả lệch, nguyên nhân có thể hoàn toàn khác

Giả sử bài toán toán học có kết quả thực là `T`, còn chương trình Java trả `R`. Việc `R ≠ T` **không đủ để kết luận** “do máy tính không lưu được số thực chính xác”.

Có ít nhất ba nguồn khác nhau:

1. **Sai số biểu diễn (*representation error*):** giá trị đầu vào hoặc hằng số toán học không được biểu diễn chính xác bằng bit của kiểu đang dùng, chẳng hạn literal `double 0.1`.
2. **Sai số làm tròn của phép toán (*roundoff error*):** kết quả của phép cộng, trừ, nhân, chia hoặc hàm toán học được làm tròn về định dạng hữu hạn. Nó liên quan đến số học dấu phẩy động nhưng có thể phát sinh **sau khi** đầu vào đã được biểu diễn chính xác.
3. **Sai số thuật toán (*algorithmic error*):** cách tính được lựa chọn gây mất thông tin, phóng đại sai số, dừng xấp xỉ quá sớm hoặc dùng một công thức số học không ổn định. Ngay cả khi dữ liệu được lưu tốt, thuật toán vẫn có thể cho kết quả kém.

Hai nhóm thường được đặt đối chiếu trong môn học này là **sai số biểu diễn** và **sai số thuật toán**; sai số làm tròn trong các phép toán là mắt xích quan trọng kết nối chúng. Cần phân biệt **sai số do bài toán/xấp xỉ** (chẳng hạn chỉ lấy vài số hạng chuỗi Taylor) với **sự bất ổn của cách triển khai** (chẳng hạn trừ hai số gần bằng nhau).

| Vấn đề | Phát sinh từ đâu? | Triệu chứng | Hướng xử lý điển hình |
| --- | --- | --- | --- |
| Biểu diễn | Số toán học không có mẫu bit chính xác trong kiểu | `0.1 + 0.2` không đúng bit với `0.3` | Chọn định dạng/kiểu phù hợp, `BigDecimal` cho decimal exact |
| Làm tròn từng bước | Phép tính có kết quả nằm giữa các số biểu diễn được | Cộng 1 vào `1e16` không thay đổi giá trị `double` | Phân tích ULP, cách nhóm, độ lớn và sai số trung gian |
| Thuật toán thiếu ổn định | Công thức/sơ đồ tính khuếch đại sai số | `sqrt(x*x+1)-x` trả 0 dù đáp án dương | Viết lại công thức tương đương ổn định hơn |
| Sai số xấp xỉ | Thuật toán cố ý dùng gần đúng hoặc dừng sớm | Dùng `1+x` thay cho `e^x` với `x` lớn | Tăng bậc xấp xỉ, kiểm soát tiêu chuẩn dừng |
| Dữ liệu đầu vào | Phép đo, làm tròn trước khi đọc vào chương trình | Hai nguồn dữ liệu đã khác nhau trước Java | Kiểm soát đo lường, định dạng nhập và hợp đồng dữ liệu |

**Điều cần nhớ:** Một lỗi số học có thể là tổng hợp nhiều nguồn sai số. Việc đổi `float` thành `double` đôi khi cải thiện đáng kể; nhưng nếu công thức tính không ổn định hoặc bài toán bị xấp xỉ sai ngay từ đầu, tăng số bit không nhất thiết giải quyết tận gốc.

## 2. Sai số biểu diễn là gì?

Máy tính lưu một số hữu hạn bit. `float` có 32 bit (24 bit chính xác hiệu dụng đối với số normal), `double` có 64 bit (53 bit chính xác hiệu dụng đối với số normal). Tập các số có thể lưu chính xác trong chúng **không phải toàn bộ số thực**.

Trong hệ 2, một số dạng `m / 2^k` có thể có biểu diễn hữu hạn nếu nằm trong miền và độ chính xác của kiểu. Nhưng phân số thập phân như `1/10` có biểu diễn nhị phân vô hạn tuần hoàn:

```text
0.1 (thập phân) = 0.000110011001100110011... (nhị phân)
                                     ^ lặp lại
```

Vì vậy literal:

```java
double x = 0.1;
```

không lưu chính xác phân số toán học `1/10`. Java chọn giá trị `double` gần nhất theo quy tắc làm tròn của dạng literal.

```java
System.out.println(Double.toHexString(0.1));
// 0x1.999999999999ap-4
```

Dạng hexadecimal floating-point hiển thị số nhị phân thực sự đã được lưu. Chuỗi này mô tả chính xác một giá trị `double` gần 0.1, nhưng **không** biến nó thành phân số thập phân 0.1 chính xác.

Một biểu hiện quen thuộc:

```java
System.out.println(0.1 + 0.2);        // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false
```

Ở đây đã có **sai số biểu diễn của đầu vào** và quá trình cộng/làm tròn. Việc in ra một chuỗi dài không có nghĩa bản thân `System.out.println` tính sai; nó chỉ chuyển giá trị nhị phân được lưu sang chuỗi thập phân để hiển thị.

### Khi số có thể biểu diễn chính xác

Không phải số decimal nào cũng bị sai số biểu diễn trong nhị phân:

```java
double a = 0.5;    // 1/2, biểu diễn chính xác
double b = 0.25;   // 1/4, biểu diễn chính xác
double c = 0.125;  // 1/8, biểu diễn chính xác

System.out.println(a + b + c); // 0.875
```

Các ví dụ này đều là tổng các phân số có mẫu là lũy thừa của 2 và có độ lớn vừa miền. Ngược lại, số nguyên đủ lớn cũng có thể mất thông tin khi chuyển sang `double`, dù viết hoàn toàn bằng chữ số nguyên thập phân.

## 3. Biểu diễn số nguyên lớn trong double cũng có giới hạn

`double` có 53 bit chính xác hiệu dụng. Nó biểu diễn chính xác mọi số nguyên trong miền từ `−2^53` đến `2^53`, nhưng không thể giữ **mọi** số nguyên lớn hơn thế với bước cách nhau 1.

```java
long original = 9_007_199_254_740_993L; // 2^53 + 1
double stored = original;

System.out.println((long) stored);
// 9007199254740992
```

Giá trị `long` ban đầu là số nguyên chính xác, nhưng chuyển sang `double` làm tròn về `9_007_199_254_740_992`. Nếu sau đó ép `double` về `long` thì con số đã mất **không được khôi phục**.

Đây là một ví dụ rõ ràng của **sai số biểu diễn/chuyển đổi dữ liệu**, không cần đến thuật toán phức tạp nào. Khi giữ ID, tiền nguyên đơn vị nhỏ nhất hoặc bộ đếm lớn, hãy dùng kiểu nguyên phù hợp (`long`, `BigInteger`) thay vì qua `double`.

**Quy tắc quan trọng:** Chọn kiểu dữ liệu theo **miền giá trị và yêu cầu chính xác**, không chỉ vì `double` có khả năng biểu diễn số rất lớn đến khoảng `1.8×10^308`. Miền độ lớn rộng không đồng nghĩa độ phân giải bằng 1 trên toàn miền.

## 4. Sai số làm tròn trong phép toán khác với sai số của đầu vào

Ngay cả khi mọi toán hạng được biểu diễn chính xác, **phép toán trung gian** vẫn có thể tạo ra kết quả không biểu diễn được với độ chính xác của kiểu.

Ví dụ:

```java
double large = 1.0e16; // số nguyên này biểu diễn chính xác bằng double
double one = 1.0;       // biểu diễn chính xác

System.out.println(large + one == large); // true
System.out.println(Math.ulp(large));      // 2.0
```

`10^16` và 1 đều có thể biểu diễn chính xác riêng lẻ. Nhưng khoảng cách giữa hai `double` kề nhau quanh `10^16` là 2, nên `10^16 + 1` nằm giữa hai giá trị biểu diễn được và được làm tròn về `10^16`.

Vì vậy câu nói “nếu đầu vào chính xác thì kết quả tính cũng chính xác” là **sai** đối với dấu phẩy động.

Một phản ví dụ về thứ tự cộng:

```java
double a = 1.0e16;
double b = -1.0e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
```

Hai biểu thức có cùng giá trị trong số thực toán học, nhưng khác vì **vị trí xảy ra phép làm tròn**. Nó minh họa sai số phép toán và ảnh hưởng của thuật toán/cách nhóm, không phải chỉ lỗi khi gõ literal 0.1.

Các phép toán số thực dấu phẩy động cũng chịu giới hạn **exponent**: tính trung gian có thể thành `Infinity` (overflow) hoặc rất gần 0 tới mức thành `subnormal`/`0.0` (underflow). Chọn thứ tự và dạng công thức đôi khi quyết định tình trạng đó.

## 5. Sai số thuật toán là gì? Không chỉ là “code sai”

Thuật toán là **trình tự phép tính** được chọn để tìm hoặc xấp xỉ lời giải. Sai số thuật toán có thể đến từ những nguyên nhân khác nhau:

- Dùng một phép **xấp xỉ toán học** thay cho hàm chính xác (ví dụ `e^x ≈ 1+x` khi `x` không đủ nhỏ).
- **Cắt cụt chuỗi/vòng lặp** trước khi đạt dung sai yêu cầu.
- Lựa chọn công thức **kém ổn định số** khiến sai số làm tròn hoặc sai số đầu vào bị khuếch đại.
- Dùng cách **cộng dồn** khiến các số nhỏ bị hấp thụ vào tổng lớn.
- Dùng sơ đồ giải phương trình hay tính đạo hàm số nhạy với bước lưới, độ chính xác hoặc số vòng lặp.

Đây không nhất thiết là lỗi logic kiểu “viết sai công thức”. Hai công thức **tương đương tuyệt đối trên số thực** vẫn có thể có chất lượng tính toán rất khác nhau khi chuyển thành một chuỗi phép toán `double`.

### Phân biệt truncation/approximation error và floating-point rounding

Một thuật toán có thể chủ động lấy gần đúng:

\[
e^x = 1+x+\frac{x^2}{2!}+\cdots
\]

Nếu dùng đúng hai số hạng đầu:

\[
e^x \approx 1+x
\]

thì tại `x = 1` giá trị xấp xỉ là **2**, trong khi giá trị toán học là khoảng **2.718281828459045...**. Chênh lệch lớn này chủ yếu do **bỏ các số hạng chuỗi**, **không** phải do Java thiếu bit lưu số 2.

```java
double firstOrder = 1.0 + 1.0;
double reference = Math.exp(1.0);

System.out.println(firstOrder); // 2.0
System.out.println(reference);  // 2.718281828459045
```

`Math.exp` vẫn trả một giá trị `double` đã làm tròn, không phải số vô hạn chữ số; nhưng kết quả này đủ để cho thấy sai số của phép xấp xỉ hai số hạng là vấn đề **thuật toán** lớn hơn rất nhiều so với một ULP quanh 2.

## 6. Một công thức toán học đúng nhưng triển khai không ổn định

Xét hàm:

\[
f(x)=\sqrt{x^2+1}-x,\qquad x>0.
\]

Trên số thực, biểu thức này luôn **dương**. Thế nhưng nếu `x` đủ lớn, `x²+1` có thể làm tròn thành `x²`, khiến kết quả gần 0 hoặc đúng bằng 0 trong `double`.

Dùng `x = 100_000_000.0`:

```java
double x = 1.0e8;
double naive = Math.sqrt(x * x + 1.0) - x;

System.out.println(naive); // 0.0
```

Giải thích tuần tự:

1. `x*x = 10^16` biểu diễn được.
2. Khi cộng 1.0 vào `10^16`, bước biểu diễn `double` quanh đó có độ rộng 2.0, nên `10^16+1` bị làm tròn lại thành `10^16`.
3. `sqrt(10^16)` trả `10^8`.
4. Trừ `x=10^8` cho kết quả `0.0`.

**Sai số biểu diễn của literal không phải thủ phạm chính:** `x = 1e8` và 1.0 đều biểu diễn chính xác. Vấn đề nằm ở **cách tổ chức phép tính trung gian**, gây mất số hạng +1 rồi trừ hai số gần bằng nhau.

### Biến đổi công thức để ổn định hơn

Nhân cả tử và mẫu với biểu thức liên hợp:

\[
\sqrt{x^2+1}-x
=\frac{(\sqrt{x^2+1}-x)(\sqrt{x^2+1}+x)}
       {\sqrt{x^2+1}+x}
=\frac{1}{\sqrt{x^2+1}+x}.
\]

Áp dụng:

```java
double x = 1.0e8;
double stable = 1.0 / (Math.sqrt(x * x + 1.0) + x);
System.out.println(stable); // 5.0E-9
```

Tuy việc tính `sqrt(x*x+1)` vẫn nhận `x` với bộ dữ liệu này, phép chia `1/(2*x)` giữ được **độ lớn đúng** của kết quả cực nhỏ: khoảng `5×10^-9`, thay vì 0.

Hai công thức **bằng nhau trong đại số số thực** (với `x>0`), nhưng dưới `double` khác hẳn về độ ổn định.

**Giới hạn của mẹo này:** Công thức đã biến đổi vẫn có thể overflow nếu `x*x` vượt miền `double`, hoặc có vấn đề ở miền đầu vào khác. Với nhiều bài toán độ lớn lớn, nên xét `Math.hypot(x, 1.0)` để tính `sqrt(x*x+1)` theo cách giảm rủi ro overflow/underflow. Không kết luận một phép biến đổi liên hợp xử lý hoàn hảo mọi đầu vào.

## 7. Công thức tính log: log1p tránh mất x gần 0

Trong toán học:

\[
\log(1+x)
\]

là công thức hoàn toàn đúng. Nhưng khi `x` cực nhỏ, phép cộng `1+x` trong `double` có thể làm tròn ngay về 1 trước khi gọi log.

```java
double x = 1.0e-16;

System.out.println(1.0 + x == 1.0); // true
System.out.println(Math.log(1.0 + x)); // 0.0
System.out.println(Math.log1p(x));     // 1.0E-16
```

Vì `x` nhỏ hơn nửa bước ULP quanh 1.0, `1+x` bị làm tròn thành 1.0. Sau đó `log(1.0)=0` là kết quả đúng của giá trị đầu vào **đã bị làm tròn**, không phải bản thân `Math.log` “không biết log”.

`Math.log1p(x)` được thiết kế để tính **log(1+x)** chính xác hơn khi `x` gần 0, tránh hình thành trung gian `1+x` kém chính xác. Tương tự, `Math.expm1(x)` tính **exp(x)-1** với độ chính xác tốt hơn khi `x` gần 0 so với cách viết `Math.exp(x)-1.0`.

Đây là ví dụ quan trọng để phân biệt:

- **Sai số biểu diễn đầu vào:** `x=1e-16` được lưu bằng một giá trị `double` gần số thập phân đó.
- **Sai số do cách tính:** biểu thức `1+x` làm mất thay đổi nhỏ trước khi áp dụng `log`.
- **Giải pháp:** chọn **hàm số học chuyên dụng** để tránh trung gian có độ phân giải không đủ.

## 8. Cộng một dãy số: thuật toán khác nhau có thể giữ hoặc mất số hạng nhỏ

Cho dãy:

```java
double[] values = {1.0e16, 1.0, -1.0e16};
```

Tổng số thực chính xác bằng 1. Cộng tuần tự:

```java
double sum = 0.0;
for (double value : values) {
    sum += value;
}
System.out.println(sum); // 0.0
```

Sau khi `sum=1e16`, cộng 1.0 bị làm tròn về `1e16`. Cộng `-1e16` tiếp theo cho 0.0. Số hạng +1 đã mất.

Nếu thay đổi thứ tự thành `{1e16, -1e16, 1.0}`, tổng tuần tự cho 1.0. Chênh lệch phát sinh từ **vị trí làm tròn**, không phải vì mỗi số hạng đầu vào không biểu diễn được.

### Neumaier compensated summation

Một thuật toán giảm sai số của phép cộng dồn là *Neumaier summation*, thuộc nhóm **compensated summation**. Thuật toán giữ biến bù để ghi nhận một phần sai số bị loại:

```java
static double neumaierSum(double[] values) {
    double sum = 0.0;
    double correction = 0.0;

    for (double x : values) {
        double t = sum + x;
        if (Math.abs(sum) >= Math.abs(x)) {
            correction += (sum - t) + x;
        } else {
            correction += (x - t) + sum;
        }
        sum = t;
    }
    return sum + correction;
}
```

Đây là **phương thức minh họa** phải đặt trong một lớp Java. Với dãy trên, nó cho 1.0. Nhưng thuật toán không bảo đảm kết quả đúng vô hạn chữ số cho mọi bộ dữ liệu, đặc biệt khi có overflow, NaN, vô cực hoặc dữ liệu vượt miền ổn định. Sự khác nhau nằm ở **thuật toán tích lũy** dù kiểu `double` và dữ liệu đầu vào vẫn như cũ.

Ở các bài toán tổng lớn, còn có **pairwise summation** (cộng theo cây cân bằng) hoặc thuật toán chuyên biệt cho mục tiêu tái lập bit-for-bit. Cần định nghĩa rõ độ chính xác và tính tái lập trước khi chọn cách song song hóa phép tổng.

## 9. Bài toán nhạy với đầu vào: condition number không phải sai số biểu diễn

Không phải mọi kết quả biến động mạnh đều do thuật toán tính kém.

Giả sử bài toán cần tính:

\[
g(x)=\frac{1}{1-x}.
\]

Khi `x` tiến sát 1, chỉ cần thay đổi rất nhỏ ở đầu vào cũng làm kết quả biến đổi đáng kể. Ví dụ trên số thực:

\[
g(0.99)=100,\qquad g(0.999)=1000.
\]

Hai giá trị đầu vào khác nhau chỉ 0.009 nhưng kết quả khác gấp 10 lần. Đây là đặc tính **nhạy đầu vào của bài toán** (*conditioning*), không chỉ do ngôn ngữ lập trình.

Phân biệt:

- **Bài toán điều kiện tốt (*well-conditioned*):** thay đổi nhỏ ở đầu vào thường chỉ gây thay đổi tương ứng nhỏ ở đầu ra theo thước đo đang xét.
- **Bài toán điều kiện kém (*ill-conditioned*):** thay đổi nhỏ ở đầu vào có thể tạo thay đổi lớn ở đầu ra.
- **Thuật toán ổn định (*numerically stable*):** cách tính không khuếch đại sai số vượt mức bất hợp lý so với độ nhạy vốn có của bài toán; cách diễn đạt định lượng cần xác định chuẩn, miền và loại ổn định (ví dụ *backward stability*).

Một thuật toán tốt không thể “đánh bại” mọi độ nhạy cố hữu của dữ liệu. Ví dụ đầu vào có sai số đo lường từ cảm biến, việc chuyển sang `BigDecimal` không thể tự phục hồi thông tin vật lý chưa từng được đo chính xác.

**Nguyên tắc chẩn đoán:** trước khi quy mọi lệch về `double`, hãy hỏi “với dữ liệu chính xác, bài toán có nhạy không?” và “thuật toán đang đưa sai số bổ sung vào ở bước nào?”.

## 10. Đo sai số: tuyệt đối, tương đối và ULP

Giả sử có kết quả tham chiếu `T` với độ tin cậy cao và kết quả tính `R`.

### 10.1. Sai số tuyệt đối

\[
E_{\mathrm{abs}} = |R-T|
\]

Ví dụ mong đợi 100.0 và nhận 100.01 thì sai số tuyệt đối là 0.01. Nó được đo bằng **cùng đơn vị** với giá trị (đồng, mét, giây...), nên thường dễ liên hệ với nghiệp vụ.

### 10.2. Sai số tương đối

Khi `T ≠ 0`:

\[
E_{\mathrm{rel}} = \frac{|R-T|}{|T|}
\]

Sai số tương đối hữu ích khi độ lớn dữ liệu trải rộng. Nhưng gần `T=0`, biểu thức này có thể rất lớn hoặc không xác định; **không dùng mỗi sai số tương đối** làm tiêu chuẩn so sánh khi mốc đúng bằng hoặc gần 0.

### 10.3. ULP (unit in the last place)

`Math.ulp(x)` thể hiện kích cỡ một bước biểu diễn quanh `x`. Ví dụ:

```java
System.out.println(Math.ulp(1.0));    // 2.220446049250313E-16
System.out.println(Math.ulp(1.0e16)); // 2.0
```

ULP giúp hiểu tại sao `double` “có độ chính xác cao” vẫn mất +1 khi cộng vào `1e16`. Nhưng **một giới hạn ULP** chỉ phù hợp khi bài toán cần đánh giá sai số gắn với các giá trị `double`, không phải mọi sai số nghiệp vụ đều được mô tả tốt bằng ULP.

### 10.4. Khi nào tham chiếu đáng tin?

Không lấy một phép tính `double` khác làm “đáp án tuyệt đối” nếu chính nó cũng chịu lỗi tương tự. Các nguồn tham chiếu tốt hơn có thể gồm:

- kết quả phân tích toán học hoặc nghiệm chính xác của trường hợp đơn giản;
- số học nguyên/bội số của 10 được lưu chính xác nếu bài toán cho phép;
- `BigDecimal` khởi tạo từ chuỗi với đủ precision và chế độ làm tròn được xác định;
- thư viện độ chính xác cao, thuật toán đã kiểm định hoặc dữ liệu chuẩn độc lập.

Với tính toán gần đúng thực tế, nên kiểm tra nhiều độ lớn, đặc biệt miền **gần 0**, **gần biên**, **dữ liệu gần bằng nhau** và **đầu vào làm đổi nhánh thuật toán**.

## 11. Chọn BigDecimal có chữa được mọi sai số không?

**Không.** `BigDecimal` giúp biểu diễn và tính toán **số thập phân chính xác theo định dạng đầu vào**, trong phạm vi tính toán và tài nguyên phù hợp. Nó đặc biệt hữu ích cho tiền hoặc dữ liệu decimal mà một chữ số cuối có ý nghĩa pháp lý/nghiệp vụ.

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
System.out.println(a.add(b)); // 0.3
```

Đây là phép cộng thập phân chính xác vì hai toán hạng được tạo từ **chuỗi**.

Nhưng:

```java
BigDecimal fromDouble = new BigDecimal(0.1);
System.out.println(fromDouble);
// 0.1000000000000000055511151231257827021181583404541015625
```

Constructor nhận `double` diễn giải chính xác **giá trị nhị phân đã được lưu**, nên không thể tự biết người dùng ban đầu muốn phân số 1/10. Nếu có một `double` và muốn tạo BigDecimal từ chuỗi decimal mà `Double.toString` biểu diễn, có thể dùng `BigDecimal.valueOf(double)`; nhưng đây là **một quy tắc chuyển đổi khác**, không phải khôi phục mọi độ chính xác bị mất.

Ngoài ra, khi dùng `MathContext` giới hạn precision, `BigDecimal` cũng có làm tròn; khi một công thức được xấp xỉ sai hoặc thuật toán dừng quá sớm, đổi kiểu không tự biến thuật toán thành chính xác.

Bảng quyết định:

| Trường hợp | Lựa chọn |
| --- | --- |
| Tiền theo chữ số thập phân, yêu cầu rounding xác định | `BigDecimal` từ chuỗi/đầu vào decimal, quy định scale và rounding |
| Tính khoa học cần tốc độ, chấp nhận dung sai số học | `double` với thuật toán ổn định và kiểm thử |
| Cần giữ số nguyên rất lớn chính xác | `BigInteger` |
| Cần tính `log(1+x)` khi `x` rất nhỏ | `Math.log1p(x)` |
| Cần tổng dài mà sai số cộng dồn đáng kể | Compensated/pairwise summation phù hợp dữ liệu |
| Bài toán đầu vào vốn không chính xác | Đánh giá sai số nguồn, sensitivity và độ bất định |
| Thuật toán lấy gần đúng quá thô | Tăng bậc/số bước hoặc đổi thuật toán, không chỉ đổi kiểu |

## 12. Thực hành kiểm thử số học an toàn

Một hàm kiểm tra gần bằng có thể kết hợp dung sai tuyệt đối và tương đối, có kiểm tra các giá trị đặc biệt:

```java
static boolean nearlyEqual(double a, double b,
                           double absoluteTolerance,
                           double relativeTolerance) {
    if (Double.isNaN(a) || Double.isNaN(b)) {
        return false;
    }
    if (a == b) {
        return true;
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }

    double difference = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return difference <= Math.max(absoluteTolerance,
                                  relativeTolerance * scale);
}
```

Đây là **phương thức minh họa**, cần nằm trong một lớp. Một triển khai production phải validate hai dung sai **hữu hạn, không âm** và xử lý tình huống tràn trong việc tính sai khác/tích tương đối nếu miền dữ liệu có thể rất lớn. Không có bộ ngưỡng tùy tiện như `1e-9` phù hợp mọi bài toán.

Phải làm rõ hợp đồng so sánh:

- **Bằng nhau về biểu diễn bit:** cần cùng mẫu IEEE 754; muốn phân biệt số 0 có dấu hoặc các NaN raw bits thì dùng API raw bits phù hợp.
- **Bằng nhau theo số học thông thường:** `+0.0 == -0.0` là `true`; `NaN == NaN` là `false`.
- **Gần bằng theo nghiệp vụ:** dựa trên đơn vị đo, độ lớn đầu vào, giới hạn sai số và mức độ an toàn chấp nhận được.

**Không dùng `Double.MIN_VALUE` làm epsilon chung.** Đó là số dương subnormal nhỏ nhất, không phải ngưỡng sai số hợp lý quanh mọi giá trị.

## 13. Chương trình Java 17+ chạy độc lập

Tạo tệp **`RepresentationVsAlgorithmErrorDemo.java`**:

```java
import java.math.BigDecimal;

public class RepresentationVsAlgorithmErrorDemo {
    private static double neumaierSum(double[] values) {
        double sum = 0.0;
        double correction = 0.0;

        for (double x : values) {
            double t = sum + x;
            if (Math.abs(sum) >= Math.abs(x)) {
                correction += (sum - t) + x;
            } else {
                correction += (x - t) + sum;
            }
            sum = t;
        }
        return sum + correction;
    }

    public static void main(String[] args) {
        // Biểu diễn thập phân trong kiểu dấu phẩy động nhị phân.
        System.out.println("0.1 hex = " + Double.toHexString(0.1));
        System.out.println("0.1 + 0.2 = " + (0.1 + 0.2));
        BigDecimal exactDecimal =
                new BigDecimal("0.1").add(new BigDecimal("0.2"));
        System.out.println("decimal sum = " + exactDecimal);

        // Mất thông tin khi chuyển số nguyên quá lớn sang double.
        long original = 9_007_199_254_740_993L;
        double rounded = original;
        System.out.println("long preserved = "
                + ((long) rounded == original));
        System.out.println("rounded long = " + (long) rounded);

        // Hai công thức tương đương toán học nhưng khác độ ổn định.
        double x = 1.0e8;
        double naive = Math.sqrt(x * x + 1.0) - x;
        double stable = 1.0 / (Math.sqrt(x * x + 1.0) + x);
        System.out.println("naive formula = " + naive);
        System.out.println("stable formula = " + stable);

        // Hàm chuyên dụng bảo toàn độ chính xác gần 0 tốt hơn.
        double tiny = 1.0e-16;
        System.out.println("log(1+x) = " + Math.log(1.0 + tiny));
        System.out.println("log1p(x) = " + Math.log1p(tiny));

        // Cộng dồn theo hai thuật toán khác nhau.
        double[] values = {1.0e16, 1.0, -1.0e16};
        double normalSum = 0.0;
        for (double value : values) {
            normalSum += value;
        }
        System.out.println("simple sum = " + normalSum);
        System.out.println("corrected sum = " + neumaierSum(values));

        // Sai số xấp xỉ do cố ý cắt chuỗi Taylor, không phải
        // chỉ vì số 2.0 bị lưu kém chính xác.
        double firstOrder = 1.0 + 1.0;
        System.out.println("first-order exp(1) = " + firstOrder);
        System.out.println("Math.exp(1) = " + Math.exp(1.0));
    }
}
```

Trong thư mục chứa tệp:

```bash
javac RepresentationVsAlgorithmErrorDemo.java
java RepresentationVsAlgorithmErrorDemo
```

**Kết quả chuẩn trên Java 17+:**

```text
0.1 hex = 0x1.999999999999ap-4
0.1 + 0.2 = 0.30000000000000004
decimal sum = 0.3
long preserved = false
rounded long = 9007199254740992
naive formula = 0.0
stable formula = 5.0E-9
log(1+x) = 0.0
log1p(x) = 1.0E-16
simple sum = 0.0
corrected sum = 1.0
first-order exp(1) = 2.0
Math.exp(1) = 2.718281828459045
```

**Giải thích đầu ra theo nguồn sai số:**

1. `0.1` không thể lưu chính xác dạng nhị phân hữu hạn: **sai số biểu diễn**.
2. `2^53 + 1` không thể giữ nguyên khi đưa vào `double`: **sai số chuyển đổi/biểu diễn**, dù đầu vào nguyên hoàn toàn chính xác.
3. `sqrt(x*x+1)-x` ra 0 trong khi công thức liên hợp ra khoảng `5e-9`: **thuật toán/công thức kém ổn định dưới rounding**.
4. `log(1+x)` ra 0 còn `log1p(x)` giữ số nhỏ: **chọn hàm tính ổn định hơn**.
5. Tổng tuần tự mất +1 còn tổng có bù giữ được +1: **khác nhau về thuật toán tổng** trên cùng dữ liệu.
6. `1+x` thay cho `exp(x)` ở `x=1` sai số lớn vì **cắt chuỗi xấp xỉ**, ngay cả với các số đơn giản có thể lưu chính xác.

## 14. Lỗi nhận định thường gặp

| Kết luận sai | Cách hiểu đúng |
| --- | --- |
| “Mọi phép tính sai vì `double` không lưu chính xác 0.1” | Có lỗi từ biểu diễn, rounding phép toán, thuật toán, dữ liệu và điều kiện bài toán |
| “Đầu vào là số nhị phân chính xác thì đầu ra chắc chắn chính xác” | Phép toán trung gian có thể làm tròn hoặc tràn |
| “Dùng `double` thay `float` sửa mọi lỗi” | Cải thiện độ chính xác nhưng không sửa thuật toán kém ổn định hoặc xấp xỉ sai |
| “Công thức tương đương đại số cho kết quả Java giống nhau” | Đổi ngoặc/dạng công thức thay vị trí làm tròn |
| “Cứ tính nhiều vòng lặp hơn thì luôn chính xác hơn” | Có thể giảm truncation error nhưng tích lũy roundoff hoặc bất ổn |
| “Bài toán nhạy đầu vào là do thuật toán lập trình sai” | Conditioning là thuộc tính của bài toán; stability là thuộc tính thuật toán |
| “Phép trừ gần bằng nhau tự động sai” | Nó có thể chính xác trên toán hạng đã lưu; rủi ro là làm lộ/phóng đại sai số tương đối có sẵn |
| “`BigDecimal` chữa được input double đã sai” | Không tự phục hồi dữ liệu đã bị làm tròn hoặc thông tin chưa được đo |
| “`Math.log(1+x)` tốt như `Math.log1p(x)` ở gần 0” | Bước `1+x` có thể làm mất x trước khi log chạy |
| “Cộng mảng bằng thứ tự nào cũng như nhau” | Kết quả floating-point phụ thuộc các bước làm tròn và thứ tự gộp |
| “`Double.MIN_VALUE` là dung sai mặc định tốt” | Không; cần xác định dung sai theo thang đo và nghiệp vụ |
| “Tham chiếu `Math.exp` là số thực vô hạn chính xác” | Hàm trả giá trị `double` hữu hạn có sai số xấp xỉ/làm tròn theo quy tắc API |

## 15. Bài tập tự kiểm tra và lời giải

**Bài 1.** Vì sao `0.1 + 0.2 == 0.3` có thể `false`? Đây là sai số biểu diễn hay thuật toán?  
**Đáp án:** `0.1`, `0.2`, `0.3` được làm tròn từ các giá trị thập phân sang các giá trị `double` nhị phân; phép cộng cũng có làm tròn. Ví dụ thể hiện ảnh hưởng biểu diễn và rounding, **chưa tự nó chứng minh công thức thuật toán sai**.

**Bài 2.** `long n = 9_007_199_254_740_993L; double d = n;` có giữ được `n` không?  
**Đáp án:** Không. `n=2^53+1` không thể biểu diễn như `double` chính xác; `d` làm tròn về `2^53`. Đây là mất dữ liệu khi chuyển kiểu.

**Bài 3.** Khi `x=1e8`, vì sao `sqrt(x*x+1)-x` cho 0 dù kết quả toán học dương?  
**Đáp án:** `x*x+1` làm tròn về `x*x`; căn trả `x` rồi trừ `x` thành 0. Công thức liên hợp `1/(sqrt(x*x+1)+x)` tránh phép trừ kém ổn định cho miền giá trị này.

**Bài 4.** Tại sao `Math.log1p(1e-16)` tốt hơn `Math.log(1+1e-16)`?  
**Đáp án:** `1+1e-16` trong `double` làm tròn thành 1, làm mất độ lớn nhỏ trước khi log. `log1p` được thiết kế tính chính `log(1+x)` khi x gần 0 mà không bị lỗi trung gian ấy.

**Bài 5.** Nếu chương trình lấy `1+x` để xấp xỉ `exp(x)` tại `x=1`, thay `double` bằng `BigDecimal` có làm đáp án bằng `e` không?  
**Đáp án:** Không. Sai số do bỏ số hạng bậc hai trở lên của chuỗi Taylor; phải đổi/tăng độ chính xác của thuật toán xấp xỉ.

**Bài 6.** Phân biệt một bài toán *ill-conditioned* với một thuật toán *unstable*.  
**Đáp án:** Ill-conditioned nghĩa là nghiệm toán học nhạy với thay đổi nhỏ ở đầu vào. Unstable nghĩa là quá trình tính số gây sai số vượt mức chấp nhận so với độ nhạy của bài toán. Có thể có bài toán nhạy nhưng thuật toán vẫn tương đối ổn định, hoặc bài toán vốn tốt nhưng triển khai kém.

**Bài 7.** Khi so sánh kết quả gần 0, tại sao chỉ dùng tỷ lệ sai số tương đối có thể gây khó?  
**Đáp án:** Vì mẫu tham chiếu gần 0 làm tỷ lệ rất lớn hoặc không xác định khi tham chiếu bằng 0. Dung sai tuyệt đối có ý nghĩa hơn trong vùng này, kết hợp dung sai tương đối ở vùng độ lớn lớn hơn.

**Bài 8.** Với mảng `{1e16,1.0,-1e16}`, cách đơn giản cho 0.0 nhưng tổng toán học là 1. Thuật toán nào có thể cải thiện?  
**Đáp án:** Tổng có bù kiểu Neumaier hoặc thuật toán phù hợp dữ liệu có thể giữ lại số 1 trong ví dụ này. Cần kiểm thử khi đổi dữ liệu, không mặc định mọi phương pháp bù đều đúng tuyệt đối.

## 16. Checklist phân tích sai số trước khi sửa chương trình

1. **Định nghĩa kết quả đúng**: số thực lý tưởng, giá trị decimal theo hợp đồng, hay kết quả thuật toán có dung sai?
2. **Kiểm tra dữ liệu đầu vào**: đã có sai số đo đạc, parse, cast, literal thập phân hoặc chuyển `long → double` chưa?
3. **Kiểm tra các trung gian**: có cộng số rất nhỏ vào số lớn, trừ hai số gần bằng nhau, overflow/underflow hay mất bit không?
4. **Kiểm tra công thức**: có cách viết tương đương ổn định hơn, `Math.log1p`/`Math.expm1`/`Math.hypot` hoặc thuật toán đặc biệt không?
5. **Phân biệt sai số xấp xỉ**: bậc chuỗi, bước lưới, tiêu chuẩn dừng, số vòng lặp có đáp ứng mục tiêu không?
6. **Đánh giá conditioning**: bài toán nhạy với đầu vào đến mức nào; đầu vào đo được có đủ độ chính xác cần thiết?
7. **Chọn kiểu và thuật toán theo mục tiêu**: primitive, `BigInteger`, `BigDecimal`, tổng có bù hay thuật toán độ chính xác cao?
8. **Đặt thước đo rõ ràng**: sai số tuyệt đối, tương đối hoặc ULP; xử lý trường hợp 0, NaN, vô cực và miền giá trị rất lớn.
9. **Kiểm thử tình huống biên**: khác nhau nhiều bậc độ lớn, số gần bằng nhau, gần zero, giá trị sát giới hạn kiểu, dữ liệu âm và dương xen kẽ.
10. **Ghi lại giả định và dung sai** trong API, test và tài liệu; đừng vá lỗi bằng một epsilon cố định không có cơ sở.

**Kết luận:** *Sai số biểu diễn* xuất hiện khi một giá trị không vừa chính xác trong định dạng lưu trữ; *sai số phép toán* xuất hiện khi các bước tính phải làm tròn; *sai số thuật toán* xuất hiện vì lựa chọn phép tính/xấp xỉ có thể mất hoặc khuếch đại thông tin. Ba nguồn có thể cộng hưởng. Muốn xử lý đúng, hãy tìm **bước đầu tiên thông tin bị mất**, đánh giá đặc tính bài toán, rồi chọn kiểu dữ liệu và công thức thích hợp thay vì chỉ tăng số chữ số in ra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-002">Sai số tuyệt đối, tương đối và tích lũy qua nhiều bước</a>

<details>
<summary>Xem chi tiết</summary>

# Sai số tuyệt đối, tương đối và tích lũy qua nhiều bước trong Java

## 1. Mục tiêu và bối cảnh

Kết quả tính toán trong Java có thể khác giá trị toán học lý tưởng vì biểu diễn số hữu hạn, làm tròn ở mỗi phép toán, thứ tự thực hiện phép tính và sai số vốn có trong dữ liệu đầu vào. Với `float` và `double`, những chênh lệch rất nhỏ có thể tích lũy khi thực hiện hàng nghìn hoặc hàng triệu bước.

Bài học tập trung vào ba câu hỏi:

1. **Sai số tuyệt đối**: kết quả tính toán lệch bao nhiêu đơn vị so với giá trị tham chiếu?
2. **Sai số tương đối**: độ lệch đó lớn hay nhỏ so với độ lớn của giá trị tham chiếu?
3. **Sai số tích lũy**: khi thực hiện nhiều phép cộng, trừ, nhân, chia hoặc phép lặp, sai số lan truyền và tăng lên như thế nào, và giảm rủi ro ra sao?

Ta sẽ dùng Java 17+, `double`, `BigDecimal`, `Math.ulp`, thuật toán cộng bù Kahan và các tình huống mất chữ số có nghĩa. Không có một ngưỡng sai số cố định nào phù hợp cho mọi nghiệp vụ.

## 2. Giá trị tham chiếu, giá trị xấp xỉ và nguồn sai số

Ký hiệu:

- \(x\): giá trị đúng (hoặc giá trị tham chiếu được chấp nhận trong bài toán).
- \(\hat{x}\): kết quả xấp xỉ do chương trình tính hoặc đo được.
- \(\hat{x}-x\): **sai số có dấu**; cho biết kết quả cao hơn hay thấp hơn tham chiếu.
- \(|\hat{x}-x|\): **độ lớn sai số**; không phụ thuộc dấu.

Cần phân biệt:

| Nguồn sai số | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Sai số dữ liệu đầu vào | Cảm biến ghi 9,98 m thay vì 10 m | Đầu vào đã không chính xác tuyệt đối |
| Sai số biểu diễn | `0.1` không có biểu diễn nhị phân hữu hạn | Dữ liệu số thực bị làm tròn ngay khi lưu |
| Sai số làm tròn phép toán | Cộng hai `double` rồi lưu kết quả vào `double` | Kết quả chính xác có thể nằm giữa hai giá trị biểu diễn được |
| Sai số cắt cụt thuật toán | Lấy 5 số hạng đầu của chuỗi vô hạn | Xấp xỉ do phương pháp toán học, ngay cả với số thực lý tưởng |
| Sai số tích lũy/lan truyền | Cộng nhiều giá trị nhỏ vào tổng lớn | Lỗi từng bước có thể làm thay đổi kết quả cuối |

Sai số biểu diễn và sai số phép toán thường được gọi chung là **floating-point rounding error**. Sai số cắt cụt xuất phát từ thuật toán và phải được phân tích riêng. Với bài toán đo đạc, giá trị “đúng” có thể không biết; khi đó chỉ có thể nói đến sai số so với tham chiếu có độ tin cậy, khoảng bất định hoặc giới hạn sai số đã được chứng minh.

## 3. Sai số tuyệt đối

**Định nghĩa**:

\[
E_{\mathrm{abs}} = |\hat{x} - x|
\]

Sai số tuyệt đối có **cùng đơn vị đo** với đại lượng ban đầu. Nếu một chiều dài đúng là 100 m và chương trình trả 99 m thì \(E_{\mathrm{abs}}=1\) m.

~~~java
double reference = 100.0;
double approximate = 99.0;
double absoluteError = Math.abs(approximate - reference);

System.out.println(absoluteError); // 1.0
~~~

Đây là đoạn minh họa đặt trong một phương thức.

**Ưu điểm:** dễ giải thích bằng đơn vị nghiệp vụ. Sai lệch 0,5°C, 1 mm hay 2 đồng tiền tệ mang ý nghĩa trực tiếp tùy hoàn cảnh.

**Giới hạn:** cùng sai số tuyệt đối chưa chắc quan trọng như nhau. Sai lệch 1 m đối với đoạn đường 100 m khác hẳn sai lệch 1 m khi đo chiều dài 1.000.000 m. Để so sánh giữa các thang đo, cần sai số tương đối.

### Cẩn trọng khi tính hiệu của hai double

Biểu thức `Math.abs(approximate - reference)` là cách tính thông dụng, nhưng vẫn dùng số học `double`. Nếu hai số hữu hạn cực lớn có dấu trái ngược nhau, phép trừ có thể **overflow** thành `Infinity`; với độ lệch cực nhỏ, sự khác biệt có thể đã bị làm tròn về 0 trước khi ta đo. Vì vậy sai số tính bằng `double` cũng có giới hạn độ chính xác và miền biểu diễn.

Nếu cần sai số thập phân chính xác và dữ liệu đầu vào vốn là số thập phân, hãy giữ chúng bằng `BigDecimal` từ đầu.

## 4. Sai số tương đối

Khi \(x \ne 0\), sai số tương đối được định nghĩa:

\[
E_{\mathrm{rel}} = \frac{|\hat{x}-x|}{|x|}
\]

Nó là tỷ lệ không có đơn vị. Nhân với 100 để đổi sang phần trăm:

\[
E_{\%} = 100E_{\mathrm{rel}}\%
\]

Ví dụ \(\hat{x}=99,\ x=100\):

\[
E_{\mathrm{abs}}=1,\qquad E_{\mathrm{rel}}=0{,}01,\qquad E_{\%}=1\%.
\]

~~~java
double reference = 100.0;
double approximate = 99.0;

double absError = Math.abs(approximate - reference);
double relativeError = absError / Math.abs(reference);
double percentError = relativeError * 100.0;

System.out.println(absError);      // 1.0
System.out.println(relativeError); // 0.01
System.out.println(percentError);  // 1.0
~~~

### Trường hợp giá trị tham chiếu bằng hoặc rất gần 0

Nếu \(x=0\), công thức sai số tương đối **không xác định**, vì mẫu số bằng 0. Không nên báo `Infinity`, `NaN` hoặc tự gán 0% mà không giải thích. Khi kết quả mong muốn bằng 0, có thể dùng **sai số tuyệt đối** theo một ngưỡng phù hợp.

Nếu \(x\) rất gần 0 nhưng khác 0, chỉ một sai số tuyệt đối nhỏ cũng có thể tạo sai số tương đối rất lớn:

| Giá trị đúng | Kết quả | Sai số tuyệt đối | Sai số tương đối |
| --- | --- | --- | --- |
| 100 | 99 | 1 | 1% |
| 1 | 0,99 | 0,01 | 1% |
| 0,000001 | 0,000002 | 0,000001 | 100% |
| 0 | 0,000001 | 0,000001 | Không xác định |

Dòng thứ ba cho thấy một chênh lệch rất nhỏ theo đơn vị có thể là **sai lệch 100%** so với giá trị mục tiêu.

### Relative error khác relative difference

Sai số tương đối đúng nghĩa đòi hỏi có giá trị chuẩn \(x\). Nếu chỉ có hai kết quả xấp xỉ `a` và `b`, ta có thể tính **độ chênh tương đối** bằng một mẫu số do ứng dụng quy định, chẳng hạn \(\max(|a|,|b|)\), nhưng không được mặc nhiên gọi đó là sai số so với “giá trị đúng” chưa biết.

## 5. Vì sao số thực dấu phẩy động có sai số?

Java `float` dùng IEEE 754 binary32 và `double` dùng binary64. Một số thập phân như 0,1 có mẫu nhị phân vô hạn, nên được lưu dưới dạng giá trị nhị phân gần nhất mà kiểu dữ liệu biểu diễn được.

~~~java
double value = 0.1 + 0.2;
System.out.println(value); // 0.30000000000000004
~~~

Trong số học thập phân lý tưởng, \(0{,}1+0{,}2=0{,}3\); còn trong binary64, hai toán hạng đã được làm tròn trước phép cộng, và phép cộng cũng làm tròn. Giá trị in ra thể hiện sự khác biệt có thể quan sát giữa hai kết quả binary64.

**Lưu ý:** số `0.30000000000000004` không chứng tỏ Java thực hiện phép cộng tùy tiện. Đây là kết quả của quy tắc làm tròn xác định. Lỗi xảy ra khi ta đòi hỏi phép toán nhị phân phải có tính chất của phép toán thập phân chính xác.

### ULP, machine epsilon và unit roundoff

- `Math.ulp(x)` trả về độ lớn của một **unit in the last place** tại giá trị `x`, theo quy tắc của API Java.
- `Math.ulp(1.0)` bằng \(2^{-52}\approx 2{,}220446049250313\times10^{-16}\): khoảng cách giữa 1,0 và số `double` kế tiếp lớn hơn 1,0.
- Với binary64 làm tròn gần nhất, *unit roundoff* thường ký hiệu \(u=2^{-53}\approx1{,}1102230246251565\times10^{-16}\). Trong nhiều tài liệu, thuật ngữ “machine epsilon” được dùng cho \(2^{-52}\) hoặc đôi khi cho \(u\); cần kiểm tra **định nghĩa** của tài liệu trước khi so sánh số liệu.

~~~java
System.out.println(Math.ulp(1.0)); // 2.220446049250313E-16
System.out.println(1.0 + Math.ulp(1.0) > 1.0); // true
~~~

**ULP không phải một hằng số cho mọi độ lớn.** Khoảng cách giữa các số `double` kề nhau thay đổi theo số mũ. Ở giá trị lớn, một bước ULP có thể lớn hơn 1; ở vùng gần 0, có các số subnormal với bước nhỏ nhất `Double.MIN_VALUE`.

## 6. Sai số tích lũy qua phép cộng lặp

Một ví dụ đơn giản là cộng `0.1` mười lần:

~~~java
double total = 0.0;
for (int i = 0; i < 10; i++) {
    total += 0.1;
}
System.out.println(total); // 0.9999999999999999
~~~

Kết quả mong muốn theo số học thập phân chính xác là 1.0. Sau mỗi phép cộng, Java lưu một giá trị `double` đã làm tròn. Sai số của những bước trước trở thành dữ liệu đầu vào cho bước tiếp theo.

Một phép nhân được biểu diễn khác:

~~~java
System.out.println(0.1 * 10.0); // 1.0
~~~

Trong trường hợp này, tích được làm tròn đến đúng `1.0`. Không nên kết luận “nhân luôn chính xác hơn cộng”: hai chuỗi phép toán khác nhau dẫn tới cách làm tròn khác nhau, và điều ngược lại có thể xảy ra với dữ liệu khác.

### Có phải sai số luôn tăng từng bước?

Không. Sai số có dấu có thể cộng dồn, triệt tiêu nhau hoặc thay đổi hướng; không phải mỗi bước đều khiến sai số tuyệt đối lớn hơn. Tuy nhiên, với nhiều thao tác, **giới hạn trên của sai số** thường tăng và kết quả có thể trở nên nhạy cảm với thứ tự tính toán.

Điều này khiến hai cách tổng hợp cùng một tập số thực có thể cho các kết quả hơi khác, đặc biệt khi dữ liệu trải rộng trên nhiều bậc độ lớn.

## 7. Mất chữ số có nghĩa (catastrophic cancellation)

Một số rất lớn cộng một số rất nhỏ có thể làm mất hoàn toàn phần nhỏ:

~~~java
double large = 1e16;
System.out.println(large + 1.0 == large); // true
System.out.println(Math.ulp(large));       // 2.0
~~~

Vì khoảng cách giữa các số binary64 kề nhau tại \(10^{16}\) là 2, thêm 1 nằm tại ranh giới làm tròn và kết quả được làm tròn về `large`. Nếu sau đó trừ `large`, ta thu được:

~~~java
double lost = (1e16 + 1.0) - 1e16;
double kept = (1e16 - 1e16) + 1.0;

System.out.println(lost); // 0.0
System.out.println(kept); // 1.0
~~~

Trong số học thực, cả hai kết quả là 1.0. Với binary64, kết quả trung gian đã làm mất số 1 nên phép trừ không thể khôi phục nó.

**Cancellation** còn xảy ra khi trừ hai số gần nhau. Bản thân phép trừ giữa hai số floating point có thể được làm tròn rất tốt hoặc thậm chí chính xác, nhưng **sai số tương đối của dữ liệu đầu vào** bị phóng đại khi kết quả đúng của phép trừ rất gần 0. Do đó phải xét độ chính xác của cả đầu vào và phép tính, thay vì luôn đổ lỗi cho phép trừ cuối cùng.

### Tính kết hợp không còn bảo đảm

Về mặt toán học, \((a+b)+c=a+(b+c)\). Trong floating point, thứ tự làm tròn khác nhau, nên phép cộng **không có tính kết hợp tuyệt đối**. Trình tự cộng, nhân song song và cách gộp các kết quả từng phần có thể ảnh hưởng các bit thấp của kết quả.

## 8. Đánh giá sai số khi có nhiều bước tính

Giả sử cộng \(n\) số trong `double` và ta dùng mô hình làm tròn tương đối chuẩn:

\[
\operatorname{fl}(a+b) = (a+b)(1+\delta), \qquad |\delta|\le u
\]

với \(u=2^{-53}\) của binary64 làm tròn gần nhất, khi phép toán thuộc miền mà mô hình trên áp dụng (không có overflow và các vấn đề underflow làm mô hình không còn đúng).

Một giới hạn thường dùng trong phân tích tổng tuần tự là:

\[
|\widehat{S}-S|\ \le\ \gamma_{n-1}\sum_{i=1}^{n}|x_i|,
\quad
\gamma_k=\frac{ku}{1-ku},
\quad ku<1,
\]

trong đó \(S=\sum_i x_i\) là tổng chính xác của **những số thực đã đưa vào phép cộng**, còn \(\widehat{S}\) là tổng được tính với làm tròn ở từng bước. Đây là giới hạn lý thuyết trong các giả thiết nêu trên; nó **không bao gồm sai số khi chuyển dữ liệu thập phân hoặc phép đo ban đầu thành binary64**.

Có ba điểm cần hiểu:

1. Khi \(nu\) nhỏ, \(\gamma_{n-1}\) xấp xỉ \((n-1)u\); nhiều phép cộng hơn có thể cho giới hạn sai số lớn hơn.
2. Giới hạn có \(\sum |x_i|\). Nếu các số dương và âm gần triệt tiêu, tổng thật \(S\) có thể nhỏ, nên **sai số tương đối của tổng** có thể rất lớn dù sai số tuyệt đối còn nhỏ.
3. Đây là **cận xấu nhất theo mô hình**, không phải lời hứa sai số thực tế bằng \((n-1)u\) hoặc chắc chắn tăng đều. Các sai số làm tròn có thể triệt tiêu; trong một số mô hình ngẫu nhiên, tốc độ tăng sai số thực tế nhỏ hơn cận xấu nhất.

Khi phép toán chỉ có vài bước, thường kiểm tra bằng dữ liệu biên và lời giải tham chiếu đủ chính xác. Khi số bước lớn, ngoài kiểm thử còn nên lựa chọn thuật toán ổn định và phân tích miền giá trị.

## 9. Phép cộng bù Kahan để giảm mất mát khi cộng dồn

**Kahan compensated summation** giữ thêm một biến hiệu chỉnh nhỏ để bù lại phần thấp bị mất khi cộng số nhỏ vào tổng hiện tại.

~~~java
static double kahanSum(double[] values) {
    double sum = 0.0;
    double correction = 0.0;

    for (double value : values) {
        double adjusted = value - correction;
        double next = sum + adjusted;
        correction = (next - sum) - adjusted;
        sum = next;
    }
    return sum;
}
~~~

Đây là **phương thức độc lập ở cấp lớp**, có thể đặt trong một lớp Java rồi gọi từ `main`. Thuật toán dùng các phép toán số thực thông thường và có lợi cho nhiều dãy cộng dài.

Ví dụ áp dụng:

~~~java
double[] tenths = new double[10];
java.util.Arrays.fill(tenths, 0.1);
System.out.println(kahanSum(tenths)); // 1.0
~~~

Trong ví dụ này, Kahan trả `1.0`, cải thiện so với phép cộng tuần tự thông thường `0.9999999999999999`.

**Giới hạn:** Kahan không làm mọi phép cộng trở thành số học thực chính xác; kết quả còn phụ thuộc cách biểu diễn đầu vào, thứ tự phép toán, overflow, underflow và đặc tính dãy số. Nó cũng không khôi phục sai số đo hoặc chữ số đã mất **trước khi** các phần tử được đưa vào mảng.

Các cách khác nên cân nhắc là **pairwise summation** (cộng theo cây cân bằng), sắp xếp dãy theo độ lớn khi phù hợp, dùng thuật toán ổn định về mặt số học hoặc tăng độ chính xác của biểu diễn.

## 10. Chương trình Java 17+ hoàn chỉnh

Lưu mã thành tệp `ErrorMetricsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.util.Arrays;

public class ErrorMetricsDemo {
    static double absoluteError(double approximate, double reference) {
        return Math.abs(approximate - reference);
    }

    static double relativeError(double approximate, double reference) {
        if (reference == 0.0) {
            throw new IllegalArgumentException("Reference must not be zero");
        }
        return absoluteError(approximate, reference) / Math.abs(reference);
    }

    static double naiveSum(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    static double kahanSum(double[] values) {
        double sum = 0.0;
        double correction = 0.0;
        for (double value : values) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("absolute=" + absoluteError(99.0, 100.0));
        System.out.println("relative=" + relativeError(99.0, 100.0));

        double[] values = new double[10];
        Arrays.fill(values, 0.1);
        System.out.println("naive=" + naiveSum(values));
        System.out.println("kahan=" + kahanSum(values));
        System.out.println("product=" + (0.1 * 10.0));

        System.out.println("ulp(1)=" + Math.ulp(1.0));
        System.out.println("ulp(1e16)=" + Math.ulp(1e16));

        double lost = (1e16 + 1.0) - 1e16;
        double kept = (1e16 - 1e16) + 1.0;
        System.out.println("lost=" + lost);
        System.out.println("kept=" + kept);

        BigDecimal decimalSum = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) {
            decimalSum = decimalSum.add(new BigDecimal("0.1"));
        }
        System.out.println("decimal=" + decimalSum);
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac --release 17 ErrorMetricsDemo.java
java ErrorMetricsDemo
~~~

Kết quả stdout:

~~~text
absolute=1.0
relative=0.01
naive=0.9999999999999999
kahan=1.0
product=1.0
ulp(1)=2.220446049250313E-16
ulp(1e16)=2.0
lost=0.0
kept=1.0
decimal=1.0
~~~

Giải thích:

- Sai số tuyệt đối giữa 99 và 100 bằng 1, còn sai số tương đối bằng 0,01 = 1%.
- Cộng `0.1` tuần tự mười lần không cho chính xác `1.0`; Kahan giảm mất mát làm tròn trong ví dụ này.
- `Math.ulp` tại \(10^{16}\) là 2, nên thêm 1 không thể làm tăng giá trị lưu trữ trong trường hợp này.
- Hai cách đặt dấu ngoặc cho phép trừ chứng minh thứ tự tính toán có thể thay đổi kết quả.
- `BigDecimal("0.1")` giữ đúng **giá trị thập phân 0,1**, nên cộng mười lần cho chuỗi thập phân `1.0`.

## 11. So sánh số thực bằng tolerance

Dùng `a == b` chỉ khi nghiệp vụ thực sự cần **cùng giá trị binary floating point**. Nếu hai kết quả được tính qua các quy trình khác nhau, so sánh xấp xỉ thường dùng kết hợp **ngưỡng tuyệt đối** và **ngưỡng tương đối**:

\[
|a-b|\ \le\ \text{absTol}+\text{relTol}\times\max(|a|,|b|).
\]

- `absTol` có đơn vị tương ứng đại lượng và hữu ích khi hai giá trị gần 0.
- `relTol` không có đơn vị, giúp dung sai tăng phù hợp với thang giá trị lớn.
- Các ngưỡng phải xuất phát từ **yêu cầu bài toán hoặc cận sai số** chứ không phải hằng “epsilon” được sao chép tùy tiện.

Ví dụ minh họa dành cho các số hữu hạn có hiệu và phép nhân ngưỡng vẫn biểu diễn được bằng `double`:

~~~java
static boolean nearlyEqual(double a, double b,
                           double absTol, double relTol) {
    if (!Double.isFinite(a) || !Double.isFinite(b)
            || !Double.isFinite(absTol) || !Double.isFinite(relTol)
            || absTol < 0.0 || relTol < 0.0) {
        return false;
    }

    double limit = absTol
            + relTol * Math.max(Math.abs(a), Math.abs(b));
    return Math.abs(a - b) <= limit;
}
~~~

Đây là **phương thức minh họa bên trong một lớp**, không phải chương trình độc lập. Với giá trị quá lớn, `Math.abs(a-b)` hoặc `limit` có thể overflow thành Infinity; khi so sánh độ chính xác cao trên toàn miền `double`, cần thuật toán so sánh được thiết kế tránh tràn/underflow hoặc chuyển sang mô hình biểu diễn thích hợp.

Ví dụ `nearlyEqual(0.1 + 0.2, 0.3, 1e-12, 1e-9)` cho `true` với các giá trị nhỏ này. Tuy nhiên, điều đó không chứng minh mọi số gần nhau đều tương đương về nghiệp vụ. Với tiền, mã định danh, số lượng chính xác hoặc điều kiện an toàn, tolerance có thể không phù hợp.

### Kiểm thử sai số cần so sánh với điều gì?

Nếu không có lời giải toán học chính xác, có thể sử dụng:

- Bộ dữ liệu có đáp án được suy ra độc lập và đáng tin cậy.
- Tính toán tham chiếu bằng `BigDecimal` hoặc phần mềm số chính xác cao khi mô hình toán học tương thích.
- Kiểm tra tính chất bất biến và miền kết quả của thuật toán.
- Thử nhiều thứ tự cộng, dữ liệu cực lớn/cực nhỏ, số gần triệt tiêu và các trường hợp ở sát số 0.

`BigDecimal` không tự giải quyết sai số cắt cụt thuật toán hoặc sai số đo từ đầu vào. Nếu tạo bằng `new BigDecimal(0.1)`, nó sẽ giữ **giá trị binary64 đã xấp xỉ**, khác với `new BigDecimal("0.1")`.

## 12. Những tình huống khiến sai số tích lũy thành vấn đề lớn

### 12.1. Tổng giá trị khác nhau rất nhiều về độ lớn

Ví dụ dãy `[1e16, 1, 1, -1e16]` có tổng chính xác bằng 2. Nhưng cộng tuần tự đơn giản có thể làm mất hai số 1 và trả về 0. Sắp xếp/gộp có chủ đích hoặc cộng bù có thể cải thiện một số trường hợp, nhưng cần đánh giá theo loại dãy số thực tế.

### 12.2. Vòng lặp cộng bước thời gian

Đo thời gian bằng `time += delta` trong rất nhiều bước có thể dẫn đến giá trị cuối khác `steps * delta` do sai số tích lũy. Nếu `delta` cố định, có thể tính từ chỉ số bước nguyên: `time = stepIndex * delta` khi phù hợp, nhằm tránh một chuỗi cộng dồn dài. Phép nhân vẫn làm tròn, nên phải kiểm tra kết quả với sai số cho phép.

### 12.3. Tích và chia qua nhiều bước

Sai số của nhân/chia có thể tích lũy theo hệ số tương đối. Tích của nhiều số lớn/nhỏ có thể overflow hoặc underflow trước khi ra kết quả mong muốn. Với bài toán như xác suất rất nhỏ, có thể dùng miền logarithm hoặc thuật toán chuẩn hóa để giảm các giá trị trung gian ngoài miền biểu diễn.

### 12.4. Sai số lan truyền trong thuật toán lặp

Một thuật toán như cập nhật vị trí `position += velocity * dt` tích lũy sai số từ dữ liệu đầu vào, phép nhân, phép cộng và số bước. Nếu thuật toán còn có **tính nhạy với điều kiện ban đầu**, sai số nhỏ có thể lớn nhanh theo thời gian. Khi đó chỉ dùng kiểu `double` chưa chắc giải quyết được; cần phân tích mô hình, bước tính, điều kiện ổn định và quy tắc dừng.

## 13. Forward error, backward error và điều kiện bài toán

Ba khái niệm giúp phân tích kỹ hơn:

- **Forward error:** độ lệch giữa kết quả tính \(\hat{y}\) và kết quả lý tưởng \(f(x)\) của *cùng đầu vào chuẩn* \(x\).
- **Backward error:** tìm xem \(\hat{y}\) có thể được hiểu là kết quả đúng \(f(x+\Delta x)\) của một đầu vào được nhiễu rất nhỏ hay không.
- **Conditioning:** bản thân bài toán nhạy tới mức nào trước thay đổi nhỏ của đầu vào. Đây là thuộc tính của bài toán, khác với tính ổn định của cách cài đặt thuật toán.

Một thuật toán được gọi là **backward stable** trong điều kiện phân tích thích hợp khi kết quả của nó tương đương nghiệm đúng của một đầu vào gần đầu vào ban đầu. Tuy nhiên, nếu bài toán *ill-conditioned*, sai số đầu vào nhỏ vẫn có thể làm thay đổi kết quả rất lớn. Vì vậy “thuật toán ổn định” không đồng nghĩa “sai số tương đối đầu ra luôn nhỏ”.

Ví dụ khi trừ hai số gần bằng nhau, kết quả thực nhỏ hơn nhiều so với cả hai toán hạng. Sai số tương đối của hai số đầu vào có thể bị phóng đại trong hiệu của chúng. Nếu hiểu điều kiện bài toán, ta sẽ tránh kỳ vọng không thực tế rằng chỉ thay `float` bằng `double` là có thể giữ đủ mọi chữ số có nghĩa.

## 14. Các biện pháp giảm sai số và lựa chọn kiểu dữ liệu

| Biện pháp | Phù hợp khi | Lưu ý |
| --- | --- | --- |
| `double` thay cho `float` | Cần thêm độ chính xác và miền số | `double` vẫn làm tròn |
| Cộng bù Kahan hoặc pairwise summation | Tổng nhiều số floating point | Đo hiệu quả trên tập dữ liệu đại diện |
| Đổi thứ tự hoặc biến đổi công thức ổn định | Có nguy cơ mất chữ số/cancellation | Phải bảo toàn ý nghĩa toán học và xử lý trường hợp biên |
| `Math.fma(a, b, c)` | Tính \(a\times b+c\) cần một lần làm tròn | Không giải quyết mọi sai số hoặc mất dữ liệu đầu vào |
| `Math.hypot(x, y)` | Tính \(\sqrt{x^2+y^2}\) | Giảm overflow/underflow trung gian |
| `BigDecimal` với chuỗi thập phân | Tiền và phép tính thập phân cần chính xác | Chia có thể cần `MathContext`/quy tắc làm tròn |
| Giá trị tham chiếu có độ chính xác cao | Kiểm thử giới hạn sai số thuật toán | Chọn tham chiếu độc lập và đúng miền toán học |
| Absolute + relative tolerance | So sánh kết quả tính xấp xỉ | Quy định ngưỡng theo nghiệp vụ |

### Trường hợp cần ưu tiên số chính xác

Với số nguyên đếm được trong miền `long`, dùng `long` để tránh sai số làm tròn của `double`. Với số nguyên vượt 64 bit, dùng `BigInteger`. Với số tiền cần chính xác theo thập phân, dùng `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất theo quy tắc nghiệp vụ.

Thêm nhiều chữ số sau dấu phẩy trong biểu diễn text **không làm tăng độ chính xác** của giá trị `double` vốn đã được tính.

## 15. Lỗi phổ biến

1. **So sánh `double` bằng `==` trong mọi hoàn cảnh.** Khi phép tính có làm tròn, so sánh xấp xỉ cần ngưỡng và điều kiện bài toán.
2. **Dùng `Double.MIN_VALUE` làm epsilon.** Đây là số subnormal dương nhỏ nhất, không phải sai số tương đối điển hình hay độ phân giải quanh 1.0.
3. **Gọi `Math.ulp(1.0)` là giới hạn sai số cho mọi số.** ULP phụ thuộc độ lớn số đang xét và mô hình tính.
4. **Tính sai số tương đối khi tham chiếu bằng 0.** Công thức không xác định; cần chỉ tiêu phù hợp.
5. **Cho rằng sai số luôn tăng đơn điệu sau mỗi phép tính.** Sai số có thể cộng hoặc triệt tiêu; chỉ cận sai số và đặc tính thuật toán cho dự đoán đáng tin.
6. **Tin `BigDecimal.valueOf(double)` hoặc `new BigDecimal(double)` phục hồi số đo/chuỗi đã mất chính xác.** Chúng tạo từ một `double` đã làm tròn; muốn giữ đúng số thập phân văn bản thì parse trực tiếp từ chuỗi.
7. **Chỉ kiểm tra dữ liệu dễ như toàn số nguyên nhỏ.** Cần thử gần 0, hiệu hai số gần nhau, quy mô khác nhau, tổng dài, giá trị biên và thứ tự gộp khác nhau.
8. **Đọc sai số in từ `System.out.println` như toàn bộ sai số thật.** Cách định dạng và round-trip của `Double.toString` không cung cấp chứng minh về cận sai số của thuật toán.
9. **Bỏ qua `NaN` và `Infinity`.** Các giá trị đặc biệt có thể khiến so sánh/ngưỡng không còn ý nghĩa; cần chính sách kiểm tra đầu vào và kết quả.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Kết quả 198 so với tham chiếu 200 có sai số tuyệt đối, tương đối và phần trăm là bao nhiêu?

Đáp án: \(E_{\mathrm{abs}}=2\), \(E_{\mathrm{rel}}=2/200=0{,}01\), tức **1%**.

**Câu 2.** Nếu giá trị tham chiếu bằng 0, tại sao không thể nói sai số tương đối bằng 0%?

Đáp án: Vì mẫu số trong \(|\hat{x}-x|/|x|\) bằng 0; công thức không xác định. Có thể dùng sai số tuyệt đối hoặc một thước đo thay thế được định nghĩa rõ.

**Câu 3.** Vì sao phép cộng mười lần `0.1` có thể khác phép nhân `10 * 0.1`?

Đáp án: Hai biểu thức có số lượng và trình tự làm tròn khác nhau. Từng phép cộng có thể tích lũy sai số; phép nhân chỉ có một phép toán arithmetic sau khi các toán hạng đã được biểu diễn.

**Câu 4.** Tại sao `(1e16 + 1.0) - 1e16` có thể bằng 0 nhưng `(1e16 - 1e16) + 1.0` bằng 1?

Đáp án: `1e16 + 1` không tăng giá trị binary64 tại độ lớn này, nên phép trừ cuối mất thông tin. Thứ tự trừ trước giữ lại số 1.

**Câu 5.** Có phải Kahan luôn cho tổng chính xác như `BigDecimal` không?

Đáp án: Không. Kahan giảm một số sai số làm tròn trong tổng floating point nhưng vẫn dùng `double` và không thay thế số học thập phân chính xác hay giải quyết mọi trường hợp.

**Câu 6.** Nếu sai số tuyệt đối của hai lời giải nhỏ nhưng sai số tương đối rất lớn, thường cần kiểm tra điều gì?

Đáp án: Giá trị tham chiếu có thể rất gần 0; kiểm tra thang đo, tiêu chí sai số, khả năng cancellation và tính nhạy của bài toán.

## 17. Ghi nhớ và tài liệu tham khảo

- **Sai số tuyệt đối:** \(E_{\mathrm{abs}}=|\hat{x}-x|\), có đơn vị như đại lượng.
- **Sai số tương đối:** \(E_{\mathrm{rel}}=E_{\mathrm{abs}}/|x|\) khi \(x\ne0\), thích hợp so sánh giữa các thang đo; gần 0 phải thận trọng.
- **Tích lũy sai số:** mỗi lần làm tròn có thể ảnh hưởng bước sau; thứ tự phép tính và conditioning của bài toán quyết định độ tin cậy kết quả.
- **Giảm sai số:** chọn thuật toán ổn định, Kahan/pairwise summation khi cộng dài, dùng `Math.fma`/công thức số ổn định khi phù hợp, kiểm thử bằng tham chiếu đáng tin và xác định dung sai theo nghiệp vụ.
- **Lựa chọn kiểu:** `double` phù hợp nhiều bài toán số học xấp xỉ; `BigDecimal`/số nguyên phù hợp khi cần kết quả thập phân hoặc số đếm chính xác.

Nguồn tra cứu chính thức:

- [Java Language Specification 17 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3).
- [Java SE 17 API — Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `ulp`, `fma`, `hypot`.
- [Java SE 17 API — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): giới hạn và biểu diễn binary64.
- [Java SE 17 API — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): số thập phân chính xác và kiểm soát làm tròn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-003">Giới hạn của hằng epsilon cố định cho mọi bài toán</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của hằng epsilon cố định cho mọi bài toán

Khi xử lý số thực `float` và `double`, phép so sánh bằng tuyệt đối bằng `==` có thể thất bại dù hai kết quả được kỳ vọng bằng nhau về mặt toán học. Một cách xử lý phổ biến là so sánh sai khác tuyệt đối với một ngưỡng nhỏ, thường gọi là **epsilon**:

~~~java
boolean approximatelyEqual = Math.abs(a - b) <= 1e-9;
~~~

Tuy nhiên, **không có một giá trị epsilon cố định phù hợp với mọi bài toán, mọi thang đo và mọi độ lớn của số**. Giá trị `1e-9` có thể quá lỏng đối với số cỡ `1e-12`, nhưng lại quá nghiêm ngặt đối với số cỡ `1e12`. Ngưỡng “nhỏ” phải được định nghĩa theo đơn vị, mức sai số được chấp nhận, độ lớn dữ liệu và mục đích của phép so sánh.

Bài học phân biệt sai số tuyệt đối, sai số tương đối và đơn vị ULP; chỉ ra giới hạn của cách dùng một epsilon tuyệt đối; sau đó xây dựng cách so sánh có chủ đích bằng Java 17+, với xử lý giá trị đặc biệt như NaN và vô cực.

## 1. Vì sao số thực có sai số?

`float` và `double` được Java biểu diễn dưới dạng số thực nhị phân hữu hạn theo IEEE 754. Nhiều phân số thập phân quen thuộc, chẳng hạn `0.1` và `0.2`, không có biểu diễn nhị phân hữu hạn chính xác. Việc lưu trữ và tính toán phải làm tròn.

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum == 0.3);        // false
System.out.println(sum);               // 0.30000000000000004
~~~

Đoạn trên là minh họa để đặt trong phương thức Java. Kết quả `sum` khác bit so với literal `0.3` một lượng nhỏ, mặc dù theo số học thập phân lý tưởng `0.1 + 0.2 = 0.3`.

Không phải mọi phép tính thực đều sai khác: các số có biểu diễn nhị phân chính xác như `0.5`, `0.25` hay một số nguyên nhỏ có thể cho kết quả chính xác trong những phép toán nhất định. Sai số phụ thuộc cả dữ liệu và phép toán, không phải đặc điểm “mọi số `double` đều không thể so sánh bằng”.

Cũng không nên đồng nhất **sai số biểu diễn** với **sai số đo đạc**. Giá trị `3.14159` làm tròn từ một phép đo thực nghiệm có thể mang độ bất định lớn hơn rất nhiều lỗi làm tròn của `double`. Ngưỡng so sánh trong ứng dụng có thể phải phản ánh sai số đo, giới hạn sensor, biến đổi đơn vị hoặc tiêu chí nghiệp vụ, bên cạnh sai số máy tính.

## 2. Ba khái niệm thường bị gọi chung là epsilon

Từ “epsilon” dễ gây nhầm vì được dùng trong nhiều ngữ cảnh:

| Khái niệm | Ý nghĩa | Ví dụ hoặc cách xác định |
| --- | --- | --- |
| **Dung sai tuyệt đối (absolute tolerance)** | Sai khác tuyệt đối lớn nhất được chấp nhận, theo **đơn vị của dữ liệu** | `absTol = 0.01` mét |
| **Dung sai tương đối (relative tolerance)** | Sai khác được chấp nhận so với **độ lớn giá trị**; không có đơn vị | `relTol = 1e-6` |
| **Machine epsilon** | Đặc trưng về khoảng cách biểu diễn của định dạng số thực quanh 1.0; không phải mặc định dung sai nghiệp vụ | Với `double`, khoảng cách `Math.ulp(1.0)` bằng `2.220446049250313E-16` |

Machine epsilon quanh 1.0 **không cố định khoảng cách giữa mọi cặp số có thể biểu diễn**. Khoảng cách giữa các số thực liền kề thay đổi theo độ lớn và vùng số mũ. Java cung cấp `Math.ulp(x)` để quan sát đơn vị theo giá trị cuối cùng tại `x` (ULP, *unit in the last place*).

Ví dụ:

~~~java
System.out.println(Math.ulp(1.0));   // 2.220446049250313E-16
System.out.println(Math.ulp(1e12));  // 1.220703125E-4
~~~

ULP quanh `1e12` lớn hơn rất nhiều ULP quanh `1.0`. Do đó, việc dùng một ngưỡng nhỏ tuyệt đối bằng `1e-15` hay `1e-9` mà không xét độ lớn dữ liệu có thể không hợp lý. Cả ULP lẫn machine epsilon đều **không tự quyết định** phép sai khác nào là chấp nhận được trong bài toán cụ thể.

## 3. Giới hạn thứ nhất: epsilon cố định quá lỏng khi số rất nhỏ

Giả sử dùng một dung sai tuyệt đối chung:

~~~java
static boolean fixed(double a, double b) {
    return Math.abs(a - b) <= 1e-9;
}
~~~

Đây là phương thức minh họa cần đặt trong lớp Java. Xét:

~~~java
double a = 1e-12;
double b = 2e-12;
boolean equal = Math.abs(a - b) <= 1e-9; // true
~~~

Sai khác tuyệt đối là `1e-12` nên nhỏ hơn epsilon `1e-9`. Nhưng `b` **gấp đôi** `a`: sai khác tương đối so với độ lớn lớn hơn bằng 50% khi lấy mẫu số là `max(|a|, |b|)`. Trong bài toán yêu cầu tương đối chính xác, coi hai số “gần bằng” là không hợp lý.

Kết luận: **epsilon tuyệt đối có thể che giấu sai khác quan trọng ở thang giá trị nhỏ**. Không thể chỉ nhìn vào số mũ của epsilon mà xác định mức độ nghiêm ngặt.

## 4. Giới hạn thứ hai: epsilon cố định quá chặt khi số lớn

Xét hai kết quả gần bằng nhau so với thang đo:

~~~java
double a = 1e12;
double b = 1e12 + 0.01;

boolean equal = Math.abs(a - b) <= 1e-9; // false
~~~

Sai khác tuyệt đối vào khoảng `0.01`, lớn hơn hằng `1e-9` nhiều lần, nên kết quả là `false`. Tuy nhiên, so với độ lớn `1e12`, sai khác này có tỷ lệ khoảng `1e-14`. Trong nhiều bài toán có mức dung sai tương đối `1e-12`, hai giá trị như vậy có thể được chấp nhận.

Do `double` làm tròn theo lưới số nhị phân, sai khác thực tế tính từ hai giá trị đã lưu có thể không đúng `0.01` tuyệt đối; trong ví dụ này nó xấp xỉ `0.010009765625`. Điều đó càng cho thấy phải suy luận trên **giá trị được biểu diễn thực tế**, thay vì cho rằng mọi literal số thập phân đều được lưu chính xác.

Kết luận: **epsilon tuyệt đối rất nhỏ có thể từ chối sai khác không đáng kể theo tỷ lệ ở thang giá trị lớn**.

## 5. Đánh giá sai số tuyệt đối và tương đối

Sai khác tuyệt đối giữa `a` và `b`:

\[
d_{\mathrm{abs}} = |a-b|
\]

Một dạng sai khác tương đối đối xứng, khi ít nhất một giá trị khác 0, là:

\[
d_{\mathrm{rel}} =
\frac{|a-b|}{\max(|a|, |b|)}
\]

Trong đó:

- Sai khác tuyệt đối vẫn mang đơn vị dữ liệu: sai khác `0.01` giây khác `0.01` đồng tiền hay `0.01` mét.
- Sai khác tương đối không có đơn vị. Nó hữu ích khi thang đo trải rộng qua nhiều bậc độ lớn.
- Khi cả hai số bằng 0, biểu thức tỷ số trên có mẫu số 0; nếu một hoặc cả hai số gần 0, sai khác tương đối trở nên nhạy và không đáp ứng tốt nhu cầu “gần 0”.

Đối với dữ liệu gần 0, cần một **ngưỡng tuyệt đối dựa trên ý nghĩa của bài toán**. Đối với dữ liệu lớn, ngưỡng tương đối thường phản ánh mức chính xác mong muốn tốt hơn. Vì thế một quy tắc kết hợp phổ biến sử dụng hai dung sai khác nhau:

\[
|a-b| \le \max\left(\mathrm{absTol},
\mathrm{relTol}\cdot\max(|a|,|b|)\right)
\]

Có thể viết dưới dạng logic:

~~~java
difference <= absTol || difference <= relTol * scale
~~~

Trong đó `difference = Math.abs(a - b)` và `scale = Math.max(Math.abs(a), Math.abs(b))`. Đây là **mẫu thiết kế**, không phải định lý rằng mọi miền ứng dụng phải chọn cùng cách chuẩn hóa hoặc cùng dung sai.

### Ví dụ đối chiếu

| Hai giá trị | absTol | relTol | Ý nghĩa |
| --- | ---: | ---: | --- |
| `0.0` và `1e-13` | `1e-12` | `1e-9` | Có thể chấp nhận sai khác cực nhỏ gần 0 |
| `1e-12` và `2e-12` | `1e-15` | `1e-9` | Không nên coi bằng chỉ vì số tuyệt đối rất nhỏ |
| `1e12` và `1e12 + 0.01` | `1e-15` | `1e-12` | Có thể chấp nhận sai khác tuyệt đối lớn hơn nhưng tương đối nhỏ |

Các ngưỡng ở bảng nhằm **minh họa cách chọn theo yêu cầu**, không có giá trị mặc định được Java hoặc IEEE 754 khuyến nghị cho mọi chương trình.

## 6. Chọn dung sai theo miền bài toán

Trước khi viết hàm so sánh, cần xác định:

1. **Đơn vị và độ phân giải:** dữ liệu nhiệt độ chính xác đến 0.1°C khác dữ liệu vị trí cần độ chính xác 0.1 micromet. `absTol` có đơn vị tương ứng với đại lượng được so sánh.
2. **Quy mô và bậc độ lớn:** giá trị đo biến thiên từ `1e-12` đến `1e12` thường cần nghĩ đến sai số tương đối hoặc phép chuẩn hóa thích hợp.
3. **Nguồn sai số:** biểu diễn nhị phân, phép cộng/trừ gần nhau, phép đo, chuyển đổi dữ liệu, cách thuật toán tích lũy lỗi.
4. **Hậu quả của sai lệch:** tiêu chí nghiệm thu hiển thị, kiểm thử thuật toán, cảnh báo an toàn, điều kiện hội tụ có thể đòi mức nghiêm ngặt khác nhau.
5. **Mức tin cậy:** nên lấy ngưỡng từ đặc tả, độ phân giải thiết bị, sai số phân tích, kiểm thử thực nghiệm có đối chứng hoặc ràng buộc nghiệm thu; không tự đặt `1e-9` chỉ vì nó trông nhỏ.

Ví dụ, trong kiểm tra cảm biến có độ phân giải 0.01 đơn vị, so sánh kết quả bằng epsilon `1e-15` là không phản ánh khả năng đo thực tế. Trong bài toán giải hệ phương trình có nghiệm gần 0, kiểm tra chỉ bằng sai số tương đối có thể gây hành vi khó hiểu. Còn với các đại lượng luôn ở một thang đo hẹp, một dung sai tuyệt đối cố định, đã được đặc tả, có thể **rất phù hợp**.

Không cần bắt buộc mọi phép so sánh phải dùng cả absTol và relTol. Hai tiêu chí này chỉ là công cụ. Chọn chúng tùy ngữ nghĩa chứ không theo một “công thức thần kỳ”.

## 7. Ví dụ Java 17+: so sánh fixed và kết hợp

Tạo tệp `EpsilonLimitsDemo.java`:

~~~java
public class EpsilonLimitsDemo {
    static boolean fixed(double a, double b, double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon < 0.0) {
            throw new IllegalArgumentException("epsilon");
        }
        return Double.isFinite(a) && Double.isFinite(b)
                && Math.abs(a - b) <= epsilon;
    }

    static boolean mixed(double a, double b, double absTol, double relTol) {
        if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
                || absTol < 0.0 || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("tolerance");
        }
        if (a == b) return true; // Cả hai cùng vô cực, hoặc +0.0/-0.0
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;

        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return difference <= absTol || difference <= relTol * scale;
    }

    public static void main(String[] args) {
        double sum = 0.1 + 0.2;
        System.out.println("sum == 0.3: " + (sum == 0.3));
        System.out.println("fixed sum: " + fixed(sum, 0.3, 1e-9));

        System.out.println("small fixed: "
                + fixed(1e-12, 2e-12, 1e-9));
        System.out.println("small mixed: "
                + mixed(1e-12, 2e-12, 1e-15, 1e-9));

        System.out.println("large fixed: "
                + fixed(1e12, 1e12 + 0.01, 1e-9));
        System.out.println("large mixed: "
                + mixed(1e12, 1e12 + 0.01, 1e-15, 1e-12));

        System.out.println("near zero mixed: "
                + mixed(0.0, 1e-13, 1e-12, 1e-9));
        System.out.println("NaN mixed: "
                + mixed(Double.NaN, 1.0, 1e-12, 1e-9));
        System.out.println("same infinity mixed: "
                + mixed(Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1e-12, 1e-9));

        System.out.println("ulp(1): " + Math.ulp(1.0));
        System.out.println("ulp(1e12): " + Math.ulp(1e12));

        System.out.println("not transitive: "
                + (fixed(0.0, 0.75, 1.0)
                && fixed(0.75, 1.5, 1.0)
                && !fixed(0.0, 1.5, 1.0)));
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac EpsilonLimitsDemo.java
java EpsilonLimitsDemo
~~~

Đầu ra đã được kiểm chứng:

~~~text
sum == 0.3: false
fixed sum: true
small fixed: true
small mixed: false
large fixed: false
large mixed: true
near zero mixed: true
NaN mixed: false
same infinity mixed: true
ulp(1): 2.220446049250313E-16
ulp(1e12): 1.220703125E-4
not transitive: true
~~~

### Phân tích kết quả

- `fixed sum` là `true` vì `0.1 + 0.2` và `0.3` chỉ sai khác khoảng `5.55e-17`, nhỏ hơn `1e-9`.
- `small fixed` là `true` nhưng `small mixed` là `false`: epsilon tuyệt đối `1e-9` quá rộng cho cặp `1e-12` và `2e-12`; tiêu chí tương đối chặt hơn phản ánh sai khác lớn theo tỷ lệ.
- `large fixed` là `false` còn `large mixed` là `true`: sai khác `0.01` quá lớn so với epsilon tuyệt đối `1e-9`, nhưng đủ nhỏ theo tỷ lệ `1e-12` trên thang `1e12`.
- `near zero mixed` là `true` nhờ `absTol`: quy tắc tương đối thuần túy không được thiết kế để xử lý vùng gần 0 theo cùng ý nghĩa.
- `Math.ulp` minh họa khoảng cách biểu diễn thay đổi mạnh theo độ lớn.
- `not transitive` bằng `true` chứng minh rằng **gần bằng theo dung sai không có tính bắc cầu**: `0` gần `0.75`, `0.75` gần `1.5`, nhưng `0` không gần `1.5` với cùng epsilon 1.

Ví dụ `mixed` minh họa một chính sách so sánh cụ thể. Nó chủ ý cho hai giá trị **cùng vô cực** là bằng nhau, còn NaN luôn không bằng. Trong ứng dụng bắt buộc hai toán hạng hữu hạn, hãy chuyển kiểm tra `Double.isFinite(a) && Double.isFinite(b)` lên **trước** nhánh `a == b`. Không có một quy tắc xử lý vô cực thích hợp cho mọi miền nghiệp vụ.

Với đối số có độ lớn rất lớn và trái dấu, `a - b` có thể tràn ra vô cực dù `a`, `b` đều hữu hạn. Trong ví dụ, khi đó `difference` là vô cực và không vượt qua các ngưỡng hữu hạn; hàm trả `false`. Nếu xây dựng thuật toán phân tích sai số chuyên dụng, cần xem cả vấn đề overflow và underflow khi tính chênh lệch, thay vì cho rằng phép trừ luôn chính xác.

## 8. Tại sao “so sánh gần bằng” không phải quan hệ bằng thật?

Xét `epsilon = 1.0`:

- `a = 0.0` gần `b = 0.75` vì sai khác 0.75.
- `b = 0.75` gần `c = 1.5` vì sai khác 0.75.
- `a = 0.0` **không** gần `c = 1.5` vì sai khác 1.5.

Vì thiếu tính bắc cầu, kiểm tra `Math.abs(a - b) <= epsilon` không tạo thành quan hệ tương đương toán học. Hệ quả:

- Không dùng “bằng trong epsilon” làm định nghĩa trực tiếp của `equals`/`hashCode` trên đối tượng nếu nó vi phạm hợp đồng tương đương.
- Không dùng bộ so sánh “trả 0 nếu gần bằng” một cách tùy tiện trong `TreeSet`, `TreeMap` hoặc `Collections.sort`: nó có thể vi phạm hợp đồng thứ tự của `Comparator` và gây hành vi khó đoán.
- Nếu cần nhóm các số theo bucket, cần xác định **quy tắc lượng tử hóa** (quantization), đơn vị, ranh giới và cách xử lý số âm một cách nhất quán. Bucket cũng có sai khác ở ranh giới, nhưng tạo khóa xác định thay vì so cặp tùy ý.

Trong một số nghiệp vụ, so sánh gần bằng dùng cho **kiểm chứng kết quả** là hợp lý, còn so sánh chính xác dùng để xác định **danh tính hoặc khóa dữ liệu**. Hai khái niệm cần tách biệt.

## 9. Những trường hợp đặc biệt cần xử lý

### 9.1. NaN

Với `double a = Double.NaN`, mọi phép `a == a` đều là `false`, và các phép `<`, `<=`, `>`, `>=` liên quan đến NaN cũng là `false`. Đừng giả định biểu thức so sánh gần bằng nhận NaN là “giá trị cần bỏ qua”; hãy quyết định tường minh:

~~~java
if (Double.isNaN(a) || Double.isNaN(b)) {
    // Từ chối, thông báo dữ liệu thiếu, hoặc chính sách riêng.
}
~~~

Đây là đoạn kiểm tra minh họa. Đối với đối tượng wrapper, `Double.equals` có hợp đồng riêng về NaN, khác toán tử primitive `==`.

### 9.2. Vô cực

`Math.abs(Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY)` là NaN, nên ngay cả hai giá trị vô cực cùng dấu cũng không được coi gần bằng bởi biểu thức chênh lệch đơn giản.

Nếu ứng dụng chấp nhận hai vô cực giống nhau, kiểm tra `a == b` **trước** khi tính `Math.abs(a-b)`. Nếu ứng dụng chỉ chấp nhận số hữu hạn, dùng `Double.isFinite` trước.

### 9.3. Hai dạng số không

`+0.0 == -0.0` là `true` cho primitive, nhưng hai bit pattern có dấu khác nhau. Hàm `mixed` coi hai số không này bằng nhau theo quy ước số học. Nếu bit dấu có ý nghĩa (ví dụ một số ngữ cảnh tính toán IEEE 754), hãy định nghĩa tiêu chí riêng hoặc xem `Double.doubleToRawLongBits`/`Double.doubleToLongBits` theo nhu cầu.

### 9.4. Giá trị nhỏ nhất và underflow

`Double.MIN_VALUE` là số `double` dương nhỏ nhất khác 0 (subnormal), **không phải giá trị âm nhất** của `double`. Ở rất gần 0, `relTol * scale` có thể dưới miền biểu diễn và trở thành 0, nên một dung sai tuyệt đối thích hợp thường có vai trò quan trọng.

### 9.5. Phép chia để tính sai số tương đối

Biểu thức `Math.abs(a-b) / Math.max(Math.abs(a), Math.abs(b))` có thể gặp mẫu số 0 khi cả hai bằng 0, hoặc các trường hợp số thực đặc biệt. So sánh bằng phép nhân như `difference <= relTol * scale` tránh được phép chia trực tiếp, nhưng vẫn cần xem overflow/underflow và kiểm tra miền giá trị. Không phải mọi cách cài đặt tương đương về đại số đều tương đương tuyệt đối khi thực thi bằng số thực hữu hạn.

## 10. Phân biệt sai số làm tròn với ổn định thuật toán

Một thuật toán có thể sinh sai số lớn vì **mất chữ số có nghĩa** (catastrophic cancellation), lặp tích lũy sai số hoặc điều kiện bài toán kém, không chỉ vì lựa chọn epsilon sai. Ví dụ khi lấy hiệu của hai số gần nhau, các chữ số có nghĩa chung có thể bị triệt tiêu; kết quả tương đối của hiệu có thể rất thiếu chính xác.

Vì vậy, nếu hai cách tính trên cùng dữ liệu không gần nhau, đừng ngay lập tức **tăng epsilon cho tới khi kiểm thử qua**. Điều đó có thể che lỗi của thuật toán, lỗi chuyển đơn vị, sai điều kiện đầu vào hoặc lựa chọn phương pháp số không ổn định.

Một quy trình hợp lý:

1. Dùng đại lượng/đơn vị đã được xác minh, dự đoán thang giá trị và khoảng sai số có thể chấp nhận.
2. Kiểm tra phương pháp tính có điều kiện tốt không; khi cần, dùng phép toán ổn định hơn hoặc thuật toán chính xác cao hơn.
3. Chọn tiêu chí kiểm thử dựa trên yêu cầu thực tế và phân tích sai số, sau đó cố định nó trong test; không hiệu chỉnh chỉ để giấu test thất bại.
4. Kiểm thử cả giá trị gần 0, giá trị cực lớn/nhỏ, NaN, vô cực và số âm nếu chúng thuộc miền đầu vào.
5. Ghi rõ lý do và phạm vi áp dụng của `absTol` và `relTol` trong tài liệu hoặc tên hằng thay vì một biến toàn cục mơ hồ như `EPSILON`.

Một bài toán tài chính đòi giá trị thập phân chính xác có thể nên dùng `BigDecimal`, phép tính theo đơn vị nhỏ nhất (ví dụ số xu nguyên) hoặc mô hình số học theo đặc tả; thay epsilon không biến `double` thành số thập phân chính xác. Với hình học tính toán, điều kiện định hướng gần thẳng hàng có thể cần predicate robust thay vì so sánh epsilon tùy tiện. Đó là những lựa chọn thuộc **thiết kế bài toán**, không có một hằng dùng chung.

## 11. Các lỗi phổ biến

**Lỗi 1 — Coi `1e-9` là “độ chính xác của double”.**  
`double` có độ phân giải phụ thuộc độ lớn. `1e-9` là một dung sai do ứng dụng lựa chọn, không phải đặc tính cố định của `double`.

**Lỗi 2 — Chỉ dùng sai số tương đối gần số 0.**  
Khi thang đo gần 0, ngưỡng tương đối có thể cực nhỏ hoặc 0; cần cân nhắc một absTol có ý nghĩa miền bài toán.

**Lỗi 3 — Dùng epsilon tuyệt đối mà không để ý đơn vị.**  
Khoảng chênh 0.01 mét và 0.01 giây không thể tùy ý dùng cùng dung sai, trừ khi đặc tả thiết kế nêu rõ.

**Lỗi 4 — Không xử lý NaN và vô cực.**  
`NaN` có phép so sánh đặc biệt; vô cực trừ chính nó sinh NaN. Cần chọn chính sách cho từng loại.

**Lỗi 5 — Xây dựng Comparator “gần bằng”.**  
Quan hệ gần bằng có thể không bắc cầu nên không dùng trực tiếp làm tương đương hoặc thứ tự trong cấu trúc dữ liệu.

**Lỗi 6 — Tăng epsilon khi phát hiện test sai.**  
Điều chỉnh dung sai phải có cơ sở về lỗi tính toán, dữ liệu đầu vào hoặc ràng buộc nghiệm thu; ngưỡng lớn tùy tiện có thể che lỗi thực sự.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao dùng `Math.abs(a-b) <= 1e-9` có thể cho `true` với `a=1e-12`, `b=2e-12` dù chúng khác nhau nhiều theo tỷ lệ?  
**Đáp án:** Sai khác tuyệt đối `1e-12` nhỏ hơn `1e-9`, nhưng `b` gấp đôi `a`; epsilon tuyệt đối quá lớn so với thang giá trị.

**Câu 2.** Vì sao một epsilon tuyệt đối rất nhỏ lại không phù hợp cho `a=1e12`, `b=1e12+0.01`?  
**Đáp án:** Sai khác tuyệt đối khoảng 0.01, nhưng sai khác tương đối chỉ khoảng `1e-14`. Nếu bài toán chấp nhận mức tương đối đó, dùng duy nhất epsilon tuyệt đối `1e-9` là quá nghiêm.

**Câu 3.** Machine epsilon và absTol giống nhau không?  
**Đáp án:** Không. Machine epsilon mô tả khoảng cách biểu diễn liên quan định dạng số thực quanh 1; absTol là tiêu chí chấp nhận sai khác tuyệt đối của **bài toán**.

**Câu 4.** Vì sao `Double.isFinite(a) && Double.isFinite(b)` có thể cần thiết trước khi so sánh gần bằng?  
**Đáp án:** NaN và vô cực không là giá trị hữu hạn. Nếu miền nghiệp vụ chỉ cho phép số hữu hạn, phải loại chúng theo chính sách chứ không phụ thuộc kết quả tình cờ của phép trừ hoặc quan hệ.

**Câu 5.** Với `epsilon=1.0`, vì sao không thể xây dựng `equals` từ quan hệ `|a-b|<=epsilon`?  
**Đáp án:** Có thể `a~b` và `b~c` nhưng `a!~c`, tức quan hệ không bắc cầu, vi phạm hợp đồng tương đương của `equals`.

**Câu 6.** Khi nào dùng `BigDecimal` thay vì tăng epsilon?  
**Đáp án:** Khi yêu cầu giá trị thập phân chính xác, quy tắc làm tròn xác định hoặc biểu diễn tiền tệ theo đặc tả; epsilon chỉ định nghĩa tính “gần bằng”, không khôi phục chữ số thập phân đã mất.

## 13. Ghi nhớ

- Epsilon dùng cho so sánh gần bằng là **dung sai theo ngữ cảnh**, không có giá trị cố định tối ưu cho tất cả chương trình.
- **Sai số tuyệt đối** có đơn vị và phù hợp với cận sai khác cố định; **sai số tương đối** thay đổi theo độ lớn, hữu ích khi dữ liệu trải rộng nhiều thang đo.
- Kết hợp absTol và relTol thường giúp xử lý cả vùng gần 0 và số rất lớn, nhưng các ngưỡng phải được định nghĩa từ yêu cầu cụ thể.
- ULP và machine epsilon mô tả biểu diễn số thực, không đồng nghĩa với dung sai nghiệp vụ.
- Hãy xét NaN, vô cực, underflow/overflow, tính không bắc cầu và độ ổn định thuật toán trước khi biến một phép so sánh gần bằng thành quy tắc quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-004">Sắp xếp thứ tự phép tính để giảm mất thông tin</a>

<details>
<summary>Xem chi tiết</summary>

# Sắp xếp thứ tự phép tính để giảm mất thông tin trong Java

Với `float` và `double`, hai biểu thức **tương đương trong toán học số thực** có thể cho kết quả khác nhau trên Java. Nguyên nhân là số dấu phẩy động chỉ có hữu hạn bit phần trị và số mũ; sau mỗi phép toán, kết quả có thể bị làm tròn, làm mất những đóng góp quá nhỏ, hoặc trở thành vô cực/zero vì tràn và underflow.

Ví dụ điển hình:

```java
double large = 1e16;

double lost = (large + 1.0) - large; // 0.0
double kept = (large - large) + 1.0; // 1.0
```

Trong toán học, cả hai đều bằng 1. Nhưng phép `large + 1.0` làm tròn về `large`: giá trị 1 bị mất trước khi thực hiện phép trừ. Khi triệt tiêu hai số lớn trước, giá trị 1 được giữ nguyên.

**Điểm cốt lõi:** thứ tự tính toán là một phần của thiết kế thuật toán số. Ta có thể giảm mất thông tin bằng cách cộng/ghép nhóm phù hợp, tránh trừ các giá trị gần bằng nhau, dùng công thức ổn định hơn và kiểm soát độ lớn của phép toán trung gian. Tuy nhiên, **không có một cách đảo thứ tự luôn tốt cho mọi dữ liệu**. Cần xét miền đầu vào và đo sai số.

Bài học thuộc mục **Sai số và ổn định** trong module **Java core – numbers**. Trọng tâm là cách áp dụng và giới hạn của những kỹ thuật trên trong Java 17+.

## 1. Vì sao thứ tự có thể thay đổi kết quả?

`float` và `double` sử dụng dấu phẩy động nhị phân IEEE 754:

| Kiểu | Bit phần trị chính xác (kể cả bit ẩn) | Khoảng số nguyên liên tiếp được biểu diễn chính xác |
| --- | ---: | --- |
| `float` | 24 | Từ `-2^24` đến `2^24` |
| `double` | 53 | Từ `-2^53` đến `2^53` |

Khi một kết quả chính xác nằm giữa hai số biểu diễn được, máy phải làm tròn. Ở nhiều phép toán cơ bản, Java áp dụng làm tròn tới giá trị gần nhất và khi bằng khoảng cách thì chọn giá trị có bit cuối chẵn (*round to nearest, ties to even*).

Khoảng cách giữa các giá trị `double` liên tiếp thay đổi theo độ lớn. Chẳng hạn:

```java
double x = 1e16;
System.out.println(Math.ulp(x));   // 2.0
System.out.println(x + 1.0 == x);  // true
System.out.println(x + 2.0 == x);  // false
```

`Math.ulp(x)` (*unit in the last place*) cho biết bước độ phân giải tại `x`. Quanh `1e16`, số 1 nhỏ hơn một bước giữa hai giá trị biểu diễn lân cận.

### Phép cộng không có tính kết hợp như số thực lý tưởng

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

double first = (a + b) + c;  // 1.0
double second = a + (b + c); // 0.0
```

Phép `a+b` triệt tiêu thành 0 rồi cộng 1 không mất thông tin; nhưng `b+c` bị làm tròn thành `b` trước. Do đó `(a+b)+c` không nhất thiết bằng `a+(b+c)` trong số học dấu phẩy động.

Phép nhân cũng có thể không kết hợp khi xuất hiện rounding, overflow hoặc underflow. Việc thay đổi dấu ngoặc có thể làm kết quả tốt hơn **hoặc** xấu đi.

### Java 17 always-strict không sửa được tính không kết hợp

Từ Java 17, Java có ngữ nghĩa *always-strict* cho biểu thức dấu phẩy động. Điều này quy định cách đánh giá chặt chẽ hơn, nhưng **không tăng vô hạn độ chính xác** và không biến `+`, `*` thành các phép kết hợp chính xác như số thực toán học. Đừng dùng `strictfp` để kỳ vọng mất sai số tự biến mất.

## 2. Khi tổng lớn nuốt mất số hạng nhỏ

Giả sử có dữ liệu:

```java
double[] values = {1e16, 1.0, 1.0, -1e16};
```

Tổng toán học là **2**. Nhưng nếu cộng tuần tự từ trái sang phải:

```java
double sum = 0.0;
for (double value : values) {
    sum += value;
}
System.out.println(sum); // 0.0
```

Diễn biến:

```text
0                  ban đầu
1e16               cộng số rất lớn
1e16               cộng 1.0 nhưng bị làm tròn mất
1e16               cộng 1.0 lần nữa vẫn mất
0                  cộng -1e16 và triệt tiêu
```

Hai số 1 không thể xuất hiện lại ở bước cuối vì thông tin đã bị mất tại hai phép cộng trung gian.

### Ghép nhóm theo ý nghĩa số học

Trong ví dụ này, nếu phép toán được phép đổi thứ tự, ta có thể nhóm hai số lớn để triệt tiêu và hai số nhỏ để cộng:

```java
double better = (values[0] + values[3])
              + (values[1] + values[2]);
System.out.println(better); // 2.0
```

Hoặc cộng hai số nhỏ trước:

```java
double smallFirst = 1.0 + 1.0 + 1e16 - 1e16;
System.out.println(smallFirst); // 2.0
```

Đây là hai ví dụ **cải thiện được kết quả của đúng bộ dữ liệu này**, không phải quy tắc “mọi phép cộng đều phải sắp xếp tăng theo trị tuyệt đối”. Với số hạng cả âm và dương, sự triệt tiêu có thể làm một cách sắp xếp khác tốt hơn. Nếu dữ liệu biểu diễn chuỗi trạng thái phụ thuộc thời gian, đôi khi thứ tự không thể tự ý đổi.

## 3. Ba chiến lược cộng nhiều số

| Phương pháp | Cách làm | Lợi ích | Giới hạn |
| --- | --- | --- | --- |
| Cộng tuần tự (*naive summation*) | Lặp `sum += x` | Ít chi phí, dễ hiểu | Các số hạng nhỏ có thể bị mất |
| Cộng từng cặp (*pairwise summation*) | Tính tổng theo cây cân bằng | Thường giảm số bước làm tròn trên mỗi đường tính | Kết quả vẫn phụ thuộc cách chia nhóm |
| Cộng bù (*compensated summation*) | Giữ một phần sai số làm tròn trong biến bổ sung | Có thể bảo toàn những đóng góp nhỏ tốt hơn | Không luôn chính xác, phức tạp hơn |

### 3.1. Pairwise summation

Thay vì cộng một chuỗi dài từ trái sang phải, thuật toán pairwise phân đôi dãy, tính tổng từng nửa, sau đó cộng tổng hai nửa. Điều này giúp giảm chiều sâu cộng của mỗi phần tử từ mức tuyến tính về mức logarit trong cây cân bằng.

```text
x0 + x1 + x2 + x3 + x4 + x5 + x6 + x7

Cộng cặp:     s01    s23    s45    s67
Cộng nhóm:        s0123        s4567
Cộng cuối:               total
```

Pairwise thường hữu ích với dãy dài và có thể phối hợp song song. Tuy nhiên, hai cách chia nhóm có thể cho kết quả khác nhau; không được hứa mọi cây pairwise đều giảm sai số với mọi bộ dữ liệu có triệt tiêu mạnh.

### 3.2. Kahan summation

Thuật toán Kahan lưu `sum` và một biến `compensation` để bù sai số đã phát sinh trong phép cộng:

```java
static double kahanSum(double[] values) {
    double sum = 0.0;
    double compensation = 0.0;

    for (double value : values) {
        double corrected = value - compensation;
        double next = sum + corrected;
        compensation = (next - sum) - corrected;
        sum = next;
    }

    return sum;
}
```

Đây là phương thức cần đặt trong một class. Với `{1e16, 1, 1, -1e16}`, Kahan có thể khôi phục tổng **2.0** do lưu lại phần 1 bị làm tròn mất ở bước đầu và bù ở bước sau.

**Kahan không phải phép cộng chính xác tuyệt đối.** Trong một số dãy triệt tiêu bất lợi, chẳng hạn `{1e16, 1.0, -1e16}`, Kahan chuẩn vẫn có thể không trả 1.0. Khi cần chính xác hoặc độ ổn định cao hơn, cân nhắc Neumaier summation, thuật toán tổng bù nâng cao, số học đa độ chính xác hoặc một cách biểu diễn dữ liệu khác.

### 3.3. Cách chọn

Dùng phép cộng tuần tự khi dữ liệu nhỏ hoặc yêu cầu sai số cho phép. Với số đo lớn, số lượng phần tử lớn và sai số tổng đáng kể, thử pairwise/Kahan cùng bộ dữ liệu kiểm thử. Nếu phép tính là **số nguyên bắt buộc chính xác**, nên dùng `long`, `Math.addExact` hoặc `BigInteger` đúng miền; nếu là tiền cần thập phân chính xác, cân nhắc `BigDecimal`. Không biến vấn đề chính xác tuyệt đối thành bài toán “mẹo cộng double”.

## 4. Triệt tiêu số gần bằng nhau: catastrophic cancellation

Khi trừ hai số gần bằng nhau, kết quả nhỏ có thể chứa **rất ít chữ số có nghĩa đáng tin**, dù mỗi toán hạng nhìn riêng có nhiều chữ số.

Xét công thức:

```text
sqrt(1 + x) - 1
```

với `x = 1e-16`:

```java
double x = 1e-16;
double naive = Math.sqrt(1.0 + x) - 1.0;
System.out.println(naive); // 0.0
```

`1.0 + 1e-16` bị làm tròn thành `1.0`; sau đó căn bậc hai và trừ 1 cho zero. Đầu vào `x` đã bị mất trong khi giá trị toán học của biểu thức vẫn dương gần `5e-17`.

### 4.1. Biến đổi bằng liên hợp

Trong miền biểu thức xác định và mẫu số khác 0:

```text
sqrt(1 + x) - 1
= [sqrt(1 + x) - 1] * [sqrt(1 + x) + 1]
  / [sqrt(1 + x) + 1]
= x / [sqrt(1 + x) + 1]
```

Cách viết ổn định hơn quanh `x=0`:

```java
double x = 1e-16;
double stable = x / (Math.sqrt(1.0 + x) + 1.0);
System.out.println(stable); // 5.0E-17
```

Tử số giữ trực tiếp `x`, nên dù `1.0+x` được làm tròn thành 1.0, phép chia vẫn bảo toàn đóng góp nhỏ.

Điều này minh họa nguyên tắc: **tránh tính hiệu của hai đại lượng gần bằng nhau nếu có công thức tương đương ổn định hơn**. Tuy nhiên phải xét miền xác định: căn yêu cầu `1+x >= 0` trong số thực; với `x=-1` mẫu số vẫn là 1, còn khi các giá trị cực lớn hoặc đặc biệt như Infinity/NaN, cách đánh giá cần được kiểm tra riêng.

### 4.2. Không thể sửa phép tính sau khi dữ liệu đã bị làm tròn mất

Nếu hai giá trị toán học khác nhau đã được lưu thành **cùng một giá trị `double`**, phép trừ giữa chúng chỉ thấy hai giá trị lưu được. Ép kiểu sau phép trừ hoặc đảo hai toán hạng không khôi phục thông tin ban đầu.

Giải pháp phải xuất hiện **trước bước mất thông tin**: đổi công thức, giữ số hạng nhỏ riêng, sử dụng kiểu chính xác hơn hoặc làm việc trực tiếp với dữ liệu gốc.

## 5. Chọn Math.log1p và Math.expm1 khi x gần 0

Thư viện Java cung cấp hàm tránh mất thông tin trong hai biểu thức rất thường gặp.

### Math.log1p(x): log tự nhiên của 1+x

```java
double x = 1e-16;
double naive = Math.log(1.0 + x); // 0.0
double stable = Math.log1p(x);    // Số dương gần 1e-16
```

Khi `x` rất nhỏ, phép `1.0+x` có thể làm tròn về 1, khiến `Math.log(1.0)` bằng 0. `Math.log1p` được thiết kế cho `ln(1+x)` và giữ độ chính xác tốt hơn ở vùng đó.

### Math.expm1(x): exp(x)-1

```java
double x = 1e-16;
double naive = Math.exp(x) - 1.0; // 0.0
double stable = Math.expm1(x);    // Số dương gần 1e-16
```

Trong phép tính trực tiếp, `exp(x)` có thể làm tròn về 1 rồi phép trừ làm mất kết quả. `Math.expm1` tránh kiểu triệt tiêu này.

Hai hàm có ích trong xác suất, thống kê, mô hình tăng trưởng và một số công thức tài chính; nhưng chỉ thay thế khi **ý nghĩa toán học cần tính đúng là `ln(1+x)` hoặc `exp(x)-1`**.

## 6. Đổi nhóm nhân/chia để tránh overflow và underflow trung gian

Một biểu thức có kết quả toán học trong miền `double` vẫn có thể thất bại nếu **kết quả trung gian** vượt miền.

### 6.1. Tràn thành Infinity quá sớm

```java
double x = Double.MAX_VALUE;

double overflowFirst = (x * 2.0) / 2.0; // Infinity
double scaleFirst = x * (2.0 / 2.0);    // Double.MAX_VALUE
```

Trong toán học cả hai bằng `x`. Nhưng phép `x*2` ở vế đầu tràn thành vô cực; chia vô cực cho 2 không đưa nó về hữu hạn. Phép tính ở vế sau tạo tỷ lệ 1 trước nên không tràn.

### 6.2. Underflow về zero quá sớm

```java
double tiny = 1e-300;

double underflowFirst = (tiny * tiny) / tiny; // 0.0
double ratioFirst = tiny * (tiny / tiny);     // 1.0E-300
```

`tiny*tiny` có độ lớn `1e-600`, quá nhỏ để lưu trong `double`; giá trị bị làm tròn thành 0. Tính tỷ lệ `tiny/tiny=1` trước tránh vấn đề cho bộ dữ liệu này.

**Chú ý:** không phải cứ “chia trước” là tốt. Với mẫu số có thể bằng zero, rất nhỏ, rất lớn hoặc chứa NaN/Infinity, đổi thứ tự có thể gây lỗi mới. Cần xác minh miền xác định, dấu, giới hạn và chính sách sai số.

### 6.3. Math.hypot tránh bình phương rồi mới lấy căn

Độ dài của vector 2D:

```text
sqrt(x² + y²)
```

Nếu tính trực tiếp:

```java
double x = 1e200;
double y = 1e200;
double naive = Math.sqrt(x * x + y * y); // Infinity
```

Vì `x*x` đã vượt miền hữu hạn của `double`, kết quả là vô cực, dù độ dài thực khoảng `1.414e200` vẫn hữu hạn.

`Math.hypot(x, y)` dùng cách tính ổn định hơn cho căn tổng bình phương, tránh overflow/underflow trung gian không cần thiết:

```java
double better = Math.hypot(1e200, 1e200);
System.out.println(Double.isFinite(better)); // true
```

`Math.hypot` không bảo đảm mọi đầu vào có kết quả hữu hạn: nếu **độ dài toán học** vượt miền biểu diễn, kết quả vẫn có thể vô cực.

## 7. Math.fma: giảm một lần làm tròn ở tích cộng

`Math.fma(a, b, c)` tính `a*b+c` bằng phép *fused multiply-add*, với một lần làm tròn ở cuối. Trong khi đó, biểu thức `a*b+c` có thể làm tròn sau nhân và làm tròn tiếp sau cộng.

```java
double u = Math.ulp(1.0);
double a = 1.0 + u;
double b = 1.0 - u;

double separate = a * b - 1.0;     // 0.0
double fused = Math.fma(a, b, -1.0); // Một số âm rất nhỏ.
```

Tích chính xác là `(1+u)(1-u)=1-u²`. Phép nhân riêng làm tròn về 1, rồi trừ 1 thành 0. FMA giữ giá trị trước khi làm tròn cuối và có thể cho kết quả âm khác 0.

FMA hữu ích ở một số thuật toán vector, đa thức và cộng dồn tích, nhưng **không có nghĩa mọi phép tính đều chính xác tuyệt đối**. Vì nó cho kết quả khác nhân/cộng tách rời, không tự ý thay nếu hệ thống cần tính tương thích bit-for-bit.

## 8. Tính toán có thứ tự khác vẫn phải đúng về kiểu và miền

Thứ tự phép toán không chỉ là vấn đề số thực; một phép tính nguyên có thể **tràn trước khi chuyển sang `double`**:

```java
int count = 1_500_000_000;

double wrong = count * 3;          // Tích int tràn trước khi gán.
double floating = (double) count * 3; // Nhân double, không tràn int.
long exact = (long) count * 3;     // 4_500_000_000L, nguyên chính xác.
```

Nếu cần kết quả **số nguyên chính xác**, phương án `long` phù hợp hơn khi nó đủ miền, hoặc có thể dùng `Math.multiplyExact`/`BigInteger` khi cần kiểm tra. Chuyển sang `double` giúp tránh tràn ở ví dụ này nhưng có thể đánh mất các đơn vị nhỏ của những số nguyên lớn hơn `2^53`.

Cũng cần xét các phép chia: `int/int` bỏ phần lẻ trước khi gán vào `double`; ép sang `double` ở **một toán hạng trước phép chia** nếu muốn tính tỷ lệ gần đúng.

## 9. Đo sai số thay vì đánh giá kết quả bằng mắt

Một cách sắp xếp chỉ nên được gọi là “tốt hơn” khi có **giá trị tham chiếu** hoặc tiêu chí sai số.

Hai thước đo phổ biến:

```text
sai_số_tuyệt_đối = |kết_quả - tham_chiếu|
sai_số_tương_đối = sai_số_tuyệt_đối / |tham_chiếu|
```

Sai số tương đối không dùng trực tiếp khi tham chiếu bằng 0. Khi kết quả thật gần 0, cần ngưỡng tuyệt đối có đơn vị, phân tích độ nhạy hoặc quy tắc miền rõ ràng.

Ví dụ tổng của `{1e16,1,1,-1e16}` có tham chiếu toán học chính xác bằng 2. Phép cộng tuần tự cho 0 nên sai số tuyệt đối bằng 2; ghép nhóm và Kahan cho 2 nên sai số bằng 0 **đối với chính dữ liệu này**.

Phân biệt:

- **Điều kiện bài toán** (*conditioning*): đầu vào thay đổi rất ít có thể làm kết quả toán học thay đổi tương đối nhiều; đặc biệt với hiệu hai số gần bằng nhau.
- **Độ ổn định thuật toán** (*numerical stability*): thuật toán có khuếch đại sai số không cần thiết do thứ tự phép toán và biểu diễn trung gian không?

Một thuật toán ổn định hơn không thể tự loại bỏ sai số vốn đã có trong dữ liệu đo. Cần biết độ chính xác nguồn và sai số được chấp nhận trước khi thay đổi công thức.

### Checklist đánh giá một phép biến đổi

1. Xác định ý nghĩa dữ liệu: số đo gần đúng, tổng nguyên chính xác, tiền tệ hay chỉ số?
2. Đọc kiểu của **từng biểu thức con**: phép nào đã tràn hoặc làm tròn trước khi gán?
3. Xem có cộng nhiều số rất nhỏ vào tổng lớn hay không; có thể thay bằng ghép nhóm, pairwise hoặc compensated summation?
4. Xem có trừ hai giá trị gần bằng nhau hay không; có công thức tương đương tránh triệt tiêu?
5. Với nhân chia, kiểm tra overflow/underflow trung gian và miền mẫu số.
6. Dùng `Math.log1p`, `Math.expm1`, `Math.hypot`, `Math.fma` khi đúng mục đích và điều kiện áp dụng.
7. Dùng test có kết quả chuẩn và đánh giá sai số tuyệt đối/tương đối có đơn vị.
8. Thử các trường hợp số dương/âm, triệt tiêu mạnh, zero, số rất lớn/rất nhỏ, NaN và Infinity.
9. Kiểm soát thứ tự tính toán trong tác vụ song song nếu cần tái lập kết quả bit-for-bit.
10. Không giả định một phép đổi dấu ngoặc hợp lý trên dữ liệu mẫu luôn tốt trên mọi miền.

## 10. Chương trình Java 17+ hoàn chỉnh

Tạo file **`CalculationOrderDemo.java`**:

```java
public class CalculationOrderDemo {
    static double naiveSum(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    static double kahanSum(double[] values) {
        double sum = 0.0;
        double compensation = 0.0;
        for (double value : values) {
            double corrected = value - compensation;
            double next = sum + corrected;
            compensation = (next - sum) - corrected;
            sum = next;
        }
        return sum;
    }

    public static void main(String[] args) {
        double large = 1e16;

        System.out.println("lost=" + ((large + 1.0) - large));
        System.out.println("kept=" + ((large - large) + 1.0));
        System.out.println("ulp=" + Math.ulp(large));

        double[] values = {1e16, 1.0, 1.0, -1e16};
        System.out.println("naive-sum=" + naiveSum(values));
        System.out.println("grouped-sum=" +
                ((values[0] + values[3]) + (values[1] + values[2])));
        System.out.println("kahan-sum=" + kahanSum(values));

        double max = Double.MAX_VALUE;
        System.out.println("overflow-first=" +
                Double.isInfinite((max * 2.0) / 2.0));
        System.out.println("scale-first=" +
                (max * (2.0 / 2.0) == max));

        double tiny = 1e-300;
        System.out.println("underflow-first=" +
                ((tiny * tiny) / tiny));
        System.out.println("ratio-first=" +
                (tiny * (tiny / tiny)));

        double x = 1e-16;
        System.out.println("root-naive=" +
                (Math.sqrt(1.0 + x) - 1.0));
        System.out.println("root-stable=" +
                (x / (Math.sqrt(1.0 + x) + 1.0)));
        System.out.println("log-naive=" + Math.log(1.0 + x));
        System.out.println("log1p-positive=" + (Math.log1p(x) > 0.0));
        System.out.println("expm1-positive=" + (Math.expm1(x) > 0.0));

        double huge = 1e200;
        double naiveHypot = Math.sqrt(huge * huge + huge * huge);
        System.out.println("hypot-naive-infinite=" +
                Double.isInfinite(naiveHypot));
        System.out.println("hypot-stable-finite=" +
                Double.isFinite(Math.hypot(huge, huge)));

        double ulp = Math.ulp(1.0);
        double a = 1.0 + ulp;
        double b = 1.0 - ulp;
        System.out.println("fma-separate-zero=" +
                (a * b - 1.0 == 0.0));
        System.out.println("fma-negative=" +
                (Math.fma(a, b, -1.0) < 0.0));
    }
}
```

Biên dịch và chạy:

```bash
javac CalculationOrderDemo.java
java CalculationOrderDemo
```

Stdout:

```text
lost=0.0
kept=1.0
ulp=2.0
naive-sum=0.0
grouped-sum=2.0
kahan-sum=2.0
overflow-first=true
scale-first=true
underflow-first=0.0
ratio-first=1.0E-300
root-naive=0.0
root-stable=5.0E-17
log-naive=0.0
log1p-positive=true
expm1-positive=true
hypot-naive-infinite=true
hypot-stable-finite=true
fma-separate-zero=true
fma-negative=true
```

**Cách đọc kết quả:** `lost`/`kept` và ba cách cộng cho thấy dữ liệu nhỏ có thể bị mất hoặc được giữ; `overflow-first`/`underflow-first` cho thấy giá trị trung gian quyết định kết quả; `root-stable`, `log1p-positive` và `expm1-positive` cho thấy công thức/hàm ổn định hơn gần 0; `Math.hypot` tránh overflow của bình phương; hai dòng FMA cho thấy giảm một lần làm tròn có thể làm kết quả khác nhân/trừ riêng.

## 11. Những sai lầm thường gặp

| Nhầm lẫn | Tác hại | Cách xử lý |
| --- | --- | --- |
| Coi `(a+b)+c` luôn bằng `a+(b+c)` | Mất số hạng nhỏ hoặc khác kết quả | Phân tích ULP và thử dữ liệu biên |
| Cộng tất cả số nhỏ sau khi tổng đã rất lớn | Đóng góp nhỏ có thể bị nuốt | Thử ghép nhóm, pairwise, Kahan |
| Khẳng định luôn phải cộng theo trị tuyệt đối tăng dần | Không đúng cho mọi dãy có dấu; có thể đổi semantics | Chỉ đổi thứ tự khi miền cho phép, đo sai số |
| Coi Kahan luôn exact | Có trường hợp triệt tiêu mà Kahan vẫn sai | Kiểm thử, cân nhắc Neumaier/độ chính xác cao |
| Lấy hiệu hai số gần bằng nhau mà không xét sai số | Mất nhiều chữ số có nghĩa | Dùng công thức tránh triệt tiêu, chẳng hạn liên hợp |
| Tính `log(1+x)` hoặc `exp(x)-1` khi x quá nhỏ | Kết quả có thể về zero | Dùng `log1p` và `expm1` đúng mục đích |
| Tính `sqrt(x*x+y*y)` với x,y lớn | Bình phương có thể vô cực | Dùng `Math.hypot` |
| Nghĩ chia trước luôn an toàn | Có thể chia cho 0 hoặc sai ở miền khác | Chứng minh điều kiện và kiểm thử |
| Nghĩ `Math.fma` và `a*b+c` phải luôn cùng bit | Khác số lần làm tròn | Chọn theo thuật toán, xem tính tương thích |
| Ép kiểu sau phép nhân nguyên đã tràn | Giá trị đã mất trước khi cast | Nâng kiểu trước, dùng checked arithmetic |
| Dùng `double` cho số lượng/tiền phải chính xác | Không bảo đảm giữ mọi đơn vị | Dùng kiểu nguyên hoặc thập phân chính xác |
| Chỉ nhìn vài dòng output có vẻ hợp lý | Không thấy ca gần giới hạn và triệt tiêu | Có kết quả tham chiếu và chỉ tiêu sai số |

## 12. Bài tập tự luyện

**Bài 1 – Thứ tự và ULP.** Tính `Math.ulp(1e16)`; dự đoán `(1e16+1)-1e16` và `(1e16-1e16)+1`. Vì sao hai đáp án khác nhau?

**Bài 2 – Tổng bốn số.** Với `{1e16, 1.0, 1.0, -1e16}`, tính tổng toán học, tổng tuần tự, tổng ghép nhóm và tổng Kahan. Hoán vị dữ liệu và quan sát thay đổi.

**Bài 3 – Phản ví dụ cho Kahan.** Thử `{1e16, 1.0, -1e16}`. Có thể chỉ dùng Kahan mà bảo đảm tổng chính xác 1.0 không? Hãy giải thích.

**Bài 4 – Triệt tiêu gần zero.** Chứng minh bằng liên hợp `sqrt(1+x)-1 = x/(sqrt(1+x)+1)`. Kiểm thử `x=1e-16`; nêu miền xác định.

**Bài 5 – Hàm thư viện ổn định.** Dùng `Math.log1p` và `Math.expm1` để tính những biểu thức tương đương với `log(1+x)` và `exp(x)-1` khi x gần 0. So sai số với kết quả độ chính xác cao.

**Bài 6 – Overflow/underflow trung gian.** Giải thích sự khác biệt của `(Double.MAX_VALUE*2)/2` và `Double.MAX_VALUE*(2/2)` với các literal kiểu double. Làm tương tự cho `1e-300` nhân/chia.

**Bài 7 – Độ dài vector.** So `Math.sqrt(x*x+y*y)` với `Math.hypot(x,y)` khi `x=y=1e200` và khi `x,y` rất nhỏ. Kết quả toán học có nằm trong miền double không?

**Bài 8 – FMA và tái lập kết quả.** Viết test cho `Math.fma(a,b,-1)` với `a=1+ulp(1)`, `b=1-ulp(1)`. Vì sao dùng FMA có thể thay đổi kết quả bitwise?

**Bài 9 – Thiết kế thực tế.** Một hệ thống tổng hợp hàng triệu phép đo trên các worker song song cần kết quả ổn định và lặp lại. Bạn sẽ cố định cây reduction, chọn thuật toán tổng nào, xử lý NaN/Infinity ra sao và đo sai số như thế nào?

## 13. Ghi nhớ

- Phép cộng và nhân dấu phẩy động **không kết hợp tổng quát**; thay đổi thứ tự có thể ảnh hưởng kết quả.
- Số hạng quá nhỏ so với tổng đang có có thể bị làm tròn mất; ghép nhóm, pairwise hoặc Kahan có thể cải thiện nhưng không bảo đảm đúng tuyệt đối.
- Trừ hai số gần bằng nhau có thể mất chữ số có nghĩa; ưu tiên biến đổi công thức để tránh triệt tiêu khi có thể.
- `Math.log1p`, `Math.expm1`, `Math.hypot` giúp giải quyết các dạng mất thông tin trung gian thường gặp.
- Nhân/chia theo thứ tự không phù hợp có thể overflow thành Infinity hoặc underflow về zero trước khi kết quả cuối được tính.
- `Math.fma` khác biểu thức nhân cộng riêng vì chỉ làm tròn một lần.
- Chọn kiểu đủ chính xác cho miền dữ liệu; **không có mẹo đổi thứ tự nào thay thế được `BigInteger`, `BigDecimal` hoặc checked arithmetic khi nghiệp vụ yêu cầu chính xác**.
- Mọi tối ưu độ ổn định nên có **kết quả chuẩn, kiểm thử biên và tiêu chí sai số**.

**Kết luận:** thay đổi thứ tự phép tính có thể giảm mất thông tin trong Java, nhưng chỉ khi đã hiểu nguyên nhân sai số và miền dữ liệu. Hãy chọn một công thức/thuật toán ổn định hơn dựa trên kiểm chứng, thay vì chỉ đổi dấu ngoặc theo thói quen.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-005">Tổng bù và tư tưởng Kahan summation</a>

<details>
<summary>Xem chi tiết</summary>

# Tổng bù và tư tưởng Kahan summation

Khi cộng nhiều số thực trong Java bằng `double`, vòng lặp `sum += x` có thể tích lũy sai số làm tròn. Đặc biệt, một số nhỏ cộng vào tổng đang rất lớn có thể **không làm tổng thay đổi**, dù số nhỏ đó có ý nghĩa thực tế. **Kahan summation** (*compensated summation*, thuật toán cộng có bù) dùng thêm một biến để ghi nhận một phần sai lệch trong các phép cộng, rồi bù vào các lần cộng tiếp theo.

Ví dụ:

```java
double sum = 0.0;
for (int i = 0; i < 10; i++) {
    sum += 0.1;
}
System.out.println(sum); // 0.9999999999999999
```

Với cùng các phần tử, phiên bản Kahan trong bài cho kết quả `1.0`. Nhưng **Kahan không biến `double` thành số học chính xác tuyệt đối**: nó giảm một loại sai số khi cộng, không sửa được mọi sai số đầu vào hoặc mọi tình huống triệt tiêu.

## 1. Nguồn gốc của sai số khi tính tổng

`double` là kiểu dấu phẩy động nhị phân 64 bit với **53 bit độ chính xác phần trị hiệu dụng**. Không phải mọi số thập phân đều biểu diễn được bằng nhị phân hữu hạn: `0.1` chỉ được lưu thành một giá trị gần đúng. Sau đó mỗi phép cộng trên `double` còn có thể làm tròn kết quả sang số gần nhất biểu diễn được.

Có hai nguồn sai số cần phân biệt:

1. **Sai số biểu diễn:** giá trị nhập như `0.1` đã được chuyển sang một số nhị phân gần đúng. Kahan không khôi phục chính xác ý nghĩa thập phân gốc.
2. **Sai số phép cộng:** một số bit thấp của kết quả toán học có thể bị mất sau mỗi phép `+`. Kahan được thiết kế để hạn chế phần sai số tích lũy này.

Phép cộng số thực máy tính **không kết hợp tuyệt đối**:

```java
double a = 1e16;
double b = 1.0;
double c = -1e16;

double first = (a + b) + c;  // 0.0
double second = (a + c) + b; // 1.0
```

Về toán học, tổng đều bằng `1`. Nhưng ở độ lớn `1e16`, khoảng cách (ULP) giữa hai số `double` liên tiếp là **2**. Do đó `1e16 + 1.0` nằm giữa hai số có thể biểu diễn và được làm tròn về `1e16`. Nếu triệt tiêu hai số lớn trước, đóng góp `1.0` được giữ.

Một ví dụ thực tế hơn: cộng `10_000` giá trị `1.0` vào `1e16` bằng cách thường, từng số `1.0` có thể lần lượt bị bỏ qua do làm tròn. Kết quả chỉ còn `1e16` thay vì tổng toán học `1e16 + 10000`.

## 2. Ý tưởng: đừng vứt bỏ phần nhỏ sau mỗi phép cộng

Nếu `sum` rất lớn và `x` nhỏ, biểu thức `sum + x` có thể quay về chính `sum` vì độ phân giải của `double` tại đó không đủ. Kahan cố ghi lại **phần thay đổi mà phép cộng chưa đưa được vào tổng**, để hiệu chỉnh số hạng tiếp theo.

Thuật toán có hai biến trạng thái:

- `sum`: tổng xấp xỉ hiện tại.
- `compensation` (thường ký hiệu `c`): lượng bù có dấu theo dõi một phần mất mát do làm tròn. Đây **không phải sai số tuyệt đối thật** so với tổng toán học lý tưởng.

Với mỗi phần tử `x`, thực hiện đúng thứ tự:

```java
double y = x - compensation;
double t = sum + y;
compensation = (t - sum) - y;
sum = t;
```

Diễn giải:

1. `y = x - compensation`: điều chỉnh số hạng mới bằng lượng bù đã tích lũy.
2. `t = sum + y`: thực hiện phép cộng dấu phẩy động thông thường, vẫn có thể làm tròn.
3. `compensation = (t - sum) - y`: xác định phần khác biệt giữa lượng tăng được phản ánh và số hạng `y` muốn cộng.
4. `sum = t`: chấp nhận tổng mới, dùng cho lần lặp kế tiếp.

**Không cập nhật `sum` trước dòng tính `compensation`**. Dòng thứ ba cần giá trị `sum` trước khi đổi.

### Ví dụ bằng trực giác với `1e16` và các số `1`

Giả sử `sum = 1e16` và `compensation = 0`:

- Với `x = 1`, `y = 1`. Phép cộng `1e16 + 1` làm tròn về `1e16`, nên `t - sum = 0` và `compensation = -1`.
- Với số `x = 1` tiếp theo, `y = 1 - (-1) = 2`. Ở độ lớn `1e16`, bước tăng `2` có thể được biểu diễn, và phần đóng góp từ hai số `1` sẽ được phản ánh.
- Quá trình tiếp tục, thay vì bỏ từng số nhỏ như vòng lặp `sum += 1` thông thường.

Dấu âm của lượng bù trong ví dụ nghĩa là phần cộng bị thiếu sẽ được **cộng bổ sung** tại bước sau. Không nên xem `compensation` là một số luôn dương.

## 3. Hàm Kahan cơ bản trong Java 17+

```java
static double kahanSum(double[] values) {
    double sum = 0.0;
    double compensation = 0.0;

    for (double x : values) {
        double y = x - compensation;
        double t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
    }

    return sum;
}
```

Đây là một **phương thức minh họa** cần đặt trong class. Mảng rỗng cho `0.0` theo cách khởi tạo này. Phiên bản cơ bản giả định đầu vào và các kết quả trung gian **hữu hạn**; cách xử lý `NaN`, vô cực và overflow cần chính sách riêng.

Hàm đối chiếu dùng phép cộng thường:

```java
static double naiveSum(double[] values) {
    double sum = 0.0;
    for (double x : values) {
        sum += x;
    }
    return sum;
}
```

Cả hai hàm đều đi qua các phần tử một lần, có **độ phức tạp thời gian `O(n)`** và dùng **bộ nhớ phụ `O(1)`**. Kahan tốn thêm một số phép trừ/cộng trên mỗi phần tử, đổi lại có thể giảm đáng kể sai số tích lũy.

**Điều Kahan không đảm bảo:** một kết quả `double` có 53 bit phần trị hiệu dụng vẫn có thể không biểu diễn được tổng toán học. Ví dụ `1e16 + 1` không có biểu diễn `double` chính xác; dù thuật toán biết “phần bị mất”, một số `double` trả về không thể lưu toàn bộ thông tin của tổng này. Kahan đặc biệt hữu ích khi **nhiều** phần nhỏ có thể được tích lũy và bù dần.

## 4. Chương trình Java hoàn chỉnh: so sánh cộng thường, Kahan và Neumaier

Lưu tệp `KahanDemo.java`:

```java
public class KahanDemo {
    static double naive(double[] values) {
        double sum = 0.0;
        for (double x : values) {
            sum += x;
        }
        return sum;
    }

    static double kahan(double[] values) {
        double sum = 0.0;
        double compensation = 0.0;

        for (double x : values) {
            double y = x - compensation;
            double t = sum + y;
            compensation = (t - sum) - y;
            sum = t;
        }
        return sum;
    }

    static double neumaier(double[] values) {
        double sum = 0.0;
        double correction = 0.0;

        for (double x : values) {
            double t = sum + x;
            if (Math.abs(sum) >= Math.abs(x)) {
                correction += (sum - t) + x;
            } else {
                correction += (x - t) + sum;
            }
            sum = t;
        }
        return sum + correction;
    }

    public static void main(String[] args) {
        double[] tenths = new double[10];
        java.util.Arrays.fill(tenths, 0.1);
        System.out.println("0.1 x 10 naive: " + naive(tenths));
        System.out.println("0.1 x 10 Kahan: " + kahan(tenths));

        double[] mixed = new double[10_001];
        mixed[0] = 1e16;
        java.util.Arrays.fill(mixed, 1, mixed.length, 1.0);
        System.out.println("large + ones naive extra: " + (naive(mixed) - 1e16));
        System.out.println("large + ones Kahan extra: " + (kahan(mixed) - 1e16));

        double[] cancel = {1e16, 1.0, -1e16};
        System.out.println("cancellation naive: " + naive(cancel));
        System.out.println("cancellation Kahan: " + kahan(cancel));
        System.out.println("cancellation Neumaier: " + neumaier(cancel));

        double[] finite = {1.0, 2.0, 3.0};
        System.out.println("simple Kahan: " + kahan(finite));
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac KahanDemo.java
java KahanDemo
```

Stdout:

```text
0.1 x 10 naive: 0.9999999999999999
0.1 x 10 Kahan: 1.0
large + ones naive extra: 0.0
large + ones Kahan extra: 10000.0
cancellation naive: 0.0
cancellation Kahan: 0.0
cancellation Neumaier: 1.0
simple Kahan: 6.0
```

Kết quả cho thấy:

- **Mười lần cộng `0.1`:** bản thường in giá trị hơi nhỏ hơn 1.0, Kahan in 1.0.
- **Một tổng `1e16` rồi thêm 10.000 số `1`:** bản thường không giữ được phần tăng; Kahan giữ lại đủ `10000.0` trong thử nghiệm.
- **Dãy `[1e16, 1, -1e16]`:** tổng toán học là 1, nhưng cả cộng thường lẫn Kahan cơ bản cho 0.0. Phiên bản Neumaier cho 1.0.
- **Dãy đơn giản `[1,2,3]`:** phép cộng không gặp vấn đề và Kahan cũng trả tổng đúng.

Không nên kết luận rằng Kahan luôn cho nhiều chữ số chính xác hơn trong mọi ví dụ chỉ vì những trường hợp đầu thành công.

## 5. Giới hạn quan trọng: triệt tiêu số lớn có thể đánh bại Kahan

Trường hợp `[1e16, 1, -1e16]` có tổng toán học 1.0, nhưng Kahan cơ bản trả 0.0. Nguyên nhân nằm ở cách cập nhật phần bù: khi số hạng `-1e16` xuất hiện, **bản thân phép điều chỉnh `y = x - compensation`** có thể làm tròn và mất thông tin của `1`. Sau đó hai số lớn triệt tiêu, nhưng phần nhỏ không còn nằm trong tổng.

Đây là lý do Kahan không phải một “thuật toán tổng chính xác” nói chung. Sai số với dãy **đổi dấu, triệt tiêu mạnh hoặc có độ lớn thay đổi đột ngột** có thể phức tạp hơn các trường hợp chỉ cộng số dương.

Một phương pháp liên quan là **Neumaier summation**, sử dụng biến `correction` để tích lũy lượng bù riêng ngoài tổng:

```java
static double neumaierSum(double[] values) {
    double sum = 0.0;
    double correction = 0.0;

    for (double x : values) {
        double t = sum + x;
        if (Math.abs(sum) >= Math.abs(x)) {
            correction += (sum - t) + x;
        } else {
            correction += (x - t) + sum;
        }
        sum = t;
    }

    return sum + correction;
}
```

Neumaier kiểm tra độ lớn của hai toán hạng để chọn cách ước lượng lượng mất do cộng, rồi **cộng phần hiệu chỉnh khi trả kết quả**. Điều này giúp trong một số trường hợp triệt tiêu mà Kahan cơ bản thất bại. Tuy vậy, `correction` vẫn là `double`, cũng có thể làm tròn; **Neumaier không luôn đạt tổng toán học chính xác**.

Sự khác biệt có thể tóm tắt:

| Phương pháp | Cách giữ bù | Điểm mạnh | Hạn chế |
| --- | --- | --- | --- |
| `sum += x` | Không có biến bù | Đơn giản, nhanh | Dễ mất số nhỏ |
| Kahan | Điều chỉnh số hạng kế tiếp bằng `compensation` | Giảm sai số tích lũy trên nhiều dãy | Có dãy triệt tiêu khó vẫn sai |
| Neumaier | Tích lũy `correction` riêng, cuối cùng cộng vào tổng | Có thể xử lý một số trường hợp triệt tiêu tốt hơn | Cũng bị giới hạn bởi `double` và thứ tự số học |

## 6. Sai số, độ ổn định và thứ tự cộng

Tính chất cơ bản của tổng số thực máy tính là **thứ tự ảnh hưởng kết quả**. Xét `[1e16, 1, -1e16]`: thay đổi thứ tự thành `[1e16, -1e16, 1]` có thể cho kết quả đúng 1.0 ngay với cộng thường. Nhưng không nên tự ý sắp xếp lại đầu vào nếu thứ tự mang ý nghĩa nghiệp vụ, hoặc cho rằng có một thứ tự tối ưu cho mọi dãy.

Về mặt thuật toán, cần phân biệt:

- **Sai số tuyệt đối:** `|computed - reference|` khi biết giá trị tham chiếu đủ chính xác.
- **Sai số tương đối:** sai số tuyệt đối chia cho độ lớn tổng tham chiếu, thường không có ý nghĩa tốt nếu tổng thật gần 0.
- **Mức độ điều kiện của bài toán:** tổng của nhiều số lớn với dấu trái chiều có thể gần 0; chỉ một thay đổi rất nhỏ ở dữ liệu đầu vào cũng khiến sai số tương đối tăng mạnh.
- **Sai số của phép tính:** phần phát sinh thêm khi thuật toán dùng các phép dấu phẩy động, là điều Kahan cố hạn chế.

Có thể chọn Kahan để **ổn định hơn** trong một miền dữ liệu mà vẫn không có bảo đảm “đúng tuyệt đối”. Nếu đầu vào đã bị làm tròn từ phép đo hoặc khi chuyển từ số nguyên lớn sang `double`, tổng bù không thể khôi phục chính xác giá trị đầu vào ban đầu.

### Hiệu năng và khả năng tái lập

Cộng Kahan tuần tự dùng **`O(n)` thời gian**, **`O(1)` bộ nhớ phụ**. Nó làm nhiều phép toán hơn cộng thường và trạng thái bù phụ thuộc **thứ tự**. Vì vậy không được cho nhiều luồng tùy ý cập nhật chung `sum`/`compensation`: ngoài data race, nó còn phá trình tự tính bù.

Trong parallel reduction, có thể tính các tổng con riêng rồi kết hợp có kiểm soát. Tuy nhiên, kết quả cuối có thể khác bit so với phép cộng tuần tự do thứ tự nhóm phép cộng khác. Nếu cần tính tái lập, hãy quy định rõ thứ tự/chiến lược reduction, và kiểm thử với chính thuật toán đó.

## 7. Khi nào không nên dùng Kahan làm giải pháp duy nhất?

### `NaN`, vô cực và tràn số

Công thức bù có phép trừ `t - sum`. Nếu `sum` hoặc `t` trở thành vô cực, phép `Infinity - Infinity` có thể tạo NaN và sau đó làm `compensation` hoặc kết quả cuối thành NaN. Kahan **không có nhiệm vụ** xử lý ngoại lệ dữ liệu số thực.

Với ứng dụng chỉ chấp nhận số hữu hạn:

```java
for (double x : values) {
    if (!Double.isFinite(x)) {
        throw new IllegalArgumentException("non-finite input");
    }
}
```

Đây là mảnh mã trong phương thức. Kiểm tra đầu vào hữu hạn chưa bảo đảm các **phép cộng trung gian không overflow**; cần đánh giá miền dữ liệu và kiểm tra kết quả theo yêu cầu hệ thống. Không biến NaN thành 0 nếu điều đó không được chính sách dữ liệu cho phép.

### Số thập phân chính xác: dùng biểu diễn phù hợp

Nếu bài toán yêu cầu cộng chính xác các số thập phân từ chuỗi (ví dụ tiền tệ), cân nhắc `BigDecimal`:

```java
java.math.BigDecimal total = java.math.BigDecimal.ZERO;
for (int i = 0; i < 10; i++) {
    total = total.add(new java.math.BigDecimal("0.1"));
}
System.out.println(total); // 1.0
```

Đây là **mảnh mã minh họa**, cần đặt trong phương thức. `new BigDecimal("0.1")` chứa giá trị thập phân một phần mười đúng theo chuỗi. **`new BigDecimal(0.1)` không tương đương** vì constructor nhận `double` phản ánh giá trị nhị phân gần `0.1` đã được lưu; nếu nguồn là chuỗi thập phân thì hãy parse trực tiếp từ chuỗi để giữ ý nghĩa nguồn.

Với số lượng nguyên, có thể dùng `long`, `BigInteger` hoặc đơn vị nguyên phù hợp, và kiểm tra overflow. Dùng đúng kiểu dữ liệu từ đầu có thể đơn giản hơn việc cố bù sai số của `double`.

### Tổng chính xác hơn bằng thuật toán khác

Ngoài Neumaier, **pairwise summation** chia dãy thành các nhóm rồi cộng các tổng con theo cây cân bằng; nó thường cải thiện sai số so với cộng tuần tự, đặc biệt với dãy dài, và phù hợp hơn với một số mô hình chia để trị. Nhưng kết quả vẫn phụ thuộc cách nhóm và không chính xác tuyệt đối.

Có thể cân nhắc thư viện/hệ thống có **accumulator mở rộng độ chính xác** nếu miền yêu cầu rất chặt chẽ; thuật toán tổng bù cơ bản không đảm bảo mọi mẫu bit đầu vào được cộng chính xác trước khi làm tròn kết quả cuối cùng.

## 8. Dùng trong tổng thống kê và trung bình

Nếu tính trung bình của dãy `n > 0`, viết:

```java
double mean = kahanSum(values) / values.length;
```

Dòng này cải thiện khâu tính tổng so với vòng lặp thường trong những dãy phù hợp. Nhưng phép chia cuối vẫn tạo số `double` làm tròn. Nếu tổng trung gian có thể overflow thành vô cực dù trung bình nằm trong phạm vi, nên dùng cách tính trung bình được thiết kế để tránh overflow trung gian; Kahan cơ bản không giải quyết trường hợp đó.

Trong dữ liệu cảm biến, thống kê mô phỏng, cộng thời gian hoặc tổng các đại lượng có độ lớn thay đổi, Kahan có thể là lựa chọn tốt nhờ bộ nhớ phụ nhỏ. Tuy nhiên, cần phân biệt **độ chính xác số học** với **độ tin cậy của phép đo**: thuật toán không xóa lỗi cảm biến, NaN hoặc sai số do dữ liệu nhập.

### Kế hoạch kiểm thử hữu ích

Đánh giá thuật toán trên những dãy có ý nghĩa rõ ràng:

1. Dãy ngắn chính xác: `[1, 2, 3]` → 6.
2. Dãy thập phân: mười số `0.1`.
3. Chênh lệch lớn: `1e16` cộng nhiều số `1.0`.
4. Triệt tiêu mạnh: `[1e16, 1, -1e16]`.
5. Dãy âm/dương, dãy dài, dữ liệu sắp xếp theo các thứ tự khác nhau.
6. Dữ liệu có NaN, Infinity và giá trị rất gần biên (nếu là đầu vào có thể xảy ra).

Khi có thể, sử dụng tổng tham chiếu bằng số học chính xác hoặc độ chính xác cao từ **dữ liệu nguồn đáng tin**, rồi đo sai số tuyệt đối và tương đối. Đừng mặc định một thuật toán đúng chỉ vì nó trùng kết quả thuật toán khác.

## 9. Những nhầm lẫn thường gặp

1. **“`double` luôn cộng chính xác các số nguyên nhỏ.”** Sai: dù từng số `1` biểu diễn được chính xác, `1e16 + 1` vẫn bị làm tròn.
2. **“Kahan khiến `0.1` có biểu diễn thập phân chính xác.”** Sai: nó không đổi bit của giá trị đầu vào.
3. **“Chỉ cần thêm biến `compensation` thì tổng mọi dãy đều đúng.”** Sai: `[1e16, 1, -1e16]` vẫn cho 0.0 với Kahan cơ bản.
4. **“`compensation` chính là tổng sai số tuyệt đối.”** Sai: đó là trạng thái hiệu chỉnh có dấu; không phải giá trị tham chiếu toán học.
5. **“Có thể cập nhật `sum` trước khi tính `compensation`.”** Sai: công thức cần tổng cũ.
6. **“Kahan không phụ thuộc thứ tự cộng.”** Sai: các phép tính vẫn có làm tròn và kết quả có thể phụ thuộc thứ tự.
7. **“Neumaier luôn chính xác tuyệt đối.”** Sai: vẫn có các phép `double` và sai số.
8. **“`BigDecimal` tạo từ `double` bất kỳ sẽ phục hồi các chữ số đã mất.”** Sai: nguồn đã làm tròn không thể tự đảo ngược.
9. **“Kahan phù hợp để âm thầm bỏ qua NaN và vô cực.”** Sai: cần chính sách xử lý đầu vào rõ ràng.
10. **“Kahan có thể cập nhật chung `sum` từ nhiều luồng mà vẫn ổn.”** Sai: phụ thuộc trình tự bù và có nguy cơ data race.
11. **“Khi in ra `1.0`, ta đã chứng minh mọi bước tính chính xác.”** Sai: đó chỉ là một giá trị biểu diễn cuối cùng.
12. **“Độ chính xác của tổng và độ chính xác của dữ liệu cảm biến là cùng một thứ.”** Sai: thuật toán cộng không sửa được sai số đo ban đầu.

## 10. Câu hỏi tự kiểm tra

**1. Vì sao `1e16 + 1` có thể bằng `1e16` trong `double`?** Vì ULP gần `1e16` là 2, nên phần tăng 1 có thể bị làm tròn mất.

**2. Vai trò của `compensation` là gì?** Lưu một phần sai lệch do phép cộng đã làm tròn, để điều chỉnh số hạng ở lần cộng tiếp theo.

**3. Vì sao phải tính `compensation = (t - sum) - y` trước `sum = t`?** Vì công thức cần `sum` cũ.

**4. Dãy mười giá trị `0.1` có luôn được lưu chính xác như thập phân trong `double` nhờ Kahan không?** Không; các giá trị đầu vào vẫn là số nhị phân gần `0.1`.

**5. Với `[1e16, 1, -1e16]`, tổng toán học và tổng Kahan là gì?** Tổng toán học là 1; Kahan cơ bản trong ví dụ cho 0.0, Neumaier cho 1.0.

**6. Bộ nhớ phụ Kahan phụ thuộc `n` như thế nào?** `O(1)` khi cộng tuần tự vì chỉ thêm một biến bù.

**7. Nếu cần tổng chính xác các giá trị thập phân từ chuỗi, nên dùng gì?** Một biểu diễn thập phân chính xác phù hợp như `BigDecimal` từ chuỗi, hoặc số nguyên theo đơn vị quy ước nếu thích hợp.

**8. Nếu đầu vào có vô cực, có thể tin rằng Kahan sẽ cho kết quả hợp lý hơn cộng thường không?** Không. Các phép cộng/trừ bù có thể đưa `compensation` thành NaN; phải kiểm tra miền và xử lý ngoại lệ theo bài toán.

**Ghi nhớ:** Kahan summation dùng **tổng `sum` và lượng bù `compensation`** để giảm tác hại của sai số làm tròn tích lũy. Nó thường tốt hơn cộng tuần tự trong dãy dài có giá trị chênh lệch độ lớn, vẫn chạy `O(n)` và dùng `O(1)` bộ nhớ phụ. Tuy nhiên, nó **không biến số thực máy tính thành số học chính xác**, không bảo đảm xử lý triệt tiêu mạnh, NaN/Infinity hoặc overflow. Chọn Kahan, Neumaier, pairwise hay biểu diễn số chính xác dựa trên **yêu cầu sai số, dữ liệu và miền giá trị**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-006">Điều kiện bài toán và trường hợp sai số đầu vào bị khuếch đại</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện bài toán và trường hợp sai số đầu vào bị khuếch đại

Trong tính toán số, **một kết quả sai nhiều không nhất thiết bắt nguồn từ code Java sai**. Có những bài toán vốn rất nhạy: chỉ thay đổi một lượng rất nhỏ trong dữ liệu đầu vào cũng làm **giá trị đầu ra thay đổi lớn theo tỷ lệ tương đối**. Tính chất này gọi là **điều kiện của bài toán** (*conditioning*). Một bài toán có điều kiện xấu (*ill-conditioned*) vẫn có thể được giải bằng thuật toán ổn định; ngược lại, một bài toán có điều kiện tốt vẫn có thể bị tính sai nếu chọn công thức hoặc cách triển khai không ổn định.

Ví dụ đơn giản là lấy hiệu hai số gần nhau:

```text
a = 1 + 2^-40
b = 1

a - b = 2^-40

Nếu a tăng 2^-50:
(a + 2^-50) - b = 2^-40 + 2^-50
```

**Thay đổi tuyệt đối** của đầu vào và đầu ra đều là `2^-50`, nhưng **tỷ lệ thay đổi của đầu ra so với giá trị hiệu ban đầu** là `2^-10 = 0.0009765625` (gần 0,1%). Trong khi đó, tỷ lệ thay đổi của `a` chỉ khoảng `8.88 × 10^-16`. Chênh lệch tương đối được khuếch đại hơn **một nghìn tỷ lần**.

Bài học sẽ giải thích điều kiện tuyệt đối, điều kiện tương đối, số điều kiện, phép trừ gần triệt tiêu, bài toán gần điểm kỳ dị, và cách phân biệt **độ nhạy vốn có của đầu vào** với **lỗi do thuật toán** khi lập trình Java.

## 1. Hai loại sai số cần tách biệt

Cho một bài toán toán học nhận đầu vào `x` và tính đầu ra `f(x)`. Trong thực tế, máy tính có thể nhận `x̃` thay vì `x` do dữ liệu đo đạc, chuyển đổi văn bản sang số thực, hoặc kết quả của các phép tính trước đó. Sau đó máy tính tạo ra một kết quả xấp xỉ `ŷ`.

Ta có thể phân tích hai ảnh hưởng khác nhau:

1. **Ảnh hưởng của nhiễu đầu vào:** so sánh `f(x̃)` với `f(x)`. Nếu chỉ thay đổi một chút đầu vào mà kết quả đúng của bài toán thay đổi mạnh, **bài toán nhạy**. Đây là conditioning.
2. **Ảnh hưởng của thuật toán/số học máy:** so sánh `ŷ` với `f(x̃)`. Nếu thuật toán tạo nhiều sai số bổ sung do làm tròn, overflow, underflow hoặc phép biến đổi bất lợi, đó là vấn đề **ổn định của thuật toán** (*numerical stability*).

Một cách tóm lược:

```text
Đầu vào lý tưởng x
      │ sai số đo/chuyển đổi dữ liệu
      ▼
Đầu vào thực có x̃ ──[bài toán toán học]──► f(x̃)
      │                                         │
      └────────[thuật toán số thực tế]────────► ŷ
```

**Conditioning** liên quan tới chính hàm toán học và điểm đầu vào; **stability** liên quan tới cách tính hàm ấy. Thuật toán tốt có thể giảm sai số tính toán **thêm vào**, nhưng không thể bảo đảm khôi phục thông tin không có trong đầu vào.

## 2. Sai số tuyệt đối và sai số tương đối

Với đại lượng đúng `v` và đại lượng xấp xỉ `ṽ`:

```text
Sai số tuyệt đối:       |ṽ - v|
Sai số tương đối:       |ṽ - v| / |v|    (khi v khác 0)
```

Ví dụ: cùng sai số tuyệt đối `0.001`, nhưng ở giá trị `1000` tỷ lệ chỉ `10^-6`, còn ở giá trị `0.01` tỷ lệ là `0.1`, tức **10%**. Vì vậy, cần nêu rõ đang nói tới **độ lớn tuyệt đối** hay **tỷ lệ tương đối**.

Đối với đầu ra bằng 0 hoặc rất gần 0, sai số tương đối có thể **không xác định hoặc cực lớn** dù sai số tuyệt đối rất nhỏ. Khi đó, một chuẩn sai số tuyệt đối có thang đo nghiệp vụ thường hữu ích hơn. Không nên chia bừa cho một giá trị gần zero rồi diễn giải con số rất lớn như “thuật toán chắc chắn sai”.

Lưu ý Java có `double` hữu hạn, `NaN`, Infinity và `+0.0`/`-0.0`; nếu tự viết công cụ đo sai số, hãy xác định cách xử lý các trường hợp này trước khi chia hoặc so sánh.

## 3. Điều kiện tuyệt đối và điều kiện tương đối của hàm một biến

Với hàm `f(x)` khả vi và nhiễu nhỏ `δx`, xấp xỉ tuyến tính là:

```text
δy = f(x + δx) - f(x) ≈ f'(x) × δx
```

**Độ nhạy tuyệt đối cục bộ** được phản ánh bởi `|f'(x)|`: giá trị lớn cho biết nhiễu tuyệt đối nhỏ ở `x` có thể tạo thay đổi tuyệt đối lớn ở `f(x)`.

Nếu `x ≠ 0` và `f(x) ≠ 0`, **số điều kiện tương đối cục bộ** thường được định nghĩa:

```text
κ_rel(x) = |x × f'(x) / f(x)|
```

Khi `δx` đủ nhỏ để xấp xỉ tuyến tính phù hợp:

```text
|δy| / |f(x)| ≈ κ_rel(x) × (|δx| / |x|)
```

Diễn giải:

- `κ_rel ≈ 1`: sai số tương đối đầu ra cùng bậc với đầu vào (theo mô hình cục bộ).
- `κ_rel ≫ 1`: sai số tương đối của đầu vào có thể được **khuếch đại mạnh** ở đầu ra; đây là điều kiện xấu về mặt tương đối.
- `κ_rel ≪ 1`: một nhiễu tương đối nhỏ ở đầu vào có thể tạo nhiễu tương đối đầu ra còn nhỏ hơn.

Đây là **xấp xỉ cục bộ**, không phải bảo đảm cho mọi nhiễu lớn. Số điều kiện còn phụ thuộc **chuẩn đo đầu vào/đầu ra và cách đặt bài toán**; gần zero, thang đo tuyệt đối và tương đối có thể đưa tới những nhận định khác nhau.

## 4. Phép trừ hai số gần nhau: đầu ra nhỏ, độ nhạy tương đối lớn

Xét `f(a,b) = a - b`. Đây là bài toán hai đầu vào. Nếu cả `a` và `b` đều có nhiễu:

```text
a → a + δa
b → b + δb

(a + δa) - (b + δb) = (a - b) + (δa - δb)
```

Sai số tuyệt đối của hiệu **không vượt** `|δa| + |δb|`, nhưng nếu `|a-b|` rất nhỏ so với `|a|` và `|b|`, sai số tương đối của hiệu có thể lớn. Một thước đo điều kiện tương đối cho phép trừ (với thành phần đầu vào có sai số tương đối cùng cỡ) là:

```text
κ_sub ≈ (|a| + |b|) / |a - b|
```

Khi `a ≈ b`, mẫu số rất nhỏ, khiến `κ_sub` lớn. Điều này không có nghĩa lệnh trừ của CPU nhất định sai. Trái lại, phép trừ hai số floating-point đủ gần nhau đôi khi còn **chính xác hoàn toàn đối với hai giá trị đã được biểu diễn** (liên quan tới định lý Sterbenz). Vấn đề là nếu dữ liệu đầu vào hơi đổi, **đáp án đúng của bài toán cũng đổi nhiều theo tỷ lệ**.

Ví dụ được chọn với các số nhị phân biểu diễn chính xác:

```java
double a = 1.0 + 0x1.0p-40;
double changedA = a + 0x1.0p-50;
double difference = a - 1.0;
double changedDifference = changedA - 1.0;
```

Trong literal hex floating-point của Java, `0x1.0p-40` là `2^-40`, `0x1.0p-50` là `2^-50`. Tất cả các giá trị này được `double` biểu diễn chính xác.

| Chỉ số | Giá trị |
| --- | --- |
| Hiệu ban đầu `a - 1` | `2^-40` |
| Nhiễu thêm vào `a` | `2^-50` ≈ `8.881784197001252E-16` |
| Thay đổi tuyệt đối của hiệu | Cũng là `2^-50` |
| Thay đổi tương đối của hiệu | `2^-10 = 0.0009765625` |
| Thay đổi tương đối của `a` | Khoảng `8.88E-16` |
| Hệ số khuếch đại tương đối tại điểm xét | Gần `2^40`, hơn một nghìn tỷ lần |

Trong ví dụ có đúng một đầu vào biến động, ta xem `f(a)=a-1`; số điều kiện tương đối là `|a/(a-1)| = 2^40+1`. Giá trị của hệ số khuếch đại tính bằng hai sai khác thực tế cũng bằng **1_099_511_627_777** (in dưới dạng số nguyên), trong khi phép trừ ở ví dụ **không mắc lỗi làm tròn đầu ra**. Đó là minh chứng rõ ràng rằng conditioning xấu và thuật toán mất ổn định **không phải một khái niệm**.

## 5. Hàm có mẫu số gần zero: điều kiện xấu gần điểm kỳ dị

Xét:

```text
f(x) = 1 / (x - 1),   x ≠ 1

f'(x) = -1 / (x - 1)^2

κ_rel(x) = |x / (x - 1)|
```

Khi `x` tiến gần `1`, cả đạo hàm tuyệt đối lẫn số điều kiện tương đối có thể rất lớn. Ví dụ, với `x = 1 + 2^-20`:

```text
f(x) = 2^20 = 1_048_576
κ_rel(x) ≈ 2^20
```

Nếu tăng `x` thêm `2^-40` (khoảng `9.09 × 10^-13`), đầu ra đổi thành gần `1_048_575.0000009537`. Đầu vào thay đổi cực nhỏ, nhưng sự thay đổi tương đối của đầu ra có độ lớn gần một phần triệu, tức hệ số khuếch đại vào khoảng một triệu lần so với sai số tương đối đầu vào.

Đây là hệ quả của bài toán **gần cực (pole)**, không phải do lập trình viên viết thiếu dấu ngoặc. Một thuật toán ổn định tính `1/(x-1)` vẫn phải chịu độ nhạy này nếu giá trị thực của `x` không chắc chắn.

Tại **`x = 1.0` chính xác**, phép chia `1.0 / (x - 1.0)` trong Java trả **`+Infinity`** theo ngữ nghĩa floating-point; không ném `ArithmeticException` như phép chia **số nguyên** cho zero. Trong chương trình thực cần kiểm tra miền xác định và khoảng cách tới điểm kỳ dị trước khi dùng kết quả.

## 6. Vì sao “thuật toán ổn định” không xóa được điều kiện xấu?

Một thuật toán được gọi là **ổn định số** khi sai số do tính toán của nó được kiểm soát tốt so với yêu cầu và độ nhạy của bài toán. Phân biệt hai hướng đánh giá phổ biến:

- **Forward error:** `|ŷ - f(x)|`, tức khoảng cách từ đáp án máy tính đến đáp án lý tưởng tại đầu vào gốc.
- **Backward error:** xem `ŷ` có thể được coi là đáp án đúng `f(x+Δx)` của **một đầu vào gần `x`** hay không. Nếu có với `Δx` đủ nhỏ theo chuẩn đã chọn, thuật toán có thể được đánh giá là ổn định ngược (*backward stable*).

Với bài toán điều kiện tốt, một thuật toán có backward error nhỏ thường cho forward error nhỏ. Với bài toán điều kiện xấu, **ngay cả thay đổi đầu vào rất nhỏ** vẫn có thể cho forward error lớn. Một quy tắc phân tích ở mức xấp xỉ cục bộ là:

```text
sai số tương đối đầu ra
    ≈ số điều kiện × sai số tương đối đầu vào
      + ảnh hưởng sai số thuật toán
```

Công thức mang tính trực giác, không phải định luật cộng sai số có thể áp dụng nguyên xi cho mọi hàm hay mọi trường hợp `NaN`/Infinity. Phân tích chính xác cần xác định chuẩn đo, các hạng sai số và phạm vi đầu vào. Tuy vậy, nguyên tắc quan trọng là: **thuật toán tốt không thể làm một bài toán ill-conditioned trở thành well-conditioned chỉ bằng việc thay cách viết code**.

## 7. Trường hợp ngược lại: bài toán không quá nhạy nhưng công thức tính gây mất ổn định

Xét hàm:

```text
g(x) = sqrt(x + 1) - sqrt(x),  với x ≥ 0
```

Khi `x` rất lớn, hai căn bậc hai gần bằng nhau. Phép trừ hai số sau khi làm tròn có thể **triệt tiêu gần hết các chữ số có nghĩa**. Nhưng ta có thể biến đổi đại số (với `x ≥ 0`):

```text
sqrt(x+1) - sqrt(x)
    = ((x+1)-x) / (sqrt(x+1)+sqrt(x))
    = 1 / (sqrt(x+1)+sqrt(x))
```

Trong Java:

```java
double x = 1.0e16;
double direct = Math.sqrt(x + 1.0) - Math.sqrt(x);
double stable = 1.0 / (Math.sqrt(x + 1.0) + Math.sqrt(x));
```

Với `x = 1e16`, `x + 1.0` bị làm tròn về chính `x` ở kiểu `double`; cách viết trực tiếp cho **`0.0`**. Cách viết theo mẫu số cho khoảng **`5.0E-9`**, gần giá trị toán học `1/(2×10^8)`.

Điểm mấu chốt: **bài toán tính `g(x)` không tự nhiên vô vọng ở `x` lớn** (theo độ nhạy tương đối, nó khá tốt ở miền này), nhưng cách tính hiệu của hai căn gần nhau gây mất ổn định. Biến đổi công thức giúp tránh phép trừ gây hủy triệt tiêu. Đây là lỗi **thuật toán**, khác với ví dụ `a-b` có đầu vào không chắc chắn và hiệu rất nhỏ.

Không nên áp dụng công thức biến đổi một cách mù quáng cho mọi miền: cần kiểm tra miền xác định và nguy cơ overflow/underflow của các trung gian trong công thức mới. Với `x ≥ 0` hữu hạn của ví dụ và độ lớn thông dụng, dạng nghịch đảo là lựa chọn tốt để tránh cancellation.

## 8. Một công thức tốt hơn còn có thể tránh overflow trung gian

Tính độ dài vector hai chiều:

```text
h(a,b) = sqrt(a^2 + b^2)
```

Bài toán này thường **không nhạy quá mức** theo sai số tương đối của các thành phần khi độ lớn có ý nghĩa. Nhưng công thức trực tiếp có thể tràn dù kết quả cuối vẫn nằm trong phạm vi `double`:

```java
double a = 1.0e200;
double naive = Math.sqrt(a * a + a * a); // Infinity.
double robust = Math.hypot(a, a);        // Hữu hạn, khoảng 1.414e200.
```

Ở đây `a*a = 1e400` vượt `Double.MAX_VALUE` trước khi gọi `sqrt`. `Math.hypot` dùng phương pháp phù hợp để tránh overflow/underflow trung gian trong những trường hợp như vậy. Ví dụ này không phải “sửa điều kiện bài toán”: nó dùng **thuật toán tốt hơn** để tính một hàm vốn có kết quả hữu hạn.

Thông điệp thực tiễn: trước khi tăng độ chính xác kiểu số, hãy xem có **hàm thư viện hoặc biến đổi ổn định** cho chính phép tính đang thực hiện không.

## 9. Chương trình Java 17+ chạy độc lập

Tạo tệp **`ConditionSensitivityDemo.java`**:

```java
public class ConditionSensitivityDemo {
    public static void main(String[] args) {
        double x = 1.0 + 0x1.0p-40;
        double perturbed = x + 0x1.0p-50;
        double gap = x - 1.0;
        double perturbedGap = perturbed - 1.0;

        double relativeInputChange = (perturbed - x) / x;
        double relativeOutputChange = (perturbedGap - gap) / gap;
        System.out.println("gapHex=" + Double.toHexString(gap));
        System.out.println("changedGapHex=" + Double.toHexString(perturbedGap));
        System.out.println("absoluteInputChange=" + (perturbed - x));
        System.out.println("absoluteOutputChange=" + (perturbedGap - gap));
        System.out.println("relativeOutputChange=" + relativeOutputChange);
        System.out.println("relativeAmplificationRounded="
                + Math.round(relativeOutputChange / relativeInputChange));

        double p = 1.0 + 0x1.0p-20;
        double q = p + 0x1.0p-40;
        double fP = 1.0 / (p - 1.0);
        double fQ = 1.0 / (q - 1.0);
        System.out.println("nearPoleOriginal=" + fP);
        System.out.println("nearPoleChanged=" + fQ);
        System.out.println("nearPoleOutputChanged=" + (fP != fQ));

        double large = 1.0e16;
        double direct = Math.sqrt(large + 1.0) - Math.sqrt(large);
        double reformulated =
                1.0 / (Math.sqrt(large + 1.0) + Math.sqrt(large));
        System.out.println("directCancellation=" + direct);
        System.out.println("stableReformulation=" + reformulated);

        double a = 1.0e200;
        System.out.println("naiveHypotenuseInfinite="
                + Double.isInfinite(Math.sqrt(a * a + a * a)));
        System.out.println("hypotFinite=" + Double.isFinite(Math.hypot(a, a)));

        double threshold = 0x1.0p53;
        System.out.println("inputIncrementLost=" + (threshold + 1.0 == threshold));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ConditionSensitivityDemo.java
java ConditionSensitivityDemo
```

Kết quả stdout chính xác:

```text
gapHex=0x1.0p-40
changedGapHex=0x1.004p-40
absoluteInputChange=8.881784197001252E-16
absoluteOutputChange=8.881784197001252E-16
relativeOutputChange=9.765625E-4
relativeAmplificationRounded=1099511627777
nearPoleOriginal=1048576.0
nearPoleChanged=1048575.0000009537
nearPoleOutputChanged=true
directCancellation=0.0
stableReformulation=5.0E-9
naiveHypotenuseInfinite=true
hypotFinite=true
inputIncrementLost=true
```

Các dòng kiểm chứng ba nhóm kiến thức:

1. **Độ nhạy tương đối của phép trừ:** `absoluteInputChange` và `absoluteOutputChange` bằng nhau, nhưng `relativeOutputChange` gần `0.001`, còn `relativeAmplificationRounded` vượt `10^12`. Đây là bài toán nhạy dù phép trừ trên các giá trị biểu diễn được là chính xác.
2. **Hàm gần điểm kỳ dị:** `nearPoleOriginal` và `nearPoleChanged` khác nhau sau một nhiễu rất nhỏ ở `x`.
3. **Thuật toán có/không ổn định:** `directCancellation=0.0` so với `stableReformulation=5.0E-9`; `naiveHypotenuseInfinite=true` trong khi `hypotFinite=true`.

Dòng `inputIncrementLost=true` thể hiện sự thật bổ sung: ở `2^53`, `double` không còn biểu diễn được mọi số nguyên kế tiếp, nên `2^53 + 1.0` làm tròn về `2^53`. Nếu một chênh lệch **đã mất khi hình thành dữ liệu đầu vào**, thuật toán phía sau không có cách nào suy ra chính xác nó từ giá trị `double` ấy.

## 10. Độ chính xác biểu diễn khác điều kiện bài toán

`float` có khoảng 24 bit significand hiệu dụng, `double` có khoảng 53 bit; mọi phép toán được biểu diễn trên lưới giá trị hữu hạn với khoảng cách phụ thuộc độ lớn. Công cụ `Math.ulp(x)` cho biết bước biểu diễn liên quan tới `x`, còn `Math.nextUp(x)` và `Math.nextDown(x)` giúp xem các giá trị kề nhau.

Ví dụ:

```java
double limit = 0x1.0p53; // 2^53.
boolean lost = (limit + 1.0 == limit); // true.
double step = Math.ulp(limit);        // 2.0.
```

Dữ liệu đo/chuyển đổi có thể bị làm tròn khi tạo ra `double`, khiến `x̃` khác giá trị lý tưởng `x`. **Conditioning** mô tả tác động của sự khác biệt đó lên câu trả lời toán học, còn **ULP và quy tắc floating-point** mô tả một nguồn sai khác phổ biến trong máy tính.

Nếu một bài toán gần điểm kỳ dị có `κ_rel` rất lớn, sai số tương đối của đầu vào cỡ vài ULP vẫn có thể làm đầu ra đổi đáng kể. Nhưng chỉ nhìn ULP không đủ xác định chất lượng dữ liệu: sai số cảm biến, khuyết dữ liệu, mô hình hóa hoặc nhập liệu sai thường lớn hơn sai số làm tròn máy nhiều bậc.

## 11. Chọn thang đo và đơn vị trước khi báo cáo bài toán “nhạy”

Tính điều kiện tương đối có thể thay đổi khi chọn đầu vào hoặc thang đo. Ví dụ `f(x)=x-1` gần `x=1` rất nhạy về **sai số tương đối của đầu ra** vì `f(x)` gần zero. Nhưng độ nhạy **tuyệt đối** `|f'(x)|=1` vẫn không lớn: đầu vào tăng bao nhiêu thì đầu ra tăng bấy nhiêu.

Nếu nghiệp vụ quan tâm **sai số tuyệt đối không quá `10^-9`**, việc đầu ra nhỏ bị sai tỷ lệ 50% không nhất thiết gây hậu quả. Ngược lại, nếu hệ thống dùng **tỷ lệ tăng trưởng**, độ nhạy tương đối lớn có thể là vấn đề nghiêm trọng.

Khi đo nhiễu, xác định:

- Đầu vào nào thay đổi: một tham số, nhiều tham số hay tất cả?
- Dùng chuẩn nào cho vector đầu vào (ví dụ chuẩn vô cùng `L∞` hoặc Euclid `L2`)?
- Nhiễu là tuyệt đối, tương đối, hay được mô tả bằng khoảng tin cậy từ dữ liệu đo?
- Đầu ra yêu cầu **số chữ số có nghĩa**, sai số tuyệt đối, sai số tương đối hay giới hạn nghiệp vụ?
- Có điểm `f(x)=0`, `x=0`, điểm kỳ dị hoặc bất liên tục làm công thức tương đối trở nên vô nghĩa không?

Với bài toán nhiều biến, **Jacobian** là phiên bản tổng quát của đạo hàm; độ nhạy phụ thuộc hướng nhiễu và chuẩn được chọn. Trong hệ tuyến tính `Ax=b`, số điều kiện ma trận `κ(A)` cho biết độ nhạy của nghiệm đối với nhiễu dữ liệu theo một chuẩn cụ thể. Không nên dùng nguyên công thức một biến `|x f'/f|` cho mọi hệ phương trình nhiều chiều.

## 12. Gắn conditioning với bài toán dữ liệu thực tế

**Tình huống A — hiệu giữa hai phép đo lớn.** Ví dụ đo hai khối lượng gần `1000 kg` rồi trừ để tìm phần chênh `0.001 kg`. Nếu mỗi phép đo có độ bất định `±0.01 kg`, thì độ bất định của hiệu có thể lớn hơn chính kết quả cần tìm. Dùng `BigDecimal` để trừ chính xác các số thập phân đã ghi **không thể phục hồi độ chính xác đo đạc ban đầu**. Cần cảm biến/qui trình đo tốt hơn hoặc đo trực tiếp đại lượng chênh lệch.

**Tình huống B — chia cho một độ lệch gần zero.** Ví dụ tính chỉ tiêu `1/(p - p0)` khi `p` gần `p0`. Một sai số rất nhỏ của cảm biến `p` sẽ làm chỉ tiêu thay đổi mạnh. Cần xác định miền hợp lệ, đặt ngưỡng khoảng cách tới `p0`, tính khoảng đầu ra tương ứng với bất định đầu vào hoặc đổi mô hình nếu gần điểm kỳ dị không có ý nghĩa nghiệp vụ.

**Tình huống C — tính toán hình học lớn.** Độ dài hai cạnh `1e200` vẫn có thể cho đường chéo hữu hạn, nhưng công thức dùng `a*a+b*b` ở `double` gây overflow. `Math.hypot(a,b)` xử lý tốt hơn mà không cần giả sử dữ liệu gốc phải bớt chính xác.

**Tình huống D — tính chênh của hai căn lớn.** Trực tiếp `sqrt(x+1)-sqrt(x)` có thể cho 0 dù đáp án dương. Biến đổi thành `1/(sqrt(x+1)+sqrt(x))` là sửa **thuật toán**, không phải giảm độ nhạy vốn có của hàm.

## 13. Cách kiểm thử độ nhạy một cách có hệ thống trong Java

Để đánh giá một hàm số trong ứng dụng, nên kết hợp:

1. **Tìm miền và điểm khó:** mẫu số gần zero, căn của số gần biên, hiệu hai đại lượng gần nhau, dữ liệu khác bậc độ lớn, điều kiện cực trị.
2. **Ước lượng condition number** khi có đạo hàm hoặc mô hình toán học phù hợp; phân biệt tuyệt đối/tương đối và tránh chia cho zero.
3. **Tạo perturbation có kiểm soát** cho đầu vào. Có thể dùng `Math.nextUp(x)`/`Math.nextDown(x)` để thay một bước biểu diễn, hoặc nhiễu theo sai số đo thực tế.
4. **So hai nguồn sai số:** chạy thuật toán trên các đầu vào hơi khác nhau, rồi so với phương pháp tham chiếu có độ chính xác cao hơn hoặc công thức giải tích khi khả thi.
5. **Thử thêm các giá trị biên:** gần zero, Infinity, NaN, subnormal, giá trị rất lớn, `Double.MAX_VALUE`, vùng đầu vào gây cancellation.
6. **Thử tính chất (properties):** đơn điệu nếu mô hình phải đơn điệu, giá trị không âm nếu đại lượng không âm, giới hạn vật lý, khả năng kiểm tra ngược từ nghiệm về dữ liệu.
7. **Ghi rõ thang sai số mong muốn** trong test, thay vì đòi `double` bằng đúng bit với đáp án thập phân lý tưởng trong mọi tình huống.

Một số phép tính có thể dùng `BigDecimal` với `MathContext` đủ chính xác để tạo giá trị tham chiếu, nhưng **`BigDecimal` không hỗ trợ sẵn mọi hàm số học**, và nó không tự giải quyết mô hình ill-conditioned. Với biểu thức cần các hàm siêu việt, tham chiếu có thể cần thư viện số học độ chính xác cao hoặc biến đổi giải tích được kiểm chứng.

## 14. Sai số forward/backward: hiểu đúng một kết quả khác nhau

Giả sử mô hình lý tưởng có `f(x)`, đầu vào lưu trong máy là `x̃`, kết quả máy là `ŷ`. Phân tích nên hỏi từng câu:

| Câu hỏi | Đang nói về |
| --- | --- |
| `x̃` khác `x` bao nhiêu? | Sai số dữ liệu/biểu diễn |
| `f(x̃)` khác `f(x)` bao nhiêu? | Độ nhạy của **bài toán** |
| `ŷ` khác `f(x̃)` bao nhiêu? | Sai số **thuật toán** tại đầu vào đã lưu |
| `ŷ` có bằng `f(x+Δx)` với `Δx` nhỏ không? | Góc nhìn **backward error** |
| Cả hệ thống có đạt yêu cầu độ chính xác không? | Kết hợp mô hình, dữ liệu, thuật toán và điều kiện |

Trong ví dụ `a = 1 + 2^-40`, phép trừ gần nhau **không phải thủ phạm chính**: cả giá trị gốc và giá trị nhiễu đều có thể lưu và trừ chính xác. Sự khuếch đại tương đối là thuộc tính của việc lấy **hiệu rất nhỏ giữa hai giá trị cỡ 1**.

Ngược lại, trong ví dụ `sqrt(x+1)-sqrt(x)`, việc lựa chọn công thức trực tiếp tạo ra mất chữ số có nghĩa khi các trung gian bị làm tròn. Chuyển sang dạng nghịch đảo giúp giảm sai số tính toán; đó là cải thiện thuật toán, không phải tái định nghĩa đáp án toán học.

## 15. Những nhầm lẫn thường gặp

| Nhầm lẫn | Vì sao sai | Cách tiếp cận đúng |
| --- | --- | --- |
| Có kết quả sai lớn thì chắc chắn do Java `double` triển khai sai | Bài toán có thể có condition number rất lớn | Phân tích độ nhạy toán học trước |
| Thuật toán ổn định sẽ luôn cho đáp án gần `f(x)` | Dữ liệu `x̃` có thể đã khác `x`; bài toán nhạy | Phân biệt sai số dữ liệu với sai số thuật toán |
| Hai số gần nhau thì phép trừ luôn được CPU tính sai | Với các số floating-point đủ gần, phép trừ đôi khi chính xác | Xét nhiễu đầu vào và thuật toán riêng |
| Sai số tương đối lớn thì sai số tuyệt đối cũng lớn | Đầu ra rất nhỏ dễ có sai số tương đối lớn | Nêu chuẩn lỗi phù hợp miền đo |
| Tăng từ `float` lên `double` luôn chữa được điều kiện xấu | Tăng độ chính xác không đổi bản chất hàm toán học | Phân tích condition number và chất lượng dữ liệu |
| `BigDecimal` sửa được nhiễu cảm biến | Nó chỉ tăng kiểm soát biểu diễn/tính toán | Cải thiện đo đạc hoặc mô hình nếu cần |
| `sqrt(a*a+b*b)` an toàn khi kết quả cuối hữu hạn | Trung gian `a*a` có thể overflow | Dùng `Math.hypot(a,b)` |
| `sqrt(x+1)-sqrt(x)` luôn an toàn vì toán học dương | Hai căn được làm tròn gần bằng nhau | Dùng biến đổi đại số ổn định |
| Số điều kiện một biến áp dụng y hệt cho nhiều biến | Độ nhạy nhiều biến phụ thuộc chuẩn và hướng nhiễu | Xem Jacobian và condition number phù hợp |
| So sánh `double` bằng `==` đủ để đánh giá tính đúng | Kết quả bị làm tròn và có thể rất gần nhau | Dùng tiêu chí sai số có thiết kế và test biên |

## 16. Câu hỏi tự kiểm tra

1. **Conditioning là gì?** Là tính nhạy của đáp án toán học đối với thay đổi nhỏ ở đầu vào, tại miền/điểm và chuẩn đo đã xác định.
2. **Numerical stability khác conditioning ở điểm nào?** Stability nói về sai số do **thuật toán tính**, conditioning nói về **bản thân bài toán**.
3. **Với `f(x)=x-1`, tại `x` rất gần 1, tại sao sai số tương đối đầu ra tăng mạnh?** Vì `f(x)` rất nhỏ, trong khi nhiễu tuyệt đối của `x` vẫn truyền vào hiệu; số điều kiện tương đối `|x/(x-1)|` lớn.
4. **Phép trừ hai số gần nhau có nhất thiết làm tròn sai không?** Không. Nó có thể chính xác trên hai số đã lưu, nhưng kết quả rất nhạy theo **sai số tương đối của đầu vào**.
5. **Với `f(x)=1/(x-1)`, chuyện gì xảy ra khi `x` gần 1?** Đạo hàm và số điều kiện tương đối có thể rất lớn; một sai số nhỏ của `x` có thể làm `f(x)` thay đổi mạnh.
6. **Vì sao `sqrt(1e16 + 1) - sqrt(1e16)` trong `double` có thể bằng zero?** Vì các giá trị trung gian bị làm tròn thành hai số như nhau; công thức trực tiếp gây cancellation.
7. **Biến đổi ổn định hơn của `sqrt(x+1)-sqrt(x)` là gì với `x≥0`?** `1/(sqrt(x+1)+sqrt(x))`.
8. **Vì sao `Math.hypot(1e200,1e200)` có thể hữu hạn trong khi `Math.sqrt(a*a+a*a)` là Infinity?** `a*a` đã overflow trước khi lấy căn; `Math.hypot` tránh trung gian không cần thiết đó.
9. **Nếu một số đo có sai số vật lý lớn, đổi `double` sang `BigDecimal` có phục hồi giá trị chính xác không?** Không. Độ chính xác biểu diễn không phục hồi thông tin không tồn tại trong phép đo.
10. **Khi nào dùng sai số tuyệt đối thay vì sai số tương đối?** Khi đầu ra có thể bằng/gần zero hoặc nghiệp vụ quy định dung sai tuyệt đối cụ thể.

**Ghi nhớ:** sai số đầu vào có thể bị **khuếch đại do điều kiện bài toán**, kể cả khi thuật toán hoàn toàn đúng trên các giá trị đã lưu. Hãy đo cả **sai số tuyệt đối và tương đối**, phân tích số điều kiện gần những điểm nhạy, phân biệt lỗi dữ liệu với lỗi thuật toán, rồi lựa chọn công thức số ổn định (`Math.hypot`, biến đổi tránh cancellation, kiểm tra miền) và độ chính xác phù hợp với yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-007">Không làm tròn sau mọi phép tính nếu hợp đồng không yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Không làm tròn sau mọi phép tính nếu hợp đồng không yêu cầu

Trong Java, **làm tròn số không phải là công việc nên tự động thêm sau mọi phép toán**. Một chương trình có thể cần độ chính xác để tính toán nội bộ, rồi mới làm tròn đến một đơn vị được quy định khi quyết toán, ghi sổ hoặc hiển thị. Làm tròn quá sớm có thể làm mất thông tin và khiến tổng cuối khác với phép tính giữ độ chính xác rồi chỉ làm tròn tại điểm chốt.

Nhưng cũng không thể khẳng định **chỉ làm tròn cuối luôn đúng**: nếu hợp đồng hoặc quy định yêu cầu mỗi dòng hóa đơn, từng giao dịch, từng phần phân bổ phải được làm tròn độc lập, những điểm đó là ranh giới nghiệp vụ bắt buộc.

Bài học phân biệt ba khái niệm: **làm tròn nhị phân cố hữu** của `float`/`double`; **làm tròn thập phân chủ động** bằng `BigDecimal`, `Math.round` và `RoundingMode`; và **định dạng kết quả để hiển thị**. Cách quyết định đúng là đọc hợp đồng tính toán: làm tròn **giá trị nào, vào lúc nào, đến bao nhiêu chữ số, bằng quy tắc nào**?

## 1. Làm tròn mỗi khoản và làm tròn tổng có thể khác nhau

Giả sử hai khoản đều bằng `0.015`, và ta phải làm tròn về **hai chữ số thập phân** theo quy tắc `HALF_UP`.

**Phương án A — chốt từng khoản trước khi cộng:**

```text
0.015 -> 0.02
0.015 -> 0.02
Tổng = 0.04
```

**Phương án B — cộng trước, chốt tổng sau:**

```text
0.015 + 0.015 = 0.030
round(0.030, 2) = 0.03
```

Cùng dữ liệu, cùng scale, cùng rounding mode nhưng chênh `0.01`. Nếu **mỗi khoản là nghĩa vụ thanh toán độc lập** được ghi nhận ở `0.02`, tổng `0.04` có thể đúng. Nếu **hai khoản chỉ là thành phần trung gian của một nghĩa vụ chung** và yêu cầu chốt một lần, `0.03` phù hợp. Không thể tự chọn cách nào nếu hợp đồng chưa xác định.

Ví dụ Java dùng `BigDecimal` chính xác từ chuỗi:

```java
BigDecimal amount = new BigDecimal("0.015");

BigDecimal roundedEach = amount.setScale(2, RoundingMode.HALF_UP);
BigDecimal sumRoundedLines = roundedEach.add(roundedEach); // 0.04

BigDecimal sumThenRound = amount.add(amount)
        .setScale(2, RoundingMode.HALF_UP); // 0.03
```

Các đoạn code ngắn trong bài giả định đã có `import java.math.BigDecimal;` và `import java.math.RoundingMode;`, đặt trong một method. Chương trình độc lập đầy đủ nằm ở phần 9.

## 2. Đừng nhầm làm tròn thập phân chủ động với precision nhị phân

Java `double` có phần trị khoảng 53 bit, `float` khoảng 24 bit. Phần lớn phân số thập phân như `0.1` không có biểu diễn nhị phân hữu hạn, nên giá trị lưu trữ và mỗi phép toán số thực có thể chịu làm tròn **kể cả khi ta không hề gọi hàm round**.

```java
double value = 0.1 + 0.2;
System.out.println(value); // 0.30000000000000004
```

Vì vậy khuyến cáo “không làm tròn sau mọi phép tính” nghĩa là **không tự ép kết quả sau mỗi bước về số chữ số thập phân tùy chọn**, chứ không phải máy tính có thể loại bỏ sai số IEEE 754.

Việc viết:

```java
double value = 0.1 + 0.2;
String displayed = String.format(java.util.Locale.US, "%.2f", value);
// displayed là chuỗi "0.30"; value vẫn là 0.30000000000000004
```

chỉ **định dạng chuỗi**. Nó không sửa giá trị `value`. Nếu lưu chuỗi đã định dạng rồi parse ngược để tính tiếp, chương trình đã âm thầm đưa một bước làm tròn vào dữ liệu.

### Vì sao làm tròn mọi bước không “sửa” số thực?

- Có phép toán đã mất độ chính xác **trước** khi bước làm tròn thập phân diễn ra.
- Làm tròn theo scale nhỏ sau mỗi bước lại xóa thêm thông tin.
- Các bước sau có thể cộng dồn phần chênh mới.
- `double` chứa giá trị nhị phân; kết quả `Math.round(x * 100) / 100.0` vẫn mang kiểu `double` và không nhất thiết biểu diễn chính xác bội số `0.01`.

Chỉ dùng phép làm tròn thập phân khi đó **thực sự là một phần của yêu cầu**, không phải vì muốn số in ra đẹp hơn.

## 3. Scale, precision và rounding mode trong `BigDecimal`

`BigDecimal` phân biệt:

| Thuật ngữ | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Scale** | Vị trí dấu thập phân, thường tương ứng số chữ số sau dấu chấm khi scale không âm | `12.345` có scale 3 |
| **Precision** | Tổng chữ số có nghĩa của hệ thập phân | `12.345` có precision 5 |
| **Rounding mode** | Chính sách quyết định khi phải bỏ chữ số có nghĩa | `HALF_UP`, `HALF_EVEN`, `DOWN` ... |

`setScale(2, RoundingMode.HALF_UP)` yêu cầu **hai chữ số sau dấu chấm**. `round(new MathContext(4, HALF_UP))` yêu cầu **bốn chữ số có nghĩa**:

```java
BigDecimal x = new BigDecimal("123.4567");

BigDecimal byScale = x.setScale(2, RoundingMode.HALF_UP);
// 123.46

BigDecimal byPrecision = x.round(
        new java.math.MathContext(4, RoundingMode.HALF_UP));
// 123.5
```

Hai thao tác không thay thế nhau. `MathContext(2)` **không** có nghĩa làm tròn mọi giá trị đến 2 chữ số sau dấu thập phân.

Các mode phổ biến:

| Mode | Ý nghĩa | Ví dụ scale 2 |
| --- | --- | --- |
| `HALF_UP` | Trường hợp đúng giữa hai khả năng, làm tròn ra xa 0 | `2.345 -> 2.35` |
| `HALF_EVEN` | Trường hợp đúng giữa, chọn chữ số cuối giữ lại chẵn | `2.345 -> 2.34`; `2.355 -> 2.36` |
| `DOWN` | Bỏ phần dư về phía 0 | `-2.349 -> -2.34` |
| `FLOOR` | Hướng về âm vô cực | `-2.341 -> -2.35` |
| `CEILING` | Hướng về dương vô cực | `-2.341 -> -2.34` |
| `UNNECESSARY` | Không cho phép mất chữ số có nghĩa; nếu cần làm tròn thì ném `ArithmeticException` | `2.340 -> 2.34` hợp lệ; `2.345` bị từ chối |

**Không có một mode đúng cho mọi nghiệp vụ.** Cần biết quy định đối với số âm, điểm giữa, tiền tệ, thuế, đo lường và từng ranh giới quyết toán.

## 4. Dùng `BigDecimal` khi cần tính decimal chính xác

Đối với phép cộng, trừ và nhân, `BigDecimal` tạo từ chuỗi cùng API không truyền `MathContext` có thể giữ kết quả thập phân chính xác (trong giới hạn tài nguyên).

```java
BigDecimal a = new BigDecimal("0.015");
BigDecimal b = new BigDecimal("0.015");
BigDecimal sum = a.add(b); // 0.030
BigDecimal settled = sum.setScale(2, RoundingMode.HALF_UP); // 0.03
```

Không nên tạo decimal chính xác từ literal `double` bằng constructor:

```java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromTextRepresentation = BigDecimal.valueOf(0.1);
BigDecimal fromBinaryApproximation = new BigDecimal(0.1);
```

`new BigDecimal(0.1)` thể hiện **chính xác số nhị phân xấp xỉ 0.1 đang nằm trong `double`**, chứ không phải đúng số thập phân `0.1`. Dùng chuỗi thập phân hoặc số nguyên kèm scale nếu dữ liệu gốc thuộc mô hình decimal.

### Phép chia là ngoại lệ quan trọng

`1/3` không có biểu diễn thập phân hữu hạn, nên `BigDecimal.ONE.divide(BigDecimal.valueOf(3))` ném `ArithmeticException` nếu không chỉ định cách xử lý. Có thể dùng:

```java
BigDecimal third = BigDecimal.ONE.divide(
        BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
// 0.333333
```

Phép chia này buộc phải chọn precision/scale làm việc và sẽ có sai số. **Làm tròn trung gian có thể cần thiết** khi kết quả không biểu diễn hữu hạn. Mục tiêu là chọn độ chính xác làm việc đủ cho yêu cầu và không tiếp tục thu hẹp tùy tiện sau mọi phép cộng/nhân.

Cũng cần chú ý truyền `MathContext` có precision hữu hạn vào mỗi phép `add`/`multiply` có thể **chủ động làm tròn ngay ở các bước đó**, khác với việc dùng API không truyền context.

## 5. Làm tròn đơn giá trước hay thành tiền dòng trước?

Giả sử đơn giá chính xác là `0.335`, số lượng là `3`, mode `HALF_UP` và đích là scale 2.

**Chốt đơn giá trước:**

```text
round(0.335, 2) = 0.34
0.34 * 3 = 1.02
```

**Tính thành tiền dòng trước:**

```text
0.335 * 3 = 1.005
round(1.005, 2) = 1.01
```

Trong Java:

```java
BigDecimal price = new BigDecimal("0.335");
BigDecimal quantity = BigDecimal.valueOf(3);

BigDecimal totalFromUnit = price.setScale(2, RoundingMode.HALF_UP)
        .multiply(quantity); // 1.02

BigDecimal totalFromLine = price.multiply(quantity)
        .setScale(2, RoundingMode.HALF_UP); // 1.01
```

Nếu hợp đồng định nghĩa **đơn giá được chốt ở 0.34**, phép nhân theo đơn giá đã chốt có thể là đúng. Nếu hợp đồng định nghĩa đơn giá gốc 0.335 dùng để tính **thành tiền dòng** rồi mới làm tròn, kết quả là 1.01.

Điểm quan trọng là phân biệt **giá trị trung gian**, **giá trị đã được quyết toán** và **giá trị chỉ định dạng để hiển thị**. Khi một khoản đã chốt theo quy định, tổng về sau phải dựa trên dữ liệu đã chốt nếu hợp đồng yêu cầu, chứ không tự tính lại một cách khác.

## 6. Những trường hợp cần làm tròn giữa chừng

Khuyến cáo này **không cấm làm tròn có mục đích**. Ví dụ:

- **Dòng hóa đơn độc lập:** nếu thuế từng dòng phải được làm tròn, tổng thuế theo quy định có thể là tổng **thuế các dòng đã chốt**.
- **Giao dịch được thanh toán độc lập:** mỗi khoản đã chuyển thực tế là một nghĩa vụ ở đơn vị tiền tệ nhỏ nhất. Không thể tính lại từ các số tiền “chưa làm tròn” rồi ghi đè lịch sử giao dịch.
- **Chia tổng tiền thành nhiều phần:** từng phần phải nằm trên lưới đơn vị thanh toán; cần giải quyết khoản dư để bảo toàn tổng.
- **Dữ liệu đo lường:** ở lúc tiếp nhận kết quả từ thiết bị có độ phân giải cố định, làm tròn/lượng tử hóa có thể là một phần của phép đo.
- **Thuật toán có phép lượng tử hóa được đặc tả:** nếu mỗi vòng lặp được yêu cầu làm tròn, bỏ bước ấy sẽ thay đổi thuật toán.

Ví dụ chia `10.00` thành ba khoản ở scale 2: `10 / 3` cho 3.333...; làm tròn từng khoản thành `3.33` rồi cộng sẽ chỉ được `9.99`. Một chính sách bảo toàn tổng có thể phân bổ thành `3.34 + 3.33 + 3.33 = 10.00`. Cần đặc tả ai nhận phần dư, phương thức phân bổ và cách xử lý khoản âm.

**Ranh giới nghiệp vụ** quan trọng hơn số lượng bước tính hoặc cảm giác rằng “số tiền chỉ có hai chữ số sau dấu chấm”.

## 7. `Math.round` không tự động tương đương một quy tắc kế toán

Một mẹo thường thấy:

```java
double x = 12.345;
double twoPlaces = Math.round(x * 100.0) / 100.0;
```

Nó có những hạn chế:

- `x` và phép nhân `x*100` có thể đã là các xấp xỉ nhị phân.
- `Math.round(double)` trả `long` và quy tắc điểm giữa hướng về dương vô cực: `Math.round(1.5)` là 2, nhưng `Math.round(-1.5)` là -1.
- Với giá trị cực lớn hoặc NaN/vô cực, bước chuyển thành `long` có hành vi giới hạn riêng.
- `twoPlaces` vẫn là `double`; biểu diễn nhị phân không đảm bảo đúng mọi bội của 0.01.
- Nếu đặt mẹo này ở **mọi bước trung gian**, bạn lại làm mất thêm thông tin.

```java
System.out.println(Math.round(1.5));  // 2
System.out.println(Math.round(-1.5)); // -1
```

Trong khi `new BigDecimal("-1.5").setScale(0, RoundingMode.HALF_UP)` cho -2. Hai API không cùng quy tắc tại điểm giữa âm. Quy định nghiệp vụ cần nêu rõ chế độ làm tròn, thay vì dùng “round” như một khái niệm mơ hồ.

Nếu chỉ cần in, `String.format` trả chuỗi, không thay đổi biến. Nếu cần decimal chính xác có thể tính tiếp, chọn mô hình decimal thích hợp từ đầu.

## 8. Hoãn làm tròn chưa đủ để bảo đảm ổn định số học

Ngay cả khi không thêm phép làm tròn decimal, `double` vẫn có các rủi ro: mất chữ số có nghĩa, sai số tích lũy và kết quả phụ thuộc thứ tự phép toán.

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

double first = (a + b) + c; // 1.0
double second = a + (b + c); // 0.0
```

Toán học thực cho cùng kết quả 1, nhưng `b + c` làm mất đóng góp 1 ở mức khoảng 1e16 vì precision `double` không đủ. Đây là **tính không kết hợp của số học dấu phẩy động**.

Khi yêu cầu nghiêm ngặt, cần cân nhắc thuật toán ổn định số, thứ tự cộng, phương pháp bù sai số, `BigDecimal` hoặc kiểu khác thích hợp. **Tránh làm tròn sớm** chỉ là một nguyên tắc giữ thông tin, không thay thế thiết kế thuật toán số.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu nội dung dưới đây thành **`RoundingBoundaryDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class RoundingBoundaryDemo {
    public static void main(String[] args) {
        BigDecimal amount = new BigDecimal("0.015");
        BigDecimal each = amount.setScale(2, RoundingMode.HALF_UP);
        System.out.println("roundEach=" + each.add(each).toPlainString());
        System.out.println("roundTotal=" +
                amount.add(amount).setScale(2, RoundingMode.HALF_UP));

        BigDecimal unit = new BigDecimal("0.335");
        BigDecimal quantity = BigDecimal.valueOf(3);
        System.out.println("unitRounded=" +
                unit.setScale(2, RoundingMode.HALF_UP)
                        .multiply(quantity).toPlainString());
        System.out.println("lineRounded=" +
                unit.multiply(quantity)
                        .setScale(2, RoundingMode.HALF_UP).toPlainString());

        System.out.println("halfUpTie=" +
                new BigDecimal("2.345").setScale(2, RoundingMode.HALF_UP));
        System.out.println("halfEvenTie=" +
                new BigDecimal("2.345").setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("halfEvenOther=" +
                new BigDecimal("2.355").setScale(2, RoundingMode.HALF_EVEN));

        try {
            new BigDecimal("2.345").setScale(2, RoundingMode.UNNECESSARY);
            System.out.println("unnecessary=missed");
        } catch (ArithmeticException ex) {
            System.out.println("unnecessary=detected");
        }

        try {
            BigDecimal.ONE.divide(BigDecimal.valueOf(3));
            System.out.println("divideExact=unexpected");
        } catch (ArithmeticException ex) {
            System.out.println("divideExact=requiresRounding");
        }

        BigDecimal third = BigDecimal.ONE.divide(
                BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
        System.out.println("third=" + third.toPlainString());
        System.out.println("thirdTimesThree=" +
                third.multiply(BigDecimal.valueOf(3)).toPlainString());
        System.out.println("thirdAtBoundary=" +
                third.multiply(BigDecimal.valueOf(3))
                        .setScale(2, RoundingMode.HALF_UP).toPlainString());

        double binarySum = 0.1 + 0.2;
        System.out.println("binarySum=" + binarySum);
        System.out.println("display=" +
                String.format(Locale.US, "%.2f", binarySum));
        System.out.println("rawValue=" + binarySum);

        System.out.println("mathRoundPositive=" + Math.round(1.5));
        System.out.println("mathRoundNegative=" + Math.round(-1.5));

        double first = (1e16 + -1e16) + 1.0;
        double second = 1e16 + (-1e16 + 1.0);
        System.out.println("associationFirst=" + first);
        System.out.println("associationSecond=" + second);
    }
}
```

Dùng JDK 17 trở lên:

```bash
javac RoundingBoundaryDemo.java
java RoundingBoundaryDemo
```

Kết quả mong đợi:

```text
roundEach=0.04
roundTotal=0.03
unitRounded=1.02
lineRounded=1.01
halfUpTie=2.35
halfEvenTie=2.34
halfEvenOther=2.36
unnecessary=detected
divideExact=requiresRounding
third=0.333333
thirdTimesThree=0.999999
thirdAtBoundary=1.00
binarySum=0.30000000000000004
display=0.30
rawValue=0.30000000000000004
mathRoundPositive=2
mathRoundNegative=-1
associationFirst=1.0
associationSecond=0.0
```

Kết quả `roundEach` và `roundTotal` khác nhau dù quy tắc scale/mode giống nhau. Hai ví dụ đơn giá chứng minh lựa chọn ranh giới rất quan trọng. `display=0.30` không làm thay đổi `rawValue`, và hai dòng association là ví dụ độc lập về sai số thuật toán dấu phẩy động.

## 10. Cách mô tả hợp đồng làm tròn cho ứng dụng

Một đặc tả tốt cần trả lời:

| Câu hỏi | Thông tin cần quy định |
| --- | --- |
| Đầu vào là decimal chính xác hay số đo xấp xỉ? | Chuỗi decimal, số nguyên đơn vị nhỏ nhất, float/double đo lường |
| Đơn vị và miền giá trị là gì? | Đồng, xu, phần trăm, đơn vị vật lý, khoảng hợp lệ |
| Giá trị được **chốt** là gì? | Đơn giá, từng dòng, từng khoản thuế, tổng hóa đơn, khoản thanh toán |
| Ranh giới làm tròn ở đâu? | Sau mỗi khoản, sau tổng, khi trả kết quả hay khi hiển thị |
| Dùng scale và mode nào? | `scale=2`, `HALF_UP` hoặc quy tắc khác |
| Tổng phải bảo toàn theo các khoản đã chốt không? | Cách cộng/phân phối phần dư |
| Phép chia vô hạn thập phân xử lý thế nào? | Precision làm việc, rounding mode và chính sách sai số |
| Giá trị nào được lưu để đối soát? | Bản ghi quyết toán và (nếu cần) giá trị gốc phục vụ kiểm toán |

Một luồng điển hình là: **kiểm tra dữ liệu → tính với precision phù hợp → chỉ làm tròn tại ranh giới có ý nghĩa → lưu giá trị đã chốt → định dạng hiển thị**. Các phép tính sau phải dùng giá trị đã chốt nếu hợp đồng yêu cầu. Đừng vô tình parse dữ liệu hiển thị đã làm tròn rồi dùng làm nguồn tính mới.

## 11. Các lỗi phổ biến và checklist

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Round sau mọi phép tính làm kết quả chính xác hơn.” | Có thể làm tổng sai khác và mất dữ liệu trung gian. |
| “Round một lần cuối bao giờ cũng đúng.” | Sai nếu từng dòng hoặc giao dịch có nghĩa vụ chốt độc lập. |
| “Không gọi `Math.round` thì `double` không bao giờ bị làm tròn.” | Sai, số thực nhị phân hữu hạn vốn chịu roundoff. |
| “`String.format("%.2f", x)` sửa `x`.” | Sai, trả chuỗi định dạng. |
| “`setScale(2)` giống `MathContext(2)`.” | Sai, scale khác số chữ số có nghĩa. |
| “`BigDecimal` tự chia chính xác được 1/3.” | Sai, cần precision/rounding hoặc xử lý exception. |
| “Dùng `new BigDecimal(0.1)` là lấy thập phân 0.1 chính xác.” | Sai, nó lấy giá trị nhị phân xấp xỉ đã nằm trong `double`. |
| “`Math.round` luôn giống `HALF_UP`.” | Sai, nhất là tie âm. |
| “Ba khoản `3.33` cộng lại bằng `10.00`.” | Sai, tổng `9.99`; cần phân bổ phần dư theo hợp đồng. |
| “Không round sớm bảo đảm tính toán không sai số.” | Sai, cần xét thuật toán và precision của kiểu dữ liệu. |

Trước khi viết một phép làm tròn mới, hãy tự hỏi: **(1)** yêu cầu nào bắt buộc nó, **(2)** giá trị nào phải được chốt, **(3)** scale/mode nào được áp dụng, **(4)** tổng dùng bản gốc hay các khoản đã chốt, **(5)** có cần bảo toàn tổng/đối soát không, và **(6)** đây là phép tính hay chỉ là định dạng hiển thị?

**Kết luận:** Đừng làm tròn sau mọi phép tính chỉ vì muốn kết quả “gọn”. Hãy làm tròn **đúng ranh giới được quy định**, bằng **kiểu số và rounding mode phù hợp**. Khi cần thiết, giữ precision nội bộ để tránh mất thông tin; khi hợp đồng yêu cầu chốt từng khoản, phải thực hiện và bảo toàn chính những khoản đã chốt đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-008">Định dạng ít chữ số không sửa sai số của giá trị đang lưu</a>

<details>
<summary>Xem chi tiết</summary>

# Định dạng ít chữ số không sửa sai số của giá trị đang lưu trong Java

Trong Java, một giá trị `double` có thể **hiển thị** thành `0.30` trong khi giá trị thực sự đang giữ là số dấu phẩy động gần `0.30000000000000004`. Việc yêu cầu `printf`, `String.format` hay `DecimalFormat` in hai chữ số sau dấu thập phân chỉ thay đổi **chuỗi trình bày**. Nó không cập nhật biến, không thay đổi các bit của `double` và không chữa sai số đã xuất hiện trong phép tính.

Bài học thuộc chủ đề **Sai số và ổn định**. Mục tiêu là phân biệt rõ (1) biểu diễn nhị phân được lưu, (2) chuỗi văn bản nhìn thấy, (3) phép làm tròn có chủ đích và (4) tính toán thập phân chính xác bằng `BigDecimal` khi miền nghiệp vụ yêu cầu. Sự khác biệt quyết định cách kiểm thử số thực, cộng dồn, xuất dữ liệu và tính toán tiền tệ.

## 1. Ba tầng thường bị đánh đồng

| Tầng | Ví dụ | Điều gì thực sự xảy ra? |
| --- | --- | --- |
| Giá trị đang lưu trong `double` | `double sum = 0.1 + 0.2;` | Máy lưu mẫu bit của một giá trị `double` gần tổng toán học |
| Chuỗi dùng để hiển thị | `String.format(Locale.ROOT, "%.2f", sum)` | Tạo chuỗi `"0.30"`; **không sửa `sum`** |
| Giá trị đã được làm tròn/định lượng lại | `Math.round(sum * 100.0) / 100.0` | Tính và gán một **giá trị `double` mới** (nhưng vẫn là binary floating point) |
| Số thập phân theo chính sách | `new BigDecimal("0.30")` | Lưu số thập phân bằng `BigDecimal`, có thể làm tròn theo `RoundingMode` |

Hai khái niệm quan trọng:

- **Formatting**: biến đổi *cách thể hiện một giá trị* thành ký tự. Số chữ số in ra không đồng nghĩa số chữ số được lưu.
- **Rounding**: tạo kết quả số học hoặc thập phân mới theo một quy tắc làm tròn. Nó có thể thay đổi kết quả được giữ nếu bạn thực sự gán lại, nhưng không đảm bảo một `double` trở thành thập phân chính xác.

Ngay cả khi màn hình, log, bảng hay JSON được yêu cầu “hiển thị ngắn”, bản thân những phép tính trước đó vẫn đã được thực thi trên giá trị đang lưu.

## 2. Vì sao `0.1 + 0.2` không bằng chính xác `0.3`?

`float` và `double` dùng dạng dấu phẩy động nhị phân IEEE 754. Phần lớn phân số thập phân như 0.1, 0.2 và 0.3 có biểu diễn nhị phân **vô hạn tuần hoàn**, nên cần làm tròn về mẫu bit gần nhất khi lưu ở độ rộng hữu hạn. Khi cộng hai toán hạng đã được làm tròn, kết quả tiếp tục được làm tròn theo quy tắc dấu phẩy động.

~~~java
double sum = 0.1 + 0.2;
System.out.println(sum);             // 0.30000000000000004
System.out.println(sum == 0.3);      // false
System.out.println(sum - 0.3);       // 5.551115123125783E-17
~~~

`0.3` dưới dạng literal `double` cũng là số nhị phân xấp xỉ, nhưng biểu diễn đó **khác một khoảng rất nhỏ** so với kết quả `0.1 + 0.2`. Toán tử `==` so sánh hai giá trị dấu phẩy động đang lưu theo quy tắc Java, không tự áp dụng dung sai thập phân.

Đầu ra mặc định của `Double.toString` và `System.out.println(double)` thường là dạng thập phân ngắn đủ để đọc lại đúng mẫu bit (`shortest round-trip representation`), **không phải bản khai triển thập phân đầy đủ của bit lưu trữ**. Để thấy giá trị thập phân chính xác tương ứng với `double`, có thể dùng `new BigDecimal(sum)` cho mục đích phân tích. Phương pháp đó khác với cách khởi tạo `BigDecimal` được khuyến nghị cho dữ liệu thập phân chính xác.

## 3. `String.format` và `printf` chỉ thay đổi những gì được nhìn thấy

~~~java
double sum = 0.1 + 0.2;
String display = String.format(java.util.Locale.ROOT, "%.2f", sum);
System.out.println(display); // 0.30
System.out.println(sum);     // 0.30000000000000004
~~~

`%.2f` yêu cầu định dạng cố định với hai chữ số sau dấu thập phân. Formatter tạo chuỗi, **không gán ngược vào biến `sum`**. Tương tự:

~~~java
System.out.printf(java.util.Locale.ROOT, "Kết quả: %.2f%n", sum);
// In: Kết quả: 0.30
// Không sửa giá trị của sum
~~~

Với `DecimalFormat`, một mẫu như `"0.00"` cũng quy định **cách trình bày**. `DecimalFormat` có các quy tắc riêng về locale, pattern và rounding mode; không nên suy đoán rằng mọi formatter có chính sách làm tròn giống nhau trong trường hợp ở đúng điểm giữa.

Dấu phân cách thập phân có thể phụ thuộc locale: mặc định của máy tại một quốc gia có thể dùng dấu phẩy. Khi cần xuất văn bản máy đọc hoặc một ví dụ có output xác định, chỉ định `Locale.ROOT` hoặc một locale cố định phù hợp. Khi hiển thị cho người dùng, chọn locale theo yêu cầu giao diện.

**Lỗi tư duy phổ biến:** thấy `0.30` trong log và kết luận phép tính đã chính xác tới hai chữ số. Log chỉ thể hiện một bản rút gọn, có thể giấu chênh lệch đủ lớn để ảnh hưởng so sánh, ngưỡng quyết định hoặc phép tính tiếp theo.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu tệp tên `DisplayVsValue.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public class DisplayVsValue {
    public static void main(String[] args) {
        double result = 0.1 + 0.2;
        String displayed =
                String.format(Locale.ROOT, "%.2f", result);
        double parsed = Double.parseDouble(displayed);

        System.out.println("raw sum = " + result);
        System.out.println("formatted = " + displayed);
        System.out.println("raw sum after formatting = " + result);
        System.out.println("sum == 0.3 = " + (result == 0.3));
        System.out.println("parsed = " + parsed);
        System.out.println("parsed == sum = " + (parsed == result));
        System.out.println("sum - 0.3 = " + (result - 0.3));

        System.out.println("decimal from computed sum = "
                + BigDecimal.valueOf(result));
        System.out.println("exact decimal from stored double = "
                + new BigDecimal(result));
        System.out.println("scaled decimal = "
                + BigDecimal.valueOf(result)
                .setScale(2, RoundingMode.HALF_UP));

        BigDecimal exactMoney =
                new BigDecimal("0.10").add(new BigDecimal("0.20"));
        System.out.println("string decimal sum = " + exactMoney);

        double roundedDouble = Math.round(result * 100.0) / 100.0;
        System.out.println("rounded as double = " + roundedDouble);
        System.out.println("rounded double == 0.3 = "
                + (roundedDouble == 0.3));
        System.out.println("rounded double exact = "
                + new BigDecimal(roundedDouble));
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc mới hơn:

~~~bash
javac DisplayVsValue.java
java DisplayVsValue
~~~

Stdout chính xác:

~~~text
raw sum = 0.30000000000000004
formatted = 0.30
raw sum after formatting = 0.30000000000000004
sum == 0.3 = false
parsed = 0.3
parsed == sum = false
sum - 0.3 = 5.551115123125783E-17
decimal from computed sum = 0.30000000000000004
exact decimal from stored double = 0.3000000000000000444089209850062616169452667236328125
scaled decimal = 0.30
string decimal sum = 0.30
rounded as double = 0.3
rounded double == 0.3 = true
rounded double exact = 0.299999999999999988897769753748434595763683319091796875
~~~

Đối chiếu:

- Ba dòng đầu cho thấy chuỗi `0.30` **không thay đổi biến gốc**.
- Sau khi parse chuỗi `"0.30"`, ta nhận `double` được biểu diễn ngắn là `0.3`; giá trị này **khác** kết quả ban đầu `0.1 + 0.2`. Đây là một lần chuyển đổi dữ liệu mới, không phải tác dụng phụ của `String.format`.
- `BigDecimal.valueOf(result)` thu được số thập phân `0.30000000000000004` dựa trên chuỗi dạng ngắn của `double`, trong khi `new BigDecimal(result)` biểu diễn **chính xác giá trị nhị phân đã lưu dưới dạng thập phân**. Hai cách chuyển đổi có mục đích khác nhau.
- `setScale(2, HALF_UP)` trả đối tượng `BigDecimal` mới với hai chữ số lẻ và chính sách làm tròn tường minh.
- `Math.round(...)/100.0` đã tạo một `double` mới bằng với literal `0.3` về bit, **nhưng bản thân `double` đó vẫn không lưu được 0.3 thập phân chính xác**; dòng cuối cho thấy biểu diễn thập phân đầy đủ.

## 5. Định dạng, parse và gán lại là ba hành động khác nhau

Giả sử:

~~~java
double x = 0.1 + 0.2;
String text = String.format(java.util.Locale.ROOT, "%.2f", x);
~~~

Sau hai dòng, `x` **vẫn bằng** `0.30000000000000004`. Nếu sau đó gọi `Double.parseDouble(text)`, một giá trị `double` **mới** được tính từ chuỗi định dạng. Chỉ khi bạn chủ động dùng giá trị mới, ví dụ `x = Double.parseDouble(text)`, biến `x` mới thay đổi.

Việc format rồi parse không phải phương pháp tổng quát để sửa tính chính xác:

1. Bạn đã **loại bỏ thông tin** theo độ dài hiển thị. Hai giá trị thực sự khác nhau có thể cùng thành chuỗi `"0.30"`.
2. Chuỗi có thể dùng dấu thập phân, dấu nhóm hàng nghìn hoặc chữ số theo locale; `Double.parseDouble` không phải bộ parse tùy locale.
3. Giá trị parse lại vẫn là `double` nhị phân. Nhiều phân số thập phân vẫn **không biểu diễn được chính xác**.
4. Việc format/parse trong mỗi bước tính thay đổi thuật toán theo cách khó kiểm soát và có thể tạo sai lệch do làm tròn lặp lại.

Nếu yêu cầu thực tế là lưu giá trị đã lượng tử hóa tới một số chữ số, hãy định nghĩa **quy tắc lượng tử hóa số học** cụ thể, rồi lưu kết quả theo kiểu phù hợp. Nếu chỉ cần hiển thị, chỉ định dạng ở bước xuất dữ liệu.

## 6. `Math.round` tạo một số khác nhưng không chuyển sang số thập phân chính xác

~~~java
double x = 0.1 + 0.2;
double rounded = Math.round(x * 100.0) / 100.0;
System.out.println(rounded); // 0.3
~~~

Trong ví dụ, `rounded` không còn cùng mẫu bit với `x`. Điều đó khác về bản chất với `String.format`, vì phép tính đã thực sự tạo một số mới. Tuy nhiên kết quả vẫn thuộc **kiểu `double`**, nên không thể biểu diễn chính xác phần lớn phân số thập phân như 0.3.

Biểu thức `Math.round(x * 100.0) / 100.0` cũng **không phải công thức làm tròn thập phân an toàn cho mọi giá trị**. Nó có thể gặp vấn đề với số rất lớn, overflow khi nhân trước, NaN/vô cực, và cách xử lý điểm giữa đối với số âm. `Math.round` làm tròn theo quy tắc riêng của nó; đừng mặc định quy tắc đó phù hợp hóa đơn hay chính sách tài chính.

Ví dụ sự khác biệt giữa chính sách làm tròn và hiển thị:

~~~java
BigDecimal amount = new BigDecimal("1.005");
BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
// rounded = 1.01 (số thập phân với scale 2)
~~~

`setScale` trả **một `BigDecimal` mới** (đối tượng gốc không thay đổi), và `RoundingMode.HALF_UP` mô tả rõ cách xử lý chữ số bị bỏ. Cùng một dữ liệu có thể dùng chính sách khác, ví dụ `HALF_EVEN`, nếu nghiệp vụ yêu cầu. Đừng chọn một chính sách chỉ vì giao diện in hai chữ số.

## 7. Khi nào nên dùng `BigDecimal`?

Nếu miền dữ liệu đòi hỏi phép cộng, trừ, nhân và lượng tử hóa **thập phân theo quy tắc chính xác**, chẳng hạn số tiền, giá niêm yết hay đơn vị đo quy đổi có bước thập phân cố định, `BigDecimal` thường phù hợp hơn `double`.

~~~java
BigDecimal first = new BigDecimal("0.10");
BigDecimal second = new BigDecimal("0.20");
BigDecimal sum = first.add(second);
System.out.println(sum); // 0.30
~~~

Ở đây chuỗi `"0.10"` và `"0.20"` xác định số thập phân chính xác. Điều quan trọng là **xây dựng dữ liệu đúng ngay từ nguồn**:

- `new BigDecimal("0.1")`: biểu diễn giá trị thập phân 0.1 chính xác.
- `BigDecimal.valueOf(0.1)`: sử dụng dạng chuỗi chuẩn `Double.toString(0.1)`, tạo `BigDecimal` bằng 0.1. Hữu ích khi nhận giá trị `double` đã tồn tại nhưng **không biết ý định thập phân ban đầu**.
- `new BigDecimal(0.1)`: nhận chính xác giá trị nhị phân đã lưu của literal `double`, không phải chính xác con số thập phân 0.1 được viết trong mã.
- `BigDecimal.valueOf(0.1 + 0.2)`: trở thành `0.30000000000000004`, **không tự phục hồi phép cộng thập phân 0.10 + 0.20**.

Vì vậy chuyển sang `BigDecimal` **sau** khi sai số đã xuất hiện trong các phép tính `double` không bảo đảm phục hồi giá trị mà người dùng dự định nhập. Khi có thể, parse trực tiếp chuỗi thập phân gốc hoặc nhận `BigDecimal` từ lớp dữ liệu có hợp đồng chính xác, rồi tính toán bằng `BigDecimal` ngay từ đầu.

`BigDecimal` cũng cần quy tắc chia: `BigDecimal.ONE.divide(new BigDecimal("3"))` không thể biểu diễn thương thập phân hữu hạn và sẽ ném `ArithmeticException` nếu không chỉ định scale hoặc `MathContext` phù hợp. Kiểu thập phân không loại bỏ nhu cầu thiết kế chính sách làm tròn.

## 8. Sai số có thể ảnh hưởng cả so sánh và thuật toán

### So sánh số thực

~~~java
double x = 0.1 + 0.2;
if (x == 0.3) {
    // Nhánh này không chạy trong ví dụ
}
~~~

In hai giá trị với `%.2f` có thể cho `0.30` ở cả hai phía, dù `x == 0.3` là `false`. Để **so sánh gần bằng**, cần định nghĩa tolerance theo đơn vị và miền giá trị. Ví dụ đơn giản cho số hữu hạn với trị tuyệt đối không quá lớn:

~~~java
static boolean nearlyEqual(double a, double b, double eps) {
    if (!Double.isFinite(a) || !Double.isFinite(b)
            || !Double.isFinite(eps) || eps < 0.0) {
        return false;
    }
    return Math.abs(a - b) <= eps;
}
~~~

Đây là **phương thức minh họa**, cần đặt bên trong một class. Với các số có độ lớn rất lớn hoặc rất nhỏ, phép trừ có thể mất thông tin, overflow; tolerance tương đối hoặc kiểm tra theo ULP có thể thích hợp hơn tùy bài toán. Không dùng comparator theo epsilon trong `TreeSet`/`TreeMap` vì tính “gần bằng” có thể không bắc cầu, vi phạm hợp đồng sắp thứ tự.

### Mất chính xác do triệt tiêu

Định dạng cũng không thể cứu thông tin đã mất khi cộng/trừ số chênh lệch lớn:

~~~java
double large = 1e16;
double difference = (large + 1.0) - large;
System.out.println(difference); // 0.0, không phải 1.0
~~~

Ở độ lớn `1e16`, một thay đổi bằng `1.0` quá nhỏ để biểu diễn thành một `double` khác ở bước cộng. Bước trừ phía sau không có cách nào phục hồi phần thông tin đã bị làm tròn. In `0.000000` thay vì `0.0` chỉ thay đổi chữ viết của kết quả.

Đây là lý do phân biệt **hiển thị số đẹp** với **thuật toán số học ổn định**: sai số và thứ tự phép toán cần được đánh giá ngay trong thiết kế tính toán.

## 9. Chọn cách xử lý theo mục đích

| Mục đích | Lựa chọn phù hợp | Điều phải kiểm tra |
| --- | --- | --- |
| Chỉ in số dễ đọc cho người dùng | `String.format`, `printf`, `DecimalFormat` | Locale, số chữ số, quy tắc định dạng |
| Gửi giá trị dùng cho round-trip mà không mất thông tin do rút ngắn tùy ý | `Double.toString` hoặc định dạng có round-trip được bảo đảm | Cách parse tương ứng, NaN/Infinity nếu có |
| So sánh kết quả số thực gần nhau | Tolerance có hợp đồng rõ | Miền giá trị, finite, lỗi số học, tuyệt đối/tương đối |
| Lưu số đã định lượng theo bước thập phân | `BigDecimal` và `setScale(..., RoundingMode)` | Scale, rounding policy, nguồn dữ liệu |
| Tính toán tiền tệ thập phân | `BigDecimal` từ chuỗi gốc hoặc dữ liệu decimal chuẩn | Không đi qua phép tính `double` gây sai số trước đó |
| Đo đạc/khoa học chấp nhận sai số | `double`/`float` + kiểm soát sai số | Tính ổn định, phạm vi, overflow/underflow |

**Xuất dữ liệu cũng cần chú ý:** Nếu một hệ thống xuất số `double` dưới dạng chuỗi hai chữ số rồi hệ thống khác parse lại, đó là **thay đổi độ chính xác dữ liệu trao đổi**. Có thể là một quyết định sản phẩm có chủ đích, nhưng không còn là truyền nguyên vẹn cùng giá trị ban đầu. Đặc biệt, độ phân giải nhỏ hơn `0.01` có thể bị mất.

## 10. Lỗi thường gặp

1. **Cho rằng chuỗi `"0.30"` chứng minh `sum == 0.3`.** Hai giá trị có thể được in giống nhau dù khác nhau ở các bit thấp.
2. **Ghi đè log bằng một chuỗi làm tròn rồi mất khả năng chẩn đoán.** Khi cần debug sai số, lưu thêm dạng đủ chính xác (`Double.toString`, bit nếu cần) hoặc nguồn đầu vào.
3. **Format rồi parse để “chữa” sai số.** Việc này chủ động loại bỏ thông tin và vẫn tạo một `double` xấp xỉ.
4. **Đồng nhất `Math.round` với định dạng.** `Math.round` thực sự tính giá trị mới, nhưng vẫn giữ loại số nhị phân nếu kết quả cuối là `double` và không phù hợp mọi quy tắc thập phân.
5. **Chuyển `new BigDecimal(double)` rồi cho rằng nhận được decimal người dùng đã nhập.** Constructor này giữ **chính xác biểu diễn nhị phân** của đối số; cần nguồn chuỗi thập phân ban đầu nếu muốn decimal chính xác.
6. **Chỉ sửa số chữ số hiển thị để xử lý tích lũy sai số.** Sai số có thể đã ảnh hưởng các nhánh `if`, giới hạn ngưỡng, tổng, trung bình trước thời điểm format.
7. **Dùng số chữ số in ra làm bằng chứng chính xác nghiệp vụ.** Hai chữ số thập phân trên hóa đơn cần chính sách tính tiền, làm tròn, cộng dồn và thuế được định nghĩa nhất quán.
8. **Không quy định locale và đầu ra máy đọc.** Dấu phân cách, nhóm chữ số và giá trị đặc biệt có thể khiến việc parse không đúng nếu hai hệ thống dùng quy ước khác nhau.

## 11. Câu hỏi tự kiểm tra

1. Nếu `double x = 0.1 + 0.2;` và `String.format(Locale.ROOT, "%.2f", x)` cho `"0.30"`, `x == 0.3` có trở thành `true` không?
2. `Double.parseDouble("0.30")` có nhất thiết lưu đúng giá trị nhị phân đang có trong `0.1 + 0.2` không?
3. Sau `double y = Math.round(x * 100) / 100.0;`, `y` đã trở thành số thập phân chính xác chưa?
4. `new BigDecimal(0.1)` và `new BigDecimal("0.1")` có biểu diễn cùng số thập phân chính xác không?
5. Khi nào phải dùng `BigDecimal` từ nguồn dữ liệu và khi nào `double` với kiểm soát sai số vẫn hợp lý?

**Đáp án:** (1) Không; formatting không thay đổi `x` nên so sánh vẫn `false`. (2) Không; parse chuỗi ngắn tạo mẫu bit cho literal `0.3`, khác bit của `0.1 + 0.2`. (3) Chưa: kết quả là `double` mới, có thể gần một số thập phân tròn nhưng vẫn là biểu diễn nhị phân xấp xỉ. (4) Không; constructor nhận `double` phản ánh chính xác giá trị nhị phân gần 0.1, constructor nhận chuỗi biểu diễn số thập phân 0.1. (5) Dùng `BigDecimal` khi cần số học thập phân và chính sách scale/rounding xác định; `double` thích hợp nhiều phép đo, tính khoa học và thuật toán biết cách kiểm soát sai số.

## Tài liệu tham khảo

- [Java Language Specification `4.2.3 — Floating-Point Types](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3).
- [Java SE 21 API — Double](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html): `toString`, `parseDouble` và giá trị dấu phẩy động.
- [Java SE 21 API — Formatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Formatter.html): các dạng format dấu phẩy động, độ chính xác và locale.
- [Java SE 21 API — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): constructor `String`/`double`, `valueOf`, `setScale`.
- [Java SE 21 API — RoundingMode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html): quy tắc làm tròn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-009">Chọn double hay BigDecimal theo mô hình dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `double` hay `BigDecimal` theo mô hình dữ liệu trong Java

## 1. Câu hỏi đúng không phải “kiểu nào chính xác hơn?”

Khi thiết kế chương trình Java, lựa chọn giữa `double` và `BigDecimal` cần bắt đầu từ **ý nghĩa và quy tắc của dữ liệu**, không chỉ từ việc kiểu nào lưu được nhiều chữ số hơn.

- **`double`** là kiểu nguyên thủy dấu phẩy động nhị phân 64 bit (*IEEE 754 binary64*). Nó phù hợp với các phép đo, mô phỏng, thống kê và thuật toán số mà **sai số xấp xỉ được chấp nhận và kiểm soát**.
- **`BigDecimal`** là lớp số thập phân có phần trị nguyên độ lớn tùy ý và một *scale* thập phân. Nó phù hợp với dữ liệu và quy tắc tính toán cần **biểu diễn thập phân chính xác**, đặc biệt khi chính sách làm tròn có ý nghĩa nghiệp vụ.
- **Số nguyên `long` (hoặc `int`) lưu đơn vị nhỏ nhất** là một lựa chọn thứ ba hữu ích, chẳng hạn lưu số tiền bằng xu khi đơn vị và độ chính xác luôn cố định, miễn là kiểm soát miền số và tràn.

Không có quy tắc “mọi phép tính tiền đều phải dùng `BigDecimal`” hay “`double` luôn sai”. Điều quan trọng là: **nguồn dữ liệu có chính xác không, phép toán nào được thực hiện, kết quả nào phải bảo toàn và khi nào phải làm tròn?**

## 2. Bảng quyết định nhanh

| Trường hợp | Lựa chọn thường hợp lý | Lý do |
|---|---|---|
| Nhiệt độ từ cảm biến, vị trí, đo đạc | `double` | Số đo vốn có sai số; hiệu năng và API số học thuận tiện |
| Mô phỏng vật lý, thống kê, hàm lượng giác | `double` | Thư viện toán số và thuật toán thường sử dụng số thực nhị phân |
| Giá niêm yết “19.90” cần bảo toàn đúng số thập phân | `BigDecimal` từ chuỗi | `19.90` là giá trị decimal có ý nghĩa nghiệp vụ |
| Thuế, chiết khấu, tỷ giá với quy định làm tròn | `BigDecimal` cùng chính sách `RoundingMode` | Kiểm soát scale, độ chính xác và điểm làm tròn |
| Số dư chỉ có hai chữ số thập phân, lưu theo xu | `long` nếu phạm vi đủ | Tính cộng/trừ số nguyên chính xác, nhưng phải quản lý đơn vị |
| Số lượng nguyên rất lớn vượt `long` | `BigInteger` | Không cần phần thập phân và không bị giới hạn 64 bit |
| Tiền lưu decimal nhưng cần nhân với số lượng | `BigDecimal` nhân với số lượng nguyên | Giữ chính xác các hằng decimal của nghiệp vụ |
| Dữ liệu JSON/API ghi rõ một decimal tùy ý | `BigDecimal` nếu phải bảo toàn decimal | Tránh làm tròn qua `double` trước khi parse |
| Một thuật toán chuyên biệt được đặc tả bằng IEEE 754 | `double`/`float` theo đặc tả | Cần đúng ngữ nghĩa floating-point và tính tương thích |
| Giá trị đo được đưa vào báo cáo có hai chữ số | `double` có thể vẫn phù hợp | **Định dạng hiển thị** hai chữ số không đồng nghĩa cần lưu decimal chính xác |

Bảng chỉ là gợi ý thiết kế. Ví dụ ứng dụng tài chính vẫn có thể dùng `double` trong mô hình rủi ro thống kê; ngược lại một hệ thống khoa học vẫn có thể dùng `BigDecimal` nếu phép đo đặc biệt cần số học decimal ở độ chính xác quy định.

## 3. Vì sao `double` không biểu diễn chính xác mọi số thập phân?

`double` lưu giá trị dưới dạng nhị phân hữu hạn. Nhiều phân số thập phân, chẳng hạn \(0.1 = 1/10\), **không có biểu diễn nhị phân hữu hạn**. JVM lưu một số binary64 gần với 0.1, chứ không lưu phân số toán học 1/10 chính xác.

Ví dụ quen thuộc:

```java
double sum = 0.1 + 0.2;
System.out.println(sum);        // 0.30000000000000004
System.out.println(sum == 0.3); // false
```

Giá trị `0.3` được viết trong mã nguồn cũng được làm tròn thành một mẫu binary64, nhưng mẫu đó không bằng kết quả binary64 của phép cộng `0.1 + 0.2`. Đây là hành vi dự kiến của số học dấu phẩy động, **không phải lỗi của Java**.

Số `double` có **53 bit độ chính xác phần trị** (kể cả bit ẩn) — khoảng 15–17 chữ số thập phân có nghĩa tùy số cụ thể. Điều đó không có nghĩa mọi số có 15 chữ số thập phân đều được lưu chính xác. Độ chính xác theo chữ số và khả năng biểu diễn đúng một phân số decimal là **hai vấn đề khác nhau**.

### Khi nào sai số này chấp nhận được?

Giả sử một cảm biến cung cấp nhiệt độ khoảng 23.7°C với sai số thiết bị ±0.1°C. Sai khác ở mức vài đơn vị phần cuối nhị phân của `double` thường nhỏ hơn nhiều so với sai số đo; điều quan trọng hơn có thể là hiệu chuẩn và cách tổng hợp số đo.

Trong khi đó, nếu hợp đồng yêu cầu lưu **đúng 0.10 đơn vị tiền** và đối soát đến xu, dùng `double` rồi so sánh trực tiếp từng giá trị thập phân có thể gây rắc rối. Mô hình thập phân chính xác giúp biểu diễn và thực thi quy tắc nghiệp vụ dễ giải thích hơn.

## 4. `BigDecimal` biểu diễn số theo cách nào?

Một `BigDecimal` hữu hạn có thể hiểu bằng công thức:

\[
\text{value}=\text{unscaledValue}\times 10^{-\text{scale}}
\]

Ví dụ, `new BigDecimal("12.3400")` có:

- Giá trị toán học là **12.3400**, bằng 12.34.
- `unscaledValue()` bằng **123400**.
- `scale()` bằng **4**.
- `precision()` bằng **6** (số chữ số trong phần trị nguyên, không phải số chữ số sau dấu chấm).

Với `BigDecimal`, chuỗi thập phân `"0.1"` được biểu diễn **chính xác**. Cộng với `"0.2"` cho giá trị đúng **0.3**, không phải xấp xỉ binary64.

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal sum = a.add(b); // 0.3, biểu diễn chính xác
```

Đây là đoạn mã minh họa đặt **bên trong một phương thức Java**, không phải một chương trình độc lập. `BigDecimal` là lớp bất biến (*immutable*): `add` trả về đối tượng biểu diễn kết quả, không sửa `a` hay `b`.

**Giới hạn quan trọng:** `BigDecimal` không tạo ra “số thực chính xác vô hạn” cho mọi phép toán. Nó biểu diễn chính xác các số thập phân hữu hạn được cung cấp chính xác; phép chia như \(1/3\) cho decimal vô hạn và cần quy tắc làm tròn hoặc số chữ số cho phép.

## 5. Sai lầm kinh điển: `new BigDecimal(0.1)`

Hai cách tạo sau **không tương đương**:

```java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromBinaryDouble = new BigDecimal(0.1);
```

- `"0.1"` là chuỗi decimal chính xác, nên constructor từ `String` tạo giá trị đúng 0.1.
- `0.1` không có hậu tố là literal `double`, đã được làm tròn thành một giá trị binary64 gần 0.1. Constructor `BigDecimal(double)` ghi lại **chính xác giá trị binary64 đó** dưới dạng decimal, nên không bằng 0.1 toán học.

Vì vậy, khi bạn **biết hằng số nghiệp vụ dưới dạng decimal**, nên dùng:

```java
BigDecimal price = new BigDecimal("19.90");
BigDecimal taxRate = new BigDecimal("0.075");
```

Hoặc trong một số trường hợp:

```java
BigDecimal decimal = BigDecimal.valueOf(0.1);
```

`BigDecimal.valueOf(double)` sử dụng biểu diễn thập phân chuẩn từ `Double.toString(double)`. Với literal 0.1, kết quả có giá trị decimal `0.1` như mong muốn; tuy nhiên **không thể lấy lại chữ số gốc bị mất nếu một phép tính `double` đã xảy ra trước đó**.

Ví dụ, `BigDecimal.valueOf(0.1 + 0.2)` cho giá trị decimal **0.30000000000000004**, không tự sửa thành 0.3. Với dữ liệu tiền đọc từ form, file hoặc API, nên parse trực tiếp từ **chuỗi decimal ban đầu**, tránh đi vòng qua `double` rồi mới đổi thành `BigDecimal`.

## 6. Chương trình Java 17+: so sánh trên dữ liệu thực tế

Tạo tệp `DoubleVsBigDecimalDemo.java`:

```java
import java.math.BigDecimal;

public class DoubleVsBigDecimalDemo {
    public static void main(String[] args) {
        double binarySum = 0.1 + 0.2;
        BigDecimal decimalSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));

        System.out.println("double: " + binarySum);
        System.out.println("double == 0.3: " + (binarySum == 0.3));
        System.out.println("decimal: " + decimalSum.toPlainString());

        BigDecimal direct = new BigDecimal(0.1);
        BigDecimal fromText = new BigDecimal("0.1");

        System.out.println("double constructor equals text: "
                + direct.equals(fromText));
        System.out.println("valueOf(0.1): "
                + BigDecimal.valueOf(0.1).toPlainString());
        System.out.println("valueOf(computed double): "
                + BigDecimal.valueOf(binarySum).toPlainString());
        System.out.println("double sum finite: "
                + Double.isFinite(binarySum));
    }
}
```

Biên dịch và chạy:

```bash
javac DoubleVsBigDecimalDemo.java
java DoubleVsBigDecimalDemo
```

Kết quả:

```text
double: 0.30000000000000004
double == 0.3: false
decimal: 0.3
double constructor equals text: false
valueOf(0.1): 0.1
valueOf(computed double): 0.30000000000000004
double sum finite: true
```

Phần cuối là điểm rất đáng nhớ: **`Double.isFinite` trả true không có nghĩa giá trị decimal chính xác**. Nó chỉ kiểm tra số không phải NaN hoặc ±Infinity.

## 7. Số học `BigDecimal`: không dùng trực tiếp `+`, `-`, `*`, `/`

Trong Java, `BigDecimal` là đối tượng, không hỗ trợ nạp chồng các toán tử số học như `+` hay `*` cho hai `BigDecimal`. Dùng các phương thức:

| Phép toán | API | Ghi chú |
|---|---|---|
| Cộng | `a.add(b)` | Kết quả thập phân chính xác nếu không truyền `MathContext` |
| Trừ | `a.subtract(b)` | Tương tự |
| Nhân | `a.multiply(b)` | Tích decimal chính xác khi không giới hạn precision |
| Chia | `a.divide(b)` | Có thể ném `ArithmeticException` nếu thương decimal vô hạn |
| Chia có làm tròn | `a.divide(b, scale, mode)` | Chỉ rõ chữ số thập phân và quy tắc làm tròn |
| Đặt số chữ số thập phân | `a.setScale(scale, mode)` | Có thể cần làm tròn |
| So sánh giá trị | `a.compareTo(b)` | So sánh giá trị số, bỏ qua khác biệt scale |
| Chuyển chính xác thành `long` | `a.longValueExact()` | Ném lỗi nếu có phần lẻ hoặc ngoài miền `long` |

Ví dụ:

```java
BigDecimal subtotal = new BigDecimal("19.90")
        .multiply(BigDecimal.valueOf(3)); // 59.70

BigDecimal discount = subtotal
        .multiply(new BigDecimal("0.075")) // 4.47750
        .setScale(2, java.math.RoundingMode.HALF_UP); // 4.48

BigDecimal total = subtotal.subtract(discount); // 55.22
```

Cần quyết định **khi nào được làm tròn**: làm tròn từng dòng hóa đơn, tổng trước khi làm tròn, hay làm tròn sau khi tính thuế có thể cho kết quả khác nhau. Không có một công thức chung thay thế chính sách tính toán được hợp đồng/quy định chỉ định.

## 8. Chia `BigDecimal` và lựa chọn `RoundingMode`

Một số thương thập phân không biểu diễn hữu hạn. Ví dụ:

\[
\frac{1}{3} = 0.333333333\ldots
\]

Nếu gọi:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = BigDecimal.valueOf(3);
// BigDecimal invalid = one.divide(three);
```

Dòng comment sẽ **ném `ArithmeticException` lúc chạy** do decimal vô hạn và không có quy tắc làm tròn. Không phải mọi phép chia `BigDecimal` tự chạy mãi để tìm “độ chính xác vô hạn”.

Cần chọn số chữ số sau dấu thập phân hoặc `MathContext`:

```java
BigDecimal quotient = BigDecimal.ONE.divide(
        BigDecimal.valueOf(3),
        4,
        java.math.RoundingMode.HALF_UP
); // 0.3333
```

### Scale khác precision

- **Scale**: số vị trí thập phân của giá trị biểu diễn (có thể âm), ví dụ `12.3400` có scale 4.
- **Precision**: số chữ số có nghĩa của phần trị nguyên không chia tỷ lệ, ví dụ `12.3400` có precision 6.
- **`MathContext(5, RoundingMode.HALF_EVEN)`**: giới hạn **5 chữ số có nghĩa**, **không phải 5 chữ số sau dấu thập phân**.

Nhờ vậy một quy tắc `MathContext` có thể phù hợp cho phép tính với độ chính xác tương đối, còn `setScale(2, ...)` thường dễ hiểu khi cần số tiền với hai chữ số lẻ.

### `HALF_UP` và `HALF_EVEN`

Ở một điểm đúng nửa:

- `HALF_UP` làm tròn ra xa 0.
- `HALF_EVEN` làm tròn đến chữ số chẵn gần nhất ở vị trí giữ lại (còn gọi là *banker's rounding*).

Ví dụ decimal **chính xác**:

```java
new BigDecimal("2.345").setScale(2, RoundingMode.HALF_UP);   // 2.35
new BigDecimal("2.345").setScale(2, RoundingMode.HALF_EVEN); // 2.34
new BigDecimal("2.355").setScale(2, RoundingMode.HALF_EVEN); // 2.36
```

Ba dòng là biểu thức minh họa; để biên dịch, đặt trong phương thức có import `java.math.BigDecimal` và `java.math.RoundingMode`, đồng thời gán/sử dụng kết quả. **Đừng tự chọn mode theo cảm tính**: quy tắc làm tròn cần phù hợp yêu cầu sản phẩm và các quy định áp dụng.

## 9. `equals` không giống `compareTo` với `BigDecimal`

Đây là một bẫy API thường gặp:

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.equals(b));  // false
System.out.println(a.compareTo(b)); // 0
```

Hai giá trị **bằng nhau theo toán học**, nhưng khác **scale** (1 so với 2). `BigDecimal.equals` xét cả giá trị phần trị và scale, còn `compareTo` xét quan hệ giá trị số.

Khi cần kiểm tra **hai số tiền có cùng giá trị**, thường nên dùng:

```java
boolean sameAmount = a.compareTo(b) == 0;
```

Khi cần so khớp **cả định dạng/scale** theo hợp đồng dữ liệu, `equals` có thể là chủ đích đúng. Điều này ảnh hưởng đến `HashSet<BigDecimal>` hay `HashMap<BigDecimal, ...>`: chúng dùng `equals`/`hashCode`, nên `2.0` và `2.00` là hai key khác nhau. Một `TreeSet<BigDecimal>` sắp theo so sánh tự nhiên có thể xem hai giá trị đó là tương đương theo thứ tự.

Nếu cần chuẩn hóa scale, có thể dùng `setScale` với quy tắc phù hợp. `stripTrailingZeros()` cũng thay đổi scale; một số giá trị như `1000.00` có thể nhận scale âm sau khi bỏ số 0 không cần thiết, nên không phải phép chuẩn hóa định dạng tùy ý.

### Ví dụ Java 17+: scale, compare và chia

Tạo tệp `BigDecimalRulesDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalRulesDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        System.out.println("equals: " + a.equals(b));
        System.out.println("compareTo: " + a.compareTo(b));
        System.out.println("a scale: " + a.scale());
        System.out.println("b scale: " + b.scale());

        BigDecimal sample = new BigDecimal("12.3400");
        System.out.println("precision: " + sample.precision());
        System.out.println("scale: " + sample.scale());
        System.out.println("stripped: "
                + sample.stripTrailingZeros().toPlainString());

        try {
            System.out.println(
                    BigDecimal.ONE.divide(BigDecimal.valueOf(3)));
        } catch (ArithmeticException e) {
            System.out.println("1/3 without rounding: ArithmeticException");
        }

        System.out.println("1/3 scale 4: "
                + BigDecimal.ONE.divide(
                        BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP));
        System.out.println("half up: " + new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_UP));
        System.out.println("half even: " + new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_EVEN));
    }
}
```

Chạy:

```bash
javac BigDecimalRulesDemo.java
java BigDecimalRulesDemo
```

Kết quả:

```text
equals: false
compareTo: 0
a scale: 1
b scale: 2
precision: 6
scale: 4
stripped: 12.34
1/3 without rounding: ArithmeticException
1/3 scale 4: 0.3333
half up: 2.35
half even: 2.34
```

## 10. Tình huống thực tế: giá, số lượng và chiết khấu

Giả sử một dòng hóa đơn có:

- Đơn giá **19.90**.
- Số lượng **3**.
- Chiết khấu **7.5%**.
- Quy tắc giả định: **làm tròn tiền chiết khấu đến 2 chữ số thập phân theo HALF_UP**.

Đây là **ví dụ quy tắc nghiệp vụ giả định**, không phải quy định thuế/làm tròn áp dụng cho mọi hệ thống. Phương án tính:

1. Thành tiền trước giảm: 19.90 × 3 = 59.70.
2. Tiền giảm chưa làm tròn: 59.70 × 0.075 = 4.47750.
3. Tiền giảm sau làm tròn: 4.48.
4. Số tiền phải trả: 59.70 - 4.48 = 55.22.

Lưu `MoneyCalculationDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyCalculationDemo {
    public static void main(String[] args) {
        BigDecimal unitPrice = new BigDecimal("19.90");
        int quantity = 3;
        BigDecimal discountRate = new BigDecimal("0.075");

        BigDecimal subtotal = unitPrice
                .multiply(BigDecimal.valueOf(quantity));

        BigDecimal discount = subtotal.multiply(discountRate)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = subtotal.subtract(discount)
                .setScale(2, RoundingMode.UNNECESSARY);

        long cents = total.movePointRight(2).longValueExact();

        System.out.println("subtotal: " + subtotal.toPlainString());
        System.out.println("discount: " + discount.toPlainString());
        System.out.println("total: " + total.toPlainString());
        System.out.println("cents: " + cents);
    }
}
```

Chạy:

```bash
javac MoneyCalculationDemo.java
java MoneyCalculationDemo
```

Kết quả:

```text
subtotal: 59.70
discount: 4.48
total: 55.22
cents: 5522
```

`RoundingMode.UNNECESSARY` có nghĩa **không cho phép phải làm tròn** ở bước đặt scale cuối: nếu tổng chưa có thể biểu diễn ở 2 chữ số thập phân mà không mất dữ liệu, bước này ném `ArithmeticException`. Nó hữu ích khi muốn khẳng định một bất biến dữ liệu ở điểm lưu kết quả.

Chuyển sang số xu ở cuối bằng `movePointRight(2).longValueExact()` yêu cầu không có phần lẻ còn lại và không vượt miền `long`. Nếu tiền tệ dùng độ lớn đơn vị thập phân khác, con số 2 cần được thay theo **currency và hợp đồng lưu trữ**, không nên mã hóa cứng trong mọi hệ thống.

## 11. Phương án thứ ba: dùng số nguyên theo đơn vị nhỏ nhất

Nếu tất cả dữ liệu luôn nằm trong một đơn vị cố định, chẳng hạn số tiền có đúng **hai chữ số thập phân**, lưu `long cents` cũng là mô hình có thể **đúng tuyệt đối trong miền số nguyên**:

```java
long unitPriceCents = 1_990L;
int quantity = 3;
long subtotalCents = Math.multiplyExact(
        unitPriceCents, quantity); // 5970 xu
```

Ưu điểm:

- Cộng, trừ và kiểm tra bằng nhau là **phép toán số nguyên chính xác** khi không tràn.
- Không phụ thuộc biểu diễn binary64 của 0.1.
- Dữ liệu lưu truyền có thể rất rõ đơn vị khi được đặt tên `amountInCents` thay vì `amount`.

Rủi ro:

- Nếu có tỷ lệ chiết khấu, thuế hoặc tỷ giá thập phân, vẫn phải đặt quy tắc **nhân/chia và làm tròn**.
- Các đơn vị tiền khác nhau có quy ước số chữ số thập phân khác nhau; “cent” không phải đơn vị chung cho mọi currency.
- `long` có thể tràn; dùng `Math.addExact`/`Math.multiplyExact` hoặc kiểm tra miền phù hợp.
- Nếu nghiệp vụ cần độ chính xác thay đổi hoặc hỗ trợ số decimal với nhiều scale, cách lưu xu cố định có thể quá cứng nhắc.

**Cách kết hợp hợp lý:** dùng `BigDecimal` để tính và làm tròn theo chính sách nghiệp vụ, sau đó chuyển chính xác sang đơn vị nguyên tại ranh giới lưu trữ nếu mô hình đã quy định đơn vị đó. Hoặc sử dụng số nguyên từ đầu nếu hệ thống được thiết kế hoàn toàn bằng đơn vị nhỏ nhất.

## 12. `double` vẫn là lựa chọn rất tốt cho tính toán khoa học

Trong nhiều ứng dụng, dùng `BigDecimal` thay `double` không những không cần thiết mà còn làm thuật toán phức tạp hơn:

- `Math.sin`, `Math.cos`, `Math.sqrt` cùng nhiều API khoa học nhận/trả `double`.
- Các phương pháp phân tích số, mô phỏng và thống kê thường chấp nhận sai số xấp xỉ có kiểm soát.
- `double` có **miền độ lớn rất rộng**, hỗ trợ số dưới chuẩn, ±Infinity, NaN và các quy tắc số học IEEE 754.
- `BigDecimal` có thể tốn tài nguyên hơn do dùng đối tượng và số học thập phân theo độ chính xác yêu cầu.

Nhưng `double` không bảo đảm phép toán có tính chất đại số như số thực toán học với độ chính xác vô hạn. Ví dụ, thứ tự cộng có thể ảnh hưởng đến sai số:

```java
double a = 1.0e16;
double b = -1.0e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
```

Giải thích: `b + c` làm tròn thành `-1.0e16` dưới `double` do số 1 quá nhỏ so với độ lớn của b; sau đó `a + (b+c)` bằng 0.0. Còn `(a+b)+c` cho 1.0. Như vậy với `double`, bạn phải quan tâm **ổn định số**, thứ tự tích lũy, thang giá trị và sai số chứ không chỉ kiểm tra NaN/Infinity.

Khi cộng nhiều mẫu đo, có thể cân nhắc thuật toán cộng ổn định như **Kahan summation** hoặc chiến lược tính phù hợp thay vì tự động chuyển toàn bộ hệ thống sang `BigDecimal`.

## 13. Nhập liệu, JSON, database và hợp đồng API

Kiểu dữ liệu không chỉ là quyết định ở biến Java. Hãy thống nhất **ý nghĩa số tại mọi ranh giới**:

### Nhận dữ liệu từ giao diện hoặc file

Nếu nguồn có chuỗi thập phân chính xác, ví dụ `"123.45"`, hãy đưa trực tiếp vào `BigDecimal`:

```java
String input = "123.45";
BigDecimal amount = new BigDecimal(input);
```

Việc parse trước thành `double` có thể đánh mất biểu diễn decimal mong muốn. Với dữ liệu người dùng, cũng cần kiểm tra chuỗi rỗng, cú pháp, giới hạn độ lớn, scale tối đa và lỗi parse `NumberFormatException`.

### JSON và API

Một token JSON kiểu number như `19.90` thường **không tự mang metadata về đơn vị hoặc chính sách scale**. Tùy thư viện parse, nó có thể đi qua `double` hoặc được đọc trực tiếp thành `BigDecimal`. Đừng giả định mọi thư viện JSON tự bảo toàn độ chính xác decimal hoặc số 0 cuối.

Nếu API tài chính cần giao tiếp ổn định, hãy quy định rõ trường nào là:

- Số thập phân được parse chính xác.
- Số nguyên theo đơn vị nhỏ nhất (kèm currency/unit).
- Số xấp xỉ đo đạc.
- Hoặc chuỗi decimal để tránh mất ý nghĩa khi qua hệ thống khác.

### Database

Cột `DECIMAL`/`NUMERIC` thường phù hợp với `BigDecimal` khi database và ORM có cấu hình precision/scale tương ứng; `DOUBLE`/`FLOAT` thường phù hợp giá trị xấp xỉ. Tuy nhiên, chi tiết phụ thuộc engine, driver và mapping thực tế. Hãy kiểm tra **schema, precision, scale, phép làm tròn ở database và mapping ORM**, không chỉ kiểu Java.

Một hệ thống dùng `BigDecimal` trong code nhưng ghi xuống cột floating-point vẫn có thể mất chính xác; ngược lại, một cột decimal có precision/scale quá hẹp có thể làm tròn hoặc từ chối giá trị.

## 14. So sánh, đồng nhất và giá trị không hợp lệ

### Với `double`

Tránh kỳ vọng `0.1 + 0.2 == 0.3`. Khi so kết quả xấp xỉ, sử dụng tiêu chí sai số phù hợp thang giá trị và ngữ cảnh. Ví dụ kiểm tra gần bằng:

```java
static boolean approximatelyEqual(double a, double b,
                                  double tolerance) {
    if (!Double.isFinite(a) || !Double.isFinite(b)
            || !Double.isFinite(tolerance) || tolerance < 0.0) {
        return false;
    }
    return Math.abs(a - b) <= tolerance;
}
```

Đây là **phương thức minh họa**. Sai số tuyệt đối cố định không phù hợp mọi miền số; ứng dụng cần có thể kết hợp sai số tương đối, so sánh theo ULP hoặc tiêu chí đặc thù. Ngoài ra, phép trừ có thể tràn thành Infinity nếu hai số hữu hạn cực lớn khác dấu, khiến phép thử trên trả false dù ngưỡng không cho phép khác biệt đó; vì vậy phải hiểu giới hạn của chiến lược so sánh.

Với `double` còn cần xử lý **NaN, ±Infinity, -0.0** khi hợp đồng đầu vào cấm hoặc yêu cầu phân biệt các giá trị đặc biệt.

### Với `BigDecimal`

Muốn so giá trị toán học dùng `compareTo`:

```java
BigDecimal a = new BigDecimal("10.0");
BigDecimal b = new BigDecimal("10.00");
boolean sameNumber = a.compareTo(b) == 0; // true
```

`equals` còn xét scale, nên có thể false. `BigDecimal` không có các giá trị NaN hoặc Infinity theo mô hình lớp; `BigDecimal.valueOf(Double.NaN)` không biến nó thành một đối tượng “decimal NaN” mà sẽ thất bại khi chuyển đổi.

Điều này giúp mô hình decimal tránh một lớp lỗi NaN/Infinity, nhưng vẫn cần kiểm tra các lỗi khác như **chia cho 0**, vượt giới hạn nghiệp vụ, hoặc phép chia không kết thúc decimal.

## 15. Hiệu năng và chi phí của sự chính xác

`double` là primitive 64 bit, phù hợp với các cấu trúc dữ liệu mảng primitive, phép tính số học lặp lại và nhiều thư viện tính toán được tối ưu. `BigDecimal` là đối tượng bất biến, thường tạo kết quả mới sau mỗi phép toán và cần xử lý phần trị/scale.

Trong một vòng lặp hàng triệu phép tính, sự khác biệt về cấp phát và CPU **có thể đáng kể**, nhưng không thể kết luận bằng một con số tốc độ chung nếu chưa đo trong ứng dụng cụ thể.

Một cách ra quyết định:

1. Xác nhận loại chính xác mà bài toán cần: **xấp xỉ nhị phân**, **decimal chính xác**, hay **số nguyên theo đơn vị cố định**.
2. Dùng kiểu phù hợp trước, kiểm tra kết quả và trường hợp biên.
3. Nếu hiệu năng là vấn đề, dùng benchmark thực tế (ví dụ JMH) trên đường đi quan trọng, thay vì tối ưu theo phỏng đoán.
4. Đừng đổi kiểu chỉ để đạt hiệu năng nếu nó phá vỡ quy tắc làm tròn hoặc làm sai dữ liệu nghiệp vụ.

Thực tế có thể sử dụng **nhiều mô hình số trong cùng hệ thống**: số tiền giao dịch bằng `BigDecimal`, các tổng hợp số nguyên bằng `long`, các dự báo thống kê bằng `double`. Quan trọng là các bước chuyển đổi giữa mô hình phải rõ ràng và có kiểm tra.

## 16. Những lỗi thiết kế phổ biến

| Lỗi | Vì sao sai/rủi ro | Cách sửa |
|---|---|---|
| “`double` có 15 chữ số nên 0.1 phải chính xác” | 1/10 không có biểu diễn nhị phân hữu hạn | Nhận biết xấp xỉ hoặc dùng decimal khi cần |
| `new BigDecimal(0.1)` để biểu diễn chính xác 0.1 | Tham số là một `double` đã xấp xỉ | Dùng `new BigDecimal("0.1")` |
| Chuyển `double` sai sang `BigDecimal` để “sửa” | Chữ số mất trước đó không được khôi phục | Parse từ nguồn decimal nếu có |
| Gọi `divide` cho 1/3 mà không rounding | Thương decimal vô hạn, có thể ném ngoại lệ | Chọn scale/mode hoặc `MathContext` |
| Dùng `equals` để so giá trị `BigDecimal` | `2.0` và `2.00` khác scale | Dùng `compareTo(...) == 0` nếu so giá trị |
| Đặt `setScale(2)` tùy ý ở mọi bước | Làm tròn sớm có thể đổi số tiền cuối | Quy định điểm làm tròn nghiệp vụ |
| Nghĩ `BigDecimal` có độ chính xác vô hạn miễn phí | Số học đối tượng/decimal có chi phí, phép chia cần chính sách | Chọn precision và yêu cầu đúng |
| Nghĩ `double` finite là số chính xác | Finite chỉ loại NaN/Infinity | Kiểm soát sai số, miền và độ chính xác |
| Dùng đơn vị tiền nhỏ nhất nhưng không ghi đơn vị | Dễ nhầm xu với đơn vị tiền chính | Đặt tên rõ, lưu metadata currency/scale |
| Dùng `long` cho tiền và quên overflow | Số nguyên vẫn có miền hữu hạn | `Math.addExact`, `multiplyExact` hoặc giới hạn |
| Lưu `BigDecimal` trong cột DB `DOUBLE` | Cột đích có thể mất chính xác decimal | Dùng mapping `DECIMAL/NUMERIC` phù hợp |
| Gọi `toString` như cơ chế chuẩn hóa scale | Biểu diễn có thể có số mũ và scale khác | Dùng `toPlainString`/`setScale` theo hợp đồng |
| Dùng decimal cho mọi cảm biến để “bảo đảm đo đúng” | Sai số vật lý của nguồn không tự biến mất | Giữ mô hình đo, sai số và độ chính xác hợp lý |

## 17. Bài tập tự kiểm tra

**Câu 1.** Vì sao `double x = 0.1 + 0.2;` có thể in `0.30000000000000004`?  
**Đáp án:** 0.1 và 0.2 được lưu dưới dạng các giá trị binary64 gần đúng; phép cộng tiếp tục chịu làm tròn nhị phân.

**Câu 2.** Với giá literal decimal `"19.90"`, nên tạo `BigDecimal` thế nào?  
**Đáp án:** `new BigDecimal("19.90")` (hoặc parse trực tiếp chuỗi decimal nguồn).

**Câu 3.** `BigDecimal.valueOf(0.1 + 0.2)` có trở thành 0.3 chính xác không?  
**Đáp án:** Không. Đầu vào đã là `double` 0.30000000000000004, nên phương thức biểu diễn decimal giá trị đó.

**Câu 4.** Vì sao `BigDecimal.ONE.divide(BigDecimal.valueOf(3))` ném ngoại lệ?  
**Đáp án:** 1/3 có biểu diễn thập phân vô hạn; không có quy tắc làm tròn thì không thể tạo một kết quả decimal hữu hạn chính xác.

**Câu 5.** `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` và `compareTo` cho kết quả gì?  
**Đáp án:** `equals` là false, `compareTo` trả 0; các scale khác nhau nhưng giá trị toán học bằng nhau.

**Câu 6.** Hai giá trị được dùng cho mô phỏng vật lý với sai số đo lớn hơn sai số binary64 nhiều lần: nên ưu tiên kiểu nào?  
**Đáp án:** Thường `double` nếu thuật toán/API phù hợp, đồng thời kiểm soát ổn định số và các giá trị đặc biệt.

**Câu 7.** Lưu 59.70 bằng xu trong `long` thì biểu diễn ra sao?  
**Đáp án:** 5970 xu, với giả thiết currency có đúng 100 xu trên một đơn vị chính và miền `long` đủ.

**Câu 8.** `new BigDecimal("12.3400")` có `scale` và `precision` bao nhiêu?  
**Đáp án:** Scale 4, precision 6. Bỏ số 0 cuối có thể thay đổi scale.

**Câu 9.** Khi nào `HALF_UP` khác `HALF_EVEN`?  
**Đáp án:** Ở điểm giữa đúng nửa, ví dụ 2.345 làm tròn hai chữ số: `HALF_UP` cho 2.35, `HALF_EVEN` cho 2.34.

**Câu 10.** Có nên làm tròn mỗi phép nhân/chiết khấu ngay lập tức không?  
**Đáp án:** Chỉ khi chính sách nghiệp vụ yêu cầu; thời điểm làm tròn ảnh hưởng kết quả nên phải được đặc tả và kiểm thử.

**Câu 11.** Lưu `BigDecimal` xuống cột database kiểu `DOUBLE` có bảo đảm chính xác decimal không?  
**Đáp án:** Không; phải kiểm tra kiểu cột, precision/scale và mapping thực tế.

**Câu 12.** Nếu `double` được sử dụng để tính toán khoa học, nên quan tâm điều gì ngoài chuyện `isFinite`?  
**Đáp án:** Sai số biểu diễn, tích lũy sai số, ổn định số của thuật toán, miền dữ liệu và tiêu chí so sánh gần đúng.

## 18. Quy trình chọn kiểu và tài liệu tham khảo

**Bước 1 — xác định bản chất nguồn:** số đo, số đếm nguyên, hay decimal có ý nghĩa pháp lý/nghiệp vụ? Có cần bảo toàn đúng chuỗi decimal đầu vào không?

**Bước 2 — xác định bất biến cần giữ:** giá trị toán học chính xác, độ lớn sai số cho phép, số chữ số thập phân, số chữ số có nghĩa, hoặc đơn vị lưu trữ.

**Bước 3 — xác định phép toán và quy tắc làm tròn:** cộng tổng, chia tỷ lệ, thuế, phần trăm, chuyển đơn vị; làm tròn ở bước nào, theo mode nào, có chấp nhận overflow/NaN không?

**Bước 4 — chọn mô hình:**
- **`double`** nếu bài toán là tính toán xấp xỉ nhị phân có sai số kiểm soát.
- **`BigDecimal`** nếu cần biểu diễn và tính toán decimal theo chính sách chính xác.
- **`long`/`BigInteger`** nếu dữ liệu là số nguyên hoặc đơn vị nhỏ nhất và cần số học nguyên chính xác.

**Bước 5 — kiểm thử tại ranh giới:** decimal có số 0 cuối, 0.1 + 0.2, âm/dương, các giá trị cực lớn, chia không kết thúc, nhiều bước làm tròn, chuyển kiểu, serialization, database và overflow.

**Ghi nhớ:** Không chọn `BigDecimal` chỉ vì `double` có sai số; hãy chọn nó khi **đòi hỏi của dữ liệu là decimal chính xác và cần kiểm soát làm tròn**. Không chọn `double` cho số tiền chỉ vì cú pháp thuận tiện nếu hợp đồng cần bảo toàn thập phân. Và nếu dữ liệu thực sự là số nguyên theo đơn vị cố định, `long` cùng kiểm tra tràn có thể là mô hình đơn giản nhất.

Tài liệu tham khảo chính thức:
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [Java SE 17 — MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html)
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html)
- [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numerical-error-and-stability-010">Kiểm chứng thuật toán bằng dữ liệu biên và kết quả tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng thuật toán bằng dữ liệu biên và kết quả tham chiếu trong Java

## 1. Vì sao kết quả “trông hợp lý” chưa chứng minh thuật toán đúng?

Với thuật toán số học, một vài kết quả đúng trên dữ liệu thông thường không đủ chứng minh thuật toán đúng. Lỗi có thể chỉ xuất hiện khi **đầu vào chạm giới hạn kiểu**, hai số chênh lệch độ lớn rất lớn, dữ liệu có dấu âm, xuất hiện `NaN`/vô cực hoặc nhiều phép tính làm tròn liên tiếp.

Ví dụ:

```java
int a = 50_000;
int b = 50_000;

long wrong = a * b;          // -1794967296: phép nhân int tràn trước khi gán.
long right = (long) a * b;   // 2500000000: phép nhân long.
```

Một bài test chỉ dùng `a = 2, b = 3` không thể phát hiện lỗi trên. Thuật toán cộng số thực có thể tương tự: nhiều trường hợp nhỏ đúng, nhưng `1e16 + 1.0` ở kiểu `double` được làm tròn về `1e16`.

Để đánh giá tin cậy, cần kết hợp **hai nguồn chứng cứ**:

1. **Dữ liệu biên (*boundary and adversarial cases*):** cố tình tạo tình huống khó, sát giới hạn hay dễ bộc lộ lỗi.
2. **Kết quả tham chiếu (*reference result, test oracle*):** xác định kết quả kỳ vọng bằng một cách đủ độc lập, đủ chính xác và phù hợp với hợp đồng của thuật toán.

Bài này tập trung vào kiểm chứng các phép tính số trong Java Core: số nguyên cố định, số thực `float`/`double`, độ ổn định số học và chiến lược đối chiếu. Mục tiêu không chỉ viết `assert`, mà còn biết **đang khẳng định điều gì, căn cứ từ đâu và sai số nào được chấp nhận**.

## 2. Trước tiên phải định nghĩa “đúng” là gì

Một thuật toán có thể đúng theo nhiều hợp đồng khác nhau:

- **Số nguyên toán học:** cần kết quả chính xác không giới hạn về mặt ý nghĩa, có thể dùng `BigInteger` làm tham chiếu.
- **Số nguyên có độ rộng cố định:** chương trình có thể chủ đích dùng modulo `2^32`/`2^64`, hoặc phải báo lỗi khi tràn. Cần xác định kỳ vọng, không mặc định rằng mọi wraparound đều là bug.
- **Số thực IEEE 754:** chấp nhận sai số làm tròn theo một mô hình đã định nghĩa. Kiểm chứng phải xét độ lớn, độ chính xác và giá trị đặc biệt.
- **Thập phân chính xác:** ví dụ số tiền hoặc số đo được quy định chính xác đến một số chữ số, có thể cần `BigDecimal` và quy tắc `RoundingMode`.
- **Thuật toán xấp xỉ:** kết quả được chấp nhận nếu thỏa sai số tuyệt đối/tương đối, khoảng tin cậy hoặc giới hạn sai số riêng của miền ứng dụng.

Một phép toán `int` cho kết quả âm do tràn có thể **đúng theo số học modulo** nhưng **sai trong nghiệp vụ tính doanh thu**. Một kết quả `double` lệch vài ULP có thể **chấp nhận được** trong bài toán mô phỏng nhưng **không đạt yêu cầu** nếu hợp đồng phải chính xác đến đơn vị nhỏ nhất.

Vì vậy, trước khi tạo testcase, ghi rõ:

| Phần hợp đồng | Câu hỏi cần trả lời |
| --- | --- |
| Miền đầu vào | Cho phép số âm, zero, `NaN`, vô cực, giá trị ngoài miền không? |
| Miền đầu ra | Kết quả có phải hữu hạn, không âm, trong một khoảng hay đúng kiểu không? |
| Tràn và ngoại lệ | Tràn cần báo lỗi, bão hòa (*saturate*) hay modulo? Chia cho 0 xử lý ra sao? |
| Độ chính xác | Cần chính xác tuyệt đối, làm tròn đến chữ số nào hay giới hạn sai số nào? |
| Tính ổn định | Sai số có tăng quá lớn khi thay đổi nhẹ dữ liệu hoặc thứ tự tính không? |
| Điều kiện tiên quyết | Mảng rỗng, `null`, dữ liệu chưa sắp xếp có được phép không? |

**Test oracle phải xuất phát từ hợp đồng**, không phải từ kết quả đang được tạo ra bởi chính hàm cần kiểm thử.

## 3. Thiết kế bộ dữ liệu biên cho các phép tính số

Không chỉ kiểm tra `0` và `MAX_VALUE`. Với thuật toán số, cần kiểm tra **giá trị ngay bên dưới, tại và ngay bên trên các ngưỡng** cùng những tổ hợp dễ gây mất ổn định.

| Nhóm dữ liệu | Ví dụ đầu vào | Lỗi/rủi ro muốn phát hiện |
| --- | --- | --- |
| Zero và dấu | `0`, `-1`, `1`, `+0.0`, `-0.0` | Chia 0, quy tắc dấu, xử lý signed zero |
| Cận số nguyên | `Integer.MIN_VALUE`, `Integer.MAX_VALUE`, giá trị sát cận | Tràn cộng/nhân, `Math.abs(MIN_VALUE)` |
| Cận `long` | `Long.MIN_VALUE`, `Long.MAX_VALUE` | Tràn 64 bit và cast hẹp |
| Giới hạn chính xác `float` | `2^24` và số nguyên kề | Mất khả năng phân biệt bước `1` |
| Giới hạn chính xác `double` | `2^53` và số nguyên kề | Làm tròn số nguyên lớn |
| Khác biệt độ lớn | `1e16 + 1.0` | Số hạng nhỏ bị nuốt |
| Triệt tiêu | `(1e16 + 1.0) - 1e16` | Mất thông tin trước khi trừ |
| Cực trị số thực | `Double.MAX_VALUE`, `Double.MIN_NORMAL`, `Double.MIN_VALUE` | Overflow, subnormal, underflow |
| Giá trị đặc biệt | `Double.NaN`, `Double.POSITIVE_INFINITY`, `-0.0` | So sánh, kiểm tra finite, hợp đồng giá trị |
| Dữ liệu tổ hợp | Mảng rỗng, một phần tử, nhiều số cùng dấu, số khác dấu xen kẽ | Sai chỉ số, trung bình sai, độ ổn định của phép tổng |
| Đầu vào không hợp lệ | Mẫu số 0, độ dài âm, số vượt miền | Ngoại lệ đúng loại, không nuốt lỗi |

Đối với mỗi ranh giới `B`, một mẫu cơ bản là thử **`B-1`, `B`, `B+1`**, nhưng phải tạo dữ liệu bằng kiểu **đủ rộng**; đừng để chính code tạo test bị tràn trước khi test chạy:

```java
long below = (long) Integer.MAX_VALUE - 1;
long at = Integer.MAX_VALUE;
long above = (long) Integer.MAX_VALUE + 1;
```

Đối với số thực, `x - 1`/`x + 1` không phải lúc nào cũng là hai giá trị kề nhau. Dùng `Math.nextDown(x)`, `Math.nextUp(x)`, `Math.ulp(x)` khi muốn kiểm tra ranh giới theo độ phân giải của `double`.

### Các testcase đối nghịch đặc biệt hữu ích

- `Integer.MAX_VALUE + 1` và `Long.MAX_VALUE + 1L`: quan sát tràn số nguyên.
- `Integer.MIN_VALUE / -1`: trường hợp chia nguyên đặc biệt; ở kiểu `int` kết quả là `Integer.MIN_VALUE` do thương toán học không nằm trong miền.
- `0 / 0` theo số nguyên: phải ném `ArithmeticException`; `0.0 / 0.0` theo số thực: là `NaN`.
- `1e16 + 1.0` và `1e16 + 2.0`: phân biệt thay đổi bị làm tròn và thay đổi giữ được.
- `Double.MIN_VALUE / 2.0`: phép toán nhỏ hơn số `double` dương nhỏ nhất, làm tròn thành `0.0`.
- `Double.MAX_VALUE * 2.0`: kết quả là `Infinity`.
- Hai số thực sát nhau quanh mốc cần so sánh: dùng `Math.nextUp` thay vì cộng một epsilon bất kỳ.

Đừng coi số lượng trường hợp càng nhiều là đủ. Một bộ dữ liệu nhỏ nhưng có lý do chọn từng trường hợp thường hiệu quả hơn hàng nghìn đầu vào ngẫu nhiên chỉ quanh giá trị trung bình.

## 4. Xây dựng kết quả tham chiếu đáng tin cậy

**Kết quả tham chiếu** phải được tạo bằng phương pháp có độ tin cậy cao hơn, đủ khác biệt so với code đang kiểm thử để không vô tình lặp lại cùng một lỗi.

### 4.1. `BigInteger` làm oracle cho số nguyên toán học

Nếu thuật toán tính toán các giá trị nguyên có thể vượt 32/64 bit, dùng `java.math.BigInteger`:

```java
import java.math.BigInteger;

int a = 50_000;
int b = 50_000;

BigInteger expected = BigInteger.valueOf(a)
        .multiply(BigInteger.valueOf(b));

System.out.println(expected); // 2500000000
```

Không làm phép nhân nguyên thủy **trước khi** đưa kết quả vào `BigInteger`:

```java
// Sai oracle: phép nhân int có thể tràn trước.
BigInteger wrongReference = BigInteger.valueOf(a * b);
```

Tương tự, muốn kiểm tra phép cộng `long` ở biên, dùng `BigInteger.valueOf(x).add(BigInteger.valueOf(y))`, không dùng `BigInteger.valueOf(x + y)`. Nếu hợp đồng cho phép modulo 32/64 bit, kết quả BigInteger phải được đối chiếu sau khi áp dụng **chính phép quy đổi modulo** mà hợp đồng yêu cầu.

Với hợp đồng “không được tràn”, thay vì so sánh kết quả đã wraparound, hãy xác nhận `Math.addExact`, `Math.multiplyExact` hoặc logic kiểm tra biên phát ra `ArithmeticException` đúng trường hợp.

### 4.2. `BigDecimal` làm oracle cho đầu vào thập phân

Giả sử hợp đồng đầu vào là **mười lần giá trị thập phân đúng `0.1`**:

```java
import java.math.BigDecimal;

BigDecimal reference = new BigDecimal("0.1")
        .multiply(BigDecimal.TEN);

System.out.println(reference.toPlainString()); // 1.0
```

Điểm quan trọng là **định nghĩa đầu vào**. Có hai cách làm tham chiếu khác nhau:

1. **Tham chiếu thập phân ý định:** `new BigDecimal("0.1")` biểu diễn đúng phân số thập phân `1/10`.
2. **Tham chiếu đúng mẫu bit `double` đã nhận:** `new BigDecimal(0.1)` biểu diễn chính xác giá trị **nhị phân xấp xỉ** đang lưu ở `double`. Nó khác `1/10` về mặt toán học.

Trong Java, `BigDecimal.valueOf(0.1)` dùng cách chuyển `double` thành chuỗi thập phân ngắn, thường cho `0.1`. Nhưng khi mục đích là tính **giá trị chính xác của mẫu bit `double`**, `new BigDecimal(x)` mới là lựa chọn phù hợp. Phải chọn oracle theo loại đầu vào thực tế, không dùng một cách xây dựng `BigDecimal` cho mọi bài toán.

Đối với các phép chia thập phân có khai triển vô hạn như `1/3`, hãy chọn `MathContext` hoặc `RoundingMode` và scale theo hợp đồng; nếu không, `BigDecimal.divide` có thể ném `ArithmeticException`. Oracle không được “chính xác vô hạn” nếu bài toán thực sự yêu cầu làm tròn đến hai chữ số: khi đó quy tắc làm tròn **là một phần kết quả mong đợi**.

### 4.3. Dùng một cài đặt độc lập hoặc bảng kết quả đã kiểm chứng

Với thuật toán có công thức phức tạp, tham chiếu có thể là:

- Một cách triển khai đơn giản, rõ ràng hơn nhưng chậm hơn (ví dụ `O(n^2)` cho kích thước test nhỏ).
- Giá trị tính ở độ chính xác cao hơn với mô hình số phù hợp.
- Bộ trường hợp chuẩn có giá trị đã được kiểm tra thủ công hoặc từ tài liệu đáng tin.
- Một phép tính độc lập từ thư viện/thuật toán khác, với **hợp đồng, miền giá trị và quy tắc làm tròn tương thích**.

**Không dùng chính kết quả của hàm đang test làm “expected”** rồi kiểm tra nó bằng một phép biến đổi tương đương. Hai triển khai chia sẻ cùng phép tính lỗi có thể cùng tạo một kết quả sai; tính độc lập của oracle là phần quan trọng của kiểm chứng.

## 5. So sánh actual với expected: kiểm tra chính xác hay gần đúng?

### Với số nguyên, kiểu liệt kê hoặc trạng thái

Nếu hợp đồng yêu cầu **chính xác**, so sánh trực tiếp:

```java
long actual = 2_500_000_000L;
long expected = 2_500_000_000L;

if (actual != expected) {
    throw new AssertionError("Wrong integer result");
}
```

Đối với `BigInteger`/`BigDecimal`, dùng API so sánh phù hợp:

- `BigInteger.equals` kiểm tra bằng nhau theo giá trị số nguyên.
- `BigDecimal.compareTo` so sánh giá trị toán học; `BigDecimal.equals` còn yêu cầu **cùng scale**. Ví dụ `1.0` và `1.00` có `compareTo == 0` nhưng `equals == false`.

### Với `float`/`double`, sai số tuyệt đối và tương đối

So sánh `actual == expected` có thể không phù hợp nếu kết quả trải qua những bước làm tròn khác nhau. Một chính sách thường gặp là:

```text
|actual - expected| <= max(absTol, relTol * max(|actual|, |expected|))
```

Ở đây:

- `absTol` là **ngưỡng sai số tuyệt đối**, hữu ích gần zero.
- `relTol` là **ngưỡng sai số tương đối**, hữu ích khi độ lớn thay đổi.
- Cả hai ngưỡng phải bắt nguồn từ yêu cầu mô hình, đơn vị đo và độ chính xác cần đạt.

Ví dụ Java:

```java
static boolean nearlyEqual(double actual, double expected,
                           double absTol, double relTol) {
    if (!(Double.isFinite(actual) && Double.isFinite(expected))) {
        return false;
    }
    if (!(Double.isFinite(absTol) && Double.isFinite(relTol))
            || absTol < 0.0 || relTol < 0.0) {
        throw new IllegalArgumentException("Invalid tolerances");
    }

    double diff = Math.abs(actual - expected);
    double scale = Math.max(Math.abs(actual), Math.abs(expected));
    return diff <= Math.max(absTol, relTol * scale);
}
```

Đây là **hàm minh họa cho phép so sánh hữu hạn trong miền số bình thường**, không phải bộ so sánh tổng quát hoàn hảo. Phép trừ `actual - expected` có thể tràn thành vô cực với hai giá trị hữu hạn trái dấu cực lớn; phép nhân `relTol * scale` cũng có thể gặp các tình huống đặc biệt. Với tính toán đòi hỏi độ tin cậy cao, hãy chọn và kiểm chứng cách tính sai số tương ứng miền dữ liệu.

**Không tự động dùng `1e-6` hoặc `1e-12` cho mọi thuật toán**. Ngưỡng quá chặt có thể đánh rớt kết quả hợp lệ; ngưỡng quá lỏng có thể bỏ sót lỗi nghiêm trọng. Gắn ngưỡng với độ phân giải đo, độ lớn kết quả, số phép tính và sai số mà bài toán cho phép.

### NaN, vô cực và signed zero cần testcase riêng

```java
System.out.println(Double.isNaN(0.0 / 0.0));         // true
System.out.println(Double.isInfinite(1.0 / 0.0));    // true
System.out.println(+0.0 == -0.0);                   // true
System.out.println(Double.compare(+0.0, -0.0) == 0); // false
```

- `NaN` không bằng chính nó qua toán tử nguyên thủy `==`, nhưng `Double.equals` xem các NaN cùng kiểu bằng nhau. Nếu hợp đồng **cấm NaN**, hãy kiểm tra `Double.isFinite` thay vì so sánh gần đúng.
- `Infinity` có thể là kết quả đúng của phép chia số thực cho zero theo IEEE 754, hoặc là dấu hiệu đầu vào/lỗi số học không chấp nhận được theo nghiệp vụ.
- `+0.0` và `-0.0` bằng nhau theo `==` nhưng khác bit dấu. Nếu dấu zero là một phần hợp đồng (ví dụ quan sát qua phép nghịch đảo), kiểm tra `Double.doubleToRawLongBits` hoặc `Double.compare` có chủ đích.

Trong kiểm thử, không được “chữa” `NaN` bằng cách đặt `tolerance` lớn: phải định nghĩa nó là hợp lệ hay lỗi riêng biệt.

## 6. Kiểm thử thuộc tính và quan hệ biến đổi (*property/metamorphic testing*)

Không phải phép tính nào cũng có oracle độc lập dễ xây dựng. Có thể kiểm tra những **bất biến đã chứng minh được trong miền hợp lệ**, thay vì chỉ đối chiếu một con số mẫu.

Ví dụ cho số nguyên và mảng:

- Nếu một hàm đếm số phần tử, `count(empty) == 0` và thêm một phần tử tăng kết quả đúng `1`, miễn không vượt miền kiểu.
- Nếu thuật toán tìm giá trị nhỏ nhất của tập không rỗng, kết quả phải là một phần tử trong tập và không lớn hơn bất kỳ phần tử nào.
- Nếu một phép mã hóa rồi giải mã được thiết kế không mất dữ liệu, kiểm tra `decode(encode(x)).equals(x)` cho mọi `x` hợp lệ.
- Đối với thuật toán tổng, nếu **tính theo số nguyên chính xác không tràn**, đổi thứ tự phần tử không làm thay đổi tổng.
- Với thuật toán số thực, việc đổi thứ tự cộng có thể làm thay đổi kết quả vì phép cộng dấu phẩy động **không kết hợp**; chỉ kiểm tra một bất biến gần đúng khi đã xác lập mức sai số hợp lý.

**Không áp dụng bất biến toán học lý tưởng một cách máy móc cho số thực máy.** Ví dụ `(a+b)+c == a+(b+c)` không phải một thuộc tính đúng chung của phép cộng `double`. `x - x == 0` cũng cần điều kiện `x` hữu hạn; với `Infinity` thì phép trừ cho `NaN`.

Một số quan hệ biến đổi hữu ích khi đã chứng minh miền áp dụng:

1. **Thêm zero:** `sum(values plus 0)` không nên thay đổi giá trị toán học; nhưng với dữ liệu có signed zero, cách biểu diễn dấu có thể thay đổi theo thứ tự/cách cộng.
2. **Đối chiếu công thức độc lập:** với `long` không tràn và `divisor != 0` (trừ trường hợp đặc biệt `MIN_VALUE / -1` khi xét theo số học toán học), quan hệ `dividend = divisor * quotient + remainder` giúp đối chiếu chia và lấy dư.
3. **Kiểm tra miền kết quả:** tỷ lệ xác suất hợp lệ phải nằm trong `0..1` nếu hợp đồng quy định; tuy nhiên giá trị dấu phẩy động sát biên có thể cần ngưỡng hoặc chiến lược chặn được đặc tả.
4. **Kiểm tra tính đơn điệu:** một hàm được chứng minh tăng theo đầu vào trong miền xác định không được giảm bất thường, trừ trường hợp độ chính xác hoặc overflow mà hợp đồng xử lý riêng.

Các bất biến tốt phải có **điều kiện tiên quyết rõ ràng**. Test không đạt có thể chỉ ra lỗi thuật toán, nhưng cũng có thể chỉ ra rằng bất biến bạn viết không đúng trong mô hình số học thực tế.

## 7. Kiểm thử độ ổn định số học: điều kiện và thuật toán

Một thuật toán có thể đúng về công thức toán học nhưng **không ổn định về cách tính** trong dấu phẩy động.

Ví dụ tính `a + b + c` với các số rất khác độ lớn:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
```

Cả ba toán hạng đều là giá trị `double` hợp lệ. Sự khác nhau đến từ **làm tròn các biểu thức trung gian**. Trước khi tối ưu hoặc thay đổi thứ tự phép cộng, kiểm tra:

- Thứ tự tính có làm thay đổi kết quả vượt mức sai số được chấp nhận không?
- Có số lớn trái dấu dễ triệt tiêu và làm lộ sai số tương đối không?
- Có nhiều số hạng nhỏ bị “nuốt” khi cộng vào tổng quá lớn không?
- Bài toán có điều kiện kém (*ill-conditioned*), khiến nhiễu rất nhỏ ở input dẫn tới thay đổi lớn ở output, dù thuật toán đã ổn định hay không?

**Conditioning** mô tả độ nhạy của **bài toán toán học** với biến đổi đầu vào. **Numerical stability** mô tả thuật toán triển khai có khuếch đại sai số tính toán quá mức hay không. Hai khái niệm liên quan nhưng không đồng nghĩa: nếu bản thân bài toán rất nhạy, một thuật toán tốt vẫn khó cho kết quả tuyệt đối chính xác với dữ liệu đầu vào bị nhiễu.

Ví dụ cụ thể của tình huống “số nhỏ bị nuốt”:

```java
double naive = 1e16;
for (int i = 0; i < 10; i++) {
    naive += 1.0;
}
System.out.println(naive - 1e16); // 0.0
```

Tổng toán học đáng lẽ tăng `10.0`. Các cách thay thế như **cộng nhóm những số hạng nhỏ trước**, pairwise summation, Kahan/Neumaier compensated summation có thể giảm sai số cho nhiều bộ dữ liệu, nhưng không bảo đảm kết quả tuyệt đối chính xác trong mọi trường hợp.

Cần thiết kế testcase riêng để **chứng minh tính hữu ích của một kỹ thuật ổn định** đối với dữ liệu đặc trưng, sau đó đối chiếu với oracle có độ chính xác cao hơn.

## 8. Test theo ranh giới, phân hoạch tương đương và dữ liệu ngẫu nhiên có tái lập

Một bộ test có giá trị thường kết hợp ba lớp:

### Dữ liệu biên có chủ đích

Dùng một bảng nhỏ ghi rõ input, oracle và lý do:

| ID | Input | Kết quả kỳ vọng | Mục đích |
| --- | --- | --- | --- |
| INT-01 | `Integer.MAX_VALUE + 1` theo phép cộng `int` | `Integer.MIN_VALUE` nếu hợp đồng wraparound | Kiểm tra dấu/tràn |
| INT-02 | `Math.addExact(Integer.MAX_VALUE, 1)` | `ArithmeticException` | Kiểm tra báo tràn |
| DEC-01 | Mười giá trị thập phân `"0.1"` | `BigDecimal("1.0")` | Oracle thập phân chính xác |
| FP-01 | `1e16 + 1.0` | Cùng mẫu `double` với `1e16` | Độ phân giải/ULP |
| FP-02 | `Double.MIN_VALUE / 2.0` | `0.0` | Underflow |
| FP-03 | `Double.MAX_VALUE * 2.0` | `+Infinity` | Overflow số thực |
| FP-04 | `0.0 / 0.0` | `NaN` | Giá trị đặc biệt |
| FP-05 | So sánh `+0.0` với `-0.0` | `==` cho true, so bit cho false | Signed zero |

### Phân hoạch tương đương

Chia miền đầu vào thành nhóm có ý nghĩa: số âm/dương, zero, khoảng nhỏ/lớn, hữu hạn/không hữu hạn, có/không có nguy cơ tràn, dữ liệu đúng/sai miền. Trong mỗi nhóm lấy một số đại diện, sau đó bổ sung các giá trị sát biên. Việc phân hoạch giúp tránh chỉ test các số dương nhỏ dễ tính.

### Randomized/property-based testing

Sinh thêm dữ liệu có **seed cố định** để tái lập:

```java
java.util.Random random = new java.util.Random(2026L);
for (int i = 0; i < 1_000; i++) {
    int x = random.nextInt();
    int y = random.nextInt();
    // Đối chiếu thuật toán bằng oracle độc lập phù hợp.
}
```

Nếu một lần kiểm thử ngẫu nhiên thất bại, lưu **seed, input chính xác, thứ tự sinh, môi trường và thông tin lỗi** để tái tạo. Đừng chỉ lưu “1000 testcase đều pass”. Ngoài random đều trên một miền, nên phát sinh có chủ đích các số biên vì xác suất ngẫu nhiên “trúng” giá trị như `Integer.MIN_VALUE` hoặc đúng ranh giới ULP rất nhỏ.

Với test đặc biệt của `double`, có thể sinh các mẫu bit bằng `Double.longBitsToDouble(random.nextLong())`, nhưng cần phân nhóm đầu vào hữu hạn/`NaN`/vô cực và xử lý theo hợp đồng thay vì ép tất cả vào một phép so sánh xấp xỉ.

## 9. Những lỗi ngay trong code kiểm thử

**Kiểm thử cũng có thể sai.** Những bẫy phổ biến:

1. **Oracle bị tràn cùng kiểu:** `BigInteger.valueOf(a * b)` nhận kết quả `int` đã tràn. Chuyển từng toán hạng trước khi nhân.
2. **Oracle cùng thuật toán với code test:** nếu cùng sai công thức, kết quả trùng nhau không phát hiện được bug.
3. **So sánh `double` bằng `==` ở mọi trường hợp:** lỗi làm tròn vô hại bị coi là thất bại; ngược lại, phép so sánh gần đúng quá rộng có thể che bug.
4. **Chỉ test dữ liệu ngẫu nhiên bình thường:** bỏ sót ngưỡng `MAX_VALUE`, ranh giới bit chính xác, zero và giá trị đặc biệt.
5. **Không xét NaN:** trong so sánh điều kiện, `NaN` có thể khiến cả `>`, `<` và `==` đều false, làm đoạn assert thủ công bị lọt điều kiện.
6. **Nhầm `Double.MIN_VALUE` với số âm nhỏ nhất:** nó là **số dương subnormal nhỏ nhất lớn hơn 0**, không phải giá trị âm lớn về độ lớn; số âm có trị tuyệt đối lớn nhất là `-Double.MAX_VALUE`.
7. **Đổi `double` đã lỗi sang `BigDecimal` rồi kỳ vọng sửa dữ liệu:** một khi làm tròn/mất thông tin đã xảy ra, phép chuyển kiểu không khôi phục nó.
8. **Dùng một epsilon cho mọi quy mô:** mất cân đối giữa kết quả gần zero và kết quả rất lớn.
9. **Không kiểm thử ngoại lệ:** đối với phép chia cho 0 hoặc overflow được thiết kế để báo lỗi, chính loại ngoại lệ là expected result.
10. **Dựa vào thời gian chạy hoặc thứ tự lặp collection không được đảm bảo:** các yếu tố này không nên là oracle cho giá trị số học nếu hợp đồng không quy định.
11. **Chỉ test kết quả cuối mà bỏ qua bất biến:** thuật toán có thể cho đúng một số đầu vào nhưng vi phạm miền kết quả hoặc tính đơn điệu.
12. **Sử dụng `assert` Java nhưng không bật nó khi chạy:** câu lệnh `assert` bị tắt theo mặc định trong nhiều môi trường; cần `java -ea` hoặc một framework kiểm thử phù hợp. `throw new AssertionError(...)` không phụ thuộc cờ `-ea`.

## 10. Ví dụ Java 17+ chạy độc lập, có oracle và dữ liệu biên

Tạo tệp **`NumericalBoundaryReferenceDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumericalBoundaryReferenceDemo {
    static boolean nearlyEqual(double actual, double expected,
                               double absTol, double relTol) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) {
            return false;
        }
        if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
                || absTol < 0.0 || relTol < 0.0) {
            throw new IllegalArgumentException("Invalid tolerance");
        }

        double diff = Math.abs(actual - expected);
        double scale = Math.max(Math.abs(actual), Math.abs(expected));
        return diff <= Math.max(absTol, relTol * scale);
    }

    public static void main(String[] args) {
        double sum = 0.0;
        for (int i = 0; i < 10; i++) {
            sum += 0.1;
        }

        BigDecimal decimalReference =
                new BigDecimal("0.1").multiply(BigDecimal.TEN);

        System.out.println("double sum = " + sum);
        System.out.println("decimal reference = "
                + decimalReference.toPlainString());
        System.out.println("strict comparison = "
                + (sum == decimalReference.doubleValue()));
        System.out.println("tolerant comparison = "
                + nearlyEqual(sum, decimalReference.doubleValue(),
                              1e-12, 1e-12));

        int max = Integer.MAX_VALUE;
        System.out.println("int wrap = "
                + (max + 1 == Integer.MIN_VALUE));
        BigInteger exactInteger = BigInteger.valueOf(max)
                .add(BigInteger.ONE);
        System.out.println("BigInteger reference = " + exactInteger);

        try {
            Math.addExact(max, 1);
            System.out.println("unexpected: no overflow");
        } catch (ArithmeticException ex) {
            System.out.println("checked overflow detected");
        }

        double huge = 1e16;
        System.out.println("small increment lost = "
                + (huge + 1.0 == huge));
        System.out.println("ULP at 1e16 = " + Math.ulp(huge));
        System.out.println("BigDecimal exact increment = "
                + new BigDecimal("10000000000000000")
                    .add(BigDecimal.ONE).toPlainString());

        System.out.println("underflow to zero = "
                + (Double.MIN_VALUE / 2.0 == 0.0));
        System.out.println("overflow to infinity = "
                + Double.isInfinite(Double.MAX_VALUE * 2.0));
        System.out.println("NaN detected = "
                + Double.isNaN(0.0 / 0.0));
        System.out.println("signed zero primitive equal = "
                + (+0.0 == -0.0));
        System.out.println("signed zero raw bits equal = "
                + (Double.doubleToRawLongBits(+0.0)
                == Double.doubleToRawLongBits(-0.0)));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac NumericalBoundaryReferenceDemo.java
java NumericalBoundaryReferenceDemo
```

Kết quả dự kiến:

```text
double sum = 0.9999999999999999
decimal reference = 1.0
strict comparison = false
tolerant comparison = true
int wrap = true
BigInteger reference = 2147483648
checked overflow detected
small increment lost = true
ULP at 1e16 = 2.0
BigDecimal exact increment = 10000000000000001
underflow to zero = true
overflow to infinity = true
NaN detected = true
signed zero primitive equal = true
signed zero raw bits equal = false
```

Các nhóm testcase minh họa **những hợp đồng khác nhau**: `BigInteger` và `BigDecimal` làm tham chiếu chính xác, `Math.addExact` xác nhận hành vi báo tràn, kiểm tra gần đúng cho phép cộng `double`, và kiểm tra riêng các giá trị đặc biệt.

**Lưu ý về kiểm chứng:** đầu ra trên được trình bày để đối chiếu có chủ đích. Trong dự án thật, nên chuyển các trường hợp thành các phép assert có tên, chạy tự động theo CI và báo rõ input/expected/actual mỗi khi sai. Khi thay đổi thuật toán để cải thiện ổn định số học, giữ lại các trường hợp biên làm **regression tests** để ngăn lỗi quay lại.

## 11. Checklist kiểm chứng trước khi chấp nhận một thuật toán số

1. **Hợp đồng:** viết rõ miền đầu vào, cách làm tròn, kiểu dữ liệu, ngoại lệ và sai số cho phép.
2. **Oracle:** chọn giá trị tham chiếu độc lập và đúng mô hình đầu vào; tránh overflow trong quá trình tạo expected.
3. **Miền biên:** có zero, giá trị âm, cực trị kiểu, ngay quanh ngưỡng và các tổ hợp lớn–nhỏ/trái dấu.
4. **Số thực đặc biệt:** quyết định hành vi với `NaN`, vô cực, signed zero, subnormal, overflow và underflow.
5. **Cách so sánh:** dùng equality chính xác khi cần; dùng ngưỡng tuyệt đối/tương đối/ULP có căn cứ khi chấp nhận sai số.
6. **Tính ổn định:** test những dữ liệu dễ gây triệt tiêu, mất số hạng nhỏ, tích lũy sai số hoặc độ nhạy cao.
7. **Thuộc tính:** kiểm tra bất biến và quan hệ biến đổi đúng trong **miền thực thi Java**, không ngầm giả định mọi định luật đại số số thực vẫn đúng.
8. **Tái lập:** ghi seed, input và môi trường khi test ngẫu nhiên; lưu regression cases sau khi sửa lỗi.
9. **Tự động hóa:** dùng JUnit hoặc cơ chế kiểm tra rõ ràng; chạy lại cùng bộ test trong pipeline khi chỉnh code.
10. **Giải thích lỗi:** mỗi lần test thất bại nên cho biết sai lệch giá trị, ngưỡng, quy tắc và ý nghĩa nghiệp vụ, không chỉ báo “expected != actual”.

**Kết luận:** Kiểm chứng thuật toán số không thể chỉ dựa vào vài ví dụ “đẹp”. Hãy phối hợp **dữ liệu biên được chọn có chủ đích**, **kết quả tham chiếu đủ độc lập/chính xác** và **quy tắc so sánh tương ứng với hợp đồng**. Trong Java, `BigInteger`/`BigDecimal`, `Math.*Exact`, `Math.ulp` và các phép kiểm tra `isFinite`/`isNaN` giúp phát hiện những lỗi chỉ xuất hiện tại ranh giới độ rộng kiểu và độ chính xác dấu phẩy động.

**Tài liệu tham khảo:** [Java Language Specification §4.2 — Primitive Types and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2), [JLS §15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.17), [Math API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html), [BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html), [BigInteger API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).


</details>

- [Quay lại đầu trang](#back-to-top)
