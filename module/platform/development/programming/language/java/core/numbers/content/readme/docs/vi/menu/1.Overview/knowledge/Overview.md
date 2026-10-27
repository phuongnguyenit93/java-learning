<a id="back-to-top"></a>

# Tổng quan

## Menu
- [Số trong Java là gì và những bài toán cần biểu diễn](#overview-001)
- [Phân biệt giá trị toán học, kiểu dữ liệu và cách lưu trữ](#overview-002)
- [Số nguyên, số dấu phẩy động và số thập phân chính xác](#overview-003)
- [Phạm vi giá trị khác độ chính xác như thế nào](#overview-004)
- [Bản đồ primitive, wrapper, Number, BigInteger và BigDecimal](#overview-005)
- [Chọn kiểu theo ý nghĩa dữ liệu và yêu cầu tính toán](#overview-006)
- [Kiến thức tiên quyết và trình tự học đề xuất](#overview-007)
- [Phạm vi Java Core và cách đối chiếu phiên bản JDK](#overview-008)

## <a id="overview-001">Số trong Java là gì và những bài toán cần biểu diễn</a>

<details>
<summary>Xem chi tiết</summary>

# Số trong Java là gì và những bài toán cần biểu diễn?

## 1. Số trong chương trình là gì?

Trong toán học, số nguyên và số thực là những khái niệm có miền giá trị rất rộng. Trong Java, một **giá trị số** được biểu diễn bằng một kiểu dữ liệu có giới hạn xác định: số bit, miền giá trị, độ chính xác và phép toán được hỗ trợ. Vì bộ nhớ hữu hạn, không một kiểu nguyên thủy nào biểu diễn chính xác mọi số toán học.

Hãy nhìn vào những giá trị đều gồm chữ số nhưng khác ý nghĩa:

- `27` học viên: một **số lượng nguyên**, cần đếm, cộng và so sánh.
- `36.5` °C: một **đại lượng đo**, thường có phần thập phân và sai số.
- `19.90` USD: một **giá tiền**, cần chính sách chính xác thập phân và làm tròn.
- `00123`: có thể là **mã khách hàng**, phải giữ nguyên số 0 đầu, không cần phép cộng.
- `20261027`: có thể là cách viết ngày, không thích hợp cho phép toán số học tùy tiện.

Vì vậy, trước khi chọn `int` hay `double`, hãy xác định **ý nghĩa dữ liệu**, miền hợp lệ, độ chính xác cần thiết, phép toán, đơn vị và định dạng. Một chuỗi ký tự toàn chữ số chưa chắc nên lưu bằng kiểu số.

## 2. Những kiểu số chủ yếu của Java

Sáu kiểu nguyên thủy thường dùng để tính số học là `byte`, `short`, `int`, `long`, `float` và `double`. Java còn có `char` (một kiểu *integral* theo đặc tả, nhưng ý nghĩa chính là đơn vị mã UTF-16) và `boolean` (không phải kiểu số).

| Kiểu | Kích thước | Miền hoặc đặc điểm | Mục đích |
|---|---:|---|---|
| `byte` | 8 bit | −128 đến 127 | Byte/giao thức, dữ liệu nhị phân |
| `short` | 16 bit | −32.768 đến 32.767 | Tương thích giao thức, định dạng 16 bit |
| `int` | 32 bit | −2.147.483.648 đến 2.147.483.647 | Số lượng, chỉ số, phép toán nguyên thông thường |
| `long` | 64 bit | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | Bộ đếm, số nguyên lớn |
| `float` | 32 bit | Dấu phẩy động nhị phân IEEE 754, khoảng 6–9 chữ số thập phân có nghĩa | Dữ liệu 32 bit có thể chấp nhận sai số |
| `double` | 64 bit | Dấu phẩy động nhị phân IEEE 754, khoảng 15–17 chữ số thập phân có nghĩa | Đo lường và tính toán khoa học thông thường |

Độ chính xác của `float`/`double` là **số chữ số có nghĩa**, không phải luôn chính xác đến một số cố định chữ số sau dấu phẩy. Đây là kiểu **xấp xỉ nhị phân**: các giá trị như `0.1` thập phân thường không được lưu chính xác tuyệt đối. `float` và `double` còn có `NaN`, vô cực và số 0 có dấu.

Hai lớp `java.math` đặc biệt quan trọng:

- **`BigInteger`**: số nguyên chính xác có độ lớn tùy ý trong giới hạn tài nguyên máy; dùng khi `long` cũng không đủ.
- **`BigDecimal`**: số thập phân chính xác theo phần trị nguyên và *scale* (số chữ số theo thang thập phân). Phù hợp cho giá, thuế, phép tính có yêu cầu thập phân và quy tắc làm tròn rõ ràng.

`BigInteger` và `BigDecimal` là **đối tượng**, không phải primitive; dùng các phương thức như `add`, `multiply`, `divide` thay vì toán tử số học thông thường. Các lớp bao `Integer`, `Long`, `Double`... dành cho các tình huống cần đối tượng/generic/collection; cần nhớ có boxing/unboxing và giá trị `null` có thể gây lỗi khi unboxing.

## 3. Ba chiều quan trọng của biểu diễn số

### 3.1. Miền giá trị: số lớn đến đâu?

`int` chứa số nguyên có dấu 32 bit. `long` chứa số nguyên có dấu 64 bit. Một phép cộng, trừ hoặc nhân số nguyên primitive có thể **tràn miền (overflow)**: ví dụ `Integer.MAX_VALUE + 1` thành `Integer.MIN_VALUE`. Java không tự mở rộng kết quả thành `long` và phép cộng nguyên bình thường không tự ném lỗi.

Khi cần phát hiện tràn số, sử dụng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`; chúng ném `ArithmeticException` khi vượt miền. Khi cần miền rộng hơn, chuyển toán hạng sang `long` **trước phép tính**, ví dụ `(long) a * b`; viết `(long) (a * b)` có thể đã quá muộn nếu `a * b` là phép nhân `int` bị tràn. Nếu `long` không đủ, cân nhắc `BigInteger` và đánh giá chi phí bộ nhớ/hiệu năng.

### 3.2. Độ chính xác: số có đúng bằng giá trị mong muốn?

`double` dùng biểu diễn nhị phân có độ chính xác hữu hạn. Giá trị thập phân `0.1` thường chỉ được lưu xấp xỉ, nên `0.1 + 0.2 == 0.3` là `false`. Điều này không làm `double` vô dụng: nó rất phù hợp cho nhiều phép đo và thuật toán số có mô hình sai số được kiểm soát.

Nếu bài toán đòi **đúng theo thập phân**, như cộng tiền `0.10 + 0.20 = 0.30`, hãy dùng `BigDecimal` được tạo từ chuỗi, ví dụ `new BigDecimal("0.10")`. Tránh `new BigDecimal(0.1)`: constructor nhận giá trị nhị phân xấp xỉ sẵn có của literal `double`. `BigDecimal.valueOf(0.1)` thường mang ý nghĩa thập phân mong muốn khi bắt đầu từ literal `double`, nhưng tốt nhất dữ liệu tiền nên đi từ chuỗi hoặc đơn vị nhỏ nhất rõ ràng.

### 3.3. Ngữ nghĩa và đơn vị: đó có thật là đại lượng số học?

Một mã bưu chính `"00123"`, mã nhân viên `"000045"` hay số điện thoại nên dùng `String` để bảo toàn ký tự, số 0 đầu, dấu `+` và định dạng. Ngày tháng dùng `LocalDate`, thời điểm dùng `Instant` và khoảng thời gian dùng `Duration` trong `java.time` khi phù hợp; tránh số nguyên “trần” không rõ là giây, mili giây hay ngày lịch.

Số `5` mét khác `5` giây. Nếu phải nhận số nguyên thủy qua API, đặt tên kèm đơn vị (ví dụ `timeoutMillis`), kiểm tra miền dữ liệu và chuyển đổi ở biên hệ thống.

## 4. Literal và phép toán cơ bản

Các dạng literal thường gặp:

~~~java
int learners = 27;                // hệ thập phân
int oneMillion = 1_000_000;       // _ để dễ đọc
int bits = 0b1010;                // nhị phân: 10
int hex = 0xFF;                   // thập lục phân: 255
long views = 3_000_000_000L;      // hậu tố L cho long
double ratio = 0.125;             // literal thập phân mặc định double
float sample = 0.125f;            // f hoặc F cho float
double distance = 6.02e3;         // 6020.0
~~~

Literal nguyên thập phân không có `L` thường có kiểu `int`, trong khi literal có dấu chấm thập phân thường có kiểu `double` (trừ khi có hậu tố `f`/`F`). Dấu `_` chỉ được đặt giữa các chữ số ở vị trí hợp lệ; `1_.0` là cú pháp sai. Java cũng có literal bát phân với tiền tố `0`: `010` mang giá trị thập phân `8`, dễ gây bất ngờ nếu người đọc tưởng số thập phân.

Một số quy tắc ảnh hưởng trực tiếp đến kết quả:

- `5 / 2` bằng `2` vì hai toán hạng là số nguyên; `5.0 / 2` và `(double) 5 / 2` bằng `2.5`.
- `byte`, `short` và `char` thường được nâng lên `int` trong biểu thức số học (*numeric promotion*).
- `int` có thể mở rộng sang `long` và `double`, nhưng **chuyển sang dấu phẩy động vẫn có thể mất độ chính xác**, nhất là số nguyên lớn.
- Ép kiểu từ `double` sang `int` bỏ phần thập phân đối với giá trị hữu hạn trong miền; ngoài miền hữu hạn kết quả bão hòa ở biên, còn `NaN` thành 0. Không coi cast là bảo đảm không mất thông tin.
- Literal `byte b = 100;` được chấp nhận vì hằng số `int` nằm trong miền `byte`, nhưng không thể tự động chuyển mọi **biến** `int` sang `byte`.

Khi nhập số từ văn bản, có thể dùng `Integer.parseInt("42")`, `Long.parseLong("3000000000")`, `new BigInteger("12345678901234567890")` hoặc `new BigDecimal("19.90")`. Đầu vào không hợp lệ hoặc vượt miền sẽ tạo lỗi như `NumberFormatException`; cần kiểm tra và xử lý ở biên ứng dụng. Với số hiển thị theo khu vực (dấu phẩy/dấu chấm phân cách), định nghĩa `Locale` và công cụ parse/format tương ứng; không tự thay ký tự một cách mơ hồ.

## 5. Bài toán nào chọn kiểu nào?

| Dữ liệu cần biểu diễn | Ví dụ | Lựa chọn khởi đầu | Điều cần xác định |
|---|---|---|---|
| Số lượng thông thường | 27 học viên; chỉ số mảng | `int` | Có âm không, giới hạn lớn nhất? |
| Bộ đếm lớn | 3 tỷ lượt truy cập | `long` | Có cộng dồn gây tràn không? |
| Dữ liệu nhị phân/giao thức | Byte, số nguyên 16 bit | `byte`, `short` | Signedness, cách giải mã, thứ tự byte |
| Giá/tiền/thuế | 19.90 × 3 | `BigDecimal` hoặc `long` lưu đơn vị nhỏ nhất theo hợp đồng | Tiền tệ, scale, làm tròn |
| Phép đo vật lý | 36.5 °C; 2.75 m | `double` | Sai số thiết bị, đơn vị và cách so sánh |
| Số nguyên cực lớn | Số nguyên 100 chữ số | `BigInteger` | Thời gian và bộ nhớ |
| Tỉ lệ, xác suất | 17/23; 12.5% | `double` nếu cho phép xấp xỉ; `BigDecimal` khi thập phân phải chính xác | Miền 0..1, cách làm tròn/hiển thị |
| Định danh | `"00123"`; số điện thoại | `String` | Bảo toàn số 0 đầu và ký tự |
| Thời gian | 90 phút; thời điểm UTC | `Duration`; `Instant` | Đơn vị, lịch, múi giờ |

Bảng là **gợi ý bắt đầu**, chưa thay thế quy tắc nghiệp vụ. Chẳng hạn, hóa đơn không chỉ cần `BigDecimal`: còn phải quy định tiền tệ, thời điểm làm tròn, số chữ số sau dấu phẩy và cách phân bổ phần lẻ. Tỉ lệ trong thống kê có thể cần `double`, trong hệ thống tài chính có thể cần phương pháp chính xác thập phân.

### Một kịch bản bán vé

- `seatCount = 120` là số lượng ghế, `int` thường phù hợp.
- `ticketPrice = 149.90` là giá tiền, biểu diễn bằng `BigDecimal("149.90")`.
- `bookingCode = "000120"` là mã đặt chỗ; dùng `String`, không làm mất `00`.
- `elapsed = 90 phút` là khoảng thời gian; dùng `Duration.ofMinutes(90)`.
- `successRate = 0.973` là tỷ lệ đo thống kê; `double` có thể phù hợp nếu sai số được chấp nhận.

Cùng là chữ số, năm trường hợp đòi năm cách hiểu dữ liệu khác nhau.

## 6. Ví dụ Java 17+ độc lập: biểu diễn đúng từng đại lượng

Tạo tệp `NumberRepresentationDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberRepresentationDemo {
    public static void main(String[] args) {
        int students = 27;
        long views = 3_000_000_000L;
        double temperature = 36.5;

        BigDecimal ticketPrice = new BigDecimal("19.90");
        BigDecimal ticketCount = new BigDecimal("3");
        BigDecimal total = ticketPrice.multiply(ticketCount);

        BigInteger hugeNumber =
                new BigInteger("123456789012345678901234567890");
        String customerCode = "00123";

        System.out.println("students=" + students);
        System.out.println("views=" + views);
        System.out.println("temperature=" + temperature);
        System.out.println("total=" + total.toPlainString());
        System.out.println("huge+1=" + hugeNumber.add(BigInteger.ONE));
        System.out.println("customerCode=" + customerCode);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac NumberRepresentationDemo.java
java NumberRepresentationDemo
~~~

Đầu ra chính xác:

~~~text
students=27
views=3000000000
temperature=36.5
total=59.70
huge+1=123456789012345678901234567891
customerCode=00123
~~~

`views` vượt miền `int` nên dùng `long`; `temperature` là phép đo cho phép biểu diễn floating-point. `BigDecimal` giữ hai chữ số thập phân trong phép nhân `19.90 × 3 = 59.70`. `BigInteger` cho phép cộng số nguyên lớn hơn miền `long`. `customerCode` bảo toàn số 0 đầu vì nó là mã định danh.

## 7. Ví dụ Java 17+ độc lập: lỗi phổ biến

Tạo tệp `NumberPitfalls.java`:

~~~java
public class NumberPitfalls {
    public static void main(String[] args) {
        int a = 5;
        int b = 2;

        System.out.println("5 / 2 (int) = " + (a / b));
        System.out.println("5 / 2 (double) = " + ((double) a / b));
        System.out.println("int overflow = " + (Integer.MAX_VALUE + 1));
        System.out.println("0.1 + 0.2 == 0.3 = " + ((0.1 + 0.2) == 0.3));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
            System.out.println("overflow detected");
        }
    }
}
~~~

~~~bash
javac NumberPitfalls.java
java NumberPitfalls
~~~

Đầu ra:

~~~text
5 / 2 (int) = 2
5 / 2 (double) = 2.5
int overflow = -2147483648
0.1 + 0.2 == 0.3 = false
overflow detected
~~~

Ba hiện tượng **khác nhau**: chia nguyên bỏ phần lẻ, phép cộng nguyên tràn miền, và floating-point chỉ lưu xấp xỉ. Cách xử lý phụ thuộc bài toán: dùng toán hạng floating-point khi muốn thương xấp xỉ, `Math.addExact` hoặc kiểu rộng hơn để xử lý số nguyên, `BigDecimal` khi cần chính xác thập phân. So sánh `double` gần đúng bằng dung sai chỉ phù hợp khi dung sai được xác định theo đơn vị, độ lớn và sai số của bài toán; không chọn một `epsilon` cố định cho mọi trường hợp.

## 8. BigDecimal, phép chia và làm tròn

`BigDecimal` không hỗ trợ toán tử `+`/`*`/`/` như primitive; nó có `add`, `subtract`, `multiply`, `divide`. Phép chia `10 / 3` là thập phân vô hạn: gọi `new BigDecimal("10").divide(new BigDecimal("3"))` sẽ ném `ArithmeticException` vì không thể biểu diễn chính xác bằng số chữ số hữu hạn. Hãy xác định scale và quy tắc làm tròn theo nghiệp vụ.

Ví dụ tạo tệp `DecimalRoundingDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalRoundingDemo {
    public static void main(String[] args) {
        BigDecimal result = new BigDecimal("10")
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        System.out.println(result.toPlainString());
    }
}
~~~

~~~bash
javac DecimalRoundingDemo.java
java DecimalRoundingDemo
~~~

Kết quả:

~~~text
3.33
~~~

Thêm một chi tiết: `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false` vì `equals` xem xét cả *scale*. `compareTo` giữa hai giá trị đó trả 0 vì bằng nhau về mặt số học. Cần chọn cách so sánh phù hợp, đặc biệt khi dùng làm khóa bản đồ hoặc kiểm tra điều kiện nghiệp vụ.

Một hệ thống cũng có thể lưu giá bằng số **đơn vị tiền nhỏ nhất** với `long`, ví dụ giá quy ra xu. Cách này phù hợp nếu đơn vị, khoảng giá trị và quy tắc chuyển đổi được quy định rõ; nó không tự giải quyết các phép chia, tỷ giá hoặc làm tròn tùy ý.

## 9. Checklist chọn cách biểu diễn

1. **Bản chất dữ liệu:** đó là số lượng để tính toán, phép đo, tỷ lệ, số tiền, mã định danh hay ngày/giờ?
2. **Miền giá trị:** nhỏ nhất/lớn nhất, có âm không, có thể tăng không giới hạn không, giá trị trung gian có vượt miền không?
3. **Độ chính xác:** cần số nguyên chính xác, thập phân chính xác hay xấp xỉ floating-point? Có dung sai được đo/thiết kế không?
4. **Phép toán:** có chia, cộng dồn, tính thuế, nhân nhiều lần hoặc so sánh bằng không? Quy tắc overflow/làm tròn là gì?
5. **Đơn vị và định dạng:** mét hay km, giây hay mili giây, VND hay USD, ký tự mã cần bảo toàn, locale nhập/xuất?
6. **Giá trị biên:** thử với 0, số âm, cực đại, số rất lớn, mẫu số bằng 0, dữ liệu sai, sai số tích lũy và các phép toán trung gian.

### Những hiểu lầm thường gặp

- “`double` có 64 bit nên tiền luôn chính xác”: sai, vì đó là dấu phẩy động **nhị phân**.
- “`int` tràn thì Java tự đổi sang `long`”: sai, cần nâng kiểu trước phép tính hoặc kiểm tra overflow.
- “Toàn chữ số thì phải lưu bằng số”: sai, mã định danh và số điện thoại thường là văn bản.
- “`BigDecimal` tự chọn cách làm tròn như kế toán”: sai, phải đặt scale và `RoundingMode` theo hợp đồng.
- “`float` có bảy chữ số **sau dấu phẩy**”: sai, cần hiểu số chữ số **có nghĩa**.
- “Cast là chuyển đổi không mất dữ liệu”: sai, thu hẹp kiểu và chuyển sang floating-point có thể mất thông tin.
- “`BigInteger` vô hạn hoàn toàn”: sai, nó bị giới hạn bởi bộ nhớ và chi phí tính toán của môi trường chạy.

## 10. Câu hỏi tự kiểm tra

1. Bộ đếm có thể tăng đến 4 tỷ: `int` hay `long` phù hợp hơn? Vì sao?
2. Vì sao `"000045"` là mã nhân viên nên giữ dưới dạng `String`?
3. Khi cộng tiền `0.10` và `0.20`, vì sao không mặc nhiên dùng `double`?
4. `5 / 2` và `5.0 / 2` khác nhau thế nào?
5. Một cảm biến có sai số ±0,1 °C: cần làm rõ gì trước khi so sánh hai số đo?
6. Với `int a, b`, tại sao `(long) (a * b)` có thể đã mất thông tin, còn `(long) a * b` có thể tránh được?

**Gợi ý đáp án:** (1) `long` vì 4 tỷ vượt `int`; (2) phải giữ số 0 đầu và mã không dùng phép toán số học; (3) `double` biểu diễn xấp xỉ nhị phân còn `BigDecimal` phù hợp yêu cầu thập phân chính xác; (4) một biểu thức chia nguyên nhận `2`, biểu thức kia nhận `2.5`; (5) đơn vị, độ chính xác cảm biến và dung sai so sánh; (6) phép nhân `int` có thể tràn **trước khi** ép kiểu, nên cần nâng toán hạng trước.

**Ghi nhớ:** Chọn kiểu số trong Java là chọn **mô hình biểu diễn** của đại lượng: ý nghĩa, miền giá trị, độ chính xác, đơn vị, phép toán và quy tắc làm tròn đều ảnh hưởng đến tính đúng đắn của chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Phân biệt giá trị toán học, kiểu dữ liệu và cách lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giá trị toán học, kiểu dữ liệu và cách lưu trữ trong Java

Trong lập trình số, ba khái niệm thường bị trộn lẫn: **giá trị toán học** (con số ta muốn mô tả), **kiểu dữ liệu** (những giá trị và phép toán chương trình cho phép) và **cách biểu diễn/lưu trữ** (các bit hoặc cấu trúc dữ liệu máy tính dùng để mã hóa giá trị). Phân biệt ba tầng này giúp giải thích vì sao `0.1 + 0.2` không ra đúng `0.3`, vì sao `int` tràn số, và vì sao hai số trông giống nhau vẫn có hành vi khác nhau trong Java.

## 1. Ba tầng cần phân biệt

| Tầng | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| Giá trị toán học | Ta đang nói đến đại lượng nào, độc lập với máy tính? | Số nguyên 42; phân số 1/3; số thực 0,1; số âm −5 |
| Kiểu dữ liệu | Java cho phép miền giá trị và phép toán nào? | `int`, `long`, `double`, `BigInteger`, `BigDecimal` |
| Biểu diễn/lưu trữ | Giá trị được mã hóa bằng gì và có chính xác không? | `int` có 32 bit bù hai; `double` dùng nhị phân dấu phẩy động; `BigDecimal` dùng số nguyên không tỉ lệ và scale |

Ví dụ, **42** là một giá trị toán học. Những cách viết sau có thể cùng mang giá trị số học 42 nhưng thuộc kiểu/biểu diễn khác nhau:

```java
int a = 42;
long b = 42L;
double c = 42.0;
BigInteger d = new BigInteger("42");
BigDecimal e = new BigDecimal("42.00");
```

`a` và `b` là số nguyên chính xác trong miền hữu hạn, `c` là số chấm động nhị phân (42 là một số nguyên nhỏ biểu diễn chính xác được), `d` là số nguyên có độ lớn tùy ý trong giới hạn tài nguyên, còn `e` biểu diễn thập phân chính xác với **unscaled value = 4200**, **scale = 2**. Trong `BigDecimal`, `42.00` và `42.0` bằng nhau về **giá trị số học** nhưng khác **scale**, điều ảnh hưởng đến `equals()`.

> Ghi nhớ: cách **hiển thị** số bằng ký tự (`"42"`, `"42.00"`) là một tầng khác. Một chuỗi văn bản chưa tự động là một giá trị kiểu số.

## 2. Miền giá trị và cách mã hóa của số nguyên nguyên thủy

Java quy định kích thước và khoảng giá trị cho các kiểu nguyên có dấu:

| Kiểu | Độ rộng logic | Khoảng giá trị (bao gồm hai đầu) |
| --- | ---: | --- |
| `byte` | 8 bit | −128 đến 127 |
| `short` | 16 bit | −32.768 đến 32.767 |
| `int` | 32 bit | −2.147.483.648 đến 2.147.483.647 |
| `long` | 64 bit | −9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |

Với kiểu số nguyên có dấu rộng `n` bit, khoảng là `−2^(n−1)` đến `2^(n−1)−1`, vì có hữu hạn `2^n` mẫu bit. Java dùng **bù hai (two's complement)** để biểu diễn số nguyên có dấu. Ví dụ ở 8 bit: `0000_0101` diễn tả +5, còn `1111_1011` diễn tả −5. Cùng dãy bit chỉ có ý nghĩa số học sau khi gắn với **quy tắc diễn giải và độ rộng**.

`char` là kiểu nguyên 16 bit **không dấu**, có miền `0` đến `65535`, dùng cho đơn vị mã UTF-16. `char` không phải kiểu dành cho tính toán số thông thường, và một ký tự Unicode có thể cần hai `char`. Java không có kiểu nguyên nguyên thủy không dấu độc lập như `unsigned int` của C; các hàm như `Integer.toUnsignedLong` và `Integer.divideUnsigned` chỉ **diễn giải thao tác** trên mẫu bit của `int` theo nghĩa không dấu.

### Tràn số không làm giá trị toán học thay đổi, mà làm kết quả biểu diễn khác đi

```java
int max = Integer.MAX_VALUE;
System.out.println(max + 1);             // -2147483648
System.out.println(Math.addExact(max, 1)); // ném ArithmeticException
```

Giá trị toán học `2_147_483_647 + 1` là `2_147_483_648`, nhưng kết quả `int` thông thường vòng qua giá trị nhỏ nhất do phép toán số nguyên chỉ giữ 32 bit kết quả. Java **không tự ném exception** cho `+`, `-`, `*` tràn số nguyên. Dùng `Math.addExact`, `subtractExact`, `multiplyExact` hoặc chuyển sang `long`/`BigInteger` trước phép toán khi cần kiểm soát tràn.

Chú ý: ép kiểu **sau khi phép nhân `int` đã tràn** không khôi phục được kết quả đúng:

```java
int x = 1_000_000_000;
long wrong = (long) (x * 3); // -1294967296: nhân int trước
long right = (long) x * 3;   // 3000000000: nhân long
```

Khi ép hẹp từ `int` sang `byte`, Java chỉ giữ 8 bit thấp; `(byte) 130` cho `-126`, không phải một giá trị số học 130 có thể nằm trong `byte`. Ngược lại, gán `byte b = 130;` là lỗi biên dịch vì hằng nguyên không vừa kiểu đích.

## 3. Kiểu chấm động là phép xấp xỉ nhị phân có quy tắc riêng

`float` (32 bit) và `double` (64 bit) dùng định dạng IEEE 754 **binary32/binary64**, với bit dấu, số mũ và phần trị (*significand*). Độ chính xác hữu hạn: khoảng **7 chữ số thập phân có nghĩa** cho `float` và **15–16 chữ số** cho `double`; đây là ước lượng sử dụng, không phải số chữ số sau dấu phẩy cố định.

Nhiều phân số thập phân như `0.1` không thể viết hữu hạn bằng cơ số 2. Java lưu một số nhị phân gần nhất, vì vậy:

```java
System.out.println(0.1 + 0.2);       // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false
System.out.println(0.5 + 0.25);      // 0.75
```

`0.5 = 1/2` và `0.25 = 1/4` có biểu diễn nhị phân hữu hạn nên chính xác ở đây; `0.1` và `0.2` thì không. **In ra `0.1` không có nghĩa là bit trong `double` lưu đúng phân số toán học 1/10**: `Double.toString` chọn chuỗi thập phân ngắn có thể phân tích trở lại cùng giá trị `double`.

Đừng kiểm tra gần đúng bằng `==` khi sai số làm tròn có thể xuất hiện. Với phép đo có sai số, cân nhắc dung sai tuyệt đối/tương đối theo miền bài toán, chẳng hạn:

```java
static boolean nearlyEqual(double a, double b, double absTol, double relTol) {
    if (Double.doubleToLongBits(a) == Double.doubleToLongBits(b)) return true;
    if (!Double.isFinite(a) || !Double.isFinite(b)) return false;
    double diff = Math.abs(a - b);
    return diff <= Math.max(absTol, relTol * Math.max(Math.abs(a), Math.abs(b)));
}
```

Hàm này là **chính sách so sánh cho trường hợp có dung sai**, không phải định nghĩa bằng nhau cho mọi bài toán. Chọn `absTol` và `relTol` từ yêu cầu đo lường, không lấy một epsilon cố định cho mọi độ lớn. Nếu cần giá trị tiền tệ/thập phân chính xác, ưu tiên `BigDecimal`.

### Các trường hợp đặc biệt của số chấm động

- `Double.POSITIVE_INFINITY`/`NEGATIVE_INFINITY` có thể xuất hiện do phép tính chấm động tràn độ lớn, hoặc chia số hữu hạn khác 0 cho ±0.0; không phải là số thực hữu hạn.
- `Double.NaN` biểu diễn kết quả không xác định theo IEEE 754; `Double.NaN == Double.NaN` trả `false`. Kiểm tra bằng `Double.isNaN(x)`.
- `+0.0` và `-0.0` so sánh bằng `==` nhưng dấu của chúng có thể ảnh hưởng đến phép tính như `1.0 / -0.0` cho `-Infinity`.
- Với `double`, mọi số nguyên có trị tuyệt đối **không vượt quá `2^53`** đều biểu diễn chính xác, nhưng không phải tất cả số nguyên lớn hơn ngưỡng đó. `double` có thể có phạm vi độ lớn rộng hơn `long` mà vẫn mất chi tiết đơn vị.

## 4. `BigInteger` và `BigDecimal`: độ chính xác được quyết định bởi mô hình dữ liệu

### `BigInteger`: số nguyên chính xác, không giới hạn bởi 32/64 bit

`BigInteger` giữ giá trị nguyên bằng cấu trúc nhiều từ máy, có thể chứa số vượt `Long.MAX_VALUE` và dùng các phương thức `add`, `multiply`, `divide`, `mod`. Độ lớn thực tế bị giới hạn bởi bộ nhớ, hiệu năng và giới hạn triển khai; đối tượng là **immutable**.

```java
BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE)
        .add(BigInteger.ONE);
System.out.println(beyondLong); // 9223372036854775808
```

### `BigDecimal`: số thập phân theo `unscaledValue × 10^(−scale)`

Hai thành phần cốt lõi là **một số nguyên tùy ý** và **scale**, không phải một giá trị nhị phân `double` được “in đẹp”:

```java
BigDecimal amount = new BigDecimal("12.30");
System.out.println(amount.unscaledValue()); // 1230
System.out.println(amount.scale());         // 2
System.out.println(amount);                 // 12.30
```

Tạo `BigDecimal` từ chuỗi thập phân khi nguồn là chuỗi/money decimal:

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
System.out.println(a.add(b)); // 0.3
```

**Tránh `new BigDecimal(0.1)` nếu muốn biểu diễn đúng số thập phân 0.1**: constructor nhận chính giá trị nhị phân `double` đã được làm tròn, nên kết quả chứa các chữ số xấp xỉ kéo dài. `BigDecimal.valueOf(0.1)` thông qua biểu diễn thập phân chuẩn của `double` và thường phù hợp hơn, nhưng nếu dữ liệu đầu vào ban đầu là văn bản thập phân thì giữ chuỗi gốc vẫn rõ ràng nhất.

`BigDecimal` chính xác cho các phép cộng/trừ/nhân trên những số thập phân hữu hạn đã biểu diễn, nhưng **chia có thể tạo khai triển vô hạn**: `BigDecimal.ONE.divide(new BigDecimal("3"))` ném `ArithmeticException` nếu không cung cấp chính sách làm tròn. Cần chọn `scale`/`RoundingMode` hoặc `MathContext`:

```java
BigDecimal oneThird = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP);
System.out.println(oneThird); // 0.33
```

`equals()` của `BigDecimal` xét cả giá trị lẫn scale: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false`. `compareTo()` so sánh giá trị số học và trả `0` trong trường hợp này. Việc loại bỏ zero dư bằng `stripTrailingZeros()` có thể đổi scale/biểu diễn; trong khóa của `HashMap`/`HashSet` phải chú ý hợp đồng `equals/hashCode`.

## 5. Literal, phép toán và ép kiểu có thể thay đổi kết quả

### Viết literal đúng kiểu

| Biểu thức | Kiểu mặc định / giá trị | Ghi chú |
| --- | --- | --- |
| `42` | `int` | Hằng nguyên thập phân mặc định là `int` nếu vừa |
| `42L` | `long` | `L` nên viết hoa, nhất là với số 1 |
| `42.0`, `1e3` | `double` | Hằng dấu phẩy động mặc định là `double` |
| `42.0f` | `float` | Hậu tố `f` hoặc `F` |
| `0b101010`, `0x2A` | `int` có giá trị 42 | Nhị phân và thập lục phân chỉ là cách viết literal |
| `1_000_000` | `int` có giá trị 1.000.000 | Dấu gạch dưới là cú pháp, không lưu trong giá trị |
| `"42"` | `String` | Văn bản, không tự động là số |

Chú ý literal `010` trong Java là **bát phân** (giá trị 8), không phải mười; đặt số 0 đầu literal nguyên dễ gây nhầm lẫn. Ký tự `-` trong `-42` là toán tử dấu âm, không phải “bit dấu” trong cú pháp literal.

### Kiểu của toán hạng quyết định phép toán

```java
System.out.println(1 / 2);          // 0: chia số nguyên
System.out.println(1.0 / 2);        // 0.5: phép chia double
System.out.println(5 / 2 * 2);      // 4: phần lẻ đã mất sau chia
System.out.println((double) 5 / 2); // 2.5: đổi trước phép chia
```

Các toán hạng `byte`, `short` và `char` thường được **promote** lên `int` trước phép toán số học. Ví dụ `byte a = 1, b = 2; byte c = a + b;` không biên dịch vì `a + b` có kiểu `int`, dù giá trị toán học 3 vừa `byte`. Gán sang kiểu hẹp cần conversion hợp lệ hoặc cast tường minh và chấp nhận khả năng mất dữ liệu.

Ép `double` sang `int` **cắt bỏ phần thập phân hướng về 0** khi giá trị trong khoảng cho phép; NaN được chuyển về 0, các giá trị vượt miền được bão hòa về biên tương ứng. Vì thế `(int) 3.9` là 3 còn `(int) -3.9` là −3; đây không phải làm tròn đến số nguyên gần nhất.

**Auto-boxing** (`int` ↔ `Integer`) thay đổi việc dùng primitive thành đối tượng wrapper, không mở rộng miền giá trị của số. Với đối tượng, `==` so sánh danh tính tham chiếu; dùng `equals` nếu so sánh giá trị wrapper, và chú ý `null` khi unboxing.

## 6. Nói về “lưu trữ” mà không nhầm với vị trí trong bộ nhớ

- Một biến primitive kiểu `int` mô tả một giá trị 32 bit theo quy tắc Java; kiểu `double` dùng biểu diễn 64 bit. Điều này **không đồng nghĩa** với lời hứa rằng một biến luôn chiếm đúng 4/8 byte trên stack: bố cục stack frame, thanh ghi, alignment và tối ưu JIT là chi tiết triển khai JVM.
- Với `BigInteger`/`BigDecimal`, biến tham chiếu trỏ đến đối tượng có trạng thái cần thiết; đối tượng còn có overhead, có thể dùng mảng nội bộ, và được quản lý bởi JVM/GC. Không thể suy ra tổng bộ nhớ chỉ từ số chữ số của chuỗi hiển thị.
- Mẫu bit, kiểu và cách hiển thị phải được tách riêng: ví dụ các bit thấp `1111_1111` có thể đọc là `255` nếu là byte không dấu trong một giao thức, hoặc `-1` nếu diễn giải kiểu `byte` Java. Một ký tự `'9'` có mã Unicode khác số nguyên `9`.
- Chuyển đổi qua JSON, CSV, cơ sở dữ liệu hoặc giao thức mạng cần đặc tả **loại số, miền giá trị, độ chính xác, đơn vị và quy tắc làm tròn**; một chuỗi `"9007199254740993"` có thể mất chính xác nếu trung gian chuyển qua số `double`.

## 7. Ví dụ Java 17+ chạy độc lập

Lưu vào `NumericLayersDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class NumericLayersDemo {
    public static void main(String[] args) {
        int integer = 42;
        double floating = 42.0;
        BigDecimal decimal = new BigDecimal("42.00");

        System.out.println("int: " + integer);
        System.out.println("double: " + floating);
        System.out.println("BigDecimal: " + decimal);
        System.out.println("0.1 + 0.2 = " + (0.1 + 0.2));
        System.out.println("1 / 2 = " + (1 / 2));
        System.out.println("1.0 / 2 = " + (1.0 / 2));
        System.out.println("int overflow = " + (Integer.MAX_VALUE + 1));
        System.out.println("decimal sum = "
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("beyond long = "
                + BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        System.out.println("rounded third = "
                + BigDecimal.ONE.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        System.out.println("same numeric value = "
                + (decimal.compareTo(new BigDecimal("42.0")) == 0));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac NumericLayersDemo.java
java NumericLayersDemo
```

Output:

```text
int: 42
double: 42.0
BigDecimal: 42.00
0.1 + 0.2 = 0.30000000000000004
1 / 2 = 0
1.0 / 2 = 0.5
int overflow = -2147483648
decimal sum = 0.3
beyond long = 9223372036854775808
rounded third = 0.33
same numeric value = true
```

Mỗi dòng thể hiện sự khác nhau giữa **giá trị mong muốn**, **quy tắc tính của kiểu** và **khả năng biểu diễn**. `double` biểu diễn chính xác `42` nhưng không biểu diễn chính xác `0.1`; `BigDecimal` giữ được thập phân 0.1 khi xây dựng từ chuỗi; `int` không thể biểu diễn `MAX_VALUE+1`; `BigInteger` làm được.

## 8. Chọn kiểu từ yêu cầu của bài toán

| Nhu cầu | Lựa chọn thường phù hợp | Điều cần kiểm tra |
| --- | --- | --- |
| Đếm, chỉ số, số lượng trong miền nhỏ | `int` | Tràn khi cộng/nhân, kể cả ở trung gian |
| Số nguyên lớn hơn miền `int`, timestamp/đơn vị phù hợp | `long` | Vẫn hữu hạn; kiểm tra đơn vị và tràn |
| Số nguyên lớn tùy ý (mật mã, tổ hợp, số lớn) | `BigInteger` | Chi phí CPU/bộ nhớ, đối tượng immutable |
| Đo đạc/khoa học, tính toán chấp nhận sai số chấm động | `double`; đôi khi `float` khi ràng buộc tài nguyên | NaN, infinity, làm tròn, dung sai |
| Tiền tệ/quy tắc decimal cụ thể, giá trị thập phân cần chính xác | `BigDecimal` hoặc số nguyên ở đơn vị nhỏ nhất theo hợp đồng | Scale, rounding, phép chia, định dạng, đơn vị tiền |
| Dữ liệu số từ chuỗi ngoài hệ thống | Parse có kiểm tra miền và định dạng | Không coi cách hiển thị là kiểu; tránh chuyển qua `double` khi cần decimal chính xác |

### Những lỗi phổ biến cần tự kiểm tra

1. **Nhầm số hiển thị với số được lưu:** `System.out.println(0.1)` cho “0.1” nhưng `double` lưu một xấp xỉ nhị phân.
2. **Chọn kiểu theo số chữ số nhìn thấy:** `double` chứa giá trị rất lớn nhưng không giữ chính xác mọi số nguyên 64 bit.
3. **Chỉ cast ở kết quả:** `(long) (a * b)` không cứu được phép nhân `int` đã tràn.
4. **Tưởng `BigDecimal` tự khắc phục đầu vào sai:** xây dựng từ `new BigDecimal(0.1)` đã nhận xấp xỉ `double`; hãy giữ dữ liệu decimal gốc.
5. **Quên phép toán điều khiển bởi kiểu:** `1 / 2` bằng 0; `byte + byte` ra `int`.
6. **Nhầm “exact” với vô hạn:** `BigDecimal` tính thập phân hữu hạn chính xác trong nhiều phép toán nhưng chia 1/3 phải chỉ định làm tròn; `BigInteger` chính xác nhưng chịu giới hạn tài nguyên.
7. **Khẳng định cứng vị trí stack/heap hoặc số byte đối tượng:** kích thước logic của primitive được Java định nghĩa; bố trí bộ nhớ vật lý do JVM quyết định.

## 9. Tự kiểm tra mức hiểu

- Vì sao `42`, `42.0` và `new BigDecimal("42.00")` có thể biểu diễn cùng giá trị toán học nhưng không cùng kiểu, scale và phép toán?
- Với `int` 32 bit, tại sao `Integer.MAX_VALUE + 1` âm trong khi số nguyên toán học không âm?
- Tại sao `0.5` biểu diễn chính xác bằng binary64 nhưng `0.1` thì không? Khi nào chọn `double`, khi nào chọn `BigDecimal`?
- Tại sao `BigDecimal("1.0").equals(BigDecimal("1.00"))` khác `compareTo() == 0`? Tại sao điều này quan trọng khi dùng làm khóa?
- Nếu chuỗi đầu vào là `"9007199254740993"`, chọn `long`, `double` hay `BigInteger` để giữ nguyên giá trị và vì sao?

**Nguyên tắc ứng dụng:** bắt đầu từ **miền giá trị toán học và yêu cầu độ chính xác**, chọn **kiểu và phép toán** đáp ứng yêu cầu đó, rồi xác minh **biểu diễn, chuyển đổi, tràn số và hiển thị** ở từng ranh giới dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Số nguyên, số dấu phẩy động và số thập phân chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# Số nguyên, số dấu phẩy động và số thập phân chính xác trong Java

Trong Java, “số” không phải một kiểu duy nhất. Chọn kiểu dữ liệu là chọn **miền giá trị**, **cách biểu diễn**, **mức chính xác** và **quy tắc tính toán**. Ba nhóm cần phân biệt là số nguyên (integer), số dấu phẩy động nhị phân (floating point) và số thập phân chính xác (decimal với `BigDecimal`). Bài này tập trung vào ý nghĩa, hành vi thực tế và cách chọn đúng cho từng phép tính.

## 1. Bản đồ lựa chọn nhanh

| Nhu cầu | Kiểu nên xét | Lý do và giới hạn |
| --- | --- | --- |
| Đếm, chỉ số mảng, số lượng nguyên trong phạm vi đã biết | `int`, `long` | Tính toán nguyên chính xác **trong phạm vi kiểu**; tràn số cần kiểm soát. |
| Số nguyên lớn hơn `long` | `BigInteger` | Độ lớn tùy ý theo bộ nhớ; phép toán qua phương thức, không dùng `+` với đối tượng. |
| Đo lường, mô phỏng, đồ họa, dữ liệu khoa học chấp nhận sai số làm tròn nhị phân | `double` (hoặc `float` khi có lý do rõ ràng) | Nhanh, hỗ trợ các giá trị đặc biệt; nhiều phân số thập phân không biểu diễn chính xác. |
| Tiền, thuế, đơn giá, phép tính thập phân cần quy tắc làm tròn minh bạch | `BigDecimal` | Lưu hệ số thập phân và scale; vẫn phải quyết định độ chính xác/làm tròn khi phép chia không kết thúc. |
| Số tiền theo đơn vị nhỏ nhất, với thang cố định | `long` hoặc `BigInteger` | Có thể biểu diễn chính xác số đồng/xu/cent nguyên; phải quy định đơn vị, phạm vi và cách chuyển đổi. |

Không có kiểu nào tự giải quyết mọi yêu cầu nghiệp vụ: kể cả khi dùng `BigDecimal`, ứng dụng vẫn cần quy tắc làm tròn, đơn vị, scale và thời điểm làm tròn.

## 2. Số nguyên: chính xác trong miền biểu diễn

### 2.1. Các kiểu nguyên nguyên thủy

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `byte` | 8 | -128 | 127 |
| `short` | 16 | -32.768 | 32.767 |
| `int` | 32 | -2.147.483.648 | 2.147.483.647 |
| `long` | 64 | -9.223.372.036.854.775.808 | 9.223.372.036.854.775.807 |

Các kiểu này có dấu và dùng biểu diễn bù hai (two's complement). `char` là kiểu ký tự 16 bit không dấu, không nên chọn làm kiểu số đếm. Các lớp bao `Byte`, `Short`, `Integer`, `Long` là đối tượng, khác với kiểu nguyên thủy về `null`, boxing và chi phí cấp phát.

Literal nguyên mặc định thường là `int`; thêm `L` để tạo literal `long`. Java cho phép viết nhị phân với `0b`, thập lục phân với `0x`, bát phân với tiền tố `0`, và dùng dấu gạch dưới để dễ đọc:

```java
int population = 1_250_000;
long distance = 9_000_000_000L;
int mask = 0b1010_0001;
int color = 0xFF_A0_10;
```

Đây là các khai báo minh họa, không phải một chương trình chạy độc lập. Không thêm ký tự `L` cho giá trị nguyên `long` lớn dễ khiến trình biên dịch từ chối literal vì nó cố phân tích thành `int` trước. Ngoài ra, một chữ số đầu `0` trên literal nguyên mang nghĩa bát phân, nên `010` là 8, không phải 10.

### 2.2. Tràn số và cách phòng tránh

Phép cộng, trừ, nhân trên `int`/`long` không tự ném ngoại lệ khi tràn; kết quả quay vòng theo độ rộng bit. Ví dụ `Integer.MAX_VALUE + 1` cho `Integer.MIN_VALUE`. Việc gán kết quả sang `long` **sau** khi tính không cứu được phép tính đã tràn:

```java
int count = 1_500_000_000;
long wrong = count * 2;          // Phép nhân int đã tràn trước khi gán.
long correct = (long) count * 2; // Nâng một toán hạng trước phép nhân.
```

Khi tràn được xem là lỗi, dùng `Math.addExact`, `subtractExact`, `multiplyExact`, `incrementExact`, `toIntExact` hoặc `negateExact` ở nơi thích hợp. Chúng ném `ArithmeticException` khi kết quả không biểu diễn được. Cần chọn `long` nếu dữ liệu vượt `int` nhưng vẫn nằm trong 64 bit; chọn `BigInteger` khi không có biên hữu hạn phù hợp.

### 2.3. Chia nguyên, phần dư và số âm

Khi cả hai toán hạng là số nguyên, `/` là **chia nguyên, bỏ phần thập phân hướng về 0**, còn `%` là phép lấy phần dư; mẫu số 0 gây `ArithmeticException`.

| Biểu thức Java | Kết quả | Giải thích |
| --- | ---: | --- |
| `7 / 2` | `3` | Bỏ phần lẻ. |
| `7 / 2.0` | `3.5` | Một toán hạng là `double`. |
| `-7 / 3` | `-2` | Làm tròn về 0. |
| `-7 % 3` | `-1` | Phần dư có dấu của số bị chia. |
| `Math.floorDiv(-7, 3)` | `-3` | Chia làm tròn xuống. |
| `Math.floorMod(-7, 3)` | `2` | Phù hợp quy tắc floor khi số chia dương. |

Với `byte` hoặc `short`, các toán tử số học thường nâng toán hạng lên `int`, nên `byte b = 10; b = b + 1;` không biên dịch nếu không ép kiểu; `b++` có cơ chế chuyển kiểu ngầm riêng. Ép kiểu thu hẹp (`(int) aLong`, `(byte) anInt`) có thể mất bit và đổi giá trị; đừng xem cast là kiểm tra phạm vi.

### 2.4. Số nguyên độ lớn tùy ý: BigInteger

`java.math.BigInteger` lưu số nguyên không giới hạn cố định 32/64 bit và là bất biến (immutable). Tạo từ chuỗi để tránh giới hạn của literal `long`, sau đó dùng `add`, `subtract`, `multiply`, `divide`, `remainder`, `pow`, `gcd`, `compareTo`:

```java
BigInteger huge = new BigInteger("9223372036854775808");
BigInteger next = huge.add(BigInteger.ONE);
boolean larger = next.compareTo(huge) > 0;
```

Đoạn trên cần `import java.math.BigInteger;`. Hàm `add` trả đối tượng mới: gọi `huge.add(BigInteger.ONE);` mà không nhận kết quả sẽ không thay đổi `huge`. `BigInteger` phù hợp cho số đếm hoặc phép toán nguyên vượt `long`, nhưng không mang phần thập phân.

## 3. Số dấu phẩy động: gần đúng theo hệ nhị phân

### 3.1. float và double lưu gì?

Java dùng chuẩn IEEE 754 cho `float` 32 bit và `double` 64 bit. Mỗi giá trị hữu hạn được biểu diễn theo dấu, số mũ và phần trị nhị phân; `float` có khoảng 6–7 chữ số thập phân có nghĩa, `double` khoảng 15–16 chữ số có nghĩa. `double` có phạm vi và độ chính xác lớn hơn, thường là lựa chọn mặc định cho phép tính thực khi được phép có sai số nhị phân. Literal `0.1` có kiểu `double`; literal `0.1f` có kiểu `float`.

Nhiều phân số tưởng chừng đơn giản trong hệ thập phân, ví dụ 1/10, không có biểu diễn nhị phân hữu hạn. Vì thế:

```java
System.out.println(0.1 + 0.2);        // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false
```

Các dòng này là mảnh mã minh họa đặt trong `main`. Sai số không có nghĩa JVM cộng “sai”; phép toán đã làm tròn đến giá trị `double` gần biểu thức toán học nhất theo quy tắc của kiểu. Thứ tự cộng các số thực có thể thay đổi kết quả: do làm tròn sau mỗi bước, tính toán dấu phẩy động nói chung không có tính kết hợp như số thực toán học.

### 3.2. Giá trị đặc biệt và cách so sánh

Floating point có `NaN` (không phải số), `POSITIVE_INFINITY`, `NEGATIVE_INFINITY`, `+0.0` và `-0.0`:

- `1.0 / 0.0` cho `Infinity`, `0.0 / 0.0` cho `NaN`; khác chia nguyên cho 0.
- `NaN == NaN` là `false`. Kiểm tra bằng `Double.isNaN(value)` hoặc `Float.isNaN(value)`; kiểm tra giá trị hữu hạn bằng `Double.isFinite(value)`.
- `-0.0 == 0.0` là `true`, nhưng một số phép toán và `Double.compare(-0.0, 0.0)` phân biệt được dấu (kết quả `Double.compare` là số âm).
- `Double.compare` tạo thứ tự tổng để phục vụ sắp xếp theo quy tắc Java, nhưng không thay thế tiêu chí so gần đúng trong tính toán khoa học.

Tránh kiểm tra hai kết quả phép tính dấu phẩy động bằng `==` khi ý nghĩa bài toán là “xấp xỉ bằng nhau”. Một cách cơ bản là kiểm tra `Math.abs(a - b) <= tolerance`, với ngưỡng tuyệt đối phù hợp **đơn vị và quy mô**. Với miền giá trị rộng, cần kết hợp ngưỡng tuyệt đối và tương đối; phải định nghĩa cách xử lý `NaN`, vô cực, giá trị rất gần 0 và nguy cơ tràn khi trừ. Không có một `epsilon` cố định dùng đúng cho mọi chương trình.

### 3.3. Làm tròn và in kết quả

Hàm `Math.round`, `Math.floor`, `Math.ceil` có quy tắc riêng và không biến `double` thành số thập phân chính xác. `System.out.printf` hoặc `String.format` có thể **định dạng cách hiển thị**, nhưng phần lưu trữ vẫn là nhị phân:

```java
System.out.printf(java.util.Locale.US, "%.2f%n", 1.235);
```

Mảnh lệnh trên hiển thị hai chữ số sau dấu thập phân theo quy tắc định dạng số thực; tránh suy luận rằng chuỗi hiển thị là bằng chứng phép tính tài chính đã được thực hiện bằng thập phân chính xác. Locale ảnh hưởng ký hiệu dấu thập phân và dấu nhóm khi định dạng; dữ liệu nội bộ và dữ liệu trao đổi nên có quy ước rõ ràng.

## 4. BigDecimal: giá trị thập phân chính xác cùng quy tắc scale

### 4.1. Mô hình giá trị

`BigDecimal` biểu diễn số hữu hạn dạng **unscaledValue × 10^(-scale)**: hệ số nguyên không giới hạn cố định và `scale` là số nguyên 32 bit. Ví dụ `2.50` có hệ số 250 và scale 2. Khác `double`, chuỗi thập phân `"0.1"` có thể được lưu chính xác.

Tạo `BigDecimal` từ **chuỗi**, hoặc dùng `BigDecimal.valueOf(double)` nếu đầu vào đang là `double` và bạn chủ ý lấy biểu diễn thập phân chuẩn của chính giá trị đó:

```java
BigDecimal precise = new BigDecimal("0.1");
BigDecimal fromDouble = BigDecimal.valueOf(0.1);
BigDecimal exposedBinary = new BigDecimal(0.1);
```

`precise` và `fromDouble` đều có giá trị thập phân `0.1` trong ví dụ này. `exposedBinary` mang giá trị thập phân chính xác của số nhị phân `double` gần 0.1, thường là một chuỗi dài; tránh `new BigDecimal(0.1)` khi kỳ vọng số tiền 0.1. `BigDecimal.valueOf(0.1 + 0.2)` không thể khôi phục ý định `0.3` sau khi phép cộng `double` đã làm tròn thành `0.30000000000000004`.

Có thể sử dụng `BigDecimal.ZERO`, `ONE`, `TEN`, hoặc `BigDecimal.valueOf(long)`. Không dùng toán tử `+`, `-`, `*`, `/` trực tiếp giữa các `BigDecimal`; dùng phương thức và nhận giá trị mới.

### 4.2. Bốn phép toán và bài toán phép chia

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal sum = a.add(b);                       // 0.3
BigDecimal difference = b.subtract(a);          // 0.1
BigDecimal product = a.multiply(b);             // 0.02
BigDecimal quotient = BigDecimal.ONE.divide(
        new BigDecimal("8"));                   // 0.125, kết thúc hữu hạn
```

Đây là mảnh mã; thêm `import java.math.BigDecimal;` và đặt trong phương thức khi chạy. Các phép cộng, trừ, nhân có thể cho kết quả thập phân chính xác (giới hạn tài nguyên máy). Tuy nhiên **1/3 không có biểu diễn thập phân hữu hạn**. `BigDecimal.ONE.divide(new BigDecimal("3"))` không có chế độ làm tròn sẽ ném `ArithmeticException` vì yêu cầu kết quả chính xác.

Khi kết quả không kết thúc, quy định số chữ số sau dấu thập phân và chế độ làm tròn:

```java
BigDecimal third = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP); // 0.33
```

Đoạn này cần `import java.math.RoundingMode;`. Không tự mặc định `HALF_UP` là “đúng về tài chính”: quy tắc có thể là `HALF_EVEN` (làm tròn điểm giữa về chữ số chẵn), `DOWN`, `UP`, `CEILING`, `FLOOR`, v.v., tùy hợp đồng hoặc chuẩn nghiệp vụ. Chọn đúng cả dấu, trường hợp nửa chừng và nơi áp dụng làm tròn.

### 4.3. Scale khác precision

- **Scale**: số chữ số ở bên phải dấu thập phân theo biểu diễn (`12.340` có scale 3); scale có thể âm.
- **Precision**: số chữ số của hệ số nguyên khi tính theo `BigDecimal.precision()` (`12.340` có precision 5).
- `setScale(2, mode)`: đặt số vị trí thập phân của kết quả; nếu phải bỏ chữ số, cần `RoundingMode`.
- `MathContext(precision, mode)`: giới hạn số **chữ số có nghĩa** khi thực hiện phép toán với context; không đồng nghĩa luôn lấy từng ấy chữ số sau dấu thập phân.

```java
BigDecimal price = new BigDecimal("12.345");
BigDecimal cents = price.setScale(2, RoundingMode.HALF_EVEN); // 12.34
BigDecimal significant = price.round(
        new MathContext(3, RoundingMode.HALF_EVEN));          // 12.3
```

Mảnh này cần `import java.math.MathContext;` và `import java.math.RoundingMode;`. `BigDecimal` bất biến: `price.setScale(2, ...)` không sửa `price`; phải gán kết quả. Việc làm tròn từng dòng hóa đơn trước khi cộng và làm tròn một lần ở tổng có thể khác nhau; quy tắc nghiệp vụ cần xác định rõ.

### 4.4. equals, compareTo và hiển thị

`new BigDecimal("2.0").equals(new BigDecimal("2.00"))` trả `false` vì `equals` xét cả **giá trị và scale**. `compareTo` trả 0 vì hai số có cùng giá trị toán học. Vì thế kiểm tra bằng giá trị nên dùng `compareTo(...) == 0` khi scale không mang ý nghĩa:

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = new BigDecimal("2.00");
boolean sameRepresentation = x.equals(y);   // false
boolean sameNumericValue = x.compareTo(y) == 0; // true
```

Tương ứng, `HashSet<BigDecimal>` dùng `equals`/`hashCode`, còn cấu trúc sắp xếp như `TreeSet<BigDecimal>` thường xét `compareTo`; do đó hai chuỗi biểu diễn khác scale có thể được xử lý khác nhau. `stripTrailingZeros()` có thể đổi scale (kể cả sang scale âm). Dùng `toPlainString()` khi muốn chuỗi thập phân thông thường và tránh cách in ký hiệu khoa học ở những giá trị có thể biểu diễn kiểu đó; vẫn cần quy định scale của dữ liệu xuất ra.

`BigDecimal` không mang `NaN` hay vô cực; nếu nhận `double` không hữu hạn để chuyển sang `BigDecimal`, phải kiểm tra và xử lý trước.

## 5. Chương trình Java 17+ chạy độc lập

Lưu đoạn sau thành **`NumericDemo.java`**. Ví dụ này chọn những phép tính có kết quả và cách in xác định, để thấy sự khác nhau giữa integer, floating point, `BigInteger` và `BigDecimal`.

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class NumericDemo {
    public static void main(String[] args) {
        System.out.println("int-division=" + (7 / 2));
        System.out.println("real-division=" + (7 / 2.0));

        System.out.println("int-overflow=" + (Integer.MAX_VALUE + 1));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("checked-overflow=detected");
        }

        System.out.println("floating-sum=" + (0.1 + 0.2));
        System.out.println("floating-equal=" + ((0.1 + 0.2) == 0.3));
        System.out.println("nan=" + Double.isNaN(0.0 / 0.0));

        BigDecimal oneTenth = new BigDecimal("0.1");
        BigDecimal twoTenths = new BigDecimal("0.2");
        System.out.println("decimal-sum=" + oneTenth.add(twoTenths));
        System.out.println("third=" + BigDecimal.ONE.divide(
                new BigDecimal("3"), 2, RoundingMode.HALF_UP));

        BigDecimal first = new BigDecimal("2.0");
        BigDecimal second = new BigDecimal("2.00");
        System.out.println("scale-equals=" + first.equals(second));
        System.out.println("value-equal=" + (first.compareTo(second) == 0));

        BigInteger beyondLong = new BigInteger("9223372036854775808");
        System.out.println("big-integer=" + beyondLong.add(BigInteger.ONE));
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac NumericDemo.java
java NumericDemo
```

Kết quả stdout:

```text
int-division=3
real-division=3.5
int-overflow=-2147483648
checked-overflow=detected
floating-sum=0.30000000000000004
floating-equal=false
nan=true
decimal-sum=0.3
third=0.33
scale-equals=false
value-equal=true
big-integer=9223372036854775809
```

Giải thích: `7 / 2` mất phần lẻ theo chia nguyên, còn `7 / 2.0` dùng `double`; cộng `int` tràn thì quay vòng trong khi `Math.addExact` phát hiện; `0.1 + 0.2` làm tròn nhị phân; `BigDecimal` tạo từ chuỗi cộng ra đúng `0.3`. Hai biểu diễn `2.0` và `2.00` có cùng giá trị toán học nhưng khác scale. `BigInteger` xử lý giá trị lớn hơn `Long.MAX_VALUE`.

## 6. Quy tắc chuyển đổi và lỗi phổ biến

| Tình huống | Điều gì có thể xảy ra? | Cách xử lý |
| --- | --- | --- |
| `int` cộng/nhân vượt giới hạn | Tràn âm thầm | Phân tích miền dữ liệu; dùng `long`, `BigInteger` hoặc hàm `Math.*Exact`. |
| Nhân hai `int` rồi gán `long` | Đã tràn ở bước nhân | Nâng ít nhất một toán hạng lên `long` **trước** phép tính. |
| `int / int` rồi gán `double` | Phần lẻ đã bị bỏ | Dùng `(double) numerator / denominator` nếu muốn chia thực. |
| Lưu tiền vào `double` | Sai số biểu diễn hoặc phép tính | Tạo `BigDecimal` từ chuỗi/thông tin thập phân gốc; hoặc lưu đơn vị tiền nhỏ nhất dạng nguyên. |
| `new BigDecimal(someDouble)` | Bộc lộ giá trị nhị phân gần đúng | Nhận chuỗi thập phân gốc; `valueOf` khi chủ ý chuyển theo `Double.toString`. |
| `BigDecimal.divide` không quy định làm tròn | Có thể ném `ArithmeticException` với kết quả vô hạn tuần hoàn | Quy định scale hoặc `MathContext` và `RoundingMode`. |
| So sánh `BigDecimal.equals` | `2.0` khác `2.00` | Dùng `compareTo == 0` để so giá trị không phụ thuộc scale. |
| Không nhận kết quả của `add`/`setScale` | Đối tượng ban đầu không đổi | Gán hoặc trả về đối tượng mới. |
| Ép `double` sang `int`/`long` | Bỏ phần lẻ, NaN thành 0, giá trị ngoài miền bị chặn về biên theo quy tắc chuyển kiểu | Kiểm tra hữu hạn, miền giá trị và quy tắc làm tròn trước khi chuyển. |
| Gọi `Double.parseDouble` hoặc `new BigDecimal(text)` trên dữ liệu lỗi | `NumberFormatException` | Kiểm tra đầu vào; thống nhất quy ước thập phân và locale ở lớp giao tiếp. |

**Về conversion:** chuyển `int` sang `double` thường chính xác cho mọi giá trị `int`, nhưng không phải mọi `long` đều biểu diễn được chính xác dưới dạng `double` (độ chính xác phần trị hữu hạn). Chuyển `BigDecimal` sang `double` qua `doubleValue()` cũng có thể mất chữ số hoặc vượt miền. Không đem phép định dạng chuỗi làm bước thay thế cho phép tính chính xác.

**Về dữ liệu tiền tệ:** ví dụ số tiền `"19.99"` có thể tạo bằng `new BigDecimal("19.99")`. Khi nhân với số lượng nguyên, dùng `price.multiply(BigDecimal.valueOf(quantity))`. Ứng dụng cần định nghĩa đồng tiền/đơn vị, scale hợp lệ, chế độ làm tròn khi thuế/chiết khấu/phân bổ, cách xử lý giá trị âm và so khớp tổng các dòng. Một `BigDecimal` đơn lẻ không chứa mã tiền tệ và không tự chọn quy tắc kế toán.

## 7. Tự kiểm tra kiến thức

1. Vì sao `long total = 2_000_000_000 * 2;` có thể cho kết quả sai, còn `long total = 2_000_000_000L * 2;` thì không tràn tại phép nhân này? Hãy chỉ ra **kiểu của toán hạng trước khi phép nhân xảy ra**.
2. Dự đoán và xác minh `-7 / 3`, `-7 % 3`, `Math.floorDiv(-7, 3)` và `Math.floorMod(-7, 3)`.
3. Vì sao `0.1 + 0.2 == 0.3` là `false`, nhưng `new BigDecimal("0.1").add(new BigDecimal("0.2"))` có giá trị đúng `0.3`?
4. Viết phép chia `1 / 3` với `BigDecimal` lấy bốn chữ số sau dấu thập phân, chỉ rõ chế độ làm tròn và giải thích tại sao `divide` không có quy tắc làm tròn có thể ném lỗi.
5. So sánh `new BigDecimal("10.0")` và `new BigDecimal("10.00")` bằng `equals` và `compareTo`. Trong chương trình dùng `HashSet` và `TreeSet`, khác biệt đó ảnh hưởng thế nào?
6. Thiết kế phép tính tổng hóa đơn có đơn giá thập phân, số lượng, giảm giá và thuế. Nêu thời điểm làm tròn, scale, đơn vị tiền và kiểm thử tình huống nửa chừng; không sử dụng `double` để tính số tiền.

## 8. Điểm cần ghi nhớ

- **Số nguyên** chính xác trong miền kiểu, nhưng tràn số cần được chủ động kiểm soát; `BigInteger` dành cho số nguyên vượt `long`.
- **`float`/`double`** biểu diễn số thực nhị phân gần đúng; phù hợp bài toán có sai số chấp nhận được, cần chính sách so gần đúng và xử lý `NaN`/vô cực.
- **`BigDecimal`** biểu diễn thập phân hữu hạn chính xác khi khởi tạo đúng; phép chia và ràng buộc nghiệp vụ vẫn đòi hỏi chính sách precision, scale và rounding.
- Kiểu dữ liệu đúng được quyết định từ miền giá trị, đơn vị, độ chính xác yêu cầu và quy tắc của bài toán, không chỉ từ việc chương trình có biên dịch thành công hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Phạm vi giá trị khác độ chính xác như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi giá trị khác độ chính xác như thế nào trong Java?

Hai khái niệm trả lời **hai câu hỏi khác nhau**:

- **Phạm vi giá trị (range):** Kiểu dữ liệu có thể biểu diễn các giá trị nhỏ nhất, lớn nhất nào? Một phép tính có vượt khỏi miền biểu diễn hay không?
- **Độ chính xác (precision):** Trong phạm vi đó, kiểu dữ liệu phân biệt được các giá trị sát nhau đến mức nào? Có bao nhiêu chữ số có nghĩa được giữ lại khi lưu và tính toán?

Một kiểu có phạm vi rất rộng vẫn có thể có độ chính xác hữu hạn. Đặc biệt, `double` biểu diễn được các số lớn hơn `int` rất nhiều, nhưng **không biểu diễn chính xác mọi số nguyên hoặc mọi số thập phân** trong phạm vi ấy.

## 1. Đối với số nguyên: giới hạn miền giá trị, biểu diễn chính xác trong miền

Các kiểu số nguyên nguyên thủy của Java sử dụng số bit cố định:

| Kiểu | Số bit | Phạm vi giá trị |
| --- | ---: | --- |
| `byte` | 8 | -128 đến 127 |
| `short` | 16 | -32.768 đến 32.767 |
| `int` | 32 | -2.147.483.648 đến 2.147.483.647 |
| `long` | 64 | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 |

- Mỗi giá trị **nguyên** nằm trong phạm vi của kiểu đều được lưu chính xác: `int` biểu diễn được `1_000_000` và `1_000_001` thành hai giá trị khác nhau.
- Giới hạn ở đây là **phạm vi**, không phải hiện tượng làm tròn một giá trị còn nằm trong miền. Kiểu `int` không chứa được một số nguyên lớn hơn `Integer.MAX_VALUE`.
- Phép cộng, trừ, nhân số nguyên nguyên thủy có thể **tràn số (overflow)**: kết quả tính theo các bit của kiểu và có thể đổi dấu. Java không tự động mở rộng từ `int` sang `long` để giữ kết quả; muốn tính với `long`, hãy chuyển toán hạng trước khi phép tính diễn ra.
- Dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact` hoặc `Math.toIntExact` để phát hiện các trường hợp tràn số phù hợp. Với số nguyên vượt 64 bit, cân nhắc `BigInteger`.

**Ví dụ:** `Integer.MAX_VALUE + 1` cho `-2_147_483_648` do tràn `int`. Đây không phải lỗi làm tròn vì thiếu chữ số thập phân: kết quả toán học đã vượt phạm vi `int`.

> `char` cũng chứa 16 bit không dấu (từ `'\u0000'` đến `'\uffff'`), nhưng là một **đơn vị mã UTF-16** và không nên coi là kiểu số nguyên dùng cho tính toán thông thường.

## 2. Đối với số thực: phạm vi rộng nhưng độ chính xác hữu hạn

`float` (32 bit) và `double` (64 bit) sử dụng biểu diễn dấu phẩy động nhị phân theo IEEE 754. Số bit được phân bổ cho **dấu**, **số mũ** và **phần trị (significand)**:

- **Số mũ** giúp biểu diễn các giá trị có độ lớn rất khác nhau; đây là một lý do phạm vi của số thực rộng.
- **Phần trị** chứa hữu hạn bit nên chỉ biểu diễn được một tập giá trị rời rạc. Những số nằm giữa hai giá trị biểu diễn được thường phải **làm tròn** sang một trong hai.
- Khoảng cách giữa hai số thực biểu diễn được kề nhau (thường gọi là **ULP**, *unit in the last place*) **thay đổi theo độ lớn**. Gần giá trị lớn, khoảng cách lớn hơn; gần 0, khoảng cách thường nhỏ hơn. Ở vùng số *subnormal*, độ chính xác tương đối giảm.

| Thuộc tính | `float` | `double` |
| --- | --- | --- |
| Số bit toàn bộ | 32 | 64 |
| Số bit phần trị hiệu dụng của số chuẩn hóa | 24 | 53 |
| Độ chính xác thập phân thường dùng để ước lượng | khoảng 7 chữ số có nghĩa | khoảng 15–16 chữ số có nghĩa |
| Trị tuyệt đối hữu hạn lớn nhất | khoảng `3.4028235E38` | khoảng `1.7976931348623157E308` |
| Số dương khác 0 nhỏ nhất (*subnormal*) | khoảng `1.4E-45` | khoảng `4.9E-324` |
| Mọi số nguyên liên tiếp đều chính xác đến | `2^24 = 16_777_216` | `2^53 = 9_007_199_254_740_992` |

Các mốc `2^24` và `2^53` nói rằng **mọi số nguyên trong đoạn tương ứng đều biểu diễn chính xác**; chúng không có nghĩa mọi số nguyên lớn hơn đều sai. Chẳng hạn, các số chẵn đủ gần `2^24` vẫn có thể được lưu chính xác bằng `float`, nhưng số nguyên liền kề ở giữa có thể không.

**Phân biệt “chữ số có nghĩa” và “chữ số sau dấu phẩy”:** Với `double`, độ chính xác xấp xỉ 15–16 **chữ số có nghĩa cho cả số**, không phải luôn có 15–16 chữ số *sau dấu phẩy*. Số `1_000_000_000_000_000` dùng nhiều chữ số có nghĩa hơn số `1.0`; khoảng cách tới số `double` kế tiếp phụ thuộc bậc độ lớn.

Lưu ý về các hằng Java:

- `Float.MAX_VALUE` / `Double.MAX_VALUE`: số **dương hữu hạn lớn nhất**; số âm hữu hạn nhỏ nhất theo thứ tự số học là `-Float.MAX_VALUE` / `-Double.MAX_VALUE`.
- `Float.MIN_VALUE` / `Double.MIN_VALUE`: số **dương khác 0 nhỏ nhất**, **không** phải số âm nhỏ nhất. `MIN_NORMAL` là số dương *chuẩn hóa* nhỏ nhất.
- `Float.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` và `NaN` là các giá trị đặc biệt. Chúng không phải số hữu hạn nằm giữa `-MAX_VALUE` và `MAX_VALUE`.

### Vì sao một `double` rất lớn lại có thể “bỏ qua” `+1`?

`double` chứa 53 bit độ chính xác phần trị, nhưng phải biểu diễn cả những số có bậc độ lớn đến khoảng `10^308`. Gần `1.0E16`, hai số `double` kề nhau cách nhau **2**; vì thế `1.0E16 + 1` được làm tròn trở lại `1.0E16`. Trong khi đó, gần `1.0`, `Math.ulp(1.0)` chỉ khoảng `2.22E-16`.

Đây là **mất độ phân giải**, không phải tràn phạm vi. Cả `1.0E16` và `1.0E16 + 1` đều rất nhỏ so với `Double.MAX_VALUE`, nhưng `double` không giữ được hai giá trị ấy như hai số phân biệt.

### Vì sao `0.1 + 0.2` không bằng chính xác `0.3`?

Phần lớn phân số thập phân, gồm `0.1`, không có biểu diễn nhị phân hữu hạn. Ngay từ khi lưu literal `0.1` hoặc `0.2` vào `double`, giá trị thực tế đã là một số nhị phân gần đúng. Sau phép cộng, kết quả được làm tròn thêm nên phép so sánh bằng chính xác:

`0.1 + 0.2 == 0.3`

trả về `false`. **Phạm vi số thực không phải vấn đề ở đây**: cả ba số đều rất nhỏ và nằm trong miền biểu diễn. Vấn đề là **độ chính xác biểu diễn và làm tròn**.

## 3. Chạy thử: quan sát range, overflow, precision và decimal

Tạo tệp `RangePrecisionDemo.java` (Java 17 trở lên):

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class RangePrecisionDemo {
    public static void main(String[] args) {
        System.out.println("byte: " + Byte.MIN_VALUE + " .. " + Byte.MAX_VALUE);
        System.out.println("int: " + Integer.MIN_VALUE + " .. " + Integer.MAX_VALUE);
        System.out.println("int overflow: " + (Integer.MAX_VALUE + 1));
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            System.out.println("addExact: overflow detected");
        }

        float f = 16_777_216f;
        System.out.println("float keeps +1? " + (f + 1 == f));
        double d = 1e16;
        System.out.println("double keeps +1? " + (d + 1 == d));
        System.out.println("0.1 + 0.2 == 0.3? " + (0.1 + 0.2 == 0.3));
        System.out.println("ulp(1.0): " + Math.ulp(1.0));
        System.out.println("double infinity: " + Double.isInfinite(1e308 * 10));

        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("BigDecimal sum: " + a.add(b));
        BigDecimal x = new BigDecimal("123.4500");
        System.out.println("precision=" + x.precision() + ", scale=" + x.scale());
        BigDecimal thirds = BigDecimal.ONE.divide(
            new BigDecimal("3"), new MathContext(5, RoundingMode.HALF_UP));
        System.out.println("1 / 3 (5 digits): " + thirds);
    }
}
```

Biên dịch và chạy:

```bash
javac RangePrecisionDemo.java
java RangePrecisionDemo
```

Đầu ra đã kiểm tra với JDK 21 (chương trình chỉ dùng API có từ Java 17):

```text
byte: -128 .. 127
int: -2147483648 .. 2147483647
int overflow: -2147483648
addExact: overflow detected
float keeps +1? true
double keeps +1? true
0.1 + 0.2 == 0.3? false
ulp(1.0): 2.220446049250313E-16
double infinity: true
BigDecimal sum: 0.3
precision=7, scale=4
1 / 3 (5 digits): 0.33333
```

Cách đọc kết quả:

1. `int overflow`: vượt **phạm vi**; `Math.addExact` phát hiện bằng ngoại lệ thay vì cho kết quả quấn vòng.
2. `float keeps +1? true`: `16_777_216f + 1` được làm tròn về `16_777_216f`; `float` chưa vượt phạm vi, nhưng không giữ được bước tăng bằng 1.
3. `double keeps +1? true`: điều tương tự xảy ra với `double` ở độ lớn `10^16`.
4. `0.1 + 0.2 == 0.3? false`: phép tính thập phân nhìn đơn giản nhưng các toán hạng ở dạng nhị phân gần đúng.
5. `Math.ulp(1.0)` đo khoảng cách tới số `double` kề tiếp ở gần `1.0`.
6. `double infinity: true`: phép nhân vượt số hữu hạn lớn nhất dẫn đến `+Infinity`; đây là **overflow số thực**, khác với làm tròn mất `+1`.
7. `BigDecimal` tính tổng số thập phân đầu vào chính xác. Nhưng phép chia `1 / 3` không có khai triển thập phân hữu hạn, vì vậy phải chủ động chọn độ chính xác và quy tắc làm tròn.

## 4. Precision, scale và rounding của BigDecimal có giống float/double không?

`BigDecimal` lưu một **số nguyên không chia tỷ lệ** cùng với `scale` (số chữ số thập phân theo cách biểu diễn cơ số 10). Nó không chịu độ chính xác nhị phân cố định 24/53 bit như `float` và `double`:

- `new BigDecimal("123.4500").precision()` = `7`: có bảy chữ số trong số nguyên không chia tỷ lệ `1234500`.
- `new BigDecimal("123.4500").scale()` = `4`: có bốn vị trí sau dấu phẩy theo biểu diễn đang giữ.
- `precision` **khác** `scale`. Một giá trị có thể có scale lớn mà không có nhiều chữ số có nghĩa, hoặc ngược lại.
- Không truyền `MathContext` thì phép cộng/trừ/nhân `BigDecimal` thường cho kết quả thập phân chính xác, với giới hạn thực tế về bộ nhớ và độ lớn scale. Chia một số cho một số khác mà có phần thập phân vô hạn, như `1 / 3`, cần chỉ rõ `MathContext` hoặc scale và `RoundingMode`; nếu dùng `divide` đòi hỏi kết quả chính xác mà không thể kết thúc, Java ném `ArithmeticException`.
- Khi cần làm tròn để hiển thị tiền có hai chữ số sau dấu phẩy, có thể dùng `amount.setScale(2, RoundingMode.HALF_UP)` (chỉ là một chính sách làm tròn; quy định nghiệp vụ có thể yêu cầu cách khác).
- `new BigDecimal("0.1")` giữ đúng số thập phân `0.1`. `new BigDecimal(0.1)` lại nhận **giá trị double nhị phân gần đúng**; vì vậy nên khởi tạo từ `String` hoặc một dạng nhập thập phân tin cậy. `BigDecimal.valueOf(0.1)` sử dụng biểu diễn chuỗi của `double`, nhưng không thể khôi phục thông tin đã mất nếu dữ liệu bị làm tròn từ trước.

`BigDecimal.equals` còn xét cả scale: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false`, trong khi `compareTo` cho kết quả `0` vì hai giá trị bằng nhau về mặt số học. Đây là vấn đề **ngữ nghĩa biểu diễn và so sánh**, không phải khác phạm vi.

`BigInteger` giải quyết bài toán **phạm vi số nguyên** khi `long` không đủ; `BigDecimal` giải quyết bài toán **biểu diễn thập phân và kiểm soát làm tròn**. Cả hai vẫn chịu giới hạn tài nguyên thực tế.

## 5. Chọn kiểu dữ liệu đúng theo yêu cầu

| Nhu cầu | Gợi ý | Lý do |
| --- | --- | --- |
| Đếm, chỉ số, số lượng nằm trong miền `int` | `int` | Mọi số nguyên hợp lệ đều chính xác, chi phí gọn |
| Số nguyên lớn hơn miền `int` nhưng vừa `long` | `long` | Mở rộng phạm vi; nhớ tính bằng toán hạng `long` trước khi tràn |
| Số nguyên lớn hơn cả `long` | `BigInteger` | Không có giới hạn cố định 64 bit |
| Đo lường, mô phỏng, tính toán khoa học chấp nhận sai số | Thường là `double` | Phạm vi rộng và độ chính xác nhị phân cao hơn `float` |
| Tối ưu bộ nhớ cho dữ liệu thực lớn khi sai số chấp nhận được | `float` sau khi đánh giá sai số | 32 bit nhưng độ chính xác thấp hơn `double` |
| Tiền tệ, thuế, giá và quy tắc làm tròn thập phân | `BigDecimal`, với scale/rounding được quy định | Tránh sai lệch do biểu diễn nhị phân của `0.1`, `0.2` |
| Lưu tiền ở đơn vị nhỏ nhất, ví dụ số xu, khi miền giá trị cho phép | `long` hoặc `int` | Số nguyên chính xác; phải quy định đơn vị và xử lý chuyển đổi |

Khi xử lý `double`/`float`, tránh so sánh `==` nếu ý định là “gần bằng về mặt toán học” sau tính toán. Có thể dùng sai số tuyệt đối và/hoặc tương đối theo miền giá trị; ngưỡng sai số phải xuất phát từ yêu cầu bài toán. `Math.ulp(value)` hữu ích khi muốn hiểu độ phân giải của biểu diễn tại một giá trị nhất định. Ngoại lệ: so sánh chính xác vẫn hợp lệ khi đó là đúng ngữ nghĩa mong muốn (ví dụ kiểm tra một giá trị đã được gán trực tiếp hoặc kiểm tra cờ trạng thái đặc biệt).

## 6. Các nhầm lẫn thường gặp

1. **“`double` có phạm vi lớn hơn `long` nên lưu chính xác mọi `long`.”** Sai: từ `2^53 + 1` trở đi, không phải mọi số nguyên kề nhau đều phân biệt được bằng `double`. Ép một `long` lớn sang `double` có thể làm mất chữ số dù không overflow.
2. **“`double` chính xác đến 16 chữ số thập phân sau dấu phẩy.”** Sai: thường nói đến khoảng 15–16 **chữ số có nghĩa**, và độ phân giải phụ thuộc độ lớn.
3. **“`Double.MIN_VALUE` là số âm nhất.”** Sai: nó là số dương khác 0 nhỏ nhất; số âm hữu hạn thấp nhất là `-Double.MAX_VALUE`.
4. **“Tính `0.1 + 0.2` sai do vượt phạm vi.”** Sai: đó là sai số biểu diễn/làm tròn trong miền; tràn số thực mới có thể tạo `Infinity`.
5. **“Ép kiểu sau phép tính sẽ tránh overflow.”** Sai: `long result = (long) (Integer.MAX_VALUE + 1);` vẫn overflow ở phép cộng `int` trước khi ép. Cần `(long) Integer.MAX_VALUE + 1` hoặc `1L`.
6. **“Thêm nhiều chữ số vào literal `double` sẽ làm nó chính xác tùy ý.”** Sai: một `double` chỉ giữ số bit phần trị cố định; cú pháp literal không mở rộng precision.
7. **“`BigDecimal` luôn chia chính xác và không cần làm tròn.”** Sai đối với thương thập phân vô hạn, chẳng hạn `1 / 3`.
8. **“`1 / 3` ra `0` vì `int` thiếu precision.”** Cần phân biệt: Java dùng **phép chia số nguyên** khi cả hai toán hạng là số nguyên, nên phần dư bị bỏ theo quy tắc phép toán. Viết `1.0 / 3` để thực hiện phép chia số thực, nhưng kết quả vẫn là xấp xỉ nhị phân.

## 7. Tự kiểm tra

- Nếu muốn lưu mọi số nguyên từ `0` đến `1_000_000_000` một cách chính xác, `int` có đủ **phạm vi** không? **Có**, và các giá trị hợp lệ đều biểu diễn chính xác.
- `double` có thể lưu giá trị khoảng `10^100` không? **Có**, vì nằm trong phạm vi hữu hạn; nhưng không có nghĩa giữ chính xác mọi chữ số của giá trị đó.
- Vì sao `16_777_216f + 1 == 16_777_216f`? **Vì độ phân giải của `float` ở vùng đó không còn phân biệt bước tăng 1**, dù chưa vượt phạm vi.
- Khi cần tính `0.1 + 0.2` chính xác bằng thập phân trong nghiệp vụ tiền, nên dùng gì? **`BigDecimal` khởi tạo từ chuỗi thập phân**, cùng chính sách làm tròn rõ ràng khi cần.

**Ghi nhớ:** *Phạm vi* nói về **giá trị xa đến đâu**; *độ chính xác* nói về **các giá trị gần nhau phân biệt được đến mức nào**. Với số nguyên Java, mọi giá trị trong miền đều chính xác; với số dấu phẩy động, miền có thể cực rộng nhưng độ phân giải hữu hạn và thay đổi theo độ lớn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Bản đồ primitive, wrapper, Number, BigInteger và BigDecimal</a>

<details>
<summary>Xem chi tiết</summary>

# Bản đồ primitive, wrapper, Number, BigInteger và BigDecimal trong Java

> **Chủ đề:** Java core – numbers · **Nhóm:** Tổng quan  
> **Mục tiêu:** Nhận diện các họ kiểu số trong Java, hiểu quan hệ giữa chúng, chọn đúng kiểu lưu trữ/tính toán và tránh lỗi chuyển đổi, tràn số, sai số thập phân.

## 1. Bức tranh tổng thể

Java có hai cách biểu diễn số thường gặp: **primitive** lưu một giá trị theo kiểu nguyên thủy; **object** biểu diễn số qua lớp wrapper hoặc các lớp số trong thư viện. Không có một “kiểu Number” chung bao trùm cả primitive lẫn object.

```text
Giá trị số trong Java
├── Primitive (không kế thừa Object/Number)
│   ├── Số nguyên có dấu: byte, short, int, long
│   ├── Số thực dấu phẩy động: float, double
│   └── char: mã UTF-16 16 bit không dấu, dùng được trong vài phép số học
└── Reference / object
    ├── java.lang.Number (lớp trừu tượng)
    │   ├── Byte, Short, Integer, Long
    │   ├── Float, Double
    │   └── java.math.BigInteger, java.math.BigDecimal
    └── Character (wrapper của char; KHÔNG extends Number)

boolean / Boolean: kiểu logic, không phải kiểu số.
```

Cây trên là **bản đồ khái niệm**, không phải cây thừa kế từ primitive sang wrapper. Primitive `int` không phải lớp con của `Integer`; `Integer` mới là một đối tượng thuộc nhánh `Number`. `Character` và `Boolean` là wrapper nhưng không thuộc nhánh `Number`. Một số lớp chuyên dụng khác cũng kế thừa `Number`, nhưng không cần thiết để hiểu bộ kiểu số nền tảng này.

## 2. Primitive: các kiểu nền tảng và miền giá trị

| Kiểu | Kích thước | Ý nghĩa và phạm vi đáng nhớ | Ví dụ |
| --- | --- | --- | --- |
| `byte` | 8 bit | Số nguyên có dấu: −128 đến 127 | `byte b = 100;` |
| `short` | 16 bit | −32.768 đến 32.767 | `short s = 32000;` |
| `int` | 32 bit | −2³¹ đến 2³¹ − 1 | `int n = 1_000_000;` |
| `long` | 64 bit | −2⁶³ đến 2⁶³ − 1 | `long n = 3_000_000_000L;` |
| `float` | 32 bit | IEEE 754 nhị phân; thường khoảng 6–7 chữ số thập phân có nghĩa | `float f = 1.5f;` |
| `double` | 64 bit | IEEE 754 nhị phân; thường khoảng 15–16 chữ số thập phân có nghĩa | `double d = 1.5;` |
| `char` | 16 bit | Mã đơn vị UTF-16 không dấu: 0 đến 65.535; chủ yếu biểu diễn ký tự | `char c = 'A';` |

**Phân biệt phạm vi và độ chính xác:** `float`/`double` biểu diễn được những số có trị tuyệt đối rất lớn/nhỏ nhưng không biểu diễn chính xác mọi số thập phân. “15–16 chữ số” là ước lượng độ chính xác, không phải cam kết rằng một giá trị thập phân bất kỳ sẽ được giữ nguyên.

- Phép cộng, trừ, nhân trên `byte`, `short`, `char` thường được nâng lên `int` trước khi cho kết quả; ví dụ `byte x = 1, y = 2; int z = x + y;`. Muốn gán lại `byte` cần chuyển kiểu tường minh và tự chịu rủi ro mất dữ liệu.
- Một biểu thức `int * int` được tính bằng `int`, dù biến nhận kết quả là `long`. Phải mở rộng ít nhất một toán hạng **trước phép nhân**.
- Dùng chữ `L` (nên viết hoa) cho literal `long`; `f` hoặc `F` cho literal `float`. Dấu gạch dưới trong literal như `1_000_000` chỉ giúp đọc dễ hơn.
- `boolean` không tham gia tính toán số học; `char` có thể nâng lên `int`, nhưng không phải lựa chọn phù hợp để lưu đại lượng số thông thường.

**Tràn số nguyên:** khi phép tính primitive nguyên vượt miền kiểu, kết quả có thể quay vòng theo biểu diễn bit, không tự báo lỗi. `Math.addExact`, `Math.multiplyExact` và các hàm `*Exact` liên quan phát hiện tràn và ném `ArithmeticException`. Chúng không mở rộng miền giá trị; nếu cần vượt miền `long`, dùng `BigInteger`.

## 3. Wrapper: đối tượng tương ứng primitive

| Primitive | Wrapper | Kế thừa `Number`? |
| --- | --- | --- |
| `byte` | `Byte` | Có |
| `short` | `Short` | Có |
| `int` | `Integer` | Có |
| `long` | `Long` | Có |
| `float` | `Float` | Có |
| `double` | `Double` | Có |
| `char` | `Character` | Không |
| `boolean` | `Boolean` | Không |

Wrapper là các lớp **bất biến (immutable)**: không sửa được giá trị bên trong đối tượng sau khi tạo. Giá trị wrapper có thể là `null` vì đó là tham chiếu; primitive không thể là `null`.

Java hỗ trợ:

- **Boxing:** `int` → `Integer`; ví dụ `Integer a = 42;`.
- **Unboxing:** `Integer` → `int`; ví dụ `int b = a;`. Nếu `a == null`, unboxing ném `NullPointerException`.
- **Chuyển đổi tường minh:** `Integer.parseInt("42")` trả `int`, còn `Integer.valueOf("42")` trả `Integer`. `Long.parseLong`, `Double.parseDouble`... có vai trò tương tự.

Wrapper hữu ích khi API đòi đối tượng: `List<Integer>` hợp lệ, `List<int>` không hợp lệ; khi cần biểu diễn giá trị thiếu bằng `null`; hoặc khi dùng API nhận `Number`. Mảng `int[]` và `Integer[]` là hai loại khác nhau; không có tự động chuyển cả mảng.

**So sánh:** `==` giữa hai wrapper tham chiếu so sánh danh tính đối tượng; `equals` so sánh giá trị theo cùng kiểu wrapper. Một số wrapper có bộ nhớ đệm đối tượng nhỏ, nên kết quả `==` có thể gây hiểu lầm. Với hai `Integer`, dùng `a.equals(b)` nếu chắc chắn khác `null`, hoặc `Objects.equals(a, b)` nếu có thể `null`. Đừng dùng `==` để kết luận hai đối tượng wrapper có cùng giá trị.

**Chi phí:** primitive thường đơn giản hơn cho dữ liệu số dày đặc và tính toán liên tục. Boxing/unboxing trong vòng lặp hoặc collection có thể tạo thêm chi phí đối tượng và rủi ro `null`. Chỉ tối ưu dựa trên nhu cầu và đo đạc thực tế.

## 4. `Number` là gì, và không phải là gì?

`java.lang.Number` là **abstract class** chung của nhiều lớp biểu diễn số. Nó cung cấp hợp đồng lấy giá trị theo kiểu primitive:

```java
Number value = Integer.valueOf(42);
int i = value.intValue();
double d = value.doubleValue();
```

Các phương thức chính gồm `byteValue()`, `shortValue()`, `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`. Hai phương thức đầu có thể có triển khai chung dựa trên `intValue()`; bốn phương thức còn lại là thành phần trừu tượng trọng tâm của hợp đồng `Number`.

**Giới hạn quan trọng:**

1. `Number` là kiểu tham chiếu, nên không thể khai báo `Number n = 5;` rồi coi nó là primitive. Phép boxing cho phép gán `5` thành `Integer` rồi lên `Number`, nhưng biến `n` vẫn là tham chiếu.
2. Không thể viết `n1 + n2` khi cả hai biến khai báo kiểu `Number`; `Number` không định nghĩa toán tử cộng đa hình. Chọn kiểu cụ thể hoặc thiết kế phép toán riêng sau khi biết chính xác yêu cầu.
3. `intValue()`, `longValue()`... là **phép chuyển đổi**, có thể cắt phần thập phân, tràn hoặc làm tròn. Chúng không bảo toàn độ chính xác tùy ý: ví dụ `BigInteger` cực lớn chuyển sang `long` sẽ mất các bit cao.
4. `Number` không khiến `BigDecimal`, `BigInteger` và `Double` có cùng quy tắc so sánh hoặc cùng khả năng biểu diễn. Không ép mọi số sang `double` chỉ để xử lý qua giao diện chung.

Ví dụ ngắn: một API thống kê chấp nhận `Number` có thể đọc `doubleValue()` nếu chấp nhận mất độ chính xác. API kế toán cần tính toán tiền nên công khai `BigDecimal` trong hợp đồng, tránh ép sang `double`.

## 5. `BigInteger`: số nguyên lớn tùy ý độ chính xác

`java.math.BigInteger` biểu diễn **số nguyên có dấu** vượt phạm vi cố định của `long`. Độ lớn chủ yếu chịu giới hạn bộ nhớ/chi phí xử lý, không bị giới hạn 64 bit như primitive. Nó là đối tượng bất biến và kế thừa `Number`.

```java
import java.math.BigInteger;

BigInteger population = new BigInteger("9223372036854775808");
BigInteger next = population.add(BigInteger.ONE);
BigInteger squared = next.multiply(next);
```

Các thao tác phổ biến: `add`, `subtract`, `multiply`, `divide` (chia lấy phần nguyên), `remainder`, `pow` (số mũ nguyên không âm), `gcd`, `compareTo`. `mod` dùng số chia dương và cho số dư không âm; phân biệt nó với `remainder` khi làm việc với số âm. Mỗi phép toán trả một kết quả mới; gọi `population.add(BigInteger.ONE)` mà bỏ kết quả **không thay đổi** `population`.

Tạo đối tượng bằng `new BigInteger("...")` cho số lớn dạng chuỗi, hoặc `BigInteger.valueOf(123L)` cho giá trị đã nằm trong `long`. Dùng `toString()` để xuất và `intValueExact()`/`longValueExact()` khi muốn ném `ArithmeticException` nếu giá trị không vừa kiểu đích. `intValue()`/`longValue()` thông thường có thể mất phần cao mà không báo lỗi.

**Khi chọn:** đếm số nguyên khổng lồ, tính toán tổ hợp/chỉnh hợp lớn, thuật toán số học nguyên. Nếu `long` đã đủ và chạy trong vòng lặp hiệu năng cao, chưa có lý do mặc định chuyển mọi thứ sang `BigInteger`.

## 6. `BigDecimal`: số thập phân chính xác theo hệ 10

`java.math.BigDecimal` biểu diễn số thập phân bằng **giá trị nguyên không chia tỉ lệ (unscaled value)** và **scale**: về mặt toán học, giá trị = `unscaledValue × 10^(-scale)`. Ví dụ `"12.340"` có unscaled value `12340`, scale `3`. Nó bất biến, kế thừa `Number` và hỗ trợ độ chính xác thập phân do người lập trình kiểm soát.

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal price = new BigDecimal("19.90");
BigDecimal tax = new BigDecimal("1.99");
BigDecimal total = price.add(tax); // 21.89

BigDecimal oneThird = BigDecimal.ONE.divide(
    new BigDecimal("3"), 2, RoundingMode.HALF_UP
); // 0.33
```

**Khởi tạo đúng rất quan trọng:**

- `new BigDecimal("0.1")` biểu diễn đúng số thập phân `0.1`.
- `BigDecimal.valueOf(0.1)` dùng dạng chuỗi của `double` và cho `0.1` trong ví dụ này; có ích khi đầu vào đã là `double`, nhưng không thể khôi phục mọi ý định/độ chính xác đã mất trước đó.
- `new BigDecimal(0.1)` nhận **giá trị nhị phân thực tế** của literal `double`; kết quả không đúng bằng thập phân `0.1`. Tránh cách này nếu nguồn là số tiền hoặc số thập phân định trước.
- `BigDecimal.valueOf(123L)` hữu ích cho số nguyên nhỏ; dùng chuỗi cho literal thập phân cần chính xác.

**Phép tính và rounding:**

- `add`, `subtract`, `multiply` không bắt buộc làm tròn nếu không truyền `MathContext`, nhưng kết quả có scale/precision theo phép toán.
- Chia có thể tạo biểu diễn thập phân vô hạn: `BigDecimal.ONE.divide(new BigDecimal("3"))` ném `ArithmeticException` nếu không chỉ định quy tắc làm tròn. Chọn scale + `RoundingMode`, hoặc `MathContext` phù hợp với nghiệp vụ.
- `setScale(2, RoundingMode.HALF_UP)` điều chỉnh số chữ số sau dấu thập phân; `MathContext(precision, roundingMode)` giới hạn **tổng chữ số có nghĩa**, không phải số chữ số sau dấu thập phân.
- Không có một quy tắc làm tròn đúng cho mọi ứng dụng. `HALF_EVEN` hay `HALF_UP` phụ thuộc chuẩn và quy định nghiệp vụ; với tiền tệ, số chữ số lẻ còn tùy loại tiền/đơn vị và quy trình.

**So sánh và lưu khóa:**

```java
BigDecimal x = new BigDecimal("1.0");
BigDecimal y = new BigDecimal("1.00");

System.out.println(x.equals(y));           // false
System.out.println(x.compareTo(y) == 0);    // true
```

`equals` xét **cả giá trị và scale**; `compareTo` so sánh giá trị số học. Vì vậy `BigDecimal` dùng làm key của `HashMap` hoặc phần tử `HashSet` phải có quy tắc chuẩn hóa scale rõ ràng nếu nghiệp vụ coi `1.0` và `1.00` tương đương. `stripTrailingZeros()` có thể đưa scale về 0 hoặc âm, nên đừng mặc định dạng hiển thị sẽ giữ nguyên. `toPlainString()` hữu ích khi muốn chuỗi thập phân không dùng ký pháp lũy thừa.

## 7. Lựa chọn kiểu theo yêu cầu

| Tình huống | Lựa chọn thường phù hợp | Lý do chính |
| --- | --- | --- |
| Biến đếm/chỉ số/mã số nguyên vừa miền 32 bit | `int` | Tự nhiên trong Java, nhẹ, dễ dùng |
| Số nguyên cần phạm vi 64 bit | `long` | Miền lớn hơn `int` nhưng vẫn hữu hạn |
| Dữ liệu thô/định dạng nhị phân yêu cầu 8 hoặc 16 bit | `byte`, `short` | Khớp giao thức/định dạng; không vì thế mà mọi phép toán được thực hiện ở 8/16 bit |
| Đo lường khoa học, đồ họa, thống kê chấp nhận xấp xỉ | `double` (hoặc `float` khi có lý do) | Tốc độ và miền số thực dấu phẩy động |
| Số nguyên vượt `long` và cần phép toán chính xác | `BigInteger` | Không tràn tại biên 64 bit |
| Tiền, đơn giá, tỷ lệ thập phân có quy tắc làm tròn rõ | `BigDecimal` | Biểu diễn thập phân chính xác và kiểm soát rounding |
| API generic cần chấp nhận một họ đối tượng số | `Number` | Hợp đồng chuyển đổi chung; không hàm ý phép tính chính xác chung |
| Collection/generic hoặc dữ liệu có thể thiếu | Wrapper tương ứng | Đối tượng, có thể `null`; chú ý unboxing |

Không dùng `BigDecimal` chỉ vì `double` “không đủ lớn”: hai kiểu giải quyết hai vấn đề khác nhau là **số thực xấp xỉ nhị phân** và **số thập phân có độ chính xác/rounding kiểm soát**. Cũng không dùng `BigInteger` để thay `BigDecimal` nếu phép tính có phần thập phân.

## 8. Ví dụ Java 17+ chạy độc lập: kết nối cả bản đồ

Tạo tệp `NumberMapDemo.java`:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class NumberMapDemo {
    public static void main(String[] args) {
        int primitive = 42;
        Integer wrapper = Integer.valueOf(primitive);
        Number general = wrapper;

        BigInteger huge = new BigInteger("9223372036854775808");
        BigInteger next = huge.add(BigInteger.ONE);

        BigDecimal decimal = new BigDecimal("0.10")
                .add(new BigDecimal("0.20"));

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");

        System.out.println("primitive=" + primitive);
        System.out.println("wrapper=" + wrapper);
        System.out.println("Number->int=" + general.intValue());
        System.out.println("BigInteger=" + next);
        System.out.println("BigDecimal=" + decimal.toPlainString());
        System.out.println("equals=" + a.equals(b));
        System.out.println("compareTo=" + (a.compareTo(b) == 0));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac NumberMapDemo.java
java NumberMapDemo
```

Kết quả chuẩn:

```text
primitive=42
wrapper=42
Number->int=42
BigInteger=9223372036854775809
BigDecimal=0.30
equals=false
compareTo=true
```

Ý nghĩa: `int` được boxing thành `Integer` rồi tham chiếu bằng `Number`; `BigInteger` vượt miền `long`; phép cộng `BigDecimal` giữ scale trong kết quả `0.30`; hai số thập phân bằng nhau về số học nhưng khác biểu diễn scale có kết quả `equals` khác nhau.

## 9. Thử nghiệm lỗi thường gặp: tràn, sai số và unboxing

Tạo tệp `NumericPitfalls.java`:

```java
import java.math.BigDecimal;

public class NumericPitfalls {
    public static void main(String[] args) {
        int amount = 1_500_000_000;
        long overflowed = amount * 2;
        long safe = (long) amount * 2;

        double binary = 0.1 + 0.2;
        BigDecimal exact = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));

        System.out.println("overflowed=" + overflowed);
        System.out.println("safe=" + safe);
        System.out.println("double=" + binary);
        System.out.println("decimal=" + exact.toPlainString());

        Integer maybeMissing = null;
        try {
            int value = maybeMissing; // unboxing null
            System.out.println(value);
        } catch (NullPointerException e) {
            System.out.println("unboxing=null -> NullPointerException");
        }
    }
}
```

```bash
javac NumericPitfalls.java
java NumericPitfalls
```

Kết quả:

```text
overflowed=-1294967296
safe=3000000000
double=0.30000000000000004
decimal=0.3
unboxing=null -> NullPointerException
```

Giải thích: `amount * 2` được tính ở 32 bit trước khi gán cho `long`; ép `(long) amount` trước phép nhân giúp phép nhân diễn ra ở 64 bit. `0.1` và `0.2` ở dạng `double` không biểu diễn chính xác tuyệt đối theo hệ nhị phân; hai chuỗi `"0.1"` và `"0.2"` ở `BigDecimal` giữ được giá trị thập phân mong muốn. Cuối cùng, tham chiếu wrapper có thể `null` và lỗi xuất hiện khi unboxing.

## 10. Những nhầm lẫn cần tránh

1. **“Mọi số đều là `Number`.”** Sai: primitive không kế thừa lớp nào, và `char`/`Character` không thuộc nhánh `Number`.
2. **“Wrapper giống hệt primitive.”** Sai: wrapper là object, có `null`, phương thức, danh tính đối tượng và chi phí liên quan.
3. **“Gán vào `long` tránh mọi tràn `int`.”** Sai nếu biểu thức trước phép gán vẫn tính bằng `int`.
4. **“`double` chính xác cho mọi số tiền vì có nhiều chữ số.”** Sai: biểu diễn nhị phân của nhiều phân số thập phân là xấp xỉ.
5. **“`new BigDecimal(0.1)` tương đương `new BigDecimal("0.1")`.”** Sai: đối số `double` đã mang giá trị nhị phân xấp xỉ.
6. **“`BigDecimal.divide` luôn chạy được.”** Sai: phép chia không có biểu diễn thập phân hữu hạn có thể cần chỉ định rounding.
7. **“Mọi `equals` số học đều nhất quán giữa các lớp.”** Sai: wrapper khác kiểu và `BigDecimal` khác scale có quy tắc `equals` riêng.
8. **“Chuyển qua `Number.doubleValue()` không mất dữ liệu.”** Sai với số nguyên rất lớn và nhiều số thập phân; chuyển sang primitive có thể làm tròn/tràn.
9. **“Gọi `add` sẽ sửa `BigInteger`/`BigDecimal`.”** Sai: cả hai bất biến; cần lưu kết quả trả về.
10. **“`float`/`double` chỉ có các số hữu hạn.”** Sai: còn có `NaN`, dương/vô cực âm và số 0 có dấu. Các trường hợp này cần được xử lý rõ khi kiểm tra dữ liệu và so sánh.

## 11. Checklist khi thiết kế một giá trị số

- Giá trị có phần thập phân không? Nếu có, ứng dụng chấp nhận **xấp xỉ nhị phân** hay cần **thập phân chính xác có rounding**?
- Giá trị nguyên lớn nhất/nhỏ nhất có vừa `int` hoặc `long` không? Cần phát hiện tràn hay miền số tùy ý?
- API cần primitive hay object? Có chấp nhận `null` không, và đâu là điểm unboxing?
- Có phép chuyển `Number` → primitive nào mất dữ liệu không?
- Với `BigDecimal`: nguồn vào là chuỗi hay `double`; quy tắc scale, rounding, `equals`/`compareTo` và định dạng hiển thị được quyết định ở đâu?
- Với dữ liệu đưa vào collection/map, quy tắc bằng nhau và chuẩn hóa đã nhất quán chưa?

**Ghi nhớ bản đồ:** `int/long/double` giải quyết phép toán primitive theo miền hữu hạn; wrapper cung cấp đối tượng cho những giá trị đó; `Number` là điểm chung để **đọc/chuyển đổi** số trong các lớp; `BigInteger` giải quyết **số nguyên rất lớn**; `BigDecimal` giải quyết **thập phân chính xác với quy tắc scale/rounding**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Chọn kiểu theo ý nghĩa dữ liệu và yêu cầu tính toán</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kiểu số trong Java theo ý nghĩa dữ liệu và yêu cầu tính toán

Một chương trình có thể biên dịch và chạy mà vẫn tính **sai về nghiệp vụ** nếu chọn kiểu số không phù hợp. Câu hỏi đầu tiên không phải “nên dùng `int` hay `double`?”, mà là: **giá trị này đại diện cho điều gì, được phép nhận những giá trị nào, phép tính nào sẽ thực hiện và thế nào được coi là đúng?**

Trong module **Java core – numbers**, bài học này xây dựng quy trình ra quyết định để chọn `byte`, `short`, `int`, `long`, `float`, `double`, `BigInteger`, `BigDecimal`, hoặc một kiểu **không phải số** khi dữ liệu chỉ trông giống số. Mục tiêu là lựa chọn có giải thích và có thể kiểm chứng, không chỉ ghi nhớ bảng phạm vi kiểu dữ liệu.

## 1. Bốn câu hỏi cần trả lời trước khi chọn kiểu

### Câu hỏi 1: Dữ liệu thực sự mang ý nghĩa gì?

Phân biệt **đại lượng** dùng để tính toán với **mã định danh** dùng để nhận diện:

- **Số lượng rời rạc**: số học viên, số kiện hàng, lượt truy cập, số byte → số nguyên.
- **Giá trị đo liên tục**: nhiệt độ cảm biến, tọa độ vật lý, vận tốc → thường có thể dùng `double` nếu chấp nhận sai số nhị phân.
- **Số thập phân phải tuân thủ quy tắc nghiệp vụ**: đơn giá, thuế, tiền, tỷ lệ phân bổ, điểm theo thang thập phân → thường cần `BigDecimal` hoặc số nguyên biểu diễn đơn vị nhỏ nhất.
- **Mã nhận diện**: mã bưu chính `"00123"`, số điện thoại, mã khách hàng `"000042"`, số tài khoản, số thẻ, mã hàng → thường là `String`. Những giá trị này không có ý nghĩa cộng, trừ và có thể chứa số 0 đầu.
- **Thời gian và khoảng thời gian**: thời điểm sự kiện, ngày giao hàng, hạn xử lý → thường dùng `Instant`, `LocalDate`, `Duration` hoặc kiểu `java.time` phù hợp thay vì tự chọn một con số không rõ đơn vị.

**Phản ví dụ:** đặt `int postalCode = 00123;` vừa làm mất các số 0 đầu vừa có thể vướng cú pháp literal bát phân. Đúng theo ý nghĩa dữ liệu sẽ là `String postalCode = "00123";`. Tương tự, một ID có toàn chữ số vẫn không tự động là đại lượng toán học.

### Câu hỏi 2: Miền giá trị và mức tăng trưởng là bao nhiêu?

Xác định giá trị nhỏ nhất, lớn nhất, có âm không, có thể tăng trong bao lâu, và **các giá trị trung gian** có lớn hơn kết quả cuối không.

Ví dụ, số lượng sản phẩm trong giỏ hàng có thể phù hợp `int`; tổng số byte xử lý trong một dịch vụ chạy nhiều năm có thể cần `long`; một bộ đếm tổ hợp toán học có thể vượt `long`, cần `BigInteger`.

Đừng chỉ dựa vào dữ liệu hiện tại. Hãy hỏi cả “3 năm sau thì sao?”, “cộng 10.000 đối tượng thì sao?” và “nhân hai số lớn thì sao?”. Biên độ an toàn là yêu cầu thiết kế, không phải lý do bỏ qua kiểm tra tràn số.

### Câu hỏi 3: Yêu cầu chính xác được định nghĩa thế nào?

Ba mục tiêu khác nhau dẫn đến lựa chọn khác nhau:

1. **Chính xác tuyệt đối cho số nguyên:** `int`, `long` trong phạm vi; `BigInteger` khi phạm vi có thể vượt 64 bit.
2. **Gần đúng có sai số kiểm soát:** `double` hoặc `float` cho bài toán số học nhị phân, đo lường, mô phỏng, thống kê; quy định sai số theo ngữ cảnh.
3. **Chính xác theo hệ thập phân với cách làm tròn quy định:** `BigDecimal` cho các giá trị thập phân nghiệp vụ. “Chính xác” ở đây không có nghĩa phép chia vô hạn như `1/3` tự được biểu diễn hoàn toàn; phải chỉ định scale/precision và rounding khi cần.

Một giá trị có dấu phẩy thập phân không tự động yêu cầu `BigDecimal`: phép đo nhiệt độ `23.7°C` có thể phù hợp `double`; số tiền `23.70` thường phải có quy tắc thập phân chặt chẽ.

### Câu hỏi 4: Những phép toán và biên trao đổi dữ liệu nào tham gia?

Kiểu được chọn còn phụ thuộc vào:

- Phép toán: chỉ lưu/hiển thị, cộng trừ, nhân chia, lũy thừa, so sánh, sắp xếp, tổng hợp, làm tròn.
- Luồng dữ liệu: giá trị đến từ JSON, CSV, cơ sở dữ liệu, API, cảm biến, người dùng nhập hay một phép đo đã lượng tử hóa.
- Phần mềm nhận kết quả: một số `long` không thể được giữ chính xác trong JavaScript khi truyền như JSON number; phía nhận có thể cần chuỗi hoặc giao thức số nguyên an toàn.
- Quy ước miền: đơn vị (`ms`, `s`, `bytes`, `kg`), dấu âm, scale thập phân, mức làm tròn, giới hạn hợp lệ, cách biểu diễn giá trị thiếu.

## 2. Bảng quyết định: chọn theo mục đích

| Ý nghĩa dữ liệu / yêu cầu | Lựa chọn nên ưu tiên | Vì sao | Điều cần kiểm tra |
| --- | --- | --- | --- |
| Số lượng nhỏ, chỉ số vòng lặp/mảng | `int` | Thuận tiện, nhiều API Java sử dụng `int` | Không âm khi miền yêu cầu; không tràn khi cộng/nhân |
| Bộ đếm/tổng dung lượng có thể vượt 2,1 tỷ | `long` | Miền 64 bit rộng hơn `int` | Tràn vẫn có thể xảy ra; đọc/ghi API hoặc CSDL nhất quán |
| Số nguyên vượt giới hạn `long` | `BigInteger` | Không cố định độ rộng 64 bit | Hiệu năng, bộ nhớ, giới hạn đầu vào |
| Giá trị thực đo, mô phỏng, xác suất gần đúng | `double` | Dải động và độ chính xác tốt hơn `float` | Sai số tuyệt đối/tương đối; NaN, vô cực |
| Dữ liệu `float` được yêu cầu rõ bởi API/bộ nhớ/mô hình | `float` | Tương thích định dạng 32 bit, có thể tiết kiệm bộ nhớ trong mảng lớn | Chỉ khoảng 6–7 chữ số thập phân có nghĩa |
| Đơn giá, tiền, thuế, lãi suất thập phân theo quy tắc nghiệp vụ | `BigDecimal` | Thập phân hữu hạn được biểu diễn đúng nếu khởi tạo đúng | Scale, rounding, currency, thời điểm làm tròn |
| Số tiền đã quy đổi sang đơn vị nhỏ nhất và có giới hạn rõ | `long` | Phép cộng/trừ nguyên chính xác trong miền | Đơn vị tiền, chính sách chuyển đổi/phân chia |
| Cờ trạng thái đúng/sai | `boolean` | Biểu diễn đúng miền logic | Không dùng 0/1 khi không cần |
| Mã bưu chính, điện thoại, số thẻ, mã có số 0 đầu | `String` | Mã định danh, không phải đại lượng | Giữ nguyên ký tự/độ dài; bảo vệ dữ liệu nhạy cảm |
| Ngày, thời điểm, khoảng thời gian | `java.time` phù hợp | Có ngữ nghĩa thời gian rõ ràng | Múi giờ, lịch, đơn vị, UTC so với giờ địa phương |

`byte` và `short` có vai trò khi giao tiếp nhị phân, cấu trúc lưu trữ hoặc API yêu cầu đúng bề rộng 8/16 bit. Với phép đếm và tính toán thông thường, `int` thường dễ dùng hơn dù giá trị hiện tại nhỏ: biểu thức số học trên `byte`/`short` vẫn hay được nâng lên `int`. Không lựa chọn `byte` chỉ vì “số nhỏ hơn 100”.

## 3. Quy trình quyết định có thể áp dụng lại

```text
Giá trị có phải đại lượng dùng để tính toán không?
├─ Không → String, boolean, enum, java.time hoặc kiểu miền riêng
└─ Có
   ├─ Giá trị bắt buộc là số nguyên?
   │  ├─ Có → Trong miền int?
   │  │        ├─ Có → int
   │  │        └─ Không → Trong miền long?
   │  │                     ├─ Có → long
   │  │                     └─ Không → BigInteger
   │  └─ Không
   │     ├─ Được phép sai số nhị phân có kiểm soát?
   │     │  ├─ Có → double (float nếu có yêu cầu cụ thể)
   │     │  └─ Không → BigDecimal hoặc số nguyên đã định đơn vị/scale
   │     └─ Phép chia/làm tròn → phải định nghĩa chính sách
   └─ Ở mọi nhánh: xác nhận miền dữ liệu, đơn vị, kiểm soát lỗi và
      cách bảo toàn giá trị khi lưu hoặc gửi qua API.
```

Sơ đồ là điểm khởi đầu; một kiểu số vẫn có thể không đạt yêu cầu nếu một phép tính trung gian tràn hoặc nếu lớp giao tiếp làm mất độ chính xác. Với bộ đếm có giá trị âm không hợp lệ, bạn vẫn phải kiểm tra `value >= 0`: `int` là kiểu **có dấu**, không tự ràng buộc miền nghiệp vụ.

## 4. Chọn số nguyên: phạm vi, tràn và kết quả trung gian

Các kiểu nguyên có dấu trong Java:

| Kiểu | Phạm vi | Gợi ý sử dụng |
| --- | --- | --- |
| `byte` (8 bit) | -128 đến 127 | Dữ liệu nhị phân hoặc giao thức định dạng 8 bit |
| `short` (16 bit) | -32.768 đến 32.767 | Tương thích cấu trúc 16 bit |
| `int` (32 bit) | -2.147.483.648 đến 2.147.483.647 | Số lượng và chỉ số thông thường |
| `long` (64 bit) | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | Số nguyên lớn hơn `int` |
| `BigInteger` | Không bị giới hạn bởi số bit cố định | Toán học/số đếm rất lớn; giới hạn thực tế bởi bộ nhớ/tài nguyên |

**Cần xác định kiểu tại thời điểm thực hiện phép tính.** Trong ví dụ:

```java
int count = 1_500_000_000;

long wrong = count * 3;          // Nhân theo int rồi mới gán long: đã tràn.
long correct = (long) count * 3; // Nâng kiểu trước phép nhân: 4_500_000_000L.
long exact = Math.multiplyExact((long) count, 3L);
```

Đây là mảnh mã minh họa, không phải chương trình độc lập. Java không tự báo lỗi tràn cho các phép tính `int`/`long` thông thường. Dùng `Math.addExact`, `Math.subtractExact`, `Math.multiplyExact`, `Math.toIntExact` khi tràn là điều không thể chấp nhận. Nếu không thể bảo đảm kết quả trong `long`, hãy chuyển hẳn phép tính sang `BigInteger`, thay vì ép kiểu kết quả đã tràn.

**Chia nguyên không giữ phần lẻ:**

```java
int completed = 7;
int groups = 2;
int each = completed / groups;            // 3
double average = (double) completed / groups; // 3.5
```

Nếu chia cho 0 bằng số nguyên, Java ném `ArithmeticException`. Với số đếm chính xác cần chia và cho ra phần lẻ, quyết định đó có ý nghĩa gì: có thể là tỉ lệ đo gần đúng (`double`), phép tính tỷ lệ nghiệp vụ (`BigDecimal`) hoặc quotient/remainder nguyên để phân bổ đơn vị còn dư.

**BigInteger:** tạo bằng `new BigInteger("9223372036854775808")`, dùng `add`, `multiply`, `divide`, `compareTo`. Đây là đối tượng bất biến; `value.add(BigInteger.ONE)` trả một đối tượng mới và không tự thay đổi `value`. Đừng kỳ vọng toán tử `+` hoạt động với hai đối tượng `BigInteger`.

Một lưu ý quan trọng: biểu diễn không dấu trong giao thức bên ngoài cần được xử lý rõ ràng. Các kiểu nguyên thông thường của Java đều có dấu; `Integer.toUnsignedLong`, `Long.compareUnsigned`, `Long.toUnsignedString` chỉ hỗ trợ **diễn giải/thao tác không dấu trên bit hiện có**, không biến `long` thành kiểu có phạm vi nguyên dương tùy ý. Với giá trị unsigned 64 bit đầy đủ, cân nhắc `BigInteger` ở lớp xử lý miền khi cần một giá trị toán học không âm vượt `Long.MAX_VALUE`.

## 5. Chọn số thực: sai số và tiêu chí kiểm chứng

`float` và `double` là số dấu phẩy động **nhị phân**. Chúng có thể xấp xỉ nhiều giá trị thập phân nhưng không giữ đúng mọi phân số như `0.1`. Với phép đo có nhiễu và mô hình vật lý, sai số số học có thể là một phần hợp lý của bài toán; với tiền bạc, sai số đó thường không phù hợp.

```java
double measuredTemperature = 23.75;
double a = 0.1;
double b = 0.2;
boolean exactSame = (a + b == 0.3); // false
```

Hai điều cần tránh:

1. Không dùng `==` với hai kết quả tính thực khi mục đích là hỏi “đủ gần nhau hay không”. Xác định sai số theo đơn vị và độ lớn, có thể kết hợp ngưỡng tuyệt đối và tương đối; ngưỡng thích hợp với nhiệt độ có thể vô nghĩa với khoảng cách thiên văn.
2. Không cho rằng `double` có “15 chữ số sau dấu phẩy”. Nó có khoảng 15–16 chữ số **có nghĩa**; độ phân giải phụ thuộc độ lớn. `float` thường chỉ có khoảng 6–7 chữ số thập phân có nghĩa.

`NaN`, `Infinity` và `-Infinity` tồn tại trong dấu phẩy động. Chúng không phải dữ liệu đo lường hợp lệ trong mọi miền; cần kiểm tra `Double.isFinite(value)` trước khi ghi hoặc truyền vào các phép tính yêu cầu giá trị hữu hạn. `Double.isNaN` xác định NaN; `NaN == NaN` là `false`. Một số phép chia dấu phẩy động cho 0 trả giá trị vô cực hoặc NaN, khác với chia nguyên cho 0.

**Khi nào chọn `float`?** Chỉ khi giao thức, thư viện, định dạng tệp, mảng số rất lớn hoặc mô hình tính toán cho phép độ chính xác 32 bit và có lợi ích xác định. Trong Java, literal `1.5` mặc định là `double`; viết `1.5f` cho `float`. Đừng chuyển `double` sang `float` chỉ vì giá trị in ra trông giống nhau; độ chính xác có thể mất.

## 6. Chọn số thập phân chính xác: BigDecimal và chính sách làm tròn

`java.math.BigDecimal` biểu diễn giá trị hữu hạn bằng **hệ số nguyên × 10^(-scale)**. Giá trị từ chuỗi `"19.95"` là thập phân chính xác; không mắc sai số biểu diễn của literal `double`.

```java
BigDecimal price = new BigDecimal("19.95");
BigDecimal quantity = BigDecimal.valueOf(3L);
BigDecimal subtotal = price.multiply(quantity); // 59.85
```

Mảnh mã trên cần `import java.math.BigDecimal;` và đặt trong phương thức. Các thao tác với `BigDecimal` trả đối tượng mới: phải giữ kết quả của `add`, `multiply`, `setScale`…

### 6.1. Khởi tạo từ đúng nguồn

Nên ưu tiên `new BigDecimal("0.1")` nếu nguồn là biểu diễn thập phân. Tránh `new BigDecimal(0.1)` khi ý định là **đúng một phần mười**, vì constructor nhận chính giá trị nhị phân của `double` và tạo một biểu diễn thập phân chính xác cho giá trị nhị phân đó. `BigDecimal.valueOf(0.1)` sử dụng chuỗi thập phân chuẩn của `double` và cho `0.1` trong ví dụ này, nhưng không thể sửa lỗi đã phát sinh từ phép tính `double` trước đó.

### 6.2. Precision, scale, rounding là ba quyết định khác nhau

- **Scale** là số vị trí thập phân trong biểu diễn: `12.340` có scale 3.
- **Precision** là số chữ số có nghĩa theo biểu diễn `BigDecimal`: `12.340` có precision 5.
- **RoundingMode** quyết định cách bỏ chữ số khi phải giảm độ chi tiết. `HALF_UP`, `HALF_EVEN`, `DOWN`, `CEILING` không tương đương nhau, đặc biệt với số âm và giá trị ở điểm giữa.

```java
BigDecimal amount = new BigDecimal("12.345");
BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP); // 12.35

BigDecimal oneThird = BigDecimal.ONE.divide(
        new BigDecimal("3"), 4, RoundingMode.HALF_EVEN); // 0.3333
```

Mảnh mã cần `import java.math.RoundingMode;`. Nếu gọi `BigDecimal.ONE.divide(new BigDecimal("3"))` mà không cung cấp điều kiện làm tròn, phép chia không kết thúc sẽ ném `ArithmeticException`. Khi dùng `MathContext`, precision là **số chữ số có nghĩa** cho phép tính, không phải số vị trí sau dấu phẩy. Bài toán tiền thường còn cần đặt scale sau những bước nhất định.

### 6.3. Hai giá trị bằng nhau nhưng scale khác nhau

`new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false`, vì `equals` có xét scale. `compareTo` trả `0`, vì so giá trị toán học. Cần chọn chủ ý theo nghiệp vụ: cùng số tiền có thể được coi là cùng **giá trị**, trong khi định dạng hiển thị vẫn cần giữ 2 chữ số thập phân.

### 6.4. Tiền dùng BigDecimal hay long?

Có hai cách thiết kế hợp lý nếu hợp đồng dữ liệu nêu đủ đơn vị:

- **`BigDecimal` theo đơn vị tiền chính**: ví dụ `19.95` USD, thao tác bằng thập phân, quy tắc scale/rounding rõ.
- **`long` theo đơn vị nhỏ nhất**: ví dụ `1995` cent, cộng và trừ nguyên; phải kiểm soát tràn và xác định phép chia/phân bổ số cent dư. Không được giả định mọi loại tiền tệ đều có 2 chữ số thập phân.

Nếu tính thuế, giảm giá, tỷ giá, lãi suất hoặc phân bổ, cần quy định **làm tròn ở mỗi dòng hay sau khi cộng tổng**. Hai thứ tự có thể cho kết quả khác nhau dù đều dùng `BigDecimal`. Một kiểu dữ liệu chính xác không thay thế quy tắc kế toán.

## 7. Ranh giới kiểu: null, boxing, lưu trữ và giao tiếp

### 7.1. Primitive hay wrapper?

- `int` là giá trị nguyên thủy, không thể là `null`. `Integer` là đối tượng có thể biểu diễn `null` trong API/DTO, và là kiểu tham số của `List<Integer>`.
- `long`/`Long`, `double`/`Double` có quan hệ tương tự. Boxing/unboxing diễn ra tự động trong nhiều ngữ cảnh, nhưng unboxing `null` sẽ gây `NullPointerException`.
- Nếu “chưa nhập” khác “bằng 0”, hãy mô hình hóa sự khác biệt rõ ràng bằng wrapper hoặc một đối tượng miền thích hợp. Không chọn `Integer` chỉ để có `null` nếu miền bắt buộc có giá trị và điều đó làm code dễ lỗi.

```java
Integer optionalScore = null;
// int score = optionalScore; // Nếu thực hiện sẽ ném NullPointerException.
```

So sánh wrapper bằng `equals` khi cần so **giá trị đối tượng**, thay vì phụ thuộc `==` vốn so tham chiếu giữa hai wrapper (và có trường hợp cache khiến kết quả gây nhầm lẫn). Kiểm tra `null` trước khi unboxing.

### 7.2. API và database có thể thay đổi quyết định

Một kiểu Java phải tương thích với môi trường lưu trữ. Khi ghi xuống database, kiểm tra giới hạn `INTEGER`/`BIGINT`, `DECIMAL(p,s)`, kiểu của driver, schema migration và ràng buộc nghiệp vụ. Ví dụ `DECIMAL(10,2)` quy định tối đa 10 chữ số tổng cộng và 2 chữ số sau dấu phẩy; kiểu này không tự bao trùm mọi `BigDecimal` mà ứng dụng có thể tạo.

Với JSON/JavaScript, các số nguyên có độ lớn hơn `2^53 - 1` có thể không được giữ chính xác trong kiểu `Number` của JavaScript. Một `long` hợp lệ phía Java không bảo đảm an toàn khi xuất trực tiếp thành JSON number để JavaScript tiêu thụ. Có thể thiết kế JSON string cho mã/số nguyên lớn, hoặc thỏa thuận rõ bộ giải mã `BigInt` ở bên nhận.

Với số thập phân chính xác, phải chắc rằng tầng JSON/parser không chuyển qua `double` rồi mới đưa vào `BigDecimal`. Truyền định dạng thập phân dưới dạng string khi giao thức yêu cầu bảo toàn nguyên văn hoặc quy tắc serialisation của cả hai phía đã được kiểm chứng.

### 7.3. Đơn vị phải đi theo dữ liệu

`long timeout = 5000;` không cho biết 5000 mili giây, giây hay nano giây. Trong code thực tế, dùng `Duration.ofSeconds(5)` hoặc tên `timeoutMillis` khi API phải nhận số nguyên. Tương tự, không cộng `distanceMeters` với `distanceKilometers` mà chưa quy đổi; cân nhắc định nghĩa class/record miền hoặc API nhận đơn vị rõ.

Một hệ thống có thể chọn `long` đúng về miền giá trị nhưng vẫn sai nếu cộng số cent với số đồng. **Kiểu nguyên thủy không mang đơn vị**, nên trách nhiệm bảo toàn đơn vị nằm ở thiết kế miền, tên biến, validation và hợp đồng API.

## 8. Những phép chuyển đổi dễ làm mất ý nghĩa

| Chuyển đổi hoặc biểu thức | Rủi ro | Hướng xử lý |
| --- | --- | --- |
| `long` → `int` bằng cast | Cắt các bit cao, đổi giá trị | `Math.toIntExact(value)` hoặc kiểm tra miền trước |
| `int * int` → gán `long` | Nhân theo `int` rồi mới gán, có thể tràn | Nâng ít nhất một toán hạng lên `long` trước nhân |
| `long` → `double` | Không phải mọi `long` đều biểu diễn chính xác | Tránh với định danh/bộ đếm đòi hỏi exactness |
| `double` → `int` | Bỏ phần lẻ theo hướng 0; ngoài miền và NaN có quy tắc cast đặc biệt | Kiểm tra `Double.isFinite`, miền và chính sách làm tròn |
| `double` → `BigDecimal` | Dữ liệu có thể đã bị làm tròn nhị phân | Lấy chuỗi thập phân gốc; nếu nguồn là double, hiểu semantics của `valueOf` |
| `BigDecimal` → `double` | Có thể mất precision hoặc vượt miền | Chỉ thực hiện khi ứng dụng chấp nhận xấp xỉ |
| `BigDecimal` → `int`/`long` | `intValue`/`longValue` có thể bỏ phần lẻ hoặc mất bit | Dùng `intValueExact()`/`longValueExact()` khi cần chính xác |
| `int / int` → gán `double` | Phép chia đã bỏ phần lẻ | Cast một toán hạng trước phép chia |
| `String` chứa số có dấu phẩy theo locale → parser chuẩn | `NumberFormatException` hoặc hiểu sai dấu | Tách quy tắc nhập/hiển thị khỏi biểu diễn số chuẩn |
| `Integer` là `null` → `int` | `NullPointerException` khi unboxing | Validation trước unboxing hoặc thiết kế không-null |

Không phải mọi giá trị `long` đều biểu diễn chính xác bằng `double`, dù cả hai đều là 64 bit: `double` dành nhiều bit cho dấu và số mũ, nên số bit có nghĩa hữu hạn. Điều này đặc biệt nguy hiểm khi biến mã số lớn thành số thực để “tiện xử lý”.

## 9. Ví dụ tình huống: quyết định cho từng trường dữ liệu

Giả sử xây một dịch vụ xử lý đơn hàng và thống kê thiết bị:

| Trường | Kiểu đề xuất | Lập luận |
| --- | --- | --- |
| `orderCode = "0000157"` | `String` | Định danh, giữ số 0 đầu, không tính toán |
| `quantity = 3` | `int` | Đếm sản phẩm; kiểm tra `quantity >= 0` và mức tối đa theo nghiệp vụ |
| `processedBytes = 5_000_000_000` | `long` | Vượt `Integer.MAX_VALUE`; có đơn vị byte |
| `temperatureCelsius = 23.75` | `double` | Phép đo có thể chấp nhận sai số nhị phân |
| `unitPrice = "19.95"` | `BigDecimal` | Đơn giá thập phân chính xác |
| `taxRate = "0.08"` | `BigDecimal` | Tỷ lệ tính tiền theo quy tắc làm tròn |
| `globalSequence` vượt `long` | `BigInteger` (nếu cần tính toán) hoặc `String` (nếu chỉ định danh) | Ý nghĩa quyết định kiểu, không chỉ độ dài |
| `deadline` | `Instant` | Mốc thời gian; có ngữ nghĩa rõ hơn số milliseconds trần |

**Điểm cần phân biệt:** `globalSequence` nếu chỉ được gửi qua API như một mã định danh có thể phù hợp `String`; nếu là số nguyên cần tăng, so sánh số học, cộng trừ vượt 64 bit thì cần `BigInteger`. Cùng một chuỗi ký tự chứa toàn chữ số nhưng quyết định khác nhau vì hành vi nghiệp vụ khác nhau.

## 10. Chương trình Java 17+ hoàn chỉnh: biến yêu cầu thành lựa chọn kiểu

Lưu file **`NumericTypeChoices.java`**. Chương trình minh họa cách giữ mã định danh dưới dạng chuỗi, dùng `long` cho số byte lớn, `double` cho một phép đo, `BigInteger` cho số nguyên rất lớn, `BigDecimal` cho tiền, và kiểm tra tràn bằng `Math.multiplyExact`. Trong ví dụ hóa đơn, để minh họa quy tắc nghiệp vụ, thuế được làm tròn đến hai chữ số thập phân theo `HALF_UP` trước khi cộng vào tổng.

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class NumericTypeChoices {
    public static void main(String[] args) {
        String orderCode = "0000157";
        int quantity = 3;
        long processedBytes = 5_000_000_000L;
        double temperatureCelsius = 23.75;

        BigInteger globalSequence =
                new BigInteger("9223372036854775808");

        BigDecimal unitPrice = new BigDecimal("19.95");
        BigDecimal taxRate = new BigDecimal("0.08");
        BigDecimal subtotal =
                unitPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal tax = subtotal.multiply(taxRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        long checkedProduct =
                Math.multiplyExact(1_500_000_000L, 3L);

        System.out.println("order-code=" + orderCode);
        System.out.println("quantity=" + quantity);
        System.out.println("processed-bytes=" + processedBytes);
        System.out.println("temperature-c=" + temperatureCelsius);
        System.out.println("sequence=" + globalSequence);
        System.out.println("subtotal=" + subtotal);
        System.out.println("tax=" + tax);
        System.out.println("total=" + total);
        System.out.println("checked-product=" + checkedProduct);
    }
}
```

Biên dịch và chạy:

```bash
javac NumericTypeChoices.java
java NumericTypeChoices
```

Kết quả stdout chính xác:

```text
order-code=0000157
quantity=3
processed-bytes=5000000000
temperature-c=23.75
sequence=9223372036854775808
subtotal=59.85
tax=4.79
total=64.64
checked-product=4500000000
```

Kiểm tra phép tính: `19.95 × 3 = 59.85`; `59.85 × 0.08 = 4.788`; theo `HALF_UP` ở scale 2 thành `4.79`; tổng là `64.64`. Đây là quy tắc được **chọn riêng cho ví dụ**, không khẳng định mọi hóa đơn hoặc mọi loại tiền tệ đều làm tròn theo cách này.

Ví dụ cho thấy cùng một chương trình cần nhiều kiểu vì mỗi trường có vai trò khác nhau. Việc dùng một kiểu duy nhất cho mọi trường sẽ gây mất ngữ nghĩa hoặc tạo thêm rủi ro không cần thiết.

## 11. Kỹ thuật thiết kế và kiểm thử sau khi chọn kiểu

Sau khi quyết định kiểu, nên ghi lại các điều kiện bảo đảm tính đúng đắn trong hợp đồng dữ liệu:

1. **Miền hợp lệ:** Có được âm không? Có được 0 không? Giá trị tối đa? Chấp nhận `null` không?
2. **Đơn vị:** Byte, milli giây, kilogram, USD hay cent? Đơn vị đã được quy đổi trước phép toán chưa?
3. **Chính xác:** Kết quả bắt buộc chính xác nguyên/thập phân hay chỉ gần đúng? Nếu gần đúng, sai số hợp lệ được đo như thế nào?
4. **Chia và làm tròn:** Khi nào thực hiện? Scale bao nhiêu? `RoundingMode` nào? Có chính sách khi số không chia hết không?
5. **Tràn số:** Có kiểm tra trước/sau phép nhân và phép cộng, sử dụng phép `Exact`, hoặc chuyển sang `BigInteger` chưa?
6. **Đường đi dữ liệu:** Kiểu ở Java, JSON, JavaScript, database và CSV có bảo toàn cùng giá trị/đơn vị không?
7. **Giá trị thiếu/không hợp lệ:** `null`, chuỗi rỗng, NaN/vô cực, chữ số quá dài, số âm ngoài quy tắc được xử lý ra sao?

Các trường hợp kiểm thử quan trọng thường nằm ở **biên**, không phải ví dụ bình thường:

| Kiểm thử | Mục đích |
| --- | --- |
| `Integer.MAX_VALUE + 1` với `Math.addExact` | Phát hiện tràn thay vì nhận giá trị quay vòng |
| Nhân hai số `int` lớn và so kết quả khi nâng lên `long` trước nhân | Bắt lỗi tràn ở biểu thức trung gian |
| `BigDecimal.ONE.divide(new BigDecimal("3"))` | Xác nhận chính sách chia số thập phân không kết thúc |
| `0.1 + 0.2` và so gần đúng với `0.3` | Bắt giả định sai về `double` |
| `new BigDecimal("2.0")` so với `"2.00"` | Phân biệt so giá trị và so biểu diễn |
| Giá trị `long` trên `2^53 - 1` đi qua JSON/JavaScript | Xác minh không mất tính chính xác khi trao đổi |
| Mã `"0000157"` qua lưu trữ/serialize/deserialize | Xác minh số 0 đầu và định danh được bảo toàn |
| Thuế ở điểm giữa, giảm giá âm/dương và nhiều dòng hóa đơn | Xác minh đúng rounding rule và thời điểm làm tròn |
| `null` của wrapper trong DTO | Bắt unboxing ngoài ý muốn |
| Đơn vị mili giây so với giây | Bắt lỗi chuyển đổi đơn vị |

Đối với phép đo, hãy thử các giá trị 0, gần 0, cực lớn, NaN và vô cực tùy miền dữ liệu; tránh một dung sai tuyệt đối duy nhất cho mọi thang đo. Đối với tiền, chú trọng những giá trị mà rounding làm thay đổi kết quả và kết quả gộp nhiều dòng.

## 12. Bài tập áp dụng

**Bài 1 — Phân loại theo ngữ nghĩa.** Chọn kiểu Java cho mỗi trường và giải thích: `zipCode = "01234"`, `activeUserCount` tối đa 20 triệu, `totalBytes` tối đa 12 tỷ, `stockPrice = "123.4567"`, `sensorVoltage` đo gần đúng, `createdAt` là thời điểm UTC, `isEnabled` chỉ đúng/sai. Nêu kiểm tra hợp lệ và định dạng lưu trữ.

**Bài 2 — Tràn số có thể ẩn.** Viết phép tính số byte trung bình cho 3 tỷ bản ghi, biết mỗi bản ghi 4 byte; thử các biểu thức `int`, `long` và `Math.multiplyExact`. Giải thích kiểu toán hạng ở bước nhân quyết định kết quả ra sao.

**Bài 3 — Tiền và quy tắc kế toán.** Xây phương thức tính tổng cho đơn giá `"19.95"`, số lượng 3 và thuế 8%. Kiểm thử hai cách: làm tròn thuế từng dòng rồi cộng, hoặc cộng cơ sở thuế trước và làm tròn một lần. Viết ra quy tắc nào được yêu cầu trước khi chọn một kết quả.

**Bài 4 — Giao tiếp API.** Một dịch vụ Java trả `long id = 9_007_199_254_740_993L`. Đề xuất schema JSON giữ nguyên ID khi client JavaScript sử dụng `Number` theo mặc định. Giải thích vì sao ID nên được xem là mã nhận diện và kiểm tra bảo toàn khi round-trip.

**Bài 5 — Lựa chọn không hiển nhiên.** Một đo lường có 6 chữ số có nghĩa, dữ liệu gốc từ phần cứng `float32`; một chỉ số tài chính cũng có 6 chữ số nhưng cần tính lãi suất lặp nhiều kỳ. Chọn kiểu cho từng trường và lý giải tại sao “cùng số chữ số” không đủ để dùng cùng một kiểu.

## 13. Checklist quyết định trước khi đưa vào code thật

- Xác định giá trị là đại lượng, định danh hay ngày/giờ; không đánh đồng chữ số với kiểu số.
- Ghi miền min/max, có dấu, giá trị trung gian, chu kỳ tăng trưởng và đơn vị.
- Chọn `int`/`long`/`BigInteger` cho đại lượng nguyên theo phạm vi; kiểm soát overflow.
- Chọn `double` cho phép tính thực cho phép sai số; dùng `float` khi có yêu cầu rõ.
- Chọn `BigDecimal` hoặc số nguyên theo đơn vị nhỏ nhất cho nghiệp vụ thập phân chính xác; xác định scale và rounding.
- Làm rõ `null` và wrapper, không unbox khi chưa kiểm tra.
- Xác nhận kiểu và ý nghĩa được giữ nguyên qua API, JSON, CSV, database và các phép chuyển đổi.
- Kiểm thử cả biên, lỗi nhập liệu, đổi đơn vị, tràn số, giá trị không hữu hạn và trường hợp phải làm tròn.

**Quy tắc cốt lõi:** lựa chọn kiểu dữ liệu đúng bắt đầu từ **ý nghĩa và bất biến của miền dữ liệu**, sau đó mới xét phạm vi, cách biểu diễn, độ chính xác, phép tính và môi trường trao đổi. Kiểu giúp biểu diễn giá trị; validation và quy tắc nghiệp vụ quyết định giá trị đó có đúng trong hệ thống hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Kiến thức tiên quyết và trình tự học đề xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức tiên quyết và trình tự học đề xuất — Java Core: Numbers

## 1. Bài này dành cho ai?

Đây là **bản đồ chuẩn bị và tự học** dành cho người đã biết cú pháp Java căn bản nhưng chưa chắc nên bắt đầu từ đâu khi gặp bài toán số. Mục đích là đánh giá điểm xuất phát, định thứ tự luyện tập và biết quay lại phần thích hợp khi sai; các bài đứng trước cung cấp giải thích sâu về từng kiểu và phép tính.

Bạn cần một JDK 17 trở lên để chạy ví dụ, biết tạo class có `main`, khai báo biến, dùng biểu thức, gọi phương thức, xử lý ngoại lệ và đọc output terminal. Chưa cần biết Spring, đa luồng hay chi tiết bộ nhớ JVM.

## 2. Kiểm tra kiến thức tiên quyết

| Nền tảng | Phải làm được | Nếu chưa đạt |
| --- | --- | --- |
| JDK và terminal | Chạy `javac File.java` rồi `java File` | Ôn trình biên dịch, classpath và tệp Java |
| Biến, literal, toán tử | Dự đoán `2 + 3 * 4` và `(2 + 3) * 4` | Ôn thứ tự toán tử và dấu ngoặc |
| Kiểu dữ liệu, chuyển kiểu | Phân biệt `int`, `double`, cast trước/sau chia | Ôn kiểu toán hạng và phép gán |
| Phương thức và `import` | Gọi `Math.max(2, 3)` | Ôn cách gọi static method |
| Rẽ nhánh, vòng lặp | Kiểm tra giá trị âm, lặp qua nhiều input | Ôn điều kiện, phép so sánh |
| Ngoại lệ | Dùng `try/catch` khi parse sai hoặc chia nguyên cho 0 | Ôn `NumberFormatException`, `ArithmeticException` |

### Bài kiểm tra đầu vào có thể tự chạy

Tạo `NumbersPrerequisiteCheck.java`:

```java
public class NumbersPrerequisiteCheck {
    public static void main(String[] args) {
        int items = 7;
        int groups = 2;
        System.out.println(items / groups);
        System.out.println((double) items / groups);
        System.out.println(2 + 3 * 4);
        System.out.println((2 + 3) * 4);
    }
}
```

```bash
javac NumbersPrerequisiteCheck.java
java NumbersPrerequisiteCheck
```

Kết quả:

```text
3
3.5
14
20
```

Nếu kết quả sai dự đoán, giải thích **kiểu tại lúc phép toán diễn ra** và thứ tự ưu tiên trước khi chạy lại. Đây là bài kiểm tra kỹ năng Java nền tảng, không phải bài kiểm tra thuộc lòng bảng miền giá trị.

## 3. Trình tự học tám bài của chương

Menu của chương bắt đầu bằng tình huống thực tế, chuyển sang cơ chế, rồi kết thúc bằng việc dùng JDK đúng phiên bản. Đi theo thứ tự dưới đây khi học lần đầu; nếu đã biết một phần, có thể dùng cột tự kiểm tra để chọn nơi ôn.

| Chặng | Bài trong chương | Câu hỏi tự kiểm tra trước khi đi tiếp |
| --- | --- | --- |
| 1 | Số trong Java là gì và những bài toán cần biểu diễn | Giải thích vì sao mã `"00123"` khác số lượng 123 và giá 123.00 |
| 2 | Phân biệt giá trị toán học, kiểu dữ liệu và cách lưu trữ | Phân tách giá trị, kiểu, bit lưu và chuỗi hiển thị |
| 3 | Số nguyên, số dấu phẩy động và số thập phân chính xác | Dự đoán hành vi chia nguyên, số thực và BigDecimal |
| 4 | Phạm vi giá trị khác độ chính xác như thế nào | Phân biệt overflow với mất độ phân giải ở số thực |
| 5 | Bản đồ primitive, wrapper, Number, BigInteger và BigDecimal | Biết tại sao unboxing null gây lỗi và Number không có toán tử cộng chung |
| 6 | Chọn kiểu theo ý nghĩa dữ liệu và yêu cầu tính toán | Lý giải lựa chọn kiểu cho hóa đơn, bộ đếm, cảm biến và API |
| 7 | Kiến thức tiên quyết và trình tự học đề xuất | Tự đánh giá điểm yếu và lập kế hoạch ôn phần cần thiết |
| 8 | Phạm vi Java Core và cách đối chiếu phiên bản JDK | Kiểm tra xem phương thức có trong Java 17 hay chỉ ra đời ở Java 21 |

**Cách sử dụng bản đồ:** Sau chặng 2, thử tự giải thích một số được hiển thị khác với bit biểu diễn. Sau chặng 3–4, thử tính `0.1 + 0.2` và cộng `1` vào `1e16` để xem sự khác nhau giữa lỗi biểu diễn và độ phân giải. Chặng 5 giúp đọc API nhận `Number`; chặng 6 yêu cầu ra quyết định có hợp đồng dữ liệu; chặng 8 kiểm chứng tương thích khi chuyển dự án sang JDK khác.

## 4. Luyện tập có thứ tự

### Bài A — Đọc biểu thức trước khi chạy

Với `int a = 9, b = 4;`, dự đoán `a / b`, `a % b`, `(double) a / b` và `(double) (a / b)`. Tự xác minh output lần lượt là `2`, `1`, `2.25`, `2.0`. Nếu sai, ôn **chặng 2–3**; nguyên nhân là kiểu toán hạng và thời điểm cast.

### Bài B — Xác định loại sai số

Chạy `Integer.MAX_VALUE + 1`, `16777216f + 1 == 16777216f` và `0.1 + 0.2 == 0.3`. Ba biểu thức lần lượt minh họa tràn số nguyên, độ phân giải float và làm tròn nhị phân. Giải thích sự khác nhau trước khi chọn `Math.addExact`, thay kiểu số hoặc dùng decimal chính xác. Nếu nhầm lẫn, ôn **chặng 3–4**.

### Bài C — Từ yêu cầu đến kiểu dữ liệu

Một đơn hàng có mã `"000045"`, số lượng 3, giá `"19.95"`, thuế 8% và tổng dung lượng xử lý vượt 4 tỷ byte. Hãy đề xuất kiểu Java và xác định:

1. Trường nào là mã định danh, trường nào là đại lượng để tính?
2. Phép toán trung gian nào có thể tràn nếu hai toán hạng đều là `int`?
3. Phải thống nhất scale, đơn vị tiền tệ, `RoundingMode` và thời điểm làm tròn tại đâu?
4. JSON/client và database có giữ nguyên giá trị, đơn vị và các số 0 đầu không?

Nếu không bảo vệ được quyết định, quay về **chặng 5–6**.

### Bài D — Kiểm tra tương thích JDK

Dùng tài liệu API đúng bản phát hành tìm `Math.addExact`, `NumberFormat.getCompactNumberInstance` và `Math.clamp`. Biên dịch ví dụ thích hợp với `javac --release 17`, phân biệt thiếu API với lỗi cú pháp. Nếu chưa biết compiler, runtime và `--release` khác nhau thế nào, ôn **chặng 8**.

## 5. Cách tự học và ghi bằng chứng

Ở mỗi chặng, ghi một bảng ngắn gồm *dự đoán → phép thử → output thật → nguyên nhân → bài cần ôn*. Với phép đo, thử `NaN` và giới hạn sai số; với số nguyên, thử cực trị và phép toán trung gian; với tiền, thử các giá trị ở điểm giữa cần làm tròn; với wrapper, thử `null` tại unboxing. Mục tiêu là giải thích được **vì sao** một kết quả xảy ra và **khi nào** cách xử lý còn phù hợp.

Đánh dấu hoàn thành sau khi tự kiểm tra:

- [ ] Biên dịch/chạy được các ví dụ Numbers độc lập và đọc được lỗi biên dịch so với ngoại lệ runtime.
- [ ] Dự đoán kiểu toán hạng của chia nguyên, promotion, cast và gán kết quả.
- [ ] Nhận biết overflow và dùng `Math.*Exact` ở đúng phép toán nếu cần kiểm tra.
- [ ] Phân biệt range, precision và sai số do biểu diễn floating-point.
- [ ] Giải thích primitive, wrapper, `Number`, `BigInteger`, `BigDecimal` và rủi ro `null`.
- [ ] Chọn kiểu theo miền, đơn vị, phép toán và yêu cầu decimal/rounding cụ thể.
- [ ] Xác minh bảo toàn dữ liệu qua JSON, database, định dạng và locale.
- [ ] Biết tìm đúng phiên bản API và kiểm chứng `javac --release` trên runtime đích.

**Khi đã đạt:** chuyển sang các bài toán API số nâng cao trong module. Nếu còn mục chưa hoàn thành, quay lại đúng chặng và tự tạo một phép thử biên mới; không cần học lại toàn chương chỉ vì một sai lầm nhỏ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Phạm vi Java Core và cách đối chiếu phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi Java Core và cách đối chiếu phiên bản JDK — Java Core: Numbers

## 1. Bài này giải quyết vấn đề gì?

Khi học xử lý số trong Java, dễ gặp hai câu hỏi tưởng đơn giản: “`BigDecimal` có phải thư viện ngoài không?” và “Ví dụ dùng `Math.clamp` có chạy trên JDK 17 không?”. Muốn trả lời, phải phân biệt **phạm vi kiến thức Java Core**, **API chuẩn**, **phiên bản JDK dùng để biên dịch**, **phiên bản Java nơi chương trình chạy** và **phiên bản mà dự án cam kết hỗ trợ**.

Bài này tập trung vào **Java Core — Numbers**. Mục tiêu là giúp bạn:

1. Biết những thành phần xử lý số nào thuộc cú pháp ngôn ngữ Java, thư viện chuẩn `java.base` hoặc các API chuẩn khác; không nhầm với thư viện của ứng dụng.
2. Xác định API/phương thức tồn tại từ Java nào bằng tài liệu phiên bản cụ thể, đặc biệt các mục `Since`, `Deprecated` và `Preview`.
3. Hiểu khác nhau giữa khả năng biên dịch mã nguồn, khả năng gọi API, phiên bản bytecode và khả năng chạy thực tế.
4. Tự kiểm tra với `java`, `javac` và `javac --release` trước khi kết luận mã chạy được trên JDK đích.
5. Xây dựng quy trình đối chiếu phiên bản có thể áp dụng cho mọi bài Numbers tiếp theo.

**Giới hạn phạm vi:** đây là bài định hướng và đối chiếu phiên bản, không phải tài liệu tra cứu mọi phép toán số hay bảng liệt kê toàn bộ thay đổi của từng bản JDK. Các phiên bản 8, 11, 17 và 21 được dùng làm **mốc kiểm tra**; chúng không có nghĩa là chỉ tồn tại bốn bản Java hoặc phiên bản mới nhất của Java luôn là 21.

## 2. Năm khái niệm cần phân biệt

| Thuật ngữ | Ý nghĩa thực hành | Ví dụ liên quan Numbers |
| --- | --- | --- |
| **Java (ngôn ngữ)** | Cú pháp, kiểu primitive, toán tử, quy tắc chuyển kiểu được quy định bởi Java Language Specification | `int`, `double`, `+`, `/`, numeric promotion |
| **Java SE (nền tảng chuẩn)** | Bộ API, mô hình thực thi và đặc tả chuẩn theo một bản phát hành | `java.lang.Math`, `java.math.BigDecimal`, `java.text.NumberFormat` |
| **JDK (Java Development Kit)** | Bộ công cụ phát triển gồm trình biên dịch, công cụ tài liệu và môi trường chạy | `javac`, `java`, `javadoc`, `jshell` (từ JDK 9) |
| **JVM/runtime** | Môi trường tải, xác minh và thực thi bytecode; một ứng dụng còn phụ thuộc API/runtime có sẵn | Chạy `NumbersDemo.class`; có thể gặp `UnsupportedClassVersionError` hoặc `NoSuchMethodError` |
| **Bản phân phối JDK** | Một bản dựng của Java từ nhà cung cấp cụ thể, theo version, bản vá và chính sách hỗ trợ của họ | OpenJDK build, Oracle JDK, Eclipse Temurin hoặc bản dựng khác |

**Java Core** là cách gọi trong học tập cho kiến thức nền của Java và thư viện chuẩn thường dùng, không phải tên một module Java SE độc lập. Từ Java 9, Java SE có hệ thống module; nhiều lớp xử lý số phổ biến nằm trong module **`java.base`**, được mọi module Java đọc ngầm.

**JRE** từng được phân phối riêng trong nhiều dòng Java cũ; với các bản JDK hiện đại, khi nói “runtime” nên xác nhận đúng bộ chạy thực tế thay vì giả định máy nhất thiết có một bản cài JRE độc lập.

Để so sánh tính khả dụng của một API, luôn ghi **số phiên bản tính năng (feature release)**, ví dụ 17 hay 21, rồi mới xét cập nhật vá lỗi như 17.0.x hoặc 21.0.x. Một bản cập nhật vá lỗi và một feature release mới không có cùng ý nghĩa tương thích.

## 3. Bản đồ Numbers trong phạm vi Java Core

### 3.1. Phần thuộc ngôn ngữ Java

Các kiểu primitive `byte`, `short`, `int`, `long`, `float` và `double`; literal như `42`, `42L`, `1.5F`; toán tử `+`, `-`, `*`, `/`, `%`; ép kiểu và quy tắc nâng kiểu số nằm ở **ngôn ngữ Java**. Không cần cài thư viện số bên ngoài để sử dụng chúng.

Những quy tắc cơ bản cần kiểm tra theo **Java Language Specification (JLS)** của phiên bản mục tiêu, chẳng hạn cách tính `7 / 2`, tràn `int` và sự khác biệt giữa số thực gần đúng với số thập phân chính xác. Phần lớn nền tảng này ổn định qua nhiều phiên bản, nhưng không vì vậy mà bỏ qua những thay đổi đặc tả về xử lý dấu phẩy động hoặc tính năng ngôn ngữ khác.

### 3.2. Các lớp/thư viện chuẩn thường gặp

| Nhu cầu | Các API tiêu biểu | Thuộc đâu? |
| --- | --- | --- |
| Hằng số và phép toán toán học cơ bản | `Math`, `StrictMath` | `java.lang` / `java.base` |
| Bao kiểu và xử lý số nguyên | `Number`, `Integer`, `Long`, `Short`, `Byte` | `java.lang` / `java.base` |
| Bao kiểu số thực và kiểm tra trạng thái | `Float`, `Double` | `java.lang` / `java.base` |
| Số nguyên lớn | `BigInteger` | `java.math` / `java.base` |
| Số thập phân, độ chính xác, làm tròn | `BigDecimal`, `MathContext`, `RoundingMode` | `java.math` / `java.base` |
| Parse/định dạng theo vùng | `NumberFormat`, `DecimalFormat`, `DecimalFormatSymbols` | `java.text` / `java.base` |
| Quy định vùng/ngôn ngữ | `Locale` | `java.util` / `java.base` |
| Sinh số giả ngẫu nhiên | `Random`, `SplittableRandom`; `RandomGenerator` từ JDK 17 | `java.util` và `java.util.random` / `java.base` |
| Dạng số làm đầu vào/đầu ra | `Scanner`, `Formatter` | `java.util` / `java.base` |

`java.lang` được import tự động. Các package khác như `java.math`, `java.text`, `java.util` cần import hoặc dùng tên đầy đủ.

Ví dụ, `BigDecimal` là API tiêu chuẩn của Java SE. Cú pháp `import java.math.BigDecimal;` không thể hiện phụ thuộc Maven hoặc Gradle ngoài JDK. Ngược lại, một kiểu số do một thư viện bên thứ ba định nghĩa phải tra tài liệu/phiên bản **của chính thư viện đó**, không gán phiên bản ra đời của nó cho Java SE.

### 3.3. Những gì nằm ngoài bài

JNI, Vector API ươm thử (incubator), cơ chế JVM nội bộ, thuật toán toán học chuyên ngành, SQL/JPA mapping và thư viện số khoa học là các chủ đề liên quan nhưng **không phải điều kiện bắt buộc** để hiểu phạm vi Numbers của Java Core. Chỉ mở rộng khi bài toán cụ thể yêu cầu.

## 4. “Chạy trên Java X” thực ra có những lớp tương thích nào?

Một câu “đoạn code chạy trên Java 17” là chưa đủ chính xác nếu chưa xác định:

1. **Source compatibility:** trình biên dịch có chấp nhận cú pháp/ngữ nghĩa của phiên bản ngôn ngữ mục tiêu không? Một tính năng cú pháp phát hành sau Java 17 sẽ không thể dùng trong chế độ source 17.
2. **API compatibility:** tất cả lớp và phương thức có trong Java SE API của phiên bản đích không? Mã cú pháp cũ vẫn có thể gọi một API chỉ ra đời ở Java 21.
3. **Binary/class-file compatibility:** class được biên dịch ra phiên bản bytecode nào; runtime đích có đọc được không?
4. **Runtime behavior:** API có thể tồn tại nhưng dữ liệu locale, provider, triển khai, cấu hình, hành vi được đặc tả hoặc lỗi sửa trong bản vá có thể ảnh hưởng kết quả.
5. **Support/security:** JDK của nhà cung cấp còn nhận cập nhật bảo mật hay không và bản cụ thể nào được tổ chức hỗ trợ? Đây là quyết định vận hành, không suy ra từ việc code biên dịch được.

Ví dụ: build một `.class` bằng JDK 21 ở mặc định rồi đưa cho runtime Java 17 thường vướng class-file version mới hơn. Nhưng ngay cả nếu ép class-file target về 17, mã vẫn có thể vô tình tham chiếu API chỉ có trong 21 nếu không kiểm soát API khi biên dịch.

### 4.1. Quy tắc chiều tương thích

- JDK mới thường có thể **biên dịch mã nhắm phiên bản cũ được hỗ trợ** qua `--release`. Mức hỗ trợ cụ thể phải xem `javac --help` của bản đang cài.
- JDK cũ **không thể** biên dịch source/API được đưa vào ở phiên bản tương lai.
- Runtime cũ không được bảo đảm chạy class file sinh cho Java mới hơn.
- Runtime mới có mức tương thích ngược rất cao nhưng không phải cam kết mọi chương trình cũ đều không cần điều chỉnh; thay đổi API đã loại bỏ, module encapsulation, phụ thuộc thư viện, môi trường và thiết lập có thể gây lỗi.

**Tuyên bố chính xác** nên là: “Được biên dịch bằng JDK 21 với `--release 17`, chỉ dùng Java SE API nằm trong Java 17, và đã chạy thử trên JDK 17 được chỉ định.” Tuyên bố này có bằng chứng rõ hơn “viết bằng Java 21 nhưng chạy mọi nơi”.

## 5. Tra được phiên bản thực tế trước khi học hoặc sửa code

Trên terminal, chạy:

```bash
java -version
javac -version
javac --help
```

- `java -version` cho biết bản runtime được chọn bởi lệnh `java` hiện tại.
- `javac -version` cho biết bản compiler được chọn; không nên mặc định hai lệnh cùng trỏ tới một JDK khi máy có nhiều bản Java.
- `javac --help` cho phép kiểm tra bản compiler chấp nhận các giá trị `--release` nào.
- Từ JDK 9, `java --list-modules` có thể liệt kê các module của runtime hiện tại, bao gồm `java.base`.

Nếu kết quả khác dự kiến, kiểm tra đường dẫn công cụ và cấu hình của terminal, IDE, Gradle hoặc CI:

```powershell
# Windows PowerShell
Get-Command java
Get-Command javac
```

```bash
# Linux/macOS
command -v java
command -v javac
```

Lệnh trên giúp xác định **lệnh nào được shell tìm thấy**. Với dự án sử dụng IDE, Gradle toolchain, Maven toolchains, container hoặc CI, cần kiểm tra thêm cấu hình từng công cụ vì compiler/runtime mà build dùng có thể khác kết quả shell.

Trong ứng dụng Java, có thể truy vấn:

```java
System.out.println(System.getProperty("java.version"));
System.out.println(Runtime.version().feature());
```

Hai dòng này chỉ là **mảnh mã** chạy trong một phương thức; `Runtime.version()` có từ Java 9. Đầu ra phụ thuộc runtime, nên không ghi cố định là “17” hoặc “21” nếu chưa kiểm tra. Thông tin build JDK và runtime JDK nên được ghi nhận riêng khi tái hiện lỗi.

## 6. Dùng tài liệu theo đúng phiên bản: phương pháp đối chiếu

Đừng chỉ tìm “Java Math docs” rồi mở trang tìm kiếm đầu tiên. Một trang API của JDK mới có thể liệt kê phương thức mà bản đích chưa có. Hãy làm theo thứ tự:

1. Chốt **JDK đích**, ví dụ Java SE 17; ghi cả phiên bản dùng để build nếu khác.
2. Mở **API Specification** của đúng version, vào package/lớp/phương thức có liên quan. Ví dụ `java.lang.Math`, `java.math.BigDecimal`, `java.text.NumberFormat`.
3. Đọc **Since** ở phương thức, constructor hoặc lớp để biết lần đầu được giới thiệu. Kiểm tra cả **overload**: tên phương thức đã tồn tại từ trước không đồng nghĩa mọi chữ ký đều có từ trước.
4. Đọc **Deprecated** (đặc biệt `forRemoval`), **Preview**, **API Note**, **Throws**, lưu ý hành vi các giá trị biên và điều kiện rỗng/null.
5. Nếu API có trong JDK đích, kiểm tra lại bằng `javac --release <đích>`; nếu không có, cân nhắc giải pháp API cũ hoặc tăng minimum JDK một cách có chủ đích.
6. Khi nâng JDK, đọc **release notes** và **migration guide**, rồi thực thi bài kiểm thử trên đúng runtime triển khai; tài liệu API không thay thế kiểm chứng thực tế.

Các địa chỉ gốc hữu ích (nên mở và chuyển sang đúng phiên bản khi cần):

- [Java SE 8 API](https://docs.oracle.com/javase/8/docs/api/) — mốc đối chiếu các API lâu đời.
- [Java SE 11 API](https://docs.oracle.com/en/java/javase/11/docs/api/) — so sánh với hệ API trước JDK 12.
- [Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/) — mốc của các ví dụ Java 17+.
- [Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/) — kiểm tra API và phương thức bổ sung ở 21.
- [Java Language Specification](https://docs.oracle.com/javase/specs/) — quy tắc kiểu, toán tử và ngôn ngữ.
- [JDK Enhancement Proposals (JEP)](https://openjdk.org/jeps/0) — ngữ cảnh và trạng thái các thay đổi lớn.

**Đọc đúng cấp:** JLS dùng để xác minh phép `int / int` và chuyển kiểu; API docs dùng để xác minh `Math.addExact` hoặc `NumberFormat.getCompactNumberInstance`; release notes/migration docs dùng để điều tra hành vi bị ảnh hưởng khi nâng JDK.

## 7. Các mốc phiên bản minh họa trực tiếp bằng Numbers

Bảng sau tập trung vào một vài API kiểm chứng được, **không phải danh sách thay đổi đầy đủ** của mỗi phiên bản.

| Mốc | Ví dụ thuộc Numbers | Từ phiên bản | Ý nghĩa khi đối chiếu |
| --- | --- | --- | --- |
| Java 8 | `Math.addExact(int, int)`, `Integer.compareUnsigned` | 8 | Khi đích thấp hơn 8, không thể giả định các API này tồn tại |
| Java 9 | `Math.multiplyHigh(long, long)` | 9 | Phải kiểm tra `Since` của đúng overload |
| Java 12 | `NumberFormat.getCompactNumberInstance(...)`, `CompactNumberFormat` | 12 | Có cú pháp Java 11 hợp lệ vẫn không được dùng API này nếu target 11 |
| Java 17 | `java.util.random.RandomGenerator` và họ API random mới | 17 | Phải kiểm tra version khi đổi từ `Random` truyền thống sang API mới |
| Java 17 | JEP 306 khôi phục floating-point semantics luôn strict | 17 | Đối chiếu số thực không chỉ là tìm API mới |
| Java 21 | `Math.clamp(...)` | 21 | Phương thức không sẵn có trong Java 17; cần target 21 hoặc tự viết logic tương đương |

**Một lỗi dễ gặp:** nhìn thấy `Math` có từ lâu rồi cho rằng `Math.clamp` dùng được trên Java 8. Tuổi đời **lớp** và **phương thức** là hai thông tin khác nhau.

**Lưu ý số thực:** JEP 306 ở Java 17 làm phép toán dấu phẩy động tuân theo ngữ nghĩa strict thống nhất, thay cho phân biệt strict/default trước đó. Điều đó không làm `double` trở thành số thập phân chính xác. Với mã phụ thuộc kết quả dấu phẩy động hay hiệu năng, cần đọc đặc tả và chạy kiểm thử độ chính xác trên các runtime liên quan.

**Lưu ý sinh số ngẫu nhiên:** `RandomGenerator` cung cấp giao diện/thuật toán giả ngẫu nhiên mới từ 17; không mặc nhiên dùng nó cho mật mã. Khi cần số ngẫu nhiên phục vụ bảo mật, dùng `SecureRandom` theo hợp đồng bảo mật phù hợp.

## 8. Ví dụ A — Những phép tính Numbers cơ bản chạy với JDK 17+

Ví dụ minh họa các API tiêu chuẩn cũ, không phụ thuộc tính năng mới của JDK 21. Lưu tệp **`NumbersBase.java`**:

```java
import java.math.BigDecimal;

public class NumbersBase {
    public static void main(String[] args) {
        int total = 7;
        int groups = 2;

        System.out.println(total / groups);
        System.out.println((double) total / groups);
        System.out.println(Math.addExact(20, 22));

        BigDecimal exact = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        System.out.println(exact);
    }
}
```

Chạy trên JDK 17 trở lên:

```bash
javac NumbersBase.java
java NumbersBase
```

Kết quả:

```text
3
3.5
42
0.3
```

Giải thích:

- `int / int` bỏ phần thập phân; khi ép một toán hạng sang `double` **trước phép chia**, kết quả là `3.5`.
- `Math.addExact` thực hiện phép cộng số nguyên có kiểm tra tràn. `20 + 22` nằm trong miền `int` nên trả `42`.
- `BigDecimal` khởi tạo từ **chuỗi thập phân** cho phép cộng thập phân chính xác ở ví dụ này.
- Kết quả dựa trên quy tắc số học chuẩn; sự tồn tại của API cần đối chiếu từ tài liệu phiên bản tương ứng, không dựa vào việc dùng JDK mới để biên dịch mặc định.

Nếu chương trình này cần tương thích Java 17 khi build trên JDK 21, sử dụng:

```bash
javac --release 17 NumbersBase.java
java NumbersBase
```

`--release 17` yêu cầu compiler biên dịch theo quy tắc ngôn ngữ, tạo class file và giới hạn Java SE/JDK API theo bản 17. Dòng `java NumbersBase` ở trên chạy bằng **runtime được terminal chọn**, nên vẫn cần thử trên JDK 17 thật nếu đó là bản phải hỗ trợ.

## 9. Ví dụ B — API ra đời sau bản đích: compact number formatting

Tạo tệp **`CompactNumbers.java`**:

```java
import java.text.NumberFormat;
import java.util.Locale;

public class CompactNumbers {
    public static void main(String[] args) {
        NumberFormat format = NumberFormat.getCompactNumberInstance(
                Locale.US, NumberFormat.Style.SHORT);
        System.out.println(format.format(1200));
    }
}
```

`NumberFormat.getCompactNumberInstance` được giới thiệu từ **Java 12**. Vì vậy với compiler JDK 17 hoặc 21 hỗ trợ target 11:

```bash
javac --release 11 CompactNumbers.java
```

Lệnh **phải báo lỗi biên dịch** do phương thức compact formatting không tồn tại trong API Java 11; wording/line number của lỗi phụ thuộc phiên bản và cách chạy `javac`. Tiếp theo:

```bash
javac --release 17 CompactNumbers.java
java CompactNumbers
```

Mã có thể biên dịch vì Java 17 chứa API này. Chuỗi compact được in phụ thuộc dữ liệu locale và phiên bản runtime, nên không lấy một chuỗi đầu ra quan sát trên máy này làm hợp đồng bất biến của mọi JDK. Nếu dự án bắt buộc Java 11, hãy chọn `NumberFormat` kiểu cũ hoặc tự định nghĩa quy tắc viết gọn một cách có kiểm thử; đừng chỉ thay `--target`.

### Tại sao `--source` và `--target` riêng lẻ chưa đủ?

- `--source 11` xét **quy tắc cú pháp/ngôn ngữ** của Java 11.
- `--target 11` chọn **định dạng class file** cho phiên bản đích.
- Hai tùy chọn này **không tự bảo đảm** mọi Java SE API đang được tham chiếu đều tồn tại ở Java 11 nếu dùng classpath/platform API hiện tại.
- `--release 11` kết hợp cả ba ý: cú pháp, class file và Java SE/JDK API được tài liệu hóa của phiên bản 11. Không được kết hợp `--release` với `--source` hay `--target` trong cùng lời gọi.

Do đó, đối với dự án cần chạy trên bản Java cũ, **`--release` là điểm xuất phát phù hợp**. Vẫn cần rà các thư viện phụ thuộc và kiểm thử với runtime đích thật.

## 10. Ví dụ C — Nhìn đúng `Since` của phương thức JDK 21

Tạo tệp **`ClampNumbers.java`**:

```java
public class ClampNumbers {
    public static void main(String[] args) {
        System.out.println(Math.clamp(150, 0, 100));
        System.out.println(Math.clamp(-5, 0, 100));
    }
}
```

Các overload `Math.clamp` có từ **Java 21**. Chạy với JDK 21:

```bash
javac --release 21 ClampNumbers.java
java ClampNumbers
```

Kết quả:

```text
100
0
```

Nếu compiler hỗ trợ `--release 17`, chạy `javac --release 17 ClampNumbers.java` sẽ báo không tìm thấy phương thức tương ứng trong API 17. Đó là lỗi **API-version**, không có nghĩa phép so sánh giới hạn số là bất khả thi trên Java 17.

Trường hợp phải hỗ trợ Java 17, có thể viết hàm tương đương cho miền dữ liệu cụ thể:

```java
public static int clampInt(int value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min > max");
    }
    return Math.max(min, Math.min(max, value));
}
```

Đoạn trên là **phương thức minh họa**, đặt trong một class trước khi gọi; nó không thay thế mọi overload và mọi chi tiết hợp đồng của `Math.clamp` (đặc biệt số thực, `NaN`, signed zero). Đây chính là lý do phải đọc **chữ ký và hợp đồng** trước khi tự viết bản thay thế.

## 11. Bytecode: tại sao đã biên dịch vẫn có thể không chạy?

Một source file có thể compile thành class file ở phiên bản mới hơn runtime đích. Các mốc **class-file major version** thường thấy:

| Java release | Class-file major |
| --- | ---: |
| 8 | 52 |
| 11 | 55 |
| 17 | 61 |
| 21 | 65 |

Khi lớp được biên dịch theo Java 21 (major 65), runtime Java 17 (hỗ trợ đến major 61) không thể tải nó. Thông báo thường có `UnsupportedClassVersionError`. Đây là lỗi về **mức bytecode**, khác với thiếu một class hoặc method ở runtime.

Có thể xem thông tin class file bằng:

```bash
javap -verbose NumbersBase
```

Tìm dòng `major version: ...` trong kết quả. Lệnh `javap` được cung cấp cùng bộ công cụ JDK. Cách kiểm tra thực tế:

1. Biên dịch bằng compiler dự án với `--release` đặt đúng target.
2. Kiểm tra major version nếu cần chứng minh artifact phù hợp target.
3. Chạy bài test bằng Java runtime thực sự triển khai.
4. Đảm bảo mọi dependency dùng class file và API được runtime đích hỗ trợ.

`--release` điều khiển compilation của **source được biên dịch trong lời gọi đó**; nó không tự dịch lại JAR phụ thuộc đã build bằng JDK mới hoặc khắc phục khác biệt của provider ngoài Java SE.

## 12. Các khác biệt dễ bỏ sót dù chương trình vẫn compile

### 12.1. Hành vi so sánh và precision

Một phép toán `double` có thể cho kết quả gần đúng vì biểu diễn nhị phân. Thay đổi JDK không tự chữa `0.1 + 0.2 == 0.3`. Đặt tolerance khi đo đạc, hoặc dùng `BigDecimal` và quy tắc scale/rounding khi cần số thập phân có hợp đồng chính xác. Những phép toán có tính nhạy số học nên có test với dữ liệu biên trên các JDK được hỗ trợ.

### 12.2. Locale và dữ liệu định dạng

`NumberFormat`, `DecimalFormat`, dữ liệu locale/CLDR và nhà cung cấp dịch vụ locale có thể ảnh hưởng cách hiển thị cùng một số giữa các môi trường. Không viết test so sánh tuyệt đối chuỗi “1.234,50” trên máy này với chuỗi từ mọi locale khác.

Muốn kết quả dự đoán được, truyền `Locale` cụ thể, thống nhất currency, quy tắc rounding và dữ liệu provider; tránh vô tình dùng locale mặc định. Dù đã cố định locale, dữ liệu/nguyên tắc định dạng có thể được cập nhật giữa JDK; test nào phụ thuộc từng ký tự cần nêu rõ phiên bản runtime và yêu cầu nghiệp vụ.

### 12.3. Deprecation, Preview, Incubator, API nội bộ

- Một API được gắn `@Deprecated` có thể **vẫn compile** trên bản hiện tại, nhưng cần xem lý do, ngày giới thiệu cảnh báo và có đánh dấu “for removal” không.
- **Preview feature/API** có điều kiện kích hoạt riêng và có thể đổi hoặc bị loại bỏ ở bản sau. Đừng dựa vào preview cho bài Numbers cơ bản trừ khi thực sự phải thử tính năng đó.
- **Incubator module** không đồng nghĩa API chuẩn ổn định của `java.base`; kiểm tra trạng thái cụ thể ở version sử dụng.
- `sun.*`, `jdk.internal.*` không phải bề mặt API chuẩn nên tránh dựa vào chúng chỉ để thực hiện phép toán số thường ngày.

### 12.4. Khác nhau giữa version và nhà cung cấp

Cùng Java SE feature release nhưng vendor/build/update khác nhau có thể mang bản vá, dữ liệu hệ thống hoặc lịch hỗ trợ khác. Khi tái hiện lỗi, ghi nguyên đầu ra `java -version`, bản OS/architecture, cờ JVM cần thiết và dữ liệu input. Khi quyết định nâng cấp JDK, đối chiếu chính sách hỗ trợ của **nhà cung cấp đang dùng**, không suy ra từ nhãn LTS chung chung.

## 13. Quy trình 7 bước để kiểm tra một đoạn code Numbers bất kỳ

Giả sử bạn thấy đoạn `Math.clamp(amount, 0, 100)` trong tài liệu hoặc trên diễn đàn.

**Bước 1 — Ghi môi trường đích.** Ví dụ: dự án biên dịch trên JDK 21 nhưng sản phẩm phải chạy trên Java 17; ghi rõ 17 là **minimum runtime**.

**Bước 2 — Tách cú pháp với lời gọi API.** `amount` là `int`, lời gọi `Math.clamp` thuộc thư viện chuẩn; vấn đề có thể ở phương thức, không phải toán tử hoặc cú pháp Java.

**Bước 3 — Tìm đúng chữ ký.** Mở API `Math` của Java 17 và 21, đối chiếu tên, parameter types, overload, `Since` và `Throws`.

**Bước 4 — Thử biên dịch theo target.** Trên compiler mới có hỗ trợ target 17, chạy `javac --release 17 ...`. Nếu báo lỗi, đọc lỗi để phân biệt thiếu API, lỗi kiểu, lỗi tên class và lỗi cú pháp.

**Bước 5 — Chọn hành động.** Nếu API có từ 21, hoặc nâng mức tối thiểu runtime **theo quyết định của dự án**, hoặc thay bằng logic tương thích 17 và test ranh giới.

**Bước 6 — Test runtime thực.** Kiểm tra class-file major và chạy test trên JDK 17 đã cam kết. Với dữ liệu số, bao gồm 0, số âm, min/max, sai số, `NaN`/vô cực (nếu nhận `double`), scale/rounding và locale khi liên quan.

**Bước 7 — Ghi lại bằng chứng.** Một mô tả có thể lặp lại gồm code, target release, compile JDK, runtime JDK, lệnh test, output và nguồn tài liệu API; đừng ghi đơn thuần “hỗ trợ Java 17” khi mới chỉ nhìn mã.

Ví dụ báo cáo kỹ thuật đủ rõ:

```text
Mục tiêu: chạy trên Java 17
Build JDK: 21
Compiler flags: javac --release 17
API kiểm tra: Math.addExact(int,int) - Since 8
Kiểm tra runtime: test trên JDK 17 của môi trường triển khai
Rủi ro còn lại: dependency ngoài JDK và provider/locale cần test riêng
```

Đây là **mẫu báo cáo**, không phải kết quả kiểm thử đã thực hiện cho một dự án có thật.

## 14. Các câu hỏi tự kiểm tra

1. `BigInteger` và `BigDecimal` có phải cài thêm dependency Maven không? **Không.** Đây là API chuẩn trong `java.math`.
2. Tại sao code gọi `NumberFormat.getCompactNumberInstance` chạy trên JDK 17 nhưng bị lỗi khi biên dịch với `--release 11`? **Vì API này được giới thiệu từ Java 12**, không nằm trong API đích 11.
3. Dùng JDK 21, compile `--source 17 --target 17` có tự ngăn tham chiếu method chỉ có trong 21 không? **Không bảo đảm**; dùng `--release 17` để giới hạn cả API.
4. Class file major 65 chạy trên Java 17 được không? **Không**; Java 17 hỗ trợ class file đến major 61.
5. `Math` có từ Java lâu rồi, vậy `Math.clamp` có trong Java 17? **Không**; kiểm tra `Since 21` của overload tương ứng.
6. Hai JDK cùng Java 17 có thể khác dữ liệu locale hoặc bản vá không? **Có thể**; cần ghi vendor/build/update và thử trong môi trường thực tế.
7. Chỉ cần biên dịch được là đủ khẳng định “chạy trên Java 17”? **Chưa.** Phải đối chiếu APIs, class file, dependencies và runtime.
8. Khi học Numbers, đâu là điểm xuất phát phù hợp? **Bám JDK mục tiêu**, luyện primitives/`Math`/wrapper/`BigDecimal` theo API tương ứng, sau đó mới thử các tính năng mới hơn.

## 15. Checklist khi đọc mỗi bài Java Core — Numbers tiếp theo

- [ ] Tôi biết **minimum supported Java release** của bài tập/dự án.
- [ ] Tôi đã tách cú pháp ngôn ngữ với lớp/phương thức thuộc Java SE API.
- [ ] Tôi mở đúng tài liệu **API của phiên bản đích**, không chỉ trang JDK mới nhất.
- [ ] Tôi kiểm tra `Since` trên chính **phương thức/overload** đang dùng, cùng `Throws`, `Deprecated` và trạng thái preview nếu có.
- [ ] Tôi biết compiler JDK và runtime JDK hiện hành, không nhầm cài đặt IDE/CI/shell.
- [ ] Tôi dùng `--release` khi biên dịch theo bản đích cũ được compiler hỗ trợ.
- [ ] Tôi thử chạy trên runtime đích, rà cả phụ thuộc bên ngoài và class-file version.
- [ ] Tôi kiểm tra kết quả số, overflow, precision, rounding, locale và dữ liệu biên khi có liên quan.
- [ ] Tôi lưu lại phiên bản, lệnh và kết quả để người khác có thể tái hiện.

**Tài liệu tham khảo chính thức** để tự xác minh: [JDK 17 Math API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html), [JDK 21 Math API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html), [JDK 17 BigDecimal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [JDK 12 NumberFormat API](https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/text/NumberFormat.html), [JDK 17 random package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html), [javac command specification](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html) và [JDK 17 migration notes](https://docs.oracle.com/en/java/javase/17/migrate/significant-changes-jdk-release.html).

</details>

- [Quay lại đầu trang](#back-to-top)
