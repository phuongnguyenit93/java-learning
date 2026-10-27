<a id="back-to-top"></a>

# So sánh số thực

## Menu
- [Toán tử ==, !=, &lt;, &lt;=, &gt; và &gt;=](#floating-point-comparison-001)
- [NaN không bằng chính nó trong so sánh primitive](#floating-point-comparison-002)
- [Float.isNaN, Double.isNaN, isInfinite và isFinite](#floating-point-comparison-003)
- [+0.0 và -0.0 với toán tử ==](#floating-point-comparison-004)
- [Float.compare, Double.compare và thứ tự toàn phần](#floating-point-comparison-005)
- [equals của Float, Double với NaN và signed zero](#floating-point-comparison-006)
- [So sánh bằng sai số tuyệt đối](#floating-point-comparison-007)
- [So sánh bằng sai số tương đối](#floating-point-comparison-008)
- [Kết hợp sai số tuyệt đối và tương đối](#floating-point-comparison-009)
- [Khi nào cần giới hạn theo ULP](#floating-point-comparison-010)
- [Không dùng so sánh gần bằng tùy tiện làm quan hệ equals hoặc thứ tự sắp xếp](#floating-point-comparison-011)

## <a id="floating-point-comparison-001">Toán tử ==, !=, &lt;, &lt;=, &gt; và &gt;=</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `==`, `!=`, `<`, `<=`, `>` và `>=` khi so sánh số thực trong Java

Java cho phép so sánh các giá trị `float` và `double` bằng sáu toán tử: **`==`, `!=`, `<`, `<=`, `>` và `>=`**. Chúng trả về `boolean` và sử dụng **ngữ nghĩa so sánh số dấu phẩy động** của Java, không phải cách so sánh xấp xỉ theo suy luận toán học.

Các điểm cốt lõi:

- `==` kiểm tra **hai giá trị số học có bằng nhau theo so sánh dấu phẩy động hay không**, chứ không phải “hai số gần nhau”.
- `!=` trả về kết quả phủ định của `==`, kể cả với `NaN`.
- `<`, `<=`, `>`, `>=` kiểm tra quan hệ thứ tự. **Nếu có toán hạng `NaN`, cả bốn phép so sánh thứ tự đều là `false`.**
- **`NaN == NaN` là `false`**, nhưng **`NaN != NaN` là `true`**.
- **`+0.0 == -0.0` là `true`**, dù hai mẫu bit khác nhau.
- Các giá trị `float`/`double` có độ chính xác nhị phân hữu hạn. Vì vậy `0.1 + 0.2 == 0.3` có thể là `false`, không phải lỗi trình biên dịch.

Để so sánh giá trị tính toán có sai số, cần **quy tắc dung sai do bài toán quyết định**. Để sắp xếp tất cả giá trị, kể cả `NaN` và `-0.0`, có thể dùng `Double.compare`/`Float.compare` với quy tắc thứ tự **khác** các toán tử primitive.

## 1. Sáu toán tử hoạt động như thế nào?

| Toán tử | Ý nghĩa | Ví dụ | Kết quả |
| --- | --- | --- | --- |
| `==` | Bằng nhau về giá trị theo so sánh primitive | `1.5 == 1.5` | `true` |
| `!=` | Không bằng nhau | `1.5 != 2.5` | `true` |
| `<` | Nhỏ hơn | `1.5 < 2.5` | `true` |
| `<=` | Nhỏ hơn hoặc bằng | `2.5 <= 2.5` | `true` |
| `>` | Lớn hơn | `2.5 > 1.5` | `true` |
| `>=` | Lớn hơn hoặc bằng | `2.5 >= 2.5` | `true` |

Khi hai toán hạng là các số hữu hạn không phải `NaN`, chúng tuân theo thứ tự số học quen thuộc. Các toán tử trả `boolean`, không trả số -1/0/1.

```java
double x = 1.5;
double y = 2.5;

boolean a = x == y; // false
boolean b = x != y; // true
boolean c = x < y;  // true
boolean d = x <= y; // true
boolean e = x > y;  // false
boolean f = x >= y; // false
```

Với các phép `<`, `<=`, `>`, `>=`, hai toán hạng phải là kiểu số có thể so sánh bằng phép quan hệ. Java không hỗ trợ so sánh thứ tự hai `String` bằng `<`; `String` dùng cách so sánh riêng như `compareTo` nếu bài toán cần.

## 2. Quy tắc nâng kiểu toán hạng trước khi so sánh

Khi so sánh hai giá trị thuộc kiểu số primitive khác nhau, Java áp dụng **binary numeric promotion**. Nếu một toán hạng là `double`, toán hạng còn lại được đưa sang `double`; nếu có `float` mà không có `double`, phép so sánh dùng `float`; với các kiểu số nguyên thì quy tắc nâng kiểu số nguyên áp dụng.

```java
float f = 0.1f;
double d = 0.1;

boolean equal = f == d; // false
```

Tại sao `equal` là false? Literal `0.1f` đã được làm tròn theo độ chính xác `float` (24 bit phần trị hiệu dụng). Khi `f` được mở rộng sang `double` để so sánh, nó mang giá trị `float` đã làm tròn chứ **không khôi phục** đúng cùng mẫu giá trị của literal `double 0.1`. Do vậy, dù mã nguồn đều viết gần “0.1”, hai giá trị có thể khác.

Ví dụ khác:

```java
int i = 16_777_217;
float f = 16_777_216f;

boolean same = i == f; // true: int được đổi thành float và bị làm tròn
```

`16_777_217` không biểu diễn chính xác trong `float`, nên khi chuyển kiểu để so sánh với `f`, nó có thể được làm tròn thành `16_777_216f`. Vì vậy **`==` không bảo đảm phát hiện khác biệt giữa mọi số nguyên** khi trộn `float` với `int`/`long` lớn. Tránh trộn kiểu khi yêu cầu so sánh số nguyên chính xác; chuyển về loại phù hợp trước khi mất độ chính xác.

Trong `Double`/`Float` wrapper, cần xem thêm quy tắc unboxing và `equals` (mục 8). Không nên suy diễn rằng mọi đối tượng chứa số đều dùng cùng ngữ nghĩa với các toán tử primitive.

## 3. Vì sao `0.1 + 0.2 == 0.3` là `false`?

`float` và `double` dùng biểu diễn nhị phân có độ chính xác hữu hạn. Nhiều phân số thập phân như `0.1`, `0.2`, `0.3` không biểu diễn chính xác dưới dạng nhị phân hữu hạn. Một phép cộng sẽ làm tròn kết quả theo quy tắc dấu phẩy động.

```java
double actual = 0.1 + 0.2;
double expected = 0.3;

System.out.println(actual);             // 0.30000000000000004
System.out.println(actual == expected); // false
System.out.println(actual != expected); // true
```

Lúc này, `==` đang **hoạt động đúng**: hai giá trị `double` cuối cùng thực sự không đồng nhất. Sai lầm nằm ở việc mong muốn số học `double` tạo ra biểu diễn thập phân hoàn toàn chính xác.

**Không có nghĩa là không bao giờ dùng `==` với `double`.** Có những trường hợp `==` là lựa chọn đúng: kiểm tra giá trị sentinel hữu hạn được thiết kế rõ ràng, so sánh hai giá trị cùng quy trình biểu diễn khi cần đúng tính bằng nhau, hoặc so với `0.0` khi ngữ nghĩa bằng 0 phù hợp và xử lý `NaN` riêng. Vấn đề là **mục tiêu so sánh**: bằng nhau chính xác của giá trị `double` hay gần bằng theo sai số cho phép.

### Độ chính xác hữu hạn còn ảnh hưởng phép so sánh thứ tự

```java
float big = 16_777_216f;
boolean stillSame = (big + 1 == big); // true
```

Ở vùng này, khoảng cách giữa hai giá trị `float` biểu diễn được liên tiếp là 2. Khi cộng 1, kết quả nằm ở chính giữa hai giá trị biểu diễn được và có thể làm tròn quay về `big` theo quy tắc round-to-nearest, ties-to-even. Do đó phép so sánh cho `true`, mặc dù về số học thực `x + 1 > x`.

Chọn đúng miền và loại số rất quan trọng trước khi kết luận các phép so sánh số thực có phản ánh thứ tự của **giá trị toán học ban đầu** hay không.

## 4. `NaN` làm thay đổi trực giác về `==`, `!=` và thứ tự

`NaN` (*Not a Number*) là giá trị đặc biệt có thể sinh từ các phép toán không có kết quả số thực xác định, ví dụ `0.0 / 0.0`. Cả `Float.NaN` và `Double.NaN` đều là NaN.

Trong các phép so sánh primitive dấu phẩy động, NaN được coi là **unordered** (không có thứ tự) so với mọi giá trị, **kể cả chính NaN**:

| Biểu thức | Kết quả |
| --- | --- |
| `Double.NaN == Double.NaN` | `false` |
| `Double.NaN != Double.NaN` | `true` |
| `Double.NaN < 1.0` | `false` |
| `Double.NaN <= 1.0` | `false` |
| `Double.NaN > 1.0` | `false` |
| `Double.NaN >= 1.0` | `false` |
| `1.0 < Double.NaN` | `false` |
| `1.0 >= Double.NaN` | `false` |

Điều này cũng đúng với các phép so sánh `float` khi có `Float.NaN`.

**Hệ quả logic rất dễ nhầm:** Với giá trị NaN, `!(a < b)` là `true` nhưng `a >= b` lại `false`. Vì vậy biến đổi điều kiện `!(x < y)` thành `x >= y` **không tương đương** nếu `x` hoặc `y` có thể là NaN. Tương tự, `!(x <= y)` không đồng nghĩa với `x > y` khi có NaN. Quy tắc “không nhỏ hơn thì phải lớn hơn hoặc bằng” chỉ đúng khi dữ liệu được bảo đảm **không NaN**.

`!=` là phủ định trực tiếp của `==`, vì thế khi xuất hiện NaN, `!=` là `true` ngay cả khi hai biến chứa cùng một đối tượng NaN theo cách lưu trữ số thực.

### Kiểm tra NaN đúng cách

```java
double value = Double.NaN;

boolean wrong = value == Double.NaN; // false
boolean isNan = Double.isNaN(value);  // true
```

Không dùng `value == Double.NaN` để nhận biết NaN. Có thể dựa vào `value != value` theo tính chất NaN, nhưng `Double.isNaN(value)`/`Float.isNaN(value)` diễn đạt rõ ý định hơn và dễ đọc hơn.

Nếu phải loại cả NaN lẫn vô cực, dùng `Double.isFinite(value)` hoặc `Float.isFinite(value)` trước khi tính toán/so sánh thứ tự.

## 5. `+0.0` và `-0.0`: bằng nhau về toán tử, khác mẫu bit

IEEE 754 có hai biểu diễn zero: **zero dương** và **zero âm**. Java coi chúng là bằng nhau trong các toán tử primitive:

```java
double positive = +0.0;
double negative = -0.0;

boolean eq = (positive == negative); // true
boolean ne = (positive != negative); // false
boolean lt = (negative < positive);  // false
boolean le = (negative <= positive); // true
boolean gt = (negative > positive);  // false
boolean ge = (negative >= positive); // true
```

Tuy nhiên, dấu bit của zero có thể ảnh hưởng đến phép tính khác:

```java
double p = 1.0 / +0.0; // +Infinity
double n = 1.0 / -0.0; // -Infinity
```

`Double.doubleToLongBits(0.0)` và `Double.doubleToLongBits(-0.0)` trả hai mẫu bit khác nhau. Tương tự với `Float.floatToIntBits`. **Nếu chỉ dùng `==`, bạn đang chọn ngữ nghĩa số học rằng cả hai số zero bằng nhau**; đó không phải công cụ nhận diện dấu bit.

Khi cần nhận dạng bit vì định dạng nhị phân hay tính toán có dấu zero, sử dụng API bit hoặc `Double.compare`/`Float.compare` với quy tắc thứ tự bổ sung, thay vì kết luận từ `==`.

## 6. `+Infinity` và `-Infinity`: có thứ tự nếu không có NaN

Khác với NaN, vô cực tham gia thứ tự số thực theo quy tắc quen thuộc:

\[
-\infty < \text{mọi số hữu hạn} < +\infty
\]

Ví dụ:

```java
boolean a = Double.POSITIVE_INFINITY > Double.MAX_VALUE; // true
boolean b = Double.NEGATIVE_INFINITY < -Double.MAX_VALUE; // true
boolean c = Double.POSITIVE_INFINITY == (1.0 / 0.0);     // true
boolean d = Double.POSITIVE_INFINITY == Double.NEGATIVE_INFINITY; // false
```

Dù có quan hệ thứ tự, vô cực thường **không phải dữ liệu đầu vào hợp lệ** cho tỷ lệ, giá cả, số đo hay bộ đếm thực tế. Nhiều phép toán có thể biến dữ liệu hữu hạn thành vô cực do overflow hoặc chia cho zero theo kiểu dấu phẩy động; cần quyết định rõ có cho phép trạng thái này hay không.

**Không nhầm** chia dấu phẩy động cho zero với chia nguyên cho zero: `1.0 / 0.0` trả `Infinity`, còn phép chia nguyên `1 / 0` (khi được thực hiện) gây `ArithmeticException`; Java có thể báo lỗi sớm nếu đó là biểu thức hằng không hợp lệ trong ngữ cảnh yêu cầu hằng số.

## 7. Khi nào dùng so sánh gần đúng thay vì `==`?

Với kết quả của nhiều phép tính `double`, đôi khi cần coi hai giá trị là “gần bằng” trong **ngưỡng sai số ứng dụng cho phép**, thay vì yêu cầu `==`.

Một cách thông dụng kết hợp **dung sai tuyệt đối** và **dung sai tương đối**:

\[
|a-b| \leq \max(\text{absTol},\ \text{relTol}\cdot\max(|a|,|b|)).
\]

- `absTol` phù hợp với mức sai số cố định, đặc biệt **gần số 0**.
- `relTol` phù hợp khi độ lớn của số thay đổi nhiều bậc, giúp giới hạn sai số theo tỉ lệ.
- Các ngưỡng phải **được xác định từ đơn vị đo, yêu cầu nghiệp vụ và đặc tính thuật toán**; không có một epsilon cố định đúng cho mọi bài toán.

Ví dụ phương thức minh họa:

```java
static boolean nearlyEqual(double a, double b, double absTol, double relTol) {
    if (Double.isNaN(a) || Double.isNaN(b)) return false;
    if (a == b) return true; // Bao gồm hai vô cực cùng dấu và ±0.0
    if (!Double.isFinite(a) || !Double.isFinite(b)) return false;

    double delta = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return delta <= Math.max(absTol, relTol * scale);
}
```

Đây là **mảnh phương thức** cần đặt trong class. Nó giả định người gọi truyền ngưỡng **hữu hạn, không âm, đã lựa chọn hợp lý**. Nếu đây là API dùng cho dữ liệu không tin cậy, nên xác thực `absTol` và `relTol` (không NaN/Infinity, không âm), đồng thời xét nguy cơ tràn số trong phép trừ hay nhân tạo ngưỡng khi trị tuyệt đối rất lớn.

**Quan trọng:** `nearlyEqual` không phải thay thế phổ quát cho `==`:

- Quan hệ “gần bằng” với dung sai dương **thường không có tính bắc cầu**. Có thể `a` gần `b` và `b` gần `c`, nhưng `a` không gần `c`.
- Vì thế không dùng trực tiếp gần bằng để định nghĩa `equals`/`hashCode` hoặc comparator sắp xếp, vốn đòi hợp đồng nhất quán.
- Không nên dùng `Double.MIN_VALUE` làm một epsilon tuyệt đối mặc định: nó là **số dương subnormal nhỏ nhất**, không phải sai số đại diện khi cộng trừ nhiều giá trị `double`.
- Gần bằng **0** cần giới hạn tuyệt đối phù hợp; chỉ dùng tương đối có thể phản trực giác khi hai số đều rất nhỏ.
- Với tiền tệ và yêu cầu chính xác chữ số thập phân, `BigDecimal` với `RoundingMode` phù hợp hơn trong nhiều trường hợp, nhưng phải khai báo chính sách chính xác.

Nếu mục tiêu là phát hiện **bit thay đổi**, dùng API bit. Nếu mục tiêu là so sánh **giá trị chính xác theo kiểu `double`**, dùng `==`. Nếu cần so sánh **độ gần theo phép đo**, dùng tolerance được định nghĩa cụ thể. Ba yêu cầu khác nhau.

## 8. So sánh primitive khác `Double.compare`, `Float.compare` và wrapper `equals`

Khi cần **sắp xếp** danh sách chứa NaN hay signed zero, các toán tử primitive không tạo được thứ tự toàn phần hợp lệ cho mọi giá trị: NaN làm tất cả phép `<`/`>` thành `false`.

Java cung cấp:

```java
int orderDouble = Double.compare(a, b);
int orderFloat = Float.compare(x, y);
```

Giá trị trả về **âm, zero hoặc dương** (không cần chính xác -1, 0, 1 trong mọi API so sánh) theo thứ tự được định nghĩa. Với `Double.compare`/`Float.compare`:

- `-Infinity` nhỏ hơn mọi số hữu hạn, `+Infinity` lớn hơn mọi số hữu hạn.
- **`-0.0` nhỏ hơn `+0.0`**, dù phép primitive `-0.0 == +0.0` là `true`.
- **NaN được coi lớn hơn mọi giá trị khác**, kể cả `+Infinity`.
- Các NaN được xem là **bằng nhau trong phép compare** vì các mẫu NaN được xử lý theo ngữ nghĩa canonical.

Ví dụ:

```java
int z = Double.compare(-0.0, +0.0); // Giá trị âm (-1 trong JDK hiện hành)
int n = Double.compare(Double.NaN, Double.POSITIVE_INFINITY); // Giá trị dương
int bothNaN = Double.compare(Double.NaN, Double.NaN); // 0
```

`Double.valueOf(0.0).equals(Double.valueOf(-0.0))` là `false`; `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là `true`. Wrapper `equals` sử dụng ngữ nghĩa nhất quán với `Double.compare` cho giá trị tương ứng, **khác** primitive `==` đối với signed zero/NaN.

Nếu cả hai toán hạng là object wrapper `Double`, toán tử `==` giữa chúng có thể **so sánh tham chiếu**, không phải số học. Khi một bên là primitive và Java áp dụng unboxing, có thể thành so sánh số học; unboxing `null` sẽ ném `NullPointerException`. Khi làm việc với collection, phải chọn `equals` hoặc comparator phù hợp thay vì dùng `==` trên hai object wrapper.

**Sắp xếp đúng:**

```java
double[] values = { Double.NaN, +0.0, -0.0, Double.POSITIVE_INFINITY };
java.util.Arrays.sort(values);
// [-0.0, 0.0, Infinity, NaN] theo thứ tự của Double.compare
```

Không viết comparator bằng `(int) (a - b)`, bởi phép trừ có thể là số rất nhỏ bị cast thành 0, có thể overflow thành infinity, và với NaN cho 0 theo quy tắc cast sang nguyên. Comparator như thế có thể vi phạm yêu cầu sắp xếp.

## 9. Chương trình Java 17+ kiểm chứng sáu toán tử và trường hợp biên

Lưu vào tệp `FloatingComparisonDemo.java`:

```java
public class FloatingComparisonDemo {
    static boolean nearlyEqual(double a, double b, double absTol, double relTol) {
        if (Double.isNaN(a) || Double.isNaN(b)) return false;
        if (a == b) return true;
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;

        double delta = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return delta <= Math.max(absTol, relTol * scale);
    }

    public static void main(String[] args) {
        double a = 0.1 + 0.2, b = 0.3;
        System.out.println("sum: " + a);
        System.out.println("sum == 0.3: " + (a == b));
        System.out.println("sum != 0.3: " + (a != b));
        System.out.println("near sum: " + nearlyEqual(a, b, 1e-12, 1e-9));

        System.out.println("1 < 2: " + (1.0 < 2.0));
        System.out.println("2 <= 2: " + (2.0 <= 2.0));
        System.out.println("2 > 1: " + (2.0 > 1.0));
        System.out.println("2 >= 2: " + (2.0 >= 2.0));

        double nan = Double.NaN;
        System.out.println("NaN == NaN: " + (nan == nan));
        System.out.println("NaN != NaN: " + (nan != nan));
        System.out.println("NaN < 1: " + (nan < 1));
        System.out.println("NaN <= 1: " + (nan <= 1));
        System.out.println("NaN > 1: " + (nan > 1));
        System.out.println("NaN >= 1: " + (nan >= 1));

        System.out.println("+0 == -0: " + (0.0 == -0.0));
        System.out.println("1/+0: " + (1.0 / 0.0));
        System.out.println("1/-0: " + (1.0 / -0.0));
        System.out.println("Infinity > MAX: "
            + (Double.POSITIVE_INFINITY > Double.MAX_VALUE));
        System.out.println("-Infinity < MIN: "
            + (Double.NEGATIVE_INFINITY < -Double.MAX_VALUE));

        float f = 0.1f;
        System.out.println("0.1f == 0.1: " + (f == 0.1));
        float big = 16_777_216f;
        System.out.println("float next equals: " + (big + 1 == big));

        System.out.println("compare -0,+0: " + Double.compare(-0.0, +0.0));
        System.out.println("compare NaN,+Infinity: "
            + Double.compare(Double.NaN, Double.POSITIVE_INFINITY));
        System.out.println("compare NaN,NaN: " + Double.compare(Double.NaN, Double.NaN));
        System.out.println("boxed zeros equals: "
            + Double.valueOf(0.0).equals(Double.valueOf(-0.0)));
        System.out.println("boxed NaNs equals: "
            + Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN)));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac FloatingComparisonDemo.java
java FloatingComparisonDemo
```

Stdout:

```text
sum: 0.30000000000000004
sum == 0.3: false
sum != 0.3: true
near sum: true
1 < 2: true
2 <= 2: true
2 > 1: true
2 >= 2: true
NaN == NaN: false
NaN != NaN: true
NaN < 1: false
NaN <= 1: false
NaN > 1: false
NaN >= 1: false
+0 == -0: true
1/+0: Infinity
1/-0: -Infinity
Infinity > MAX: true
-Infinity < MIN: true
0.1f == 0.1: false
float next equals: true
compare -0,+0: -1
compare NaN,+Infinity: 1
compare NaN,NaN: 0
boxed zeros equals: false
boxed NaNs equals: true
```

**Đọc kết quả:** Các dòng đầu cho thấy phép so sánh chính xác giữa hai giá trị `double` đã làm tròn khác với so sánh theo tolerance. Các dòng `NaN` chứng minh mọi phép quan hệ thứ tự đều false, `==` false và `!=` true. Các dòng `±0` và vô cực cho thấy sự khác biệt giữa bằng nhau về giá trị số học với dấu zero. Cuối cùng, `Double.compare` và wrapper `equals` thể hiện quy tắc phục vụ sắp xếp/collection, không đồng nhất với primitive `==`.

## 10. Thiết kế điều kiện so sánh an toàn

### Kiểm tra miền đầu vào

Nếu hệ thống chỉ chấp nhận kết quả số hữu hạn, **xác thực trước**:

```java
static boolean inRange(double value, double min, double max) {
    return Double.isFinite(value)
        && value >= min
        && value <= max;
}
```

Đoạn này là **mảnh mã minh họa**: chỉ dùng khi `min` và `max` là các ngưỡng hữu hạn được kiểm chứng và `min <= max`. Việc kiểm tra `isFinite` diễn đạt rõ yêu cầu không chấp nhận NaN/Infinity; riêng toán tử `>=` và `<=` đã trả false với NaN, nhưng không loại vô cực trong mọi miền nếu ngưỡng không hữu hạn.

### Tránh đảo điều kiện bất cẩn khi có NaN

Nếu muốn bác bỏ mọi giá trị **không nhỏ hơn ngưỡng** hoặc NaN, đừng mặc định đổi `!(x < threshold)` thành `x >= threshold` vì chúng khác nhau với NaN.

```java
double x = Double.NaN;
boolean notLess = !(x < 10.0); // true
boolean greaterOrEqual = x >= 10.0; // false
```

Khi muốn điều kiện độc lập với NaN, hãy viết tường minh:

```java
boolean accepted = Double.isFinite(x) && x >= 0.0 && x <= 10.0;
```

### Khi cần kiểm tra gần bằng

Đặt tên có chủ đích như `nearlyEqual`, `withinTolerance` và truyền các tham số `absTol`/`relTol` theo đơn vị đo. Không áp dụng tolerance tùy ý để so sánh khóa map, mã định danh, mã giao dịch hoặc mọi giá trị trong bài toán.

### Khi cần thứ tự ổn định để sắp xếp

Dùng `Double.compare`/`Float.compare`, hoặc `Comparator.comparingDouble` khi làm việc với object và trường dữ liệu `double`. Nếu dữ liệu có thể chứa `NaN`, hãy quyết định liệu quy tắc “NaN xếp cuối” của comparator này có phù hợp, hay cần lọc dữ liệu trước.

## 11. Những nhầm lẫn thường gặp

1. **“Hai biểu thức thập phân giống nhau về toán học phải so sánh `==` bằng nhau.”** Sai: `double` có sai số biểu diễn và làm tròn.
2. **“`!=` cũng false khi có NaN vì không so sánh được.”** Sai: `NaN != NaN` là true.
3. **“Không nhỏ hơn thì chắc chắn lớn hơn hoặc bằng.”** Sai với NaN: `!(x < y)` có thể true trong khi `x >= y` false.
4. **“`NaN == NaN` là true vì hai bên đều là NaN.”** Sai: primitive `==` trả false; dùng `Double.isNaN` để kiểm tra.
5. **“`+0.0` và `-0.0` khác nhau theo `==` vì bit dấu khác.”** Sai: `+0.0 == -0.0` true.
6. **“`Double.compare` hoạt động y hệt `<`, `>` và `==` trên mọi giá trị.”** Sai: nó phân biệt signed zero và quy định vị trí cho NaN.
7. **“Chỉ cần dùng `Double.MIN_VALUE` làm epsilon khi so sánh gần đúng.”** Sai: đây là số dương nhỏ nhất, thường không phù hợp sai số của thuật toán.
8. **“Tolerance gần đúng có thể thay thế `equals` và `hashCode`.”** Sai: gần bằng thường không bắc cầu nên không phải quan hệ tương đương hợp lệ cho nhiều collection.
9. **“`0.1f` và `0.1` chắc chắn bằng nhau sau khi nâng kiểu.”** Sai: `float` đã bị làm tròn theo độ chính xác thấp hơn trước khi sang `double`.
10. **“So sánh số thực bằng cách ép phép trừ về `int` là comparator tốt.”** Sai: mất sai khác nhỏ, xử lý NaN sai và có thể phá hợp đồng comparator.
11. **“Vô cực luôn không có quan hệ thứ tự.”** Sai: chỉ NaN unordered; `-Infinity` và `+Infinity` có thứ tự.
12. **“Dùng toán tử `==` trên hai wrapper `Double` luôn so sánh giá trị số.”** Sai: hai object có thể bị so sánh tham chiếu; `equals` và unboxing có quy tắc riêng.

## 12. Câu hỏi tự kiểm tra

- `Double.NaN == Double.NaN` có kết quả gì? **false**.
- `Double.NaN != Double.NaN` có kết quả gì? **true**.
- Với `x = Double.NaN`, `!(x < 3.0)` và `x >= 3.0` bằng nhau không? **Không**: kết quả lần lượt true và false.
- `-0.0 == 0.0` có đúng không? **Đúng**; tuy nhiên `Double.compare(-0.0, 0.0)` cho kết quả âm.
- `Double.POSITIVE_INFINITY > Double.MAX_VALUE` là gì? **true**.
- `float a = 0.1f; double b = 0.1;` thì `a == b` chắc chắn true không? **Không**: trong ví dụ này là false vì làm tròn `float`.
- Nếu muốn “gần bằng” theo sai số đo cụ thể, nên dùng gì? **Một hàm so sánh với tolerance được chọn theo miền/đơn vị**, kiểm tra NaN và vô cực theo chính sách.
- Nếu cần sắp xếp một danh sách chứa NaN và signed zero, nên dùng gì? **`Double.compare`/`Float.compare`** hoặc comparator theo quy tắc thứ tự nghiệp vụ, không dùng `(int)(a-b)`.

**Ghi nhớ:** Sáu toán tử trên `float`/`double` so sánh **giá trị số thực đã được biểu diễn**, không tự áp dụng ngưỡng sai số. `NaN` làm `==` và bốn phép quan hệ thứ tự thành false, nhưng `!=` thành true. `+0.0` và `-0.0` bằng nhau theo primitive `==`, còn các comparator chuẩn phân biệt chúng. Khi cần so sánh gần đúng hoặc sắp xếp, hãy chọn **quy tắc riêng phù hợp mục tiêu**, thay vì suy diễn trực tiếp từ toán học số thực vô hạn độ chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-002">NaN không bằng chính nó trong so sánh primitive</a>

<details>
<summary>Xem chi tiết</summary>

# NaN không bằng chính nó trong so sánh primitive của Java

Khi so sánh các số thực nguyên thủy `float` và `double`, biểu thức `x == x` **không luôn là `true`**. Nếu `x` là **NaN (Not a Number)**, Java trả `false` cho phép so sánh bằng, kể cả hai vế chính là cùng một biến. Ngược lại, `x != x` trả `true`. Đây là một quy tắc có chủ đích của số thực IEEE 754 được Java áp dụng, không phải lỗi của toán tử `==`.

~~~java
double x = Double.NaN;

System.out.println(x == x);         // false
System.out.println(x != x);         // true
System.out.println(x == Double.NaN); // false
System.out.println(Double.isNaN(x)); // true
~~~

Đây là đoạn mã minh họa cần đặt trong phương thức Java. Trong chương trình thực tế, **nên dùng `Double.isNaN(x)` hoặc `Float.isNaN(x)`** để nhận biết NaN rõ ràng, thay vì dựa vào mẹo `x != x`.

Bài học thuộc chủ đề **so sánh số thực**: tập trung vào toán tử so sánh của primitive, các điều kiện dễ sai khi có NaN, và lý do kết quả của lớp wrapper có thể khác.

## 1. NaN là gì và xuất hiện từ đâu?

`float` (32 bit) và `double` (64 bit) là các kiểu số thực dấu phẩy động của Java. Ngoài các giá trị hữu hạn, chúng có những giá trị đặc biệt như **dương vô cực**, **âm vô cực** và **NaN**. NaN biểu thị kết quả không phải một giá trị số thông thường có thể so sánh theo thứ tự số học.

Các ví dụ tạo NaN hợp lệ trong Java:

~~~java
double a = 0.0 / 0.0;               // NaN
double b = Double.POSITIVE_INFINITY
         - Double.POSITIVE_INFINITY; // NaN
double c = Math.sqrt(-1.0);        // NaN
float d = Float.NaN;                // NaN
~~~

Lưu ý sự khác biệt giữa phép chia số nguyên và phép chia số thực:

- `0.0 / 0.0` là phép chia `double`, cho ra NaN, không ném `ArithmeticException`.
- Phép chia số nguyên cho 0 ném `ArithmeticException` khi được thực thi, kể cả biểu thức `1 / 0`. Đừng nhầm với phép chia số thực `0.0 / 0.0` cho NaN.
- `1.0 / 0.0` cho dương vô cực, **không** phải NaN.
- Một khi NaN tham gia nhiều phép toán số học thông thường, kết quả tiếp tục là NaN, ví dụ `Double.NaN + 10.0`.

NaN có các mẫu bit đặc biệt trong biểu diễn số thực và có thể tồn tại nhiều bit pattern NaN khác nhau. Quy tắc so sánh primitive nói đến **bất kỳ giá trị NaN nào**, không phụ thuộc nó được tạo như thế nào. Ta không cần so sánh hay chỉnh sửa các bit này để nhận diện NaN.

## 2. Quy tắc == và != với primitive float/double

Theo ngữ nghĩa phép so sánh số thực của Java:

- Nếu **một trong hai toán hạng** là NaN, `==` luôn là `false`.
- Nếu **một trong hai toán hạng** là NaN, `!=` luôn là `true`.
- Điều này vẫn đúng khi cả hai toán hạng là NaN, hoặc hai vế lấy từ **cùng một biến**.

| Phép so sánh primitive | Kết quả |
| --- | --- |
| `Double.NaN == Double.NaN` | `false` |
| `Double.NaN != Double.NaN` | `true` |
| `Double.NaN == 1.0` | `false` |
| `Double.NaN != 1.0` | `true` |
| `Float.NaN == Float.NaN` | `false` |
| `Float.NaN != Float.NaN` | `true` |
| `1.0 == 1.0` | `true` |
| `1.0 != 1.0` | `false` |

Việc có NaN làm cho một trực giác quen thuộc, “mọi thứ đều bằng chính nó”, **không đúng đối với phép `==` trên primitive `float`/`double`**. Nói một cách chính xác, quan hệ bằng theo số thực ở đây không mang tính phản xạ trên toàn miền giá trị primitive vì có NaN.

Một biến không phải NaN vẫn so sánh với chính nó bằng `true` theo `==`, kể cả khi nó là `Double.POSITIVE_INFINITY` hay `Double.NEGATIVE_INFINITY`. Ví dụ:

~~~java
double infinity = Double.POSITIVE_INFINITY;
System.out.println(infinity == infinity); // true

double zero = +0.0;
System.out.println(zero == -0.0);         // true
~~~

Hai giá trị `+0.0` và `-0.0` có biểu diễn bit khác nhau, nhưng `==` của primitive coi chúng bằng nhau. Quy tắc đặc biệt của NaN không đồng nghĩa mọi giá trị đặc biệt đều tự bất đẳng.

## 3. Các phép so sánh thứ tự với NaN

NaN còn làm cho các phép so sánh thứ tự `<`, `<=`, `>` và `>=` đều trả về `false` khi **ít nhất một toán hạng** là NaN.

| Biểu thức | Kết quả khi `x = Double.NaN` |
| --- | --- |
| `x < 1.0` | `false` |
| `x <= 1.0` | `false` |
| `x > 1.0` | `false` |
| `x >= 1.0` | `false` |
| `x == 1.0` | `false` |
| `x != 1.0` | `true` |

Không thể coi NaN là số “rất lớn”, “rất nhỏ” hay “bằng chính nó” trong các phép so sánh primitive. NaN được xem là **unordered** so với các giá trị số thực trong quan hệ thứ tự của các toán tử trên.

Hậu quả quan trọng: những cách kiểm tra tưởng tương đương khi dữ liệu là số hữu hạn **không tương đương** nếu dữ liệu có NaN.

~~~java
double x = Double.NaN;

boolean outOfRange = x < 0.0 || x > 100.0; // false
boolean insideRange = x >= 0.0 && x <= 100.0; // false
boolean notInsideRange = !insideRange;        // true
~~~

Biểu thức `x < 0 || x > 100` **không phát hiện được NaN** và có thể cho phép NaN lọt qua nếu ứng dụng chỉ dùng nó để từ chối dữ liệu ngoài miền. Tương tự, `!(x < 0)` là `true` với NaN, nhưng `x >= 0` là `false`. Hai điều kiện không tương đương trên toàn miền `double`.

## 4. Nhận diện NaN bằng API đúng nghĩa

Ưu tiên hai phương thức tĩnh:

~~~java
double x = 0.0 / 0.0;
float y = Float.NaN;

boolean a = Double.isNaN(x); // true
boolean b = Float.isNaN(y);  // true
~~~

Chúng trả `true` đúng khi đối số là NaN và `false` cho các giá trị khác, bao gồm dương/âm vô cực.

Có thể viết:

~~~java
boolean hasNaN = x != x; // true khi x là NaN, dựa trên đặc tính IEEE 754
~~~

Nhưng đây là cách kiểm tra khó đọc hơn. `Double.isNaN(x)` nêu rõ chủ đích, thuận lợi cho review, bảo trì và tìm kiếm trong codebase.

Nếu nghiệp vụ cần chấp nhận **chỉ giá trị số hữu hạn**, `isNaN` là chưa đủ, vì vô cực không phải NaN:

~~~java
double value = Double.POSITIVE_INFINITY;

System.out.println(Double.isNaN(value));   // false
System.out.println(Double.isFinite(value)); // false
~~~

`Double.isFinite(value)` và `Float.isFinite(value)` trả `true` khi giá trị không phải NaN và không phải vô cực. Khi xử lý số đo, tiền tệ quy đổi, xác suất hoặc giới hạn, hãy chọn chính sách rõ ràng: cấm riêng NaN hay cấm cả vô cực.

## 5. Ví dụ Java 17+ độc lập và đầu ra

Tạo tệp `NaNComparisonDemo.java`:

~~~java
public class NaNComparisonDemo {
    public static void main(String[] args) {
        double invalid = 0.0 / 0.0;
        float missing = Float.NaN;

        System.out.println("value: " + invalid);
        System.out.println("self ==: " + (invalid == invalid));
        System.out.println("self !=: " + (invalid != invalid));
        System.out.println("NaN == NaN: " + (invalid == Double.NaN));
        System.out.println("NaN < 1: " + (invalid < 1.0));
        System.out.println("NaN <= 1: " + (invalid <= 1.0));
        System.out.println("NaN > 1: " + (invalid > 1.0));
        System.out.println("NaN >= 1: " + (invalid >= 1.0));
        System.out.println("float self ==: " + (missing == missing));

        System.out.println("Double.isNaN: " + Double.isNaN(invalid));
        System.out.println("Float.isNaN: " + Float.isNaN(missing));
        System.out.println("Double.isFinite: " + Double.isFinite(invalid));

        System.out.println("boxed equals: "
                + Double.valueOf(invalid).equals(Double.valueOf(Double.NaN)));
        System.out.println("Double.compare: "
                + Double.compare(invalid, Double.NaN));

        System.out.println("infinity self ==: "
                + (Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY));
        System.out.println("signed zero ==: " + (+0.0 == -0.0));
        System.out.println("sqrt(-1): " + Math.sqrt(-1.0));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac NaNComparisonDemo.java
java NaNComparisonDemo
~~~

Kết quả:

~~~text
value: NaN
self ==: false
self !=: true
NaN == NaN: false
NaN < 1: false
NaN <= 1: false
NaN > 1: false
NaN >= 1: false
float self ==: false
Double.isNaN: true
Float.isNaN: true
Double.isFinite: false
boxed equals: true
Double.compare: 0
infinity self ==: true
signed zero ==: true
sqrt(-1): NaN
~~~

Giải thích từng nhóm:

1. Phép chia `0.0 / 0.0` tạo NaN, nên `invalid == invalid` vẫn là `false` và `invalid != invalid` là `true`.
2. Các toán tử quan hệ với NaN trả `false`, kể cả `<=` và `>=`, vì không có thứ tự số học với NaN.
3. `Float.NaN` có cùng **quy tắc so sánh primitive** như `Double.NaN`, không phụ thuộc độ rộng 32 hay 64 bit.
4. `Double.isNaN`/`Float.isNaN` nhận diện NaN, còn `Double.isFinite` loại NaN và vô cực.
5. Hai dòng `boxed equals` và `Double.compare` có kết quả khác toán tử `==` primitive theo **hợp đồng API** của lớp wrapper, được giải thích ở phần sau.
6. Vô cực so sánh bằng chính nó cho `true`, hai số không có dấu đối nhau vẫn được coi bằng nhau, và căn bậc hai của số âm trong `Math.sqrt(-1.0)` là NaN.

## 6. Khác biệt với Double.equals, Double.compare và Float tương ứng

Chủ đề “NaN không bằng chính nó” nói về **toán tử so sánh giá trị nguyên thủy**. Java cũng có các API phục vụ so sánh đối tượng, sắp xếp và cấu trúc dữ liệu, với quy ước khác.

| Cách so sánh | Hai giá trị NaN | `+0.0` và `-0.0` | Mục đích |
| --- | --- | --- | --- |
| `doubleA == doubleB` | `false` | `true` | Phép so sánh primitive |
| `Double.valueOf(a).equals(Double.valueOf(b))` | `true` | `false` | Hợp đồng bằng nhau của wrapper |
| `Double.compare(a, b)` | `0` | Khác 0 | Thứ tự xác định, phục vụ sắp xếp |

`Double.equals` coi tất cả các giá trị NaN là bằng nhau theo hợp đồng của lớp `Double`. Nó đồng thời phân biệt bit dấu của hai số 0; `Double.compare` có cùng quy ước thứ tự: `-0.0` đứng trước `+0.0`, và NaN được xếp lớn hơn mọi giá trị số (kể cả dương vô cực). Các quy ước này hữu ích cho sắp xếp, bản đồ có thứ tự và cấu trúc dữ liệu cần quan hệ nhất quán.

Ví dụ đoạn mã minh họa **đặt trong một phương thức Java**:

~~~java
double a = Double.NaN;
double b = Double.NaN;

System.out.println(a == b);  // false (primitive)
System.out.println(Double.valueOf(a).equals(Double.valueOf(b))); // true
System.out.println(Double.compare(a, b) == 0); // true
System.out.println(+0.0 == -0.0);              // true
System.out.println(Double.compare(-0.0, +0.0)); // -1
~~~

Tương tự có `Float.equals` và `Float.compare`. Các API `Double.compare`/`Float.compare` xây dựng một thứ tự toàn phần theo **quy ước của Java**, khác quan hệ thứ tự primitive khi xuất hiện NaN.

Cần tránh nhầm thêm: với hai biến tham chiếu `Double`, toán tử `==` trên **hai đối tượng** có thể so sánh danh tính tham chiếu, không phải nội dung số thực. Nếu một vế là primitive và xảy ra unboxing, phép toán lại theo quy tắc primitive. Vì vậy khi nói “so sánh NaN”, hãy chỉ rõ đang dùng toán tử primitive, wrapper `equals` hay `compare`.

## 7. Lỗi thường gặp khi kiểm tra dữ liệu

### 7.1. So sánh với Double.NaN bằng ==

~~~java
double value = compute();

// Sai: điều kiện luôn false kể cả khi value là NaN.
if (value == Double.NaN) {
    throw new IllegalArgumentException("NaN");
}

// Đúng: nhận diện NaN rõ ràng.
if (Double.isNaN(value)) {
    throw new IllegalArgumentException("NaN");
}
~~~

Ở đây `compute()` là tên phương thức **giả định**, nên đoạn code này chỉ minh họa điều kiện kiểm tra, không phải chương trình chạy độc lập. Vì `Double.NaN == Double.NaN` vốn là `false`, dùng `== Double.NaN` sẽ không bao giờ là cách nhận diện NaN đúng.

### 7.2. Chỉ kiểm tra điều kiện ngoài khoảng

~~~java
double percentage = Double.NaN;

if (percentage < 0.0 || percentage > 100.0) {
    throw new IllegalArgumentException("Out of range");
}
// NaN lọt qua vì cả hai phép so sánh đều false.
~~~

Nếu chỉ chấp nhận tỷ lệ hữu hạn từ 0 đến 100, hãy viết điều kiện bao quát miền đặc biệt:

~~~java
static boolean validPercentage(double value) {
    return Double.isFinite(value) && value >= 0.0 && value <= 100.0;
}
~~~

Đoạn trên là **phương thức minh họa** để đặt trong lớp Java. Khi `value` là NaN, `Double.isFinite(value)` là `false`; khi là vô cực cũng vậy. Chỉ khi giá trị hữu hạn mới cần xét cận dưới và cận trên.

Nếu yêu cầu cho phép vô cực thì hãy định nghĩa quy tắc riêng, không tự động dùng `isFinite`. Ví dụ bài toán tối ưu đôi khi chủ ý dùng `Double.POSITIVE_INFINITY` như một giá trị sentinel.

### 7.3. Suy luận từ phủ định của toán tử thứ tự

~~~java
double x = Double.NaN;
boolean notLess = !(x < 5.0); // true
boolean atLeast = x >= 5.0;   // false
~~~

Với NaN, `!(x < 5)` và `x >= 5` **khác nhau**. Tương tự `!(x == y)` tương đương `x != y`, nhưng `!(x < y)` không có nghĩa `x >= y` khi một bên NaN. Đây là nguyên nhân những cách viết lại điều kiện toán học có thể thay đổi kết quả khi dữ liệu chứa NaN.

### 7.4. Nhầm NaN với null hoặc Infinity

- `NaN` là **một giá trị primitive đặc biệt** của `float`/`double`, không phải `null`.
- `null` là trạng thái của **tham chiếu** như `Double`, không phải giá trị mà primitive `double` chứa được.
- `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` là vô cực, không phải NaN. Có thể kiểm tra bằng `Double.isInfinite` hoặc kiểm tra cả tính hữu hạn bằng `Double.isFinite`.
- Một biến `Double` chứa `null` nếu bị **unbox** để dùng phép toán primitive có thể gây `NullPointerException`; đây là lỗi khác với việc toán tử so sánh nhận NaN.

## 8. NaN trong điều kiện rẽ nhánh và sắp xếp

Xét cách viết:

~~~java
double measurement = Double.NaN;

if (measurement < 0.0) {
    System.out.println("Negative");
} else if (measurement >= 0.0) {
    System.out.println("Nonnegative");
} else {
    System.out.println("Not ordered (NaN)");
}
~~~

Nhánh cuối chạy, bởi cả `measurement < 0.0` và `measurement >= 0.0` đều `false`. Khi thuật toán phân nhánh dựa vào điều kiện thứ tự, phải xử lý NaN rõ ràng trước hoặc có nhánh dự phòng cho trường hợp không có thứ tự.

Khi cần sắp xếp một danh sách có NaN, không nên tạo comparator đơn giản như `(a, b) -> a < b ? -1 : a > b ? 1 : 0`. Comparator đó sẽ trả 0 khi một bên NaN, khiến NaN bị coi ngang hàng với mọi số, và có thể vi phạm tính nhất quán mà thuật toán sắp xếp yêu cầu.

Hãy dùng:

~~~java
java.util.List<Double> values = new java.util.ArrayList<>(
        java.util.List.of(Double.NaN, 2.0, -1.0, 0.0));
values.sort(Double::compare);
// Thứ tự theo Double.compare: -1.0, 0.0, 2.0, NaN
~~~

Đây là **đoạn mã minh họa** để đặt trong một phương thức; `Double::compare` bảo đảm thứ tự theo hợp đồng Java, bao gồm quy tắc NaN đứng sau mọi số. Các dấu phân cách danh sách là ví dụ giải thích, không phải stdout bắt buộc.

## 9. Câu hỏi tự luyện có lời giải

**Câu 1.** Cho `double x = Double.NaN;`, các biểu thức `x == x`, `x != x`, `x <= x` có kết quả gì?  
**Đáp án:** `false`, `true`, `false`. Với NaN, so sánh bằng và so sánh thứ tự đều `false`, còn so sánh khác `true`.

**Câu 2.** `Double.NaN == 5.0` và `Double.NaN > 5.0` có giống kết quả không?  
**Đáp án:** Có, cả hai đều `false`; nhưng lý do không phải NaN là một số nhỏ hơn 5 mà vì NaN không có thứ tự số học với 5.

**Câu 3.** Vì sao `!(x < 10.0)` không luôn tương đương `x >= 10.0`?  
**Đáp án:** Khi `x` là NaN, `x < 10.0` là `false` nên biểu thức phủ định là `true`, nhưng `x >= 10.0` vẫn `false`.

**Câu 4.** Nên dùng gì để kiểm tra một giá trị `double` được phép tham gia phép tính nhưng tuyệt đối không được là NaN?  
**Đáp án:** `!Double.isNaN(value)`. Nếu đồng thời không chấp nhận vô cực thì dùng `Double.isFinite(value)`.

**Câu 5.** Tại sao `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là `true` còn `Double.NaN == Double.NaN` là `false`?  
**Đáp án:** `Double.equals` áp dụng hợp đồng của wrapper để coi các NaN là bằng nhau; `==` trên primitive áp dụng quy tắc so sánh số thực, theo đó NaN không bằng bất kỳ giá trị nào, kể cả chính nó.

**Câu 6.** Một comparator viết `a < b ? -1 : a > b ? 1 : 0` có lỗi gì khi `a` hoặc `b` là NaN?  
**Đáp án:** Cả `a < b` và `a > b` đều `false`, nên comparator trả 0 cho nhiều cặp không tương đương, có nguy cơ phá vỡ hợp đồng so sánh. Hãy dùng `Double.compare(a, b)` để có quy tắc thứ tự rõ ràng.

## 10. Ghi nhớ

- NaN là giá trị đặc biệt của `float` và `double`; phép `==` primitive với bất kỳ NaN nào trả `false`, ngay cả `x == x`.
- Phép `!=` với NaN trả `true`; `<`, `<=`, `>`, `>=` với NaN đều trả `false`.
- Dùng `Float.isNaN`/`Double.isNaN` để nhận biết NaN. Nếu cấm cả vô cực, dùng `Float.isFinite`/`Double.isFinite`.
- Không suy ra “không nhỏ hơn” thì “lớn hơn hoặc bằng” khi dữ liệu có NaN; kiểm tra miền giá trị phải tính đến trường hợp không có thứ tự.
- `Double.equals` và `Double.compare` có quy ước riêng về NaN để phục vụ so sánh đối tượng và sắp xếp, vì vậy cần phân biệt với `==` primitive.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-003">Float.isNaN, Double.isNaN, isInfinite và isFinite</a>

<details>
<summary>Xem chi tiết</summary>

# Float.isNaN, Double.isNaN, isInfinite và isFinite trong Java

## 1. Vì sao phải phân loại giá trị số thực?

Trong Java, `float` và `double` tuân theo biểu diễn số thực nhị phân IEEE 754. Ngoài các giá trị hữu hạn (*finite*), chúng còn có các giá trị đặc biệt:

- **NaN** (*Not a Number*): biểu diễn kết quả không phải số thực thông thường, chẳng hạn `0.0 / 0.0`.
- **+Infinity**: vô cực dương, chẳng hạn `1.0 / 0.0`.
- **-Infinity**: vô cực âm, chẳng hạn `-1.0 / 0.0`.
- **Giá trị hữu hạn**: số thông thường (kể cả +0.0, -0.0, số dưới chuẩn và số cực lớn nhưng còn hữu hạn).

Để kiểm tra đúng từng loại, Java cung cấp ba nhóm phương thức tĩnh trên cả hai lớp bao:

| Phương thức | Trả `true` khi nào? | Không trả `true` với |
|---|---|---|
| `Float.isNaN(float)` / `Double.isNaN(double)` | Giá trị là NaN | ±Infinity, mọi số hữu hạn |
| `Float.isInfinite(float)` / `Double.isInfinite(double)` | Giá trị là +Infinity hoặc -Infinity | NaN, mọi số hữu hạn |
| `Float.isFinite(float)` / `Double.isFinite(double)` | Giá trị hữu hạn, bao gồm ±0 và số dưới chuẩn | NaN, +Infinity, -Infinity |

Các phương thức này trả `boolean` và **không thay đổi** số được kiểm tra. Nếu một số `double` đã là NaN hoặc vô cực do bước tính trước đó, gọi `isFinite` chỉ phát hiện trạng thái ấy; phương thức không tự sửa kết quả.

Ví dụ ngắn:

```java
double a = 12.5;
double b = 0.0 / 0.0;
double c = 1.0 / 0.0;

System.out.println(Double.isFinite(a));   // true
System.out.println(Double.isNaN(b));      // true
System.out.println(Double.isInfinite(c)); // true
```

**Điểm cần nhớ:** một giá trị thuộc đúng một trong ba loại **NaN**, **vô cực**, hoặc **hữu hạn**. Riêng dấu của Infinity có thể xác định bằng so sánh với `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` (hoặc các hằng tương ứng của `Float`).

## 2. Dấu phẩy động có khác số nguyên ở phép chia cho 0

Trong số học nguyên, chia cho 0 bằng toán tử `/` ném `ArithmeticException`:

```java
// int bad = 1 / 0; // nếu được thực thi, ném ArithmeticException
int numerator = 1;
int denominator = 0;
// int result = numerator / denominator; // lúc chạy: ArithmeticException
```

Với `float`/`double`, phép chia cho 0 tuân theo quy tắc dấu phẩy động:

```java
double positiveInfinity = 1.0 / 0.0; // Infinity
double negativeInfinity = -1.0 / 0.0; // -Infinity
double notANumber = 0.0 / 0.0;       // NaN
```

Đây là những giá trị có thể lưu trong biến `double`; **phép chia số thực không tự ném `ArithmeticException` vì mẫu 0.0**.

NaN cũng có thể xuất hiện từ phép toán không có kết quả số thực hữu hạn:

```java
double a = Math.sqrt(-1.0); // NaN
double b = Double.POSITIVE_INFINITY
         + Double.NEGATIVE_INFINITY; // NaN
double c = Double.POSITIVE_INFINITY * 0.0; // NaN
```

Vô cực có thể xuất hiện từ phép toán hữu hạn bị **tràn miền số thực**:

```java
double tooLarge = Double.MAX_VALUE * 2.0; // Infinity
float alsoTooLarge = Float.MAX_VALUE * 2.0f; // Infinity
```

Ngược lại, số rất nhỏ có thể **underflow** thành 0.0 hoặc còn là số dưới chuẩn; cả hai vẫn là **hữu hạn** theo `isFinite`.

## 3. NaN: tại sao không dùng `==` để kiểm tra?

NaN có quy tắc so sánh đặc biệt. Theo toán tử so sánh số thực Java:

- `NaN == NaN` trả **false**, kể cả khi hai vế là cùng một biến.
- `NaN != NaN` trả **true**.
- `NaN < x`, `NaN <= x`, `NaN > x` và `NaN >= x` đều **false** với mọi giá trị số thực `x`, kể cả NaN.

Ví dụ:

```java
double x = Double.NaN;

System.out.println(x == x); // false
System.out.println(x != x); // true
System.out.println(x < 0);  // false
System.out.println(x > 0);  // false
```

Do đó, cách đúng, rõ ràng và ổn định để phát hiện NaN là:

```java
boolean invalid = Double.isNaN(x);
```

Không viết:

```java
// boolean invalid = (x == Double.NaN); // luôn false
```

Đôi khi bạn thấy mẹo `x != x` để nhận diện NaN. Mẹo này đúng với primitive floating-point theo quy tắc trên, nhưng **`Double.isNaN(x)` nói rõ ý định**, dễ đọc và ít gây nhầm hơn.

### NaN lan truyền qua phép toán

Với các phép cộng/trừ/nhân/chia số thực thông thường, nếu một toán hạng là NaN thì kết quả cũng thường là NaN:

```java
double x = Double.NaN;
System.out.println(Double.isNaN(x + 1.0)); // true
System.out.println(Double.isNaN(x * 0.0)); // true
```

Không nên áp dụng một quy tắc này một cách máy móc cho **mọi API toán học**: mỗi phương thức `Math` có hợp đồng riêng cho các trường hợp đặc biệt. Khi cần đảm bảo dữ liệu đầu vào hợp lệ, kiểm tra `isFinite` **trước** khi đưa vào chuỗi tính toán.

## 4. Infinity: có cả dấu dương và dấu âm

`Float.isInfinite` và `Double.isInfinite` trả `true` cho **cả hai dấu**:

```java
double plus = Double.POSITIVE_INFINITY;
double minus = Double.NEGATIVE_INFINITY;

System.out.println(Double.isInfinite(plus));  // true
System.out.println(Double.isInfinite(minus)); // true
System.out.println(Double.isNaN(plus));       // false
```

Khi cần phân biệt dấu:

```java
if (value == Double.POSITIVE_INFINITY) {
    // giá trị là vô cực dương
} else if (value == Double.NEGATIVE_INFINITY) {
    // giá trị là vô cực âm
}
```

Đây là **đoạn mã minh họa** với biến `double value` đã khai báo trong phạm vi thích hợp, không phải chương trình độc lập.

Với toán tử so sánh số học:

- `Double.POSITIVE_INFINITY > Double.MAX_VALUE` là `true`.
- `Double.NEGATIVE_INFINITY < -Double.MAX_VALUE` là `true`.
- Vô cực không phải NaN, và không phải số hữu hạn.

**Sai lầm thường gặp:** kiểm tra `!Double.isNaN(value)` rồi kết luận giá trị hữu hạn. Điều này **sai** vì ±Infinity không phải NaN.

## 5. isFinite: kiểm tra một giá trị có hữu hạn không

`Double.isFinite(value)` tương đương về ý nghĩa với:

```java
!Double.isNaN(value) && !Double.isInfinite(value)
```

Tương tự:

```java
Float.isFinite(value)
```

tương đương với:

```java
!Float.isNaN(value) && !Float.isInfinite(value)
```

Ví dụ:

```java
System.out.println(Double.isFinite(100.0)); // true
System.out.println(Double.isFinite(0.0)); // true
System.out.println(Double.isFinite(-0.0)); // true
System.out.println(Double.isFinite(Double.MIN_VALUE)); // true
System.out.println(Double.isFinite(Double.MAX_VALUE)); // true

System.out.println(Double.isFinite(Double.NaN)); // false
System.out.println(Double.isFinite(Double.POSITIVE_INFINITY)); // false
System.out.println(Double.isFinite(Double.NEGATIVE_INFINITY)); // false
```

**`Double.MIN_VALUE` không phải số âm nhỏ nhất!** Hằng này là số **dương khác 0 nhỏ nhất** biểu diễn được (số dưới chuẩn). `-Double.MAX_VALUE` gần vùng hữu hạn âm thấp nhất. Việc nhầm `MIN_VALUE` trong số thực với `Integer.MIN_VALUE` trong số nguyên là lỗi rất phổ biến.

Tương tự cho `Float.MIN_VALUE` và `Float.MAX_VALUE`.

### Phân biệt “hữu hạn” với “hợp lệ theo nghiệp vụ”

`Double.isFinite(1.0e300)` là `true`, dù số ấy có thể quá lớn đối với số tiền, cân nặng hay số lượng sản phẩm. `isFinite` **chỉ loại NaN và vô cực**, không tự kiểm tra giá trị dương, không kiểm tra giới hạn min/max, và không khẳng định số có độ chính xác thập phân mong muốn.

Muốn đảm bảo số trong đoạn [0, 100], cần **cả tính hữu hạn và giới hạn**:

```java
boolean valid = Double.isFinite(value)
        && value >= 0.0
        && value <= 100.0;
```

Nếu cần số nguyên chính xác, còn phải kiểm tra phần lẻ và độ chính xác của dữ liệu, không chỉ `isFinite`.

## 6. Chương trình chạy được: phân loại đầy đủ

Tạo file `FloatingClassificationDemo.java`:

```java
public class FloatingClassificationDemo {
    public static void main(String[] args) {
        double[] values = {
                0.0,
                -0.0,
                Double.MIN_VALUE,
                Double.MAX_VALUE,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NaN
        };
        String[] labels = {
                "+0.0", "-0.0", "MIN_VALUE", "MAX_VALUE",
                "+Infinity", "-Infinity", "NaN"
        };

        for (int i = 0; i < values.length; i++) {
            double x = values[i];
            System.out.printf(
                    "%s: NaN=%b, Infinite=%b, Finite=%b%n",
                    labels[i],
                    Double.isNaN(x),
                    Double.isInfinite(x),
                    Double.isFinite(x));
        }

        float f = 3.5f;
        System.out.println("Float 3.5 finite: " + Float.isFinite(f));
        System.out.println("Float NaN: " + Float.isNaN(Float.NaN));
        System.out.println("Float +Infinity: "
                + Float.isInfinite(Float.POSITIVE_INFINITY));
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac FloatingClassificationDemo.java
java FloatingClassificationDemo
```

Kết quả chính xác:

```text
+0.0: NaN=false, Infinite=false, Finite=true
-0.0: NaN=false, Infinite=false, Finite=true
MIN_VALUE: NaN=false, Infinite=false, Finite=true
MAX_VALUE: NaN=false, Infinite=false, Finite=true
+Infinity: NaN=false, Infinite=true, Finite=false
-Infinity: NaN=false, Infinite=true, Finite=false
NaN: NaN=true, Infinite=false, Finite=false
Float 3.5 finite: true
Float NaN: true
Float +Infinity: true
```

Dấu `+` trong `+0.0` hoặc `+Infinity` ở cột nhãn là văn bản do mảng `labels` cung cấp, không phải dấu tự thêm bởi `printf`. Ba cột boolean là kết quả kiểm tra thực tế bằng API.

## 7. So sánh NaN và hai giá trị ±0.0

Ngoài NaN, số thực còn có **+0.0** và **-0.0**. Hai giá trị bằng nhau với toán tử `==` nhưng khác bit dấu:

```java
double a = 0.0;
double b = -0.0;

System.out.println(a == b);          // true
System.out.println(1.0 / a);         // Infinity
System.out.println(1.0 / b);         // -Infinity
System.out.println(Double.isFinite(a)); // true
System.out.println(Double.isFinite(b)); // true
```

Cả hai số 0 đều **hữu hạn**; `isNaN` và `isInfinite` đều trả false.

### `Double.compare` và `Double.equals` có quy tắc khác `==`

Đối với primitive, `NaN == NaN` false. Nhưng khi sử dụng `Double.compare` hoặc phương thức `Double.equals` của wrapper, Java đặt ra một thứ tự/quan hệ bằng nhau **nhằm làm việc nhất quán với sắp xếp và collections**:

- `Double.compare(Double.NaN, Double.NaN)` trả **0**.
- `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả **true**.
- `Double.compare(+0.0, -0.0)` trả **số dương**.
- `Double.valueOf(+0.0).equals(Double.valueOf(-0.0))` trả **false**.

Điều này không mâu thuẫn: `==` trên primitive áp dụng quan hệ số học IEEE 754, còn `compare`/`equals` được đặc tả riêng cho wrapper. Khi so sánh cho mục đích nghiệp vụ, phải biết rõ bạn muốn **so sánh số học** hay **phân biệt các biểu diễn đặc biệt**.

Chương trình minh họa:

```java
public class FloatingComparisonDemo {
    public static void main(String[] args) {
        double nan = Double.NaN;

        System.out.println("NaN == NaN: " + (nan == nan));
        System.out.println("NaN != NaN: " + (nan != nan));
        System.out.println("NaN < 0: " + (nan < 0.0));
        System.out.println("NaN > 0: " + (nan > 0.0));
        System.out.println("Double.isNaN: " + Double.isNaN(nan));
        System.out.println("Double.compare(NaN, NaN): "
                + Double.compare(nan, Double.NaN));
        System.out.println("Double.equals(NaN): "
                + Double.valueOf(nan).equals(Double.valueOf(Double.NaN)));

        System.out.println("+0.0 == -0.0: " + (0.0 == -0.0));
        System.out.println("Double.compare(+0, -0): "
                + Double.compare(0.0, -0.0));
        System.out.println("Double.equals(+0, -0): "
                + Double.valueOf(0.0).equals(Double.valueOf(-0.0)));
    }
}
```

Lưu file `FloatingComparisonDemo.java`, biên dịch và chạy:

```bash
javac FloatingComparisonDemo.java
java FloatingComparisonDemo
```

Kết quả:

```text
NaN == NaN: false
NaN != NaN: true
NaN < 0: false
NaN > 0: false
Double.isNaN: true
Double.compare(NaN, NaN): 0
Double.equals(NaN): true
+0.0 == -0.0: true
Double.compare(+0, -0): 1
Double.equals(+0, -0): false
```

Nếu mục tiêu là **phát hiện NaN**, vẫn nên gọi `Double.isNaN`/`Float.isNaN`, thay vì tìm cách kiểm tra bằng wrapper `equals` hoặc thao tác so sánh bit.

## 8. Một biểu thức kiểm tra phạm vi có thể lọt NaN

Đây là lỗi logic quan trọng. Xem cách kiểm tra một giá trị phải ở trong đoạn từ 0 đến 100:

```java
double value = Double.NaN;

boolean outside = value < 0.0 || value > 100.0;
System.out.println(outside); // false
```

Vì **mọi so sánh quan hệ với NaN đều false**, `outside` trở thành false. Nếu ứng dụng chỉ bác bỏ giá trị khi `outside == true`, NaN sẽ bị **lọt qua** như một giá trị “không nằm ngoài phạm vi”, dù thực ra không hợp lệ.

Cách an toàn:

```java
boolean accepted = Double.isFinite(value)
        && value >= 0.0
        && value <= 100.0;
```

Hoặc muốn kiểm tra điều kiện bị từ chối:

```java
boolean rejected = !Double.isFinite(value)
        || value < 0.0
        || value > 100.0;
```

Nhờ kiểm tra `isFinite` trước, cả NaN và ±Infinity đều bị loại. Tính short-circuit của `&&`/`||` giúp biểu thức thể hiện rõ các bước kiểm tra; không phải vì việc so sánh NaN ném ngoại lệ (nó không ném).

Nếu chỉ kiểm tra `value >= 0.0 && value <= 100.0`, NaN cũng bị loại vì hai so sánh đều false. Tuy nhiên thêm `isFinite` giúp **diễn đạt trực tiếp yêu cầu** và tránh các lỗi khi tái cấu trúc biểu thức theo dạng “không ở ngoài miền”.

## 9. Cần phân loại trước hay sau chuyển từ `double` sang `float`?

`double` có miền độ lớn rộng hơn `float`. Một số đang hữu hạn với `double` có thể thành Infinity sau khi ép hẹp sang `float`:

```java
double big = 1.0e40;
float narrowed = (float) big;

System.out.println(Double.isFinite(big));  // true
System.out.println(Float.isFinite(narrowed));  // false
System.out.println(Float.isInfinite(narrowed)); // true
```

Tương tự, giá trị `double` rất nhỏ có thể bị làm tròn thành `0.0f` khi chuyển sang `float`. Số 0 vẫn hữu hạn, nhưng thông tin độ lớn đã mất.

Do đó cần đặt kiểm tra **ở đúng biên dữ liệu**:

- Nếu API nhận `double` và yêu cầu dữ liệu `double` hữu hạn, kiểm tra `Double.isFinite` trước.
- Nếu API **phải lưu thành `float`**, kiểm tra thêm **`Float.isFinite` sau chuyển đổi** khi không cho phép Infinity ở đích.
- Nếu cần chống mất chính xác do ép kiểu hoặc làm tròn, phải dùng kiểm tra khác; `isFinite` không đủ để chứng minh dữ liệu được bảo toàn chính xác.

### Ví dụ ngắn về chuyển đổi

```java
double finiteDouble = 1.0e40;
float overflowedFloat = (float) finiteDouble;

System.out.println("double finite: "
        + Double.isFinite(finiteDouble));
System.out.println("float finite: "
        + Float.isFinite(overflowedFloat));
System.out.println("float infinite: "
        + Float.isInfinite(overflowedFloat));
```

Kết quả:

```text
double finite: true
float finite: false
float infinite: true
```

Đây là **đoạn minh họa đặt bên trong phương thức Java** (không có class/`main` độc lập). Nó nhấn mạnh rằng không nên kiểm tra một lần ở kiểu nguồn rồi mặc định mọi chuyển đổi về sau đều an toàn.

## 10. Kiểm tra chuỗi đầu vào và API trước khi tính toán

Một số API parse số thực của Java có thể chấp nhận chuỗi biểu diễn giá trị đặc biệt, chẳng hạn `"NaN"`, `"Infinity"` và `"-Infinity"`. Vì thế parse thành công **không** có nghĩa dữ liệu hữu hạn.

Thiết kế phương thức parse chấp nhận **chỉ số hữu hạn**:

```java
static double parseFiniteDouble(String text) {
    double value = Double.parseDouble(text);
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("non-finite number");
    }
    return value;
}
```

`Double.parseDouble` có thể ném `NumberFormatException` với chuỗi sai cú pháp; phương thức trên dùng `IllegalArgumentException` để từ chối đầu vào đã parse được nhưng không hữu hạn. Nếu đầu vào do người dùng cung cấp, cần chuyển những lỗi này thành thông báo phù hợp giao diện/API, thay vì đưa nguyên stack trace ra ngoài.

### Chương trình chạy được

Tạo file `FiniteInputDemo.java`:

```java
public class FiniteInputDemo {
    static double parseFiniteDouble(String text) {
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite number");
        }
        return value;
    }

    public static void main(String[] args) {
        String[] samples = {
                "1.25", "0", "NaN", "Infinity", "-Infinity",
                "1e309", "abc"
        };

        for (String text : samples) {
            try {
                double value = parseFiniteDouble(text);
                System.out.println(text + " -> accepted: " + value);
            } catch (NumberFormatException e) {
                System.out.println(text + " -> invalid format");
            } catch (IllegalArgumentException e) {
                System.out.println(text + " -> non-finite");
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FiniteInputDemo.java
java FiniteInputDemo
```

Kết quả:

```text
1.25 -> accepted: 1.25
0 -> accepted: 0.0
NaN -> non-finite
Infinity -> non-finite
-Infinity -> non-finite
1e309 -> non-finite
abc -> invalid format
```

Chuỗi `"1e309"` có cú pháp số khoa học hợp lệ, nhưng độ lớn vượt miền hữu hạn `double` và được parse thành +Infinity. Vì vậy cần kiểm tra **sau parse**, không chỉ kiểm tra cú pháp chuỗi.

Nếu giá trị là đơn giá, tỷ lệ phần trăm, khoảng thời gian hoặc một đại lượng có miền xác định, bổ sung kiểm tra miền **sau** bước `isFinite`.

## 11. Lưu ý với `null`, wrapper và dữ liệu thiếu

Các phương thức `Float.isNaN(float)`, `Double.isNaN(double)`, `isInfinite` và `isFinite` nhận **primitive**, không nhận giá trị `null` trực tiếp. Nếu bạn truyền một biến kiểu wrapper có thể null, Java có thể **unbox** trước:

```java
Double nullable = null;

// boolean ok = Double.isFinite(nullable);
// sẽ ném NullPointerException khi unbox null
```

Đừng nhầm “thiếu dữ liệu” với “NaN”:

- **`null`** là tham chiếu không có đối tượng, có thể dùng trong `Double`/`Float`.
- **`Double.NaN`/`Float.NaN`** là một giá trị dấu phẩy động đặc biệt, **không phải null**.
- **Infinity** cũng là giá trị số thực đặc biệt, không phải null.

Một đoạn kiểm tra an toàn, có chính sách rõ ràng:

```java
static boolean isPresentAndFinite(Double value) {
    return value != null && Double.isFinite(value);
}
```

Đây là đoạn **phương thức minh họa**, đặt trong class. Nhờ short-circuit `&&`, khi `value == null`, Java không đánh giá vế phải nên không unbox null.

Nếu dữ liệu thiếu cần được coi là lỗi bắt buộc nhập, đừng âm thầm đổi null thành 0.0; nên phân biệt lỗi thiếu dữ liệu và lỗi giá trị không hữu hạn để xử lý chính xác.

## 12. isFinite không phải bộ kiểm tra tính chính xác

Một số `double` hữu hạn vẫn có thể là kết quả của sai số do biểu diễn nhị phân:

```java
double sum = 0.1 + 0.2;
System.out.println(sum);                 // 0.30000000000000004
System.out.println(Double.isFinite(sum)); // true
System.out.println(sum == 0.3);          // false
```

`Double.isFinite(sum)` trả true vì sum không phải NaN hoặc Infinity; nó **không** chứng minh sum bằng 0.3 theo cách so sánh bit hoặc biểu diễn thập phân mong muốn.

Với số thực, một kiểm tra so sánh gần đúng có thể dùng **ngưỡng sai số phù hợp miền ứng dụng** khi mục đích là so sánh kết quả tính toán gần nhau. Chỉ dùng ngưỡng cố định mà không xem xét độ lớn có thể gây sai cho nhiều bài toán.

Khi xử lý tiền hoặc số thập phân cần chính xác theo quy tắc nghiệp vụ, cân nhắc `BigDecimal` với nguồn dữ liệu thập phân thích hợp và chính sách làm tròn rõ ràng. `BigDecimal` không có khái niệm giá trị NaN hay Infinity như `double`; vì vậy một số phép chuyển đầu vào đặc biệt sẽ phải được từ chối.

Ngoài ra, `-0.0` là hữu hạn nhưng khác `+0.0` ở bit dấu; nếu thuật toán liên quan đến nghịch đảo hoặc bảo toàn biểu diễn, cần cân nhắc dấu của 0.

## 13. Thực hành phát hiện kết quả không hợp lệ sau phép toán

Nếu đầu vào đều hữu hạn, kết quả **không được bảo đảm** vẫn hữu hạn. Có thể tràn khi nhân/cộng hoặc tạo NaN từ phép toán không xác định:

```java
double a = Double.MAX_VALUE;
double b = 2.0;

double product = a * b; // +Infinity
System.out.println(Double.isFinite(a)); // true
System.out.println(Double.isFinite(b)); // true
System.out.println(Double.isFinite(product)); // false
```

Vì vậy trong đoạn tính toán quan trọng, kiểm tra tính hữu hạn ở **các ranh giới có ý nghĩa**: khi nhận dữ liệu đầu vào, sau những phép có thể tạo giá trị đặc biệt, và trước khi lưu/trả kết quả nếu hợp đồng API không cho phép các giá trị ấy.

Ví dụ chức năng nghiệp vụ chỉ chấp nhận kết quả hữu hạn:

```java
static double multiplyFinite(double a, double b) {
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        throw new IllegalArgumentException("non-finite input");
    }
    double result = a * b;
    if (!Double.isFinite(result)) {
        throw new ArithmeticException("non-finite product");
    }
    return result;
}
```

Đây là **phương thức minh họa**, đặt trong class để sử dụng. Nó phát hiện NaN và Infinity, nhưng không ngăn mọi vấn đề: kết quả có thể **underflow về 0** hoặc có **sai số làm tròn** mà vẫn hữu hạn. Nếu những tình huống ấy cũng quan trọng, bạn phải đặt thêm quy tắc kiểm tra.

## 14. Những lỗi phổ biến

| Nhầm lẫn | Kết quả thực tế | Cách xử lý |
|---|---|---|
| `x == Double.NaN` kiểm tra NaN | Luôn false | Dùng `Double.isNaN(x)` |
| `x != Double.NaN` nghĩa là không phải NaN | Luôn true, kể cả x là NaN | Dùng `!Double.isNaN(x)` |
| `!isNaN(x)` nghĩa là hữu hạn | Infinity không phải NaN | Dùng `isFinite(x)` |
| `isInfinite(x)` phân biệt dấu vô cực | Trả true cho cả ±Infinity | So sánh với hằng dấu tương ứng nếu cần |
| `x < min || x > max` sẽ loại mọi dữ liệu sai | NaN làm cả hai vế false | Dùng `!isFinite(x) || x < min || x > max` |
| `isFinite` loại số cực lớn | `Double.MAX_VALUE` vẫn hữu hạn | Kiểm tra giới hạn nghiệp vụ |
| `isFinite` phát hiện mất độ chính xác | Sai số làm tròn vẫn hữu hạn | Kiểm tra độ chính xác theo mục tiêu |
| Kiểm tra `double` hữu hạn là đủ trước khi cast `float` | Cast có thể tạo Infinity | Kiểm tra `Float.isFinite` sau cast |
| `Double.MIN_VALUE` là số âm nhỏ nhất | Đây là số dương khác 0 nhỏ nhất | Dùng `-Double.MAX_VALUE` cho cận âm hữu hạn |
| Parse thành công nghĩa là số bình thường | Có thể parse NaN/Infinity | Kiểm tra `isFinite` sau parse |
| `Double.isFinite(nullWrapper)` trả false | Unboxing null ném NPE | Kiểm tra `wrapper != null` trước |
| Đầu vào hữu hạn thì đầu ra cũng hữu hạn | Phép toán có thể tràn hoặc tạo NaN | Kiểm tra kết quả khi cần |

## 15. Bài tập tự kiểm tra

**Câu 1.** `Double.isNaN(0.0 / 0.0)` bằng gì?  
**Đáp án:** `true`.

**Câu 2.** `Double.isInfinite(1.0 / 0.0)` và `Double.isInfinite(-1.0 / 0.0)` bằng gì?  
**Đáp án:** Cả hai đều `true` vì phương thức nhận biết cả hai dấu vô cực.

**Câu 3.** `Double.isFinite(Double.NaN)` có đúng không?  
**Đáp án:** Không, là `false`.

**Câu 4.** `Double.isFinite(Double.MIN_VALUE)` có đúng không?  
**Đáp án:** Có. `MIN_VALUE` là số dưới chuẩn dương nhỏ nhất, nhưng vẫn hữu hạn.

**Câu 5.** Vì sao `double x = Double.NaN; x == x` trả false?  
**Đáp án:** Theo quy tắc so sánh dấu phẩy động, NaN không bằng bất kỳ giá trị nào, kể cả chính nó, khi dùng `==` primitive.

**Câu 6.** Có thể dùng `value < 0 || value > 100` để từ chối mọi giá trị sai không?  
**Đáp án:** Không. NaN khiến cả hai phép so sánh đều false. Cần kiểm tra `!Double.isFinite(value)` hoặc xác minh cả hữu hạn và thuộc miền.

**Câu 7.** `Double.compare(Double.NaN, Double.NaN)` bằng bao nhiêu?  
**Đáp án:** 0. `compare` dành cho thứ tự/so sánh wrapper, khác quan hệ `==` primitive.

**Câu 8.** `+0.0 == -0.0` có đúng không, và `isFinite` của chúng thế nào?  
**Đáp án:** `==` là true; cả hai đều hữu hạn. Chúng vẫn có bit dấu khác nhau.

**Câu 9.** Nếu `double x = 1e40` rồi ép `float y = (float) x` thì `Double.isFinite(x)` và `Float.isFinite(y)` cho gì?  
**Đáp án:** Lần lượt true và false vì x hữu hạn dưới `double` nhưng y trở thành Infinity dưới `float`.

**Câu 10.** Dữ liệu `Double nullable = null` truyền vào `Double.isNaN(nullable)` có an toàn không?  
**Đáp án:** Không. Java cần unbox để lấy `double` và sẽ ném `NullPointerException`.

**Câu 11.** `double x = 0.1 + 0.2` có hữu hạn không, và có chắc bằng `0.3` qua `==` không?  
**Đáp án:** Hữu hạn, nhưng `x == 0.3` thường false trong Java vì x là 0.30000000000000004.

**Câu 12.** Hai toán hạng `double` đều hữu hạn có bảo đảm tích hữu hạn không?  
**Đáp án:** Không. `Double.MAX_VALUE * 2.0` cho Infinity; cần kiểm tra kết quả nếu ứng dụng yêu cầu giá trị hữu hạn.

## 16. Tóm tắt và tài liệu tham khảo

- **`isNaN(x)`**: nhận biết chính xác trường hợp NaN.
- **`isInfinite(x)`**: nhận biết +Infinity và -Infinity.
- **`isFinite(x)`**: chỉ true khi x không phải NaN và không phải ±Infinity.
- **NaN không dùng `==` để phát hiện**; các phép `<`, `>`, `<=`, `>=` với NaN đều false.
- **Hữu hạn không đồng nghĩa hợp lệ hay chính xác**: vẫn cần kiểm tra miền, sai số, chính sách null và kết quả sau phép tính.
- **`float` và `double` có miền khác nhau**; một `double` hữu hạn có thể thành Infinity khi ép sang `float`.

Tra cứu API chính thức: [Float — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html), [Double — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html), và [Java Language Specification — Floating-Point Operations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-004">+0.0 và -0.0 với toán tử ==</a>

<details>
<summary>Xem chi tiết</summary>

# `+0.0` và `-0.0` với toán tử `==` trong Java

Trong Java, hai giá trị dấu phẩy động `+0.0` (zero dương) và `-0.0` (zero âm) **bằng nhau theo toán tử so sánh số học `==`**:

```java
System.out.println(+0.0 == -0.0);  // true
System.out.println(0.0f == -0.0f);  // true
```

Tuy nhiên, chúng **không có biểu diễn bit giống nhau** và **không thể thay thế nhau trong mọi phép toán**. Ví dụ:

```java
System.out.println(1.0 / +0.0); // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

Điểm quan trọng của bài học là phân biệt **bằng nhau theo số học**, **giống nhau về biểu diễn**, và **bằng nhau theo các API đối tượng** như `Double.equals` hoặc `Double.compare`. Ba khái niệm này cho kết quả khác nhau với signed zero.

## 1. Tại sao số 0 lại có dấu?

Java dùng dấu phẩy động nhị phân theo IEEE 754 cho `float` và `double`. Một giá trị dấu phẩy động có **bit dấu** riêng ngoài phần số mũ và phần trị. Do vậy `0.0` và `-0.0` là hai mẫu bit hợp lệ khác nhau:

| Kiểu | `+0.0` (hex bit pattern) | `-0.0` (hex bit pattern) |
| --- | --- | --- |
| `float` (32 bit) | `0x00000000` | `0x80000000` |
| `double` (64 bit) | `0x0000000000000000` | `0x8000000000000000` |

Ở cả hai kiểu, phần trị và phần số mũ đều bằng 0; chỉ bit dấu khác nhau. Thông thường `0.0` viết không có dấu là dương zero; biểu thức `-0.0` tạo âm zero.

```java
double positiveZero = 0.0;
double negativeZero = -0.0;

System.out.println(positiveZero); // 0.0
System.out.println(negativeZero); // -0.0
```

Việc Java có hai zero không có nghĩa nó có hai giá trị số nguyên 0. Các kiểu `byte`, `short`, `int`, `long` không có âm zero riêng: biểu thức nguyên `-0` có cùng biểu diễn và giá trị `0`. Signed zero là đặc điểm của `float`/`double`.

### Dấu zero có thể xuất hiện từ phép tính

Âm zero không chỉ có thể viết bằng literal. Ví dụ một giá trị âm rất nhỏ có thể **underflow** về `-0.0` khi kết quả quá gần 0:

```java
double verySmallNegative = -Double.MIN_VALUE;
double roundedToNegativeZero = verySmallNegative / 2.0;
// -0.0
```

`Double.MIN_VALUE` là số `double` dương nhỏ nhất lớn hơn 0, không phải giá trị âm có độ lớn lớn nhất. Vì vậy `-Double.MIN_VALUE` là một giá trị âm rất nhỏ; chia thêm cho 2 làm tròn thành âm zero.

## 2. `==` coi `+0.0` và `-0.0` bằng nhau

Với toán hạng primitive số thực, Java áp dụng quy tắc **floating-point equality**. Trong đó `+0.0` và `-0.0` được xem là **bằng nhau**, còn `NaN` không bằng bất kỳ giá trị nào, kể cả chính nó.

```java
double a = +0.0;
double b = -0.0;

System.out.println(a == b); // true
System.out.println(a != b); // false
System.out.println(a < b);  // false
System.out.println(a > b);  // false
System.out.println(a <= b); // true
System.out.println(a >= b); // true
```

Ở ngữ cảnh so sánh số học, cả hai zero có cùng vị trí tại gốc trục số. `==` **không kiểm tra bit dấu**; nó trả lời rằng hai giá trị số có bằng nhau theo quy tắc toán tử Java hay không.

Bảng tóm tắt:

| Biểu thức | Kết quả | Ý nghĩa |
| --- | --- | --- |
| `+0.0 == -0.0` | `true` | Bằng nhau theo số học |
| `+0.0 != -0.0` | `false` | Không được xem là khác nhau |
| `-0.0 < +0.0` | `false` | Không nhỏ hơn theo phép so sánh số |
| `-0.0 <= +0.0` | `true` | Có bằng nhau |
| `Double.NaN == Double.NaN` | `false` | Quy tắc riêng của NaN |

Cần chú ý **kiểu toán hạng**. `0.0` là literal `double`, `0.0f` là literal `float`. Nếu so sánh `+0.0f == -0.0`, toán hạng `float` được mở rộng thành `double` rồi so sánh; kết quả vẫn `true`.

## 3. Bằng nhau về số học không có nghĩa giống bit

Có thể đọc bit của `double` và `float` bằng các phương thức chuyên dụng:

```java
long pos = Double.doubleToRawLongBits(+0.0);
long neg = Double.doubleToRawLongBits(-0.0);

System.out.println(Long.toHexString(pos)); // 0
System.out.println(Long.toHexString(neg)); // 8000000000000000
System.out.println(pos == neg);            // false
```

Với `float`:

```java
int pos = Float.floatToRawIntBits(+0.0f);
int neg = Float.floatToRawIntBits(-0.0f);

System.out.println(Integer.toHexString(pos)); // 0
System.out.println(Integer.toHexString(neg)); // 80000000
```

`Double.doubleToRawLongBits` và `Float.floatToRawIntBits` rất hữu ích khi debug biểu diễn bit, giao thức nhị phân hoặc lỗi số học. Với signed zero, phương thức có hoặc không có chữ `Raw` đều phân biệt dấu; khác biệt quan trọng của phiên bản `Raw` là cách giữ nguyên các mẫu bit NaN thay vì canonicalize NaN.

**Không nhầm** hai loại phép so sánh:

```java
double x = +0.0;
double y = -0.0;

boolean equalAsNumbers = x == y; // true
boolean sameBitPattern =
        Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y); // false
```

Nếu nghiệp vụ chỉ cần so sánh giá trị số, thường dùng `==` là có ý nghĩa cho trường hợp zero. Nếu nghiệp vụ cần phân biệt dấu zero (chẳng hạn tính toán khoa học), phải chọn cách kiểm tra phù hợp.

## 4. Vì sao `1.0 / +0.0` khác `1.0 / -0.0`?

Phép chia dấu phẩy động theo IEEE 754 **sử dụng dấu của zero**. Khi tử số hữu hạn và khác 0, còn mẫu là zero, kết quả là vô cực với dấu tương ứng.

| Phép chia | Kết quả |
| --- | --- |
| `1.0 / +0.0` | `Infinity` |
| `1.0 / -0.0` | `-Infinity` |
| `-1.0 / +0.0` | `-Infinity` |
| `-1.0 / -0.0` | `Infinity` |
| `+0.0 / +0.0` | `NaN` |
| `-0.0 / +0.0` | `NaN` |

Vì vậy:

```java
double x = +0.0;
double y = -0.0;

System.out.println(x == y);  // true
System.out.println(1.0 / x); // Infinity
System.out.println(1.0 / y); // -Infinity
```

**Không mâu thuẫn:** `==` hỏi hai số có bằng nhau về giá trị không; phép chia có quy tắc sử dụng bit dấu để chọn dấu vô cực. Hai giá trị có thể bằng nhau theo một quan hệ so sánh nhưng ảnh hưởng khác nhau tới một phép toán khác.

Đối với `int` hoặc `long`, chia cho 0 ném `ArithmeticException` khi thực thi. Chỉ vì biểu thức có biến đích `double` không làm phép chia nguyên tự trở thành chia số thực:

```java
int numerator = 1;
int denominator = 0;
// double invalid = numerator / denominator; // nếu chạy: ArithmeticException
double floating = (double) numerator / denominator; // Infinity
```

Đây là đoạn **minh họa** chứ không phải chương trình độc lập. Chú ý chuyển một toán hạng sang `double` **trước** phép chia để chọn quy tắc floating-point.

## 5. `Double.compare` và `Double.equals` lại phân biệt dấu zero

Đây là điểm dễ gây ngạc nhiên nhất khi chuyển từ primitive sang wrapper `Double`:

```java
System.out.println(Double.compare(-0.0, +0.0)); // -1
System.out.println(Double.compare(+0.0, -0.0)); // 1

System.out.println(Double.valueOf(-0.0)
        .equals(Double.valueOf(+0.0))); // false
```

`Double.compare` tạo một thứ tự nhất quán để sắp xếp số thực theo quy tắc wrapper; nó xem **`-0.0` đứng trước `+0.0`**. `Double.equals` định nghĩa bằng nhau theo biểu diễn chuẩn hóa bit của wrapper, nên **không coi hai zero khác dấu là bằng nhau**.

| Cách kiểm tra | `-0.0` so với `+0.0` |
| --- | --- |
| Primitive `a == b` | `true` |
| Primitive `a != b` | `false` |
| `Double.compare(a, b) == 0` | `false` |
| `Double.valueOf(a).equals(Double.valueOf(b))` | `false` |
| So sánh bit bằng `Double.doubleToLongBits` | Khác nhau |

`Double.compare` trả số âm, 0 hoặc số dương, tùy thứ tự; không nên xây dựng thuật toán đòi hỏi mọi kết quả khác nhau phải chính xác bằng `-1` hoặc `1` theo một quy tắc chung cho các comparator khác.

### Tại sao wrapper cần cách hiểu khác?

`Double.equals` và `Double.hashCode` có hợp đồng phù hợp cho các cấu trúc dữ liệu kiểu `HashMap` hoặc `HashSet`. Java wrapper muốn phân biệt signed zero như hai khóa riêng; đồng thời xử lý NaN nhất quán trong `equals` và `compare`.

```java
java.util.Set<Double> values = new java.util.HashSet<>();
values.add(+0.0);
values.add(-0.0);
System.out.println(values.size()); // 2
```

Nếu so sánh primitive `+0.0 == -0.0` ta được `true`, nhưng một `HashSet<Double>` có thể chứa cả hai phần tử và coi chúng là **hai key khác nhau**. Đây là hệ quả có thật khi lưu kết quả tính toán trong collection.

### Không dùng `==` giữa hai object `Double` để hỏi giá trị

`==` trên **hai tham chiếu object** hỏi chúng có trỏ đến cùng object không, khác hoàn toàn với `==` trên primitive `double`:

```java
Double first = Double.valueOf(+0.0);
Double second = Double.valueOf(-0.0);

// first == second: so sánh identity tham chiếu, không phải numeric equality
boolean primitiveEqual = first.doubleValue() == second.doubleValue(); // true
boolean objectEqual = first.equals(second); // false
```

Đừng diễn giải kết quả của `first == second` thành quy tắc signed zero: đó là câu hỏi về **identity của object**. Việc Java có tự boxing/unboxing trong một số ngữ cảnh không đồng nghĩa so sánh hai wrapper tham chiếu luôn unbox.

## 6. `Float` có quy tắc tương tự `Double`

Đối với `float`:

```java
float a = +0.0f;
float b = -0.0f;

System.out.println(a == b);                  // true
System.out.println(Float.compare(a, b));      // 1
System.out.println(Float.valueOf(a).equals(
        Float.valueOf(b)));                   // false
```

`Float.compare(-0.0f, +0.0f)` trả số âm; `Float.compare(+0.0f, -0.0f)` trả số dương. Vì vậy khi dùng `List<Float>` và sort, giá trị âm zero có thể xuất hiện trước dương zero dù phép `==` cho kết quả bằng nhau.

Một lưu ý khác: `Double.NaN == Double.NaN` trả `false`, nhưng `Double.compare(Double.NaN, Double.NaN) == 0` và `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` đều trả `true`. Wrapper có quan hệ bằng nhau phục vụ collection và sắp xếp, không hoàn toàn giống toán tử số học `==`.

## 7. `Math.copySign`, `Math.abs`, `Math.min` và `Math.max`

Để phát hiện hoặc bảo toàn dấu zero khi cần, Java có các API hữu ích.

### `Math.copySign`: đọc dấu zero mà không so sánh bit

`Math.copySign(magnitude, sign)` trả giá trị có **độ lớn lấy từ đối số đầu** và **dấu từ đối số thứ hai**.

```java
System.out.println(Math.copySign(1.0, +0.0)); // 1.0
System.out.println(Math.copySign(1.0, -0.0)); // -1.0
```

Nếu chỉ cần xem một zero là dương hay âm, áp dụng `Math.copySign(1.0, zero)` có thể dễ đọc. Phương pháp này được dùng khi đã biết biến cần phân tích là zero; tránh suy luận mọi giá trị âm được gặp là âm zero.

### `Math.abs` có thể bỏ dấu âm zero

```java
System.out.println(Math.abs(-0.0));  // 0.0
```

Kết quả là dương zero. Nếu dấu zero có ý nghĩa trong thuật toán, phép lấy trị tuyệt đối đã làm mất thông tin đó.

### `Math.min` và `Math.max` xử lý signed zero riêng

Theo quy tắc Java:

```java
System.out.println(Math.min(+0.0, -0.0)); // -0.0
System.out.println(Math.max(+0.0, -0.0)); // 0.0
```

`Math.min` chọn `-0.0` khi gặp hai zero khác dấu; `Math.max` chọn `+0.0`. Như vậy không thể suy ra mọi API đều phải đối xử signed zero giống `==`.

Lưu ý thêm: có NaN tham gia `Math.min`/`Math.max` có thể tạo NaN; khi chọn giá trị min/max từ dữ liệu ngoài, cần cân nhắc chính sách xử lý NaN.

## 8. Các trường hợp sinh âm zero và cần giữ nó

Signed zero có giá trị trong một số bài toán số học:

- **Underflow:** kết quả âm rất nhỏ có thể làm tròn thành `-0.0`, lưu thông tin dấu của giá trị rất nhỏ trước khi làm tròn.
- **Phép nhân/chia:** dấu các toán hạng ảnh hưởng dấu zero hoặc vô cực trong kết quả.
- **Hàm toán học:** một số phép toán và hàm thư viện có thể dùng signed zero để chọn dấu kết quả hoặc giữ thông tin giới hạn một phía.

Ví dụ:

```java
double underflow = -Double.MIN_VALUE / 2.0; // -0.0
double fromProduct = -2.0 * 0.0;            // -0.0
double reciprocal = 1.0 / fromProduct;      // -Infinity
```

Nếu hệ thống tự chuyển mọi `-0.0` thành `+0.0`, hai loại giá trị sẽ vẫn bằng nhau theo `==`, nhưng một số bước tính sau đó có thể đổi kết quả.

Ở hệ thống thông thường chỉ cần coi zero là zero, việc phân biệt dấu có thể không cần thiết. **Phụ thuộc vào mục đích dữ liệu**: không phải mọi chương trình đều nên dùng so sánh bit thay thế `==`.

## 9. Thiết kế so sánh, sắp xếp và khóa tập hợp

Khi làm việc với zero có dấu, cần chọn **một quy tắc nhất quán**.

| Mục tiêu | Cách làm điển hình | Hệ quả |
| --- | --- | --- |
| Chỉ hỏi hai giá trị primitive có bằng nhau về số học? | `a == b` | `+0.0` bằng `-0.0`; NaN không bằng chính nó |
| Muốn phân biệt bit dấu zero | `Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b)` | Hai zero khác nhau |
| Muốn thứ tự tương thích với wrapper `Double` | `Double.compare(a,b)` | `-0.0` đứng trước `+0.0` |
| Muốn dùng `Double` làm khóa map/set | `Double.equals`/`hashCode` theo collection | Hai zero khác dấu được xem là hai khóa |
| Muốn coi hai zero là một khóa | **Chuẩn hóa** `x == 0.0 ? 0.0 : x` trước khi làm khóa | Mất chủ đích phân biệt dấu |
| Cần giữ dấu zero qua xử lý | Giữ primitive/raw bits hoặc cấu trúc dữ liệu phù hợp | Không làm mất dấu do normalize hay `abs` |

Ví dụ chuẩn hóa trước khi lưu `Double` vào set:

```java
static double normalizeZero(double x) {
    return x == 0.0 ? 0.0 : x;
}
```

Method này là **đoạn đặt trong class**, không phải chương trình độc lập. Nó chuyển cả `-0.0` và `+0.0` về `+0.0`, còn các số khác được giữ nguyên. Với `NaN`, điều kiện `x == 0.0` là `false`, nên giá trị NaN được trả về không đổi. Đây là **chính sách chủ động** để coi signed zero tương đương theo khóa, không phải hành vi sẵn có của `Double.equals`.

Nếu dùng `TreeSet<Double>`, thứ tự mặc định dựa vào so sánh wrapper cũng phân biệt hai zero; khi dùng comparator riêng cho tập có thứ tự, comparator cần nhất quán với định nghĩa “trùng nhau” mà ứng dụng mong muốn.

## 10. Chương trình Java 17+ đầy đủ

Lưu thành tệp **`SignedZeroEqualityDemo.java`**:

```java
import java.util.HashSet;
import java.util.Set;

public class SignedZeroEqualityDemo {
    public static void main(String[] args) {
        double positive = +0.0;
        double negative = -0.0;

        System.out.println("primitiveEquals=" + (positive == negative));
        System.out.println("primitiveNotEquals=" + (positive != negative));
        System.out.println("negativeLess=" + (negative < positive));
        System.out.println("negativeLessOrEqual=" + (negative <= positive));

        System.out.println("positiveText=" + positive);
        System.out.println("negativeText=" + negative);
        System.out.println("positiveBits=" +
                Long.toHexString(Double.doubleToRawLongBits(positive)));
        System.out.println("negativeBits=" +
                Long.toHexString(Double.doubleToRawLongBits(negative)));
        System.out.println("rawBitsEqual=" +
                (Double.doubleToRawLongBits(positive)
                        == Double.doubleToRawLongBits(negative)));

        System.out.println("reciprocalPositive=" + (1.0 / positive));
        System.out.println("reciprocalNegative=" + (1.0 / negative));
        System.out.println("zeroOverZero=" + (positive / negative));

        System.out.println("compareNegativePositive=" +
                Double.compare(negative, positive));
        System.out.println("comparePositiveNegative=" +
                Double.compare(positive, negative));
        System.out.println("wrapperEquals=" +
                Double.valueOf(negative).equals(Double.valueOf(positive)));

        Set<Double> set = new HashSet<>();
        set.add(positive);
        set.add(negative);
        System.out.println("setSize=" + set.size());

        System.out.println("copySignPositive=" + Math.copySign(1.0, positive));
        System.out.println("copySignNegative=" + Math.copySign(1.0, negative));
        System.out.println("absNegative=" + Math.abs(negative));
        System.out.println("minZero=" + Math.min(positive, negative));
        System.out.println("maxZero=" + Math.max(positive, negative));

        System.out.println("floatEquals=" + (+0.0f == -0.0f));
        System.out.println("negativeFloatBits=" +
                Integer.toHexString(Float.floatToRawIntBits(-0.0f)));
        System.out.println("negativeUnderflow=" +
                (-Double.MIN_VALUE / 2.0));

        System.out.println("nanPrimitiveEquals=" + (Double.NaN == Double.NaN));
        System.out.println("nanWrapperCompare=" +
                Double.compare(Double.NaN, Double.NaN));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac SignedZeroEqualityDemo.java
java SignedZeroEqualityDemo
```

Kết quả chính xác:

```text
primitiveEquals=true
primitiveNotEquals=false
negativeLess=false
negativeLessOrEqual=true
positiveText=0.0
negativeText=-0.0
positiveBits=0
negativeBits=8000000000000000
rawBitsEqual=false
reciprocalPositive=Infinity
reciprocalNegative=-Infinity
zeroOverZero=NaN
compareNegativePositive=-1
comparePositiveNegative=1
wrapperEquals=false
setSize=2
copySignPositive=1.0
copySignNegative=-1.0
absNegative=0.0
minZero=-0.0
maxZero=0.0
floatEquals=true
negativeFloatBits=80000000
negativeUnderflow=-0.0
nanPrimitiveEquals=false
nanWrapperCompare=0
```

Dòng `primitiveEquals=true` đặt cạnh `rawBitsEqual=false` cho thấy rõ sự khác biệt giữa **so sánh giá trị số học** và **so sánh biểu diễn bit**. Các dòng `compareNegativePositive=-1`, `wrapperEquals=false` và `setSize=2` chứng minh rằng API wrapper không có cùng quy tắc bằng nhau với primitive `==`.

## 11. Lỗi phổ biến và checklist

| Hiểu nhầm | Giải thích đúng |
| --- | --- |
| “`+0.0 == -0.0` là `false` vì bit dấu khác nhau.” | Sai: `==` so sánh số học và cho `true`. |
| “`+0.0` và `-0.0` có cùng mọi hành vi.” | Sai: `1.0 / ±0.0` tạo vô cực khác dấu; các API khác cũng phân biệt. |
| “`Double.compare(+0.0,-0.0) == 0` vì `==` trả `true`.” | Sai: `Double.compare` xếp `-0.0` trước `+0.0`. |
| “`Double.valueOf(+0.0).equals(Double.valueOf(-0.0))` cho `true`.” | Sai: `Double.equals` phân biệt dấu zero. |
| “`HashSet<Double>` chắc chắn chỉ có một phần tử nếu thêm cả hai zero.” | Sai: có thể giữ hai khóa phân biệt theo `equals`. |
| “`Float` không có âm zero.” | Sai: `-0.0f` có bit dấu và các quy tắc tương ứng. |
| “Nếu Java in `-0.0` thì giá trị nhỏ hơn 0 theo `<`.” | Sai: `-0.0 < +0.0` cho `false`. |
| “`Math.abs(-0.0)` giữ âm zero.” | Sai: trả `+0.0`. |
| “`Double.NaN == Double.NaN` giống `Double.compare(NaN,NaN) == 0`.” | Sai: toán tử `==` cho `false`, `Double.compare` có thể cho `0`. |
| “So sánh hai `Double` object bằng `==` là so sánh giá trị.” | Sai: hai tham chiếu wrapper được `==` so sánh identity, trừ khi có ngữ cảnh unboxing khác. |

Khi xử lý zero có dấu, hãy kiểm tra:

1. **Đang so sánh primitive hay object?** `==` trên `double`/`float` coi hai zero bằng nhau; `==` trên hai wrapper tham chiếu lại xét identity.
2. **Muốn so sánh giá trị số học hay bit lưu trữ?** Chọn `==` hoặc `doubleToRawLongBits`/`floatToRawIntBits` tương ứng.
3. **Đang sắp xếp hoặc làm key của collection?** Hiểu `Double.compare`, `Double.equals` và `hashCode` phân biệt signed zero.
4. **Dấu zero có ý nghĩa ở các phép tiếp theo?** Kiểm tra phép chia, `Math.copySign`, underflow và các hàm có quy tắc dấu.
5. **Ứng dụng có muốn gộp hai zero không?** Chuẩn hóa rõ ràng trước khi lưu và so sánh bằng cấu trúc dữ liệu, thay vì giả định collection tự dùng primitive `==`.

**Ghi nhớ:** `+0.0 == -0.0` là **`true`** với primitive `float`/`double`. Tuy nhiên, **bit dấu khác nhau**, phép chia cho zero cho **vô cực khác dấu**, và `Double.compare`/`Double.equals` **phân biệt hai giá trị zero này**. Luôn chọn quy tắc so sánh phù hợp với mục tiêu của chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-005">Float.compare, Double.compare và thứ tự toàn phần</a>

<details>
<summary>Xem chi tiết</summary>

# Float.compare, Double.compare và thứ tự toàn phần trong Java

Trong Java, hai số `float` hoặc `double` có thể được so sánh bằng các toán tử `<`, `>`, `<=`, `>=`, `==`, `!=` hoặc bằng `Float.compare(a, b)` và `Double.compare(a, b)`. Với số hữu hạn thông thường, hai cách thường dẫn đến cùng nhận định lớn/nhỏ; nhưng khi gặp **NaN** và **số 0 có dấu** (`-0.0`, `+0.0`), hành vi khác nhau có chủ đích.

`Float.compare` và `Double.compare` tạo ra một thứ tự so sánh **toàn phần trên các giá trị theo quy ước Java**: kể cả NaN cũng được xếp vào một vị trí xác định để có thể sắp xếp, tìm kiếm và dùng làm khóa collection. Hiểu sự khác nhau này giúp tránh lỗi rất khó phát hiện khi chuyển từ tính toán số học sang `sort`, `TreeSet`, `TreeMap` hoặc wrapper `Float`/`Double`.

## 1. Cần phân biệt hai mục đích so sánh

**Toán tử so sánh primitive** phản ánh quan hệ số học của các toán hạng dấu phẩy động:

- `1.0 < 2.0` là `true`; `2.0 == 2.0` là `true`.
- `-0.0 == +0.0` là `true`: so sánh bằng của primitive xem hai số 0 có dấu là bằng nhau.
- `Double.NaN == Double.NaN` là `false`, kể cả so sánh cùng một biến NaN với chính nó.
- Khi có NaN, các quan hệ `<`, `<=`, `>`, `>=`, `==` đều trả `false`; riêng `!=` trả `true`.

**`Double.compare(a, b)` / `Float.compare(a, b)`** tạo kết quả kiểu `int`:

| Kết quả | Ý nghĩa |
| --- | --- |
| Nhỏ hơn 0 | `a` đứng trước `b` theo thứ tự Java |
| Bằng 0 | `a` và `b` tương đương trong thứ tự Java |
| Lớn hơn 0 | `a` đứng sau `b` theo thứ tự Java |

Hợp đồng API chỉ bảo đảm **dấu** âm/0/dương. Đừng viết logic dựa vào giả định mọi lời gọi luôn trả đúng `-1` hoặc `1`; các ví dụ cụ thể bên dưới có thể trả những số ấy nhưng hợp đồng là dấu kết quả.

## 2. Thứ tự toàn phần mà Java sử dụng

Theo `Double.compare`, các giá trị được sắp như sau (từ nhỏ tới lớn):

~~~text
-Infinity < số hữu hạn âm < -0.0 < +0.0
          < số hữu hạn dương < +Infinity < NaN
~~~

Các điểm cốt lõi:

1. Số hữu hạn và vô cực được xếp theo thứ tự số học bình thường.
2. **`-0.0` nhỏ hơn `+0.0`**, dù toán tử primitive `==` cho rằng chúng bằng nhau.
3. **NaN đứng sau mọi giá trị khác**, kể cả `Double.POSITIVE_INFINITY`.
4. **Mọi NaN được `Double.compare` xem là tương đương** và cho kết quả 0 khi so sánh với nhau. Điều này đúng dù hai NaN có mẫu bit (*payload*) khác nhau.

`Float.compare` có cùng quy ước thứ tự cho `float`. Các chữ ký cần nhớ:

~~~java
int orderD = Double.compare(1.0, 2.0);      // < 0
int orderF = Float.compare(1.0f, 2.0f);     // < 0
~~~

`Float.compare` nhận tham số `float`; `Double.compare` nhận `double`. Không tự ép dữ liệu `double` về `float` chỉ để gọi `Float.compare`: phép ép có thể làm tròn hoặc làm hai số khác nhau trở nên bằng nhau.

**“Toàn phần” ở đây là thứ tự trên giá trị theo định nghĩa Java**, không phải khả năng phân biệt mọi mẫu bit IEEE 754. Mọi payload NaN được gộp về một lớp tương đương khi so sánh. Điều này khác với việc yêu cầu một thứ tự riêng biệt cho *tất cả các encoding bit* của NaN.

## 3. Số 0 có dấu: `==` và `compare` cố ý khác nhau

IEEE 754 cho phép `+0.0` và `-0.0` là hai mẫu bit khác nhau. Java giữ dấu của 0 trong nhiều phép toán; ví dụ:

~~~java
double positiveZero = +0.0;
double negativeZero = -0.0;

boolean primitiveEqual = positiveZero == negativeZero; // true
int first = Double.compare(negativeZero, positiveZero); // < 0
int second = Double.compare(positiveZero, negativeZero); // > 0

double inversePositive = 1.0 / positiveZero; // Infinity
double inverseNegative = 1.0 / negativeZero; // -Infinity
~~~

Trong ví dụ chạy phía dưới, `Double.compare(-0.0, +0.0)` trả `-1`, còn chiều ngược lại trả `1`. Hai số 0 vì thế **có thể chiếm hai vị trí riêng** trong một `TreeSet<Double>`.

Lưu ý khi dùng logic kiểu `if (x == 0.0)`: biểu thức này đúng với **cả hai** dấu của 0. Nếu phải phân biệt bit dấu vì nghiệp vụ số học, dùng `Double.compare`, `Double.doubleToRawLongBits` hoặc API phù hợp thay vì chỉ dùng `==`.

## 4. NaN: primitive unordered nhưng `compare` vẫn sắp xếp được

NaN có thể xuất hiện từ `0.0 / 0.0`, `Infinity - Infinity`, `Math.sqrt(-1.0)` hoặc một nguồn dữ liệu có giá trị NaN. Toán tử primitive không áp đặt quan hệ thứ tự giữa NaN và số khác:

~~~java
double nan = Double.NaN;

boolean equalsSelf = nan == nan;                 // false
boolean smaller = nan < Double.POSITIVE_INFINITY; // false
boolean greater = nan > Double.POSITIVE_INFINITY; // false
boolean differs = nan != nan;                   // true
~~~

Trong khi đó:

~~~java
int afterInfinity =
        Double.compare(Double.NaN, Double.POSITIVE_INFINITY); // > 0
int equalNaNs =
        Double.compare(Double.NaN, Double.NaN);               // 0
~~~

Không có mâu thuẫn: toán tử primitive mô tả **so sánh số học**, còn `compare` dùng **quy tắc total ordering của API Java** để làm việc với sorting và collections.

Kiểm tra “có phải NaN không” bằng `Double.isNaN(value)` hoặc `Float.isNaN(value)`, **không dùng** `value == Double.NaN` (luôn false) và cũng không dùng `Double.compare(value, Double.NaN) == 0` để che mất ý định phân loại giá trị. Cách thứ hai có thể nhận ra NaN nhưng diễn đạt sai mục đích, khó bảo trì hơn `isNaN`.

## 5. Hai NaN có payload khác nhau vẫn cho `compare == 0`

Một `double` NaN có thể có nhiều mẫu bit IEEE 754 khác nhau. Java có hai API cần phân biệt:

- `Double.doubleToRawLongBits(x)` giữ mẫu bit thô của giá trị `double` tại thời điểm gọi.
- `Double.doubleToLongBits(x)` **chuẩn hóa mọi NaN thành mẫu canonical NaN**; vì vậy thích hợp với quy ước `Double.equals`/`hashCode`.

~~~java
double nanA = Double.longBitsToDouble(0x7ff8_0000_0000_0001L);
double nanB = Double.longBitsToDouble(0x7ff8_0000_0000_0002L);

boolean rawDifferent = Double.doubleToRawLongBits(nanA)
                    != Double.doubleToRawLongBits(nanB); // true
int order = Double.compare(nanA, nanB);        // 0
boolean wrapperEqual =
        Double.valueOf(nanA).equals(Double.valueOf(nanB)); // true
~~~

Đừng dựa vào `compare` để phân biệt payload, dấu NaN hoặc truy vết nguyên vẹn encoding từng NaN. Nếu việc phân biệt payload là một yêu cầu giao thức/bit-level, cần định nghĩa chính sách dựa trên bit thô và biết rằng một số thao tác dấu phẩy động có thể không giữ nguyên payload. Với `float`, cặp API tương ứng là `Float.floatToRawIntBits` và `Float.floatToIntBits`.

## 6. Quan hệ với `Double.equals`, `Float.equals` và `compareTo`

Wrapper `Double` có `equals`/`hashCode` dựa trên biểu diễn chuẩn hóa NaN. Do đó:

| Hai giá trị | Primitive `a == b` | `Double.compare(a,b) == 0` | `Double.valueOf(a).equals(Double.valueOf(b))` |
| --- | --- | --- | --- |
| `2.0` và `2.0` | `true` | `true` | `true` |
| `+0.0` và `-0.0` | `true` | `false` | `false` |
| `NaN` và `NaN` | `false` | `true` | `true` |
| `-Infinity` và `-Infinity` | `true` | `true` | `true` |

`Double.compareTo` sử dụng quy tắc giống `Double.compare`; `Float.compareTo` tương ứng với `Float.compare`. Một lý do tồn tại thứ tự này là để **`compareTo` nhất quán với `equals` của wrapper**, rất quan trọng đối với collection có sắp thứ tự.

Khi viết `Double a = ...; Double b = ...; a.equals(b)`, bạn đang dùng **object equality của wrapper**; còn `a == b` nếu cả hai là object `Double` sẽ kiểm tra **đồng nhất tham chiếu**, không phải so sánh số. Chỉ khi toán hạng được unbox sang primitive, `==` mới thực hiện so sánh dấu phẩy động. Vì vậy muốn nói rõ ngữ nghĩa, hãy xác định kiểu tĩnh của toán hạng trước khi dự đoán kết quả.

## 7. Sắp xếp và cấu trúc dữ liệu

Các API như `Arrays.sort(double[])`, `Arrays.sort(float[])` và comparator dựa trên `Double.compare` hoặc `Float.compare` sử dụng thứ tự xử lý NaN/zero có dấu nhất quán. Với danh sách các `Double`, có thể viết:

~~~java
List<Double> values = new ArrayList<>(Arrays.asList(
        Double.NaN, 3.0, -0.0, Double.NEGATIVE_INFINITY,
        +0.0, Double.POSITIVE_INFINITY, -5.0, Double.NaN));
values.sort(Double::compare);
// [-Infinity, -5.0, -0.0, 0.0, 3.0, Infinity, NaN, NaN]
~~~

Đoạn trên **nằm trong phương thức của lớp có các import `java.util.*`**; ví dụ hoàn chỉnh ở mục 9 có thể chạy trực tiếp.

Với `TreeSet<Double>` (thứ tự tự nhiên theo `Double.compareTo`):

- `-0.0` và `+0.0` được giữ như **hai phần tử phân biệt**.
- Hai giá trị NaN được coi **trùng nhau** và chỉ có một phần tử NaN được giữ.
- NaN đứng cuối khi duyệt tăng dần.

Nếu dùng `TreeMap<Double, ...>` thì `-0.0` và `+0.0` cũng là hai khóa khác nhau, nhưng các NaN được xem là cùng một khóa theo thứ tự comparator. `HashSet<Double>` cũng coi các NaN là bằng nhau và hai zero có dấu khác nhau do `Double.equals`/`hashCode`, dù `HashSet` không bảo đảm thứ tự duyệt.

**Quy tắc cho comparator tự viết:** trả `0` nghĩa là hai khóa tương đương trong `TreeSet`/`TreeMap`. Comparator kiểu `(a, b) -> a < b ? -1 : a > b ? 1 : 0` là **sai** nếu có NaN hoặc số 0 có dấu: nó trả 0 với rất nhiều cặp không tương đương, có thể làm mất phần tử/ghi đè khóa. Dùng `Double.compare(a,b)` hoặc `Comparator.naturalOrder()`.

## 8. Chương trình Java 17+ biên dịch và chạy được

Tạo tệp `CompareDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CompareDemo {
    public static void main(String[] args) {
        double negZero = -0.0;
        double posZero = 0.0;
        double nanA = Double.NaN;
        double nanB = Double.longBitsToDouble(0x7ff8_0000_0000_0001L);
        double nanC = Double.longBitsToDouble(0x7ff8_0000_0000_0002L);

        System.out.println("primitive +0 == -0: " + (posZero == negZero));
        System.out.println("primitive NaN == NaN: " + (nanA == nanA));
        System.out.println("primitive NaN < Infinity: "
                + (nanA < Double.POSITIVE_INFINITY));
        System.out.println("Double.compare(-0,+0): "
                + Double.compare(negZero, posZero));
        System.out.println("Double.compare(+0,-0): "
                + Double.compare(posZero, negZero));
        System.out.println("Double.compare(NaN,+Infinity): "
                + Double.compare(nanA, Double.POSITIVE_INFINITY));
        System.out.println("Double.compare(NaN,NaN): "
                + Double.compare(nanB, nanC));
        System.out.println("raw NaN payloads differ: "
                + (Double.doubleToRawLongBits(nanB)
                != Double.doubleToRawLongBits(nanC)));
        System.out.println("Double.equals(+0,-0): "
                + Double.valueOf(posZero).equals(Double.valueOf(negZero)));
        System.out.println("Double.equals(NaN,NaN): "
                + Double.valueOf(nanB).equals(Double.valueOf(nanC)));
        System.out.println("Float.compare(-0f,+0f): "
                + Float.compare(-0.0f, 0.0f));
        System.out.println("Float.compare(NaN,Infinity): "
                + Float.compare(Float.NaN, Float.POSITIVE_INFINITY));

        List<Double> values = new ArrayList<>(Arrays.asList(
                Double.NaN, 3.0, -0.0, Double.NEGATIVE_INFINITY,
                +0.0, Double.POSITIVE_INFINITY, -5.0, Double.NaN));
        values.sort(Double::compare);
        System.out.println("sorted: " + values);

        Set<Double> tree = new TreeSet<>(values);
        System.out.println("tree: " + tree);
        System.out.println("tree size: " + tree.size());
        System.out.println("nanB == nanC: " + (nanB == nanC));
    }
}
~~~

Lệnh chạy:

~~~bash
javac CompareDemo.java
java CompareDemo
~~~

Kết quả stdout:

~~~text
primitive +0 == -0: true
primitive NaN == NaN: false
primitive NaN < Infinity: false
Double.compare(-0,+0): -1
Double.compare(+0,-0): 1
Double.compare(NaN,+Infinity): 1
Double.compare(NaN,NaN): 0
raw NaN payloads differ: true
Double.equals(+0,-0): false
Double.equals(NaN,NaN): true
Float.compare(-0f,+0f): -1
Float.compare(NaN,Infinity): 1
sorted: [-Infinity, -5.0, -0.0, 0.0, 3.0, Infinity, NaN, NaN]
tree: [-Infinity, -5.0, -0.0, 0.0, 3.0, Infinity, NaN]
tree size: 7
nanB == nanC: false
~~~

Đầu ra cho thấy điều tưởng nghịch lý: `nanB == nanC` là `false` nhưng `Double.compare(nanB,nanC)` là 0, và hai số 0 có dấu có quan hệ ngược lại. Vì `TreeSet` dùng thứ tự tự nhiên của `Double`, nó giữ hai số 0 có dấu và gộp NaN trùng nhau.

## 9. `compare` không phải phép so sánh số gần bằng

Giả sử hai kết quả tính toán gần nhau do sai số dấu phẩy động. `Double.compare(a,b) == 0` chỉ nhận ra **bằng nhau theo thứ tự wrapper**, không tự áp dụng sai số cho phép (*tolerance*). Không dùng nó thay logic so sánh gần đúng của nghiệp vụ.

Ví dụ:

~~~java
double x = 0.1 + 0.2;
double y = 0.3;

boolean exactlyEqual = Double.compare(x, y) == 0; // false
~~~

Nếu bài toán có tolerance, bạn cần quy định đơn vị/độ lớn sai số. Ví dụ đơn giản với số hữu hạn trong miền không gây overflow khi trừ:

~~~java
static boolean approximatelyEqual(
        double a, double b, double absoluteTolerance) {
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }
    if (!Double.isFinite(absoluteTolerance)
            || absoluteTolerance < 0.0) {
        throw new IllegalArgumentException("Invalid tolerance");
    }
    return Math.abs(a - b) <= absoluteTolerance;
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một lớp. Với độ lớn cực lớn, hiệu `a-b` có thể overflow thành vô cực; nhiều bài toán cần kết hợp tolerance tuyệt đối/tương đối với thuật toán phù hợp miền giá trị. Cần quyết định riêng cách xử lý NaN và infinities, vì chúng không có nghĩa “gần bằng” số hữu hạn chỉ nhờ một epsilon.

Đối với **sắp xếp, khóa dữ liệu, tìm kiếm nhị phân**, tránh dùng comparator dựa trên epsilon tùy ý vì quan hệ gần bằng có thể **không bắc cầu** (ví dụ A gần B, B gần C nhưng A không gần C), vi phạm hợp đồng comparator. Dùng `Double.compare` làm thứ tự và tách riêng logic gần bằng.

## 10. Cách chọn phép so sánh

| Nhu cầu | Công cụ thích hợp | Lưu ý |
| --- | --- | --- |
| Quan hệ số học thông thường giữa primitive | `<`, `>`, `==` ... | NaN làm các quan hệ có thứ tự luôn false; ±0 bằng nhau |
| Kiểm tra NaN | `Double.isNaN` / `Float.isNaN` | Không dùng `x == NaN` |
| Định nghĩa thứ tự ổn định để sort | `Double.compare` / `Float.compare` | `-0 < +0 < Infinity < NaN` |
| So sánh wrapper theo hợp đồng equals | `Double.equals` / `Float.equals` | NaN bằng NaN; ±0 khác nhau |
| Kiểm tra đầu vào hữu hạn | `Double.isFinite` / `Float.isFinite` | Loại NaN và vô cực |
| So sánh kết quả gần bằng | Hàm tolerance có đặc tả nghiệp vụ | Không dùng epsilon comparator cho ordered collections |
| Kiểm tra đúng mẫu bit kể cả NaN payload | `doubleToRawLongBits` / `floatToRawIntBits` | Cần chính sách bit-level riêng |

Sai lầm phổ biến cần tránh là triển khai comparator:

~~~java
// CỐ Ý SAI nếu dữ liệu có NaN hoặc số 0 có dấu:
static int brokenCompare(double a, double b) {
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
}
~~~

Với `a = Double.NaN` và `b = 5.0`, hàm trên trả 0, dù hai giá trị không tương đương. Với `-0.0` và `+0.0`, hàm cũng trả 0, khác hợp đồng thứ tự của `Double`. Phiên bản đúng cho dữ liệu `double` là:

~~~java
static int correctCompare(double a, double b) {
    return Double.compare(a, b);
}
~~~

Hai đoạn là **phương thức độc lập để minh họa**, không phải các chương trình chạy có `main`.

## 11. Câu hỏi tự luyện

1. `Double.compare(-0.0, +0.0)` âm, 0 hay dương? `-0.0 == +0.0` thì sao?
2. `Double.NaN > Double.POSITIVE_INFINITY` có trả `true` không? `Double.compare(Double.NaN, Double.POSITIVE_INFINITY)` trả dấu nào?
3. Hai NaN có payload bit khác nhau có thể cùng tồn tại như hai phần tử riêng trong `TreeSet<Double>` dùng natural order không?
4. Vì sao `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả `true`, nhưng `Double.NaN == Double.NaN` trả `false`?
5. Comparator dùng `a < b ? -1 : a > b ? 1 : 0` có gì nguy hiểm khi so sánh NaN? Có nên dùng epsilon comparator cho `TreeMap` không?

**Đáp án:** (1) âm; toán tử primitive trả `true`. (2) toán tử `>` trả `false`, còn `Double.compare` dương. (3) Không, `Double.compare` coi mọi NaN tương đương nên set chỉ có một giá trị NaN. (4) Wrapper áp dụng quy tắc bằng nhau theo bit canonical, còn primitive NaN là unordered. (5) Hàm trả 0 với NaN và nhiều số không tương đương; epsilon comparator có thể vi phạm tính bắc cầu, vì vậy không dùng cho collection có thứ tự.

## Tài liệu tham khảo

- [Java SE 21: Double.compare, equals, compareTo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html).
- [Java SE 21: Float.compare, equals, compareTo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Float.html).
- [JLS `15.20 và `15.21: Relational and Equality Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20).
- [Java SE 21: Comparator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html), [TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-006">equals của Float, Double với NaN và signed zero</a>

<details>
<summary>Xem chi tiết</summary>

# `equals()` của `Float`, `Double` với `NaN` và signed zero trong Java

## 1. Vì sao cùng là so sánh số thực mà cho kết quả khác nhau?

Trong Java, `float` và `double` là **kiểu nguyên thủy** (*primitive*), còn `Float` và `Double` là **lớp bao** (*wrapper*) tương ứng. Toán tử `==` trên các giá trị nguyên thủy tuân theo quy tắc so sánh số thực IEEE 754; phương thức `Float.equals(Object)` và `Double.equals(Object)` lại có một hợp đồng riêng, phù hợp với `Object.equals` và các collection của Java.

Hai trường hợp dễ nhầm nhất là **NaN** (*Not a Number*) và **signed zero** (số không có dấu: `+0.0`, `-0.0`):

```java
double positiveZero = +0.0;
double negativeZero = -0.0;

System.out.println(positiveZero == negativeZero); // true
System.out.println(Double.valueOf(positiveZero)
        .equals(Double.valueOf(negativeZero)));    // false

System.out.println(Double.NaN == Double.NaN); // false
System.out.println(Double.valueOf(Double.NaN)
        .equals(Double.valueOf(Double.NaN))); // true
```

**Đây không phải mâu thuẫn hay lỗi Java**. Hai cơ chế định nghĩa khái niệm “bằng nhau” phục vụ những mục đích khác nhau:

- **So sánh số học bằng `==`:** `NaN` không bằng bất kỳ giá trị nào, kể cả chính nó; `+0.0` và `-0.0` bằng nhau về mặt số học.
- **So sánh `equals` của lớp bao:** tất cả các giá trị `NaN` của cùng kiểu lớp bao được coi là bằng nhau; `+0.0` và `-0.0` **không** bằng nhau.

Các quy tắc trên áp dụng đối xứng cho `Float`/`float` và `Double`/`double` với đúng độ rộng của từng kiểu.

## 2. Hiểu `NaN` và hai mẫu bit của số không

### `NaN` là gì?

`NaN` biểu thị kết quả không phải một số thực hợp lệ trong số học dấu phẩy động, ví dụ:

```java
double invalid = 0.0 / 0.0;
float invalidFloat = 0.0f / 0.0f;

System.out.println(Double.isNaN(invalid)); // true
System.out.println(Float.isNaN(invalidFloat)); // true
```

Theo quy tắc so sánh số thực nguyên thủy, nếu một toán hạng là `NaN`:

- `x == y` cho `false`, kể cả `NaN == NaN`.
- `x != y` cho `true`.
- `x < y`, `x <= y`, `x > y`, `x >= y` đều cho `false`.

Đừng kiểm tra `NaN` bằng biểu thức `value == Double.NaN` hoặc `value == Float.NaN`; phép so sánh đó **luôn false**. Dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`.

`NaN` có nhiều mẫu bit IEEE 754 khả dĩ (*NaN payload*). Chúng không tương ứng với các giá trị số thực thông thường. Java cung cấp các hàm đổi bit cho phép phân biệt giữa **cách nhìn canonical** và **cách nhìn raw**; đó là cơ sở để wrapper `equals` cho mọi `NaN` cùng kiểu có thể bằng nhau.

### Signed zero là gì?

IEEE 754 có **hai biểu diễn zero**:

- `+0.0`: bit dấu là `0`.
- `-0.0`: bit dấu là `1`.

Chúng đều có phần trị và số mũ bằng `0`, chỉ khác bit dấu. Java coi chúng bằng nhau khi so sánh số học `==`. Tuy nhiên, dấu của zero có thể ảnh hưởng phép toán khác:

```java
System.out.println(+0.0 == -0.0); // true
System.out.println(1.0 / +0.0);    // Infinity
System.out.println(1.0 / -0.0);    // -Infinity
```

Như vậy hai mẫu zero có cùng giá trị dưới phép `==` nhưng **không phải mọi phép toán đều xử lý chúng giống hệt nhau**. Wrapper `equals` chủ đích phân biệt hai mẫu bit zero này.

## 3. Bảng đối chiếu: primitive `==` và wrapper `equals`

| Tình huống (cùng kiểu `double`/`Double`) | So sánh primitive `==` | `Double.equals` |
| --- | --- | --- |
| `1.0` với `1.0` | `true` | `true` |
| `1.0` với `2.0` | `false` | `false` |
| `+0.0` với `-0.0` | `true` | `false` |
| `Double.NaN` với `Double.NaN` | `false` | `true` |
| Hai `NaN` có payload khác nhau | `false` | `true` |
| `Double.POSITIVE_INFINITY` với chính nó | `true` | `true` |
| `Double.NEGATIVE_INFINITY` với dương vô cực | `false` | `false` |

Với `Float`, thay `Double` bằng `Float` cho các trường hợp tương ứng. Chú ý `Float.valueOf(1.0f).equals(Double.valueOf(1.0))` vẫn là **`false`**, dù chúng mô tả cùng giá trị toán học, vì `equals` của wrapper yêu cầu **đúng loại lớp bao**.

Các hành vi đặc biệt của `NaN` và signed zero cũng là lý do không nên viết một hàm so sánh số thực chung bằng cách thay `a == b` thành `Double.valueOf(a).equals(Double.valueOf(b))` mà không xem xét hợp đồng nghiệp vụ.

## 4. `Double.equals` và `Float.equals` thực sự so sánh theo gì?

Theo tài liệu JDK, `Double.equals(Object obj)` cho `true` khi đối tượng kia cũng là `Double` và kết quả từ phép **`Double.doubleToLongBits`** trên hai giá trị bằng nhau. Tương tự, `Float.equals` sử dụng **`Float.floatToIntBits`**.

Có thể hiểu khái niệm như sau (mã **minh họa cơ chế**, không sao chép toàn bộ triển khai JDK):

```java
static boolean sameDoubleWrapperValue(double a, double b) {
    return Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
}

static boolean sameFloatWrapperValue(float a, float b) {
    return Float.floatToIntBits(a) == Float.floatToIntBits(b);
}
```

Hai đặc tính của phép đổi bit này giải thích các trường hợp ngoại lệ:

1. **NaN được chuẩn hóa (*canonicalized*):** `Double.doubleToLongBits` trả về cùng **mẫu bit NaN chuẩn** cho mọi giá trị `double` là `NaN`. `Float.floatToIntBits` làm tương tự trên 32 bit. Vì vậy các `NaN` cùng lớp bao có `equals == true`.
2. **Bit dấu zero được giữ:** `+0.0` và `-0.0` chuyển thành hai chuỗi bit khác nhau; vì thế wrapper `equals` trả `false`.

Ví dụ:

```java
System.out.println(Double.doubleToLongBits(+0.0)
        == Double.doubleToLongBits(-0.0)); // false

System.out.println(Float.floatToIntBits(+0.0f)
        == Float.floatToIntBits(-0.0f)); // false

System.out.println(Double.doubleToLongBits(Double.NaN)
        == Double.doubleToLongBits(0.0 / 0.0)); // true
```

**Không nhầm** `doubleToLongBits` với `doubleToRawLongBits` hoặc `floatToIntBits` với `floatToRawIntBits`:

- `doubleToLongBits` / `floatToIntBits` **canonicalize NaN**, phù hợp với `equals`.
- `doubleToRawLongBits` / `floatToRawIntBits` **không cố ý canonicalize NaN** khi trả về bit, nên có thể quan sát các payload NaN khác nhau, tùy giá trị bit còn được giữ sau các phép xử lý trước đó.

Nếu mục tiêu là kiểm tra/ghi lại chính xác payload nhị phân của dữ liệu theo một giao thức, phải chọn công cụ raw-bit có chủ đích. Nếu mục tiêu là khóa `Float`/`Double` trong collection, cần tôn trọng ngữ nghĩa canonical mà wrapper định nghĩa.

## 5. `Double.compare`, `Float.compare` và `compareTo` liên quan gì tới `equals`?

Java cần một **thứ tự tổng** (*total order*) để sắp xếp và lưu số thực trong các cấu trúc có thứ tự. Với kiểu wrapper, `Double.compare(double x, double y)` và `Float.compare(float x, float y)` định nghĩa thêm hai quy tắc đặc biệt:

- `-0.0` được xếp **nhỏ hơn** `+0.0`.
- `NaN` được xem là **lớn hơn mọi số thực**, kể cả `+Infinity`; các giá trị `NaN` được xem là bằng nhau khi so sánh qua `compare`.

`Double.compareTo(Double)` và `Float.compareTo(Float)` theo quy tắc cùng lớp bao. Khi so sánh hai giá trị đã được định kiểu, `compare(x,y) == 0` nhất quán với wrapper `equals` tương ứng.

```java
System.out.println(Double.compare(-0.0, +0.0) < 0); // true
System.out.println(Double.compare(Double.NaN,
        Double.POSITIVE_INFINITY) > 0); // true
System.out.println(Double.compare(Double.NaN, 0.0 / 0.0) == 0); // true
```

Trong khi đó, các phép toán thứ tự nguyên thủy:

```java
System.out.println(-0.0 < +0.0); // false
System.out.println(Double.NaN > Double.POSITIVE_INFINITY); // false
```

Không dùng phép `<`/`>` trên primitive để giả lập một comparator có thứ tự tổng. Với sắp xếp `List<Double>`, dùng `Double::compare` hoặc `Double::compareTo` khi chính sách mong muốn là **NaN cuối, -0.0 đứng trước +0.0**.

Cần phân biệt **thứ tự tổng phục vụ collection** với **thứ tự số học của IEEE 754 khi gặp NaN**. Các quy tắc khác nhau là cố ý và đã được mô tả trong JDK.

## 6. Vì sao các collection cần hợp đồng `equals` như vậy?

`HashSet`, `HashMap` và những collection dựa trên khóa thường sử dụng hợp đồng `equals` và `hashCode` của đối tượng, không dùng toán tử số học `==` để quyết định khóa trùng nhau.

Với `Double`:

- Hai đối tượng chứa `NaN` được xem là **bằng nhau theo `equals`**, nên chúng đại diện cho **cùng khóa**.
- `Double.valueOf(+0.0)` và `Double.valueOf(-0.0)` là **hai khóa khác nhau**.
- Quy tắc `hashCode` được thiết kế nhất quán với `equals`: hai giá trị bằng nhau phải có cùng hash code. Các NaN cùng lớp bao được chuẩn hóa tương ứng; hai zero khác dấu giữ thông tin phân biệt.

Ví dụ:

```java
import java.util.HashSet;
import java.util.Set;

Set<Double> values = new HashSet<>();
values.add(+0.0);
values.add(-0.0);
values.add(Double.NaN);
values.add(0.0 / 0.0);

System.out.println(values.size()); // 3
```

Tập chứa ba giá trị theo `Double.equals`: zero dương, zero âm và một khóa NaN. Không dựa vào thứ tự lặp của `HashSet` để minh họa vì `HashSet` **không đảm bảo thứ tự**.

Còn với `HashMap`:

```java
import java.util.HashMap;
import java.util.Map;

Map<Double, String> descriptions = new HashMap<>();
descriptions.put(+0.0, "positive zero");
descriptions.put(-0.0, "negative zero");
descriptions.put(Double.NaN, "first");
descriptions.put(0.0 / 0.0, "second");

System.out.println(descriptions.size()); // 3
System.out.println(descriptions.get(+0.0)); // positive zero
System.out.println(descriptions.get(-0.0)); // negative zero
System.out.println(descriptions.get(Double.NaN)); // second
```

Lần ghi `NaN` sau **thay thế value** của cùng khóa NaN; hai zero vẫn là hai khóa độc lập.

### Với `TreeSet`, `TreeMap` thì sao?

Cấu trúc có thứ tự như `TreeSet<Double>` thường dựa trên `Double.compareTo`. Bộ so sánh đó nhất quán với `Double.equals`, vì thế vẫn có ba giá trị riêng biệt trong ví dụ zero dương, zero âm và NaN; khi sắp xếp theo thứ tự tự nhiên, `-0.0` đứng trước `+0.0`, còn `NaN` đứng sau vô cực dương.

**Quan trọng:** Nếu bạn tự đưa một comparator khác vào `TreeSet` hoặc `TreeMap`, chính comparator đó quyết định hai khóa có được xem là tương đương về thứ tự hay không. Comparator không nhất quán với `equals` có thể làm hành vi “trùng phần tử” khác với `HashSet`. Cần thiết kế tiêu chí so sánh một cách nhất quán với mục đích nghiệp vụ.

## 7. Không nhầm `equals`, `==` đối tượng và autounboxing

### `equals` so sánh giá trị theo hợp đồng lớp bao

```java
Double a = Double.valueOf(1.0);
Double b = Double.valueOf(1.0);

System.out.println(a.equals(b)); // true
```

### `==` giữa hai tham chiếu wrapper có thể là so sánh danh tính đối tượng

Khi **cả hai toán hạng đều có kiểu tham chiếu**, phép `==` kiểm tra chúng có trỏ đến **cùng đối tượng** hay không, không dùng `Double.equals`.

```java
Double a = Double.valueOf(1.0);
Double b = Double.valueOf(1.0);

boolean sameReference = a == b; // Không dùng để so sánh giá trị.
boolean sameValue = a.equals(b); // true.
```

Không dự đoán `sameReference` luôn `true` hoặc `false`: cách tạo/tái sử dụng đối tượng có thể thay đổi, và không phải hợp đồng so sánh số thực.

### Khi một toán hạng primitive, phép so sánh có thể unbox wrapper

```java
Double boxed = Double.valueOf(Double.NaN);
double primitive = Double.NaN;

System.out.println(boxed == primitive); // false
```

Ở đây `boxed` được unbox thành `double` để so sánh số học, nên quay lại quy tắc `NaN != NaN` của primitive.

Nếu wrapper là `null`, phép unboxing có thể ném `NullPointerException`. Ví dụ `Double d = null; boolean eq = d == 0.0;` **không** cho `false` an toàn mà lỗi khi đánh giá `d`.

Khi so sánh nullable wrapper theo nghĩa đối tượng, `java.util.Objects.equals(a, b)` có thể tránh lỗi `NullPointerException`, nhưng vẫn sử dụng chính `equals` của wrapper cho hai đối tượng không null, nên vẫn giữ ngữ nghĩa NaN/signed zero ở trên.

## 8. Khác lớp bao: `Float` và `Double` không bằng nhau qua `equals`

```java
Float f = Float.valueOf(1.0f);
Double d = Double.valueOf(1.0);

System.out.println(f.equals(d)); // false
System.out.println(d.equals(f)); // false
System.out.println(f.floatValue() == d.doubleValue()); // true
```

Hai wrapper chứa cùng giá trị số học `1`, nhưng `equals` yêu cầu đối tượng cùng loại. Trong biểu thức primitive cuối, binary numeric promotion nâng `float` lên `double` rồi so sánh giá trị, nên trả `true`.

Đừng vì ví dụ `1.0` mà kết luận mọi `float` so với `double` có thể so sánh chính xác: `float` chỉ có 24 bit chính xác và nhiều số thập phân đã được làm tròn khi lưu. Khi nghiệp vụ cần so sánh qua hai kiểu, phải xác định rõ **giá trị mong muốn** và phép chuyển đổi được chấp nhận.

## 9. Khi nào nên dùng cách so sánh nào?

| Mục tiêu | Cách làm phù hợp | Lưu ý |
| --- | --- | --- |
| Xử lý giá trị primitive theo số học IEEE 754 | `a == b`, `a < b` | `NaN` luôn làm so sánh bằng false; zero hai dấu bằng nhau |
| Phát hiện NaN | `Double.isNaN(a)`, `Float.isNaN(a)` | Không dùng `a == NaN` |
| So sánh đối tượng `Double`/`Float` | `a.equals(b)` | NaN bằng NaN; zero hai dấu khác nhau |
| So sánh nullable wrapper | `Objects.equals(a, b)` | Tránh NPE do gọi `equals` trên null; giữ ngữ nghĩa wrapper |
| Sắp xếp số thực với NaN và signed zero | `Double.compare(a, b)` hoặc `Float.compare(a, b)` | NaN cuối, `-0.0 < +0.0` theo comparator |
| Dùng số thực làm khóa `HashMap`/`HashSet` | Định nghĩa dữ liệu theo `equals`/`hashCode` | NaN gộp khóa; signed zero tách khóa |
| So sánh hai kết quả tính toán gần nhau | Một chính sách sai số phù hợp miền nghiệp vụ | Không mặc định dùng `equals` hay epsilon cố định tùy ý |
| So sánh chính xác thập phân tiền tệ | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất | `BigDecimal.equals` xét cả scale; `compareTo` chỉ so giá trị |

Một ví dụ *so sánh gần đúng* với kết quả phép tính:

```java
double computed = 0.1 + 0.2;
double expected = 0.3;
double tolerance = 1e-12;

boolean close = Double.isFinite(computed)
        && Double.isFinite(expected)
        && Math.abs(computed - expected) <= tolerance;
System.out.println(close); // true
```

Đây là quy tắc ứng dụng riêng, **không thay thế** hợp đồng `Double.equals`. Ngưỡng `1e-12` chỉ có ý nghĩa với bài toán và thang đo thích hợp, không phải hằng số chuẩn cho mọi số thực. Nếu xử lý vô cực, NaN, số rất lớn, rất nhỏ hoặc yêu cầu chính xác bit, cần xác định cách xử lý chi tiết riêng.

## 10. Ví dụ Java 17+ chạy độc lập

Tạo tệp **`FloatingEqualityDemo.java`**:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FloatingEqualityDemo {
    public static void main(String[] args) {
        double posZero = +0.0;
        double negZero = -0.0;

        System.out.println("primitive NaN == NaN = "
                + (Double.NaN == Double.NaN));
        System.out.println("primitive +0 == -0 = "
                + (posZero == negZero));

        System.out.println("Double.equals NaN = "
                + Double.valueOf(Double.NaN).equals(Double.valueOf(0.0 / 0.0)));
        System.out.println("Double.equals signed zero = "
                + Double.valueOf(posZero).equals(Double.valueOf(negZero)));

        System.out.println("Float.equals NaN = "
                + Float.valueOf(Float.NaN).equals(Float.valueOf(0.0f / 0.0f)));
        System.out.println("Float.equals signed zero = "
                + Float.valueOf(+0.0f).equals(Float.valueOf(-0.0f)));

        System.out.println("Double.compare -0 < +0 = "
                + (Double.compare(negZero, posZero) < 0));
        System.out.println("Double.compare NaN > infinity = "
                + (Double.compare(Double.NaN,
                        Double.POSITIVE_INFINITY) > 0));

        System.out.println("zero bits equal = "
                + (Double.doubleToLongBits(posZero)
                == Double.doubleToLongBits(negZero)));
        System.out.println("NaN canonical bits equal = "
                + (Double.doubleToLongBits(Double.NaN)
                == Double.doubleToLongBits(0.0 / 0.0)));

        Set<Double> set = new HashSet<>();
        set.add(posZero);
        set.add(negZero);
        set.add(Double.NaN);
        set.add(0.0 / 0.0);
        System.out.println("HashSet size = " + set.size());

        Map<Double, String> names = new HashMap<>();
        names.put(posZero, "positive");
        names.put(negZero, "negative");
        names.put(Double.NaN, "first NaN");
        names.put(0.0 / 0.0, "second NaN");
        System.out.println("HashMap size = " + names.size());
        System.out.println("positive zero key = " + names.get(posZero));
        System.out.println("negative zero key = " + names.get(negZero));
        System.out.println("NaN key = " + names.get(Double.NaN));

        System.out.println("Float.equals Double = "
                + Float.valueOf(1.0f).equals(Double.valueOf(1.0)));

        Double boxedNaN = Double.valueOf(Double.NaN);
        double primitiveNaN = Double.NaN;
        System.out.println("unboxed NaN comparison = "
                + (boxedNaN == primitiveNaN));

        System.out.println("NaN detected = " + Double.isNaN(0.0 / 0.0));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac FloatingEqualityDemo.java
java FloatingEqualityDemo
```

Kết quả:

```text
primitive NaN == NaN = false
primitive +0 == -0 = true
Double.equals NaN = true
Double.equals signed zero = false
Float.equals NaN = true
Float.equals signed zero = false
Double.compare -0 < +0 = true
Double.compare NaN > infinity = true
zero bits equal = false
NaN canonical bits equal = true
HashSet size = 3
HashMap size = 3
positive zero key = positive
negative zero key = negative
NaN key = second NaN
Float.equals Double = false
unboxed NaN comparison = false
NaN detected = true
```

Ví dụ tập trung vào hành vi **được hợp đồng Java đảm bảo**, không dựa vào thứ tự lặp của `HashMap`/`HashSet`, cache của wrapper hay payload NaN raw cụ thể. Có thể kiểm chứng những dòng `HashSet`/`HashMap` để hiểu ngay vì sao cùng là “zero” nhưng có hai khóa, còn nhiều nguồn tạo NaN lại gộp thành một khóa.

## 11. Những lỗi dễ mắc khi xử lý `Float`/`Double`

1. **Cho rằng `Double.equals` tương đương `==` trên `double`.** Sai ở hai trường hợp cốt lõi: `NaN` và signed zero.
2. **Dùng `x == Double.NaN` để kiểm tra lỗi tính toán.** Kết quả luôn `false`; dùng `Double.isNaN(x)`.
3. **Cho rằng `NaN` không bao giờ “bằng chính nó” trong mọi API.** Primitive `==` trả `false`, nhưng wrapper `equals` trả `true` và `Double.compare(NaN,NaN)` trả 0.
4. **Cho rằng `+0.0` và `-0.0` là một khóa duy nhất của `HashMap<Double,...>`.** Wrapper phân biệt chúng, nên thường có hai khóa.
5. **Dùng `Double.compare(a,b)==0` rồi suy ra `a==b` đối với mọi giá trị.** Không đúng với NaN và signed zero. Kết quả `compare == 0` nhất quán với wrapper `equals`, không phải toàn bộ phép so sánh primitive.
6. **Dùng `==` giữa hai wrapper để so sánh nội dung.** Khi cả hai là tham chiếu, phép toán xét danh tính đối tượng; muốn theo ngữ nghĩa wrapper hãy dùng `equals`.
7. **Dùng `equals` trên `Float` và `Double` khác lớp và mong `true` vì cùng là số 1.** Wrapper yêu cầu cùng loại đối tượng.
8. **Giả sử mọi NaN có cùng raw-bit payload.** `equals` dựa trên các phép đổi bit có canonicalization; khi làm việc với giao thức nhị phân cần hiểu sự khác biệt với `doubleToRawLongBits`/`floatToRawIntBits`.
9. **Dùng `equals` để kiểm tra kết quả gần đúng** của những phép tính như `0.1+0.2`. `equals` không phải so sánh theo sai số; chọn một chính sách khác nếu bài toán cho phép sai lệch nhỏ.
10. **Không xét tới `null` khi so sánh wrapper.** `a.equals(b)` có thể ném NPE khi `a` null; phép `==` có unboxing cũng có thể ném NPE. Dùng quy tắc nullable phù hợp.

## 12. Checklist thiết kế so sánh số thực

Trước khi chọn toán tử hay API, xác định rõ:

- **Dữ liệu là primitive hay wrapper?** Cùng ký hiệu `==` có thể biểu thị so sánh số học hoặc so sánh danh tính tham chiếu, tùy kiểu toán hạng.
- **NaN có được phép xuất hiện không?** Nếu là tín hiệu dữ liệu lỗi, kiểm tra sớm bằng `isNaN`; nếu là giá trị hợp lệ trong khóa collection, hiểu quy tắc wrapper canonicalization.
- **Hai zero có phải phân biệt không?** Nếu không, cân nhắc chuẩn hóa zero trước khi lưu khóa; nếu có, `Double.equals`/`Float.equals` giữ được sự khác nhau.
- **Có cần thứ tự sắp xếp toàn phần không?** Chọn `Double.compare`/`Float.compare` cho thứ tự có quy tắc rõ ràng với NaN và signed zero.
- **Độ chính xác thực sự yêu cầu là gì?** Nếu so sánh số liệu đo/ước lượng, xác định ngưỡng từ bài toán; nếu cần chính xác theo thập phân, cân nhắc `BigDecimal` và quy tắc `equals`/`compareTo` riêng của lớp đó.
- **Có dùng làm khóa dữ liệu hay collection không?** Một khi chọn định nghĩa bằng nhau, đảm bảo bộ so sánh, mã băm và chuẩn hóa dữ liệu nhất quán với mục đích đó.

**Kết luận:** `==` trên `float`/`double` tuân thủ so sánh số học IEEE 754 (**NaN không bằng NaN, hai zero bằng nhau**). `Float.equals` và `Double.equals` lại dựa trên các mẫu bit đã canonicalize NaN (**NaN bằng NaN, hai zero khác nhau**), giúp xây dựng ngữ nghĩa khóa và thứ tự nhất quán trong các collection. Đừng thay thế hai quy tắc một cách vô thức; hãy lựa chọn theo yêu cầu thực sự của chương trình.

**Tài liệu tham khảo:** [Java SE 21 — Double.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#equals(java.lang.Object)), [Double.compare](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#compare(double,double)), [Double.doubleToLongBits](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#doubleToLongBits(double)), [Float.equals](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Float.html#equals(java.lang.Object)), [JLS §15.21.1 — Numerical Equality Operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21.1).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-007">So sánh bằng sai số tuyệt đối</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh bằng sai số tuyệt đối trong Java

Khi làm việc với `float` và `double`, không phải lúc nào hai giá trị đại diện cho cùng kết quả toán học cũng có cùng **mẫu bit**. Chẳng hạn phép cộng `0.1 + 0.2` trên `double` thường cho giá trị được in là `0.30000000000000004`, khác literal `0.3`. Nếu bài toán đo lường cho phép chênh lệch nhỏ, ta có thể so sánh bằng **sai số tuyệt đối** (*absolute tolerance*) thay vì bắt buộc dùng `==`.

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double epsilon = 1e-12;

boolean exact = actual == expected; // false
boolean close = Math.abs(actual - expected) <= epsilon; // true
```

Tuy nhiên, biểu thức thứ hai **không phải cách thay thế `==` cho mọi tình huống**. Nó chỉ có ý nghĩa khi `epsilon` được chọn từ yêu cầu thực tế, đơn vị của dữ liệu, và có chính sách xử lý NaN, vô cực, số rất lớn, số gần 0. Bài này trình bày phép so sánh tuyệt đối, ý nghĩa kỹ thuật, cài đặt Java 17+, cách kiểm thử và những ranh giới không nên lạm dụng.

## 1. Tại sao so sánh `double` bằng `==` dễ gây bất ngờ?

`double` lưu số theo định dạng nhị phân IEEE 754 binary64 với 53 bit significand. Phân số như `0.1` và `0.2` không có biểu diễn nhị phân hữu hạn, nên Java lưu các giá trị **gần nhất có thể biểu diễn**. Phép cộng lại áp dụng quy tắc làm tròn chấm động.

```java
double sum = 0.1 + 0.2;
System.out.println(sum);        // 0.30000000000000004
System.out.println(sum == 0.3); // false
```

Ở đây không có lỗi “Java cộng sai”. Vấn đề là phép toán chạy trên các giá trị nhị phân xấp xỉ; kết quả gần 0.3 nhưng không có cùng giá trị binary64 với literal `0.3`.

**Phân biệt hai loại yêu cầu:**

- **Bằng nhau chính xác:** Hai giá trị số cần thật sự bằng nhau theo ngữ nghĩa Java; `==` có thể là lựa chọn đúng, ví dụ kiểm tra bộ đếm nguyên, mã định danh, hoặc một trạng thái số phải chính xác.
- **Gần nhau trong dung sai:** Hai kết quả đo hoặc tính toán có thể khác nhẹ nhưng vẫn được coi như đạt yêu cầu ứng dụng. Khi đó, một ngưỡng sai số cụ thể có thể phù hợp hơn.

Đừng chuyển toàn bộ phép so sánh số thực sang gần đúng theo thói quen. Sự gần đúng là **chính sách nghiệp vụ**, không phải thuộc tính mặc định của mọi số `double`.

## 2. Định nghĩa sai số tuyệt đối

Với hai giá trị hữu hạn `a` và `b`, **sai số tuyệt đối** là:

\[
E_{\mathrm{abs}} = |a-b|
\]

Ta nói hai giá trị **gần nhau theo ngưỡng tuyệt đối** `ε` khi:

\[
|a-b| \leq \varepsilon
\qquad (\varepsilon \geq 0)
\]

Trong Java, cách viết tương ứng cho phép kiểm tra đơn giản:

```java
boolean close = Math.abs(a - b) <= epsilon;
```

Ở đây `Math.abs(double)` trả độ lớn không âm của hiệu (trừ các trường hợp đặc biệt như NaN), còn dấu `<=` chọn quy ước **cho phép bằng ngưỡng**. Nếu bài toán yêu cầu nhỏ hơn **nghiêm ngặt**, dùng `<`, nhưng phải viết rõ hợp đồng kiểm thử.

### Ví dụ bằng số

Cho `a = 12.34`, `b = 12.35` và sai số cho phép `epsilon = 0.02`:

```text
absolute error = |12.34 - 12.35|
               ≈ 0.01
tolerance      = 0.02
0.01 <= 0.02   => true
```

Các số thập phân nhập dưới dạng `double` có thể bị làm tròn; dấu `≈` nhắc rằng phép trừ thực tế cũng dùng số nhị phân. Ví dụ chỉ minh họa **đại lượng chênh lệch**; kết luận nên được kiểm tra bằng chính dữ liệu và kiểu số thực tế.

Với giá trị **biểu diễn chính xác trong nhị phân**, ranh giới rất rõ:

```java
System.out.println(Math.abs(1.0 - 1.125) <= 0.125); // true
System.out.println(Math.abs(1.0 - 1.25) <= 0.125);  // false
```

`1.125`, `1.25` và `0.125` là các phân số có mẫu là lũy thừa 2, nên ở đây không bị nhập nhằng do việc lưu literal.

## 3. `epsilon` là ngưỡng có **đơn vị**, không phải hằng số thần kỳ

`epsilon` phải diễn tả **mức chênh lệch lớn nhất mà bài toán chấp nhận**.

| Dữ liệu | Ví dụ mức dung sai tuyệt đối | Đơn vị và ý nghĩa |
| --- | --- | --- |
| Nhiệt độ cảm biến | `0.2` | 0,2 °C nếu dữ liệu biểu diễn °C |
| Tọa độ theo mét | `0.005` | 5 mm |
| Thời gian theo giây | `0.001` | 1 ms |
| Hiệu điện thế | `0.01` | 0,01 V |
| Thuật toán chỉ kiểm tra sai số làm tròn quanh 0.3 | `1e-12`, chỉ khi có căn cứ | Là lựa chọn của bài toán thử nghiệm, **không phải ngưỡng chuẩn** cho mọi `double` |

Các con số minh họa không phải khuyến cáo đo lường cho một thiết bị cụ thể; tiêu chuẩn chất lượng thiết bị và hợp đồng ứng dụng quyết định ngưỡng thật.

Nếu `a` và `b` có đơn vị **mét**, `epsilon` cũng phải tính bằng **mét**. Nếu một bên dùng mét còn bên kia dùng millimét, phép so sánh có thể trả lời rất “hợp cú pháp” nhưng **sai ngữ nghĩa**.

**Không dùng một giá trị `1e-6` ở mọi dự án** chỉ vì nó nhỏ. Trong một hệ thống đo kích thước nano, `1e-6` có thể quá lớn; khi đo quãng đường tính bằng triệu km, nó có thể quá nhỏ.

### Tại sao `Double.MIN_VALUE` không phải epsilon mặc định?

`Double.MIN_VALUE` là số dương **khác 0 nhỏ nhất** mà binary64 có thể biểu diễn, xấp xỉ `4.9e-324`. Nó không biểu thị sai số làm tròn điển hình. `Math.ulp(1.0)` khoảng `2.220446049250313e-16` gần 1.0; khoảng cách giữa các giá trị biểu diễn thay đổi theo độ lớn. Dùng `Double.MIN_VALUE` để kiểm tra “gần nhau” thường tương đương yêu cầu gần như bằng nhau chính xác.

Ngay cả một bội số cố định của `Math.ulp(1.0)` cũng không có nghĩa phù hợp với mọi giá trị: bước lượng tử chấm động phụ thuộc độ lớn của số, trong khi ngưỡng tuyệt đối là một độ chênh **theo đơn vị nghiệp vụ**.

## 4. Hàm so sánh Java 17+ có kiểm tra điều kiện đầu vào

Biểu thức `Math.abs(a-b) <= epsilon` ngắn gọn nhưng **không nêu rõ chính sách NaN, vô cực hoặc ngưỡng sai**. Với dữ liệu đo lường yêu cầu các số phải **hữu hạn**, có thể viết:

```java
static boolean closeAbs(double a, double b, double epsilon) {
    if (!Double.isFinite(epsilon) || epsilon < 0.0) {
        throw new IllegalArgumentException(
                "epsilon must be finite and >= 0");
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }
    if (a == b) {
        return true;
    }
    return Math.abs(a - b) <= epsilon;
}
```

Đây là **phương thức minh họa**, cần đặt trong một lớp. Phiên bản đầy đủ chạy được có ở phần 10.

**Hợp đồng của hàm:**

1. `epsilon` phải **hữu hạn** và **không âm**. NaN, ±Infinity và epsilon âm bị từ chối bằng `IllegalArgumentException`.
2. Nếu `a` hoặc `b` là NaN/vô cực, trả `false`. Điều này phù hợp bài toán chỉ chấp nhận **giá trị đo hữu hạn**.
3. Hai số hữu hạn đã bằng nhau theo `==` được coi là gần nhau, kể cả epsilon bằng 0.
4. Với các số hữu hạn còn lại, trả giá trị của `Math.abs(a-b) <= epsilon`.

Không nên gọi chính sách trên là **quy tắc duy nhất**. Một ứng dụng có thể coi `+Infinity` với `+Infinity` là cùng trạng thái đặc biệt, hoặc yêu cầu zero phải phân biệt dấu. Nếu vậy cần **định nghĩa chính sách khác**, thay vì đổi mã mà không giải thích.

### Vì sao phải kiểm tra `epsilon` trước?

Nếu `epsilon = Double.NaN`, phép so sánh `<=` với NaN luôn sai; nếu `epsilon = Double.POSITIVE_INFINITY`, nhiều cặp số cực kỳ khác nhau sẽ được coi là gần, làm mất ý nghĩa kiểm tra. Nếu `epsilon < 0`, ngay cả hai số hữu hạn giống nhau cũng không thỏa công thức ngưỡng thông thường.

Việc ném `IllegalArgumentException` làm lộ lỗi cấu hình hoặc lỗi chọn ngưỡng ngay tại ranh giới gọi hàm.

### Có cần nhánh `a == b`?

Trong hàm này, với các số hữu hạn, nhánh đó giúp thể hiện rõ rằng **bằng nhau chính xác phải được chấp nhận ngay cả khi epsilon bằng 0**. Công thức trừ và lấy trị tuyệt đối cũng cho kết quả đúng trong trường hợp ấy; nhánh chỉ làm hợp đồng dễ đọc và tránh tính hiệu không cần thiết.

**Không** đặt `a == b` trước kiểm tra hữu hạn nếu chính sách yêu cầu từ chối Infinity: `Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY` là `true`, nhưng ứng dụng không muốn coi hai giá trị đo vô hạn là hợp lệ.

## 5. Những trường hợp ranh giới phải xử lý rõ

### 5.1. NaN

`Double.NaN` thường đại diện cho kết quả không xác định trong phép toán chấm động. Các toán tử so sánh với NaN có hành vi đặc biệt:

```java
double x = Double.NaN;
System.out.println(x == x); // false
System.out.println(Math.abs(x - 1.0) <= 1.0); // false
```

Hàm `closeAbs` phía trên trả `false` cho NaN trước khi trừ. Nếu muốn coi hai giá trị NaN là cùng **trạng thái dữ liệu thiếu**, cần logic riêng; đừng lẫn việc “cùng trạng thái” với **sai số số học**.

### 5.2. Vô cực

`Math.abs(Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY)` là NaN, do đó phép kiểm tra ngưỡng thuần túy trả `false`. Nhưng `Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY` là `true`. Hai cách so sánh đơn giản có chính sách khác nhau.

Vì vậy, cách làm rõ ràng là **quyết định trước** có cho phép giá trị không hữu hạn hay không. Với dữ liệu đo cần hữu hạn, `Double.isFinite` giúp loại vô cực và NaN một cách tường minh.

### 5.3. Zero có dấu

`+0.0` và `-0.0` được coi là bằng nhau theo `==`:

```java
System.out.println(+0.0 == -0.0); // true
```

Hàm so sánh sai số tuyệt đối trên trả `true` cho cặp này, kể cả `epsilon = 0`. Nếu cần so sánh **mẫu bit IEEE 754** (chẳng hạn serialization hoặc giao thức số học đặc biệt), `+0.0` và `-0.0` khác nhau; khi ấy so sánh độ gần số học **không phải bài toán đúng**. Cân nhắc `Double.doubleToRawLongBits` với chính sách NaN rõ ràng.

### 5.4. Hiệu tràn khi trừ hai số hữu hạn rất lớn

Hai `double` đều hữu hạn nhưng hiệu của chúng có thể vượt miền `double`:

```java
double a = Double.MAX_VALUE;
double b = -Double.MAX_VALUE;
double difference = a - b; // +Infinity
```

Nếu `epsilon` cũng là một số hữu hạn, hiệu toán học của cặp trên lớn hơn mọi epsilon hữu hạn có thể biểu diễn. Vì vậy trả `false` là hợp lý. Trường hợp này cho thấy **phép trừ cũng có miền biểu diễn**, và không nên mù quáng đưa các phép biến đổi đại số tương đương vào mã số học chấm động.

### 5.5. Giá trị rất gần 0

Giả sử `a = 1e-12`, `b = 0` và `epsilon = 1e-9`. Hai giá trị được coi là gần vì `1e-12 <= 1e-9`, nhưng `a` vẫn là **một giá trị khác 0**.

Một ngưỡng tuyệt đối giúp xác định “đủ gần zero” theo đơn vị, nhưng **không biến giá trị đó thành zero toán học**. Nếu `a` là dòng rò điện cần phát hiện, hoặc tín hiệu vật lý cực nhỏ quan trọng, việc coi gần zero thành zero có thể che giấu sự kiện thật.

## 6. Sai số tuyệt đối tốt khi nào, yếu khi nào?

### Tốt: đại lượng có miền và ngưỡng chênh cố định

Ví dụ kiểm tra nhiệt độ đo so với nhiệt độ mục tiêu, sai số cho phép ±0,2 °C, một ngưỡng tuyệt đối có ý nghĩa rõ ràng. Với tọa độ đo bằng mét, kiểm tra chênh không quá 1 mm cũng là một quyết định theo **đơn vị**.

Sai số tuyệt đối đặc biệt dễ hiểu gần zero: “giá trị này cách zero bao nhiêu đơn vị?”.

### Yếu: dữ liệu trải qua nhiều bậc độ lớn

Xét hai trường hợp dùng cùng `epsilon = 1e-6`:

- So sánh các giá trị quanh `1e-12`: ngưỡng lớn gấp nhiều lần giá trị, dễ coi những giá trị khác nhau về tỷ lệ là tương đương.
- So sánh các giá trị quanh `1e12`: ngưỡng nhỏ hơn khoảng cách giữa một số cặp `double` liền kề ở vùng này, có thể quá khắt khe.

Do đó, nếu lỗi chấp nhận được **phụ thuộc tỷ lệ hoặc độ lớn**, một ngưỡng **tương đối** hoặc kết hợp tương đối/tuyệt đối có thể phù hợp hơn. Nhưng đó là một chính sách khác; chỉ nên chọn sau khi xác định đầu ra và mức lỗi nghiệp vụ.

Một công thức kết hợp thường gặp là:

\[
|a-b| \leq \max(\varepsilon_{\mathrm{abs}},\ \varepsilon_{\mathrm{rel}}\max(|a|,|b|)).
\]

Không dùng công thức đó mà quên kiểm tra NaN, Infinity, ngưỡng âm hoặc overflow khi tính các đại lượng trung gian. Ở đây trọng tâm là **absolute tolerance**; công thức kết hợp chỉ để định vị giới hạn của phương pháp.

### Bảng lựa chọn

| Trường hợp | Sai số tuyệt đối có phù hợp? | Lưu ý |
| --- | --- | --- |
| Giá trị gần 0, đơn vị và ngưỡng rõ | Thường phù hợp | Ví dụ khoảng cách tuyệt đối tới 0 |
| Độ lệch cảm biến cho phép tối đa 0,2 đơn vị | Phù hợp nếu hợp đồng đã quy định | Ngưỡng phải cùng đơn vị |
| So sánh giá trị từ `1e-12` đến `1e12` | Một ngưỡng duy nhất thường không đủ | Cân nhắc tương đối/kết hợp |
| Kiểm tra số nguyên, ID hoặc hash | Không nên dùng | Dùng bằng nhau chính xác và kiểu nguyên |
| Tiền tệ cần tính đúng theo cent | Không phù hợp như cách chữa sai số | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất |
| So sánh mẫu bit và signed zero | Không phù hợp | Dùng API kiểm tra bit |
| NaN, Infinity trong mô hình trạng thái | Chỉ khi có chính sách riêng | Không gọi chúng là số đo “gần nhau” |
| Dùng ngưỡng để che sai thuật toán | Không phù hợp | Điều tra nguyên nhân sai số |

## 7. `float`, `double` và tác động của kiểu dữ liệu

`float` có khoảng 24 bit significand, còn `double` có 53 bit significand. Vì vậy, cùng một giá trị toán học có thể chịu sai số lưu trữ khác nhau theo kiểu.

```java
float x = 0.1f;
double y = 0.1d;

System.out.println((double) x); // 0.10000000149011612
System.out.println(y);          // 0.1
```

Khi so sánh một `float` với một `double`, giá trị `float` được **mở rộng sang `double` nhưng không khôi phục thông tin đã mất**. Nếu dùng hàm nhận `double`, phép chuyển mở rộng là hợp lệ; tuy nhiên ngưỡng phải được chọn theo độ phân giải thực tế của đầu vào binary32, không giả định hai toán hạng có cùng độ chính xác.

### Vì sao không dùng một epsilon bất biến cho mọi `float`?

Giống `double`, khoảng cách giữa hai số `float` biểu diễn được **tăng theo độ lớn**. Ở gần 1.0, `Math.ulp(1.0f)` khoảng `1.1920929e-7`; ở gần 1.0 với `double`, `Math.ulp(1.0d)` khoảng `2.220446049250313e-16`.

`Math.ulp` đo bước biểu diễn ở một vùng giá trị, **không phải ngưỡng sai số nghiệp vụ tự sinh**. Nó hữu ích khi đánh giá việc một ngưỡng tuyệt đối có nhỏ hơn độ phân giải của dữ liệu hay không.

Nếu dữ liệu đến từ cảm biến chính xác tới 0,01 đơn vị, ngưỡng `1e-12` có thể hoàn toàn không thực tế, dù kiểu lưu trữ là `double`.

## 8. So sánh “gần bằng” không phải quan hệ bằng nhau toán học

Với `epsilon > 0`, một phép so sánh gần đúng có thể:

- **Đối xứng:** `closeAbs(a,b,epsilon)` và `closeAbs(b,a,epsilon)` cho cùng kết quả trên số hữu hạn theo tiêu chí sai số tuyệt đối.
- **Tự gần chính mình:** mọi giá trị **hữu hạn** đều gần chính nó nếu epsilon hợp lệ và không âm.
- **Không có tính bắc cầu:** `a` gần `b` và `b` gần `c` **không bảo đảm** `a` gần `c`.

Ví dụ chọn các số biểu diễn được chính xác:

```java
double epsilon = 0.25;
double a = 0.0;
double b = 0.25;
double c = 0.5;

// closeAbs(a,b,epsilon) == true.
// closeAbs(b,c,epsilon) == true.
// closeAbs(a,c,epsilon) == false.
```

Vì thế **không** nên dùng so sánh epsilon thay cho `equals`/`hashCode` của khóa trong `HashMap`/`HashSet` hoặc kỳ vọng nó là quan hệ tương đương của nhóm đối tượng. Các thuật toán sắp xếp và cấu trúc dữ liệu đòi hỏi hợp đồng nhất quán; comparator dùng epsilon thiếu bắc cầu có thể vi phạm hợp đồng so sánh.

### Một hậu quả khác: “gần bằng” có thể làm mờ ngưỡng

Nếu dùng `closeAbs(x, limit, epsilon)` để quyết định “`x` không được vượt `limit`”, đó không phải kiểm tra bất đẳng thức phù hợp: số nằm **dưới** giới hạn rất xa là hợp lệ nhưng không “gần” giới hạn. Bài toán **kiểm tra giới hạn** nên dùng bất đẳng thức có chính sách biên rõ:

```java
boolean withinUpperBound = x <= upperBound;
```

Nếu cho phép vượt nhẹ ngưỡng, phải quy định cụ thể “`x <= upperBound + tolerance`” cùng chính sách chống overflow và xử lý không hữu hạn, thay vì gọi “gần bằng” một cách mơ hồ. So sánh gần đúng chỉ trả lời **độ chênh giữa hai số**, không thay thế logic thứ tự.

## 9. Lưu ý khi viết test tự động

Trong unit test, thường có ba đầu vào quan trọng: **actual**, **expected**, và **tolerance**.

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double tolerance = 1e-12;

if (Math.abs(actual - expected) > tolerance) {
    throw new AssertionError("Actual is too far from expected");
}
```

Đoạn này minh họa ý tưởng nhưng chưa đầy đủ chính sách NaN/Infinity: với NaN, phép `>` cũng trả `false`, khiến nhánh throw **không chạy**. Vì vậy, khi viết helper kiểm thử tin cậy, nên dùng kết quả của một hàm đã kiểm tra finite và ngưỡng:

```java
if (!closeAbs(actual, expected, tolerance)) {
    throw new AssertionError("Values differ beyond absolute tolerance");
}
```

Với thư viện kiểm thử như JUnit, dùng overload assertion cho số chấm động có `delta` nếu phiên bản/framework cung cấp, và đọc hợp đồng của API đó (nhất là NaN và Infinity). Không tự giả định mọi thư viện xử lý giá trị đặc biệt giống hệt helper tự viết.

### Các bài test nên có

| Trường hợp | Ví dụ | Kỳ vọng theo helper `closeAbs` |
| --- | --- | --- |
| Chênh nhỏ trong ngưỡng | `0.1+0.2`, `0.3`, `1e-12` | `true` |
| Chênh vượt ngưỡng | Cặp trên với `1e-17` | `false` |
| Chính xác tại biên | `1.0`, `1.125`, `0.125` | `true` |
| Vượt biên | `1.0`, `1.25`, `0.125` | `false` |
| Giá trị hữu hạn bằng nhau | `2.0`, `2.0`, `0` | `true` |
| Zero khác dấu | `+0.0`, `-0.0`, `0` | `true` |
| Có NaN | `NaN`, `NaN`, `1` | `false` |
| Vô cực | `+Infinity`, `+Infinity`, `1` | `false` |
| Epsilon âm | `1`, `1`, `-1` | `IllegalArgumentException` |
| Epsilon NaN/vô cực | `1`, `1`, `NaN` | `IllegalArgumentException` |
| Hai số hữu hạn có hiệu tràn | `MAX_VALUE`, `-MAX_VALUE`, `MAX_VALUE` | `false` |

Cần test cả **thay đổi đơn vị** và **cận trên/cận dưới của phép đo**, vì một ngưỡng hợp lý ở dữ liệu nhỏ có thể quá lỏng hoặc quá chặt ở miền lớn.

## 10. Chương trình Java 17+ chạy độc lập

Tạo tệp `AbsoluteToleranceDemo.java`:

```java
import java.util.Locale;

public class AbsoluteToleranceDemo {
    static boolean closeAbs(double a, double b, double epsilon) {
        if (!Double.isFinite(epsilon) || epsilon < 0.0) {
            throw new IllegalArgumentException(
                    "epsilon must be finite and >= 0");
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        if (a == b) {
            return true;
        }
        return Math.abs(a - b) <= epsilon;
    }

    public static void main(String[] args) {
        double sum = 0.1 + 0.2;
        System.out.printf(Locale.ROOT, "0.1 + 0.2 = %.17f%n", sum);

        System.out.println("Exact equality with 0.3: " + (sum == 0.3));
        System.out.println("epsilon 1e-12: " + closeAbs(sum, 0.3, 1e-12));
        System.out.println("epsilon 1e-17: " + closeAbs(sum, 0.3, 1e-17));

        System.out.println("At boundary: "
                + closeAbs(1.0, 1.125, 0.125));
        System.out.println("Outside boundary: "
                + closeAbs(1.0, 1.25, 0.125));

        System.out.println("Signed zero: "
                + closeAbs(+0.0, -0.0, 0.0));
        System.out.println("NaN accepted: "
                + closeAbs(Double.NaN, Double.NaN, 1.0));
        System.out.println("Infinity accepted: "
                + closeAbs(Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1.0));

        System.out.println("Large values: "
                + closeAbs(1e12, 1e12 + 0.01, 0.001));
        System.out.println("Tiny signal considered close: "
                + closeAbs(1e-12, 0.0, 1e-9));
        System.out.println("Gap overflow accepted: "
                + closeAbs(Double.MAX_VALUE,
                        -Double.MAX_VALUE, Double.MAX_VALUE));

        try {
            closeAbs(1.0, 1.0, Double.NaN);
        } catch (IllegalArgumentException ex) {
            System.out.println("Invalid tolerance: rejected");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 AbsoluteToleranceDemo.java
java AbsoluteToleranceDemo
```

Stdout (đã kiểm chứng):

```text
0.1 + 0.2 = 0.30000000000000004
Exact equality with 0.3: false
epsilon 1e-12: true
epsilon 1e-17: false
At boundary: true
Outside boundary: false
Signed zero: true
NaN accepted: false
Infinity accepted: false
Large values: false
Tiny signal considered close: true
Gap overflow accepted: false
Invalid tolerance: rejected
```

Điều quan trọng nhất ở ví dụ là **cùng một cặp giá trị** có thể gần nhau với epsilon này nhưng không gần với epsilon nhỏ hơn. Đó không phải sự mâu thuẫn: epsilon là **đầu vào quyết định hợp đồng**, không phải một hằng nội tại của `double`.

Dòng `Tiny signal considered close: true` cũng là lời cảnh báo: một ngưỡng tuyệt đối quá rộng có thể che khuất tín hiệu nhỏ. Dòng `Infinity accepted: false` phản ánh **chính sách chỉ nhận số hữu hạn** của helper, chứ không phải toán tử `==` của Java.

## 11. Những lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `a == b` cho mọi kết quả tính toán chấm động | Các bước làm tròn có thể khiến khác bit | Chỉ dùng absolute tolerance khi nghiệp vụ cho phép |
| Dùng epsilon cố định `1e-6` cho mọi đơn vị và miền | Ngưỡng quá lớn hoặc quá nhỏ tùy ngữ cảnh | Chọn epsilon theo đơn vị, miền, độ phân giải, yêu cầu |
| Dùng `Double.MIN_VALUE` như “sai số mặc định” | Đây là số dương subnormal nhỏ nhất | Chọn ngưỡng theo bài toán, hiểu `Math.ulp` |
| Dùng epsilon âm hoặc NaN | Công thức gần bằng mất ý nghĩa hoặc cho kết quả bất ngờ | Validate epsilon hữu hạn và không âm |
| Chỉ kiểm tra `if (abs(actual-expected) > eps) fail` | Với NaN, phép `>` trả false, có thể bỏ lọt lỗi | Dùng `if (!closeAbs(...)) fail` và chính sách NaN |
| Coi Infinity bằng Infinity là “gần nhau” dù dữ liệu đo phải hữu hạn | `==` và so sánh sai số có hợp đồng khác | Kiểm tra `Double.isFinite` |
| Coi `+0.0` và `-0.0` phải khác nhau trong đo lường | Dấu 0 thường không có ý nghĩa khoảng cách | So sánh theo độ gần; dùng raw bits khi cần phân biệt dấu |
| Dùng tolerance để so sánh index/ID | Chấp nhận nhầm hai giá trị khác nhau | Dùng kiểu nguyên và `==`/`equals` thích hợp |
| Dùng `closeAbs` làm `equals`/Comparator | Gần bằng không bắc cầu | Tách kiểm tra gần bằng khỏi hợp đồng collection |
| Quên thay đổi đơn vị | Ngưỡng 0,01 m không bằng 0,01 mm | Chuẩn hóa đơn vị trước khi so sánh |
| Chỉ nghĩ đến sai số lúc lưu literal | Mỗi phép toán trung gian có thể làm tròn | Xem toàn bộ pipeline tính toán |
| Dùng `float` rồi lấy epsilon của `double` mà không đánh giá | `float` có độ phân giải thấp hơn | Xem `Math.ulp` và mức sai số của dữ liệu nguồn |
| Dùng “gần bằng giới hạn” để thay kiểm tra `<=` | Gần bằng không diễn đạt miền cho phép | Viết điều kiện giới hạn và chính sách biên riêng |

## 12. Câu hỏi tự kiểm tra

1. Vì sao `0.1 + 0.2 == 0.3` là `false` trong Java? Đổi sang so sánh tuyệt đối có ý nghĩa khi nào?
2. Với `a = 1.0`, `b = 1.125`, `epsilon = 0.125`, điều kiện `Math.abs(a-b) <= epsilon` cho kết quả gì? Nếu dùng `<` thay vì `<=` thì sao?
3. Vì sao epsilon cần có **cùng đơn vị** với dữ liệu? Chuyện gì xảy ra nếu một giá trị tính bằng mét và một giá trị tính bằng mm?
4. Nếu một cảm biến đo gần zero nhưng tín hiệu `1e-12` là quan trọng, vì sao epsilon `1e-9` có thể nguy hiểm?
5. Vì sao `Double.MIN_VALUE` không phải ngưỡng thích hợp để kiểm tra các phép toán gần 1.0?
6. `Math.abs(Double.NaN - expected) <= epsilon` cho kết quả gì? Dùng biểu thức `Math.abs(...) > epsilon` để fail unit test có thể gây vấn đề nào?
7. Hàm `closeAbs` trong bài xử lý Infinity như thế nào? Nếu ứng dụng muốn coi Infinity cùng dấu là cùng trạng thái, bạn sẽ thay đổi hợp đồng ra sao?
8. `closeAbs(+0.0,-0.0,0.0)` trả gì? Khi nào cần kiểm tra bit thay vì độ gần số học?
9. Vì sao quan hệ “gần bằng” không có tính bắc cầu? Đưa ví dụ ba số gần nhau từng cặp kế tiếp nhưng hai đầu không gần nhau.
10. Khi dữ liệu trải trên miền từ `1e-12` tới `1e12`, tại sao một epsilon tuyệt đối cố định có thể không phù hợp?
11. Nếu lưu cảm biến dưới dạng `float` rồi so sánh với dữ liệu `double`, bước widening có phục hồi các bit đã mất không?
12. Vì sao khi cần kiểm tra giá trị không vượt ngưỡng, `closeAbs(x,upperBound,eps)` không thay thế trực tiếp cho `x <= upperBound`?

## 13. Tham khảo

- [Java SE 17 — `Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): các giá trị NaN/Infinity, `isFinite`, `isNaN` và các quy tắc liên quan tới binary64.
- [Java SE 17 — `Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `abs(double)`, `ulp(double)` và các phép toán số học.
- [Java Language Specification, `4.2.3 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): định dạng số dấu phẩy động và các giá trị đặc biệt.
- [Java Language Specification, `15.21.1 — Numerical Equality Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21.1): `==`/`!=` và hành vi với NaN/signed zero.

**Ghi nhớ:** So sánh bằng sai số tuyệt đối là quy tắc **`|a-b| <= epsilon`**, trong đó `epsilon` phải được chọn theo **đơn vị, miền và yêu cầu chính xác của bài toán**. Đây là cách đánh giá chênh lệch có chủ đích, **không phải phép thay thế `==` phổ quát**. Để triển khai đáng tin cậy trên Java, cần xác định chính sách NaN/Infinity/±0, validate epsilon, kiểm thử biên, và biết khi nào một ngưỡng tương đối hoặc mô hình số khác phù hợp hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-008">So sánh bằng sai số tương đối</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh bằng sai số tương đối trong Java

## 1. Vì sao so sánh `double` bằng `==` thường chưa đủ?

Với số nguyên, phép so sánh `==` thường là lựa chọn thích hợp để kiểm tra hai giá trị có bằng nhau không. Với `float` và `double`, kết quả của phép toán dấu phẩy động có thể **khác đôi chút so với giá trị lý tưởng** vì chỉ một số hữu hạn các giá trị nhị phân có thể biểu diễn được. Ví dụ:

~~~java
double computed = 0.1 + 0.2;
double expected = 0.3;

System.out.println(computed);            // 0.30000000000000004
System.out.println(computed == expected); // false
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. Phép `==` **không sai**: nó trả lời câu hỏi hai giá trị `double` hiện có bằng nhau theo quy tắc so sánh dấu phẩy động hay không. Vấn đề là nhiều bài toán thực tế muốn trả lời **“hai kết quả có đủ gần nhau trong mức sai số chấp nhận được không?”**.

Một cách định lượng mức gần nhau là **sai số tương đối** (*relative error/tolerance*), dùng tỷ lệ chênh lệch so với độ lớn của giá trị được so sánh. Nó đặc biệt hữu ích khi các đại lượng thay đổi theo nhiều bậc độ lớn.

Ví dụ:

- Độ chênh lệch **1** giữa 1.000.000 và 1.000.001 chỉ khoảng **0,0001%**.
- Độ chênh lệch **1** giữa 1 và 2 lại lên tới **50% hoặc 100%**, tùy chọn mốc tham chiếu.

Cùng một sai số tuyệt đối `1` không mang cùng ý nghĩa khi thang giá trị thay đổi lớn. Đó là lý do dùng sai số tương đối.

**Lưu ý phạm vi:** bài này giải thích cách so sánh **gần bằng** về mặt số học. Nó không thay đổi quy tắc của `==`, không làm cho phép toán `double` trở thành chính xác tuyệt đối và không thay thế tiêu chuẩn sai số cụ thể của từng lĩnh vực.

## 2. Sai số tuyệt đối và sai số tương đối

### Sai số tuyệt đối

Với hai giá trị hữu hạn `a` và `b`, sai số tuyệt đối thường được định nghĩa:

\[
E_{\mathrm{abs}} = |a-b|
\]

Nếu `a=1000000.0` và `b=1000000.5`, sai số tuyệt đối là `0.5`. Một phép kiểm tra gần bằng theo ngưỡng tuyệt đối là:

~~~java
Math.abs(a - b) <= absoluteTolerance
~~~

Đoạn trên chỉ là biểu thức minh họa. Nó phù hợp khi bài toán đặt sai số theo **đơn vị cụ thể**: chẳng hạn sai số đo không quá 0,001 mét.

Nhưng nếu `a` và `b` đôi lúc ở cỡ `1e-12`, đôi lúc ở cỡ `1e12`, một ngưỡng tuyệt đối chung có thể quá rộng ở thang nhỏ và quá khắt khe ở thang lớn.

### Sai số tương đối theo mốc tham chiếu

Nếu `reference` là một giá trị chuẩn **khác 0**, sai số tương đối có thể định nghĩa:

\[
E_{\mathrm{rel,ref}} =
\frac{|observed-reference|}{|reference|}
\]

Đây là định nghĩa hợp lý trong phép đo: mẫu số là độ lớn của **giá trị chuẩn**. Ví dụ, sai số `0.5` trên mốc `1_000_000` bằng `0.0000005`, tức `0.00005%`. Với ngưỡng `relativeTolerance = 1e-6` (một phần triệu), hai số được coi là đủ gần nhau.

**Mẫu số `reference == 0` khiến công thức không xác định.** Không được lặng lẽ thay mẫu số 0 bằng một số tùy tiện mà không mô tả chính sách của bài toán.

### Sai số tương đối đối xứng

Nếu không có giá trị nào được ưu tiên là “chuẩn”, có thể chọn thang đo đối xứng:

\[
scale = \max(|a|,|b|)
\]

\[
E_{\mathrm{rel,sym}} =
\frac{|a-b|}{\max(|a|,|b|)}
\quad \text{(khi }scale>0\text{)}
\]

Bài này sẽ dùng biến thể **đối xứng theo `max(|a|, |b|)`** trong ví dụ chính. Đổi chỗ `a` và `b` không thay đổi kết quả. Công thức không phải định nghĩa duy nhất của “relative error”: một số tài liệu dùng `|reference|`, trung bình độ lớn hoặc một thang vật lý xác định trước. Cần thống nhất **mẫu số** khi thiết kế API và viết test.

### Phần trăm và ngưỡng

| Giá trị ngưỡng `relTol` | Tương đương tỷ lệ | Ý nghĩa |
|---:|---:|---|
| `1e-2` | `1%` | Cho phép chênh lệch khoảng một phần trăm của thang đo |
| `1e-3` | `0.1%` | Khoảng một phần nghìn |
| `1e-6` | `0.0001%` | Khoảng một phần triệu |
| `1e-9` | `0.0000001%` | Khoảng một phần tỷ |

Ngưỡng là **chính sách của ứng dụng**, không phải một con số Java “đúng cho mọi `double`”. Độ chính xác của dữ liệu đầu vào, thuật toán, số phép tính, đơn vị đo và hậu quả của kết luận sai đều ảnh hưởng đến ngưỡng thích hợp.

## 3. Công thức so sánh tương đối và hai điểm phải xử lý

Một phép kiểm tra lý tưởng thường được viết:

\[
|a-b| \le relTol\cdot\max(|a|,|b|)
\]

hoặc chia hai vế cho `scale` dương:

\[
\frac{|a-b|}{scale} \le relTol
\]

Giả sử `relTol` nằm trong `[0, 1)` để có ý nghĩa “hai số phải thật sự gần nhau”, khi cả hai số hữu hạn.

**Nhưng đừng sao chép công thức trực tiếp mà bỏ qua các trường hợp biên**:

1. **Cả hai bằng 0:** `scale == 0`, phép chia tạo biểu thức `0.0 / 0.0` là `NaN`; nếu không xử lý trước, phép so sánh sẽ trả `false` dù hai số bằng nhau.
2. **Hai số hữu hạn rất lớn và trái dấu:** `a-b` có thể tràn thành `Infinity`, dù `a` và `b` riêng lẻ đều hữu hạn. Khi `relTol < 1`, hai số trái dấu không thể đủ gần theo quy tắc tương đối thuần túy (ngoại trừ cùng bằng 0 đã được xử lý).
3. **`NaN` hoặc vô cực:** phải quyết định rõ cách diễn giải gần bằng; áp dụng phép chia công thức một cách máy móc có thể trả `NaN` hoặc giá trị không hữu ích.
4. **Số gần 0:** một giá trị 0 và một giá trị dương rất nhỏ có sai số tương đối đối xứng bằng `1`, **không** được coi là gần nhau nếu `relTol < 1`. Bài toán có thể cần **ngưỡng tuyệt đối** riêng ở gần 0.

Việc thêm các điều kiện này giúp ví dụ chạy ổn định trên miền `double` của Java.

## 4. Triển khai so sánh **tương đối thuần túy** trong Java 17+

Tạo tệp `RelativeErrorDemo.java`:

~~~java
public class RelativeErrorDemo {
    /**
     * Gần bằng theo tỷ lệ đối xứng max(abs(a), abs(b)).
     * Chính sách: NaN khác mọi giá trị; vô cực bằng chính cùng dấu.
     * Ngưỡng phải hữu hạn và nằm trong [0, 1).
     */
    static boolean nearlyEqualRelative(double a, double b, double relTol) {
        if (!Double.isFinite(relTol) || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("invalid relative tolerance");
        }

        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (a == b) {
            return true; // gồm +0.0/-0.0 và vô cực cùng dấu
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }

        // Với relTol < 1, hai số khác dấu hoặc một số là 0
        // không thể đạt điều kiện sai số tương đối thuần túy.
        if (a == 0.0 || b == 0.0 || ((a < 0.0) != (b < 0.0))) {
            return false;
        }

        // Hai số hữu hạn cùng dấu: phép trừ không tràn vô cực.
        double scale = Math.max(Math.abs(a), Math.abs(b));
        double relativeError = Math.abs(a - b) / scale;
        return relativeError <= relTol;
    }

    public static void main(String[] args) {
        System.out.println("largeLoose="
                + nearlyEqualRelative(1_000_000.0, 1_000_000.5, 1e-6));
        System.out.println("largeTight="
                + nearlyEqualRelative(1_000_000.0, 1_000_000.5, 1e-7));
        System.out.println("tiny="
                + nearlyEqualRelative(1e-12, 1.01e-12, 0.02));
        System.out.println("zeroVsTiny="
                + nearlyEqualRelative(0.0, 1e-12, 0.02));
        System.out.println("signedZero="
                + nearlyEqualRelative(0.0, -0.0, 0.0));
        System.out.println("oppositeLarge="
                + nearlyEqualRelative(
                        Double.MAX_VALUE, -Double.MAX_VALUE, 0.5));
        System.out.println("sameInfinity="
                + nearlyEqualRelative(
                        Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1e-6));
        System.out.println("notANumber="
                + nearlyEqualRelative(Double.NaN, Double.NaN, 1e-6));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RelativeErrorDemo.java
java RelativeErrorDemo
~~~

Đầu ra chính xác:

~~~text
largeLoose=true
largeTight=false
tiny=true
zeroVsTiny=false
signedZero=true
oppositeLarge=false
sameInfinity=true
notANumber=false
~~~

**Đọc kết quả:**

- `1_000_000.0` và `1_000_000.5` chỉ lệch xấp xỉ `5e-7` tương đối nên lọt ngưỡng `1e-6` nhưng không lọt `1e-7`.
- `1e-12` và `1.01e-12` lệch khoảng 1%, nên lọt ngưỡng 2% dù hiệu tuyệt đối chỉ khoảng `1e-14`.
- `0` và `1e-12` có tỷ lệ lệch bằng 100% theo mẫu số `max`, nên không được coi là gần nếu chỉ dùng tương đối với ngưỡng dưới 1.
- `+0.0` và `-0.0` so sánh `==` là `true`, nên được chấp nhận trước khi tính tỷ lệ.
- Chính sách ở đây coi hai vô cực **cùng dấu** là bằng nhau, vô cực khác dấu hoặc so với số hữu hạn là không gần; `NaN` luôn trả `false`.

Trong thực tế, một hệ thống có thể yêu cầu **từ chối mọi `NaN`/`Infinity`** thay vì cho phép vô cực cùng dấu. Nếu vậy, hãy kiểm tra `Double.isFinite(a) && Double.isFinite(b)` *trước* câu `a == b` và trả lỗi hoặc `false` theo hợp đồng API. Điều quan trọng là chính sách phải **được ghi rõ và kiểm thử**.

## 5. Vì sao phải kết hợp sai số tuyệt đối khi gần 0?

Một ví dụ rất thường gặp: thuật toán toán học kỳ vọng kết quả `0.0`, nhưng tính toán thực tế cho `1e-12`. Với sai số tương đối đối xứng:

\[
\frac{|0 - 10^{-12}|}{\max(0, 10^{-12})} = 1
\]

Ngưỡng `relTol = 1e-6` không chấp nhận khác biệt này, dù `1e-12` có thể hoàn toàn nằm trong sai số đo hoặc giới hạn số học hợp lý của bài toán.

Cách thường dùng là **kết hợp điều kiện tuyệt đối hoặc điều kiện tương đối**:

\[
\operatorname{near}(a,b) =
(|a-b| \le absTol)
\;\lor\;
\left(
\frac{|a-b|}{\max(|a|,|b|)} \le relTol
\right)
\]

trong đó trường hợp `scale == 0` và các giá trị đặc biệt được xử lý riêng.

Cũng có thể viết dưới dạng `|a-b| <= max(absTol, relTol*scale)` trên các giá trị hữu hạn thông thường, nhưng **không nên nhân/chênh lệch một cách không kiểm soát** khi làm việc gần giới hạn `double`. Cách kiểm tra hai nhánh riêng giúp tránh nhân `relTol * scale` và cho phép kiểm tra trước khi tính tỷ lệ.

Hai tham số có **vai trò khác nhau**:

| Tham số | Đơn vị | Dùng khi nào |
|---|---|---|
| `absTol` | Cùng đơn vị với `a` và `b` | Cho phép sai số nhỏ gần số 0 |
| `relTol` | Không thứ nguyên | Cho phép sai số tỷ lệ theo độ lớn giá trị |
 
Ví dụ, nếu dữ liệu là mét, `absTol = 1e-9` có nghĩa dung sai tuyệt đối 1 nanomét; `relTol = 1e-6` là dung sai một phần triệu tương đối. **Không được lấy ngưỡng chỉ vì thấy nó phổ biến**; hãy chọn theo độ chính xác đo và hậu quả khi chấp nhận nhầm.

## 6. Triển khai kết hợp tuyệt đối/tương đối an toàn với số hữu hạn

Tạo tệp `HybridToleranceDemo.java`:

~~~java
public class HybridToleranceDemo {
    /**
     * Chính sách: chấp nhận nếu sai số tuyệt đối HOẶC tương đối đủ nhỏ.
     * Cho phép hai vô cực cùng dấu bằng nhau, NaN luôn khác.
     */
    static boolean nearlyEqual(double a, double b,
                               double absTol, double relTol) {
        if (!Double.isFinite(absTol) || absTol < 0.0
                || !Double.isFinite(relTol)
                || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("invalid tolerance");
        }

        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }

        // Có thể thành Infinity nếu a và b cực lớn, trái dấu.
        double diff = Math.abs(a - b);
        if (diff <= absTol) {
            return true;
        }

        // Nếu cả hai hữu hạn, khác dấu hoặc có một số bằng 0,
        // sai số tương đối >= 1. absTol đã được thử ở trên.
        if (a == 0.0 || b == 0.0 || ((a < 0.0) != (b < 0.0))) {
            return false;
        }

        double scale = Math.max(Math.abs(a), Math.abs(b));
        return diff / scale <= relTol;
    }

    public static void main(String[] args) {
        double absTol = 1e-10;
        double relTol = 1e-6;

        System.out.println("nearZero="
                + nearlyEqual(0.0, 1e-12, absTol, relTol));
        System.out.println("largeClose="
                + nearlyEqual(1_000_000.0, 1_000_000.5,
                        absTol, relTol));
        System.out.println("largeFar="
                + nearlyEqual(1_000_000.0, 1_000_002.0,
                        absTol, relTol));
        System.out.println("oppositeSmall="
                + nearlyEqual(1e-12, -1e-12, absTol, relTol));
        System.out.println("oppositeLarge="
                + nearlyEqual(Double.MAX_VALUE,
                        -Double.MAX_VALUE, absTol, relTol));
        System.out.println("exactZero="
                + nearlyEqual(0.0, -0.0, 0.0, 0.0));
        System.out.println("notFinite="
                + nearlyEqual(Double.NaN, 1.0, absTol, relTol));
    }
}
~~~

~~~bash
javac HybridToleranceDemo.java
java HybridToleranceDemo
~~~

Đầu ra:

~~~text
nearZero=true
largeClose=true
largeFar=false
oppositeSmall=true
oppositeLarge=false
exactZero=true
notFinite=false
~~~

**Giải thích điều kiện đối với dấu:** Khi `a` và `b` hữu hạn, khác 0 và trái dấu, `|a-b| >= max(|a|,|b|)`. Do đó tỷ số tương đối không nhỏ hơn 1, không thể thỏa `relTol < 1`. Trường hợp này chỉ có thể được chấp nhận nhờ **`absTol`** nếu cả hai nằm rất gần 0. Bằng việc kiểm tra `absTol` trước, hàm vẫn có thể chấp nhận `1e-12` và `-1e-12` mà không cần đánh đồng hai số khác dấu ở quy mô lớn.

**Vì sao không tính `diff / scale` ngay từ đầu?** Nếu `a == b == 0.0`, `scale` bằng 0 và biểu thức `0.0 / 0.0` thành `NaN`. Nếu hai giá trị khác dấu cực lớn, `a-b` có thể thành `Infinity`; nhánh kiểm tra dấu tránh dựa vào tỷ lệ không cần thiết. Với hai số hữu hạn cùng dấu, độ lớn chênh lệch không vượt số có độ lớn lớn hơn và phép trừ không gây overflow về vô cực.

## 7. Ba lựa chọn mẫu số và khi nào dùng

Không có một công thức tương đối độc quyền:

### 7.1. Dựa trên giá trị chuẩn

\[
\frac{|measured-reference|}{|reference|} \le relTol
\]

Phù hợp khi có **reference khác 0** mang ý nghĩa vật lý hoặc nghiệp vụ: một mục tiêu sản lượng, một giá trị đo chuẩn, kết quả tham chiếu có độ chính xác cao. Nhưng đảo thứ tự `measured` và `reference` thay đổi mẫu số, do đó có thể thay đổi kết luận.

Ví dụ, lấy `reference = 100`, `measured = 101` thì sai số tham chiếu bằng 1%; nếu lấy `reference = 101`, `measured = 100` thì sai số bằng khoảng 0,9901%.

### 7.2. Đối xứng theo độ lớn lớn nhất

\[
\frac{|a-b|}{\max(|a|,|b|)} \le relTol
\]

Phù hợp khi không có tham chiếu ưu tiên, ví dụ kiểm tra hai kết quả tính độc lập có gần nhau không. Đây là cách chọn trong hai chương trình Java trên.

### 7.3. Dựa trên thang đo định sẵn

Một phép so sánh có thể sử dụng mẫu số `scale` theo chuẩn ngành (kích thước thiết bị, miền chuẩn hóa, định mức) thay vì chọn từ hai giá trị. Cách này hữu ích khi **gần 0** nhưng bài toán vẫn có một thang đo tự nhiên.

**Quan trọng:** lựa chọn mẫu số phải dựa trên ý nghĩa dữ liệu. Đừng gọi ba công thức là hoàn toàn tương đương, và đừng đưa ra ngưỡng `relTol` mà không nêu mẫu số.

## 8. `float` so với `double` và ý nghĩa của epsilon

`float` có khoảng 24 bit độ chính xác phần định trị nhị phân, `double` có 53 bit. Một phép đo hoặc thuật toán ở kiểu `float` thường tạo sai số tương đối làm tròn lớn hơn trường hợp tương ứng ở `double`, nhưng **không có quy luật rằng cứ dùng `double` thì ngưỡng `1e-12` luôn phù hợp**.

Các hằng:

- `Math.ulp(1.0f)` là khoảng cách tới giá trị `float` liền trên ở `1.0f`, xấp xỉ `1.1920929e-7`.
- `Math.ulp(1.0)` là khoảng cách tới giá trị `double` liền trên ở `1.0`, xấp xỉ `2.220446049250313e-16`.

Đây là thông tin về **độ phân giải biểu diễn tại một thang cụ thể**, không phải lời hứa mọi phép tính đều có sai số không vượt một ULP. Sai số có thể tích lũy qua nhiều bước, phát sinh từ mô hình dữ liệu đầu vào, hoặc do thuật toán không ổn định.

### Một số tên thường bị hiểu nhầm

- `Double.MIN_VALUE` là số dương subnormal nhỏ nhất lớn hơn 0 của kiểu `double`, **không phải epsilon để so sánh gần bằng**.
- `Float.MIN_VALUE` cũng là số dương subnormal nhỏ nhất lớn hơn 0, không phải ngưỡng tương đối phổ quát.
- Java không có một hằng `Double.EPSILON` tiêu chuẩn trong `java.lang.Double` để tự động giải quyết mọi phép so sánh gần bằng. `Math.ulp(1.0)` thường dùng để minh họa độ phân giải gần 1, nhưng **ngưỡng so sánh vẫn phải đến từ yêu cầu bài toán**.

Nếu cần kiểm tra **sự trùng khớp chính xác theo bit**, hoặc dùng số làm khóa định danh, tuyệt đối không đánh tráo yêu cầu đó bằng một phép so sánh có dung sai.

## 9. Các bẫy thường gặp: overflow, underflow, NaN và vô cực

### 9.1. Chênh lệch của hai số hữu hạn có thể overflow

~~~java
double a = Double.MAX_VALUE;
double b = -Double.MAX_VALUE;
double difference = Math.abs(a - b); // Infinity
~~~

Đây là đoạn minh họa. Cả `a` và `b` hữu hạn, nhưng chênh lệch toán học vượt miền số hữu hạn `double`, vì thế `difference` thành `Infinity`. Nếu sau đó tùy tiện tính `difference / Math.max(Math.abs(a), Math.abs(b))`, tỷ số trở thành `Infinity`, không phải sai số tương đối hữu hạn dự kiến khoảng 2. Dù hai số không nên được coi là “gần nhau”, phép chia đơn giản này cho thấy **công thức viết thẳng có thể đánh mất thông tin tại biên**.

Với ngưỡng `relTol < 1`, chỉ cần loại trường hợp trái dấu (sau kiểm tra tuyệt đối trong phiên bản hybrid). Điều đó tránh phải tính tỷ lệ ở vùng overflow của hiệu. Nếu bài toán đòi **tính chính xác trị số tỷ lệ tới ngưỡng lớn hơn hoặc bằng 1**, cần một thuật toán khác có xử lý thang giá trị, không được tái sử dụng hàm được thiết kế cho `relTol < 1`.

### 9.2. Dưới mức biểu diễn và trường hợp `scale` quá nhỏ

Gần 0, giá trị `double` có thể thuộc miền **subnormal**, và số học có thể bị underflow. Sai số tương đối lúc đó có thể rất nhạy với lượng thay đổi một ULP; phép so sánh có `absTol` mang ý nghĩa đo đạc thường hữu ích hơn.

Tuy nhiên, `absTol` quá lớn so với ý nghĩa nghiệp vụ cũng gây **false positive**: hai số khác nhau đáng kể theo tỷ lệ lại bị xem là gần nhau chỉ vì chúng đều nhỏ. Vì vậy, không được chọn `absTol` một cách máy móc theo “một số bé”.

### 9.3. `NaN` và `Infinity` không phải số hữu hạn bình thường

Trong Java:

- `Double.NaN == Double.NaN` là `false`.
- `Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY` là `true`.
- `Double.isNaN(a)` hoặc `Double.isFinite(a)` cung cấp kiểm tra rõ ràng.
- Nếu chỉ một toán hạng là vô cực và toán hạng kia hữu hạn, phép gần bằng được định nghĩa trong hai ví dụ là `false`.

**Chính sách của hai ví dụ**: NaN không bao giờ gần bằng; vô cực chỉ gần bằng chính vô cực cùng dấu. Một thư viện kiểm tra dữ liệu đầu vào có thể chọn **bác bỏ mọi giá trị không hữu hạn** ngay từ đầu để kết luận có ý nghĩa theo số đo. Không có một chính sách ngoại lệ duy nhất cho mọi ứng dụng.

### 9.4. `-0.0` và `+0.0`

`-0.0 == 0.0` cho `true` mặc dù biểu diễn bit và dấu khi chia cho 0 có thể khác. Hai hàm mẫu chấp nhận chúng là bằng nhau, giống ngữ nghĩa `==`. Nếu ứng dụng cần phân biệt bit dấu của số 0, phải dùng kiểm tra khác phù hợp, chứ không dùng phép gần bằng.

## 10. Ví dụ kiểm tra lỗi do chọn ngưỡng tuyệt đối cố định

Một sai lầm phổ biến là dùng mỗi điều kiện:

~~~java
Math.abs(a - b) < 1e-6
~~~

Thử ở hai thang khác nhau:

- `a = 1e-12`, `b = 2e-12`: hiệu chỉ `1e-12`, nên ngưỡng `1e-6` chấp nhận, dù hai số lệch **50% theo mẫu số max**.
- `a = 1_000_000`, `b = 1_000_000.5`: hiệu `0.5`, nên ngưỡng `1e-6` loại, dù lệch **khoảng 0,00005%**.

Tạo tệp `AbsoluteVsRelativeDemo.java`:

~~~java
public class AbsoluteVsRelativeDemo {
    static boolean absoluteClose(double a, double b, double absTol) {
        return Math.abs(a - b) <= absTol;
    }

    static boolean relativeClose(double a, double b, double relTol) {
        if (a == b) {
            return true;
        }
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return Math.abs(a - b) / scale <= relTol;
    }

    public static void main(String[] args) {
        System.out.println("tinyAbs="
                + absoluteClose(1e-12, 2e-12, 1e-6));
        System.out.println("tinyRel="
                + relativeClose(1e-12, 2e-12, 1e-6));
        System.out.println("largeAbs="
                + absoluteClose(1_000_000, 1_000_000.5, 1e-6));
        System.out.println("largeRel="
                + relativeClose(1_000_000, 1_000_000.5, 1e-6));
    }
}
~~~

~~~bash
javac AbsoluteVsRelativeDemo.java
java AbsoluteVsRelativeDemo
~~~

Đầu ra:

~~~text
tinyAbs=true
tinyRel=false
largeAbs=false
largeRel=true
~~~

**Giới hạn của ví dụ phụ:** `relativeClose` trong class này **chỉ để minh họa so sánh ở hai cặp hữu hạn khác 0 cùng dấu được gọi từ `main`**. Nó không phải hàm thư viện an toàn cho mọi đầu vào, vì thiếu xử lý `NaN`/vô cực, `scale == 0` và overflow của hiệu. Trong mã ứng dụng, hãy dùng triển khai kiểm tra đầy đủ tại phần 4 hoặc phần 6, hoặc áp dụng chính sách đặc thù có kiểm thử.

## 11. Gần bằng không phải quan hệ tương đương

Một điểm dễ bị bỏ qua là phép “gần bằng” với dung sai dương thường **không có tính bắc cầu** (*transitive*):

- Có thể `near(a, b) == true`.
- Có thể `near(b, c) == true`.
- Nhưng `near(a, c) == false`.

Ví dụ, dùng ngưỡng tuyệt đối `0.1`: `a=0.0`, `b=0.09`, `c=0.18`. Hai cặp kề có hiệu 0.09, còn `a` và `c` cách nhau 0.18. Với dung sai tương đối, tính không bắc cầu cũng xảy ra ở các giá trị dương gần nhau.

**Hệ quả thiết kế:** Không dùng `nearlyEqual` như thể nó là `Object.equals`, hoặc trực tiếp thay đổi `equals`/`hashCode` của các khóa `HashMap`/`HashSet`. Các cấu trúc tập hợp thường đòi quan hệ tương đương ổn định; dùng gần bằng có thể tạo hành vi trái mong đợi trong phân nhóm, tra cứu, sắp xếp.

Nếu cần phân cụm giá trị gần nhau, phải xác định thuật toán phân cụm hoặc **lượng tử hóa** (*quantization*) với quy tắc biên rõ ràng, thay vì giả định phép gần bằng bắc cầu. Nếu cần so sánh định danh hoặc kết quả chính xác, dùng kiểu số/chính sách chính xác phù hợp.

## 12. Không phải bài toán nào cũng nên dùng dung sai tương đối

| Bài toán | Cách tiếp cận đáng cân nhắc |
|---|---|
| So sánh hai kết quả số học/đo đạc có thang dao động lớn | Sai số tương đối đối xứng, hoặc kết hợp với tuyệt đối |
| So sánh với **giá trị chuẩn** khác 0 | Sai số tương đối theo `reference` |
| So sánh số gần 0 với sai số đo có đơn vị rõ ràng | Ngưỡng tuyệt đối theo đơn vị vật lý |
| Kiểm tra sự bằng nhau chính xác của số lượng nguyên | `==` trên `int`/`long` hoặc kiểu số nguyên chính xác |
| So sánh dữ liệu tiền thập phân chính xác | `BigDecimal` và chính sách scale/rounding, không dùng sai số `double` tùy ý |
| So sánh bit biểu diễn của số thực | Kiểm tra bit phù hợp, không dùng phép gần bằng |
| Xác định thứ tự sắp xếp | Comparator có thứ tự nhất quán; tránh “bằng nhau gần đúng” không bắc cầu |

Các dung sai mặc định “luôn là `1e-6`” hoặc “luôn là `Double.MIN_VALUE`” là dấu hiệu phải xem lại yêu cầu độ chính xác thực tế.

## 13. Những nhầm lẫn phổ biến

1. **“Sai số tương đối là `Math.abs(a-b)`.”** Không: đó là sai số **tuyệt đối**. Sai số tương đối phải so với một thang đo được xác định.
2. **“`Math.abs(a-b) / Math.abs(b)` luôn an toàn.”** Không: `b` có thể bằng 0, gần 0, hoặc `a-b` overflow; đồng thời mẫu số dựa vào `b` không đối xứng.
3. **“Hai số gần 0 thì ngưỡng tương đối nhỏ chắc chắn chấp nhận.”** Không: `0` so với `1e-12` có sai số tương đối đối xứng bằng 1.
4. **“Ngưỡng `1e-12` là chính xác cho mọi `double`.”** Không: phải chọn theo thang dữ liệu, phép toán, số bước tính và mục tiêu nghiệp vụ.
5. **“`Double.MIN_VALUE` chính là epsilon của `double`.”** Không: nó là số dương subnormal nhỏ nhất, không phải ngưỡng sai số tương đối tổng quát.
6. **“Nếu chênh lệch hữu hạn thì phép `a-b` luôn hữu hạn.”** Không: hai số lớn trái dấu có thể làm chênh lệch overflow tới vô cực.
7. **“`NaN` có thể được xử lý giống số hữu hạn.”** Không: phải có chính sách riêng; `NaN == NaN` là `false`.
8. **“Hai số rất gần thì có thể làm khóa bằng nhau trong mọi `HashMap`.”** Không: gần bằng với ngưỡng dương không nhất thiết bắc cầu.
9. **“Chỉ cần phần trăm sai số, không cần chọn mẫu số.”** Không: mẫu số là phần của định nghĩa bài toán; `reference`, `max` và thang chuẩn cố định có thể cho kết quả khác nhau.
10. **“Tăng `relTol` tới 100% chắc chắn không có vấn đề.”** Một ngưỡng quá rộng làm mất ý nghĩa “gần”; các hàm mẫu cố tình giới hạn `relTol < 1` và yêu cầu chính sách khác nếu cần miền rộng hơn.

## 14. Checklist xây dựng phép so sánh gần bằng

1. **Xác định dữ liệu:** đại lượng đo, kết quả mô phỏng, giá tiền, tỉ lệ hay mã định danh?
2. **Chọn định nghĩa sai số:** theo giá trị chuẩn, theo `max(|a|, |b|)` hay một thang cố định có ý nghĩa.
3. **Đặt `relTol` theo yêu cầu**, không dựa trên một hằng mặc định chưa được giải thích.
4. **Xử lý gần 0:** nếu cần, đặt `absTol` theo đơn vị và độ phân giải của dữ liệu.
5. **Kiểm tra đầu vào đặc biệt:** `NaN`, vô cực, số 0 có dấu; xác định chúng bị từ chối hay so sánh bằng quy tắc riêng.
6. **Tránh overflow trung gian** khi số hữu hạn nhưng rất lớn/trái dấu.
7. **Không nhầm “gần bằng” với “bằng chính xác”.** Đặc biệt khi làm khóa, nhóm, sắp xếp và xác thực dữ liệu.
8. **Viết test biên:** cùng số, hai số lân cận, ở trên/dưới ngưỡng, gần 0, khác dấu, số cực đại, vô cực, NaN, ngưỡng âm/NaN.
9. **Ghi rõ API trả `boolean` hay ném exception** khi ngưỡng không hợp lệ; ví dụ mẫu ném `IllegalArgumentException`.
10. **Đánh giá sai số nguồn**: nếu cảm biến sai tới 1%, ngưỡng `1e-12` không phản ánh độ chắc chắn của dữ liệu cho dù dùng `double`.

## 15. Tự kiểm tra

1. Sai số tuyệt đối khác sai số tương đối ra sao? Đơn vị của mỗi loại?
2. Vì sao `a=1e-12` và `b=2e-12` có sai số tuyệt đối rất nhỏ nhưng sai số tương đối đối xứng lớn?
3. Sai số tương đối theo `reference` có giữ nguyên khi đổi chỗ hai số không?
4. Tại sao `0.0` với `1e-12` không gần nhau theo `relTol=1e-6` thuần túy, nhưng có thể gần nhau theo `absTol=1e-10`?
5. Vì sao nên xử lý `a == b` trước khi chia cho `max(|a|,|b|)`?
6. Nếu một số là `Double.NaN`, có nên dùng trực tiếp phép `==` hoặc phép chia để kết luận gần bằng?
7. Hai số hữu hạn `Double.MAX_VALUE` và `-Double.MAX_VALUE` có hiệu `a-b` kiểu `double` ra sao?
8. Khi nào nên chọn `BigDecimal` thay vì so sánh `double` bằng dung sai?
9. Tại sao phép gần bằng không thích hợp để thay thế `equals`/`hashCode`?
10. Nếu hai phép đo có cùng đơn vị nhưng thay đổi từ `1e-9` tới `1e9`, tại sao ngưỡng tuyệt đối đơn độc thường không đủ?

**Gợi ý đáp án:** (1) Tuyệt đối là độ lệch cùng đơn vị, tương đối là tỷ lệ không thứ nguyên. (2) Chênh lệch bằng nửa giá trị lớn hơn nên tỷ lệ `0.5`. (3) Không, vì đổi giá trị chuẩn làm đổi mẫu số. (4) Relative error bằng 1, còn absolute difference bằng `1e-12`. (5) Tránh `0/0` khi cả hai 0 và xử lý trùng nhau chính xác. (6) Xử lý `NaN` theo chính sách; trong ví dụ là `false`. (7) Overflow thành `Infinity`. (8) Khi cần thập phân chính xác theo nghiệp vụ như tiền. (9) Quan hệ gần bằng có thể không bắc cầu. (10) Sai số có ý nghĩa phụ thuộc thang giá trị, thường cần phần tương đối kết hợp điều kiện tuyệt đối gần 0.

**Ghi nhớ:** **Sai số tương đối đánh giá chênh lệch theo thang giá trị**, giúp so sánh số `float`/`double` ở nhiều độ lớn. Chọn rõ **mẫu số**, đặt **ngưỡng theo yêu cầu bài toán**, và xử lý **gần 0, NaN, vô cực và overflow trung gian**. Khi cần, kết hợp **sai số tuyệt đối hoặc tương đối**, chứ không dùng một epsilon cố định cho mọi tình huống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-009">Kết hợp sai số tuyệt đối và tương đối</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp sai số tuyệt đối và tương đối khi so sánh số thực trong Java

## 1. Vì sao so sánh số thực cần ngưỡng sai số?

`float` và `double` trong Java dùng biểu diễn dấu phẩy động nhị phân. Nhiều giá trị thập phân, như `0.1`, không có biểu diễn nhị phân hữu hạn chính xác. Khi tính toán, các phép toán có thể làm tròn, tạo ra kết quả gần với giá trị mong đợi nhưng không có cùng mẫu bit.

Ví dụ:

```java
double computed = 0.1 + 0.2;
double expected = 0.3;

System.out.println(computed == expected); // false
System.out.println(computed);              // 0.30000000000000004
System.out.println(expected);              // 0.3
```

`==` trả lời câu hỏi: *hai toán hạng `double` hiện có bằng nhau theo quy tắc toán tử Java hay không?* Nó không trả lời câu hỏi: *hai giá trị có đủ gần nhau theo dung sai của bài toán hay không?*

Khi bài toán cho phép sai số, ta thường so sánh:

\[
|a-b|\le T,
\]

trong đó `T` là ngưỡng dung sai (*tolerance*). Vấn đề là **ngưỡng nào phù hợp với độ lớn của dữ liệu**. Một ngưỡng cố định có thể hợp lý ở gần 0 nhưng quá chặt với số lớn; ngược lại, ngưỡng tỷ lệ theo độ lớn có thể gần bằng 0 và không bảo vệ được các kết quả nhỏ.

Vì vậy, trong nhiều bài toán tính gần đúng, ta kết hợp **sai số tuyệt đối** và **sai số tương đối**. Đây là tiêu chí chấp nhận sai lệch do ứng dụng chọn, không phải quy tắc “làm cho mọi phép so sánh `double` đều đúng”.

## 2. Sai số tuyệt đối và tương đối khác nhau ở đâu?

### 2.1. Sai số tuyệt đối

Với giá trị tính được `a` và giá trị tham chiếu `b`:

\[
E_{\text{abs}}=|a-b|.
\]

Ví dụ `a = 10.002`, `b = 10.000` có sai số tuyệt đối xấp xỉ `0.002`. Nếu dữ liệu là mét thì dung sai tuyệt đối cũng được hiểu theo đơn vị mét; nếu là giây thì theo đơn vị giây.

Điều kiện so sánh chỉ dùng sai số tuyệt đối:

\[
|a-b|\le \text{absTol}.
\]

Trong Java:

```java
boolean closeByAbsoluteTolerance =
        Math.abs(a - b) <= absTol;
```

Đoạn trên là **minh họa biểu thức**, giả sử `a`, `b`, `absTol` đã được khai báo và đều là giá trị hữu hạn, `absTol >= 0`. Đối với dữ liệu bất kỳ, phải xử lý riêng `NaN`, vô cực và trường hợp phép trừ tràn ra vô cực.

Sai số tuyệt đối phù hợp khi có một khoảng sai lệch tối đa mang ý nghĩa thực tế. Chẳng hạn, nếu cảm biến đo chiều dài chấp nhận lệch `0.001` mét, ngưỡng tuyệt đối `0.001` có thể có ý nghĩa dù số đo nằm gần 0.

Nhược điểm: một ngưỡng như `1e-9` có thể quá lớn khi so sánh các giá trị cỡ `1e-12`, nhưng lại quá nhỏ khi so sánh kết quả có độ lớn hàng tỷ.

### 2.2. Sai số tương đối

Khi có giá trị tham chiếu `b != 0`, dạng thường dùng là:

\[
E_{\text{rel,ref}}=\frac{|a-b|}{|b|}.
\]

Nếu `b = 1_000_000` và `a = 1_000_000.001`, sai lệch tuyệt đối khoảng `0.001`, nhưng sai lệch tương đối khoảng `10^{-9}` (một phần tỷ).

Đây là cách diễn giải tốt khi **`b` là một giá trị chuẩn có ý nghĩa**, ví dụ kết quả tham chiếu độc lập hoặc chuẩn hiệu chuẩn. Nhưng mẫu số `|b|` gây vấn đề khi `b = 0` và làm tiêu chí rất khắt khe khi `b` cực nhỏ.

Trong so sánh **hai kết quả ngang vai trò** (không có bên nào là chuẩn), thường dùng chuẩn hóa đối xứng:

\[
S=\max(|a|,|b|),\qquad
|a-b|\le \text{relTol}\times S.
\]

Cách này không ưu tiên bên nào, và cùng kết quả khi đổi vị trí `a`, `b`. `relTol` không có đơn vị; chẳng hạn `1e-9` tương ứng dung sai ở mức khoảng một phần tỷ **theo thang độ lớn `S`**. Nếu cả hai cùng bằng 0, cần xử lý riêng hoặc kết hợp ngưỡng tuyệt đối.

**Phân biệt rõ:** “Sai số tương đối” có nhiều cách định nghĩa mẫu số tùy bài toán. Dùng `max(|a|, |b|)` là một lựa chọn đối xứng cho phép so sánh gần bằng nhau, không phải mẫu số duy nhất đúng trong mọi lĩnh vực.

## 3. Kết hợp hai ngưỡng thành một phép so sánh

Một tiêu chí phổ biến:

\[
\boxed{|a-b|\le\max\bigl(\text{absTol},
\text{relTol}\cdot\max(|a|,|b|)\bigr)}
\]

trong đó:

- `absTol >= 0`: dung sai tuyệt đối, cùng đơn vị với `a` và `b`;
- `relTol >= 0`: dung sai tương đối, không có đơn vị;
- `max(|a|, |b|)`: thang độ lớn đối xứng;
- dấu `<=` quy định sai lệch đúng bằng ngưỡng cũng được chấp nhận.

Điều kiện trên tương đương:

\[
|a-b|\le\text{absTol}
\quad\text{HOẶC}\quad
|a-b|\le\text{relTol}\times\max(|a|,|b|).
\]

Nghĩa là hai giá trị được coi là “đủ gần” nếu đáp ứng **ít nhất một** tiêu chí. Khi một giá trị gần 0, ngưỡng tuyệt đối bảo vệ phép so sánh. Khi cả hai có độ lớn đáng kể, ngưỡng tương đối thường là phần quyết định.

Một quy tắc khác cũng hợp lệ tùy tài liệu/ứng dụng là:

\[
|a-b|\le \text{absTol}
+\text{relTol}\cdot\max(|a|,|b|).
\]

Dạng **cộng** rộng hơn hoặc bằng dạng **max** vì tổng hai số không âm không nhỏ hơn số lớn nhất. Không nên xem hai dạng tương đương hoặc dùng lẫn nếu cần kết quả nhất quán với một tiêu chuẩn, thư viện hay hệ thống kiểm thử cụ thể. Bài này dùng **dạng max** xuyên suốt để dễ giải thích và kiểm thử.

### Minh họa tại ba thang đo

Giả sử `absTol = 1e-9`, `relTol = 1e-9`:

| Cặp giá trị | Hiệu tuyệt đối xấp xỉ | Ngưỡng chi phối | Đủ gần? |
| --- | ---: | --- | --- |
| `0.0` và `1e-10` | `1e-10` | `absTol = 1e-9` | Có |
| `0.0` và `1e-7` | `1e-7` | `absTol = 1e-9` | Không |
| `1e6` và `1e6 + 1e-4` | `1e-4` | `relTol × 1e6 ≈ 1e-3` | Có |
| `1e6` và `1e6 + 1e-2` | `1e-2` | `relTol × 1e6 ≈ 1e-3` | Không |
| `100.0` và `101.0` | `1.0` | `relTol × 101 ≈ 1.01e-7` | Không |

Các kết luận phụ thuộc **cặp ngưỡng đã chọn**. Không có một cặp `absTol`/`relTol` mặc định thích hợp cho mọi thang đo, mọi phép tính và mọi yêu cầu chính xác.

### Vì sao không áp dụng ngưỡng tương đối ở gần 0 một cách đơn độc?

Với `a = 0.0` và `b = 1e-12`, ta có `S = 1e-12`. Nếu `relTol = 1e-9` thì ngưỡng tương đối chỉ là `1e-21`, nên phép so sánh báo khác nhau dù chênh lệch `1e-12` có thể nhỏ hơn sai số đo hợp lệ. Ngưỡng tuyệt đối cung cấp một mức chấp nhận sai lệch thực tế quanh 0.

Ngược lại, nếu dữ liệu thực sự cần phân biệt `0` với `1e-12` (ví dụ tín hiệu cỡ pico), đặt `absTol = 1e-9` sẽ quá dễ dãi. **Đơn vị và yêu cầu nghiệp vụ phải quyết định `absTol`**, chứ không phải thói quen dùng `1e-9` trong mọi chương trình.

## 4. Hàm `approximatelyEqual` cho `double` trong Java 17

Một hàm sử dụng được phải làm rõ cả cách xử lý giá trị đặc biệt. Trong bài này, ta chọn chính sách:

- `NaN` không “gần bằng” bất kỳ giá trị nào, kể cả `NaN`.
- Hai vô cực **cùng dấu** được coi bằng nhau vì chúng bằng nhau theo `==`; vô cực khác dấu hoặc so với số hữu hạn không được coi là gần.
- `+0.0` và `-0.0` được coi bằng nhau về giá trị số.
- Dung sai phải hữu hạn, không âm; để tránh dung sai tương đối quá dễ dãi, yêu cầu `relTol < 1.0`.
- Với hai giá trị hữu hạn không bằng nhau chính xác, áp dụng công thức kết hợp dạng `max`.

Lưu thành tệp `ApproximatelyEqual.java`:

```java
public final class ApproximatelyEqual {
    private ApproximatelyEqual() {
    }

    public static boolean isClose(
            double a, double b, double absTol, double relTol) {

        if (!Double.isFinite(absTol) || absTol < 0.0
                || !Double.isFinite(relTol)
                || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("Invalid tolerances");
        }

        // Theo chính sách của ví dụ, NaN không so sánh gần bằng.
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }

        // Gồm hai số hữu hạn bằng nhau, ±0.0, và vô cực cùng dấu.
        if (a == b) {
            return true;
        }

        // Nếu còn vô cực mà chưa bằng nhau thì không chấp nhận.
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }

        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        double limit = Math.max(absTol, relTol * scale);

        return difference <= limit;
    }

    public static void main(String[] args) {
        double absTol = 1e-9;
        double relTol = 1e-9;

        System.out.println("0.1 + 0.2 == 0.3: " + (0.1 + 0.2 == 0.3));
        System.out.println("decimal close: "
                + isClose(0.1 + 0.2, 0.3, absTol, relTol));
        System.out.println("near zero small: "
                + isClose(0.0, 1e-10, absTol, relTol));
        System.out.println("near zero large: "
                + isClose(0.0, 1e-7, absTol, relTol));
        System.out.println("large values close: "
                + isClose(1e6, 1e6 + 1e-4, absTol, relTol));
        System.out.println("large values far: "
                + isClose(1e6, 1e6 + 1e-2, absTol, relTol));
        System.out.println("NaN: "
                + isClose(Double.NaN, Double.NaN, absTol, relTol));
        System.out.println("same infinity: "
                + isClose(Double.POSITIVE_INFINITY,
                          Double.POSITIVE_INFINITY, absTol, relTol));
        System.out.println("infinity vs finite: "
                + isClose(Double.POSITIVE_INFINITY,
                          Double.MAX_VALUE, absTol, relTol));
        System.out.println("signed zeros: "
                + isClose(-0.0, +0.0, absTol, relTol));
        System.out.println("opposite extremes: "
                + isClose(-Double.MAX_VALUE,
                          Double.MAX_VALUE, absTol, relTol));
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac ApproximatelyEqual.java
java ApproximatelyEqual
```

Kết quả:

```text
0.1 + 0.2 == 0.3: false
decimal close: true
near zero small: true
near zero large: false
large values close: true
large values far: false
NaN: false
same infinity: true
infinity vs finite: false
signed zeros: true
opposite extremes: false
```

Hàm này nhận giá trị `double` đã tính xong. Nó **không sửa hoặc phục hồi độ chính xác** đã mất trong một phép tính. Việc trả `true` chỉ biểu thị cặp giá trị **đạt tiêu chí dung sai cụ thể**.

### Tại sao phải kiểm tra `NaN` và vô cực trước?

`NaN` có tính chất `Double.NaN == Double.NaN` là `false`; phép trừ với `NaN` cũng cho `NaN`. Mọi phép so sánh `NaN <= limit` đều trả `false`. Tuy nhiên, kiểm tra tường minh thể hiện đúng chính sách, tránh vô tình thay đổi hành vi khi sửa thuật toán.

`Infinity - Infinity` tạo `NaN`; vì vậy nếu chỉ tính `Math.abs(a-b)`, hai vô cực cùng dấu có thể bị báo khác nhau. Ví dụ chủ động công nhận chúng qua `a == b`. Với dữ liệu mô phỏng hoặc cảm biến, ứng dụng cũng có thể muốn **từ chối mọi giá trị không hữu hạn** ngay từ đầu; khi ấy thay chính sách `a == b` bằng kiểm tra `Double.isFinite` trước. Chính sách phải được ghi rõ trong API.

### Phép trừ có thể tràn ra vô cực

Với `a = Double.MAX_VALUE` và `b = -Double.MAX_VALUE`, cả hai là số hữu hạn nhưng `a-b` vượt độ lớn hữu hạn của `double`, nên `difference` thành `+Infinity`. Hàm trả `false`, đúng với các ngưỡng hợp lệ nêu trên (`relTol < 1`, `absTol` hữu hạn). Đây là lý do hàm tránh chia `difference` cho `scale` để lập tỷ số: phép tính trung gian có thể tràn, dẫn đến kết quả khó giải thích.

Dù đã bảo vệ trường hợp này, không nên coi công thức là thuật toán tính khoảng cách chính xác tùy ý ở toàn miền `double`. Nó là một **quy tắc chấp nhận gần bằng nhau** dựa trên số học `double` và dung sai ứng dụng.

## 5. Chọn `absTol` và `relTol` dựa trên dữ liệu

Trước khi quyết định dung sai, cần biết độ lớn điển hình, đơn vị và nguồn sai số.

### 5.1. Sai số tuyệt đối mang đơn vị

Ví dụ hai kết quả tính quãng đường theo mét được coi tương đương nếu chênh nhau không quá `0.001 m`. Khi đó `absTol = 0.001` có căn cứ nghiệp vụ. Nếu đổi kết quả sang milimét, cùng dung sai đó phải đổi thành `1.0 mm`. Không được giữ nguyên trị số `0.001` khi đổi đơn vị.

Với những giá trị cực nhỏ, nếu `absTol` quá lớn thì nhiều giá trị thực sự khác nhau đều bị xem là gần bằng 0. Một cách chọn ngưỡng là dựa vào độ phân giải thiết bị đo, độ chính xác của dữ liệu nguồn hoặc sai số phân tích đã biết.

### 5.2. Sai số tương đối theo quy mô

Nếu kết quả ở thang hàng triệu có thể dao động ở mức vài phần tỷ do thuật toán, một `relTol` được đặt theo mức sai số dự kiến có thể hợp lý hơn ngưỡng tuyệt đối cực nhỏ.

Ví dụ `relTol = 1e-9` cho phép sai lệch khoảng `0.001` ở thang `1e6`. Nhưng điều đó **không** tự chứng minh sai lệch `0.001` chấp nhận được với số lượng tài sản, tiền hoặc ID. Cần dựa vào quy định của miền nghiệp vụ trước khi chọn số.

### 5.3. Ngưỡng chính xác bằng 0

Nếu đặt `absTol = 0.0`, `relTol = 0.0`, hàm ở mục 4 gần như chỉ chấp nhận các cặp **bằng nhau về giá trị số**: nó vẫn cho `+0.0` và `-0.0` bằng nhau, cho vô cực cùng dấu bằng nhau, và từ chối `NaN`. Điều này khác với yêu cầu so sánh cùng **mẫu bit**.

Không nên dùng ngưỡng 0 nếu mục tiêu là bỏ qua sai số làm tròn thông thường.

### 5.4. Không lấy epsilon máy làm ngưỡng tùy ý

`Math.ulp(1.0)` đối với `double` bằng khoảng `2.220446049250313E-16`. Đây là khoảng cách giữa `1.0` và số `double` kế tiếp, không phải một “dung sai chung” hợp lý cho toàn miền.

Một ULP thay đổi theo độ lớn giá trị: `Math.ulp(1e16)` là `2.0`. `Double.MIN_VALUE` lại là số **dương nhỏ nhất khác 0**, không phải sai số máy dùng cho mọi phép so sánh. Khi chọn ngưỡng theo số ULP, cần cân nhắc sự thay đổi độ chính xác theo miền, số dưới chuẩn và nghiệp vụ; cách đó phục vụ mục đích khác với `absTol`/`relTol` thuần túy.

## 6. So sánh với giá trị tham chiếu và so sánh đối xứng

Có hai tình huống khác nhau:

**Một bên là tham chiếu đáng tin hơn.** Ví dụ mô phỏng dùng kết quả độ chính xác cao làm chuẩn `reference` và muốn đánh giá kết quả `actual`. Khi đó tiêu chí:

\[
|\text{actual}-\text{reference}|
\le \max(\text{absTol},\text{relTol}\cdot|\text{reference}|)
\]

có thể phản ánh đúng yêu cầu “sai số tính theo quy mô chuẩn”. Tuy nhiên cần có chính sách rõ cho `reference == 0` và dữ liệu không hữu hạn. Nếu miền giá trị có thể rất lớn và dễ tràn, cần thiết kế cách so sánh phù hợp.

**Hai bên ngang vai trò.** Khi đối chiếu kết quả từ hai triển khai mà không chọn bên nào làm chuẩn, tiêu chí đối xứng với `max(|a|,|b|)` giúp đổi chỗ hai đối số mà không đổi phán đoán.

Cả hai tiêu chí đều có thể chấp nhận một cặp số này nhưng từ chối cặp số khác so với tiêu chí còn lại. Đừng thay mẫu số khi đang triển khai một chuẩn kiểm thử hoặc hợp đồng API cụ thể.

### Quan hệ “gần bằng” không có tính bắc cầu

Với `absTol = 0.1` và `relTol = 0`, hãy xét `a = 0.0`, `b = 0.09`, `c = 0.18`:

- `a` gần `b` vì chênh lệch `0.09 <= 0.1`.
- `b` gần `c` vì chênh lệch `0.09 <= 0.1`.
- `a` **không** gần `c` vì chênh lệch `0.18 > 0.1`.

Vì vậy “approximately equal” **không phải quan hệ tương đương toán học**, cho dù hàm được thiết kế đối xứng và trả `true` với hai số hữu hạn giống nhau. Không nên dùng hàm này làm `equals`/`hashCode`, làm định nghĩa tính duy nhất của `HashSet`/`HashMap`, hay làm `Comparator` để sắp xếp các khóa: các cấu trúc đó đòi hỏi các hợp đồng khác, và độ gần không bắc cầu có thể phá hỏng tính nhất quán.

Nếu bài toán cần nhóm giá trị gần nhau, hãy định nghĩa quy tắc gom cụ thể (ví dụ chia khoảng theo độ phân giải được chỉ định), thay vì coi `isClose` là quan hệ bằng nhau của đối tượng.

## 7. `==`, `Double.compare`, `BigDecimal.compareTo` và so sánh gần đúng

Các phép này có mục đích khác nhau:

| Cách so sánh | Mục tiêu | Đặc điểm cần nhớ |
| --- | --- | --- |
| `a == b` với `double` | Bằng nhau theo toán tử số học Java | `+0.0 == -0.0` là `true`; `NaN == NaN` là `false` |
| `Double.compare(a, b)` | Thứ tự nhất quán để so sánh/sắp xếp `double` theo hợp đồng `Double` | Phân biệt `-0.0` với `+0.0`; coi các `NaN` là bằng nhau trong cách so sánh này |
| `isClose(a, b, absTol, relTol)` | Chấp nhận chênh lệch theo ngưỡng của ứng dụng | Không bắc cầu, không dùng thay `equals`/`Comparator` |
| `BigDecimal.compareTo` | So sánh giá trị decimal của `BigDecimal` | `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0` |
| `BigDecimal.equals` | Bằng nhau cả giá trị và `scale` theo hợp đồng `BigDecimal` | `"2.0"` và `"2.00"` không bằng nhau theo `equals` |

Đừng chuyển từ `double` sang `BigDecimal` sau khi một phép tính đã làm mất phần thông tin nguồn rồi kỳ vọng kết quả tự trở nên chính xác. Nếu nghiệp vụ cần số thập phân chính xác (ví dụ tiền), giữ đầu vào từ chuỗi decimal hoặc biểu diễn số nguyên theo đơn vị nhỏ nhất phù hợp.

Cũng không thể suy rằng mọi phép tính `double` đều phải so sánh gần đúng. Kiểm tra sentinel rõ ràng, cờ trạng thái, số nguyên biểu diễn chính xác, hoặc các giá trị mà hợp đồng API yêu cầu chính xác có thể dùng so sánh trực tiếp. Chọn loại so sánh theo **ý nghĩa dữ liệu**.

## 8. Kiểm thử hợp lý cho phép so sánh có dung sai

Một bộ kiểm thử có giá trị nên bao gồm cả các trường hợp dễ bị bỏ qua:

1. **Sai số do biểu diễn:** `0.1 + 0.2` đối chiếu với `0.3`.
2. **Rất gần 0:** `0` so với một lượng nhỏ hơn và lớn hơn `absTol`.
3. **Thang lớn:** chênh lệch nhỏ tương đối nhưng lớn hơn `absTol`.
4. **Ngoài ngưỡng:** cặp chênh lệch lớn hơn cả hai loại dung sai.
5. **Dấu âm:** hai số âm gần nhau, hoặc số âm và dương ở gần 0; dùng `Math.abs` để kiểm tra độ lớn.
6. **Hai số 0 có dấu khác nhau:** chính sách `+0.0`/`-0.0` rõ ràng.
7. **`NaN` và vô cực:** chính sách phải thống nhất với người gọi.
8. **Dữ liệu cực lớn:** tránh phép trừ hoặc phép chia trung gian tạo `Infinity`/`NaN` bất ngờ.
9. **Dung sai không hợp lệ:** giá trị âm, `NaN`, vô cực hoặc `relTol` không thuộc miền cấu hình cho phép.
10. **Bằng đúng ngưỡng:** hiểu tác động của dấu `<` so với `<=` và sai số khi biểu diễn chính ngưỡng.

Để kiểm tra phần cốt lõi của hàm trên, có thể tự thử:

```java
// Các dòng này nằm trong một phương thức Java; ví dụ dùng hàm
// ApproximatelyEqual.isClose được định nghĩa đầy đủ ở mục 4.
boolean nearZero = ApproximatelyEqual.isClose(
        0.0, 1e-10, 1e-9, 1e-9);      // true

boolean farNearZero = ApproximatelyEqual.isClose(
        0.0, 1e-7, 1e-9, 1e-9);       // false

boolean largeClose = ApproximatelyEqual.isClose(
        1e6, 1e6 + 1e-4, 1e-9, 1e-9); // true
```

Với các quy trình khoa học cần đánh giá độ chính xác, nên lưu thêm sai số tuyệt đối, sai số tương đối (khi mẫu số có nghĩa), giá trị tham chiếu, số ULP nếu phù hợp và lý do lựa chọn ngưỡng. Một giá trị boolean `true`/`false` không cung cấp đủ thông tin để chẩn đoán chất lượng thuật toán.

## 9. Những lỗi thường gặp

### Chỉ dùng ngưỡng tuyệt đối cho mọi giá trị

```java
Math.abs(a - b) < 1e-9
```

Đây có thể là quy tắc hợp lý nếu mọi dữ liệu cùng đơn vị và quy mô đã biết. Nhưng khi phải so sánh từ các giá trị gần 0 đến hàng triệu, nó không tự điều chỉnh theo độ lớn. Có thể dùng thêm `relTol` để phản ánh sai số tỷ lệ.

### Chỉ dùng ngưỡng tương đối quanh 0

```java
Math.abs(a - b) <= relTol * Math.max(Math.abs(a), Math.abs(b))
```

Với một bên bằng 0 và bên kia khác 0, nếu `0 <= relTol < 1` thì điều kiện không thể đúng. Điều này có thể quá chặt nếu bài toán chấp nhận chênh lệch tuyệt đối rất nhỏ. Dùng thêm `absTol` khi thực tế cần.

### Chia để tính tỷ lệ mà không xét 0 và overflow

Biểu thức `Math.abs(a-b) / Math.abs(b)` gặp vấn đề với `b = 0`, và có thể tạo vô cực/`NaN` với đầu vào hoặc hiệu đặc biệt. Công thức so sánh bằng ngưỡng nhân với thang độ lớn thường tránh được phép chia trong điều kiện đơn giản, nhưng vẫn phải kiểm tra tính hữu hạn.

### Chấp nhận `NaN` hoặc vô cực ngoài ý muốn

Không dựa vào một biểu thức đại số ngắn để quyết định chính sách. Xử lý riêng `Double.isNaN`, `Double.isFinite`, và vô cực cùng dấu nếu ứng dụng muốn coi chúng bằng nhau.

### Chọn một “EPSILON” cố định vì thấy trong ví dụ

Các giá trị như `1e-6`, `1e-9`, `1e-12` không có giá trị đúng phổ quát. Hãy chọn chúng từ yêu cầu, đơn vị, độ phân giải đầu vào và sai số của thuật toán. Một `absTol` quá lớn sẽ làm hai giá trị khác biệt về nghiệp vụ bị coi như nhau; một `relTol` quá nhỏ làm kiểm thử dễ thất bại vì sai số hợp lệ.

### Dùng so sánh gần đúng cho định danh và cấu trúc dữ liệu

ID, timestamp nguyên, số thứ tự, chỉ số mảng và khóa định danh cần tiêu chí chính xác. Nếu hai ID khác nhau một đơn vị, việc dùng `relTol` có thể khiến chúng bị coi là “bằng nhau” khi độ lớn lớn, gây hỏng logic.

### Quên rằng sai số đã xảy ra trước khi so sánh

Nếu hai số nguyên lớn khác nhau đã cùng bị làm tròn thành một `double`, cả `==` và `isClose` đều có thể trả `true`; không cách so sánh nào phục hồi nguồn đã mất. Khi cần phân biệt số nguyên liên tiếp lớn, giữ chúng dưới dạng `long` hoặc `BigInteger`.

### Nhầm sự gần nhau với tính đúng của thuật toán

Một kết quả có thể nằm trong dung sai mà vẫn sai logic. Kiểm thử số học cần kiểm tra cả invariant, miền giá trị, nhánh điều kiện và so sánh với kết quả đáng tin, chứ không chỉ nới ngưỡng đến khi mọi test qua.

## 10. Bài tập tự kiểm tra

**Câu 1.** Với `absTol = 1e-8`, `relTol = 1e-6`, hai giá trị `a = 0` và `b = 5e-9` có đạt điều kiện dạng `max` không? Còn `b = 5e-7`?

**Đáp án:** `5e-9` đạt vì nhỏ hơn ngưỡng tuyệt đối `1e-8`. `5e-7` không đạt: ngưỡng tương đối chỉ `1e-6 × 5e-7 = 5e-13`, còn ngưỡng tuyệt đối là `1e-8`.

**Câu 2.** Với `absTol = 1e-8`, `relTol = 1e-6`, cặp `a = 1_000_000` và `b = 1_000_000.5` có gần nhau không?

**Đáp án:** Có. Sai lệch tuyệt đối là `0.5`, còn ngưỡng tương đối khoảng `1.0000005`, lớn hơn `0.5`.

**Câu 3.** Tại sao cần một nhánh riêng cho vô cực cùng dấu trong hàm so sánh gần đúng?

**Đáp án:** `Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY` là `true`, nhưng `Infinity - Infinity` cho `NaN`; nếu chỉ dùng `Math.abs(a-b) <= limit` thì kết quả sai với chính sách coi hai vô cực cùng dấu bằng nhau.

**Câu 4.** Có thể dùng `isClose` làm logic `equals` cho khóa `HashMap` không?

**Đáp án:** Không. Quan hệ gần bằng có thể không bắc cầu; nó không đáp ứng hợp đồng bằng nhau cần thiết để định nghĩa khóa một cách nhất quán. Dùng giá trị/đại diện chuẩn hóa và `equals`/`hashCode` tương ứng với chính sách định danh.

**Câu 5.** Khi nào nên dùng dung sai dựa vào `reference` thay vì `max(|a|, |b|)`?

**Đáp án:** Khi một bên là chuẩn có ý nghĩa độc lập và yêu cầu sai số được xác định theo tỷ lệ của giá trị chuẩn. Phải quy định cách xử lý `reference = 0`; nếu hai bên ngang vai trò, công thức đối xứng dễ giải thích hơn.

**Câu 6.** Phép so sánh gần đúng có thể chứng minh `0.1 + 0.2` được tính chính xác về mặt toán học không?

**Đáp án:** Không. Nó chỉ đánh giá chênh lệch nằm trong dung sai đặt ra. Các giá trị nhị phân vẫn đã làm tròn; để yêu cầu chữ số thập phân chính xác theo nguồn, cần biểu diễn và quy tắc tính thích hợp như `BigDecimal` từ chuỗi decimal.

**Ghi nhớ:** Sai số tuyệt đối trả lời *“lệch bao nhiêu theo đơn vị dữ liệu?”*; sai số tương đối trả lời *“lệch bao nhiêu so với quy mô?”*. Kết hợp bằng `|a-b| <= max(absTol, relTol × max(|a|,|b|))` thường phù hợp khi dữ liệu trải qua nhiều thang đo, nhưng ngưỡng và chính sách `NaN`/vô cực vẫn phải do bài toán quyết định. Phép so sánh gần đúng là quy tắc chấp nhận kết quả, không thay thế giá trị chính xác, hợp đồng `equals`, hay phân tích sai số thuật toán.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-010">Khi nào cần giới hạn theo ULP</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào cần giới hạn theo ULP khi so sánh số thực trong Java?

## 1. Vì sao một sai số tuyệt đối cố định không luôn đủ?

Phép toán **float** và **double** sử dụng số dấu phẩy động nhị phân hữu hạn độ chính xác. Kết quả trung gian và kết quả cuối có thể phải làm tròn. Vì vậy, hai phép tính về mặt toán học có cùng đáp số vẫn có thể trả về hai mẫu bit khác nhau.

Ví dụ:

~~~java
double computed = 0.1 + 0.2;
double expected = 0.3;

System.out.println(computed == expected); // false
~~~

Số **0.1**, **0.2** và **0.3** không có biểu diễn nhị phân hữu hạn chính xác trong kiểu double. Ở đây hai kết quả nằm ở **hai giá trị double liên tiếp**. Điều đó gợi ý một cách đo sai khác theo bước của chính định dạng số: **ULP**.

Tuy nhiên, việc hai giá trị cách nhau ít ULP **không tự chứng minh** kết quả đủ chính xác cho nghiệp vụ. Một ULP phụ thuộc vào độ lớn của giá trị: tại số rất lớn, khoảng cách một ULP có thể rất lớn theo đơn vị vật lý; gần zero, một ULP có thể tương ứng với tỷ lệ sai khác rất lớn.

Mục tiêu của bài là xác định **khi nào giới hạn sai số theo ULP hợp lý**, khi nào nên dùng sai số tuyệt đối/tương đối, và cách viết kiểm tra Java tránh kết luận sai.

## 2. ULP là gì?

**ULP** viết tắt của *unit in the last place*. Với một giá trị floating-point hữu hạn x, **Math.ulp(x)** trong Java trả về kích thước một ULP tại x: khoảng cách dương đến giá trị biểu diễn được kế tiếp theo hướng **tăng độ lớn**. Điều này phụ thuộc vào độ rộng kiểu (**float** hay **double**) và vị trí của x trên trục số.

Các phương thức liên quan:

- **Math.ulp(double)** trả về double; **Math.ulp(float)** trả về float.
- **Math.nextUp(x)** tìm số floating-point sát bên theo hướng dương vô cùng.
- **Math.nextDown(x)** tìm số sát bên theo hướng âm vô cùng.
- **Math.nextAfter(start, direction)** tìm số sát bên theo hướng chỉ định.
- **Double.toHexString(x)** và **Float.toHexString(x)** hiển thị significand và số mũ nhị phân để quan sát ranh giới.

Một số mốc quan trọng:

| Giá trị | ULP trong Java | Ý nghĩa |
| --- | --- | --- |
| **Math.ulp(1.0)** | **2^−52** | Bước phía trên 1 của double |
| **Math.ulp(1.0f)** | **2^−23** | Bước phía trên 1 của float |
| **Math.ulp(0.0)** | **Double.MIN_VALUE = 2^−1074** | Bước subnormal nhỏ nhất của double |
| **Math.ulp(0.0f)** | **Float.MIN_VALUE = 2^−149** | Bước subnormal nhỏ nhất của float |
| **Math.ulp(Double.MAX_VALUE)** | **2^971** | Kích thước ULP tại double hữu hạn lớn nhất |
| **Math.ulp(Double.POSITIVE_INFINITY)** | **Infinity** | Trường hợp đặc biệt; không dùng làm mức dung sai hữu hạn |
| **Math.ulp(Double.NaN)** | **NaN** | Không có khoảng cách số để so sánh |

Trong cùng một đoạn giá trị mà số mũ nhị phân không đổi (*binade*), các giá trị biểu diễn được cách nhau đều; khi chuyển sang đoạn số mũ kế tiếp, bước lưới thay đổi. Các **subnormal** gần zero có khoảng cách tuyệt đối cố định bằng **MIN_VALUE**.

### Bẫy ở đúng lũy thừa của hai

Tại **1.0 = 2^0** của double:

~~~text
Math.nextDown(1.0) = 1.0 - 2^-53
1.0                = 1.0
Math.nextUp(1.0)   = 1.0 + 2^-52

Math.ulp(1.0)      = 2^-52
~~~

Khoảng cách tới giá trị phía dưới **chỉ bằng một nửa** khoảng cách tới giá trị phía trên. Vì vậy, biểu thức **Math.abs(a - b) / Math.ulp(a)** không phải phép đếm số giá trị floating-point nằm giữa a và b trong mọi trường hợp.

Phân biệt hai ý nghĩa thường bị gọi chung là “sai số N ULP”:

1. **Ngưỡng khoảng cách số học:** chấp nhận khi |a − b| không vượt **N × Math.ulp(reference)** hoặc một ULP tại thang đo được chọn. Đây là ngưỡng độ lớn có đơn vị của a/b.
2. **Số bước biểu diễn (representable-value distance):** chấp nhận khi từ một giá trị đến giá trị kia đi qua không quá N lần **nextUp/nextDown** hợp lệ. Đây là số bước nguyên, không luôn bằng tỷ số ở mục 1.

Cần xác định hợp đồng kiểm thử muốn sử dụng nghĩa nào **trước khi chọn thuật toán**.

## 3. Khi nào nên đặt giới hạn ULP?

### Trường hợp phù hợp

Một ngưỡng theo ULP hữu ích khi:

- **Kiểm thử thuật toán số mức thấp:** bạn có giá trị tham chiếu và muốn đo xem kết quả double/float của một triển khai lệch bao nhiêu bước làm tròn so với tham chiếu, đặc biệt khi tiêu chí kiểm thử hoặc tài liệu API mô tả sai số bằng ULP.
- **So sánh kết quả của hai cách tính số học nhị phân:** cả hai tính cùng đại lượng, có cùng đơn vị, cùng độ rộng float/double và dự kiến chỉ sai nhau ít bước biểu diễn.
- **Khảo sát tính chính xác tại một vùng giá trị có độ lớn tương đương:** bạn muốn dung sai co giãn cùng độ phân giải biểu diễn, thay vì gắn cố định một **epsilon = 1e−9** trên mọi thang đo.
- **Đánh giá chất lượng phép làm tròn:** xác định kết quả có cách giá trị tham chiếu đúng hoặc có độ chính xác cao chỉ 0, 1, 2... bước biểu diễn. Phép đếm này có ý nghĩa đặc biệt khi giá trị tham chiếu đã được làm tròn về đúng kiểu đích theo một quy tắc đã biết.

**Điều kiện quan trọng:** trước hết phải biết giới hạn sai số của thuật toán hoặc hợp đồng kiểm thử. Không có giá trị N mặc định đúng cho mọi hàm. “Cho phép 4 ULP” chỉ là một quyết định có cơ sở nếu được chứng minh bởi phân tích sai số, đặc tả API, dữ liệu thực nghiệm hoặc mục tiêu kiểm thử rõ ràng.

### Trường hợp không nên chỉ dựa vào ULP

- **Nghiệp vụ có đơn vị và ngưỡng chấp nhận cụ thể:** ví dụ sai khác nhiệt độ không quá 0.01 °C, vị trí không quá 1 mm, thời gian không quá 1 µs. Ngưỡng tuyệt đối theo đơn vị thực là quan trọng hơn.
- **Sai số tương đối có ý nghĩa:** đo tỷ lệ thay đổi của đại lượng trên nhiều bậc độ lớn. Giới hạn tương đối thường biểu đạt rõ yêu cầu hơn.
- **Miền gần zero:** 0 và **Double.MIN_VALUE** cách đúng một bước biểu diễn nhưng tỷ lệ sai khác so với giá trị khác 0 là 100%. Một ngưỡng “1 ULP” có thể quá dễ dãi nếu nghiệp vụ yêu cầu giá trị phải thực sự khác 0.
- **Thuật toán mất ổn định số hoặc triệt tiêu chữ số (cancellation):** đầu ra có thể cách kết quả chính xác nhiều ULP dù sai số tuyệt đối nhỏ, hoặc ngược lại. ULP không thay cho phân tích độ điều kiện (*conditioning*) của bài toán.
- **So sánh giá trị tiền tệ/định danh/giới hạn pháp lý phải chính xác theo thập phân:** dùng mô hình tiền và quy tắc làm tròn rõ ràng (chẳng hạn BigDecimal đúng cách) thay vì mặc định so sánh double gần nhau.
- **Dùng số thực làm khóa phân vùng, khóa Map, hoặc điều kiện đồng nhất bắt buộc bắc cầu:** quan hệ “gần nhau trong N ULP” không bắc cầu. Nó không nên được nhét tùy tiện vào **equals/hashCode**.

Ví dụ: nếu x và y cách một ULP, y và z cũng cách một ULP, thì x và z có thể cách **hai ULP**. Khi ấy **close(x,y)** và **close(y,z)** có thể cùng true nhưng **close(x,z)** false.

## 4. So sánh tuyệt đối, tương đối và ULP

Ba dạng kiểm tra phục vụ ba kiểu yêu cầu:

| Cách so sánh | Quy tắc thường gặp | Phù hợp với |
| --- | --- | --- |
| **Sai số tuyệt đối** | **abs(a−b) ≤ absTol** | Có ngưỡng thực tế theo đơn vị, nhất là gần zero |
| **Sai số tương đối** | **abs(a−b) ≤ relTol × max(abs(a), abs(b))** | Độ lớn khác nhau, quan tâm tỉ lệ sai số |
| **Kết hợp tuyệt đối/tương đối** | **abs(a−b) ≤ max(absTol, relTol × scale)** | Nhiều bài kiểm thử ứng dụng trên cả giá trị nhỏ và lớn |
| **Ngưỡng ULP theo thang** | **abs(a−b) ≤ N × Math.ulp(scale)** | Dung sai cùng độ phân giải biểu diễn ở vùng scale |
| **Đếm chính xác bước ULP** | **distanceInRepresentableValues(a,b) ≤ N** | Hợp đồng cho phép tối đa N bước float/double |

Dùng **max** thay vì chỉ relative tolerance giúp tránh vấn đề so sánh gần 0 với dung sai tương đối bằng 0. Nhưng **absTol** và **relTol** vẫn phải được chọn theo yêu cầu bài toán; công thức không tự tạo ra hai ngưỡng đúng.

**Lưu ý:** viết phép trừ **a−b** trực tiếp có thể tràn thành **Infinity** với hai số double hữu hạn rất lớn và trái dấu. Với sai số ULP theo số bước, ta có thể tránh phép trừ double bằng cách đếm khoảng cách mẫu bit có thứ tự. Cũng không dùng **Math.ulp(Infinity)** làm dung sai cho các phép so sánh hữu hạn.

### Một epsilon cố định không phải ULP

Trong Java, **Math.ulp(1.0)** bằng **2^−52** (khoảng 2.22 × 10^−16), còn **Math.ulp(1.0f)** bằng **2^−23**. Không có **Double.EPSILON** được định nghĩa như một hằng chuẩn trong **java.lang.Double**. Nếu tự viết **EPSILON = 1e−9**, đó chỉ là dung sai tuyệt đối được lập trình viên lựa chọn.

Ngay cả khi tự đặt **EPSILON = Math.ulp(1.0)**, nó cũng chỉ mô tả độ phân giải gần 1.0, không phải ngưỡng thích hợp cho mọi độ lớn.

## 5. Cách dùng Math.ulp: ngưỡng theo thang đo, có giới hạn

Khi hai số a, b cùng kiểu double, hữu hạn và gần cùng một thang đo, một phép so sánh mức ULP xấp xỉ có thể là:

~~~java
// Hàm minh họa, nằm trong class chứa ứng dụng:
static boolean withinLocalUlps(double a, double b, int maxUlps) {
    if (maxUlps < 0) {
        throw new IllegalArgumentException("maxUlps must be >= 0");
    }
    if (Double.isNaN(a) || Double.isNaN(b)) {
        return false;
    }
    if (a == b) {
        return true; // cũng coi +0.0 và -0.0 bằng nhau
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }

    double scale = Math.max(Math.abs(a), Math.abs(b));
    double tolerance = maxUlps * Math.ulp(scale);
    return Math.abs(a - b) <= tolerance;
}
~~~

**Đây là ngưỡng tuyệt đối có kích thước được lấy từ ULP tại scale**, không phải phép đếm chính xác maxUlps bước biểu diễn. Nó thích hợp khi tiêu chí của bạn thực sự là ngưỡng khoảng cách số học đó.

Những điểm phải kiểm soát:

1. **maxUlps** không được âm. Nếu bạn cho phép N quá lớn, **N × Math.ulp(scale)** có thể tràn thành Infinity; nên dùng ngưỡng nhỏ, hợp lý và có kiểm chứng.
2. Cần quyết định cách xử lý **NaN** và **Infinity**: hàm trên từ chối NaN; hai infinity cùng dấu bằng nhau theo **==**, còn một infinity với số hữu hạn không được coi là gần.
3. **+0.0** và **−0.0** được coi là bằng nhau nếu không phân biệt dấu zero. Trong ứng dụng cần phân biệt biểu diễn bit, dùng quy tắc riêng.
4. Tại ranh giới lũy thừa của 2, hai bước biểu diễn ở phía số nhỏ hơn có thể có khoảng cách bằng một **Math.ulp** ở điểm ranh giới. Không gọi hàm này là “đếm đúng N ULP”.
5. Nếu a và b trái dấu, cách nhau rất xa hoặc có kiểu float nhưng đã chuyển lên double, thang đo ULP này có thể không diễn đạt đúng hợp đồng kiểm thử.

Với **float**, sử dụng overload **Math.ulp(float)** và thao tác trên float khi bạn muốn đo độ phân giải binary32. Chuyển float sang double rồi đếm ULP double sẽ tạo ra thang đo khác hẳn.

## 6. Đếm chính xác số bước biểu diễn được

Trong một chuỗi các **double hữu hạn**, ta có thể chuyển mẫu bit thành một số nguyên có thứ tự tăng theo giá trị số. Từ đó lấy hiệu thứ tự hai giá trị; **BigInteger** cho phép hiệu không tràn khi khoảng cách vượt quá miền long có dấu.

Ý tưởng đối với bit dấu:

- Mẫu **double** dương dùng thứ tự bit nguyên không dấu đồng biến với giá trị.
- Mẫu double âm có thứ tự bit đảo chiều; cần ánh xạ chúng về một chuỗi tăng.
- Cả **−0.0** và **+0.0** được ánh xạ cùng vị trí 0 để phản ánh phép so sánh giá trị; chúng khác nhau về bit nhưng không được coi là một bước khác biệt giá trị.
- Loại **NaN** và không đếm bước đến Infinity trong helper này. Infinity được xử lý theo **==** ở lớp so sánh ngoài.

Hàm minh họa chính xác về **số bước giá trị double hữu hạn**, với quy tắc nêu trên:

~~~java
import java.math.BigInteger;

// Các phương thức sau đặt bên trong cùng một class Java.
static long orderedBits(double x) {
    long bits = Double.doubleToRawLongBits(x);
    return bits < 0 ? Long.MIN_VALUE - bits : bits;
}

static BigInteger ulpSteps(double a, double b) {
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        throw new IllegalArgumentException("Only finite double values");
    }
    return BigInteger.valueOf(orderedBits(a))
            .subtract(BigInteger.valueOf(orderedBits(b)))
            .abs();
}

static boolean withinSteps(double a, double b, long maxSteps) {
    if (maxSteps < 0) {
        throw new IllegalArgumentException("maxSteps must be >= 0");
    }
    if (Double.isNaN(a) || Double.isNaN(b)) {
        return false;
    }
    if (a == b) {
        return true;
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }
    return ulpSteps(a, b)
            .compareTo(BigInteger.valueOf(maxSteps)) <= 0;
}
~~~

Phép trừ trong **orderedBits** chạy trên **long** theo quy tắc tràn bù hai của Java. Biến đổi này dùng thứ tự số nguyên có dấu sau ánh xạ; hiệu cuối được tính bằng **BigInteger** nên không làm mất trường hợp khoảng cách cực lớn. Với float, cần một phiên bản riêng dựa trên **Float.floatToRawIntBits** và độ rộng 32 bit, không nên ép nguyên xi quy tắc double.

Đây là một cách cài đặt dùng để **kiểm thử kỹ thuật**. Khi dùng trong thư viện production, nên kèm kiểm thử biên với số âm, dương, zero, subnormal, MIN_NORMAL, MAX_VALUE, NaN và vô cực; lựa chọn đếm 0 bước cho cặp zero trái dấu phải phù hợp hợp đồng của bạn.

## 7. Ví dụ Java 17+ có thể biên dịch và chạy độc lập

Tạo tệp **UlpComparisonDemo.java**:

~~~java
import java.math.BigInteger;

public class UlpComparisonDemo {
    static long orderedBits(double x) {
        long bits = Double.doubleToRawLongBits(x);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    static BigInteger ulpSteps(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("Only finite values");
        }
        return BigInteger.valueOf(orderedBits(a))
                .subtract(BigInteger.valueOf(orderedBits(b)))
                .abs();
    }

    static boolean withinSteps(double a, double b, long maxSteps) {
        if (maxSteps < 0) {
            throw new IllegalArgumentException("Invalid maxSteps");
        }
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        return ulpSteps(a, b).compareTo(BigInteger.valueOf(maxSteps)) <= 0;
    }

    static boolean withinLocalUlps(double a, double b, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Invalid count");
        }
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (a == b) {
            return true;
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        return Math.abs(a - b)
                <= count * Math.ulp(Math.max(Math.abs(a), Math.abs(b)));
    }

    public static void main(String[] args) {
        double one = 1.0;
        double up = Math.nextUp(one);
        double down = Math.nextDown(one);
        double down2 = Math.nextDown(down);

        System.out.println("ULP(1): " + Double.toHexString(Math.ulp(one)));
        System.out.println("up gap: " + Double.toHexString(up - one));
        System.out.println("down gap: " + Double.toHexString(one - down));
        System.out.println("nextUp steps: " + ulpSteps(one, up));
        System.out.println("down2 steps: " + ulpSteps(one, down2));
        System.out.println("approx 1 ULP includes down2: "
                + withinLocalUlps(one, down2, 1));
        System.out.println("exact 1 ULP includes down2: "
                + withinSteps(one, down2, 1));

        System.out.println("0 to min sub steps: "
                + ulpSteps(0.0, Double.MIN_VALUE));
        System.out.println("-min to +min steps: "
                + ulpSteps(-Double.MIN_VALUE, Double.MIN_VALUE));
        System.out.println("signed zero steps: " + ulpSteps(-0.0, +0.0));
        System.out.println("0.1 + 0.2 to 0.3 steps: "
                + ulpSteps(0.1 + 0.2, 0.3));

        System.out.println("NaN close: "
                + withinSteps(Double.NaN, Double.NaN, 1));
        System.out.println("Infinity self close: "
                + withinSteps(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0));
        System.out.println("Infinity vs max close: "
                + withinSteps(Double.POSITIVE_INFINITY, Double.MAX_VALUE, 1));
        System.out.println("float ULP(1): "
                + Float.toHexString(Math.ulp(1f)));
    }
}
~~~

Lệnh chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 UlpComparisonDemo.java
java UlpComparisonDemo
~~~

Kết quả stdout:

~~~text
ULP(1): 0x1.0p-52
up gap: 0x1.0p-52
down gap: 0x1.0p-53
nextUp steps: 1
down2 steps: 2
approx 1 ULP includes down2: true
exact 1 ULP includes down2: false
0 to min sub steps: 1
-min to +min steps: 2
signed zero steps: 0
0.1 + 0.2 to 0.3 steps: 1
NaN close: false
Infinity self close: true
Infinity vs max close: false
float ULP(1): 0x1.0p-23
~~~

Điểm đáng chú ý:

- Hai bước từ **1.0** sang **down2** có tổng khoảng cách **2^−52**, bằng **Math.ulp(1.0)**. Phép so sánh ngưỡng theo thang trả **true** nhưng phép đếm **một bước** trả **false**.
- **0.0** và **Double.MIN_VALUE** cách nhau một bước; khoảng cách gần zero quá bé về trị tuyệt đối nhưng có thể rất lớn về tỷ lệ.
- **−Double.MIN_VALUE** và **+Double.MIN_VALUE** cách nhau hai bước khi coi **±0.0** là cùng một giá trị.
- **0.1 + 0.2** khác **0.3** đúng một bước double ở môi trường Java 17+; đó là minh họa về biểu diễn, chưa phải giấy phép tự động chấp nhận mọi sai số một ULP.
- **Infinity** không được biến thành giá trị hữu hạn thông qua phép so sánh gần đúng. Hai infinity bằng nhau vẫn trả **true** theo quy tắc của helper; nếu nghiệp vụ cấm mọi infinity, hãy loại chúng trước.

## 8. Chọn ngưỡng bao nhiêu ULP?

Không có con số cố định cho mọi thuật toán. Một quy trình thực tế:

1. **Xác định giá trị tham chiếu:** có thể là kết quả tính bằng độ chính xác cao, công thức chính xác rồi làm tròn theo kiểu đích, bộ dữ liệu kiểm thử được thẩm định, hoặc hợp đồng của hàm thư viện.
2. **Xác định dạng sai số được cam kết:** “không quá N bước representable” khác “không quá N × Math.ulp(reference)”. Viết rõ định nghĩa vào kiểm thử.
3. **Chọn độ rộng đo:** nếu dữ liệu được tính và lưu dưới dạng float, đo theo ULP float; nếu là double, đo theo ULP double. Không để **binary numeric promotion** âm thầm thay đổi thang đo.
4. **Dùng N từ phân tích sai số hoặc hợp đồng:** một phép tính được làm tròn đúng có thể yêu cầu 0 ULP so với kết quả đã làm tròn tham chiếu; một hàm xấp xỉ có thể cần N khác. Số lượng thao tác và độ điều kiện ảnh hưởng tới sai số tích lũy, nên “1 ULP cho mọi phép tính” không phải quy luật.
5. **Kiểm tra ranh giới:** zero, subnormal, lũy thừa của 2, đầu mút miền normal, giá trị âm, NaN, infinity, số gần cực đại, các trường hợp cancellation.
6. **Đặt ngưỡng nghiệp vụ bổ sung nếu cần:** giới hạn ULP và giới hạn tuyệt đối/tương đối có thể **đồng thời** được yêu cầu. Không cho phép một phép đo thay thế yêu cầu còn lại.

Ví dụ, với một thuật toán tính đại lượng đo bằng mét, yêu cầu có thể là: sai lệch **không quá 2 bước double** **và** sai số tuyệt đối **không quá 10^−6 m**. Khi đó cả hai điều kiện phải được kiểm tra; một kết quả vượt ngưỡng mét sẽ bị từ chối dù khoảng cách ULP rất nhỏ.

## 9. Các tình huống thường gặp

### Tình huống A — Kiểm thử hai triển khai hàm số

Một thuật toán được đặc tả sai khác không quá **2 ULP** so với kết quả chuẩn đã làm tròn theo double. Trong trường hợp đó, **đếm bước ULP** là cách diễn đạt trực tiếp của hợp đồng. Các test cần mẫu số âm, dương, giá trị gần 0 và các ranh giới số mũ.

Lưu ý: nếu tài liệu định nghĩa ULP theo **đơn vị khoảng cách tại kết quả chính xác** thay vì số bước biểu diễn, phải triển khai đúng định nghĩa được ghi trong hợp đồng; đừng tự thay bằng phép đếm nguyên.

### Tình huống B — Kiểm thử kết quả có sai số tương đối

Một phép đo dương có độ lớn từ **10^−8** đến **10^8**, yêu cầu sai lệch tối đa **0.01%**. Điều kiện **relative tolerance** diễn đạt đúng yêu cầu hơn một N ULP bất kỳ. Có thể thêm absTol nếu miền gần zero cần ngưỡng vật lý phù hợp.

### Tình huống C — Kiểm tra hội tụ vòng lặp

Điều kiện kiểu **Math.abs(next - current) <= N * Math.ulp(current)** chỉ phát hiện các bước thay đổi nhỏ so với độ phân giải tại current. Nó **không đảm bảo** kết quả đã gần nghiệm đúng: thuật toán có thể đình trệ do làm tròn, đạo hàm nhỏ, vùng bằng phẳng hoặc lỗi số học. Nên kết hợp tiêu chí dựa vào **residual** và giới hạn số vòng lặp khi thích hợp.

### Tình huống D — Tiền và quy tắc làm tròn thập phân

So sánh hai khoản tiền với ngưỡng **0.01** đơn vị tiền tệ là bài toán theo đơn vị và quy tắc thập phân. **BigDecimal** khởi tạo từ chuỗi thập phân rõ ràng, kết hợp **setScale** và **RoundingMode** theo nghiệp vụ, thường biểu đạt chính xác hơn. Đừng dùng ULP double làm quy tắc kế toán.

### Tình huống E — Hai biểu thức tính ra hai số sát nhau

**0.1 + 0.2** và **0.3** cách nhau một double step. Nếu đây là kiểm thử đơn thuần xác nhận một triển khai số cho kết quả trong sai số làm tròn đã dự kiến, giới hạn một bước có thể được biện minh. Nếu giá trị này là điều kiện giao dịch hoặc dữ liệu cần khớp tuyệt đối ở cơ số 10, hãy dùng biểu diễn và hợp đồng phù hợp thay vì mặc nhiên chấp nhận.

## 10. NaN, Infinity, signed zero: phải quyết định trước

Các giá trị đặc biệt không tuân theo mọi quy tắc so sánh hữu hạn.

| Trường hợp | Hành vi ngôn ngữ Java | Chính sách thường dùng trong helper kiểm thử |
| --- | --- | --- |
| **NaN** | **Double.NaN == Double.NaN** là **false** | Trả **false**; nếu test yêu cầu NaN, kiểm tra **Double.isNaN** riêng |
| **+Infinity** so với **+Infinity** | **== true** | Có thể cho **true** nếu kết quả vô cực là hợp lệ |
| **+Infinity** so với số hữu hạn | **== false** | Không tính khoảng cách N ULP đến infinity |
| **+0.0** so với **−0.0** | **== true** | Thường cùng giá trị, song có thể cần kiểm tra bit dấu |
| **0.0** so với **Double.MIN_VALUE** | Giá trị khác nhau | Chỉ cách **một bước**; vẫn phải xem xét yêu cầu near-zero |

Nếu dấu của zero quan trọng, ví dụ **1.0 / +0.0** cho **+Infinity** và **1.0 / −0.0** cho **−Infinity**, hãy dùng:

~~~java
boolean sameBits = Double.doubleToRawLongBits(a)
        == Double.doubleToRawLongBits(b);
~~~

Đoạn trên minh họa đặt trong một method đã định nghĩa biến **a** và **b**; nó **không phải** chương trình Java độc lập. Phép so sánh bit chính xác còn phân biệt payload NaN; khi yêu cầu chỉ cần nhận diện NaN, dùng **Double.isNaN** phù hợp hơn.

Đối với **Double.compare(a,b)**, Java sắp xếp **−0.0** và **+0.0** khác nhau, trong khi toán tử **==** coi chúng bằng nhau. Chọn đúng phép so sánh cho mục đích của bạn.

## 11. Tại sao ULP vẫn không đủ để đánh giá độ đúng?

ULP đo độ khác biệt **theo độ phân giải định dạng đích**. Nó không nói bản thân thuật toán có tính **ổn định số**, cũng không phản ánh toàn bộ ràng buộc của bài toán.

### Gần zero và cancellation

Giả sử hai số gần bằng nhau bị trừ đi. Kết quả có thể rất nhỏ về trị tuyệt đối, nhưng sai lệch tương đối lớn nếu các toán hạng đã mang lỗi làm tròn từ trước. Nếu dùng ULP của **kết quả nhỏ** làm chuẩn, chỉ một sai lệch tuyệt đối nhỏ cũng có thể tương ứng với rất nhiều ULP. Nếu dùng ULP của **toán hạng lớn**, bạn có thể cho phép sai số quá rộng. Cần biết **đầu ra nào** có ý nghĩa và thang nào được phép đo.

### Ở độ lớn rất lớn

Ở gần **Double.MAX_VALUE**, một ULP bằng **2^971** — con số khổng lồ theo đơn vị tuyệt đối. Hai kết quả sát nhau trên lưới double vẫn có thể sai khác quá lớn đối với ứng dụng có giới hạn chặt bằng mét, giây hoặc tiền tệ.

### Sai số thuật toán tích lũy

Phép cộng lặp đi lặp lại, các phép biến đổi kém điều kiện và thay đổi thứ tự phép tính có thể tích lũy sai số. Số ULP của một phép tính đơn không tự trở thành giới hạn ULP cho cả thuật toán dài. Nếu thuật toán chứa các phép toán transcendental, hãy đọc tiêu chuẩn độ chính xác của **API được gọi** thay vì đoán.

**Nguyên tắc:** dùng ULP khi đó là đơn vị sai số được định nghĩa, có thể giải thích và kiểm chứng; dùng đơn vị nghiệp vụ khi điều kiện nghiệm thu nằm ở thế giới thực.

## 12. Các lỗi phổ biến cần tránh

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Dùng **1e−9** cho mọi số | Dung sai quá rộng gần zero hoặc quá chặt ở độ lớn khác | Chọn absTol/relTol theo bài toán, hoặc thang ULP có lý do |
| Coi **N × Math.ulp(x)** là đúng N bước trong mọi trường hợp | Sai ở ranh giới lũy thừa của 2 | Phân biệt ngưỡng số học và đếm bước |
| Dùng ULP double cho kết quả thực chất là float | Ngưỡng so sánh thay đổi theo kiểu | Kiểm tra độ rộng phép tính/lưu trữ |
| Chọn N = 4, 8 hoặc 16 theo thói quen | Che giấu lỗi mà không có căn cứ | Xuất phát từ hợp đồng/phân tích lỗi |
| Dùng **Math.ulp(Infinity)** như epsilon | Infinity không phải dung sai hữu hạn | Phân loại NaN/Infinity trước |
| Thực hiện trừ double cực đại trái dấu rồi coi kết quả là khoảng cách số học hữu hạn | Phép trừ có thể tràn Infinity | Dùng hàm đếm bước chính xác hoặc xử lý miền riêng |
| Cho rằng **0.0** và **MIN_VALUE** gần nhau nên luôn chấp nhận | Sai khác tương đối có thể rất lớn | Thêm ngưỡng near-zero hoặc kiểm tra kết quả phải khác 0 |
| Đổi điều kiện hội tụ thành “khác tối đa 1 ULP” | Có thể chỉ xác nhận thuật toán đình trệ | Kiểm tra residual, mục tiêu nghiệm và ngưỡng nghiệp vụ |
| Dùng so sánh gần đúng làm **equals/hashCode** | Quan hệ gần đúng không bắc cầu | Dùng biểu diễn/khóa chính xác hoặc lượng tử hóa theo hợp đồng |
| Bỏ qua **−0.0**, **NaN**, **Infinity** | Kết quả kiểm thử khó hiểu hoặc sai | Định nghĩa chính sách từng trường hợp |

## 13. Bài tập kiểm tra

**Bài 1.** Tại **1.0**, vì sao **Math.nextUp(1.0) − 1.0** khác **1.0 − Math.nextDown(1.0)**?

*Đáp án:* 1.0 nằm đúng ranh giới hai đoạn số mũ nhị phân; bước phía trên là **2^−52**, phía dưới là **2^−53**. **Math.ulp(1.0)** bằng bước theo hướng tăng độ lớn, tức **2^−52**.

**Bài 2.** Nếu giới hạn cho phép **1 ULP** hiểu là “không quá 1 bước double”, **1.0** và **Math.nextDown(Math.nextDown(1.0))** có gần nhau không?

*Đáp án:* **Không**. Hai số cách nhau **hai** giá trị kế tiếp, dù sai khác tuyệt đối của chúng bằng **Math.ulp(1.0)**.

**Bài 3.** **0.0** và **Double.MIN_VALUE** cách bao nhiêu bước double hữu hạn? Có nên chấp nhận chúng là bằng nhau về nghiệp vụ?

*Đáp án:* **Một bước**. Việc có chấp nhận hay không phải dựa vào yêu cầu gần zero; một bước ULP không chứng minh tỷ lệ sai khác nhỏ.

**Bài 4.** Vì sao không dùng **Math.ulp(1.0)** làm epsilon chung cho mọi kết quả double?

*Đáp án:* Kích thước ULP thay đổi cùng độ lớn số. ULP tại 1.0 không đại diện cho độ phân giải ở 1e−100 hay 1e100.

**Bài 5.** Một thuật toán xử lý khoảng cách địa lý được yêu cầu sai khác tối đa **0.5 m**. Nên lấy tiêu chí nào làm điều kiện nghiệm thu chính?

*Đáp án:* Dùng sai số tuyệt đối theo mét. ULP có thể là công cụ kiểm thử chi tiết thêm khi có căn cứ, nhưng không thay thế yêu cầu 0.5 m.

**Bài 6.** **NaN** xuất hiện trong cả expected và actual. **withinSteps(NaN, NaN, 0)** theo ví dụ trả gì, và làm sao kiểm thử kết quả phải là NaN?

*Đáp án:* **false**. Kiểm tra **Double.isNaN(actual)** riêng khi NaN là kết quả mong đợi.

## 14. Ghi nhớ để lựa chọn đúng

Khi thiết kế phép so sánh số thực, hãy trả lời theo trình tự: **(1) Đại lượng nào đang được so sánh? (2) Có giới hạn bằng đơn vị vật lý/kinh doanh không? (3) Có tiêu chí sai số tương đối không? (4) Nếu muốn giới hạn ULP, đó là ngưỡng khoảng cách số học hay số bước giá trị biểu diễn được? (5) Tham chiếu và N lấy từ đâu? (6) Các giá trị biên sẽ được xử lý thế nào?**

Giới hạn theo ULP phát huy giá trị nhất ở **kiểm thử độ chính xác của phép toán/thuật toán dấu phẩy động cùng kiểu** khi sai số được định nghĩa bằng đơn vị lưới biểu diễn. Nó cần được dùng cùng hiểu biết về rounding, đặc tính thuật toán và các ngưỡng nghiệp vụ.

## 15. Tài liệu tham khảo

- [Java SE 17 Math API — ulp, nextUp, nextDown, nextAfter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html).
- [Java SE 17 Double API — doubleToRawLongBits, compare, isFinite](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html).
- [Java SE 17 Float API — floatToRawIntBits, MIN_VALUE](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html).
- [Java Language Specification 17 — §4.2.3 Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3).
- [Java Language Specification 17 — §15.21 Equality Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-comparison-011">Không dùng so sánh gần bằng tùy tiện làm quan hệ equals hoặc thứ tự sắp xếp</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng so sánh gần bằng tùy tiện làm quan hệ equals hoặc thứ tự sắp xếp

## 1. Vấn đề cốt lõi

Khi dùng `float` hoặc `double` trong Java, người học thường được khuyên tránh `a == b` sau phép tính có sai số làm tròn. Lời khuyên ấy có lý trong **kiểm tra gần bằng theo yêu cầu đo lường**, nhưng có một ranh giới cực kỳ quan trọng:

> **“Hai số đủ gần” không tự động là quan hệ bằng nhau hợp lệ để triển khai `equals`, `hashCode`, `Comparator` hoặc khóa của `HashMap`/`TreeMap`.**

Ví dụ quy tắc “coi bằng nhau nếu chênh lệch không vượt 0,1”:

```java
static boolean close(double a, double b) {
    return Math.abs(a - b) <= 0.1;
}
```

Xét ba giá trị:

```text
a = 0.00
b = 0.09
c = 0.18

close(a, b) = true
close(b, c) = true
close(a, c) = false
```

Đây là trường hợp **không bắc cầu**: `a` gần `b`, `b` gần `c`, nhưng `a` không gần `c`. Nếu ta cho `close` thay thế `equals` hoặc trả `0` trong `Comparator.compare`, các cấu trúc dữ liệu vốn cần một khái niệm bằng nhau/thứ tự **nhất quán** có thể hoạt động trái trực giác.

Bài này thuộc **Java Core — Numbers / So sánh số thực**. Mục tiêu là:

1. Biết khi nào dùng so sánh số thực **chính xác về giá trị**, khi nào dùng **gần bằng** với dung sai.
2. Hiểu yêu cầu **phản xạ, đối xứng, bắc cầu** của `equals` và mối liên hệ với `hashCode`.
3. Hiểu hợp đồng `Comparator`, đặc biệt ràng buộc khi `compare(a,b) == 0` và tính bắc cầu.
4. Giải thích vì sao “epsilon comparator” không an toàn cho `TreeSet`, `TreeMap` và các thuật toán sắp xếp.
5. Sử dụng `Double.compare` hoặc một **khóa đại diện được chuẩn hóa/định lượng hóa** có quy tắc rõ ràng.
6. Thiết kế riêng phép kiểm tra gần bằng cho thuật toán số, có xét NaN, infinity, signed zero, sai số tuyệt đối/tương đối và dữ liệu biên.

Ví dụ chạy được với **Java 17+**; không cần thư viện ngoài JDK.

## 2. Cần phân biệt bốn câu hỏi khác nhau

Các câu hỏi về hai giá trị `double` không nhất thiết có cùng đáp án:

| Câu hỏi | Công cụ gợi ý | Mục đích |
| --- | --- | --- |
| Hai giá trị bằng theo phép so sánh floating-point của Java không? | `a == b` | Logic toán tử nguyên thủy |
| Hai giá trị gần nhau theo sai số của bài toán không? | `isClose(a,b,absTol,relTol)` | Kiểm định sai số phép đo/kết quả tính |
| Hai đối tượng là cùng một khóa theo hợp đồng ứng dụng không? | `equals` và `hashCode` nhất quán | `HashSet`, `HashMap`, mô hình dữ liệu |
| Hai phần tử có thứ tự nào để sắp xếp/lưu trong cây? | `Comparator` hợp lệ; thường `Double.compare` | `sort`, `TreeSet`, `TreeMap` |

So sánh gần bằng là một **truy vấn** về khoảng cách, không phải mặc nhiên là **định nghĩa danh tính** của một phần tử.

Không nên hiểu “`==` nguy hiểm với `double`” thành “cứ thay mọi phép bằng bằng epsilon”. `==` chính xác có thể hoàn toàn đúng khi so sánh sentinel, kiểm tra zero theo giá trị, hay khi hợp đồng yêu cầu **đúng giá trị dấu phẩy động**. Điều quan trọng là chọn phép so sánh phù hợp với ý nghĩa dữ liệu.

## 3. Quan hệ tương đương mà `equals` phải tuân thủ

Theo hợp đồng của `Object.equals`, với các đối tượng khác `null`, một quan hệ bằng nhau hợp lệ phải có ít nhất những tính chất sau:

- **Phản xạ (reflexive):** `x.equals(x)` phải là `true`.
- **Đối xứng (symmetric):** nếu `x.equals(y)` thì `y.equals(x)`.
- **Bắc cầu (transitive):** nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)`.
- **Nhất quán (consistent):** nhiều lần gọi không đổi kết quả nếu những thông tin được dùng để so sánh không đổi.
- `x.equals(null)` phải là `false`.

Ngoài ra, nếu `x.equals(y)` là `true` thì **`x.hashCode() == y.hashCode()` bắt buộc đúng**. Hai đối tượng không bằng nhau vẫn có thể trùng hash, nhưng hai đối tượng bằng nhau không được khác hash.

### 3.1. Vì sao epsilon phá vỡ tính bắc cầu?

Đặt `ε = 0.1` và định nghĩa:

```text
a ≈ b  khi  |a - b| <= ε
```

Chọn `a = 0.00`, `b = 0.09` và `c = 0.18`.

| Cặp | Khoảng cách | Có “gần bằng” không? |
| --- | ---: | --- |
| `a,b` | 0,09 | Có |
| `b,c` | 0,09 | Có |
| `a,c` | 0,18 | Không |

Hợp đồng `equals` sẽ yêu cầu nếu `a` bằng `b` và `b` bằng `c` thì `a` phải bằng `c`. Nhưng kiểm tra dung sai ở đây nói ngược lại.

Một dung sai **dương cố định** trên một miền số liên tục thường có hiện tượng “bắc cầu qua các bước gần nhau”. Đổi từ `<=` sang `<` hoặc đổi epsilon nhỏ hơn không biến nó thành quan hệ tương đương tổng quát.

### 3.2. NaN có thể phá cả phản xạ nếu viết sai

Giả sử:

```java
boolean near = Math.abs(Double.NaN - Double.NaN) <= 1e-9;
```

`near` là `false` do mọi so sánh thứ tự `<=` với NaN trả false. Nếu `equals` của một đối tượng dùng chính phép đó để so sánh field và **không có nhánh identity phù hợp**, một đối tượng chứa NaN thậm chí có thể không bằng chính nó. Nhưng thêm `if (this == other) return true;` chỉ có thể chữa **một nhánh phản xạ**, không sửa tính bắc cầu của quan hệ epsilon.

Tự đặt epsilon vào `equals` không phải “nâng cấp” toàn diện phép so sánh số thực.

## 4. `hashCode` không cứu được quan hệ gần bằng không bắc cầu

Một lỗi thường thấy:

```java
// MÃ Ý TƯỞNG SAI, KHÔNG NÊN DÙNG TRONG equals:
return Math.abs(this.value - other.value) <= EPSILON;
```

rồi triển khai `hashCode` bằng `Double.hashCode(value)`. Khi `this.value` gần `other.value` nhưng bit/giá trị khác nhau, hai object có thể bị `equals` coi là bằng, trong khi hash của chúng khác nhau. Điều này **vi phạm trực tiếp** hợp đồng giữa `equals` và `hashCode`.

Có người sửa bằng cách cho mọi object `hashCode()` cố định bằng `1`. Cách đó tránh yêu cầu hash khác nhau, nhưng:

1. Không khôi phục tính bắc cầu của `equals`.
2. Có thể làm `HashMap`/`HashSet` suy giảm hiệu năng vì quá nhiều va chạm.
3. Khi dữ liệu bằng nhau theo từng cặp nhưng không bắc cầu, thao tác tìm/xóa/phân nhóm vẫn khó định nghĩa một cách nhất quán.

**Kết luận:** một quan hệ gần bằng không bắc cầu không thể trở thành `equals` hợp lệ chỉ nhờ chọn một công thức `hashCode` khéo léo.

## 5. `Comparator` cần một quan hệ thứ tự nhất quán

Java định nghĩa `Comparator<T>` qua phương thức:

```java
int compare(T a, T b);
```

Ý nghĩa dấu kết quả:

- Nhỏ hơn `0`: `a` đứng trước `b`.
- Bằng `0`: comparator coi `a` và `b` **tương đương về thứ tự**.
- Lớn hơn `0`: `a` đứng sau `b`.

Hợp đồng comparator yêu cầu (trình bày theo ý nghĩa toán học):

1. **Đổi hai đối số thì dấu đổi:** dấu của `compare(a,b)` phải đối dấu với `compare(b,a)`, với yêu cầu ngoại lệ phù hợp.
2. **Bắc cầu:** nếu `a > b` và `b > c` theo comparator thì `a > c`.
3. **Nhất quán của lớp so sánh bằng 0:** nếu `compare(a,b) == 0` thì dấu của `compare(a,z)` và `compare(b,z)` phải giống nhau với mọi `z`.

Điều (3) có nghĩa comparator định nghĩa một **quan hệ tương đương dựa trên kết quả so sánh bằng 0**. Nếu dùng “chênh lệch dưới epsilon” để trả 0, điều (3) dễ bị phá.

### 5.1. Comparator sai điển hình

```java
static final double EPSILON = 0.1;

static int badCompare(double a, double b) {
    if (Math.abs(a - b) <= EPSILON) {
        return 0;
    }
    return Double.compare(a, b);
}
```

Đoạn này có vẻ hợp lý vì phân biệt số rất khác nhau, còn số gần thì coi bằng. Nhưng với `a=0.00`, `b=0.09`, `c=0.18`:

```text
badCompare(a, b) = 0
badCompare(b, c) = 0
badCompare(a, c) = -1
```

Comparator vừa nói `a` tương đương `b`, `b` tương đương `c`, nhưng lại nói `a` đứng trước `c`. Đó là **vi phạm hợp đồng**, không chỉ là “độ chính xác chưa tốt”.

**Cảnh báo:** không nên viết `return (int) (a - b);` để so sánh `double`. Phép trừ có thể làm tròn, sinh vô cực hoặc NaN; cast sang `int` sẽ làm mất phần thập phân, làm nhiều cặp khác nhau trả 0, thậm chí tràn/saturate. Với `int`/`long`, phép trừ để so sánh cũng có thể tràn. Hãy dùng comparator có hợp đồng rõ ràng.

## 6. Ví dụ chạy được: cùng dữ liệu, `TreeSet` cho kết quả phụ thuộc thứ tự thêm

Lưu tệp **`BadEpsilonComparator.java`**:

```java
import java.util.Comparator;
import java.util.TreeSet;

public class BadEpsilonComparator {
    private static final double EPSILON = 0.1;

    public static void main(String[] args) {
        double a = 0.00;
        double b = 0.09;
        double c = 0.18;

        System.out.println(near(a, b));
        System.out.println(near(b, c));
        System.out.println(near(a, c));

        Comparator<Double> bad = (x, y) ->
                near(x, y) ? 0 : Double.compare(x, y);

        System.out.println(bad.compare(a, b));
        System.out.println(bad.compare(b, c));
        System.out.println(bad.compare(a, c));

        TreeSet<Double> first = new TreeSet<>(bad);
        first.add(a);
        first.add(b);
        first.add(c);

        TreeSet<Double> second = new TreeSet<>(bad);
        second.add(b);
        second.add(a);
        second.add(c);

        System.out.println(first.size());
        System.out.println(second.size());
    }

    static boolean near(double x, double y) {
        return Math.abs(x - y) <= EPSILON;
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac --release 17 BadEpsilonComparator.java
java BadEpsilonComparator
```

**Stdout với JDK Java chuẩn:**

```text
true
true
false
0
0
-1
2
1
```

Diễn giải:

- Khi thêm `a` trước: `b` bị xem như cùng phần tử với `a`, còn `c` khác `a` nên tập có `2` phần tử.
- Khi thêm `b` trước: cả `a` và `c` đều được comparator coi là cùng phần tử với `b`, nên tập chỉ có `1` phần tử.
- Dữ liệu đầu vào là cùng bộ ba, nhưng ý niệm “loại trùng” phụ thuộc thứ tự thêm. Đây là một phản ví dụ đủ để loại bỏ comparator dạng epsilon.

`TreeSet`/`TreeMap` dùng `compare(a,b) == 0` để quyết định hai khóa có cùng vị trí trong cây, **không dùng `equals` trong bước này**. Các thuật toán `sort` cũng giả định comparator tuân thủ hợp đồng; comparator sai có thể gây thứ tự bất hợp lý hoặc `IllegalArgumentException` về vi phạm hợp đồng trong một số trường hợp, nhưng không bảo đảm lúc nào cũng ném lỗi.

**Không triển khai comparator sai rồi “kiểm thử thấy không crash” để kết luận an toàn.** Hợp đồng phải đúng với toàn bộ miền dữ liệu cho phép.

## 7. Dùng `Double.compare` để tạo thứ tự ổn định

Với các khóa dựa trên giá trị `double`, lựa chọn mặc định phù hợp thường là:

```java
Comparator<Double> byNumericValue = Double::compare;
```

Hoặc với đối tượng có thuộc tính số:

```java
Comparator<Reading> byValue =
        Comparator.comparingDouble(Reading::value);
```

Ở đây `Reading` là một kiểu tự định nghĩa có phương thức `value()` trả `double`; đoạn thứ hai là **mảnh minh họa**, không phải chương trình hoàn chỉnh khi chưa khai báo `Reading`.

`Double.compare` tạo thứ tự được xác định cho cả các giá trị đặc biệt:

- `Double.compare(-0.0, +0.0) < 0`: **zero âm đứng trước zero dương**.
- `Double.compare(Double.NaN, Double.NaN) == 0`: NaN được coi tương đương trong thứ tự của `Double`.
- NaN được xếp **lớn hơn các giá trị số khác**, kể cả `Double.POSITIVE_INFINITY`.
- Các số hữu hạn thông thường và ±Infinity được sắp xếp theo thứ tự số tương ứng.

Điều này khác với toán tử `==` primitive:

```java
System.out.println(-0.0 == +0.0); // true
System.out.println(Double.compare(-0.0, +0.0)); // -1

System.out.println(Double.NaN == Double.NaN); // false
System.out.println(Double.compare(Double.NaN, Double.NaN)); // 0
```

**Không dùng `x < y ? -1 : x > y ? 1 : 0` làm comparator tổng quát cho `double`**: với NaN cả `<` và `>` đều false, nên có thể trả 0 cho NaN và bất kỳ số nào; với ±0, nó cũng xem bằng nhau. Nếu muốn một thứ tự khác `Double.compare`, phải thiết kế **toàn bộ** quy tắc (bao gồm NaN, ±Infinity, ±0) sao cho vẫn bắc cầu và nhất quán.

### 7.1. Thứ tự ổn định không đồng nghĩa dữ liệu đo “giống hệt”

`Double.compare(a,b) == 0` là một tiêu chí **thứ tự theo giá trị IEEE 754** hữu ích cho dữ liệu dùng làm khóa; không phải phép kiểm định hai kết quả đo có nằm trong sai số cho phép hay không. Vì vậy, vẫn có thể dùng `isClose(a,b)` để hiển thị cảnh báo “hai cảm biến cho kết quả tương tự”, đồng thời dùng `Double.compare` cho việc sắp xếp danh sách theo kết quả.

## 8. `Double.equals`, `hashCode`, signed zero và NaN

`Double.equals` có hợp đồng khác primitive `==`:

| Trường hợp | Primitive `==` | `Double.equals` |
| --- | --- | --- |
| `+0.0` và `-0.0` | `true` | `false` |
| `Double.NaN` và `Double.NaN` | `false` | `true` |
| Hai giá trị hữu hạn giống nhau về biểu diễn giá trị | `true` | `true` |

`Double.equals` so sánh theo mã hóa bit do `Double.doubleToLongBits` trả về; phương thức đó chuẩn hóa các dạng NaN về một mẫu canonical nhưng **giữ sự khác biệt ±0**. `Double.hashCode` nhất quán với hợp đồng này.

Do đó, `HashSet<Double>` có thể giữ hai khóa `+0.0` và `-0.0` riêng biệt, trong khi nhận thêm một NaN canonical-equivalent như đã có thì không tạo phần tử mới.

### Ví dụ chạy được: so sánh bằng nhau ở ba tầng

Lưu **`FloatingIdentity.java`**:

```java
import java.util.HashSet;
import java.util.Set;

public class FloatingIdentity {
    public static void main(String[] args) {
        System.out.println(+0.0 == -0.0);
        System.out.println(Double.valueOf(+0.0)
                .equals(Double.valueOf(-0.0)));

        System.out.println(Double.NaN == Double.NaN);
        System.out.println(Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN)));

        System.out.println(Double.compare(-0.0, +0.0));
        System.out.println(Double.compare(Double.NaN,
                Double.POSITIVE_INFINITY));

        Set<Double> values = new HashSet<>();
        values.add(+0.0);
        values.add(-0.0);
        values.add(Double.NaN);
        values.add(Double.NaN);
        System.out.println(values.size());
    }
}
```

Chạy:

```bash
javac --release 17 FloatingIdentity.java
java FloatingIdentity
```

**Stdout:**

```text
true
false
false
true
-1
1
3
```

Hai giá trị zero khác nhau theo wrapper equality; hai NaN được canonicalize cho `equals`. Điều này phù hợp cho các collection Java, không nhằm khẳng định NaN có quan hệ bằng nhau theo toán học thông thường.

### 8.1. Nếu tự viết kiểu dữ liệu có field `double`

Một lựa chọn đúng khi **danh tính chính là bit-value theo `Double`** là viết `equals`/`hashCode` tương thích:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Measurement other)) return false;
    return Double.doubleToLongBits(this.value)
            == Double.doubleToLongBits(other.value);
}

@Override
public int hashCode() {
    return Double.hashCode(value);
}
```

Đây là **hai phương thức minh họa bên trong class `Measurement`** có field `private final double value`, không phải mã Java độc lập. Dùng `Double.doubleToLongBits` thay vì raw bits nếu muốn tất cả NaN patterns có cùng quy tắc `equals`/`hashCode` như wrapper. Dùng `Double.compare` trong comparator để tương thích với thứ tự của wrapper.

Nếu danh tính đối tượng là `id` ổn định, có thể định nghĩa `equals` bằng `id` và chỉ dùng số đo để lọc/gần bằng. Tránh để field floating-point **có thể thay đổi** quyết định `hashCode` sau khi đối tượng đã được đưa vào `HashSet`/`HashMap`.

## 9. Khi nào **gần bằng** thực sự phù hợp?

Một phép kiểm tra dung sai rất hữu ích khi bạn quan tâm đến **độ lớn chênh lệch theo nghĩa vật lý hoặc số học**, ví dụ:

- Hai phép đo nhiệt độ có chênh lệch nằm trong sai số cảm biến.
- So sánh kết quả thuật toán số với giá trị kỳ vọng có dung sai đã xác định.
- Phát hiện đầu ra mô phỏng khác nhau nhưng vẫn trong ngưỡng chấp nhận.
- Xác định sự hội tụ theo tiêu chuẩn dựa trên sai số, kèm ngưỡng và tiêu chí dừng được thiết kế phù hợp.

Nhưng câu trả lời `isClose(a,b)` nên là **một boolean của truy vấn cụ thể**, không dùng mặc định để quyết định object nào là “cùng khóa” trong hash/tree collection.

### 9.1. Sai số tuyệt đối (absolute tolerance)

Một phép kiểm tra đơn giản:

```java
boolean near = Math.abs(actual - expected) <= absoluteTolerance;
```

Nếu absolute tolerance có đơn vị đo (ví dụ độ C, mét, giây), hãy đặt giá trị theo sai số mà nghiệp vụ chấp nhận; không dùng một con số “ε thần kỳ” cho mọi tình huống.

Ví dụ `|1.00001 - 1.00002| <= 0.0001` hợp lý khi chỉ cần sai số nhỏ hơn `0.0001` đơn vị; nhưng cùng epsilon đó có thể quá nhỏ hoặc quá lớn ở thang số khác.

### 9.2. Sai số tương đối (relative tolerance)

Khi thang độ lớn thay đổi, ta có thể dùng:

```text
|a - b| <= relTol × max(|a|, |b|)
```

Sai số tương đối chỉ có ý nghĩa khi thang đo khác 0. Gần zero, một tolerance tương đối thuần túy trở nên khó sử dụng; thường cần một phần **absolute tolerance** để ổn định.

Ví dụ kết hợp:

```text
|a - b| <= max(absTol, relTol × max(|a|, |b|))
```

Đây là **quy tắc nghiệp vụ được chọn**, không phải định nghĩa equality của ngôn ngữ. Nó vẫn **không có tính bắc cầu tổng quát**.

### 9.3. Cần phân loại NaN/Infinity và kiểm soát input

`Math.abs(a - b) <= eps` có thể trả false cho `a == b == Double.POSITIVE_INFINITY` vì `+∞ - +∞` là NaN. Với NaN, biểu thức `<=` trả false. Nếu một hệ thống muốn “cùng vô cực là cùng trạng thái”, quy tắc đó phải được viết rõ, không tự suy từ hiệu số.

Nếu comparator dùng `near` để trả 0 khi gặp NaN, điều kiện false có thể đẩy chương trình tới nhánh `Double.compare` nhưng quan hệ trộn vẫn không được bảo đảm bắc cầu. Với epsilon âm hoặc NaN, hành vi càng mơ hồ; phải **validate tolerance** ngay từ đầu.

## 10. Ví dụ Java 17+ cho truy vấn gần bằng an toàn theo phạm vi sử dụng

Lưu thành **`NearEnough.java`**:

```java
public class NearEnough {
    static boolean near(double a, double b,
                        double absTol, double relTol) {
        if (!Double.isFinite(absTol) || !Double.isFinite(relTol)
                || absTol < 0 || relTol < 0) {
            throw new IllegalArgumentException("Invalid tolerance");
        }

        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false; // Chính sách: chỉ so sánh gần bằng số hữu hạn
        }

        double diff = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return diff <= Math.max(absTol, relTol * scale);
    }

    public static void main(String[] args) {
        System.out.println(near(0.0, 5e-13, 1e-12, 1e-8));
        System.out.println(near(0.0, 2e-12, 1e-12, 1e-8));
        System.out.println(near(1_000_000.0, 1_000_000.005,
                1e-12, 1e-8));
        System.out.println(near(Double.NaN, Double.NaN,
                1e-12, 1e-8));
        System.out.println(near(-0.0, +0.0,
                1e-12, 1e-8));
    }
}
```

Chạy:

```bash
javac --release 17 NearEnough.java
java NearEnough
```

**Stdout dự kiến:**

```text
true
false
true
false
true
```

Mục tiêu của hàm là **truy vấn “chênh lệch nằm trong mức cho phép” trên dữ liệu hữu hạn**. Nó **không** được dùng trực tiếp cho `equals` hay `Comparator`.

Cần hiểu giới hạn của chính hàm mẫu:

- Nếu `a-b` tràn thành `Infinity` dù hai input hữu hạn, kiểm tra này sẽ trả false. Điều đó phù hợp với nhiều chính sách kiểm tra gần bằng với tolerance nhỏ, nhưng cần phân tích nếu miền dữ liệu rất rộng.
- Tích `relTol * scale` có thể tràn với tolerance lớn; nên ràng buộc `relTol` và miền input theo ứng dụng. Hàm xác nhận tolerance hữu hạn và không âm, nhưng không thay thế đánh giá điều kiện số học ở mọi miền IEEE 754.
- Giá trị `-0.0` và `+0.0` được coi gần bằng; đúng nếu ứng dụng chỉ quan tâm độ chênh lệch, **không** đúng nếu cần giữ signed zero.
- Tolerance có thể không bắc cầu bất kể bạn kết hợp ngưỡng tuyệt đối và tương đối thế nào.

## 11. Nếu cần gom nhóm: chọn một quy tắc **khóa** ổn định, không dùng epsilon làm equals

Đôi khi yêu cầu thực sự là: “mọi số thuộc cùng một khoảng nên được nhóm chung”. Đây là bài toán khác với “hai số gần nhau không quá epsilon”.

Có thể định nghĩa một hàm ánh xạ **mỗi giá trị vào một khóa xác định**, rồi áp dụng equality/order lên khóa:

```text
bucket(x) = floor((x - origin) / width)
```

Khi đó:

```text
sameBucket(a, b) = bucket(a) == bucket(b)
```

Do mỗi giá trị có **một khóa duy nhất**, quan hệ `sameBucket` có tính phản xạ, đối xứng và bắc cầu, giả sử đầu vào và quy tắc bucket được kiểm soát nhất quán. Nó khác gần bằng theo khoảng cách: hai số rất gần nhau nhưng nằm hai phía ranh giới bucket **có thể** bị xếp hai nhóm khác nhau. Đây là lựa chọn có chủ đích để lấy **tính ổn định của quan hệ khóa**.

### 11.1. Ví dụ bucket cho miền dữ liệu bị ràng buộc

Lưu **`BucketGrouping.java`**:

```java
public class BucketGrouping {
    static long bucket(double value) {
        if (!Double.isFinite(value)
                || value < -1_000_000.0
                || value > 1_000_000.0) {
            throw new IllegalArgumentException("Out of range");
        }
        return (long) Math.floor(value / 0.5);
    }

    public static void main(String[] args) {
        System.out.println(bucket(0.1));
        System.out.println(bucket(0.4));
        System.out.println(bucket(0.51));
        System.out.println(bucket(-0.1));

        System.out.println(bucket(0.1) == bucket(0.4));
        System.out.println(bucket(0.49) == bucket(0.51));
    }
}
```

Chạy:

```bash
javac --release 17 BucketGrouping.java
java BucketGrouping
```

**Stdout:**

```text
0
0
1
-1
true
false
```

`0.49` và `0.51` cách nhau chỉ `0.02` nhưng rơi vào hai bucket khác nhau. Đây **không phải lỗi** của hàm: nó cho thấy bucket equality và distance-based closeness là **hai khái niệm khác nhau**.

Ví dụ cố ý dùng `width=0.5` biểu diễn chính xác trong nhị phân và miền đầu vào được chặn. Nếu bucket theo bước thập phân như `0.01` hay dùng các số rất lớn, phải xét làm tròn tại ranh giới, overflow/cast saturation và hợp đồng làm tròn. Có thể dùng `BigDecimal` cho dữ liệu thập phân cần chính xác và lựa chọn chế độ làm tròn phù hợp. Không đưa giá trị NaN/Infinity vào hàm bucket nếu chưa định nghĩa chính sách riêng.

### 11.2. Muốn nhóm theo sai số, có thể cần thuật toán clustering

Nếu yêu cầu là “mọi phép đo được coi thuộc một cụm khi có chuỗi liên kết gần nhau” thì bạn đang định nghĩa **connected components theo quan hệ gần kề**, không phải equality từng cặp. Cụm có thể chứa hai điểm cách nhau lớn hơn epsilon nếu có chuỗi điểm trung gian. Nếu yêu cầu “mọi cặp trong một cụm đều gần nhau”, lại là tiêu chí khác. Hãy chọn rõ bài toán clustering; đừng ngầm cài đặt bằng `equals` hoặc comparator tùy tiện.

## 12. Thực hành với `HashMap`, `TreeMap`, `TreeSet` và `sort`

### 12.1. `HashMap`/`HashSet`

Các collection dựa trên hash sử dụng `hashCode` và `equals` của khóa. Chúng mong đợi hợp đồng equality ổn định, bắc cầu và hash nhất quán. Nếu đối tượng chứa giá trị floating-point thay đổi **sau khi đã dùng làm khóa**, có thể không tìm được khóa trong đúng bucket ban đầu.

Tình huống nên tránh:

```java
// Ý tưởng sai: dùng một trường mutable làm hash/equality rồi đổi nó
// sau khi đối tượng đã được chèn vào HashSet/HashMap.
```

Đây là **mô tả sai lầm**, không phải API Java có thể chạy độc lập. Thiết kế tốt hơn là dùng khóa **immutable** — ví dụ ID, mã mẫu đo, hoặc bucket key được xác định từ đầu.

### 12.2. `TreeMap`/`TreeSet`

Collection dạng cây sử dụng `compare` hoặc `compareTo` để xác định thứ tự và sự trùng khóa. Nếu comparator trả `0` cho hai đối tượng mà `equals` coi khác nhau, tập có thể có hành vi khác `Set` dựa trên equals. Java cho phép comparator **không nhất quán với equals** trong một số ngữ cảnh nhưng cần ghi rõ; đối với `SortedSet`/`SortedMap`, việc không nhất quán có thể làm vi phạm kỳ vọng chung của hợp đồng `Set`/`Map`.

Nhưng comparator **không bắc cầu** là vấn đề nghiêm trọng hơn: nó vi phạm chính hợp đồng `Comparator` và có thể gây kết quả phụ thuộc đường đi so sánh, như ví dụ ở mục 6.

Nếu cần thứ tự theo số đo rồi phá hòa bằng ID:

```java
// Đoạn này giả định Sample có value():double và id():long
Comparator<Sample> order =
        Comparator.comparingDouble(Sample::value)
                .thenComparingLong(Sample::id);
```

Đây là **mảnh minh họa**, cần khai báo `Sample`. Comparator tạo thứ tự có tiêu chí rõ, không dùng epsilon để trả `0`. Chú ý: thêm tiêu chí ID chỉ khắc phục những trường hợp **đã bằng nhau theo tiêu chí đầu hợp lệ**; nó không cứu một comparator epsilon vốn không bắc cầu.

### 12.3. `Collections.sort` và `List.sort`

Các thuật toán sắp xếp mong đợi comparator tuân thủ hợp đồng. Với comparator sai, thứ tự sắp xếp có thể thiếu nhất quán, hoặc có thể gặp ngoại lệ cho biết comparator vi phạm hợp đồng trên một số dữ liệu. **Không có ngoại lệ** không chứng minh comparator đúng; phản ví dụ bắc cầu là đủ để bác bỏ tính hợp lệ.

## 13. Các bẫy đặc biệt của floating point khi thiết kế equality/ordering

### 13.1. Signed zero

`+0.0 == -0.0` là `true`, nhưng `Double.compare(-0.0,+0.0) < 0` và `Double.equals` phân biệt hai dạng zero.

Nếu ứng dụng muốn gộp cả hai thành một khóa, hãy chuẩn hóa theo quy tắc:

```java
static double normalizeZero(double value) {
    return value == 0.0 ? 0.0 : value;
}
```

Mảnh phương thức này biến `-0.0` thành `+0.0`, giữ các số khác zero; chỉ dùng nếu dấu zero không có ý nghĩa ở các phép tính kế tiếp. Với phép chia hoặc `Math.atan2`, signed zero có thể có ý nghĩa thực sự.

### 13.2. NaN

`Double.NaN == Double.NaN` là false, trong khi `Double.compare(Double.NaN, Double.NaN) == 0`. Một comparator tự viết dựa trên `a > b` và `a < b` thường sai với NaN. Khi domain không cho phép NaN, hãy **chặn bằng `Double.isFinite`** (nó cũng loại infinity) hoặc `Double.isNaN`; nếu domain cho phép NaN, phải định nghĩa rõ thứ tự và equality.

### 13.3. Infinity và overflow của phép trừ

`Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` là NaN. `Double.MAX_VALUE - (-Double.MAX_VALUE)` tràn thành infinity dù hai đầu vào hữu hạn. Công thức `Math.abs(a-b)` chỉ thích hợp khi bạn đã đánh giá miền giá trị và chính sách đối với các kết quả đặc biệt.

### 13.4. Tolerance thay đổi theo ngữ cảnh

Sai số chấp nhận được của phép đo nhiệt độ có thể khác đơn vị, độ chính xác thiết bị và thời gian lấy mẫu. Sai số cho thuật toán tính số có thể thay đổi theo magnitude và cách tích lũy phép tính. Không dùng một biến global `EPSILON` rồi áp cho mọi field, mọi domain và mọi chức năng.

### 13.5. BigDecimal cũng có phân biệt equality và ordering

`BigDecimal` là số thập phân tùy ý độ chính xác, nhưng `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là false vì `equals` xét cả scale, trong khi `compareTo` trả `0` vì hai giá trị số bằng nhau. Nếu dùng `BigDecimal` làm khóa, phải quyết định liệu scale là một phần danh tính hay có quy tắc chuẩn hóa riêng. Việc đổi từ `double` sang `BigDecimal` **không tự giải quyết mọi vấn đề thiết kế equals/comparator**.

## 14. Quy trình chọn quan hệ so sánh cho dự án

**Bước 1 — Xác định mục đích.** Bạn cần trả lời “có nằm trong sai số cho phép”, “có phải cùng thực thể”, “có cùng nhóm đã định nghĩa”, hay “xếp thứ tự nào”?

**Bước 2 — Xác định domain.** Có cho phép `NaN`, `Infinity`, `-0.0`, giá trị ngoài ngưỡng, hoặc số thập phân cần chính xác không? Có cần phân biệt dữ liệu đo theo đơn vị, timestamp, ID?

**Bước 3 — Nếu chỉ là truy vấn gần bằng**, chọn absolute/relative tolerance theo bài toán, kiểm tra finite và tập dữ liệu biên; trả về một `boolean` riêng, không dùng làm `equals`/`compare`.

**Bước 4 — Nếu cần danh tính**, dùng ID ổn định hoặc khóa canonical/bucket có chính sách rõ, tạo `equals`/`hashCode` nhất quán; tránh field mutable.

**Bước 5 — Nếu cần thứ tự**, dùng `Double.compare` hoặc comparator có các bước so sánh bắc cầu, kiểm tra quy tắc `compare(a,b)==0` và cân nhắc tương thích với equality của khóa.

**Bước 6 — Kiểm thử phản ví dụ.** Với ba giá trị `0.00, 0.09, 0.18`, truy vấn gần bằng có thể không bắc cầu; nhưng mọi equality hoặc comparator-zero thực sự dùng làm khóa phải có tính bắc cầu. Thêm bộ dữ liệu chứa ±0, NaN, ±Infinity, số lớn, số gần ranh giới bucket.

**Bước 7 — Ghi rõ quyết định.** Tài liệu hóa: “gần bằng theo dung sai là phép kiểm tra nghiệp vụ” hoặc “khóa nhóm là bucket 0.5 trên miền dữ liệu đã kiểm tra”, không dùng mơ hồ “tất cả gần bằng đều là bằng nhau”.

## 15. Bộ kiểm thử đặc tính nên có

Thay vì chỉ kiểm thử từng cặp bất kỳ, với comparator/equality nên kiểm thử các **tính chất**:

| Tính chất | Câu hỏi kiểm tra |
| --- | --- |
| Reflexivity | Với object `x` hợp lệ, `x.equals(x)` có luôn true? |
| Symmetry | `x.equals(y)` và `y.equals(x)` có giống nhau? |
| Transitivity | Nếu `x == y` và `y == z` theo equality tự định nghĩa, `x == z` có đúng? |
| Hash consistency | Nếu `x.equals(y)` thì hash có giống nhau? |
| Comparator sign symmetry | Dấu của `compare(x,y)` và `compare(y,x)` có ngược nhau? |
| Comparator transitivity | Nếu `x>y` và `y>z` thì có `x>z` không? |
| Comparator zero consistency | Nếu `compare(x,y)==0`, thì mọi `z` nhìn `x` và `y` theo cùng dấu thứ tự không? |
| Special values | ±0.0, NaN, ±Infinity, rất lớn/rất nhỏ được xử lý thế nào? |
| Mutable key | Khóa có bị thay đổi trong thời gian ở collection không? |

**Bộ ba bắt buộc để bắt lỗi epsilon:** `a=0.00`, `b=0.09`, `c=0.18`, `ε=0.1`. Nếu comparator trả `0` cho cặp `a,b` và `b,c` nhưng khác 0 cho `a,c`, dừng lại và thiết kế lại quan hệ, không chỉ tăng số lượng bài test.

## 16. Những ngộ nhận phổ biến

1. **“Mọi phép `double ==` đều sai.”** Không đúng. `==` là phép so sánh số học có hợp đồng xác định; sai là dùng nó cho mục tiêu gần bằng khi kết quả có sai số không được xem xét.
2. **“Thêm epsilon là phép sửa chung cho `equals`.”** Sai: gần bằng thường không bắc cầu.
3. **“`hashCode() = 1` làm equals epsilon hợp lệ.”** Sai: tính bắc cầu vẫn bị phá.
4. **“Comparator trả 0 nghĩa là khoảng cách nhỏ hơn epsilon.”** Chỉ có thể nếu quan hệ trả 0 vẫn là quan hệ tương đương hợp lệ; ngưỡng chênh lệch tùy tiện không đáp ứng.
5. **“`TreeSet` dùng `equals` để loại trùng.”** Sai: nó dùng so sánh bằng 0 của comparator/natural ordering.
6. **“Sắp xếp không ném exception là comparator đúng.”** Sai: lỗi hợp đồng có thể không lộ ở bộ dữ liệu đang kiểm tra.
7. **“Chỉ cần `a < b ? -1 : a > b ? 1 : 0` là đủ.”** Không đủ với NaN và signed zero.
8. **“`Double.compare` cũng chỉ là `a-b`.”** Sai: nó có thứ tự rõ cho NaN, ±0 và các số khác.
9. **“Bucket 0.5 tương đương gần bằng 0.5.”** Sai: hai điểm rất gần ở ranh giới bucket có thể khác nhóm.
10. **“NaN luôn bị mọi kiểu so sánh coi không bằng.”** Primitive `==` và wrapper `equals` có hợp đồng khác nhau.
11. **“Dữ liệu số đo đã gần bằng thì nên là cùng khóa HashMap.”** Không tự động; cần một chính sách danh tính ổn định.
12. **“Dùng BigDecimal thì equals luôn so sánh theo giá trị số.”** Không đúng khi scale khác nhau.
13. **“Epsilon = 1e-9 đúng cho mọi thang độ lớn.”** Sai; sai số phải theo đơn vị, quy mô và hợp đồng.
14. **“Một quan hệ bằng 0 không bắc cầu vẫn có thể thành comparator hợp lệ.”** Sai, đây là vi phạm trực tiếp hợp đồng comparator.

## 17. Bài tập có đáp án

### Bài 1 — Tìm tính chất bị vi phạm

Với `ε=0.1`, `a=0.00`, `b=0.09`, `c=0.18`, hãy xác định tính chất nào của `equals` không được bảo đảm.

**Đáp án:** tính bắc cầu. `near(a,b)` và `near(b,c)` true, nhưng `near(a,c)` false.

### Bài 2 — Comparator sai

Cho comparator trả 0 nếu `Math.abs(x-y)<=0.1`, còn lại dùng `Double.compare`. Vì sao không nên dùng nó trong `TreeSet`?

**Đáp án:** quan hệ `compare(x,y)==0` không bắc cầu. `TreeSet` dùng comparator để xác định trùng khóa, nên thứ tự chèn có thể làm kết quả khác nhau.

### Bài 3 — Phân biệt `==` với `Double.compare`

Dự đoán `-0.0 == +0.0` và `Double.compare(-0.0,+0.0)`.

**Đáp án:** `true` và `-1`. Java primitive equality coi signed zero bằng nhau, wrapper comparison có thứ tự phân biệt.

### Bài 4 — NaN

Dự đoán `Double.NaN == Double.NaN` và `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))`.

**Đáp án:** `false` và `true`, theo hợp đồng riêng của primitive equality và wrapper equality.

### Bài 5 — HashCode

Tại sao không nên viết `equals` bằng `Math.abs(a-b)<=eps` nhưng `hashCode` bằng `Double.hashCode(a)`?

**Đáp án:** hai object được coi là bằng nhau theo epsilon có thể có hash khác nhau. Điều này vi phạm yêu cầu “equals true ⇒ hashCode bằng nhau”, bên cạnh lỗi không bắc cầu.

### Bài 6 — Chọn phép so sánh đúng nhu cầu

Một phép đo nhiệt độ có dung sai ±0.2°C. Nên dùng quan hệ nào để xác định **hai phép đo chênh nhau trong dung sai**, và quan hệ nào để xác định **hai bản ghi có cùng ID**?

**Đáp án:** phép truy vấn gần bằng dựa trên `Math.abs(temp1-temp2)<=0.2` cho yêu cầu đầu; equality bằng `id` ổn định cho yêu cầu thứ hai. Không dùng khoảng cách nhiệt độ làm danh tính bản ghi.

### Bài 7 — Bucket ranh giới

Với `bucket(x)=floor(x/0.5)`, `bucket(0.49)` và `bucket(0.51)` bằng nhau không?

**Đáp án:** không; chúng thuộc bucket `0` và `1` dù chỉ cách nhau 0.02. Đây là hệ quả chính sách lượng hóa, không phải vi phạm bắc cầu.

### Bài 8 — Infinity

Tại sao `Math.abs(Double.POSITIVE_INFINITY-Double.POSITIVE_INFINITY)<=1e-6` là false?

**Đáp án:** `Infinity-Infinity` là NaN, và phép so sánh `NaN <= ...` là false. Nếu nghiệp vụ muốn nhận “cùng vô cực”, phải có quy tắc riêng.

### Bài 9 — Comparator thứ cấp

Khi sắp xếp mẫu đo theo giá trị, nếu hai giá trị bằng nhau theo `Double.compare`, bạn muốn dùng mã bản ghi để sắp thứ tự. Nên làm gì?

**Đáp án:** dùng `Comparator.comparingDouble(Sample::value).thenComparingLong(Sample::id)` với `Sample` có hai phương thức phù hợp. Không dùng epsilon comparator rồi hy vọng ID khắc phục mọi vi phạm.

### Bài 10 — Chọn cấu trúc dữ liệu

Bạn cần lưu các giá trị và cho phép truy vấn “liệt kê mọi số cách target không quá 0.1”. Có nhất thiết biến quy tắc gần bằng thành `equals`/comparator không?

**Đáp án:** không. Lưu bằng khóa/thứ tự hợp lệ (`Double.compare` hoặc khóa ID), rồi thực hiện truy vấn khoảng cách riêng trên các phần tử ứng viên. Có thể sử dụng range query có ranh giới thích hợp và lọc bằng hàm near; chính sách với NaN/Infinity phải rõ.

## 18. Checklist trước khi dùng số thực làm khóa hoặc thứ tự

- [ ] Tôi biết rõ **gần bằng** là truy vấn sai số, không mặc nhiên là equality.
- [ ] Tôi kiểm tra phản xạ, đối xứng, **bắc cầu** và `hashCode` nếu triển khai `equals`.
- [ ] Tôi kiểm tra hợp đồng `Comparator`, nhất là sự nhất quán khi `compare(a,b)==0`.
- [ ] Tôi không trả 0 trong comparator chỉ vì `Math.abs(a-b)<=epsilon`.
- [ ] Tôi dùng `Double.compare`/`Comparator.comparingDouble` khi cần thứ tự theo giá trị floating point.
- [ ] Tôi biết quy tắc khác nhau của `==` primitive, `Double.equals`, `Double.compare` với NaN và signed zero.
- [ ] Tôi chọn tolerance theo **đơn vị và thang độ lớn**, validate NaN/Infinity và trường hợp tràn.
- [ ] Nếu cần nhóm ổn định, tôi định nghĩa **khóa canonical/bucket**, chấp nhận ranh giới nhóm rõ ràng.
- [ ] Tôi không sửa trường tham gia equality/hash khi đối tượng đang nằm trong hash collection.
- [ ] Tôi kiểm tra việc sắp xếp/tập hợp với ba giá trị `0.00,0.09,0.18` cùng ±0, NaN, ±Infinity.
- [ ] Tôi đã biên dịch/chạy ít nhất ba ví dụ và hiểu vì sao kết quả khác nhau theo cơ chế collection.
- [ ] Quy tắc so sánh/nhóm/sắp xếp được ghi rõ cho người bảo trì sau này.

**Tài liệu chính thức (Java SE 17):**

- [Object.equals và Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): hợp đồng equality và hash.
- [Comparator interface](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Comparator.html): thứ tự, tính bắc cầu và consistency.
- [Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `compare`, `equals`, `hashCode`, signed zero, NaN.
- [TreeSet API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeSet.html): thứ tự và tiêu chí bằng nhau.
- [TreeMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/TreeMap.html): sorted map theo comparator.
- [HashMap API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html): sử dụng equality/hash của khóa.
- [BigDecimal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `equals` phân biệt scale, `compareTo` theo giá trị số.

**Kết luận:** sai số gần bằng là **thông tin của bài toán**, còn `equals`, `hashCode` và `Comparator` là **hợp đồng cấu trúc dữ liệu**. Giữ chúng tách biệt: dùng tolerance cho truy vấn số học; dùng khóa ổn định hoặc `Double.compare` cho danh tính và thứ tự.

</details>

- [Quay lại đầu trang](#back-to-top)
