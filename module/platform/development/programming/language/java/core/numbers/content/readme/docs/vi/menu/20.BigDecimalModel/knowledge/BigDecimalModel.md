<a id="back-to-top"></a>

# Mô hình BigDecimal

## Menu
- [Số thập phân độ chính xác tùy ý và tính bất biến](#big-decimal-model-001)
- [Unscaled value, scale và công thức xác định giá trị](#big-decimal-model-002)
- [Precision khác scale](#big-decimal-model-003)
- [Scale bằng 0, dương hoặc âm](#big-decimal-model-004)
- [Tạo từ String, BigInteger, long và unscaled value](#big-decimal-model-005)
- [Constructor nhận double và nhiễu biểu diễn nhị phân](#big-decimal-model-006)
- [valueOf và các hằng ZERO, ONE, TEN](#big-decimal-model-007)
- [Giữ dấu 0 cuối trong biểu diễn có ý nghĩa](#big-decimal-model-008)
- [BigDecimal không có NaN hoặc vô cực](#big-decimal-model-009)
- [Khi nào dùng BigDecimal thay float hoặc double](#big-decimal-model-010)

## <a id="big-decimal-model-001">Số thập phân độ chính xác tùy ý và tính bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Số thập phân độ chính xác tùy ý và tính bất biến của BigDecimal trong Java

## 1. BigDecimal giải quyết vấn đề gì?

Các kiểu **float** và **double** lưu số theo hệ dấu phẩy động **nhị phân**. Không phải mọi phân số thập phân đều biểu diễn chính xác trong hệ nhị phân hữu hạn. Kết quả ví dụ quen thuộc:

~~~java
double a = 0.1;
double b = 0.2;
System.out.println(a + b); // 0.30000000000000004
~~~

Đây là hành vi đúng của kiểu double, không phải lỗi JVM. Khi ứng dụng yêu cầu tính **thập phân chính xác theo những quy tắc đã định**, Java cung cấp lớp **java.math.BigDecimal**.

~~~java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");

System.out.println(a.add(b)); // 0.3
~~~

BigDecimal có đặc tính:

- **Biểu diễn thập phân dựa trên một số nguyên tùy ý độ dài và scale**, thay vì significand nhị phân 53 bit.
- **Immutable**: sau khi tạo, đối tượng không đổi qua các phép toán. Phép tính trả về giá trị kết quả mới.
- **Các phép toán chính xác** khi có thể biểu diễn kết quả thập phân hữu hạn trong giới hạn tài nguyên và scale.
- **Có kiểm soát rounding** qua RoundingMode, MathContext, hoặc setScale.
- **Có nhiều cách so sánh** tùy cần bằng nhau về giá trị số hay cả về scale.

BigDecimal hữu ích trong tài chính, thuế, giá cả, tính tỷ lệ thập phân, nhập liệu chữ số do người dùng cung cấp và các giao thức yêu cầu giữ chữ số thập phân.

**Arbitrary precision không có nghĩa chính xác vô hạn cho mọi số thực**: phép chia 1/3 không có biểu diễn thập phân hữu hạn; BigDecimal phải làm tròn theo chính sách được cung cấp hoặc ném exception. Nó cũng không có tài nguyên vô hạn: scale là int, còn độ dài số nguyên và phép toán chịu giới hạn triển khai, CPU và bộ nhớ.

## 2. Cấu trúc: unscaled value và scale

Một BigDecimal biểu diễn giá trị toán học qua công thức:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

- **unscaledValue**: số nguyên có dấu; phương thức unscaledValue() trả BigInteger.
- **scale**: số nguyên kiểu int, có thể dương, bằng 0 hoặc âm.
- **precision**: số chữ số thập phân của unscaled value khi bỏ dấu; zero có precision bằng 1.

Ví dụ:

~~~java
BigDecimal n = new BigDecimal("123.4500");

System.out.println(n.unscaledValue()); // 1234500
System.out.println(n.scale());         // 4
System.out.println(n.precision());     // 7
System.out.println(n.toPlainString()); // 123.4500
~~~

Giá trị chính xác là 1234500 × 10^−4 = 123.4500.

| Chuỗi khởi tạo | Unscaled value | Scale | Giá trị số |
| --- | ---: | ---: | --- |
| "123.4500" | 1234500 | 4 | 123.45 |
| "12.34" | 1234 | 2 | 12.34 |
| "12" | 12 | 0 | 12 |
| "1E+3" | 1 | −3 | 1000 |
| "0.001" | 1 | 3 | 0.001 |

**Scale và precision là hai khái niệm khác nhau.** Với 123.4500, scale=4, precision=7; với 1E+3, scale=−3, precision=1. BigDecimal không giới hạn scale chỉ là số chữ số phía phải dấu thập phân; scale **âm** là hợp lệ.

### Scale âm trong thực tế

~~~java
BigDecimal x = new BigDecimal("1E+3");
System.out.println(x.scale());         // -3
System.out.println(x.toString());      // 1E+3
System.out.println(x.toPlainString()); // 1000
~~~

Mẫu số mũ thập phân này vẫn là giá trị **1000** chính xác. Nhưng khi parse chuỗi plain "1000" trở lại, đối tượng mới có thể có scale 0, khác scale −3 của giá trị ban đầu. Do đó không mặc định toPlainString duy trì đầy đủ cách biểu diễn khi round-trip.

### Độ chính xác tùy ý vẫn có giới hạn

BigDecimal có thể xử lý hàng nghìn chữ số mà double không thể lưu chính xác, nhưng:

1. Scale thuộc miền int (không thể là mọi số nguyên không giới hạn).
2. Unscaled integer cần heap và có giới hạn thực tế/triển khai.
3. Nhân, chia và nâng lũy thừa số rất lớn tiêu tốn thời gian và tài nguyên.
4. Một MathContext có precision hữu hạn **cho phép làm tròn có chủ đích**.

Nếu dữ liệu từ người dùng có thể cực dài, hãy kiểm tra số chữ số, scale, độ phức tạp phép toán và giới hạn tài nguyên trước khi xử lý.

## 3. Cách tạo BigDecimal không bị mất chữ số

Để biểu diễn một **giá trị thập phân mà bạn chủ ý nhập**, ưu tiên chuỗi thập phân:

~~~java
BigDecimal price = new BigDecimal("19.99");
BigDecimal taxRate = new BigDecimal("0.075");
BigDecimal quantity = BigDecimal.valueOf(3);
~~~

Constructor String chuyển văn bản thành BigDecimal mà không đi qua double.

### Bẫy của new BigDecimal(double)

~~~java
BigDecimal fromBinaryDouble = new BigDecimal(0.1);
BigDecimal expected = new BigDecimal("0.1");

System.out.println(fromBinaryDouble.equals(expected)); // false
~~~

Nguyên nhân: literal double **0.1** không phải chính xác 1/10 về mặt biểu diễn nhị phân. **new BigDecimal(double)** mô tả chính xác giá trị nhị phân xấp xỉ mà double chứa, dẫn đến nhiều chữ số thập phân.

**BigDecimal.valueOf(0.1)** sử dụng văn bản thập phân tương ứng với Double.toString của double:

~~~java
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
System.out.println(fromValueOf.toPlainString()); // 0.1
~~~

Phương thức valueOf(double) thường phù hợp hơn constructor double nếu phải nhận giá trị double đã có và muốn chuyển theo biểu diễn thập phân ngắn tương ứng. Nhưng **nó không chữa được sai số đã phát sinh trước đó**:

~~~java
double alreadyRounded = 0.1 + 0.2;
BigDecimal converted = BigDecimal.valueOf(alreadyRounded);
// converted = 0.30000000000000004, không trở lại 0.3.
~~~

Đối với input thập phân có yêu cầu chính xác, chuyển **String → BigDecimal ngay từ đầu**. Đối với số nguyên long, BigDecimal.valueOf(long) giữ chính xác giá trị.

### Các hằng và hàm tiện dụng

- **BigDecimal.ZERO**, **BigDecimal.ONE**, **BigDecimal.TEN** là hằng sẵn có ở Java 17.
- **BigDecimal.valueOf(2)** tạo giá trị 2.
- **BigDecimal.TWO** không phải hằng trong API Java 17.
- **BigDecimal.valueOf(long, int scale)** hữu ích khi giá trị ban đầu là một số nguyên không chia tỷ lệ đã xác định scale.

Không nhầm BigDecimal với BigInteger: BigInteger dành cho số nguyên, còn BigDecimal hỗ trợ lũy thừa của 10 để biểu diễn thập phân.

## 4. Tính bất biến: không sửa đối tượng trong phép toán

BigDecimal là **immutable object**: các phương thức add, subtract, multiply, divide, negate, abs, setScale, round và stripTrailingZeros không sửa nội dung toán hạng; chúng trả về BigDecimal kết quả.

~~~java
BigDecimal first = new BigDecimal("1.20");
BigDecimal second = new BigDecimal("2.30");

BigDecimal sum = first.add(second);

System.out.println(first.toPlainString()); // 1.20
System.out.println(sum.toPlainString());   // 3.50
~~~

**Nếu bỏ qua kết quả trả về, dữ liệu của biến không đổi:**

~~~java
BigDecimal total = BigDecimal.ZERO;
total.add(new BigDecimal("10.00"));
System.out.println(total); // 0

total = total.add(new BigDecimal("10.00"));
System.out.println(total); // 10.00
~~~

Câu lệnh gán **total = ...** chỉ cập nhật tham chiếu trong biến; nó không thay đổi đối tượng ZERO ban đầu.

### Lợi ích của tính bất biến

- Có thể tái sử dụng các hằng BigDecimal.ZERO/ONE/TEN trong nhiều nơi.
- Giảm nguy cơ một hàm khác thay đổi một đối tượng đã truyền qua tham số.
- Dễ kiểm thử vì đối tượng đầu vào giữ nguyên sau tính toán.
- Có thể chia sẻ đối tượng BigDecimal đã tạo để **đọc** trên nhiều luồng mà không có tình trạng mutate nội dung nó.

Tuy nhiên tính bất biến **không biến thao tác cập nhật một biến chia sẻ thành atomic**. Hai thread cùng đọc một biến balance, cùng tính giá trị cộng rồi cùng gán lại có thể mất một cập nhật. Phải dùng synchronization, transaction hoặc cấu trúc atomic phù hợp khi cập nhật trạng thái chung.

### Immutability là thuộc tính của đối tượng, không phải biến

~~~java
BigDecimal a = new BigDecimal("100");
BigDecimal alias = a;

a = a.add(BigDecimal.ONE);

// a chứa 101; alias vẫn trỏ tới đối tượng cũ có giá trị 100.
~~~

Nếu một DTO có setter cho trường BigDecimal, chính DTO vẫn có thể **mutable**, dù từng BigDecimal trong trường là immutable.

## 5. Phép cộng/trừ/nhân chính xác và scale của kết quả

Các phép cộng, trừ và nhân **không nhận MathContext** cố gắng tạo kết quả thập phân chính xác trong giới hạn triển khai.

~~~java
BigDecimal a = new BigDecimal("12.30");
BigDecimal b = new BigDecimal("0.045");

BigDecimal sum = a.add(b);       // 12.345
BigDecimal diff = a.subtract(b); // 12.255
~~~

Đối với cộng/trừ exact, **preferred scale** thường là max(scale của các toán hạng). Với nhân exact, preferred scale là tổng scale:

~~~java
BigDecimal a = new BigDecimal("1.20"); // scale 2
BigDecimal b = new BigDecimal("3.00"); // scale 2
BigDecimal product = a.multiply(b);   // 3.6000, scale 4
~~~

Chuỗi kết quả có thể mang số zero tận cùng. Đó không phải sai số: nó phản ánh cách biểu diễn BigDecimal và quy tắc scale. Nếu nghiệp vụ cần đúng 2 chữ số phần thập phân, hãy dùng setScale hoặc bước làm tròn đã quy định; đừng giả định multiply tự động xuất đúng hai chữ số.

## 6. Vì sao phép chia khác cộng/trừ/nhân?

Một phân số có thể không có khai triển thập phân hữu hạn. Với BigDecimal:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = new BigDecimal("8");
BigDecimal exact = one.divide(eight); // 0.125
~~~

Ngược lại:

~~~java
// BigDecimal.ONE.divide(new BigDecimal("3"));
// Ném ArithmeticException khi thực thi vì 1/3 không kết thúc.
~~~

BigDecimal.divide(BigDecimal) không có thông tin giới hạn chữ số/làm tròn thì chỉ chấp nhận thương thập phân **chính xác hữu hạn**. Nếu số chia bằng zero, cũng ném ArithmeticException.

Một phân số a/b sau khi rút gọn thành mẫu số dương b có dạng thập phân hữu hạn khi các thừa số nguyên tố của b chỉ gồm **2 và 5**. Vì vậy 1/8 và 1/40 kết thúc, còn 1/3 và 1/7 không kết thúc.

### Chia theo scale và rounding

~~~java
import java.math.RoundingMode;

BigDecimal result = BigDecimal.ONE.divide(
        new BigDecimal("3"), 4, RoundingMode.HALF_UP);
// 0.3333, scale 4
~~~

Overload này yêu cầu **4 chữ số sau dấu thập phân**, không phải 4 chữ số có nghĩa.

### Chia theo MathContext

~~~java
import java.math.MathContext;

MathContext mc = new MathContext(5, RoundingMode.HALF_UP);
BigDecimal result = BigDecimal.ONE.divide(
        new BigDecimal("3"), mc);
// 0.33333, precision 5
~~~

MathContext kiểm soát **độ chính xác tính bằng số chữ số có nghĩa** cùng RoundingMode. Sự khác nhau giữa **scale** và **precision** thể hiện rất rõ trong ví dụ này.

## 7. MathContext: precision không phải scale

- **scale**: số mũ thập phân được biểu diễn trong giá trị, có thể hiểu là số vị trí phần lẻ khi scale không âm.
- **precision**: số chữ số thập phân của unscaled value, không phải chỉ số chữ số bên phải dấu chấm.
- **MathContext**: tham số chính sách giới hạn precision của một **phép tính cụ thể**.

Ví dụ:

~~~java
BigDecimal x = new BigDecimal("12345.6789");
BigDecimal y = x.round(
        new MathContext(5, RoundingMode.HALF_EVEN));
// y = 12346, gồm 5 chữ số có nghĩa
~~~

Kết quả không có 5 chữ số sau dấu chấm; nó có **5 chữ số có nghĩa toàn số**.

**MathContext.UNLIMITED** có precision bằng 0 và yêu cầu kết quả chính xác đối với các phép toán thích hợp. Nó không khiến 1/3 đột nhiên có khai triển thập phân hữu hạn, nên phép chia không kết thúc vẫn cần rounding policy.

MathContext không phải một biến toàn cục tự động ảnh hưởng mọi phép toán. Bạn có thể dùng MathContext ở từng lời gọi:

~~~java
BigDecimal a = new BigDecimal("12345.6789");
BigDecimal b = new BigDecimal("2.5");

BigDecimal exact = a.multiply(b);
BigDecimal rounded = a.multiply(b,
        new MathContext(6, RoundingMode.HALF_EVEN));
~~~

Hai kết quả có thể khác nhau do **rounded** được làm tròn theo chính sách sau phép nhân.

## 8. setScale: điều khiển số chữ số sau dấu thập phân

~~~java
BigDecimal original = new BigDecimal("2.345");
BigDecimal newValue = original.setScale(2, RoundingMode.HALF_UP);
// original vẫn là 2.345; newValue là 2.35
~~~

setScale trả một BigDecimal kết quả, **không sửa original**. Nếu chỉ tăng scale từ 1.2 thành 1.20, việc thêm zero không thay đổi giá trị:

~~~java
BigDecimal price = new BigDecimal("1.2");
BigDecimal twoDigits = price.setScale(2, RoundingMode.UNNECESSARY);
// 1.20
~~~

Với RoundingMode.UNNECESSARY, nếu thay đổi scale bắt buộc phải làm tròn bỏ chữ số có nghĩa, phương thức sẽ ném ArithmeticException.

### Các lựa chọn RoundingMode

| Chế độ | Mô tả |
| --- | --- |
| HALF_EVEN | Tie → chữ số cuối giữ lại chẵn |
| HALF_UP | Tie → xa zero |
| HALF_DOWN | Tie → về zero |
| UP | Có phần dư thì xa zero |
| DOWN | Có phần dư thì về zero |
| CEILING | Về dương vô cùng |
| FLOOR | Về âm vô cùng |
| UNNECESSARY | Không được phép làm tròn |

Không có RoundingMode duy nhất đúng cho mọi ứng dụng. Với thuế, tính phí, tiền tệ hoặc dữ liệu đo, hãy lựa chọn theo **quy định nghiệp vụ và tiêu chuẩn áp dụng**.

**Vị trí làm tròn quan trọng**: làm tròn từng thành phần trước khi tổng hợp có thể khác làm tròn tổng cuối. BigDecimal cho công cụ thực hiện chính sách, nhưng không tự quyết định chính sách.

## 9. equals() khác compareTo() như thế nào?

Hai BigDecimal có cùng giá trị số nhưng scale khác:

~~~java
BigDecimal a = new BigDecimal("1.0");  // scale 1
BigDecimal b = new BigDecimal("1.00"); // scale 2

System.out.println(a.equals(b));   // false
System.out.println(a.compareTo(b)); // 0
~~~

- **equals()** xem cả giá trị số và scale.
- **compareTo()** so giá trị số theo thứ tự số học và có thể trả 0 dù scale khác.
- **==** so đối tượng tham chiếu, không phải phép so giá trị BigDecimal.

Khi kiểm tra hai giá trị thập phân có cùng giá trị số:

~~~java
boolean numericallyEqual = a.compareTo(b) == 0;
boolean smaller = a.compareTo(b) < 0;
boolean larger = a.compareTo(b) > 0;
~~~

### HashSet, HashMap và TreeSet

**hashCode()** của BigDecimal xét scale, nhất quán với equals. HashSet có thể chứa cả 1.0 và 1.00 như hai giá trị khác nhau theo equals. Ngược lại, TreeSet dùng natural ordering/compareTo, nên thường chỉ giữ một phần tử tương đương về giá trị số. **Natural ordering của BigDecimal không nhất quán với equals.**

Nếu dùng BigDecimal làm **khóa** hoặc đại diện cho tiền, định nghĩa chính sách rõ ràng: scale có là một phần của danh tính không, và khi nào chuẩn hóa? Tránh trộn cách dùng equals và compareTo tùy tiện khi ứng dụng yêu cầu logic nhất quán.

## 10. stripTrailingZeros và biểu diễn khoa học

~~~java
BigDecimal a = new BigDecimal("123.4500");
BigDecimal normalized = a.stripTrailingZeros();

System.out.println(normalized.toPlainString()); // 123.45
System.out.println(normalized.scale());         // 2
~~~

Không nên cho rằng stripTrailingZeros luôn tạo một số có scale không âm:

~~~java
BigDecimal large = new BigDecimal("1000").stripTrailingZeros();
System.out.println(large.toString()); // 1E+3
System.out.println(large.scale());    // -3
~~~

Đôi khi chuẩn hóa scale là phù hợp cho khóa/so sánh; nhưng với giá tiền yêu cầu đúng hai chữ số, bỏ trailing zeros có thể làm mất một phần quy ước biểu diễn. Cần định nghĩa rõ mục tiêu trước.

**Zero đặc biệt:** stripTrailingZeros có thể chuẩn hóa giá trị zero về BigDecimal.ZERO, scale 0. Điều này ảnh hưởng equals đối với một BigDecimal zero có scale khác.

## 11. toString, toPlainString và giữ biểu diễn

- **toString()**: xuất dạng chính tắc có thể dùng exponent notation.
- **toPlainString()**: xuất dạng chữ số thập phân không có ký pháp mũ.
- **toEngineeringString()**: có thể xuất dạng kỹ thuật với số mũ bội 3.

~~~java
BigDecimal n = new BigDecimal("1E+3");

System.out.println(n.toString());      // 1E+3
System.out.println(n.toPlainString()); // 1000
~~~

**toPlainString có thể không bảo toàn scale nếu chuỗi được parse lại**, như ví dụ 1E+3 scale −3 chuyển thành "1000" scale 0. Nếu hệ thống cần giữ cả giá trị lẫn scale, sử dụng biểu diễn round-trip phù hợp hoặc lưu scale riêng theo hợp đồng.

Việc format BigDecimal bằng NumberFormat dành cho hiển thị UI có thể thay dấu nhóm, dấu thập phân và giới hạn chữ số; đừng ghi lại kết quả đã format như một giá trị gốc nếu cần độ chính xác ban đầu.

## 12. Ứng dụng tiền và thuế: chính xác không đồng nghĩa không cần làm tròn

~~~java
BigDecimal unitPrice = new BigDecimal("19.99");
BigDecimal quantity = BigDecimal.valueOf(3);
BigDecimal subtotal = unitPrice.multiply(quantity); // 59.97

BigDecimal taxRate = new BigDecimal("0.075");
BigDecimal tax = subtotal.multiply(taxRate); // 4.49775
~~~

Việc tax có nhiều chữ số sau dấu thập phân không phải sai số nhị phân; đó là kết quả thập phân **chính xác của phép nhân**.

Nếu chính sách yêu cầu làm tròn ở bước này:

~~~java
BigDecimal chargedTax = tax.setScale(2, RoundingMode.HALF_UP);
// 4.50 theo quy tắc ví dụ
~~~

Không dùng RoundingMode.HALF_UP theo thói quen khi nghiệp vụ/pháp luật yêu cầu quy tắc khác. Tương tự cần xác định thứ tự làm tròn: mỗi dòng sản phẩm, từng khoản thuế hay tổng hóa đơn. Đơn vị tiền tệ và mã currency nên được lưu riêng; BigDecimal không tự biểu diễn USD/EUR/VND và không tự đổi tỷ giá.

## 13. Thread safety và mô hình lưu trữ

Immutable BigDecimal không có setter sửa unscaled value hay scale; do đó chia sẻ **đối tượng đã tạo** để đọc an toàn về nội dung đối tượng. Nhưng việc quản lý một **biến mutable chứa tham chiếu BigDecimal** trên nhiều thread vẫn đòi hỏi đồng bộ hóa thích hợp nếu có ghi.

Ví dụ một thuộc tính balance được hai request cập nhật theo quy trình đọc–cộng–gán có thể mất một cập nhật. Cần lock, transaction hoặc cấu trúc atomic phù hợp, tùy kiến trúc.

Khi truyền qua JSON, CSV, database:

1. Kiểm tra kiểu parser có giữ thập phân hay âm thầm đổi thành double.
2. Dùng **String decimal** hoặc kiểu native decimal có precision/scale phù hợp khi giao thức yêu cầu.
3. Với SQL DECIMAL/NUMERIC, kiểm tra precision/scale cột và quy tắc chuyển đổi của driver.
4. Giới hạn chiều dài chuỗi và độ lớn scale trước khi xử lý input không tin cậy.
5. Không nhầm chuỗi UI chứa grouping/currency với chuỗi chuẩn được chấp nhận bởi BigDecimal(String).

BigDecimal không giải quyết mọi vấn đề về an toàn dữ liệu, giao dịch hoặc định dạng. Nó cung cấp mô hình số thập phân để những quy tắc đó được triển khai rõ ràng.


## 14. Ví dụ Java 17+ chạy độc lập

Lưu thành tệp **BigDecimalModelDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalModelDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("123.4500");

        System.out.println("value: " + a.toPlainString());
        System.out.println("unscaled: " + a.unscaledValue());
        System.out.println("scale: " + a.scale());
        System.out.println("precision: " + a.precision());

        BigDecimal normalized = a.stripTrailingZeros();
        System.out.println("normalized: " + normalized.toPlainString()
                + " (scale=" + normalized.scale() + ")");

        BigDecimal first = new BigDecimal("1.20");
        BigDecimal second = new BigDecimal("2.30");
        BigDecimal sum = first.add(second);

        System.out.println("first stays: " + first.toPlainString());
        System.out.println("sum: " + sum.toPlainString());
        System.out.println("decimal 0.1 + 0.2: "
                + new BigDecimal("0.1").add(new BigDecimal("0.2")));
        System.out.println("valueOf(0.1): " + BigDecimal.valueOf(0.1));

        System.out.println("new BigDecimal(0.1) equals 0.1: "
                + new BigDecimal(0.1).equals(new BigDecimal("0.1")));

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println("equals 1.0 / 1.00: " + x.equals(y));
        System.out.println("compareTo 1.0 / 1.00: " + x.compareTo(y));

        System.out.println("8th: "
                + BigDecimal.ONE.divide(new BigDecimal("8")));
        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException e) {
            System.out.println("1 / 3 exact: ArithmeticException");
        }

        System.out.println("1 / 3 scale 4: "
                + BigDecimal.ONE.divide(
                        new BigDecimal("3"), 4, RoundingMode.HALF_UP));
        System.out.println("1 / 3 precision 5: "
                + BigDecimal.ONE.divide(
                        new BigDecimal("3"),
                        new MathContext(5, RoundingMode.HALF_UP)));

        BigDecimal rounded = new BigDecimal("12345.6789")
                .round(new MathContext(5, RoundingMode.HALF_EVEN));
        System.out.println("round 12345.6789 to 5 digits: "
                + rounded.toPlainString());
        System.out.println("setScale HALF_UP: "
                + new BigDecimal("2.345")
                        .setScale(2, RoundingMode.HALF_UP));

        BigDecimal scientific = new BigDecimal("1E+3");
        System.out.println("scientific scale: " + scientific.scale());
        System.out.println("scientific string: " + scientific);
        System.out.println("scientific plain: " + scientific.toPlainString());
        System.out.println("stripped 1000: "
                + new BigDecimal("1000").stripTrailingZeros());

        System.out.println("10.00 / 4: "
                + new BigDecimal("10.00").divide(new BigDecimal("4")));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 BigDecimalModelDemo.java
java BigDecimalModelDemo
~~~

Stdout (đã đối chiếu với API BigDecimal trên JDK):

~~~text
value: 123.4500
unscaled: 1234500
scale: 4
precision: 7
normalized: 123.45 (scale=2)
first stays: 1.20
sum: 3.50
decimal 0.1 + 0.2: 0.3
valueOf(0.1): 0.1
new BigDecimal(0.1) equals 0.1: false
equals 1.0 / 1.00: false
compareTo 1.0 / 1.00: 0
8th: 0.125
1 / 3 exact: ArithmeticException
1 / 3 scale 4: 0.3333
1 / 3 precision 5: 0.33333
round 12345.6789 to 5 digits: 12346
setScale HALF_UP: 2.35
scientific scale: -3
scientific string: 1E+3
scientific plain: 1000
stripped 1000: 1E+3
10.00 / 4: 2.50
~~~

### Đọc kết quả

- unscaled=1234500, scale=4, precision=7 phản ánh chính xác mô hình số thập phân ban đầu.
- normalized có scale=2 nhưng giá trị số không đổi.
- first vẫn là 1.20 sau add; đây là tính immutable.
- 0.1+0.2 dưới dạng chuỗi BigDecimal cho 0.3 thập phân chính xác.
- new BigDecimal(0.1) không equals giá trị thập phân "0.1" vì nhận double nhị phân.
- equals phân biệt scale, compareTo so giá trị số.
- 1/8 có kết quả thập phân hữu hạn, 1/3 không thể chia exact mà không làm tròn.
- Scale 4 khác precision 5; kết quả lần lượt 0.3333 và 0.33333.
- Round với MathContext(5) chỉ giữ 5 chữ số có nghĩa nên 12345.6789 thành 12346.
- Giá trị 1000 có thể được biểu diễn qua scale âm khi chuẩn hóa.
- 10.00/4 trả 2.50 do preferred scale của phép chia chính xác.

## 15. Các lỗi phổ biến

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| Dùng new BigDecimal(0.1) để tạo chính xác 0.1 | Nhận đúng giá trị nhị phân double gần 0.1, không phải 0.1 thập phân | Dùng new BigDecimal("0.1") |
| Chuyển kết quả double đã làm tròn sang BigDecimal và nghĩ sẽ sửa sai số | Sai số trước đó vẫn tồn tại | Parse dữ liệu thập phân trước phép tính |
| Gọi total.add(amount) mà không gán | total không đổi | total = total.add(amount) |
| Dùng == để so hai BigDecimal | So tham chiếu, không so giá trị | Dùng equals/compareTo đúng mục đích |
| Dùng equals cho 1.0 và 1.00 nhưng muốn so giá trị số | false do scale khác | Dùng compareTo == 0 |
| Dùng BigDecimal làm HashMap key mà không có quy tắc scale | Hai số cùng giá trị có thể là hai khóa | Chuẩn hóa biểu diễn theo hợp đồng |
| Nghĩ scale và precision cùng nghĩa | Sai giới hạn làm tròn | Scale là số mũ thập phân; precision là số chữ số có nghĩa |
| Nghĩ scale luôn dương | 1E+3 có scale −3 | Xử lý scale âm |
| Tin stripTrailingZeros luôn in dạng plain | Có thể xuất 1E+3 | Dùng toPlainString khi cần và hiểu round-trip |
| Gọi divide cho 1/3 không có rounding | ArithmeticException | Đặt scale/RoundingMode hoặc MathContext |
| Nghĩ MathContext(5) tương đương setScale(5) | Làm tròn khác nhau | Chọn theo chữ số có nghĩa hay phần thập phân |
| Nghĩ setScale sửa original | Bỏ qua kết quả, original không đổi | Gán đối tượng trả về |
| Tự chọn HALF_UP cho mọi loại tiền và thuế | Có thể sai nghiệp vụ | Chọn RoundingMode theo chính sách thực |
| Nghĩ BigDecimal giữ vô hạn chữ số không tốn tài nguyên | Có thể tốn nhiều heap/CPU, scale int | Đặt giới hạn tài nguyên |
| Nghĩ immutable tự làm biến chia sẻ an toàn khi nhiều luồng cùng ghi | Có thể mất cập nhật | Đồng bộ hóa hoặc dùng transaction |
| Dùng NumberFormat làm mô hình lưu giá trị | Chuỗi hiển thị có thể làm tròn/phụ thuộc Locale | Lưu số thập phân theo dữ liệu gốc |

## 16. Bài tập tự kiểm tra

**Bài 1.** Với BigDecimal từ chuỗi "12.3400", unscaledValue và scale là gì?

**Đáp án:** 123400 và 4. Giá trị số = 123400 × 10^−4 = 12.3400.

**Bài 2.** new BigDecimal("1.0").equals(new BigDecimal("1.00")) trả gì? compareTo trả gì?

**Đáp án:** equals trả false vì scale 1 và 2 khác nhau; compareTo trả 0 vì giá trị số bằng nhau.

**Bài 3.** BigDecimal a = new BigDecimal("2"); a.multiply(BigDecimal.TEN); sau đó a còn bao nhiêu?

**Đáp án:** Vẫn là 2. BigDecimal immutable, phải gán kết quả nếu muốn biến tham chiếu giá trị mới.

**Bài 4.** Tại sao phép chia exact 1/3 ném ArithmeticException?

**Đáp án:** Kết quả có khai triển thập phân vô hạn, không biểu diễn bằng một BigDecimal hữu hạn khi không có rounding policy.

**Bài 5.** Muốn 1/3 được biểu diễn thành 0.3333, phải dùng overload nào?

**Đáp án:** BigDecimal.ONE.divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP).

**Bài 6.** MathContext(5) giữ 5 chữ số sau dấu thập phân hay 5 chữ số có nghĩa?

**Đáp án:** 5 chữ số có nghĩa. Nếu cần 5 chữ số phần thập phân, hãy dùng setScale(5, mode) theo chính sách.

**Bài 7.** Tại sao BigDecimal từ chuỗi "1000" sau stripTrailingZeros có thể thành "1E+3"?

**Đáp án:** Trailing zeros được bỏ khỏi unscaled value, scale có thể trở thành −3; giá trị toán học vẫn là 1000.

**Bài 8.** Cần làm tròn tiền thuế ở bước nào?

**Đáp án:** Theo hợp đồng nghiệp vụ/pháp luật của hệ thống. BigDecimal chỉ hỗ trợ thực thi cách làm tròn đã được xác định.

**Bài 9.** BigDecimal.valueOf(0.1 + 0.2) có đảm bảo thành BigDecimal chính xác 0.3 không?

**Đáp án:** Không. Phép cộng double đã tạo giá trị gần 0.30000000000000004 trước khi chuyển.

**Bài 10.** Một biến balance chứa BigDecimal dùng chung nhiều thread có an toàn khi các thread đồng thời gán balance = balance.add(delta) không?

**Đáp án:** Không mặc nhiên. Đối tượng immutable, nhưng cập nhật biến có thể mất dữ liệu nếu thiếu đồng bộ/giao dịch.

## 17. Tài liệu gốc

- [BigDecimal API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): mô hình unscaledValue/scale, immutability, preferred scales, phép toán, equals/compareTo.
- [MathContext API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): precision, rounding và UNLIMITED.
- [RoundingMode API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): các chế độ làm tròn.
- [BigInteger API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): số nguyên độ chính xác tùy ý và unscaled value.
- [Double API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): biểu diễn double, Double.toString.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-002">Unscaled value, scale và công thức xác định giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Unscaled value, scale và công thức xác định giá trị của BigDecimal trong Java

`java.math.BigDecimal` là kiểu số **thập phân bất biến với độ chính xác tùy ý**, rất hữu ích khi cần biểu diễn giá trị thập phân một cách có kiểm soát. Điểm quan trọng nhất khi học `BigDecimal` không phải chỉ là “nó có nhiều chữ số hơn `double`”, mà là **cách Java xác định giá trị của mỗi object từ một số nguyên và một scale**.

Một `BigDecimal` có hai thành phần cốt lõi:

- **Unscaled value**: một số nguyên có dấu, có thể biểu diễn chính xác bằng `BigInteger`.
- **Scale**: một số nguyên kiểu `int`; cho biết số mũ của 10 trong cách diễn giải unscaled value.

Công thức định nghĩa giá trị là:

~~~text
value = unscaledValue × 10^(-scale)
~~~

Vì vậy `unscaledValue = 12345`, `scale = 2` sẽ cho `123.45`; nhưng cùng unscaled value với `scale = -2` sẽ cho `1,234,500`. **Scale không phải luôn luôn là số chữ số nằm sau dấu thập phân**, vì scale có thể dương, bằng 0 hoặc âm.

Bài học này thuộc mục **Mô hình BigDecimal** và giải thích cách đọc hai thành phần, mối quan hệ với `precision`, các thao tác thay đổi scale, sự khác nhau giữa equality theo giá trị và theo biểu diễn, cùng cách dùng an toàn trong tính toán thực tế với Java 17+.

## 1. Phân tích công thức bằng ví dụ

Với `new BigDecimal("123.45")`, Java có thể mô tả giá trị bằng:

~~~text
unscaledValue = 12345
scale         = 2
value         = 12345 × 10^(-2) = 123.45
~~~

Một số biểu diễn khác:

| Unscaled value | Scale | Tính toán | Giá trị số |
| ---: | ---: | --- | ---: |
| `12345` | `2` | `12345 × 10^-2` | `123.45` |
| `12345` | `0` | `12345 × 10^0` | `12345` |
| `123` | `-2` | `123 × 10^2` | `12300` |
| `-12345` | `3` | `-12345 × 10^-3` | `-12.345` |
| `12` | `2` | `12 × 10^-2` | `0.12` |
| `0` | `4` | `0 × 10^-4` | `0` |

Ở trường hợp unscaled value bằng 0, **mọi scale đều cho cùng giá trị toán học là zero**, nhưng object vẫn có thể giữ scale khác nhau, chẳng hạn `new BigDecimal("0.000")` có scale bằng 3.

Không nên gọi `scale` là “precision”, bởi nó không đếm tổng số chữ số có nghĩa và không diễn đạt độ tin cậy vật lý của dữ liệu. Scale chỉ là **số mũ thập phân trong mô hình giá trị**.

## 2. Đọc trực tiếp bằng `unscaledValue()` và `scale()`

~~~java
BigDecimal amount = new BigDecimal("123.4500");

System.out.println(amount);                // 123.4500
System.out.println(amount.unscaledValue()); // 1234500
System.out.println(amount.scale());         // 4
System.out.println(amount.precision());     // 7
~~~

`unscaledValue()` trả `BigInteger`, không phải `long`. Do đó một số decimal rất lớn không nhất thiết bị giới hạn trong 64 bit. `scale()` trả `int`, thể hiện số mũ thập phân dùng trong công thức. `precision()` đếm số chữ số thập phân có nghĩa trong **unscaled value**; với `1234500` là 7 chữ số.

Kết quả `123.4500` và `123.45` **bằng nhau về giá trị toán học**, nhưng có cấu trúc khác nhau:

| Văn bản đầu vào | Unscaled value | Scale | Precision |
| --- | ---: | ---: | ---: |
| `"123.4500"` | `1234500` | `4` | `7` |
| `"123.45"` | `12345` | `2` | `5` |
| `"1.23E+4"` | `123` | `-2` | `3` |
| `"0.000"` | `0` | `3` | `1` |

**Đếm precision của zero:** theo hợp đồng `BigDecimal`, `precision()` của một giá trị zero là 1, không phải 0, bất kể scale của biểu diễn zero được tạo ra. Việc giữ thêm chữ số 0 trong một số decimal ảnh hưởng scale và có thể ảnh hưởng precision trong nhiều trường hợp, nhưng không biến dữ liệu đầu vào thành phép đo “đáng tin” hơn.

## 3. Scale dương, bằng 0 và âm

### Scale dương

Với `new BigDecimal("12.345")`, unscaled value là `12345` và scale là `3`: giá trị `12345 × 10^-3 = 12.345`. Đây là trường hợp quen thuộc khiến người mới học nghĩ scale luôn đồng nghĩa “ba chữ số sau dấu thập phân”.

### Scale bằng 0

`new BigDecimal("12345")` có unscaled value `12345` và scale `0`. Giá trị chính là số nguyên 12345, nhưng vẫn là một đối tượng `BigDecimal`, khác kiểu so với `BigInteger` hay `Long`.

### Scale âm

`new BigDecimal("1.23E+4")` có:

~~~java
BigDecimal scientific = new BigDecimal("1.23E+4");
System.out.println(scientific);                 // 1.23E+4
System.out.println(scientific.unscaledValue()); // 123
System.out.println(scientific.scale());         // -2
System.out.println(scientific.toPlainString()); // 12300
~~~

Vì `123 × 10^2 = 12300`, scale âm hoàn toàn hợp lệ. Dạng `toString()` có thể dùng ký pháp khoa học, còn `toPlainString()` thể hiện giá trị dưới dạng chữ số thập phân thông thường, không dùng ký pháp số mũ. **Đừng suy ra `scale < 0` là lỗi hoặc số âm.** Dấu của số nằm trong unscaled value; dấu của scale nói về vị trí dấu thập phân.

Có thể xây trực tiếp từ cặp unscaled/scale:

~~~java
new BigDecimal(BigInteger.valueOf(12345), 2); // 123.45
new BigDecimal(BigInteger.valueOf(123), -2);  // 1.23E+4
BigDecimal.valueOf(12L, 2);                    // 0.12
~~~

Overload `BigDecimal.valueOf(long unscaledVal, int scale)` khác `BigDecimal.valueOf(double)`. Đừng nhầm các overload chỉ vì cùng tên phương thức.

## 4. Cùng giá trị nhưng khác biểu diễn: `equals` và `compareTo`

Hai số:

~~~java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");
~~~

đều bằng toán học với 1, nhưng `a` có unscaled = 10, scale = 1; `b` có unscaled = 100, scale = 2. Do đó:

~~~java
System.out.println(a.equals(b));        // false
System.out.println(a.compareTo(b));     // 0
~~~

- **`equals`** xem hai `BigDecimal` bằng nhau khi cả *giá trị số* và *scale* phù hợp bằng nhau; ví dụ trên trả false.
- **`compareTo`** so sánh giá trị số, nên trả 0 với hai biểu diễn `1.0` và `1.00`.

Hệ quả về collections:

~~~java
Set<BigDecimal> hashed = new HashSet<>(Arrays.asList(a, b));
Set<BigDecimal> ordered = new TreeSet<>(Arrays.asList(a, b));
// hashed.size() = 2, ordered.size() = 1
~~~

`HashSet` dựa trên `equals`/`hashCode`, còn `TreeSet` mặc định dùng `compareTo` làm thứ tự, nên **hai collection có thể gộp phần tử theo chính sách khác nhau**. Nếu dùng giá tiền hoặc số decimal làm khóa, hãy xác định scale có ý nghĩa nghiệp vụ không, và chuẩn hóa biểu diễn trước khi lưu khóa khi cần.

**Đừng thay đổi `equals`/`hashCode` tùy tiện** hoặc dùng so sánh “gần bằng” theo epsilon làm comparator của `TreeSet`; quan hệ so sánh phải giữ được hợp đồng nhất quán và bắc cầu.

## 5. `stripTrailingZeros()` thay đổi cấu trúc nhưng giữ giá trị số

`stripTrailingZeros()` loại bỏ những số 0 cuối trong **biểu diễn decimal** mà không thay đổi giá trị toán học:

~~~java
BigDecimal original = new BigDecimal("123.4500");
BigDecimal stripped = original.stripTrailingZeros();

System.out.println(stripped);                 // 123.45
System.out.println(stripped.unscaledValue()); // 12345
System.out.println(stripped.scale());         // 2
~~~

Object `original` **không thay đổi**, vì `BigDecimal` bất biến. Với một số nguyên có nhiều số 0 cuối, scale kết quả thậm chí có thể trở thành **âm**; chẳng hạn `new BigDecimal("1200.0").stripTrailingZeros()` có thể được trình bày bằng số mũ để thể hiện scale âm mà vẫn giữ giá trị 1200.

Với zero, `stripTrailingZeros` cho dạng zero chuẩn có scale 0:

~~~java
BigDecimal z = new BigDecimal("0.000");
System.out.println(z.unscaledValue());                 // 0
System.out.println(z.scale());                         // 3
System.out.println(z.precision());                     // 1
System.out.println(z.stripTrailingZeros().scale());    // 0
~~~

**Không dùng `stripTrailingZeros` nếu nghiệp vụ cần giữ số chữ số đã nhập như một thông tin riêng.** Trong báo giá hoặc phép đo, `1.20` đôi khi cần được hiển thị đúng hai chữ số phần lẻ; normalize thành `1.2` sẽ loại mất scale ban đầu dù không làm mất giá trị toán học.

## 6. `setScale`: thay đổi scale và đôi khi cần làm tròn giá trị

`setScale(newScale)` tạo một `BigDecimal` mới với scale được yêu cầu.

**Tăng scale** (thêm các vị trí thập phân) thường có thể thực hiện chính xác bằng cách nhân unscaled value với lũy thừa 10:

~~~java
BigDecimal initial = new BigDecimal("12.3");
BigDecimal padded = initial.setScale(3);
// padded = 12.300, unscaled = 12300, scale = 3
~~~

**Giảm scale** thường đòi bỏ chữ số có nghĩa và có thể cần làm tròn:

~~~java
BigDecimal amount = new BigDecimal("12.35");
BigDecimal rounded = amount.setScale(1, RoundingMode.HALF_UP);
// rounded = 12.4
~~~

Khi dùng overload `setScale(int)` không có rounding mode, Java yêu cầu phép thay đổi scale **diễn ra chính xác**. Nếu cần bỏ chữ số khác 0, nó ném `ArithmeticException`. Để phát hiện thao tác không được phép làm tròn, có thể chọn `RoundingMode.UNNECESSARY` một cách tường minh.

Sự khác nhau quan trọng:

- `setScale` đổi **scale và có thể đổi giá trị toán học** khi phải làm tròn.
- `stripTrailingZeros` đổi **cách biểu diễn, không đổi giá trị toán học**.
- `DecimalFormat.format` tạo **chuỗi hiển thị**, không đổi object nguồn.
- `BigDecimal` immutable: cần lưu kết quả trả về, không chờ object gốc bị mutate.

**Rounding mode** phải theo yêu cầu nghiệp vụ. `HALF_UP`, `HALF_EVEN`, `DOWN`, `CEILING` và `FLOOR` xử lý các ranh giới và số âm khác nhau; không có mode mặc định “đúng với mọi hóa đơn”.

## 7. `scaleByPowerOfTen` và `movePointLeft/Right`

Từ công thức `unscaled × 10^-scale`, việc nhân hoặc chia cho một lũy thừa 10 có thể thực hiện bằng các API dịch dấu thập phân:

~~~java
BigDecimal input = new BigDecimal("12.30");
BigDecimal scaled = input.scaleByPowerOfTen(2);
BigDecimal moved = input.movePointRight(3);

// scaled = 1230, moved = 12300
~~~

Với `scaleByPowerOfTen(n)`, về mặt mô hình `BigDecimal`, scale mới về nguyên tắc là `scale - n`. `movePointRight(n)` và `movePointLeft(n)` mang ngữ nghĩa dịch điểm thập phân, và kết quả của chúng có scale **không âm** theo hợp đồng. Vì vậy ở những trường hợp scale âm, hai nhóm API có thể cho **biểu diễn khác nhau dù giá trị toán học bằng nhau**.

Đừng lấy kiểu biểu diễn chuỗi đã format làm tiêu chí để phân biệt phép toán có đổi giá trị hay không. Hãy kiểm tra:

~~~text
Giá trị mới = Giá trị cũ × 10^n      (dịch sang phải / scaleByPowerOfTen)
Giá trị mới = Giá trị cũ × 10^(-n)   (dịch sang trái)
~~~

Những thao tác này còn chịu giới hạn scale kiểu `int` và tài nguyên. Đối với scale quá lớn/quá nhỏ, phép biến đổi có thể ném ngoại lệ hoặc không thể biểu diễn theo giới hạn thực thi; không sử dụng giá trị `n` không tin cậy mà không kiểm tra.

## 8. Phép toán có thể tạo scale mới theo quy tắc

`BigDecimal` không chỉ giữ unscaled và scale ban đầu: **kết quả phép toán cũng có cặp unscaled/scale riêng**.

### Cộng và trừ

Đối với phép cộng/trừ chính xác, scale ưu tiên (*preferred scale*) là **lớn hơn trong hai scale của toán hạng**:

~~~java
BigDecimal x = new BigDecimal("1.20"); // scale 2
BigDecimal y = new BigDecimal("3.4");  // scale 1

BigDecimal sum = x.add(y); // 4.60, scale 2
~~~

Vì hai số cần cộng ở cùng đơn vị decimal, hệ thống có thể thêm số 0 vào biểu diễn nội bộ trước khi cộng unscaled integer.

### Nhân

Scale ưu tiên của phép nhân chính xác là **tổng hai scale**:

~~~java
BigDecimal product = new BigDecimal("1.20")
        .multiply(new BigDecimal("3.4"));
// product = 4.080, scale 3 = 2 + 1
~~~

Hai số `4.080` và `4.08` bằng nhau theo `compareTo` nhưng có scale khác nhau. Khi nhân tiền với tỷ lệ hay số lượng, hãy xác định scale đầu ra và thời điểm làm tròn theo quy tắc nghiệp vụ.

### Chia

Scale ưu tiên của phép chia là **scale của số bị chia trừ scale của số chia**, nhưng kết quả chính xác có thể cần thêm chữ số phần lẻ. Ví dụ:

~~~java
BigDecimal quotient = new BigDecimal("1.00")
        .divide(new BigDecimal("4"));
// quotient = 0.25, scale 2
~~~

Một phép chia như `BigDecimal.ONE.divide(new BigDecimal("3"))` có khai triển decimal vô hạn nên overload chia chính xác không rounding có thể ném `ArithmeticException`. Khi nghiệp vụ cho phép xấp xỉ, hãy truyền `scale` và `RoundingMode` hoặc `MathContext`, đồng thời ghi rõ tiêu chí độ chính xác.

**Lưu ý:** Khi dùng overload có `MathContext` và giới hạn precision, các phép toán có thể làm tròn, kéo theo scale thực tế khác preferred scale. Đừng dự đoán scale kết quả mà không xét overload và rounding context.

## 9. `MathContext.precision` khác `BigDecimal.scale`

`MathContext` giới hạn **tổng số chữ số có nghĩa của kết quả**, không phải số chữ số sau dấu thập phân.

~~~java
BigDecimal value = new BigDecimal("12345.67");
BigDecimal rounded = value.round(
        new MathContext(3, RoundingMode.HALF_UP));
// rounded = 1.23E+4: ba chữ số có nghĩa, scale âm
~~~

Ngược lại, `setScale(2, RoundingMode.HALF_UP)` yêu cầu hai vị trí thập phân sau dấu phân cách ở kết quả. Hai thao tác có thể cho các kết quả rất khác nhau với cùng đầu vào.

| Phương thức | Câu hỏi cần trả lời |
| --- | --- |
| `scale()` | Số mũ thập phân của object này là bao nhiêu? |
| `precision()` | Unscaled value có bao nhiêu chữ số decimal có nghĩa? |
| `setScale(2, mode)` | Muốn kết quả giữ scale chính xác bằng 2, cho phép làm tròn theo mode? |
| `round(new MathContext(3, mode))` | Muốn giữ ba chữ số có nghĩa của kết quả? |
| `format(...)` | Muốn chuỗi hiển thị theo quy tắc trình bày nào? |

`MathContext.UNLIMITED` có precision bằng 0 theo hợp đồng `MathContext` (ý nghĩa “không giới hạn”), khác với `BigDecimal.precision() == 0`; một `BigDecimal` có giá trị zero vẫn có precision bằng 1. Tránh nhầm hai kiểu “precision”.

## 10. Constructor, String và double: nguồn vào quyết định unscaled value

Nếu yêu cầu **decimal chính xác như người dùng nhập**, tạo object trực tiếp từ chuỗi decimal:

~~~java
BigDecimal exact = new BigDecimal("0.1");
// unscaled 1, scale 1
~~~

Nếu đi qua `double`, giá trị đã được làm tròn thành binary64 trước khi constructor `BigDecimal(double)` nhận được nó:

~~~java
BigDecimal fromDouble = new BigDecimal(0.1);
// 0.1000000000000000055511151231257827021181583404541015625
~~~

`new BigDecimal(0.1)` biểu diễn **chính xác giá trị binary64 đang lưu**, không phải chính xác giá trị thập phân 1/10 theo ý định literal. `BigDecimal.valueOf(0.1)` dùng chuỗi canonical do `Double.toString(0.1)` tạo ra và trong ví dụ trả decimal `0.1`, nhưng factory đó không biết cách khôi phục dữ liệu decimal ban đầu nếu đã qua một chuỗi phép toán `double`.

Để giữ cả **giá trị và scale của đầu vào decimal**, nhận chuỗi gốc là cách rõ ràng:

~~~java
new BigDecimal("1.20"); // unscaled 120, scale 2
new BigDecimal("1.2");  // unscaled 12, scale 1
~~~

Nếu bạn chuyển hai chuỗi thành `double` rồi quay lại decimal, thông tin về hai cách ghi và scale ban đầu có thể mất. Khi parse đầu vào từ CSV/JSON/form, hãy kiểm tra ngữ pháp số thập phân, locale, miền giá trị và giới hạn precision/scale một cách tường minh.

## 11. Chương trình Java 17+ hoàn chỉnh và stdout kiểm chứng

Lưu thành tệp `BigDecimalModelDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeSet;

public class BigDecimalModelDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("123.4500");
        System.out.println("a = " + a);
        System.out.println("a.unscaledValue = " + a.unscaledValue());
        System.out.println("a.scale = " + a.scale());
        System.out.println("a.precision = " + a.precision());

        BigDecimal stripped = a.stripTrailingZeros();
        System.out.println("stripped = " + stripped);
        System.out.println("stripped unscaled = "
                + stripped.unscaledValue());
        System.out.println("stripped scale = " + stripped.scale());

        BigDecimal e = new BigDecimal("1.23E+4");
        System.out.println("e = " + e);
        System.out.println("e unscaled = " + e.unscaledValue());
        System.out.println("e scale = " + e.scale());
        System.out.println("e plain = " + e.toPlainString());
        System.out.println("construct (123,-2) = "
                + new BigDecimal(BigInteger.valueOf(123), -2));
        System.out.println("construct (12345,2) = "
                + new BigDecimal(BigInteger.valueOf(12345), 2));
        System.out.println("valueOf(12,2) = "
                + BigDecimal.valueOf(12L, 2));

        BigDecimal z = new BigDecimal("0.000");
        System.out.println("zero unscaled,scale,precision = "
                + z.unscaledValue() + "," + z.scale()
                + "," + z.precision());
        System.out.println("zero stripped scale = "
                + z.stripTrailingZeros().scale());

        System.out.println("upscale = "
                + new BigDecimal("12.3").setScale(3));
        System.out.println("downscale HALF_UP = "
                + new BigDecimal("12.35")
                .setScale(1, RoundingMode.HALF_UP));
        System.out.println("sum = "
                + new BigDecimal("1.20")
                .add(new BigDecimal("3.4")));
        System.out.println("product = "
                + new BigDecimal("1.20")
                .multiply(new BigDecimal("3.4")));
        System.out.println("division = "
                + new BigDecimal("1.00")
                .divide(new BigDecimal("4")));

        System.out.println("scaleByPowerOfTen = "
                + new BigDecimal("12.30").scaleByPowerOfTen(2));
        System.out.println("movePointRight = "
                + new BigDecimal("12.30").movePointRight(3));
        System.out.println("negative zero = "
                + new BigDecimal("-0.00"));
        System.out.println("negative zero scale = "
                + new BigDecimal("-0.00").scale());

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println("equals different scales = " + x.equals(y));
        System.out.println("compareTo different scales = " + x.compareTo(y));
        System.out.println("HashSet size = "
                + new HashSet<>(Arrays.asList(x, y)).size());
        System.out.println("TreeSet size = "
                + new TreeSet<>(Arrays.asList(x, y)).size());
        System.out.println("round MathContext(3) = "
                + new BigDecimal("12345.67")
                .round(new MathContext(3, RoundingMode.HALF_UP)));
        System.out.println("0.1 from string = "
                + new BigDecimal("0.1"));
        System.out.println("0.1 from double = "
                + new BigDecimal(0.1));

        try {
            new BigDecimal("1.23")
                    .setScale(1, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("UNNECESSARY catches rounding");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalModelDemo.java
java BigDecimalModelDemo
~~~

Stdout:

~~~text
a = 123.4500
a.unscaledValue = 1234500
a.scale = 4
a.precision = 7
stripped = 123.45
stripped unscaled = 12345
stripped scale = 2
e = 1.23E+4
e unscaled = 123
e scale = -2
e plain = 12300
construct (123,-2) = 1.23E+4
construct (12345,2) = 123.45
valueOf(12,2) = 0.12
zero unscaled,scale,precision = 0,3,1
zero stripped scale = 0
upscale = 12.300
downscale HALF_UP = 12.4
sum = 4.60
product = 4.080
division = 0.25
scaleByPowerOfTen = 1230
movePointRight = 12300
negative zero = 0.00
negative zero scale = 2
equals different scales = false
compareTo different scales = 0
HashSet size = 2
TreeSet size = 1
round MathContext(3) = 1.23E+4
0.1 from string = 0.1
0.1 from double = 0.1000000000000000055511151231257827021181583404541015625
UNNECESSARY catches rounding
~~~

Kết quả này minh họa cặp unscaled/scale **trong cả trường hợp scale âm và zero**, phép thay đổi scale có/không làm tròn, scale phép toán, việc chuẩn hóa zero, và tác động của scale lên collections.

## 12. Dạng chuỗi `toString` và `toPlainString`

`BigDecimal.toString()` tạo dạng chuỗi canonical có thể chứa ký pháp khoa học khi cần. `toPlainString()` tạo biểu diễn **không sử dụng số mũ**, phù hợp nếu bên nhận yêu cầu hiển thị decimal thông thường.

~~~java
BigDecimal big = new BigDecimal("1.23E+4");
big.toString();      // "1.23E+4"
big.toPlainString(); // "12300"
~~~

**Không nhầm định dạng xuất với cấu trúc số:** hai chuỗi trên mô tả cùng giá trị nhưng nếu parse ngược `"12300"`, object mới có thể có scale khác object ban đầu (`"1.23E+4"` có scale -2; `"12300"` có scale 0). Nếu cần giữ cả **giá trị và scale** khi serialize, phải xác định format có khả năng round-trip cả hai, chẳng hạn dùng `toString` theo hợp đồng `BigDecimal` hoặc truyền riêng unscaled/scale.

Một số nghiệp vụ không muốn dùng ký pháp khoa học khi hiển thị tiền, nhưng cũng không nên tùy tiện dùng `toPlainString()` cho dữ liệu có scale âm cực lớn vì chuỗi có thể phình to và gây tốn tài nguyên.

## 13. Các trường hợp đặc biệt và giới hạn thực tế

### Zero và dấu âm

`new BigDecimal("-0.00")` có giá trị số zero, unscaled value 0 và scale 2. `BigDecimal` **không giữ một giá trị zero âm riêng** giống IEEE 754 `double -0.0`. Chuỗi `toString()` thường biểu diễn `0.00`, dù văn bản gốc có dấu âm.

### Scale và precision có giới hạn khác nhau

Unscaled value được lưu dưới dạng số nguyên độ chính xác tùy ý nhưng **phụ thuộc tài nguyên thực tế**; scale vẫn là `int`, nên không thể sử dụng bất kỳ số mũ thập phân lớn vô hạn. Các thao tác có thể gặp `ArithmeticException` khi không thể biểu diễn scale mong muốn hoặc làm tròn bị cấm, và có thể tốn nhiều bộ nhớ khi mở rộng giá trị hoặc tạo chuỗi dài.

### Không có NaN hoặc vô cực

`BigDecimal` không biểu diễn NaN hoặc `Infinity` như `double`. Khi chuyển đầu vào bất thường sang decimal, phải có chính sách validation riêng. Nếu đang nhận `double`, sử dụng `Double.isFinite(value)` trước nếu ứng dụng chỉ chấp nhận số hữu hạn.

### Trường dữ liệu tài chính

Nếu lưu amount theo hai chữ số thập phân, bạn có thể yêu cầu `setScale(2, mode)` tại ranh giới lưu; nhưng đừng **tự động** chuẩn hóa bằng `stripTrailingZeros` nếu scale còn mang nghĩa của đơn vị tiền tệ, quy định trình bày hoặc auditing. Việc “đúng giá trị số” và “đúng đại diện số decimal” là hai hợp đồng khác nhau.

## 14. Những sai lầm thường gặp

1. **Coi scale luôn là số chữ số sau dấu phẩy.** Scale có thể âm, ví dụ `1.23E+4` có scale -2.
2. **Nhầm scale với precision.** `123.4500` có scale 4 và precision 7, không phải đều là 4.
3. **Nghĩ `stripTrailingZeros` đổi giá trị số.** Nó giữ giá trị, nhưng thay unscaled/scale và có thể khiến scale âm.
4. **Dùng `equals` để kiểm tra giá trị decimal bất kể scale.** `1.0` và `1.00` không `equals` nhưng `compareTo` bằng 0.
5. **Dùng `BigDecimal` làm khóa collection mà không quyết định chính sách scale.** `HashSet` và `TreeSet` có thể gộp dữ liệu khác nhau.
6. **Gọi `setScale` rồi nghĩ object ban đầu đã đổi.** `BigDecimal` bất biến; phải nhận object trả về.
7. **Giảm scale mà không cân nhắc rounding.** Có thể cần rounding mode hoặc ném ngoại lệ nếu chuyển không chính xác.
8. **Cho rằng `MathContext(3)` là ba chữ số sau dấu thập phân.** Nó đặt ba chữ số có nghĩa tổng cộng.
9. **Cho rằng scale kết quả phép toán luôn bằng scale một toán hạng.** Cộng/nhân/chia có quy tắc scale riêng, còn rounding context có thể thay đổi scale.
10. **Đi qua `double` khi cần giữ đúng decimal từ chuỗi đầu vào.** Binary64 làm tròn nhiều phân số thập phân trước khi tạo `BigDecimal`.
11. **Đánh đồng chuỗi in với cấu trúc object.** `toPlainString` có thể mất thông tin scale âm nếu parse lại.
12. **Bỏ qua trường hợp zero có nhiều scale hoặc dấu âm đầu vào.** Mọi zero bằng nhau theo giá trị nhưng object equality còn xét scale; BigDecimal không lưu signed zero riêng.
13. **Không xét tràn scale và giới hạn tài nguyên.** “Độ chính xác tùy ý” không có nghĩa xử lý chuỗi/mũ lớn vô hạn trong bộ nhớ thực.
14. **Coi scale là độ tin cậy của một phép đo.** Scale chỉ là một đặc tính biểu diễn decimal; độ bất định vật lý phải được mô tả bằng dữ liệu nghiệp vụ khác.

## 15. Câu hỏi tự luyện

**Câu 1.** Unscaled value 12345, scale 3 biểu diễn giá trị gì?

**Đáp án:** `12345 × 10^-3 = 12.345`.

**Câu 2.** `new BigDecimal("1.23E+4")` có unscaled value và scale là gì? Tại sao scale âm?

**Đáp án:** Unscaled = 123, scale = -2 vì `123 × 10^2 = 12300`. Scale âm là ký hiệu tăng độ lớn bằng lũy thừa của 10, không phải dấu âm của số.

**Câu 3.** Tại sao `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là false nhưng `compareTo` trả 0?

**Đáp án:** `equals` xét cả scale, còn `compareTo` chỉ so sánh giá trị số decimal.

**Câu 4.** `new BigDecimal("123.4500").stripTrailingZeros()` thay đổi gì?

**Đáp án:** Giữ giá trị 123.45, nhưng unscaled từ 1234500 thành 12345 và scale từ 4 thành 2; object nguồn vẫn giữ nguyên.

**Câu 5.** `new BigDecimal("12.3").setScale(3)` khác `new BigDecimal("12.35").setScale(1, HALF_UP)` ra sao?

**Đáp án:** Lần đầu thêm số 0, giữ nguyên giá trị thành 12.300; lần sau cần làm tròn thành 12.4 theo quy tắc HALF_UP.

**Câu 6.** Vì sao `new BigDecimal("1.20").multiply(new BigDecimal("3.4"))` trả `4.080` thay vì bắt buộc `4.08`?

**Đáp án:** Phép nhân chính xác có preferred scale bằng tổng scale của hai toán hạng: 2 + 1 = 3. Hai biểu diễn có cùng giá trị số nhưng scale khác nhau.

**Câu 7.** `BigDecimal.ZERO.precision()` bằng 0 vì zero không có chữ số có nghĩa đúng không?

**Đáp án:** Sai. Theo định nghĩa `BigDecimal`, precision của zero là 1. Đừng nhầm với `MathContext.UNLIMITED` có precision bằng 0 để biểu thị không giới hạn.

**Câu 8.** Khi nào dùng `toString()` thay vì `toPlainString()` để serialize?

**Đáp án:** Nếu muốn round-trip đồng thời giá trị **và scale**, dạng canonical của `toString` phù hợp hơn. `toPlainString` bỏ ký pháp khoa học; parse lại có thể làm thay đổi scale của dữ liệu ban đầu có scale âm.

## Tài liệu tham khảo

- [Oracle Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): công thức giá trị, unscaled value, scale, precision, preferred scale, equals/compareTo, setScale và stripTrailingZeros.
- [Oracle Java SE 21 — BigInteger](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html): biểu diễn số nguyên không giới hạn độ rộng dùng làm unscaled value.
- [Oracle Java SE 21 — MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html): precision, rounding mode và UNLIMITED.
- [Oracle Java SE 21 — RoundingMode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html): ý nghĩa từng quy tắc làm tròn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-003">Precision khác scale</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal`: Precision khác Scale trong Java

## 1. Hiểu đúng hai thuộc tính quan trọng nhất

Trong Java, `java.math.BigDecimal` biểu diễn số thập phân **chính xác theo hệ cơ số 10** bằng một số nguyên không giới hạn độ dài cố định (*unscaled value*) và một số nguyên `scale`. `BigDecimal` không tự đồng nghĩa với “có vô hạn độ chính xác trong mọi phép chia”: cách làm tròn và độ chính xác kết quả phải được kiểm soát khi phép tính không có biểu diễn thập phân hữu hạn hoặc khi ứng dụng quy định số chữ số.

Hai khái niệm rất dễ bị nhầm:

- **Precision (`precision()`):** số chữ số thập phân trong giá trị nguyên không scale, tính từ chữ số khác 0 đầu tiên; với giá trị nguyên không scale bằng 0, precision được định nghĩa là **1**.
- **Scale (`scale()`):** số mũ thập phân dùng để đặt dấu chấm theo quy tắc `unscaledValue × 10^(-scale)`. `scale` có thể **dương, bằng 0 hoặc âm**. Với cách viết decimal thông thường không có ký hiệu số mũ, scale dương thường tương ứng với số chữ số sau dấu thập phân được giữ trong biểu diễn.

Ví dụ:

```java
BigDecimal amount = new BigDecimal("123.4500");

System.out.println(amount.precision());     // 7
System.out.println(amount.scale());         // 4
System.out.println(amount.unscaledValue()); // 1234500
```

Số `123.4500` có bảy chữ số có ý nghĩa trong phần nguyên chưa scale: `1`, `2`, `3`, `4`, `5`, `0`, `0`; dấu thập phân đứng trước bốn chữ số cuối, nên `scale = 4`.

**Điểm nhớ:** `precision` không phải “số chữ số sau dấu phẩy”; `scale` không phải “độ chính xác tổng thể”. Chúng mô tả **hai thuộc tính khác nhau** của một giá trị `BigDecimal`.

## 2. Mô hình toán học: `unscaledValue × 10^(-scale)`

Về mặt khái niệm, một `BigDecimal` mang:

\[
\text{value}=\text{unscaledValue}\times 10^{-\text{scale}}.
\]

Ở đây `unscaledValue()` trả `BigInteger`; `scale()` trả `int`. Mô hình decimal tách riêng **hệ số nguyên có dấu** và **vị trí dấu thập phân**.

Ví dụ:

| Cách tạo | Unscaled value | Scale | Giá trị decimal |
| --- | ---: | ---: | --- |
| `new BigDecimal("123.4500")` | `1234500` | `4` | `123.4500` |
| `new BigDecimal("0.0012300")` | `12300` | `7` | `0.0012300` |
| `new BigDecimal("1000")` | `1000` | `0` | `1000` |
| `new BigDecimal("1E+3")` | `1` | `-3` | `1000` |
| `new BigDecimal("1.20E+3")` | `120` | `-1` | `1200` |
| `new BigDecimal("0.000")` | `0` | `3` | `0.000` |

Ví dụ `new BigDecimal("1E+3")`:

\[
1\times10^{-(-3)}=1\times10^3=1000.
\]

Do đó, scale âm **không phải lỗi** và cũng không có nghĩa số âm: nó biểu thị hệ số nguyên cần được nhân với một lũy thừa dương của 10.

### `precision()` đếm như thế nào?

Precision thường là số chữ số decimal của **trị tuyệt đối unscaled value**, bỏ qua các chữ số 0 vô nghĩa ở đầu. Với `1234500`, precision bằng 7; với `12300`, precision bằng 5. Các số 0 ở cuối **vẫn được tính** nếu tồn tại trong unscaled representation.

Đối với `0`, Java quy định precision là 1, không phải 0:

```java
System.out.println(new BigDecimal("0").precision());     // 1
System.out.println(new BigDecimal("0.000").precision()); // 1
```

Dù biểu diễn `"0.000"` có ba chữ số thập phân phía sau dấu chấm, unscaled value bằng 0 và precision vẫn bằng 1. Không lấy số chữ số trong chuỗi ban đầu để suy ra `precision()` một cách máy móc.

## 3. Các ví dụ đối chiếu Precision và Scale

Hãy đọc đồng thời `precision()`, `scale()` và `unscaledValue()` thay vì dựa vào cách số được in trên màn hình.

| Chuỗi đầu vào | `precision()` | `scale()` | `unscaledValue()` | Giải thích |
| --- | ---: | ---: | ---: | --- |
| `"123.4500"` | 7 | 4 | 1234500 | Hai số 0 cuối được giữ trong unscaled |
| `"0.0012300"` | 5 | 7 | 12300 | Các số 0 trước chữ số 1 không tăng precision |
| `"1000"` | 4 | 0 | 1000 | Bốn chữ số có nghĩa theo biểu diễn |
| `"1E+3"` | 1 | -3 | 1 | Cùng giá trị 1000, scale âm |
| `"1.20E+3"` | 3 | -1 | 120 | Cùng giá trị 1200, ba chữ số unscaled |
| `"0"` | 1 | 0 | 0 | Precision của zero là 1 |
| `"0.00"` | 1 | 2 | 0 | Scale giữ hai chữ số decimal của biểu diễn |
| `"-0.000"` | 1 | 3 | 0 | `BigDecimal` không có số âm 0 tách biệt |

Mã Java để tự khảo sát (đặt bên trong một phương thức hoặc trong JShell):

```java
for (String text : new String[] {
        "123.4500", "0.0012300", "1000",
        "1E+3", "1.20E+3", "0", "0.00"
}) {
    BigDecimal x = new BigDecimal(text);
    System.out.printf(
            "%s -> unscaled=%s, precision=%d, scale=%d%n",
            text, x.unscaledValue(), x.precision(), x.scale());
}
```

`BigDecimal` ghi nhớ giá trị và scale, **không ghi nhớ nguyên xi mọi ký tự chuỗi đầu vào**. Ví dụ `"1.2300E+4"` tạo giá trị `12300` có `scale = 0` và `precision = 5`; cách viết chuỗi nguồn không nhất thiết được giữ để phát lại cùng cú pháp.

### Vì sao precision có thể nhỏ hơn số chữ số hiển thị?

Chuỗi `"0.0012300"` có nhiều ký tự và nhiều số 0 trước chữ số khác 0 đầu tiên. `precision()` không đếm các số 0 dẫn đầu của phần trị decimal. Nó đếm các chữ số của `unscaledValue=12300`, nên trả 5, trong khi `scale=7`.

Với cách viết số mũ, `"1E+3"` có `precision=1` nhưng in dạng decimal đầy đủ là `"1000"` gồm bốn ký tự chữ số. Precision không phải độ dài của `toPlainString()`.

## 4. Hai giá trị bằng nhau nhưng precision và scale khác nhau

`BigDecimal` phân biệt **bằng nhau về giá trị số học** và **bằng nhau theo biểu diễn có scale**.

```java
BigDecimal a = new BigDecimal("2.0");
BigDecimal b = new BigDecimal("2.00");

System.out.println(a.compareTo(b)); // 0
System.out.println(a.equals(b));    // false
```

Cả hai đều biểu diễn số toán học 2, nhưng:

| Biến | Unscaled | Precision | Scale |
| --- | ---: | ---: | ---: |
| `a = "2.0"` | 20 | 2 | 1 |
| `b = "2.00"` | 200 | 3 | 2 |

`compareTo` cho `0` vì hai **giá trị số học** bằng nhau. `equals` trả `false` vì `BigDecimal.equals` yêu cầu cùng giá trị và cùng scale (nên cũng cần có unscaled tương ứng).

Điều này khác `BigInteger`, nơi `compareTo()==0` và `equals` nhất quán với nhau về giá trị.

### Hậu quả với `HashSet` và `TreeSet`

```java
Set<BigDecimal> hash = new HashSet<>();
hash.add(new BigDecimal("2.0"));
hash.add(new BigDecimal("2.00"));

System.out.println(hash.size()); // 2
```

`HashSet` dùng `equals`/`hashCode`, nên nhận hai đối tượng có scale khác nhau làm phần tử khác nhau.

```java
Set<BigDecimal> tree = new TreeSet<>();
tree.add(new BigDecimal("2.0"));
tree.add(new BigDecimal("2.00"));

System.out.println(tree.size()); // 1
```

`TreeSet` dùng thứ tự tự nhiên thông qua `compareTo`; hai số có giá trị toán học bằng nhau được xem là cùng khóa theo thứ tự đó. Không dùng `BigDecimal` làm khóa collection mà chưa quyết định nghiệp vụ muốn phân biệt scale hay chỉ phân biệt giá trị.

### Không dùng `==` để so hai đối tượng BigDecimal

`==` giữa hai tham chiếu `BigDecimal` chỉ so identity, không phải giá trị số. Dùng `compareTo(...) == 0` để kiểm tra cùng giá trị **không phân biệt scale**, hoặc `equals` nếu scale cũng phải khớp. `BigDecimal.ZERO`, `BigDecimal.ONE`, `BigDecimal.TEN` là các hằng tiện ích chứ không biến `BigDecimal` thành primitive.

## 5. `setScale` thay đổi vị trí thập phân; `MathContext` giới hạn precision

Hai API này phục vụ hai mục đích khác nhau.

### 5.1. `setScale(int, RoundingMode)`

`setScale` yêu cầu **scale mục tiêu**:

```java
BigDecimal x = new BigDecimal("123.4500");

BigDecimal y = x.setScale(2, RoundingMode.UNNECESSARY);
BigDecimal z = x.setScale(6);

System.out.println(y); // 123.45
System.out.println(z); // 123.450000
```

Kết quả:

| Giá trị | Precision | Scale |
| --- | ---: | ---: |
| `x = 123.4500` | 7 | 4 |
| `y = 123.45` | 5 | 2 |
| `z = 123.450000` | 9 | 6 |

`setScale(6)` thêm các số 0 cuối cho phù hợp biểu diễn; **giá trị toán học không đổi**. Giảm scale có thể cần bỏ chữ số có ý nghĩa; khi đó phải chọn `RoundingMode` hoặc chấp nhận `ArithmeticException` nếu dùng chế độ `UNNECESSARY`.

Ví dụ:

```java
BigDecimal price = new BigDecimal("12.345");

System.out.println(price.setScale(2, RoundingMode.HALF_UP)); // 12.35
// price.setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException: sẽ phải làm tròn
```

Từ Java 17, còn overload `setScale(int)` mặc định yêu cầu không được mất thông tin do làm tròn. Với thao tác giảm scale có thể mất chữ số, nên **truyền rõ `RoundingMode`** theo chính sách nghiệp vụ.

`setScale` không phải “đặt tổng số chữ số có nghĩa”; nó quy định **số mũ thập phân của biểu diễn**. Ở scale âm, bước làm tròn tương ứng hàng chục, hàng trăm... chứ không còn là giữ các chữ số sau dấu thập phân.

### 5.2. `MathContext` giới hạn số chữ số có nghĩa

`MathContext` cấu hình **precision tối đa của kết quả** và cách làm tròn dùng trong các phép tính có nhận context:

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal result = new BigDecimal("123.4500").round(mc);

System.out.println(result);             // 123
System.out.println(result.precision()); // 3
System.out.println(result.scale());     // 0
```

Ở đây `MathContext(3, HALF_UP)` có nghĩa dùng **tối đa ba chữ số có nghĩa**, **không phải** ba chữ số sau dấu thập phân. Vì vậy `123.4500` làm tròn thành `123` với `scale=0`, chứ không phải `123.450`.

Đối chiếu:

```java
BigDecimal x = new BigDecimal("123.4500");
System.out.println(x.setScale(3, RoundingMode.HALF_UP)); // 123.450
System.out.println(x.round(new MathContext(3, RoundingMode.HALF_UP))); // 123
```

Hai kết quả khác nhau vì câu hỏi khác nhau:

- `setScale(3, ...)` → muốn biểu diễn **ba vị trí thập phân**.
- `round(MathContext(3, ...))` → muốn kết quả có **tối đa ba chữ số có nghĩa**.

Không thay một cái bằng cái kia trong tính tiền, tính giá, đo lường hoặc số liệu khoa học khi hợp đồng nói rõ yêu cầu.

### 5.3. Precision 0 của `MathContext` không phải precision 0 của BigDecimal

`MathContext.UNLIMITED.getPrecision()` trả `0`: trong **ngữ cảnh tính toán**, precision 0 là **không giới hạn precision theo context**.

```java
System.out.println(MathContext.UNLIMITED.getPrecision()); // 0
System.out.println(BigDecimal.ZERO.precision());           // 1
```

Điều đó **không** có nghĩa có một số `BigDecimal` hợp lệ với `precision() == 0` vì bằng 0. Hai API dùng số 0 với **ý nghĩa khác nhau**.

Một phép chia như `1 / 3` không có biểu diễn thập phân hữu hạn: ngay cả khi không giới hạn precision, phép chia chính xác không thể kết thúc trong số chữ số hữu hạn. Nó sẽ cần quy tắc làm tròn hoặc ném ngoại lệ; “unlimited” không biến số thập phân vô hạn thành dữ liệu lưu hữu hạn.

## 6. Rounding có thể đổi cả precision lẫn scale

Làm tròn theo số chữ số có nghĩa có thể phát sinh **carry**, làm thay đổi số mũ và scale theo cách khó đoán nếu chỉ đếm chữ số từ chuỗi.

```java
BigDecimal x = new BigDecimal("999.9");
BigDecimal rounded = x.round(new MathContext(3, RoundingMode.HALF_UP));

System.out.println(rounded);             // 1.00E+3
System.out.println(rounded.precision()); // 3
System.out.println(rounded.scale());     // -1
```

Giá trị kết quả toán học là 1000, nhưng biểu diễn là `unscaled=100`, `scale=-1`, precision 3. Nếu chuyển thành `"1000"` để in thì có bốn ký tự chữ số, **không làm precision tự thành 4**.

Ví dụ thứ hai:

```java
BigDecimal x = new BigDecimal("0.0012345");
BigDecimal y = x.round(new MathContext(3, RoundingMode.HALF_UP));

System.out.println(y); // 0.00123
```

Ở đây ba chữ số có nghĩa là `1`, `2`, `3` dù chuỗi hiển thị chứa nhiều số 0 ở đầu; `scale` của kết quả là 5.

### RoundingMode quyết định điều gì?

`RoundingMode.HALF_UP` làm tròn phần nửa theo hướng tăng độ lớn (xa 0); `HALF_EVEN` chọn chữ số chẵn khi đúng điểm giữa, thường dùng trong phép làm tròn giảm thiên lệch tích lũy; `DOWN` bỏ phần bị loại về phía 0; `UNNECESSARY` yêu cầu không cần làm tròn.

Ví dụ với số dương:

```java
BigDecimal x = new BigDecimal("1.235");
System.out.println(x.setScale(2, RoundingMode.HALF_UP));   // 1.24
System.out.println(x.setScale(2, RoundingMode.HALF_EVEN)); // 1.24
```

Hai chế độ trùng nhau trong ví dụ vì chữ số cuối được giữ là `3` trước dấu nửa? Hãy xem chính xác quy tắc: khi làm tròn `1.235` còn hai chữ số lẻ, chữ số bị loại đầu tiên là `5`, chữ số giữ cuối là `3` (lẻ), vì vậy `HALF_EVEN` cũng làm tròn lên `1.24`. Với `1.245`, `HALF_UP` cho `1.25` còn `HALF_EVEN` cho `1.24` vì chữ số giữ cuối `4` là chẵn.

Không giả định `HALF_UP` hay `HALF_EVEN` là đúng tuyệt đối cho mọi nghiệp vụ; quy tắc kế toán hoặc khoa học của hệ thống phải quyết định.

## 7. `stripTrailingZeros()`: cùng giá trị, biểu diễn mới

Phương thức `stripTrailingZeros()` loại bỏ các số 0 ở cuối trong unscaled representation bằng cách thay đổi scale phù hợp, giữ nguyên **giá trị số học**.

```java
BigDecimal x = new BigDecimal("123.4500");
BigDecimal y = x.stripTrailingZeros();

System.out.println(y);             // 123.45
System.out.println(y.precision()); // 5
System.out.println(y.scale());     // 2
```

Với số có nhiều chữ số 0 hàng đơn vị:

```java
BigDecimal x = new BigDecimal("1000.00");
BigDecimal y = x.stripTrailingZeros();

System.out.println(y);             // 1E+3
System.out.println(y.precision()); // 1
System.out.println(y.scale());     // -3
```

Đây là biến đổi có giá trị học tập lớn: **scale có thể trở thành âm ngay cả khi chuỗi đầu vào hoàn toàn không có ký hiệu số mũ**.

### Trường hợp zero

```java
BigDecimal x = new BigDecimal("0.000");
BigDecimal y = x.stripTrailingZeros();

System.out.println(y);             // 0
System.out.println(y.precision()); // 1
System.out.println(y.scale());     // 0
```

Một số zero có scale dương có thể được chuẩn hóa về `BigDecimal.ZERO` với scale 0. Vì vậy, nếu ứng dụng phải giữ `0.000` để thể hiện độ phân giải của thiết bị đo, **không** tự gọi `stripTrailingZeros()` trước khi lưu trữ dữ liệu đại diện.

### Có thể dùng để so sánh không?

Có thể chủ động chuẩn hóa một quy tắc biểu diễn cụ thể khi muốn dùng `BigDecimal` làm khóa dựa trên giá trị:

```java
BigDecimal canonical = value.stripTrailingZeros();
```

Nhưng **đừng áp dụng vô điều kiện** cho giá tiền, tiền tệ, phép đo hoặc dữ liệu cần giữ scale. Nếu dùng chuẩn hóa, các thành phần hệ thống phải thống nhất quy tắc, cách xử lý số 0, chuỗi output và scale âm. Chỉ cần kiểm tra hai số có cùng giá trị toán học thì `compareTo` thường rõ ràng hơn.

## 8. Phép cộng, nhân, chia: precision và scale thay đổi thế nào?

Các phép `BigDecimal` có kết quả `BigDecimal` bất biến. Scale trong phép tính **không nhất thiết** bằng scale của toán hạng hay precision mà người dùng mong muốn.

### 8.1. Phép cộng và trừ không `MathContext`

Với các phép cộng/trừ chính xác không context, preferred scale là **scale lớn hơn** của hai toán hạng:

```java
BigDecimal a = new BigDecimal("1.20");  // scale 2
BigDecimal b = new BigDecimal("3.004"); // scale 3

BigDecimal sum = a.add(b);
System.out.println(sum);         // 4.204
System.out.println(sum.scale()); // 3
```

Precision của kết quả được suy ra từ giá trị unscaled thực tế, **không đơn giản là tổng precision của hai toán hạng**.

### 8.2. Phép nhân không `MathContext`

Preferred scale của phép nhân chính xác là **tổng scale** hai toán hạng:

```java
BigDecimal a = new BigDecimal("1.20"); // scale 2
BigDecimal b = new BigDecimal("3.0");  // scale 1

BigDecimal product = a.multiply(b);
System.out.println(product);         // 3.600
System.out.println(product.scale()); // 3
```

Dù giá trị toán học là 3.6, biểu diễn giữ scale theo phép nhân chính xác và có precision tương ứng với chữ số unscaled.

### 8.3. Phép chia có thể cần quy tắc làm tròn

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");
```

Phép chia chính xác `one.divide(three)` ném `ArithmeticException` vì kết quả decimal 1/3 vô hạn tuần hoàn. Hai cách giới hạn khác nhau:

```java
BigDecimal byScale =
        one.divide(three, 2, RoundingMode.HALF_UP);
BigDecimal byPrecision =
        one.divide(three, new MathContext(3, RoundingMode.HALF_UP));

System.out.println(byScale);     // 0.33
System.out.println(byPrecision); // 0.333
```

Kết quả đầu có **scale = 2**, precision = 2; kết quả sau có **precision = 3**, scale = 3.

Với các phép có `MathContext`, Java áp dụng preferred scale và quy tắc làm tròn của API, nên scale kết quả có thể khác với công thức scale của phép chính xác, đặc biệt khi phải làm tròn hoặc rút gọn chữ số. Nếu quy trình cuối phải giữ cố định 2 chữ số thập phân, hãy `setScale(2, ...)` theo hợp đồng tại bước thích hợp, thay vì coi `MathContext(2)` đồng nghĩa với `scale=2`.

## 9. Đọc/ghi `BigDecimal`: đừng làm mất scale ngoài ý muốn

### 9.1. Tạo từ chuỗi decimal thay vì `double` tùy tiện

Nếu mục tiêu là giữ đúng decimal `"0.1"` và scale 1:

```java
BigDecimal exact = new BigDecimal("0.1");
System.out.println(exact.precision()); // 1
System.out.println(exact.scale());     // 1
```

Cách khác:

```java
BigDecimal displayValue = BigDecimal.valueOf(0.1);
```

`BigDecimal.valueOf(double)` dùng dạng decimal từ `Double.toString` của giá trị nhị phân đã có; với `0.1`, thường tạo kết quả `0.1`, scale 1.

Còn:

```java
BigDecimal binaryExact = new BigDecimal(0.1);
System.out.println(binaryExact);
// 0.1000000000000000055511151231257827021181583404541015625
```

`new BigDecimal(double)` phản ánh **chính xác giá trị nhị phân của `double`**, vốn không bằng số decimal toán học 0.1. Không dùng constructor từ `double` một cách vô thức khi nhập tiền hoặc số decimal từ người dùng. Nếu đầu vào đã trải qua các phép toán `double` làm mất dữ liệu trước đó, `valueOf(double)` cũng không thể khôi phục bản gốc.

### 9.2. `toString()` có thể xuất dạng số mũ

```java
BigDecimal x = new BigDecimal("1E+3");

System.out.println(x.toString());      // 1E+3
System.out.println(x.toPlainString()); // 1000
```

Cả hai mô tả cùng **giá trị toán học**, nhưng khi parse chuỗi `"1000"` bằng `new BigDecimal`, scale sẽ là 0, **không giữ scale -3** như bản gốc `1E+3`. `toString()` của `BigDecimal` có biểu diễn decimal canonical có thể khôi phục lại đúng unscaled value và scale khi parse trở lại; `toPlainString()` phục vụ hiển thị decimal không số mũ, không hứa giữ scale âm sau round-trip.

Nếu lưu dữ liệu mà **scale là một phần quan trọng của schema**, hãy chọn định dạng serialization và database type bảo toàn scale theo hợp đồng, thay vì chỉ hiển thị `toPlainString()` rồi kỳ vọng mọi metadata được khôi phục.

### 9.3. Làm việc với tiền tệ

Ví dụ:

```java
BigDecimal amount = new BigDecimal("12.3");
BigDecimal currencyValue = amount.setScale(2, RoundingMode.UNNECESSARY);

System.out.println(currencyValue); // 12.30
```

Ở đây `precision` từ 3 thành 4 sau khi thêm số 0 cuối, nhưng **giá trị toán học không thay đổi**. Giữ `scale=2` là yêu cầu biểu diễn tiền với hai chữ số thập phân, **không** phải yêu cầu chỉ giữ hai chữ số chính xác.

Nếu giá trị ban đầu cần làm tròn, phải dùng `setScale(2, roundingMode)` theo quy tắc nghiệp vụ. Không áp dụng `stripTrailingZeros()` cho dữ liệu tiền cần xuất `"12.30"`; nó sẽ bỏ số 0 sau cùng và thay đổi scale.

## 10. Ví dụ Java 17 hoàn chỉnh

Tạo tệp `PrecisionVsScaleDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class PrecisionVsScaleDemo {
    private static void describe(String label, BigDecimal value) {
        System.out.println(label + " = " + value
                + " | unscaled=" + value.unscaledValue()
                + " | precision=" + value.precision()
                + " | scale=" + value.scale());
    }

    public static void main(String[] args) {
        describe("amount", new BigDecimal("123.4500"));
        describe("small", new BigDecimal("0.0012300"));
        describe("integer", new BigDecimal("1000"));
        describe("scientific", new BigDecimal("1E+3"));
        describe("zero", new BigDecimal("0.000"));

        BigDecimal x = new BigDecimal("123.4500");
        describe("scale2", x.setScale(2, RoundingMode.UNNECESSARY));
        describe("scale6", x.setScale(6));
        describe("precision3",
                x.round(new MathContext(3, RoundingMode.HALF_UP)));

        BigDecimal carried = new BigDecimal("999.9")
                .round(new MathContext(3, RoundingMode.HALF_UP));
        describe("carry", carried);

        BigDecimal stripped = new BigDecimal("1000.00")
                .stripTrailingZeros();
        describe("stripped", stripped);

        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        System.out.println("equals 2.0 / 2.00: " + a.equals(b));
        System.out.println("compare 2.0 / 2.00: " + a.compareTo(b));

        BigDecimal byScale = BigDecimal.ONE.divide(
                new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        BigDecimal byPrecision = BigDecimal.ONE.divide(
                new BigDecimal("3"),
                new MathContext(3, RoundingMode.HALF_UP));
        describe("divideScale2", byScale);
        describe("dividePrecision3", byPrecision);

        System.out.println("UNLIMITED context precision: "
                + MathContext.UNLIMITED.getPrecision());
        System.out.println("ZERO value precision: "
                + BigDecimal.ZERO.precision());

        BigDecimal inScientific = new BigDecimal("1E+3");
        System.out.println("scientific toString: "
                + inScientific.toString());
        System.out.println("scientific toPlainString: "
                + inScientific.toPlainString());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac PrecisionVsScaleDemo.java
java PrecisionVsScaleDemo
```

Đầu ra:

```text
amount = 123.4500 | unscaled=1234500 | precision=7 | scale=4
small = 0.0012300 | unscaled=12300 | precision=5 | scale=7
integer = 1000 | unscaled=1000 | precision=4 | scale=0
scientific = 1E+3 | unscaled=1 | precision=1 | scale=-3
zero = 0.000 | unscaled=0 | precision=1 | scale=3
scale2 = 123.45 | unscaled=12345 | precision=5 | scale=2
scale6 = 123.450000 | unscaled=123450000 | precision=9 | scale=6
precision3 = 123 | unscaled=123 | precision=3 | scale=0
carry = 1.00E+3 | unscaled=100 | precision=3 | scale=-1
stripped = 1E+3 | unscaled=1 | precision=1 | scale=-3
equals 2.0 / 2.00: false
compare 2.0 / 2.00: 0
divideScale2 = 0.33 | unscaled=33 | precision=2 | scale=2
dividePrecision3 = 0.333 | unscaled=333 | precision=3 | scale=3
UNLIMITED context precision: 0
ZERO value precision: 1
scientific toString: 1E+3
scientific toPlainString: 1000
```

**Đọc kết quả theo ba cột:** `unscaled`, `precision` và `scale` là thông tin biểu diễn; số decimal được in có thể dùng số mũ. Một số phép có thể thay scale và precision mà **không thay giá trị toán học**, trong khi phép làm tròn có thể thay cả giá trị toán học.

## 11. Lỗi thường gặp

1. **Nghĩ `precision()` là số chữ số sau dấu thập phân.** `new BigDecimal("123.4500")` có precision 7, scale 4.
2. **Nghĩ scale luôn không âm.** `"1E+3"` có scale -3; `stripTrailingZeros()` còn có thể tạo scale âm từ số viết thông thường.
3. **Đếm các số 0 ở đầu để suy precision.** Với `"0.0012300"`, precision là 5 chứ không phải độ dài chuỗi chữ số.
4. **Nghĩ số 0 có precision 0.** Mọi giá trị `BigDecimal` bằng 0 có precision được định nghĩa là 1.
5. **Nhầm `MathContext(2)` với `setScale(2)`.** Một bên giới hạn số chữ số có nghĩa; bên kia xác định vị trí thập phân.
6. **Tin `MathContext.UNLIMITED.getPrecision() == 0` nghĩa kết quả BigDecimal có precision 0.** Số 0 ở `MathContext` là mã “không giới hạn precision”.
7. **Giảm scale mà không chọn `RoundingMode`.** Nếu cần bỏ chữ số khác 0, `setScale` không có rounding có thể ném `ArithmeticException`.
8. **Dùng `equals` để kiểm tra bằng nhau về số mà bỏ qua scale.** `"2.0"` và `"2.00"` có `compareTo == 0` nhưng `equals == false`.
9. **Dùng `BigDecimal` làm khóa `HashSet` và `TreeSet` mà không hiểu khác biệt.** `HashSet` dựa vào `equals`; `TreeSet` dựa vào `compareTo`.
10. **Gọi `stripTrailingZeros` mà vẫn kỳ vọng giữ `"12.30"` hoặc `"0.000"`.** Kết quả có thể đổi scale và cách viết.
11. **Nghĩ `BigDecimal` không bao giờ cần làm tròn.** Phép chia 1/3 phải có context hoặc quy tắc làm tròn; nếu yêu cầu chính xác không thể có kết quả decimal hữu hạn thì `divide` exact ném lỗi.
12. **Dùng `new BigDecimal(0.1)` để nhập decimal 0.1 chính xác.** Constructor này phản ánh giá trị nhị phân của double, không phải nguồn decimal toán học.
13. **Dùng `toPlainString()` rồi parse lại và kỳ vọng giữ scale âm.** `1E+3` thành `"1000"` sẽ được parse với scale 0.
14. **Cho rằng mọi chữ số 0 cuối đều không có ý nghĩa nghiệp vụ.** Trong đo lường và báo cáo, trailing zeros có thể biểu đạt quy tắc độ phân giải; không chuẩn hóa mất chúng khi cần giữ.
15. **Cố đo precision bằng `value.toString().replace(...)`.** Dạng số mũ, dấu âm, số 0 đầu, scale âm làm cách đếm ký tự sai. Dùng `precision()` API.
16. **Dùng `BigDecimal` như thể nó là primitive mutable.** Nó bất biến; mọi `setScale`, `round`, `stripTrailingZeros`, `add`... trả kết quả phải được giữ khi cần.
17. **Dùng scale để suy chắc mức độ chính xác của phép đo.** Scale là thuộc tính biểu diễn, không phải chứng cứ rằng nguồn có sai số nhỏ hay đã được kiểm định đến số chữ số đó.
18. **Cho rằng precision luôn bằng đúng giới hạn của MathContext.** Một kết quả có thể có ít chữ số hơn ngưỡng nếu phép tính không cần đủ số chữ số hoặc các chữ số cuối bị rút gọn theo quy tắc API.

## 12. Bài tập và lời giải

**Câu 1.** `new BigDecimal("123.4500")` có precision và scale bằng bao nhiêu?

**Đáp án:** Precision 7 và scale 4; unscaled value là 1234500.

**Câu 2.** `new BigDecimal("0.0012300")` có bao nhiêu chữ số precision? Tại sao scale lại là 7?

**Đáp án:** Precision 5 vì unscaled value là 12300; scale 7 vì có bảy chữ số sau dấu chấm trong biểu diễn đầu vào.

**Câu 3.** `new BigDecimal("1E+3")` và `new BigDecimal("1000")` có bằng nhau không theo `equals` và theo `compareTo`?

**Đáp án:** `compareTo` cho 0 vì cùng giá trị số 1000; `equals` cho `false` vì scale -3 và scale 0 khác nhau.

**Câu 4.** Một ứng dụng cần hiển thị `12.3` thành `12.30`. Có nên dùng `MathContext(2)` không?

**Đáp án:** Không. Dùng `setScale(2, RoundingMode.UNNECESSARY)` để có scale 2 khi không cần làm tròn. `MathContext(2)` giới hạn hai chữ số có nghĩa, không phải hai số thập phân.

**Câu 5.** Vì sao `new BigDecimal("1000.00").stripTrailingZeros()` có thể in ra `"1E+3"`?

**Đáp án:** Phép chuẩn hóa bỏ các số 0 cuối trong unscaled representation, giữ giá trị 1000 nhưng đổi unscaled về 1 và scale thành -3, nên biểu diễn canonical có dạng số mũ.

**Câu 6.** Với `new BigDecimal("999.9").round(new MathContext(3, HALF_UP))`, kết quả precision/scale là gì?

**Đáp án:** Kết quả là `1.00E+3`, precision 3, scale -1. Phần carry khi làm tròn đổi cả cách đặt dấu decimal lẫn unscaled value.

**Câu 7.** Tại sao `BigDecimal.ZERO.precision()` là 1 nhưng `MathContext.UNLIMITED.getPrecision()` là 0?

**Đáp án:** `precision()` của số 0 dùng quy tắc BigDecimal: 1. `MathContext` dùng giá trị cấu hình 0 để biểu thị precision không giới hạn; hai API có nghĩa khác nhau.

**Câu 8.** Hai cách tính `1 / 3`:

```java
BigDecimal a = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP);
BigDecimal b = BigDecimal.ONE.divide(
        new BigDecimal("3"), new MathContext(3, RoundingMode.HALF_UP));
```

Kết quả là gì?

**Đáp án:** `a = 0.33` (scale 2, precision 2); `b = 0.333` (scale 3, precision 3).

**Câu 9.** Nếu muốn giữ nguyên scale của một `BigDecimal` với scale âm khi serialization rồi parse lại, có nên dùng `toPlainString` không?

**Đáp án:** Không nên nếu yêu cầu round-trip giữ nguyên unscaled value và scale. `toPlainString` của `1E+3` in `"1000"` và parse lại có scale 0; dùng cách serialization có hợp đồng giữ scale, ví dụ chuỗi canonical từ `toString()` khi phù hợp.

**Câu 10.** `BigDecimal("0.000")` có `precision = 3` không? Sau `stripTrailingZeros` thì sao?

**Đáp án:** Không: precision = 1, scale = 3. Sau khi strip, giá trị 0 được chuẩn hóa về scale = 0 và precision vẫn = 1.

**Tóm tắt:** `BigDecimal` lưu `unscaledValue × 10^(-scale)`. `precision` cho biết số chữ số có nghĩa trong unscaled representation (zero có precision 1); `scale` quyết định vị trí dấu thập phân và có thể âm. Dùng `setScale` cho số vị trí decimal theo hợp đồng, dùng `MathContext` cho giới hạn chữ số có nghĩa, và nhớ rằng `equals` phân biệt scale còn `compareTo` so giá trị số.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-004">Scale bằng 0, dương hoặc âm</a>

<details>
<summary>Xem chi tiết</summary>

# Scale bằng 0, dương hoặc âm trong mô hình `BigDecimal`

`java.math.BigDecimal` lưu số thập phân **theo giá trị nguyên không có dấu phẩy và một số nguyên `scale`**. Scale có thể **dương, bằng 0 hoặc âm**. Đó là một phần của biểu diễn dữ liệu, không đơn giản là “bao nhiêu chữ số hiển thị sau dấu chấm”.

Trong Java, giá trị toán học của một `BigDecimal` được xác định bởi:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

`unscaledValue()` trả về `BigInteger`, còn `scale()` trả về `int`. Chỉ cần nhớ quy tắc **mũ âm của scale** là có thể hiểu cả ba trường hợp:

| `unscaledValue` | `scale` | Tính toán | Giá trị |
|---:|---:|---|---:|
| 12345 | 2 | `12345 × 10^-2` | `123.45` |
| 12345 | 0 | `12345 × 10^0` | `12345` |
| 12345 | -2 | `12345 × 10^2` | `1234500` |
| -123 | 3 | `-123 × 10^-3` | `-0.123` |
| 1 | -3 | `1 × 10^3` | `1000` |

Cách mô hình hóa này giúp `BigDecimal` biểu diễn thập phân hữu hạn **chính xác**, không mắc sai số nhị phân vốn phổ biến với `double`. Bài học tập trung vào ý nghĩa của dấu scale, cách quan sát biểu diễn và những hệ quả khi thay đổi scale.

## 1. Scale dương: số chữ số về bên phải dấu thập phân

Ví dụ:

```java
import java.math.BigDecimal;

BigDecimal amount = new BigDecimal("123.4500");

System.out.println(amount.unscaledValue()); // 1234500
System.out.println(amount.scale());         // 4
System.out.println(amount.precision());     // 7
```

Đây là đoạn mã minh họa đặt trong phương thức hoặc JShell. Chuỗi `"123.4500"` có bốn chữ số sau dấu thập phân nên `scale()` bằng **4**. Theo công thức, `1234500 × 10^-4 = 123.4500`.

Hai số `new BigDecimal("1.2")` và `new BigDecimal("1.20")` có **giá trị toán học bằng nhau**, nhưng có `scale` lần lượt là 1 và 2. Các số 0 ở cuối được giữ trong biểu diễn và có thể quan trọng khi dữ liệu cần ghi lại chính xác cách biểu diễn hoặc các ràng buộc scale.

Scale dương **không đảm bảo** giá trị phải nằm giữa -1 và 1. Chẳng hạn `123.45` có scale 2, còn `12345.67` cũng có scale 2. Scale là thông tin về vị trí dấu thập phân so với `unscaledValue`.

## 2. Scale bằng 0: số nguyên theo hệ thập phân

Khi `scale = 0`, công thức trở thành:

\[
\text{value} = \text{unscaledValue} \times 10^0 = \text{unscaledValue}.
\]

Ví dụ:

```java
BigDecimal count = new BigDecimal("12345");
// count.unscaledValue() = 12345
// count.scale() = 0
// count.toPlainString() = "12345"
```

Một `BigDecimal` scale 0 biểu diễn giá trị nguyên; nhưng **không phải mọi `BigDecimal` có giá trị nguyên đều bắt buộc có scale 0**:

- `new BigDecimal("100")` có scale 0.
- `new BigDecimal("100.0")` có scale 1.
- `new BigDecimal("1E+2")` có scale -2.
- Cả ba cùng biểu diễn giá trị số **100**.

Do đó cần phân biệt **giá trị số là một số nguyên** với **biểu diễn có scale bằng 0**.

## 3. Scale âm: biểu diễn bội số của 10

Khi `scale < 0`, số mũ `-scale` là số dương; `unscaledValue` được **nhân** với một lũy thừa của 10. Vì thế:

```java
BigDecimal x = new BigDecimal("1.23E+4");
// x.unscaledValue() = 123
// x.scale() = -2
// x.toPlainString() = "12300"
```

`1.23E+4` nghĩa là `1.23 × 10^4 = 12300`. Trên thực tế, biểu diễn bên trong có `unscaledValue = 123`, `scale = -2`, nên `123 × 10^2 = 12300`.

Có thể tạo trực tiếp biểu diễn này:

```java
import java.math.BigInteger;

BigDecimal x = new BigDecimal(new BigInteger("123"), -2);
// x.scale() = -2
// x.toString() = "1.23E+4"
// x.toPlainString() = "12300"
```

Đây là ví dụ minh họa, cần đặt trong một phương thức và có đủ import. Đừng hiểu “scale âm” là “giá trị âm”: dấu của số nằm ở `unscaledValue`, còn dấu scale điều khiển **lũy thừa của 10**. Một `BigDecimal` với scale -2 vẫn có thể chứa số dương, âm hoặc 0.

Với scale -2, các giá trị được biểu diễn chính xác từ những `unscaledValue` nguyên là bội của 100; với scale -3 là bội của 1000. Chẳng hạn 12345 không thể được giữ nguyên giá trị ở scale -2 vì 12345 không chia hết cho 100.

## 4. `scale` khác `precision`

`scale()` và `precision()` không mô tả cùng một thuộc tính:

- `scale`: số mũ âm trong công thức `unscaled × 10^-scale`, có thể âm, 0 hoặc dương.
- `precision`: số **chữ số thập phân trong `unscaledValue`** (bỏ dấu âm), tính theo định nghĩa của `BigDecimal`; đối với số 0, precision là 1.

| Giá trị được tạo từ chuỗi | `unscaledValue` | `scale` | `precision` |
|---|---:|---:|---:|
| `"123.4500"` | 1234500 | 4 | 7 |
| `"12345"` | 12345 | 0 | 5 |
| `"1.23E+4"` | 123 | -2 | 3 |
| `"0.000"` | 0 | 3 | 1 |
| `"100.00"` | 10000 | 2 | 5 |

Một số có scale dương lớn không có nghĩa precision phải lớn; ví dụ `0.0001` có scale 4 nhưng precision 1. Ngược lại, số có scale âm có thể có precision nhỏ nhưng giá trị rất lớn. Khi muốn khống chế **số chữ số có nghĩa** trong phép toán, Java cung cấp `MathContext`; khi muốn quy định **vị trí phần thập phân**, API liên quan thường là `setScale`.

## 5. `setScale`: thay đổi biểu diễn và khả năng làm tròn

`setScale(int newScale)` yêu cầu kết quả có scale cụ thể. Nếu đổi scale mà vẫn giữ nguyên được giá trị số, Java trả một `BigDecimal` tương đương về toán học. Nếu việc **giảm scale** khiến phải bỏ phần giá trị khác 0, phiên bản một tham số sẽ ném `ArithmeticException` (vì phép làm tròn không được chỉ định).

### Tăng scale: thường thêm các chữ số 0

```java
BigDecimal price = new BigDecimal("1.23");
BigDecimal displayed = price.setScale(4);
// displayed.toPlainString() = "1.2300"
// displayed.unscaledValue() = 12300
// displayed.scale() = 4
// price.scale() vẫn bằng 2
```

Chuyển từ scale 2 sang scale 4 nhân `unscaledValue` với 100, nhưng **giữ nguyên giá trị số 1.23**. Những số 0 được thêm vào biểu diễn.

### Giảm scale chính xác

```java
BigDecimal whole = new BigDecimal("12300");
BigDecimal hundreds = whole.setScale(-2);
// hundreds.toString() = "1.23E+4"
// hundreds.toPlainString() = "12300"
// hundreds.unscaledValue() = 123
// hundreds.scale() = -2
```

Ở đây 12300 chia hết cho 100, nên giảm scale từ 0 xuống -2 không làm mất thông tin. Tương tự, `new BigDecimal("1.2300").setScale(2)` giữ được chính xác giá trị 1.23 vì chỉ bỏ các số 0 cuối.

### Giảm scale không chính xác: phải chọn quy tắc làm tròn

```java
BigDecimal number = new BigDecimal("12345");

// number.setScale(-2); // ArithmeticException
BigDecimal rounded = number.setScale(-2, java.math.RoundingMode.HALF_UP);
// rounded.toPlainString() = "12300"
```

Chọn scale -2 nghĩa là làm tròn tới **hàng trăm**, không phải “giữ âm hai chữ số thập phân”. Giá trị 12345 nằm gần 12300 hơn 12400, nên với `HALF_UP` kết quả là 12300. Với `12350`, trường hợp đúng giữa hai mức 12300 và 12400, `HALF_UP` cho **12400**.

`setScale(int, RoundingMode)` biểu đạt rõ trường hợp cần làm tròn. Đặt chế độ dựa trên quy định nghiệp vụ: `HALF_UP`, `HALF_EVEN`, `DOWN`, `CEILING`, `FLOOR`, v.v. Một phép làm tròn không thể tự quyết định đúng chính sách tính tiền, thuế hoặc số liệu báo cáo nếu ứng dụng chưa xác định **thời điểm** và **quy tắc** làm tròn.

### Bảng quy đổi scale hữu ích

| Giá trị gốc | Scale gốc | Scale mới | Kết quả | Cần làm tròn? |
|---|---:|---:|---|---|
| `1.23` | 2 | 4 | `1.2300` | Không |
| `1.2300` | 4 | 2 | `1.23` | Không |
| `12300` | 0 | -2 | `1.23E+4` | Không |
| `12345` | 0 | -2 | `1.23E+4` với `HALF_UP` | Có |
| `12350` | 0 | -2 | `1.24E+4` với `HALF_UP` | Có |
| `100` | 0 | 0 | `100` | Không |

Bảng dùng biểu diễn thông thường của `toString()`. Với `toPlainString()`, kết quả scale âm ở trên được viết dưới dạng `"12300"` và `"12400"`.

## 6. `stripTrailingZeros`: vì sao scale có thể trở thành âm?

Phương thức `stripTrailingZeros()` loại các chữ số 0 cuối trong biểu diễn mà vẫn giữ **giá trị số**. Nó có thể làm `scale` giảm xuống dưới 0.

```java
BigDecimal initial = new BigDecimal("100.00");
BigDecimal stripped = initial.stripTrailingZeros();

// initial: unscaled=10000, scale=2, toString="100.00"
// stripped: unscaled=1, scale=-2, toString="1E+2"
```

Giải thích: `100.00 = 10000 × 10^-2 = 100`. Sau khi bỏ hết các số 0 cuối của `unscaledValue`, còn `1`; để tiếp tục có giá trị 100, scale phải là -2, vì `1 × 10^2 = 100`.

Một điểm đặc biệt là số 0:

```java
BigDecimal zero = new BigDecimal("0.000");
BigDecimal normalized = zero.stripTrailingZeros();
// normalized có scale=0, precision=1 và biểu diễn là "0"
```

Điều này không có nghĩa mọi số 0 ban đầu đều có scale 0: chỉ **sau** khi gọi `stripTrailingZeros()` ta nhận biểu diễn chuẩn hóa ở trường hợp ví dụ này.

## 7. `equals` và `compareTo`: scale ảnh hưởng kết quả so sánh

`BigDecimal.equals` yêu cầu cả **giá trị số** và **scale** phù hợp. Còn `compareTo` so theo **thứ tự giá trị số**, bỏ qua khác biệt biểu diễn scale.

```java
BigDecimal a = new BigDecimal("1.0");  // scale=1
BigDecimal b = new BigDecimal("1.00"); // scale=2

a.equals(b);          // false
a.compareTo(b) == 0;  // true
```

Vì vậy:

- Dùng `compareTo(...) == 0` nếu cần kiểm tra **bằng nhau về giá trị toán học**.
- Dùng `equals(...)` nếu scale là một phần quan trọng trong khái niệm bằng nhau của dữ liệu.
- `hashCode()` đi cùng hợp đồng `equals`, nên các `BigDecimal` bằng nhau về số học nhưng khác scale **có thể có hash khác nhau**. Nếu dùng làm khóa trong `HashMap`/`HashSet`, phải quyết định chính sách chuẩn hóa trước.
- `stripTrailingZeros()` có thể giúp chuẩn hóa giá trị để so sánh hoặc tạo khóa, nhưng làm thay đổi scale biểu diễn; chỉ áp dụng khi điều đó phù hợp với hợp đồng dữ liệu.

Đặc biệt, `new BigDecimal("100")` và `new BigDecimal("1E+2")` có cùng giá trị số, nhưng scale lần lượt là 0 và -2, nên `equals` là `false`, còn `compareTo` trả 0.

## 8. `toString`, `toPlainString` và ký pháp khoa học

Khi scale âm, `BigDecimal.toString()` thường biểu diễn bằng ký pháp khoa học:

```java
BigDecimal number = new BigDecimal("1.23E+4");

number.toString();      // "1.23E+4"
number.toPlainString(); // "12300"
number.scale();         // -2
```

`toString()` tạo biểu diễn chuẩn theo quy tắc của `BigDecimal` và giữ thông tin cần thiết cho việc round-trip biểu diễn. `toPlainString()` bỏ ký pháp số mũ, thường dễ hiển thị với người dùng, nhưng **không tự giữ metadata scale âm khi được phân tích ngược từ chuỗi**:

```java
BigDecimal x = new BigDecimal("1E+3"); // scale=-3
BigDecimal y = new BigDecimal(x.toPlainString()); // scale=0
// x.compareTo(y) == 0, nhưng x.equals(y) == false
```

Với dữ liệu lưu trữ cần bảo toàn scale, phải giữ biểu diễn phù hợp, hoặc lưu rõ `unscaledValue` và `scale`. Không nên dựa vào chuỗi thập phân dạng plain để phục hồi được scale âm.

## 9. `movePointRight`, `movePointLeft` và `scaleByPowerOfTen`

Các phương thức dịch dấu thập phân được sử dụng khi cần nhân/chia cho một lũy thừa của 10:

- `movePointRight(n)`: tăng giá trị lên `10^n`, nhưng đảm bảo scale kết quả **không âm**.
- `movePointLeft(n)`: chia giá trị cho `10^n`, cũng đảm bảo scale kết quả **không âm**.
- `scaleByPowerOfTen(n)`: nhân giá trị với `10^n`, chủ yếu bằng cách điều chỉnh `scale` thành `scale - n`; scale kết quả **có thể âm**.

Ví dụ cùng giá trị khởi đầu:

```java
BigDecimal original = new BigDecimal("1.00"); // unscaled=100, scale=2

BigDecimal moved = original.movePointRight(3);
// moved.toPlainString() = "1000", scale=0

BigDecimal scaled = original.scaleByPowerOfTen(3);
// scaled.toString() = "1.00E+3", scale=-1
// scaled.toPlainString() = "1000"
```

Hai kết quả cùng **giá trị số 1000**, nhưng có scale khác nhau (0 và -1); `compareTo` cho bằng nhau nhưng `equals` không bằng nhau. Đây là khác biệt có thể ảnh hưởng lưu trữ, định dạng và logic đồng nhất dữ liệu.

Các thao tác nhân/chia lũy thừa lớn có thể cần nhiều tài nguyên hoặc gây `ArithmeticException` trong trường hợp scale tính ra vượt miền `int`. Đừng coi scale là một con số luôn tăng/giảm vô hạn mà không có giới hạn.

## 10. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp **`BigDecimalScaleDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class BigDecimalScaleDemo {
    static void show(String name, BigDecimal x) {
        System.out.println(name + ": " + x.toString()
                + " | plain=" + x.toPlainString()
                + " | unscaled=" + x.unscaledValue()
                + " | scale=" + x.scale()
                + " | precision=" + x.precision());
    }

    public static void main(String[] args) {
        show("positive", new BigDecimal("123.4500"));
        show("zero", new BigDecimal("12345"));
        show("negative", new BigDecimal("1.23E+4"));
        show("constructor", new BigDecimal(new BigInteger("123"), -2));

        show("raise scale", new BigDecimal("1.23").setScale(4));
        show("exact lower", new BigDecimal("12300").setScale(-2));
        show("strip", new BigDecimal("100.00").stripTrailingZeros());
        show("zero after strip", new BigDecimal("0.000").stripTrailingZeros());

        BigDecimal a = new BigDecimal("1.0");
        BigDecimal b = new BigDecimal("1.00");
        System.out.println("equal objects: " + a.equals(b));
        System.out.println("same numeric value: " + (a.compareTo(b) == 0));

        try {
            new BigDecimal("12345").setScale(-2);
        } catch (ArithmeticException ex) {
            System.out.println("inexact setScale: ArithmeticException");
        }
        show("rounded", new BigDecimal("12350").setScale(-2, RoundingMode.HALF_UP));
        show("movePointRight", new BigDecimal("1.00").movePointRight(3));
        show("scaleByPowerOfTen", new BigDecimal("1.00").scaleByPowerOfTen(3));
    }
}
```

Chạy với JDK 17+:

```bash
javac -encoding UTF-8 BigDecimalScaleDemo.java
java BigDecimalScaleDemo
```

Stdout đã được xác minh bằng cách biên dịch và thực thi Java:

```text
positive: 123.4500 | plain=123.4500 | unscaled=1234500 | scale=4 | precision=7
zero: 12345 | plain=12345 | unscaled=12345 | scale=0 | precision=5
negative: 1.23E+4 | plain=12300 | unscaled=123 | scale=-2 | precision=3
constructor: 1.23E+4 | plain=12300 | unscaled=123 | scale=-2 | precision=3
raise scale: 1.2300 | plain=1.2300 | unscaled=12300 | scale=4 | precision=5
exact lower: 1.23E+4 | plain=12300 | unscaled=123 | scale=-2 | precision=3
strip: 1E+2 | plain=100 | unscaled=1 | scale=-2 | precision=1
zero after strip: 0 | plain=0 | unscaled=0 | scale=0 | precision=1
equal objects: false
same numeric value: true
inexact setScale: ArithmeticException
rounded: 1.24E+4 | plain=12400 | unscaled=124 | scale=-2 | precision=3
movePointRight: 1000 | plain=1000 | unscaled=1000 | scale=0 | precision=4
scaleByPowerOfTen: 1.00E+3 | plain=1000 | unscaled=100 | scale=-1 | precision=3
```

Chương trình cho thấy mối liên hệ nhất quán giữa ba thông tin `unscaled`, `scale`, `precision`. Hai dòng `negative`/`constructor` minh họa hai cách tạo cùng biểu diễn scale âm. Dòng `strip` cho thấy chuẩn hóa có thể tạo scale âm; `zero after strip` cho thấy trường hợp số 0 được chuẩn hóa về scale 0. Hai dòng `equal objects`/`same numeric value` phân biệt `equals` với `compareTo`. Cuối cùng `movePointRight` và `scaleByPowerOfTen` tạo **cùng giá trị** nhưng **khác scale**.

## 11. Những lỗi thường gặp

1. **Cho rằng scale luôn không âm.** Chuỗi dạng số mũ như `"1E+3"` hoặc `stripTrailingZeros()` có thể dẫn tới scale âm một cách hợp lệ.
2. **Cho rằng scale âm là số âm.** Scale âm thể hiện nhân với `10` mũ dương; dấu của giá trị độc lập với dấu của scale.
3. **Nhầm scale với precision.** `123.4500` có scale 4 nhưng precision 7; `1.23E+4` có scale -2 nhưng precision 3.
4. **Cho rằng giá trị số nguyên nhất thiết phải scale 0.** `100`, `100.0`, `1E+2` cùng giá trị số nhưng scale lần lượt 0, 1, -2.
5. **Giảm scale mà bỏ qua việc làm tròn.** `setScale(-2)` trên `12345` ném `ArithmeticException`; nếu chủ đích làm tròn tới hàng trăm, chỉ định `RoundingMode`.
6. **Lạm dụng `equals` để so giá trị toán học.** `1.0.equals(1.00)` là false; dùng `compareTo == 0` khi chỉ xét giá trị.
7. **Dùng `toPlainString` để lưu mà mong phục hồi scale âm.** Chuỗi `"1000"` phân tích ngược có scale 0, khác `"1E+3"` có scale -3.
8. **Tưởng rằng `stripTrailingZeros` chỉ giảm scale tới 0.** `100.00` chuẩn hóa thành `1E+2` có scale -2.
9. **Trộn `BigDecimal` với `double` mà bỏ qua biểu diễn.** `new BigDecimal(0.1)` tạo số thập phân chính xác tương ứng giá trị nhị phân `double` đã làm tròn; để biểu đạt 0.1 thập phân chính xác, dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)`.
10. **Suy luận scale kết quả mọi phép số học đều giống một toán hạng.** Ví dụ với phép nhân, scale ưu tiên của kết quả là tổng scale hai toán hạng (trừ các trường hợp giới hạn/điều chỉnh biểu diễn); các phép khác có quy tắc riêng. Sau phép tính, đọc `scale()` thực tế nếu đây là một phần của hợp đồng dữ liệu.

## 12. Bài tập tự kiểm tra

Dựa vào công thức `value = unscaled × 10^-scale`, hãy xác định giá trị số và `scale`:

| Câu hỏi | Đáp án |
|---|---|
| `new BigDecimal("3.1400")` có scale bao nhiêu? | 4; unscaled là 31400 |
| `new BigDecimal("4E+3")` có scale và giá trị nào? | Scale -3; giá trị 4000 |
| `new BigDecimal("150").setScale(-1)` có thành công không? | Có; giá trị 150; scale -1; `toString()` là `"1.5E+2"` |
| `new BigDecimal("155").setScale(-1)` có thành công không? | Không; ném `ArithmeticException` nếu không có rounding mode |
| `new BigDecimal("100.0").stripTrailingZeros()` có scale nào? | -2; giá trị 100; biểu diễn `"1E+2"` |
| `"100.0"` và `"1E+2"` có `compareTo == 0` không? | Có, vì cùng giá trị số 100 |
| Hai giá trị ở câu trên có `equals` không? | Không, vì scale 1 khác -2 |
| `new BigDecimal("0.000").precision()` trả gì? | 1 |

Để tự kiểm chứng thêm, thử lấy một `BigDecimal` bất kỳ rồi in `toString`, `toPlainString`, `unscaledValue`, `scale` và `precision`. Dự đoán trước những giá trị nào khiến `setScale` giảm được chính xác, những giá trị nào bắt buộc làm tròn.

## Ghi nhớ

- Công thức nền tảng là **`value = unscaledValue × 10^-scale`**.
- Scale **dương** chia phần nguyên biểu diễn cho lũy thừa của 10; scale **0** giữ nguyên; scale **âm** nhân với lũy thừa của 10.
- Scale là một phần của **biểu diễn**, không phải dấu số hay precision.
- `setScale` có thể bảo toàn giá trị hoặc cần làm tròn; `stripTrailingZeros` có thể tạo scale âm.
- `equals` xét cả scale; `compareTo` xét giá trị toán học.
- Khi cần bảo toàn scale âm qua lưu trữ, phải chọn biểu diễn có giữ được thông tin đó, thay vì chỉ dựa vào `toPlainString`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-005">Tạo từ String, BigInteger, long và unscaled value</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo BigDecimal từ String, BigInteger, long và unscaled value trong Java

`java.math.BigDecimal` là kiểu số thập phân có độ chính xác tùy ý, thích hợp khi cần biểu diễn các giá trị decimal mà `float`/`double` không thể giữ chính xác, chẳng hạn số tiền, đơn giá, tỷ lệ hoặc dữ liệu số thập phân do hệ thống bên ngoài gửi tới. Khi tạo `BigDecimal`, một lựa chọn constructor tưởng như nhỏ có thể **thay đổi scale, độ chính xác, ý nghĩa của dữ liệu và kết quả `equals`**.

Bốn hướng khởi tạo chính trong bài:

1. **Từ chuỗi decimal:** `new BigDecimal("12.3400")` — giữ chính xác các chữ số thập phân đã nhập và scale.
2. **Từ số nguyên lớn `BigInteger`:** `new BigDecimal(new BigInteger("123400"))` — tạo decimal có scale bằng 0.
3. **Từ `long`:** `new BigDecimal(123400L)` hoặc `BigDecimal.valueOf(123400L)` — cũng có scale bằng 0.
4. **Từ *unscaled value* cùng *scale*:** `new BigDecimal(new BigInteger("123400"), 4)` hoặc `BigDecimal.valueOf(123400L, 4)` — tạo giá trị `12.3400`.

Điểm cốt lõi là công thức:

\[
\boxed{\text{BigDecimal value}=\text{unscaledValue}\times 10^{-\text{scale}}}
\]

**Giữ nguyên giá trị số** chưa đủ để giữ nguyên mô hình `BigDecimal`: hai đối tượng có cùng giá trị toán học nhưng khác scale có thể cho `equals() == false`. Chọn cách xây dựng phù hợp với *ngữ nghĩa dữ liệu đầu vào*, không chỉ chọn cách nào biên dịch được.

## 1. Mô hình nội tại: unscaledValue × 10^(-scale)

Một `BigDecimal` về bản chất có hai thành phần:

- Một **giá trị nguyên không scale** (*unscaled value*) với độ chính xác tùy ý, có thể lấy qua `unscaledValue()`.
- Một **scale** kiểu `int`, có thể lấy qua `scale()`.

Ví dụ:

| Cách biểu diễn | Unscaled value | Scale | Giá trị toán học |
| --- | ---: | ---: | ---: |
| `"12.34"` | 1234 | 2 | 12.34 |
| `"12.3400"` | 123400 | 4 | 12.3400 |
| `"1234"` | 1234 | 0 | 1234 |
| `"1.234E+3"` | 1234 | 0 | 1234 |
| `"1.23E+4"` | 123 | -2 | 12300 |
| `"0.000"` | 0 | 3 | 0.000 |
| `"-12.34"` | -1234 | 2 | -12.34 |

Với `scale = 4`, lấy `123400 × 10^-4 = 12.3400`. Với `scale = -2`, lấy `123 × 10^2 = 12300`. **Scale có thể âm**, không phải lúc nào cũng là “số chữ số sau dấu chấm”. Đây là lỗi suy luận thường gặp khi đọc dữ liệu ở ký hiệu khoa học.

Quan sát trực tiếp:

```java
import java.math.BigDecimal;

BigDecimal x = new BigDecimal("12.3400");
System.out.println(x.unscaledValue()); // 123400
System.out.println(x.scale());         // 4
System.out.println(x.precision());     // 6
```

`precision()` là số chữ số có nghĩa của **unscaled value** trong cách biểu diễn thập phân, khác `scale()`. `123400` có sáu chữ số, nên precision bằng 6. Không nhầm precision với tổng số chữ số ở phần lẻ.

`BigDecimal` là **immutable**: constructor tạo giá trị mới, còn các phép cộng/nhân/`setScale` trả giá trị mới, không sửa đối tượng cũ.

## 2. Tạo từ String: giữ đúng giá trị decimal nhập vào

Khi nguồn đã là **chuỗi thập phân**, lựa chọn trực tiếp nhất là:

```java
BigDecimal price = new BigDecimal("12.3400");
BigDecimal tiny = new BigDecimal("0.000000001");
BigDecimal large = new BigDecimal("123456789012345678901234567890.123");
```

Constructor `BigDecimal(String)` **phân tích giá trị decimal chính xác** của chuỗi; nó không đi qua `double`, nên không nhận thêm sai số nhị phân. Các số có rất nhiều chữ số vẫn được biểu diễn theo precision cần thiết trong giới hạn tài nguyên.

### Chuỗi quyết định scale

```java
BigDecimal a = new BigDecimal("12.34");
BigDecimal b = new BigDecimal("12.3400");

System.out.println(a.scale()); // 2
System.out.println(b.scale()); // 4
System.out.println(a.compareTo(b)); // 0: bằng nhau về giá trị số
System.out.println(a.equals(b));    // false: scale khác
```

Các số 0 ở cuối **có thể mang thông tin biểu diễn**. Nếu hệ thống yêu cầu lưu số đo đến bốn chữ số sau dấu thập phân hoặc lưu đơn giá với scale chuẩn, giá trị `"12.3400"` khác `"12.34"` về biểu diễn, mặc dù hai số so sánh bằng nhau theo `compareTo`.

### Cú pháp chuỗi decimal

Những chuỗi sau được hỗ trợ:

```java
new BigDecimal("+12.5");
new BigDecimal("-0.25");
new BigDecimal(".5");
new BigDecimal("5.");
new BigDecimal("1.23E+4");
new BigDecimal("1e-7");
```

Hậu tố exponent `e`/`E` có thể tạo **scale âm hoặc dương** tùy vị trí dấu chấm và độ lớn số mũ. Ví dụ:

```java
BigDecimal scientific = new BigDecimal("1.23E+4");
System.out.println(scientific.unscaledValue()); // 123
System.out.println(scientific.scale());         // -2
System.out.println(scientific.toString());      // 1.23E+4
System.out.println(scientific.toPlainString()); // 12300
```

Constructor nhận String **không phải parser số địa phương** và không tự động làm sạch mọi dữ liệu:

- `new BigDecimal("1,5")` gây `NumberFormatException`, không phụ thuộc vào Locale máy.
- `new BigDecimal("1_000")` gây `NumberFormatException`; dấu gạch dưới có thể hợp lệ trong literal nguồn Java, nhưng **không** trong chuỗi BigDecimal.
- `new BigDecimal("NaN")` và `new BigDecimal("Infinity")` không tạo giá trị BigDecimal đặc biệt; cả hai đều sai cú pháp.
- `new BigDecimal(" 12.5 ")` không tự động loại khoảng trắng đầu/cuối. Nếu hợp đồng cho phép khoảng trắng, có thể chuẩn hóa **có kiểm soát** trước khi parse.
- Tham số String `null` không đại diện cho số 0; cần kiểm tra null trước khi parse, thay vì tự nhận là 0.

**Không âm thầm xóa dấu, separator hoặc ký tự lạ**. Với dữ liệu đầu vào từ API/CSV, hãy định nghĩa rõ ngữ pháp được phép rồi báo lỗi khi không hợp lệ.

## 3. Tạo từ BigInteger: scale mặc định là 0

`BigInteger` là số nguyên có dấu với precision tùy ý. Constructor:

```java
import java.math.BigInteger;

BigInteger integer = new BigInteger("123400");
BigDecimal decimal = new BigDecimal(integer);

System.out.println(decimal.toPlainString()); // 123400
System.out.println(decimal.unscaledValue()); // 123400
System.out.println(decimal.scale());         // 0
```

**Đừng suy luận số nguyên `123400` có dấu thập phân ẩn.** `new BigDecimal(BigInteger)` đặt **scale 0**, nên đại diện cho **123400**, không phải `12.3400`. Nếu `BigInteger` lưu số tiền ở đơn vị cent, micro-unit hoặc tỷ lệ đã nhân hệ số, cần **chuyển scale theo contract**.

Ví dụ dữ liệu đầu vào chứa 123400 **đơn vị nhỏ nhất 1/10000**:

```java
BigInteger storedUnits = new BigInteger("123400");
BigDecimal restored = new BigDecimal(storedUnits, 4);
// 12.3400
```

Dùng số nguyên lớn là cách tốt khi dữ liệu upstream đã lưu bằng **unscaled integer**, chẳng hạn số lượng micro-unit vượt phạm vi `long`. Đó là mô hình **số nguyên + scale**, không phải phép “làm tròn sang số thực”.

### Khác nhau giữa toString(2) và chuỗi decimal

`new BigInteger("101101", 2)` tạo số 45 từ chuỗi nhị phân; `new BigDecimal(theBigInteger)` cho **45** decimal với scale 0. `new BigDecimal("101101")` lại tạo số thập phân một trăm linh một nghìn một trăm linh một. **Radix** là một phần của constructor `BigInteger`, không tự được áp dụng cho chuỗi truyền vào `BigDecimal(String)`.

## 4. Tạo từ long: số nguyên đúng giá trị, scale bằng 0

Đối với một `long` đã có:

```java
long units = 123400L;

BigDecimal fromConstructor = new BigDecimal(units);
BigDecimal fromFactory = BigDecimal.valueOf(units);
```

Cả hai cách đều có giá trị số **123400** và `scale() == 0`. Vì `long` chứa giá trị nguyên chính xác trong miền \([-2^{63}, 2^{63}-1]\), việc chuyển từ `long` sang `BigDecimal` không làm mất chữ số nguyên nào.

`BigDecimal.valueOf(long)` là factory thuận tiện và **có thể tái sử dụng các đối tượng đã tạo**, nên thường được ưu tiên khi không cần constructor trực tiếp. Tuy nhiên, **không dựa vào identity của object** khi viết nghiệp vụ:

```java
BigDecimal a = BigDecimal.valueOf(7L);
BigDecimal b = BigDecimal.valueOf(7L);

System.out.println(a.equals(b)); // true
// Đừng dùng a == b để kiểm tra hai giá trị số bằng nhau.
```

`BigDecimal` là đối tượng. `a == b` hỏi chúng có cùng tham chiếu hay không, không phải hai số có cùng giá trị.

### Nếu long là đơn vị nhỏ nhất, phải truyền scale

Giả sử 12345 là số cent và 1 đơn vị chính chứa 100 cent:

```java
long cents = 12345L;
BigDecimal amount = BigDecimal.valueOf(cents, 2);
// 123.45
```

Nếu viết `BigDecimal.valueOf(cents)` không truyền scale, kết quả là `12345` — **lệch 100 lần** so với giá trị thật. Đây là lỗi dữ liệu, không phải lỗi làm tròn.

Không chuyển số rất lớn vượt `long` thành `long` chỉ để gọi factory; khi giá trị unscaled vượt miền `long`, hãy giữ nó trong `BigInteger`.

## 5. Tạo từ unscaled value: truyền đúng scale

`BigDecimal` có hai cách thuận tiện để gắn decimal scale lên **một giá trị nguyên**:

```java
// Với BigInteger không giới hạn phạm vi long:
BigDecimal a = new BigDecimal(new BigInteger("123400"), 4);

// Với giá trị unscaled đã nằm trong long:
BigDecimal b = BigDecimal.valueOf(123400L, 4);

System.out.println(a.toPlainString()); // 12.3400
System.out.println(b.toPlainString()); // 12.3400
```

Với unscaled value `123400` và scale `4`:

\[
123400\times10^{-4}=12.3400.
\]

Hãy coi `scale` là **tham số mang ngữ nghĩa**, không phải một gợi ý định dạng tùy ý. Chỉ cần đổi scale từ 4 thành 2, giá trị toán học sẽ đổi từ `12.3400` thành `1234.00` dù unscaled value không đổi.

| Unscaled | Scale | `toPlainString()` |
| ---: | ---: | --- |
| 123400 | 0 | `123400` |
| 123400 | 2 | `1234.00` |
| 123400 | 4 | `12.3400` |
| 123400 | 6 | `0.123400` |
| 123 | -2 | `12300` |
| -123400 | 4 | `-12.3400` |

`BigDecimal.valueOf(long unscaledVal, int scale)` chấp nhận cả **scale âm**. Ví dụ:

```java
BigDecimal x = BigDecimal.valueOf(123L, -2);
System.out.println(x.toString());      // 1.23E+4
System.out.println(x.toPlainString()); // 12300
System.out.println(x.unscaledValue()); // 123
System.out.println(x.scale());         // -2
```

**Scale âm không phải lỗi**: có nghĩa nhân với \(10^{|\text{scale}|}\). Khi xuất chuỗi, `toString()` và `toPlainString()` có thể khác định dạng. Nếu phải truyền giá trị cùng scale giữa các hệ thống, nên truyền riêng unscaled value + scale, hoặc chọn biểu diễn văn bản có quy tắc round-trip rõ ràng.

## 6. Mối quan hệ với setScale và movePointLeft

Ba thao tác có thể cùng cho `12.3400` nhưng mang **đầu vào và mục đích khác nhau**:

```java
BigDecimal x = new BigDecimal(new BigInteger("123400"), 4);
BigDecimal y = BigDecimal.valueOf(123400L, 4);
BigDecimal z = BigDecimal.valueOf(123400L).movePointLeft(4);
```

Với `x`, `y`, `z`, kết quả số đều bằng `12.3400` và trong trường hợp cụ thể này có cùng scale 4. Tuy nhiên, các phương thức không có ý nghĩa chung cho mọi đầu vào:

- **Constructor/factory với unscaled + scale:** diễn giải cặp dữ liệu lưu trữ thành đúng một BigDecimal.
- **`movePointLeft(n)`:** biến đổi giá trị số có sẵn bằng nhân \(10^{-n}\); scale mới còn phụ thuộc ngữ nghĩa đặc tả của phương thức, đặc biệt khi đang có scale âm.
- **`setScale(newScale, mode)`:** đổi **scale biểu diễn** nhưng giữ cùng giá trị toán học nếu có thể; khi phải loại chữ số có nghĩa, cần chính sách **rounding**.

Ví dụ không được coi `setScale(4)` giống `movePointLeft(4)`:

```java
BigDecimal value = BigDecimal.valueOf(123400L);
BigDecimal changedScale = value.setScale(4); // 123400.0000
BigDecimal shiftedPoint = value.movePointLeft(4); // 12.3400
```

`setScale(4)` thêm bốn số 0 phần lẻ để giữ nguyên **giá trị 123400**; `movePointLeft(4)` thực sự chia giá trị cho \(10^4\).

Nếu mục đích là khôi phục từ unscaled value, cách rõ nhất vẫn là constructor hoặc factory **hai tham số**.

## 7. Khi có MathContext: constructor có thể làm tròn ngay lúc khởi tạo

Các overload nhận `MathContext` như `new BigDecimal(String, MathContext)`, `new BigDecimal(BigInteger, MathContext)` và `new BigDecimal(long, MathContext)` **khác** constructor không truyền context: chúng có thể chủ động giới hạn **precision** và làm tròn giá trị.

```java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext context = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal exact = new BigDecimal("12345");
BigDecimal rounded = new BigDecimal("12345", context);

System.out.println(exact.toString());   // 12345
System.out.println(rounded.toString()); // 1.23E+4
```

Giá trị mới `1.23E+4` là `12300`; **không còn giữ nguyên giá trị 12345**. Vì vậy lời gọi constructor không phải lúc nào chỉ “chuyển kiểu” mà không đổi số.

| Dạng constructor | Scale khi không dùng MathContext | Có thể làm tròn trong lúc tạo? |
| --- | --- | --- |
| `BigDecimal(String)` | Suy ra từ cú pháp decimal | Không, giữ giá trị decimal chính xác |
| `BigDecimal(BigInteger)` | 0 | Không |
| `BigDecimal(long)` | 0 | Không |
| `BigDecimal(BigInteger, int)` | Scale truyền vào | Không |
| `BigDecimal(String, MathContext)` | Theo kết quả sau round | Có |
| `BigDecimal(BigInteger, MathContext)` | Theo kết quả sau round | Có |
| `BigDecimal(long, MathContext)` | Theo kết quả sau round | Có |

`MathContext.precision()` **không phải** số chữ số sau dấu chấm. Nó giới hạn số **chữ số có nghĩa**. Nếu mục tiêu nghiệp vụ là làm tròn số tiền đến 2 chữ số decimal, không tự dùng `new MathContext(2)` thay cho `setScale(2, roundingMode)`.

Nếu cần lưu giá trị gốc chính xác rồi chỉ làm tròn tại bước chốt, hãy tạo BigDecimal chính xác trước và tách bước làm tròn riêng.

## 8. Giá trị BigDecimal bằng nhau nhưng scale có thể khác

`BigDecimal.compareTo` so sánh **giá trị số**; `BigDecimal.equals` yêu cầu **cùng giá trị và cùng scale**.

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

System.out.println(a.compareTo(b)); // 0
System.out.println(a.equals(b));    // false
System.out.println(a.scale());      // 1
System.out.println(b.scale());      // 2
```

Điều này có ảnh hưởng trực tiếp khi dùng `BigDecimal` làm khóa trong collection:

- `HashSet<BigDecimal>` sử dụng `equals`/`hashCode`, nên có thể giữ **hai phần tử** `1.0` và `1.00`.
- `TreeSet<BigDecimal>` với thứ tự tự nhiên sử dụng `compareTo` nên thường xem hai giá trị ấy là **trùng nhau**.

Nếu nghiệp vụ muốn bỏ qua trailing zeros để so sánh khóa, có thể dùng chuẩn hóa `stripTrailingZeros()`, nhưng phải hiểu rằng **scale có thể trở thành số âm**:

```java
BigDecimal x = new BigDecimal("1000.00");
BigDecimal normalized = x.stripTrailingZeros();
System.out.println(normalized.toString()); // 1E+3
System.out.println(normalized.scale());    // -3
```

Chuẩn hóa là **quyết định nghiệp vụ**, không phải bước bắt buộc sau mọi constructor. Nếu scale mang ý nghĩa như độ chính xác đo lường hoặc đơn vị tiền tệ, đừng vô tình loại bỏ nó.

## 9. Tránh bẫy tạo BigDecimal qua double

Lý do thường xuyên cần `BigDecimal(String)` là nhiều phân số thập phân **không thể biểu diễn chính xác bằng double**.

```java
BigDecimal exact = new BigDecimal("0.1");
BigDecimal fromDoubleConstructor = new BigDecimal(0.1);
BigDecimal fromDoubleFactory = BigDecimal.valueOf(0.1);
```

Ba cách không hoàn toàn tương đương:

- `new BigDecimal("0.1")`: giá trị decimal đúng bằng một phần mười.
- `new BigDecimal(0.1)`: biểu diễn **chính xác giá trị nhị phân xấp xỉ 0.1** đang nằm trong `double`; thường có rất nhiều chữ số decimal.
- `BigDecimal.valueOf(0.1)`: dùng chuỗi biểu diễn chuẩn của `double` (`Double.toString`) để tạo BigDecimal; với `0.1` cho `0.1`, nhưng **không hồi phục mọi chữ số decimal đã mất trước đó**.

```java
System.out.println(new BigDecimal("0.1").toPlainString());
// 0.1
System.out.println(BigDecimal.valueOf(0.1).toPlainString());
// 0.1
System.out.println(new BigDecimal(0.1).toPlainString());
// 0.1000000000000000055511151231257827021181583404541015625
```

Nếu dữ liệu gốc là decimal từ JSON, database hoặc chuỗi người dùng, giữ **dữ liệu gốc dạng chuỗi hoặc dạng số nguyên + scale** đến khi tạo BigDecimal. Đừng đưa qua `double` rồi hy vọng constructor `BigDecimal` sẽ tái dựng precision đã mất.

## 10. Số 0, số âm và exponent

### Zero có scale vẫn có ý nghĩa biểu diễn

```java
BigDecimal a = new BigDecimal("0");
BigDecimal b = new BigDecimal("0.00");
BigDecimal c = BigDecimal.valueOf(0L, 2);
```

Tất cả có **giá trị số 0**, nhưng `a.scale() == 0`, còn `b.scale() == 2` và `c.scale() == 2`. Nếu đầu vào `"-0.00"`, `BigDecimal` không bảo tồn “âm zero” theo cách `double` làm: kết quả có giá trị **zero với signum() bằng 0**, scale vẫn 2.

```java
BigDecimal negativeZeroText = new BigDecimal("-0.00");
System.out.println(negativeZeroText.toPlainString()); // 0.00
System.out.println(negativeZeroText.signum());        // 0
System.out.println(negativeZeroText.scale());         // 2
```

Nếu hệ thống cần lưu **dấu âm nguyên văn người dùng đã nhập** ở zero, phải lưu thông tin văn bản/dấu đó riêng, không chỉ lưu BigDecimal.

### Exponent ảnh hưởng scale

```java
BigDecimal a = new BigDecimal("1.23E+4");
BigDecimal b = new BigDecimal("12300");

System.out.println(a.compareTo(b)); // 0
System.out.println(a.equals(b));    // false
System.out.println(a.scale());      // -2
System.out.println(b.scale());      // 0
```

Dù `a.toPlainString()` và `b.toPlainString()` đều có thể in `"12300"`, biến `a` lưu scale âm. Muốn round-trip cả **giá trị và scale**, `toString()` có quy tắc biểu diễn chuẩn phù hợp hơn `toPlainString()` trong các trường hợp scale âm. Hoặc truyền **unscaledValue và scale thành hai trường dữ liệu riêng**.

## 11. Chương trình Java 17+ chạy độc lập

Lưu thành tệp **`BigDecimalConstructionDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalConstructionDemo {
    public static void main(String[] args) {
        BigDecimal fromString = new BigDecimal("0012.3400");
        System.out.println("fromString=" + fromString.toPlainString());
        System.out.println("stringUnscaled=" + fromString.unscaledValue());
        System.out.println("stringScale=" + fromString.scale());
        System.out.println("stringPrecision=" + fromString.precision());

        BigInteger integer = new BigInteger("123400");
        BigDecimal fromBigInteger = new BigDecimal(integer);
        BigDecimal fromLong = new BigDecimal(123400L);
        BigDecimal fromLongFactory = BigDecimal.valueOf(123400L);
        System.out.println("fromBigInteger=" +
                fromBigInteger.toPlainString());
        System.out.println("fromLong=" + fromLong.toPlainString());
        System.out.println("fromLongFactory=" +
                fromLongFactory.toPlainString());
        System.out.println("integerScale=" + fromBigInteger.scale());

        BigDecimal fromUnscaledBig = new BigDecimal(integer, 4);
        BigDecimal fromUnscaledLong = BigDecimal.valueOf(123400L, 4);
        System.out.println("unscaledBig=" +
                fromUnscaledBig.toPlainString());
        System.out.println("unscaledLong=" +
                fromUnscaledLong.toPlainString());
        System.out.println("sameRepresentation=" +
                fromString.equals(fromUnscaledBig));

        BigDecimal negativeScale = BigDecimal.valueOf(123L, -2);
        System.out.println("negativeScaleText=" +
                negativeScale.toString());
        System.out.println("negativeScalePlain=" +
                negativeScale.toPlainString());
        System.out.println("negativeScaleValue=" +
                negativeScale.unscaledValue());
        System.out.println("negativeScaleScale=" +
                negativeScale.scale());

        BigDecimal fromScientific = new BigDecimal("1.23E+4");
        System.out.println("scientificUnscaled=" +
                fromScientific.unscaledValue());
        System.out.println("scientificScale=" +
                fromScientific.scale());
        System.out.println("scientificEqualsPlain=" +
                fromScientific.equals(new BigDecimal("12300")));
        System.out.println("scientificComparePlain=" +
                fromScientific.compareTo(new BigDecimal("12300")));

        BigDecimal differentScaleA = new BigDecimal("1.0");
        BigDecimal differentScaleB = new BigDecimal("1.00");
        System.out.println("equalsDifferentScale=" +
                differentScaleA.equals(differentScaleB));
        System.out.println("compareDifferentScale=" +
                differentScaleA.compareTo(differentScaleB));

        Set<BigDecimal> hashValues = new HashSet<>();
        hashValues.add(differentScaleA);
        hashValues.add(differentScaleB);
        Set<BigDecimal> sortedValues = new TreeSet<>();
        sortedValues.add(differentScaleA);
        sortedValues.add(differentScaleB);
        System.out.println("hashSetSize=" + hashValues.size());
        System.out.println("treeSetSize=" + sortedValues.size());

        BigDecimal zeroText = new BigDecimal("-0.00");
        System.out.println("zeroText=" + zeroText.toPlainString());
        System.out.println("zeroScale=" + zeroText.scale());
        System.out.println("zeroSignum=" + zeroText.signum());

        MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
        BigDecimal exact = new BigDecimal("12345");
        BigDecimal rounded = new BigDecimal("12345", mc);
        System.out.println("contextExact=" + exact.toString());
        System.out.println("contextRounded=" + rounded.toString());
        System.out.println("contextScale=" + rounded.scale());

        System.out.println("scaledCents=" +
                BigDecimal.valueOf(12345L, 2).toPlainString());
        System.out.println("setScaleNotShift=" +
                BigDecimal.valueOf(123400L).setScale(4).toPlainString());
        System.out.println("movePoint=" +
                BigDecimal.valueOf(123400L).movePointLeft(4).toPlainString());

        BigDecimal normalized = new BigDecimal("1000.00")
                .stripTrailingZeros();
        System.out.println("stripTrailingZeros=" +
                normalized.toString());
        System.out.println("stripScale=" + normalized.scale());

        System.out.println("decimalFromString=" +
                new BigDecimal("0.1").toPlainString());
        System.out.println("decimalFromValueOfDouble=" +
                BigDecimal.valueOf(0.1).toPlainString());
        System.out.println("decimalFromDoubleConstructor=" +
                new BigDecimal(0.1).toPlainString());

        try {
            new BigDecimal("1,5");
            System.out.println("invalidComma=missed");
        } catch (NumberFormatException ex) {
            System.out.println("invalidComma=detected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalConstructionDemo.java
java BigDecimalConstructionDemo
```

Kết quả:

```text
fromString=12.3400
stringUnscaled=123400
stringScale=4
stringPrecision=6
fromBigInteger=123400
fromLong=123400
fromLongFactory=123400
integerScale=0
unscaledBig=12.3400
unscaledLong=12.3400
sameRepresentation=true
negativeScaleText=1.23E+4
negativeScalePlain=12300
negativeScaleValue=123
negativeScaleScale=-2
scientificUnscaled=123
scientificScale=-2
scientificEqualsPlain=false
scientificComparePlain=0
equalsDifferentScale=false
compareDifferentScale=0
hashSetSize=2
treeSetSize=1
zeroText=0.00
zeroScale=2
zeroSignum=0
contextExact=12345
contextRounded=1.23E+4
contextScale=-2
scaledCents=123.45
setScaleNotShift=123400.0000
movePoint=12.3400
stripTrailingZeros=1E+3
stripScale=-3
decimalFromString=0.1
decimalFromValueOfDouble=0.1
decimalFromDoubleConstructor=0.1000000000000000055511151231257827021181583404541015625
invalidComma=detected
```

Chương trình cho thấy **giá trị toán học**, **unscaled value**, **scale** và **representation equality** là những khái niệm khác nhau. Chú ý cặp `fromBigInteger=123400` và `unscaledBig=12.3400`: cùng `BigInteger("123400")` nhưng **scale được chọn khác**, nên khác giá trị theo hệ số \(10^4\).

## 12. Các lỗi phổ biến và checklist chọn constructor

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`BigDecimal("12.3400")` tự bỏ trailing zero khi tạo.” | Constructor từ String giữ scale 4 theo cú pháp gốc. |
| “`new BigDecimal(BigInteger("123400"))` tạo 12.3400.” | Không: không truyền scale thì giá trị là 123400, scale 0. |
| “`BigDecimal.valueOf(12345L)` hiểu đó là cent và trả 123.45.” | Không: chỉ `valueOf(12345L,2)` mới mang scale 2. |
| “Scale luôn không âm.” | Sai: `BigDecimal.valueOf(123L,-2)` có giá trị 12300 và scale -2. |
| “`setScale(4)` giống `movePointLeft(4)`.” | Sai: `setScale` giữ giá trị số (hoặc làm tròn nếu cần); movePointLeft thay đổi giá trị. |
| “`equals` và `compareTo` đều chỉ so sánh giá trị số.” | Sai: `equals` xét cả scale, `compareTo` xét giá trị. |
| “`precision=scale`.” | Không: precision là chữ số có nghĩa, scale là độ lớn lũy thừa 10. |
| “BigDecimal lưu âm zero như `double`.” | Không: `new BigDecimal("-0.00")` có `signum()==0` và in `0.00`. |
| “Constructor với `MathContext` không bao giờ làm tròn.” | Có thể giới hạn precision và làm tròn ngay lúc tạo. |
| “`new BigDecimal(0.1)` tái tạo số thập phân 0.1 chính xác.” | Nó tạo giá trị đúng của xấp xỉ nhị phân `double`, không phải decimal 0.1. |
| “`BigDecimal.valueOf(0.1)` khôi phục mọi decimal đã mất trước đó.” | Nó đi qua `Double.toString`; không thể lấy lại precision nguồn đã mất. |
| “BigDecimal(String) đọc theo Locale tiếng Việt mặc định.” | Không: dùng cú pháp decimal, không tự nhận dấu phẩy thập phân. |
| “`toPlainString()` luôn round-trip cả scale.” | Với scale âm, chuỗi plain có thể mất thông tin scale khi parse lại. |
| “`==` thích hợp so sánh hai BigDecimal.” | `==` so sánh reference identity, không phải cùng giá trị số. |

Để chọn cách tạo cho đúng:

1. **Nguồn là String decimal chính xác?** Dùng `new BigDecimal(text)`; xác thực cú pháp và giữ precision đầu vào.
2. **Nguồn là BigInteger với giá trị nguyên thực sự?** Dùng `new BigDecimal(integer)` để có scale 0.
3. **Nguồn là long nguyên thực sự?** Dùng `BigDecimal.valueOf(value)` hoặc `new BigDecimal(value)`, scale 0.
4. **Nguồn là unscaled value có đơn vị ngầm?** Dùng `new BigDecimal(bigInteger, scale)` hoặc `BigDecimal.valueOf(longUnscaled, scale)`; **đừng quên scale**.
5. **Có cần giữ trailing zeros hoặc độ phân giải phép đo không?** Xem `scale()` và chọn canonicalization có chủ đích.
6. **Có cần rounding khi tạo không?** Chỉ truyền `MathContext` nếu đó là yêu cầu; đừng mặc định giới hạn precision ở bước parse.
7. **Dữ liệu đã đi qua double?** Xác định xem có chấp nhận mất precision hay phải quay về decimal string gốc.
8. **Giá trị được lưu/truyền ra hệ ngoài thế nào?** Cân nhắc cặp `unscaledValue + scale` để giữ đầy đủ ngữ nghĩa, và phân biệt `toString` với `toPlainString`.
9. **So sánh hoặc làm khóa collection?** Hiểu khác nhau giữa `equals` và `compareTo`; nếu scale là phần của identity dữ liệu, không tự strip.
10. **Dữ liệu có thể rất lớn hoặc xấu?** Đặt giới hạn độ dài chuỗi và scale hợp lệ theo yêu cầu ứng dụng để tránh xử lý tốn tài nguyên.

**Ghi nhớ:** `BigDecimal` không chỉ là “một số có nhiều chữ số” mà là **unscaled integer cùng scale**. `String` giữ ý nghĩa thập phân của văn bản, `BigInteger` và `long` tạo số nguyên scale 0 khi không chỉ định khác, còn các overload nhận **unscaled value + scale** diễn giải đúng dữ liệu theo đơn vị đã quy định. Chọn constructor đúng giúp tránh lỗi nhân/chia lệch hệ số 10, mất scale và nhầm lẫn trong so sánh.

Tài liệu tham khảo: [Oracle Java 17 — BigDecimal Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-006">Constructor nhận double và nhiễu biểu diễn nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal: Constructor nhận `double` và nhiễu biểu diễn nhị phân

Trong Java, `java.math.BigDecimal` cho phép tính toán số **thập phân** với unscaled value và scale, không phải chịu sai số làm tròn nhị phân ở mỗi phép cộng, trừ, nhân như `double`. Tuy nhiên, **cách tạo `BigDecimal` quan trọng không kém cách tính**. Nếu đầu vào là một `double` vốn đã xấp xỉ số thập phân dự định, constructor `new BigDecimal(double)` sẽ **bảo toàn chính xác giá trị nhị phân đã nhận**, kể cả độ lệch nhỏ của nó, thay vì tự đoán số thập phân mà người viết mã muốn.

```java
import java.math.BigDecimal;

BigDecimal fromDouble = new BigDecimal(0.1);
BigDecimal fromText = new BigDecimal("0.1");
BigDecimal fromFactory = BigDecimal.valueOf(0.1);

System.out.println(fromDouble);
// 0.1000000000000000055511151231257827021181583404541015625

System.out.println(fromText);    // 0.1
System.out.println(fromFactory); // 0.1
```

Dòng dài không phải “`BigDecimal` tạo ra sai số mới”. Nó là **cách viết thập phân chính xác của giá trị `double` đã bị lượng tử hóa theo nhị phân trước đó**. Hiểu điểm này giúp tránh lỗi khi tính tiền, đọc dữ liệu decimal, quy đổi đơn vị và đưa kết quả số vào báo cáo.

Bài học này tập trung vào: hai lớp số, nguồn gốc nhiễu nhị phân, hợp đồng của `BigDecimal(double)`, `BigDecimal.valueOf(double)`, `BigDecimal(String)`, scale/precision, trường hợp có và không có nhiễu, ảnh hưởng lên phép tính và so sánh, rounding, giới hạn khi dữ liệu đã đi qua `double`, và những cách chọn constructor an toàn.

## 1. Vì sao `0.1` không phải một số `double` thập phân chính xác?

`double` dùng **IEEE 754 binary64**, gồm thông tin dấu, số mũ và phần trị (*significand*) được biểu diễn theo cơ số **2**. Trong khi đó, chữ số `0.1` theo toán học là **một phần mười**, tức `1/10`.

Một phân số thập phân có thể biểu diễn hữu hạn theo nhị phân chỉ khi, sau khi rút gọn, mẫu số là lũy thừa của 2. `1/10` có mẫu số gồm cả thừa số 5, nên **không có khai triển nhị phân hữu hạn**.

Trình biên dịch Java phải chọn một giá trị binary64 gần với `0.1`. Giá trị thực được lưu là:

```text
0.1000000000000000055511151231257827021181583404541015625
```

Đây chính là giá trị thập phân chính xác của bit pattern binary64 được chọn cho literal `0.1`, **không** phải nội dung gốc “một phần mười” ở độ chính xác vô hạn.

### Sơ đồ đường đi dữ liệu

```text
Mong muốn toán học:  0.1  (1/10)
          |
          | Gán cho literal / biến double
          v
binary64 gần nhất:   0.1000000000000000055511...
          |
          | new BigDecimal(double) bảo toàn chính xác giá trị binary64
          v
BigDecimal:          0.1000000000000000055511...
```

**Chốt:** `BigDecimal(double)` không “cố tình thêm nhiễu”; nó nhận đầu vào đã xấp xỉ và chuyển **chính xác** giá trị đó sang một số thập phân. Độ dài chuỗi là dấu hiệu của việc nguồn dữ liệu vốn là binary64.

### Thử các phân số khác

| Giá trị toán học | Có biểu diễn nhị phân hữu hạn? | `new BigDecimal(double)` khi nhận literal |
| --- | --- | --- |
| `0.5 = 1/2` | Có | `0.5` |
| `0.25 = 1/4` | Có | `0.25` |
| `0.125 = 1/8` | Có | `0.125` |
| `0.1 = 1/10` | Không | Một chuỗi thập phân dài, lớn hơn 0.1 một chút |
| `0.2 = 1/5` | Không | Một chuỗi thập phân dài |
| `0.3 = 3/10` | Không | Một chuỗi thập phân dài, tùy giá trị binary64 được chọn |

**Không phải mọi giá trị `double` đều tạo ra chuỗi dài.** Nhiều số nguyên nhỏ, phân số có mẫu số là lũy thừa của 2 và một số giá trị khác có biểu diễn thập phân gọn. Quyết định sai nhất là giả định `new BigDecimal(double)` luôn tương đương `new BigDecimal("giá-trị-muốn-nhập")`.

## 2. `BigDecimal` lưu giá trị như thế nào?

Về mặt khái niệm, giá trị số của một `BigDecimal` là:

\[
\mathrm{value} = \mathrm{unscaledValue} \times 10^{-\mathrm{scale}}
\]

`unscaledValue()` trả về một `BigInteger`; `scale()` trả số nguyên chỉ vị trí dấu thập phân. Có hai phần này, `BigDecimal` mô tả được số thập phân chính xác mà không cần giữ một giá trị `double` bên trong.

Ví dụ:

```java
BigDecimal value = new BigDecimal("12.34");

System.out.println(value.unscaledValue()); // 1234
System.out.println(value.scale());         // 2
```

`1234 × 10^-2 = 12.34`.

Với dữ liệu từ `new BigDecimal(0.1)`, phần không tỷ lệ là một số nguyên có rất nhiều chữ số, còn scale lớn hơn vì phải viết chính xác giá trị binary64 ở cơ số 10:

```java
BigDecimal fromDouble = new BigDecimal(0.1);

System.out.println(fromDouble.scale()); // 55
System.out.println(fromDouble.unscaledValue());
// 1000000000000000055511151231257827021181583404541015625
```

Điều này giải thích tại sao toán học trên các `BigDecimal` tạo từ `double` có thể kéo theo nhiều chữ số thập phân: chúng thật sự chứa **các giá trị decimal với precision/scale lớn**, và phép toán BigDecimal mặc định trên nhiều thao tác cố gắng giữ kết quả chính xác theo các toán hạng đó.

### `precision` không phải `scale`

- **Scale**: số mũ thập phân bị trừ đi (đối với scale dương, thường là số chữ số sau dấu phẩy).
- **Precision**: số chữ số trong `unscaledValue` (bỏ qua dấu), theo hợp đồng API.

```java
BigDecimal a = new BigDecimal("1.2300");
System.out.println(a.scale());     // 4
System.out.println(a.precision()); // 5

BigDecimal b = new BigDecimal("1.23");
System.out.println(b.scale());     // 2
System.out.println(b.precision()); // 3
```

Hai giá trị có thể bằng nhau về mặt số học mà khác scale. Vì vậy, khi quan sát chuỗi dài do `new BigDecimal(double)`, cần xét cả **giá trị số** lẫn **biểu diễn**; chỉ nhìn số lượng chữ số hiển thị không giải quyết được nguyên nhân.

## 3. Hợp đồng của `new BigDecimal(double)`

Oracle Java SE 17 API mô tả constructor:

```java
public BigDecimal(double val)
```

Nó tạo một `BigDecimal` có giá trị đúng bằng **giá trị floating-point nhị phân của đối số `double`**. Scale được chọn nhỏ nhất để giá trị đó có thể viết như một số nguyên nhân `10^-scale`.

```java
BigDecimal x = new BigDecimal(0.1);
System.out.println(x.toPlainString());
// 0.1000000000000000055511151231257827021181583404541015625
```

Hệ quả:
1. Constructor **không** đọc lại token `0.1` trong source để đoán ý định của người viết.
2. Constructor **không** dùng chuỗi rút gọn từ `Double.toString`.
3. Nó **không tự làm tròn** thành một chữ số thập phân vì “có vẻ giống 0.1”.
4. Giá trị của `BigDecimal` được tạo ra **chính xác đối với đầu vào `double`**, dù khác giá trị decimal mà ta mong muốn.
5. Nếu đối số là `NaN` hoặc `Infinity`, constructor ném `NumberFormatException`: BigDecimal không biểu diễn số không hữu hạn.
6. Với `-0.0`, dấu zero âm của binary64 không được giữ dưới dạng một trạng thái signed-zero BigDecimal riêng.

Tính “exact” ở đây có nghĩa **chuyển đổi đầu vào binary64 sang decimal không mất thêm thông tin số học của giá trị nguồn**, không có nghĩa **khôi phục một phân số thập phân trước khi nó được chuyển thành binary64**.

### Cùng một literal trông đơn giản nhưng giá trị khác

```java
BigDecimal exactBinary = new BigDecimal(0.1);
BigDecimal exactDecimal = new BigDecimal("0.1");

System.out.println(exactBinary.equals(exactDecimal));    // false
System.out.println(exactBinary.compareTo(exactDecimal)); // 1
```

`compareTo` trả dương bởi giá trị exact binary64 của `0.1` hơi **lớn hơn** đúng 0.1. Trường hợp này không phải chỉ khác `scale`: **hai giá trị toán học thực sự khác nhau**.

## 4. `new BigDecimal("0.1")`: tạo từ chữ số decimal gốc

Constructor:

```java
BigDecimal correctDecimal = new BigDecimal("0.1");
```

đọc **chuỗi ký tự thập phân**. Nó không ép chuỗi `"0.1"` qua `double` trước. Vì vậy, nó biểu diễn **chính xác phân số `1/10`**, với unscaled value 1 và scale 1.

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");

System.out.println(a.add(b)); // 0.3
```

Đây là cách phù hợp nếu dữ liệu vốn đã là **text decimal có ý nghĩa chính xác**: tiền tệ, tỷ lệ thập phân, dữ liệu đo được cung cấp với số chữ số cố định, giá trị đọc từ database DECIMAL/NUMERIC hoặc một cột CSV có schema decimal.

### Đừng biến chuỗi thành `double` ở giữa

```java
String input = "0.1";

// Tránh nếu yêu cầu giữ đúng decimal nhập vào:
BigDecimal lossyPath =
        new BigDecimal(Double.parseDouble(input));

// Giữ được decimal đúng như ký tự:
BigDecimal exactPath = new BigDecimal(input);
```

Dòng thứ nhất đi qua `double` và tạo giá trị binary64 xấp xỉ; dòng thứ hai đọc trực tiếp văn bản decimal. Sau khi dữ liệu đã qua `double`, không có API nào tự biết chắc nguồn gốc **ban đầu** có bao nhiêu chữ số, scale hay đơn vị.

### Hợp đồng nguồn quan trọng hơn tên constructor

`new BigDecimal("0.1")` đúng khi `"0.1"` là một chuỗi số thập phân hợp lệ theo schema. Không nên lấy chuỗi đã định dạng cho người đọc như `"1,234.50"` hoặc `"1.234,50"` rồi đưa thẳng cho `BigDecimal(String)`: constructor dùng cú pháp decimal riêng, không tự hiểu dấu phân nhóm locale.

Nếu đầu vào do người dùng nhập, cần parse theo locale/hợp đồng rõ ràng **mà không làm mất precision trước khi tới `BigDecimal`**. Với các thư viện parse số địa phương, hãy đảm bảo chúng có chế độ trả `BigDecimal` trực tiếp khi yêu cầu decimal chính xác, hoặc dùng một parser decimal riêng phù hợp schema.

## 5. `BigDecimal.valueOf(double)`: đường đi qua `Double.toString`

Khi dữ liệu **đã tồn tại dưới dạng `double`**, factory method sau thường được ưu tiên hơn constructor nhận `double`:

```java
BigDecimal friendly = BigDecimal.valueOf(0.1);
System.out.println(friendly); // 0.1
```

Oracle mô tả rằng `BigDecimal.valueOf(double)` dùng **biểu diễn chuỗi chuẩn từ `Double.toString(double)`** rồi tạo `BigDecimal` từ biểu diễn đó.

Về mặt kết quả có thể nghĩ:

```java
double source = 0.1;

BigDecimal a = BigDecimal.valueOf(source);
BigDecimal b = new BigDecimal(Double.toString(source));

System.out.println(a.equals(b)); // true
```

`Double.toString` cung cấp biểu diễn thập phân đủ để parse quay lại giá trị binary64 gốc theo quy tắc chuyển đổi `double`. Với `0.1`, biểu diễn ấy là chuỗi ngắn `"0.1"`. `BigDecimal.valueOf` từ đó tạo ra **số decimal 0.1**.

### Ba cách tạo, ba ý nghĩa

| Cách tạo | Nguồn được diễn giải | Kết quả `0.1` | Khi nào chọn |
| --- | --- | --- | --- |
| `new BigDecimal("0.1")` | Chuỗi decimal gốc | `0.1` chính xác | Khi **có decimal text đáng tin cậy** |
| `BigDecimal.valueOf(0.1)` | `Double.toString` của binary64 đầu vào | `0.1` | Khi **chỉ còn `double`** và muốn cách chuyển decimal theo chuỗi chuẩn |
| `new BigDecimal(0.1)` | Giá trị binary64 chính xác | `0.10000000000000000555...` | Khi **chủ đích cần chính xác giá trị binary64** |

**Không gọi `valueOf(double)` là máy “phục hồi giá trị gốc”**: nó chỉ chọn một cách biểu diễn ngắn và xác định của giá trị `double` đã có. Nếu sai số đã tích lũy trong tính toán `double`, kết quả ấy vẫn xuất hiện.

### Không phục hồi được sai số từ phép toán trung gian

```java
double binarySum = 0.1 + 0.2;

System.out.println(binarySum); // 0.30000000000000004

BigDecimal converted = BigDecimal.valueOf(binarySum);
System.out.println(converted); // 0.30000000000000004
```

Ở đây `Double.toString(binarySum)` là `"0.30000000000000004"`, nên factory giữ kết quả đó dưới dạng decimal. Nó **không sửa** phép cộng double thành decimal 0.3.

Muốn kết quả decimal chính xác:

```java
BigDecimal decimalSum =
        new BigDecimal("0.1").add(new BigDecimal("0.2"));

System.out.println(decimalSum); // 0.3
```

**Quy tắc vận hành:** nếu cần decimal exact, hãy tạo `BigDecimal` **ngay ở ranh giới đầu vào**, không đợi một chuỗi phép toán `double` kết thúc mới chuyển đổi.

## 6. Một thí nghiệm với phép cộng: nhiễu được giữ lại

Cùng hai literal `0.1` và `0.2`:

```java
BigDecimal sumExactOfBinary =
        new BigDecimal(0.1).add(new BigDecimal(0.2));

BigDecimal sumOfDecimalText =
        new BigDecimal("0.1").add(new BigDecimal("0.2"));

BigDecimal afterBinaryAddition =
        BigDecimal.valueOf(0.1 + 0.2);
```

Ba con đường khác nhau:

| Con đường | Giá trị kết quả |
| --- | --- |
| `new BigDecimal(0.1).add(new BigDecimal(0.2))` | `0.3000000000000000166533453693773481063544750213623046875` |
| `new BigDecimal("0.1").add(new BigDecimal("0.2"))` | `0.3` |
| `BigDecimal.valueOf(0.1 + 0.2)` | `0.30000000000000004` |

Các con số khác nhau không mâu thuẫn:
- Con đường 1 chuyển từng binary64 sang BigDecimal **chính xác**, rồi cộng hai giá trị chính xác ấy bằng decimal.
- Con đường 2 bắt đầu từ **decimal exact** và cộng exact.
- Con đường 3 **cộng bằng double trước**, nhận kết quả đã làm tròn nhị phân, rồi chuyển kết quả qua `Double.toString`.

`BigDecimal` không sửa được một nguồn đã bị tính sai theo yêu cầu. Cần thiết kế **toàn bộ tuyến dữ liệu số** phù hợp ngay từ đầu.

## 7. Khi nào constructor `double` là đúng?

Không nên hiểu `new BigDecimal(double)` luôn “sai”. Nó hoàn toàn có ý nghĩa khi bài toán thật sự cần **chuyển một số nhị phân floating-point cụ thể sang biểu diễn decimal exact của chính nó**.

Ví dụ:
- Phân tích sai số của thuật toán `double`.
- Kiểm tra ranh giới làm tròn từ binary64 sang decimal.
- Nghiên cứu serialization/quantization và sự khác nhau giữa chuỗi ngắn nhất với giá trị binary64 chính xác.
- Tạo tham chiếu số học không làm tròn thêm tại thời điểm **chuyển đổi**.

```java
double binaryValue = 0.1;
BigDecimal exactRepresentation = new BigDecimal(binaryValue);
```

Đây là một yêu cầu kỹ thuật hợp lệ. Nhưng nó **không** phù hợp khi yêu cầu là “giá sản phẩm người dùng nhập 0.10” hoặc “thuế suất decimal đúng 0.1” mà dữ liệu có sẵn ở dạng văn bản.

### Phân số nhị phân có biểu diễn hữu hạn

`0.5` bằng `1/2` nên được lưu đúng trong binary64:

```java
System.out.println(new BigDecimal(0.5)); // 0.5
```

Tương tự, `0.25`, `0.125` và nhiều số nguyên nhỏ cũng có thể chuyển đẹp. Việc một vài test đơn giản “cho chuỗi đẹp” không chứng minh constructor double an toàn cho mọi số decimal.

## 8. Không phải mọi số lớn đều giữ nguyên khi đi qua `double`

Lỗi dữ liệu không chỉ xảy ra với phần lẻ. `double` có 53 bit significand, vì vậy nó không biểu diễn chính xác **mọi** số nguyên lớn.

```java
long id = 9_007_199_254_740_993L; // 2^53 + 1

BigDecimal directly = BigDecimal.valueOf(id);
BigDecimal viaDouble = BigDecimal.valueOf((double) id);

System.out.println(directly.toPlainString());
// 9007199254740993

System.out.println(viaDouble.toPlainString());
// 9007199254740992
```

Lời gọi `BigDecimal.valueOf((double) id)` không thể lấy lại bit đã mất khi chuyển `long -> double`. `new BigDecimal((double) id)` cũng không thể phục hồi nó: constructor có thể biểu diễn chính xác binary64, nhưng binary64 **đã mang giá trị khác**.

Nếu nguồn là số nguyên:
- `BigDecimal.valueOf(long)` tạo decimal integer chính xác, scale 0.
- `new BigDecimal(long)` cũng tạo giá trị số nguyên chính xác.
- `new BigDecimal(BigInteger)` phù hợp với số nguyên vượt `long`.
- **Tránh** chuyển qua `double` chỉ để gọi một constructor nhận double.

Lưu ý sự khác biệt giữa hai overload **`BigDecimal.valueOf(long)`** và **`BigDecimal.valueOf(double)`**. Với `long`, factory không cần thuật toán biểu diễn floating-point.

## 9. `float` chuyển sang `double` không khôi phục chính xác decimal

`float` dùng binary32, có khoảng 24 bit significand. Khi truyền một `float` vào API nhận `double`, Java **mở rộng** giá trị float sang double, nhưng giá trị đã lượng tử hóa ở binary32 không quay trở thành literal decimal ban đầu.

```java
float sample = 0.1f;
double widened = sample;

System.out.println(widened); // 0.10000000149011612
System.out.println(BigDecimal.valueOf(widened));
// 0.10000000149011612
```

Đây là ví dụ hữu ích khi số đo đi qua `float`, sau đó được giao cho một module làm việc với `BigDecimal`. Chỉ vì module đích dùng BigDecimal, dữ liệu **không** tự có độ chính xác decimal của nguồn.

**Chiến lược theo yêu cầu:**
- Nếu muốn lưu chính xác **giá trị float**, constructor `new BigDecimal((double) sample)` có thể dùng để biểu diễn exact giá trị đã có.
- Nếu muốn decimal text ngắn theo `Float.toString(sample)`, có thể tạo `new BigDecimal(Float.toString(sample))` với hợp đồng rõ; điều này **khác** đường `BigDecimal.valueOf((double) sample)`.
- Nếu cần decimal gốc của dữ liệu từ trước khi bị ép thành float, phải đọc hoặc giữ **dữ liệu nguồn ban đầu**.

Không dùng `Float.toString` như cách sửa mọi sai số: nó là một chính sách biểu diễn giá trị float đã có, không phải bằng chứng về dữ liệu gốc.

## 10. Dùng `MathContext` có giúp tránh nhiễu không?

`BigDecimal` có constructor:

```java
new BigDecimal(double value, MathContext mc)
```

Nó chuyển từ `double` và làm tròn theo precision/rounding được chỉ định. Ví dụ:

```java
import java.math.MathContext;
import java.math.RoundingMode;

BigDecimal rounded =
        new BigDecimal(0.1, new MathContext(4, RoundingMode.HALF_UP));
```

Kết quả có thể trông “gọn” vì được làm tròn ở 4 chữ số có nghĩa. Nhưng đó là **một phép làm tròn có chủ đích** từ giá trị binary64, **không phải phục hồi decimal gốc**.

`MathContext` quy định **precision** (số chữ số có nghĩa) và rounding mode. Nó không đồng nghĩa với “luôn giữ 2 chữ số sau dấu thập phân”. Để quy định số chữ số lẻ cố định, `setScale(scale, roundingMode)` thường phù hợp hơn.

### `setScale` giúp chuẩn hóa theo hợp đồng decimal

```java
BigDecimal exactBinary = new BigDecimal(0.1);

BigDecimal twoDecimals =
        exactBinary.setScale(2, RoundingMode.HALF_UP);

System.out.println(twoDecimals); // 0.10
```

Nếu nghiệp vụ là “số tiền được định nghĩa bằng cent và phải làm tròn HALF_UP”, việc setScale ở ranh giới xác định có thể hợp lý. **Nhưng nếu bạn không biết dữ liệu nguồn có đúng là 0.1 hay là 0.10000000000000001**, thao tác rounding không thể xác minh ý định của nguồn.

`setScale(2, RoundingMode.UNNECESSARY)` yêu cầu không mất thông tin theo mức scale đích; với `new BigDecimal(0.1)` thường ném `ArithmeticException` vì có các chữ số dư khác 0. Đây có thể là **một phép phát hiện dữ liệu không thỏa hợp đồng**, chứ không phải lỗi ngẫu nhiên của BigDecimal.

### Không chọn rounding mode tùy tiện

| Chính sách | Ý nghĩa thường dùng | Ghi chú |
| --- | --- | --- |
| `RoundingMode.HALF_UP` | Làm tròn ở phần nửa theo hướng tăng độ lớn | Phổ biến ở một số nghiệp vụ, không mặc định phù hợp mọi chuẩn |
| `RoundingMode.HALF_EVEN` | Rounding ties to even | Hữu ích giảm thiên lệch khi làm tròn nhiều lần |
| `RoundingMode.DOWN` | Cắt về 0 | Không phải floor cho số âm |
| `RoundingMode.FLOOR` | Về âm vô cùng | Khác DOWN nếu giá trị âm |
| `RoundingMode.UNNECESSARY` | Cấm mọi bước làm tròn | Ném exception nếu cần loại chữ số khác 0 |

Hợp đồng tài chính/thuế/đo lường phải chọn chế độ và **thời điểm** làm tròn. Không tự động gọi `setScale(2)` với mode tùy ý chỉ vì giá trị trông như số tiền.

## 11. So sánh: khác giá trị và khác scale là hai chuyện riêng

`BigDecimal.equals` so sánh cả **giá trị** và **scale**, còn `compareTo` so sánh thứ tự/giá trị số học.

```java
BigDecimal a = new BigDecimal("2.00");
BigDecimal b = new BigDecimal("2.0");

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0
```

Đây là trường hợp **giá trị số bằng nhau nhưng scale khác**.

Ngược lại:

```java
BigDecimal noisy = new BigDecimal(0.1);
BigDecimal exact = new BigDecimal("0.1");

System.out.println(noisy.equals(exact));    // false
System.out.println(noisy.compareTo(exact)); // 1
```

Trường hợp này **giá trị khác thật**; không thể sửa bằng `stripTrailingZeros()` bởi các chữ số thập phân dư của `noisy` là có ý nghĩa trong chính giá trị đang lưu, không phải toàn bộ là số 0 cuối.

Đừng dùng một tolerance `double` không rõ đơn vị để che sai số BigDecimal trong các quy trình phải exact. Nếu so sánh kiểu “gần bằng” thật sự là yêu cầu ứng dụng, hãy định nghĩa ngưỡng decimal tường minh, ví dụ:

```java
BigDecimal difference = noisy.subtract(exact).abs();
BigDecimal tolerance = new BigDecimal("0.000000000000001");

boolean close = difference.compareTo(tolerance) <= 0;
```

Đây là **một chính sách chấp nhận chênh lệch**, không phải phép biến `noisy` thành đúng 0.1. Với dữ liệu tiền tệ cần đúng cent, hãy dùng quy tắc scale/rounding trên dữ liệu đầu vào và loại bỏ việc chuyển qua double ngay từ đầu.

## 12. NaN, Infinity, zero âm và các giá trị đặc biệt

`double` có `NaN`, `+Infinity`, `-Infinity` và signed zero; `BigDecimal` không chứa các trạng thái không hữu hạn ấy như `double`.

```java
// Những lời gọi sau ném NumberFormatException:
new BigDecimal(Double.NaN);
BigDecimal.valueOf(Double.POSITIVE_INFINITY);
new BigDecimal(Double.NEGATIVE_INFINITY);
```

Chúng là các lệnh minh họa **cố ý gây exception**, không phải một đoạn mã có thể chạy tuần tự nếu không bắt lỗi.

Trước khi nhận một nguồn `double` có thể đến từ thiết bị hoặc tính toán, xác thực nếu ứng dụng yêu cầu số hữu hạn:

```java
static BigDecimal fromFiniteDouble(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("finite number required");
    }
    return BigDecimal.valueOf(value);
}
```

Phương thức minh họa trên từ chối NaN/Infinity và chọn chính sách chuyển đổi `valueOf(double)`, nhưng **không** hứa phục hồi decimal nguồn.

### Zero âm

`-0.0d` có mẫu bit khác `+0.0d` nhưng so sánh toán học `-0.0d == +0.0d` là true. `BigDecimal` không mô hình hóa signed zero riêng, nên:

```java
System.out.println(new BigDecimal(-0.0).toPlainString()); // 0
```

Nếu dấu zero trong dữ liệu cảm biến/IEEE 754 là một phần ngữ nghĩa cần bảo toàn, **chuyển sang BigDecimal không giữ thông tin đó**. Hãy thiết kế kiểu dữ liệu/schema riêng nếu cần.

## 13. Dữ liệu tài chính: không đưa qua `double` vì tiện

Giả sử nghiệp vụ có hai khoản decimal chính xác 0.10 và 0.20 đơn vị tiền tệ:

```java
BigDecimal first = new BigDecimal("0.10");
BigDecimal second = new BigDecimal("0.20");

BigDecimal total = first.add(second);
System.out.println(total.toPlainString()); // 0.30
```

Giá trị chính xác và scale được giữ theo phép cộng, vì `add` mặc định chọn scale là lớn nhất của hai toán hạng.

Một cách đi đường vòng không nên làm:

```java
double x = 0.10;
double y = 0.20;
BigDecimal convertedTooLate = BigDecimal.valueOf(x + y);
System.out.println(convertedTooLate);
// 0.30000000000000004
```

Nếu code đang nhận số từ file CSV, JSON hoặc JDBC, cần làm rõ **kiểu dữ liệu tại ranh giới**:
- Một cột `DECIMAL`/`NUMERIC` nên được đọc thành `BigDecimal` khi driver/framework hỗ trợ chính xác.
- Một JSON string decimal được schema quy định có thể parse trực tiếp qua `new BigDecimal(text)` sau khi validate.
- Một JSON number có thể được thư viện nhận thành `double`, `BigDecimal` hay kiểu khác; phải cấu hình parser để không mất precision khi cần.
- Một giá trị tiền với scale xác định có thể lưu số nguyên theo đơn vị nhỏ nhất (`long cents`) nếu miền/đơn vị phù hợp, nhưng không cần ép mọi mô hình tiền sang một cách duy nhất.

**Không xử lý dữ liệu định dạng theo locale như `"1.234,50"` bằng `new BigDecimal(String)` trực tiếp**. Phải có parser nhập liệu đúng locale và kiểu kết quả, tránh đi qua `double` nếu muốn exact decimal.

## 14. Một số trường hợp làm tròn và biểu diễn rất dễ nhầm

### 14.1. `new BigDecimal(0.1).toString()` không “phóng đại lỗi”

`toString` hiện ra tất cả chữ số cần thiết để diễn đạt chính xác giá trị BigDecimal. Nó **không tạo thêm số học hoặc tăng sai số**. Cùng một đối tượng ấy, `toPlainString` cũng chỉ thay cách hiển thị exponent/decimal theo hợp đồng, chứ không thay giá trị.

Nếu thấy một chuỗi dài sau khi gọi `toString`, nguyên nhân thường đã xảy ra ở **cách xây BigDecimal từ double**, không phải formatter đang tính sai.

### 14.2. `BigDecimal.valueOf(double)` vẫn mất thông tin về text gốc

`BigDecimal.valueOf(1.0)` có thể có scale khác `new BigDecimal("1.00")`. Factory chỉ biết binary64 **1.0** và chuỗi chuẩn của nó; nó không biết ban đầu người dùng nhập `"1.00"` với **hai chữ số lẻ**.

Nếu số chữ số lẻ/scale mang ý nghĩa chứng từ, hãy giữ decimal text hoặc scale như một phần hợp đồng.

### 14.3. `BigDecimal(double, MathContext)` và `setScale` không giống nhau

Constructor có `MathContext` làm tròn theo **tổng số chữ số có nghĩa**. `setScale(2,...)` làm tròn theo **vị trí hai chữ số sau dấu thập phân** nếu scale đích là 2. Hai cách có thể tạo kết quả khác, đặc biệt với số rất lớn hoặc rất nhỏ.

### 14.4. `BigDecimal` phép chia không tự sinh vô hạn chữ số

`BigDecimal.ONE.divide(new BigDecimal("3"))` ném `ArithmeticException` vì `1/3` không có biểu diễn thập phân hữu hạn, trừ khi cung cấp scale/rounding hoặc `MathContext` thích hợp. Điều này độc lập với vấn đề nhiễu do `double`, nhưng cùng cho thấy **BigDecimal yêu cầu bạn định nghĩa chính sách làm tròn** thay vì âm thầm chọn mặc định.

### 14.5. `BigDecimal.valueOf(double)` không làm mọi phép toán thành số học decimal

Một chương trình như:

```java
double balance = 0.0;
balance += 0.1;
balance += 0.2;

BigDecimal after = BigDecimal.valueOf(balance);
```

vẫn đã thực hiện phép cộng trong binary64. Cần chuyển **từng giá trị decimal nguồn** sang BigDecimal trước rồi mới cộng bằng `BigDecimal.add`. Không thể “bật chế độ decimal” cho một chuỗi phép tính `double` chỉ bằng cách đổi kiểu ở cuối.

## 15. Cách chọn constructor/factory theo nguồn thực tế

| Nguồn | Mục tiêu | Lựa chọn |
| --- | --- | --- |
| Chuỗi decimal hợp lệ `"0.10"` | Giữ đúng giá trị và scale nguồn | `new BigDecimal("0.10")` |
| Primitive `int`/`long` | Giữ nguyên số nguyên, scale 0 | `BigDecimal.valueOf(long)` hoặc constructor integer |
| `BigInteger` | Chuyển số nguyên rất lớn | `new BigDecimal(bigInteger)` |
| Đầu vào `double` đã có | Lấy decimal dựa trên canonical string của double | `BigDecimal.valueOf(d)` |
| Đầu vào `double` đã có | Chính xác giá trị binary64 tới chữ số cuối | `new BigDecimal(d)` |
| Đầu vào `float` đã có | Muốn decimal theo `Float.toString` | `new BigDecimal(Float.toString(f))` khi đó là policy |
| Chuỗi decimal có nhiều chữ số nhưng đang định parse qua `double` | Không được mất precision | Parse trực tiếp sang BigDecimal, bỏ bước double |
| JSON number trong API tài chính | Cần decimal exact khi parse | Cấu hình JSON deserializer nhận decimal token thành BigDecimal |
| Phép toán `double` đã xảy ra | Không thể tự phục hồi decimal gốc | Xem lại upstream hoặc xác lập rounding policy |
| Input có locale/grouping | Cần decimal exact | Parse theo locale/schema thành BigDecimal, không parse qua double |

### Hướng dẫn ra quyết định ngắn

1. **Có decimal text gốc?** Giữ và parse trực tiếp qua BigDecimal.
2. **Có integer primitive hoặc BigInteger?** Chuyển trực tiếp, không thông qua double.
3. **Chỉ còn `double`?** Nếu cần giá trị decimal theo chuỗi chuẩn, dùng `BigDecimal.valueOf(d)`; nếu cần giá trị binary64 **exact**, dùng `new BigDecimal(d)`.
4. **Dữ liệu tính tiền/decimal?** Thực hiện phép tính bằng BigDecimal từ đầu, có scale và rounding mode phù hợp.
5. **Cần giữ cả dấu zero, NaN, Infinity hoặc bit pattern?** BigDecimal không thay thế được mô hình floating-point đặc biệt ấy.

## 16. Chương trình Java 17+ chạy độc lập

Tạo tệp `DecimalDoubleDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalDoubleDemo {
    public static void main(String[] args) {
        double d = 0.1;
        BigDecimal exact = new BigDecimal(d);
        BigDecimal text = new BigDecimal("0.1");
        BigDecimal factory = BigDecimal.valueOf(d);

        System.out.println("double text: " + Double.toString(d));
        System.out.println("new BigDecimal(0.1): " + exact.toPlainString());
        System.out.println("new BigDecimal(\"0.1\"): "
                + text.toPlainString());
        System.out.println("BigDecimal.valueOf(0.1): "
                + factory.toPlainString());
        System.out.println("exact equals text: " + exact.equals(text));
        System.out.println("factory equals text: " + factory.equals(text));
        System.out.println("exact compareTo text: "
                + exact.compareTo(text));
        System.out.println("exact scale: " + exact.scale());
        System.out.println("text scale: " + text.scale());
        System.out.println("exact unscaled: " + exact.unscaledValue());

        System.out.println("decimal sum: "
                + text.add(new BigDecimal("0.2")).toPlainString());
        System.out.println("double sum: " + Double.toString(0.1 + 0.2));
        System.out.println("factory after double sum: "
                + BigDecimal.valueOf(0.1 + 0.2).toPlainString());
        System.out.println("exact binary sum: "
                + new BigDecimal(0.1).add(new BigDecimal(0.2))
                    .toPlainString());

        System.out.println("half exactly representable: "
                + new BigDecimal(0.5).toPlainString());
        System.out.println("exact rounded to 2 decimals: "
                + exact.setScale(2, RoundingMode.HALF_UP).toPlainString());
        System.out.println("factory rounded to 2 decimals: "
                + factory.setScale(2, RoundingMode.HALF_UP).toPlainString());

        System.out.println("float promoted: "
                + BigDecimal.valueOf((double) 0.1f).toPlainString());

        long big = 9_007_199_254_740_993L;
        System.out.println("long exact: "
                + BigDecimal.valueOf(big).toPlainString());
        System.out.println("long through double: "
                + BigDecimal.valueOf((double) big).toPlainString());

        try {
            new BigDecimal(Double.NaN);
        } catch (NumberFormatException ex) {
            System.out.println("NaN rejected: true");
        }
        try {
            BigDecimal.valueOf(Double.POSITIVE_INFINITY);
        } catch (NumberFormatException ex) {
            System.out.println("Infinity rejected: true");
        }

        System.out.println("negative zero from double: "
                + new BigDecimal(-0.0).toPlainString());
        System.out.println("2.00 equals 2.0: "
                + new BigDecimal("2.00")
                    .equals(new BigDecimal("2.0")));
        System.out.println("2.00 compareTo 2.0: "
                + new BigDecimal("2.00")
                    .compareTo(new BigDecimal("2.0")));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 DecimalDoubleDemo.java
java DecimalDoubleDemo
```

Stdout đã kiểm chứng:

```text
double text: 0.1
new BigDecimal(0.1): 0.1000000000000000055511151231257827021181583404541015625
new BigDecimal("0.1"): 0.1
BigDecimal.valueOf(0.1): 0.1
exact equals text: false
factory equals text: true
exact compareTo text: 1
exact scale: 55
text scale: 1
exact unscaled: 1000000000000000055511151231257827021181583404541015625
decimal sum: 0.3
double sum: 0.30000000000000004
factory after double sum: 0.30000000000000004
exact binary sum: 0.3000000000000000166533453693773481063544750213623046875
half exactly representable: 0.5
exact rounded to 2 decimals: 0.10
factory rounded to 2 decimals: 0.10
float promoted: 0.10000000149011612
long exact: 9007199254740993
long through double: 9007199254740992
NaN rejected: true
Infinity rejected: true
negative zero from double: 0
2.00 equals 2.0: false
2.00 compareTo 2.0: 0
```

Ví dụ này đối chiếu **ba cơ chế khởi tạo**, giá trị và scale, sự khác biệt trong phép cộng, ảnh hưởng của `float`, mất dữ liệu nguyên lớn qua `double`, NaN/Infinity và equality theo scale. Điều quan trọng nhất là các chuỗi rất dài **đã được giải thích bởi đầu vào binary64**, không phải “sự bất ổn” của phép cộng BigDecimal.

## 17. Bộ kiểm thử nên có

| Kiểm thử | Input / thao tác | Kỳ vọng |
| --- | --- | --- |
| Constructor double | `new BigDecimal(0.1)` | Giá trị decimal chính xác của binary64, có chữ số dài |
| Constructor text | `new BigDecimal("0.1")` | Decimal exact 0.1, scale 1 |
| Factory double | `BigDecimal.valueOf(0.1)` | `0.1`, tương ứng `Double.toString` |
| Factory sau cộng double | `BigDecimal.valueOf(0.1+0.2)` | `0.30000000000000004` |
| Cộng decimal exact | `new BigDecimal("0.1").add(new BigDecimal("0.2"))` | `0.3` |
| Cộng hai exact binary64 | `new BigDecimal(0.1).add(new BigDecimal(0.2))` | Không bằng đúng decimal 0.3 |
| Số biểu diễn nhị phân hữu hạn | `new BigDecimal(0.5)` | `0.5` |
| Float bị widen | `BigDecimal.valueOf((double)0.1f)` | `0.10000000149011612` |
| Số nguyên qua double | `2^53+1` chuyển long → double → BigDecimal | Mất 1 đơn vị |
| Tạo từ long | `BigDecimal.valueOf(9007199254740993L)` | Chính xác |
| NaN | `new BigDecimal(Double.NaN)` | `NumberFormatException` |
| Infinity | `BigDecimal.valueOf(+Infinity)` | `NumberFormatException` |
| Signed zero | `new BigDecimal(-0.0)` | Giá trị BigDecimal bằng 0, không giữ signed-zero |
| Scale vs numerical equality | `"2.00"` và `"2.0"` | `equals=false`, `compareTo=0` |
| Rounding 2 decimals | `exactBinary.setScale(2, HALF_UP)` | `0.10`; rounding là policy, không khôi phục text nguồn |

Ngoài test số học, hãy kiểm thử **chuỗi dữ liệu end-to-end** từ CSV/JSON/JDBC qua lớp service đến khi ghi DB hoặc xuất file: liệu có đoạn nào tự ép decimal thành `double`? Lỗi có thể nằm trong cấu hình framework, mapping kiểu, hoặc serializer chứ không nằm ngay ở constructor trong business code.

## 18. Những lỗi phổ biến và cách sửa

| Sai lầm | Hậu quả | Cách phù hợp |
| --- | --- | --- |
| Dùng `new BigDecimal(0.1)` và mong đúng decimal 0.1 | Nhận chính xác binary64, tạo nhiều chữ số thập phân | Dùng `new BigDecimal("0.1")` khi có text decimal |
| Gọi chuỗi dài là “lỗi làm tròn của BigDecimal” | Chẩn đoán sai nguyên nhân | Xem dữ liệu đã qua `double` trước khi tạo BigDecimal |
| Tin `BigDecimal.valueOf(double)` luôn sửa sai số | Phép cộng double trước đó vẫn giữ lỗi | Tạo và tính BigDecimal từ nguồn |
| Dùng `String.valueOf(double)` để khôi phục mọi chữ số nhập ban đầu | Chuỗi ngắn chỉ giữ roundtrip double | Giữ text decimal từ đầu nếu cần |
| Đổi `long` lớn thành `double` rồi gọi `valueOf` | Mất chính xác số nguyên > 2^53 | `BigDecimal.valueOf(long)` hoặc `BigInteger` |
| Dùng `new BigDecimal(0.1f)` và nghĩ đó là decimal 0.1 | `float` đã xấp xỉ và widen thành double | Dùng nguồn decimal, hoặc Float.toString theo policy |
| Dùng `setScale(2, HALF_UP)` ở mọi nơi để che nhiễu | Có thể làm mất giá trị hợp lệ và sai quy tắc rounding | Xác định đơn vị/scale/mode ở biên |
| Tưởng `MathContext(2)` nghĩa là 2 chữ số lẻ | Nó là 2 chữ số **có nghĩa** | Phân biệt `precision` và `scale` |
| Dùng `equals` làm kiểm tra cùng giá trị toán học | `equals` xét cả scale | `compareTo==0` khi không cần scale |
| Tin `stripTrailingZeros` xóa được nhiễu | Chữ số dư không nhất thiết là zero | Xử lý đúng nguồn, không làm sạch mù |
| Nhập NaN/Infinity vào BigDecimal | `NumberFormatException` | Check `Double.isFinite`; schema xử lý riêng |
| Tin BigDecimal giữ dấu `-0.0` | Không có signed-zero như double | Giữ bit/metadata ở mô hình riêng nếu cần |
| Parse số decimal qua `Double.parseDouble` trước `BigDecimal` | Mất precision ở bước giữa | `new BigDecimal(text)` trực tiếp |
| Cho rằng BigDecimal luôn chia được với kết quả hữu hạn | 1/3 là decimal vô hạn | Dùng `divide` với scale/rounding khi phù hợp |
| Làm tròn tại nhiều bước trung gian không theo chính sách | Sai tổng/thuế/phép đo | Quy định điểm làm tròn và bảo toàn exact trước đó |
| Truyền money dưới dạng JSON number rồi client parse binary64 tùy ý | Mất precision/scale trong tích hợp | Thiết kế schema và parser decimal chính xác |

## 19. Câu hỏi tự kiểm tra

1. Vì sao literal `0.1` trong một `double` không giữ được phân số toán học `1/10` chính xác?
2. `new BigDecimal(0.1)` chứa giá trị gì? Vì sao một chuỗi thập phân dài lại có thể là **chuyển đổi exact**?
3. `new BigDecimal("0.1")` và `BigDecimal.valueOf(0.1)` trông cùng kết quả. Chúng có cùng nguồn thông tin không?
4. Tại sao `BigDecimal.valueOf(0.1 + 0.2)` không cho decimal 0.3?
5. Hai con đường `new BigDecimal(0.1).add(new BigDecimal(0.2))` và `BigDecimal.valueOf(0.1+0.2)` khác nhau về thứ tự làm tròn/convert thế nào?
6. Những phân số nào như `0.5`, `0.25`, `0.125` được binary64 biểu diễn hữu hạn? Tiêu chí mẫu số là gì?
7. `BigDecimal.valueOf(0.1f)` có phục hồi decimal text gốc 0.1 không? Vì sao widening float sang double không làm thế?
8. Vì sao `2^53+1` có thể mất chính xác khi chuyển từ `long` sang `double`?
9. Nếu nhận chuỗi tiền `"1234.50"`, bạn chọn constructor nào để giữ cả giá trị và scale? Điều gì xảy ra nếu parse qua double?
10. `MathContext` và `setScale` khác nhau ở precision và số chữ số sau dấu như thế nào?
11. `new BigDecimal(0.1).setScale(2, HALF_UP)` trả gì? Có phải nó đã khôi phục chắc chắn giá trị nguồn decimal không?
12. `BigDecimal.equals` và `compareTo` khác nhau khi so `2.00` với `2.0`?
13. `new BigDecimal(Double.NaN)` có tạo object không? Vì sao?
14. `BigDecimal` có bảo toàn bit dấu của `-0.0` không? Nếu ứng dụng cần điều đó, giải pháp là gì?
15. Bạn sẽ kiểm tra ở những ranh giới nào để bảo đảm toàn hệ thống tính decimal exact, từ nhập liệu đến database và API?

## 20. Tài liệu tham khảo chính thức

- [Java SE 17 — `BigDecimal(double)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#%3Cinit%3E(double)): định nghĩa chuyển giá trị binary64 thành decimal exact, ví dụ `0.1` và khuyến nghị không dùng constructor này nếu muốn decimal người dùng nghĩ đến.
- [Java SE 17 — `BigDecimal.valueOf(double)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#valueOf(double)): chuyển qua biểu diễn chuẩn của `Double.toString(double)`.
- [Java SE 17 — `BigDecimal(String)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html#%3Cinit%3E(java.lang.String)): đọc trực tiếp chuỗi decimal, không qua double.
- [Java SE 17 — `BigDecimal`: precision, scale, MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): mô hình unscaled value/scale, các phép toán và làm tròn.
- [Java SE 17 — `Double`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Double.html): `Double.toString`, `parseDouble`, binary64, NaN và Infinity.
- [Java Language Specification `4.2.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.3): miền, cách biểu diễn và hành vi số dấu phẩy động Java.

**Ghi nhớ:** `new BigDecimal(double)` là phép chuyển **chính xác giá trị binary64**, nên có thể xuất hiện chuỗi decimal dài như khi dùng `0.1`. `new BigDecimal("0.1")` giữ đúng decimal từ ký tự nguồn; `BigDecimal.valueOf(0.1)` tạo decimal từ chuỗi chuẩn `Double.toString` của double đã có. Không có cách nào tự khôi phục chắc chắn dữ liệu decimal **đã bị mất** ở các bước tính toán double. Với hệ thống cần decimal exact, lựa chọn đúng nhất là **giữ dữ liệu decimal và xử lý bằng BigDecimal từ ranh giới đầu vào**, rồi làm tròn theo chính sách được xác định rõ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-007">valueOf và các hằng ZERO, ONE, TEN</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.valueOf và các hằng ZERO, ONE, TEN trong Java

`java.math.BigDecimal` là kiểu số thập phân có độ chính xác tùy ý trong Java. Khi tạo giá trị `BigDecimal`, hai câu hỏi cần được trả lời riêng: **đầu vào mang ý nghĩa gì** (số nguyên, decimal có scale hay số thực nhị phân `double`) và **cách biểu diễn thập phân nào cần giữ**. Các factory `valueOf` cùng ba hằng `ZERO`, `ONE`, `TEN` là các công cụ nền tảng cho việc đó.

Ví dụ:

```java
import java.math.BigDecimal;

BigDecimal a = BigDecimal.valueOf(42L);       // 42, scale 0
BigDecimal b = BigDecimal.valueOf(12345L, 2); // 123.45, scale 2
BigDecimal c = BigDecimal.valueOf(0.1);       // 0.1, scale 1
BigDecimal zero = BigDecimal.ZERO;            // 0, scale 0
BigDecimal one = BigDecimal.ONE;              // 1, scale 0
BigDecimal ten = BigDecimal.TEN;              // 10, scale 0
```

Đây là **mảnh mã minh họa** để đặt trong một phương thức. Điểm quan trọng: `valueOf(double)` **khác** `new BigDecimal(double)`; các hằng có **scale bằng 0**; và hai số có cùng giá trị nhưng khác scale có thể `compareTo` bằng 0 trong khi `equals` trả false.

## 1. Mô hình số thập phân của BigDecimal

Một `BigDecimal` được diễn giải bằng công thức:

```text
Giá trị = unscaledValue × 10^(-scale)
```

`unscaledValue()` là một `BigInteger` có dấu. `scale()` là một số nguyên `int` có thể dương, bằng 0 hoặc âm. `precision()` là số chữ số có nghĩa của unscaled value; **precision không phải scale**.

| Unscaled value | Scale | Giá trị số |
| ---: | ---: | ---: |
| 12345 | 0 | 12345 |
| 12345 | 2 | 123.45 |
| 12345 | 4 | 1.2345 |
| 12345 | -2 | 1234500 |
| 10 | 1 | 1.0 |
| 1 | 0 | 1 |
| 0 | 2 | 0.00 |

Hai đối tượng có thể cùng giá trị số học mà khác biểu diễn: `1` và `1.0` có giá trị bằng nhau, nhưng cặp `(unscaled=1, scale=0)` khác `(unscaled=10, scale=1)`. Vì vậy khi học các factory và hằng, phải luôn quan sát **cả giá trị và scale**.

## 2. Các overload của valueOf

| API | Kiểu đối số | Hành vi chính |
| --- | --- | --- |
| `BigDecimal.valueOf(long val)` | `long` | Tạo số decimal từ số nguyên chính xác, scale 0 |
| `BigDecimal.valueOf(long unscaledVal, int scale)` | `long`, `int` | Tạo cặp unscaled/scale theo công thức decimal |
| `BigDecimal.valueOf(double val)` | `double` | Tạo từ biểu diễn decimal round-trip của `Double.toString(val)` |

Chọn overload theo **loại thông tin bạn thật sự có**. Nếu nguồn là số nguyên lớn đang lưu trong `long`, tránh đổi sang `double` trước. Nếu nguồn là số nguyên lớn hơn cả `long`, dùng constructor từ `BigInteger` hoặc chuỗi số, thay vì ép về primitive.

Factory là phương thức `static`. Nó không làm tròn toàn bộ dữ liệu một cách “thần kỳ”; nó tạo đối tượng từ **giá trị truyền vào** theo ngữ nghĩa của từng overload.

## 3. valueOf(long): số nguyên chính xác, scale bằng 0

```java
BigDecimal count = BigDecimal.valueOf(12345L);
System.out.println(count);                 // 12345
System.out.println(count.unscaledValue()); // 12345
System.out.println(count.scale());         // 0
```

Mọi giá trị `long` đều có thể biểu diễn chính xác bằng `BigDecimal`. Do đó, factory này rất phù hợp với các trường số nguyên, bộ đếm hoặc giá trị đơn vị nhỏ nhất đã được xác định.

**Không vòng qua double:**

```java
long large = 9_007_199_254_740_993L; // 2^53 + 1.
BigDecimal exact = BigDecimal.valueOf(large); // Chính xác 9007199254740993.
BigDecimal lossy = BigDecimal.valueOf((double) large);
// Double đã làm tròn thành 9007199254740992 trước khi valueOf được gọi.
```

`double` có 53 bit significand hiệu dụng nên không biểu diễn được mọi số nguyên lớn hơn `2^53`. Nếu đã mất một đơn vị ở bước cast, `BigDecimal` không thể phục hồi bit thông tin đó.

## 4. valueOf(long, int): xây dựng từ unscaled value và scale

```java
BigDecimal dollars = BigDecimal.valueOf(12345L, 2);
System.out.println(dollars); // 123.45
```

Hai đối số **không phải “phần nguyên” và “phần thập phân”**. Đối số đầu là unscaled integer, đối số hai là scale. Ví dụ:

| Lời gọi | Giá trị toán học | Kết quả `toString()` trên Java 17 |
| --- | ---: | --- |
| `valueOf(12345L,0)` | 12345 | 12345 |
| `valueOf(12345L,2)` | 123.45 | 123.45 |
| `valueOf(12345L,-2)` | 1234500 | 1.2345E+6 |
| `valueOf(0L,2)` | 0 | 0.00 |

**Scale âm là hợp lệ**: unscaled 12345, scale -2 nghĩa là nhân với `10^2`. Vì thế `toString()` có thể dùng dạng số mũ. Khi một biểu diễn không có số mũ được yêu cầu, dùng `toPlainString()`.

Ứng dụng thường gặp là số tiền lưu bằng đơn vị nhỏ nhất, ví dụ 125 cents và quy tắc decimal scale 2:

```java
long cents = 125;
BigDecimal amount = BigDecimal.valueOf(cents, 2); // 1.25.
```

Cần ghi rõ đơn vị và scale ở hợp đồng dữ liệu. Nếu nguồn đã tính theo đơn vị lớn, áp scale 2 một lần nữa sẽ làm giá trị bị chia cho 100 ngoài ý muốn.

## 5. valueOf(double) được tạo như thế nào?

Với `BigDecimal.valueOf(double)`, Java dùng **biểu diễn chuỗi decimal round-trip** của `double` (tương tự chuyển `Double.toString` rồi dựng BigDecimal). Ví dụ:

```java
BigDecimal value = BigDecimal.valueOf(0.1);
System.out.println(value.toPlainString()); // 0.1
System.out.println(value.scale());         // 1
```

Một `double` lưu gần đúng 0.1 theo nhị phân, nhưng `Double.toString(0.1)` trả `"0.1"` vì đó là chuỗi thập phân ngắn thích hợp để parse ngược về cùng giá trị double. `valueOf(0.1)` từ đó tạo ra **decimal 0.1 với scale 1**.

Điều này không có nghĩa mọi phép tính đã thực hiện trước đó bằng `double` được sửa lại cho chính xác:

```java
double computed = 0.1 + 0.2;
System.out.println(computed);                   // 0.30000000000000004
System.out.println(BigDecimal.valueOf(computed)); // 0.30000000000000004
```

Nếu nghiệp vụ yêu cầu cộng decimal chính xác, nên tạo BigDecimal từ decimal gốc **trước khi cộng**, không sau khi phép cộng double đã gây sai khác.

## 6. valueOf(0.1) khác new BigDecimal(0.1)

Đây là một trong những nhầm lẫn phổ biến nhất:

```java
BigDecimal a = BigDecimal.valueOf(0.1);
BigDecimal b = new BigDecimal(0.1);
BigDecimal c = new BigDecimal("0.1");
```

| Biểu thức | Giá trị hiển thị |
| --- | --- |
| `BigDecimal.valueOf(0.1)` | `0.1` |
| `new BigDecimal("0.1")` | `0.1` |
| `new BigDecimal(0.1)` | `0.1000000000000000055511151231257827021181583404541015625` |

`new BigDecimal(double)` lưu **đúng giá trị thực của mẫu nhị phân double đã có**, diễn đạt nó bằng số thập phân chính xác. Giá trị nhị phân của literal `0.1` hơi lớn hơn đúng 1/10, nên chuỗi dài xuất hiện.

`valueOf(0.1)` dùng chuỗi decimal round-trip `"0.1"`, còn `new BigDecimal("0.1")` đi thẳng từ ký tự người viết nhập. Cả hai tạo decimal chính xác 0.1 trong ví dụ, nhưng nguồn dữ liệu và ý nghĩa API vẫn khác.

**Lựa chọn thực tiễn:** nếu nguồn là chuỗi số tiền, số đo decimal hay dữ liệu JSON decimal cần exact, đọc từ chuỗi decimal với `new BigDecimal(text)`; nếu nguồn là `long`, dùng `valueOf(long)`; nếu phải tiếp nhận `double`, `valueOf(double)` thường có kết quả decimal dễ giải thích hơn constructor trực tiếp từ double, nhưng phải nhớ double đã là đầu vào xấp xỉ.

## 7. Ba hằng ZERO, ONE, TEN

Ba hằng có sẵn:

```java
BigDecimal zero = BigDecimal.ZERO;
BigDecimal one = BigDecimal.ONE;
BigDecimal ten = BigDecimal.TEN;
```

| Hằng | `unscaledValue()` | `scale()` | Giá trị |
| --- | ---: | ---: | ---: |
| `BigDecimal.ZERO` | 0 | 0 | 0 |
| `BigDecimal.ONE` | 1 | 0 | 1 |
| `BigDecimal.TEN` | 10 | 0 | 10 |

Chúng rất hữu ích làm giá trị khởi tạo, số hạng tăng thêm một đơn vị hoặc nhân với 10. Không cần viết `new BigDecimal("0")` lặp đi lặp lại.

```java
BigDecimal total = BigDecimal.ZERO;
total = total.add(BigDecimal.ONE); // 1.
total = total.multiply(BigDecimal.TEN); // 10.
```

**Cả ba hằng có scale 0**, không tự mang scale 2 của tiền tệ. Nếu muốn biểu diễn zero là `0.00`, hãy tạo giá trị có scale 2 từ `ZERO.setScale(2)` hoặc `valueOf(0L,2)`.

## 8. Hằng có giá trị 0 không đồng nghĩa mọi BigDecimal 0 bằng nhau theo equals

Xét:

```java
BigDecimal zero = BigDecimal.ZERO;          // 0, scale 0.
BigDecimal moneyZero = BigDecimal.valueOf(0L, 2); // 0.00, scale 2.

System.out.println(zero.equals(moneyZero));   // false.
System.out.println(zero.compareTo(moneyZero)); // 0.
```

Cả hai bằng zero về mặt toán học. Tuy nhiên:

- `equals()` so sánh **giá trị và scale**, do đó false;
- `compareTo()` so sánh **giá trị số học**, do đó 0;
- `signum()` là 0 đối với mọi BigDecimal có giá trị zero, bất kể scale.

Nếu muốn kiểm tra “số này có bằng 0 không” theo nghiệp vụ số học, dùng `value.signum() == 0` hoặc `value.compareTo(BigDecimal.ZERO) == 0`. Nếu muốn kiểm tra cả biểu diễn scale, dùng `equals` là hợp lý.

**Hệ quả cho collection:** `HashSet<BigDecimal>` dựa trên `equals/hashCode`, trong khi `TreeSet<BigDecimal>` với thứ tự tự nhiên dùng `compareTo`; do đó `1.0` và `1.00` có thể được xem là hai giá trị khóa khác nhau trong HashSet nhưng cùng vị trí thứ tự trong TreeSet. Chọn cấu trúc dữ liệu theo hợp đồng nghiệp vụ.

## 9. valueOf(1L) không giống valueOf(1.0) về scale

Java quyết định overload theo **kiểu tĩnh của tham số**:

```java
BigDecimal integerOne = BigDecimal.valueOf(1L); // 1, scale 0.
BigDecimal decimalOne = BigDecimal.valueOf(1.0); // 1.0, scale 1.

System.out.println(integerOne.equals(BigDecimal.ONE)); // true
System.out.println(decimalOne.equals(BigDecimal.ONE)); // false
System.out.println(decimalOne.compareTo(BigDecimal.ONE)); // 0
```

Cả hai có giá trị số học là 1. Nhưng `Double.toString(1.0)` cho `"1.0"`, nên factory từ double giữ dạng decimal scale 1. Điều này quan trọng với báo cáo số chữ số sau dấu thập phân, mã hóa decimal và các quy tắc nhất quán cho equality/hash.

Có thể đặt scale chủ động:

```java
BigDecimal oneAtScale2 = BigDecimal.ONE.setScale(2);
System.out.println(oneAtScale2.toPlainString()); // 1.00
```

`setScale` trả về kết quả BigDecimal có scale mới; nó không sửa hằng `ONE`.

## 10. BigDecimal không có negative zero riêng

`double` có hai mẫu bit `+0.0` và `-0.0`. `BigDecimal` thì biểu diễn zero với unscaled value bằng 0; không có giá trị toán học zero âm độc lập.

```java
BigDecimal z = BigDecimal.valueOf(-0.0);
System.out.println(z);          // 0.0
System.out.println(z.scale());  // 1
System.out.println(z.signum()); // 0
```

Dấu âm của `-0.0` không được giữ như một trạng thái bit-level của BigDecimal, nhưng **scale 1** của chuỗi `"0.0"` vẫn được giữ. Vì vậy `z.equals(BigDecimal.ZERO)` là false, còn `z.compareTo(BigDecimal.ZERO)` bằng 0.

Nếu một định dạng dữ liệu yêu cầu **bảo toàn bit dấu IEEE 754**, không dùng BigDecimal làm bước trung gian rồi mong khôi phục raw bit zero. Dùng giá trị `double` hoặc raw bits theo một hợp đồng truyền tải thích hợp.

## 11. NaN và Infinity có tạo được BigDecimal hay không?

Không. BigDecimal chỉ biểu diễn những số decimal **hữu hạn**. Vì vậy:

```java
// BigDecimal.valueOf(Double.NaN) -> NumberFormatException.
// BigDecimal.valueOf(Double.POSITIVE_INFINITY) -> NumberFormatException.
```

Kiểm tra trước khi gọi nếu input từ nguồn floating-point không bảo đảm hữu hạn:

```java
static BigDecimal requireFiniteDecimal(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("Expected finite value");
    }
    return BigDecimal.valueOf(value);
}
```

Đây là **phương thức minh họa trong lớp** có import BigDecimal, không phải chương trình độc lập. Kiểm tra `Double.isFinite` chỉ xử lý NaN/Infinity, không thể phục hồi số decimal lý tưởng đã mất chính xác trong các phép tính double trước đó.

## 12. BigDecimal bất biến: add không cập nhật đối tượng cũ

`BigDecimal` là lớp **immutable**, nên `add`, `subtract`, `multiply`, `divide`, `setScale` và `stripTrailingZeros` đều trả kết quả, không tự sửa đối tượng gốc.

```java
BigDecimal total = BigDecimal.ZERO;
total.add(BigDecimal.ONE);        // Không dùng giá trị trả về: total vẫn là 0.
total = total.add(BigDecimal.ONE); // total trở thành 1.
```

Đặc biệt khi viết accumulator:

```java
BigDecimal sum = BigDecimal.ZERO;
for (BigDecimal x : amounts) {
    sum = sum.add(x);
}
```

Mảnh mã này giả định `amounts` là danh sách BigDecimal đã có. Với yêu cầu tiền tệ có scale 2, cần định nghĩa chính sách scale/ràng buộc đầu vào thay vì nghĩ rằng hằng ZERO đã tự có đúng hai chữ số thập phân.

Không nên dùng toán tử cộng `+` giữa hai BigDecimal: Java không cho nạp chồng toán tử `+` cho đối tượng này. Dùng `sum.add(x)`.

## 13. MathContext và RoundingMode: valueOf không thay thế chính sách làm tròn

`valueOf` chỉ tạo giá trị; các phép toán tiếp theo có thể cần quy tắc precision/rounding.

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = BigDecimal.valueOf(3L);

// one.divide(three) sẽ ném ArithmeticException vì 1/3 vô hạn decimal.

BigDecimal result = one.divide(
        three,
        new java.math.MathContext(10, java.math.RoundingMode.HALF_UP));
System.out.println(result.toPlainString()); // 0.3333333333
```

Một `MathContext` với precision 10 giới hạn kết quả **10 chữ số có nghĩa**, không phải scale 10 mặc định. `setScale(2, RoundingMode.HALF_UP)` là quyết định khác: yêu cầu hai chữ số sau dấu thập phân.

Đừng nhầm `valueOf(12345L,2)` với “làm tròn 12345 đến hai chữ số thập phân”: nó chỉ đặt scale để giá trị trở thành 123.45.

## 14. toString, toPlainString và stripTrailingZeros

Cách in phụ thuộc scale:

```java
BigDecimal a = BigDecimal.valueOf(12345L, -2);
System.out.println(a.toString());       // 1.2345E+6
System.out.println(a.toPlainString()); // 1234500
```

Một số zero ở cuối có thể là **thông tin biểu diễn** mà nghiệp vụ quan tâm:

```java
BigDecimal original = new BigDecimal("10.00");
BigDecimal stripped = original.stripTrailingZeros();

System.out.println(original); // 10.00
System.out.println(stripped); // 1E+1
```

Hai giá trị đều bằng 10 theo số học, nhưng scale khác. `stripTrailingZeros` không đơn thuần là trang trí hiển thị; nó có thể làm thay đổi scale và kết quả `equals`. Nếu số chữ số sau dấu thập phân được dùng trong báo cáo/hợp đồng, không strip một cách mặc định.

`valueOf(1e20)` cũng cho thấy scale âm tự nhiên xuất hiện từ chuỗi khoa học: trên Java 17, kết quả là `1.0E+20`, unscaled 10, scale -19.

## 15. Hằng TEN, dịch dấu thập phân và đơn vị

`BigDecimal.TEN` có giá trị 10 với scale 0. Có thể nhân với TEN:

```java
BigDecimal hundred = BigDecimal.TEN.multiply(BigDecimal.TEN); // 100.
```

Nếu mục tiêu của bạn là **nhân với lũy thừa 10** hoặc đổi vị trí dấu thập phân, xem các phương thức phù hợp:

```java
BigDecimal amount = new BigDecimal("12.34");
BigDecimal cents = amount.movePointRight(2);       // 1234.
BigDecimal scaled = amount.scaleByPowerOfTen(2);   // 1234 về giá trị.
```

Hai lời gọi có thể cho scale không giống nhau ở một số trường hợp, đặc biệt khi chuyển qua scale âm. Cần xác định yêu cầu về **giá trị** và **scale** riêng, không suy rằng mọi cách nhân 10 đều đồng nhất theo `equals`.

## 16. Có nên so sánh hai giá trị BigDecimal bằng == không?

Không, nếu mục đích là so sánh giá trị. `==` trên đối tượng Java là so sánh **identity tham chiếu**, không phải giá trị decimal. Một số factory có thể tối ưu bằng cách **cache/tái sử dụng đối tượng** cho các số thường gặp; vì thế càng không được dùng kết quả `==` trong nghiệp vụ.

```java
BigDecimal a = BigDecimal.valueOf(1L);
BigDecimal b = BigDecimal.ONE;

// a == b không phải phép so sánh giá trị đáng tin cho mọi API/JDK.
// a.equals(b) là true; a.compareTo(b) == 0 cũng true.
```

Để so sánh **value + scale**, dùng `equals`. Để so sánh **giá trị số học**, dùng `compareTo`. Để kiểm tra **có bằng 0 về số học**, `signum() == 0` thường đơn giản hơn.

## 17. Chọn cách tạo BigDecimal theo nguồn dữ liệu

| Nguồn | Cách tạo khuyến nghị | Lý do |
| --- | --- | --- |
| `long` đã xác thực | `BigDecimal.valueOf(long)` | Bảo toàn số nguyên, scale 0 |
| Số nguyên đơn vị nhỏ nhất + quy ước decimal scale | `BigDecimal.valueOf(unscaled, scale)` | Bảo toàn cặp số decimal |
| Chuỗi decimal từ input/API | `new BigDecimal(String)` | Không vòng qua xấp xỉ nhị phân |
| `double` hữu hạn đã tồn tại | `BigDecimal.valueOf(double)` nếu ngữ nghĩa phù hợp | Dựa trên decimal round-trip |
| Cần nghiên cứu exact binary double | `new BigDecimal(double)` | Lộ rõ giá trị double nhị phân đã lưu |
| Zero làm giá trị khởi tạo | `BigDecimal.ZERO` | Rõ ý nghĩa, scale 0 |
| Một đơn vị | `BigDecimal.ONE` | Rõ ý nghĩa, scale 0 |
| Hệ số 10 | `BigDecimal.TEN` | Rõ ý nghĩa, scale 0 |
| Zero với 2 chữ số decimal | `BigDecimal.ZERO.setScale(2)` | Giữ scale 2 |
| Số nguyên vượt long | `new BigDecimal(BigInteger)` hoặc chuỗi | Không gây mất bit do ép primitive |

Nếu dữ liệu đến từ kết quả tính double `0.1 + 0.2`, chuyển sang BigDecimal sau cùng **không cho đúng decimal 0.3**; phải thiết kế kiểu dữ liệu ngay từ đầu.

## 18. Ví dụ Java 17+ hoàn chỉnh và kết quả chạy

Lưu tệp **`BigDecimalValueOfDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalValueOfDemo {
    static void describe(String label, BigDecimal number) {
        System.out.println(label + "=" + number.toString()
                + ",scale=" + number.scale()
                + ",unscaled=" + number.unscaledValue());
    }

    static String failure(Runnable work) {
        try {
            work.run();
            return "NO_EXCEPTION";
        } catch (ArithmeticException | NumberFormatException ex) {
            return ex.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        describe("zeroConstant", BigDecimal.ZERO);
        describe("oneConstant", BigDecimal.ONE);
        describe("tenConstant", BigDecimal.TEN);
        describe("longValueOf", BigDecimal.valueOf(12345L));
        describe("decimalScaled", BigDecimal.valueOf(12345L, 2));
        describe("negativeScale", BigDecimal.valueOf(12345L, -2));
        describe("zeroScale2", BigDecimal.valueOf(0L, 2));
        describe("doublePointOne", BigDecimal.valueOf(0.1));
        describe("doubleConstructor", new BigDecimal(0.1));
        describe("decimalString", new BigDecimal("0.1"));
        describe("onePointZero", BigDecimal.valueOf(1.0));
        describe("negativeDoubleZero", BigDecimal.valueOf(-0.0));
        describe("scientificDouble", BigDecimal.valueOf(1e20));

        System.out.println("valueOfEqualsString="
                + BigDecimal.valueOf(0.1).equals(new BigDecimal("0.1")));
        System.out.println("valueOfEqualsDoubleCtor="
                + BigDecimal.valueOf(0.1).equals(new BigDecimal(0.1)));
        System.out.println("onePointZeroEqualsONE="
                + BigDecimal.valueOf(1.0).equals(BigDecimal.ONE));
        System.out.println("onePointZeroCompareONE="
                + BigDecimal.valueOf(1.0).compareTo(BigDecimal.ONE));
        System.out.println("valueOfOneLongEqualsONE="
                + BigDecimal.valueOf(1L).equals(BigDecimal.ONE));
        System.out.println("scaledZeroEqualsZERO="
                + BigDecimal.valueOf(0L, 2).equals(BigDecimal.ZERO));
        System.out.println("scaledZeroCompareZERO="
                + BigDecimal.valueOf(0L, 2).compareTo(BigDecimal.ZERO));
        System.out.println("negativeZeroEqualsZERO="
                + BigDecimal.valueOf(-0.0).equals(BigDecimal.ZERO));
        System.out.println("negativeZeroSignum="
                + BigDecimal.valueOf(-0.0).signum());

        BigDecimal original = BigDecimal.valueOf(7L);
        BigDecimal increased = original.add(BigDecimal.ONE);
        System.out.println("immutableOriginal=" + original);
        System.out.println("immutableResult=" + increased);

        BigDecimal tenth = BigDecimal.valueOf(0.1);
        BigDecimal fifth = BigDecimal.valueOf(0.2);
        System.out.println("decimalSum=" + tenth.add(fifth).toPlainString());
        System.out.println("binaryDoubleComparison=" + ((0.1 + 0.2) == 0.3));

        System.out.println("divideExactThirdError="
                + failure(() -> BigDecimal.ONE.divide(BigDecimal.valueOf(3L))));
        System.out.println("divideRoundedThird="
                + BigDecimal.ONE.divide(BigDecimal.valueOf(3L),
                        new MathContext(10, RoundingMode.HALF_UP)).toPlainString());
        System.out.println("nanValueOfError="
                + failure(() -> BigDecimal.valueOf(Double.NaN)));
        System.out.println("positiveInfinityValueOfError="
                + failure(() -> BigDecimal.valueOf(Double.POSITIVE_INFINITY)));
        System.out.println("stripTrailingZeros="
                + new BigDecimal("10.00").stripTrailingZeros().toString());
        System.out.println("oneScaled=" + BigDecimal.ONE.setScale(2).toPlainString());
        System.out.println("tenTimesTen=" + BigDecimal.TEN.multiply(BigDecimal.TEN));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -encoding UTF-8 BigDecimalValueOfDemo.java
java BigDecimalValueOfDemo
```

Stdout đã kiểm chứng:

```text
zeroConstant=0,scale=0,unscaled=0
oneConstant=1,scale=0,unscaled=1
tenConstant=10,scale=0,unscaled=10
longValueOf=12345,scale=0,unscaled=12345
decimalScaled=123.45,scale=2,unscaled=12345
negativeScale=1.2345E+6,scale=-2,unscaled=12345
zeroScale2=0.00,scale=2,unscaled=0
doublePointOne=0.1,scale=1,unscaled=1
doubleConstructor=0.1000000000000000055511151231257827021181583404541015625,scale=55,unscaled=1000000000000000055511151231257827021181583404541015625
decimalString=0.1,scale=1,unscaled=1
onePointZero=1.0,scale=1,unscaled=10
negativeDoubleZero=0.0,scale=1,unscaled=0
scientificDouble=1.0E+20,scale=-19,unscaled=10
valueOfEqualsString=true
valueOfEqualsDoubleCtor=false
onePointZeroEqualsONE=false
onePointZeroCompareONE=0
valueOfOneLongEqualsONE=true
scaledZeroEqualsZERO=false
scaledZeroCompareZERO=0
negativeZeroEqualsZERO=false
negativeZeroSignum=0
immutableOriginal=7
immutableResult=8
decimalSum=0.3
binaryDoubleComparison=false
divideExactThirdError=ArithmeticException
divideRoundedThird=0.3333333333
nanValueOfError=NumberFormatException
positiveInfinityValueOfError=NumberFormatException
stripTrailingZeros=1E+1
oneScaled=1.00
tenTimesTen=100
```

Các dòng đầu lần lượt chứng minh hằng ZERO/ONE/TEN có scale 0; `decimalScaled` và `negativeScale` kiểm tra công thức unscaled/scale. `doublePointOne` khác `doubleConstructor` vì đường chuyển đổi từ double khác nhau. `onePointZeroEqualsONE=false` nhưng `onePointZeroCompareONE=0` chứng minh so sánh representation khác so sánh giá trị. `scaledZeroEqualsZERO=false` và `negativeZeroSignum=0` minh họa zero decimal có scale nhưng không có negative-zero value riêng.

`immutableOriginal=7` và `immutableResult=8` chứng minh tính bất biến; `decimalSum=0.3` đối lập với `binaryDoubleComparison=false` khi nguồn số được tạo đúng thời điểm. Các dòng `divideExactThirdError`, `nanValueOfError` và `positiveInfinityValueOfError` cho thấy ràng buộc của phép chia và miền BigDecimal. Hai dòng cuối xác nhận `setScale` và nhân với TEN.

Hàm `failure` trong chương trình bắt ngoại lệ để **hiển thị tên trong bài học**; đây không phải gợi ý nuốt ngoại lệ khi lập trình production.

## 19. Sai lầm hay gặp

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Dùng `new BigDecimal(0.1)` tưởng ra decimal 0.1 | Được giá trị binary double triển khai dưới dạng decimal dài | Dùng chuỗi decimal hoặc `valueOf(0.1)` khi phù hợp |
| Dùng `valueOf(double)` để chữa phép tính double đã sai | Giá trị double đã làm tròn trước đó | Tính BigDecimal từ đầu |
| Dùng `valueOf((double)bigLong)` | Mất đơn vị số nguyên lớn trên 2^53 | Gọi `valueOf(long)` |
| Nghĩ `valueOf(12345,2)` nghĩa 12345.2 | Hai tham số là unscaled và scale | Hiểu thành 123.45 |
| Nghĩ hằng ZERO có scale 2 | ZERO có scale 0 | Tạo `ZERO.setScale(2)` |
| Nghĩ `ZERO.equals(0.00)` luôn true | Scale khác khiến equals false | Dùng `compareTo` cho giá trị |
| So sánh BigDecimal bằng `==` | Đang so identity, không phải value | Dùng `equals`/`compareTo` |
| Gọi `sum.add(x)` nhưng không dùng kết quả | Tổng không đổi | Gán `sum = sum.add(x)` |
| Quên chính sách rounding khi chia 1/3 | `ArithmeticException` | Truyền `MathContext` hoặc scale + rounding mode |
| Cho NaN/Infinity vào `valueOf(double)` | `NumberFormatException` | Kiểm tra `Double.isFinite` |
| Dùng `stripTrailingZeros` khi phải giữ scale | Có thể đổi scale và toString | Giữ scale theo hợp đồng |
| Coi `valueOf(1L)` và `valueOf(1.0)` cùng representation | Scale 0 khác scale 1 | Chú ý kiểu đầu vào |

## 20. Câu hỏi tự kiểm tra

1. **BigDecimal được biểu diễn bằng công thức nào?** Unscaled value nhân với `10^(-scale)`.
2. **`valueOf(12345L,2)` tạo số gì?** 123.45.
3. **`valueOf(12345L,-2)` tạo số gì?** 1,234,500; `toString` có thể in `1.2345E+6`.
4. **Vì sao `valueOf(0.1)` khác `new BigDecimal(0.1)`?** Factory đi qua chuỗi decimal round-trip; constructor lấy chính xác giá trị binary double đã làm tròn.
5. **Ba hằng ZERO, ONE, TEN có scale bao nhiêu?** Đều scale 0.
6. **`valueOf(1.0).equals(BigDecimal.ONE)` là gì?** False, vì khác scale.
7. **Nếu muốn so sánh chúng theo giá trị số học?** Dùng `compareTo`, kết quả 0.
8. **Vì sao `BigDecimal.valueOf(0L,2).equals(ZERO)` false?** Hai zero có scale 2 và 0.
9. **Tạo zero với hai chữ số decimal thế nào?** `BigDecimal.ZERO.setScale(2)` hoặc `valueOf(0L,2)`.
10. **`valueOf(-0.0).signum()` bằng bao nhiêu?** 0; BigDecimal không có negative zero riêng.
11. **Có thể đưa `Double.NaN` vào `valueOf` không?** Không, ném `NumberFormatException`.
12. **`BigDecimal.ONE.divide(BigDecimal.valueOf(3L))` có thành công không?** Không nếu không cung cấp rounding context vì 1/3 không có biểu diễn decimal hữu hạn.
13. **Gọi `total.add(ONE)` mà không gán lại có đổi total không?** Không, BigDecimal immutable.
14. **Tạo BigDecimal từ long nguyên lớn bằng cách nào để giữ chính xác?** `valueOf(long)`.
15. **`stripTrailingZeros` có thể đổi scale không?** Có, ví dụ `10.00` thành `1E+1`.
16. **Factory có thể cache object thì có nên dùng `==`?** Không; caching không phải hợp đồng so sánh giá trị.

**Ghi nhớ:** `valueOf(long)` tạo số nguyên decimal chính xác với scale 0; `valueOf(long,scale)` dùng unscaled value và scale; `valueOf(double)` dựa trên biểu diễn decimal round-trip của double hiện có. Các hằng `ZERO`, `ONE`, `TEN` **bất biến** và đều có **scale 0**. Chú ý sự khác nhau giữa giá trị số học và representation scale, kiểm soát rounding ở phép toán, và không dùng `double` làm trung gian nếu cần decimal exact từ đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-008">Giữ dấu 0 cuối trong biểu diễn có ý nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ dấu 0 cuối trong biểu diễn có ý nghĩa với BigDecimal (Java)

## 1. Vì sao “1.20” không đơn giản là “1.2”?

Trong toán học, **1,20 và 1,2 bằng nhau về giá trị**. Nhưng trong nhiều hệ thống, chuỗi thập phân `"1.20"` còn mang một thông tin khác: người nhập đã cung cấp giá trị tới **hai chữ số sau dấu thập phân**, thay vì một. Điều này có thể có ý nghĩa đối với tiền tệ, giá niêm yết, dữ liệu đo đạc, file báo cáo hoặc yêu cầu lưu trữ.

`java.math.BigDecimal` lưu không chỉ giá trị số học mà còn một thành phần **scale**. Nhờ đó:

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("1.2");

System.out.println(a);         // 1.20
System.out.println(b);         // 1.2
System.out.println(a.scale()); // 2
System.out.println(b.scale()); // 1
~~~

Đây là đoạn mã đặt trong một phương thức và cần `import java.math.BigDecimal;`. Cả hai đại diện cùng một số toán học nhưng giữ **hai biểu diễn thập phân khác nhau**.

Mục tiêu của bài:

- Hiểu `unscaledValue`, `scale` và `precision` của `BigDecimal`.
- Biết cách **giữ số 0 cuối** khi đọc, tính toán, lưu trữ và xuất chuỗi.
- Không dùng `stripTrailingZeros()` khi các chữ số 0 cuối có ý nghĩa.
- Phân biệt `equals()` với `compareTo()` và tác động lên `HashMap`/`TreeSet`.
- Hiểu `setScale()` khi thêm/bớt chữ số 0, quy tắc làm tròn và các thay đổi biểu diễn do phép toán.
- Tránh làm mất thông tin khi đi qua `double`, JSON, JDBC hoặc bộ định dạng.
- Nhận biết giới hạn: scale có thể biểu thị **độ phân giải đã ghi**, nhưng không tự chứng minh độ chính xác vật lý của phép đo.

Các ví dụ dùng **Java 17+**, ưu tiên xây dựng BigDecimal từ **chuỗi thập phân gốc**.

## 2. Mô hình unscaled value × 10^(-scale)

Giá trị toán học của một `BigDecimal` được biểu diễn bởi:

\[
\text{value}=\text{unscaledValue}\times10^{-\text{scale}}
\]

- **Unscaled value** là một số nguyên `BigInteger` chứa các chữ số có nghĩa trong biểu diễn hiện tại.
- **Scale** là số chữ số bên phải dấu thập phân, khi scale dương; scale cũng có thể là 0 hoặc âm.
- **Precision** là số chữ số của giá trị unscaled (theo định nghĩa API), với quy tắc đặc biệt khi giá trị bằng 0.

| Chuỗi tạo BigDecimal | Unscaled value | Scale | Precision | Giá trị toán học |
| --- | ---: | ---: | ---: | --- |
| `"1.2"` | 12 | 1 | 2 | 1,2 |
| `"1.20"` | 120 | 2 | 3 | 1,2 |
| `"1.200"` | 1200 | 3 | 4 | 1,2 |
| `"0.00"` | 0 | 2 | 1 | 0 |
| `"1200"` | 1200 | 0 | 4 | 1200 |
| `"1E+3"` | 1 | −3 | 1 | 1000 |

Ví dụ kiểm tra:

~~~java
BigDecimal amount = new BigDecimal("1.20");

System.out.println(amount.unscaledValue()); // 120
System.out.println(amount.scale());         // 2
System.out.println(amount.precision());     // 3
~~~

**Điểm cốt lõi:** các số 0 cuối của `1.20` không chỉ là chi tiết của `println`. Chúng gắn với `scale=2` và `unscaledValue=120` trong mô hình dữ liệu của đối tượng.

Không nhầm `precision` với `scale`. `1.20` có precision 3, scale 2; `1200` có precision 4, scale 0. Một giá trị có thể có nhiều biểu diễn hợp lệ với precision và scale khác nhau.

## 3. “Có ý nghĩa” là yêu cầu của hợp đồng dữ liệu

Có ít nhất ba tình huống cần phân biệt.

### 3.1. Số tiền có hai chữ số thập phân

Một hóa đơn biểu diễn `"12.30"` có thể bắt buộc thể hiện **hai chữ số sau dấu chấm** vì đơn vị tiền tệ và tiêu chuẩn xuất chứng từ. Dù giá trị số của `12.30` bằng `12.3`, định dạng không hoàn toàn có thể thay thế trong bản in hoặc API.

### 3.2. Số đo được ghi với độ phân giải

Một phép đo chiều dài `"1.20 m"` có thể được báo cáo đến 0,01 m. Việc biến thành `"1.2 m"` có thể làm mất **thông tin về mức chi tiết đã được ghi**.

Tuy nhiên, một chuỗi có hai chữ số thập phân **không tự chứng minh cảm biến chính xác ±0,01 m**. Nếu cần bất định đo lường, độ chính xác thiết bị, sai số chuẩn hoặc đơn vị, phải lưu chúng bằng metadata/hợp đồng riêng.

### 3.3. Giá trị thuần toán học

Trong một thuật toán chỉ cần so sánh giá trị số và không quan tâm biểu diễn, `1.20` và `1.2` có thể được coi là tương đương. Lúc đó loại số 0 cuối có thể chấp nhận được, thậm chí có ích khi chuẩn hóa dữ liệu.

**Nguyên tắc thiết kế:** xác định hệ thống cần **giữ đúng số gốc**, **giữ một scale chuẩn**, hay chỉ **giữ giá trị số học**. Ba mục tiêu này không hoàn toàn giống nhau.

## 4. Tạo BigDecimal từ String để bảo toàn biểu diễn thập phân

Cách đáng tin khi nhận văn bản decimal:

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("0.000");
BigDecimal c = new BigDecimal("125.00");

System.out.println(a.toPlainString()); // 1.20
System.out.println(b.toPlainString()); // 0.000
System.out.println(c.toPlainString()); // 125.00
~~~

`BigDecimal(String)` phân tích văn bản decimal và thiết lập unscaled value cùng scale theo biểu diễn số được cung cấp. Nó không mặc định gọi `stripTrailingZeros`.

Khi input là một trường decimal có định dạng cố định, nên lưu **chuỗi ban đầu**, hoặc ít nhất là `BigDecimal` cùng hợp đồng scale cụ thể, tùy yêu cầu tái tạo. Nếu cần giữ nguyên mọi ký tự như dấu cộng đầu, chữ `E` hoa/thường hoặc số 0 ở đầu phần nguyên, **chỉ BigDecimal không đủ**: nó lưu cấu trúc số, không lưu toàn bộ văn bản gốc.

### Không đi qua double để “giữ 1.20”

~~~java
BigDecimal text = new BigDecimal("1.20");
BigDecimal fromDouble = BigDecimal.valueOf(1.20);

System.out.println(text);       // 1.20
System.out.println(fromDouble); // 1.2
~~~

Lý do: literal `1.20` đã được Java coi là một giá trị `double`; cách viết `1.20` và `1.2` không được giữ như metadata văn bản. `BigDecimal.valueOf(double)` lấy biểu diễn decimal từ `Double.toString(double)`, nên không thể phục hồi số 0 cuối đã mất.

`new BigDecimal(1.20)` còn mang một vấn đề khác: constructor từ `double` diễn giải chính xác **giá trị nhị phân đã xấp xỉ**, có thể dẫn đến phần thập phân dài. Khi cần giữ dữ liệu decimal đã nhập, hãy đọc trực tiếp từ chuỗi hoặc kiểu decimal chuyên dụng.

## 5. equals và compareTo: bằng số chưa chắc bằng đối tượng

`BigDecimal` có hai cách so sánh quan trọng.

**`compareTo()`** so sánh **giá trị số học**:

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("1.2");

System.out.println(a.compareTo(b)); // 0
~~~

**`equals()`** đòi hỏi cả **giá trị số** và **scale** tương thích (cùng biểu diễn số theo định nghĩa của BigDecimal):

~~~java
System.out.println(a.equals(b)); // false
~~~

Bởi vậy, nếu nghiệp vụ chỉ muốn biết “cùng số tiền” hoặc “cùng giá trị” thì dùng `compareTo(b) == 0`. Nếu muốn nhận biết “cùng số và cùng scale” thì `equals()` đúng ý nghĩa hơn.

### HashCode và collection

`hashCode()` phải nhất quán với `equals()`, nhưng **không nhất thiết** nhất quán với `compareTo() == 0`. Vì vậy:

~~~java
java.util.Map<BigDecimal, String> map = new java.util.HashMap<>();
map.put(new BigDecimal("1.20"), "two decimal places");

System.out.println(map.get(new BigDecimal("1.20"))); // two decimal places
System.out.println(map.get(new BigDecimal("1.2")));  // null
~~~

Trong `HashMap`, hai khóa có cùng giá trị toán học nhưng khác scale **không được coi là cùng khóa** nếu `equals()` trả false.

Ngược lại, `TreeSet<BigDecimal>` dùng thứ tự tự nhiên dựa trên `compareTo()`, vì vậy có thể coi hai biểu diễn số là cùng một phần tử:

~~~java
java.util.Set<BigDecimal> sorted =
        new java.util.TreeSet<>();
sorted.add(new BigDecimal("1.20"));
sorted.add(new BigDecimal("1.2"));

System.out.println(sorted.size()); // 1
~~~

Đây là một điểm khác biệt thật sự giữa collection dựa vào `equals/hashCode` và collection có thứ tự. Nếu scale mang ý nghĩa nghiệp vụ, hãy cân nhắc rất kỹ trước khi dùng `TreeSet` như kho duy nhất của các bản ghi cần giữ biểu diễn riêng.

## 6. setScale: giữ hoặc áp đặt số chữ số sau dấu thập phân

`setScale(newScale)` trả về một `BigDecimal` có scale mới; **không thay đổi đối tượng nguồn**.

~~~java
BigDecimal original = new BigDecimal("1.2");
BigDecimal fixed = original.setScale(2);

System.out.println(original); // 1.2
System.out.println(fixed);    // 1.20
~~~

Vì chỉ thêm số 0, giá trị toán học không đổi và không cần làm tròn.

Tương tự:

~~~java
BigDecimal twoPlaces = new BigDecimal("1.20");
BigDecimal threePlaces = twoPlaces.setScale(3);

System.out.println(threePlaces); // 1.200
~~~

Khi **giảm scale**, có thể phải bỏ chữ số khác 0. Nếu gọi `setScale(int)` không chỉ định rounding mode và thao tác không thể chính xác, Java ném `ArithmeticException`:

~~~java
BigDecimal value = new BigDecimal("1.234");

// value.setScale(2); // ArithmeticException: cần loại chữ số khác 0.
~~~

Nếu quy định nghiệp vụ cho phép làm tròn, hãy chỉ rõ chế độ:

~~~java
BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
System.out.println(rounded); // 1.23
~~~

Đoạn này cần import `java.math.RoundingMode`. `setScale` không biến kết quả thành số kiểu `double`; kết quả vẫn là BigDecimal có thể giữ đúng số 0 cuối theo scale mong muốn.

## 7. Làm tròn không đồng nghĩa chỉ thêm số 0

Cần tách hai trường hợp:

| Thao tác | Giá trị trước | Scale đích | Kết quả | Có làm tròn giá trị? |
| --- | --- | ---: | --- | --- |
| Tăng scale | `1.2` | 2 | `1.20` | Không |
| Tăng scale | `1.20` | 3 | `1.200` | Không |
| Giảm scale khi chữ số bỏ đều là 0 | `1.200` | 2 | `1.20` | Không |
| Giảm scale khi phải bỏ chữ số khác 0 | `1.235` | 2 với `HALF_UP` | `1.24` | Có |
| Giảm scale, yêu cầu chính xác | `1.235` | 2 với `UNNECESSARY` | Exception | Không chấp nhận làm tròn |

~~~java
BigDecimal a = new BigDecimal("1.200");
BigDecimal b = new BigDecimal("1.235");

System.out.println(a.setScale(2, RoundingMode.UNNECESSARY)); // 1.20
System.out.println(b.setScale(2, RoundingMode.HALF_UP));     // 1.24
~~~

**Khuyến nghị:** nếu yêu cầu đầu vào không được có hơn hai chữ số thập phân có nghĩa, dùng `setScale(2, RoundingMode.UNNECESSARY)` hoặc kiểm tra scale/grammar theo quy định. Nếu số chữ số gốc (kể cả zero) phải được giữ, không dùng `setScale(2)` một cách vô điều kiện vì như vậy có thể xóa thông tin độ phân giải.

Ví dụ, `1.200` xuống `1.20` không đổi giá trị toán học nhưng **đã thay đổi biểu diễn gốc**.

## 8. stripTrailingZeros: hữu ích khi chuẩn hóa, không phù hợp nếu phải giữ biểu diễn

`stripTrailingZeros()` tạo một biểu diễn tương đương về giá trị nhưng bỏ những số 0 cuối dư thừa trong hệ thập phân.

~~~java
BigDecimal original = new BigDecimal("1.2000");
BigDecimal stripped = original.stripTrailingZeros();

System.out.println(original); // 1.2000
System.out.println(stripped); // 1.2
~~~

Kết quả có cùng giá trị số học, nhưng scale mới có thể nhỏ hơn. Nếu các số 0 cuối thể hiện chuẩn báo cáo, **không gọi phương thức này trước khi lưu hoặc xuất dữ liệu**.

### Trường hợp 1000.00: có thể chuyển sang ký hiệu khoa học

~~~java
BigDecimal original = new BigDecimal("1000.00");
BigDecimal stripped = original.stripTrailingZeros();

System.out.println(original);                 // 1000.00
System.out.println(stripped);                 // 1E+3
System.out.println(stripped.scale());         // -3
System.out.println(stripped.toPlainString()); // 1000
~~~

Đây là kết quả quan trọng: bỏ số 0 cuối **không nhất thiết** tạo ra một chuỗi giống văn bản ban đầu nhưng ngắn hơn. `BigDecimal` cho phép scale âm, và `toString()` có thể dùng ký hiệu mũ.

### Zero cũng có thể mất scale

~~~java
BigDecimal measuredZero = new BigDecimal("0.000");
BigDecimal strippedZero = measuredZero.stripTrailingZeros();

System.out.println(measuredZero);       // 0.000
System.out.println(strippedZero);       // 0
System.out.println(strippedZero.scale()); // 0
~~~

Đối với một phép đo được ghi ở ba chữ số thập phân, `0.000` và `0` cùng giá trị toán học nhưng không cùng thông tin hiển thị. Bởi vậy không chuẩn hóa mọi BigDecimal bằng `stripTrailingZeros()` nếu nghiệp vụ dựa vào scale.

## 9. toString, toPlainString và toEngineeringString

Cùng một BigDecimal có thể được biểu diễn bằng các định dạng văn bản khác nhau.

- `toString()` tạo biểu diễn theo quy tắc chuẩn của API và có thể dùng ký hiệu khoa học tùy scale và adjusted exponent.
- `toPlainString()` xuất theo dạng thập phân không sử dụng ký hiệu mũ.
- `toEngineeringString()` khi cần ký hiệu mũ thì dùng số mũ là bội số của 3, phù hợp một số bối cảnh kỹ thuật.

Ví dụ an toàn:

~~~java
BigDecimal thousand = new BigDecimal("1000.00")
        .stripTrailingZeros();

System.out.println(thousand.toString());      // 1E+3
System.out.println(thousand.toPlainString()); // 1000
~~~

**Không suy rằng `toPlainString()` khôi phục được số 0 cuối đã bỏ.** Nó chỉ đổi cách in của **BigDecimal hiện tại**. Một khi đã `stripTrailingZeros()`, chuỗi `"1000.00"` không còn được phục hồi chỉ bằng `toPlainString()`.

Ngược lại, với một BigDecimal giữ scale dương thông thường như `new BigDecimal("1.20")`, cả `toString()` và `toPlainString()` đều cho `"1.20"`.

**Cảnh báo tài nguyên:** đối với số có scale rất lớn về độ lớn tuyệt đối, `toPlainString()` có thể tạo chuỗi rất dài. Không dùng nó tùy tiện cho dữ liệu không được giới hạn kích thước.

## 10. Các phép toán ảnh hưởng scale như thế nào?

`BigDecimal` có quy tắc riêng cho scale của kết quả. Vì vậy, dù **đầu vào giữ số 0 cuối**, kết quả sau phép tính có thể có scale khác.

### Cộng/trừ: thường chọn scale lớn hơn

Với phép cộng hoặc trừ chính xác không có `MathContext` làm tròn, preferred scale là:

\[
\text{scale(result)}=\max(\text{scale}(a),\text{scale}(b)).
\]

~~~java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("2.30");

System.out.println(a.add(b));      // 3.50
System.out.println(a.subtract(b)); // -1.10
~~~

Số 0 cuối trong kết quả được giữ phù hợp với scale lớn nhất của hai số.

### Nhân: scale thường cộng lại

Với phép nhân chính xác:

\[
\text{scale(result)}=\text{scale}(a)+\text{scale}(b)
\]

~~~java
BigDecimal length = new BigDecimal("1.20");
BigDecimal factor = new BigDecimal("2.0");

System.out.println(length.multiply(factor)); // 2.400
~~~

Giá trị số là 2,4 nhưng scale kết quả là 3, bởi `2 + 1 = 3`. Tuy nhiên, **không tự suy scale kết quả chính là độ chính xác phép đo thực tế**. Trong đo lường, phép nhân thường có quy tắc số chữ số có nghĩa riêng và cần biết bất định đầu vào.

### Chia: có nhiều trường hợp hơn

`divide(BigDecimal divisor)` không tự động làm tròn khi kết quả decimal vô hạn. Nó trả kết quả chính xác nếu biểu diễn hữu hạn, hoặc ném `ArithmeticException` nếu không thể biểu diễn chính xác.

~~~java
BigDecimal amount = new BigDecimal("1.20");

System.out.println(amount.divide(new BigDecimal("2"))); // 0.60
// amount.divide(new BigDecimal("7")); // ArithmeticException
~~~

Với phép chia chính xác, preferred scale bắt đầu từ `scale(a) - scale(b)`, nhưng có thể cần điều chỉnh scale để biểu diễn đúng kết quả. Khi chọn overload nhận scale và `RoundingMode` hoặc `MathContext`, quy tắc scale/precision phụ thuộc overload và làm tròn.

### Không dựa vào scale tự nhiên để quyết định số chữ số đầu ra

Sau các phép toán, nếu hợp đồng yêu cầu **luôn hai chữ số thập phân**, hãy áp dụng chính sách rõ ràng tại ranh giới dữ liệu:

~~~java
BigDecimal result = new BigDecimal("1.20")
        .multiply(new BigDecimal("2.0"));

System.out.println(result); // 2.400

BigDecimal displayed = result.setScale(2, RoundingMode.UNNECESSARY);
System.out.println(displayed); // 2.40
~~~

Đây là trường hợp loại một chữ số 0, không làm tròn giá trị. Nếu kết quả có chữ số khác 0 sau hai chữ số thập phân, `UNNECESSARY` sẽ từ chối, giúp phát hiện quy tắc dữ liệu bị vi phạm.

## 11. Chương trình Java 17+ đầy đủ

Tạo file `BigDecimalTrailingZerosDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class BigDecimalTrailingZerosDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.20");
        BigDecimal b = new BigDecimal("1.2");

        System.out.println("a=" + a
                + " scale=" + a.scale()
                + " precision=" + a.precision());
        System.out.println("b=" + b
                + " scale=" + b.scale()
                + " precision=" + b.precision());
        System.out.println("unscaled=" + a.unscaledValue());
        System.out.println("numeric equal=" + (a.compareTo(b) == 0));
        System.out.println("object equal=" + a.equals(b));

        System.out.println("scale up=" + b.setScale(2));
        System.out.println("trim=" + a.stripTrailingZeros());
        System.out.println("sum=" + a.add(new BigDecimal("2.30")));
        System.out.println("product=" + a.multiply(new BigDecimal("2.0")));
        System.out.println("quotient=" + a.divide(new BigDecimal("2")));

        BigDecimal thousand = new BigDecimal("1000.00")
                .stripTrailingZeros();
        System.out.println("thousands=" + thousand
                + " scale=" + thousand.scale()
                + " plain=" + thousand.toPlainString());

        BigDecimal zero = new BigDecimal("0.000")
                .stripTrailingZeros();
        System.out.println("zero=" + zero + " scale=" + zero.scale());

        BigDecimal rounded = new BigDecimal("1.235")
                .setScale(2, RoundingMode.HALF_UP);
        System.out.println("rounded=" + rounded);

        System.out.println("from double=" + BigDecimal.valueOf(1.20));
        System.out.println("negative zero=" + new BigDecimal("-0.00"));

        Map<BigDecimal, String> map = new HashMap<>();
        map.put(a, "two places");
        System.out.println("hash map same scale=" + map.get(a));
        System.out.println("hash map different scale=" + map.get(b));

        Set<BigDecimal> set = new TreeSet<>();
        set.add(a);
        set.add(b);
        System.out.println("tree set size=" + set.size());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 BigDecimalTrailingZerosDemo.java
java BigDecimalTrailingZerosDemo
~~~

Stdout (đã đối chiếu các phép toán với JDK):

~~~text
a=1.20 scale=2 precision=3
b=1.2 scale=1 precision=2
unscaled=120
numeric equal=true
object equal=false
scale up=1.20
trim=1.2
sum=3.50
product=2.400
quotient=0.60
thousands=1E+3 scale=-3 plain=1000
zero=0 scale=0
rounded=1.24
from double=1.2
negative zero=0.00
hash map same scale=two places
hash map different scale=null
tree set size=1
~~~

Giải thích:

1. Hai số có cùng giá trị nhưng khác `scale` và `precision`; `compareTo` cho 0, còn `equals` cho false.
2. `setScale(2)` thêm chữ số 0 mà không đổi giá trị; `stripTrailingZeros` bỏ số 0 cuối.
3. Phép cộng/nhân/chia chính xác giữ scale theo quy tắc số học BigDecimal, không theo một tiêu chuẩn hiển thị chung.
4. `1000.00` sau khi strip thành một số có scale âm, in là `1E+3` nhưng `toPlainString` là `1000`.
5. `0.000` sau khi strip thành `0` với scale 0; biểu diễn gốc không còn.
6. `HALF_UP` tại `1.235` xuống hai chữ số cho `1.24`.
7. `BigDecimal.valueOf(1.20)` không thể biết literal nguồn đã viết thêm zero.
8. `BigDecimal("-0.00")` biểu diễn zero với scale 2; dấu âm của zero **không được bảo toàn** như một thuộc tính giá trị BigDecimal.
9. `HashMap` và `TreeSet` có thể xem hai biểu diễn cùng số là khác hoặc giống nhau theo hợp đồng equality/ordering khác nhau.

## 12. Số 0 có dấu và trailing zeros là hai khái niệm khác nhau

`BigDecimal` lưu giá trị số nguyên không có dạng signed zero riêng như `double`. Vì vậy:

~~~java
BigDecimal negativeZero = new BigDecimal("-0.00");
BigDecimal positiveZero = new BigDecimal("0.00");

System.out.println(negativeZero);                    // 0.00
System.out.println(negativeZero.equals(positiveZero)); // true
~~~

**Scale vẫn được giữ**, nhưng dấu âm của số 0 trong **văn bản gốc** không còn là một thuộc tính số học của BigDecimal. Nếu một ứng dụng khoa học thật sự cần biết người dùng đã nhập `"-0.00"` thay vì `"0.00"`, hãy lưu văn bản gốc hoặc metadata riêng.

Không nhầm điều này với `-0.0` của IEEE 754 binary floating point, vốn có bit dấu. `BigDecimal` không sử dụng mô hình giá trị số thực binary64 để lưu dấu zero.

## 13. Chính sách nhập liệu: giữ nguyên scale hay chuẩn hóa?

Bạn cần xác định yêu cầu trước khi viết parser.

**Phương án A — giữ scale gốc:** sử dụng `new BigDecimal(text)` rồi giữ đối tượng không strip. Phù hợp khi số chữ số sau dấu thập phân là một phần thông tin cần lưu.

**Phương án B — chuẩn hóa tất cả về 2 chữ số:** dùng `setScale(2, mode)`, có thể chọn `RoundingMode.UNNECESSARY` để từ chối dữ liệu không thể biểu diễn chính xác với hai chữ số.

**Phương án C — chỉ giữ giá trị toán học:** dùng so sánh số học hoặc chuẩn hóa có chủ đích; không cam kết khôi phục lại chuỗi đầu vào.

Một phương thức validation theo chính sách **hai chữ số có thể thêm zero nhưng không làm tròn**:

~~~java
static BigDecimal requireTwoDecimalPlaces(String input) {
    if (input == null) {
        throw new IllegalArgumentException("Missing amount");
    }

    final BigDecimal value;
    try {
        value = new BigDecimal(input);
    } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("Malformed decimal amount", ex);
    }

    try {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException ex) {
        throw new IllegalArgumentException("More than two significant fractional digits", ex);
    }
}
~~~

Đây là phương thức đặt trong một lớp có import `BigDecimal` và `RoundingMode`. Nó nhận `"1.2"` và biến thành `1.20`, nhận `"1.200"` và trả `1.20`, nhưng từ chối `"1.234"`. Nó **không bảo toàn số lượng zero nguyên gốc**; nó chỉ bảo đảm kết quả có scale = 2.

Nếu hợp đồng đòi **đầu vào phải được viết đúng hai chữ số sau dấu chấm**, kể cả `"1.2"` cũng phải bị từ chối bằng một bước kiểm tra **grammar/scale gốc** trước khi chuẩn hóa. Đây là hai yêu cầu khác nhau.

## 14. Kiểm tra scale đầu vào khi zero cuối phải có ý nghĩa

Nếu quy định nhập liệu “phải đúng 2 chữ số thập phân”, có thể kiểm tra scale của BigDecimal nhận từ chuỗi:

~~~java
BigDecimal amount = new BigDecimal("1.20");

if (amount.scale() != 2) {
    throw new IllegalArgumentException("Expected scale 2");
}
~~~

Nhưng kiểm tra scale **không thay thế kiểm tra cú pháp gốc**:

- `new BigDecimal("1.20")` có scale 2.
- `new BigDecimal("1.2E+0")` có scale 1.
- `new BigDecimal("120E-2")` có scale 2 và cùng giá trị 1,20, nhưng cách viết khác `"1.20"`.
- `new BigDecimal("001.20")` có cùng giá trị và scale với `"1.20"` nhưng giữ zero đầu nếu cần là vấn đề **của chuỗi**, không phải của BigDecimal.

Nếu hệ thống yêu cầu **dạng chuỗi chuẩn duy nhất**, hãy xác thực raw input theo grammar định nghĩa rõ (ví dụ không có exponent, dấu thập phân là `.`, đúng hai chữ số phần lẻ), sau đó parse thành BigDecimal. Nếu cần giữ nguyên ký tự người nhập, lưu thêm raw input.

**Đừng suy rằng scale là thước đo đáng tin của bất định phép đo.** Khi cần thông tin về sai số, số chữ số có nghĩa đo lường hay độ phân giải, hãy biểu diễn chúng rõ ràng trong mô hình nghiệp vụ.

## 15. Định dạng hiển thị khác với thay đổi dữ liệu

Nếu dữ liệu nội bộ được lưu dưới dạng `BigDecimal("1.2")` mà màn hình phải hiển thị `"1.20"`, có hai hướng:

1. **Chuẩn hóa giá trị xuất:** `value.setScale(2, RoundingMode.UNNECESSARY).toPlainString()`, khi chính sách yêu cầu đúng 2 chữ số và cấm làm tròn.
2. **Định dạng bằng formatter:** dùng `NumberFormat`/`DecimalFormat` với locale và số chữ số fraction cố định nếu cần hiển thị theo khu vực.

~~~java
BigDecimal value = new BigDecimal("1.2");
String output = value.setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString();

System.out.println(output); // 1.20
~~~

Không nhầm `toString()` / `toPlainString()` với formatter theo locale. Chúng dùng cú pháp số thập phân Java, không tự thay dấu thập phân thành dấu phẩy cho tiếng Việt/Đức.

Khi cần định dạng tiền theo locale, `NumberFormat.getCurrencyInstance(locale)` có thể áp dụng grouping, biểu tượng tiền và quy tắc số chữ số lẻ của đồng tiền. Việc dùng formatter có thể **khác với giữ nguyên biểu diễn đầu vào**; hãy lựa chọn theo mục tiêu.

### Đừng “giữ zero” bằng String.format trên double

~~~java
// Cách này chỉ tạo chuỗi định dạng, không bảo toàn scale gốc:
String display = String.format(java.util.Locale.US, "%.2f", 1.2);
System.out.println(display); // 1.20
~~~

Dòng code cho phép hiển thị hai chữ số sau dấu thập phân, nhưng giá trị gốc là `double` và không lưu thông tin về số chữ số đã nhập. Nếu phải bảo toàn scale qua nhiều bước nghiệp vụ, dùng BigDecimal hoặc metadata tương ứng.

## 16. JSON, database, CSV: mất số 0 cuối ở đâu?

Việc lưu BigDecimal trong RAM không đảm bảo **mỗi bước truyền dữ liệu** sẽ giữ scale.

### JSON

Một JSON number có thể được viết như:

~~~json
{"amount": 1.20}
~~~

Nhưng nhiều parser/serializer xem trường number là **giá trị số** và có thể chuyển qua kiểu floating point, chuẩn hóa hoặc xuất `1.2`. JSON không buộc mọi phần mềm phải giữ đúng biểu diễn lexical ban đầu của number.

Nếu định dạng có ý nghĩa bắt buộc, có thể thiết kế API lưu decimal như **chuỗi**:

~~~json
{"amount": "1.20"}
~~~

Cần ghi rõ trong schema/API rằng trường này là decimal string được validation, chứ không phải tùy tiện đổi number thành text trong từng hệ thống. Nếu khách hàng yêu cầu số JSON thay vì chuỗi, hãy kiểm thử serializer cụ thể với `BigDecimal` và định nghĩa rõ cam kết round-trip về scale.

### JDBC và cột DECIMAL

Khi sử dụng `DECIMAL(p, s)` hoặc `NUMERIC(p, s)` trong cơ sở dữ liệu, cột có thể có scale được cấu hình. DBMS, JDBC driver và mapping ORM có thể **chuẩn hóa giá trị về scale của cột** hoặc áp dụng quy tắc lưu khác.

- Ghi `1.2` vào cột scale 2 có thể đọc lại thành `1.20`.
- Ghi `1.200` vào cột scale 2 có thể bị chuẩn hóa/điều chỉnh theo quy tắc DBMS.
- Nếu cần giữ **scale nhập ban đầu thay đổi theo từng bản ghi**, cột decimal có scale cố định có thể không đủ; cân nhắc lưu metadata hoặc văn bản gốc.

Không khẳng định mọi database tự bảo toàn dấu 0 cuối nguyên văn. Phải kiểm tra schema, driver, dữ liệu đọc lại và yêu cầu nghiệp vụ.

### CSV và file văn bản

Với CSV, nếu writer xuất `BigDecimal.toPlainString()` từ đối tượng còn giữ scale thích hợp, chuỗi `1.20` có thể được giữ. Nhưng nếu bước nhập đã parse thành `double` rồi tạo BigDecimal từ giá trị đó, thông tin zero cuối có thể đã biến mất.

**Quy tắc round-trip:** xác nhận trạng thái scale tại từng ranh giới — input → parse → tính toán → lưu → đọc → serialize → hiển thị.

## 17. Hàm xuất giá trị chính xác với scale cố định

Một helper dễ kiểm thử cho kết quả decimal có đúng scale:

~~~java
static String toFixedScale(BigDecimal value, int scale) {
    if (value == null || scale < 0) {
        throw new IllegalArgumentException("Invalid value or scale");
    }
    return value.setScale(scale, RoundingMode.UNNECESSARY)
            .toPlainString();
}
~~~

Ví dụ:

~~~java
System.out.println(toFixedScale(new BigDecimal("10"), 2));    // 10.00
System.out.println(toFixedScale(new BigDecimal("10.50"), 2)); // 10.50
// toFixedScale(new BigDecimal("10.501"), 2); // ArithmeticException
~~~

**Giới hạn:** tham số scale và kích thước output phải nằm trong ngân sách ứng dụng. `setScale(1_000_000)` có thể tạo giá trị/chuỗi rất lớn hoặc tốn tài nguyên, dù giá trị số không đổi. Việc cố tình chỉ cho scale không âm là **chính sách của helper**, không phải giới hạn của BigDecimal, vốn cho phép scale âm.

Nếu yêu cầu làm tròn giá trị cho phép, có thể dùng overload helper nhận `RoundingMode`. Tuy nhiên phải ghi rõ chính sách, vì `HALF_EVEN`, `HALF_UP`, `DOWN` ... cho kết quả khác ở các điểm giữa.

## 18. Chuẩn hóa cho so sánh và canonicalization

Một số hệ thống muốn coi `1.20`, `1.200` và `1.2` như một giá trị duy nhất để tạo khóa hoặc so sánh. Khi **scale không thuộc khóa nghiệp vụ**, có thể dùng:

~~~java
BigDecimal canonical = new BigDecimal("1.200")
        .stripTrailingZeros();
System.out.println(canonical); // 1.2
~~~

Nhưng hãy nhớ `1000.00` sau strip có thể thành `1E+3`. Khi tạo textual canonical key, có thể cân nhắc `stripTrailingZeros().toPlainString()`, cùng ngân sách độ dài và quy tắc riêng cho zero.

Nếu hệ thống cần phân biệt mức chi tiết được ghi, canonicalization này **không đúng**. Hai bản ghi `"1.2"` và `"1.20"` phải được giữ riêng ở tầng dữ liệu thích hợp.

Với `HashMap<BigDecimal, ...>`, bạn có thể chuẩn hóa cả lúc ghi và đọc nếu thật sự cần tra cứu theo giá trị số. Với `TreeMap`/`TreeSet`, hãy hiểu `compareTo` không phân biệt scale, nên chính lựa chọn kiểu collection đã ảnh hưởng cách định danh phần tử.

## 19. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Tạo từ `new BigDecimal(1.20)` để giữ `"1.20"` | Literal đã là double; số 0 cuối bị mất, có thể thêm sai số binary | `new BigDecimal("1.20")` |
| Dùng `BigDecimal.valueOf(1.20)` để giữ độ dài fraction gốc | `Double.toString` không lưu cách viết literal | Parse từ String |
| Gọi `stripTrailingZeros` trước khi lưu mọi số | Mất scale, có thể chuyển thành `1E+3` | Chỉ strip khi mục tiêu là canonicalization |
| Tin `toPlainString` phục hồi `1000.00` sau strip | Scale đã thay đổi | Giữ đối tượng gốc hoặc metadata |
| Dùng `equals` để so **chỉ giá trị số** | `1.2` khác `1.20` | `compareTo(b) == 0` |
| Dùng `TreeSet<BigDecimal>` khi cần hai scale như hai bản ghi khác nhau | `compareTo` coi giá trị bằng nhau | Chọn key/record có scale rõ |
| Dùng `HashMap<BigDecimal,...>` nhưng truy vấn với scale khác | `equals` khác, get có thể null | Thống nhất scale hoặc canonical key |
| Chỉ kiểm tra `precision()` khi cần 2 chữ số phần lẻ | Precision khác scale | Kiểm tra `scale()` và grammar |
| Gọi `setScale(2)` với số có chữ số lẻ khác 0 bị loại | Ném `ArithmeticException` | Chọn rounding policy hoặc reject tường minh |
| Làm tròn nhiều bước mà không có chính sách | Kết quả thay đổi, sai số tích lũy | Chọn ranh giới rounding rõ ràng |
| Tin JSON number luôn giữ `1.20` | Serializer có thể chuẩn hóa | Kiểm tra round-trip hoặc dùng decimal string |
| Tin cột DECIMAL tự giữ scale nhập gốc | DB schema có thể cố định scale | Lưu thêm scale/raw input khi cần |
| Nghĩ số 0 cuối tự chứng minh sai số đo | Thiếu metadata bất định/đơn vị | Lưu độ phân giải, sai số và đơn vị riêng |
| Tin `BigDecimal("-0.00")` giữ dấu âm | BigDecimal biểu diễn số 0 không có sign âm riêng | Giữ raw text nếu cần |
| Dùng `toPlainString` với scale không giới hạn | Có thể tạo output cực lớn | Giới hạn scale và kích thước chuỗi |

## 20. Câu hỏi tự kiểm tra

**1.** `new BigDecimal("1.20").scale()` và `precision()` là bao nhiêu?

**Đáp án:** Scale 2, precision 3; unscaled value là 120.

**2.** `new BigDecimal("1.20").equals(new BigDecimal("1.2"))` có true không?

**Đáp án:** Không, vì khác scale. `compareTo()` của chúng trả 0 vì cùng giá trị toán học.

**3.** Vì sao `stripTrailingZeros()` có thể không phù hợp với dữ liệu đo được ghi là `"0.000"`?

**Đáp án:** Nó tạo BigDecimal tương đương với zero có scale 0 và khi in thành `"0"`, làm mất độ phân giải đã thể hiện trong biểu diễn gốc.

**4.** `new BigDecimal("1000.00").stripTrailingZeros().toString()` có thể hiển thị gì?

**Đáp án:** `1E+3`. Sau strip, scale là −3; `toPlainString()` là `"1000"`, không phải `"1000.00"`.

**5.** `new BigDecimal("1.20").multiply(new BigDecimal("2.0"))` in gì?

**Đáp án:** `2.400` vì scale kết quả của phép nhân chính xác là tổng scale 2+1 = 3.

**6.** Muốn xuất `1.2` thành chuỗi đúng hai chữ số sau dấu thập phân, không làm tròn giá trị, dùng gì?

**Đáp án:** `new BigDecimal("1.2").setScale(2, RoundingMode.UNNECESSARY).toPlainString()` → `"1.20"`.

**7.** Nếu yêu cầu phải lưu *chính xác từng ký tự* của `"+001.20"`, BigDecimal có đủ không?

**Đáp án:** Không. BigDecimal giữ giá trị thập phân cùng scale, không giữ mọi ký tự lexical như dấu cộng hoặc zero đầu. Cần lưu thêm text gốc.

**8.** Vì sao `new BigDecimal("1.235").setScale(2, RoundingMode.UNNECESSARY)` thất bại?

**Đáp án:** Bỏ chữ số 5 khác 0 sẽ làm thay đổi giá trị. `UNNECESSARY` yêu cầu phép thay đổi scale phải chính xác; nếu cần làm tròn, chọn một `RoundingMode` được nghiệp vụ chấp nhận.

## 21. Tóm tắt và tài liệu tham khảo

- `BigDecimal` biểu diễn số bằng **unscaled value × 10^(-scale)**. **Scale** giúp bảo toàn số 0 cuối sau dấu thập phân khi tạo trực tiếp từ chuỗi.
- **Giá trị số học bằng nhau** không có nghĩa `equals()` bằng nhau: `compareTo() == 0` chỉ so giá trị; `equals()` còn xét scale.
- `setScale()` có thể **thêm zero**, **loại zero** hoặc **làm tròn**, tùy scale đích và rounding mode.
- **`stripTrailingZeros()` chủ động bỏ thông tin biểu diễn**: `1.200 → 1.2`, `1000.00 → 1E+3` và `0.000 → 0`.
- Phép cộng, nhân, chia có quy tắc scale; **đầu ra nên có chính sách format/scale riêng** nếu yêu cầu đồng nhất.
- **Không đi qua double** để giữ số 0 cuối của dữ liệu decimal. Nếu cần giữ mọi ký tự nguyên gốc hoặc mức bất định phép đo, lưu metadata/văn bản nguồn bên cạnh BigDecimal.
- Kiểm tra round-trip qua JSON, database, CSV và formatter khi scale mang ý nghĩa nghiệp vụ.

Tài liệu chính thức (Java 17):

- [BigDecimal API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `scale()`, `unscaledValue()`, `precision()`, `equals()`, `compareTo()`, `setScale()`, `stripTrailingZeros()`, `toString()` và `toPlainString()`.
- [RoundingMode API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): lựa chọn quy tắc làm tròn decimal.
- [BigDecimal — preferred scales for operations](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): quy tắc scale khi cộng, trừ, nhân và chia chính xác.
- [NumberFormat API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html): hiển thị theo locale, giới hạn chữ số phần thập phân.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-009">BigDecimal không có NaN hoặc vô cực</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal không có NaN hoặc vô cực trong Java

`BigDecimal` và `double` đều biểu diễn số, nhưng **không có cùng tập giá trị**. Các kiểu dấu phẩy động nhị phân `double`/`float` theo IEEE 754 hỗ trợ ba loại giá trị đặc biệt: **NaN** (*Not a Number*), **dương vô cực** (`+Infinity`) và **âm vô cực** (`-Infinity`). `java.math.BigDecimal` **không có bất kỳ giá trị nào trong ba loại đó**; nó dùng số nguyên unscaled và scale để biểu diễn các **giá trị thập phân hữu hạn**, với miền độ lớn thực tế bị giới hạn bởi tài nguyên và kiểu `int` của scale.

Hậu quả quan trọng:

- Không tạo được một `BigDecimal` “chứa NaN” hoặc “chứa Infinity” bằng constructor hay `valueOf(double)`.
- Các phép `BigDecimal` không dùng vô cực để biểu diễn phép chia cho zero; chúng **ném `ArithmeticException`**.
- `BigDecimal` rất lớn/nhỏ vẫn hữu hạn trong mô hình decimal, nhưng khi gọi `doubleValue()` có thể bị chuyển thành `Infinity` hoặc `0.0` do giới hạn của binary64.
- Khi một API dùng `double` giao dữ liệu cho tầng `BigDecimal`, phải **xác thực số hữu hạn trước khi chuyển** và chọn chính sách lỗi rõ ràng.

Bài Knowledge thuộc nhóm **Mô hình BigDecimal** của Java Core, trình bày cơ chế và ví dụ Java 17+ kiểm chứng từng ranh giới.

## 1. Tập giá trị: BigDecimal so với double

Một đối tượng `BigDecimal` được mô tả bằng cặp `unscaledValue`, `scale`, với công thức:

~~~text
value = unscaledValue × 10^(-scale)
~~~

Trong đó `unscaledValue` là một số nguyên `BigInteger` và `scale` là số nguyên `int`. Với một cặp hợp lệ và có thể xây được, kết quả là một số thập phân hữu hạn; **không có mã đặc biệt để biểu diễn NaN hoặc vô cực**.

| Thuộc tính | `double` / `float` | `BigDecimal` |
| --- | --- | --- |
| Biểu diễn | IEEE 754 binary64/binary32 | Decimal qua unscaled integer và scale |
| NaN | Có | **Không** |
| `+Infinity`, `-Infinity` | Có | **Không** |
| Số 0 có dấu `+0.0`/`-0.0` | Phân biệt được dấu ở biểu diễn bit | **Không giữ riêng negative zero** |
| Số hữu hạn rất lớn | Có giới hạn exponent và độ chính xác nhị phân | Không có giới hạn exponent kiểu binary64; vẫn chịu giới hạn scale `int` và bộ nhớ |
| `1.0 / 0.0` | `Infinity` | `divide(BigDecimal.ZERO)` ném `ArithmeticException` |
| `0.0 / 0.0` | `NaN` | `BigDecimal.ZERO.divide(BigDecimal.ZERO)` ném `ArithmeticException` |
| Giá trị 1/3 chính xác ở decimal hữu hạn | Không biểu diễn chính xác | `divide` không rounding ném lỗi; có thể tính xấp xỉ với chính sách |
| Kiểu dữ liệu kết quả | Primitive `double` hoặc `float` | Object `BigDecimal` bất biến |

Không thể suy ra rằng “BigDecimal chính xác hơn” nghĩa là nó hỗ trợ mọi trạng thái của IEEE 754. **Độ chính xác và tập giá trị là hai vấn đề khác nhau.** `BigDecimal` thường thích hợp với số tiền và decimal cố định; `double` thích hợp nhiều phép đo và tính toán số học cần dải exponent rộng, đồng thời mang các giá trị đặc biệt.

## 2. NaN và Infinity có thể xuất hiện trong double như thế nào?

Trong Java, các biểu thức dấu phẩy động có thể tạo ra giá trị không hữu hạn:

~~~java
double nan = 0.0 / 0.0;         // NaN
double positive = 1.0 / 0.0;    // Infinity
double negative = -1.0 / 0.0;   // -Infinity

System.out.println(Double.isNaN(nan));       // true
System.out.println(Double.isInfinite(positive)); // true
System.out.println(Double.isFinite(negative));   // false
~~~

NaN còn có thể xuất hiện từ `Math.sqrt(-1.0)` hoặc `Infinity - Infinity`. Infinity có thể xuất hiện khi phép tính `double` bị overflow. Đây là những kết quả hợp lệ theo ngữ nghĩa dấu phẩy động, **không nhất thiết là ngoại lệ Java được ném**.

Ba công cụ nên nhớ:

- `Double.isNaN(x)`: xác định NaN, thay vì `x == Double.NaN` (luôn false với NaN).
- `Double.isInfinite(x)`: xác định dương hoặc âm vô cực.
- `Double.isFinite(x)`: true nếu và chỉ nếu không phải NaN/vô cực.

Có API tương ứng trên `Float`. `Double.isFinite(-0.0)` vẫn là true: số 0, dù có dấu âm trong binary64, là một giá trị **hữu hạn**.

Không dùng `x != x` thay `isNaN` trong mã nghiệp vụ nếu có API trực tiếp; cách đó khó đọc và dễ khiến người bảo trì bỏ lỡ ý định xác thực.

## 3. Tạo BigDecimal từ NaN hoặc Infinity sẽ lỗi

Các cách tạo đối tượng không thể biến trạng thái không hữu hạn thành một giá trị `BigDecimal`:

~~~java
// Mỗi lời gọi sau ném NumberFormatException:
new BigDecimal(Double.NaN);
new BigDecimal(Double.POSITIVE_INFINITY);
BigDecimal.valueOf(Double.NaN);
BigDecimal.valueOf(Double.NEGATIVE_INFINITY);
new BigDecimal("NaN");
new BigDecimal("Infinity");
~~~

**`NumberFormatException`** trong các trường hợp này thể hiện rằng đầu vào không thể là một biểu diễn `BigDecimal` hợp lệ. Constructor `BigDecimal(double)` không chấp nhận NaN/Infinity; `BigDecimal.valueOf(double)` dùng `Double.toString(double)`, nhưng dạng chuỗi `"NaN"` hoặc `"Infinity"` cũng không được constructor chuỗi của `BigDecimal` chấp nhận.

Điểm phân biệt quan trọng:

~~~java
Double.parseDouble("NaN");         // Double.NaN
Double.parseDouble("Infinity");    // Double.POSITIVE_INFINITY

// new BigDecimal("NaN");      // NumberFormatException
// new BigDecimal("Infinity"); // NumberFormatException
~~~

**Cùng một văn bản không nhất thiết được hai parser chấp nhận.** `Double.parseDouble` phục vụ kiểu `double` với các giá trị đặc biệt; `BigDecimal(String)` yêu cầu cú pháp số thập phân hữu hạn hợp lệ. Không cố parse chuỗi đặc biệt thành decimal rồi giả định ngoại lệ đồng nghĩa giá trị bằng zero.

Cũng không phải `BigDecimal.valueOf(Double.MAX_VALUE)` là vô cực. `Double.MAX_VALUE` vẫn là giá trị **hữu hạn** (dù rất lớn) và có thể chuyển sang `BigDecimal`. Ngoại lệ chỉ xảy ra khi **giá trị double đầu vào đã là NaN hoặc ±Infinity** hoặc không thể chuyển đổi theo các giới hạn khác của API.

## 4. Chia cho zero: ArithmeticException thay vì Infinity hoặc NaN

So sánh phép chia cùng biểu thức toán học:

~~~java
double infinity = 1.0 / 0.0; // Infinity
double undefined = 0.0 / 0.0; // NaN

// BigDecimal.ONE.divide(BigDecimal.ZERO);  // ArithmeticException
// BigDecimal.ZERO.divide(BigDecimal.ZERO); // ArithmeticException
~~~

`BigDecimal` không có phần tử vô cực/NaN trong tập giá trị, nên không có một đối tượng hợp lệ để biểu diễn các kết quả ấy. Với phép chia cho zero, hành vi là **ném ngoại lệ**.

Với `BigDecimal.ZERO.divide(BigDecimal.ZERO)`, thông báo exception có thể khác trường hợp `BigDecimal.ONE.divide(BigDecimal.ZERO)`; không dùng `getMessage()` như một mã lỗi nghiệp vụ ổn định. Nên bắt theo **loại ngoại lệ** hoặc — tốt hơn — kiểm tra tiền điều kiện `divisor.signum() == 0` trước khi tính nếu cần thông báo dễ hiểu.

Ví dụ phương thức kiểm tra mẫu số:

~~~java
static BigDecimal safeDivide(
        BigDecimal numerator,
        BigDecimal denominator,
        int scale,
        RoundingMode mode) {
    if (denominator.signum() == 0) {
        throw new IllegalArgumentException("denominator must be nonzero");
    }
    return numerator.divide(denominator, scale, mode);
}
~~~

Đây là **phương thức minh họa trong một class**; caller cần kiểm tra null hoặc định nghĩa chính sách null phù hợp. Truyền `denominator = null` vào `denominator.signum()` vẫn sẽ ném `NullPointerException`; validation không thể tự động thay thế hợp đồng null.

**Một lỗi khác cùng lớp ngoại lệ:** phép chia **không có khai triển thập phân hữu hạn** cũng có thể ném `ArithmeticException` khi không chỉ định rounding. Ví dụ `BigDecimal.ONE.divide(new BigDecimal("3"))` không có nghiệm decimal hữu hạn; nó ném lỗi dù mẫu số khác zero.

~~~java
BigDecimal third = BigDecimal.ONE.divide(
        BigDecimal.valueOf(3),
        5,
        RoundingMode.HALF_UP);
// third = 0.33333
~~~

Đặt scale và rounding mode phù hợp sẽ xử lý **kết quả chia lặp vô hạn về thập phân**, **không** khiến phép chia cho zero trở nên hợp lệ.

## 5. BigDecimal cực lớn vẫn hữu hạn — nhưng double có thể là Infinity

Một `BigDecimal` có thể biểu diễn `10^400` bằng:

~~~java
BigDecimal huge = new BigDecimal("1E+400");
System.out.println(huge); // 1E+400
~~~

Nó có một unscaled value hữu hạn và scale âm, nên là số thập phân hợp lệ. Nhưng kiểu `double` chỉ có dải exponent hữu hạn. Khi chuyển:

~~~java
double narrowed = huge.doubleValue();
System.out.println(narrowed);                 // Infinity
System.out.println(Double.isFinite(narrowed)); // false
~~~

**Không có mâu thuẫn** với tiêu đề bài: đối tượng `BigDecimal` vẫn giữ giá trị hữu hạn `1E+400`; chỉ có **kết quả của phép chuyển kiểu sang `double`** là Infinity vì vượt phạm vi số hữu hạn binary64.

Ngược lại, một decimal cực nhỏ cũng có thể làm mất thông tin khi chuyển sang binary64:

~~~java
BigDecimal tiny = new BigDecimal("1E-400");
double narrowed = tiny.doubleValue();
System.out.println(narrowed); // 0.0
~~~

Ở đây `tiny` khác zero về toán học, nhưng sau narrowing thành `double` nó underflow về 0.0. Một kết quả `double` **hữu hạn** (ví dụ 0.0) chưa chắc giữ nguyên giá trị decimal đầu vào.

`BigDecimal.floatValue()` cũng có nguy cơ tương tự, với dải hữu hạn còn hẹp hơn. Khi hợp đồng cần giữ giá trị decimal chính xác, hãy **tiếp tục tính toán bằng BigDecimal** và xuất dưới dạng decimal thích hợp thay vì ép sang `float`/`double`.

Không có `doubleValueExact()` như `intValueExact()` cho số nguyên. Nếu cần kiểm tra conversion decimal→double, bạn phải đặt tiêu chí phù hợp cho phạm vi, độ chính xác được phép mất và các trạng thái không hữu hạn.

## 6. Không đồng nhất “giá trị hữu hạn” với “giá trị chính xác khi qua double”

Có ba tình huống độc lập cần phân biệt:

1. **`double` hữu hạn nhưng xấp xỉ giá trị decimal mong đợi**, ví dụ literal `0.1` là một biểu diễn binary64 gần 1/10.
2. **`BigDecimal` hữu hạn và chính xác như chuỗi decimal**, ví dụ `new BigDecimal("0.1")` giữ đúng 1/10.
3. **`BigDecimal` hữu hạn nhưng chuyển sang `double` bị overflow/underflow/làm tròn**, ví dụ `1E+400` thành Infinity hoặc `1E-400` thành 0.0.

~~~java
BigDecimal exact = new BigDecimal("0.1");
BigDecimal binaryExact = new BigDecimal(0.1);
BigDecimal canonical = BigDecimal.valueOf(0.1);
~~~

`exact` là 0.1 decimal chính xác; `binaryExact` giữ đúng giá trị binary64 của `double 0.1` nên có đuôi dài; `canonical` dựa trên dạng chuỗi round-trip của `Double.toString(0.1)`, thường là lựa chọn hợp lý khi dữ liệu bắt buộc đã đến dưới dạng `double` hữu hạn. Không có cách nào trong số đó “nâng” NaN hoặc Infinity thành một `BigDecimal` hợp lệ.

**Chú ý nguồn dữ liệu:** nếu một chuỗi đầu vào là `"1E+400"`, `new BigDecimal("1E+400")` biểu diễn số hữu hạn lớn trực tiếp. Nếu parse nó thành `double` trước, `Double.parseDouble("1E+400")` có thể trả Infinity, và bạn đã mất cơ hội chuyển tiếp giá trị decimal chính xác đó qua pipeline `double`.

## 7. Thiết kế ranh giới API an toàn

Khi một đầu vào `double` được phép chuyển sang `BigDecimal` **chỉ khi hữu hạn**, hãy kiểm tra ở ranh giới:

~~~java
static BigDecimal requireFinite(double value) {
    if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("value must be finite");
    }
    return BigDecimal.valueOf(value);
}
~~~

Phương thức trên thực sự tạo decimal khi giá trị `double` hữu hạn. Nó **không đảm bảo** decimal vừa tạo bằng giá trị thập phân con người dự định trước các phép tính `double`; cũng không bảo đảm decimal không sai số. Nó chỉ tránh trường hợp NaN/vô cực bị đưa vào constructor không hỗ trợ.

Khi đầu vào từ chuỗi, có thể tạo `new BigDecimal(text)` trực tiếp, sau khi xác thực null, cú pháp, độ dài, scale, số chữ số và miền nghiệp vụ. Việc parse chuỗi có thể ném `NumberFormatException` vì nhiều lý do, không chỉ NaN/vô cực.

Khi kết quả decimal sắp đi tới một API nhận `double`, hãy xem xét cả hai bước:

~~~java
BigDecimal amount = new BigDecimal("1E+400");
double output = amount.doubleValue();
if (!Double.isFinite(output)) {
    throw new IllegalArgumentException("result cannot fit in finite double");
}
~~~

Kiểm tra này phát hiện overflow ra Infinity, nhưng **không đủ** để loại các phép chuyển hữu hạn song mất precision hoặc underflow về 0.0. Nếu cần *exact round-trip*, hãy giữ một contract rõ về giá trị và biểu diễn; chẳng hạn chuyển lại và so sánh theo miền dữ liệu, trong đó phải lưu ý ngữ nghĩa scale và các giá trị đặc biệt.

Nếu tầng nhận dữ liệu vẫn cần biểu diễn NaN hoặc vô cực như một trạng thái nghiệp vụ, **đừng dùng BigDecimal làm “container” cho các trạng thái đó**. Tạo kiểu kết quả có phân biệt *giá trị hữu hạn*, *thiếu dữ liệu*, *không xác định* hoặc *vượt phạm vi*.

## 8. Chương trình Java 17+ có thể biên dịch và chạy

Tạo tệp `DecimalFiniteDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalFiniteDemo {
    static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println(label + " = OK");
        } catch (RuntimeException ex) {
            System.out.println(label + " = "
                    + ex.getClass().getSimpleName());
        }
    }

    static BigDecimal requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return BigDecimal.valueOf(value);
    }

    public static void main(String[] args) {
        System.out.println("Double NaN detected = "
                + Double.isNaN(Double.NaN));
        System.out.println("Double +Infinity detected = "
                + Double.isInfinite(Double.POSITIVE_INFINITY));
        System.out.println("Double -Infinity finite = "
                + Double.isFinite(Double.NEGATIVE_INFINITY));
        System.out.println("finite decimal = "
                + new BigDecimal("12.50"));

        attempt("NaN double constructor",
                () -> new BigDecimal(Double.NaN));
        attempt("+Infinity double constructor",
                () -> new BigDecimal(Double.POSITIVE_INFINITY));
        attempt("NaN valueOf",
                () -> BigDecimal.valueOf(Double.NaN));
        attempt("-Infinity valueOf",
                () -> BigDecimal.valueOf(Double.NEGATIVE_INFINITY));
        attempt("NaN string", () -> new BigDecimal("NaN"));
        attempt("Infinity string", () -> new BigDecimal("Infinity"));

        System.out.println("double 1/0 = " + (1.0 / 0.0));
        System.out.println("double 0/0 is NaN = "
                + Double.isNaN(0.0 / 0.0));
        attempt("decimal 1/0",
                () -> BigDecimal.ONE.divide(BigDecimal.ZERO));
        attempt("decimal 0/0",
                () -> BigDecimal.ZERO.divide(BigDecimal.ZERO));
        attempt("decimal 1/3 exact",
                () -> BigDecimal.ONE.divide(BigDecimal.valueOf(3)));
        System.out.println("decimal 1/3 rounded = "
                + BigDecimal.ONE.divide(
                        BigDecimal.valueOf(3),
                        5, RoundingMode.HALF_UP));

        BigDecimal huge = new BigDecimal("1E+400");
        BigDecimal tiny = new BigDecimal("1E-400");
        System.out.println("huge BigDecimal = " + huge);
        System.out.println("huge to double = " + huge.doubleValue());
        System.out.println("huge to double is finite = "
                + Double.isFinite(huge.doubleValue()));
        System.out.println("tiny BigDecimal = " + tiny);
        System.out.println("tiny to double = " + tiny.doubleValue());
        System.out.println("tiny to double is finite = "
                + Double.isFinite(tiny.doubleValue()));

        System.out.println("negative zero decimal = "
                + new BigDecimal("-0.00"));
        System.out.println("negative zero decimal scale = "
                + new BigDecimal("-0.00").scale());
        System.out.println("finite guard 2.5 = " + requireFinite(2.5));
        attempt("finite guard Infinity",
                () -> requireFinite(Double.POSITIVE_INFINITY));
    }
}
~~~

Chạy trên Java 17 trở lên:

~~~bash
javac DecimalFiniteDemo.java
java DecimalFiniteDemo
~~~

Stdout chính xác:

~~~text
Double NaN detected = true
Double +Infinity detected = true
Double -Infinity finite = false
finite decimal = 12.50
NaN double constructor = NumberFormatException
+Infinity double constructor = NumberFormatException
NaN valueOf = NumberFormatException
-Infinity valueOf = NumberFormatException
NaN string = NumberFormatException
Infinity string = NumberFormatException
double 1/0 = Infinity
double 0/0 is NaN = true
decimal 1/0 = ArithmeticException
decimal 0/0 = ArithmeticException
decimal 1/3 exact = ArithmeticException
decimal 1/3 rounded = 0.33333
huge BigDecimal = 1E+400
huge to double = Infinity
huge to double is finite = false
tiny BigDecimal = 1E-400
tiny to double = 0.0
tiny to double is finite = true
negative zero decimal = 0.00
negative zero decimal scale = 2
finite guard 2.5 = 2.5
finite guard Infinity = IllegalArgumentException
~~~

Đọc output theo cơ chế:

- Các constructor/factory từ NaN và Infinity ném **`NumberFormatException`**, vì không có đối tượng `BigDecimal` tương ứng.
- Các phép chia `BigDecimal` không được phép theo ngữ nghĩa decimal hữu hạn ném **`ArithmeticException`**; một phép chia `1/3` với scale và rounding cụ thể có thể cho số decimal hữu hạn xấp xỉ.
- `huge` và `tiny` là **hai BigDecimal hợp lệ và hữu hạn**; vấn đề chỉ xuất hiện khi gọi `doubleValue()`. Trường hợp dưới 0 nhưng rất nhỏ chuyển thành 0.0 mà `Double.isFinite` vẫn true, cho thấy **finite check không đồng nghĩa exactness check**.
- `new BigDecimal("-0.00")` giữ scale hai chữ số nhưng giá trị in là `0.00`; không có mã negative-zero riêng theo IEEE 754.
- `requireFinite` chặn Infinity **trước** khi chọn `BigDecimal.valueOf`; trong ví dụ nó báo `IllegalArgumentException` do chính ứng dụng định nghĩa, không phải lỗi parser mặc định.

Hàm `attempt` bắt `RuntimeException` **chỉ để demo loại ngoại lệ một cách có kiểm soát**. Trong ứng dụng thật, không nên sử dụng catch quá rộng để nuốt lỗi hoặc mặc định trả 0; cần bắt có chủ đích ở biên giao tiếp, nêu rõ nguồn lỗi và giữ ngữ cảnh thích hợp.

## 9. Phân biệt lỗi NumberFormatException, ArithmeticException và validation error

| Tình huống | Loại kết quả/lỗi | Cách xử lý gợi ý |
| --- | --- | --- |
| `new BigDecimal(Double.NaN)` | `NumberFormatException` | Từ chối input non-finite |
| `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` | `NumberFormatException` | Kiểm tra `Double.isFinite` trước |
| `new BigDecimal("NaN")` hoặc `"Infinity"` | `NumberFormatException` | Báo cú pháp không hợp lệ cho decimal |
| `BigDecimal.ONE.divide(BigDecimal.ZERO)` | `ArithmeticException` | Kiểm tra mẫu số khác zero |
| `BigDecimal.ZERO.divide(BigDecimal.ZERO)` | `ArithmeticException` | Xử lý phép tính không xác định |
| `BigDecimal.ONE.divide(new BigDecimal("3"))` | `ArithmeticException` | Dùng overload có rounding nếu nghiệp vụ cho phép |
| `new BigDecimal("1E+400").doubleValue()` | `Infinity` dạng `double` | Kiểm tra overflow và thiết kế dữ liệu đầu ra |
| `new BigDecimal("1E-400").doubleValue()` | `0.0` dạng `double` | Kiểm tra mất precision/underflow nếu quan trọng |
| `requireFinite(Double.NaN)` trong ví dụ | `IllegalArgumentException` | Lỗi validation do ứng dụng định nghĩa |

**Điểm cần nhớ:** `NumberFormatException` nói đầu vào **không tạo được BigDecimal hợp lệ**; `ArithmeticException` nói **phép toán decimal đã yêu cầu không thực hiện được dưới hợp đồng hiện tại**. Đừng viết `catch (NumberFormatException)` và kỳ vọng bắt luôn phép chia cho zero. Ngược lại, không nên đánh đồng mọi `ArithmeticException` với chia cho zero: phép chia decimal vô hạn không rounding cũng có thể ném cùng loại.

Không dựa vào nội dung `getMessage()` như một mã phân loại ổn định. Khi cần xác định nghiệp vụ “chia cho 0”, hãy kiểm tra `denominator.signum() == 0` trước phép chia và chuyển thành lỗi rõ nghĩa.

## 10. Dữ liệu thiếu, NaN và các trạng thái không hợp lệ nên được mô hình hóa riêng

`null`, `NaN`, vô cực, và số 0 mang **những ý nghĩa khác nhau**. Không nên dùng một số decimal giả như `0` hoặc `-1` để đại diện đồng thời cho tất cả trạng thái này nếu chúng có thể là giá trị nghiệp vụ hợp lệ.

Một API trả kết quả tính toán có thể dùng một kiểu kết quả phân biệt:

~~~java
sealed interface AmountResult
        permits AmountResult.Finite,
                AmountResult.Missing,
                AmountResult.Invalid {
    record Finite(BigDecimal value) implements AmountResult {}
    record Missing() implements AmountResult {}
    record Invalid(String reason) implements AmountResult {}
}
~~~

Đây là **đoạn khai báo minh họa**, cần đặt trong tệp và ngữ cảnh class/package hợp lệ của ứng dụng Java 17+, không phải chương trình độc lập có `main`. Khi dùng trong hệ thống, đặt `BigDecimal` vào `Finite` **chỉ sau khi validation**, yêu cầu object không null (ví dụ kiểm tra `Objects.requireNonNull` trong constructor), và lựa chọn thêm trạng thái overflow/undefined nếu cần.

Có thể chọn `Optional<BigDecimal>` nếu chỉ cần “có giá trị hữu hạn hay không”, nhưng `Optional.empty()` **không nói nguyên nhân**. Với ứng dụng cần chẩn đoán dữ liệu không hợp lệ, một kiểu kết quả có thông tin lỗi thường tốt hơn.

Với API nhận `double`, lựa chọn thường là **từ chối mọi non-finite** (fail fast) trước khi tính decimal. Nếu bài toán khoa học cần lan truyền NaN/Infinity theo IEEE 754, có thể phải giữ pipeline ở kiểu `double` hoặc tạo mô hình trạng thái rõ ràng — `BigDecimal` không tự cung cấp phép lan truyền NaN.

## 11. Lưu và trao đổi dữ liệu giữa JSON, CSV, database và BigDecimal

Khi kết nối các hệ thống, cần xác định **hợp đồng biểu diễn**:

- Nếu trường là một số decimal hợp lệ, đọc trực tiếp dạng chuỗi decimal hoặc kiểu decimal chính xác thành `BigDecimal`; không ép qua `double` nếu cần giữ nguyên precision/scale.
- Nếu dữ liệu nguồn có thể là NaN/Infinity, chuẩn hóa thành **trạng thái riêng** hoặc báo lỗi theo hợp đồng, không giả vờ đó là một `BigDecimal`.
- JSON chuẩn có ngữ pháp số không bao gồm token NaN hoặc Infinity như số; nếu nhà cung cấp gửi chuỗi `"NaN"` hoặc `"Infinity"`, chuỗi đó vẫn không phải decimal hợp lệ và cần chính sách riêng. Một thư viện có thể hỗ trợ tiện ích ngoài chuẩn, nhưng không suy ra mọi parser đều như vậy.
- Trong CSV, giá trị trống, `"NaN"` và `"0"` phải được xử lý theo quy tắc dữ liệu — chúng không tự tương đương.
- Khi truy vấn database, cần phân biệt `NULL` với một số decimal 0; behavior của các kiểu số đặc biệt phụ thuộc hệ quản trị và kiểu cột, không nên suy luận từ Java.

Định dạng xuất `BigDecimal.toString()` có thể dùng ký pháp khoa học cho số rất lớn/nhỏ; `toPlainString()` tránh ký pháp khoa học nhưng có thể tạo chuỗi cực dài với scale lớn. Khi lưu dữ liệu số decimal, hãy giới hạn kích thước/scale theo nghiệp vụ để kiểm soát tài nguyên.

## 12. Các lỗi phổ biến cần tránh

1. **“BigDecimal chính xác hơn double nên phải có Infinity chính xác hơn.”** Sai: `BigDecimal` không biểu diễn Infinity/NaN.
2. **“BigDecimal chia cho zero sẽ tạo Infinity.”** Sai: ném `ArithmeticException`.
3. **“0/0 bằng 0 vì BigDecimal không có NaN.”** Sai: phép chia cho zero không hợp lệ, không được tự đổi thành 0.
4. **“BigDecimal.valueOf(NaN) trả null.”** Sai: ném `NumberFormatException`.
5. **“new BigDecimal("NaN") cho một số decimal đặc biệt.”** Sai: chuỗi không hợp lệ với parser decimal.
6. **“Mọi phép chia `BigDecimal` ném ArithmeticException đều do mẫu số 0.”** Sai: khai triển thập phân vô hạn không rounding cũng có thể gây lỗi.
7. **“Đặt rounding mode sẽ cho phép chia cho zero.”** Sai: rounding chỉ xử lý độ chính xác của phép chia hợp lệ, không tạo giá trị cho mẫu số 0.
8. **“BigDecimal hữu hạn thì doubleValue cũng hữu hạn.”** Sai: decimal lớn có thể bị overflow thành Infinity.
9. **“doubleValue hữu hạn nghĩa là không mất thông tin.”** Sai: nó có thể làm tròn hoặc underflow về 0.0.
10. **“Kết quả `Double.isFinite` true chứng minh decimal ban đầu đã được bảo toàn.”** Sai: một số decimal khác zero có thể chuyển thành 0.0 và vẫn finite.
11. **“Chuỗi số decimal rất lớn nhất định phải qua double trước.”** Sai: parse trực tiếp thành `BigDecimal` có thể giữ được dữ liệu decimal.
12. **“BigDecimal giữ được `-0.0` như double.”** Sai: BigDecimal không có mã negative zero riêng; nó vẫn có thể giữ scale của chuỗi `"-0.00"`.
13. **“NaN đồng nghĩa null/thiếu giá trị.”** Sai: các trạng thái mang ý nghĩa khác nhau; không gộp tùy tiện.
14. **“Bắt mọi exception rồi trả BigDecimal.ZERO là an toàn.”** Sai: làm mất tín hiệu lỗi, có thể khiến kết quả tài chính sai.
15. **“Không cần kiểm tra giới hạn đầu vào vì BigDecimal có độ chính xác tùy ý.”** Sai: unscaled integer, scale, tạo chuỗi và phép toán vẫn có giới hạn tài nguyên và đặc tả.
16. **“Có thể dùng BigDecimal để tự mô phỏng IEEE 754 decimal floating point đầy đủ.”** Sai: IEEE 754 decimal có thêm các lớp giá trị đặc biệt và các ràng buộc format/exponent không giống mô hình BigDecimal.

## 13. Checklist tích hợp an toàn

1. Xác định trường đầu vào là **decimal chính xác** hay `double` có thể chứa NaN/Infinity.
2. Nếu nguồn là `double`, kiểm tra `Double.isFinite` trước khi chuyển; chọn chính sách khi NaN/vô cực xuất hiện.
3. Nếu nguồn là chuỗi, parse trực tiếp thành `BigDecimal` sau khi kiểm tra cú pháp và giới hạn input; bắt `NumberFormatException` tại ranh giới.
4. Nếu tính phép chia decimal, xác minh mẫu số khác 0; chọn `scale`, `precision` và `RoundingMode` nếu kết quả không thể biểu diễn chính xác.
5. Không mặc định mọi `ArithmeticException` đều nghĩa là chia cho 0.
6. Trước khi xuất `BigDecimal` thành `double`/`float`, đánh giá khả năng overflow, underflow và mất chính xác.
7. Phân biệt null, dữ liệu sai, số 0, NaN và vô cực theo hợp đồng API; không dùng một sentinel mơ hồ.
8. Với tiền tệ và số liệu quan trọng, giữ phép tính ở `BigDecimal` đến tầng xuất thích hợp; không đi qua binary64 khi không cần.
9. Thiết kế kiểm thử ranh giới cho `NaN`, `±Infinity`, số 0, mẫu số 0, `1/3`, decimal rất lớn và rất nhỏ.
10. Khi báo lỗi cho người dùng, sử dụng thông điệp nghiệp vụ rõ ràng và tránh log thừa dữ liệu nhạy cảm.

## 14. Câu hỏi tự luyện

**Câu 1.** `new BigDecimal(Double.NaN)` trả gì? `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` thì sao?

**Đáp án:** Cả hai ném `NumberFormatException` vì `BigDecimal` không có giá trị tương ứng với NaN hoặc vô cực.

**Câu 2.** `BigDecimal.ONE.divide(BigDecimal.ZERO)` và `1.0/0.0` có hành vi tương tự không?

**Đáp án:** Không. BigDecimal ném `ArithmeticException`; `double` tạo dương Infinity.

**Câu 3.** Vì sao `BigDecimal.ONE.divide(new BigDecimal("3"))` có thể ném `ArithmeticException` dù mẫu số khác zero?

**Đáp án:** Thương 1/3 không có biểu diễn decimal hữu hạn; overload chia chính xác không cho phép tự làm tròn. Cần chỉ định precision/scale và rounding nếu nghiệp vụ cho phép xấp xỉ.

**Câu 4.** `new BigDecimal("1E+400")` là vô cực không? Còn kết quả `doubleValue()`?

**Đáp án:** BigDecimal là số hữu hạn hợp lệ; ép sang `double` có thể trở thành `Infinity` vì overflow binary64.

**Câu 5.** `new BigDecimal("1E-400").doubleValue()` có cho số hữu hạn không và có bảo toàn giá trị không?

**Đáp án:** Cho `0.0` hữu hạn trong ví dụ, nhưng không bảo toàn giá trị decimal 10^-400 khác zero.

**Câu 6.** Nếu một dịch vụ trả chuỗi `"NaN"` thay vì số tiền, có nên dùng `new BigDecimal("NaN")` hoặc tự thay bằng 0?

**Đáp án:** Không. Nên xác định trạng thái “dữ liệu không hợp lệ/không xác định” trong hợp đồng và xử lý riêng, thay vì gán một amount 0 không có cơ sở.

**Câu 7.** `Double.isFinite(amount.doubleValue())` true có đủ chứng minh conversion không mất chính xác không?

**Đáp án:** Không. Tính hữu hạn không kiểm tra round-trip chính xác; underflow và làm tròn vẫn có thể xảy ra.

## Tài liệu tham khảo

- [Oracle Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): mô hình unscaled/scale, khác biệt với IEEE 754 về NaN/vô cực/negative zero, lỗi khi chia 0, `doubleValue` và constructor.
- [Oracle Java SE 21 — Double](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html): `isNaN`, `isInfinite`, `isFinite`, `parseDouble`.
- [Oracle Java SE 21 — RoundingMode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html): chính sách làm tròn khi tính số decimal.
- [Oracle Java SE 21 — MathContext](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/MathContext.html): precision và rounding trong phép toán BigDecimal.
- [RFC 8259 — JSON Data Interchange Format](https://www.rfc-editor.org/rfc/rfc8259): ngữ pháp JSON number không có NaN/Infinity.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-model-010">Khi nào dùng BigDecimal thay float hoặc double</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào dùng `BigDecimal` thay `float` hoặc `double` trong Java?

Chọn kiểu số là một quyết định về **ngữ nghĩa dữ liệu và sai số được phép**, không chỉ về dung lượng bộ nhớ. `float` và `double` lưu số dấu phẩy động **nhị phân, độ chính xác hữu hạn**; `BigDecimal` lưu số thập phân theo mô hình một số nguyên không chia tỷ lệ (*unscaled value*) và một `scale`. Những con số trông giống nhau khi in ra có thể có cách biểu diễn và kết quả tính toán khác nhau.

Quy tắc thực hành quan trọng nhất:

- **Dùng `BigDecimal`** khi cần giữ chính xác các giá trị thập phân được nhập vào (giá, tiền, thuế, số lượng tính theo đơn vị thập phân, tỷ lệ hợp đồng), hoặc phải thực hiện chính sách làm tròn thập phân tường minh và kiểm tra được.
- **Dùng `double`** khi đang giải quyết bài toán số học xấp xỉ như khoa học, mô phỏng, đồ họa, thống kê hoặc học máy, nơi sai số dấu phẩy động đã được chấp nhận và hiệu năng/API số học là ưu tiên. `float` phù hợp khi ưu tiên bộ nhớ, băng thông, GPU hoặc API yêu cầu 32 bit và độ chính xác thấp hơn là đủ.
- **Không có kiểu nào tự làm phép tính của bạn đúng theo nghiệp vụ.** `BigDecimal` có phép chia phải làm tròn khi kết quả không thể biểu diễn hữu hạn ở hệ thập phân; `double` cần phân tích sai số và điều kiện biên.

## 1. Khác nhau ở cách biểu diễn

| Đặc tính | `float` | `double` | `BigDecimal` |
|---|---|---|---|
| Mô hình | IEEE 754 nhị phân 32 bit | IEEE 754 nhị phân 64 bit | `unscaledValue × 10^-scale` |
| Độ chính xác | 24 bit có nghĩa (khoảng 6–9 chữ số thập phân hữu ích) | 53 bit có nghĩa (khoảng 15–17 chữ số thập phân hữu ích) | Do biểu diễn nguyên/scale và tài nguyên cho phép |
| `0.1` thập phân | Không biểu diễn nhị phân chính xác | Không biểu diễn nhị phân chính xác | `new BigDecimal("0.1")` biểu diễn chính xác |
| Giá trị đặc biệt | `NaN`, `±Infinity`, số 0 có dấu | `NaN`, `±Infinity`, số 0 có dấu | Không có giá trị `NaN` hoặc vô cực |
| Phép toán | Toán tử `+ - * /` | Toán tử `+ - * /` | `add`, `subtract`, `multiply`, `divide`... |
| Chi phí thường gặp | Thấp, dùng phần cứng | Thấp, dùng phần cứng | Cao hơn, nhiều đối tượng/bộ nhớ |
| Ngữ cảnh điển hình | Đồ họa, dữ liệu số lượng lớn | Tính toán khoa học, số liệu gần đúng | Thập phân chính xác và làm tròn theo hợp đồng |

`BigDecimal` không có một giới hạn cố định về số chữ số như `double`, nhưng **không phải số thực vô hạn độ chính xác**: biểu diễn thập phân là hữu hạn, kích thước và scale bị giới hạn bởi tài nguyên và API; phép chia như `1/3` vẫn cần chọn quy tắc làm tròn để biểu diễn kết quả hữu hạn.

### Vì sao `0.1` không chính xác trong `double`?

Một số phân số thập phân hữu hạn lại là phân số nhị phân vô hạn. Ví dụ `0.5 = 1/2` biểu diễn chính xác bằng nhị phân, còn `0.1 = 1/10` có khai triển nhị phân lặp vô hạn. `double` chỉ giữ một số bit hữu hạn nên lưu giá trị **gần** 0.1 nhất trong lưới số nhị phân.

```java
double approximate = 0.1 + 0.2;
System.out.println(approximate); // 0.30000000000000004

BigDecimal exact = new BigDecimal("0.1").add(new BigDecimal("0.2"));
System.out.println(exact);       // 0.3
```

Đây là **đoạn minh họa trong phương thức**, không phải toàn bộ lớp Java độc lập. Đừng suy ra mọi phép cộng `double` đều sai, hoặc cứ thấy chuỗi dài là một lỗi JVM: kết quả tuân theo độ chính xác hữu hạn và quy tắc làm tròn của dấu phẩy động nhị phân.

## 2. Những tình huống nên chọn `BigDecimal`

### 2.1. Giá, thuế, lãi suất, chiết khấu và số tiền theo quy tắc thập phân

Khi đầu vào là các chữ số thập phân có ý nghĩa hợp đồng (ví dụ giá đơn vị `"19.99"`), việc biểu diễn chính xác giúp tránh đưa sai số nhị phân vào phép tính. Ví dụ:

```java
BigDecimal price = new BigDecimal("19.99");
BigDecimal quantity = new BigDecimal("3");
BigDecimal subtotal = price.multiply(quantity); // 59.97
```

Nếu cần tính phần thuế, làm tròn theo một chính sách **được định nghĩa rõ**:

```java
import java.math.RoundingMode;

BigDecimal taxRate = new BigDecimal("0.075");
BigDecimal tax = subtotal.multiply(taxRate)
                         .setScale(2, RoundingMode.HALF_UP);
// tax = 4.50
```

Đoạn mã minh họa giả định đã có các biến và import. `HALF_UP` ở đây chỉ là **ví dụ chính sách**, không phải quy định bắt buộc cho mọi quốc gia hoặc giao dịch. Độ chính xác phần nhỏ nhất của tiền tệ, điểm làm tròn (từng dòng hay tổng hóa đơn), cách xử lý số âm, khấu trừ và quy định làm tròn có thể khác nhau. Kiểu `BigDecimal` chỉ cho công cụ để thực hiện một quy tắc đã chọn.

Nếu dữ liệu toàn là số đơn vị nhỏ nhất (ví dụ số xu nguyên) và **không cần phép chia thập phân**, lưu `long` hoặc `BigInteger` dưới dạng số đơn vị nhỏ nhất cũng là thiết kế đáng cân nhắc. Chọn kiểu theo miền dữ liệu và thao tác, không mặc định rằng tất cả giá trị tiền phải luôn là `BigDecimal`.

### 2.2. Dữ liệu thập phân đi vào/đi ra có hợp đồng rõ ràng

Nếu API nhận chuỗi `"0.000001"` hoặc cơ sở dữ liệu có cột decimal chính xác, hãy giữ ý nghĩa số thập phân đó đến tận logic tính toán. `BigDecimal` có thể giúp:

- tránh mất chữ số có nghĩa khi chuyển qua `double`;
- xác thực scale hoặc số chữ số theo hợp đồng;
- làm tròn tường minh cho trường hợp quy định tối đa số chữ số;
- so sánh các giá trị thập phân mà không phụ thuộc sai số biểu diễn nhị phân.

Ví dụ kiểm tra dữ liệu phải có **tối đa hai chữ số thập phân mà không thay đổi giá trị**:

```java
BigDecimal input = new BigDecimal("12.340");
BigDecimal canonical = input.setScale(2); // 12.34, chính xác

// new BigDecimal("12.345").setScale(2)
// sẽ ném ArithmeticException nếu phải bỏ phần khác 0.
```

`setScale(2)` một tham số yêu cầu không được làm thay đổi giá trị toán học. Nếu cần **kiểm tra cả định dạng nguyên bản** (ví dụ cấm nhập quá hai ký tự sau dấu thập phân kể cả số 0), việc chuẩn hóa như trên chưa đủ: phải kiểm tra chuỗi nguồn hoặc metadata bổ sung trước khi chuẩn hóa.

### 2.3. Giá trị nguyên lớn chuyển sang phép toán thập phân

Một số `long` lớn có thể bị mất đơn vị khi ép sang `double`; độ chính xác nguyên tuyệt đối của `double` chỉ được đảm bảo cho tất cả số nguyên trong miền đến `2^53`. Khi cần tính tiếp với giá trị lớn và các hệ số thập phân chính xác, `BigDecimal.valueOf(long)` giữ nguyên số nguyên đầu vào.

```java
long idLikeNumber = 9_007_199_254_740_993L;
BigDecimal exact = BigDecimal.valueOf(idLikeNumber);
// exact = 9007199254740993
```

Tuy nhiên, với ID, khóa hoặc mã định danh **không thực hiện số học**, dùng kiểu nguyên hoặc chuỗi có ý nghĩa hơn. Chỉ chuyển sang `BigDecimal` nếu bài toán thật sự cần phép tính thập phân.

## 3. Khi nào `double` và `float` là lựa chọn đúng?

`double` thường phù hợp khi dữ liệu mang bản chất **đo lường/xấp xỉ**: vị trí, vận tốc, góc, tín hiệu, xác suất, thống kê, mô phỏng hoặc phép toán sử dụng các hàm toán học như `Math.sin`, `Math.sqrt`, `Math.log`. Trong các miền này:

1. Nguồn dữ liệu vốn có sai số đo đạc nên biểu diễn thập phân chính xác không làm nó trở thành số đo chính xác tuyệt đối.
2. Các thuật toán đã thiết kế với sai số dấu phẩy động, khoảng sai số tương đối/tuyệt đối và độ ổn định số.
3. Phần cứng, thư viện và vector hóa thường hỗ trợ `float`/`double` tốt hơn.
4. `double` có miền số mũ rất rộng và giá trị đặc biệt (`NaN`, `±Infinity`) đôi khi là một phần của mô hình tính toán.
5. Thư viện chuẩn `Math`/`StrictMath` tập trung vào kiểu nguyên thủy dấu phẩy động, không có API `BigDecimal` tương đương cho toàn bộ lượng giác, hàm mũ hay logarit.

`float` phù hợp với đồ họa, dữ liệu tensor, dữ liệu lưu trữ lớn hoặc API cần số 32 bit, **nếu sai số cho phép**. Với tính toán tổng quát trên CPU, `double` thường là mặc định hợp lý trước khi cân nhắc giảm độ chính xác xuống `float`.

Ví dụ đo một đại lượng thực nghiệm có sai số ±0.01, việc ép nó vào `BigDecimal("1.2345")` không làm số đo thực sự chính xác đến bốn chữ số sau dấu thập phân. Cần phản ánh độ bất định của phép đo trong mô hình.

### So sánh kết quả số gần đúng

Không mặc định so sánh hai kết quả tính toán `double` qua `==` nếu thuật toán chấp nhận sai số:

```java
double actual = 0.1 + 0.2;
double expected = 0.3;
double tolerance = 1e-12;

boolean close = Math.abs(actual - expected) <= tolerance;
// true trong tình huống này
```

Đây chỉ là ví dụ kiểm tra với miền giá trị nhỏ, hữu hạn. Ngưỡng sai số phải căn cứ miền dữ liệu và đặc tả thuật toán; phép kiểm tra đơn giản này không xử lý đầy đủ `NaN`, vô cực hay các giá trị có độ lớn rất khác nhau. Với dữ liệu có nhiều bậc độ lớn, có thể cần xét kết hợp sai số tuyệt đối/tương đối hoặc khoảng cách ULP.

## 4. Tạo `BigDecimal` đúng cách: nguồn thập phân rất quan trọng

Đây là lỗi ảnh hưởng trực tiếp đến lý do chọn `BigDecimal`.

| Cách tạo | Giá trị được lưu | Khi nào nên dùng |
|---|---|---|
| `new BigDecimal("0.1")` | Chính xác thập phân 0.1 | Khi nguồn là một chuỗi thập phân |
| `BigDecimal.valueOf(0.1)` | Dựa trên dạng chuỗi chuẩn của `double` (ở đây là 0.1) | Khi đầu vào hiện có kiểu `double` và muốn chuyển theo dạng thập phân mà Java thường in ra |
| `new BigDecimal(0.1)` | Chính xác giá trị **nhị phân của `double`**, hơi lớn hơn 0.1 | Chỉ khi thật sự cần giữ chính xác giá trị số nhị phân ban đầu |
| `BigDecimal.valueOf(100L)` | Chính xác số nguyên 100 | Khi nguồn là `long` |

`new BigDecimal(0.1)` không tạo chính xác số thập phân `0.1` theo ý định thông thường. Nó tạo biểu diễn thập phân chính xác của giá trị nhị phân đã được làm tròn:

```text
0.1000000000000000055511151231257827021181583404541015625
```

`BigDecimal.valueOf(0.1)` thường là lựa chọn chuyển đổi tốt hơn `new BigDecimal(0.1)` khi nguồn chỉ còn `double`, **nhưng nó không thể phục hồi những chữ số đã bị mất bởi các phép toán trước đó**.

```java
double alreadyRounded = 0.1 + 0.2;
BigDecimal converted = BigDecimal.valueOf(alreadyRounded);
// converted = 0.30000000000000004, không phải 0.3
```

Nếu đầu vào thực tế là `"0.1"` và `"0.2"`, hãy phân tích hai chuỗi thành `BigDecimal` **trước khi cộng**. Đó mới là cách tránh cho sai số nhị phân đi vào luồng xử lý thập phân.

## 5. `BigDecimal` vẫn cần chính sách độ chính xác và làm tròn

Biểu diễn thập phân hữu hạn không thể chứa chính xác mọi phân số. `1/8 = 0.125` có biểu diễn thập phân hữu hạn, còn `1/3 = 0.333...` thì không. Vì vậy:

```java
BigDecimal one = BigDecimal.ONE;

one.divide(new BigDecimal("8")); // 0.125
// one.divide(new BigDecimal("3")); // ArithmeticException
```

Phép `divide` không kèm quy tắc làm tròn yêu cầu kết quả chính xác và có biểu diễn thập phân hữu hạn. Với kết quả thập phân vô hạn, phải quy định số chữ số hoặc độ chính xác và cách làm tròn:

```java
BigDecimal ratio = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP);
// ratio = 0.33
```

Ngoài `setScale`, có thể dùng `MathContext` khi muốn giới hạn tổng số **chữ số có nghĩa** cho một phép tính hay nhóm phép tính, thay vì cố định chữ số sau dấu thập phân:

```java
import java.math.MathContext;

MathContext context = new MathContext(5, RoundingMode.HALF_EVEN);
BigDecimal result = BigDecimal.ONE.divide(new BigDecimal("3"), context);
// result = 0.33333
```

**Phân biệt:**

- `setScale(2, ...)`: yêu cầu hai chữ số sau dấu thập phân (scale 2).
- `MathContext(5, ...)`: yêu cầu năm chữ số có nghĩa cho phép toán được áp dụng.
- Không có tham số làm tròn khi kết quả bắt buộc cắt chữ số khác 0: có thể ném `ArithmeticException`.

Không làm tròn tùy tiện ở mọi bước trung gian rồi cho rằng tổng cuối chắc chắn đúng. Ví dụ tính thuế từng dòng hóa đơn và cộng lại có thể khác việc cộng trước rồi tính thuế trên tổng. Chọn **điểm làm tròn** theo hợp đồng nghiệp vụ rồi kiểm thử kết quả.

## 6. So sánh giá trị `BigDecimal`: `equals` khác `compareTo`

Scale là một phần của biểu diễn `BigDecimal`, nên:

```java
BigDecimal a = new BigDecimal("1.0");
BigDecimal b = new BigDecimal("1.00");

a.equals(b);         // false (scale khác nhau)
a.compareTo(b) == 0; // true  (giá trị số bằng nhau)
```

`equals` xét cả giá trị lẫn scale; `compareTo` xét thứ tự theo giá trị số. Khi kiểm tra mức tiền bằng nhau về toán học, thường dùng `compareTo(...) == 0`; khi scale là một phần của hợp đồng dữ liệu, có thể chủ động dùng `equals`.

Hợp đồng `hashCode` đi theo `equals`. Hai giá trị thập phân bằng nhau về số nhưng khác scale có thể hoạt động như các **khóa khác nhau** trong `HashMap`/`HashSet`. Nếu dùng làm khóa, cần quy tắc chuẩn hóa rõ ràng, ví dụ scale cố định hoặc một định dạng chuẩn được thống nhất.

## 7. Ví dụ Java 17+ chạy độc lập

Lưu mã dưới tên **`BigDecimalChoiceDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalChoiceDemo {
    public static void main(String[] args) {
        double binarySum = 0.1 + 0.2;
        System.out.println("double sum: " + binarySum);

        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("decimal sum: " + a.add(b));
        System.out.println("new BigDecimal(0.1): " + new BigDecimal(0.1));
        System.out.println("BigDecimal.valueOf(0.1): " + BigDecimal.valueOf(0.1));
        System.out.println("valueOf(double sum): " + BigDecimal.valueOf(binarySum));

        long exactInteger = 9_007_199_254_740_993L;
        System.out.println("large as double then long: " + (long) (double) exactInteger);
        System.out.println("large as BigDecimal: " + BigDecimal.valueOf(exactInteger));

        System.out.println("1 / 8: " + BigDecimal.ONE.divide(new BigDecimal("8")));
        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 exact: ArithmeticException");
        }
        System.out.println("1 / 3 rounded: " +
                BigDecimal.ONE.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP));
        System.out.println("rounded amount: " +
                new BigDecimal("1.005").setScale(2, RoundingMode.HALF_UP));

        BigDecimal x = new BigDecimal("1.0");
        BigDecimal y = new BigDecimal("1.00");
        System.out.println("numeric equals: " + (x.compareTo(y) == 0));
        System.out.println("object equals: " + x.equals(y));

        System.out.println("double infinity: " + (1.0 / 0.0));
        try {
            BigDecimal.valueOf(Double.POSITIVE_INFINITY);
        } catch (NumberFormatException ex) {
            System.out.println("BigDecimal infinity: NumberFormatException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BigDecimalChoiceDemo.java
java BigDecimalChoiceDemo
```

Kết quả stdout đã được xác minh bằng JDK:

```text
double sum: 0.30000000000000004
decimal sum: 0.3
new BigDecimal(0.1): 0.1000000000000000055511151231257827021181583404541015625
BigDecimal.valueOf(0.1): 0.1
valueOf(double sum): 0.30000000000000004
large as double then long: 9007199254740992
large as BigDecimal: 9007199254740993
1 / 8: 0.125
1 / 3 exact: ArithmeticException
1 / 3 rounded: 0.33
rounded amount: 1.01
numeric equals: true
object equals: false
double infinity: Infinity
BigDecimal infinity: NumberFormatException
```

Ý nghĩa các kết quả:

1. `double sum` và `decimal sum` cho thấy sai số biểu diễn nhị phân khác với phép cộng số thập phân chính xác.
2. `new BigDecimal(0.1)` cho biết chính xác giá trị nhị phân `double` thực sự lưu; `valueOf(0.1)` dùng chuỗi thập phân chuẩn của `double`.
3. `valueOf(double sum)` chứng minh phép chuyển kiểu **sau khi tính toán** không xóa sai số đã có.
4. Một số nguyên chỉ lớn hơn `2^53` một đơn vị không còn được giữ nguyên qua `double`, nhưng `BigDecimal.valueOf(long)` vẫn giữ đúng số nguyên.
5. `1/8` có kết quả chính xác; `1/3` không biểu diễn hữu hạn nên phải chọn phương pháp làm tròn.
6. `1.005` làm tròn tới hai chữ số với `HALF_UP` thành `1.01`, là ví dụ chính sách làm tròn thập phân tường minh.
7. `equals` khác `compareTo`; vô cực thuộc miền `double` nhưng không phải giá trị hợp lệ của `BigDecimal`.

## 8. Các đánh đổi về hiệu năng và khả năng tương tác

`BigDecimal` hữu ích khi bài toán **cần** độ chính xác thập phân, song không nên dùng cho mọi giá trị số theo thói quen.

**Hiệu năng:** `double` và `float` thường được xử lý bằng phần cứng, ít cấp phát hơn trong tính toán số quy mô lớn. `BigDecimal` là đối tượng bất biến và thực hiện số học dựa trên biểu diễn nguyên/scale nên có chi phí cấp phát, bộ nhớ và CPU đáng kể trong các vòng lặp lớn. Với yêu cầu hiệu năng, hãy đo trên workload thực thay vì đoán một tỷ lệ nhanh/chậm cố định.

**Tương tác API:** nhiều thư viện khoa học, đồ họa, xử lý tín hiệu và học máy nhận/trả `double` hoặc `float`. Đưa `BigDecimal` vào rồi liên tục chuyển ngược sang `double` có thể không tăng độ chính xác thực tế, vì khi chuyển ngược vẫn làm tròn về định dạng nhị phân.

**Miền giá trị:** `float`/`double` có `NaN`, vô cực, signed zero, số subnormal; `BigDecimal` chỉ biểu diễn số thập phân hữu hạn. `BigDecimal.valueOf(Double.NaN)` và `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` không tạo được số hợp lệ, thường ném `NumberFormatException`.

**Lỗi tính toán:** `BigDecimal` không chữa được sai thuật toán, sai đơn vị đo, sai thời điểm làm tròn hoặc dữ liệu đo đạc vốn không chính xác. Nếu người dùng nhập “10,00” nhưng ứng dụng chọn nhầm đơn vị tiền tệ, `BigDecimal` vẫn tính đúng trên **giả định sai**.

**Độ lớn:** `BigDecimal` không bị khóa ở 64 bit, nhưng scale dùng kiểu `int` và khối lượng chữ số bị giới hạn thực tế. Kết quả hoặc phép dịch scale quá lớn có thể không khả thi hoặc ném ngoại lệ; không dùng nó như một kiểu đại diện vô hạn cho tất cả số thực.

## 9. Tiêu chí chọn kiểu dữ liệu trong dự án

| Câu hỏi thiết kế | Gợi ý chọn |
|---|---|
| Đầu vào là giá trị tiền tệ/số thập phân hợp đồng cần giữ đúng chữ số? | Ưu tiên `BigDecimal` từ chuỗi/decimal |
| Phép toán phải tuân theo scale và rounding cụ thể? | `BigDecimal` + `RoundingMode` |
| Giá trị thực sự là số đơn vị nhỏ nhất nguyên và đủ miền? | Cân nhắc `long`; dùng `BigInteger` nếu cần miền nguyên lớn |
| Bài toán là mô phỏng, hình học, thống kê, lượng giác, phép đo? | Thường chọn `double` với kiểm soát sai số |
| Dữ liệu lớn, GPU, băng thông hoặc API yêu cầu 32 bit? | Cân nhắc `float` nếu độ chính xác đáp ứng |
| Nguồn ban đầu đã tính bằng `double` và giờ đổi sang `BigDecimal`? | Xem lại nguồn và chuỗi phép tính; chuyển cuối không sửa sai số cũ |
| Cần biểu diễn `NaN` hay vô cực? | `float`/`double` hoặc thiết kế kiểu bao trạng thái riêng; `BigDecimal` không có các giá trị đó |

Một quy trình chọn kiểu thực tế gồm: xác định **đơn vị và miền giá trị**, độ chính xác/sai số yêu cầu, cách xử lý phép chia, khi nào phải làm tròn, kiểu dữ liệu ở ranh giới API/database, rồi mới tối ưu hiệu năng. Cần kiểm tra toàn bộ luồng dữ liệu, vì một bước ép sang `double` giữa chuỗi có thể làm mất ý nghĩa thập phân chính xác.

## 10. Lỗi phổ biến khi quyết định hoặc chuyển đổi

1. **Dùng `double` cho tiền rồi hy vọng `String.format("%.2f", ...)` sẽ sửa mọi phép tính.** Định dạng chỉ ảnh hưởng chuỗi cuối; sai số trong phép tính trước đó vẫn tồn tại.
2. **Viết `new BigDecimal(0.1)` vì tưởng nó biểu diễn đúng 0.1 thập phân.** Sử dụng `new BigDecimal("0.1")` khi nguồn là chuỗi số thập phân, hoặc `valueOf` khi chuyển một `double` sẵn có.
3. **Dùng `BigDecimal.valueOf(0.1 + 0.2)` rồi nghĩ là đã phục hồi 0.3.** Phép cộng thực hiện bằng `double` trước nên giá trị chuyển đổi vẫn mang dấu vết làm tròn.
4. **Gọi `divide` mà không thiết kế phép làm tròn.** Kết quả như 1/3 không có dạng thập phân hữu hạn; cần scale/`MathContext` và `RoundingMode` khi phù hợp.
5. **Cho rằng `BigDecimal` luôn chính xác tuyệt đối cho mọi phép số học.** Chia không kết thúc và các phép được giới hạn bởi `MathContext` đều có thể làm tròn; chính sách đó phải được công khai.
6. **Cho rằng mọi giá trị tiền tệ phải làm tròn tới hai chữ số.** Đơn vị tiền, quy định và trường hợp làm tròn có thể khác; không hard-code khi chưa có đặc tả.
7. **So sánh hai giá trị `BigDecimal` bằng `equals` khi chỉ cần bằng nhau về toán học.** Khác scale có thể khiến `equals` false; dùng `compareTo(...) == 0` nếu scale không có ý nghĩa nghiệp vụ.
8. **Dùng `BigDecimal` ở mọi thuật toán số mà không xét hiệu năng/thư viện.** Hãy đánh giá sai số cho phép, đặc trưng thư viện và khối lượng công việc.
9. **Chuyển `BigDecimal` sang `doubleValue()` rồi mặc định không mất dữ liệu.** Chuyển đổi có thể làm tròn, mất chữ số hoặc tràn sang vô cực khi ngoài miền `double`.
10. **Dùng so sánh `double` với một epsilon cố định cho mọi thang đo.** Ngưỡng đúng phải liên quan quy mô dữ liệu, số phép toán và đặc tả số học; cần xử lý riêng `NaN`/vô cực.

## 11. Bài tập và câu hỏi tự kiểm tra

1. Một API gửi `"23.45"` làm **đơn giá**, sau đó tính chiết khấu 12,5% và phải làm tròn từng dòng theo chính sách được cung cấp. Nên chọn kiểu gì? **Đáp án:** `BigDecimal` từ chuỗi, phép nhân thập phân và `setScale`/`RoundingMode` theo quy định.
2. Một cảm biến xuất nhiệt độ với sai số đo ±0,2°C và chương trình tính trung bình trên hàng triệu bản ghi. Có bắt buộc dùng `BigDecimal`? **Đáp án:** Không. `double` thường phù hợp nếu phân tích sai số/độ chính xác yêu cầu cho phép.
3. `BigDecimal.valueOf(0.1 + 0.2)` có cho `"0.3"` không? **Đáp án:** Không; kết quả là `0.30000000000000004` vì phép cộng đã thực hiện ở miền `double`.
4. `BigDecimal.ONE.divide(new BigDecimal("3"))` trả gì? **Đáp án:** Ném `ArithmeticException` do thập phân vô hạn mà không có quy tắc làm tròn.
5. Vì sao `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` false nhưng `compareTo == 0`? **Đáp án:** `equals` xét cả scale; `compareTo` chỉ xét giá trị số.
6. Một bài toán đồ họa cần hàng triệu tọa độ 32 bit cho GPU. Có nên chuyển toàn bộ sang `BigDecimal`? **Đáp án:** Thường không; `float` là kiểu phù hợp khi API và độ chính xác 32 bit đáp ứng yêu cầu.
7. Một số nguyên `9007199254740993` chuyển qua `double` rồi về `long` có bảo toàn không? **Đáp án:** Không; chương trình đã kiểm chứng nó thành `9007199254740992`. Giữ bằng `long` hoặc kiểu nguyên/decimal chính xác khi cần.

## Ghi nhớ

- `BigDecimal` giải quyết **nhu cầu biểu diễn và tính toán thập phân có kiểm soát**, đặc biệt khi chữ số thập phân và chính sách làm tròn mang ý nghĩa nghiệp vụ.
- `float`/`double` là lựa chọn hiệu quả cho **tính toán gần đúng có phân tích sai số**; không cần thay chúng bằng `BigDecimal` theo phản xạ.
- Chọn `BigDecimal` từ **nguồn số thập phân chính xác**. `new BigDecimal(double)` phản ánh giá trị nhị phân; `valueOf(double)` không sửa được sai số đã phát sinh trước khi chuyển.
- `BigDecimal` vẫn cần quy định phép chia, `MathContext`, scale và `RoundingMode`; đồng thời phải xử lý `equals`/`compareTo` đúng.
- Chất lượng kết quả đến từ **toàn bộ luồng dữ liệu và chính sách số học**, không chỉ từ tên kiểu dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
