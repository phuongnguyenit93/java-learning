<a id="back-to-top"></a>

# Sai lầm thường gặp

## Menu
- [Gán kết quả int đã tràn sang long rồi mong khôi phục giá trị](#common-numeric-mistakes-001)
- [Chia hai int trước khi chuyển sang double](#common-numeric-mistakes-002)
- [Dùng Float.MIN_VALUE hoặc Double.MIN_VALUE như số âm nhỏ nhất](#common-numeric-mistakes-003)
- [Dùng == để so sánh hai wrapper](#common-numeric-mistakes-004)
- [Unbox null trong phép toán hoặc toán tử điều kiện](#common-numeric-mistakes-005)
- [So sánh số thực bằng một epsilon tùy tiện](#common-numeric-mistakes-006)
- [Tạo BigDecimal từ double khi đầu vào cần chính xác thập phân](#common-numeric-mistakes-007)
- [Chia BigDecimal không chỉ định chính sách cho kết quả vô hạn tuần hoàn](#common-numeric-mistakes-008)
- [Nhầm precision với số chữ số sau dấu thập phân](#common-numeric-mistakes-009)
- [Dùng phép trừ trong Comparator có thể tràn](#common-numeric-mistakes-010)
- [Nhầm parseInt, decode và getInteger](#common-numeric-mistakes-011)
- [Định dạng rồi parse lại để thực hiện làm tròn tính toán](#common-numeric-mistakes-012)

## <a id="common-numeric-mistakes-001">Gán kết quả int đã tràn sang long rồi mong khôi phục giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Gán kết quả int đã tràn sang long không thể khôi phục giá trị trong Java

## 1. Sai lầm cốt lõi: kiểu đích rộng không thay đổi kiểu phép tính nguồn

Xét một đoạn Java tưởng như an toàn:

~~~java
int a = 2_000_000_000;
int b = 1_500_000_000;

long result = a + b;
System.out.println(result); // -794967296, KHÔNG phải 3500000000
~~~

Tại sao biến `result` là `long` 64 bit mà vẫn mang số âm?

**Vì `a + b` được tính hoàn toàn bằng `int` 32 bit trước**, vượt quá `Integer.MAX_VALUE = 2_147_483_647`. Kết quả `int` tràn theo quy tắc số nguyên Java. Sau khi phép cộng kết thúc, giá trị `int` **đã sai** mới được chuyển rộng sang `long`. Phép chuyển rộng giữ nguyên giá trị số âm đang có, chứ không biết kết quả toán học ban đầu.

~~~text
int a = 2_000_000_000
int b = 1_500_000_000
       |
       v
a + b   (phép cộng int, 32 bit)
       |
       v
-794_967_296   (đã tràn int)
       |
       v
chuyển int -> long
       |
       v
long result = -794_967_296
~~~

Tổng toán học đúng là `3_500_000_000`, nằm trong miền `long` nhưng không nằm trong miền `int`.

Sửa đúng:

~~~java
long result = (long) a + b; // 3500000000
~~~

Phép ép `(long) a` xảy ra **trước khi cộng**, nên `+` chọn phép cộng `long`; `b` cũng được chuyển sang `long` để thực hiện phép tính.

**Nguyên tắc:** Muốn phép tính dùng kiểu rộng, hãy làm cho **ít nhất một toán hạng phù hợp có kiểu rộng trước khi phép toán diễn ra**, hoặc sử dụng API phát hiện tràn. Không trông chờ kiểu biến nhận ở vế trái cứu kết quả đã tràn.

## 2. Vì sao Java tính bằng int trước khi gán sang long?

Java xác định kiểu của biểu thức `+`, `-`, `*`, `/`, `%` qua **binary numeric promotion** (quy tắc nâng kiểu số cho hai toán hạng):

- Nếu một toán hạng là `double`, phép toán thường thực hiện trong kiểu `double`.
- Nếu không có `double` nhưng có `float`, phép toán thường thực hiện trong kiểu `float`.
- Nếu không có hai kiểu trên nhưng có `long`, phép toán số nguyên thực hiện bằng `long`.
- Nếu chỉ còn các kiểu nguyên nhỏ hơn hoặc bằng `int` (`byte`, `short`, `char`, `int`), chúng được nâng lên `int`.

Trong ví dụ `a` và `b` đều là `int`, nên `a + b` là **biểu thức int**, bất kể nơi gán kết quả là `long`, `double`, `BigInteger` (qua chuyển đổi tường minh) hay một phương thức nhận long.

Thứ tự khái niệm là:

1. Xác định kiểu toán hạng và phép toán.
2. Tính biểu thức, có thể xảy ra tràn hoặc cắt phần lẻ.
3. Chuyển kiểu kết quả theo ngữ cảnh gán/gọi hàm.
4. Gán hoặc truyền giá trị đã tính.

Quy tắc gán mở rộng `int -> long` **không thay đổi bước 1 và bước 2**.

~~~java
int x = 2_000_000_000;
int y = 1_500_000_000;

long late = x + y;           // -794967296
long earlyLeft = (long) x + y; // 3500000000
long earlyRight = x + (long) y; // 3500000000
~~~

Bạn cũng có thể dùng literal có hậu tố `L`:

~~~java
long correct = 2_000_000_000L + 1_500_000_000; // 3500000000
~~~

Ngược lại, chỉ đổi kiểu vế trái không đủ:

~~~java
long wrong = 2_000_000_000 + 1_500_000_000; // -794967296
~~~

**Lưu ý về constant expression:** Java có thể tính biểu thức hằng ngay tại thời điểm biên dịch. Điều đó không có nghĩa phép cộng hai `int` tự động được nâng sang `long`. Hãy kiểm tra **kiểu của toán hạng**, kể cả trong hằng số và biểu thức không có biến.

## 3. Một ví dụ khác: nhân hai int trước khi gán sang long

Ví dụ tính diện tích hoặc tổng tiền:

~~~java
int quantity = 50_000;
int unitPrice = 50_000;

long wrong = quantity * unitPrice;       // -1794967296
long stillWrong = (long) (quantity * unitPrice); // -1794967296
long correct = (long) quantity * unitPrice;      // 2500000000
~~~

Tích toán học là `50_000 × 50_000 = 2_500_000_000`, vượt miền `int`. Khi chỉ có toán hạng int, phép `*` cho kết quả int đã tràn.

Sự khác biệt giữa hai cách viết rất quan trọng:

| Biểu thức | Trình tự | Kết quả |
| --- | --- | ---: |
| `(long) (a * b)` | Tính `int * int` trước, **sau đó** cast | Có thể là số sai đã tràn |
| `(long) a * b` | Cast `a` trước, rồi `long * long` | Giữ được tích của hai int trong miền long |
| `a * (long) b` | Cast `b` trước, rồi `long * long` | Tương tự |
| `1L * a * b` | Nhân với `1L` trước theo thứ tự kết hợp | Tích tính bằng long ngay từ đầu |

Trong Java, **tích toán học của hai giá trị int bất kỳ vừa trong miền long**, nên chuyển ít nhất một toán hạng sang long trước phép nhân là đủ tránh **tràn số nguyên tại phép nhân đó**. Tuy nhiên, một biểu thức chứa nhiều lần nhân hoặc cộng trên các giá trị long vẫn có thể tràn `long` nếu vượt miền 64 bit.

Ví dụ, `a * b * 1L` **có thể sai** vì Java kết hợp phép nhân từ trái sang phải: `a * b` được tính bằng int trước, sau đó mới nhân với `1L`. Hãy viết `1L * a * b` hoặc `(long) a * b`.

## 4. Dấu ngoặc và phép ép: vị trí quyết định tính đúng

So sánh:

~~~java
long a = (long) (x + y); // Quá muộn nếu x+y đã tràn int
long b = (long) x + y;   // x đã là long khi thực hiện phép cộng
~~~

Dấu ngoặc ở biểu thức thứ nhất bắt Java tính `x + y` như một biểu thức `int` trước khi ép; ép kiểu ngoài ngoặc chỉ giữ nguyên giá trị int đã tính xong.

Với nhiều phép toán, cần xem xét **từng phép trung gian**:

~~~java
int a = 1_500_000_000;
int b = 1_500_000_000;
int c = 1;

long wrong = (a + b) + (long) c; // a+b đã tràn int
long correct = (long) a + b + c; // mọi phép cộng trong chuỗi này là long
~~~

Một trường hợp khác:

~~~java
int width = 50_000;
int height = 50_000;

long wrongArea = width * height;          // tràn int
long correctArea = 1L * width * height;   // long ngay từ phép nhân đầu
~~~

Tự hỏi tại **nút phép toán** trong cây biểu thức: hai toán hạng lúc đó có kiểu gì? Câu trả lời có thể quan trọng hơn kiểu khai báo của kết quả.

## 5. Chương trình Java 17 đầy đủ và kết quả chính xác

Tạo file `OverflowBeforeWideningDemo.java`:

~~~java
import java.util.Arrays;

public class OverflowBeforeWideningDemo {
    public static void main(String[] args) {
        int a = 2_000_000_000;
        int b = 1_500_000_000;

        long sumTooLate = a + b;
        long sumCorrect = (long) a + b;
        System.out.println("sum too late = " + sumTooLate);
        System.out.println("sum widened = " + sumCorrect);

        int x = 50_000;
        int y = 50_000;
        long productTooLate = (long) (x * y);
        long productCorrect = (long) x * y;
        System.out.println("product too late = " + productTooLate);
        System.out.println("product widened = " + productCorrect);

        try {
            Math.addExact(a, b);
            System.out.println("Unexpected add success");
        } catch (ArithmeticException ex) {
            System.out.println("addExact = overflow detected");
        }

        try {
            Math.multiplyExact(x, y);
            System.out.println("Unexpected multiply success");
        } catch (ArithmeticException ex) {
            System.out.println("multiplyExact = overflow detected");
        }

        int[] numbers = {1_500_000_000, 1_500_000_000, 1};
        int wrongSum = Arrays.stream(numbers).sum();
        long correctSum = Arrays.stream(numbers).asLongStream().sum();
        System.out.println("int stream sum = " + wrongSum);
        System.out.println("long stream sum = " + correctSum);

        int min = Integer.MIN_VALUE;
        long wrongAbs = Math.abs(min);
        long correctAbs = Math.abs((long) min);
        System.out.println("abs too late = " + wrongAbs);
        System.out.println("abs widened = " + correctAbs);

        long daysWrong = 30 * 24 * 60 * 60 * 1000;
        long daysCorrect = 30L * 24 * 60 * 60 * 1000;
        System.out.println("30 days millis wrong = " + daysWrong);
        System.out.println("30 days millis correct = " + daysCorrect);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac OverflowBeforeWideningDemo.java
java OverflowBeforeWideningDemo
~~~

Kết quả:

~~~text
sum too late = -794967296
sum widened = 3500000000
product too late = -1794967296
product widened = 2500000000
addExact = overflow detected
multiplyExact = overflow detected
int stream sum = -1294967295
long stream sum = 3000000001
abs too late = -2147483648
abs widened = 2147483648
30 days millis wrong = -1702967296
30 days millis correct = 2592000000
~~~

**Giải thích kết quả:**

1. Phép cộng `2_000_000_000 + 1_500_000_000` vượt `Integer.MAX_VALUE`, nên kết quả int là `-794_967_296`. Cast trước phép cộng giữ kết quả `3_500_000_000`.
2. Phép nhân `50_000 × 50_000` cho `2_500_000_000`, cũng vượt int; kiểu `long` phải tham gia **trước** phép nhân.
3. `Math.addExact(int, int)` và `Math.multiplyExact(int, int)` phát hiện tràn bằng `ArithmeticException`, thay vì âm thầm wrap.
4. `IntStream.sum()` trả int nên tổng có thể tràn; chuyển các phần tử sang long bằng `asLongStream()` **trước khi cộng dồn** tránh tràn int tại bước tổng.
5. `Math.abs(Integer.MIN_VALUE)` trả lại chính `Integer.MIN_VALUE` vì `2147483648` không thuộc miền int dương. Ép đầu vào lên long **trước** `Math.abs` cho `2147483648`.
6. Tích `30 × 24 × 60 × 60 × 1000` (mili giây trong 30 ngày, bỏ qua DST và chỉ minh họa số học độ dài cố định) cho `2_592_000_000`, vượt int. Dùng `30L` ngay từ đầu để kết quả tính bằng long.

Những dòng ra ở trên là cố định, không phụ thuộc vào luồng, thời gian thực hoặc đầu vào bên ngoài.

## 6. Tại sao long chỉ giữ lại kết quả đã tràn?

Với các phép số nguyên cộng, trừ, nhân Java, overflow **không tự ném exception**. Về mặt giá trị, phép toán int dùng 32 bit; kết quả khi vượt miền có thể “quay vòng” theo bit thấp, rồi được diễn giải lại như số int có dấu.

Phép cộng ví dụ:

~~~text
Tổng toán học:         3,500,000,000
2^32:                  4,294,967,296
Kết quả int có dấu:     -794,967,296
Mở rộng sang long:      -794,967,296
~~~

Khi chuyển từ `int` âm sang `long`, Java thực hiện phép mở rộng dấu (*sign extension*) để **giữ nguyên giá trị số âm**, không cố khôi phục bit đã loại trong phép tính trước đó. Nói cách khác, phép `int -> long` tự nó có thể chính xác — vấn đề nằm ở **phép tính int trước khi chuyển**.

Một điểm tinh tế: Có thể có nhiều tổng toán học khác nhau cùng tạo một giá trị int giống nhau sau tràn. Chẳng hạn `-794_967_296` có thể là giá trị hợp lệ chưa tràn hoặc là phần dư sau phép cộng `3_500_000_000`. Chỉ dựa vào một kết quả int đã lưu, không thể tự xác định tổng chính xác ban đầu là gì nếu không có thêm toán hạng hoặc thông tin khác.

Nếu dữ liệu nguồn vẫn được giữ nguyên, bạn có thể **tính lại** bằng long. Đó là tính lại từ nguồn, **không phải** khôi phục phép tính đã tràn chỉ bằng một phép ép trên kết quả sai.

## 7. Phòng tránh: nâng kiểu trước khi tính hoặc phát hiện tràn

Có ba chiến lược hay gặp.

### Chiến lược A — Tính bằng long khi miền kết quả đủ chứa

~~~java
int price = 50_000;
int quantity = 50_000;

long total = (long) price * quantity;
~~~

Dùng khi kết quả đúng có thể vượt int nhưng vẫn nằm trong long. Với tổng nhiều int, cho biến tích lũy là long từ đầu:

~~~java
int[] values = {1_500_000_000, 1_500_000_000, 1};

long total = 0L;
for (int v : values) {
    total += v; // long + int -> long
}
~~~

Đừng cộng toàn bộ bằng int rồi chuyển tổng sang long:

~~~java
int partial = 0;
for (int v : values) {
    partial += v; // Có thể tràn int ở vòng lặp!
}
long wrong = partial;
~~~

### Chiến lược B — Bắt overflow khi kiểu kết quả bắt buộc là int

Nếu hợp đồng API yêu cầu giá trị kiểu int và vượt miền phải bị từ chối:

~~~java
int safeSum = Math.addExact(a, b);
int safeProduct = Math.multiplyExact(a, b);
~~~

`Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `decrementExact` và `negateExact` có những overload cho kiểu nguyên thích hợp và báo `ArithmeticException` khi kết quả không biểu diễn được.

Ví dụ:

~~~java
int count = Integer.MAX_VALUE;
int next = Math.incrementExact(count);
// ArithmeticException, thay vì quay vòng sang Integer.MIN_VALUE.
~~~

Nếu tính bằng long rồi cần chuyển về int, dùng:

~~~java
long wide = (long) a + b;
int checked = Math.toIntExact(wide);
~~~

`Math.toIntExact` nhận giá trị long đã tính chính xác và chỉ cho phép chuyển khi vừa int. Nhưng `Math.toIntExact(a + b)` là **sai thứ tự** nếu `a + b` đã tràn int: overload nhận long chỉ thấy kết quả int đã sai được chuyển sang long; phép kiểm tra có thể không phát hiện vấn đề.

### Chiến lược C — Dùng BigInteger khi long cũng không đủ

`long` có miền hữu hạn: `-9_223_372_036_854_775_808` đến `9_223_372_036_854_775_807`. Các phép long cộng/nhân vẫn có thể tràn.

Khi bài toán cần số nguyên lớn hơn miền long:

~~~java
import java.math.BigInteger;

BigInteger left = BigInteger.valueOf(Long.MAX_VALUE);
BigInteger answer = left.add(BigInteger.ONE);
System.out.println(answer); // 9223372036854775808
~~~

Đây là **mảnh code**, cần đặt trong lớp/phương thức để chạy. `BigInteger` có độ chính xác số nguyên tùy ý, giới hạn thực tế bởi bộ nhớ và tài nguyên hệ thống.

Nếu các toán hạng đã là `long`, chuyển **trước phép tính**, không phải sau khi long đã tràn:

~~~java
BigInteger aBig = BigInteger.valueOf(longA);
BigInteger bBig = BigInteger.valueOf(longB);
BigInteger correct = aBig.multiply(bBig);
~~~

`BigInteger.valueOf(longA * longB)` vẫn có thể nhận một kết quả long **đã tràn**, nên không có tác dụng bảo toàn phép nhân.

## 8. Những bẫy cùng bản chất: Math.abs, phép chia và phép phủ định

### 8.1. Math.abs(Integer.MIN_VALUE)

`Integer.MIN_VALUE` là `-2147483648`. Giá trị tuyệt đối toán học `2147483648` vượt `Integer.MAX_VALUE`:

~~~java
int min = Integer.MIN_VALUE;

long wrong = Math.abs(min);         // -2147483648
long correct = Math.abs((long) min); // 2147483648
~~~

`Math.abs(int)` trả int, nên không thể tạo ra số dương 2147483648. Cast đầu vào trước làm Java chọn `Math.abs(long)`, sau đó mới lấy trị tuyệt đối.

Đây cũng là ví dụ vì sao **chọn overload** dựa trên kiểu toán hạng có thể quan trọng: kiểu biến nhận `long` không làm phương thức `Math.abs(int)` trở thành `Math.abs(long)`.

### 8.2. Phủ định số int nhỏ nhất

~~~java
int min = Integer.MIN_VALUE;

long wrong = -min;        // -2147483648: phủ định int bị tràn
long correct = -(long) min; // 2147483648
~~~

Phép toán unary `-` trên biến int cũng cho kết quả int. Chuyển sau khi phủ định không sửa được kết quả. `Math.negateExact(min)` báo overflow, phù hợp khi kiểu đích bắt buộc int.

### 8.3. Chia Integer.MIN_VALUE cho -1

Đây là trường hợp đặc biệt của phép chia nguyên int:

~~~java
int min = Integer.MIN_VALUE;

long wrong = min / -1;          // -2147483648
long correct = (long) min / -1; // 2147483648
~~~

`-2147483648 / -1` có thương toán học `2147483648`, vượt miền int. Java quy định trường hợp này cho kết quả `Integer.MIN_VALUE` nếu phép chia được thực hiện bằng int. Trong khi đó, một toán hạng long chuyển phép chia sang long và cho thương đúng.

Một phép chia thông thường còn có rủi ro **cắt phần lẻ trước khi gán**:

~~~java
int numerator = 5;
int denominator = 2;

double wrong = numerator / denominator;          // 2.0
double correctApprox = (double) numerator / denominator; // 2.5
~~~

Đây không phải overflow; nguyên nhân vẫn là **phép toán trên kiểu nguồn hoàn tất trước khi kết quả được gán**. Nếu cần độ chính xác thập phân có quy tắc, dùng `BigDecimal.divide` với scale và `RoundingMode` phù hợp.

## 9. Ngữ cảnh gọi hàm cũng không tự cứu phép tính

Lỗi không chỉ xuất hiện trong phép gán:

~~~java
static void report(long value) {
    System.out.println(value);
}
~~~

Với `int a` và `int b`:

~~~java
report(a + b);         // a+b là int, có thể tràn, rồi mới truyền cho long
report((long) a + b);  // cộng bằng long trước khi truyền
~~~

Đây là các **mảnh mã** cần đặt trong lớp chứa phương thức `report` và các biến tương ứng.

Tương tự, API nhận `long` không tự nâng kiểu của một biểu thức con đã là `int`:

~~~java
long millis = 30 * 24 * 60 * 60 * 1000;   // int overflow
long fixedMillis = 30L * 24 * 60 * 60 * 1000; // long
~~~

Một cách tránh phép nhân thủ công khi chuyển đơn vị thời gian là chọn API thời gian phù hợp (ví dụ `Duration.ofDays(30).toMillis()`) và hiểu rõ ngữ nghĩa khoảng thời gian/đơn vị lịch. `Duration` tính độ dài thời gian theo đơn vị xác định; nó không đồng nghĩa tự cộng 30 **ngày lịch tại múi giờ cụ thể**, khi có chuyển giờ mùa hè (DST).

## 10. Tích lũy: kiểm tra kiểu của cả biến tổng lẫn từng phép cộng

Lỗi thường gặp trong thống kê và stream:

~~~java
int[] numbers = {1_500_000_000, 1_500_000_000, 1};

int sum = java.util.Arrays.stream(numbers).sum();
// -1294967295 vì sum của IntStream là int.

long wrong = java.util.Arrays.stream(numbers).sum();
// Vẫn -1294967295: gán rộng sau khi IntStream.sum() hoàn tất.

long correct = java.util.Arrays.stream(numbers)
        .asLongStream()
        .sum();
// 3000000001: mỗi phần tử được đưa vào phép cộng long.
~~~

`mapToLong(value -> value).sum()` cũng là lựa chọn để nâng từng phần tử trước khi cộng.

Trong vòng lặp, phải dùng biến tổng long **ngay từ đầu**:

~~~java
long total = 0L;
for (int number : numbers) {
    total += number; // long + int => long
}
~~~

Tuy nhiên `long total` không bảo đảm an toàn vô điều kiện: tổng của một danh sách đủ dài hoặc giá trị rất lớn vẫn có thể vượt miền long. Nếu trường hợp đó thực tế có thể xảy ra và không cho phép mất dữ liệu, dùng `Math.addExact(long, long)` cho từng lần cộng hoặc tích lũy bằng `BigInteger`.

## 11. Phân biệt overflow số nguyên với lỗi precision của float/double

Có người sửa bằng cách chuyển sang `double`:

~~~java
int a = 2_000_000_000;
int b = 1_500_000_000;

double wrong = a + b;            // -794967296.0
double betterHere = (double) a + b; // 3500000000.0
~~~

Đổi một toán hạng sang double trước phép cộng tránh overflow int trong trường hợp này; nhưng **double không giữ chính xác mọi giá trị số nguyên lớn**. `double` chỉ có 53 bit chữ số có nghĩa nhị phân, nên không nên dùng như giải pháp tổng quát cho tính toán số nguyên chính xác trong miền long.

Ví dụ:

~~~java
long n = 9_007_199_254_740_993L; // 2^53 + 1
double d = (double) n;            // Không biểu diễn đúng n.
~~~

Nếu cần kết quả nguyên chính xác của các phép toán trên int và kết quả vừa miền long, lựa chọn tự nhiên là **long**, không phải double. Nếu long không đủ, hãy chuyển sang `BigInteger` trước khi tính.

Cũng đừng dùng `(long) (doubleExpression)` để tự động “phục hồi” chữ số đã mất trong `double`: chuyển kiểu ngược không tạo lại thông tin ban đầu.

## 12. Điều kiện phát hiện overflow và giới hạn kiểu kết quả

Có hai mục tiêu khác nhau:

### A. Muốn một kết quả rộng và chính xác

~~~java
int a = Integer.MAX_VALUE;
int b = 1;

long total = (long) a + b; // 2147483648
~~~

### B. Muốn giữ kết quả int và từ chối nếu không biểu diễn được

~~~java
int a = Integer.MAX_VALUE;
int b = 1;

// int total = Math.addExact(a, b); // ArithmeticException
~~~

Khi ứng dụng yêu cầu báo lỗi thay vì tự động chuyển sang long, `Math.addExact` phù hợp hơn.

Có thể kiểm tra thủ công bằng cách tính bằng long rồi so biên:

~~~java
static int checkedAdd(int a, int b) {
    long exact = (long) a + b;
    if (exact < Integer.MIN_VALUE || exact > Integer.MAX_VALUE) {
        throw new ArithmeticException("int overflow");
    }
    return (int) exact;
}
~~~

Với **tổng của hai int**, long luôn chứa được kết quả toán học, nên kiểm tra biên trên long là chính xác. Phương thức `Math.addExact(a, b)` ngắn gọn hơn và đã được chuẩn hóa.

Trường hợp `Math.toIntExact(wide)` cũng hợp lệ khi biến `wide` thật sự chứa giá trị đúng từ một phép tính rộng hoặc từ dữ liệu ngoài:

~~~java
long wide = (long) a + b;
int value = Math.toIntExact(wide); // Ném ArithmeticException nếu ngoài int.
~~~

Điểm quan trọng là biến `wide` phải được tạo **trước khi overflow**, chứ không phải `long wide = a + b`.

## 13. Những lỗi phổ biến cần nhận diện ngay khi review code

| Mã dễ sai | Lý do | Mã thay thế / điều kiện |
| --- | --- | --- |
| `long s = a + b;` với hai int | Tổng int có thể tràn trước khi gán | `long s = (long) a + b;` |
| `long p = a * b;` | Tích int có thể tràn | `long p = (long) a * b;` |
| `long p = (long)(a * b);` | Cast quá muộn | `long p = (long)a * b;` |
| `long p = a * b * 1L;` | `a*b` đã tính trước | `long p = 1L*a*b;` |
| `Math.toIntExact(a + b)` | `a+b` đã tràn trong int nên kiểm tra không thấy | `Math.addExact(a,b)` hoặc `Math.toIntExact((long)a+b)` |
| `long x = Math.abs(intValue);` | `Math.abs(int)` có thể tràn tại MIN_VALUE | `long x = Math.abs((long)intValue);` |
| `long x = -intValue;` | Unary negation vẫn tính bằng int | `long x = -(long)intValue;` |
| `long m = 30*24*60*60*1000;` | Toàn literal int; phép nhân tràn | `long m = 30L*24*60*60*1000;` |
| `long sum = intStream.sum();` | `sum()` của IntStream trả int | `long sum = intStream.asLongStream().sum();` |

Trong code review, đừng chỉ hỏi “biến cuối cùng có phải long không?”. Hãy đi ngược lên từng biểu thức, phương thức, overload và thao tác trung gian, rồi xác định **kiểu tại lúc phép tính thực sự chạy**.

## 14. Phép tràn có thể không tạo số âm rõ ràng

Không phải overflow nào cũng lộ ra như một số âm khiến người lập trình nghi ngờ. Kết quả sau wrap có thể là **số dương trông hợp lý**, **số 0**, hoặc một giá trị bất kỳ trong miền int.

Ví dụ:

~~~java
int value = Integer.MAX_VALUE;
int doubled = value * 2; // -2
~~~

Nhưng khi cộng tiếp nhiều giá trị, kết quả có thể vòng qua miền và trở lại giá trị dương. Việc thấy “kết quả dương”, “kết quả không ném lỗi”, hoặc “gán được vào long” không bảo đảm phép tính chính xác.

Không nên lấy dấu của kết quả hay ngưỡng tùy tiện làm bộ phát hiện overflow tổng quát. Ví dụ `result < 0` không phải dấu hiệu đầy đủ vì các toán hạng có thể âm hợp lệ, và overflow có thể cho số dương. Dùng `Math.*Exact` hoặc tính trước bằng kiểu đủ rộng và so phạm vi.

## 15. Bài tập có đáp án

### Bài 1: Sửa một phép nhân diện tích

~~~java
int width = 70_000;
int height = 70_000;
long area = width * height;
~~~

**Đáp án:** `long area = (long) width * height;`, kết quả chính xác `4_900_000_000`. Bản ban đầu thực hiện phép nhân int trước và có thể cho số sai.

### Bài 2: Dự đoán giá trị

~~~java
int a = 2_000_000_000;
int b = 1_500_000_000;

long first = a + b;
long second = (long) a + b;
long third = (long) (a + b);
~~~

**Đáp án:** `first = -794967296`, `second = 3500000000`, `third = -794967296`. Chỉ `second` chuyển một toán hạng trước khi tính.

### Bài 3: Vì sao Math.toIntExact vẫn có thể không bắt được lỗi?

~~~java
int a = 2_000_000_000;
int b = 1_500_000_000;
int result = Math.toIntExact(a + b);
~~~

**Đáp án:** `a+b` đã tràn int thành `-794967296`. Khi đối số được chuyển thành long để truyền cho `toIntExact`, nó vẫn nằm trong miền int; `toIntExact` trả chính giá trị âm ấy và **không báo lỗi**. Sửa thành `Math.addExact(a,b)` (sẽ ném exception) hoặc `Math.toIntExact((long)a+b)`.

### Bài 4: Tổng stream

Với `int[] values = {1_500_000_000, 1_500_000_000, 1}`, tại sao `long sum = Arrays.stream(values).sum();` không đúng?

**Đáp án:** `IntStream.sum()` hoàn tất phép cộng theo int rồi mới chuyển kết quả sang long. Dùng `Arrays.stream(values).asLongStream().sum()` để cộng thành long từ đầu, kết quả `3000000001`.

### Bài 5: Trị tuyệt đối

Vì sao `long magnitude = Math.abs(Integer.MIN_VALUE);` âm?

**Đáp án:** Overload `Math.abs(int)` trả int và không thể biểu diễn trị tuyệt đối toán học của `Integer.MIN_VALUE`. Sửa `Math.abs((long) Integer.MIN_VALUE)` để nhận `2147483648`.

### Bài 6: Cách chọn chính sách lỗi

Một API trả `int` bắt buộc, yêu cầu tổng `a+b` không được mất dữ liệu. Nên chọn kiểu long và ép lại không kiểm tra, hay gọi `Math.addExact`?

**Đáp án:** Gọi `Math.addExact(a,b)` để từ chối trường hợp vượt int. Nếu yêu cầu nghiệp vụ cho phép tổng lớn hơn và kiểu kết quả có thể là long, dùng `(long)a+b`. Chọn theo **hợp đồng dữ liệu đích**, không theo thói quen cast.

## 16. Checklist trước khi gán kết quả tính toán số nguyên

1. **Hai toán hạng tại từng phép cộng/nhân/trừ/chia đang có kiểu gì?** Có `int` ở một phép trung gian không?
2. **Kiểu rộng có xuất hiện trước phép toán rủi ro chưa?** `(long)(a*b)` không thay thế cho `(long)a*b`.
3. **Các dấu ngoặc và thứ tự kết hợp có tạo phần `int` đã tràn trước khi gặp long không?**
4. **Có overload phương thức trả int không?** Đặc biệt `Math.abs(int)`, `IntStream.sum()`, `Math.max(int,int)`.
5. **Có tính qua literal int trước khi gán long không?** Dùng `L` đúng vị trí hoặc API chuyên biệt.
6. **Hợp đồng muốn kết quả rộng hay phát hiện overflow?** Chọn `long`, `Math.*Exact` hoặc `BigInteger` phù hợp.
7. **Nếu phép tính đã xảy ra rồi, còn giữ các toán hạng gốc để tính lại chính xác không?** Cast kết quả sai không thể khôi phục dữ liệu.
8. **Đã test sát biên chưa?** `Integer.MAX_VALUE + 1`, `Integer.MIN_VALUE`, phép nhân lớn, cộng dồn dài, `MIN_VALUE / -1`.

**Kết luận:** Trong Java, **kiểu biến nhận không quyết định kiểu của phép toán trước đó**. `long result = a + b` vẫn tính int nếu `a` và `b` là int, và một khi biểu thức đó đã tràn, chuyển thành long chỉ lưu **giá trị đã tràn**. Muốn đúng, **nâng ít nhất một toán hạng trước phép toán**, dùng `Math.*Exact` để phát hiện lỗi khi phải giữ int, hoặc dùng `BigInteger` nếu long cũng không đủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-002">Chia hai int trước khi chuyển sang double</a>

<details>
<summary>Xem chi tiết</summary>

# Chia hai int trước khi chuyển sang double: vì sao mất phần thập phân?

## 1. Lỗi điển hình

Trong Java, đoạn code sau **không cho ra 3.5**:

~~~java
int numerator = 7;
int denominator = 2;
double ratio = numerator / denominator;

System.out.println(ratio); // 3.0
~~~

Dù biến đích có kiểu `double`, phép chia `numerator / denominator` được Java thực hiện **trước**, trên **hai toán hạng `int`**, nên dùng **phép chia nguyên** và cho kết quả `3`. Sau đó `3` mới được chuyển thành `3.0` khi gán cho `ratio`. Phần `0.5` đã bị bỏ và phép chuyển đổi về sau không thể khôi phục.

Cách sửa đúng theo mục tiêu tính tỷ số thực:

~~~java
double ratio = (double) numerator / denominator; // 3.5
~~~

Dòng trên chuyển **một toán hạng** sang `double` **trước phép chia**. Phép `/` khi đó áp dụng **binary numeric promotion** và tính phép chia dấu phẩy động, cho kết quả `3.5`.

Bài này tập trung vào thứ tự đánh giá biểu thức, quy tắc ép kiểu, các lỗi xảy ra khi tính trung bình/phần trăm/tiến độ, chia số âm, chia cho 0, các trường hợp overflow, và khi nào `double` vẫn không đủ chính xác.

## 2. Kiểu toán hạng quyết định kiểu phép chia

Trong Java, dấu `/` áp dụng cho các kiểu số primitive; trước khi tính, Java thực hiện các quy tắc **binary numeric promotion**. Nói đơn giản:

- Nếu một toán hạng là `double`, phép toán dùng `double`.
- Nếu không có `double` nhưng có `float`, dùng `float`.
- Nếu không có `double`/`float` nhưng có `long`, dùng `long`.
- Còn lại, các kiểu số nguyên nhỏ như `byte`, `short`, `char` được nâng thành `int` và phép toán dùng `int`.

**Điểm quyết định là kiểu của biểu thức ngay tại toán tử `/`**, không phải kiểu của biến được gán kết quả sau đó.

| Biểu thức | Kiểu phép chia | Kết quả | Giải thích |
|---|---|---|---|
| `7 / 2` | `int` | `3` | Bỏ phần thập phân |
| `7L / 2` | `long` | `3L` | Có `long` nhưng vẫn là chia nguyên |
| `7.0 / 2` | `double` | `3.5` | Literal `7.0` là double |
| `7 / 2.0` | `double` | `3.5` | Literal `2.0` là double |
| `7f / 2` | `float` | `3.5f` | Literal có hậu tố `f` |
| `(double) 7 / 2` | `double` | `3.5` | Ép toán hạng trước khi chia |
| `(double) (7 / 2)` | `int` rồi ép `double` | `3.0` | Ép **kết quả đã bị cắt phần dư** |
| `7 / (double) 2` | `double` | `3.5` | Ép toán hạng bên phải trước |
| `7 / 2 * 1.0` | `int` ở phép chia đầu | `3.0` | Phần lẻ mất trước khi nhân |
| `7 * 1.0 / 2` | `double` ở phép chia | `3.5` | Chuyển sang double từ phép nhân đầu |

`int` và `long` khác độ rộng số nguyên; tăng độ rộng sang `long` **không tự tạo kết quả thập phân**. `float` và `double` cho phép biểu diễn phân số nhưng là số dấu phẩy động nhị phân với độ chính xác hữu hạn.

### Vì sao kiểu đích không ảnh hưởng ngược lại?

Xét:

~~~java
double result = 7 / 2;
~~~

Phân tích theo thứ tự:

1. Java suy ra literal `7` và `2` đều là `int`.
2. Phép `/` được tính là phép chia `int`: `7 / 2 == 3`.
3. Gán cho biến `double` kích hoạt widening primitive conversion: `3` thành `3.0`.

Không có bước nào xem `result` rồi “quay lại” thực hiện phép chia bằng double. Tương tự:

~~~java
double result = 7 / 2;           // 3.0
double result2 = (double) (7 / 2); // 3.0
double result3 = (double) 7 / 2;   // 3.5
~~~

Với ba biểu thức này, Java có thể gán kết quả cho `double` ở cả ba trường hợp; nhưng chỉ biểu thức thứ ba dùng chia dấu phẩy động ngay từ đầu.

## 3. Vị trí dấu ngoặc và ép kiểu làm thay đổi kết quả

So sánh hai cách đặt dấu ngoặc:

~~~java
int a = 7;
int b = 2;

double wrong = (double) (a / b);  // 3.0
double right = ((double) a) / b;  // 3.5
~~~

Ở `wrong`, ngoặc tròn `(a / b)` buộc thực hiện phép chia nguyên trước. Ở `right`, ngoặc `((double) a)` chỉ bao toán hạng `a`, khiến `a` thành `double` khi dấu `/` được đánh giá.

Có thể ép **một trong hai toán hạng**:

~~~java
double x = (double) a / b; // 3.5
double y = a / (double) b; // 3.5
double z = a / 2.0;        // 3.5
~~~

Trường hợp số chia đến từ biến, ép kiểu rõ ràng trên `a` hoặc `b` dễ đọc và tránh vô tình thay đổi công thức khi giá trị literal không còn cố định.

### Toán tử cùng ưu tiên được đánh giá từ trái sang phải

Nhân và chia có cùng mức ưu tiên, kết hợp từ trái sang phải:

~~~java
double lost = 7 / 2 * 100.0; // 300.0
double good = 7 * 100.0 / 2; // 350.0
~~~

Ở `lost`, Java đã thực hiện `7 / 2 == 3` rồi mới nhân `100.0`. Việc xuất hiện `double` ở **phía sau** không cứu phần thập phân đã mất. Trong `good`, phép nhân `7 * 100.0` sinh double trước, nên phép chia tiếp theo cũng dùng double.

Điều tương tự xuất hiện trong code thực tế:

~~~java
int completed = 3;
int total = 8;

double incorrectPercent = 100.0 * (completed / total); // 0.0
double correctPercent = 100.0 * completed / total;     // 37.5
~~~

Cách có ngoặc đầu tiên tính `3 / 8 == 0`, rồi nhân 100; cách đúng nhân với `100.0` trước, cho tỷ lệ thực `37.5%`.

Để dễ review, còn có thể biểu đạt trực tiếp:

~~~java
double fraction = (double) completed / total;
double percent = fraction * 100.0;
~~~

Với dữ liệu quan trọng, cần quyết định tiếp có chấp nhận độ chính xác của `double` hay dùng số thập phân chính xác như `BigDecimal`.

## 4. Chương trình Java 17+ chạy độc lập

Lưu file tên **`IntegerDivisionDemo.java`**:

~~~java
public class IntegerDivisionDemo {
    public static void main(String[] args) {
        int numerator = 7;
        int denominator = 2;

        double wrong = (double) (numerator / denominator);
        double correct = (double) numerator / denominator;

        System.out.println("wrong = " + wrong);
        System.out.println("correct = " + correct);
        System.out.println("right operand cast = "
                + numerator / (double) denominator);
        System.out.println("literal double = " + numerator / 2.0);

        System.out.println("-7 / 2 = " + (-numerator / denominator));
        System.out.println("-7 % 2 = " + (-numerator % denominator));
        System.out.println("floorDiv(-7, 2) = "
                + Math.floorDiv(-numerator, denominator));
        System.out.println("floorMod(-7, 2) = "
                + Math.floorMod(-numerator, denominator));

        int completed = 3;
        int total = 8;
        System.out.println("wrong percent = "
                + 100.0 * (completed / total));
        System.out.println("correct percent = "
                + 100.0 * completed / total);

        int zero = 0;
        try {
            int ignored = numerator / zero;
            System.out.println(ignored);
        } catch (ArithmeticException expected) {
            System.out.println(
                    "integer divide by zero: ArithmeticException");
        }

        System.out.println("double divide by zero = " + numerator / 0.0);
        System.out.println("double zero by zero = " + 0.0 / 0.0);
        System.out.println("MAX/MIN edge = "
                + (Integer.MIN_VALUE / -1));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac IntegerDivisionDemo.java
java IntegerDivisionDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
wrong = 3.0
correct = 3.5
right operand cast = 3.5
literal double = 3.5
-7 / 2 = -3
-7 % 2 = -1
floorDiv(-7, 2) = -4
floorMod(-7, 2) = 1
wrong percent = 0.0
correct percent = 37.5
integer divide by zero: ArithmeticException
double divide by zero = Infinity
double zero by zero = NaN
MAX/MIN edge = -2147483648
~~~

Ví dụ vừa chỉ ra thứ tự ép kiểu, vừa phân biệt phép chia nguyên âm và chia số thực cho 0. Kết quả `Integer.MIN_VALUE / -1` là một ngoại lệ overflow của phép chia nguyên có quy tắc riêng trong Java; phần 7 giải thích chi tiết.

## 5. Chia nguyên không làm tròn xuống âm vô cực

Khi hai toán hạng `int` hoặc `long`, phép chia dùng thương nguyên **bỏ phần lẻ theo hướng về 0** (truncate toward zero), không phải luôn dùng `Math.floor`.

~~~java
System.out.println(7 / 2);    // 3
System.out.println(-7 / 2);   // -3
System.out.println(7 / -2);   // -3
System.out.println(-7 / -2);  // 3
~~~

Theo số thực:

- `7 / 2 = 3.5`, bỏ phần lẻ được `3`.
- `-7 / 2 = -3.5`, bỏ phần lẻ về 0 được `-3`. Nếu làm tròn xuống âm vô cực thì là `-4`.

Java cung cấp `Math.floorDiv` khi yêu cầu phép chia **làm tròn xuống âm vô cực**:

~~~java
int truncated = -7 / 2;               // -3
int floored = Math.floorDiv(-7, 2);   // -4
~~~

Nếu cần phần dư tương ứng với thương floor, dùng `Math.floorMod`. Phân biệt:

~~~java
System.out.println(-7 % 2);            // -1
System.out.println(Math.floorMod(-7, 2)); // 1
~~~

`/` và `%` của `int` tuân theo quy tắc Java: ngoại trừ trường hợp overflow `MIN_VALUE / -1`, phép chia thương `q = a / b` và dư `r = a % b` thỏa `a == b * q + r` trong số học nguyên theo hợp đồng, với `|r| < |b|` và phần dư có cùng dấu với số bị chia hoặc bằng 0. Với `floorDiv`/`floorMod`, chọn cặp đó cùng nhau khi nghiệp vụ đòi phân hoạch theo hướng âm vô cực.

**Đừng dùng `(int) ((double) a / b)` để tự triển khai `floorDiv`.** Ép kiểu từ double về int cũng cắt phần lẻ về 0 (và có quy tắc riêng khi overflow/NaN). Nếu cần phép chia nguyên có floor semantics, gọi API phù hợp.

## 6. Lỗi thực tế trong trung bình, phần trăm và các công thức

### 6.1. Tính điểm trung bình

~~~java
int totalScore = 17;
int subjects = 4;

double wrongAverage = totalScore / subjects;          // 4.0
double correctAverage = (double) totalScore / subjects; // 4.25
~~~

Việc gán cho `double` không tự làm biểu thức `totalScore / subjects` trở thành phép chia double. Nếu chỉ muốn phần nguyên, có thể dùng `int quotient = totalScore / subjects` một cách có chủ đích và đặt tên biến thể hiện ý nghĩa.

### 6.2. Tính tỷ lệ hoàn thành

~~~java
int done = 3;
int planned = 8;

double wrongRatio = done / planned;            // 0.0
double correctRatio = (double) done / planned; // 0.375
double correctPercent = correctRatio * 100;   // 37.5
~~~

Sai lầm nghiêm trọng khi chỉ có một phần công việc hoàn tất: tỷ lệ đúng khác 0 nhưng phép chia nguyên cho 0, khiến hiển thị **0%**. Với điều kiện `planned == 0`, cần xác định quy tắc ứng dụng: trả trạng thái “chưa có kế hoạch”, báo lỗi, hoặc một giá trị quy ước được tài liệu hóa; không tự chia cho 0.

### 6.3. Trung bình cộng của hai int lớn

Ép đúng kiểu ở phép chia vẫn **chưa đủ** nếu phép cộng ở trước đã overflow:

~~~java
int a = Integer.MAX_VALUE;
int b = Integer.MAX_VALUE;

double wrong = (a + b) / 2.0;        // -1.0: a+b overflow trước
double right = ((double) a + b) / 2.0; // 2.147483647E9
~~~

Trong `wrong`, `a + b` là phép cộng `int`, overflow từ `4294967294` (toán học) xuống `-2` theo wraparound 32 bit; `-2 / 2.0` bằng `-1.0`. Biến đích double hoặc divisor 2.0 không sửa được phép cộng đã overflow.

Một cách khác là nâng lên `long` **trước khi cộng**:

~~~java
double exactSumThenDivide =
        ((long) a + b) / 2.0; // 2.147483647E9
~~~

Với hai giá trị kiểu `int`, tổng luôn nằm trong miền `long`, nên tổng không overflow long; phép chia `2.0` sau đó là double. Nhưng nếu ngay từ đầu hai toán hạng đã là long gần cực trị, ngay cả phép cộng long cũng có thể overflow; khi đó cần thiết kế phù hợp hơn, chẳng hạn kiểm soát overflow hoặc dùng `BigInteger`.

### 6.4. Phân trang và làm tròn số trang

Với `itemCount = 10` và `pageSize = 3`, số trang cần là **4**, không phải 3.

~~~java
int itemCount = 10;
int pageSize = 3;

int wrongPages = itemCount / pageSize; // 3
int correctPages = itemCount / pageSize
        + (itemCount % pageSize == 0 ? 0 : 1); // 4
~~~

Công thức trên đúng khi `itemCount >= 0`, `pageSize > 0`. Nó tránh việc chuyển sang double và tránh overflow của công thức thường gặp `(itemCount + pageSize - 1) / pageSize` khi `itemCount + pageSize - 1` vượt giới hạn int. Nếu chỉ cần số lượng trang nguyên, không có lý do bắt buộc phải dùng double; hãy chọn phép chia nguyên cùng chính sách làm tròn lên thích hợp.

Tương tự, khi tính “bao nhiêu bucket đủ chứa n phần tử”, điều quan trọng không chỉ là sửa sang double mà là xác định **kết quả mong muốn có phải số nguyên làm tròn lên hay không**.

## 7. Chia cho 0: int và double có hợp đồng khác nhau

Với số nguyên:

~~~java
int denominator = 0;
// int invalid = 1 / denominator; // ném ArithmeticException khi thực thi
~~~

Phép chia nguyên cho 0 gây `ArithmeticException`. Một biểu thức hằng `1 / 0` cũng không nên đưa vào code như một giá trị hợp lệ; đừng dựa vào việc IDE/compiler phát hiện hay không trong mọi ngữ cảnh để thay thế kiểm tra input.

Khi có toán hạng `double` hoặc `float`, Java tuân theo quy tắc dấu phẩy động IEEE 754 đối với chia cho 0:

~~~java
System.out.println(1.0 / 0.0);  // Infinity
System.out.println(-1.0 / 0.0); // -Infinity
System.out.println(0.0 / 0.0);  // NaN
~~~

Đây là các giá trị dấu phẩy động đặc biệt, **không giống lỗi chia nguyên**. Chính sách nghiệp vụ có thể yêu cầu từ chối divisor 0 trong cả hai trường hợp. Nếu biểu thức thành `Infinity` hoặc `NaN` rồi lan qua các bước sau, báo cáo/tính toán có thể bị hỏng mà không có exception.

**Luôn quyết định đầu vào 0 có hợp lệ không.** Ví dụ hàm tỷ lệ chỉ có ý nghĩa với tổng lớn hơn 0:

~~~java
static double completionRatio(int completed, int total) {
    if (total <= 0) {
        throw new IllegalArgumentException("total must be positive");
    }
    return (double) completed / total;
}
~~~

Đây là **phương thức minh họa**, đặt trong class, không phải chương trình có `main` độc lập. Nếu `completed` có thể âm hoặc lớn hơn `total` thì domain cũng cần quy định có từ chối hay không; việc ép kiểu không thay thế validation.

## 8. Một toán hạng double có thể gây sai số nhị phân

Chuyển toán hạng sang `double` sửa lỗi chia nguyên, nhưng **không biến mọi phân số thành số thập phân chính xác tuyệt đối**.

~~~java
double oneThird = (double) 1 / 3;
System.out.println(oneThird); // 0.3333333333333333
~~~

Số thực 1/3 có khai triển vô hạn, còn double chỉ có số bit độ chính xác hữu hạn nên kết quả là xấp xỉ. Tương tự, các số thập phân như 0.1 và 0.2 thường không có biểu diễn nhị phân chính xác.

Với số nguyên `int`, mọi giá trị riêng lẻ chuyển sang `double` đều biểu diễn chính xác vì double có 53 bit precision nhị phân và int chỉ có 32 bit. Nhưng kết quả **phép chia** giữa chúng vẫn có thể không biểu diễn chính xác bằng double.

Với `long`, không phải mọi số nguyên đều biểu diễn chính xác bằng double. Ví dụ:

~~~java
long large = 9_007_199_254_740_993L;  // 2^53 + 1
double converted = (double) large;
// converted là số double gần nhất, không giữ đúng từng đơn vị của large.
~~~

Nếu yêu cầu so sánh/đối soát số nguyên lớn theo giá trị chính xác, đừng chuyển sang double rồi coi mọi số là phân biệt. Dùng `long` khi kết quả thuộc phạm vi long và phép toán không overflow, hoặc `BigInteger` cho số nguyên vượt phạm vi. Với tỷ số yêu cầu độ chính xác thập phân hay quy tắc làm tròn xác định, cân nhắc `BigDecimal`.

### Khi cần BigDecimal

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal numerator = BigDecimal.valueOf(7);
BigDecimal denominator = BigDecimal.valueOf(2);
BigDecimal exact = numerator.divide(denominator);

System.out.println(exact); // 3.5

BigDecimal oneThird = BigDecimal.ONE.divide(
        BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
System.out.println(oneThird); // 0.3333
~~~

Đoạn này nằm trong một phương thức, giả định import đã khai báo. `BigDecimal.divide(divisor)` không truyền rounding mode chỉ thành công khi thương có biểu diễn thập phân hữu hạn; với `1/3` phải chỉ định scale/rounding hoặc MathContext phù hợp. Chọn số chữ số làm tròn dựa trên yêu cầu thực, không mặc định luôn 4.

**Tránh** khởi tạo tiền bằng `new BigDecimal(0.1)` vì giá trị đầu vào `double` đã gần đúng nhị phân. Đối với dữ liệu tiền tệ, ưu tiên parse chuỗi thập phân, đơn vị nguyên nhỏ nhất, hoặc phép tính BigDecimal được thiết kế ngay từ đầu.

## 9. Ép kiểu trước hoặc sau còn ảnh hưởng overflow và precision

Quy tắc tổng quát: **mọi phép toán xảy ra trước cast giữ ngữ nghĩa của kiểu đang áp dụng**, kể cả overflow, chia nguyên và sai số float.

| Biểu thức | Điều gì xảy ra trước | Hậu quả |
|---|---|---|
| `(double) (a / b)` | Chia int | Phần lẻ mất |
| `(double) a / b` | Cast a sang double | Có phần lẻ nếu thương cần |
| `(double) (a + b) / 2` | Cộng int | Có thể overflow trước cast |
| `((double) a + b) / 2` | Cast a sang double trước cộng | Tránh overflow int tại phép cộng đó, nhưng dùng số thực |
| `(long) (a * b)` | Nhân int | Có thể overflow trước cast |
| `(long) a * b` | Cast a sang long trước nhân | Nhân long, nhưng vẫn có giới hạn long |
| `(float) a / b` | Chia float | Có phần lẻ nhưng precision thấp hơn double |
| `a / (long) b` | Chia long | Vẫn cắt phần thập phân |

Ví dụ cụ thể lỗi nhân trước khi ép:

~~~java
int a = 50_000;
int b = 50_000;

long wrong = (long) (a * b);  // -1794967296 vì int overflow
long right = (long) a * b;    // 2500000000
~~~

Phần này mở rộng nguyên tắc từ phép chia sang phép nhân để tránh cùng dạng sai lầm: **cast đúng kiểu nhưng quá muộn**. Trong Java, nếu phép toán phải chính xác và có thể vượt kiểu hiện tại, hãy nâng kiểu hoặc dùng phép kiểm tra overflow **trước phép toán**.

## 10. Chọn cách tính theo ý nghĩa nghiệp vụ

Tùy mục tiêu, không phải lúc nào cũng nên chuyển sang double:

| Mục tiêu | Ví dụ giải pháp | Lý do |
|---|---|---|
| Lấy thương nguyên và bỏ phần lẻ hướng về 0 | `int q = a / b;` | Đúng khi chỉ cần số lần chia trọn |
| Tính tỷ lệ thực gần đúng | `double r = (double) a / b;` | Tránh chia nguyên |
| Tính tỷ lệ phần trăm gần đúng | `100.0 * a / b` | Chuyển sang double trước phép chia |
| Làm tròn chia nguyên xuống âm vô cực | `Math.floorDiv(a, b)` | Đúng với số âm |
| Tính số trang cho số lượng không âm | `a / b + (a % b != 0 ? 1 : 0)` | Đếm số nhóm nguyên và làm tròn lên |
| Tính tiền chính xác theo scale/mode | `BigDecimal.divide(..., scale, mode)` | Quy tắc thập phân rõ ràng |
| Tính số nguyên lớn chính xác | `BigInteger` và phép chia của nó | Tránh mất độ chính xác khi convert double |

Các biểu thức có `a / b` yêu cầu `b != 0`; công thức đếm trang giả định `a >= 0, b > 0`. Nếu nghiệp vụ xác định trước điều kiện này, kiểm tra input ở biên dễ hiểu và an toàn hơn xử lý ngoại lệ trong nhiều tầng.

## 11. Cách kiểm thử để bắt lỗi từ lần review đầu

Không chỉ kiểm thử trường hợp chia hết, vì `6 / 2` cho kết quả `3.0` dù viết sai `double result = 6 / 2`. Một bộ test nên có:

1. **Có phần lẻ:** `7/2 → 3.5`, `1/2 → 0.5`, `3/8 → 0.375` khi yêu cầu double.
2. **Chia hết:** `8/2 → 4.0`; bảo đảm sửa cast không làm sai trường hợp đơn giản.
3. **Số âm:** `-7/2 == -3` khi dùng chia int; với double là `-3.5`; với `Math.floorDiv` là -4.
4. **Bằng 0:** `0/5 == 0`; divisor 0 được từ chối theo policy.
5. **Biên int:** `Integer.MAX_VALUE`, `Integer.MIN_VALUE`, và cộng/nhân trước chia để phát hiện overflow sớm.
6. **Lỗi thứ tự toán tử:** `100.0 * (3/8) == 0.0` và `100.0 * 3/8 == 37.5`.
7. **Độ chính xác thực:** các tỷ số khó biểu diễn (1/3) cần so sánh với tolerance phù hợp hoặc với kỳ vọng thập phân theo domain; không dùng `==` bừa bãi giữa hai công thức floating được tính theo đường khác nhau.
8. **Điểm đến của kết quả:** nếu gọi hàm trả `double` mà phần thân trả `a/b` với hai int, chữ ký hàm vẫn không ngăn lỗi. Test phải kiểm chứng kết quả số học.

Ví dụ assertion thuần Java trong một phương thức:

~~~java
int a = 7;
int b = 2;
double actual = (double) a / b;

if (Double.compare(actual, 3.5) != 0) {
    throw new AssertionError("Expected 3.5");
}
~~~

Ở đây `3.5` biểu diễn chính xác trong nhị phân nên so sánh trực tiếp đáng tin cho ca đơn lẻ. Với `1/3` hoặc chuỗi phép toán số thực dài, cần chọn cách kiểm tra dung sai hoặc reference chính xác phù hợp yêu cầu.

### Tự hỏi khi review một biểu thức chia

- Hai toán hạng tại dấu `/` hiện có kiểu gì?
- Có cần **phần lẻ** hay chỉ cần **thương nguyên**?
- Cast nằm trước phép chia hay bao **toàn bộ kết quả đã tính**?
- Các phép cộng/nhân trước đó có thể overflow?
- Divisor có thể bằng 0 không?
- Nếu dùng double, độ chính xác xấp xỉ có đủ?
- Với số âm, mong muốn truncate hay floor?
- Nếu kết quả cần biểu diễn tiền tệ, có quy tắc scale và rounding chưa?

## 12. Các lỗi phổ biến và cách sửa nhanh

**Sai:** `double avg = sum / count;` khi `sum`, `count` đều int và cần kết quả có phần lẻ.

**Đúng:** `double avg = (double) sum / count;` khi count khác 0.

**Sai:** `double avg = (double) (sum / count);` vì cast quá muộn.

**Đúng:** `double avg = sum / (double) count;` hoặc cast tử số trước.

**Sai:** `double pct = (done / total) * 100.0;` vì ngoặc ép chia nguyên trước.

**Đúng:** `double pct = ((double) done / total) * 100.0;` với total khác 0.

**Sai:** `double avg = (a + b) / 2.0;` nếu `a+b` có thể overflow int.

**Đúng:** `double avg = ((double) a + b) / 2.0;` khi phù hợp semantics double, hoặc nâng lên long trước cộng nếu phạm vi cho phép.

**Sai:** `double r = (long) a / b;` và cho rằng phép chia sẽ có phần lẻ.

**Đúng:** `double r = (double) a / b;` vì long vẫn là số nguyên.

**Sai:** dùng `Math.floorDiv` chỉ để “lấy số có dấu thập phân”.

**Đúng:** `Math.floorDiv` dành cho **thương nguyên làm tròn xuống**, còn để giữ phần lẻ phải có kiểu số thực tại phép chia.

## 13. Bài tập

**Bài 1.** Dự đoán:

~~~java
System.out.println(5 / 2);
System.out.println((double) (5 / 2));
System.out.println((double) 5 / 2);
System.out.println(5 / 2.0);
~~~

**Bài 2.** Với `a = -5` và `b = 2`, so sánh `a / b`, `(double) a / b`, `Math.floorDiv(a,b)`, `a % b` và `Math.floorMod(a,b)`.

**Bài 3.** Sửa đoạn code:

~~~java
static double percentage(int success, int total) {
    return success / total * 100;
}
~~~

Yêu cầu trả tỷ lệ `37.5` khi success=3 và total=8; từ chối total=0.

**Bài 4.** Vì sao `double x = (Integer.MAX_VALUE + Integer.MAX_VALUE) / 2.0;` không ra giá trị trung bình mong muốn? Hãy sửa mà không cộng hai int trước.

**Bài 5.** Nếu số lượng trang cần dùng phép làm tròn lên nguyên cho `items = 10, pageSize = 3`, tại sao chỉ ép sang double chưa hoàn thành yêu cầu? Hãy chọn cách tính số trang trả int.

**Đáp án tham khảo:**

1. `2`, `2.0`, `2.5`, `2.5`.
2. `-2`, `-2.5`, `-3`, `-1`, `1`.
3. Kiểm tra `total != 0` (hoặc `total > 0` theo domain), sau đó `return (double) success / total * 100.0;`.
4. Phép cộng int overflow trước khi chia. Dùng `((double) Integer.MAX_VALUE + Integer.MAX_VALUE) / 2.0` hoặc cộng long trước.
5. Số trang phải nguyên làm tròn lên; với items không âm và pageSize dương, dùng `items / pageSize + (items % pageSize == 0 ? 0 : 1)` cho kết quả 4.

## 14. Tài liệu chuẩn Java SE 17

- Java Language Specification, ``15.17.2 Division Operator /`: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17.2
- Java Language Specification, ``5.6 Numeric Contexts` (binary numeric promotion): https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6
- Java Language Specification, ``5.1.2 Widening Primitive Conversion`: https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.2
- Java SE 17, `Math.floorDiv`/`floorMod`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html
- Java SE 17, `BigDecimal`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html

**Ghi nhớ:** `double result = a / b;` với hai `int` là **chia nguyên rồi mới chuyển sang double**. Muốn giữ phần lẻ, hãy đặt `(double)` trước **ít nhất một toán hạng của phép chia** và đảm bảo không có phép toán int trước đó đã làm mất thông tin hoặc overflow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-003">Dùng Float.MIN_VALUE hoặc Double.MIN_VALUE như số âm nhỏ nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `Float.MIN_VALUE` và `Double.MIN_VALUE` không phải số âm nhỏ nhất?

## 1. Sai lầm phổ biến: nhầm “MIN” với số âm lớn nhất về độ lớn

Khi lập trình Java, người học thường quen với `Integer.MIN_VALUE` là số nguyên âm nhỏ nhất có thể biểu diễn. Từ đó dễ suy luận nhầm rằng `Float.MIN_VALUE` hay `Double.MIN_VALUE` cũng mang ý nghĩa tương tự.

**Thực tế trong Java 17+:**

- `Float.MIN_VALUE` là **số `float` dương nhỏ nhất khác 0**, bằng khoảng `1.4E-45`.
- `Double.MIN_VALUE` là **số `double` dương nhỏ nhất khác 0**, bằng khoảng `4.9E-324`.
- Số `float` hữu hạn âm nhỏ nhất là **`-Float.MAX_VALUE`**, khoảng `-3.4028235E38`.
- Số `double` hữu hạn âm nhỏ nhất là **`-Double.MAX_VALUE`**, khoảng `-1.7976931348623157E308`.
- Nếu tính cả giá trị vô cực đặc biệt, `Float.NEGATIVE_INFINITY` và `Double.NEGATIVE_INFINITY` nhỏ hơn tất cả các số hữu hạn cùng kiểu.

Một bug điển hình:

```java
double max = Double.MIN_VALUE;  // SAI nếu dữ liệu có thể toàn số âm.

for (double value : new double[]{-8.0, -3.0, -11.0}) {
    if (value > max) {
        max = value;
    }
}
System.out.println(max); // 4.9E-324 — không có trong dữ liệu!
```

Vì `Double.MIN_VALUE > 0`, cả ba số âm đều nhỏ hơn giá trị khởi tạo nên vòng lặp không bao giờ cập nhật `max`.

**Mục tiêu của bài:** nắm được chính xác ý nghĩa các hằng số `MIN_VALUE`, `MIN_NORMAL`, `MAX_VALUE`, vô cực và `NaN`; chọn cách khởi tạo min/max đúng; xây dựng cách tổng hợp an toàn trên tập rỗng và các giá trị đặc biệt; tránh dùng `MIN_VALUE` làm ngưỡng sai số.

## 2. Bảng đối chiếu với kiểu số nguyên

| Hằng số | Giá trị xấp xỉ | Ý nghĩa |
| --- | --- | --- |
| `Integer.MIN_VALUE` | `-2147483648` | Số `int` nhỏ nhất |
| `Integer.MAX_VALUE` | `2147483647` | Số `int` lớn nhất |
| `Long.MIN_VALUE` | `-9223372036854775808` | Số `long` nhỏ nhất |
| `Float.MIN_VALUE` | `1.4E-45` | Số `float` **dương** nhỏ nhất khác 0 |
| `Float.MIN_NORMAL` | `1.1754944E-38` | Số `float` dương **normal** nhỏ nhất |
| `Float.MAX_VALUE` | `3.4028235E38` | Số `float` dương hữu hạn lớn nhất |
| `-Float.MAX_VALUE` | `-3.4028235E38` | Số `float` âm hữu hạn nhỏ nhất |
| `Double.MIN_VALUE` | `4.9E-324` | Số `double` **dương** nhỏ nhất khác 0 |
| `Double.MIN_NORMAL` | `2.2250738585072014E-308` | Số `double` dương **normal** nhỏ nhất |
| `Double.MAX_VALUE` | `1.7976931348623157E308` | Số `double` dương hữu hạn lớn nhất |
| `-Double.MAX_VALUE` | `-1.7976931348623157E308` | Số `double` âm hữu hạn nhỏ nhất |
| `Float.NEGATIVE_INFINITY`, `Double.NEGATIVE_INFINITY` | `-Infinity` | Giá trị đặc biệt nhỏ hơn số hữu hạn |
| `Float.POSITIVE_INFINITY`, `Double.POSITIVE_INFINITY` | `Infinity` | Giá trị đặc biệt lớn hơn số hữu hạn |

Giá trị trên dùng định dạng mà Java thường in ra bằng `Float.toString`/`Double.toString`; các hằng số lưu trữ theo nhị phân IEEE 754 nên không nhất thiết có đúng biểu diễn thập phân ngắn như chuỗi in ra.

**Điểm cần nhớ:** đối với kiểu nguyên, `MIN_VALUE` là cận dưới âm của miền giá trị. Đối với `float` và `double`, `MIN_VALUE` lại là **độ lớn dương nhỏ nhất khác 0** của kiểu dấu phẩy động, bao gồm cả vùng subnormal. Cách đặt tên là đặc điểm API đã được chuẩn hóa; không suy diễn ý nghĩa từ cùng tên hằng số của kiểu số nguyên.

## 3. Tại sao số dương nhỏ nhất khác 0 lại nhỏ đến như vậy?

`float` và `double` là kiểu dấu phẩy động nhị phân theo IEEE 754, không lưu số như một đoạn liên tiếp với khoảng cách đều. Biểu diễn có dấu, phần mũ và phần trị, nên độ phân giải phụ thuộc độ lớn số.

Các giá trị hữu hạn thông thường (**normal**) có phần mũ và các bit hữu hiệu theo quy tắc normal. Khi giá trị tiến rất gần 0, Java còn có các giá trị **subnormal** (*denormalized*) để biểu diễn một số độ lớn nhỏ hơn cận normal; chúng có ít chữ số/bit hữu hiệu hơn.

- `Float.MIN_NORMAL` là cận dương nhỏ nhất còn **normal**.
- `Float.MIN_VALUE` nhỏ hơn `Float.MIN_NORMAL` rất nhiều và là số dương **subnormal** nhỏ nhất khác 0.
- `Double.MIN_NORMAL` và `Double.MIN_VALUE` có quan hệ tương tự.

Ví dụ trên Java:

```java
System.out.println(Float.MIN_VALUE);       // 1.4E-45
System.out.println(Float.MIN_NORMAL);      // 1.1754944E-38
System.out.println(Double.MIN_VALUE);      // 4.9E-324
System.out.println(Double.MIN_NORMAL);     // 2.2250738585072014E-308
```

Đoạn này là mã **nhúng** trong một phương thức. `Float.MIN_NORMAL` không phải số âm nhỏ nhất; chữ `NORMAL` chỉ phân biệt số normal với subnormal. `MIN_VALUE` **không phải** số dương nhỏ nhất *theo ý nghĩa số thực toán học* (trên trục số thực không tồn tại số dương nhỏ nhất); nó chỉ là giá trị nhỏ nhất khác 0 biểu diễn được bằng kiểu `float`/`double` trong Java.

### 3.1. Trục giá trị quan trọng

Sắp xếp theo **thứ tự số học**:

```text
-Infinity
   <
-Double.MAX_VALUE
   <
... các số hữu hạn âm ...
   <
-Double.MIN_VALUE
   <
-0.0 / +0.0 (cùng giá trị dưới phép so sánh ==)
   <
Double.MIN_VALUE
   <
... các số hữu hạn dương ...
   <
Double.MAX_VALUE
   <
+Infinity
```

Sơ đồ là một cách mô tả trật tự số học; với `Double.compare`, `-0.0` được xếp trước `+0.0`. `NaN` không nằm trên trục số thực này. Đối với phép `<`, `>`, `==` với `NaN`, hành vi không phải trật tự tuyến tính thông thường (xem mục 6).

`Double.MIN_VALUE` nằm **ngay phía dương của 0** trong các giá trị biểu diễn được, chứ không nằm phía âm sâu nhất.

### 3.2. `Math.nextUp` và `Math.nextDown` làm rõ giới hạn

```java
System.out.println(Math.nextUp(0.0));   // 4.9E-324
System.out.println(Math.nextDown(0.0)); // -4.9E-324
```

`Math.nextUp(0.0)` trả giá trị `double` biểu diễn được kế tiếp lớn hơn 0, chính là `Double.MIN_VALUE`. `Math.nextDown(0.0)` trả giá trị kế tiếp nhỏ hơn 0, tức `-Double.MIN_VALUE`. Giá trị **`-Double.MIN_VALUE` là số âm gần 0 nhất nhưng vẫn khác 0**, không phải số âm nhỏ nhất.

Tương tự, `Math.nextDown(-Double.MAX_VALUE)` chuyển sang `Double.NEGATIVE_INFINITY`: không còn số `double` **hữu hạn** nào nhỏ hơn `-Double.MAX_VALUE`.

## 4. Lỗi khi khởi tạo cực đại bằng `MIN_VALUE`

### 4.1. Bug rõ nhất: toàn bộ dữ liệu âm

```java
double[] temperatures = {-8.0, -3.0, -11.0};
double maximum = Double.MIN_VALUE; // Sai chính sách khởi tạo.

for (double temperature : temperatures) {
    if (temperature > maximum) {
        maximum = temperature;
    }
}
System.out.println(maximum); // 4.9E-324 — sai!
```

Giá trị đúng phải là **`-3.0`**. Nếu code dùng `Float.MIN_VALUE`, hiện tượng tương tự xảy ra cho `float[]` toàn số âm.

### 4.2. Đối xứng: dùng `MIN_VALUE` làm giá trị khởi tạo của cực tiểu cũng sai

```java
double[] positives = {8.0, 3.0, 11.0};
double minimum = Double.MIN_VALUE; // Sai: nhỏ hơn mọi phần tử.

for (double value : positives) {
    if (value < minimum) {
        minimum = value;
    }
}
System.out.println(minimum); // 4.9E-324 — sai, đúng là 3.0.
```

Tên `MIN_VALUE` nghe như “phần tử nhỏ nhất” nhưng **không có nghĩa** là hằng khởi tạo phù hợp cho thuật toán tìm min. Nếu thuật toán cần tìm min/max của dữ liệu, phải bảo đảm kết quả thuộc dữ liệu và giải quyết đúng trường hợp rỗng.

### 4.3. Chỉ thay bằng `-Double.MAX_VALUE` có đủ chưa?

Đối với **dữ liệu được bảo đảm hữu hạn và không rỗng**, khởi tạo:

```java
double maximum = -Double.MAX_VALUE;
```

có thể hợp lệ để tìm cực đại. Tuy nhiên, nếu dữ liệu chứa `Double.NEGATIVE_INFINITY` và không có số hữu hạn nào, `-Double.MAX_VALUE` lớn hơn mọi phần tử: bạn vẫn nhận kết quả sai, vì sentinel không thuộc dữ liệu. Nếu mảng rỗng, sentinel còn bị trả như một cực trị dù không có phần tử nào.

Tương tự:

```java
double minimum = Double.MAX_VALUE;
```

sai nếu mọi phần tử là `Double.POSITIVE_INFINITY`, hoặc mảng rỗng mà kết quả được coi là min thực.

`Double.NEGATIVE_INFINITY` có thể là sentinel khởi tạo max, và `Double.POSITIVE_INFINITY` là sentinel khởi tạo min trong một số thuật toán, nhưng **cần cờ có dữ liệu** để phân biệt tập rỗng với đầu vào thực sự có toàn vô cực. Nếu miền dữ liệu có `NaN`, phải có thêm chính sách xử lý riêng.

Giải pháp thường dễ bảo trì hơn: **lấy giá trị đầu tiên khi dữ liệu tồn tại**, hoặc dùng stream trả `OptionalDouble` (mục 5).

## 5. Chọn cách tìm `min`/`max` không dựa vào sentinel sai

### 5.1. Dùng `DoubleStream.min()` và `max()`

```java
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

OptionalDouble min = DoubleStream.of(-8.0, -3.0, -11.0).min();
OptionalDouble max = DoubleStream.of(-8.0, -3.0, -11.0).max();

System.out.println(min.orElseThrow()); // -11.0
System.out.println(max.orElseThrow()); // -3.0
```

Đây là mã **nhúng**. Các phương thức `min()`/`max()` là terminal operation và trả `OptionalDouble` vì nguồn **có thể rỗng**. Không dùng lại cùng một stream sau một terminal operation: tạo stream mới từ mảng/list hoặc chọn `summaryStatistics()` nếu cần nhiều chỉ số trong một lượt.

```java
OptionalDouble none = DoubleStream.empty().max();
System.out.println(none.isEmpty()); // true
```

Trường hợp nguồn rỗng được mô tả rõ bằng Optional thay vì giả một con số làm kết quả. Nếu nghiệp vụ yêu cầu ít nhất một giá trị, có thể dùng `orElseThrow(() -> new IllegalArgumentException("Dữ liệu rỗng"))`. Nếu rỗng là hợp lệ, trả Optional cho caller. `orElse(0.0)` chỉ phù hợp **khi nghiệp vụ thực sự định nghĩa giá trị mặc định bằng 0**, vì 0 cũng là một giá trị dữ liệu hợp lệ.

### 5.2. Dùng giá trị đầu tiên và cờ hiện diện

Khi bạn cần một vòng lặp chuyên biệt (ví dụ vừa kiểm tra dữ liệu vừa tìm max), nên dùng cờ `found`:

```java
boolean found = false;
double maximum = 0.0; // Giá trị ban đầu không được sử dụng nếu chưa found.

for (double value : values) {
    if (!found || Double.compare(value, maximum) > 0) {
        maximum = value;
        found = true;
    }
}

OptionalDouble result =
        found ? OptionalDouble.of(maximum) : OptionalDouble.empty();
```

Đây là mã **nhúng** với `double[] values` và `import java.util.OptionalDouble;`. Vì `!found` luôn thắng cho phần tử đầu tiên, `0.0` chỉ là chỗ đặt bộ nhớ, không bị hiểu lầm là một kết quả dữ liệu.

**Nhưng cần lưu ý:** `Double.compare` định nghĩa trật tự cho cả `NaN` (xếp `NaN` lớn hơn mọi giá trị số) và hai số 0 có dấu. Vì vậy đoạn lặp trên không tự động có cùng chính sách `NaN` như `DoubleStream.min/max`. Khi muốn từ chối `NaN`, hãy kiểm tra trước khi so sánh; khi muốn `NaN` lan truyền, triển khai rõ chính sách đó. Đừng thay thế hai cách mà không đối chiếu hợp đồng.

### 5.3. Khi chỉ nhận dữ liệu hữu hạn

Nếu miền đầu vào không được có `NaN` hoặc `±Infinity`, hãy xác thực:

```java
if (!Double.isFinite(value)) {
    throw new IllegalArgumentException("Chỉ nhận số hữu hạn");
}
```

Sau kiểm tra, hàm có thể tìm cực trị bằng `Double.compare` hoặc trực tiếp các toán tử `<`/`>`, tùy có cần phân biệt `-0.0` và `+0.0` hay không. Không mặc định loại bỏ giá trị không hữu hạn một cách im lặng nếu chúng báo lỗi upstream (ví dụ chia cho 0 hoặc dữ liệu cảm biến bị hỏng).

## 6. Ba trường hợp đặc biệt của `double`: vô cực, `NaN` và số 0 có dấu

### 6.1. Vô cực và cận hữu hạn

`Double.MAX_VALUE` là số **hữu hạn** dương lớn nhất; nó không phải `Double.POSITIVE_INFINITY`. `-Double.MAX_VALUE` là số hữu hạn âm nhỏ nhất; nó không phải `Double.NEGATIVE_INFINITY`.

```java
System.out.println(Double.NEGATIVE_INFINITY < -Double.MAX_VALUE); // true
System.out.println(Double.POSITIVE_INFINITY > Double.MAX_VALUE);  // true
```

Nếu dữ liệu có thể chứa vô cực, không được trả về `-Double.MAX_VALUE` như max của một nguồn chỉ chứa `-Infinity`. Tương tự, min của nguồn chỉ chứa `+Infinity` phải là `+Infinity` nếu hợp đồng chấp nhận vô cực. `Math.min` và `DoubleStream.min` không tự từ chối vô cực.

### 6.2. `NaN` không phải số thực và không so sánh theo `<`/`>`

`Double.NaN` (Not a Number) là giá trị đặc biệt:

```java
double invalid = Double.NaN;

System.out.println(invalid < 0.0);          // false
System.out.println(invalid > 0.0);          // false
System.out.println(invalid == Double.NaN);  // false
System.out.println(Double.isNaN(invalid));  // true
```

Với các toán tử so sánh primitive, ngay cả `NaN == NaN` cũng là `false`. Một vòng lặp dùng `if (x > current)` có thể **bỏ qua** `NaN`; điều này nguy hiểm nếu `NaN` biểu thị phép đo không hợp lệ và phải gây lỗi.

Theo Java 17, `DoubleStream.min/max` **trả `NaN` nếu trong nguồn có bất kỳ giá trị `NaN` nào**, kể cả khi còn giá trị hữu hạn. `Math.min`/`Math.max` trên `double` cũng trả `NaN` nếu một toán hạng là `NaN`.

```java
double min = java.util.stream.DoubleStream
        .of(5.0, Double.NaN, -2.0)
        .min()
        .orElseThrow();

System.out.println(min); // NaN
```

`OptionalDouble` ở đây **có giá trị**, chỉ là giá trị đặc biệt `NaN`; `isPresent()` không có nghĩa là “dữ liệu hợp lệ”.

Nếu nghiệp vụ yêu cầu bỏ qua `NaN`, thực hiện `.filter(v -> !Double.isNaN(v))` và quyết định trường hợp sau filter rỗng; nếu nghiệp vụ yêu cầu báo lỗi, ném ngoại lệ ngay khi phát hiện. Không tự chọn một chính sách chỉ vì nó giúp vòng lặp chạy nhanh hơn.

### 6.3. `-0.0` và `+0.0` có biểu diễn khác nhau

```java
System.out.println(-0.0 == +0.0);         // true
System.out.println(Double.compare(-0.0, +0.0) < 0); // true

System.out.println(Math.min(-0.0, +0.0)); // -0.0
System.out.println(Math.max(-0.0, +0.0)); // 0.0
```

Hai số 0 cùng bằng nhau theo `==`, nhưng `Double.compare` sắp xếp `-0.0` nhỏ hơn `+0.0`. `Math.min`/`Math.max` và `DoubleStream.min/max` có quy tắc cho số 0 có dấu. Nếu muốn thay các API này bằng vòng lặp, cần kiểm thử trường hợp `-0.0`/`+0.0` để không vô tình đổi nghĩa khi thứ tự phần tử thay đổi.

Các vấn đề trên không xảy ra với `int` theo cách tương tự: `int` không có `NaN`, vô cực hay số 0 mang dấu âm tách biệt.

## 7. `Float.MIN_VALUE` và `Double.MIN_VALUE` cũng không phải epsilon để so sánh gần bằng

Một nhầm lẫn liên quan là dùng:

```java
boolean approximatelyEqual =
        Math.abs(a - b) < Double.MIN_VALUE; // Gần như luôn quá nghiêm ngặt.
```

Đây là mã minh họa giả định hai biến `double a`, `double b` đã tồn tại. `Double.MIN_VALUE` mô tả **khoảng cách từ 0 đến số dương nhỏ nhất khác 0**, không phải sai số làm tròn tuyệt đối phù hợp cho mọi phép toán và mọi độ lớn.

Tại quanh `1.0`, khoảng cách tới số `double` biểu diễn được kế tiếp phía trên là:

```java
System.out.println(Math.ulp(1.0)); // 2.220446049250313E-16
System.out.println(Math.ulp(1.0f)); // 1.1920929E-7
```

Nó lớn hơn `Double.MIN_VALUE` và `Float.MIN_VALUE` rất nhiều. `Math.ulp(x)` mô tả độ lớn của một đơn vị ở chữ số cuối gần `x`, khác với một “epsilon nghiệp vụ” phổ quát.

### 7.1. Chính sách so sánh gần bằng phải dựa trên bài toán

Một cách **minh họa** cho số hữu hạn trong trường hợp ứng dụng đã xác định ngưỡng:

```java
static boolean approximatelyEqualFinite(
        double a, double b, double absTolerance, double relTolerance) {

    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        throw new IllegalArgumentException("Chỉ so sánh số hữu hạn");
    }
    if (!Double.isFinite(absTolerance) || absTolerance < 0.0
            || !Double.isFinite(relTolerance) || relTolerance < 0.0) {
        throw new IllegalArgumentException("Tolerance không hợp lệ");
    }
    if (a == b) { // Gồm +0.0 == -0.0 theo chính sách ví dụ.
        return true;
    }

    double difference = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return difference <= Math.max(absTolerance, relTolerance * scale);
}
```

Đây là phương thức **nhúng** áp dụng cho một miền hữu hạn đã định. Với giá trị cực lớn, phép trừ hoặc nhân ngưỡng có thể tràn thành vô cực, nên một bộ so sánh đòi ràng buộc số học chặt phải xử lý miền và phép tính trung gian kỹ hơn. Công thức chỉ là điểm khởi đầu khi nghiệp vụ cho phép sai số tuyệt đối và tương đối đã được xác định, **không phải phép chữa tổng quát** cho mọi kiểm thử số học.

Nếu cần so sánh chính xác tiền tệ hoặc số nguyên, dùng kiểu biểu diễn thích hợp (`BigDecimal`/integer minor units) và quy tắc so sánh tương ứng; không dùng epsilon để che việc đổi kiểu số gây mất dữ liệu.

## 8. Chương trình Java 17+ hoàn chỉnh và kết quả chạy

Lưu dưới tên **`FloatingMinimumDemo.java`**:

```java
import java.util.OptionalDouble;

public class FloatingMinimumDemo {

    // Hợp đồng: chấp nhận cả vô cực, nhưng từ chối NaN;
    // trả Optional.empty() khi không có phần tử.
    static OptionalDouble maximumRejectNaN(double[] values) {
        boolean found = false;
        double maximum = 0.0;

        for (double value : values) {
            if (Double.isNaN(value)) {
                throw new IllegalArgumentException("NaN is not allowed");
            }

            if (!found || Double.compare(value, maximum) > 0) {
                maximum = value;
                found = true;
            }
        }

        return found
                ? OptionalDouble.of(maximum)
                : OptionalDouble.empty();
    }

    public static void main(String[] args) {
        System.out.println("Float.MIN_VALUE = " + Float.MIN_VALUE);
        System.out.println("-Float.MAX_VALUE = " + (-Float.MAX_VALUE));
        System.out.println("Double.MIN_VALUE = " + Double.MIN_VALUE);
        System.out.println("-Double.MAX_VALUE = " + (-Double.MAX_VALUE));

        double[] values = {-8.0, -3.0, -11.0};
        double wrongMax = Double.MIN_VALUE;

        for (double value : values) {
            if (value > wrongMax) {
                wrongMax = value;
            }
        }

        System.out.println("wrong max = " + wrongMax);
        System.out.println("correct max = "
                + maximumRejectNaN(values).orElseThrow());
        System.out.println("empty = "
                + maximumRejectNaN(new double[0]).isEmpty());
        System.out.println("only -Infinity = "
                + maximumRejectNaN(
                        new double[]{Double.NEGATIVE_INFINITY})
                        .orElseThrow());
        System.out.println("signed zero max = "
                + maximumRejectNaN(new double[]{-0.0, 0.0})
                        .orElseThrow());
        System.out.println("nextUp zero = " + Math.nextUp(0.0));

        try {
            maximumRejectNaN(new double[]{1.0, Double.NaN});
        } catch (IllegalArgumentException e) {
            System.out.println("NaN policy = rejected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac FloatingMinimumDemo.java
java FloatingMinimumDemo
```

Stdout chuẩn trên Java 17+:

```text
Float.MIN_VALUE = 1.4E-45
-Float.MAX_VALUE = -3.4028235E38
Double.MIN_VALUE = 4.9E-324
-Double.MAX_VALUE = -1.7976931348623157E308
wrong max = 4.9E-324
correct max = -3.0
empty = true
only -Infinity = -Infinity
signed zero max = 0.0
nextUp zero = 4.9E-324
NaN policy = rejected
```

### Diễn giải kết quả

1. Bốn dòng đầu phân biệt rõ **dương nhỏ nhất khác 0** với **âm hữu hạn nhỏ nhất** cho `float` và `double`.
2. `wrong max` bộc lộ bug phổ biến: sentinel `Double.MIN_VALUE` lớn hơn mọi phần tử âm, nên kết quả sai.
3. `maximumRejectNaN` chọn phần tử đầu tiên trước khi so sánh những phần tử còn lại, trả `-3.0` cho dữ liệu mẫu.
4. Mảng rỗng trả `OptionalDouble.empty()` thay vì một sentinel giả.
5. Dữ liệu chỉ có `-Infinity` vẫn được trả đúng vì hợp đồng cho phép vô cực.
6. `Double.compare` giữ thứ tự chuẩn giữa `-0.0` và `+0.0`, nên `max` chọn `+0.0`.
7. `Math.nextUp(0.0)` chứng minh `Double.MIN_VALUE` nằm ngay phía dương của 0.
8. `NaN` bị từ chối theo chính sách của **hàm minh họa**. Chính sách này khác `DoubleStream.max()` vốn trả `NaN` khi nguồn chứa NaN; cần chọn rõ một trong các chính sách khi thiết kế API.

Không phải lúc nào tự viết vòng lặp cũng tốt hơn `DoubleStream.max()`: ví dụ ở đây nhằm minh họa điều kiện đầu vào và quy tắc chọn kết quả. Đối với nghiệp vụ thông thường, ưu tiên API chuẩn nếu hợp đồng `NaN`/infinity và kết quả rỗng phù hợp.

## 9. Chọn cách khởi tạo phù hợp theo từng hợp đồng

| Yêu cầu dữ liệu và kết quả | Cách triển khai gợi ý | Lưu ý |
| --- | --- | --- |
| Tìm `max` của mảng không rỗng, **chỉ số hữu hạn** | Bắt đầu bằng phần tử đầu, hoặc `-Double.MAX_VALUE` khi miền đã chứng minh | Chặn mảng rỗng; xác thực finite |
| Tìm `min` của mảng không rỗng, **chỉ số hữu hạn** | Bắt đầu bằng phần tử đầu, hoặc `Double.MAX_VALUE` khi miền đã chứng minh | Không dùng `Double.MIN_VALUE` |
| Có thể rỗng và có thể chứa ±infinity | `DoubleStream.min/max()` trả `OptionalDouble` | Dữ liệu NaN được lan truyền theo API |
| Có thể rỗng; `NaN` là lỗi; ±infinity hợp lệ | Cờ `found` + `Double.compare` + reject NaN | Như chương trình ví dụ |
| Nguồn chỉ chấp nhận số hữu hạn | Xác thực `Double.isFinite` rồi tính | Không âm thầm loại bỏ phần tử lỗi |
| Cần giá trị hữu hạn nhỏ nhất theo miền kiểu | `-Double.MAX_VALUE` / `-Float.MAX_VALUE` | Không phải kết quả `min` của một mảng tùy ý |
| Cần giá trị đặc biệt thấp hơn mọi số hữu hạn | `Double.NEGATIVE_INFINITY` / `Float.NEGATIVE_INFINITY` | Phân biệt với giá trị thực trong nguồn |
| Cần số dương biểu diễn được kế tiếp 0 | `Double.MIN_VALUE` / `Float.MIN_VALUE` | Đây mới là trường hợp dùng đúng `MIN_VALUE` |

### Khi nào sentinel vẫn hợp lý?

Sentinel có thể là một **giá trị kỹ thuật** trong thuật toán khi bạn biết chính xác miền đầu vào và có cách đánh dấu tập rỗng. Ví dụ phép reduction cần giá trị identity `-Infinity` cho cực đại có thể hợp lý trên số `double` nếu hợp đồng xử lý `NaN` được công bố. Nhưng identity không phải “phần tử lớn nhất của một dãy rỗng”, và không được nhầm trạng thái rỗng với dữ liệu thực.

Nếu API trả dữ liệu cho tầng gọi, `OptionalDouble` thường truyền đạt trạng thái “không có cực trị” rõ ràng hơn việc phát ra `-Infinity` không có ngữ cảnh.

## 10. Những lỗi đi kèm cần nhận diện

**Lỗi 1: `Float.MIN_VALUE` là giới hạn âm.** Sai vì hằng số dương. Số âm hữu hạn thấp nhất là `-Float.MAX_VALUE`.

**Lỗi 2: `-Float.MIN_VALUE` là số âm thấp nhất.** Sai vì đây là số âm khác 0 gần 0 nhất; nó có độ lớn rất nhỏ. Không nhầm **âm nhỏ nhất theo thứ tự số học** với **số âm có độ lớn tuyệt đối nhỏ nhất**.

**Lỗi 3: `Double.MIN_NORMAL` là số dương biểu diễn được nhỏ nhất.** Sai: còn các số subnormal dương nhỏ hơn, xuống đến `Double.MIN_VALUE`.

**Lỗi 4: `Double.MAX_VALUE` có nghĩa là vô cực.** Sai: `Double.MAX_VALUE` hữu hạn. Phép toán vượt miền `double` có thể cho `Infinity`, nhưng hằng này không phải vô cực.

**Lỗi 5: Trả sentinel ngay khi dữ liệu rỗng.** Sai khi hợp đồng yêu cầu “không có dữ liệu”; phải biểu diễn vắng mặt hoặc ném lỗi.

**Lỗi 6: Dùng `Double.MIN_VALUE` cho ngưỡng kiểm thử xấp xỉ.** Quá nhỏ so với khoảng cách biểu diễn ở hầu hết các miền; ngưỡng phải dựa trên sai số cho phép của bài toán.

**Lỗi 7: Dùng `Math.abs(Double.MIN_VALUE)` để nhận số âm.** `Math.abs` giữ giá trị không âm, vì thế không tạo cận âm. Muốn đảo dấu cần toán tử `-`, nhưng `-Double.MIN_VALUE` vẫn gần 0 chứ không phải thấp nhất.

**Lỗi 8: So sánh qua `Double.compare` rồi bỏ quên `NaN`.** `Double.compare` cung cấp một thứ tự xác định, nhưng nó coi NaN lớn hơn mọi giá trị số; đó có thể không phải quy tắc dữ liệu cảm biến/nghiệp vụ.

**Lỗi 9: Bỏ qua `-0.0` khi thay `Math.min/max` bằng `if (a < b)` hoặc `if (a > b)`.** Các toán tử không phân biệt `-0.0` với `+0.0`; đầu ra có thể đổi dấu số 0 theo thứ tự dữ liệu.

**Lỗi 10: Cắt số `double` sang `float` khi tạo cận.** `-Double.MAX_VALUE` không nằm trong phạm vi hữu hạn của `float`; ép kiểu sang `float` sẽ tạo `-Infinity`, khác với `-Float.MAX_VALUE`.

## 11. Kiểm thử đầu vào biên để tránh tái xuất hiện bug

Khi viết hoặc review hàm tìm min/max, nên kiểm tra tối thiểu:

1. **Toàn số âm:** `{-8.0, -3.0, -11.0}` phải cho max `-3.0`, min `-11.0`.
2. **Toàn số dương:** `{8.0, 3.0, 11.0}` phải cho min `3.0`, max `11.0`.
3. **Chỉ số 0:** `{0.0}` phải trả 0, không trả `Double.MIN_VALUE`.
4. **Mảng rỗng:** trả Optional rỗng hoặc lỗi đã định, không trả sentinel tùy ý.
5. **Subnormal:** `{Double.MIN_VALUE, 0.0}` phải được xử lý theo chính sách dấu/zero phù hợp.
6. **Dữ liệu vô cực:** `{-Infinity}` và `{+Infinity}` để bảo đảm không trả nhầm cận hữu hạn.
7. **NaN:** `{1.0, NaN}` phải lan truyền NaN, bị từ chối hoặc bị lọc đúng theo hợp đồng, không do tình cờ của toán tử so sánh.
8. **Số 0 có dấu:** `{-0.0, +0.0}` kiểm tra lựa chọn và chuỗi/bit biểu diễn nếu dấu quan trọng.
9. **Cận cực đại hữu hạn:** `{-Double.MAX_VALUE, Double.MAX_VALUE}` và các trường hợp overflow khi tính giá trị khác từ cận.
10. **Kiểu float riêng:** lặp lại các case với `Float.MIN_VALUE`, `Float.MAX_VALUE`, ±infinity; không giả định mọi phép đổi kiểu giữ nguyên miền.

Kiểm thử nên đối chiếu giá trị kỳ vọng theo **hợp đồng**. Nếu cần phân biệt hai số 0 có dấu, `==` không đủ; kiểm tra `Double.compare` hoặc raw bits. Nếu kiểm tra giá trị NaN, dùng `Double.isNaN`, không viết `actual == Double.NaN`.

## 12. Bài tập tự đánh giá

1. `Float.MIN_VALUE > 0.0f` đúng hay sai? Vậy `Float.MIN_VALUE` có thể là số âm nhỏ nhất không?
2. Phân biệt `Double.MIN_VALUE`, `Double.MIN_NORMAL`, `-Double.MAX_VALUE` và `Double.NEGATIVE_INFINITY`. Giá trị nào là cận thấp nhất **hữu hạn**?
3. Với dữ liệu `{-10.0, -20.0, -5.0}`, đoạn vòng lặp dùng `double max = Double.MIN_VALUE` trả giá trị gì? Vì sao?
4. Nếu phép toán chấp nhận `-Infinity` nhưng không chấp nhận NaN, tại sao `double max = -Double.MAX_VALUE` là sentinel không an toàn cho mọi nguồn?
5. Với một nguồn rỗng, tại sao `DoubleStream.empty().max()` trả `OptionalDouble.empty()` và không trả `-Infinity`?
6. `Math.nextUp(0.0)` nhận giá trị gì? `Math.nextDown(0.0)` tương ứng với số âm nào?
7. Vì sao một hàm so sánh gần bằng dùng `Double.MIN_VALUE` có thể bác bỏ cả hai giá trị trông gần như bằng nhau quanh `1.0`?
8. Viết phương thức `minimumRejectNaN(double[])` tương tự hàm `maximumRejectNaN` trong bài, dùng `Double.compare` và `OptionalDouble`. Thử `+Infinity`, `-0.0`, nguồn rỗng và NaN.
9. Giải thích vì sao `DoubleStream.of(2.0, Double.NaN).max().isPresent()` vẫn trả `true` mặc dù max thực tế là `NaN`.

**Đáp án tóm tắt:** (1) Đúng; hằng này dương. (2) `Double.MIN_VALUE` là số dương subnormal nhỏ nhất; `MIN_NORMAL` là số dương normal nhỏ nhất; `-MAX_VALUE` là cận hữu hạn âm thấp nhất; `NEGATIVE_INFINITY` nhỏ hơn các số hữu hạn nhưng không hữu hạn. (3) Trả `4.9E-324` sai vì mọi phần tử đều âm. (4) Với nguồn chỉ có `-Infinity`, sentinel hữu hạn lớn hơn giá trị thật. (5) Cực trị của nguồn rỗng không tồn tại. (6) `+Double.MIN_VALUE`, `-Double.MIN_VALUE`. (7) Khoảng cách representable quanh 1 lớn hơn `Double.MIN_VALUE` rất nhiều. (8) Dùng cờ `found`, kiểm tra NaN rồi đổi phép `> 0` thành `< 0` trong `Double.compare`. (9) Optional có phần tử; NaN là giá trị `double` đặc biệt, khác trạng thái thiếu dữ liệu.

## 13. Ghi nhớ nhanh

`Float.MIN_VALUE` và `Double.MIN_VALUE` đều **dương**, rất nhỏ và khác 0; chúng không phải cận âm của kiểu. Khi cần **số hữu hạn thấp nhất** của `float`/`double`, dùng `-Float.MAX_VALUE` hoặc `-Double.MAX_VALUE`. Khi cần giá trị đặc biệt **nhỏ hơn mọi số hữu hạn**, dùng `NEGATIVE_INFINITY` với hiểu biết về miền giá trị.

Khi tìm cực trị của dữ liệu thực, cách chắc chắn nhất thường là `DoubleStream.min/max()` trả `OptionalDouble` hoặc vòng lặp khởi tạo bằng **phần tử đầu tiên**, kết hợp chính sách xử lý rỗng, vô cực, NaN và số 0 có dấu. Không dùng một hằng số tên `MIN_VALUE` như sentinel chỉ vì chữ “MIN”.

Tài liệu chuẩn Java SE 17:
- [`Float`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Float.html), [`Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): ý nghĩa chính xác của `MIN_VALUE`, `MIN_NORMAL`, `MAX_VALUE`, ±infinity và `NaN`.
- [`Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `min`, `max`, `nextUp`, `nextDown`, `ulp`.
- [`DoubleStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/DoubleStream.html), [`OptionalDouble`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalDouble.html): cực trị an toàn trên nguồn có thể rỗng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-004">Dùng == để so sánh hai wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm khi dùng `==` để so sánh hai wrapper trong Java

## 1. Vấn đề: hai số bằng nhau vẫn có thể không `==`

Java có các kiểu nguyên thủy (*primitive*) như `int`, `long`, `double`, `boolean`, và các **lớp bao (*wrapper*)** tương ứng: `Integer`, `Long`, `Double`, `Boolean`, v.v. Cách so sánh chúng phụ thuộc **kiểu tĩnh của hai toán hạng** trong biểu thức, không chỉ phụ thuộc giá trị số hiển thị.

```java
int primitiveA = 1000;
int primitiveB = 1000;
System.out.println(primitiveA == primitiveB); // true: so sánh giá trị int

Integer boxA = Integer.valueOf(1000);
Integer boxB = Integer.valueOf(1000);
System.out.println(boxA.equals(boxB)); // true: hai giá trị Integer bằng nhau

// Tránh dùng boxA == boxB để quyết định giá trị số học bằng nhau:
// phép đó kiểm tra có phải cùng object hay không.
```

Các dòng trên là mảnh mã minh họa, đặt trong `main` của một lớp và không cần import `Integer`. Biểu thức `boxA == boxB` có thể trả `true` hoặc `false` tùy identity/caching; **không được xem là tiêu chí so sánh số**.

Trong bài này, ta sẽ:

- Hiểu `==` trên primitive, reference và wrapper.
- Nhận diện bẫy **Integer cache**, autoboxing, unboxing và trường hợp `null`.
- Chọn `equals()`, `Objects.equals()` và `compareTo()` đúng mục đích.
- Phân biệt wrapper khác loại như `Integer` với `Long`.
- Giải thích ngoại lệ `Double.NaN` và signed zero.
- Kiểm tra bằng các chương trình Java 17+ có đầu ra xác định.

## 2. Quy tắc nền tảng của `==`

Trong Java, `==` là toán tử **bằng nhau** và thực hiện so sánh theo loại toán hạng:

| Hai toán hạng | `==` có ý nghĩa gì? | Ví dụ |
|---|---|---|
| Hai giá trị primitive số | So sánh giá trị số sau quy tắc chuyển kiểu số | `10 == 10L` → `true` |
| Hai primitive `boolean` | So sánh giá trị logic | `true == true` → `true` |
| Hai tham chiếu kiểu có thể so sánh tham chiếu | So sánh **identity**: có cùng đối tượng hoặc đều null không | `Integer a, b; a == b` |
| Một wrapper số và một primitive số | Wrapper thường được **unbox**; sau đó so sánh số | `Integer a = 5; a == 5` → `true` |
| Wrapper khác loại không thể so sánh trực tiếp dưới quy tắc `==` tham chiếu | Có thể là **lỗi biên dịch**, ví dụ `Integer == Long` | Xem mục 6 |

Vì vậy, câu nói “`==` luôn so sánh địa chỉ” là **không đúng cho mọi biểu thức**. Nó đúng khi Java đang thực hiện **reference equality** giữa hai object. Nếu ít nhất một toán hạng là primitive số, Java có thể unbox wrapper rồi áp dụng **numeric equality**. Cần xem chính xác biểu thức được viết.

### Primitive và wrapper: khác kiểu dữ liệu

```java
int a = 200;
int b = 200;
System.out.println(a == b); // true

Integer x = Integer.valueOf(200);
Integer y = Integer.valueOf(200);
System.out.println(x.equals(y)); // true
```

`int` không có identity đối tượng; `Integer` là reference type. Cả hai loại có thể cùng biểu diễn số 200 nhưng khác quy tắc so sánh mặc định.

## 3. Autoboxing, unboxing và Integer cache

### 3.1. Autoboxing là gì?

Java tự động chuyển primitive thành wrapper trong ngữ cảnh cần reference:

```java
Integer count = 10; // boxing từ int
int raw = count;    // unboxing trở về int
```

Về mặt ý nghĩa, boxing kiểu `int` được thực hiện tương tự `Integer.valueOf(10)`, không bắt buộc tạo đối tượng mới mỗi lần. JVM có thể dùng lại object wrapper theo cơ chế **cache**.

### 3.2. Vì sao `127 == 127` dạng `Integer` thường true?

Đối với `Integer.valueOf(int)`, Java **bảo đảm cache** các giá trị `-128` đến `127`, bao gồm hai đầu mút. Vì thế:

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);

System.out.println(a == b);      // true: cùng object cache
System.out.println(a.equals(b)); // true: bằng giá trị
```

Kết quả `a == b` là `true` vì implementation trả về cùng tham chiếu đã cache; điều này không biến `==` thành phép so sánh giá trị.

Những wrapper khác cũng có cơ chế reuse theo hợp đồng:

- `Byte.valueOf`: cache toàn bộ miền `byte`.
- `Short.valueOf`, `Long.valueOf`: bảo đảm cache ít nhất `-128` đến `127`.
- `Character.valueOf`: cache ít nhất các ký tự mã `\u0000` đến `\u007F`.
- `Boolean.valueOf`: trả các đối tượng `Boolean.TRUE` hoặc `Boolean.FALSE`.
- `Float` và `Double`: **không có cam kết cache tương tự** cho các giá trị số; tránh suy luận identity.

Với `Integer` ở ngoài miền `-128..127`, Java **có thể mở rộng cache** tùy phiên bản JVM, cài đặt và cấu hình. Vì vậy, không viết test kiểu “`Integer.valueOf(128) == Integer.valueOf(128)` chắc chắn là false”.

### 3.3. Ví dụ đầy đủ với đầu ra ổn định

**Tệp `WrapperCacheDemo.java`:**

```java
public class WrapperCacheDemo {
    public static void main(String[] args) {
        Integer smallA = Integer.valueOf(127);
        Integer smallB = Integer.valueOf(127);

        Integer largeA = Integer.valueOf(1_000);
        Integer largeB = Integer.valueOf(1_000);

        System.out.println("smallSameReference=" + (smallA == smallB));
        System.out.println("smallSameValue=" + smallA.equals(smallB));
        System.out.println("largeSameValue=" + largeA.equals(largeB));
        System.out.println("largeViaCompareTo="
                + (largeA.compareTo(largeB) == 0));
    }
}
```

```bash
javac WrapperCacheDemo.java
java WrapperCacheDemo
```

```text
smallSameReference=true
smallSameValue=true
largeSameValue=true
largeViaCompareTo=true
```

Ví dụ **cố ý không in `largeA == largeB` như một kết quả cố định**, vì ở ngoài miền cache bắt buộc, identity phụ thuộc triển khai. Đừng xây quy tắc nghiệp vụ từ hiệu ứng cache dù có vẻ luôn đúng trên máy bạn.

## 4. `equals()` là phép so sánh giá trị của wrapper cùng loại

Các wrapper chuẩn triển khai `equals(Object)` để kiểm tra giá trị theo hợp đồng từng lớp, kèm yêu cầu kiểu đối tượng thích hợp.

```java
Integer x = Integer.valueOf(1_000);
Integer y = Integer.valueOf(1_000);
System.out.println(x.equals(y)); // true

Long a = Long.valueOf(1_000L);
Long b = Long.valueOf(1_000L);
System.out.println(a.equals(b)); // true

Boolean yes = Boolean.valueOf(true);
System.out.println(yes.equals(Boolean.TRUE)); // true
```

Đây là mảnh mã minh họa trong `main`. Với `Integer`, `Long`, `Short`, `Byte` và `Character`, `equals` xét **cùng loại wrapper và cùng giá trị tương ứng**. Nếu đối tượng khác loại thì không được coi là bằng nhau chỉ vì cùng biểu diễn số.

`equals` là instance method, nên:

```java
Integer maybe = null;
// maybe.equals(0); // Nếu chạy sẽ ném NullPointerException.
```

Khi dữ liệu có thể `null`, `Objects.equals(a,b)` có thể là lựa chọn thích hợp; xem mục 7.

### So sánh theo thứ tự bằng `compareTo()`

Khi cần nhỏ hơn/lớn hơn, wrapper số có thể dùng `compareTo` hoặc phương thức so sánh kiểu primitive:

```java
Integer x = 2;
Integer y = 10;

System.out.println(x.compareTo(y) < 0); // true
System.out.println(Integer.compare(x, y) < 0); // true
```

`Integer.compare(x,y)` nhận `int`, vì vậy sẽ unbox cả hai wrapper. Nó **không xử lý `null`**. Với `compareTo`, cũng tránh giả định kết quả nhất thiết chỉ là `-1`, `0`, `1`; hợp đồng so sánh dùng **dấu âm, zero hoặc dấu dương**.

## 5. Khi một bên là primitive: `==` có thể đúng giá trị nhưng gặp NPE

So sánh **một `Integer` với `int`** khiến Java unbox `Integer` rồi so sánh các giá trị primitive:

```java
Integer wrapper = Integer.valueOf(1_000);
int primitive = 1_000;
long wider = 1_000L;

System.out.println(wrapper == primitive); // true
System.out.println(wrapper == wider);     // true
```

Ở biểu thức thứ hai, `wrapper` được unbox từ `Integer` thành `int`, sau đó áp dụng binary numeric promotion để so với `long`. Đây là so sánh giá trị số, không phải so sánh identity của hai object.

**Nguy hiểm:** nếu wrapper là `null`, unboxing ném `NullPointerException`:

```java
Integer count = null;

System.out.println(count == null); // true: so sánh tham chiếu với null
// System.out.println(count == 0); // NullPointerException do unboxing
```

Sự khác biệt được quyết định bởi kiểu toán hạng thứ hai:

- `count == null`: so sánh reference, không unbox.
- `count == Integer.valueOf(0)`: hai reference kiểu `Integer`, so sánh identity, không unbox; nếu `count` null, kết quả `false`.
- `count == 0`: một bên primitive `int`, buộc unbox `count`; nếu null sẽ lỗi.
- `count > 0`, `count + 1`: đều cần unboxing, nên null cũng lỗi.

### Ví dụ đầy đủ: dự đoán trường hợp null

**Tệp `WrapperUnboxingDemo.java`:**

```java
public class WrapperUnboxingDemo {
    public static void main(String[] args) {
        Integer boxed = Integer.valueOf(1_000);
        int primitive = 1_000;
        long wide = 1_000L;

        System.out.println("boxVsInt=" + (boxed == primitive));
        System.out.println("boxVsLongPrimitive=" + (boxed == wide));

        Integer missing = null;
        Integer otherMissing = null;
        Integer one = Integer.valueOf(1);

        System.out.println("twoNullReferences=" + (missing == otherMissing));
        System.out.println("nullVsWrapper=" + (missing == one));
        System.out.println("nullLiteral=" + (missing == null));

        try {
            System.out.println(missing == 1);
        } catch (NullPointerException ex) {
            System.out.println("nullUnboxing=NullPointerException");
        }
    }
}
```

```bash
javac WrapperUnboxingDemo.java
java WrapperUnboxingDemo
```

```text
boxVsInt=true
boxVsLongPrimitive=true
twoNullReferences=true
nullVsWrapper=false
nullLiteral=true
nullUnboxing=NullPointerException
```

Lưu ý: các phép `==` giữa hai tham chiếu null trả `true` vì **cùng giá trị tham chiếu null**, không phải vì hai object “bằng nhau”; ở đây không có object nào cả.

## 6. Hai wrapper khác loại: `Integer` và `Long`

Đây là bẫy quan trọng khác: hai wrapper có thể biểu diễn cùng giá trị số học nhưng **`equals` không cho true**:

```java
Integer i = Integer.valueOf(1);
Long l = Long.valueOf(1L);

System.out.println(i.equals(l)); // false: khác lớp
System.out.println(i.longValue() == l.longValue()); // true
```

**Không viết `i == l`** khi cả hai là object `Integer` và `Long`: biểu thức đó là **lỗi biên dịch** trong Java vì hai loại tham chiếu final này không có quan hệ chuyển kiểu cần thiết cho reference equality. Việc cả hai là `Number` **không tự biến `==` giữa chúng thành so sánh số học**.

Mã sau **chỉ để giải thích lỗi, không phải chương trình chạy được**:

```java
Integer i = 1;
Long l = 1L;
// boolean equal = (i == l); // LỖI BIÊN DỊCH: incomparable types
```

Nếu nghiệp vụ muốn so sánh **giá trị số học khác loại**, phải chọn cách chuẩn hóa có ý thức, ví dụ:

- Với `Integer` và `Long`, `i.longValue() == l.longValue()` bảo toàn chính xác cả hai miền, bởi `int` luôn chuyển chính xác sang `long`.
- Nếu liên quan `double`, `float` hoặc số nguyên rất lớn, chuyển sang floating-point có thể **mất chữ số**, nên cần tiêu chí và kiểu dữ liệu phù hợp (chẳng hạn `BigDecimal` được tạo đúng nguồn).
- Nếu nhu cầu là **bằng nhau về object và kiểu**, `Objects.equals(i,l)` trả `false` là hành vi hợp lý.

### Ví dụ đầy đủ

**Tệp `MixedWrapperDemo.java`:**

```java
public class MixedWrapperDemo {
    public static void main(String[] args) {
        Integer i = Integer.valueOf(1_000);
        Long l = Long.valueOf(1_000L);

        System.out.println("equals=" + i.equals(l));
        System.out.println("numeric=" + (i.longValue() == l.longValue()));
        System.out.println("againstPrimitiveLong=" + (i == 1_000L));
    }
}
```

```bash
javac MixedWrapperDemo.java
java MixedWrapperDemo
```

```text
equals=false
numeric=true
againstPrimitiveLong=true
```

Chỉ vì cùng chữ số `1000` không có nghĩa wrapper cùng kiểu hoặc cùng tham chiếu. Khi thiết kế API, hãy thống nhất một kiểu số phù hợp để tránh phải so sánh tùy tiện các wrapper khác loại.

## 7. `Objects.equals(a,b)`: xử lý null an toàn và dùng đúng `equals`

`java.util.Objects.equals(a,b)` kiểm tra hai tham chiếu đều null hoặc ủy quyền cho `a.equals(b)`. Nó tránh `NullPointerException` khi biến bên trái null:

```java
import java.util.Objects;

Integer a = null;
Integer b = null;
Integer one = 1;

System.out.println(Objects.equals(a, b)); // true
System.out.println(Objects.equals(a, one)); // false
System.out.println(Objects.equals(Integer.valueOf(500),
                                  Integer.valueOf(500))); // true
```

Đoạn mã trên minh họa trong `main`, với import đặt đầu tệp. `Objects.equals` **không tự quy đổi kiểu số**; `Objects.equals(Integer.valueOf(1), Long.valueOf(1L))` vẫn là `false`.

### Ví dụ hoàn chỉnh: bảng so sánh null-safe

**Tệp `WrapperObjectsEqualsDemo.java`:**

```java
import java.util.Objects;

public class WrapperObjectsEqualsDemo {
    public static void main(String[] args) {
        Integer a = null;
        Integer b = null;
        Integer x = Integer.valueOf(500);
        Integer y = Integer.valueOf(500);

        System.out.println("nullNull=" + Objects.equals(a, b));
        System.out.println("nullValue=" + Objects.equals(a, x));
        System.out.println("valueValue=" + Objects.equals(x, y));
        System.out.println("crossType=" + Objects.equals(
                Integer.valueOf(1), Long.valueOf(1L)));
    }
}
```

```bash
javac WrapperObjectsEqualsDemo.java
java WrapperObjectsEqualsDemo
```

```text
nullNull=true
nullValue=false
valueValue=true
crossType=false
```

**Quan trọng:** nếu `null` là dữ liệu không hợp lệ, `Objects.equals` không thay thế validation. Nó coi hai null là bằng nhau; với quy tắc nghiệp vụ đòi cả hai đều chứa số, hãy chủ động từ chối null trước khi so sánh.

## 8. `Double` và `Float`: `NaN`, signed zero còn gây khác biệt

Với wrapper số thực, `equals` không hoàn toàn giống phép `==` trên primitive. Điều này đặc biệt quan trọng khi bạn kiểm thử NaN và zero có dấu.

### 8.1. `NaN`: primitive không bằng chính nó, wrapper `equals` coi hai NaN bằng nhau

```java
System.out.println(Double.NaN == Double.NaN); // false
System.out.println(Double.valueOf(Double.NaN)
        .equals(Double.valueOf(Double.NaN)));  // true
```

Ở dòng đầu, toán hạng là primitive `double` nên áp dụng so sánh IEEE 754: `NaN == NaN` luôn false. Dòng thứ hai dùng `Double.equals` trên hai wrapper; phương thức dựa vào quy tắc so sánh biểu diễn bit (chuẩn hóa các cách mã hóa NaN), nên hai giá trị NaN được xem là bằng nhau.

Vì vậy, nếu muốn kiểm tra một giá trị có phải NaN hay không, dùng `Double.isNaN(value)` hoặc `Float.isNaN(value)`; không dùng `== Double.NaN`.

### 8.2. `+0.0` và `-0.0`: primitive bằng nhau, wrapper `equals` phân biệt

```java
System.out.println(0.0 == -0.0); // true
System.out.println(Double.valueOf(0.0)
        .equals(Double.valueOf(-0.0))); // false
```

Hai zero bằng nhau theo so sánh số học primitive nhưng có bit dấu khác nhau. `Double.equals` phân biệt chúng, phù hợp với `Double.hashCode` và hành vi các collections phụ thuộc hash/equality.

`Double.compare(0.0, -0.0) > 0` cũng là `true`: API áp dụng thứ tự có phân biệt zero có dấu. Không suy ra rằng `Double.compare` luôn tương đương `==` của primitive.

### 8.3. Chương trình đầy đủ

**Tệp `FloatingWrapperEqualityDemo.java`:**

```java
public class FloatingWrapperEqualityDemo {
    public static void main(String[] args) {
        Double nanA = Double.valueOf(Double.NaN);
        Double nanB = Double.valueOf(Double.NaN);
        Double plusZero = Double.valueOf(0.0);
        Double minusZero = Double.valueOf(-0.0);

        System.out.println("primitiveNaN=" + (Double.NaN == Double.NaN));
        System.out.println("wrapperNaN=" + nanA.equals(nanB));
        System.out.println("primitiveZero=" + (0.0 == -0.0));
        System.out.println("wrapperZero=" + plusZero.equals(minusZero));
        System.out.println("compareZero=" + Double.compare(0.0, -0.0));
    }
}
```

```bash
javac FloatingWrapperEqualityDemo.java
java FloatingWrapperEqualityDemo
```

```text
primitiveNaN=false
wrapperNaN=true
primitiveZero=true
wrapperZero=false
compareZero=1
```

Tương tự, `Float` có quy tắc phù hợp cho `Float.NaN` và zero có dấu. Không dùng `==` để làm tiêu chí đồng nhất của mọi trường hợp floating-point mà không xét mục tiêu: so sánh số học, đồng nhất biểu diễn hay chính sách dung sai.

## 9. Vì sao lỗi này ảnh hưởng `HashSet` và `HashMap`?

`HashSet` và khóa của `HashMap` dùng `equals` cùng `hashCode` để xác định sự trùng lặp; chúng **không dựa trên `==` để so sánh giá trị wrapper**.

Ví dụ:

**Tệp `WrapperCollectionDemo.java`:**

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WrapperCollectionDemo {
    public static void main(String[] args) {
        Set<Integer> ids = new HashSet<>();
        ids.add(Integer.valueOf(1_000));
        ids.add(Integer.valueOf(1_000));

        Map<Integer, String> byId = new HashMap<>();
        byId.put(Integer.valueOf(1_000), "first");
        byId.put(Integer.valueOf(1_000), "updated");

        System.out.println("setSize=" + ids.size());
        System.out.println("mapSize=" + byId.size());
        System.out.println("value=" + byId.get(Integer.valueOf(1_000)));
    }
}
```

```bash
javac WrapperCollectionDemo.java
java WrapperCollectionDemo
```

```text
setSize=1
mapSize=1
value=updated
```

Dù wrapper 1000 có thể được tạo thành các object khác nhau, chúng bằng nhau theo `Integer.equals`. `HashSet` giữ một phần tử và `HashMap` thay thế value của khóa cùng giá trị.

Nếu tự viết thuật toán khử trùng lặp với `==` trên wrapper, chương trình có thể hoạt động “đúng” ở vài giá trị nhỏ do cache rồi sai với đầu vào lớn hơn. Đừng sử dụng tính chất cache làm kiểm tra tính duy nhất.

## 10. Mẫu code thường sai và cách sửa

| Mục đích | Dễ viết sai | Cách nên dùng |
|---|---|---|
| Hai `Integer` bằng giá trị? | `a == b` | `a.equals(b)` nếu chắc chắn non-null |
| Hai wrapper cùng loại có thể null? | `a.equals(b)` | `Objects.equals(a,b)` |
| So sánh `Integer` lớn nhỏ | `a == b` | `a.compareTo(b) < 0`, `> 0`, `== 0` |
| `Integer` và `Long` cùng giá trị? | `a == b` (lỗi biên dịch) | `a.longValue() == b.longValue()` sau kiểm tra null |
| Wrapper với primitive | Mặc định không thể null | Validate null trước khi unbox |
| Kiểm tra `Double.NaN` | `x == Double.NaN` | `Double.isNaN(x)` |
| Phân biệt `+0.0` và `-0.0` kiểu `Double` | `x.doubleValue() == y.doubleValue()` | `x.equals(y)` hoặc so sánh bit phù hợp |
| Nhận biết cùng đối tượng? | `a.equals(b)` | `a == b` nếu identity thực sự là mục tiêu |
| Test equality dựa trên cache `Integer` | Kiểm tra `Integer.valueOf(128) == ...` với expected cứng | Kiểm tra `equals` cho giá trị, không dựa vào cache |

**Mẹo review:** khi thấy `==` xuất hiện giữa hai biến có tên `Integer`, `Long`, `Double`, `Boolean`, hãy đặt câu hỏi: “Mình có cần biết hai biến cùng tham chiếu không, hay cần so sánh giá trị?” Trong phần lớn nghiệp vụ xử lý số, câu trả lời là **giá trị**.

### Không dùng `new Integer(int)` để né cache

Constructor wrapper kiểu `new Integer(10)` đã bị deprecated và đánh dấu chờ loại bỏ ở Java hiện đại. Không dùng nó để ép “hai wrapper chắc chắn là hai object khác nhau” trong mã production hoặc test thông thường. Đây cũng là lý do các ví dụ ở bài ưu tiên `Integer.valueOf` và kiểm tra giá trị bằng `equals`.

## 11. Một ví dụ tổng hợp có thể chạy nguyên vẹn

Chương trình sau kiểm tra một loạt khác biệt bằng các đầu ra **không phụ thuộc cache ở ngoài miền bắt buộc**.

**Tệp `WrapperComparisonSummary.java`:**

```java
import java.util.Objects;

public class WrapperComparisonSummary {
    public static void main(String[] args) {
        Integer smallA = Integer.valueOf(127);
        Integer smallB = Integer.valueOf(127);
        Integer largeA = Integer.valueOf(1_000);
        Integer largeB = Integer.valueOf(1_000);

        System.out.println("cache127=" + (smallA == smallB));
        System.out.println("equals1000=" + largeA.equals(largeB));
        System.out.println("numeric1000=" + (largeA == 1_000));

        Integer absent = null;
        System.out.println("nullEquals=" + Objects.equals(absent, null));

        Integer oneInt = Integer.valueOf(1);
        Long oneLong = Long.valueOf(1L);
        System.out.println("crossEquals=" + oneInt.equals(oneLong));
        System.out.println("crossNumeric="
                + (oneInt.longValue() == oneLong.longValue()));

        System.out.println("NaNprimitive=" + (Double.NaN == Double.NaN));
        System.out.println("NaNwrapper=" + Double.valueOf(Double.NaN)
                .equals(Double.valueOf(Double.NaN)));
        System.out.println("zeroPrimitive=" + (0.0 == -0.0));
        System.out.println("zeroWrapper=" + Double.valueOf(0.0)
                .equals(Double.valueOf(-0.0)));
    }
}
```

```bash
javac WrapperComparisonSummary.java
java WrapperComparisonSummary
```

```text
cache127=true
equals1000=true
numeric1000=true
nullEquals=true
crossEquals=false
crossNumeric=true
NaNprimitive=false
NaNwrapper=true
zeroPrimitive=true
zeroWrapper=false
```

Đầu ra `cache127=true` dựa trên cache được `Integer.valueOf` bảo đảm. Chương trình không coi identity của wrapper 1000 là điều kiện đúng/sai, vì nó không thuộc miền cache bắt buộc. `numeric1000` là so sánh `Integer` với literal **primitive** `int` nên unbox.

## 12. Kiểm thử để bắt lỗi `==` trên wrapper

Một số test thực dụng cho dự án xử lý wrapper:

1. **Vượt cache**: kiểm tra equality của `Integer.valueOf(1000)` và một `Integer` khác cùng giá trị qua `equals`, không cố gán expected cho `==`.
2. **Giá trị sát biên cache**: `127`, `128`, `-128`, `-129` giúp nhận diện sự khác nhau giữa identity và value; các ca ngoài miền cache phải kiểm tra theo hợp đồng, không dựa vào kết quả máy riêng.
3. **Null**: `null == null`, `Objects.equals(null, value)`, unboxing null khi so với primitive để xác nhận xử lý lỗi đúng.
4. **Kiểu khác nhau**: `Integer.valueOf(1).equals(Long.valueOf(1))` là `false`, còn so sánh giá trị qua kiểu chung có thể là `true`.
5. **Số thực đặc biệt**: `NaN`, `+0.0`, `-0.0` với primitive `==` và wrapper `equals`.
6. **Collections**: hai wrapper cùng giá trị ngoài cache được `HashSet`/khóa `HashMap` gộp đúng theo `equals/hashCode`.
7. **Không phụ thuộc JVM cache mở rộng**: test nên chạy ổn trên các JDK khác nhau, không đặt kỳ vọng giả rằng `Integer.valueOf(128) == Integer.valueOf(128)` nhất định là `false`.

Nếu kiểm tra biến thể null trong code có unboxing, hãy dùng bài test xác nhận nó **được từ chối đúng hoặc được xử lý trước khi unbox**. Không viết unit test chỉ so sánh vài giá trị trong miền cache rồi kết luận tính đúng cho mọi `Integer`.

## 13. Bài tập tự kiểm tra có lời giải

1. **`Integer a = 127, b = 127; a == b` có thể là gì?**  
   `true` theo bảo đảm boxing/cache cho các giá trị thuộc `-128..127`. Nhưng đây vẫn là phép so sánh reference.

2. **`Integer a = 1_000, b = 1_000; a == b` có phải luôn `false` không?**  
   Không thể khẳng định. Cache có thể được mở rộng; đừng dùng `==` để so sánh giá trị. `a.equals(b)` trả `true`.

3. **Vì sao `Integer a = null; a == 0` ném ngoại lệ, còn `a == null` không?**  
   `a == 0` cần unbox `a` thành `int`; null không thể unbox. `a == null` so sánh tham chiếu.

4. **`Integer.valueOf(3).equals(Long.valueOf(3L))` trả gì?**  
   `false`; hai đối tượng thuộc các lớp wrapper khác nhau.

5. **Có thể viết trực tiếp `Integer i; Long l; i == l` không?**  
   Không; hai wrapper final khác loại không so sánh được bằng toán tử reference `==` ở đây. Nếu cần, dùng cách so sánh số học có chuyển kiểu an toàn.

6. **`Double.NaN == Double.NaN` và `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` trả gì?**  
   Lần lượt `false` và `true` vì primitive `double` và `Double.equals` có hợp đồng khác nhau.

7. **`0.0 == -0.0` và `Double.valueOf(0.0).equals(Double.valueOf(-0.0))` trả gì?**  
   Lần lượt `true` và `false`; wrapper `equals` phân biệt bit dấu của zero.

8. **`Objects.equals(null,null)` có `true` không?**  
   Có. Tuy nhiên, nếu trường số bắt buộc phải có giá trị, cần kiểm tra validation riêng.

## 14. Kết luận và tài liệu tham khảo

Khi hai biến là **wrapper**, `==` có thể kiểm tra **cùng reference**, không phải số học. Kết quả đúng ở giá trị nhỏ có thể là do **cache**, và so sánh wrapper với primitive lại **unbox**. Để kiểm tra **hai wrapper cùng loại bằng giá trị**, dùng `equals` hoặc `Objects.equals` nếu cần hỗ trợ null; dùng `compareTo`/API so sánh thích hợp khi cần thứ tự. Với `Double`/`Float`, xác định rõ mục tiêu khi gặp `NaN` hoặc signed zero.

Tài liệu chính thức:

- [Java 21 — `Integer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Integer.html)
- [Java 21 — `Double`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html)
- [Java 21 — `Objects`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Objects.html)
- [Java Language Specification — Equality Operators, §15.21](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.21)
- [Java Language Specification — Boxing Conversion, §5.1.7](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-005">Unbox null trong phép toán hoặc toán tử điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Unbox null trong phép toán hoặc toán tử điều kiện của Java

## 1. Vấn đề: một biến Integer có thể chứa null, nhưng int thì không

Trong Java, các kiểu số nguyên thủy như `int`, `long`, `double`, `float`, `short`, `byte` và kiểu logic `boolean` luôn chứa một giá trị primitive khi được sử dụng hợp lệ. Trong khi đó, các lớp wrapper tương ứng (`Integer`, `Long`, `Double`, `Float`, `Short`, `Byte`, `Boolean`, `Character`) là **kiểu tham chiếu**, nên biến có thể chứa `null`.

Java hỗ trợ chuyển đổi tự động:

- **Boxing**: từ primitive sang wrapper, ví dụ `int → Integer`.
- **Unboxing**: từ wrapper sang primitive, ví dụ `Integer → int`.

Với một wrapper khác `null`, unboxing là thao tác thuận tiện. Với wrapper đang bằng `null`, phép chuyển sang primitive gây **`NullPointerException` tại thời điểm chạy**.

~~~java
Integer count = null;

// Lỗi xảy ra tại lệnh gán vì Java cần unbox count thành int:
// int total = count; // NullPointerException

Integer safeToStore = count; // Hợp lệ: chỉ sao chép tham chiếu null.
~~~

Một số lỗi khó nhận ra hơn vì chương trình **không ghi tường minh** `intValue()`, `longValue()` hoặc `booleanValue()`. Toán tử số học, so sánh, và đặc biệt toán tử điều kiện `?:` có thể ngầm đòi primitive. Bài học này giải thích chính xác điểm unboxing xảy ra và cách tránh lỗi mà không tự ý thay đổi ý nghĩa nghiệp vụ của `null`.

## 2. Unboxing thực chất tương đương gọi phương thức lấy giá trị primitive

Ví dụ:

~~~java
Integer number = Integer.valueOf(12);
int a = number;                  // Auto-unboxing
int b = number.intValue();       // Tương đương về giá trị
System.out.println(a == b);      // true
~~~

Nếu `number` là `null`, lời gọi `number.intValue()` không thể thực hiện. Tương tự:

| Wrapper | Primitive đích | Phương thức lấy primitive tương ứng |
|---|---|---|
| `Integer` | `int` | `intValue()` |
| `Long` | `long` | `longValue()` |
| `Double` | `double` | `doubleValue()` |
| `Float` | `float` | `floatValue()` |
| `Short` | `short` | `shortValue()` |
| `Byte` | `byte` | `byteValue()` |
| `Boolean` | `boolean` | `booleanValue()` |
| `Character` | `char` | `charValue()` |

Nhiều trường hợp Java chấp nhận cả wrapper và primitive trong biểu thức, nhưng quá trình biên dịch chèn logic unboxing khi kiểu được yêu cầu là primitive. Do đó, **biểu thức biên dịch thành công không chứng minh dữ liệu `null` sẽ chạy an toàn**.

~~~java
Integer quantity = null;

// Các lệnh dưới đây có thể biên dịch, nhưng khi thực thi sẽ gây NPE:
// int result = quantity + 1;
// boolean positive = quantity > 0;
// int maximum = Math.max(quantity, 0);
~~~

`NullPointerException` là vấn đề runtime, không phải một quy tắc Java buộc compiler phải từ chối các biến wrapper có khả năng `null`.

## 3. Các phép toán số học dễ kích hoạt unboxing

Khi toán hạng là wrapper nhưng toán tử cần primitive, Java sẽ thử unbox. Các trường hợp phổ biến:

| Biểu thức với `Integer n = null` | Tại sao gặp NPE? |
|---|---|
| `int x = n;` | Gán cho `int` yêu cầu `n.intValue()` |
| `int x = n + 1;` | `+` số học yêu cầu giá trị số của `n` |
| `int x = n * 2;` | `*` số học yêu cầu unbox |
| `boolean b = n > 0;` | `>` là toán tử so sánh số, cần `int` |
| `boolean b = n == 0;` | Wrapper so với primitive số, cần quy tắc so sánh số |
| `n++;` | Đọc giá trị primitive, tăng, rồi boxing/gán lại |
| `n += 2;` | Compound assignment vẫn cần đọc và tính từ giá trị cũ |
| `int x = Math.max(n, 0);` | Phương thức `Math.max(int, int)` cần tham số `int` |
| `int x = Math.abs(n);` | Overload số nguyên cần `int` |

Ví dụ toán tử tăng không làm wrapper biến thành đối tượng có thể thay đổi. `Integer` vẫn bất biến; `n++` thường được hiểu như lấy giá trị cũ, thực hiện phép cộng primitive rồi gán wrapper mới. Nếu giá trị cũ là `null`, bước unbox thất bại trước khi có kết quả.

~~~java
Integer attempts = Integer.valueOf(2);
attempts++; // Thành Integer chứa 3, không sửa đối tượng Integer(2) cũ.
~~~

Các phép số học được đánh giá theo thứ tự và quy tắc kiểu Java; toán hạng nào được thực sự đánh giá và cần unbox thì null ở vị trí đó gây lỗi. Không nên giải thích NPE này bằng "Java cộng null như số 0": Java **không có quy tắc coi null là 0**.

### Phân biệt nối chuỗi với phép cộng số học

Dấu `+` cũng dùng để nối chuỗi:

~~~java
Integer n = null;
System.out.println("n=" + n); // n=null

// int total = n + 1;          // NullPointerException
~~~

Trong nối chuỗi, Java có thể chuyển tham chiếu `null` thành chuỗi `"null"`; điều này khác hẳn `+` số học. Đừng suy luận rằng vì in `n` không lỗi nên phép cộng số với `n` cũng an toàn.

## 4. Toán tử điều kiện ?: gây unboxing ngay cả khi biến nhận là Integer

Toán tử điều kiện có cú pháp:

~~~java
condition ? expressionIfTrue : expressionIfFalse
~~~

Java **chỉ đánh giá một trong hai nhánh kết quả** tùy điều kiện. Tuy nhiên, kiểu của toàn bộ biểu thức `?:` được xác định tại thời điểm biên dịch từ các toán hạng và quy tắc ngôn ngữ. Điều này tạo ra trường hợp bất ngờ:

~~~java
Integer nullable = null;
boolean chooseFirst = true;

// Biên dịch được, nhưng chạy NPE:
Integer result = chooseFirst ? nullable : 7;
~~~

Ở đây một nhánh là `Integer` và nhánh kia là primitive `int`. Theo quy tắc của **numeric conditional expression**, kết quả điều kiện có kiểu primitive `int` trong trường hợp này. Nếu nhánh `nullable` được chọn, Java phải unbox `nullable` để tạo kết quả `int`. Vì nó là `null`, chương trình ném `NullPointerException`.

**Đặc biệt:** biến nhận kết quả có kiểu `Integer` **không bảo vệ** đoạn mã trên. Java trước hết phải tính giá trị biểu thức `?:` (kiểu `int`), sau đó mới boxing để gán vào `Integer`. Lỗi đã xảy ra ở bước unbox trước đó.

Tương tự:

~~~java
Integer nullable = null;
boolean yes = true;

// CŨNG NPE dù biến nhận kiểu Object:
Object value = yes ? nullable : 7;
~~~

Không thể dùng kiểu biến nhận `Object` làm "lá chắn" cho unboxing đã được quyết định bên trong biểu thức điều kiện.

### 4.1. Cách tránh: giữ cả hai nhánh là Integer nếu cần cho phép null

~~~java
Integer nullable = null;
boolean yes = true;

// Hai nhánh đều là Integer: kết quả tham chiếu có thể là null.
Integer result = yes ? nullable : Integer.valueOf(7);
System.out.println(result); // null
~~~

Cần hiểu đúng: đoạn này **không thay null thành giá trị mặc định**. Nó chủ ý giữ `null` ở kết quả nếu nhánh tương ứng được chọn. Điều này phù hợp khi `null` có ý nghĩa "không có dữ liệu" hoặc "chưa được xác định".

Sau đó nếu muốn lấy primitive, vẫn phải kiểm tra:

~~~java
int safeValue = result == null ? 0 : result;
~~~

Lời giải trên chỉ phù hợp nếu hợp đồng nói `null` có thể được thay bằng 0. Nếu thiếu dữ liệu là lỗi, cần báo lỗi thay vì âm thầm gán mặc định.

### 4.2. Bảng so sánh các nhánh của ?:

Giả sử `Integer missing = null; boolean chooseFirst = true;`.

| Biểu thức | Kiểu/hiệu ứng quan trọng | Khi nhánh đầu được chọn |
|---|---|---|
| `chooseFirst ? missing : 7` | Một nhánh `Integer`, một nhánh `int` → unboxing để thành số | **NPE** |
| `chooseFirst ? missing : Integer.valueOf(7)` | Hai nhánh `Integer` | Trả tham chiếu `null`, chưa NPE |
| `chooseFirst ? missing : "fallback"` | Hai nhánh tham chiếu không phải numeric conditional | Trả `null`, chưa NPE |
| `chooseFirst ? null : Integer.valueOf(7)` | Nhánh `null` và tham chiếu `Integer` | Có thể trả `null` an toàn khi nơi nhận chấp nhận tham chiếu |
| `chooseFirst ? null : 7` rồi gán cho `int` | Nhánh `null` được chọn, phép gán cần unbox kết quả nullable | **NPE** |
| `chooseFirst ? missing : 7` rồi gán cho `Object` | Biểu thức numeric conditional vẫn cần `int` trước khi boxing | **NPE** |

Điểm quan trọng: hãy phân biệt **NPE xảy ra khi tính toán biểu thức** và **NPE xảy ra sau đó khi ép kết quả tham chiếu sang primitive**. Chúng có thể có stack trace khác nhau, nhưng đều bắt nguồn từ việc cần primitive khi giá trị tham chiếu là `null`.

### 4.3. Điều kiện false thì nhánh null không nhất thiết gây lỗi

~~~java
Integer missing = null;
boolean chooseFirst = false;

int value = chooseFirst ? missing : 7; // 7, không NPE
~~~

Vì điều kiện false nên nhánh `missing` không được đánh giá. Tuy vậy biểu thức này **không an toàn với mọi dữ liệu**: nếu đổi điều kiện sang true, NPE xuất hiện. Đây là nguyên nhân các lỗi sản xuất đôi khi chỉ xảy ra trên một đường nhánh hiếm gặp.

## 5. Boolean null ngay tại phần điều kiện

Không chỉ nhánh kết quả; **điều kiện của `if`, `while`, `for` và `?:` cần boolean primitive**. Khi truyền `Boolean`, Java có thể unbox nó:

~~~java
Boolean approved = null;

// Các biểu thức sau biên dịch nhưng có thể ném NullPointerException:
// if (approved) { ... }
// String label = approved ? "yes" : "no";
// while (approved) { ... }
~~~

Toán tử logic `&&` và `||` cũng yêu cầu giá trị boolean ở những toán hạng thực sự được đánh giá:

~~~java
Boolean enabled = null;

// boolean wrong = enabled && true; // NPE
boolean safe = enabled != null && enabled; // false
~~~

Trong biểu thức an toàn, toán tử `&&` đánh giá từ trái sang phải và **short-circuit**: khi `enabled != null` false, Java không đánh giá `enabled` ở vế phải nên không unbox.

Một cách rõ ràng hơn khi muốn coi `null` là false:

~~~java
Boolean enabled = null;
boolean enabledOnlyIfTrue = Boolean.TRUE.equals(enabled); // false
~~~

`Boolean.TRUE.equals(enabled)` không gọi phương thức trên đối tượng có thể null; receiver là hằng `Boolean.TRUE` không null. Nó trả false cho cả `Boolean.FALSE` và `null`.

**Cẩn thận với ba trạng thái:** nếu `null` là trạng thái "chưa trả lời", khác với false ("từ chối"), đừng gộp cả hai thành false mà không có yêu cầu nghiệp vụ. Có thể kiểm tra rõ:

~~~java
if (enabled == null) {
    // Thiếu dữ liệu hoặc trạng thái chưa xác định.
} else if (enabled) {
    // true
} else {
    // false
}
~~~

Ở đây điều kiện `enabled` chỉ được đánh giá sau khi đã biết wrapper khác null.

### Sự khác biệt giữa && và &

`&&` short-circuit; `&` trên boolean luôn đánh giá cả hai vế:

~~~java
Boolean enabled = null;

boolean safe = enabled != null && enabled; // false
// boolean unsafe = enabled != null & enabled; // NPE: vế phải vẫn chạy.
~~~

Quy tắc tương tự với `||` (short-circuit) và `|` (không short-circuit) khi dùng cho boolean. Đây là lỗi thường xảy ra khi thay nhầm một ký tự toán tử logic.

## 6. So sánh ==, equals và < khi tham gia wrapper

Không phải mọi toán tử `==` liên quan đến wrapper đều bắt buộc unbox. Kiểu tĩnh của **cả hai toán hạng** quyết định so sánh tham chiếu hay so sánh số.

~~~java
Integer a = null;
Integer b = Integer.valueOf(7);

System.out.println(a == b);      // false: hai tham chiếu Integer
System.out.println(a == null);   // true: kiểm tra tham chiếu
System.out.println(java.util.Objects.equals(a, b)); // false

// boolean numerical = a == 7; // NPE: Integer so với primitive int
// boolean less = a < 7;       // NPE: phép < đòi giá trị số
~~~

Hai `Integer` dùng `==` là **so sánh nhận dạng tham chiếu**, không phải so sánh giá trị số. Điều này có thể dẫn đến một loại bug khác ngay cả khi không null:

~~~java
Integer x = new Integer(1000); // Ví dụ minh họa API cũ; constructor bị deprecated.
Integer y = new Integer(1000);

// x == y có thể false dù cùng giá trị; đừng dùng == để so giá trị wrapper.
~~~

Trong mã mới, không cần tạo wrapper bằng constructor. Dùng `Integer.valueOf(...)` hoặc autoboxing và **`Objects.equals(x, y)`** cho so sánh giá trị có thể null, hoặc sau khi kiểm tra null dùng `x.intValue() == y.intValue()` nếu thực sự cần primitive.

So sánh giá trị khi null có thể có ý nghĩa riêng. Ví dụ:

~~~java
Integer score = null;
boolean isZero = java.util.Objects.equals(score, 0); // false, không NPE
~~~

`Objects.equals` an toàn với null nhưng **không** coi null là 0; nó coi một bên null và bên kia không null là không bằng nhau.

Riêng `score.equals(0)` vẫn ném NPE nếu `score == null`, bởi receiver của lời gọi phương thức là null:

~~~java
// score.equals(0); // NullPointerException
boolean zero = Integer.valueOf(0).equals(score); // false
~~~

Khi so sánh `Integer` với `Long`, `Objects.equals` xét cả kiểu đối tượng, nên `Integer.valueOf(5)` không bằng `Long.valueOf(5L)` theo `equals`. Nếu nghiệp vụ muốn so giá trị giữa các kiểu số, hãy chuyển đổi **có kiểm soát và kiểm tra null**; không mặc định mọi wrapper số đều tương đương chỉ vì cùng trị số.

## 7. Chương trình Java 17+ hoàn chỉnh: tái hiện NPE và các cách an toàn

Tạo tệp `NullUnboxingDemo.java`. Chương trình bắt `NullPointerException` theo từng ví dụ để toàn bộ chương trình có thể chạy đến cuối, đồng thời giữ đầu ra ổn định và dễ kiểm tra:

~~~java
import java.util.Objects;
import java.util.Optional;

public class NullUnboxingDemo {
    private static void check(String name, Runnable action) {
        try {
            action.run();
        } catch (NullPointerException ex) {
            System.out.println(name + ": NullPointerException");
        }
    }

    public static void main(String[] args) {
        Integer missing = null;
        Boolean unknown = null;
        boolean chooseFirst = true;

        check("arithmetic", () -> {
            int value = missing + 1;
            System.out.println(value);
        });

        check("mixed-ternary", () -> {
            Integer value = chooseFirst ? missing : 7;
            System.out.println(value);
        });

        Integer kept = chooseFirst
                ? missing
                : Integer.valueOf(7);
        System.out.println("both-boxed: " + kept);

        check("Object-target", () -> {
            Object result = chooseFirst ? missing : 7;
            System.out.println(result);
        });

        Object reference = chooseFirst ? missing : "fallback";
        System.out.println("reference-ternary: " + reference);

        check("condition", () -> {
            String answer = unknown ? "yes" : "no";
            System.out.println(answer);
        });

        check("compare-with-int", () -> {
            boolean equalsZero = missing == 0;
            System.out.println(equalsZero);
        });

        System.out.println("Objects.equals: "
                + Objects.equals(missing, 0));
        System.out.println("safe-Boolean: "
                + Boolean.TRUE.equals(unknown));

        int fallback = missing == null ? 0 : missing;
        System.out.println("checked-fallback: " + fallback);

        int optionalFallback = Optional.ofNullable(missing).orElse(0);
        System.out.println("optional-fallback: " + optionalFallback);
    }
}
~~~

Chạy với JDK 17 hoặc mới hơn:

~~~bash
javac NullUnboxingDemo.java
java NullUnboxingDemo
~~~

Kết quả stdout:

~~~text
arithmetic: NullPointerException
mixed-ternary: NullPointerException
both-boxed: null
Object-target: NullPointerException
reference-ternary: null
condition: NullPointerException
compare-with-int: NullPointerException
Objects.equals: false
safe-Boolean: false
checked-fallback: 0
optional-fallback: 0
~~~

Đọc kết quả theo từng nhóm:

- `arithmetic`: Java cần `int` để thực hiện phép cộng.
- `mixed-ternary` và `Object-target`: biểu thức điều kiện có nhánh `Integer`/`int` nên unbox trước khi trả kết quả, bất kể biến nhận là `Integer` hay `Object`.
- `both-boxed`: hai nhánh đều là `Integer`, nên kết quả có thể là tham chiếu null.
- `reference-ternary`: hai nhánh `Integer` và `String` tạo biểu thức tham chiếu, nhánh `missing` có thể trả null mà không unbox.
- `condition`: `Boolean` null bị unbox khi chọn nhánh.
- `compare-with-int`: phép so sánh wrapper với primitive cần xử lý giá trị số.
- `Objects.equals` và `Boolean.TRUE.equals`: không dùng unboxing của đối tượng nullable; kết quả false.
- `checked-fallback` và `optional-fallback`: lựa chọn mặc định 0 có chủ đích, không NPE.

**Không phụ thuộc vào văn bản chi tiết của thông báo NPE** trong test: "helpful NullPointerExceptions" của các bản JDK có thể tạo thông báo khác nhau tùy cách biên dịch. Chương trình in tên ngoại lệ cố định để giúp hiểu hiện tượng, không in `ex.getMessage()`.

## 8. Các mẫu sửa lỗi: chọn theo ý nghĩa của null

Lỗi unboxing không nên được "sửa" bằng cách đổi mọi null thành 0. Cần quyết định ý nghĩa nghiệp vụ của null trước.

### 8.1. Null nghĩa là giá trị mặc định

~~~java
Integer quantity = null;
int value = quantity != null ? quantity : 0;
~~~

Toán tử `?:` trong ví dụ vẫn có kiểu primitive `int` nhưng **chỉ chọn nhánh có thể null khi đã biết quantity khác null**. Phép unboxing vì thế an toàn.

Có thể viết ngắn hơn:

~~~java
int value = quantity == null ? 0 : quantity;
~~~

Hoặc khi dữ liệu đầu vào được thiết kế theo kiểu nullable:

~~~java
int value = java.util.Optional.ofNullable(quantity).orElse(0);
~~~

`Optional.ofNullable` nhận null và cho một `Optional` rỗng; `orElse(0)` trả một `Integer` không null, sau đó Java unbox an toàn thành `int`. Cách này không cần thiết cho mọi đường tính toán; kiểm tra null trực tiếp thường rõ ràng và nhẹ hơn.

### 8.2. Null là lỗi dữ liệu, phải từ chối

~~~java
import java.util.Objects;

Integer amount = null;

// Khi gọi và amount null, lỗi xảy ra sớm với thông báo có chủ đích:
int required = Objects.requireNonNull(amount, "amount must not be null");
~~~

`requireNonNull` ném `NullPointerException` với thông điệp chỉ định khi giá trị null. Nếu ứng dụng muốn một loại ngoại lệ khác, có thể kiểm tra và ném `IllegalArgumentException` hoặc exception miền nghiệp vụ ở đúng ranh giới dữ liệu.

Điều quan trọng là **phát hiện null sớm**, thay vì để exception xuất hiện sâu trong phép cộng hoặc một biểu thức `?:` phức tạp rồi khó lần ra nguồn.

### 8.3. Null là trạng thái có nghĩa, phải giữ lại

~~~java
Integer measured = null;

Integer selected = measured == null
        ? null
        : Integer.valueOf(measured + 1);

// selected là null; không ép thành 0.
~~~

Nếu API trả `Integer` nullable, giữ nguyên khả năng trả null thay vì ép sang `int`. Với kết quả cuối chỉ có thể là số hoặc không có số, một `OptionalInt` cũng có thể diễn đạt ý nghĩa vắng mặt một cách rõ ràng:

~~~java
java.util.OptionalInt maybeValue = measured == null
        ? java.util.OptionalInt.empty()
        : java.util.OptionalInt.of(measured);
~~~

`OptionalInt` không chứa null bên trong, nhưng người gọi phải xử lý trạng thái không có giá trị qua `isPresent()`, `orElse(...)`, `orElseThrow()`... Không coi `OptionalInt.empty()` tương đương số 0 nếu nghiệp vụ không nói vậy.

### 8.4. Boolean ba trạng thái

~~~java
Boolean answer = null;

if (answer == null) {
    System.out.println("unknown");
} else if (answer) {
    System.out.println("yes");
} else {
    System.out.println("no");
}
~~~

Chỉ sau khi kiểm tra khác null mới được unbox trong `else if`. Nếu nghiệp vụ cho phép null được xem là false, `Boolean.TRUE.equals(answer)` là một thành ngữ gọn. Nếu nghiệp vụ phân biệt chưa trả lời và từ chối, dùng ba nhánh như ví dụ.

## 9. Chú ý ranh giới dữ liệu: List, Map, Stream và Optional

Unboxing null không chỉ xuất hiện trong các biểu thức nhìn thấy ngay ở một dòng mã. Nó còn thường xảy ra khi dữ liệu được đưa vào API yêu cầu primitive.

### 9.1. List<Integer> và Stream

~~~java
import java.util.Arrays;
import java.util.List;

List<Integer> values = Arrays.asList(1, null, 3);

// NullPointerException khi terminal operation thực sự duyệt tới phần tử null:
// int sum = values.stream().mapToInt(Integer::intValue).sum();

int safeSum = values.stream()
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .sum(); // 4
~~~

`mapToInt(Integer::intValue)` yêu cầu unbox wrapper và xảy ra khi pipeline được thực thi (vì Stream intermediate operations là lazy). Phải quyết định rõ null nghĩa là "bỏ qua", "coi là 0" hay "lỗi đầu vào"; đoạn `filter(Objects::nonNull)` chỉ phù hợp khi việc bỏ qua phần tử rỗng là đúng.

Lưu ý `List.of(1, null, 3)` tự nó đã không cho phép null và ném `NullPointerException` khi tạo list; ví dụ sử dụng `Arrays.asList` để mô tả tình huống một collection thực sự chứa null.

### 9.2. Map.get hoặc getOrDefault

~~~java
import java.util.HashMap;
import java.util.Map;

Map<String, Integer> counts = new HashMap<>();
counts.put("A", null);

// int value = counts.get("A"); // NPE vì unboxing null.
~~~

`Map.getOrDefault(key, defaultValue)` **không phải luôn là biện pháp thay null**: nếu key tồn tại và được gắn với giá trị null, nó có thể trả null (với `HashMap`). Ví dụ:

~~~java
// Map<String, Integer> counts như ở trên.
// int value = counts.getOrDefault("A", 0); // vẫn có thể NPE.
~~~

Nếu ứng dụng cho phép `null` là giá trị có nghĩa, xử lý rõ ràng:

~~~java
Integer count = counts.get("A");
int value = count == null ? 0 : count;
~~~

Nếu cần phân biệt "không có key" và "key có giá trị null", dùng `containsKey` cùng `get` hoặc thiết kế lại cấu trúc dữ liệu cho phù hợp.

### 9.3. Optional không tự bảo đảm unboxing an toàn

~~~java
import java.util.Optional;

Optional<Integer> maybe = Optional.empty();

// int value = maybe.orElse(null); // NPE nếu cố gán null vào int.
int safeValue = maybe.orElse(0);
~~~

`Optional<Integer>` diễn đạt khả năng không có wrapper. Nhưng kết quả của `orElse(null)` vẫn có thể là null; nếu được unbox sau đó, lỗi vẫn xuất hiện. Chọn `OptionalInt` khi API cần biểu diễn trực tiếp "có hoặc không có một giá trị int" mà không cần wrapper ở mỗi bước.

## 10. Trường hợp điều kiện phức tạp và thứ tự đánh giá

Các biểu thức có nhiều nhánh dễ che giấu một phép unbox nằm sâu bên trong:

~~~java
Integer discount = null;
boolean vip = true;
boolean promotion = false;

// Ví dụ cần kiểm tra kiểu và null trước khi viết code phức tạp:
// int applied = vip ? discount : (promotion ? 5 : 0);
~~~

Nếu `vip` là true, nhánh `discount` được chọn; do biểu thức số đòi primitive, null có thể bị unbox. Dù nhánh còn lại không chạy, điều đó không làm biểu thức an toàn.

Thay vào đó, làm rõ chính sách:

~~~java
Integer discount = null;
boolean vip = true;
boolean promotion = false;

int applied;
if (vip) {
    if (discount == null) {
        throw new IllegalStateException("VIP discount is missing");
    }
    applied = discount;
} else {
    applied = promotion ? 5 : 0;
}
~~~

Khi điều kiện nghiệp vụ phức tạp, một khối `if/else` với kiểm tra null tường minh thường dễ hiểu và dễ review hơn lồng nhiều biểu thức `?:`, nhất là khi các nhánh có kiểu wrapper/primitive khác nhau.

### Toán tử short-circuit giúp tránh phép unbox không cần thiết

~~~java
Integer age = null;

boolean adult = age != null && age >= 18; // false, không NPE
boolean missingOrAdult = age == null || age >= 18; // true, không NPE
~~~

Ở ví dụ đầu, vế phải `age >= 18` chỉ chạy khi `age != null`. Ở ví dụ sau, vế phải chỉ chạy khi `age == null` là false. Mẫu kiểm tra này an toàn nhờ quy tắc đánh giá ngắn mạch; thay nhầm `&&` thành `&` hoặc `||` thành `|` có thể làm mất khả năng bảo vệ.

## 11. Quy tắc rà soát mã để phát hiện unboxing null

Khi review đoạn Java có wrapper nullable, hãy lần theo **kiểu tĩnh** của từng toán hạng, không chỉ nhìn kiểu khai báo biến đích.

Một checklist ngắn:

1. **Nguồn nullable:** trường entity/DTO, kết quả `Map.get`, parse dữ liệu thiếu, cột database có null, phương thức trả wrapper có thể null.
2. **Đích primitive:** gán `Integer → int`, truyền vào phương thức `method(int)`, trả về từ hàm khai báo `int`.
3. **Toán tử yêu cầu primitive:** `+`, `-`, `*`, `/`, so sánh `<`/`>`, toán tử tăng/giảm và compound assignment.
4. **Điều kiện logic:** `if(Boolean)`, `while(Boolean)`, `Boolean && ...`, `Boolean ? ... : ...`.
5. **Toán tử `?:`:** khi trộn wrapper số và primitive số, kiểm tra kiểu của **toàn bộ biểu thức**. Việc kết quả được gán cho `Integer`/`Object` không nhất thiết tránh unboxing.
6. **API chuyển đổi:** `Integer::intValue`, `mapToInt`, `Collectors.summingInt`, `Math.max` và các overload yêu cầu primitive.
7. **Kiểm thử cả hai nhánh:** điều kiện true/false và các giá trị null/non-null; lỗi hiếm thường nằm ở nhánh không được chạy trong test đơn giản.

### Kiểm thử bằng ca nhỏ nhất

Để xác nhận một biểu thức có rủi ro, viết test với tất cả tổ hợp có ý nghĩa:

| Điều kiện | Giá trị wrapper | Điều cần kiểm tra |
|---|---|---|
| true | khác null | Kết quả nhánh đầu đúng |
| true | null | Có NPE hoặc null được giữ lại theo hợp đồng? |
| false | khác null | Nhánh thứ hai và kiểu kết quả |
| false | null | Nhánh null có bị bỏ qua thật không? |
| `Boolean` null làm điều kiện | bất kỳ | Có NPE từ phần điều kiện trước khi chọn nhánh? |

Không nên chỉ test "happy path" với `Integer.valueOf(5)`. Phải có test chạy thực sự với `null` và kiểm tra đúng kiểu exception hoặc kết quả mặc định.

## 12. Sai lầm thường gặp và cách thay thế

| Cách viết / suy nghĩ sai | Vấn đề | Hướng xử lý |
|---|---|---|
| `Integer n = flag ? nullable : 0;` chắc chắn không NPE vì kết quả là Integer | Nhánh `Integer`/`int` có thể unbox `nullable` | Kiểm tra null hoặc dùng hai nhánh wrapper nếu muốn giữ null |
| `Object o = flag ? nullable : 0;` chắc chắn giữ được null | Kiểu biểu thức được quyết định trước khi gán vào Object | Xác định kiểu của `?:`, sửa kiểu nhánh |
| `Boolean b = null; if (b) ...` | Điều kiện đòi `boolean` | `Boolean.TRUE.equals(b)` hoặc kiểm tra ba trạng thái |
| `n == 0` an toàn nếu n là Integer | So với primitive làm unbox null | `Objects.equals(n, 0)` hoặc kiểm tra null |
| `n.equals(0)` là null-safe | Gọi phương thức trên null gây NPE | `Objects.equals(n, 0)` |
| `n++` không thể NPE vì chỉ tăng wrapper | Tăng yêu cầu unbox giá trị cũ | Khởi tạo giá trị, kiểm tra null |
| `mapToInt(Integer::intValue)` tự bỏ qua null | Pipeline unbox từng phần tử khi tiêu thụ | Lọc hoặc báo lỗi rõ theo hợp đồng |
| `map.getOrDefault(k, 0)` luôn khác null | Map có key gắn với null vẫn có thể trả null | Đọc vào wrapper, kiểm tra null |
| `Optional.ofNullable(n).orElse(null)` là safe | Có thể trả null rồi bị unbox | Chọn default không null hoặc giữ Optional |
| Thay mọi null thành 0 | Mất ý nghĩa "thiếu" hay "chưa xác định" | Xác lập chính sách xử lý null |
| Dùng `==` để so giá trị hai Integer | Hai wrapper có thể so tham chiếu | `Objects.equals` cho so giá trị null-safe |
| Chỉ test với điều kiện false | Không kiểm tra đường nhánh null được chọn | Test true/false với null và non-null |

## 13. Bài tập tự kiểm tra

**Câu 1.** Điều gì xảy ra?

~~~java
Integer n = null;
int r = n + 10;
~~~

**Đáp án:** `NullPointerException` khi Java unbox `n` để thực hiện phép cộng `int`.

**Câu 2.** Tại sao mã này có thể ném NPE dù kiểu biến nhận là `Integer`?

~~~java
Integer n = null;
boolean flag = true;
Integer r = flag ? n : 5;
~~~

**Đáp án:** hai nhánh có kiểu `Integer` và `int`; biểu thức điều kiện số có kiểu `int`, nên nhánh `n` được unbox khi chọn. `flag` true, `n` null, dẫn đến NPE trước bước boxing kết quả về `Integer`.

**Câu 3.** Đổi `5` thành `Integer.valueOf(5)` trong câu 2 thì sao?

**Đáp án:** Hai nhánh đều kiểu `Integer`, nên kết quả có thể là tham chiếu `null` và phép gán vào `Integer` không NPE. NPE vẫn có thể xuất hiện nếu unbox kết quả sau đó.

**Câu 4.** `Boolean approved = null; boolean ok = approved != null && approved;` trả kết quả nào?

**Đáp án:** `false`, không NPE. Vế phải không chạy vì `approved != null` false.

**Câu 5.** Với `Integer a = null; Integer b = 2;`, so sánh `a == b` và `a == 2` khác nhau thế nào?

**Đáp án:** `a == b` so sánh tham chiếu wrapper, trả false, không cần unbox. `a == 2` so với primitive `int`, unbox `a` và gây NPE. Khi muốn so giá trị, nên dùng `Objects.equals(a, b)` thay vì so nhận dạng wrapper.

**Câu 6.** Vì sao đoạn code `String s = "n=" + nullableInteger;` có thể chạy khi `nullableInteger` null?

**Đáp án:** Toán tử `+` với chuỗi nối chuỗi từ tham chiếu, có thể tạo `"n=null"`; đây không phải phép cộng số học cần unbox.

**Câu 7.** Một DTO chứa `Integer quantity` có thể null. API yêu cầu kết quả là `int`. Nên làm gì?

**Đáp án:** Xác định null mang nghĩa gì: mặc định 0, lỗi dữ liệu hay thiếu thông tin phải chuyển lên tầng gọi. Sau đó kiểm tra null trước khi unbox hoặc thay mô hình dữ liệu/kiểu trả về. Không tự gán 0 nếu nghiệp vụ chưa quy định.

**Câu 8.** `Optional<Integer>` có bảo đảm biểu thức `int x = optional.orElse(null);` chạy an toàn không?

**Đáp án:** Không. Nếu Optional rỗng thì `orElse(null)` trả null và phép gán vào `int` cố unbox null, dẫn đến NPE.

---

**Ghi nhớ:** `null` hợp lệ đối với wrapper nhưng không thể trở thành primitive bằng unboxing. Phép số học, so sánh, điều kiện `Boolean`, API nhận primitive và toán tử `?:` trộn wrapper/primitive đều có thể kích hoạt unboxing ẩn. **Kiểm tra kiểu của biểu thức trước khi gán**, kiểm tra null tại ranh giới và chọn chính sách xử lý null đúng nghiệp vụ thay vì mặc định coi null bằng 0.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-006">So sánh số thực bằng một epsilon tùy tiện</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: so sánh số thực bằng một epsilon tùy tiện

Khi lập trình với `double` hoặc `float`, nhiều người thay phép so sánh `a == b` bằng `Math.abs(a - b) < 1e-9` và cho rằng như vậy đã giải quyết mọi sai số dấu phẩy động. Đó là một sai lầm: **không tồn tại một epsilon tuyệt đối duy nhất phù hợp cho mọi giá trị, phép toán, đơn vị đo và yêu cầu nghiệp vụ**. Ngưỡng quá nhỏ khiến những kết quả đáng ra được coi là tương đương bị từ chối; ngưỡng quá lớn làm những giá trị thực sự khác nhau bị coi là bằng nhau. Với `NaN`, vô cực, số cực lớn hoặc phép trừ bị overflow, công thức đơn giản còn mang những cạm bẫy khác.

Bài học thuộc nhóm **Sai lầm thường gặp** của Java Core - Numbers. Mục tiêu là hiểu gốc rễ sai số IEEE 754, phân biệt so sánh **chính xác** với so sánh **gần bằng theo một ngưỡng có ý nghĩa**, dùng sai số tuyệt đối/tương đối/ULP đúng hoàn cảnh, xử lý dữ liệu đặc biệt và biết khi nào phải chọn `BigDecimal`.

## 1. Vì sao 0.1 + 0.2 có thể không bằng 0.3?

`float` và `double` là các kiểu dấu phẩy động nhị phân theo chuẩn IEEE 754. Phần lớn phân số thập phân như 0.1, 0.2 không có biểu diễn hữu hạn chính xác trong hệ cơ số 2. Java lưu giá trị nhị phân gần nhất có thể biểu diễn; mỗi phép tính có thể làm tròn theo quy tắc của kiểu số.

~~~java
double a = 0.1 + 0.2;
double b = 0.3;

System.out.println(a);      // 0.30000000000000004
System.out.println(b);      // 0.3
System.out.println(a == b); // false
~~~

Đây là đoạn minh họa dùng bên trong một phương thức Java. Kết quả `a == b` không sai theo ngôn ngữ: nó kiểm tra sự bằng nhau của **hai giá trị `double` đang được biểu diễn**, không kiểm tra "hai công thức toán học lý tưởng trên giấy có ý nghĩa tương đương hay không".

Một số phép tính tạo được kết quả chính xác, ví dụ những số nguyên nhỏ hoặc các phân số có mẫu là lũy thừa của 2. Vì vậy cũng không đúng khi khẳng định rằng `double` "không bao giờ dùng được `==`". Vấn đề là **định nghĩa bằng nhau mong muốn** và **nguồn gốc của từng giá trị**.

## 2. Epsilon tùy tiện gây hai loại lỗi trái ngược

Cách viết phổ biến:

~~~java
static boolean almostEqualWrong(double a, double b) {
    return Math.abs(a - b) < 1e-9;
}
~~~

Đoạn phương thức trên dùng một ngưỡng **sai số tuyệt đối** là `1e-9` trong đơn vị của dữ liệu. Nó có thể thích hợp nếu hệ thống **có hợp đồng độ chính xác tương ứng**; nó không tự nhiên đúng cho mọi bài toán.

**Lỗi 1 — ngưỡng quá chặt:** Giả sử hai kết quả tính lớn khác nhau khoảng `0.5` đơn vị do quá trình xấp xỉ đã được nghiệm thu cho phép sai số tối đa một đơn vị. Epsilon `1e-9` sẽ báo "khác nhau", dù yêu cầu miền ứng dụng có thể coi chúng tương đương.

**Lỗi 2 — ngưỡng quá rộng:** Nếu đang so sánh mức chênh lệch `1e-10` của một cảm biến có khả năng phân biệt đến `1e-12`, ngưỡng `1e-9` lại xóa đi một khác biệt có ý nghĩa. Tương tự, một ngưỡng tuyệt đối `0.01` được dùng để so sánh số tiền có thể vô tình xem hai khoản chênh nhau nhỏ hơn một xu là bằng nhau dù hợp đồng thanh toán yêu cầu giá trị chính xác.

Ngưỡng nào "rộng" hay "chặt" phụ thuộc ít nhất vào:
- **Đơn vị và độ lớn:** mét, micromét, giây, phần trăm hay đồng tiền không chung một mức sai số tuyệt đối hợp lý.
- **Sai số đầu vào:** độ phân giải thiết bị, phép đo hoặc nguồn dữ liệu.
- **Chuỗi phép toán:** số lần cộng/trừ/nhân/chia, độ điều kiện của bài toán, hiện tượng triệt tiêu chữ số (cancellation), nguy cơ overflow/underflow.
- **Hậu quả khi phân loại sai:** chỉ vẽ biểu đồ, cảnh báo cảm biến, quyết định phân quyền, tính tiền hay kiểm tra điều kiện dừng thuật toán.

**Một epsilon phải là phần của yêu cầu, không phải một con số chọn vì thấy quen thuộc trên mạng.**

## 3. Ba khái niệm cần tách riêng: machine epsilon, tolerance và ULP

| Thuật ngữ | Ý nghĩa | Cạm bẫy |
|---|---|---|
| Machine epsilon (với `double` là khoảng `2.22 × 10^-16` quanh 1.0) | Độ giãn biểu diễn tương đối quanh 1.0 của số dấu phẩy động nhị phân | Không phải ngưỡng đúng cho mọi phép toán |
| Absolute tolerance (`absTol`) | Chênh lệch tối đa tính theo **đơn vị dữ liệu** | Cần chọn theo ngữ cảnh, không scale theo độ lớn |
| Relative tolerance (`relTol`) | Tỷ lệ chênh lệch tối đa so với độ lớn tham chiếu | Cần chính sách gần 0 và tránh cho phép khác biệt lớn một cách vô lý |
| ULP (*unit in the last place*) | Khoảng cách lân cận giữa những giá trị có thể biểu diễn ở một thang độ lớn | Đo độ phân giải máy tại độ lớn đó, không đo độ đúng nghiệp vụ |

`Math.ulp(1.0)` trả khoảng `2.220446049250313E-16`; `Math.ulp(0.0)` trả `Double.MIN_VALUE` (khoảng `4.9E-324`, số dương *nhỏ nhất khác 0* của `double`). `Double.MIN_VALUE` **không** phải số âm nhỏ nhất: tên này dễ gây nhầm.

Không suy rằng một sai số bằng 1 hoặc 2 ULP luôn chấp nhận được. Một phép tính kém điều kiện có thể sai hơn nhiều ULP; ngược lại, hai số cách nhau chỉ một ULP cũng có thể là hai mã giá trị khác nhau cần giữ nguyên (ví dụ khóa dữ liệu hoặc trạng thái tính toán).

## 4. Sai số tuyệt đối: khi nào phù hợp?

Kiểm tra theo ngưỡng tuyệt đối:

\[
|a - b| \leq \varepsilon_{\text{abs}}
\]

`absTol` được đo cùng đơn vị với `a`, `b`. Ví dụ, một thiết bị đo chiều dài có hợp đồng **chấp nhận độ lệch không quá 0.01 mét** thì `absTol = 0.01` có thể là một quy tắc miền dữ liệu được lựa chọn có căn cứ.

~~~java
boolean within = Math.abs(actualMeters - targetMeters) <= 0.01;
~~~

Đoạn code trên là minh họa trong một phương thức; yêu cầu rằng `actualMeters` và `targetMeters` là các giá trị hữu hạn và `0.01` thật sự là sai lệch cho phép. Đừng chỉ áp dụng công thức này lên mọi `double` không kiểm tra đầu vào.

Ưu điểm: dễ hiểu, phù hợp gần 0 và có đơn vị rõ ràng. Hạn chế: một độ lệch `0.01` là lớn đối với phép đo cỡ `0.001` nhưng rất nhỏ đối với phép đo cỡ hàng tỷ đơn vị.

Với việc so sánh kết quả phép tính nội bộ, không nên chọn `absTol` chỉ vì số hiển thị có sáu chữ số thập phân. **Số chữ số người dùng muốn nhìn** và **mức sai số được phép của phép tính** là hai yêu cầu khác nhau.

## 5. Sai số tương đối: khi độ lớn thay đổi mạnh

Một tiêu chí thường dùng cho các giá trị khác 0 là:

\[
|a-b| \leq \varepsilon_{\text{rel}} \cdot \max(|a|, |b|)
\]

`relTol` là tỷ lệ không có đơn vị, ví dụ `1e-6` tương ứng khoảng một phần triệu của độ lớn tham chiếu. Nó điều chỉnh ngưỡng theo thang độ lớn.

Ví dụ `a = 1_000_000_000.5` và `b = 1_000_000_000.0`: chênh lệch tuyệt đối là `0.5`. Với `relTol = 1e-9`, ngưỡng tương đối xấp xỉ `1.0` nên chúng được xem là "gần bằng". Điều này **có thể hoàn toàn sai về nghiệp vụ** nếu khác biệt `0.5` là quan trọng. Do vậy, so sánh tương đối cũng không tự động đúng.

Gần 0, một tiêu chí thuần tương đối thường không đáp ứng mục tiêu: so sánh `0.0` với `1e-13` và `relTol = 1e-9` sẽ cho ngưỡng cỡ `1e-22`, dẫn tới "khác nhau". Nếu dụng ý là cho phép nhiễu đầu vào gần 0 với `absTol = 1e-12`, hãy kết hợp hai dạng.

## 6. Kết hợp ngưỡng tuyệt đối và tương đối có kiểm soát

Một tiêu chí phổ biến cho **giá trị `double` hữu hạn** là:

\[
|a-b| \leq \max\left(\varepsilon_{\text{abs}},
\varepsilon_{\text{rel}}\max(|a|,|b|)\right)
\]

Ý nghĩa: chênh lệch được chấp nhận khi **nằm trong ngưỡng tuyệt đối** hoặc **ngưỡng tương đối theo thang độ lớn**. Dùng `max` ở đây tương đương phép OR giữa hai phép thử sai số; không được nhầm với quy tắc bắt buộc đáp ứng *cả hai* ngưỡng (AND). Chọn OR hay AND là một quyết định miền dữ liệu; công thức này là **một chính sách gợi ý**, không phải định nghĩa bằng nhau toàn cục.

Ví dụ helper ở Java 17:

~~~java
static boolean close(double a, double b, double absTol, double relTol) {
    if (!Double.isFinite(absTol) || absTol < 0.0
            || !Double.isFinite(relTol) || relTol < 0.0
            || relTol >= 1.0) {
        throw new IllegalArgumentException("invalid tolerances");
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }
    if (a == b) {
        return true;
    }
    double difference = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return difference <= absTol || difference <= relTol * scale;
}
~~~

Đây là **đoạn phương thức** cần đặt trong một lớp Java. Ràng buộc `0 <= relTol < 1` là **chính sách an toàn của ví dụ** để không chấp nhận các ngưỡng tương đối cực lớn; không phải định nghĩa chung mà Java bắt buộc. Giá trị `absTol` và `relTol` được truyền từ yêu cầu hệ thống, không hard-code một epsilon bất kỳ trong helper.

Các lựa chọn đã được chủ động khai báo:
- `NaN` và cả hai loại vô cực bị từ chối. Nếu nghiệp vụ muốn `+Infinity` bằng chính nó, phải định nghĩa riêng trước khi dùng chính sách này.
- `+0.0` và `-0.0` được coi là bằng nhau (`a == b` là `true`). Nếu dấu của zero quan trọng, cần cách kiểm tra khác.
- Dùng `<=` để chấp nhận chính điểm ranh giới sai số. `<` là một quy tắc khác, không được đổi ngầm giữa kiểm thử và hệ thống.
- Với `absTol = 0`, `relTol = 0`, helper tương đương so sánh bằng nhau về số học cho giá trị hữu hạn, ngoại trừ việc nó vẫn từ chối `NaN` và vô cực.

**Giới hạn số học của công thức:** Nếu `a` và `b` rất lớn nhưng trái dấu, `a-b` có thể thành vô cực dù cả hai đầu vào hữu hạn; ví dụ `Double.MAX_VALUE - (-Double.MAX_VALUE)`. Với chính sách `relTol < 1` và `absTol` hữu hạn của helper, hai giá trị quá xa như vậy vẫn được phân loại "khác", nên phép trừ overflow không làm một kết quả sai được chấp nhận. Tuy nhiên, không coi helper này là thư viện so sánh số học bất biến cho mọi nhu cầu; nếu cần so sánh sai số tương đối cực kỳ chặt ở cả miền cực trị, hãy thiết kế thuật toán có kiểm soát overflow/underflow và đánh giá độ điều kiện.

## 7. Ví dụ chạy độc lập Java 17+

Tạo tệp `FloatingCompareDemo.java`:

~~~java
import java.math.BigDecimal;

public final class FloatingCompareDemo {
    static boolean close(double a, double b, double absTol, double relTol) {
        if (!Double.isFinite(absTol) || absTol < 0.0
                || !Double.isFinite(relTol) || relTol < 0.0
                || relTol >= 1.0) {
            throw new IllegalArgumentException("invalid tolerances");
        }
        if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
        if (a == b) return true;
        double difference = Math.abs(a - b);
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return difference <= absTol || difference <= relTol * scale;
    }

    public static void main(String[] args) {
        double calculated = 0.1 + 0.2;
        System.out.println("exact binary comparison = "
                + (calculated == 0.3));
        System.out.println("domain tolerance = "
                + close(calculated, 0.3, 1e-12, 1e-12));
        System.out.println("near zero = "
                + close(1e-13, 0.0, 1e-12, 1e-9));
        System.out.println("different measurements = "
                + close(1.001, 1.0, 1e-5, 1e-6));
        System.out.println("large relative = "
                + close(1e9 + 0.5, 1e9, 1e-6, 1e-9));
        System.out.println("large absolute only = "
                + close(1e9 + 0.5, 1e9, 1e-6, 0.0));
        System.out.println("NaN = "
                + close(Double.NaN, Double.NaN, 1e-12, 1e-9));
        System.out.println("infinities = "
                + close(Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, 1e-12, 1e-9));
        System.out.println("signed zeros = "
                + close(+0.0, -0.0, 0.0, 0.0));

        double next = Math.nextUp(1.0);
        System.out.println("adjacent = " + (next > 1.0));
        System.out.println("one ulp = "
                + ((next - 1.0) == Math.ulp(1.0)));

        System.out.println("nontransitive a,b = "
                + close(0.0, 0.75, 1.0, 0.0));
        System.out.println("nontransitive b,c = "
                + close(0.75, 1.5, 1.0, 0.0));
        System.out.println("nontransitive a,c = "
                + close(0.0, 1.5, 1.0, 0.0));

        BigDecimal money = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("decimal equality = "
                + money.equals(new BigDecimal("0.3")));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 FloatingCompareDemo.java
java FloatingCompareDemo
~~~

Kết quả được kiểm chứng:

~~~text
exact binary comparison = false
domain tolerance = true
near zero = true
different measurements = false
large relative = true
large absolute only = false
NaN = false
infinities = false
signed zeros = true
adjacent = true
one ulp = true
nontransitive a,b = true
nontransitive b,c = true
nontransitive a,c = false
decimal equality = true
~~~

Kết luận từ ví dụ:

- `0.1 + 0.2` khác `0.3` ở biểu diễn nhị phân nhưng có thể được coi là gần nhau nếu **mức sai số cho phép đã được xác định**.
- `1e9 + 0.5` và `1e9` vượt ngưỡng tuyệt đối `1e-6` nhưng nằm trong ngưỡng tương đối `1e-9`. Điều này chỉ đúng với chính sách của helper, không mặc nhiên đúng cho quyết định tài chính.
- Hai trường hợp `NaN` và vô cực được phân loại theo quy tắc rõ ràng thay vì để phép trừ sinh `NaN` gây kết quả ngầm.
- So sánh gần bằng có thể không bắc cầu; `0.0` gần `0.75` và `0.75` gần `1.5` nhưng `0.0` không gần `1.5`.

## 8. Gần bằng không có tính bắc cầu: đừng dùng làm equals của khóa

Đây là một trong những lý do quan trọng nhất để không dùng epsilon tùy ý ở mọi nơi.

Giả sử `absTol = 1.0` và `relTol = 0`:

~~~text
a = 0.00
b = 0.75
c = 1.50

close(a, b) == true  (chênh lệch 0.75)
close(b, c) == true  (chênh lệch 0.75)
close(a, c) == false (chênh lệch 1.50)
~~~

Trong toán học, **quan hệ tương đương** phải có tính phản xạ, đối xứng và bắc cầu. Quan hệ “cách nhau không quá epsilon” nhìn chung **không bắc cầu**, vì vậy nó không phải một định nghĩa `equals()` hợp lệ cho các đối tượng dùng làm khóa.

Hậu quả nếu dùng `close(a,b)` làm `equals()` của một lớp:

- Không có cách thông thường nào để xây dựng `hashCode()` vừa hữu ích vừa giữ hợp đồng hash/equality cho các “lân cận” chồng nhau trên toàn miền.
- `HashSet`/`HashMap` có thể cho kết quả không ổn định theo thứ tự chèn, vi phạm kỳ vọng về quan hệ bằng nhau.
- Một `Comparator` trả 0 cho những cặp “gần” nhau có thể vi phạm tính nhất quán và các quy tắc sắp thứ tự mà `TreeSet`/`TreeMap` dựa vào.

**Giải pháp:** nếu cần gom nhóm các giá trị theo độ phân giải, định nghĩa một **quy tắc lượng tử hóa/nhóm thành bucket có biên cố định** rõ ràng, rồi so sánh khóa bucket theo quan hệ tương đương thực sự; các điểm gần ranh giới bucket có thể rơi vào nhóm khác, vì vậy phải nêu rõ semantics. Đừng biến ngưỡng gần bằng vốn không bắc cầu thành `equals` toàn cục.

Khi chỉ dùng để *kiểm tra một kết quả số học đơn lẻ* với kỳ vọng đã định nghĩa, hàm `close` lại rất hữu ích. Tính phù hợp phụ thuộc **vai trò của phép so sánh**.

## 9. NaN, Infinity và số 0 có dấu

IEEE 754 có những giá trị đặc biệt; một epsilon tùy tiện không xử lý chúng đúng cho mọi tình huống.

| Giá trị | Với toán tử số học Java | Điều cần quyết định |
|---|---|---|
| `Double.NaN` | `NaN == NaN` là `false`; `NaN < x` và `NaN > x` đều `false` | Từ chối, biểu diễn dữ liệu thiếu hay có quy tắc riêng? |
| `+Infinity` và `-Infinity` | Vô cực cùng dấu `==` nhau, trừ/chia có thể tạo vô cực/`NaN` | Có cho phép vô cực trong miền nghiệp vụ không? |
| `+0.0` và `-0.0` | `+0.0 == -0.0` là `true` | Dấu zero có mang ý nghĩa hay không? |
| Hai giá trị `double` lớn trái dấu | `a - b` có thể thành Infinity vì overflow | Tránh suy luận vội từ một phép trừ |
| Số rất nhỏ gần 0 | Có giá trị subnormal và rounding/underflow | Ngưỡng tuyệt đối hoặc thiết kế thuật toán tương ứng |

Không làm điều sau mà chưa xác định chính sách:

~~~java
return Math.abs(a - b) <= epsilon;
~~~

Nếu `a` và `b` đều là `+Infinity`, `a - b` là `NaN`; phép so sánh với epsilon sẽ là `false`, dù `a == b` trả `true`. Nếu `NaN` xuất hiện trong tính toán, việc chỉ nhận `false` âm thầm có thể che khuất nguồn lỗi; tùy ứng dụng nên kiểm tra và báo dữ liệu không hợp lệ.

Với mục tiêu **sắp xếp/khóa collection**, `Double.compare(a,b)` tạo thứ tự toàn phần theo quy tắc của Java: `-0.0` đứng trước `+0.0`, và `NaN` được xếp sau vô cực dương. Điều này khác với `a == b` (coi hai zero bằng nhau nhưng không coi `NaN` bằng chính nó). `Double.equals()` của wrapper cũng định nghĩa ngữ nghĩa phù hợp cho hash collections. Không thay comparator sắp xếp bằng kiểm tra epsilon thiếu tính bắc cầu.

## 10. Math.ulp và Math.nextUp: đo khoảng cách biểu diễn

`Math.ulp(x)` trả kích thước một đơn vị ở vị trí chữ số có trọng số thấp nhất (*unit in the last place*) quanh giá trị `x`, phụ thuộc thang độ lớn. `Math.nextUp(x)` trả giá trị `double` kế tiếp hướng tới dương vô cực.

~~~java
double x = 1.0;
double next = Math.nextUp(x);

System.out.println(next > x);              // true
System.out.println(next - x == Math.ulp(x)); // true
~~~

Gần `1.0`, khoảng cách giữa hai số `double` liền kề khoảng `2.22e-16`. Ở độ lớn khác, khoảng cách có thể lớn hơn hoặc nhỏ hơn. Ví dụ quanh `1e16`, không thể đại diện mọi số nguyên liền kề bằng `double`: bước giữa các giá trị có thể biểu diễn đã lớn hơn 1.

ULP có giá trị khi mục tiêu là đánh giá **chất lượng số học của một thuật toán dấu phẩy động** so với kết quả tham chiếu cùng kiểu, đặc biệt khi bạn hiểu quy tắc rounding và miền đầu vào.

Tuy nhiên:
- Một thuật toán tích lũy sai số qua nhiều bước có thể khác kết quả lý tưởng nhiều hơn vài ULP; không chọn “4 ULP” chỉ vì thấy trong mẫu test.
- Hai giá trị rất nhỏ khác nhau vài ULP có thể nhỏ hơn độ phân giải đo lường thực tế nhiều bậc.
- ULP không thay thế việc định nghĩa sai số nghiệp vụ; giữa sai số máy và sai số của phép đo/dữ liệu còn nhiều tầng khác.
- Nếu áp dụng kiểm tra khoảng cách theo bit representation, phải xử lý cẩn thận số âm, signed zero, `NaN` và vô cực. Không chuyển `double` sang `long` bằng ép kiểu rồi trừ hai `long` để tính ULP distance.

ULP là công cụ *chẩn đoán và lượng hóa độ phân giải máy*, không phải một epsilon phổ quát khác.

## 11. Sai số và độ điều kiện của thuật toán

Không chỉ **cách lưu số** mà cả **cấu trúc phép tính** ảnh hưởng sai số. Ví dụ, trừ hai số gần bằng có thể khiến phần kết quả còn lại mang sai số tương đối lớn vì triệt tiêu chữ số có nghĩa (*catastrophic cancellation*) khi dữ liệu đầu vào đã bị làm tròn.

~~~java
double x = 1e16;
double y = x + 1.0;

System.out.println(x == y); // true
~~~

Ở thang `1e16`, thêm 1 có thể không đổi biểu diễn `double` do bước ULP đã lớn hơn 1. Nếu sau đó tính `y - x`, kết quả 0 không chứng minh sự thay đổi toán học ban đầu không tồn tại; thông tin đã bị mất khi biểu diễn `y`. Không một lựa chọn epsilon nào có thể **khôi phục chữ số đã mất**.

Cách cải thiện tùy bài toán:
- Chọn thuật toán ổn định số học hơn, ví dụ công thức giảm sự trừ hai số gần nhau, dùng `Math.fma` khi giảm được lỗi làm tròn ở phép nhân-cộng.
- Thay đổi thang đo/chuẩn hóa đơn vị khi thích hợp, nhưng phải giữ diễn giải nghiệp vụ.
- Sử dụng độ chính xác cao hơn hoặc `BigDecimal` nếu yêu cầu tính thập phân chính xác và hiệu năng cho phép.
- Ước lượng/chứng minh sai số từ số bước và đặc tính dữ liệu thay vì chọn một con số “cảm giác an toàn”.

Với phép thử hồi quy thuật toán số, **expected value** thường cần lấy từ một nguồn tham chiếu có chất lượng tốt hơn phép tính cần kiểm tra, và tolerance phải gắn với độ chính xác mong đợi của thuật toán.

## 12. Khi nào dùng ==, Double.compare hoặc BigDecimal?

| Nhu cầu | Lựa chọn thích hợp | Vì sao |
|---|---|---|
| Kiểm tra một cờ/trạng thái encoded bằng các giá trị `double` chính xác đã định nghĩa | `==`, sau khi có chính sách `NaN`/zero rõ ràng | Không cần tự ý gộp các giá trị lân cận |
| Kiểm tra kết quả xấp xỉ của một phép toán với sai số được phép | `close(a,b,absTol,relTol)` theo miền dữ liệu | Chọn ngưỡng từ yêu cầu |
| Sắp xếp `Double` và làm khóa sorted collection | `Double.compare`/comparator nhất quán | Giữ quy tắc thứ tự hợp lệ |
| Số lượng, ID, timestamp dạng nguyên trong giới hạn kiểu số nguyên | `int`/`long` hoặc kiểu phù hợp | Tránh đưa bài toán nguyên sang dấu phẩy động không cần thiết |
| Tiền/định mức decimal phải chính xác | `BigDecimal` từ chuỗi hoặc số nguyên đơn vị nhỏ nhất | Biểu diễn thập phân và rounding có chính sách rõ ràng |
| Kiểm tra chất lượng hàm số học gần giới hạn khả năng của `double` | ULP + phân tích sai số/độ điều kiện | Đánh giá gần mức biểu diễn máy |

Ví dụ tính thập phân:

~~~java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal expected = new BigDecimal("0.3");

System.out.println(a.add(b).equals(expected)); // true
~~~

`BigDecimal` không phải tự động bảo đảm mọi phép toán vô hạn được biểu diễn chính xác: `divide` có thể yêu cầu `RoundingMode` và `MathContext`. Ngoài ra, `BigDecimal.equals()` còn kiểm tra `scale`: `2.0` không `equals()` với `2.00`, dù `compareTo() == 0`. Chọn ngữ nghĩa bằng nhau phù hợp.

Tránh `new BigDecimal(0.1)` nếu muốn decimal chính xác 0.1: constructor nhận `double` giữ giá trị nhị phân thực có thể không đúng biểu diễn thập phân mong muốn. Dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` theo nguồn dữ liệu.

## 13. Đặt epsilon trong unit test như thế nào?

Trong kiểm thử phép tính số, không chỉ chọn epsilon để “test xanh”. Một test tốt bao gồm:

1. **Giải thích độ chính xác kỳ vọng:** sai số đến từ biểu diễn nhị phân, thuật toán hay độ chính xác của nguồn dữ liệu?
2. **Tách absolute và relative tolerance:** với kết quả có thể gần 0, một ngưỡng tuyệt đối có căn cứ thường cần thiết; với kết quả trải nhiều bậc độ lớn, kiểm tra relative tolerance.
3. **Kiểm tra trường hợp biên:** gần 0, số rất lớn/nhỏ, số âm, signed zero, `NaN`, vô cực, phép tính ở vùng cancellation.
4. **Kiểm tra các giá trị phải phân biệt:** tránh tăng epsilon chỉ để những case sai bị coi là "đúng".
5. **Không dùng gần bằng làm invariant collection:** nếu kiểm thử cấu trúc dữ liệu yêu cầu duy nhất/sắp thứ tự, định nghĩa semantics khóa/Comparator rõ ràng.
6. **Báo sai số thực tế khi fail:** in `actual`, `expected`, `difference`, ngưỡng cho phép và unit để dễ chẩn đoán.

Ví dụ một bài toán đo độ dài quy định dung sai tối đa `0.001 m`, viết test với `absTol = 0.001` có giải thích “dung sai đo một milimét”. Một phép tính xấp xỉ tương đối `1e-8` có thể dùng `relTol = 1e-8` nếu đã được chứng minh/kiểm định cho miền dữ liệu; không lấy cả hai con số từ những bài toán khác rồi ghép ngẫu nhiên.

Với các framework test hỗ trợ assertion kiểm tra `double` kèm `delta`, cần đọc kỹ hợp đồng: `delta` thường là một sai số **tuyệt đối**, không tự thực hiện chính sách tương đối/phối hợp. Tự đổi framework assertion không giải quyết nguồn gốc lựa chọn ngưỡng.

## 14. Những lỗi phổ biến cần loại bỏ

1. **Hard-code `1e-9` ở mọi nơi:** không có ngưỡng tuyệt đối nào phù hợp tất cả miền giá trị.
2. **Dùng `Double.MIN_VALUE` làm epsilon chung:** đây là số dương `double` nhỏ nhất, không phải sai số rounding thông thường gần 1.0.
3. **Lấy `Math.ulp(1.0)` áp cho mọi số:** khoảng biểu diễn phụ thuộc độ lớn; nó cũng không phản ánh yêu cầu nghiệp vụ.
4. **Chỉ dùng sai số tương đối cho giá trị gần 0:** ngưỡng thu nhỏ về 0 và có thể không nhận sai số tuyệt đối có ý nghĩa.
5. **Không kiểm tra `NaN`/vô cực:** `Math.abs(a-b)` có thể là `NaN` hoặc `Infinity`; phép so sánh boolean chỉ trở thành false mà không nói vì sao.
6. **Lấy `==` hoặc `Double.compare()` thay cho kiểm tra xấp xỉ:** hai API ấy có semantics riêng; so sánh exact không diễn đạt sai số được phép.
7. **Dùng so sánh gần bằng làm `equals()`/`Comparator`:** tính bắc cầu không được bảo đảm; có thể phá hợp đồng map/set.
8. **Coi tolerance là cách sửa thuật toán kém ổn định:** epsilon không phục hồi dữ liệu bị mất do cancellation/rounding.
9. **Đổi đơn vị nhưng giữ nguyên ngưỡng tuyệt đối:** `0.001 m` phải được đổi thành `1 mm` khi biến dữ liệu từ mét sang milimét, nếu thực sự là cùng dung sai.
10. **Bỏ qua overflow trong phép trừ:** hai toán hạng hữu hạn rất lớn trái dấu có thể cho hiệu vô cực; cần đánh giá trường hợp đặc biệt và miền đầu vào.
11. **Đánh đồng độ chính xác hiển thị với độ chính xác tính toán:** in 2 chữ số sau dấu phẩy không chứng minh phép tính chỉ sai ít hơn `0.005`.
12. **Chọn epsilon bằng cách tăng dần cho tới khi test qua:** điều đó có thể che bug hoặc thay đổi sai lệch mà đáng ra phải phát hiện.

## 15. Bài tập tự kiểm tra

**Bài 1 — epsilon quá rộng**

Một phép đo khác nhau `0.0005` mét và yêu cầu chính thức chỉ cho phép chênh `0.0001` mét. Dùng `Math.abs(a-b) < 0.001` có phù hợp không?

**Đáp án:** Không. Ngưỡng 0.001 mét quá rộng so với dung sai quy định 0.0001 mét; nó nhận một giá trị sai. Cần dùng ngưỡng đúng theo hợp đồng, kiểm tra tình huống biên và đơn vị.

**Bài 2 — ngưỡng tương đối và số lớn**

`a = 1e9 + 0.5`, `b = 1e9`. Theo `relTol = 1e-9` và `absTol = 1e-6` trong helper của mục 6, hai số có gần bằng không?

**Đáp án:** Có, vì ngưỡng tương đối khoảng 1.0 lớn hơn chênh lệch 0.5. Điều đó chỉ phản ánh **chính sách cho phép 1 phần tỷ theo độ lớn**, không phải xác nhận hai số phải được coi là tương đương cho mọi nghiệp vụ.

**Bài 3 — tính bắc cầu**

Với ngưỡng tuyệt đối 1, hãy so `0.0`, `0.75`, `1.5`. Vì sao không được dùng quy tắc đó để viết `equals()`?

**Đáp án:** Hai cặp kề nhau "gần" nhưng cặp ngoài cùng không "gần". Quan hệ không bắc cầu, nên không phải quan hệ tương đương thích hợp cho `equals()` hoặc các khóa collection.

**Bài 4 — NaN**

`Double.NaN == Double.NaN` là `true` hay `false`? Nếu muốn từ chối dữ liệu không hợp lệ trước so sánh gần bằng, kiểm tra gì?

**Đáp án:** `false`. Sử dụng `Double.isFinite(a)`/`Double.isFinite(b)` nếu quy tắc phải từ chối cả `NaN` lẫn hai loại vô cực.

**Bài 5 — rounding và exact equality**

Một hệ thống tiền tệ yêu cầu số thập phân chính xác từ chuỗi `"0.1"` và `"0.2"`. Có nên lấy `double` rồi thêm epsilon?

**Đáp án:** Không cần. Dùng `BigDecimal` tạo từ chuỗi, xác định `scale` và `RoundingMode` theo hợp đồng khi cần. `BigDecimal` giúp mô tả chính xác dữ liệu decimal; nó không thay thế việc thiết kế quy tắc làm tròn.

**Bài 6 — số ở thang 1e16**

`double x = 1e16; double y = x + 1.0;`. Nếu `x == y` là `true`, có thể chọn epsilon để khôi phục sự khác biệt đúng bằng 1 không?

**Đáp án:** Không. Phép cộng đã làm tròn về cùng một giá trị biểu diễn; thông tin về phần cộng 1 bị mất. Cần đổi kiểu biểu diễn hoặc cách tính khi bài toán thực sự đòi phân biệt các đơn vị nguyên tại thang độ lớn này.

## 16. Ghi nhớ và tài liệu chuẩn

Đừng hỏi “nên dùng epsilon là bao nhiêu cho Java?”. Hãy hỏi: **đơn vị gì, thang số nào, sai số nào được phép, và hệ quả nếu coi hai số khác nhau là bằng nhau?**

- `==` có ích khi mục tiêu là bằng nhau chính xác theo semantics dấu phẩy động đã định nghĩa.
- `absTol` thích hợp khi có mức lệch được phép theo đơn vị; `relTol` hỗ trợ nhiều thang độ lớn; **phối hợp ngưỡng vẫn cần lý do định lượng**.
- `Math.ulp`/`Math.nextUp` giúp hiểu độ phân giải biểu diễn máy, không xác định tự động dung sai nghiệp vụ.
- So sánh gần bằng **không bắc cầu**, không thay thế `equals()` hoặc comparator cho cấu trúc dữ liệu.
- Với decimal chính xác, kiểu nguyên theo đơn vị nhỏ nhất hoặc `BigDecimal` thường hợp lý hơn một epsilon tùy tiện.

Tài liệu chính thức:
- [Double — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): IEEE 754, `NaN`, signed zero, `Double.compare`, `Double.isFinite` và semantics của wrapper.
- [Math — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `ulp`, `nextUp`, `fma` và độ chính xác các hàm floating point.
- [Java Language Specification SE 17, `4.2.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): biểu diễn kiểu `float`/`double`, giá trị đặc biệt và hành vi dấu phẩy động.
- [BigDecimal — Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): tính thập phân, `equals`, `compareTo` và rounding theo hợp đồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-007">Tạo BigDecimal từ double khi đầu vào cần chính xác thập phân</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: tạo `BigDecimal` từ `double` khi đầu vào cần chính xác thập phân

Một số giá trị như tiền, đơn giá, thuế suất, số liệu đo đã được quy định theo chữ số thập phân cần được giữ **đúng giá trị đã nhập**. Lỗi phổ biến trong Java là nhận chuỗi `"0.1"` nhưng chuyển thành `double` rồi mới tạo `BigDecimal`. Khi ấy ứng dụng có thể đã làm mất thông tin thập phân tại bước chuyển sang dấu phẩy động nhị phân. `BigDecimal` không thể tự suy đoán để khôi phục nguyên văn số mà người dùng gửi.

Bài này phân biệt `new BigDecimal(double)`, `BigDecimal.valueOf(double)` và `new BigDecimal(String)`; chỉ rõ trường hợp mỗi cách phù hợp, hậu quả khi đã qua `double`, cách kiểm soát `scale`/làm tròn và cách kiểm thử dữ liệu đầu vào chính xác trong Java 17+.

## 1. Vì sao `double` không biểu diễn chính xác mọi số thập phân?

`double` trong Java tuân theo kiểu dấu phẩy động nhị phân IEEE 754 binary64. Phần hữu hạn thông thường của nó có thể hiểu như một số nhị phân với độ chính xác hữu hạn. Các phân số có mẫu số chỉ chứa lũy thừa của 2, chẳng hạn `0.5` (1/2), `0.25` (1/4), có thể được biểu diễn chính xác. Nhưng `0.1` (1/10) không có biểu diễn hữu hạn trong hệ nhị phân, nên phải được **làm tròn đến giá trị `double` gần nhất**.

Ví dụ:

```java
double a = 0.1;
double b = 0.2;
System.out.println(a + b); // 0.30000000000000004
```

Đầu ra không phải do Java “cộng sai ngẫu nhiên”; đây là kết quả của biểu diễn và làm tròn trong phép tính nhị phân. Bản thân `double` được thiết kế rất hữu ích cho nhiều bài toán kỹ thuật và khoa học có sai số cho phép, nhưng nó **không phải kho lưu giữ nguyên văn các chữ số decimal**.

Có hai loại chính xác cần tách bạch:

- **Chính xác của giá trị nhị phân đang có:** Giá trị `double` cụ thể là một số hữu tỉ nhị phân xác định; có thể chuyển sang biểu diễn thập phân đầy đủ của **chính giá trị đó**.
- **Chính xác của số thập phân ban đầu:** Chuỗi `"0.1"` phải tương ứng đúng 1/10, hoặc chuỗi `"1.2300"` còn cần bảo toàn cả số chữ số phần lẻ theo hợp đồng.

Một khi chuỗi đầu vào được parse thành `double`, hai dạng thông tin trên không còn tương đương.

## 2. Bẫy chính: `new BigDecimal(double)`

Constructor nhận `double` tạo một `BigDecimal` có giá trị **chính xác bằng giá trị nhị phân mà `double` thực sự đang biểu diễn**, không phải giá trị thập phân ngắn thường được in trên màn hình:

```java
import java.math.BigDecimal;

BigDecimal fromDouble = new BigDecimal(0.1);
System.out.println(fromDouble.toPlainString());
```

Kết quả:

```text
0.1000000000000000055511151231257827021181583404541015625
```

Ở đây constructor làm đúng hợp đồng của nó: lưu chính xác giá trị `double` gần 0,1. Chính `double` đã xấp xỉ 1/10 trước khi constructor được gọi. Vì thế cách này **không phù hợp khi muốn giữ đúng số decimal `0.1`**.

So sánh:

```java
BigDecimal expected = new BigDecimal("0.1");
BigDecimal actual = new BigDecimal(0.1);

System.out.println(actual.compareTo(expected) == 0); // false
```

`actual` không bằng `expected` về **giá trị số**, chứ không đơn thuần khác `scale`. Điều này có thể làm kiểm tra giới hạn, so khớp số tiền, kiểm thử equality hay phân bổ số tiền cho kết quả bất ngờ.

Một số giá trị như `0.5` được biểu diễn chính xác bởi binary64, nên `new BigDecimal(0.5)` cho giá trị thập phân đúng `0.5`. Tuy nhiên, không nên suy từ một ví dụ may mắn để dùng constructor `double` cho mọi đầu vào decimal.

## 3. Đúng khi có văn bản nguồn: `new BigDecimal(String)`

Nếu dữ liệu gốc đang ở dạng chuỗi thập phân, hãy parse trực tiếp từ chuỗi:

```java
BigDecimal price = new BigDecimal("19.99");
BigDecimal rate = new BigDecimal("0.075");
BigDecimal value = new BigDecimal("0.1");
```

Các giá trị trên được tạo theo đúng chữ số thập phân trong chuỗi, không trải qua bước làm tròn binary64. Constructor này hỗ trợ dấu và dạng biểu diễn decimal hợp lệ theo cú pháp của `BigDecimal`; nó không tự xử lý mọi quy ước locale, dấu phân nhóm tiền tệ hoặc ký hiệu đơn vị.

Không có gì đặc biệt về **literal chuỗi**: chuỗi đọc từ HTTP body, tệp cấu hình, CSV hoặc một trường nhập liệu cũng thích hợp nếu đó là đại diện decimal gốc và đã được xác thực theo hợp đồng.

Ví dụ:

```java
BigDecimal left = new BigDecimal("0.1");
BigDecimal right = new BigDecimal("0.2");

System.out.println(left.add(right).toPlainString()); // 0.3
```

Không nên viết:

```java
BigDecimal wrong = new BigDecimal(Double.parseDouble("19.99"));
```

`Double.parseDouble` làm tròn theo binary64 trước, sau đó `BigDecimal` lưu chính xác **giá trị đã làm tròn**. Nếu yêu cầu là đọc chính xác `19.99`, parse thẳng bằng `new BigDecimal("19.99")`.

### So sánh ba cách tạo

| Cách tạo | Giá trị nhận vào | Điều nó bảo đảm | Khi phù hợp |
| --- | --- | --- | --- |
| `new BigDecimal("0.1")` | Chuỗi decimal | Giá trị decimal đúng như chuỗi, giữ scale theo biểu diễn | Dữ liệu gốc cần decimal chính xác |
| `new BigDecimal(0.1)` | `double` | Giá trị **nhị phân double hiện tại** được chuyển chính xác sang decimal | Cần phân tích/lưu chính xác giá trị binary64 đã có |
| `BigDecimal.valueOf(0.1)` | `double` | Chuyển qua biểu diễn `Double.toString(double)` rồi tạo decimal | Khi đầu vào thực sự chỉ có `double` và muốn decimal theo chuỗi hiển thị chuẩn |
| `new BigDecimal(Double.toString(d))` | Chuỗi chuẩn từ một `double` | Về giá trị tương đương đường chuyển thường dùng của `valueOf(d)` | Khi cần thể hiện rõ bước chuyển từ `double` |

Với dữ liệu gốc là chuỗi, **đừng đi qua `double`** chỉ để rồi gọi `valueOf`.

## 4. `BigDecimal.valueOf(double)` sửa được điều gì, không sửa được điều gì?

`BigDecimal.valueOf(double)` thường là lựa chọn tốt hơn `new BigDecimal(double)` **khi dữ liệu duy nhất bạn có thực sự là một `double`**. Nó dùng biểu diễn chuỗi chuẩn của `Double.toString(double)`, tức biểu diễn decimal ngắn cho phép round-trip trở lại cùng giá trị `double`:

```java
BigDecimal a = new BigDecimal(0.1);
BigDecimal b = BigDecimal.valueOf(0.1);

System.out.println(b.toPlainString()); // 0.1
System.out.println(a.compareTo(b) == 0); // false
```

`valueOf` loại bỏ dãy chữ số dài do biểu diễn nhị phân của ví dụ `0.1` và tạo decimal `0.1`. Nhưng **nó không bảo đảm khôi phục đầu vào decimal ban đầu** trong mọi trường hợp.

Ví dụ, chuỗi `"0.10000000000000001"` và `"0.1"` đều có thể được `Double.parseDouble` làm tròn về cùng giá trị `double`. Khi giá trị đã bị gộp, `BigDecimal.valueOf` không biết chuỗi nào đã xuất hiện ban đầu:

```java
String source = "0.10000000000000001";
double rounded = Double.parseDouble(source);

BigDecimal recovered = BigDecimal.valueOf(rounded);
BigDecimal original = new BigDecimal(source);

System.out.println(recovered.toPlainString()); // 0.1
System.out.println(original.toPlainString());  // 0.10000000000000001
```

Ngoài ra, nếu đã có phép tính dùng `double`, `valueOf` sẽ lưu lại decimal tương ứng với **kết quả tính binary64**:

```java
double computed = 0.1 + 0.2;
BigDecimal result = BigDecimal.valueOf(computed);

System.out.println(result.toPlainString()); // 0.30000000000000004
```

Chuyển đổi sau phép cộng không thể biến kết quả `double` thành một phép cộng decimal đúng 0,3. Muốn tính thập phân chính xác, phải chuyển sang `BigDecimal` **trước khi thực hiện phép toán** và có số decimal nguồn chính xác.

### `valueOf` cũng không bảo toàn định dạng văn bản ban đầu

`BigDecimal.valueOf(1.0)` được xây dựng từ chuỗi `"1.0"` của `Double.toString(1.0)`; nó không phân biệt liệu dữ liệu ban đầu là `"1"`, `"1.0"`, `"1.00"` hay `"1.000"`. Nếu số chữ số phần lẻ mang ý nghĩa nghiệp vụ, phải nhận và giữ giá trị đó qua văn bản gốc hoặc qua metadata riêng, không thể khôi phục từ một `double`.

## 5. Vì sao chuyển đổi muộn có thể phá hợp đồng thập phân?

Xét một API nhận thuế suất hoặc số tiền từ người dùng. Nếu đường đi là:

```text
"0.10000000000000001" (decimal gốc)
     ↓ Double.parseDouble
0.1 (double gần đúng, không còn ghi nhớ chuỗi gốc)
     ↓ BigDecimal.valueOf
0.1 (BigDecimal decimal)
```

kết quả cuối không còn đại diện cho toàn bộ thông tin decimal đã gửi. Hai chuỗi decimal khác nhau đã có thể tạo cùng một `double`, nên **không tồn tại thuật toán đảo ngược chung** để xác định chính xác chuỗi nào ban đầu.

Đường đi giữ chính xác:

```text
"0.10000000000000001" (decimal gốc)
     ↓ new BigDecimal(String)
0.10000000000000001 (BigDecimal)
     ↓ tính toán BigDecimal
Kết quả decimal theo hợp đồng toán học/rounding
```

Nhận diện điểm mất thông tin là quan trọng hơn việc chọn constructor ở cuối pipeline. Ngay cả khi cuối cùng cần `BigDecimal`, hãy kiểm tra kiểu của **mọi bước trước đó**: thư viện JSON, parser CSV, mapping DTO, JDBC, phép toán trung gian, hoặc getter trả `double`.

## 6. `scale`, `precision` và equality: hiểu cả giá trị lẫn biểu diễn

`BigDecimal` lưu giá trị thập phân như một số nguyên không tỉ lệ cùng với `scale`. Hai giá trị có thể bằng nhau về số học nhưng có `scale` khác nhau:

```java
BigDecimal x = new BigDecimal("1.0");
BigDecimal y = new BigDecimal("1.00");

System.out.println(x.compareTo(y) == 0); // true
System.out.println(x.equals(y));         // false
System.out.println(y.scale());           // 2
```

`compareTo` so sánh **giá trị số**. `equals` xét cả **giá trị số và scale**, nên `"1.0"` và `"1.00"` không bằng nhau theo `equals`. `HashMap`/`HashSet` sử dụng `equals` và `hashCode`; `TreeMap`/`TreeSet` theo thứ tự tự nhiên dùng `compareTo`, vì vậy sự khác biệt này cũng ảnh hưởng đến xác định khóa trùng.

Khi parse trực tiếp từ `"1.00"`, scale 2 được giữ. Nếu parse qua `double`, thông tin về số chữ số phần lẻ gốc đã có thể mất.

Phân biệt:

- **Độ chính xác của giá trị:** decimal chính xác có bằng đại lượng gốc không?
- **`precision()` của `BigDecimal`:** số chữ số có nghĩa trong phần nguyên không tỉ lệ.
- **`scale()` của `BigDecimal`:** số chữ số theo lũy thừa 10 ở mẫu; scale có thể âm.
- **Độ phân giải nghiệp vụ:** chẳng hạn đơn vị nhỏ nhất là 0,01 hoặc 0,001.
- **Định dạng hiển thị:** dấu thập phân, dấu phân nhóm, số chữ số sau dấu và locale.

Không nên mặc định rằng một `BigDecimal` được tạo thành công là đã đáp ứng độ phân giải hoặc giới hạn của nghiệp vụ.

## 7. Hợp đồng làm tròn và kiểm tra độ phân giải

Nếu một trường tiền chỉ được phép có độ phân giải 0,01, có ít nhất hai chính sách:

**Chính sách từ chối giá trị không biểu diễn chính xác ở hai chữ số:**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal amount = new BigDecimal("12.30")
        .setScale(2, RoundingMode.UNNECESSARY);
```

Giá trị `"12.345"` sẽ khiến `setScale(2, RoundingMode.UNNECESSARY)` ném `ArithmeticException`, vì phải làm tròn để về hai chữ số. Giá trị `"12.300"` lại có thể chuyển chính xác thành `"12.30"`: bỏ số 0 cuối **không đổi giá trị số**. Điều này khác với hợp đồng bắt buộc **đúng hình thức hai chữ số trong chuỗi đầu vào**; hợp đồng về định dạng đó cần kiểm tra riêng.

**Chính sách cho phép làm tròn có quy tắc rõ ràng:**

```java
BigDecimal rounded = new BigDecimal("12.345")
        .setScale(2, RoundingMode.HALF_UP);
// rounded = 12.35
```

`HALF_UP` chỉ là một lựa chọn; nhiều bài toán cần `HALF_EVEN`, quy tắc pháp lý, hoặc từ chối mọi phép làm tròn. Nên ghi rõ **lúc nào** thực hiện rounding (mỗi dòng, tổng hóa đơn, bước xuất dữ liệu...) vì làm tròn ở các thời điểm khác nhau có thể cho kết quả khác nhau.

### `new BigDecimal(double, MathContext)` không khôi phục đầu vào

Có thể chỉ định `MathContext` để làm tròn giá trị được xây dựng từ `double` đến số chữ số có nghĩa nhất định. Tuy nhiên, giá trị xuất phát vẫn là binary64 đã xấp xỉ, nên đây không phải cách đảm bảo tái lập chuỗi decimal ban đầu. `MathContext` mô tả **chính sách precision và rounding**, không phải công cụ giải mã thông tin đã mất.

Nếu ứng dụng cần số đơn vị nhỏ dưới dạng `long`, nên chuyển từ decimal chính xác rồi dùng exact conversion:

```java
long cents = new BigDecimal("12.30")
        .movePointRight(2)
        .longValueExact(); // 1230
```

`movePointRight(2)` nhân 100 theo số học decimal; `longValueExact()` từ chối phần không nguyên hoặc giá trị ngoài miền `long`. Đừng thay bằng `longValue()` nếu không chấp nhận cắt phần lẻ hoặc bit cao.

## 8. Code Java 17+ hoàn chỉnh: so sánh đầu vào, conversion và giới hạn

Lưu chương trình sau thành **`BigDecimalFromDoubleDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalFromDoubleDemo {
    static BigDecimal parseExactAmount(String text) {
        if (text == null) {
            throw new IllegalArgumentException("amount is required");
        }
        BigDecimal amount = new BigDecimal(text)
                .setScale(2, RoundingMode.UNNECESSARY);

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        return amount;
    }

    public static void main(String[] args) {
        BigDecimal fromDouble = new BigDecimal(0.1);
        BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
        BigDecimal fromText = new BigDecimal("0.1");

        System.out.println("ctor decimal = " + fromDouble.toPlainString());
        System.out.println("valueOf decimal = " + fromValueOf.toPlainString());
        System.out.println("text decimal = " + fromText.toPlainString());
        System.out.println("ctor equals numeric text = "
                + (fromDouble.compareTo(fromText) == 0));

        BigDecimal exactSum = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println("exact sum = " + exactSum.toPlainString());
        System.out.println("double sum converted = "
                + BigDecimal.valueOf(0.1 + 0.2).toPlainString());

        String source = "0.10000000000000001";
        System.out.println("original = " + new BigDecimal(source).toPlainString());
        System.out.println("through double = "
                + BigDecimal.valueOf(Double.parseDouble(source)).toPlainString());

        BigDecimal scaled = new BigDecimal("1.00");
        System.out.println("scale = " + scaled.scale());
        System.out.println("numeric equal = "
                + (scaled.compareTo(new BigDecimal("1.0")) == 0));
        System.out.println("object equal = "
                + scaled.equals(new BigDecimal("1.0")));

        System.out.println("accepted amount = "
                + parseExactAmount("12.300").toPlainString());
        try {
            parseExactAmount("12.345");
            System.out.println("extra decimals = accepted");
        } catch (ArithmeticException ex) {
            System.out.println("extra decimals = rejected");
        }
        try {
            parseExactAmount("-0.01");
            System.out.println("negative = accepted");
        } catch (IllegalArgumentException ex) {
            System.out.println("negative = rejected");
        }

        long cents = parseExactAmount("12.30")
                .movePointRight(2)
                .longValueExact();
        System.out.println("cents = " + cents);
        System.out.println("half exact = " + new BigDecimal(0.5).toPlainString());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 BigDecimalFromDoubleDemo.java
java BigDecimalFromDoubleDemo
```

Stdout:

```text
ctor decimal = 0.1000000000000000055511151231257827021181583404541015625
valueOf decimal = 0.1
text decimal = 0.1
ctor equals numeric text = false
exact sum = 0.3
double sum converted = 0.30000000000000004
original = 0.10000000000000001
through double = 0.1
scale = 2
numeric equal = true
object equal = false
accepted amount = 12.30
extra decimals = rejected
negative = rejected
cents = 1230
half exact = 0.5
```

Giải thích quan trọng:

1. Constructor nhận `double` cho ra biểu diễn decimal dài, chính xác của **binary64 0.1**, khác số decimal `0.1` trong văn bản.
2. `BigDecimal.valueOf(0.1)` cho giá trị decimal `0.1`, nhưng khi `0.1 + 0.2` được cộng trong `double` **trước khi gọi** `valueOf`, kết quả vẫn là `0.30000000000000004`.
3. Chuỗi `"0.10000000000000001"` bị mất các chữ số bổ sung khi đi qua `Double.parseDouble`, trong khi `new BigDecimal(source)` giữ nguyên giá trị decimal.
4. `"1.00"` và `"1.0"` bằng nhau về số học nhưng khác `equals` do scale.
5. Bộ parse nghiệp vụ dùng `setScale(2, UNNECESSARY)` để chuẩn hóa các giá trị có thể biểu diễn đúng theo phần trăm của đơn vị tiền; từ chối `"12.345"` và số âm.
6. `BigDecimal.valueOf(0.5)` hay `new BigDecimal(0.5)` không thể hiện bẫy vì 1/2 được biểu diễn chính xác trong nhị phân; không phải mọi phân số decimal đều như vậy.

## 9. Ranh giới JSON, database và các nguồn dữ liệu bên ngoài

**JSON:** JSON number là ký pháp thập phân trong văn bản, nhưng cách thư viện giải mã mới quyết định có mất dữ liệu không. Nếu serializer/deserializer đưa trường JSON decimal qua `double` trước khi tạo `BigDecimal`, độ chính xác có thể đã mất. Cấu hình parser để đọc token số decimal trực tiếp thành `BigDecimal` khi hợp đồng cần chính xác. Với đầu vào `{"amount": 0.10000000000000001}`, giữ kiểu decimal trong backend sẽ khác với dùng `double` làm bước trung gian.

**JavaScript:** `Number` sử dụng binary64. Khi JavaScript parse số JSON thành `Number`, nó có thể không giữ chính xác các chữ số decimal mà server mong muốn. Nếu giao thức yêu cầu giữ chính xác tiền/tỷ lệ hoặc số nguyên lớn, cần thỏa thuận biểu diễn (ví dụ **chuỗi thập phân** và thư viện decimal tương ứng) giữa frontend và backend. Đừng đổi tùy tiện một trường JSON number sang string khi client chưa có schema mới.

**Database/JDBC:** SQL `DECIMAL`/`NUMERIC` thường thích hợp cho decimal chính xác trong phạm vi schema. Khi đọc số decimal, dùng mapping/getter tương ứng `BigDecimal`; lấy qua `getDouble()` có thể làm tròn trước khi convert. Ngược lại, dữ liệu trong cột dấu phẩy động `FLOAT`/`DOUBLE` đã có thể bị xấp xỉ khi ghi, dù ứng dụng nhận bằng `BigDecimal`.

**CSV/Excel:** Cột số có thể đã được nhập, lưu hoặc tính dưới dạng dấu phẩy động trong một công cụ khác. Nếu file đã mất chữ số khi lưu số dạng binary64, parse sang `BigDecimal` ở bước nhập Java **không thể khôi phục lại nguồn gốc**. Với tệp CSV chứa nguyên chuỗi decimal, parse trực tiếp từ trường văn bản và xác định rõ dấu thập phân/locale.

**Tích hợp API:** Nếu chữ số sau dấu thập phân là một phần của hợp đồng, cần nói rõ trong tài liệu JSON/CSV/DTO: đơn vị, miền, scale tối đa, có cho phép dạng exponent không, có chấp nhận số 0 ở cuối không, làm tròn hay từ chối khi quá độ phân giải. Cả hai phía cần cùng một quy tắc parse và validate.

## 10. Chọn API theo nguồn gốc dữ liệu

| Nguồn đầu vào | Nên làm gì | Tránh |
| --- | --- | --- |
| Chuỗi decimal do người dùng nhập | `new BigDecimal(text)` rồi validate miền/scale | `Double.parseDouble(text)` trước |
| Hằng số decimal cần chính xác | `new BigDecimal("0.1")` | `new BigDecimal(0.1)` |
| Đã có `double` hợp lệ từ phép đo | Cân nhắc `BigDecimal.valueOf(d)` nếu cần decimal theo `Double.toString(d)` | Tưởng `valueOf` khôi phục số decimal nguyên thủy |
| Muốn biết chính xác giá trị binary64 đã lưu | `new BigDecimal(d)` | Nhầm với biểu diễn decimal gốc |
| Kết quả `double` đã được tính toán | Giữ nguyên ý nghĩa xấp xỉ hoặc tính lại từ nguồn decimal | Chỉ gọi `valueOf` ở cuối rồi tin phép tính chính xác |
| Decimal có độ phân giải cố định | `setScale(scale, UNNECESSARY)` để từ chối rounding, hoặc mode tường minh | `setScale(scale)` rồi bỏ qua ngoại lệ/rounding |
| Số tiền dạng đơn vị nhỏ nguyên | Parse decimal đúng → nhân theo thang decimal → `longValueExact()` | Nhân bằng `double` rồi cast sang `long` |
| JSON/SQL decimal | Giữ ánh xạ decimal trực tiếp qua ranh giới | Đi qua kiểu `double` trung gian |
| Đầu vào đã là số nguyên | `BigDecimal.valueOf(longValue)` hoặc `new BigDecimal(BigInteger)` | Đổi số nguyên lớn thành `double` trước |

`BigDecimal.valueOf(long)` là một overload khác với `BigDecimal.valueOf(double)`: khi đầu vào là `long`, giá trị nguyên được chuyển **chính xác**, không qua binary64. Đây là lựa chọn phù hợp khi đầu vào vốn là số nguyên cần biểu diễn dạng decimal.

## 11. Kiểm thử phát hiện sự cố chuyển đổi

Các ca kiểm thử nên được thiết kế từ **chuỗi nguồn và miền nghiệp vụ**, không chỉ từ kết quả của cùng một parser đã bị lỗi.

| Ca kiểm thử | Cách tạo | Điều cần khẳng định |
| --- | --- | --- |
| Phân số không biểu diễn đúng bằng nhị phân | `"0.1"` | `new BigDecimal("0.1")` giữ giá trị; `new BigDecimal(0.1)` khác |
| Phân số biểu diễn được bằng nhị phân | `"0.5"` | Constructor `double` tình cờ khớp |
| Phép cộng decimal | `"0.1" + "0.2"` | `BigDecimal.add` trên chuỗi cho `0.3` |
| Phép cộng binary64 rồi mới convert | `BigDecimal.valueOf(0.1 + 0.2)` | `0.30000000000000004` |
| Thông tin decimal mất khi parse double | `"0.10000000000000001"` | Parse chuỗi trực tiếp giữ nguyên decimal |
| Scale khác nhau | `"1.0"` và `"1.00"` | `compareTo == 0` nhưng `equals == false` |
| Dư độ phân giải | `"12.345"` với scale 2 | Từ chối hoặc làm tròn đúng theo hợp đồng |
| Có số 0 cuối | `"12.300"` với scale 2 | Chuẩn hóa thành `12.30` mà không đổi giá trị |
| Giá trị âm ngoài miền | `"-0.01"` trong trường không âm | Từ chối sau khi parse |
| Ký hiệu không hợp lệ | `"12,34"` với parser không locale | Không tự hiểu dấu phẩy decimal |
| Giá trị vượt miền nguyên nhỏ | Số cent vượt `long` | `longValueExact()` ném |
| Giá trị NaN/vô cực | `Double.NaN`, `Infinity` | Không có giá trị `BigDecimal` thông thường tương ứng |

Nếu phải chuyển `BigDecimal` sang `double`, hãy kiểm tra rủi ro mất precision ở chiều ngược lại. `bigDecimal.doubleValue()` có thể trả một giá trị xấp xỉ hoặc vô cực với đầu vào đủ lớn; nếu hợp đồng cấm mất độ chính xác, không được dùng conversion này làm bước vô hại.

Các kiểm thử tích hợp nên bao trùm cả đường đi đầu-cuối: từ literal JSON/CSV/database qua DTO, domain model, phép tính, đến dữ liệu được serialize trả lại. Nếu trong bất kỳ khâu nào đầu vào bị chuyển thành `double`, xác minh xem đó là quyết định chấp nhận sai số có chủ đích hay là lỗi thiết kế.

## 12. Những sai lầm thường gặp

1. **Dùng `new BigDecimal(0.1)` vì tưởng constructor sẽ giữ giá trị decimal 0,1.** Constructor thực sự lưu chính xác giá trị binary64 gần 0,1.
2. **Tin `BigDecimal.valueOf(d)` luôn khôi phục đầu vào decimal.** Nó dựa trên `Double.toString(d)`; nếu dữ liệu đã bị làm tròn thành `double`, thông tin nguồn không thể phục hồi.
3. **Tính bằng `double` trước, đổi sang `BigDecimal` sau.** Các sai số và kết quả làm tròn trước đó vẫn tồn tại trong biểu diễn decimal của kết quả.
4. **Đi từ chuỗi → `double` → `BigDecimal`.** Thêm bước binary64 không cần thiết và có thể đánh mất chữ số.
5. **Nhầm `compareTo` với `equals`.** Cùng giá trị thập phân nhưng khác `scale` có thể khác nhau theo `equals`; ngược lại giá trị từ constructor `double` có thể khác thật cả về số.
6. **Cho rằng `setScale` là cách phục hồi chính xác decimal.** Làm tròn có thể cho một số trông giống mong muốn nhưng không chứng minh được chữ số gốc.
7. **Dùng `BigDecimal.valueOf(1.0)` rồi tưởng nhớ được chuỗi `"1.000"` đã nhập.** `double` không lưu biểu diễn văn bản.
8. **Không quy định lỗi phần thập phân thừa.** Một API vô tình làm tròn có thể thay đổi giá trị nghiệp vụ.
9. **Không xem xét JSON/JDBC/front-end.** Dù Java dùng `BigDecimal`, một thư viện trung gian dùng `double` vẫn có thể gây mất decimal.
10. **Dùng `double` là sai trong mọi ngữ cảnh.** Điều này cũng không đúng: `double` có giá trị sử dụng trong tính toán xấp xỉ. Lỗi nằm ở việc dùng nó **khi hợp đồng yêu cầu decimal chính xác từ đầu vào**.

## 13. Quy tắc áp dụng

Khi đọc dữ liệu thập phân cần chính xác, **giữ biểu diễn decimal từ nguồn và tạo `BigDecimal` trực tiếp từ chuỗi hoặc kiểu decimal chính xác**. Chỉ dùng `BigDecimal.valueOf(double)` khi nguồn thực sự là `double` và cần decimal theo cách biểu diễn chuẩn của nó. Dùng `new BigDecimal(double)` khi muốn biết chính xác giá trị nhị phân hiện có, không phải khi muốn tái tạo ý nghĩa thập phân của người nhập.

Sau khi parse, kiểm tra miền, độ phân giải, rounding và `null` theo hợp đồng. Tính toán bằng API `BigDecimal` phù hợp, tránh conversion trung gian qua `double`, và dùng phép chuyển **exact** khi đưa về kiểu số nguyên. Điều quan trọng nhất là phát hiện bước đầu tiên làm mất thông tin; các phép chuyển đổi được thêm sau bước ấy không thể thay thế độ chính xác vốn đã mất.

### Tài liệu chính thức

- [`BigDecimal` — constructor, `valueOf`, scale, rounding](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [`Double` — `toString`, `parseDouble`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html)
- [`RoundingMode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)
- [JLS §4.2.3 — Floating-point types and values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-008">Chia BigDecimal không chỉ định chính sách cho kết quả vô hạn tuần hoàn</a>

<details>
<summary>Xem chi tiết</summary>

# Chia BigDecimal không chỉ định chính sách cho kết quả vô hạn tuần hoàn

Trong Java, `BigDecimal` biểu diễn số thập phân bằng hệ số nguyên (`unscaledValue`) và `scale`: giá trị bằng `unscaledValue × 10^(-scale)`. Nó hỗ trợ phép toán decimal chính xác nhưng **không thể chứa vô hạn chữ số thập phân trong một đối tượng hữu hạn**. Do đó phép chia exact như `1 / 3` là trường hợp cần hiểu rõ.

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

BigDecimal result = one.divide(three);
// Ném ArithmeticException: thương không có khai triển thập phân hữu hạn
```

Mặc dù `1` và `3` đều là số nguyên, thương toán học `1/3 = 0.333333…` không có biểu diễn thập phân hữu hạn. `BigDecimal.divide(BigDecimal)` **không tự chọn** số chữ số thập phân, cách làm tròn hay độ chính xác. Nếu phải làm tròn, lập trình viên cần truyền **chính sách tường minh**, phù hợp yêu cầu nghiệp vụ.

## 1. Phân biệt chia chính xác với chia có làm tròn

Hai phép chia này trả kết quả khác nhau về ý nghĩa:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

one.divide(three); // ArithmeticException: yêu cầu thương chính xác
one.divide(three, 2, RoundingMode.HALF_UP); // 0.33: thương đã làm tròn
```

Lời gọi `divide(three, 2, HALF_UP)` **không làm cho `1/3` trở thành `0.33` về mặt toán học**; đó là giá trị gần đúng được làm tròn theo scale 2 và mode `HALF_UP`. Khi tính tiếp, cần nhận biết rằng thông tin đã bị bỏ đi ở bước chia.

| Tình huống | Dùng `divide(divisor)` | Vì sao? |
|---|---|---|
| `1 / 2` | `0.5` | Thương thập phân hữu hạn |
| `1 / 8` | `0.125` | Thương thập phân hữu hạn |
| `1 / 40` | `0.025` | Thương thập phân hữu hạn |
| `3 / 6` | `0.5` | Rút gọn thành `1/2` |
| `1 / 3` | Ném `ArithmeticException` | `0.333333…` |
| `1 / 6` | Ném `ArithmeticException` | `0.166666…` |
| `1 / 7` | Ném `ArithmeticException` | `0.142857142857…` |

**Quan trọng:** `ArithmeticException` là ngoại lệ của một phép tính không thể đáp ứng yêu cầu exact, không phải bằng chứng rằng dữ liệu đầu vào “không phải số” hoặc rằng `BigDecimal` bị lỗi.

## 2. Tại sao có thương hữu hạn và thương vô hạn?

Với phân số hữu tỉ `p/q` sau khi **rút gọn tối giản** và chọn `q > 0`:

- Khai triển thập phân **kết thúc** nếu mẫu `q` chỉ có thừa số nguyên tố `2` và/hoặc `5`, tức `q = 2^a × 5^b` với `a,b ≥ 0`.
- Nếu mẫu tối giản còn thừa số khác như `3` hoặc `7`, khai triển thập phân vô hạn tuần hoàn.
- Phải xét mẫu **sau rút gọn**, không phải mẫu ban đầu: `3/6 = 1/2` nên chia exact được.

Ví dụ `1/8 = 1/(2³) = 0.125`; `1/40 = 1/(2³ × 5) = 0.025`. Trong khi đó `1/3` không thể biến thành phân số có mẫu là một lũy thừa của 10.

Một giá trị `BigDecimal` chỉ mang hệ số nguyên hữu hạn và scale nguyên; do đó nếu thương cần dãy thập phân vô hạn, yêu cầu “trả chính xác toàn bộ thương bằng một BigDecimal” là bất khả thi. Java từ chối thay vì âm thầm cắt hoặc làm tròn.

Quy tắc toán học này dành cho thương của hai số hữu tỉ. Không cần tự phân tích thừa số nguyên tố trong ứng dụng thông thường chỉ để chia: chọn API phù hợp với hợp đồng phép tính là đủ.

## 3. Năm cách chia cần phân biệt

| API | Ý nghĩa | Thương tuần hoàn |
|---|---|---|
| `a.divide(b)` | Chia chính xác, không cho phép mất thông tin | `ArithmeticException` |
| `a.divide(b, scale, mode)` | Làm tròn thương theo **scale đích** chỉ định | Trả kết quả đã làm tròn |
| `a.divide(b, mode)` | Dùng **scale của `a`** làm scale kết quả | Trả kết quả đã làm tròn tại scale đó |
| `a.divide(b, mathContext)` | Điều khiển **precision (chữ số có nghĩa)** và mode | Có thể trả kết quả làm tròn với precision dương |
| `a.divide(b, MathContext.UNLIMITED)` | Không giới hạn precision, yêu cầu exact | `ArithmeticException` |

Tất cả đều có thể ném `ArithmeticException` khi **divisor bằng 0**. Khi `RoundingMode.UNNECESSARY` được dùng và không thể biểu diễn thương tại giới hạn đã chọn mà giữ nguyên giá trị, phép chia cũng ném ngoại lệ.

### 3.1. Exact: `divide(divisor)`

```java
BigDecimal a = new BigDecimal("1.00");
BigDecimal b = new BigDecimal("8");

BigDecimal exact = a.divide(b); // 0.125
```

Thương có thể mang scale **khác** scale của dividend. `1.00` có scale 2, nhưng kết quả chính xác `0.125` có scale 3. Scale đầu vào **không** tự trở thành giới hạn làm tròn của `divide(divisor)`.

### 3.2. Chia theo scale đích và mode: `divide(divisor, scale, roundingMode)`

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

BigDecimal third = one.divide(
        three, 2, RoundingMode.HALF_UP); // 0.33

BigDecimal twoThirds = new BigDecimal("2").divide(
        three, 2, RoundingMode.HALF_UP); // 0.67

BigDecimal oneEighth = one.divide(
        new BigDecimal("8"), 2, RoundingMode.HALF_UP); // 0.13
```

Ở đây `scale=2` yêu cầu kết quả có đúng hai vị trí thập phân. Ngay cả `1/8 = 0.125`, vốn có thương decimal exact, cũng phải làm tròn thành `0.13` vì **scale đích** không đủ biểu diễn chính xác 0.125.

Chú ý phương thức `setScale(2, HALF_UP)` sau khi có thương exact có thể là một lựa chọn khi thương exact kết thúc, nhưng **không** sửa được tình huống `one.divide(three)` đã ném ngoại lệ trước khi đến bước `setScale`. Chỉ định chính sách **ngay tại phép chia** nếu biết thương có thể tuần hoàn.

### 3.3. Chia chỉ với RoundingMode: scale đầu vào có thể gây bất ngờ

```java
BigDecimal three = new BigDecimal("3");

System.out.println(
        new BigDecimal("1").divide(three, RoundingMode.HALF_UP)
); // 0

System.out.println(
        new BigDecimal("1.00").divide(three, RoundingMode.HALF_UP)
); // 0.33
```

Hai dividend có **cùng giá trị toán học**, nhưng scale lần lượt là 0 và 2. Overload `divide(divisor, roundingMode)` trả thương với scale bằng **scale của dividend**. Nếu data layer cung cấp `1` thay vì `1.00`, kết quả có thể thay đổi không mong đợi.

Đối với quy tắc nghiệp vụ cần scale cố định, **truyền scale rõ ràng** thay vì phụ thuộc biểu diễn đầu vào.

### 3.4. Chia với MathContext: precision không phải scale

```java
MathContext context =
        new MathContext(5, RoundingMode.HALF_UP);

BigDecimal third = BigDecimal.ONE.divide(
        new BigDecimal("3"), context); // 0.33333

BigDecimal tenSixths = new BigDecimal("10").divide(
        new BigDecimal("6"),
        new MathContext(3, RoundingMode.HALF_UP)); // 1.67
```

`MathContext(5, HALF_UP)` yêu cầu tối đa năm **chữ số có nghĩa** trong kết quả, không phải năm chữ số sau dấu trong mọi trường hợp. Precision kiểm soát một khía cạnh khác scale. Khi độ lớn thay đổi, số chữ số sau dấu cần để đạt precision nhất định cũng thay đổi.

`MathContext` có ích trong những thuật toán kiểm soát precision theo chữ số có nghĩa. Nếu ứng dụng cần kết quả ở đơn vị thập phân cụ thể, ví dụ chính xác hai vị trí để kết toán theo chính sách đã duyệt, overload có scale tường minh thường trực tiếp hơn.

### 3.5. MathContext.UNLIMITED và mode UNNECESSARY

```java
BigDecimal.ONE.divide(
        new BigDecimal("3"), MathContext.UNLIMITED);
// ArithmeticException

BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.UNNECESSARY);
// ArithmeticException
```

`MathContext.UNLIMITED` có precision 0, nghĩa là **không giới hạn số chữ số có nghĩa**, nhưng không đồng nghĩa “tự sinh vô hạn chữ số cho phép chia tuần hoàn”. Phép chia vẫn đòi kết quả decimal exact và ném ngoại lệ nếu bất khả thi.

`RoundingMode.UNNECESSARY` biểu thị **không được làm tròn mất thông tin**. Khi cần bỏ một chữ số khác 0 để đạt giới hạn mong muốn, phương thức từ chối. Đây là cách kiểm tra một ràng buộc exact, không phải một mode làm tròn mặc định.

## 4. Chọn chính sách theo yêu cầu, không theo mẹo tránh lỗi

Trước một phép chia `BigDecimal`, hãy xác định:

1. **Có yêu cầu exact không?** Nếu có, `divide(divisor)` phù hợp và phải xử lý trường hợp thương không thể biểu diễn hữu hạn.
2. **Nếu cho phép gần đúng, cần bao nhiêu chữ số?** Phân biệt scale (vị trí thập phân) với precision (chữ số có nghĩa).
3. **Làm tròn theo mode nào?** `HALF_UP`, `HALF_EVEN`, `FLOOR`, `CEILING`, `DOWN`... khác nhau về hướng, đặc biệt với số âm và giá trị đúng điểm giữa.
4. **Làm tròn ở bước nào?** Ngay khi chia, sau khi nhân, trên từng dòng, sau khi cộng tổng, hay lúc kết toán cuối cùng?
5. **Chia với mục đích gì?** Tính tỷ lệ thống kê, tính số lượng, lãi suất, phân bổ tài chính hoặc hiển thị có thể đòi chính sách khác nhau.
6. **Phần dư xử lý ra sao?** Đặc biệt khi phân chia một tổng thành nhiều phần cần bảo toàn tổng cuối.

Ví dụ, dùng `divide(3, 2, HALF_UP)` để chia `1.00` thành ba khoản:

```text
1.00 / 3 ≈ 0.33333…
Kết quả từng khoản (scale 2, HALF_UP): 0.33

0.33 + 0.33 + 0.33 = 0.99
Chênh lệch với tổng gốc = 0.01
```

Nếu yêu cầu là “ba khoản sau phân bổ phải cộng **đúng bằng** 1.00”, chỉ phép chia làm tròn **không đủ**. Phải có quy tắc phân bổ một đơn vị nhỏ nhất còn dư, chẳng hạn một khoản 0.34 và hai khoản 0.33. Cách chọn khoản nhận chênh lệch phụ thuộc nghiệp vụ; `BigDecimal` không quyết định thay.

Không suy quy tắc làm tròn từ tên kiểu hoặc số chữ số của dữ liệu đầu vào: `BigDecimal` không biết yêu cầu về tỷ lệ, thuế, tiền tệ, đo lường hay phân bổ.

## 5. Ví dụ Java 17+ có thể biên dịch, chạy độc lập

Tạo tệp `BigDecimalDivisionDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalDivisionDemo {

    private static void exact(
            String label, BigDecimal a, BigDecimal b) {
        try {
            System.out.println(label + " = " + a.divide(b));
        } catch (ArithmeticException ex) {
            System.out.println(label + " -> ArithmeticException");
        }
    }

    public static void main(String[] args) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");

        exact("1 / 8 exact", one, new BigDecimal("8"));
        exact("1 / 40 exact", one, new BigDecimal("40"));
        exact("1 / 3 exact", one, three);
        exact("3 / 6 exact",
                new BigDecimal("3"), new BigDecimal("6"));

        System.out.println("1 / 3 scale 2 = "
                + one.divide(three, 2, RoundingMode.HALF_UP));
        System.out.println("2 / 3 scale 2 = "
                + new BigDecimal("2").divide(
                        three, 2, RoundingMode.HALF_UP));
        System.out.println("1 / 6 scale 2 HALF_EVEN = "
                + one.divide(new BigDecimal("6"),
                        2, RoundingMode.HALF_EVEN));

        System.out.println("1 / 3 precision 5 = "
                + one.divide(three,
                        new MathContext(5, RoundingMode.HALF_UP)));
        System.out.println("10 / 6 precision 3 = "
                + new BigDecimal("10").divide(
                        new BigDecimal("6"),
                        new MathContext(3, RoundingMode.HALF_UP)));

        System.out.println("dividend scale 0 = "
                + new BigDecimal("1").divide(
                        three, RoundingMode.HALF_UP));
        System.out.println("dividend scale 2 = "
                + new BigDecimal("1.00").divide(
                        three, RoundingMode.HALF_UP));

        System.out.println("1.00 / 8 exact = "
                + new BigDecimal("1.00").divide(new BigDecimal("8")));
        System.out.println("1.00 / 8 scale 2 = "
                + new BigDecimal("1.00").divide(
                        new BigDecimal("8"), 2, RoundingMode.HALF_UP));

        try {
            one.divide(three, MathContext.UNLIMITED);
        } catch (ArithmeticException ex) {
            System.out.println("UNLIMITED -> ArithmeticException");
        }
        try {
            one.divide(three, 2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("UNNECESSARY -> ArithmeticException");
        }
        try {
            one.divide(BigDecimal.ZERO);
        } catch (ArithmeticException ex) {
            System.out.println("division by zero -> ArithmeticException");
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac BigDecimalDivisionDemo.java
java BigDecimalDivisionDemo
```

Stdout:

```text
1 / 8 exact = 0.125
1 / 40 exact = 0.025
1 / 3 exact -> ArithmeticException
3 / 6 exact = 0.5
1 / 3 scale 2 = 0.33
2 / 3 scale 2 = 0.67
1 / 6 scale 2 HALF_EVEN = 0.17
1 / 3 precision 5 = 0.33333
10 / 6 precision 3 = 1.67
dividend scale 0 = 0
dividend scale 2 = 0.33
1.00 / 8 exact = 0.125
1.00 / 8 scale 2 = 0.13
UNLIMITED -> ArithmeticException
UNNECESSARY -> ArithmeticException
division by zero -> ArithmeticException
```

Bốn điểm cần quan sát:

- `1 / 3` exact ném ngoại lệ, còn `1 / 8` exact trả `0.125`.
- Khi dùng scale hoặc precision tường minh, kết quả của `1 / 3` đã là **giá trị gần đúng**.
- `1 / 3` với `divide(divisor, HALF_UP)` cho 0 nếu dividend mang scale 0; `1.00 / 3` với cùng overload cho `0.33`.
- Không nên bắt `ArithmeticException` rồi coi mọi trường hợp đều do thương tuần hoàn: chia cho số 0 hoặc mode cấm làm tròn cũng có thể ném cùng loại ngoại lệ.

Ví dụ in **tên loại ngoại lệ** thay vì `ex.getMessage()` bởi nội dung thông báo lỗi không nên được dùng làm hợp đồng ổn định của một ứng dụng.

## 6. Những sai lầm dễ mắc và cách tránh

### Sai lầm A: chia exact trước, rồi mới setScale

```java
BigDecimal rounded = BigDecimal.ONE
        .divide(new BigDecimal("3")) // Ném ArithmeticException trước
        .setScale(2, RoundingMode.HALF_UP);
```

Phương thức `setScale` **không bao giờ được gọi**, vì `divide` đã thất bại. Nếu chấp nhận làm tròn, hãy dùng:

```java
BigDecimal rounded = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP);
```

### Sai lầm B: chuyển sang double để né ArithmeticException

```java
double approximate = 1.0 / 3.0;
BigDecimal back = BigDecimal.valueOf(approximate);
```

`double` chứa một xấp xỉ nhị phân của `1/3`; chuyển ngược sang `BigDecimal` không khôi phục được giá trị hữu tỉ chính xác. Với miền nhạy cảm về decimal/đối soát, cách này dễ tạo thêm sai số và che giấu chính sách chưa xác định.

Nếu bài toán **chủ đích** dùng số thực gần đúng nhị phân, `double` là một lựa chọn kỹ thuật có thể phù hợp; nhưng không nên coi đó là “cách sửa” cho phép chia decimal cần được định nghĩa rõ.

### Sai lầm C: mặc định scale 2 cho mọi phép chia

Các bài toán đo lường, tỷ lệ hoặc tính toán khoa học có thể cần precision cao hơn hoặc quy tắc làm tròn ở bước khác. Hai chữ số thập phân không phải chính sách chung chỉ vì dùng `BigDecimal` hay dữ liệu được ghi trong một cột decimal.

### Sai lầm D: nhầm “không giới hạn precision” với “được phép vô hạn chữ số”

`MathContext.UNLIMITED` không cung cấp một cơ chế lưu dãy `0.333…` vô hạn; nó yêu cầu phép tính exact và có thể ném ngoại lệ.

### Sai lầm E: nuốt ArithmeticException rồi trả 0

```java
try {
    return amount.divide(divisor);
} catch (ArithmeticException ex) {
    return BigDecimal.ZERO; // Sai: che giấu lỗi thành kết quả 0
}
```

Đây là **phản ví dụ**, không phải một phương thức độc lập để sử dụng. Một lỗi chia cho 0, một tỷ lệ tuần hoàn và một ràng buộc exact không được phép tùy tiện biến thành giá trị 0. Cần xử lý ở ranh giới phù hợp, chọn overload đúng, hoặc nêu lỗi rõ theo hợp đồng.

### Sai lầm F: không tính phần dư sau phân bổ

Khi làm tròn từng phần của một tổng, tổng các phần đã làm tròn có thể khác số gốc. Nếu hệ thống yêu cầu bảo toàn tổng, cần một quy tắc phân bổ phần dư; phép chia làm tròn không tự làm điều này.

## 7. Thiết kế API theo chính sách nghiệp vụ

Một hàm dùng để chia theo policy có thể nhận scale và mode được xác định từ cấu hình nghiệp vụ. Ví dụ kiểu record Java 17:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record DivisionPolicy(int scale, RoundingMode mode) {
    public DivisionPolicy {
        Objects.requireNonNull(mode, "mode");
    }

    public BigDecimal apply(BigDecimal a, BigDecimal b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        return a.divide(b, scale, mode);
    }
}
```

Đây là kiểu Java `record` có thể lưu vào `DivisionPolicy.java`, **không phải lớp chạy độc lập có `main()`**. Trong ứng dụng, các quyết định như *chia tại bước nào*, *làm tròn từng khoản hay tổng*, *mode áp dụng cho số âm* và *cách phân bổ số dư* vẫn cần được xác định ở tầng nghiệp vụ.

Ví dụ minh họa khi đã có kiểu `DivisionPolicy`:

```java
DivisionPolicy policy =
        new DivisionPolicy(2, RoundingMode.HALF_UP);

BigDecimal part = policy.apply(
        new BigDecimal("10.00"), new BigDecimal("3"));
// part = 3.33; ba phần như nhau chỉ cộng thành 9.99
```

Chú ý: `new DivisionPolicy(2, HALF_UP)` chỉ là một chính sách minh họa, không phải quy định cho mọi đồng tiền hoặc đơn vị.

### Nếu thật sự cần lưu giá trị tỷ số exact

Thay vì ép tỷ số `1/3` vào một decimal hữu hạn, có thể mô hình hóa nó thành **tử số + mẫu số** dưới dạng số nguyên (`BigInteger`), chuẩn hóa phân số và chỉ chuyển sang `BigDecimal` ở bước có chính sách làm tròn:

```text
numerator = 1
denominator = 3

ratio exact = 1/3
decimal view with scale=2, HALF_UP = 0.33
```

Đây là gợi ý **mô hình hóa**, không phải một lớp có sẵn tên `Rational` trong thư viện chuẩn `java.math`. Khi sử dụng phân số, cần xử lý mẫu khác 0, dấu, rút gọn và giới hạn tài nguyên.

## 8. Kiểm thử các trường hợp quan trọng

Một bộ test tốt không chỉ kiểm tra `1/3` mà còn cả các nhánh hành vi:

| Trường hợp | Kỳ vọng |
|---|---|
| `BigDecimal.ONE.divide(new BigDecimal("8"))` | `0.125` |
| `BigDecimal.ONE.divide(new BigDecimal("40"))` | `0.025` |
| `new BigDecimal("3").divide(new BigDecimal("6"))` | `0.5` |
| `BigDecimal.ONE.divide(new BigDecimal("3"))` | `ArithmeticException` |
| `BigDecimal.ONE.divide(new BigDecimal("3"), 2, HALF_UP)` | `0.33` |
| `new BigDecimal("2").divide(new BigDecimal("3"), 2, HALF_UP)` | `0.67` |
| `BigDecimal.ONE.divide(new BigDecimal("3"), MathContext.UNLIMITED)` | `ArithmeticException` |
| `BigDecimal.ONE.divide(new BigDecimal("3"), 2, UNNECESSARY)` | `ArithmeticException` |
| `BigDecimal.ONE.divide(BigDecimal.ZERO)` | `ArithmeticException` |
| `new BigDecimal("1.00").divide(new BigDecimal("8"))` | `0.125` |
| `new BigDecimal("1.00").divide(new BigDecimal("8"), 2, HALF_UP)` | `0.13` |

Ngoài ra cần kiểm tra dấu âm, điểm giữa của các mode làm tròn, scale âm khi có dữ liệu kiểu ký pháp khoa học, và quy tắc phân bổ số dư nếu nghiệp vụ có nhiều phần.

Khi so sánh kỳ vọng với kết quả `BigDecimal`:

- `equals()` kiểm tra **cả giá trị và scale**. Ví dụ `0.50` không `equals()` với `0.5`.
- `compareTo() == 0` kiểm tra **giá trị số học** bất kể scale.

Chọn cách assertion tùy hợp đồng: nếu scale đích là một phần của yêu cầu, bài test cần kiểm tra scale; nếu chỉ yêu cầu bằng nhau về giá trị số học, so sánh bằng `compareTo` sẽ phù hợp.

## 9. Câu hỏi tự kiểm tra

**1. Tại sao hai số nguyên BigDecimal có thể chia nhau mà ném ArithmeticException?**  
Vì thương exact có thể là thập phân vô hạn tuần hoàn, không thể lưu hoàn chỉnh trong một `BigDecimal` hữu hạn.

**2. Khi nào `divide(divisor)` trả kết quả chính xác?**  
Khi thương có biểu diễn thập phân hữu hạn. Với phân số tối giản, mẫu chỉ chứa thừa số nguyên tố 2 và/hoặc 5.

**3. Vì sao `3/6` chia exact được nhưng `1/6` thì không?**  
`3/6` rút gọn thành `1/2`; `1/6` đã tối giản và mẫu còn thừa số 3.

**4. `divide(divisor, 2, HALF_UP)` có phải trả thương exact cho `1/3` không?**  
Không. Nó trả `0.33`, một giá trị đã làm tròn theo chính sách.

**5. `divide(divisor, HALF_UP)` có dùng scale mặc định 2 không?**  
Không. Nó dùng **scale của dividend**, nên cùng giá trị đầu vào nhưng khác scale có thể trả kết quả khác.

**6. `MathContext.UNLIMITED` có làm thương tuần hoàn hết ném ngoại lệ không?**  
Không. Unlimited ở đây vẫn đòi exact.

**7. Ba khoản 0.33 cộng được 0.99, muốn phân bổ đủ 1.00 thì làm sao?**  
Cần quy tắc phân bổ phần dư 0.01 theo nghiệp vụ; `divide` không tự quyết định.

**8. Vì sao không nên bắt ArithmeticException rồi trả BigDecimal.ZERO?**  
Vì như vậy biến tình huống không có thương exact hoặc chia cho 0 thành kết quả 0 sai lệch, che giấu lỗi.

**Ghi nhớ:** `BigDecimal.divide(divisor)` không tự làm tròn kết quả tuần hoàn. Muốn có thương decimal hữu hạn đã lượng hóa, phải chỉ định **scale hoặc precision và RoundingMode** một cách tường minh. Nếu yêu cầu exact, xử lý ngoại lệ như một ràng buộc của phép tính, không giấu nó bằng cách chuyển sang `double` hoặc tự trả 0.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-009">Nhầm precision với số chữ số sau dấu thập phân</a>

<details>
<summary>Xem chi tiết</summary>

# Nhầm precision với số chữ số sau dấu thập phân trong Java `BigDecimal`

## 1. Sai lầm cốt lõi

Trong Java, **precision** và **scale** của `BigDecimal` là hai khái niệm khác nhau:

- **Precision (độ chính xác chữ số):** số chữ số có nghĩa trong biểu diễn thập phân của giá trị không scale. `BigDecimal.precision()` trả số chữ số của unscaled value, bỏ dấu; riêng zero có precision 1.
- **Scale:** số mũ điều chỉnh vị trí dấu thập phân trong công thức `unscaledValue × 10^(-scale)`. Với dạng thập phân thông thường có scale không âm, scale thường tương ứng số chữ số sau dấu thập phân của **biểu diễn hiện tại**. Scale cũng có thể âm.

Ví dụ, `new BigDecimal("123.4500")` có **precision = 7**, **scale = 4**, không phải precision = 4. Khi viết `new MathContext(3, HALF_UP)`, số 3 **không** có nghĩa “giữ ba chữ số sau dấu thập phân”; nó có nghĩa “tối đa ba chữ số có nghĩa”.

So sánh:

~~~java
BigDecimal number = new BigDecimal("1234.567");
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

System.out.println(number.round(mc));                         // 1.23E+3
System.out.println(number.setScale(3, RoundingMode.HALF_UP));  // 1234.567
~~~

Đây là **đoạn minh họa cần import `java.math.*` và đặt trong phương thức**; chương trình chạy độc lập ở phần 9.

Cả hai lời gọi đều chứa số 3, nhưng có ý nghĩa hoàn toàn khác:

- `round(mc)`: chỉ giữ ba chữ số có nghĩa, nên 1234.567 trở thành khoảng 1230, được in `1.23E+3`.
- `setScale(3, ...)`: yêu cầu đúng ba chữ số sau dấu thập phân, mà `1234.567` đã có sẵn, nên không cần làm tròn.

**Cách ghi nhớ:** `MathContext` điều khiển **số chữ số có nghĩa của toàn bộ kết quả**; `setScale` điều khiển **vị trí dấu thập phân/số chữ số thập phân của biểu diễn**.

## 2. Công thức nền tảng: `unscaledValue`, scale, precision

Một `BigDecimal` gồm một số nguyên không scale và một scale nguyên:

~~~text
numericValue = unscaledValue × 10^(-scale)
~~~

Ví dụ:

| Chuỗi đầu vào | Unscaled value | Scale | Precision |
| --- | ---: | ---: | ---: |
| `"12"` | 12 | 0 | 2 |
| `"12.0"` | 120 | 1 | 3 |
| `"12.00"` | 1200 | 2 | 4 |
| `"123.4500"` | 1234500 | 4 | 7 |
| `"0.00120"` | 120 | 5 | 3 |
| `"0.0000"` | 0 | 4 | 1 |
| `"1E+3"` | 1 | -3 | 1 |

Đọc bảng đúng cách:

1. `"0.00120"` có 5 vị trí sau dấu thập phân nhưng chỉ có **3 chữ số có nghĩa**: 1, 2 và 0 tận cùng. Các zero đứng trước chữ số 1 chỉ xác định vị trí dấu thập phân.
2. `"123.4500"` có **7 chữ số có nghĩa** trong unscaled value 1234500. Các zero tận cùng vẫn là chữ số được lưu trong biểu diễn và được tính vào precision của đối tượng hiện tại.
3. `"1E+3"` có giá trị toán học 1000 nhưng scale **-3**: `1 × 10^3`. Không thể định nghĩa scale chỉ bằng cách “đếm dấu chấm” trên chuỗi `toString()`.
4. Zero có precision 1, nhưng có thể có nhiều scale khác nhau. `"0"` và `"0.0000"` bằng nhau về giá trị, không giống nhau về scale.

Không nên suy đoán precision hoặc scale chỉ từ độ lớn số, cũng không nên bỏ qua trailing zeros nếu hợp đồng dữ liệu cần giữ chúng.

### 2.1. `toString()` không nhất thiết cho dạng thập phân thường

Sau phép làm tròn hoặc normalize, `BigDecimal.toString()` có thể dùng ký pháp mũ như `1.23E+3`. Điều này không có nghĩa số đã biến thành kiểu `double`, cũng không phải lỗi tính toán; đó là cách biểu diễn văn bản của `BigDecimal` với precision/scale hiện tại.

`toPlainString()` cho chuỗi không sử dụng ký pháp mũ. Tuy nhiên, không nên dùng chuỗi hiển thị để xác định precision hoặc scale; dùng trực tiếp `precision()` và `scale()`.

## 3. `precision()` của đối tượng và precision trong `MathContext`

Hai chữ “precision” liên quan nhưng có vai trò khác nhau:

- `value.precision()` **đọc trạng thái hiện tại** của `BigDecimal`: bao nhiêu chữ số có nghĩa đang được lưu trong unscaled value.
- `new MathContext(p, mode)` **đặt giới hạn precision cho kết quả phép toán sử dụng context**, với `p > 0` là số chữ số có nghĩa tối đa.

Ví dụ:

~~~java
BigDecimal value = new BigDecimal("123.4500");
System.out.println(value.precision()); // 7
System.out.println(value.scale());     // 4

BigDecimal rounded = value.round(
        new MathContext(4, RoundingMode.HALF_UP));
System.out.println(rounded);            // 123.5
System.out.println(rounded.precision()); // 4
System.out.println(rounded.scale());     // 1
~~~

`MathContext` không chỉ “đặt cờ metadata”. Khi kết quả chính xác có nhiều chữ số có nghĩa hơn giới hạn, `BigDecimal` **làm tròn giá trị** theo rounding mode. Có trường hợp làm tròn chỉ loại bỏ zero tận cùng mà không đổi giá trị; có trường hợp làm tròn thật sự thay đổi số.

### 3.1. Precision = 0 trong `MathContext`

~~~java
MathContext unlimited = MathContext.UNLIMITED;
// unlimited.getPrecision() == 0
~~~

Precision bằng 0 trong `MathContext` có nghĩa **không giới hạn chữ số có nghĩa**, không phải “giữ 0 chữ số” và không phải “giữ 0 số sau dấu thập phân”. Nó không khiến các phép toán thập phân không có kết quả hữu hạn, như `1 ÷ 3`, tự có biểu diễn `BigDecimal` chính xác hữu hạn.

`MathContext.DECIMAL32`, `DECIMAL64`, `DECIMAL128` lần lượt có precision 7, 16 và 34, sử dụng `HALF_EVEN`. Chúng cũng điều khiển **chữ số có nghĩa**, không phải scale 7/16/34.

## 4. `setScale()`: quy tắc về vị trí dấu thập phân

Khi yêu cầu là “giá trị có **đúng hai chữ số sau dấu chấm**”, thường dùng:

~~~java
BigDecimal stored = value.setScale(2, RoundingMode.HALF_EVEN);
~~~

`setScale(2, ...)` trả `BigDecimal` có scale đúng 2 hoặc ném ngoại lệ nếu không thực hiện được theo quy tắc đã chỉ định. Với dạng thường, kết quả có đúng hai chữ số thập phân khi in theo biểu diễn scale 2.

Các ví dụ:

| Giá trị ban đầu | Gọi | Kết quả |
| --- | --- | --- |
| `12` | `setScale(2, HALF_UP)` | `12.00` |
| `12.3` | `setScale(2, HALF_UP)` | `12.30` |
| `12.345` | `setScale(2, HALF_UP)` | `12.35` |
| `12.345` | `setScale(2, HALF_EVEN)` | `12.34` |
| `-1.236` | `setScale(2, HALF_UP)` | `-1.24` |
| `12.3400` | `setScale(2, UNNECESSARY)` | `12.34` |

`setScale` có thể tăng scale bằng cách thêm zero mà không đổi giá trị; khi giảm scale, nó cần bỏ bớt chữ số và có thể phải làm tròn. Vì vậy, phải xác định **rounding mode** theo yêu cầu nghiệp vụ, không chỉ đặt scale.

Nếu cấm thay đổi giá trị, dùng `RoundingMode.UNNECESSARY`:

~~~java
BigDecimal exact = new BigDecimal("12.3400")
        .setScale(2, RoundingMode.UNNECESSARY); // 12.34

// Ném ArithmeticException nếu thực thi:
// new BigDecimal("12.345").setScale(2, RoundingMode.UNNECESSARY);
~~~

**`UNNECESSARY` không có nghĩa là làm tròn xuống hoặc bỏ qua chữ số:** nó yêu cầu việc điều chỉnh scale phải **chính xác**, nếu không báo lỗi.

## 5. `round(new MathContext(...))`: giới hạn chữ số có nghĩa

Xét cùng một số dưới hai phép biến đổi:

| Đầu vào | Precision 3 với `HALF_UP` | Scale 3 với `HALF_UP` |
| --- | --- | --- |
| `1234.567` | `1.23E+3` (= 1230) | `1234.567` |
| `0.0012345` | `0.00123` | `0.001` |
| `9.995` | `10.0` | `9.995` |
| `12.34` | `12.3` | `12.340` |

Tại sao `0.0012345` với precision 3 được `0.00123`? Các số 0 trước chữ số 1 không chiếm precision. Trong khi đó, `setScale(3)` chỉ cho phép ba chữ số sau dấu chấm nên phải đổi thành `0.001`.

### 5.1. Carry làm thay đổi phần nguyên và scale

Ví dụ:

~~~java
BigDecimal value = new BigDecimal("9.995");
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

BigDecimal rounded = value.round(mc);
System.out.println(rounded);             // 10.0
System.out.println(rounded.precision());  // 3
System.out.println(rounded.scale());      // 1

BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
System.out.println(scaled);              // 10.00
System.out.println(scaled.precision());   // 4
System.out.println(scaled.scale());       // 2
~~~

Cả hai đều có giá trị số học bằng 10 sau khi làm tròn, nhưng **precision và scale của kết quả khác nhau**. Chúng không thể thay thế nhau trong một hợp đồng yêu cầu scale 2.

Khi phép làm tròn tạo carry từ 9 lên 10, độ dài phần nguyên có thể tăng. Điều này rất quan trọng khi kiểm tra giới hạn cột database, miền giá trị tiền tệ hoặc độ rộng báo cáo.

### 5.2. Trailing zeros vẫn ảnh hưởng precision

~~~java
BigDecimal value = new BigDecimal("1000.00");
System.out.println(value.precision()); // 6
System.out.println(value.scale());     // 2

BigDecimal result = value.round(
        new MathContext(3, RoundingMode.HALF_UP));
System.out.println(result);            // 1.00E+3
~~~

Ở ví dụ này, phép làm tròn có thể **loại bỏ chữ số 0 dư** mà không mất giá trị toán học. Kết quả `1.00E+3` có precision 3 và scale -1, không phải scale 2.

Nếu yêu cầu là giữ hai chữ số sau dấu chấm trong đầu ra, **`MathContext(3)` không thực hiện yêu cầu ấy**.

## 6. `MathContext` trong các phép tính không giống `setScale` cuối cùng

Một số phương thức `BigDecimal` nhận `MathContext`:

~~~java
BigDecimal total = left.add(right, mc);
BigDecimal product = left.multiply(right, mc);
BigDecimal quotient = left.divide(right, mc);
~~~

Khi context giới hạn precision, **kết quả của từng phép toán** có thể bị làm tròn tới số chữ số có nghĩa được cho phép. Điều này khác với chiến lược:

~~~java
BigDecimal exactTotal = left.add(right);
BigDecimal stored = exactTotal.setScale(2, RoundingMode.HALF_EVEN);
~~~

Cách thứ nhất hạn chế **chữ số có nghĩa ở mỗi kết quả của phép toán**; cách thứ hai lấy tổng chính xác rồi chuẩn hóa về **scale 2** theo chính sách lưu trữ.

Nếu cộng nhiều khoản tiền, hai quy tắc có thể cho tổng khác nhau. Ví dụ với `1.234 + 0.004 + 0.004` và `MathContext(3, HALF_UP)`:

- Cộng chính xác được `1.242`, rồi `round(mc)` thành `1.24`.
- Dùng `add(x, mc)` ở **từng lần cộng** có thể ra `1.23`, vì các phần nhỏ đã mất khi làm tròn trung gian.

Nếu hợp đồng chỉ yêu cầu “tổng cuối cùng có hai chữ số sau dấu chấm”, không được tự coi `new MathContext(2, ...)` là cùng một yêu cầu. Nó sẽ giới hạn toàn bộ tổng chỉ còn **hai chữ số có nghĩa**, kể cả các chữ số phần nguyên.

### 6.1. Phép chia cho thấy khác biệt rõ hơn

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = new BigDecimal("8");

BigDecimal byScale = one.divide(
        eight, 2, RoundingMode.HALF_UP); // 0.13

BigDecimal byPrecision = one.divide(
        eight, new MathContext(2, RoundingMode.HALF_UP)); // 0.13
~~~

Hai kết quả **tình cờ bằng nhau trong phép chia này**. Nhưng nếu đổi số bị chia hoặc tỷ lệ, ý nghĩa vẫn khác: `divide(divisor, scale, mode)` chọn scale kết quả; `divide(divisor, MathContext)` chọn precision kết quả. Không suy ra hai overload tương đương từ một test đơn lẻ.

Ví dụ phân biệt rõ:

~~~java
BigDecimal numerator = new BigDecimal("1234");
BigDecimal denominator = new BigDecimal("8");

BigDecimal byScale = numerator.divide(
        denominator, 2, RoundingMode.HALF_UP); // 154.25

BigDecimal byPrecision = numerator.divide(
        denominator, new MathContext(2, RoundingMode.HALF_UP)); // 1.5E+2
~~~

Với context precision 2, kết quả 154.25 phải làm tròn còn 150, còn scale 2 giữ kết quả chính xác 154.25.

## 7. Thiết kế dữ liệu: precision và scale trong database

Trong nhiều hệ quản trị cơ sở dữ liệu, `DECIMAL(p, s)` hoặc `NUMERIC(p, s)` dùng:

- `p`: tổng số chữ số thập phân được phép theo định nghĩa cột.
- `s`: số chữ số sau dấu thập phân, theo quy định của hệ quản trị/cột.

Ví dụ thông thường `DECIMAL(5,2)` cho phép các giá trị như `999.99`, nhưng `1234.56` vượt phần nguyên tối đa ba chữ số của định dạng đó.

Điều này **không có nghĩa** cứ dùng `new MathContext(5, ...)` là đã đảm bảo tương thích `DECIMAL(5,2)`. Context không cố định scale và có thể đổi giá trị. Cần hai bước kiểm tra theo chính sách rõ:

~~~java
BigDecimal scaled = value.setScale(2, RoundingMode.UNNECESSARY);
if (scaled.precision() > 5) {
    throw new IllegalArgumentException("Gia tri qua gioi han DECIMAL(5,2)");
}
~~~

Đây là **đoạn minh họa**, phải đặt trong phương thức có biến `value` kiểu `BigDecimal`. Với hợp đồng cấm làm tròn, `UNNECESSARY` bảo đảm không đổi giá trị; sau đó `precision() > 5` từ chối các giá trị vượt tổng chữ số của biểu diễn đã chuẩn hóa. Nếu ứng dụng cho phép làm tròn trước lưu, chính sách khác sẽ dùng rounding mode được phê duyệt trước bước kiểm tra precision.

**Cẩn trọng:** mỗi database có quy tắc riêng về giới hạn miền số, xử lý overflow, quy tắc rounding khi ghi, và khả năng hỗ trợ scale âm. Cần đọc schema và kiểm chứng database/driver thực tế. Ví dụ ở đây nhằm diễn đạt hai điều kiện độc lập, không thay thế kiểm thử tích hợp.

### 7.1. Tràn sau khi làm tròn

Một số nằm sát ngưỡng có thể thành số **nhiều chữ số phần nguyên hơn** sau làm tròn. Ví dụ nếu chuẩn hóa scale 2, giá trị `999.995` với `HALF_UP` thành `1000.00`. Giá trị sau làm tròn này không phù hợp với `DECIMAL(5,2)` thông thường, dù giá trị nguồn có thể trông gần giới hạn.

Vì thế kiểm tra precision/miền giá trị **sau khi áp quy tắc scale đã chọn**, không chỉ trước khi làm tròn.

## 8. Kiểm thử đúng hợp đồng: số, scale, precision và chuỗi

Một bộ assertion tốt phân biệt bốn điều kiện:

| Hợp đồng | Kiểm tra gợi ý |
| --- | --- |
| Giá trị toán học bằng nhau | `expected.compareTo(actual) == 0` |
| Giá trị và scale cùng đúng | `expected.equals(actual)` hoặc hai assertion riêng |
| Scale chính xác bằng 2 | `actual.scale() == 2` |
| Precision không vượt 5 | `actual.precision() <= 5` sau bước normalize phù hợp |
| Chuỗi trao đổi đúng dạng `"12.00"` | Test riêng kết quả serialize hoặc format |

Ví dụ JUnit 5, **đặt trong một phương thức test và thêm dependencies JUnit**:

~~~java
BigDecimal actual = new BigDecimal("12.00");

assertEquals(0, new BigDecimal("12").compareTo(actual));
assertEquals(2, actual.scale());
assertTrue(actual.precision() <= 5);
~~~

Các assertion trên dùng `org.junit.jupiter.api.Assertions.assertEquals` và `assertTrue`. Chúng kiểm tra ba yêu cầu **riêng**. `actual.equals(new BigDecimal("12"))` sẽ `false` vì khác scale mặc dù giá trị bằng nhau.

Nếu cần một số không âm có chính xác hai vị trí thập phân, ngoài scale còn có thể phải kiểm tra miền giá trị (ví dụ `signum() >= 0`). Không dùng precision/scale để suy ra tất cả ràng buộc nghiệp vụ.

### 8.1. `stripTrailingZeros` thay đổi precision và scale

~~~java
BigDecimal raw = new BigDecimal("1000.00");
BigDecimal compact = raw.stripTrailingZeros();

System.out.println(compact);             // 1E+3
System.out.println(compact.precision()); // 1
System.out.println(compact.scale());     // -3
~~~

`stripTrailingZeros()` loại zero tận cùng mà vẫn giữ **giá trị toán học**. Vì vậy nó thường làm precision giảm và scale giảm; scale có thể **âm**. Nó không phải phương thức đặt “số chữ số sau dấu thập phân” và không nên được dùng để che lỗi scale của một API cam kết `scale == 2`.

Với zero, `stripTrailingZeros()` có thể trả `BigDecimal.ZERO` có scale 0, dù ban đầu là `0.0000`. Nếu kiểm thử yêu cầu giữ scale của zero, phải kiểm tra riêng.

## 9. Chương trình Java 17+ hoàn chỉnh

Tạo tệp **PrecisionVsScaleDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class PrecisionVsScaleDemo {
    private static void inspect(String label, BigDecimal value) {
        System.out.println(label + ": " + value
                + " (precision=" + value.precision()
                + ", scale=" + value.scale() + ")");
    }

    public static void main(String[] args) {
        inspect("literal", new BigDecimal("123.4500"));
        inspect("small", new BigDecimal("0.00120"));
        inspect("zero", new BigDecimal("0.0000"));
        inspect("scientific", new BigDecimal("1E+3"));

        BigDecimal original = new BigDecimal("1234.567");
        MathContext threeDigits =
                new MathContext(3, RoundingMode.HALF_UP);

        inspect("round precision3", original.round(threeDigits));
        inspect("set scale3",
                original.setScale(3, RoundingMode.HALF_UP));

        BigDecimal small = new BigDecimal("0.0012345");
        System.out.println("small precision3: "
                + small.round(threeDigits));
        System.out.println("small scale3: "
                + small.setScale(3, RoundingMode.HALF_UP));

        inspect("carry precision3",
                new BigDecimal("9.995").round(threeDigits));
        inspect("carry scale2",
                new BigDecimal("9.995")
                        .setScale(2, RoundingMode.HALF_UP));

        inspect("round trailing zeros",
                new BigDecimal("1000.00").round(threeDigits));

        BigDecimal a = new BigDecimal("12.0");
        BigDecimal b = new BigDecimal("12.00");
        System.out.println("same numeric value: "
                + (a.compareTo(b) == 0));
        System.out.println("same value and scale: "
                + a.equals(b));

        try {
            new BigDecimal("12.345")
                    .setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("strict scale: ArithmeticException");
        }
    }
}
~~~

Chạy:

~~~bash
javac PrecisionVsScaleDemo.java
java PrecisionVsScaleDemo
~~~

Đầu ra chính xác:

~~~text
literal: 123.4500 (precision=7, scale=4)
small: 0.00120 (precision=3, scale=5)
zero: 0.0000 (precision=1, scale=4)
scientific: 1E+3 (precision=1, scale=-3)
round precision3: 1.23E+3 (precision=3, scale=-1)
set scale3: 1234.567 (precision=7, scale=3)
small precision3: 0.00123
small scale3: 0.001
carry precision3: 10.0 (precision=3, scale=1)
carry scale2: 10.00 (precision=4, scale=2)
round trailing zeros: 1.00E+3 (precision=3, scale=-1)
same numeric value: true
same value and scale: false
strict scale: ArithmeticException
~~~

Chương trình này chỉ dùng Java standard library. Các dòng `inspect` cho thấy một số 3 trong `MathContext` và một số 3 trong `setScale` tác động đến **hai thuộc tính khác nhau**. Chú ý số `9.995` sau làm tròn có cùng giá trị 10 nhưng biểu diễn `10.0` hoặc `10.00` tùy chính sách.

## 10. Quy tắc lựa chọn đúng API

### Muốn đúng N chữ số có nghĩa

Sử dụng `MathContext`:

~~~java
MathContext mc = new MathContext(4, RoundingMode.HALF_EVEN);
BigDecimal rounded = value.round(mc);
~~~

Giới hạn **precision tối đa 4** của kết quả, không ép scale thành 4. Nó thích hợp khi hợp đồng mô tả kết quả theo chữ số có nghĩa, ví dụ báo cáo đo lường hoặc tính toán có giới hạn precision.

### Muốn đúng N chữ số sau dấu thập phân

Sử dụng `setScale`:

~~~java
BigDecimal scaled = value.setScale(2, RoundingMode.HALF_EVEN);
~~~

Kết quả có **scale đúng 2** khi thành công. Nó phù hợp với một số hợp đồng lưu trữ, hiển thị thập phân có scale cố định, hoặc quy tắc tiền tệ đã định nghĩa rõ thời điểm làm tròn.

### Muốn không được thay đổi giá trị

Đặt `UNNECESSARY` trong phép chuẩn hóa hoặc phép toán tương ứng:

~~~java
BigDecimal exactAtTwoPlaces =
        value.setScale(2, RoundingMode.UNNECESSARY);
~~~

Nếu không thể biểu diễn chính xác giá trị ở scale yêu cầu, sẽ có `ArithmeticException`. `UNNECESSARY` không tự bảo đảm miền giá trị hay số chữ số phần nguyên.

### Muốn vừa scale 2 vừa giới hạn tổng chữ số

Chuẩn hóa **theo policy được duyệt**, rồi kiểm tra cả scale lẫn precision/miền giá trị, ví dụ:

~~~java
BigDecimal normalized =
        value.setScale(2, RoundingMode.UNNECESSARY);

if (normalized.precision() > 5) {
    throw new IllegalArgumentException("Precision vuot 5");
}
~~~

Nếu hợp đồng cho phép rounding, thay `UNNECESSARY` bằng mode được phê duyệt và kiểm tra dữ liệu **sau** làm tròn. Không dùng `round(new MathContext(5, ...))` để thay thế hoàn toàn kiểm tra `DECIMAL(5,2)`.

## 11. Những lỗi hay gặp

| Hiểu lầm | Điều đúng |
| --- | --- |
| `MathContext(2)` có nghĩa hai số sau dấu chấm | Precision 2 là hai chữ số có nghĩa trên toàn bộ kết quả |
| `precision()` đếm chữ số sau dấu chấm | Nó đếm chữ số trong unscaled value, bỏ dấu; zero có precision 1 |
| `scale()` luôn không âm | Scale có thể âm, chẳng hạn `1E+3` có scale -3 |
| `0.00120` có precision 5 | Precision là 3, còn scale là 5 |
| Rounding chỉ thay phần thập phân | `MathContext` có thể làm tròn cả chữ số phần nguyên và đổi dạng sang ký pháp mũ |
| `setScale` luôn chỉ thêm số 0 | Khi giảm scale, nó có thể đổi giá trị theo mode hoặc ném lỗi |
| `UNNECESSARY` nghĩa là bỏ qua rounding | Nó từ chối thao tác phải thay đổi giá trị |
| `BigDecimal.toString()` luôn in dấu chấm thông thường | Có thể in dạng mũ, tùy scale và cách biểu diễn |
| Bằng giá trị số thì bằng scale | `compareTo == 0` không bảo đảm `equals == true` |
| `DECIMAL(5,2)` chỉ cần `MathContext(5)` | Phải kiểm tra cả scale, precision và giới hạn của DB |
| `stripTrailingZeros()` đặt scale thành 0 | Nó có thể tạo scale âm và thay đổi biểu diễn của zero |

## 12. Tự kiểm tra

1. `new BigDecimal("123.4500").precision()` là bao nhiêu? **7**; scale là **4**.
2. `new BigDecimal("0.00120")` có precision 5 vì có 5 chữ số sau dấu chấm không? **Không**; precision là **3**, scale là **5**.
3. Với `1234.567`, `MathContext(3, HALF_UP)` và `setScale(3, HALF_UP)` lần lượt trả gì? **`1.23E+3`** và **`1234.567`**.
4. Vì sao `new BigDecimal("1E+3").scale()` bằng -3? **Giá trị = 1 × 10^3**, theo công thức scale là -3.
5. `new BigDecimal("12").setScale(2)` có thay giá trị số không? **Không**; nó trở thành `12.00` với scale 2.
6. `new BigDecimal("12.345").setScale(2, UNNECESSARY)` có thành công không? **Không**; phải bỏ chữ số 5, nên ném `ArithmeticException`.
7. Vì sao `9.995` có thể thành `10.0` với precision 3, còn thành `10.00` với scale 2? **Hai ràng buộc biểu diễn khác nhau dù giá trị sau làm tròn bằng nhau**.
8. Nếu API yêu cầu scale đúng 2 và giá trị chính xác, chỉ `MathContext(2)` có đủ không? **Không**; phải dùng `setScale(2, UNNECESSARY)` hoặc kiểm tra tương đương, cùng các ràng buộc còn lại.
9. Khi hai `BigDecimal` có giá trị bằng nhau nhưng scale khác nhau, nên kiểm tra bằng gì? **`compareTo == 0`** nếu hợp đồng chỉ xét giá trị; **`equals`** hoặc kiểm tra riêng scale nếu hợp đồng yêu cầu cả hai.

**Ghi nhớ cuối cùng:** **Precision = số chữ số có nghĩa; scale = vị trí dấu thập phân trong biểu diễn.** Dùng `MathContext` khi cần giới hạn precision của phép toán, dùng `setScale` khi cần đúng số chữ số sau dấu thập phân, và kiểm tra cả hai khi hợp đồng dữ liệu yêu cầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-010">Dùng phép trừ trong Comparator có thể tràn</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng phép trừ trong Comparator có thể tràn số trong Java

## 1. Lỗi thường gặp: dùng hiệu số làm kết quả so sánh

Một Comparator số nguyên thường được viết rất ngắn:

```java
Comparator<Integer> bad = (a, b) -> a - b;
```

Cách viết này có vẻ hợp lý: nếu a nhỏ hơn b, hiệu âm; nếu bằng nhau, hiệu bằng 0; nếu lớn hơn, hiệu dương. Nhưng phép trừ trên kiểu `int` và `long` có thể **overflow**. Khi hiệu toán học không biểu diễn được trong kiểu dữ liệu, dấu của kết quả Java có thể sai, khiến Comparator mô tả một quan hệ thứ tự mâu thuẫn.

Ví dụ:

```java
int min = Integer.MIN_VALUE; // -2147483648
int zero = 0;

System.out.println(min - zero); // -2147483648
System.out.println(zero - min); // -2147483648, đã overflow
```

Theo toán học, `0 - (-2147483648) = 2147483648` là số dương. Nhưng `Integer.MAX_VALUE` chỉ bằng `2147483647`. Trong phép trừ `int`, giá trị này trở thành `-2147483648`. Comparator sai sẽ cho rằng `min < zero` **và đồng thời** `zero < min`.

Giải pháp đúng:

```java
Comparator<Integer> good = Integer::compare;
```

Hoặc:

```java
Comparator<Integer> good = (a, b) -> Integer.compare(a, b);
```

`Integer.compare` so sánh số nguyên có dấu mà không tính hiệu có thể tràn. Với `long`, dùng `Long.compare`. Với thuộc tính của một đối tượng, ưu tiên `Comparator.comparingInt` hoặc `Comparator.comparingLong`.

**Ghi nhớ:** `Comparator.compare` chỉ yêu cầu số **âm, 0 hoặc dương** để biểu diễn thứ tự, không yêu cầu một giá trị đúng bằng khoảng cách giữa hai số.

## 2. Hợp đồng Comparator cần tuân thủ

`java.util.Comparator<T>` là functional interface có phương thức:

```java
int compare(T left, T right);
```

| Kết quả | Ý nghĩa |
| --- | --- |
| `< 0` | `left` đứng trước `right` |
| `== 0` | Hai đối tượng ngang nhau **theo quan hệ thứ tự của Comparator** |
| `> 0` | `left` đứng sau `right` |

Kết quả không nhất thiết là -1, 0 hoặc 1: bất kỳ số âm hay dương đều có thể hợp lệ nếu tuân thủ quy tắc. Vì thế phép trừ số học vốn không cần thiết.

Ba tính chất nền tảng:

1. **Phản đối xứng theo dấu:** `sgn(compare(a,b)) == -sgn(compare(b,a))`. Nếu a đứng trước b thì b phải đứng sau a.
2. **Bắc cầu:** nếu `compare(a,b) > 0` và `compare(b,c) > 0`, thì `compare(a,c) > 0`; tương tự với chiều âm.
3. **Tính nhất quán của phần tử ngang thứ tự:** khi `compare(a,b) == 0`, dấu của `compare(a,z)` và `compare(b,z)` phải trùng nhau với mọi z.

Comparator sai bởi overflow có thể vi phạm ngay tính chất thứ nhất: cả hai chiều so sánh đều âm. Những lần sort chạy thành công trên số nhỏ **không chứng minh** Comparator đúng cho mọi giá trị.

Một khái niệm liên quan nhưng khác là **nhất quán với equals**. Comparator hợp lệ vẫn có thể cho `compare(a,b) == 0` khi `!a.equals(b)`; đây là vấn đề thiết kế danh tính trong sorted collection. Nếu muốn `TreeSet` và `TreeMap` tuân thủ hoàn toàn ý nghĩa chung của `Set`/`Map` dựa trên `equals`, thứ tự cần nhất quán với `equals`.

## 3. Vì sao phép trừ bị tràn trước khi trả về?

Java `int` có miền từ `-2147483648` đến `2147483647`. Phép trừ `int` thông thường không tự ném lỗi khi vượt miền; giá trị wraparound theo số học 32-bit. Ví dụ:

```text
min = -2147483648
zero = 0

min - zero:
  -2147483648 → hợp lệ trong int → dấu âm

zero - min:
  +2147483648 → vượt Integer.MAX_VALUE
  kết quả trong int = -2147483648 → dấu âm SAI
```

Với các số nhỏ như 3 và 5, `a-b` dường như luôn đúng vì hiệu chưa vượt miền. Lỗi thường chỉ lộ khi so sánh số âm với số dương lớn, hoặc giá trị gần `Integer.MIN_VALUE`/`Integer.MAX_VALUE`.

`long` cũng có giới hạn: miền từ `Long.MIN_VALUE` đến `Long.MAX_VALUE`. Hiệu của hai giá trị `long` có thể cần nhiều hơn 64 bit có dấu, nên `(a,b) -> a-b` **vẫn nguy hiểm với long**.

Có một hiểu nhầm đáng tránh:

```java
long result = intA - intB; // phép trừ int có thể đã tràn
```

Dù biến nhận là `long`, phép trừ vẫn diễn ra bằng `int` khi hai toán hạng là `int`. Việc gán sang `long` không phục hồi giá trị đã mất. Trong trường hợp chỉ cần tính **hiệu chính xác của hai int**, `(long) intA - intB` có thể an toàn; nhưng một Comparator không cần hiệu, nên dùng `Integer.compare`.

## 4. Ví dụ hoàn chỉnh Java 17+: lỗi phản đối xứng, sort và TreeSet

Lưu `ComparatorOverflowDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class ComparatorOverflowDemo {
    public static void main(String[] args) {
        int min = Integer.MIN_VALUE;
        int zero = 0;

        Comparator<Integer> bad = (a, b) -> a - b;
        Comparator<Integer> good = Integer::compare;

        System.out.println("bad(min, zero): " + bad.compare(min, zero));
        System.out.println("bad(zero, min): " + bad.compare(zero, min));
        System.out.println("good(min, zero): " + good.compare(min, zero));
        System.out.println("good(zero, min): " + good.compare(zero, min));

        List<Integer> badSorted = new ArrayList<>(List.of(min, zero));
        badSorted.sort(bad);
        System.out.println("bad sorted: " + badSorted);

        List<Integer> goodSorted = new ArrayList<>(List.of(min, zero));
        goodSorted.sort(good);
        System.out.println("good sorted: " + goodSorted);

        TreeSet<Integer> badSet = new TreeSet<>(bad);
        badSet.add(min);
        badSet.add(zero);
        System.out.println("bad TreeSet first: " + badSet.first());

        TreeSet<Integer> goodSet = new TreeSet<>(good);
        goodSet.add(min);
        goodSet.add(zero);
        System.out.println("good TreeSet first: " + goodSet.first());
    }
}
```

Biên dịch và chạy:

```bash
javac ComparatorOverflowDemo.java
java ComparatorOverflowDemo
```

Stdout đã đối chiếu với Java 17+:

```text
bad(min, zero): -2147483648
bad(zero, min): -2147483648
good(min, zero): -1
good(zero, min): 1
bad sorted: [0, -2147483648]
good sorted: [-2147483648, 0]
bad TreeSet first: 0
good TreeSet first: -2147483648
```

Kết quả chứng minh:

- Comparator lỗi trả dấu âm ở **cả hai chiều** giữa `Integer.MIN_VALUE` và 0.
- `List.sort` với hai phần tử có thể trả thứ tự sai mà không ném ngoại lệ.
- `TreeSet` dùng Comparator sai cho `first() == 0`, trái với thứ tự số nguyên mong đợi.
- Comparator dùng `Integer.compare` cho kết quả đúng.

Trong những trường hợp khác, thuật toán sort có thể ném `IllegalArgumentException` kèm thông điệp như `Comparison method violates its general contract!`. Nhưng ngoại lệ đó **không phải bảo đảm sẽ xuất hiện cho mọi Comparator sai**. Thứ tự kết quả sai một cách im lặng còn nguy hiểm hơn.

## 5. Cách viết an toàn theo kiểu dữ liệu

### int/Integer

```java
Comparator<Integer> natural = Integer::compare;
Comparator<Integer> alsoNatural = Comparator.naturalOrder();
```

Với thuộc tính `int age()` của object:

```java
Comparator<Employee> byAge = Comparator.comparingInt(Employee::age);
```

`Employee` là kiểu minh họa do chương trình tự định nghĩa, chẳng hạn `record Employee(String name, int age) {}`; đoạn này chưa phải chương trình độc lập.

### long/Long

```java
Comparator<Long> byLong = Long::compare;
Comparator<Order> byAmount = Comparator.comparingLong(Order::amountCents);
```

`Order` là kiểu minh họa có phương thức `long amountCents()`. Cả hai cách dùng tránh phép trừ `long` có thể overflow.

**Không dùng** `(int)(aLong-bLong)`: có thể overflow ở phép trừ, rồi tiếp tục mất bit khi ép hiệu `long` về `int`. Hai số khác nhau có thể bị xem ngang thứ tự do hiệu sau cast thành 0.

### double/Double

```java
Comparator<Double> byDouble = Double::compare;
Comparator<Measurement> byMeasure =
        Comparator.comparingDouble(Measurement::value);
```

`Measurement` là kiểu minh họa có phương thức `double value()`. Không dùng `(int)(a-b)` với số thực: hiệu nhỏ hơn 1 về trị tuyệt đối sẽ thành 0 khi cast `int`, dù hai số khác nhau. Ngoài ra còn có NaN, vô cực và signed zero.

`Double.compare` có quy tắc cho các giá trị đó, chẳng hạn `Double.compare(-0.0,+0.0)<0` và NaN được xếp theo thứ tự wrapper. Hãy dùng API chuẩn thay vì tự xét dấu hiệu trừ.

### BigInteger/BigDecimal

```java
Comparator<java.math.BigInteger> integerOrder =
        java.math.BigInteger::compareTo;
Comparator<java.math.BigDecimal> decimalOrder =
        java.math.BigDecimal::compareTo;
```

Không tính `a.subtract(b).intValue()` cho `BigDecimal`: phép đổi sang `int` có thể cắt giá trị hoặc bỏ phần thập phân. `BigDecimal.compareTo` so theo giá trị số; lưu ý `2.0` và `2.00` ngang thứ tự dù `equals` có xét scale.

### Số unsigned

Nếu `int`/`long` đang lưu bit pattern của số không dấu, dùng `Integer.compareUnsigned` hoặc `Long.compareUnsigned`. Đó là thứ tự **khác** signed: `-1` trong unsigned 32-bit tương ứng giá trị lớn nhất. Chỉ dùng khi domain yêu cầu, không dùng unsigned như mẹo “chống overflow”.

## 6. Sắp xếp theo nhiều trường bằng thenComparing

Khi sort các đối tượng có nhiều tiêu chí, tránh viết biểu thức thủ công kiểu:

```java
// Sai: có thể overflow ngay tại phép trừ đầu.
return a.score() - b.score();
```

Nếu khóa chính bằng nhau, có thể so thêm khóa phụ; không nên cộng các hiệu hay cố gộp thành một giá trị `int`. Sử dụng:

- `Comparator.comparingInt` / `comparingLong` / `comparingDouble` để lấy khóa primitive.
- `thenComparingInt` / `thenComparingLong` / `thenComparingDouble` cho khóa phụ.
- `thenComparing` để so thêm String, Comparable hoặc một comparator khác.

Ví dụ đầy đủ lưu `ComparatorFieldsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComparatorFieldsDemo {
    record Item(String id, long cents, int priority) {}

    public static void main(String[] args) {
        List<Item> items = new ArrayList<>(List.of(
                new Item("C", 250L, 2),
                new Item("B", -10L, 4),
                new Item("A", 250L, 1),
                new Item("D", 250L, 1)));

        Comparator<Item> order = Comparator
                .comparingLong(Item::cents)
                .thenComparingInt(Item::priority)
                .thenComparing(Item::id);
        items.sort(order);
        System.out.println("ordered ids: "
                + items.stream().map(Item::id).toList());

        long min = Long.MIN_VALUE;
        System.out.println("bad long (min - 0): " + (min - 0L));
        System.out.println("bad long (0 - min): " + (0L - min));
        System.out.println("safe long order: " + Long.compare(min, 0L));
        System.out.println("reverse cents first: "
                + items.stream().sorted(order.reversed())
                        .map(Item::id).findFirst().orElseThrow());

        int bits = -1;
        System.out.println("signed -1 vs 1: " + Integer.compare(bits, 1));
        System.out.println("unsigned -1 vs 1: " + Integer.compareUnsigned(bits, 1));
    }
}
```

Chạy:

```bash
javac ComparatorFieldsDemo.java
java ComparatorFieldsDemo
```

Stdout đã kiểm chứng:

```text
ordered ids: [B, A, D, C]
bad long (min - 0): -9223372036854775808
bad long (0 - min): -9223372036854775808
safe long order: -1
reverse cents first: C
signed -1 vs 1: -1
unsigned -1 vs 1: 1
```

Các khóa được áp dụng lần lượt: `cents` tăng dần, rồi `priority` tăng dần, rồi `id` theo thứ tự String. `Long.MIN_VALUE` và 0 tạo lỗi overflow tương tự trường hợp `int`. Hai dòng signed/unsigned cho thấy việc chọn thứ tự phải dựa vào ý nghĩa miền dữ liệu.

## 7. Đảo chiều sắp xếp đúng cách

Đổi `a-b` thành `b-a` chỉ đảo một phép trừ có thể tràn thành phép trừ có thể tràn khác. Dùng:

```java
Comparator<Integer> descending =
        (a, b) -> Integer.compare(b, a);
```

Hoặc:

```java
Comparator<Integer> descending = Comparator.reverseOrder();
```

Với Comparator đã có, sử dụng `reversed()`:

```java
Comparator<Item> allKeysReversed = order.reversed();
```

Đoạn này giả sử `Item` và `order` đã được khai báo trong chương trình phần trước. `order.reversed()` đảo **toàn bộ thứ tự**, bao gồm các khóa phụ. Nếu muốn khóa chính giảm nhưng khóa phụ vẫn tăng, xây dựng lại cụ thể:

```java
Comparator<Item> centsDescendingPriorityAscending =
        Comparator.<Item>comparingLong(Item::cents)
                .reversed()
                .thenComparingInt(Item::priority);
```

**Đừng mặc định đảo Comparator tổng quát bằng `-compare(a,b)`.** Một Comparator hợp lệ được phép trả bất kỳ số âm, bao gồm `Integer.MIN_VALUE`. Phủ định `Integer.MIN_VALUE` ở kiểu `int` vẫn trả giá trị âm đó vì overflow; `Comparator.reversed()` an toàn hơn và thể hiện đúng ý định.

## 8. Những mẹo cast thường không khắc phục được vấn đề

### 8.1. Tính hiệu bằng long rồi ép về int

```java
Comparator<Integer> bad = (a, b) -> (int) ((long) a - b);
```

Phép trừ của hai số `int` được mở rộng sang `long` nên hiệu không tràn `long`; nhưng cast cuối về `int` có thể làm sai dấu. Ví dụ hiệu `0 - Integer.MIN_VALUE` là `2147483648L`, cast về `int` thành số âm.

Với hai giá trị `long`, ngay cả `long diff = a-b` cũng có thể overflow, nên không có một kiểu nguyên primitive có dấu rộng hơn `long` để luôn chứa mọi hiệu. Dùng `Long.compare`.

### 8.2. Dùng Math.subtractExact trong Comparator

`Math.subtractExact(a,b)` ném `ArithmeticException` khi hiệu tràn. Nó hữu ích nếu thật sự cần hiệu nguyên chính xác. Nhưng **so sánh không cần hiệu**: việc ném lỗi khi hai khóa hợp lệ có thể so thứ tự bằng `Integer.compare` làm tăng rủi ro mà không mang lại lợi ích.

### 8.3. Lấy Math.signum của hiệu đã tràn

`Math.signum` áp dụng cho `float`/`double`; `Integer.signum` và `Long.signum` áp dụng cho nguyên. Nếu hiệu nguyên **đã tràn**, lấy dấu của hiệu vẫn sai. Có thể lấy `Integer.signum(Integer.compare(a,b))`, nhưng lúc đó hàm `signum` là thừa vì `Integer.compare` đã trả dấu đúng.

### 8.4. So sánh bằng phép trừ số thực

`Double.compare(a-b,0.0)` cũng không phải thay thế tổng quát cho `Double.compare(a,b)`: hiệu có thể tràn thành vô cực, bị mất precision, trở thành signed zero hoặc NaN. So sánh trực tiếp cho biết thứ tự theo quy tắc API; tính hiệu là một bài toán khác.

## 9. Hệ quả thực tế trong collection và thuật toán

Comparator không chỉ quyết định thứ tự của một danh sách:

| API | Rủi ro khi Comparator sai |
| --- | --- |
| `List.sort` / `Collections.sort` | Có thể cho danh sách sai thứ tự hoặc ném lỗi do vi phạm hợp đồng |
| `Arrays.sort` với Comparator | Sort có thể sai hoặc không ổn định trước các quan hệ mâu thuẫn |
| `Stream.sorted` | Dữ liệu đi tiếp theo thứ tự không phù hợp |
| `TreeSet` | `first`, `last`, `add`, `contains` và tính duy nhất bị ảnh hưởng |
| `TreeMap` | Tra cứu key, các phép điều hướng/range và cập nhật có thể sai |
| `PriorityQueue` | Phần tử được lấy ra không phải độ ưu tiên đúng theo nghiệp vụ |

Đối với `TreeSet` và `TreeMap`, `compare(a,b)==0` quyết định hai phần tử hoặc hai key tương đương về thứ tự. Một comparator kiểu `(int)(aLong-bLong)` có thể biến hai số khác nhau thành kết quả 0 nếu hiệu bị cắt bit, dẫn tới dữ liệu bị xem trùng trong `TreeSet` hoặc `put` ghi đè entry trong `TreeMap`. Điều này nguy hiểm hơn một kết quả sort sai hiển nhiên.

Ví dụ với Comparator sai (đặt trong một phương thức):

```java
TreeSet<Integer> values = new TreeSet<>((a, b) -> a - b);
values.add(Integer.MIN_VALUE);
values.add(0);

System.out.println(values.first()); // 0, thứ tự sai
```

Trong bài này, ví dụ đầy đủ ở mục 4 kiểm chứng hành vi đó. Sau khi sửa comparator, nếu collection đã được xây dựng với quan hệ thứ tự sai, có thể cần **xây dựng lại collection/index**; đừng giả định mọi cấu trúc đã bị sắp sai sẽ tự phục hồi.

## 10. Kiểm tra hợp đồng bằng các giá trị biên

Bộ test chỉ có 1, 2, 3 thường không phát hiện tràn số. Với `int`, nên kiểm tra các giá trị:

```text
Integer.MIN_VALUE
Integer.MIN_VALUE + 1
-1
0
1
Integer.MAX_VALUE - 1
Integer.MAX_VALUE
```

Với mỗi cặp a,b, kiểm tra tính phản đối xứng theo dấu:

```java
int ab = Integer.signum(comparator.compare(a, b));
int ba = Integer.signum(comparator.compare(b, a));
if (ab != -ba) {
    throw new AssertionError("Comparator violates antisymmetry");
}
```

Đây là đoạn minh họa trong phương thức với biến `comparator`, a và b đã định nghĩa. **Lấy dấu trước khi phủ định** sẽ tránh overflow của `-Integer.MIN_VALUE` nếu một Comparator trả về giá trị đó.

Có thể kiểm tra tính bắc cầu cho các bộ ba giá trị, kiểm tra quy tắc tie-breaker với các khóa bằng nhau, và so sánh với `Integer.compare(a,b)` nếu ý định là thứ tự số nguyên tăng dần.

Chương trình độc lập `ComparatorContractCheck.java`:

```java
import java.util.Comparator;

public class ComparatorContractCheck {
    static boolean antisymmetric(Comparator<Integer> comparator,
                                 int[] values) {
        for (int a : values) {
            for (int b : values) {
                int ab = Integer.signum(comparator.compare(a, b));
                int ba = Integer.signum(comparator.compare(b, a));
                if (ab != -ba) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        int[] extremes = {
                Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE
        };
        Comparator<Integer> bad = (a, b) -> a - b;
        Comparator<Integer> good = Integer::compare;

        System.out.println("Bad comparator valid: "
                + antisymmetric(bad, extremes));
        System.out.println("Good comparator valid: "
                + antisymmetric(good, extremes));
    }
}
```

Chạy:

```bash
javac ComparatorContractCheck.java
java ComparatorContractCheck
```

Stdout đã xác minh:

```text
Bad comparator valid: false
Good comparator valid: true
```

Test chỉ chứng minh tính chất trên tập đầu vào cụ thể, **không phải chứng minh mọi hợp đồng của mọi comparator**. Nên kết hợp test giá trị biên, test bộ ba cho tính bắc cầu và lập luận rõ ràng rằng API `Integer.compare`/`Long.compare` có thứ tự chính xác.

## 11. Liên hệ với equals, BigDecimal và null

Không phải mọi comparator cho kết quả 0 khi `!a.equals(b)` đều bị overflow. Ví dụ:

```java
new java.math.BigDecimal("2.0")
        .compareTo(new java.math.BigDecimal("2.00")) == 0; // true
```

Hai `BigDecimal` cùng giá trị toán học nhưng khác scale. `BigDecimal.equals` vẫn trả false. Comparator dựa trên `compareTo` là thứ tự có định nghĩa, nhưng sorted collection sử dụng `compareTo` để quyết định key tương đương, nên ứng dụng phải thống nhất danh tính của key.

Tương tự, khi so một object nhiều trường mà chỉ so age:

```java
Comparator<Person> byAge = Comparator.comparingInt(Person::age);
```

Hai người khác nhau nhưng cùng age được xem ngang thứ tự theo comparator. Nếu cần phân biệt, thêm khóa phụ như ID:

```java
Comparator<Person> byAgeThenId =
        Comparator.comparingInt(Person::age)
                  .thenComparing(Person::id);
```

`Person` là một kiểu minh họa có `int age()` và `String id()`. Nếu hai object vẫn có age và id bằng nhau, Comparator trả 0; điều đó phải là quy tắc được chấp nhận của domain.

Với `null`, quyết định chính sách trước khi sort. Ví dụ:

```java
Comparator<Integer> nullable =
        Comparator.nullsLast(Integer::compare);
```

Comparator này cho số null nằm cuối, số nguyên còn lại tăng dần. Nếu null là dữ liệu lỗi, nên reject ở ranh giới thay vì tự gán thứ tự.

## 12. Quy trình sửa Comparator trong code thực tế

1. **Tìm những lambda trả hiệu:** `(a,b)->a-b`, `return x.id-y.id`, `(int)(a.longValue-b.longValue)`, và mọi phép phủ định kết quả của Comparator khác.
2. **Xem kiểu thật của toán hạng:** `int`, `long`, `double`, `BigDecimal`, unsigned hoặc object có null. Đừng bị đánh lừa bởi kiểu biến nhận kết quả.
3. **Đọc yêu cầu nghiệp vụ về thứ tự:** signed hay unsigned? Tăng hay giảm? Khóa chính và phụ nào? NaN/null có hợp lệ?
4. **Thay bằng API so sánh trực tiếp:** `Integer.compare`, `Long.compare`, `Double.compare`, `Comparable.compareTo`, `Comparator.comparing*`.
5. **Dùng `thenComparing` khi cần khóa phụ** và `reversed()` khi muốn đảo thứ tự. Tránh trừ hoặc cộng các hiệu trường.
6. **Test MIN/MAX, số âm, zero, giá trị trùng**, đồng thời kiểm tra cả hai chiều so sánh và các bộ ba bắc cầu.
7. **Kiểm tra các collection thực sự dùng comparator:** sort, `TreeSet`, `TreeMap`, `PriorityQueue`, đặc biệt việc chứa và tra cứu key.
8. **Xem xét dữ liệu đã lưu/index đang hoạt động:** nếu cấu trúc được dựng bằng comparator sai, có thể cần tái tạo theo comparator đúng.

Một Comparator nên làm một việc: **định nghĩa thứ tự**. Nếu logic nghiệp vụ cần **khoảng cách số học**, hãy viết phép tính hiệu ở hàm riêng, dùng kiểu đủ rộng hoặc checked arithmetic phù hợp. Không tái sử dụng giá trị chênh lệch làm kết quả so sánh.

## 13. Lỗi phổ biến và cách thay thế

| Cách viết hoặc quan niệm | Rủi ro | Cách sửa |
| --- | --- | --- |
| `(a,b)->a-b` với `int` | Tràn số, sai dấu | `Integer.compare(a,b)` |
| `(a,b)->b-a` để giảm dần | Vẫn tràn | `Integer.compare(b,a)` hoặc `reversed()` |
| `(a,b)->(int)(a-b)` với `long` | Tràn rồi cast mất bit | `Long.compare(a,b)` |
| `(int)((long)a-b)` với `int` | Cast trở lại `int` mất bit | `Integer.compare(a,b)` |
| `-comparator.compare(a,b)` | Có thể overflow nếu comparator trả `Integer.MIN_VALUE` | `comparator.reversed()` |
| `Math.subtractExact(a,b)` làm Comparator | Ném exception khi hiệu vượt miền dù thứ tự xác định | `Integer.compare`/`Long.compare` |
| `(int)(aDouble-bDouble)` | Chênh lệch nhỏ thành 0; NaN khó xử lý | `Double.compare` |
| Thử vài số dương nhỏ rồi kết luận an toàn | Không thử biên MIN/MAX | Test phản đối xứng, bắc cầu và giá trị cực trị |
| Cho rằng sort không ném lỗi thì comparator đúng | Sai thứ tự có thể xảy ra im lặng | Dựa trên hợp đồng và test biên |
| Dùng comparator sai trong `TreeSet`/`TreeMap` | Key ngang thứ tự hoặc vị trí cây có thể sai | Sửa comparator và cân nhắc dựng lại collection |

## 14. Câu hỏi tự kiểm tra

1. **Vì sao `return a-b` không an toàn trong Comparator<Integer>?** Hiệu `int` có thể overflow, làm dấu không còn phản ánh thứ tự.
2. **Cặp nào phá phản đối xứng?** `Integer.MIN_VALUE` và 0: phép trừ hai chiều đều cho dấu âm.
3. **Kiểu trả về của `compare` là int; có cần trả khoảng cách toán học giữa hai số không?** Không. Chỉ dấu âm, zero hoặc dương mới quan trọng.
4. **Đảo từ `a-b` sang `b-a` có sửa được lỗi không?** Không; cả hai vẫn có thể overflow.
5. **Sắp xếp theo trường `long cents` nên dùng gì?** `Comparator.comparingLong(Item::cents)` hoặc `Long.compare`.
6. **Sắp xếp nhiều trường số nên làm gì?** Dùng `thenComparingInt`, `thenComparingLong`, `thenComparing`.
7. **Có nên phủ định trực tiếp kết quả của Comparator tùy ý?** Không; `-Integer.MIN_VALUE` trong int vẫn âm. Dùng `reversed()`.
8. **Tại sao `TreeSet` và `TreeMap` dễ bị ảnh hưởng nghiêm trọng?** Comparator quyết định thứ tự và tính tương đương của key, có thể ảnh hưởng việc thêm, tìm, xóa, duyệt.
9. **`Math.subtractExact` có cần thiết để xây Comparator không?** Không, vì so sánh không cần tính hiệu.
10. **`Double.compare` có giống phép trừ double rồi xét dấu không?** Không; nó có quy tắc cho NaN/signed zero và tránh sự cố cast/precision.
11. **Nếu hai object khác nhau có cùng khóa sắp xếp thì sao?** Comparator có thể trả 0; cần xác định đó là cùng key hay phải bổ sung tie-breaker.
12. **Một sort chạy thành công có chứng minh comparator đúng không?** Không; test biên và lập luận hợp đồng vẫn cần thiết.

## 15. Kết luận

**Đừng dùng phép trừ để cài đặt Comparator số chỉ vì nó ngắn.** Phép trừ `int`/`long` có thể overflow ở giới hạn miền số, vi phạm phản đối xứng hoặc bắc cầu, khiến `sort` cho thứ tự sai và `TreeSet`/`TreeMap` vận hành ngoài mong đợi.

Dùng `Integer.compare`, `Long.compare`, `Double.compare` hoặc `Comparable.compareTo` theo đúng loại khóa. Với object có nhiều trường, dùng `Comparator.comparingInt`, `comparingLong` và `thenComparing*`; khi cần giảm dần dùng `reversed()`. Luôn kiểm thử các cặp MIN/MAX và hai chiều so sánh — vì lỗi nguy hiểm nhất là Comparator **có vẻ đúng trên dữ liệu nhỏ nhưng sai ở biên**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-011">Nhầm parseInt, decode và getInteger</a>

<details>
<summary>Xem chi tiết</summary>

# Nhầm Integer.parseInt, Integer.decode và Integer.getInteger

## 1. Ba phương thức, ba ý nghĩa hoàn toàn khác nhau

Trong Java, `java.lang.Integer` có những tên phương thức dễ khiến ta tưởng chúng đều nhận một chuỗi rồi chuyển thành số nguyên. Sự khác nhau nằm ở **chuỗi truyền vào là giá trị hay tên cấu hình** và **quy tắc cơ số**:

- **`Integer.parseInt(String)`** phân tích **nội dung chuỗi giá trị** theo **hệ thập phân**, trả primitive `int`. Overload `parseInt(String, int radix)` dùng cơ số tường minh từ 2 đến 36.
- **`Integer.decode(String)`** phân tích **nội dung chuỗi giá trị** và nhận các tiền tố để tự xác định cơ số: `0x`/`0X` hoặc `#` cho hex, số `0` dẫn đầu chuỗi nhiều chữ số cho octal, không prefix cho decimal. Kết quả là `Integer`.
- **`Integer.getInteger(String)`** coi chuỗi đối số là **tên JVM system property**, đọc property rồi diễn giải **giá trị property** theo cách `decode`; nó không parse chính đối số truyền vào. Kết quả là `Integer` hoặc `null` nếu không có/không parse được và không chỉ định mặc định.

Ví dụ:

```java
System.out.println(Integer.parseInt("010")); // 10
System.out.println(Integer.decode("010"));   // 8
```

`Integer.getInteger("010")` lại tìm **system property có tên `010`**; kết quả tùy thuộc cấu hình JVM, có thể là `null`. Nó không có nghĩa chuyển chuỗi `"010"` thành `10` hoặc `8`.

### Bảng so sánh

| Tiêu chí | `parseInt` | `decode` | `getInteger` |
| --- | --- | --- | --- |
| Tham số chuỗi | Giá trị cần parse | Giá trị cần decode | **Tên property** |
| Nguồn thực của số | Đối số `s` | Đối số `nm` | `System.getProperty(name)` |
| Mặc định radix | 10 | Suy ra từ prefix | Suy ra từ **giá trị property** |
| Đầu vào `"010"` | 10 | 8 | Tìm property tên `010` |
| Đầu vào `"0x10"` | Lỗi | 16 | Tìm property tên `0x10` |
| Kiểu trả về | `int` | `Integer` | `Integer` |
| Chuỗi số sai | Ném `NumberFormatException` | Ném `NumberFormatException` | `null` hoặc giá trị mặc định nếu property không parse được |
| Dùng cho | Số nguyên với radix rõ | Số nguyên cho phép prefix | Cấu hình `-D` của JVM |

Đây là bài học về **lựa chọn API theo hợp đồng dữ liệu**, không chỉ cách nhớ tên hàm. Sai lầm thường không xuất hiện khi test `"123"`, vì `parseInt` và `decode` đều trả 123; các ca có số 0 đầu và property JVM mới phơi bày khác biệt.

## 2. parseInt: số nguyên decimal mặc định

`Integer.parseInt(String s)` sử dụng radix 10:

```java
System.out.println(Integer.parseInt("42"));   // 42
System.out.println(Integer.parseInt("+42"));  // 42
System.out.println(Integer.parseInt("-42"));  // -42
System.out.println(Integer.parseInt("010"));  // 10
System.out.println(Integer.parseInt("08"));   // 8
```

Dấu `+` hoặc `-` có thể đứng **ở đầu** số. Dấu `0` dẫn đầu **không** biến chuỗi thành octal. Ngược lại, các chuỗi dưới đây **không hợp lệ** đối với overload decimal:

```java
// Mỗi dòng dưới đây ném NumberFormatException khi chạy:
Integer.parseInt("0x10");
Integer.parseInt("12.5");
Integer.parseInt("1_000");
Integer.parseInt(" 42 ");
Integer.parseInt("");
Integer.parseInt("2147483648");
```

`parseInt` không tự loại khoảng trắng, chấp nhận dấu phân nhóm hàng nghìn hoặc chấp nhận dấu gạch dưới `_` trong chuỗi. Java có literal số ở mã nguồn `1_000`, nhưng cú pháp literal Java **không giống** cú pháp chuỗi runtime của `parseInt`.

### Radix tường minh

`Integer.parseInt(String s, int radix)` cho phép radix từ `Character.MIN_RADIX` (2) đến `Character.MAX_RADIX` (36):

```java
System.out.println(Integer.parseInt("1010", 2)); // 10
System.out.println(Integer.parseInt("17", 8));   // 15
System.out.println(Integer.parseInt("10", 16));  // 16
System.out.println(Integer.parseInt("ff", 16));  // 255
System.out.println(Integer.parseInt("-ff", 16)); // -255
System.out.println(Integer.parseInt("Z", 36));   // 35
```

Ngay cả khi truyền radix 16, **prefix `0x` không được chấp nhận**:

```java
// Đều ném NumberFormatException:
Integer.parseInt("0x10", 16);
Integer.parseInt("0b1010", 2);
```

Muốn parse chuỗi `0x10` theo radix 16, có thể tách prefix sau khi validate rồi parse `"10"`, hoặc dùng `decode` nếu hợp đồng cú pháp cho phép nó. Nếu tự tách prefix, cần xử lý `+`/`-` trước prefix, chuỗi rỗng, và giới hạn `int`.

**Kiểu trả về:** `parseInt` trả primitive `int`, còn `Integer.valueOf("42")` trả wrapper `Integer` sau khi parse decimal. Cả hai không tìm system property.

## 3. decode: các prefix và bẫy octal

`Integer.decode(String nm)` cho phép các biểu diễn sau:

| Input | Radix | Kết quả |
| --- | ---: | ---: |
| `"42"` | 10 | 42 |
| `"0x2A"` | 16 | 42 |
| `"0X2a"` | 16 | 42 |
| `"#2A"` | 16 | 42 |
| `"052"` | 8 | 42 |
| `"-0x2A"` | 16 | -42 |
| `"+052"` | 8 | 42 |

```java
System.out.println(Integer.decode("42"));    // 42
System.out.println(Integer.decode("0x2A"));  // 42
System.out.println(Integer.decode("0X2a"));  // 42
System.out.println(Integer.decode("#2A"));   // 42
System.out.println(Integer.decode("052"));   // 42
System.out.println(Integer.decode("-0x2A")); // -42
System.out.println(Integer.decode("+052"));  // 42
```

Đặt dấu trước prefix: `"-0x2A"` hợp lệ, `"0x-2A"` không hợp lệ. Hex cho phép chữ số `a-f` hoặc `A-F`.

**Nguy cơ cao nhất: zero đầu có ý nghĩa octal.** Với các giá trị được thêm số 0 để hiển thị:

```java
System.out.println(Integer.parseInt("010")); // 10
System.out.println(Integer.decode("010"));   // 8
System.out.println(Integer.parseInt("08"));  // 8
// Integer.decode("08") -> NumberFormatException
```

Vì `8` và `9` không phải chữ số octal, `decode("08")` hoặc `decode("09")` thất bại. Nếu người dùng nhập số lượng dạng decimal zero-padded, dùng `parseInt` sẽ phù hợp hơn.

### decode không nhận mọi literal của Java

Java source hỗ trợ `0b1010` là binary integer literal, nhưng `Integer.decode("0b1010")` **không** parse được. Hãy dùng `Integer.parseInt("1010", 2)` khi contract xác định radix 2.

```java
System.out.println(0b1010);                    // 10: literal trong code
System.out.println(Integer.parseInt("1010", 2)); // 10
// Integer.decode("0b1010") ném NumberFormatException
```

`decode` cũng không parse phần thập phân như `"10.5"`. Nó trả signed `Integer`, nên input biểu diễn giá trị lớn hơn `Integer.MAX_VALUE` cũng không trở thành số dương hợp lệ chỉ vì viết ở dạng hex.

## 4. getInteger: đọc JVM system property, không đọc giá trị trực tiếp

`Integer.getInteger(String name)` tương đương về ý tưởng với việc tra chuỗi từ system property, sau đó decode **giá trị**:

```java
String raw = System.getProperty("app.maxWorkers");
Integer parsed = raw == null ? null : Integer.decode(raw);
```

Đây chỉ là minh họa **đường đi dữ liệu**; API thật có xử lý các trường hợp property sai cú pháp và giá trị mặc định. Cần nhớ:

```java
System.setProperty("app.maxWorkers", "0x10");

System.out.println(System.getProperty("app.maxWorkers")); // 0x10
System.out.println(Integer.getInteger("app.maxWorkers")); // 16
System.out.println(Integer.getInteger("app.maxWorkers", 4)); // 16
```

`System.getProperty` trả chuỗi raw, còn `getInteger` trả `Integer` sau khi decode. Không tự tin rằng `getInteger("100")` sẽ nhận số 100; nó đang tra property tên `100`.

### Ba overload

```java
Integer.getInteger(String name)
Integer.getInteger(String name, int defaultValue)
Integer.getInteger(String name, Integer defaultValue)
```

Đây là những **chữ ký minh họa**, không phải ba câu lệnh Java được đặt trong thân method. Overload không có default trả `null` nếu property không tồn tại **hoặc giá trị không decode được**. Overload có default trả giá trị mặc định cho cả hai trường hợp đó.

```java
System.setProperty("app.retryLimit", "08");

System.out.println(Integer.getInteger("app.retryLimit"));    // null
System.out.println(Integer.getInteger("app.retryLimit", 3)); // 3
```

Tại sao? `getInteger` dùng logic `decode` cho giá trị `"08"`, nên octal thất bại. Điều đáng lưu ý là ở `getInteger`, **lỗi cú pháp cấu hình có thể bị che bằng default**, trong khi `decode("08")` gọi trực tiếp ném `NumberFormatException`.

### JVM -D và environment variable khác nhau

System property có thể được truyền khi khởi động chương trình:

```bash
java -Dapp.maxWorkers=0x10 App
```

Khi đó `Integer.getInteger("app.maxWorkers")` có thể trả 16. Trái lại, biến môi trường `APP_MAX_WORKERS` phải đọc bằng:

```java
String raw = System.getenv("APP_MAX_WORKERS");
int maxWorkers = raw == null ? 4 : Integer.parseInt(raw);
```

Đoạn trên chỉ là minh họa; production cần báo lỗi về cú pháp và giới hạn giá trị. **`Integer.getInteger("APP_MAX_WORKERS")` không đọc environment variable** — trừ việc JVM có system property *riêng* trùng tên, hai không gian cấu hình là khác nhau.

System properties thuộc phạm vi JVM và có thể được thay đổi bởi chương trình. Trong test, nên lưu giá trị cũ, set property tạm rồi khôi phục bằng `finally`. Quyền truy cập property cũng có thể bị hạn chế, và `SecurityException` là một vấn đề khác với “không có property”.

## 5. Bảng các ca phân biệt ba API

| Biểu thức | Kết quả / ý nghĩa |
| --- | --- |
| `parseInt("10")` | 10 |
| `parseInt("010")` | 10 |
| `decode("010")` | 8 |
| `parseInt("10", 8)` | 8 |
| `parseInt("10", 16)` | 16 |
| `decode("0x10")` | 16 |
| `decode("#10")` | 16 |
| `decode("-0x10")` | -16 |
| `parseInt("08")` | 8 |
| `decode("08")` | `NumberFormatException` |
| `parseInt("0x10")` | `NumberFormatException` |
| `parseInt("0x10", 16)` | `NumberFormatException` |
| `parseInt("1010", 2)` | 10 |
| `decode("0b1010")` | `NumberFormatException` |
| `getInteger("app.limit")` | Giá trị đọc từ property `app.limit`; có thể `null` |
| `getInteger("010")` | Tra **property tên 010**, không parse số 010 |

Chọn parser bằng **đặc tả input**, không phải bằng cảm giác phương thức nào tiện nhất. Với decimal người dùng nhập, việc âm thầm hỗ trợ octal thường là thay đổi nguy hiểm.

## 6. Chương trình Java 17+ hoàn chỉnh và stdout xác định

Chương trình tự đặt các system property có tên riêng, xoá một property giả định “missing” trong phạm vi ví dụ và khôi phục trạng thái ban đầu bằng `finally`. Nó kiểm tra các trường hợp sai bằng cách bắt `NumberFormatException` rồi in tên ngoại lệ, không dựa vào thông điệp lỗi có thể thay đổi giữa các JDK.

Lưu thành **`IntegerMethodComparison.java`**:

```java
public class IntegerMethodComparison {

    static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    static void expectBad(String label, String text, boolean decode) {
        try {
            if (decode) {
                Integer.decode(text);
            } else {
                Integer.parseInt(text);
            }
            System.out.println(label + ": unexpectedly accepted");
        } catch (NumberFormatException ex) {
            System.out.println(label + ": NumberFormatException");
        }
    }

    public static void main(String[] args) {
        System.out.println("parseInt 010 = " + Integer.parseInt("010"));
        System.out.println("decode 010 = " + Integer.decode("010"));
        System.out.println("parseInt base16 = "
                + Integer.parseInt("10", 16));
        System.out.println("decode hex = " + Integer.decode("0x10"));
        System.out.println("decode #2A = " + Integer.decode("#2A"));
        System.out.println("decode -010 = " + Integer.decode("-010"));

        expectBad("parseInt 0x10", "0x10", false);
        expectBad("decode 08", "08", true);
        expectBad("decode 0b1010", "0b1010", true);
        expectBad("parseInt overflow", "2147483648", false);

        String good = "lesson381.integer.good";
        String invalid = "lesson381.integer.invalid";
        String absent = "lesson381.integer.absent";
        String prevGood = System.getProperty(good);
        String prevInvalid = System.getProperty(invalid);
        String prevAbsent = System.getProperty(absent);

        try {
            System.setProperty(good, "0x10");
            System.setProperty(invalid, "08");
            System.clearProperty(absent);

            System.out.println("raw property = "
                    + System.getProperty(good));
            System.out.println("getInteger good = "
                    + Integer.getInteger(good));
            System.out.println("getInteger invalid default = "
                    + Integer.getInteger(invalid, 7));
            System.out.println("getInteger absent = "
                    + Integer.getInteger(absent));
            System.out.println("getInteger absent default = "
                    + Integer.getInteger(absent, 3));
        } finally {
            restore(good, prevGood);
            restore(invalid, prevInvalid);
            restore(absent, prevAbsent);
        }
    }
}
```

Chạy bằng JDK 17 hoặc mới hơn, từ thư mục chứa file:

```bash
javac IntegerMethodComparison.java
java IntegerMethodComparison
```

Đầu ra dự kiến:

```text
parseInt 010 = 10
decode 010 = 8
parseInt base16 = 16
decode hex = 16
decode #2A = 42
decode -010 = -8
parseInt 0x10: NumberFormatException
decode 08: NumberFormatException
decode 0b1010: NumberFormatException
parseInt overflow: NumberFormatException
raw property = 0x10
getInteger good = 16
getInteger invalid default = 7
getInteger absent = null
getInteger absent default = 3
```

Ví dụ kiểm tra đồng thời **khác biệt radix** và **khác biệt nguồn dữ liệu**. Nó không dựa vào system property bất kỳ đã có sẵn; các giá trị được ghi đè tạm thời và phục hồi. Việc dùng tên property riêng giúp hạn chế tác động tới ứng dụng khác trong cùng JVM, tuy nhiên phần test thực tế vẫn cần cẩn thận với luồng chạy song song sử dụng cùng global properties.

## 7. Kiểm soát cú pháp input: parse decimal thật sự

Đối với dữ liệu số đếm nhận từ form hoặc API, người dùng thường mong decimal, có thể có số 0 đầu. Một hàm strict có thể từ chối mọi ký tự ngoài digit ASCII, kiểm tra giới hạn rồi parse:

```java
static int parseItemCount(String raw) {
    if (raw == null || !raw.matches("[0-9]+")) {
        throw new IllegalArgumentException(
                "Count must use ASCII decimal digits only");
    }
    final int count;
    try {
        count = Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Count exceeds int range", ex);
    }
    if (count > 5000) {
        throw new IllegalArgumentException("Count too large");
    }
    return count;
}
```

Đây là **phương thức minh họa phải nằm trong một lớp**, không phải chương trình hoàn chỉnh. Nó chấp nhận `"0008"` làm decimal 8 và từ chối `"-2"`, `"+2"`, khoảng trắng, `"0x10"`, dấu phẩy và các chữ số Unicode không thuộc ASCII. Nếu hợp đồng cho phép số âm, dấu cộng hoặc whitespace bên ngoài, hãy thay validation rõ ràng chứ không tự đổi `parseInt` thành `decode`.

`parseInt` tự dùng `Character.digit` cho từng ký tự theo radix, nên có thể nhận một số chữ số Unicode. Nếu hệ thống yêu cầu **đúng dạng ASCII**, cần bước xác thực riêng như ví dụ trên.

### Làm gì khi người dùng muốn nhập hex?

Nếu hợp đồng cho phép decimal `42` hoặc hex `0x2A` nhưng **không** octal từ zero đầu, `decode` không hoàn toàn phù hợp. Có thể phát hiện prefix hex và áp dụng `parseInt` với radix 16, còn các chuỗi khác parse decimal. Chú ý dấu `-` và `+` phải được xử lý trước prefix, và không được để chuỗi chỉ có `0x` lọt qua.

Cần test `"08"`, `"010"`, `"0x2A"`, `"-0x2A"`, `"0x"` và giá trị vượt `int`. Cách triển khai parser nên được định nghĩa bởi giao thức cụ thể, không đơn thuần sao chép quy tắc của Java source literals.

## 8. getInteger có default có thể che lỗi cấu hình

Xét `-Dapp.poolSize=08`. Người vận hành mong tám luồng; nếu chương trình gọi:

```java
int poolSize = Integer.getInteger("app.poolSize", 4);
```

giá trị thực có thể trở thành **4**, vì `decode("08")` không hợp lệ trong octal và API trả default. Chương trình chạy nhưng không như cấu hình mong muốn. Điều này khác với `parseInt("08")` trả 8.

Một cách đọc **decimal strict** hơn:

```java
static int positiveDecimalProperty(String name, int defaultValue) {
    String raw = System.getProperty(name);
    if (raw == null) {
        return defaultValue; // Chỉ thiếu mới sử dụng mặc định.
    }

    final int value;
    try {
        value = Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
                "Invalid decimal property: " + name, ex);
    }
    if (value <= 0) {
        throw new IllegalArgumentException(
                "Property must be positive: " + name);
    }
    return value;
}
```

Đây là phương thức minh họa, cần đặt trong một lớp; ví dụ giả định `defaultValue` hợp lệ theo miền dương, nên caller cũng cần kiểm tra giá trị mặc định. Nó phân biệt property **vắng mặt** với property **có mặt nhưng sai cú pháp**, đồng thời đảm bảo số lớn hơn 0.

Nếu property là bắt buộc, không nên trả default khi thiếu; có thể ném `IllegalStateException` hoặc ngoại lệ cấu hình có thông báo cụ thể. Nếu cho phép `0x` nhưng không cho phép octal ngầm, parser phải có chính sách rõ, không dùng `getInteger` như một trình kiểm tra magic.

## 9. Overflow, kiểu wrapper, null và signedness

`Integer.parseInt` và `Integer.decode` xử lý số nguyên có dấu 32 bit: miền `-2147483648` đến `2147483647`. Những chuỗi có giá trị ngoài miền tạo `NumberFormatException`:

```java
// Các lời gọi này ném NumberFormatException:
Integer.parseInt("2147483648");
Integer.decode("0x80000000");
```

Nếu mục tiêu là số nguyên lớn hơn `int`, cân nhắc `Long.parseLong`/`Long.decode` hoặc `java.math.BigInteger`. Với bit pattern unsigned 32-bit, `Integer.parseUnsignedInt` có hợp đồng khác: nó có thể trả một `int` mang bit pattern được diễn giải là âm theo signed; cần xử lý `Integer.toUnsignedString` hay kiểu rộng hơn khi trình bày. Đừng tự ép kiểu hay dùng `decode` rồi xem đó là unsigned parser.

Vì `getInteger` trả nullable `Integer`, auto-unboxing có thể ném `NullPointerException`:

```java
Integer maybePort = Integer.getInteger("app.port");
if (maybePort != null) {
    int port = maybePort;
    System.out.println(port);
}
```

Không viết `int port = Integer.getInteger("app.port");` nếu không có bảo đảm property tồn tại và hợp lệ. Dùng overload có default khi fallback được quy định hoặc kiểm tra null rõ.

`Integer.valueOf("123")` trả wrapper `Integer`; `Integer.parseInt("123")` trả primitive `int`. So sánh hai wrapper bằng `==` có thể phụ thuộc cache object; dùng `equals` cho value (sau khi kiểm tra null), hoặc so sánh primitive.

## 10. Bài kiểm tra hồi quy cho lỗi nhầm lẫn

Khi review việc dùng `parseInt`, `decode` và `getInteger`, tối thiểu hãy kiểm tra các nhóm đầu vào:

| Nhóm | Dữ liệu | Điều cần xác nhận |
| --- | --- | --- |
| Decimal thông thường | `"0"`, `"123"`, `"-9"` | Dấu và radix 10. |
| Zero-padded | `"010"`, `"08"`, `"009"` | `decode` thay đổi nghĩa hoặc từ chối. |
| Hex | `"0x2A"`, `"#2A"`, `"-0x2A"` | `decode` chấp nhận; `parseInt` decimal từ chối. |
| Binary | `"1010"` radix 2, `"0b1010"` | Prefix binary không được `decode` hỗ trợ. |
| Không sạch | `""`, `" 42 "`, `"1_000"`, `"12.0"` | Kiểm tra NFE/validation. |
| Biên kiểu int | `"2147483647"`, `"2147483648"`, `"-2147483648"` | Tràn hoặc chính xác. |
| Property thiếu | Khóa test chưa được đặt | `getInteger` trả null/default. |
| Property sai | `"08"`, `"abc"` | Default có thể che lỗi; strict parser phải phát hiện. |
| Property đúng | `"42"`, `"0x2A"` | Giá trị được parse theo `decode`. |
| Phân biệt nguồn | `-Dapp.x` và environment variable riêng | `getInteger` chỉ đọc system property. |

Trong unit test, đừng dùng property tên chung đã tồn tại như `java.version` để chứng minh một số nguyên cụ thể. Tạo khóa độc lập, lưu giá trị ban đầu, set/clear và restore trong `finally`. Nếu chạy test song song, system properties là **global state** và có thể gây race; ưu tiên đưa cấu hình vào hàm qua tham số hoặc lớp cấu hình thay vì dựa hoàn toàn vào trạng thái JVM.

### Checklist chẩn đoán khi giá trị cấu hình sai

1. Xem **đối số** của phương thức: là giá trị `"08"` hay key `"app.limit"`?
2. Kiểm tra **giá trị raw** từ `System.getProperty` nếu dùng `getInteger`.
3. Ghi rõ radix kỳ vọng: decimal, octal ngầm, hex hay binary.
4. Kiểm tra dấu, prefix, whitespace và ký tự không hợp lệ.
5. Kiểm tra phạm vi `int` và có cần `long`/`BigInteger` không.
6. Xác định thiếu cấu hình có được default hay phải là lỗi.
7. Ghi regression test cho `"010"`, `"08"` và `"0x10"`; ba ca này phân biệt parser nhanh hơn `"123"`.

## 11. Những sai lầm thường gặp

1. **Gọi `getInteger("42")` để chuyển `"42"` thành số:** thực chất tra property tên `42`.
2. **Cho rằng `getInteger` lấy biến môi trường:** nó đọc JVM system property, không phải `System.getenv`.
3. **Dùng `decode` cho số thập phân zero-padded:** `"010"` thành 8 và `"08"` ném lỗi.
4. **Truyền `0x` vào `parseInt(s,16)`:** cần chuỗi chữ số radix 16 không có prefix.
5. **Dùng `decode` để parse `0b`:** nó không hỗ trợ binary prefix.
6. **Cho rằng input chuỗi hỗ trợ underscore như Java literal:** `"1_000"` không hợp lệ với các parser nói trên.
7. **Không phân biệt property missing và invalid:** `getInteger(name,default)` có thể dùng default cho cả hai.
8. **Bỏ qua null từ `getInteger`:** auto-unboxing sẽ ném NPE khi property không có hoặc sai.
9. **Không kiểm tra overflow `int`:** chuỗi quá lớn không thể parse thành signed 32-bit number.
10. **Lạm dụng `decode` vì tiện:** giao thức cần decimal phải dùng decimal parser, không tự thay đổi nghĩa input.
11. **Dùng wrapper `==` làm kiểm tra nội dung:** nguy cơ nhầm instance caching; kiểm tra value bằng `equals`/primitive.
12. **Che mọi lỗi parsing bằng catch rồi trả 0:** cấu hình sai có thể trở thành lỗi nghiệp vụ âm thầm; nên phân loại và truyền thông báo có tên trường.
13. **Dùng `Integer` cho decimal có phần lẻ:** nếu dữ liệu là tiền/đo lường cần decimal exact, dùng `BigDecimal` và chính sách rounding phù hợp.
14. **Cho rằng `parseInt` là kiểm tra ASCII:** nó sử dụng `Character.digit`; validate format ASCII trước nếu giao thức yêu cầu.
15. **Không thử property qua `-D` và `System.setProperty`:** test chỉ gọi trực tiếp `decode` không phát hiện lỗi nhầm key/property.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `parseInt("075")` và `decode("075")` bằng bao nhiêu?

**Đáp án:** 75 và 61. `decode` coi `075` là octal, tức `7 × 8 + 5 = 61`.

**Câu 2.** Vì sao `parseInt("08")` thành 8 nhưng `decode("08")` ném lỗi?

**Đáp án:** `parseInt` mặc định radix 10; `decode` với số 0 đầu dùng radix 8, mà octal không có chữ số 8.

**Câu 3.** Muốn parse chuỗi `"0x2A"` thì chọn phương thức nào?

**Đáp án:** `Integer.decode("0x2A")` trả 42; nếu `parseInt` với radix 16 phải truyền phần chữ số `"2A"`.

**Câu 4.** `getInteger("123")` trả 123 không?

**Đáp án:** Không mặc định. Nó tìm JVM system property có tên `123`; chỉ khi property có giá trị phù hợp thì mới decode giá trị ấy.

**Câu 5.** Vì sao `getInteger("app.workers", 4)` có thể không phù hợp cho cấu hình bắt buộc?

**Đáp án:** Property không tồn tại hoặc sai cú pháp đều có thể dẫn đến default 4, che lỗi người vận hành nhập sai.

**Câu 6.** Chuỗi `"0b1010"` có được `decode` chấp nhận không?

**Đáp án:** Không. Dùng `Integer.parseInt("1010", 2)` khi radix đã rõ.

**Câu 7.** Với property `app.port` có giá trị `"0x1F90"`, `getInteger("app.port")` là bao nhiêu?

**Đáp án:** 8080, vì `0x1F90` là hexadecimal.

**Câu 8.** Khi nào cần validate `[0-9]+` trước `parseInt`?

**Đáp án:** Khi giao thức đòi digit ASCII thuần và không chấp nhận Unicode digit, dấu cộng/trừ hoặc ký tự khác mà parser có thể xử lý.

**Câu 9.** Tại sao `int x = Integer.getInteger("missing.key")` nguy hiểm?

**Đáp án:** Nếu property không có, `getInteger` trả `null`; auto-unboxing sang `int` gây `NullPointerException`.

**Câu 10.** Ba test có giá trị phát hiện nhầm parser nhất là gì?

**Đáp án:** `"010"`, `"08"`, `"0x10"` — chúng cho kết quả/lỗi khác nhau rõ rệt giữa `parseInt` và `decode`; với `getInteger` còn phải kiểm tra property có tên cụ thể.

## 13. Tài liệu tham khảo

- [Java SE 17 — Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): `parseInt`, `valueOf`, `decode`, `getInteger`, signed/unsigned và overload.
- [Java SE 17 — System](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): `getProperty`, `setProperty`, `clearProperty` và `getenv`.
- [Java SE 17 — Character](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Character.html): `Character.digit` và các chữ số theo radix.
- [Java SE 17 — BigInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): số nguyên vượt miền `int` khi ứng dụng cần.

**Ghi nhớ:** `parseInt` đọc **giá trị với radix được xác định**, `decode` đọc **giá trị có prefix hex/octal**, còn `getInteger` đọc **JVM system property theo tên** rồi decode nội dung property. Muốn code dễ hiểu, hãy gọi đúng API theo **nguồn dữ liệu, cú pháp và chính sách lỗi**; kiểm thử zero đầu và property invalid trước khi coi hai lời gọi tương đương.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="common-numeric-mistakes-012">Định dạng rồi parse lại để thực hiện làm tròn tính toán</a>

<details>
<summary>Xem chi tiết</summary>

# Sai lầm: định dạng rồi parse lại để thực hiện làm tròn tính toán

## 1. Vấn đề cốt lõi

Một anti-pattern trong Java là **format số thành chuỗi với N chữ số thập phân, parse lại chuỗi đó thành số, rồi dùng kết quả để tính toán tiếp**. Ví dụ:

~~~java
// Chỉ là đoạn minh họa, không phải chương trình độc lập.
String text = String.format(java.util.Locale.US, "%.2f", amount);
double rounded = Double.parseDouble(text);
~~~

Cách làm này có vẻ giống việc làm tròn nhưng đã trộn ba nhiệm vụ: **trình bày**, **đọc chuỗi**, **biến đổi số học**. Chuỗi hiển thị có thể chứa dấu nhóm hàng nghìn, dấu thập phân theo locale, ký hiệu tiền tệ, số 0 thêm vào, hoặc chữ số đã bị loại. Parse lại còn phụ thuộc loại parser và trạng thái cấu hình, có thể trả số kiểu `double` với sai số nhị phân.

Nếu nghiệp vụ cần làm tròn giá trị BigDecimal, dùng **`setScale(scale, RoundingMode)`** hoặc **`round(MathContext)`**; nếu cần làm tròn thương, dùng overload `divide` có scale/MathContext và rounding mode. Chỉ format sau khi đã xác định giá trị cần trình bày. Nếu nghiệp vụ cần giữ giá trị nguyên gốc, format bản gốc để hiển thị và không dùng chuỗi đó tính toán ngược lại.

## 2. Ba thao tác có mục đích khác nhau

| Thao tác | Mục tiêu | Ví dụ | Đặc điểm |
| --- | --- | --- | --- |
| **Format** | Hiển thị số cho người dùng | `NumberFormat.format(value)` | Có locale, grouping, ký hiệu tiền, số 0 và có thể làm tròn chuỗi |
| **Parse** | Đọc văn bản vào dạng số | `DecimalFormat.parse(text)` | Phụ thuộc cú pháp/locale, có thể chấp nhận tiền tố hợp lệ |
| **Round** | Áp dụng quy tắc số học lên giá trị | `value.setScale(2, HALF_EVEN)` | Không cần chuyển sang String, chính sách rõ ràng |

Ví dụ số chính xác `1234.567` có thể được formatter US hiển thị là `1,234.57`. Phần nghìn đã bị bỏ: parse ngược lại **không thể tái tạo `1234.567`**. Format là thao tác ở ranh giới hiển thị; không tự xác lập chính sách làm tròn cho toàn bộ phép tính.

`BigDecimal` là immutable: `setScale`, `round` và các phép cộng/trừ/nhân trả kết quả BigDecimal mà không sửa đối tượng gốc. Locale cũng không phải thuộc tính của BigDecimal. Số được tính nên có ý nghĩa nhất quán trước khi hiển thị theo US, Đức hay Việt Nam.

## 3. Minh họa anti-pattern: mất precision dù parse thành công

Đoạn chương trình sau cố ý làm format→parse để chứng minh dữ liệu bị đổi. Pattern `#,##0.00` của US thêm dấu nhóm và giữ đúng hai chữ số lẻ. Parser được cấu hình trả BigDecimal để không phát sinh thêm sai số `double`, nhưng **chữ số đã mất tại bước format vẫn không quay lại**.

Lưu thành `FormatParseTrapDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class FormatParseTrapDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("1234.567");

        DecimalFormat format = new DecimalFormat(
                "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setParseBigDecimal(true);

        String shown = format.format(original);
        ParsePosition position = new ParsePosition(0);
        BigDecimal parsed = (BigDecimal) format.parse(shown, position);

        System.out.println("original = " + original.toPlainString());
        System.out.println("formatted = " + shown);
        System.out.println("parsed = " + parsed.toPlainString());
        System.out.println("fully parsed = "
                + (position.getIndex() == shown.length()));
        System.out.println("same value = "
                + (original.compareTo(parsed) == 0));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac FormatParseTrapDemo.java
java FormatParseTrapDemo
~~~

Kết quả:

~~~text
original = 1234.567
formatted = 1,234.57
parsed = 1234.57
fully parsed = true
same value = false
~~~

Parser tiêu thụ hết chuỗi và trả BigDecimal, nhưng giá trị đã bị làm tròn theo yêu cầu **hiển thị**. Nếu đem `parsed` cộng vào báo cáo, tính phí hoặc làm tròn lần nữa, chương trình đã thay đổi đầu vào cho các phép tính tiếp theo mà không có chính sách số học rõ ràng.

Ngoài ra, `new BigDecimal("1,234.57")` sẽ ném `NumberFormatException`, vì constructor BigDecimal không hiểu dấu nhóm theo locale. Vì vậy chuỗi định dạng để hiển thị không phải chuỗi decimal chuẩn để lưu hoặc parse bằng constructor.

## 4. Locale: cùng chuỗi có thể có nghĩa khác

Dấu thập phân và dấu nhóm phụ thuộc locale. `1,234` thường biểu thị một nghìn hai trăm ba mươi bốn theo US, còn theo Germany lại là một phẩy hai ba bốn. Đức thường hiển thị `1234.57` dưới dạng `1.234,57`.

Các locale có thể dùng khoảng trắng Unicode, ký tự số khác, hoặc cách hiển thị tiền tệ riêng. Đừng dùng `replace(",", ".")` một cách máy móc, vì có thể biến dấu nhóm thành dấu thập phân. Không format theo `Locale.getDefault()` rồi parse bằng một locale khác; cấu hình hệ điều hành và JVM có thể thay đổi giữa máy phát triển và triển khai.

### Ví dụ Java 17: hai giá trị từ cùng một chuỗi

Lưu thành `LocaleNumberDemo.java`:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleNumberDemo {
    static BigDecimal parseEntire(String text, Locale locale)
            throws ParseException {
        NumberFormat nf = NumberFormat.getNumberInstance(locale);
        if (!(nf instanceof DecimalFormat df)) {
            throw new IllegalStateException("DecimalFormat unavailable");
        }
        df.setParseBigDecimal(true);
        ParsePosition pos = new ParsePosition(0);
        Number parsed = df.parse(text, pos);

        if (!(parsed instanceof BigDecimal result)
                || pos.getIndex() != text.length()) {
            throw new ParseException("Invalid decimal",
                    Math.max(0, pos.getIndex()));
        }
        return result;
    }

    public static void main(String[] args) throws ParseException {
        String input = "1,234";
        System.out.println("US = " + parseEntire(input, Locale.US));
        System.out.println("DE = " + parseEntire(input, Locale.GERMANY));
    }
}
~~~

~~~bash
javac LocaleNumberDemo.java
java LocaleNumberDemo
~~~

Kết quả với dữ liệu locale chuẩn của JDK 17:

~~~text
US = 1234
DE = 1.234
~~~

`NumberFormat.parse` có thể trả `Long` hoặc `Double` theo cấu hình mặc định; không cast thẳng về BigDecimal nếu chưa bật `DecimalFormat.setParseBigDecimal(true)` và kiểm tra kiểu. `DecimalFormat` vẫn có thể parse tương đối dễ dãi, nhất là dấu nhóm. Kiểm tra `ParsePosition.getIndex() == text.length()` chỉ xác nhận đã tiêu thụ toàn chuỗi, **không xác thực cấu trúc grouping nghiêm ngặt**. Nếu giao diện yêu cầu cú pháp chặt chẽ, phải có validation bổ sung phù hợp locale. Java 17 chưa có API `DecimalFormat.setStrict`.

Đặc biệt, BigDecimal không biểu diễn `NaN` hoặc vô cực; luôn kiểm tra kết quả parse và xử lý input ngoại lệ theo hợp đồng dữ liệu.

## 5. Dùng setScale để làm tròn số học

Khi yêu cầu là **làm tròn về hai chữ số thập phân**, thao tác đúng:

~~~java
// Ví dụ minh họa; value cần được khai báo trước.
BigDecimal rounded = value.setScale(2, RoundingMode.HALF_EVEN);
~~~

`setScale(2, ...)` giữ đúng hai chữ số sau dấu thập phân. Khi giảm scale làm mất chữ số có giá trị, Java dùng chế độ làm tròn được truyền vào. Nếu tăng scale, nó bổ sung số 0 mà không làm đổi giá trị toán học.

Các chế độ:

| RoundingMode | Ý nghĩa |
| --- | --- |
| `HALF_UP` | Chọn gần nhất; nếu hòa thì xa 0 |
| `HALF_DOWN` | Chọn gần nhất; nếu hòa thì gần 0 |
| `HALF_EVEN` | Chọn gần nhất; nếu hòa thì chữ số cuối giữ lại chẵn |
| `UP` | Xa 0 mỗi khi phần bỏ khác 0 |
| `DOWN` | Về 0 mỗi khi phần bỏ khác 0 |
| `CEILING` | Hướng dương vô cực |
| `FLOOR` | Hướng âm vô cực |
| `UNNECESSARY` | Cấm bỏ phần có giá trị; cần làm tròn thì ném `ArithmeticException` |

Chọn chế độ theo yêu cầu nghiệp vụ; `UP` không đồng nghĩa `HALF_UP`. Đối với số âm, `CEILING` và `FLOOR` không giống quy tắc gần hoặc xa zero. Trong tiền tệ, thuế và hóa đơn, chính sách làm tròn nên được ghi rõ, không phụ thuộc mặc định của formatter.

### Ví dụ Java 17: round rồi format

Lưu thành `RoundThenFormatDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public class RoundThenFormatDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("1234.567");
        BigDecimal rounded = original.setScale(
                2, RoundingMode.HALF_UP);

        NumberFormat formatter =
                NumberFormat.getNumberInstance(Locale.US);
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        System.out.println("original = " + original.toPlainString());
        System.out.println("rounded = " + rounded.toPlainString());
        System.out.println("display = " + formatter.format(rounded));
        System.out.println("source remains = " + original.toPlainString());
    }
}
~~~

~~~bash
javac RoundThenFormatDemo.java
java RoundThenFormatDemo
~~~

Kết quả:

~~~text
original = 1234.567
rounded = 1234.57
display = 1,234.57
source remains = 1234.567
~~~

Nếu nghiệp vụ thật sự yêu cầu giá trị hai chữ số lẻ, tiếp tục tính bằng `rounded`. Nếu chỉ muốn giao diện **hiển thị** hai chữ số trong khi phép tính vẫn phải giữ `1234.567`, tiếp tục dùng `original` và chỉ format khi xuất giao diện. Việc chọn bên nào không được do formatter tự quyết định.

## 6. MathContext: precision không phải scale

Một nguồn hiểu lầm nữa là `MathContext`. `setScale(2, HALF_EVEN)` giữ hai chữ số **sau dấu thập phân**; `round(new MathContext(4, HALF_EVEN))` giữ tối đa bốn chữ số **có nghĩa**.

Ví dụ với `123.45`:

- `setScale(2, HALF_EVEN)` ⇒ `123.45`.
- `round(MathContext(4, HALF_EVEN))` ⇒ `123.4`.

### Ví dụ Java 17: hai khái niệm khác nhau

Lưu thành `ScalePrecisionDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class ScalePrecisionDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("123.45");
        System.out.println("source = " + x);
        System.out.println("scale 2 = " + x.setScale(
                2, RoundingMode.HALF_EVEN));
        System.out.println("precision 4 = " + x.round(
                new MathContext(4, RoundingMode.HALF_EVEN)));
    }
}
~~~

~~~bash
javac ScalePrecisionDemo.java
java ScalePrecisionDemo
~~~

Kết quả:

~~~text
source = 123.45
scale 2 = 123.45
precision 4 = 123.4
~~~

Nếu phép chia decimal là vô hạn, như `1/3`, `BigDecimal.divide(divisor)` chính xác có thể ném `ArithmeticException`. Dùng `divide(divisor, 2, RoundingMode.HALF_UP)` cho hai chữ số lẻ hoặc `divide(divisor, MathContext)` cho precision có nghĩa. **Không format rồi parse thương** để né yêu cầu phải chọn chế độ làm tròn.

## 7. Parse về double tái xuất sai số nhị phân

`double` là số nhị phân có số bit hữu hạn. Nhiều số thập phân như `0.1` không được biểu diễn hoàn toàn chính xác. Vì thế định dạng chuỗi hai số lẻ rồi `Double.parseDouble` **không tạo một số thập phân chính xác có hai số lẻ**. Nó tạo giá trị nhị phân gần nhất có thể tiếp tục sinh sai số khi cộng nhân.

Ngay cả phép chuyển BigDecimal từ double cũng có bẫy: `new BigDecimal(2.675)` biểu diễn chính xác giá trị double đã nhận, vốn hơi nhỏ hơn số thập phân lý tưởng 2.675. `new BigDecimal("2.675")` biểu diễn chính xác số decimal 2.675.

### Ví dụ Java 17: sai khác ở ranh giới làm tròn

Lưu thành `DoubleSourceTrapDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DoubleSourceTrapDemo {
    public static void main(String[] args) {
        BigDecimal decimal = new BigDecimal("2.675");
        BigDecimal binary = new BigDecimal(2.675);

        System.out.println("decimal = " + decimal.setScale(
                2, RoundingMode.HALF_EVEN));
        System.out.println("from double = " + binary.setScale(
                2, RoundingMode.HALF_EVEN));
        System.out.println("double sum = " + (0.1 + 0.2));
    }
}
~~~

~~~bash
javac DoubleSourceTrapDemo.java
java DoubleSourceTrapDemo
~~~

Kết quả:

~~~text
decimal = 2.68
from double = 2.67
double sum = 0.30000000000000004
~~~

Giá trị decimal đúng 2.675 nằm giữa 2.67 và 2.68; HALF_EVEN chọn chữ số cuối 8 chẵn. Giá trị xuất phát từ double hơi nhỏ hơn điểm giữa nên làm tròn về 2.67. `BigDecimal.valueOf(double)` dùng biểu diễn chuỗi chuẩn của double và có thể là lựa chọn tốt hơn `new BigDecimal(double)` khi buộc phải nhận double, nhưng **không phục hồi** precision đã mất ở những phép tính trước đó.

## 8. Format rồi parse có thể thay đổi scale mà không thay đổi giá trị

BigDecimal lưu cả giá trị và scale. `2.0` có scale 1, trong khi `2.00` có scale 2. Hai số bằng nhau về giá trị theo `compareTo`, nhưng khác nhau theo `equals`. Formatter có thể thêm số 0 cuối do `minimumFractionDigits` hoặc bỏ chữ số/zero do `maximumFractionDigits`, nên round-trip có thể làm thay đổi scale ngay cả khi không làm thay đổi giá trị toán học.

### Ví dụ Java 17: hai giá trị numeric bằng nhau nhưng equals khác

Lưu thành `FormatScaleDemo.java`:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

public class FormatScaleDemo {
    public static void main(String[] args) {
        BigDecimal source = new BigDecimal("2.0");
        DecimalFormat formatter = new DecimalFormat(
                "0.00", DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setParseBigDecimal(true);

        String text = formatter.format(source);
        BigDecimal parsed = (BigDecimal) formatter.parse(
                text, new ParsePosition(0));

        System.out.println("source = " + source);
        System.out.println("display = " + text);
        System.out.println("parsed = " + parsed);
        System.out.println("source scale = " + source.scale());
        System.out.println("parsed scale = " + parsed.scale());
        System.out.println("numeric equal = "
                + (source.compareTo(parsed) == 0));
        System.out.println("equals = " + source.equals(parsed));
    }
}
~~~

~~~bash
javac FormatScaleDemo.java
java FormatScaleDemo
~~~

Kết quả:

~~~text
source = 2.0
display = 2.00
parsed = 2.00
source scale = 1
parsed scale = 2
numeric equal = true
equals = false
~~~

Nếu muốn cố ý chuẩn hóa scale thành 2, dùng `source.setScale(2, roundingMode)`. Việc đổi scale có thể ảnh hưởng `equals/hashCode` và những khóa BigDecimal trong `HashMap`, nên đây là quyết định dữ liệu chứ không phải mẹo format vô hại.

## 9. Làm tròn vào thời điểm nào?

Làm tròn **từng dòng** rồi cộng có thể khác cộng **giá trị chính xác** rồi làm tròn một lần ở cuối. Giả sử ba giá trị `1.005` và dùng HALF_EVEN để giữ hai chữ số lẻ:

- Mỗi giá trị làm tròn trước: `1.00`, tổng `3.00`.
- Tổng chính xác: `3.015`, làm tròn sau: `3.02`.

### Ví dụ Java 17: hai chính sách tính tổng

Lưu thành `RoundingStageDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class RoundingStageDemo {
    public static void main(String[] args) {
        BigDecimal[] entries = {
            new BigDecimal("1.005"),
            new BigDecimal("1.005"),
            new BigDecimal("1.005")
        };
        BigDecimal exact = BigDecimal.ZERO;
        BigDecimal sumOfRounded = BigDecimal.ZERO;

        for (BigDecimal x : entries) {
            exact = exact.add(x);
            sumOfRounded = sumOfRounded.add(
                    x.setScale(2, RoundingMode.HALF_EVEN));
        }

        System.out.println("exact total = " + exact);
        System.out.println("sum rounded lines = " + sumOfRounded);
        System.out.println("round after sum = " + exact.setScale(
                2, RoundingMode.HALF_EVEN));
    }
}
~~~

~~~bash
javac RoundingStageDemo.java
java RoundingStageDemo
~~~

Kết quả:

~~~text
exact total = 3.015
sum rounded lines = 3.00
round after sum = 3.02
~~~

Cả hai phép làm tròn đều tuân theo HALF_EVEN. Khác biệt đến từ **thời điểm** áp dụng. Trong tiền tệ, thuế, hóa đơn, báo cáo, phải xác định nghiệp vụ yêu cầu làm tròn từng khoản, từng giao dịch hay tổng. Không để hành vi hiển thị hai chữ số lẻ làm thay đổi dữ liệu tính toán trung gian một cách âm thầm.

## 10. Khi nào phải từ chối thay vì làm tròn?

Đôi khi quy định không cho phép làm tròn tự động; dữ liệu vượt quá số chữ số lẻ có giá trị phải bị từ chối. `RoundingMode.UNNECESSARY` hỗ trợ xác minh BigDecimal có thể chuyển về scale mong muốn **mà không mất thông tin số học** hay không.

### Ví dụ Java 17: kiểm tra cents chính xác

Lưu thành `RequireExactScaleDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class RequireExactScaleDemo {
    static BigDecimal requireCents(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static void main(String[] args) {
        System.out.println(requireCents(new BigDecimal("12.5")));
        System.out.println(requireCents(new BigDecimal("12.5000")));

        try {
            System.out.println(requireCents(new BigDecimal("12.345")));
        } catch (ArithmeticException ex) {
            System.out.println("Rejected: rounding required");
        }
    }
}
~~~

~~~bash
javac RequireExactScaleDemo.java
java RequireExactScaleDemo
~~~

Kết quả:

~~~text
12.50
12.50
Rejected: rounding required
~~~

`12.5000` có bốn chữ số lẻ trong **chuỗi đầu vào**, nhưng giá trị có thể chuyển chính xác thành `12.50` vì phần bỏ chỉ gồm số 0. Nếu yêu cầu cấm cả input có hơn hai chữ số lẻ, kể cả zero, phải kiểm tra **cú pháp đầu vào**, không chỉ kiểm tra khả năng biểu diễn số học.

## 11. Luồng dữ liệu rõ ràng cho ứng dụng

Một luồng xử lý thập phân đáng tin nên tách biệt các bước:

1. **Xác định kiểu đầu vào:** chuỗi decimal chuẩn của máy hay chuỗi người dùng theo locale? Với input locale, dùng parser theo đúng locale và kiểm tra toàn chuỗi cùng các quy tắc được phép.
2. **Giữ giá trị số trong BigDecimal** nếu nghiệp vụ cần decimal chính xác. Tránh trung gian double hoặc chuỗi đã format để trình bày.
3. **Kiểm tra ràng buộc** min/max, dấu âm, precision, scale, mã tiền tệ và yêu cầu từ chối hay làm tròn.
4. **Tính toán trên số gốc** bằng API BigDecimal. Nếu phép tính đòi MathContext hoặc RoundingMode, cung cấp chính sách tường minh.
5. **Làm tròn ở đúng ranh giới nghiệp vụ** bằng setScale hoặc MathContext, không bị điều khiển bởi formatter.
6. **Chỉ format tại ranh giới đầu ra** theo locale và yêu cầu giao diện. Lưu/trao đổi bằng dạng decimal machine-readable có hợp đồng rõ ràng.

`NumberFormat` và `DecimalFormat` có trạng thái mutable, không mặc định thread-safe. Không chia sẻ cùng một instance formatter mutable giữa nhiều luồng mà thiếu đồng bộ phù hợp. Với phần tính toán, tránh lệ thuộc `Locale.getDefault()` khi kết quả phải nhất quán trên nhiều máy.

## 12. Sai lầm thường gặp

1. **Format→parse làm tròn số học:** biến logic tính toán thành logic trình bày.
2. **Parse bằng BigDecimal(String) chuỗi có grouping:** `"1,234.57"` không phải literal decimal hợp lệ.
3. **Format và parse ở locale khác nhau:** dấu phẩy/chấm có thể được hiểu ngược, parse sai hoặc thất bại.
4. **Cast NumberFormat.parse sang BigDecimal trực tiếp:** chưa có `setParseBigDecimal(true)` có thể nhận Long/Double.
5. **Cho rằng parse thành công nghĩa là chuỗi hợp lệ hoàn toàn:** parser có thể chỉ đọc tiền tố hoặc chấp nhận grouping lỏng; dùng ParsePosition và validation.
6. **Dùng double sau khi format BigDecimal:** vẫn còn xấp xỉ nhị phân; có thể làm mất precision.
7. **Nhầm format tối đa 2 chữ số lẻ với setScale(2):** cái đầu điều khiển chuỗi, cái sau tạo kết quả số học.
8. **Nhầm precision và scale:** MathContext đếm chữ số có nghĩa; setScale đếm chữ số sau dấu thập phân.
9. **Không truyền RoundingMode:** phụ thuộc mặc định hoặc có thể gặp ArithmeticException.
10. **Round-trip làm đổi scale:** `2.0` và `2.00` khác nhau theo equals/hashCode.
11. **Làm tròn từng bước theo yêu cầu UI:** có thể đổi tổng hoặc công thức nghiệp vụ.
12. **Dùng UNNECESSARY để kiểm tra số chữ số người dùng gõ:** nó chỉ kiểm tra không mất giá trị, không kiểm tra số zero ở cuối văn bản.
13. **Dùng formatter static chung trong xử lý song song:** DecimalFormat không thread-safe.
14. **Lưu chuỗi định dạng cho người đọc vào API/database làm số:** phụ thuộc locale và có thể mất thông tin.

## 13. Bài tập tự kiểm tra

**Câu 1.** `1234.567` format thành `"1,234.57"` rồi parse có còn bằng giá trị nguồn không?

**Đáp án:** Không. Chữ số hàng phần nghìn đã bị loại ở bước format; giá trị parse là `1234.57`.

**Câu 2.** Muốn làm tròn `1234.567` đến hai chữ số lẻ theo HALF_UP phải làm gì?

**Đáp án:** `new BigDecimal("1234.567").setScale(2, RoundingMode.HALF_UP)` cho `1234.57`. Không cần chuỗi trung gian và độc lập locale.

**Câu 3.** Vì sao `"1,234"` có hai cách hiểu?

**Đáp án:** US thường dùng dấu phẩy cho grouping ⇒ 1234; Germany dùng dấu phẩy làm decimal ⇒ 1.234.

**Câu 4.** Tại sao `2.0` format thành `2.00` rồi parse BigDecimal có thể làm `equals` false?

**Đáp án:** Source có scale 1, parse được scale 2; `compareTo` vẫn coi bằng về giá trị.

**Câu 5.** Ba giá trị `1.005` làm tròn từng khoản cho tổng nào, và nếu cộng trước thì cho tổng nào?

**Đáp án:** HALF_EVEN scale 2: tổng từng khoản đã làm tròn là `3.00`, cộng chính xác rồi làm tròn là `3.02`.

**Câu 6.** Vì sao `12.5000.setScale(2, UNNECESSARY)` không bị từ chối?

**Đáp án:** Chỉ bỏ zero nên không mất giá trị. Nếu muốn cấm nhập hơn hai chữ số lẻ phải kiểm tra chuỗi input.

## 14. Ghi nhớ

- **Format là trình bày; parse là đọc văn bản; round là biến đổi số học.** Không nên format rồi parse lại chỉ để làm tròn cho phép tính.
- Dùng `BigDecimal.setScale` cho số chữ số thập phân, `MathContext` cho chữ số có nghĩa và overload `divide` cho thương cần làm tròn.
- Luôn xác định `RoundingMode` và **thời điểm làm tròn** theo nghiệp vụ, độc lập locale giao diện.
- Giữ BigDecimal gốc nếu cần precision đầy đủ; format ở bước xuất, parse đúng một lần khi nhận đầu vào.
- Cẩn thận với `double`, grouping, parser nhận một phần chuỗi, thay đổi scale và formatter dùng chung đa luồng.

Tham khảo Java SE 17 API: `java.math.BigDecimal`, `java.math.MathContext`, `java.math.RoundingMode`, `java.text.DecimalFormat`, `java.text.NumberFormat` và `java.text.ParsePosition`.

</details>

- [Quay lại đầu trang](#back-to-top)
