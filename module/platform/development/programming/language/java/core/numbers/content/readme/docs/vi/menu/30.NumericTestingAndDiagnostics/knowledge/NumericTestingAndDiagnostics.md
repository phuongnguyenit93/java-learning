<a id="back-to-top"></a>

# Kiểm thử và chẩn đoán

## Menu
- [Kiểm tra MIN_VALUE, MAX_VALUE, số 0 và lân cận biên](#numeric-testing-and-diagnostics-001)
- [Kiểm tra overflow trước và sau chuyển kiểu](#numeric-testing-and-diagnostics-002)
- [Số âm với phép chia, lấy dư và làm tròn](#numeric-testing-and-diagnostics-003)
- [NaN, vô cực, signed zero và subnormal](#numeric-testing-and-diagnostics-004)
- [Kiểm tra phép so sánh với sai số phù hợp](#numeric-testing-and-diagnostics-005)
- [Kiểm tra BigDecimal cả giá trị và scale khi hợp đồng yêu cầu](#numeric-testing-and-diagnostics-006)
- [Đối chiếu mọi RoundingMode tại trường hợp đúng giữa](#numeric-testing-and-diagnostics-007)
- [Kiểm thử round-trip parse, format và cơ số](#numeric-testing-and-diagnostics-008)
- [Dùng BigInteger hoặc phép tính tham chiếu để phát hiện tràn](#numeric-testing-and-diagnostics-009)
- [Kiểm thử số ngẫu nhiên bằng seed hoặc nguồn được truyền vào](#numeric-testing-and-diagnostics-010)
- [In mẫu bit, kiểu biểu thức và precision để điều tra kết quả bất ngờ](#numeric-testing-and-diagnostics-011)

## <a id="numeric-testing-and-diagnostics-001">Kiểm tra MIN_VALUE, MAX_VALUE, số 0 và lân cận biên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra `MIN_VALUE`, `MAX_VALUE`, số 0 và các giá trị lân cận biên trong Java

## 1. Vì sao kiểm thử số phải tập trung vào biên?

Một đoạn mã xử lý số có thể chạy đúng với dữ liệu thông thường nhưng sai ở **giới hạn miền giá trị**: phép cộng `int` tràn, phép chia cho 0, số thực dưới ngưỡng biểu diễn thành 0, hoặc kiểm tra khoảng sử dụng `MAX_VALUE + 1`. Những lỗi này thường không xuất hiện khi chỉ thử các giá trị như 10, 20 và 50.

Đề bài “kiểm tra `MIN_VALUE`, `MAX_VALUE`, số 0 và lân cận biên” yêu cầu lập tập ca kiểm thử theo **phân vùng tương đương** (*equivalence partitioning*) và **phân tích giá trị biên** (*boundary-value analysis*). Nếu một hàm chấp nhận khoảng `[min, max]`, tối thiểu cần nghĩ đến `min`, `max`, giá trị ngay **bên trong**, ngay **bên ngoài** và các ca trung tâm/0 khi có ý nghĩa.

Đặc biệt cần phân biệt hai loại số trong Java:

- **Số nguyên kiểu nguyên thủy**: `byte`, `short`, `int`, `long` có biên hữu hạn, phép tính thông thường có thể tràn mà không ném ngoại lệ.
- **Số dấu phẩy động**: `float`, `double` có giá trị hữu hạn cực lớn, giá trị dương khác 0 nhỏ nhất, số *subnormal*, `+0.0`/`-0.0`, `NaN` và `±Infinity`. “Lân cận” phải được hiểu theo những giá trị **có thể biểu diễn** chứ không đơn giản cộng/trừ 1.

Với `BigInteger` và `BigDecimal`, miền giá trị được mở rộng tùy ý trong giới hạn tài nguyên, nhưng chuyển sang `int`, `long` và các phép kiểm tra scale/precision vẫn có ca biên.

Mục tiêu bài học: lập ma trận ca thử có hệ thống, dự đoán và kiểm chứng kết quả Java 17+, chẩn đoán lỗi tràn/thiếu chính xác, và viết test không phụ thuộc may rủi.

## 2. Quy trình thiết kế ca biên

Giả sử hàm `accept(int value)` chỉ chấp nhận `0 <= value <= 100`. Hãy bắt đầu bằng đặc tả, rồi lấy các điểm:

| Phân vùng | Giá trị | Kết quả mong đợi |
|---|---:|---|
| Ngay ngoài biên dưới | `-1` | Từ chối |
| Đúng biên dưới | `0` | Chấp nhận |
| Vừa trên biên dưới | `1` | Chấp nhận |
| Một giá trị giữa khoảng | `50` | Chấp nhận |
| Vừa dưới biên trên | `99` | Chấp nhận |
| Đúng biên trên | `100` | Chấp nhận |
| Ngay ngoài biên trên | `101` | Từ chối |

Từ ví dụ này, mở rộng thành một checklist khi làm việc với số:

1. **Loại giá trị**: số nguyên, `double`, `BigDecimal`, v.v.; cùng ký hiệu nhưng quy tắc overflow khác nhau.
2. **Miền hợp lệ**: cận dưới/trên là **bao gồm** (`>=`, `<=`) hay **không bao gồm** (`>`, `<`)? Có chấp nhận số âm, 0, số thập phân?
3. **Lân cận giá trị**: với số nguyên có thể dùng `±1` **nếu còn trong miền**; với `double` nên dùng `Math.nextUp`/`Math.nextDown`.
4. **Các trường hợp đặc biệt**: chia 0, `MIN_VALUE / -1`, `Math.abs(MIN_VALUE)`, `-0.0`, `NaN`, vô cực, subnormal.
5. **Hành vi mong đợi**: trả đúng kết quả, ném ngoại lệ, bão hòa, từ chối dữ liệu hay làm tròn theo đặc tả. Đừng chỉ kiểm tra “không crash”.
6. **Không tự tạo lỗi trong bộ test**: biểu thức `Integer.MAX_VALUE + 1` đã tràn trước khi đưa vào hàm; đó là một đầu vào âm, không phải một `int` vượt max.
7. **Kiểm thử nhiều biến**: ưu tiên những tổ hợp khiến phép tính chạm biên, ví dụ `a + b`, `a * b`, chia cho 0 hoặc chia `MIN_VALUE / -1`.

Số test càng nhiều không tự động càng tốt; hãy chọn các trường hợp có **lý do trực tiếp từ hợp đồng và loại dữ liệu**.

## 3. Biên các kiểu số nguyên nguyên thủy

| Kiểu | `MIN_VALUE` | `MAX_VALUE` | Miền |
|---|---:|---:|---|
| `byte` | `-128` | `127` | 8 bit có dấu |
| `short` | `-32768` | `32767` | 16 bit có dấu |
| `int` | `-2147483648` | `2147483647` | 32 bit có dấu |
| `long` | `-9223372036854775808` | `9223372036854775807` | 64 bit có dấu |

Với `int` có thể chia các giá trị quan trọng thành:

```text
Integer.MIN_VALUE       = -2147483648
Integer.MIN_VALUE + 1   = -2147483647
-1
0
1
Integer.MAX_VALUE - 1   =  2147483646
Integer.MAX_VALUE       =  2147483647
```

Đây là **bảy điểm kiểm tra** chính ở biên vật lý của kiểu. Nếu hàm có giới hạn nghiệp vụ khác, hãy bổ sung biên nghiệp vụ đó (ví dụ `0` và `100`). Cần đọc kỹ: `Integer.MIN_VALUE - 1` **không phải một `int` hợp lệ**; phép toán kiểu `int` sẽ tràn.

### 3.1. Tràn số `int` thường âm thầm quay vòng

Java tính toán `int` trong miền 32 bit. Nếu kết quả toán học vượt giới hạn, phép cộng/trừ/nhân nguyên thủy thông thường **không tự ném ngoại lệ**:

```java
int wrappedUp = Integer.MAX_VALUE + 1;
int wrappedDown = Integer.MIN_VALUE - 1;

System.out.println(wrappedUp);   // -2147483648
System.out.println(wrappedDown); // 2147483647
```

Không xem kết quả âm sau phép tăng là bằng chứng dữ liệu âm đã được nhập vào; chính **phép toán** có thể tạo ra giá trị ngoài ý định.

Khi yêu cầu “tính đúng hoặc báo lỗi”, dùng họ `Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact` hoặc `negateExact` tùy phép toán. Chúng ném `ArithmeticException` nếu kết quả không vừa kiểu đích.

### 3.2. Bẫy `Math.abs(Integer.MIN_VALUE)`

Miền `int` không đối xứng: trị tuyệt đối toán học của `Integer.MIN_VALUE` là `2147483648`, lớn hơn `Integer.MAX_VALUE` một đơn vị. Vì vậy:

```java
System.out.println(Math.abs(Integer.MIN_VALUE)); // -2147483648
```

Với `Math.abs(long)` cũng có vấn đề tương tự ở `Long.MIN_VALUE`. Nếu muốn kết quả trị tuyệt đối chính xác cho `int` nhưng đủ miền, có thể nâng kiểu **trước** khi tính:

```java
long magnitude = Math.abs((long) Integer.MIN_VALUE);
System.out.println(magnitude); // 2147483648
```

Với `Long.MIN_VALUE`, nâng lên `long` không giúp được vì đã ở giới hạn `long`; có thể dùng `BigInteger.valueOf(Long.MIN_VALUE).abs()` khi thực sự cần độ lớn chính xác.

### 3.3. Trường hợp `MIN_VALUE / -1`

Trong số học toán học, `-2147483648 / -1 = 2147483648`, ngoài miền `int`. Java quy định phép chia nguyên `Integer.MIN_VALUE / -1` trả `Integer.MIN_VALUE` do overflow, không báo lỗi. Với `long`, `Long.MIN_VALUE / -1L` tương tự.

Đặc biệt, phép chia nguyên với mẫu số **0** lại ném `ArithmeticException`. Do đó hai ca sau phải được kiểm tra **riêng biệt**, không gộp:

```text
MIN_VALUE / -1  -> overflow đặc biệt, không ném
anyInteger / 0  -> ArithmeticException
```

Nếu dùng Java 18+ và có nhu cầu phép chia chính xác với báo tràn, xem `Math.divideExact`. Để minh họa/test Java 17, có thể kiểm tra tường minh cặp `MIN_VALUE, -1` trước phép chia.

## 4. Ví dụ chạy được: chẩn đoán biên `int`

**Tệp `IntegralBoundaryDemo.java`:**

```java
public class IntegralBoundaryDemo {
    public static void main(String[] args) {
        System.out.println("int.min=" + Integer.MIN_VALUE);
        System.out.println("int.max=" + Integer.MAX_VALUE);

        System.out.println("wrapMax=" + (Integer.MAX_VALUE + 1));
        System.out.println("wrapMin=" + (Integer.MIN_VALUE - 1));
        System.out.println("absMin=" + Math.abs(Integer.MIN_VALUE));
        System.out.println("minDivNegOne=" + (Integer.MIN_VALUE / -1));

        try {
            System.out.println(Math.addExact(Integer.MAX_VALUE, 1));
        } catch (ArithmeticException ex) {
            System.out.println("addExact=ArithmeticException");
        }

        try {
            int impossible = Integer.MAX_VALUE / 0;
            System.out.println(impossible);
        } catch (ArithmeticException ex) {
            System.out.println("divideZero=ArithmeticException");
        }

        long magnitude = Math.abs((long) Integer.MIN_VALUE);
        System.out.println("safeAbs=" + magnitude);
    }
}
```

```bash
javac IntegralBoundaryDemo.java
java IntegralBoundaryDemo
```

Kết quả:

```text
int.min=-2147483648
int.max=2147483647
wrapMax=-2147483648
wrapMin=2147483647
absMin=-2147483648
minDivNegOne=-2147483648
addExact=ArithmeticException
divideZero=ArithmeticException
safeAbs=2147483648
```

Ví dụ đặt phép chia cho 0 trong biến `int` với mẫu số là literal `0`; Java cho phép biên dịch phép chia nguyên như vậy và chỉ ném ngoại lệ khi chạy. Hãy lưu ý rằng biểu thức `Integer.MAX_VALUE + 1` có kết quả tràn theo kiểu `int`, và việc gán về `long` **sau** phép cộng không sửa được giá trị đã tràn:

```java
long wrong = Integer.MAX_VALUE + 1;     // Đã tràn int trước khi gán
long right = (long) Integer.MAX_VALUE + 1; // Cộng ở miền long

System.out.println(wrong); // -2147483648
System.out.println(right); // 2147483648
```

## 5. Lân cận biên với số thực: khác bản chất số nguyên

`float`/`double` theo IEEE 754 biểu diễn một tập các số có mật độ không đều. Khoảng cách giữa hai giá trị biểu diễn được liên tiếp thay đổi theo độ lớn. Vì vậy **không thể tùy tiện dùng `x + 1` để tìm giá trị kế bên**.

Các trường hợp đặc biệt của `double`:

| Hằng / giá trị | Ý nghĩa |
|---|---|
| `Double.MAX_VALUE` | Số hữu hạn dương lớn nhất, khoảng `1.7976931348623157E308` |
| `-Double.MAX_VALUE` | Số hữu hạn âm nhỏ nhất về thứ tự |
| `Double.MIN_NORMAL` | Số **normal dương** nhỏ nhất, khoảng `2.2250738585072014E-308` |
| `Double.MIN_VALUE` | Số **dương khác 0 nhỏ nhất**, khoảng `4.9E-324` |
| `+0.0`, `-0.0` | Hai biểu diễn zero khác bit dấu |
| `Double.POSITIVE_INFINITY` / `NEGATIVE_INFINITY` | Giá trị vô cực |
| `Double.NaN` | Không phải số, có quy tắc so sánh đặc biệt |

**Đặc biệt dễ sai:** `Double.MIN_VALUE` **không phải** số âm hữu hạn nhỏ nhất. `Double.MIN_VALUE` là số **dương** khác 0 nhỏ nhất. Với `Float.MIN_VALUE` cũng vậy. Nếu cần kiểm tra biên âm hữu hạn, dùng `-Double.MAX_VALUE` hoặc `-Float.MAX_VALUE`.

Số **subnormal** nằm giữa 0 và `MIN_NORMAL` (về độ lớn tuyệt đối). Chúng cho phép biểu diễn những số cực nhỏ gần 0 bằng độ chính xác tương đối giảm dần. Khi phép tính tạo ra số nhỏ hơn khả năng biểu diễn, kết quả có thể underflow về 0.

### 5.1. `Math.nextUp`, `Math.nextDown` và `Math.ulp`

Để tạo các giá trị có thể biểu diễn **kề nhau**:

- `Math.nextUp(x)`: giá trị dấu phẩy động biểu diễn được liền sau theo thứ tự số học.
- `Math.nextDown(x)`: giá trị liền trước.
- `Math.nextAfter(x, direction)`: bước sang giá trị kề theo hướng `direction`.
- `Math.ulp(x)`: độ lớn một đơn vị ở vị trí thấp nhất (*unit in the last place*) tại độ lớn tương ứng của `x`.

Ví dụ:

```java
System.out.println(Math.nextUp(1.0));   // 1.0000000000000002
System.out.println(Math.nextDown(1.0)); // 0.9999999999999999
System.out.println(Math.nextUp(0.0));   // 4.9E-324
System.out.println(Math.nextDown(0.0)); // -4.9E-324
```

Khác với `+1`, các hàm này có thể tạo **sát biên biểu diễn** để kiểm tra điều kiện so sánh, thuật toán số và việc làm tròn.

### 5.2. Cẩn thận với số 0 có dấu

Theo `==`, `+0.0 == -0.0` là `true`, nhưng bit dấu khác nhau và có thể ảnh hưởng phép chia:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
```

`Double.compare(0.0, -0.0)` **không trả 0** vì `Double.compare` phân biệt signed zero trong thứ tự toàn phần của API. Nếu chỉ kiểm tra điều kiện `x == 0.0`, cả hai sẽ được xem là zero. Trong một số thuật toán, cần xác định liệu dấu zero có là một phần của hợp đồng hay không.

### 5.3. `NaN` và vô cực

`Double.NaN` không thể kiểm tra bằng `x == Double.NaN` vì mọi so sánh `==` với `NaN` đều là `false`. Dùng `Double.isNaN(x)`. Để từ chối mọi giá trị không hữu hạn, dùng `Double.isFinite(x)`.

Nếu `x` là `NaN`, cả `x < limit` lẫn `x > limit` đều `false`. Vì vậy, một validator viết chỉ bằng hai phép so sánh cận có thể vô tình **chấp nhận NaN**; kiểm tra `Double.isFinite` trước là lựa chọn rõ ràng khi miền chỉ chấp nhận số hữu hạn.

## 6. Ví dụ chạy được: số thực và lân cận biên

**Tệp `FloatingBoundaryDemo.java`:**

```java
public class FloatingBoundaryDemo {
    public static void main(String[] args) {
        System.out.println("smallestPositive=" + Double.MIN_VALUE);
        System.out.println("smallestNormal=" + Double.MIN_NORMAL);

        System.out.println("nextUpZero=" + Math.nextUp(0.0));
        System.out.println("nextDownZero=" + Math.nextDown(0.0));
        System.out.println("nextUpOne=" + Math.nextUp(1.0));
        System.out.println("nextDownOne=" + Math.nextDown(1.0));

        System.out.println("underflow=" + (Double.MIN_VALUE / 2));
        System.out.println("overflow="
                + Double.isInfinite(Double.MAX_VALUE * 2));
        System.out.println("negativeZeroSign=" + (1.0 / -0.0));

        System.out.println("zeroEqual=" + (0.0 == -0.0));
        System.out.println("nanEqual=" + (Double.NaN == Double.NaN));
        System.out.println("finiteOne=" + Double.isFinite(1.0));
        System.out.println("finiteInfinity="
                + Double.isFinite(Double.POSITIVE_INFINITY));
    }
}
```

```bash
javac FloatingBoundaryDemo.java
java FloatingBoundaryDemo
```

Kết quả:

```text
smallestPositive=4.9E-324
smallestNormal=2.2250738585072014E-308
nextUpZero=4.9E-324
nextDownZero=-4.9E-324
nextUpOne=1.0000000000000002
nextDownOne=0.9999999999999999
underflow=0.0
overflow=true
negativeZeroSign=-Infinity
zeroEqual=true
nanEqual=false
finiteOne=true
finiteInfinity=false
```

Những giá trị in ở đây theo biểu diễn `Double.toString` chuẩn của Java. `Double.MIN_VALUE / 2` bị underflow và làm tròn về `+0.0`; `Double.MAX_VALUE * 2` overflow thành `+Infinity`. Khác với chia nguyên cho 0, chia `double` cho `+0.0` hoặc `-0.0` với tử hữu hạn khác 0 thường cho vô cực có dấu theo IEEE 754; `0.0 / 0.0` cho `NaN`.

## 7. Các ca thử cần có ở biên phép chuyển đổi kiểu

Một bộ test tốt không chỉ thử dữ liệu đầu vào mà còn thử **kết quả trung gian** khi chuyển kiểu.

### 7.1. `long` sang `int`: tràn hoặc phát hiện lỗi

```java
long aboveInt = (long) Integer.MAX_VALUE + 1;
int truncated = (int) aboveInt; // -2147483648

// Math.toIntExact(aboveInt) ném ArithmeticException.
```

Ép `long` sang `int` giữ các bit thấp, nên có thể biến một số dương lớn thành số âm. Nếu chuyển đổi phải chính xác, dùng `Math.toIntExact(long)` (Java 8+).

### 7.2. `BigInteger` không có `MAX_VALUE` cố định như `long`

`BigInteger` biểu diễn số nguyên có độ lớn tùy ý trong giới hạn bộ nhớ. Có thể tạo số vượt `Long.MAX_VALUE` chính xác:

```java
BigInteger beyondLong =
        BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

System.out.println(beyondLong); // 9223372036854775808
```

Mảnh mã trên cần import `java.math.BigInteger` và đặt trong một phương thức khi chạy. `beyondLong.longValue()` không kiểm tra mất bit; `beyondLong.longValueExact()` ném `ArithmeticException`. Ca thử nên bao gồm **giá trị cuối còn vừa miền**, và **giá trị đầu tiên vượt miền**.

### 7.3. `BigDecimal` có thể khác cách biểu diễn dù cùng số 0

`new BigDecimal("0.00")` có scale 2, còn `BigDecimal.ZERO` có scale 0:

```java
BigDecimal zero = new BigDecimal("0.00");

System.out.println(zero.signum());                 // 0
System.out.println(zero.compareTo(BigDecimal.ZERO)); // 0
System.out.println(zero.equals(BigDecimal.ZERO)); // false
```

Đây là đoạn mã minh họa với import `java.math.BigDecimal`. `signum()` và `compareTo()` kiểm tra giá trị số học; `equals()` còn xét scale. Vì vậy **test số 0 trong `BigDecimal` nên kiểm tra cả dữ liệu có trailing zeros**, tùy yêu cầu nghiệp vụ.

Khi đưa `BigDecimal` về `int`/`long`, dùng `intValueExact()`/`longValueExact()` nếu phải từ chối giá trị ngoài miền hoặc có phần lẻ. `intValue()`/`longValue()` có thể mất phần lẻ và bit cao mà không báo lỗi.

### 7.4. Không hiểu nhầm `Double.MIN_VALUE` và `Integer.MIN_VALUE`

Tên hằng giống nhau nhưng ý nghĩa khác:

- `Integer.MIN_VALUE` là cận dưới theo thứ tự số học, âm.
- `Double.MIN_VALUE` là **số dương khác 0 nhỏ nhất**, không phải cận dưới âm.
- `BigDecimal` và `BigInteger` không có cặp hằng `MIN_VALUE`/`MAX_VALUE` theo miền cố định tương tự primitive.

Do đó, không viết test tổng quát cho mọi kiểu bằng công thức “`MIN_VALUE` là số âm nhỏ nhất”; phải tra hợp đồng của từng kiểu.

## 8. Viết bộ kiểm tra ranh giới không phụ thuộc framework

Một bài test tốt cần:

1. **Đầu vào đã biết** và kết quả mong đợi lấy từ yêu cầu.
2. Đánh giá đúng ngữ nghĩa dấu, overflow và ngoại lệ.
3. Không dùng `assert` của Java nếu chưa bật `-ea`, vì assertion mặc định có thể không được thực thi; có thể dùng `AssertionError` chủ động hoặc JUnit.
4. Kiểm tra các ca vừa trong/vừa ngoài cận, kể cả 0.
5. Kiểm tra các phép chuyển kiểu và phép tính **sau khi validate**, không chỉ validate riêng.
6. Khi kiểm tra `double` cho thuật toán tính gần đúng, dùng dung sai có ý nghĩa theo độ lớn hoặc ULP; khi cần kiểm tra hành vi đúng tại giá trị kề, dùng `Math.nextUp`/`Math.nextDown` và expected cụ thể.

**Tệp `BoundaryChecks.java` — chạy độc lập Java 17+:**

```java
import java.math.BigDecimal;

public class BoundaryChecks {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean inRange(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Invalid bounds");
        }
        return value >= min && value <= max;
    }

    private static boolean isValidFiniteRatio(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static void expectArithmeticException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // Ca thử thành công: phép toán phải từ chối dữ liệu.
        }
    }

    public static void main(String[] args) {
        // Miền nghiệp vụ bao gồm hai đầu [0, 100].
        check(!inRange(-1, 0, 100), "below min");
        check(inRange(0, 0, 100), "at min");
        check(inRange(1, 0, 100), "above min");
        check(inRange(99, 0, 100), "below max");
        check(inRange(100, 0, 100), "at max");
        check(!inRange(101, 0, 100), "above max");

        // Biên vật lý của kiểu int.
        check(Integer.MAX_VALUE + 1 == Integer.MIN_VALUE,
                "int overflow");
        expectArithmeticException(
                () -> Math.addExact(Integer.MAX_VALUE, 1));
        expectArithmeticException(
                () -> Math.toIntExact((long) Integer.MAX_VALUE + 1));

        // Hai zero của double và giá trị kề.
        check(0.0 == -0.0, "numeric zero comparison");
        check(Double.doubleToRawLongBits(0.0)
                != Double.doubleToRawLongBits(-0.0),
                "signed zero bits");
        check(Math.nextUp(0.0) == Double.MIN_VALUE,
                "successor of zero");
        check(Math.nextDown(0.0) == -Double.MIN_VALUE,
                "predecessor of zero");
        check(!isValidFiniteRatio(Double.NaN), "NaN rejected");
        check(!isValidFiniteRatio(Double.POSITIVE_INFINITY),
                "Infinity rejected");
        check(isValidFiniteRatio(0.0), "ratio min");
        check(isValidFiniteRatio(1.0), "ratio max");
        check(!isValidFiniteRatio(Math.nextUp(1.0)),
                "ratio just above max");

        // Số thập phân zero có scale khác nhau.
        BigDecimal scaledZero = new BigDecimal("0.00");
        check(scaledZero.compareTo(BigDecimal.ZERO) == 0,
                "numeric decimal zero");
        check(!scaledZero.equals(BigDecimal.ZERO),
                "BigDecimal scale preserved");

        System.out.println("All boundary checks passed");
    }
}
```

```bash
javac BoundaryChecks.java
java BoundaryChecks
```

Kết quả:

```text
All boundary checks passed
```

`inRange` kiểm tra `min > max` để từ chối cấu hình vô nghĩa. Các so sánh trực tiếp `value >= min && value <= max` **không tính `max + 1`**, nên không tạo overflow ngoài ý muốn. Trong validator `double`, kiểm tra `Double.isFinite` trước để chủ động từ chối `NaN`/`Infinity`; ngoài biên trên được tạo bằng `Math.nextUp(1.0)`, không bằng phép cộng một epsilon tùy tiện.

Đối với dự án dùng JUnit 5, các điều kiện `check` có thể chuyển sang `assertTrue`, `assertFalse`, `assertEquals` và `assertThrows`; nội dung test vẫn là các ca biên được thiết kế từ đặc tả.

## 9. Những bẫy trong test giá trị sát biên

### 9.1. Không tự tính `MIN_VALUE - 1` hoặc `MAX_VALUE + 1` trong kiểu nguyên

Nếu cần thử “ngoài biên” của `int`, **không thể lưu số đó vào một biến `int`**. Phải thử tại ranh giới của API parse hoặc ở kiểu rộng hơn:

```java
// Parse "2147483648" thành int -> NumberFormatException.
Integer.parseInt("2147483648");

// Có thể lưu vào long để kiểm tra chuyển kiểu:
long justAboveInt = (long) Integer.MAX_VALUE + 1;
```

Dòng `Integer.parseInt` là minh họa một lời gọi **sẽ ném ngoại lệ** nếu chạy, vì vậy trong test cần bọc bằng `assertThrows`/try-catch như phần trên.

### 9.2. Không mặc định epsilon cố định đúng cho mọi `double`

`Double.MIN_VALUE` không phải một epsilon tương đối có ích cho mọi phép tính. Khoảng cách hai giá trị `double` kề nhau gần `1.0` xấp xỉ `2.220446049250313E-16`, nhưng gần `1E20` khoảng cách lớn hơn nhiều. Kiểm tra sai số cần xác định **dung sai tuyệt đối/ tương đối** theo bài toán; khi kiểm tra bit/cấu trúc số biểu diễn được, dùng `Math.ulp` và `Math.nextUp/Down`.

### 9.3. Không dùng `==` làm tiêu chí gần đúng một cách máy móc

```java
System.out.println((0.1 + 0.2) == 0.3); // false
```

Đây là hệ quả biểu diễn nhị phân, không phải bằng chứng Java thực hiện sai số học. Kiểm thử số gần đúng nên có tolerance rõ; ngược lại, với các trường hợp API quy định chính xác như signed-zero bits, `nextUp(0.0) == Double.MIN_VALUE`, kiểm tra chính xác hoàn toàn phù hợp.

### 9.4. Chẩn đoán bằng bit và loại dữ liệu

Khi điều tra sự khác biệt giữa `+0.0` và `-0.0`, `==` không đủ. Dùng `Double.doubleToRawLongBits` để quan sát biểu diễn dấu; đối với `NaN`, dùng `Double.isNaN` và cân nhắc quy tắc normalize NaN payload ở từng API. Với `BigDecimal`, in `toPlainString()`, `scale()`, `precision()` và `unscaledValue()` để hiểu tại sao hai giá trị bằng nhau theo `compareTo` mà khác `equals`.

Các thông tin chẩn đoán nên bao gồm kiểu dữ liệu, nguồn giá trị và phép biến đổi đã tạo ra kết quả, đặc biệt khi phát hiện overflow hoặc underflow. Không kết luận từ cách Java in số thập phân rằng toàn bộ chữ số đó tồn tại chính xác trong `double`.

## 10. Ma trận kiểm tra nhanh theo loại dữ liệu

| Loại giá trị | Biên và lân cận cần thử | Rủi ro điển hình |
|---|---|---|
| `byte`, `short` | MIN, MIN+1, -1, 0, 1, MAX-1, MAX | Promotion lên `int`, ép hẹp mất bit |
| `int`, `long` | MIN, MIN+1, -1, 0, 1, MAX-1, MAX | Tràn cộng/nhân, `abs(MIN)`, chia `MIN / -1` |
| Chia nguyên | mẫu số -1, 0, 1; tử 0, MIN, MAX | Chia 0 ném lỗi, tràn `MIN/-1` |
| `float`, `double` | ±MAX_VALUE, ±MIN_NORMAL, ±MIN_VALUE, ±0.0, ±Infinity, NaN | Overflow, underflow, signed zero, so sánh NaN |
| Giá trị `double` ngay sát cận | `Math.nextDown(bound)`, `bound`, `Math.nextUp(bound)` | Cộng một “epsilon” không đúng độ lớn |
| `BigDecimal` | 0, 0.00, số sát ngưỡng quy định, scale khác nhau | Nhầm `equals` với `compareTo`, rounding |
| `BigInteger` | Vượt cận `int`/`long` ±1, giá trị âm cực lớn | `intValue`/`longValue` mất bit |

Không nhất thiết mọi lớp/hàm đều cần toàn bộ ma trận. Chọn ca từ **kiểu input, thao tác đang dùng và quy tắc nghiệp vụ cụ thể**. Tuy nhiên, nếu phương thức có các phép cộng, nhân, chia, ép kiểu hoặc so sánh cận, các vùng biên tương ứng gần như luôn đáng được kiểm tra.

## 11. Bài tập có lời giải

1. **`Integer.MAX_VALUE + 1` trả gì?**  
   `-2147483648`, vì overflow kiểu `int`. `Math.addExact(Integer.MAX_VALUE, 1)` thì ném `ArithmeticException`.

2. **`Math.abs(Integer.MIN_VALUE)` có dương không?**  
   Không, kết quả vẫn là `-2147483648`. Cần nâng sang `long` trước khi gọi `Math.abs` nếu cần độ lớn chính xác.

3. **`Double.MIN_VALUE` có phải số thực âm nhỏ nhất không?**  
   Không. Đây là giá trị `double` **dương khác 0 nhỏ nhất**. Cận hữu hạn âm là `-Double.MAX_VALUE`.

4. **Giá trị `double` biểu diễn được ngay trên `1.0` là gì?**  
   `Math.nextUp(1.0)`, thường được in `1.0000000000000002`.

5. **`+0.0 == -0.0` trả gì? Hai giá trị có hoàn toàn đồng nhất ở mọi phép toán không?**  
   Trả `true`, nhưng bit dấu khác nhau và `1.0 / +0.0` trả `Infinity` còn `1.0 / -0.0` trả `-Infinity`.

6. **Vì sao chỉ dùng `x < 0 || x > 1` để từ chối tỷ lệ không hợp lệ là chưa đủ?**  
   Vì với `NaN`, cả hai so sánh đều `false`. Cần `Double.isFinite(x)` nếu chỉ chấp nhận số hữu hạn.

7. **Tại sao `BigDecimal("0.00").equals(BigDecimal.ZERO)` là `false`, trong khi `compareTo` trả `0`?**  
   Hai số có cùng giá trị số học 0 nhưng khác scale; `equals` xét thêm scale, `compareTo` chỉ xét giá trị.

8. **`Long.MAX_VALUE + 1` có phải đầu vào `long` lớn hơn `Long.MAX_VALUE` để test hàm không?**  
   Không. Biểu thức đã overflow về `Long.MIN_VALUE`. Muốn mô tả số toán học nằm ngoài miền `long`, dùng `BigInteger` hoặc chuỗi đầu vào và kiểm tra phương thức parse/conversion.

## 12. Ghi nhớ và tài liệu tham khảo

Kiểm thử số cần kiểm tra **đúng ba lớp biên**: **biên kiểu dữ liệu**, **biên phép toán**, và **biên nghiệp vụ**. Đối với số nguyên, thử MIN/MAX/0 và điểm kề bằng phép toán không tự overflow; đối với `double`, thử zero có dấu, MIN_VALUE dương, MIN_NORMAL, ±MAX_VALUE, NaN/vô cực và dùng `Math.nextUp`/`Math.nextDown` để tạo giá trị sát biên. Kỳ vọng của mỗi ca phải bám hợp đồng phương thức, không dựa vào cảm giác về số học thông thường.

Tài liệu chính thức Java 21 (các API chính dùng được trên Java 17 trừ khi Javadoc ghi khác):

- [`Integer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html)
- [`Long`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html)
- [`Double`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html)
- [`Math`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [`BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [`BigInteger`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-002">Kiểm tra overflow trước và sau chuyển kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra overflow trước và sau chuyển kiểu trong Java

Khi xử lý dữ liệu số, một phép tính có thể gây mất dữ liệu ở **hai thời điểm khác nhau**: ngay lúc thực hiện phép cộng/nhân trong kiểu hiện tại, hoặc lúc chuyển kết quả sang kiểu có miền giá trị nhỏ hơn. Nếu chỉ kiểm tra sau khi dữ liệu đã bị mất, chương trình có thể kết luận sai rằng giá trị vẫn hợp lệ. Đây là một lỗi quan trọng khi kiểm thử và chẩn đoán chương trình Java.

Bài này hướng dẫn xác định **kiểu của từng phép tính**, phân biệt chuyển kiểu mở rộng và thu hẹp, phát hiện overflow đúng thời điểm bằng `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact`, và sử dụng `BigInteger`/`BigDecimal` khi cần. Các ví dụ tập trung vào Java 17+, bao gồm những phản ví dụ thường làm bài test vượt biên cho kết luận sai.

## 1. Ba câu hỏi phải kiểm tra riêng

Giả sử một API nhận số lượng và đơn giá, tính tổng rồi lưu vào trường số nguyên. Cần làm rõ:

1. **Đầu vào có nằm trong miền hợp lệ không?** Ví dụ số lượng không âm và đơn giá không vượt giới hạn hợp đồng.
2. **Mọi phép tính trung gian có biểu diễn được trong kiểu đang dùng không?** Hai số `int` hợp lệ chưa chắc tích hoặc tổng `int` cũng hợp lệ.
3. **Kết quả cuối có vừa kiểu đích và giữ đúng độ chính xác không?** Một kết quả `long` hợp lệ có thể không vừa `int`; một `BigDecimal` có thể có phần thập phân không thể chuyển sang số nguyên mà không mất dữ liệu.

Ba câu hỏi độc lập với nhau. Việc kiểm tra điều kiện nghiệp vụ trên đầu vào không bảo đảm phép tính an toàn; việc kết quả nằm trong miền kiểu đích sau phép ép kiểu cũng **không chứng minh** dữ liệu ban đầu không bị tràn.

### Các miền kiểu số nguyên thường gặp

| Kiểu | Miền biểu diễn | Ghi chú |
| --- | --- | --- |
| `byte` | -128 đến 127 | Chuyển từ `int` sang `byte` có thể cắt bit |
| `short` | -32.768 đến 32.767 | Chuyển từ `int` sang `short` có thể cắt bit |
| `char` | 0 đến 65.535 | Không dấu; chuyển từ số nguyên lớn/âm có thể cắt bit |
| `int` | -2.147.483.648 đến 2.147.483.647 | Phép cộng/nhân `int` có thể overflow |
| `long` | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | Rộng hơn `int`, nhưng không vô hạn |
| `BigInteger` | Độ lớn thay đổi | Không bị giới hạn ở 32/64 bit; vẫn chịu giới hạn tài nguyên |

Java dùng số nguyên có dấu có độ rộng cố định cho `byte`, `short`, `int`, `long`. Phép cộng và nhân nguyên thông thường vượt miền sẽ cho kết quả theo các bit thấp của phép tính; đối với `int` và `long`, có thể hiểu như số học modulo \(2^{32}\) hoặc \(2^{64}\) rồi diễn giải theo số có dấu. Các phép `+`, `-`, `*` trên nguyên thủy **không tự ném** `ArithmeticException` khi overflow.

## 2. Overflow xảy ra **trước** khi chuyển sang kiểu rộng hơn

Trong Java, kiểu của phép toán được quyết định từ **toán hạng**, không từ kiểu biến nhận kết quả:

```java
int max = Integer.MAX_VALUE; // 2147483647
int one = 1;

long tooLate = max + one;       // -2147483648
long alsoTooLate = (long) (max + one); // -2147483648
long inTime = (long) max + one; // 2147483648
```

Hai biểu thức đầu cộng dưới dạng `int`, overflow đã xảy ra rồi mới chuyển kết quả sai sang `long`. Đặt `(long)` trên **một toán hạng trước phép cộng** khiến phép toán được thực hiện ở miền `long`, nên ví dụ này giữ tổng chính xác.

Sơ đồ xử lý:

```text
Sai:
(int) MAX_VALUE + (int) 1  → cộng int → overflow → ép sang long
Đúng:
(long) MAX_VALUE + (int) 1 → cộng long → 2147483648
```

Đây cũng là lý do không nên viết phép kiểm tra như:

```java
long total = (long) (left + right);
if (total > Integer.MAX_VALUE) {
    // Không thể phát hiện mọi overflow int đã xảy ra.
}
```

Nếu `left + right` đã thành giá trị âm do overflow, điều kiện `total > Integer.MAX_VALUE` có thể `false` dù tổng toán học thực sự vượt cực đại.

### Phép nhân và biểu thức tổng hợp

```java
int quantity = 50_000;
int unitPrice = 50_000;

long wrong = quantity * unitPrice;        // -1794967296
long right = (long) quantity * unitPrice; // 2500000000
```

Ngay cả khi tổng được tích lũy bằng `long`, phần tử đầu vào của tổng có thể đã sai:

```java
long total = 0L;
total += quantity * unitPrice;          // Nhân int trước, rồi cộng long: sai.
total += (long) quantity * unitPrice;   // Phép nhân thứ hai chính xác trong long.
```

Đoạn này minh họa thứ tự đánh giá; nó không phải cách tính tổng đúng vì `total` đã chứa kết quả sai từ dòng đầu. Trong chương trình thật, chỉ sử dụng biểu thức có phép nâng kiểu sớm. Cần phân tích **từng phép cộng/nhân**, đặc biệt trong lambda, mapper và collector.

### `byte` và `short` cũng cần chú ý

Phép toán `byte + byte` hay `short + short` được **nâng kiểu lên `int`** trước khi cộng; tuy nhiên khi ép kết quả trở lại kiểu nhỏ, nó có thể mất dữ liệu:

```java
byte a = 120;
byte b = 20;
int safeInt = a + b;     // 140
byte wrapped = (byte) (a + b); // -116
```

Ở đây không có overflow của phép cộng `int`; lỗi xảy ra ở bước **narrowing conversion** từ `int` về `byte`. Đây là lý do cần phân biệt overflow của phép tính với mất dữ liệu khi chuyển kiểu.

## 3. Kiểm tra overflow **ngay tại phép tính**

Khi hợp đồng yêu cầu lỗi phải được phát hiện ở từng phép toán nguyên, dùng các API **exact** trong `java.lang.Math`:

```java
int checkedAdd = Math.addExact(a, b);
int checkedSub = Math.subtractExact(a, b);
int checkedMul = Math.multiplyExact(a, b);

long checkedLong = Math.addExact(longA, longB);
```

Các biến ở trên là biến minh họa cần khai báo trong ngữ cảnh ứng dụng. `Math.addExact`, `subtractExact`, `multiplyExact` ném `ArithmeticException` khi kết quả vượt miền của kiểu tham số/kết quả tương ứng.

Ví dụ:

```java
try {
    int sum = Math.addExact(Integer.MAX_VALUE, 1);
    System.out.println(sum);
} catch (ArithmeticException ex) {
    System.out.println("Overflow during int addition");
}
```

Output:

```text
Overflow during int addition
```

Phương thức `Math.addExact(int, int)` kiểm tra phép cộng `int` khi nó diễn ra. Nếu đã viết `int bad = a + b;` rồi sau đó mới gọi `Math.addExact(bad, 0)`, phép kiểm tra **không** thể biết một phép tính trước đó đã overflow.

### Khi có thể nâng kiểu rồi chỉ kiểm tra tổng cuối

Một hợp đồng khác là “phép tính cuối cùng phải vừa `int`”, dù giá trị trung gian theo thứ tự thực hiện có thể vượt `int`. Khi tổng của một mảng `int[]` được thực hiện trong `long`, có thể kiểm tra bằng:

```java
int[] values = {Integer.MAX_VALUE, 1, -1};
long exactSum = Arrays.stream(values).asLongStream().sum();
int result = Math.toIntExact(exactSum); // 2147483647
```

Đoạn trên cần `import java.util.Arrays;` và đặt trong phương thức. `Math.toIntExact` kiểm tra việc **thu hẹp kết quả cuối**. Nếu dùng `Math.addExact` để cộng từ trái sang phải trong `int`, phép cộng `Integer.MAX_VALUE + 1` sẽ ném ngoại lệ trước khi cộng `-1`.

Với `parallel stream`, nhóm các phép cộng có thể khác nhau; các phép kiểm tra overflow **từng bước** có thể gặp hoặc không gặp lỗi tùy các tổng trung gian, dù tổng cuối theo toán học vẫn như nhau. Nếu hợp đồng chỉ kiểm tra tổng cuối, hãy cộng trong miền đủ rộng (`long` khi có thể chứng minh đủ, hoặc `BigInteger`) trước khi chuyển kiểu.

## 4. Overflow và mất dữ liệu xảy ra **khi ép sang kiểu nhỏ**

Chuyển `long` sang `int` bằng cast không phải phép kiểm tra. Nó giữ các bit thấp của giá trị nguồn và có thể đổi cả dấu:

```java
long beyondInt = 2_147_483_648L;

int unsafe = (int) beyondInt; // -2147483648
```

Nếu muốn từ chối trường hợp ngoài miền, dùng:

```java
int checked = Math.toIntExact(beyondInt);
// Ném ArithmeticException.
```

Với trường hợp hợp lệ:

```java
int checked = Math.toIntExact(42L); // 42
```

`Math.toIntExact` kiểm tra **long → int**. Nó không cung cấp trực tiếp phiên bản `int → short` hoặc `int → byte`; cần kiểm tra biên rồi mới cast:

```java
static byte toByteExact(int value) {
    if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new ArithmeticException("outside byte range: " + value);
    }
    return (byte) value;
}
```

Đây là phương thức minh họa phải nằm trong một class. Với `short` thay bằng `Short.MIN_VALUE`/`Short.MAX_VALUE`. Với `char`, miền từ 0 đến `Character.MAX_VALUE` và không mang dấu. Không dùng `(byte)`, `(short)`, `(char)` như phép kiểm tra phạm vi.

### Bẫy kiểm tra **sau khi** cast

```java
long original = 2_147_483_648L;
int narrowed = (int) original;

// Luôn true, nên không phát hiện original đã nằm ngoài int:
boolean nowFitsInt = narrowed >= Integer.MIN_VALUE
        && narrowed <= Integer.MAX_VALUE;
```

Sau khi cast, `narrowed` **đương nhiên** là một `int`; so sánh nó với `Integer.MIN_VALUE` và `Integer.MAX_VALUE` không xác thực được giá trị ban đầu. Phải kiểm tra `original` **trước** khi ép hoặc dùng `Math.toIntExact(original)`.

Có thể kiểm tra round trip khi nguồn **chưa bị tràn**:

```java
long original = 2_147_483_648L;
int narrowed = (int) original;
boolean exact = (long) narrowed == original; // false
```

Mẫu round trip này có tác dụng cho phép chuyển `long → int`, nhưng `Math.toIntExact` làm mục đích kiểm tra rõ ràng hơn. **Nếu bản thân `original` được tạo từ một phép cộng `int` đã overflow**, round trip cũng không khôi phục được tổng toán học đã mất.

## 5. Chuyển đổi `BigInteger`/`BigDecimal`: kiểm tra cả miền lẫn phần thập phân

`BigInteger` có thể vượt giới hạn `long`. Các phương thức chuyển kiểu nguyên thông thường có thể làm mất các bit cao:

```java
BigInteger large = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);

long wrapped = large.longValue(); // Long.MIN_VALUE
```

Khi cần giữ nguyên toàn bộ giá trị số, dùng:

```java
long checked = large.longValueExact();
// Ném ArithmeticException vì large vượt miền long.
```

Các API chính xác gồm `BigInteger.intValueExact()`, `longValueExact()`, `shortValueExact()`, `byteValueExact()`. Không dùng `longValue()` rồi kiểm tra miền của biến `long` mới tạo; lúc đó phần lớn giá trị có thể đã mất.

Với `BigDecimal`, có **hai dạng mất dữ liệu** khi chuyển sang kiểu nguyên:

1. Giá trị vượt miền của kiểu đích.
2. Giá trị có phần thập phân khác 0 và phép chuyển sẽ cắt bỏ phần lẻ.

Ví dụ:

```java
BigDecimal decimal = new BigDecimal("12.5");

int truncated = decimal.intValue(); // 12, phần .5 bị bỏ.
int exact = decimal.intValueExact(); // ArithmeticException.
```

`BigDecimal.intValueExact()`/`longValueExact()` từ chối cả kết quả ngoài miền và kết quả không phải số nguyên. Nếu muốn làm tròn trước khi chuyển, **quy tắc làm tròn phải được xác định trước**:

```java
BigDecimal rounded = new BigDecimal("12.5")
        .setScale(0, RoundingMode.HALF_UP);
int value = rounded.intValueExact(); // 13
```

Ví dụ làm tròn cần import `java.math.RoundingMode`. `HALF_UP` chỉ là chính sách ví dụ; với phép tính tài chính, kết quả có thể đòi hỏi `HALF_EVEN` hoặc không cho phép làm tròn. Phân biệt rõ “giá trị vừa kiểu đích” với “chuyển đổi chính xác không mất phần thập phân”.

## 6. Chuyển số thực `double` sang số nguyên không giống overflow kiểu `int`

Một cast từ `double` sang `int`/`long` có hành vi khác hoàn toàn so với cast từ `long` sang `int`. Trong Java, chuyển narrowing từ `double` sang số nguyên trước hết làm tròn về 0; đối với `NaN` kết quả là 0; khi độ lớn vượt miền đích, kết quả **bão hòa ở giới hạn** của kiểu đích.

```java
(int) 12.9;                     // 12: bỏ phần lẻ.
(int) Double.NaN;               // 0.
(int) Double.POSITIVE_INFINITY; // Integer.MAX_VALUE.
(int) Double.NEGATIVE_INFINITY; // Integer.MIN_VALUE.
(long) Double.POSITIVE_INFINITY;// Long.MAX_VALUE.
```

Vì vậy, Java không tự ném lỗi khi cast giá trị `double` quá lớn sang `int`. Nếu hợp đồng chỉ cho phép số hữu hạn, trong phạm vi và không có phần lẻ, hãy kiểm tra **trước khi cast**:

```java
static int doubleToIntExact(double value) {
    if (!Double.isFinite(value)
            || value < Integer.MIN_VALUE
            || value > Integer.MAX_VALUE
            || value != Math.rint(value)) {
        throw new IllegalArgumentException("not an exact int: " + value);
    }
    return (int) value;
}
```

Đây là phương thức minh họa cần đặt trong class. `Math.rint(value)` trả số nguyên dạng `double` gần nhất, nhưng điều kiện `value != Math.rint(value)` trong đoạn này dùng để xác nhận **không có phần lẻ**. Nó không tự đảm bảo giá trị `double` đến từ nguồn thập phân ban đầu hoàn toàn chính xác.

Chuyển ngược từ `long` sang `double` cũng có thể mất độ chính xác mà không ném lỗi. Ví dụ `2^53 + 1` không thể biểu diễn chính xác dưới dạng `double`:

```java
long original = 9_007_199_254_740_993L;
double approximate = original; // Làm tròn thành 9007199254740992.0.
```

**Không dùng `(long) approximate == original` như kiểm chứng tổng quát** cho `long → double`: khi cast `double` lớn ngược lại về `long` có cơ chế bão hòa và giá trị khác có thể cho kết quả đánh lừa. Nếu hợp đồng đòi số nguyên chính xác, giữ ở `long`/`BigInteger`, hoặc dùng mô hình decimal phù hợp trước khi đi qua `double`.

### Tình huống JSON/JavaScript

Số nguyên `long` ở Java có thể vượt miền integer an toàn của JavaScript `Number` (`2^53 - 1`). Nếu truyền `long` lớn dưới dạng JSON number rồi đọc bằng JavaScript, độ chính xác có thể đã mất **trước khi Java nhận dữ liệu trả lại**. Khi API yêu cầu truyền số nguyên 64 bit chính xác qua client JavaScript, cần thiết kế schema, ví dụ biểu diễn dưới dạng chuỗi thập phân và parse/validate phù hợp ở hai đầu. Thử nghiệm `long` ở backend không đủ để khẳng định round trip JSON an toàn.

## 7. Phân biệt kiểm tra **đầu vào**, **từng phép tính** và **đầu ra**

Cùng một bài toán có thể có ba hợp đồng hợp lý nhưng hành vi rất khác nhau.

### Hợp đồng A: kiểm tra đầu vào, tính trong miền đã chứng minh đủ rộng

Nếu mỗi phần tử là `int` và đang cộng tất cả phần tử của một mảng `int[]` theo `long`, tổng toán học của một mảng như vậy nằm trong miền `long`. Dùng:

```java
long sum = Arrays.stream(values).asLongStream().sum();
```

Với một `IntStream` có thể sinh hơn số phần tử tối đa của một mảng, không tự động suy ra `long` luôn đủ. Cần biết giới hạn số phần tử và độ lớn từng phần tử trong hợp đồng.

### Hợp đồng B: ném lỗi nếu **bất kỳ phép tính trung gian nào** tràn

Dùng vòng lặp tuần tự với `Math.addExact`/`multiplyExact` trong kiểu đã chọn. Ví dụ:

```java
int total = 0;
for (int value : values) {
    total = Math.addExact(total, value);
}
```

Nếu tổng trung gian vượt `int`, ném lỗi tại thời điểm đó. Cách này phù hợp khi chính hợp đồng yêu cầu mọi bước đều nằm trong kiểu tích lũy.

### Hợp đồng C: tính tổng toán học chính xác, chỉ kiểm tra kiểu đầu ra

Tích lũy bằng `long` nếu đủ rộng, hoặc `BigInteger` nếu không thể chứng minh `long` đủ. Sau đó thu hẹp:

```java
BigInteger total = numbers.stream()
        .map(BigInteger::valueOf)
        .reduce(BigInteger.ZERO, BigInteger::add);

int result = total.intValueExact();
```

Mảnh mã này giả định `numbers` là `Stream<Long>`; nó không phải chương trình độc lập. Nhờ `BigInteger`, không có overflow trung gian ở độ rộng 32/64 bit. `intValueExact()` chỉ từ chối khi **tổng cuối** vượt miền `int`.

**Quan trọng:** `Math.toIntExact` đặt ở cuối không giúp sửa những phép toán `int` hoặc `long` đã overflow trước đó. Nó kiểm tra kết quả nó thực sự nhận, chứ không suy ra kết quả toán học từng tồn tại.

## 8. Chương trình Java 17+ hoàn chỉnh để quan sát

Lưu thành tệp **`OverflowCastDemo.java`**. Chương trình chỉ ghi kết quả xác định, bắt ngoại lệ trong các ca kiểm tra để không phụ thuộc nguyên văn thông báo của JDK:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

public class OverflowCastDemo {
    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        int one = 1;
        long late = (long) (max + one);
        long early = (long) max + one;
        System.out.println("late widen = " + late);
        System.out.println("early widen = " + early);

        try {
            Math.addExact(max, one);
            System.out.println("checked add = valid");
        } catch (ArithmeticException ex) {
            System.out.println("checked add = overflow");
        }

        long beyondInt = (long) max + one;
        System.out.println("narrow cast = " + (int) beyondInt);
        try {
            Math.toIntExact(beyondInt);
            System.out.println("toIntExact = valid");
        } catch (ArithmeticException ex) {
            System.out.println("toIntExact = overflow");
        }

        int left = 50_000;
        int right = 50_000;
        System.out.println("late multiply = " + (long) (left * right));
        System.out.println("early multiply = " + ((long) left * right));

        int[] values = {Integer.MAX_VALUE, 1};
        System.out.println("int sum = " + Arrays.stream(values).sum());
        System.out.println("long sum = "
                + Arrays.stream(values).asLongStream().sum());

        BigInteger beyondLong =
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        System.out.println("BigInteger longValue = " + beyondLong.longValue());
        try {
            beyondLong.longValueExact();
            System.out.println("BigInteger exact = valid");
        } catch (ArithmeticException ex) {
            System.out.println("BigInteger exact = overflow");
        }

        BigDecimal fractional = new BigDecimal("12.5");
        System.out.println("BigDecimal intValue = " + fractional.intValue());
        try {
            fractional.intValueExact();
            System.out.println("BigDecimal exact = valid");
        } catch (ArithmeticException ex) {
            System.out.println("BigDecimal exact = fractional");
        }

        System.out.println("NaN cast = " + (int) Double.NaN);
        System.out.println("infinity cast = "
                + (int) Double.POSITIVE_INFINITY);
    }
}
```

Biên dịch/chạy tại thư mục chứa tệp:

```bash
javac -encoding UTF-8 OverflowCastDemo.java
java OverflowCastDemo
```

Stdout:

```text
late widen = -2147483648
early widen = 2147483648
checked add = overflow
narrow cast = -2147483648
toIntExact = overflow
late multiply = -1794967296
early multiply = 2500000000
int sum = -2147483648
long sum = 2147483648
BigInteger longValue = -9223372036854775808
BigInteger exact = overflow
BigDecimal intValue = 12
BigDecimal exact = fractional
NaN cast = 0
infinity cast = 2147483647
```

Những cặp kết quả cần đối chiếu:

- `late widen` khác `early widen`: phép cộng đã tràn trước cast ở trường hợp đầu.
- `narrow cast` khác `toIntExact`: cast làm mất bit cao, còn exact conversion từ chối.
- `late multiply` khác `early multiply`: kiểu toán hạng quyết định phép nhân.
- `int sum` khác `long sum`: loại bộ tích lũy quyết định an toàn tổng.
- `BigInteger.longValue` khác `longValueExact`: một bên có thể cắt bit, bên kia kiểm tra.
- `BigDecimal.intValue` khác `intValueExact`: một bên có thể bỏ phần lẻ, bên kia từ chối.
- Cast `NaN`/vô cực sang `int` không phát sinh ngoại lệ.

## 9. Kiểm thử biên theo từng thời điểm thất bại

Để phát hiện lỗi thật thay vì chỉ kiểm tra “kết quả có vẻ hợp lý”, cần thiết kế ca kiểm thử đúng điểm dữ liệu có thể mất.

| Tình huống | Biểu thức/giá trị | Kết quả cần khẳng định |
| --- | --- | --- |
| Cộng tại biên trên `int` | `Integer.MAX_VALUE + 1` | `+` wrap; `Math.addExact` ném |
| Cộng tại biên dưới `int` | `Integer.MIN_VALUE - 1` | `-` wrap; `Math.subtractExact` ném |
| Nhân vượt `int` | `50_000 * 50_000` | `int` sai; nâng toán hạng thành `long` trước nhân |
| Nâng kiểu quá muộn | `(long) (max + 1)` | Kết quả âm đã bị tràn |
| Nâng kiểu đúng lúc | `(long) max + 1` | 2.147.483.648 |
| Thu hẹp ở biên trên | `(int) 2_147_483_648L` | Giá trị âm; `Math.toIntExact` ném |
| Thu hẹp hợp lệ | `Math.toIntExact(2_147_483_647L)` | Trả `Integer.MAX_VALUE` |
| Thu hẹp biên dưới | `Math.toIntExact(-2_147_483_649L)` | Ném |
| `BigInteger` vượt `long` | `Long.MAX_VALUE + 1` theo `BigInteger` | `longValueExact()` ném |
| Decimal có phần lẻ | `new BigDecimal("12.5")` | `intValue()` cắt; `intValueExact()` ném |
| `double` không hữu hạn | `NaN`, `+Infinity` | Cast có quy tắc đặc biệt, API chặt phải từ chối trước |
| Số nguyên lớn sang `double` | `9_007_199_254_740_993L` | Có thể mất độ chính xác |

### Một helper kiểm tra chuyển `int` sang `byte`

```java
static byte toByteExact(int input) {
    if (input < Byte.MIN_VALUE || input > Byte.MAX_VALUE) {
        throw new ArithmeticException("byte range exceeded");
    }
    return (byte) input;
}
```

Kiểm tra `127` thành công; `128`, `-129` bị từ chối. Nếu cast trước rồi mới kiểm tra `byte`, điều kiện về miền `byte` luôn đúng vì biến đã có kiểu `byte`.

### Một helper kiểm tra nhân và thu hẹp có chủ đích

```java
static int multiplyThenNarrow(int a, int b) {
    long product = (long) a * b;
    return Math.toIntExact(product);
}
```

Phương thức này tính sản phẩm hai `int` trong `long`, sau đó chỉ trả về `int` nếu sản phẩm cuối vừa kiểu. Với hai đối số `int`, tích toán học luôn nằm trong `long`, nên đây là lựa chọn phù hợp khi **chỉ cần kiểm tra kết quả cuối của phép nhân**. Nó không khác `Math.multiplyExact(a, b)` về việc chấp nhận hay từ chối khi chỉ có một phép nhân `int * int`, nhưng mô tả rõ ý tưởng “mở rộng rồi thu hẹp”.

Hai helper trên là **mã minh họa**, đặt trong một class để biên dịch.

## 10. Chẩn đoán lỗi đã xảy ra trong hệ thống

Nếu gặp số âm bất thường, tổng giảm khi thêm phần tử dương, hoặc chuyển kiểu ra giá trị lạ, nên quan sát tại **mỗi ranh giới số học**:

1. **Ghi nhận kiểu thực của toán hạng:** `int`, `long`, `BigInteger`, `BigDecimal`, `double`? Biểu thức trong lambda trả kiểu gì?
2. **Xác định phép tính mất dữ liệu đầu tiên:** trước cast hay tại cast? Xét cả phép nhân, cộng, trừ, dịch bit và tổng hợp.
3. **Tái hiện bằng giá trị ở biên:** `MAX_VALUE`, `MIN_VALUE`, `MAX_VALUE + 1` bằng kiểu rộng hơn, giá trị âm, giá trị có phần thập phân.
4. **Thay thế bằng phép kiểm tra chính xác ở đúng điểm:** `Math.addExact`, `Math.multiplyExact`, `Math.toIntExact`, `BigInteger.longValueExact`, `BigDecimal.intValueExact`.
5. **Xác định lại hợp đồng:** muốn lỗi khi trung gian tràn, hay chỉ khi kết quả cuối không vừa kiểu? Có cho phép làm tròn/cắt phần lẻ hay không?
6. **Kiểm tra các tầng lưu trữ/truyền tải:** kiểu cột SQL, JSON number qua JavaScript, parse từ chuỗi, API client. Một giá trị có thể đã mất chính xác trước khi vào phương thức đang debug.

Ví dụ, khi debug một mapping DTO có câu lệnh `(int) order.getTotalCents()`, hãy tìm kiểu `getTotalCents()` và miền giá trị thực tế. Nếu trả `long`, đổi sang `Math.toIntExact` sẽ làm lỗi lộ ra ngay tại ranh giới thay vì đưa một giá trị âm vào bộ phận phía sau. Nhưng nếu `getTotalCents()` đã tính bằng phép nhân `int * int` bị tràn, trước tiên phải sửa phép nhân ở nơi tạo giá trị.

**Log hữu ích** cần có giá trị đầu vào (trong giới hạn bảo mật), kiểu và đơn vị, tên phép tính, cùng tên trường đích; tránh chỉ ghi “invalid number” không đủ ngữ cảnh. Với luồng quan trọng, hãy kiểm thử kết quả số học chính xác so với `BigInteger` hoặc `BigDecimal` làm oracle, thay vì dùng chính phép tính nguyên thủy có thể tràn làm expected value.

## 11. Các lỗi thường gặp

- **“Gán sang `long` thì sẽ an toàn”**: sai nếu toán hạng của phép cộng/nhân vẫn đều là `int`.
- **“Cast sang `int` sẽ tự cảnh báo”**: sai; cast từ `long` sang `int` có thể cắt bit.
- **“Sau cast, giá trị vẫn trong miền `int` nên không overflow”**: điều hiển nhiên về kiểu của biến không chứng minh miền của giá trị trước cast.
- **“`Math.toIntExact` ở cuối đủ phát hiện mọi phép cộng sai”**: nó chỉ kiểm tra đối số `long` nhận được; không truy được overflow trước đó.
- **“`BigInteger.longValue()` giống `longValueExact()`”**: không; phương thức không có `Exact` có thể mất các bit cao.
- **“`BigDecimal.intValue()` luôn là chuyển đổi hợp lệ”**: nó có thể bỏ phần thập phân và mất giá trị ngoài miền.
- **“`(int) Double.NaN` phải ném exception”**: cast trả 0 theo quy tắc Java.
- **“Mọi `long` chuyển sang `double` đều chính xác”**: sai với các số lớn không biểu diễn được trong binary64.
- **“Kiểm thử vài số nhỏ là đủ”**: các lỗi chỉ lộ ở `MAX_VALUE`, `MIN_VALUE`, giá trị ngoài biên và phép tính trung gian.
- **“Dữ liệu sai có thể sửa bằng cast thêm một lần nữa”**: khi phép toán trước đã quấn vòng hoặc bị làm tròn, cast không phục hồi thông tin đã mất.

## 12. Quy tắc thực hành

Khi thiết kế hoặc kiểm thử phép tính số nguyên, hãy xác định miền tổng toán học **trước khi viết biểu thức**. Nếu kiểu nguyên thủy đủ, dùng kiểu đó và kiểm tra các phép tính nguy cơ tràn bằng `Math.*Exact` theo hợp đồng. Nếu kiểu hiện tại quá hẹp, nâng toán hạng **trước phép tính**. Nếu phải chuyển về kiểu nhỏ, dùng exact conversion hoặc kiểm tra giới hạn **trước cast**. Nếu tổng cuối vượt `long`, dùng `BigInteger`; nếu giá trị có phần thập phân, xác định rõ được làm tròn hay phải giữ chính xác trước khi chuyển sang số nguyên.

Một phép kiểm tra được đặt đúng thời điểm giữ cho lỗi xuất hiện ở nguồn gây ra. Một phép kiểm tra quá muộn chỉ kiểm tra **giá trị đã bị biến đổi**, không thể chứng minh kết quả ban đầu còn đúng.

### Tài liệu Java chính thức

- [JLS §5.1 — Primitive conversions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1)
- [JLS §15.18 — Additive operators](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.18)
- [`Math` — `addExact`, `multiplyExact`, `toIntExact`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [`BigInteger` — exact conversion](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html)
- [`BigDecimal` — exact conversion](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-003">Số âm với phép chia, lấy dư và làm tròn</a>

<details>
<summary>Xem chi tiết</summary>

# Số âm với phép chia, lấy dư và làm tròn trong Java

## 1. Vì sao số âm là một bài kiểm thử bắt buộc?

Với số dương, nhiều cách chia và làm tròn vô tình cho kết quả giống nhau. Khi xuất hiện số âm, những khác biệt trước đó trở nên rõ ràng:

- Phép chia nguyên `/` trong Java **cắt phần lẻ về 0** (*truncate toward zero*), không làm tròn xuống âm vô cực.
- Phép dư `%` là **remainder**, không nhất thiết là modulo không âm; nếu khác 0, nó có **cùng dấu với số bị chia**.
- `Math.floorDiv` và `Math.floorMod` áp dụng phép chia theo hướng **âm vô cực**, giúp tính các chu kỳ với mẫu số dương.
- Làm tròn `UP`/`DOWN` khác `CEILING`/`FLOOR`. Với số âm, chữ “up” không luôn có nghĩa là “giá trị số học lớn hơn”.
- `Math.round` và `BigDecimal.setScale(..., HALF_UP)` **không dùng cùng quy tắc khi gặp điểm giữa âm**.

Nếu phần mềm xử lý số dư tài khoản, ngày trước mốc tham chiếu, chỉ số vòng tròn, nhiệt độ âm, tiền hoàn trả, lợi nhuận âm hoặc sai số âm, chọn nhầm phép toán có thể tạo lỗi khó thấy ở bộ test chỉ có đầu vào dương.

Bài này thuộc nhóm **Kiểm thử và chẩn đoán**: ngoài giải thích công thức, mỗi phần chỉ ra cách dựng phản ví dụ, tính kết quả kỳ vọng và kiểm chứng hành vi bằng Java 17+.

## 2. Chia nguyên trong Java: cắt phần thập phân về 0

Toán tử `/` với hai toán hạng kiểu nguyên (`int`, `long` và các kiểu được nâng lên thành `int`) cho **thương nguyên được cắt về 0**. Đối với số âm, đây không phải luôn là giá trị floor.

```java
System.out.println( 7 /  3); //  2
System.out.println(-7 /  3); // -2
System.out.println( 7 / -3); // -2
System.out.println(-7 / -3); //  2
```

Ví dụ quan trọng: phép chia thực `-7 / 3 = -2.333...`, nhưng chia nguyên Java nhận `-2`, không phải `-3`.

| Biểu thức | Thương thực | `int / int` của Java | `floor(thương thực)` |
| --- | ---: | ---: | ---: |
| `7 / 3` | khoảng 2.333 | 2 | 2 |
| `-7 / 3` | khoảng -2.333 | -2 | -3 |
| `7 / -3` | khoảng -2.333 | -2 | -3 |
| `-7 / -3` | khoảng 2.333 | 2 | 2 |

Vì toán tử có các overload theo kiểu dữ liệu, `-7 / 3` và `-7.0 / 3` cũng không tương đương:

```java
System.out.println(-7 / 3);     // -2
System.out.println(-7.0 / 3);   // -2.3333333333333335
```

Câu hỏi chẩn đoán đầu tiên là: **Các toán hạng là số nguyên hay số dấu phẩy động?** Nếu phải có thương thực, dùng kiểu hoặc phương pháp số học phù hợp và đừng vô tình chia nguyên trước khi ép kiểu.

## 3. Toán tử % là remainder, không phải modulo không âm

Với toán hạng nguyên thông thường và mẫu số khác 0, Java xác định `q = a / b` bằng cách cắt thương về 0, sau đó:

```text
r = a - q * b
a = q * b + r
```

Toán tử `a % b` trả `r`. Khi `r != 0`, dấu của `r` giống dấu của **`a` (số bị chia)**, không phải dấu của `b`.

```java
System.out.println( 7 %  3); //  1
System.out.println(-7 %  3); // -1
System.out.println( 7 % -3); //  1
System.out.println(-7 % -3); // -1
```

Kiểm tra đẳng thức với `a = -7`, `b = 3`:

```text
q = -7 / 3 = -2
r = -7 % 3 = -1
q*b + r = (-2)*3 + (-1) = -7
```

Trong nhiều tài liệu toán học, **modulo với modulus dương** lại được quy ước nằm trong `[0, m)`. Do đó `-7 mod 3` thường được viết là `2`; Java `-7 % 3` lại là `-1`. Hai phép toán phục vụ các quy ước chia khác nhau.

Điều này làm xuất hiện bug phổ biến:

```java
int size = 5;
int previous = -1;
int index = previous % size; // -1, không thể dùng làm index!
```

Nếu nghiệp vụ cần một chỉ số vòng trong `[0, size)`, không dùng `%` một cách trực tiếp khi `previous` có thể âm. Dùng `Math.floorMod(previous, size)` sau khi kiểm tra `size > 0`.

## 4. Math.floorDiv và Math.floorMod: chia làm tròn xuống

Java cung cấp `Math.floorDiv(int, int)` / `Math.floorDiv(long, long)` và `Math.floorMod(...)`. Chúng dùng **thương lấy floor** thay cho thương cắt về 0.

```java
System.out.println(Math.floorDiv(-7, 3));  // -3
System.out.println(Math.floorMod(-7, 3));  //  2
System.out.println(Math.floorDiv(7, -3));  // -3
System.out.println(Math.floorMod(7, -3));  // -2
System.out.println(Math.floorDiv(-7, -3)); //  2
System.out.println(Math.floorMod(-7, -3)); // -1
```

Các giá trị thoả hệ thức:

```text
a = floorDiv(a,b) * b + floorMod(a,b)
```

với lưu ý riêng cho trường hợp tràn số nguyên ở phép chia `MIN_VALUE / -1` sẽ được bàn ở phần 10.

| `a` | `b` | `a / b` | `a % b` | `floorDiv(a,b)` | `floorMod(a,b)` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 3 | 2 | 1 | 2 | 1 |
| -7 | 3 | -2 | -1 | -3 | 2 |
| 7 | -3 | -2 | 1 | -3 | -2 |
| -7 | -3 | 2 | -1 | 2 | -1 |
| -6 | 3 | -2 | 0 | -2 | 0 |

**Với `b > 0`, `floorMod(a,b)` luôn nằm trong `[0,b)`.** Khi `b < 0`, kết quả khác 0 có dấu của `b`, tức có thể âm. Bởi vậy, nếu cần modulo **không âm**, phải bảo đảm modulus **dương**, không chỉ thay `%` bằng `floorMod` rồi bỏ qua validation.

### Tình huống 1: chỉ số tuần hoàn

```java
int numberOfSlots = 7;
int previousSlot = Math.floorMod(-1, numberOfSlots);
System.out.println(previousSlot); // 6
```

`-1` có thể biểu thị bước lùi một vị trí trước chỉ số 0. Kết quả 6 hợp lệ trong một mảng độ dài 7.

### Tình huống 2: chia thành các khoảng thời gian

Giả sử tính offset mili giây tương đối với một mốc, âm nghĩa là thời điểm trước mốc. Với mỗi đoạn dài `1_000 ms`:

```java
long offsetMillis = -1L;
long bucket = Math.floorDiv(offsetMillis, 1_000L);
long withinBucket = Math.floorMod(offsetMillis, 1_000L);

System.out.println(bucket);       // -1
System.out.println(withinBucket); // 999
```

Đây là ví dụ về cách phân ô thời gian: offset `-1 ms` nằm ở ô thời gian `-1` và vị trí `999 ms` bên trong ô đó. Nếu dùng `/` và `%`, sẽ ra bucket 0 và vị trí -1, không theo quy ước chia thành các khoảng `[k*1000, (k+1)*1000)`.

## 5. Phân biệt bốn hướng làm tròn với số âm

`java.math.RoundingMode` định nghĩa các chính sách áp dụng cho `BigDecimal`. Những tên dễ gây hiểu nhầm nhất là:

| Mode | Quy tắc | Ví dụ làm tròn `-1.26` thành 1 chữ số sau dấu phẩy |
| --- | --- | --- |
| `DOWN` | Hướng **về 0**: bỏ chữ số lẻ vượt scale | `-1.2` |
| `UP` | Hướng **ra xa 0** nếu có phần bị bỏ khác 0 | `-1.3` |
| `CEILING` | Hướng về **dương vô cực** | `-1.2` |
| `FLOOR` | Hướng về **âm vô cực** | `-1.3` |

Với số dương `1.26`, `UP` và `CEILING` cùng thành `1.3`; `DOWN` và `FLOOR` cùng thành `1.2`. **Với số âm, hai cặp này tách ra**. Đây là lý do cần test số dương và số âm khi viết chung một hàm rounding.

```java
BigDecimal x = new BigDecimal("-1.26");
System.out.println(x.setScale(1, RoundingMode.DOWN));    // -1.2
System.out.println(x.setScale(1, RoundingMode.UP));      // -1.3
System.out.println(x.setScale(1, RoundingMode.CEILING)); // -1.2
System.out.println(x.setScale(1, RoundingMode.FLOOR));   // -1.3
```

Các đoạn mã rời về `BigDecimal` trong bài cần `import java.math.BigDecimal;` và `import java.math.RoundingMode;`; chúng minh họa lệnh trong thân phương thức. Phần 11 có lớp Java đầy đủ, biên dịch và chạy độc lập.

### Các chế độ HALF có quy tắc riêng khi đúng điểm giữa

Với `-1.25` làm tròn còn 1 chữ số sau dấu phẩy, hai kết quả gần nhất là `-1.2` và `-1.3`. Giá trị gốc nằm chính giữa:

- `HALF_UP`: khi đúng điểm giữa, chọn **xa 0** → `-1.3`.
- `HALF_DOWN`: khi đúng điểm giữa, chọn **gần 0** → `-1.2`.
- `HALF_EVEN`: khi đúng điểm giữa, chọn chữ số còn giữ ở vị trí cuối là số **chẵn** → `-1.2` (chữ số 2 chẵn).

```java
BigDecimal x = new BigDecimal("-1.25");
System.out.println(x.setScale(1, RoundingMode.HALF_UP));   // -1.3
System.out.println(x.setScale(1, RoundingMode.HALF_DOWN)); // -1.2
System.out.println(x.setScale(1, RoundingMode.HALF_EVEN)); // -1.2
```

Đừng diễn giải `HALF_UP` là “đi về dương vô cực ở điểm .5”. Trong `BigDecimal`, với số âm, `HALF_UP` ở điểm giữa **đi ra xa 0**. Nếu bài toán thuế, phí hoặc hoàn tiền có yêu cầu làm tròn, chính sách phải được mô tả bằng tên mode và ranh giới áp dụng, thay vì chỉ viết “làm tròn lên”.

## 6. Math.round, floor, ceil, rint: khác rounding của BigDecimal

`java.lang.Math` cung cấp các phép toán trên `float`/`double`:

- `Math.floor(x)`: số nguyên lớn nhất **không lớn hơn** `x`, trả về `double`.
- `Math.ceil(x)`: số nguyên nhỏ nhất **không nhỏ hơn** `x`, trả về `double`.
- `Math.round(double)`: trả về `long`, chọn giá trị nguyên gần nhất; điểm đúng giữa được làm tròn về **dương vô cực** (tương ứng quy tắc `floor(x + 0.5)` cho các đầu vào hữu hạn thông thường).
- `Math.round(float)`: tương tự, nhưng trả `int`.
- `Math.rint(x)`: trả về `double` nguyên theo nghĩa số học; ở điểm giữa chọn số nguyên **chẵn**.

Thử các giá trị âm:

```java
System.out.println(Math.floor(-1.5)); // -2.0
System.out.println(Math.ceil(-1.5));  // -1.0
System.out.println(Math.round(-1.5)); // -1
System.out.println(Math.round(-2.5)); // -2
System.out.println(Math.rint(-1.5));  // -2.0
System.out.println(Math.rint(-2.5));  // -2.0
```

Vì `Math.round(-1.5)` cho `-1`, nó **khác** `BigDecimal("-1.5").setScale(0, HALF_UP)` cho `-2`:

```java
System.out.println(Math.round(-1.5)); // -1
System.out.println(new BigDecimal("-1.5")
        .setScale(0, RoundingMode.HALF_UP)); // -2
```

Lưu ý: `Math.round` trên các giá trị `NaN` và ±`Infinity` có xử lý đặc biệt, không phải phép chuyển chính xác tùy ý; số dấu phẩy động cũng có thể chứa `-0.0`. Đừng tự xây hợp đồng cho dữ liệu nhạy cảm bằng một câu “round to nearest” thiếu giải thích điểm giữa, kiểu trả về, miền và đầu vào đặc biệt.

### Quy tắc chọn API

- Muốn **chia chỉ số/bucket** với bội số nguyên và số âm: `floorDiv`/`floorMod`.
- Muốn **làm tròn một giá trị double** tới số nguyên với quy tắc Java của `round`: `Math.round`.
- Muốn **chính sách decimal explicit** về scale và rounding: `BigDecimal.setScale(scale, mode)`.
- Muốn **định dạng đầu ra** có số chữ số sau dấu chấm: tính và làm tròn theo chính sách trước, sau đó dùng `toPlainString()` hoặc formatter theo locale.

## 7. Chia và lấy dư với BigDecimal

`BigDecimal` không dùng toán tử `/` hay `%`. Nó cung cấp các phương thức có hợp đồng riêng:

| Phương thức | Ý nghĩa |
| --- | --- |
| `divide(divisor)` | Chia **chính xác** nếu có biểu diễn thập phân hữu hạn; nếu kết quả vô hạn như 1/3, có thể ném `ArithmeticException`. |
| `divide(divisor, scale, roundingMode)` | Chia và trả kết quả có scale yêu cầu, áp dụng mode khi cần. |
| `divideToIntegralValue(divisor)` | Lấy **phần nguyên của thương theo hướng về 0**, không phải floor với số âm. |
| `remainder(divisor)` | Lấy remainder theo thương nguyên cắt về 0; khác modulo không âm. |
| `divideAndRemainder(divisor)` | Trả về mảng gồm thương nguyên và remainder theo cùng quy ước. |

```java
BigDecimal a = new BigDecimal("-7");
BigDecimal b = new BigDecimal("3");

System.out.println(a.divideToIntegralValue(b)); // -2
System.out.println(a.remainder(b));             // -1
System.out.println(a.divide(b, 2, RoundingMode.HALF_UP)); // -2.33
```

Với `BigDecimal.divideToIntegralValue`, cách biểu diễn `scale` của thương có thể phụ thuộc đầu vào và phép toán; hãy xét trị số và hợp đồng biểu diễn riêng. Phép `remainder` với số bị chia âm có thể trả giá trị âm, bởi nó **không giống `Math.floorMod`**.

Nếu một ứng dụng thực sự yêu cầu **modulo không âm trên decimal với modulus dương**, có thể chuẩn hóa remainder:

```java
static BigDecimal positiveModulo(BigDecimal value, BigDecimal modulus) {
    if (modulus.signum() <= 0) {
        throw new IllegalArgumentException("modulus must be positive");
    }
    BigDecimal rem = value.remainder(modulus);
    return rem.signum() < 0 ? rem.add(modulus) : rem;
}
```

Đây là **phương thức minh họa**, cần đặt trong một lớp Java. Ví dụ `positiveModulo(new BigDecimal("-7.5"), new BigDecimal("3"))` cho `1.5`. Không cần `Math.floorMod` vì API đó chỉ nhận số nguyên primitive.

### Chia BigDecimal cũng có thể mất chính xác theo rounding mode

`-7 / 3` có decimal vô hạn. Phép:

```java
BigDecimal q = new BigDecimal("-7")
        .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
System.out.println(q); // -2.33
```

được làm tròn hai chữ số sau dấu chấm. Đây là **làm tròn có chủ đích**, không phải đồng nghĩa với chia nguyên `-7 / 3 = -2`. Nếu không được phép bỏ chữ số, `RoundingMode.UNNECESSARY` giúp phát hiện giá trị không thể biểu diễn ở scale được yêu cầu bằng cách ném `ArithmeticException`.

Với `BigDecimal`, không sử dụng `new BigDecimal(0.1)` nếu mong chính xác decimal `0.1`; tạo từ chuỗi hoặc nguồn decimal chính xác. Dù dữ liệu gốc âm hay dương, sai số `double` đầu vào không được tự sửa nhờ một lần `setScale`.

## 8. Quy tắc làm tròn âm trong nghiệp vụ

Không có một chính sách rounding đúng cho mọi bài toán. Hãy xác định:

1. **Tọa độ/giá trị mang dấu có ý nghĩa gì?** Số dư âm, độ dịch chuyển, hoàn tiền, nợ, sai lệch hay số ngày trước mốc?
2. **Muốn chia phân đoạn theo trục số nào?** Nếu bucket `[kL, (k+1)L)`, dùng `floorDiv`; nếu loại bỏ phần lẻ của chỉ số về 0, dùng `/`.
3. **Muốn remainder giữ dấu số bị chia hay thuộc khoảng không âm?** `%` và `floorMod` không giống nhau với đầu vào âm.
4. **Rounding theo hướng nào?** Hướng về 0 (`DOWN`), xa 0 (`UP`), dương vô cực (`CEILING`), âm vô cực (`FLOOR`), hay gần nhất kèm quy tắc điểm giữa?
5. **Làm tròn ở ranh giới nào?** Từng dòng, từng giao dịch, tổng cuối hoặc chỉ lúc xuất dữ liệu?
6. **Độ chính xác nào phải được giữ?** Decimal chính xác, đo lường gần đúng hay floating-point theo chuẩn số học?

Ví dụ: giá trị hoàn tiền `-1.25` được làm tròn về một chữ số. Nếu quy định “làm tròn gần nhất, điểm giữa ra xa 0”, `HALF_UP` cho `-1.3`; nếu quy định “làm tròn gần nhất, điểm giữa lấy số chẵn”, `HALF_EVEN` cho `-1.2`. Chênh lệch `0.1` có thể mang ý nghĩa kế toán. Không diễn giải từ tên “HALF_UP” mà bỏ kiểm tra thực tế với âm.

Đối với số tiền, scale cuối và mode cần được quy định theo loại tiền, luật hoặc hợp đồng nghiệp vụ. Không tự giả định hai chữ số thập phân áp dụng được cho mọi currency. Với đo đạc, rounding hiển thị không làm giảm sai số vật lý của phép đo.

## 9. Những lỗi dễ bị che giấu bởi test chỉ có số dương

### Lỗi A — Chỉ số vòng bị âm

```java
int size = 5;
int result = -1 % size; // -1
// access array[result] có thể ném ArrayIndexOutOfBoundsException
int fixed = Math.floorMod(-1, size); // 4
```

**Test cần có:** `-1`, `-size`, `-size-1`, `0`, `size-1`, `size` và `size+1`, đồng thời validate `size > 0`.

### Lỗi B — Dùng / thay floorDiv cho thời điểm trước epoch

```java
System.out.println(-1 / 1000);                 // 0
System.out.println(Math.floorDiv(-1, 1000));   // -1
```

Nếu code phân chia thành các bucket 1000 đơn vị bắt đầu ở mốc 0, bucket của `-1` phải là `-1` chứ không phải bucket 0. Với ngày/giờ thực tế, ưu tiên `java.time` khi nghiệp vụ có múi giờ, lịch và quy tắc DST; ví dụ ở đây chỉ nói về phân đoạn số học.

### Lỗi C — Nhầm HALF_UP với Math.round

```java
System.out.println(Math.round(-1.5)); // -1
System.out.println(new BigDecimal("-1.5")
        .setScale(0, RoundingMode.HALF_UP)); // -2
```

Chúng khác nhau ở điểm giữa âm. Đọc yêu cầu thực sự thay vì tự thay API với niềm tin rằng hai lời gọi “làm tròn” tương đương.

### Lỗi D — Tái sử dụng modulus âm

```java
System.out.println(Math.floorMod(-7, 3));  //  2
System.out.println(Math.floorMod(-7, -3)); // -1
```

Nếu đích là chỉ số từ 0 đến `size - 1`, phải kiểm tra `size > 0`. Một phép `floorMod` đúng về mặt Java có thể vẫn sai về miền nghiệp vụ khi modulus âm.

### Lỗi E — Dấu âm biến mất sau làm tròn về 0

```java
BigDecimal small = new BigDecimal("-0.004");
System.out.println(small.setScale(2, RoundingMode.HALF_UP)); // 0.00
```

Đó là giá trị số học 0 theo scale 2, không phải chứng minh đầu vào đã dương. Nếu nghiệp vụ cần giữ **dấu gốc** hoặc phân biệt “âm nhưng bị làm tròn về 0”, phải lưu thông tin đó riêng; `BigDecimal` không lưu số 0 âm như một giá trị có dấu riêng. Ngược lại, `double` có `-0.0` theo IEEE 754 nên logic so sánh và serialize cần được thiết kế rõ nếu phụ thuộc dấu của zero.

## 10. Những cạnh nguy hiểm: chia 0, overflow, MIN_VALUE và floating-point

### 10.1. Phép chia nguyên cho 0

`int` và `long` chia hoặc lấy dư cho 0 đều ném `ArithmeticException` khi chạy:

```java
int zero = 0;
// System.out.println(7 / zero); // ArithmeticException: / by zero
// System.out.println(7 % zero); // ArithmeticException: / by zero
```

`Math.floorDiv` và `Math.floorMod` cũng không nhận divisor 0. Hãy validate trước khi chia, đặc biệt khi modulus là kích thước collection hoặc độ dài bucket do người dùng cung cấp.

### 10.2. Trường hợp Integer.MIN_VALUE / -1

`Integer.MIN_VALUE` là `-2147483648`. Giá trị toán học của thương khi chia `-1` là `2147483648`, vượt miền `int`. Trong Java, phép chia nguyên đặc biệt này **không ném exception**; nó trả lại `Integer.MIN_VALUE` vì overflow. `Math.floorDiv` không tự sửa được giới hạn kiểu:

```java
int min = Integer.MIN_VALUE;
System.out.println(min / -1);             // -2147483648
System.out.println(min % -1);             // 0
System.out.println(Math.floorDiv(min, -1)); // -2147483648
System.out.println(Math.floorMod(min, -1)); // 0
```

Nếu cần thương toán học chính xác, **mở rộng kiểu trước khi chia**:

```java
long exact = (long) Integer.MIN_VALUE / -1L;
System.out.println(exact); // 2147483648
```

Với `Long.MIN_VALUE / -1L`, ngay cả `long` cũng không chứa thương; cân nhắc `BigInteger` hoặc kiểm tra overflow tường minh. Đừng vô tình gọi `Math.abs(Integer.MIN_VALUE)` để làm modulus dương: giá trị trả về vẫn âm vì trị tuyệt đối toán học vượt miền `int`.

### 10.3. Floating-point / và % không giống integer

`double` chia 0 không dùng cùng quy tắc ngoại lệ như số nguyên: theo IEEE 754, `1.0 / 0.0` là dương vô cực, còn `0.0 / 0.0` là `NaN`. Phép `%` với toán hạng floating point theo quy tắc remainder kiểu Java, không phải `Math.floorMod`, và không phải toán tử `Math.IEEEremainder`:

```java
System.out.println(-7.5 % 3.0); // -1.5
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(0.0 / 0.0);  // NaN
```

Trong xử lý số học thực tế, phải nêu rõ việc cho phép `NaN`/vô cực, quy tắc chuyển về decimal, cũng như sai số nhị phân của các giá trị không biểu diễn chính xác bằng `double`. Không tạo modulo không âm cho số thực bằng cách ép `(int) (a % b)` tùy tiện: conversion làm mất phần lẻ và có thể sai hoàn toàn với miền lớn.

## 11. Ví dụ Java 17+ hoàn chỉnh với stdout xác định

Lưu chương trình sau vào **`NegativeArithmeticDemo.java`**. Nó không phụ thuộc locale và chỉ in các kết quả dự đoán được.

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NegativeArithmeticDemo {

    static BigDecimal positiveModulo(BigDecimal value,
                                     BigDecimal modulus) {
        if (modulus.signum() <= 0) {
            throw new IllegalArgumentException("modulus must be > 0");
        }
        BigDecimal rem = value.remainder(modulus);
        return rem.signum() < 0 ? rem.add(modulus) : rem;
    }

    public static void main(String[] args) {
        System.out.println("-7 / 3 = " + (-7 / 3));
        System.out.println("-7 % 3 = " + (-7 % 3));
        System.out.println("7 / -3 = " + (7 / -3));
        System.out.println("7 % -3 = " + (7 % -3));
        System.out.println("-7 / -3 = " + (-7 / -3));
        System.out.println("-7 % -3 = " + (-7 % -3));

        System.out.println("floorDiv(-7, 3) = "
                + Math.floorDiv(-7, 3));
        System.out.println("floorMod(-7, 3) = "
                + Math.floorMod(-7, 3));
        System.out.println("floorDiv(7, -3) = "
                + Math.floorDiv(7, -3));
        System.out.println("floorMod(7, -3) = "
                + Math.floorMod(7, -3));
        System.out.println("ring previous = "
                + Math.floorMod(-1, 5));

        System.out.println("round(-1.5) = " + Math.round(-1.5));
        System.out.println("round(-2.5) = " + Math.round(-2.5));
        System.out.println("floor(-1.5) = " + Math.floor(-1.5));
        System.out.println("ceil(-1.5) = " + Math.ceil(-1.5));
        System.out.println("rint(-1.5) = " + Math.rint(-1.5));

        BigDecimal negative = new BigDecimal("-1.25");
        System.out.println("HALF_UP = "
                + negative.setScale(1, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN = "
                + negative.setScale(1, RoundingMode.HALF_EVEN));
        System.out.println("DOWN = "
                + negative.setScale(1, RoundingMode.DOWN));
        System.out.println("UP = "
                + negative.setScale(1, RoundingMode.UP));
        System.out.println("FLOOR = "
                + negative.setScale(1, RoundingMode.FLOOR));
        System.out.println("CEILING = "
                + negative.setScale(1, RoundingMode.CEILING));

        BigDecimal a = new BigDecimal("-7");
        BigDecimal b = new BigDecimal("3");
        System.out.println("decimal quotient = "
                + a.divideToIntegralValue(b));
        System.out.println("decimal remainder = " + a.remainder(b));
        System.out.println("decimal division = "
                + a.divide(b, 2, RoundingMode.HALF_UP));
        System.out.println("positive decimal modulo = "
                + positiveModulo(new BigDecimal("-7.5"), b));

        System.out.println("MIN / -1 = "
                + (Integer.MIN_VALUE / -1));
        System.out.println("wide MIN / -1 = "
                + ((long) Integer.MIN_VALUE / -1L));
    }
}
```

Chạy bằng JDK 17 trở lên, trong thư mục chứa file:

```bash
javac NegativeArithmeticDemo.java
java NegativeArithmeticDemo
```

Đầu ra kỳ vọng:

```text
-7 / 3 = -2
-7 % 3 = -1
7 / -3 = -2
7 % -3 = 1
-7 / -3 = 2
-7 % -3 = -1
floorDiv(-7, 3) = -3
floorMod(-7, 3) = 2
floorDiv(7, -3) = -3
floorMod(7, -3) = -2
ring previous = 4
round(-1.5) = -1
round(-2.5) = -2
floor(-1.5) = -2.0
ceil(-1.5) = -1.0
rint(-1.5) = -2.0
HALF_UP = -1.3
HALF_EVEN = -1.2
DOWN = -1.2
UP = -1.3
FLOOR = -1.3
CEILING = -1.2
decimal quotient = -2
decimal remainder = -1
decimal division = -2.33
positive decimal modulo = 1.5
MIN / -1 = -2147483648
wide MIN / -1 = 2147483648
```

Cần quan sát bốn nhóm kết quả:

1. `/` và `%` theo quy tắc **truncation toward zero**, nên `-7 / 3 = -2` và `-7 % 3 = -1`.
2. `floorDiv` và `floorMod` cùng dùng thương floor, nên `-7` chia theo ô kích thước 3 thành thương `-3` và phần dư `2`.
3. `Math.round(-1.5)` trả `-1` còn `HALF_UP` decimal ở điểm giữa âm làm tròn ra xa 0 thành `-2` nếu scale 0; chương trình thể hiện sự khác biệt tương ứng ở scale 1 cho `-1.25`.
4. `Integer.MIN_VALUE / -1` không ném exception dù thương toán học vượt kiểu `int`; nới rộng kiểu trước khi chia để nhận thương chính xác.

## 12. Viết test chẩn đoán theo bất biến thay vì kiểm tra vài ví dụ

Ngoài test đầu ra cố định, có thể viết các kiểm tra **bất biến** cho hàng loạt cặp số:

- Với `b != 0` và tránh trường hợp overflow phép chia đặc biệt, `a == (a / b) * b + (a % b)`.
- Với `b > 0`, `Math.floorMod(a, b)` luôn thỏa `0 <= r < b`.
- Với `b > 0` và `a < 0` không chia hết `b`, thương floor nhỏ hơn thương trunc đúng 1.
- Với `BigDecimal`, kiểm tra `divideToIntegralValue` và `remainder` tái tạo đúng `a` về **trị số**, dùng `compareTo() == 0` thay vì `equals()` nếu scale có thể khác.
- Với `RoundingMode`, so sánh âm và dương tại các điểm `±1.25`, `±1.26`, và quanh 0.

Một bộ test không cần thư viện ngoài, chạy độc lập dưới tên **`SignedDivisionChecks.java`**:

```java
public class SignedDivisionChecks {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        int cases = 0;
        for (int a = -20; a <= 20; a++) {
            for (int b = -7; b <= 7; b++) {
                if (b == 0) {
                    continue;
                }

                int quotient = a / b;
                int remainder = a % b;
                int floorQuotient = Math.floorDiv(a, b);
                int floorRemainder = Math.floorMod(a, b);

                check(a == quotient * b + remainder,
                        "Truncating identity failed");
                check(a == floorQuotient * b + floorRemainder,
                        "Floor identity failed");
                check(remainder == 0 ||
                        Integer.signum(remainder) == Integer.signum(a),
                        "Remainder sign failed");
                check(floorRemainder == 0 ||
                        Integer.signum(floorRemainder) == Integer.signum(b),
                        "Floor remainder sign failed");

                if (b > 0) {
                    check(floorRemainder >= 0 && floorRemainder < b,
                            "Nonnegative modulo range failed");
                }
                cases++;
            }
        }
        System.out.println("checked cases = " + cases);
        System.out.println("all properties passed");
    }
}
```

Chạy:

```bash
javac SignedDivisionChecks.java
java SignedDivisionChecks
```

Đầu ra:

```text
checked cases = 574
all properties passed
```

Tại đây có 41 giá trị `a` từ `-20` đến `20` và 14 giá trị `b` khác 0 từ `-7` đến `7`: `41 × 14 = 574` cặp. Miền nhỏ tránh tràn ở tích `quotient * b`; nếu viết property test trên toàn miền `int`, phải xử lý riêng `Integer.MIN_VALUE / -1` và overflow của phép tính kiểm tra, ví dụ bằng `long` hoặc `BigInteger`.

Test này không thay cho kiểm thử theo đặc tả nghiệp vụ. Nó chỉ kiểm tra các quy tắc số học mà chương trình dựa vào.

## 13. Ma trận test tối thiểu cho số âm

Một bộ test tốt phải cắt theo **dấu của số bị chia**, **dấu của mẫu số**, **chia hết hay không**, **điểm đúng giữa khi làm tròn** và **giá trị biên**:

| Nhóm | Đầu vào gợi ý | Mục tiêu |
| --- | --- | --- |
| Chia số dương | `7 / 3`, `7 % 3` | Mốc so sánh cơ bản. |
| Số bị chia âm | `-7 / 3`, `-7 % 3` | Phân biệt trunc và floor, remainder âm. |
| Mẫu số âm | `7 / -3`, `7 % -3` | Kiểm tra dấu quotient và remainder. |
| Cả hai âm | `-7 / -3`, `-7 % -3` | Không mặc định remainder cùng dấu divisor. |
| Chia hết | `-6 / 3`, `-6 % 3` | Remainder bằng 0. |
| Sát 0 | `-1 / 3`, `-1 % 3` | Phát hiện bucket sai gần mốc. |
| Modulo tuần hoàn | `-1`, `-5`, `-6` với modulus 5 | Giữ index trong `[0, 5)`. |
| Modulus âm | `floorMod(-7, -3)` | Không tự cho rằng modulo luôn không âm. |
| Số âm ở điểm giữa | `-1.25` về scale 1, `-1.5` về số nguyên | Phân biệt HALF_UP, HALF_EVEN, Math.round. |
| Phần lẻ nhỏ âm | `-0.004` về scale 2 | Rounding thành zero và chuyện lưu dấu gốc. |
| Mẫu số bằng 0 | `7 / 0`, `floorMod(7,0)` | Xử lý lỗi một cách có chủ đích. |
| Overflow chia nguyên | `Integer.MIN_VALUE / -1` | Phát hiện thương toán học vượt miền. |
| Decimal không kết thúc | `-7 / 3` trong BigDecimal | Chọn scale và rounding mode rõ. |
| Floating point đặc biệt | `-0.0`, `NaN`, ±`Infinity` | Xác định chính sách dữ liệu đầu vào/đầu ra. |

### Lộ trình chẩn đoán bug ngoài thực tế

Khi log cho thấy một kết quả âm “không đúng ý”:

1. **Ghi lại kiểu số** của mọi toán hạng: `int`, `long`, `double`, `BigDecimal`?
2. **Ghi lại quy tắc thương** kỳ vọng: trunc về 0 hay floor về âm vô cực?
3. **Ghi lại ràng buộc remainder** kỳ vọng: có được âm không, modulus có luôn dương không?
4. **Ghi lại chính sách rounding**: scale, mode và thời điểm áp dụng.
5. **Tái hiện bằng ca nhỏ nhất**: `-7 / 3`, `-1 % 5`, `-1.25`.
6. **Kiểm tra giá trị biên**: chia 0, overflow, số 0 âm, độ chính xác decimal/floating point.
7. **Bổ sung regression test** từ ca đã tái hiện để thay đổi code về sau không tái gây lỗi.

Nếu thay một phép `%` bằng `floorMod`, phải kiểm thử cả dữ liệu dương cũ, dữ liệu âm mới và mọi đường dẫn mà modulus bằng 0 hoặc âm. Nếu thay `Math.round` bằng `BigDecimal.setScale`, phải kiểm thử chính sách điểm giữa và conversion trước khi coi đó là một refactor “không đổi hành vi”.

## 14. Các nhầm lẫn thường gặp

1. **“`-7 / 3` là `-3`.”** Sai đối với chia `int` Java; kết quả là `-2`. `Math.floorDiv(-7,3)` mới là `-3`.
2. **“`a % b` luôn không âm.”** Sai khi `a` âm. `-7 % 3 == -1`.
3. **“`Math.floorMod` luôn dương.”** Không đúng khi divisor âm; `floorMod(7,-3) == -2`.
4. **“`Math.round` tương đương `HALF_UP`.”** Không ở điểm giữa âm; `round(-1.5) == -1` trong khi `HALF_UP` decimal về số nguyên là `-2`.
5. **“`UP` nghĩa là càng lớn trên trục số.”** Không. `RoundingMode.UP` là **xa 0**; với âm nó có thể làm số **âm hơn**.
6. **“`DOWN` nghĩa là âm vô cực.”** Không. `DOWN` là **về 0**; `FLOOR` mới về âm vô cực.
7. **“BigDecimal.remainder là modulo không âm.”** Sai; với dividend âm, remainder có thể âm.
8. **“Phép chia nguyên luôn phát hiện overflow.”** Sai ở `MIN_VALUE / -1`.
9. **“Mọi phép chia cho 0 trong Java đều ném ngoại lệ.”** Sai với `double`/`float` trong các trường hợp IEEE 754 như `1.0/0.0`.
10. **“Có thể dùng `Math.abs(size)` để đảm bảo modulus dương.”** Nguy hiểm với `Integer.MIN_VALUE`/`Long.MIN_VALUE`; phải validate miền và xử lý ngoại lệ biên.
11. **“Một vài test số dương chứng minh modulo hoạt động đúng.”** Không. Các test số âm mới thường bộc lộ khác biệt trunc và floor.
12. **“Scale và rounding mode chỉ ảnh hưởng hiển thị.”** Không. Nếu áp dụng chúng vào giá trị trung gian của công thức, kết quả cuối có thể thay đổi.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Java trả gì cho `-8 / 3` và `-8 % 3`?

**Đáp án:** `-2` và `-2` vì thương nguyên bị cắt về 0: `-8 = (-2)*3 + (-2)`.

**Câu 2.** `Math.floorDiv(-8, 3)` và `Math.floorMod(-8, 3)` là gì?

**Đáp án:** `-3` và `1` vì `-8 = (-3)*3 + 1`.

**Câu 3.** Khi modulus là `5`, vì sao index `-1 % 5` sai cho mảng?

**Đáp án:** Nó là `-1`. `Math.floorMod(-1,5)` cho `4`, một index trong `[0,5)`.

**Câu 4.** `Math.floorMod(8, -3)` có phải số không âm không?

**Đáp án:** Không; kết quả `-1` do floor quotient `-3` và `8 = (-3)*(-3) + (-1)`. Với modulus âm, floor remainder có thể âm.

**Câu 5.** Cho `BigDecimal("-1.25")`, làm tròn còn một chữ số với `HALF_UP` và `HALF_EVEN` được bao nhiêu?

**Đáp án:** `-1.3` và `-1.2`.

**Câu 6.** Vì sao `Math.round(-2.5)` là `-2`?

**Đáp án:** `Math.round` chọn điểm giữa về phía dương vô cực; giữa `-3` và `-2` nó chọn `-2`.

**Câu 7.** `Math.floor(-1.2)` và `BigDecimal("-1.2").setScale(0, DOWN)` là gì?

**Đáp án:** `Math.floor(-1.2) == -2.0`, còn `DOWN` về 0 cho `-1`.

**Câu 8.** Với `BigDecimal("-7")` chia `BigDecimal("3")`, `divideToIntegralValue` và `remainder` là gì?

**Đáp án:** Trị số `-2` và `-1`. Chúng dùng quotient cắt về 0, không phải quotient floor.

**Câu 9.** Tại sao `Integer.MIN_VALUE / -1` nguy hiểm dù divisor khác 0?

**Đáp án:** Thương toán học `2147483648` không nằm trong miền `int`; phép chia Java trả lại `Integer.MIN_VALUE` do overflow. Hãy nới kiểu trước khi chia hoặc kiểm tra rõ.

**Câu 10.** Muốn test hàm làm tròn dùng cho số âm, những nhóm ca nào bắt buộc có?

**Đáp án:** Dương/âm, gần 0, đúng midpoint và sát hai phía midpoint, nhiều rounding mode, giá trị quá lớn/quá nhỏ, và thứ tự làm tròn trong pipeline.

## 16. Tham khảo API Java 17

- [Java Language Specification `15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17): quy tắc `/`, `%` của kiểu nguyên và floating-point, chia 0 và overflow.
- [Java SE 17 — Math.floorDiv, floorMod, round](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): phép chia theo floor, modulo, rounding và hành vi biên.
- [Java SE 17 — RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): định nghĩa `UP`, `DOWN`, `CEILING`, `FLOOR` và các chế độ `HALF_*`.
- [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `divide`, `divideToIntegralValue`, `remainder`, `setScale` và `MathContext`.

**Ghi nhớ:** Với số âm, trước hết phải chọn **ngữ nghĩa thương** (trunc hay floor), rồi chọn **quy ước phần dư** (`%` hay `floorMod` với modulus dương), sau đó mới chọn **quy tắc làm tròn** cho thập phân. Đừng coi `Math.round`, `RoundingMode.HALF_UP`, `Math.floorDiv` và `BigDecimal.remainder` là các cách viết thay thế cho nhau. Các phản ví dụ nhỏ như `-7 / 3`, `-1 % 5` và `-1.25` sẽ bắt được đa số sai lầm từ rất sớm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-004">NaN, vô cực, signed zero và subnormal</a>

<details>
<summary>Xem chi tiết</summary>

# NaN, vô cực, signed zero và subnormal trong Java

## 1. Vì sao phải hiểu các giá trị đặc biệt?

`float` và `double` của Java biểu diễn số dấu phẩy động nhị phân theo các quy tắc IEEE 754. Ngoài những số hữu hạn thông thường, chúng có **NaN** (*Not a Number*), **vô cực dương/âm**, **số 0 có dấu** (+0.0 và -0.0), và **số subnormal** (số hữu hạn khác 0 có độ lớn nhỏ hơn số normal dương nhỏ nhất).

Những giá trị này ảnh hưởng trực tiếp tới phép chia, so sánh, sắp xếp, thống kê, kiểm thử, định dạng, lưu trữ và xử lý dữ liệu từ API. Có thể gặp kết quả trông “hợp lệ” theo kiểu `double` nhưng không phù hợp với ràng buộc nghiệp vụ.

Các điểm phân biệt ngay từ đầu:

| Trường hợp | Ví dụ | Tính chất |
| --- | --- | --- |
| NaN | `0.0 / 0.0` | Không phải một giá trị số có thứ tự thông thường |
| Vô cực dương | `1.0 / 0.0` | Lớn hơn mọi số hữu hạn khi so sánh dấu phẩy động |
| Vô cực âm | `-1.0 / 0.0` | Nhỏ hơn mọi số hữu hạn khi so sánh dấu phẩy động |
| Dương không | `+0.0` | Số 0 có bit dấu dương |
| Âm không | `-0.0` | Số 0 có bit dấu âm; có thể cho kết quả khác trong vài phép toán |
| Subnormal dương nhỏ nhất | `Double.MIN_VALUE` | Số dương hữu hạn nhỏ nhất khác 0 của `double` |

**Cảnh báo tên hằng:** `Double.MIN_VALUE` **không** phải số âm hữu hạn nhỏ nhất. Nó là `4.9E-324`, một số dương subnormal. Giá trị hữu hạn âm lớn về độ lớn gần đáy miền biểu diễn là `-Double.MAX_VALUE`. Ngưỡng số *normal* dương nhỏ nhất là `Double.MIN_NORMAL`.

## 2. Nền tảng biểu diễn double và float

Một giá trị `double` có 64 bit, thường được mô tả thành 1 bit dấu, 11 bit exponent và 52 bit fraction. `float` có 32 bit với 1 bit dấu, 8 bit exponent và 23 bit fraction. Số normal sử dụng significand với một bit dẫn ngầm, tạo độ chính xác tương ứng khoảng 53 và 24 bit nhị phân.

Mô hình ý tưởng:

```text
double = sign (1 bit) | exponent (11 bit) | fraction (52 bit)

Exponent thông thường + fraction → số normal hữu hạn
Exponent toàn bit 1, fraction = 0 → +Infinity hoặc -Infinity
Exponent toàn bit 1, fraction ≠ 0 → NaN
Exponent toàn bit 0, fraction = 0 → +0.0 hoặc -0.0
Exponent toàn bit 0, fraction ≠ 0 → số subnormal
```

`float` cũng dùng những nhóm biểu diễn tương tự, nhưng số bit khác nhau. Đây là cơ chế của **kiểu dấu phẩy động nhị phân**, không phải của `BigDecimal`. Không suy diễn bit layout này cho `BigInteger` hoặc kiểu nguyên.

Từ Java 17, ngữ nghĩa dấu phẩy động luôn nghiêm ngặt theo đặc tả Java; khai báo `strictfp` không còn cần thiết để có cùng ngữ nghĩa như trước. Dù vậy, các hàm thư viện toán học có hợp đồng độ chính xác riêng; không nên tự hứa hẹn rằng mọi phép tính đều đúng tuyệt đối như phép toán trên số thực.

## 3. NaN — kết quả của phép toán không xác định

NaN có nghĩa *Not a Number*. Ví dụ:

```java
double a = 0.0 / 0.0;
double b = Math.sqrt(-1.0);
double c = Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY;
double d = Double.POSITIVE_INFINITY * 0.0;
```

Cả bốn biến đều mang NaN trong Java. Đây là các đoạn lệnh minh họa, đặt trong phương thức mới chạy được.

**Phép chia hai số dấu phẩy động bằng 0.0 / 0.0 trả NaN**, chứ không ném `ArithmeticException` như phép chia số nguyên cho 0. Ví dụ `int x = 1 / 0;` có thể biên dịch, nhưng khi chạy ném `ArithmeticException`; với mẫu số là biến nguyên có giá trị 0, phép chia cũng ném ngoại lệ này. Với `double`, chia số hữu hạn khác 0 cho ±0.0 thường cho vô cực có dấu.

### 3.1. So sánh primitive với NaN

NaN không có quan hệ thứ tự thông thường với số khác — kể cả chính nó:

```java
double x = Double.NaN;

System.out.println(x == x);  // false
System.out.println(x != x);  // true
System.out.println(x < 1.0); // false
System.out.println(x > 1.0); // false
System.out.println(x <= 1.0);// false
System.out.println(x >= 1.0);// false
```

Đừng dùng điều kiện `value == Double.NaN` để phát hiện NaN: nó **luôn false**. Hãy dùng:

```java
boolean isNan = Double.isNaN(value);
boolean isNanFloat = Float.isNaN(floatValue);
```

Hai dòng này giả định `value` có kiểu `double`, `floatValue` có kiểu `float` và nằm trong một phương thức.

### 3.2. Vì sao Double.equals khác toán tử ==?

`Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả **true** theo hợp đồng lớp wrapper, dù phép `doubleNaN == doubleNaN` trả false. `Double.compare` xây dựng một thứ tự tổng để các API collection có thể sắp xếp; theo thứ tự này, NaN được xem là bằng nhau trong comparator và **lớn hơn `Double.POSITIVE_INFINITY`**.

Vì vậy, hãy phân biệt:

- `==` và `<`, `>`: quan hệ so sánh primitive, NaN không bằng số nào.
- `Double.isNaN`: phát hiện NaN đúng ý nghĩa.
- `Double.equals`, `Double.hashCode`: hợp đồng object/hash, trong đó mọi biểu diễn NaN được đối xử tương đương để kiểm tra bằng nhau.
- `Double.compare`: thứ tự dùng được cho sắp xếp, có quy tắc đặc biệt cho NaN và signed zero.

NaN có thể có nhiều payload bit bên trong. `Double.doubleToLongBits` chuẩn hóa NaN về một dạng canonical; `Double.doubleToRawLongBits` giữ raw bits của đầu vào. Không dựa vào payload cụ thể của kết quả phép toán để xác định nguyên nhân NaN theo business logic.

## 4. Vô cực dương và âm

Java có `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`, tương ứng với `Float.POSITIVE_INFINITY` và `Float.NEGATIVE_INFINITY`.

```java
double positive = 1.0 / 0.0;   // Infinity
double negative = -1.0 / 0.0;  // -Infinity
double overflow = Double.MAX_VALUE * 2.0; // Infinity
```

Một số kết quả quan trọng:

| Phép toán | Kết quả |
| --- | --- |
| `1.0 / +0.0` | `Infinity` |
| `1.0 / -0.0` | `-Infinity` |
| `-1.0 / +0.0` | `-Infinity` |
| `1.0 / Double.POSITIVE_INFINITY` | `0.0` |
| `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` | `NaN` |
| `0.0 * Double.POSITIVE_INFINITY` | `NaN` |
| `Double.MAX_VALUE * 2.0` | `Infinity` |

Vô cực **khác NaN**: `Double.isInfinite(positive)` là true, `Double.isNaN(positive)` là false. Vô cực cũng **không hữu hạn**. Các phép toán có thể lan truyền vô cực, biến hai vô cực đối dấu thành NaN, hoặc trả số không.

Đừng coi vô cực là “một số rất lớn nhưng bình thường” khi domain yêu cầu kích thước, thời gian, giá tiền hoặc tỷ lệ hữu hạn. Phép toán có thể tràn từ một input hoàn toàn hữu hạn thành vô cực; do đó validation chỉ kiểm tra input là chưa đủ.

## 5. Signed zero: +0.0 và -0.0 bằng nhau trong == nhưng vẫn khác bit

IEEE 754 có hai biểu diễn cho số 0: `+0.0` và `-0.0`. Với phép so sánh primitive:

```java
double positiveZero = 0.0;
double negativeZero = -0.0;

System.out.println(positiveZero == negativeZero); // true
System.out.println(1.0 / positiveZero); // Infinity
System.out.println(1.0 / negativeZero); // -Infinity
```

Như vậy, phép `==` coi hai số bằng nhau về giá trị, nhưng một số phép toán vẫn phân biệt được bit dấu. Âm không có thể phát sinh từ phép tính âm quá nhỏ rồi underflow, từ literal `-0.0` hoặc từ việc chuyển đổi và giữ dấu.

**Ba cách nhận biết khác nhau:**

- `negativeZero == 0.0` là true, không đủ phát hiện dấu.
- `Double.compare(-0.0, +0.0) < 0` là true theo thứ tự tổng của wrapper.
- `Double.doubleToRawLongBits(-0.0) == Long.MIN_VALUE` là true: bit dấu cao nhất được bật, các bit còn lại bằng 0.

Theo `Double.equals`, `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` là **false**. Đó là điểm khác với primitive `==`. `Double.hashCode` cũng phải bảo toàn hợp đồng `equals`, nên kết quả hash tương ứng có thể khác.

### 5.1. Tác động khi dùng HashSet và sắp xếp

Khi dùng `HashSet<Double>`, hai phần tử `+0.0` và `-0.0` là **hai giá trị khác nhau theo equals**. Với NaN, hai giá trị NaN có payload raw bit khác nhau vẫn được wrapper coi bằng nhau.

Một tập chứa `+0.0`, `-0.0`, NaN thứ nhất và NaN thứ hai có thể có kích thước **3**, không phải 2 hoặc 4. Tương tự, `TreeSet<Double>` dùng `Double.compare` nên tách signed zero và gom NaN. Hành vi này khác với cách primitive `==` đối xử với chúng; đừng thay thế các quy tắc đó một cách vô thức.

Các phép toán thư viện như `Math.min(+0.0, -0.0)` trả `-0.0`, còn `Math.max(+0.0, -0.0)` trả `+0.0`; nếu có tham số NaN, `Math.min` và `Math.max` trả NaN. Đây là các ngoại lệ hữu ích khi test logic min/max.

## 6. Subnormal: vùng rất gần 0 và gradual underflow

Số **normal** sử dụng significand với bit dẫn ngầm; số **subnormal** có exponent field bằng 0 và fraction field khác 0. Subnormal cho phép biểu diễn các số gần 0 nhỏ hơn ngưỡng normal nhỏ nhất bằng độ chính xác giảm dần (*gradual underflow*).

Với `double`:

- `Double.MIN_NORMAL` = `2.2250738585072014E-308`, số normal dương nhỏ nhất.
- `Double.MIN_VALUE` = `4.9E-324`, số subnormal dương nhỏ nhất khác 0.
- Một số hữu hạn `x` là subnormal khi `0 < Math.abs(x) && Math.abs(x) < Double.MIN_NORMAL`.
- `Double.MIN_VALUE / 2.0` làm tròn thành `+0.0`, vì kết quả toán học quá nhỏ để biểu diễn là một số khác 0 của `double`.
- `Math.nextUp(0.0)` là `Double.MIN_VALUE`; `Math.nextDown(0.0)` là `-Double.MIN_VALUE`.

`Float.MIN_NORMAL` và `Float.MIN_VALUE` có vai trò tương ứng cho kiểu `float`. Đừng dùng `Double.MIN_VALUE` để mô tả miền âm nhỏ nhất; tên viết tắt này chỉ số **dương nhỏ nhất**.

### 6.1. Không nhầm subnormal với zero, NaN hay Infinity

Subnormal vẫn là một số hữu hạn, khác 0. Nó có thể tham gia cộng/trừ/nhân/chia và `Double.isFinite` trả true. Tuy nhiên, khoảng cách giữa những giá trị biểu diễn được ở vùng subnormal khiến **độ chính xác tương đối suy giảm**.

Một phép tính rất nhỏ có thể:

1. Cho kết quả subnormal khác 0.
2. Tiếp tục nhỏ xuống, rồi làm tròn thành signed zero.
3. Khi kết quả bị mất hoàn toàn vì underflow, điều kiện `value == 0.0` có thể đúng dù kết quả toán học chưa phải 0.

Đây là lý do không nên dùng phép kiểm tra `x == 0.0` như một khẳng định rằng một thuật toán số không hề có underflow. Nếu ứng dụng xử lý xác suất cực nhỏ, chuẩn hóa log-likelihood, ma trận với hệ số rất bé hay phép chia tỷ lệ, cần cân nhắc miền giá trị và thuật toán ổn định số.

## 7. Chương trình Java 17+ thực thi các trường hợp đặc biệt

Lưu toàn bộ chương trình sau vào `FloatingDiagnosticsDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;
import java.util.stream.DoubleStream;

public class FloatingDiagnosticsDemo {
    public static void main(String[] args) {
        double nan = 0.0 / 0.0;
        double positiveInfinity = 1.0 / 0.0;
        double negativeInfinity = -1.0 / 0.0;
        double positiveZero = 0.0;
        double negativeZero = -0.0;
        double subnormal = Double.MIN_VALUE;

        System.out.println("NaN detected: " + Double.isNaN(nan));
        System.out.println("NaN == NaN: " + (nan == nan));
        System.out.println("NaN != NaN: " + (nan != nan));
        System.out.println("Infinity positive: "
                + (positiveInfinity == Double.POSITIVE_INFINITY));
        System.out.println("Infinity negative: "
                + (negativeInfinity == Double.NEGATIVE_INFINITY));
        System.out.println("Infinity finite: "
                + Double.isFinite(positiveInfinity));
        System.out.println("Signed zeros primitive equal: "
                + (positiveZero == negativeZero));
        System.out.println("Signed zeros boxed equal: "
                + Double.valueOf(positiveZero).equals(Double.valueOf(negativeZero)));
        System.out.println("Negative zero reciprocal: " + (1.0 / negativeZero));
        System.out.println("Signed zero ordering: "
                + (Double.compare(negativeZero, positiveZero) < 0));
        System.out.println("Subnormal positive: " + (subnormal > 0.0));
        System.out.println("Subnormal below normal: "
                + (subnormal < Double.MIN_NORMAL));
        System.out.println("Subnormal half underflow: "
                + (subnormal / 2.0 == 0.0));
        System.out.println("Next after zero: "
                + (Math.nextUp(0.0) == subnormal));
        System.out.println("Negative zero raw sign: "
                + (Double.doubleToRawLongBits(negativeZero) == Long.MIN_VALUE));
        System.out.println("Nonempty average can be NaN: "
                + Double.isNaN(DoubleStream.of(1.0, Double.NaN)
                        .average().orElseThrow()));

        Set<Double> samples = new HashSet<>();
        samples.add(positiveZero);
        samples.add(negativeZero);
        samples.add(Double.NaN);
        samples.add(Double.longBitsToDouble(0x7ff8000000000001L));
        System.out.println("HashSet size: " + samples.size());
        System.out.println("HashSet contains NaN: "
                + samples.contains(Double.NaN));
        System.out.println("Boxed NaN equals NaN: "
                + Double.valueOf(Double.NaN).equals(Double.valueOf(nan)));
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingDiagnosticsDemo.java
java FloatingDiagnosticsDemo
```

Stdout đã kiểm chứng:

```text
NaN detected: true
NaN == NaN: false
NaN != NaN: true
Infinity positive: true
Infinity negative: true
Infinity finite: false
Signed zeros primitive equal: true
Signed zeros boxed equal: false
Negative zero reciprocal: -Infinity
Signed zero ordering: true
Subnormal positive: true
Subnormal below normal: true
Subnormal half underflow: true
Next after zero: true
Negative zero raw sign: true
Nonempty average can be NaN: true
HashSet size: 3
HashSet contains NaN: true
Boxed NaN equals NaN: true
```

Đầu ra chỉ dùng các thuộc tính có hợp đồng xác định; không phụ thuộc vào thuật toán sinh số ngẫu nhiên hay thứ tự duyệt của HashSet. Cặp NaN thêm vào Set có raw bit payload khác nhau nhưng `Double.equals` vẫn xem chúng cùng một khóa.

## 8. Double.isFinite: kiểm tra đầu vào và cả kết quả

Đối với nhiều nghiệp vụ, điều kiện tối thiểu là **giá trị phải hữu hạn**. Java cung cấp:

```java
Double.isNaN(x);       // Chỉ NaN
Double.isInfinite(x);  // Chỉ +Infinity hoặc -Infinity
Double.isFinite(x);    // Không phải NaN, cũng không phải vô cực
```

Ba dòng này minh họa API với biến `x` kiểu `double` trong một phương thức. `Double.isFinite` trả true cho cả +0.0, -0.0 và các số subnormal. Nó **không** kiểm tra rằng giá trị nằm trong miền nghiệp vụ, có đúng độ chính xác hay không.

Khi một phép tính chia yêu cầu kết quả hữu hạn và mẫu số khác 0, nên kiểm tra cả input lẫn output. Ví dụ lưu trong `FiniteValueDemo.java`:

```java
public class FiniteValueDemo {
    static double divideFinite(double numerator, double denominator) {
        if (!Double.isFinite(numerator) || !Double.isFinite(denominator)) {
            throw new IllegalArgumentException("Inputs must be finite");
        }
        if (denominator == 0.0) {
            throw new IllegalArgumentException("Denominator must be nonzero");
        }
        double result = numerator / denominator;
        if (!Double.isFinite(result)) {
            throw new ArithmeticException("Result is not finite");
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("4 / 2 = " + divideFinite(4.0, 2.0));
        try {
            divideFinite(1.0, -0.0);
        } catch (IllegalArgumentException e) {
            System.out.println("Negative zero rejected");
        }
        try {
            divideFinite(Double.MAX_VALUE, 0.5);
        } catch (ArithmeticException e) {
            System.out.println("Overflow rejected");
        }
        try {
            divideFinite(Double.NaN, 1.0);
        } catch (IllegalArgumentException e) {
            System.out.println("NaN rejected");
        }
    }
}
```

Chạy:

```bash
javac FiniteValueDemo.java
java FiniteValueDemo
```

Stdout đã kiểm chứng:

```text
4 / 2 = 2.0
Negative zero rejected
Overflow rejected
NaN rejected
```

Hàm cố ý từ chối cả hai dạng zero làm mẫu số bằng điều kiện `denominator == 0.0`; điều kiện này bắt được cả +0.0 lẫn -0.0. Nó cũng kiểm tra kết quả sau phép chia, vì `Double.MAX_VALUE / 0.5` có input hữu hạn, mẫu số khác 0 nhưng **kết quả tràn thành Infinity**.

Đây là hợp đồng ví dụ khi kết quả *phải hữu hạn*. Có ứng dụng khoa học chủ động dùng Infinity hoặc NaN và cần chính sách khác. Không bắt mọi trường hợp thành một lỗi chung mà không xét ý nghĩa nghiệp vụ.

## 9. So sánh, sort, min/max và collection

Các toán tử quan hệ primitive, `Double.compare`, `Double.equals` và `HashSet/HashMap` có những quy tắc khác nhau. Phân biệt chúng trước khi viết điều kiện lọc hoặc khóa số.

| Cặp giá trị | `primitive ==` | `Double.equals` | `Double.compare` |
| --- | --- | --- | --- |
| `+0.0` và `-0.0` | true | false | `compare(-0.0, +0.0) < 0` |
| `NaN` và `NaN` | false | true | 0 |
| `+Infinity` và `+Infinity` | true | true | 0 |
| `1.0` và `1.0` | true | true | 0 |

`Double.compare` dùng một thứ tự tổng hữu dụng cho sort: `-Infinity` nhỏ hơn số hữu hạn, các số hữu hạn được xếp theo giá trị với `-0.0 < +0.0`, tiếp theo là `+Infinity` rồi NaN. Đừng biến quy tắc sort này thành một nhận định rằng NaN là “giá trị số lớn nhất” về mặt toán học.

`Math.min` và `Math.max` dùng quy tắc khác với `Double.compare` ở chỗ NaN **lan truyền**: nếu có một tham số NaN thì kết quả NaN. Với signed zero, `Math.min(+0.0, -0.0)` trả -0.0 và `Math.max(+0.0, -0.0)` trả +0.0.

Nếu cần tìm cực trị nhưng muốn **bỏ qua NaN**, hãy xây dựng chính sách lọc trước; nếu muốn từ chối dữ liệu bất thường, hãy xác thực bằng `Double.isFinite`. Không dựa vào `min`/`max` để “tự chữa” đầu vào.

## 10. NaN và thống kê: Optional có giá trị vẫn có thể là NaN

Với `DoubleStream.average()`, một stream rỗng trả `OptionalDouble.empty()`. Nhưng một stream **không rỗng** có NaN có thể trả `OptionalDouble` có mặt và chứa NaN:

```java
var result = java.util.stream.DoubleStream
        .of(1.0, Double.NaN)
        .average();

System.out.println(result.isPresent()); // true
System.out.println(Double.isNaN(result.orElseThrow())); // true
```

Đây là đoạn minh họa bên trong một phương thức; mã runnable trong chương trình ở mục 7 kiểm chứng tình huống này.

**“Không có mẫu”** và **“có mẫu nhưng phép tổng hợp trả NaN”** là hai trạng thái khác nhau. Nếu nghiệp vụ không cho phép NaN hoặc vô cực, kiểm tra dữ liệu đầu vào trước khi tổng hợp hoặc kiểm tra kết quả theo quy tắc đã xác định. Tránh tự động lọc NaN khỏi dữ liệu nếu việc đó thay đổi ý nghĩa sample count, nhất là ở báo cáo chất lượng dữ liệu.

Một phép tổng hợp toàn NaN không thể tự biến thành trung bình hợp lệ. Ngoài NaN, các phép cộng dồn với các giá trị hữu hạn rất lớn cũng có thể overflow thành Infinity, nên không có gì bảo đảm “mọi input hữu hạn” sẽ tạo output hữu hạn.

## 11. Underflow và thuật toán số: cách hiểu đúng

**Overflow** xảy ra khi một kết quả hữu hạn theo toán học vượt miền biểu diễn hữu hạn và có thể trở thành Infinity. **Underflow** xảy ra khi kết quả có độ lớn rất nhỏ, làm mất precision, thành subnormal hoặc làm tròn về signed zero.

Ví dụ:

```java
double tiny = Double.MIN_VALUE;
double half = tiny / 2.0;    // +0.0, underflow
double huge = Double.MAX_VALUE;
double twice = huge * 2.0;  // Infinity, overflow
```

Subnormal tạo khoảng đệm thay vì lập tức đưa mọi giá trị dưới `Double.MIN_NORMAL` về zero. Tuy vậy, vùng này có ít chữ số có nghĩa tương đối hơn; tính các sai số tương đối như `Math.abs(actual - expected) / Math.abs(expected)` với `expected` cực nhỏ có thể trở nên không ổn định, overflow, hoặc chia zero.

Một số nguyên tắc thực hành:

- Tránh trừ hai số gần bằng nhau nếu thuật toán có công thức tương đương ổn định hơn.
- Với tích của nhiều xác suất rất nhỏ, cân nhắc làm việc trong miền log khi phù hợp.
- Khi kiểm tra gần bằng zero, dùng tolerance được thiết kế theo scale của bài toán; một epsilon cố định cho mọi dữ liệu có thể gây sai.
- Đừng suy ra rằng `x == 0.0` nghĩa là không có signal; nó có thể là kết quả của underflow.
- Không tự tắt/substitute subnormal nếu chưa biết yêu cầu độ chính xác và nền tảng chạy.

Đối với tính toán tài chính cần decimal chính xác, `BigDecimal` có thể phù hợp hơn `double`, nhưng cũng phải xử lý scale, rounding, giới hạn tài nguyên và phép chia không hữu hạn thập phân.

## 12. Parse, định dạng và ranh giới API

Java có thể biểu diễn `NaN` và `Infinity` dưới dạng chuỗi khi gọi `Double.toString`:

```java
System.out.println(Double.toString(Double.NaN)); // NaN
System.out.println(Double.toString(Double.POSITIVE_INFINITY)); // Infinity
System.out.println(Double.toString(-0.0)); // -0.0
```

`Double.parseDouble("NaN")` trả NaN, `Double.parseDouble("Infinity")` trả vô cực dương. Điều này không có nghĩa mọi giao thức trao đổi dữ liệu đều cho phép chúng.

**JSON chuẩn không có token số NaN hoặc Infinity.** Một số thư viện từ chối serialize giá trị không hữu hạn, số khác có chế độ đặc biệt; phải chọn hợp đồng rõ ràng: từ chối yêu cầu, biểu diễn trạng thái lỗi, trường nullable, hoặc chuỗi đặc biệt được định nghĩa và xác thực. Không gửi token `NaN` như một JSON number và giả định client nào cũng đọc được.

**BigDecimal không có NaN hay Infinity.** Vì vậy, `BigDecimal.valueOf(Double.NaN)` và `new BigDecimal("Infinity")` không tạo giá trị decimal hợp lệ; chúng ném `NumberFormatException`. Chuyển từ `double` sang `BigDecimal` cần kiểm tra tính hữu hạn, đồng thời hiểu rằng giá trị `double` ban đầu có thể đã là xấp xỉ.

**Cơ sở dữ liệu và các hệ thống ngoài:** một số hệ quản trị có biểu diễn hay quy tắc cho giá trị dấu phẩy động không hữu hạn, một số không; thao tác sort, group, index, so sánh có thể khác Java. Hãy xác thực giá trị và schema ngay tại ranh giới, thay vì dựa trên hành vi mặc định của một hệ cụ thể.

**Signed zero khi xuất dữ liệu:** một hệ thống có thể quyết định giữ dấu âm để bảo toàn thông tin phép đo hoặc chuẩn hóa -0.0 thành 0.0 cho hiển thị. Nếu chuẩn hóa, hãy làm ở ranh giới đã định nghĩa, không áp đặt tùy tiện lên mọi thuật toán số.

## 13. Kiểm thử đúng các giá trị đặc biệt

Các test với floating point nên nêu chính xác loại kết quả mong đợi:

| Mục tiêu | Assertion phù hợp |
| --- | --- |
| Nhận biết NaN | `Double.isNaN(actual)` |
| Nhận biết vô cực dương | `actual == Double.POSITIVE_INFINITY` |
| Đảm bảo giá trị hữu hạn | `Double.isFinite(actual)` |
| Phân biệt -0.0 và +0.0 | `Double.doubleToRawLongBits(actual)` hoặc `Double.compare` |
| Xác nhận subnormal | `actual != 0.0 && Math.abs(actual) < Double.MIN_NORMAL` và hữu hạn |
| Xác nhận underflow về zero | Kiểm tra output zero và điều kiện đầu vào/hợp đồng phép tính |
| So sánh xấp xỉ hữu hạn | Tolerance phù hợp miền và giá trị, sau khi xử lý NaN/Infinity |

Đừng chỉ dùng `Math.abs(expected - actual) < tolerance` mà không kiểm tra trường hợp đặc biệt. Nếu `actual` là NaN, biểu thức so sánh trả false; nếu cả hai vô cực cùng dấu, phép trừ có thể tạo NaN, dù theo một chính sách chúng được xem tương đương. Việc định nghĩa so sánh gần bằng phải thể hiện rõ **chính sách đối với NaN, vô cực, signed zero** và cách chọn tolerance.

Một ví dụ kiểm tra finite và sai số cho các giá trị hữu hạn:

```java
static boolean closeFinite(double left, double right,
                           double absoluteTolerance) {
    if (!Double.isFinite(left) || !Double.isFinite(right)
            || !Double.isFinite(absoluteTolerance)
            || absoluteTolerance < 0.0) {
        return false;
    }
    return Math.abs(left - right) <= absoluteTolerance;
}
```

Đây là phương thức mẫu cần đặt trong class. Nếu phép trừ `left - right` overflow thành Infinity dù cả hai input hữu hạn thì kết quả trả false, phù hợp cách hiểu “không gần nhau” trong kiểm tra này. Đối với dữ liệu trải rộng nhiều bậc độ lớn, thêm tolerance tương đối với xử lý overflow cẩn thận, thay vì dùng ngưỡng tuyệt đối duy nhất.

## 14. Lỗi thường gặp

| Lỗi | Hậu quả | Hướng khắc phục |
| --- | --- | --- |
| `x == Double.NaN` | Luôn false | `Double.isNaN(x)` |
| Nghĩ mọi phép chia cho 0 đều ném `ArithmeticException` | Sai với `double`/`float`; có Infinity/NaN | Phân biệt phép tính nguyên và floating point |
| Coi Infinity là số bình thường | Có thể lan truyền giá trị không hợp lệ | `Double.isFinite` và domain validation |
| Chỉ kiểm tra input hữu hạn | Phép toán vẫn có thể overflow thành Infinity | Kiểm tra output |
| Coi `+0.0` và `-0.0` có raw bit giống nhau | Lỗi reciprocal, serialization hoặc hashing | Kiểm tra `Double.compare`/raw bits khi cần |
| Cho rằng `Double.MIN_VALUE` là số âm nhỏ nhất | Nhầm miền subnormal | Phân biệt `MIN_VALUE`, `MIN_NORMAL`, `-MAX_VALUE` |
| Coi subnormal là zero | Đánh mất những số hữu hạn rất nhỏ | Kiểm tra `0 < abs(x) < MIN_NORMAL` |
| Coi `OptionalDouble` có mặt là một số hữu hạn | NaN/Infinity vẫn có thể được chứa | Phân biệt presence và `Double.isFinite` |
| Truyền NaN/Infinity vào JSON number không có chính sách | Dữ liệu không hợp chuẩn hoặc hành vi thư viện không đồng nhất | Chuẩn hóa/serialize theo schema đã chốt |
| Dùng `double` cho số tiền exact | Sai số biểu diễn nhị phân | Chọn `BigDecimal` với rounding phù hợp |
| Suy NaN trong hash collection sẽ có hành vi giống `primitive ==` | Sai: `Double.equals` coi các NaN bằng nhau | Dùng API kiểm tra phù hợp mục đích |

## 15. Câu hỏi tự kiểm tra

1. **`0.0 / 0.0` trả gì?** NaN; không phải ngoại lệ phép chia nguyên.
2. **`1.0 / -0.0` trả gì?** Vô cực âm.
3. **`Double.NaN == Double.NaN` là true hay false?** False; dùng `Double.isNaN` để phát hiện NaN.
4. **`Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là gì?** True, vì lớp wrapper định nghĩa tính bằng nhau của NaN phục vụ object/collection.
5. **`0.0 == -0.0` là gì?** True với primitive, nhưng `Double.equals` phân biệt hai biểu diễn.
6. **`Double.MIN_VALUE` có phải âm vô cùng nhỏ không?** Không; đó là số **dương subnormal nhỏ nhất khác 0**.
7. **Subnormal có hữu hạn không?** Có; nó khác 0, khác NaN và vô cực, nhưng độ chính xác tương đối nhỏ hơn số normal.
8. **`Double.MIN_VALUE / 2.0` cho gì?** `+0.0` vì underflow và làm tròn.
9. **Một tập có dữ liệu và `DoubleStream.average()` có thể trả NaN không?** Có; trạng thái Optional có mặt không đảm bảo số hữu hạn.
10. **Kiểm tra đầu vào bằng `Double.isFinite` đã đủ cho kết quả chưa?** Chưa; phép nhân/chia của input hữu hạn vẫn có thể overflow thành vô cực.
11. **Tại sao nên kiểm tra NaN/Infinity trước JSON?** JSON number chuẩn không có token NaN/Infinity; giao diện trao đổi dữ liệu phải xác định cách xử lý.
12. **Tại sao không luôn chuẩn hóa -0.0 thành +0.0?** Dấu có thể mang thông tin quan trọng trong thuật toán số hoặc quy tắc kết quả; chỉ chuẩn hóa nếu domain yêu cầu.

## 16. Ghi nhớ

`float`/`double` biểu diễn nhiều hơn các số hữu hạn thông thường. **NaN** khiến so sánh primitive khác kỳ vọng; **±Infinity** xuất hiện từ phép chia cho zero và overflow; **±0.0** bằng nhau theo `==` nhưng có bit và một số hành vi khác nhau; **subnormal** giúp biểu diễn số cực nhỏ trước khi underflow về zero.

Khi triển khai, hãy xác định rõ API đang dùng **so sánh primitive**, **thứ tự wrapper**, hay **equals/hashCode**. Dùng `Double.isNaN`, `Double.isInfinite`, `Double.isFinite` để chẩn đoán; kiểm tra cả input và output nếu cần hữu hạn; giữ chính sách dữ liệu thiếu, rounding, min/max và serialization nhất quán. Các ví dụ Java 17+ minh họa rằng hiểu đúng những trường hợp biên này quan trọng hơn việc chỉ kiểm thử trên những con số thông thường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-005">Kiểm tra phép so sánh với sai số phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra phép so sánh với sai số phù hợp trong Java

Khi kiểm thử hoặc chẩn đoán các phép tính số, không phải lúc nào so sánh hai kết quả bằng dấu `==` hoặc `equals()` cũng phản ánh đúng yêu cầu. Với số dấu phẩy động nhị phân (`float`, `double`), kết quả toán học mong muốn có thể được biểu diễn bằng giá trị xấp xỉ. Ngược lại, cho phép một mức sai số quá rộng có thể làm **bài kiểm thử chấp nhận cả kết quả thực sự sai**.

Mục tiêu của bài này là chọn **tiêu chí bằng nhau phù hợp với loại số và yêu cầu đo lường/nghiệp vụ**, dùng sai số tuyệt đối, sai số tương đối hoặc kết hợp hai tiêu chí khi hợp lý, và kiểm tra các trường hợp đặc biệt như số gần 0, độ lớn khác nhau, `NaN`, vô cực, số có dấu âm 0 và `BigDecimal`.

**Nguyên tắc:** trước khi viết assertion, phải trả lời “Hai kết quả này cần bằng nhau về mặt nào và được phép sai lệch bao nhiêu?”. Không có một hằng số `epsilon` đúng cho mọi phép tính, mọi đơn vị và mọi miền giá trị.

## 1. Vì sao `double` thường không nên so sánh chính xác bằng `==`?

Kiểu `double` Java dùng biểu diễn nhị phân IEEE 754 với độ chính xác hữu hạn. Nhiều phân số thập phân đơn giản, như 0,1 hoặc 0,2, không có biểu diễn nhị phân hữu hạn chính xác. Khi lưu và tính toán, giá trị phải được làm tròn theo quy tắc số dấu phẩy động.

Ví dụ:

```java
double actual = 0.1 + 0.2;
double expected = 0.3;

System.out.println(actual);             // 0.30000000000000004
System.out.println(actual == expected); // false
```

`actual` và `expected` là hai giá trị `double` gần nhau, nhưng **khác bit**, vì vậy `==` trả `false`. Trong một bài kiểm thử tính gần đúng, ta có thể chấp nhận mức sai lệch rất nhỏ nếu yêu cầu cho phép.

Tuy nhiên, `==` **không sai trong mọi trường hợp**:

- Khi kiểm tra một giá trị được quy định phải chính xác, như số nguyên nhỏ được biểu diễn chính xác trong `double`, so sánh chính xác có thể hợp lý.
- Khi kiểm tra những giá trị đặc biệt hoặc trạng thái, có thể cần kiểm tra **chính xác** hoặc kiểm tra bit, chứ không dùng sai số.
- Khi yêu cầu nghiệp vụ đòi chính xác tuyệt đối, không thể chữa thiết kế sai kiểu dữ liệu bằng cách tùy tiện thêm epsilon.

Những sai khác trong số dấu phẩy động có thể đến từ biểu diễn đầu vào, trình tự phép tính, sự làm tròn ở từng bước, phép biến đổi đại số, hoặc các thuật toán xấp xỉ. Một “khoảng chấp nhận” chỉ hữu ích khi có **cơ sở xác định độ sai lệch hợp lệ**.

## 2. Sai số tuyệt đối: dùng khi đơn vị và độ lệch tối đa quan trọng

Đặt giá trị tham chiếu là `expected` và kết quả cần kiểm tra là `actual`:

```text
absoluteError = |actual - expected|

Chấp nhận khi:
|actual - expected| <= absoluteTolerance
```

Ví dụ, nếu phép đo được phép lệch tối đa 0,01 đơn vị, ta có thể kiểm tra:

```java
boolean acceptable =
        Math.abs(actual - expected) <= 0.01;
```

Đây là **đoạn minh họa** trong phạm vi các biến `actual` và `expected` là số `double` hữu hạn, cùng đơn vị, không quá lớn đến mức hiệu bị tràn. Trường hợp `NaN` hoặc vô cực cần được xử lý riêng nếu bài kiểm thử có thể gặp chúng.

### Lợi ích và giới hạn

Sai số tuyệt đối dễ diễn đạt theo đơn vị, chẳng hạn “không được lệch quá 0,001 giây” hoặc “không lệch quá 0,1 °C”, **nếu giới hạn đó thực sự được đặc tả**. Nó đặc biệt có ích gần **giá trị 0**.

Nhưng một ngưỡng cố định có thể quá lỏng với số rất nhỏ hoặc quá chặt với số rất lớn:

| Expected | Actual | Sai số tuyệt đối | Kết quả với tolerance = 0,001 |
|---:|---:|---:|---|
| 0 | 0,0005 | 0,0005 | Chấp nhận |
| 1000 | 1000,0005 | 0,0005 | Chấp nhận |
| 1.000.000.000 | 1.000.000.000,1 | Khoảng 0,1 | Từ chối |

Ví dụ cuối có sai lệch khoảng một phần mười tỷ theo tỷ lệ, nhưng bị từ chối bởi ngưỡng tuyệt đối 0,001. Điều này có thể đúng hoặc sai tùy yêu cầu: nếu sai số tuyệt đối phải nhỏ hơn 0,001 thì **phải từ chối**, bất kể số lớn đến đâu.

## 3. Sai số tương đối: đánh giá sai lệch theo độ lớn

Một tiêu chí khác là so sánh sai lệch với độ lớn của giá trị tham chiếu:

```text
relativeError = |actual - expected| / |expected|
```

Công thức này có vấn đề khi `expected = 0`, vì phép chia cho 0 không cho một sai số tương đối hữu ích. Nếu expected gần 0, tỷ lệ còn có thể phóng đại một sai lệch tuyệt đối rất nhỏ.

Trong trường hợp so sánh hai giá trị gần đúng một cách **đối xứng**, có thể dùng thước đo:

```text
scale = max(|expected|, |actual|)
relativeCondition: |actual - expected| <= relTol * scale
```

Nếu cả hai bằng 0, điều kiện bằng nhau chính xác đã được đáp ứng. Khi hai số rất gần 0 nhưng không bằng nhau, tiêu chí tương đối một mình thường quá khắt khe; lúc này cần cân nhắc ngưỡng tuyệt đối.

**Cần phân biệt:** “sai số tương đối so với expected” và “ngưỡng tương đối đối xứng theo max độ lớn” là hai định nghĩa khác nhau. Trong tài liệu kiểm thử, nên viết rõ công thức thay vì chỉ ghi “dùng epsilon tương đối”.

## 4. Kết hợp absolute tolerance và relative tolerance

Một phép kiểm tra tổng quát cho nhiều phép tính `double` hữu hạn là:

```text
diff = |actual - expected|
scale = max(|expected|, |actual|)

acceptable = diff <= max(absTol, relTol * scale)
```

Giải thích:

- **`absTol`** là khoảng sai lệch tuyệt đối cho phép, hữu ích quanh 0.
- **`relTol`** cho phép sai lệch thay đổi theo độ lớn, hữu ích với những đại lượng rất lớn.
- Dùng `max` nghĩa là một trong hai giới hạn đủ để chấp nhận. Đây là chính sách dung sai **kết hợp theo OR**, không đồng nghĩa “phải đồng thời thỏa cả hai”.
- Nếu nghiệp vụ yêu cầu **cả** sai số tuyệt đối lẫn tương đối phải dưới ngưỡng, hãy kiểm tra **hai điều kiện AND** riêng. Không tùy ý dùng công thức `max` cho một hợp đồng nghiêm ngặt hơn.

Ví dụ giả định `absTol = 1e-12` và `relTol = 1e-9`:

| Expected | Actual | Đánh giá |
|---:|---:|---|
| `0.3` | `0.1 + 0.2` | Chấp nhận: sai số nhị phân rất nhỏ |
| `0.0` | `1e-13` | Chấp nhận: nằm trong ngưỡng tuyệt đối |
| `0.0` | `1e-8` | Từ chối: vượt ngưỡng tuyệt đối và tiêu chí tương đối |
| `1e9` | `1e9 + 0.1` | Chấp nhận: sai số nhỏ theo tỷ lệ |
| `1.0` | `1.1` | Từ chối: sai lệch quá lớn |

**Các ngưỡng trên chỉ phục vụ ví dụ minh họa**, không phải giá trị mặc định nên sao chép vào mọi dự án. Khi kiểm thử thuật toán khoa học, các ngưỡng có thể xuất phát từ phân tích sai số số học, chất lượng phương pháp xấp xỉ hoặc yêu cầu độ chính xác. Với phép đo vật lý, có thể phải xét độ phân giải thiết bị. Với dữ liệu tài chính/đếm/số định danh, phải dùng quy tắc khác.

### Một hàm so sánh gần bằng an toàn cho double

```java
static boolean nearlyEqual(
        double expected, double actual,
        double absTol, double relTol) {

    if (!Double.isFinite(absTol) || absTol < 0.0
            || !Double.isFinite(relTol)
            || relTol < 0.0 || relTol >= 1.0) {
        throw new IllegalArgumentException(
                "Invalid tolerance");
    }

    if (Double.isNaN(expected) || Double.isNaN(actual)) {
        return false;
    }

    if (expected == actual) {
        return true;
    }

    if (!Double.isFinite(expected) || !Double.isFinite(actual)) {
        return false;
    }

    double difference = Math.abs(actual - expected);
    double scale = Math.max(Math.abs(expected), Math.abs(actual));

    return difference <= Math.max(absTol, relTol * scale);
}
```

Đây là **một phương thức** có thể đặt trong lớp Java. Các quyết định thiết kế của nó cần được hiểu rõ:

1. Ngưỡng được yêu cầu hữu hạn, không âm; `relTol < 1` để ngăn một ngưỡng tương đối vô nghĩa có thể chấp nhận chênh lệch cực lớn. Đây là ràng buộc của **hàm minh họa**, không phải giới hạn chung của mọi API.
2. `NaN` luôn bị coi là không so sánh gần bằng, kể cả khi hai phía đều là `NaN`. Nếu bài test **mong đợi NaN**, hãy kiểm tra riêng bằng `Double.isNaN(...)`.
3. Hai giá trị vô cực cùng dấu được coi là bằng nhau tại nhánh `expected == actual`; một phía hữu hạn và phía còn lại vô cực, hoặc hai vô cực trái dấu, bị từ chối.
4. `+0.0` và `-0.0` được xem là bằng nhau về **giá trị số học**. Nếu bài kiểm tra đòi phân biệt dấu của 0, cần so sánh bit.
5. Với hai giá trị hữu hạn cực lớn trái dấu, phép trừ có thể ra vô cực do overflow. Hàm sẽ từ chối, phù hợp với chính sách tolerance hữu hạn và `relTol < 1` ở đây.
6. Hàm không tự suy ra absTol/relTol; người sử dụng phải cung cấp chúng dựa trên bài toán.

Không viết một hàm “gần bằng” mà ngầm cho phép `NaN` hoặc vô cực đi qua điều kiện sai số chỉ vì phép toán IEEE 754 trả về kết quả đặc biệt.

## 5. Những trường hợp biên đặc biệt

### 5.1. Gần 0 và dấu âm 0

Tỷ lệ `|actual - expected| / |expected|` không xác định hữu ích khi expected bằng 0. Vì vậy sai số tuyệt đối quan trọng để so sánh kết quả dự kiến gần 0:

```java
nearlyEqual(0.0, 1e-13, 1e-12, 1e-9); // true
nearlyEqual(0.0, 1e-8, 1e-12, 1e-9);  // false
```

Trong Java, `+0.0 == -0.0` là `true`. Nhưng bit dấu của hai giá trị khác nhau, và một số phép toán cho kết quả khác: `1.0 / +0.0` là `+Infinity`, còn `1.0 / -0.0` là `-Infinity`.

Nếu dấu của số 0 **là một phần hợp đồng**, cần kiểm tra bit:

```java
boolean sameBits = Double.doubleToRawLongBits(0.0)
        == Double.doubleToRawLongBits(-0.0); // false
```

So sánh bit có thể cũng phân biệt các mẫu biểu diễn `NaN`; hãy chỉ dùng khi yêu cầu thực sự gắn với biểu diễn IEEE 754.

### 5.2. NaN

`Double.NaN` thể hiện “Not a Number”. Theo phép so sánh nguyên thủy của IEEE 754:

```java
System.out.println(Double.NaN == Double.NaN); // false
System.out.println(Double.isNaN(Double.NaN));   // true
```

Nếu mục đích kiểm thử là phát hiện thuật toán **đáng lẽ phải ra số hữu hạn**, một kết quả `NaN` phải bị từ chối. Nếu thuật toán **được đặc tả phải trả NaN** cho một đầu vào không hợp lệ, hãy viết assertion về `Double.isNaN(actual)`. Đừng xem khoảng sai số là công cụ kiểm tra `NaN`.

### 5.3. Vô cực và tràn số thực

`Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` là các giá trị đặc biệt. Hai số cùng vô cực có thể so sánh `==` bằng nhau, nhưng vô cực trừ chính nó tạo `NaN`; vì thế công thức chỉ dùng `Math.abs(expected - actual)` không đủ bao quát.

Nếu thuật toán phải trả số hữu hạn, kiểm tra `Double.isFinite(actual)` trước khi so sánh. Nếu vô cực là kết quả hợp lệ được đặc tả, kiểm tra chính xác trường hợp đó, tách biệt với tolerance.

### 5.4. ULP: khoảng cách giữa các giá trị floating-point lân cận

`Math.ulp(x)` trả kích thước một *unit in the last place* tại `x` theo độ lớn và biểu diễn floating-point. Ví dụ:

```java
System.out.println(Math.ulp(1.0)); // 2.220446049250313E-16
```

Trong kiểm thử thuật toán số, đôi khi người ta quy định sai lệch theo số ULP (ví dụ kết quả chỉ cách tham chiếu vài giá trị `double` có thể biểu diễn). Điều này có thể hữu ích khi cần đánh giá đặc tính làm tròn của một thuật toán cụ thể.

Tuy nhiên, **một khoảng ULP cố định cũng không phải ngưỡng nghiệp vụ chung**. Khoảng cách ULP thay đổi theo độ lớn, cần cẩn trọng gần 0, tại miền subnormal, qua 0, với hai số trái dấu và khi một phía là `NaN` hoặc vô cực. Không nên tùy tiện dùng `Math.ulp(expected)` làm epsilon mặc định để kiểm thử mọi phép tính.

## 6. Ví dụ Java 17+ hoàn chỉnh và có kết quả xác định

Lưu tệp `NumericToleranceDemo.java`:

```java
import java.math.BigDecimal;

public class NumericToleranceDemo {
    static boolean nearlyEqual(
            double expected, double actual,
            double absTol, double relTol) {

        if (!Double.isFinite(absTol) || absTol < 0.0
                || !Double.isFinite(relTol)
                || relTol < 0.0 || relTol >= 1.0) {
            throw new IllegalArgumentException("Invalid tolerance");
        }
        if (Double.isNaN(expected) || Double.isNaN(actual)) {
            return false;
        }
        if (expected == actual) {
            return true;
        }
        if (!Double.isFinite(expected) || !Double.isFinite(actual)) {
            return false;
        }
        double difference = Math.abs(actual - expected);
        double scale = Math.max(Math.abs(expected), Math.abs(actual));
        return difference <= Math.max(absTol, relTol * scale);
    }

    static boolean withinDecimalTolerance(
            BigDecimal expected,
            BigDecimal actual,
            BigDecimal tolerance) {
        if (tolerance.signum() < 0) {
            throw new IllegalArgumentException("Negative tolerance");
        }
        return actual.subtract(expected).abs()
                .compareTo(tolerance) <= 0;
    }

    public static void main(String[] args) {
        double absTol = 1e-12;
        double relTol = 1e-9;

        System.out.println("exact double: " + ((0.1 + 0.2) == 0.3));
        System.out.println("near double: "
                + nearlyEqual(0.3, 0.1 + 0.2, absTol, relTol));
        System.out.println("near zero pass: "
                + nearlyEqual(0.0, 1e-13, absTol, relTol));
        System.out.println("near zero fail: "
                + nearlyEqual(0.0, 1e-8, absTol, relTol));
        System.out.println("large magnitude: "
                + nearlyEqual(1e9, 1e9 + 0.1, absTol, relTol));
        System.out.println("far values: "
                + nearlyEqual(1.0, 1.1, absTol, relTol));

        System.out.println("NaN near NaN: "
                + nearlyEqual(Double.NaN, Double.NaN, absTol, relTol));
        System.out.println("same +infinity: "
                + nearlyEqual(Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, absTol, relTol));
        System.out.println("opposite infinities: "
                + nearlyEqual(Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, absTol, relTol));
        System.out.println("signed zero near: "
                + nearlyEqual(0.0, -0.0, absTol, relTol));
        System.out.println("signed zero same bits: "
                + (Double.doubleToRawLongBits(0.0)
                   == Double.doubleToRawLongBits(-0.0)));

        BigDecimal exactA = new BigDecimal("1.00");
        BigDecimal exactB = new BigDecimal("1.000");
        System.out.println("BigDecimal equals: "
                + exactA.equals(exactB));
        System.out.println("BigDecimal numeric equal: "
                + (exactA.compareTo(exactB) == 0));
        System.out.println("BigDecimal within 0.01: "
                + withinDecimalTolerance(exactA,
                        new BigDecimal("1.009"),
                        new BigDecimal("0.01")));
        System.out.println("BigDecimal outside 0.01: "
                + withinDecimalTolerance(exactA,
                        new BigDecimal("1.011"),
                        new BigDecimal("0.01")));
    }
}
```

Biên dịch, chạy với Java 17 trở lên:

```bash
javac NumericToleranceDemo.java
java NumericToleranceDemo
```

Kết quả:

```text
exact double: false
near double: true
near zero pass: true
near zero fail: false
large magnitude: true
far values: false
NaN near NaN: false
same +infinity: true
opposite infinities: false
signed zero near: true
signed zero same bits: false
BigDecimal equals: false
BigDecimal numeric equal: true
BigDecimal within 0.01: true
BigDecimal outside 0.01: false
```

Điểm quan trọng trong chương trình:

- Phép so sánh `0.1 + 0.2 == 0.3` thất bại về **bằng nhau chính xác**, nhưng thành công với ngưỡng thử nghiệm đã khai báo.
- Cùng một ngưỡng kết hợp vẫn phải từ chối `0` so với `1e-8` hoặc `1.0` so với `1.1`. Một tolerance quá rộng sẽ không có giá trị phát hiện lỗi.
- `NaN` không được chấp nhận như “gần một số”; vô cực cần quy tắc riêng.
- Trong `BigDecimal`, bằng nhau về giá trị số học và bằng nhau về biểu diễn có thể khác nhau do scale. Phép so sánh với một **khoảng sai số thập phân chính xác** không cần chuyển sang `double`.

## 7. BigDecimal: không dùng epsilon như phản xạ mặc định

`BigDecimal` biểu diễn giá trị thập phân theo `unscaledValue × 10^(-scale)`, phù hợp khi cần kiểm soát chính xác số học thập phân. Với hai `BigDecimal`, trước tiên xác định yêu cầu:

| Nhu cầu | Cách kiểm tra |
|---|---|
| Cùng giá trị số học, không quan tâm scale | `a.compareTo(b) == 0` |
| Cùng giá trị số học **và** cùng scale | `a.equals(b)` |
| Cho phép độ lệch thập phân được quy định | `a.subtract(b).abs().compareTo(tolerance) <= 0` |
| Kết quả phải đạt scale/mode nghiệp vụ cụ thể | Chuẩn hóa/làm tròn theo chính sách được phê duyệt rồi kiểm tra giá trị và scale tương ứng |

Ví dụ:

```java
BigDecimal expected = new BigDecimal("10.00");
BigDecimal actual = new BigDecimal("10.005");

BigDecimal tolerance = new BigDecimal("0.01");

boolean within = actual.subtract(expected).abs()
        .compareTo(tolerance) <= 0; // true
```

Phương pháp này **không dùng `doubleValue()`**, nên không thêm sai số do chuyển sang nhị phân. Nhưng cho phép lệch 0,01 vẫn là một **quyết định kiểm thử/nghiệp vụ**, không phải thuộc tính mặc định của `BigDecimal`.

Ví dụ khác, nếu yêu cầu là “phải tính chính xác 10,00 theo scale 2”, việc chấp nhận `10.005` vì “không lệch quá 0,01” sẽ che giấu một kết quả sai. Khi kiểm thử tổng tiền, giao dịch hoặc tính thuế, phải lấy chính sách chính thức về độ chính xác, scale, thứ tự làm tròn và thời điểm làm tròn làm chuẩn.

### Không tự gán epsilon theo scale

`new BigDecimal("1.00")` có scale 2, nhưng điều đó không có nghĩa sai số tối đa cho phép là `0.01` hay `0.005`. Giá trị có scale 2 có thể đã được làm tròn, có thể được nhập nguyên văn, hoặc có thể chỉ là định dạng lưu trữ.

Tương tự, cơ sở dữ liệu `DECIMAL(p, s)` hoặc một API nhận `BigDecimal` **không tự cho biết** giá trị nào được chấp nhận sai lệch. Tolerance cần có nguồn từ đặc tả, phương pháp đo, phân tích sai số hoặc yêu cầu nghiệp vụ.

## 8. Kiểm thử bằng assertion: đừng để ngưỡng che giấu lỗi

Trong JUnit Jupiter, ví dụ:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;

// Ví dụ bên trong một @Test
assertEquals(0.3, 0.1 + 0.2, 1e-12);
```

Đây là ví dụ **phụ thuộc JUnit Jupiter** và không phải một chương trình Java độc lập chỉ với JDK. Tham số `delta` trong overload so sánh số thực là ngưỡng dung sai **tuyệt đối** cho sai lệch số học, có xử lý các giá trị đặc biệt theo hợp đồng của API. Nó không triển khai công thức **kết hợp tuyệt đối và tương đối** ở phần 4.

Nếu yêu cầu là tolerance hỗn hợp, có thể dùng helper riêng đã được kiểm thử:

```java
import static org.junit.jupiter.api.Assertions.assertTrue;

// Ví dụ bên trong một @Test
assertTrue(nearlyEqual(expected, actual, absTol, relTol));
```

Khi một assertion thất bại, chẩn đoán nên hiển thị **expected, actual, sai số tuyệt đối, ngưỡng được áp dụng và đơn vị** nếu có. Điều này giúp phân biệt lỗi logic, sai số tính toán chấp nhận được và trường hợp tolerance bị cấu hình không phù hợp.

Một số lưu ý:

- Không dùng `assertEquals(expected, actual, 0.0)` như thể nó tự động xử lý mọi sai số floating-point; khi yêu cầu chính xác, ghi rõ đó là chủ ý.
- Không đặt `delta` quá lớn chỉ để “bài test chuyển xanh”. Tolerance là một phần của **đặc tả**, không phải công cụ né lỗi.
- Không trộn lẫn kiểm tra xấp xỉ với so sánh kiểu nguyên hoặc các mã nhận dạng: ID, số lần lặp và chỉ số mảng thường đòi **bằng nhau chính xác**.
- Không mặc định cùng một tolerance phù hợp cho số giây, độ dài, phần trăm, tiền tệ hoặc xác suất. **Đơn vị và thang đo** quyết định ý nghĩa ngưỡng.

## 9. Chọn ngưỡng sai số dựa trên yêu cầu nào?

Độ dung sai nên xuất phát từ một hoặc nhiều căn cứ sau:

1. **Sai số cho phép trong hợp đồng nghiệp vụ hoặc tiêu chuẩn đo:** ví dụ giá trị đo có độ phân giải và sai số thiết bị đã được công bố.
2. **Phân tích sai số thuật toán:** số phép tính, độ ổn định số, mất mát do triệt tiêu khi trừ, độ điều kiện của bài toán và sai số làm tròn tích lũy.
3. **Phạm vi dữ liệu và đơn vị:** expected gần 0, rất lớn, dương/âm hay trải qua nhiều bậc độ lớn.
4. **Chuẩn tham chiếu và độ chính xác của expected:** kết quả “đúng” có phải là giá trị toán học chính xác, nghiệm gần đúng độ chính xác cao, hay số đo thực nghiệm cũng chứa sai số?
5. **Hành vi đặc biệt:** có chấp nhận `NaN`, `Infinity`, `-0.0` hoặc overflow/underflow hay không?
6. **Mục đích phép thử:** xác nhận một bất biến chính xác, đối chiếu thuật toán gần đúng, kiểm thử regression qua nền tảng, hay xác nhận sai số theo quy định.

**Phân biệt độ chính xác máy và sai số nghiệp vụ:** `Math.ulp(1.0)` là đặc tính số thực nhị phân gần 1, không phải mức sai số cho phép của một kết quả đo đạc hoặc của hệ thống kế toán. Một phép tính kém ổn định có thể tích lũy sai lệch lớn hơn nhiều ULP; ngược lại, một nghiệp vụ có thể cấm bất cứ lệch số nào dù mức lệch rất nhỏ.

Nếu không có hợp đồng sai số rõ ràng, kiểm thử nên ghi nhận khoảng sai số dự kiến và **xác minh với người sở hữu phép tính**, không gán hằng số mặc định thiếu căn cứ.

## 10. Những lỗi thường gặp

1. **Chọn một `epsilon = 1e-6` cho mọi số.** Ngưỡng đó có thể vô nghĩa khi đơn vị, miền giá trị hoặc yêu cầu thay đổi.
2. **Dùng duy nhất sai số tương đối gần 0.** Mẫu số bằng 0 hoặc rất nhỏ làm tỷ lệ không hữu ích. Thêm sai số tuyệt đối khi phù hợp.
3. **Dùng duy nhất sai số tuyệt đối cho miền nhiều bậc độ lớn.** Có thể từ chối một giá trị có sai số tương đối cực nhỏ hoặc chấp nhận sai lệch tương đối lớn với số nhỏ; chỉ kết luận theo yêu cầu.
4. **Xem mọi `NaN` là bằng nhau trong helper so sánh xấp xỉ.** Một hàm tính sai ra `NaN` có thể bị bỏ qua. Kiểm tra NaN riêng khi đó là kết quả được đặc tả.
5. **Bỏ qua infinity và số 0 âm.** Những giá trị này có ngữ nghĩa IEEE 754 đặc biệt; cần chính sách rõ ràng.
6. **Chuyển `BigDecimal` sang `double` để so sánh với epsilon.** Làm vậy bỏ mất lợi thế tính toán thập phân chính xác.
7. **Dùng `BigDecimal.equals()` khi chỉ cần cùng giá trị số học.** `1.0` và `1.00` không `equals()`, nhưng `compareTo()==0`.
8. **Dùng tolerance che phủ lỗi nghiệp vụ về làm tròn.** Một hóa đơn phải đúng 2 chữ số theo quy tắc đã duyệt không nên được chấp nhận chỉ vì số sai vẫn gần kết quả.
9. **Không thử giá trị ranh giới ngay quanh tolerance.** Cần kiểm tra trường hợp bằng ngưỡng, nhỏ hơn rất ít, lớn hơn rất ít, số âm và gần 0; hiểu rằng chính tolerance có thể không được biểu diễn thập phân chính xác trong `double`.
10. **Dựa vào việc một lần chạy trùng kết quả để kết luận thuật toán chính xác.** Các phép tính số có thể thay đổi theo đầu vào và thứ tự toán học; hãy kiểm tra nhiều miền đại diện và bất biến quan trọng.

## 11. Tự kiểm tra

**Câu 1.** Tại sao `0.1 + 0.2 == 0.3` có thể là `false` trong Java?  
**Đáp án:** `double` biểu diễn nhị phân hữu hạn; 0,1 và 0,2 không thể lưu chính xác, dẫn tới sai khác giữa kết quả cộng và biểu diễn gần đúng của 0,3.

**Câu 2.** Khi expected bằng 0, có nên chia sai số tuyệt đối cho `|expected|` để tính sai số tương đối không?  
**Đáp án:** Không; mẫu số bằng 0. Dùng một tiêu chí sai số tuyệt đối được xác định theo bài toán, hoặc chính sách phù hợp khác.

**Câu 3.** Với `absTol = 1e-12` và `relTol = 1e-9`, kiểm tra `expected = 0`, `actual = 1e-8` có qua không?  
**Đáp án:** Không; sai lệch `1e-8` vượt cả ngưỡng tuyệt đối `1e-12` và giới hạn tương đối dựa trên độ lớn của cặp giá trị.

**Câu 4.** Trong helper ở trên, hai giá trị `NaN` có được coi là gần bằng nhau không?  
**Đáp án:** Không. Nếu NaN là kết quả được kỳ vọng, dùng `Double.isNaN(actual)`.

**Câu 5.** `BigDecimal("1.0")` và `BigDecimal("1.00")` nên kiểm tra thế nào nếu chỉ cần cùng giá trị số học?  
**Đáp án:** `a.compareTo(b) == 0`. `equals()` còn xét cả scale.

**Câu 6.** Khi kiểm thử giá trị tiền tệ phải chính xác đến hai chữ số theo quy tắc đã phê duyệt, có nên dùng một epsilon tùy ý không?  
**Đáp án:** Không. Tính toán và làm tròn theo chính sách chính thức rồi kiểm tra giá trị (và scale nếu hợp đồng yêu cầu) bằng `BigDecimal`.

**Câu 7.** `Math.ulp(1.0)` có phải “epsilon chuẩn cho mọi thuật toán” không?  
**Đáp án:** Không; ULP thay đổi theo miền giá trị và không phản ánh mặc định sai số đo, sai số thuật toán hay mức lệch mà nghiệp vụ chấp nhận.

**Ghi nhớ:** Hãy so sánh **chính xác** khi hợp đồng yêu cầu chính xác; dùng **sai số tuyệt đối** theo đơn vị khi sai lệch cố định quan trọng; dùng **sai số tương đối** khi sai lệch theo độ lớn quan trọng; dùng **kết hợp có kiểm soát** cho miền rộng và gần 0. Mọi ngưỡng phải có lý do kiểm thử, không được suy ra tùy tiện chỉ từ kiểu số.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-006">Kiểm tra BigDecimal cả giá trị và scale khi hợp đồng yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra `BigDecimal` cả giá trị và scale khi hợp đồng yêu cầu

## 1. Vì sao kiểm thử `BigDecimal` phải xác định rõ hợp đồng?

Một `BigDecimal` chứa **giá trị thập phân** và **scale** (vị trí dấu thập phân). Hai đối tượng có thể cùng giá trị toán học nhưng khác scale:

~~~java
BigDecimal a = new BigDecimal("12.0");
BigDecimal b = new BigDecimal("12.00");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));          // false
System.out.println(a.scale());            // 1
System.out.println(b.scale());            // 2
~~~

Về số học, cả hai đều bằng 12. Tuy nhiên, `12.0` lưu unscaled value 120 và scale 1; `12.00` lưu unscaled value 1200 và scale 2. Hai cách biểu diễn này có thể mang ý nghĩa khác nhau đối với định dạng đầu ra, chính sách phần thập phân, hợp đồng dữ liệu hoặc kiểm thử.

**Quy tắc quan trọng:** Trước khi viết phép kiểm tra, phải hỏi hợp đồng yêu cầu gì:

1. Chỉ cần **giá trị toán học** bằng nhau?
2. Cần **giá trị và scale** bằng nhau?
3. Cần một **scale chuẩn cụ thể**, ví dụ đúng 2, dù nguồn có thể truyền scale khác?
4. Cần **biểu diễn văn bản** giống hệt, gồm chữ số 0 tận cùng, dấu cộng, ký pháp mũ hay định dạng locale?

Đây là các yêu cầu khác nhau. Không có một phép so sánh duy nhất phù hợp mọi trường hợp.

## 2. Hiểu rõ giá trị, scale và precision

Về cấu trúc:

~~~text
Giá trị = unscaledValue × 10^(-scale)
~~~

Ví dụ:

| Cách tạo | Giá trị toán học | Unscaled value | Scale | Precision |
| --- | --- | ---: | ---: | ---: |
| `new BigDecimal("12")` | 12 | 12 | 0 | 2 |
| `new BigDecimal("12.0")` | 12 | 120 | 1 | 3 |
| `new BigDecimal("12.00")` | 12 | 1200 | 2 | 4 |
| `new BigDecimal("0.000")` | 0 | 0 | 3 | 1 |
| `new BigDecimal("1E+3")` | 1000 | 1 | -3 | 1 |

**Scale** không phải precision:

- Scale có thể **dương, bằng 0 hoặc âm**. Scale âm biểu diễn bội của 10 mũ dương, chẳng hạn `1E+3` có scale -3.
- Precision là số chữ số thập phân trong giá trị nguyên không scale, bỏ dấu; với zero, precision là 1.
- Hai số bằng nhau về giá trị có thể có precision lẫn scale khác nhau.
- `BigDecimal` không giữ trọn văn bản gốc: các ký tự `+0012.00` và `12.00` tạo ra đối tượng có cùng giá trị và scale, dù chuỗi nguồn khác nhau.

Nếu kiểm thử yêu cầu giữ nguyên **từng ký tự** đầu vào, chỉ so sánh `BigDecimal` là chưa đủ. Cần giữ và kiểm tra cả văn bản hoặc một bản ghi biểu diễn riêng.

## 3. `compareTo()` kiểm tra giá trị toán học

Phương thức:

~~~java
int comparison = expected.compareTo(actual);
~~~

trả số âm, 0 hoặc số dương tùy giá trị của `expected` nhỏ hơn, bằng hoặc lớn hơn `actual`. Khi cần kiểm tra bằng nhau **chỉ về giá trị số học**, dùng:

~~~java
boolean sameNumericValue = expected.compareTo(actual) == 0;
~~~

Các cặp ví dụ:

| Giá trị thứ nhất | Giá trị thứ hai | `compareTo(...) == 0` |
| --- | --- | --- |
| `1.0` | `1.00` | `true` |
| `0` | `0.000` | `true` |
| `1000` | `1E+3` | `true` |
| `12.00` | `12.01` | `false` |
| `-2.50` | `-2.5` | `true` |

`compareTo` phù hợp khi hợp đồng chỉ yêu cầu **kết quả số học bằng nhau**, ví dụ tổng 10.0 và 10.00 là tương đương về giá trị.

**Không nên so sánh `BigDecimal` bằng cách đổi sang `double` rồi so sánh gần đúng** nếu chính xác thập phân là mục tiêu. Ép sang `double` có thể làm tròn hoặc tràn phạm vi và khiến hai giá trị khác nhau thành cùng một giá trị nhị phân.

Tuy nhiên, `compareTo` **cố ý bỏ qua sự khác nhau về scale**. Nó không chứng minh đầu ra đáp ứng yêu cầu “phải có đúng hai chữ số thập phân ở dạng `BigDecimal`”.

## 4. `equals()` kiểm tra cả giá trị và scale

`BigDecimal.equals(Object)` yêu cầu cả giá trị và scale giống nhau. Có thể dùng:

~~~java
BigDecimal expected = new BigDecimal("12.00");
BigDecimal actual = new BigDecimal("12.0");

System.out.println(expected.equals(actual)); // false
System.out.println(expected.compareTo(actual) == 0); // true
~~~

Nếu hai đối tượng là `BigDecimal` hợp lệ, `equals` bằng `true` khi **cùng giá trị và cùng scale**. Một cách diễn đạt tường minh tương đương:

~~~java
boolean sameValueAndScale =
        expected.compareTo(actual) == 0
        && expected.scale() == actual.scale();
~~~

Nếu `expected` và `actual` có thể `null`, cần có chính sách null riêng. `Objects.equals(expected, actual)` hỗ trợ so sánh null an toàn nhưng vẫn sử dụng ngữ nghĩa `BigDecimal.equals`; hai giá trị null cùng nhau sẽ được xem là bằng nhau, điều này có thể **không hợp lệ** nếu hợp đồng bắt buộc có giá trị.

### 4.1. `equals` không phải kiểm tra định dạng chuỗi

Hai chuỗi sau khác nhau về mặt ký tự:

~~~java
String rawA = "+0012.00";
String rawB = "12.00";
~~~

Nhưng `new BigDecimal(rawA).equals(new BigDecimal(rawB))` là `true`. Constructor đã chuyển chuỗi thành số và không giữ nguyên ký tự cộng hay các zero đứng đầu.

Vì vậy:

- **Chỉ giá trị:** kiểm tra `compareTo == 0`.
- **Giá trị và scale:** dùng `equals` hoặc hai assertion tường minh.
- **Chuỗi hiển thị/trao đổi:** kiểm tra `String` sau khi format/serialize theo hợp đồng, ngoài kiểm tra giá trị số khi cần.

## 5. Viết assertion rõ ràng trong kiểm thử

### 5.1. Kiểm thử chỉ yêu cầu giá trị

Nếu dùng JUnit 5:

~~~java
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;

BigDecimal expected = new BigDecimal("12.00");
BigDecimal actual = new BigDecimal("12.0");

assertEquals(0, expected.compareTo(actual));
~~~

Đây là **đoạn mã đặt bên trong phương thức test JUnit 5**, không phải chương trình chạy độc lập. Câu assertion nói rõ rằng **khác scale được phép**.

Nếu muốn thông báo thất bại mang ý nghĩa nghiệp vụ:

~~~java
assertEquals(0, expected.compareTo(actual),
        "Gia tri thap phan phai bang nhau");
~~~

### 5.2. Kiểm thử cần cả giá trị và scale

~~~java
BigDecimal expected = new BigDecimal("12.00");
BigDecimal actual = new BigDecimal("12.0");

assertEquals(0, expected.compareTo(actual), "Sai gia tri");
assertEquals(expected.scale(), actual.scale(), "Sai scale");
~~~

Đoạn này sẽ **thất bại ở assertion scale**: expected scale = 2, actual scale = 1. Chia hai assertion giúp chẩn đoán nhanh **giá trị sai** hay **chỉ sai cách biểu diễn scale**.

Nếu chỉ cần một assertion, `assertEquals(expected, actual)` của JUnit sử dụng `equals` nên cũng kiểm tra cả hai. Việc tách assertion có giá trị khi thông báo lỗi cần nhấn mạnh hai khía cạnh khác nhau.

Trong production, tránh dùng câu lệnh Java `assert condition;` làm cơ chế thực thi hợp đồng vì Java assertion mặc định có thể bị tắt nếu không chạy với `-ea`. Trong kiểm thử, dùng API assertions của framework; ở ranh giới dữ liệu production, dùng kiểm tra và exception tường minh.

### 5.3. Nếu yêu cầu scale cố định

Có hợp đồng không cần giữ scale giống một đối tượng expected, mà quy định **mọi kết quả phải có scale = 2**:

~~~java
BigDecimal actual = new BigDecimal("12.00");

assertEquals(0, actual.compareTo(new BigDecimal("12")));
assertEquals(2, actual.scale());
~~~

Đây là yêu cầu **giá trị bằng 12 và scale đúng 2**. Tách hai điều kiện là cách dễ thấy nhất để xác nhận hợp đồng.

Không thay assertion scale bằng `actual.toPlainString().endsWith("00")`: kiểm tra ký tự cuối không chứng minh đúng số chữ số sau dấu thập phân hoặc giá trị mong muốn.

## 6. Chuẩn hóa scale: `setScale` phải có chính sách rõ

Khi các nguồn vào có scale khác nhau nhưng contract quy định scale chuẩn, có thể chuẩn hóa ở **ranh giới nhập/xuất hoặc tầng domain**:

~~~java
BigDecimal raw = new BigDecimal("12.0");
BigDecimal canonical = raw.setScale(2, RoundingMode.UNNECESSARY);

System.out.println(canonical); // 12.00
System.out.println(canonical.scale()); // 2
~~~

`setScale(2, UNNECESSARY)` yêu cầu kết quả giữ nguyên giá trị khi chuyển sang scale 2. Các tình huống:

| Giá trị đầu vào | `setScale(2, UNNECESSARY)` | Lý do |
| --- | --- | --- |
| `12` | `12.00` | Thêm zero, không đổi giá trị |
| `12.0` | `12.00` | Thêm zero |
| `12.000` | `12.00` | Bỏ zero dư |
| `12.345` | `ArithmeticException` | Không thể bỏ chữ số 5 mà giữ đúng giá trị |
| `-0.000` | `0.00` | `BigDecimal` không bảo toàn zero âm theo IEEE 754 |

Nếu hợp đồng **cho phép làm tròn**, có thể chọn `HALF_UP` hoặc `HALF_EVEN`, chẳng hạn:

~~~java
BigDecimal rounded =
        new BigDecimal("12.345").setScale(2, RoundingMode.HALF_UP);
// 12.35
~~~

Lúc này giá trị đã thay đổi theo quy tắc được chọn. Hãy **kiểm tra giá trị sau làm tròn**, đồng thời kiểm tra scale. Không tự chuẩn hóa bằng `HALF_UP` chỉ để một bài test từ đỏ thành xanh, bởi hành động đó có thể che mất lỗi hợp đồng “không được làm tròn”.

### 6.1. Không sửa cả expected và actual bằng cùng cách rồi coi như xong

Một anti-pattern:

~~~java
// Che lỗi scale nếu hợp đồng đòi kết quả có scale = 2:
BigDecimal e = expected.stripTrailingZeros();
BigDecimal a = actual.stripTrailingZeros();
assertEquals(e, a);
~~~

Cách này bỏ khác biệt về scale trước khi kiểm tra. Nó phù hợp **chỉ khi** hợp đồng thực chất là so sánh giá trị, lúc đó hãy dùng `compareTo` để biểu đạt trực tiếp.

Ngược lại, nếu contract cho phép mọi biểu diễn đầu vào nhưng bắt buộc API xuất trả scale 2, hãy chuẩn hóa **ở đúng nơi sản xuất kết quả** rồi assert đầu ra có scale 2. Không nên để test tự sửa `actual` trước khi kiểm tra, vì có thể làm mất lỗi của code cần kiểm thử.

## 7. Cạm bẫy `stripTrailingZeros()`: scale có thể thành số âm

Phương thức `stripTrailingZeros()` loại bỏ số 0 tận cùng trong cách biểu diễn của `BigDecimal`, có thể làm scale giảm, kể cả sang **giá trị âm**:

~~~java
BigDecimal x = new BigDecimal("1000.00");
BigDecimal stripped = x.stripTrailingZeros();

System.out.println(stripped);         // 1E+3
System.out.println(stripped.scale()); // -3
~~~

Giá trị vẫn bằng 1000, nhưng biểu diễn `1E+3` có scale -3. Nếu hợp đồng yêu cầu scale 2, việc gọi `stripTrailingZeros` làm đầu ra **vi phạm hợp đồng** dù giá trị số học không thay đổi.

Với zero:

~~~java
BigDecimal zero = new BigDecimal("0.000");
System.out.println(zero.scale()); // 3
System.out.println(zero.stripTrailingZeros().scale()); // 0
~~~

Zero sau `stripTrailingZeros()` được chuẩn hóa về `BigDecimal.ZERO` có scale 0. Khi kiểm thử hợp đồng “số không phải biểu diễn `0.00`”, cần cố ý tạo hoặc kiểm tra scale 2, ví dụ `BigDecimal.ZERO.setScale(2)`.

**Không xem `stripTrailingZeros` là cách chuẩn hóa scale về một scale cố định.** Nó là phép loại bỏ zero dư, có kết quả scale phụ thuộc vào giá trị cụ thể.

## 8. Những phép toán nào làm thay đổi scale?

Khi test các pipeline tính toán, không nên giả định scale đầu ra luôn trùng scale của một đầu vào. `BigDecimal` có các quy tắc preferred scale:

| Phép toán chính xác | Preferred scale điển hình |
| --- | --- |
| `a.add(b)`, `a.subtract(b)` | `max(a.scale(), b.scale())` |
| `a.multiply(b)` | `a.scale() + b.scale()` |
| `a.divide(b)` khi kết quả chính xác có biểu diễn thập phân hữu hạn | `a.scale() - b.scale()` là preferred scale; kết quả có thể cần scale lớn hơn |
| `a.setScale(target, mode)` | `target` khi thành công |
| `a.stripTrailingZeros()` | Có thể giảm, về 0 hoặc âm |

Ví dụ:

~~~java
BigDecimal first = new BigDecimal("1.20"); // scale 2
BigDecimal second = new BigDecimal("3.0"); // scale 1

System.out.println(first.add(second));      // 4.20, scale 2
System.out.println(first.multiply(second)); // 3.600, scale 3
~~~

Phép chia với `MathContext`, phép `round(context)`, hoặc thao tác có quy tắc làm tròn cụ thể có thể tạo scale khác các phép toán chính xác đơn giản. Vì vậy nếu API cam kết scale cụ thể, hãy kiểm tra scale **ở điểm trả kết quả**, không suy luận chỉ từ scale đầu vào.

**Precision cũng là khái niệm khác:** `new MathContext(3, HALF_UP)` giới hạn ba chữ số có nghĩa. Nó **không** tự bảo đảm scale 3 hoặc scale 2. Nếu cả precision và scale đều là điều kiện hợp đồng, phải kiểm tra cả hai theo chính sách có thứ tự rõ ràng.

## 9. Kết quả trong tập hợp và dữ liệu tra cứu

`BigDecimal.compareTo` và `BigDecimal.equals` có cách nhận định bằng nhau khác nhau. Điều này ảnh hưởng đến collection:

~~~java
BigDecimal a = new BigDecimal("12.0");
BigDecimal b = new BigDecimal("12.00");

Set<BigDecimal> hashSet = new HashSet<>(List.of(a, b));
Set<BigDecimal> treeSet = new TreeSet<>(List.of(a, b));

System.out.println(hashSet.size()); // 2
System.out.println(treeSet.size()); // 1
~~~

Đây là đoạn mã minh họa với import `java.math.BigDecimal` và `java.util.*`.

- `HashSet` dựa trên `equals`/`hashCode`, nên giữ hai `BigDecimal` khác scale thành **hai phần tử**.
- `TreeSet` dùng thứ tự tự nhiên `compareTo` để xác định phần tử bằng nhau theo comparator, nên xem hai giá trị bằng nhau về số học là **một phần tử**.

Khi dùng `BigDecimal` làm khóa trong `HashMap` hoặc `TreeMap`, hãy chọn collection có ngữ nghĩa phù hợp: đối tượng khác scale là khóa khác hay không? Đừng giả định chuyển từ map dạng hash sang map có sắp thứ tự chỉ ảnh hưởng thứ tự duyệt.

Nếu muốn các giá trị có scale khác nhau là cùng một khóa, nên thiết kế quy tắc chuẩn hóa rõ ràng; nếu muốn khác biệt scale có ý nghĩa, giữ ngữ nghĩa `equals` và kiểm thử cấu trúc dữ liệu tương ứng.

## 10. Test dữ liệu qua API, JSON, database

### 10.1. API/JSON

Giả sử một API công bố:

~~~json
{
  "amount": "12.00",
  "currency": "USD"
}
~~~

Nếu `amount` là chuỗi chuẩn với **đúng hai chữ số sau dấu thập phân**, có thể cần hai lớp assertion:

1. Parse `"12.00"` thành `BigDecimal` và xác minh **giá trị**, **scale**.
2. Kiểm tra chuỗi trao đổi đúng pattern, ví dụ **đúng hai chữ số ở phần thập phân, dấu chấm, không phân nhóm**.

`BigDecimal.equals` không bảo đảm chuỗi gốc không có dấu `+` hay zero dẫn đầu. Dù cùng một giá trị và scale, `"+0012.00"` và `"12.00"` khác nhau về văn bản. Nếu hợp đồng yêu cầu biểu diễn canonical, nên kiểm tra thêm trường chuỗi.

Nếu hợp đồng chỉ yêu cầu **giá trị thập phân bằng nhau**, không nên tự áp assertion scale chặt hơn đặc tả. Một producer có thể serialize cùng giá trị dưới các dạng `12`, `12.0` hoặc `12.00` mà vẫn thỏa yêu cầu giá trị. JSON number và JSON string cũng có cách parse khác nhau tùy thư viện và consumer; hãy kiểm tra đúng kiểu dữ liệu contract quy định.

### 10.2. JDBC và cột `DECIMAL`/`NUMERIC`

Các cột số thập phân ở database thường có precision/scale được khai báo trong schema, ví dụ `DECIMAL(12,2)`. Nhưng **cách driver/database trả về `BigDecimal` có thể chịu ảnh hưởng schema và hệ quản trị cụ thể**. Không suy ra một scale Java cụ thể từ dữ liệu mẫu mà không kiểm chứng hệ thống thực.

Nếu hợp đồng yêu cầu số đã đọc về có scale 2, kiểm tra tại boundary JDBC/repository. Nếu yêu cầu chỉ là cùng giá trị toán học, tránh để test thất bại chỉ vì scale khác mà schema hoặc driver cho phép.

Không biến `BigDecimal` sang `double` làm trung gian khi so sánh kết quả database nếu độ chính xác thập phân là yêu cầu. Việc làm tròn nhị phân có thể che hoặc tạo sai lệch.

### 10.3. Định dạng hiển thị

`DecimalFormat` hoặc `NumberFormat` có thể in `12.0` thành `12.00` mà **không sửa `BigDecimal` đầu vào**. Nếu hợp đồng chỉ nói “UI phải hiển thị hai số sau dấu chấm”, nên test chuỗi format. Nếu hợp đồng nói “domain trả `BigDecimal` scale 2”, phải test `scale()`. Hai phép kiểm tra khác nhau.

## 11. Ví dụ Java 17+ chạy độc lập: hai loại hợp đồng

Tạo tệp **BigDecimalContractDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

public class BigDecimalContractDemo {
    private static void requireNumeric(
            BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            throw new AssertionError("Numeric value differs");
        }
    }

    private static void requireValueAndScale(
            BigDecimal expected, BigDecimal actual) {
        requireNumeric(expected, actual);
        if (expected.scale() != actual.scale()) {
            throw new AssertionError("Scale differs");
        }
    }

    private static boolean passes(Runnable assertion) {
        try {
            assertion.run();
            return true;
        } catch (AssertionError ex) {
            return false;
        }
    }

    public static void main(String[] args) {
        BigDecimal expected = new BigDecimal("12.00");
        BigDecimal actual = new BigDecimal("12.0");

        System.out.println("compareTo zero: "
                + (expected.compareTo(actual) == 0));
        System.out.println("equals: " + expected.equals(actual));
        System.out.println("numeric assertion: "
                + passes(() -> requireNumeric(expected, actual)));
        System.out.println("value and scale assertion: "
                + passes(() -> requireValueAndScale(expected, actual)));

        BigDecimal normalized =
                actual.setScale(2, RoundingMode.UNNECESSARY);
        System.out.println("normalized: " + normalized.toPlainString());
        System.out.println("normalized assertion: "
                + passes(() -> requireValueAndScale(expected, normalized)));

        System.out.println("HashSet size: "
                + new HashSet<>(List.of(expected, actual)).size());
        System.out.println("TreeSet size: "
                + new TreeSet<>(List.of(expected, actual)).size());

        BigDecimal stripped =
                new BigDecimal("1000.00").stripTrailingZeros();
        System.out.println("stripped: " + stripped
                + ", scale: " + stripped.scale());

        BigDecimal zero =
                new BigDecimal("0.000").stripTrailingZeros();
        System.out.println("zero stripped scale: " + zero.scale());

        try {
            new BigDecimal("12.345")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("strict scale: ArithmeticException");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalContractDemo.java
java BigDecimalContractDemo
~~~

Đầu ra chính xác:

~~~text
compareTo zero: true
equals: false
numeric assertion: true
value and scale assertion: false
normalized: 12.00
normalized assertion: true
HashSet size: 2
TreeSet size: 1
stripped: 1E+3, scale: -3
zero stripped scale: 0
strict scale: ArithmeticException
~~~

Chương trình sử dụng `AssertionError` để mô phỏng một phép assertion chủ động; `passes` chỉ giúp in ra `true`/`false` mà không dừng toàn bộ demo. Trong kiểm thử thật, **đừng bao lỗi assertion bằng `passes` rồi bỏ qua kết quả**: để JUnit báo test failed với tên trường hợp và thông tin expected/actual.

## 12. Các trường hợp kiểm thử cần có

Nên lập bộ test dựa trên chính hợp đồng, gồm các nhóm sau:

| Trường hợp | Giá trị mẫu | Điều cần xác minh |
| --- | --- | --- |
| Bằng giá trị, khác scale | `12.0` vs `12.00` | Chọn `compareTo` hay `equals` đúng yêu cầu |
| Bằng 0, khác scale | `0` vs `0.000` | Zero vẫn có scale |
| Dạng lũy thừa | `1E+3` vs `1000.00` | `compareTo` bằng 0; scale khác |
| Chuyển sang scale lớn hơn | `12` thành `12.00` | Không mất giá trị |
| Giảm scale nhờ zero dư | `12.000` thành `12.00` | Chuyển chính xác |
| Giảm scale làm mất chữ số | `12.345` sang scale 2 | Exception khi `UNNECESSARY`, hoặc làm tròn nếu được phép |
| `stripTrailingZeros` | `1000.00` | Scale có thể âm |
| Tính toán tổng | `1.20 + 2.345` | Giá trị chính xác và preferred scale |
| Pipeline serialize/parse | `"12.00"` qua JSON/JDBC | Giá trị, scale, chuỗi theo contract riêng |
| Đầu vào null | `null` | Từ chối hay chấp nhận có chủ đích |
| Số âm, số lớn và số rất nhỏ | `-0.0010`, `1E+20` | Không suy diễn scale theo độ lớn |

Một bộ kiểm thử đủ mạnh có thể gồm **test numeric**, **test value-and-scale**, **test canonical string** và **test chuyển đổi scale không mất giá trị**, tùy contract. Không cần ép mọi use case đều phải so scale: điều đó có thể biến khác biệt biểu diễn vô hại thành lỗi giả.

## 13. Các hiểu lầm thường gặp

| Nhận định sai | Điều đúng |
| --- | --- |
| Hai `BigDecimal` cùng số thì `equals` luôn `true` | `equals` xét cả scale |
| `compareTo == 0` chứng minh scale bằng nhau | Nó chỉ chứng minh cùng giá trị toán học |
| `setScale(2, UNNECESSARY)` luôn thành công | Nó thất bại nếu muốn giữ giá trị mà phải loại chữ số khác 0 |
| `stripTrailingZeros` chuẩn hóa thành scale 0 | Scale có thể âm, như `1000.00 -> 1E+3` |
| `precision == scale` | Precision và scale là hai khái niệm độc lập |
| Test hiển thị chuỗi `12.00` đủ chứng minh domain scale 2 | Formatter có thể tạo chuỗi đó từ domain scale 1 |
| `HashSet` và `TreeSet` xem các `BigDecimal` bằng nhau theo cách giống nhau | Hash dùng `equals`/`hashCode`; tree dùng `compareTo` |
| Chuyển sang `double` sẽ làm so sánh đơn giản hơn | Có thể mất độ chính xác và làm sai kết quả |
| Dữ liệu từ SQL `DECIMAL` luôn có scale Java đúng như ta đoán | Phải dựa vào contract, schema và hành vi driver thực tế |
| Assertion Java luôn được chạy khi triển khai | Câu lệnh `assert` có thể bị vô hiệu hóa nếu không bật assertions |

## 14. Tự kiểm tra

1. `new BigDecimal("1.00").equals(new BigDecimal("1.0"))` trả gì? **`false`**, vì scale lần lượt là 2 và 1.
2. Cặp trên có `compareTo == 0` không? **Có**, vì giá trị toán học bằng nhau.
3. Khi một DTO cam kết amount có đúng scale 2, chỉ dùng `compareTo` đã đủ chưa? **Chưa**; phải kiểm tra scale 2.
4. Vì sao `new BigDecimal("1000.00").stripTrailingZeros().scale()` là -3? **Vì dạng kết quả `1E+3` giữ nguyên giá trị với unscaled value 1 và scale -3**.
5. `new BigDecimal("12.340").setScale(2, UNNECESSARY)` có thành công không? **Có**, kết quả `12.34`, vì chỉ bỏ zero dư.
6. `new BigDecimal("12.345").setScale(2, UNNECESSARY)` có thành công không? **Không**, vì không thể giữ nguyên giá trị ở scale 2.
7. Khi nào `assertEquals(expected, actual)` hợp lý hơn `assertEquals(0, expected.compareTo(actual))`? **Khi hợp đồng yêu cầu cùng cả giá trị và scale**.
8. Nếu API yêu cầu giữ chính xác văn bản `"0012.00"`, kiểm tra `BigDecimal.equals` có đủ không? **Không**, phải kiểm tra chuỗi gốc hoặc chuỗi xuất đúng quy tắc biểu diễn.

**Ghi nhớ:** Đối với `BigDecimal`, **bằng nhau về số** (`compareTo == 0`) và **bằng nhau về số lẫn scale** (`equals`, hoặc hai phép kiểm tra rõ ràng) không đồng nghĩa. Hãy viết assertion theo hợp đồng dữ liệu; chỉ chuẩn hóa scale khi chính sách cho phép và đừng vô tình bỏ scale bằng `stripTrailingZeros` trước khi kiểm thử một yêu cầu cần giữ scale.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-007">Đối chiếu mọi RoundingMode tại trường hợp đúng giữa</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu mọi `RoundingMode` tại trường hợp đúng giữa trong Java

## 1. Bài toán: cùng một số ở “đúng giữa”, vì sao kết quả khác nhau?

Khi số thập phân cần giảm số chữ số sau dấu phẩy hoặc giảm số chữ số có nghĩa, **làm tròn** là quyết định phải thực hiện rõ ràng. Một tình huống dễ nhầm nhất là khi giá trị nằm **đúng giữa hai kết quả biểu diễn được** tại độ chính xác đang yêu cầu.

Chẳng hạn, yêu cầu làm tròn `2.5` thành số nguyên:

- Kết quả gần nhất có thể là `2` hoặc `3`.
- Khoảng cách từ `2.5` đến `2` bằng khoảng cách đến `3`, đều là `0.5`.
- Chế độ làm tròn (*rounding mode*) quyết định phải chọn `2`, `3` hay từ chối phép tính.

Với `-2.5`, hai kết quả nguyên lân cận là `-3` và `-2`; việc “làm tròn lên” có thể gây hiểu lầm nếu không phân biệt **về phía +∞**, **về phía −∞**, **về phía 0** và **ra xa 0**.

Trong Java 17+, các chế độ được định nghĩa bằng enum `java.math.RoundingMode` và thường dùng với `BigDecimal.setScale(...)`, `BigDecimal.divide(...)` hoặc `MathContext`.

**Mục tiêu:** đối chiếu **tất cả tám** giá trị `RoundingMode` ở điểm đúng giữa cho cả số dương và số âm; hiểu quy tắc với trường hợp không đúng giữa; biết khi nào `UNNECESSARY` hợp lệ; tạo bộ test có kết quả xác định và tránh lỗi do đầu vào `double`.

## 2. Định nghĩa “đúng giữa” phải gắn với độ chính xác đích

Một số **đúng giữa** (*exact halfway/tie*) là số có cùng khoảng cách tới hai kết quả lân cận có thể biểu diễn ở **scale hoặc precision được chọn**.

Ví dụ với `setScale(0, mode)`, đích là các số nguyên:

| Số gốc | Hai số nguyên lân cận | Có đúng giữa? |
| --- | --- | --- |
| `2.5` | `2` và `3` | Có |
| `-2.5` | `-3` và `-2` | Có |
| `3.5` | `3` và `4` | Có |
| `2.5000` | `2` và `3` | Có, các số 0 cuối không đổi giá trị |
| `2.5001` | `2` và `3` | Không, gần `3` hơn |
| `2.4999` | `2` và `3` | Không, gần `2` hơn |

Với `setScale(1, mode)`, các bước giữa các kết quả hợp lệ có kích thước `0.1`. Lúc này `2.25` ở đúng giữa `2.2` và `2.3`; `2.35` ở đúng giữa `2.3` và `2.4`. Nhưng `2.5` đã có một chữ số thập phân, nên **không cần làm tròn** ở scale 1.

Do đó không thể nói “giá trị có đuôi `.5` là tie” mà thiếu **scale đích**. Với phép làm tròn theo `MathContext`, tie còn phụ thuộc **số chữ số có nghĩa** được yêu cầu, không đơn giản là số vị trí sau dấu phẩy.

Dùng chuỗi thập phân để tạo giá trị test:

```java
BigDecimal value = new BigDecimal("2.5000");
BigDecimal rounded = value.setScale(0, RoundingMode.HALF_EVEN);
// rounded là 2
```

Cách này giữ **chính xác** giá trị thập phân mong muốn. Tránh viết `new BigDecimal(2.675)` cho testcase thập phân “đúng giữa”, vì số `double` đầu vào có thể chỉ là giá trị nhị phân *gần* số thập phân đã gõ.

## 3. Tám `RoundingMode`: chính xác ý nghĩa từng chế độ

`RoundingMode` gồm tám giá trị: `UP`, `DOWN`, `CEILING`, `FLOOR`, `HALF_UP`, `HALF_DOWN`, `HALF_EVEN`, `UNNECESSARY`.

| Chế độ | Quy tắc chính | Với tie dương `+2.5` tới scale 0 | Với tie âm `-2.5` tới scale 0 |
| --- | --- | ---: | ---: |
| `UP` | Nếu phải bỏ phần khác 0, làm tròn **ra xa 0** | `3` | `-3` |
| `DOWN` | Nếu phải bỏ phần khác 0, làm tròn **về 0** | `2` | `-2` |
| `CEILING` | Làm tròn **về +∞** | `3` | `-2` |
| `FLOOR` | Làm tròn **về −∞** | `2` | `-3` |
| `HALF_UP` | Chọn gần nhất; **nếu tie**, chọn **ra xa 0** | `3` | `-3` |
| `HALF_DOWN` | Chọn gần nhất; **nếu tie**, chọn **về 0** | `2` | `-2` |
| `HALF_EVEN` | Chọn gần nhất; **nếu tie**, chọn kết quả có chữ số cuối giữ lại **chẵn** | `2` | `-2` |
| `UNNECESSARY` | **Cấm mọi trường hợp phải làm tròn** | Lỗi | Lỗi |

Đây là **bảng đối chiếu tại tie**; không nên suy ra `UP` và `HALF_UP` giống nhau cho mọi số, hoặc `DOWN` và `HALF_DOWN` giống nhau cho mọi số. Chúng chỉ có thể cho cùng kết quả ở một số trường hợp.

### 3.1. `UP` khác “làm tròn lên theo trục số”

`UP` nghĩa là **ra xa số 0** khi phải bỏ phần khác 0:

```java
new BigDecimal("2.1").setScale(0, RoundingMode.UP);   // 3
new BigDecimal("-2.1").setScale(0, RoundingMode.UP);  // -3
```

Với số âm, kết quả `-3` lại **nhỏ hơn** `-2.1` theo trục số. Vì vậy đừng dịch đơn giản `UP` thành “chọn số lớn hơn”.

`DOWN` nghĩa là **về 0**:

```java
new BigDecimal("2.9").setScale(0, RoundingMode.DOWN);   // 2
new BigDecimal("-2.9").setScale(0, RoundingMode.DOWN);  // -2
```

Đây không phải `Math.floor` với số âm; `FLOOR` của `-2.9` là `-3`, còn `DOWN` là `-2`.

### 3.2. `CEILING` và `FLOOR` phụ thuộc hướng trên trục số

- `CEILING`: số biểu diễn được **nhỏ nhất không nhỏ hơn** số gốc.
- `FLOOR`: số biểu diễn được **lớn nhất không lớn hơn** số gốc.

Ở scale 0:

```java
new BigDecimal("2.5").setScale(0, RoundingMode.CEILING);   // 3
new BigDecimal("-2.5").setScale(0, RoundingMode.CEILING);  // -2

new BigDecimal("2.5").setScale(0, RoundingMode.FLOOR);     // 2
new BigDecimal("-2.5").setScale(0, RoundingMode.FLOOR);    // -3
```

Với số dương, `CEILING` thường giống `UP` khi cần bỏ chữ số và `FLOOR` giống `DOWN`. Với số âm thì ngược lại. Quy tắc này áp dụng không chỉ tại tie mà với mọi phần cần bỏ khác 0.

### 3.3. Ba chế độ `HALF_*`: chọn gần nhất rồi xử lý tie

Cả `HALF_UP`, `HALF_DOWN` và `HALF_EVEN` đều **ưu tiên kết quả gần số gốc nhất**. Chúng chỉ khác nhau khi khoảng cách đến hai kết quả lân cận **bằng nhau tuyệt đối**:

- `HALF_UP`: tie → ra xa 0.
- `HALF_DOWN`: tie → về 0.
- `HALF_EVEN`: tie → chữ số cuối cùng **được giữ lại** là chẵn.

`HALF_EVEN` còn được gọi là *round half to even* hoặc *banker's rounding* trong một số tài liệu. Tên gọi không có nghĩa mọi giao dịch ngân hàng đều bắt buộc dùng nó; quy tắc nghiệp vụ, hợp đồng hoặc pháp luật mới xác định chế độ.

Chẳng hạn tại scale 0:

- `2.5` → `2` với `HALF_EVEN`, vì `2` chẵn và `3` lẻ.
- `3.5` → `4` với `HALF_EVEN`, vì `4` chẵn và `3` lẻ.
- `-2.5` → `-2` với `HALF_EVEN`, vì chữ số đơn vị `2` chẵn.
- `-3.5` → `-4` với `HALF_EVEN`, vì chữ số đơn vị `4` chẵn.

Chú ý `HALF_EVEN` **không phải luôn làm tròn xuống**: kết quả với `3.5` là `4`.

### 3.4. `UNNECESSARY`: một hợp đồng không được mất thông tin

`RoundingMode.UNNECESSARY` yêu cầu phép toán chính xác **không cần làm tròn**. Nếu số không thể biểu diễn ở scale/precision yêu cầu mà không bỏ phần có giá trị, API ném `ArithmeticException`.

```java
new BigDecimal("2.00").setScale(0, RoundingMode.UNNECESSARY);
// 2 — hợp lệ: chỉ bỏ các số 0 không có giá trị.

new BigDecimal("2.50").setScale(0, RoundingMode.UNNECESSARY);
// ArithmeticException — cần bỏ phần 0.5 có giá trị.
```

`UNNECESSARY` không có ý nghĩa “làm tròn theo mặc định”; đó là **yêu cầu cấm làm tròn**. Hữu ích khi cần validation dữ liệu, đơn vị tiền nhỏ nhất, hoặc yêu cầu kết quả chính xác tuyệt đối. Thay nó bằng `DOWN` để tránh exception sẽ **thay đổi hợp đồng**.

## 4. Ma trận kết quả đầy đủ tại `±2.5` và `±3.5`

Các giá trị đầu vào đều là chuỗi `BigDecimal` chính xác; mục tiêu là `scale = 0`.

| `RoundingMode` | `+2.5` | `-2.5` | `+3.5` | `-3.5` |
| --- | ---: | ---: | ---: | ---: |
| `UP` | `3` | `-3` | `4` | `-4` |
| `DOWN` | `2` | `-2` | `3` | `-3` |
| `CEILING` | `3` | `-2` | `4` | `-3` |
| `FLOOR` | `2` | `-3` | `3` | `-4` |
| `HALF_UP` | `3` | `-3` | `4` | `-4` |
| `HALF_DOWN` | `2` | `-2` | `3` | `-3` |
| `HALF_EVEN` | `2` | `-2` | `4` | `-4` |
| `UNNECESSARY` | **lỗi** | **lỗi** | **lỗi** | **lỗi** |

Hai cột `2.5` kiểm tra dấu; hai cột `3.5` kiểm tra **parity của HALF_EVEN**. Nếu chỉ test `2.5`, một implementation sai “HALF_EVEN luôn lấy số nhỏ hơn về độ lớn” có thể không lộ lỗi.

Một điểm khác: `HALF_UP` ở đúng giữa âm `-2.5` cho `-3`, không phải `-2`. “UP” trong tên `HALF_UP` là **ra xa 0 khi tie**, không phải *ceiling*.

## 5. Test đúng giữa tại một chữ số sau dấu phẩy

Đối với `setScale(1, mode)`:

| Giá trị | Kết quả lân cận | `HALF_UP` | `HALF_DOWN` | `HALF_EVEN` |
| --- | --- | --- | --- | --- |
| `2.25` | `2.2` hoặc `2.3` | `2.3` | `2.2` | `2.2` |
| `2.35` | `2.3` hoặc `2.4` | `2.4` | `2.3` | `2.4` |
| `-2.25` | `-2.2` hoặc `-2.3` | `-2.3` | `-2.2` | `-2.2` |
| `-2.35` | `-2.3` hoặc `-2.4` | `-2.4` | `-2.3` | `-2.4` |

Với `2.25`, chữ số cuối giữ lại ở hai ứng viên là `2` và `3`; `HALF_EVEN` lấy `2.2`. Với `2.35`, chữ số cuối là `3` và `4`; kết quả là `2.4`. Không phải lấy **toàn bộ số nguyên** hay “chữ số trước dấu phẩy” để xét tính chẵn khi làm tròn scale 1.

Tương tự cho số âm: tính chẵn/lẻ của chữ số cuối không phụ thuộc dấu. Kết quả được chọn vẫn theo **hai ứng viên gần giá trị gốc nhất**, không tùy tiện bỏ dấu âm.

### Điểm sát tie nhưng không đúng giữa

Chỉ sai khác một chữ số nhỏ là có thể làm các `HALF_*` không còn khác nhau:

| Giá trị tới scale 0 | `HALF_UP` | `HALF_DOWN` | `HALF_EVEN` | Giải thích |
| --- | --- | --- | --- | --- |
| `2.4999` | `2` | `2` | `2` | Gần `2` hơn |
| `2.5000` | `3` | `2` | `2` | Tie thực sự |
| `2.5001` | `3` | `3` | `3` | Gần `3` hơn |
| `-2.4999` | `-2` | `-2` | `-2` | Gần `-2` hơn |
| `-2.5000` | `-3` | `-2` | `-2` | Tie thực sự |
| `-2.5001` | `-3` | `-3` | `-3` | Gần `-3` hơn |

`HALF_DOWN` **không phải lúc nào cũng hướng về 0**; nó chỉ chọn hướng đó khi đúng giữa. Ví dụ `2.5001` với `HALF_DOWN` ra `3`, còn `DOWN` ra `2`.

Đó là lý do bộ test cần cả tie, dưới tie và trên tie, không chỉ một trường hợp `.5`.

## 6. Kiểm chứng chương trình Java 17+ hoàn chỉnh

Lưu nội dung sau thành file **`RoundingMidpointDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class RoundingMidpointDemo {

    // Trả văn bản ERROR khi làm tròn là điều bị cấm.
    static String roundOrReject(
            String input, int scale, RoundingMode mode) {
        try {
            return new BigDecimal(input)
                    .setScale(scale, mode)
                    .toPlainString();
        } catch (ArithmeticException ex) {
            return "ERROR";
        }
    }

    public static void main(String[] args) {
        System.out.println(
                "Mode: +2.5, -2.5, +3.5, -3.5 (scale=0)");

        for (RoundingMode mode : RoundingMode.values()) {
            System.out.println(mode + ": "
                    + roundOrReject("2.5", 0, mode) + ", "
                    + roundOrReject("-2.5", 0, mode) + ", "
                    + roundOrReject("3.5", 0, mode) + ", "
                    + roundOrReject("-3.5", 0, mode));
        }

        System.out.println("HALF_EVEN 2.25 => "
                + roundOrReject("2.25", 1, RoundingMode.HALF_EVEN));
        System.out.println("HALF_EVEN 2.35 => "
                + roundOrReject("2.35", 1, RoundingMode.HALF_EVEN));
        System.out.println("HALF_EVEN -2.25 => "
                + roundOrReject("-2.25", 1, RoundingMode.HALF_EVEN));
        System.out.println("HALF_EVEN -2.35 => "
                + roundOrReject("-2.35", 1, RoundingMode.HALF_EVEN));

        System.out.println("HALF_DOWN 2.5001 => "
                + roundOrReject("2.5001", 0, RoundingMode.HALF_DOWN));
        System.out.println("HALF_UP 2.4999 => "
                + roundOrReject("2.4999", 0, RoundingMode.HALF_UP));

        System.out.println("UNNECESSARY 2.00 => "
                + roundOrReject("2.00", 0, RoundingMode.UNNECESSARY));
        System.out.println("UNNECESSARY 2.50 => "
                + roundOrReject("2.50", 0, RoundingMode.UNNECESSARY));

        System.out.println("String 2.675 => "
                + new BigDecimal("2.675")
                        .setScale(2, RoundingMode.HALF_EVEN));

        System.out.println("Double 2.675 => "
                + new BigDecimal(2.675)
                        .setScale(2, RoundingMode.HALF_EVEN));

        System.out.println("precision2 9.95 => "
                + new BigDecimal("9.95")
                        .round(new MathContext(
                                2, RoundingMode.HALF_UP))
                        .toPlainString());
    }
}
```

Biên dịch và thực thi:

```bash
javac RoundingMidpointDemo.java
java RoundingMidpointDemo
```

Stdout xác định trên Java 17+:

```text
Mode: +2.5, -2.5, +3.5, -3.5 (scale=0)
UP: 3, -3, 4, -4
DOWN: 2, -2, 3, -3
CEILING: 3, -2, 4, -3
FLOOR: 2, -3, 3, -4
HALF_UP: 3, -3, 4, -4
HALF_DOWN: 2, -2, 3, -3
HALF_EVEN: 2, -2, 4, -4
UNNECESSARY: ERROR, ERROR, ERROR, ERROR
HALF_EVEN 2.25 => 2.2
HALF_EVEN 2.35 => 2.4
HALF_EVEN -2.25 => -2.2
HALF_EVEN -2.35 => -2.4
HALF_DOWN 2.5001 => 3
HALF_UP 2.4999 => 2
UNNECESSARY 2.00 => 2
UNNECESSARY 2.50 => ERROR
String 2.675 => 2.68
Double 2.675 => 2.67
precision2 9.95 => 10
```

Phần `roundOrReject(...)` chỉ in `ERROR` cho **mục đích học và đối chiếu**. Trong ứng dụng sản xuất, nếu `UNNECESSARY` báo lỗi, bạn cần xử lý đúng loại ngoại lệ hoặc trả thông báo validation rõ ràng; không âm thầm thay kết quả bằng 0, không nuốt ngoại lệ rồi tiếp tục tính tiền.

Bộ vòng lặp qua `RoundingMode.values()` giúp **không vô ý bỏ sót chế độ thứ tám**. Việc in cả hai dấu và hai parity (`2.5`, `3.5`) giúp phát hiện nhiều loại lỗi trong implementation custom hơn là một ví dụ duy nhất.

## 7. Vì sao dùng `new BigDecimal(2.675)` có thể khiến test midpoint sai?

Nhiều số thập phân không biểu diễn chính xác bằng nhị phân hữu hạn. Literal Java `2.675` có kiểu `double` và được lưu dưới dạng một giá trị nhị phân rất gần `2.675` nhưng **không bằng chính xác** số thập phân ấy.

Vì vậy:

```java
BigDecimal decimalExact = new BigDecimal("2.675");
BigDecimal fromDouble  = new BigDecimal(2.675);

System.out.println(
        decimalExact.setScale(2, RoundingMode.HALF_EVEN)); // 2.68
System.out.println(
        fromDouble.setScale(2, RoundingMode.HALF_EVEN));   // 2.67
```

`decimalExact` nằm đúng giữa `2.67` và `2.68`; chữ số cuối `8` chẵn nên `HALF_EVEN` ra `2.68`. Còn `fromDouble` thể hiện **giá trị nhị phân chính xác đã được lưu từ literal**, hơi nhỏ hơn `2.675`, nên gần `2.67` hơn và ra `2.67`. Kết quả này không chứng minh `HALF_EVEN` hoạt động không nhất quán; **đầu vào đã khác nhau**.

Nếu bắt buộc bắt đầu từ một `double`, `BigDecimal.valueOf(2.675)` sử dụng biểu diễn thập phân từ `Double.toString` và thường hữu ích hơn `new BigDecimal(double)` cho chuyển đổi thông thường. Tuy nhiên, `valueOf` cũng không thể phục hồi giá trị thập phân gốc nếu bạn đã tính toán xấp xỉ bằng `double` trước đó. Với các testcase tie thập phân, ưu tiên tạo từ **chuỗi** hoặc `BigDecimal.valueOf(long, scale)` khi có giá trị nguyên đã định.

### Cách dựng dữ liệu kiểm thử chuẩn

```java
// Hai cách chính xác cho 2.675:
BigDecimal a = new BigDecimal("2.675");
BigDecimal b = BigDecimal.valueOf(2675L, 3);

System.out.println(a.compareTo(b) == 0); // true
```

Với các ca đúng giữa cần độ chính xác decimal, không dùng phép toán `double` trung gian để tạo expected value. Đừng dùng `Math.round(double)` như một oracle tổng quát để kiểm tra tám `RoundingMode`: nó có quy tắc riêng và chịu biểu diễn dấu phẩy động.

## 8. Phân biệt `setScale` với `MathContext`: hai loại bài toán làm tròn

### 8.1. `setScale`: số chữ số sau dấu phẩy

`setScale(targetScale, mode)` làm tròn theo **scale đích**:

```java
BigDecimal value = new BigDecimal("123.45");

BigDecimal fixed = value.setScale(1, RoundingMode.HALF_UP);
// fixed = 123.5
```

Khi dùng scale 0, giá trị kết quả là số nguyên; khi dùng scale 2, kết quả có hai vị trí thập phân (có thể thêm các số 0 cuối). Giá trị BigDecimal có thể có **scale âm**, nên đây không phải chỉ là tùy chọn định dạng; nó quyết định đơn vị lượng tử hóa thập phân.

### 8.2. `MathContext`: tổng số chữ số có nghĩa

`new MathContext(precision, mode)` dùng cho **precision** (số chữ số có nghĩa), không phải số chữ số sau dấu phẩy:

```java
BigDecimal input = new BigDecimal("9.95");
BigDecimal rounded = input.round(
        new MathContext(2, RoundingMode.HALF_UP));

// Kết quả giá trị số là 10, có 2 chữ số có nghĩa.
System.out.println(rounded.toPlainString()); // 10
```

Điểm đúng giữa trong bài toán precision còn phụ thuộc độ lớn đầu vào và vị trí chữ số bị bỏ. Nếu bạn cần tiền có đúng hai chữ số sau dấu phẩy, không nên thay `setScale(2, mode)` bằng `round(new MathContext(2, mode))`.

### 8.3. Làm tròn ở bước trung gian khác làm tròn một lần ở cuối

```java
BigDecimal a = new BigDecimal("0.005");
BigDecimal b = new BigDecimal("0.005");
BigDecimal c = new BigDecimal("0.005");

BigDecimal roundTotal = a.add(b).add(c)
        .setScale(2, RoundingMode.HALF_UP); // 0.02

BigDecimal roundEach = a.setScale(2, RoundingMode.HALF_UP)
        .add(b.setScale(2, RoundingMode.HALF_UP))
        .add(c.setScale(2, RoundingMode.HALF_UP)); // 0.03
```

Cùng mode nhưng **khác ranh giới làm tròn** nên tổng khác. Đây là quyết định nghiệp vụ (làm tròn từng dòng hay toàn hóa đơn), không phải lựa chọn tùy hứng để giảm chi phí tính toán.

## 9. Ma trận kiểm thử khuyến nghị cho mọi `RoundingMode`

Một testcase ở `2.5` là chưa đủ. Nên thiết kế **ma trận theo trục**, đặc biệt nếu đang kiểm chứng hàm rounding tự viết hoặc tích hợp với DB/API.

| Nhóm test | Giá trị mẫu | Điều cần xác minh |
| --- | --- | --- |
| Tie nguyên dương | `2.5`, `3.5` tới scale 0 | Chọn parity ở `HALF_EVEN`, away/toward |
| Tie nguyên âm | `-2.5`, `-3.5` tới scale 0 | Phân biệt `UP` và `CEILING`; `DOWN` và `FLOOR` |
| Tie scale 1 | `2.25`, `2.35`, `-2.25`, `-2.35` | Chữ số giữ lại chẵn/lẻ |
| Ngay dưới tie | `2.4999`, `-2.4999` | `HALF_*` chọn ứng viên gần nhất |
| Ngay trên tie | `2.5001`, `-2.5001` | Không coi sai là tie |
| Không cần rounding | `2.00` tới scale 0 | Mọi mode cho `2`; `UNNECESSARY` thành công |
| Buộc rounding | `2.50` tới scale 0 | `UNNECESSARY` phải ném ngoại lệ |
| Giá trị 0 | `0`, `0.000`, `-0.000` | Không tạo kết quả sai khác; chú ý BigDecimal không bảo toàn dấu âm của 0 |
| Giá trị lớn/nhỏ | `999.95`, `0.00005` theo scale thích hợp | Carry sang hàng chữ số mới, miền scale |
| Precision hữu hạn | `9.95` với precision 2 | Phân biệt `MathContext` với scale |
| Dữ liệu từ `double` | `2.675` literal và `"2.675"` | Không nhầm sai số đầu vào với bug rounding |
| Phân số từ phép chia | `1/3`, `1/8` | Cần mode/context phù hợp; `UNNECESSARY` nếu không chính xác |
| Dữ liệu số âm | `-0.1`, `-9.9` | Hướng +∞, −∞ và 0 đúng |

### 9.1. Kiểm tra trường hợp không cần làm tròn

Nếu số đã nằm chính xác trên “lưới” của scale đích, mọi chế độ phải giữ nguyên **giá trị số**:

```java
BigDecimal input = new BigDecimal("5.000");

for (RoundingMode mode : RoundingMode.values()) {
    BigDecimal result = input.setScale(0, mode);
    if (result.compareTo(new BigDecimal("5")) != 0) {
        throw new AssertionError("Sai khi không cần làm tròn: " + mode);
    }
}
```

Với `BigDecimal`, `equals` còn kiểm tra scale nên `new BigDecimal("5.000")` không `equals(new BigDecimal("5"))`, dù về số học bằng nhau. Hãy chọn phép so sánh theo hợp đồng testcase.

### 9.2. Một số bất biến hữu ích cho property-based testing

Nếu `x` là giá trị hữu hạn kiểu `BigDecimal`, `r = x.setScale(scale, mode)` thành công và không có vấn đề về miền/tài nguyên:

- **Cố định scale:** `r.scale() == scale`.
- **Idempotence ở cùng scale:** `r.setScale(scale, mode).equals(r)`; sau lần đầu, không còn phần chữ số cần bỏ.
- **Không dùng `UP`/`DOWN` sai hướng:** `UP` không cho độ lớn tuyệt đối thấp hơn giá trị gốc khi cần bỏ chữ số; `DOWN` không cho độ lớn tuyệt đối cao hơn giá trị gốc.
- **Không dùng `CEILING`/`FLOOR` sai thứ tự:** `CEILING` không trả số nhỏ hơn `x`; `FLOOR` không trả số lớn hơn `x`.
- **Nếu không cần rounding:** mọi mode, kể cả `UNNECESSARY`, cho cùng giá trị số.
- **Trong nhóm `HALF_*`:** nếu đầu vào không đúng giữa, ba mode phải chọn cùng ứng viên gần nhất (khi cần làm tròn).
- **Nếu phải bỏ chữ số có giá trị:** `UNNECESSARY` phải ném `ArithmeticException`.

Các bất biến không thay thế **expected value** cụ thể cho tất cả trường hợp tie. Phải kết hợp oracle (bảng kết quả ở mục 4/5), kiểm tra ngoại lệ và test giá trị dương/âm.

### 9.3. Kiểm tra ngoại lệ chính xác

Không nên viết test:

```java
// Không tốt: vô tình nuốt mọi lỗi và coi là thành công.
try {
    computation();
} catch (Exception ignored) {
}
```

Nếu quy tắc yêu cầu `UNNECESSARY` ném `ArithmeticException`, hãy kiểm tra **đúng kiểu ngoại lệ**. Trong JUnit 5, có thể dùng `assertThrows(ArithmeticException.class, ...)`; nếu chỉ dùng Java core, dùng một cờ và `try/catch(ArithmeticException)` rõ ràng, rồi ném `AssertionError` khi ngoại lệ không xuất hiện.

Test “không ném ngoại lệ” với `2.00` cũng quan trọng như test “phải ném” với `2.50`.

## 10. Ví dụ kiểm thử không dùng thư viện ngoài

Bạn có thể đưa phương thức này vào một lớp thử nghiệm Java, dùng trực tiếp API chuẩn:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

static void expectRounded(
        String input, int scale,
        RoundingMode mode, String expected) {

    BigDecimal actual = new BigDecimal(input).setScale(scale, mode);

    // So cả toPlainString để kiểm tra scale/trình bày của ví dụ.
    if (!actual.toPlainString().equals(expected)) {
        throw new AssertionError(
                input + " với " + mode + " => "
                + actual.toPlainString() + ", kỳ vọng " + expected);
    }
}

static void expectRoundingRejected(
        String input, int scale) {

    try {
        new BigDecimal(input)
                .setScale(scale, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException expected) {
        return;
    }
    throw new AssertionError(
            "Phải từ chối rounding cho " + input);
}
```

Đây là **hai phương thức nhúng**, không phải file Java độc lập. Ví dụ lời gọi trong một hàm test:

```java
expectRounded("2.5", 0, RoundingMode.HALF_UP, "3");
expectRounded("-2.5", 0, RoundingMode.HALF_UP, "-3");
expectRounded("3.5", 0, RoundingMode.HALF_EVEN, "4");
expectRounded("2.25", 1, RoundingMode.HALF_EVEN, "2.2");
expectRounded("2.35", 1, RoundingMode.HALF_EVEN, "2.4");
expectRounded("-2.5", 0, RoundingMode.CEILING, "-2");
expectRounded("-2.5", 0, RoundingMode.FLOOR, "-3");
expectRounded("2.00", 0, RoundingMode.UNNECESSARY, "2");
expectRoundingRejected("2.50", 0);
```

Khuyến nghị tiếp theo là triển khai **data-driven test** để kiểm tra ma trận tám mode cho nhiều đầu vào, thay vì viết từng điều kiện `if` cho mỗi mode. Đừng tính expected bằng cùng hàm production đang test rồi so với chính nó; như vậy không phát hiện lỗi thuật toán.

## 11. Ứng dụng thực tế: tiền, thuế, kiểm thử liên hệ thống

Các API như `BigDecimal.divide(scale, mode)`, `setScale`, `round(MathContext)` đều có thể làm tròn nhưng **thời điểm** và **mục đích** khác nhau.

Với dữ liệu tiền tệ, một chính sách có thể nói: “Lưu số tiền theo hai chữ số thập phân, từ chối đầu vào không biểu diễn chính xác ở scale 2.” Đây là lý do hợp lệ để dùng `setScale(2, UNNECESSARY)` ở ranh giới lưu. Chính sách khác cho phép tính thuế ở precision cao rồi làm tròn `HALF_UP` khi kết toán; đó lại là một lựa chọn khác. Không có mode mặc định “đúng cho mọi tiền tệ”.

Với tích hợp CSDL `DECIMAL(p,s)`, serializer JSON, phần mềm kế toán hoặc hệ thống khác, kiểm thử không chỉ `BigDecimal` trong JVM mà cả quá trình:

1. Đọc chuỗi đầu vào thành số thập phân chính xác.
2. Áp dụng quy tắc rounding, scale hoặc precision tại ranh giới xác định.
3. Ghi qua JDBC/ORM hoặc xuất JSON theo định dạng đã công bố.
4. Đọc lại và so sánh cả **giá trị số** lẫn **scale/trình bày**, nếu hợp đồng yêu cầu.
5. Kiểm thử những tie dương/âm và chính sách xử lý overflow/exception ở cả hai phía.

Một database/driver có thể có quy tắc làm tròn riêng. Đừng để việc ép kiểu ở DB âm thầm thay thế quyết định trong tầng nghiệp vụ; làm rõ trách nhiệm từng hệ thống và dùng integration test cho các giá trị đúng giữa.

Nếu giao thức trao đổi bắt buộc giữ nguyên “`2.50`” thay vì “`2.5`”, hãy phân biệt giá trị số với biểu diễn chuỗi và scale. `BigDecimal.toPlainString()` giữ scale hiện có, còn `stripTrailingZeros()` có thể làm mất những số 0 cuối có ý nghĩa đối với định dạng/hợp đồng.

## 12. Những nhầm lẫn phổ biến

| Nhầm lẫn | Hậu quả | Quy tắc sửa |
| --- | --- | --- |
| “`UP` nghĩa là trục số tăng” | Làm tròn số âm sai | `UP` là **ra xa 0** |
| “`DOWN` luôn giống `FLOOR`” | Sai cho số âm | `DOWN` về 0; `FLOOR` về −∞ |
| “`CEILING` luôn tăng độ lớn” | Sai với số âm | `CEILING` về +∞ |
| “`HALF_UP` là làm tròn lên mọi phần dư” | Sai khi dưới tie | Chọn gần nhất; tie mới ra xa 0 |
| “`HALF_DOWN` luôn làm tròn xuống” | Sai khi trên tie | Chọn gần nhất; tie mới về 0 |
| “`HALF_EVEN` luôn xuống” | `3.5` thành `3` sai | Tie chọn chữ số **giữ lại** chẵn |
| “HALF_EVEN xét chữ số ngay trước dấu phẩy” | Sai khi scale > 0 hoặc âm | Xét chữ số cuối của **kết quả đích** |
| “`UNNECESSARY` là mode không làm gì” | Nuốt lỗi đáng phải ném | Chỉ hợp lệ khi không phải rounding |
| Chỉ test `+2.5`, bỏ `-2.5` | Không thấy lỗi hướng với số âm | Test cả hai dấu |
| Chỉ test `2.5`, bỏ `3.5` | Không thấy lỗi chẵn/lẻ | Test cả parity |
| Test với `new BigDecimal(2.675)` | Dữ liệu không phải tie thập phân | Tạo bằng chuỗi, dùng giá trị exact |
| Nhầm `MathContext(2)` với `setScale(2)` | Khác phạm vi precision | Dùng đúng API theo hợp đồng |
| Làm tròn mỗi dòng thay vì cuối tổng | Có thể đổi tổng tiền | Giữ ranh giới rounding được quy định |
| Dùng integer `BigDecimal.ROUND_*` cũ | API khó đọc, bị deprecated | Dùng enum `RoundingMode` |
| So `equals` khi chỉ cần bằng nhau về số | Sai với `2.0` và `2.00` | Dùng `compareTo` nếu scale không quan trọng |

## 13. Bài tập và hướng giải

**Bài 1.** Điền kết quả `setScale(0, ...)` cho `+7.5` và `-7.5` với đủ tám mode. Sau đó giải thích vì sao `HALF_EVEN` chọn `8` và `-8`.

*Gợi ý:* các ứng viên là `7`, `8`, `-7`, `-8`; tại tie, kết quả có chữ số cuối chẵn là ±8. `UNNECESSARY` báo lỗi.

**Bài 2.** Với đầu vào `-2.4999`, `-2.5000`, `-2.5001`, so kết quả `HALF_UP`, `HALF_DOWN`, `HALF_EVEN`, `UP`, `DOWN`, `CEILING`, `FLOOR`. Hai giá trị ngoài điểm giữa có giống nhau giữa các `HALF_*` không?

*Gợi ý:* `HALF_*` cùng ra `-2` ở `-2.4999`, khác nhau ở `-2.5000`, cùng ra `-3` ở `-2.5001`. Các mode hướng (UP/DOWN/CEILING/FLOOR) không phụ thuộc điều kiện tie.

**Bài 3.** Hệ thống chỉ lưu tối đa hai chữ số thập phân chính xác, không được tự làm tròn. `1.2300`, `1.234` và `1.200` sẽ được xử lý ra sao với `setScale(2, UNNECESSARY)`?

*Gợi ý:* `1.2300` → `1.23`; `1.234` bị từ chối; `1.200` → `1.20`. Các số 0 cuối có thể loại bỏ chính xác, nhưng chữ số khác 0 phải giữ nguyên.

**Bài 4.** Tại sao `new BigDecimal("2.675")` và `new BigDecimal(2.675)` có thể cho kết quả `HALF_EVEN` khác nhau tại scale 2? Cách tạo đầu vào nào nên được dùng cho bài kiểm thử midpoint thập phân?

*Gợi ý:* thứ nhất là decimal exact; thứ hai chứa giá trị nhị phân thực của `double`, không phải đúng `2.675`. Tạo từ chuỗi hoặc nguyên với scale.

**Bài 5.** Một kết quả `9.95` được làm tròn bằng `new MathContext(2, HALF_UP)`. Nó có nghĩa giữ hai chữ số sau dấu phẩy hay hai chữ số có nghĩa? Vì sao kết quả bằng `10`?

*Gợi ý:* precision 2 là **hai chữ số có nghĩa**, không phải scale 2. Phần cần làm tròn đẩy chữ số cuối lên và tạo carry.

**Bài 6.** Thiết kế bảng test cho các chế độ `RoundingMode` trên hai chiều **dấu** và **parity**, thêm ít nhất ba ca biên không đúng giữa. Làm thế nào để test `UNNECESSARY` cả đường thành công lẫn thất bại?

*Gợi ý:* dùng ma trận mục 4, 5 và các số `2.4999`, `2.5001`, `-2.4999`; test đúng kiểu `ArithmeticException`.

## 14. Kết luận thực hành

Để kiểm thử rounding đúng, **luôn bắt đầu bằng hợp đồng về scale/precision và thời điểm làm tròn**. Xây dựng một ma trận với **cả số dương lẫn số âm**, **chữ số cuối chẵn và lẻ**, **đúng giữa, dưới giữa, trên giữa**, cùng trường hợp **không cần làm tròn** và trường hợp `UNNECESSARY` phải từ chối.

Dùng `BigDecimal` từ chuỗi để tạo midpoint thập phân chính xác; không dùng `double` rồi coi kết quả xấp xỉ là một tie thực sự. `UP`, `DOWN`, `CEILING` và `FLOOR` là quy tắc **hướng**; `HALF_UP`, `HALF_DOWN` và `HALF_EVEN` là quy tắc **chọn gần nhất + xử lý tie**; `UNNECESSARY` là quy tắc **không được làm tròn**.

Tài liệu chuẩn Java SE 17:
- [`RoundingMode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): mô tả từng chế độ, sơ đồ trường hợp dương và âm.
- [`BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `setScale`, `divide`, `round`, precision/scale và xử lý `ArithmeticException`.
- [`MathContext`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): precision và rounding mode cho phép tính.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-008">Kiểm thử round-trip parse, format và cơ số</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử round-trip parse, format và cơ số trong Java

## 1. Round-trip là gì và vì sao phải kiểm thử?

Dữ liệu số thường đi qua hai hướng chuyển đổi:

```text
Giá trị số -> format/toString -> chuỗi văn bản
Chuỗi văn bản -> parse         -> giá trị số
```

**Round-trip** là phép chuyển đổi đi rồi quay về. Với một giá trị `x` và cơ chế định dạng/phân tích tương ứng, ta muốn kiểm tra:

```text
parse(format(x)) == x
```

Nhưng ký hiệu `==` ở đây phải được xác định rõ: **bằng giá trị số học**, **bằng tất cả các thuộc tính của biểu diễn** (ví dụ scale của `BigDecimal`), hay **bằng chính xác các bit** (ví dụ dấu âm của `-0.0` trong `double`)?

Một ví dụ quan trọng:

```java
BigDecimal a = new BigDecimal("1E+3");   // giá trị 1000, scale -3
BigDecimal b = new BigDecimal(a.toPlainString()); // "1000", scale 0

System.out.println(a.compareTo(b) == 0); // true: giá trị số học như nhau
System.out.println(a.equals(b));         // false: scale khác nhau
```

Như vậy, chuỗi `"1000"` đủ để phục hồi **giá trị số học** nhưng không đủ để phục hồi **scale** ban đầu của `1E+3`. Nếu hệ thống lưu trữ định dạng/độ chi tiết theo scale, bài test chỉ dùng `compareTo()` sẽ bỏ sót lỗi.

Kiểm thử round-trip đặc biệt quan trọng khi số đi qua cấu hình, file CSV/JSON, dữ liệu giao tiếp với hệ thống khác, thông điệp, format hiển thị và các cơ số 2/8/10/16/36.

## 2. Ba mức độ cần kiểm thử

| Mức round-trip | Tiêu chí | Ví dụ |
| --- | --- | --- |
| **Giữ giá trị số học** | Parse lại cùng giá trị số | `new BigDecimal("1E+3").compareTo(new BigDecimal("1000")) == 0` |
| **Giữ thuộc tính biểu diễn** | Ngoài giá trị, giữ scale, dấu 0, v.v. theo hợp đồng | `BigDecimal.equals()` kiểm tra cả giá trị và scale |
| **Giữ bit** | Mọi bit của kiểu số sau parse trùng với ban đầu | `Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b)` |

Không phải bài toán nào cũng cần cùng mức. Giá trị hiển thị cho con người có thể chấp nhận làm tròn. Mã lưu trữ để tái dựng chính xác một đối tượng có thể cần bảo toàn bit hoặc các thuộc tính biểu diễn.

Cũng phải phân biệt **chuỗi đầu vào gốc** với **chuỗi chuẩn hóa**. Chẳng hạn `Integer.parseInt("+00042")` cho 42, nhưng `Integer.toString(42)` là `"42"`. Round-trip giá trị thành công, round-trip văn bản nguyên trạng thất bại. Nếu cần bảo toàn dấu `+` hoặc số 0 đứng đầu, lưu thêm chuỗi gốc.

## 3. Round-trip số nguyên có dấu với các cơ số

### 3.1. Integer.toString và Integer.parseInt

Với `int value` và cơ số `radix` hợp lệ trong khoảng **2 đến 36**:

```java
int value = -255;
String text = Integer.toString(value, 16); // "-ff"
int parsed = Integer.parseInt(text, 16);   // -255
```

Hợp đồng nên kiểm thử:

```java
Integer.parseInt(Integer.toString(value, radix), radix) == value
```

Cho mọi giá trị `int`, kể cả `Integer.MIN_VALUE` và `Integer.MAX_VALUE`, khi cả format và parse sử dụng cùng cơ số 2..36. Những chữ cái đại diện cho chữ số lớn hơn 9 thường theo quy ước `a..z` hoặc `A..Z` trong lúc parse, còn `toString` tạo chữ thường.

`Long.toString(longValue, radix)` và `Long.parseLong(text, radix)` có cơ chế tương tự cho số nguyên 64 bit.

Bảng minh họa một giá trị:

| Radix | `Integer.toString(255, radix)` | `Integer.parseInt(text, radix)` |
| ---: | --- | ---: |
| 2 | `11111111` | 255 |
| 8 | `377` | 255 |
| 10 | `255` | 255 |
| 16 | `ff` | 255 |
| 36 | `73` | 255 |

`radix` là tham số khi parse; ký tự `"ff"` không thể giải mã trong cơ số 10. `Integer.parseInt("ff", 16)` cho 255 nhưng `Integer.parseInt("ff", 10)` ném `NumberFormatException`.

### 3.2. Dấu âm và dạng bit bù hai

Không nhầm:

```java
Integer.toString(-1, 16); // "-1"
Integer.toHexString(-1);   // "ffffffff"
```

`Integer.toString(value, radix)` biểu diễn **số có dấu** với dấu `-`. Ngược lại, `toHexString`, `toBinaryString`, `toOctalString` của `Integer` sử dụng dạng **không dấu của mẫu bit bù hai**. Do đó `Integer.toHexString(-1)` tạo chuỗi 8 chữ `f`.

Nếu muốn khôi phục mẫu bit từ `"ffffffff"`, dùng:

```java
int bits = Integer.parseUnsignedInt("ffffffff", 16);
// bits == -1 về cách diễn giải signed int;
// Integer.toUnsignedLong(bits) == 4294967295L
```

Không dùng `Integer.parseInt("ffffffff", 16)` vì giá trị 4.294.967.295 vượt phạm vi `int` có dấu.

### 3.3. Parse unsigned

Dùng `Integer.toUnsignedString(value)` và `Integer.parseUnsignedInt(text)` nếu dữ liệu văn bản biểu diễn số nguyên 32 bit **không dấu**:

```java
int rawBits = -1;
String unsignedText = Integer.toUnsignedString(rawBits); // "4294967295"
int restoredBits = Integer.parseUnsignedInt(unsignedText); // -1
boolean sameBits = rawBits == restoredBits; // true
```

Giá trị `-1` trong `int` cuối cùng là **mẫu bit ban đầu được phục hồi**, không phải phép parse thất bại. Tương tự, Java có `Long.toUnsignedString` và `Long.parseUnsignedLong`.

### 3.4. Tiền tố cơ số không tự được parseInt nhận

`Integer.parseInt("0xff", 16)` **không** chấp nhận tiền tố `0x` và ném `NumberFormatException`. Bản thân `parseInt` chỉ parse chuỗi chữ số hợp lệ (có thể có dấu `+`/`-` ở đầu) theo cơ số đã cho.

`Integer.decode("0xff")` có thể nhận các ký hiệu tiền tố như `0x`, `0X`, `#`; nó còn có cách hiểu số có số 0 đứng đầu theo quy ước octal. **Không đánh tráo `decode` và `parseInt` trong một giao thức dữ liệu** nếu chưa định nghĩa chuẩn input, bởi cùng `"010"` có thể mang nghĩa khác theo API.

Cơ số vượt giới hạn 2..36 và chữ số không hợp lệ đều là ca cần test. Với API `Integer.parseInt`, đầu vào quá dài/tràn phạm vi hoặc chứa dấu cách không được chấp nhận như thể đã được `trim()` tự động.

## 4. Số nguyên lớn với BigInteger

`BigInteger` không bị giới hạn bởi miền `int`/`long` có dấu. Khi dữ liệu có thể vượt `Long.MAX_VALUE`, dùng:

```java
BigInteger original = new BigInteger("123456789012345678901234567890");
String base36 = original.toString(36);
BigInteger parsed = new BigInteger(base36, 36);
boolean same = original.equals(parsed); // true
```

Các ký tự chữ số ở cơ số 36 có thể từ `0` tới `9` và `a` tới `z`. Test trên số dương, số âm, 0, và những giá trị lớn hơn phạm vi 64 bit. Khi parse, đảm bảo đối số radix đúng với format và xử lý `NumberFormatException` đối với đầu vào lỗi.

**Chú ý phạm vi:** `BigInteger.toString(radix)` và constructor `new BigInteger(text, radix)` thiết kế cho **giá trị nguyên**. Không suy từ đó rằng `BigDecimal` có thể parse trực tiếp phần thập phân trong mọi cơ số. Với phần lẻ cơ số 2/16 hay những biểu diễn số thực khác, cần một định dạng hoặc thuật toán được định nghĩa riêng; không tùy ý dùng `BigDecimal(String)` cho văn bản không phải cú pháp thập phân.

## 5. BigDecimal: bảo toàn giá trị khác bảo toàn scale

### 5.1. toString để khôi phục biểu diễn BigDecimal

`BigDecimal.toString()` có dạng biểu diễn chuẩn, có thể dùng ký pháp khoa học khi cần. Khi tạo lại bằng `new BigDecimal(text)`, nó bảo toàn **giá trị số học và scale**:

```java
BigDecimal x = new BigDecimal("123.4500");
String encoded = x.toString();        // "123.4500"
BigDecimal back = new BigDecimal(encoded);

System.out.println(x.equals(back));   // true
System.out.println(back.scale());     // 4
```

Đây là tiêu chí nghiêm ngặt hơn `compareTo() == 0`. `equals()` của `BigDecimal` phân biệt `1.0` và `1.00`, còn `compareTo()` xem chúng bằng nhau về số học.

Nên kiểm thử thêm:
- số 0 với các scale khác nhau (`"0"`, `"0.000"`);
- số âm và số rất lớn/nhỏ;
- `"1E+3"` với **scale âm**;
- số 0 tận cùng (`"123.4500"`);
- giá trị chính xác có nhiều hơn 15–17 chữ số, để chắc rằng dữ liệu không vô tình đi qua `double`.

### 5.2. toPlainString ưu tiên đọc, có thể không giữ scale âm

`BigDecimal.toPlainString()` bỏ ký pháp mũ, phù hợp trường hợp cần biểu diễn dạng thường. Nhưng với số có scale âm, round-trip qua `toPlainString()` rồi `new BigDecimal(...)` có thể đổi scale:

```java
BigDecimal original = new BigDecimal("1E+3"); // scale -3
String plain = original.toPlainString();       // "1000"
BigDecimal reparsed = new BigDecimal(plain);  // scale 0

boolean equalValue = original.compareTo(reparsed) == 0; // true
boolean equalRepresentation = original.equals(reparsed); // false
```

Nếu cần khôi phục scale một cách nghiêm ngặt, ưu tiên `toString()` (hoặc lưu riêng `unscaledValue` và `scale` theo schema rõ ràng). Nếu chỉ cần giá trị số học, `toPlainString()` có thể phù hợp.

### 5.3. setScale, stripTrailingZeros và format làm tròn

`setScale` có thể làm tròn theo `RoundingMode`; `stripTrailingZeros()` giữ nguyên giá trị số học nhưng có thể đổi scale. Vì vậy test phải nêu rõ:
- có chấp nhận thay đổi giá trị không?
- có chấp nhận thay đổi scale không?
- số chữ số lẻ do **dữ liệu nguồn** hay **quy tắc hiển thị** quyết định?

Ví dụ, định dạng `12.3456` thành `"12.35"` đã làm mất thông tin. Parse về `BigDecimal` sẽ ra 12.35, không thể tự suy ra 12.3456.

Không dùng `new BigDecimal(doubleValue)` như cách khôi phục một chuỗi thập phân nếu không hiểu sai số nhị phân của `double`. Với dữ liệu thập phân ở dạng chuỗi, thường dùng trực tiếp `new BigDecimal(text)`.

## 6. Round-trip double và float

### 6.1. Double.toString/Double.parseDouble

Với giá trị `double` hữu hạn, `Double.toString(value)` tạo dạng chuỗi thập phân đủ thông tin để `Double.parseDouble(text)` khôi phục **cùng giá trị double** (bao gồm mẫu bit của `-0.0`). Ví dụ:

```java
double value = Math.PI;
String text = Double.toString(value);
double back = Double.parseDouble(text);

boolean identicalBits =
        Double.doubleToRawLongBits(value)
        == Double.doubleToRawLongBits(back); // true
```

Các điểm biên phải test gồm `0.0`, `-0.0`, số rất nhỏ (*subnormal*), `Double.MIN_VALUE`, `Double.MAX_VALUE` và giá trị gần biên biểu diễn. Với `float` có `Float.toString()` và `Float.parseFloat()` cùng ý tưởng.

**Đừng dùng `a == b` làm phép kiểm round-trip bit cho mọi tình huống:**
- `0.0 == -0.0` là `true`, dù bit dấu khác nhau.
- `Double.NaN == Double.NaN` là `false` dù cả hai đều NaN.
- `Double.doubleToLongBits` chuẩn hóa các bit NaN thành dạng canonical; `Double.doubleToRawLongBits` giữ raw payload bit của giá trị đang kiểm tra.

`Double.toString(Double.NaN)` sinh `"NaN"` và `Double.parseDouble("NaN")` phục hồi trạng thái NaN; **không bảo đảm giữ từng bit payload** của mọi NaN có thể biểu diễn. Nếu bắt buộc round-trip chính xác **mọi mẫu bit**, gồm NaN payload, serialize kết quả từ `Double.doubleToRawLongBits(x)` theo một giao thức bit cố định rồi khôi phục bằng `Double.longBitsToDouble(bits)`.

Tương tự, `Infinity` và `-Infinity` được API `Double.toString/parseDouble` hiểu, nhưng không phải mọi định dạng dữ liệu trao đổi (chẳng hạn JSON chuẩn nghiêm ngặt) đều cho phép hai giá trị không hữu hạn này. Phải phân biệt hợp đồng của **Java parser** và hợp đồng của **định dạng lưu trữ**.

### 6.2. Format với ít chữ số có thể phá round-trip

`String.format(Locale.US, "%.2f", Math.PI)` trả chuỗi `"3.14"`. Khi `Double.parseDouble`, giá trị sau parse là double gần 3.14, khác bit với `Math.PI`.

```java
String shown = String.format(Locale.US, "%.2f", Math.PI);
// shown = "3.14"; đây là format hiển thị đã làm tròn
double reconstructed = Double.parseDouble(shown);
// reconstructed không có cùng bit với Math.PI
```

Vì vậy:
- dùng `Double.toString()` cho hành trình văn bản cần giữ cùng giá trị double;
- dùng `String.format`/`DecimalFormat` với precision cụ thể cho **hiển thị**, hiểu rằng kết quả thường mất thông tin;
- không tự suy rằng 2 chữ số thập phân đủ cho bất kỳ `double` nào.

Các trường hợp như `0.1 + 0.2` còn chịu đặc tính nhị phân của `double`. Test round-trip có thể thành công 100% nhưng điều đó **không chứng minh phép tính đầu vào chính xác theo thập phân**.

## 7. Locale, dấu phân cách và DecimalFormat

Cùng số 12345.67 có thể được hiển thị:

| Bối cảnh | Ví dụ văn bản | Ý nghĩa |
| --- | --- | --- |
| Dạng máy đọc, không phụ thuộc Locale | `12345.67` | Dấu chấm là dấu thập phân |
| Đức, nhóm hàng nghìn và thập phân | `12.345,67` | Dấu chấm để nhóm, dấu phẩy là thập phân |
| Định dạng làm tròn còn hai chữ số | `12,35` từ 12.3456 (theo mẫu và chế độ làm tròn) | Đã bỏ thông tin chính xác |

Để parse chuỗi theo quy tắc địa phương, cần **cùng quy ước Locale, mẫu format và quy tắc làm tròn**. Ví dụ với `DecimalFormat`:

```java
DecimalFormat german = new DecimalFormat(
        "#,##0.00",
        DecimalFormatSymbols.getInstance(Locale.GERMANY));
german.setParseBigDecimal(true);

BigDecimal source = new BigDecimal("12345.67");
String text = german.format(source);   // "12.345,67"

ParsePosition position = new ParsePosition(0);
Number parsed = german.parse(text, position);
// parsed instanceof BigDecimal == true
// position.getIndex() == text.length()
```

Đây là **đoạn minh họa**: cần import `java.text.DecimalFormat`, `java.text.DecimalFormatSymbols`, `java.text.ParsePosition`, `java.math.BigDecimal` và `java.util.Locale`, đặt mã trong phương thức/lớp.

Chú ý:
1. `DecimalFormat` phục vụ format/parse theo mẫu. Nếu mẫu **làm tròn hoặc cắt thông tin**, parse lại không thể khôi phục giá trị gốc.
2. `setParseBigDecimal(true)` giúp parse thành `BigDecimal` thay vì tự nhận kết quả số bằng các kiểu như `Long` hoặc `Double`; điều này quan trọng khi dữ liệu thập phân yêu cầu tính chính xác.
3. `NumberFormat.parse`/`DecimalFormat.parse` có thể parse được **phần đầu** của chuỗi và dừng trước ký tự không hợp lệ. Vì thế không đủ để thấy return không null: phải kiểm tra **đã tiêu thụ toàn bộ chuỗi**, ví dụ `ParsePosition.getIndex() == text.length()`, và kiểm tra lỗi parse nếu có.
4. Không mặc định kết quả Locale này được `Double.parseDouble` hay `new BigDecimal(text)` hiểu; các API parse số chuẩn đó sử dụng cú pháp riêng, không phải parser cho mọi Locale.
5. Nếu kiểm thử một định dạng có dấu nhóm hàng nghìn, dấu tiền tệ, chữ số phi Latin hoặc dấu âm đặc thù, hãy định nghĩa schema đầu vào và toàn bộ tiêu chí chấp nhận. Một parser hỗ trợ ký tự phân nhóm không nhất thiết kiểm tra **mọi quy tắc phân nhóm chặt chẽ**; có thể cần validate pattern riêng.

**Lưu ý cho số tiền:** Một số văn bản hiển thị có số chữ số thập phân cố định, ví dụ `"12.30"`, trong khi số gốc `BigDecimal("12.3")` có scale 1. Round-trip của **giá trị số** có thể thành công nhưng `equals()` có thể thất bại nếu formatter chủ động thêm số 0 tận cùng. Hãy xác định muốn kiểm tra giá trị hay scale.

## 8. Chương trình Java 17 chạy độc lập: kiểm thử nhiều kiểu round-trip

Tạo tệp **`NumberRoundTripDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class NumberRoundTripDemo {
    static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        int[] sample = {Integer.MIN_VALUE, -255, -1, 0, 1, 255, Integer.MAX_VALUE};
        int[] bases = {2, 8, 10, 16, 36};
        int checked = 0;
        for (int base : bases) {
            for (int number : sample) {
                String text = Integer.toString(number, base);
                require(Integer.parseInt(text, base) == number, "int base=" + base);
                checked++;
            }
        }
        System.out.println("integerCases=" + checked);
        System.out.println("minus255Hex=" + Integer.toString(-255, 16));

        String unsigned = Integer.toUnsignedString(-1);
        int u = Integer.parseUnsignedInt(unsigned);
        require(u == -1, "unsigned bits");
        System.out.println("unsignedText=" + unsigned + ",signedBits=" + u);

        BigInteger big = new BigInteger("123456789012345678901234567890");
        String base36 = big.toString(36);
        require(new BigInteger(base36, 36).equals(big), "BigInteger");
        System.out.println("bigBase36=" + base36);

        BigDecimal amount = new BigDecimal("123.4500");
        BigDecimal restored = new BigDecimal(amount.toString());
        System.out.println("decimalEquals=" + amount.equals(restored)
                + ",scale=" + restored.scale());

        BigDecimal exponential = new BigDecimal("1E+3");
        BigDecimal plainBack = new BigDecimal(exponential.toPlainString());
        System.out.println("plainValueEqual=" + (exponential.compareTo(plainBack) == 0)
                + ",plainEquals=" + exponential.equals(plainBack)
                + ",scales=" + exponential.scale() + "," + plainBack.scale());

        double[] values = {0.0, -0.0, 0.1, Math.PI,
                Double.MIN_VALUE, Double.MAX_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        int doubleCount = 0;
        for (double original : values) {
            double back = Double.parseDouble(Double.toString(original));
            require(Double.doubleToRawLongBits(original)
                    == Double.doubleToRawLongBits(back), "double bits");
            doubleCount++;
        }
        System.out.println("doubleBitCases=" + doubleCount);
        String roundedPi = String.format(Locale.US, "%.2f", Math.PI);
        System.out.println("piText=" + roundedPi
                + ",bitwiseEqual=" + (Double.doubleToRawLongBits(Math.PI)
                    == Double.doubleToRawLongBits(Double.parseDouble(roundedPi))));

        DecimalFormat german = new DecimalFormat(
                "#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        german.setParseBigDecimal(true);
        BigDecimal localizedValue = new BigDecimal("12345.67");
        String localized = german.format(localizedValue);
        ParsePosition position = new ParsePosition(0);
        Number parsed = german.parse(localized, position);
        require(parsed instanceof BigDecimal, "localized parse type");
        require(position.getIndex() == localized.length(), "complete locale parse");
        require(((BigDecimal) parsed).compareTo(localizedValue) == 0,
                "localized number value");
        System.out.println("germanText=" + localized
                + ",parsed=" + parsed + ",fullyConsumed=true");

        ParsePosition trailing = new ParsePosition(0);
        german.parse(localized + "X", trailing);
        System.out.println("trailingJunkDetected="
                + (trailing.getIndex() != (localized + "X").length()));

        try {
            Integer.parseInt("0xff", 16);
            throw new AssertionError("expected parse failure");
        } catch (NumberFormatException expected) {
            System.out.println("prefixedHexRejected=true");
        }
    }
}
```

Biên dịch, chạy bằng JDK 17 trở lên:

```bash
javac NumberRoundTripDemo.java
java NumberRoundTripDemo
```

Kết quả thực tế, đã biên dịch với `javac --release 17` và thực thi:

```text
integerCases=35
minus255Hex=-ff
unsignedText=4294967295,signedBits=-1
bigBase36=byw97um9s91dlz68tsi
decimalEquals=true,scale=4
plainValueEqual=true,plainEquals=false,scales=-3,0
doubleBitCases=8
piText=3.14,bitwiseEqual=false
germanText=12.345,67,parsed=12345.67,fullyConsumed=true
trailingJunkDetected=true
prefixedHexRejected=true
```

Giải thích bằng chứng:
- **35 ca số nguyên** = 7 giá trị biên/điển hình × 5 cơ số. Mọi ca đều được parse về `int` ban đầu.
- **Unsigned**: chuỗi `4294967295` giữ bit 32 bit của `-1`, không phải trả về `long`.
- **BigInteger**: số vượt xa `long` đã được chuyển qua cơ số 36 và khôi phục nguyên vẹn.
- **BigDecimal**: `toString()` giữ scale 4, nhưng `toPlainString()` với scale âm mất đặc tính scale.
- **double**: tám trường hợp gồm `-0.0`, số cực nhỏ/cực lớn, hai vô cực đều giữ raw bits khi dùng `toString/parseDouble`. Giá trị NaN payload không nằm trong bộ tám này.
- **Format hai chữ số**: `Math.PI` biến thành văn bản `3.14`, nên parse không giữ bit cũ.
- **Locale Đức**: dấu phân tách được tạo theo Locale và parser tiêu thụ hết chuỗi hợp lệ; hậu tố `X` được phát hiện nhờ kiểm tra vị trí parse.
- **Tiền tố hex**: `parseInt("0xff", 16)` bị từ chối đúng như hợp đồng.

## 9. Thiết kế test đủ mạnh cho parse/format

### 9.1. Tổ hợp ca hợp lệ

Với số nguyên:
- 0, 1, -1;
- min/max của kiểu (`Integer.MIN_VALUE`, `Integer.MAX_VALUE`, `Long.MIN_VALUE`, `Long.MAX_VALUE`);
- giá trị thường, số âm, lũy thừa của cơ số, số ngay trước/sau mốc (ví dụ 15, 16, 17 ở radix 16);
- cơ số 2, 8, 10, 16, 36, và có thể kiểm tra mọi cơ số từ 2 tới 36;
- định dạng ký tự viết hoa/viết thường, dấu `+`/`-` và số 0 đứng đầu nếu hợp đồng parser cho phép.

Với `BigDecimal`:
- số 0 với nhiều scale;
- có số 0 tận cùng;
- số rất lớn và rất nhỏ;
- scale âm và dạng mũ;
- phép format giữ nguyên giá trị nhưng thay scale;
- số sau làm tròn có thể khác giá trị ban đầu.

Với `double` và `float`:
- +0 và -0;
- số cực trị và subnormal;
- các giá trị số bình thường và gần điểm làm tròn;
- NaN và vô cực theo **quy định riêng** của schema;
- các mẫu bit sinh ngẫu nhiên để khảo sát nhiều trường hợp, tách riêng NaN payload.

### 9.2. Ca input không hợp lệ

Các test chỉ kiểm tra “happy path” dễ bỏ sót lỗi parse. Bổ sung:
- chuỗi rỗng, toàn dấu cách, thừa dấu `+`/`-`;
- chữ số không thuộc cơ số (ví dụ `"2"` ở radix 2, `"g"` ở radix 16);
- radix nhỏ hơn 2 hoặc lớn hơn 36;
- số lớn vượt phạm vi `int` hoặc `long`;
- tiền tố `0x` đưa nhầm cho `parseInt`;
- dấu phân cách nhóm hàng nghìn với Locale không phù hợp;
- chuỗi hợp lệ nhưng có hậu tố rác;
- chuỗi có dấu âm nhưng parser unsigned không hỗ trợ theo hợp đồng.

Đối với parser chuỗi thuần như `Integer.parseInt`, kiểm tra `NumberFormatException`. Đối với `DecimalFormat.parse`/`NumberFormat.parse`, kiểm tra đồng thời **kiểu số trả về**, **lỗi parse**, **vị trí đã tiêu thụ** và **quy tắc làm tròn**, vì có tình huống parser dừng ở phần đầu mà không báo lỗi theo cách chương trình mong muốn.

### 9.3. Thuộc tính cần được kiểm thử

Một số *property* hữu ích:

```text
Signed radix:
parseInt(toString(n, r), r) == n, với mọi n và 2 <= r <= 36.

BigInteger:
new BigInteger(n.toString(r), r).equals(n).

BigDecimal representation:
new BigDecimal(x.toString()).equals(x).

BigDecimal numeric value, khi dùng format có chủ đích:
parsed.compareTo(x) == 0 (nếu format không làm tròn).

Double finite:
rawBits(parseDouble(Double.toString(x))) == rawBits(x).

Localized format:
parser đúng Locale phải parse hết chuỗi;
nếu định dạng không làm mất thông tin, so sánh kết quả theo mục tiêu.
```

Đây là các **thuộc tính tham chiếu**, không phải đoạn code Java độc lập. Với generator dữ liệu ngẫu nhiên, hãy ghi lại *seed* để tái hiện ca thất bại, có giới hạn thời gian và chụp cả giá trị đầu vào, chuỗi trung gian, radix/Locale khi assertion sai.

## 10. Kiểm thử theo cơ số và tính nhất quán giữa hệ thống

**Cơ số là một phần của định dạng.** Chuỗi `"10"` biểu diễn 2 trong radix 2, 8 trong radix 8, 10 trong radix 10, 16 trong radix 16. Nếu bên gửi và nhận không trao đổi radix, round-trip có thể parse **thành công về mặt cú pháp nhưng sai giá trị**.

Với giao thức lưu trữ, nên quy định rõ:
1. kiểu giá trị và miền (signed/unsigned, số nguyên/số thực);
2. radix, cách ghi dấu và có cho phép tiền tố hay không;
3. bảng ký tự chữ số/casing cho radix lớn hơn 10;
4. có cho phép số 0 đứng đầu, ký tự khoảng trắng, dấu phân tách hay không;
5. Locale, dấu nhóm, dấu thập phân khi cần hiển thị;
6. định nghĩa round-trip: đúng số, đúng scale, đúng raw bits hay đúng chuỗi;
7. chính sách lỗi: từ chối đầu vào không hợp lệ, không âm thầm đổi radix hoặc cắt dữ liệu.

Ví dụ gửi `"ff"` không kèm radix và phía nhận dùng `Integer.parseInt(text)` (mặc định thập phân) sẽ thất bại; nhưng gửi `"10"` mà không kèm radix nguy hiểm hơn vì parser có thể trả một **giá trị khác mà không ném lỗi**.

## 11. Những lỗi phổ biến và cách phòng tránh

1. **Kiểm thử `Integer.toString(n)` rồi parse bằng radix 16.** Format và parse không cùng radix. Luôn truyền radix nhất quán ở hai phía.
2. **Nhầm `toHexString(-1)` với `Integer.toString(-1,16)`.** Một bên biểu diễn bit không dấu `ffffffff`, một bên biểu diễn số có dấu `-1`.
3. **Giả định `Integer.parseInt("0xff",16)` hiểu tiền tố.** Không: phải xử lý tiền tố theo giao thức hoặc dùng API decode phù hợp.
4. **Chỉ dùng `BigDecimal.compareTo()` khi cần giữ scale.** Giá trị bằng nhau có thể có scale khác nhau; sử dụng `equals()` nếu cần tính đầy đủ của biểu diễn.
5. **Dùng `toPlainString()` để serialize `BigDecimal` rồi muốn giữ scale âm.** Nó có thể biến `1E+3` thành `1000` và làm mất scale -3. Dùng `toString()` hoặc lưu riêng scale.
6. **Dùng `%.2f` hay `DecimalFormat` làm phương án lưu trữ lossless.** Format hiển thị có thể làm tròn hoặc đổi scale; lưu dữ liệu gốc ở định dạng đúng mức cần bảo toàn.
7. **Đo double bằng `==` rồi bỏ qua -0.0/NaN.** So sánh bit theo yêu cầu; xử lý NaN payload riêng nếu phải giữ raw bits.
8. **Cho rằng Locale Đức hoặc Việt Nam luôn dùng dấu chấm thập phân.** Dấu thập phân/nhóm phụ thuộc Locale và định dạng. Xác định rõ Locale ở cả encode và decode.
9. **Parse cục bộ được đoạn đầu rồi coi như thành công.** Với `DecimalFormat`, xác nhận `ParsePosition.getIndex() == input.length()`.
10. **Không test overflow và input sai.** Round-trip trên các số nhỏ không chứng minh parser xử lý ranh giới kiểu hoặc radix bất hợp lệ.
11. **Nhầm độ chính xác double với khả năng round-trip.** Có thể serialize/parse chính xác một *giá trị double vốn đã xấp xỉ*, nhưng nó vẫn khác số thập phân lý tưởng.
12. **So sánh văn bản chuẩn hóa với chuỗi nhập ban đầu.** Các biểu diễn như `"0007"` và `"7"` có cùng giá trị. Nếu phải bảo toàn văn bản gốc, lưu văn bản gốc.

## 12. Bài tập tự kiểm tra

**Bài 1.** `Integer.toString(31,16)` trả gì, và parse lại bằng gì?  
**Đáp án:** chuỗi `"1f"`; `Integer.parseInt("1f",16)` trả 31.

**Bài 2.** Tại sao `Integer.parseInt(Integer.toHexString(-1),16)` thất bại?  
**Đáp án:** `toHexString(-1)` trả `"ffffffff"` biểu diễn bit không dấu, giá trị vượt phạm vi signed `int`. Dùng `Integer.parseUnsignedInt("ffffffff",16)` để phục hồi cùng mẫu bit.

**Bài 3.** `new BigDecimal("5.00").toString()` parse lại có cùng scale không?  
**Đáp án:** có. Tạo lại bằng `new BigDecimal("5.00")` giữ scale 2, nên `equals()` trả true.

**Bài 4.** Nếu `BigDecimal("1E+3")` được format bằng `toPlainString()` rồi parse lại, `equals()` ra sao?  
**Đáp án:** false vì scale đổi từ -3 sang 0; `compareTo() == 0` vẫn true.

**Bài 5.** `String.format(Locale.US, "%.2f", Math.PI)` có tạo chuỗi có thể khôi phục đúng mọi bit của π dưới dạng double không?  
**Đáp án:** không. Chuỗi `"3.14"` đã làm tròn và thiếu thông tin.

**Bài 6.** Parser Locale đã trả một số khi nhập `"12.345,67X"`. Có nên coi đây là thành công không?  
**Đáp án:** không, nếu schema yêu cầu toàn bộ input hợp lệ. Dùng `ParsePosition` để kiểm tra parser đã tiêu thụ hết văn bản.

**Bài 7.** Vì sao chuỗi `"10"` có thể nguy hiểm hơn `"ff"` khi bên gửi và nhận không thống nhất radix?  
**Đáp án:** `"10"` là cú pháp hợp lệ trong nhiều radix nhưng mang giá trị khác, có thể sai âm thầm; `"ff"` lại bị parser thập phân từ chối.

## 13. Kết luận

- **Round-trip phải định nghĩa rõ mục tiêu:** bảo toàn số học, biểu diễn như scale, bit số thực, hay chuỗi nguyên trạng.
- Với số nguyên, dùng cặp `toString(value, radix)` và `parseInt(text, radix)`/`parseLong(text, radix)` đúng phạm vi 2..36; phân biệt signed với unsigned và ký pháp bù hai.
- Với số nguyên lớn, dùng `BigInteger`; với `BigDecimal` cần giữ scale, dùng `toString()` và `new BigDecimal(text)` để round-trip theo `equals()`.
- Với `double`/`float`, dùng `toString/parseDouble` hoặc `toString/parseFloat` để giữ lại giá trị floating-point. Nếu phải bảo toàn *raw NaN payload*, cần định dạng bit chuyên biệt.
- Với Locale/format hiển thị, hãy kiểm tra khả năng làm tròn, kiểu parse, dấu phân cách và tiêu thụ toàn bộ đầu vào.
- Một bộ kiểm thử đáng tin phải bao gồm trường hợp biên, cơ số, chuỗi không hợp lệ, overflow và phép so sánh phù hợp với hợp đồng dữ liệu.

Tài liệu tra cứu chính thức (Java SE 17):
- [`Integer` — toString, parseInt, unsigned](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html)
- [`Long`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html)
- [`BigInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html)
- [`BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [`Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html)
- [`DecimalFormat`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html)
- [`ParsePosition`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-009">Dùng BigInteger hoặc phép tính tham chiếu để phát hiện tràn</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng BigInteger hoặc phép tính tham chiếu để phát hiện tràn trong Java

## 1. Tại sao cần kiểm thử tràn số?

Trong Java, `int` và `long` là số nguyên có dấu với độ rộng cố định:

| Kiểu | Độ rộng | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `int` | 32 bit | `-2147483648` | `2147483647` |
| `long` | 64 bit | `-9223372036854775808` | `9223372036854775807` |

Các phép cộng, trừ, nhân kiểu nguyên thông thường **không ném ngoại lệ khi tràn**. Kết quả chỉ giữ các bit thấp trong độ rộng tương ứng rồi được diễn giải theo số nguyên có dấu hai bù. Ví dụ:

~~~java
int x = Integer.MAX_VALUE;
int wrapped = x + 1; // -2147483648, không có exception
~~~

Kết quả toán học đúng phải là `2147483648`, lớn hơn `Integer.MAX_VALUE` một đơn vị. Nếu ứng dụng chỉ kiểm tra “không có exception”, lỗi có thể lọt vào số lượng hàng, tổng doanh thu biểu diễn theo đơn vị nhỏ nhất, timestamp, giới hạn bộ nhớ, kích thước mảng hoặc thuật toán so sánh.

**Chiến lược kiểm thử cốt lõi:** tính cùng phép toán bằng một biểu diễn có phạm vi đủ lớn để làm **kết quả tham chiếu (oracle)**, chẳng hạn `BigInteger`, rồi so kết quả đó với miền giá trị của kiểu đích. Đối với phép toán đang chạy trong sản phẩm, `Math.addExact`, `subtractExact`, `multiplyExact` và các API kiểm tra chính xác khác giúp phát hiện tràn ngay tại chỗ.

Có hai câu hỏi khác nhau:

1. **Kết quả toán học đúng bằng bao nhiêu?** Dùng `BigInteger` hoặc phép tính với kiểu rộng hơn khi đủ chắc chắn.
2. **Có thể biểu diễn kết quả chính xác bằng `int` hoặc `long` không?** So với `MIN_VALUE`/`MAX_VALUE`, hoặc dùng `intValueExact()`, `longValueExact()` và các hàm `Math.*Exact`.

Không nên dùng ngay kết quả đã tràn làm đáp án tham chiếu, vì không còn đủ thông tin để biết giá trị toán học ban đầu.

## 2. Cơ chế tràn số nguyên: hai bù và miền biểu diễn

Với số nguyên có dấu `n` bit, miền giá trị là:

\[
-2^{n-1} \le x \le 2^{n-1}-1.
\]

Khi một phép cộng/trừ/nhân nguyên thông thường vượt miền, Java giữ phần phù hợp với độ rộng 32/64 bit. Có thể hình dung kết quả được lấy theo modulo \(2^n\), sau đó diễn giải lại thành số có dấu.

Ví dụ `Integer.MAX_VALUE + 1`:

- Kết quả toán học: `2147483648 = 2^31`.
- Biểu diễn `int` 32 bit ứng với bit dấu bật và các bit còn lại 0.
- Giá trị `int` Java nhận được: `-2147483648`, tức `Integer.MIN_VALUE`.

Với `Long.MAX_VALUE * 2`:

- Kết quả toán học: `18446744073709551614`.
- Kết quả `long` thông thường: `-2`.

**Không có cờ overflow tự động trả về từ toán tử `+`, `-`, `*`.** Nếu ứng dụng cần từ chối tràn, phải chủ động dùng API exact hoặc kiểm tra với oracle.

Đây là hành vi **được Java định nghĩa**, không phải undefined behavior. Nhưng việc kết quả có tính xác định không làm nó đúng với yêu cầu nghiệp vụ.

### 2.1. Đừng nhầm tràn với mất độ chính xác dấu phẩy động

`float` và `double` hoạt động theo hệ số thực dấu phẩy động IEEE 754. Vượt giới hạn có thể dẫn đến `Infinity`, trong khi nhiều phép tính không tràn vẫn bị làm tròn vì precision hữu hạn. Chủ đề này tập trung vào **tràn `int`/`long`**, với `BigInteger` làm tham chiếu cho số học nguyên chính xác. Không dùng cùng một cách so sánh byte-for-byte cho số thực khi hợp đồng cho phép sai số xấp xỉ.

`BigDecimal` có thể hữu ích cho phép tính thập phân chính xác, nhưng nếu mục tiêu kiểm thử là phép toán nguyên `int`/`long`, `BigInteger` biểu đạt trực tiếp kết quả nguyên và thường là oracle rõ ràng hơn.

## 3. BigInteger làm oracle độc lập

`BigInteger` (gói `java.math`) biểu diễn số nguyên với độ chính xác tùy ý theo tài nguyên máy, không giới hạn trong 32 hoặc 64 bit.

~~~java
import java.math.BigInteger;

long a = Long.MAX_VALUE;
long b = 2L;

long actual = a * b; // -2, kết quả long bị tràn
BigInteger expected = BigInteger.valueOf(a)
        .multiply(BigInteger.valueOf(b));
// 18446744073709551614: kết quả toán học chính xác
~~~

`BigInteger` bất biến. Các phép toán như `add`, `subtract`, `multiply`, `divide`, `remainder`, `pow`, `shiftLeft` trả đối tượng kết quả mới. Vì vậy các phép tham chiếu không chịu tràn kiểu `int`/`long` trong điều kiện bộ nhớ và giới hạn tài nguyên cho phép.

**Bẫy quan trọng:** phải chuyển **toán hạng gốc** sang `BigInteger` *trước* khi tính:

~~~java
long a = Long.MAX_VALUE;
long b = 2L;

BigInteger wrongOracle = BigInteger.valueOf(a * b);
// -2: phép nhân long đã tràn trước khi BigInteger xuất hiện!

BigInteger correctOracle = BigInteger.valueOf(a)
        .multiply(BigInteger.valueOf(b));
// 18446744073709551614
~~~

Tương tự, `BigInteger.valueOf((long) (intA + intB))` **không an toàn** nếu phép cộng `int` đã được thực hiện trước khi ép kiểu.

### 3.1. Kiểm tra oracle có nằm trong phạm vi long/int không

~~~java
BigInteger exact = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);

boolean fitsLong =
        exact.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
        && exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
// false
~~~

Với `int`, thay các cận bằng `Integer.MIN_VALUE` và `Integer.MAX_VALUE`. Đây là kiểm tra chính xác dựa trên toán học và không dựa vào kết quả primitive đã bị tràn.

Có lựa chọn ngắn hơn:

~~~java
try {
    long exactLong = exact.longValueExact();
} catch (ArithmeticException ex) {
    // Giá trị không nằm trong miền long
}
~~~

`intValueExact()` và `longValueExact()` của `BigInteger` ném `ArithmeticException` khi không thể biểu diễn chính xác trong kiểu đích.

**Không dùng** `exact.longValue()` hoặc `exact.intValue()` làm phép kiểm tra tràn: các phương thức chuyển đổi không có chữ `Exact` có thể bỏ bit cao và trả một giá trị đã bị thay đổi.

## 4. API Math.*Exact giúp phát hiện tràn tại phép toán

Java cung cấp những phương thức kiểm tra khi cần kết quả `int`/`long` chính xác:

| Nhu cầu | API | Khi vượt miền |
| --- | --- | --- |
| Cộng | `Math.addExact(a, b)` | Ném `ArithmeticException` |
| Trừ | `Math.subtractExact(a, b)` | Ném `ArithmeticException` |
| Nhân | `Math.multiplyExact(a, b)` | Ném `ArithmeticException` |
| Đổi dấu | `Math.negateExact(value)` | Ném `ArithmeticException` |
| Tăng/giảm một đơn vị | `Math.incrementExact(value)`, `Math.decrementExact(value)` | Ném `ArithmeticException` |
| Trị tuyệt đối của `int`/`long` | `Math.absExact(value)` | Ném khi là giá trị MIN tương ứng |
| Thu hẹp `long` xuống `int` | `Math.toIntExact(value)` | Ném nếu ngoài phạm vi int |

Các overload và phương thức cụ thể phải được chọn đúng kiểu. Chẳng hạn `Math.addExact(int,int)` trả `int`, còn `Math.addExact(long,long)` trả `long`. Khi hai toán hạng là `int` và mong muốn kết quả `long`, có thể chủ động mở rộng ít nhất một toán hạng **trước khi cộng**.

~~~java
int left = Integer.MAX_VALUE;
int right = 1;

long correct = (long) left + right;  // 2147483648
long wrong = (long) (left + right);  // -2147483648

// Nếu kết quả bắt buộc phải là int:
try {
    int checked = Math.addExact(left, right);
} catch (ArithmeticException ex) {
    // Vượt phạm vi int, xử lý lỗi theo hợp đồng nghiệp vụ
}
~~~

Trong biến thể `wrong`, dấu ngoặc buộc cộng `int` trước rồi mới ép kiểu `long`, nên không thể “sửa” tràn đã xảy ra. Trong biến thể `correct`, toán hạng trái thành `long` trước khi cộng, phép cộng được tính ở phạm vi 64 bit.

Với hai toán hạng `long`, mở rộng lên `long` lần nữa không giúp gì. Dùng `Math.*Exact` hoặc `BigInteger` để tính một kết quả lớn hơn miền 64 bit.

### 4.1. BigInteger và Math.*Exact bổ trợ nhau ra sao?

- **Trong logic ứng dụng:** dùng `Math.*Exact` nếu tràn là lỗi và cần phát hiện ngay khi thao tác.
- **Trong test:** dùng `BigInteger` độc lập để xác nhận phép toán trả đúng giá trị khi còn trong miền và báo lỗi khi vượt miền.
- **Khi dữ liệu hợp lệ thực sự có thể vượt long:** dùng `BigInteger` trong mô hình dữ liệu/kết quả thay vì chỉ bắt lỗi rồi tiếp tục lưu vào `long`.
- **Khi cần giá trị thay thế như saturating arithmetic:** định nghĩa rõ hợp đồng trả `MIN_VALUE`/`MAX_VALUE`, rồi kiểm tra với oracle. Không gọi đó là phép tính exact.

Một test chỉ gọi `Math.addExact` rồi tin nó đúng có thể hữu ích để kiểm tra API ứng dụng đã chọn. Nhưng kiểm tra độc lập bằng `BigInteger` giúp bắt lỗi ở cách gọi nhầm overload, ép kiểu sai thứ tự hoặc xử lý trường hợp biên thiếu nhất quán.

## 5. Những phép toán đặc biệt rất dễ bỏ sót

### 5.1. Phép chia `MIN_VALUE / -1`

Trong Java, phép chia nguyên có một trường hợp tràn đặc biệt:

~~~java
long wrapped = Long.MIN_VALUE / -1L;
// -9223372036854775808: không ném ArithmeticException
~~~

Kết quả toán học đúng là `9223372036854775808`, **lớn hơn `Long.MAX_VALUE`**. Tương tự, `Integer.MIN_VALUE / -1` trả lại `Integer.MIN_VALUE`.

`Math.addExact` hay `multiplyExact` không phải phép kiểm tra cho phép chia. Với Java 17, bạn có thể kiểm tra trường hợp ngoại lệ bằng điều kiện:

~~~java
static long divideChecked(long dividend, long divisor) {
    if (divisor == 0L) {
        throw new ArithmeticException("/ by zero");
    }
    if (dividend == Long.MIN_VALUE && divisor == -1L) {
        throw new ArithmeticException("long division overflow");
    }
    return dividend / divisor;
}
~~~

Đây là phương thức minh họa trong một lớp; nếu dùng phép chia BigInteger làm oracle, nhớ rằng `BigInteger.divide` cũng từ chối chia cho 0. Phép chia nguyên thông thường **cắt phần dư về 0**, không phải chia cho kết quả thực rồi làm tròn theo một rounding mode thập phân.

### 5.2. Phép đổi dấu và trị tuyệt đối của giá trị nhỏ nhất

~~~java
int x = Integer.MIN_VALUE;

int negated = -x;         // vẫn là Integer.MIN_VALUE
int magnitude = Math.abs(x); // vẫn là Integer.MIN_VALUE
~~~

Nguyên nhân: `2147483648` không nằm trong miền `int` dương. Muốn từ chối trường hợp này có `Math.negateExact(x)` và `Math.absExact(x)` (Java 17+). Nếu chỉ cần độ lớn và chấp nhận `long`, dùng:

~~~java
long exactAbs = Math.abs((long) Integer.MIN_VALUE);
// 2147483648
~~~

Ép sang `long` phải diễn ra **trước** phép lấy trị tuyệt đối. `Math.abs(Integer.MIN_VALUE)` có thể trả số âm, nên không được coi `Math.abs` là phép kiểm tra rằng giá trị chắc chắn không âm.

### 5.3. Nhân, bình phương và biểu thức trung gian

~~~java
int x = 50_000;

long wrongSquare = x * x;         // -1794967296 rồi nới sang long
long correctSquare = (long) x * x; // 2500000000
~~~

Đối với phép nhân hai `int`, `Math.multiplyFull(x, x)` trả kết quả `long` đầy đủ trong Java 17. Với phép nhân `long`, kết quả có thể vượt 64 bit; `BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))` là oracle phù hợp.

`BigInteger` có `shiftLeft` để kiểm tra phép dịch cần giữ toàn bộ bit. Trong khi đó, toán tử `<<` trên primitive luôn giữ độ rộng kiểu và có thể bỏ bit cao; không nên dựa vào `Math.addExact` để phát hiện mất bit ở phép dịch.

### 5.4. Tích lũy trong vòng lặp và stream

~~~java
int sum = 0;
for (int value : values) {
    sum = Math.addExact(sum, value);
}
~~~

Đoạn mã trên nằm trong một phương thức có biến `int[] values`. Nó phát hiện tràn tại **từng bước tích lũy**.

Một oracle độc lập có thể dùng:

~~~java
BigInteger reference = BigInteger.ZERO;
for (int value : values) {
    reference = reference.add(BigInteger.valueOf(value));
}
~~~

Cần phân biệt hai mục tiêu:

- Nếu yêu cầu “không có bất kỳ phép cộng trung gian nào được phép tràn”, phép tính kiểm tra từng bước phù hợp.
- Nếu chỉ yêu cầu “**tổng toán học cuối cùng** nằm trong miền int”, việc tích lũy `int` có thể tràn ở giữa rồi quay về giá trị hợp lệ khi có số âm bù lại. Oracle `BigInteger` vẫn biết tổng thực cuối cùng. Tùy hợp đồng, một quy tắc có thể chặt hơn quy tắc kia.

Ví dụ mảng `{Integer.MAX_VALUE, 1, -1}` có tổng toán học cuối cùng bằng `Integer.MAX_VALUE`, nhưng cách cộng kiểm tra từng bước sẽ thất bại tại phần tử thứ hai. Cần xác định rõ ứng dụng phải ngăn tràn **trong mọi bước** hay chỉ chứng minh phạm vi của **kết quả cuối**.

`IntStream.sum()` trả `int` và có thể tràn. `mapToLong(x -> x).sum()` tăng phạm vi cho nguồn `int` nhưng tổng cực lớn vẫn có thể vượt `long`. Nếu cần tổng toán học tùy ý, tích lũy bằng `BigInteger`.

## 6. Ví dụ Java 17+ hoàn chỉnh: so sánh kết quả với oracle

Tạo file `OverflowOracleDemo.java`:

~~~java
import java.math.BigInteger;
import java.util.Random;

public class OverflowOracleDemo {
    private static final BigInteger LONG_MIN =
            BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX =
            BigInteger.valueOf(Long.MAX_VALUE);

    private static boolean fitsLong(BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0
                && value.compareTo(LONG_MAX) <= 0;
    }

    private static void verifyRandomAdd() {
        Random rng = new Random(2026L);

        for (int i = 0; i < 10_000; i++) {
            long left = rng.nextLong();
            long right = rng.nextLong();
            BigInteger oracle = BigInteger.valueOf(left)
                    .add(BigInteger.valueOf(right));
            boolean fits = fitsLong(oracle);

            try {
                long got = Math.addExact(left, right);
                if (!fits || !BigInteger.valueOf(got).equals(oracle)) {
                    throw new AssertionError("undetected overflow");
                }
            } catch (ArithmeticException ex) {
                if (fits) {
                    throw new AssertionError("false overflow");
                }
            }
        }

        System.out.println("random additions: 10000 PASS");
    }

    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;

        System.out.println("int wrapped: " + (max + 1));
        System.out.println("int oracle: "
                + BigInteger.valueOf(max).add(BigInteger.ONE));

        try {
            Math.addExact(max, 1);
        } catch (ArithmeticException ex) {
            System.out.println("Math.addExact: ArithmeticException");
        }

        System.out.println("widen after: " + (long) (max + 1));
        System.out.println("widen before: " + ((long) max + 1));

        long top = Long.MAX_VALUE;
        System.out.println("long wrapped multiply: " + (top * 2));
        System.out.println("long oracle multiply: "
                + BigInteger.valueOf(top).multiply(BigInteger.TWO));

        try {
            Math.multiplyExact(top, 2L);
        } catch (ArithmeticException ex) {
            System.out.println("Math.multiplyExact: ArithmeticException");
        }

        System.out.println("long MIN / -1: "
                + (Long.MIN_VALUE / -1L));
        System.out.println("BigInteger MIN / -1: "
                + BigInteger.valueOf(Long.MIN_VALUE).negate());

        verifyRandomAdd();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac OverflowOracleDemo.java
java OverflowOracleDemo
~~~

Kết quả:

~~~text
int wrapped: -2147483648
int oracle: 2147483648
Math.addExact: ArithmeticException
widen after: -2147483648
widen before: 2147483648
long wrapped multiply: -2
long oracle multiply: 18446744073709551614
Math.multiplyExact: ArithmeticException
long MIN / -1: -9223372036854775808
BigInteger MIN / -1: 9223372036854775808
random additions: 10000 PASS
~~~

Cách đọc kết quả:

- `int wrapped` là kết quả primitive đã tràn; `int oracle` cho kết quả toán học đúng.
- `Math.addExact` phát hiện tràn thay vì trả số sai.
- `widen after` thực hiện mở rộng **sau** khi cộng `int`, nên quá muộn; `widen before` chuyển một toán hạng sang `long` trước khi tính.
- `long wrapped multiply` bằng `-2` dù kết quả thật dương rất lớn.
- `Long.MIN_VALUE / -1` chứng minh phép chia nguyên có một trường hợp tràn đặc biệt mà phép chia thông thường không báo.
- `verifyRandomAdd` tạo 10.000 cặp `long` từ seed cố định. Với từng cặp, oracle `BigInteger` quyết định kết quả có vừa long không; test yêu cầu `Math.addExact` trả đúng số khi vừa và ném ngoại lệ khi vượt miền. Dòng `PASS` chỉ được in sau khi toàn bộ vòng lặp kết thúc không có assertion thất bại.

Đây là **chương trình minh họa kiểm thử logic**, không phải benchmark tốc độ. `BigInteger` thường tốn tài nguyên hơn primitive, nhưng trong test nó ưu tiên sự chính xác và tính độc lập của oracle.

## 7. Kiểm thử biên trước, rồi mới kiểm thử ngẫu nhiên

Random testing có ích nhưng không thay thế kiểm thử ranh giới. Những giá trị gây tràn thường tập trung ở các mép của miền; một bộ kiểm thử nên bắt đầu bằng các case xác định được mong đợi.

| Biểu thức | Kết quả toán học | `int`/`long` thông thường | Kỳ vọng với phép kiểm tra exact |
| --- | --- | --- | --- |
| `Integer.MAX_VALUE + 1` | `2147483648` | `Integer.MIN_VALUE` | `Math.addExact` ném lỗi |
| `Integer.MIN_VALUE - 1` | `-2147483649` | `Integer.MAX_VALUE` | `Math.subtractExact` ném lỗi |
| `50_000 * 50_000` khi cả hai là int | `2500000000` | `-1794967296` | `Math.multiplyExact(int,int)` ném lỗi |
| `Long.MAX_VALUE + 1L` | `9223372036854775808` | `Long.MIN_VALUE` | `Math.addExact(long,long)` ném lỗi |
| `Long.MAX_VALUE * 2L` | `18446744073709551614` | `-2` | `Math.multiplyExact(long,long)` ném lỗi |
| `-Integer.MIN_VALUE` | `2147483648` | `Integer.MIN_VALUE` | `Math.negateExact` ném lỗi |
| `Long.MIN_VALUE / -1L` | `9223372036854775808` | `Long.MIN_VALUE` | Phải xử lý trường hợp đặc biệt hoặc kiểm bằng oracle |
| `Integer.MAX_VALUE + 0` | `2147483647` | Đúng | `Math.addExact` trả kết quả |
| `Long.MIN_VALUE * 1L` | `Long.MIN_VALUE` | Đúng | `Math.multiplyExact` trả kết quả |

Test thêm các cặp `MIN_VALUE`, `MAX_VALUE`, `-1`, `0`, `1`, `2`, số gần ranh giới, giá trị âm/dương đối xứng và số lớn. Với phép chia, phải phân biệt **chia cho 0** và **tràn `MIN_VALUE / -1`**, vì đây là hai điều kiện lỗi khác nhau.

### 7.1. Dùng property-based testing với BigInteger

Một thuộc tính (*property*) hữu ích cho hàm cộng long kiểm tra tràn:

> Với mọi `a` và `b`, gọi `BigInteger.valueOf(a).add(BigInteger.valueOf(b))` được oracle. Nếu oracle nằm trong khoảng `Long.MIN_VALUE..Long.MAX_VALUE`, hàm cần trả đúng oracle; nếu nằm ngoài, hàm phải báo tràn theo hợp đồng.

Áp dụng tương tự cho trừ/nhân. Có thể lấy dữ liệu bằng `Random` với seed cố định như chương trình mẫu để tái tạo lỗi. Tốt hơn nữa, kết hợp một tập giá trị biên được chọn thủ công; các cặp ngẫu nhiên `long` có thể gặp tràn cộng nhưng dễ bỏ qua một số tình huống quan trọng như `MIN_VALUE / -1` nếu không cố ý tạo.

Nếu dùng thư viện property-testing, mỗi phép thử thất bại cần ghi đủ toán hạng, operation, giá trị oracle và exception để dễ tái lập. Thu hẹp (*shrinking*) giúp tìm phản ví dụ đơn giản hơn, nhưng không làm mất ý nghĩa của kiểm thử biên.

### 7.2. Phải xác định hợp đồng trước khi test

Không phải ứng dụng nào cũng cần ném lỗi khi vượt miền:

- **Checked arithmetic:** tràn là lỗi; dùng `Math.*Exact` và kỳ vọng `ArithmeticException`.
- **Wraparound có chủ đích:** một số thuật toán hash/bit manipulation định nghĩa modulo \(2^{32}\) hoặc \(2^{64}\); oracle vẫn hữu ích nhưng cần so **bit thấp** theo đặc tả, thay vì luôn yêu cầu ném lỗi.
- **Saturating arithmetic:** kẹp kết quả ở min/max; oracle xác định trước khi kẹp rồi kiểm kết quả cuối.
- **Arbitrary precision:** giữ giá trị `BigInteger` hoặc kiểu thích hợp, không ép về `long`.

Việc so `long actual` với `BigInteger.longValue()` chỉ kiểm tra phần bit thấp và **không phát hiện tràn** nếu yêu cầu là số học chính xác. Cách so ấy chỉ hợp lệ khi contract thực sự là modulo/wraparound.

## 8. Phát hiện tràn khi ép kiểu và tổng hợp nhiều bước

### 8.1. Ép long xuống int

~~~java
long input = 2_147_483_648L;

int silent = (int) input; // -2147483648, bỏ bit cao

try {
    int checked = Math.toIntExact(input);
} catch (ArithmeticException ex) {
    // input nằm ngoài miền int
}
~~~

`Math.toIntExact` chỉ kiểm tra **chuyển `long` → `int`**. Nếu kết quả đã tràn khi tính trong `long` trước đó, nó không thể phục hồi giá trị gốc.

`BigInteger.intValueExact()` cũng phát hiện giá trị ngoài miền int:

~~~java
BigInteger large = BigInteger.valueOf(Integer.MAX_VALUE)
        .add(BigInteger.ONE);

large.intValue();      // -2147483648: mất bit
large.intValueExact(); // ArithmeticException
~~~

### 8.2. Tìm lỗi ở đâu trong chuỗi tính toán

Một quy trình chẩn đoán có thể đi theo các bước:

1. **Xác định kiểu của từng toán hạng ở thời điểm phép toán chạy**: hai `int` cộng trước khi gán vào `long` vẫn cộng theo `int`.
2. **Ghi lại toán hạng gốc trước phép toán nghi ngờ**, không chỉ giá trị đầu ra sau khi đã tràn.
3. **Tính oracle bằng BigInteger trên các toán hạng gốc**, tách khỏi mã sản phẩm đang kiểm thử.
4. **So cận kiểu đích** và xác định bước đầu tiên vượt miền.
5. **Thay toán tử bằng API exact hoặc đổi mô hình kiểu** nếu hợp đồng yêu cầu từ chối tràn.
6. **Thêm regression test** với bộ số gây lỗi và những giá trị sát biên.

Cẩn thận khi debug bằng log: nếu log chỉ in `sum` đã bị wrap, việc thấy số âm có thể gây hiểu nhầm rằng dữ liệu nguồn đã âm. Log toán hạng gốc và ngữ nghĩa đơn vị/range giúp truy ngược nguyên nhân.

### 8.3. Tính toán kích thước và chỉ số

Ví dụ sai kinh điển là:

~~~java
int mid = (left + right) / 2;
~~~

Khi `left` và `right` là chỉ số int rất lớn, phép cộng có thể tràn dù trung điểm toán học vẫn hợp lệ. Với `0 <= left <= right`, biểu thức:

~~~java
int mid = left + (right - left) / 2;
~~~

tránh phép cộng trực tiếp hai chỉ số lớn trong trường hợp điều kiện đã được giữ. Nhưng **đừng áp công thức này ngoài điều kiện** vì `right - left` cũng có thể tràn khi chỉ số có dấu khác thường. Nếu cần hỗ trợ mọi giá trị int, có thể nâng sang long trước:

~~~java
long midWide = ((long) left + (long) right) / 2;
~~~

Kết quả khi chia hai và làm tròn có thể khác nếu có số âm, nên định nghĩa lại hợp đồng trung điểm khi không còn dùng chỉ số không âm. Sửa overflow phải đi kèm bảo toàn **ngữ nghĩa tính toán**.

## 9. Các lỗi phổ biến khi tạo phép tính tham chiếu

1. **Chuyển sang BigInteger sau khi đã tràn:** `BigInteger.valueOf(a * b)` không còn là oracle cho tích chính xác nếu `a*b` chạy bằng long.
2. **Ép về long/int sau tính toán mà không dùng Exact:** `big.longValue()` và `big.intValue()` có thể mất bit cao.
3. **Cho rằng mọi phép cộng có thể sửa bằng cast ở cuối:** `(long)(a+b)` tính `a+b` bằng int trước khi cast.
4. **Chỉ kiểm tra số dương:** âm dưới `MIN_VALUE` cũng tràn, còn `MIN_VALUE` đổi dấu/trị tuyệt đối là trường hợp đặc biệt.
5. **Bỏ qua phép chia `MIN_VALUE / -1`:** kết quả primitive đã tràn mà không ném ngoại lệ.
6. **Nhầm overflow với rounding của float/double:** BigInteger oracle áp dụng cho số nguyên chính xác; số thực cần chiến lược test và sai số thích hợp.
7. **Dùng lại chính công thức bị kiểm thử làm oracle:** hai phía có cùng lỗi sẽ cùng sai. Tách đường tính tham chiếu, dùng kiểu rộng/độ chính xác độc lập.
8. **Random test mà không có case biên:** xác suất chạm đúng `Integer.MIN_VALUE`, `Long.MAX_VALUE` hoặc tổ hợp đặc biệt có thể rất nhỏ.
9. **Chỉ kiểm kết quả cuối khi hợp đồng cấm tràn trung gian:** tổng cuối có thể nằm trong miền sau khi các tổng từng bước từng vượt miền.
10. **Báo lỗi sau khi đã dùng giá trị hỏng:** kiểm tra tràn tại chỗ tạo giá trị, trước khi dùng làm chỉ số, kích thước, giới hạn hoặc thanh toán.
11. **Đổi sang BigInteger khắp production không có chủ đích:** cần đánh giá hiệu năng/bộ nhớ và khả năng tương tác. Nếu phạm vi dữ liệu luôn phải nằm trong long, phép tính exact thường đơn giản hơn.
12. **Ngộ nhận BigInteger tuyệt đối không có giới hạn thực tế:** nó không bị giới hạn 32/64 bit trong toán học thông thường, nhưng bộ nhớ, kích thước mảng và thời gian tính toán vẫn hữu hạn.

## 10. Tự kiểm tra

**Câu 1.** Vì sao `long result = Integer.MAX_VALUE + 1;` lại có thể nhận số âm?

**Đáp án:** Hai toán hạng được cộng theo `int` trước khi gán vào `long`. Cộng tràn rồi mới mở rộng. Viết `(long) Integer.MAX_VALUE + 1` để phép cộng chạy bằng long.

**Câu 2.** Kết quả của `BigInteger.valueOf(Long.MAX_VALUE * 2L)` có phải oracle đúng không?

**Đáp án:** Không. Phép nhân long đã tràn thành `-2` trước khi chuyển. Oracle đúng là `BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)`.

**Câu 3.** Khi nào `Math.addExact(a,b)` trả số, khi nào ném lỗi?

**Đáp án:** Trả chính xác kết quả nếu còn trong phạm vi kiểu của overload được gọi; ném `ArithmeticException` nếu phép cộng vượt phạm vi đó.

**Câu 4.** `Long.MIN_VALUE / -1L` có ném ngoại lệ không?

**Đáp án:** Không; toán tử phép chia long trả `Long.MIN_VALUE`. Kết quả toán học lớn hơn `Long.MAX_VALUE`, nên phải kiểm trường hợp này hoặc dùng BigInteger nếu cần tính chính xác.

**Câu 5.** Có thể dùng `BigInteger.longValue()` để phát hiện một số quá lớn cho long không?

**Đáp án:** Không. Dùng `longValueExact()` hoặc so với hai cận để phát hiện tràn; `longValue()` có thể bỏ bit cao.

**Câu 6.** Mảng `{Integer.MAX_VALUE, 1, -1}` có tổng toán học cuối là bao nhiêu và vì sao test dùng `Math.addExact` có thể thất bại?

**Đáp án:** Tổng chính xác cuối bằng `Integer.MAX_VALUE`, nhưng tổng trung gian sau phần tử thứ hai vượt phạm vi int. Test từ chối mọi tràn trung gian sẽ phát hiện ngay bước đó; test chỉ kiểm tổng toán học cuối cần một oracle khác.

**Câu 7.** Nếu yêu cầu thực sự là arithmetic modulo \(2^{32}\), oracle nên làm gì?

**Đáp án:** Tính kết quả nguyên chính xác bằng BigInteger, sau đó xác định 32 bit thấp theo hợp đồng. Khi hợp đồng cố ý wrap, việc `Math.addExact` ném lỗi không nhất thiết là hành vi mong muốn.

## 11. Tài liệu chính thức

- [`BigInteger` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): số nguyên chính xác tùy ý, `valueOf`, `add`, `multiply`, `intValueExact`, `longValueExact`.
- [`Math` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `addExact`, `subtractExact`, `multiplyExact`, `absExact`, `negateExact`, `toIntExact`.
- [Java Language Specification 17, Integer Operations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html): ngữ nghĩa toán tử số nguyên, chuyển kiểu, toán tử số học và trường hợp chia đặc biệt.
- [`Integer` và `Long` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): giới hạn kiểu số nguyên; xem thêm [`Long`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html).

**Ghi nhớ:** Khi kiểm thử tràn `int`/`long`, tạo kết quả tham chiếu bằng `BigInteger` **trước khi tính**, xác minh miền kiểu đích và đối chiếu hành vi expected/actual. Trong logic sản phẩm, dùng `Math.*Exact` nếu muốn báo tràn ngay; khi giá trị hợp lệ vượt 64 bit thì giữ `BigInteger` thay vì ép về primitive. Kiểm thử các giá trị biên, phép chia `MIN_VALUE / -1` và lỗi “ép kiểu sau phép toán” để tránh những sai lệch âm thầm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-010">Kiểm thử số ngẫu nhiên bằng seed hoặc nguồn được truyền vào</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử số ngẫu nhiên bằng seed hoặc nguồn được truyền vào trong Java

## 1. Vì sao code có yếu tố ngẫu nhiên khó kiểm thử?

Một chức năng dùng số ngẫu nhiên có thể chọn phần tử, mô phỏng xúc xắc, lấy mẫu telemetry, quyết định nhánh A/B, sinh dữ liệu thử nghiệm hoặc thêm độ trễ *jitter*. Nếu mỗi lần chạy test đều lấy nguồn ngẫu nhiên mới, một test có thể thành công nhiều lần rồi thất bại trong CI dù code không đổi. Tình trạng này gọi là **flaky test**: kết quả không ổn định do test phụ thuộc dữ liệu không kiểm soát.

Ví dụ khó kiểm thử:

~~~java
public static int rollDie() {
    return new java.util.Random().nextInt(6) + 1;
}
~~~

Mỗi lần gọi tạo một đối tượng `Random` mới rồi sinh giá trị bất kỳ từ 1 đến 6. Một test viết `assertEquals(4, rollDie())` không có cơ sở: 4 chỉ là một kết quả khả dĩ.

Hai kỹ thuật quan trọng:

1. **Cố định seed** cho một bộ sinh số giả ngẫu nhiên (PRNG) để lặp lại chuỗi khi cùng thuật toán, seed và thứ tự gọi.
2. **Truyền nguồn sinh số ngẫu nhiên từ ngoài vào** (*dependency injection*) để code nghiệp vụ không tự tạo nguồn; test có thể cung cấp PRNG cố định seed hoặc nguồn giả lập có kết quả định trước.

Đây là bài về **thiết kế kiểm thử và chẩn đoán**, không phải hướng dẫn tạo số ngẫu nhiên dùng cho bảo mật. Với token, mật khẩu, khóa mật mã hoặc nonce bảo mật, phải dùng `SecureRandom` và quy tắc riêng (xem mục 9).

## 2. Từ seed đến tính tái lập

`java.util.Random` là PRNG. Nó tạo chuỗi các giá trị từ trạng thái nội bộ, nên hai instance `Random` độc lập có cùng seed sẽ cho cùng chuỗi nếu thực hiện cùng các lời gọi theo cùng thứ tự.

~~~java
import java.util.Random;

Random first = new Random(2026L);
Random second = new Random(2026L);

System.out.println(first.nextInt(10) == second.nextInt(10)); // true
System.out.println(first.nextInt(10) == second.nextInt(10)); // true
~~~

Ở đây seed `2026L` không làm hai đối tượng dùng chung trạng thái; nó khởi tạo trạng thái tương đương. Hai phép gọi tuần tự tạo hai cặp giá trị trùng nhau. Không cần ghi nhớ giá trị cụ thể để hiểu cơ chế.

Điều kiện để tái lập hữu ích:

- Cùng **thuật toán**: `Random`, `SplittableRandom` hoặc một algorithm của `RandomGeneratorFactory` không nhất thiết sinh cùng chuỗi dù seed giống nhau.
- Cùng **seed**, số lần gọi và phương thức được gọi: thêm một lời gọi `nextInt()` trước bước cần test sẽ làm lệch các giá trị tiếp theo.
- Cùng cách phân phối giá trị: `nextInt(6)` và `nextInt()` không có nghĩa như nhau.
- Không có nhiều luồng tranh nhau gọi vào cùng một nguồn theo thứ tự không xác định.

Seed là công cụ tái hiện lỗi rất hữu ích: ghi lại seed cùng tên thuật toán, cấu hình kiểm thử và dữ liệu đầu vào. Nếu testcase thất bại ở seed cụ thể, chạy lại với đúng thông tin đó giúp điều tra.

**Không nên đánh đồng tính tái lập với tính dự đoán nghiệp vụ.** Một seed cố định giúp lặp lại chuỗi, nhưng một test quá phụ thuộc vào “giá trị lần gọi thứ 37 phải là 6” dễ bị hỏng khi code thêm một lần rút số ngẫu nhiên hợp lệ.

## 3. Java 17+: truyền `RandomGenerator` vào logic nghiệp vụ

Java 17 cung cấp interface `java.util.random.RandomGenerator`. `java.util.Random` là một implementation của interface này; vì thế có thể nhận dependency qua interface, trong khi nơi khởi tạo quyết định dùng implementation nào.

Ví dụ:

~~~java
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class Dice {
    private final RandomGenerator random;

    public Dice(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public int roll() {
        return random.nextInt(6) + 1;
    }
}
~~~

Đây là lớp độc lập minh họa; ở mục 7 có chương trình Java hoàn chỉnh chứa nhiều bài test. `random.nextInt(6)` trả một `int` trong khoảng **0 đến 5**; cộng thêm 1 để tạo giá trị **1 đến 6**. Việc dùng overload có bound giúp tránh modulo thủ công dễ tạo phân phối lệch hoặc số âm.

Lợi ích của dependency injection:

- Production có thể truyền một nguồn PRNG được lựa chọn theo chính sách ứng dụng.
- Unit test truyền một `Random` có seed cố định để tái hiện một chuỗi.
- Unit test cần nhánh cụ thể truyền một nguồn giả (*fake*) để trả đúng những giá trị đã chọn.
- Lớp nghiệp vụ chỉ diễn đạt cách sử dụng số ngẫu nhiên, không quyết định cách khởi tạo seed và không cần biết thuật toán.

Ví dụ cách dùng:

~~~java
// Ví dụ trong phạm vi một phương thức:
Dice productionDice = new Dice(new java.util.Random());
Dice repeatableDice = new Dice(new java.util.Random(2026L));
~~~

Có thể sử dụng `RandomGenerator.getDefault()` khi chính sách ứng dụng chấp nhận thuật toán mặc định. Nếu cần tái lập ổn định trên môi trường khác nhau, **nêu rõ implementation/algorithm và seed**; mặc định của môi trường không phải một hợp đồng cố định về chuỗi.

Nhiều ứng dụng còn cần truyền *factory* tạo RNG cho mỗi phiên hoặc mỗi worker, thay vì một instance chung. Quyết định này đặc biệt quan trọng khi có nhiều luồng và cần tái lập thứ tự tiêu thụ.

## 4. Kiểm thử bằng seed: nên khẳng định điều gì?

### Kiểm tra cùng seed tạo cùng chuỗi

Một test hợp lý so sánh hai thực thể độc lập dùng cùng seed, cùng số bước:

~~~java
import java.util.Arrays;
import java.util.Random;

Random a = new Random(2026L);
Random b = new Random(2026L);

int[] sequenceA = new int[10];
int[] sequenceB = new int[10];

for (int i = 0; i < sequenceA.length; i++) {
    sequenceA[i] = a.nextInt(100);
    sequenceB[i] = b.nextInt(100);
}

System.out.println(Arrays.equals(sequenceA, sequenceB)); // true
~~~

Test chứng minh cơ chế tái lập trong cùng thuật toán và cùng lời gọi. Nếu cần kiểm thử **logic nghiệp vụ**, ta nên assert điều kiện nghiệp vụ thực sự quan tâm hơn là chỉ kiểm tra PRNG của JDK.

Ví dụ với lớp `Dice`, có thể chạy nhiều lượt và assert `1 <= roll && roll <= 6`. Thử 10.000 lần tăng khả năng phát hiện lỗi bound hoặc phép biến đổi, nhưng vẫn không phải chứng minh phân phối hoàn toàn đồng đều.

### Tái hiện một lỗi theo seed

Giả sử một pipeline gặp lỗi ở seed `58723L`. Lưu lại:

~~~text
generator: java.util.Random
seed: 58723
scenario: choose promotion after reading customer category
input fixture: customer-group-A
~~~

Thông tin seed **một mình** có thể không đủ nếu thuật toán, thứ tự lời gọi hoặc tập dữ liệu đã đổi. Một fixture tái hiện đáng tin cần ghi cả quy tắc rút số và điều kiện của test.

Không đặt seed cố định trong hệ thống production nếu mục tiêu là tạo hành vi khó dự đoán. Seed cố định dành cho kiểm thử, mô phỏng cần tái lập hoặc chức năng nghiệp vụ có yêu cầu rõ ràng về chuỗi giả ngẫu nhiên.

### Hạn chế của seeded tests

Test chỉ dùng seed có thể không chạm được trường hợp cần thiết, ví dụ rút đúng 0 hoặc giá trị biên lớn nhất. “Chạy 1.000 lần rồi hy vọng gặp điều kiện đó” tạo test khó đọc và có nguy cơ flaky. Để kiểm tra một nhánh xác định, hãy truyền nguồn giả với kết quả được kiểm soát.

## 5. Kiểm thử bằng nguồn được truyền vào: tạo fake có kịch bản

Giả sử ứng dụng có:

~~~java
boolean shouldSample(int percent) {
    return random.nextInt(100) < percent;
}
~~~

Với `percent = 0`, kết quả luôn `false`; với `percent = 100`, kết quả luôn `true`; với `percent = 25`, các giá trị rút trong khoảng 0–24 làm kết quả `true`, còn 25–99 cho `false`.

Thay vì đoán seed nào sẽ sinh 24 hoặc 25 đúng vị trí, một fake có thể cung cấp danh sách giá trị rõ ràng. Vì `Random` implement `RandomGenerator`, ta có thể kế thừa `Random` và override `nextInt(int bound)` cho trường hợp đang kiểm thử:

~~~java
import java.util.Random;

final class ScriptedRandom extends Random {
    private final int[] values;
    private int cursor;

    ScriptedRandom(int... values) {
        super(0L);
        this.values = values.clone();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if (cursor >= values.length) {
            throw new AssertionError("Scripted values exhausted");
        }
        int value = values[cursor++];
        if (value < 0 || value >= bound) {
            throw new AssertionError(
                    "Invalid scripted value " + value
                            + " for bound " + bound);
        }
        return value;
    }

    int calls() {
        return cursor;
    }
}
~~~

Đây là một fake **chỉ dành cho test**. Nó kiểm tra bound để phát hiện code sản phẩm gọi API với khoảng không mong muốn. Nó không mô phỏng toàn bộ hành vi của `Random`, không được dùng làm PRNG production, và chỉ thay thế chính xác lời gọi `nextInt(int)` mà code nghiệp vụ thực sự sử dụng.

Nếu code sản phẩm thay sang `nextLong()` hoặc phương thức RNG khác, fake phải hỗ trợ lời gọi đó hoặc thiết kế một interface domain hẹp hơn, ví dụ:

~~~java
@FunctionalInterface
interface IndexSource {
    int nextIndex(int exclusiveUpperBound);
}
~~~

Trong thiết kế domain, truyền `IndexSource` thường tốt nếu service chỉ cần rút index. Test có thể truyền lambda `bound -> 0` để luôn chọn phần tử đầu; production truyền `random::nextInt`. Interface hẹp giảm sự phụ thuộc vào chi tiết toàn bộ RNG.

**Chọn mức trừu tượng phù hợp:** dùng `RandomGenerator` khi logic cần các thao tác RNG phổ biến; dùng interface domain nhỏ khi muốn chỉ cho phép một hoặc vài thao tác ngẫu nhiên có ý nghĩa nghiệp vụ.

## 6. Những nhánh cần ép bằng fake

Giả sử method `chooseIndex(int size)` chọn index trong `[0, size)` và method `shouldSample(int percent)` nhận phần trăm từ 0 đến 100.

| Trường hợp | Giá trị fake trả | Điều cần assert |
| --- | ---: | --- |
| `chooseIndex(6)` | `0` | Chọn phần tử đầu |
| `chooseIndex(6)` | `5` | Chọn phần tử cuối |
| `shouldSample(25)` | `24` | `true` |
| `shouldSample(25)` | `25` | `false` |
| `shouldSample(0)` | `0` | `false` |
| `shouldSample(100)` | `99` | `true` |
| `chooseIndex(0)` | Không gọi nguồn | Ném `IllegalArgumentException` trước khi rút |

Với fake, các kiểm thử này **không phụ thuộc vào xác suất** và nên cho kết quả giống nhau ở mọi lần chạy.

Phải phân biệt **giới hạn inclusive/exclusive**. Với `nextInt(bound)`, bound là **exclusive**: `nextInt(6)` không thể trả 6. Đối với `RandomGenerator.nextInt(origin, bound)`, origin là inclusive, bound là exclusive. Tránh tự dùng phép `Math.abs(random.nextInt()) % size`: `Math.abs(Integer.MIN_VALUE)` vẫn âm và phép lấy dư có thể gây phân phối lệch.

## 7. Chương trình Java 17+ chạy độc lập

Tạo tệp `RandomSourceTestingDemo.java`:

~~~java
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

public class RandomSourceTestingDemo {

    static final class Picker {
        private final RandomGenerator random;

        Picker(RandomGenerator random) {
            this.random = Objects.requireNonNull(random, "random");
        }

        int chooseIndex(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("size must be positive");
            }
            return random.nextInt(size);
        }

        int rollDie() {
            return random.nextInt(6) + 1;
        }

        boolean shouldSample(int percent) {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException(
                        "percent must be between 0 and 100");
            }
            return random.nextInt(100) < percent;
        }
    }

    static final class ScriptedRandom extends Random {
        private final int[] values;
        private int cursor;

        ScriptedRandom(int... values) {
            super(0L);
            this.values = values.clone();
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("Invalid bound");
            }
            if (cursor >= values.length) {
                throw new AssertionError("No more scripted values");
            }
            int value = values[cursor++];
            if (value < 0 || value >= bound) {
                throw new AssertionError("Scripted value outside bound");
            }
            return value;
        }

        int calls() {
            return cursor;
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        // Hai PRNG cùng seed, cùng thứ tự gọi, tạo cùng chuỗi.
        Picker first = new Picker(new Random(2026L));
        Picker second = new Picker(new Random(2026L));
        int[] valuesA = new int[5];
        int[] valuesB = new int[5];

        for (int i = 0; i < 5; i++) {
            valuesA[i] = first.chooseIndex(10);
            valuesB[i] = second.chooseIndex(10);
        }

        boolean reproducible = Arrays.equals(valuesA, valuesB);
        require(reproducible, "Seeded sequences must match");
        System.out.println("same seed, same sequence: " + reproducible);

        // Fake ép giá trị đầu, cuối và hai nhánh phần trăm.
        ScriptedRandom scripted = new ScriptedRandom(0, 5, 0, 99);
        Picker predictable = new Picker(scripted);

        int firstIndex = predictable.chooseIndex(6);
        int lastIndex = predictable.chooseIndex(6);
        boolean zeroPercent = predictable.shouldSample(0);
        boolean fullPercent = predictable.shouldSample(100);

        require(firstIndex == 0, "Expected first index");
        require(lastIndex == 5, "Expected last index");
        require(!zeroPercent, "0 percent must be false");
        require(fullPercent, "100 percent must be true");
        require(scripted.calls() == 4, "Wrong number of random draws");

        System.out.println("scripted first index: " + firstIndex);
        System.out.println("scripted last index: " + lastIndex);
        System.out.println("sample at 0%: " + zeroPercent);
        System.out.println("sample at 100%: " + fullPercent);
        System.out.println("scripted calls: " + scripted.calls());

        // Kiểm tra bất biến theo khoảng giá trị, không hard-code chuỗi.
        Picker die = new Picker(new Random(7L));
        boolean inRange = true;
        for (int i = 0; i < 10_000; i++) {
            int rolled = die.rollDie();
            if (rolled < 1 || rolled > 6) {
                inRange = false;
                break;
            }
        }
        require(inRange, "A die roll must be between 1 and 6");
        System.out.println("die range verified: " + inRange);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RandomSourceTestingDemo.java
java RandomSourceTestingDemo
~~~

Stdout:

~~~text
same seed, same sequence: true
scripted first index: 0
scripted last index: 5
sample at 0%: false
sample at 100%: true
scripted calls: 4
die range verified: true
~~~

Đoạn fake được test bằng giá trị `0, 5, 0, 99` tương ứng các bound `6, 6, 100, 100`. Mỗi giá trị đều nằm trong khoảng hợp lệ. Test cũng kiểm tra chính xác số lần tiêu thụ RNG để phát hiện thay đổi bất ngờ trong cách code chọn số.

Bài học về cấu trúc test:

- **Seeded PRNG** kiểm tra khả năng tái lập một chuỗi qua cùng thuật toán và thứ tự gọi.
- **Scripted fake** kiểm tra nhánh cụ thể và các điểm biên một cách quyết định.
- **Property/bound test** kiểm tra điều kiện đúng với nhiều lần lấy mẫu, không yêu cầu một chuỗi ngẫu nhiên cụ thể.

Cả ba có vai trò khác nhau; không nên dùng một test seeded duy nhất để thay thế mọi kiểm thử nhánh.

## 8. Thiết kế unit test JUnit 5

Với dự án dùng JUnit Jupiter, có thể viết test theo hướng sau. Đây là **đoạn trích** giả định lớp `Picker` và `ScriptedRandom` của chương trình ở mục 7 là các kiểu mà test có thể truy cập, ví dụ đặt test trong cùng package hoặc chuyển chúng sang class phù hợp:

~~~java
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PickerTest {
    @Test
    void sameSeedReproducesDraws() {
        var a = new RandomSourceTestingDemo.Picker(new Random(42L));
        var b = new RandomSourceTestingDemo.Picker(new Random(42L));
        for (int i = 0; i < 100; i++) {
            assertEquals(a.chooseIndex(1_000), b.chooseIndex(1_000));
        }
    }

    @Test
    void fakeForcesBoundaryBranches() {
        var fake = new RandomSourceTestingDemo.ScriptedRandom(0, 5);
        var picker = new RandomSourceTestingDemo.Picker(fake);
        assertEquals(0, picker.chooseIndex(6));
        assertEquals(5, picker.chooseIndex(6));
        assertEquals(2, fake.calls());
    }

    @Test
    void invalidSizeRejectedBeforeDraw() {
        var fake = new RandomSourceTestingDemo.ScriptedRandom(0);
        var picker = new RandomSourceTestingDemo.Picker(fake);
        assertThrows(IllegalArgumentException.class,
                () -> picker.chooseIndex(0));
        assertEquals(0, fake.calls());
    }

    @Test
    void everyDieResultStaysInsideDomain() {
        var picker = new RandomSourceTestingDemo.Picker(
                new Random(1001L));
        for (int i = 0; i < 10_000; i++) {
            int rolled = picker.rollDie();
            assertTrue(rolled >= 1 && rolled <= 6);
        }
    }
}
~~~

Đoạn JUnit ở trên cần dependency JUnit Jupiter trong build và được chạy qua test runner (Gradle/Maven/IDE) của dự án. Nó **không** phải một chương trình chỉ dùng JDK để chạy bằng lệnh `java PickerTest`.

Nếu bản production chỉ định một PRNG/thuật toán khác, tạo constructor/factory phù hợp để test chủ động chọn chính implementation ấy. Không so sánh trực tiếp các chuỗi từ hai thuật toán khác nhau chỉ vì chúng được khởi tạo cùng seed.

## 9. Phân biệt PRNG kiểm thử với ngẫu nhiên bảo mật

`Random`, `SplittableRandom` và nhiều implementation của `RandomGenerator` là nguồn **giả ngẫu nhiên không dùng cho mật mã**. Seed có thể khiến chuỗi lặp lại và có thể bị dự đoán; đó là điều hữu ích cho test mô phỏng nhưng nguy hiểm với token đăng nhập, mật khẩu ngẫu nhiên, mã khôi phục, khóa hoặc giá trị cần bí mật.

Với mục đích bảo mật, sử dụng `java.security.SecureRandom` theo hướng dẫn bảo mật và thuật toán/provider phù hợp:

~~~java
import java.security.SecureRandom;

SecureRandom secure = new SecureRandom();
byte[] token = new byte[32];
secure.nextBytes(token);
~~~

**Không cố định seed của `SecureRandom` trong production** để mong tạo token lặp lại. `SecureRandom.setSeed(...)` không phải giao diện bảo đảm “đặt trạng thái thành đúng seed để phát lại cùng chuỗi” như giả định đơn giản với `Random`; chi tiết còn tùy provider và cách khởi tạo. Khi kiểm thử code liên quan bảo mật, tách logic xử lý token/nonce khỏi nguồn tạo byte: test có thể tiêm dữ liệu giả định **chỉ trong môi trường test** để kiểm tra format, validation, đường lỗi; production vẫn dùng nguồn bảo mật.

Không thay `SecureRandom` bằng `new Random(seed)` chỉ để test dễ hơn. Nếu hệ thống cần tính khó dự đoán, đó là một yêu cầu chức năng quan trọng cần giữ nguyên.

## 10. Seed, nguồn chia sẻ và nhiều luồng

Một nguồn được tạo từ seed có thể sinh chuỗi ổn định khi chạy tuần tự, nhưng **cùng seed không bảo đảm cùng kết quả nghiệp vụ nếu thứ tự các luồng tiêu thụ thay đổi**.

Ví dụ nhiều thread gọi cùng một `Random`:

- Một lần chạy: thread A rút giá trị thứ nhất, B rút thứ hai.
- Lần chạy khác: B rút giá trị thứ nhất, A rút thứ hai.

Ngay cả khi PRNG tạo cùng một chuỗi tổng thể, kết quả gắn với từng thread hoặc từng bản ghi có thể khác. Tranh chấp cũng ảnh hưởng hiệu năng. Các API như `ThreadLocalRandom.current()` cung cấp RNG theo thread, nhưng `ThreadLocalRandom.setSeed(long)` không hỗ trợ đặt seed để phát lại theo cách quen thuộc; nó ném `UnsupportedOperationException`.

Nếu bài toán mô phỏng song song phải tái lập ổn định, có thể:

1. Xác định một quy tắc gán seed theo **định danh công việc** được giữ ổn định, ví dụ cùng mã task và một master seed được lưu.
2. Tạo RNG riêng cho mỗi task/partition từ chính sách đó.
3. Đảm bảo mỗi task thực hiện cùng thứ tự rút số hoặc thiết kế cách rút không phụ thuộc thứ tự.
4. Nếu dùng `SplittableRandom.split()` hoặc generator hỗ trợ split, ghi nhận quy tắc phân chia; thay đổi thứ tự split có thể làm thay đổi chuỗi của từng task.

Không nên giả định nhiều thread chia sẻ một instance tùy ý đều thread-safe: hãy kiểm tra hợp đồng của implementation được chọn. Ngay cả khi implementation hỗ trợ gọi đồng thời, **tính tái lập kết quả theo nhiệm vụ** vẫn là vấn đề riêng.

## 11. Những kiểu test gây flaky hoặc che lỗi

### Sai 1: assert một kết quả cụ thể của RNG thật không seed

~~~java
int x = new Random().nextInt(6) + 1;
// Sai: assert x == 4. Điều này chỉ đúng ngẫu nhiên.
~~~

Thay bằng test bất biến của output hoặc fake ép nhánh.

### Sai 2: phụ thuộc một chuỗi cố định không cần thiết

Một test mã hóa sẵn 100 kết quả PRNG cho một chức năng chỉ yêu cầu nằm trong `[0, 6)` sẽ dễ hỏng khi đổi algorithm hoặc thêm một lần rút số. Nếu thực sự cần đối chiếu thuật toán PRNG, chuỗi chuẩn cố định có thể là *golden vectors* hữu ích. Còn với nghiệp vụ, ưu tiên assert kết quả và các nhánh nghiệp vụ.

### Sai 3: tạo RNG trong method không thể thay thế

~~~java
int pick() {
    return new Random().nextInt(10);
}
~~~

Không có cách đơn giản để tiêm fake vào method này. Chuyển thành dependency qua constructor, tham số method hoặc factory nếu cần kiểm soát vòng đời.

### Sai 4: reset seed giữa các lời gọi

~~~java
Random random = new Random(42L);
for (int i = 0; i < 5; i++) {
    random.setSeed(42L); // lặp lại trạng thái khởi đầu
    System.out.println(random.nextInt(10));
}
~~~

Đặt lại seed trước **mỗi lần rút** sẽ tái sử dụng trạng thái ban đầu và có thể tạo cùng giá trị lặp đi lặp lại, không giống với một chuỗi rút liên tiếp. Test tái lập thường đặt seed **một lần khi tạo nguồn cho từng test**.

### Sai 5: chia sẻ instance giữa các test

Nếu nhiều unit test cùng dùng một `static Random`, test này làm thay đổi trạng thái mà test khác sẽ đọc. Thứ tự chạy hoặc parallel execution có thể làm kết quả khác nhau. Mỗi test nên sở hữu một nguồn riêng (hoặc fixture riêng) và seed rõ ràng.

### Sai 6: dùng thống kê sơ sài để “chứng minh ngẫu nhiên”

Kiểm tra rằng 10 lần tung xúc xắc phải xuất hiện đủ cả 6 mặt là test flaky ngay cả với PRNG chính xác. Phép thử thống kê phải có giả thuyết, kích thước mẫu và ngưỡng sai số thích hợp; không dùng nó làm unit test bắt buộc nếu chưa phân tích xác suất sai báo. Kiểm thử logic biến đổi/bound với fake và kiểm thử thống kê đặc thù là hai nhiệm vụ khác nhau.

## 12. Mẫu lựa chọn chiến lược test

| Mục đích | Cách phù hợp | Điều cần khẳng định |
| --- | --- | --- |
| Tái hiện bug cụ thể | Seed, algorithm, fixture và thứ tự rút được ghi lại | Cùng bối cảnh tái hiện bug |
| Chọn nhánh đầu/cuối | Fake có kịch bản | Code xử lý đúng giá trị 0 và bound−1 |
| Đảm bảo output trong miền | Kiểm tra invariant/property | Không ra ngoài miền giá trị |
| Test phân phối | Thử nghiệm thống kê riêng với ngưỡng có cơ sở | Sai lệch nằm trong ngưỡng đã đặt |
| Test concurrency | RNG/seed cho từng task và đồng bộ kịch bản | Tính đúng đắn độc lập lịch chạy |
| Test luồng bảo mật | Tiêm dữ liệu giả ở ranh giới logic, production dùng `SecureRandom` | Format, xử lý lỗi và yêu cầu an toàn |

Không cần ép mọi thành phần của ứng dụng nhận cả interface `RandomGenerator` nếu component chỉ cần một quyết định rất hẹp. Có thể truyền một interface domain riêng như `IndexSource` hoặc `BooleanDecision` để unit test ngắn hơn và giảm sự ràng buộc vào chi tiết PRNG.

## 13. Tự kiểm tra

**Câu 1.** Hai instance `new Random(123L)` có chắc chắn cho cùng giá trị trong 20 lần gọi `nextInt(10)` khi được gọi theo cùng thứ tự không? Nếu một instance bị gọi thêm `nextLong()` trước lượt 10 thì sao?

**Câu 2.** Tại sao không nên kiểm tra `rollDie() == 6` bằng cách gọi 10 lần rồi hy vọng có giá trị 6? Hãy nêu một cách chủ động kiểm tra nhánh 6.

**Câu 3.** Một phương thức quyết định lấy mẫu bằng `random.nextInt(100) < 25`. Fake phải trả những giá trị nào để kiểm tra đúng cả hai phía của biên?

**Câu 4.** Vì sao không nên để nhiều test dùng chung một `static Random`, dù nó được khởi tạo với seed cố định?

**Câu 5.** Vì sao `SecureRandom` không được thay thế bằng `Random(42L)` trong chức năng tạo token production?

**Câu 6.** Nếu một lần chạy simulation song song và lần chạy kế tiếp có cùng master seed nhưng thứ tự phân chia công việc khác, kết quả của từng task có chắc chắn giống nhau không?

### Đáp án

1. Có, khi cùng `java.util.Random`, cùng seed và cùng chuỗi lời gọi. Nếu một nguồn tiêu thụ thêm giá trị, các lượt tiếp theo không còn tương ứng theo vị trí cũ.
2. Đó là kiểm thử dựa vào may rủi; có thể cung cấp fake cho `nextInt(6)` trả 5, rồi assert kết quả `rollDie()` là 6.
3. Fake trả 24 cho nhánh `true` và 25 cho nhánh `false`; cả hai đều nằm trong `[0,100)`.
4. Mỗi test tiêu thụ trạng thái chung nên thứ tự chạy và chạy song song ảnh hưởng dữ liệu ngẫu nhiên.
5. `Random` là PRNG thông thường và chuỗi có seed cố định có thể dự đoán, không đáp ứng mục đích bảo mật.
6. Không. Tính tái lập còn cần quy tắc gán nguồn/seed, thứ tự split, thứ tự tiêu thụ và ánh xạ công việc ổn định.

**Điểm cần nhớ:** seed cố định giúp **tái hiện một chuỗi**, còn dependency injection giúp **kiểm soát chính xác nhánh nghiệp vụ**. Kết hợp seeded test, scripted fake và kiểm tra bất biến để tạo test rõ ràng, ổn định; phân biệt hoàn toàn với yêu cầu sinh số ngẫu nhiên có tính bảo mật.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="numeric-testing-and-diagnostics-011">In mẫu bit, kiểu biểu thức và precision để điều tra kết quả bất ngờ</a>

<details>
<summary>Xem chi tiết</summary>

# In mẫu bit, kiểu biểu thức và precision để điều tra kết quả bất ngờ trong Java

## 1. Tại sao một biểu thức số cho kết quả “không đúng như nhìn thấy”?

Khi điều tra lỗi liên quan đến số trong Java, việc chỉ in ra giá trị cuối bằng `System.out.println(result)` thường không đủ. Cùng một kết quả bất ngờ có thể đến từ các nguyên nhân khác nhau:

- **Kiểu biểu thức sai với kỳ vọng:** `5 / 2` là phép chia giữa hai `int`, kết quả `2`, dù sau đó được gán sang `double`.
- **Tràn số trước khi mở rộng kiểu:** `int * int` được tính bằng `int`, có thể tràn rồi mới gán vào biến `long`.
- **Biểu diễn nhị phân gần đúng:** `0.1` và `0.2` không có biểu diễn hữu hạn chính xác trong IEEE 754 nhị phân, nên tổng `double` có thể in thành `0.30000000000000004`.
- **Chuyển kiểu làm mất bit có nghĩa:** `long` đủ chứa một số nguyên lớn nhưng `double` không chứa chính xác mọi số nguyên trong cùng miền giá trị.
- **Nhầm precision với scale:** `BigDecimal("1.2300")` có precision bằng `5`, scale bằng `4`; việc bỏ số 0 cuối hoặc làm tròn thay đổi cách biểu diễn.
- **Nhầm giá trị và mẫu bit:** `+0.0` và `-0.0` so sánh `==` là bằng nhau nhưng có mẫu bit khác nhau.

Để tìm nguyên nhân, hãy thu thập **giá trị + kiểu của biểu thức + mẫu bit/biểu diễn + độ chính xác và thời điểm chuyển đổi**. Đây là quy trình chẩn đoán, không phải yêu cầu mọi phép tính đều phải tự phân tích bit.

## 2. Đọc đúng kiểu biểu thức trước khi xem giá trị

Java xác định **kiểu của một biểu thức ở thời điểm biên dịch** theo quy tắc chuyển đổi và *numeric promotion*. Kiểu của biến nhận kết quả không nhất thiết thay đổi loại phép toán đã thực hiện trước đó.

Ví dụ:

```java
double a = 5 / 2;    // Phép chia int / int => int 2, sau đó thành double 2.0.
double b = 5 / 2.0;  // Có toán hạng double => chia double => 2.5.

int x = 50_000;
int y = 50_000;
long wrong = x * y;        // int * int bị tràn trước khi gán long.
long right = (long) x * y; // Ép x sang long TRƯỚC phép nhân.
```

Kết quả tương ứng:

| Biểu thức | Kiểu tính toán | Kết quả |
|---|---|---|
| `5 / 2` | `int` | `2` |
| `(double) (5 / 2)` | `int` khi chia; đổi thành `double` sau đó | `2.0` |
| `5 / 2.0` | `double` | `2.5` |
| `x * y` với cả hai `int = 50_000` | `int` | `-1794967296` (tràn) |
| `(long) x * y` | `long` | `2500000000` |
| `Integer.MAX_VALUE + 1` | `int` | `-2147483648` (tràn vòng) |

Các phép toán số nguyên nhị phân thông thường áp dụng **binary numeric promotion**: `byte`, `short` và `char` thường được nâng lên `int`; nếu có `long` thì nhiều phép sẽ tính bằng `long`; nếu có `float` hoặc `double`, phép toán tương ứng có kiểu đó theo thứ tự nâng kiểu. Đừng suy luận chỉ từ kiểu khai báo của toán hạng nhỏ.

Ví dụ `byte left = 10; byte right = 20;` nhưng `left + right` có kiểu biểu thức **`int`**, không phải `byte`. Và `var total = left + right;` suy diễn `total` là `int` vì `var` nhận kiểu biểu thức lúc biên dịch.

### Chẩn đoán kiểu bằng overload

Primitive như `int`/`double` không có `getClass()`. Nếu ép sang `Object` rồi gọi `getClass()`, quá trình **boxing** tạo `Integer`/`Double`, nghĩa là ta đang thấy lớp của wrapper, không trực tiếp thấy kiểu primitive ban đầu. Một cách dạy và kiểm tra hiệu quả là dùng overload:

```java
static String kind(int value)    { return "int"; }
static String kind(long value)   { return "long"; }
static String kind(double value) { return "double"; }

// kind(5 / 2) => "int"
// kind(5 / 2.0) => "double"
// kind(left + right), với hai byte => "int"
```

Đây là **đoạn minh họa đặt trong lớp**, chưa phải chương trình độc lập. Trình biên dịch chọn overload theo kiểu biểu thức khi gọi; vì vậy có thể dùng cách này trong một đoạn thử nhỏ để xác nhận dự đoán kiểu.

Cần nhớ: kiểu của biến khi gán, kiểu của biểu thức con và kiểu sau một phép ép có thể khác nhau. Hãy nhìn từ **biểu thức gây mất thông tin đầu tiên**, không chỉ biến được in ở cuối.

## 3. In mẫu bit của số nguyên: hai bù và độ rộng cố định

`int` là số nguyên có dấu 32-bit; `long` là số nguyên có dấu 64-bit. Java dùng biểu diễn **two's complement (bù hai)** cho số nguyên có dấu. Khi chẩn đoán overflow, in số ở hệ 16 hoặc hệ 2 cho phép xem trực tiếp những bit đang được lưu:

```java
int n = -1;
System.out.printf("%08X%n", n); // FFFFFFFF
System.out.println(Integer.toBinaryString(n));
// 11111111111111111111111111111111
```

`%08X` in `int` thành chuỗi hex có độ rộng tối thiểu 8 ký tự; `%016X` phù hợp để quan sát đầy đủ 16 chữ số hex của giá trị `long` 64-bit. Dấu `X` dùng chữ cái hex viết hoa, `%n` xuống dòng theo môi trường.

Với một giá trị `int`, `Integer.toHexString(n)` và `Integer.toBinaryString(n)` cũng hữu ích, nhưng có thể không tự chèn số 0 ở đầu cho số dương, nên cần định dạng nếu muốn so sánh từng vị trí bit.

Ví dụ `Integer.MAX_VALUE` là `0x7FFFFFFF`. Sau phép cộng `1` trên `int`, chuỗi bit trở thành `0x80000000`, được hiểu là `Integer.MIN_VALUE`. Đây là tràn theo miền 32-bit, **không phải lỗi in số**.

Để **phát hiện overflow thay vì âm thầm nhận kết quả vòng**, có thể dùng `Math.addExact(int,int)` hoặc `Math.multiplyExact(int,int)`. Các phương thức này ném `ArithmeticException` khi kết quả toán học không biểu diễn được trong kiểu mục tiêu. Nếu cần miền lớn hơn, dùng phép mở rộng sang `long` **trước khi tính** hoặc `BigInteger` tùy yêu cầu. Ép kiểu sau khi phép `int` đã tràn không khôi phục được những bit đã mất.

### Lưu ý với số nguyên không giới hạn độ lớn

`BigInteger` không bị cố định ở 32 hoặc 64 bit. `big.toString(2)` in chuỗi giá trị cơ số 2, nhưng với giá trị âm nó dùng dấu trừ phía trước; đây **không phải** bản dump hai bù với một độ rộng máy cố định. Khi cần quan sát hai bù 32-bit/64-bit, hãy làm trên `int`/`long` hoặc tự định nghĩa rõ độ rộng bit mong muốn.

`BigInteger.bitLength()` cho biết độ dài bit tối thiểu theo quy tắc API; nó khác với việc in toàn bộ chuỗi bit cố định của một từ máy.

## 4. Floating point: nhìn giá trị thập phân và mẫu bit IEEE 754 cùng lúc

`float` trong Java dùng định dạng IEEE 754 binary32 (32 bit); `double` dùng binary64 (64 bit). Mẫu bit gồm:

| Kiểu | Bit dấu | Bit exponent | Bit fraction lưu trữ | Precision nhị phân hữu hiệu của số chuẩn hóa |
|---|---:|---:|---:|---:|
| `float` | 1 | 8 | 23 | 24 bit |
| `double` | 1 | 11 | 52 | 53 bit |

Đối với số hữu hạn chuẩn hóa (*normal*), có một bit có nghĩa đứng đầu được ngầm định; vì vậy precision hữu hiệu lớn hơn số bit fraction lưu trữ. Giá trị subnormal có cơ chế khác và độ chính xác tương đối thấp hơn gần số 0.

Những phân số thập phân như `0.1` và `0.2` có khai triển vô hạn trong hệ nhị phân. JVM lưu giá trị nhị phân gần nhất có thể biểu diễn theo định dạng đã chọn. Khi cộng các số xấp xỉ và làm tròn về `double`, kết quả có thể không trùng đúng `0.3`.

Cách xem mẫu bit mà không làm thay đổi giá trị:

```java
double value = 0.1;
long bits = Double.doubleToRawLongBits(value);
System.out.printf("0.1 double bits: %016X%n", bits);

float small = 0.1f;
int floatBits = Float.floatToRawIntBits(small);
System.out.printf("0.1f float bits: %08X%n", floatBits);
```

`Double.doubleToRawLongBits` trả **mẫu 64 bit thực** của `double` dưới dạng `long`; `Float.floatToRawIntBits` trả **mẫu 32 bit thực** của `float` dưới dạng `int`. Dù giá trị `long` có thể mang dấu khi xem theo số nguyên, các bit vẫn giữ nguyên, nên định dạng hex giúp quan sát thuận tiện.

Hai phương thức không có chữ `Raw` — `Double.doubleToLongBits` và `Float.floatToIntBits` — **chuẩn hóa các dạng NaN** về mẫu NaN canonical; nếu đang điều tra payload bit của NaN, cần phân biệt rõ phương thức raw và non-raw. Đối với số hữu hạn thông thường, kết quả trùng nhau.

**Cần phân biệt:** mẫu bit IEEE 754 là thứ tự **vị trí bit trong giá trị**, không trực tiếp là thứ tự các **byte trong bộ nhớ**. Endianness khi tuần tự hóa byte là một vấn đề khác.

### Bit thập phân cho thấy điều gì?

Trong ví dụ cụ thể:

- `0.1` có mẫu `3FB999999999999A`.
- `0.2` có mẫu `3FC999999999999A`.
- `0.1 + 0.2` có mẫu `3FD3333333333334`, và `Double.toString` biểu diễn nó bằng `0.30000000000000004`.

Sự khác biệt trong mẫu bit giải thích vì sao `sum == 0.3` có thể trả `false` dù hai kết quả hiển thị gần như giống nhau. `Double.toString` lựa chọn chuỗi thập phân ngắn đủ để tái lập giá trị `double`, **không phải phép đo số chữ số đúng của giá trị toán học**.

Khi debug, hãy in đồng thời `Double.toString(value)`, mẫu bit raw, và nếu cần, hiệu số so với giá trị kỳ vọng. Đừng ép một chuỗi bit bất kỳ thành lời giải thích dựa trên chữ số thập phân mà chưa xét định dạng nhị phân.

### Hai số 0 có dấu

`+0.0` và `-0.0` so sánh bằng `==` là `true`, nhưng có bit dấu khác nhau:

```text
+0.0 bits: 0000000000000000
-0.0 bits: 8000000000000000
```

Vì vậy, việc chỉ kiểm tra `a == b` không đủ khi cần điều tra dấu của zero. Hai zero còn có thể dẫn đến kết quả khác nhau trong một số phép toán, ví dụ phép chia `1.0 / +0.0` cho dương vô cực và `1.0 / -0.0` cho âm vô cực.

Tương tự, `Double.NaN == Double.NaN` là `false`; dùng `Double.isNaN(value)` để nhận biết NaN và `Double.isFinite(value)` khi muốn yêu cầu số hữu hạn. Khi ghi log mẫu bit NaN, lưu ý phép toán trung gian có thể làm biến đổi payload NaN, và API canonical có thể làm mất sự khác biệt giữa các payload.

## 5. Precision, ULP và độ chính xác của số thực

Không nên mô tả chung rằng `double` “có 16 chữ số thập phân chính xác” rồi cho rằng tất cả số 16 chữ số đều biểu diễn được. Binary64 có **53 bit precision nhị phân**, thường tương ứng khoảng 15–17 chữ số thập phân có nghĩa tùy phép chuyển đổi và bài toán. Binary32 có **24 bit precision nhị phân**, thường vào khoảng 6–9 chữ số thập phân. Precision và khoảng cách giữa các số có thể biểu diễn **phụ thuộc độ lớn**.

**ULP** (*unit in the last place*) là đơn vị bước tại vị trí bit có nghĩa cuối trong vùng giá trị đang xét. Trong Java:

```java
System.out.println(Math.ulp(1.0));
// 2.220446049250313E-16
```

Ngay trên `1.0`, khoảng cách tới số `double` biểu diễn tiếp theo là \(2^{-52}\), bằng khoảng `2.22 × 10^-16`. Với số có độ lớn khác, ULP thay đổi. Vì vậy một epsilon tuyệt đối duy nhất không phù hợp cho mọi phép so sánh `double` trên các thang đo rất khác nhau.

Khi cần so sánh số thực theo sai số cho phép, định nghĩa dung sai từ **yêu cầu bài toán** rồi cân nhắc độ lớn và trường hợp biên, ví dụ:

```java
static boolean approximatelyEqual(double a, double b,
                                  double absTol, double relTol) {
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return a == b; // +Infinity/-Infinity tự so sánh theo ==; NaN false.
    }
    double delta = Math.abs(a - b);
    return delta <= Math.max(absTol,
            relTol * Math.max(Math.abs(a), Math.abs(b)));
}
```

Đây là **hàm minh họa**, dùng khi phạm vi dữ liệu và các tolerance đã được chọn theo yêu cầu; với số cực lớn, phép trừ `a-b` có thể tràn thành vô cực dù hai toán hạng hữu hạn. Một thuật toán so sánh dùng trong sản phẩm phải xử lý các biên đó, zero, NaN, infinity và nghiệp vụ cụ thể. Không chọn `1e-9` một cách máy móc cho mọi đại lượng.

### Số nguyên long rất lớn đi qua double

Một `double` có 53 bit precision nên biểu diễn được chính xác mọi số nguyên có trị tuyệt đối **không quá \(2^{53}\)**; nhưng không phải mọi số nguyên lớn hơn ngưỡng này đều biểu diễn chính xác được. Ví dụ:

```java
long exact = 9_007_199_254_740_993L;   // 2^53 + 1
long after = (long) (double) exact;      // 9007199254740992
```

Ở đây ép `long → double` làm tròn giá trị xuống số nguyên biểu diễn được gần nhất rồi ép về `long`, gây sai lệch 1. Việc in chính xác bit `double` hoặc so sánh lại với `long` sẽ bộc lộ chỗ mất thông tin. Nếu thuật toán đòi số nguyên chính xác, đừng đi qua `float`/`double` khi không có lý do rõ ràng.

### float được nâng lên double không tăng lại precision đã mất

`float f = 0.1f` lưu một xấp xỉ binary32. Khi ép `(double) f`, giá trị binary32 ấy được biểu diễn **chính xác dưới dạng double**, nhưng phần thông tin so với thập phân `0.1` đã mất khi gán vào `float` thì không được “phục hồi”. Vì vậy:

- `Float.toString(0.1f)` thường in `0.1`.
- `Double.toString((double) 0.1f)` in `0.10000000149011612`.
- Bit float của `0.1f` là `3DCCCCCD`.

Khi điều tra, in cả **kiểu nguồn, thời điểm chuyển kiểu và mẫu bit**, không chỉ in biến cuối cùng là `double`.

## 6. BigDecimal: decimal precision khác với bit precision

`BigDecimal` giữ một **unscaled value** nguyên và một `scale`; giá trị số học là:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

`precision()` ở đây là **số chữ số thập phân của unscaled value** (giá trị 0 có precision 1), **không phải 53 bit của double**. Ví dụ:

```java
BigDecimal number = new BigDecimal("1.2300");

System.out.println(number.precision()); // 5
System.out.println(number.scale());     // 4
```

`"1.2300"` có unscaled value `12300` và scale `4`; vì thế precision bằng `5`. Nếu dùng `stripTrailingZeros()`, kết quả `1.23` có precision `3`, scale `2`: giá trị số học vẫn bằng nhau nhưng cách biểu diễn/scale khác.

Khi cần tìm nguyên nhân kết quả `BigDecimal` bất ngờ, in ra:

- `toString()` hoặc `toPlainString()` để xem cách viết số hiện tại.
- `precision()` và `scale()` để biết giới hạn và vị trí dấu thập phân.
- `unscaledValue()` nếu cần xác minh giá trị nguyên phía sau biểu diễn.
- `MathContext` hoặc `RoundingMode` đã sử dụng trong phép tính: số chữ số có nghĩa cần giữ và quy tắc làm tròn.

Một `MathContext(3, RoundingMode.HALF_EVEN)` yêu cầu **3 chữ số có nghĩa**, khác với `setScale(3, ...)` yêu cầu **3 chữ số sau dấu thập phân**. Ví dụ `new BigDecimal("12.35").round(new MathContext(3, HALF_EVEN))` cho `12.4`. Đừng gọi cả hai khái niệm đơn giản là “3 chữ số precision” mà không nói rõ đây là digits có nghĩa hay chữ số sau dấu chấm.

### BigDecimal tạo từ double có thể kế thừa sai số nhị phân

`new BigDecimal(0.1)` biểu diễn chính xác **giá trị nhị phân thực tế** của double `0.1`, vốn khác số thập phân `0.1` toán học. `new BigDecimal("0.1")` biểu diễn chính xác thập phân `0.1`. Do đó:

```java
BigDecimal fromBinary = new BigDecimal(0.1);
BigDecimal fromDecimal = new BigDecimal("0.1");

System.out.println(fromBinary.equals(fromDecimal)); // false
System.out.println(BigDecimal.valueOf(0.1));        // 0.1
```

`BigDecimal.valueOf(double)` dùng biểu diễn thập phân qua `Double.toString` và thường phù hợp hơn constructor nhận trực tiếp `double` nếu đã có một giá trị double. Nhưng nó **không khôi phục dữ liệu chính xác** đã mất từ các phép toán double trước đó. Khi nguồn dữ liệu là chuỗi số thập phân có ý nghĩa nghiệp vụ, ưu tiên tạo `BigDecimal` trực tiếp từ chuỗi.

Kiểm tra ví dụ làm tròn:

```java
BigDecimal precise = new BigDecimal("1.005");
System.out.println(precise.setScale(2, RoundingMode.HALF_UP));
// 1.01
```

Nếu thay chuỗi bằng double literal rồi dùng constructor `new BigDecimal(1.005)`, giá trị nhị phân xấp xỉ thực tế có thể làm kết quả khác. Phải điều tra **nguồn số và điểm chuyển đổi trước khi nhìn vào chế độ làm tròn**.

## 7. Ví dụ Java 17+ độc lập: in kiểu, bit và precision

Bài thực hành sau cố ý chọn các giá trị dễ tái hiện để kết quả không phụ thuộc lịch đa luồng hay nguồn ngẫu nhiên. Lưu chương trình dưới tên **`NumericDiagnostics.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class NumericDiagnostics {
    static String kind(int x)    { return "int"; }
    static String kind(long x)   { return "long"; }
    static String kind(double x) { return "double"; }

    public static void main(String[] args) {
        byte left = 10;
        byte right = 20;
        System.out.println("byte + byte expression: " + kind(left + right));
        System.out.println("5 / 2 expression: " + kind(5 / 2));
        System.out.println("5 / 2 = " + (5 / 2));
        System.out.println("double assigned 5 / 2 = " + (double) (5 / 2));
        System.out.println("5 / 2.0 = " + (5 / 2.0));

        int a = 50_000;
        int b = 50_000;
        long wrong = a * b;
        long rightResult = (long) a * b;
        System.out.println("int multiplication assigned to long: " + wrong);
        System.out.println("cast before multiplication: " + rightResult);
        System.out.println("int overflow: " + (Integer.MAX_VALUE + 1));
        System.out.printf("minus one int bits: %08X%n", -1);
        try {
            Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            System.out.println("multiplyExact: overflow detected");
        }

        double p = 0.1;
        double q = 0.2;
        double sum = p + q;
        System.out.println("0.1 + 0.2 = " + sum);
        System.out.printf("0.1 double bits: %016X%n",
                Double.doubleToRawLongBits(p));
        System.out.printf("0.2 double bits: %016X%n",
                Double.doubleToRawLongBits(q));
        System.out.printf("sum double bits: %016X%n",
                Double.doubleToRawLongBits(sum));
        System.out.printf("+0.0 bits: %016X%n",
                Double.doubleToRawLongBits(+0.0));
        System.out.printf("-0.0 bits: %016X%n",
                Double.doubleToRawLongBits(-0.0));
        System.out.println("+0.0 == -0.0: " + (+0.0 == -0.0));
        System.out.println("Math.ulp(1.0): " + Math.ulp(1.0));

        float f = 0.1f;
        System.out.printf("0.1f float bits: %08X%n",
                Float.floatToRawIntBits(f));
        System.out.println("0.1f promoted to double: " + (double) f);

        long exact = 9_007_199_254_740_993L;
        System.out.println("long after double roundtrip: "
                + (long) (double) exact);

        BigDecimal value = new BigDecimal("1.2300");
        System.out.println("BigDecimal 1.2300 precision/scale: "
                + value.precision() + "/" + value.scale());
        System.out.println("stripTrailingZeros precision/scale: "
                + value.stripTrailingZeros().precision() + "/"
                + value.stripTrailingZeros().scale());
        System.out.println("new BigDecimal(0.1) equals decimal 0.1: "
                + new BigDecimal(0.1).equals(new BigDecimal("0.1")));
        System.out.println("BigDecimal.valueOf(0.1): "
                + BigDecimal.valueOf(0.1).toPlainString());
        System.out.println("HALF_UP 1.005 to scale 2: "
                + new BigDecimal("1.005")
                        .setScale(2, RoundingMode.HALF_UP));
        System.out.println("MathContext precision 3 for 12.35: "
                + new BigDecimal("12.35").round(
                        new MathContext(3, RoundingMode.HALF_EVEN)));
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac NumericDiagnostics.java
java NumericDiagnostics
```

Đầu ra:

```text
byte + byte expression: int
5 / 2 expression: int
5 / 2 = 2
double assigned 5 / 2 = 2.0
5 / 2.0 = 2.5
int multiplication assigned to long: -1794967296
cast before multiplication: 2500000000
int overflow: -2147483648
minus one int bits: FFFFFFFF
multiplyExact: overflow detected
0.1 + 0.2 = 0.30000000000000004
0.1 double bits: 3FB999999999999A
0.2 double bits: 3FC999999999999A
sum double bits: 3FD3333333333334
+0.0 bits: 0000000000000000
-0.0 bits: 8000000000000000
+0.0 == -0.0: true
Math.ulp(1.0): 2.220446049250313E-16
0.1f float bits: 3DCCCCCD
0.1f promoted to double: 0.10000000149011612
long after double roundtrip: 9007199254740992
BigDecimal 1.2300 precision/scale: 5/4
stripTrailingZeros precision/scale: 3/2
new BigDecimal(0.1) equals decimal 0.1: false
BigDecimal.valueOf(0.1): 0.1
HALF_UP 1.005 to scale 2: 1.01
MathContext precision 3 for 12.35: 12.4
```

Đọc kết quả theo nhóm:

1. **Kiểu:** `byte + byte` trở thành `int`; `5 / 2` là `int`. Khi cast sau phép chia, giá trị đã bị cắt phần dư nên chỉ thành `2.0`; dùng `2.0` ngay trong biểu thức mới cho `2.5`.
2. **Overflow:** `a*b` được tính theo `int` và tràn thành `-1794967296` trước khi gán vào `long`. Ép `a` trước phép nhân cho `2500000000`. `Math.multiplyExact` phát hiện thay vì âm thầm tràn.
3. **IEEE 754:** tổng `0.1+0.2` cho ra `0.30000000000000004` và mẫu bit khác với biểu diễn thập phân `0.3` mà người đọc kỳ vọng. Bit dấu của hai zero khác nhau dù toán tử `==` coi chúng bằng nhau.
4. **Precision:** `Math.ulp(1.0)` cung cấp bước gần 1; khi `float 0.1f` được nâng sang `double`, chữ số thập phân của giá trị thực lưu trong float lộ rõ.
5. **BigDecimal:** `precision/scale` của `1.2300` là `5/4`; bỏ trailing zeros đổi thành `3/2`. Tạo từ chuỗi thập phân và dùng đúng `MathContext`/`RoundingMode` giúp chẩn đoán khác biệt làm tròn.

## 8. Quy trình điều tra một kết quả số bất ngờ

Một checklist thực tế để đi từ triệu chứng đến nguyên nhân:

1. **Ghi lại đầu vào gốc trước khi chuyển kiểu.** Có phải chuỗi, số nguyên, JSON number, `float` hay `double`? Nếu nguồn là thập phân, ghi rõ dấu, chữ số và scale mong đợi.
2. **Viết rõ biểu thức con, kiểu của từng toán hạng và kiểu của phép toán.** Xem quy tắc chia nguyên, numeric promotion, `var`, overload, kiểu literal như `2` so với `2.0` hoặc `2L`.
3. **Kiểm tra điểm tràn trước phép gán.** Nếu là `int/long`, đối chiếu miền giá trị, in hex hoặc dùng `Math.addExact`/`Math.multiplyExact` khi cần phát hiện.
4. **Nếu là floating point, in giá trị cùng raw bits.** Với `float` dùng `Float.floatToRawIntBits`; với `double` dùng `Double.doubleToRawLongBits`. Kiểm tra NaN/infinity, signed zero và ULP gần vùng quan tâm.
5. **Nếu có chuyển kiểu, in cả giá trị trước và sau.** Đặc biệt chú ý `long → double`, `double → float`, các phép toán `int` được gán `long`, hoặc tạo `BigDecimal` từ `double`.
6. **Nếu là BigDecimal, in precision, scale, unscaledValue, MathContext và RoundingMode.** Phân biệt làm tròn theo số chữ số có nghĩa với làm tròn theo số chữ số sau dấu thập phân.
7. **Tạo ca kiểm thử nhỏ nhất tái hiện được.** Ưu tiên kết quả xác định, có assert/giá trị mong đợi rõ. Kiểm tra các giá trị biên: lớn nhất, nhỏ nhất, dấu âm, zero, nửa bước làm tròn và giá trị sát ranh giới representable.
8. **Quyết định cách sửa theo hợp đồng dữ liệu**, không chỉ đổi định dạng đầu ra để “trông đẹp hơn”. Ví dụ sửa bằng cách ép `long` trước phép nhân; dùng `BigDecimal(String)` cho dữ liệu thập phân chính xác; hoặc thiết kế tolerance phù hợp nếu bài toán cho phép sai số nhị phân.

### Biết lúc nào không nên in toàn bộ số

Với `BigInteger` hoặc `BigDecimal` cực lớn, `toString()` và đặc biệt `toPlainString()` có thể tạo chuỗi rất dài. Trong hệ thống thật, đặt giới hạn log/đầu ra; có thể ghi có kiểm soát `signum`, `bitLength`, `precision`, `scale` và một phần đầu/cuối chuỗi đã được kiểm tra kích thước. Tuy nhiên, không nên in cả payload không tin cậy chỉ để debug. Những phép toán kiểm tra cũng tiêu tốn tài nguyên nếu đối tượng ban đầu đã quá lớn; giới hạn từ lớp nhập vẫn cần thiết.

## 9. Những lỗi chẩn đoán phổ biến

### Chỉ tăng số chữ số khi printf rồi cho rằng đã sửa sai số

`System.out.printf("%.17f", value)` có thể hiện thêm chữ số giúp nhìn rõ giá trị binary64 đã được xấp xỉ. Nó **không thay đổi các bit** của `value` và không tạo lại độ chính xác đã mất. Hiển thị ít chữ số hơn cũng không sửa lỗi phép toán. Khi cần hiểu chính xác, in raw bits hoặc biểu diễn thập phân đủ để round-trip.

### Nghĩ biến đích quyết định kiểu của phép nhân/chia

`long product = x*y` khi `x` và `y` là `int` vẫn nhân dưới dạng `int`. `double ratio = 5/2` vẫn chia nguyên. Cần đưa kiểu đúng vào **trong biểu thức trước phép toán**, không cast kết quả sau khi đã mất thông tin.

### Hiểu nhầm signed zero và NaN

`+0.0 == -0.0` đúng nhưng bit khác; `NaN == NaN` sai. Đừng dùng riêng toán tử `==` để kiểm tra mọi tính chất floating point. Dùng `Double.isNaN`, `isFinite` và raw bits theo mục tiêu.

### Cho rằng BigDecimal luôn không có sai số

`BigDecimal` có thể biểu diễn thập phân chính xác, nhưng nếu dựng từ một giá trị `double` đã xấp xỉ, đối tượng sẽ phản ánh giá trị được cung cấp. Phép chia còn có thể cần `MathContext` hoặc `RoundingMode`. Độ chính xác của cấu trúc lưu trữ không tự sửa sai sót ở **nguồn giá trị và chính sách tính toán**.

### Dùng equals để so sánh mọi BigDecimal

`BigDecimal.equals` xét cả giá trị và scale; `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` trả `false`, trong khi `compareTo` trả `0` vì giá trị số học như nhau. Nếu điều tra lý do so sánh thất bại, hãy in cả `scale()` và phân biệt hợp đồng so sánh giá trị với so sánh biểu diễn.

### Dùng so sánh epsilon không theo miền dữ liệu

Một ngưỡng sai số tuyệt đối duy nhất có thể quá chặt với số rất lớn và quá lỏng với số rất nhỏ. Xem ULP và đơn vị của bài toán, xử lý riêng NaN/infinity, và xây phép kiểm thử sát biên. Không tự động thay tất cả `==` bằng một phép so sánh gần đúng mà chưa xác định yêu cầu.

## 10. Tự kiểm tra

**Câu 1:** Tại sao `long result = 50_000 * 50_000;` cho giá trị âm, nhưng `long result = 50_000L * 50_000;` cho `2500000000`? Vì hai literal `int` khiến phép nhân đầu chạy trong `int` và tràn; hậu tố `L` ở toán hạng đầu làm phép nhân bằng `long` ngay từ đầu.

**Câu 2:** Nếu `double result = 7 / 2;` thì `result` bằng bao nhiêu? **`3.0`**. Muốn kết quả `3.5`, dùng `7 / 2.0` hoặc `(double) 7 / 2`.

**Câu 3:** `0.1f` in ra `0.1` nhưng ép sang `double` in `0.10000000149011612`. Có phải ép kiểu làm hỏng số không? **Không**. Giá trị đã được làm tròn khi lưu trong `float`; ép sang `double` lưu lại chính xác giá trị float ấy và biểu diễn thập phân của nó có thể dài hơn.

**Câu 4:** `new BigDecimal("1.2300")` có precision 5, scale 4. Sau `stripTrailingZeros()` thành precision 3, scale 2; điều này có làm giá trị số học đổi không? **Không**, chỉ thay đổi biểu diễn và số 0 sau dấu chấm.

**Câu 5:** Một bộ so sánh kiểm tra `0.1 + 0.2 == 0.3` trả `false`. Nên kiểm tra gì? In mẫu bit và giá trị `double` thực của hai phía, xác định số sai lệch có được bài toán chấp nhận không, rồi dùng chính sách so sánh phù hợp; nếu cần thập phân chính xác như tiền tệ, cân nhắc `BigDecimal` dựng từ chuỗi.

**Ghi nhớ:** Khi kết quả số gây bất ngờ, hãy kiểm tra **kiểu biểu thức trước khi phép toán xảy ra**, **mẫu bit sau khi tính**, và **precision/scale cùng nguồn giá trị**. Chẩn đoán theo từng bước sẽ phân biệt tràn số nguyên, mất dữ liệu khi chuyển kiểu, sai số IEEE 754 và làm tròn thập phân, từ đó chọn cách sửa đúng nguyên nhân.


</details>

- [Quay lại đầu trang](#back-to-top)
