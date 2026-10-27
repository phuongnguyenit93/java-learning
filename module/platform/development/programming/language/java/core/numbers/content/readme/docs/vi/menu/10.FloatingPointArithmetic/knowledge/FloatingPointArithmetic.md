<a id="back-to-top"></a>

# Phép toán dấu phẩy động

## Menu
- [Cộng, trừ, nhân, chia và làm tròn về giá trị biểu diễn được](#floating-point-arithmetic-001)
- [Chia cho 0: phân biệt kết quả vô cực và NaN](#floating-point-arithmetic-002)
- [Overflow, underflow và giá trị subnormal](#floating-point-arithmetic-003)
- [Toán tử % khác Math.IEEEremainder](#floating-point-arithmetic-004)
- [Lan truyền NaN và các trường hợp đặc biệt](#floating-point-arithmetic-005)
- [Tác động của signed zero tới phép chia và hàm toán học](#floating-point-arithmetic-006)
- [Vì sao phép toán không bảo đảm tính kết hợp của số thực toán học](#floating-point-arithmetic-007)
- [Mất thông tin khi cộng số rất nhỏ vào số rất lớn](#floating-point-arithmetic-008)
- [Triệt tiêu chữ số khi trừ hai số gần bằng nhau](#floating-point-arithmetic-009)
- [Math.fma và sự khác biệt với nhân rồi cộng](#floating-point-arithmetic-010)
- [Always-strict từ Java 17 và vai trò lịch sử của strictfp](#floating-point-arithmetic-011)

## <a id="floating-point-arithmetic-001">Cộng, trừ, nhân, chia và làm tròn về giá trị biểu diễn được</a>

<details>
<summary>Xem chi tiết</summary>

# Cộng, trừ, nhân, chia và làm tròn về giá trị biểu diễn được trong Java

## 1. Điểm cốt lõi: kết quả toán học và kết quả lưu trong máy có thể khác nhau

Trong toán học, các phép `+`, `-`, `*` và `/` trên số thực tạo ra kết quả chính xác nếu các toán hạng được biết chính xác. Trong Java, các kiểu `float` và `double` chỉ lưu một **tập hữu hạn các giá trị dấu phẩy động nhị phân**. Khi một phép tính cho ra kết quả toán học không thuộc tập đó, Java phải **làm tròn về một giá trị biểu diễn được**.

Ví dụ kinh điển:

~~~java
double result = 0.1 + 0.2;
System.out.println(result);           // 0.30000000000000004
System.out.println(result == 0.3);    // false
~~~

Đây là đoạn minh họa, không phải lớp chạy độc lập. Chương trình không “cộng sai”: các literal thập phân `0.1`, `0.2` và `0.3` đã được chuyển thành các giá trị `double` gần nhất có thể biểu diễn. Kết quả cộng hai giá trị `double` đầu tiên sau làm tròn không trùng đúng bit với giá trị `double` mà literal `0.3` biểu diễn.

**Điều cần phân biệt:**

1. **Giá trị toán học**: ví dụ `1/10 + 2/10 = 3/10`.
2. **Giá trị được lưu của toán hạng**: biểu diễn nhị phân hữu hạn gần các phân số thập phân đó.
3. **Kết quả phép tính trước khi làm tròn**: kết quả số học chính xác của hai giá trị đã lưu, xét về mặt mô hình.
4. **Kết quả Java trả về**: được làm tròn vào miền giá trị biểu diễn được của kiểu kết quả, hoặc nhận giá trị đặc biệt như vô cực/NaN theo quy tắc IEEE 754.

Không nên coi `float` và `double` là những số thập phân có thể ghi và tính toán chính xác tùy ý.

## 2. Hai kiểu dấu phẩy động trong Java

| Đặc tính | `float` | `double` |
|---|---|---|
| Chuẩn biểu diễn | IEEE 754 binary32 | IEEE 754 binary64 |
| Kích thước | 32 bit | 64 bit |
| Độ chính xác phần định trị (*significand precision*) | 24 bit nhị phân | 53 bit nhị phân |
| Số chữ số thập phân có nghĩa gần đúng | 6–7 | 15–16 |
| Khoảng số hữu hạn bình thường (*normal*) | Rộng, nhưng kém `double` | Rộng hơn `float` |
| Giá trị đặc biệt | `+Infinity`, `-Infinity`, `NaN`, `+0.0`, `-0.0` | Tương tự |
| Literal mặc định | Cần hậu tố `f`/`F`, ví dụ `0.1f` | `0.1` mặc định là `double` |

Ngoài số *normal*, cả hai kiểu có thể lưu các số **subnormal** rất gần 0, với độ chính xác tương đối suy giảm. `Double.MIN_VALUE` là **số dương subnormal nhỏ nhất** lớn hơn 0, không phải “số `double` âm nhất”; trị số hữu hạn âm xa 0 nhất là `-Double.MAX_VALUE`. Với `float`, ý nghĩa của `Float.MIN_VALUE` cũng tương tự.

Khi cần ưu tiên độ chính xác và không có yêu cầu tiết kiệm bộ nhớ hay giao thức bắt buộc `float`, `double` thường là lựa chọn phù hợp hơn. Tuy nhiên, `double` cũng không làm cho `0.1` trở thành phân số nhị phân chính xác.

### Vì sao `0.1` không biểu diễn chính xác bằng nhị phân?

Giá trị `0.5 = 1/2` và `0.25 = 1/4` có biểu diễn nhị phân hữu hạn. Nhưng `0.1 = 1/10` có mẫu nhị phân lặp vô hạn; không thể lưu chính xác bằng số bit hữu hạn. Trình biên dịch chuyển literal thập phân thành giá trị `float` hoặc `double` theo quy tắc làm tròn.

Phép in `System.out.println(0.1)` cho `0.1` **không chứng minh lưu chính xác**: `Double.toString` chọn biểu diễn thập phân ngắn gọn mà khi phân tích lại sẽ khôi phục đúng giá trị `double` tương ứng. Chữ số thập phân khi hiển thị không phải toàn bộ nội dung bit của giá trị đã lưu.

## 3. Quy tắc làm tròn về giá trị biểu diễn được

Với các phép số học dấu phẩy động cơ bản ở Java 17+, kết quả hữu hạn được làm tròn theo chế độ **round to nearest, ties to even** của IEEE 754:

- Nếu kết quả chính xác nằm gần một giá trị biểu diễn được hơn giá trị khác, chọn giá trị gần nhất.
- Nếu kết quả nằm **đúng giữa** hai giá trị biểu diễn được kế tiếp, chọn giá trị có bit cuối của phần định trị chẵn (*ties to even*).
- Quy tắc này áp dụng cho kết quả phép toán số học dấu phẩy động, **không đồng nghĩa** với quy tắc của mọi hàm trong `Math`, chẳng hạn `Math.round`.

Đặc biệt, **mỗi phép toán cơ bản** cộng/trừ/nhân/chia tạo kết quả theo kiểu của biểu thức với độ chính xác và miền số mũ tương ứng. Trong Java 17+, ngữ nghĩa dấu phẩy động luôn nghiêm ngặt (*always-strict*), nên không còn chuyện dùng trung gian có số mũ mở rộng khác nhau vì từng vùng `strictfp`/không `strictfp` như các JDK rất cũ.

### ULP và khoảng cách giữa hai số kề nhau

**ULP** (*unit in the last place*) giúp diễn tả độ thô của lưới các số biểu diễn được ở một cỡ trị số nhất định. Java cung cấp `Math.ulp(double)` và `Math.ulp(float)`; `Math.nextUp(x)` và `Math.nextDown(x)` dịch tới số dấu phẩy động biểu diễn được kế cận theo hướng tương ứng.

Quanh `1.0` kiểu `double`, khoảng cách từ `1.0` đến số `double` kế tiếp lớn hơn là `2^-52`, xấp xỉ `2.220446049250313E-16`. Vì vậy, biểu thức:

~~~java
double halfGap = Math.ulp(1.0) / 2.0;
double rounded = 1.0 + halfGap;
~~~

có `rounded == 1.0`: tổng lý tưởng nằm đúng giữa `1.0` và `Math.nextUp(1.0)`, tie được làm tròn về `1.0` theo quy tắc chẵn.

Không có một “độ sai số tuyệt đối cố định” cho mọi giá trị `double`. Khi trị tuyệt đối tăng, khoảng cách giữa các số biểu diễn được nhìn chung tăng; với số rất nhỏ trong vùng subnormal, cơ chế lại có chi tiết riêng.

## 4. Cộng và trừ: làm tròn và mất chữ số có nghĩa

### 4.1. Cộng `+`

Với hai toán hạng dấu phẩy động, Java cộng những giá trị mà mỗi toán hạng đang lưu, rồi trả kết quả biểu diễn được của kiểu kết quả. Điều đó có thể làm xuất hiện chênh lệch nhỏ so với phép cộng thập phân tưởng tượng.

~~~java
double a = 0.1;
double b = 0.2;
double sum = a + b;      // 0.30000000000000004
~~~

Không có biểu diễn chính xác `0.1` hoặc `0.2` trong binary64, nên phép so sánh `sum == 0.3` là `false` ở đây.

**Cộng số rất lớn với số quá nhỏ** có thể “nuốt mất” số nhỏ khi độ chênh lệch vượt mức phân giải của `double`:

~~~java
double large = 1e16;
double small = 1.0;
double same = large + small; // vẫn biểu diễn thành 1.0E16
~~~

Chênh lệch 1 quá nhỏ so với khoảng cách ULP quanh `1e16`. Do đó, dù toán học `large + 1` khác `large`, kết quả Java có thể bằng đúng `large`.

### 4.2. Trừ `-` và *catastrophic cancellation*

Phép trừ hai số rất gần nhau có thể làm mất nhiều chữ số có nghĩa: nếu hai số đã được làm tròn ở bước trước, sai số của mỗi số có thể trở nên lớn so với hiệu nhỏ. Hiện tượng này được gọi là **triệt tiêu chữ số có nghĩa** (*catastrophic cancellation*).

Ví dụ, không phải biểu thức đại số nào cũng giữ nguyên kết quả khi các phép trung gian được làm tròn:

~~~java
double original = 1e16;
double modified = original + 1.0;
double difference = modified - original; // 0.0, không phải 1.0
~~~

Giá trị `modified` đã bằng `original` sau phép cộng, nên phép trừ không thể “tìm lại” thông tin 1.0 đã mất. Thay đổi sang `double` từ `float` có thể giảm sai số nhưng không loại bỏ tính chất này.

### 4.3. Phép cộng không kết hợp tuyệt đối

Trong đại số thực, `(a + b) + c = a + (b + c)`. Với dấu phẩy động, mỗi phép `+` có thể làm tròn, nên hai cách đặt ngoặc có thể khác:

~~~java
double left = (1e16 + -1e16) + 1.0;    // 1.0
double right = 1e16 + (-1e16 + 1.0);  // 0.0
~~~

Phép cộng trong ngoặc `-1e16 + 1.0` mất số 1 nhỏ; còn `1e16 + -1e16` cho 0. Đây là lý do thứ tự cộng nhiều phần tử ảnh hưởng độ chính xác; không được tự do biến đổi thứ tự phép tính dấu phẩy động như khi biến đổi biểu thức đại số trên số thực chính xác.

## 5. Nhân `*`: kết quả có thể tràn ra vô cực hoặc hụt về 0

Phép nhân hai giá trị hữu hạn có thể cho tích toán học lớn hơn mọi số hữu hạn biểu diễn được trong kiểu kết quả: **overflow dấu phẩy động**. Trong trường hợp đó, kết quả có thể là `+Infinity` hoặc `-Infinity`, tùy dấu. Khác phép nhân số nguyên Java, phép nhân dấu phẩy động **không cuốn vòng về giá trị âm có mẫu bit bù hai**.

~~~java
double overflow = 1e308 * 10.0; // Infinity
~~~

Một phép nhân (hoặc chia) cũng có thể tạo số khác 0 nhưng quá nhỏ để biểu diễn, dẫn đến **underflow**, kết quả subnormal hoặc bị làm tròn thành `+0.0`/`-0.0`.

~~~java
double underflow = Double.MIN_VALUE / 2.0; // 0.0
~~~

Ở đây `Double.MIN_VALUE` chính là đơn vị số dương subnormal nhỏ nhất; chia đôi không tạo số dương `double` khác 0 nào biểu diễn được, nên kết quả được làm tròn về `0.0`.

Không phải mọi phép nhân số rất lớn đều cho Infinity, cũng không phải mọi số rất nhỏ đều tự động về 0: các số subnormal vẫn có thể lưu những giá trị hữu hạn khác 0 gần sát 0.

## 6. Chia `/`: làm tròn, số chia 0, Infinity và NaN

Với toán hạng dấu phẩy động, `/` tính thương và làm tròn về biểu diễn được. Phép chia không hết trong nhị phân thường tạo kết quả gần đúng, tương tự `1.0 / 10.0`.

Điểm khác biệt lớn với **chia nguyên**: phép chia dấu phẩy động cho 0 **không ném `ArithmeticException`**. Nó tuân theo quy tắc IEEE 754:

| Phép tính `double` | Kết quả | Giải thích |
|---|---|---|
| `1.0 / 0.0` | `Infinity` | Giá trị dương chia cho +0 |
| `1.0 / -0.0` | `-Infinity` | Giá trị dương chia cho -0 |
| `0.0 / 0.0` | `NaN` | Dạng không xác định |
| `1.0 / Double.POSITIVE_INFINITY` | `0.0` | Thương tiến gần 0 và có dấu dương |
| `Double.MIN_VALUE / 2.0` | `0.0` | Underflow |
| `-7.0 / 2.0` | `-3.5` | Có phần thập phân vì chia dấu phẩy động |

Trong Java:

~~~java
int integerQuotient = 7 / 2;    // 3: chia nguyên, bỏ phần lẻ
double floatingQuotient = 7.0 / 2; // 3.5
double castFirst = (double) 7 / 2; // 3.5
double castTooLate = (double) (7 / 2); // 3.0
~~~

Đây là đoạn minh họa. Khi cả hai toán hạng là số nguyên, `7 / 2` đã hoàn thành phép chia nguyên **trước khi cast kết quả** sang `double`. Muốn có phép chia dấu phẩy động phải có ít nhất một toán hạng `float` hoặc `double` (hoặc ép kiểu trước khi tính).

`NaN` không bằng chính nó theo `==`. Hãy dùng `Double.isNaN(result)`, `Float.isNaN(result)` để nhận diện, và `Double.isFinite`/`Float.isFinite` khi chỉ chấp nhận số hữu hạn.

## 7. Số 0 có dấu và phép toán trên giá trị đặc biệt

Cả `float` lẫn `double` phân biệt về mặt biểu diễn `+0.0` và `-0.0`, dù phép so sánh số học `0.0 == -0.0` cho `true`.

- `1.0 / 0.0` là `Infinity`.
- `1.0 / -0.0` là `-Infinity`.
- Phủ định `-0.0` bằng toán tử một ngôi đổi dấu thành `+0.0`.
- Cộng/trừ số 0 có dấu tuân theo quy tắc của dấu phẩy động; không giả định mọi phép tính trên 0 giữ nguyên dấu bit.
- `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` cho `NaN`; `0.0 * Double.POSITIVE_INFINITY` cũng cho `NaN`.

Nếu có `NaN` trong phép toán số học thông thường, kết quả thường tiếp tục là `NaN`. Phần lớn so sánh thứ tự với `NaN` (`<`, `<=`, `>`, `>=`) và so sánh bằng `==` đều là `false`; `NaN != NaN` là `true`.

**Đừng dùng điều kiện `result != Double.NaN` để kiểm tra hợp lệ:** điều kiện này luôn `true` khi phép so sánh số thông thường chứa NaN. Dùng `Double.isNaN` hoặc `Double.isFinite` tùy yêu cầu.

## 8. Kiểu biểu thức ảnh hưởng độ chính xác trước khi gán

`float` và `double` tham gia các quy tắc **binary numeric promotion** của Java:

- Nếu một trong hai toán hạng là `double`, phép tính thực hiện ở `double`.
- Nếu không có `double` mà có `float`, phép tính thực hiện ở `float`.
- Với hai toán hạng `int` hoặc `long`, phép tính **không tự trở thành dấu phẩy động** chỉ vì biến nhận kết quả là `double`.

Ví dụ:

~~~java
float f = 0.1f;
double d = 0.2;
double mixed = f + d;      // kiểu double, nhưng f đã được làm tròn dưới kiểu float
double simple = 1f + 1d;   // 2.0

int a = 7;
int b = 2;
double wrong = a / b;         // 3.0: chia int trước rồi chuyển thành double
double correct = (double) a / b; // 3.5
~~~

Đây là đoạn minh họa. Nâng `float` lên `double` không tái tạo phần chữ số chính xác đã mất trong `float` trước đó. Thứ tự chuyển kiểu quan trọng giống như thứ tự tính toán.

Với các biểu thức liên quan literal, `0.1f` là literal `float`, còn `0.1` là literal `double`. Không nên viết `float x = 0.1;` vì gán literal `double` sang biến `float` như vậy thường bị lỗi kiểu; dùng `0.1f` hoặc cast rõ ràng.

## 9. Ví dụ Java 17+: thấy phép làm tròn và kiểu kết quả

Tạo tệp `FloatingArithmeticDemo.java`:

~~~java
public class FloatingArithmeticDemo {
    public static void main(String[] args) {
        double sum = 0.1 + 0.2;

        System.out.println("sum=" + sum);
        System.out.println("equal03=" + (sum == 0.3));
        System.out.println("sumHex=" + Double.toHexString(sum));
        System.out.println("literal03Hex="
                + Double.toHexString(0.3));

        double large = 1e16;
        System.out.println("lostOne=" + ((large + 1.0) - large));
        System.out.println("assocLeft=" + ((large + -large) + 1.0));
        System.out.println("assocRight=" + (large + (-large + 1.0)));

        System.out.println("intDivision=" + (7 / 2));
        System.out.println("doubleDivision=" + (7.0 / 2));
        System.out.println("castTooLate=" + (double) (7 / 2));
        System.out.println("floatSum=" + (0.1f + 0.2f));
    }
}
~~~

Chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac FloatingArithmeticDemo.java
java FloatingArithmeticDemo
~~~

Đầu ra:

~~~text
sum=0.30000000000000004
equal03=false
sumHex=0x1.3333333333334p-2
literal03Hex=0x1.3333333333333p-2
lostOne=0.0
assocLeft=1.0
assocRight=0.0
intDivision=3
doubleDivision=3.5
castTooLate=3.0
floatSum=0.3
~~~

`Double.toHexString` thể hiện biểu diễn theo lũy thừa nhị phân của giá trị `double`. Hai dòng `sumHex` và `literal03Hex` khác nhau **1 ULP tại cỡ đó**: tổng sau làm tròn có bit cuối khác với literal `0.3`.

Dòng `floatSum=0.3` chỉ là dạng chuỗi thập phân Java chọn khi in giá trị `float`; nó không chứng minh `0.1f` và `0.2f` đã là các phân số thập phân chính xác.

## 10. Ví dụ Java 17+: ULP, làm tròn tại nửa khoảng và giới hạn số nguyên chính xác

Tạo tệp `FloatingRoundingDemo.java`:

~~~java
public class FloatingRoundingDemo {
    public static void main(String[] args) {
        double one = 1.0;
        double gap = Math.ulp(one);
        double halfGap = gap / 2.0;

        System.out.println("ulpAtOne=" + gap);
        System.out.println("halfUlpAdded=" + (one + halfGap));
        System.out.println("nextUp=" + Math.nextUp(one));

        float largeFloat = 16_777_216f; // 2^24
        System.out.println("floatBefore=" + largeFloat);
        System.out.println("floatPlusOne=" + (largeFloat + 1.0f));
        System.out.println("floatPlusTwo=" + (largeFloat + 2.0f));

        System.out.println("doubleRoundTie=" + Math.rint(2.5));
        System.out.println("mathRoundPositive=" + Math.round(2.5));
        System.out.println("mathRoundNegative=" + Math.round(-2.5));
    }
}
~~~

~~~bash
javac FloatingRoundingDemo.java
java FloatingRoundingDemo
~~~

Đầu ra:

~~~text
ulpAtOne=2.220446049250313E-16
halfUlpAdded=1.0
nextUp=1.0000000000000002
floatBefore=1.6777216E7
floatPlusOne=1.6777216E7
floatPlusTwo=1.6777218E7
doubleRoundTie=2.0
mathRoundPositive=3
mathRoundNegative=-2
~~~

Ở `float`, `2^24` là 16.777.216; khoảng cách đến số `float` lớn hơn kế tiếp là 2. Thêm 1 nằm đúng giữa hai giá trị biểu diễn được, nên làm tròn về số có bit cuối chẵn: **vẫn là 16.777.216**. Thêm 2 đạt số có thể biểu diễn chính xác: **16.777.218**.

Ba dòng cuối **không mô tả phép làm tròn của mỗi toán tử số học**, mà cho thấy các API làm tròn tường minh trong `Math` có quy tắc riêng:

- `Math.rint(2.5)` trả `double 2.0` theo *nearest, ties to even*.
- `Math.round(2.5)` trả `long 3` (overload nhận `double`).
- `Math.round(-2.5)` trả `long -2`.

Với các giá trị hữu hạn thông thường, `Math.round(x)` làm tròn tới số nguyên gần nhất, trường hợp chính giữa chọn hướng **dương vô cùng**. Đừng đồng nhất nó với “round-to-nearest ties-to-even” của `+`, `-`, `*` và `/`.

## 11. Ví dụ Java 17+: overflow, underflow, Infinity và NaN

Tạo tệp `FloatingSpecialValuesDemo.java`:

~~~java
public class FloatingSpecialValuesDemo {
    public static void main(String[] args) {
        double overflow = 1e308 * 10.0;
        double underflow = Double.MIN_VALUE / 2.0;
        double positiveInfinity = 1.0 / 0.0;
        double negativeInfinity = 1.0 / -0.0;
        double notANumber = 0.0 / 0.0;

        System.out.println("overflow=" + overflow);
        System.out.println("underflow=" + underflow);
        System.out.println("positiveInfinity=" + positiveInfinity);
        System.out.println("negativeInfinity=" + negativeInfinity);
        System.out.println("notANumber=" + notANumber);
        System.out.println("finiteOverflow=" + Double.isFinite(overflow));
        System.out.println("isNaN=" + Double.isNaN(notANumber));
        System.out.println("nanEqualsSelf=" + (notANumber == notANumber));
        System.out.println("zerosEqual=" + (0.0 == -0.0));
    }
}
~~~

~~~bash
javac FloatingSpecialValuesDemo.java
java FloatingSpecialValuesDemo
~~~

Đầu ra:

~~~text
overflow=Infinity
underflow=0.0
positiveInfinity=Infinity
negativeInfinity=-Infinity
notANumber=NaN
finiteOverflow=false
isNaN=true
nanEqualsSelf=false
zerosEqual=true
~~~

Những kết quả trên là **giá trị dấu phẩy động đặc biệt theo đặc tả**, không phải ngoại lệ Java. Nếu chương trình không được phép có NaN hoặc vô cực, cần **kiểm tra** sau khi tính hoặc kiểm tra đầu vào và điều kiện miền giá trị trước phép toán.

Lưu ý: khi xử lý dữ liệu cần lưu lâu dài hoặc chuyển qua JSON, giá trị `NaN`/`Infinity` có thể không được định dạng trao đổi dữ liệu chấp nhận. Đừng mặc định mọi serializer đều xử lý chúng theo một cách.

## 12. Các phép toán cơ bản có thể làm tròn *ở mỗi bước*

Hãy xét:

~~~java
double a = 1e16;
double b = -1e16;
double c = 1.0;

double first = (a + b) + c; // 1.0
double second = a + (b + c); // 0.0
~~~

Cả hai biểu thức giống nhau nếu xem `a`, `b`, `c` là số thực với đại số chính xác, nhưng **khác trong IEEE 754 binary64** vì kết quả của `b + c` không lưu được phần chênh lệch `1.0`. Vì vậy:

- Không được giả định phép cộng dấu phẩy động có tính kết hợp.
- Phép nhân và chia có thể làm tròn; gộp/đổi thứ tự phép tính có thể thay đổi sai số và thời điểm overflow/underflow.
- Khi tính tổng nhiều số chênh lệch bậc lớn, cân nhắc thuật toán cộng có bù (*compensated summation*, ví dụ Kahan) hoặc thay đổi thứ tự cộng khi bài toán và yêu cầu tái lập cho phép.
- Khi cần phép nhân-cộng với **một lần làm tròn** thay vì nhân rồi cộng làm tròn riêng, Java cung cấp `Math.fma(a, b, c)`: tính gần đúng phép toán `a * b + c` theo ngữ nghĩa hợp nhất (*fused multiply-add*). Nó hữu ích cho một số thuật toán số, nhưng không làm cho mọi dữ liệu nhị phân trở nên chính xác tuyệt đối.

`Math.fma` không phải toán tử `*` rồi `+` đơn thuần: mục đích của API là làm tròn **một lần sau kết quả nhân-cộng kết hợp**, thay vì hai lần cho hai phép riêng lẻ.

## 13. Phân biệt làm tròn biểu diễn với làm tròn chủ động

Có ít nhất **ba việc** thường bị gọi chung là “làm tròn”:

| Mục tiêu | Cơ chế | Ví dụ |
|---|---|---|
| Lưu kết quả phép cộng/trừ/nhân/chia vào `float`/`double` | Làm tròn IEEE 754 về giá trị **biểu diễn được** | `1.0 + ulp(1.0)/2` có thể vẫn là `1.0` |
| Làm tròn số dấu phẩy động về giá trị nguyên hoặc giá trị nguyên dưới dạng `double` | `Math.round`, `Math.rint`, `Math.floor`, `Math.ceil` | `Math.round(2.5) == 3L` |
| Làm tròn số thập phân đến số chữ số theo quy tắc nghiệp vụ | `BigDecimal.setScale` cùng `RoundingMode` | Số tiền hai chữ số thập phân |

**Không nhầm một số được in ra có hai chữ số thập phân với kết quả được lưu chính xác đến hai chữ số.** `System.out.printf("%.2f", value)` chỉ định dạng hiển thị; biến `value` vẫn là số `double` cũ.

Trong một số ứng dụng (giá, thuế, thanh toán), cần số thập phân chính xác và quy tắc làm tròn theo nghiệp vụ. `BigDecimal` thích hợp hơn với điều kiện **khởi tạo từ chuỗi thập phân hoặc `BigDecimal.valueOf` phù hợp với nguồn dữ liệu**, chọn `RoundingMode` rõ ràng, và không chuyển kết quả qua `double` ở các bước cần chính xác.

### Ví dụ tiền tệ: dùng thập phân từ nguồn

Tạo tệp `DecimalVsBinaryDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalVsBinaryDemo {
    public static void main(String[] args) {
        double binarySum = 0.1 + 0.2;
        BigDecimal decimalSum =
                new BigDecimal("0.1").add(new BigDecimal("0.2"));

        System.out.println("binarySum=" + binarySum);
        System.out.println("decimalSum=" + decimalSum);
        System.out.println("money="
                + decimalSum.setScale(2, RoundingMode.HALF_UP));
        System.out.println("formattedBinary="
                + String.format(java.util.Locale.ROOT,
                        "%.2f", binarySum));
    }
}
~~~

~~~bash
javac DecimalVsBinaryDemo.java
java DecimalVsBinaryDemo
~~~

Đầu ra:

~~~text
binarySum=0.30000000000000004
decimalSum=0.3
money=0.30
formattedBinary=0.30
~~~

Lệnh định dạng dòng cuối **không biến** `binarySum` thành số thập phân chính xác; đó chỉ là chuỗi để trình bày. Dòng `decimalSum` là kết quả phép cộng số thập phân chính xác của hai `BigDecimal` được tạo từ chuỗi. Tránh `new BigDecimal(0.1)` nếu mục tiêu là chính xác theo giá trị thập phân người dùng gõ vào, vì constructor nhận `double` bảo tồn chính xác giá trị nhị phân gần đúng của `0.1`.

## 14. So sánh kết quả gần đúng và kiểm tra điều kiện biên

Với các phép tính `float`/`double`, đừng dùng `==` để hỏi “hai kết quả toán học gần nhau hay không” khi chúng đi qua những chuỗi phép tính khác nhau và sai số làm tròn có thể tích lũy.

Ví dụ, với các giá trị hữu hạn nhỏ gần `0.3`:

~~~java
double observed = 0.1 + 0.2;
double expected = 0.3;
boolean close = Math.abs(observed - expected) <= 1e-12;
~~~

`close` bằng `true` ở đây. Đây là **đoạn minh họa**, không phải giải pháp tổng quát cho mọi phép so sánh số. Một ngưỡng tuyệt đối `1e-12` có thể quá rộng với đại lượng cực nhỏ hoặc quá hẹp với đại lượng lớn.

Khi thiết kế phép so sánh gần bằng, nên xem xét:

1. **Đơn vị và mức sai số chấp nhận được của nghiệp vụ** (độ, mét, giây, tiền...).
2. Sai số **tuyệt đối** và đôi khi cả sai số **tương đối**, đặc biệt khi giá trị thay đổi nhiều bậc độ lớn.
3. Trường hợp `NaN`, `Infinity` và số 0 có dấu — phép trừ và `Math.abs` không giải quyết mọi trường hợp.
4. Cần kết quả xác định theo bit hay chấp nhận sai số: thuật toán, thứ tự phép tính và dữ liệu đầu vào phải được quy định phù hợp.

Trong mã cần kiểm tra tính hữu hạn trước khi sử dụng số làm chỉ số, kích thước, tỷ lệ hoặc giá trị lưu trữ:

~~~java
if (!Double.isFinite(observed)) {
    throw new IllegalArgumentException("Kết quả không hữu hạn");
}
~~~

Đây là đoạn mã minh họa. `Double.isFinite` trả `false` cho `NaN` và `+/-Infinity`, nhưng **không** đánh giá tính chính xác hoặc tính hợp lệ nghiệp vụ của một giá trị hữu hạn.

## 15. Những lỗi thường gặp

| Nhận định | Giải thích chính xác |
|---|---|
| “`0.1 + 0.2` phải bằng đúng `0.3` vì toàn số thập phân.” | Các literal `double` là xấp xỉ nhị phân; kết quả sau làm tròn có thể khác bit. |
| “`double` không có sai số vì dùng 64 bit.” | `double` có 53 bit độ chính xác phần định trị; nhiều phân số không biểu diễn được. |
| “Chỉ phép chia mới làm tròn.” | `+`, `-`, `*`, `/` đều có thể làm tròn. |
| “Đổi thứ tự phép cộng không thay đổi kết quả.” | Làm tròn ở từng bước khiến phép cộng dấu phẩy động không kết hợp tuyệt đối. |
| “`double huge + 1` luôn lớn hơn `huge`.” | Khi `huge` đủ lớn, 1 có thể nhỏ hơn nửa khoảng ULP. |
| “Phép chia `double` cho 0 ném `ArithmeticException`.” | Thường trả Infinity hoặc NaN; chia nguyên cho 0 mới ném lỗi. |
| “Overflow số thực cuốn vòng như `int`.” | Kết quả có thể là Infinity, không phải wraparound nguyên. |
| “`Double.MIN_VALUE` là số âm bé nhất.” | Nó là **số dương subnormal nhỏ nhất** lớn hơn 0. |
| “`Math.round` dùng cùng quy tắc tie-even của toán tử `+`.” | `Math.round` có quy tắc làm tròn riêng; `Math.rint` dùng ties-to-even cho kết quả `double`. |
| “In `%.2f` thì số đã thành số tiền hai chữ số chính xác.” | Đó chỉ là định dạng; muốn nghiệp vụ thập phân chính xác, dùng `BigDecimal` đúng cách. |
| “Gán phép `int / int` sang biến `double` cho ra thương thực.” | `int / int` tính chia nguyên trước; phải chuyển toán hạng trước phép chia. |

## 16. Checklist khi giải bài toán số thực

1. **Dữ liệu thuộc miền nào?** Số nguyên đếm, đại lượng đo gần đúng hay số thập phân chính xác?
2. **Chọn `float` hay `double`:** cân nhắc độ chính xác, phạm vi và yêu cầu giao thức.
3. **Xác định kiểu trước mỗi phép tính:** `int / int` không tự thành chia thực; `float` ghép với `double` có thể mang sai số từ toán hạng `float`.
4. **Xem xét sai số khi phép toán diễn ra:** nhất là cộng số chênh lệch bậc lớn, trừ hai số gần nhau và phép nhân/chia gần giới hạn.
5. **Kiểm tra giá trị đặc biệt:** dùng `Double.isFinite`, `Double.isNaN` khi bài toán không chấp nhận Infinity/NaN.
6. **Tách làm tròn biểu diễn khỏi làm tròn nghiệp vụ:** `Math.round`, `Math.rint`, `BigDecimal.setScale` là các công cụ khác nhau.
7. **Kiểm thử biên:** `0.1 + 0.2`, nửa ULP, `Double.MIN_VALUE / 2`, `Double.MAX_VALUE * 2`, chia cho `+0.0`/`-0.0` và các phép tính dễ mất chữ số.
8. **Giữ khả năng tái lập:** ghi rõ trình tự tính và đơn vị; tránh biến đổi biểu thức theo các đẳng thức đại số thực mà không đánh giá ảnh hưởng tới làm tròn.

## 17. Bài tập tự kiểm tra

1. Vì sao `0.1 + 0.2 == 0.3` là `false` trong `double`?
2. Tại sao `1e16 + 1.0` vẫn có thể bằng `1e16`?
3. `Math.ulp(1.0)` biểu thị đại lượng gì, và vì sao cộng nửa khoảng này với `1.0` có thể không đổi kết quả?
4. `(a + b) + c` có luôn bằng `a + (b + c)` đối với `double` không? Cho ví dụ.
5. `1.0 / 0.0`, `1.0 / -0.0` và `0.0 / 0.0` có kết quả gì? Có ném `ArithmeticException` không?
6. `double x = 7 / 2;` cho giá trị nào? Cách sửa để nhận `3.5`?
7. Vì sao phép nhân `1e308 * 10.0` có thể trả Infinity, trong khi `Double.MIN_VALUE / 2.0` trả `0.0`?
8. `Math.round(2.5)` khác `Math.rint(2.5)` thế nào?
9. Vì sao `String.format("%.2f", 0.1 + 0.2)` không giải quyết độ chính xác của dữ liệu tiền?
10. Nếu cần cộng hai giá trị thập phân `0.1` và `0.2` chính xác theo nghiệp vụ, nên chọn cách biểu diễn nào?

**Gợi ý đáp án:** (1) Các giá trị nhị phân được làm tròn khác nhau, tổng và literal `0.3` không cùng bit. (2) ULP gần `1e16` lớn hơn mức thay đổi 1. (3) Khoảng đơn vị tại vị trí cuối; tie-even có thể chọn `1.0`. (4) Không, ví dụ `(1e16 + -1e16) + 1` khác `1e16 + (-1e16 + 1)`. (5) `Infinity`, `-Infinity` và `NaN`; không ném lỗi chia thực. (6) `3.0`, dùng `7.0 / 2` hoặc ép trước phép chia. (7) Overflow và underflow theo miền giá trị biểu diễn được. (8) `3L` so với `2.0`. (9) Chỉ làm tròn chuỗi hiển thị, không thay dữ liệu nguồn. (10) `BigDecimal` khởi tạo từ chuỗi thập phân phù hợp.

**Ghi nhớ:** Java làm tròn kết quả `float`/`double` của **cộng, trừ, nhân, chia** về giá trị nhị phân biểu diễn được, theo quy tắc IEEE 754. Sai số có thể xuất hiện **từ lúc ghi literal** và tiếp tục sau **mỗi phép tính**. Đừng nhầm cơ chế đó với làm tròn về số nguyên, làm tròn để trình bày, hoặc số học thập phân chính xác theo nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-002">Chia cho 0: phân biệt kết quả vô cực và NaN</a>

<details>
<summary>Xem chi tiết</summary>

# Chia cho 0 trong Java: phân biệt vô cực và NaN

Trong Java, câu hỏi “chia cho 0 sẽ xảy ra chuyện gì?” **không có cùng một đáp án cho mọi kiểu số**. Với phép chia **số nguyên** (`int`, `long` và các kiểu nguyên nhỏ được nâng lên `int`), số chia bằng 0 dẫn đến `ArithmeticException` khi phép toán được thực hiện. Với phép chia **dấu phẩy động** (`float` hoặc `double`), Java sử dụng các giá trị đặc biệt IEEE 754: `Infinity`, `-Infinity` và `NaN`.

Quy tắc cần nhớ:

- **Số hữu hạn khác 0 / ±0.0 → vô cực có dấu (`Infinity` hoặc `-Infinity`).**
- **±0.0 / ±0.0 → `NaN` (*Not a Number*).**
- **Phép chia nguyên với số chia 0 → `ArithmeticException`**, không sinh `Infinity` hoặc `NaN`.
- Vô cực và NaN còn có thể xuất hiện **không phải do chia cho 0**, chẳng hạn số thực tràn phạm vi hoặc các phép toán vô định khác.

Bài học dưới đây dùng Java 17+ và phân biệt **kết quả tính toán**, **cách Java hiển thị kết quả** và **cách kiểm tra/xử lý giá trị bất thường**.

## 1. Vì sao Java có kết quả vô cực và NaN?

`float` và `double` là kiểu số dấu phẩy động nhị phân theo IEEE 754. Ngoài các giá trị hữu hạn, chúng chứa các mã biểu diễn riêng cho:

| Giá trị | Ý nghĩa thực tế | Java |
| --- | --- | --- |
| Dương vô cực | Kết quả vượt phía dương của miền số hữu hạn hoặc phép toán được định nghĩa cho ra dương vô cực | `Double.POSITIVE_INFINITY`, `Float.POSITIVE_INFINITY` |
| Âm vô cực | Kết quả vượt phía âm hoặc phép toán được định nghĩa cho ra âm vô cực | `Double.NEGATIVE_INFINITY`, `Float.NEGATIVE_INFINITY` |
| NaN | Kết quả số thực không xác định theo các quy tắc IEEE 754, ví dụ `0.0 / 0.0` | `Double.NaN`, `Float.NaN` |
| Dương zero | Số 0 có bit dấu bằng 0 | `0.0`, `0.0f` |
| Âm zero | Số 0 có bit dấu bằng 1 | `-0.0`, `-0.0f` |

Java mặc định in `Double.POSITIVE_INFINITY` là `Infinity`, in `Double.NEGATIVE_INFINITY` là `-Infinity` và in `Double.NaN` là `NaN`. Những chữ này là cách biểu diễn văn bản của **giá trị kiểu số thực**, không phải chuỗi được phép trộn tùy ý với phép toán nguyên.

```java
double plusInfinity = Double.POSITIVE_INFINITY;
double minusInfinity = Double.NEGATIVE_INFINITY;
double notANumber = Double.NaN;
System.out.println(plusInfinity);  // Infinity
System.out.println(minusInfinity); // -Infinity
System.out.println(notANumber);    // NaN
```

**NaN không có nghĩa “giá trị là số 0”, “lỗi bằng 0” hay “chưa khởi tạo”.** Nó là một giá trị đặc biệt mang ý nghĩa rằng phép tính không có kết quả số thực thông thường theo quy tắc của kiểu.

## 2. Số hữu hạn khác 0 chia cho zero: vô cực có dấu

Đối với `double`, khi tử số **hữu hạn và khác 0**, còn mẫu số là `+0.0` hoặc `-0.0`, kết quả là vô cực; dấu của kết quả phụ thuộc vào dấu của hai toán hạng.

| Biểu thức | Kết quả | Diễn giải |
| --- | --- | --- |
| `1.0 / 0.0` | `Infinity` | Dương / zero dương |
| `-1.0 / 0.0` | `-Infinity` | Âm / zero dương |
| `1.0 / -0.0` | `-Infinity` | Dương / zero âm |
| `-1.0 / -0.0` | `Infinity` | Âm / zero âm |
| `10.0 / 0.0` | `Infinity` | Tử số dương hữu hạn |
| `0.0 / 0.0` | `NaN` | Tử số không khác 0 — quy tắc khác |

Ví dụ:

```java
System.out.println(1.0 / 0.0);   // Infinity
System.out.println(-1.0 / 0.0);  // -Infinity
System.out.println(1.0 / -0.0);  // -Infinity
System.out.println(-1.0 / -0.0); // Infinity
```

**Không có exception chỉ vì mẫu số số thực bằng 0.** Bởi vậy `try/catch (ArithmeticException ...)` không phải cách thông thường để phát hiện phép chia `double` cho 0. Phải kiểm tra miền đầu vào hoặc kết quả sau phép tính.

### `float` cũng có hành vi tương ứng

```java
float result = 5.0f / 0.0f;       // Float.POSITIVE_INFINITY
float indeterminate = 0.0f / 0.0f; // Float.NaN
```

`5.0f` và `0.0f` có kiểu `float`, còn `5.0` và `0.0` có kiểu `double`. Hậu tố `f`/`F` giúp ghi rõ kiểu literal `float`.

## 3. Zero chia cho zero: vì sao lại là NaN?

Khi **cả tử số và mẫu số đều bằng zero**, phép chia dấu phẩy động cho `NaN` bất kể zero mang dấu nào:

```java
double a = 0.0 / 0.0;   // NaN
double b = -0.0 / 0.0;  // NaN
double c = 0.0 / -0.0;  // NaN
double d = -0.0 / -0.0; // NaN
```

**Vô cực và NaN là hai trạng thái khác nhau.** Nếu tử số hữu hạn khác 0 và mẫu bằng 0, dấu của tỷ số xác định được theo quy tắc số thực nên nhận vô cực có dấu. Nếu tử số cũng bằng 0, không có một tỷ số xác định chung cho biểu thức `0/0`, vì vậy quy tắc số thực cho `NaN`.

Đừng diễn giải `NaN` như một số quá lớn: nó **không phải vô cực** và không có thứ tự số học thông thường. Hơn nữa, các giá trị `NaN` có thể phát sinh từ nhiều phép toán không xác định khác, không riêng `0.0 / 0.0`.

## 4. Số nguyên chia cho 0: `ArithmeticException`

Với `int` và `long`, Java **không có giá trị vô cực hoặc NaN**. Nếu mẫu số bằng 0, phép chia nguyên tại thời điểm chạy ném `ArithmeticException`.

```java
int dividend = 10;
int divisor = 0;

try {
    int quotient = dividend / divisor;
    System.out.println(quotient);
} catch (ArithmeticException ex) {
    System.out.println("integer division by zero");
}
```

Output:

```text
integer division by zero
```

**Kiểu của biểu thức tại thời điểm chia** mới quyết định hành vi, không phải kiểu biến dùng để lưu kết quả:

```java
int a = 1;
int b = 0;

// double wrong = a / b; // thực thi sẽ ném ArithmeticException trước khi gán
double floating = (double) a / b; // Infinity: toán hạng bên trái đã là double
double alsoFloating = a / (double) b; // Infinity
double literalFloating = 1.0 / 0;     // Infinity: int 0 được nâng thành double
```

Dòng `wrong` được chú thích là **minh họa mã ném exception khi thực thi**, không phải lỗi do gán kiểu. Khi hai toán hạng đều là `int`, `a / b` là phép chia `int`. Nếu chuyển một toán hạng sang `double` **trước** phép chia, Java thực hiện phép chia dấu phẩy động.

Với hai `byte`/`short`, phép chia số học thường nâng lên `int`; `byte b = 0; int x = 1 / b;` cũng ném `ArithmeticException`. Với kiểu `long`, `1L / 0L` ném cùng loại exception.

**Lưu ý:** `ArithmeticException` có thể có những nguyên nhân số học nguyên khác. Không nên dựa hoàn toàn vào thông điệp chi tiết của exception để suy luận chính xác nghiệp vụ.

## 5. Dấu âm của zero quan trọng như thế nào?

IEEE 754 và Java phân biệt `+0.0` với `-0.0` ở **biểu diễn bit**. Tuy nhiên, phép so sánh số học `==` cho chúng bằng nhau:

```java
System.out.println(0.0 == -0.0); // true
```

Các phép toán có thể nhận ra dấu khác nhau:

```java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(1.0 / -0.0); // -Infinity
System.out.println(-1.0 / Double.POSITIVE_INFINITY); // -0.0
```

Có thể xem bit gốc:

```java
long positiveZeroBits = Double.doubleToRawLongBits(0.0);  // 0
long negativeZeroBits = Double.doubleToRawLongBits(-0.0); // Long.MIN_VALUE
```

Không nên viết một kiểm tra `denominator == 0.0` rồi nghĩ rằng nó phân biệt được dấu zero; phép `==` coi hai giá trị zero bằng nhau.

### Khi số thực hữu hạn chia cho vô cực

Hành vi đối xứng cũng đáng chú ý:

| Biểu thức | Kết quả |
| --- | --- |
| `1.0 / Double.POSITIVE_INFINITY` | `0.0` |
| `-1.0 / Double.POSITIVE_INFINITY` | `-0.0` |
| `1.0 / Double.NEGATIVE_INFINITY` | `-0.0` |
| `-1.0 / Double.NEGATIVE_INFINITY` | `0.0` |

Đây là các phép chia số thực hợp lệ. Zero có dấu còn có thể xuất hiện do **underflow** khi một kết quả quá nhỏ về trị tuyệt đối so với khả năng biểu diễn.

## 6. Nếu có vô cực hoặc NaN trong phép toán tiếp theo

Một kết quả vô cực hoặc NaN có thể tiếp tục tham gia các biểu thức:

| Biểu thức | Kết quả |
| --- | --- |
| `Double.POSITIVE_INFINITY + 100.0` | `Infinity` |
| `Double.POSITIVE_INFINITY + Double.NEGATIVE_INFINITY` | `NaN` |
| `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` | `NaN` |
| `0.0 * Double.POSITIVE_INFINITY` | `NaN` |
| `Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY` | `NaN` |
| `Double.NaN + 1.0` | `NaN` |
| `1.0 / Double.POSITIVE_INFINITY` | `0.0` |
| `1.0 % 0.0` | `NaN` |

**NaN thường lan truyền** qua các phép toán số thực. Ví dụ `(0.0 / 0.0) + 10.0` vẫn ra `NaN`, chứ không trở thành số 10.

```java
double infinity = 1.0 / 0.0;
double nan = 0.0 / 0.0;

System.out.println(infinity + 1.0); // Infinity
System.out.println(nan + 1.0);      // NaN
```

### Phép dư `%` cũng có quy tắc riêng

Với `double`/`float`, `1.0 % 0.0` cho `NaN`. Với `int`/`long`, `1 % 0` ném `ArithmeticException` khi thực thi.

```java
System.out.println(1.0 % 0.0); // NaN
```

Toán tử `%` của Java dùng phép lấy dư theo ngữ nghĩa của ngôn ngữ, không đồng nhất với phép toán `Math.IEEEremainder` trong mọi trường hợp. Đừng tự cho rằng kết quả hoặc cách xử lý phần dư giống mọi ngôn ngữ lập trình khác.

## 7. Vô cực không chỉ đến từ phép chia cho 0

Một số dấu phẩy động hữu hạn có thể vượt phạm vi biểu diễn khi nhân hoặc thực hiện các phép toán khác:

```java
double overflow = Double.MAX_VALUE * 2.0;
System.out.println(overflow); // Infinity
```

`Double.MAX_VALUE` là giá trị `double` hữu hạn dương lớn nhất. Khi kết quả vượt khả năng biểu diễn hữu hạn, phép toán có thể cho `Infinity` ngay cả khi không hề chia cho 0.

Ở phía ngược lại, **underflow** có thể làm một kết quả khác 0 về mặt toán học trở thành zero có dấu:

```java
double underflow = Double.MIN_VALUE / 2.0;
System.out.println(underflow); // 0.0
```

`Double.MIN_VALUE` trong Java là **số `double` dương nhỏ nhất lớn hơn 0** (*positive subnormal*), **không** phải số hữu hạn âm lớn nhất về trị tuyệt đối. Do đó chia tiếp cho 2 sẽ làm tròn về `+0.0`.

Một số phép toán không xác định khác cũng sinh NaN:

```java
double invalidRoot = Math.sqrt(-1.0); // NaN
double undefined = Double.POSITIVE_INFINITY
        - Double.POSITIVE_INFINITY; // NaN
```

**Không thể kết luận “mẫu số bằng 0” chỉ bằng cách thấy `Infinity`**; cũng không thể kết luận “tử và mẫu đều bằng 0” chỉ bằng cách thấy `NaN`. Muốn tìm nguyên nhân, hãy kiểm tra đầu vào và các bước trung gian.

## 8. Cách phát hiện `Infinity`, `-Infinity` và `NaN`

Java cung cấp API chuyên dụng:

| API | `Infinity` | `-Infinity` | `NaN` | Số hữu hạn |
| --- | --- | --- | --- | --- |
| `Double.isInfinite(x)` | `true` | `true` | `false` | `false` |
| `Double.isNaN(x)` | `false` | `false` | `true` | `false` |
| `Double.isFinite(x)` | `false` | `false` | `false` | `true` |

`Float` có các API tương ứng: `Float.isInfinite`, `Float.isNaN` và `Float.isFinite`.

```java
double positiveInfinity = 1.0 / 0.0;
double undefined = 0.0 / 0.0;

System.out.println(Double.isInfinite(positiveInfinity)); // true
System.out.println(Double.isNaN(undefined));              // true
System.out.println(Double.isFinite(positiveInfinity));    // false
System.out.println(Double.isFinite(42.0));                // true
```

### Vì sao không nên kiểm tra NaN bằng `==`?

Theo ngữ nghĩa số học dấu phẩy động, NaN không bằng bất kỳ giá trị nào, **kể cả chính nó**:

```java
double x = Double.NaN;
System.out.println(x == Double.NaN); // false
System.out.println(x == x);          // false
System.out.println(Double.isNaN(x)); // true
```

Các phép so sánh thứ tự với NaN như `x < 1.0`, `x >= 1.0` đều `false`. Riêng `x != x` là `true` khi `x` là NaN. Trong code thực tế, `Double.isNaN(x)` diễn đạt ý định rõ hơn mẹo `x != x`.

`Double.compare` có quy tắc sắp xếp NaN và zero có dấu để phục vụ các tình huống cần thứ tự nhất quán. Nó không đồng nghĩa với `==` về mọi trường hợp đặc biệt: chẳng hạn `0.0 == -0.0` là `true` nhưng `Double.compare(0.0, -0.0)` không bằng 0.

## 9. Nên kiểm tra đầu vào hay kiểm tra đầu ra?

Có hai chính sách khác nhau, tùy hợp đồng nghiệp vụ.

### Chính sách A: Không cho mẫu số bằng zero

Nếu API của bạn đòi hỏi **mẫu khác 0**, hãy kiểm tra mẫu trước khi chia:

```java
static double divideFiniteInputs(double numerator, double denominator) {
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
```

Đây là **method minh họa phải đặt trong một class**, không phải chương trình Java độc lập. Mẫu `denominator == 0.0` chủ động từ chối cả `+0.0` lẫn `-0.0`. Kiểm tra `Double.isFinite` sau phép chia còn bắt trường hợp **overflow sang vô cực dù mẫu không bằng 0**, và các kết quả không hữu hạn khác.

Phần xử lý lỗi là **chính sách của API**, không phải exception Java tự động ném cho phép chia số thực. `IllegalArgumentException` và `ArithmeticException` ở ví dụ trên do **chính người lập trình viết ra**.

### Chính sách B: Chấp nhận giá trị IEEE 754

Trong một số tính toán khoa học, việc tạo `Infinity` hoặc `NaN` có thể được giữ lại có chủ đích và được xử lý ở tầng phân tích kết quả:

```java
double ratio = numerator / denominator;
if (Double.isNaN(ratio)) {
    // Xử lý phép toán không xác định
} else if (Double.isInfinite(ratio)) {
    // Xử lý vô cực theo mô hình của ứng dụng
} else {
    // Xử lý kết quả hữu hạn
}
```

Đoạn này là **ví dụ nằm trong method có các biến đã khai báo**. Đừng mặc định biến mọi `NaN` thành 0: `0` có thể là giá trị hợp lệ, khiến lỗi dữ liệu bị che khuất.

Đối với tính tiền, chia số lượng hoặc phân bổ tài nguyên, thông thường cần thiết kế quy tắc làm tròn, phạm vi và kiểm tra dữ liệu rõ ràng; `double`/`float` không phải công cụ lưu tiền thập phân chính xác. `BigDecimal` có quy tắc phép chia và exception riêng, **không** áp dụng trực tiếp bảng IEEE 754 của `double`.

## 10. Một số bẫy khi chuyển kiểu và truyền dữ liệu

### Cast vô cực hoặc NaN sang số nguyên

Narrowing từ `double` sang `int` không ném exception trong trường hợp `Infinity` hay `NaN`; Java áp dụng quy tắc chuyển đổi được định nghĩa:

```java
int positive = (int) Double.POSITIVE_INFINITY; // Integer.MAX_VALUE
int negative = (int) Double.NEGATIVE_INFINITY; // Integer.MIN_VALUE
int nan = (int) Double.NaN;                    // 0
```

Điều này đặc biệt nguy hiểm nếu bạn cố “chữa” kết quả phép chia bằng cast. Mọi giá trị `NaN` đều trở thành `0` khi cast trực tiếp sang `int`; vô cực được chặn tại biên `int`. Việc cast **không chứng minh dữ liệu đầu vào hợp lệ**.

### Chuỗi đầu ra không tự kể nguyên nhân

`String.valueOf(Double.POSITIVE_INFINITY)` tạo chuỗi `"Infinity"`, `String.valueOf(Double.NaN)` tạo `"NaN"`. Giá trị gốc vẫn là số thực. Java có thể parse các chuỗi hợp lệ này bằng `Double.parseDouble("Infinity")` hay `Double.parseDouble("NaN")`; tuy nhiên giao thức bên ngoài như JSON tiêu chuẩn **không mặc nhiên cho phép** NaN/vô cực dưới dạng số JSON. Khi xuất dữ liệu cần xác định rõ cách biểu diễn, ví dụ từ chối hoặc ánh xạ sang cấu trúc lỗi riêng theo contract.

### Không nhầm NaN với `null` hoặc chuỗi rỗng

`Double.NaN` là một giá trị primitive `double`. `null` chỉ áp dụng cho tham chiếu như `Double` wrapper. Khi `Double` wrapper chứa `null` bị *unbox* trong phép chia, có thể xảy ra `NullPointerException` **trước cả khi tính toán**. Đây là tình huống hoàn toàn khác `0.0 / 0.0` sinh `NaN`.

## 11. Chương trình Java 17+ kiểm chứng đầy đủ

Lưu thành tệp **`FloatingDivideByZeroDemo.java`**:

```java
public class FloatingDivideByZeroDemo {
    public static void main(String[] args) {
        System.out.println("positiveOverZero=" + (1.0 / 0.0));
        System.out.println("negativeOverZero=" + (-1.0 / 0.0));
        System.out.println("positiveOverNegativeZero=" + (1.0 / -0.0));
        System.out.println("negativeOverNegativeZero=" + (-1.0 / -0.0));
        System.out.println("zeroOverZero=" + (0.0 / 0.0));
        System.out.println("negativeZeroOverZero=" + (-0.0 / 0.0));

        System.out.println("floatOverZero=" + (5.0f / 0.0f));
        System.out.println("floatZeroOverZero=" + (0.0f / 0.0f));
        System.out.println("zeroEquality=" + (0.0 == -0.0));
        System.out.println("negativeZeroBits=" +
                Long.toHexString(Double.doubleToRawLongBits(-0.0)));

        System.out.println("finiteOverPositiveInfinity=" +
                (1.0 / Double.POSITIVE_INFINITY));
        System.out.println("negativeOverPositiveInfinity=" +
                (-1.0 / Double.POSITIVE_INFINITY));
        System.out.println("infinityOverInfinity=" +
                (Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY));
        System.out.println("zeroTimesInfinity=" +
                (0.0 * Double.POSITIVE_INFINITY));
        System.out.println("remainderByZero=" + (1.0 % 0.0));

        double infinity = 1.0 / 0.0;
        double nan = 0.0 / 0.0;
        System.out.println("isInfinite=" + Double.isInfinite(infinity));
        System.out.println("isNaN=" + Double.isNaN(nan));
        System.out.println("isFiniteInfinity=" + Double.isFinite(infinity));
        System.out.println("isFiniteOne=" + Double.isFinite(1.0));
        System.out.println("nanEqualsItself=" + (nan == nan));
        System.out.println("nanLessThanOne=" + (nan < 1.0));

        int numerator = 1;
        int denominator = 0;
        try {
            System.out.println(numerator / denominator);
        } catch (ArithmeticException ex) {
            System.out.println("integerDivideByZero=ArithmeticException");
        }
        System.out.println("promotedDivision=" +
                ((double) numerator / denominator));

        System.out.println("overflowWithoutZero=" + (Double.MAX_VALUE * 2.0));
        System.out.println("underflowToZero=" + (Double.MIN_VALUE / 2.0));
        System.out.println("castInfinity=" + (int) Double.POSITIVE_INFINITY);
        System.out.println("castNaN=" + (int) Double.NaN);
    }
}
```

Tại thư mục chứa file, chạy bằng JDK 17 trở lên:

```bash
javac FloatingDivideByZeroDemo.java
java FloatingDivideByZeroDemo
```

Output chính xác:

```text
positiveOverZero=Infinity
negativeOverZero=-Infinity
positiveOverNegativeZero=-Infinity
negativeOverNegativeZero=Infinity
zeroOverZero=NaN
negativeZeroOverZero=NaN
floatOverZero=Infinity
floatZeroOverZero=NaN
zeroEquality=true
negativeZeroBits=8000000000000000
finiteOverPositiveInfinity=0.0
negativeOverPositiveInfinity=-0.0
infinityOverInfinity=NaN
zeroTimesInfinity=NaN
remainderByZero=NaN
isInfinite=true
isNaN=true
isFiniteInfinity=false
isFiniteOne=true
nanEqualsItself=false
nanLessThanOne=false
integerDivideByZero=ArithmeticException
promotedDivision=Infinity
overflowWithoutZero=Infinity
underflowToZero=0.0
castInfinity=2147483647
castNaN=0
```

Bốn dòng đầu chứng minh dấu của tử và mẫu quyết định dấu vô cực. Các dòng `zeroOverZero` và `infinityOverInfinity` chứng minh NaN không đồng nghĩa với “mẫu số bằng 0”. `integerDivideByZero` và `promotedDivision` cho thấy chỉ một bước chuyển kiểu **trước khi chia** đã đổi hoàn toàn hành vi.

## 12. Lỗi phổ biến và checklist giải bài

| Nhận định | Đúng hay sai? | Giải thích |
| --- | --- | --- |
| “Mọi phép chia cho 0 trong Java đều ném exception.” | Sai | `float`/`double` có `Infinity` hoặc `NaN`; phép chia nguyên mới ném `ArithmeticException`. |
| “`1.0 / 0.0` trả `NaN`.” | Sai | Tử hữu hạn khác zero nên trả `Infinity`. |
| “`0.0 / 0.0` trả `Infinity`.” | Sai | Đây là `NaN`. |
| “`-0.0` giống hoàn toàn `+0.0`.” | Sai | `==` coi bằng nhau nhưng dấu zero ảnh hưởng phép chia và bit lưu. |
| “`double x = 1 / 0` sẽ cho `Infinity`.” | Sai | Hai toán hạng `int`; chia nguyên ném exception trước khi gán. |
| “Dùng `Double.isNaN(x)` để phát hiện vô cực.” | Sai | Dùng `Double.isInfinite(x)` cho vô cực; `isNaN` chỉ cho NaN. |
| “`Double.NaN == Double.NaN` đúng.” | Sai | Theo `==` số thực, NaN không bằng chính nó. |
| “Thấy `Infinity` tức là chắc chắn đã chia cho 0.” | Sai | Overflow số thực cũng tạo vô cực. |
| “Thấy `NaN` tức là chắc chắn tử và mẫu đều bằng 0.” | Sai | `Infinity/Infinity`, `0*Infinity` và nhiều phép khác cũng sinh NaN. |
| “Cast `NaN` sang `int` sẽ ném exception.” | Sai | `(int) Double.NaN` cho 0. |
| “`try/catch ArithmeticException` đủ bảo vệ phép chia `double`.” | Sai | Cần kiểm tra mẫu, tính hữu hạn của đầu vào và/hoặc kết quả. |

Khi gặp phép chia trong code, hãy kiểm tra theo trình tự:

1. **Kiểu của biểu thức tại phép chia là gì?** `int/long` → chia zero ném exception; `float/double` → quy tắc IEEE 754.
2. **Tử số là hữu hạn khác 0 hay bằng 0?** Với mẫu zero, tử khác 0 dẫn đến vô cực có dấu; zero/zero dẫn đến NaN.
3. **Mẫu zero có dấu gì?** `+0.0` và `-0.0` có thể cho dấu vô cực khác nhau.
4. **Đã có Infinity/NaN trong toán hạng từ trước chưa?** Nếu có, phân tích quy tắc lan truyền thay vì chỉ tập trung mẫu zero.
5. **Contract có cho kết quả không hữu hạn không?** Nếu không, kiểm tra `Double.isFinite` của đầu vào/kết quả và trả lỗi rõ ràng.
6. **Đừng chữa lỗi bằng cast hoặc chuỗi hiển thị.** Việc chuyển vô cực/NaN sang kiểu khác có thể che mất thông tin về lỗi.

**Ghi nhớ:** Với phép chia số thực, **khác 0 chia cho ±0.0 tạo ±Infinity; ±0.0 chia cho ±0.0 tạo NaN**. Với phép chia nguyên, mẫu 0 ném `ArithmeticException`. Muốn kiểm soát đúng nghiệp vụ, hãy xác định kiểu phép toán, kiểm tra dữ liệu và xử lý `Infinity`/`NaN` tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-003">Overflow, underflow và giá trị subnormal</a>

<details>
<summary>Xem chi tiết</summary>

# Overflow, underflow và giá trị subnormal trong phép toán dấu phẩy động Java

## 1. Mục tiêu và bức tranh tổng quát

Các kiểu `float` và `double` của Java dùng số dấu phẩy động nhị phân theo IEEE 754. Chúng biểu diễn được nhiều số rất lớn và rất nhỏ nhưng **miền giá trị hữu hạn** và **độ chính xác** có giới hạn. Một phép toán có kết quả toán học hợp lệ vẫn có thể cho `Infinity`, `0.0` hoặc một số *subnormal* khi lưu vào `float`/`double`.

Bài học tập trung vào ba hiện tượng:

- **Overflow (tràn trên):** độ lớn kết quả vượt miền số hữu hạn biểu diễn được; với phép toán Java thông thường, kết quả có thể trở thành `+Infinity` hoặc `-Infinity`.
- **Underflow (tràn dưới):** kết quả khác 0 về mặt toán học quá gần 0 để được giữ với độ chính xác thông thường; Java có thể làm tròn thành số subnormal, hoặc thành `+0.0`/`-0.0`.
- **Subnormal (số dưới chuẩn):** số hữu hạn khác 0 có độ lớn nhỏ hơn số dương *normal* nhỏ nhất, giúp biểu diễn giá trị gần 0 theo cơ chế *gradual underflow*.

Không nên nhầm các hiện tượng này với **integer overflow** (số nguyên quay vòng theo modulo) hoặc lỗi biên dịch khi một literal có số mũ không thể biểu diễn.

## 2. Phạm vi IEEE 754 trong float và double

| Thuộc tính | `float` (binary32) | `double` (binary64) |
| --- | --- | --- |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit số mũ | 8 | 11 |
| Bit phân số lưu trữ | 23 | 52 |
| Độ chính xác hiệu dụng của số normal | 24 bit | 53 bit |
| Số dương normal nhỏ nhất | 2⁻¹²⁶ ≈ 1,17549435 × 10⁻³⁸ | 2⁻¹⁰²² ≈ 2,2250738585072014 × 10⁻³⁰⁸ |
| Số dương subnormal nhỏ nhất | 2⁻¹⁴⁹ ≈ 1,4 × 10⁻⁴⁵ | 2⁻¹⁰⁷⁴ ≈ 4,9 × 10⁻³²⁴ |
| Số hữu hạn dương lớn nhất | ≈ 3,4028235 × 10³⁸ | ≈ 1,7976931348623157 × 10³⁰⁸ |

Java có sẵn các hằng số hữu ích:

~~~java
Float.MAX_VALUE
Float.MIN_NORMAL
Float.MIN_VALUE
Double.MAX_VALUE
Double.MIN_NORMAL
Double.MIN_VALUE
~~~

**Rất dễ nhầm:** `Double.MIN_VALUE` **không phải giá trị âm nhỏ nhất**. Nó là **số dương khác 0 nhỏ nhất**, tức số subnormal dương thấp nhất. Giá trị hữu hạn âm có độ lớn lớn nhất là `-Double.MAX_VALUE`. Điều tương tự áp dụng cho `Float.MIN_VALUE`.

Miền số hữu hạn của `double` có khoảng âm, số 0 và khoảng dương; thêm vào đó còn có `-Infinity`, `+Infinity` và `NaN` (*Not a Number*). IEEE 754 phân biệt **`+0.0` và `-0.0`** bằng bit dấu dù toán tử `==` coi chúng bằng nhau.

## 3. Vì sao có số normal và subnormal?

Một số nhị phân *normal* khác 0 về ý tưởng có dạng:

\[
(-1)^s \times (1.f)_2 \times 2^e
\]

Trong đó `s` là bit dấu, `f` là các bit phân số và `e` là số mũ đã giải mã. Số `1` ở đầu phần định trị của giá trị normal là bit **ngầm định**. Vì thế `double` lưu 52 bit phân số nhưng có 53 bit có nghĩa đối với normal (và `float` là 23 + 1 = 24 bit).

Ở số mũ thấp nhất, không thể tiếp tục giảm số mũ normal. Chuẩn IEEE 754 cho phép **subnormal** sử dụng định trị không còn bit `1` ngầm định:

\[
(-1)^s \times (0.f)_2 \times 2^{e_{\min}}
\]

Cụ thể, với binary64: số normal dương nhỏ nhất là 2⁻¹⁰²²; một số subnormal có dạng `k × 2⁻¹⁰⁷⁴`, với `k` nguyên từ 1 đến 2⁵²−1. Khoảng cách giữa hai subnormal dương liền nhau luôn bằng `Double.MIN_VALUE`.

Bởi vậy, Java có thể biểu diễn những số nhỏ hơn `Double.MIN_NORMAL` mà chưa phải làm tròn ngay về 0. Đổi lại, **số bit có nghĩa hiệu dụng giảm dần** khi độ lớn của subnormal tiến về 0.

<text>Hình dung tiến dần về 0:</text>

~~~text
... normal ...  2^-1022  |  subnormal ...  2^-1074  |  +0.0
                 ↑                         ↑
           MIN_NORMAL                 MIN_VALUE
~~~

Biểu đồ là minh họa khái niệm; khoảng cách trên trục số không được vẽ theo tỷ lệ.

### Biên normal/subnormal

~~~java
double smallestNormal = Double.MIN_NORMAL;
double largestSubnormal = Math.nextDown(smallestNormal);
double smallestSubnormal = Double.MIN_VALUE;

System.out.println(largestSubnormal < smallestNormal); // true
System.out.println(largestSubnormal > 0.0);            // true
System.out.println(Math.nextUp(0.0) == smallestSubnormal); // true
~~~

Các đoạn mã ngắn trong bài, trừ chương trình hoàn chỉnh ở phần 8, được viết để đặt bên trong một phương thức hoặc `main`.

## 4. Overflow: kết quả vượt số hữu hạn lớn nhất

Xét phép nhân:

~~~java
double x = Double.MAX_VALUE;
double overflow = x * 2.0;
System.out.println(overflow);                 // Infinity
System.out.println(Double.isInfinite(overflow)); // true
~~~

`Double.MAX_VALUE` là số `double` hữu hạn dương lớn nhất. Kết quả toán học của phép nhân gấp đôi lớn hơn rất nhiều miền số hữu hạn, nên phép nhân được làm tròn theo quy tắc floating point và trả `+Infinity`.

Với kết quả âm vượt giới hạn, có thể nhận `-Infinity`:

~~~java
double negativeOverflow = -Double.MAX_VALUE * 2.0;
System.out.println(negativeOverflow); // -Infinity
~~~

Một số điểm quan trọng:

1. Phép toán số thực Java thông thường **không ném `ArithmeticException`** vì overflow/underflow.
2. `Infinity` là một giá trị floating point đặc biệt. Các phép tính tiếp theo có thể tiếp tục cho `Infinity` hoặc tạo `NaN`, chẳng hạn `Infinity - Infinity`.
3. Overflow có thể xảy ra ngay cả khi **kết quả cuối của phép biến đổi đại số** đáng lẽ hữu hạn: kết quả trung gian có thể tràn trước.

~~~java
double a = 1e200;
double b = 1e200;

double unstable = (a * b) / b;
double rearranged = a * (b / b);

System.out.println(unstable);   // Infinity
System.out.println(rearranged); // 1.0E200
~~~

Trong số học thực, hai biểu thức bằng nhau khi `b != 0`. Trong số học floating point, phép nhân trung gian `a * b` đã overflow. Biến đổi thứ tự tính toán chỉ phù hợp khi hiểu miền giá trị, quy tắc làm tròn, xử lý số 0/Infinity/NaN và các yêu cầu chính xác của bài toán.

### Infinity khác NaN

~~~java
double inf = Double.MAX_VALUE * 2.0;
double undefined = inf - inf;

System.out.println(Double.isInfinite(inf)); // true
System.out.println(Double.isNaN(undefined)); // true
~~~

`NaN` biểu diễn kết quả không xác định trong ngữ nghĩa dấu phẩy động; không phải số vô hạn. Ví dụ khác: `0.0 / 0.0` cho `NaN`, còn `1.0 / 0.0` cho `Infinity`. Cả ba kết quả đều không làm ném ngoại lệ số học.

## 5. Underflow: kết quả quá nhỏ để giữ đủ độ chính xác

Với số thực, underflow xảy ra ở đầu **gần số 0** của dải số. Phải phân biệt *kết quả subnormal vẫn khác 0* với *kết quả đã làm tròn thành 0*.

~~~java
double smallButRepresentable = Double.MIN_NORMAL / 2.0;
double tooSmall = Double.MIN_VALUE / 2.0;

System.out.println(smallButRepresentable > 0.0); // true
System.out.println(smallButRepresentable < Double.MIN_NORMAL); // true
System.out.println(tooSmall); // 0.0
~~~

`Double.MIN_NORMAL / 2` bằng 2⁻¹⁰²³ và biểu diễn được như một số subnormal. `Double.MIN_VALUE / 2` bằng 2⁻¹⁰⁷⁵, nằm đúng giữa 0 và số subnormal dương nhỏ nhất. Làm tròn **nearest, ties to even** cho ra `+0.0`.

**Lưu ý về thuật ngữ IEEE 754:** underflow theo nghĩa điều kiện ngoại lệ kỹ thuật liên quan đến tính *tininess* và mất độ chính xác. Trong bài học lập trình Java, “underflow thành subnormal/zero” mô tả hành vi vùng độ lớn rất nhỏ. Không nên khẳng định rằng **mọi** kết quả subnormal đều là một lỗi hay một phép toán underflow không chính xác: nhiều kết quả subnormal (như chia `MIN_NORMAL` cho 2) vẫn chính xác.

### Gradual underflow

Nếu không có subnormal, một kết quả dương nhỏ hơn `MIN_NORMAL` phải đột ngột trở thành 0. Subnormal giúp chuyển tiếp **dần dần**: rất nhiều giá trị nhỏ vẫn khác 0, nhưng độ chính xác tương đối kém dần.

Ví dụ:

~~~java
double halfNormal = Double.MIN_NORMAL / 2.0;
double smaller = halfNormal / 2.0;

System.out.println(halfNormal != 0.0); // true
System.out.println(smaller != 0.0);    // true
~~~

Đây là hành vi hữu ích khi một phép trừ của hai số dương gần nhau tạo giá trị rất nhỏ; vẫn có thể giữ được thông tin về sự khác nhau nếu kết quả nằm trong miền subnormal biểu diễn được.

## 6. Dấu của zero khi underflow

`double` phân biệt `+0.0` và `-0.0`. Một phép toán dương quá nhỏ thường làm tròn về `+0.0`; phép toán âm tương ứng có thể làm tròn về `-0.0`.

~~~java
double positiveZero = Double.MIN_VALUE / 2.0;
double negativeZero = -Double.MIN_VALUE / 2.0;

System.out.println(positiveZero); // 0.0
System.out.println(negativeZero); // -0.0
System.out.println(positiveZero == negativeZero); // true
System.out.println(1.0 / positiveZero); // Infinity
System.out.println(1.0 / negativeZero); // -Infinity
~~~

Do đó không được kết luận hai giá trị floating point có biểu diễn bit giống nhau chỉ vì `a == b`. Khi cần quan sát dấu của số 0 hoặc bit của NaN, có thể dùng `Double.doubleToRawLongBits`; với `float`, dùng `Float.floatToRawIntBits`. Cơ chế so sánh số thực, sắp xếp và serialization phải xác định rõ có quan tâm `-0.0` hay không.

## 7. API Java để phát hiện tình trạng số thực

| API | Mục đích |
| --- | --- |
| `Double.isFinite(x)`, `Float.isFinite(x)` | Kiểm tra x là số hữu hạn (bao gồm ±0, normal và subnormal) |
| `Double.isInfinite(x)`, `Float.isInfinite(x)` | Kiểm tra ±Infinity |
| `Double.isNaN(x)`, `Float.isNaN(x)` | Kiểm tra NaN |
| `Double.MIN_NORMAL`, `Float.MIN_NORMAL` | Ngưỡng dương nhỏ nhất của số normal |
| `Double.MIN_VALUE`, `Float.MIN_VALUE` | Số dương khác 0 nhỏ nhất: subnormal thấp nhất |
| `Math.ulp(x)` | Khoảng cách theo đơn vị cuối cùng (*unit in last place*) tại x |
| `Math.nextUp(x)`, `Math.nextDown(x)` | Giá trị floating point kề trên/kề dưới |

Hàm nhận diện subnormal hữu hạn, khác 0:

~~~java
static boolean isSubnormal(double x) {
    return Double.isFinite(x)
            && x != 0.0
            && Math.abs(x) < Double.MIN_NORMAL;
}
~~~

Đây là một phương thức Java 17 độc lập trong thân lớp (chưa có `main`). Điều kiện `x != 0.0` loại cả `+0.0` và `-0.0`. Điều kiện `isFinite` loại Infinity/NaN; `Math.abs(x) < MIN_NORMAL` phân biệt miền subnormal với miền normal. Biểu thức tương tự áp dụng cho `float` với `Float.MIN_NORMAL` và `Float.isFinite`.

Đặc biệt:

~~~java
System.out.println(Math.ulp(0.0) == Double.MIN_VALUE); // true
System.out.println(Math.ulp(Double.MIN_NORMAL) == Double.MIN_VALUE); // true
System.out.println(Math.nextUp(0.0) == Double.MIN_VALUE); // true
~~~

Một số floating point hữu hạn *normal* có bước nhảy phụ thuộc độ lớn. Ở vùng subnormal dương, các giá trị liên tiếp cách nhau đúng `Double.MIN_VALUE`.

Không nên dùng `Math.getExponent(x)` một mình để phát hiện subnormal, vì nó trả `Double.MIN_EXPONENT - 1` cho **cả zero lẫn subnormal** (với `double` là −1023).

## 8. Chương trình Java 17+ đầy đủ, biên dịch và quan sát

Tạo tệp `FloatingRangeDemo.java`:

~~~java
public class FloatingRangeDemo {
    private static boolean isSubnormal(double x) {
        return Double.isFinite(x)
                && x != 0.0
                && Math.abs(x) < Double.MIN_NORMAL;
    }

    public static void main(String[] args) {
        double overflow = Double.MAX_VALUE * 2.0;
        double subnormal = Double.MIN_NORMAL / 2.0;
        double lost = Double.MIN_VALUE / 2.0;
        double negativeZero = -Double.MIN_VALUE / 2.0;

        System.out.println("overflow=" + overflow);
        System.out.println("overflow finite=" + Double.isFinite(overflow));
        System.out.println("subnormal=" + subnormal);
        System.out.println("is subnormal=" + isSubnormal(subnormal));
        System.out.println("min normal=" + Double.MIN_NORMAL);
        System.out.println("min positive=" + Double.MIN_VALUE);
        System.out.println("underflow to zero=" + lost);
        System.out.println("negative zero=" + negativeZero);
        System.out.println("negative reciprocal=" + (1.0 / negativeZero));
        System.out.println("ulp zero=" + Math.ulp(0.0));

        double parsedBig = Double.parseDouble("1e309");
        double parsedSmall = Double.parseDouble("1e-400");
        System.out.println("parsed big=" + parsedBig);
        System.out.println("parsed small=" + parsedSmall);

        float fSubnormal = Float.MIN_NORMAL / 2.0f;
        float fOverflow = Float.MAX_VALUE * 2.0f;
        System.out.println("float subnormal=" +
                (fSubnormal > 0.0f && fSubnormal < Float.MIN_NORMAL));
        System.out.println("float infinity=" + Float.isInfinite(fOverflow));

        double nan = overflow - overflow;
        System.out.println("NaN detected=" + Double.isNaN(nan));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 FloatingRangeDemo.java
java FloatingRangeDemo
~~~

Kết quả stdout:

~~~text
overflow=Infinity
overflow finite=false
subnormal=1.1125369292536007E-308
is subnormal=true
min normal=2.2250738585072014E-308
min positive=4.9E-324
underflow to zero=0.0
negative zero=-0.0
negative reciprocal=-Infinity
ulp zero=4.9E-324
parsed big=Infinity
parsed small=0.0
float subnormal=true
float infinity=true
NaN detected=true
~~~

Phân tích từng nhóm kết quả:

- `MAX_VALUE * 2` tràn thành Infinity; `isFinite` báo false.
- `MIN_NORMAL / 2` thuộc vùng subnormal nhưng còn khác 0; chương trình kiểm tra bằng miền giá trị chứ không chỉ dựa trên định dạng in.
- `MIN_VALUE / 2` nhỏ hơn giá trị dương khác 0 nhỏ nhất, nên được làm tròn thành 0.
- Underflow của số âm có thể tạo `-0.0`; phép lấy nghịch đảo minh họa dấu có ý nghĩa.
- `Double.parseDouble` xử lý chuỗi ngoài miền thành Infinity/0 theo quy tắc chuyển đổi floating point; phép parse không thay thế được việc **kiểm tra miền** nếu ứng dụng yêu cầu kết quả hữu hạn khác 0.
- `float` cũng có dải subnormal và overflow, nhưng với biên gần 0/Infinity lớn nhỏ khác `double`.
- `Infinity - Infinity` tạo NaN.

## 9. Literal số quá lớn/nhỏ: khác với kết quả phép toán runtime

Literals dấu phẩy động trong mã nguồn Java có quy tắc ở thời điểm biên dịch. Các ví dụ này **không biên dịch**:

~~~java
// double tooLarge = 1e309; // literal double ngoài miền
// double tooSmall = 1e-400; // literal double khác 0 quá nhỏ để thành nonzero
// float alsoTooLarge = 1e39f; // literal float ngoài miền
~~~

Khi cần minh họa overflow/underflow runtime, thực hiện phép toán từ các giá trị hợp lệ hoặc parse từ chuỗi ở runtime:

~~~java
double overflowRuntime = 1e308 * 10.0;
double underflowRuntime = 1e-300 * 1e-300;

System.out.println(overflowRuntime);  // Infinity
System.out.println(underflowRuntime); // 0.0
~~~

`Double.parseDouble("1e309")` trả Infinity và `Double.parseDouble("1e-400")` trả 0.0; khi giá trị chuỗi đến từ cấu hình/người dùng, lập trình viên cần kiểm tra để tránh silently chấp nhận dữ liệu mất miền.

Lưu ý riêng: literal `0.0` hoàn toàn hợp lệ; `0x0.0p0` cũng vậy. Giới hạn đối với literal *khác 0* quá nhỏ phản ánh quy tắc biên dịch, không có nghĩa ngôn ngữ cấm số 0.

## 10. Underflow không giống mất độ chính xác thông thường

Phần lớn số thập phân không có biểu diễn nhị phân hữu hạn, chẳng hạn `0.1`. Điều đó tạo ra **rounding error**, nhưng không nhất thiết là underflow:

~~~java
double x = 0.1 + 0.2;
System.out.println(x); // 0.30000000000000004
~~~

`0.30000000000000004` là kết quả làm tròn của các số normal và phép cộng; nó **không** thuộc vùng subnormal. Underflow liên quan đến độ lớn quá nhỏ gần 0, không phải mọi sai số dấu phẩy động.

Một kết quả subnormal vẫn có thể là biểu diễn **chính xác** của phép toán. Ví dụ, `Double.MIN_NORMAL / 2.0` là 2⁻¹⁰²³ và có thể biểu diễn chính xác dưới dạng subnormal. Khác biệt giữa **miền (range)** và **độ chính xác (precision)** là nền tảng của việc phân tích floating point.

Ngoài ra, đừng nhầm underflow của **giá trị** với underflow của **số mũ trong hàm Math.exp**: ví dụ `Math.exp(-1000)` có thể trả 0.0 vì kết quả toán học quá nhỏ để biểu diễn bằng `double`, không phải do số mũ nguyên trong máy “quay vòng”.

## 11. Các vấn đề phổ biến và cách xử lý

### 11.1. Intermediate overflow dù kết quả mong muốn hữu hạn

Công thức độ dài vector:

\[
\sqrt{x^2 + y^2}
\]

có thể overflow ở `x*x`/`y*y` dù kết quả cuối vẫn nhỏ hơn `Double.MAX_VALUE`.

~~~java
double x = 1e308;
double y = 1e308;

double naive = Math.sqrt(x * x + y * y);
double stable = Math.hypot(x, y);

System.out.println(naive);  // Infinity
System.out.println(stable); // 1.4142135623730951E308
~~~

`Math.hypot` được thiết kế giảm nguy cơ overflow/underflow trung gian. Nó vẫn trả Infinity khi độ lớn cuối cùng thật sự vượt dải biểu diễn.

### 11.2. Tích của nhiều số rất nhỏ bị làm tròn thành 0

~~~java
double a = 1e-200;
double b = 1e-200;
double c = 1e200;

double lostFirst = (a * b) * c;
double keptFirst = a * (b * c);

System.out.println(lostFirst); // 0.0
System.out.println(keptFirst); // 1.0E-200
~~~

Khi tính tích/xác suất của rất nhiều số nhỏ, đôi lúc chuyển sang **log-domain** (cộng logarithm thay vì nhân trực tiếp, rồi áp dụng biến đổi ổn định khi cần) giúp tránh underflow trung gian. Phải đặc biệt xử lý số 0, giá trị âm, NaN và yêu cầu trả lại giá trị cuối có thể hoặc không thể biểu diễn.

### 11.3. Định dạng in khác với tính chính xác

Java in `Double.MIN_VALUE` là `4.9E-324`; biểu thức đó là chuỗi thập phân ngắn đủ để round-trip về cùng giá trị `double`, không phải khẳng định giá trị nhị phân đúng bằng số hữu tỉ thập phân có bốn chữ số 4.9.

Tương tự, `System.out.println(0.1)` in `0.1`, nhưng lưu trữ nội bộ là số binary64 xấp xỉ 0,1. Đừng suy ra tính chính xác chỉ từ số chữ số hiển thị.

### 11.4. Kiểm tra kết quả trước khi sử dụng

Ví dụ nhận số thực từ một phép tính:

~~~java
static double requireFinite(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Kết quả phải hữu hạn");
    }
    return value;
}
~~~

Hàm này từ chối Infinity/NaN nhưng **cho phép 0 và subnormal**. Nếu nghiệp vụ cấm kết quả khác 0 bị mất thành 0, không có cách nhìn **chỉ vào kết quả 0.0 cuối cùng** để biết trước phép tính từng có một giá trị nhỏ hơn miền lưu trữ. Cần kiểm soát dữ liệu đầu vào, dùng phương pháp số ổn định hơn hoặc mô hình độ chính xác rộng hơn.

### 11.5. Khi nào dùng BigDecimal?

`BigDecimal` biểu diễn hệ thập phân với độ chính xác tùy ý (giới hạn tài nguyên và phạm vi scale) và quy tắc làm tròn có kiểm soát. Nó phù hợp với số tiền, dữ liệu thập phân chính xác hoặc phép tính cần giữ nhiều chữ số hơn `double`:

~~~java
import java.math.BigDecimal;

// Đoạn minh họa bên trong phương thức:
BigDecimal x = new BigDecimal("1e400");
BigDecimal y = x.multiply(BigDecimal.TEN);
System.out.println(y.toEngineeringString()); // 10E+400
~~~

`BigDecimal` không có giá trị NaN/Infinity giống `double`; không phải công cụ thay thế `double` cho mọi bài toán khoa học. Khi lập `BigDecimal` từ số thập phân cần giữ nguyên, ưu tiên constructor chuỗi (`new BigDecimal("0.1")`) hoặc `BigDecimal.valueOf(double)` phù hợp; `new BigDecimal(0.1)` giữ chính xác **giá trị nhị phân đã bị xấp xỉ** trước đó.

Lựa chọn kiểu dữ liệu phụ thuộc bài toán: phép đo kỹ thuật chấp nhận sai số thường vẫn dùng `double`; tài chính và dữ liệu decimal exact thường dùng `BigDecimal`; bài toán rất lớn/nhỏ có thể cần scale, log-domain hoặc thư viện số chuyên dụng.

## 12. Giá trị biên và những điều không nên nhầm

| Biểu thức (đặt trong một phương thức) | Kết quả/ý nghĩa |
| --- | --- |
| `Double.MAX_VALUE * 2.0` | `Infinity`: overflow dương |
| `-Double.MAX_VALUE * 2.0` | `-Infinity`: overflow âm |
| `Double.MIN_NORMAL / 2.0` | Subnormal dương khác 0, **chính xác** |
| `Double.MIN_VALUE / 2.0` | `+0.0` theo round-to-nearest ties-to-even |
| `-Double.MIN_VALUE / 2.0` | `-0.0` |
| `Math.nextUp(0.0)` | `Double.MIN_VALUE` |
| `Math.nextDown(0.0)` | `-Double.MIN_VALUE` |
| `Double.isFinite(Double.MIN_VALUE)` | `true`: subnormal vẫn hữu hạn |
| `Double.isNaN(Double.POSITIVE_INFINITY)` | `false` |
| `Double.NaN == Double.NaN` | `false` theo phép `==` của IEEE 754 |
| `Double.compare(-0.0, +0.0) < 0` | `true`: thứ tự theo `Double.compare` phân biệt signed zero |
| `1.0 / 0.0` | `Infinity`, không ném `ArithmeticException` |
| `1 / 0` | Lỗi phép chia số nguyên: khác quy tắc chia floating point |

### Java 17 và strict floating point

Từ **Java 17**, ngữ nghĩa floating point luôn strict theo Java Language Specification (JEP 306). Từ khóa `strictfp` vẫn tồn tại để tương thích mã cũ nhưng không còn cần thiết để bắt buộc strict trên Java 17+. Điều này giúp mã `float`/`double` có các quy tắc xác định của ngôn ngữ, nhưng **không loại bỏ** overflow, underflow, subnormal hay rounding error.

Java không cung cấp qua phép toán floating point thông thường cơ chế tự động ném exception cho mọi overflow/underflow hoặc một cờ trạng thái IEEE 754 mà lập trình viên có thể luôn truy cập trực tiếp. Nếu hệ thống cần phát hiện rủi ro, hãy kết hợp kiểm tra giá trị (`isFinite`, miền subnormal) với thiết kế thuật toán và dữ liệu đầu vào.

## 13. Tự kiểm tra

**Câu 1.** Vì sao `Double.MIN_VALUE` lớn hơn 0 nhưng nhỏ hơn `Double.MIN_NORMAL`?

Vì `MIN_VALUE` là số subnormal dương nhỏ nhất (2⁻¹⁰⁷⁴), còn `MIN_NORMAL` là số normal dương nhỏ nhất (2⁻¹⁰²²). Tên `MIN_VALUE` ở đây nói đến độ lớn **dương khác 0**, không phải điểm âm cực tiểu của trục số.

**Câu 2.** `Double.MIN_NORMAL / 2.0` có bắt buộc bằng 0 hay mất chính xác không?

Không. 2⁻¹⁰²³ là một giá trị subnormal có thể biểu diễn **chính xác** dưới dạng binary64.

**Câu 3.** Vì sao `Double.MIN_VALUE / 2.0` cho `0.0`?

Vì giá trị toán học 2⁻¹⁰⁷⁵ nằm giữa 0 và giá trị dương khác 0 nhỏ nhất; mặc định round-to-nearest ties-to-even chọn +0.0.

**Câu 4.** Có thể dựa vào `Double.isFinite(result)` để phát hiện mọi underflow không?

Không. `0.0` và các số subnormal đều **hữu hạn**. `isFinite` nhận biết NaN/Infinity; dưới chuẩn cần kiểm tra thêm điều kiện khác 0 và `abs(result) < MIN_NORMAL`, còn việc làm tròn thành 0 cần được phát hiện bằng thông tin về bài toán hoặc phép tính trước đó.

**Câu 5.** Vì sao `Math.hypot(x, y)` thường an toàn hơn `Math.sqrt(x*x + y*y)`?

Nó được thiết kế hạn chế overflow và underflow trong các kết quả trung gian khi tính khoảng cách; việc bình phương trực tiếp có thể tạo Infinity/0 trước khi căn bậc hai được áp dụng.

**Câu 6.** Có nên thay mọi phép tính `double` bằng `BigDecimal`?

Không. `BigDecimal` hữu ích khi cần chính xác thập phân và kiểm soát làm tròn; nó thay đổi mô hình dữ liệu, tốc độ, bộ nhớ và thuật toán. Với bài toán khoa học/kỹ thuật, chọn `double` và thuật toán số ổn định có thể là lựa chọn phù hợp hơn.

## 14. Tóm tắt và tham khảo

- **Overflow:** độ lớn vượt miền hữu hạn → thường `±Infinity`, cần kiểm tra `isFinite`/`isInfinite`.
- **Underflow:** độ lớn quá gần 0 → kết quả subnormal hoặc bị làm tròn về `±0.0`; không nhất thiết là một ngoại lệ Java.
- **Subnormal:** giá trị khác 0 có `abs(x) < MIN_NORMAL`; giúp *gradual underflow* nhưng có ít bit có nghĩa hiệu dụng.
- `MIN_VALUE` là số dương khác 0 nhỏ nhất, còn `MIN_NORMAL` là số normal dương nhỏ nhất.
- Viết thuật toán tránh kết quả trung gian quá lớn/quá nhỏ và kiểm tra NaN, Infinity, zero có dấu, tính chính xác theo nhu cầu.

Tài liệu tra cứu:

- [Java SE 17 — Double](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html) và [Float](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html): `MAX_VALUE`, `MIN_NORMAL`, `MIN_VALUE` và các API kiểm tra.
- [Java SE 17 — Math](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `ulp`, `nextUp`, `nextDown`, `hypot`.
- [JLS SE 17 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): số hữu hạn, subnormal, NaN, Infinity và zero.
- [JEP 306 — Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306): thay đổi ngữ nghĩa strict của Java 17.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-004">Toán tử % khác Math.IEEEremainder</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử `%` khác `Math.IEEEremainder` trong Java

Java hỗ trợ **hai phép lấy phần dư trên số dấu phẩy động với hai quy tắc khác nhau**:

- Toán tử **`%`** lấy phần dư từ phép chia có thương **cắt phần lẻ về 0** (*truncation toward zero*), gần với hàm `fmod` của C.
- **`Math.IEEEremainder(double, double)`** lấy phần dư từ phép chia có thương được làm tròn đến **số nguyên gần nhất**; nếu đúng giữa hai số nguyên, chọn **số nguyên chẵn** (*ties to even*) theo định nghĩa IEEE 754.

Chúng có thể cho hai kết quả **khác dấu**, dù cùng nhận một cặp số thực.

```java
double x = 7.0;
double y = 2.0;

System.out.println(x % y);                  // 1.0
System.out.println(Math.IEEEremainder(x,y)); // -1.0
```

Cả hai kết quả đều hợp lý **theo quy tắc thương riêng**. `7 / 2 = 3.5`; nếu cắt về 0, thương nguyên là 3 và dư là `7 - 2*3 = 1`. Nếu chọn thương nguyên gần nhất với quy tắc hòa chọn số chẵn, thương là 4 và dư là `7 - 2*4 = -1`.

Bài học tập trung vào cách hiểu, API đúng, dấu của phần dư, trường hợp IEEE 754 đặc biệt, các bẫy khi dùng cho góc/chu kỳ, và cách kiểm chứng kết quả trên Java 17+.

## 1. Hai khái niệm khác nhau: remainder từ thương nào?

Gọi `x` là **số bị chia** (*dividend*), `y` là **số chia** (*divisor*) và giả sử cả hai hữu hạn, `y != 0`. Về mặt toán học, một phần dư được mô tả bởi:

```text
r = x - y * q
```

Điểm khác biệt nằm ở cách chọn **số nguyên `q`**.

### `x % y`: thương cắt về 0

Với toán hạng chấm động, toán tử `%` của Java xác định thương nguyên khái niệm:

```text
q = trunc(x / y)
r = x - q * y
```

`trunc` loại bỏ phần lẻ theo hướng về 0. Ví dụ:

- `7 / 2 = 3.5`: `q = 3`, `r = +1`.
- `-7 / 2 = -3.5`: `q = -3`, `r = -1`.
- `7 / -2 = -3.5`: `q = -3`, `r = +1`.
- `-7 / -2 = 3.5`: `q = 3`, `r = -1`.

**Nếu khác 0, phần dư `%` mang dấu của `x`**, không phải dấu của `y`. Giá trị tuyệt đối của dư nhỏ hơn `|y|` khi hai toán hạng hữu hạn và số chia khác 0.

### `Math.IEEEremainder`: thương nguyên gần nhất, hòa chọn chẵn

Phương thức `Math.IEEEremainder(x, y)` dùng:

```text
q = số nguyên gần nhất với thương toán học x / y;
    nếu hai số nguyên cách đều, chọn số nguyên chẵn.
r = x - q * y
```

Với `7 / 2 = 3.5`, hai số nguyên 3 và 4 cùng cách 0.5. Số 4 **chẵn**, vậy `q = 4` và `r = -1`. Với `5 / 2 = 2.5`, hai ứng viên là 2 và 3, nên chọn `q = 2` và `r = +1`.

Điểm cần nhấn mạnh: “số nguyên chẵn” nói về **thương `q`**, **không** nói về phần dư. Đây không phải làm tròn theo kiểu “0.5 luôn lên”.

> Các công thức trên giải thích **ngữ nghĩa toán học**. Không nên tự mô phỏng phép `%` hoặc `IEEEremainder` bằng biểu thức chấm động `x - y * (...)` vì phép chia và phép nhân trung gian có thể làm tròn hoặc overflow, khiến kết quả khác API được định nghĩa. Hãy gọi đúng toán tử/phương thức.

## 2. Bảng so sánh với số dương và âm

| `x` | `y` | `trunc(x/y)` | `q` gần nhất (ties-even) | `x % y` | `Math.IEEEremainder(x,y)` |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 2 | 3 | 4 | 1 | −1 |
| 5 | 2 | 2 | 2 | 1 | 1 |
| 3 | 2 | 1 | 2 | 1 | −1 |
| −7 | 2 | −3 | −4 | −1 | 1 |
| 7 | −2 | −3 | −4 | 1 | −1 |
| −7 | −2 | 3 | 4 | −1 | 1 |
| 6 | 4 | 1 | 2 | 2 | −2 |
| −6 | 4 | −1 | −2 | −2 | 2 |
| 8 | 3 | 2 | 3 | 2 | −1 |

Trong bảng này, tất cả số là những số nguyên nhỏ được biểu diễn **chính xác** dưới dạng `double`, nên bảng không lẫn vấn đề sai số của literal thập phân.

Các trường hợp dễ nhầm:

- **`5.0 % 2.0 == Math.IEEEremainder(5.0,2.0)`:** cả hai bằng 1, vì ties-even chọn thương 2.
- **`7.0 % 2.0 != Math.IEEEremainder(7.0,2.0)`:** 1 khác −1, vì ties-even chọn thương 4.
- **`6.0 % 4.0` bằng 2, còn `Math.IEEEremainder(6.0,4.0)` bằng −2:** thương 1.5 nằm giữa 1 và 2, nên IEEE chọn 2.
- **Đổi dấu số chia** có thể đổi dấu thương nhưng không tự làm đổi dấu phần dư trong ví dụ tương ứng: các kết quả được quyết định bởi quy tắc chọn thương và dấu của số bị chia.

Để học nhanh, chỉ cần nhớ `7.0` và `2.0`: nó đủ làm phản ví dụ cho nhận định “cả hai đều là modulo nên như nhau”.

## 3. Vì sao tên “IEEEremainder” không có nghĩa “phần dư luôn dương”?

Với `%` trên chấm động, nếu kết quả khác 0, dấu theo **số bị chia `x`**. Nhưng với `Math.IEEEremainder`, phần dư có thể dương **hoặc âm**, không phụ thuộc riêng vào dấu của `x` hay `y`.

Khi `x`, `y` hữu hạn và `y != 0`:

- Phần dư của `%` có độ lớn **nhỏ hơn** `|y|`.
- Phần dư của `IEEEremainder` có độ lớn **không lớn hơn một nửa** `|y|` theo quan hệ toán học của thương làm tròn gần nhất.
- Ở đúng vị trí nửa chu kỳ, dấu kết quả `IEEEremainder` được quyết định gián tiếp bởi **ties-to-even của thương**, nên có thể xuất hiện cả `+|y|/2` và `-|y|/2` tùy trường hợp.

Ví dụ:

```java
System.out.println(Math.IEEEremainder(5.0, 2.0)); // 1.0
System.out.println(Math.IEEEremainder(7.0, 2.0)); // -1.0
```

Cả hai giá trị có cùng độ lớn 1.0 nhưng **khác dấu** dù `x` và `y` đều dương. Do đó, `Math.IEEEremainder` **không phù hợp để tự động tạo index từ 0 đến n−1**.

Cụm từ *remainder* trong API không đồng nghĩa với quy ước “modulo không âm”. Khi thiết kế dữ liệu, phải nêu rõ phần dư thuộc khoảng nào và số chia có được âm không.

## 4. Hòa ở nửa đơn vị: ties-to-even khác `Math.round`

Quy tắc hòa của `Math.IEEEremainder` tương thích với nguyên tắc làm tròn về số nguyên gần nhất, chọn số chẵn. Java cung cấp `Math.rint(double)` theo quy tắc chọn số nguyên gần nhất với ties-to-even, nhưng **không** được coi `Math.IEEEremainder` đơn giản là `x - Math.rint(x/y) * y` trên mọi đầu vào.

```java
System.out.println(Math.rint(2.5));  // 2.0
System.out.println(Math.round(2.5)); // 3
```

`Math.round` là API **khác**: với trường hợp nằm đúng giữa hai số nguyên, nó chọn hướng về **dương vô cực** theo quy tắc của phương thức này. Còn `Math.rint(2.5)` chọn 2 vì 2 chẵn. `Math.rint(3.5)` chọn 4 vì 4 chẵn.

Cẩn thận: phép chia `x/y` tính trước bằng `double` có thể bị làm tròn thành giá trị khác thương toán học thật, hoặc overflow thành vô cực khi `x` rất lớn và `y` cực nhỏ. Phép nhân `q*y` cũng có thể làm tròn. Vì thế, dùng `Math.rint` để **minh họa quy tắc ties-even**, nhưng dùng `Math.IEEEremainder` khi cần chính xác **ngữ nghĩa IEEE remainder**.

### Còn `Math.floor` và `Math.floorMod`?

- `Math.floor(double)` trả giá trị `double` là số nguyên toán học lớn nhất không vượt đối số; đây không phải phép lấy phần dư.
- `Math.floorMod` nhận đối số **nguyên** (`int`/`long`), **không có overload nhận `double`**. Với modulo nguyên dương, nó tạo phần dư không âm kể cả khi số bị chia âm.
- `Math.IEEEremainder` dùng thương **gần nhất** chứ **không** dùng `floor` hay `trunc`.

Ba cách chọn thương (*truncation*, *floor*, *nearest-ties-even*) là ba quy tắc khác nhau; chỉ dùng chúng thay thế nhau khi bạn chứng minh kết quả đáp ứng hợp đồng bài toán.

## 5. Trường hợp đặc biệt: zero, NaN và Infinity

Dấu phẩy động của Java có các giá trị `NaN`, `+Infinity`, `-Infinity`, `+0.0` và `-0.0`. Kết quả của `%` **khác đáng kể** với phép `%` số nguyên khi chia cho 0.

| Tình huống dấu phẩy động | `x % y` | `Math.IEEEremainder(x,y)` |
| --- | --- | --- |
| Bất kỳ toán hạng nào là `NaN` | `NaN` | `NaN` |
| `x` là ±Infinity, `y` hữu hạn khác 0 | `NaN` | `NaN` |
| `y` là `+0.0` hoặc `-0.0` | `NaN` | `NaN` |
| `x` hữu hạn, `y` là ±Infinity | Trả đúng `x` | Trả đúng `x` |
| `x` bằng `+0.0` hoặc `-0.0`, `y` hữu hạn khác 0 | Trả zero cùng dấu với `x` | Trả zero cùng dấu với `x` |
| Kết quả khác 0, hai toán hạng hữu hạn | Dấu theo `x` | Dấu tùy thương làm tròn gần nhất |
| Kết quả đúng bằng 0 | Zero cùng dấu với `x` | Zero cùng dấu với `x` |

`Math.IEEEremainder` là phương thức nhận và trả `double`. Không có overload `IEEEremainder(float,float)`: nếu truyền hai `float`, Java mở rộng chúng sang `double` và phương thức trả `double`.

### Chia cho 0 là `NaN`, không phải `ArithmeticException`

```java
double a = 7.0 % 0.0;
double b = Math.IEEEremainder(7.0, 0.0);
System.out.println(Double.isNaN(a)); // true
System.out.println(Double.isNaN(b)); // true
```

Khác với:

```java
// int invalid = 7 % 0; // Phép % số nguyên chia cho 0 không hợp lệ;
// với số chia nguyên bằng 0 ở runtime, Java ném ArithmeticException.
```

Với toán hạng dấu phẩy động, `%` không ném ngoại lệ runtime do zero divisor. `NaN` cũng không so sánh bằng chính nó theo `==`, nên kiểm tra bằng `Double.isNaN`.

### Zero âm vẫn mang dấu

```java
double r1 = -4.0 % 2.0;
double r2 = Math.IEEEremainder(-4.0, 2.0);

System.out.println(r1); // -0.0
System.out.println(r2); // -0.0
```

Dù `-0.0 == +0.0` là `true`, chúng có mẫu bit dấu khác nhau. Nếu định dạng dữ liệu/giao thức cần bảo toàn zero âm, có thể kiểm tra `Double.doubleToRawLongBits`.

Không dùng `Math.abs` để kết luận mọi phần dư giờ có nghĩa modulo mong muốn: trị tuyệt đối của dư âm có thể cho một vị trí **khác hẳn** vị trí tương đương trong chu kỳ.

## 6. Kiểu của biểu thức: `float`, `double` và numeric promotion

Toán tử `%` có thể dùng với `int`, `long`, `float` hoặc `double`. Đối với phép toán có các toán hạng khác kiểu, Java áp dụng **binary numeric promotion**. Ví dụ:

```java
float a = 7.0f;
float b = 2.0f;

float floatRemainder = a % b; // Kết quả phép % là float.
double ieeeRemainder = Math.IEEEremainder(a, b); // Hai float được mở rộng lên double.
```

Giá trị ở ví dụ là `floatRemainder == 1.0f` và `ieeeRemainder == -1.0`. Khi có `double` ở một toán hạng của `%`, phép toán được tính với `double`:

```java
float x = 7.0f;
double y = 2.0;
double mixed = x % y; // Kết quả double.
```

Với số thập phân không biểu diễn chính xác trong binary32/binary64, dữ liệu đã được làm tròn **trước khi gọi phương thức** hoặc thực hiện toán tử. Vì thế, một phép tính tưởng là “x % 0.1” có thể cho phần dư nhỏ không mong muốn do bản thân `0.1` là giá trị nhị phân gần đúng.

Muốn khẳng định kết quả đúng, phải tách hai vấn đề:

1. **Quy tắc lấy thương**: truncate hay nearest-ties-even?
2. **Biểu diễn các toán hạng**: `float`/`double` có lưu chính xác các số đầu vào không?

`Math.IEEEremainder` giải quyết quy tắc thứ nhất, **không** biến mọi giá trị `double` thành thập phân chính xác.

## 7. Ví dụ chạy độc lập trên Java 17+

Lưu mã dưới tên `RemainderComparisonDemo.java`:

```java
public class RemainderComparisonDemo {
    public static void main(String[] args) {
        double[][] samples = {
                {7, 2}, {5, 2}, {3, 2},
                {-7, 2}, {7, -2}, {-7, -2},
                {6, 4}, {-6, 4}, {8, 3}
        };

        for (double[] sample : samples) {
            double x = sample[0];
            double y = sample[1];
            System.out.println(x + " % " + y + " = " + (x % y)
                    + " | IEEE = " + Math.IEEEremainder(x, y));
        }

        System.out.println("rint(2.5) = " + Math.rint(2.5));
        System.out.println("round(2.5) = " + Math.round(2.5));

        System.out.println("zero divisor with % -> NaN: "
                + Double.isNaN(7.0 % 0.0));
        System.out.println("zero divisor with IEEE -> NaN: "
                + Double.isNaN(Math.IEEEremainder(7.0, 0.0)));
        System.out.println("infinite dividend -> NaN: "
                + Double.isNaN(Math.IEEEremainder(
                        Double.POSITIVE_INFINITY, 2.0)));

        System.out.println("finite % infinity = "
                + (5.0 % Double.POSITIVE_INFINITY));
        System.out.println("finite IEEE infinity = "
                + Math.IEEEremainder(5.0, Double.POSITIVE_INFINITY));

        System.out.println("signed zero from %: " + (-4.0 % 2.0));
        System.out.println("signed zero from IEEE: "
                + Math.IEEEremainder(-4.0, 2.0));
        System.out.println("negative zero detected: "
                + (Double.doubleToRawLongBits(
                        Math.IEEEremainder(-4.0, 2.0)) == Long.MIN_VALUE));

        float floatRemainder = 7.0f % 2.0f;
        double ieeeRemainder = Math.IEEEremainder(7.0f, 2.0f);
        System.out.println("float remainder = " + floatRemainder
                + ", IEEE returns double = " + ieeeRemainder);
    }
}
```

Lệnh biên dịch và chạy với JDK 17 trở lên:

```bash
javac --release 17 RemainderComparisonDemo.java
java RemainderComparisonDemo
```

Kết quả stdout:

```text
7.0 % 2.0 = 1.0 | IEEE = -1.0
5.0 % 2.0 = 1.0 | IEEE = 1.0
3.0 % 2.0 = 1.0 | IEEE = -1.0
-7.0 % 2.0 = -1.0 | IEEE = 1.0
7.0 % -2.0 = 1.0 | IEEE = -1.0
-7.0 % -2.0 = -1.0 | IEEE = 1.0
6.0 % 4.0 = 2.0 | IEEE = -2.0
-6.0 % 4.0 = -2.0 | IEEE = 2.0
8.0 % 3.0 = 2.0 | IEEE = -1.0
rint(2.5) = 2.0
round(2.5) = 3
zero divisor with % -> NaN: true
zero divisor with IEEE -> NaN: true
infinite dividend -> NaN: true
finite % infinity = 5.0
finite IEEE infinity = 5.0
signed zero from %: -0.0
signed zero from IEEE: -0.0
negative zero detected: true
float remainder = 1.0, IEEE returns double = -1.0
```

Chương trình cho thấy: các kết quả khác dấu với cùng dữ liệu, quy tắc ties-to-even, xử lý zero divisor/infinity, zero âm và sự khác biệt kiểu trả về khi gọi `Math.IEEEremainder` với `float`.

## 8. Chọn API nào trong bài toán thực tế?

### Trường hợp A — Phần dư theo chia cắt về 0

Chọn **`%`** khi hợp đồng mong muốn dấu của phần dư đi theo số bị chia, gần với cách tính remainder nguyên của Java.

Ví dụ, tính phần dư của độ dài dịch chuyển `-7.0` theo quãng `2.0`:

```java
double offset = -7.0 % 2.0; // -1.0
```

Đây là cách tính hợp lệ nếu logic của bạn cần **phần dư có dấu theo dịch chuyển**. Nhưng nó **không phải** tọa độ index modulo luôn không âm.

### Trường hợp B — Sai lệch có độ lớn gần 0 nhất trong miền chu kỳ

Chọn **`Math.IEEEremainder`** khi muốn một phần dư nằm gần 0 hơn, chẳng hạn trong một quy ước **hiệu pha có dấu** hoặc sai lệch chu kỳ được đo theo khoảng đối xứng xung quanh 0. Với modulus hữu hạn khác 0, độ lớn phần dư không quá nửa modulus.

```java
double angle = 350.0;
double signedRemainder = Math.IEEEremainder(angle, 360.0);
// -10.0, vì chọn thương 1 thay vì 0.
```

Đây có thể là cách thuận tiện để biểu diễn độ lệch góc ngắn nhất quanh điểm 0. Nhưng ở chính **nửa chu kỳ** (±180 độ với chu kỳ 360), kết quả có thể là +180 hoặc −180 tùy thương làm tròn và vị trí ban đầu; hãy quy định rõ muốn khoảng `[-180,180]`, `[-180,180)` hay `(-180,180]` và chuẩn hóa đường biên nếu cần.

`Math.IEEEremainder` cũng không tự bảo đảm giá trị hữu hạn khi đầu vào là NaN/Infinity hoặc chu kỳ bằng 0. Kiểm tra dữ liệu đầu vào tại ranh giới nghiệp vụ.

### Trường hợp C — Index vòng không âm

Nếu đang đánh chỉ số mảng/list, thường nên dùng số **nguyên**, với `Math.floorMod`:

```java
int size = 5;
int index = Math.floorMod(-1, size); // 4
```

`Math.floorMod` không có overload cho `double`. Nếu dữ liệu của bạn thực chất là index nguyên, đừng chuyển sang `double` để dùng `IEEEremainder`.

Với góc hoặc pha chấm động cần chuẩn hóa về `[0,period)`, có thể bắt đầu bằng phép `%` rồi điều chỉnh khi phần dư âm, nhưng phải cân nhắc việc cộng `period` có thể làm tròn ở biên. Ví dụ minh họa cho **đầu vào hữu hạn, chu kỳ dương**, không phải công thức bảo đảm độ chính xác tuyệt đối:

```java
double value = -10.0;
double period = 360.0;
double r = value % period;
if (r < 0.0) r += period;
if (r >= period) r = 0.0; // Chặn trường hợp làm tròn lên đúng period.
System.out.println(r); // 350.0
```

Nếu cần chuẩn hóa góc với quy tắc chính xác tại boundary, viết test rõ cho ±0, bội số chu kỳ, nửa chu kỳ, dữ liệu rất lớn và trường hợp gần đường biên. Không tự áp dụng `Math.abs(value % period)`, vì `Math.abs(-10.0 % 360.0)` là 10.0 chứ **không phải** vị trí tương đương 350.0.

### Trường hợp D — Số tiền hoặc số thập phân chính xác

Nếu bài toán dùng tiền hoặc số thập phân cần quy tắc chính xác, cả `%` trên `double` lẫn `Math.IEEEremainder` đều **không** giải quyết việc nhị phân không biểu diễn chính xác `0.1`.

Có thể dùng `BigDecimal`:

```java
java.math.BigDecimal amount = new java.math.BigDecimal("7.5");
java.math.BigDecimal step = new java.math.BigDecimal("2.0");
java.math.BigDecimal r = amount.remainder(step); // 1.5
```

`BigDecimal.remainder` có hợp đồng riêng dựa trên quotient theo hướng về 0, **không phải** IEEE remainder gần nhất. Nó làm việc theo biểu diễn thập phân chính xác nhưng vẫn cần kiểm tra divisor khác 0 và xác định chính sách scale/precision của phép tính.

## 9. Các lỗi phổ biến

| Nhầm lẫn | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Cho rằng `x % y` và `Math.IEEEremainder(x,y)` là một phép toán | Sai dấu và biên khi thương có phần lẻ | Xác định quy tắc truncate hay nearest-ties-even |
| Cho rằng `IEEEremainder` luôn trả số không âm | `IEEEremainder(7,2) == -1` | Dùng API phù hợp khoảng kết quả nghiệp vụ |
| Nhầm ties-to-even với `Math.round` | Hòa 2.5 được xử lý khác | Hiểu thương IEEE là nearest, ties-even |
| Tự tính `x - Math.rint(x / y) * y` thay phương thức | Phép toán trung gian có thể làm tròn/overflow | Gọi `Math.IEEEremainder` trực tiếp |
| Dùng `IEEEremainder` làm index mảng | Dư có thể âm, đồng thời là `double` | Dùng `Math.floorMod` cho index nguyên |
| Gọi `Math.floorMod` với `double` | Không biên dịch | Dùng overload kiểu nguyên hoặc thiết kế lại chuẩn hóa thực |
| Mong `7.0 % 0.0` ném `ArithmeticException` | Kết quả thực tế là `NaN` | Kiểm tra divisor và `Double.isNaN` |
| Quên số 0 có dấu | `-0.0` có bit dấu âm dù bằng `+0.0` theo `==` | Chỉ kiểm tra raw bits nếu nghiệp vụ cần |
| Dùng `float` để gọi `IEEEremainder` và giả định kết quả `float` | Phương thức trả `double` | Khai báo `double` và cân nhắc chuyển hẹp nếu cần |
| Chọn `%` vì muốn chuẩn hóa góc về `[0,360)` | Số âm vẫn cho phần dư âm | Điều chỉnh có kiểm tra và test đường biên |
| Cho rằng “IEEE” đồng nghĩa với decimal exact | Không thay đổi việc lưu binary64 | Dùng `BigDecimal` nếu bài toán cần thập phân chính xác |
| Đọc `Math.IEEEremainder` như phép chia lấy thương | Đây là **phần dư**, không phải quotient | Sử dụng `/`, `floorDiv` hoặc thuật toán quotient đúng nhu cầu |

## 10. Bộ kiểm thử tối thiểu và câu hỏi ôn tập

Khi thay một phép tính remainder bằng API khác, nên kiểm tra ít nhất các nhóm:

1. **Thương nguyên chẵn/lẻ và trường hợp hòa:** `(5.0,2.0)`, `(7.0,2.0)`, `(6.0,4.0)`.
2. **Dấu của hai toán hạng:** `(-7.0,2.0)`, `(7.0,-2.0)`, `(-7.0,-2.0)`.
3. **Bội số chính xác và zero âm:** `(-4.0,2.0)`, `(+0.0,3.0)`, `(-0.0,3.0)`.
4. **Giá trị đặc biệt:** một toán hạng NaN, dividend Infinity, divisor ±0, divisor Infinity.
5. **Giới hạn thực tế của ứng dụng:** số rất lớn/rất nhỏ, số thập phân nhị phân xấp xỉ, nửa chu kỳ và sát đường biên.

Tự trả lời:

- Vì sao `7.0 % 2.0` bằng 1.0 nhưng `Math.IEEEremainder(7.0,2.0)` bằng −1.0?
- Vì sao `Math.IEEEremainder(5.0,2.0)` lại bằng +1.0? Vai trò của thương chẵn là gì?
- Nếu cần luôn có phần dư trong `[0,n)` với `n` nguyên dương, nên chọn `%`, `IEEEremainder` hay `floorMod`?
- Java xử lý `7.0 % 0.0` khác `7 % 0` như thế nào?
- Vì sao `Math.rint(x/y)` chỉ giúp hiểu quy tắc mà chưa phải một cách tự cài đặt IEEE remainder đáng tin cho mọi `double`?
- Với góc 350 độ và chu kỳ 360 độ, `Math.IEEEremainder` cho kết quả bao nhiêu? Tại sao cần quy ước biên ±180?
- Khi một hàm nhận `float` nhưng gọi `Math.IEEEremainder`, kiểu trả về thực sự là gì?
- Với dữ liệu thập phân chính xác, vì sao chuyển sang `BigDecimal.remainder` cũng là thay đổi **hợp đồng phép toán**, không đơn thuần thay đổi số bit?

## 11. Tài liệu chính thức

- [Java Language Specification, `15.17.3 — Remainder Operator % (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.3): `%` nguyên và dấu phẩy động; quy tắc truncate, dấu của phần dư và các trường hợp đặc biệt.
- [`Math.IEEEremainder` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#IEEEremainder(double,double)): định nghĩa quotient gần nhất, ties-to-even, số 0 có dấu và xử lý NaN/Infinity.
- [`Math.rint` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#rint(double)): làm tròn về số nguyên gần nhất, hòa chọn số chẵn.
- [`Math.floorMod` — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#floorMod(int,int)): phép modulo dành cho kiểu nguyên.

**Kết luận:** `%` và `Math.IEEEremainder` không phải hai cách viết của một phép toán. `%` tính phần dư từ thương **cắt về 0**, nên dấu của kết quả khác 0 theo số bị chia. `Math.IEEEremainder` tính phần dư từ thương nguyên **gần nhất, hòa chọn chẵn**, nên có thể cho dư trái dấu số bị chia. Chọn phép toán theo **khoảng giá trị và chính sách làm tròn** của bài toán; kiểm tra NaN, Infinity, divisor bằng zero và dữ liệu gần biên trước khi đưa vào hệ thống thực tế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-005">Lan truyền NaN và các trường hợp đặc biệt</a>

<details>
<summary>Xem chi tiết</summary>

# Lan truyền NaN và các trường hợp đặc biệt trong phép toán dấu phẩy động Java

Trong Java, `float` (32 bit) và `double` (64 bit) dùng kiểu dấu phẩy động nhị phân theo IEEE 754. Bên cạnh số hữu hạn thông thường, chúng còn có **NaN** (*Not a Number*), **vô cực dương/âm** và **số 0 có dấu**. Các giá trị này tham gia vào phép toán theo những quy tắc xác định: một phép toán có thể trả về `NaN` hoặc `Infinity` mà **không ném ngoại lệ chia cho 0** như phép chia số nguyên.

Trọng tâm bài là **NaN sinh ra từ đâu, lan truyền như thế nào, so sánh ra sao và cách xử lý đúng khi dữ liệu đi qua nhiều phép tính**. Các ví dụ dùng Java 17+ và kiểu `double`; phần lớn quy tắc tương ứng cũng áp dụng cho `float`.

## 1. Phân biệt các giá trị đặc biệt

| Giá trị | Ví dụ Java | Ý nghĩa |
| --- | --- | --- |
| Số hữu hạn bình thường | `1.5`, `-20.0` | Một giá trị số nằm trong miền biểu diễn của kiểu |
| Số hữu hạn dưới chuẩn (*subnormal*) | `Double.MIN_VALUE` | Rất gần 0 nhưng khác 0; độ chính xác tương đối có thể giảm |
| Dương vô cực | `Double.POSITIVE_INFINITY` | Có thể thu được do tràn số hữu hạn hoặc chia dương cho `+0.0` |
| Âm vô cực | `Double.NEGATIVE_INFINITY` | Có thể thu được khi chia âm cho `+0.0` |
| NaN | `Double.NaN` | Kết quả không phải một số có thứ tự thông thường |
| Dương và âm 0 | `+0.0`, `-0.0` | Bằng nhau khi dùng `==` nhưng mang bit dấu khác nhau |

`Float.NaN`, `Float.POSITIVE_INFINITY` và `Float.NEGATIVE_INFINITY` là các hằng tương ứng cho `float`. **`Double.MIN_VALUE` là số dương khác 0 nhỏ nhất có thể biểu diễn**, không phải giá trị âm lớn về độ lớn; giá trị hữu hạn âm có độ lớn tối đa là `-Double.MAX_VALUE`.

NaN không có nghĩa là `null`, giá trị còn thiếu hay ngoại lệ Java. Nó là một **giá trị bit hợp lệ** của `float`/`double`, có thể được lưu trong biến, truyền làm tham số và lan qua phép tính. Nếu dữ liệu nghiệp vụ dùng NaN để biểu thị “thiếu”, đó là **quy ước riêng của ứng dụng**, không phải ý nghĩa bắt buộc của IEEE 754.

## 2. Những phép tính sinh ra NaN

Các phép tính sau trả về NaN với kiểu `double`:

~~~java
double a = 0.0 / 0.0;                           // NaN
double b = Double.POSITIVE_INFINITY
         - Double.POSITIVE_INFINITY;           // NaN
double c = Double.POSITIVE_INFINITY * 0.0;      // NaN
double d = Double.POSITIVE_INFINITY
         / Double.POSITIVE_INFINITY;           // NaN
double e = Math.sqrt(-1.0);                     // NaN
double f = Double.NaN;                          // NaN có sẵn
~~~

Giải thích:

- `0.0 / 0.0` không có một thương số được xác định duy nhất.
- `+Infinity - +Infinity` và `Infinity / Infinity` không thể suy ra kết quả hữu hạn chỉ từ hai giá trị vô cực.
- `Infinity * 0.0` là trường hợp không xác định theo quy tắc phép toán dấu phẩy động.
- `Math.sqrt(-1.0)` nằm ngoài miền số thực của hàm căn bậc hai trong API này.

**Không nhầm với các trường hợp trả về vô cực:**

~~~java
double p = 1.0 / 0.0;   // +Infinity
double n = -1.0 / 0.0;  // -Infinity
double z = -1.0 / -0.0; // +Infinity
~~~

Chia số nguyên `1 / 0` (nếu thực thi với mẫu số nguyên 0) gây `ArithmeticException`, còn chia dấu phẩy động cho 0 tuân theo quy tắc NaN/vô cực. Chú ý kiểu của toán hạng: `1.0 / 0.0` khác với `1 / 0`.

Các trường hợp tràn/thiếu phạm vi cũng đáng lưu ý:

~~~java
double overflow = Double.MAX_VALUE * 2.0; // Infinity
double underflow = Double.MIN_VALUE / 2.0; // 0.0 do quá nhỏ
~~~

Tràn số hữu hạn có thể cho vô cực; kết quả quá nhỏ có thể được làm tròn thành 0 có dấu hoặc một số dưới chuẩn. **Không phải mọi lần tràn hoặc mất chính xác đều sinh NaN.**

## 3. NaN lan truyền qua phép toán như thế nào?

Trong các phép tính số học thông thường (`+`, `-`, `*`, `/`) trên `float` và `double`, nếu một toán hạng là NaN thì kết quả là NaN. NaN có thể đi tiếp qua nhiều bước:

~~~java
double reading = Double.NaN;
double adjusted = reading + 5.0;  // NaN
double scaled = adjusted * 2.0;   // NaN
double average = scaled / 3.0;    // NaN
~~~

Khi kết quả một bước đã thành NaN, cộng thêm một số hữu hạn **không làm kết quả “trở lại bình thường”**. Vì vậy, một dữ liệu đầu vào bất thường có thể làm toàn bộ tổng, trung bình, hệ số hoặc kết quả tính toán phía sau thành NaN.

Tuy nhiên, **đừng khẳng định mọi API nhận NaN đều luôn trả NaN**: các hàm thư viện có quy tắc đặc biệt riêng. Chẳng hạn:

~~~java
double result = Math.pow(Double.NaN, 0.0); // 1.0
~~~

Số mũ bằng 0 là trường hợp được `Math.pow` quy định trả `1.0`, kể cả khi cơ số là NaN. Ngược lại, `Math.min(Double.NaN, 2.0)` trả NaN. Hãy đọc hợp đồng của API thay vì suy diễn một quy tắc truyền NaN cho mọi hàm.

Một chương trình nên phát hiện NaN tại **điểm nhập dữ liệu hoặc trước phép tính có ràng buộc giá trị hữu hạn**, để tránh làm mất dấu vị trí đã tạo ra vấn đề.

## 4. So sánh NaN: không dùng `==` để nhận biết

NaN có hành vi so sánh **unordered** (*không có thứ tự thông thường*) trong các phép so sánh primitive. Dù hai biểu thức cùng là NaN:

~~~java
double x = Double.NaN;

boolean equal = x == x;      // false
boolean different = x != x;  // true
boolean less = x < 1.0;      // false
boolean greater = x > 1.0;   // false
boolean atMost = x <= 1.0;   // false
boolean atLeast = x >= 1.0;  // false
~~~

Đối với NaN, `==`, `<`, `>`, `<=` và `>=` đều trả `false`; `!=` trả `true`. Điều này cũng áp dụng khi toán hạng còn lại là NaN. **Đừng thay kiểm tra NaN bằng `x == Double.NaN`:** biểu thức này luôn là `false`.

Cách kiểm tra chuẩn:

~~~java
double x = Double.NaN;

if (Double.isNaN(x)) {
    System.out.println("Dữ liệu không phải số hợp lệ");
}
~~~

Với `float`, dùng `Float.isNaN(value)`. Mẹo kiểm tra `x != x` có thể hoạt động do quy tắc IEEE 754 nhưng khó đọc, nên ưu tiên `Double.isNaN` hoặc `Float.isNaN`.

## 5. Vô cực và số 0 có dấu: không được bỏ qua

Vô cực **không đồng nghĩa NaN**: nó có thứ tự đối với các số hữu hạn và có thể là kết quả hợp lệ theo quy tắc dấu phẩy động. Ví dụ `Double.POSITIVE_INFINITY > Double.MAX_VALUE` là `true`; `Double.NEGATIVE_INFINITY < -Double.MAX_VALUE` cũng là `true`.

Các phép tính kết hợp vô cực có kết quả khác nhau:

| Biểu thức | Kết quả |
| --- | --- |
| `+Infinity + 5.0` | `Infinity` |
| `-Infinity - 5.0` | `-Infinity` |
| `+Infinity + -Infinity` | `NaN` |
| `+Infinity * -2.0` | `-Infinity` |
| `+Infinity * 0.0` | `NaN` |
| `1.0 / +0.0` | `Infinity` |
| `1.0 / -0.0` | `-Infinity` |
| `-1.0 / -0.0` | `Infinity` |

Số 0 có dấu giữ thông tin dấu trong phép chia và trong một số API:

~~~java
System.out.println(+0.0 == -0.0);            // true
System.out.println(1.0 / -0.0);               // -Infinity
System.out.println(Double.compare(+0.0, -0.0)); // 1
~~~

So sánh primitive `==` xem `+0.0` và `-0.0` bằng nhau, trong khi `Double.compare` phân biệt được bit dấu. Nếu thuật toán sử dụng dấu của 0 (chẳng hạn khi tính nghịch đảo), đừng chuẩn hóa `-0.0` thành `+0.0` một cách tùy tiện.

## 6. Phân biệt `Double.isNaN`, `isInfinite` và `isFinite`

Các API hữu ích:

| API | NaN | ±Infinity | Số hữu hạn (kể cả ±0 và subnormal) |
| --- | --- | --- | --- |
| `Double.isNaN(x)` | `true` | `false` | `false` |
| `Double.isInfinite(x)` | `false` | `true` | `false` |
| `Double.isFinite(x)` | `false` | `false` | `true` |

Với dữ liệu nghiệp vụ **bắt buộc hữu hạn**, kiểm tra `Double.isFinite(value)` ngăn cả NaN lẫn vô cực đi sâu vào phép tính:

~~~java
static double requireFinite(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Expected finite value");
    }
    return value;
}
~~~

Ngược lại, trong một số lĩnh vực như tính toán khoa học, vô cực hoặc NaN có thể là tín hiệu có ích cần giữ để chẩn đoán. Chỉ loại bỏ hoặc chuyển đổi chúng khi **hợp đồng dữ liệu** quy định rõ.

## 7. So sánh primitive khác với `Double.compare` và `Double.equals`

Java cung cấp một thứ tự được định nghĩa bởi `Double.compare(a, b)` để hỗ trợ sắp xếp/collections. Theo thứ tự này:

- `-0.0` **nhỏ hơn** `+0.0`.
- NaN được xem **lớn hơn mọi giá trị khác**, kể cả `+Infinity`.
- Các NaN được xem **bằng nhau** theo `Double.compare`.

Vì thế:

~~~java
Double.compare(+0.0, -0.0); // 1
Double.compare(Double.NaN, Double.POSITIVE_INFINITY); // 1
Double.compare(Double.NaN, Double.NaN); // 0
~~~

Trong khi `Double.NaN == Double.NaN` là `false`, lời gọi `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` lại là `true`. Ngược lại, hai wrapper `Double.valueOf(+0.0)` và `Double.valueOf(-0.0)` không bằng nhau theo `equals`. Quy ước này giúp `Double.compare` và `Double.equals` nhất quán với nhau trong các collections và phép sắp xếp.

Đó là khác biệt **giữa quy tắc toán tử primitive và hợp đồng so sánh của wrapper**. Không dùng `Double.compare` để suy ra rằng NaN bỗng trở thành một số có thứ tự theo phép so sánh primitive.

## 8. Ví dụ Java 17+ hoàn chỉnh và kết quả chạy

Lưu tệp `FloatingSpecialCases.java`:

~~~java
public class FloatingSpecialCases {
    public static void main(String[] args) {
        double nan = Double.NaN;
        double infinity = Double.POSITIVE_INFINITY;
        double negZero = -0.0;

        System.out.println("nan + 5 = " + (nan + 5.0));
        System.out.println("0.0 / 0.0 = " + (0.0 / 0.0));
        System.out.println("1.0 / 0.0 = " + (1.0 / 0.0));
        System.out.println("-1.0 / 0.0 = " + (-1.0 / 0.0));
        System.out.println("infinity - infinity = "
                + (infinity - infinity));
        System.out.println("infinity * 0.0 = " + (infinity * 0.0));

        System.out.println("NaN == NaN = " + (nan == nan));
        System.out.println("NaN != NaN = " + (nan != nan));
        System.out.println("NaN < 1 = " + (nan < 1.0));
        System.out.println("isNaN = " + Double.isNaN(nan));
        System.out.println("isFinite(1.5) = " + Double.isFinite(1.5));
        System.out.println("isInfinite = "
                + Double.isInfinite(infinity));

        System.out.println("+0.0 == -0.0 = " + (0.0 == negZero));
        System.out.println("1.0 / -0.0 = " + (1.0 / negZero));
        System.out.println("Double.compare(+0,-0) = "
                + Double.compare(+0.0, -0.0));
        System.out.println("Double.compare(NaN,+Inf) = "
                + Double.compare(nan, infinity));
        System.out.println("Double.NaN.equals = "
                + Double.valueOf(nan).equals(Double.valueOf(nan)));
        System.out.println("Double(+0).equals(Double(-0)) = "
                + Double.valueOf(0.0).equals(Double.valueOf(-0.0)));
        System.out.println("sqrt(-1.0) = " + Math.sqrt(-1.0));
        System.out.println("Math.min(NaN, 2) = " + Math.min(nan, 2.0));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac FloatingSpecialCases.java
java FloatingSpecialCases
~~~

Stdout chính xác:

~~~text
nan + 5 = NaN
0.0 / 0.0 = NaN
1.0 / 0.0 = Infinity
-1.0 / 0.0 = -Infinity
infinity - infinity = NaN
infinity * 0.0 = NaN
NaN == NaN = false
NaN != NaN = true
NaN < 1 = false
isNaN = true
isFinite(1.5) = true
isInfinite = true
+0.0 == -0.0 = true
1.0 / -0.0 = -Infinity
Double.compare(+0,-0) = 1
Double.compare(NaN,+Inf) = 1
Double.NaN.equals = true
Double(+0).equals(Double(-0)) = false
sqrt(-1.0) = NaN
Math.min(NaN, 2) = NaN
~~~

Mỗi dòng kiểm tra một đặc tính: nguồn tạo NaN, lan truyền, so sánh không có thứ tự, nhận diện giá trị không hữu hạn, dấu của 0 và sự khác nhau giữa toán tử `==` với comparator/wrapper.

## 9. Khi NaN xuất hiện trong tập dữ liệu

Giả sử ứng dụng cần tính tổng ba giá trị `2.0`, `NaN` và `3.0`. Tổng thông thường sẽ là NaN:

~~~java
double sum = java.util.stream.DoubleStream
        .of(2.0, Double.NaN, 3.0)
        .sum(); // NaN
~~~

Nếu yêu cầu nghiệp vụ quy định rõ **chỉ cộng các giá trị hữu hạn**, có thể lọc trước:

~~~java
double finiteSum = java.util.stream.DoubleStream
        .of(2.0, Double.NaN, 3.0)
        .filter(Double::isFinite)
        .sum(); // 5.0
~~~

Hai đoạn này minh họa hai **chính sách khác nhau**, không phải hai cách tính thay thế tùy ý. Việc bỏ NaN khỏi tập dữ liệu có thể thay đổi ý nghĩa trung bình, mẫu số, chất lượng thống kê và khả năng phát hiện lỗi cảm biến/đầu vào. Khi cần điều tra, hãy ghi nhận nguồn dữ liệu bất thường thay vì âm thầm lọc.

Một cách kiểm tra tại ranh giới nhận đầu vào:

~~~java
static double normalizedRate(double numerator, double denominator) {
    if (!Double.isFinite(numerator) || !Double.isFinite(denominator)) {
        throw new IllegalArgumentException("Inputs must be finite");
    }
    if (denominator == 0.0) {
        throw new IllegalArgumentException("Denominator must be nonzero");
    }
    double result = numerator / denominator;
    if (!Double.isFinite(result)) {
        throw new ArithmeticException("Non-finite quotient");
    }
    return result;
}
~~~

Đây là **đoạn phương thức minh họa**, có thể đặt vào một lớp Java; không phải chương trình chạy độc lập. Nó từ chối đầu vào không hữu hạn và số chia ±0, đồng thời phát hiện kết quả không hữu hạn do tính toán. Nếu nghiệp vụ cho phép vô cực/NaN, chính sách cần đổi tương ứng.

## 10. Các lỗi phổ biến cần tránh

1. **Dùng `x == Double.NaN` hoặc `x != Double.NaN` để phân loại NaN.** Cái đầu luôn sai, cái sau luôn đúng; dùng `Double.isNaN(x)`.
2. **Chỉ kiểm tra NaN nhưng quên vô cực.** `!Double.isNaN(x)` vẫn nhận `Infinity`. Nếu yêu cầu số hữu hạn, dùng `Double.isFinite(x)`.
3. **Cho rằng phép chia `double` cho 0 ném `ArithmeticException`.** `1.0 / 0.0` trả vô cực và `0.0 / 0.0` trả NaN; kết quả không hữu hạn cần được xử lý theo hợp đồng API.
4. **Dùng `==` để kỳ vọng hành vi giống `Double.equals`.** Đặc biệt với NaN và số 0 có dấu, hai kiểu so sánh khác nhau.
5. **Tin rằng có thể “sửa” NaN bằng cộng 0 hoặc nhân 1.** Các phép số học thông thường vẫn lan truyền NaN; cần giải quyết nguồn sinh NaN hoặc thay đổi chính sách dữ liệu có chủ đích.
6. **Cho rằng mọi kết quả số học bất thường đều là NaN.** Tràn có thể thành vô cực; thiếu phạm vi có thể thành 0 hoặc số dưới chuẩn.
7. **Nói mọi hàm toán học đều truyền NaN không điều kiện.** Một số trường hợp đặc biệt được API định nghĩa khác, như `Math.pow(Double.NaN, 0.0)` trả `1.0`.
8. **Không phân biệt phép tính nội bộ với biểu diễn/serialize dữ liệu.** Hệ thống JSON, cơ sở dữ liệu hoặc giao thức có thể không chấp nhận NaN/vô cực như các số thông thường; cần xác định cách biểu diễn ở ranh giới tích hợp.

## 11. Tự kiểm tra

1. `0.0 / 0.0`, `-1.0 / 0.0` và `Double.POSITIVE_INFINITY * 0.0` lần lượt trả gì?
2. Có đúng rằng `Double.NaN > Double.POSITIVE_INFINITY` không? `Double.compare(Double.NaN, Double.POSITIVE_INFINITY)` có trả số dương không?
3. Nếu một tổng có một phần tử NaN thì tiếp tục cộng `100.0` có cho tổng hữu hạn không?
4. Vì sao `1.0 / -0.0` là âm vô cực dù `+0.0 == -0.0` là `true`?
5. Cần hàm nào để từ chối đồng thời NaN, `+Infinity` và `-Infinity`?

**Đáp án:** (1) NaN, `-Infinity`, NaN. (2) Toán tử `>` trả `false`; `Double.compare` trả số dương. (3) Không, tổng tiếp tục là NaN trong phép cộng thông thường. (4) IEEE 754 giữ dấu của 0 để xác định dấu của kết quả phép chia, còn primitive equality xem hai số 0 bằng nhau. (5) `Double.isFinite(value)` phải là `true` mới chấp nhận.

## Tài liệu tham khảo

- [Java Language Specification `4.2.3 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3).
- [Java Language Specification `15.20–15.21 — Comparisons and Equality](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.20).
- [Java SE API — Double](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html): `isNaN`, `isFinite`, `isInfinite`, `compare`, `equals`.
- [Java SE API — Math](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html): `sqrt`, `pow`, `min` và quy tắc trường hợp đặc biệt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-006">Tác động của signed zero tới phép chia và hàm toán học</a>

<details>
<summary>Xem chi tiết</summary>

# Tác động của signed zero tới phép chia và hàm toán học trong Java

## 1. Mục tiêu và câu hỏi mở đầu

Java có hai giá trị dấu phẩy động biểu diễn **số không**: `+0.0` (positive zero) và `-0.0` (negative zero). Hai giá trị này **so sánh bằng nhau với toán tử `==`**, nhưng **không thể thay thế nhau trong mọi phép tính**. Dấu của số không có thể quyết định dấu của vô cực khi chia, dấu của kết quả một số hàm toán học, cách sắp xếp bằng `Double.compare` và dữ liệu thô khi tuần tự hóa.

Hãy nhìn hai câu lệnh:

```java
System.out.println(+0.0 == -0.0); // true
System.out.println(1.0 / -0.0);    // -Infinity
```

Không có mâu thuẫn: phép `==` trên primitive so sánh theo quy tắc số học floating point; phép chia xét cả dấu của hai toán hạng. Đây là một chủ đề quan trọng trong **Java Core — Numbers / Phép toán dấu phẩy động**.

Sau bài này, bạn sẽ:

1. Hiểu mẫu bit của `+0.0` và `-0.0` trong `float`/`double`.
2. Dự đoán kết quả `/` khi mẫu số hoặc tử số là signed zero; phân biệt `Infinity`, `-Infinity` và `NaN`.
3. Phân tích tác động của dấu zero đối với `Math.abs`, `Math.signum`, `Math.copySign`, `Math.min`, `Math.max`, `Math.sqrt`, `Math.ceil`, `Math.floor`, `Math.rint`, `Math.atan2` và `Math.pow`.
4. Phân biệt so sánh primitive `==` với `Double.compare`/`Double.equals` và những ảnh hưởng lên collection.
5. Đọc đúng raw bits, kiểm tra sign của zero, bảo toàn dấu khi thật sự cần; không nhầm với epsilon hoặc chuyển kiểu số nguyên.

Các ví dụ **chạy với Java 17+** và không cần thư viện bên ngoài JDK.

## 2. Hai số không trong IEEE 754 binary32/binary64

Kiểu `float` của Java dùng khuôn dạng **binary32** gồm 1 bit dấu, 8 bit mũ và 23 bit fraction. Kiểu `double` dùng **binary64** gồm 1 bit dấu, 11 bit mũ và 52 bit fraction.

Số không ở cả hai khuôn dạng có trường **exponent toàn 0 và fraction toàn 0**; trường sign quyết định đó là `+0.0` hay `-0.0`.

| Giá trị | Kiểu | Mẫu bit hệ hex |
| --- | --- | --- |
| `+0.0f` | `float` | `0x00000000` |
| `-0.0f` | `float` | `0x80000000` |
| `+0.0` | `double` | `0x0000000000000000` |
| `-0.0` | `double` | `0x8000000000000000` |

Giá trị toán học “độ lớn bằng 0” được chia thành hai mẫu bit để giúp phép toán dấu phẩy động lưu lại thông tin dấu hữu ích, nhất là khi một kết quả rất nhỏ **underflow** về zero hoặc khi chia cho zero.

Trong Java, `-0.0` là một biểu thức dùng toán tử âm một ngôi tác động lên literal `0.0`; kết quả là zero âm. `Double.toString(-0.0)` in `-0.0`, còn `Double.toString(+0.0)` in `0.0`. Điều này cho thấy cách in mặc định bảo toàn dấu, dù `==` coi hai giá trị bằng nhau.

### 2.1. Tự kiểm tra các bit

```java
double plus = +0.0;
double minus = -0.0;

System.out.printf("%016X%n", Double.doubleToRawLongBits(plus));
System.out.printf("%016X%n", Double.doubleToRawLongBits(minus));
```

Stdout:

```text
0000000000000000
8000000000000000
```

Đây là **mảnh mã** cần đặt trong một phương thức Java trước khi chạy. `Double.doubleToRawLongBits` trả mẫu 64 bit, khác hoàn toàn cast `(long) minus` — cast số thực sang số nguyên cho `0L`, không cho “raw signed-zero bits”.

Có API tương ứng cho `float`: `Float.floatToRawIntBits`. Nếu đang kiểm tra NaN, cần biết phương thức raw có hợp đồng khác các phương thức `doubleToLongBits`/`floatToIntBits` vốn chuẩn hóa nhiều mẫu NaN. Với **zero**, cả raw và non-raw đều cho mẫu dấu đúng.

## 3. `==` coi +0.0 và -0.0 bằng nhau, nhưng bit khác nhau

```java
double positive = +0.0;
double negative = -0.0;

System.out.println(positive == negative); // true
System.out.println(positive != negative); // false
System.out.println(positive < negative);  // false
System.out.println(positive > negative);  // false
```

Các toán tử so sánh primitive không xếp `-0.0` nhỏ hơn `+0.0`. Tuy nhiên, có ít nhất ba cách hỏi **khác nhau** về số zero:

| Câu hỏi | Biểu thức | Với `-0.0` so với `+0.0` |
| --- | --- | --- |
| Hai giá trị có bằng theo phép `==` số học không? | `a == b` | `true` |
| Hai mã bit IEEE 754 có giống hệt không? | `Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b)` | `false` |
| Trong thứ tự đầy đủ dành cho wrapper, số nào nhỏ hơn? | `Double.compare(a, b)` | `-0.0` đứng trước `+0.0` |

**Hệ quả:** nếu ứng dụng chỉ hỏi “giá trị có bằng zero về số học không?”, dùng `value == 0.0` là hợp lý và khớp cho cả hai dấu. Nếu ứng dụng muốn **phân biệt dấu zero**, phép `==` không đủ. Ngược lại, nếu mọi zero phải được coi tương đương ở mức nghiệp vụ, dùng một comparator phân biệt hai dấu có thể không phù hợp.

## 4. Phép chia với zero: dấu của kết quả không thể bỏ qua

Quy tắc dấu của phép chia dấu phẩy động: **cùng dấu thì kết quả dương, khác dấu thì kết quả âm**, ngoại trừ các trường hợp cho NaN. Quy tắc này áp dụng cho zero và infinity theo đặc tả IEEE 754 của Java.

### 4.1. Một số hữu hạn khác 0 chia cho ±0.0

| Biểu thức | Kết quả |
| --- | --- |
| `1.0 / +0.0` | `Infinity` |
| `1.0 / -0.0` | `-Infinity` |
| `-1.0 / +0.0` | `-Infinity` |
| `-1.0 / -0.0` | `Infinity` |

Dù `+0.0 == -0.0` là `true`, **không được** từ đó suy ra `1.0 / +0.0 == 1.0 / -0.0`. Hai vế tương ứng là dương vô cực và âm vô cực.

```java
System.out.println(1.0 / +0.0);
System.out.println(1.0 / -0.0);
System.out.println(-1.0 / +0.0);
System.out.println(-1.0 / -0.0);
```

Stdout:

```text
Infinity
-Infinity
-Infinity
Infinity
```

Đó là **quy tắc cho kiểu `double`**, không phải “chia nguyên”. Nếu cả hai toán hạng là `int`/`long`, chia cho số `0` ném `ArithmeticException` ở runtime khi phép chia được thực hiện.

### 4.2. Zero làm tử số, mẫu số hữu hạn khác 0

| Biểu thức | Kết quả |
| --- | --- |
| `+0.0 / 2.0` | `0.0` |
| `-0.0 / 2.0` | `-0.0` |
| `+0.0 / -2.0` | `-0.0` |
| `-0.0 / -2.0` | `0.0` |

Kết quả vẫn bằng zero về giá trị số học, nhưng **dấu của zero** tuân theo dấu phép chia. Nếu đưa kết quả đó vào một phép chia tiếp theo, dấu có thể thay đổi vô cực:

```java
double tinyResult = +0.0 / -2.0; // -0.0
double reciprocal = 1.0 / tinyResult; // -Infinity
```

Đây là ví dụ cho thấy việc âm thầm thay mọi giá trị zero bằng `+0.0` có thể làm thay đổi hành vi về sau.

### 4.3. Zero chia zero và vô cực

Các quy tắc biên khác cần nhớ:

- `+0.0 / +0.0`, `+0.0 / -0.0`, `-0.0 / +0.0` và `-0.0 / -0.0` đều cho **`NaN`**.
- Số hữu hạn chia cho `+Infinity` hoặc `-Infinity` tạo **signed zero**, dấu theo hai toán hạng nếu không có NaN.
- `Infinity / Infinity` và `-Infinity / Infinity` cho `NaN`.
- Nếu một toán hạng là `NaN`, kết quả phép chia là `NaN`.

Ví dụ:

```java
System.out.println(0.0 / -0.0);                 // NaN
System.out.println(3.0 / Double.NEGATIVE_INFINITY); // -0.0
System.out.println(Double.isNaN(0.0 / 0.0));     // true
```

Dùng `Double.isNaN(result)` hoặc `Double.isInfinite(result)` khi cần **kiểm tra trạng thái**; không kiểm tra NaN bằng `result == Double.NaN` vì NaN không bằng chính nó theo primitive `==`.

### 4.4. Phân biệt `1 / 0` với `1.0 / 0.0`

`1 / 0` là phép chia nguyên cho zero (một biểu thức hằng như vậy có thể là lỗi biên dịch trong các ngữ cảnh constant expression; phép chia nguyên cho zero thực thi tại runtime ném `ArithmeticException`). `1.0 / 0.0` là phép chia dấu phẩy động trả `Infinity`.

Các phép chia dấu phẩy động theo JLS không ném runtime exception chỉ vì chia cho zero, overflow, underflow hay mất thông tin. Tuy nhiên, chương trình **vẫn phải tự quyết định** có chấp nhận `Infinity` hoặc `NaN` làm kết quả nghiệp vụ hay không.

## 5. Số zero sinh ra qua cộng, trừ và nhân

Signed zero không chỉ đến từ literal `-0.0`. Phép nhân một số không với số âm có thể đổi dấu:

```java
System.out.println(-0.0 * 2.0);  // -0.0
System.out.println(-0.0 * -2.0); // 0.0
System.out.println(0.0 * -2.0);  // -0.0
```

Cộng trừ có các quy tắc dấu zero riêng. Ví dụ theo chế độ làm tròn mặc định Java:

```java
System.out.println(-0.0 + +0.0); // 0.0
System.out.println(-0.0 + -0.0); // -0.0
```

Điều này dẫn đến một cảnh báo quan trọng: một phép biến đổi biểu thức mà ta thường cho là hiển nhiên trong **đại số số thực** có thể không bảo toàn **mọi bit kết quả floating-point**. Chẳng hạn thêm `+0.0` vào một biểu thức có thể đổi `-0.0` thành `+0.0`.

**Không suy ra phép cộng số thực luôn kết hợp**: làm tròn, overflow, underflow và signed zero đều có thể tạo khác biệt giữa những biểu thức được nhóm khác nhau. Khi yêu cầu kết quả bit chính xác (reproducible), cần kiểm tra các biểu thức được tối ưu hay biến đổi và tuân thủ ngữ nghĩa Java/JLS.

## 6. `Math.abs`, `Math.signum` và `Math.copySign`

Ba phương thức rất dễ bị nhầm vì tên đều liên quan đến dấu:

| Biểu thức `double` | Kết quả | Ý nghĩa |
| --- | --- | --- |
| `Math.abs(-0.0)` | `0.0` | Trả độ lớn không âm, bỏ dấu âm |
| `Math.signum(-0.0)` | `-0.0` | Giữ signed zero |
| `Math.signum(+0.0)` | `0.0` | Giữ signed zero |
| `Math.copySign(2.5, -0.0)` | `-2.5` | Dùng độ lớn toán hạng đầu, dấu toán hạng thứ hai |
| `Math.copySign(0.0, -1.0)` | `-0.0` | Có thể tạo zero âm |
| `Math.copySign(-0.0, 1.0)` | `0.0` | Có thể tạo zero dương |

### 6.1. `Math.abs`: loại bỏ dấu zero âm

`Math.abs(-0.0)` là `+0.0`. Nếu bạn muốn **chuẩn hóa** mọi zero thành dương để hiển thị hoặc áp dụng quy ước nghiệp vụ, đây là một cách; nhưng nó cũng biến số âm hữu hạn thành dương nên không phù hợp khi phải **chỉ thay đổi zero**.

Nếu chỉ muốn chuẩn hóa hai loại zero:

```java
static double normalizeZero(double value) {
    return value == 0.0 ? 0.0 : value;
}
```

Đây là **mảnh phương thức**, không phải file Java độc lập. Nó giữ các giá trị khác zero, kể cả `NaN` và infinity, nhưng biến `-0.0` thành `+0.0`. Chỉ áp dụng khi **đúng yêu cầu**: chuẩn hóa zero có thể làm đổi dấu của kết quả chia về sau.

### 6.2. `Math.signum`: không làm mất dấu của zero

`Math.signum(-0.0)` trả `-0.0`, không phải `0.0` hay số nguyên `-1`. `Math.signum(+0.0)` trả `+0.0`. Với số hữu hạn khác zero, `signum` trả ±1.0; với NaN, trả NaN.

Nếu bạn đang tìm “giá trị dấu là -1, 0, +1” theo số học nhưng phải phân biệt zero âm, `signum` vẫn trả **floating-point signed zero**. Muốn mã `int` riêng cho zero âm, cần thiết kế quy ước tường minh và kiểm tra raw bits.

### 6.3. `Math.copySign`: sao chép bit dấu một cách có chủ đích

`Math.copySign(magnitude, sign)` dùng **độ lớn của đối số thứ nhất** và **dấu của đối số thứ hai**. Với đầu vào là zero, nó có thể bảo toàn/tạo zero âm. Ví dụ:

```java
double negativeMagnitude = Math.copySign(5.0, -0.0); // -5.0
double negativeZero = Math.copySign(0.0, -3.0);       // -0.0
```

`Math.copySign` không giống `Math.signum`. Với NaN, chi tiết của bit dấu có thể phụ thuộc API/hợp đồng từng phương thức, vì vậy hãy đọc tài liệu `Math`/`StrictMath` của phiên bản đích nếu có yêu cầu xử lý NaN payload/sign cụ thể.

## 7. `Math.min` và `Math.max` phân biệt signed zero

Đây là trường hợp dễ gây bất ngờ: mặc dù `-0.0 == +0.0` là `true`, `Math.min` và `Math.max` trên `double`/`float` **xét zero âm nhỏ hơn zero dương**.

```java
System.out.println(Math.min(-0.0, +0.0)); // -0.0
System.out.println(Math.max(-0.0, +0.0)); // 0.0
```

Dù đổi thứ tự hai đối số, cùng cặp ±0 sẽ vẫn cho `-0.0` với `min` và `+0.0` với `max`. Các overload `int`/`long` không có khái niệm signed zero riêng.

| Phương thức | Với -0.0 và +0.0 | Với một đối số NaN |
| --- | --- | --- |
| `Math.min` (`double`/`float`) | Trả `-0.0` | Trả NaN |
| `Math.max` (`double`/`float`) | Trả `+0.0` | Trả NaN |

Do đó, nếu một thuật toán gom giá trị nhỏ nhất/lớn nhất qua nhiều bước, hãy nghĩ đến **dấu zero trong dữ liệu**, đặc biệt khi sau đó tính nghịch đảo hoặc xuất raw bits.

**Không thay `Math.min(a,b)` một cách máy móc bằng `a < b ? a : b`** khi cần giữ đầy đủ hợp đồng IEEE 754, vì phép so sánh primitive không phân biệt signed zero và có quy tắc khác với NaN. Cũng không nên giả định mọi phép viết min/max tùy biến tương đương API chuẩn.

## 8. `Math.sqrt`, `Math.ceil`, `Math.floor`, `Math.rint` và signed zero

Các phương thức làm việc với số thực có thể nhận/trả zero âm:

| Biểu thức | Kết quả |
| --- | --- |
| `Math.sqrt(-0.0)` | `-0.0` |
| `Math.cbrt(-0.0)` | `-0.0` |
| `Math.ceil(-0.5)` | `-0.0` |
| `Math.floor(-0.0)` | `-0.0` |
| `Math.rint(-0.4)` | `-0.0` |
| `Math.abs(-0.0)` | `+0.0` |

### 8.1. Căn bậc hai của zero âm

`Math.sqrt(-0.0)` trả **`-0.0`**, dù người học đôi khi cho rằng căn bậc hai không thể là số âm. Kết quả zero âm được giữ theo hợp đồng floating point của phương thức, không phải “căn bậc hai của một số âm hữu hạn”.

Phân biệt rõ:

- `Math.sqrt(-0.0)` → `-0.0`.
- `Math.sqrt(-1.0)` → `NaN`.

Cả `+0.0` và `-0.0` đều là zero theo `==`, nhưng phương thức sqrt có thể phân biệt tại mức bit đầu ra.

### 8.2. Làm tròn gần zero từ phía âm

`Math.ceil(-0.5)` trả `-0.0`: trần toán học của `-0.5` là zero, nhưng dấu kết quả là âm theo hợp đồng `ceil`. `Math.rint(-0.4)` cũng có thể trả `-0.0` khi làm tròn về số nguyên gần nhất được biểu diễn ở kiểu `double`.

`Math.floor(-0.0)` bảo toàn `-0.0`. Khi áp dụng `Math.floor(-0.5)` thì kết quả lại là `-1.0`; không đánh đồng `floor` và `ceil`.

**Bẫy chuyển kiểu:** nếu cast `(int) Math.ceil(-0.5)`, giá trị cuối trở thành `0` kiểu `int` và không còn bit dấu zero. Khi cần lưu nguyên bản biểu diễn số thực, không nên chuyển qua kiểu nguyên.

### 8.3. Một số hàm luôn không âm vẫn có thể làm mất dấu

`Math.abs` cố ý trả độ lớn. Các phương thức như `Math.hypot` mô tả độ dài không âm cũng không thích hợp để giữ dấu zero từ một trục đầu vào. Hãy đọc hợp đồng **từng hàm**, không suy từ tên gọi chung “hàm toán học”.

## 9. `Math.atan2`: dấu zero quyết định góc ở đường biên

`Math.atan2(y, x)` trả góc tính bằng radian khi xử lý cặp tọa độ, và có các quy tắc đặc biệt đối với zero có dấu. Trong đó:

- `Math.atan2(+0.0, -1.0)` = `+π`.
- `Math.atan2(-0.0, -1.0)` = `-π`.

Ví dụ:

```java
System.out.println(Math.atan2(+0.0, -1.0));
System.out.println(Math.atan2(-0.0, -1.0));
```

Stdout:

```text
3.141592653589793
-3.141592653589793
```

Điều này có ích khi làm hình học, đồ họa, biểu diễn hướng, miền góc từ `-π` đến `+π`. Cùng tọa độ có `y` **bằng zero theo `==`**, nhưng dấu zero giúp `atan2` lựa chọn **phía của nhánh góc (branch cut)**.

Đừng tự thay `atan2(y,x)` bằng `atan(y/x)` khi cần xử lý góc đầy đủ: cách đó mất thông tin về góc phần tư, có thể sinh vô cực/NaN tại `x = 0`, và không tương đương tại nhiều trường hợp đặc biệt.

### 9.1. Các tình huống tọa độ thường gặp

Với `x > 0`:

- `Math.atan2(+0.0, x)` trả `+0.0`.
- `Math.atan2(-0.0, x)` trả `-0.0`.

Với `x < 0`:

- `Math.atan2(+0.0, x)` trả `+π`.
- `Math.atan2(-0.0, x)` trả `-π`.

Các phép lượng giác khác có hợp đồng riêng (ví dụ `Math.sin(-0.0)` trả `-0.0`), nhưng không nên suy rằng **mọi** hàm đều bảo toàn dấu zero: cần xem Java SE `Math` hoặc `StrictMath` của bản JDK đang hỗ trợ.

## 10. `Math.pow` và dấu zero theo số mũ

Một số trường hợp của `Math.pow` phụ thuộc dấu zero và số mũ, đặc biệt với **số mũ nguyên lẻ hay chẵn**:

| Biểu thức | Kết quả |
| --- | --- |
| `Math.pow(-0.0, 3.0)` | `-0.0` |
| `Math.pow(-0.0, 2.0)` | `+0.0` |
| `Math.pow(-0.0, -3.0)` | `-Infinity` |
| `Math.pow(-0.0, -2.0)` | `Infinity` |

Diễn giải: dấu âm của cơ số zero được giữ với lũy thừa nguyên lẻ (khi kết quả còn zero hoặc vô cực); với số mũ nguyên chẵn kết quả có dấu dương. Điều này giống trực giác quy tắc dấu của phép nhân/chia, nhưng `Math.pow` có nhiều nhánh đặc biệt cho NaN, infinity và các số mũ không nguyên.

Khi cần bảo đảm hành vi tại ranh giới, hãy kiểm tra **giá trị cụ thể và contract**. Không thay `Math.pow` bằng phép nhân thủ công hoặc ngược lại rồi mặc nhiên khẳng định toàn bộ trường hợp biên giữ nguyên mẫu bit.

## 11. `Math.nextUp` và `Math.nextDown` đi qua vùng zero

`Math.nextUp(x)` trả giá trị floating-point **biểu diễn được kế tiếp theo hướng +∞**; `Math.nextDown(x)` trả giá trị kế tiếp theo hướng −∞.

Tại zero:

```java
System.out.println(Math.nextUp(-0.0) == Double.MIN_VALUE);
System.out.println(Math.nextDown(+0.0) == -Double.MIN_VALUE);
```

Stdout:

```text
true
true
```

`Double.MIN_VALUE` là số **dương khác zero nhỏ nhất** của binary64 (subnormal), chứ không phải số âm hữu hạn nhỏ nhất. Hai phép nextUp/nextDown phản ánh thứ tự số biểu diễn được **theo giá trị**: `+0.0` và `-0.0` so sánh bằng số học, nhưng có hai bit patterns riêng.

Đừng nhầm kết quả khi áp dụng nextUp/nextDown với quan hệ `Double.compare` trên các zero; các API này có mục tiêu khác nhau. Nếu muốn so sánh raw bits của hai zero, dùng API bit chứ không đi vòng qua `nextUp`.

## 12. `Double.compare` và `Double.equals` khác `==`

So sánh primitive `==`:

```java
System.out.println(-0.0 == +0.0); // true
```

So sánh theo **thứ tự đầy đủ** của wrapper:

```java
System.out.println(Double.compare(-0.0, +0.0)); // -1
System.out.println(Double.valueOf(-0.0)
        .equals(Double.valueOf(+0.0))); // false
```

`Double.compare` đặt `-0.0` thấp hơn `+0.0`. `Double.equals` dựa trên biểu diễn bit đã canonicalize NaN, nên coi ±0 là **không bằng nhau**. Điều này giúp `Double` có quan hệ bằng/so sánh tương thích với cách dùng trong `HashSet`, `HashMap`, `TreeSet`.

Tương tự, `Float.compare(-0.0f, +0.0f)` trả số âm và `Float.valueOf(-0.0f).equals(Float.valueOf(+0.0f))` là false.

### 12.1. Ảnh hưởng đến collections

```java
java.util.Set<Double> zeros = new java.util.HashSet<>();
zeros.add(+0.0);
zeros.add(-0.0);
System.out.println(zeros.size()); // 2
```

Đây là **mảnh mã**, đặt trong phương thức nếu muốn chạy. Khi thêm vào `HashSet<Double>`, auto-boxing tạo hai giá trị `Double` có ý nghĩa khác nhau theo `equals`. Vì thế tập hợp có **2 phần tử**, dù primitive `+0.0 == -0.0` là true.

Nếu yêu cầu nghiệp vụ gộp hai zero thành một khóa, phải **chuẩn hóa có chủ đích** trước khi đưa vào map/set. Ví dụ:

```java
double value = -0.0;
double normalized = value == 0.0 ? 0.0 : value;
```

Tuy nhiên, nếu giá trị được dùng để tính nghịch đảo, phân tích hình học hoặc giữ raw IEEE bits, việc chuẩn hóa có thể làm mất thông tin cần thiết. **Quyết định lưu hay gộp signed zero phải theo yêu cầu**, không áp dụng máy móc cho mọi hệ thống.

### 12.2. Vì sao so sánh NaN cũng khác?

`Double.compare` còn quy định một thứ tự cho NaN, trong khi phép `==` với NaN luôn false. `Double.equals` coi các NaN đã canonicalize là tương đương. Đây là lý do cần biết bạn đang dùng **phép so sánh toán học primitive**, **thứ tự dành cho collection** hay **kiểm tra mẫu bit**.

## 13. Chương trình Java hoàn chỉnh A: chia cho signed zero

Lưu thành **`SignedZeroDivision.java`**:

```java
public class SignedZeroDivision {
    public static void main(String[] args) {
        double positive = +0.0;
        double negative = -0.0;

        System.out.println(positive == negative);

        System.out.println(1.0 / positive);
        System.out.println(1.0 / negative);
        System.out.println(-1.0 / positive);
        System.out.println(-1.0 / negative);

        System.out.println(negative / 2.0);
        System.out.println(positive / -2.0);
        System.out.println(negative / -2.0);

        System.out.println(positive / negative);
        System.out.println(3.0 / Double.NEGATIVE_INFINITY);
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac --release 17 SignedZeroDivision.java
java SignedZeroDivision
```

**Stdout chính xác:**

```text
true
Infinity
-Infinity
-Infinity
Infinity
-0.0
-0.0
0.0
NaN
-0.0
```

Giải thích từng nhóm:

1. Dòng `true` chỉ nói hai zero bằng theo phép so sánh primitive.
2. Bốn dòng vô cực thể hiện quy tắc **cùng dấu/dị dấu**.
3. Ba dòng kết quả zero thể hiện dấu của thương.
4. `0.0 / -0.0` cho NaN, không có dấu số hữu hạn.
5. `3.0 / -Infinity` cho zero âm theo quy tắc dấu.

Không gán `-Infinity` vào một phép tính nghiệp vụ mà không kiểm tra, vì nó có thể làm thuật toán lan truyền infinity/NaN ở các bước sau.

## 14. Chương trình Java hoàn chỉnh B: signed zero trong Math

Lưu thành **`SignedZeroMath.java`**:

```java
public class SignedZeroMath {
    public static void main(String[] args) {
        System.out.println(Math.min(-0.0, +0.0));
        System.out.println(Math.max(-0.0, +0.0));
        System.out.println(Math.abs(-0.0));
        System.out.println(Math.signum(-0.0));

        System.out.println(Math.copySign(2.5, -0.0));
        System.out.println(Math.copySign(0.0, -1.0));

        System.out.println(Math.sqrt(-0.0));
        System.out.println(Math.ceil(-0.5));
        System.out.println(Math.floor(-0.0));
        System.out.println(Math.rint(-0.4));

        System.out.println(Math.atan2(+0.0, -1.0));
        System.out.println(Math.atan2(-0.0, -1.0));

        System.out.println(Math.pow(-0.0, 3.0));
        System.out.println(Math.pow(-0.0, -3.0));
    }
}
```

Chạy:

```bash
javac --release 17 SignedZeroMath.java
java SignedZeroMath
```

**Stdout chính xác:**

```text
-0.0
0.0
0.0
-0.0
-2.5
-0.0
-0.0
-0.0
-0.0
-0.0
3.141592653589793
-3.141592653589793
-0.0
-Infinity
```

Những trường hợp này đã được chọn vì dấu zero **được quy định bởi hợp đồng API**. Các phương thức khác của `Math` có thể có quy tắc riêng về signed zero, NaN và infinity; luôn tham khảo tài liệu theo đúng phiên bản JDK mà dự án hỗ trợ.

## 15. Chương trình Java hoàn chỉnh C: bit, equality và ordering

Lưu thành **`SignedZeroIdentity.java`**:

```java
import java.util.HashSet;
import java.util.Set;

public class SignedZeroIdentity {
    public static void main(String[] args) {
        double positive = +0.0;
        double negative = -0.0;

        System.out.printf("%016X%n", Double.doubleToRawLongBits(positive));
        System.out.printf("%016X%n", Double.doubleToRawLongBits(negative));

        System.out.println(positive == negative);
        System.out.println(Double.compare(negative, positive));
        System.out.println(Double.valueOf(negative)
                .equals(Double.valueOf(positive)));

        Set<Double> values = new HashSet<>();
        values.add(positive);
        values.add(negative);
        System.out.println(values.size());

        System.out.println(Math.nextUp(negative) == Double.MIN_VALUE);
        System.out.println(Math.nextDown(positive) == -Double.MIN_VALUE);
    }
}
```

Chạy:

```bash
javac --release 17 SignedZeroIdentity.java
java SignedZeroIdentity
```

**Stdout chính xác:**

```text
0000000000000000
8000000000000000
true
-1
false
2
true
true
```

Tại đây có ba câu hỏi khác nhau: `==` xác định tương đương theo số học, raw bits xác định **bit identity**, và wrapper/collection xác định **equivalence/ordering** theo hợp đồng của `Double`.

## 16. Kiểm tra và bảo toàn signed zero trong chương trình thực tế

### 16.1. Chỉ cần biết một giá trị có phải zero không

Dùng:

```java
boolean isZero = value == 0.0;
```

Điều này nhận cả zero dương và zero âm; `NaN` không thỏa. Đây là lựa chọn tốt khi dấu zero **không có ý nghĩa nghiệp vụ**.

### 16.2. Cần biết chính xác zero âm

Dùng raw bits hoặc so sánh phần dấu của zero:

```java
static boolean isNegativeZero(double x) {
    return Double.doubleToRawLongBits(x) == 0x8000000000000000L;
}
```

Phương thức trên đúng với **zero âm**, không coi mọi số âm hoặc NaN có bit sign bằng 1 là zero âm. Nó là một **mảnh phương thức** cần đặt trong class.

Cách kiểm tra khác dựa trên phép chia: `x == 0.0 && 1.0 / x == Double.NEGATIVE_INFINITY`. Tuy nhiên, raw-bits thường rõ mục đích hơn, tránh khiến người đọc nghĩ bạn đang thử phép chia với mẫu số không hợp lệ.

### 16.3. Cần bảo toàn dấu khi truyền dữ liệu

- Dùng `Double.doubleToRawLongBits`/`Double.longBitsToDouble` khi hợp đồng yêu cầu bảo toàn bit của giá trị IEEE 754.
- Nếu lưu thành chuỗi thập phân theo `Double.toString`, `-0.0` sẽ xuất hiện rõ như chuỗi `"-0.0"`. Nhưng hệ thống bên ngoài có thể chuẩn hóa nó khi parse hoặc mapping.
- Khi đi qua kiểu nguyên, định dạng số thập phân cố định, dữ liệu tài chính, cơ sở dữ liệu hay giao thức trao đổi, cần xác minh đầu-cuối liệu dấu zero có được giữ không. **Đừng giả định** mọi định dạng số hỗ trợ signed zero giống IEEE 754.
- Nếu chuẩn hóa về `+0.0` là yêu cầu, thực hiện một lần ở ranh giới hệ thống và ghi rõ quy ước.

### 16.4. Các kiểm thử boundary nên có

| Mục tiêu | Trường hợp kiểm thử |
| --- | --- |
| Chia | `1.0/+0.0`, `1.0/-0.0`, `-1.0/-0.0` |
| Zero/zero | `0.0/0.0`, `-0.0/+0.0` → NaN |
| Zero từ phép khác | `-0.0*2.0`, `-0.0+-0.0`, `-0.0++0.0` |
| Hàm toán học | `Math.min/max` của ±0, `abs`, `signum`, `copySign` |
| Rounding | `Math.ceil(-0.5)`, `Math.rint(-0.4)` |
| Góc | `Math.atan2(±0.0,-1.0)` |
| Bit identity | `doubleToRawLongBits(±0.0)` |
| Collection | Hai `Double` ±0 làm khóa |
| Giá trị đặc biệt | `NaN`, `Infinity`, `Double.MIN_VALUE` |

**Gợi ý:** đừng chỉ so sánh số qua `==` trong unit test cần phân biệt zero âm/dương; hãy đối chiếu raw bits hoặc phép toán phụ thuộc dấu. Với comparator/collections, kiểm tra đúng hợp đồng `equals` và `compareTo`.

## 17. Những ngộ nhận thường gặp

1. **“`+0.0 == -0.0` nên chúng thay thế nhau được.”** Sai. Chia lấy nghịch đảo và một số hàm toán học phân biệt dấu.
2. **“Chia cho zero floating point luôn ném exception.”** Sai; thường trả ±Infinity hoặc NaN. `int`/`long` chia 0 mới ném `ArithmeticException` khi chạy.
3. **“`Math.abs(-0.0)` bảo toàn dấu.”** Sai; trả +0.0.
4. **“`Math.signum(-0.0)` trả -1.0.”** Sai; trả -0.0.
5. **“`Math.min(+0.0,-0.0)` tùy ý trả bất kỳ zero nào.”** Sai; overload số thực quy định kết quả là -0.0.
6. **“`Math.atan2(0.0,-1.0)` không cần biết dấu 0.”** Sai; +0 cho +π, -0 cho -π.
7. **“`Double.equals` dùng chính xác cùng quy tắc `==` primitive.”** Sai; wrapper phân biệt hai zero và xử lý NaN theo hợp đồng riêng.
8. **“`HashSet<Double>` chắc chắn gộp ±0.”** Sai; mặc định có thể lưu cả hai vì `equals` khác nhau.
9. **“Ép `(long) -0.0` giữ bit dấu.”** Sai; cast numeric cho số nguyên 0, mất signed-zero bit.
10. **“`Double.MIN_VALUE` là số âm nhỏ nhất.”** Sai; đó là subnormal dương khác 0 nhỏ nhất.
11. **“Đầu ra `-0.0` là lỗi in số.”** Không; `Double.toString` giữ dấu để thể hiện khác biệt IEEE 754.
12. **“Có thể chuẩn hóa mọi -0.0 thành +0.0 mà không ảnh hưởng chương trình.”** Sai nếu dấu zero có tác động đến các phép tính sau.
13. **“Mọi hàm toán học xử lý signed zero giống nhau.”** Sai; phải tra contract của từng hàm.
14. **“NaN có thể kiểm tra bằng `== Double.NaN`.”** Sai; hãy dùng `Double.isNaN`.

## 18. Bài tập kèm đáp án

### Bài 1 — Hai giá trị bằng nhau nhưng hai kết quả khác dấu

Với `double a=+0.0, b=-0.0;`, hãy tính `a == b`, `1.0 / a` và `1.0 / b`.

**Đáp án:** `true`, `Infinity`, `-Infinity`. `==` không so sánh bit dấu của zero theo nghĩa số học, còn phép chia có quy tắc dấu.

### Bài 2 — Cùng dấu và khác dấu

Tính `-2.0 / +0.0` và `-2.0 / -0.0`.

**Đáp án:** `-Infinity` và `Infinity`. Phép chia hai toán hạng cùng dấu cho dương, khác dấu cho âm.

### Bài 3 — Zero chia zero

`-0.0 / -0.0` cho zero âm, zero dương hay NaN?

**Đáp án:** `NaN`. Zero chia zero không có thương số hữu hạn hoặc vô cực xác định.

### Bài 4 — Ba hàm xử lý dấu

Dự đoán `Math.abs(-0.0)`, `Math.signum(-0.0)` và `Math.copySign(2.5,-0.0)`.

**Đáp án:** `0.0`, `-0.0` và `-2.5`. `abs` bỏ dấu, `signum` giữ zero âm, `copySign` lấy dấu của đối số thứ hai.

### Bài 5 — Min/max signed zero

`Math.min(-0.0,+0.0)` và `Math.max(-0.0,+0.0)` cho gì?

**Đáp án:** `-0.0` và `0.0` theo hợp đồng `Math`.

### Bài 6 — Góc ranh giới

So sánh `Math.atan2(+0.0,-1.0)` và `Math.atan2(-0.0,-1.0)`.

**Đáp án:** `+π` và `-π`. Dấu của tọa độ y biểu thị phía của nhánh góc.

### Bài 7 — Wrapper và nguyên thủy

Tại sao `-0.0 == +0.0` là true nhưng `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` là false?

**Đáp án:** `==` primitive tuân theo equality floating point; `Double.equals` dựa trên bit-conversion có phân biệt zero âm/dương.

### Bài 8 — Xác định dấu chính xác

Viết điều kiện chỉ đúng với `double x = -0.0`, không đúng với `+0.0` hoặc `-Double.MIN_VALUE`.

**Đáp án:** `Double.doubleToRawLongBits(x) == 0x8000000000000000L`.

### Bài 9 — Căn bậc hai

Tính `Math.sqrt(-0.0)` và `Math.sqrt(-1.0)`.

**Đáp án:** `-0.0` và `NaN`. Zero âm được bảo toàn; số âm hữu hạn khác zero không có căn bậc hai thực trong kết quả `double`.

### Bài 10 — Làm tròn về zero

`Math.ceil(-0.5)`, `Math.floor(-0.5)` và `Math.rint(-0.4)` trả gì?

**Đáp án:** `-0.0`, `-1.0`, `-0.0`. Dấu zero còn được giữ ở kết quả của `ceil`/`rint`.

### Bài 11 — Kiểm thử collections

Thêm `+0.0` và `-0.0` vào `HashSet<Double>` rồi gọi `size()`. Kết quả bao nhiêu?

**Đáp án:** `2`. `Double.equals` phân biệt zero âm/dương, dù primitive `==` coi chúng bằng nhau.

### Bài 12 — Quyết định chuẩn hóa

Một API chỉ cần kiểm tra “khối lượng có bằng zero không” nên phân biệt ±0 hay không? Một API tính `1.0/x` có thể bỏ dấu zero không?

**Đáp án:** câu đầu thường chỉ cần `x == 0.0`, không phân biệt dấu; câu sau **không thể** bỏ dấu nếu muốn giữ chính xác dấu của infinity và hợp đồng IEEE 754. Quyết định phải dựa trên mục đích dữ liệu.

## 19. Checklist hoàn thành

- [ ] Tôi biết chính xác mẫu bit `+0.0` và `-0.0` của `float`/`double`.
- [ ] Tôi giải thích được vì sao primitive `==` trả true, dù phép chia theo dấu cho hai kết quả vô cực khác nhau.
- [ ] Tôi phân biệt kết quả `±0.0`, `±Infinity` và `NaN` trong phép `/`.
- [ ] Tôi biết `Math.abs` bỏ dấu zero âm, trong khi `Math.signum` bảo toàn nó.
- [ ] Tôi biết `Math.copySign` có thể tạo zero âm và `Math.min/max` chọn dấu zero theo hợp đồng.
- [ ] Tôi hiểu `Math.sqrt`, `Math.ceil`, `Math.floor`, `Math.rint` có thể trả signed zero.
- [ ] Tôi giải thích được `Math.atan2(+0.0,-1.0)` khác `Math.atan2(-0.0,-1.0)`.
- [ ] Tôi phân biệt `Double.compare`/`equals` với `==` khi dùng collection.
- [ ] Tôi dùng raw bits để kiểm tra zero âm chính xác mà không lẫn số âm nhỏ hay NaN.
- [ ] Tôi có lý do rõ ràng trước khi **giữ lại** hoặc **chuẩn hóa** signed zero trong dữ liệu.
- [ ] Tôi đã chạy ba ví dụ Java 17+ và kiểm tra các giá trị ở ranh giới.

**Tài liệu chính thức để đối chiếu:**

- [Java SE 17 — Double API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): signed zero, `compare`, `equals`, `doubleToRawLongBits`, `toString`.
- [Java SE 17 — Float API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html): cấu trúc binary32 và các API raw bits.
- [Java SE 17 — Math API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `abs`, `signum`, `copySign`, `min`, `max`, `sqrt`, `ceil`, `floor`, `rint`, `atan2`, `pow`.
- [JLS 17 `15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17): ngữ nghĩa nhân/chia số thực, signed zero và infinity.
- [JLS 17 `15.18 — Additive Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18): cộng/trừ floating-point.
- [JLS 17 `15.21.1 — Numerical Equality](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21.1): `==` và NaN/zero.

**Kết luận:** signed zero là **thông tin dấu được lưu trong biểu diễn số thực**, không chỉ là cách in. Khi xây dựng thuật toán Java dùng `double`/`float`, đừng suy từ `+0.0 == -0.0` rằng chúng có thể thay thế nhau trong phép chia hoặc mọi hàm toán học. Muốn kết quả đúng, hãy xác định bạn cần **equality theo giá trị**, **raw bit identity**, hay **quy tắc xử lý dấu** của từng phép toán và API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-007">Vì sao phép toán không bảo đảm tính kết hợp của số thực toán học</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao phép toán dấu phẩy động không bảo đảm tính kết hợp của số thực toán học trong Java

> **Java core – numbers · Phép toán dấu phẩy động**  
> **Mục tiêu:** Hiểu chính xác vì sao các phép cộng/nhân trên `float` và `double` có thể cho kết quả khác nhau khi đổi ngoặc; phân biệt tính kết hợp của đại số với cách nhóm cú pháp và thứ tự đánh giá trong Java; biết cách thiết kế phép tính tổng ổn định hơn, kiểm thử và xử lý sai số.

## 1. Tính kết hợp trong toán học nói gì?

Với các số thực toán học và phép cộng, phép nhân chính xác, ta có:

\[
(a+b)+c=a+(b+c)
\]
\[
(a\times b)\times c=a\times(b\times c)
\]

Đây là **tính kết hợp** (*associativity*). Nó cho phép đổi ngoặc trong một chuỗi cộng hoặc nhân số thực mà không đổi giá trị cuối cùng.

Nhưng các giá trị `float` và `double` trong Java không phải tập số thực có độ chính xác vô hạn. Chúng lưu một **tập hữu hạn các mẫu bit** của chuẩn dấu phẩy động nhị phân IEEE 754. Sau nhiều phép toán, kết quả toán học phải **làm tròn** về một giá trị mà kiểu dữ liệu biểu diễn được.

Do đó các phép toán dấu phẩy động **không được bảo đảm có tính kết hợp như phép cộng và phép nhân trên tập số thực toán học**. Đây không phải lỗi Java hay lỗi CPU: việc tính trong miền có độ chính xác hữu hạn và làm tròn ở mỗi bước chính là nguồn gốc của sự khác biệt.

Điểm cần tránh: **phép trừ và phép chia trên số thực toán học vốn không có tính kết hợp**, nên các ví dụ `(a-b)-c` khác `a-(b-c)` không phải bằng chứng riêng về mất tính kết hợp do IEEE 754. Ta dùng **cộng** và **nhân** để minh họa hiện tượng đúng trọng tâm.

## 2. Mô hình đúng: cộng rồi làm tròn, không phải cộng số thực vô hạn

Đối với phép toán `op`, có thể hình dung:

\[
\mathrm{fl}(a\ \mathrm{op}\ b)
 = \mathrm{round}_{\mathrm{format}}(a\ \mathrm{op}\ b)
\]

Trong đó `fl` là kết quả được biểu diễn bằng `float` hoặc `double` sau khi phép toán được làm tròn. Với các phép cộng thông thường trên các giá trị hữu hạn, Java 17+ dùng ngữ nghĩa IEEE 754 xác định; chế độ làm tròn áp dụng cho các toán tử số học tiêu chuẩn là **làm tròn đến giá trị gần nhất, hòa thì chọn bit cuối chẵn** (*round to nearest, ties to even*).

Bởi vậy hai cách nhóm tính hai chuỗi làm tròn khác nhau:

\[
\mathrm{fl}\big(\mathrm{fl}(a+b)+c\big)
\]
\[
\mathrm{fl}\big(a+\mathrm{fl}(b+c)\big)
\]

Khi `fl(a+b)` hoặc `fl(b+c)` đã khác giá trị toán học chính xác, phép toán tiếp theo không thể tự khôi phục tất cả bit bị bỏ đi. Hai chuỗi có thể cho **hai kết quả cuối cùng khác nhau**, mặc dù cả hai biểu thức đều hợp lệ và tuân thủ Java.

### Độ chính xác hữu hạn

| Kiểu | Độ rộng | Bit chính xác hiệu dụng của số normal | Khoảng chữ số thập phân có nghĩa |
| --- | ---: | ---: | --- |
| `float` | 32 | 24 bit | Khoảng 6–9 |
| `double` | 64 | 53 bit | Khoảng 15–17 |

Các bit này không được phân bổ đồng đều trên trục số thực: khoảng cách giữa các giá trị biểu diễn được thường **lớn hơn khi độ lớn của số lớn hơn**. Từ đây nảy sinh hiện tượng một số nhỏ cộng vào số rất lớn có thể **không làm thay đổi** số lớn.

## 3. Phản ví dụ kinh điển: cùng ba số, đổi ngoặc đổi kết quả

Chọn:

```java
double a = 1.0e16;  // 10^16
double b = -1.0e16; // -10^16
double c = 1.0;
```

Trên số thực toán học:

\[
(a+b)+c=(10^{16}-10^{16})+1=1
\]
\[
a+(b+c)=10^{16}+(-10^{16}+1)=1
\]

Trong `double` Java:

```java
double left = (a + b) + c;
double right = a + (b + c);

System.out.println(left);          // 1.0
System.out.println(right);         // 0.0
System.out.println(left == right); // false
```

**Vì sao vế trái ra 1.0?**

1. `a+b`: hai số có cùng độ lớn và trái dấu triệt tiêu nhau, tạo ra `0.0` chính xác.
2. Cộng tiếp `c=1.0`, được `1.0` chính xác.

**Vì sao vế phải ra 0.0?**

1. Trong `b+c`, ta thử cộng `1.0` vào `-10^16`.
2. Ở độ lớn quanh `10^16`, hai số `double` liên tiếp cách nhau **2.0**. Giá trị toán học `-10^16+1` nằm đúng giữa những giá trị có thể biểu diễn, và quy tắc *ties to even* làm tròn về `-10^16`.
3. Sau đó `a + b` trở thành `10^16 + (-10^16)`, cho `0.0`.

Có thể kiểm tra trực tiếp:

```java
System.out.println(Math.ulp(1.0e16)); // 2.0
System.out.println(-1.0e16 + 1.0 == -1.0e16); // true
```

`Math.ulp(x)` cho biết kích cỡ một đơn vị ở vị trí bit có nghĩa cuối (*unit in the last place*) quanh `x`. Ở đây độ lệch 1.0 nhỏ hơn khả năng phân giải cần để tạo ra một giá trị `double` mới quanh `10^16`.

| Phép tính | Giá trị trung gian sau làm tròn | Kết quả |
| --- | --- | --- |
| `(a+b)+c` | `a+b = 0.0` | **1.0** |
| `a+(b+c)` | `b+c = -1.0E16` | **0.0** |

**Kết luận từ phản ví dụ:** Java không bảo đảm `(a+b)+c == a+(b+c)` khi các biến là `float`/`double`. Chỉ một trường hợp hợp lệ trả khác nhau đã đủ bác bỏ giả thiết luôn có tính kết hợp.

## 4. Cách nhóm cú pháp khác thứ tự đánh giá toán hạng

Cần phân biệt ba vấn đề liên quan nhưng khác nhau:

- **Độ ưu tiên** (*precedence*): `*` gắn toán hạng chặt hơn `+`.
- **Tính kết hợp của cú pháp** (*syntactic associativity*): chuỗi `a+b+c` được nhóm theo bên trái, tức `(a+b)+c`.
- **Thứ tự đánh giá** (*evaluation order*): Java đánh giá các toán hạng theo quy tắc từ trái sang phải, bao gồm tác dụng phụ quan sát được của lời gọi phương thức.

Ví dụ:

```java
double a = 1.0e16, b = -1.0e16, c = 1.0;

double defaultGroup = a + b + c;    // (a + b) + c -> 1.0
double otherGroup   = a + (b + c);  // 0.0
```

Dấu ngoặc thứ hai **thay đổi cây phép tính**, do đó thay đổi chỗ thực hiện làm tròn. Nhưng ngoặc ở vế phải không có nghĩa Java tự ý gọi những hàm ở bên phải trước những hàm đứng bên trái.

Ví dụ:

```java
double result = first() + (second() + third());
```

Java vẫn đánh giá các toán hạng theo thứ tự: `first()`, rồi `second()`, rồi `third()` (nếu không có ngoại lệ ngắt quá trình). **Các kết quả đã nhận** được kết hợp theo dấu ngoặc: `second+third` trước, sau đó mới cộng với `first`.

Trong Java 17+, biểu thức dấu phẩy động có ngữ nghĩa **always-strict**. Compiler/JVM không được tùy tiện đổi `(a+b)+c` thành `a+(b+c)` nếu điều đó có thể đổi hành vi được Java quy định. Việc tối ưu phải bảo toàn ngữ nghĩa quan sát được; lập trình viên không được tự giả định hai cách nhóm bằng nhau để “tối ưu đại số”.

## 5. Vì sao bit thấp có thể biến mất: độ lớn, ULP và hấp thụ

Một số nguyên nhỏ có thể biểu diễn chính xác riêng lẻ, nhưng không đủ lớn để thay đổi một số `double` đã có độ lớn cao:

```java
double big = 1.0e16;
double small = 1.0;

System.out.println(big + small == big); // true
System.out.println(Math.ulp(big));      // 2.0
```

Hiện tượng này thường gọi là **hấp thụ số hạng nhỏ** (*absorption*) trong phép cộng dấu phẩy động. Không phải số 1 “không tồn tại”; nó tồn tại, nhưng khi cộng với `10^16`, kết quả toán học rơi giữa các điểm có thể biểu diễn bằng 53 bit chính xác của `double`.

Mấu chốt là **độ chính xác tương đối**, không chỉ phạm vi lớn/nhỏ của kiểu:

- `double` có thể biểu diễn các số đến gần `1.8×10^308`.
- Điều đó không có nghĩa mọi số nguyên cách nhau 1 đều biểu diễn chính xác ở gần `10^308` hay `10^16`.
- Trong miền số nguyên của `double`, mọi số nguyên đến `2^53` đều có thể biểu diễn chính xác, nhưng ngoài đó **không còn bảo đảm** khoảng cách 1.

Ở kiểu `float`, hiệu ứng đến sớm hơn:

```java
float bigFloat = 100_000_000f;
float smallFloat = 1f;

System.out.println(bigFloat + smallFloat == bigFloat); // true
System.out.println(Math.ulp(bigFloat)); // 8.0
```

Vì `float` có 24 bit chính xác hiệu dụng, độ phân giải quanh một trăm triệu là **8.0**. Tăng 1.0 không đủ để chuyển sang giá trị khác.

**Không dùng** `Float.MIN_VALUE` hoặc `Double.MIN_VALUE` như “bước giữa hai số” trên toàn trục số. Hai hằng này chỉ là số dương subnormal nhỏ nhất; bước ULP quanh 1, quanh một triệu và quanh một giá trị rất lớn là khác nhau.

## 6. Sai số do triệt tiêu: mất thông tin trước khi trừ/cộng

*Cộng hai số gần bằng nhau nhưng trái dấu* có thể tạo ra **cancellation** (*triệt tiêu*). Việc triệt tiêu **không tự nó luôn gây sai số lớn**: nếu hai toán hạng được lưu chính xác và phép cộng là chính xác, kết quả vẫn có thể đúng. Vấn đề là khi các toán hạng **đã có sai số làm tròn**, phép trừ gần bằng nhau có thể làm sai số tương đối của kết quả tăng lên.

Xét biểu thức:

```java
double a = 1.0e16;
double b = 1.0;
double c = -1.0e16;

double computed = (a + b) + c;
System.out.println(computed); // 0.0
```

Giá trị toán học mong muốn là 1, nhưng `a+b` được làm tròn về `a` rồi cộng `c` nên ra 0. **Phép cộng cuối không sai trong bản thân đầu vào đã được làm tròn**; thông tin “+1” đã bị bỏ ở bước trước.

Một cách nhóm khác:

```java
double betterForThisCase = (a + c) + b;
System.out.println(betterForThisCase); // 1.0
```

Cho bộ dữ liệu này, cộng hai số lớn trái dấu trước làm kết quả nhỏ 0 rồi mới cộng 1, giữ lại phần nhỏ. Nhưng **không có một thứ tự nhóm chung tốt nhất cho mọi dữ liệu**; việc chọn thuật toán tổng phải xét miền giá trị, dấu, điều kiện số và yêu cầu tái lập.

## 7. Nhân cũng không có bảo đảm kết hợp: overflow trung gian

Tính kết hợp toán học cho phép:

\[
(a\times b)\times c=a\times(b\times c).
\]

Tuy nhiên, trong `double`:

```java
double a = 1.0e308;
double b = 2.0;
double c = 0.5;

System.out.println((a * b) * c); // Infinity
System.out.println(a * (b * c)); // 1.0E308
```

Phân tích:

1. Vế trái tính `a*b = 2×10^308`, vượt giá trị hữu hạn lớn nhất của `double`, nên sau làm tròn thành `+Infinity`. Nhân `Infinity*0.5` vẫn là `Infinity`.
2. Vế phải tính `b*c = 1.0` trước, rồi `a*1.0 = 1.0E308`, vẫn hữu hạn.

Dưới số thực toán học, hai vế đều bằng `10^308`. Với số dấu phẩy động có giới hạn exponent, **đổi ngoặc có thể quyết định có overflow trung gian hay không**.

Không nên suy ra rằng luôn phải nhân các toán hạng nhỏ trước; trong bài toán khác, phương án đó có thể gây **underflow**, tăng sai số hoặc thay đổi nhu cầu xử lý các giá trị đặc biệt.

## 8. Underflow trung gian cũng làm kết quả khác nhau

Ngược lại với overflow, kết quả trung gian quá gần 0 có thể bị làm tròn về 0 dù kết quả toán học cuối sau nhân các hệ số tiếp theo khác 0.

```java
double tiny = Double.MIN_VALUE;
double half = 0.5;
double two = 2.0;

System.out.println((tiny * half) * two); // 0.0
System.out.println(tiny * (half * two)); // 4.9E-324
```

`Double.MIN_VALUE` là số dương `double` nhỏ nhất khác 0, tương đương `2^-1074`. Giá trị toán học `tiny*0.5 = 2^-1075` nằm giữa 0 và giá trị dương nhỏ nhất có thể biểu diễn. Theo quy tắc làm tròn đến gần nhất, hòa thì chọn chẵn, nó làm tròn về `+0.0`. Nhân tiếp với 2 không thể phục hồi giá trị đã mất.

Vế phải tính `0.5*2.0=1.0` trước, nên `tiny*1.0` vẫn bằng `Double.MIN_VALUE`.

Đây là **underflow ở bước trung gian**, không phải lỗi cú pháp hay chương trình ném ngoại lệ. Phép toán dấu phẩy động với underflow/overflow thường tạo giá trị theo IEEE 754 thay vì ném `ArithmeticException` như chia nguyên cho 0.

## 9. Biểu diễn thập phân không chính xác là một nguyên nhân liên quan, nhưng không phải nguyên nhân duy nhất

`0.1` không có biểu diễn nhị phân hữu hạn. Khi gõ:

```java
double tenth = 0.1;
System.out.println(Double.toHexString(tenth));
// 0x1.999999999999ap-4
```

Java lưu **giá trị `double` gần 0.1**, không phải chính xác phân số toán học 1/10. Các phép toán tiếp theo dùng giá trị đã được lưu đó, rồi lại làm tròn trung gian.

Tuy nhiên, ví dụ phản chứng chính ở phần 3 **không cần** literal `0.1`: các giá trị `10^16`, `-10^16` và `1` đều có thể biểu diễn chính xác bằng `double`, nhưng **phép cộng trung gian** vẫn gây mất số hạng nhỏ. Vì vậy không đúng nếu kết luận rằng chỉ các số thập phân có biểu diễn nhị phân vô hạn mới bị ảnh hưởng.

Một ví dụ dễ thấy về làm tròn:

```java
System.out.println(0.1 + 0.2);        // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false
```

Đây là ví dụ về sai số biểu diễn và cộng/làm tròn, **không tự nó chứng minh tính không kết hợp** vì không so sánh hai cách đặt ngoặc khác nhau.

## 10. Java 17+, strictfp, Math.fma và khả năng tái lập

Kể từ Java 17, JEP 306 khôi phục ngữ nghĩa dấu phẩy động **luôn nghiêm ngặt** (*always-strict floating-point semantics*); từ khóa `strictfp` không còn cần để yêu cầu cùng quy tắc chính xác đó cho các biểu thức số thực.

Điều này giúp hành vi phép toán ổn định theo đặc tả Java, nhưng **không làm phép cộng/nhân trở thành kết hợp**. “Kết quả tuân theo chuẩn và có thể dự đoán” hoàn toàn khác với “có thể đổi ngoặc tùy ý”.

### Fused multiply-add

`Math.fma(a, b, c)` tính tích chính xác lý tưởng `a*b` rồi cộng `c` trước khi làm tròn **một lần** về `float`/`double`; khác với biểu thức `a*b+c` thông thường, vốn có thể làm tròn sau phép nhân rồi sau phép cộng.

```java
double fused = Math.fma(a, b, c);
double separate = a * b + c;
```

Ở đây giá trị của các biến phụ thuộc ngữ cảnh đoạn code; đây là **minh họa API**, không phải chương trình độc lập. Dùng `fma` khi thuật toán được thiết kế để tận dụng phép nhân-cộng hợp nhất. **Nó không khiến mọi tổng hay mọi phép nhân tự động có tính kết hợp** và không thay thế việc kiểm soát overflow, underflow trong toàn thuật toán.

### Không tự cho phép trình biên dịch đổi ngoặc

Các phép biến đổi đại số như:

```java
// Không tự coi hai vế tương đương cho mọi float/double!
double left = (a + b) + c;
double right = a + (b + c);
```

không được áp dụng vô điều kiện. Một biểu thức có thể khác do sai số làm tròn, giá trị vô cực, `NaN` và số 0 có dấu. Khi cần cải thiện hiệu năng hoặc tính ổn định, đánh giá **kết quả tính được của dữ liệu thực**, không chỉ biến đổi đại số trên giấy.

## 11. Tổng nhiều số: thứ tự lặp và giảm song song có thể đổi kết quả

Trong chương trình tính tổng mảng, tổng từng phần (*partial sums*) có thể thay đổi theo:

- thứ tự duyệt phần tử;
- cách chia mảng thành các nhóm;
- cách kết hợp kết quả từ những luồng tính khác nhau;
- thuật toán cộng thường, ghép cặp hoặc cộng có bù sai số.

Dù dữ liệu đầu vào giống nhau, đổi cây cộng có thể đổi các bước làm tròn. Ví dụ:

```java
double[] values = {1.0e16, -1.0e16, 1.0};

double firstOrder = 0.0;
for (double value : values) {
    firstOrder += value;
}
// firstOrder = 1.0
```

Nếu đổi thứ tự thành `{1.0e16, 1.0, -1.0e16}`, phép cộng tuần tự cho `0.0`. Nếu một phép **parallel reduction** nhóm các phần tử theo cách khác, kết quả có thể khác. Điều này không có nghĩa mọi lần chạy song song đều khác nhau: một lịch gộp xác định có thể ổn định; vấn đề là **không được hứa thứ tự ghép mà API/thuật toán không cam kết**.

Trong phân tích dữ liệu, tài chính, tính điểm hay phép tổng trên nhiều máy, cần xác định mục tiêu:

1. **Gần đúng đủ tốt** theo dung sai nghiệp vụ.
2. **Tái lập bit-for-bit** theo một thuật toán và thứ tự cố định.
3. **Tổng đúng theo quy tắc số học cụ thể** (ví dụ số thập phân chính xác, phép tổng bù sai số, hoặc tích lũy độ chính xác cao).

Ba mục tiêu trên đòi hỏi chiến lược triển khai và kiểm thử khác nhau.

## 12. Giảm sai số khi cộng nhiều giá trị

### 12.1. Chọn thứ tự cộng phù hợp dữ liệu

Trong một số trường hợp, cộng các số nhỏ với nhau trước khi ghép với số lớn có thể giảm hấp thụ. Cộng hai số lớn trái dấu trước cũng có thể giữ lại số nhỏ, như phản ví dụ phần 6.

Tuy nhiên, **sắp xếp theo trị tuyệt đối không phải định lý bảo đảm kết quả tốt nhất cho mọi chuỗi số**, đặc biệt khi có nhiều giá trị khác dấu hoặc gần biên. Không thay đổi thứ tự theo cảm tính nếu hệ thống đòi kết quả tái lập theo quy trình cố định.

### 12.2. Pairwise summation

*Pairwise summation* cộng theo cây cân bằng thay vì cộng lần lượt vào một accumulator. Đối với nhiều tập dữ liệu, cách này có thể giảm sự tích lũy sai số và phù hợp phép tính song song.

Nhưng thay đổi cây tổng **thay đổi các bước làm tròn**, nên kết quả có thể khác tổng tuần tự. Khi đặt mục tiêu reproducibility, ghi rõ thuật toán giảm và thứ tự ghép.

### 12.3. Kahan và các thuật toán compensated summation

*Compensated summation* (chẳng hạn Kahan hoặc Neumaier) giữ thêm thông tin về phần sai số bị bỏ trong một số bước cộng. Nó có thể cải thiện độ chính xác đáng kể so với cộng tuần tự đơn giản.

Không nên khẳng định “Kahan trả lời đúng cho mọi bộ số”: ví dụ có số lớn trái dấu, triệt tiêu và số nhỏ ở giữa có thể đòi biến thể bù sai số khác; Kahan không bảo đảm mọi biểu thức đại số trở thành kết hợp.

Một minh họa thuật toán **Neumaier summation** bằng Java:

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

Đây là **phương thức minh họa** cần nằm trong một lớp Java. Với mảng `{1.0e16, 1.0, -1.0e16}`, thuật toán giữ số hạng nhỏ trong biến bù và trả `1.0`. Nó vẫn dùng `double`, không phải phép tính chính xác tùy ý cho mọi trường hợp, và vẫn cần kiểm thử với infinities, NaN, overflow và giới hạn tài nguyên.

### 12.4. Khi cần số thập phân chính xác, dùng BigDecimal có chủ đích

Đối với tính tiền, các giá trị thập phân có quy tắc làm tròn cụ thể, cân nhắc `BigDecimal` tạo từ **chuỗi**:

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("10000000000000000");
BigDecimal b = new BigDecimal("-10000000000000000");
BigDecimal c = new BigDecimal("1");

BigDecimal exact = a.add(b).add(c);
System.out.println(exact); // 1
```

Các phép cộng `BigDecimal` không dùng `MathContext` giới hạn độ chính xác được thực hiện chính xác theo số thập phân của các toán hạng (giả sử đủ tài nguyên). Nếu áp dụng `MathContext` với độ chính xác giới hạn hoặc làm tròn từng bước, **thứ tự ghép vẫn có thể ảnh hưởng kết quả**. `BigDecimal` không tự chữa một dữ liệu `double` đã làm tròn trước đó; `new BigDecimal("0.1")` biểu diễn 0.1 thập phân chính xác, còn `new BigDecimal(0.1)` phản ánh giá trị nhị phân của `double 0.1`.

## 13. Cẩn trọng khi kiểm thử so sánh gần bằng

Vì `double` có sai số, nhiều thuật toán không nên so sánh kết quả gần đúng với `==` nếu chúng được tạo qua các chuỗi phép tính khác nhau. Nhưng **không có một epsilon cố định phù hợp mọi bài toán**.

Một phép kiểm tra bằng dung sai tuyệt đối + tương đối có dạng:

```java
static boolean nearlyEqual(double a, double b,
                           double absTolerance, double relTolerance) {
    if (Double.isNaN(a) || Double.isNaN(b)) {
        return false;
    }
    if (a == b) {
        return true; // kể cả hai vô cực cùng dấu hoặc +0.0 == -0.0
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }

    double diff = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return diff <= Math.max(absTolerance, relTolerance * scale);
}
```

Đây là **phương thức minh họa**, cần đặt trong một lớp. Các ngưỡng dung sai phải **không âm, hữu hạn và phù hợp bài toán**; trong code nghiệp vụ phải validate chúng. Công thức này là mẫu kiểm tra cơ bản, không phải giải pháp tổng quát cho mọi miền độ lớn vì chính phép trừ/trị tuyệt đối/nhân dung sai cũng có thể chịu ảnh hưởng của tràn, underflow và làm tròn.

Trong kiểm thử, phân biệt:

- **Tính đúng theo bit**: yêu cầu kết quả đúng chính xác như đặc tả thuật toán/quy trình tính, kể cả dấu số 0 khi có ý nghĩa.
- **Tính gần đúng số học**: sai khác có thể chấp nhận trong dung sai được chọn.
- **Tính đúng nghiệp vụ**: chẳng hạn số tiền cần làm tròn thập phân theo quy tắc cố định, tốt hơn là thiết kế với `BigDecimal` ngay từ đầu.

Không dùng `Double.MIN_VALUE` làm epsilon chung: đó là số dương subnormal nhỏ nhất, không phản ánh sai số gần 1, 100 hay `10^16`.

## 14. Chương trình Java 17+ chạy độc lập

Tạo tệp **`FloatingPointAssociativityDemo.java`**:

```java
import java.math.BigDecimal;

public class FloatingPointAssociativityDemo {
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
        double a = 1.0e16;
        double b = -1.0e16;
        double c = 1.0;

        System.out.println("(a+b)+c = " + ((a + b) + c));
        System.out.println("a+(b+c) = " + (a + (b + c)));
        System.out.println("same = " + (((a + b) + c) == (a + (b + c))));
        System.out.println("ulp(1e16) = " + Math.ulp(a));
        System.out.println("-1e16 + 1 == -1e16 = " + (b + c == b));

        double big = 1.0e308;
        System.out.println("multiply left = " + ((big * 2.0) * 0.5));
        System.out.println("multiply right = " + (big * (2.0 * 0.5)));

        double min = Double.MIN_VALUE;
        System.out.println("underflow left = " + ((min * 0.5) * 2.0));
        System.out.println("underflow right = " + (min * (0.5 * 2.0)));

        double[] values = {1.0e16, 1.0, -1.0e16};
        double naive = 0.0;
        for (double x : values) {
            naive += x;
        }
        System.out.println("naive sum = " + naive);
        System.out.println("compensated sum = " + neumaierSum(values));

        BigDecimal decimal = new BigDecimal("10000000000000000")
                .add(new BigDecimal("-10000000000000000"))
                .add(new BigDecimal("1"));
        System.out.println("decimal exact = " + decimal);
    }
}
```

Biên dịch/chạy trong thư mục chứa tệp:

```bash
javac FloatingPointAssociativityDemo.java
java FloatingPointAssociativityDemo
```

**Đầu ra chuẩn với Java 17+:**

```text
(a+b)+c = 1.0
a+(b+c) = 0.0
same = false
ulp(1e16) = 2.0
-1e16 + 1 == -1e16 = true
multiply left = Infinity
multiply right = 1.0E308
underflow left = 0.0
underflow right = 4.9E-324
naive sum = 0.0
compensated sum = 1.0
decimal exact = 1
```

**Đọc kết quả:**

1. Vế trái của phép cộng làm triệt tiêu hai số lớn trước, giữ lại 1; vế phải làm tròn mất số hạng +1 trước khi triệt tiêu.
2. `Math.ulp(1e16)` bằng 2.0 giải thích vì sao cộng 1.0 vào số có độ lớn `10^16` không thay đổi kết quả.
3. Trong phép nhân, overflow trung gian biến vế trái thành Infinity dù vế phải còn hữu hạn.
4. Trong ví dụ underflow, nhân với 0.5 trước làm mất `Double.MIN_VALUE`, trong khi nhân các hệ số 0.5 và 2.0 trước bảo toàn nó.
5. Tổng tuần tự của mảng `{1e16,1,-1e16}` mất số 1; tổng có bù theo Neumaier giữ lại trong trường hợp này. `BigDecimal` với đầu vào thập phân được định nghĩa chính xác cũng cho 1.

## 15. Sai lầm phổ biến

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Cộng và nhân trên `double` cũng luôn kết hợp vì toán học nói vậy” | Các phép toán `double` có làm tròn trung gian nên không bảo đảm tính kết hợp |
| “Chỉ số thập phân như 0.1 mới gây sai số” | Ngay các số `1e16` và `1.0` có thể lưu chính xác vẫn gặp hấp thụ khi cộng |
| “Dấu ngoặc chỉ để dễ đọc” | Với `float`/`double`, đổi ngoặc có thể đổi giá trị cuối |
| “Associativity cú pháp bằng tính kết hợp toán học” | `a+b+c` nhóm trái theo cú pháp; điều này không bảo đảm `a+(b+c)` cùng kết quả |
| “Phép `*` ưu tiên cao nên hàm tạo toán hạng phải chạy trước hàm bên trái” | Ưu tiên là cách nhóm; Java quy định thứ tự đánh giá toán hạng riêng |
| “`double` có miền lớn nên mọi số nguyên đều cách nhau 1” | Khoảng cách ULP tăng theo độ lớn; quanh `1e16` khoảng cách là 2 |
| “Overflow luôn ném exception như chia nguyên cho 0” | IEEE 754 có thể trả ±Infinity; underflow có thể trả subnormal hoặc ±0 |
| “`Math.fma` khiến tất cả phép tính thành chính xác vô hạn” | FMA chỉ làm tròn một lần cho phép nhân-cộng riêng, không loại bỏ mọi sai số |
| “Kahan/Neumaier luôn cho đáp án toán học chính xác” | Chúng giảm sai số nhiều trường hợp nhưng không phải số học chính xác vô hạn |
| “BigDecimal luôn kết hợp dù giới hạn precision” | Khi làm tròn từng bước bằng `MathContext`, các cách nhóm có thể khác |
| “`Double.MIN_VALUE` dùng làm epsilon cho mọi so sánh” | Đây là subnormal nhỏ nhất, không phải ngưỡng sai số chung |
| “Parallel sum luôn cho đúng cùng bit như sequential sum” | Nhóm phép cộng khác có thể cho kết quả khác, trừ khi thuật toán cam kết quy tắc gộp phù hợp |

## 16. Bài tập tự kiểm tra và lời giải

**Bài 1.** Với `a=1e16`, `b=-1e16`, `c=1.0` kiểu `double`, tính `(a+b)+c` và `a+(b+c)`.  
**Đáp án:** `1.0` và `0.0`. `b+c` được làm tròn về `b` do khoảng cách hai `double` kề nhau quanh `1e16` là 2.

**Bài 2.** Vì sao `double x=1e16; x+1.0 == x` cho `true`?  
**Đáp án:** `Math.ulp(1e16) == 2.0`, cộng thêm 1 rơi đúng điểm giữa hai số biểu diễn được và làm tròn về `x` theo ties-to-even.

**Bài 3.** `(1e308*2.0)*0.5` và `1e308*(2.0*0.5)` in gì?  
**Đáp án:** `Infinity` và `1.0E308`. Vế trái overflow trung gian, vế phải nhân 2 với 0.5 trước tạo 1.

**Bài 4.** `(Double.MIN_VALUE*0.5)*2` có giữ được `Double.MIN_VALUE` không?  
**Đáp án:** Không, ra `0.0` do underflow ở phép nhân đầu. `Double.MIN_VALUE*(0.5*2)` vẫn bằng `Double.MIN_VALUE`.

**Bài 5.** Vì sao `0.1+0.2 == 0.3` cho `false` chưa tự chứng minh mất tính kết hợp?  
**Đáp án:** Vì đó là một phép cộng và một phép so sánh, chưa so sánh hai cách nhóm **cùng ba số hạng**. Nó minh họa sai số biểu diễn/làm tròn.

**Bài 6.** `a+b+c` trên Java được nhóm như thế nào? Nó có tự động tương đương `a+(b+c)` không?  
**Đáp án:** Nhóm `(a+b)+c` theo kết hợp cú pháp trái. Với dấu phẩy động, **không bảo đảm** cho cùng giá trị với nhóm phải.

**Bài 7.** Nếu thứ tự cộng mảng ảnh hưởng kết quả, cần làm gì để một báo cáo có thể tái lập?  
**Đáp án:** Quy định thuật toán cộng, thứ tự đầu vào/cách phân nhóm và quy tắc kiểm thử; khi cần độ chính xác tốt hơn có thể dùng tổng có bù hoặc phương pháp phù hợp miền nghiệp vụ. Nếu cần số thập phân chính xác, cân nhắc `BigDecimal` có quy tắc precision/rounding rõ ràng.

**Bài 8.** Dùng `BigDecimal` có bảo đảm nhóm nào cũng giống nhau không?  
**Đáp án:** Với các toán hạng thập phân chính xác và các phép cộng không làm tròn theo precision giới hạn, tính kết hợp của cộng được bảo toàn (khi phép tính hoàn tất). Nhưng đặt `MathContext` giới hạn độ chính xác và làm tròn ở từng bước có thể làm kết quả phụ thuộc thứ tự.

## 17. Checklist khi thiết kế phép tính dấu phẩy động

1. **Ghi rõ kiểu:** `float` hay `double`? Có chuyển kiểu, literal hậu tố `f`, hoặc chuyển dữ liệu từ decimal không?
2. **Xem cách nhóm:** `(a+b)+c` khác `a+(b+c)` không chỉ trên giấy mà có thể khác kết quả runtime.
3. **Xác định tỷ lệ độ lớn:** có cộng giá trị cực nhỏ vào accumulator lớn? Dùng `Math.ulp` để đánh giá độ phân giải quanh giá trị thực tế.
4. **Theo dõi overflow/underflow:** kết quả trung gian có thể thành vô cực hoặc 0 trước khi các hệ số khác được nhân vào.
5. **Chú ý dấu và special values:** `NaN`, ±Infinity, ±0.0 có các quy tắc riêng; kiểm tra `isFinite`/`isNaN` khi cần.
6. **Đừng đổi ngoặc vô điều kiện:** compiler cũng không được biến đổi làm sai ngữ nghĩa Java; phép biến đổi đại số cần chứng minh an toàn với miền dữ liệu.
7. **Chọn thuật toán tổng:** tuần tự, pairwise, compensated hoặc phương thức đáp ứng yêu cầu bit-level reproducibility.
8. **Định rõ sai số chấp nhận:** kiểm thử dung sai theo đơn vị và độ lớn nghiệp vụ; không dùng `MIN_VALUE` như epsilon dùng chung.
9. **Dùng BigDecimal khi phù hợp:** nhất là dữ liệu tiền/decimal có quy tắc làm tròn; tạo giá trị từ chuỗi và hiểu ảnh hưởng của `MathContext`.
10. **Kiểm tra bằng ví dụ phản chứng:** dùng `1e16`, `-1e16`, `1.0`, trường hợp overflow/underflow để chứng minh trực tiếp khi cần giải thích code.

**Ghi nhớ:** Số thực toán học có phép cộng và phép nhân kết hợp, nhưng `float`/`double` thực hiện **phép toán rồi làm tròn ở từng bước**. Đổi ngoặc làm đổi vị trí làm tròn, nên kết quả có thể đổi. Lập trình Java đúng cần tôn trọng **cách nhóm**, **thứ tự đánh giá**, **giới hạn biểu diễn** và **thuật toán tổng** thay vì giả định mọi biến đổi đại số đều giữ nguyên kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-008">Mất thông tin khi cộng số rất nhỏ vào số rất lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Mất thông tin khi cộng số rất nhỏ vào số rất lớn trong Java

## 1. Hiện tượng: cộng một số khác 0 nhưng kết quả không đổi

Trong toán học, nếu `a` là số hữu hạn và `b > 0` thì `a + b > a`. Tuy nhiên, đối với `float` và `double` của Java, kết quả phép cộng phải được biểu diễn bằng một số lượng **bit có nghĩa hữu hạn**, nên phép cộng một số rất nhỏ vào một số rất lớn có thể **không làm thay đổi giá trị lưu**.

```java
double big = 1e16;
double small = 1.0;

System.out.println(big + small == big); // true
System.out.println((big + small) - big); // 0.0
```

Kết quả toán học của `10^16 + 1` là `10_000_000_000_000_001`. Nhưng một giá trị `double` gần `10^16` không thể biểu diễn chính xác mọi số nguyên liên tiếp. Khi làm tròn kết quả phép cộng về giá trị `double` gần nhất, `big + 1.0` trở thành **chính `big`**.

Hiện tượng này thường gọi là **absorption** (số hạng nhỏ bị “nuốt mất”), hoặc **loss of significance** khi xét việc đóng góp của số hạng nhỏ không còn thể hiện trong kết quả. Nó khác với tràn số nguyên (*integer overflow*) và cũng không cần tạo ra `Infinity`/`NaN`.

Điều cần nhớ ngay:

- `double` có miền độ lớn rất rộng nhưng **không có độ chính xác vô hạn**.
- Độ chính xác phụ thuộc **khoảng cách giữa những số có thể biểu diễn** quanh giá trị hiện tại, không chỉ giá trị tuyệt đối của số được cộng.
- Số hạng nhỏ vẫn là một số hợp lệ, khác `0`. Nó bị mất do **làm tròn kết quả phép toán**.
- Chuyển kết quả đã làm tròn sang kiểu khác hoặc định dạng in ra nhiều chữ số **không khôi phục** phần thông tin đã mất.

## 2. Vì sao số rất lớn lại có bước nhảy giữa các giá trị?

Một `double` thông thường tuân theo định dạng dấu phẩy động nhị phân 64 bit, với **53 bit chính xác của phần trị** (*significand*, kể cả bit dẫn đầu ngầm đối với số chuẩn hóa). `float` có **24 bit chính xác của phần trị**.

Với số chuẩn hóa trong khoảng `2^e <= |x| < 2^(e+1)`, khoảng cách giữa những giá trị nhị phân kề nhau trong cùng miền lũy thừa hai là:

```text
ULP(double) = 2^(e - 52)
ULP(float)  = 2^(e - 23)
```

Trong đó **ULP** (*unit in the last place*) là đơn vị ở vị trí bit có nghĩa thấp nhất tại độ lớn đang xét. Khoảng cách thay đổi theo số mũ; càng xa 0 thì những giá trị dấu phẩy động có thể biểu diễn càng thưa.

Ví dụ:

| Độ lớn của giá trị | Khoảng cách gần đó của `double` | Ý nghĩa |
| --- | ---: | --- |
| `1.0` | `2^-52`, khoảng `2.22e-16` ở phía trên | Có thể biểu diễn những thay đổi rất nhỏ gần 1 |
| `2^52` | `1.0` | Mọi số nguyên liên tiếp trong vùng này còn có thể phân biệt |
| `2^53` | `2.0` | Số nguyên kế tiếp `2^53+1` không còn biểu diễn riêng |
| `1e16` | `2.0` | Cộng `1.0` có thể làm tròn về chính số cũ |
| `2^54` | `4.0` | Phải thay đổi lớn hơn mới chắc chắn có giá trị khác |

Bảng xét các giá trị chuẩn hóa và bước nhảy về phía số lớn hơn tại các mốc; ở **ranh giới lũy thừa của hai**, khoảng cách về phía dưới có thể nhỏ hơn khoảng cách về phía trên. Không nên hiểu một ULP là hằng số duy nhất cho mọi giá trị `double`.

Java cung cấp API kiểm tra ngay:

```java
double x = 1e16;
System.out.println(Math.ulp(x));          // 2.0
System.out.println(Math.nextUp(x) - x);   // 2.0
```

`Math.ulp(x)` trả về kích thước ULP theo định nghĩa Java; `Math.nextUp(x)` trả về giá trị `double` kế tiếp lớn hơn `x`. Nhờ đó có thể nhìn thấy khoảng cách biểu diễn mà không phải tự đọc từng bit IEEE 754.

## 3. Cộng `1.0` vào `1e16`: phân tích từng bước

Ở gần `1e16`, các giá trị `double` kề nhau theo chiều tăng là:

```text
10000000000000000   (big)
10000000000000002   (Math.nextUp(big))
10000000000000004
...
```

Giá trị toán học `10000000000000001` nằm **đúng giữa** hai giá trị đầu tiên. Java dùng quy tắc làm tròn mặc định của phép toán dấu phẩy động: **gần nhất, trường hợp hòa chọn bit cuối chẵn** (*round to nearest, ties to even*). Trong ví dụ này, giá trị được chọn là `10000000000000000`, nên:

```java
double big = 1e16;

System.out.println(big + 1.0 == big); // true
System.out.println(big + 2.0 == big); // false
System.out.println(big + 2.0 - big);  // 2.0
```

Không phải cứ số hạng nhỏ hơn một ULP thì kết quả **luôn** bị bỏ qua. Kết quả phụ thuộc khoảng cách tới hai giá trị biểu diễn gần nhất và quy tắc làm tròn. Ví dụ, gần `big`, một số hạng có độ lớn **nhỏ hơn một nửa khoảng cách lên số kế tiếp** sẽ không đổi kết quả khi làm tròn gần nhất; còn trường hợp đúng giữa cần áp dụng quy tắc xử lý hòa.

Trong mô hình tính toán, trước phép cộng phần trị của hai số phải được **căn chỉnh số mũ**. Khi một số có số mũ lớn hơn hẳn số còn lại, các bit của số nhỏ bị dịch tương đối rất xa. Kết quả toán học có thể chứa những bit thấp không còn đủ chỗ trong 53 bit có nghĩa của `double`; quá trình làm tròn loại bỏ đóng góp đó. Đây là cách hiểu trực quan của hiện tượng absorption.

**Chú ý:** Cả `big` lẫn `1.0` trong ví dụ đều biểu diễn **chính xác** bằng `double`. Phần thông tin bị mất ở **kết quả phép cộng**, khác với trường hợp literal `0.1` đã cần xấp xỉ ngay khi được lưu.

## 4. `float` gặp hiện tượng này sớm hơn `double`

Vì phần trị của `float` chỉ có 24 bit chính xác, nó không phân biệt được mọi số nguyên liên tiếp khi độ lớn vượt `2^24`.

```java
float big = 16_777_216.0f; // 2^24
float small = 1.0f;

System.out.println(big + small == big); // true
System.out.println(Math.ulp(big));      // 2.0
```

Trong khi đó, `double` có 53 bit chính xác, nên cùng phép cộng với `16_777_216.0` và `1.0` vẫn giữ được kết quả chính xác:

```java
double big = 16_777_216.0;
System.out.println(big + 1.0); // 1.6777217E7
```

`1.6777217E7` là cách `Double.toString` in số `16777217.0` ở ký pháp khoa học trong ví dụ này. Việc chọn `double` thay `float` **giảm** nguy cơ mất số hạng nhỏ tại một độ lớn nhất định, nhưng không giải quyết bài toán vô hạn: ở độ lớn đủ cao, `double` cũng phải làm tròn.

| Kiểu | Số bit có nghĩa | Mốc mà phép cộng 1 có thể bị mất |
| --- | ---: | --- |
| `float` | 24 | `2^24 + 1` không có biểu diễn `float` riêng; `2^24f + 1f` làm tròn về `2^24f` |
| `double` | 53 | `2^53 + 1` không có biểu diễn `double` riêng; `2^53 + 1.0` làm tròn về `2^53` |

Vì vậy, câu hỏi quan trọng không chỉ là “số của tôi có nằm trong miền `float`/`double` không?” mà là **“ở độ lớn đó, kiểu đang dùng còn phân biệt được bước thay đổi mà tôi quan tâm không?”**

## 5. Mất số hạng nhỏ dẫn đến mất tính kết hợp của phép cộng

Trong toán học, phép cộng số thực có tính kết hợp:

```text
(a + b) + c = a + (b + c)
```

Đối với số học dấu phẩy động, kết quả trung gian được làm tròn, nên biểu thức có cách nhóm khác nhau có thể cho kết quả khác nhau:

```java
double a = 1e16;
double b = -1e16;
double c = 1.0;

double leftGrouped = (a + b) + c;
double rightGrouped = a + (b + c);

System.out.println(leftGrouped);  // 1.0
System.out.println(rightGrouped); // 0.0
```

Giải thích:

1. `(a + b) + c`: hai số lớn trái dấu `1e16` và `-1e16` triệt tiêu **chính xác** thành `0.0`, rồi cộng `1.0` cho `1.0`.
2. `a + (b + c)`: trước hết tính `-1e16 + 1.0`; số `1.0` quá nhỏ so với khoảng cách giữa các giá trị biểu diễn ở độ lớn đó, nên kết quả được làm tròn về `-1e16`. Sau đó cộng `a` cho `0.0`.

Hai kết quả khác nhau dù các toán hạng là cùng ba giá trị. Đây là lý do **thứ tự cộng và cách nhóm phép toán có thể ảnh hưởng đến kết quả** trong tính toán dấu phẩy động.

Trong những thuật toán thống kê, tổng số đo, mô phỏng và phép tính số học, đừng tự ý dùng các phép biến đổi đại số trên số thực máy như thể mọi phép cộng đều chính xác. Trình biên dịch Java tuân thủ những quy tắc đánh giá dấu phẩy động của ngôn ngữ; tuy nhiên, **doanh nghiệp/người viết thuật toán** vẫn phải lựa chọn công thức và thứ tự xử lý phù hợp với yêu cầu độ chính xác.

## 6. Cộng dồn nhiều số hạng nhỏ vào tổng rất lớn

Một ví dụ thực tế: ban đầu có tổng `1e16`, sau đó cộng `1.0` mười lần.

```java
double total = 1e16;

for (int i = 0; i < 10; i++) {
    total += 1.0;
}

System.out.println(total - 1e16); // 0.0
```

Toán học yêu cầu tổng tăng thêm `10`. Nhưng mỗi lần cộng `1.0`, phép cộng được làm tròn ngay về `1e16`, nên tổng **không tích lũy được bất kỳ đơn vị nào**.

Khác biệt quan trọng: nếu tạo tổng các phần nhỏ **trước khi** cộng vào số lớn, kết quả có thể được giữ:

```java
double big = 1e16;
double increments = 0.0;
for (int i = 0; i < 10; i++) {
    increments += 1.0;
}

System.out.println((big + increments) - big); // 10.0
```

Khi đó `increments` bằng `10.0`, lớn hơn nhiều so với bước biểu diễn `2.0` gần `big`, nên lần cộng cuối giữ được thông tin.

Nhưng **không có cách sắp xếp thứ tự cộng đơn giản nào đúng nhất cho mọi dữ liệu**. Ví dụ cộng các số âm/dương lớn có thể gây *catastrophic cancellation* (mất độ chính xác tương đối khi hai số gần bằng nhau bị trừ/triệt tiêu). Việc nhóm các số nhỏ trước chỉ là một kỹ thuật hữu ích trong những trường hợp xác định được cấu trúc dữ liệu.

## 7. Dùng thuật toán cộng có bù sai số: Kahan summation

Khi phải cộng nhiều giá trị dấu phẩy động, một kỹ thuật là **Kahan compensated summation**. Nó giữ thêm một biến bù để theo dõi phần nhỏ bị mất trong phép cộng trước và có cơ hội đưa phần đó vào các phép cộng tiếp theo.

```java
static double kahanAddOnTop(double start, double[] values) {
    double sum = start;
    double compensation = 0.0;

    for (double value : values) {
        double adjusted = value - compensation;
        double updated = sum + adjusted;
        compensation = (updated - sum) - adjusted;
        sum = updated;
    }

    return sum;
}
```

Đoạn này là phương thức minh họa cần đặt vào một lớp Java để chạy. Ba bước bên trong vòng lặp:

1. `adjusted = value - compensation` điều chỉnh giá trị sắp cộng bằng phần dư đã ghi nhận.
2. `updated = sum + adjusted` tạo tổng mới (vẫn có thể bị làm tròn).
3. `compensation = (updated - sum) - adjusted` ghi lại phần bị mất trong phép cộng vừa rồi để điều chỉnh về sau.

Ví dụ khi cộng mười số `1.0` vào `1e16`, thuật toán trên có thể giữ được mức tăng tổng cộng `10.0`, trong khi cộng ngây thơ từng lần cho mức tăng `0.0`.

**Giới hạn của Kahan:**

- Nó giúp giảm sai số tích lũy trong nhiều bài toán nhưng **không biến `double` thành số chính xác vô hạn**.
- Giá trị `sum` trả về vẫn là một `double`. Nếu kết quả toán học cuối cùng nằm giữa hai giá trị `double` mà không thể biểu diễn, hàm vẫn phải làm tròn.
- Với một phép cộng duy nhất `1e16 + 1.0`, biến bù có thể ghi nhận phần nhỏ cho **bước tiếp theo**, nhưng trả về mỗi `sum` vẫn không biểu diễn được `1e16 + 1`.
- Các thuật toán thay thế (Neumaier, pairwise summation, phép toán đa độ chính xác) phù hợp trong những bối cảnh khác nhau; cần đo chất lượng và chi phí theo dữ liệu thực.

Nếu một API đòi hỏi kết quả không sai một đơn vị, việc dùng một thuật toán dấu phẩy động ổn định hơn **chưa chắc** đáp ứng được hợp đồng. Khi đó cần chọn cách biểu diễn chính xác phù hợp với miền giá trị.

## 8. Khi nào dùng `Math.ulp`, `Math.nextUp` và ngưỡng sai số?

`Math.ulp(x)` đo **độ phân giải xấp xỉ tại độ lớn của `x`**, rất hữu ích để phát hiện một số hạng nhỏ có nguy cơ bị nuốt.

```java
double big = 1e16;
double step = Math.ulp(big);

System.out.println(step);               // 2.0
System.out.println(big + step == big);  // false
```

Tuy nhiên, không nên áp dụng mù quáng quy tắc **“cứ số nhỏ hơn ULP thì chắc chắn bằng 0”**. Một số hạng nằm giữa khoảng cách biểu diễn có thể được làm tròn theo hướng tăng/giảm; việc xử lý hòa cũng phụ thuộc bit cuối của phần trị. Kỹ thuật dùng ULP là để **đánh giá độ phân giải**, không phải thay cho phân tích sai số đầy đủ.

Khi so sánh hai kết quả số thực gần nhau, có thể cân nhắc ngưỡng tuyệt đối và tương đối:

```java
static boolean nearlyEqual(double a, double b,
                           double absTol, double relTol) {
    if (a == b) {
        return true; // Kể cả hai vô cực cùng dấu.
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false; // Bao gồm NaN và vô cực không cùng giá trị.
    }

    double difference = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return difference <= Math.max(absTol, relTol * scale);
}
```

Đây là **một chính sách so sánh gần đúng**, không phải cách phục hồi giá trị đã mất. Ví dụ `big + 1.0 == big` là `true` ngay ở mức biểu diễn; `nearlyEqual` không thể biết rằng người dùng từng mong có thêm `1.0`.

Ngưỡng phải phù hợp **đơn vị đo, độ lớn dữ liệu và yêu cầu nghiệp vụ**. Công thức trên là minh họa đơn giản; dữ liệu cực lớn/cực nhỏ còn cần xét overflow/underflow trong tính ngưỡng, `NaN` và `Infinity`. Không dùng một epsilon tùy tiện cho tất cả ứng dụng.

## 9. Định dạng số và chuyển kiểu không khôi phục thông tin

Một số người thử in nhiều chữ số hơn:

```java
double big = 1e16;
double rounded = big + 1.0;
System.out.printf("%.0f%n", rounded); // 10000000000000000
```

Lệnh định dạng chỉ in giá trị **đã được tính và làm tròn**. Không có thông tin nào còn lại trong `rounded` để biết phép cộng ban đầu có số `1.0`.

Tương tự, gán kết quả sang `BigDecimal` **sau** phép tính cũng quá muộn:

```java
import java.math.BigDecimal;

double wrong = 1e16 + 1.0;
BigDecimal after = BigDecimal.valueOf(wrong);
System.out.println(after.toPlainString()); // 10000000000000000
```

`BigDecimal.valueOf(double)` dùng cách chuyển `double` đã có thành chuỗi rồi tạo đối tượng thập phân; nó không lưu được chữ số `1` vốn đã biến mất ở phép cộng `double`.

Muốn giữ giá trị thập phân chính xác, chuyển dữ liệu sang biểu diễn chính xác **trước phép toán**:

```java
BigDecimal big = new BigDecimal("10000000000000000");
BigDecimal one = BigDecimal.ONE;
BigDecimal exact = big.add(one);

System.out.println(exact); // 10000000000000001
```

`BigDecimal` giúp biểu diễn các số thập phân với độ chính xác tùy ý trong giới hạn tài nguyên; các phép chia có khai triển vô hạn vẫn có thể cần `MathContext`/`RoundingMode`. Nó phù hợp khi yêu cầu nghiệp vụ cần chính xác từng chữ số thập phân, nhưng không có nghĩa là nên thay mọi phép tính số học khoa học bằng `BigDecimal`.

### Dùng số nguyên khi bản chất dữ liệu là số đếm

Nếu đang đếm sản phẩm, số lượng giao dịch hoặc đơn vị tiền nhỏ nhất, cách lưu bằng `int`/`long` có thể phù hợp hơn số thực:

```java
long base = 10_000_000_000_000_000L;
long exact = base + 1L;
System.out.println(exact); // 10000000000000001
```

`long` có thể giữ chính xác số nguyên này vì nằm trong miền 64 bit; **nhưng `long` vẫn có nguy cơ tràn** nếu vượt giới hạn. Khi vượt cả `long`, dùng `BigInteger`. Phân biệt “kiểu nguyên chính xác trong miền” với “số thực nhị phân xấp xỉ ở độ lớn lớn” giúp chọn mô hình dữ liệu đúng.

## 10. Liên hệ với hiện tượng mất chính xác do trừ hai số gần nhau

**Absorption** và **catastrophic cancellation** đều liên quan độ chính xác dấu phẩy động, nhưng không phải một hiện tượng:

- **Absorption:** cộng một số hạng quá nhỏ vào một số rất lớn; phần đóng góp nhỏ không thể hiện trong kết quả làm tròn. Ví dụ `1e16 + 1.0 == 1e16`.
- **Cancellation:** lấy hiệu hai giá trị gần nhau; những chữ số có nghĩa chung triệt tiêu, và sai số đã tồn tại trong các toán hạng có thể chiếm tỷ lệ lớn trong hiệu. Ví dụ nếu hai số đã được làm tròn thành cùng mẫu bit, phép trừ cho `0.0` dù hiệu giữa hai giá trị lý tưởng khác 0.

Ví dụ:

```java
double a = 1e16 + 1.0; // Thành 1e16 ngay lúc cộng.
double b = 1e16;
System.out.println(a - b); // 0.0
```

Nếu `a` ban đầu được thiết kế để chứa giá trị toán học `1e16 + 1`, phần `1` đã mất **trước** khi thực hiện phép trừ. Thay công thức `a - b` hoặc ép kết quả sang `long` không hồi phục thông tin; phải chọn biểu diễn và thuật toán phù hợp từ bước tạo toán hạng.

## 11. Ví dụ Java 17+ độc lập và kết quả

Tạo tệp **`TinyPlusHugeDemo.java`**:

```java
import java.math.BigDecimal;

public class TinyPlusHugeDemo {
    public static void main(String[] args) {
        double big = 1e16;

        System.out.println("ULP at 1e16 = " + Math.ulp(big));
        System.out.println("next gap = " + (Math.nextUp(big) - big));
        System.out.println("big + 1 == big = " + (big + 1.0 == big));
        System.out.println("big + 2 == big = " + (big + 2.0 == big));

        float floatBig = 16_777_216f;
        System.out.println("float big + 1 == big = "
                + (floatBig + 1.0f == floatBig));

        double a = 1e16;
        double b = -1e16;
        double c = 1.0;
        System.out.println("(a+b)+c = " + ((a + b) + c));
        System.out.println("a+(b+c) = " + (a + (b + c)));

        double naive = big;
        double kahan = big;
        double compensation = 0.0;

        for (int i = 0; i < 10; i++) {
            naive += 1.0;

            double adjusted = 1.0 - compensation;
            double updated = kahan + adjusted;
            compensation = (updated - kahan) - adjusted;
            kahan = updated;
        }

        System.out.println("naive delta = " + (naive - big));
        System.out.println("Kahan delta = " + (kahan - big));

        BigDecimal exact = new BigDecimal("10000000000000000")
                .add(BigDecimal.ONE);
        System.out.println("BigDecimal exact = " + exact);

        long integerExact = 10_000_000_000_000_000L + 1L;
        System.out.println("long exact = " + integerExact);
    }
}
```

Biên dịch và chạy:

```bash
javac TinyPlusHugeDemo.java
java TinyPlusHugeDemo
```

Kết quả:

```text
ULP at 1e16 = 2.0
next gap = 2.0
big + 1 == big = true
big + 2 == big = false
float big + 1 == big = true
(a+b)+c = 1.0
a+(b+c) = 0.0
naive delta = 0.0
Kahan delta = 10.0
BigDecimal exact = 10000000000000001
long exact = 10000000000000001
```

Ví dụ chứng minh hai khía cạnh: giá trị `1.0` có thể **bị mất trong từng phép cộng `double`**, nhưng thuật toán tổng có bù có thể thu hồi các phần bị mất ở **các lần cộng sau**. Dữ liệu cần chính xác tuyệt đối từng đơn vị nên dùng số nguyên đủ rộng hoặc `BigDecimal`/`BigInteger` thay vì mong số thực nhị phân tự giải quyết mọi trường hợp.

## 12. Những hiểu lầm và lỗi phổ biến

| Hiểu lầm | Thực tế |
| --- | --- |
| “Số nhỏ là `1.0` nên không thể tự biến thành 0.” | Số hạng không biến thành `0`; **kết quả cộng sau làm tròn** có thể trùng số lớn ban đầu. |
| “Chỉ khi phép toán tràn `Infinity` mới mất dữ liệu.” | Mất độ phân giải diễn ra với giá trị hoàn toàn hữu hạn như `1e16`. |
| “`double` giữ mọi số nguyên nếu chúng nhỏ hơn `Double.MAX_VALUE`.” | Miền độ lớn và số bit chính xác là hai khái niệm khác nhau. |
| “Cứ tăng từ `float` sang `double` là hết lỗi.” | `double` có độ phân giải tốt hơn nhưng vẫn hữu hạn. |
| “Cộng theo thứ tự nào cũng cho cùng kết quả.” | Phép cộng dấu phẩy động không đảm bảo tính kết hợp như số học thực lý tưởng. |
| “Kahan đảm bảo kết quả chính xác tuyệt đối.” | Nó giảm sai số cho nhiều dãy cộng, nhưng kết quả `double` vẫn phải làm tròn. |
| “Một epsilon cố định giải quyết mọi vấn đề.” | Ngưỡng gần đúng phụ thuộc độ lớn, đơn vị và tính đúng đắn nghiệp vụ. |
| “In ra thêm chữ số sẽ lấy lại được số nhỏ.” | Định dạng không khôi phục thông tin đã bị mất từ phép toán. |
| “Đổi sang `BigDecimal` sau phép cộng sẽ sửa được.” | Muốn tính thập phân chính xác, phải chuyển **trước** phép tính gây mất thông tin. |
| “Cứ dùng `long` thay số thực trong mọi trường hợp.” | `long` hữu hạn và chỉ biểu diễn số nguyên; số đo thực có thể cần thuật toán số ổn định. |

## 13. Cách chọn phương pháp xử lý

Trước khi cộng những giá trị rất chênh lệch, hãy đặt các câu hỏi:

1. **Độ lớn của tổng là bao nhiêu?** Ước lượng `Math.ulp(total)` ở những vùng thường gặp.
2. **Số hạng nhỏ nhất cần giữ là bao nhiêu?** Nếu nó nhỏ hơn khả năng phân giải gần tổng, phải xem xét cách tính khác.
3. **Có cộng nhiều số hạng không?** Nếu có, thứ tự cộng hoặc thuật toán Kahan/pairwise/Neumaier có thể giảm sai số tích lũy.
4. **Có các số lớn trái dấu dễ triệt tiêu nhau không?** Xem xét sai số tương đối và cấu trúc công thức, không chỉ tổng cuối.
5. **Yêu cầu là chính xác tuyệt đối hay chấp nhận sai số?** Số đếm chính xác có thể dùng `long`/`BigInteger`; thập phân chính xác dùng `BigDecimal` với quy tắc làm tròn phù hợp; mô phỏng/đo lường có thể dùng `double` cùng phân tích sai số.

**Kết luận:** Khi cộng số rất nhỏ vào số rất lớn, số hạng nhỏ có thể bị mất vì khoảng cách giữa các giá trị `float`/`double` có thể biểu diễn **tăng theo độ lớn của số**. Hiện tượng này làm tổng phụ thuộc thứ tự tính, gây mất đóng góp của số hạng nhỏ và có thể ảnh hưởng đến thống kê hoặc điều kiện so sánh. Hãy hiểu ULP, chọn thứ tự/thuật toán tổng phù hợp và sử dụng kiểu biểu diễn chính xác **trước khi tính** nếu bài toán không chấp nhận sai số.

**Tài liệu tham khảo:** [Java Language Specification §4.2.3 — Floating-Point Types, Formats, and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3), [JLS §15.4 — FP-strict Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.4), [Math.ulp API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#ulp(double)), [Math.nextUp API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#nextUp(double)), [BigDecimal API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-009">Triệt tiêu chữ số khi trừ hai số gần bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Triệt tiêu chữ số khi trừ hai số gần bằng nhau trong Java

## 1. Hiện tượng cần hiểu

Trong tính toán dấu phẩy động, **triệt tiêu chữ số** (*cancellation*) xảy ra khi trừ hai số có giá trị gần nhau. Các chữ số đầu giống nhau bị triệt tiêu, phần hiệu rất nhỏ trở thành kết quả. Nếu hai toán hạng đã chứa sai số làm tròn, sai số ấy có thể chiếm tỷ lệ rất lớn so với hiệu. Trường hợp làm mất nghiêm trọng độ chính xác tương đối thường gọi là **catastrophic cancellation**.

Ví dụ, trong số thực toán học:

\[
(10^{16}+1)-10^{16}=1.
\]

Nhưng với `double` Java:

```java
double base = 1e16;
double plusOne = base + 1.0;
double difference = plusOne - base;

System.out.println(difference);  // 0.0
```

Lý do: `1e16` là một giá trị `double` biểu diễn được, nhưng số nguyên `10^16 + 1` nằm giữa hai giá trị `double` liền kề ở vùng đó. Biểu thức `base + 1.0` được làm tròn trở về `base`. Đến lúc thực hiện phép trừ, chương trình đang trừ **hai giá trị `double` bằng nhau**, vì vậy nhận `0.0`. Giá trị thực cần tìm là `1`, tức sai số tuyệt đối bằng `1` và sai số tương đối của kết quả bằng **100%**.

Điểm cốt lõi: phép trừ có thể thực hiện hoàn toàn chính xác trên **hai số `double` đã được lưu**, nhưng vẫn khác xa hiệu của **hai giá trị toán học ban đầu**. Không nên quy mọi sai số cho riêng toán tử `-`; nguồn sai số thường xuất hiện từ bước tạo toán hạng hoặc phép tính trước đó.

## 2. Vì sao dấu phẩy động dễ gặp triệt tiêu chữ số?

`float` và `double` biểu diễn tập hữu hạn các số nhị phân, với độ chính xác phần trị lần lượt khoảng **24 bit** và **53 bit**. Không phải mọi số thực hay mọi số nguyên lớn đều biểu diễn được chính xác. Khi kết quả nằm giữa hai giá trị đại diện được, Java phải làm tròn theo quy tắc IEEE 754.

Một mô hình sai số đơn giản:

\[
\widehat a=a+\delta_a,\qquad
\widehat b=b+\delta_b
\]

trong đó `a`, `b` là các giá trị toán học dự định, còn `â`, `b̂` là hai giá trị đã lưu. Khi trừ:

\[
\widehat a-\widehat b
=(a-b)+(\delta_a-\delta_b).
\]

Nếu `a` và `b` gần bằng nhau thì `|a-b|` rất nhỏ, trong khi `δa` và `δb` có thể tương đối lớn **so với hiệu**. Ví dụ:

- Giá trị thật: `a = 1_000_000_001`, `b = 1_000_000_000`, hiệu bằng `1`.
- Giả sử hai toán hạng sau làm tròn lần lượt thành `1_000_000_000` và `1_000_000_000`. Hiệu tính được bằng `0`, dù sai số của từng toán hạng chỉ khoảng một phần tỷ so với độ lớn của chúng.

Sai số tuyệt đối của phép trừ có thể nhỏ khi so với toán hạng, nhưng sai số tương đối của kết quả có thể rất lớn. Với `float`, hiện tượng xuất hiện ở mức độ lớn thấp hơn `double` vì số bit chính xác ít hơn.

### Chỉ số nhạy cảm của phép trừ

Với `a != b`, một thước đo độ nhạy đối với sai số tương đối của hai toán hạng là:

\[
\kappa_{\mathrm{sub}}=\frac{|a|+|b|}{|a-b|}.
\]

Khi `a` và `b` gần nhau nhưng có độ lớn đáng kể, mẫu số tiến gần 0 nên `κ` rất lớn. Điều này báo rằng **sai số tương đối nhỏ ở đầu vào có thể bị khuếch đại** khi tính hiệu.

Ví dụ `a ≈ 1_000_000`, `b ≈ 999_999`, thì `|a-b| ≈ 1` và `κ` khoảng hai triệu. Đây là đặc tính độ nhạy của bài toán, không phải bằng chứng một thuật toán cụ thể đã sai. Nếu đầu vào là số chính xác tuyệt đối và hiệu vẫn biểu diễn được, phép trừ có thể không gây thêm sai số.

## 3. Triệt tiêu chữ số không đồng nghĩa “phép trừ luôn thiếu chính xác”

Có một kết quả quan trọng trong số học dấu phẩy động: **bổ đề Sterbenz (Sterbenz's lemma)**. Với hai số dấu phẩy động hữu hạn dương `x`, `y` đã biểu diễn được, nếu

\[
\frac{x}{2}\le y\le 2x,
\]

thì trong số học nhị phân tuân thủ làm tròn và xử lý số dưới chuẩn phù hợp, phép trừ `x - y` có thể được biểu diễn **chính xác**. Java dùng IEEE 754 với số dưới chuẩn, nên đây là một cách giải thích hữu ích cho nhiều phép trừ số gần nhau.

Ví dụ:

```java
double a = Math.nextUp(1.0);  // 1.0000000000000002
double b = 1.0;
double delta = a - b;         // 2.220446049250313E-16
```

Cả `a`, `b` là những giá trị `double` xác định và hiệu của chúng được biểu diễn chính xác. Nếu ta **định nghĩa bài toán trên đúng hai giá trị `double` này**, phép trừ không mất thêm chữ số.

Nhưng nếu `a` và `b` là kết quả của những bước tính gần đúng trước đó, hiệu đúng của **dữ liệu ban đầu** vẫn có thể khác nhiều. Do đó:

1. **Cancellation** mô tả các chữ số đầu triệt tiêu khi lấy hiệu.
2. **Catastrophic cancellation** nhấn mạnh việc sai số có trước bị phóng đại tương đối đến mức làm kết quả kém tin cậy.
3. **Làm tròn của phép trừ** là vấn đề riêng; đôi khi phép trừ giữa hai số `double` gần nhau được thực hiện chính xác.

Nhận ra khác biệt này giúp chọn đúng biện pháp: chỉnh công thức để tránh làm mất thông tin **trước phép trừ**, thay đổi cách lưu đầu vào, hoặc dùng độ chính xác cao hơn khi thực sự cần.

## 4. ULP và ví dụ mất hẳn phần chênh lệch

**ULP** (*unit in the last place*) là đơn vị khoảng cách giữa các giá trị biểu diễn ở một vùng. Java cung cấp `Math.ulp(float)` và `Math.ulp(double)` để quan sát độ lớn ULP tại giá trị cụ thể.

```java
double x = 1e16;

System.out.println(Math.ulp(x));       // 2.0
System.out.println(x + 1 == x);        // true
System.out.println((x + 1) - x);       // 0.0
System.out.println((x + 2) - x);       // 2.0
```

Ở `1e16`, khoảng cách giữa các số `double` liền kề là `2`. Do đó:

- `x + 1` nằm đúng giữa hai số biểu diễn; quy tắc hòa về phần trị chẵn làm tròn về `x`.
- `x + 2` nằm ở số `double` kế tiếp, nên hiệu là `2`.
- Không có ngoại lệ nào được ném; `0.0` là một kết quả `double` hợp lệ.

Nếu thay `double` bằng `float`, khoảng cách nhảy lên `2` ngay tại `2^24 = 16_777_216`, khiến các số nguyên gần nhau bị nhập thành cùng một giá trị sớm hơn nhiều.

Tránh suy luận rằng `double` có “15–16 chữ số thập phân” thì mọi hiệu của hai số có cỡ 15 chữ số đều có chừng ấy chữ số đáng tin. Độ chính xác đáng tin của **hiệu** còn phụ thuộc vào sai số từng toán hạng và khoảng cách giữa chúng.

## 5. Dạng công thức dễ gặp: hiệu của hai căn bậc hai

Xét biểu thức:

\[
d(x)=\sqrt{x+1}-\sqrt{x},\qquad x\ge 0.
\]

Khi `x` lớn, hai căn rất gần nhau. Nếu tính trực tiếp, sự khác biệt nhỏ có thể bị làm tròn đến 0. Nhân với biểu thức liên hợp:

\[
\begin{aligned}
d(x)
&=\frac{(\sqrt{x+1}-\sqrt{x})(\sqrt{x+1}+\sqrt{x})}
{\sqrt{x+1}+\sqrt{x}}\\
&=\frac{(x+1)-x}{\sqrt{x+1}+\sqrt{x}}\\
&=\boxed{\frac{1}{\sqrt{x+1}+\sqrt{x}}}.
\end{aligned}
\]

Công thức cuối **tránh trừ trực tiếp hai số gần bằng nhau**. Với `x = 1e16`:

```java
double x = 1e16;
double naive = Math.sqrt(x + 1.0) - Math.sqrt(x);
double stable = 1.0 / (Math.sqrt(x + 1.0) + Math.sqrt(x));

System.out.println(naive);  // 0.0
System.out.println(stable); // 5.0E-9
```

Giá trị toán học đúng xấp xỉ `5 × 10^-9`. Công thức liên hợp cho kết quả hợp lý ở đây ngay cả khi `x + 1.0` đã làm tròn thành `x`; mẫu số chủ yếu phụ thuộc vào `sqrt(x)`.

**Giới hạn cần biết:** biến đổi đại số làm phép tính ổn định hơn trong nhiều miền, không bảo đảm tránh mọi overflow/underflow hoặc làm tròn. Khi `x` gần `Double.MAX_VALUE`, riêng `x + 1` có thể vẫn bằng `x`; khi `x` quá lớn, tổng các căn có thể gây vấn đề trong những biến thể công thức khác. Phải xét miền đầu vào và yêu cầu chính xác của bài toán, nhất là với các công thức tổng quát như `sqrt(a) - sqrt(b)`, nơi `(a - b)` cũng có thể đã mất thông tin.

Một trường hợp tổng quát hữu ích, khi mẫu số khác 0:

\[
\sqrt{a}-\sqrt{b}=\frac{a-b}{\sqrt{a}+\sqrt{b}}.
\]

Nếu `a` và `b` đều là số đã làm tròn đến cùng một giá trị thì biến đổi này **không** phục hồi chênh lệch đã bị mất; cần giữ hoặc tính `a-b` theo cách chính xác hơn từ dữ liệu ban đầu.

## 6. Dùng API chuyên dụng: `Math.expm1` và `Math.log1p`

Một số hàm thư viện được thiết kế riêng cho hiệu nhỏ để tránh triệt tiêu chữ số.

### `Math.expm1(x)` thay cho `Math.exp(x) - 1`

Khi `x` gần 0, giá trị `e^x` rất gần `1`. Nếu `Math.exp(x)` được làm tròn thành `1.0`, trừ `1.0` cho `0` dù kết quả toán học gần `x`.

```java
double tiny = 1e-16;

System.out.println(Math.exp(tiny) - 1.0); // 0.0
System.out.println(Math.expm1(tiny));     // 1.0E-16
```

`Math.expm1(x)` được định nghĩa là `e^x - 1` và được triển khai để có độ chính xác cao hơn với `x` gần 0. Trong bài toán tích lũy lãi nhỏ, hàm phân phối xác suất hoặc các phép biến đổi mũ, đây thường là cách thay thế trực tiếp có ý nghĩa.

### `Math.log1p(x)` thay cho `Math.log(1 + x)`

Tương tự, khi `x` quá nhỏ, biểu thức `1.0 + x` có thể được làm tròn thành `1.0` trước khi gọi `Math.log`:

```java
double tiny = 1e-16;

System.out.println(Math.log(1.0 + tiny)); // 0.0
System.out.println(Math.log1p(tiny));     // 1.0E-16
```

`Math.log1p(x)` tính `ln(1 + x)` với độ chính xác tốt hơn gần 0. Miền số thực của `log1p(x)` là `x >= -1` (`x = -1` cho âm vô cực, `x < -1` cho `NaN`). Không thay đổi mù quáng các biểu thức đại số khác thành `log1p` nếu không có cùng ý nghĩa toán học.

Điểm chung: các API này nhận **số chênh nhỏ `x` từ đầu**, không cần dựng giá trị gần `1` rồi trừ hoặc lấy log. Nhờ vậy, phần chênh không bị đánh mất tại bước trung gian.

## 7. Nghiệm phương trình bậc hai: cách tính tránh trừ hai số gần nhau

Xét `a*x^2 + b*x + c = 0` với `a != 0`, biệt thức `D = b*b - 4*a*c >= 0`. Công thức quen thuộc:

\[
x_{1,2}=\frac{-b\pm\sqrt D}{2a}.
\]

Nếu `-b` và `sqrt(D)` gần bằng nhau, một nghiệm có thể bị sai số nghiêm trọng khi dùng dấu trừ. Ví dụ `a = 1`, `b = -10^8`, `c = 1`: nghiệm nhỏ xấp xỉ `10^-8`, còn hai số bị trừ đều cỡ `10^8`.

Một công thức thay thế khi `q != 0`:

\[
q=-\frac12\left(b+\operatorname{copySign}(\sqrt D,b)\right),
\qquad x_1=\frac qa,\quad x_2=\frac cq.
\]

Trong `Math.copySign(magnitude, sign)`, dấu của tham số thứ hai được áp lên độ lớn của tham số thứ nhất. Cách tính `q` chọn phép **cộng hai lượng cùng dấu** để tránh triệt tiêu ở nghiệm lớn; nghiệm còn lại dùng tích hai nghiệm `x1*x2 = c/a`.

Ví dụ minh họa từng phép tính:

```java
double a = 1.0;
double b = -1e8;
double c = 1.0;
double discriminant = b * b - 4 * a * c;
double root = Math.sqrt(discriminant);

double naiveSmall = (-b - root) / (2 * a);
double q = -0.5 * (b + Math.copySign(root, b));
double stableLarge = q / a;
double stableSmall = c / q;

System.out.println(naiveSmall);  // 7.450580596923828E-9
System.out.println(stableSmall); // 1.0E-8
System.out.println(stableLarge); // 1.0E8
```

Hiệu trừ để tính nghiệm nhỏ bằng công thức trực tiếp có sai số đáng kể; dùng `c/q` cho kết quả gần hơn. Đây là ví dụ về **đổi cấu trúc phép toán**, thay vì chỉ tăng kiểu từ `float` sang `double`.

Đây **chưa phải bộ giải phương trình bậc hai hoàn chỉnh**. Trong chương trình thực tế phải xử lý `a == 0`, `D < 0`, `q == 0`, nghiệm kép, số vô cực/`NaN`, overflow/underflow khi tính biệt thức, và miền giá trị rất lớn hoặc rất nhỏ. Không dùng đoạn code minh họa này như bộ giải đầu vào tùy ý.

## 8. Demo Java 17 đầy đủ, chạy được

Lưu chương trình dưới tên `CancellationDemo.java`:

```java
import java.math.BigDecimal;

public class CancellationDemo {
    public static void main(String[] args) {
        double base = 1e16;
        double plusOne = base + 1.0;

        System.out.println("ulp = " + Math.ulp(base));
        System.out.println("plusOne equals base = " + (plusOne == base));
        System.out.println("difference = " + (plusOne - base));

        double x = 1e16;
        double naiveRoot = Math.sqrt(x + 1.0) - Math.sqrt(x);
        double stableRoot = 1.0 / (Math.sqrt(x + 1.0) + Math.sqrt(x));
        System.out.println("sqrt naive = " + naiveRoot);
        System.out.println("sqrt stable = " + stableRoot);

        double tiny = 1e-16;
        System.out.println("exp naive = " + (Math.exp(tiny) - 1.0));
        System.out.println("expm1 = " + Math.expm1(tiny));
        System.out.println("log naive = " + Math.log(1.0 + tiny));
        System.out.println("log1p = " + Math.log1p(tiny));

        BigDecimal preciseA = new BigDecimal("10000000000000001");
        BigDecimal preciseB = new BigDecimal("10000000000000000");
        System.out.println("decimal difference = "
                + preciseA.subtract(preciseB));

        double a = 1.0;
        double b = -1e8;
        double c = 1.0;
        double disc = b * b - 4 * a * c;
        double squareRoot = Math.sqrt(disc);
        double naiveSmall = (-b - squareRoot) / (2 * a);
        double q = -0.5 * (b + Math.copySign(squareRoot, b));
        double stableSmall = c / q;
        System.out.println("quadratic naive small = " + naiveSmall);
        System.out.println("quadratic stable small = " + stableSmall);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac CancellationDemo.java
java CancellationDemo
```

Kết quả:

```text
ulp = 2.0
plusOne equals base = true
difference = 0.0
sqrt naive = 0.0
sqrt stable = 5.0E-9
exp naive = 0.0
expm1 = 1.0E-16
log naive = 0.0
log1p = 1.0E-16
decimal difference = 1
quadratic naive small = 7.450580596923828E-9
quadratic stable small = 1.0E-8
```

Ví dụ dùng `BigDecimal` từ hai chuỗi thập phân **chưa bị làm tròn**. Vì vậy phép trừ `10000000000000001 - 10000000000000000` giữ đúng hiệu `1`. Trái lại, tạo `BigDecimal.valueOf(plusOne)` và `BigDecimal.valueOf(base)` ở đây chỉ nhìn thấy cùng giá trị `double` là `1e16`; lớp `BigDecimal` không thể phục hồi số `+1` đã mất.

Đầu ra của các phép dấu phẩy động trong ví dụ phản ánh hành vi Java với những giá trị đã chọn; khi sửa tham số, kết quả và mức sai số có thể thay đổi.

## 9. Các cách giảm rủi ro triệt tiêu chữ số

### Chuyển đổi công thức trước khi tính

Khi hiệu của hai giá trị gần nhau là mục tiêu, thử tìm một biểu thức toán học tương đương ít phép trừ hơn. Các ví dụ điển hình:

- `sqrt(x+1) - sqrt(x)` → `1 / (sqrt(x+1) + sqrt(x))` với `x >= 0`.
- `exp(x) - 1` → `Math.expm1(x)` khi `x` nhỏ.
- `log(1+x)` → `Math.log1p(x)` khi `x` nhỏ.
- Nghiệm nhỏ của phương trình bậc hai → phương pháp dùng `q` và hệ thức tích hai nghiệm, với các kiểm tra miền hợp lệ.

Mục tiêu là tránh tạo ra rồi trừ hai giá trị đã bị làm tròn thành gần như bằng nhau.

### Giữ biểu diễn chính xác từ nguồn khi nghiệp vụ cần

Nếu đầu vào là decimal chính xác, hãy giữ dạng chuỗi hoặc `BigDecimal` và thực hiện phép tính trong hệ thập phân:

```java
BigDecimal a = new BigDecimal("10000000000000001");
BigDecimal b = new BigDecimal("10000000000000000");
BigDecimal diff = a.subtract(b); // 1
```

Không viết `new BigDecimal(0.1)` với kỳ vọng nó khôi phục số thập phân `0.1` chính xác; constructor nhận `double` chuyển **giá trị nhị phân đã có**. Dùng `new BigDecimal("0.1")` khi dữ liệu nguồn là số thập phân đúng. `BigDecimal.valueOf(double)` thường hữu ích khi muốn biểu diễn thập phân ngắn theo `Double.toString`, nhưng vẫn không thể phục hồi thông tin đã mất do phép tính trước đó.

`BigDecimal` không phải lựa chọn mặc định cho mọi tính toán khoa học: cần cân nhắc hiệu năng, quy tắc chia/làm tròn (`MathContext` khi cần) và tính đúng đắn của mô hình số.

### Chọn độ chính xác phù hợp

`double` thường giảm rủi ro so với `float` vì có 53 bit phần trị so với 24 bit. Tuy nhiên, `double` **không** tự chữa được các biểu thức nhạy cảm. Nếu kết quả cần phân biệt các số nguyên lớn liên tiếp, dùng `long` hoặc `BigInteger` thay vì lưu chúng trong `double`; nếu dữ liệu decimal cần chính xác, cân nhắc `BigDecimal` từ nguồn chính xác.

### Đánh giá sai số bằng dữ liệu kiểm thử có chủ đích

Nên kiểm tra các đầu vào:

- Hai toán hạng bằng nhau hoặc chỉ khác nhau khoảng một ULP.
- Hai toán hạng rất lớn nhưng hiệu mong đợi rất nhỏ.
- Giá trị ở gần 0, giá trị âm, `+0.0`/`-0.0`, và biên có thể gây overflow/underflow.
- Trường hợp có nghiệm gần bằng 0 trong công thức bậc hai hoặc hiệu của các căn.
- Một bộ giá trị tham chiếu có độ chính xác cao hơn (chẳng hạn `BigDecimal` từ chuỗi, tính số học ký hiệu hoặc phần mềm chuyên dụng), khi việc so sánh là có ý nghĩa.

So sánh với mốc tham chiếu có thể dùng sai số tuyệt đối `|computed - expected|` và sai số tương đối `|computed - expected|/|expected|` khi `expected != 0`. Nếu giá trị đúng gần hoặc bằng 0, chỉ dùng sai số tương đối có thể gây hiểu nhầm; nên kết hợp ngưỡng tuyệt đối dựa trên đơn vị nghiệp vụ.

### Đừng dùng biện pháp không giải quyết gốc vấn đề

- **`Math.abs(a - b) < epsilon` chỉ kiểm tra gần bằng nhau**, không phục hồi một hiệu đã mất độ chính xác. Một ngưỡng `epsilon` cố định cũng có thể vô nghĩa ở các thang số rất khác nhau.
- **Định dạng thêm chữ số khi in** (`printf`, `DecimalFormat`) không tạo lại chữ số đã mất.
- **Ép kiểu rồi ép ngược** không khôi phục thông tin bị bỏ khi chuyển `long` sang `double` hay khi cộng `1` vào một `double` quá lớn.
- **Kahan summation** có thể giảm sai số khi cộng dồn nhiều số, nhưng không sửa được hai toán hạng vốn đã trùng nhau trước phép trừ. Đây là bài toán khác.
- **Dùng `BigDecimal` sau khi đã tính sai bằng `double`** chỉ chuyển giá trị sai sang một biểu diễn khác, không tự tái tạo dữ liệu gốc.

## 10. Bài tập và đáp án

**Bài 1.** Giải thích tại sao chương trình sau in `true`:

```java
double n = 1e16;
System.out.println(n == n + 1.0);
```

**Đáp án:** `Math.ulp(1e16) == 2.0`. Khoảng cách giữa hai số `double` đại diện được là `2` ở vùng này; `n + 1` nằm đúng giữa hai giá trị, và quy tắc hòa về chẵn đưa kết quả về `n`.

**Bài 2.** Hai cách tính `sqrt(1e16+1) - sqrt(1e16)` trực tiếp và qua liên hợp cho kết quả nào?

**Đáp án:** Trực tiếp: `0.0`. Qua công thức `1 / (sqrt(1e16+1) + sqrt(1e16))`: `5.0E-9` trong Java với các giá trị đã chọn. Trong số học thực, giá trị đúng gần `5 × 10^-9`.

**Bài 3.** `Math.exp(1e-16) - 1` có thể thành `0.0`. Cần dùng hàm nào nếu mục tiêu là `e^x - 1` với `x` rất nhỏ?

**Đáp án:** `Math.expm1(x)`. Tương tự, với `ln(1+x)` và `x` nhỏ, dùng `Math.log1p(x)`.

**Bài 4.** “Trừ hai `double` rất gần nhau luôn làm mất chữ số trong chính phép trừ.” Đúng hay sai?

**Đáp án:** Sai. Bổ đề Sterbenz cho thấy nhiều phép trừ của hai giá trị floating đã đại diện được và đủ gần nhau được thực hiện chính xác. Sai số nghiêm trọng thường do **hai toán hạng đã gần đúng** hoặc do một công thức khiến sai số tương đối của hiệu tăng mạnh.

**Bài 5.** Vì sao `BigDecimal.valueOf(base + 1.0).subtract(BigDecimal.valueOf(base))` không trả `1` với `base = 1e16`?

**Đáp án:** Phép cộng `base + 1.0` đã được làm tròn thành `base` ở kiểu `double` trước khi gọi `BigDecimal.valueOf`; phép trừ chính xác của hai giá trị bằng nhau cho `0`.

**Bài 6.** Một người muốn đo chênh lệch của hai phép đo `1000000.001` và `1000000.000`. Nên chú ý điều gì?

**Đáp án:** Mức chính xác của từng phép đo và cách lưu đầu vào quyết định hiệu `0.001` đáng tin đến đâu. Cần biết đơn vị, độ phân giải đo, sai số thiết bị và yêu cầu nghiệp vụ; phép trừ trong `double` không thể làm hiệu chính xác hơn thông tin nguồn. Nếu đầu vào là decimal cần giữ nguyên các chữ số đã công bố, có thể dùng `BigDecimal` từ chuỗi.

**Ghi nhớ:** Triệt tiêu chữ số khiến hiệu của hai số gần nhau đặc biệt nhạy với sai số của từng toán hạng. Hãy kiểm tra nguồn sai số, quan sát ULP, ưu tiên công thức ổn định (`expm1`, `log1p`, liên hợp, phương pháp tính nghiệm thích hợp), và giữ dữ liệu ở dạng chính xác từ trước khi thông tin bị làm tròn mất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-010">Math.fma và sự khác biệt với nhân rồi cộng</a>

<details>
<summary>Xem chi tiết</summary>

# Math.fma và sự khác biệt với nhân rồi cộng trong Java

**`Math.fma(a, b, c)`** tính phép **fused multiply-add** (FMA): lấy tích `a × b`, cộng `c`, sau đó **làm tròn kết quả cuối cùng một lần** sang `float` hoặc `double`. Trái lại, biểu thức Java thông thường **`a * b + c`** thực hiện **phép nhân có làm tròn trước**, rồi mới cộng và **làm tròn thêm lần nữa**.

Đây không chỉ là khác biệt cú pháp hay hiệu năng. Khi các chữ số thấp của tích bị mất và phép cộng tiếp theo hủy bỏ gần hết phần lớn của tích, hai cách tính có thể trả **hai kết quả số học khác nhau**. FMA đôi khi còn tránh việc **phép nhân trung gian overflow hoặc underflow** dù kết quả toán học của cả biểu thức vẫn biểu diễn được.

```java
double a = 1.0 + 0x1.0p-27;
double b = 1.0 - 0x1.0p-27;

double separate = a * b - 1.0;      // +0.0
double fused = Math.fma(a, b, -1.0); // -2^-54, khác 0
```

Các khai báo trên là **mảnh mã** có thể đặt trong phương thức Java. Bài học này giải thích nguyên nhân của khác biệt, cách dùng API Java 17+, trường hợp biên và giới hạn của việc dùng FMA để cải thiện độ chính xác.

## 1. Bối cảnh: phép tính dấu phẩy động luôn có bước làm tròn

Java cung cấp hai kiểu dấu phẩy động nhị phân thường dùng:

| Kiểu | Tổng bit | Độ chính xác phần trị (significand) | Số mũ | Đặc điểm |
| --- | ---: | ---: | --- | --- |
| `float` | 32 | 24 bit hiệu dụng | Phạm vi hẹp hơn | Nhanh/gọn hơn về lưu trữ; lưới số thưa hơn |
| `double` | 64 | 53 bit hiệu dụng | Phạm vi rộng hơn | Thường là lựa chọn mặc định cho tính toán dấu phẩy động |

Với một số thực toán học, không phải mọi chữ số đều có thể lưu trong `float` hay `double`. Phép toán cần chọn **số biểu diễn được gần nhất**, thông thường theo quy tắc **gần nhất; khi đúng giữa hai số thì chọn phần trị chẵn** (*round to nearest, ties to even*).

Một cách ký hiệu kết quả làm tròn về đúng kiểu là `round(x)`. Với các toán hạng hữu hạn và không gặp ngoại lệ đặc biệt:

```text
Nhân rồi cộng:
intermediate = round(a × b)
result       = round(intermediate + c)

Fused multiply-add:
result       = round((a × b) + c)
               ↑ tích và tổng được xét ở độ chính xác toán học
                 trước khi làm tròn một lần
```

FMA không có nghĩa Java cất một `double` với vô hạn bit; nó có nghĩa **phép nhân trung gian không bị làm tròn độc lập trước phép cộng**. Kết quả trả về vẫn là một `float` hoặc `double` hữu hạn độ chính xác, hoặc một giá trị đặc biệt theo quy tắc IEEE 754.

Đặc biệt, các toán hạng truyền vào hàm có thể **đã bị làm tròn từ trước** khi được tạo ra. FMA chỉ giúp kết hợp phép `a*b+c` đối với **các giá trị đầu vào hiện có**, không khôi phục thông tin đã mất trong bước tính `a`, `b` hay `c` trước đó.

## 2. API `Math.fma` và kiểu kết quả

Java có hai overload chính:

```java
static double Math.fma(double a, double b, double c)
static float  Math.fma(float a, float b, float c)
```

Đây là **chữ ký phương thức**, không phải một chương trình độc lập. Với mọi đối số `double`, phương thức trả `double`; với toàn bộ đối số `float`, có overload trả `float`.

```java
double d = Math.fma(2.0, 3.0, 4.0);  // 10.0
float f = Math.fma(2.0f, 3.0f, 4.0f); // 10.0f
```

Khi trộn `float` với `double`, Java có thể chọn overload `double` vì một số đối số cần được nâng kiểu. Do vậy nên **chọn kiểu toán hạng có chủ đích**. Ví dụ `Math.fma(1.0f, 2.0f, 3.0)` trả `double`, còn `Math.fma(1.0f, 2.0f, 3.0f)` trả `float`.

Trong Java 17 trở lên, ngữ nghĩa dấu phẩy động được quy định nhất quán; một biểu thức Java `a * b + c` thông thường **không được tự coi như FMA nếu việc thay thế làm thay đổi kết quả bắt buộc**. Hãy dùng `Math.fma` tường minh khi bạn muốn phép toán fused một lần làm tròn.

## 3. Ví dụ chính: sai số phép nhân bị lộ ra sau khi trừ 1

Đặt:

```text
a = 1 + 2^-27
b = 1 - 2^-27
c = -1
```

Các số `a` và `b` biểu diễn **chính xác** bằng `double`. Tích toán học là:

```text
a × b = (1 + 2^-27)(1 - 2^-27)
      = 1 - 2^-54

a × b + c = (1 - 2^-54) - 1
          = -2^-54
```

**Cách tính thông thường:**

```java
double a = 1.0 + 0x1.0p-27;
double b = 1.0 - 0x1.0p-27;
double separate = a * b - 1.0;
```

Quanh `1.0`, số `double` biểu diễn được ngay phía dưới là `1 - 2^-53`. Giá trị `1 - 2^-54` nằm **gần `1.0` hơn** giá trị phía dưới đó, nên phép nhân được làm tròn thành chính xác `1.0`. Sau khi trừ `1.0`, `separate` là **`+0.0`**: phần dư nhỏ nhưng có thật đã bị mất trong bước nhân.

**Cách tính fused:**

```java
double fused = Math.fma(a, b, -1.0);
```

FMA tính tích và phép trừ dưới dạng một phép toán kết hợp trước khi làm tròn:

```text
round((1 - 2^-54) - 1) = round(-2^-54) = -2^-54
```

Vì `-2^-54` cũng là một số `double` biểu diễn được, FMA giữ chính xác kết quả ấy. Khi in bằng dạng hex floating-point:

```text
separate = 0x0.0p0
fused    = -0x1.0p-54
```

**Điểm mấu chốt:** chênh lệch không đến từ việc gọi hàm thay vì toán tử; nó đến từ **số lần và thời điểm làm tròn**. Cả hai phép tính nhận **cùng ba giá trị đầu vào** nhưng cách tính trung gian khác nhau.

## 4. Vì sao ví dụ này liên quan đến ULP?

**ULP (Unit in the Last Place)** biểu diễn kích cỡ một bước của lưới giá trị dấu phẩy động quanh một độ lớn. Với `double`, `Math.ulp(1.0)` là `2^-52`, nhưng **khoảng cách từ `1.0` xuống số kề dưới** là `2^-53`, vì `1.0` là ranh giới lũy thừa 2.

Trong ví dụ:

- Tích thực `1 - 2^-54` cách `1.0` đúng `2^-54`.
- Bước đến số `double` ngay dưới `1.0` là `2^-53`.
- Khoảng sai lệch `2^-54` là **nửa khoảng ở phía dưới**, nên ở điểm chính giữa, quy tắc *ties to even* chọn `1.0` thay vì số kề dưới (bit phần trị cuối của `1.0` là chẵn).
- Nhân rồi cộng làm mất phần chênh ấy; `Math.fma` giữ phần chênh đến lúc cuối mới làm tròn.

Vì vậy, các biểu thức kiểu `a*b + c` có **cancellation** (tích gần bằng `-c`) là nơi sự khác nhau về độ chính xác thể hiện rõ. Không có bảo đảm FMA làm sai số tuyệt đối của **toàn bộ thuật toán** nhỏ nhất; nó chỉ tính đúng phép fused với một lần làm tròn cuối cùng.

## 5. Cùng hiện tượng ở `float`

`float` có 24 bit significand hiệu dụng, ít hơn `double`. Chọn:

```java
float a = 1.0f + 0x1.0p-13f;
float b = 1.0f - 0x1.0p-13f;

float separate = a * b - 1.0f;      // +0.0f
float fused = Math.fma(a, b, -1.0f); // -2^-26f
```

Tích chính xác là `1 - 2^-26`; phép nhân kiểu `float` thông thường làm tròn thành `1.0f`, sau đó trừ `1.0f` cho 0. FMA trả `-2^-26f`, và hai giá trị được in như sau:

```text
floatSeparate=0x0.0p0
floatFused=-0x1.0p-26
```

Ví dụ này cho thấy cả **overload float** cũng có ý nghĩa fused, không chỉ `double`. Hãy thêm hậu tố `f` cho literal khi muốn duy trì phép tính và kiểu đầu ra `float`. Nếu vô tình dùng literal `double` như `1.0` hoặc `-1.0` trong biểu thức, kết quả có thể được tính ở kiểu khác do quy tắc nâng kiểu và overload.

## 6. Khi phép nhân riêng lẻ overflow nhưng FMA vẫn trả số hữu hạn

Có các phép `a*b+c` mà tích riêng lẻ vượt `Double.MAX_VALUE`, nhưng phép cộng với `c` **triệt tiêu phần vượt giới hạn** trước khi kết quả cuối được làm tròn.

```java
double max = Double.MAX_VALUE;
double separate = max * 2.0 - max;       // +Infinity
double fused = Math.fma(max, 2.0, -max); // Double.MAX_VALUE
```

Giải thích:

- Với phép nhân độc lập, `max * 2.0` vượt dải hữu hạn của `double`, nên được làm tròn thành `+Infinity`.
- Sau đó `+Infinity - max` vẫn là `+Infinity`; thông tin về tích hữu hạn thực đã mất.
- FMA xét biểu thức toán học `max × 2 - max = max` **trước bước làm tròn cuối**. Kết quả `max` vẫn là `double` hữu hạn, nên trả đúng `Double.MAX_VALUE`.

Đây là trường hợp mà khác biệt không chỉ vài bit cuối mà là **Infinity so với một giá trị hữu hạn rất lớn**. Cần phân biệt đầu vào `max` hữu hạn với đầu vào **đã là Infinity**: nếu truyền `Double.POSITIVE_INFINITY` từ trước, FMA không “khôi phục” nó thành `max`.

## 7. Khi tích riêng lẻ underflow và làm mất phần đóng góp nhỏ

Ngược lại, xét số dương subnormal nhỏ nhất:

```java
double tiny = Double.MIN_VALUE; // 2^-1074
double separate = tiny * 0.5 + tiny;
double fused = Math.fma(tiny, 0.5, tiny);
```

- Tích toán học `tiny × 0.5 = 2^-1075` nhỏ hơn bước subnormal nhỏ nhất. Làm tròn phép nhân riêng lẻ thành **`0.0`** (điểm hòa được giải theo *ties to even*).
- Sau đó `0.0 + tiny = tiny`, nên kết quả `separate` bằng **1 đơn vị** `Double.MIN_VALUE`.
- FMA giữ tích nhỏ cho tới khi cộng: `2^-1075 + 2^-1074 = 1.5 × 2^-1074`. Khi làm tròn cuối, kết quả thành **2 đơn vị** `Double.MIN_VALUE` (điểm hòa chọn phần trị chẵn).

Do đó:

```text
separate / Double.MIN_VALUE = 1.0
fused / Double.MIN_VALUE    = 2.0
```

Ví dụ chứng minh việc làm tròn một lần có thể quan trọng ngay tại miền **subnormal/underflow**, không chỉ ở giá trị gần 1 hay khi phép nhân bị overflow.

## 8. Chương trình Java 17+ hoàn chỉnh và kết quả đã kiểm thử

Tạo tệp **`FmaDemo.java`**:

```java
public class FmaDemo {
    public static void main(String[] args) {
        double a = 1.0 + 0x1.0p-27;
        double b = 1.0 - 0x1.0p-27;
        double separate = a * b - 1.0;
        double fused = Math.fma(a, b, -1.0);

        System.out.println("a=" + Double.toHexString(a));
        System.out.println("b=" + Double.toHexString(b));
        System.out.println("separate=" + Double.toHexString(separate));
        System.out.println("fused=" + Double.toHexString(fused));
        System.out.println("fusedExact=" + (fused == -0x1.0p-54));
        System.out.println("plainExample=" + Math.fma(2.0, 3.0, 4.0));

        float fa = 1.0f + 0x1.0p-13f;
        float fb = 1.0f - 0x1.0p-13f;
        System.out.println("floatSeparate=" + Float.toHexString(fa * fb - 1.0f));
        System.out.println("floatFused=" + Float.toHexString(Math.fma(fa, fb, -1.0f)));

        double max = Double.MAX_VALUE;
        System.out.println("separateOverflow=" + (max * 2.0 - max));
        System.out.println("fusedAvoidsOverflow=" + (Math.fma(max, 2.0, -max) == max));

        double tiny = Double.MIN_VALUE;
        double separateUnderflow = tiny * 0.5 + tiny;
        double fusedUnderflow = Math.fma(tiny, 0.5, tiny);
        System.out.println("separateUnderflowUnits=" + (separateUnderflow / tiny));
        System.out.println("fusedUnderflowUnits=" + (fusedUnderflow / tiny));

        System.out.println("nanProduct=" + Double.isNaN(Math.fma(Double.POSITIVE_INFINITY, 0.0, 1.0)));
        System.out.println("nanOppositeInfinities=" + Double.isNaN(
                Math.fma(Double.POSITIVE_INFINITY, 2.0, Double.NEGATIVE_INFINITY)));
        System.out.println("nanOperand=" + Double.isNaN(Math.fma(Double.NaN, 1.0, 2.0)));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac --release 17 -encoding UTF-8 FmaDemo.java
java FmaDemo
```

Stdout chính xác:

```text
a=0x1.0000002p0
b=0x1.ffffffcp-1
separate=0x0.0p0
fused=-0x1.0p-54
fusedExact=true
plainExample=10.0
floatSeparate=0x0.0p0
floatFused=-0x1.0p-26
separateOverflow=Infinity
fusedAvoidsOverflow=true
separateUnderflowUnits=1.0
fusedUnderflowUnits=2.0
nanProduct=true
nanOppositeInfinities=true
nanOperand=true
```

Nhìn vào từng nhóm dòng:

1. `separate=0x0.0p0` và `fused=-0x1.0p-54`: hai phép tính nhận cùng đầu vào nhưng giữ lượng thông tin khác nhau. `fusedExact=true` xác minh phần dư `-2^-54`.
2. `plainExample=10.0`: nếu phép nhân/cộng đều biểu diễn chính xác thì không nhất thiết có khác biệt; `2×3+4` là phép tính đơn giản cho cùng kết quả.
3. `floatSeparate` so với `floatFused`: kiểm chứng overload `float`.
4. `separateOverflow=Infinity`, `fusedAvoidsOverflow=true`: FMA tránh overflow trung gian trong ví dụ cụ thể.
5. `separateUnderflowUnits=1.0`, `fusedUnderflowUnits=2.0`: FMA giữ phần đóng góp rất nhỏ đến bước làm tròn cuối.
6. Ba kết quả `nan...=true`: kiểm tra các quy tắc đặc biệt với `NaN` và vô cực.

Sử dụng `Double.toHexString` và `Float.toHexString` giúp thể hiện chính xác dạng mũ nhị phân của kết quả, tránh việc chỉ nhìn chuỗi thập phân ngắn rồi nhầm `0.0` với một số khác rất nhỏ.

## 9. `NaN`, vô cực, dấu zero và quy tắc trường hợp đặc biệt

Giống các phép toán dấu phẩy động khác, `Math.fma` không thể bỏ qua các giá trị IEEE 754 đặc biệt:

| Tình huống | Kết quả |
| --- | --- |
| Một toán hạng là `NaN` | Kết quả `NaN` |
| `a` hoặc `b` là vô cực, toán hạng kia là zero | `NaN`: tích `0 × ∞` không hợp lệ |
| Tích vô cực và `c` vô cực có dấu ngược | `NaN`: hai lượng vô cực đối nhau |
| Cả ba toán hạng hữu hạn nhưng kết quả toán học vượt kiểu đích | Có thể trả `+Infinity` hoặc `-Infinity` sau làm tròn cuối |
| Kết quả quá nhỏ để thành số subnormal khác 0 | Có thể làm tròn về `+0.0` hoặc `-0.0` |
| Tích trung gian lớn nhưng tổng cuối vẫn hữu hạn | FMA có thể trả số hữu hạn, không bắt buộc overflow như phép nhân riêng |

Ví dụ:

```java
boolean invalid1 = Double.isNaN(
        Math.fma(Double.POSITIVE_INFINITY, 0.0, 1.0)); // true
boolean invalid2 = Double.isNaN(
        Math.fma(Double.POSITIVE_INFINITY, 2.0, Double.NEGATIVE_INFINITY)); // true
```

Các dòng trên là mã minh họa, có thể đặt vào phương thức. Đây là tính chất của toán học dấu phẩy động IEEE 754, **không phải `ArithmeticException`**. Để kiểm tra kết quả, dùng `Double.isFinite`, `Double.isNaN`, `Double.isInfinite` (hoặc API tương ứng của `Float`).

IEEE 754 có cả `+0.0` và `-0.0`. Dấu của một kết quả zero có thể có ý nghĩa trong tính toán (ví dụ phép chia cho zero), mặc dù `+0.0 == -0.0` là `true`. Khi làm việc với zero và phép toán fused, không chỉ so sánh `== 0.0` nếu yêu cầu phân biệt mẫu bit dấu; có thể xem `Double.doubleToRawLongBits`.

## 10. FMA giúp được gì và không giúp được gì?

**Ưu điểm trực tiếp:** với cùng đầu vào đã được biểu diễn trong kiểu đích, FMA loại bỏ phép **làm tròn trung gian giữa nhân và cộng**, nên trả kết quả của `round(a*b+c)` với một bước làm tròn cuối theo quy tắc của API. Điều này đặc biệt có ích trong:

- **Tích lũy tích vô hướng:** `sum = Math.fma(x[i], y[i], sum)` kết hợp nhân và cộng ở mỗi bước, giảm một điểm làm tròn so với `sum += x[i] * y[i]`.
- **Đánh giá đa thức:** bước Horner `value = Math.fma(value, x, coefficient)` khi thích hợp, giảm làm tròn trung gian.
- **Một số bộ lọc số, tính toán hình học và thuật toán numerical linear algebra**, khi độ chính xác gần máy quan trọng và phân tích số cho thấy việc dùng FMA có lợi.

**Giới hạn bắt buộc nhớ:**

1. FMA **không làm mọi số thực trở nên biểu diễn chính xác**. Ví dụ `0.1` vẫn không được lưu hoàn toàn chính xác bằng `double`.
2. Nếu `a`, `b`, `c` đã bị làm tròn hoặc sai từ trước, FMA không thể khôi phục dữ liệu ban đầu.
3. Chuỗi nhiều phép FMA vẫn **làm tròn sau mỗi lời gọi FMA**; không phải toàn bộ vòng lặp được thực hiện bằng độ chính xác vô hạn.
4. Kết quả FMA có thể khác kết quả biểu thức cũ theo một hoặc nhiều bit. Đây là khác biệt tính toán có chủ đích; khi đổi mã sản xuất, cần xác nhận việc thay đổi đáp ứng **hợp đồng kết quả và các bài kiểm thử**, đặc biệt nơi tính tái lập bit là yêu cầu.
5. FMA **không có nghĩa trả số chính xác tuyệt đối cho mọi bài toán** hoặc loại bỏ các vấn đề mất ổn định số, điều kiện bài toán kém hay cancellation ở các bước khác.

Về hiệu năng, một số CPU có chỉ thị FMA và JVM có thể tận dụng; nhưng **không nên coi `Math.fma` luôn nhanh hơn** phép nhân/cộng trong mọi môi trường. JVM, kiến trúc, khả năng tối ưu và tải công việc đều ảnh hưởng. Cân nhắc benchmark như JMH khi hiệu năng là mục tiêu chính, và ưu tiên lựa chọn FMA theo **ngữ nghĩa số học** trước.

## 11. Ví dụ ứng dụng: dot product và Horner

### Tích vô hướng

Một thuật toán thường tính:

```java
// Giả sử x và y là hai mảng double có cùng chiều dài.
double sum = 0.0;
for (int i = 0; i < x.length; i++) {
    sum = Math.fma(x[i], y[i], sum);
}
```

Đoạn này là **mảnh code** cần đặt trong phương thức có sẵn mảng `x`, `y`. Mỗi bước tính tích một phần tử `x[i] * y[i]` cộng vào `sum`, rồi làm tròn một lần. So với `sum += x[i] * y[i]`, nó loại bỏ một bước làm tròn của tích tại từng vòng.

Tuy nhiên, thứ tự các số hạng vẫn có thể ảnh hưởng; khi các giá trị khác bậc độ lớn hoặc có hủy triệt tiêu mạnh, một phương pháp cộng bù (Kahan/Neumaier), chia nhóm hoặc thuật toán số khác có thể cần thiết. Dùng FMA **không tự bảo đảm** kết quả chính xác hoặc độc lập thứ tự phép cộng.

### Đánh giá đa thức theo Horner

Với `p(x) = a*x*x + b*x + c`, cách Horner là `(a*x + b)*x + c`. Nếu thuật toán muốn dùng FMA tại mỗi bước:

```java
double result = Math.fma(Math.fma(a, x, b), x, c);
```

Đây là **mảnh mã** khi `a`, `b`, `c`, `x` là các biến `double` đã có. Mỗi lời gọi FMA chỉ làm tròn một lần. Kết quả có thể khác `a*x*x + b*x + c` vì **cả cấu trúc đánh giá lẫn số lần làm tròn** đã thay đổi; với bài toán cần kết quả tham chiếu, hãy chọn thứ tự phép tính rõ ràng và kiểm chứng sai số.

## 12. Khi nào không nên thay `a*b+c` thành `Math.fma(a,b,c)`?

Có thể không phù hợp nếu:

- **Yêu cầu tương thích kết quả bit-for-bit** với chương trình từng sử dụng hai phép toán làm tròn riêng. Đổi sang FMA có thể là thay đổi hành vi, dù đôi khi chính xác hơn.
- **Muốn quan sát hoặc kiểm tra tích trung gian** (`product`) như một kết quả có ý nghĩa riêng; FMA chỉ trả kết quả tổng, không lộ tích đã làm tròn.
- **Phép toán thuộc hệ số nguyên, decimal exact hoặc tiền tệ**. `Math.fma` là API cho `float` và `double`, không phải `BigDecimal`, không loại bỏ sai số nhị phân của số thập phân; với tài chính có thể cần `BigDecimal` và chính sách `MathContext`/rounding rõ ràng.
- **Biên `NaN`, Infinity, zero** có ngữ nghĩa nghiệp vụ cần xử lý tường minh. FMA có quy tắc đặc biệt và không tự “sửa” dữ liệu không hợp lệ.
- **Hiệu năng là lý do duy nhất** nhưng chưa có benchmark trên nền tảng mục tiêu. Tập trung lựa chọn dựa trên độ chính xác và phép đo thực tế.

Ngược lại, nếu yêu cầu phép toán fused đúng đặc tả và muốn giảm làm tròn trung gian, `Math.fma` là API thể hiện ý định rõ nhất trong Java.

## 13. Những lỗi và nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật | Cách xử lý |
| --- | --- | --- |
| `Math.fma(a,b,c)` luôn bằng `a*b+c` | Hai bước làm tròn so với một bước có thể khác nhau | Kiểm thử ví dụ cancellation và các biên |
| FMA “làm tròn tích chính xác hơn” rồi mới cộng | FMA **không làm tròn tích riêng** | Hiểu công thức `round((a×b)+c)` |
| FMA luôn giảm sai số của cả chương trình | Chỉ bảo đảm ngữ nghĩa của **một phép fused** | Phân tích sai số cả thuật toán |
| Dùng `double` thì không còn cancellation | `double` vẫn có số bit fraction hữu hạn | Xem ví dụ `-2^-54` |
| Tràn khi `a*b` nghĩa FMA cũng phải trả Infinity | Nếu `a*b+c` cuối cùng hữu hạn, FMA có thể trả hữu hạn | `Math.fma(MAX,2,-MAX)` |
| Underflow ở tích nhỏ không quan trọng | Sau khi cộng, phần đóng góp có thể thay đổi việc làm tròn | Thử `Double.MIN_VALUE` |
| `Math.fma` dùng được cho `BigDecimal` | API chỉ có overload dấu phẩy động | Dùng phương thức `BigDecimal.multiply/add` theo yêu cầu |
| `Math.fma` biến literal `0.1` thành chính xác | Literal đã làm tròn khi trở thành `double` | Chọn biểu diễn decimal chính xác nếu cần |
| Thay toàn bộ `a*b+c` là tối ưu hiển nhiên | Có thể đổi bit kết quả; hiệu năng không bất biến | Kiểm thử tính đúng và benchmark |
| Một kết quả `NaN` nghĩa Java ném lỗi | IEEE 754 có thể trả `NaN` mà không có exception | Dùng `Double.isNaN`/isFinite |

## 14. Tự kiểm tra

1. **`Math.fma(a,b,c)` khác `a*b+c` ở đâu?** FMA cộng tích chính xác với `c` rồi làm tròn **một lần**; biểu thức thường làm tròn sau nhân và sau cộng.
2. **Với `a=1+2^-27`, `b=1-2^-27`, `c=-1`, kết quả fused là gì?** `-2^-54`. Nhân rồi cộng cho `0.0` vì mất phần chênh ở bước nhân.
3. **FMA có thể giúp khi phép nhân riêng overflow không?** Có trong một số trường hợp, ví dụ `Math.fma(Double.MAX_VALUE,2.0,-Double.MAX_VALUE)` trả `Double.MAX_VALUE` hữu hạn.
4. **FMA có thể tạo kết quả khác ở miền subnormal không?** Có, vì tích rất nhỏ có thể chỉ đóng góp đủ để đổi lựa chọn làm tròn sau khi cộng.
5. **Khi nào `Math.fma` vẫn trả `NaN`?** Ví dụ khi một đối số là `NaN`, khi nhân vô cực với 0, hoặc khi tích vô cực bị cộng với vô cực trái dấu.
6. **Nếu `a` đã bị làm tròn từ một số thực nhập vào thì FMA có lấy lại giá trị thực gốc không?** Không. Nó chỉ xử lý chính xác phép fused trên những toán hạng hiện có.
7. **Đối số đều là `float`, kiểu trả về là gì?** `float` khi overload `Math.fma(float,float,float)` được chọn. Có `double` trong lời gọi thì overload `double` có thể được chọn.
8. **FMA có nên thay mọi phép tính tiền bằng `double` không?** Không. Yêu cầu thập phân chính xác và chính sách làm tròn cần cách biểu diễn phù hợp, thường là `BigDecimal`.
9. **FMA có làm toàn bộ vòng lặp dot product tính bằng vô hạn độ chính xác không?** Không. Mỗi lời gọi FMA vẫn làm tròn kết quả một lần, vòng lặp vẫn có thể tích lũy sai số.

**Ghi nhớ:** `Math.fma(a,b,c)` biểu diễn **fused multiply-add**, tức **một bước làm tròn cuối sau khi nhân và cộng**. Với dữ liệu nhạy ở phần bit thấp hoặc cancellation, kết quả có thể khác hoàn toàn biểu thức `a*b+c` gồm hai bước làm tròn. Dùng FMA để kiểm soát ngữ nghĩa và đôi khi cải thiện độ chính xác, nhưng luôn kiểm tra đầu vào, trường hợp biên và yêu cầu kết quả của ứng dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="floating-point-arithmetic-011">Always-strict từ Java 17 và vai trò lịch sử của strictfp</a>

<details>
<summary>Xem chi tiết</summary>

# Always-strict từ Java 17 và vai trò lịch sử của `strictfp`

Từ **Java SE 17**, các biểu thức dấu phẩy động `float` và `double` được đánh giá **luôn theo ngữ nghĩa strict** (*always-strict floating-point semantics*). Đây là thay đổi của **JEP 306 – Restore Always-Strict Floating-Point Semantics**. Vì vậy, trên Java 17 trở lên, thêm hay bỏ từ khóa `strictfp` **không làm thay đổi kết quả đánh giá một biểu thức dấu phẩy động**.

Trước Java 17, đặc biệt trong Java 1.2 đến Java 16, `strictfp` từng có tác dụng: nó yêu cầu các phép tính trong phạm vi áp dụng tuân thủ đúng tập giá trị `float`/`double` chuẩn, thay vì cho phép một số JVM sử dụng tập giá trị trung gian có **miền số mũ mở rộng**. Thay đổi từ Java 17 loại bỏ khác biệt này.

Ví dụ quan trọng:

```java
static double ordinary(double x) {
    return x * 2.0 / 2.0;
}

static strictfp double legacyStrict(double x) {
    return x * 2.0 / 2.0;
}
```

Hai phương thức trên có **cùng ngữ nghĩa dấu phẩy động trên Java 17+**. `strictfp` vẫn được chấp nhận trong source code nhằm tương thích, nhưng trình biên dịch hiện đại có thể cảnh báo rằng modifier này không còn cần thiết.

Bài học thuộc phần **Phép toán dấu phẩy động** của module **Java core – numbers**. Trọng tâm là hiểu “strict” nghĩa là gì, vì sao từng có `strictfp`, điều gì thực sự thay đổi trong Java 17, và những điều **always-strict không bảo đảm**.

## 1. “Strict floating-point” thực sự có nghĩa gì?

Java sử dụng biểu diễn số dấu phẩy động nhị phân IEEE 754:

| Kiểu | Định dạng nhị phân | Độ chính xác phần trị | Số mũ và miền giá trị |
| --- | --- | --- | --- |
| `float` | binary32, 32 bit | 24 bit kể cả bit ẩn | Miền tương ứng với binary32 |
| `double` | binary64, 64 bit | 53 bit kể cả bit ẩn | Miền tương ứng với binary64 |

Khi đánh giá **strict**, mỗi biểu thức có kiểu `float` chỉ nhận giá trị biểu diễn được bằng `float` chuẩn; mỗi biểu thức có kiểu `double` chỉ nhận giá trị biểu diễn được bằng `double` chuẩn. Các phép toán tương ứng với IEEE 754 tạo kết quả theo định dạng và quy tắc làm tròn được đặc tả, không được âm thầm duy trì một tập giá trị trung gian có số mũ lớn hơn kiểu của biểu thức.

Ví dụ, với `double`:

```java
double x = Double.MAX_VALUE;
double result = (x * 2.0) / 2.0;
// result là Infinity, không trở về x.
```

Tại bước `x * 2.0`, giá trị toán học vượt miền **hữu hạn** của `double`, nên kết quả là `+Infinity`. Sau đó `Infinity / 2.0` vẫn là `Infinity`.

Đây là ví dụ minh họa sự khác nhau giữa toán học số thực lý tưởng và đánh giá dấu phẩy động với miền số mũ hữu hạn. Không được diễn giải `(x * 2) / 2 == x` là một đẳng thức luôn đúng khi dùng `double`.

**Strict không có nghĩa vô hạn độ chính xác.** Kết quả hữu hạn vẫn có thể bị làm tròn; `0.1` và `0.2` không có biểu diễn nhị phân hữu hạn chính xác; phép toán có thể tràn thành vô cực hoặc underflow thành số subnormal/zero có dấu. Strict chỉ khiến **quy tắc đánh giá được xác định chặt chẽ**, không biến `float`/`double` thành `BigDecimal`.

## 2. Lược sử ba giai đoạn của dấu phẩy động Java

| Giai đoạn | Hành vi mặc định | Vai trò của `strictfp` |
| --- | --- | --- |
| Java 1.0–1.1 | Luôn dùng ngữ nghĩa strict | Chưa có sự phân đôi chế độ như Java 1.2 |
| Java 1.2–16 | Có thể dùng strict hoặc chế độ cho phép tập giá trị trung gian với **số mũ mở rộng** ở những nơi đặc tả cho phép | Yêu cầu đánh giá strict trong phạm vi được khai báo |
| **Java 17 trở lên** | **Mọi biểu thức `float`/`double` đều always-strict** | Giữ để tương thích mã cũ; không còn ảnh hưởng phép tính |

### 2.1. Giai đoạn Java 1.0 và 1.1

Những phiên bản đầu Java đã yêu cầu phép toán dấu phẩy động có ngữ nghĩa strict. Điều này phục vụ mục tiêu chương trình có hành vi được quy định rõ ràng trên các môi trường thực thi khác nhau.

### 2.2. Từ Java 1.2 đến Java 16: lý do xuất hiện strictfp

Một số họ CPU đời cũ có đặc điểm phần cứng khiến việc thực hiện chính xác chế độ strict tốn chi phí đáng kể. Vì thế, từ Java 1.2, đặc tả cho phép một số phép tính `float` hoặc `double` **không FP-strict** dùng tập giá trị **extended-exponent**:

- `float-extended-exponent value set`: **cùng số bit chính xác phần trị** như `float`, nhưng miền số mũ được mở rộng.
- `double-extended-exponent value set`: **cùng số bit chính xác phần trị** như `double`, nhưng miền số mũ được mở rộng.

Điểm dễ nhầm: các tập giá trị lịch sử này được mô tả với **miền số mũ lớn hơn**, **không phải bảo đảm có thêm bit chính xác phần trị**. Không nên tóm tắt sai rằng “không strict thì Java được phép giữ toàn bộ phép tính ở bất kỳ độ chính xác 80-bit nào”.

Với phép tính không strict, khi phần cứng/JVM được phép sử dụng miền số mũ mở rộng, một kết quả trung gian có thể tránh tràn hoặc underflow ở một vị trí mà phép tính strict đã đạt vô cực hoặc zero. Điều đó có thể tạo khác biệt ở kết quả cuối trong các môi trường lịch sử.

`strictfp` được đưa vào để lập trình viên **chủ động yêu cầu strict evaluation** ở class/interface hoặc method thích hợp, khống chế khác biệt cho những biểu thức nằm trong phạm vi áp dụng.

### 2.3. Từ Java 17: JEP 306 khôi phục always-strict

Phần cứng và khả năng triển khai JVM đã tiến bộ, làm cho sự linh hoạt vì phần cứng cũ không còn cần thiết. **JEP 306** thống nhất lại ngữ nghĩa:

- Không còn cho phép phép toán dấu phẩy động Java sử dụng tập giá trị trung gian có miền số mũ mở rộng thay cho `float`/`double` chuẩn.
- Mọi biểu thức dấu phẩy động Java được đánh giá strict, **dù có hoặc không có `strictfp`**.
- Từ khóa `strictfp` được giữ vì tương thích mã nguồn, nhưng không tạo “chế độ nghiêm ngặt hơn” cho phương thức/class có đánh dấu.

Đây là thay đổi ở **đặc tả ngôn ngữ và máy ảo**, không chỉ là lựa chọn trình biên dịch hay một cờ tối ưu của riêng HotSpot.

## 3. Cách dùng strictfp trong mã Java cũ

Trong các phiên bản mà `strictfp` có tác dụng, modifier này thường được đặt trên **class**, **interface** hoặc **method** thích hợp để các biểu thức dấu phẩy động trong phạm vi đó có ngữ nghĩa strict.

Ví dụ cú pháp:

```java
public strictfp class LegacyCalculator {
    public double calculate(double x, double y) {
        return (x * y) / 3.0;
    }
}
```

Hoặc:

```java
public class LegacyCalculator {
    public static strictfp double calculate(double x, double y) {
        return (x * y) / 3.0;
    }
}
```

Hai đoạn trên là **mẫu khai báo độc lập**, không nên đặt cả hai class `public` cùng tên trong một tệp. Trong Java 17+, modifier vẫn hợp lệ về cú pháp nhưng **không thay đổi ngữ nghĩa đánh giá**. Với mã Java 17+ viết mới, thông thường không cần thêm `strictfp` chỉ để có kết quả strict.

Không phải mọi vị trí trong Java đều nhận modifier `strictfp`; chẳng hạn, nó không phải modifier dành cho một biến số riêng lẻ. Trong code review mã cũ, cần đọc phiên bản Java mục tiêu để hiểu nguyên nhân lịch sử của modifier.

## 4. Ví dụ sự khác biệt về số mũ trung gian

Xét phép tính:

```java
double x = Double.MAX_VALUE;
double value = x * 2.0 / 2.0;
```

Trong đánh giá **strict**, kết quả của `x * 2.0` là `Infinity`. Vì vậy kết quả cuối vẫn là `Infinity`, dù trong toán học số thực lý tưởng `(x × 2) ÷ 2 = x`.

Trong mô hình lịch sử cho phép **extended-exponent**, một số trường hợp có thể giữ trung gian có số mũ lớn hơn, khiến phần nhân chưa tràn ngay trong giá trị trung gian. Khi các bước tiếp theo đưa giá trị trở về miền chuẩn, kết quả có thể khác.

Lưu ý ba giới hạn khi dùng ví dụ này:

1. Nó mô tả **lý do có thể khác** giữa các chế độ đánh giá lịch sử, **không khẳng định** rằng mọi JVM trước Java 17 chắc chắn trả một giá trị khác.
2. Các đoạn mã không strict ở Java 1.2–16 vẫn có thể được đánh giá strict trong nhiều môi trường. Việc cho phép miền số mũ mở rộng không bắt buộc JVM phải dùng miền đó.
3. Trong **Java 17+**, không cần và không thể dùng `strictfp` để thay đổi lựa chọn giữa hai chế độ này: **chỉ có ngữ nghĩa strict**.

Bởi vậy, khi phân tích một lỗi số học ghi nhận trên JVM cũ, hãy xem cả **phiên bản Java**, CPU/JVM và nguồn code có `strictfp`; đừng áp dụng suy luận “strictfp thay đổi kết quả” cho một chương trình thuần Java 17+ hiện đại.

## 5. Strict khác gì với độ chính xác số học?

Ngay cả khi luôn strict:

```java
double sum = 0.1 + 0.2;
System.out.println(sum == 0.3); // false
```

`0.1` và `0.2` không thể được biểu diễn chính xác bằng số hữu hạn bit của binary64. Mỗi phép toán làm tròn theo quy tắc IEEE 754 phù hợp. Strict yêu cầu phép toán tạo ra **kết quả đã làm tròn có thể biểu diễn**, không yêu cầu kết quả bằng số thực toán học vô hạn độ chính xác.

### Các điểm không thay đổi bởi always-strict

- `float` vẫn có khoảng 24 bit độ chính xác nhị phân; `double` vẫn có khoảng 53 bit.
- `NaN`, `+Infinity`, `-Infinity`, `+0.0`, `-0.0` vẫn tồn tại và hoạt động theo quy tắc số thực của Java.
- Phép chia dấu phẩy động cho zero vẫn có thể tạo vô cực/NaN thay vì `ArithmeticException`.
- Kết quả phép cộng, nhân có thể thay đổi khi đổi **thứ tự tính toán** do làm tròn và tràn.
- Không nên dùng `double` thay `BigDecimal` cho tiền hoặc nghiệp vụ đòi thập phân chính xác chỉ vì Java 17 đã always-strict.

### Strict và thứ tự đánh giá biểu thức

```java
double big = 1e16;

double left = (big + 1.0) - big; // 0.0
double right = (big - big) + 1.0; // 1.0
```

Ở miền `1e16`, chênh lệch 1 quá nhỏ để `double` phân biệt sau phép cộng với `big`, nên `(big + 1.0)` được làm tròn trở lại `big`. Nhưng `big - big` cho 0 chính xác, rồi cộng 1 cho 1.0.

**Always-strict không làm phép cộng dấu phẩy động có tính kết hợp như số thực toán học.** Nó chỉ xác định kết quả tương ứng với biểu thức và thứ tự đánh giá mà Java đã quy định. Đổi nhóm bằng dấu ngoặc, đổi thứ tự cộng dồn hoặc thay thuật toán vẫn có thể đổi kết quả.

## 6. Strict không có nghĩa “bất kể thao tác nào cũng cho cùng bit”

Từ Java 17, các toán tử dấu phẩy động của Java và các phép chuyển đổi tương ứng có quy tắc đánh giá strict. Nhưng nếu hai chương trình **không cùng phép toán hoặc không cùng thuật toán**, chúng không buộc phải cho cùng kết quả bit.

### 6.1. Math.fma khác với nhân rồi cộng riêng

`Math.fma(a, b, c)` (*fused multiply-add*) thực hiện tích và tổng với **một lần làm tròn cuối**, thay vì làm tròn sau nhân rồi lại làm tròn sau cộng. Nó có thể cho kết quả khác so với `a * b + c` ngay trên Java 17+:

```java
double epsilon = Math.ulp(1.0);
double a = 1.0 + epsilon;
double b = 1.0 - epsilon;

double separate = a * b - 1.0;
double fused = Math.fma(a, b, -1.0);

System.out.println(separate == 0.0); // true
System.out.println(fused < 0.0);      // true
```

Vì tích chính xác của hai toán hạng là `1 - epsilon²`, phép nhân riêng làm tròn về 1.0 rồi phép trừ cho 0.0. `fma` chỉ làm tròn một lần sau tổng cuối và giữ được phần dư âm rất nhỏ.

**Sự khác biệt này không phải lỗi vi phạm always-strict.** `Math.fma` là một phép toán khác, có quy tắc rõ ràng. Một tối ưu hóa không được tự ý thay `a*b+c` bằng `Math.fma(a,b,c)` nếu thay đổi kết quả có thể quan sát được.

### 6.2. Math và StrictMath không phải strictfp

Tên `StrictMath` dễ gây nhầm với modifier `strictfp`.

- **`strictfp`** là modifier ngôn ngữ lịch sử, liên quan đến cách đánh giá **biểu thức** trong phạm vi áp dụng.
- **`Math`** là lớp thư viện toán học có các hàm `sin`, `cos`, `log`, `sqrt`, `fma`, v.v. Hợp đồng làm tròn và sai số phụ thuộc **từng phương thức**.
- **`StrictMath`** là lớp thư viện được thiết kế cho các yêu cầu tái lập kết quả của những hàm được đặc tả theo cách nghiêm ngặt, đặc biệt nhiều hàm siêu việt gắn với thuật toán chuẩn tham chiếu lịch sử.

Java 17 always-strict **không có nghĩa mọi lời gọi `Math.sin` và `StrictMath.sin` đều phải có bit giống nhau** trong mọi tình huống. Cần đọc hợp đồng phương thức cụ thể, nhất là khi thuật toán yêu cầu tái lập bit giữa môi trường khác nhau.

Cũng không nên mặc định rằng thay mọi lời gọi `Math` bằng `StrictMath` là cần thiết hoặc giúp xử lý mọi sai số; nó giải quyết một lớp yêu cầu về thư viện toán học, không thay thế thiết kế thuật toán hay lựa chọn kiểu số.

### 6.3. Song song và thứ tự cộng dồn

Hai luồng tính tổng cùng dữ liệu nhưng theo hai thứ tự khác nhau có thể nhận kết quả dấu phẩy động khác nhau. Always-strict không buộc `parallelStream().sum()` và một vòng lặp tuần tự phải đánh giá các phép cộng theo đúng cùng cây tính toán nếu API cho phép khác biệt thuật toán.

Khi cần tái lập kết quả, hãy kiểm soát **thứ tự phép toán**, cách chia nhóm, thuật toán cộng dồn và những lời gọi thư viện liên quan. `strictfp` trên Java 17 không giải quyết thay cho những quyết định này.

## 7. Java 17+ xử lý overflow, underflow, NaN và signed zero thế nào?

Strict evaluation vẫn bao gồm các giá trị IEEE 754 đặc biệt:

| Biểu thức | Kết quả ở Java 17+ | Điều cần hiểu |
| --- | --- | --- |
| `Double.MAX_VALUE * 2.0` | `Infinity` | Tràn miền hữu hạn của double |
| `Double.MIN_VALUE / 2.0` | `+0.0` | Underflow từ số dương subnormal nhỏ nhất |
| `-Double.MIN_VALUE / 2.0` | `-0.0` | Underflow giữ dấu |
| `1.0 / 0.0` | `Infinity` | Chia thực cho 0 không ném ArithmeticException |
| `0.0 / 0.0` | `NaN` | Phép toán không có kết quả số thực xác định |
| `0.1 + 0.2 == 0.3` | `false` | Dấu phẩy động nhị phân vẫn làm tròn |
| `Math.fma(a,b,c)` so với `a*b+c` | Có thể khác nhau | Khác số lần làm tròn |

`Double.MIN_VALUE` **không phải số double âm nhỏ nhất**; nó là số dương khác 0 nhỏ nhất của `double`. Đây là một ca kiểm thử underflow rất hữu ích.

Với giá trị rất nhỏ nhưng vẫn biểu diễn được, Java hỗ trợ **subnormal numbers** và **gradual underflow**, không được tự ý “flush to zero” mọi số dưới ngưỡng chuẩn hóa. Trong Java 17+, các quy tắc strict cho `float`/`double` tiếp tục áp dụng ở những miền này.

## 8. Compiler xử lý strictfp trên Java 17+ ra sao?

Một chương trình Java 17+ vẫn có thể khai báo:

```java
public class LegacyStrictDemo {
    public static strictfp double twice(double x) {
        return x * 2.0;
    }
}
```

Đây là đoạn source độc lập và tên file phải là `LegacyStrictDemo.java`. Với `javac` hiện đại, khai báo có thể gây cảnh báo dạng:

```text
[strictfp] as of release 17, all floating-point expressions
are evaluated strictly and 'strictfp' is not required
```

Thông báo cụ thể/phần đường dẫn/số dòng phụ thuộc phiên bản công cụ, nên không nên coi chuỗi cảnh báo này là **stdout của chương trình**. Nó là cảnh báo **lúc biên dịch**, không phải kết quả của toán tử dấu phẩy động.

### Có nên xóa strictfp khỏi code cũ?

Nếu **chỉ triển khai và biên dịch cho Java 17 trở lên**, thường có thể bỏ modifier dư thừa mà không làm thay đổi ngữ nghĩa phép toán `float`/`double`. Tuy nhiên:

1. Hãy xác nhận phạm vi build thực sự chỉ nhắm Java 17+.
2. Nếu cùng source vẫn cần tương thích với Java 8/11 hay các phiên bản trước 17, không vội xóa `strictfp` khi tính tái lập dấu phẩy động có thể phụ thuộc modifier đó.
3. Kiểm thử những thuật toán nhạy với dấu phẩy động trước và sau refactor, đặc biệt nếu đồng thời thay đổi thứ tự phép toán, hàm `Math`/`StrictMath` hoặc phương thức `fma`.
4. Không cần thêm `strictfp` để “vá” sai số `0.1 + 0.2` hoặc để ép chương trình tự ném exception khi tính vô cực.

Cách giải thích phù hợp trong code review hiện đại là: **modifier còn được chấp nhận vì tương thích nhưng không làm kết quả phép tính strict hơn trên Java 17+**.

## 9. Chương trình Java 17+ chạy độc lập

Lưu file **`AlwaysStrictDemo.java`**:

```java
public class AlwaysStrictDemo {
    static double ordinary(double x) {
        return x * 2.0 / 2.0;
    }

    static strictfp double legacyStrict(double x) {
        return x * 2.0 / 2.0;
    }

    public static void main(String[] args) {
        double large = Double.MAX_VALUE;

        double plain = ordinary(large);
        double marked = legacyStrict(large);

        System.out.println("plain-infinite=" +
                Double.isInfinite(plain));
        System.out.println("marked-infinite=" +
                Double.isInfinite(marked));
        System.out.println("same-bits=" +
                (Double.doubleToLongBits(plain)
                        == Double.doubleToLongBits(marked)));

        double big = 1e16;
        System.out.println("round-left=" + ((big + 1.0) - big));
        System.out.println("round-right=" + ((big - big) + 1.0));
        System.out.println("decimal-equal=" + (0.1 + 0.2 == 0.3));

        double epsilon = Math.ulp(1.0);
        double a = 1.0 + epsilon;
        double b = 1.0 - epsilon;
        double separate = a * b - 1.0;
        double fused = Math.fma(a, b, -1.0);

        System.out.println("separate-zero=" + (separate == 0.0));
        System.out.println("fused-negative=" + (fused < 0.0));

        System.out.println("positive-underflow=" +
                (Double.MIN_VALUE / 2.0));
        System.out.println("negative-underflow=" +
                (-Double.MIN_VALUE / 2.0));
        System.out.println("nan-check=" +
                Double.isNaN(0.0 / 0.0));
    }
}
```

Biên dịch/chạy trên JDK 17 hoặc mới hơn:

```bash
javac AlwaysStrictDemo.java
java AlwaysStrictDemo
```

**Lưu ý:** `javac` có thể phát cảnh báo `strictfp` không cần thiết. Cảnh báo đó là output biên dịch, **không nằm trong stdout dưới đây**.

Stdout của chương trình:

```text
plain-infinite=true
marked-infinite=true
same-bits=true
round-left=0.0
round-right=1.0
decimal-equal=false
separate-zero=true
fused-negative=true
positive-underflow=0.0
negative-underflow=-0.0
nan-check=true
```

Diễn giải:

- Hai phương thức có và không có `strictfp` cho cùng kết quả tại phép tính tràn `double` trên Java 17+: `Infinity`.
- `round-left` và `round-right` cho kết quả khác nhau vì **dấu ngoặc thay đổi thứ tự các phép toán**, không liên quan đến modifier.
- `decimal-equal=false` chứng minh always-strict **không loại bỏ sai số nhị phân**.
- `separate-zero=true` và `fused-negative=true` cho thấy phép `Math.fma` (một lần làm tròn) không tương đương nhân rồi trừ với hai lần làm tròn.
- Underflow có thể cho zero dương hoặc âm; NaN vẫn có thể xuất hiện từ `0.0/0.0`.

Không nên đưa một đoạn ví dụ chỉ chạy được trên Java 17+ rồi suy ra rằng JVM cũ **chắc chắn** trả kết quả khác ở chỗ không có `strictfp`. Các JVM cũ vẫn có thể lựa chọn strict evaluation ở những nơi đặc tả lịch sử cho phép.

## 10. Những ngộ nhận phổ biến

| Ngộ nhận | Thực tế |
| --- | --- |
| “Java 17 bỏ từ khóa `strictfp` khỏi cú pháp.” | Modifier vẫn được nhận để tương thích, nhưng không ảnh hưởng đánh giá dấu phẩy động. |
| “Thêm `strictfp` vào Java 17 giúp kết quả chính xác hơn.” | Mọi biểu thức đã strict; modifier không bổ sung bit chính xác. |
| “Strictfp từng bảo đảm mọi tính toán ở độ chính xác thực vô hạn.” | Nó chỉ kiểm soát tập giá trị `float`/`double` và phép làm tròn phù hợp; kết quả vẫn có sai số. |
| “Chế độ không strict lịch sử luôn dùng phần trị nhiều bit hơn.” | Cơ chế lịch sử được đặc tả qua **extended exponent** với cùng số bit phần trị, không phải quyền tùy ý tăng precision. |
| “Mọi JVM trước 17 đều cho kết quả khác khi bỏ strictfp.” | Chế độ số mũ mở rộng được **cho phép**, không bị bắt buộc ở mọi JVM. |
| “Always-strict khiến phép cộng double có tính kết hợp.” | `(a+b)+c` vẫn có thể khác `a+(b+c)` do làm tròn. |
| “Always-strict khiến `Math.fma(a,b,c)` giống `a*b+c`.” | `fma` có một lần làm tròn, biểu thức tách rời có thể hai lần. |
| “`strictfp` và `StrictMath` là một tính năng.” | Modifier ngôn ngữ và lớp thư viện là hai cơ chế riêng. |
| “Strict có nghĩa mọi `Math`/ `StrictMath` bitwise giống nhau.” | Cần xem hợp đồng và thuật toán từng phương thức. |
| “Always-strict giúp tính tiền bằng double chính xác thập phân.” | `BigDecimal` hoặc số nguyên đơn vị nhỏ nhất vẫn phù hợp hơn khi yêu cầu decimal exactness. |
| “Một cảnh báo strictfp lúc biên dịch là lỗi runtime.” | Đó là warning từ compiler; chương trình vẫn có thể chạy. |

## 11. Áp dụng trong ứng dụng và kiểm thử

### 11.1. Khi nâng phiên bản Java

Một dự án cũ chuyển từ Java 8/11 sang Java 17 nên:

- Kiểm tra những chỗ dùng `strictfp` và ghi chú lý do lịch sử, nếu có.
- Đối chiếu kết quả ở các thuật toán nhạy với overflow/underflow, số subnormal, phép tính lặp nhiều lần và điều kiện ngưỡng.
- Không quy kết mọi thay đổi số học khi nâng Java cho JEP 306: thay đổi thư viện, thuật toán, thứ tự tổng hợp, kiểu dữ liệu hoặc mã ứng dụng cũng có thể làm khác kết quả.
- Nếu mã dùng `Math.sin`, `Math.log`… và cần khả năng lặp lại kết quả có tính ràng buộc, kiểm tra hợp đồng API cụ thể thay vì chỉ thêm modifier.

### 11.2. Khi thiết kế code Java 17+ mới

Lựa chọn thông thường là **không dùng `strictfp`**. Tập trung vào:

1. Chọn đúng kiểu `float`, `double`, `BigDecimal` hay số nguyên theo ý nghĩa dữ liệu.
2. Xác định mức sai số có thể chấp nhận và quy tắc so sánh.
3. Xác thực `NaN` và vô cực bằng `Double.isFinite` hoặc `Float.isFinite` nếu miền yêu cầu hữu hạn.
4. Kiểm soát thứ tự phép toán, phép cộng dồn song song và các lời gọi như `Math.fma`.
5. Kiểm thử biên gần overflow, subnormal và zero có dấu khi thuật toán nhạy với chúng.

### 11.3. Khi phải tái lập bit giữa nhiều máy hoặc nhiều lần chạy

Always-strict giúp **thu hẹp nguồn khác biệt từng đến từ các chế độ số mũ trung gian**. Nhưng một lời hứa “kết quả bit-for-bit giống nhau” cho cả ứng dụng còn phụ thuộc vào:

- Có dùng cùng biểu thức và cùng dữ liệu đầu vào bit-for-bit hay không.
- Thứ tự tính toán và các lần làm tròn (ví dụ FMA so với nhân/cộng riêng).
- Việc gọi các hàm thư viện có bảo đảm kết quả xác định theo hợp đồng cụ thể hay không.
- Mô hình song song, thứ tự reduction và những thay đổi của thuật toán.

Một phép toán primitive được xác định chặt chẽ **không tự làm toàn bộ pipeline số học trở thành có tính quyết định** nếu pipeline còn những lựa chọn thuật toán khác nhau.

## 12. Câu hỏi tự kiểm tra

1. Tại sao Java 17 gọi là **always-strict**, và nó khác gì chế độ mặc định cho phép extended exponent trên Java 1.2–16?
2. `strictfp` lịch sử mở rộng số bit phần trị hay chỉ **hạn chế sự mở rộng miền số mũ trung gian**? Giải thích đúng bản chất.
3. Trên Java 17+, hai phương thức có và không có `strictfp` nhưng có cùng biểu thức `double` có cần hai chế độ tính toán riêng không? Vì sao compiler có thể cảnh báo?
4. Vì sao `Double.MAX_VALUE * 2.0 / 2.0` không trả lại `Double.MAX_VALUE` trong ngữ nghĩa strict?
5. Vì sao `(1e16 + 1.0) - 1e16` khác `(1e16 - 1e16) + 1.0`? Always-strict có thay đổi điều đó không?
6. Hãy giải thích sự khác nhau giữa `Math.fma(a,b,c)` và `a*b+c` về **số lần làm tròn**.
7. Có nên thay toàn bộ `Math` bằng `StrictMath` để “bật strictfp” trong Java 17? Nêu đúng vai trò của từng tên.
8. Một ứng dụng Java 8 có `strictfp` trên các phép tính nhạy với số mũ. Khi nâng lên Java 17 và muốn xóa modifier, bạn cần kiểm tra những gì?
9. Vì sao always-strict không khiến `0.1 + 0.2 == 0.3` thành `true`?
10. Nếu ứng dụng Java 17 tính tổng theo nhiều thứ tự trong các worker khác nhau, sự khác biệt kết quả có thể xuất hiện dù mọi biểu thức đều strict không?

## 13. Checklist ghi nhớ

- **Java 17+ luôn strict:** không còn chọn giữa hai chế độ đánh giá dấu phẩy động theo `strictfp`.
- **`strictfp` mang tính lịch sử và tương thích nguồn** trên Java 17+; không mang đến độ chính xác cao hơn.
- **Java 1.2–16 cho phép extended-exponent value sets** trong ngữ cảnh không strict; cùng độ chính xác phần trị, miền số mũ rộng hơn.
- **Strict không loại bỏ làm tròn**, overflow, underflow, signed zero, Infinity hoặc NaN.
- **Biểu thức và thứ tự phép toán vẫn quan trọng**; `Math.fma` khác phép nhân cộng tách rời.
- **`StrictMath` là API thư viện**, không phải cơ chế kích hoạt modifier `strictfp`.
- **Kiểm thử reproducibility ở mức toàn thuật toán**, không chỉ nhìn modifier ở một method.
- Với dữ liệu cần **thập phân chính xác**, vẫn phải dùng mô hình số thích hợp như `BigDecimal`, không trông cậy `strictfp`.

### Nguồn đặc tả để đọc thêm

- [JEP 306 – Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306).
- [Java Language Specification, Java SE 17 – §15.4 Floating-point Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.4).
- [Java Virtual Machine Specification, Java SE 17 – §2.8 Floating-Point Arithmetic](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.8).

**Kết luận:** Java 17 không “làm số thực trở nên chính xác tuyệt đối”, mà **loại bỏ sự khác biệt về chế độ đánh giá strict/non-strict từng được cho phép vì phần cứng cũ**. `strictfp` vẫn xuất hiện trong mã di sản nhưng không làm thay đổi kết quả biểu thức Java 17+. Khi đánh giá tính đúng của phép toán thực, hãy chú ý miền biểu diễn, làm tròn, thứ tự phép tính, API thư viện và thuật toán.


</details>

- [Quay lại đầu trang](#back-to-top)
